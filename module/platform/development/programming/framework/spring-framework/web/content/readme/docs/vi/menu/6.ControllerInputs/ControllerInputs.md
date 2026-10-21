<a id="back-to-top"></a>

# Đầu vào controller

## Menu
- [@PathVariable và @MatrixVariable trong đường dẫn](#controller-inputs-001)
- [@RequestParam: giá trị bắt buộc, mặc định, nhiều giá trị và Map](#controller-inputs-002)
- [@RequestHeader và @CookieValue](#controller-inputs-003)
- [@RequestBody, HttpEntity và đọc nội dung yêu cầu](#controller-inputs-004)
- [@ModelAttribute và liên kết dữ liệu biểu mẫu](#controller-inputs-005)
- [@RequestPart và tham số multipart](#controller-inputs-006)
- [@RequestAttribute, @SessionAttribute và dữ liệu có sẵn trong ngữ cảnh](#controller-inputs-007)
- [HttpServletRequest, HttpServletResponse, Principal và Locale trong chữ ký phương thức](#controller-inputs-008)
- [Giá trị null, Optional và phân biệt đầu vào thiếu với đầu vào rỗng](#controller-inputs-009)
- [Quy tắc chọn nơi lấy tham số và giới hạn đọc request body nhiều lần](#controller-inputs-010)

## <a id="controller-inputs-001">@PathVariable và @MatrixVariable trong đường dẫn</a>

<details>
<summary>Xem chi tiết</summary>

# @PathVariable và @MatrixVariable trong đường dẫn — Spring MVC

> **Phạm vi:** Spring MVC trên Servlet stack, ví dụ Java 17+ và Spring Boot dùng `spring-boot-starter-web`. Bài này giải thích cách lấy dữ liệu từ **phần đường dẫn URL**, cách phân biệt biến chiếm chỗ với tham số theo từng đoạn đường dẫn, và những điều kiện để matrix variable hoạt động an toàn.

## 1. Nhìn vào URL trước khi viết controller

Xét ba HTTP request:

```text
GET /api/orders/42
GET /api/cars/7;color=red,green;year=2012
GET /api/owners/42;q=11/pets/21;q=22
```

- Trong `/orders/42`, `42` thay thế chỗ trống `{orderId}` trong route `/orders/{orderId}`: dùng `@PathVariable` để nhận mã đơn hàng.
- Trong `/cars/7;color=red,green;year=2012`, đoạn đường dẫn có giá trị chính `7` và metadata `color`, `year` nằm sau dấu chấm phẩy `;`: `@PathVariable` nhận `7`; `@MatrixVariable` nhận `color` và `year`.
- Trong `/owners/42;q=11/pets/21;q=22`, tên `q` xuất hiện ở **hai đoạn** khác nhau. `@MatrixVariable(pathVar = "...")` chỉ định chính xác đoạn cần đọc.

| Annotation | Dữ liệu lấy từ đâu? | Ví dụ | Giá trị |
| --- | --- | --- | --- |
| `@PathVariable("orderId")` | Biến `{orderId}` trong mẫu đường dẫn | `/orders/42` | `42` |
| `@MatrixVariable("color")` | Cặp `key=value` sau dấu `;` trong một path segment | `/cars/7;color=red` | `red` |
| `@RequestParam("color")` | Query string sau `?` | `/cars/7?color=red` | `red` |

**Ghi nhớ:** `@PathVariable` định danh tài nguyên hoặc thành phần của route; `@MatrixVariable` mô tả tham số gắn với một **đoạn đường dẫn cụ thể**. Chúng có thể dùng cùng nhau trong một phương thức xử lý.

## 2. `@PathVariable`: ánh xạ URI template vào tham số Java

### 2.1. Khai báo route, đặt tên rõ ràng

```java
@RestController
@RequestMapping("/api")
class OrderController {

    @GetMapping("/orders/{orderId}")
    public String findOrder(@PathVariable("orderId") long orderId) {
        return "orderId=" + orderId;
    }
}
```

- `@GetMapping("/orders/{orderId}")` đăng ký mẫu URL; dấu ngoặc nhọn khai báo biến của đường dẫn.
- `@PathVariable("orderId")` lấy giá trị ứng với `{orderId}`. `long` yêu cầu Spring chuyển chuỗi `42` thành số.
- `GET /api/orders/42` trả nội dung `orderId=42`.
- `GET /api/orders/abc` khớp mẫu route nhưng không chuyển được `abc` thành `long`, thông thường trả HTTP **400 Bad Request** do lỗi chuyển kiểu.
- `GET /api/orders` thiếu đoạn bắt buộc `/{orderId}` nên không khớp mapping trên, thông thường trả HTTP **404 Not Found** nếu không có route khác.

Có thể bỏ tên trong `@PathVariable` nếu tên tham số Java khớp tên biến route và thông tin tên tham số được lưu lúc biên dịch (ví dụ `-parameters`). Viết rõ `@PathVariable("orderId")` giúp tránh phụ thuộc thiết lập biên dịch và dễ đọc khi tên Java khác URI.

### 2.2. Lấy nhiều biến và ghép mapping lớp/phương thức

```java
@RestController
@RequestMapping("/owners/{ownerId}")
class PetController {

    @GetMapping("/pets/{petId}")
    public String getPet(
            @PathVariable("ownerId") long ownerId,
            @PathVariable("petId") long petId) {
        return "ownerId=" + ownerId + ", petId=" + petId;
    }
}
```

Request `GET /owners/42/pets/21` trả `ownerId=42, petId=21`. `{ownerId}` ở mapping cấp lớp vẫn được method đọc như bình thường.

Nếu cần lấy mọi URI template variable mà không khai báo từng tham số, có thể dùng `@PathVariable Map<String, String> variables`; tuy nhiên, tên cụ thể và kiểu dữ liệu rõ ràng thường dễ kiểm tra, bảo trì hơn.

### 2.3. Ràng buộc mẫu URI và kiểu dữ liệu

```java
@GetMapping("/versions/{version:v[0-9]+}")
public String version(@PathVariable("version") String version) {
    return version;
}
```

Route trên khớp `/versions/v12` và không khớp `/versions/latest`. Mẫu `{version:v[0-9]+}` là **điều kiện khớp URL**, còn annotation `@PathVariable` chỉ nhận giá trị đã trích ra. Với `@PathVariable UUID id` hoặc `long id`, chuyển kiểu được thực hiện bằng hệ thống conversion của Spring MVC. Regex không thay thế kiểm tra nghiệp vụ, quyền truy cập hoặc xác thực tài nguyên.

Trong Spring MVC hiện đại, `PathPatternParser` xử lý đường dẫn theo từng segment. `{id}` thông thường bắt một đoạn; ký tự `/` phân chia các đoạn. Khi cần bắt nhiều đoạn cuối, Spring còn có cú pháp `{*path}` nhưng chỉ dùng ở vị trí thích hợp theo quy tắc `PathPattern`, tránh giả định rằng `{id}` sẽ tự nhận mọi ký tự `/`.

## 3. `@MatrixVariable`: tham số `;key=value` gắn với path segment

Một **path segment** là phần nằm giữa hai dấu `/`. Matrix variable xuất hiện ngay trong segment đó, sau dấu `;`:

```text
/cars/7;color=red;year=2012
      └── segment: "7;color=red;year=2012"
          ├── path variable carId = "7"
          ├── matrix variable color = "red"
          └── matrix variable year = "2012"
```

Mẫu mapping nên có URI variable che phần segment mang tham số, chẳng hạn `/cars/{carId}`:

```java
@GetMapping("/cars/{carId}")
public String getCar(
        @PathVariable("carId") long carId,
        @MatrixVariable(name = "color", pathVar = "carId") String color,
        @MatrixVariable(name = "year", pathVar = "carId") int year) {
    return "carId=" + carId + ", color=" + color + ", year=" + year;
}
```

Với `GET /cars/7;color=red;year=2012`, kết quả là:

```text
carId=7, color=red, year=2012
```

`name` (hoặc `value`) chọn tên tham số; `pathVar` chọn URI template variable của **segment chứa nó**. Nếu chỉ có một segment mang matrix variable và không mơ hồ, có thể bỏ `pathVar`. Khi nhiều segment cùng chứa tên giống nhau, hãy chỉ định `pathVar` rõ ràng.

### 3.1. Hai cách truyền nhiều giá trị

Spring MVC hỗ trợ nhiều giá trị phân tách bằng dấu phẩy hoặc lặp lại cùng key:

```text
/cars/7;color=red,green;year=2012
/cars/7;color=red;color=green;year=2012
```

Có thể nhận `color` dưới dạng `List<String>` (qua cơ chế chuyển đổi giá trị mặc định của Spring) hoặc dùng `MultiValueMap<String, String>` để giữ nguyên các giá trị theo từng tên. Ví dụ nhận một tham số:

```java
@GetMapping("/cars/{carId}")
public List<String> carColors(
        @MatrixVariable(name = "color", pathVar = "carId") List<String> colors) {
    return colors;
}
```

Với `/cars/7;color=red,green`, body JSON tương ứng là:

```json
["red","green"]
```

Đoạn code trên **minh họa một method controller riêng**, không khai báo đồng thời với một method khác có cùng `@GetMapping("/cars/{carId}")` trong cùng ứng dụng; nếu trùng mapping, Spring có thể báo ambiguous mapping khi khởi động.

### 3.2. Phân biệt cùng tên trên các segment

```java
@GetMapping("/owners/{ownerId}/pets/{petId}")
public String getPet(
        @PathVariable("ownerId") long ownerId,
        @PathVariable("petId") long petId,
        @MatrixVariable(name = "q", pathVar = "ownerId") int ownerQ,
        @MatrixVariable(name = "q", pathVar = "petId") int petQ) {
    return "ownerId=" + ownerId + ", petId=" + petId
            + ", ownerQ=" + ownerQ + ", petQ=" + petQ;
}
```

Request:

```text
GET /owners/42;q=11/pets/21;q=22
```

Trả về:

```text
ownerId=42, petId=21, ownerQ=11, petQ=22
```

`pathVar = "ownerId"` đọc `q=11` từ segment `42;q=11`; `pathVar = "petId"` đọc `q=22` từ segment `21;q=22`. Không nên dựa vào việc Spring chọn ngẫu nhiên một giá trị khi cùng tên xuất hiện nhiều chỗ.

### 3.3. Tùy chọn, giá trị mặc định và bản đồ tham số

Mặc định `@MatrixVariable` có `required = true`. Khi tham số vắng mặt, Spring MVC thông thường trả 400 vì không giải quyết được tham số bắt buộc.

```java
@GetMapping("/pets/{petId}")
public String petDetail(
        @PathVariable("petId") long id,
        @MatrixVariable(name = "lang", pathVar = "petId",
                        defaultValue = "vi") String lang,
        @MatrixVariable(name = "page", pathVar = "petId",
                        defaultValue = "1") int page) {
    return "id=" + id + ", lang=" + lang + ", page=" + page;
}
```

Với `/pets/21`, giá trị là `id=21, lang=vi, page=1`. `defaultValue` cũng khiến tham số không còn bắt buộc. Nếu chọn `required = false` mà **không** có `defaultValue`, nên dùng kiểu tham chiếu `Integer` thay vì `int` để biểu diễn thiếu giá trị rõ ràng.

Để đọc toàn bộ matrix variables thay vì một key:

```java
@GetMapping("/owners/{ownerId}/pets/{petId}")
public MultiValueMap<String, String> allPetParameters(
        @MatrixVariable(pathVar = "petId")
        MultiValueMap<String, String> petParameters) {
    return petParameters;
}
```

Đoạn method minh họa này cần `import org.springframework.util.MultiValueMap;`. Request `/owners/42;q=11/pets/21;q=22;s=23` cho map của segment `petId` với `q → ["22"]`, `s → ["23"]`. Nếu bỏ `pathVar` trên `MultiValueMap`, Spring có thể gom matrix variables từ các segment của route; chẳng hạn `q` có hai giá trị `11` và `22`. Cần dùng `MultiValueMap` khi có khả năng lặp key; `Map<String, String>` chỉ biểu diễn một giá trị mỗi key.

## 4. Spring MVC xử lý hai annotation như thế nào?

1. `DispatcherServlet` nhận request và tìm handler qua `RequestMappingHandlerMapping` theo HTTP method và pattern đã đăng ký.
2. Engine match đường dẫn xác định template variables (ví dụ `ownerId=42`, `petId=21`) và dữ liệu matrix gắn với từng segment nếu đường dẫn, cấu hình và tầng bảo mật cho phép.
3. `HandlerMethodArgumentResolver` thích hợp xử lý `@PathVariable`, `@MatrixVariable`; `ConversionService` chuyển giá trị chuỗi thành `long`, `int`, collection, kiểu tùy biến khi được cấu hình.
4. Spring gọi method controller với các đối số; `@RestController` chuyển giá trị trả về thành HTTP response body thông qua message converter.

Mô hình tư duy: **pattern chọn handler → các resolver lấy dữ liệu URL → conversion chuyển kiểu → method xử lý nghiệp vụ**. Annotation không tự thực hiện truy vấn cơ sở dữ liệu hoặc xác thực quyền truy cập tài nguyên.

## 5. Ví dụ hoàn chỉnh có thể chạy — Java 17 + Spring Web

Tạo project Spring Boot trên [Spring Initializr](https://start.spring.io/) với Java 17 trở lên và dependency **Spring Web**. Dùng đúng package `com.example.demo`, thay nội dung file `src/main/java/com/example/demo/DemoApplication.java` bằng toàn bộ đoạn sau. Đây là **một chương trình đầy đủ** (ba endpoint không trùng mapping):

```java
package com.example.demo;

import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@RestController
@RequestMapping("/api")
class RouteController {

    @GetMapping("/orders/{orderId}")
    public String order(@PathVariable("orderId") long orderId) {
        return "orderId=" + orderId;
    }

    @GetMapping("/cars/{carId}")
    public String car(
            @PathVariable("carId") long carId,
            @MatrixVariable(name = "color", pathVar = "carId",
                            required = false) List<String> colors,
            @MatrixVariable(name = "year", pathVar = "carId",
                            defaultValue = "0") int year) {
        List<String> safeColors = colors == null ? List.of() : colors;
        return "carId=" + carId
                + ", colors=" + safeColors
                + ", year=" + year;
    }

    @GetMapping("/owners/{ownerId}/pets/{petId}")
    public String pet(
            @PathVariable("ownerId") long ownerId,
            @PathVariable("petId") long petId,
            @MatrixVariable(name = "q", pathVar = "ownerId") int ownerQ,
            @MatrixVariable(name = "q", pathVar = "petId") int petQ) {
        return "ownerId=" + ownerId
                + ", petId=" + petId
                + ", ownerQ=" + ownerQ
                + ", petQ=" + petQ;
    }
}
```

Từ thư mục gốc dự án đã sinh bằng Spring Initializr, chạy Maven wrapper:

```bash
# macOS/Linux
./mvnw spring-boot:run

# Windows PowerShell hoặc Command Prompt
mvnw.cmd spring-boot:run
```

Các lệnh cURL sau sử dụng URL đặt trong dấu nháy để shell không diễn giải dấu `;`. Trên Windows PowerShell, thay `curl` bằng `curl.exe` nếu `curl` được ánh xạ sang lệnh khác:

```bash
curl -s 'http://localhost:8080/api/orders/42'
# orderId=42

curl -s 'http://localhost:8080/api/cars/7;color=red,green;year=2012'
# carId=7, colors=[red, green], year=2012

curl -s 'http://localhost:8080/api/cars/7'
# carId=7, colors=[], year=0

curl -s 'http://localhost:8080/api/owners/42;q=11/pets/21;q=22'
# ownerId=42, petId=21, ownerQ=11, petQ=22
```

Các dòng `# ...` là **kết quả body dự kiến**, không phải một phần stdout của ứng dụng Java. `curl -s` in body không kèm status; nếu cần phân tích HTTP status/header dùng `curl -i`. Kết quả matrix variable giả định các tầng HTTP và bảo mật thực sự cho phép dấu `;` đi đến Spring MVC; xem mục 6 trước khi kết luận controller lỗi.

## 6. Cấu hình và bảo mật: nguyên nhân phổ biến khiến matrix variable không chạy

### 6.1. Khác biệt engine khớp đường dẫn

Spring MVC 6.0+ mặc định dùng **`PathPatternParser`**: parser tách path theo segment, xử lý path parameters phục vụ trích matrix variables; thông thường không cần cấu hình legacy `UrlPathHelper` chỉ để đọc matrix data.

Một số ứng dụng Spring MVC cũ dùng **`AntPathMatcher`** (string pattern matching). Với cấu hình legacy này, nếu `UrlPathHelper` xóa nội dung sau dấu `;`, `@MatrixVariable` không còn dữ liệu để lấy. Các bài hướng dẫn cũ thường cấu hình:

```java
// Chỉ minh họa cho ứng dụng MVC dùng cơ chế legacy AntPathMatcher.
@Override
public void configurePathMatch(PathMatchConfigurer configurer) {
    UrlPathHelper helper = new UrlPathHelper();
    helper.setRemoveSemicolonContent(false);
    configurer.setUrlPathHelper(helper);
}
```

Đây là **đoạn method cấu hình minh họa**, cần đặt trong lớp `@Configuration implements WebMvcConfigurer` với các import thích hợp. Không sao chép máy móc vào cấu hình Spring MVC 6+/7+ hiện đại, vì cách khớp đường dẫn và tính tương thích khác nhau. Nếu dùng legacy, trước tiên xác minh cấu hình matcher hiện hành.

### 6.2. Spring Security thường chặn dấu chấm phẩy

`StrictHttpFirewall` của Spring Security mặc định **từ chối semicolon trong URL** do rủi ro HTTP request ambiguity và bypass quy tắc bảo mật. Điều này có thể gây lỗi trước khi request đi vào controller, dù `@MatrixVariable` và mapping viết đúng. Nếu ứng dụng thật sự cần matrix parameters và đã đánh giá rủi ro, tài liệu Spring Security đưa ra ví dụ:

```java
// Đoạn cấu hình độc lập; yêu cầu spring-security-web trên classpath.
@Bean
StrictHttpFirewall httpFirewall() {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setAllowSemicolon(true);
    return firewall;
}
```

Import `org.springframework.context.annotation.Bean` và `org.springframework.security.web.firewall.StrictHttpFirewall` trong lớp cấu hình Security. **Chỉ bật khi thực sự cần**; sau đó phải kiểm thử các quy tắc authorization, URL normalization, proxy, cache và giới hạn tham số. Với Spring Security, đây là thay đổi chính sách bảo mật, không phải bước bắt buộc chung của Spring MVC. Khi `spring-boot-starter-web` chạy đơn lẻ mà không thêm Spring Security, ví dụ ở mục 5 không cần bean này.

### 6.3. Proxy, gateway và client

Dấu `;` có thể bị reverse proxy, server, firewall hoặc client xử lý theo các quy tắc riêng. Hãy xem **request thực sự mà controller nhận được** khi gỡ lỗi, kiểm tra gateway và container. Không đặt access token, mật khẩu, dữ liệu bí mật vào cả path, matrix parameter hay query string vì URL có thể xuất hiện trong log, lịch sử trình duyệt và bộ nhớ đệm. Với API công khai có yêu cầu tương thích rộng, `?color=red&year=2012` cùng `@RequestParam` thường dễ vận hành hơn.

## 7. Phân biệt và chọn cách thiết kế endpoint

| Nhu cầu | Đề xuất | Ví dụ |
| --- | --- | --- |
| Xác định một tài nguyên cụ thể | `@PathVariable` | `GET /orders/42` |
| Xác định tài nguyên lồng nhau | Nhiều `@PathVariable` | `GET /owners/42/pets/21` |
| Filter/sort/pagination chung của tài nguyên danh sách | `@RequestParam` | `GET /cars?color=red&page=2` |
| Metadata có ngữ nghĩa **theo từng segment** | `@MatrixVariable`, nếu hạ tầng hỗ trợ | `GET /owners/42;q=11/pets/21;q=22` |

Đừng dùng `@MatrixVariable` để thay thế `@PathVariable` cho ID; cũng đừng giả định `@RequestParam` có thể đọc `;color=red`. Mỗi annotation lấy dữ liệu từ một vị trí khác nhau của URL.

Một lựa chọn thiết kế dễ bảo trì thường là route rõ định danh + query parameters cho bộ lọc. Matrix variables hữu ích khi cần diễn đạt tùy chọn gắn với **một đoạn cụ thể** của tuyến đường; chi phí là yêu cầu hỗ trợ nhất quán của client, gateway và tầng bảo mật.

## 8. Lỗi thường gặp và cách khoanh vùng

| Triệu chứng | Nguyên nhân cần kiểm tra | Cách xử lý |
| --- | --- | --- |
| `404` với `/orders` | Mapping yêu cầu `/{orderId}` | Gọi đúng URL hoặc khai báo route riêng cho collection |
| `400` với `/orders/abc` | `abc` không chuyển được sang `long` | Dùng ID hợp lệ hoặc chọn kiểu `String` kèm kiểm tra |
| `400` khi thiếu `;color=...` | `@MatrixVariable` mặc định bắt buộc | Dùng `required=false` hoặc `defaultValue` |
| `q` sai/không rõ thuộc segment nào | Cùng tên matrix variable ở nhiều segment | Dùng `pathVar="ownerId"` hoặc `pathVar="petId"` |
| Có `;` thì request bị từ chối sớm | Spring Security `StrictHttpFirewall` hoặc proxy/container | Xác định lớp chặn; chỉ đổi chính sách sau khi đánh giá rủi ro |
| Không đọc được matrix param dù route khớp | Parser/cấu hình legacy đã loại nội dung `;...` | Kiểm tra MVC path matching mode và `UrlPathHelper` nếu dùng legacy |
| Lỗi `Name for argument ... not specified` | Bỏ tên annotation và không giữ parameter names khi compile | Viết rõ `@PathVariable("id")` / `@MatrixVariable("color")` |
| Lỗi ambiguous mapping lúc khởi động | Hai method khai báo cùng HTTP method + route | Chỉ giữ một handler cho một mapping tương đương |

Thực hành gỡ lỗi: (1) thử route đơn giản `/api/orders/42`, (2) gọi matrix route với `curl -i`, (3) kiểm tra status và log Spring Security/proxy, (4) xác nhận có đúng URI template variable bao lấy segment, (5) đối chiếu tên `pathVar` và kiểu dữ liệu Java. Tránh sửa cấu hình bảo mật như một phản xạ khi chưa xác định tầng gây lỗi.

## 9. Tự kiểm tra kiến thức

**Câu 1.** Với `@GetMapping("/books/{id}")` và `@PathVariable("id") Long id`, URL `/books/50` cho `id` bằng gì? Nếu gọi `/books/abc` thì điều gì có thể xảy ra?

**Đáp án:** `id=50L` sau chuyển kiểu; `abc` không chuyển sang `Long` nên thông thường Spring MVC trả 400.

**Câu 2.** Với `/owners/42;view=compact/pets/21;view=full`, làm sao đọc `view=full` một cách rõ ràng?

```java
@MatrixVariable(name = "view", pathVar = "petId") String view
```

Method mapping tương ứng phải có `/owners/{ownerId}/pets/{petId}`, và trong ví dụ `view` nhận `full`.

**Câu 3.** `/cars/7;color=red` và `/cars/7?color=red` khác ở đâu?

**Đáp án:** Trường hợp đầu là matrix variable thuộc path segment `7`; trường hợp sau là query parameter, thường dùng `@RequestParam("color")`.

**Câu 4.** Vì sao `@MatrixVariable` không đọc được trong ứng dụng có Spring Security dù method đúng?

**Đáp án:** Một khả năng đáng kiểm tra là `StrictHttpFirewall` từ chối URL chứa `;` trước bước mapping. Thay đổi cấu hình cần cân nhắc rủi ro bảo mật và xem xét lựa chọn query parameters.

## 10. Tài liệu chuẩn để tra cứu

- [Spring Framework — Mapping Requests / URI patterns](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html): `@PathVariable`, pattern, regexp, chuyển kiểu.
- [Spring Framework — Matrix Variables](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/matrix-variables.html): cú pháp `;name=value`, `pathVar`, `defaultValue`, `MultiValueMap`.
- [Spring Framework — Servlet Path Matching](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/handlermapping-path.html): `PathPatternParser` và khác biệt matching.
- [Spring Security — HttpFirewall](https://docs.spring.io/spring-security/reference/servlet/exploits/firewall.html): mặc định chặn semicolon và chính sách ngoại lệ.
- [Spring Framework — Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html): vị trí các annotation trong hệ thống resolver.

**Tổng kết vận dụng:** dùng `@PathVariable` cho thành phần được đặt tên bằng mẫu URI, dùng `@MatrixVariable` khi cần đọc các cặp `key=value` gắn với từng segment, chỉ rõ `pathVar` khi có nhiều đoạn, và kiểm tra tầng bảo mật/hạ tầng trước khi đưa dấu `;` vào hợp đồng API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="controller-inputs-002">@RequestParam: giá trị bắt buộc, mặc định, nhiều giá trị và Map</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC: @RequestParam — tham số bắt buộc, giá trị mặc định, nhiều giá trị và Map

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Nhận dữ liệu đầu vào của controller bằng `@RequestParam` và phân biệt tham số bắt buộc với tham số tùy chọn.
- Đặt `defaultValue`, hiểu khi nào giá trị mặc định được dùng và Spring chuyển đổi kiểu dữ liệu như thế nào.
- Nhận nhiều giá trị cùng tên qua mảng hoặc `List<T>`.
- Thu thập toàn bộ request parameters bằng `Map<String, String>` hoặc `MultiValueMap<String, String>`, chọn đúng cấu trúc khi key có thể xuất hiện nhiều lần.
- Chẩn đoán lỗi thường gặp (thiếu tham số, chuyển đổi kiểu sai, giá trị rỗng) và kiểm thử endpoint thực tế.

Phạm vi: Spring MVC (Servlet stack), Java 17+; ví dụ chạy bằng Spring Boot 3.5.0 và Spring Framework 6.2.x. Các hành vi cốt lõi bên dưới cũng được mô tả trong tài liệu Spring MVC.

## 2. @RequestParam nhận dữ liệu nào?

`@RequestParam` ánh xạ **request parameter** sang một tham số của phương thức controller:

~~~java
@GetMapping("/hello")
public String hello(@RequestParam("name") String name) {
    return "Hello " + name;
}
~~~

Với yêu cầu `GET /hello?name=Lan`, biến `name` nhận `"Lan"`.

Trong **Spring MVC**, khái niệm request parameter của Servlet bao gồm query string (ví dụ `?page=2`) và dữ liệu form được Servlet phân tích (thường là `application/x-www-form-urlencoded`). Đối với JSON trong request body, sử dụng `@RequestBody` với kiểu DTO phù hợp; đối với một đoạn URL như `/products/42`, sử dụng `@PathVariable`. Spring WebFlux có quy tắc khác: `@RequestParam` chỉ lấy query parameters; không chuyển nguyên tắc MVC này sang WebFlux một cách máy móc.

**Cơ chế tóm lược:** Spring tìm request parameter theo tên → xác định có bị thiếu hay không → dùng giá trị mặc định nếu được cấu hình → chuyển đổi chuỗi sang kiểu Java đích → gọi phương thức controller. Các lỗi ở giai đoạn phân giải đối số có thể trả HTTP 400 trước khi phần thân phương thức chạy.

## 3. Thuộc tính của @RequestParam

| Thuộc tính | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `name` / `value` | Tên request parameter; hai thuộc tính là bí danh của nhau | `@RequestParam("q")`, `@RequestParam(name = "q")` |
| `required` | Mặc định `true`: phải có tham số; `false`: cho phép vắng mặt | `@RequestParam(name = "q", required = false)` |
| `defaultValue` | Giá trị thay thế khi tham số **vắng mặt hoặc có giá trị rỗng**; tự động khiến `required` không còn bắt buộc | `@RequestParam(name = "page", defaultValue = "0")` |

Nên ghi rõ `name` trong ví dụ và API công khai. Nếu bỏ tên, việc suy ra tên từ tham số Java phụ thuộc thông tin tên tham số có sẵn khi biên dịch; tên tường minh cũng giúp API dễ đọc.

### 3.1. Tham số bắt buộc

~~~java
@GetMapping("/search")
public String search(@RequestParam(name = "q") String q) {
    return "Searching: " + q;
}
~~~

- `GET /search?q=java` → `q = "java"`.
- `GET /search` → thiếu `q`, Spring MVC mặc định trả HTTP **400 Bad Request**, thường với `MissingServletRequestParameterException`.
- `GET /search?q=` → `q` có mặt nhưng là chuỗi rỗng. Với `String` và không có ràng buộc kiểm tra nội dung, `required = true` **không đồng nghĩa** “không được để trống”.

Nếu nghiệp vụ đòi hỏi chữ có nghĩa, hãy bổ sung kiểm tra đầu vào (ví dụ Bean Validation với `@NotBlank` và cấu hình validation phù hợp) hoặc logic kiểm tra riêng. Đừng dùng `required` thay cho validation.

### 3.2. Không bắt buộc: required = false và Optional

~~~java
@GetMapping("/filter")
public String filter(
        @RequestParam(name = "category", required = false) String category,
        @RequestParam(name = "minPrice") Optional<Integer> minPrice) {
    String selected = category == null ? "all" : category;
    String lowerBound = minPrice.map(String::valueOf).orElse("none");
    return selected + " / " + lowerBound;
}
~~~

Khi thiếu `category`, đối số tham chiếu thường nhận `null`; khi thiếu `minPrice`, `Optional<Integer>` là `Optional.empty()`. Dùng `Optional` có thể biểu diễn ý nghĩa “có hoặc không có” rõ hơn. Tránh dựa vào `null` với kiểu nguyên thủy như `int`: hãy chọn `Integer`, `Optional<Integer>` hoặc cung cấp giá trị mặc định.

### 3.3. Giá trị mặc định

~~~java
@GetMapping("/page")
public String page(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size) {
    return "page=" + page + ", size=" + size;
}
~~~

| URL | `page` | `size` | Lý do |
| --- | ---: | ---: | --- |
| `/page` | 0 | 20 | Cả hai vắng mặt |
| `/page?page=3` | 3 | 20 | Chỉ `size` dùng mặc định |
| `/page?page=&size=5` | 0 | 5 | Chuỗi rỗng kích hoạt `defaultValue` |
| `/page?page=abc` | — | — | Không đổi `abc` thành `int` được; thông thường HTTP 400 |

`defaultValue` là chuỗi trong annotation; Spring chuyển đổi nó sang kiểu khai báo (`"20"` → `int 20`). Khai báo đồng thời `required = true` và `defaultValue` gây hiểu nhầm vì **có mặc định là không bắt buộc**. Giá trị mặc định không có tác dụng thay thế mọi giá trị không hợp lệ: `?page=abc` không “rơi về 0”.

## 4. Nhiều giá trị của cùng một tham số

Một URL có thể lặp lại cùng key:

~~~text
GET /api/tags?tag=java&tag=spring&tag=web
~~~

Nhận bằng mảng hoặc danh sách:

~~~java
@GetMapping("/tags")
public List<String> tags(@RequestParam(name = "tag") List<String> tags) {
    return tags;
}

@GetMapping("/tag-array")
public String[] tagArray(@RequestParam(name = "tag") String[] tags) {
    return tags;
}
~~~

Với URL trên, kết quả biểu diễn ba phần tử `["java", "spring", "web"]`. Nếu khai báo `List<Integer>`, Spring còn phải chuyển đổi từng giá trị sang số; một phần tử như `tag=abc` có thể làm binding thất bại. Với `List<String>` bắt buộc, thiếu hẳn tham số `tag` có thể gây HTTP 400; để cho phép vắng mặt:

~~~java
@GetMapping("/tags-optional")
public List<String> tagsOptional(
        @RequestParam(name = "tag", required = false) List<String> tags) {
    return tags == null ? List.of() : tags;
}
~~~

Khi khách hàng gửi một chuỗi ngăn cách bằng dấu phẩy (`?tag=java,spring`), Spring có thể phân tách trong quá trình chuyển đổi kiểu tùy dạng dữ liệu và cấu hình. Để API không mơ hồ, hãy **quy ước rõ** cách gửi danh sách; các ví dụ ở đây dùng key lặp lại. Không giả định mọi client hoặc converter đều xử lý cú pháp dấu phẩy giống nhau.

## 5. Map và MultiValueMap

### 5.1. Thu tất cả parameters với Map<String, String>

~~~java
@GetMapping("/params")
public Map<String, String> params(@RequestParam Map<String, String> params) {
    return params;
}
~~~

Yêu cầu `/params?name=Lan&sort=asc` tạo map có `name → "Lan"`, `sort → "asc"`. Điểm quyết định: **không ghi tên** trong `@RequestParam` và kiểu đối số là `Map<String, String>`, Spring thu các request parameters theo tên vào map. Mỗi key trong map chỉ lưu được **một** chuỗi; khi một key xuất hiện nhiều lần, cấu trúc này không giữ được đầy đủ các giá trị lặp lại.

### 5.2. Thu đầy đủ giá trị lặp lại với MultiValueMap<String, String>

~~~java
import org.springframework.util.MultiValueMap;

@GetMapping("/params-all")
public MultiValueMap<String, String> paramsAll(
        @RequestParam MultiValueMap<String, String> params) {
    return params;
}
~~~

Với `/params-all?tag=java&tag=spring&sort=asc`, dữ liệu tương đương:

~~~json
{
  "tag": ["java", "spring"],
  "sort": ["asc"]
}
~~~

`MultiValueMap<K, V>` biểu diễn một key → `List<V>`. Dùng `getFirst("tag")` nếu chỉ muốn giá trị đầu tiên, `get("tag")` khi muốn toàn bộ danh sách. Chú ý import chính xác `org.springframework.util.MultiValueMap`.

| Kiểu tham số controller | Chọn khi nào | Nếu gửi `?tag=a&tag=b` |
| --- | --- | --- |
| `@RequestParam("tag") String tag` | API cần một giá trị | Không phù hợp để bảo toàn mọi giá trị |
| `@RequestParam("tag") List<String> tags` | Chỉ cần mọi giá trị của riêng key `tag` | `["a", "b"]` |
| `@RequestParam Map<String, String> params` | Muốn toàn bộ tên tham số, một giá trị/key | Không bảo toàn cả `a` và `b` |
| `@RequestParam MultiValueMap<String, String> params` | Muốn toàn bộ tên và mọi giá trị/key | `tag → ["a", "b"]` |

**Cẩn thận với tên annotation:** `@RequestParam("filters") Map<...>` không có nghĩa “thu toàn bộ parameters vào map”. Khi chỉ rõ tên `filters`, Spring tìm một parameter có tên đó và tìm cách chuyển đổi **giá trị của riêng parameter ấy** thành `Map` (cần bộ chuyển đổi thích hợp). Để gom tất cả key, dùng `@RequestParam Map<String, String>` **không chỉ rõ tên**.

## 6. Ví dụ chạy được với Java 17 và Spring Boot

Cấu trúc dự án Maven:

~~~text
request-param-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── RequestParamDemoApplication.java
~~~

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>request-param-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
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

Tạo `src/main/java/demo/RequestParamDemoApplication.java`:

~~~java
package demo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class RequestParamDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(RequestParamDemoApplication.class, args);
    }
}

@RestController
@RequestMapping("/api")
class ParamController {
    @GetMapping("/required")
    public Map<String, String> required(@RequestParam("q") String q) {
        return Map.of("q", q);
    }

    @GetMapping("/defaults")
    public Map<String, Integer> defaults(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return Map.of("page", page, "size", size);
    }

    @GetMapping("/optional")
    public Map<String, Object> optional(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "limit") Optional<Integer> limit) {
        return Map.of(
                "keyword", keyword == null ? "(none)" : keyword,
                "limit", limit.orElse(10));
    }

    @GetMapping("/tags")
    public Map<String, List<String>> tags(
            @RequestParam(name = "tag", required = false) List<String> tags) {
        return Map.of("tags", tags == null ? List.of() : tags);
    }

    @GetMapping("/map")
    public Map<String, String> map(@RequestParam Map<String, String> all) {
        return all;
    }

    @GetMapping("/multi")
    public MultiValueMap<String, String> multi(
            @RequestParam MultiValueMap<String, String> all) {
        return all;
    }
}
~~~

Ở thư mục chứa `pom.xml`, chạy:

~~~bash
mvn spring-boot:run
~~~

Ứng dụng mặc định lắng nghe trên `http://localhost:8080`. Có thể thử:

~~~bash
curl -i 'http://localhost:8080/api/required?q=java'
curl -i 'http://localhost:8080/api/required'
curl -i 'http://localhost:8080/api/defaults?page=&size=5'
curl -i 'http://localhost:8080/api/defaults?page=oops'
curl -i 'http://localhost:8080/api/optional'
curl -i 'http://localhost:8080/api/tags?tag=java&tag=spring'
curl -i 'http://localhost:8080/api/map?name=Lan&sort=asc'
curl -i 'http://localhost:8080/api/multi?tag=java&tag=spring&sort=asc'
~~~

Nếu chạy trong Windows PowerShell và `curl` là alias, dùng `curl.exe`.

Đối chiếu kết quả quan trọng (thứ tự key trong JSON object không được bảo đảm):

| Lệnh/đường dẫn | Kết quả chính |
| --- | --- |
| `/api/required?q=java` | HTTP 200, `{"q":"java"}` |
| `/api/required` | HTTP 400 do thiếu `q` |
| `/api/defaults?page=&size=5` | HTTP 200, `page=0`, `size=5` |
| `/api/defaults?page=oops` | HTTP 400 vì `oops` không chuyển thành `int` |
| `/api/optional` | HTTP 200, `keyword="(none)"`, `limit=10` |
| `/api/tags?tag=java&tag=spring` | HTTP 200, `{"tags":["java","spring"]}` |
| `/api/map?name=Lan&sort=asc` | HTTP 200, map chứa `name="Lan"`, `sort="asc"` |
| `/api/multi?tag=java&tag=spring&sort=asc` | HTTP 200, `tag=["java","spring"]`, `sort=["asc"]` |

Các chuỗi phản hồi lỗi mặc định (body, message, trace) có thể khác theo cấu hình Spring Boot; kiểm tra HTTP status và dữ liệu trả về thay vì phụ thuộc thông điệp lỗi nguyên văn.

## 7. Những lỗi dễ mắc và cách xử lý

1. **Tưởng `required = true` đồng nghĩa phải có chữ:** Với `String`, `?q=` vẫn là chuỗi rỗng. Kiểm tra tính hợp lệ ở tầng validation.
2. **Cho rằng `defaultValue` sửa được mọi đầu vào sai:** `?page=abc` thường là lỗi chuyển đổi số, không biến thành giá trị mặc định.
3. **Dùng `int` với `required = false` mà không có mặc định:** Kiểu nguyên thủy không chứa `null`; dùng `Integer`, `Optional<Integer>` hoặc `defaultValue`.
4. **Dùng `Map<String, String>` để nhận mọi giá trị trùng key:** Cần `MultiValueMap<String, String>` hoặc `List<String>`.
5. **Ghi `@RequestParam("x") Map<String, String>` để gom tất cả:** Tên `x` khiến Spring xử lý một parameter được đặt tên, không phải mọi parameters.
6. **Nhầm request parameters với JSON body hoặc path variable:** Chọn đúng `@RequestBody`, `@PathVariable`, `@RequestParam` theo nguồn dữ liệu.
7. **Tin dữ liệu Map có thể dùng trực tiếp vào truy vấn hoặc bộ lọc:** Mọi key/value từ client đều không đáng tin. Với API nghiêm ngặt, dùng tham số/DTO đã định nghĩa, validate giá trị, giới hạn trang/kích thước và whitelist tên sort/filter trước khi dùng.
8. **Không chuẩn hóa cách client gửi danh sách:** Quy định rõ key lặp lại, mã hóa URL hợp lệ, tên key chính xác; dùng `curl --get --data-urlencode` khi giá trị chứa khoảng trắng hoặc ký tự đặc biệt.

## 8. Tự luyện tập

**Bài 1 — Required:** Viết `GET /orders?id=12` nhận `long id`. Điều gì xảy ra khi bỏ `id` hoặc gửi `id=xyz`? Giải thích hai nguyên nhân khác nhau dẫn tới HTTP 400.

**Bài 2 — Default:** Viết `GET /products?page=1&size=10` với mặc định `page=0`, `size=20`. Dự đoán `/products?page=&size=7` và đề xuất giới hạn hợp lệ cho `size`.

**Bài 3 — Multi-value:** Nhận `?color=red&color=blue` thành `List<String>`. Sửa endpoint để không lỗi khi thiếu `color`; trả danh sách rỗng.

**Bài 4 — Map:** Với `?tag=a&tag=b&sort=asc`, nêu sự khác nhau giữa `Map<String,String>` và `MultiValueMap<String,String>`. Cho biết cách lấy riêng phần tử đầu tiên của `tag` từ `MultiValueMap`.

*Gợi ý kiểm tra:* Bài 1 phân biệt thiếu parameter và chuyển đổi `String → long` sai; bài 2 `page=0`, `size=7`; bài 3 dùng `required=false` rồi thay `null` bằng `List.of()`; bài 4 dùng `params.get("tag")` và `params.getFirst("tag")`.

## 9. Tài liệu tham khảo

- [Spring Framework — MVC `@RequestParam`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html): cách bind request parameters, `Optional`, mảng/list, `Map` và `MultiValueMap`.
- [Spring Framework — `RequestParam` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/bind/annotation/RequestParam.html): ý nghĩa chính xác của `name`, `required` và `defaultValue`.
- [Spring Boot 3.5.0 release](https://spring.io/blog/2025/05/22/spring-boot-3-5-0-available-now/): phiên bản dùng trong dự án ví dụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="controller-inputs-003">@RequestHeader và @CookieValue</a>

<details>
<summary>Xem chi tiết</summary>

# @RequestHeader và @CookieValue trong Spring MVC

## Mục tiêu bài học

Sau bài này, bạn có thể đọc và chuyển đổi HTTP request header bằng `@RequestHeader`; đọc cookie từ request bằng `@CookieValue`; xử lý trường hợp thiếu giá trị, giá trị mặc định và lỗi chuyển kiểu; chọn đúng cách lấy một hoặc nhiều header; viết và kiểm thử controller Spring MVC mà không nhầm header/cookie với query parameter hay request body.

Phạm vi: **Spring MVC (Servlet stack)**, Java 17+, ví dụ dùng Spring Boot 3.x và package `jakarta.servlet`. Trong Spring WebFlux cũng có hai annotation này, nhưng cơ chế xử lý request thuộc reactive stack và không phải trọng tâm ở đây.

## 1. Header và cookie xuất hiện ở đâu trong HTTP?

Một request có thể mang metadata trong **header**, cookie trong header đặc biệt `Cookie`, dữ liệu URL trong query string và dữ liệu gửi lên trong body:

~~~http
GET /api/inspect?source=mobile HTTP/1.1
Host: localhost:8080
X-Request-Id: req-42
X-Retry-Count: 3
Accept-Language: vi-VN
Cookie: theme=dark; visitCount=7
~~~

- `@RequestHeader("X-Request-Id")` lấy header `X-Request-Id` (giá trị `req-42`).
- `@CookieValue("theme")` lấy **cookie có tên `theme`** (giá trị `dark`) từ request.
- `@RequestParam("source")` sẽ lấy query parameter `source=mobile`; đó là cơ chế khác.
- `@RequestBody` đọc body, không dùng để đọc header/cookie.

**Điểm phân biệt quan trọng:** `Cookie` là header HTTP chứa danh sách cookie, còn `@CookieValue` phân tích để lấy **cookie theo tên**. Header `Set-Cookie` thuộc **response** khi server yêu cầu trình duyệt lưu cookie; không phải cách dùng `@CookieValue` để đọc cookie từ request.

| Tiêu chí | `@RequestHeader` | `@CookieValue` |
| --- | --- | --- |
| Đọc từ | Header của HTTP request | Cookie của HTTP request |
| Chọn bằng | Tên header, ví dụ `X-Request-Id` | Tên cookie, ví dụ `theme` |
| Kiểu thường gặp | `String`, `Integer`, `List<String>`, `HttpHeaders` | `String`, `Integer`, `Cookie` |
| Bắt buộc theo mặc định | Có (`required=true`) | Có (`required=true`) |
| Nếu thiếu và không có default | Thường phản hồi HTTP 400 | Thường phản hồi HTTP 400 |
| Nếu `defaultValue` được đặt | Giá trị fallback; không còn bắt buộc | Giá trị fallback; không còn bắt buộc |

Theo giao thức HTTP, **tên header không phân biệt hoa/thường**; tên cookie thì cần dùng đúng tên đã đặt (tránh tự ý đổi chữ hoa/thường). Cả header và cookie đều là dữ liệu do client gửi, **không tự động đáng tin cậy**.

## 2. Cú pháp và cơ chế binding

Hai annotation được khai báo tại **tham số của handler method** (`@GetMapping`, `@PostMapping`…):

~~~java
@GetMapping("/example")
public String example(
        @RequestHeader("X-Request-Id") String requestId,
        @CookieValue("theme") String theme) {
    return requestId + ":" + theme;
}
~~~

Spring MVC tìm handler theo URL và HTTP method, sau đó các argument resolver đọc header hoặc cookie tương ứng và bind vào tham số. Nếu tham số là `Integer`, `Long`, enum hoặc kiểu khác được hệ thống conversion hỗ trợ, Spring áp dụng **type conversion**. Không phải JSON deserialization: việc đọc hai annotation này không cần `HttpMessageConverter` hoặc `@RequestBody`.

Các thuộc tính chính áp dụng cho **cả hai**:

~~~java
@RequestHeader(name = "X-Request-Id", required = false)
@RequestHeader(value = "X-Request-Id", defaultValue = "unknown")
@CookieValue(name = "theme", required = false)
@CookieValue(value = "theme", defaultValue = "light")
~~~

- `name` và `value` là hai cách đặt cùng một tên; cú pháp rút gọn `@RequestHeader("X-Request-Id")` tương đương `value = "X-Request-Id"`.
- `required = true` là mặc định: nếu request thiếu dữ liệu bắt buộc, Spring MVC phát sinh lỗi binding và thông thường trả **400 Bad Request**.
- `required = false` cho phép thiếu. Với tham số tham chiếu như `String`, `Integer` hoặc `Cookie`, kết quả thường là `null`; có thể dùng `Optional<T>` khi phù hợp.
- `defaultValue = "light"` cung cấp fallback nếu thiếu **hoặc giá trị rỗng**, và ngầm làm `required = false`. Chuỗi mặc định cũng phải chuyển được sang kiểu đích khi có chuyển kiểu.
- Đối với giá trị tùy chọn, tránh kiểu nguyên thủy như `int` nếu có thể trả `null`; ưu tiên `Integer`, `Optional<Integer>` hoặc đặt `defaultValue`.

## 3. Ví dụ Spring Boot MVC chạy được

Tạo dự án bằng Spring Initializr với **Java 17+**, **Maven**, Spring Boot **3.x**, dependency **Spring Web** (kéo theo `spring-boot-starter-web`). Dùng hai file dưới đây; nếu muốn chạy phần kiểm thử, thêm dependency **Spring Boot Starter Test**.

**`src/main/java/com/example/demo/DemoApplication.java`**

~~~java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
~~~

**`src/main/java/com/example/demo/HeaderCookieController.java`**

~~~java
package com.example.demo;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HeaderCookieController {

    @GetMapping("/inspect")
    public RequestSnapshot inspect(
            @RequestHeader(name = "X-Request-Id", defaultValue = "anonymous")
            String requestId,
            @RequestHeader(name = "X-Retry-Count", required = false)
            Integer retryCount,
            @CookieValue(name = "theme", defaultValue = "light")
            String theme,
            @CookieValue(name = "visitCount", required = false)
            Integer visitCount) {

        return new RequestSnapshot(requestId, retryCount, theme, visitCount);
    }

    // Một tham số có thể bắt buộc: request thiếu header hoặc cookie sẽ bị từ chối.
    @GetMapping("/mandatory")
    public String mandatory(
            @RequestHeader("X-Request-Id") String requestId,
            @CookieValue("consent") String consent) {
        return requestId + ":" + consent;
    }

    // Đọc cả bộ header; getFirst chỉ trả giá trị đầu tiên của một tên.
    @GetMapping("/trace")
    public String trace(@RequestHeader HttpHeaders headers) {
        String traceId = headers.getFirst("X-Trace-Id");
        return traceId == null ? "none" : traceId;
    }

    // Spring có thể chuyển header dạng danh sách phân tách bởi dấu phẩy.
    @GetMapping("/accept")
    public List<String> accept(
            @RequestHeader(name = "Accept", required = false)
            List<String> acceptedTypes) {
        return acceptedTypes == null ? List.of() : acceptedTypes;
    }

    // Khi cần thuộc tính đối tượng Cookie của Servlet, có thể nhận Cookie.
    @GetMapping("/raw-cookie")
    public String rawCookie(
            @CookieValue(name = "theme", required = false) Cookie cookie) {
        return cookie == null ? "missing" : cookie.getName() + "=" + cookie.getValue();
    }

    public record RequestSnapshot(
            String requestId,
            Integer retryCount,
            String theme,
            Integer visitCount) {}
}
~~~

**Cách hoạt động:** `/inspect` trả một record được Spring MVC tuần tự hóa thành JSON. `X-Retry-Count` và `visitCount` được chuyển từ chuỗi HTTP sang `Integer`; hai tham số tùy chọn có thể là `null`. `/mandatory` minh họa yêu cầu bắt buộc; `/trace` dùng `HttpHeaders`; `/raw-cookie` dùng `jakarta.servlet.http.Cookie`.

Chạy trong thư mục dự án:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows có thể dùng `mvnw.cmd spring-boot:run`; nếu không có Maven Wrapper, dùng `mvn spring-boot:run` khi đã cài Maven.

Gửi một request có cả header và cookie:

~~~bash
curl -s -H "X-Request-Id: req-42" -H "X-Retry-Count: 3" --cookie "theme=dark; visitCount=7" http://localhost:8080/api/inspect
~~~

**Nội dung JSON kỳ vọng** (thứ tự các property JSON không quan trọng):

~~~json
{"requestId":"req-42","retryCount":3,"theme":"dark","visitCount":7}
~~~

Thử bỏ tất cả header/cookie do ví dụ tự định nghĩa:

~~~bash
curl -s http://localhost:8080/api/inspect
~~~

Với cấu hình JSON mặc định của Spring Boot 3, kết quả tương đương:

~~~json
{"requestId":"anonymous","retryCount":null,"theme":"light","visitCount":null}
~~~

Hai tình huống khác để quan sát:

~~~bash
curl -i http://localhost:8080/api/mandatory
curl -i -H "X-Request-Id: req-42" -H "X-Retry-Count: abc" http://localhost:8080/api/inspect
~~~

Request thứ nhất thiếu thông tin bắt buộc; request thứ hai không thể chuyển `abc` thành `Integer`. Mặc định Spring MVC thường trả **HTTP 400** cho cả hai. Nội dung JSON lỗi thay đổi theo cấu hình ứng dụng, vì vậy **không nên phụ thuộc vào một body lỗi cố định**.

> Nếu dùng PowerShell trên Windows, gọi `curl.exe` để dùng cú pháp CLI `curl` nhất quán; trong một số môi trường `curl` là alias của PowerShell.

## 4. Đọc một header, nhiều giá trị và toàn bộ header

### Một tên header

~~~java
@GetMapping("/language")
public String language(
        @RequestHeader(name = "Accept-Language", required = false)
        String acceptLanguage) {
    return acceptLanguage == null ? "not specified" : acceptLanguage;
}
~~~

Ví dụ `Accept-Language: vi-VN, en;q=0.8` được đọc dưới dạng chuỗi. Nếu mục tiêu là chọn locale theo cơ chế đàm phán ngôn ngữ của Spring, `Locale` hoặc `LocaleResolver` thường phù hợp hơn việc tự tách chuỗi `Accept-Language`.

### Một header có nhiều giá trị

Header có thể có nhiều dòng cùng tên hoặc biểu diễn một danh sách bằng dấu phẩy. Spring hỗ trợ `String[]`, `List<String>` cho trường hợp có thể chuyển đổi; ví dụ endpoint `/api/accept` trên có thể nhận:

~~~bash
curl -s -H "Accept: application/json, text/plain" http://localhost:8080/api/accept
~~~

Kết quả danh sách minh họa:

~~~json
["application/json","text/plain"]
~~~

Tuy nhiên **không phải mọi header HTTP đều được phép ghép hoặc tách đơn giản bằng dấu phẩy**; cú pháp từng header có thể có dấu nháy, tham số hoặc quy tắc đặc biệt. Với dữ liệu cần phân tích chính xác, dùng API chuyên biệt như `HttpHeaders.getAccept()` thay vì tự split chuỗi.

### Lấy tất cả header

~~~java
@GetMapping("/header-count")
public int headerCount(@RequestHeader HttpHeaders headers) {
    return headers.size();
}
~~~

Với tham số `@RequestHeader` không có tên, Spring có thể bind cả bộ header vào `HttpHeaders`, `Map<String, String>` hoặc `MultiValueMap<String, String>`:

- `Map<String, String>`: biểu diễn một giá trị cho mỗi tên; có thể mất thông tin nếu header có nhiều giá trị.
- `MultiValueMap<String, String>`: giữ danh sách giá trị cho mỗi tên.
- `HttpHeaders`: API HTTP chuyên dụng, có phương thức như `getFirst`, `getAccept`, `getContentType`; thường là lựa chọn rõ ràng nhất khi cần nhiều header.

Đọc mọi header không có nghĩa là nên **trả hoặc log nguyên bộ**: các header như `Authorization` hay `Cookie` có thể chứa bí mật.

## 5. Đọc cookie và hiểu những gì server thực sự nhận

Ví dụ trình duyệt gửi:

~~~http
Cookie: theme=dark; visitCount=7
~~~

Thì `@CookieValue("theme") String theme` lấy `dark`, `@CookieValue("visitCount") Integer visits` lấy số `7`. Trong Spring MVC dùng Servlet, bạn cũng có thể khai báo tham số kiểu `jakarta.servlet.http.Cookie` để đọc `getName()`, `getValue()`, `getPath()`… Tuy nhiên chỉ các thuộc tính thật sự có sẵn trong **cookie request** mới đáng tin để quan sát; các thuộc tính cấu hình lúc server gửi `Set-Cookie` (như `HttpOnly`, `Secure`, `SameSite`) **không được gửi lại trong header `Cookie`** của request.

Server thường cấp cookie qua response, ví dụ:

~~~http
HTTP/1.1 200 OK
Set-Cookie: theme=dark; Path=/; HttpOnly; Secure; SameSite=Lax
~~~

Ở request sau đủ điều kiện, client chỉ gửi cặp tên–giá trị như `Cookie: theme=dark`. `Secure` yêu cầu gửi cookie qua kết nối an toàn theo quy tắc trình duyệt; `HttpOnly` hạn chế truy cập từ JavaScript; `SameSite` ảnh hưởng việc đính kèm cookie trong ngữ cảnh cross-site. Những thuộc tính này cần thiết kế ở **lúc thiết lập cookie**, không cấu hình bằng `@CookieValue`.

Cookie có thể vắng mặt do chưa được set, hết hạn, không khớp `Domain`/`Path`, chính sách `SameSite`, hoặc yêu cầu credentials của trình duyệt. Không kết luận “Spring đọc lỗi” trước khi kiểm tra tab Network của trình duyệt xem request có thật sự chứa header `Cookie` hay không.

## 6. Quyết định `required`, `defaultValue` và xử lý lỗi

| Tình huống | Khai báo phù hợp | Kết quả |
| --- | --- | --- |
| Header bắt buộc | `@RequestHeader("X-Request-Id") String id` | Thiếu → thường 400 |
| Header tùy chọn | `@RequestHeader(name="X-Retry-Count", required=false) Integer count` | Thiếu → `null` |
| Header có fallback | `@RequestHeader(name="X-Source", defaultValue="web") String source` | Thiếu/rỗng → `web` |
| Cookie bắt buộc | `@CookieValue("consent") String consent` | Thiếu → thường 400 |
| Cookie tùy chọn | `@CookieValue(name="theme", required=false) String theme` | Thiếu → `null` |
| Cookie có fallback | `@CookieValue(name="theme", defaultValue="light") String theme` | Thiếu/rỗng → `light` |
| Giá trị không chuyển kiểu được | `@CookieValue("visitCount") Integer n` nhận `abc` | Thường 400 do lỗi conversion |

Các ngoại lệ thường gặp ở Spring MVC: `MissingRequestHeaderException` và `MissingRequestCookieException` khi thiếu dữ liệu bắt buộc; lỗi chuyển kiểu như `MethodArgumentTypeMismatchException` khi chuỗi không hợp lệ. Bạn có thể dùng `@ControllerAdvice`/`@ExceptionHandler` nếu API cần cấu trúc lỗi tùy chỉnh. **Đừng biến mặc định 400 thành 200 chỉ để che lỗi client.**

Nếu muốn giá trị số mặc định, cấu hình trực tiếp `defaultValue = "0"`; Spring sẽ chuyển chuỗi `"0"` sang `Integer`. Không dùng `required=false int count` vì `int` không nhận `null`.

## 7. Kiểm thử bằng MockMvc

Với dependency `spring-boot-starter-test` trong dự án Spring Boot 3.x, thêm file **`src/test/java/com/example/demo/HeaderCookieControllerTest.java`**:

~~~java
package com.example.demo;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HeaderCookieController.class)
class HeaderCookieControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void readsHeaderAndCookies() throws Exception {
        mockMvc.perform(get("/api/inspect")
                        .header("X-Request-Id", "req-42")
                        .header("X-Retry-Count", "3")
                        .cookie(new Cookie("theme", "dark"),
                                new Cookie("visitCount", "7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-42"))
                .andExpect(jsonPath("$.retryCount").value(3))
                .andExpect(jsonPath("$.theme").value("dark"))
                .andExpect(jsonPath("$.visitCount").value(7));
    }

    @Test
    void appliesDefaultsWhenMissing() throws Exception {
        mockMvc.perform(get("/api/inspect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("anonymous"))
                .andExpect(jsonPath("$.theme").value("light"));
    }

    @Test
    void rejectsMissingRequiredValues() throws Exception {
        mockMvc.perform(get("/api/mandatory"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidNumber() throws Exception {
        mockMvc.perform(get("/api/inspect")
                        .header("X-Retry-Count", "abc"))
                .andExpect(status().isBadRequest());
    }
}
~~~

Chạy bằng:

~~~bash
./mvnw test
~~~

`MockMvc` xây dựng request giả lập đủ header/cookie và kiểm tra HTTP status, giá trị JSON; không cần mở server thật. Kiểm thử cả trường hợp có giá trị, thiếu giá trị tùy chọn, thiếu giá trị bắt buộc và lỗi chuyển kiểu để tránh nhầm lẫn giữa **fallback** và **bad request**.

## 8. Bảo mật và lỗi triển khai thường gặp

1. **Không tin header/cookie như bằng chứng danh tính.** Client có thể tự gửi `X-User-Id` hoặc chỉnh cookie `role=admin`. Để xác thực/phân quyền, dùng Spring Security và `Principal`/`Authentication`; session ID hoặc token phải được xác minh tại server.
2. **Không log dữ liệu nhạy cảm.** `Authorization`, cookie phiên, access token và thông tin nhận diện không nên được in vào log hay trả về trong response ví dụ thực tế. Cookie giá trị nhạy cảm nên cân nhắc `HttpOnly`, `Secure`, `SameSite` và thời hạn phù hợp; `HttpOnly` không tự loại bỏ CSRF.
3. **Phân biệt cookie gửi đến và cookie đặt đi.** `@CookieValue` đọc request. Muốn set cookie, sử dụng response `Set-Cookie` (ví dụ `ResponseCookie`/HTTP response header), kiểm soát `Path`, `Domain`, `Max-Age`, `Secure`, `HttpOnly`, `SameSite`.
4. **Không giả định header tùy chọn luôn có.** Việc dereference `null` khi dùng `required=false` có thể gây `NullPointerException`. Chọn wrapper/`Optional`/default hợp lý.
5. **Không tự tách mọi header bằng dấu phẩy.** Header phức tạp cần parser theo chuẩn; khi cần multi-value, dùng `HttpHeaders` hoặc `MultiValueMap` và API chuyên biệt.
6. **Không nhầm thiếu dữ liệu với dữ liệu sai định dạng.** `required=false` xử lý thiếu, không có nghĩa `"abc"` hợp lệ đối với `Integer`. Tùy yêu cầu API, giữ 400 và cung cấp thông báo lỗi rõ ràng.
7. **Không phụ thuộc vào header tự do từ reverse proxy.** Header như `X-Forwarded-For` chỉ nên tin theo cấu hình proxy đáng tin và chính sách của hệ thống; chúng không mặc nhiên là địa chỉ IP thật của người gọi.
8. **Không ép browser gửi cookie khi chính sách chặn.** Cần kiểm tra HTTPS, domain/path, SameSite và credentials với các request từ frontend. Kiểm tra request thực tế trước khi sửa controller.

## 9. Checklist áp dụng nhanh

- Nếu cần **metadata HTTP**, dùng `@RequestHeader`; nếu cần **cookie đã được client gửi**, dùng `@CookieValue`.
- Xác định chính xác tên, kiểu Java đích, có bắt buộc hay không và cần fallback hay không.
- Với nhiều header, chọn `HttpHeaders` hoặc `MultiValueMap`; không làm mất các giá trị lặp.
- Kiểm thử cả dữ liệu hợp lệ, thiếu và không chuyển kiểu được; không dùng header/cookie chưa xác minh để quyết định phân quyền.

## Tham khảo chính thức

- [Spring Framework — `@RequestHeader`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestheader.html)
- [Spring Framework — `@CookieValue`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/cookievalue.html)
- [Spring Framework — Controller Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="controller-inputs-004">@RequestBody, HttpEntity và đọc nội dung yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# @RequestBody, HttpEntity và đọc nội dung yêu cầu trong Spring MVC

## 1. Mục tiêu và phạm vi

Sau bài này, bạn có thể đọc và giải thích body của một HTTP request trong Spring MVC, chọn đúng giữa `@RequestBody` và `HttpEntity<T>`, hiểu vai trò của `HttpMessageConverter`, kiểm soát `Content-Type` và validation, đồng thời biết khi nào cần đọc trực tiếp `HttpServletRequest`. Bài tập trung vào **Spring MVC chạy trên Servlet stack**; các cơ chế reactive của Spring WebFlux có API xử lý body khác.

Một request HTTP có ba phần thường gặp:

```http
POST /api/orders HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json
X-Trace-Id: trace-42

{"sku":"JAVA-17","quantity":2}
```

- **Request line** xác định phương thức và URL.
- **Headers** cung cấp siêu dữ liệu. `Content-Type` mô tả **định dạng body gửi lên**; `Accept` mô tả **định dạng response client mong muốn**.
- **Body** là dữ liệu sau dòng trống ngăn headers và body. Trong ví dụ trên, body là JSON; giá trị `sku` và `quantity` nằm trong body, không phải query string.

`@RequestBody` giải mã body thành tham số Java. `HttpEntity<T>` gom body đã giải mã và headers vào một đối tượng. Cả hai thường dựa trên hệ thống HTTP message converters của Spring MVC.

## 2. Cơ chế Spring MVC đọc body

Với controller sau:

```java
@PostMapping("/orders")
public void create(@RequestBody OrderRequest request) {
    // request đã được đọc và chuyển đổi trước khi method được gọi
}
```

Luồng xử lý về nguyên tắc:

```text
Client gửi bytes + HTTP headers
  -> Servlet container tạo HttpServletRequest
  -> DispatcherServlet chọn controller/method theo route
  -> HandlerAdapter / argument resolver xử lý tham số
  -> Chọn HttpMessageConverter phù hợp kiểu Java và media type
  -> Converter đọc body, ví dụ JSON -> OrderRequest
  -> (Nếu có @Valid) kiểm tra ràng buộc
  -> Gọi controller method với đối tượng Java
  -> Chuyển đổi response body bằng converter thích hợp
```

`HttpMessageConverter` là giao diện đọc/ghi nội dung HTTP theo kiểu dữ liệu và media type. Các lựa chọn thông dụng:

| Đầu vào HTTP | Kiểu tham số | Cách đọc thường gặp |
| --- | --- | --- |
| `application/json` | DTO/record Java | Jackson JSON converter khi có thư viện phù hợp |
| `text/plain` | `String` | String converter |
| `application/octet-stream` | `byte[]` | Byte-array converter |
| JSON array | `List<MyDto>` | JSON converter dựa vào generic type |

Spring chỉ chuyển đổi được khi có converter hỗ trợ cả kiểu dữ liệu đích lẫn media type. `Content-Type: application/json` **không tự biến JSON thành object** nếu ứng dụng không có converter JSON thích hợp. Trong ứng dụng Spring Boot Web thông thường, starter Web cung cấp cấu hình JSON phổ biến; Spring Framework thuần cần tự đảm bảo các phụ thuộc và cấu hình tương ứng.

Lưu ý rằng `HttpMessageConverter` và Jackson xử lý ánh xạ dữ liệu; validation là **bước riêng** sau khi tạo object. Một JSON parse được vẫn có thể vi phạm `@NotBlank` hoặc `@Min`.

## 3. Dùng @RequestBody

```java
@PostMapping(path = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<OrderResponse> create(
        @Valid @RequestBody OrderRequest request) {
    // ...
}
```

Những điểm cần nắm:

- `@RequestBody` đánh dấu **toàn bộ body** cần được ánh xạ vào tham số. Nó không hoạt động như `@RequestParam` (query/form parameter) hay `@PathVariable` (đoạn đường dẫn).
- Với DTO/record, JSON field thường được ánh xạ theo tên thành property hoặc constructor/record component tương ứng. Kiểu dữ liệu không hợp lệ hoặc JSON lỗi cú pháp có thể gây `HttpMessageNotReadableException` và response `400 Bad Request` theo cấu hình mặc định.
- Thuộc tính `required` mặc định là `true`. Nếu body bắt buộc mà request không cung cấp body hợp lệ, Spring thông thường trả `400` trước khi gọi controller. `@RequestBody(required = false)` cho phép không có body, do đó method phải xử lý trường hợp giá trị `null`. Đừng dùng chế độ tùy chọn để vô tình bỏ qua dữ liệu bắt buộc về nghiệp vụ.
- `@Valid` từ `jakarta.validation.Valid` kích hoạt Bean Validation cho object phù hợp khi provider validation hiện diện. Với kiểu tham số DTO thông thường, lỗi ràng buộc thường gây `MethodArgumentNotValidException` và `400`; trong một số chữ ký có method validation, `HandlerMethodValidationException` có thể được dùng thay thế.
- `consumes = "application/json"` ràng buộc định dạng request ở bước chọn handler. Gửi `text/plain` đến endpoint chỉ nhận JSON thường dẫn đến `415 Unsupported Media Type`. `produces` mô tả response media type; client gửi `Accept` không được hỗ trợ có thể nhận `406 Not Acceptable`.
- Trong mã ứng dụng thực, ưu tiên request DTO được thiết kế riêng. Không bind trực tiếp JSON không tin cậy vào entity persistence có trường mà client không được phép điều khiển.

### Một body, nhiều cách biểu diễn

```java
@PostMapping(path = "/payload/text", consumes = MediaType.TEXT_PLAIN_VALUE)
public String text(@RequestBody String body) {
    return "length=" + body.length();
}

@PostMapping(path = "/payload/binary",
             consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public int binary(@RequestBody byte[] body) {
    return body.length;
}
```

Đây là **hai method minh họa** để đặt trong một `@RestController` có mapping phù hợp, chưa phải chương trình Java độc lập. `String` là văn bản đã giải mã bằng charset do converter áp dụng; `byte[]` phù hợp khi cần byte gốc. `String.length()` đếm đơn vị mã UTF-16, không đếm số byte hay luôn bằng số ký tự người dùng nhìn thấy.

## 4. HttpEntity<T>: body kèm headers

`HttpEntity<T>` là đối tượng chứa **request body** kiểu `T` và `HttpHeaders`. Ví dụ:

```java
@PostMapping(path = "/orders/entity",
             consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<String> inspect(HttpEntity<OrderRequest> entity) {
    OrderRequest body = entity.getBody();
    String traceId = entity.getHeaders().getFirst("X-Trace-Id");
    // Xử lý body và header...
    return ResponseEntity.ok(traceId == null ? "missing" : traceId);
}
```

Trong Spring MVC, `HttpEntity<OrderRequest>` ở tham số controller được framework resolve và đọc body thông qua message converters, tương tự `@RequestBody OrderRequest`, đồng thời giữ headers. Không cần đặt thêm `@RequestBody` lên cùng tham số `HttpEntity<T>`. `getBody()` có thể là `null` khi request không có body; kiểm tra trước khi truy cập. Nếu cần xác thực dữ liệu, đừng giả định Bean Validation được thực hiện chỉ vì body nằm trong `HttpEntity`: hãy thiết kế luồng validation tường minh, hoặc dùng `@Valid @RequestBody` khi phù hợp.

| Nhu cầu | Tham số nên chọn |
| --- | --- |
| Nhận JSON thành DTO, có `@Valid` | `@Valid @RequestBody MyDto body` |
| Nhận DTO và đọc nhiều request headers như một gói | `HttpEntity<MyDto> entity` |
| Chỉ lấy một header | `@RequestHeader("X-Trace-Id") String traceId` cùng `@RequestBody` |
| Cần điều khiển trực tiếp Servlet body stream | `HttpServletRequest` và `getInputStream()` |
| Nhận form fields kiểu `application/x-www-form-urlencoded` | Thường dùng `@RequestParam` hoặc `@ModelAttribute` |

**Đừng nhầm `HttpEntity<T>` với `ResponseEntity<T>`:** `HttpEntity` mô hình hóa body + headers; `ResponseEntity` kế thừa ý tưởng đó và thêm HTTP status cho **response**. Controller có thể nhận `HttpEntity<RequestDto>` và trả `ResponseEntity<ResponseDto>`.

## 5. Ví dụ đầy đủ, chạy được: Spring MVC + Java 17

Ví dụ sử dụng **Spring Boot 3.3.5 (Spring Framework 6.x)** và Java 17 để có một ứng dụng MVC nhỏ. Spring Boot chỉ giúp bootstrap, tự cấu hình Jackson và validation; API controller là của Spring MVC. Dùng Maven và cấu trúc:

```text
web-body-demo/
├── pom.xml
└── src/main/java/demo/webbody/
    ├── WebBodyApplication.java
    └── OrderController.java
```

**`pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>web-body-demo</artifactId>
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
```

**`src/main/java/demo/webbody/WebBodyApplication.java`**

```java
package demo.webbody;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebBodyApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebBodyApplication.class, args);
    }
}
```

**`src/main/java/demo/webbody/OrderController.java`**

```java
package demo.webbody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record OrderRequest(@NotBlank String sku,
                               @Min(1) int quantity) {}

    public record OrderResponse(long id, String sku, int quantity) {}

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody OrderRequest request) {
        OrderResponse result =
                new OrderResponse(7L, request.sku(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping(path = "/entity",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> inspect(HttpEntity<OrderRequest> entity) {
        OrderRequest request = entity.getBody();
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }
        String traceId = entity.getHeaders().getFirst("X-Trace-Id");
        String value = traceId == null ? "missing" : traceId;
        return ResponseEntity.ok("sku=" + request.sku() + ", trace=" + value);
    }

    @PostMapping(path = "/raw",
                 consumes = MediaType.TEXT_PLAIN_VALUE,
                 produces = MediaType.TEXT_PLAIN_VALUE)
    public String raw(@RequestBody String body) {
        return "length=" + body.length();
    }

    @PostMapping(path = "/bytes",
                 consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Integer> bytes(@RequestBody byte[] body) {
        return Map.of("bytes", body.length);
    }
}
```

Hai `record` được khai báo lồng trong `OrderController`, nhưng vẫn là kiểu Java có thể được Jackson ánh xạ. Dùng `@NotBlank` cho SKU và `@Min(1)` cho số lượng. `id = 7` là giá trị minh họa cố định, không có cơ sở dữ liệu hoặc tạo đơn hàng thực. Endpoint `/entity` chỉ minh họa truy cập headers/body; nó **không có `@Valid`**, vì vậy ví dụ không hứa validation tương tự endpoint `create`.

Chạy từ thư mục có `pom.xml`:

```bash
mvn spring-boot:run
```

Khi ứng dụng đã mở cổng mặc định `8080`, gọi thử trong Bash (Linux/macOS/Git Bash):

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"sku":"JAVA-17","quantity":2}'
```

Kết quả chính (bỏ các HTTP headers thay đổi theo môi trường):

```http
HTTP/1.1 201
Content-Type: application/json

{"id":7,"sku":"JAVA-17","quantity":2}
```

Thử `HttpEntity` với request header riêng:

```bash
curl -i -X POST http://localhost:8080/api/orders/entity \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/plain' \
  -H 'X-Trace-Id: trace-42' \
  -d '{"sku":"SPRING","quantity":1}'
```

Response body:

```text
sku=SPRING, trace=trace-42
```

Thử nhận văn bản và bytes:

```bash
curl -s -X POST http://localhost:8080/api/orders/raw \
  -H 'Content-Type: text/plain' --data-binary 'hello'
curl -s -X POST http://localhost:8080/api/orders/bytes \
  -H 'Content-Type: application/octet-stream' --data-binary 'ABC'
```

Lần lượt nhận:

```text
length=5
{"bytes":3}
```

Trên PowerShell, có thể gửi JSON bằng lệnh một dòng:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/orders' -ContentType 'application/json' -Body '{"sku":"JAVA-17","quantity":2}'
```

Các số liệu trên cố định do dữ liệu và cách xử lý của ví dụ; header như `Date`, `Transfer-Encoding` và định dạng thân lỗi mặc định có thể khác nhau tùy cấu hình.

## 6. Nếu cần đọc trực tiếp HttpServletRequest

Trong Servlet MVC, `HttpServletRequest.getInputStream()` cung cấp stream bytes của body; `getReader()` cung cấp cách đọc ký tự. Đọc trực tiếp hữu ích khi xử lý định dạng tùy biến, kiểm tra chữ ký trên **raw bytes**, hoặc có yêu cầu đặc biệt với stream. Ví dụ **method thay thế** có thể thêm vào controller; đây là đoạn mã minh họa, cần imports `jakarta.servlet.http.HttpServletRequest`, `java.io.IOException` và `java.io.InputStream`:

```java
@PostMapping(path = "/stream",
             consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseEntity<String> readStream(
        HttpServletRequest request) throws IOException {
    try (InputStream input = request.getInputStream()) {
        byte[] data = input.readAllBytes();
        return ResponseEntity.ok("bytes=" + data.length);
    }
}
```

**Chú ý:** `readAllBytes()` nạp toàn bộ dữ liệu vào RAM, chỉ hợp lý khi đã giới hạn kích thước request. Body thông thường là stream được tiêu thụ một lần; không nên đồng thời đọc bằng `@RequestBody` và tự gọi `getInputStream()` cho cùng dữ liệu. Filter/interceptor đã đọc stream trước đó cũng có thể làm controller thấy body rỗng. `ContentCachingRequestWrapper` chỉ cache dữ liệu **đã được đọc**, không tự động làm body trở thành stream có thể đọc lại vô hạn. Với tải lên tệp lớn, hãy cân nhắc giải pháp upload/streaming phù hợp, tránh biến mọi nội dung thành một `byte[]` lớn.

Khi đọc JSON thông thường, `@RequestBody` tốt hơn vì giải quyết chuyện ánh xạ và lỗi định dạng; chỉ xuống mức Servlet API khi cần kiểm soát bytes thật sự.

## 7. Phân biệt JSON body, query, form và multipart

Đây là các nguồn dữ liệu khác nhau:

| Request gửi lên | Spring MVC thường dùng | Ví dụ |
| --- | --- | --- |
| `/orders?limit=10` | `@RequestParam int limit` | Query parameter |
| `/orders/123` | `@PathVariable long id` | Path segment |
| `Content-Type: application/json` | `@RequestBody OrderRequest dto` | JSON body |
| `Content-Type: application/x-www-form-urlencoded` | `@RequestParam` hoặc `@ModelAttribute` | Form fields |
| `Content-Type: multipart/form-data` | `@RequestPart`, `MultipartFile` hoặc form binding | Multipart parts |

Theo lưu ý của Spring MVC, request form `application/x-www-form-urlencoded` nên xử lý dưới dạng tham số/form binding; Servlet API có thể parse body khi truy cập request parameters, khiến việc đọc body thêm lần nữa thiếu tin cậy. `@RequestPart` hữu ích khi một phần multipart chứa JSON và cần converter riêng cho phần đó.

`@RequestBody` thường dùng cho POST, PUT, PATCH có body; HTTP không buộc annotation này chỉ thuộc ba phương thức đó, nhưng thiết kế API cần tôn trọng ngữ nghĩa của từng method.

## 8. Lỗi thường gặp và cách chẩn đoán

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra/xử lý |
| --- | --- | --- |
| `400 Bad Request`, controller chưa chạy | JSON sai, body bắt buộc bị thiếu, không ánh xạ được kiểu, hoặc validation thất bại | Kiểm tra raw body, DTO/record, exception thực tế và constraint |
| `415 Unsupported Media Type` | `Content-Type` không phù hợp `consumes` hoặc converter | Gửi đúng media type, đăng ký converter nếu cần |
| `406 Not Acceptable` | `Accept` không phù hợp response có thể tạo | Kiểm tra `produces` và response converter |
| Field là `null` hoặc giá trị mặc định | JSON thiếu field, tên property không khớp, hoặc primitive có giá trị mặc định | Kiểm tra JSON và thêm ràng buộc đúng mục tiêu |
| `@Valid` không có tác dụng | Thiếu Bean Validation provider/starter hoặc validation không áp dụng ở luồng này | Bổ sung dependency và đặt validation đúng trên DTO |
| Body rỗng dù client đã gửi | Stream bị filter/handler khác đọc trước | Rà soát các thành phần can thiệp request |
| Dữ liệu nhị phân bị hỏng | Đọc bytes bằng `String` và mã hóa/giải mã sai charset | Dùng `byte[]` hoặc stream cho dữ liệu binary |

Khi `@Valid @RequestBody` thông thường thất bại, Spring trả `400` theo handler mặc định, nhưng **JSON body của response lỗi không có một dạng duy nhất** giữa mọi phiên bản/cấu hình. Để chuẩn hóa lỗi, ứng dụng có thể dùng `@RestControllerAdvice` / `@ExceptionHandler` hoặc `ResponseEntityExceptionHandler`. Đừng bắt mọi exception và trả `200 OK` vì khiến client không biết request thất bại.

Ngoài ra, cần giới hạn kích thước body ở tầng phù hợp, không ghi log payload nhạy cảm, tránh tin rằng kiểu Java và annotation validation có thể thay thế quy tắc nghiệp vụ hoặc phân quyền.

## 9. Câu hỏi tự kiểm tra

1. Client gửi `Content-Type: text/plain` đến method chỉ định `consumes = "application/json"`. Vì sao method thường không được gọi?
2. Khi nào chọn `HttpEntity<OrderRequest>` thay cho `@RequestBody OrderRequest`? Nếu chỉ cần một header thì còn cách nào ngắn hơn?
3. `@Valid`, Jackson và `HttpMessageConverter` đảm nhiệm ba vai trò khác nhau như thế nào?
4. Vì sao không nên vừa có `@RequestBody String body` vừa tự đọc `request.getInputStream()` của cùng request?
5. Khi nào dùng `@RequestParam`, `@ModelAttribute` hoặc `@RequestPart` thay cho `@RequestBody`?

**Gợi ý đáp án:** (1) Media type không hợp mapping/converter, thường `415`. (2) `HttpEntity` khi cần body kèm headers; chỉ một header có thể thêm `@RequestHeader`. (3) Converter đọc bytes và deserialize, Jackson thực hiện JSON mapping, Bean Validation kiểm tra constraints sau đó. (4) Body stream có thể đã bị tiêu thụ. (5) Query/form/multipart tương ứng với cơ chế binding khác nhau.

## 10. Tài liệu tham khảo chính thức

- [Spring MVC — @RequestBody](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html)
- [Spring MVC — HttpEntity](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/httpentity.html)
- [Spring MVC — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html)
- [Spring MVC — Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)

Các đường dẫn trên trỏ đến tài liệu Spring Framework hiện hành; ví dụ runnable đã cố định Spring Boot 3.3.5 để lệnh Maven có môi trường xác định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="controller-inputs-005">@ModelAttribute và liên kết dữ liệu biểu mẫu</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — @ModelAttribute và liên kết dữ liệu biểu mẫu (form data binding)

## 1. Mục tiêu

Sau bài học, bạn có thể:

- Giải thích `@ModelAttribute` trên **tham số controller**: lấy/tạo đối tượng model rồi liên kết các giá trị của HTTP request vào thuộc tính Java.
- Nhận form HTML (`application/x-www-form-urlencoded`) và query parameters mà không phải đọc từng trường bằng `@RequestParam`.
- Hiểu JavaBean/property binding, constructor binding, nested property, danh sách và chuyển đổi kiểu.
- Kết hợp `@Valid` hoặc `@Validated` với `BindingResult` để xử lý lỗi đầu vào.
- Phân biệt `@ModelAttribute` trên **tham số** và trên **phương thức**; phân biệt `@ModelAttribute`, `@RequestParam`, `@RequestBody`.
- Hạn chế trường được phép bind để tránh client sửa thuộc tính ngoài ý muốn.

Bối cảnh: **Spring MVC (Servlet stack)**, Java 17+; ví dụ thực hành dùng Spring Boot 3.5.0 và Spring Framework 6.2.x. Không nhầm cơ chế MVC với Spring WebFlux.

## 2. Từ form HTML đến đối tượng Java

Một form gửi request như sau:

~~~http
POST /api/register HTTP/1.1
Content-Type: application/x-www-form-urlencoded

fullName=Lan%20Nguyen&email=lan%40example.com&age=22&address.city=Da%20Nang&interests=java&interests=spring
~~~

Đoạn body chứa các cặp `name=value`, có thể lặp key `interests`. Trong Spring MVC, request parameters theo Servlet bao gồm **query string** và **dữ liệu form** mà Servlet giải mã; vì vậy Spring có thể dùng chúng để điền một object:

~~~java
@PostMapping("/register")
public String register(@ModelAttribute("registration") RegistrationForm form) {
    // form.getFullName(), form.getAge(), form.getAddress()...
    return "success";
}
~~~

Đoạn này chỉ minh họa chữ ký handler; để chạy độc lập, xem dự án ở mục 8.

**Luồng xử lý** thường gặp:

1. Spring MVC chọn handler dựa trên URL, HTTP method và các điều kiện mapping.
2. Spring tìm model attribute tên `registration` (nếu đã có trong model/session theo cấu hình) hoặc tạo đối tượng mới.
3. `WebDataBinder` lấy dữ liệu request, liên kết với constructor hoặc các thuộc tính writable và chuyển đổi chuỗi sang kiểu Java.
4. Nếu có `@Valid`/`@Validated`, Spring chạy validation sau khi binding.
5. Spring truyền object và kết quả lỗi (nếu controller khai báo `BindingResult`) vào handler.
6. Handler quyết định trả về view, redirect hoặc response HTTP.

Theo cơ chế Spring MVC hiện đại, giá trị bind có thể đến từ request parameters, URI path variables và headers. Khi trùng tên, request parameter ưu tiên hơn path variable/header; tên header có dấu gạch ngang được chuẩn hóa theo quy tắc binder. Với form thông thường, hãy tập trung vào field name và request parameters, và tránh phụ thuộc ngầm vào nguồn khác.

## 3. Thiết kế form object theo quy tắc JavaBean

~~~java
public class ProfileForm {
    private String displayName;
    private Integer age;

    public ProfileForm() {
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
~~~

Handler:

~~~java
@PostMapping("/profiles")
public String save(@ModelAttribute("profile") ProfileForm form) {
    // Trường displayName và age đã được bind.
    return "profile-success";
}
~~~

Form HTML:

~~~html
<form method="post" action="/profiles">
  <label>Tên <input type="text" name="displayName"></label>
  <label>Tuổi <input type="number" name="age"></label>
  <button type="submit">Lưu</button>
</form>
~~~

`name="displayName"` khớp tên property `displayName`; `name="age"` khớp `age`. Với property binding dựa trên JavaBean, setter cho Spring cách gán giá trị. Thông thường Spring tạo object bằng constructor không tham số rồi gọi các setter phù hợp; Spring còn hỗ trợ **constructor binding** dựa trên tên tham số constructor. Hai chiến lược có thể cùng tham gia vào quá trình binding theo cấu hình mặc định.

Không suy ra rằng annotation `@ModelAttribute` tự tạo bảng cơ sở dữ liệu hoặc lưu thông tin người dùng: nó chỉ chuẩn bị object trong request/model. Các thao tác business và lưu trữ nằm ở service/repository.

### 3.1. Tên model attribute có ý nghĩa gì?

`@ModelAttribute("profile")` đặt tên attribute là `profile`. Tên này dùng để tìm/đưa object vào model, xác định mục tiêu `@InitBinder("profile")`, và gắn BindingResult của đối tượng. **Tên property được bind** vẫn là `displayName`, `age`, v.v.; input HTML không cần tiền tố `profile.` khi bind đối tượng top-level này.

Nếu không chỉ rõ tên, Spring có thể suy ra theo kiểu đối tượng, ví dụ `ProfileForm` → `profileForm`. Dùng tên tường minh khi cấu trúc view và binder cần nhất quán. Với tham số phức tạp không được argument resolver khác xử lý, Spring MVC có thể ngầm coi đó là `@ModelAttribute`; khai báo tường minh giúp rõ ý và an toàn hơn cho ứng dụng native/AOT.

## 4. Nested fields, danh sách và chuyển đổi kiểu

Giả sử form class:

~~~java
public class OrderForm {
    private Customer customer = new Customer();
    private List<String> tags = new ArrayList<>();

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public static class Customer {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
~~~

Cần import `java.util.ArrayList` và `java.util.List`. Form data tương ứng:

~~~text
customer.name=Lan
tags=java
tags=spring
~~~

- `customer.name`: nested property path, được điền vào `orderForm.getCustomer().getName()`.
- Hai trường `tags`: Spring có thể nhận nhiều giá trị của cùng key rồi chuyển sang `List<String>`.
- Với phần tử đối tượng trong list, cú pháp dạng `items[0].name` có thể được binder hỗ trợ nếu cấu trúc object, quyền ghi và cấu hình phù hợp; tránh tạo số chỉ mục quá lớn từ dữ liệu không tin cậy.

Một ví dụ gửi bằng curl:

~~~bash
curl --get 'http://localhost:8080/orders/preview' \
  --data-urlencode 'customer.name=Lan' \
  --data-urlencode 'tags=java' \
  --data-urlencode 'tags=spring'
~~~

Lệnh trên minh họa **cách gửi request parameters**; endpoint `/orders/preview` chưa được cài đặt trong ứng dụng mẫu ở mục 8.

### 4.1. Chuyển đổi kiểu

Data binding sử dụng cơ chế chuyển đổi dữ liệu của Spring:

| Request parameter | Property Java | Kết quả thường gặp |
| --- | --- | --- |
| `age=22` | `Integer age` | Số nguyên 22 |
| `active=true` | `Boolean active` | `true` |
| `birthDate=2026-05-01` | `LocalDate birthDate` với `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` | Ngày 2026-05-01 |
| `age=abc` | `Integer age` | Lỗi type mismatch, ghi trong binding errors |
| `address.city=Hue` | Nested property `address.city` | `address.getCity() == "Hue"` nếu object/binder phù hợp |

Ví dụ ngày:

~~~java
@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
private LocalDate birthDate;
~~~

Cần import `java.time.LocalDate` và `org.springframework.format.annotation.DateTimeFormat`. Với định dạng riêng hoặc kiểu nghiệp vụ, đăng ký `Converter`, `Formatter` hoặc cấu hình `ConversionService` phù hợp. Chuyển đổi kiểu và **validation** là hai bước khác nhau: `age=abc` là lỗi chuyển đổi; `age=17` có thể chuyển đổi thành công nhưng vi phạm quy tắc tuổi tối thiểu.

## 5. @ModelAttribute và validation bằng BindingResult

Khi DTO có constraint Bean Validation:

~~~java
public class SignUpForm {
    @NotBlank
    private String username;

    @Email
    @NotBlank
    private String email;

    // Constructor, getters, setters theo JavaBean...
}
~~~

Một controller có thể nhận object và lỗi ngay trong cùng handler:

~~~java
@PostMapping("/signup")
public String signup(
        @Valid @ModelAttribute("signUp") SignUpForm form,
        BindingResult errors) {
    if (errors.hasErrors()) {
        return "signup-form";
    }
    return "signup-success";
}
~~~

Phần `SignUpForm` và các view ở đoạn này là minh họa; bản thực hành hoàn chỉnh nằm ở mục 8.

**Quy tắc bắt buộc:** `BindingResult` (hoặc `Errors`) phải **đứng ngay sau** đối số `@ModelAttribute` mà nó đại diện. Không đặt `Model`, `@RequestParam` hoặc tham số khác chen giữa. Ví dụ sau sai thứ tự:

~~~java
// Sai: Model chen giữa object đã validate và BindingResult.
public String signup(@Valid @ModelAttribute("signUp") SignUpForm form,
                     Model model,
                     BindingResult errors) { /* ... */ }
~~~

Khi khai báo liền kề, handler có thể đọc cả lỗi chuyển đổi và lỗi validation. Nếu không có `BindingResult` để xử lý, quá trình phân giải đối số có thể ném `MethodArgumentNotValidException`; khi validation cấp phương thức áp dụng, loại exception có thể khác (như `HandlerMethodValidationException`). Không coi mọi lỗi form mặc nhiên trả cùng một chuỗi hay cấu trúc JSON: cách trả response phụ thuộc xử lý exception của ứng dụng.

Các API hữu ích của `BindingResult`:

- `hasErrors()`: có lỗi nào không?
- `hasFieldErrors("email")`: riêng trường email có lỗi không?
- `getFieldErrors()`: danh sách lỗi theo trường.
- `getFieldValue("age")`: giá trị được binder ghi nhận cho trường (có thể là rejected value).
- `getSuppressedFields()`: các field bị binder loại ra khi cấu hình giới hạn trường.

### 5.1. Trong giao diện HTML

Ví dụ luồng sử dụng view template:

~~~java
@Controller
@RequestMapping("/signup")
class SignupPageController {
    @GetMapping
    String showForm(Model model) {
        model.addAttribute("signUp", new SignUpForm());
        return "signup-form";
    }

    @PostMapping
    String submit(@Valid @ModelAttribute("signUp") SignUpForm form,
                  BindingResult errors) {
        if (errors.hasErrors()) {
            return "signup-form";
        }
        return "redirect:/signup/success";
    }
}
~~~

Để chạy đoạn này cần lớp `SignUpForm` đầy đủ, engine/template view tương ứng, định nghĩa view `signup-form` và đường dẫn `/signup/success`. Đây là **sơ đồ luồng MVC có view**, không phải một chương trình Java độc lập.

Template engine (ví dụ Thymeleaf) có thể render lại dữ liệu người dùng nhập và thông báo lỗi từ model; trong Spring MVC, `BindingResult` cũng được cung cấp trong model theo tên quy ước. Lưu ý xử lý CSRF khi POST từ form trình duyệt nếu ứng dụng bật Spring Security.

## 6. @ModelAttribute trên method: chuẩn bị dữ liệu model

Annotation này có **hai vai trò** cần phân biệt:

| Vị trí | Ví dụ | Vai trò |
| --- | --- | --- |
| Tham số handler | `submit(@ModelAttribute("profile") ProfileForm form)` | Lấy/tạo model object, bind request values vào object |
| Phương thức controller hoặc `@ControllerAdvice` | `@ModelAttribute("countries") List<String> countries()` | Đưa dữ liệu sẵn vào model trước handler để view dùng |
| Trên request-handling method | `@ModelAttribute("profile") @GetMapping ...` | Diễn giải giá trị trả về như model attribute thay vì kiểu trả về mặc định |

Ví dụ model cho danh sách quốc gia:

~~~java
@Controller
class ProfilePageController {
    @ModelAttribute("countries")
    public List<String> countries() {
        return List.of("VN", "SG", "JP");
    }

    @GetMapping("/profiles/new")
    public String show(Model model) {
        model.addAttribute("profile", new ProfileForm());
        return "profile-form";
    }
}
~~~

`countries()` được Spring MVC gọi để chuẩn bị attribute cho các handler thích hợp trong controller; nếu cần áp dụng rộng hơn, có thể đặt method tương tự trong `@ControllerAdvice`. Đoạn này cần các class/view đã được khai báo ở ứng dụng thực, không phải code độc lập. Các method-level `@ModelAttribute` không có nghĩa là gửi dữ liệu trực tiếp trong HTTP response; object trong model được view hoặc handler sử dụng.

**Thứ tự quan trọng:** Model object có thể đã được tạo/sẵn trong model bởi `@ModelAttribute` method trước khi tham số handler được resolve; controller tiếp tục bind request values vào object đó nếu binding được bật.

### 6.1. Tắt property binding khi chỉ muốn lấy object đã chuẩn bị

~~~java
@ModelAttribute("account")
public Account findAccount(@PathVariable String accountId) {
    return accountService.getById(accountId);
}

@PostMapping("/accounts/{accountId}/action")
public String action(@ModelAttribute(value = "account", binding = false) Account account) {
    accountService.performAction(account);
    return "redirect:/accounts";
}
~~~

Ở đây `binding = false` thể hiện object `account` được cung cấp nhưng **không nhận property values từ request** qua annotation này; có ích khi object domain đã được tìm từ DB và không muốn client sửa field. Đoạn minh họa cần kiểu `Account`, service và endpoint tương ứng.

## 7. An toàn dữ liệu: tránh mass assignment/over-posting

Giả sử entity `User` có `name`, `email`, `role`, `isAdmin`. Nếu bind request trực tiếp vào một entity có setter cho tất cả field, client có thể gửi thêm `role=ADMIN` hoặc `isAdmin=true` dù form HTML không hiển thị trường đó.

**Khuyến nghị thực tế:**

1. Thiết kế **form DTO riêng** chỉ có các field client được phép cung cấp. Sau validation, ánh xạ sang entity trong service; role, id, owner, giá tiền hoặc trạng thái nhạy cảm lấy từ server.
2. Khi dùng **property binding**, cấu hình `WebDataBinder#setAllowedFields(...)` cho danh sách trường được phép gán, nhất là với object có thể sở hữu nhiều setter.
3. Với immutable DTO, có thể chọn constructor binding và chủ động thiết kế rõ những tham số được bind; Spring hỗ trợ tên constructor argument và `@BindParam` khi cần tên khác.
4. Luôn kiểm tra quyền nghiệp vụ ở tầng service; chỉ giới hạn binding không thay cho authorization.

Giới hạn property binding theo attribute name:

~~~java
@InitBinder("registration")
public void initRegistrationBinder(WebDataBinder binder) {
    binder.setAllowedFields(
            "fullName", "email", "age", "address.city", "interests");
}
~~~

`@InitBinder` áp dụng trong phạm vi controller (hoặc theo cấu hình advice) và có thể cấu hình binder. `setAllowedFields` hạn chế việc đặt **properties** bằng property binding; không nên cho rằng nó thay thế việc xem xét constructor arguments, kiểm tra quyền hoặc validation. Field không được cho phép có thể bị suppress, và bạn có thể đọc `BindingResult#getSuppressedFields()` nếu cần theo dõi.

## 8. Ví dụ hoàn chỉnh chạy với Spring Boot + Java 17

Ứng dụng REST nhỏ này thực hiện form binding, nested address, danh sách sở thích, chuyển kiểu số, validation và whitelist field. Nó trả JSON để thử nhanh bằng `curl`, không cần template engine.

Cấu trúc:

~~~text
model-attribute-demo/
├── pom.xml
└── src/main/java/demo/
    ├── ModelAttributeDemoApplication.java
    ├── RegistrationForm.java
    └── RegistrationController.java
~~~

### 8.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>model-attribute-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
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

### 8.2. ModelAttributeDemoApplication.java

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ModelAttributeDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModelAttributeDemoApplication.class, args);
    }
}
~~~

### 8.3. RegistrationForm.java

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RegistrationForm {
    @NotBlank
    private String fullName;

    @NotBlank
    @Email
    private String email;

    @NotNull
    @Min(18)
    private Integer age;

    @Valid
    private Address address = new Address();

    private List<String> interests = new ArrayList<>();

    public RegistrationForm() {
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }

    public static class Address {
        @NotBlank
        private String city;

        public Address() {
        }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }
}
~~~

Nhờ `@Valid` trên `address`, các ràng buộc ở object lồng nhau được kiểm tra. Với `Integer age`, `@NotNull` bắt trường vắng mặt, còn `@Min(18)` bắt tuổi dưới 18. Dùng `jakarta.validation` cho Spring Boot 3.x thay vì package `javax.validation` cũ.

### 8.4. RegistrationController.java

~~~java
package demo;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RegistrationController {
    @InitBinder("registration")
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(
                "fullName", "email", "age", "address.city", "interests");
    }

    @PostMapping(
            path = "/register",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> register(
            @Valid @ModelAttribute("registration") RegistrationForm form,
            BindingResult result) {

        if (result.hasErrors()) {
            List<String> errors = result.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .toList();
            return ResponseEntity.badRequest().body(Map.of("errors", errors));
        }

        return ResponseEntity.ok(Map.of(
                "fullName", form.getFullName(),
                "email", form.getEmail(),
                "age", form.getAge(),
                "city", form.getAddress().getCity(),
                "interests", form.getInterests()));
    }
}
~~~

**Vì sao ví dụ hoạt động?**

- Spring nhận request body `application/x-www-form-urlencoded` từ Servlet như request parameters.
- `@ModelAttribute("registration")` tạo/lấy `RegistrationForm`, binder điền `fullName`, `email`, `age`, `address.city`, `interests`.
- `@InitBinder("registration")` giới hạn các property được bind.
- `@Valid` kiểm tra các ràng buộc; `BindingResult` liền sau form cho controller biết field nào sai.
- Khi hợp lệ, `Map` được chuyển thành JSON nhờ `@RestController` và Jackson do Spring Boot starter web cung cấp.

### 8.5. Chạy và kiểm thử

Cần Java 17+ và Maven; trong thư mục `model-attribute-demo`:

~~~bash
mvn spring-boot:run
~~~

Gửi form hợp lệ:

~~~bash
curl -i -X POST 'http://localhost:8080/api/register' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'fullName=Lan Nguyen' \
  --data-urlencode 'email=lan@example.com' \
  --data-urlencode 'age=22' \
  --data-urlencode 'address.city=Da Nang' \
  --data-urlencode 'interests=java' \
  --data-urlencode 'interests=spring'
~~~

Phần body trả về có giá trị tương đương JSON sau (thứ tự key object không được đảm bảo):

~~~json
{
  "fullName": "Lan Nguyen",
  "email": "lan@example.com",
  "age": 22,
  "city": "Da Nang",
  "interests": ["java", "spring"]
}
~~~

Gửi form vi phạm validation:

~~~bash
curl -i -X POST 'http://localhost:8080/api/register' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'fullName=' \
  --data-urlencode 'email=wrong-format' \
  --data-urlencode 'age=17' \
  --data-urlencode 'address.city='
~~~

Kết quả dự kiến: HTTP **400 Bad Request** với JSON chứa `errors` (các trường như `fullName`, `email`, `age`, `address.city`). Thứ tự và ngôn ngữ thông điệp validation tùy cấu hình, vì vậy không cố định nguyên văn.

Gửi sai kiểu dữ liệu:

~~~bash
curl -i -X POST 'http://localhost:8080/api/register' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'fullName=Lan' \
  --data-urlencode 'email=lan@example.com' \
  --data-urlencode 'age=abc' \
  --data-urlencode 'address.city=Hue'
~~~

`age=abc` không chuyển sang `Integer` được nên binder ghi lỗi `typeMismatch`; `BindingResult` giúp trả HTTP 400 thay vì tiếp tục dùng object không hợp lệ.

Với Windows PowerShell, có thể thay `curl` bằng `curl.exe` và đổi cách xuống dòng theo cú pháp PowerShell (ví dụ viết toàn bộ lệnh trên một dòng); dấu `\` nối dòng trong ví dụ trên dành cho shell Unix.

### 8.6. Không gửi JSON bằng cách gọi @ModelAttribute

Request sau **không phù hợp** với endpoint `/api/register` ở trên:

~~~http
POST /api/register HTTP/1.1
Content-Type: application/json

{"fullName":"Lan","age":22}
~~~

Endpoint đặt `consumes = application/x-www-form-urlencoded` nên request `application/json` thường nhận HTTP **415 Unsupported Media Type**. Nếu API cần JSON, hãy đổi handler sang `@RequestBody RegistrationRequest dto` cùng `consumes=application/json` và khai báo DTO phù hợp. Đừng đồng nhất form binding với JSON deserialization.

## 9. Constructor binding và @BindParam

Spring MVC hiện đại cũng hỗ trợ bind trực tiếp các giá trị request vào tham số của constructor, hữu ích với object immutable. Ví dụ minh họa (không thuộc dự án thực hành ở mục 8):

~~~java
import org.springframework.web.bind.annotation.BindParam;

public class SearchForm {
    private final String keyword;
    private final int page;

    public SearchForm(@BindParam("q") String keyword,
                      @BindParam("page") int page) {
        this.keyword = keyword;
        this.page = page;
    }

    public String getKeyword() { return keyword; }
    public int getPage() { return page; }
}
~~~

Request `?q=spring&page=2` có thể cung cấp `keyword="spring"` và `page=2` cho constructor. Cần giữ tên tham số constructor trong bytecode khi dựa vào tên suy luận; `@BindParam` cho phép đặt tên nguồn rõ ràng. Khi chọn immutable object, tránh thiết kế setter/property binding không cần thiết. Không giả định `@InitBinder#setAllowedFields` lọc được mọi constructor argument: hai hình thức binding có phạm vi kiểm soát khác nhau.

## 10. So sánh các annotation thường gặp

| Annotation | Phù hợp với | Ghi nhớ |
| --- | --- | --- |
| `@RequestParam("page") int page` | Một query/form parameter theo tên | Dễ đọc cho input đơn giản |
| `@ModelAttribute("form") FormDto form` | Nhiều trường request map vào object, kể cả nested/list | Có data binding, conversion, validation |
| `@RequestBody FormDto form` | JSON/XML body qua HTTP message converters | Phụ thuộc Content-Type, không phải form-property binding |
| `@PathVariable Long id` | Segment của URL như `/users/42` | Không thay thế cả form |
| `@RequestPart MultipartFile file` | Phần của multipart request, đặc biệt file | Có thể kết hợp với form fields theo cơ chế multipart |

Chọn theo **hình dạng HTTP request** và mục đích của controller. Ví dụ đăng ký từ HTML form thường hợp lý với `@ModelAttribute`; REST API nhận JSON thường hợp lý với `@RequestBody`.

## 11. Lỗi phổ biến và cách xử lý

1. **Tên input không khớp property:** `name="full_name"` không tự bind sang `fullName` của JavaBean. Đặt tên nhất quán hoặc dùng constructor mapping/`@BindParam` khi phù hợp.
2. **Thiếu constructor/setter phù hợp:** Với property binding, Spring cần object writable và property có thể truy cập; với constructor binding, Spring cần nhận diện đúng tham số và chuyển kiểu.
3. **Nested field bị null hoặc không thể tạo:** Đảm bảo nested object có cách tạo/khởi tạo phù hợp, ví dụ `private Address address = new Address()`.
4. **Ngộ nhận `@Valid` tự xử lý giao diện lỗi:** `BindingResult` giúp kiểm tra field lỗi nhưng controller/view phải quyết định cách hiển thị.
5. **Đặt `BindingResult` cách xa form argument:** Điều này có thể gây lỗi chữ ký method hoặc không gắn lỗi vào đúng object.
6. **Bỏ `spring-boot-starter-validation` trong ứng dụng Boot:** Nếu không có Bean Validation provider phù hợp, annotation constraint có thể không được chạy như mong đợi.
7. **Gửi JSON tới endpoint dành cho form:** Kiểm tra `Content-Type`, `consumes`; dùng `@RequestBody` cho JSON.
8. **Bind trực tiếp entity nghiệp vụ:** Client có thể over-post thêm field không hiển thị. Dùng DTO rõ ràng, allowed fields và authorization trong service.
9. **Đọc mã lỗi chuyển kiểu như lỗi nghiệp vụ:** `age=abc` là binding/type mismatch, khác với `age=17` đã chuyển số thành công nhưng không qua `@Min(18)`.
10. **Nhầm model với HTTP session:** Model attribute thường gắn với xử lý request; chỉ khi dùng cơ chế `@SessionAttributes` và quy tắc liên quan mới có lưu xuyên nhiều request theo session.

## 12. Bài tập tự kiểm tra

**Bài 1 — Form object:** Viết `ContactForm` gồm `name`, `email`, `message`; tạo handler POST nhận `@ModelAttribute("contact")`, chỉ ra tên ba input HTML cần gửi.

**Bài 2 — Nested + list:** Thiết kế `BookingForm` có `guest.name` và `preferences` (`List<String>`). Tạo dữ liệu HTTP request có hai preference, giải thích cách Spring điền từng trường.

**Bài 3 — Validate:** Thêm `@NotBlank` cho `name`, `@Email` cho `email`, khai báo `@Valid` và `BindingResult` đúng vị trí; phân biệt lỗi `age=abc` và `age=17` khi có `@Min(18)`.

**Bài 4 — Security:** Giả sử `AccountEntity` có `role` và `balance` bên cạnh `displayName`. Giải thích vì sao không bind trực tiếp entity, đề xuất `AccountUpdateForm` và tập allowed fields.

**Bài 5 — Annotation:** Với client gửi `Content-Type: application/json`, hãy chọn giữa `@ModelAttribute` và `@RequestBody`; khi nào bạn sẽ dùng `@ModelAttribute` ở mức phương thức thay vì tham số?

*Gợi ý:* Form name khớp JavaBean property; nested field có dấu chấm; repeated key có thể thành list; `BindingResult` đứng ngay sau form; DTO giới hạn field từ client; JSON phù hợp với `@RequestBody`.

## 13. Tài liệu chính thức

- [Spring Framework — MVC `@ModelAttribute` method arguments](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html) — lấy/tạo object, bind, validation, BindingResult, constructor binding.
- [Spring Framework — MVC model methods](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-modelattrib-methods.html) — `@ModelAttribute` trên phương thức chuẩn bị model.
- [Spring Framework — MVC Data Binding](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-data-binding.html) — WebDataBinder và conversion.
- [Spring Framework — MVC Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html) — các quy tắc validation/exception.
- [Spring Framework — MVC `@InitBinder`](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-initbinder.html) — cấu hình binder theo controller/advice.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="controller-inputs-006">@RequestPart và tham số multipart</a>

<details>
<summary>Xem chi tiết</summary>

# @RequestPart và tham số multipart trong Spring MVC

## Mục tiêu

Sau bài này, bạn có thể giải thích `multipart/form-data` gồm những phần nào, khi nào sử dụng `@RequestPart` thay cho `@RequestParam` hay `@ModelAttribute`, nhận file và JSON metadata trong cùng một HTTP request, khai báo phần bắt buộc/tùy chọn, xử lý nhiều file, xác thực đầu vào, cấu hình giới hạn dung lượng và kiểm thử upload bằng MockMvc.

Bài tập trung vào **Spring MVC chạy trên Servlet stack**, sử dụng **Java 17+**, **Spring Boot 3.x**, `jakarta.servlet`. Spring WebFlux có kiểu `FilePart` và cơ chế reactive riêng; không thay `MultipartFile` trong các ví dụ MVC dưới đây bằng `FilePart`.

## 1. Multipart request là gì?

Request có body kiểu `multipart/form-data` gồm nhiều **part**. Mỗi part có header riêng (chẳng hạn `Content-Disposition`, `Content-Type`) và phần dữ liệu riêng. Header `Content-Type` của *request* chứa `boundary` để máy chủ xác định ranh giới giữa các part.

Ví dụ giản lược (chuỗi boundary chỉ là ví dụ):

~~~http
POST /api/uploads HTTP/1.1
Host: localhost:8080
Content-Type: multipart/form-data; boundary=BoundaryDemo

--BoundaryDemo
Content-Disposition: form-data; name="metadata"
Content-Type: application/json

{"title":"Spring MVC","category":"notes"}
--BoundaryDemo
Content-Disposition: form-data; name="file"; filename="note.txt"
Content-Type: text/plain

Hello Spring!
--BoundaryDemo
Content-Disposition: form-data; name="comment"
Content-Type: text/plain

first upload
--BoundaryDemo--
~~~

Trong request này, `metadata`, `file` và `comment` là **tên part**. Một part có thể chứa chuỗi, JSON, tệp nhị phân hoặc nội dung khác. `filename` là thông tin do client gửi kèm, không phải đường dẫn được tin cậy để ghi file.

Ở phía server Spring MVC, multipart request được Servlet container và cơ chế `MultipartResolver` xử lý để controller truy cập từng part. Spring Boot thường tự cấu hình multipart support khi có Spring MVC; nếu bạn tự cấu hình Spring MVC/Servlet, có thể cần khai báo `MultipartResolver` và multipart configuration cho Servlet.

**Lưu ý:** Đừng tự đặt header request `Content-Type: multipart/form-data` mà thiếu `boundary`. Hãy để `curl -F`, trình duyệt `FormData` hoặc HTTP client tạo boundary và body khớp nhau.

## 2. `@RequestPart` đọc gì và binding diễn ra thế nào?

Một endpoint có thể khai báo:

~~~java
@PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public String upload(
        @RequestPart("metadata") DocumentMetadata metadata,
        @RequestPart("file") MultipartFile file) {
    return metadata.title() + ": " + file.getSize();
}
~~~

Đây là đoạn minh họa nằm trong một controller; `DocumentMetadata` cần được định nghĩa riêng.

- `@RequestPart("metadata")` tìm part có **name = metadata** rồi dùng `HttpMessageConverter` phù hợp với `Content-Type` của **part đó** để chuyển body sang `DocumentMetadata` (ví dụ JSON nhờ Jackson).
- `@RequestPart("file") MultipartFile` lấy dữ liệu upload dưới dạng đối tượng Spring `MultipartFile`, cung cấp `getInputStream()`, `getSize()`, `getContentType()`, `getOriginalFilename()`, `isEmpty()` và `transferTo(...)`.
- Cũng có thể nhận `jakarta.servlet.http.Part` với Servlet multipart parsing khi cần API Servlet và header của part.
- `name` và `value` là hai cách chỉ tên part; `@RequestPart("metadata")` là cú pháp gọn của `@RequestPart(name = "metadata")`.
- `required = true` là mặc định; phần bắt buộc bị thiếu thường gây **400 Bad Request** (`MissingServletRequestPartException`). `required = false` cho phép không có part, tham số tham chiếu có thể là `null`; có thể sử dụng `Optional<T>` theo hỗ trợ binding của Spring.
- **Không có thuộc tính `defaultValue` trên `@RequestPart`**. Nếu muốn fallback, hãy xử lý sau khi bind hoặc dùng kiểu cơ chế input phù hợp.

`@RequestPart` rất gần `@RequestBody` về **chuyển đổi payload bằng message converter**, nhưng `@RequestBody` nhắm vào body toàn request; còn `@RequestPart` nhắm vào **một phần cụ thể** bên trong multipart body. Header `Content-Type` của từng part quan trọng: JSON metadata nên đặt `application/json`; gửi JSON dưới dạng `text/plain` không bảo đảm bind được sang DTO JSON.

## 3. So sánh `@RequestPart`, `@RequestParam`, `@ModelAttribute` và `@RequestBody`

| Tình huống | Cách chọn thông dụng | Lý do |
| --- | --- | --- |
| Form upload có trường `description` là chuỗi và một file | `@RequestParam String description`, `@RequestParam MultipartFile file` | Bind tham số form đơn giản; Spring MVC hỗ trợ file multipart với `@RequestParam` |
| Upload file đi cùng part `metadata` mang JSON | `@RequestPart UploadMetadata metadata`, `@RequestPart MultipartFile file` | JSON part được deserialization bằng `HttpMessageConverter` |
| Form HTML nhiều trường được bind thành object | `@ModelAttribute UploadForm form` (có trường `MultipartFile`) | Data binding từ các form field vào thuộc tính của object |
| Request body JSON thuần, không có multipart | `@RequestBody UploadMetadata metadata` | Chuyển đổi body duy nhất của HTTP request |
| Cần API Servlet mức thấp | `@RequestPart Part part` hoặc `HttpServletRequest` | Làm việc trực tiếp với `Part` và metadata theo Servlet API |

Cả `@RequestParam MultipartFile` và `@RequestPart MultipartFile` đều có thể nhận file. **Sự khác nhau rõ nhất là với object JSON trong một part:** `@RequestPart` đọc payload theo media type của part để dùng converter; `@RequestParam` thiên về request parameter và type conversion của form data.

Không nên gửi JSON trong part rồi giả định `@ModelAttribute` tự giải mã JSON ấy như một `@RequestPart` DTO. Ngược lại, nếu frontend gửi các field `title` và `category` thông thường, một DTO `@ModelAttribute` có thể phù hợp hơn việc cố ghép chúng thành JSON.

## 4. Ví dụ Spring Boot 3.x chạy được: JSON metadata + một file

Tạo dự án Maven bằng Spring Initializr, chọn **Java 17 hoặc cao hơn**, **Spring Boot 3.x**, các dependency **Spring Web** và **Validation**; nếu muốn chạy phần kiểm thử bên dưới, thêm **Spring Boot Starter Test**. `spring-boot-starter-web` cung cấp Spring MVC, Tomcat và hỗ trợ JSON; `spring-boot-starter-validation` cung cấp Jakarta Bean Validation.

Cấu trúc tối thiểu:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/UploadController.java
src/main/resources/application.properties
src/test/java/com/example/demo/UploadControllerTest.java
~~~

**`src/main/java/com/example/demo/DemoApplication.java`**

~~~java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
~~~

**`src/main/java/com/example/demo/UploadController.java`**

~~~java
package com.example.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResult> create(
            @Valid @RequestPart("metadata") UploadMetadata metadata,
            @RequestPart("file") MultipartFile file,
            @RequestPart(name = "comment", required = false) String comment) {

        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "File must not be empty");
        }

        UploadResult result = new UploadResult(
                metadata.title(),
                metadata.category(),
                comment,
                file.getSize(),
                file.getContentType());

        // Chỉ báo nhận thành công; ví dụ không lưu file xuống đĩa.
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping(path = "/many",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Long> many(
            @RequestPart("files") List<MultipartFile> files) {

        if (files.isEmpty() || files.stream().anyMatch(MultipartFile::isEmpty)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Files must not be empty");
        }

        return Map.of(
                "count", (long) files.size(),
                "totalBytes",
                files.stream().mapToLong(MultipartFile::getSize).sum());
    }

    public record UploadMetadata(
            @NotBlank String title,
            @NotBlank String category) {
    }

    public record UploadResult(
            String title,
            String category,
            String comment,
            long bytes,
            String mediaType) {
    }
}
~~~

Các điểm cần chú ý:

1. `consumes = MediaType.MULTIPART_FORM_DATA_VALUE` tuyên bố endpoint xử lý request multipart/form-data; request JSON đơn thuần không khớp media type này.
2. Part `metadata` phải có nội dung JSON phù hợp với record `UploadMetadata`. Annotation `@Valid` yêu cầu kiểm tra `@NotBlank`; metadata không hợp lệ thường bị trả **400**.
3. `file` là part bắt buộc về sự hiện diện. Tuy nhiên một part file rỗng **có thể vẫn tồn tại**, nên kiểm tra `file.isEmpty()` riêng.
4. `comment` là part tùy chọn; thiếu part có thể cho `null`. Nếu ứng dụng muốn giá trị `""`, tự chuẩn hóa trong business logic.
5. Kết quả trả về chỉ chứng minh **binding/validation và metadata**; file **chưa được lưu**. Để có API lưu file thực tế, cần storage service, kiểm tra quyền và chính sách an toàn file.
6. `mediaType` từ `file.getContentType()` là metadata do client khai báo, có thể `null` hoặc sai; đừng sử dụng nó như bằng chứng loại tệp.

**`src/main/resources/application.properties`** (giới hạn minh họa)

~~~properties
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=10MB
~~~

Giới hạn `max-file-size` áp dụng mỗi file; `max-request-size` áp dụng toàn request multipart, gồm dữ liệu các part và overhead. Spring Boot thường bật multipart theo mặc định. Giá trị mặc định thông dụng khi không cấu hình là **1MB/file và 10MB/request**, nhưng hãy đối chiếu phiên bản/config triển khai.

Trong thư mục dự án, chạy:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows dùng `mvnw.cmd spring-boot:run`; nếu không có Maven Wrapper, dùng `mvn spring-boot:run` sau khi cài Maven.

Tạo file mẫu `note.txt` chứa chuỗi `Hello Spring!` theo cách phù hợp hệ điều hành rồi gửi request:

~~~bash
curl -i -X POST "http://localhost:8080/api/uploads" \
  -F 'metadata={"title":"Spring MVC","category":"notes"};type=application/json' \
  -F 'file=@note.txt;type=text/plain' \
  -F 'comment=first upload'
~~~

Lệnh trên viết theo cú pháp shell hỗ trợ dấu `\` để xuống dòng (Linux/macOS/Git Bash). Trên PowerShell, chạy `curl.exe` và gõ một dòng hoặc dùng cú pháp nối dòng của PowerShell; dấu nháy và escaping trong `-F` cũng cần theo shell. Đặc biệt, `;type=application/json` gắn media type vào **part metadata**, không phải vào toàn request.

Phản hồi thành công có HTTP status **201 Created**. Body minh họa, giả sử file UTF-8 có đúng 13 byte (chuỗi `Hello Spring!` không có newline):

~~~json
{
  "title": "Spring MVC",
  "category": "notes",
  "comment": "first upload",
  "bytes": 13,
  "mediaType": "text/plain"
}
~~~

Nếu tạo file có newline hoặc nội dung khác, `bytes` sẽ thay đổi; JSON property order không có ý nghĩa. Khi không gửi `comment`, cấu hình Jackson mặc định thường biểu diễn `comment: null`.

## 5. Kết nối frontend bằng `FormData`

Khi dùng trình duyệt, `FormData` tạo multipart body. Ví dụ trong frontend có `fileInput` trỏ đến một `<input type="file">`:

~~~javascript
const file = fileInput.files[0];

const form = new FormData();
form.append("metadata", new Blob(
  [JSON.stringify({ title: "Spring MVC", category: "notes" })],
  { type: "application/json" }
));
form.append("file", file);
form.append("comment", "first upload");

const response = await fetch("/api/uploads", {
  method: "POST",
  body: form
});

if (!response.ok) {
  throw new Error("Upload failed: HTTP " + response.status);
}
console.log(await response.json());
~~~

Dùng `Blob` với `type: "application/json"` bảo đảm Spring thấy metadata part là JSON và có thể chọn converter. Nếu chỉ dùng `form.append("metadata", JSON.stringify(object))`, trình duyệt sẽ gửi **text field**, thường thiếu media type JSON phù hợp và có thể dẫn đến lỗi chuyển DTO.

**Không tự khai báo** `Content-Type` của request khi dùng `fetch` với `FormData`; trình duyệt sẽ tự đặt multipart boundary. Với frontend và backend khác origin, xem xét CORS, cấu hình credentials/cookie và CSRF theo Spring Security; xử lý frontend không thay thế chính sách bảo mật server.

## 6. Một file, nhiều file và Servlet `Part`

Đối với một file, các kiểu tham số phổ biến:

~~~java
@RequestPart("file") MultipartFile file
@RequestParam("file") MultipartFile file
@RequestPart("file") jakarta.servlet.http.Part file
~~~

Đây là **các khai báo tham số**, dùng bên trong một handler method thích hợp. `MultipartFile` là abstraction của Spring; `Part` là API Servlet, có `getInputStream()`, `getSize()`, `getSubmittedFileName()`, `getHeader(...)`. Không dùng `org.springframework.http.codec.multipart.FilePart` trong Spring MVC: đó là abstraction cho WebFlux.

Nếu một tên part xuất hiện nhiều lần, có thể nhận nhiều file qua `List<MultipartFile>` hoặc `MultipartFile[]`. Ví dụ endpoint `/api/uploads/many` ở trên:

~~~bash
curl -i -X POST "http://localhost:8080/api/uploads/many" \
  -F 'files=@first.txt' \
  -F 'files=@second.txt'
~~~

Cả hai part đều dùng **name = files**. Body phản hồi chứa `count = 2` và tổng kích thước byte của hai file. Không nhầm với hai part tên `file1`, `file2`, vì chúng **không tự động** bind vào cùng một tham số `files`.

Muốn nhận các file mang tên khác nhau, có thể dùng `@RequestParam MultiValueMap<String, MultipartFile>` hoặc `Map<String, MultipartFile>` không chỉ định tên tham số (phân biệt: map thường chỉ một giá trị mỗi tên, multivalue map giữ danh sách). Chỉ sử dụng khi API thật sự cho phép tên part động; với API xác định rõ tên, tham số cụ thể dễ kiểm soát hơn.

**Không coi `file.getSize() > 0` là xác minh file hợp lệ.** Một file có thể không rỗng nhưng nội dung, tên, encoding hoặc định dạng vẫn nguy hiểm hoặc sai nghiệp vụ.

## 7. Xử lý lỗi khi thiếu part, sai định dạng hoặc quá lớn

| Tình huống | Nguyên nhân thường gặp | Phản hồi/kết quả |
| --- | --- | --- |
| Thiếu `metadata` hoặc `file` bắt buộc | Tên field sai hoặc client không gửi part | Thường **400**, `MissingServletRequestPartException` |
| Không có `comment` | Được khai báo `required=false` | Controller nhận `null` |
| Metadata không phải JSON hợp lệ | `HttpMessageConverter` đọc JSON thất bại | Thường **400** với lỗi body không đọc được |
| JSON đúng cú pháp nhưng sai `@NotBlank` | Jakarta Bean Validation trên `@Valid @RequestPart` | Thường **400**, `MethodArgumentNotValidException` |
| Metadata JSON gửi với `Content-Type: text/plain` | Không có converter phù hợp cho DTO | Thường **415 Unsupported Media Type** |
| Request dùng `application/json` thay `multipart/form-data` | Không khớp `consumes` của handler | Thường **415 Unsupported Media Type** |
| File part rỗng | Part tồn tại nhưng không có bytes | Ví dụ controller chủ động trả **400** |
| File hoặc toàn request vượt giới hạn | Multipart parser/container từ chối | Lỗi upload/size; **413 Content Too Large** là status hợp lý khi được cấu hình/ánh xạ |
| Request multipart có boundary lỗi | Parser không tách được part | Lỗi parse multipart; status phụ thuộc cấu hình container và error handler |

Các status ở bảng là **hành vi điển hình**, không phải lời hứa về mọi body lỗi trong mọi cấu hình Spring Boot, Servlet container, reverse proxy hoặc custom exception handler. Với upload quá lớn, `MaxUploadSizeExceededException` có thể xuất hiện; HTTP 413 phụ thuộc cách stack xử lý và ánh xạ exception. Cấu hình server/proxy (ví dụ giới hạn body) có thể từ chối trước khi request tới controller.

Nếu muốn chuẩn hóa body lỗi cho API, dùng `@RestControllerAdvice` và ánh xạ chính xác các ngoại lệ cần thiết. Tránh catch chung mọi `Exception` rồi đổi thành 200 hoặc 400, vì như vậy sẽ che lỗi server. Với multipart validation, có thể đặt `BindingResult` ngay sau tham số DTO `@Valid @RequestPart` nếu thật sự cần xử lý lỗi validation tại handler, nhưng nhánh này phải được thiết kế và kiểm thử rõ.

## 8. Kiểm thử `@RequestPart` với MockMvc

Cần `spring-boot-starter-test`. Thêm file **`src/test/java/com/example/demo/UploadControllerTest.java`**:

~~~java
package com.example.demo;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadController.class)
class UploadControllerTest {

    @Autowired
    MockMvc mockMvc;

    private static MockMultipartFile metadata(String json, String contentType) {
        return new MockMultipartFile(
                "metadata",
                "",
                contentType,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "Hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uploadsValidJsonAndFile() throws Exception {
        mockMvc.perform(multipart("/api/uploads")
                        .file(metadata(
                                "{\"title\":\"Spring MVC\",\"category\":\"notes\"}",
                                "application/json"))
                        .file(file())
                        .file(new MockMultipartFile(
                                "comment",
                                "",
                                "text/plain",
                                "first upload".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Spring MVC"))
                .andExpect(jsonPath("$.category").value("notes"))
                .andExpect(jsonPath("$.comment").value("first upload"))
                .andExpect(jsonPath("$.bytes").value(5));
    }

    @Test
    void missingRequiredFileReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/uploads")
                        .file(metadata(
                                "{\"title\":\"Spring MVC\",\"category\":\"notes\"}",
                                "application/json")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidJsonMetadataReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/uploads")
                        .file(metadata("{invalid-json", "application/json"))
                        .file(file()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void beanValidationRejectsBlankTitle() throws Exception {
        mockMvc.perform(multipart("/api/uploads")
                        .file(metadata(
                                "{\"title\":\"\",\"category\":\"notes\"}",
                                "application/json"))
                        .file(file()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsTwoFilesWithSamePartName() throws Exception {
        MockMultipartFile one = new MockMultipartFile(
                "files", "a.txt", "text/plain", new byte[] {1, 2});
        MockMultipartFile two = new MockMultipartFile(
                "files", "b.txt", "text/plain", new byte[] {3});

        mockMvc.perform(multipart("/api/uploads/many")
                        .file(one).file(two))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.totalBytes").value(3));
    }
}
~~~

Chạy test:

~~~bash
./mvnw test
~~~

Kiểm thử chú ý `MockMultipartFile` có tên part đầu tiên: `"metadata"`, `"file"`, `"comment"` phải **khớp chính xác** tên trên annotation. `MockMultipartFile("metadata", "", "application/json", ...)` giúp JSON part được đọc bằng converter tương ứng. Trong test nhiều file, cả hai file phải cùng tên `"files"`.

**Giới hạn của MockMvc:** test chủ yếu xác minh argument resolution, conversion, validation và logic controller. Việc Servlet container xử lý upload thật, lưu tạm, giới hạn body ở reverse proxy và lỗi upload quá lớn nên được kiểm thử tích hợp thêm trong môi trường có server thật.

## 9. Lưu file an toàn trong ứng dụng thật

Ví dụ trên cố ý **không lưu** file. Khi triển khai storage, cần chú ý:

1. **Không nối trực tiếp `getOriginalFilename()` vào đường dẫn đích.** Tên gốc do client kiểm soát, có thể rỗng, có ký tự phân cách đường dẫn hoặc chuỗi `../`. Sinh tên lưu trữ độc lập (ví dụ UUID), dùng thư mục được cấu hình rõ và kiểm soát quyền.
2. **Giới hạn kích thước ở nhiều lớp.** Đặt `spring.servlet.multipart.max-file-size`, `spring.servlet.multipart.max-request-size` phù hợp, và đối chiếu giới hạn Servlet container/reverse proxy. Nếu xử lý ảnh/video hoặc nhiều file, thiết kế timeout và quota hợp lý.
3. **Không chỉ tin MIME type hoặc extension được client khai báo.** Đối chiếu loại nội dung dựa trên dữ liệu thực (đọc magic bytes/parser phù hợp), whitelist định dạng được phép, cân nhắc quét malware khi cần, và giới hạn thao tác giải nén với archive.
4. **Không giữ file tạm như storage lâu dài.** `MultipartFile` có thể được lưu tạm trong bộ nhớ hoặc trên đĩa và thường có vòng đời gắn với request. Nếu cần lưu, đọc stream/copy sang storage được quản lý trong lúc xử lý request; có chính sách dọn dẹp tài nguyên.
5. **Không gọi `getBytes()` với file lớn chỉ để sao chép.** Xem xét `getInputStream()` và streaming/copy có giới hạn để giảm áp lực bộ nhớ; cần quản lý tài nguyên và ngoại lệ I/O.
6. **Bảo vệ endpoint.** Xác thực, phân quyền, quota, logging metadata an toàn, kiểm tra CSRF theo ngữ cảnh trình duyệt; không cho client quyết định storage path hoặc file owner tùy ý.
7. **Trả file tải xuống có kiểm soát.** Nếu phục vụ file người dùng tải lên, thiết lập content type, content disposition và chính sách browser phù hợp để giảm rủi ro nội dung chủ động được thực thi.
8. **Không coi multipart upload là giao dịch nguyên tử.** Nếu đã lưu file rồi ghi database lỗi (hoặc ngược lại), cần có chiến lược rollback/cleanup/compensation để tránh file mồ côi.

Có thể lưu ra file qua `transferTo(...)`, nhưng phương thức này không thay cho việc kiểm soát đường dẫn và validation. Nếu dùng cloud object storage, thiết kế giới hạn/streaming phù hợp SDK và kiểm soát quyền truy cập bucket.

## 10. Các lỗi thường gặp và cách tự chẩn đoán

- **`MissingServletRequestPartException`:** so lại chính xác tên trong `-F "metadata=..."` hoặc `form.append("metadata", ...)` với `@RequestPart("metadata")`; kiểm tra request có đúng multipart và có boundary.
- **Lỗi 415 khi metadata đã là JSON:** nhìn vào `Content-Type` **riêng của metadata part** trong tab Network hoặc HTTP client; dùng `application/json`.
- **DTO null hoặc không được bind như dự kiến:** đối chiếu JSON property names với record/POJO, Jackson và constructor/binding; phân biệt gửi JSON part với nhiều text field.
- **Gửi multipart từ frontend nhưng server không nhận:** đừng tự set `Content-Type` toàn request khi dùng `FormData`; kiểm tra CORS/credentials và giới hạn proxy.
- **File trống:** đối chiếu client chọn file chưa, `MultipartFile.isEmpty()`, cũng như trường hợp file có kích thước 0 hợp lệ theo nghiệp vụ hay không.
- **Lỗi 400 dù có đủ part:** JSON sai, validation thất bại hoặc tên part đúng nhưng media type/nội dung không tương thích; kiểm tra log exception và phản hồi lỗi.
- **Quá giới hạn kích thước:** kiểm tra cả `max-file-size`, `max-request-size` và giới hạn tầng proxy/server; đừng tăng vô hạn chỉ để bỏ lỗi.
- **Nhiều file chỉ nhận một:** chắc rằng gửi lặp cùng tên part `files` và handler nhận `List<MultipartFile>` hoặc kiểu collection thích hợp.

## 11. Checklist chọn và triển khai

1. Xác định request có thật sự là `multipart/form-data` và frontend/client tạo boundary hợp lệ.
2. Với JSON metadata, dùng `@RequestPart` DTO và gắn `Content-Type: application/json` cho **part**; với form text đơn giản, cân nhắc `@RequestParam` hoặc `@ModelAttribute`.
3. Với file, dùng `MultipartFile` hoặc `jakarta.servlet.http.Part`; thiết lập tên part chính xác và `required` phù hợp.
4. Validate dữ liệu JSON, độ rỗng và nội dung file; giới hạn từng file và toàn request; không tin tên/MIME type do client gửi.
5. Kiểm thử request hợp lệ, thiếu part, JSON/validation sai, nhiều file và các giới hạn trong môi trường chạy thật.

## Tài liệu chính thức

- [Spring Framework — Multipart trong Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html)
- [Spring Framework — Controller Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html)
- [Spring Framework — Multipart Resolver](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/multipart.html)
- [Spring Boot — Handling Multipart File Uploads](https://docs.spring.io/spring-boot/how-to/spring-mvc.html#howto.webserver.configure-multipart-file-uploads)
- [Spring Boot — Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="controller-inputs-007">@RequestAttribute, @SessionAttribute và dữ liệu có sẵn trong ngữ cảnh</a>

<details>
<summary>Xem chi tiết</summary>

# @RequestAttribute, @SessionAttribute và dữ liệu có sẵn trong ngữ cảnh Spring MVC

## 1. Tại sao controller có thể nhận dữ liệu dù client không truyền trực tiếp?

Một HTTP request đi qua nhiều thành phần trước khi chạy vào phương thức controller: web server/Servlet container, Servlet Filter, `DispatcherServlet`, `HandlerInterceptor`, rồi đến quá trình Spring MVC chọn và gọi handler. Ở các bước trước controller, ứng dụng có thể đã tạo thông tin nội bộ như mã theo dõi, tenant đã xác định, metadata của request hoặc đối tượng được tải từ session.

Hai annotation trong **Spring MVC (Servlet stack)** giúp controller nhận lại các giá trị **đã tồn tại** ở đúng phạm vi:

- `@RequestAttribute`: đọc một thuộc tính từ **Servlet request hiện tại**; thường được đặt trước đó qua `HttpServletRequest#setAttribute` bởi filter, interceptor hoặc thành phần khác.
- `@SessionAttribute`: đọc một thuộc tính từ **HTTP session liên quan đến client**, thường được đặt qua `HttpSession#setAttribute` ở một request trước hoặc bởi logic quản lý session.

Cả hai là annotation trên **tham số phương thức controller**, không tự động tạo ra giá trị, không đọc query parameter và không tự đọc JSON body. Nếu thuộc tính chưa có, phải xử lý tình huống thiếu theo `required`.

Ví dụ:

```text
Client
  GET /api/context/request
       |
       v
Servlet Filter                request.setAttribute("traceId", "demo-42")
       |
       v
DispatcherServlet
       |
       v
HandlerInterceptor (nếu có)  có thể đặt thêm request attribute
       |
       v
@RequestAttribute("traceId") String traceId
       |
       v
Controller: "traceId=demo-42"
```

Trong luồng khác, session có thể lưu một lựa chọn `language=vi` từ request trước. Request mới gửi cookie xác định session, server tìm lại thuộc tính và `@SessionAttribute("language")` đọc giá trị đó. Request attribute và session attribute độc lập: **cùng một tên key** không có nghĩa là cùng một giá trị.

## 2. Phân biệt các nguồn dữ liệu đầu vào

| Nguồn | Controller lấy bằng | Giá trị đến từ đâu? | Vòng đời điển hình |
| --- | --- | --- | --- |
| Query/form parameter | `@RequestParam("q")` | URL `?q=java` hoặc form body phù hợp | Theo request |
| Path variable | `@PathVariable("id")` | Segment do URL mapping bắt được, ví dụ `/users/7` | Theo request |
| Header | `@RequestHeader("X-Trace-Id")` | HTTP header client/proxy gửi | Theo request |
| Cookie | `@CookieValue("theme")` | Cookie trong request | Theo request/client |
| Body JSON | `@RequestBody Dto` | Payload được `HttpMessageConverter` đọc | Theo request |
| Request attribute | `@RequestAttribute("traceId")` | Object ở `ServletRequest#setAttribute`, thường do thành phần phía server đặt | Theo request |
| Session attribute | `@SessionAttribute("language")` | Object ở `HttpSession#setAttribute` | Qua nhiều request của cùng session |
| Model attribute | `@ModelAttribute`, `Model` | Model của Spring MVC; có thể khởi tạo/bind từ request | Theo luồng MVC, tùy cơ chế model/session |
| Servlet context attribute | `ServletContext#getAttribute` | Trạng thái cấp ứng dụng web do server code đặt | Chia sẻ ở cấp ServletContext |
| Security principal | `Principal`, `Authentication` hoặc `@AuthenticationPrincipal` khi dùng Spring Security | Danh tính đã được xác thực theo cơ chế bảo mật | Theo security context đã thiết lập |

**Điểm dễ nhầm:** `request.getParameter("traceId")` và `request.getAttribute("traceId")` tra **hai kho dữ liệu khác nhau**. Gửi `?traceId=evil` không tự tạo `request` attribute tên `traceId`, và một JSON field `{"language":"vi"}` cũng không tự biến thành `session` attribute.

## 3. `@RequestAttribute`: đọc thuộc tính thuộc request hiện tại

Cú pháp:

```java
@GetMapping("/example")
public String handle(
        @RequestAttribute("traceId") String traceId,
        @RequestAttribute(name = "tenant", required = false) String tenant) {
    return traceId + ":" + (tenant == null ? "unknown" : tenant);
}
```

Các thành phần quan trọng:

- `value` và `name` là alias. Nên **ghi rõ tên** để tránh phụ thuộc cấu hình compiler giữ tên tham số (ví dụ `-parameters`).
- `required` mặc định là `true`. Nếu không có attribute tương ứng, Spring ném ngoại lệ khi resolve tham số, phương thức controller không được gọi. Mã HTTP cuối cùng chịu ảnh hưởng của bộ xử lý ngoại lệ (thông thường lỗi binding sẽ trở thành 400).
- `required = false` cho phép giá trị vắng mặt, thường được truyền là `null` cho reference type; cũng có thể dùng `Optional<T>`.
- Annotation **không có `defaultValue`** như `@RequestParam`. Nếu cần fallback, tự tính trong controller hoặc thành phần cung cấp.
- Tên attribute so khớp tên đã đặt vào request; `@RequestAttribute("tenant")` không đọc `tenantId` chỉ vì kiểu dữ liệu tương ứng.
- Object lấy ra phải phù hợp với kiểu tham số hoặc có cơ chế chuyển kiểu thích hợp. Annotation không biến JSON hay Map thành một đối tượng domain tùy ý.

Nguồn đặt attribute phổ biến:

```java
// Servlet Filter:
request.setAttribute("traceId", traceId);

// HandlerInterceptor#preHandle:
request.setAttribute("tenant", resolvedTenant);

// Bất cứ thành phần phía server có cùng ServletRequest:
String traceId = (String) request.getAttribute("traceId");
request.removeAttribute("traceId");
```

Một attribute có thể được sửa/loại bỏ trong quá trình xử lý request. Nó **không tự tồn tại** ở request HTTP tiếp theo. Forward trong Servlet container có thể cùng dùng một request; redirect là request mới nên request attribute cũ không đi theo.

**Không nhầm request attribute với Spring `@RequestScope`:** cả hai liên quan request scope, nhưng `@RequestScope` quản lý vòng đời bean Spring, trong khi `@RequestAttribute` đọc thuộc tính do Servlet API lưu dưới một key. Spring không tự lấy mọi bean request-scoped làm request attribute cùng tên.

## 4. `@SessionAttribute`: đọc dữ liệu đã lưu qua các request

Cú pháp:

```java
@GetMapping("/preferences")
public String preferences(
        @SessionAttribute(name = "language", required = false) String language) {
    return "language=" + (language == null ? "none" : language);
}
```

- `@SessionAttribute("language")` tìm attribute trong **HTTP session**. Nó không đọc một cookie `language` trực tiếp; thông thường cookie `JSESSIONID` hoặc cơ chế session id khác giúp server xác định session.
- `required` mặc định là `true`: thiếu session hoặc thiếu key sẽ gây lỗi resolve tham số. Dùng `required=false` khi giá trị có thể chưa được thiết lập.
- `value` và `name` là alias; nên khai báo tên tường minh. Không có `defaultValue` trong annotation này.
- `@SessionAttribute` phù hợp khi **đọc** trạng thái session do các thành phần khác quản lý. Để **ghi/xóa**, dùng `HttpSession`, `WebRequest` hoặc dịch vụ quản lý session phù hợp.
- Thời hạn tồn tại phụ thuộc timeout, invalidate, cookie/session id và backend session. Không phải dữ liệu bền vững hay luôn còn sau khi server restart.
- Nếu hai request đồng thời sửa chung một object trong session, có thể phát sinh race condition; session không mặc nhiên đảm bảo thread-safety cho những object mutable.

Ví dụ quản lý trực tiếp:

```java
@PostMapping("/set-language")
public String setLanguage(@RequestParam("language") String language,
                          HttpSession session) {
    session.setAttribute("language", language);
    return "saved";
}

@PostMapping("/clear-language")
public String clearLanguage(HttpSession session) {
    session.removeAttribute("language");
    return "removed";
}
```

Đây là các **method minh họa** đặt trong `@RestController` có cấu hình Spring MVC; không phải chương trình Java chạy độc lập. Khi một method yêu cầu đối số `HttpSession`, servlet có thể tạo session nếu chưa có. Nếu cần chỉ kiểm tra session đã tồn tại mà không tạo mới:

```java
HttpSession existing = request.getSession(false);
if (existing != null) {
    existing.removeAttribute("language");
}
```

Đoạn trên giả sử đang có biến `HttpServletRequest request`. `getSession(false)` trả `null` nếu request chưa có session.

## 5. Ví dụ ứng dụng Spring Boot 3.x + Java 17 trở lên

Ví dụ dưới đây minh họa đồng thời **request attribute do Filter cung cấp** và **session attribute do controller ghi/đọc**. Dùng một dự án Spring Boot Servlet/MVC có dependency `spring-boot-starter-web`. Với phần test ở sau, thêm `spring-boot-starter-test`.

Ở Spring Boot 3.x dùng namespace `jakarta.servlet.*`; nếu bạn đang ở Boot 2.x sẽ gặp `javax.servlet.*` và cần điều chỉnh imports theo phiên bản.

Tạo **`src/main/java/demo/web/ContextAttributesApplication.java`**:

```java
package demo.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.filter.OncePerRequestFilter;

@SpringBootApplication
public class ContextAttributesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContextAttributesApplication.class, args);
    }

    @Bean
    OncePerRequestFilter traceAttributeFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain)
                    throws ServletException, IOException {

                String traceId = request.getHeader("X-Trace-Id");
                if (traceId == null || traceId.isBlank()) {
                    traceId = "generated-demo";
                }

                request.setAttribute("traceId", traceId);
                try {
                    chain.doFilter(request, response);
                } finally {
                    request.removeAttribute("traceId");
                }
            }
        };
    }
}

@RestController
class ContextController {

    @GetMapping("/api/context/request")
    String requestContext(@RequestAttribute("traceId") String traceId) {
        return "traceId=" + traceId;
    }

    @GetMapping("/api/context/optional")
    String optionalRequestContext(
            @RequestAttribute(name = "feature", required = false) String feature) {
        return "feature=" + (feature == null ? "none" : feature);
    }

    @PostMapping("/api/context/session")
    String rememberLanguage(@RequestParam("language") String language,
                            HttpSession session) {
        session.setAttribute("language", language);
        return "stored=" + language;
    }

    @GetMapping("/api/context/session")
    String readLanguage(
            @SessionAttribute(name = "language", required = false)
            String language) {
        return "language=" + (language == null ? "none" : language);
    }

    @GetMapping("/api/context/session-required")
    String readRequiredLanguage(@SessionAttribute("language") String language) {
        return "language=" + language;
    }

    @PostMapping("/api/context/session/remove")
    String removeLanguage(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute("language");
        }
        return "removed";
    }
}
```

**Giải thích hoạt động:**

1. Spring Boot đăng ký bean `OncePerRequestFilter` vào Servlet filter chain theo cơ chế autoconfiguration; mỗi request dispatch được xử lý phù hợp với semantics của filter.
2. Filter lấy header `X-Trace-Id` hoặc dùng giá trị mẫu `generated-demo`, rồi đặt `request.setAttribute("traceId", ...)`. Nó gọi `chain.doFilter` để tiếp tục tới Spring MVC.
3. Khi MVC gặp `@RequestAttribute("traceId")`, Spring lấy attribute mà filter vừa đặt. Controller không cần tự đọc Servlet API.
4. Block `finally` dọn attribute sau khi downstream kết thúc. Việc dọn này **không ảnh hưởng** giá trị mà controller đã nhận khi chạy trước đó.
5. POST session lưu `language`; một GET khác chỉ đọc lại khi client gửi đúng session id. GET dùng `required=false` trả `none` khi chưa có lựa chọn.
6. `/api/context/session-required` khác endpoint optional: nếu thiếu attribute, argument resolution thất bại trước khi method body chạy.

Filter trên dùng header do client cung cấp **chỉ để trình diễn dữ liệu trace**. Không được coi header tự khai là bằng chứng tenant, user id hoặc quyền truy cập. Ở hệ thống thật, ID do hạ tầng tin cậy cấp cần cơ chế kiểm soát nguồn và logging rõ ràng.

Trong dự án Spring Boot 3.x dùng Gradle/Groovy, dependency tối thiểu liên quan bài (khi project đã có Boot plugin, dependency management và repository):

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Khởi chạy project qua `./gradlew bootRun` (hoặc `./mvnw spring-boot:run` khi dự án dùng Maven). Đoạn dependency này là **mảnh cấu hình build**, không phải toàn bộ file `build.gradle` độc lập.

### Quan sát bằng HTTP client

Sau khi chạy ứng dụng trên `localhost:8080`, thực hiện trong shell Bash (các lệnh `curl` chuẩn):

```bash
curl -sS -H 'X-Trace-Id: demo-42' http://localhost:8080/api/context/request
curl -sS http://localhost:8080/api/context/request
curl -sS http://localhost:8080/api/context/optional
curl -sS http://localhost:8080/api/context/session
curl -sS -c cookies.txt -X POST -d 'language=vi' http://localhost:8080/api/context/session
curl -sS -b cookies.txt http://localhost:8080/api/context/session
curl -sS -b cookies.txt -X POST http://localhost:8080/api/context/session/remove
curl -sS -b cookies.txt http://localhost:8080/api/context/session
```

Body trả về tương ứng, **nếu các endpoint không bị filter/security khác can thiệp**:

```text
traceId=demo-42
traceId=generated-demo
feature=none
language=none
stored=vi
language=vi
removed
language=none
```

`-c cookies.txt` ghi cookie từ phản hồi POST, và `-b cookies.txt` gửi lại cookie ấy để server xác định cùng session. Nếu gọi GET mới **không gửi cookie session**, không có bảo đảm lấy lại `language=vi`. Đây là kết quả body minh họa, không phải stdout của lệnh `javac`. Không commit file `cookies.txt` vào repository.

Trong những client tự theo redirect hoặc ứng dụng có middleware kiểm tra authentication, cần dùng `curl -i` để quan sát HTTP status, header `Set-Cookie` và cookie gửi lại.

## 6. Đặt request attribute bằng `HandlerInterceptor`

Khi cần thêm dữ liệu **sau khi Spring MVC đã chọn handler** nhưng trước khi method chạy, `HandlerInterceptor#preHandle` là một điểm mở rộng phù hợp. Ví dụ rút gọn:

```java
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

class TenantInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        String resolvedTenant = "tenant-demo"; // Thay bằng bộ phân giải đáng tin cậy.
        request.setAttribute("tenant", resolvedTenant);
        return true;
    }
}
```

Đăng ký interceptor trong cấu hình MVC (đoạn dưới là **mảnh class configuration**):

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class MvcInterceptors implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantInterceptor())
                .addPathPatterns("/api/tenant/**");
    }
}
```

Controller được interceptor hỗ trợ có thể khai báo:

```java
@GetMapping("/api/tenant/info")
public String tenantInfo(@RequestAttribute("tenant") String tenant) {
    return "tenant=" + tenant;
}
```

Nếu ghép đúng ba fragment trên vào một dự án, `GET /api/tenant/info` sẽ nhận `tenant-demo`. Trong ứng dụng thật, giá trị tenant phải được xác định từ nguồn đáng tin (ví dụ principal đã xác thực hoặc context đã xác thực); đoạn này chỉ mô phỏng nơi đặt attribute.

Khác biệt quan trọng:

- **Filter** tham gia Servlet chain, có thể chạy trước khi Spring MVC chọn handler; có thể áp dụng cả request chưa match controller, static content và một số dispatch type tùy cấu hình.
- **Interceptor** là thành phần của Spring MVC, chạy quanh handler đã được chọn; không tương đương lớp bảo vệ ở Servlet chain.
- **MethodArgumentResolver** là cơ chế Spring dùng để đưa giá trị vào tham số controller, trong đó hai annotation ở bài này có resolver tương ứng. Nếu chỉ cần truyền giá trị đã lưu ở request/session, thường **không cần** viết custom resolver.

Đừng đặt logic bảo mật bắt buộc chỉ trong một interceptor rồi cho rằng nó bảo vệ toàn bộ URL. Những request không đi qua handler MVC dự kiến có thể không đi qua interceptor đó.

## 7. `@SessionAttribute` khác `@SessionAttributes` (có “s”)

Hai annotation tên gần giống nhưng có vai trò rất khác:

| `@SessionAttribute` | `@SessionAttributes` |
| --- | --- |
| Đặt ở **tham số method** | Đặt trên **class controller** |
| **Đọc** một HTTP session attribute đã có | Quản lý **model attributes** của quy trình controller và lưu chúng tạm trong session |
| Không tự tạo/cập nhật attribute | Có thể “promote” model attribute tương ứng vào session |
| `required = true/false` | Cấu hình theo `names`/`types`, không có `required` tương tự |
| Thích hợp đọc lựa chọn người dùng, context lâu hơn một request | Thích hợp nhiều bước của một form/workflow |
| Xóa thủ công bằng `HttpSession#removeAttribute` hoặc API quản lý | Kết thúc hội thoại model/session bằng `SessionStatus#setComplete()` |

Ví dụ minh họa workflow model/session (các đoạn nằm trong cùng controller):

```java
@Controller
@SessionAttributes("draft")
class DraftController {

    @ModelAttribute("draft")
    public Draft draft() {
        return new Draft();
    }

    @PostMapping("/draft/finish")
    public String finish(@ModelAttribute("draft") Draft draft,
                         SessionStatus status) {
        // Xử lý draft hợp lệ rồi hoàn thành workflow.
        status.setComplete();
        return "redirect:/done";
    }
}
```

`Draft` ở đây là kiểu domain do ứng dụng tự định nghĩa; ví dụ chỉ minh họa luồng MVC, **không phải source file biên dịch độc lập**. Với `@SessionAttributes`, tên lưu trữ session thực tế có thể được framework quản lý theo cơ chế model của controller, nên code khác không nên phụ thuộc vào tên key nội bộ đó. `SessionStatus#setComplete()` **không đồng nghĩa invalidate toàn bộ HTTP session** và không xóa mọi thuộc tính khác của người dùng.

Nếu yêu cầu đơn giản là đọc key do filter hoặc controller đặt vào session, chọn `@SessionAttribute`. Nếu mục tiêu là đưa model qua nhiều bước form với lifecycle rõ, `@SessionAttributes` và `SessionStatus` phù hợp hơn.

## 8. `required`, `Optional`, thiếu attribute và lỗi thường gặp

Ví dụ các khai báo:

```java
// Bắt buộc: không có attribute -> exception khi resolve tham số.
@RequestAttribute("correlationId") String id

// Tùy chọn: không có -> null.
@RequestAttribute(name = "correlationId", required = false) String id

// Tùy chọn ở dạng đối tượng:
@RequestAttribute("correlationId") Optional<String> id

// Không có HTTP session hoặc không có key -> cho phép null:
@SessionAttribute(name = "language", required = false) String language
```

Các dòng trên là **chữ ký tham số** để so sánh, không phải một method Java hợp lệ khi đứng riêng. Với `Optional<String>` hãy import `java.util.Optional`.

Các vấn đề thường phát sinh:

1. **Key không khớp**: filter đặt `request.setAttribute("traceId", ...)` nhưng controller yêu cầu `@RequestAttribute("requestId")`.
2. **Đặt quá muộn**: thuộc tính được thêm sau khi controller đã được gọi hoặc chỉ trong một nhánh interceptor.
3. **Không cùng scope**: lưu `request` nhưng đọc `session`, hoặc lưu session trên request A rồi request B không mang session id.
4. **Sai kiểu dữ liệu**: đặt một object domain hoặc `Integer` nhưng tham số khai báo kiểu không phù hợp; không mặc định diễn giải JSON thành object.
5. **Thiếu dữ liệu nhưng vẫn dùng kiểu primitive**: hãy dùng reference type hoặc `Optional<T>` cho trường hợp nullable.
6. **Giả định status code cứng**: thông thường lỗi thuộc tính bắt buộc thiếu được Spring MVC xử lý như lỗi binding với phản hồi 400, nhưng `@ControllerAdvice`, exception resolver và bộ lọc bảo mật có thể thay đổi kết quả HTTP. Xác minh trong test của ứng dụng.
7. **Rò rỉ dữ liệu giữa người dùng**: đặt context theo user trong singleton field hoặc static variable rồi nhầm là request-scoped. Controller mặc định thường là singleton; dữ liệu request phải nằm ở method arguments/request scope, session và tài nguyên chia sẻ phải được bảo vệ khỏi tranh chấp.
8. **Giả định `@RequestAttribute` là dữ liệu client gửi**: HTTP header/parameter không được Spring tự chuyển thành request attribute tương ứng.

## 9. Ngữ cảnh sẵn có: lấy khi nào và dùng cách nào?

Spring MVC hỗ trợ inject trực tiếp các kiểu Servlet/Spring quen thuộc vào method, ví dụ:

```java
@GetMapping("/context/details")
public String details(HttpServletRequest request, HttpSession session) {
    Object currentTrace = request.getAttribute("traceId");
    Object language = session.getAttribute("language");
    return String.valueOf(currentTrace) + "/" + String.valueOf(language);
}
```

Đây là **một method minh họa** trong controller. Lưu ý tham số `HttpSession session` có thể tạo session dù mục tiêu ban đầu chỉ đọc; nếu không muốn tạo, thay bằng `HttpServletRequest request` rồi gọi `request.getSession(false)`.

Dùng kiểu tham số rõ (`@RequestAttribute`, `@SessionAttribute`) khi chỉ cần một hoặc hai giá trị, vì method signature biểu lộ phụ thuộc và dễ test. Dùng `HttpServletRequest`, `HttpSession` hoặc `WebRequest` khi cần đọc/ghi nhiều thuộc tính, kiểm tra sự tồn tại, xóa hoặc xử lý lifecycle.

Một số cơ chế liên quan cần phân biệt:

- `RequestContextHolder` giữ `RequestAttributes` gắn với thread thực thi request theo cơ chế Spring. Đây là tiện ích cho code nằm sâu trong luồng web nhưng làm tăng phụ thuộc ngầm; tránh dựa vào nó trong service nếu có thể truyền giá trị tường minh.
- Sau khi chuyển việc sang thread khác (ví dụ `@Async`, `CompletableFuture` hoặc executor), context gắn với thread **không mặc nhiên được truyền đi**; tránh lấy request/session tùy tiện từ worker thread, cần thiết kế context propagation phù hợp và chỉ truyền dữ liệu cần thiết.
- `@RequestScope` và `@SessionScope` quản lý **Spring bean theo scope**, khác với map thuộc tính Servlet trực tiếp dù có cùng khái niệm vòng đời.
- `ServletContext#getAttribute` là dữ liệu chia sẻ ở cấp ứng dụng, không phải request riêng hay user session; cần cân nhắc concurrency và lifecycle khi cập nhật.
- `SecurityContextHolder` và `@AuthenticationPrincipal` thuộc cơ chế của Spring Security khi được cấu hình; không tự khẳng định rằng một attribute từ request/session đã được xác thực.

Bảng vòng đời dễ nhớ:

```text
Request A ----------------------------------X       (request attribute hết scope)
     Session S ===================================X  (tồn tại đến timeout/invalidate)
Request B -----------X                           |
Request C -----------------X                     |
ServletContext ====================================== (cấp ứng dụng)
```

Các hình dạng thời gian trên chỉ diễn đạt **quan hệ vòng đời**, không đại diện timeout cụ thể. Một session có thể hết hạn giữa hai request; khi triển khai nhiều node, nơi lưu session phải nhất quán hoặc dùng cơ chế phù hợp (sticky session, store chung, Spring Session khi cần).

## 10. Bảo mật và tính đúng đắn trong ứng dụng thật

Request/session attributes giúp truyền context thuận tiện nhưng **không tự động là nguồn tin cậy**:

- Giá trị lấy trực tiếp từ `X-User-Id`, `X-Tenant` hoặc query parameter mà chưa kiểm tra có thể bị giả mạo. Nếu attribute quyết định phân quyền, trước hết phải xác thực nguồn và ràng buộc với principal đã được Spring Security xác nhận.
- Không lưu mật khẩu, token thô, PII không cần thiết hoặc object lớn vào session; cân nhắc thời gian sống, serialization và chính sách bảo mật. Session ID phải được bảo vệ; thực tế cần HTTPS, cookie `HttpOnly`/`Secure`/`SameSite` phù hợp và chống session fixation.
- Không dùng một Java mutable map/object trong session rồi mặc định rằng nhiều request đồng thời có thể sửa an toàn. Thiết kế bất biến, đồng bộ hóa thích hợp hoặc cập nhật qua lớp lưu trữ có tính nhất quán.
- Sau logout hoặc revoke cần xóa trạng thái liên quan; session invalidation hoặc hệ thống authentication/session management phải chịu trách nhiệm. Xóa `@SessionAttributes` của một workflow không đồng nghĩa đăng xuất.
- Không dùng request attribute làm kho lưu dữ liệu bền vững hoặc qua HTTP redirect; dùng URL/flash attribute/session hoặc store chuyên dụng theo đúng mục tiêu.
- Không đưa stack trace hoặc tên nội bộ attribute ra API response trong production; xử lý lỗi theo hợp đồng HTTP/API rõ ràng.

## 11. Kiểm thử với `MockMvc`

Trong một dự án đã có Spring Boot test, tạo **`src/test/java/demo/web/ContextAttributesApplicationTests.java`**:

```java
package demo.web;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContextAttributesApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void filterProvidesRequestAttribute() throws Exception {
        mvc.perform(get("/api/context/request")
                        .header("X-Trace-Id", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(content().string("traceId=abc-123"));

        mvc.perform(get("/api/context/request")
                        .param("traceId", "value-from-query"))
                .andExpect(status().isOk())
                .andExpect(content().string("traceId=generated-demo"));
    }

    @Test
    void sessionAttributeNeedsTheSameSession() throws Exception {
        MvcResult saved = mvc.perform(post("/api/context/session")
                        .param("language", "vi"))
                .andExpect(status().isOk())
                .andExpect(content().string("stored=vi"))
                .andReturn();

        HttpSession servletSession = saved.getRequest().getSession(false);
        assertNotNull(servletSession);
        MockHttpSession session = (MockHttpSession) servletSession;

        mvc.perform(get("/api/context/session").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("language=vi"));

        mvc.perform(get("/api/context/session"))
                .andExpect(status().isOk())
                .andExpect(content().string("language=none"));

        mvc.perform(post("/api/context/session/remove").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("removed"));

        mvc.perform(get("/api/context/session").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("language=none"));
    }
}
```

Chạy `./gradlew test` hoặc `./mvnw test`. Khi ứng dụng ví dụ chỉ có Spring MVC và các starter như đã nêu, kỳ vọng **hai bài test pass**. Test đầu chứng minh query parameter `traceId` không ghi đè request attribute do filter đặt. Test thứ hai chứng minh session tồn tại qua nhiều request **khi tái sử dụng cùng `MockHttpSession`**, và trạng thái bị xóa khi gọi endpoint remove.

**Giới hạn:** test tích hợp `MockMvc` sử dụng môi trường Spring test, không mô phỏng hoàn toàn proxy, network, cookie policy của browser hay cluster nhiều node. Nếu dự án bổ sung Spring Security, CSRF, filter custom hoặc cấu hình lỗi riêng, test cần setup authentication, CSRF và filter chain theo hệ thống thực tế.

Có thể test thiếu request attribute một cách độc lập bằng `MockMvcBuilders.standaloneSetup(new ContextController())` **không đăng ký filter** và gọi `GET /api/context/request`; nếu `required=true`, controller không được chạy. Khi kiểm thử session bắt buộc, gọi `/api/context/session-required` với session mới hoặc không có session và xác minh HTTP status/error contract của ứng dụng.

## 12. Quy trình chọn annotation trong dự án

1. **Nguồn dữ liệu client gửi?** Sử dụng `@RequestParam`, `@PathVariable`, `@RequestHeader`, `@CookieValue`, `@RequestBody` theo định dạng, không dùng `@RequestAttribute` để mong Spring tự đọc HTTP input.
2. **Đã có object do filter/interceptor tạo trong cùng request?** Đặt bằng `request.setAttribute` trước controller rồi đọc `@RequestAttribute`; quyết định rõ attribute bắt buộc hay tùy chọn.
3. **Cần lựa chọn/trạng thái qua nhiều request của cùng phiên HTTP?** Ghi/xóa bằng `HttpSession` hoặc cơ chế session đã chọn, đọc bằng `@SessionAttribute`.
4. **Đang làm form nhiều bước với Spring MVC model?** Cân nhắc `@SessionAttributes` trên controller và `SessionStatus#setComplete()`.
5. **Cần user đã xác thực hoặc quyền truy cập?** Dùng Spring Security và principal đã được kiểm tra; attribute tùy ý không đủ để quyết định quyền.
6. **Cần làm việc bất đồng bộ hoặc liên dịch vụ?** Đóng gói context cần thiết thành dữ liệu truyền tường minh hoặc cơ chế propagation phù hợp, không giả định request-bound ThreadLocal hoặc HTTP session tự đi qua thread/process.

## 13. Bài tập tự kiểm tra

- **Tình huống 1:** Filter đặt `request.setAttribute("region", "apac")`. Controller khai báo `@RequestParam("region")`; request không có query parameter. Điều gì xảy ra? Hãy sửa controller bằng annotation đúng và kiểm thử.
- **Tình huống 2:** Client POST chọn `language=en`, GET tiếp theo không có cookie session. Vì sao `@SessionAttribute(name="language", required=false)` có thể trả `null`?
- **Tình huống 3:** Controller khai báo `@RequestAttribute("tenant")` bắt buộc, nhưng interceptor chỉ đặt attribute cho `/api/admin/**`. Request vào endpoint không thuộc pattern sẽ có hành vi gì? Thiết kế test và thống nhất pattern.
- **Tình huống 4:** Form tạo draft nhiều bước dùng `@SessionAttributes("draft")`. Sau khi hoàn tất, gọi `status.setComplete()` có xóa luôn thuộc tính `language` đang có ở session không? Giải thích.
- **Tình huống 5:** Một `@Async` service cần trace id. Hãy so sánh việc truyền `String traceId` như tham số với đọc `RequestContextHolder` từ worker thread. Chỉ ra trường hợp dữ liệu có thể không tồn tại.

## Tài liệu tham khảo chính thức

- [Spring MVC — `@RequestAttribute`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestattrib.html)
- [Spring MVC — `@SessionAttribute`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/sessionattribute.html)
- [Spring MVC — `@SessionAttributes`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/sessionattributes.html)
- [Spring MVC — Handler Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html)
- [Spring MVC — Interception](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html)
- [Spring Framework Javadoc — `@RequestAttribute`](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/bind/annotation/RequestAttribute.html)
- [Spring Framework Javadoc — `@SessionAttribute`](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/bind/annotation/SessionAttribute.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="controller-inputs-008">HttpServletRequest, HttpServletResponse, Principal và Locale trong chữ ký phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# HttpServletRequest, HttpServletResponse, Principal và Locale trong chữ ký phương thức Spring MVC

> **Bối cảnh:** Spring Web MVC theo mô hình Servlet, với Java 17+ và Spring Boot 3.x. Bài tập trung vào **controller method arguments**: nhận trực tiếp đối tượng request, đối tượng response, thông tin người dùng đã xác thực và ngôn ngữ/vùng miền hiện hành. Các ví dụ dùng package `jakarta.servlet.*` tương thích Spring Framework 6+; dự án Spring 5 và cũ hơn còn dùng `javax.servlet.*`.

## 1. Vì sao đặt bốn kiểu này vào tham số controller?

Spring MVC cho phép một handler lấy dữ liệu request theo hai mức:

- **Mức khai báo:** `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@CookieValue`, `@RequestBody`… Spring chỉ chuyển đúng dữ liệu mà method cần.
- **Mức ngữ cảnh request/response:** Spring có thể truyền trực tiếp `HttpServletRequest`, `HttpServletResponse`, `Principal` và `Locale` vào **chữ ký phương thức** để method truy cập những khả năng hoặc thông tin rộng hơn.

Ví dụ cú pháp cốt lõi:

```java
@GetMapping("/context")
public String context(
        HttpServletRequest request,
        HttpServletResponse response,
        Principal principal,
        Locale locale) {
    response.setHeader("X-Handler", "context");
    String user = principal == null ? "anonymous" : principal.getName();
    return request.getMethod() + " / " + user
            + " / " + locale.toLanguageTag();
}
```

Đây là **đoạn method minh họa**, cần đặt trong controller và import đúng các kiểu. Spring tự tìm nguồn dữ liệu và cung cấp đối số tương ứng trong lúc gọi handler; **không cần `@RequestParam`, `@Autowired`, hay khởi tạo các đối tượng này bằng `new`**.

| Kiểu tham số | Import Java | Chức năng chính | Giá trị/đối tượng đến từ |
| --- | --- | --- | --- |
| `HttpServletRequest` | `jakarta.servlet.http.HttpServletRequest` | Xem method, URI, header, cookie, tham số, thuộc tính và session liên quan đến HTTP request | Servlet container và chuỗi xử lý request |
| `HttpServletResponse` | `jakarta.servlet.http.HttpServletResponse` | Điều khiển status, header, content type, cookie và output của HTTP response | Servlet container |
| `Principal` | `java.security.Principal` | Danh tính người dùng đã được cơ chế xác thực xác nhận | Request principal do container/filter bảo mật cung cấp |
| `Locale` | `java.util.Locale` | Ngôn ngữ/vùng miền được xác định cho request | `LocaleResolver`/`LocaleContextResolver` của Spring MVC |

**Phân biệt:** `HttpServletRequest` và `HttpServletResponse` là hai đối tượng mutable liên quan đến HTTP I/O; `Principal` và `Locale` là dữ liệu ngữ cảnh do hạ tầng xác định. Sử dụng các tham số này trong controller đồng bộ thông thường; không giữ request/response để tái sử dụng giữa nhiều request.

## 2. `HttpServletRequest`: đọc ngữ cảnh HTTP hiện tại

`HttpServletRequest` kế thừa `ServletRequest`, cung cấp API mức thấp hơn so với các annotation ánh xạ dữ liệu. Một số method phổ biến:

| Method | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `getMethod()` | HTTP method | `GET`, `POST` |
| `getRequestURI()` | Đường dẫn URI theo servlet container, thường bao gồm context path, không gồm query string | `/shop/api/orders/42` |
| `getContextPath()` | Context path của ứng dụng | `/shop` hoặc chuỗi rỗng |
| `getQueryString()` | Phần sau dấu `?`, hoặc `null` | `sort=asc&page=2` |
| `getHeader("X-Request-Id")` | Đọc header, hoặc `null` nếu vắng | `abc-123` |
| `getParameter("page")` | Một tham số request, thường từ query/form data | `2` |
| `getCookies()` | Cookie client gửi, có thể `null` | `Cookie[]` |
| `getAttribute("key")` | Thuộc tính do filter/interceptor/servlet đặt cho request | `Object` hoặc `null` |
| `getSession(false)` | Session hiện có nếu có, **không tự tạo session mới** | `HttpSession` hoặc `null` |
| `getUserPrincipal()` | Principal theo hợp đồng Servlet | `Principal` hoặc `null` |
| `getRemoteAddr()` | Địa chỉ peer kết nối đến container | Có thể là proxy, không nhất thiết IP máy người dùng |

Ví dụ đọc header và query:

```java
@GetMapping("/inspect")
public String inspect(HttpServletRequest request) {
    String requestId = request.getHeader("X-Request-Id");
    String page = request.getParameter("page");

    return "method=" + request.getMethod()
            + ", uri=" + request.getRequestURI()
            + ", page=" + page
            + ", requestId=" + requestId;
}
```

Với `GET /inspect?page=2` và header `X-Request-Id: abc-123`, body là `method=GET, uri=/inspect, page=2, requestId=abc-123` (nếu ứng dụng chạy tại context path rỗng).

### 2.1. Khi nào dùng `HttpServletRequest`, khi nào dùng annotation?

Nếu chỉ cần một giá trị, nên khai báo hẹp:

```java
@GetMapping("/products")
public String list(
        @RequestParam(defaultValue = "1") int page,
        @RequestHeader(name = "X-Request-Id", required = false)
        String requestId) {
    return "page=" + page + ", requestId=" + requestId;
}
```

Cách khai báo trên tự chuyển `page` sang `int` và thể hiện yêu cầu đầu vào rõ ràng. `HttpServletRequest` thích hợp khi cần nhiều đặc tính servlet, thông tin request chưa được annotation mô tả thuận tiện, request attribute do filter cấp, hoặc tích hợp API Servlet.

Lưu ý về luồng dữ liệu: `getInputStream()` và `getReader()` đọc **body thô**. Đừng đọc body bằng `HttpServletRequest` rồi giả định `@RequestBody` vẫn đọc lại được; stream thường chỉ đọc một lần trừ khi có wrapper thích hợp. Tương tự, `getSession()` có thể tạo session mới, còn `getSession(false)` thì không. Đừng lưu `HttpServletRequest` vào singleton field hay giữ để dùng sau vòng đời xử lý request.

### 2.2. Header, địa chỉ client và độ tin cậy

Dữ liệu header, query, cookie và đường dẫn đều do client hoặc proxy có thể tác động. Chẳng hạn, `X-Forwarded-For` là header **có thể giả mạo** nếu cấu hình reverse proxy không xác thực và chuẩn hóa đúng; `getRemoteAddr()` có thể trả IP của proxy. Không dùng một trong hai làm căn cứ duy nhất cho quyền truy cập. Kiểm tra/giới hạn kích thước input, không đưa nội dung header tùy ý vào HTML, log nhạy cảm hay tên file.

## 3. `HttpServletResponse`: điều khiển phản hồi

Spring thường tự tạo HTTP response qua `@ResponseBody`, `@RestController`, `ResponseEntity` hoặc view rendering. Khi controller nhận `HttpServletResponse`, nó có thể thao tác trực tiếp:

```java
@GetMapping("/custom")
public void custom(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_ACCEPTED); // 202
    response.setContentType("text/plain;charset=UTF-8");
    response.setHeader("Cache-Control", "no-store");
    response.getWriter().write("accepted\n");
}
```

Ví dụ này là **một handler trả về `void`**: method tự ghi body. Khi HTTP response chưa bị middleware thay đổi, response dự kiến có status 202, `Content-Type: text/plain;charset=UTF-8`, `Cache-Control: no-store` và body `accepted` kèm newline. Import `java.io.IOException` và `jakarta.servlet.http.HttpServletResponse`.

Nhóm thao tác thường gặp:

| API | Ý nghĩa | Ghi chú |
| --- | --- | --- |
| `setStatus(201)` | Chọn HTTP status | Chỉ đặt mã; không tự viết body |
| `setHeader("X-Test", "1")` | Đặt/thay giá trị header | `addHeader` thêm một giá trị cho cùng tên |
| `setContentType("application/json")` | Loại nội dung phản hồi | Đặt charset trước khi lấy writer nếu cần |
| `setCharacterEncoding("UTF-8")` | Charset cho phần văn bản | Nên gọi trước `getWriter()` |
| `addCookie(cookie)` | Gửi `Set-Cookie` | Chọn `HttpOnly`, `Secure`, `SameSite` phù hợp ứng dụng |
| `getWriter()` | Ghi nội dung ký tự | Dùng cho văn bản, JSON tự ghi phải đúng format/escaping |
| `getOutputStream()` | Ghi byte, ví dụ file | Không dùng lẫn với `getWriter()` trên cùng response |
| `sendError(404)` | Gửi lỗi theo cơ chế container | Có thể kích hoạt cơ chế error handling |
| `sendRedirect("/login")` | Phản hồi chuyển hướng | Cẩn thận với URL do client cung cấp |
| `isCommitted()` | Kiểm tra response đã commit chưa | Sau commit thường không thể đổi status/header |

**Thứ tự rất quan trọng:** đặt status, content type, charset và header **trước** khi ghi body hay flush buffer. Khi response đã committed, thay đổi header/status có thể không có hiệu lực. Không vừa trực tiếp gọi `response.getWriter().write(...)` vừa trả về một body để Spring chuyển qua `HttpMessageConverter` trong cùng một handler; như vậy dễ gây ghi đúp, xung đột converter hoặc content type.

### 3.1. Khi nào nên dùng `ResponseEntity`?

Với REST API thông thường, dùng `ResponseEntity` sẽ gọn hơn và dễ kiểm tra hơn:

```java
@GetMapping("/created")
public ResponseEntity<String> created() {
    return ResponseEntity.status(201)
            .header("X-Source", "mvc")
            .contentType(MediaType.TEXT_PLAIN)
            .body("created");
}
```

Ví dụ trên dùng import `org.springframework.http.ResponseEntity` và `org.springframework.http.MediaType`; trả status 201, header `X-Source: mvc`, text body `created`. Khi **không thực sự cần Servlet API**, đây là lựa chọn thường hợp lý để phản hồi status/header/body. `HttpServletResponse` vẫn có giá trị cho streaming, cookie, tích hợp thư viện servlet hoặc xử lý ở mức HTTP thấp.

## 4. `Principal`: nhận danh tính đã xác thực

`java.security.Principal` là interface đơn giản với `String getName()`. Spring MVC có thể giải quyết đối số kiểu này theo request principal (từ `HttpServletRequest#getUserPrincipal()`). Principal phản ánh danh tính đã được tầng xác thực cung cấp; nó **không tự kích hoạt đăng nhập**, không tự kiểm tra mật khẩu và không phải bằng chứng rằng người dùng được phép truy cập mọi tài nguyên.

```java
@GetMapping("/me")
public String me(Principal principal) {
    return principal == null
            ? "No authenticated principal"
            : "Hello, " + principal.getName();
}
```

Ở request **không có principal đã xác thực**, đối số có thể là `null`; method trên xử lý an toàn. Khi container hoặc Spring Security cung cấp request principal, `principal.getName()` lấy tên của danh tính đó. Đừng mặc định toàn bộ request luôn có Principal, nhất là endpoint công khai.

### 4.1. `Principal` so với Spring Security `Authentication` / `@AuthenticationPrincipal`

- `Principal` là interface Java chuẩn, chỉ cam kết `getName()`.
- Với Spring Security, `Authentication` triển khai `Principal` và có thêm quyền hạn/credentials/details/trạng thái xác thực. Loại đối tượng cụ thể được gắn vào request phụ thuộc cách tích hợp bảo mật.
- `@AuthenticationPrincipal` là annotation riêng của Spring Security, lấy **đối tượng `Authentication.getPrincipal()`**, thường là `UserDetails` hoặc principal tùy biến; khác với việc khai báo một đối số `Principal` thuần túy.

Ví dụ minh họa cho **ứng dụng đã cài và cấu hình Spring Security**:

```java
// Ví dụ method riêng, chỉ dùng nếu tích hợp Spring Security.
@GetMapping("/username")
public String username(@AuthenticationPrincipal UserDetails currentUser) {
    return currentUser == null ? "anonymous" : currentUser.getUsername();
}
```

Cần import `org.springframework.security.core.annotation.AuthenticationPrincipal` và `org.springframework.security.core.userdetails.UserDetails`. Ví dụ này chỉ đúng khi principal thực sự là một `UserDetails`; với JWT/OAuth2/custom principal kiểu khác, cần khai báo kiểu tương ứng. Không giả định `Principal`, `Authentication` và `UserDetails` luôn cùng một object.

**Bảo mật:** tên người dùng trong Principal chỉ là nhận diện. Kiểm tra quyền truy cập bằng chính sách bảo mật (request authorization/method security, ownership checks), không bằng so sánh chuỗi lấy từ header `X-User` hay query parameter do người gọi tự nhập.

## 5. `Locale`: ngôn ngữ và vùng miền của request

`java.util.Locale` mô tả quy ước **ngôn ngữ, quốc gia/vùng** cho request, chẳng hạn `vi-VN`, `en-US` hoặc `fr-FR`. Spring MVC suy ra Locale bằng `LocaleResolver` hoặc `LocaleContextResolver` được cấu hình. Resolver mặc định thường dựa trên header HTTP `Accept-Language`; nhưng ứng dụng có thể cấu hình cookie, session hay resolver tùy biến nên kết quả **không bắt buộc trùng tuyệt đối với header**.

```java
@GetMapping("/language")
public String language(Locale locale) {
    return "locale=" + locale.toLanguageTag();
}
```

Với một cấu hình dựa vào `Accept-Language` và header `Accept-Language: vi-VN,vi;q=0.9`, body thường là `locale=vi-VN`.

Khác biệt quan trọng:

| Khái niệm | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| `Locale` | `vi-VN` | Ngôn ngữ/vùng miền trình bày số, ngày tháng, văn bản |
| `TimeZone` / `ZoneId` | `Asia/Ho_Chi_Minh` | Quy tắc múi giờ, giờ mùa hè và tính thời điểm địa phương |
| `Accept-Language` | `vi-VN,vi;q=0.9,en;q=0.7` | Nguyện vọng ngôn ngữ do client gửi, không phải bằng chứng địa lý |

**Locale không phải TimeZone.** Không suy múi giờ hay vị trí thật chỉ từ `vi-VN`. Với văn bản đa ngôn ngữ, dùng `MessageSource` và các bundle thông điệp; với số/ngày, dùng API định dạng phù hợp và locale đã xác định. Với timestamp, dùng `ZoneId` độc lập được xác định bởi chính sách ứng dụng hoặc lựa chọn người dùng.

Nếu dùng `AcceptHeaderLocaleResolver`, không nên kỳ vọng đổi locale thông qua `LocaleChangeInterceptor` là đủ: resolver đọc HTTP header và thường không hỗ trợ `setLocale` để thay đổi locale phía server. Muốn đổi qua cookie/session, cấu hình resolver có khả năng lưu lựa chọn locale một cách phù hợp.

## 6. Ví dụ đầy đủ có thể chạy: dùng cả bốn tham số

Tạo dự án Spring Boot **3.x**, Java **17+**, có dependency **Spring Web** bằng [Spring Initializr](https://start.spring.io/). Ví dụ này **không cài Spring Security**, vì mục tiêu là minh họa request công khai với Principal vắng mặt. Thay nội dung `src/main/java/com/example/demo/DemoApplication.java` bằng:

```java
package com.example.demo;

import java.io.IOException;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/context")
class ContextController {

    @GetMapping("/inspect")
    public Map<String, String> inspect(
            HttpServletRequest request,
            Principal principal,
            Locale locale) {

        Map<String, String> result = new LinkedHashMap<>();
        result.put("method", request.getMethod());
        result.put("uri", request.getRequestURI());
        result.put("locale", locale.toLanguageTag());
        result.put("user", principal == null
                ? "anonymous"
                : principal.getName());
        return result;
    }

    @GetMapping("/manual")
    public void manual(
            HttpServletRequest request,
            HttpServletResponse response,
            Principal principal,
            Locale locale) throws IOException {

        String user = principal == null
                ? "anonymous"
                : principal.getName();

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        response.getWriter().write(
                "method=" + request.getMethod() + "\n"
                + "locale=" + locale.toLanguageTag() + "\n"
                + "user=" + user + "\n");
    }

    @GetMapping("/entity")
    public ResponseEntity<String> entity(Locale locale) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header("X-Language", locale.getLanguage())
                .body("locale=" + locale.toLanguageTag());
    }
}
```

Giải thích vai trò các endpoint:

- `GET /api/context/inspect` nhận request + principal + locale và **để Spring tự serialize `Map` thành JSON**.
- `GET /api/context/manual` có đủ **cả bốn kiểu tham số**; handler tự đặt status/header và ghi text trực tiếp qua `HttpServletResponse`, nên method trả `void`.
- `GET /api/context/entity` minh họa đối chứng: dùng `ResponseEntity` để mô tả response mà không cần cầm `HttpServletResponse`.

Chạy từ thư mục project:

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows PowerShell / Command Prompt
mvnw.cmd spring-boot:run
```

Dùng cURL để kiểm tra. Dấu `-i` hiển thị cả HTTP status/header. Ở lệnh đầu, ký tự `-s` chỉ tắt progress bar:

```bash
curl -s -H 'Accept-Language: vi-VN,vi;q=0.9' \
  http://localhost:8080/api/context/inspect
```

Với Spring Web không có security và resolver ngôn ngữ dựa trên request header, JSON body dự kiến (các trường theo thứ tự chèn):

```json
{"method":"GET","uri":"/api/context/inspect","locale":"vi-VN","user":"anonymous"}
```

Tiếp theo:

```bash
curl -i -H 'Accept-Language: vi-VN' \
  http://localhost:8080/api/context/manual
```

Phản hồi chứa status HTTP `200 OK`, header `Cache-Control: no-store`, `Content-Type: text/plain;charset=UTF-8` và **body chính xác**:

```text
method=GET
locale=vi-VN
user=anonymous
```

Có ký tự newline cuối body do `"\n"`. Các header khác (ví dụ `Date`, `Connection`) phụ thuộc container. Lệnh trên dùng cú pháp nối dòng kiểu POSIX; trên PowerShell có thể viết toàn bộ cURL trên một dòng với `curl.exe -i -H "Accept-Language: vi-VN" "http://localhost:8080/api/context/manual"`.

Cuối cùng:

```bash
curl -i -H 'Accept-Language: en-US' \
  http://localhost:8080/api/context/entity
```

Body dự kiến là `locale=en-US`, header `X-Language: en`. Nếu thêm Spring Security chưa tùy biến, endpoint có thể yêu cầu đăng nhập và kết quả `Principal` cũng phụ thuộc cấu hình security; khi đó không coi ví dụ anonymous là kết quả bắt buộc.

## 7. Spring giải quyết đối số theo cơ chế nào?

1. Servlet container tạo `HttpServletRequest`/`HttpServletResponse` cho HTTP request; filter chain có thể bọc, thêm thuộc tính hoặc xác thực request.
2. `DispatcherServlet` gọi hệ thống handler mapping để chọn controller method phù hợp.
3. `RequestMappingHandlerAdapter` phối hợp `HandlerMethodArgumentResolver` để giải quyết các tham số dựa vào **kiểu hoặc annotation**.
4. Resolver cho Servlet request/response cung cấp đối tượng servlet; resolver cho `Principal` lấy danh tính liên kết với request; resolver cho `Locale` lấy locale từ resolver đang có hiệu lực.
5. Method controller được gọi. Nếu trả Java object thông thường từ `@RestController`, Spring có thể dùng message converter để tạo body. Nếu method tự thao tác `HttpServletResponse` và trả `void`, HTTP response được viết trực tiếp.

Điểm đáng nhớ là **Spring inject giá trị theo từng HTTP request tại thời điểm gọi handler**. Không cần đánh dấu `@Autowired` trên từng tham số method. Đối số `Principal` có thể trống khi chưa xác thực; `HttpServletRequest`, `HttpServletResponse` là đối tượng request hiện hành chứ không phải bean dùng chung cho mọi request.

### 7.1. Có cần khai báo bốn tham số theo thứ tự nhất định?

Không. Mỗi đối số được resolver xử lý độc lập dựa trên kiểu/annotation, miễn method signature hợp lệ. Cả hai cách sau đều thể hiện cùng nhu cầu:

```java
public String show(HttpServletRequest request, Locale locale, Principal principal) { /* ... */ }

public String show(Locale locale, Principal principal, HttpServletRequest request) { /* ... */ }
```

**Hai dòng trên chỉ là ví dụ so sánh chữ ký, không phải hai method đầy đủ để chép vào cùng một controller**; vị trí tham số không đổi nguồn cung cấp. Tuy nhiên, hãy giữ thứ tự ổn định, dễ đọc và tránh truyền thừa đối tượng request/response khi chỉ cần một trường dữ liệu.

## 8. Lỗi thường gặp và cách sửa

| Biểu hiện | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| `package javax.servlet.http does not exist` trên Spring Boot 3.x | Spring Framework 6 dùng Jakarta namespace | Đổi import thành `jakarta.servlet.http.*` |
| `NullPointerException` tại `principal.getName()` | Endpoint anonymous hoặc thiếu principal | Xử lý `principal == null`; cấu hình xác thực nếu API yêu cầu người dùng |
| `Locale` không đổi theo query `?lang=fr` | Locale resolver mặc định không đọc query đó | Cấu hình resolver/interceptor phù hợp hoặc gửi `Accept-Language` |
| `Accept-Language: vi-VN` nhưng locale khác | App có resolver tùy biến, danh sách locale hỗ trợ hoặc fallback | Kiểm tra `LocaleResolver`, cấu hình supported locales và request thực nhận |
| Trả header/status không như dự kiến | Response đã được commit hoặc do filter/container sửa | Đặt status/header trước lúc ghi/flush, kiểm tra filter |
| `IllegalStateException` khi ghi body | Gọi `getWriter()` và `getOutputStream()` trên một response | Chọn đúng một cách ghi body |
| JSON/text bị lỗi encoding | Đặt charset sau khi đã lấy writer/ghi body | Đặt `Content-Type` và encoding trước `getWriter()` |
| Gửi dữ liệu hai lần hoặc response hỏng | Tự ghi qua response và đồng thời trả body để converter xử lý | Dùng một chiến lược: `void` + viết trực tiếp, hoặc `ResponseEntity`/body converter |
| Client IP không đúng | Ứng dụng ở sau reverse proxy | Dựa vào cấu hình proxy tin cậy, tránh tin header tự khai |
| Controller đọc body nhưng `@RequestBody` trống | Request stream đã bị đọc | Chỉ đọc body một lần hoặc dùng caching wrapper đúng cách |

### 8.1. Tránh nhập nhằng khi kết hợp các cơ chế

- `@RequestHeader("Accept-Language") String raw` trả chuỗi header do client gửi; `Locale locale` trả đối tượng đã được resolver xác định. Hai giá trị không đồng nghĩa.
- `@RequestParam("user") String user` là input client tự cung cấp; `Principal principal` được hình thành qua cơ chế xác thực. Không dùng request parameter thay principal cho quyết định bảo mật.
- `HttpServletRequest#getUserPrincipal()` là lối đọc thủ công tương ứng với cách lấy principal ở API Servlet. Khai báo `Principal` ngay trong signature rõ ý định hơn khi chỉ cần tên người dùng.
- `HttpServletResponse#setStatus(404)` chỉ đổi status; `ResponseEntity.notFound().build()` là cách khai báo response phổ biến trong REST; `sendError(404)` có thể kích hoạt error handling của container. Chọn theo ý định.
- `Locale` dành cho lược đồ hiển thị và quốc tế hóa, không phải cơ chế phân quyền hay định danh người dùng.

## 9. Tự kiểm tra kiến thức

**Câu 1.** Tại sao controller không cần `@Autowired` cho `HttpServletRequest`?

**Đáp án:** Trong Spring MVC, hệ thống `HandlerMethodArgumentResolver` có sẵn khả năng cung cấp request object làm đối số của handler method.

**Câu 2.** Request chưa đăng nhập tới controller có `Principal principal`. Có nên gọi `principal.getName()` ngay không?

**Đáp án:** Không. Principal có thể `null`; kiểm tra trước và bảo vệ route bằng security nếu cần quyền đăng nhập.

**Câu 3.** `Accept-Language: fr-FR` có luôn đảm bảo `locale.toLanguageTag()` trả `fr-FR`?

**Đáp án:** Không. Resolver, danh sách locale hỗ trợ và fallback của ứng dụng quyết định locale; header chỉ là một nguồn đầu vào.

**Câu 4.** Muốn status 201 + header `Location` + JSON body, nên dùng gì trong REST API?

**Đáp án:** Thường chọn `ResponseEntity` để trả status, header và body rõ ràng, ít phải thao tác trực tiếp trên servlet response.

**Câu 5.** Tại sao nên đặt `response.setContentType("text/plain;charset=UTF-8")` trước `getWriter()`?

**Đáp án:** Servlet response cần xác định content type và mã hóa trước khi mở writer/ghi body, tránh charset không như mong muốn hoặc không còn đổi được sau commit.

## 10. Tài liệu chính thức

- [Spring Framework — MVC Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html): danh sách đối số hỗ trợ, gồm `ServletRequest`, `ServletResponse`, `Principal` và `Locale`.
- [Spring Framework — Return Values](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html): `void`, `ResponseEntity`, cách Spring xử lý kết quả method.
- [Spring Framework — Locale Resolution](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/localeresolver.html): `LocaleResolver`, `AcceptHeaderLocaleResolver` và các resolver khác.
- [Spring Framework — ResponseEntity](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html): điều khiển status/header/body bằng Spring MVC.
- [Spring Security — Spring MVC Integration](https://docs.spring.io/spring-security/reference/servlet/integrations/mvc.html): phân biệt `Principal` và `@AuthenticationPrincipal`.
- [Jakarta Servlet — HttpServletRequest / HttpServletResponse](https://jakarta.ee/specifications/servlet/): API HTTP request/response ở mức container.

**Điểm cần ghi nhớ:** `HttpServletRequest` đọc ngữ cảnh request; `HttpServletResponse` cho phép kiểm soát response trực tiếp; `Principal` cho biết danh tính đã được xác thực nếu có; `Locale` biểu thị quy ước ngôn ngữ/vùng miền của request. Spring MVC tự resolve bốn tham số trong handler method, còn kiểm soát quyền truy cập và lựa chọn ngôn ngữ phụ thuộc vào cấu hình tương ứng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="controller-inputs-009">Giá trị null, Optional và phân biệt đầu vào thiếu với đầu vào rỗng</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị null, Optional và phân biệt đầu vào thiếu với đầu vào rỗng trong Spring MVC

## Mục tiêu

Trong Spring MVC, **không có đầu vào**, **đầu vào hiện diện nhưng rỗng**, **đầu vào chứa khoảng trắng**, **giá trị JSON null** và **giá trị đã bị chuyển kiểu thành null** có thể dẫn đến hành vi khác nhau. Bài này giúp bạn:

- Chọn đúng cách nhận giá trị qua `@RequestParam`, `@RequestHeader`, `@PathVariable`, `@RequestBody`, `@ModelAttribute`.
- Dùng `required=false`, `Optional<T>`, `defaultValue` và `@Nullable` mà không đánh mất các trạng thái quan trọng.
- Phân biệt `?name`, `?name=`, không có `name` và `?name=%20` ở mức Servlet request parameter.
- Phân biệt JSON **thiếu field**, field mang `null`, chuỗi `""`, chuỗi chỉ có khoảng trắng, mảng `[]` và object `{}`.
- Thiết kế kiểm tra đầu vào và mã lỗi HTTP phù hợp; tránh ghi đè dữ liệu khi xử lý cập nhật từng phần (PATCH).

Phạm vi: **Spring MVC (Servlet stack)**, Java 17+, Spring Framework 6.x/Spring Boot 3.x; nhiều nguyên lý tương tự ở phiên bản mới nhưng phải kiểm tra thay đổi cấu hình thực tế. Đây không phải bài về Java `Optional` nói chung hay Spring WebFlux (WebFlux có quy tắc lấy request parameter khác).

## 1. Bảng thuật ngữ: “thiếu” khác “rỗng” thế nào?

Xét một đầu vào tên `name`:

| Tình huống | Ví dụ HTTP/JSON | Ý nghĩa ban đầu |
| --- | --- | --- |
| Thiếu (missing/absent) | `GET /items`, không có tham số `name` | Client **không gửi tên tham số** |
| Có tên, giá trị rỗng | `GET /items?name=` hoặc `?name` | Tham số hiện diện, giá trị chuỗi có độ dài 0 |
| Có tên, chỉ có khoảng trắng | `GET /items?name=%20` | Giá trị là `" "`: **khác chuỗi rỗng** |
| Giá trị thường | `GET /items?name=An` | Giá trị là `"An"` |
| JSON field thiếu | `{}` | Object không có khóa `name` |
| JSON field explicit null | `{"name":null}` | Object **có khóa** nhưng giá trị là JSON `null` |
| JSON field chuỗi rỗng | `{"name":""}` | Object có khóa, giá trị là chuỗi rỗng |
| JSON field chuỗi trắng | `{"name":"   "}` | Object có khóa, giá trị là chuỗi chứa khoảng trắng |
| JSON collection rỗng | `{"tags":[]}` | Mảng tồn tại, có 0 phần tử |
| JSON object rỗng | `{"settings":{}}` | Object tồn tại, có 0 field |

**Java `null` không phải là JSON `null` theo nghĩa “đã nhận được”.** JSON field không tồn tại và JSON field hiện diện có giá trị `null` đều có thể đi vào một thuộc tính Java `String name` dưới dạng `null` sau deserialize. Nếu yêu cầu nghiệp vụ cần biết *client có gửi field hay không*, kiểu Java `String` đơn lẻ thường không đủ.

Một request parameter không thể tự có giá trị “JSON null”. Chuỗi ký tự `null` trong `?name=null` chỉ là chuỗi `"null"`, hoàn toàn không tự chuyển thành `null` cho `String`.

## 2. `@RequestParam`: required, null, chuỗi rỗng và defaultValue

`@RequestParam` nhận Servlet request parameter. Trên Spring MVC, các parameter có thể đến từ **query string hoặc dữ liệu form được Servlet container parse**; không nên mặc định mọi `@RequestParam` đều chỉ lấy URL query.

### 2.1. Mặc định là bắt buộc

```java
@GetMapping("/required")
public String required(@RequestParam("name") String name) {
    return "Hello " + name;
}
```

Với cấu hình thông thường:

- `GET /required` → thiếu `name` → Spring trả `400 Bad Request` trước khi chạy method.
- `GET /required?name=` → `name` là `""`; annotation **không mặc định từ chối chuỗi rỗng**.
- `GET /required?name=%20` → `name` chứa một khoảng trắng; nó không tự biến thành chuỗi rỗng.
- `GET /required?name=An` → `name` là `"An"`.

`required=true` nói về **sự hiện diện/có thể resolve được của tham số**, không đồng nghĩa ràng buộc “phải có ký tự hữu ích”. Để kiểm tra văn bản bắt buộc không được rỗng hoặc chỉ có khoảng trắng, dùng quy tắc nghiệp vụ hay Jakarta Bean Validation (`@NotBlank`) khi đã thiết lập validation.

### 2.2. Cho phép thiếu bằng `required=false`

```java
@GetMapping("/optional-string")
public String optionalString(
        @RequestParam(name = "name", required = false) String name) {
    return name == null ? "MISSING" :
            name.isEmpty() ? "EMPTY" :
            name.isBlank() ? "BLANK" : "VALUE";
}
```

Với binder mặc định và không áp dụng bộ chỉnh chuỗi riêng:

| URL | `name` nhận được | Kết quả |
| --- | --- | --- |
| `/optional-string` | Java `null` | `MISSING` |
| `/optional-string?name` | `""` | `EMPTY` |
| `/optional-string?name=` | `""` | `EMPTY` |
| `/optional-string?name=%20` | `" "` | `BLANK` |
| `/optional-string?name=An` | `"An"` | `VALUE` |

Đừng suy ra rằng `?name` và `?name=` có thể phân biệt qua `@RequestParam String`: cả hai thường được Servlet API xem là tham số hiện diện với giá trị `""`. Nếu bài toán cần phân biệt chính xác cú pháp hai cách viết này, phải phân tích raw query string theo một giao ước được kiểm soát (xem thêm vấn đề encoding, lặp tên, ký tự `+` và `%xx`); đa số API **không cần** phân biệt như vậy.

### 2.3. Giá trị mặc định thay thế *cả thiếu và rỗng*

```java
@GetMapping("/with-default")
public String withDefault(
        @RequestParam(name = "name", defaultValue = "guest") String name) {
    return name;
}
```

| Request | Kết quả |
| --- | --- |
| `/with-default` | `guest` |
| `/with-default?name=` | `guest` |
| `/with-default?name` | `guest` |
| `/with-default?name=An` | `An` |
| `/with-default?name=%20` | `" "` trong cấu hình mặc định, **không phải guest** |

`defaultValue` ngầm làm tham số không còn required, nhưng nó **xóa khác biệt giữa missing và empty**. Nếu nghiệp vụ cần “thiếu thì dùng mặc định; rỗng thì báo lỗi hoặc xóa dữ liệu”, đừng dùng `defaultValue` trực tiếp: nhận `required=false` và tự phân nhánh theo sự hiện diện/giá trị.

### 2.4. Đổi kiểu có thể làm mất trạng thái ban đầu

```java
@GetMapping("/integer")
public String integer(
        @RequestParam(name = "count", required = false) Integer count) {
    return count == null ? "NO_COUNT" : count.toString();
}
```

Với converter mặc định, tham số vắng mặt và `?count=` có thể cùng được chuyển thành Java `null`. `?count=abc` không chuyển được thành số: thường là `400 Bad Request` do lỗi chuyển kiểu; nó không tự thành `null`. Nếu dùng `@RequestParam("count") int count`, không thể gán `null` vào primitive `int`, và Spring xử lý lỗi thay vì đưa null vào method.

Tài liệu Spring nhấn mạnh: **chuỗi rỗng trở thành null sau conversion có thể bị coi là thiếu**, gây lỗi required ngay cả khi tên tham số thực sự xuất hiện. Ví dụ `@RequestParam("count") Long count` với `?count=` thường tạo lỗi 400 do “missing after conversion”. Hành vi cụ thể phụ thuộc bộ converter và `@InitBinder` custom.

## 3. Dùng `Optional<T>` trong controller

Spring MVC hỗ trợ `java.util.Optional` cho controller method argument có annotation sở hữu thuộc tính `required`, ví dụ `@RequestParam` và `@RequestHeader`. Nó tương đương cho phép thiếu giá trị.

```java
@GetMapping("/optional-count")
public String count(@RequestParam("count") Optional<Integer> count) {
    return count.map(String::valueOf).orElse("NO_COUNT");
}
```

- `/optional-count` → `Optional.empty()` → `NO_COUNT`.
- `/optional-count?count=7` → `Optional.of(7)` → `7`.
- `/optional-count?count=` → với conversion mặc định, chuỗi rỗng sang `Integer` thành null → `Optional.empty()`.
- `/optional-count?count=abc` → converter không hiểu `abc` → thường HTTP 400, **không** thành `Optional.empty()`.

Đối với `Optional<String>`, một giá trị chuỗi rỗng thường là **`Optional.of("")`** vì converter giữ chuỗi rỗng, trong khi giá trị thiếu cho `Optional.empty()`; đừng suy luận mọi `Optional<T>` đều gom chuỗi rỗng thành empty. Custom formatter/binder có thể thay đổi hành vi. `Optional` chỉ biểu diễn **có giá trị / không có giá trị sau binding**; nó không tự giữ lại toàn bộ lịch sử vì sao giá trị biến mất.

Quy tắc thực hành:

1. Nếu chỉ cần dùng giá trị hoặc mặc định khi không có: dùng `Optional<T>` hoặc `required=false`.
2. Nếu cần phân biệt query missing và empty: giữ tham số dạng `String` và kiểm tra sự hiện diện qua `HttpServletRequest.getParameterMap()` khi cần.
3. Nếu cần phân biệt số bị để trống và không gửi: nhận tham số dưới dạng `String` trước, xác định trạng thái rồi parse `Integer.parseInt`/conversion và phản hồi lỗi phù hợp.
4. Không khai báo `Optional` làm **field của DTO JSON** chỉ vì muốn biểu diễn PATCH ba trạng thái; JSON field missing và explicit null có thể cùng biến thành “empty” trong nhiều cách deserialize. Dùng biểu diễn có dấu vết hiện diện khi bài toán cần ba trạng thái.

**Java `Optional` không thay thế validation.** `Optional.of("")` vẫn chứa chuỗi rỗng; nếu cần ký tự hữu ích thì phải kiểm tra hoặc validate nội dung sau khi lấy ra.

## 4. Kiểm tra *tên tham số có xuất hiện* bằng Servlet API

Khi `@RequestParam(required=false) String name` trả `null`, phần lớn ứng dụng có thể coi là thiếu. Nếu đã đăng ký formatter/binder có thể chuyển chuỗi rỗng thành null, hãy kiểm tra thêm **sự hiện diện của key**:

```java
@GetMapping("/inspect")
public String inspect(
        @RequestParam(name = "name", required = false) String name,
        jakarta.servlet.http.HttpServletRequest request) {

    boolean sent = request.getParameterMap().containsKey("name");
    if (!sent) {
        return "MISSING";
    }
    if (name == null) {
        return "PRESENT_BUT_CONVERTED_TO_NULL";
    }
    if (name.isEmpty()) {
        return "EMPTY";
    }
    return name.isBlank() ? "BLANK" : "VALUE";
}
```

`request.getParameterMap()` lấy **parameter hiện diện**, kể cả nếu chuỗi value rỗng. `request.getParameterValues("name")` hữu ích khi client gửi nhiều lần như `?name=a&name=b`; nhận một `String` đơn lẻ thường không phù hợp để quyết định ngữ nghĩa input lặp. Với API chỉ chấp nhận một giá trị, nên kiểm tra và từ chối tham số lặp khi điều đó ảnh hưởng nghiệp vụ.

Đừng parse thẳng `getQueryString()` để ra quyết định phổ thông nếu không cần: query string có phần mã hóa, lặp key và tương tác với form binding. Dùng Servlet API cho quan hệ **có tham số/không có tham số**; dùng raw query chỉ khi hợp đồng API yêu cầu cú pháp ở mức byte.

## 5. `@RequestBody` và JSON: missing field khác explicit null

`@RequestBody` đi qua `HttpMessageConverter` (thường là Jackson JSON converter khi có Jackson). Có **hai lớp vắng mặt** khác nhau:

- **Không có body HTTP**: request gửi 0 byte body.
- **Có JSON object body, nhưng không có một field**: ví dụ `{}`. Đây không phải “không có request body”.

### 5.1. Toàn bộ request body có bắt buộc không?

```java
@PostMapping(path = "/receive", consumes = "application/json")
public String receive(@RequestBody(required = false) MyDto body) {
    return body == null ? "NO_DESERIALIZED_OBJECT" : "OBJECT_RECEIVED";
}

public record MyDto(String name) {}
```

Với `@RequestBody` mặc định `required=true`, body vắng mặt thường dẫn tới **HTTP 400** trước khi chạy method. `required=false` cho phép thiếu body và có thể đưa `null` vào method. Tuy nhiên:

- JSON literal `null` cũng có thể được Jackson chuyển thành Java `null`. Ví dụ trên **không xác định chắc được** request không có byte body hay client gửi bốn ký tự `null`.
- JSON `{}` là object hợp lệ; với DTO record có `String name` thì `name` có thể bằng `null` nhưng bản thân DTO vẫn tồn tại.
- Body `{"name":null}` có thể tạo DTO với `name==null`, giống `{}`. Bạn đã mất dấu vết field có được gửi hay không.
- JSON sai cú pháp thường gây **400 Bad Request** (lỗi đọc body), không phải “body vắng mặt”.
- `required=false` không bỏ qua kiểm tra kiểu media (`Content-Type`) hoặc lỗi chuyển đổi JSON; không sử dụng nó để hợp thức hóa dữ liệu sai.

Khi cần kiểm tra **độ dài body HTTP thực sự** khác JSON literal `null`, phải chủ động thiết kế một tầng đọc raw body phù hợp với Servlet/request caching. Không đọc input stream lần hai sau khi converter đã tiêu thụ. Tránh coi `required=false` là giải pháp cho yêu cầu phân biệt mọi trạng thái.

### 5.2. Một DTO Java đơn giản thường gộp missing và null

```java
public record ProfileInput(String nickname, Integer age) {}
```

Với Jackson thông thường, nếu không có cấu hình bắt buộc đặc biệt:

| JSON gửi lên | `nickname` trong DTO | `age` trong DTO |
| --- | --- | --- |
| `{}` | `null` | `null` |
| `{"nickname":null}` | `null` | `null` |
| `{"nickname":""}` | `""` | `null` |
| `{"nickname":" "}` | `" "` | `null` |
| `{"age":0}` | `null` | `0` |

Điểm cốt lõi là DTO chỉ nắm **giá trị sau deserialization**. Jackson có các tùy chọn liên quan coercion, constructor, primitive và null-handling; kết quả có thể khác với cấu hình tùy chỉnh. Không lấy hành vi mặc định của DTO làm giao ước về PATCH nếu cần phân biệt “giữ nguyên” với “xóa”.

### 5.3. `JsonNode` giữ lại sự hiện diện của field

Nếu nghiệp vụ yêu cầu phân biệt missing/null/empty, có thể nhận object JSON bằng `JsonNode`:

```java
@PostMapping(path = "/field", consumes = "application/json")
public String field(@RequestBody com.fasterxml.jackson.databind.JsonNode root) {
    if (!root.isObject()) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "JSON object required");
    }

    com.fasterxml.jackson.databind.JsonNode field = root.get("nickname");

    if (field == null) {
        return "MISSING";              // không có key "nickname"
    }
    if (field.isNull()) {
        return "NULL";                 // {"nickname":null}
    }
    if (field.isTextual() && field.textValue().isEmpty()) {
        return "EMPTY";                // {"nickname":""}
    }
    if (field.isTextual() && field.textValue().isBlank()) {
        return "BLANK";                // {"nickname":"   "}
    }
    return "PRESENT_NONEMPTY";
}
```

`JsonNode.get("nickname") == null` thể hiện field vắng mặt. `root.has("nickname")` đúng nếu key tồn tại **kể cả khi value là explicit JSON null**. `root.hasNonNull("nickname")` chỉ đúng khi field tồn tại và value khác JSON null. `root.path("nickname")` trả `MissingNode` nếu vắng mặt, còn explicit `null` là `NullNode`; nếu dùng `path` phải kiểm tra `isMissingNode()` và `isNull()` riêng. Không nên gọi `asText()` vô điều kiện rồi dùng chuỗi trả về để suy ra key có tồn tại.

Với PATCH phức tạp, dùng DTO có cơ chế ghi nhận field nào xuất hiện (custom Jackson setter, presence flag hoặc JSON Merge Patch/JSON Patch theo hợp đồng riêng). Dùng `JsonNode` là một lựa chọn dễ hiểu cho ví dụ, nhưng trong hệ thống lớn cần validation, giới hạn field hợp lệ và tránh cập nhật trực tiếp entity bằng JSON tùy ý.

## 6. Validation: `@NotNull`, `@NotEmpty`, `@NotBlank` khác nhau

Dùng Jakarta Bean Validation với Spring Boot 3 và dependency `spring-boot-starter-validation`. Khi dùng `@Valid @RequestBody` cho DTO phù hợp, lỗi validation thường thành **HTTP 400** thông qua `MethodArgumentNotValidException` theo cấu hình MVC mặc định.

| Annotation | Kiểm tra ý nghĩa | `null` | `""` | `"   "` |
| --- | --- | --- | --- | --- |
| `@NotNull` | Giá trị không phải null | Không hợp lệ | Hợp lệ | Hợp lệ |
| `@NotEmpty` | Không null và không rỗng; áp dụng chuỗi/collection/array/map được hỗ trợ | Không hợp lệ | Không hợp lệ | Hợp lệ |
| `@NotBlank` | Không null và chuỗi có ít nhất một ký tự không phải khoảng trắng | Không hợp lệ | Không hợp lệ | Không hợp lệ |

Ví dụ DTO tạo mới:

```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateProfile(
        @NotBlank String nickname,
        @NotNull @Positive Integer age) {}
```

- `nickname` thiếu, explicit null, `""` hoặc `"   "` đều không hợp lệ theo `@NotBlank`.
- `age` thiếu/null bị `@NotNull` từ chối; `0` hoặc số âm bị `@Positive` từ chối.
- `Integer` cho phép biểu diễn thiếu/null trước validation. Với primitive `int`, Jackson có thể gán mặc định `0` (tùy coercion) và làm mất dấu vết null, nên cân nhắc wrapper khi cần quy tắc “bắt buộc”.
- `@Size(min=1)` không đồng nghĩa `@NotNull`: với nhiều constraint như `@Size`, `null` được xem là hợp lệ để tách kiểm tra tồn tại khỏi giới hạn độ dài.

Nếu ràng buộc đặt trực tiếp trên controller method parameter (ví dụ `@RequestParam @NotBlank String name`), đó có thể là **method validation** với đường xử lý ngoại lệ khác (`HandlerMethodValidationException` trong Spring MVC 6.1+). Cần kiểm tra cấu hình Spring thực tế; bài này sử dụng `@Valid` với DTO để có kiểm thử ổn định hơn.

## 7. Ví dụ ứng dụng đầy đủ: Java 17+ và Spring Boot 3.x

Đoạn dưới là một controller **biên dịch trong dự án Spring Boot có Spring MVC, Jackson, Jakarta Validation**. Đặt ở `src/main/java/com/example/demo/InputController.java`. Để có một ứng dụng chạy, lớp `@SpringBootApplication` ở cùng package hoặc package cha và dependency Web/Validation là cần thiết.

```java
package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/inputs")
public class InputController {

    @GetMapping("/query")
    public QueryView query(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "count") Optional<Integer> count,
            HttpServletRequest request) {

        boolean supplied = request.getParameterMap().containsKey("q");
        String state;
        if (!supplied) {
            state = "MISSING";
        } else if (q == null) {
            state = "CONVERTED_TO_NULL";
        } else if (q.isEmpty()) {
            state = "EMPTY";
        } else if (q.isBlank()) {
            state = "BLANK";
        } else {
            state = "VALUE";
        }

        return new QueryView(state, q,
                count.map(String::valueOf).orElse("NO_COUNT"));
    }

    @GetMapping("/default")
    public Map<String, String> defaultQuery(
            @RequestParam(name = "q", defaultValue = "guest") String q) {
        return Map.of("q", q);
    }

    @GetMapping("/required")
    public Map<String, Long> required(@RequestParam("id") Long id) {
        return Map.of("id", id);
    }

    @PostMapping(path = "/json",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public FieldView inspectJson(@RequestBody JsonNode root) {
        if (!root.isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "JSON object required");
        }

        JsonNode nickname = root.get("nickname");
        String state;
        if (nickname == null) {
            state = "MISSING";
        } else if (nickname.isNull()) {
            state = "NULL";
        } else if (nickname.isTextual() && nickname.textValue().isEmpty()) {
            state = "EMPTY";
        } else if (nickname.isTextual() && nickname.textValue().isBlank()) {
            state = "BLANK";
        } else {
            state = "PRESENT_NONEMPTY";
        }

        String textValue = (nickname != null && nickname.isTextual())
                ? nickname.textValue() : null;
        return new FieldView(state, textValue);
    }

    @PostMapping(path = "/optional-body",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> optionalBody(
            @RequestBody(required = false) JsonNode root) {
        return Map.of("state",
                root == null ? "NO_BODY_OR_JSON_NULL" : "HAS_JSON_VALUE");
    }

    @PostMapping(path = "/profiles",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> createProfile(
            @Valid @RequestBody CreateProfile body) {
        // Demo xác nhận dữ liệu hợp lệ, chưa lưu vào database.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "ACCEPTED"));
    }

    public record QueryView(String state, String q, String count) {}
    public record FieldView(String state, String value) {}

    public record CreateProfile(
            @NotBlank String nickname,
            @NotNull @Positive Integer age) {}
}
```

Cần dependency `spring-boot-starter-web` (cung cấp Web MVC và Jackson trong cấu hình Boot phổ biến) và `spring-boot-starter-validation` (cho Jakarta Bean Validation). Tạo lớp khởi động nếu dự án chưa có:

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

Tệp thứ hai: `src/main/java/com/example/demo/DemoApplication.java`. Chạy dự án Maven đã cấu hình Spring Boot:

```bash
./mvnw spring-boot:run
```

Trên Windows PowerShell: `.\mvnw.cmd spring-boot:run`. Vì controller dùng Spring, Jackson và Jakarta Validation, **không thể chạy chỉ bằng `javac InputController.java` thiếu thư viện**; lệnh trên áp dụng cho một dự án Boot có `pom.xml` và Maven Wrapper phù hợp. Cấu hình mặc định chạy trên cổng 8080 nếu không đổi `server.port`.

## 8. Thực hành curl và kết quả có thể kiểm tra

Các lệnh dưới dùng `curl` trên bash/zsh; Windows PowerShell có thể dùng `curl.exe` và viết trên một dòng.

### 8.1. Query parameter

**Không truyền `q`:**

```bash
curl -s "http://localhost:8080/inputs/query"
```

JSON dự kiến theo Jackson mặc định:

```json
{"state":"MISSING","q":null,"count":"NO_COUNT"}
```

**Truyền chuỗi rỗng `q=`:**

```bash
curl -s "http://localhost:8080/inputs/query?q="
```

```json
{"state":"EMPTY","q":"","count":"NO_COUNT"}
```

**Truyền một dấu cách đã URL encode:**

```bash
curl -s "http://localhost:8080/inputs/query?q=%20"
```

```json
{"state":"BLANK","q":" ","count":"NO_COUNT"}
```

**Giá trị thường và số hợp lệ:**

```bash
curl -s "http://localhost:8080/inputs/query?q=Lan&count=3"
```

```json
{"state":"VALUE","q":"Lan","count":"3"}
```

Các JSON trên thể hiện **giá trị trường**, không cam kết thứ tự key theo bộ serializer. Nếu ứng dụng đổi cấu hình Jackson để bỏ field null, `"q":null` có thể không xuất hiện; trường `state` vẫn minh họa phân biệt.

**Tham số số rỗng:** `GET /inputs/query?count=` → `count` là `Optional.empty()` theo converter mặc định, nên body `count` là `"NO_COUNT"`. **Tham số số sai định dạng:** `GET /inputs/query?count=abc` → thường HTTP 400 do không chuyển thành Integer.

### 8.2. Giá trị mặc định

```bash
curl -s "http://localhost:8080/inputs/default"
curl -s "http://localhost:8080/inputs/default?q="
curl -s "http://localhost:8080/inputs/default?q=Lan"
```

Theo thứ tự, `q` trong JSON là `"guest"`, `"guest"`, `"Lan"`. Đây là ví dụ cho thấy `defaultValue` đã cố ý gộp missing và empty.

### 8.3. Trường JSON: missing, null, empty, blank

```bash
curl -s http://localhost:8080/inputs/json \
  -H "Content-Type: application/json" \
  --data '{}'
```

`state` dự kiến: `MISSING`.

```bash
curl -s http://localhost:8080/inputs/json \
  -H "Content-Type: application/json" \
  --data '{"nickname":null}'
```

`state` dự kiến: `NULL`.

```bash
curl -s http://localhost:8080/inputs/json \
  -H "Content-Type: application/json" \
  --data '{"nickname":""}'
```

`state` dự kiến: `EMPTY`.

```bash
curl -s http://localhost:8080/inputs/json \
  -H "Content-Type: application/json" \
  --data '{"nickname":"   "}'
```

`state` dự kiến: `BLANK`.

Ở đây `JsonNode` bảo toàn sự hiện diện của key: `get` trả Java null khi **không có key**, còn `{"nickname":null}` tạo node có `isNull() == true`.

### 8.4. Body bắt buộc và validation

```bash
curl -i http://localhost:8080/inputs/profiles \
  -H "Content-Type: application/json" \
  --data '{"nickname":"","age":18}'
```

Dự kiến **400** do `@NotBlank` từ chối nickname rỗng. Với input hợp lệ:

```bash
curl -i http://localhost:8080/inputs/profiles \
  -H "Content-Type: application/json" \
  --data '{"nickname":"Lan","age":18}'
```

Dự kiến **201 Created** và body JSON chứa `"status":"ACCEPTED"`.

`POST /inputs/optional-body` với `Content-Type: application/json` nhưng không có body được nhận vì `required=false`. Nếu gửi body JSON literal `null`, converter cũng có thể cho ra Java `null`; endpoint sẽ trả cùng trạng thái `NO_BODY_OR_JSON_NULL`. Đây là **giới hạn chủ ý** của kiểu đã giải mã, không phải bằng chứng HTTP body giống nhau.

## 9. Test bằng MockMvc: xác minh từng trạng thái

Thêm `spring-boot-starter-test` làm dependency test. Ví dụ sau đặt tại `src/test/java/com/example/demo/InputControllerTest.java`, chạy với Boot 3.x và không thêm custom security/filter vào môi trường test:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InputController.class)
class InputControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void distinguishesMissingEmptyAndBlankQuery() throws Exception {
        mvc.perform(get("/inputs/query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MISSING"));

        mvc.perform(get("/inputs/query").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EMPTY"));

        mvc.perform(get("/inputs/query").param("q", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("BLANK"));
    }

    @Test
    void optionalNumberKeepsValidValueButAllowsMissing() throws Exception {
        mvc.perform(get("/inputs/query").param("count", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value("3"));

        mvc.perform(get("/inputs/query").param("count", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value("NO_COUNT"));

        mvc.perform(get("/inputs/query").param("count", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultValueCollapsesMissingAndEmpty() throws Exception {
        mvc.perform(get("/inputs/default"))
                .andExpect(jsonPath("$.q").value("guest"));

        mvc.perform(get("/inputs/default").param("q", ""))
                .andExpect(jsonPath("$.q").value("guest"));
    }

    @Test
    void missingOrBlankRequiredNumberIsRejected() throws Exception {
        mvc.perform(get("/inputs/required"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/inputs/required").param("id", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jsonFieldPresenceIsPreservedByJsonNode() throws Exception {
        mvc.perform(post("/inputs/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MISSING"));

        mvc.perform(post("/inputs/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NULL"));

        mvc.perform(post("/inputs/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EMPTY"));

        mvc.perform(post("/inputs/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\" \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("BLANK"));
    }

    @Test
    void validatesProfileOnCreate() throws Exception {
        mvc.perform(post("/inputs/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\" \",\"age\":18}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/inputs/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"Lan\",\"age\":18}"))
                .andExpect(status().isCreated());
    }

    @Test
    void optionalBodyAllowsNoBody() throws Exception {
        mvc.perform(post("/inputs/optional-body")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state")
                        .value("NO_BODY_OR_JSON_NULL"));
    }
}
```

Dùng `./mvnw test` hoặc Windows `.\mvnw.cmd test`. Đây là **bộ test có thể chạy trong dự án Boot đủ dependency**, không phải kết quả đã thực thi ở mọi môi trường. Nếu có security hoặc Jackson tùy chỉnh, cấu hình MockMvc tương ứng. Test cố ý kiểm tra field `state` thay vì giả định thứ tự JSON hoặc body lỗi Spring Boot cố định.

## 10. Các loại đầu vào controller khác

### `@RequestHeader` và `@CookieValue`

`@RequestHeader("X-Trace-Id")` mặc định required. Dùng `@RequestHeader(name = "X-Trace-Id", required = false) String traceId` nếu muốn cho phép thiếu; `Optional<String>` cũng được hỗ trợ. Header không có và header tồn tại với giá trị rỗng là hai trường hợp khái niệm khác nhau, nhưng proxy, browser và HTTP library có thể chuẩn hóa, loại bỏ hoặc từ chối header trống. Nếu dùng `defaultValue`, kiểu annotation tương ứng có thể gộp missing với empty tương tự request parameter.

`@CookieValue` có cơ chế required/default/Optional liên quan; cookie vắng mặt không đồng nghĩa cookie có nội dung rỗng. Cần chú ý decode, xác thực và giá trị mặc định theo giao ước cookie.

### `@PathVariable`

`@PathVariable("id") Long id` gắn với **URL pattern đã match**, ví dụ `/items/{id}`. URL `/items` không tự mang một `id=null` tới mapping chỉ nhận `/items/{id}`; thông thường đó là **path không match (404)**, chứ không nhất thiết là lời gọi method với id thiếu. Nếu muốn hỗ trợ cả hai dạng URL, hãy khai báo mapping tương ứng rõ ràng và cân nhắc xử lý `required=false`/`Optional`.

Giá trị `/items/abc` với `Long id` có thể dẫn đến 400 vì lỗi chuyển kiểu, trong khi `/items/` có thể không match path pattern. Đây là lỗi khác nhau: **không có route** và **có route nhưng input không chuyển kiểu được**.

### `@ModelAttribute` và form data

Đối tượng `@ModelAttribute` có thể nhận dữ liệu từ form/query và qua data binding. Field không được gửi có thể giữ `null` hoặc giá trị khởi tạo; field gửi rỗng thường là `""` với `String` (trừ custom binder), còn trường số có thể trở thành null hoặc lỗi conversion. Khi cần đánh dấu field nào thực sự có mặt trong form, hãy kiểm tra tham số request hay thiết kế DTO có cơ chế lưu thông tin hiện diện. Dùng `@Valid` và `BindingResult` nếu cần xử lý lỗi binding/validation trong method.

Trong Spring MVC, dữ liệu form `application/x-www-form-urlencoded` thường dùng `@RequestParam`/`@ModelAttribute`. Đừng kỳ vọng `@RequestBody` đọc form qua Servlet API giống đọc JSON trong mọi môi trường.

### Multipart file

`@RequestParam(name="file", required=false) MultipartFile file`: nếu không có part, thường nhận `null`; nếu có part chứa file kích thước 0, có thể nhận đối tượng `MultipartFile` với `file.isEmpty() == true`. `MultipartFile.isEmpty()` cũng có thể đúng trong những trường hợp không chọn file cho form; vì vậy khi nghiệp vụ cần phân biệt “không upload” và “upload file 0 byte”, cần xét **sự tồn tại part**, `getSize()`, filename và quy tắc HTTP/form thực tế, không chỉ gọi `isEmpty()`. Đối với upload thực, còn cần giới hạn kích thước và kiểm tra loại file.

## 11. Áp dụng cho CREATE, UPDATE và PATCH

Một hợp đồng cập nhật từng phần hay dùng ba trạng thái:

| JSON request field | Ý định ví dụ khi PATCH profile |
| --- | --- |
| Không có `nickname` | **Giữ nguyên** nickname đang lưu |
| Có `"nickname":null` | **Xóa** nickname, nếu nghiệp vụ cho phép nullable |
| Có `"nickname":""` | **Đặt thành chuỗi rỗng** hoặc báo validation error, theo quy tắc API |
| Có `"nickname":"Lan"` | **Đổi** nickname thành `Lan` |

Nếu deserialize thẳng ba request đầu vào DTO `record PatchProfile(String nickname)`, hai trường hợp missing và explicit null có thể cùng đưa ra Java null. Code như `if (nickname == null) skip` sẽ không thể thực hiện yêu cầu “explicit null nghĩa là xóa”.

Giải pháp phù hợp tùy dự án:

- **Đơn giản, ít field:** nhận `JsonNode` và kiểm tra `has`/`isNull`/`isTextual`, sau đó chuyển sang command/DTO đã validate.
- **DTO rõ kiểu và nhiều field:** dùng Jackson setter/presence flags hoặc cấu trúc wrapper ba trạng thái (`ABSENT`/`EXPLICIT_NULL`/`VALUE`) và converter có kiểm thử.
- **Theo chuẩn cập nhật:** dùng JSON Merge Patch (RFC 7396) hoặc JSON Patch (RFC 6902), khai báo `Content-Type` và ngữ nghĩa riêng. Theo JSON Merge Patch, null trong patch **xóa member**; cần đọc đúng chuẩn trước khi chọn.
- **PUT toàn phần:** hợp đồng thường yêu cầu representation đầy đủ; quy tắc field missing/null nên được chỉ rõ và validate theo yêu cầu resource, tránh tự áp nghĩa PATCH vào PUT.

Validation cho CREATE có thể nghiêm ngặt hơn PATCH. Ví dụ `@NotBlank` phù hợp nickname bắt buộc khi tạo mới; PATCH cần cho phép **vắng mặt** để không sửa, nhưng khi field đã có mặt thì vẫn kiểm tra điều kiện (trừ khi explicit null được phép để xóa). Đừng tùy tiện áp dụng cùng DTO/ràng buộc cho cả hai nghiệp vụ.

## 12. Checklist chẩn đoán sai dữ liệu đầu vào

1. **Ghi lại request chính xác**: method, URL đã được encode, `Content-Type`, raw body, tên parameter lặp. Đừng suy luận từ object Java sau binding rằng client đã gửi gì.
2. **Xác định nguồn**: query/form dùng `@RequestParam`; JSON body dùng `@RequestBody`; path dùng `@PathVariable`; header dùng `@RequestHeader`. Việc thiếu ở mỗi nguồn dẫn đến khác nhau.
3. **Phân biệt hai bước**: “có field/parameter trong request” và “giá trị còn lại sau conversion/deserialization”. Khi convert chuỗi rỗng thành null, thông tin đã biến mất.
4. **Kiểm tra default và binder**: `defaultValue` gộp missing/empty; `StringTrimmerEditor` hoặc `@InitBinder` có thể đổi `""` hay chuỗi khoảng trắng thành null; custom Jackson coercion có thể đổi giá trị JSON.
5. **Nhìn status 400 và exception cụ thể**: `MissingServletRequestParameterException` thường là thiếu required param; lỗi chuyển kiểu có thể là `MethodArgumentTypeMismatchException`; lỗi JSON parse thường liên quan `HttpMessageNotReadableException`; validation DTO thường là `MethodArgumentNotValidException`. Handler tùy chỉnh có thể đổi body/status.
6. **Không lạm dụng primitive**: `int age` có thể không thể hiện null; dùng `Integer` + `@NotNull` khi thực sự cần kiểm tra bắt buộc, đồng thời lưu ý Jackson coercion.
7. **Không coi Optional là bộ xác thực**: Optional thể hiện presence sau binding, vẫn phải validate dữ liệu bên trong.
8. **Không coi empty là null theo mặc định**: chuỗi `""`, chuỗi trắng, `[]`, `{}`, `null` và field vắng mặt là các trường hợp khác nhau. Chỉ chuẩn hóa khi hợp đồng nghiệp vụ cho phép.
9. **Không đưa giá trị null vào `Map.of`**: Java `Map.of` không chấp nhận null key/value; nếu cần response chứa field null, dùng DTO/record (như demo) hoặc `LinkedHashMap`/`HashMap` hỗ trợ null.
10. **Kiểm thử theo ma trận rõ ràng**: ít nhất missing, empty, blank, null (nếu JSON), valid, malformed và multi-value (nếu query cho phép lặp). Kiểm tra trạng thái trả về lẫn tác dụng nghiệp vụ.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** `@RequestParam(required=false) String q` nhận gì khi không có `q` và khi `?q=`?

**Đáp án:** Với binder mặc định: `null` khi thiếu; `""` khi có tham số nhưng không có ký tự.

**Câu 2.** `@RequestParam(defaultValue="all")` có giữ được khác biệt giữa thiếu và chuỗi rỗng không?

**Đáp án:** Không. Cả hai thường nhận `"all"`.

**Câu 3.** `Optional<Integer> count` có biến `?count=abc` thành `Optional.empty()` không?

**Đáp án:** Không. Chuỗi không phải số thường gây lỗi conversion HTTP 400; Optional chỉ đại diện thiếu giá trị hợp lệ sau binding.

**Câu 4.** `record Patch(String name)` có phân biệt JSON `{}` và `{"name":null}` không?

**Đáp án:** Với deserialization thông thường, không: `name` đều null. Dùng `JsonNode` hoặc cơ chế theo dõi field hiện diện.

**Câu 5.** `@NotNull String name` có từ chối chuỗi `""` hoặc `"   "` không?

**Đáp án:** Không. Dùng `@NotBlank` nếu muốn từ chối null, empty và chỉ-khoảng-trắng.

**Câu 6.** Nếu mapping `/items/{id}` không có đường dẫn `/items`, `@PathVariable(required=false)` có bảo đảm GET `/items` chạy method không?

**Đáp án:** Không. URL phải khớp mapping trước khi method parameter được resolve; hãy khai báo đường dẫn `/items` riêng hoặc mapping hỗ trợ cả hai.

## Tài liệu tham khảo

- Spring Framework — `@RequestParam`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html
- Spring Framework — Controller Method Arguments và `Optional`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html
- Spring Framework — Type Conversion (empty String → null sau conversion): https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/typeconversion.html
- Spring Framework — `@RequestBody` và Validation: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html
- Spring Framework — `@ModelAttribute`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html
- Spring Framework — `@RequestHeader`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestheader.html
- Jackson Databind `JsonNode` API: https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/latest/com/fasterxml/jackson/databind/JsonNode.html
- Jakarta Bean Validation `@NotNull`, `@NotBlank`, `@NotEmpty`: https://jakarta.ee/specifications/bean-validation/
- JSON Merge Patch (RFC 7396): https://www.rfc-editor.org/rfc/rfc7396


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="controller-inputs-010">Quy tắc chọn nơi lấy tham số và giới hạn đọc request body nhiều lần</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc chọn nơi lấy tham số và giới hạn đọc request body nhiều lần trong Spring MVC

## 1. Vì sao phải phân biệt nguồn dữ liệu của request?

Một HTTP request có **nhiều nơi chứa dữ liệu**: biến đường dẫn, query string, header, cookie, form field, multipart part, JSON/XML body, request attribute và session attribute. Chúng có mục đích khác nhau. Spring MVC không tự coi mọi dữ liệu là một tập giá trị đồng nhất; **annotation trên tham số handler** là cách nói rõ nên lấy ở đâu và chuyển kiểu thế nào.

Ví dụ request:

~~~http
POST /api/orders/42?notify=true HTTP/1.1
Host: localhost:8080
Content-Type: application/json
X-Trace-Id: abc-123
Cookie: locale=vi

{"sku":"BOOK-1","quantity":2}
~~~

Các nguồn khác nhau được đọc như sau:

| Giá trị | Nguồn HTTP | Cách nhận rõ ràng trong Spring MVC |
| --- | --- | --- |
| `42` | URI template `/orders/{orderId}` | `@PathVariable("orderId") long orderId` |
| `true` | Query `?notify=true` | `@RequestParam("notify") boolean notify` |
| `abc-123` | Header `X-Trace-Id` | `@RequestHeader("X-Trace-Id") String traceId` |
| `vi` | Cookie `locale` | `@CookieValue("locale") String locale` |
| `{"sku":"BOOK-1","quantity":2}` | JSON request body | `@RequestBody OrderInput input` |

**Quan trọng:** `@RequestParam` trong **Spring MVC (Servlet)** đọc *Servlet request parameters*. Tập này có thể bao gồm query string **và** trường form `application/x-www-form-urlencoded`, chứ không có nghĩa chỉ query. Phía Spring WebFlux có quy ước khác về nguồn `@RequestParam`, vì vậy phải biết mình đang dùng stack nào.

Mục tiêu bài học:
1. Xác định nguồn bind theo annotation và quy tắc mặc định khi bỏ annotation.
2. Chọn annotation đúng cho JSON, form và multipart; tách metadata khỏi payload.
3. Hiểu vì sao request body thông thường là luồng tiêu thụ một lần, và tương tác với việc phân tích form.
4. Tổ chức validation, logging/filter, caching và kiểm thử sao cho body không bị đọc mất trước controller.

Ví dụ tập trung vào **Spring MVC** với Java 17+, Spring Framework 6.x và Spring Boot 3.x, sử dụng Jakarta Servlet API (`jakarta.servlet`).

## 2. Spring chọn resolver cho một tham số như thế nào?

Khi `DispatcherServlet` tìm thấy handler method phù hợp, `RequestMappingHandlerAdapter` sử dụng một chuỗi `HandlerMethodArgumentResolver` để xử lý từng tham số. Resolver nào hỗ trợ kiểu/annotation sẽ tạo ra giá trị cho tham số đó. Các resolver sau có thể chuyển kiểu, bind object, giải mã body hay báo lỗi nếu dữ liệu không hợp lệ.

Sơ đồ khái niệm:

~~~text
HTTP request
    │
    ├── URL path / query ───────────────┐
    ├── headers / cookies ──────────────┤
    ├── body (JSON, form, multipart) ───┤
    ├── request attributes / session ───┤
    └── servlet context ────────────────┘
                                         ▼
                            HandlerMethodArgumentResolver
                            (xem annotation + Java type)
                                         │
                         ┌───────────────┼───────────────┐
                         ▼               ▼               ▼
                   URL/metadata     data binding    message converter
                         │               │               │
                  @PathVariable     @ModelAttribute   @RequestBody
                  @RequestParam      form/query       @RequestPart
                  @RequestHeader                      JSON/XML/part
                         └───────────────┬───────────────┘
                                         ▼
                                  Controller method
~~~

Đây là sơ đồ cơ chế, không phải một thứ tự ưu tiên cố định giữa các **nguồn HTTP**. Annotation được xử lý bởi resolver tương ứng; không có quy tắc chung kiểu “nếu thiếu ở path thì thử header rồi thử JSON”.

### 2.1. Bảng chọn nguồn

| Nhu cầu | Tham số controller | Lưu ý |
| --- | --- | --- |
| ID của tài nguyên nằm trong route | `@PathVariable("id") Long id` | Tên phải khớp `{id}` trong route |
| Giá trị đơn / nhiều giá trị từ query hoặc form | `@RequestParam("tag") List<String> tags` | Servlet params có thể gộp query và form |
| Header HTTP | `@RequestHeader("If-Match") String value` | Tên header HTTP không phân biệt hoa/thường |
| Cookie | `@CookieValue("theme") String theme` | Cookie là dữ liệu client gửi, không tự đáng tin |
| Thuộc tính do filter/interceptor đặt | `@RequestAttribute("requestId") String id` | Khác HTTP header và query |
| Giá trị đã đặt trong HTTP session | `@SessionAttribute("cartId") String id` | Khác `@SessionAttributes` dành cho model workflow |
| Dữ liệu form/query đưa vào DTO | `@ModelAttribute SearchForm form` | Data binding qua constructor/setter |
| JSON/XML body thành DTO | `@RequestBody CreateOrder input` | `HttpMessageConverter` tiêu thụ body |
| Cả body và headers | `HttpEntity<CreateOrder> request` | Cũng đọc body qua converter |
| File trong multipart | `@RequestParam("file") MultipartFile file` | Có thể dùng `@RequestPart` |
| Một part JSON trong multipart | `@RequestPart("metadata") Metadata metadata` | Đọc nội dung của *part* với converter |
| Truy cập dữ liệu thô từ Servlet | `HttpServletRequest`, `InputStream`, `Reader` | Cần hiểu trạng thái đã tiêu thụ body |
| Context/framework object | `Principal`, `Locale`, `HttpSession`, `Model` | Đây là đối tượng Spring/Servlet cung cấp, không bind từ JSON |

Không mặc định dùng `@RequestBody` cho tất cả đầu vào: path, query, header và cookie vẫn là những nguồn độc lập, có thể nhận cùng lúc với **một** DTO JSON.

### 2.2. Nếu không khai báo annotation?

Spring MVC vẫn có quy tắc dự phòng cho tham số mà các resolver chuyên biệt chưa nhận:
- Kiểu đơn giản theo `BeanUtils.isSimpleProperty` (như `String`, số, primitive, enum, nhiều kiểu thời gian phù hợp) thường được xử lý như `@RequestParam`.
- Kiểu phức tạp chưa được resolver khác hỗ trợ thường được xử lý như `@ModelAttribute`.

Ví dụ:

~~~java
@GetMapping("/search")
public String search(String q, SearchCriteria criteria) {
    // q thường lấy từ Servlet request parameters;
    // criteria thường được bind như @ModelAttribute.
    return "ok";
}
~~~

Đây là **đoạn minh họa**, với `SearchCriteria` phải là kiểu có thể bind. Không hiểu nhầm rằng `SearchCriteria` tự động nhận JSON body; muốn JSON thì cần `@RequestBody`. Nên viết annotation tường minh trên public API, đặc biệt khi triển khai có nhiều nguồn dữ liệu hoặc cần AOT/native image. Nếu bỏ tên trong annotation, việc suy ra tên Java parameter còn phụ thuộc metadata biên dịch; khai báo tên rõ giúp tránh lỗi khi đổi cấu hình compiler.

### 2.3. `required`, `defaultValue`, `Optional` và chuyển kiểu

~~~java
@GetMapping("/catalog")
public String catalog(
        @RequestParam("q") String q,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
    return q + ":" + page + ":" + requestId;
}
~~~

Ở đây `q` được yêu cầu mặc định; thiếu `q` thường bị Spring trả `400 Bad Request`. `page` có mặc định và được chuyển từ chuỗi sang `int`; nhập giá trị không phải số có thể phát sinh lỗi bind, thường là `400`. Header `X-Request-Id` tùy chọn nên có thể nhận `null`. Với annotation cho phép `required`, một tham số `Optional<T>` cũng thường diễn đạt sự vắng mặt. Chọn tên và điều kiện rõ ràng, rồi kiểm thử request thực tế.

Các giới hạn quan trọng:
- `@RequestParam Map<String,String>` **không ghi tên** có thể thu thập nhiều tham số; `MultiValueMap<String,String>` giữ được nhiều giá trị cho một tên.
- `@PathVariable` không được thay thế bằng `@RequestParam` dù hai giá trị đều là chuỗi có thể chuyển kiểu.
- Nếu form và query cùng có một tên, hành vi lấy giá trị phụ thuộc cách Servlet container gom request parameters; không thiết kế API dựa trên việc “query chắc chắn thắng form”.
- Chữ ký method dùng `@RequestBody Foo` với JSON phải khai báo converter phù hợp (Jackson thường có sẵn từ `spring-boot-starter-web`). `Content-Type` và `Accept` còn ảnh hưởng việc chọn endpoint/converter.

## 3. Chọn đúng cách đọc theo Content-Type

### 3.1. GET với query và path

~~~http
GET /api/products/15?sort=price&tag=java&tag=spring HTTP/1.1
Accept: application/json
~~~

Chọn `@PathVariable("id") long id`, `@RequestParam("sort") String sort` và `@RequestParam("tag") List<String> tags`. Không cần request body cho GET thông thường. Đừng đặt nguồn filter/sort trong JSON body rồi trông chờ mọi client, proxy, cache xử lý thống nhất.

### 3.2. POST JSON (`application/json`)

~~~http
POST /api/products?dryRun=true HTTP/1.1
Content-Type: application/json

{"name":"Spring Guide","price":100}
~~~

Chọn **một DTO `@RequestBody`** cho JSON và `@RequestParam("dryRun")` cho query. Bộ giải mã JSON tạo object từ body. Mặc định `@RequestBody` có `required=true`; body rỗng hoặc JSON lỗi thường gây lỗi đọc, dẫn đến HTTP 400 nếu không có custom error handler.

**Sai về mặt nguồn:** `@RequestParam("name") String name` không tự lấy trường `name` bên trong JSON. JSON là body có cấu trúc, không phải Servlet form parameters.

### 3.3. Form urlencoded (`application/x-www-form-urlencoded`)

~~~http
POST /api/products/form HTTP/1.1
Content-Type: application/x-www-form-urlencoded

name=Spring+Guide&quantity=2
~~~

Nên dùng `@RequestParam` cho từng field hoặc `@ModelAttribute FormInput` để bind nhiều field vào DTO. Với Servlet API, truy cập parameters có thể **kích hoạt việc parse POST form body**. Spring khuyến nghị xử lý form theo cách này thay vì dùng `@RequestBody` nếu sau đó còn cần request parameters, vì body có thể đã bị tiêu thụ bởi việc parse form.

Đây là lý do **không thể mặc định “đọc lại raw form body”** sau khi gọi `request.getParameter("name")`. Khi trộn form parsing và trực tiếp đọc stream, hành vi có thể phụ thuộc Servlet container, thứ tự xử lý và các filter.

### 3.4. Multipart (`multipart/form-data`)

~~~text
Part "metadata" (Content-Type: application/json): {"source":"batch"}
Part "file"     (Content-Type: text/plain): bytes của tệp
~~~

Chọn `@RequestPart("metadata") Metadata meta` để convert part JSON và `@RequestPart("file") MultipartFile file` hoặc `@RequestParam("file") MultipartFile file` cho tệp. Mỗi **part** có header/body riêng; đừng cố dùng một `@RequestBody CreateRequest` để nhận trọn multipart thành JSON DTO.

Multipart resolver/container có thể parse request trước khi vào controller. Nội dung tệp lớn cần quản lý kích thước, dung lượng tạm và phương án streaming phù hợp; tránh ép toàn bộ multipart vào một `byte[]` chỉ để log.

## 4. Ví dụ chạy được: bốn loại đầu vào trong một controller

Ví dụ ứng dụng **Spring Boot 3.x, Java 17+**, với `spring-boot-starter-web` và `spring-boot-starter-test`. Package root `com.example.inputdemo`.

~~~text
src/main/java/com/example/inputdemo/
├── InputDemoApplication.java
└── web/InputController.java
src/test/java/com/example/inputdemo/web/
└── InputControllerTest.java
~~~

Ứng dụng khởi chạy, đặt tại `src/main/java/com/example/inputdemo/InputDemoApplication.java`:

~~~java
package com.example.inputdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InputDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(InputDemoApplication.class, args);
    }
}
~~~

Controller đặt tại `src/main/java/com/example/inputdemo/web/InputController.java`:

~~~java
package com.example.inputdemo.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/input")
public class InputController {

    @GetMapping("/items/{id}")
    public ItemView find(
            @PathVariable("id") long id,
            @RequestParam(name = "mode", defaultValue = "summary") String mode,
            @RequestHeader(name = "X-Trace-Id", required = false) String traceId,
            @CookieValue(name = "lang", required = false) String lang) {
        return new ItemView(id, mode, traceId, lang);
    }

    @PostMapping(path = "/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CreatedItem create(
            @RequestParam(name = "source", defaultValue = "web") String source,
            @RequestBody NewItem input) {
        return new CreatedItem(input.title(), input.quantity(), source);
    }

    @PostMapping(path = "/forms",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public FormResult submit(@ModelAttribute FormInput form) {
        return new FormResult(form.getTitle(), form.getQuantity());
    }

    @PostMapping(path = "/uploads",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResult upload(
            @RequestPart("metadata") ImportMetadata metadata,
            @RequestPart("file") MultipartFile file) {
        return new UploadResult(metadata.source(), file.getSize());
    }

    public record ItemView(long id, String mode, String traceId, String lang) {}
    public record NewItem(String title, int quantity) {}
    public record CreatedItem(String title, int quantity, String source) {}
    public record FormResult(String title, int quantity) {}
    public record ImportMetadata(String source) {}
    public record UploadResult(String source, long bytes) {}

    // JavaBean dùng riêng cho form binding; không expose JPA entity.
    public static class FormInput {
        private String title;
        private int quantity;

        public FormInput() {}

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
~~~

Những điểm cần quan sát:
- `GET /api/input/items/{id}` không đọc body: `id` từ path, `mode` từ request parameters, `traceId` từ header, `lang` từ cookie.
- `POST /api/input/items?source=mobile` có thể đồng thời nhận **query parameter** và **JSON body**; converter đọc body đúng một lần vào `NewItem`.
- `POST /api/input/forms` dùng `@ModelAttribute`: Spring khởi tạo `FormInput` rồi bind các trường `title`/`quantity` từ parameters (trong tình huống này là form).
- `POST /api/input/uploads` dùng `@RequestPart`: Spring giải mã metadata JSON của một part, file được truy cập dưới dạng `MultipartFile`; ví dụ chỉ trả `size`, không lưu file.

Các DTO cố ý tối giản để minh họa nguồn bind. Mẫu này **chưa** khai báo validation như `@NotBlank`, `@Positive` và chưa xử lý dữ liệu ngoài phạm vi/giá trị null. Trong ứng dụng thực tế, thêm `spring-boot-starter-validation`, `@Valid` và constraint cho request DTO; không chỉ dựa vào kiểu Java để kết luận request hợp lệ.

### 4.1. Gọi HTTP và kết quả

Ở project đã cấu hình Spring Boot Gradle plugin, chạy:

~~~bash
./gradlew bootRun
~~~

Có thể thử bằng curl:

~~~bash
curl -s -H 'X-Trace-Id: t-1' -H 'Cookie: lang=vi' \
  'http://localhost:8080/api/input/items/7?mode=detail'

curl -s -X POST \
  'http://localhost:8080/api/input/items?source=mobile' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Java","quantity":2}'

curl -s -X POST 'http://localhost:8080/api/input/forms' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'title=Spring&quantity=3'

curl -s -X POST 'http://localhost:8080/api/input/uploads' \
  -F 'metadata={"source":"batch"};type=application/json' \
  -F 'file=@notes.txt;type=text/plain'
~~~

Ba response đầu với input trên là:

~~~json
{"id":7,"mode":"detail","traceId":"t-1","lang":"vi"}
~~~

~~~json
{"title":"Java","quantity":2,"source":"mobile"}
~~~

~~~json
{"title":"Spring","quantity":3}
~~~

Ví dụ multipart trả `{"source":"batch","bytes":N}`, trong đó `N` đúng bằng số byte của file `notes.txt` thực tế; không bịa một con số cố định khi nội dung file chưa xác định.

Nếu dùng Windows PowerShell, lựa chọn `curl.exe` cho cú pháp curl truyền thống; PowerShell có thể dùng alias `curl`/`Invoke-WebRequest` với cách truyền tham số khác. Trường hợp không có Unix shell, dùng công cụ HTTP client hỗ trợ form/multipart để gửi cùng request.

## 5. Request body là luồng một lần: cơ chế và hậu quả

### 5.1. Stream nằm ở đâu?

Body đến Servlet container qua kết nối HTTP. Ở tầng Servlet, request thường cấp `ServletInputStream` (`request.getInputStream()`) hoặc `BufferedReader` (`request.getReader()`). **Servlet API quy định chọn một trong hai cách**: đã dùng reader thì gọi input stream (hoặc ngược lại) có thể ném `IllegalStateException`. Đọc đến hết stream thông thường khiến lần đọc tiếp theo chỉ nhận EOF; API không đảm bảo nút “rewind”.

Khi handler có:

~~~java
@PostMapping("/orders")
public void create(@RequestBody OrderInput input) {
    // Spring đọc JSON từ body thông qua HttpMessageConverter.
}
~~~

Đoạn trên chỉ minh họa cơ chế (kiểu `OrderInput` chưa được khai báo trong snippet): việc giải mã `@RequestBody` tiêu thụ luồng. Sau đó không thể mặc nhiên `request.getInputStream()` và mong nhận lại JSON ban đầu.

Một handler có **hai** tham số `@RequestBody` cố lấy hai DTO từ cùng một JSON body là thiết kế sai phổ biến:

~~~java
// MINH HỌA SAI — đừng sử dụng:
@PostMapping("/broken")
public void broken(@RequestBody UserInput user, @RequestBody AuditInput audit) {}
~~~

Spring không tự phân đôi một JSON object thành hai lượt đọc độc lập. Thường converter đầu tiên đã tiêu thụ body, khiến tham số thứ hai lỗi thiếu body hoặc lỗi giải mã. Cách đúng: định nghĩa **một DTO bao** cho payload, hoặc bind một lần rồi truyền dữ liệu đã giải mã tới service/validator/audit.

~~~java
// MINH HỌA THIẾT KẾ ĐÚNG:
public record CreateUserRequest(UserInput user, AuditInput audit) {}

@PostMapping("/users")
public void create(@RequestBody CreateUserRequest request) {
    userService.create(request.user());
    auditService.record(request.audit());
}
~~~

Hai fragment trên là ví dụ thiết kế, **không phải Java source độc lập**: trong ứng dụng thật cần khai báo `UserInput`, `AuditInput` và service tương ứng.

### 5.2. Form parameter parsing cũng có thể đọc body

Riêng `application/x-www-form-urlencoded`, Servlet container có thể đọc/parse body khi mã gọi `getParameter()`/`getParameterMap()` hoặc khi Spring bind `@RequestParam`/`@ModelAttribute`. Bởi vậy:
- Filter đọc trước `request.getInputStream()` có thể khiến handler `@RequestBody` nhìn thấy body rỗng.
- Một filter gọi `getParameterMap()` để log form có thể làm raw body không còn sẵn để đọc theo cách khác.
- Đọc raw body trước rồi sau đó yêu cầu form parameters có thể làm việc parse form thất bại hoặc không đầy đủ.

Không kết luận rằng mọi Servlet container luôn biểu hiện giống nhau sau các thao tác sai thứ tự. Cách bền vững là dùng resolver thích hợp cho từng Content-Type và không phụ thuộc hai người tiêu thụ cùng stream.

### 5.3. Lỗi quan sát được

| Tình huống | Điều có thể xảy ra | Cách khắc phục |
| --- | --- | --- |
| Filter đọc toàn bộ stream trước controller | `@RequestBody` thấy EOF, lỗi body missing / HTTP 400 | Không tiêu thụ body trong filter; dùng caching wrapper hoặc API hook phù hợp |
| Hai tham số `@RequestBody` | Tham số sau không còn dữ liệu | Một request DTO và phân tách sau khi decode |
| `getReader()` rồi `getInputStream()` | `IllegalStateException` | Chỉ chọn một API của Servlet request |
| `getParameterMap()` trên form rồi đọc raw body | Body đã được parse, không thể đọc lại đáng tin cậy | Bind form bằng `@RequestParam` hoặc `@ModelAttribute` |
| Dùng `@RequestParam` đọc key nằm trong JSON | Không tìm thấy parameter | Đặt field trong `@RequestBody` DTO |
| JSON có `Content-Type` không tương thích | Có thể `415 Unsupported Media Type` | Gửi đúng Content-Type / mapping `consumes` |
| JSON cú pháp hỏng | `HttpMessageNotReadableException`, thường HTTP 400 | Kiểm tra JSON, DTO, converter, error handler |
| Request vượt limit server/proxy | Bị từ chối theo cấu hình, có thể HTTP 413 | Cấu hình giới hạn phù hợp; không buffer vô hạn |

Một số API có thể dùng `RequestBodyAdvice` để quan sát/biến đổi dữ liệu **ngay trong tiến trình conversion** và `HandlerInterceptor`/filter để gắn metadata. Những hook này vẫn phải tôn trọng thời điểm và trách nhiệm đọc body.

## 6. `ContentCachingRequestWrapper` giúp gì, và KHÔNG giúp gì?

Spring cung cấp `org.springframework.web.util.ContentCachingRequestWrapper`: nó giữ bản sao các byte **đã được đọc qua wrapper**; mã khác có thể xem `getContentAsByteArray()` sau khi request được xử lý.

Ba điều rất quan trọng:
1. Wrapper **không tự đọc** body khi mới tạo. Nếu downstream chưa tiêu thụ body, cache có thể trống.
2. `getContentAsByteArray()` lấy bản cache để quan sát, có thể giới hạn dung lượng. Nó **không làm `getInputStream()` trở thành luồng replayable** cho lượt đọc tiếp theo.
3. Nếu filter log body trước `chain.doFilter(...)`, cache có thể chưa có gì; thường cần kiểm tra cache **sau** khi controller/converter đã đọc. Với async processing cần xét thời điểm hoàn tất dispatch.

### 6.1. Ví dụ filter quan sát số byte đã được đọc

Đây là một file có thể biên dịch trong ứng dụng Spring Boot 3.x có `spring-boot-starter-web`. Chỉ log **số byte đã cache** để tránh ghi dữ liệu nhạy cảm.

`src/main/java/com/example/inputdemo/web/BodyAuditFilter.java`:

~~~java
package com.example.inputdemo.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Component
public class BodyAuditFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(BodyAuditFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        ContentCachingRequestWrapper cached =
                new ContentCachingRequestWrapper(request, 8192);

        try {
            chain.doFilter(cached, response);
        } finally {
            log.debug("HTTP {} {}: bytes cached = {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    cached.getContentAsByteArray().length);
        }
    }
}
~~~

Phân tích:
- `chain.doFilter(cached, response)` chuyển wrapper xuống Spring MVC. Nếu controller giải mã JSON, wrapper sao chép các byte đã đi qua nó; controller vẫn dùng body theo quy trình bình thường.
- Sau `chain.doFilter`, ví dụ chỉ ghi số byte được cache. Cache limit `8192` là **giới hạn giữ bản sao**, không phải quy tắc từ chối tất cả request lớn hơn 8192 byte.
- Không có downstream đọc body thì `bytes cached = 0` là tình huống hợp lệ.
- Không dùng wrapper này làm cơ chế đọc body lần hai; nó cũng không thay thế chính sách request size limit.
- Nếu cần log payload để gỡ lỗi, phải hạn chế kích thước, che token/password/cookie/PII, phân quyền log và kiểm tra xử lý encoding. Không ghi raw body của request chứa bí mật vào production log.

### 6.2. Nếu thật sự cần đọc nhiều lần?

Thứ tự ưu tiên thiết kế:

1. **Đọc và giải mã đúng một lần** trong controller; truyền DTO/byte[] đã có tới nhiều service, validator hoặc component. Đây là phương án đơn giản nhất.
2. Nếu nhiều thành phần cần quan sát lúc body đã được giải mã, cân nhắc `RequestBodyAdvice` hoặc truyền object đã parse vào context do ứng dụng sở hữu, thay vì đọc lại servlet stream.
3. Nếu nhiều middleware *bắt buộc* cần các lượt đọc riêng, cân nhắc một `HttpServletRequestWrapper` tùy chỉnh: đọc body **một lần, ở rất sớm**, giới hạn kích thước, lưu byte[], và trả một `ServletInputStream` mới trên cùng byte[] khi `getInputStream()` được gọi. Lúc này cần xử lý đúng `getReader()` theo charset, các phương thức/notification nonblocking (`ReadListener`, `isReady`, `isFinished`), async dispatch, error path, thứ tự filter và tương tác đặc thù với parsing form/multipart.
4. Với payload lớn, streaming, file upload hay dữ liệu nhạy cảm, tránh toàn bộ việc cache trong heap; chuyển sang cơ chế đọc/ghi theo stream, file tạm được kiểm soát hoặc thiết kế API một lần tiêu thụ.

Wrapper replay tùy chỉnh **không chỉ là** đặt `ContentCachingRequestWrapper` rồi gọi lại `getInputStream()`. Cũng không nên biến mọi request thành byte[] lớn trước khi authenticate/validate giới hạn; điều đó có thể làm tăng dùng bộ nhớ và nguy cơ DoS.

## 7. Kiểm thử nguồn dữ liệu bằng MockMvc

`src/test/java/com/example/inputdemo/web/InputControllerTest.java`:

~~~java
package com.example.inputdemo.web;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InputController.class)
class InputControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void pathQueryHeaderAndCookieAreIndependent() throws Exception {
        mvc.perform(get("/api/input/items/7")
                        .param("mode", "detail")
                        .header("X-Trace-Id", "t-1")
                        .cookie(new Cookie("lang", "vi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.mode").value("detail"))
                .andExpect(jsonPath("$.traceId").value("t-1"))
                .andExpect(jsonPath("$.lang").value("vi"));
    }

    @Test
    void jsonBodyAndQueryCanBeReadTogether() throws Exception {
        mvc.perform(post("/api/input/items")
                        .param("source", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Java\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Java"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.source").value("mobile"));
    }

    @Test
    void urlEncodedFormBindsThroughModelAttribute() throws Exception {
        mvc.perform(post("/api/input/forms")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", "Spring")
                        .param("quantity", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring"))
                .andExpect(jsonPath("$.quantity").value(3));
    }

    @Test
    void multipartJsonPartAndFileHaveTheirOwnSources() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata",
                "",
                "application/json",
                "{\"source\":\"batch\"}".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", new byte[] {65, 66});

        mvc.perform(multipart("/api/input/uploads")
                        .file(metadata)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("batch"))
                .andExpect(jsonPath("$.bytes").value(2));
    }
}
~~~

Chạy:

~~~bash
./gradlew test
~~~

Lớp test dùng Spring MVC test slice và JSON converter thường có sẵn từ `spring-boot-starter-test`/`spring-boot-starter-web`. Nó chứng minh path/query/header/cookie, JSON body, form binding và hai multipart part được phân biệt đúng. Không có dependency database/service vì controller demo tự xử lý dữ liệu. Nếu project thêm Spring Security, custom filters, advice hoặc cấu hình MVC riêng, cần điều chỉnh test context/credentials tương ứng.

**Về test form:** `MockMvcRequestBuilders.post(...).param(...)` mô phỏng Servlet form parameters. Để kiểm tra chính xác hành vi một request chứa byte body đã encode, hãy bổ sung integration test chạy qua container thực tế. Không dùng mock request để kết luận mọi container sẽ xử lý giống nhau nếu filter đọc raw stream trước khi form được parse.

Các test lỗi nên có cho dự án thực tế:
- Thiếu `@RequestParam` bắt buộc hoặc `@RequestHeader` bắt buộc: kiểm tra `400`.
- `id` là chuỗi không chuyển được sang `long`: kiểm tra error response.
- `Content-Type: text/plain` gửi đến endpoint chỉ nhận JSON: kiểm tra `415`.
- JSON cú pháp sai, thiếu body: kiểm tra `400` và format lỗi có chủ đích.
- Multipart thiếu `metadata`, sai content type JSON part, tệp quá lớn: kiểm tra policy.
- Test một filter tùy chỉnh có/không có caching wrapper để phát hiện body bị đọc trước controller, nhưng không dựa vào hành vi chỉ xuất hiện trong `MockHttpServletRequest`.

## 8. Validation, bảo mật và các giới hạn nên đặt rõ

**Chọn DTO phù hợp cho từng transport.** `@ModelAttribute` cho form/query binding có thể gán thuộc tính qua setter; không bind trực tiếp domain/JPA entity với những field nhạy cảm như `role`, `isAdmin`, `ownerId`. Dùng DTO dành riêng cho web hoặc constructor binding để kiểm soát những trường được phép. Nếu phải dùng property binding, áp dụng `@InitBinder` với `setAllowedFields(...)` cho danh sách được phép.

**Xác thực đầu vào:** 
- `@Valid @RequestBody CreateOrder input`: áp dụng Bean Validation cho dữ liệu đã deserialize khi DTO có constraint và thư viện validation được cấu hình.
- `@Valid @ModelAttribute FormInput input`: validate sau bind form/query, có thể nhận `BindingResult` ngay sau tham số được validate để tự xử lý lỗi.
- `@Valid @RequestPart("metadata") Metadata metadata`: validate part JSON.
- Lỗi `@Valid`, ràng buộc trên trực tiếp các tham số method, hay lỗi conversion có thể đi qua những loại exception khác nhau; không hardcode một thông báo lỗi Spring mặc định trong client.

**Kích thước và chi phí:** đặt giới hạn request/upload và resource trên reverse proxy, Servlet container và ứng dụng. Caching body toàn phần dùng bộ nhớ tuyến tính theo size; vài request lớn đồng thời có thể tăng đột biến heap. `ContentCachingRequestWrapper` chỉ hạn chế cache tại wrapper, không phải lớp bảo vệ upload toàn cục.

**Tránh đọc/log dữ liệu bí mật:** query có thể nằm trong access log, header/cookie có thể chứa token, JSON có thể chứa password, upload có thể là tệp riêng tư. Ưu tiên log trace ID, method, path chuẩn hóa, duration và trạng thái lỗi; tránh log payload mặc định.

**Đảm bảo tương thích HTTP:** phân biệt `Content-Type` (client nói body gửi lên là gì) và `Accept` (client mong response dạng gì). Đối với định tuyến `consumes` và `produces`, gửi sai media type có thể dẫn đến `415`/`406` trước khi vào handler.

## 9. Cây quyết định thực hành

~~~text
Giá trị cần lấy từ HTTP request?
|
+-- Nằm trong phần {variable} của đường dẫn? --> @PathVariable
|
+-- Là query / application/x-www-form-urlencoded field?
|      +-- Ít trường đơn giản?                  --> @RequestParam
|      +-- Nhiều field thành object?            --> @ModelAttribute + DTO
|
+-- Là header?                                  --> @RequestHeader
+-- Là cookie?                                  --> @CookieValue
+-- Do filter/interceptor đưa vào request?      --> @RequestAttribute
+-- Có sẵn trong HTTP session?                  --> @SessionAttribute
|
+-- Nằm trong raw HTTP body?
       +-- JSON/XML document?                   --> @RequestBody + DTO
       +-- Multipart JSON part?                 --> @RequestPart + DTO
       +-- Multipart file?                      --> MultipartFile/@RequestPart
       +-- Stream lớn/chuyên biệt?              --> xem xét Servlet streaming,
                                                    giới hạn size và ownership
                                                    của lượt đọc
~~~

Khi bạn nghĩ “tôi phải đọc body lần hai”, trước tiên kiểm tra xem **dữ liệu đã được parse ở đâu**. Phần lớn trường hợp chỉ cần truyền DTO đã giải mã xuống service hoặc audit component. Nếu một filter hiện tại đang đọc raw JSON để log trước khi controller chạy, hãy sửa cơ chế quan sát tại thời điểm request được xử lý, kiểm tra giới hạn cache và test lại endpoint nhận `@RequestBody`.

## 10. Checklist chẩn đoán nhanh

1. Xác minh `@RequestMapping`/HTTP method, `Content-Type` và `Accept` của request.
2. Với từng tham số handler, ghi rõ nguồn: path, Servlet parameters, header, cookie, body, multipart part, request/session attribute.
3. Kiểm tra kiểu Java, converter/formatter, tên tham số tường minh, `required`, default value và validation.
4. Không trộn nguồn form và JSON. Với JSON, ưu tiên một `@RequestBody` DTO; với form, dùng `@RequestParam`/`@ModelAttribute`.
5. Rà soát filter, interceptor, custom resolver và advice: thành phần nào đọc `getInputStream()`/`getReader()`/`getParameterMap()` trước handler?
6. Nếu dùng `ContentCachingRequestWrapper`, phân biệt cache để xem lại byte đã đọc với wrapper có thể replay (hai cơ chế khác nhau).
7. Kiểm thử MockMvc cho việc chọn nguồn; test qua Servlet container cho form parsing, filter order và các giới hạn upload.
8. Ràng buộc trường bind và kích thước request; hạn chế ghi log dữ liệu nhạy cảm.

## 11. Tài liệu chính thức tham khảo

- [Spring Framework — MVC Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html): resolver và loại tham số được hỗ trợ.
- [Spring Framework — `@RequestParam`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html): Servlet request parameters, query/form, required/optional, map và list.
- [Spring Framework — `@RequestBody`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html): `HttpMessageConverter` và cảnh báo tương tác form parameter parsing.
- [Spring Framework — `@ModelAttribute`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html): constructor/property binding, validation, dữ liệu đầu vào không đáng tin cậy.
- [Spring Framework — Multipart](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html): `@RequestPart`, file, JSON part.
- [Spring Framework — `ContentCachingRequestWrapper` API](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/util/ContentCachingRequestWrapper.html): chỉ lưu byte đã đọc, cache limit, không tự tiêu thụ body.
- [Jakarta Servlet — `ServletRequest` API](https://javadoc.io/static/jakarta.servlet/jakarta.servlet-api/6.0.0/jakarta.servlet/jakarta/servlet/ServletRequest.html): `getInputStream()`, `getReader()` và phân tích request parameters.

**Điểm cần nhớ:** nguồn dữ liệu được xác định bởi hợp đồng tham số và cơ chế resolver của Spring MVC; body chỉ nên có **một chủ thể tiêu thụ** trong luồng xử lý thông thường. Dữ liệu đã giải mã có thể được chia sẻ ở mức object; stream HTTP thô không tự trở thành dữ liệu có thể đọc vô hạn lần.


</details>

- [Quay lại đầu trang](#back-to-top)
