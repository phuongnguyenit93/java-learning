<a id="back-to-top"></a>

# Kiểm thử web

## Menu
- [Phân biệt kiểm thử controller độc lập, kiểm thử hạ tầng và HTTP qua máy chủ thật](#web-testing-001)
- [MockMvc: standaloneSetup và webAppContextSetup](#web-testing-002)
- [Kiểm chứng mapping, tham số, header, status và nội dung JSON](#web-testing-003)
- [Kiểm thử binding, validation, ProblemDetail và ControllerAdvice](#web-testing-004)
- [Kiểm thử view, form, redirect, flash attributes và session](#web-testing-005)
- [Kiểm thử multipart, CORS và yêu cầu có điều kiện](#web-testing-006)
- [Kiểm thử MVC async, asyncDispatch và phản hồi streaming](#web-testing-007)
- [WebTestClient với controller, RouterFunction, ApplicationContext hoặc máy chủ thật](#web-testing-008)
- [Kiểm thử Flux, SSE, hủy đăng ký và giới hạn thời gian](#web-testing-009)
- [MockRestServiceServer cho client đồng bộ và máy chủ giả lập cho WebClient](#web-testing-010)
- [Kiểm thử HTTP Service Client và WebSocket/STOMP theo hợp đồng](#web-testing-011)

## <a id="web-testing-001">Phân biệt kiểm thử controller độc lập, kiểm thử hạ tầng và HTTP qua máy chủ thật</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kiểm thử controller độc lập, kiểm thử hạ tầng và HTTP qua máy chủ thật

Khi kiểm thử một endpoint Spring Web, câu hỏi đầu tiên là **đang muốn chứng minh điều gì?** Một lời gọi phương thức Java thành công chưa chứng minh rằng URL, JSON, bộ lọc hay HTTP thực sự hoạt động. Ngược lại, khởi động cả ứng dụng để thử một nhánh tính toán nhỏ có thể làm bài kiểm thử chậm và khó xác định nguyên nhân lỗi.

Bài này dùng Spring MVC (Servlet) làm ví dụ chính, phân biệt ba ranh giới: **controller độc lập**, **hạ tầng Spring MVC trong bộ nhớ**, và **HTTP đến máy chủ thật**. Các tiện ích Spring Boot 3.3 được trình bày như cách cấu hình thuận tiện; khái niệm của Spring Framework không phụ thuộc vào việc có dùng Spring Boot hay không.

## 1. Hiểu đúng các ranh giới kiểm thử

Một HTTP request thật đi qua nhiều lớp, thường theo luồng:

```text
HTTP client → cổng TCP của embedded server → Servlet filters
            → DispatcherServlet → handler mapping / argument resolver
            → Controller → Service → các dependency khác
            → message converter / exception handler → HTTP response
```

Có thể kiểm thử ở những điểm khác nhau của luồng này:

| Phương án | Cách thực thi | Nạp Spring context? | Mở cổng HTTP? | Chứng minh tốt nhất |
| --- | --- | --- | --- | --- |
| Gọi phương thức controller trực tiếp | Tự tạo object, gọi method như Java thông thường | Không | Không | Nhánh xử lý và tương tác dependency của controller |
| `MockMvcBuilders.standaloneSetup(...)` | Tạo controller thủ công, dựng bộ phận MVC mặc định/cấu hình tường minh quanh controller | Không cần | Không | Request mapping, binding, JSON, status và advice mà bài test đã cấu hình |
| `MockMvcBuilders.webAppContextSetup(...)` | Dùng `WebApplicationContext` thật cùng hệ thống cấu hình Spring MVC | Có | Không | Tích hợp controller với cấu hình MVC, bean, resolver, converter, interceptor/filter được đưa vào |
| Spring Boot `@WebMvcTest` + `MockMvc` | Nạp lát cắt ứng dụng phục vụ Spring MVC, mock collaborator cần thiết | Có, giới hạn ở web slice | Không | Web layer với cấu hình mà slice thực sự nạp |
| Spring Boot `@SpringBootTest` + `@AutoConfigureMockMvc` | Nạp application context rộng hơn; gửi request mô phỏng qua MVC | Có, gần/toàn bộ ứng dụng theo cấu hình | Không | Tương tác nhiều bean thật, MVC và cấu hình ứng dụng, nhưng không kiểm tra mạng |
| `@SpringBootTest(webEnvironment = RANDOM_PORT)` + HTTP client | Khởi động embedded server, gửi request đến địa chỉ localhost/cổng thật | Có | **Có** | Đường truyền HTTP thật, container/Servlet integration, serialization và cấu hình runtime |

**Điểm quyết định:** `MockMvc` mô phỏng request/response Servlet và thực thi nhiều bước xử lý MVC, nhưng **không mở socket**. Vì vậy status 200 từ `MockMvc` không tương đương với chứng cứ rằng server có thể nhận một HTTP request qua mạng.

Cụm từ **“kiểm thử hạ tầng”** ở đây chủ yếu nói đến hạ tầng *web* (Spring MVC configuration, filter, converter, exception resolver, dependency injection), không tự động có nghĩa là kiểm thử PostgreSQL, Redis hoặc dịch vụ bên ngoài. Những dependency ấy chỉ được kiểm thử thật khi bài test thực sự khởi động/kết nối chúng.

## 2. Mẫu ứng dụng chung cho các cấp kiểm thử

Giả sử project Spring Boot 3.3, Java 17+, có `spring-boot-starter-web` và `spring-boot-starter-test`, dùng JUnit Jupiter. Đặt các lớp sau trong package `com.example.demo`. Dùng cùng một endpoint `GET /api/greetings/{name}` để thấy chính xác lớp nào đã được kiểm tra.

Tệp `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

Tệp `src/main/java/com/example/demo/GreetingService.java`:

```java
package com.example.demo;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    public String greet(String name) {
        return "Xin chào, " + name;
    }
}
```

Tệp `src/main/java/com/example/demo/GreetingController.java`:

```java
package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {
    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/{name}")
    public GreetingDto greet(@PathVariable String name) {
        return new GreetingDto(greetingService.greet(name));
    }

    public record GreetingDto(String message) {}
}
```

Khi ứng dụng chạy, request `GET /api/greetings/An` trả HTTP 200 với JSON có trường `message` là `"Xin chào, An"`. Record giúp mô tả dữ liệu response rõ ràng; việc chuyển nó thành JSON do MVC/Jackson thực hiện, **không phải** do phương thức `greet` tự tạo chuỗi JSON.

## 3. Cấp 1 — kiểm thử controller độc lập như một object Java

Tệp `src/test/java/com/example/demo/GreetingControllerUnitTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GreetingControllerUnitTest {
    @Test
    void delegatesNameToService() {
        GreetingService service = mock(GreetingService.class);
        when(service.greet("An")).thenReturn("Xin chào, An");

        GreetingController controller = new GreetingController(service);
        GreetingController.GreetingDto result = controller.greet("An");

        assertEquals("Xin chào, An", result.message());
        verify(service).greet("An");
    }
}
```

Bài test tạo controller với `new`, cung cấp mock service và gọi method trực tiếp. Nó chứng minh controller truyền tên vào service và trả kết quả mong muốn. Không có Spring context, `DispatcherServlet`, URL matching, JSON converter hoặc filter nào chạy. Nếu gõ sai `@GetMapping("/...")`, bài test này vẫn có thể xanh.

**Khi dùng:** kiểm tra nhánh quyết định, logic điều phối hoặc tương tác collaborator. Với controller chỉ ủy quyền một dòng, giá trị tăng thêm của unit test thường nhỏ; hãy ưu tiên kiểm tra behavior có rủi ro thật và bổ sung một test kiểm chứng HTTP contract ở cấp MVC.

## 4. Cấp 2a — MockMvc standalone: chạy Spring MVC trong bộ nhớ nhưng tự chọn cấu hình

Tệp `src/test/java/com/example/demo/GreetingControllerStandaloneTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GreetingControllerStandaloneTest {
    @Test
    void mapsHttpRequestAndSerializesResponse() throws Exception {
        GreetingService service = mock(GreetingService.class);
        when(service.greet("An")).thenReturn("Xin chào, An");

        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new GreetingController(service))
                .build();

        mvc.perform(get("/api/greetings/An"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xin chào, An"));
    }
}
```

`standaloneSetup` không nạp application context. Nó dựng đủ thành phần MVC để xử lý một mock request, vì vậy kiểm tra được mapping từ đường dẫn sang handler, giá trị `@PathVariable` và phản hồi JSON trong cấu hình test. Tuy nhiên, custom `@ControllerAdvice`, `WebMvcConfigurer`, converter, validator, filter hoặc bảo mật của ứng dụng **không tự nhiên xuất hiện**: hãy đăng ký chúng trong builder khi chính chúng thuộc phạm vi cần kiểm thử.

Đây không đơn thuần là unit test gọi method, cũng chưa phải test ứng dụng thật. Nó là kiểm thử web behavior có phạm vi được dựng bằng tay, thường chạy nhanh và giúp cô lập controller.

## 5. Cấp 2b — kiểm thử cấu hình MVC/ứng dụng: context thật nhưng request giả

### 5.1. Spring Framework: webAppContextSetup

Khi đã có `WebApplicationContext` do Spring TestContext nạp, có thể tạo:

```java
// Đoạn trích dùng bên trong bài test đã được cấu hình WebApplicationContext.
MockMvc mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
```

Ở đây `webApplicationContext` phải là context được tạo từ cấu hình MVC mong muốn. `webAppContextSetup` lấy mapping, resolver, converter và nhiều thành phần đã đăng ký từ chính context đó, nhờ vậy phát hiện lỗi tích hợp mà standalone thiếu. Dù vậy, test vẫn chạy request mô phỏng, **không** kết nối TCP tới embedded server. Có thể dùng Spring TestContext với `@WebAppConfiguration`, `@ContextConfiguration` hoặc các annotation cấu hình tương đương trong ứng dụng Spring Framework thuần.

### 5.2. Spring Boot: @WebMvcTest là một lát cắt web

Tệp `src/test/java/com/example/demo/GreetingControllerWebSliceTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GreetingController.class)
class GreetingControllerWebSliceTest {
    @Autowired
    MockMvc mvc;

    @MockBean
    GreetingService service;

    @Test
    void returnsGreetingThroughWebLayer() throws Exception {
        when(service.greet("An")).thenReturn("Xin chào, An");

        mvc.perform(get("/api/greetings/An"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xin chào, An"));
    }
}
```

`@WebMvcTest` là tiện ích **Spring Boot**, không phải annotation cốt lõi của Spring Framework. Nó tạo một context giới hạn vào web layer, tự cấu hình `MockMvc`, và thường dùng mock để thay collaborator bên ngoài slice. Trong **Spring Boot 3.3**, `@MockBean` thuộc `org.springframework.boot.test.mock.mockito`; ở các phiên bản Spring/Boot mới hơn có API `@MockitoBean` khác, nên phải đối chiếu version trước khi sao chép import.

Bài test kiểm tra MVC được nạp trong slice, nhưng không chứng minh service/repository thật được nối đúng. Nếu thêm security vào classpath, hãy xác định filter chain, user/role/CSRF dự kiến trong test thay vì tắt filter chỉ để được mã 200; slice có thể nạp các cấu phần bảo mật phù hợp.

### 5.3. @SpringBootTest + @AutoConfigureMockMvc: context rộng, request vẫn mock

Tệp `src/test/java/com/example/demo/GreetingControllerContextTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GreetingControllerContextTest {
    @Autowired
    MockMvc mvc;

    @Test
    void usesRealServiceBeanThroughMvc() throws Exception {
        mvc.perform(get("/api/greetings/An"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xin chào, An"));
    }
}
```

`@SpringBootTest` mặc định có `webEnvironment = MOCK`: tạo application context với web environment mô phỏng mà **không khởi động embedded server**. `@AutoConfigureMockMvc` cho phép kiểm thử `DispatcherServlet` dựa vào bean, MVC mapping, converter và các cấu hình được nạp. Khác với ví dụ slice, service ở đây là bean thật; nếu ứng dụng có datasource/messaging/bean khởi tạo khó khăn, việc nạp context đầy đủ cũng mang theo chi phí và phụ thuộc đó. Chỉ coi database hoặc dịch vụ ngoài là đã được kiểm thử khi test dùng hệ thống đó thật sự.

**So sánh hai cách dùng MockMvc:** standalone là hạ tầng được dựng bằng tay; `@WebMvcTest` là web slice từ Spring context; `@SpringBootTest` là context rộng hơn. Chúng đều có thể trả cùng JSON trong ví dụ, nhưng **ý nghĩa bằng chứng và nguyên nhân thất bại khác nhau**.

## 6. Cấp 3 — HTTP thật qua embedded server

Tệp `src/test/java/com/example/demo/GreetingControllerLiveHttpTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class GreetingControllerLiveHttpTest {
    @Autowired
    TestRestTemplate client;

    @Test
    void answersActualHttpRequest() {
        ResponseEntity<GreetingController.GreetingDto> response =
                client.getForEntity(
                        "/api/greetings/An",
                        GreetingController.GreetingDto.class
                );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Xin chào, An", response.getBody().message());
    }
}
```

Spring Boot khởi động embedded server trên một cổng khả dụng ngẫu nhiên. `TestRestTemplate` do môi trường kiểm thử cung cấp gửi request HTTP đến server đó và biết cổng đang chạy. Do đó bài test đi qua client HTTP, kết nối mạng loopback, servlet container, filter chain, MVC và các bean ứng dụng. Muốn tự tạo client khác (ví dụ `RestClient` hoặc `WebTestClient.bindToServer()`), hãy dùng base URL trỏ đến localhost và cổng được cấp; cấu hình client quyết định có thực sự gửi request mạng hay không.

**Một sai lầm dễ gặp:** khai báo `@SpringBootTest(webEnvironment = RANDOM_PORT)` nhưng chỉ gọi `mockMvc.perform(...)` rồi tuyên bố “đã kiểm thử HTTP thật”. Chỉ việc khởi động server không chứng minh request được gửi đến socket; chính **client/đường thực thi của lời gọi** mới xác định ranh giới được kiểm thử.

Máy chủ thật giúp kiểm tra những vấn đề khó tái hiện với mock Servlet, chẳng hạn behavior phụ thuộc container, error page, cấu hình cổng và một phần HTTP transport. Nó vẫn **không tự động** kiểm tra reverse proxy, TLS termination, DNS, load balancer, firewall, production database hoặc hạ tầng cloud. Các phần đó cần môi trường kiểm thử tích hợp riêng.

Lưu ý transaction: nếu test method đánh dấu `@Transactional` khi dùng server thật, client và server chạy ở các thread/transaction khác nhau; rollback transaction của test **không tự rollback** thay đổi dữ liệu đã commit trong request phía server. Hãy dùng cơ chế dọn dữ liệu, database dùng riêng cho test hoặc transaction phù hợp phía ứng dụng.

## 7. So sánh cùng một lỗi và cách chọn cấp kiểm thử

Giả sử developer vô tình đổi `@GetMapping("/{name}")` thành `@GetMapping("/fixed")` nhưng giữ nguyên nội dung phương thức:

- **Gọi controller trực tiếp** có thể vẫn xanh vì không nhìn annotation định tuyến.
- **MockMvc standalone**, **web slice** và **full-context MockMvc** sẽ phát hiện `GET /api/greetings/An` không còn đi đến đúng handler.
- **HTTP thật** cũng phát hiện lỗi; thêm nữa có thể bắt lỗi do server/container/transport mà MockMvc không tái hiện.

Giả sử một global `@ControllerAdvice` được đăng ký qua cấu hình ứng dụng nhưng test standalone quên thêm vào builder. Test standalone có thể không chứng minh error body/status giống runtime; context-based test sẽ có cơ hội kiểm tra đúng advice **nếu advice thực sự được nạp**. Nếu dùng security filter trong production, phải xét filter chain của chính mỗi chế độ; không mặc nhiên suy ra “MockMvc kiểm thử security” chỉ vì dùng `mockMvc.perform`.

| Câu hỏi cần trả lời | Điểm bắt đầu phù hợp | Test bổ sung khi cần |
| --- | --- | --- |
| Controller truyền argument và chọn nhánh đúng không? | Gọi method trực tiếp với mock/stub | MockMvc nếu URL và contract quan trọng |
| Path, query, header, validation, converter, JSON, status có đúng không? | MockMvc standalone hoặc `@WebMvcTest` | Context-based MockMvc khi phụ thuộc cấu hình thực tế |
| `@ControllerAdvice`, filters, interceptors, WebMvcConfigurer, Security hoạt động cùng nhau không? | `webAppContextSetup` / `@WebMvcTest` với cấu hình phù hợp | `@SpringBootTest + @AutoConfigureMockMvc` nếu cần bean rộng hơn |
| Controller kết nối service/repository/config thực của ứng dụng không? | `@SpringBootTest + @AutoConfigureMockMvc` | HTTP thật nếu transport/container cũng quan trọng |
| Endpoint thực sự nhận HTTP qua port và trả response được không? | `@SpringBootTest(RANDOM_PORT)` + HTTP client | Kiểm thử môi trường triển khai cho proxy/TLS/infra thật |

Không có “cấp càng cao càng tốt” cho mọi test. Một bộ test hợp lý thường nhiều test nhanh ở ranh giới nhỏ để chỉ ra lỗi rõ ràng, thêm test context kiểm chứng wiring và số ít test HTTP thật cho những luồng đại diện hoặc rủi ro cao. Cân nhắc chi phí khởi tạo Spring context, container và dependency bên ngoài. Spring TestContext có cache context dùng lại giữa các bài test có cấu hình tương đương, nhưng không loại bỏ hoàn toàn chi phí tích hợp.

## 8. Chạy và đọc kết quả

Với project Spring Boot 3.3 đã khai báo `spring-boot-starter-web` và `spring-boot-starter-test`, đặt mỗi class vào đúng package/tệp như trên rồi chạy:

```bash
./gradlew test
```

Trên Windows PowerShell có thể dùng `.\gradlew.bat test`. Các kiểm thử mẫu khẳng định:

1. Unit: method trả chuỗi mong đợi và đã gọi service với đối số đúng.
2. Standalone: mock MVC request khớp đường dẫn và JSON response.
3. Web slice: MVC slice dùng mock service, trả status/JSON đúng.
4. Full context: bean service thật được wiring qua MVC và trả response đúng.
5. Live HTTP: HTTP client nhận status 200 và giải mã JSON do embedded server trả về.

Đó là **kỳ vọng từ source ví dụ**, không phải log thực thi được cung cấp sẵn. Nếu test không chạy, kiểm tra dependency/version, cấu hình security hoặc context trước khi kết luận chức năng controller sai. Khi bài học dùng Spring Framework thuần không có Spring Boot, thay các annotation Boot (`@WebMvcTest`, `@SpringBootTest`) bằng cấu hình Spring TestContext phù hợp.

## 9. Lỗi phổ biến và checklist kiểm chứng

- **Nhầm unit test với HTTP test:** gọi trực tiếp `controller.greet(...)` không xác minh route annotation, binding hay serialization.
- **Nhầm MockMvc với server thật:** MockMvc xử lý request Servlet giả trong bộ nhớ; không chứng minh cổng mạng mở.
- **Nhầm context đầy đủ với integration đầy đủ:** một context có thể chứa bean/mock hoặc datasource giả; hãy kiểm tra dependency đang thật hay giả.
- **Standalone bỏ sót cấu hình:** nếu behavior phụ thuộc exception handler, custom JSON module, interceptor hoặc security filter, cần tự cấu hình builder hoặc chuyển sang context-based test.
- **Lạm dụng @WebMvcTest cho business logic:** collaborator thường mock nên bài test không chứng minh business/service thật đúng.
- **Dùng RANDOM_PORT nhưng không gửi request mạng:** chọn HTTP client thực sự hướng về server, không gọi MockMvc để đại diện cho transport.
- **Test lỗi quá phụ thuộc vào JSON string nguyên văn:** nếu thứ tự trường/format không là hợp đồng công khai, dùng JSONPath hay parse body để kiểm tra giá trị có ý nghĩa.
- **Test HTTP thật dùng dữ liệu lâu dài:** cô lập state, quản lý fixture và dọn dữ liệu; đặc biệt cẩn trọng với transaction rollback khi client/server ở thread riêng.

Trước khi xem một test là đủ, hãy hỏi: **(1)** request đã đi qua lớp nào? **(2)** Spring context có được nạp không và gồm những bean nào? **(3)** dependency nào là mock? **(4)** TCP/HTTP thật có xảy ra không? **(5)** assertion đang chứng minh status, body, authorization, validation hay business outcome nào? Câu trả lời phải dựa trên setup và lời gọi thực tế của test, không chỉ tên annotation.

### Tài liệu tham khảo

- Spring Framework — MockMvc overview: https://docs.spring.io/spring-framework/reference/testing/mockmvc/overview.html
- Spring Framework — MockMvc setup options: https://docs.spring.io/spring-framework/reference/6.2/testing/mockmvc/setup-options.html
- Spring Framework — WebTestClient setup (mock hoặc live server): https://docs.spring.io/spring-framework/reference/testing/webtestclient.html
- Spring Boot 3.3 — Testing Spring Boot Applications: https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-testing-002">MockMvc: standaloneSetup và webAppContextSetup</a>

<details>
<summary>Xem chi tiết</summary>

# MockMvc: `standaloneSetup` và `webAppContextSetup`

## 1. MockMvc kiểm thử điều gì?

`MockMvc` là công cụ Spring MVC Test (`spring-test`) dùng để tạo HTTP request mô phỏng, đưa request qua `DispatcherServlet`, rồi kiểm tra response **mà không khởi chạy server HTTP thật trên một cổng mạng**. Nó tạo `MockHttpServletRequest` và `MockHttpServletResponse`, vận hành handler mapping, data binding, message conversion, exception resolution và các thành phần MVC được cấu hình.

```text
MockMvc.perform(GET/POST/PUT/...)
    │
    ▼
MockHttpServletRequest
    │
    ▼
[Filter nếu được thêm vào MockMvc]
    │
    ▼
DispatcherServlet
    ├─ HandlerMapping
    ├─ HandlerAdapter / argument resolvers
    ├─ Controller → service hoặc mock service
    ├─ HandlerExceptionResolver / @ControllerAdvice
    └─ HttpMessageConverter
    │
    ▼
MockHttpServletResponse
    │
    ▼
andExpect(status, headers, JSON, model, view...)
```

Ví dụ cơ bản (chỉ minh họa; các class xuất hiện trong chương trình hoàn chỉnh phía dưới):

```java
mockMvc.perform(get("/api/greetings/{name}", "Ada"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.message").value("Xin chào, Ada"));
```

- `perform(...)` gửi request mock.
- `get(...)` / `post(...)` / `put(...)` / `delete(...)` mô tả method, path, query, headers, cookies, body.
- `andExpect(...)` kiểm tra status, JSONPath, content type, headers, forwarded/redirected URL hoặc model/view.
- `andDo(print())` giúp chẩn đoán request và response; đừng in payload/headers nhạy cảm.
- `andReturn()` trả `MvcResult` để kiểm tra chuyên sâu.

**Hai đường dựng MockMvc cần hiểu rõ**:

```text
MockMvcBuilders.standaloneSetup(controllerInstance)
    → MVC infrastructure tối thiểu, cấu hình thủ công
    → không load application context thật

MockMvcBuilders.webAppContextSetup(webApplicationContext)
    → dùng các bean/cấu hình MVC trong Spring web context
    → context được Spring TestContext/Boot khởi tạo trước
```

Cả hai dùng cùng DSL thực thi request, nhưng kiểm thử phạm vi khác nhau. Đó là nội dung trọng tâm của bài.

## 2. `standaloneSetup` — kiểm thử controller cô lập

`MockMvcBuilders.standaloneSetup(Object... controllers)` nhận trực tiếp instance controller (hoặc class để tạo instance trong các trường hợp phù hợp), tự khởi tạo hạ tầng Spring MVC cần thiết để chạy những controller này.

```java
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

GreetingService service = new GreetingService();
GreetingController controller = new GreetingController(service);

MockMvc mockMvc = MockMvcBuilders
    .standaloneSetup(controller)
    .setControllerAdvice(new ApiErrorAdvice())
    .build();
```

Các class được định nghĩa ở ví dụ runnable phần 7. Với cách này:

1. **Không tải Spring application context** để component scan toàn bộ `@Service`, `@Repository`, `@Configuration` hay tự wiring controller. Dependencies do test cung cấp thông qua constructor/setter, thường dùng fake hoặc Mockito mock.
2. Builder tạo cấu hình Spring MVC cơ bản, gồm khả năng dispatch, request mapping, binding và convert response, nên bài test vẫn kiểm tra cơ chế MVC thật thay vì chỉ gọi `controller.greet("Ada")`.
3. `@ControllerAdvice` toàn cục **không được auto-discover qua component scan**; phải dùng `.setControllerAdvice(adviceInstance)` nếu muốn kiểm tra handler tương ứng.
4. MVC configuration riêng của production (converter tự tùy biến, global validator, interceptor, locale resolver, custom argument resolver, filter/Security) có thể **không hiện diện**. Khi chúng tác động đến test, thiết lập tương ứng trên builder.
5. Khởi tạo ít tài nguyên, thích hợp nhiều biến thể input, status và body của controller.

Ví dụ dùng Mockito để cô lập tầng service:

```java
GreetingService mockService = Mockito.mock(GreetingService.class);
Mockito.when(mockService.greetingFor("Ada"))
    .thenReturn("Xin chào, Ada");

MockMvc mvc = MockMvcBuilders
    .standaloneSetup(new GreetingController(mockService))
    .build();

mvc.perform(get("/api/greetings/{name}", "Ada"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.message").value("Xin chào, Ada"));

Mockito.verify(mockService).greetingFor("Ada");
```

Đây là **snippet minh họa**, cần import `Mockito` và các static helper MockMvc/JUnit như ở ví dụ hoàn chỉnh. Việc mock service giúp kiểm tra controller độc lập với database; không xác nhận implementation/wiring của service thật.

### Cấu hình thêm với `StandaloneMockMvcBuilder`

Các hook có ích:

- `setControllerAdvice(...)`: đăng ký các exception handlers, binding advice cần kiểm thử.
- `setMessageConverters(...)`: chỉ định converter HTTP, ví dụ cấu hình Jackson khác mặc định.
- `setValidator(...)`: validator sử dụng khi kiểm tra `@Valid`/binding.
- `setConversionService(...)`: conversion rules cho path/query/body.
- `setCustomArgumentResolvers(...)` / `setCustomReturnValueHandlers(...)`: những cơ chế method arguments/return tùy biến.
- `addInterceptors(...)`: chạy những MVC interceptors đã chọn.
- `defaultRequest(...)`: request properties áp dụng mặc định.
- `alwaysExpect(...)` / `alwaysDo(...)`: expectation/hook chung của test suite.

Ví dụ:

```java
MockMvc mvc = MockMvcBuilders
    .standaloneSetup(new GreetingController(new GreetingService()))
    .setControllerAdvice(new ApiErrorAdvice())
    .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
    .build();
```

Đoạn này **minh họa builder**, phải import `MediaType` và static `get`. Khi dùng `alwaysExpect(status().isOk())` sẽ làm test HTTP 404 cố ý thất bại, vì vậy chỉ đặt global expectation khi tất cả case có chung điều kiện.

**Không nhầm:** `standaloneSetup` vẫn dùng MVC infrastructure được dựng cho test. Nó gần với unit test về phạm vi/controller dependencies, nhưng không phải pure unit test của một Java method.

## 3. `webAppContextSetup` — kiểm tra Spring MVC configuration trong context

`MockMvcBuilders.webAppContextSetup(WebApplicationContext)` dùng một Spring **web application context đã khởi tạo** (có `ServletContext`). `DispatcherServlet` khám phá controllers, handler mappings, converters, validators, exception resolvers, `@ControllerAdvice` và những bean MVC có trong context. Việc này giúp phát hiện lỗi component scan, bean injection và cấu hình mà standalone có thể không nhìn thấy.

Ví dụ với Spring Boot:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class GreetingWebContextTest {
    @Autowired
    WebApplicationContext webContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    @Test
    void responds() throws Exception {
        mockMvc.perform(get("/api/greetings/{name}", "Ada"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Xin chào, Ada"));
    }
}
```

Đây là **mã minh họa ngắn**, chưa gồm imports và model; phần 7 có chương trình đầy đủ. `@SpringBootTest(webEnvironment=MOCK)` trong Spring Boot Servlet/MVC khởi động application context thích hợp nhưng **không mở cổng server thật**. Khi test class nằm dưới package gốc có `@SpringBootApplication`, Boot tìm cấu hình khởi tạo tương ứng.

Khác với standalone:

- Controller/service/advice được Spring dựng và inject như bean. Nếu bean thiếu, context có thể không khởi động.
- Spring MVC configuration thực được áp dụng (ví dụ `WebMvcConfigurer`, exception resolvers, message converters do context cấu hình).
- Chi phí load context thường cao hơn, có thể kích hoạt database/messaging/application startup logic nếu ứng dụng khai báo.
- Spring TestContext **cache application context** giữa các test có cấu hình tương thích. Tránh `@DirtiesContext` hoặc đổi profile/property mỗi lớp một cách không cần thiết, vì dễ vô hiệu cache.

### TestContext thuần Spring Framework

Không dùng Boot cũng có thể kết hợp JUnit 5 và Spring:

```java
@SpringJUnitWebConfig(classes = TestWebConfig.class)
class MvcContextTest {
    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }
}
```

Đây là **mẫu minh họa, không phải file độc lập**: `TestWebConfig` phải được tạo với `@EnableWebMvc`, controller và dependencies cần thiết. `@SpringJUnitWebConfig` gộp `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration` và `@WebAppConfiguration`. Nếu dùng các annotation rời, cần cấu hình đầy đủ cả context và web environment, không chỉ `@ContextConfiguration`.

## 4. Bảng so sánh lựa chọn

| Tiêu chí | `standaloneSetup` | `webAppContextSetup` |
| --- | --- | --- |
| Đầu vào | Controller instance/class cụ thể | `WebApplicationContext` đã khởi tạo |
| ApplicationContext | Không tải application context | Dùng context từ Spring TestContext/Boot |
| DI service/repository | Test chủ động tạo/mock dependency | Bean do Spring quản lý và inject |
| `@ControllerAdvice` | Cần đăng ký khi áp dụng qua `setControllerAdvice` | Khám phá các advice bean trong context |
| Converter/validator/interceptor | MVC mặc định, tùy chỉnh trên builder | Dùng các MVC bean/cấu hình được khai báo |
| Security/filters | Phải lắp đúng khi cần | Cũng cần đảm bảo filter chain thực sự vào MockMvc |
| Khả năng phát hiện wiring lỗi | Hạn chế | Tốt hơn, tùy mức độ của context được load |
| Thời gian chạy | Thường nhanh, setup gọn | Có chi phí khởi động context, được cache |
| Network/server port thật | Không | Không |
| Mục tiêu chính | Contract controller, edge cases | MVC configuration và collaboration giữa beans |

**Lựa chọn thực tế:**

- Nếu kiểm thử nhiều dạng request của một controller và cần service mock: `standaloneSetup` rất phù hợp, vì kiểm soát rõ dependency và chạy nhanh.
- Nếu muốn xác nhận `@ControllerAdvice`/`WebMvcConfigurer`, custom converters, filters, wiring và behavior theo cấu hình app: `webAppContextSetup` phù hợp hơn.
- Trong dự án lớn, kết hợp standalone cho phần lớn test controller và context tests đại diện để bảo vệ MVC configuration.
- Spring Boot `@WebMvcTest` tạo **MVC slice context** và thường auto-inject `MockMvc`. Nó có phạm vi ít hơn `@SpringBootTest` full context và **không đồng nghĩa** bản thân builder `webAppContextSetup`, dù có thể dùng Spring MVC Test infrastructure.

## 5. Các matcher và cách kiểm thử chính xác

Lời gọi thường dùng:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

mockMvc.perform(get("/api/greetings/{name}", "Ada")
        .accept(MediaType.APPLICATION_JSON)
        .header("X-Request-Id", "test-1"))
    .andExpect(status().isOk())
    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
    .andExpect(jsonPath("$.message").value("Xin chào, Ada"));

mockMvc.perform(post("/api/books")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Spring\"}"))
    .andExpect(status().isCreated());

mockMvc.perform(get("/absent"))
    .andExpect(status().isNotFound());
```

Hai request cuối **chỉ minh họa API matcher**: cần có endpoints tương ứng mới chạy được. `contentType(MediaType.APPLICATION_JSON)` xác định kiểu body của request; `accept(MediaType.APPLICATION_JSON)` là media type response mong muốn. Với POST JSON, chỉ gọi `.content(...)` chưa chắc đủ để framework chọn converter phù hợp.

Các expectation khác:

- `header().string("Location", "...")`: response headers.
- `content().json("{\"message\":\"...\"}")`: JSON structure theo phương pháp matcher.
- `handler().handlerType(GreetingController.class)` và `handler().methodName("greet")`: mapping có trúng controller/method dự kiến không.
- `model().attributeExists("product")` / `view().name("products")`: MVC render view.
- `redirectedUrl("/login")` / `forwardedUrl("/...")`: redirect/forward.
- `request().asyncStarted()`, `asyncDispatch(...)`: controller với Spring MVC async types.

**MockMvc không kiểm thử HTTP network thật:** request đi thẳng vào Servlet MVC infrastructure. Nếu muốn xác minh TCP, TLS, reverse proxy, servlet container, HTTP keepalive hay browser behavior, cần test end-to-end qua server thật, ví dụ `@SpringBootTest(webEnvironment = RANDOM_PORT)` và HTTP client.

## 6. Security, filter và giới hạn của mock servlet

**Điểm dễ gây false positive:** `webAppContextSetup(context).build()` có context MVC nhưng không mặc định đảm bảo *mọi servlet filter runtime* đã được thêm vào mock chain. Với custom filters, có thể cần:

```java
MockMvc mockMvc = MockMvcBuilders
    .webAppContextSetup(context)
    .addFilters(auditFilter)
    .build();
```

Đoạn là **mã minh họa**: `auditFilter` là bean `Filter` có trong context. Với Spring Security, khi phụ thuộc `spring-security-test`, có thể dùng `.apply(springSecurity())` (tùy ứng dụng cần `springSecurityFilterChain` thích hợp). Khi Spring Boot dùng `@AutoConfigureMockMvc`, Boot có logic cấu hình thêm filters phù hợp; cần xác nhận config đang chạy theo test.

`@WithMockUser` tạo security context phục vụ test, không thay thế việc bảo đảm security filter chain hợp lệ khi test thực sự muốn xác minh authorization và CSRF. Một GET trả 200 khi filter bị bỏ qua không chứng minh endpoint được bảo vệ.

Tương tự, MockMvc không tự mô phỏng việc gateway cắt kết nối, TLS handshake, HTTP/2 multiplexing, proxy header rewriting, socket timeout, hay các mức giới hạn tải thực. Hãy lựa chọn test theo rủi ro muốn kiểm tra.

## 7. Ví dụ Java 17+ hoàn chỉnh, chạy hai cách setup

Bài thực hành dùng **Java 17+, Spring Boot 3.3.13, Spring MVC, JUnit Jupiter**. Cả hai test class cùng kiểm tra một controller và exception advice: standalone tự dựng collaborator/advice; web context lấy từ Spring. Chạy `gradle test` mà không mở HTTP server hoặc cần database.

Cấu trúc:

```text
mockmvc-two-setups/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/demo/
    │   ├── GreetingApplication.java
    │   ├── GreetingService.java
    │   ├── GreetingController.java
    │   └── ApiErrorAdvice.java
    └── test/java/demo/
        ├── GreetingStandaloneTest.java
        └── GreetingWebContextTest.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'mockmvc-two-setups'
```

**`build.gradle`** — JDK 17+, Gradle 8.x (không cần plugin Spring Boot khi chỉ chạy test):

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web:3.3.13'
    testImplementation 'org.springframework.boot:spring-boot-starter-test:3.3.13'
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events 'passed', 'failed'
    }
}
```

Các starter mang tới Spring MVC/Jackson và Spring Test/JUnit/Mockito; không cần tạo `bootJar` để học hai builder.

**`src/main/java/demo/GreetingApplication.java`**

```java
package demo;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GreetingApplication {
}
```

Lớp cấu hình gốc đủ để `@SpringBootTest` tìm và khởi tạo application context trong package `demo`. Không cần phương thức `main` vì ví dụ này chỉ chạy test.

**`src/main/java/demo/GreetingService.java`**

```java
package demo;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    public String greetingFor(String name) {
        if ("missing".equals(name)) {
            throw new MissingNameException(name);
        }
        return "Xin chào, " + name;
    }
}

class MissingNameException extends RuntimeException {
    MissingNameException(String name) {
        super("Không tìm thấy tên: " + name);
    }
}
```

Service không có DB và có hai hành vi: trả greeting, hoặc ném lỗi nghiệp vụ khi name là `missing`.

**`src/main/java/demo/GreetingController.java`**

```java
package demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {
    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/{name}")
    public GreetingResponse greet(@PathVariable("name") String name) {
        return new GreetingResponse(greetingService.greetingFor(name));
    }
}

record GreetingResponse(String message) {}
```

Response dùng record, Jackson chuyển thành JSON với property `message`.

**`src/main/java/demo/ApiErrorAdvice.java`**

```java
package demo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorAdvice {
    @ExceptionHandler(MissingNameException.class)
    public ResponseEntity<ErrorResponse> handleMissing(MissingNameException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage()));
    }
}

record ErrorResponse(String error) {}
```

Handler ánh xạ lỗi `MissingNameException` thành HTTP 404 với body JSON `{"error":"Không tìm thấy tên: missing"}`.

**`src/test/java/demo/GreetingStandaloneTest.java`**

```java
package demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GreetingStandaloneTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        GreetingController controller =
            new GreetingController(new GreetingService());

        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiErrorAdvice())
            .build();
    }

    @Test
    void returnsGreeting() throws Exception {
        mvc.perform(get("/api/greetings/{name}", "Ada"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Xin chào, Ada"));
    }

    @Test
    void mapsBusinessErrorWithExplicitAdvice() throws Exception {
        mvc.perform(get("/api/greetings/{name}", "missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Không tìm thấy tên: missing"));
    }
}
```

Lớp này **không dùng `@SpringBootTest`**. `new GreetingService()` và `new ApiErrorAdvice()` do test trực tiếp tạo, giúp nhìn thấy rõ controller dependencies và exception handler được thiết lập ở đâu.

**`src/test/java/demo/GreetingWebContextTest.java`**

```java
package demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class GreetingWebContextTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void usesSpringManagedControllerAndService() throws Exception {
        mvc.perform(get("/api/greetings/{name}", "Ada"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Xin chào, Ada"));
    }

    @Test
    void discoversAdviceInSpringContext() throws Exception {
        mvc.perform(get("/api/greetings/{name}", "missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Không tìm thấy tên: missing"));
    }
}
```

Ở web context setup, Spring tìm `GreetingController`, `GreetingService`, `ApiErrorAdvice` bằng component scanning từ package `demo`, sau đó inject constructor. Test xác nhận cả HTTP contract **và** thành phần Spring được nối đúng.

Từ thư mục dự án, dùng JDK 17+ và Gradle 8.x:

```bash
gradle test
```

Lệnh chạy **4 phương thức `@Test`** (hai standalone và hai web context). Khi pass, Gradle báo `BUILD SUCCESSFUL` và tạo báo cáo tại `build/reports/tests/test/index.html`. Các dòng log chi tiết có thể khác nhau theo Gradle, hệ điều hành và test runner; không cần dự đoán toàn bộ stdout.

Nếu muốn chạy qua Gradle Wrapper, có thể tạo wrapper bằng Gradle cài sẵn rồi chạy `./gradlew test` (Linux/macOS) hoặc `gradlew.bat test` (Windows).

## 8. Mở rộng kiểm thử theo mục tiêu

**Controller cần validation thật:** Nếu code dùng `@Valid`, Jakarta Bean Validation và custom validator, standalone cần đảm bảo validator/converter tương đương runtime. Web context test giúp phát hiện `Validator`/`@ControllerAdvice` bean bị cấu hình sai. Spring Boot application có thể cần dependency `spring-boot-starter-validation`; `spring-boot-starter-web` không phải bảo đảm toàn bộ Bean Validation API/implementation.

**Controller cần Security:** Với `spring-security-test`, cấu hình MockMvc đúng filter chain và user/CSRF phù hợp. `@WithMockUser` không tự làm filter xuất hiện nếu MockMvc builder không gắn bảo mật. Controller advice có thể xử lý domain exception nhưng authentication failure chủ yếu được xử lý tại filter chain; đừng nhầm hai tầng.

**Kiểm thử MVC async:** Với `Callable`, `DeferredResult` hay Spring MVC async return types, có thể assert `request().asyncStarted()` rồi gọi `asyncDispatch`:

```java
MvcResult pending = mvc.perform(get("/async"))
    .andExpect(request().asyncStarted())
    .andReturn();

mvc.perform(asyncDispatch(pending))
    .andExpect(status().isOk());
```

Đây là **snippet minh họa**, cần endpoint `/async` phù hợp. Không dùng API trên để suy đoán mọi Reactor `Flux` tương đương servlet async.

**Boot MVC test slice:** `@WebMvcTest(GreetingController.class)` thường giúp load web slice nhanh hơn full `@SpringBootTest` và inject `MockMvc` qua `@AutoConfigureMockMvc`. Dependency service của controller cần mock hoặc test bean tương ứng (Boot 3.3 dùng `@MockBean`; các version mới có `@MockitoBean`). Đây là lựa chọn trung gian giữa standalone và full context, khác nhiệm vụ trực tiếp của hai builder đang học.

**Test database/external API:** Standalone thích hợp mock collaborator; full context cũng có thể cần fake/embedded DB hoặc stub external service để ổn định. Đừng dùng request mock để xác nhận DB migration, network retry, cookie bảo mật tại proxy hoặc TLS handshake thành công.

## 9. Lỗi thường gặp khi lựa chọn và viết test

1. **Tưởng `standaloneSetup` tự component scan mọi bean.** Hãy tự truyền controller dependencies, advice và MVC customizations.
2. **Quên `setControllerAdvice`** trong standalone khiến service ném exception nhưng test nhận lỗi không được convert như runtime.
3. **Tự tạo controller bằng `new` trong web-context test.** Như vậy bỏ qua cơ chế dependency injection muốn kiểm chứng; hãy dùng các bean trong context.
4. **Quên `@WebAppConfiguration`/`@SpringJUnitWebConfig`** khi dùng Spring TestContext thuần, dẫn đến không có `WebApplicationContext` có `ServletContext`.
5. **Nghĩ `webAppContextSetup` chạy HTTP server thật.** Cả hai builder đều dùng mock servlet; cần `RANDOM_PORT` cho network integration.
6. **Không gắn filters/Security chain** khi test authorization, khiến assertion 200/403 mang nghĩa sai.
7. **Chỉ assert status 200** mà bỏ qua JSON, headers, content-type, xử lý exception.
8. **Đặt `alwaysExpect(status().isOk())`** trên builder dùng cả trường hợp muốn test 404/400.
9. **Gửi JSON request nhưng thiếu `Content-Type: application/json`**, dẫn đến 415 hoặc behavior khác runtime.
10. **Nhầm `@WebMvcTest` với full context**, rồi kỳ vọng service/repository được component scan tự động.
11. **Mock cả lớp đang cần kiểm chứng**, chẳng hạn mock `GreetingController` rồi coi như đã kiểm thử request mapping và serialization.
12. **Dùng `@DirtiesContext` tùy tiện** hoặc cấu hình mỗi lớp khác nhau làm context cache kém hiệu quả.
13. **So sánh toàn bộ JSON string cứng nhắc** khi có khác biệt thứ tự key/escaping; ưu tiên JSONPath hoặc JSON structural matching.
14. **Giả định MockMvc tái hiện network/Servlet container production**, trong khi các giới hạn TLS/proxy, connection, sockets nằm ngoài phạm vi test.

Khi case fail, xem `andDo(print())` và phân tích thứ tự: path/method → handler mapping → argument binding → controller/service exception → advice → converters → filters/Security → response assertions.

## 10. Câu hỏi tự kiểm tra

1. **`standaloneSetup(new Controller(...))` có tải `@Service` bằng Spring không?** → Không. Test tự tạo hoặc mock collaborator, rồi truyền vào controller.
2. **Standalone có tự thấy `@RestControllerAdvice` của production không?** → Không; cần `setControllerAdvice` hoặc cấu hình thủ công.
3. **`webAppContextSetup(context)` có dùng server socket?** → Không; nó dùng `DispatcherServlet` trên request/response giả lập.
4. **Phát hiện controller thiếu bean injection nên chọn gì?** → Web-context test có khả năng phát hiện hơn vì Spring thực sự tạo và wiring bean.
5. **Tại sao context test thường chậm hơn standalone?** → Cần load context, nhưng TestContext cache các context cùng cấu hình.
6. **Có `@SpringBootTest(MOCK)` mà quên Security filter trong MockMvc có nguy cơ gì?** → Authorization tests có thể cho kết quả sai; cần xác nhận filter chain được áp dụng.
7. **Khi nào nên dùng `@WebMvcTest`?** → Khi muốn MVC slice với một số controller/advice/converter, nhanh hơn load cả application.
8. **Muốn kiểm tra HTTPS/cổng thật, MockMvc có đủ không?** → Không; cần HTTP client tới server thật cùng môi trường phù hợp.
9. **Muốn kiểm tra JSON error 404 của controller, phải assert gì?** → HTTP 404 và body lỗi (ví dụ `$.error`), đồng thời bảo đảm advice được đăng ký đúng.

## 11. Tài liệu tham khảo

- Spring Framework — MockMvc configuring/setup: https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/setup.html
- Spring Framework — Setup options: https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/setup-options.html
- Spring Framework — MockMvc requests: https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/requests.html
- Spring Framework — MockMvc expectations: https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/expectations.html
- Spring Framework 6.1 — `MockMvcBuilders` Javadoc: https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/test/web/servlet/setup/MockMvcBuilders.html
- Spring Framework 6.1 — `StandaloneMockMvcBuilder` Javadoc: https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/test/web/servlet/setup/StandaloneMockMvcBuilder.html
- Spring Boot 3.3 — Testing Spring Boot Applications: https://docs.spring.io/spring-boot/docs/3.3.13/reference/html/features.html#features.testing


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-testing-003">Kiểm chứng mapping, tham số, header, status và nội dung JSON</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng mapping, tham số, header, status và nội dung JSON trong Spring Web

## 1. Mục tiêu kiểm thử một HTTP endpoint

Một endpoint Spring Web đúng không chỉ là phương thức Java trả về đối tượng đúng. Nó phải nhận **đúng HTTP method và URL**, ánh xạ **path variable, query parameter, request header và request body** vào tham số Java, tạo **status code**, **response headers** và **JSON body** đúng hợp đồng. Kiểm thử web xác minh các điều này qua HTTP request/response ở tầng Spring MVC hoặc Spring WebFlux.

Ví dụ hợp đồng cần bảo vệ:

~~~http
GET /api/books/7?lang=en
X-Tenant: school
Accept: application/json

HTTP/1.1 200 OK
Content-Type: application/json
X-Tenant-Echo: school

{"id":7,"title":"Spring","language":"en"}
~~~

Không nhất thiết test mỗi dòng bằng một test riêng, nhưng trong cả bộ test phải kiểm tra được:

1. **Mapping:** request GET /api/books/7 có đi vào đúng controller method, còn sai method/path trả status phù hợp không?
2. **Tham số:** **7** trở thành tham số **@PathVariable long id**, **lang=en** đi vào **@RequestParam**, header **X-Tenant** đi vào **@RequestHeader**?
3. **Request contract:** client gửi đúng **Content-Type**, **Accept**, body JSON, hoặc thiếu/nhập sai thì server xử lý thế nào?
4. **Response contract:** status là 200/201/400/404/405/415/406 theo trường hợp, header **Location**, **Content-Type** và các header nghiệp vụ có đúng không?
5. **JSON:** các trường, giá trị, cấu trúc object/array, kiểu số/chuỗi có đúng không? Có vô tình trả một đối tượng lỗi với HTTP 200 không?

Trong Spring, nên kiểm thử cả **happy path** và **negative path**. Đừng dừng ở **status().isOk()**: một controller sai mapping hoặc trả JSON sai schema vẫn có thể trả 200.

## 2. Chọn công cụ và mức kiểm thử

Spring hỗ trợ hai công cụ nổi bật:

- **MockMvc** dành cho **Spring MVC** (Servlet stack). Nó chạy qua hạ tầng Spring MVC và tạo mock request/response, **không mở HTTP server thật**. Nó đặc biệt hữu ích để xác minh controller mapping, argument resolver, validation, message converter, exception handler, response status và JSON.
- **WebTestClient** là HTTP test client với API fluent; dùng được với **Spring WebFlux** và cũng có thể test **Spring MVC** (qua **MockMvcWebTestClient**), hoặc chạy end-to-end với server thật. Nó kiểm tra response bằng **expectStatus**, **expectHeader** và **expectBody**.

| Mức kiểm thử | Cách khởi tạo | Điều được kiểm chứng | Điều chưa bao phủ |
| --- | --- | --- | --- |
| Controller unit test bằng gọi method Java | new Controller(...).method(...) | Logic Java thuần | HTTP mapping, binding, converter, status/headers thực |
| **MockMvc standalone** | MockMvcBuilders.standaloneSetup(controller) | Mapping/binding/response với MVC cấu hình cục bộ | Không tự nạp mọi filter, advice, custom converter của app |
| **@WebMvcTest** + MockMvc | Spring Boot MVC slice | Controller, MVC infrastructure và cấu hình thuộc slice | Không nạp toàn bộ service/repository của ứng dụng |
| **@SpringBootTest** + MockMvc | Full ApplicationContext + mock servlet | Tích hợp rộng gồm beans/filter/application wiring | Không xác minh HTTP network thật |
| **WebTestClient.bindToController** | WebFlux mock setup | WebFlux router/controller và response reactive | Network/proxy thực |
| **MockMvcWebTestClient.bindToController** | MVC mock setup | MVC dùng cú pháp WebTestClient | Network thực |
| **WebTestClient.bindToServer** | Kết nối server đang chạy | HTTP end-to-end, network stack | Chậm hơn, cần server/test environment |

**Khuyến nghị cho bài học:** dùng **@WebMvcTest + MockMvc** để học từng assertion MVC, sau đó dùng **WebTestClient** nếu dự án dùng WebFlux hoặc muốn test thông qua API HTTP client thống nhất. Đừng thay HTTP-level test bằng cách gọi trực tiếp method controller: khi đó **@GetMapping**, **@PathVariable**, **@RequestBody** và chuyển JSON không được thực thi.

## 3. Thiết kế controller và hợp đồng trước khi viết test

Giả sử ứng dụng cung cấp hai endpoint:

- **GET /api/books/{id}?lang=...** bắt buộc header **X-Tenant**. Chỉ sách id=7 tồn tại, có thể chọn language, và server echo tenant qua response header.
- **POST /api/books** yêu cầu **Content-Type: application/json** với body **{"title":"..."}**, trả **201 Created**, **Location: /api/books/8** và body mô tả sách đã tạo.

Một phiên bản controller Java 17:

~~~java
package demo;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/books",
                produces = MediaType.APPLICATION_JSON_VALUE)
public class BookController {
    public record BookView(long id, String title, String language) {}
    public record CreateBook(String title) {}

    @GetMapping("/{id}")
    public ResponseEntity<BookView> getBook(
            @PathVariable("id") long id,
            @RequestParam(name = "lang", defaultValue = "vi") String lang,
            @RequestHeader("X-Tenant") String tenant) {

        if (id != 7L) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .header("X-Tenant-Echo", tenant)
            .body(new BookView(7, "Spring", lang));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookView> create(
            @RequestBody CreateBook request) {
        return ResponseEntity.created(URI.create("/api/books/8"))
            .body(new BookView(8, request.title(), "vi"));
    }
}
~~~

**Những điểm cần chứng minh qua test:**

- **@RequestMapping(path=..., produces=...)** đặt path chung và media type đầu ra. Hai annotation method bổ sung điều kiện GET/POST và path.
- **@PathVariable("id") long** kiểm tra đường dẫn và chuyển chuỗi sang số. Chuỗi không chuyển được thành long dẫn đến lỗi binding, thường HTTP 400.
- **@RequestParam(name="lang", defaultValue="vi")** dùng "vi" khi thiếu query parameter; nếu có **lang=en** phải nhận "en".
- **@RequestHeader("X-Tenant")** mặc định **required=true**; thiếu header thường trả 400 do Spring MVC xử lý, trước khi code nghiệp vụ chạy.
- **@PostMapping(consumes="application/json")** chỉ khớp request có content type phù hợp. Gửi **text/plain** thường trả HTTP 415.
- **ResponseEntity** định rõ status và header. **created(...)** đặt HTTP 201 và Location; body record được Jackson serialize thành JSON.
- **@RestController** tự viết response body qua converter. Nếu dùng **@Controller** thông thường mà thiếu **@ResponseBody**, String return value có thể được hiểu là view name.

Đây là ví dụ có trạng thái cố định để test có tính quyết định. Trong hệ thống thật, controller thường gọi service/repository; khi đó mock service trong MVC slice và xác minh cả input truyền tới service.

## 4. MockMvc: cấu trúc một assertion đúng

Cách đọc một test MockMvc:

~~~java
mockMvc.perform(
        get("/api/books/{id}", 7)
            .param("lang", "en")
            .header("X-Tenant", "school")
            .accept(MediaType.APPLICATION_JSON))
    .andExpect(status().isOk())
    .andExpect(handler().handlerType(BookController.class))
    .andExpect(handler().methodName("getBook"))
    .andExpect(header().string("X-Tenant-Echo", "school"))
    .andExpect(content().contentTypeCompatibleWith(
        MediaType.APPLICATION_JSON))
    .andExpect(jsonPath("$.id").value(7))
    .andExpect(jsonPath("$.title").value("Spring"))
    .andExpect(jsonPath("$.language").value("en"));
~~~

Ở đây **get(...)** là request builder từ **MockMvcRequestBuilders**, không phải gọi method Java trực tiếp.

- **perform(...)** chuyển mock HTTP request qua hạ tầng MVC.
- **status().isOk()** xác minh status response là 200.
- **handler().handlerType(...)**, **handler().methodName(...)** xác minh **handler được Spring chọn**. Đây là bằng chứng trực tiếp về mapping khi dùng MockMvc.
- **header().string(...)** đọc **response header**, khác với **.header(...)** ở request builder là đặt **request header**.
- **content().contentTypeCompatibleWith(APPLICATION_JSON)** kiểm tra response media type tương thích JSON; hữu ích nếu server có thêm tham số charset.
- **jsonPath("$.title").value(...)** trích giá trị trong JSON response. **$** là root của JSON document.

Nếu test chỉ có **jsonPath("$.title")**, nó có thể bỏ qua lỗi status hoặc header. Nếu test chỉ có **handler().methodName("getBook")**, nó chưa biết nội dung trả về có đúng. Nên khẳng định những phần là contract, không khẳng định mọi chi tiết implementation nội bộ.

### Mapping là tập hợp nhiều điều kiện

Một mapping Spring MVC có thể phụ thuộc:

- HTTP method: GET, POST, PUT, PATCH, DELETE.
- URL path, template variables, điều kiện **params** và **headers** trong annotation.
- **consumes** (Content-Type của request) và **produces** (tương thích với Accept của client).
- Cấu hình content negotiation, trailing slash và path matching của ứng dụng.

Ví dụ:

~~~java
@GetMapping(path = "/reports",
            params = "format=summary",
            headers = "X-Report-Client=web",
            produces = MediaType.APPLICATION_JSON_VALUE)
public Report summary() {
    return new Report("ready");
}
~~~

Request thiếu **format=summary** hoặc thiếu header có thể **không khớp mapping**, vì điều kiện nằm ở chính **@GetMapping**. Đừng nhầm với trường hợp một method đã khớp nhưng thiếu **@RequestHeader(required=true)**, khi đó Spring phát lỗi binding trước khi method chạy. Các status cụ thể có thể khác theo cấu hình/custom error handler; nên assert theo hợp đồng ứng dụng.

## 5. Kiểm chứng path variable, query parameter và header

### 5.1 Path variable

Giả sử **@GetMapping("/{id}")** với **@PathVariable long id**:

~~~java
mockMvc.perform(get("/api/books/{id}", 7)
        .header("X-Tenant", "school"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.id").value(7));
~~~

Một vài ca cần có:

- **/api/books/7** → id=7, HTTP 200.
- **/api/books/100** → mapping vẫn khớp nhưng controller trả HTTP 404 do tài nguyên vắng mặt.
- **/api/books/not-a-number** → path vẫn có segment, nhưng chuyển kiểu String sang long thất bại, HTTP 400 theo MVC mặc định.
- **/api/books** không có segment id → không khớp method GET /{id}; với controller có POST /api/books, GET /api/books có thể trả 405, tùy các mapping khác. Không đoán mọi trường hợp đều là 404.

**Không gom 404 từ nghiệp vụ với 404 do không tồn tại route**. Trường hợp id=100 có thể xác minh handler **getBook** đã được chọn và vẫn trả 404; còn route không tồn tại thì không có handler đó.

### 5.2 Query parameter

~~~java
mockMvc.perform(get("/api/books/{id}", 7)
        .param("lang", "en")
        .header("X-Tenant", "school"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.language").value("en"));

mockMvc.perform(get("/api/books/{id}", 7)
        .header("X-Tenant", "school"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.language").value("vi"));
~~~

**.param("lang", "en")** thêm request parameter vào mock request; với GET nó kiểm tra Spring MVC binding của query-like request parameter. Nếu cần kiểm chứng raw query string/URI encoding, gửi URL chứa query thực tế qua builder **get("/api/books?lang=en")** hoặc sử dụng **.queryParam(...)** phù hợp thay vì chỉ kiểm tra dữ liệu sau binding.

Với **@RequestParam(required=true)** không có default, thiếu parameter thường bị Spring MVC trả 400. Với một danh sách, dùng **.param("tag","java","spring")** và Java parameter **List<String>**; cần kiểm tra cả số lượng/thứ tự theo hợp đồng API.

### 5.3 Request header và response header

~~~java
mockMvc.perform(get("/api/books/7")
        .header("X-Tenant", "school")
        .accept(MediaType.APPLICATION_JSON))
    .andExpect(status().isOk())
    .andExpect(header().string("X-Tenant-Echo", "school"));
~~~

Đặt request header và assert response header là hai hành động khác nhau. HTTP header names **không phân biệt chữ hoa/thường**, dù convention thường hiển thị kiểu **Content-Type**, **X-Tenant**. Header nghiệp vụ riêng cần test đúng giá trị, nhất là authentication policy, correlation ID, cache metadata, ETag và Location nếu thuộc contract.

Test thiếu header:

~~~java
mockMvc.perform(get("/api/books/7")
        .accept(MediaType.APPLICATION_JSON))
    .andExpect(status().isBadRequest());
~~~

Status 400 của ví dụ phát sinh vì **@RequestHeader("X-Tenant")** required mặc định. Với header **@RequestHeader(value="X-Tenant",required=false)**, contract có thể cho phép không gửi; cần test behavior tương ứng. **Accept** diễn đạt dạng response muốn nhận; **Content-Type** diễn đạt dạng request body gửi đi. Đừng assert request header bằng **header().string(...)** (đó là response matcher).

## 6. Kiểm chứng status code đúng ngữ cảnh

| Tình huống | Status kỳ vọng trong demo | Cần kiểm chứng thêm |
| --- | --- | --- |
| GET /api/books/7, đủ header | **200 OK** | Mapping getBook, lang, tenant echo, JSON |
| GET /api/books/100, đủ header | **404 Not Found** | Controller chủ động báo sách không có |
| GET /api/books/not-a-number, đủ header | **400 Bad Request** | Lỗi chuyển path variable sang long |
| GET /api/books/7 thiếu X-Tenant | **400 Bad Request** | Argument resolver từ chối thiếu header |
| POST /api/books với JSON hợp lệ | **201 Created** | Location và JSON mới |
| POST /api/books với JSON cú pháp sai | **400 Bad Request** | JSON converter không đọc được body |
| POST /api/books với text/plain | **415 Unsupported Media Type** | consumes chỉ chấp nhận JSON |
| PUT /api/books/7 | **405 Method Not Allowed** | Có path pattern nhưng method không hỗ trợ |
| GET /api/books/7 với Accept không chấp nhận JSON | **406 Not Acceptable** | produces chỉ khai báo JSON |

**Lưu ý status:** bảng trên gắn với controller và cấu hình mặc định của demo. Một **@ControllerAdvice**, security filter hoặc error handler có thể đổi status/body. Test nên phản ánh contract thực tế đã chọn. HTTP 401/403 phải được tạo bằng chính cơ chế authentication/authorization nếu cần kiểm chứng bảo mật; việc thiếu header nghiệp vụ thông thường **X-Tenant** không tự đồng nghĩa HTTP 401.

Các shortcut của MockMvcResultMatchers:

~~~java
.andExpect(status().isOk());            // 200
.andExpect(status().isCreated());       // 201
.andExpect(status().isBadRequest());    // 400
.andExpect(status().isUnauthorized());  // 401
.andExpect(status().isForbidden());     // 403
.andExpect(status().isNotFound());      // 404
.andExpect(status().isMethodNotAllowed()); // 405
.andExpect(status().isUnsupportedMediaType()); // 415
~~~

Dòng trên là các mệnh đề **minh họa độc lập**, không thể nối tất cả vào cùng một request. Nếu cần status bất kỳ, dùng **status().is(422)** hoặc matcher tương ứng; không hard-code 200 cho mọi route.

## 7. Kiểm chứng JSON: JSONPath và so khớp cấu trúc

JSON response thường là object:

~~~json
{
  "id": 7,
  "title": "Spring",
  "language": "en",
  "meta": {
    "source": "catalog",
    "flags": ["popular", "featured"]
  }
}
~~~

Với MockMvc, các biểu thức JSONPath:

~~~java
.andExpect(jsonPath("$.id").value(7))
.andExpect(jsonPath("$.title").value("Spring"))
.andExpect(jsonPath("$.meta.source").value("catalog"))
.andExpect(jsonPath("$.meta.flags[0]").value("popular"))
.andExpect(jsonPath("$.meta.flags").isArray())
.andExpect(jsonPath("$.meta.flags.length()").value(2));
~~~

**$** là root; **.id** lấy field object; **[0]** là phần tử thứ nhất trong mảng (0-based). Với response là **array ở root**, dùng **$[0].title** thay vì **$.title**.

### JSON type và missing/null

- **jsonPath("$.id").value(7)** kiểm tra giá trị số 7. Một chuỗi **"7"** khác JSON number **7**.
- **jsonPath("$.title").isString()** hoặc **isNotEmpty()** dùng để kiểm tra dạng dữ liệu/không rỗng khi không muốn khóa cứng toàn bộ text.
- **jsonPath("$.meta.flags").isArray()** kiểm tra kiểu array. Các API còn có isMap(), isNumber(), isBoolean(), isEmpty(), exists(), doesNotExist().
- Trường vắng mặt và trường có giá trị **null** khác nhau. **exists()** đòi giá trị khác null; **hasJsonPath()** kiểm tra path tồn tại kể cả null; **doesNotHaveJsonPath()** kiểm tra path không tồn tại. Chọn matcher theo JSON contract cần bảo vệ.

Nếu chỉ muốn so sánh cấu trúc JSON mà không quan tâm whitespace/thứ tự keys:

~~~java
.andExpect(content().json(
    "{\"id\":7,\"title\":\"Spring\",\"language\":\"en\"}"));
~~~

Cách so sánh JSON mang tính cấu trúc. Mặc định ở một số phiên bản Spring, **content().json(...)** dùng kiểu so sánh lenient cho các field không được chỉ định; nếu muốn chặn field thừa hoặc sai kích thước array, hãy dùng strict JSON comparison đúng phiên bản hỗ trợ và test bổ sung. Không so sánh JSON string raw khi mục tiêu chỉ là giá trị trường: thứ tự keys và whitespace có thể đổi mà API vẫn đúng.

**Khi nào dùng JSONPath?** Dùng khi chỉ cần assert vài field ổn định và nhiều metadata có thể thay đổi. **Khi nào kiểm tra toàn body?** Với response error contract hoặc payload nhỏ có schema chặt cần bảo đảm mọi trường, kiểu dữ liệu và mảng.

### Dữ liệu biến động

Với timestamp, ID sinh ngẫu nhiên hoặc request ID:
- Assert trường **tồn tại/đúng định dạng**, không ghi cứng giá trị không ổn định.
- Với trường có thể xác định bằng test fixture, tạo dữ liệu cố định hoặc kiểm soát clock.
- Đừng dùng **Thread.sleep()** để đoán kết quả async; sử dụng API test async/timeout phù hợp.
- Không assert thứ tự của một collection khi hợp đồng không cam kết thứ tự. Nếu API cam kết thứ tự, assert từng phần tử và số lượng.

## 8. Ví dụ Maven + JUnit 5 + @WebMvcTest chạy được (Java 17)

Dự án Spring Boot **3.5.0**, Java 17, Spring MVC và spring-boot-starter-test. Đây là demo kiểm thử tập trung vào mapping/params/headers/status/JSON, không cần DB hay server HTTP bên ngoài.

Cấu trúc file:

~~~text
web-contract-test/
├── pom.xml
├── src/main/java/demo/
│   ├── WebContractApplication.java
│   └── BookController.java
└── src/test/java/demo/
    └── BookControllerTest.java
~~~

**pom.xml**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>

    <groupId>demo</groupId>
    <artifactId>web-contract-test</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
~~~

**src/main/java/demo/WebContractApplication.java**:

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebContractApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebContractApplication.class, args);
    }
}
~~~

**src/main/java/demo/BookController.java**:

~~~java
package demo;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/books",
                produces = MediaType.APPLICATION_JSON_VALUE)
public class BookController {
    public record BookView(long id, String title, String language) {}
    public record CreateBook(String title) {}

    @GetMapping("/{id}")
    public ResponseEntity<BookView> getBook(
            @PathVariable("id") long id,
            @RequestParam(name = "lang", defaultValue = "vi") String lang,
            @RequestHeader("X-Tenant") String tenant) {
        if (id != 7L) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .header("X-Tenant-Echo", tenant)
            .body(new BookView(id, "Spring", lang));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookView> create(
            @RequestBody CreateBook request) {
        return ResponseEntity.created(URI.create("/api/books/8"))
            .body(new BookView(8, request.title(), "vi"));
    }
}
~~~

**src/test/java/demo/BookControllerTest.java**:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void getMapsPathQueryHeaderAndResponseJson() throws Exception {
        mvc.perform(get("/api/books/{id}", 7)
                .param("lang", "en")
                .header("X-Tenant", "school")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(handler().handlerType(BookController.class))
            .andExpect(handler().methodName("getBook"))
            .andExpect(header().string("X-Tenant-Echo", "school"))
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.title").value("Spring"))
            .andExpect(jsonPath("$.language").value("en"));
    }

    @Test
    void missingQueryUsesDefaultLanguage() throws Exception {
        mvc.perform(get("/api/books/7")
                .header("X-Tenant", "school"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.language").value("vi"));
    }

    @Test
    void missingRequiredHeaderReturns400() throws Exception {
        mvc.perform(get("/api/books/7"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPathVariableReturns400() throws Exception {
        mvc.perform(get("/api/books/not-a-number")
                .header("X-Tenant", "school"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingBookReturns404() throws Exception {
        mvc.perform(get("/api/books/100")
                .header("X-Tenant", "school"))
            .andExpect(handler().methodName("getBook"))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
    }

    @Test
    void postJsonReturns201LocationAndJsonBody() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Java Testing"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/books/8"))
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(8))
            .andExpect(jsonPath("$.title").value("Java Testing"))
            .andExpect(jsonPath("$.language").value("vi"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void wrongRequestContentTypeReturns415() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.TEXT_PLAIN)
                .content("Java Testing"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mvc.perform(put("/api/books/7"))
            .andExpect(status().isMethodNotAllowed());
    }
}
~~~

### Chạy ví dụ

Tại thư mục chứa **pom.xml**, với Maven/JDK 17+:

~~~bash
mvn test
~~~

Khi môi trường tải dependency bình thường, Maven Surefire chạy **9 phương thức @Test** trong **BookControllerTest**. Điều kiện đạt là Maven kết thúc với **BUILD SUCCESS** và **Tests run: 9, Failures: 0, Errors: 0, Skipped: 0** (không tính test khác nếu tự bổ sung). Câu này mô tả kết quả kỳ vọng của test suite, không phải log đo được từ một lần chạy trong tài liệu.

Tại sao test không cần chạy server? **@WebMvcTest(BookController.class)** nạp MVC slice và tạo MockMvc, giúp request đi qua DispatcherServlet, handler mapping, argument resolver, message converter và exception resolver bằng mock servlet request/response. Nó không mở cổng 8080 và không gọi Internet.

### Mỗi test thực sự kiểm tra gì?

- **getMapsPathQueryHeaderAndResponseJson**: test một request với path, query, request header; xác nhận MVC chọn method getBook, status 200, response header, media type và ba trường JSON.
- **missingQueryUsesDefaultLanguage**: thực sự chạy binding mặc định của **@RequestParam(defaultValue="vi")**.
- **missingRequiredHeaderReturns400**: chứng minh missing header chặn request ở bước binding, không cần code controller tự kiểm tra.
- **invalidPathVariableReturns400**: chứng minh lỗi convert String → long.
- **missingBookReturns404**: cùng một mapping hoạt động nhưng dữ liệu không tồn tại; khác với route không tìm thấy.
- **postJsonReturns201LocationAndJsonBody**: kiểm tra request JSON decode, HTTP 201, header Location và JSON response.
- **malformedJsonReturns400**: kiểm tra body cú pháp JSON không hợp lệ.
- **wrongRequestContentTypeReturns415**: kiểm tra điều kiện **consumes**.
- **unsupportedMethodReturns405**: xác nhận PUT không nằm trong các method được khai báo cho /api/books/7.

Nếu triển khai controller khác code ví dụ hoặc thêm Spring Security, các status có thể thay đổi do filter/security policy. Khi đó phải khai báo rõ security expectations, ví dụ mock identity và CSRF token, chứ không xóa hết security filter chỉ để test qua.

## 9. Kiểm tra request body, header Location và JSON response có cấu trúc

Có ba hướng cần phân biệt:

**(a) Đặt request body**: dùng **.contentType(APPLICATION_JSON).content(json)** với MockMvc POST/PUT/PATCH. Nếu chỉ gọi **.content(...)** mà không đặt Content-Type, mapping có điều kiện consumes có thể trả 415.

~~~java
mvc.perform(post("/api/books")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Refactoring\"}"))
    .andExpect(status().isCreated())
    .andExpect(header().string("Location", "/api/books/8"))
    .andExpect(jsonPath("$.title").value("Refactoring"));
~~~

**(b) Phân biệt header Location và body field**: status 201 + JSON đúng nhưng thiếu **Location** vẫn có thể vi phạm contract tạo resource. Trong ví dụ server trả Location có đường dẫn tương đối; hệ thống API thực cũng có thể trả absolute URI. Test cần bám vào cách API đã quy định.

**(c) Response body là array hoặc nested object**: áp dụng JSONPath đúng cấu trúc:

~~~json
{
  "data": {
    "items": [
      {"id":7,"title":"Spring"},
      {"id":8,"title":"Java"}
    ]
  },
  "total": 2
}
~~~

Các matcher có thể là:

~~~java
.andExpect(jsonPath("$.total").value(2))
.andExpect(jsonPath("$.data.items").isArray())
.andExpect(jsonPath("$.data.items[0].id").value(7))
.andExpect(jsonPath("$.data.items[1].title").value("Java"));
~~~

Đoạn này là **fragment cho API trả cấu trúc nested như JSON mẫu**, không được gắn nguyên vào test của BookController demo vì controller demo trả BookView phẳng. Kiểm thử contract phải phản ánh JSON thật của endpoint tương ứng.

Đối với nội dung lỗi trả **application/problem+json** theo RFC 9457, JSONPath có thể kiểm tra **type**, **title**, **status**, **detail**, **instance** như hợp đồng; đừng mặc định mọi ứng dụng Spring luôn trả đúng trường đó nếu chưa bật/cấu hình ProblemDetail.

## 10. WebTestClient: cùng hợp đồng, API xác minh khác

Với WebFlux, MockMvc không chạy pipeline của Spring WebFlux. Dùng **WebTestClient**, công cụ hỗ trợ mock server hoặc server thật.

Ví dụ WebFlux controller:

~~~java
@RestController
@RequestMapping("/api/greetings")
class GreetingController {
    record Greeting(String text) {}

    @GetMapping("/{name}")
    Mono<Greeting> greet(
            @PathVariable String name,
            @RequestHeader("X-Lang") String language) {
        return Mono.just(new Greeting(
            language.equals("vi") ? "Xin chào " + name : "Hello " + name));
    }
}
~~~

Đoạn này là **fragment**: cần import **Mono**, **@RestController**, **@RequestMapping**, **@GetMapping**, **@PathVariable**, **@RequestHeader**. Với WebFlux infrastructure, test mock:

~~~java
WebTestClient client =
    WebTestClient.bindToController(new GreetingController()).build();

client.get()
    .uri("/api/greetings/{name}", "An")
    .header("X-Lang", "en")
    .accept(MediaType.APPLICATION_JSON)
    .exchange()
    .expectStatus().isOk()
    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
    .expectBody()
    .jsonPath("$.text").isEqualTo("Hello An");
~~~

**WebTestClient.bindToController(...)** thiết lập mock pipeline cho **WebFlux**. Nếu muốn dùng cú pháp WebTestClient để test một **Spring MVC** controller mà không mở server, dùng:

~~~java
WebTestClient client =
    MockMvcWebTestClient.bindToController(new BookController()).build();
~~~

Cần import **org.springframework.test.web.servlet.client.MockMvcWebTestClient**; import WebTestClient thuộc **org.springframework.test.web.reactive.server.WebTestClient**. Hai bindToController ở hai dòng trên không tương đương: một cái thiết lập WebFlux, cái còn lại thiết lập MVC. Đối với MVC controller có bean dependencies, truyền instance đã dựng hoặc dùng ApplicationContext thích hợp.

Trong Spring Boot có thể dùng **@WebFluxTest** cho WebFlux slice, hoặc **@SpringBootTest(webEnvironment = RANDOM_PORT)** kết hợp WebTestClient để chạy tích hợp với HTTP server thật. Khi test qua mock pipeline, status/headers/JSON được xác minh nhưng network, TLS, proxy/load balancer bên ngoài vẫn chưa được bao phủ.

### Kiểm tra JSON, status và header với WebTestClient

~~~java
client.get()
    .uri("/api/greetings/An")
    .header("X-Lang", "vi")
    .exchange()
    .expectStatus().isOk()
    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
    .expectBody()
    .jsonPath("$.text").isEqualTo("Xin chào An");
~~~

Có thể dùng **expectBody(Greeting.class)** để decode thành DTO, **expectBodyList(Greeting.class)** cho danh sách, hoặc **expectBody().json(...)** để so JSON có cấu trúc. Với generic phức tạp, dùng overload nhận **ParameterizedTypeReference<T>**.

Với response rỗng như HTTP 204, dùng **expectStatus().isNoContent().expectBody().isEmpty()**. Với stream SSE/NDJSON, xác minh status/header trước, sau đó lấy **FluxExchangeResult** và dùng **StepVerifier** để kiểm tra dữ liệu theo thời gian; không cố gom stream vô hạn để kiểm tra JSON body như response hữu hạn.

## 11. Tích hợp service, validation, security và error handler

Một MVC controller thường dùng service:

~~~java
@RestController
class PurchaseController {
    private final PurchaseService service;

    PurchaseController(PurchaseService service) {
        this.service = service;
    }

    @GetMapping("/api/purchases/{id}")
    Purchase find(@PathVariable long id) {
        return service.find(id);
    }
}
~~~

Khi dùng **@WebMvcTest(PurchaseController.class)**, service không tự động có mặt như trong full application context. Với Spring Boot 3.4+/Spring Framework 6.2, dùng **@MockitoBean** ở test:

~~~java
@WebMvcTest(PurchaseController.class)
class PurchaseControllerTest {
    @Autowired MockMvc mvc;

    @MockitoBean PurchaseService service;

    @Test
    void mapsIdAndSerializesResponse() throws Exception {
        given(service.find(3L)).willReturn(new Purchase(3L));

        mvc.perform(get("/api/purchases/3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(3));

        then(service).should().find(3L);
    }
}
~~~

Import **org.springframework.test.context.bean.override.mockito.MockitoBean**; **given/then** thuộc Mockito BDD (static imports). Các kiểu **PurchaseService/Purchase** ở đây là minh họa trong dự án đã khai báo sẵn; đây **không phải phần của demo chạy độc lập ở mục 8**.

Dùng test này để kiểm tra hai chiều: request HTTP có binding id đúng và controller đã gọi service với id 3; JSON response có id 3. Không nên mock **DispatcherServlet**, **HandlerMapping** hay **Jackson** khi mục tiêu là kiểm chứng Spring MVC mapping và converter.

### Validation

Khi controller dùng **@Valid @RequestBody RequestDto**, nên test:
- body hợp lệ → success (200/201);
- thiếu trường bắt buộc/constraint fail → status và error JSON theo **@ControllerAdvice** đã quy định;
- JSON sai cú pháp → 400 ở bước converter, khác lỗi Bean Validation;
- request không gửi body nhưng **@RequestBody(required=true)** → 400.

Cần thêm **spring-boot-starter-validation** nếu ứng dụng Spring Boot dựa vào Jakarta Bean Validation mà chưa có implementation. Đừng tự cho rằng chỉ có **@NotBlank** trên DTO sẽ hoạt động khi không có validation provider.

### Security

Nếu dùng Spring Security:
- **@WebMvcTest** có thể nạp security filters; anonymous request có thể trả 401/403, request POST có thể cần CSRF tùy cấu hình.
- Với test authorized route, dùng **spring-security-test** và **@WithMockUser** hoặc request post-processor thích hợp (nếu dependencies đã có).
- Cần test riêng anonymous, role thiếu và role đúng; không tắt filter chỉ để đạt status 200 trong bộ test được kỳ vọng kiểm chứng policy.
- Request header **Authorization** gửi vào MockMvc chưa đủ để giả định authentication thành công nếu ứng dụng không có filter xử lý header.

### Khi test thật sự cần end-to-end

Một test với **@SpringBootTest(webEnvironment = RANDOM_PORT)** và HTTP client nối **http://localhost:{port}** sẽ đi qua HTTP network và ứng dụng đã chạy. Hữu ích khi cần xác minh servlet container, custom filters, serialization theo cấu hình đầy đủ và các lớp tích hợp. Nó có chi phí startup và dependency state lớn hơn slice test. Đừng viết tất cả test controller thành end-to-end khi vài mệnh đề mapping/JSON có thể được kiểm nhanh, ổn định bằng MockMvc.

## 12. Lỗi thường gặp khi kiểm chứng HTTP contract

| Dấu hiệu | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| Gọi trực tiếp method controller, test vẫn pass dù sai URL | Không đi qua HandlerMapping | Dùng MockMvc/WebTestClient gửi HTTP request mock |
| Status 200 nhưng mapping nhầm method | Chỉ assert status | Thêm MockMvc **handler().methodName(...)** khi cần |
| Assert request header bằng header().string | Nhầm request builder với response matcher | Request dùng **.header(...)**, response dùng **header().string(...)** |
| Test POST trả 415 | Quên request Content-Type hoặc consumes sai | Dùng **.contentType(APPLICATION_JSON)** với body JSON |
| Test nhận 406 | Accept không phù hợp produces | Kiểm tra **.accept(...)** và media type server hỗ trợ |
| Expected 404 nhưng thực tế 400 | Path variable không đổi được sang kiểu Java | Phân biệt lỗi binding và không có dữ liệu |
| Thiếu header X-Tenant → 400 | @RequestHeader required | Gửi header hoặc thiết kế required=false có chủ đích |
| JSONPath $.items[0] thất bại | Response thực là object khác hoặc array ở root | Xem response thật, chọn đúng JSONPath |
| Test JSON string so sánh raw bị fail vì whitespace | So sánh bytes thay vì cấu trúc JSON | Dùng JSONPath hoặc content().json |
| JSON field null và field vắng bị đánh đồng | Matcher existence sai | Phân biệt exists, hasJsonPath, doesNotHaveJsonPath |
| @WebMvcTest không tạo được context | Controller phụ thuộc service bean chưa mock | Dùng @MockitoBean hoặc test config phù hợp |
| Request POST trong app có Security trả 403 | CSRF/security filter yêu cầu chính sách | Dùng test security phù hợp, giữ đúng policy |
| Test dựa trên timestamp/random ID bị flaky | Không kiểm soát clock/seed | Inject Clock, tạo fixture ổn định |
| Test pass riêng nhưng fail theo bộ | Test phụ thuộc DB/time/static mutable state | Cô lập fixture, dọn dữ liệu, bỏ phụ thuộc thứ tự |
| MVC test không phản ánh WebFlux | Dùng MockMvc với WebFlux pipeline | Dùng WebTestClient cho WebFlux |

### Checklist trước khi chấp nhận endpoint

1. Gửi đúng method, path và verify handler/route khi mục tiêu là mapping.
2. Kiểm tra **@PathVariable**, **@RequestParam**, **@RequestHeader**, defaults và các trường hợp thiếu/sai kiểu.
3. Với POST/PUT/PATCH, gửi đúng **Content-Type**, request body và test malformed/mismatched media type.
4. Assert **status** theo contract; phân biệt success, binding errors, media negotiation errors và domain errors.
5. Assert response **headers** quan trọng như Content-Type, Location, Cache-Control, ETag hay correlation ID.
6. Assert body **JSON** bằng JSONPath hoặc structural comparison; kiểm kiểu, giá trị, nested array/object, null/missing theo schema.
7. Kiểm tra negative cases (400/404/405/415/406) và security policy nếu có.
8. Dùng slice test cho controller contract; thêm integration test khi muốn bao phủ full wiring và HTTP network.

## 13. Bài tập thực hành

**Bài 1 – Mapping và negative status.** Thêm **GET /api/books/8** trả sách khác, giữ GET /api/books/7. Viết test 200 cho id=8 và 404 cho id=99. Thêm GET /api/books/abc và xác minh 400. Giải thích sự khác nhau giữa 404 do business và route không tồn tại.

**Bài 2 – Query và header.** Thêm **@RequestParam(defaultValue="10") int limit** và một header **X-Client-Version** bắt buộc. Viết test thiếu limit dùng mặc định 10, limit=5 thành số 5, limit=oops → 400, thiếu header → 400. Khi header có giá trị, trả response header echo và assert giá trị.

**Bài 3 – JSON request và response.** Mở rộng CreateBook có title và author, trả BookView chứa nested author object. Test 201, Location, **$.author.name**; test JSON lỗi cú pháp, Content-Type text/plain, và một field thiếu sau khi thêm @Valid/@NotBlank cùng validation provider.

**Bài 4 – Array và kiểu dữ liệu.** Tạo GET /api/books trả list của hai BookView. Assert body là array, độ dài, **$[0].id** là số, **$[1].title** là String, và kiểm thứ tự nếu contract có cam kết.

**Bài 5 – WebFlux.** Viết một controller trả **Mono<BookView>**, test với **WebTestClient.bindToController**. So sánh fluent expectations và giải thích tại sao test này không nên dùng MockMvc để kiểm tra pipeline WebFlux.

## Tài liệu chính thức

- [Spring Framework 6.2 – MockMvc](https://docs.spring.io/spring-framework/reference/6.2/testing/spring-mvc-test-framework.html)
- [Spring Framework 6.2 – MockMvc defining expectations](https://docs.spring.io/spring-framework/reference/6.2/testing/mockmvc/hamcrest/expectations.html)
- [Spring Framework 6.2 – WebTestClient](https://docs.spring.io/spring-framework/reference/6.2/testing/webtestclient.html)
- [Spring Boot 3.5 – Testing Spring MVC applications](https://docs.spring.io/spring-boot/3.5/reference/testing/spring-boot-applications.html)
- [Spring Framework – @MockitoBean](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-mockitobean.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-testing-004">Kiểm thử binding, validation, ProblemDetail và ControllerAdvice</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử binding, validation, ProblemDetail và ControllerAdvice trong Spring MVC

## 1. Mục tiêu và bức tranh tổng thể

Một API HTTP đúng không chỉ trả 200 cho request hợp lệ. Nó phải **chuyển dữ liệu từ HTTP thành đối tượng Java đúng cách (binding)**, từ chối input vi phạm ràng buộc (**validation**), trả status và cấu trúc lỗi nhất quán (**ProblemDetail**), đồng thời xử lý lỗi tập trung mà không làm lộ chi tiết hệ thống (**@ControllerAdvice**).

Ví dụ endpoint tạo đơn nhận JSON có mã hàng `sku` và số lượng `quantity`. Những trường hợp cần test gồm: body đúng, body rỗng, JSON sai cú pháp, Content-Type sai, `sku` chỉ chứa khoảng trắng, `quantity` nhỏ hơn 1, query parameter không chuyển được sang số, path variable vượt ràng buộc, và đơn hàng không tìm thấy.

Bài sử dụng **Spring MVC (Servlet)** với Spring Framework 6.1 / Spring Boot 3.3.x và Java 17. Trọng tâm là dùng **MockMvc** thực thi tầng MVC cùng converters, data binding, Jakarta Bean Validation, exception resolver và advice; **không cần khởi động cổng HTTP thật** trong slice test.

Học xong bạn có thể:
1. Phân biệt binding lỗi, conversion lỗi và Bean Validation lỗi; biết exception nào sẽ xuất hiện.
2. Dùng `@WebMvcTest`, `MockMvc`, `@Valid`, `@ModelAttribute`, `@RequestBody`, `@RequestParam` và `@PathVariable` có mục đích.
3. Viết `@RestControllerAdvice` kế thừa `ResponseEntityExceptionHandler` để chuyển các lỗi Spring MVC thành `ProblemDetail` chuẩn và bổ sung field errors.
4. Kiểm tra status, header `application/problem+json`, JSON body và việc controller **không chạy nhánh nghiệp vụ** nếu input không hợp lệ.
5. Xây dựng bộ test vừa có đường thành công, vừa có lỗi validation/conversion/exception, đồng thời biết những gì slice test không bảo đảm.

## 2. HTTP request đi qua Spring MVC như thế nào?

~~~mermaid
flowchart TD
    A[HTTP request] --> B[DispatcherServlet và HandlerMapping]
    B --> C[Argument resolver và HttpMessageConverter]
    C -->|JSON/form/query/path hợp lệ về cú pháp| D[Data binding và conversion]
    D -->|Có @Valid / constraint| E[Jakarta Bean Validation]
    E -->|PASS| F[Controller method]
    F --> G[Service / business result]
    G --> H[Response serialization]
    C -->|JSON không đọc được| I[Exception resolver]
    D -->|Conversion/required thiếu| I
    E -->|Validation FAIL| I
    F -->|Domain exception| I
    I --> J[ControllerAdvice / ResponseEntityExceptionHandler]
    J --> K[HTTP status + ProblemDetail JSON]
~~~

Đây là luồng khái niệm. Spring MVC có nhiều argument resolvers và exception resolvers, nên một số bước được lồng trong nhau; diagram nhằm phân biệt **nguồn phát sinh lỗi**. Nếu lỗi đã bị bắt và xử lý ở controller bằng `BindingResult`, nó có thể không chuyển sang advice.

| Nguồn input | Annotation thường gặp | Cách Spring đọc | Ví dụ lỗi |
| --- | --- | --- | --- |
| JSON request body | `@RequestBody` | Jackson/HttpMessageConverter | JSON sai → `HttpMessageNotReadableException` |
| Query string | `@RequestParam` | Converter + binder | `page=abc` không thành int → `MethodArgumentTypeMismatchException` |
| Path | `@PathVariable` | URI template + Converter | `/orders/abc` không thành long |
| Form/query bind object | `@ModelAttribute` | DataBinder điền JavaBean/constructor binding | Sai kiểu field tạo binding errors |
| Bean Validation object | `@Valid` trên DTO | Jakarta Validator (khi có dependency) | `@NotBlank`, `@Min` không đạt |
| Constraint trực tiếp trên method parameter | `@Min`/`@NotBlank` | Built-in method validation từ Spring 6.1 | `HandlerMethodValidationException` |

**Binding khác validation**: JSON `{"quantity":"abc"}` không giải mã được sang int nên chưa có DTO đúng để áp dụng `@Min` như thông thường. Ngược lại `{"quantity":0}` giải mã được thành int nhưng thất bại ràng buộc `@Min(1)`.

## 3. Validation trên DTO và validation trên method khác nhau

### 3.1. Validation một object

Ví dụ DTO:

~~~java
public record CreateOrderRequest(
    @NotBlank String sku,
    @Min(1) int quantity
) {}
~~~

Đặt `@Valid` tại tham số controller:

~~~java
@PostMapping
ResponseEntity<OrderResponse> create(
        @Valid @RequestBody CreateOrderRequest request) {
    // Chỉ đến đây nếu request đã giải mã và validation thành công.
}
~~~

Với Spring MVC, khi `@Valid @RequestBody` được validation ở mức object, **thường ném `MethodArgumentNotValidException`** nếu không đặt `Errors`/`BindingResult` ngay sau tham số và không có ràng buộc trực tiếp khác đòi hỏi method validation.

**Điều kiện tiên quyết:** ứng dụng phải có `spring-boot-starter-validation` (Jakarta Validation provider, thường là Hibernate Validator). Chỉ thêm `jakarta.validation.Valid` nhưng không có validator phù hợp sẽ không bảo đảm ràng buộc được thực thi.

Với `@NotBlank`, chuỗi null, rỗng và toàn whitespace bị từ chối. Với `@Min(1)`, các giá trị 0 hoặc âm bị từ chối. Nếu field primitive `int` không xuất hiện, Java có thể nhận giá trị mặc định 0 sau deserialization tùy cấu hình, rồi `@Min` bắt được; nhưng để phân biệt **thiếu hẳn field** với **field có giá trị 0**, dùng `Integer` kết hợp `@NotNull` và `@Min(1)`.

### 3.2. BindingResult thay đổi đường xử lý

Một controller khác có thể viết:

~~~java
@PostMapping("/preview")
ResponseEntity<?> preview(
        @Valid @RequestBody CreateOrderRequest request,
        BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
        return ResponseEntity.badRequest().body("Invalid preview");
    }
    return ResponseEntity.ok("Preview allowed");
}
~~~

Đoạn minh họa này thuộc một controller đã có `@RequestMapping`; cần imports Java/Spring và DTO ở ví dụ trước. **BindingResult phải đứng ngay sau argument cần bắt lỗi**. Khi xử lý cục bộ, bạn chủ động xây response; advice không nhất thiết nhận `MethodArgumentNotValidException` cho lỗi này. Không truyền mọi lỗi về HTTP 200 để “có body”; status phải phản ánh tính hợp lệ.

### 3.3. Validation trực tiếp trên method parameters (Spring 6.1+)

~~~java
@GetMapping("/{id}")
OrderResponse getOrder(@PathVariable @Min(1) long id) {
    return service.find(id);
}
~~~

`@Min` đặt trực tiếp lên tham số method yêu cầu **method validation**; Spring MVC 6.1 có built-in support và lỗi input thường là `HandlerMethodValidationException`. Việc thêm class-level `@Validated` có thể chuyển sang cơ chế AOP method validation khác; không tự thêm `@Validated` lên toàn controller nếu mục tiêu là kiểm thử cơ chế built-in này.

| Tình huống | Exception điển hình | Phản hồi nên test |
| --- | --- | --- |
| `@Valid @RequestBody` với field `@NotBlank` sai | `MethodArgumentNotValidException` | 400 và field errors |
| `@Valid @ModelAttribute` với field `@Min` sai | `MethodArgumentNotValidException` | 400 và field errors |
| `@PathVariable @Min(1)` nhận 0 | `HandlerMethodValidationException` | 400 (với input violation) |
| `@RequestParam int page` nhận `abc` | `MethodArgumentTypeMismatchException` / nhóm type mismatch | 400 |
| Thiếu required `@RequestParam` | `MissingServletRequestParameterException` | 400 |
| JSON sai cú pháp hoặc JSON mapping không thể hoàn tất | `HttpMessageNotReadableException` | 400 |
| `Content-Type: text/plain` cho endpoint tiêu thụ JSON | `HttpMediaTypeNotSupportedException` | 415 |
| Resource không tồn tại do nghiệp vụ | Custom `OrderNotFoundException` | 404 |
| Client không chấp nhận response media type | `HttpMediaTypeNotAcceptableException` | 406 |

Các status/mapping cuối cùng còn phụ thuộc exception handlers và Spring configuration. Không dựa vào một bảng cứng mà bỏ qua kiểm thử **response thực tế của ứng dụng**.

## 4. ProblemDetail: kiểm thử hợp đồng lỗi thay vì thông báo tùy ý

Spring Framework 6 hỗ trợ `org.springframework.http.ProblemDetail` theo mô hình **RFC 9457 Problem Details for HTTP APIs**. Body JSON điển hình:

~~~json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/orders",
  "errors": {
    "quantity": "must be greater than or equal to 1",
    "sku": "must not be blank"
  }
}
~~~

`type`, `title`, `status`, `detail`, `instance` là các member chuẩn; `errors` là extension của ứng dụng. Ví dụ mô tả **shape kỳ vọng**; message và locale của validator có thể thay đổi. Khi soạn test, chỉ kiểm tra chính xác message khi thông điệp đã thuộc API contract cố định, tránh ràng buộc vào text ngẫu nhiên của framework.

- **type:** URI nhận diện loại vấn đề; nếu không đặt, thường là `about:blank`.
- **title:** tên lỗi ngắn, có tính phân loại.
- **status:** HTTP status dạng số trong body; phải đồng nhất với status line.
- **detail:** giải thích an toàn cho người gọi, không chứa SQL, stack trace hoặc nội dung nội bộ.
- **instance:** URI của request bị lỗi; Spring có thể điền đường dẫn hiện tại nếu ứng dụng không đặt.
- **extensions:** thêm mã lỗi, field errors, correlation ID có kiểm soát. Jackson do Spring cấu hình thường serialize `ProblemDetail.setProperty("errors", ...)` ở **top-level JSON**, không bắt buộc nằm dưới `properties`.

Với `ProblemDetail` được return từ `@ExceptionHandler` hoặc controller, Spring MVC ưu tiên media type `application/problem+json` khi thương lượng nội dung. Hãy dùng `contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)` trong MockMvc để chịu được tham số charset không quan trọng.

**Không đồng nhất “mọi lỗi của Spring Boot tự trả ProblemDetail”**. Có hai lựa chọn:
1. Spring Boot có tùy chọn `spring.mvc.problemdetails.enabled=true` để cấu hình xử lý lỗi framework theo ProblemDetail.
2. Ứng dụng tự khai báo `@RestControllerAdvice` kế thừa `ResponseEntityExceptionHandler` để chủ động xử lý lỗi MVC và mở rộng response.

Ví dụ đầy đủ trong bài sử dụng **cách 2**, không cần bật property tương ứng. Tránh cấu hình đồng thời nhiều advice có trách nhiệm chồng chéo mà không xác định thứ tự `@Order`. Nếu đã bật Boot auto-config ProblemDetail, cần xét mức ưu tiên advice tùy chỉnh đối với handler tự động.

### 4.1. Những điều không nên lộ qua lỗi

Không trả trực tiếp `exception.getMessage()` cho tất cả exception 500: thông điệp có thể chứa SQL, đường dẫn file, class/package, host nội bộ hoặc token. Các field errors nên chứa **tên trường client được phép biết** và thông điệp đã kiểm tra an toàn. Khi thêm `traceId`, nên dùng ID tương quan sinh từ hệ thống quan sát; không cho phép client ghi đè dữ liệu tin cậy một cách tự do.

Đừng trả 500 cho lỗi nhập liệu 400, cũng đừng biến mọi exception thành 400. Cách phân loại có ảnh hưởng đến hành vi retry, UX, cache và vận hành.

## 5. @RestControllerAdvice và ResponseEntityExceptionHandler

`@ControllerAdvice` tập hợp xử lý dùng chung cho nhiều controller, gồm `@ExceptionHandler`, `@InitBinder` và `@ModelAttribute`. `@RestControllerAdvice` tương đương ControllerAdvice có semantics response body, rất tiện cho JSON API.

`ResponseEntityExceptionHandler` có handler sẵn cho nhiều exception của Spring MVC (body không đọc được, media type sai, binding/validation, method không hỗ trợ...). Khi kế thừa:
- **Override protected methods** như `handleMethodArgumentNotValid`, `handleHandlerMethodValidationException` để tùy biến lỗi framework.
- Thêm `@ExceptionHandler` **chỉ cho exception nghiệp vụ** của mình như OrderNotFoundException.
- Tránh khai báo thêm `@ExceptionHandler(MethodArgumentNotValidException.class)` cạnh một subclass ResponseEntityExceptionHandler, vì handler cơ sở đã đăng ký nhóm exception đó; mapping cạnh tranh có thể tạo lỗi ambiguous exception mapping.
- Trả `ResponseEntity` với status và body đồng nhất; dùng `handleExceptionInternal` nếu muốn giữ quy trình xử lý tập trung của base class.
- Không thêm `@ExceptionHandler(Exception.class)` bắt hết nếu chưa có yêu cầu rõ và kế hoạch logging/500, vì sẽ che lỗi lập trình, lỗi framework và status đúng.

Ví dụ chữ ký override trên **Spring Framework 6.1**:

~~~java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        status, "Request validation failed");
    problem.setTitle("Invalid request");
    return handleExceptionInternal(ex, problem, headers, status, request);
}
~~~

`HttpStatusCode` (không còn `HttpStatus` ở vị trí parameter đó) là phần quan trọng để biên dịch đúng Spring 6.1. Đây là đoạn tích hợp, class đầy đủ xuất hiện ở ví dụ sau.

## 6. Bài thực hành đầy đủ: Spring MVC + ProblemDetail + MockMvc

Demo có endpoint POST tạo đơn, GET tra cứu đơn, GET search dùng ModelAttribute, và GET quote dùng RequestParam. Không có database để giữ bài tập tập trung vào request mapping và lỗi HTTP. Application được tạo bằng Spring Boot **3.3.13**, Java 17+, Maven; dependency validation khai báo riêng.

### 6.1. Cấu trúc

~~~text
mvc-errors-demo/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── MvcErrorsApplication.java
    │   ├── OrderController.java
    │   └── ApiProblemAdvice.java
    └── test/java/demo/
        └── OrderControllerWebTest.java
~~~

### 6.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.13</version>
    <relativePath/>
  </parent>
  <groupId>demo</groupId>
  <artifactId>mvc-errors-demo</artifactId>
  <version>1.0.0</version>
  <properties>
    <java.version>17</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
~~~

Ba dependencies đảm nhiệm: Spring MVC + Jackson; Jakarta Bean Validation + Hibernate Validator; JUnit 5/MockMvc/assertions. Ví dụ không cài Spring Security, vì FilterChain xác thực/CSRF sẽ làm thay đổi status và cấu hình test; dự án có Security cần kiểm thử security thực tế, không mặc định tắt filter.

### 6.3. MvcErrorsApplication.java

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MvcErrorsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MvcErrorsApplication.class, args);
    }
}
~~~

### 6.4. OrderController.java

~~~java
package demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record CreateOrderRequest(
        @NotBlank String sku,
        @Min(1) int quantity
    ) {}

    public record OrderResponse(long id, String sku, int quantity) {}

    public static class SearchCriteria {
        @Min(1)
        private Integer page;

        public Integer getPage() {
            return page;
        }

        public void setPage(Integer page) {
            this.page = page;
        }
    }

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(long id) {
            super("Order " + id + " was not found");
        }
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest input) {
        OrderResponse output = new OrderResponse(
            42L, input.sku(), input.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(output);
    }

    @GetMapping("/{id}")
    public OrderResponse find(@PathVariable @Min(1) long id) {
        if (id != 42L) {
            throw new OrderNotFoundException(id);
        }
        return new OrderResponse(42L, "A-7", 2);
    }

    @GetMapping("/search")
    public String search(@Valid @ModelAttribute SearchCriteria filter) {
        return "page=" + filter.getPage();
    }

    @GetMapping("/quote")
    public String quote(@RequestParam int quantity) {
        return "quantity=" + quantity;
    }
}
~~~

**Lý do cố ý viết như vậy:**
- `create` cho thấy `@RequestBody` + Jackson + Bean Validation `@NotBlank`/`@Min`, HTTP 201 khi thành công.
- `find` có `@Min` trực tiếp ở argument method để minh họa `HandlerMethodValidationException`; **không đặt `@Validated` ở class**, tránh thay đổi cơ chế Spring MVC 6.1.
- `search` nhận query `page` vào bean bằng `@ModelAttribute`; `page=0` bind thành Integer nhưng không đạt `@Min(1)`.
- `quote` nhận `int`, dùng để kiểm lỗi conversion `quantity=abc` hoặc thiếu tham số.
- Exception OrderNotFoundException đại diện cho lỗi nghiệp vụ. Việc response cố định và không lưu database giúp test độc lập với persistence; ứng dụng thật gọi service nhưng test MVC có thể mock service riêng.

**Chú ý mapping GET:** `/search` và `/quote` là các path cụ thể, Spring ưu tiên path cụ thể hơn `/{id}`. `/api/orders/abc` sẽ chọn pattern `/{id}` nhưng không thể bind thành long, nên là lỗi conversion, khác `/api/orders/999` chuyển được thành long và dẫn tới domain 404.

### 6.5. ApiProblemAdvice.java

~~~java
package demo;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import demo.OrderController.OrderNotFoundException;

@RestControllerAdvice
public class ApiProblemAdvice extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            status, "Request validation failed");
        problem.setTitle("Invalid request");

        Map<String, String> fieldErrors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.putIfAbsent(
                error.getField(),
                error.getDefaultMessage() == null
                    ? "Invalid value" : error.getDefaultMessage()));

        problem.setProperty("errors", fieldErrors);
        return handleExceptionInternal(
            ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            status, "Method parameter validation failed");
        problem.setTitle("Invalid parameter");

        return handleExceptionInternal(
            ex, problem, headers, status, request);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleOrderNotFound(
            OrderNotFoundException ex) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, "Requested order does not exist");
        problem.setTitle("Order not found");
        problem.setProperty("code", "ORDER_NOT_FOUND");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }
}
~~~

Class này là cấu hình xử lý lỗi **đúng phiên bản 6.1**. `ResponseEntityExceptionHandler` tiếp tục xử lý lỗi JSON không đọc được, thiếu query, sai Content-Type và lỗi built-in khác mà ta không override. `@RestControllerAdvice` được component scan cùng package demo khi chạy ứng dụng. Custom exception 404 được đặt mã lỗi máy đọc được là ORDER_NOT_FOUND; không cần gửi exception message có ID thật nếu đó là thông tin không nên tiết lộ.

`TreeMap` làm thứ tự tên field ổn định để dễ đọc response, nhưng **test JSON không nên dựa vào thứ tự field**. `putIfAbsent` chọn thông điệp đầu tiên cho mỗi field; một field có thể vi phạm nhiều constraint. Hệ thống có nhu cầu UI chi tiết có thể trả **danh sách lỗi theo field** thay vì một string, phải định nghĩa contract trước. Spring Jackson mix-in có thể hiển thị extension `errors` và `code` ở top-level JSON.

### 6.6. OrderControllerWebTest.java

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(ApiProblemAdvice.class)
class OrderControllerWebTest {

    @Autowired
    MockMvc mvc;

    @Test
    void validJsonReturns201AndMappedDto() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sku":"A-7","quantity":2}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.sku").value("A-7"))
            .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void invalidBodyReturnsProblemWithFieldErrors() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sku":"   ","quantity":0}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.errors.sku").exists())
            .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    void malformedJsonIsRejectedBeforeBeanValidation() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{broken json"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.TEXT_PLAIN)
                .content("sku=A-7"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void modelAttributeValidationReturns400() throws Exception {
        mvc.perform(get("/api/orders/search")
                .param("page", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.errors.page").exists());
    }

    @Test
    void validQueryBindsToModelAttribute() throws Exception {
        mvc.perform(get("/api/orders/search")
                .param("page", "2"))
            .andExpect(status().isOk())
            .andExpect(content().string("page=2"));
    }

    @Test
    void malformedQueryNumberReturns400() throws Exception {
        mvc.perform(get("/api/orders/quote")
                .param("quantity", "abc"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingRequiredQueryReturns400() throws Exception {
        mvc.perform(get("/api/orders/quote"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void directMethodConstraintUsesMethodValidation() throws Exception {
        mvc.perform(get("/api/orders/0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid parameter"))
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void notFoundDomainErrorReturns404Problem() throws Exception {
        mvc.perform(get("/api/orders/999"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void existingResourceReturnsSuccessfulJson() throws Exception {
        mvc.perform(get("/api/orders/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sku").value("A-7"));
    }
}
~~~

Chạy trong thư mục `mvc-errors-demo`:

~~~bash
mvn -q test
~~~

Bộ mã cung cấp **11 phương thức JUnit @Test**. Nếu chạy thành công, Maven trả exit code 0 và Surefire tạo báo cáo ở `target/surefire-reports/`. Chế độ `-q` có thể không in dòng tổng hợp trên stdout; **không giả định test đã PASS khi chưa chạy ở máy thực**. Khi test fail, đọc stack trace cùng resolved exception/response của MockMvc; không sửa assertion chỉ để che hành vi bất ngờ.

Chạy server thật:

~~~bash
mvn spring-boot:run
~~~

Lệnh thử request:

~~~bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"sku":"A-7","quantity":2}'

curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"sku":" ","quantity":0}'

curl -i http://localhost:8080/api/orders/999
~~~

Với demo này, response của lệnh thứ nhất dự kiến **HTTP 201** với body như sau (bỏ qua headers, thời điểm và whitespace):

~~~json
{"id":42,"sku":"A-7","quantity":2}
~~~

Lệnh thứ hai dự kiến **HTTP 400** và ProblemDetail chứa `title: "Invalid request"`, cùng hai field errors cho sku/quantity. Lệnh thứ ba dự kiến **HTTP 404**, `code: "ORDER_NOT_FOUND"`. Các trường `detail` hoặc validation messages được bản địa hóa tùy cấu hình validator; test không khóa text đó một cách không cần thiết.

**Về khả năng chạy:** đây là project Maven mẫu độc lập, được thiết kế theo API Spring 6.1. Việc xây dựng test trong bài giúp người đọc chạy kiểm tra trên máy của mình; bài này không đưa ra bằng chứng test đã thực thi ngoài môi trường được mô tả.

## 7. Vì sao dùng @WebMvcTest và khi nào phải kiểm thử đầy đủ?

`@WebMvcTest(OrderController.class)` tải một lát cắt MVC: controller, mapping/argument resolvers, converters, MVC configuration, validation và những advice liên quan. `@Import(ApiProblemAdvice.class)` làm explicit rằng chính advice của API được dùng khi test (ngay cả khi scan thay đổi). MockMvc tạo request/response ở tầng Servlet mô phỏng mà không bind TCP port.

| Cách test | Phần được kiểm tra | Giới hạn |
| --- | --- | --- |
| Unit test gọi trực tiếp `controller.create(dto)` | Logic Java trong method | Không chạy JSON binding, validation, mapping, advice |
| MockMvc `standaloneSetup` | Mapping MVC tối thiểu, test nhanh và có thể gắn advice thủ công | Cần tự cấu hình converter/validator/resolvers; dễ lệch production |
| `@WebMvcTest` + MockMvc | MVC slice, validation, Jackson, advice, handler mappings | Không tải toàn bộ service/database/infra |
| `@SpringBootTest` + `@AutoConfigureMockMvc` | Cấu hình app đầy đủ cùng MockMvc | Nặng hơn, vẫn không tự chứng minh socket/TLS/proxy |
| `@SpringBootTest(webEnvironment=RANDOM_PORT)` + HTTP client | Full app qua TCP local, HTTP serialization thật | Tốn tài nguyên, còn phụ thuộc cách mock infra và cấu hình test |
| Contract/E2E | Tương thích producer-consumer và deployment thực | Chậm, đòi hỏi quản lý test data và môi trường |

Nếu controller inject một service, `@WebMvcTest` **không tự tải** tất cả `@Service`, repository hay bean nghiệp vụ. Với Spring Boot 3.3 có thể dùng `@MockBean` để cung cấp dependency trong slice test hoặc `@Import` config test cần thiết; chú ý API mock annotation có thay đổi ở các nhánh Boot mới, tránh dùng annotation không thuộc version dự án.

Khi dùng Spring Security, `@WebMvcTest` có thể tải FilterChain. Request POST có thể bị 403 vì CSRF **trước khi** chạm vào controller, và 401/403 có thể do authentication. Hãy viết test security đúng `spring-security-test` với user/CSRF nếu cần; không tắt security tùy ý rồi kết luận endpoint production xử lý validation ổn.

## 8. Kiểm thử sâu hơn data binding và validation

### 8.1. Tách ba nguồn lỗi

Giả sử endpoint POST nhận int quantity:
- `{"quantity":2}`: JSON hợp lệ, binding OK, `@Min` OK.
- `{"quantity":0}`: JSON hợp lệ, binding OK, Bean Validation FAIL.
- `{"quantity":"not-a-number"}`: Jackson/type conversion FAIL trước validation.
- `{"quantity":null}`: hành vi phụ thuộc primitive/wrapper và cấu hình Jackson; test contract của mình thay vì giả định.
- Body rỗng và `@RequestBody(required=true)`: thường `HttpMessageNotReadableException` dẫn đến 400.
- Thiếu Content-Type hoặc `text/plain`: có thể gây 415 bởi `consumes="application/json"`; kiểm tra cả body lẫn header request.

Hai lỗi có status cùng 400 **không có nghĩa cùng loại**. Với API công khai, nên có mã máy đọc được phân biệt `VALIDATION_FAILED`, `INVALID_JSON`, `INVALID_PARAMETER` nếu client thực sự cần; không suy ra code từ tiếng Anh trong `detail`.

### 8.2. Lỗi trong @ModelAttribute có thể là binding hoặc validation

Với `@Valid @ModelAttribute SearchCriteria`:
- `?page=2`: String "2" chuyển thành Integer 2, pass `@Min(1)`.
- `?page=0`: chuyển thành Integer 0, validation fail.
- `?page=abc`: conversion không thành Integer; binder ghi field error, tùy exception resolution của signature có thể thành `MethodArgumentNotValidException` với BindingResult.
- `?page=`: có thể bind thành null khi dùng `Integer`, và `@Min` **không thay thế `@NotNull`**; nếu page bắt buộc, thêm `@NotNull`.
- Unknown query parameter: nếu không có matching property, nó thường không được bind, nhưng điều này không phải chính sách bảo mật đủ cho mọi DTO.

Đối với mutable object, **tránh mass assignment**: không bind thẳng entity có các field `admin`, `price`, `role`, `ownerId` từ input không tin cậy. Dùng DTO riêng, constructor binding hoặc giới hạn `DataBinder.setAllowedFields(...)` ở `@InitBinder` khi thích hợp. Unit test DTO không bắt được vấn đề này nếu không chạy MVC binder với request thật.

### 8.3. Validation groups và custom constraints

`@Validated(SomeGroup.class)` có thể chọn nhóm Bean Validation khác `@Valid` mặc định khi áp dụng cho object parameter. Nếu ứng dụng có POST create và PATCH update với điều kiện field khác nhau, hãy test **từng group** và khả năng thiếu/null riêng. Với custom `ConstraintValidator`, test ít nhất:
1. Giá trị hợp lệ và giá trị không hợp lệ;
2. Null/blank theo policy;
3. Unicode, biên số, độ dài;
4. Message/code và locale nếu là hợp đồng API.

**Bean Validation không thể thay thế kiểm tra nghiệp vụ có trạng thái**: unique SKU trong DB, quyền sửa order, số dư hay inventory cần service/database/race-condition tests. Tránh nhồi query DB vào ConstraintValidator khiến request binding tạo phụ thuộc ẩn.

### 8.4. Assert business side effects không xảy ra

Khi controller inject service, test invalid input nên kiểm tra service **không được gọi**. Ví dụ tích hợp dùng Mockito:

~~~java
mvc.perform(post("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"sku\":\" \",\"quantity\":0}"))
    .andExpect(status().isBadRequest());

verifyNoInteractions(orderService);
~~~

Đây là snippet không độc lập; `orderService` là Mockito mock được inject vào controller và cần static import Mockito.verifyNoInteractions. Với demo không có service, nên chỉ kiểm status/body như ví dụ hoàn chỉnh. Việc service không được gọi là tiêu chí an toàn khi validation bảo vệ thao tác tạo dữ liệu thật.

## 9. Kiểm thử hợp đồng ProblemDetail ổn định

Một bộ assertion tốt tách **schema contract** khỏi chi tiết tình cờ:
- Kiểm status HTTP và body status đồng nhất;
- Header Content-Type tương thích `application/problem+json`;
- `type` (khi ứng dụng định nghĩa) và `code` của domain error ổn định;
- `title`/field errors có tồn tại, đúng key/shape;
- `instance` tương ứng path request, không chứa query nhạy cảm;
- Không có stack trace, package name, credential, SQL trong response;
- Không phụ thuộc thứ tự thuộc tính JSON hoặc thứ tự field errors;
- Test với locale khác nếu messages được quốc tế hóa.

Nếu public API đã công bố `type` URI cụ thể như `https://api.example.test/problems/validation-error`, test chính xác URI đó và tài liệu hóa lâu dài. Nếu chưa công bố, `about:blank` là giá trị mặc định hợp lệ, **không nên tự bịa URI** chỉ để đủ năm field.

### Localized messages

Spring MVC có `MessageSource` để resolve validation messages và ProblemDetail detail/title. Test locale bằng `Accept-Language` nếu ứng dụng hỗ trợ. Tránh so sánh chính xác thông điệp mặc định như "must not be blank" nếu có thể đổi theo Hibernate Validator, Spring version hoặc locale; thay vào đó kiểm tra field name và mã lỗi. Nếu giao diện thật dựa vào chuỗi thông điệp, hãy **sở hữu và phiên bản hóa thông điệp đó**, chứ không để framework nội bộ tự quyết định.

### Tương tác nhiều ControllerAdvice

Một ứng dụng có thể có advice chỉ dành cho REST API và advice khác cho MVC HTML. `@RestControllerAdvice(basePackages=...)`, `assignableTypes`, `annotations` và `@Order` giới hạn phạm vi/ưu tiên. Test nhiều controllers để bảo đảm response HTML không vô tình nhận JSON ProblemDetail, và lỗi domain của module này không bị advice catch-all module khác bắt trước.

Trong Spring Boot khi bật `spring.mvc.problemdetails.enabled=true`, handler tự động có thể cùng chịu trách nhiệm exception framework. Phải thiết kế override và thứ tự rõ ràng, không chỉ copy một `@RestControllerAdvice` rồi giả định chắc chắn được chọn đầu tiên.

## 10. Các sai lầm phổ biến khi viết MockMvc test

1. **Chỉ gọi trực tiếp controller:** không test Servlet binding, Jackson hay advice; thêm MockMvc request chứa HTTP headers/body thật.
2. **Quên spring-boot-starter-validation:** `@NotBlank`/`@Min` xuất hiện trong code nhưng không có validator. Đưa một test input sai và xác minh phải 400.
3. **Không phân biệt conversion với validation:** test `quantity=abc` nhưng mong field error giống `quantity=0`; xác nhận đúng exception path.
4. **Class-level @Validated làm thay đổi path method validation:** tránh nếu muốn kiểm Spring MVC 6.1 built-in HandlerMethodValidationException.
5. **Viết @ExceptionHandler trùng với ResponseEntityExceptionHandler:** có thể gây ambiguous exception mapping; override protected handler đúng loại.
6. **Assert chính xác mọi ký tự message mặc định:** dễ vỡ theo locale/validator version; test contract tự sở hữu.
7. **Quên Content-Type hoặc Accept:** một endpoint `consumes=application/json` có thể trả 415 trước bước body decoding.
8. **Bỏ qua các trường hợp body rỗng và malformed:** chỉ test validation đúng kiểu dữ liệu sẽ thiếu một nhóm 400 quan trọng.
9. **Trả HTTP 200 kèm `{"success":false}` cho validation:** gây nhầm client, caching và metrics; trả 400/422 theo hợp đồng API.
10. **Bọc mọi exception thành HTTP 500 có raw message:** lộ thông tin hệ thống; phân loại lỗi, log nội bộ riêng.
11. **Dùng @WebMvcTest rồi giả định database/transaction thật đã được test:** slice test chỉ bao phủ MVC; thêm service/persistence/integration tests theo rủi ro.
12. **Tắt filters để vượt lỗi 403:** đang che security path thực; test authentication/CSRF có chủ đích.
13. **Mock hết MVC converters:** có thể làm mất Jackson/config thật và tạo false positive; ưu tiên slice dùng configuration production phù hợp.
14. **Giả định mọi 400 trả cùng schema:** MissingServletRequestParameterException, malformed JSON và method validation có thể đi các handler khác; viết API contract chung nếu cần rồi test từng loại.
15. **Không kiểm status-body consistency:** body `status:400` nhưng HTTP line 200 là contract lỗi.
16. **Test chỉ kiểm exception class:** kiểm response JSON và content type mà client thật đọc, vì advice có thể map sai dù exception đúng.

## 11. Ma trận test đề nghị cho một REST controller

| Input/đường đi | Dự kiến | Vì sao cần |
| --- | --- | --- |
| JSON body đầy đủ, hợp lệ | 201/200 với response DTO | Chứng minh mapping và serializer |
| JSON thiếu field bắt buộc | 400 field errors | Chặn dữ liệu thiếu |
| Field blank, âm hoặc vượt min/max | 400 field errors | Chứng minh Bean Validation chạy |
| JSON hỏng syntax | 400 ProblemDetail | Chứng minh lỗi parsing được xử lý |
| JSON field sai kiểu | 400, không gọi business service | Chặn conversion sai |
| Content-Type không hỗ trợ | 415 | Chứng minh media type contract |
| Query parameter không chuyển kiểu | 400 | Binding lỗi ngoài body |
| Thiếu query parameter required | 400 | Không chấp nhận giá trị vắng mặt |
| `@ModelAttribute` không hợp lệ | 400 field errors | Chứng minh binder + validator |
| `@PathVariable` vi phạm `@Min` | 400 | Chứng minh method validation 6.1 |
| Resource không tồn tại | 404 với mã nghiệp vụ | Chứng minh custom ControllerAdvice |
| Lỗi không dự kiến ở service | 500 response không lộ stack trace | Chứng minh error boundary |
| Request không đăng nhập/thiếu quyền | 401/403 nếu có security | Chứng minh filters/advice không phá security |
| Accept không tương thích | 406 khi contract áp dụng | Chứng minh content negotiation |
| Locale vi/en | Messages đúng ngôn ngữ nếu có hỗ trợ | Giảm rủi ro UI/i18n |

Chọn những test phù hợp với thực tế endpoint; mục tiêu không phải tăng số lượng assertion mà là **bao phủ các cơ chế riêng** và bảo vệ hợp đồng mà client đang dựa vào.

## 12. Quy trình khi một test thất bại

1. Đọc HTTP request đã tạo (method, URL, Content-Type, Accept, JSON, query). Lỗi 415 thường xuất hiện trước validation.
2. Đọc **resolved exception** của MockMvc; phân biệt HttpMessageNotReadableException, MethodArgumentNotValidException, HandlerMethodValidationException và lỗi nghiệp vụ.
3. Kiểm tra advice nào được component scan/import, thứ tự `@Order`, xem có mapping trùng hay không.
4. Kiểm tra dependency validation/provider, Bean Validation group, các annotation trực tiếp trên method parameters và vị trí BindingResult.
5. Kiểm tra response HTTP status và body ProblemDetail cùng content type; lỗi negotiation có thể làm body khác dự kiến.
6. Chỉ sau khi xác định nguyên nhân mới sửa code hoặc test; không đơn giản chuyển expected 400 thành 500 để test xanh.
7. Khi thay API contract, test lại consumer expectations, đặc biệt keys `errors`, `code`, `type`, nullable fields và semantics.

## 13. Kết luận

Một bộ kiểm thử Spring MVC đáng tin phải bao quát **HTTP binding → conversion → validation → controller → exception resolver/ControllerAdvice → response ProblemDetail**. `@WebMvcTest` + MockMvc đủ nhẹ để xác minh đa số cơ chế ở tầng MVC, còn service/database/security/transport thật cần được bổ sung bởi những test tương ứng.

`ProblemDetail` giúp tiêu chuẩn hóa thông tin lỗi nhưng không tự sửa thiết kế API: ứng dụng phải nhất quán về status, field errors, mã nghiệp vụ, bảo mật thông tin và thứ tự advice. Điều đặc biệt quan trọng từ Spring 6.1 là xử lý **cả `MethodArgumentNotValidException` lẫn `HandlerMethodValidationException`**, vì method signature quyết định tầng validation được kích hoạt.

## Tài liệu tham khảo

- Spring Boot 3.3 — Testing Spring Boot Applications / @WebMvcTest: https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html
- Spring Boot 3.3 API — @WebMvcTest: https://docs.spring.io/spring-boot/3.3/api/java/org/springframework/boot/test/autoconfigure/web/servlet/WebMvcTest.html
- Spring Framework — MVC Validation: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html
- Spring Framework — Error Responses / RFC 9457 ProblemDetail: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- Spring Framework 6.1 — ResponseEntityExceptionHandler Javadoc: https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html
- Spring Framework 6.1 — ProblemDetail Javadoc: https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/http/ProblemDetail.html
- Spring Framework — MockMvc: https://docs.spring.io/spring-framework/reference/testing/mockmvc.html
- Jakarta Bean Validation: https://jakarta.ee/specifications/bean-validation/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-testing-005">Kiểm thử view, form, redirect, flash attributes và session</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử view, form, redirect, flash attributes và session trong Spring MVC

Một luồng web dùng HTML có thể trả lại trang biểu mẫu khi nhập sai, hoặc nhận POST thành công rồi chuyển hướng tới một GET khác. Kiểm thử đúng luồng này phải nhìn được **view nào được chọn, model nào được chuyển cho view, dữ liệu form có được bind và validate, response có redirect đi đâu, thông điệp flash đến được request nào, và trạng thái session có tồn tại đúng vòng đời hay không**.

Đây là nội dung của Spring MVC theo mô hình Servlet, áp dụng cho `@Controller` trả về HTML/view (khác với các endpoint `@RestController` chủ yếu trả JSON). Ví dụ dùng Java 17+, Spring Boot 3.3 và JUnit 5 để cấu hình dễ chạy; cơ chế view/model/`RedirectAttributes`/session thuộc Spring MVC.

## 1. Nhìn cả luồng, không chỉ một status code

Giả sử có trang đăng ký tại `GET /signup` và form gửi đến `POST /signup`:

```text
GET /signup
  → controller khởi tạo đối tượng form vào Model
  → view "signup/form"
  → trình duyệt nhận trang HTML có <form>

POST /signup, name="  " (không hợp lệ)
  → data binding + Bean Validation
  → BindingResult chứa lỗi
  → trả lại view "signup/form" cùng lỗi, KHÔNG redirect

POST /signup, name="An" (hợp lệ)
  → xử lý form
  → ghi thuộc tính lâu hơn một request vào HttpSession
  → addFlashAttribute("notice", ...)
  → response 3xx + Location: /signup/done

GET /signup/done (cùng session)
  → thông điệp flash trở thành thuộc tính trong Model
  → view "signup/done" hiển thị kết quả
  → flash đã được lấy khỏi nơi lưu tạm

GET /signup/done lần nữa (cùng session)
  → flash "notice" không còn
  → thuộc tính session vẫn còn nếu chưa xóa
```

Luồng thành công là mẫu **Post/Redirect/Get (PRG)**. Trình duyệt nhận kết quả POST bằng redirect, sau đó chuyển sang GET; refresh trang kết quả thường không tự lặp lại POST. PRG giúp giảm nguy cơ gửi lại biểu mẫu do refresh, nhưng không phải bảo đảm chống mọi POST trùng lặp: thao tác thay đổi dữ liệu quan trọng vẫn cần kiểm soát ở tầng nghiệp vụ, chẳng hạn idempotency key hoặc unique constraint khi phù hợp.

| Thành phần | Dữ liệu sống ở đâu / bao lâu | Điều nên assert |
| --- | --- | --- |
| View name | Kết quả chọn view của handler | `view().name("signup/form")` |
| Model | Thuộc tính đưa cho view trong request | `model().attributeExists(...)`, `model().attribute(...)` |
| Form binding / validation | Request parameters → form object; lỗi trong `BindingResult` | `model().attributeHasFieldErrors(...)` hoặc `attributeHasNoErrors(...)` |
| Redirect | HTTP response 3xx với header `Location` | `status().is3xxRedirection()`, `redirectedUrl(...)` |
| Flash attribute | Dữ liệu tạm để chuyển sang request đích, thường lưu qua session | `flash().attribute(...)` ở POST, `model().attribute(...)` ở GET sau redirect |
| Session attribute | Trạng thái gắn với session, có thể tồn tại qua nhiều request | `session().attribute(...)` với cùng `MockHttpSession` |

**Chú ý:** `MockMvc` dùng request/response Servlet mô phỏng, có thể thực hiện MVC, view resolution và render template được cấu hình, nhưng không mở HTTP socket. `andExpect(redirectedUrl(...))` kiểm tra thông tin chuyển hướng; nó **không tự thực hiện GET tiếp theo**. Muốn kiểm tra PRG đầy đủ, bài test phải gọi GET riêng và mang cùng session.

## 2. Chuẩn bị một ví dụ chạy được

Cấu trúc ví dụ:

```text
src/main/java/com/example/webtest/
  WebTestApplication.java
  SignupForm.java
  SignupController.java
src/main/resources/templates/signup/
  form.html
  done.html
src/test/java/com/example/webtest/
  SignupControllerWebTest.java
```

Dùng một dự án Spring Boot 3.3 (Java 17+) với các dependency Gradle sau; phần cấu hình plugin/version Spring Boot của project vẫn do build chính quản lý:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

`spring-boot-starter-validation` cung cấp implementation Bean Validation cho `@NotBlank`/`@Valid`. `spring-boot-starter-thymeleaf` có tác dụng render view thật bằng template; nhờ vậy ngoài tên view và model, có thể assert nội dung HTML. Nếu ứng dụng thật dùng JSP hoặc một view resolver khác, phải cấu hình engine và môi trường tương ứng; đừng tự suy rằng tên view đúng đồng nghĩa file template tồn tại.

Tệp `WebTestApplication.java`:

```java
package com.example.webtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebTestApplication.class, args);
    }
}
```

Tệp `SignupForm.java`:

```java
package com.example.webtest;

import jakarta.validation.constraints.NotBlank;

public class SignupForm {
    @NotBlank(message = "Tên không được để trống")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

Tệp `SignupController.java`:

```java
package com.example.webtest;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/signup")
public class SignupController {

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "signup/form";
    }

    @PostMapping
    public String submit(
            @Valid @ModelAttribute("signupForm") SignupForm form,
            BindingResult errors,
            RedirectAttributes redirect,
            HttpSession session) {

        if (errors.hasErrors()) {
            return "signup/form";
        }

        session.setAttribute("lastName", form.getName());
        redirect.addFlashAttribute("notice", "Đăng ký thành công");
        return "redirect:/signup/done";
    }

    @GetMapping("/done")
    public String done(Model model, HttpSession session) {
        model.addAttribute("lastName", session.getAttribute("lastName"));
        return "signup/done";
    }

    @GetMapping("/reset")
    public String reset(HttpSession session) {
        session.removeAttribute("lastName");
        return "redirect:/signup";
    }
}
```

Đặt `BindingResult` **ngay sau** tham số `@Valid @ModelAttribute` mà nó kiểm tra. Khi dữ liệu không hợp lệ, controller trả lại form và các lỗi binding/validation trong model; nếu đẩy `BindingResult` ra vị trí không phù hợp hoặc không xử lý lỗi, hành vi có thể chuyển thành exception thay vì hiển thị lỗi trong form.

Mã trên cố ý chỉ ghi một tên vào session để minh họa; ứng dụng thực tế cần xử lý nghiệp vụ, phân quyền và dữ liệu nhạy cảm theo yêu cầu hệ thống. Không dùng `HttpSession` làm kho dữ liệu dùng chung giữa các người dùng.

Tệp `templates/signup/form.html`:

```html
<!doctype html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head><meta charset="UTF-8"><title>Đăng ký</title></head>
<body>
<h1>Nhập tên</h1>
<form th:action="@{/signup}" th:object="${signupForm}" method="post">
    <label for="name">Tên</label>
    <input id="name" type="text" th:field="*{name}">
    <p th:if="${#fields.hasErrors('name')}"
       th:errors="*{name}">Lỗi nhập tên</p>
    <button type="submit">Gửi</button>
</form>
</body>
</html>
```

Tệp `templates/signup/done.html`:

```html
<!doctype html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head><meta charset="UTF-8"><title>Kết quả</title></head>
<body>
<h1>Đăng ký</h1>
<p id="notice" th:text="${notice}">Thông báo</p>
<p id="name" th:text="${lastName}">Tên đã nhập</p>
</body>
</html>
```

Thymeleaf tự dùng `BindingResult` để render lỗi của `th:errors`. Model attribute là dữ liệu server gửi cho view; trình duyệt không có quyền truy cập trực tiếp object Java. Template tương ứng mới cho phép chuyển nội dung ấy thành HTML.

## 3. Kiểm thử GET: tên view, model và HTML được render

Sử dụng `@WebMvcTest(SignupController.class)` để nạp web slice cùng `MockMvc`. Test này kiểm tra route, binding, validation, view resolver/template sẵn có trong slice và session mô phỏng; nó không tạo server thật và không tự chứng minh các dịch vụ bên ngoài.

Tệp `src/test/java/com/example/webtest/SignupControllerWebTest.java`:

```java
package com.example.webtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.session;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(SignupController.class)
class SignupControllerWebTest {

    @Autowired
    MockMvc mvc;

    @Test
    void getFormReturnsViewModelAndRenderedHtml() throws Exception {
        mvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup/form"))
                .andExpect(model().attributeExists("signupForm"))
                .andExpect(content().string(containsString("Nhập tên")))
                .andExpect(content().string(containsString("name=\"name\"")));
    }

    @Test
    void invalidFormShowsFieldErrorWithoutRedirect() throws Exception {
        mvc.perform(post("/signup").param("name", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("signup/form"))
                .andExpect(model().attributeHasFieldErrors(
                        "signupForm", "name"))
                .andExpect(content().string(
                        containsString("Tên không được để trống")));
    }

    @Test
    void successRedirectsAndCarriesFlashAcrossOneGet() throws Exception {
        MockHttpSession browserSession = new MockHttpSession();

        mvc.perform(post("/signup")
                        .session(browserSession)
                        .param("name", "An"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/signup/done"))
                .andExpect(redirectedUrl("/signup/done"))
                .andExpect(flash().attribute(
                        "notice", "Đăng ký thành công"))
                .andExpect(session().attribute("lastName", "An"));

        mvc.perform(get("/signup/done").session(browserSession))
                .andExpect(status().isOk())
                .andExpect(view().name("signup/done"))
                .andExpect(model().attribute(
                        "notice", "Đăng ký thành công"))
                .andExpect(model().attribute("lastName", "An"))
                .andExpect(content().string(
                        containsString("Đăng ký thành công")));

        mvc.perform(get("/signup/done").session(browserSession))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("notice"))
                .andExpect(model().attribute("lastName", "An"));
    }

    @Test
    void resetRemovesPersistentSessionAttribute() throws Exception {
        MockHttpSession browserSession = new MockHttpSession();
        browserSession.setAttribute("lastName", "An");

        mvc.perform(get("/signup/reset").session(browserSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signup"))
                .andExpect(session().attributeDoesNotExist("lastName"));
    }
}
```

Chạy test với Gradle wrapper từ thư mục project:

```bash
./gradlew test --tests com.example.webtest.SignupControllerWebTest
```

Trên Windows PowerShell, có thể dùng `.\gradlew.bat test --tests com.example.webtest.SignupControllerWebTest`. Các assertion ở trên mô tả **kết quả mong đợi** của ví dụ, không phải log chạy test được công bố. Dự án cần có đúng dependency, class và template đã trình bày; nếu thiếu template, lỗi có thể xuất hiện ở khâu resolve/render view dù controller chọn đúng logical view name.

### Tại sao phải assert cả view và HTML?

`view().name("signup/form")` chứng minh handler chọn logical view phù hợp. Nó chưa chứng minh view engine tìm được template và render đúng nội dung. Khi Thymeleaf được nạp cùng các tệp `form.html` và `done.html`, kiểm tra `content().string(...)` bổ sung chứng cứ HTML trả về có các phần quan trọng.

Đừng so toàn bộ chuỗi HTML nếu mục đích chỉ là xác nhận một nhãn, lỗi validation hay trường input; format whitespace, thứ tự attribute HTML hoặc layout có thể thay đổi mà chức năng vẫn đúng. Nếu cần xác thực cấu trúc DOM và hành vi JavaScript, dùng công cụ test HTML/DOM hoặc trình duyệt phù hợp; `MockMvc` không thực thi JavaScript trình duyệt.

## 4. Kiểm thử form binding và validation

Trong ví dụ, `post("/signup").param("name", "   ")` tạo Servlet request parameter đã được decode; MVC binder gán giá trị vào `SignupForm.name` trước khi `@Valid` kiểm tra `@NotBlank`. Các trường hợp quan trọng nên tách rành mạch:

- **Form hợp lệ:** attribute `signupForm` bind được, `BindingResult` không có lỗi, controller redirect.
- **Form lỗi validation:** MVC vẫn bind được giá trị nhưng constraint không đạt, controller render lại form cùng field error; không gọi thành công nghiệp vụ.
- **Form lỗi chuyển đổi kiểu:** một trường số nhận ký tự không hợp lệ có thể sinh lỗi binding/conversion; cần assert đúng field/object error, không mặc nhiên coi đó là cùng một lỗi `@NotBlank`.
- **Thiếu parameter:** tùy kiểu dữ liệu và constraint, field có thể là `null` hoặc giá trị mặc định; test cần phản ánh hợp đồng thực tế, không dựa vào suy đoán.
- **Lỗi giả dữ liệu từ test:** `.param("name", "An")` thêm request parameter mô phỏng; nó không kiểm tra browser thật đã gửi form hay cơ chế multipart/form encoding ở tầng mạng.

Một assertion tập trung vào form hợp lệ có thể dùng `model().attributeHasNoErrors("signupForm")` khi response thực sự có thuộc tính form trong model. Trong mẫu thành công, controller trả **redirect**, vì vậy assert redirect và flash/session là đúng trọng tâm hơn việc kỳ vọng form model còn tồn tại ở response chuyển hướng.

Có thể dùng `@InitBinder` và custom validator nếu nghiệp vụ đòi hỏi. Lúc đó cần kiểm tra cùng cấu hình binder/validator với ứng dụng; tạo controller trực tiếp trong unit test sẽ không tự đi qua data binder MVC.

## 5. Redirect: kiểm tra Location và PRG

Trong controller, `return "redirect:/signup/done"` yêu cầu Spring MVC tạo redirect response. Test nên xác minh cả response là 3xx và URL đích:

```java
mvc.perform(post("/signup").param("name", "An"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/signup/done"));
```

Đoạn kiểm tra độc lập này chỉ minh họa matcher; test đầy đủ ở phần trước dùng cùng `MockHttpSession` để theo dấu state. Nếu muốn xác nhận đúng mã HTTP cụ thể do contract quy định, có thể assert `status().isFound()` cho 302, hoặc mã 303 khi ứng dụng chủ động trả “See Other”. Không nên cho rằng Spring MVC mặc định luôn dùng 303 chỉ vì code theo PRG.

`redirectedUrl(...)` kiểm tra URL trong redirect; `header().string("Location", "...")` cũng có thể dùng nếu muốn kiểm tra trực tiếp header. `view().name("redirect:/signup/done")` là tên view logic phía MVC; HTTP client thật không thấy tên view logic này, nó thấy response status và header.

`RedirectAttributes` có hai đường truyền cần phân biệt:

```java
// Minh họa trong handler xử lý redirect:
redirect.addAttribute("page", 2);          // Có thể đi vào path/query URL
redirect.addFlashAttribute("notice", "OK"); // Qua FlashMap, không nằm trong URL
```

`addAttribute` dùng giá trị để mở rộng URI template hoặc tạo query parameter tùy target redirect và cấu hình. `addFlashAttribute` lưu dữ liệu tạm cho request sau chuyển hướng (thường dựa trên HTTP session). Không đặt password/token hay dữ liệu nhạy cảm trong query string chỉ để truyền từ POST sang GET; URL có thể xuất hiện trong log, history và referrer.

## 6. Flash attributes: hai phía và đúng một lần sử dụng

Flash attributes là dữ liệu được giữ **giữa các request** để hiển thị một thông báo sau redirect. Spring MVC quản lý chúng qua `FlashMap` và `FlashMapManager`. Controller dùng `RedirectAttributes.addFlashAttribute` nên thông thường không phải thao tác trực tiếp với `FlashMap`.

Có hai phép đo khác nhau:

1. **Output flash map của POST:** `flash().attribute("notice", "...")` cho thấy handler đã chuẩn bị thông báo để chuyển tiếp.
2. **Input/model của GET đích:** `model().attribute("notice", "...")` trên `GET /signup/done` chứng minh request đích đã nhận thông báo.

Phép đo thứ ba trong ví dụ, cùng session gọi `GET /signup/done` thêm lần nữa rồi assert `model().attributeDoesNotExist("notice")`, kiểm tra đặc trưng dùng một lần. Session `lastName` vẫn còn, nên thấy rõ flash khác dữ liệu session bền hơn.

**Không đánh đồng:** việc viết `.flashAttr("notice", "OK")` ngay trên request GET trong một test là cách **tiêm sẵn input flash attribute** để kiểm tra view đích; nó hữu ích để cô lập view, nhưng không chứng minh POST đã tạo flash hay `FlashMapManager` đã lưu rồi tiêu thụ nó. Muốn kiểm tra trọn PRG như bài này, thực hiện POST và GET thật sự bằng hai `MockMvc.perform` có cùng `MockHttpSession`.

Spring MVC gắn metadata của redirect target vào flash map nhằm hạn chế request khác lấy nhầm thông báo. Dù vậy, các request song song (polling, asset hoặc AJAX trong một số trường hợp) có thể tạo tình huống đua; không dùng flash như một hàng đợi nghiệp vụ có yêu cầu giao hàng chính xác hoặc làm nơi lưu kết quả dài hạn.

## 7. Session: chia sẻ đúng một phiên qua nhiều request

Một HTTP session có thể giữ state giữa nhiều request của cùng client. Với test `MockMvc`, hai lời gọi `perform` độc lập không tự biểu diễn cùng một trình duyệt nếu bạn không tái sử dụng session. Vì vậy ta tạo một `MockHttpSession` và đưa nó vào các request bằng `.session(browserSession)`:

```java
MockHttpSession browserSession = new MockHttpSession();
browserSession.setAttribute("theme", "dark");

mvc.perform(get("/signup/done").session(browserSession))
        .andExpect(session().attribute("theme", "dark"));
```

Ở ứng dụng chạy thật, client thường gắn session id bằng cookie như `JSESSIONID` và server quản lý dữ liệu thuộc phiên đó; tên cookie, session store và chính sách bảo vệ còn phụ thuộc cấu hình Servlet container/Spring Session. `MockHttpSession` chỉ kiểm tra trạng thái ở tầng Servlet mô phỏng, không chứng minh cookie của trình duyệt được bảo vệ bởi `Secure`, `HttpOnly`, `SameSite`, hoặc session store phân tán hoạt động đúng.

Để kiểm tra session được dọn đúng lúc, endpoint `/signup/reset` gọi `session.removeAttribute("lastName")`; test dùng `session().attributeDoesNotExist("lastName")`. Nếu ứng dụng cần kết thúc **toàn bộ** phiên, `session.invalidate()` là thao tác khác, có tác động tới tất cả thuộc tính session; hãy kiểm tra theo chính sách logout và bảo mật của ứng dụng.

### HttpSession khác @SessionAttributes

`HttpSession` là Servlet API cho dữ liệu gắn với phiên. `@SessionAttributes` trên lớp `@Controller` lại là cơ chế Spring MVC đưa một số **model attributes do controller quản lý** vào session để hỗ trợ nhiều bước form/wizard:

```java
// Ví dụ ý tưởng; không phải controller hoàn chỉnh chạy độc lập.
@Controller
@SessionAttributes("draft")
class DraftController {
    // Model có thuộc tính "draft" sẽ được lưu trong session.
    // Khi kết thúc hội thoại, handler nhận SessionStatus:
    // status.setComplete();
}
```

`SessionStatus.setComplete()` kết thúc việc lưu các model attributes mà controller khai báo với `@SessionAttributes`; nó **không có nghĩa** invalidate toàn bộ `HttpSession` và không thay thế logic logout. Với form nhiều bước, test nên kiểm tra draft sống qua các request cần thiết và được dọn sau `setComplete()`; với form một bước ở ví dụ này, dùng `HttpSession` trực tiếp đủ để làm rõ state.

Dữ liệu session có thể tạo phụ thuộc giữa test cases. Tạo `MockHttpSession` mới cho từng test hoặc dọn fixture rõ ràng; không dùng một static session chung khiến thứ tự chạy test ảnh hưởng kết quả.

## 8. Những điểm cần đặc biệt lưu ý khi viết test thực tế

**Cấu hình web slice:** `@WebMvcTest` là annotation của Spring Boot; nó nạp phần MVC phù hợp, không nạp toàn bộ service/repository của ứng dụng. Controller có dependency ngoài slice cần mock rõ ràng (với Boot 3.3 thường là `@MockBean`). Muốn kiểm tra kết nối bean thật, cân nhắc `@SpringBootTest` kết hợp `@AutoConfigureMockMvc`; vẫn không có HTTP socket trừ khi dùng server thật và một client HTTP mạng. Các matcher view, model, flash và session vẫn hữu ích trong MockMvc có context lớn hơn.

**Security/CSRF:** nếu thêm `spring-boot-starter-security`, POST form có thể bị chặn 403 trước khi vào controller nếu thiếu CSRF token hoặc quyền. Khi muốn kiểm tra flow có security, thêm dependency `spring-security-test` và cung cấp xác thực/CSRF đúng với tình huống kiểm thử, ví dụ `post("/signup").with(csrf())` với static import `org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf`. Đừng tắt security filter chỉ để một test form xanh rồi xem như đã kiểm tra bảo mật.

**Tên view so với HTTP thực:** với `MockMvc`, `view().name(...)` và `model()` là assertion thuộc tầng Spring MVC; với HTTP client chạy qua cổng thật, chỉ có status/header/body/cookie và hành vi public quan sát được. Nếu endpoint phục vụ SPA hoặc response JSON, việc assert logical view không còn là hợp đồng tương ứng.

**Redirect chain:** `MockMvc` không tự theo redirect. Test có thể cần nhiều `perform` liên tiếp; một test chỉ kiểm tra mã 302 sẽ bỏ sót flash không tới đích, session bị mất hoặc view GET lỗi.

**Dữ liệu form và encoding:** `.param(...)` cung cấp parameter đã decode. Nếu đặc tả yêu cầu xử lý UTF-8, kích thước payload, multipart file upload hoặc proxy/client thật, cần thêm test phù hợp với tầng mà vấn đề có thể xảy ra.

**Lỗi validation:** không assert mỗi `status().isOk()` ở POST lỗi rồi kết luận người dùng đã được báo lỗi. Phải kiểm tra `BindingResult` và/hoặc thông báo HTML hiển thị. Kiểm thử chuyển đổi kiểu và lỗi business (ví dụ tên đã tồn tại) là các tình huống khác nhau; mỗi tình huống cần kết quả kỳ vọng riêng.

**Độ tin cậy của session:** flash thường dùng session như nơi lưu tạm nhưng có vòng đời khác session attribute. Tránh dùng flash để giữ một đối tượng lớn hoặc trạng thái quan trọng phải tồn tại qua nhiều request; tránh giữ tài nguyên không serializable nếu hệ thống dự định dùng session store phân tán.

## 9. Checklist trước khi kết luận một luồng form đã được kiểm thử

| Bước | Câu hỏi chứng minh | Assertion điển hình |
| --- | --- | --- |
| GET form | Có chọn đúng view và cung cấp form object? | `view().name`, `model().attributeExists` |
| Render form | HTML có field và nhãn cần thiết? | `content().string(containsString(...))` |
| POST sai | Có bind/validate, trả lại form và lỗi field? | `attributeHasFieldErrors`, `view().name` |
| POST đúng | Có redirect thay vì render trực tiếp trang kết quả? | `is3xxRedirection`, `redirectedUrl` |
| Flash sau POST | Thông báo được đặt vào output flash? | `flash().attribute` |
| GET sau redirect | Request đích nhận thông báo và hiển thị? | `model().attribute` và HTML content |
| GET tiếp theo | Thông báo tạm không xuất hiện lần nữa? | `attributeDoesNotExist` |
| Cùng session | Thuộc tính ổn định qua nhiều request? | Tái sử dụng `MockHttpSession`, `session().attribute` |
| Dọn state | Thuộc tính đã xóa khi workflow kết thúc? | `session().attributeDoesNotExist` |

Tách các câu hỏi này ra sẽ giúp phân biệt lỗi controller/model, lỗi render template, lỗi validation, lỗi chuyển hướng và lỗi vòng đời state. Bài test mạnh là bài test đưa ra đúng **bằng chứng cho hợp đồng người dùng cần**, với bối cảnh MVC được cấu hình tương ứng.

### Tài liệu tham khảo

- Spring Framework — MockMvc expectations: https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/expectations.html
- Spring Framework — MockMvc requests: https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/requests.html
- Spring Framework — Redirect attributes: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/redirecting-passing-data.html
- Spring Framework — Flash attributes: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/flash-attributes.html
- Spring Framework — @SessionAttributes: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/sessionattributes.html
- Spring Boot 3.3 — Testing Spring Boot applications: https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-testing-006">Kiểm thử multipart, CORS và yêu cầu có điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử multipart, CORS và yêu cầu có điều kiện trong Spring Web

## 1. Phạm vi và mục tiêu

Ba nhóm request sau rất dễ “trông có vẻ chạy” nhưng vẫn sai ở biên giao thức:

1. **Multipart:** HTTP request gồm nhiều phần (file và dữ liệu form/JSON). Test phải xác minh tên part, tên file, content type của từng part, nội dung, quy tắc validation và kết quả xử lý; đồng thời hiểu khác biệt giữa mock multipart và việc parse dữ liệu thật.
2. **CORS (Cross-Origin Resource Sharing):** browser hỏi server xem một origin khác có được truy cập tài nguyên không. Cần kiểm thử **preflight OPTIONS** và **actual request**; kết quả HTTP 200 không đủ để chứng minh trình duyệt sẽ cho JavaScript đọc response.
3. **Conditional request:** client gửi các header như `If-None-Match`, `If-Modified-Since` để tránh tải lại dữ liệu không thay đổi, hoặc `If-Match` để ngăn ghi đè một version đã cũ. Test phải phân biệt `200`, `304 Not Modified`, `412 Precondition Failed` và khi cần `428 Precondition Required`.

Bài này ưu tiên **Spring MVC (Servlet stack)** với `MockMvc`, JUnit 5 và Spring Boot 3.x / Java 17+. Cuối bài có hướng dẫn tương đương cho WebFlux / `WebTestClient` và các kiểm thử chạy trên server thật.

Nhớ ba lớp cần kiểm tra:

| Lớp | Câu hỏi |
| --- | --- |
| HTTP protocol | Method, URI, multipart boundary, status và headers có đúng không? |
| Spring handling | Controller binding, converters, validation, CORS configuration, cache condition logic có chạy đúng không? |
| Browser/container/infrastructure | Trình duyệt có chấp nhận CORS không, servlet container có parse multipart thật không, proxy/caches có xử lý headers đúng không? |

`MockMvc` rất hiệu quả cho lớp thứ hai nhưng không thay thế mọi kiểm thử ở lớp cuối cùng.

## 2. Chọn kiểu kiểm thử phù hợp

- **Unit test thuần Java:** gọi service/controller method trực tiếp để kiểm tra nghiệp vụ; không kiểm chứng request mapping, multipart argument binding, CORS handler hoặc status/headers do MVC sinh ra.
- **`MockMvc` với `@WebMvcTest`:** chạy MVC dispatching qua mock Servlet request/response, load một phần web application context; phù hợp cho request binding, controller advice, converters, validation, preflight/actual CORS và conditional responses.
- **`MockMvcBuilders.standaloneSetup(...)`:** nhanh, kiểm thử controller được chỉ định, nhưng config/filters/advice không được tự động nạp toàn bộ như app. Nếu muốn kiểm tra CORS global qua `WebMvcConfigurer` hoặc Spring Security filter chain, phải đưa đúng cấu hình vào.
- **`@SpringBootTest` + `@AutoConfigureMockMvc`:** giữ toàn bộ Spring context và phần lớn filters, hữu ích khi endpoint phụ thuộc security/DB/service khác. Vẫn dùng mock Servlet request, chưa phát HTTP socket thật.
- **`@SpringBootTest(webEnvironment=RANDOM_PORT)` + HTTP client thật:** chạy Servlet container và multipart parser, giúp phát hiện giới hạn kích thước, lỗi encoding/boundary, proxy/client behavior, networking. Dùng `TestRestTemplate` hoặc HTTP test client thích hợp; thêm browser test nếu cần xác nhận chính sách CORS của trình duyệt.

Với code bên dưới, dùng `@WebMvcTest` để chạy kiểm thử nhanh, ổn định, rồi bổ sung smoke/integration test với server thật cho các đường đi mà mock không mô phỏng.

## 3. Multipart: điều cần hiểu trước khi viết test

Một request có `Content-Type: multipart/form-data; boundary=...` chứa nhiều part. Mỗi part có thể có `Content-Disposition`, tên field, filename, `Content-Type` và nội dung riêng. Ví dụ:

~~~http
POST /api/upload HTTP/1.1
Content-Type: multipart/form-data; boundary=xyz

--xyz
Content-Disposition: form-data; name="file"; filename="notes.txt"
Content-Type: text/plain

hello
--xyz
Content-Disposition: form-data; name="tag"

quarterly
--xyz
Content-Disposition: form-data; name="metadata"
Content-Type: application/json

{"description":"report"}
--xyz--
~~~

Ba kiểu binding thường gặp trong MVC:

- `@RequestPart("file") MultipartFile file`: đọc file part, lấy `getOriginalFilename()`, `getContentType()`, `getSize()`, `getBytes()` hoặc stream.
- `@RequestParam("tag") String tag`: đọc text form field.
- `@RequestPart("metadata") Metadata metadata`: đọc một **part JSON** bằng HTTP message converter; part phải có content type phù hợp, thường `application/json`.

`@RequestParam` cho text form và `@RequestPart` cho JSON object không phải hoàn toàn thay thế nhau. Nếu server kỳ vọng JSON part mà client chỉ gửi form text thường, converter có thể trả lỗi media type/binding.

### Dựng request đúng bằng MockMvc

~~~java
MockMultipartFile file = new MockMultipartFile(
        "file", "notes.txt", "text/plain",
        "hello".getBytes(StandardCharsets.UTF_8));

mockMvc.perform(multipart("/api/upload")
        .file(file)
        .param("tag", "quarterly"))
        .andExpect(status().isOk());
~~~

`multipart(...)` tạo `MockMultipartHttpServletRequest` đã chứa parts/file do test cung cấp. **MockMvc không tự parse một luồng raw multipart có boundary**: test này chứng minh controller và binding hoạt động với mock parts đã dựng, nhưng không xác minh parser thực tế, max file size ở Servlet container hay khả năng gửi multipart từ browser/client thật.

### JSON part bên cạnh file

~~~java
MockMultipartFile file = new MockMultipartFile(
        "file", "notes.txt", MediaType.TEXT_PLAIN_VALUE,
        "hello".getBytes(StandardCharsets.UTF_8));

MockMultipartFile metadata = new MockMultipartFile(
        "metadata", "", MediaType.APPLICATION_JSON_VALUE,
        "{\"description\":\"report\"}"
            .getBytes(StandardCharsets.UTF_8));

mockMvc.perform(multipart("/api/upload")
        .file(file)
        .file(metadata)
        .param("tag", "quarterly"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("report"));
~~~

Tên `metadata` phải trùng `@RequestPart("metadata")`; khi dùng DTO JSON, part content type phải tương thích với converter. Tránh thử JSON part chỉ bằng `.param("metadata", "...")` rồi kết luận rằng JSON deserialization đã được kiểm chứng.

### Những case multipart quan trọng

| Test | Dữ liệu vào | Kỳ vọng |
| --- | --- | --- |
| Happy path | File đúng key, content type, tag và optional JSON part | 2xx cùng các trường trả về đúng |
| Thiếu part bắt buộc | Không có `file` | Thường `400 Bad Request` nếu `@RequestPart(required=true)` |
| File rỗng | File có bytes rỗng | Status theo validation của ứng dụng, ví dụ 400 |
| Sai content type | File `application/octet-stream` khi nghiệp vụ chỉ nhận `text/plain` | 415 hoặc 400 tùy contract |
| Sai field name | Gửi `upload` thay vì `file` | Thiếu part `file` → 400 |
| JSON part không hợp lệ | `metadata` có JSON sai cú pháp hoặc kiểu | Lỗi conversion/binding, thường 400 |
| File vượt giới hạn | File thật vượt `spring.servlet.multipart.max-file-size` | Test end-to-end để đi qua multipart parser/Servlet container |
| Filename nguy hiểm | `../secret.txt`, tên rất dài, Unicode | Không tin `getOriginalFilename()`; sanitize và kiểm soát path server-side |
| Kích thước/bộ nhớ | File lớn, nhiều parts | Test integration/resource limits, không chỉ assert trên mock |

Không so sánh chuỗi raw multipart bao gồm boundary do client sinh ngẫu nhiên. Ưu tiên assert ý nghĩa từng part hoặc ghi nhận request qua HTTP server thật.

## 4. CORS: preflight khác actual request thế nào?

**Origin** = scheme + host + port. `https://ui.example` và `https://api.example` là hai origin khác nhau dù cùng dùng HTTPS. CORS là chính sách của **browser** về việc JavaScript ở origin này đọc response từ origin khác; nó không thay thế server-side authentication/authorization.

Một preflight thường là request `OPTIONS` với:

~~~http
OPTIONS /api/upload HTTP/1.1
Origin: https://ui.example
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type,x-trace-id
~~~

Response hợp lệ có thể chứa:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://ui.example
Access-Control-Allow-Methods: GET,POST,PUT
Access-Control-Allow-Headers: content-type,x-trace-id
Access-Control-Max-Age: 3600
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
~~~

Giá trị `Access-Control-Allow-Methods` có thể thay đổi cách trình bày và phạm vi tùy Spring/config; assert các thành phần quan trọng thay vì phụ thuộc thứ tự header không có ý nghĩa nghiệp vụ.

Preflight được Spring MVC CORS processing xử lý trực tiếp trước khi gọi business controller method. Nếu preflight không được chấp nhận, browser thường **không gửi actual request**. Nhưng một công cụ như `curl` hoặc `MockMvc` vẫn có thể cố tình gửi actual request độc lập: CORS không phải hàng rào chống non-browser clients.

Actual request:

~~~http
POST /api/upload HTTP/1.1
Origin: https://ui.example
Content-Type: multipart/form-data; boundary=...
~~~

Response cần có `Access-Control-Allow-Origin` phù hợp để browser cho frontend đọc response. Với origin không được phép, Spring có thể trả `403 Invalid CORS request`, nhưng browser cũng có thể chặn khi server trả 200 mà thiếu CORS headers; **test browser behavior không thể suy ra chỉ từ status**.

### Kiểm thử CORS nên bao gồm

1. Preflight từ origin được phép, method/header được phép: status thành công và `Access-Control-Allow-Origin`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers` đúng.
2. Actual request từ origin được phép: business endpoint chạy và response có `Access-Control-Allow-Origin`.
3. Preflight từ origin không được phép: reject (trong MVC mặc định thường 403).
4. Method hoặc requested headers không được phép: preflight reject.
5. `Vary: Origin` để shared cache không tái sử dụng sai response giữa các origin.
6. Credentialed CORS nếu có cookie/session: chỉ phản chiếu origin đã cho phép, `Access-Control-Allow-Credentials: true`; **không dùng wildcard `*` cho `allowOrigins` cùng credentials**.
7. Khi Spring Security có mặt: CORS xử lý preflight trước auth/filter bắt login; cấu hình `http.cors(...)` và CSRF riêng cho request thay đổi trạng thái.

Ví dụ MVC annotation:

~~~java
@CrossOrigin(
    origins = "https://ui.example",
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT},
    allowedHeaders = {"Content-Type", "X-Trace-Id", "If-Match"},
    exposedHeaders = {"ETag"},
    maxAge = 3600
)
@RestController
class ApiController {
    // Endpoints...
}
~~~

`exposedHeaders` quyết định header nào ngoài các CORS-safelisted response headers mà JavaScript được đọc (ví dụ `ETag`). `Access-Control-Allow-Headers` lại nói browser được gửi những request headers nào. Hai cấu hình này **khác mục đích**.

## 5. Conditional requests: 304, 412, 428

Một response GET có thể gắn validators:

~~~http
HTTP/1.1 200 OK
ETag: "v7"
Last-Modified: Wed, 01 Jan 2025 00:00:00 GMT
Content-Type: application/json

{"version":7,"content":"first"}
~~~

Client muốn kiểm tra cache:

~~~http
GET /api/doc HTTP/1.1
If-None-Match: "v7"
~~~

Nếu representation vẫn mang ETag `"v7"`, server trả `304 Not Modified`, thường không có response body; client tiếp tục dùng bản đã cache. Nếu giá trị khác, server trả nội dung mới, thường `200 OK`.

Header ngày tháng tương ứng:

~~~http
GET /api/doc HTTP/1.1
If-Modified-Since: Wed, 01 Jan 2025 00:00:00 GMT
~~~

Nếu tài nguyên chưa sửa kể từ ngày đó, server có thể trả 304. HTTP dates chỉ có **độ chính xác theo giây**; ETag thường phù hợp hơn cho những tài nguyên thay đổi nhiều lần trong một giây. `If-None-Match` được ưu tiên hơn `If-Modified-Since` khi cả hai cùng xuất hiện.

### ETag: strong/weak và dấu ngoặc kép

- Strong validator: `ETag: "v7"`. Dùng để so identity representation và làm precondition chống ghi đè.
- Weak validator: `ETag: W/"v7"`. Hữu ích cho cache validation theo ngữ nghĩa; **không dùng weak comparison để thỏa `If-Match`** vì `If-Match` yêu cầu strong comparison.
- Trong Spring, `ResponseEntity.ok().eTag("v7")` có thể tự thêm dấu ngoặc kép, nhưng trong test nên assert chính xác HTTP header `"v7"`.
- ETag phải thay đổi phù hợp với representation/version thực tế. Một ETag cố định cho dữ liệu thay đổi là bug của cache contract.

### Tránh lost update với If-Match

Hai client cùng đọc version 7; client A cập nhật thành version 8. Nếu B ghi đè dựa vào bản version 7, server nên từ chối:

~~~http
PUT /api/doc HTTP/1.1
If-Match: "v7"
Content-Type: application/json

{"content":"client B's edit"}
~~~

Khi ETag hiện tại đã là `"v8"`, response là `412 Precondition Failed` và không được thực hiện thay đổi. Nếu nghiệp vụ yêu cầu client **bắt buộc** dùng If-Match mà request thiếu header, có thể trả `428 Precondition Required`.

| Kịch bản | Kỳ vọng |
| --- | --- |
| GET không điều kiện | 200 + body + ETag/Last-Modified |
| GET + `If-None-Match` trùng | 304, body rỗng |
| GET + `If-None-Match` cũ | 200, body mới |
| GET + `If-Modified-Since` đủ mới | 304, body rỗng |
| PUT + `If-Match` đúng strong ETag | Ghi version mới; response theo API, ví dụ 204 |
| PUT + `If-Match` cũ/sai | 412, **không cập nhật state** |
| PUT thiếu `If-Match` mà API yêu cầu | 428, không cập nhật state |
| PUT + `If-Match: W/"v7"` | Không được chấp nhận như strong match |

Có hai phương án Spring MVC thường gặp:

- Trả `ResponseEntity.ok().eTag(...).lastModified(...).body(...)`, Spring kiểm tra conditional GET/HEAD dựa trên validators để tạo 304.
- Chủ động gọi `WebRequest.checkNotModified(etag, lastModified)` khi cần kiểm soát chi tiết luồng xử lý; phương thức có thể hỗ trợ 304 cho GET/HEAD và 412 cho request thao tác trạng thái.

Đối với cập nhật dữ liệu thật, kiểm tra `If-Match` phải **đồng bộ với thao tác ghi** (optimistic locking, compare-and-swap hoặc WHERE version = ? trong DB). Chỉ `if (currentVersion.equals(ifMatch))` rồi ghi ở hai bước tách biệt mà không có khóa/CAS vẫn có race condition.

## 6. Dự án mẫu chạy được: Spring Boot 3.x + MockMvc

Mẫu sau đưa cả ba nhóm chức năng vào một project, dùng Java 17+ và Maven. Controller sử dụng bộ nhớ nội bộ để demo, không cần database, browser hoặc mạng bên ngoài khi chạy test.

Cấu trúc:

~~~text
web-request-tests/
  pom.xml
  src/main/java/demo/WebRequestTestsApplication.java
  src/main/java/demo/ApiController.java
  src/test/java/demo/ApiControllerTest.java
~~~

### 6.1. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>web-request-tests</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Dự án dùng JUnit 5, Spring Test và AssertJ/Hamcrest thông qua `spring-boot-starter-test`; `MockMvc` đến từ Spring Test. Không thêm Spring Security trong demo để nhìn rõ CORS MVC thuần.

### 6.2. src/main/java/demo/WebRequestTestsApplication.java

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebRequestTestsApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebRequestTestsApplication.class, args);
    }
}
~~~

### 6.3. src/main/java/demo/ApiController.java

~~~java
package demo;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin(
    origins = "https://ui.example",
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT},
    allowedHeaders = {"Content-Type", "X-Trace-Id", "If-Match"},
    exposedHeaders = {"ETag"},
    maxAge = 3600
)
public class ApiController {
    private static final long INITIAL_TIME =
            Instant.parse("2025-01-01T00:00:00Z").toEpochMilli();

    private final AtomicReference<State> document =
            new AtomicReference<>(new State(7, "first", INITIAL_TIME));

    record State(long version, String content, long modifiedAt) {
        String etag() {
            return "\"v" + version + "\"";
        }
    }

    public record Metadata(String description) {}
    public record UploadReceipt(
            String filename, long bytes, String tag, String description) {}
    public record DocResponse(long version, String content) {}
    public record UpdateRequest(String content) {}

    // Giúp test độc lập thứ tự: mỗi test khởi động từ version 7.
    void reset() {
        document.set(new State(7, "first", INITIAL_TIME));
    }

    @PostMapping(
        path = "/api/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<UploadReceipt> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("tag") String tag,
            @RequestPart(value = "metadata", required = false)
                    Metadata metadata) {

        if (file.isEmpty() || tag.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (!MediaType.TEXT_PLAIN_VALUE.equals(file.getContentType())) {
            return ResponseEntity.status(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        return ResponseEntity.ok(new UploadReceipt(
                file.getOriginalFilename(),
                file.getSize(),
                tag,
                metadata == null ? null : metadata.description()));
    }

    @GetMapping(
        path = "/api/doc",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DocResponse> getDocument() {
        State current = document.get();

        return ResponseEntity.ok()
                .eTag(current.etag())
                .lastModified(current.modifiedAt())
                .body(new DocResponse(
                        current.version(), current.content()));
    }

    @PutMapping(
        path = "/api/doc",
        consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> updateDocument(
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                    String ifMatch,
            @RequestBody UpdateRequest request) {

        if (request == null || request.content() == null
                || request.content().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (ifMatch == null) {
            return ResponseEntity.status(
                    HttpStatus.PRECONDITION_REQUIRED).build();
        }

        while (true) {
            State current = document.get();

            // Chỉ strong ETag chính xác mới được cập nhật.
            if (!current.etag().equals(ifMatch)) {
                return ResponseEntity.status(
                        HttpStatus.PRECONDITION_FAILED).build();
            }

            State next = new State(
                    current.version() + 1,
                    request.content(),
                    Instant.now().toEpochMilli());

            // CAS bảo đảm version không bị ghi đè qua hai request đua nhau.
            if (document.compareAndSet(current, next)) {
                return ResponseEntity.noContent().build();
            }
        }
    }
}
~~~

**Vì sao logic này dễ test?**

- File upload không cần ghi filesystem; có thể kiểm tra file name, size, tag, JSON part và status. Trong sản phẩm thật, phải sanitize filename, kiểm tra MIME bằng nội dung nếu cần và áp dụng giới hạn kích thước/storage.
- `@CrossOrigin` giới hạn chính xác origin demo; các request đến từ browser khác phải qua CORS processing.
- GET có strong `ETag` và `Last-Modified`; Spring MVC xử lý conditional requests khi trả `ResponseEntity`.
- PUT bắt buộc `If-Match`, dùng CAS thay vì check-and-set không nguyên tử; test có thể xác nhận state giữ nguyên sau 412.
- `reset()` chỉ hỗ trợ demo test in-memory. App production nên quản lý dữ liệu/version bằng persistence có optimistic locking.

### 6.4. src/test/java/demo/ApiControllerTest.java

~~~java
package demo;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiController.class)
class ApiControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ApiController controller;

    private static final String ALLOWED = "https://ui.example";
    private static final String DENIED = "https://evil.example";

    @BeforeEach
    void resetState() {
        controller.reset();
    }

    private MockMultipartFile textFile() {
        return new MockMultipartFile(
                "file", "notes.txt", MediaType.TEXT_PLAIN_VALUE,
                "hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void multipartFileAndFormField() throws Exception {
        mvc.perform(multipart("/api/upload")
                .file(textFile())
                .param("tag", "quarterly"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filename").value("notes.txt"))
            .andExpect(jsonPath("$.bytes").value(5))
            .andExpect(jsonPath("$.tag").value("quarterly"))
            .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void multipartWithJsonPart() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"description\":\"report\"}"
                        .getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/upload")
                .file(textFile())
                .file(metadata)
                .param("tag", "quarterly"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("report"));
    }

    @Test
    void missingFileReturns400() throws Exception {
        mvc.perform(multipart("/api/upload")
                .param("tag", "quarterly"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void emptyFileReturns400() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.txt", MediaType.TEXT_PLAIN_VALUE,
                new byte[0]);

        mvc.perform(multipart("/api/upload")
                .file(empty)
                .param("tag", "quarterly"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPartContentTypeReturns415() throws Exception {
        MockMultipartFile binary = new MockMultipartFile(
                "file", "raw.bin", "application/octet-stream",
                new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/upload")
                .file(binary)
                .param("tag", "quarterly"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void corsPreflightAllowed() throws Exception {
        mvc.perform(options("/api/upload")
                .header(HttpHeaders.ORIGIN, ALLOWED)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "content-type,x-trace-id"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
            .andExpect(header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                    containsString("POST")))
            .andExpect(header().exists(HttpHeaders.VARY));
    }

    @Test
    void corsPreflightDeniedOrigin() throws Exception {
        mvc.perform(options("/api/upload")
                .header(HttpHeaders.ORIGIN, DENIED)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden());
    }

    @Test
    void corsPreflightDeniedHeader() throws Exception {
        mvc.perform(options("/api/upload")
                .header(HttpHeaders.ORIGIN, ALLOWED)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "x-not-allowed"))
            .andExpect(status().isForbidden());
    }

    @Test
    void actualCorsRequestCarriesAllowOrigin() throws Exception {
        mvc.perform(get("/api/doc")
                .header(HttpHeaders.ORIGIN, ALLOWED))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
            .andExpect(header().string(
                    HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "ETag"));
    }

    @Test
    void actualCorsRequestDeniedOrigin() throws Exception {
        mvc.perform(get("/api/doc")
                .header(HttpHeaders.ORIGIN, DENIED))
            .andExpect(status().isForbidden());
    }

    @Test
    void unconditionalGetReturnsValidators() throws Exception {
        mvc.perform(get("/api/doc"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ETAG, "\"v7\""))
            .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
            .andExpect(jsonPath("$.version").value(7))
            .andExpect(jsonPath("$.content").value("first"));
    }

    @Test
    void matchingIfNoneMatchReturns304WithoutBody() throws Exception {
        mvc.perform(get("/api/doc")
                .header(HttpHeaders.IF_NONE_MATCH, "\"v7\""))
            .andExpect(status().isNotModified())
            .andExpect(header().string(HttpHeaders.ETAG, "\"v7\""))
            .andExpect(content().string(""));
    }

    @Test
    void staleIfNoneMatchReturns200WithBody() throws Exception {
        mvc.perform(get("/api/doc")
                .header(HttpHeaders.IF_NONE_MATCH, "\"v6\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(7));
    }

    @Test
    void ifModifiedSinceCanReturn304() throws Exception {
        mvc.perform(get("/api/doc")
                .header(HttpHeaders.IF_MODIFIED_SINCE,
                        "Wed, 01 Jan 2025 00:00:00 GMT"))
            .andExpect(status().isNotModified())
            .andExpect(content().string(""));
    }

    @Test
    void missingIfMatchReturns428() throws Exception {
        mvc.perform(put("/api/doc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"edited\"}"))
            .andExpect(status().isPreconditionRequired());

        mvc.perform(get("/api/doc"))
            .andExpect(jsonPath("$.version").value(7));
    }

    @Test
    void staleIfMatchReturns412WithoutMutation() throws Exception {
        mvc.perform(put("/api/doc")
                .header(HttpHeaders.IF_MATCH, "\"v6\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"edited\"}"))
            .andExpect(status().isPreconditionFailed());

        mvc.perform(get("/api/doc"))
            .andExpect(jsonPath("$.content").value("first"))
            .andExpect(header().string(HttpHeaders.ETAG, "\"v7\""));
    }

    @Test
    void weakIfMatchDoesNotPassStrongComparison() throws Exception {
        mvc.perform(put("/api/doc")
                .header(HttpHeaders.IF_MATCH, "W/\"v7\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"edited\"}"))
            .andExpect(status().isPreconditionFailed());
    }

    @Test
    void matchingIfMatchUpdatesVersion() throws Exception {
        mvc.perform(put("/api/doc")
                .header(HttpHeaders.IF_MATCH, "\"v7\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"edited\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/doc"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ETAG, "\"v8\""))
            .andExpect(jsonPath("$.version").value(8))
            .andExpect(jsonPath("$.content").value("edited"));
    }
}
~~~

Từng test chạy độc lập với version 7 nhờ `@BeforeEach`. Tests không dùng thứ tự phương thức và không cần sleep/clock giả để so trạng thái ngày giờ.

**Lưu ý về 428:** `MockMvcResultMatchers.status().isPreconditionRequired()` có sẵn trong các phiên bản Spring Test tương ứng với HTTP status 428; nếu dùng bản Spring khác, có thể thay bằng `status().is(428)`. Trong demo Spring Boot 3.5, API status này được Spring Test hỗ trợ.

### 6.5. Chạy test

Ở thư mục dự án, với JDK 17+ và Maven:

~~~bash
mvn test
~~~

Điều kiện pass: các method test nhận status và headers đúng như assertions; Maven hoàn tất với `BUILD SUCCESS` và failures/errors bằng 0. Kết quả số lượng test phụ thuộc file test bạn lưu; ví dụ trên có 18 phương thức `@Test`. Đây là **kết quả kỳ vọng khi code được cấu hình như mô tả**, không phải bản ghi log đã chạy thực tế.

Để kiểm tra thông điệp lỗi khi test hỏng, xem `target/surefire-reports`, output MVC `MockMvcResultHandlers.print()` trong test cần debug hoặc kiểm tra test stack trace. Không sửa assert theo giá trị tình cờ chỉ để làm test xanh; xác minh API contract trước.

## 7. Những khác biệt khiến test xanh giả

### 7.1. Mock multipart không kiểm tra boundary/parser

Bộ dựng `MockMultipartFile` tạo file part in-memory trực tiếp, không kiểm chứng quá trình HTTP parser tách bytes từ boundary. Vì vậy test unit/slice có thể pass dù upload thật thất bại vì:

- Multipart resolver/container không được bật hoặc cấu hình khác.
- `max-file-size`/`max-request-size` bị vượt.
- Boundary sai hoặc không có, content type tổng thể sai.
- Filename encoding và binary payload ngoài các trường hợp thông thường.

Cách bù: thêm ít nhất một integration test dùng HTTP client thật tới `RANDOM_PORT`, gửi multipart thực và assert response; thêm case vượt giới hạn để xác nhận HTTP status/exception mapping. Các giới hạn parser có thể được áp trước khi controller chạy.

### 7.2. CORS là browser enforcement

`MockMvc` có thể khẳng định Spring đã phát `Access-Control-Allow-Origin` hoặc từ chối preflight; nó **không phải browser**. Cần test trên trình duyệt hoặc automation thực nếu tích hợp cookie, redirect, domain/cổng khác, SameSite, `credentials: "include"`, Service Worker, proxies hoặc security filters. Một request gửi bằng `curl` từ origin cấm vẫn có thể gửi được ở cấp HTTP; browser mới thực thi CORS để ngăn JS truy cập.

### 7.3. @WebMvcTest có phạm vi hẹp

Nếu CORS được cấu hình bằng `WebMvcConfigurer` hoặc `CorsFilter` bên ngoài controller slice, test `@WebMvcTest(ApiController.class)` cần import đúng config. Nếu có Spring Security, filters có thể từ chối preflight trước khi đến MVC; dùng `@Import(SecurityConfig.class)` khi phù hợp hoặc `@SpringBootTest + @AutoConfigureMockMvc`. Với security-enabled tests, setup CSRF và user mocks chỉ khi đúng contract. Đừng dùng `@AutoConfigureMockMvc(addFilters=false)` để đánh giá hành vi CORS/security production rồi kết luận là đủ.

### 7.4. Conditional GET không giống kiểm tra cạnh tranh ghi

`GET If-None-Match` tạo 304 giúp tiết kiệm băng thông nhưng không chứng minh xử lý `PUT If-Match` đúng dưới concurrent writes. Với DB, tích hợp optimistic locking version column và test hai cập nhật cạnh tranh; bảo đảm request thua không làm đổi state. ETag cần được thay khi representation thay đổi, và cache-private/public cần phù hợp dữ liệu người dùng.

### 7.5. Không kiểm tra toàn bộ headers/body

Test chỉ `status().isOk()` không đủ cho protocol semantics. Cần kiểm tra CORS response headers, ETag/Last-Modified, empty body 304, JSON part binding và state không thay đổi sau 412/428. Status 200 cho multipart không chứng minh filename/MIME/data chính xác.

## 8. Kiểm thử thêm: method, headers và cache behavior

### Multipart bằng HTTP method khác

Theo mặc định `multipart("/api/upload")` tạo POST. Nếu endpoint chấp nhận PUT/PATCH multipart, chọn method tường minh và xác minh request mapping:

~~~java
mvc.perform(multipart("/api/upload")
        .file(textFile())
        .with(request -> {
            request.setMethod("PUT");
            return request;
        }))
    .andExpect(status().isMethodNotAllowed());
~~~

Với controller demo chỉ khai báo `@PostMapping`, `PUT` hợp lệ về mặt cú pháp request mock nhưng đường dẫn không có method PUT, nên kỳ vọng 405. Nếu nghiệp vụ cho phép PUT multipart, viết `@PutMapping` tương ứng và đổi expected status. Việc parser của container có hỗ trợ PUT multipart thật hay không vẫn phải kiểm tra end-to-end theo cấu hình Servlet/framework.

### CORS preflight và custom headers

Nếu frontend gửi `X-Trace-Id`, preflight phải liệt kê `x-trace-id` trong `Access-Control-Request-Headers`. Server chỉ phản hồi `Access-Control-Allow-Headers` cho những header được phép. Với `@CrossOrigin(allowedHeaders=...)`, test nên kiểm tra cả header được chấp nhận và header bị từ chối.

Nếu cần đọc `ETag` qua JavaScript:

~~~javascript
const response = await fetch("https://api.example/api/doc", {
    mode: "cors"
});
const etag = response.headers.get("ETag");
console.log(etag);
~~~

Trong demo `@CrossOrigin(exposedHeaders={"ETag"})` cho phép browser JS đọc ETag khi frontend chạy từ origin `https://ui.example`. Không có exposed header thì JS có thể không đọc được ETag dù DevTools vẫn nhìn thấy response header.

### Conditional headers kết hợp

Ưu tiên test `If-None-Match` khi gửi cả ETag và date. Theo HTTP semantics, `If-None-Match` có precedence hơn `If-Modified-Since`; đừng kết luận server sai khi nó bỏ qua date trong trường hợp có ETag. Thêm các case header yếu, malformed dates, HEAD, clock precision và proxy caching nếu API của bạn phụ thuộc.

### ETag được định dạng như thế nào?

`ETag: "v7"` là một header bao gồm dấu ngoặc kép; `If-None-Match: v7` thiếu quotes không tương đương một entity-tag hợp lệ. Nên khởi đầu test bằng giá trị header thật được HTTP client nhận từ GET, rồi dùng chính giá trị đó ở request kế tiếp.

## 9. Hướng kiểm thử với Spring WebFlux và WebTestClient

Nếu endpoint thuộc **Spring WebFlux** thay vì Spring MVC, dùng `@WebFluxTest` hoặc `WebTestClient` với mock/server binding phù hợp:

~~~java
webTestClient.get()
        .uri("/api/doc")
        .header(HttpHeaders.IF_NONE_MATCH, "\"v7\"")
        .exchange()
        .expectStatus().isNotModified()
        .expectBody().isEmpty();
~~~

Kiểm thử CORS preflight vẫn là `OPTIONS` với `Origin`, `Access-Control-Request-Method` và `Access-Control-Request-Headers`:

~~~java
webTestClient.options()
        .uri("/api/upload")
        .header(HttpHeaders.ORIGIN, "https://ui.example")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                "https://ui.example");
~~~

Đây là **snippets tích hợp**: `webTestClient` phải là bean/test fixture trỏ tới một WebFlux controller có cấu hình tương ứng. CORS và conditional behavior về nguyên tắc tương tự, nhưng server exchange/buffering/multipart abstractions khác MVC.

Để tạo multipart request WebTestClient, dùng `MultipartBodyBuilder`:

~~~java
MultipartBodyBuilder builder = new MultipartBodyBuilder();
builder.part("tag", "quarterly");
builder.part("file", new ByteArrayResource(
        "hello".getBytes(StandardCharsets.UTF_8)) {
    @Override
    public String getFilename() {
        return "notes.txt";
    }
}).contentType(MediaType.TEXT_PLAIN);

webTestClient.post()
        .uri("/api/upload")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(BodyInserters.fromMultipartData(builder.build()))
        .exchange()
        .expectStatus().isOk();
~~~

Snippet này cần `org.springframework.util.MultiValueMap` qua builder, `org.springframework.core.io.ByteArrayResource` và `BodyInserters`, cùng endpoint thật phù hợp. Khi test WebFlux handler, đối tượng inbound file có thể là `FilePart` thay vì Servlet `MultipartFile`. Không copy nguyên controller Servlet `MultipartFile` sang WebFlux rồi giả định đúng.

## 10. Checklist kiểm thử trước khi release

| Nhóm | Trường hợp tối thiểu | Chứng cứ |
| --- | --- | --- |
| Multipart | File đúng tên/type/content, form field và JSON part | Status + các field binding/processed đúng |
| Multipart errors | Thiếu file, rỗng, sai MIME, JSON part hỏng | 400/415 theo API |
| Multipart integration | Boundary, parser, file vượt size, Unicode filename, storage | HTTP test qua server thật |
| CORS preflight | Origin/method/header được phép | OPTIONS + CORS response headers |
| CORS rejection | Origin/header/method bị từ chối | OPTIONS rejected, không vô tình mở wildcard |
| CORS actual | GET/POST từ origin được phép | Business response + Allow-Origin |
| Credentials/Security | Cookie/session/CSRF/auth filters | Filter chain và browser integration |
| Conditional GET | Không header, ETag trùng/cũ, Last-Modified | 200 / 304 + ETag + body đúng |
| Conditional mutation | If-Match đúng/cũ/thiếu/weak | 204 / 412 / 428, state/version chuẩn |
| Concurrency | Hai request cập nhật cùng version | Chỉ một cập nhật thành công |
| Cache/proxy | Vary, Cache-Control, CDN/shared cache | Không trả dữ liệu của origin/user khác |

**Quy tắc:** test **request** (method/headers/parts), test **response** (status/headers/body), và test **state** sau request. Với multipart, thêm parser integration; với CORS, thêm browser/security validation; với conditional write, thêm atomic version/concurrency validation.

## 11. Tài liệu tham khảo

- Spring Framework — MockMvc: https://docs.spring.io/spring-framework/reference/testing/mockmvc.html
- Spring Framework — Performing Requests, Multipart: https://docs.spring.io/spring-framework/reference/6.2/testing/mockmvc/hamcrest/requests.html
- Spring Framework — Defining Expectations: https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/expectations.html
- Spring Framework — CORS (MVC): https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html
- Spring Framework — HTTP Caching (MVC): https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-caching.html
- Spring Framework — WebTestClient: https://docs.spring.io/spring-framework/reference/testing/webtestclient.html
- Spring Framework — WebFlux HTTP Caching: https://docs.spring.io/spring-framework/reference/web/webflux/caching.html
- RFC 9110 — HTTP Semantics, conditional requests: https://www.rfc-editor.org/rfc/rfc9110.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-testing-007">Kiểm thử MVC async, asyncDispatch và phản hồi streaming</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử MVC async, asyncDispatch và phản hồi streaming trong Spring MVC

## 1. Bài toán và mô hình tư duy

Một phương thức controller Spring MVC không nhất thiết trả dữ liệu ngay trên luồng Servlet đang nhận request. Nó có thể trả một công việc bất đồng bộ, sau đó Spring MVC giải phóng luồng tiếp nhận, chờ kết quả và thực hiện một lần **ASYNC dispatch** để hoàn thiện HTTP response. Đối với dữ liệu liên tục hoặc dung lượng lớn, controller còn có thể trả **streaming response** bằng `ResponseBodyEmitter`, `SseEmitter` hoặc `StreamingResponseBody`.

Một bài kiểm thử tốt phải xác minh đúng **cả vòng đời**, thay vì chỉ thấy status 200 ở lần gọi MockMvc đầu tiên. Hai lỗi thường gặp là kiểm tra body trước khi kết quả async sẵn sàng và coi MockMvc là một HTTP server thật có thể đo thời điểm từng chunk dữ liệu đến client.

Sơ đồ:

```text
Test sử dụng MockMvc                       Spring MVC (Servlet stack)
        |                                            |
        | perform(GET /async)                        |
        |------------------------------------------->|
        |                                  DispatcherServlet gọi controller
        |                                             |
        |                                controller trả Callable /
        |                                DeferredResult / Mono ...
        |                                             |
        |                                  request.startAsync()
        |<-------- request().asyncStarted() ----------|
        |                                             |
        |                  Nguồn async tính toán kết quả
        |                                             |
        |  request().asyncResult(...) / getAsyncResult |
        |                   chờ có kết quả             |
        |                                             |
        | perform(asyncDispatch(mvcResult)) ---------->|
        |                                  DispatcherServlet xử lý lại
        |                                  kết quả, lỗi, converter
        |<------------- HTTP status/headers/body -----|
        |                                             |
        v                                             v
 Test kiểm tra response cuối cùng       Với stream: nhiều lần ghi/flush
```

Đối tượng chính của bài:

- `MockMvc` mô phỏng Servlet API và `DispatcherServlet` để kiểm thử Spring MVC mà không khởi động socket HTTP.
- `MvcResult` giữ request/response giả lập và trạng thái xử lý async của một lượt `perform`.
- `request().asyncStarted()` kiểm tra controller đã khởi động async handling.
- `request().asyncResult(...)` kiểm tra giá trị async hoàn tất (có thể chờ kết quả).
- `asyncDispatch(mvcResult)` dựng lượt xử lý tiếp theo có dispatcher type `ASYNC` để Spring MVC hoàn thiện xử lý kết quả.
- `StreamingResponseBody` viết trực tiếp vào `OutputStream`; `ResponseBodyEmitter` và `SseEmitter` phát nhiều phần qua message converters và định dạng SSE.

**Phạm vi:** Spring MVC trên **Servlet stack**, không phải WebFlux với `WebTestClient.bindToController`. Java 17+ và Spring Framework 6.x/Spring Boot 3.x là cơ sở của ví dụ; API MockMvc nền tảng được Spring duy trì ở các bản sau.

## 2. Async MVC khác request đồng bộ và khác WebFlux thế nào?

Trong xử lý đồng bộ, controller trả kết quả ngay và `DispatcherServlet` hoàn thành response trong lượt dispatch ban đầu. Trong xử lý async:

1. Controller trả một kiểu được MVC hỗ trợ như `Callable<T>`, `DeferredResult<T>`, `WebAsyncTask<T>` hoặc một reactive type đơn giá trị (`Mono<T>` khi có adapter phù hợp).
2. Spring gọi `startAsync()` trên Servlet request. Luồng Servlet ban đầu thoát để phục vụ công việc khác trong khi response vẫn mở.
3. Một luồng/callback bên ngoài tạo kết quả, hoặc có lỗi/timeout.
4. Spring thực hiện **ASYNC dispatch** qua Servlet container để xử lý giá trị như một return value MVC, áp dụng `HttpMessageConverter`, exception resolver và hoàn tất response.

| Kiểu controller trả về | Ai tạo kết quả? | Điều quan trọng khi test |
| --- | --- | --- |
| `Callable<T>` | Task executor của Spring MVC thực thi callback trên thread khác | Kiểm tra async bắt đầu, đợi kết quả, dispatch và xác minh response |
| `DeferredResult<T>` | Ứng dụng chủ động gọi `setResult` / `setErrorResult` từ callback/thread khác | Trong test có thể chủ động hoàn thành, tránh phụ thuộc thời gian |
| `WebAsyncTask<T>` | Callable kết hợp cấu hình timeout/executor/interceptor | Test cả nhánh kết quả và timeout theo môi trường thích hợp |
| `Mono<T>` | Publisher reactive được MVC chuyển sang cơ chế async | Test kết quả sau subscription và final dispatch |
| `ResponseBodyEmitter` | Ứng dụng gọi `send` nhiều lần, kết thúc bằng `complete` | Kiểm tra response hữu hạn, các phần body và cách hoàn tất |
| `SseEmitter` | Ứng dụng phát sự kiện SSE qua `send(SseEventBuilder)` | Kiểm tra cấu trúc SSE và hoàn tất với emitter hữu hạn |
| `StreamingResponseBody` | Hàm ghi trực tiếp byte vào `OutputStream` trên executor | Chờ tác vụ ghi xong và kiểm tra byte/headers cuối cùng |

**Async không đồng nghĩa non-blocking I/O.** Spring MVC dựa trên Servlet: khi stream vào response, các thao tác ghi vẫn là I/O có thể chặn và được thực hiện trên thread do executor quản lý. WebFlux có cơ chế I/O khác. Vì vậy bài toán cấu hình `AsyncTaskExecutor`, timeout và tài nguyên trong Spring MVC vẫn quan trọng.

## 3. Quy trình MockMvc chuẩn: perform → async result → asyncDispatch

Tài liệu Spring MVC Test dùng mẫu sau:

```java
MvcResult started = mockMvc.perform(get("/async"))
        .andExpect(request().asyncStarted())
        .andExpect(request().asyncResult("done"))
        .andReturn();

mockMvc.perform(asyncDispatch(started))
        .andExpect(status().isOk())
        .andExpect(content().string("done"));
```

Đây là đoạn đặt trong một phương thức `@Test` đã có `MockMvc` và static imports cho `get`, `asyncDispatch`, `request`, `status`, `content`. Chưa có controller tương ứng nên chỉ minh họa luồng kiểm thử.

Diễn giải:

1. `perform(get(...))` mô phỏng HTTP dispatch đầu tiên.
2. `request().asyncStarted()` chứng minh controller chọn nhánh Servlet async. Nếu controller trả kết quả đồng bộ, assertion này phải thất bại — đó là lỗi kiểm thử thiết kế sai, không phải lỗi MockMvc.
3. `request().asyncResult(...)` chờ và đối chiếu giá trị bất đồng bộ do controller phát ra. Đây là **kết quả trung gian của quá trình tính toán**, không nhất thiết là body HTTP đã được serialize.
4. `asyncDispatch(started)` tạo lượt dispatch thứ hai, giúp MVC thực hiện chuyển đổi sang body, hoàn thiện status/headers và chạy exception handling.
5. `content().string(...)` và `status()` ở bước cuối mới xác nhận hợp đồng HTTP.

Không thực hiện một `mockMvc.perform(get(...))` thứ hai để “đợi async”: đó là **HTTP request mới**, có thể chạy nghiệp vụ lần thứ hai. Luôn giữ đúng `MvcResult` của lần đầu và truyền nó cho `asyncDispatch`.

### 3.1 Vì sao kiểm tra status ngay lúc đầu có thể gây hiểu nhầm?

Trong nhiều trường hợp response mock ban đầu có status 200 mặc định, mặc dù thao tác async chưa hoàn tất và có thể thất bại sau đó. Việc `andExpect(status().isOk())` tại dispatch đầu chỉ quan sát trạng thái hiện tại, chưa chứng minh cuối cùng trả 200. Cần assert status và body sau async dispatch, nhất là khi chuyển một exception thành 4xx/5xx bằng `@ExceptionHandler`.

### 3.2 Khi nào dùng MvcResult.getAsyncResult(timeout)?

`MvcResult.getAsyncResult(long timeToWait)` chờ tối đa một khoảng mili giây do test chọn để lấy kết quả async, tránh treo quá lâu khi task không bao giờ hoàn tất. Ví dụ:

```java
MvcResult started = mockMvc.perform(get("/async"))
        .andExpect(request().asyncStarted())
        .andReturn();

Object asyncValue = started.getAsyncResult(2_000);
assertEquals("done", asyncValue);

mockMvc.perform(asyncDispatch(started))
        .andExpect(status().isOk())
        .andExpect(content().string("done"));
```

`assertEquals` đến từ JUnit Jupiter. Mốc 2 giây ở đây là **thời gian chờ tối đa trong test**, khác với timeout của Servlet request do server/MVC cấu hình. Nếu test timeout, hãy kiểm tra luồng công việc, executor và dữ liệu trước khi đơn giản tăng thời gian chờ.

### 3.3 asyncDispatch thực sự làm gì?

Spring MVC Test không có Servlet container để tự dispatch HTTP qua mạng. `MockMvcRequestBuilders.asyncDispatch(mvcResult)` mô phỏng việc container gọi lại `DispatcherServlet` trong pha ASYNC sau khi đã có async result. Khi đó những bộ xử lý return value, converter và exception resolver có thể tạo response cuối.

Đối với một async handler trả object DTO, `request().asyncResult(dto)` kiểm tra DTO; response sau dispatch có thể là JSON hoặc status đặc thù. Không nên so sánh `asyncResult` với JSON đã serialize nếu giá trị controller thực tế là object. Nếu `DeferredResult<ResponseEntity<String>>`, giá trị async là `ResponseEntity`, còn HTTP status/body là kết quả của bước dispatch.

## 4. Dựng môi trường kiểm thử phù hợp

### 4.1 Hai cách tạo MockMvc

- `MockMvcBuilders.standaloneSetup(controller).build()`: nhanh, phù hợp khi test một controller và cấu hình MVC tối thiểu. Nếu cần advice, converters hoặc interceptors cụ thể, phải đăng ký rõ. Không mặc định có toàn bộ context của ứng dụng.
- `@WebMvcTest(MyController.class)` và `@Autowired MockMvc`: nạp MVC test slice; muốn service khác thì cung cấp mock/bean phù hợp. Các filter/Security trong slice có thể ảnh hưởng request.
- `@SpringBootTest` + `@AutoConfigureMockMvc`: nạp ứng dụng rộng hơn trong MockMvc, vẫn dùng Servlet mock. Khi cần kiểm tra I/O streaming theo thời gian thực, có thể chạy server thật với `webEnvironment = RANDOM_PORT`.

Ví dụ nền tảng trong Gradle của Spring Boot 3.x:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

`spring-boot-starter-test` cung cấp JUnit Jupiter, Spring Test, AssertJ/Hamcrest và các phụ thuộc kiểm thử thông dụng. Dùng dependency management của Spring Boot để các version đồng bộ. Nếu controller thật dùng Reactor `Mono` hay `Flux`, cần thêm thư viện Reactor phù hợp (thường qua `spring-boot-starter-webflux` hoặc dependency liên quan), nhưng không cần ép mọi bài MVC async phụ thuộc Reactor.

### 4.2 Sơ đồ phạm vi bảo đảm

```text
Unit test trực tiếp Callable/service
     → xác minh logic Java, KHÔNG xác minh Servlet async MVC

MockMvc (standalone / MVC slice / full application context)
     → kiểm tra request mapping, async started/result/dispatch,
       MVC converters, status/headers, body cuối

Server thật + HTTP client
     → kiểm tra network, chunk arrival, flush, SSE còn mở,
       disconnect, proxy, buffering, timeout của connector
```

Không dùng riêng MockMvc để suy luận “client đã nhận chunk sau 100 ms” hoặc “browser tự reconnect SSE”. MockMvc response lưu body ở bộ nhớ và không mô phỏng đầy đủ socket/proxy/buffering.

## 5. Ví dụ hoàn chỉnh: Callable, DeferredResult và lỗi bất đồng bộ

Phần này gồm hai tệp có thể đặt vào một dự án Spring Boot 3.x/Java 17+. Tạo controller trả kết quả theo ba con đường: `Callable`, `DeferredResult` và `setErrorResult`. Với `DeferredResult`, test tự hoàn thành kết quả nên kiểm soát chính xác thời điểm, không cần `Thread.sleep`.

**Tệp `src/main/java/com/example/demo/AsyncDemoController.java`:**

```java
package com.example.demo;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
public class AsyncDemoController {
    private final AtomicReference<DeferredResult<String>> pending =
            new AtomicReference<>();

    @GetMapping("/async/callable")
    public Callable<String> callable() {
        return () -> "callable-ready";
    }

    @GetMapping("/async/deferred")
    public DeferredResult<String> deferred() {
        DeferredResult<String> result = new DeferredResult<>(5_000L);
        pending.set(result);
        return result;
    }

    public DeferredResult<String> pending() {
        return pending.get();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handle(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("bad-request");
    }
}
```

**Lưu ý kiến trúc:** trường `pending` chỉ làm điểm điều khiển trong ví dụ học tập/test và hỗ trợ đúng **một request đang chờ**. Không dùng `AtomicReference` toàn cục như thiết kế production cho nhiều request đồng thời. Ứng dụng thực cần ánh xạ promise theo ID, service quản lý vòng đời, giới hạn số yêu cầu đang đợi và dọn dẹp khi hoàn thành/timeout/hủy.

**Tệp `src/test/java/com/example/demo/AsyncDemoControllerTest.java`:**

```java
package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.request.async.DeferredResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AsyncDemoControllerTest {
    private AsyncDemoController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new AsyncDemoController();
        mockMvc = standaloneSetup(controller).build();
    }

    @Test
    void callableMustBeDispatchedToProduceHttpBody() throws Exception {
        MvcResult started = mockMvc.perform(get("/async/callable"))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertEquals("callable-ready", started.getAsyncResult(2_000));

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string("callable-ready"));
    }

    @Test
    void deferredResultCompletesOnlyWhenApplicationSetsIt()
            throws Exception {
        MvcResult started = mockMvc.perform(get("/async/deferred"))
                .andExpect(request().asyncStarted())
                .andReturn();

        DeferredResult<String> pending = controller.pending();
        assertNotNull(pending);
        pending.setResult("deferred-ready");

        assertEquals("deferred-ready", started.getAsyncResult(2_000));

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string("deferred-ready"));
    }

    @Test
    void errorResultUsesMvcExceptionHandlingAfterDispatch()
            throws Exception {
        MvcResult started = mockMvc.perform(get("/async/deferred"))
                .andExpect(request().asyncStarted())
                .andReturn();

        controller.pending().setErrorResult(
                new IllegalArgumentException("invalid"));
        started.getAsyncResult(2_000);

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("bad-request"));
    }
}
```

Chạy trong thư mục dự án Gradle đã có `gradlew` và cấu hình dependency ở trên:

```bash
./gradlew test --tests '*AsyncDemoControllerTest'
```

Windows PowerShell có thể dùng:

```powershell
.\gradlew.bat test --tests '*AsyncDemoControllerTest'
```

Ba test đều có assertion về vòng đời async và response; khi thành công JUnit ghi nhận **3 test thành công** trong báo cáo test. Không có stdout ứng dụng được bảo đảm vì controller/test không gọi `System.out.println` (các log Spring/Gradle tùy cấu hình).

**Điểm quan trọng:** `setErrorResult` của `DeferredResult` được truyền qua async MVC result handling; `@ExceptionHandler` tạo HTTP 400 trong pha dispatch tiếp theo. Kiểm tra `status().isOk()` ở lần dispatch đầu sẽ không phát hiện đường xử lý lỗi này.

### 5.1 Ranh giới của ví dụ với yêu cầu chạy song song

Một endpoint thật không lưu mọi `DeferredResult` vào cùng một `AtomicReference`. Khi có nhiều HTTP request, request B có thể ghi đè reference của request A và khiến A chờ mãi. Dùng concurrency-safe registry có khóa (và cleanup), hoặc một event bus/message listener gắn correlation ID; test nhiều request song song để khẳng định không phát kết quả nhầm phiên.

## 6. Kiểm thử streaming body: điều gì khác với một kết quả async duy nhất?

**Streaming** có hai đặc điểm: kết quả có thể bao gồm nhiều phần được ghi trước khi toàn bộ công việc kết thúc, và một số luồng (như SSE) cố ý sống lâu/không có điểm kết thúc tự nhiên. Đối với kiểu hữu hạn, MockMvc có thể quan sát body sau khi tác vụ đã ghi xong. Đối với luồng vô hạn, phải đổi chiến lược test, không được chờ hoàn thành vô hạn.

### 6.1 StreamingResponseBody ghi byte trực tiếp

`StreamingResponseBody` phù hợp cho download hoặc xuất file lớn. Spring chạy callback ghi vào `OutputStream` dưới cơ chế async của Servlet MVC. Callback thường ghi từng buffer rồi `flush()` khi muốn đẩy dữ liệu xuống tầng response; socket/proxy có thể tiếp tục buffer trong môi trường thực.

**Tệp `src/main/java/com/example/demo/StreamingDemoController.java`:**

```java
package com.example.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class StreamingDemoController {

    @GetMapping("/stream/chunks")
    public ResponseEntity<StreamingResponseBody> chunks() {
        StreamingResponseBody stream = output -> {
            output.write("first\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.write("second\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
        };
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(stream);
    }

    private volatile SseEmitter currentEmitter;

    @GetMapping(path = "/stream/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        SseEmitter emitter = new SseEmitter(5_000L);
        currentEmitter = emitter;
        return emitter;
    }

    public SseEmitter currentEmitter() {
        return currentEmitter;
    }
}
```

Trong ví dụ này, `/stream/chunks` ghi hai phần rồi hoàn tất. `/stream/events` trả `SseEmitter` và cho phép test chủ động gửi sự kiện/kết thúc; field volatile chỉ dùng trong fixture để test **một kết nối tại một thời điểm**. Trong ứng dụng thật cần registry/session manager, timeout và dọn tài nguyên, không dùng một field chung khi nhiều client stream.

### 6.2 Test StreamingResponseBody bằng MockMvc

Tại sao không nhất thiết phải `asyncDispatch` sau khi stream ghi xong? Với `StreamingResponseBody`, nội dung được viết vào response output stream trong callback; phép kiểm tra có thể chờ async task hoàn thành rồi đọc body đã ghi. Spring cũng có mẫu test chính thức sử dụng `MvcResult::getAsyncResult` để chờ công việc streaming. Với phương thức trả một object cần MVC serialize sau async, `asyncDispatch` vẫn là bước quan trọng.

**Đoạn test** trong class có `MockMvc` đã cấu hình với `StreamingDemoController`:

```java
@Test
void finiteByteStreamHasExpectedBody() throws Exception {
    MvcResult result = mockMvc.perform(get("/stream/chunks"))
            .andExpect(request().asyncStarted())
            .andReturn();

    result.getAsyncResult(2_000); // Đợi callback ghi byte hoàn tất.

    assertEquals("first\nsecond\n",
            result.getResponse().getContentAsString());
    assertEquals(200, result.getResponse().getStatus());
}
```

Trong đoạn này cần static imports `get`, `request` và JUnit `assertEquals`. Assertion kiểm tra **nội dung cuối cùng** và HTTP status mock. Hai lần `flush()` trong controller **không thể được kết luận** là client ngoài mạng nhìn thấy hai gói/chunk riêng, vì MockMvc không có TCP socket/proxy thật.

Nếu ứng dụng muốn assert byte của tập tin, dùng `getResponse().getContentAsByteArray()` và so với dữ liệu byte mong muốn, đặc biệt khi dữ liệu nhị phân không có biểu diễn chuỗi đáng tin.

### 6.3 Khi asyncDispatch vẫn có ý nghĩa với streaming?

Với một số nhánh async return value, thực hiện `asyncDispatch` giúp MVC chạy xử lý hoàn tất hoặc resolver. Riêng callback `StreamingResponseBody` thường đã ghi trực tiếp nội dung lên response; khi test output, chờ `getAsyncResult(timeout)` và đọc mock response là đủ. Không áp dụng máy móc “mọi stream phải có asyncDispatch” cũng không cho rằng `asyncDispatch` tự tạo thêm chunk dữ liệu.

Nếu kiểm thử hợp đồng exception mapping, cần biết lỗi xuất hiện **trước hay sau khi response đã được commit**. Nếu byte đã được gửi ra mạng, server có thể không còn thay được status/body thành một JSON lỗi chuẩn. Với streaming thật, cần kiểm thử client đóng kết nối, mid-stream IOException và cleanup bằng server thật.

## 7. SseEmitter và ResponseBodyEmitter: kiểm thử dòng sự kiện hữu hạn

### 7.1 ResponseBodyEmitter và SseEmitter

`ResponseBodyEmitter` có thể phát nhiều object; mỗi `send(...)` được chuyển bằng `HttpMessageConverter` tương ứng và ghi vào response. `SseEmitter` chuyên cho **Server-Sent Events**, tạo các trường như `id:`, `event:` và `data:` theo giao thức `text/event-stream`.

```text
HTTP response status: 200
Content-Type: text/event-stream

id:evt-1
event:notice
data:ready

id:evt-2
event:notice
data:next

```

Hai dòng trống phân cách event trong SSE. Chỉ cần body cuối có đúng văn bản **chưa đủ** để chứng minh client thực sự nhận từng event ngay khi server flush. Với event chứa dữ liệu nhiều dòng, phải kiểm tra tuân thủ cú pháp SSE và cách JSON được serialize.

Các callback quan trọng:

- `emitter.send(data)` hoặc `emitter.send(SseEmitter.event().id(...).name(...).data(...))` gửi phần dữ liệu.
- `emitter.complete()` báo server hoàn tất luồng thành công; với SSE production có thể chỉ gọi khi client ngắt hoặc có chính sách kết thúc.
- `emitter.completeWithError(error)` kết thúc bằng lỗi nếu vẫn còn có thể xử lý. Nếu `send` ném `IOException` do peer disconnect, tài liệu Spring giải thích container có thể khởi phát async error handling; ứng dụng không nên tự gọi `completeWithError` thêm lần nữa một cách mù quáng trong tình huống đó.
- `onCompletion`, `onTimeout`, `onError` giúp dọn emitter khỏi danh sách kết nối đang hoạt động.

### 7.2 Test SSE hữu hạn, điều khiển thời điểm gửi và complete

Một cách kiểm thử đơn vị rõ ràng là để controller trả emitter, test chủ động gửi một event rồi gọi `complete`, sau đó mới kiểm tra body. Như vậy test không cần `Thread.sleep` và không đợi stream vô hạn:

**Tệp `src/test/java/com/example/demo/StreamingDemoControllerTest.java`:**

```java
package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class StreamingDemoControllerTest {
    private StreamingDemoController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new StreamingDemoController();
        mockMvc = standaloneSetup(controller).build();
    }

    @Test
    void bytesAreWrittenInOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/stream/chunks"))
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(2_000);

        assertEquals(200, result.getResponse().getStatus());
        assertEquals("first\nsecond\n",
                result.getResponse().getContentAsString());
    }

    @Test
    void completedSseContainsEventFields() throws Exception {
        MvcResult started = mockMvc.perform(get("/stream/events")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        SseEmitter emitter = controller.currentEmitter();
        assertNotNull(emitter);

        emitter.send(SseEmitter.event()
                .id("evt-1")
                .name("notice")
                .data("ready"));
        emitter.complete();
        started.getAsyncResult(2_000);

        mockMvc.perform(
                    org.springframework.test.web.servlet.request
                            .MockMvcRequestBuilders.asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(
                        containsString("id:evt-1\n")))
                .andExpect(content().string(
                        containsString("event:notice\n")))
                .andExpect(content().string(
                        containsString("data:ready\n\n")));
    }
}
```

Chạy:

```bash
./gradlew test --tests '*StreamingDemoControllerTest'
```

Trong test stream hữu hạn, output byte được so theo thứ tự; với SSE, test kiểm tra một số trường có ý nghĩa của event. Không dựa vào lịch nội bộ một luồng `Thread.sleep`, vì scheduler chạy khác nhau giữa máy local và CI. Hai test tạo MockMvc mới trước từng ca, hạn chế việc trạng thái emitter rò giữa các test.

Nếu response được gzip/serialize hoặc đặt charset đặc thù, điều chỉnh matcher để kiểm tra semantics thay vì khẳng định một kiểu chuỗi không ổn định qua converter/configuration. Với SSE, body chứa dấu kết thúc event `\n\n` là hợp đồng định dạng.

### 7.3 Test ResponseBodyEmitter

`ResponseBodyEmitter` không buộc phải theo định dạng SSE. Ví dụ controller nhận emitter và dùng `send` nhiều lần, rồi `complete`:

```java
@GetMapping("/stream/messages")
public ResponseBodyEmitter messages() {
    ResponseBodyEmitter emitter = new ResponseBodyEmitter(5_000L);
    // Ở production, một service/event callback sẽ giữ emitter để gửi dữ liệu.
    return emitter;
}
```

Đây chỉ là phương thức minh họa, không có nhiệm vụ phát dữ liệu nếu chạy độc lập. Test cần điều khiển emitter trong fixture tương tự `SseEmitter` và chọn `MediaType`/converter rõ ràng. Không nhầm “2 lần send” với “2 TCP packets”; để xác minh thời điểm nhận chunk cần HTTP client kết nối đến server thật.

## 8. Kiểm thử lỗi, timeout và client ngắt kết nối

### 8.1 Nhánh lỗi xảy ra trước khi response commit

Với `DeferredResult`, gọi `setErrorResult(exception)` có thể được xử lý bởi MVC exception resolver trong lượt dispatch. Test phần 5 minh họa rõ response HTTP 400 cuối. Với `Callable`, ném exception trong callable và dùng `@ExceptionHandler`/`@ControllerAdvice`; kiểm tra kết quả sau `asyncDispatch`.

Nên có các ca:

| Kịch bản | Điều cần kiểm tra |
| --- | --- |
| Callable hoàn tất | `asyncStarted`, async result đúng, dispatch ra status/body |
| DeferredResult hoàn tất sau tín hiệu | Không trả body trước khi `setResult`; final dispatch đúng |
| DeferredResult phát lỗi | Lỗi được map qua MVC exception handler đúng status/body |
| Publisher `Mono` hoàn tất/lỗi trên MVC | Subscription xảy ra, kết quả/error được Spring chuyển tiếp |
| StreamingResponseBody ghi thành công | Header, byte output và trạng thái hoàn tất |
| Emitter hoàn tất sau n event | Đúng thứ tự/syntax, dọn emitter và hoàn tất |
| Async timeout | Callback timeout / response theo chính sách của ứng dụng |
| Client disconnect khi đang stream | Callback/cleanup, ghi lỗi I/O và dừng nguồn tương ứng |

### 8.2 Timeout trong MockMvc là hai loại khác nhau

**Timeout của ứng dụng:** Ví dụ `new DeferredResult<>(5_000L)` quy định thời gian cho async request. Khi chạy trong Servlet container, timeout được container/hạ tầng MVC xử lý theo vòng đời async, có thể kích hoạt `onTimeout` và các interceptor/exception resolver.

**Timeout của test:** `mvcResult.getAsyncResult(2_000)` là giới hạn thời gian một test chờ kết quả. Nó **không tự mô phỏng servlet async timeout** và không tự gọi `onTimeout` của ứng dụng.

Nếu cần test nhánh timeout logic đơn thuần, tách logic callback thành service có thể test trực tiếp hoặc xây hạ tầng điều khiển timeout trong integration test; không dùng `Thread.sleep(5_001)` rồi mong `MockMvc` hành xử như một container production. Spring MVC cho phép cấu hình `AsyncSupportConfigurer`, `DeferredResultProcessingInterceptor`, `CallableProcessingInterceptor` hoặc timeout callbacks tùy kịch bản.

Ví dụ xử lý timeout cho một DeferredResult **minh họa**:

```java
@GetMapping("/async/controlled")
public DeferredResult<String> controlled() {
    DeferredResult<String> result = new DeferredResult<>(3_000L);
    result.onTimeout(() -> result.setErrorResult(
            new IllegalStateException("async request timeout")));
    return result;
}
```

Đoạn này phải được đặt trong controller; cần đăng ký handler phù hợp cho `IllegalStateException` nếu muốn một response cụ thể. Nó không tự tạo công việc trả kết quả và sẽ timeout trên container thật nếu không được hoàn thành. MockMvc test không thể xác minh timer mạng thực chỉ từ đoạn này.

### 8.3 StreamingResponseBody và exception sau khi đã gửi dữ liệu

Ví dụ stream viết header/body một phần rồi gặp `IOException`: response có thể đã commit, không còn thay được status thành 500 hoặc gửi JSON lỗi chuẩn. Hệ thống cần định nghĩa client sẽ phát hiện dữ liệu thiếu thế nào (Content-Length/checksum/định dạng record/trailer tùy hợp đồng), đóng luồng và log rõ nguyên nhân.

Một cuộc gọi `SseEmitter.send` có thể ném `IOException` do client đóng kết nối. Trong Servlet environment, Spring có thể nhận `AsyncListener` lỗi và thực hiện dispatch để xử lý ngoại lệ; không bắt lỗi rồi liên tục gọi `completeWithError` mà không hiểu vòng đời của emitter. Sử dụng `onCompletion`, `onTimeout` và `onError` để xóa emitter khỏi registry và dừng subscription/job liên quan.

### 8.4 Bối cảnh security và filter ở ASYNC dispatch

Nếu dùng Spring Security, Servlet filters hoặc tracing MDC, phải cấu hình chúng hỗ trợ `DispatcherType.ASYNC` theo yêu cầu thực tế. Nếu filter chỉ chạy trên `REQUEST`, các pha security context/tracing/metrics ở dispatch sau có thể khác kỳ vọng. Cũng cần hiểu `OncePerRequestFilter.shouldNotFilterAsyncDispatch()` và thiết lập filter registration; không chỉ assert logic controller rồi khẳng định toàn bộ authorization hoạt động trên async dispatch.

Trong MockMvc standalone, filter/security không tự xuất hiện nếu không add chúng; với Spring Boot test slice/full context, chúng có thể tham gia. Vì vậy mỗi test nên nói rõ cấu hình nào đã được nạp.

## 9. Khi nào cần chạy server thật và WebTestClient?

Spring cho phép dùng `WebTestClient` với `MockMvcWebTestClient` để kiểm thử phần lớn HTTP response, bao gồm async hữu hạn. Tuy nhiên khi binder là MockMvc, **luồng vô hạn không thể được client chủ động cancel trên server** giống kết nối mạng thật. Vì vậy:

- Dùng MockMvc cho `DeferredResult`, `Callable`, stream có kết thúc, định dạng body và status cuối cùng.
- Dùng embedded server + `WebTestClient`/HTTP client thực khi cần quan sát nhiều event qua thời gian, cancel subscription, đóng TCP kết nối hoặc xử lý stream vô hạn.
- Nếu cần chứng minh `flush()` đẩy dữ liệu theo nhịp, kiểm tra end-to-end qua mạng. Proxy/gateway/browser có thể buffer dù ứng dụng đã flush.
- Với SSE stream dài, thường không chờ đến hết luồng; client subscribe, nhận n sự kiện mong đợi, sau đó cancel. Server phải dọn emitter/subscription tương ứng.

Mẫu kiểm thử tích hợp **minh họa** bằng một WebTestClient gắn với server HTTP thật:

```java
FluxExchangeResult<String> response = webTestClient.get()
        .uri("/stream/events")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus().isOk()
        .returnResult(String.class);

// Test tiêu thụ một số sự kiện rồi hủy subscription.
// Cần dùng StepVerifier / Reactor và server phát SSE thật.
StepVerifier.create(response.getResponseBody())
        .expectNextCount(2)
        .thenCancel()
        .verify();
```

Đoạn này đòi hỏi test server đang chạy, `WebTestClient` được cấu hình URL/port thực, server phát tối thiểu hai phần tử phù hợp codec, và dependency `reactor-test`. Không phải chương trình Java độc lập. Với SSE, có thể dùng `ServerSentEvent<T>` để kiểm tra `id`/`event`/`data` đúng kiểu thay vì ép mọi event vào `String`. Tránh viết test sẽ chờ n event khi server không phát, cần thêm timeout trong StepVerifier và chuẩn bị dữ liệu test xác định.

Sự khác nhau giữa **MockMvc Mock HTTP response** và **HTTP response thực** là điểm giới hạn bằng chứng: một assertion body đúng ở MockMvc không đảm bảo client nhận một chunk ngay tại thời điểm server gọi `flush()`.

## 10. Cấu hình executor và độ ổn định kiểm thử

Spring MVC dùng `AsyncTaskExecutor` cho các `Callable` và blocking writes của một số kiểu streaming. Executor mặc định của MVC trong một số cấu hình không phù hợp production dưới tải cao; cần đặt giới hạn thread/queue rõ ràng, timeout hợp lý và theo dõi queue pressure. Ví dụ:

```java
package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcAsyncConfig implements WebMvcConfigurer {

    @Bean
    ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mvc-async-");
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(10_000L);
        configurer.setTaskExecutor(mvcAsyncExecutor());
    }
}
```

Đây là cấu hình mẫu riêng cho môi trường có Spring context; các tham số 4/16/100/10 giây là **giá trị minh họa**, cần chọn theo traffic, mức độ blocking và giới hạn tài nguyên thực. Spring quản lý vòng đời `ThreadPoolTaskExecutor` được tạo thành bean. Nếu context có nhiều executor hoặc yêu cầu tách tải download và task ngắn, cấu hình cần được xem xét để tránh cạn thread khi có nhiều stream dài.

Trong test dùng `standaloneSetup`, cấu hình này **không tự áp dụng** vì standalone chỉ lấy controller và những cài đặt đã đăng ký. Muốn kiểm thử đúng executor production, dùng WebApplicationContext/full integration test hoặc cấu hình MVC tương ứng trong MockMvc setup.

Không dùng `Thread.sleep` để “đợi” kết quả: đó là lựa chọn dễ flake do máy CI khác CPU. Ưu tiên công cụ có điều kiện hoàn tất: `DeferredResult.setResult` do test kiểm soát, `getAsyncResult(timeout)`, `CountDownLatch`/`CompletableFuture` nếu cần, hoặc virtual time cho reactive stream thuần (không nhầm với Servlet async timer).

## 11. Lỗi kiểm thử phổ biến và cách khắc phục

| Sai lầm | Vì sao sai? | Cách sửa |
| --- | --- | --- |
| Chỉ gọi `mockMvc.perform(get(...)).andExpect(status().isOk())` | Status ban đầu có thể là mặc định, chưa có response cuối | Assert `asyncStarted`, chờ result, `asyncDispatch` rồi kiểm tra status/body |
| Gọi lại `get(...)` thay vì `asyncDispatch(result)` | Tạo request mới, có thể chạy nghiệp vụ hai lần | Giữ `MvcResult` của dispatch đầu |
| Kiểm tra JSON ở `request().asyncResult(...)` khi controller trả DTO | Kết quả async là object trước conversion | Kiểm tra DTO ở asyncResult, JSON sau dispatch |
| Gọi `asyncDispatch` khi async result chưa sẵn | Test race/timeout | Dùng `request().asyncResult` hoặc `MvcResult.getAsyncResult(timeout)` |
| Thấy `asyncStarted` là kết luận nghiệp vụ thành công | Task async có thể lỗi hoặc timeout sau đó | Kiểm tra kết quả cuối và cả nhánh lỗi |
| Sử dụng `Thread.sleep` cố định | Flaky khi CI chậm hoặc scheduling khác | Chủ động hoàn thành `DeferredResult`; chờ điều kiện rõ ràng |
| Dùng MockMvc để khẳng định streaming flush đến browser | Không có socket/proxy/network trong MockMvc | Integration test qua server thật |
| Chờ một SSE stream vô hạn hoàn tất trong MockMvc | Không có điểm hoàn tất, test có thể treo | Test emitter hữu hạn hoặc stream thực với client cancel |
| Coi `getAsyncResult(1000)` là test Servlet timeout | Chỉ giới hạn thời gian chờ của test | Dựng test cho callback/timeout của container riêng |
| Không cấu hình HTTP message converters khi standalone | Body/status khác ứng dụng thật | Đăng ký converter/advice/filter hoặc dùng WebMvcTest/context |
| Không dọn emitter khi timeout/client disconnect | Rò danh sách connection và nguồn dữ liệu | `onCompletion`/`onError`/`onTimeout` + cleanup idempotent |
| Dùng executor không giới hạn hoặc block event callback | Tải cao gây cạn thread/tăng độ trễ | ThreadPoolTaskExecutor có giới hạn, đo queue/backpressure |
| Kiểm thử HTTP 200 sau khi bytes đã gửi nhưng giả định có thể đổi status | Response thực có thể đã commit | Thiết kế stream failure contract và test network |
| Áp dụng cách test Servlet cho WebFlux handler | Khác pipeline async/I/O | Dùng test framework đúng stack |

## 12. Bài tập và câu hỏi tự đánh giá

1. Một controller trả `DeferredResult<String>` và chưa gọi `setResult`. `MockMvc` trả gì sau `perform`? **Một result cho request đã bắt đầu async; chưa có business result cuối.**
2. Tại sao cần `asyncDispatch`? **Để Spring MVC hoàn thiện pha dispatch sau khi có async result, áp dụng converter và exception handling.**
3. Khi nào `request().asyncResult(dto)` nên so với DTO thay vì JSON? **Khi async result là object mà MVC chỉ serialize trong pha dispatch sau.**
4. Với `StreamingResponseBody` ghi hai buffer, body cuối trong MockMvc đúng có chứng minh hai chunk đến client ngay? **Không; MockMvc không quan sát network timing.**
5. Vì sao không chạy vô hạn `SseEmitter` trong MockMvc test? **Luồng không hoàn tất, MockMvc không có kết nối client thật để hủy tương đương.**
6. Khi `DeferredResult.setErrorResult` được gọi, nên kiểm tra response ở đâu? **Sau pha async dispatch và exception handling.**
7. `getAsyncResult(2_000)` có thực hiện timeout callback của server không? **Không; nó chỉ giới hạn thời gian test chờ result.**
8. Kiểm thử người dùng ngắt mạng giữa chừng nên dùng gì? **Một test server thực và client có khả năng đóng/cancel network stream.**

Bài tập thực hành: mở rộng `AsyncDemoController` để trả DTO, thêm `@ExceptionHandler` xử lý lỗi nghiệp vụ, và viết hai test: xác minh DTO ở `asyncResult` và JSON ở pha `asyncDispatch`. Tiếp theo, viết một SSE server có hai event rồi hoàn tất; kiểm tra định dạng bằng MockMvc. Cuối cùng, tạo SSE stream không tự kết thúc và kiểm thử qua `WebTestClient` kết nối server thật để xác nhận việc hủy và dọn emitter.

## 13. Tài liệu tham khảo chính thức

- [Spring Framework — MockMvc Async Requests](https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/async-requests.html): cách dùng `asyncStarted`, `asyncResult` và `asyncDispatch`.
- [Spring Framework — Asynchronous Requests, HTTP Streaming](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html): `DeferredResult`, `Callable`, `SseEmitter`, `StreamingResponseBody`, executor và timeout.
- [Spring Framework — MockMvc Streaming Responses](https://docs.spring.io/spring-framework/reference/6.2/testing/mockmvc/hamcrest/vs-streaming-response.html): giới hạn stream vô hạn của MockMvc.
- [Spring Framework — MockMvc Overview](https://docs.spring.io/spring-framework/reference/testing/mockmvc/overview.html): môi trường Servlet mô phỏng, mức độ kiểm chứng của MockMvc.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): kiểm thử qua HTTP client và truy xuất server response assertions.
- [Spring Framework — MockMvcRequestBuilders.asyncDispatch Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/web/servlet/request/MockMvcRequestBuilders.html): xây request dispatcher ASYNC từ `MvcResult`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-testing-008">WebTestClient với controller, RouterFunction, ApplicationContext hoặc máy chủ thật</a>

<details>
<summary>Xem chi tiết</summary>

# WebTestClient với controller, RouterFunction, ApplicationContext và máy chủ thật

## Mục tiêu học tập

`WebTestClient` là HTTP test client của Spring Framework. Nó cung cấp API gửi yêu cầu và kiểm tra phản hồi gần giống nhau cho cả hai loại kiểm thử:

- **Mock server**: xử lý request trong bộ nhớ, không cần mở cổng HTTP hoặc khởi động servlet container/network server.
- **Live server**: mở HTTP connection đến máy chủ thực đang chạy, phù hợp kiểm thử tích hợp đầy đủ hơn.

Trong bài này, bạn sẽ biết cách chọn và dùng cả **bốn kiểu thiết lập**:

1. `bindToController(...)`: gắn với một hay một số controller để kiểm thử tập trung.
2. `bindToRouterFunction(...)`: gắn với functional endpoint **WebFlux**.
3. `bindToApplicationContext(...)`: gắn với Spring context đã được cấu hình.
4. `bindToServer()`: gọi HTTP server đang hoạt động, có địa chỉ `baseUrl(...)`.

Ngoài cách tạo client, bài phân tích phạm vi thực sự được kiểm thử, khác biệt WebFlux/Spring MVC, phương thức `exchange()`, assertions cho status/header/body/JSON, xử lý kết quả `Flux` và ví dụ Spring Boot 3.x chạy được với Java 17+ và JUnit 5.

> **Điểm dễ nhầm:** Với **Spring WebFlux**, dùng `WebTestClient.bindToController` và `WebTestClient.bindToApplicationContext`. Với **Spring MVC**, dùng `MockMvcWebTestClient.bindToController` và `MockMvcWebTestClient.bindToApplicationContext`. `WebTestClient.bindToRouterFunction` là đường kiểm thử functional routing WebFlux; không nên suy diễn rằng nó kiểm thử được mọi RouterFunction của Spring MVC.

## 1. WebTestClient kiểm thử những thành phần nào?

`WebTestClient` thuộc package `org.springframework.test.web.reactive.server`. Tuy mang chữ “reactive” trong package, nó có thể được dùng với **Spring MVC lẫn Spring WebFlux**. Phần client dựng request qua API dựa trên `WebClient` và cung cấp fluent assertions, còn lựa chọn `bind...` xác định server được gọi như thế nào.

Một luồng test điển hình:

```text
JUnit test
   |
   v
WebTestClient
   |  .get() / .post() / .put() / .delete()
   |  .uri(), .header(), .contentType(), .bodyValue()
   |  .exchange()  -> thực thi HTTP request theo setup
   v
+---------------------- Mock setup -----------------------+
| Controller riêng | RouterFunction | ApplicationContext |
| Không có real socket, request/response mock            |
+---------------------------------------------------------+
                             HOẶC
+---------------------- Live server ----------------------+
| TCP/HTTP client -> cổng của server -> application       |
+---------------------------------------------------------+
   |
   v
.expectStatus() / .expectHeader() / .expectBody()
hoặc .returnResult(...) -> Flux + StepVerifier
```

Một test đọc JSON ví dụ:

```java
client.get()
        .uri("/books/{id}", 7)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.id").isEqualTo(7)
        .jsonPath("$.title").isEqualTo("Spring");
```

Đoạn này giả định client và endpoint đã được cấu hình. `exchange()` là mốc gửi request; `expect...` tạo assertions; test thất bại khi status, header hoặc body không đúng. `jsonPath("$.id")` tìm trường JSON, không phụ thuộc thứ tự các trường trong JSON object.

### 1.1. Khác WebClient trong ứng dụng

| Tiêu chí | `WebClient` | `WebTestClient` |
| --- | --- | --- |
| Mục đích | Gọi dịch vụ HTTP trong application | Viết kiểm thử HTTP |
| Kết quả thông dụng | `Mono<T>`, `Flux<T>`, `ClientResponse` | Assertion specs, `EntityExchangeResult` hoặc `FluxExchangeResult` |
| Phát request | Publisher được subscribe theo luồng reactive | Test gọi `exchange()` |
| Kiểm tra status/body | Tự viết logic theo yêu cầu ứng dụng | Có sẵn `expectStatus`, `expectHeader`, `expectBody` |
| Có thể dùng mock server không? | Không phải mục đích chính | Có, thông qua `bindTo...` |
| Có thể gọi server thật không? | Có | Có, qua `bindToServer` |

Không dùng `WebTestClient` để thay thế production HTTP client. Nó nằm trong thư viện kiểm thử và được thiết kế để các assertion thất bại đúng cách trong test.

## 2. Chọn hình thức binding

| Thiết lập | Có server HTTP thật? | Thành phần được nạp | Tình huống phù hợp |
| --- | --- | --- | --- |
| `WebTestClient.bindToController(...)` | Không | WebFlux infrastructure và controller được chọn | Test nhanh route, binding, status, JSON, validation của một controller WebFlux |
| `WebTestClient.bindToRouterFunction(...)` | Không | WebFlux `RouterFunction` được chỉ định và xử lý chức năng tương ứng | Test functional routing + handler function WebFlux |
| `WebTestClient.bindToApplicationContext(context)` | Không | WebFlux web configuration/controller/filter được tạo trong context | Test wiring và nhiều thành phần Spring cùng hoạt động |
| `MockMvcWebTestClient.bindToController(...)` | Không | Standalone Spring MVC MockMvc và controller được chỉ định | Test controller annotation Servlet/MVC |
| `MockMvcWebTestClient.bindToApplicationContext(wac)` | Không | `WebApplicationContext` của Spring MVC + MockMvc | Test MVC với cấu hình Spring đang dùng |
| `WebTestClient.bindToServer()` | **Có** | Server/container/network stack theo địa chỉ đích | Kiểm thử tích hợp thật, security/filter/wiring/HTTP transport |

Độ “rộng” của test tăng khi chuyển từ controller độc lập tới application context và server thật. Tuy vậy, nhiều thứ như TLS, proxy, CORS ở lớp gateway, database thực và các dịch vụ ngoài **chỉ** được chứng minh nếu môi trường test thực sự có chúng. Không gọi một mock server test là end-to-end đầy đủ chỉ vì nó dùng API HTTP giống thật.

## 3. Bind trực tiếp vào controller

### 3.1. Controller WebFlux

```java
WebTestClient client = WebTestClient
        .bindToController(new BookController())
        .build();

client.get()
        .uri("/books/7")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.title").isEqualTo("Spring");
```

Đoạn này **minh họa**, `BookController` sẽ được định nghĩa ở ví dụ chạy được bên dưới. Spring tạo chuỗi xử lý WebFlux cho controller đã truyền vào, đủ để thực hiện mock request/response. Bạn không cần `localhost`, port hoặc Netty.

Có thể truyền nhiều controller trong một setup. Đây là phương án tốt khi muốn phát hiện nhanh lỗi annotation như `@GetMapping`, `@PathVariable`, request body binding, serialization hoặc status mapping.

**Giới hạn:** `bindToController` không tự nạp toàn bộ component scan, security filter, `@ControllerAdvice`, custom codec hoặc service bean của ứng dụng. Nếu controller cần dependencies, tạo instance với mock/stub rõ ràng; cấu hình thêm controller advice/validator tùy setup, hoặc chuyển sang bind-to-context khi mục tiêu là kiểm tra wiring thực.

### 3.2. Controller Spring MVC

Controller MVC chạy trên Servlet infrastructure. Muốn dùng cùng cách viết assertions của `WebTestClient`, cần adapter MockMvc:

```java
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

WebTestClient client = MockMvcWebTestClient
        .bindToController(new MvcBookController())
        .build();
```

`MvcBookController` là class giả định do project MVC khai báo. Đây là **snippet cho một ứng dụng MVC riêng**, không phải một class thuộc demo WebFlux phía sau.

Cơ chế này dùng `StandaloneMockMvcBuilder` và Spring MVC mock request/response; không khởi động Tomcat thật. Không nhầm `WebTestClient.bindToController` (WebFlux) với `MockMvcWebTestClient.bindToController` (MVC) khi test một controller truyền thống.

## 4. Bind trực tiếp vào RouterFunction (WebFlux)

Functional endpoint WebFlux có router và handler function, không bắt buộc có `@RestController`. Một ví dụ:

```java
RouterFunction<ServerResponse> routes = RouterFunctions.route(
        RequestPredicates.GET("/hello/{name}"),
        request -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "message", "Hello " + request.pathVariable("name"))));

WebTestClient client = WebTestClient
        .bindToRouterFunction(routes)
        .build();

client.get()
        .uri("/hello/Lan")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.message").isEqualTo("Hello Lan");
```

Imports bổ sung trong snippet: `java.util.Map`, `org.springframework.http.MediaType`, `org.springframework.web.reactive.function.server.RouterFunction`, `RouterFunctions`, `RequestPredicates`, `ServerResponse`.

`WebTestClient.bindToRouterFunction(routes)` dùng `RouterFunctions.toWebHandler` để dựng mock WebFlux `WebHandler`. Nó kiểm tra route predicate, trích xuất path variables, handler response và serialization mà **không mở server port**.

Một vài tình huống đáng test:

- Request khớp predicate `GET("/hello/{name}")` trả HTTP 200 và body đúng.
- Method hoặc path không khớp route dự kiến thì không được xử lý như route đó.
- Query param/header predicate có được phân nhánh đúng không.
- Body/codec và lỗi phát sinh trong handler function có được biến thành response như mong đợi không.

**Giới hạn quan trọng:** Spring Framework 6.2 không cung cấp đường binding `WebTestClient.bindToRouterFunction` tương đương cho **functional endpoint Spring MVC**. Nếu đang dùng `org.springframework.web.servlet.function.RouterFunction`, hãy dùng công cụ test MVC phù hợp (ví dụ MockMvc với Spring MVC context) hoặc test trên server thật. Đừng truyền kiểu WebMvc RouterFunction vào method WebFlux chỉ vì cùng tên.

## 5. Bind vào ApplicationContext

Một controller test chạy độc lập đôi khi “xanh” dù Spring application thật bị thiếu bean, sai route mapping hoặc filter không được cài. `bindToApplicationContext` mở rộng phạm vi tới những thành phần Spring đã có trong context.

### 5.1. Spring WebFlux ApplicationContext

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ContextTest {
    @Autowired
    ApplicationContext context;

    @Test
    void readsFromConfiguredApplication() {
        WebTestClient client = WebTestClient
                .bindToApplicationContext(context)
                .build();

        client.get()
                .uri("/books/7")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(7);
    }
}
```

Đây là snippet Spring Boot WebFlux, giả định ứng dụng có `/books/7`. `ApplicationContext` cần thực sự mang WebFlux web configuration. Spring dùng context để dựng `WebHandler` pipeline gồm các thành phần áp dụng trong môi trường test.

Ví dụ `@SpringBootTest(webEnvironment=MOCK)` **không mở HTTP port**. Nếu dùng `@SpringBootTest(webEnvironment=RANDOM_PORT)`, server thật vẫn khởi động do annotation, nhưng client được tạo bằng `bindToApplicationContext` vẫn xử lý request **in-memory**; nó không đột nhiên chuyển sang network mode chỉ vì context đang có server.

Dùng dạng này khi cần xác minh controller, router, codec, filters, exception handlers, dependency injection và các cấu hình đang được import. Mức độ test phụ thuộc thực tế context chứa gì: test slice hoặc test configuration rút gọn sẽ không tự động chứng minh toàn bộ ứng dụng production.

### 5.2. Spring MVC WebApplicationContext

```java
@Autowired
WebApplicationContext wac;

WebTestClient mvcClient = MockMvcWebTestClient
        .bindToApplicationContext(wac)
        .build();
```

Imports thêm: `org.springframework.web.context.WebApplicationContext` và `org.springframework.test.web.servlet.client.MockMvcWebTestClient`. Đây là snippet ở trong class test có MVC web context, không phải mã chạy độc lập.

Cơ chế này xây MockMvc từ context; mặc dù interface kiểm tra response vẫn là `WebTestClient`, HTTP request/response là mock Servlet. Nếu cần assertion dành riêng cho Servlet MVC như model attribute, forwarded URL hoặc resolved exception, có thể chuyển kết quả phù hợp sang MockMvc result actions thông qua tiện ích tương ứng của `MockMvcWebTestClient`.

### 5.3. Những gì context test có thể và không thể khẳng định

Đây là **integration test của web layer trong Spring context**, không tự động là end-to-end network test.

Test có thể kiểm chứng routing/codec/filter/security được nạp trong context. Nhưng còn những câu hỏi như “reverse proxy có chuyển tiếp header không”, “TLS certificate có đúng không”, “server nén response ra sao”, “database thật có hoạt động không” phụ thuộc môi trường tương ứng. Khi các yếu tố mạng hoặc deployment là đối tượng cần chứng minh, dùng `bindToServer` trên hạ tầng phù hợp.

## 6. Bind vào máy chủ HTTP thật

```java
WebTestClient client = WebTestClient.bindToServer()
        .baseUrl("http://localhost:8080")
        .responseTimeout(Duration.ofSeconds(5))
        .build();

client.get()
        .uri("/books/7")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.id").isEqualTo(7);
```

Khác với mock binding, setup này gửi HTTP đến process/server thực đang lắng nghe port. Nó có thể test ứng dụng chạy ở localhost, test container, môi trường integration hoặc một staging environment đã được chuẩn bị.

Trong JUnit của Spring Boot, dùng **random port** để tránh đụng cổng giữa các test/process:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveHttpTest {
    @LocalServerPort
    int port;

    @Test
    void checksLiveEndpoint() {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build();

        client.get().uri("/books/7")
                .exchange()
                .expectStatus().isOk();
    }
}
```

`@LocalServerPort` thuộc `org.springframework.boot.test.web.server.LocalServerPort`. Đây là snippet đầy đủ về ý tưởng; project demo phía dưới có cấu trúc test chạy được.

**Những điểm cần nhớ:**

- `baseUrl(...)` là địa chỉ tuyệt đối (scheme, host, port), nên không cần port khi gọi `uri("/books/7")`.
- `RANDOM_PORT` chỉ dành cho **embedded server được khởi chạy bởi test**; không dùng một cổng đoán trước.
- Timeout giới hạn thời gian chờ phản hồi trong test để tránh treo CI.
- Để kiểm tra TLS, reverse proxy hoặc networking thực, kết nối tới chính tầng mạng mà bạn muốn kiểm chứng. Live localhost chỉ xác minh stack trên host local.
- Nếu chạy test song song, tránh state dùng chung không cô lập; nếu cần database/external services, dùng fixtures/test containers riêng.

## 7. Viết assertion với WebTestClient

### 7.1. Request: path, query, header, body

```java
client.post()
        .uri(builder -> builder.path("/books")
                .queryParam("source", "test")
                .build())
        .header("X-Correlation-Id", "test-123")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("title", "HTTP Tests"))
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals("Location", "/books/9")
        .expectBody()
        .jsonPath("$.title").isEqualTo("HTTP Tests");
```

Request body có thể là DTO, record, map hoặc publisher có codec hỗ trợ. Kiểu HTTP body và expected response phải khớp API. Trong test, `Content-Type` mô tả request gửi lên, còn `Accept` mô tả response mong muốn. Đối với endpoint GET không có body, `contentType(...)` thường không cần thiết.

### 7.2. Kiểm tra status và header

Các phương thức thường dùng:

```java
client.get().uri("/books/7")
        .exchange()
        .expectStatus().isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
```

Tùy endpoint, có thể dùng `isCreated()`, `isNoContent()`, `isNotFound()`, `is4xxClientError()` hoặc kiểm tra status cụ thể bằng `isEqualTo(422)`. `expectHeader().valueEquals(...)` kiểm tra chính xác giá trị header; `valueMatches(...)` cho regex khi hợp đồng cần dạng mẫu.

Với lỗi, đừng kiểm tra một JSON error body “mặc định” chưa chắc cố định giữa các phiên bản Spring Boot. Nếu hợp đồng API chỉ cam kết 404, test status 404; chỉ test body lỗi khi ứng dụng thực sự định nghĩa schema.

### 7.3. Kiểm tra JSON theo cấu trúc

```java
client.get().uri("/books/7")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.id").isEqualTo(7)
        .jsonPath("$.title").isEqualTo("Spring");
```

`jsonPath` hữu ích vì thứ tự keys, khoảng trắng và định dạng JSON thường không quan trọng về ngữ nghĩa. Có thể dùng `expectBody(Book.class)` để decode thành DTO và assert record/object. Muốn kiểm tra cả danh sách, `expectBodyList(Book.class)` hỗ trợ assertions về size và phần tử.

### 7.4. Nhận toàn bộ kết quả để assert sâu hơn

```java
EntityExchangeResult<Book> response = client.get()
        .uri("/books/7")
        .exchange()
        .expectStatus().isOk()
        .expectBody(Book.class)
        .returnResult();

assertEquals(7L, response.getResponseBody().id());
```

Imports: `org.springframework.test.web.reactive.server.EntityExchangeResult`, `org.junit.jupiter.api.Assertions.assertEquals`. Đây là đoạn để đặt trong test, giả định `Book` đã khai báo trong project. `returnResult()` hữu ích khi muốn assert những điều mà JSONPath không tiện diễn đạt; đừng mặc định DTO luôn khác null nếu endpoint có thể trả HTTP 204.

### 7.5. Reactive stream và dữ liệu vô hạn

Nếu endpoint trả stream nhiều phần tử (SSE/NDJSON), đừng dùng `expectBodyList(...)` với luồng vô hạn: nó có thể chờ tới timeout hoặc tích lũy quá nhiều phần tử. Thay vào đó:

```java
FluxExchangeResult<Book> response = client.get()
        .uri("/books/events")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus().isOk()
        .returnResult(Book.class);

StepVerifier.create(response.getResponseBody())
        .expectNext(new Book(7, "Spring"))
        .expectNext(new Book(8, "Reactor"))
        .verifyComplete();
```

Đoạn trên giả định server phát **đúng hai phần tử** và hoàn tất. `StepVerifier` thuộc `reactor-test`; `FluxExchangeResult` thuộc `org.springframework.test.web.reactive.server`. Nếu server phát vô hạn, áp dụng `take(n)` hoặc hủy subscription có chủ đích với timeout. Streaming semantics phải được cả server, media type và decoder hỗ trợ.

## 8. Ví dụ hoàn chỉnh: bốn kiểu binding trên Spring Boot WebFlux

Dự án sau có:

- Một `@RestController` với GET, POST và endpoint sự kiện.
- Một `RouterFunction<ServerResponse>`.
- Một test class dùng **cả bốn mode**: controller, router function, application context và live server.
- Kiểm thử JSON body, Location header, 404 và `Flux` streaming.

Cấu hình dựa trên **Spring Boot 3.3.6** (Spring Framework 6.1.x), Java 17. Những API dùng trong demo cũng phù hợp về ý tưởng với Spring Framework 6.2. Không cần database hoặc external HTTP service.

### 8.1. Cấu trúc

```text
webtestclient-demo/
├── build.gradle
└── src/
    ├── main/java/demo/webtest/
    │   ├── WebTestDemoApplication.java
    │   └── BookController.java
    └── test/java/demo/webtest/
        └── WebTestClientModesTest.java
```

### 8.2. build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.6'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'demo'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

`spring-boot-starter-webflux` mang WebFlux server và WebClient runtime; `spring-boot-starter-test` cung cấp JUnit 5, Spring Test, WebTestClient test support và thư viện assertions; `reactor-test` cho `StepVerifier`. Gradle plugin và dependency management đồng bộ phiên bản từ Spring Boot.

**Không thêm đồng thời `spring-boot-starter-web` vào demo này**, vì Spring Boot có quy tắc tự chọn web stack khi cả Spring MVC và WebFlux cùng trên classpath. Ví dụ muốn minh họa thuần WebFlux để những binding cụ thể đúng context.

### 8.3. WebTestDemoApplication.java

```java
package demo.webtest;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@SpringBootApplication
public class WebTestDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebTestDemoApplication.class, args);
    }

    @Bean
    RouterFunction<ServerResponse> helloRouter() {
        return RouterFunctions.route(
                GET("/hello/{name}"),
                request -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "message",
                                "Hello " + request.pathVariable("name"))));
    }
}
```

`helloRouter()` tạo endpoint functional `GET /hello/{name}`. Handler đọc biến `name` và tạo JSON response, ví dụ gọi `/hello/Lan` trả `{"message":"Hello Lan"}`. Nó là bean của ApplicationContext nên vừa được phép kiểm thử tách riêng bằng `bindToRouterFunction`, vừa được load chung khi bind context hoặc gọi server thật.

### 8.4. BookController.java

```java
package demo.webtest;

import java.net.URI;
import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/books")
public class BookController {

    public record Book(long id, String title) {}
    public record CreateBook(String title) {}

    @GetMapping("/{id}")
    public Mono<Book> one(@PathVariable long id) {
        if (id != 7) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Book not found"));
        }
        return Mono.just(new Book(7, "Spring"));
    }

    @GetMapping
    public Flux<Book> all(@RequestParam(defaultValue = "2") int limit) {
        return Flux.just(
                new Book(7, "Spring"),
                new Book(8, "Reactor"))
                .take(Math.max(0, Math.min(limit, 2)));
    }

    @PostMapping
    public Mono<ResponseEntity<Book>> create(
            @RequestBody Mono<CreateBook> input) {
        return input.map(dto -> ResponseEntity
                .created(URI.create("/books/9"))
                .body(new Book(9, dto.title())));
    }

    @GetMapping(
            value = "/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Book> events() {
        return Flux.just(
                new Book(7, "Spring"),
                new Book(8, "Reactor"))
                .delayElements(Duration.ofMillis(10));
    }
}
```

Controller này không giữ state mutable: mỗi test có input và kết quả xác định. `Mono.error(ResponseStatusException)` trở thành HTTP 404 qua tầng WebFlux. `create()` đọc JSON request thành `CreateBook` và trả status 201 với `Location: /books/9`. `events()` phát hai object theo định dạng SSE rồi kết thúc, để `StepVerifier` có thể xác nhận `verifyComplete()`.

Lưu ý `all()` không phụ thuộc database: demo ưu tiên cơ chế test, không mô phỏng persistence. Cần kiểm thử business logic, persistence hay transaction thật thì thêm các thành phần đó trong bài test chuyên biệt.

### 8.5. WebTestClientModesTest.java

```java
package demo.webtest;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.test.StepVerifier;

import demo.webtest.BookController.Book;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class WebTestClientModesTest {

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    RouterFunction<ServerResponse> helloRouter;

    @Test
    void bindToController_usesMockWebFluxRequest() {
        WebTestClient client = WebTestClient
                .bindToController(new BookController())
                .build();

        client.get()
                .uri("/books/{id}", 7)
                .exchange()
                .expectStatus().isOk()
                .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                    .jsonPath("$.id").isEqualTo(7)
                    .jsonPath("$.title").isEqualTo("Spring");
    }

    @Test
    void bindToRouterFunction_testsFunctionalEndpoint() {
        WebTestClient client = WebTestClient
                .bindToRouterFunction(helloRouter)
                .build();

        client.get()
                .uri("/hello/Lan")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                    .jsonPath("$.message").isEqualTo("Hello Lan");
    }

    @Test
    void bindToApplicationContext_usesConfiguredWebFluxPipeline() {
        WebTestClient client = WebTestClient
                .bindToApplicationContext(applicationContext)
                .configureClient()
                .responseTimeout(Duration.ofSeconds(5))
                .build();

        client.post()
                .uri("/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", "HTTP Tests"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader()
                    .valueEquals("Location", "/books/9")
                .expectBody()
                    .jsonPath("$.id").isEqualTo(9)
                    .jsonPath("$.title").isEqualTo("HTTP Tests");
    }

    @Test
    void bindToServer_sendsHttpToRandomPort() {
        WebTestClient client = WebTestClient
                .bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .responseTimeout(Duration.ofSeconds(5))
                .build();

        client.get()
                .uri("/books/404")
                .exchange()
                .expectStatus().isNotFound();

        client.get()
                .uri("/books?limit=2")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Book.class)
                .hasSize(2);
    }

    @Test
    void streamResultCanBeCheckedWithStepVerifier() {
        WebTestClient client = WebTestClient
                .bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .responseTimeout(Duration.ofSeconds(5))
                .build();

        FluxExchangeResult<Book> response = client.get()
                .uri("/books/events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(Book.class);

        StepVerifier.create(response.getResponseBody())
                .expectNext(new Book(7, "Spring"))
                .expectNext(new Book(8, "Reactor"))
                .verifyComplete();
    }
}
```

**Chú ý về cú pháp import:** Trong Java, cách đúng để import nested record là `import demo.webtest.BookController.Book;`, **không phải static import type**. Hãy dùng dòng sau thay cho dòng `import static demo.webtest.BookController.Book;` trong phần imports nếu IDE/compiler không chấp nhận:

```java
import demo.webtest.BookController.Book;
```

Mỗi test ở trên kiểm tra một đường khác nhau. Vì dùng `@SpringBootTest(RANDOM_PORT)` trên cả lớp, embedded server được tạo chung cho test class, **ngay cả ba test mock**. Điều này là lựa chọn minh họa tiện gom đủ bốn chế độ trong một file. Trong test suite thực tế, nên đặt các test mock controller/router vào class **không có `@SpringBootTest`** để tăng tốc và chứng minh chúng không cần khởi động server.

### 8.6. Chạy dự án

Tại thư mục có `build.gradle`, cài JDK 17+ và Gradle tương thích, chạy:

```bash
gradle test
```

Hoặc `./gradlew test` (Unix/macOS) / `gradlew.bat test` (Windows) nếu đã có Gradle Wrapper. Spring Boot 3.3.6 tương thích Java 17; nếu dùng JDK 21 cũng ổn.

Các kết quả mong đợi theo **hợp đồng HTTP trong demo**:

| Request/test | HTTP status | Đặc điểm body/header |
| --- | --- | --- |
| Controller `GET /books/7` | 200 | JSON có `id=7`, `title=Spring` |
| Router `GET /hello/Lan` | 200 | JSON có `message=Hello Lan` |
| Context `POST /books` | 201 | `Location: /books/9`, JSON `id=9` |
| Server `GET /books/404` | 404 | Không giả định schema lỗi cụ thể |
| Server `GET /books?limit=2` | 200 | JSON array 2 cuốn sách |
| Server `GET /books/events` | 200 | SSE gồm hai `Book`, sau đó kết thúc |

Không cam kết một stdout cố định của Gradle: logs và kết quả build phụ thuộc phiên bản Gradle, JDK và môi trường. Bài kiểm thử đạt khi JUnit 5 assertions đều pass. Để xem một request thật trong trình duyệt, chạy `gradle bootRun` và mở `http://localhost:8080/books/7`.

## 9. Tổ chức test suite hợp lý trong dự án thật

Một thiết kế thực tế thường chia:

```text
src/test/java/...
├── BookControllerMockTest.java       // bindToController
├── HelloRouterTest.java              // bindToRouterFunction
├── WebContextIntegrationTest.java    // bindToApplicationContext
└── LiveServerIntegrationTest.java    // bindToServer + RANDOM_PORT
```

Tại sao?

- **Controller test** chạy nhanh, ít dependency, tập trung status, binding, serialization của một controller.
- **RouterFunction test** xác minh route predicate và handler function; không cần Spring context nếu tự tạo router.
- **ApplicationContext test** xác minh component scan, filters, `@ControllerAdvice`, converters và cấu hình thực sự được nạp.
- **Live server test** xác minh thêm HTTP transport, server configuration và các vấn đề có thể chỉ xuất hiện khi request đi qua real socket.

Đừng dùng test quá nặng cho lỗi rất đơn giản. Ngược lại, test mock một controller độc lập không đủ làm bằng chứng rằng production web security, proxy và kết nối bên ngoài hoạt động.

### 9.1. Test slice và Spring Boot annotations

Trong Boot WebFlux, `@WebFluxTest` có thể tạo context rút gọn chỉ cho web slice; `@AutoConfigureWebTestClient` có thể cấu hình client để injection tùy kiểu test. Nếu dùng injection `@Autowired WebTestClient` thì phải bảo đảm test annotation/configuration hỗ trợ nó, thay vì tưởng mọi `@SpringBootTest` đều tự tạo WebTestClient ở mọi cấu hình.

Với MVC, `@WebMvcTest` và `MockMvc` là con đường phổ biến cho controller slice. Nếu muốn giao diện assertion của WebTestClient, có thể dùng `MockMvcWebTestClient` trên MockMvc hoặc MVC context phù hợp. Chọn test annotation theo **stack phía server**.

### 9.2. Dữ liệu test, isolation và concurrency

Nếu có database thật, dùng fixture có thể tái lập, cleanup/transaction rõ ràng và test containers khi cần. Với server chạy ở random port, mỗi test process có thể chọn một cổng tự do nhưng các test vẫn có thể chia sẻ database/cache. Không dựa vào thứ tự chạy của JUnit; tránh phụ thuộc state tạo bởi test khác. Khi kiểm tra streaming, đặt timeout và hủy subscription để test không bị treo.

## 10. Cấu hình client và khả năng chẩn đoán

### 10.1. configureClient() và bindToServer()

Mock server binding trả một `MockServerSpec`. Sau khi chỉnh setup mock server, dùng `configureClient()` để chuyển sang phần cấu hình HTTP client:

```java
WebTestClient client = WebTestClient
        .bindToController(new BookController())
        .configureClient()
        .baseUrl("/api")
        .defaultHeader("X-Test-Source", "junit")
        .responseTimeout(Duration.ofSeconds(5))
        .build();
```

Nếu controller mapping thực là `/books/...` và đặt `baseUrl("/api")`, request `client.get().uri("/books/7")` mang path có tiền tố `/api`: controller ví dụ sẽ không khớp nếu không có route tương ứng. Đoạn cấu hình là ví dụ API **không dùng trực tiếp để chạy test trong demo**. Hãy chỉ định base URL thật sự đúng với server setup.

Với live server, các client options thường được cấu hình trực tiếp từ `bindToServer()`:

```java
WebTestClient client = WebTestClient.bindToServer()
        .baseUrl("http://127.0.0.1:8080")
        .defaultHeader("X-Test-Source", "junit")
        .responseTimeout(Duration.ofSeconds(5))
        .build();
```

Không log Authorization hoặc cookie thật. Test dùng HTTP headers khi cần kiểm tra hợp đồng; riêng việc có header trong request không chứng minh server đã xác thực user. Nếu kiểm thử Spring Security, dùng security test utilities và assertion 401/403/200 theo từng user/role cụ thể.

### 10.2. Assertion thông báo lỗi dễ hiểu

Giảm độ khó chẩn đoán khi test fail bằng cách chia expectations rõ ràng và thêm mô tả hoặc dùng `consumeWith(...)` cho assertions tùy chỉnh. Khi dữ liệu là JSON, thường chọn `jsonPath` hoặc `json(...)` theo nội dung hợp đồng, thay vì so sánh chuỗi JSON chứa thứ tự keys/whitespace không quan trọng.

Với body lớn hoặc streaming, chỉ log lượng dữ liệu vừa đủ và đã loại thông tin nhạy cảm. Mọi mock/live test nên đặt timeout hợp lý; không dùng `Thread.sleep(...)` để “đợi server”.

### 10.3. Request và response có cấu trúc

Sau `exchange()` bạn có thể kiểm tra:

- `expectStatus()`: HTTP status.
- `expectHeader()`: Content-Type, Location, ETag hoặc custom headers.
- `expectCookie()`: HTTP response cookies khi hợp đồng có cookie.
- `expectBody()`: body bytes, JSONPath hoặc JSON assertion.
- `expectBody(T.class)`, `expectBodyList(T.class)`: giải mã body thành DTO/collection.
- `returnResult(T.class)`: kiểm tra publisher response, nhất là stream nhiều phần tử.

Đặt assertion đúng hợp đồng: nếu chỉ cam kết 204 No Content, không cố đọc một DTO; nếu phản hồi là SSE vô hạn, tránh API buộc đọc toàn bộ body.

## 11. Những lỗi thường gặp

1. **Gọi `WebTestClient.bindToController` cho controller MVC rồi mong test Servlet stack.** Với MVC, dùng `MockMvcWebTestClient.bindToController` hoặc MockMvc; với WebFlux mới dùng trực tiếp.
2. **Dùng `bindToRouterFunction` với `org.springframework.web.servlet.function.RouterFunction`.** Method này dành cho WebFlux functional router; đây là hai kiểu khác package.
3. **Nghĩ mock binding tự chạy HTTP server.** `bindToController`, `bindToRouterFunction` và `bindToApplicationContext` chạy mock request/response, không mở network connection.
4. **Nghĩ test có `RANDOM_PORT` nghĩa client luôn dùng server thật.** Chỉ `bindToServer()` với URL/port thật mới đi qua socket; `bindToApplicationContext` vẫn là mock mode.
5. **Quên cấu hình route/baseUrl và nhận 404 giả.** Soát đường dẫn được tạo từ base URL, controller mapping và URI template.
6. **Đặt `@SpringBootTest` cho mọi controller unit test.** Khi không cần context, `bindToController` có thể chạy nhanh hơn và cô lập hơn.
7. **Dùng `expectBodyList` cho stream vô hạn.** Dùng `returnResult`, Reactor `StepVerifier`, timeout và giới hạn phần tử phù hợp.
8. **Assert toàn bộ body lỗi Spring mặc định.** Schema lỗi có thể thay đổi theo cấu hình/phiên bản; test hợp đồng nghiệp vụ đã được xác định.
9. **Gọi service thật ngoài Internet từ test mock.** Nếu controller tự gọi service ngoài qua HTTP, bind controller không tự chặn những calls đó. Stub/mock dependency hoặc tạo test integration riêng.
10. **Test security bằng một controller độc lập nhưng quên filters/security chain.** Chọn context/security configuration hoặc live server mode để đánh giá bảo vệ end-to-end.
11. **Quên dependency reactor-test khi dùng StepVerifier.** `reactor-test` là thư viện kiểm thử Reactor, cần nằm trong test classpath.
12. **So sánh JSON theo thứ tự thuộc tính hoặc timing chính xác.** Kiểm tra nội dung theo contract, dùng timeout hợp lý và tránh giả định scheduling.

## 12. Bài tập thực hành

**Bài 1 — Controller mock:** Viết test GET `/books/7` trả 200, `id=7`; GET `/books/8` trả 404. Dùng `WebTestClient.bindToController(new BookController())`. Nêu những thành phần Spring application chưa được xác minh.

**Bài 2 — Functional router:** Đổi router thành `GET /hello/{name}?lang=vi`, response JSON là `{"message":"Xin chào Lan"}`. Test một URL đúng và một URL sai với `bindToRouterFunction`.

**Bài 3 — Context wiring:** Thêm một `WebFilter` vào application context để gắn header response `X-Env: test`. Tại sao test `bindToApplicationContext` thích hợp để xác nhận filter này hơn test một controller standalone không bổ sung filter?

**Bài 4 — Live server:** Chạy test với `RANDOM_PORT`, gửi GET qua `bindToServer` tới `http://127.0.0.1:<port>/books/7`. Thay port thành một cổng không có server, phân biệt lỗi kết nối với một HTTP status 404.

**Bài 5 — POST và Location:** Dùng client từ context test để POST `{"title":"New Book"}` tới `/books`. Assert HTTP 201, `Location: /books/9` và JSON title đúng. Giải thích khác biệt giữa `contentType` và `accept`.

**Bài 6 — SSE:** Giả sử endpoint `/books/events` phát vô hạn. Viết test chỉ nhận hai phần tử đầu rồi hủy subscription. Vì sao `verifyComplete()` không đúng nếu stream không bao giờ hoàn tất?

**Bài 7 — Chọn mức kiểm thử:** Với một bug mapping path variable, một bug thiếu WebFilter bean, một bug TLS certificate và một bug reverse proxy strip header, hãy chọn mode ít tốn chi phí nhất mà vẫn kiểm chứng được từng lỗi. Phân biệt cái nào đòi môi trường live thật.

## Tóm tắt kiến thức

- `WebTestClient` thống nhất cú pháp request và assertions cho mock và live HTTP test.
- `bindToController`: tập trung một hoặc nhiều controller, thường nhanh và cô lập.
- `bindToRouterFunction`: kiểm thử **functional endpoints WebFlux** trong bộ nhớ.
- `bindToApplicationContext`: kiểm thử web infrastructure đã được load từ Spring context, vẫn là mock HTTP.
- `bindToServer`: gửi HTTP qua mạng đến server đang chạy, phù hợp integration/e2e hơn.
- Trong **Spring MVC**, dùng `MockMvcWebTestClient` cho controller/context mock, còn live server vẫn dùng `WebTestClient.bindToServer`.
- `exchange()` kích hoạt request; sau đó dùng `expectStatus`, `expectHeader`, `expectBody` hoặc `returnResult` và `StepVerifier`.
- Chọn mode dựa vào bug cần bắt, thời gian chạy và các hạ tầng thật sự được đưa vào phạm vi test.

## Tài liệu tham khảo chính thức

- Spring Framework 6.2 — WebTestClient và bốn chế độ setup: https://docs.spring.io/spring-framework/reference/6.2/testing/webtestclient.html
- Spring Framework 6.2 — Spring MVC test framework: https://docs.spring.io/spring-framework/reference/6.2/testing/spring-mvc-test-framework.html
- Spring Framework 6.2 — WebFlux functional endpoints: https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html
- Spring Framework — WebTestClient Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/web/reactive/server/WebTestClient.html
- Spring Boot — Testing Spring Boot Applications: https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-testing-009">Kiểm thử Flux, SSE, hủy đăng ký và giới hạn thời gian</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử Flux, SSE, hủy đăng ký và giới hạn thời gian trong Spring WebFlux

## 1. Mục tiêu: kiểm thử một luồng khác với kiểm thử một JSON response

Trong Spring WebFlux, một HTTP response có thể chứa **một đối tượng** (`Mono<T>`), **một chuỗi hữu hạn** (`Flux<T>`), hoặc **một luồng dài hạn/không kết thúc** (Server-Sent Events — SSE, NDJSON streaming…). Kiểm thử phải quan sát các tín hiệu Reactive Streams, không chỉ HTTP status.

Ba loại tín hiệu quan trọng của `Publisher`:

- `onNext(value)`: có phần tử mới.
- `onComplete()`: luồng kết thúc thành công; **không bắt buộc** phải xảy ra với stream SSE sống liên tục.
- `onError(error)`: luồng kết thúc có lỗi; không thể tiếp tục phát `onNext` sau đó.

Ngoài ba tín hiệu downstream, `cancel()` là hành động từ **subscriber gửi ngược lên upstream** để hủy subscription. Hủy không phải lỗi, cũng không phải `onComplete` của nguồn.

```text
                   HTTP status + headers
WebTestClient.exchange() ────────┐
                                 ▼
                       FluxExchangeResult<T>
                                 │ getResponseBody()
                                 ▼
                         Flux<T> đã decode
                                 │
                         StepVerifier
                   ┌─────────────┼──────────────┐
                   ▼             ▼              ▼
              expectNext     expectError     thenCancel
                   │             │              │
             verifyComplete    verify       verify
             (luồng hữu hạn) (kết thúc lỗi) (luồng vô hạn)
```

**Hai lớp kiểm thử bổ sung nhau:**

1. **Kiểm thử HTTP contract/streaming** bằng `WebTestClient`: status, `Content-Type`, mã hóa SSE/NDJSON, dữ liệu đầu ra và behavior khi client hủy.
2. **Kiểm thử trực tiếp Reactor publisher** bằng `StepVerifier`: đúng thứ tự dữ liệu, hoàn tất/lỗi, backpressure, cancellation và các operator thời gian với virtual time.

Bài dùng **Spring Framework 6.1.x, Spring Boot 3.3.x, Java 17+, Reactor Test/JUnit 5**. Mẫu Java độc lập và lệnh chạy nằm ở phần 7.

## 2. Flux hữu hạn: assert phần tử, thứ tự, hoàn tất và lỗi

Khi một endpoint trả chuỗi hữu hạn, cần kiểm tra **đúng loại nội dung**, thứ tự, số phần tử và tín hiệu cuối.

Ví dụ nguồn Reactor thuần:

```java
Flux<Integer> values = Flux.just(2, 4, 6);

StepVerifier.create(values)
    .expectNext(2, 4, 6)
    .verifyComplete();
```

`StepVerifier.create(...)` đăng ký một subscriber kiểm thử. `expectNext(...)` là so sánh từng phần tử **theo thứ tự**. `verifyComplete()` vừa kỳ vọng `onComplete` vừa thực thi verification. Nếu nguồn phát thêm phần tử, phát lỗi hoặc không bao giờ hoàn tất, test sẽ thất bại hoặc có thể bị treo nếu không có thời hạn kiểm thử.

Đối với dữ liệu phức tạp:

```java
StepVerifier.create(service.findProducts())
    .assertNext(p -> assertEquals("Notebook", p.name()))
    .expectNextMatches(p -> p.price() > 0)
    .verifyComplete();
```

Đây là **mã minh họa**, phụ thuộc `service`, `Product.price()` và import `assertEquals` được định nghĩa trong ứng dụng. `assertNext` dùng JUnit assertions bên trong; `expectNextMatches` dùng predicate. Dùng `expectNextCount(n)` khi chỉ cần biết có n phần tử, nhưng với dữ liệu quan trọng nên kiểm tra cả nội dung.

Kiểm tra nguồn kết thúc lỗi:

```java
Flux<String> broken = Flux.concat(
    Flux.just("first"),
    Flux.error(new IllegalStateException("upstream failed"))
);

StepVerifier.create(broken)
    .expectNext("first")
    .expectErrorMatches(error ->
        error instanceof IllegalStateException
        && "upstream failed".equals(error.getMessage()))
    .verify();
```

Các snippet này cần imports `reactor.core.publisher.Flux`, `reactor.test.StepVerifier` và JUnit assertions khi phù hợp. Lưu ý `expectErrorMatches(...).verify()` cho một terminal error; không dùng `verifyComplete()` nếu hợp đồng của luồng là phát lỗi.

**Với HTTP endpoint trả `Flux<T>`:** `expectBodyList(T.class)` phù hợp khi response **hữu hạn và được gom hoàn chỉnh**. Với stream không kết thúc, hãy dùng `returnResult(T.class)` để lấy dữ liệu theo dòng, không gom toàn bộ body.

## 3. SSE: hiểu định dạng để viết assertion chính xác

**Server-Sent Events (SSE)** là cơ chế HTTP một chiều server → client, thường phản hồi `Content-Type: text/event-stream`. Mỗi event có thể bao gồm các trường:

```text
id: 17
event: price-updated
data: {"sku":"P1","price":120}

```

Dòng trống đánh dấu ranh giới event. Có thể có `retry: 5000`, nhiều dòng `data:`, dòng comment `: heartbeat` hoặc sự kiện chỉ có data. Thứ tự và presence của các trường phụ thuộc server; đừng áp đặt tất cả event phải có `id`, `event` hay `retry` nếu hợp đồng không yêu cầu.

Spring hỗ trợ `ServerSentEvent<T>`:

```java
@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> events() {
    return Flux.interval(Duration.ofSeconds(1))
        .map(index -> ServerSentEvent.<String>builder()
            .id(String.valueOf(index))
            .event("tick")
            .data("value-" + index)
            .build());
}
```

Đây là đoạn **minh họa trong controller**; cần các imports `GetMapping`, `MediaType`, `Flux`, `ServerSentEvent`, `Duration`. Nguồn `Flux.interval` phát 0, 1, 2… theo thời gian. Do không có `take(...)`, nó **không tự hoàn tất**.

Kiểm thử SSE qua `WebTestClient`:

```java
FluxExchangeResult<ServerSentEvent<String>> result = client.get()
    .uri("/events")
    .accept(MediaType.TEXT_EVENT_STREAM)
    .exchange()
    .expectStatus().isOk()
    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
    .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

StepVerifier.create(result.getResponseBody())
    .assertNext(e -> {
        assertEquals("tick", e.event());
        assertEquals("0", e.id());
        assertEquals("value-0", e.data());
    })
    .assertNext(e -> assertEquals("value-1", e.data()))
    .thenCancel()
    .verify(Duration.ofSeconds(5));
```

Cần imports `FluxExchangeResult`, `ParameterizedTypeReference`, `StepVerifier`, `MediaType`, `ServerSentEvent`, `Duration` và JUnit assertions. Đây là **mã minh họa**; biến `client` cần được khởi tạo đúng; ví dụ hoàn chỉnh ở phần 7. `returnResult(new ParameterizedTypeReference<...>() {})` giữ thông tin kiểu generic để decoder chọn đúng `ServerSentEvent<String>`. Nếu chỉ lấy dữ liệu `data`, có thể decode trực tiếp thành `String` theo media type và codec tương ứng, nhưng khi cần kiểm tra `id/event/retry` nên giải mã `ServerSentEvent<T>`.

**Đừng kiểm tra raw bytes một cách cứng nhắc** nếu mục tiêu là semantic: encoder có thể biểu diễn newline hoặc metadata khác nhau tùy codec/version. Kiểm tra status, `Content-Type`, dữ liệu và event metadata theo hợp đồng. Kiểm tra raw SSE frames riêng khi hợp đồng yêu cầu chính xác wire format.

### SSE khác WebSocket và NDJSON

- **SSE:** một chiều server → client trên HTTP, media type `text/event-stream`, event framing với `data/id/event/retry`.
- **NDJSON:** nhiều JSON object theo từng dòng, media type thường là `application/x-ndjson`; có thể decode thành `Flux<Dto>`, không có metadata SSE.
- **WebSocket:** kết nối hai chiều với protocol framing khác; không dùng `WebTestClient` SSE assertions để kiểm thử một WebSocket session. Với WebSocket dùng `WebSocketClient`/`WebSocketHandler` hoặc công cụ test thích hợp.

`Content-Type` chỉ xác định cách body được diễn giải; nó không tự bảo đảm server thật phát từng chunk mà không bị proxy buffer, vì vậy cần test thực qua HTTP khi muốn kiểm tra streaming network.

## 4. Không bao giờ chờ onComplete của stream vô hạn

Đây là **anti-pattern**:

```java
// Sai nếu endpoint /events không bao giờ onComplete:
client.get().uri("/events")
    .exchange()
    .expectBodyList(String.class)
    .hasSize(10);
```

`expectBodyList` thường đòi đọc hết response để gom mọi phần tử; một stream liên tục có thể không kết thúc và tăng thời gian/bộ nhớ. Tương tự, các thao tác `collectList().block()` hoặc `blockLast()` trên `Flux.interval` vô hạn là không phù hợp.

Cách đúng: kiểm tra một số event có ý nghĩa rồi **chủ động hủy** subscription:

```java
StepVerifier.create(result.getResponseBody())
    .expectNextCount(3)
    .thenCancel()
    .verify(Duration.ofSeconds(5));
```

`thenCancel()` diễn đạt rõ kỳ vọng: **test dừng sau ba sự kiện, không đòi server gửi `onComplete`**. Thời hạn `verify(Duration.ofSeconds(5))` giúp test fail có giới hạn nếu sự kiện không bao giờ đến.

Có thể dùng `take(n)` để tạo **Flux hữu hạn ở downstream**, ví dụ:

```java
StepVerifier.create(source.take(3))
    .expectNextCount(3)
    .verifyComplete();
```

`take(3)` thu nhận ba phần tử rồi **cancel subscription upstream** và phát `onComplete` cho downstream. Hai quan sát “downstream hoàn tất” và “upstream bị hủy” khác nhau nhưng tương thích. Nếu cần chứng minh cleanup phía producer, phải theo dõi tín hiệu cancel hoặc `doFinally` tại nguồn.

## 5. Hủy đăng ký: kiểm tra đúng SignalType và cleanup

**Cancellation** xảy ra khi người tiêu thụ không muốn nhận thêm dữ liệu. Ví dụ: tab bị đóng, client ngắt SSE, `StepVerifier.thenCancel()`, downstream `take(2)` hoặc timeout hủy nguồn. Reactive Streams gửi `cancel()` ngược về upstream; phần còn lại của pipeline có thể thực hiện `doOnCancel` và `doFinally(SignalType.CANCEL)`.

Ví dụ đơn giản:

```java
AtomicInteger cancelCalls = new AtomicInteger();

Flux<Long> source = Flux.interval(Duration.ofMillis(20))
    .doOnCancel(cancelCalls::incrementAndGet);

StepVerifier.create(source)
    .expectNext(0L, 1L)
    .thenCancel()
    .verify(Duration.ofSeconds(3));

assertEquals(1, cancelCalls.get());
```

Đây là snippet minh họa; cần imports `AtomicInteger`, `Duration`, `Flux`, `StepVerifier` và `assertEquals`. Cách viết với `Flux.interval` và thời gian thật có thể bị ảnh hưởng bởi scheduling; phần 7 cung cấp test **virtual time** ổn định hơn.

Phân biệt:

```java
source
    .doOnCancel(() -> cleanupSubscription())
    .doFinally(signal -> recordFinalSignal(signal));
```

- `doOnCancel` được gọi khi source nhận cancel.
- `doFinally` được gọi sau complete, error hoặc cancel; phân biệt bằng `SignalType.ON_COMPLETE`, `ON_ERROR`, `CANCEL`.
- `doOnTerminate` chỉ phản ánh onComplete/onError ở điểm đặt operator, **không thay thế** `doFinally` khi cần dọn tài nguyên cho cả cancellation.
- Khi đăng ký resource như listener, timer, file handle hoặc subscription đến broker, nên dùng `Flux.using`/`usingWhen` hoặc cleanup phù hợp; viết cleanup idempotent vì nhiều luồng lỗi/timeout có thể tương tác.
- `StepVerifier.thenCancel` xác minh luồng client dừng theo kịch bản, **không tự chứng minh kernel socket đã đóng hay server container đã giải phóng connection**. Cần test thực ở tầng network/lifecycle nếu đó là yêu cầu.

**Kiểm thử cancellation không đồng nghĩa kiểm thử backpressure**: backpressure là `request(n)` điều tiết nhu cầu; cancel là kết thúc subscription. Khi muốn kiểm tra số lượng yêu cầu, dùng `StepVerifier.create(source, initialRequest)` rồi `thenRequest(n)` với `TestPublisher` hoặc nguồn tôn trọng demand. Không suy đoán demand từ số event bạn nhìn thấy.

## 6. Ba loại giới hạn thời gian khác nhau

| Cơ chế | Điều đang giới hạn | Biểu hiện khi vượt |
| --- | --- | --- |
| `WebTestClient.Builder.responseTimeout(Duration)` | Thời gian WebTestClient đợi **response** trong quá trình exchange; mặc định của Spring 6.1 là 5 giây | Exchange/test có thể thất bại vì chờ response quá lâu |
| `StepVerifier.verify(Duration)` | Thời gian thực tối đa dành cho **kịch bản assertion của StepVerifier** | Test fail khi không đạt terminal/cancel expectation kịp hạn; không phải HTTP response |
| Reactor `Publisher.timeout(Duration)` | Khoảng chờ tín hiệu tiếp theo của publisher (tùy overload có timeout cho từng item) | Phát `TimeoutException` downstream và hủy upstream |
| Reactor `take(Duration)` | Khoảng thời gian cho phép phát các phần tử | Hoàn tất downstream khi hết thời gian, thường cancel upstream |
| Connect/read/response timeout của HTTP client nền | DNS/TCP connect, chờ byte response, transport | Lỗi request/transport tương ứng, khác timeout Reactor |
| Deadline nghiệp vụ xuyên retry | Tổng thời gian cả request, retry, xử lý | Phụ thuộc cách thiết kế pipeline và vị trí operator |

### Timeout cho WebTestClient

`WebTestClient` gắn trực tiếp vào controller/router mock (không cần server):

```java
WebTestClient client = WebTestClient
    .bindToController(new EventController())
    .configureClient()
    .responseTimeout(Duration.ofSeconds(5))
    .build();
```

Đây là cấu hình minh họa; phần 7 có class thật. `responseTimeout` của WebTestClient bảo vệ quá trình đợi phản hồi; với SSE **đã bắt đầu**, dùng `StepVerifier.verify(Duration)` để giới hạn thời gian quan sát event. Đừng coi `responseTimeout(5s)` là hợp đồng “mọi SSE connection phải đóng sau 5s”.

### Timeout trong pipeline Reactor

`timeout` phát lỗi nếu không có tín hiệu kịp deadline:

```java
Mono<String> expensive = Mono.delay(Duration.ofSeconds(10))
    .map(value -> "ready")
    .timeout(Duration.ofSeconds(3));
```

Publisher này dự kiến phát lỗi `TimeoutException` (từ `java.util.concurrent`) thay vì phát "ready". Lưu ý `timeout` **không chỉ là assert của test**: nó là một operator sản phẩm có tác dụng hủy subscription upstream. Với `Flux` có event liên tục, `timeout(Duration.ofSeconds(3))` giám sát khoảng **giữa các event**; luồng cứ đều đặn nhận event thì có thể tồn tại lâu hơn 3s. Muốn deadline **tổng** trong cả giao dịch, phải thiết kế ngân sách thời gian riêng (ví dụ áp giới hạn lên một `Mono` đại diện cho toàn bộ kết quả hoặc dùng `take(Duration)` cho stream).

`take(Duration)` có ý nghĩa khác:

```java
Flux<Long> finite = Flux.interval(Duration.ofSeconds(1))
    .take(Duration.ofSeconds(3));
```

Khi hết window, downstream có thể nhận `onComplete` thay vì lỗi timeout. Thứ tự, số item tại đúng ranh giới thời gian và loại scheduler có thể ảnh hưởng; nếu test cần con số chính xác, hãy dùng virtual time và kiểm soát mốc thời gian.

## 7. Ví dụ đầy đủ Java 17+ với JUnit 5, Spring WebFlux và Reactor Test

Project này **không cần chạy HTTP server thật**. `WebTestClient.bindToController(...)` dùng mock server binding để kiểm tra endpoint WebFlux, decoder SSE/NDJSON, trong khi `StepVerifier.withVirtualTime(...)` kiểm tra cancellation và timeout của publisher mà không phải chờ nhiều giây thực tế.

Cấu trúc:

```text
flux-sse-test/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/demo/
    │   └── StreamController.java
    └── test/java/demo/
        ├── StreamControllerTest.java
        └── PublisherTimingTest.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'flux-sse-test'
```

**`build.gradle`** — yêu cầu JDK 17+, Gradle 8.x:

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation platform('org.springframework.boot:spring-boot-dependencies:3.3.13')
    testImplementation platform('org.springframework.boot:spring-boot-dependencies:3.3.13')

    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}

tasks.test {
    useJUnitPlatform()
}
```

Spring Boot BOM quản lý các phiên bản phụ thuộc tương thích, gồm Reactor. `reactor-test` chứa `StepVerifier`, không phải là thành phần code production cần đưa vào `implementation`. Không yêu cầu plugin Boot vì ví dụ chỉ chạy unit/web-layer tests.

**`src/main/java/demo/StreamController.java`**

```java
package demo;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
public class StreamController {
    private final AtomicInteger canceled = new AtomicInteger();

    public int cancellationCount() {
        return canceled.get();
    }

    @GetMapping(value = "/items", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Item> items() {
        return Flux.just(
            new Item(1, "Notebook"),
            new Item(2, "Pen")
        );
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> events() {
        return Flux.interval(Duration.ofMillis(25))
            .map(index -> ServerSentEvent.<String>builder()
                .id(Long.toString(index))
                .event("tick")
                .data("value-" + index)
                .build())
            .doOnCancel(canceled::incrementAndGet);
    }

    public record Item(int id, String name) {}
}
```

`/items` là **NDJSON hữu hạn**; `/events` là SSE **không kết thúc** cho tới khi downstream cancel hoặc connection đóng. `AtomicInteger` giúp quan sát số lần nguồn bị hủy khi cần; test HTTP không dựa vào giá trị này để tránh race giữa mock exchange và cleanup.

**`src/test/java/demo/StreamControllerTest.java`**

```java
package demo;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamControllerTest {
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(new StreamController())
            .configureClient()
            .responseTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Test
    void finiteNdjsonFluxHasTwoItemsAndCompletes() {
        FluxExchangeResult<StreamController.Item> result = client.get()
            .uri("/items")
            .accept(MediaType.APPLICATION_NDJSON)
            .exchange()
            .expectStatus().isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
            .returnResult(StreamController.Item.class);

        StepVerifier.create(result.getResponseBody())
            .expectNext(
                new StreamController.Item(1, "Notebook"),
                new StreamController.Item(2, "Pen"))
            .verifyComplete();
    }

    @Test
    void sseHasMetadataAndCanBeCanceledAfterTwoEvents() {
        FluxExchangeResult<ServerSentEvent<String>> result = client.get()
            .uri("/events")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(
                new ParameterizedTypeReference<ServerSentEvent<String>>() {});

        StepVerifier.create(result.getResponseBody())
            .assertNext(event -> {
                assertEquals("0", event.id());
                assertEquals("tick", event.event());
                assertEquals("value-0", event.data());
            })
            .assertNext(event -> {
                assertEquals("1", event.id());
                assertEquals("value-1", event.data());
            })
            .thenCancel()
            .verify(Duration.ofSeconds(5));
    }
}
```

**Điểm cần quan sát:** test 1 kết thúc bằng `verifyComplete()` vì hai phần tử là toàn bộ response. Test 2 kết thúc bằng `thenCancel()` vì stream không có `onComplete` tự nhiên. `ParameterizedTypeReference` giữ kiểu `ServerSentEvent<String>` để có thể kiểm tra cả `id`, `event` và `data` sau khi client decode.

**`src/test/java/demo/PublisherTimingTest.java`**

```java
package demo;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublisherTimingTest {

    @Test
    void cancelNotifiesUpstream() {
        AtomicInteger cancelCalls = new AtomicInteger();

        StepVerifier.withVirtualTime(() ->
                Flux.interval(Duration.ofSeconds(1))
                    .doOnCancel(cancelCalls::incrementAndGet))
            .expectSubscription()
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(0L, 1L)
            .thenCancel()
            .verify(Duration.ofSeconds(2));

        assertEquals(1, cancelCalls.get());
    }

    @Test
    void timeoutEmitsErrorAndCancelsUpstream() {
        AtomicInteger cancelCalls = new AtomicInteger();

        StepVerifier.withVirtualTime(() ->
                Mono.delay(Duration.ofSeconds(10))
                    .doOnCancel(cancelCalls::incrementAndGet)
                    .timeout(Duration.ofSeconds(3)))
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(2))
            .thenAwait(Duration.ofSeconds(1))
            .expectError(TimeoutException.class)
            .verify(Duration.ofSeconds(2));

        assertEquals(1, cancelCalls.get());
    }
}
```

Giải thích hai test thời gian:

- `withVirtualTime(Supplier<Publisher>)` thay thế scheduler thời gian của Reactor để kiểm tra các operator như `interval`/`delay`/`timeout` mà không chờ 10 giây thực. **Publisher phải được tạo lười bên trong lambda**; nếu khai báo `Mono.delay(...)` bên ngoài rồi đưa vào, test có thể vẫn dùng scheduler thật.
- `expectSubscription()` xác nhận subscription trước khi gọi `expectNoEvent(...)`. Nếu `expectNoEvent` được đặt ngay đầu mà chưa consume subscription signal, nó có thể fail.
- `thenAwait(...)` tiến đồng hồ ảo. Một `timeout(3s)` được kiểm tra bằng 2s không có event, thêm 1s và kỳ vọng lỗi.
- `verify(Duration.ofSeconds(2))` là **timeout thời gian thực của test**, giúp ngăn test treo nếu kịch bản virtual time bị cấu hình sai. Nó không biến thành operator `timeout` trong production.
- `doOnCancel` trên nguồn đo upstream bị cancel. Cả `thenCancel()` chủ động và `timeout(...)` khi quá hạn đều có thể gửi cancel cho nguồn.
- Các event của SSE qua `WebTestClient` vẫn dùng scheduling/codec thật của mock WebFlux execution; không nên áp virtual time tuỳ tiện vào toàn bộ HTTP test, trừ khi bạn kiểm soát hoàn toàn scheduler và lifecycle.

Chạy trong thư mục project:

```bash
gradle test
```

Khi cả bốn test pass, Gradle kết thúc với:

```text
BUILD SUCCESSFUL
```

Thông tin log và tổng thời gian tùy máy; Gradle sinh báo cáo mặc định tại `build/reports/tests/test/index.html`. Đây là **kết quả kỳ vọng khi chạy**, không phải transcript đã được thu thập. Có thể dùng Gradle Wrapper nếu project có sẵn (`./gradlew test` hoặc `gradlew.bat test`).

## 8. Tách kiểm thử logic thời gian khỏi HTTP I/O và network behavior

`WebTestClient.bindToController(...)` dùng WebFlux mock request/response và chạy controller/codec mà không đi qua TCP socket. Nó là lựa chọn nhanh để kiểm tra status, media type và event decoding. Để kiểm tra **end-to-end streaming thực sự**, có thể dùng:

```java
WebTestClient client = WebTestClient.bindToServer()
    .baseUrl("http://127.0.0.1:8080")
    .responseTimeout(Duration.ofSeconds(5))
    .build();
```

Đây là **snippet minh họa**; phải có server đang chạy trên cổng tương ứng. Với Spring Boot, có thể chạy `@SpringBootTest(webEnvironment = RANDOM_PORT)`, lấy `@LocalServerPort` và gắn base URL động, hoặc dùng `@AutoConfigureWebTestClient` khi phù hợp. Tầng network test hữu ích cho chunk flushing, HTTP proxies, load balancer, TCP disconnect, TLS và behavior của connector.

**Virtual time nên ưu tiên cho business Reactor publishers** dùng `delay`, `interval`, `timeout` và scheduler mặc định. Nó không tự rút ngắn thời gian thực cho:
- Network HTTP, DNS/TLS/connection pool.
- Scheduler đã được khởi tạo riêng và giữ sẵn ngoài lambda.
- Blocking I/O, `Thread.sleep` hoặc external service.
- Request xử lý bằng scheduler không thuộc hệ thống được virtual hóa.

Kiểm tra stream live trong CI cần thời hạn hữu hạn và tốc độ phát event đủ rộng để tránh flaky vì CPU scheduling. Nếu có thể, tách test HTTP “decode được hai event” khỏi test thời gian ở mức publisher virtual time.

## 9. Kiểm thử cancellation thực tế và backpressure nâng cao

Có thể kiểm tra số yêu cầu (`request(n)`) bằng `StepVerifier` khi nguồn hỗ trợ backpressure:

```java
Flux<Integer> demandAware = Flux.range(1, 10);

StepVerifier.create(demandAware, 0)
    .expectSubscription()
    .thenRequest(2)
    .expectNext(1, 2)
    .thenRequest(1)
    .expectNext(3)
    .thenCancel()
    .verify(Duration.ofSeconds(2));
```

Giải thích: `create(source, 0)` khởi tạo demand bằng 0, `thenRequest(2)` yêu cầu hai phần tử, sau đó thêm một. `thenCancel` dừng luồng dù nguồn có thể phát tới 10 giá trị. Điều này kiểm tra **honor demand** của publisher, không đo chính xác số TCP chunks hoặc HTTP packets gửi ra.

Với tài nguyên thật, hãy kết hợp `doFinally` hoặc `usingWhen` với assertion ở **đúng vị trí upstream**:

```java
AtomicReference<SignalType> finalSignal = new AtomicReference<>();

Flux<Long> source = Flux.interval(Duration.ofSeconds(1))
    .doFinally(finalSignal::set);

StepVerifier.withVirtualTime(() -> source)
    .expectSubscription()
    .thenAwait(Duration.ofSeconds(1))
    .expectNext(0L)
    .thenCancel()
    .verify(Duration.ofSeconds(2));
```

**Đoạn này chỉ để minh họa ý tưởng**, và khi kiểm thử virtual time nghiêm túc phải **tạo cả nguồn `Flux.interval` bên trong lambda**, không dùng `source` được tạo bên ngoài như đoạn phác thảo này. Cách đúng:

```java
AtomicReference<SignalType> finalSignal = new AtomicReference<>();

StepVerifier.withVirtualTime(() ->
        Flux.interval(Duration.ofSeconds(1))
            .doFinally(finalSignal::set))
    .expectSubscription()
    .thenAwait(Duration.ofSeconds(1))
    .expectNext(0L)
    .thenCancel()
    .verify(Duration.ofSeconds(2));

assertEquals(SignalType.CANCEL, finalSignal.get());
```

Cần import `AtomicReference` và `reactor.core.publisher.SignalType`. `doFinally` chạy ở mốc terminal/cancel, phù hợp chứng minh cleanup signal. Trong test production có concurrency, đừng kiểm tra ngay một biến cleanup bất đồng bộ mà không có đồng bộ hóa; dùng `CountDownLatch`/`Awaitility` hoặc sink signal để chờ giới hạn.

## 10. Lỗi phổ biến

1. **`expectBodyList` / `collectList` trên SSE vô hạn.** Test chờ mãi hoặc tiêu thụ không kiểm soát. Dùng `returnResult` và `thenCancel` sau vài event.
2. **Gọi `verifyComplete()` sau vài sự kiện của stream vô hạn.** Không có `onComplete` dự kiến; kết thúc bằng `thenCancel().verify(Duration)`.
3. **Không đặt timeout của bài test.** Kịch bản sai có thể treo pipeline hoặc chờ rất lâu; dùng `verify(Duration)` cho stream.
4. **Nhầm `verify(Duration)` với `Publisher.timeout(Duration)`.** Cái đầu bảo vệ test runner; cái sau thay đổi pipeline thực và phát `TimeoutException`.
5. **Dùng `withVirtualTime` nhưng tạo `Flux.interval` trước supplier.** Scheduler đã được capture; test không tăng tốc như mong đợi.
6. **Gọi `expectNoEvent` trước khi tiêu thụ subscription.** Subscription bản thân là sự kiện; cần `expectSubscription()` trước.
7. **Chỉ assert HTTP 200 cho SSE.** Response có thể không chứa event hoặc sai schema; kiểm tra `Content-Type`, payload, event id/type khi cần.
8. **Decode SSE thành String rồi nghĩ đã kiểm tra metadata.** Khi cần verify `id/event/retry`, dùng `ServerSentEvent<T>`.
9. **Cho rằng hủy downstream luôn chứng minh socket đã đóng.** `thenCancel` xác minh reactive subscription; cần integration test network cho connection closure.
10. **Quên rằng cancel không phải onComplete/onError.** Cleanup trong `doOnTerminate` có thể bỏ sót cancellation; dùng `doFinally`/`doOnCancel` khi thích hợp.
11. **Không phân biệt stream timeout và total deadline.** `Flux.timeout(3s)` có thể không fail nếu cứ có event mỗi 2s; hạn tổng cần chính sách khác.
12. **Kiểm tra chính xác thời điểm phát event real-time ở CI.** CPU scheduling và OS jitter tạo flaky; test logic thời gian bằng virtual time.
13. **Dùng virtual time cho HTTP network thật** rồi mong vài phút truyền mạng diễn ra tức khắc; đồng hồ ảo không thay network I/O.
14. **Bỏ qua client disconnect/reconnect ở production.** SSE client cần retry/backoff, last-event-id nếu dùng cursor/replay; server cần cleanup on cancel và xử lý slow consumer.

## 11. Câu hỏi tự kiểm tra

1. **SSE không bao giờ complete: nên dùng `verifyComplete` hay `thenCancel`?** → `thenCancel` khi đã assert đủ event, kèm `verify(Duration)` để có giới hạn.
2. **`Flux.take(2)` hoàn tất downstream có nghĩa upstream nhận gì?** → Thông thường upstream nhận `cancel()` sau khi đủ hai phần tử.
3. **Khi nào cần `returnResult(new ParameterizedTypeReference<ServerSentEvent<T>>() {})`?** → Khi cần decode SSE có generic data và kiểm tra cả id/event/retry metadata.
4. **Vì sao không dùng `expectBodyList` cho stream vô hạn?** → Nó chờ body hoàn tất và có thể giữ dữ liệu vô hạn.
5. **SSE gửi dữ liệu mỗi 2 giây và `timeout(3s)`, stream kéo dài 5 phút có phải lỗi?** → Không nhất thiết; timeout theo khoảng lặng giữa event không phải deadline tổng.
6. **`verify(Duration.ofSeconds(2))` có tự gửi timeout tới application pipeline không?** → Không; đó là giới hạn thời gian xác minh test.
7. **Virtual time có tăng tốc một request HTTPS thực không?** → Không; chỉ hỗ trợ các operator/scheduler Reactor được điều khiển.
8. **Khi subscriber cancel, nên cleanup listener ở đâu?** → Trong resource lifecycle, ví dụ `doFinally(CANCEL)`, `doOnCancel` hoặc `usingWhen`, cùng kiểm thử side effect.
9. **Để kiểm tra request(n) khác cancel thế nào?** → `request(n)` phát nhu cầu; `cancel()` kết thúc subscription. Dùng StepVerifier initialRequest + thenRequest.
10. **Kiểm thử SSE bằng mock WebFlux đã đảm bảo proxy production không buffer chưa?** → Chưa; cần end-to-end streaming test với proxy/HTTP connector thật.

## 12. Tài liệu tham khảo

- Spring Framework — WebTestClient, streaming response assertions: https://docs.spring.io/spring-framework/reference/testing/webtestclient.html
- Spring Framework — WebFlux annotated SSE controllers: https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/return-types.html
- Spring Framework 6.1 Javadoc — WebTestClient.Builder responseTimeout: https://docs.spring.io/spring-framework/docs/6.1.13/javadoc-api/org/springframework/test/web/reactive/server/WebTestClient.Builder.html
- Spring Framework — ServerSentEvent Javadoc: https://docs.spring.io/spring-framework/docs/6.1.13/javadoc-api/org/springframework/http/codec/ServerSentEvent.html
- Reactor Core — Testing with StepVerifier and virtual time: https://projectreactor.io/docs/core/release/reference/testing.html
- Reactor Test API — StepVerifier: https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifier.html
- Reactive Streams Specification — Subscriber demand and cancellation: https://www.reactive-streams.org/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-testing-010">MockRestServiceServer cho client đồng bộ và máy chủ giả lập cho WebClient</a>

<details>
<summary>Xem chi tiết</summary>

# MockRestServiceServer cho client đồng bộ và máy chủ giả lập cho WebClient

## 1. Khi kiểm thử HTTP client cần giả lập điều gì?

Khi ứng dụng Spring gọi một dịch vụ HTTP bên ngoài, unit test của service thường không nên phụ thuộc API thật: mạng có thể mất, dữ liệu có thể thay đổi, authentication hết hạn, rate limit, chi phí và tốc độ sẽ làm test không ổn định. Bài toán là kiểm tra **mã HTTP client của chính ứng dụng** có tạo đúng request và diễn giải đúng response hay không, với một bên server được kiểm soát.

Một hợp đồng mẫu: ứng dụng gọi catalog API:
- **GET /api/books/7?lang=en**, header **X-Tenant: training**, Accept: application/json. Server đáp **200**, body **{"id":7,"title":"Spring"}**.
- **POST /api/books**, Content-Type: application/json, body **{"title":"New Book"}**; server đáp **201**, Location: /api/books/8 và JSON sách mới.
- Khi server đáp **404**, client phải đưa lỗi ra ngoài hoặc ánh xạ theo chính sách nghiệp vụ; test không nên luôn giả lập 200.

Ở đây ta kiểm tra **client gửi đi**. Đừng nhầm với **MockMvc** hoặc **WebTestClient.bindToController(...)**: hai API đó chuyên kiểm thử **server-side controller/handler**. Công cụ trong bài tác động lên phía client đang tạo outbound HTTP request.

### Hai hướng mô phỏng

1. **MockRestServiceServer** của Spring Test: gắn request factory giả vào **RestClient** hoặc **RestTemplate** đồng bộ. Client vẫn đi qua phần tạo URI, headers, serialization/deserialization của Spring, nhưng **không có kết nối HTTP thật**. Test khai báo kỳ vọng bằng **expect(...).andRespond(...)**, gọi code thực tế và **verify()**.
2. **Mock HTTP server chạy trên cổng loopback**: ví dụ **OkHttp MockWebServer** hoặc **WireMock**. Nó lắng nghe HTTP thật tại localhost, trả response được định sẵn và lưu request để test kiểm tra. **WebClient** gọi server giả qua HTTP client/connector thật, phù hợp hơn để thử encoding, timeout, network, connection behavior hoặc streaming.

Spring Framework chính thức khuyến nghị mock web server khi muốn kiểm thử transport/network đầy đủ; **MockRestServiceServer** vẫn hữu ích, nhanh và cô lập tốt cho RestTemplate/RestClient. Chọn công cụ theo điều muốn khẳng định, không dùng giả lập HTTP để thay thế tất cả bài kiểm thử nghiệp vụ.

## 2. So sánh phạm vi, tốc độ và độ tin cậy

| Tiêu chí | MockRestServiceServer | MockWebServer hoặc WireMock |
| --- | --- | --- |
| Đối tượng thường kiểm thử | RestClient, RestTemplate (đồng bộ) | WebClient, RestClient, RestTemplate, HTTP service proxy |
| HTTP socket thật | Không | Có: server giả mở cổng localhost |
| Client HTTP connector thật | Bị thay bằng mock request factory cho test | Có thể dùng giống cấu hình production |
| Khai báo hành vi | **expect** + **andRespond** | Enqueue/dispatcher/stub HTTP response |
| Kiểm tra request | Spring request matchers, số lượng và verify() | RecordedRequest / verification API của mock server |
| Kiểm thử serialization + headers | Có | Có |
| Kiểm thử connect/response timeout, transfer encoding | Không đầy đủ | Phù hợp hơn; phụ thuộc connector và fixture |
| Tài nguyên cần dọn | Expectation state giữa các test | Phải đóng server/port và request queue |
| Trường hợp lý tưởng | Test logic gọi đồng bộ nhanh, có tính quyết định | Test WebClient reactive và hành vi network |
| Giới hạn chung | Không xác minh API thật ngoài Internet | Vẫn không chứng minh API thật dùng đúng schema và deployment |

**Server giả** không đồng nghĩa **mock interface Java**. Khi mock một repository/client interface bằng Mockito, ta không biết URL, query, HTTP method, Content-Type có được mã hóa đúng không. Trong bài này, nên gọi **mã dùng RestClient/WebClient thật** để quan sát outbound HTTP contract.

## 3. MockRestServiceServer: gắn vào RestClient.Builder đúng thứ tự

Từ Spring Framework 6.1, **MockRestServiceServer.bindTo(RestClient.Builder)** hỗ trợ RestClient. Ví dụ:

~~~java
RestClient.Builder builder = RestClient.builder()
    .baseUrl("https://catalog.example.test");

MockRestServiceServer server =
    MockRestServiceServer.bindTo(builder).build();

RestClient client = builder.build();
~~~

**Điểm rất quan trọng:** dùng **cùng RestClient.Builder** để tạo mock server rồi mới **builder.build()**. Nếu gọi **build()** trước hoặc service tạo một builder/client độc lập, mock request factory có thể không nằm trên client mà code đang sử dụng. Địa chỉ **catalog.example.test** chỉ là host ví dụ, không hề được truy cập qua network trong bài test dùng MockRestServiceServer khi gắn đúng.

Một test hoàn chỉnh về ý tưởng:

~~~java
server.expect(requestTo(
        "https://catalog.example.test/api/books/7?lang=en"))
    .andExpect(method(HttpMethod.GET))
    .andExpect(header("X-Tenant", "training"))
    .andRespond(withSuccess(
        "{\"id\":7,\"title\":\"Spring\"}",
        MediaType.APPLICATION_JSON));

Book result = client.get()
    .uri("/api/books/{id}?lang={lang}", 7, "en")
    .header("X-Tenant", "training")
    .retrieve()
    .body(Book.class);

assertEquals(new Book(7, "Spring"), result);
server.verify();
~~~

Mẫu này cần static imports **MockRestRequestMatchers.requestTo/method/header**, **MockRestResponseCreators.withSuccess**, **Assertions.assertEquals** và các kiểu Spring liên quan.

### Quy trình nội bộ

~~~text
JUnit test
   |
   | đăng ký expect(requestTo(...)), andExpect(...), andRespond(...)
   v
MockRestServiceServer
   |
   | bindTo(RestClient.Builder), thay ClientHttpRequestFactory
   v
Mã ứng dụng gọi RestClient thật ở mức API
   |
   | build URI + headers + JSON encoding
   v
Mock ClientHttpRequestFactory
   |
   | so request với expectations, trả stub response
   v
RestClient decode response JSON thành DTO
   |
   v
JUnit assert DTO/exception + mockServer.verify()
~~~

Không có TCP/HTTP server thật trong dòng này. Vì vậy nếu muốn kiểm tra xử lý TLS, proxy, connect timeout hoặc sự khác nhau giữa HTTP clients (Reactor Netty, JDK, Apache), phải có test dùng mock web server hoặc môi trường tích hợp.

## 4. Khai báo expectation: URL, method, headers và body

Một expectation MockRestServiceServer thường gồm:
- **requestTo(String/URI)** hoặc matcher request theo logic tùy biến.
- **method(HttpMethod.GET/POST/PUT...)**.
- **header(...)**, **headerDoesNotExist(...)**, **queryParam(...)** (tùy phiên bản Spring), **content().contentType(...)**, **content().json(...)**, **content().string(...)**.
- **andRespond(withSuccess(...))**, **withStatus(HttpStatus...)**, **withServerError()** và response creator khác.

Ví dụ POST JSON:

~~~java
server.expect(requestTo(
        "https://catalog.example.test/api/books"))
    .andExpect(method(HttpMethod.POST))
    .andExpect(header("X-Tenant", "training"))
    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    .andExpect(content().json("{\"title\":\"New Book\"}"))
    .andRespond(withStatus(HttpStatus.CREATED)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Location", "/api/books/8")
        .body("{\"id\":8,\"title\":\"New Book\"}"));
~~~

**Request header khác response header**:
- **.andExpect(header("X-Tenant","training"))** kiểm tra header mà **client gửi**.
- **.andRespond(... .header("Location", ...))** đặt header mà **server giả trả về**.
- Nếu code cần đọc **Location**, phải có logic đọc headers, ví dụ dùng **toEntity(Book.class)**, thay vì chỉ **body(Book.class)**.

**content().json(...)** so khớp JSON theo cấu trúc, tránh test fail chỉ vì thứ tự field hoặc whitespace. Với contract chặt, test thêm điều kiện về trường ngoài dự kiến/kiểu dữ liệu, hoặc dùng matcher strict nếu phiên bản Spring/Test dependency hỗ trợ.

### Kiểm tra số lần gọi và thứ tự

Mặc định **MockRestServiceServer** kỳ vọng các request theo thứ tự đăng ký (với một số ngoại lệ khi một expectation cho phép nhiều lần). Có thể chỉ định số lần:

~~~java
server.expect(ExpectedCount.times(2),
        requestTo("https://catalog.example.test/api/books/7?lang=en"))
    .andRespond(withSuccess(
        "{\"id\":7,\"title\":\"Spring\"}",
        MediaType.APPLICATION_JSON));
~~~

Khi thử retry hãy xác định đúng số request kỳ vọng. Nếu application gọi một lần nhưng kỳ vọng hai lần, **server.verify()** thất bại. Khi test các request độc lập thực sự không có thứ tự bắt buộc, builder hỗ trợ **ignoreExpectOrder(true)**; vẫn nên chứng minh không có request thừa/thiếu.

Nếu nhiều expectation dùng chung URL, headers/body cần phân biệt đủ rõ. Đừng dùng một matcher quá rộng (chỉ kiểm host) khiến test pass dù endpoint sai.

### Trả lỗi 404, 429, 500

~~~java
server.expect(requestTo(
        "https://catalog.example.test/api/books/404?lang=vi"))
    .andRespond(withStatus(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"message\":\"Not found\"}"));
~~~

Với **RestClient.retrieve()**, response 4xx/5xx mặc định có thể thành **RestClientResponseException** (ví dụ **HttpClientErrorException.NotFound**). Cách assert:

~~~java
assertThrows(HttpClientErrorException.NotFound.class,
    () -> catalog.find(404, "vi"));
server.verify();
~~~

Để kiểm tra retry/bulkhead/circuit breaker, phải cấu hình thật ở client/service dưới test. MockRestServiceServer không tự retry và không tự biến lỗi 503 thành 200. Không nên lạm dụng mock phản hồi thành công nếu mục tiêu kiểm thử xử lý lỗi.

## 5. Vì sao WebClient nên dùng mock web server?

**WebClient** làm việc qua API reactive **Mono/Flux** và HTTP connector, ví dụ Reactor Netty. **MockRestServiceServer.bindTo(RestClient.Builder)** không móc trực tiếp vào WebClient như RestClient. Có thể thay ExchangeFunction của WebClient bằng mock thủ công, nhưng điều đó bỏ qua phần transport và có nguy cơ test chính mock thay vì request thực.

Một **mock HTTP server** nhận yêu cầu TCP/HTTP thực từ WebClient tại localhost; nó cho phép kiểm tra những phần sau:

1. URL/path/query encoding do WebClient tạo ra.
2. HTTP verb, headers, Content-Type, JSON request bytes.
3. Mã trạng thái và JSON response được WebClient codec giải mã.
4. Timeout, connection reset, chunked transfer hoặc delayed response nếu fixture được cấu hình.
5. Dạng stream NDJSON/SSE theo từng frame/chunk với giới hạn/thời gian có kiểm soát.

Spring Framework khuyến nghị **OkHttp MockWebServer** hoặc **WireMock** cho kiểm thử WebClient. Đừng nhầm **WebTestClient.bindToController**: công cụ đó dựng mock server framework để kiểm thử *code phía server*; nếu muốn kiểm thử một WebClient service như production, hãy tạo WebClient thật trỏ tới localhost của mock HTTP server.

## 6. MockWebServer: enqueue(response) và takeRequest()

**OkHttp MockWebServer 4.12.0** có API Java:

~~~java
MockWebServer server = new MockWebServer();
server.start();

try {
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody("{\"id\":7,\"title\":\"Spring\"}"));

    WebClient client = WebClient.builder()
        .baseUrl(server.url("/").toString())
        .build();

    Book book = client.get()
        .uri("/api/books/{id}?lang={lang}", 7, "en")
        .header("X-Tenant", "training")
        .retrieve()
        .bodyToMono(Book.class)
        .block(Duration.ofSeconds(5));

    RecordedRequest request =
        server.takeRequest(5, TimeUnit.SECONDS);

    assertNotNull(request);
    assertEquals("GET", request.getMethod());
    assertEquals("/api/books/7?lang=en", request.getPath());
    assertEquals("training", request.getHeader("X-Tenant"));
    assertEquals(new Book(7, "Spring"), book);
} finally {
    server.shutdown();
}
~~~

Các import gồm:
- **okhttp3.mockwebserver.MockWebServer**, **MockResponse**, **RecordedRequest**.
- **org.springframework.web.reactive.function.client.WebClient**.
- **java.time.Duration**, **java.util.concurrent.TimeUnit** và JUnit assertions.

**enqueue** định thứ tự các response stub sẽ được trả. **takeRequest** lấy và loại bỏ một recorded request từ queue; dùng bản có timeout tránh chờ vô hạn khi WebClient không gửi gì do quên subscribe. **server.url("/")** tạo URL chứa cổng localhost ngẫu nhiên đã cấp, tránh xung đột cổng cố định.

### Một điểm dễ sai: Mono chỉ chạy khi subscribe

~~~java
Mono<Book> pending = client.get()
    .uri("/api/books/7")
    .retrieve()
    .bodyToMono(Book.class);

// Chỉ tạo pipeline, chưa chắc đã có HTTP request.
~~~

Nếu gọi **takeRequest()** ngay sau đó mà không subscribe, test có thể treo/timeout vì request chưa hề gửi. Trong một test đồng bộ, dùng **block(Duration.ofSeconds(5))** tại ranh giới test hoặc **StepVerifier** khi thêm **reactor-test**:

~~~java
StepVerifier.create(pending)
    .expectNextMatches(book -> book.id() == 7)
    .verifyComplete();
~~~

StepVerifier chỉ là fragment khi đã có dependency và import **reactor.test.StepVerifier**. Với stream vô hạn, dùng take(n), thời hạn xác minh và cleanup subscription, không gọi collectList() một cách vô hạn.

### MockWebServer khác WebClient thực về cái gì?

MockWebServer đóng vai *remote HTTP server*. **WebClient vẫn dùng HTTP connector thật** và thực hiện HTTP call, codecs và lifecycle. Tuy vậy:
- Remote API production có thể yêu cầu TLS, OAuth, proxy và khác nội dung JSON.
- Fixture test chỉ đúng bằng response mà tác giả tạo.
- Localhost ít latency hơn Internet. Timeout test cần mô phỏng chậm thật có kiểm soát thay vì kỳ vọng network tự chậm.

## 7. Dự án Java 17 có thể chạy: RestClient + MockRestServiceServer, WebClient + MockWebServer

Ví dụ này dùng **Spring Boot 3.5.0** (Spring Framework 6.2), **JDK 17+**, JUnit Jupiter, Spring Test và **MockWebServer 4.12.0**. Toàn bộ test tự tạo clients và mocks, không cần chạy Spring Boot server hay có Internet ở runtime (Maven vẫn cần tải dependencies lần đầu).

Cấu trúc:

~~~text
client-http-contract-tests/
├── pom.xml
├── src/main/java/demo/
│   ├── Book.java
│   ├── CreateBook.java
│   ├── CatalogRestClient.java
│   └── CatalogWebClient.java
└── src/test/java/demo/
    ├── CatalogRestClientTest.java
    └── CatalogWebClientTest.java
~~~

**pom.xml**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>client-http-contract-tests</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>mockwebserver</artifactId>
            <version>4.12.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
~~~

**src/main/java/demo/Book.java**:

~~~java
package demo;

public record Book(long id, String title) {}
~~~

**src/main/java/demo/CreateBook.java**:

~~~java
package demo;

public record CreateBook(String title) {}
~~~

**src/main/java/demo/CatalogRestClient.java**:

~~~java
package demo;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public final class CatalogRestClient {
    private final RestClient client;

    public CatalogRestClient(RestClient client) {
        this.client = client;
    }

    public Book find(long id, String lang) {
        return client.get()
            .uri("/api/books/{id}?lang={lang}", id, lang)
            .header("X-Tenant", "training")
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(Book.class);
    }

    public Book create(CreateBook body) {
        return client.post()
            .uri("/api/books")
            .header("X-Tenant", "training")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Book.class);
    }
}
~~~

**src/main/java/demo/CatalogWebClient.java**:

~~~java
package demo;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public final class CatalogWebClient {
    private final WebClient client;

    public CatalogWebClient(WebClient client) {
        this.client = client;
    }

    public Mono<Book> find(long id, String lang) {
        return client.get()
            .uri("/api/books/{id}?lang={lang}", id, lang)
            .header("X-Tenant", "training")
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(Book.class);
    }

    public Mono<Book> create(CreateBook body) {
        return client.post()
            .uri("/api/books")
            .header("X-Tenant", "training")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Book.class);
    }
}
~~~

Hai class này dùng DTO giống nhau nhưng khác semantics:
- **CatalogRestClient.find** đồng bộ, chờ response và trả Book.
- **CatalogWebClient.find** trả **Mono<Book>**; HTTP được thực thi khi có subscription.
- **create** gửi JSON nhờ converter/codec từ dependency Jackson do Spring WebFlux starter cung cấp.
- Lỗi HTTP 4xx/5xx của **retrieve()** mặc định thành exception/error signal, test phải xác minh đường lỗi.

### 7.1 Test đồng bộ bằng MockRestServiceServer

**src/test/java/demo/CatalogRestClientTest.java**:

~~~java
package demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CatalogRestClientTest {

    private MockRestServiceServer server;
    private CatalogRestClient catalog;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://catalog.example.test");
        server = MockRestServiceServer.bindTo(builder).build();
        catalog = new CatalogRestClient(builder.build());
    }

    @Test
    void getAssertsMethodUrlHeaderAndParsesJson() {
        server.expect(requestTo(
                "https://catalog.example.test/api/books/7?lang=en"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Tenant", "training"))
            .andRespond(withSuccess(
                "{\"id\":7,\"title\":\"Spring\"}",
                MediaType.APPLICATION_JSON));

        Book book = catalog.find(7, "en");

        assertEquals(new Book(7, "Spring"), book);
        server.verify();
    }

    @Test
    void postAssertsJsonBodyAndParsesCreatedResponse() {
        server.expect(requestTo(
                "https://catalog.example.test/api/books"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Tenant", "training"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"title\":\"New Book\"}"))
            .andRespond(withStatus(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Location", "/api/books/8")
                .body("{\"id\":8,\"title\":\"New Book\"}"));

        Book created = catalog.create(new CreateBook("New Book"));

        assertEquals(new Book(8, "New Book"), created);
        server.verify();
    }

    @Test
    void notFoundPropagatesHttp404() {
        server.expect(requestTo(
                "https://catalog.example.test/api/books/404?lang=vi"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> catalog.find(404, "vi"));
        server.verify();
    }
}
~~~

Đặc điểm:
- **@BeforeEach** tạo builder và mock server mới cho mỗi test, tránh expectation cũ lọt sang test tiếp theo.
- Bắt buộc **MockRestServiceServer.bindTo(builder)** *trước* **builder.build()** để client dùng mock request factory.
- **expect** assert request outbound, **andRespond** giả response inbound, **assertEquals** assert dữ liệu client giải mã, **verify()** xác nhận expectations được đáp ứng.
- Test POST có Location response nhưng **CatalogRestClient.create** hiện chỉ trả body; test mới không xác minh client có đọc Location. Nếu business thực sự dùng header này, thay implementation bằng **toEntity(Book.class)** hoặc loại phản hồi có headers và assert tương ứng.
- Test 404 kiểm tra đúng exception subtype trong cấu hình Spring ví dụ; custom status handler có thể đổi exception thành loại nghiệp vụ khác.

### 7.2 Test WebClient bằng MockWebServer thật trên loopback

**src/test/java/demo/CatalogWebClientTest.java**:

~~~java
package demo;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.junit.jupiter.api.Assertions.*;

class CatalogWebClientTest {

    private MockWebServer server;
    private CatalogWebClient catalog;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        WebClient client = WebClient.builder()
            .baseUrl(server.url("/").toString())
            .build();

        catalog = new CatalogWebClient(client);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void getUsesHttpTransportAndParsesJson() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"id\":7,\"title\":\"Spring\"}"));

        Book book = catalog.find(7, "en")
            .block(Duration.ofSeconds(5));

        assertEquals(new Book(7, "Spring"), book);

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("GET", req.getMethod());
        assertEquals("/api/books/7?lang=en", req.getPath());
        assertEquals("training", req.getHeader("X-Tenant"));
    }

    @Test
    void postEncodesJsonRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(201)
            .addHeader("Content-Type", "application/json")
            .addHeader("Location", "/api/books/8")
            .setBody("{\"id\":8,\"title\":\"New Book\"}"));

        Book book = catalog.create(new CreateBook("New Book"))
            .block(Duration.ofSeconds(5));

        assertEquals(new Book(8, "New Book"), book);

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("POST", req.getMethod());
        assertEquals("/api/books", req.getPath());
        assertEquals("training", req.getHeader("X-Tenant"));
        assertTrue(req.getHeader("Content-Type")
            .startsWith("application/json"));
        assertEquals("{\"title\":\"New Book\"}",
            req.getBody().readUtf8());
    }

    @Test
    void notFoundProducesReactiveError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThrows(
            WebClientResponseException.NotFound.class,
            () -> catalog.find(404, "vi")
                .block(Duration.ofSeconds(5)));

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("/api/books/404?lang=vi", req.getPath());
    }
}
~~~

**Lưu ý test này đang kiểm tra đúng HTTP request thực:**
- **server.start()** cấp cổng localhost ngẫu nhiên.
- **baseUrl(server.url("/").toString())** bảo đảm WebClient gửi đúng vào server giả, không gọi catalog.example.test.
- **server.enqueue** chuẩn bị response cho lần gọi tương ứng.
- **Mono.block(Duration)** subscribe và chờ kết quả trong *JUnit test thread*, không phải WebFlux event-loop.
- **takeRequest(5, TimeUnit.SECONDS)** tránh đợi vô hạn khi không có request.
- **getMethod, getPath, getHeader, getBody().readUtf8()** là dữ liệu HTTP server giả ghi lại sau khi thật sự nhận request.
- **@AfterEach** đóng socket của mock server, tránh rò rỉ cổng/thread.
- Với HTTP POST, kiểm soát cả **body request** lẫn **body response**: một test chỉ assert Book trả về có thể vẫn bỏ sót bug client gửi title sai.

Mã JUnit kiểm tra JSON body POST dưới dạng chuỗi chính xác vì DTO trong demo có một field ổn định và Jackson tạo JSON đơn giản. Với payload có nhiều field có thể thay đổi thứ tự, nên parse JSON bằng ObjectMapper/JsonNode rồi so sánh cấu trúc thay vì string raw.

### 7.3 Cách chạy và kết quả kỳ vọng

Trong thư mục **client-http-contract-tests**, cài JDK 17+ và Maven rồi chạy:

~~~bash
mvn test
~~~

Dự án có **6 phương thức @Test**, gồm ba test RestClient và ba test WebClient. Khi dependencies, JDK và network loopback khả dụng, test phải hoàn thành với **0 failures** và **0 errors**, Maven **BUILD SUCCESS**. Đây là tiêu chí kỳ vọng của ví dụ; không giả định đã chạy Maven trong bất kỳ môi trường nào chỉ vì code xuất hiện trong README.

Nếu test fail:
- Xem stacktrace JUnit xác định assertion/HTTP call đầu tiên thất bại.
- Với MockRestServiceServer, kiểm tra client có được xây dựng từ đúng RestClient.Builder đã bind không.
- Với MockWebServer, kiểm tra URL localhost, enqueue trước khi subscribe, timeout lấy RecordedRequest và response Content-Type.
- Kiểm tra Maven dependency và version Spring nếu phương thức API không có mặt.

## 8. Kiểm thử lỗi, timeout và retry một cách có kiểm soát

### 8.1 Error response từ server giả

RestClient test có thể dùng:

~~~java
server.expect(requestTo(
        "https://catalog.example.test/api/books/7?lang=en"))
    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"error\":\"temporarily unavailable\"}"));
~~~

WebClient test có thể dùng:

~~~java
server.enqueue(new MockResponse()
    .setResponseCode(503)
    .addHeader("Content-Type", "application/json")
    .setBody("{\"error\":\"temporarily unavailable\"}"));
~~~

Kỳ vọng mặc định là lỗi HTTP đi vào đường exception/error signal. Nếu application có logic retry **503**, test nên enqueue chuỗi đáp ứng (503 rồi 200), assert số lần gọi, và dùng policy retry có giới hạn. Không thiết kế test chạy retry vô tận.

### 8.2 Network timeout và phản hồi chậm

MockRestServiceServer thay transport bằng mock request factory, nên **không phù hợp để khẳng định TCP connect timeout hoặc read timeout thật**. MockWebServer có thể mô phỏng phản hồi chậm:

~~~java
server.enqueue(new MockResponse()
    .setResponseCode(200)
    .setBody("{\"id\":7,\"title\":\"Spring\"}")
    .setBodyDelay(2, TimeUnit.SECONDS)
    .addHeader("Content-Type", "application/json"));
~~~

Đây là fragment để minh họa **setBodyDelay**; để test xác định, phải cấu hình timeout trên **WebClient HTTP connector hoặc pipeline Reactor** ở mức nhỏ hơn delay, rồi assert đúng error type. Nếu không có timeout ở ứng dụng, server chậm hai giây vẫn có thể trả thành công. Tránh dùng **Thread.sleep()** chỉ để đoán network conditions; mock server đã có khả năng điều khiển độ trễ.

Một ví dụ WebClient pipeline với application timeout:

~~~java
Mono<Book> result = catalog.find(7, "en")
    .timeout(Duration.ofMillis(200));
~~~

Lúc subscribe có thể xuất hiện **TimeoutException** (được Reactor bọc vào hệ thống error signal) khi không có giá trị trong 200 ms. Nhưng thời gian ngắn có thể flaky trên CI chậm; dùng margin rõ ràng và timeout ở network connector khi muốn test đúng tầng. Cần huỷ tác vụ và cleanup server sau test.

### 8.3 Retry và idempotency

Nếu retry là một phần hợp đồng, ví dụ GET idempotent:

~~~java
Mono<Book> result = catalog.find(7, "en")
    .retryWhen(
        Retry.backoff(1, Duration.ofMillis(100))
            .filter(error ->
                error instanceof WebClientResponseException.ServiceUnavailable));
~~~

Fragment này cần import **reactor.util.retry.Retry**, **java.time.Duration**, và loại WebClientResponseException. Khi dùng MockWebServer, enqueue **503** và **200** theo thứ tự rồi assert có đúng **hai RecordedRequest**. Với RestClient, retry thường ở service layer, Resilience4j hoặc HTTP client policy; MockRestServiceServer có **ExpectedCount.times(2)** để kiểm số request nếu ứng dụng thật sự retry.

Không retry POST tạo tài nguyên vô điều kiện: remote server có thể đã tạo resource nhưng client mất kết nối trước khi nhận response. Cần idempotency key hoặc cơ chế chống trùng trước khi thử retry.

## 9. Kiểm chứng JSON và headers ở cả hai phía

Có bốn lớp assertions không nên trộn lẫn:

| Lớp | Khi dùng MockRestServiceServer | Khi dùng MockWebServer |
| --- | --- | --- |
| Request method + URI | method(...), requestTo(...) | RecordedRequest.getMethod(), getPath() |
| Request headers | header(...), content().contentType(...) | RecordedRequest.getHeader(...) |
| Request JSON body | content().json(expected) | RecordedRequest.getBody() và JSON parser |
| Response status/headers/body | andRespond(...) tạo stub; client assert kết quả/exception | enqueue(...) tạo stub; client assert kết quả/exception |

**Nhớ chiều dữ liệu**:
- **Expect/RecordedRequest** là những gì client **gửi đến server**.
- **Stub response** là những gì server giả **trả về client**.
- **Assert kết quả Java** chứng minh code client đã giải mã response/hành xử theo status.

Ví dụ test status 201 và Location chỉ thực sự bảo vệ việc client đọc Location nếu client trả cả **ResponseEntity<T>** hay expose response header. Nếu implementation chỉ **body(T.class)**, test có thể xác nhận HTTP POST body và DTO nhưng **không thể** khẳng định Location đã được ứng dụng sử dụng.

### JSON có field động

Với response chứa timestamp, request ID hoặc array không bảo đảm thứ tự:
- Assert những trường bắt buộc, kiểu và constraints có ý nghĩa.
- Tránh hard-code timestamp thực, random UUID hoặc request ID thay đổi.
- Với request body, sử dụng **ObjectMapper.readTree(...)** để so sánh JSON structural nếu thứ tự keys không quan trọng.
- Với binary/multipart, so sánh Content-Type, part names, boundary và bytes theo hợp đồng; đừng chuyển file tùy tiện thành String UTF-8.

## 10. Nhiều request, concurrency và isolation

### MockRestServiceServer

- **Một mock server/RestClient.Builder riêng cho mỗi test** giúp tránh expectation state bị tái sử dụng.
- **server.verify()** phải được gọi để xác nhận đủ số lần request, kể cả khi kết quả Java đã đúng.
- Mặc định test sắp xếp expectations theo thứ tự; nếu concurrency làm thứ tự không có contract, dùng **ignoreExpectOrder(true)** hoặc test cô lập từng luồng.
- **ExpectedCount** xác định số lần request, nhưng không dùng nó để thay thế việc kiểm tra headers và body mỗi loại request.

### MockWebServer

- **Một instance per test** (hoặc per test class với cleanup nghiêm ngặt), đóng socket bằng **shutdown()**.
- Enqueue responses theo thứ tự; test gọi song song nhiều request có thể tiêu thụ response queue theo thứ tự khác với dự đoán. Khi cần routing theo path/header, dùng **Dispatcher** tùy biến hoặc WireMock stubs.
- Tránh giữ request cũ trong queue sang test mới. Luôn **takeRequest** với timeout hữu hạn.
- Một test dùng HTTP server giả vẫn có race/timing nếu thiếu subscription hoặc chọn timeout quá thấp. Cần quản lý publisher lifecycle rõ ràng.
- Nếu test nhiều network failures, WebClient có thể retry hoặc connector tự reconnect tùy cấu hình: xác minh **số request thật** để phân biệt logic bị gọi dư.

### Đặt server giả vào base URL thế nào?

Một anti-pattern thường gặp:

~~~java
WebClient client = WebClient.create("https://api.company.com");
// Sau đó mới khởi tạo MockWebServer ở localhost.
// WebClient này vẫn gọi api.company.com, không tự chuyển hướng.
~~~

Cách đúng: tạo mock server trước, lấy URL của nó và truyền vào **WebClient.Builder.baseUrl(...)**. Trong Spring Boot project thật, inject base URL qua **@ConfigurationProperties** để cả production và test sử dụng cùng factory, test chỉ thay giá trị property.

Không buộc test lệ thuộc port 8080 hoặc chạy external service. Một port localhost ngẫu nhiên cho mỗi test giúp CI ổn định và cho phép nhiều test suites chạy song song.

## 11. Mock HTTP server với WireMock

**WireMock** cũng tạo HTTP mock server và mạnh về matching request, scenario/state, mapping theo query/header/JSON, fault simulation và recording. Hữu ích khi:
- Cần nhiều endpoint/stubs và HTTP body phức tạp.
- Mock API theo tiêu chí path/query/auth rất chi tiết.
- Muốn quản lý fixtures JSON riêng, scenario 429 → 200, hoặc tích hợp vào các test suites.
- Test RestClient và WebClient với cùng một HTTP stub server, thay vì dùng MockRestServiceServer chỉ cho RestClient.

Ví dụ minh họa WireMock style:

~~~java
stubFor(get(urlPathEqualTo("/api/books/7"))
    .withQueryParam("lang", equalTo("en"))
    .withHeader("X-Tenant", equalTo("training"))
    .willReturn(okJson("{\"id\":7,\"title\":\"Spring\"}")));
~~~

Đây là **fragment**, cần dựng WireMockServer, cấu hình base URL của WebClient trỏ tới server đó, import DSL đúng phiên bản WireMock và dọn tài nguyên khi xong. Không copy nguyên fragment vào demo MockWebServer ở mục 7 vì đó là hai thư viện độc lập. Khi test cần verification:

~~~java
verify(getRequestedFor(urlPathEqualTo("/api/books/7"))
    .withQueryParam("lang", equalTo("en")));
~~~

WireMock giả lập HTTP response thật ở localhost nên có đặc tính transport tương tự hướng MockWebServer. Chọn một công cụ mock server chính cho một test suite để tránh hai nguồn fixture chồng chéo.

## 12. HTTP service interface cũng kiểm thử được

Nếu ứng dụng Spring dùng declarative **@HttpExchange** interface tạo bằng **HttpServiceProxyFactory**, vẫn nên kiểm tra HTTP contract:

~~~java
@HttpExchange(url = "/api/books")
interface BookApi {
    @GetExchange("/{id}")
    Book find(@PathVariable("id") long id);
}
~~~

Với sync interface:
1. Tạo **RestClient.Builder** và bind **MockRestServiceServer** trước khi build RestClient.
2. Tạo **RestClientAdapter.create(restClient)**, **HttpServiceProxyFactory.builderFor(adapter)** và proxy **createClient(BookApi.class)**.
3. Đăng ký expectations, gọi proxy, assert Book và **verify()**.

Với reactive interface trả **Mono<Book>**:
1. Tạo **MockWebServer** hoặc WireMock.
2. Tạo **WebClient** với base URL của server giả và **WebClientAdapter.create(webClient)**.
3. Tạo proxy cho interface reactive, subscribe/StepVerifier, kiểm **RecordedRequest** và giá trị response.

Như vậy test vẫn xác nhận **annotation mapping, URI template, headers và JSON** do proxy sinh ra. Mock trực tiếp BookApi bằng Mockito chỉ kiểm tra business logic gọi interface, chưa xác minh annotation/proxy transport.

## 13. Các lỗi thường gặp và cách khắc phục

| Dấu hiệu | Nguyên nhân | Cách kiểm tra |
| --- | --- | --- |
| MockRestServiceServer không nhận được request | RestClient được build trước khi bind hoặc từ builder khác | Bind đúng RestClient.Builder trước build |
| Test chạm Internet dù tưởng đã mock | URL/client chưa trỏ mock server đúng | In/kiểm baseUrl, xác nhận RecordedRequest |
| **verify()** báo expected but not executed | Không gọi code thực sự hoặc số lần kỳ vọng sai | Gọi service, assert số request |
| Test POST fail JSON dù semantics đúng | So raw JSON string và thứ tự fields/whitespace khác | Dùng content().json hoặc parse JsonNode |
| MockWebServer **takeRequest** timeout | Mono không subscribe, URL sai hoặc connector lỗi | block/StepVerifier, server.url, kiểm exception |
| WebClient nhận Codec/JSON decode error | Response thiếu Content-Type JSON hoặc schema sai | Thêm header đúng, tạo body phù hợp DTO |
| HTTP 404 bị xem như giá trị null | Mặc định retrieve() phát lỗi status | Test exception/error signal và custom handler nếu có |
| Test timeout không bao giờ xảy ra | Chỉ delay server nhưng không đặt timeout client | Đặt timeout ở connector hoặc Reactor phù hợp |
| Test timeout flaky | Timeout quá ngắn, CI chậm, test dùng sleep | Dùng mock delay có margin và bounded wait |
| Test nhận sai response giả | Nhiều concurrent requests tiêu thụ enqueue khác thứ tự | Dùng Dispatcher/WireMock matching theo URI |
| HTTP request dư sau retry | Client có retry hoặc logic bị gọi nhiều lần | Verify call counts, inspect recorded requests |
| Không đọc được response Location | Implementation chỉ bodyToMono/body(...), bỏ headers | Dùng toEntity/ResponseEntity |
| Mất kết nối sau test | Không gọi server.shutdown | Dùng @AfterEach/finally |
| Test nhanh pass nhưng production vẫn fail TLS | MockRestServiceServer không có transport; localhost khác prod | Thêm kiểm thử integration/cấu hình TLS khi cần |

## 14. Checklist triển khai trong dự án

1. Xác định điều đang kiểm: logic client, URI/headers/JSON, status handling hay network conditions.
2. Với **RestClient/RestTemplate đồng bộ**, ưu tiên **MockRestServiceServer** cho HTTP contract test nhanh, xác định.
3. Với **WebClient**, dùng **MockWebServer/WireMock** để chạy HTTP transport thật ở localhost.
4. Cấu hình cùng **RestClient.Builder** trước khi build; WebClient phải lấy base URL từ URL động của mock server.
5. Khai báo expectation/stub response trước khi gọi service; với WebClient phải có subscription.
6. Kiểm tra đầy đủ request method, URL/query, header, content-type và body, rồi assert response DTO/status/error.
7. Verify số lần gọi, retry, headers và cleanup tài nguyên. Đừng chỉ assert kết quả Java.
8. Tạo test 2xx, 4xx, 5xx, malformed JSON và network delay theo chính sách nghiệp vụ.
9. Tách HTTP mock test khỏi end-to-end environment test; mock server không thể chứng minh remote API production luôn tuân hợp đồng.

### Câu hỏi ôn tập

- Tại sao MockRestServiceServer.bindTo(RestClient.Builder) phải được gọi trước khi builder.build()?
- Vì sao test WebClient có thể timeout ở takeRequest khi ta đã tạo Mono nhưng chưa subscribe?
- MockRestServiceServer khác MockWebServer về lớp transport như thế nào?
- Nếu POST trả JSON chính xác nhưng Location sai, test hiện tại có bắt được lỗi không? Vì sao?
- Làm sao kiểm tra WebClient retry một GET sau 503 mà không cho retry vô hạn?
- Khi nhiều request song song tới MockWebServer, vì sao enqueue FIFO có thể thành source of flakiness?
- Nếu mock một HTTP service interface bằng Mockito, những phần nào của @HttpExchange chưa được kiểm thử?

## Tài liệu chính thức

- [Spring Framework – Testing Client Applications](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html)
- [Spring Framework – Testing WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-testing.html)
- [MockRestServiceServer Javadoc 6.2](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/web/client/MockRestServiceServer.html)
- [RestClient (Spring Framework)](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [WebClient (Spring Framework)](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
- [OkHttp MockWebServer 4.12.0 (Maven Central)](https://central.sonatype.com/artifact/com.squareup.okhttp3/mockwebserver/4.12.0)
- [WireMock Documentation](https://wiremock.org/docs/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-testing-011">Kiểm thử HTTP Service Client và WebSocket/STOMP theo hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử HTTP Service Client và WebSocket/STOMP theo hợp đồng

## 1. Mục tiêu: kiểm thử hợp đồng thật sự diễn ra trên đường truyền

Một interface Java có `@HttpExchange` có thể biên dịch thành công nhưng proxy vẫn gửi nhầm HTTP method, URL, query, header hoặc JSON. Một `@MessageMapping` của STOMP cũng có thể hoạt động trong unit test gọi method trực tiếp nhưng client thực tế không CONNECT, SUBSCRIBE hoặc nhận được MESSAGE.

**Kiểm thử theo hợp đồng (contract testing)** nghĩa là xác minh những điều hai đầu giao tiếp cùng cam kết: request/destination, dữ liệu, loại thông điệp, status/error và quy tắc tương thích. Hợp đồng cần được xác minh ở tầng mà lỗi có thể phát sinh, thay vì chỉ mock một method Java rồi coi như giao thức hoạt động.

Trong bài này có hai đường giao tiếp:

- **HTTP Service Client:** ứng dụng là consumer của REST API. Interface được Spring tạo proxy qua `HttpServiceProxyFactory` và `RestClientAdapter` hoặc `WebClientAdapter`. Kiểm tra request HTTP thật mà proxy tạo ra cùng phản hồi mà adapter decode.
- **WebSocket/STOMP:** client mở WebSocket handshake rồi dùng STOMP `CONNECT`, `SUBSCRIBE`, `SEND`, `MESSAGE`, `ERROR`, `DISCONNECT`. Kiểm tra mapping của Spring `@MessageMapping`, broker và cách dữ liệu đi qua phiên bất đồng bộ.

Bài dùng Java 17+, Spring Framework 6.1 với Spring Boot 3.3.x. Ví dụ runnable tạo một HTTP interface proxy và một STOMP endpoint cục bộ. Không cần API provider bên ngoài hoặc message broker riêng; Spring simple broker dùng trong demo chạy trên một JVM.

Sau bài học, bạn có thể:
1. Chọn unit, mock HTTP, mock socket, integration hoặc consumer-driven contract test phù hợp với rủi ro.
2. Test `@HttpExchange`: method, URI template, path/query encoding, request headers/body, JSON response và error status; kiểm tra cấu hình proxy.
3. Test STOMP từ một client thực: handshake, subscribe, send, nhận payload và timeout; tách kiểm tra destination và transport.
4. Dùng `RECEIPT` khi cần đồng bộ thời điểm subscription được broker xử lý, tránh test race do dùng `Thread.sleep`.
5. Lập ma trận thay đổi phiên bản, lỗi auth/CSRF/Origin, reconnect, fallback SockJS và delivery semantics; nhận biết giới hạn từng loại test.

## 2. “Hợp đồng” khác chữ ký interface Java

Trong HTTP Service Client, chữ ký:

~~~java
@GetExchange("/{id}")
Item findById(@PathVariable("id") long id);
~~~

mô tả **một phần** hợp đồng. Để xác minh trọn vẹn, cần biết base URL, tiền tố endpoint `@HttpExchange`, method GET, mã HTTP thành công/lỗi, `Accept`, JSON shape, field bắt buộc, auth headers và chính sách version. Ví dụ nếu server chỉ nhận `/v2/items/7` nhưng proxy thực tế gọi `/v1/items/7`, Java vẫn biên dịch và unit test mock interface có thể vẫn xanh.

Hợp đồng STOMP cũng có nhiều phần hơn một method:

~~~java
@MessageMapping("/echo")
@SendTo("/topic/echo")
public EchoReply echo(EchoRequest request) { /* ... */ }
~~~

Cần biết endpoint WebSocket HTTP (ví dụ `/events`), application destination prefix (`/app`), client gửi `/app/echo`, server phát đến `/topic/echo`, JSON payload, STOMP headers, điều kiện authentication/authorization và hành vi reconnect. HTTP URL `/events` **khác** STOMP destination `/app/echo`: client gửi `SEND` sau khi đã có kết nối, proxy HTTP không route tới `/app/echo` như một HTTP path.

| Tầng | Điều cần bảo vệ | Kiểm thử nên dùng |
| --- | --- | --- |
| Facade/business | Điều phối, phép tính, lựa chọn fallback | Unit test với fake/mock interface |
| HTTP proxy mapping | URL, method, path/query/header, JSON, status | `MockRestServiceServer` gắn với `RestClient.Builder` |
| HTTP transport | DNS/TLS, redirect, connection timeout, HTTP encoder, proxy | WireMock/MockWebServer qua cổng mạng thật |
| Provider HTTP contract | Server còn chấp nhận request và trả schema/status đã cam kết | Provider verification/consumer-driven contract |
| STOMP handler | `@MessageMapping`, conversion, validation, định tuyến tới broker | Test có Spring messaging infrastructure |
| STOMP end to end | Handshake, `CONNECTED`, `SUBSCRIBE`, `SEND`, `MESSAGE` | `WebSocketStompClient` qua local/random port |
| Deployment | LB, TLS termination, Origin, CSRF, SockJS fallback, broker relay | Integration/E2E qua đường mạng deployment |

**Mỗi cấp chỉ chứng minh phần nó thực sự chạy qua.** Unit test mock CatalogClient không chứng minh HTTP proxy. Test `MockRestServiceServer` không chứng minh TLS thật. Test dùng `WebSocketStompClient` trong một JVM không chứng minh sticky sessions trên cluster hoặc thông điệp được phục hồi sau mất mạng.

## 3. Thiết kế HTTP contract có thể kiểm thử được

Giả sử dịch vụ catalog có:
- `GET /api/items/{id}` trả 200 JSON `{"id":7,"name":"Pen"}`; yêu cầu header `X-Tenant`.
- `POST /api/items` nhận JSON `{"name":"Pen"}` và trả 201 cùng DTO.
- Khi item không có, GET trả 404.
- Phản hồi là JSON; các endpoint còn được version hóa hoặc chuyển host theo cấu hình.

Interface dự kiến:

~~~java
@HttpExchange(url = "/api/items", accept = MediaType.APPLICATION_JSON_VALUE)
public interface CatalogClient {
    @GetExchange("/{id}")
    Item findById(@PathVariable("id") long id,
                  @RequestHeader("X-Tenant") String tenant);

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    Item create(@RequestBody CreateItem body);

    record Item(long id, String name) {}
    record CreateItem(String name) {}
}
~~~

Kiểm thử phải tạo **proxy thật** bằng `HttpServiceProxyFactory` trên một `RestClient` có cấu hình tương đương production. Sau đó test điều mà proxy gửi, không stub `CatalogClient.findById()` bằng Mockito.

### 3.1. MockRestServiceServer

Trong Spring Framework 6.1, có thể bind `MockRestServiceServer` vào `RestClient.Builder`:

~~~java
RestClient.Builder builder = RestClient.builder()
    .baseUrl("https://catalog.example.test");

MockRestServiceServer server =
    MockRestServiceServer.bindTo(builder).build();

RestClient restClient = builder.build();
CatalogClient catalog = HttpServiceProxyFactory
    .builderFor(RestClientAdapter.create(restClient))
    .build()
    .createClient(CatalogClient.class);
~~~

**Thứ tự quan trọng:** bind mock server **trước** `builder.build()`, để RestClient sử dụng request factory do Spring Test lắp vào. Mock server kiểm tra method, URI, headers, body; không mở TCP socket và không đi ra `https://catalog.example.test`. Tên miền .test chỉ để xác minh route.

Những assertion cần làm rõ:
- URL đầy đủ khớp base URL + path khai báo; path variable/query param được encode đúng.
- HTTP method GET/POST/PUT/DELETE chính xác, không chỉ kiểm status.
- Request body encode JSON và Content-Type phù hợp. Khi POST có body, kiểm cả field names, type và nội dung.
- Header auth/tenant/correlation ID xuất hiện đúng, không lộ header của dịch vụ khác.
- Response JSON được decode thành kiểu DTO; test nullable/required fields và thêm field khi schema cho phép.
- 404/429/500 và các lỗi theo contract được chuyển thành exception/status đúng.
- Retry chỉ xảy ra khi được cấu hình có chủ đích; số request và idempotency key phải được xác nhận nếu có retry.

### 3.2. WebClient interface cần kiểm thêm reactive semantics

Với WebClientAdapter, interface thường trả `Mono<Item>` hoặc `Flux<Item>`. HTTP request sẽ được thực hiện khi publisher được subscribe; chỉ gọi method rồi không subscribe **không chứng minh request được gửi**. Test nên dùng `StepVerifier` từ reactor-test cùng mock HTTP server.

Snippet minh họa tích hợp:

~~~java
Mono<Item> result = reactiveCatalog.findById(7L);

StepVerifier.create(result)
    .expectNextMatches(item ->
        item.id() == 7L && "Pen".equals(item.name()))
    .verifyComplete();
~~~

Đoạn này **không standalone**: `reactiveCatalog` là proxy WebClientAdapter đã được tạo, `Item` là DTO, test cần `reactor-test`, mock HTTP server và imports tương ứng. Ngoài success, cần test error signal, timeout, cancellation, retry/resubscription và request count. Nếu mock trả `Mono.just(...)` cho một interface thay vì chạy WebClient proxy, những lỗi URI/header/codec và HTTP status sẽ không được phát hiện.

Kiểm tra `Publisher` bằng `StepVerifier` và response ở mock HTTP server là hai lớp evidence bổ sung: StepVerifier xác minh tín hiệu reactive; mock HTTP server xác minh giao thức bên ngoài.

## 4. Hợp đồng WebSocket và STOMP

Spring STOMP over WebSocket thường có luồng:

~~~mermaid
sequenceDiagram
    participant Client as STOMP client
    participant Server as Spring WebSocket endpoint
    participant Handler as @MessageMapping
    participant Broker as STOMP simple broker
    Client->>Server: HTTP GET /events + Upgrade
    Server-->>Client: 101 Switching Protocols
    Client->>Server: STOMP CONNECT
    Server-->>Client: STOMP CONNECTED
    Client->>Server: STOMP SUBSCRIBE /topic/echo
    Client->>Server: STOMP SEND /app/echo + JSON
    Server->>Handler: Resolve @MessageMapping("/echo")
    Handler->>Broker: Publish to /topic/echo
    Broker-->>Client: STOMP MESSAGE + JSON
~~~

Các rủi ro khác nhau:
- **Handshake:** endpoint sai, WebSocket Upgrade không được proxy chuyển tiếp, TLS/WSS, origin policy hoặc authentication khiến kết nối không mở.
- **CONNECT:** WebSocket được mở nhưng STOMP CONNECT thất bại do header, CSRF hoặc auth; `101` không chứng minh STOMP đã `CONNECTED`.
- **SUBSCRIBE:** destination, permission và định tuyến broker sai. Subscriber chưa được broker ghi nhận khi message đến sẽ có thể không nhận gì.
- **SEND:** client gửi sai `/app` prefix, Content-Type không khớp converter, JSON malformed hoặc Bean Validation bị từ chối.
- **MESSAGE:** payload có schema khác, destination sai, không được broadcast đến subscriber dự kiến.
- **ERROR/close/reconnect:** session cũ mất, client phải connect và subscribe lại; message trong khoảng disconnect cần cơ chế replay ở tầng nghiệp vụ khi contract yêu cầu.

**Không lấy việc gọi trực tiếp một method `@MessageMapping` làm kiểm thử STOMP contract hoàn chỉnh.** Gọi trực tiếp chỉ xác minh logic Java; không đi qua STOMP frame decoder, channel dispatch, message conversion hoặc broker subscription.

### 4.1. Tránh race condition trong integration test

Một test thường viết:

~~~java
session.subscribe("/topic/echo", handler);
session.send("/app/echo", new EchoRequest("ping"));
~~~

Hai hành động diễn ra nhanh trên phía client. Tuy khung SUBSCRIBE được gửi trước SEND, xử lý bất đồng bộ ở server hoặc broker có thể khiến message xuất hiện khi subscription chưa được ghi nhận. `Thread.sleep(500)` chỉ là đoán thời gian, dễ flaky trên CI.

STOMP hỗ trợ header `receipt`: client yêu cầu server trả `RECEIPT` sau khi frame được xử lý. Trong Spring `StompSession`, gọi `setAutoReceipt(true)` trước `subscribe`, sau đó dùng `Subscription.addReceiptTask(...)`. Để receipt tracking hoạt động, `WebSocketStompClient` phải có `TaskScheduler`; test nên đặt timeout có giới hạn và xử lý receipt-lost. Tùy broker/server, **RECEIPT cần được hỗ trợ**; nếu môi trường production dùng broker khác, xác minh khả năng này trước khi chọn chiến lược test.

Không nên assert thứ tự delivery toàn cục giữa nhiều connections: STOMP ordering là vấn đề của từng channel, broker và cấu hình Spring. Nếu nghiệp vụ cần thứ tự nghiêm ngặt hoặc at-least-once, đó phải là contract có sequence/ack riêng và test tương ứng.

## 5. Ví dụ độc lập: HTTP Service Client proxy + STOMP qua socket thật

Ví dụ dưới đây dùng **Java 17+**, **Maven** và Spring Boot **3.3.13** (Spring Framework 6.1). Một project chứa:
- Interface `CatalogClient` để test URI, header, JSON và 404 bằng `MockRestServiceServer`.
- WebSocket STOMP endpoint `/events`, destination `/app/echo` và `/topic/echo`.
- Integration test `@SpringBootTest(webEnvironment = RANDOM_PORT)` tạo client STOMP, chờ SUBSCRIBE receipt, gửi JSON và nhận MESSAGE.

Server demo dùng **simple broker**, một JVM; không giả lập cluster hoặc broker relay. URL catalog trong HTTP test là `.test`, không có outbound network. WebSocket test mở socket **localhost** trên cổng Spring Boot chọn ngẫu nhiên.

### 5.1. Cấu trúc

~~~text
contract-testing-demo/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── ContractTestingApplication.java
    │   ├── CatalogClient.java
    │   └── SocketConfiguration.java
    └── test/java/demo/
        ├── HttpProxyContractTest.java
        └── StompContractTest.java
~~~

### 5.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>contract-testing-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
~~~

`spring-boot-starter-websocket` kéo theo Spring MVC, messaging/STOMP và các thành phần Jackson cần cho demo; `spring-boot-starter-test` cung cấp JUnit 5, MockRestServiceServer và AssertJ/JUnit assertions. Khi nâng cấp Boot/Spring, phải xem lại signatures của các API, đặc biệt `HttpServiceProxyFactory.builderFor` và `WebSocketStompClient.connectAsync`.

### 5.3. ContractTestingApplication.java

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ContractTestingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractTestingApplication.class, args);
    }
}
~~~

### 5.4. CatalogClient.java

~~~java
package demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(url = "/api/items", accept = MediaType.APPLICATION_JSON_VALUE)
public interface CatalogClient {

    @GetExchange("/{id}")
    Item findById(@PathVariable("id") long id,
                  @RequestHeader("X-Tenant") String tenant);

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    Item create(@RequestBody CreateItem input);

    record Item(long id, String name) {}
    record CreateItem(String name) {}
}
~~~

Đây là interface cho proxy client; demo không triển khai REST server `/api/items` vì MockRestServiceServer đóng vai provider trong HTTP tests.

### 5.5. SocketConfiguration.java

~~~java
package demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class SocketConfiguration implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/events");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }

    public record EchoRequest(String text) {}
    public record EchoReply(String text) {}

    @Controller
    public static class EchoController {
        @MessageMapping("/echo")
        @SendTo("/topic/echo")
        public EchoReply echo(EchoRequest request) {
            return new EchoReply("echo:" + request.text());
        }
    }
}
~~~

WebSocket endpoint `/events` là native WebSocket, không bật SockJS. Cấu hình mẫu cố ý không có authentication để kiểm thử contract cơ bản. Hệ thống thật cần xác thực/authorization STOMP destinations, validate payload và bảo vệ Origin/CSRF theo security policy.

### 5.6. HttpProxyContractTest.java

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpProxyContractTest {

    private CatalogClient proxy(RestClient.Builder builder) {
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(builder.build()))
            .build()
            .createClient(CatalogClient.class);
    }

    @Test
    void getUsesAnnotatedPathMethodHeaderAndJson() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://catalog.example.test");
        MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
        CatalogClient client = proxy(builder);

        server.expect(requestTo(
                "https://catalog.example.test/api/items/7"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Tenant", "tenant-1"))
            .andRespond(withSuccess(
                "{\"id\":7,\"name\":\"Pen\"}",
                MediaType.APPLICATION_JSON));

        assertEquals(
            new CatalogClient.Item(7L, "Pen"),
            client.findById(7L, "tenant-1"));
        server.verify();
    }

    @Test
    void postSerializesBodyAndUsesExpectedEndpoint() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://catalog.example.test");
        MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
        CatalogClient client = proxy(builder);

        server.expect(requestTo(
                "https://catalog.example.test/api/items"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"name\":\"Pen\"}"))
            .andRespond(withStatus(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"id\":8,\"name\":\"Pen\"}"));

        assertEquals(
            new CatalogClient.Item(8L, "Pen"),
            client.create(new CatalogClient.CreateItem("Pen")));
        server.verify();
    }

    @Test
    void missingResourceIsReportedAsHttp404() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://catalog.example.test");
        MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
        CatalogClient client = proxy(builder);

        server.expect(requestTo(
                "https://catalog.example.test/api/items/999"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(HttpClientErrorException.NotFound.class,
            () -> client.findById(999L, "tenant-1"));
        server.verify();
    }
}
~~~

Giải thích:
- `proxy(builder)` tạo proxy thật trên adapter RestClient. `bindTo(builder)` được gọi trước đó để thay request factory phục vụ test.
- Test GET kiểm URL, method, header và DTO; nếu annotation path thay đổi hoặc header biến mất, mock expectation lỗi.
- Test POST kiểm Content-Type, request JSON và body response, tránh trường hợp interface type-safe nhưng wire format bị sai.
- Test 404 bảo vệ semantics lỗi: mặc định `retrieve` của RestClient báo 4xx qua exception. Nếu ứng dụng có custom status handler, sửa assertion theo **hợp đồng được công bố**, không theo mặc định ngầm.
- `server.verify()` xác minh những request mong đợi đã thực sự xảy ra; test không tạo TCP connection ra ngoài.

### 5.7. StompContractTest.java

~~~java
package demo;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompContractTest {

    @LocalServerPort
    int port;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();

        stompClient = new WebSocketStompClient(
            new StandardWebSocketClient());
        stompClient.setTaskScheduler(scheduler);
        stompClient.setMessageConverter(
            new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    void clientCanConnectSubscribeSendAndReceiveJson() throws Exception {
        CompletableFuture<SocketConfiguration.EchoReply> received =
            new CompletableFuture<>();
        CompletableFuture<Void> subscribed =
            new CompletableFuture<>();

        StompSession session = stompClient.connectAsync(
                "ws://127.0.0.1:" + port + "/events",
                new StompSessionHandlerAdapter() {})
            .get(10, TimeUnit.SECONDS);

        try {
            // Yêu cầu broker ACK việc xử lý SUBSCRIBE bằng RECEIPT.
            session.setAutoReceipt(true);
            StompSession.Subscription subscription =
                session.subscribe("/topic/echo", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return SocketConfiguration.EchoReply.class;
                    }

                    @Override
                    public void handleFrame(
                            StompHeaders headers, Object payload) {
                        received.complete(
                            (SocketConfiguration.EchoReply) payload);
                    }
                });

            subscription.addReceiptTask(() ->
                subscribed.complete(null));
            subscription.addReceiptLostTask(() ->
                subscribed.completeExceptionally(
                    new IllegalStateException("SUBSCRIBE receipt timeout")));

            subscribed.get(10, TimeUnit.SECONDS);
            session.send("/app/echo",
                new SocketConfiguration.EchoRequest("ping"));

            SocketConfiguration.EchoReply reply =
                received.get(10, TimeUnit.SECONDS);
            assertEquals(
                new SocketConfiguration.EchoReply("echo:ping"), reply);
        } finally {
            session.disconnect();
        }
    }
}
~~~

Test sử dụng `connectAsync` (trả CompletableFuture, API thích hợp của Spring 6.1), thay cho các overload `connect(...)` cũ đã bị deprecated. `@LocalServerPort` lấy cổng thật do Boot cấp. Client `StandardWebSocketClient` mở WebSocket TCP trên localhost, STOMP handshake đi qua Spring server, SUBSCRIBE vào simple broker, SEND tới controller và chờ MESSAGE JSON.

**Tại sao chờ RECEIPT?** Nếu test gửi message quá sớm sau SUBSCRIBE, broker có thể chưa thêm subscription. Chờ callback receipt cung cấp điểm đồng bộ dựa trên giao thức. `ThreadPoolTaskScheduler` cho phép theo dõi receipt và timeout; `CompletableFuture.get(10, TimeUnit.SECONDS)` giúp test thất bại rõ ràng nếu không CONNECT, không nhận RECEIPT hoặc không nhận MESSAGE. Không dùng `Thread.sleep` đoán thời điểm sẵn sàng.

**Giới hạn:** việc nhận một MESSAGE chứng minh luồng cơ bản hoạt động qua localhost, chưa chứng minh auth/Origin/CORS, loss/replay, network proxy hay scale-out. Server/broker thật phải hỗ trợ STOMP receipts; nếu chính sách của broker không cung cấp receipt, dùng cơ chế synchronization đã kiểm chứng riêng thay cho sleep.

### 5.8. Cách chạy và kỳ vọng

Đặt đúng các file như cấu trúc trên, cài JDK 17+ và Maven, trong thư mục project chạy:

~~~bash
mvn -q test
~~~

Có **4 phương thức `@Test`** trong hai class: 3 HTTP proxy tests và 1 STOMP integration test. Trạng thái mong đợi nếu môi trường và code phù hợp là Maven exit code **0**. Báo cáo chi tiết được sinh tại `target/surefire-reports/`; `-q` có thể không in tổng số test ra stdout. Bài này trình bày code để người đọc thực thi, **không khẳng định các test đã được chạy thành công trong môi trường biên soạn**.

Nếu test STOMP timeout, lần lượt kiểm:
1. Server đã bind port chưa; endpoint đúng `/events` và URL dùng `ws://` chưa?
2. Đã có frame `CONNECTED` chưa?
3. `SUBSCRIBE` destination có đúng prefix `/topic`? Broker có gửi `RECEIPT`?
4. `SEND` tới `/app/echo` có được map vào `@MessageMapping("/echo")`?
5. Converter có tạo/đọc JSON đúng record? Có STOMP ERROR, conversion exception hoặc close bất thường không?
6. Test có dùng timeout hữu hạn và đóng session/client/scheduler để tránh leak tài nguyên không?

Đối với HTTP proxy test fail, bật log request status/URL ở môi trường test, đọc mismatch từ MockRestServiceServer; xác định lỗi là URI/template, method, headers, body converter hay status mapping. Không sửa expected URL chỉ để xanh nếu hợp đồng provider yêu cầu URL cũ.

## 6. Làm rõ HTTP contract với nhiều dịch vụ và nhiều phiên bản

Nếu ứng dụng gọi Inventory Service v1 và Pricing Service v2, mỗi proxy cần baseUrl, credentials, timeout và error policy phù hợp. Kiểm thử không chỉ tạo các interface riêng mà nên lấy **bean cấu hình production** trong Spring context rồi kiểm tra chúng route đúng.

Ma trận ví dụ:

| Kiểm tra | Inventory Client | Pricing Client |
| --- | --- | --- |
| Base URL | inventory.example.test | pricing.example.test |
| HTTP path | /v1/stock/A-1 | /v2/prices/A-1 |
| Authentication | Audience inventory | Audience pricing |
| Response | Stock DTO | Price DTO |
| Error | OUT_OF_STOCK/404 theo contract | PRICE_UNAVAILABLE/503 theo contract |
| Schema evolution | Field available hoặc quantity | amountMinorUnits + currency |
| Negative security test | Không có token pricing | Không có token inventory |

Đừng gộp cả hai vào cùng một mock server nếu mục tiêu là xác minh **cách ly host và token**: dùng hai mock servers riêng hoặc hai cổng HTTP mock network thực. Đảm bảo factory tạo từ chính `RestClient.Builder` production hoặc cấu hình tương đương; test interface trên builder khác với production có thể bỏ sót sai cấu hình baseUrl.

### 6.1. Contract của provider, không chỉ stub theo kỳ vọng consumer

Mock HTTP test xác nhận “client gửi như consumer nghĩ API hoạt động”. Nó không đảm bảo service thật trả đúng JSON đó. Hợp đồng cần được thống nhất, version hóa và xác minh ở provider bằng:
- OpenAPI/schema + provider contract tests, hoặc consumer-driven contracts (Pact, Spring Cloud Contract nếu phù hợp stack).
- Tập JSON fixtures: field bắt buộc, field mới, missing/null, numeric boundaries, timezone/currency, status codes và error body.
- Provider verification trong CI đối với các consumer versions vẫn đang hoạt động.
- Compatibility check trước deployment: provider mới phải đáp ứng các hợp đồng đã xuất bản hoặc cung cấp API version mới.
- Integration tests với provider sandbox/staging để bắt những rủi ro ngoài contract đã mô tả như auth, redirects, certificates, rate limits.

Một thay đổi thêm field response thường tương thích khi consumer bỏ qua unknown fields; **đổi tên/xóa field bắt buộc hoặc đổi đơn vị tiền** có thể là breaking change dù JSON vẫn parse được. Client có thể dùng `@JsonIgnoreProperties(ignoreUnknown = true)` có chọn lọc, nhưng phải validation required semantics.

### 6.2. Retry và tác dụng phụ

Test HTTP proxy có retry cần kiểm **số request thực tế** và chính sách `maxRetries`. Tăng retry nhưng không dùng idempotency trên POST tạo đơn/thanh toán có thể nhân đôi side effects. Test theo kịch bản:
- GET 503 → 200, đúng tối đa số lượt; lỗi 400 không retry.
- POST response mất sau khi server đã commit: chỉ retry khi server hỗ trợ cùng Idempotency-Key với deduplication.
- Không sinh key mới trong từng lần resubscribe.
- Khi hết retry, trả lỗi cuối hoặc lỗi domain đúng contract, không biến thành thành công giả.
- Timeout toàn chuỗi phù hợp SLA của caller, không để CI chờ vô hạn.

Với MockRestServiceServer có thể khai báo các expected requests theo thứ tự, nhưng muốn kiểm kết nối ngắt, read timeout/TLS và retry ở transport thật thì dùng mock web server chạy socket thay cho request factory giả.

## 7. Thiết kế hợp đồng STOMP tương thích qua các phiên bản

STOMP contract có cả:
- **Endpoint URL:** ví dụ `/events` để mở transport; đổi path gây lỗi handshake.
- **Application destinations:** `SEND /app/echo`; đổi prefix hoặc method mapping gây lỗi route.
- **Subscribe destinations:** `SUBSCRIBE /topic/echo`; đổi topic khiến subscriber không nhận MESSAGE.
- **Payload schema:** trường tên, kiểu, nullable, default, enum và ý nghĩa thời gian/số tiền.
- **Error semantics:** STOMP ERROR frame, application-level error message, hoặc close code theo giao thức được thống nhất.
- **Security:** websocket origin, STOMP CONNECT token/CSRF, quyền SEND/SUBSCRIBE từng destination; không chỉ auth HTTP handshake.
- **Delivery:** client nhận được bao nhiêu lần? Broker giữ message khi client offline không? Có ACK/redelivery hoặc replay theo sequence ID không?
- **Reconnect/resubscribe:** người dùng/máy khách có thể ngắt mạng; session ID và subscriptions mới cần khôi phục.

Các thay đổi như `/topic/echo` thành `/topic/messages` là breaking cho client đang SUBSCRIBE topic cũ. Thêm field tùy chọn JSON có thể tương thích khi client decoder chấp nhận. Đổi String `text` thành object phức tạp là thay đổi contract đáng test ở cả producer và consumer.

**Simple broker của Spring** phù hợp demo/test một JVM; subscription nằm trong bộ nhớ của node. Chạy nhiều instance có thể cần broker relay/external STOMP broker và session affinity SockJS, nhưng việc test một node thành công không chứng minh cluster hoạt động. Không khẳng định STOMP mang lại exactly-once delivery; phần đó cần protocol/logic application riêng.

## 8. Socket tests nên bao phủ gì ngoài đường thành công?

| Kịch bản | Cách test phù hợp | Điều xác minh |
| --- | --- | --- |
| Sai endpoint WebSocket | WebSocketStompClient đến URI không tồn tại | Handshake fail có giới hạn thời gian |
| Origin không được phép | Test handshake với Origin header thực | Request không vượt qua allowed-origin policy |
| CONNECT không có auth/CSRF khi bật Security | Kết nối với STOMP headers thiếu/thừa | Từ chối đúng lúc, không để subscribe trái phép |
| SUBSCRIBE destination sai | Subscribe topic không tồn tại/không có quyền | Không nhận thông điệp hoặc bị từ chối theo contract |
| SEND JSON malformed | Client gửi payload raw sai hoặc content-type không đúng | Error behavior/conversion được quan sát |
| SEND hợp lệ | Subscription rồi SEND, chờ MESSAGE | Mapping/JSON/broker end-to-end |
| Đa subscriber | Hai sessions SUBSCRIBE topic | Broadcast đúng các subscriber có quyền |
| Unsubscribe | UNSUBSCRIBE, sau đó SEND | Session đã bỏ đăng ký không nhận thêm |
| Heartbeat/idle timeout | Test với scheduler/timeouts rút ngắn | Disconnect/reconnect phù hợp policy |
| Reconnect | Đóng socket rồi tạo session mới | CONNECT lại, SUBSCRIBE lại, missing message policy rõ |
| SockJS HTTP fallback | SockJsClient/Browser ép xhr-polling | HTTP transports hoạt động, không chỉ 101 Upgrade |
| LB + hai node | Integration qua load balancer thật | Sticky session/broker relay/routing |
| Broker unavailable | Tắt broker relay trong staging test | App/client báo lỗi và phục hồi đúng |

**Tách lớp test:** server-side test có thể dùng `ChannelInterceptor` hoặc `TestChannelInterceptor` để quan sát messages gửi/nhận trên Spring channels mà không mở WebSocket. Cách này nhanh để kiểm `@MessageMapping` và conversion, nhưng phải gắn đúng `clientInboundChannel`/`clientOutboundChannel` và xử lý executor bất đồng bộ; nó không thay thế bài test handshake. Khi sử dụng `@SendToUser`, test phải có session/principal và định tuyến user destination, không chỉ so nội dung message.

### SockJS khi có proxy

Một STOMP native WebSocket test **không** kiểm SockJS fallback. Nếu endpoint dùng `registry.addEndpoint("/events").withSockJS()`, một SockJS client thường truy cập HTTP base URL `http://host/events`, gọi `/events/info` rồi chọn WebSocket hoặc XHR transport. Proxy phải cho GET/POST/OPTIONS phù hợp và chuyển tiếp session paths, xử lý buffering/timeout và CORS/CSRF.

Trong production, có thể thêm test qua browser hoặc SockJsClient giới hạn transport:
- WebSocket transport: thấy handshake 101, CONNECT/SUBSCRIBE/SEND/MESSAGE.
- XHR polling: không cần 101; có `/info`, `/xhr`, `/xhr_send`; vẫn nhận MESSAGE.
- XHR streaming: kiểm data tới client trước khi proxy giữ buffer.
- Với nhiều backend: cùng SockJS session phải được định tuyến nhất quán, sau reconnect vẫn subscribe lại.

Không áp chính sách `"Access-Control-Allow-Origin: *"` với credentialed requests hoặc tắt toàn bộ CSRF chỉ để test xanh. Khi có Spring Security, kiểm tra STOMP CONNECT CSRF, Origin, session cookie và quyền destination trong môi trường gần production.

## 9. Giảm flaky tests và chứng minh negative paths

Kiểm thử mạng và messaging thường bất đồng bộ; **không dùng sleep làm cơ chế đồng bộ chính**. Dùng `CompletableFuture`, `BlockingQueue.poll(timeout)`, `CountDownLatch.await(timeout)`, hoặc STOMP RECEIPT và instrumentation hợp lệ. Mỗi wait phải có timeout hữu hạn; đảm bảo test đóng session, scheduler và socket.

Khi kiểm “không nhận MESSAGE” hoặc “người dùng chưa được phép SUBSCRIBE”, việc chờ ngắn rồi không thấy message **không phải bằng chứng mạnh** rằng server đã chặn. Cần quan sát dấu hiệu từ chối rõ ràng, STOMP ERROR, frame close/authorization exception, broker receipt hoặc server logs/metrics trong test. Với notification stream, so sánh số lượng và định danh messages; tránh nhận nhầm message cũ từ test trước.

Một số kỹ thuật nâng chất lượng:
1. Dùng test isolation: session/destination hoặc correlation ID riêng cho mỗi test; tránh shared broker state từ context cache.
2. Mock provider HTTP tại biên giao thức, không mock interface khi mục tiêu là proxy mapping.
3. Khi test STOMP, subscribe sau CONNECTED và chờ broker acknowledgement nếu broker hỗ trợ; gửi sau mốc synchronization thật.
4. Assert cả dữ liệu **và** status/headers/destination; test chỉ `notNull` có thể bỏ sót contract break.
5. Kiểm đúng số HTTP attempts và số STOMP messages khi retry/reconnect.
6. Giữ `maxWait` hợp lý theo CI; chẩn đoán transport/broker/serializer khi timeout, không tăng timeout vô hạn.
7. Dùng port động (`RANDOM_PORT`) và đóng clients; tránh cổng hardcode hoặc leak thread giữa các suite.
8. Chỉ test dữ liệu nhạy cảm bằng sample vô hại; không ghi token thật hoặc nội dung nghiệp vụ nhạy cảm vào log/assertion.
9. Khi test proxy HTTPS/WSS qua edge, kiểm certificate, forwarded headers, Origin, query/path rewriting và no mixed-content.
10. Đảm bảo tests chỉ gọi server giả/trong local hoặc sandbox được phép, không vô tình chạy write command vào production.

## 10. Ma trận kiểm thử kết hợp HTTP + STOMP

Một tính năng như “đặt hàng rồi nhận thông báo trạng thái” kết hợp HTTP POST để tạo đơn và STOMP topic để phát cập nhật. Cần tách hợp đồng:

| Trường hợp | HTTP Service Client | STOMP |
| --- | --- | --- |
| Happy path | POST đúng body/status/ID | Sau subscribe, nhận event theo order ID |
| Input lỗi | 400/422 ProblemDetail theo contract | Payload bất hợp lệ từ SEND bị xử lý theo policy |
| Resource không có | 404 JSON/ProblemDetail đúng | Topic/destination hoặc event “not found” theo contract |
| Sai quyền | HTTP 401/403, không tạo đơn | CONNECT/SUBSCRIBE/SEND unauthorized không được thực hiện |
| Retry | Có giới hạn; POST dùng key idempotent nếu server hỗ trợ | Reconnect không được tự tạo đơn lần nữa |
| Schema mới | Fields optional mới không phá DTO cũ | Event versioning, unknown fields, enum values |
| Gián đoạn mạng | Timeout rồi truy vấn trạng thái operation | Reconnect + resubscribe + reconcile missing events |
| Trùng dữ liệu | Server dedup key operation | Consumer event dedup theo eventId nếu contract cần |
| Scale out | Cấu hình host/auth/timeout chính xác | Broker relay/affinity/session và broadcast nhiều node |

Một “unit test chạy xanh” cho facade không đủ chứng minh hệ thống này. Ngoài hai bộ contract tests tách biệt, cần một vài **end-to-end business scenarios**: cùng order ID được trả bởi HTTP và xuất hiện trong event, status update không đến trước điều kiện mong muốn, duplicate/replay được xử lý theo idempotency contract.

## 11. Sai lầm phổ biến

- **Mock interface @HttpExchange rồi kết luận URL đúng:** không chạy proxy; thêm MockRestServiceServer hoặc mock HTTP socket server.
- **Bind MockRestServiceServer sau khi đã build RestClient:** RestClient dùng request factory cũ và mock không chặn request; bind trước build.
- **Chỉ assert HTTP status:** request có thể sai path/header/body nhưng stub vẫn trả dữ liệu; kiểm request đúng contract.
- **Dùng WebClient nhưng quên subscribe:** publisher chưa chạy, không có HTTP call; dùng StepVerifier và mock server.
- **Không test custom status handler:** 404/409/429 bị chuyển sai thành fallback hoặc exception không mong muốn.
- **Chỉ test @MessageMapping bằng gọi method Java:** không kiểm STOMP frame, converter, broker/destination.
- **Coi WebSocket 101 là STOMP CONNECTED:** handshake thành công vẫn có thể bị từ chối ở STOMP CONNECT.
- **Gửi ngay sau SUBSCRIBE rồi sleep nếu flaky:** race chưa được xử lý; dùng RECEIPT/điểm đồng bộ thật.
- **Quên task scheduler cho STOMP receipt:** callback và timeout tracking không hoạt động như mong muốn.
- **Đổi WebSocket native thành SockJS mà giữ URL ws://:** SockJS thường nhận HTTP base URL; cần test client đúng protocol.
- **Bỏ qua Origin/CSRF/auth:** localhost test không có security không đại diện production.
- **Tin vào simple broker khi scale cluster:** một node test không chứng minh cross-node fan-out.
- **Giả định reconnect sẽ replay message:** STOMP session mới không tự chứa history; thiết kế replay riêng.
- **Bắt timeout mà không đóng session:** leak socket/thread và khiến CI test sau thất bại ngẫu nhiên.
- **Đổi code để test xanh khi provider thật đã breaking:** version contract, cảnh báo và phối hợp rollout/rollback.

## 12. Checklist khi bổ sung một endpoint mới

**HTTP Service Client**
- Interface và proxy có method, base URL, URI template, query/header/body, Accept/Content-Type đúng.
- Có contract status 2xx, lỗi 4xx/5xx, media type và DTO nullable/required rõ.
- Mock HTTP test bảo vệ request và JSON; integration/contract verification kết nối provider thật hoặc sandbox khi cần.
- Có negative auth/rate limit/timeout và retry/idempotency tests tương ứng.
- Nếu thêm API version, test song song consumer cũ/mới và rollback.

**WebSocket/STOMP**
- Handshake URL, CONNECT headers, destination SEND/SUBSCRIBE và payload version được mô tả rõ.
- Có test qua STOMP client thật xác minh `MESSAGE` sau subscription, không chỉ mở socket.
- Có test ERROR/authorization, reconnect/subscribe lại và duplicate/delivery policy khi áp dụng.
- SockJS fallback, CORS/CSRF/cookies và proxy/LB được test riêng trong môi trường có chúng.
- Timeout/receipt/lifecycle được kiểm soát; resources được giải phóng sau test.

## 13. Kết luận

HTTP Service Client và WebSocket/STOMP đều có những **hợp đồng ẩn phía sau interface và annotation**: HTTP cần đúng request và response; STOMP cần đúng kết nối, destination, frames và delivery. Bảo vệ hợp đồng cần đặt assertions ở biên giao thức và thêm provider verification; một mock method Java không thay thế được điều đó.

Với HTTP, hãy dùng `MockRestServiceServer` cho `RestClient` proxy tests nhanh, mock HTTP server chạy socket cho transport và provider contracts cho khả năng tương thích. Với WebSocket/STOMP, hãy dùng test messaging có Spring infrastructure và một số integration tests với `WebSocketStompClient` kiểm CONNECT → SUBSCRIBE → SEND → MESSAGE. Khi hệ thống thực có authentication, SockJS, reverse proxy, broker relay hoặc nhiều nodes, các lớp đó cần test riêng theo đúng topology production.

## Tài liệu tham khảo

- Spring Framework Reference — HTTP Interface Client và RestClient: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
- Spring Framework 6.1 Javadoc — HttpServiceProxyFactory: https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/service/invoker/HttpServiceProxyFactory.html
- Spring Framework 6.1 Javadoc — MockRestServiceServer: https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/test/web/client/MockRestServiceServer.html
- Spring Framework Reference — Testing Client Applications: https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html
- Spring Framework Reference — STOMP over WebSocket testing: https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/testing.html
- Spring Framework 6.1 Javadoc — WebSocketStompClient: https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/socket/messaging/WebSocketStompClient.html
- Spring Framework Reference — STOMP Client, RECEIPT: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/client.html
- Spring Framework Reference — SockJS fallback: https://docs.spring.io/spring-framework/reference/6.2/web/websocket/fallback.html
- Spring Security Reference — WebSocket Security: https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html


</details>

- [Quay lại đầu trang](#back-to-top)
