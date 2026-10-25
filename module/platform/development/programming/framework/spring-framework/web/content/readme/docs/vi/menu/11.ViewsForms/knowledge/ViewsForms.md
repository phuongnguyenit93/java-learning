<a id="back-to-top"></a>

# View và biểu mẫu

## Menu
- [Model, ModelMap, ModelAndView và tên view logic](#views-forms-001)
- [ViewResolver, thứ tự phân giải và tích hợp template engine](#views-forms-002)
- [Thymeleaf, FreeMarker, JSP: vai trò và điều kiện sử dụng](#views-forms-003)
- [Truyền dữ liệu từ controller tới view bằng @ModelAttribute](#views-forms-004)
- [Liên kết biểu mẫu, hiển thị BindingResult và thông báo lỗi](#views-forms-005)
- [Redirect, forward và mẫu Post/Redirect/Get](#views-forms-006)
- [RedirectAttributes, flash attributes và vòng đời dữ liệu chuyển hướng](#views-forms-007)
- [Giới hạn nội dung động, escape HTML và nguy cơ XSS khi kết xuất view](#views-forms-008)

## <a id="views-forms-001">Model, ModelMap, ModelAndView và tên view logic</a>

<details>
<summary>Xem chi tiết</summary>

# Model, ModelMap, ModelAndView và tên view logic trong Spring MVC

## 1. Mục tiêu bài học

Khi xây dựng giao diện HTML bằng **Spring MVC**, controller thường không trả trực tiếp HTML. Nó chuẩn bị dữ liệu cho giao diện (**model**) và chỉ ra giao diện cần hiển thị (**view**). Spring sau đó dùng **ViewResolver** để chọn view thực tế (ví dụ một template Thymeleaf) và render response.

Bài học giúp bạn:

1. Phân biệt **`Model`**, **`ModelMap`**, **`ModelAndView`** về kiểu Java, khả năng chứa dữ liệu và cách chỉ định view.
2. Hiểu **tên view logic** như `"products/detail"` được resolve thành template thực tế như `classpath:/templates/products/detail.html`.
3. Chọn cách viết controller hợp lý: truyền `Model`/`ModelMap` làm tham số rồi trả `String`, hoặc trả `ModelAndView`.
4. Hiểu tên view mặc định, `redirect:`, `forward:`, `@ModelAttribute` và dữ liệu form được đưa vào model.
5. Nhận ra khi `String` bị diễn giải là **view name** và khi nó được gửi thành **response body** dưới `@ResponseBody`/`@RestController`.
6. Viết ứng dụng Java 17+ / Spring Boot 3.x với template Thymeleaf, form và kiểm thử MockMvc.

**Phạm vi:** Spring Framework 6.x, Spring Boot 3.x, Spring MVC sử dụng Servlet stack và server-rendered HTML. Spring WebFlux và SPA frontend có cơ chế render/truyền dữ liệu riêng. Tên view và data binding vẫn là kiến thức hữu ích khi ứng dụng kết hợp giao diện HTML với REST API.

## 2. Model và View là hai vai trò khác nhau

Một trang HTML hiển thị sản phẩm có thể cần dữ liệu:

~~~json
{"id":101,"name":"Sách Spring MVC","price":120000}
~~~

Với Spring MVC server-rendered, controller đưa dữ liệu đó vào **model**, đặt tên **view**, rồi Spring phối hợp cả hai để render HTML:

~~~mermaid
flowchart TD
    A[HTTP GET /products/101] --> B[DispatcherServlet]
    B --> C[Controller]
    C --> D[Model: product]
    C --> E[Logical view: products/detail]
    D --> F[ViewResolver]
    E --> F
    F --> G[Template: products/detail.html]
    G --> H[HTML response]
~~~

- **Model** là tập các **attribute theo tên** để view sử dụng. Model không phải entity/database model theo nghĩa ORM, cũng không tự lưu vào session hoặc database.
- **View** là cách render response, ví dụ Thymeleaf template, JSP hoặc một `View` implementation khác.
- **Tên view logic** (logical view name) là định danh như `"products/detail"`, chưa nhất thiết là một đường dẫn vật lý.
- **ViewResolver** tìm một `View` phù hợp dựa trên tên logic. Khi dùng Thymeleaf mặc định trong Spring Boot 3.x, tên `"products/detail"` thường tương ứng `src/main/resources/templates/products/detail.html`.
- View phối hợp model để tạo HTML trả cho trình duyệt. Dữ liệu trong model có phạm vi thông thường của request xử lý đó; không coi nó là dữ liệu lưu trữ vĩnh viễn.

Điểm dễ nhầm: Trong Spring MVC, `model.addAttribute("product", product)` **không gửi JSON** cho client. Controller cần trả một view name hoặc một view object; quá trình render template sau đó mới tạo response body HTML. Với `@RestController`, kết quả trả về thường được chuyển trực tiếp thành body thông qua `HttpMessageConverter`, không có bước tìm template.

## 3. So sánh `Model`, `ModelMap`, `ModelAndView`

| Tiêu chí | `Model` | `ModelMap` | `ModelAndView` |
| --- | --- | --- | --- |
| Package | `org.springframework.ui.Model` | `org.springframework.ui.ModelMap` | `org.springframework.web.servlet.ModelAndView` |
| Kiểu Java | **Interface** | **Class** kế thừa `LinkedHashMap<String, Object>` | **Class** gộp model và view |
| Nhiệm vụ chính | Thêm/đọc attribute của model | Model theo phong cách Map | Chỉ định view và kèm model trong cùng kết quả |
| API thêm dữ liệu | `addAttribute(...)`, `addAllAttributes(...)`, `asMap()` | `addAttribute(...)`, `put(...)`, `get(...)` | `addObject(...)`, `addAllObjects(...)` |
| Là tham số handler | Có | Có | Không phổ biến như tham số, thường là **return value** |
| Thường trả từ handler | `String` view name hoặc chính `Model` | `String` view name hoặc chính `ModelMap` | `ModelAndView` |
| View name đi kèm trong object? | Không | Không | Có (`setViewName`, constructor) |
| Khi nên chọn | Hầu hết handler HTML thông thường | Khi cần thao tác theo API Map | Khi muốn trả model + view + status trong một object |

`Model` và `ModelMap` là **hai cách truy cập model của Spring MVC**, không phải hai kho dữ liệu tách biệt. Trong các handler MVC bình thường, Spring cung cấp object model dùng cho request, còn `ModelAndView` là **giá trị trả về mang cả thông tin view và model**. Không tạo ra ba cách truyền dữ liệu khác nhau đến browser.

**Quy tắc chọn nhanh:** nếu handler chỉ cần thêm vài attribute rồi hiển thị template, dùng `Model` làm tham số và trả `String` tên view. Nếu cần API `Map`, có thể nhận `ModelMap`. Nếu muốn xây một object biểu diễn đồng thời view, model và có thể cả status, trả `ModelAndView`.

## 4. `Model`: giao diện làm việc trực tiếp với attribute

Mẫu phổ biến nhất trong controller dùng Thymeleaf:

~~~java
@Controller
@RequestMapping("/products")
public class ProductController {

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        ProductDto product = productService.findById(id);
        model.addAttribute("product", product);
        model.addAttribute("pageTitle", "Thông tin sản phẩm");
        return "products/detail";
    }
}
~~~

**Đây là đoạn minh họa bên trong một controller**, chưa là chương trình độc lập; `ProductDto` và `productService` cần được định nghĩa và inject. Những điểm đáng chú ý:

- `Model` **là interface**; Spring cung cấp instance cho method. Không cần tự tạo `new Model()`.
- `model.addAttribute("product", product)` tạo attribute với key `product`; view Thymeleaf sẽ tham chiếu bằng `${product}`.
- `return "products/detail"` là **tên view logic**, không phải JSON và không phải tên class Java.
- Spring Boot + Thymeleaf mặc định tìm template tương ứng ở `classpath:/templates/products/detail.html` nếu template engine được cấu hình.
- Controller phải xử lý trường hợp `findById` không thấy sản phẩm, ví dụ ném exception được ánh xạ sang 404 hoặc trả trang lỗi thích hợp. Ví dụ này tập trung vào luồng model/view.

Các method thường dùng của `Model`:

~~~java
model.addAttribute("title", "Danh sách sản phẩm");
model.addAttribute("count", 3);
model.addAttribute("items", items);
model.addAllAttributes(Map.of("category", "books", "sort", "name"));
Map<String, Object> snapshot = model.asMap();
~~~

Đây là các câu lệnh minh họa đặt trong handler có biến `model` và `items`. Tham chiếu được dùng trong view bằng tên attribute. Dùng `addAttribute(String, Object)` để tránh nhầm tên quy ước khi thêm object không nêu rõ key.

`Model` **không chứa view name**. Return value hoặc cơ chế tên view mặc định quyết định view cần render.

## 5. `ModelMap`: dùng model theo phong cách `Map`

`ModelMap` là class kế thừa `LinkedHashMap<String, Object>`, có cả API của Map và các method trợ giúp cho model:

~~~java
@GetMapping("/summary")
public String summary(ModelMap model) {
    model.put("pageTitle", "Tổng quan");
    model.addAttribute("total", 3);
    return "products/summary";
}
~~~

Đây là handler minh họa trong `@Controller`; cần import `ModelMap`. Trong template `products/summary.html`, hai attribute có tên `pageTitle` và `total` đều có thể được sử dụng.

Ví dụ truy xuất:

~~~java
Object total = model.get("total");
boolean contains = model.containsKey("pageTitle");
Map<String, Object> copied = new LinkedHashMap<>(model);
~~~

Đây là các câu lệnh minh họa trong cùng method với `ModelMap model`; cần import `Map` và `LinkedHashMap`. Khi bạn không cần `put`, `get`, `containsKey`, interface `Model` thường rõ ý định hơn. Tránh thêm vào model những entity nhạy cảm, access token hoặc object có đồ thị rất lớn chỉ vì Map cho phép lưu bất kỳ object nào.

**Không nên nhầm `ModelMap` với `Map` trả về JSON.** Nếu một handler HTML trả một `Map` hoặc `ModelMap` như return value, Spring MVC có thể hiểu đó là thuộc tính model rồi **tự suy ra view name**. Nếu handler REST dùng `@ResponseBody`, cùng loại `Map` lại có thể được serialize thành JSON. Annotation controller và cơ chế return value quyết định hành vi.

## 6. `ModelAndView`: đóng gói view và model

Khi muốn chỉ định cả view và dữ liệu ở cùng nơi:

~~~java
@GetMapping("/detail")
public ModelAndView detail() {
    return new ModelAndView("products/detail")
            .addObject("product", new ProductDto(101L, "Sách Spring MVC"))
            .addObject("pageTitle", "Chi tiết sản phẩm");
}
~~~

Đây là method minh họa trong `@Controller` và cần định nghĩa `ProductDto`. `ModelAndView` cho phép:

- Truyền tên view qua constructor `new ModelAndView("products/detail")` hoặc `setViewName(...)`.
- Thêm attribute bằng `addObject("key", value)` hoặc `addAllObjects(map)`.
- Đặt trực tiếp một `View` implementation qua constructor/`setView(View)`, thay vì logical name khi cần.
- Đặt HTTP status phù hợp bằng `setStatus(HttpStatus...)` trong Spring Framework 6.x (không biến thành `ResponseEntity`; status vẫn phục vụ response render view).

Một cách khác:

~~~java
ModelAndView mav = new ModelAndView();
mav.setViewName("products/detail");
mav.addObject("product", product);
mav.addObject("pageTitle", "Chi tiết sản phẩm");
return mav;
~~~

Những dòng trên cũng là minh họa đặt trong method trả `ModelAndView`. Nếu bỏ view name và không có điều kiện Spring coi request đã được xử lý hoàn toàn, framework có thể cần đến **view name mặc định**, tạo ra kết quả bất ngờ. Chọn tên view tường minh khi code cần dễ kiểm tra.

**Ví dụ status dành cho view:**

~~~java
ModelAndView mav = new ModelAndView("errors/not-found");
mav.setStatus(HttpStatus.NOT_FOUND);
mav.addObject("message", "Không tìm thấy sản phẩm");
return mav;
~~~

Cần có template lỗi tương ứng để render HTML. Thay đổi HTTP status thành 404 không tự động tạo template; Spring vẫn cần view resolver và tài nguyên thực tế. Với REST API trả JSON, thường dùng `ResponseEntity` hoặc `ProblemDetail` thay vì HTML `ModelAndView`.

## 7. Tên view logic được resolve như thế nào?

Giả sử controller:

~~~java
@GetMapping("/greeting")
public String greeting(Model model) {
    model.addAttribute("name", "Lan");
    return "hello";
}
~~~

Giả sử ứng dụng có `spring-boot-starter-thymeleaf`, Spring Boot thường tự cấu hình Thymeleaf view resolver. Mặc định:

~~~properties
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
~~~

Tên `"hello"` được ghép thành `classpath:/templates/hello.html`. Đặt file ở:

~~~text
src/main/resources/templates/hello.html
~~~

Một template tối thiểu:

~~~html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="vi">
<head><meta charset="UTF-8"><title>Demo</title></head>
<body>
  <h1 th:text="'Xin chào, ' + ${name}">Lời chào</h1>
</body>
</html>
~~~

Thymeleaf sẽ xử lý `th:text` bằng giá trị model `name` và escape dữ liệu theo ngữ cảnh HTML. Đây là điểm bảo mật quan trọng: dữ liệu từ client không nên được nhúng vào HTML raw bằng string concatenation thiếu escaping.

**Tên view logic không phải đường dẫn file trên máy.** Một view resolver có thể ghép prefix/suffix, một resolver khác có thể chọn JSP hoặc loại view khác. Không tự viết đường dẫn template dựa trực tiếp vào tham số của client (`return userSuppliedViewName;`) vì có thể dẫn đến hiển thị nhầm template hoặc rủi ro điều hướng.

### 7.1. Các hình thức tên view

| Handler trả về | Cách hiểu trong `@Controller` HTML |
| --- | --- |
| `"hello"` | Tên view logic, resolver tìm template |
| `"products/detail"` | Tên view logic nằm trong thư mục con |
| `"redirect:/products"` | Yêu cầu redirect HTTP tới URL `/products` |
| `"forward:/internal"` | Server-side forward đến đường dẫn khác |
| `new ModelAndView("hello")` | View `hello` và model kèm theo |
| `View` instance | Chỉ định view trực tiếp, không cần resolve tên |
| `Model`/`Map` return value | Model được cung cấp; view name thường suy ra từ request |

`redirect:` và `forward:` được nhận diện đặc biệt bởi những view resolver hỗ trợ chúng (như `UrlBasedViewResolver` và cơ chế cấu hình MVC thông dụng). Chúng **không phải tên template thông thường**.

### 7.2. Tên view mặc định (`RequestToViewNameTranslator`)

Nếu một HTML controller không trả view name nhưng cung cấp model và Spring chưa coi response đã xử lý hoàn toàn, framework có thể suy ra tên view dựa trên request URI thông qua `RequestToViewNameTranslator`, mặc định thường là `DefaultRequestToViewNameTranslator`.

Ví dụ request `GET /pages/implicit` có thể được diễn giải thành tên view `"pages/implicit"` dưới quy tắc mặc định. Khi đó Spring tìm template `templates/pages/implicit.html`.

Hành vi ngầm phụ thuộc cách đặt controller, route, translator và cấu hình resolver. Nếu muốn giảm sự phụ thuộc vào convention, **trả `String` view name tường minh**. Cũng cần tránh trả `void` từ controller HTML mà kỳ vọng luôn trả body rỗng: nó có thể kích hoạt **view name mặc định**.

### 7.3. Vì sao `@RestController` trả `"hello"` không tìm template?

~~~java
@Controller
class HtmlController {
    @GetMapping("/html")
    String html() {
        return "hello"; // Logical view name
    }
}

@RestController
class ApiController {
    @GetMapping("/api")
    String api() {
        return "hello"; // HTTP response body là chuỗi hello
    }
}
~~~

Đây là code minh họa gồm hai controller (cần import annotation). `@RestController` tương đương `@Controller` cùng `@ResponseBody` trên các method handler. Với `@ResponseBody`, Spring đưa String qua `HttpMessageConverter` thay vì đi qua view resolution. Muốn trả template HTML, chọn `@Controller` và return view name; muốn trả JSON hoặc text, chọn `@RestController` hoặc `@ResponseBody`.

## 8. Ví dụ Spring Boot 3.x có thể chạy: Model, ModelMap, ModelAndView và form

Tạo dự án bằng Spring Initializr:

- **Java:** 17 hoặc cao hơn.
- **Spring Boot:** nhánh 3.x.
- **Build:** Maven.
- **Dependencies:** Spring Web, Thymeleaf, Validation, Spring Boot Starter Test.

Các starter liên quan là `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-validation`, `spring-boot-starter-test`. Java package dùng `com.example.demo`. Nếu dùng Spring Boot 4.x/Framework 7.x, kiểm tra lại import và cấu hình dependency theo phiên bản.

Cấu trúc tệp:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/PageController.java
src/main/resources/templates/demo.html
src/main/resources/templates/pages/implicit.html
src/main/resources/templates/registration.html
src/main/resources/templates/thanks.html
src/test/java/com/example/demo/PageControllerTest.java
~~~

### 8.1. File khởi động

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

### 8.2. Controller đầy đủ

**`src/main/java/com/example/demo/PageController.java`**

~~~java
package com.example.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/pages")
public class PageController {

    @GetMapping("/model")
    public String useModel(Model model) {
        model.addAttribute("title", "Ví dụ Model");
        model.addAttribute("message", "Dữ liệu từ Model");
        return "demo";
    }

    @GetMapping("/modelmap")
    public String useModelMap(ModelMap model) {
        model.put("title", "Ví dụ ModelMap");
        model.addAttribute("message", "Dữ liệu từ ModelMap");
        return "demo";
    }

    @GetMapping("/modelandview")
    public ModelAndView useModelAndView() {
        ModelAndView mav = new ModelAndView("demo");
        mav.addObject("title", "Ví dụ ModelAndView");
        mav.addObject("message", "Dữ liệu từ ModelAndView");
        mav.setStatus(HttpStatus.OK);
        return mav;
    }

    // Không trả view name: MVC suy ra pages/implicit khi dùng
    // DefaultRequestToViewNameTranslator và request /pages/implicit.
    @GetMapping("/implicit")
    public Model implicitView(Model model) {
        model.addAttribute("title", "View ngầm định");
        model.addAttribute("message", "Dữ liệu từ view mặc định");
        return model;
    }

    @GetMapping("/register")
    public String showRegistration(Model model) {
        model.addAttribute("registration", new RegistrationForm());
        return "registration";
    }

    @PostMapping("/register")
    public String submitRegistration(
            @Valid @ModelAttribute("registration") RegistrationForm form,
            BindingResult errors,
            RedirectAttributes redirectAttributes) {

        if (errors.hasErrors()) {
            return "registration";
        }

        redirectAttributes.addFlashAttribute("name", form.getName());
        return "redirect:/pages/thanks";
    }

    @GetMapping("/thanks")
    public String thanks() {
        return "thanks";
    }

    public static class RegistrationForm {
        @NotBlank
        private String name;

        public RegistrationForm() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
~~~

Điểm cần quan sát:

1. Ba handler đầu cùng dùng view logic **`"demo"`**; trong template, biến `title` và `message` là attribute model. Khác nhau chỉ là API xây kết quả.
2. `useModel` nhận `Model`, `useModelMap` nhận `ModelMap`; cả hai trả `String` view name.
3. `useModelAndView` trả một `ModelAndView` có `viewName`, model và status 200. `setStatus(OK)` ở đây để minh họa, thường có thể bỏ vì 200 là mặc định thành công.
4. `implicitView` trả `Model`; framework suy ra `pages/implicit` trong cấu hình dịch tên view mặc định. Đây là ví dụ về convention, **không phải cách nên ưu tiên trong API phức tạp**.
5. `showRegistration` đưa `RegistrationForm` vào model. Template Thymeleaf dùng nó để bind form HTML.
6. POST nhận form qua `@ModelAttribute`, áp dụng Bean Validation bằng `@Valid`, đặt `BindingResult` **ngay sau** tham số form để lấy lỗi binding/validation.
7. Nếu có lỗi, trả tên view `registration`, giữ model và `BindingResult` để template hiển thị lỗi. Nếu hợp lệ, dùng redirect theo mẫu **Post/Redirect/Get** và `RedirectAttributes` để chuyển flash attribute `name` qua request redirect tiếp theo.
8. `@Controller` là bắt buộc cho các handler trả template theo cách này. Nếu đổi thành `@RestController`, các `String` return value sẽ mang ngữ nghĩa HTTP body, gây khác biệt lớn.

### 8.3. Template chung cho cả ba cách

**`src/main/resources/templates/demo.html`**

~~~html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title th:text="${title}">Ví dụ Spring MVC</title>
</head>
<body>
  <h1 th:text="${title}">Tiêu đề mặc định</h1>
  <p id="message" th:text="${message}">Nội dung mặc định</p>
</body>
</html>
~~~

Khi controller trả `"demo"`, Thymeleaf đọc `demo.html` và thay nội dung `th:text` bằng attribute cùng tên trong model.

Ví dụ gọi `GET /pages/model`, HTML render chứa:

~~~html
<h1>Ví dụ Model</h1>
<p id="message">Dữ liệu từ Model</p>
~~~

Đây là phần nội dung minh họa được trích từ HTML, không phải toàn bộ response nguyên văn (response còn có `doctype`, `head` và các thẻ còn lại). Thymeleaf escape text để giảm nguy cơ nhúng HTML không mong muốn từ dữ liệu client.

### 8.4. Template dùng tên view được suy ra

**`src/main/resources/templates/pages/implicit.html`**

~~~html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title th:text="${title}">View ngầm định</title>
</head>
<body>
  <h1 th:text="${title}">Tiêu đề</h1>
  <p th:text="${message}">Nội dung</p>
</body>
</html>
~~~

Đường dẫn URL `/pages/implicit` thường dẫn đến logical view `pages/implicit` dưới translator mặc định. Nếu refactor route mà không đổi template hoặc ngược lại, bạn có thể gặp lỗi “template not found”. Đây là lý do các handler trả view name rõ ràng thường dễ bảo trì.

### 8.5. Template form và validation

**`src/main/resources/templates/registration.html`**

~~~html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Đăng ký</title>
</head>
<body>
  <h1>Đăng ký</h1>
  <form method="post" th:action="@{/pages/register}" th:object="${registration}">
    <label for="name">Tên</label>
    <input id="name" type="text" th:field="*{name}">
    <p th:if="${#fields.hasErrors('name')}"
       th:errors="*{name}">Lỗi của tên</p>
    <button type="submit">Gửi</button>
  </form>
</body>
</html>
~~~

- `th:object="${registration}"` gắn template vào form object trong model.
- `th:field="*{name}"` lấy hoặc đặt giá trị `name` theo object, hỗ trợ hiển thị lại form khi validation không đạt.
- Khi controller trả `"registration"` cùng `BindingResult`, Thymeleaf có thể hiển thị lỗi bằng `#fields.hasErrors` và `th:errors`.
- Dữ liệu từ form là input của người dùng: validation ở server vẫn cần thiết ngay cả khi HTML có thuộc tính `required`.
- Nếu dùng Spring Security và CSRF enabled, form phải mang CSRF token theo integration/config thực tế; đừng vô hiệu hóa CSRF chỉ để làm form hoạt động.

### 8.6. Template sau redirect

**`src/main/resources/templates/thanks.html`**

~~~html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head><meta charset="UTF-8"><title>Đăng ký thành công</title></head>
<body>
  <h1>Đăng ký thành công</h1>
  <p th:if="${name}" th:text="'Xin chào, ' + ${name}">Lời chào</p>
</body>
</html>
~~~

Sau POST hợp lệ, `redirect:/pages/thanks` trả HTTP redirect (thường 302 trong ví dụ), sau đó browser gửi GET tới `/pages/thanks`. `addFlashAttribute` giữ tên trong flash storage tạm thời cho lần request sau. Nó **không có nghĩa** mọi model attribute từ POST tự động xuất hiện trong GET tiếp theo.

Trong ứng dụng thật, POST nên lưu dữ liệu thông qua service trước khi redirect, và chỉ đưa thông tin cần thiết vào flash attributes. Flash được lưu tạm (thường liên quan session) và không phải nơi lưu dữ liệu nghiệp vụ lâu dài.

## 9. Gọi ứng dụng và xem view rendering

Chạy trong thư mục có `pom.xml`:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows: `mvnw.cmd spring-boot:run`; nếu không có wrapper và đã cài Maven: `mvn spring-boot:run`.

Các URL:

~~~text
GET http://localhost:8080/pages/model
GET http://localhost:8080/pages/modelmap
GET http://localhost:8080/pages/modelandview
GET http://localhost:8080/pages/implicit
GET http://localhost:8080/pages/register
GET http://localhost:8080/pages/thanks
~~~

Ba URL đầu phải render **cùng file `demo.html`** nhưng chữ hiển thị khác nhau:

| URL | `title` | `message` |
| --- | --- | --- |
| `/pages/model` | `Ví dụ Model` | `Dữ liệu từ Model` |
| `/pages/modelmap` | `Ví dụ ModelMap` | `Dữ liệu từ ModelMap` |
| `/pages/modelandview` | `Ví dụ ModelAndView` | `Dữ liệu từ ModelAndView` |

Bạn có thể dùng `curl -i` để xem status, response headers và HTML output:

~~~bash
curl -i http://localhost:8080/pages/model
curl -i http://localhost:8080/pages/modelmap
curl -i http://localhost:8080/pages/modelandview
~~~

Kết quả là HTML (thường `Content-Type: text/html`), **không phải JSON**. Với Thymeleaf, toàn bộ `model` được dùng khi template đánh giá các expression `${...}`.

Thử form hợp lệ:

~~~bash
curl -i -X POST http://localhost:8080/pages/register   -H "Content-Type: application/x-www-form-urlencoded"   --data-urlencode "name=Lan"
~~~

Đáp ứng kỳ vọng là **redirect** (thường 302) với header `Location: /pages/thanks` trong cấu hình chuẩn. `curl` không giữ và gửi session cookie qua các lệnh tách biệt nếu không yêu cầu; để quan sát flash attribute sau redirect, dùng browser hoặc `curl -L -c cookies.txt -b cookies.txt` với cookie jar và đúng điều kiện theo redirect. Đối với flash attribute liên quan session, không giả định `curl -L` đơn lẻ luôn nhìn thấy lời chào.

Để thử lỗi validation, gửi `name` rỗng:

~~~bash
curl -i -X POST http://localhost:8080/pages/register   -H "Content-Type: application/x-www-form-urlencoded"   --data-urlencode "name="
~~~

Controller trả **view `registration`** (thường 200 HTTP trong ví dụ) và template hiển thị lỗi tại trường `name`, không redirect. Nếu sản phẩm cần status 400 cho validation khi render form, phải chủ động đặt status thích hợp; `BindingResult` không tự áp đặt 400 khi handler quyết định render lại một HTML form.

## 10. Kiểm thử `Model`, `ModelMap`, `ModelAndView` bằng MockMvc

Có `spring-boot-starter-test` trong dự án, tạo **`src/test/java/com/example/demo/PageControllerTest.java`**:

~~~java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(PageController.class)
class PageControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void modelAddsDataAndReturnsViewName() throws Exception {
        mockMvc.perform(get("/pages/model"))
                .andExpect(status().isOk())
                .andExpect(view().name("demo"))
                .andExpect(model().attribute("title", "Ví dụ Model"))
                .andExpect(model().attribute(
                        "message", "Dữ liệu từ Model"));
    }

    @Test
    void modelMapAddsDataAndReturnsSameView() throws Exception {
        mockMvc.perform(get("/pages/modelmap"))
                .andExpect(status().isOk())
                .andExpect(view().name("demo"))
                .andExpect(model().attribute("title", "Ví dụ ModelMap"))
                .andExpect(model().attribute(
                        "message", "Dữ liệu từ ModelMap"));
    }

    @Test
    void modelAndViewCombinesDataAndView() throws Exception {
        mockMvc.perform(get("/pages/modelandview"))
                .andExpect(status().isOk())
                .andExpect(view().name("demo"))
                .andExpect(model().attribute(
                        "message", "Dữ liệu từ ModelAndView"));
    }

    @Test
    void implicitViewNameUsesDefaultTranslator() throws Exception {
        mockMvc.perform(get("/pages/implicit"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/implicit"))
                .andExpect(model().attribute(
                        "message", "Dữ liệu từ view mặc định"));
    }

    @Test
    void formPageProvidesModelAttribute() throws Exception {
        mockMvc.perform(get("/pages/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("registration"))
                .andExpect(model().attributeExists("registration"));
    }

    @Test
    void validFormUsesRedirectAndFlash() throws Exception {
        mockMvc.perform(post("/pages/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Lan"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pages/thanks"))
                .andExpect(flash().attribute("name", "Lan"));
    }

    @Test
    void invalidFormReturnsToViewWithFieldErrors() throws Exception {
        mockMvc.perform(post("/pages/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("registration"))
                .andExpect(model().attributeHasFieldErrors(
                        "registration", "name"));
    }
}
~~~

Chạy test:

~~~bash
./mvnw test
~~~

Các test khẳng định chính xác sự phân tách giữa **tên view** và **attribute model**. `view().name("demo")` xác minh việc chọn tên logic; `model().attribute(...)` kiểm tra dữ liệu được đưa đến view. Kết quả view resolver có thể còn phụ thuộc engine và cấu hình test slice, nên giữ đúng dependency Thymeleaf và template khi chạy.

Nếu muốn kiểm tra HTML đã render, có thể thêm test với `content().string(containsString("Dữ liệu từ Model"))` và import matcher Hamcrest; điều này đòi view đã thực sự được render. Ứng dụng HTML production cần cả test controller và test integration của template để phát hiện template sai đường dẫn/cú pháp.

## 11. `@ModelAttribute` bổ sung model trước khi render

Ngoài việc gọi `model.addAttribute(...)` trong handler, Spring MVC hỗ trợ method có annotation `@ModelAttribute` để tạo dữ liệu dùng chung:

~~~java
@Controller
@RequestMapping("/shop")
public class ShopController {

    @ModelAttribute("siteName")
    public String siteName() {
        return "Cửa hàng Spring";
    }

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("pageTitle", "Trang chủ");
        return "shop/home";
    }
}
~~~

Đây là class minh họa, cần import. Phương thức `siteName()` không phải endpoint HTTP; nó tạo attribute `siteName` trong model trước khi các handler phù hợp được gọi. Template `shop/home` có thể dùng cả `${siteName}` và `${pageTitle}`. Có thể đưa `@ModelAttribute` method vào `@ControllerAdvice` nếu thật sự cần dữ liệu chung cho nhiều controller.

**Tránh đặt dữ liệu thay đổi nhanh hoặc truy vấn database nặng vào một advice áp dụng mọi route** nếu không cần thiết; method chung có thể bị gọi ở nhiều request. Đừng dùng `@ModelAttribute` để vô tình đưa thông tin nhạy cảm vào view HTML.

`@ModelAttribute` trên **tham số handler** lại có nhiệm vụ truy cập/tạo target object và bind giá trị request; không hoàn toàn tương đương `@ModelAttribute` ở mức method chuẩn bị model.

## 12. View name, redirect, forward và vòng đời dữ liệu model

### `return "redirect:/..."`

Dùng khi browser cần **request mới**. Sau POST thành công, redirect giúp tránh trình duyệt gửi lại form khi refresh. Giá trị model thông thường không nên được hiểu là sẽ tồn tại tự động trong request mới. `RedirectAttributes` có hai cơ chế:

- `addAttribute(...)`: thường dùng cho URL template variables hoặc query parameters khi redirect. Tránh đưa mật khẩu/token/thông tin nhạy cảm vào URL.
- `addFlashAttribute(...)`: lưu tạm dữ liệu cho request sau khi redirect (thường trong session flash map), thích hợp cho thông điệp xác nhận hoặc một phần trạng thái hiển thị.

**Ví dụ minh họa độc lập với controller phần 8:**

~~~java
@PostMapping("/save")
public String save(RedirectAttributes attributes) {
    long id = 101L; // Giả sử service đã tạo tài nguyên.
    attributes.addAttribute("id", id);
    attributes.addFlashAttribute("notice", "Đã lưu");
    return "redirect:/products/{id}";
}
~~~

Khi browser đến `/products/101`, giá trị flash `notice` có thể được đưa vào model ở request đích. Không lưu object dung lượng lớn hoặc dữ liệu nhạy cảm vào flash storage.

### `return "forward:/..."`

Server **forward nội bộ** request hiện tại đến path khác bằng servlet request dispatcher, thường không đổi URL trên thanh địa chỉ và không yêu cầu browser tự gửi redirect request. Request attributes có thể được tiếp tục dùng trong quá trình forward. Hãy tránh vòng forward vô hạn và chỉ forward tới route/view thực sự tồn tại.

### Đường dẫn tuyệt đối và tên view logic

`"products/detail"` là tên view, không tự động là đường dẫn HTTP `/products/detail`. Ngược lại, `"redirect:/products/detail"` chỉ đích của HTTP redirect. Một template dưới `templates/products/detail.html` không bắt buộc có endpoint `/products/detail` cùng tên; controller nào trả logical view đó cũng có thể render template.

## 13. Model trong form validation: khi nào dữ liệu lỗi còn ở lại?

Với `@Valid @ModelAttribute("registration") RegistrationForm form, BindingResult errors`, Spring tạo hoặc lấy form object, bind input, chạy validation và đặt thông tin lỗi vào model cùng object. Khi method trả `"registration"`, view có thể đọc giá trị đã nhập và lỗi theo tên field. Đó là lý do biểu mẫu có thể hiển thị lại trường nhập sau lỗi.

- `BindingResult` phải đặt **ngay sau** tham số được validate để handler nhận lỗi của target đó; nếu không, Spring có thể ném exception hoặc không ghép lỗi theo ý muốn.
- `model.addAttribute("registration", ...) ` khi render GET cung cấp form object ban đầu. Khi POST invalid, Spring thường đã có object đó từ binding; không nên tạo mới và ghi đè nó trước khi render lại, nếu cần giữ giá trị user nhập và lỗi.
- Nếu đang dùng Thymeleaf, `th:object`, `th:field` và `th:errors` giúp kết nối HTML form với model và `BindingResult`.
- Validation đối với DTO chỉ xác minh dữ liệu; lưu thành công phải qua service/repository thật và kiểm tra quyền theo ngữ cảnh ứng dụng.

Một model có thể chứa nhiều form attribute. Tránh dùng những tên attribute quá chung như `"data"` cho nhiều form trên cùng trang, vì template và error binding khó phân biệt.

## 14. Khi nào nên trả `ModelAndView` thay vì `String`?

Hai phong cách thường tương đương về output:

~~~java
@GetMapping("/simple")
public String simple(Model model) {
    model.addAttribute("message", "Xin chào");
    return "hello";
}
~~~

~~~java
@GetMapping("/combined")
public ModelAndView combined() {
    return new ModelAndView("hello")
            .addObject("message", "Xin chào");
}
~~~

Đây là hai method minh họa trong controller cùng có template `hello.html`. Chúng có thể tạo HTML giống nhau; không có quy tắc hiệu năng tổng quát nói `ModelAndView` luôn nhanh hơn hay chậm hơn `Model`.

**Nên dùng `Model` + `String`** khi method ngắn, đọc tự nhiên: “thêm attribute vào model, trả tên view”. **Nên dùng `ModelAndView`** khi cần tập hợp view/model/status trong một return object, khi xử lý exception theo hướng HTML hoặc khi framework extension phù hợp với kiểu `ModelAndView`.

Trả `ModelAndView` không bắt buộc phải khai báo tham số `Model`; tuy vậy bạn vẫn có thể có implicit model từ các cơ chế khác. Đừng thêm dữ liệu hai lần qua cả model argument và ModelAndView trừ khi có mục đích rõ.

## 15. Những lỗi phổ biến và cách chẩn đoán

| Dấu hiệu | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| Browser hiển thị đúng chữ `demo` thay vì HTML | Dùng `@RestController` hoặc `@ResponseBody` | Đổi thành `@Controller` cho handler cần view |
| `TemplateInputException` hoặc view không tìm được | Logical view không khớp vị trí template/prefix/suffix | Kiểm tra `templates/demo.html`, tên view và Thymeleaf dependency |
| Template render nhưng attribute rỗng | Sai key giữa `addAttribute` và `${...}` | So lại tên property, null và logic controller |
| Gọi `model.put(...)` nhưng biến là `Model` | `Model` là interface không có `put` | Dùng `addAttribute` hoặc đổi sang `ModelMap` |
| Template có vòng forward/redirect | Logic định tuyến không hợp lý hoặc tên view trùng route | Kiểm tra return value, view resolver và mapping |
| Sau POST/redirect mất thông báo | Model thường không sống qua HTTP request mới | Sử dụng `RedirectAttributes.addFlashAttribute` |
| Form invalid mất dữ liệu cũ | Ghi đè form object hoặc không render đúng binding | Giữ `@ModelAttribute` + `BindingResult` và Thymeleaf `th:field` |
| `void` ở HTML controller dẫn tới view ngoài ý muốn | Spring áp dụng default view name | Return tên view tường minh hoặc xử lý response rõ |
| Trả `Map` nhưng muốn JSON | Không có `@ResponseBody` hoặc controller kiểu REST | Dùng `@RestController` hoặc `ResponseEntity` |
| User input trở thành tên template | Tạo view name bằng dữ liệu client không kiểm soát | Whitelist view name và tách input khỏi cơ chế định tuyến |
| Test view name PASS nhưng trang chạy lỗi | MockMvc chưa kiểm tra render template thực | Thêm integration test kiểm tra HTML và tài nguyên |
| HTML đúng nhưng URL sau POST vẫn là endpoint POST | Handler render trực tiếp thay vì redirect | Dùng Post/Redirect/Get nếu quy trình yêu cầu |

### Lỗi `Model` không phải response JSON

~~~java
@GetMapping("/wrong")
public Model wrong(Model model) {
    model.addAttribute("message", "Hello");
    return model;
}
~~~

Đây là **minh họa trong HTML `@Controller`**: Spring có thể coi return value là model và suy ra tên view từ URL. Không dùng mẫu này để trả REST JSON. Với REST endpoint, nên trả DTO/record từ `@RestController` hoặc `ResponseEntity<Dto>`.

### Lỗi view name dùng trực tiếp client input

~~~java
@GetMapping("/show")
public String show(@RequestParam String template) {
    return template;
}
~~~

Đây là **mẫu phản diện**, không nên dùng. Client có thể tác động vào việc chọn template/redirect/forward. Nên map đầu vào tới một tập **view name cố định được cho phép** ở phía server, ví dụ enum hoặc `switch` với default an toàn.

## 16. Câu hỏi ôn tập

**1. `Model` và `ModelMap` khác nhau thế nào?**

`Model` là interface API của model; `ModelMap` là class dựa trên Map, có `put`, `get`, `addAttribute`. Cả hai đều dùng để chứa attribute mà view đọc được trong request.

**2. Tên view logic `"orders/list"` có nghĩa Spring truy cập URL `/orders/list` không?**

Không. Nó là tên view để resolver tìm tài nguyên render, ví dụ `templates/orders/list.html`. Chỉ các prefix như `redirect:` mới diễn đạt việc browser điều hướng sang URL khác.

**3. Khi nào chọn `ModelAndView`?**

Khi muốn trả kết quả gói view + model và đôi khi status trong một object. Nếu chỉ cần thêm attribute và trả tên view, `Model` + `String` thường ngắn gọn.

**4. Vì sao `@RestController` trả `"hello"` không render `hello.html`?**

Vì `@RestController` bao gồm ngữ nghĩa `@ResponseBody`, nên String được ghi thành HTTP response body thông qua converter, không phải logical view name.

**5. View name ngầm định dùng khi nào?**

Khi handler HTML không chỉ rõ tên view và response chưa được xem là xử lý hoàn toàn. Spring có thể dựa trên request path qua `RequestToViewNameTranslator` để suy ra tên view.

**6. Model của POST có tự giữ cho GET sau redirect không?**

Không. Đây là request khác. Khi cần chuyển thông điệp ngắn qua redirect, dùng `RedirectAttributes.addFlashAttribute` và hiểu vòng đời flash.

## 17. Checklist thực hành

1. Với trang HTML, dùng `@Controller`, return logical view name tường minh và truyền attribute qua `Model` hoặc `ModelMap`.
2. Với logic cần gom view + model + status, dùng `ModelAndView` và khai báo view name rõ.
3. Xác nhận ViewResolver đúng loại template, đường dẫn, prefix/suffix và tên file có phân biệt hoa/thường trên hệ điều hành tương ứng.
4. Test cả tên view, attribute model và HTML render. Với form, test GET tạo form, POST hợp lệ redirect, POST không hợp lệ trả view với field errors.
5. Kiểm soát nguồn dữ liệu đưa vào template, dùng escaping của engine và tránh xây tên view từ input client.
6. Phân biệt `redirect:`, `forward:`, implicit view, `@ResponseBody` và `@RestController`.
7. Giữ model dành cho dữ liệu hiển thị, không nhầm với repository/entity hoặc cơ chế lưu dài hạn.

## Tài liệu chính thức

- [Spring Framework 6.2 — Controller Return Values](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/return-types.html)
- [Spring Framework 6.2 — Controller Method Arguments](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/arguments.html)
- [Spring Framework 6.2 — `@ModelAttribute` methods](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-modelattrib-methods.html)
- [Spring Framework 6.2 — Redirect Attributes](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/redirecting-passing-data.html)
- [Spring Framework — View Resolvers](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/viewresolver.html)
- [Spring Boot — Template Engines and MVC Views](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-forms-002">ViewResolver, thứ tự phân giải và tích hợp template engine</a>

<details>
<summary>Xem chi tiết</summary>

# ViewResolver, thứ tự phân giải và tích hợp template engine trong Spring MVC

## 1. Bài toán và mục tiêu

Trong Spring MVC, một controller có thể trả về chuỗi `"books/detail"`, nhưng trình duyệt cuối cùng nhận HTML được tạo từ file `templates/books/detail.html`. Ai tìm ra file tương ứng? Tại sao tên view đôi khi chạy qua Thymeleaf, đôi khi lại bị chuyển sang JSP? Cần hiểu **ViewResolver**, **View** và thứ tự phân giải để giải đáp.

Bài học này giúp bạn:

- Phân biệt URL request, logical view name, `Model`, `ViewResolver`, `View` và template engine.
- Theo dõi đường đi từ kết quả của `@Controller` đến HTML được render.
- Hiểu nhiều `ViewResolver` được xét theo thứ tự `order` và vì sao `InternalResourceViewResolver` dành cho JSP phải đặt cuối.
- Phân biệt `BeanNameViewResolver`, `InternalResourceViewResolver`, `ThymeleafViewResolver`, `FreeMarkerViewResolver` và `ContentNegotiatingViewResolver`.
- Cấu hình Thymeleaf theo Spring Boot, dùng prefix/suffix và view names; phân biệt thứ tự MVC ViewResolver với thứ tự template resolver riêng của engine.
- Hiểu `redirect:`, `forward:`, `ModelAndView`, form binding và trường hợp `@RestController` không cần view resolution.
- Chạy ứng dụng Spring Boot 3 + Java 17 với HTML templates, form validation, redirect và test bằng `MockMvc`.

**Phạm vi:** Spring MVC trên Servlet stack, phù hợp Spring Framework 6.x, Spring Boot 3.x và Java 17+. Những class của WebFlux dùng cơ chế reactive khác và không phải trọng tâm bài.

## 2. Logical view name khác HTML, URL và API response

Một controller minh họa:

```java
@Controller
class BookPageController {

    @GetMapping("/books/7")
    String detail(Model model) {
        model.addAttribute("title", "Học Spring MVC");
        return "books/detail";
    }
}
```

*Đoạn minh họa nằm trong class controller có imports phù hợp; ví dụ project đầy đủ ở phần 7.*

Các vai trò:

| Thành phần | Ví dụ | Trách nhiệm |
| --- | --- | --- |
| HTTP URL | `GET /books/7` | Xác định request mà client muốn gửi |
| Handler mapping | `@GetMapping("/books/7")` | Chọn controller method |
| Model | `{"title":"Học Spring MVC"}` | Dữ liệu render cho request hiện tại |
| Logical view name | `"books/detail"` | Tên trừu tượng mà controller trả về |
| ViewResolver | `ThymeleafViewResolver` | Tìm đối tượng View ứng với tên và locale |
| View | Thymeleaf View | Render model bằng template phù hợp |
| Template | `classpath:/templates/books/detail.html` | Nguồn markup/biểu thức được engine xử lý |
| HTTP response | `text/html` | Nội dung HTML trả về cho trình duyệt |

Dữ liệu thường đi theo chuỗi:

```text
HTTP GET /books/7
  → DispatcherServlet
  → HandlerMapping chọn BookPageController.detail
  → HandlerAdapter gọi controller
       ├── Model có thuộc tính title
       └── view name: "books/detail"
  → ViewResolver(s): resolveViewName("books/detail", locale)
       ├── resolver 1 không nhận → null → thử resolver 2
       └── resolver 2 nhận → trả Thymeleaf View
  → View.render(model, request, response)
       ├── đọc/parse template books/detail.html
       ├── áp dụng model + biểu thức template
       └── ghi HTML vào HttpServletResponse
```

**ViewResolver giải quyết tên thành View; View thực sự render.** Controller không cần biết vị trí tuyệt đối của template nếu tên logic và convention đã cấu hình đúng. `ViewResolver` không thay thế `HandlerMapping`: cái thứ nhất hoạt động sau khi controller được chọn, cái thứ hai xác định endpoint HTTP.

### 2.1. Return type có thể bỏ qua bước giải tên view

Trong `@Controller`, một `String` return thông thường là tên view. Trong `@RestController` hoặc method có `@ResponseBody`, `String` lại là body trực tiếp. Ví dụ:

```java
@Controller
class PageController {
    @GetMapping("/home")
    String home() {
        return "home"; // logical view name
    }

    @ResponseBody
    @GetMapping("/plain")
    String plain() {
        return "Hello"; // response body là chuỗi Hello
    }
}

@RestController
class ApiController {
    @GetMapping("/api/hello")
    Map<String, String> hello() {
        return Map.of("message", "Hello"); // Jackson serialize JSON
    }
}
```

*Đây là phần minh họa riêng, cần import `Map` và các annotation khi dùng trong project.* `@RestController` kết hợp `@Controller` với semantics `@ResponseBody`. `ResponseEntity<?>` cũng thường viết thẳng HTTP response qua message converter. Không mong Thymeleaf render `home.html` nếu một `@RestController` trả `"home"`: client thường nhận đúng chữ `home`.

### 2.2. ModelAndView và View object

```java
@GetMapping("/dashboard")
ModelAndView dashboard() {
    return new ModelAndView(
        "dashboard",
        "heading",
        "Trang tổng quan"
    );
}
```

`ModelAndView` chứa view name và model; kết quả tương đương cách truyền `Model` rồi return `"dashboard"`. Cũng có thể cấp trực tiếp một `View` instance, lúc đó không nhất thiết cần resolver tìm theo tên. Trường hợp method không trả view name, MVC đôi khi có thể suy ra tên từ request qua `RequestToViewNameTranslator`, nhưng nên trả tên tường minh để API/controller dễ bảo trì.

## 3. Những ViewResolver thông dụng

| Resolver | Cơ chế | Tình huống thực tế |
| --- | --- | --- |
| `BeanNameViewResolver` | Tìm Spring bean có tên trùng view name và có kiểu `View` | CSV, Excel, PDF hoặc custom View bean |
| `InternalResourceViewResolver` | Thêm prefix/suffix rồi tạo `InternalResourceView` forward/include qua Servlet | JSP/JSTL; **luôn đặt cuối chuỗi** |
| `UrlBasedViewResolver` | Base class ánh xạ tên view sang URL theo convention | Nền tảng cho các resolver dựa trên URL |
| `FreeMarkerViewResolver` | Tạo `FreeMarkerView` cho template tương ứng | Server-side FreeMarker |
| `ThymeleafViewResolver` | Tạo Thymeleaf View, engine xử lý file template | Thymeleaf trong Spring MVC/Boot |
| `ContentNegotiatingViewResolver` | Dựa trên media type/Accept chọn View thích hợp do resolver khác cung cấp | Một resource nhiều dạng trình bày |
| `ViewResolverComposite` | Tập hợp các resolver đã cấu hình | Kết quả cấu hình bằng MVC registry |

Một `ViewResolver` có contract trả `null` nếu không tìm/không phụ trách view name. Khi có nhiều resolver, điều này cho phép phần tử kế tiếp thử. Nhưng không phải resolver nào cũng **biết** template có tồn tại trước khi trả View. Resolver quá tổng quát có thể “chiếm” tên rồi View thất bại ở bước render thay vì nhường cơ hội cho resolver khác.

`AbstractCachingViewResolver` có cơ chế cache các View đã resolve. **Cache View khác cache HTML**: model vẫn có thể khác theo request. Template engine cũng có cache riêng cho nội dung đã parse và HTTP cache/CDN còn là lớp khác nữa.

### 3.1. JSP: InternalResourceViewResolver

Ví dụ:

```java
@Bean
InternalResourceViewResolver jspViewResolver() {
    InternalResourceViewResolver resolver = new InternalResourceViewResolver();
    resolver.setPrefix("/WEB-INF/jsp/");
    resolver.setSuffix(".jsp");
    resolver.setOrder(Ordered.LOWEST_PRECEDENCE);
    return resolver;
}
```

*Đoạn cấu hình minh họa ở ứng dụng JSP riêng; cần import `@Bean`, `Ordered` và `InternalResourceViewResolver`. Không thêm đồng thời vào demo Thymeleaf ở phần 7.*

Tên `"orders/detail"` được ánh xạ thành:

```text
/WEB-INF/jsp/orders/detail.jsp
```

Thường đặt JSP dưới `WEB-INF` để trình duyệt không trực tiếp mở file template. Khi dùng JSTL, cần cấu hình `JstlView` và thư viện JSTL phù hợp. Spring Boot executable JAR thông thường không phải cách triển khai JSP thuận tiện; nếu cần JSP, hãy kiểm tra loại đóng gói WAR/container và các giới hạn JSP của Boot.

### 3.2. BeanNameViewResolver và custom View

Nếu controller return `"monthlyCsv"` và container có bean tên `monthlyCsv` thuộc kiểu `View` thì `BeanNameViewResolver` có thể trả bean đó. Đây là cách ánh xạ trực tiếp tới một View đặc biệt thay vì file template. Một bean service bình thường cùng tên **không** trở thành View.

### 3.3. Thymeleaf và FreeMarker

Với Spring Boot, thêm `spring-boot-starter-thymeleaf` sẽ kích hoạt những cấu hình cần thiết cho Thymeleaf khi điều kiện auto-configuration phù hợp. FreeMarker có starter riêng `spring-boot-starter-freemarker`. Khi chỉ dùng một engine, thường nên để Boot cấu hình mặc định, sau đó chỉnh prefix/suffix/cache qua properties.

Nếu cùng cài nhiều template engines, cần xem **resolver nào thực sự phụ trách từng tên view**. Có thể giới hạn view name patterns và điều chỉnh order để tránh một engine cố render template thuộc engine khác.

## 4. Thứ tự ViewResolver: số nhỏ ưu tiên trước

Spring MVC xem xét nhiều resolver theo `Ordered` và cấu hình thực tế:

- `order = -10` ưu tiên trước `0`.
- `order = 0` ưu tiên trước `10`.
- Resolver có độ ưu tiên thấp hơn (giá trị order lớn hơn) được xét muộn.
- `Ordered.LOWEST_PRECEDENCE` là mức ưu tiên rất thấp, thích hợp cho resolver dự phòng nhận nhiều tên.
- Khi resolver trả `null`, chuỗi có thể tiếp tục; khi nó trả View, kết quả được dùng theo pipeline đã chọn.

Giả sử **cấu hình chủ động**:

| Resolver | Order | View names được nhận |
| --- | ---: | --- |
| `BeanNameViewResolver` | 0 | Chỉ tên có View bean phù hợp |
| `ThymeleafViewResolver` | 10 | `pages/*` |
| `InternalResourceViewResolver` | 1000 | JSP fallback |

Khi controller return `"pages/home"`:

```text
BeanNameViewResolver: không có bean View → null
ThymeleafViewResolver: nhận "pages/home" → Thymeleaf View
InternalResourceViewResolver: không cần chạy
```

Khi return `"legacy/report"`:

```text
BeanNameViewResolver: không có bean → null
ThymeleafViewResolver: không thuộc pages/* → null
InternalResourceViewResolver: /WEB-INF/jsp/legacy/report.jsp
```

Các **con số order trong bảng chỉ là ví dụ**; không phải thứ tự mặc định cố định của mọi phiên bản Spring Boot. Khi debug, phải kiểm tra các bean trong ApplicationContext và cấu hình môi trường thật.

### 4.1. Tại sao JSP resolver bắt buộc cuối chuỗi?

`InternalResourceViewResolver` không thể xác nhận chắc file JSP tồn tại khi mới có view name; thông thường cần thực hiện `RequestDispatcher.forward()` rồi Servlet/JSP container mới xử lý. Do đó resolver này có thể tạo `InternalResourceView` cho hầu hết tên. Nếu nó đứng **trước Thymeleaf**, một tên `"pages/home"` có thể bị đưa tới `/WEB-INF/jsp/pages/home.jsp` dù bạn mong Thymeleaf, khiến trang không render đúng.

**Quy tắc:** đặt `InternalResourceViewResolver` cuối toàn bộ resolver order nếu nhiều ViewResolvers cùng tồn tại. Đây là quy tắc được tài liệu chính thức Spring MVC nhấn mạnh, không chỉ là thói quen cấu hình.

### 4.2. Cấu hình với WebMvcConfigurer

Một ứng dụng Spring MVC **chủ động dùng JSP** có thể cấu hình các resolvers như sau:

```java
@Configuration
class LegacyViewConfig implements WebMvcConfigurer {
    @Override
    public void configureViewResolvers(ViewResolverRegistry registry) {
        registry.beanName();
        registry.jsp("/WEB-INF/jsp/", ".jsp");
    }
}
```

*Đây là đoạn minh họa riêng; cần import `Configuration`, `WebMvcConfigurer`, `ViewResolverRegistry` và môi trường JSP phù hợp. Không chèn vào project Boot/Thymeleaf mẫu.*

`ViewResolverRegistry` còn hỗ trợ đăng ký resolver tùy chỉnh và bật content negotiation khi cần. Trong Spring Boot, **không bật `@EnableWebMvc` chỉ để chỉnh một thuộc tính template**: annotation đó chuyển nhiều phần MVC configuration về bạn và có thể làm mất auto-configuration đang có. Dùng properties và `WebMvcConfigurer` phù hợp trước khi chọn thay thế toàn bộ MVC defaults.

### 4.3. Thứ tự MVC resolver và template resolver là hai chuyện

`ThymeleafViewResolver#setOrder(...)` ảnh hưởng thứ tự **Spring MVC ViewResolver**. Nhưng Thymeleaf còn có các **ITemplateResolver** nội bộ để tìm nguồn template, prefix/suffix và cache. Thuộc tính như `spring.thymeleaf.template-resolver-order` thuộc chuỗi **template resolvers của Thymeleaf**, không sắp xếp các Spring MVC resolvers.

```text
Spring MVC ViewResolver chain
   └── ThymeleafViewResolver
        └── ThymeleafView
             └── Thymeleaf TemplateEngine
                  └── TemplateResolver chain
                       └── classpath:/templates/pages/home.html
```

Nếu view tên `"pages/home"` rơi vào JSP resolver, sửa **MVC ViewResolver order/viewNames**; nếu Thymeleaf đã chọn nhưng tìm sai file, kiểm tra **template prefix/suffix/resource resolution**.

## 5. ContentNegotiatingViewResolver và HTTP Accept

Trong một số ứng dụng, cùng tài nguyên có thể được thể hiện bằng HTML, PDF hoặc JSON:

```http
GET /reports/42 HTTP/1.1
Accept: text/html
```

hoặc:

```http
GET /reports/42 HTTP/1.1
Accept: application/pdf
```

`ContentNegotiatingViewResolver` không tự render HTML/PDF. Nó hỏi các resolver khác lấy candidate `View` rồi chọn View có media type tương thích với nội dung client chấp nhận. Có thể cung cấp thêm `defaultViews` như JSON/PDF View phù hợp. **Không có PDF View thì Accept: application/pdf không tự tạo ra PDF.**

Đường đi phổ biến của REST JSON lại khác:

```text
@RestController trả Java object
 → @ResponseBody return-value handler
 → MappingJackson2HttpMessageConverter
 → application/json response body
```

Trong trường hợp này không bắt buộc có `ContentNegotiatingViewResolver` hoặc `MappingJackson2JsonView`. Phân biệt **content negotiation khi chọn View** với **content negotiation khi chọn HTTP message converter**, dù cả hai đều liên quan đến `Accept`.

Spring Boot có thể tự cấu hình `ContentNegotiatingViewResolver` tùy loại bean `View` và các điều kiện hiện hành. Đừng giả định nó luôn có trong mọi ứng dụng; xem metadata/bean list của context cụ thể.

## 6. Prefix redirect: và forward:

Khi controller trả view name có prefix đặc biệt:

```java
@PostMapping("/save")
String save() {
    return "redirect:/documents/7";
}

@GetMapping("/legacy-entry")
String legacyEntry() {
    return "forward:/internal/page";
}
```

*Đoạn minh họa trong một `@Controller`.*

- `redirect:/documents/7` tạo **redirect HTTP** đến URL mới; browser gửi request mới. Đây là công cụ của Post/Redirect/Get (PRG).
- `forward:/internal/page` yêu cầu **Servlet forward nội bộ** trong cùng request, không phát sinh HTTP redirect cho trình duyệt.
- `redirect:` được các resolver dựa trên URL hỗ trợ như một chỉ thị đặc biệt. Đây không phải tên file HTML.
- `forward:` hữu ích khi cần chuyển đến một URL được Servlet xử lý; không cần dùng để chỉ tới JSP nếu đã cấu hình logical view name qua JSP resolver.

Nếu sau POST hợp lệ cần hiển thị thông báo ở GET tiếp theo, dùng `RedirectAttributes.addFlashAttribute(...)`. Thông tin flash được giữ tạm qua redirect, không nhất thiết xuất hiện trên URL. Nếu form **có lỗi validation**, thường trả lại cùng view trong request hiện tại để giữ input và `BindingResult` thay vì redirect vô điều kiện.

Tránh nối trực tiếp giá trị URL không tin cậy vào `"redirect:" + userInput` vì có thể sinh **open redirect**; whitelist route/host được phép.

## 7. Demo Spring Boot 3 + Thymeleaf + Java 17 hoàn chỉnh

Ứng dụng ví dụ có đủ các đường đi:

- `GET /` → controller trả `"home"`, Thymeleaf render `home.html`.
- `GET /greetings/Ada` → `"greetings/detail"`, HTML dùng `Model`.
- `GET /model-view` → `ModelAndView` trả về cùng template `home`.
- `GET /api/greetings/Ada` → `@RestController` trả JSON, **bỏ qua** view resolution.
- `GET /signup` và `POST /signup` → Thymeleaf form, `@Valid` và `BindingResult`; form hợp lệ redirect tới trang hoàn tất.

Dùng Java 17+, Maven và Spring Boot **3.3.13**. Không cần database hoặc Servlet JSP engine.

### 7.1. Cấu trúc

```text
view-resolution-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/example/ViewResolutionApplication.java
    │   └── resources/
    │       ├── application.properties
    │       └── templates/
    │           ├── home.html
    │           ├── greetings/detail.html
    │           ├── signup.html
    │           └── signup-done.html
    └── test/java/example/ViewResolutionTest.java
```

### 7.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>view-resolution-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
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
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
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
```

Thymeleaf starter giúp Boot cấu hình template engine và resolver. Không cần tạo một `ThymeleafViewResolver` bean thủ công để dùng vị trí template mặc định.

### 7.3. application.properties

```properties
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
spring.thymeleaf.mode=HTML
spring.thymeleaf.encoding=UTF-8
# Tắt cache cho môi trường học tập/development.
spring.thymeleaf.cache=false
```

Những giá trị prefix/suffix trên tương ứng với mặc định của Thymeleaf trong Boot 3.x. Với return `"greetings/detail"`, engine tìm template `classpath:/templates/greetings/detail.html`. Đừng dùng `spring.mvc.view.prefix` để điều chỉnh Thymeleaf; thuộc tính đó phục vụ resolution Servlet resource/JSP ở cấu hình phù hợp.

`spring.thymeleaf.view-names` và `spring.thymeleaf.excluded-view-names` giúp giới hạn nhóm logical names của Thymeleaf khi dùng nhiều engine. Trong production thường nên bật template cache để cải thiện hiệu suất.

### 7.4. ViewResolutionApplication.java

Toàn bộ class/record ở cùng file `src/main/java/example/ViewResolutionApplication.java`; chỉ class main được khai báo `public`.

```java
package example;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@SpringBootApplication
public class ViewResolutionApplication {
    public static void main(String[] args) {
        SpringApplication.run(ViewResolutionApplication.class, args);
    }
}

class SignupForm {
    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    public SignupForm() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

@Controller
class PageController {

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("heading", "Trang chủ MVC");
        return "home";
    }

    @GetMapping("/model-view")
    ModelAndView modelView() {
        return new ModelAndView("home", "heading", "Trang ModelAndView");
    }

    @GetMapping("/greetings/{name}")
    String greeting(@PathVariable String name, Model model) {
        model.addAttribute("message", "Xin chào, " + name + "!");
        return "greetings/detail";
    }

    @GetMapping("/signup")
    String showSignup(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "signup";
    }

    @PostMapping("/signup")
    String submit(@Valid @ModelAttribute("signupForm") SignupForm form,
                  BindingResult errors,
                  RedirectAttributes redirectAttributes) {

        if (errors.hasErrors()) {
            return "signup"; // render lại form với BindingResult
        }

        redirectAttributes.addFlashAttribute(
                "notice", "Đã đăng ký: " + form.getName());

        return "redirect:/signup/done";
    }

    @GetMapping("/signup/done")
    String done() {
        return "signup-done";
    }
}

@RestController
class GreetingApiController {
    @GetMapping("/api/greetings/{name}")
    Map<String, String> greeting(@PathVariable String name) {
        return Map.of("message", "Xin chào, " + name + "!");
    }
}
```

Để ý `PageController` và `GreetingApiController` có các đường return khác nhau: String ở `@Controller` là logical view name, trong khi Map ở `@RestController` được viết thẳng thành JSON. `BindingResult` nằm ngay sau form DTO để controller có thể render lại cùng trang khi validation không đạt.

### 7.5. templates/home.html

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Trang chủ</title>
</head>
<body>
  <h1 th:text="${heading}">Tiêu đề mặc định</h1>
  <nav>
    <a th:href="@{/greetings/Ada}">Lời chào</a>
    <a th:href="@{/signup}">Đăng ký</a>
  </nav>
</body>
</html>
```

Thymeleaf dùng `th:text` để đưa model attribute `heading` vào HTML. Cú pháp `${heading}` là một biểu thức Thymeleaf hợp lệ sau khi thay ký hiệu `$` thành dấu dollar `$`; file được tạo ra trong bài này sử dụng đúng dấu `$`.

### 7.6. templates/greetings/detail.html

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Lời chào</title>
</head>
<body>
  <h1 th:text="${message}">Lời chào mặc định</h1>
</body>
</html>
```

`th:text` mặc định escape các ký tự HTML từ model, giúp tránh việc tên người dùng chứa markup được render như HTML. Với dữ liệu không tin cậy, tránh `th:utext` trừ khi đã sanitize theo hợp đồng an toàn.

### 7.7. templates/signup.html

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Đăng ký</title>
</head>
<body>
  <h1>Đăng ký</h1>
  <form th:action="@{/signup}" th:object="${signupForm}" method="post">
    <label>Tên
      <input type="text" th:field="*{name}">
    </label>
    <span th:if="${#fields.hasErrors('name')}"
          th:errors="*{name}">Lỗi tên</span>

    <label>Email
      <input type="email" th:field="*{email}">
    </label>
    <span th:if="${#fields.hasErrors('email')}"
          th:errors="*{email}">Lỗi email</span>

    <button type="submit">Gửi</button>
  </form>
</body>
</html>
```

`th:object` truy cập model attribute `signupForm`. `th:field` liên kết input với property, còn `#fields.hasErrors(...)` và `th:errors` truy cập thông tin validation từ `BindingResult`. Ứng dụng có Spring Security cần tính tới CSRF token theo cấu hình; sample chỉ dùng MVC/Validation.

### 7.8. templates/signup-done.html

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Hoàn tất</title>
</head>
<body>
  <h1>Hoàn tất</h1>
  <p th:text="${notice}">Thông báo</p>
  <a th:href="@{/signup}">Quay lại</a>
</body>
</html>
```

Thông báo `notice` là flash attribute, được lưu tạm qua redirect. Nếu truy cập trực tiếp `/signup/done`, thuộc tính này có thể không tồn tại; ứng dụng thực nên quy định hành vi hiển thị phù hợp.

### 7.9. Chạy ứng dụng

Cần Java 17+ và Maven. Trong thư mục chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Dùng browser mở:

```text
http://localhost:8080/
http://localhost:8080/greetings/Ada
http://localhost:8080/signup
http://localhost:8080/api/greetings/Ada
```

Hoặc sử dụng cURL:

```bash
curl -i http://localhost:8080/greetings/Ada
curl -i http://localhost:8080/api/greetings/Ada
curl -i -X POST http://localhost:8080/signup \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data "name=Ada&email=ada@example.com"
```

Kết quả kỳ vọng:

- `GET /greetings/Ada` trả 200 với HTML có `<h1>Xin chào, Ada!</h1>`, được render từ template.
- `GET /api/greetings/Ada` trả 200 với JSON body `{"message":"Xin chào, Ada!"}`; không cần template.
- `POST /signup` hợp lệ trả redirect (thường HTTP 302) tới `/signup/done`; request GET sau đó có thể dùng flash attribute hiển thị `Đã đăng ký: Ada`.
- `POST /signup` với `name=` trống sẽ render lại template signup; controller tự chọn trả view và mặc định có thể HTTP 200 dù validation lỗi được hiển thị trong trang. Đây không phải tình huống REST API tự động trả 400.

Dùng `curl.exe` trên Windows PowerShell nếu `curl` là alias khác; cú pháp nối dòng bằng `\` ở trên dành cho shell POSIX.

## 8. Kiểm thử MVC view resolution bằng MockMvc

File `src/test/java/example/ViewResolutionTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ViewResolutionTest {

    @Autowired
    MockMvc mvc;

    @Test
    void homeRendersTemplateAndModel() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("home"))
            .andExpect(content().string(
                    containsString("<h1>Trang chủ MVC</h1>")));
    }

    @Test
    void nestedLogicalViewResolvesCorrectTemplate() throws Exception {
        mvc.perform(get("/greetings/Ada"))
            .andExpect(status().isOk())
            .andExpect(view().name("greetings/detail"))
            .andExpect(content().string(
                    containsString("<h1>Xin chào, Ada!</h1>")));
    }

    @Test
    void modelAndViewAlsoWorks() throws Exception {
        mvc.perform(get("/model-view"))
            .andExpect(status().isOk())
            .andExpect(view().name("home"))
            .andExpect(content().string(
                    containsString("Trang ModelAndView")));
    }

    @Test
    void restControllerWritesJsonWithoutView() throws Exception {
        mvc.perform(get("/api/greetings/Ada"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                    .value("Xin chào, Ada!"));
    }

    @Test
    void invalidFormReturnsSameTemplateWithBindingErrors()
            throws Exception {
        mvc.perform(post("/signup")
                    .param("name", "")
                    .param("email", "ada@example.com"))
            .andExpect(status().isOk())
            .andExpect(view().name("signup"))
            .andExpect(model().attributeHasFieldErrors(
                    "signupForm", "name"));
    }

    @Test
    void validFormRedirectsWithFlashAttribute() throws Exception {
        mvc.perform(post("/signup")
                    .param("name", "Ada")
                    .param("email", "ada@example.com"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signup/done"))
            .andExpect(flash().attribute(
                    "notice", "Đã đăng ký: Ada"));
    }
}
```

Chạy `mvn test`. Hai phép kiểm tra khác nhau cần chú ý: `view().name(...)` xác nhận tên logic controller trả về, còn `content().string(...)` xác nhận HTML đã render với dữ liệu từ model. Nếu chỉ assert view name mà không thử nội dung hoặc resource, test có thể “xanh” ngay cả khi template thực tế không tồn tại hoặc engine không chạy.

`MockMvc` chạy trong test context, không yêu cầu mở cổng HTTP. Với `@WebMvcTest` dạng slice, cần bảo đảm view engine configuration/resources cần thiết được nạp; ví dụ sử dụng `@SpringBootTest` + `@AutoConfigureMockMvc` để đi qua đúng auto-configuration đầy đủ.

## 9. Tích hợp nhiều engine: đường đi và chiến lược migration

Nếu cùng duy trì Thymeleaf và JSP hoặc Thymeleaf và FreeMarker:

1. Xác định nhóm view name theo công nghệ, ví dụ `pages/*` cho Thymeleaf, `legacy/*` cho JSP.
2. Đặt giới hạn tên view trên engine phù hợp; Thymeleaf hỗ trợ `spring.thymeleaf.view-names` và `spring.thymeleaf.excluded-view-names`.
3. Kiểm tra ViewResolver `order` thực tế từ ApplicationContext. Resolver có độ chính xác cao hoặc chỉ nhận tên cụ thể nên đứng sớm; JSP InternalResource resolver đứng cuối.
4. Bảo đảm tên không thuộc nhóm một engine **không bị engine đó giữ lại** và báo lỗi template trong bước render.
5. Viết test cho một HTML Thymeleaf page, một JSP page (nếu có), một custom View và một JSON endpoint.
6. Kiểm tra packaging: JSP cần Servlet container/packaging hỗ trợ; nhiều mẫu Boot JAR nên ưu tiên template engines hỗ trợ classpath.
7. Giữ model contract rõ ràng khi di chuyển từ engine cũ sang mới, để frontend còn hiển thị dữ liệu và lỗi form đúng.

**Không thể chỉ đổi suffix từ `.jsp` sang `.html` rồi coi là migration hoàn chỉnh:** cú pháp template, tag library, security escaping, layout, form binding và cách cache có thể khác.

## 10. Chẩn đoán lỗi view resolution

| Hiện tượng | Nguyên nhân thường gặp | Hướng kiểm tra |
| --- | --- | --- |
| Browser thấy chữ `home` thay vì HTML | Method `@ResponseBody` hoặc class `@RestController` | Dùng `@Controller`/view name thay cho response body |
| Không tìm thấy `home.html` | Prefix/suffix sai hoặc file không nằm trên classpath | Kiểm tra `templates/home.html` và `spring.thymeleaf.*` |
| Thymeleaf không được gọi, request chuyển JSP | JSP resolver đứng trước hoặc view names không khoanh vùng | Kiểm tra order và giới hạn resolver |
| View name đúng, response lỗi 500 | Template lỗi cú pháp, thiếu resource, lỗi biểu thức hoặc render | Xem cause/log Thymeleaf, template path |
| GET request trả 404 | Không có HTTP handler, hoặc logic/resource khác | Kiểm tra HandlerMapping trước ViewResolver |
| Redirect bất ngờ thêm query parameters | Model attributes được đưa vào redirect ngoài dự kiến | Dùng `RedirectAttributes` tường minh |
| Submit form lỗi nhưng mất thông báo | Redirect sang request mới khi chưa chuyển flash state | Trả về form view trong cùng request |
| `th:text` rỗng | Model thiếu attribute hoặc sai tên | Kiểm tra `model.addAttribute(...)` và biểu thức |
| Sửa template nhưng không thấy đổi | Template engine cache hoặc HTTP cache | Xác định cache layer và chỉ tắt cache dev |
| Có JSON trên API nhưng không có HTML | `@RestController`/message converter đang hoạt động đúng | Tách controller API và trang HTML theo ý định |
| Accept PDF nhưng không ra PDF | Không có View hỗ trợ media type PDF | Đăng ký View thực tế, đừng mong content negotiation tự tạo file |
| Đổi `spring.mvc.view.prefix` không ảnh hưởng Thymeleaf | Đang thay cấu hình JSP/Servlet resolver | Dùng `spring.thymeleaf.prefix/suffix` |
| Bật `@EnableWebMvc` làm thay đổi nhiều hành vi | Ghi đè phần lớn MVC auto-configuration | Tùy chỉnh qua Boot properties/`WebMvcConfigurer` trước |

### 10.1. Các bước debug nên làm

1. Kiểm tra **controller nào được chọn**, return type, annotation trên method/class và logical view name thực tế.
2. Nếu đang xử lý `@ResponseBody`/`ResponseEntity`, kiểm tra **HTTP message converter**, không chỉnh resolver template.
3. Liệt kê ViewResolver beans, mức `order`, danh sách view names và bean nào có thể trả null.
4. Kiểm tra template nằm tại đường dẫn dự kiến theo prefix/suffix; giữ nhất quán chữ hoa/thường.
5. Đọc stack trace để phân biệt **handler không tồn tại**, **resolver không chọn được View**, **View render lỗi**.
6. Dùng `MockMvc` assert cả `view().name(...)` lẫn body HTML; với JSON assert media type và body riêng.
7. Khi cache gây khó hiểu, kiểm tra lần lượt resolver cache, template engine cache và HTTP/browser cache.

Có thể bật debug log trong môi trường phát triển:

```properties
logging.level.org.springframework.web.servlet.view=DEBUG
logging.level.org.thymeleaf=INFO
```

Khi test cần thêm thông tin, dùng `andDo(print())` trên `MockMvc` để quan sát model, view name và response. Không log/đưa ra response dữ liệu nhạy cảm từ model trong production.

## 11. An toàn khi render template

- Dùng `th:text` cho input không tin cậy vì nó HTML-escape nội dung; thận trọng với `th:utext` vì nó có thể render HTML chưa được làm sạch.
- Không cho client tùy chọn trực tiếp tên template/view tùy ý rồi return; chỉ sử dụng tên trong whitelist rõ ràng.
- Đừng ghép `redirect:` với URL do client cung cấp nếu chưa kiểm tra host/path, để tránh open redirect.
- Giữ bí mật và field nội bộ khỏi model, HTML hidden input, JavaScript và server-rendered markup.
- Với biểu mẫu, validation không thay thế CSRF và authorization; áp dụng cơ chế bảo vệ tương ứng.
- Trong production, chọn cache template hợp lý và gắn phiên bản asset; không vô hiệu hóa cache toàn hệ thống chỉ để thuận tiện debug.
- `forward:` không phải cách vượt authorization. Cấu hình security filter có thể áp dụng khác nhau theo dispatcher type, cần kiểm chứng với hệ thống thật.

## 12. Câu hỏi tự kiểm tra

1. Controller `@Controller` return `"books/detail"` nghĩa là gì? **Tên view logic**, chưa phải HTML body hay URL browser.
2. Ai quyết định controller nào xử lý `GET /books/7`? **HandlerMapping**, không phải ViewResolver.
3. `ViewResolver` và `View` khác nhau ra sao? **Resolver chọn View; View render model vào response**.
4. Hai resolver có order `0` và `100` thì ai chạy trước? **Order 0**.
5. Vì sao JSP `InternalResourceViewResolver` đứng cuối? **Không thể xác minh JSP tồn tại trước khi Servlet dispatch và dễ chiếm mọi tên**.
6. `BeanNameViewResolver` có render service bean cùng tên không? **Không**, cần bean kiểu `View`.
7. Thymeleaf mặc định trong Boot tìm `"home"` ở đâu? **`classpath:/templates/home.html`**, nếu giữ prefix/suffix mặc định.
8. Khi method gắn `@ResponseBody` return `"home"`, Thymeleaf render không? **Không**, chữ home được viết trực tiếp vào body.
9. `ContentNegotiatingViewResolver` có tự biến object Java thành JSON? **Không**; nó chọn View khác theo media type. JSON REST thường dùng message converter.
10. `redirect:/orders/7` có tương đương template `orders/7.html` không? **Không**, redirect tới URL mới.
11. Form invalid nên trả lại template hay redirect ngay? **Thường trả lại template cùng request** để giữ BindingResult.
12. Đặt `spring.thymeleaf.template-resolver-order` có đổi thứ tự giữa Thymeleaf và JSP ViewResolver không? **Không**; đó là một mức thứ tự nội bộ của template engine.
13. Nếu view name đúng mà HTML lỗi, luôn là 404? **Không**, template render exception có thể gây HTTP 500.
14. Khi test chỉ thấy `view().name("home")` đúng thì template có chắc render được không? **Chưa chắc**; thêm assert HTML thật.
15. Vì sao tránh `th:utext` cho tên người dùng? **Vì có thể đưa markup không tin cậy vào HTML và gây XSS**.

## 13. Tài liệu chính thức

- [Spring Framework 6.2 — View Resolution](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/viewresolver.html): contract của `ViewResolver`/`View`, chaining, JSP-last, redirect/forward và content negotiation.
- [Spring Framework 6.2 — MVC View Resolvers](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/view-resolvers.html): API cấu hình resolver qua `WebMvcConfigurer`.
- [Spring Framework 6.2 — JSP and JSTL](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc-view/mvc-jsp.html): JSP resolver và `JstlView`.
- [Spring Boot 3 — Spring MVC How-to](https://docs.spring.io/spring-boot/3.3/how-to/spring-mvc.html): resolver beans mặc định và tích hợp template engines.
- [Spring Boot 3.3 — Application Properties](https://docs.spring.io/spring-boot/3.3/appendix/application-properties/index.html): `spring.thymeleaf.prefix`, suffix, cache, view-names và các properties khác.
- [Thymeleaf + Spring Tutorial](https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html): Spring MVC/Thymeleaf, expressions, forms và validation.

**Ghi nhớ:** `@Controller` trả tên view logic; MVC lần lượt hỏi các `ViewResolver` theo mức ưu tiên, tìm `View` phù hợp, rồi View render model bằng template engine hoặc Servlet/JSP. `@RestController` và `@ResponseBody` đi qua message converter. Chọn đúng pipeline trước khi điều chỉnh resolver và prefix/suffix.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-forms-003">Thymeleaf, FreeMarker, JSP: vai trò và điều kiện sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Thymeleaf, FreeMarker, JSP: vai trò và điều kiện sử dụng trong Spring MVC

> **Phạm vi:** Spring Web MVC trên Servlet stack, Java 17+ và Spring Boot 3.x / Spring Framework 6.x. Chủ đề giải thích vai trò của ba công nghệ view phía server, cách ánh xạ controller → Model → ViewResolver → template, điều kiện dùng từng công nghệ, đường dẫn và dependency, form binding, HTML escaping, giới hạn JSP khi triển khai, cùng ví dụ Java chạy được. Những snippet độc lập được đánh dấu rõ ràng.

## 1. View nằm ở đâu trong Spring MVC?

Khi xây dựng trang web render từ server, controller nhận HTTP request, chuẩn bị dữ liệu trong **Model** rồi trả về **logical view name**. `ViewResolver` tìm View phù hợp; View engine ghép model và template để tạo HTML.

```text
GET /hello?name=Lan
        |
DispatcherServlet
        |
@Controller + Model
        | model.addAttribute("name", "Lan")
        | return "hello"
        v
ViewResolver
        | Thymeleaf: templates/hello.html
        | FreeMarker: templates/hello.ftlh
        | JSP: /WEB-INF/jsp/hello.jsp
        v
Rendered HTML --> Browser
```

Controller minh họa:

```java
@Controller
class GreetingController {
    @GetMapping("/hello")
    String hello(
            @RequestParam(defaultValue = "Guest") String name,
            Model model) {
        model.addAttribute("name", name);
        return "hello";
    }
}
```

Các import ở ví dụ: `org.springframework.stereotype.Controller`, `org.springframework.web.bind.annotation.GetMapping`, `RequestParam`, `org.springframework.ui.Model`.

**Chú ý:** `return "hello"` trong `@Controller` thường biểu thị **tên view**. Nếu class là `@RestController` (hoặc method có `@ResponseBody`), chuỗi `"hello"` thường chính là **HTTP response body**, được xử lý bởi `HttpMessageConverter`. Khi viết REST API trả JSON/DTO, bạn không nhất thiết phải cài bất kỳ template engine nào.

## 2. Bản đồ lựa chọn nhanh

| Tiêu chí | Thymeleaf | FreeMarker | JSP |
| --- | --- | --- | --- |
| Bản chất | Java HTML template engine, nhấn mạnh natural templates | Java template engine cho HTML/text, có directive và macro | Jakarta Server Pages do servlet container biên dịch thành servlet |
| Cú pháp | `th:text`, `th:each`, `th:object`, `th:field` | `${...}`, `<#if>`, `<#list>`, `<#macro>` | JSP EL `${...}`, JSTL `<c:...>`, Spring taglibs |
| Starter/engine | `spring-boot-starter-thymeleaf` | `spring-boot-starter-freemarker` | JSP compiler + Jakarta JSTL và container |
| Template mặc định trong Boot 3.5 | `classpath:/templates/*.html` | `classpath:/templates/*.ftlh` | Cấu hình view resolver, thường `/WEB-INF/jsp/*.jsp` |
| Thế mạnh | HTML đọc tự nhiên, Spring MVC forms và errors | Template macro, cấu trúc trình bày và output text/HTML | Duy trì ứng dụng Servlet/JSP/JSTL có sẵn |
| Lưu ý triển khai | Template phải hiện diện trên classpath | Chọn đúng HTML output format và escaping | Executable JAR không hỗ trợ JSP; WAR/container yêu cầu riêng |

Ba engine đều làm công việc **render view**, không thay thế controller, business service, database, validation hoặc security. Với một ứng dụng mới đóng gói executable JAR, Thymeleaf hoặc FreeMarker thường dễ triển khai hơn JSP vì JSP có ràng buộc runtime/packaging.

## 3. Thymeleaf: natural HTML và tích hợp Spring form

### 3.1. Khi nào dùng?

Thymeleaf phù hợp khi giao diện chủ yếu HTML phía server, có biểu mẫu Spring MVC, lỗi validation, danh sách hoặc các fragment dùng lại. HTML template vẫn có cấu trúc hợp lệ khi mở trực tiếp để xem trước; thuộc tính `th:*` được engine xử lý khi server render.

Với Spring Boot 3.x, khai báo:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

Đây là fragment của `pom.xml` dùng Spring Boot parent/BOM; với ứng dụng servlet thêm `spring-boot-starter-web`. Các giá trị mặc định của Spring Boot 3.5:

```properties
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
spring.thymeleaf.mode=HTML
spring.thymeleaf.encoding=UTF-8
```

Không cần viết những dòng này nếu dùng đúng mặc định. `return "hello"` sẽ được resolver ánh xạ tới `src/main/resources/templates/hello.html`.

### 3.2. Ví dụ HTML

```html
<!doctype html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Xin chào</title>
</head>
<body>
  <h1 th:text="'Xin chào, ' + ${name}">Xin chào, khách</h1>
  <a th:href="@{/hello(name='Mai')}">Chào Mai</a>
</body>
</html>
```

- `${name}` lấy thuộc tính `name` từ model.
- `th:text` ghi nội dung **HTML-escaped** vào phần tử.
- `@{...}` là URL expression, tạo URL đúng hơn so với hardcode trong trường hợp ứng dụng có context path.
- Text HTML gốc `Xin chào, khách` là bản preview; khi render, engine thay nội dung bằng giá trị model.

Nếu client truyền `name=<script>alert(1)</script>`, `th:text` render nó như ký tự text đã escape, chứ không phải thẻ script. Tránh dùng `th:utext` với HTML không đáng tin cậy vì nó có thể đưa markup do người dùng kiểm soát vào DOM.

### 3.3. Lặp, điều kiện, fragments

```html
<ul>
  <li th:each="book : ${books}"
      th:text="${book}">Sách mẫu</li>
</ul>
<p th:if="${books.empty}">Chưa có sách</p>
```

Controller cần đặt model attribute `books` tương ứng. Để tái sử dụng đoạn giao diện:

```html
<!-- templates/fragments/common.html -->
<footer th:fragment="footer">Ví dụ Spring MVC</footer>
```

```html
<!-- trong template khác -->
<div th:replace="~{fragments/common :: footer}"></div>
```

### 3.4. Spring form binding

```html
<form th:action="@{/profiles}" th:object="${profileForm}" method="post">
  <label>Tên
    <input type="text" th:field="*{displayName}">
  </label>
  <p th:if="${#fields.hasErrors('displayName')}"
     th:errors="*{displayName}">Lỗi tên</p>
  <button type="submit">Lưu</button>
</form>
```

Trong controller, GET đặt `profileForm` vào model, còn POST khai báo `@Valid @ModelAttribute("profileForm") ProfileForm form` với `BindingResult` đứng **ngay sau** form object. Các helper `th:field`, `th:errors` dựa trên binding/validation infrastructure của Spring MVC.

Nếu dùng Spring Security với CSRF enabled, POST form cần CSRF token. Thymeleaf có tích hợp giúp sinh token trong những điều kiện phù hợp, nhưng luôn kiểm tra HTML thực sự render và security config. Không coi UI field hidden/disabled là bảo mật: client vẫn có thể tạo request tự do.

## 4. FreeMarker: directive, macro và template đa dụng

### 4.1. Khi nào dùng?

FreeMarker phù hợp khi hệ thống có nhiều template/macro, nhu cầu lặp/điều kiện phức tạp ở tầng trình bày, hoặc đã sử dụng FreeMarker cho HTML và các loại văn bản khác. Nó là template engine, không phải servlet container/JSP.

Dependency với Spring Boot 3.x:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-freemarker</artifactId>
</dependency>
```

Spring Boot 3.5 mặc định:

```properties
spring.freemarker.template-loader-path=classpath:/templates/
spring.freemarker.suffix=.ftlh
spring.freemarker.charset=UTF-8
spring.freemarker.content-type=text/html
```

Đặc biệt, **suffix mặc định là `.ftlh`** trong Boot 3.5, không phải `.ftl` như nhiều tutorial cũ. `return "hello"` trỏ tới `src/main/resources/templates/hello.ftlh`.

### 4.2. HTML template

```html
<!doctype html>
<html lang="vi">
<head><meta charset="UTF-8"><title>Xin chào</title></head>
<body>
  <h1>Xin chào, ${name}!</h1>
  <#if books?has_content>
    <ul>
      <#list books as book>
        <li>${book}</li>
      </#list>
    </ul>
  <#else>
    <p>Chưa có sách</p>
  </#if>
</body>
</html>
```

FreeMarker dùng:
- `${name}` để chèn model value;
- `<#if>` / `<#else>` cho điều kiện;
- `<#list books as book>` cho vòng lặp;
- `${name!'Guest'}` đặt default value khi `name` thiếu.

Với template `.ftlh` và HTML output format chuẩn, FreeMarker auto-escape văn bản trong HTML. Không tắt escaping hoặc dùng `?no_esc` cho input không tin cậy. Nếu dùng `.ftl` qua cấu hình legacy, hãy xác minh HTML output format và escaping; đổi filename chưa đủ để bảo đảm XSS safety cho mọi template.

### 4.3. Macro, form và điều kiện thực tế

```ftl
<#macro label value>
  <span class="label">${value}</span>
</#macro>

<@label value="Spring MVC" />
```

Đoạn macro có thể lưu trong template tương ứng rồi import:

```ftl
<#import "macros.ftlh" as ui>
<@ui.label value="Book" />
```

FreeMarker không dùng cú pháp `th:object/th:field`. Form thông thường có HTML `name="displayName"` và model values:

```html
<form action="/profiles" method="post">
  <input type="text" name="displayName" value="${displayName!}">
  <button type="submit">Lưu</button>
</form>
```

Đây chỉ là fragment giao diện; với context path không rỗng, CSRF, validation errors và bảo vệ dữ liệu, cần Spring/FreeMarker macro integration hoặc thêm model/URL handling tương ứng. Đừng copy đoạn trên thành form production chưa xử lý CSRF. Các macro của Spring hỗ trợ binding và thông báo lỗi khi cấu hình/import đúng.

## 5. JSP: view truyền thống thuộc Servlet/Jakarta ecosystem

### 5.1. JSP được render thế nào?

JSP (Jakarta Server Pages) được servlet container dịch thành servlet và thực thi. Trong Spring MVC, controller vẫn trả logical view name; `InternalResourceViewResolver` thường forward tới file JSP. JSP có Expression Language (EL), JSTL tag libraries và Spring JSP form tags.

Mẫu cấu trúc project:

```text
src/main/
├─ java/com/example/demo/DemoApplication.java
└─ webapp/
   └─ WEB-INF/
      └─ jsp/
         └─ hello.jsp
```

Cấu hình view resolver thông thường trong Spring Boot:

```properties
spring.mvc.view.prefix=/WEB-INF/jsp/
spring.mvc.view.suffix=.jsp
```

Vậy `return "hello"` thường forward nội bộ tới `/WEB-INF/jsp/hello.jsp`. Đặt JSP dưới `WEB-INF` giúp ngăn client truy cập trực tiếp JSP theo đường dẫn thông thường; yêu cầu phải qua controller/mapping. Với JSP, `InternalResourceViewResolver` thường cần nằm **cuối chuỗi ViewResolver** vì nó có thể nhận diện tên view rất rộng và ngăn resolver trước/sau hoạt động đúng.

### 5.2. Giới hạn quan trọng khi chạy Spring Boot

Theo hướng dẫn Spring Boot về embedded servlet containers:

- **Executable JAR không hỗ trợ JSP.**
- Với **Tomcat và Jetty**, JSP có thể hoạt động bằng **WAR packaging**; executable WAR có thể chạy với `java -jar application.war` và WAR có thể deploy vào container ngoài tương thích.
- **Undertow không hỗ trợ JSP.**
- Custom `error.jsp` không mặc định thay đổi error view của Spring Boot; cần error page setup riêng.
- Đường dẫn `src/main/webapp`, build plugin, JSP engine và version Jakarta phải phù hợp. Không đặt JSP ở `classpath:/templates` rồi kỳ vọng boot JAR render như Thymeleaf.

Ví dụ dependency WAR/Tomcat/JSP cho **Spring Boot 3.x**:

```xml
<packaging>war</packaging>

<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.apache.tomcat.embed</groupId>
    <artifactId>tomcat-embed-jasper</artifactId>
  </dependency>
  <dependency>
    <groupId>jakarta.servlet.jsp.jstl</groupId>
    <artifactId>jakarta.servlet.jsp.jstl-api</artifactId>
  </dependency>
  <dependency>
    <groupId>org.glassfish.web</groupId>
    <artifactId>jakarta.servlet.jsp.jstl</artifactId>
  </dependency>
</dependencies>
```

Đây là **fragment POM**, không phải `pom.xml` độc lập. Spring Boot BOM/parent cần quản lý các version tương thích. Khi deploy WAR vào container ngoài, thường cấu hình embedded container với scope `provided` theo hướng dẫn Boot; khi chạy executable WAR, cần giữ classpath/package phù hợp. Không dùng dependency `javax.servlet`/JSTL cũ với Spring Boot 3.x (Jakarta namespaces).

Lớp ứng dụng hỗ trợ external WAR deployment:

```java
@SpringBootApplication
public class DemoApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(
            SpringApplicationBuilder application) {
        return application.sources(DemoApplication.class);
    }
}
```

Đoạn trên yêu cầu các import Spring Boot tương ứng; đây là phần cấu hình WAR minh họa, **không phải chương trình đầy đủ**.

### 5.3. JSP/JSTL template

```jsp
<%@ page contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="vi">
<head><meta charset="UTF-8"><title>Xin chào JSP</title></head>
<body>
  <h1>Xin chào, <c:out value="${name}" />!</h1>
  <ul>
    <c:forEach items="${books}" var="book">
      <li><c:out value="${book}" /></li>
    </c:forEach>
  </ul>
</body>
</html>
```

- JSP EL `${name}` truy cập model value được expose cho view.
- JSTL `<c:out>` escape ký tự XML/HTML trong vùng text.
- `<c:forEach>` lặp collection trong model.
- URI `jakarta.tags.core` phù hợp Jakarta JSTL hiện đại; taglib `http://java.sun.com/jsp/jstl/core` hay gặp trong ứng dụng Java EE cũ, cần đánh giá/di chuyển khi nâng cấp.
- Spring JSP form tag library có thể hỗ trợ binding/form nhưng cần cấu hình JSTL/Spring tags tương thích, không đồng nhất với Thymeleaf syntax.

### 5.4. Khi nào nên duy trì JSP?

Phù hợp nếu hệ thống hiện có hàng trăm trang JSP, chuẩn deployment WAR và container tương thích, hoặc sử dụng JSTL/Spring JSP taglibs mà chưa có lý do chính đáng để chuyển. Nếu cần một Spring Boot service mới đóng gói executable JAR, Thymeleaf hoặc FreeMarker thường ít hạn chế hơn. Không nên chuyển JSP sang engine khác chỉ bằng đổi extension file: phải chuyển cả cú pháp template, taglibs và form handling.

## 6. ViewResolver, model, static assets và return values

Một controller:

```java
@GetMapping("/catalog")
String catalog(Model model) {
    model.addAttribute("title", "Sách lập trình");
    return "catalog/list";
}
```

Cùng `"catalog/list"` có thể resolve thành:

| Engine | File theo cấu hình phổ biến |
| --- | --- |
| Thymeleaf | `classpath:/templates/catalog/list.html` |
| FreeMarker | `classpath:/templates/catalog/list.ftlh` |
| JSP | `/WEB-INF/jsp/catalog/list.jsp` |

**Phân biệt ba loại output:**

1. **Template HTML:** logical view name được chuyển thành file view và render với Model.
2. **Static asset:** CSS, JavaScript, hình ảnh đặt dưới `src/main/resources/static/` (hoặc vị trí static resources đã cấu hình), phục vụ qua resource handler, không thông qua template expression.
3. **REST response:** body JSON/text thông qua `@RestController`/`@ResponseBody` và `HttpMessageConverter`, không cần ViewResolver.

View resolver order đặc biệt quan trọng nếu đăng ký nhiều engine. Với cùng view name `"hello"` và cả Thymeleaf + FreeMarker starter, có thể có resolver cạnh tranh tùy điều kiện resource và thứ tự. Cách đơn giản là **chọn một template engine chính**; nếu bắt buộc dùng nhiều engine, cấu hình `view-names` pattern và resolver order rõ ràng, test cả trường hợp view tồn tại/không tồn tại.

### 6.1. Redirect và forward

```java
@GetMapping("/old")
String old() {
    return "redirect:/new";
}
```

`"redirect:/new"` gây HTTP redirect; browser gửi một request tiếp theo. `"forward:/internal"` thực hiện forward nội bộ theo quy tắc MVC/Servlet. `return "hello"` thuần là logical view name, không yêu cầu browser gửi request mới. Sau POST thành công, **Post/Redirect/Get** giảm việc form bị submit lại khi refresh.

## 7. Ví dụ chạy được — Spring Boot 3.x + Thymeleaf

Để tránh va chạm `ViewResolver`, ví dụ hoàn chỉnh chọn một engine duy nhất: **Thymeleaf**. Tạo project từ [Spring Initializr](https://start.spring.io/), Java 17+, Maven, dependencies **Spring Web**, **Thymeleaf**, **Validation**. Các thư viện tương ứng:

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>
</dependencies>
```

Đây là fragment của POM. Khi tạo qua Initializr, Maven parent, plugin, versions và project metadata đã được chuẩn bị.

### 7.1. Cấu trúc file

```text
src/main/
├─ java/com/example/demo/DemoApplication.java
└─ resources/
   └─ templates/
      ├─ books.html
      └─ book-form.html
```

### 7.2. Java controller và form bean đầy đủ

Đặt trong `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

class BookForm {
    @NotBlank
    @Size(max = 80)
    private String title = "";

    public BookForm() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}

@Controller
class PageController {
    private final List<String> books =
            new CopyOnWriteArrayList<>(List.of("Spring MVC"));

    @GetMapping("/books")
    public String list(Model model) {
        model.addAttribute("books", List.copyOf(books));
        return "books";
    }

    @GetMapping("/books/new")
    public String form(Model model) {
        model.addAttribute("bookForm", new BookForm());
        return "book-form";
    }

    @PostMapping("/books")
    public String create(
            @Valid @ModelAttribute("bookForm") BookForm form,
            BindingResult bindingResult,
            RedirectAttributes redirects) {
        if (bindingResult.hasErrors()) {
            return "book-form";
        }

        books.add(form.getTitle());
        redirects.addFlashAttribute("notice", "Đã thêm sách");
        return "redirect:/books";
    }
}
```

- `PageController` là `@Controller` để trả **view names**.
- GET `/books` đưa danh sách `books` vào Model; GET `/books/new` đưa bean `bookForm` vào Model.
- POST `/books` nhận form theo `@ModelAttribute`; `@Valid` chạy Bean Validation và `BindingResult` phải đứng ngay sau form parameter.
- Nếu title rỗng hoặc vượt 80 ký tự, method trả `"book-form"` để render lại lỗi.
- Khi thành công, thêm sách vào list, set flash message rồi redirect sang GET `/books`. Đây là PRG, không render HTML ngay từ POST.
- Demo dùng in-memory list, không có database/security. Code production cần request DTO chỉ chứa field hợp đồng, CSRF protection (nếu có session authentication), authorization và persistence.

### 7.3. Template hiển thị danh sách

`src/main/resources/templates/books.html`:

```html
<!doctype html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Danh sách sách</title>
</head>
<body>
  <h1>Sách đã thêm</h1>
  <p th:if="${notice}" th:text="${notice}"></p>
  <ul>
    <li th:each="book : ${books}"
        th:text="${book}">Sách mẫu</li>
  </ul>
  <a th:href="@{/books/new}">Thêm sách</a>
</body>
</html>
```

Với dữ liệu ban đầu, trang hiển thị mục `Spring MVC`. Sau POST thêm `Effective Java`, request GET tiếp theo hiển thị cả hai mục. Dữ liệu title chứa dấu `<`/`>` được escape bởi `th:text`.

### 7.4. Template biểu mẫu

`src/main/resources/templates/book-form.html`:

```html
<!doctype html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Thêm sách</title>
</head>
<body>
  <h1>Thêm sách mới</h1>
  <form th:action="@{/books}" th:object="${bookForm}" method="post">
    <label for="title">Tên sách</label>
    <input id="title" type="text" th:field="*{title}" maxlength="80">
    <p th:if="${#fields.hasErrors('title')}"
       th:errors="*{title}">Tên sách không hợp lệ</p>
    <button type="submit">Lưu</button>
  </form>
  <a th:href="@{/books}">Quay lại</a>
</body>
</html>
```

Client-side `maxlength="80"` chỉ hỗ trợ giao diện; `@Size(max = 80)` phía server vẫn cần. Việc validation và binding hoạt động không đồng nghĩa đã có CSRF/authorization; ví dụ cố ý không cài Spring Security để tập trung vào view.

### 7.5. Chạy và kiểm tra kết quả

Từ root project Maven:

```bash
# macOS/Linux
./mvnw spring-boot:run

# Windows PowerShell / Command Prompt
mvnw.cmd spring-boot:run
```

Mở `http://localhost:8080/books` và `http://localhost:8080/books/new`. Với port mặc định và không có context path tùy chỉnh, cả hai GET trả HTML trang tương ứng (status 200).

Request POST hợp lệ:

```bash
curl -i -X POST 'http://localhost:8080/books' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'title=Effective Java'
```

Dự kiến status **302 Found** và header `Location` trỏ đến `/books` (có thể biểu diễn URL theo context path/config). Browser theo redirect, hiển thị list có `Effective Java`. Nếu dùng cURL `-L`, cURL đi theo redirect; đừng giả định response đầu tiên là HTML list.

Request POST title rỗng:

```bash
curl -i -X POST 'http://localhost:8080/books' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'title='
```

Controller render lại `book-form.html` có lỗi validation hiển thị qua `th:errors`. Vì đây là mô hình form truyền thống và method chỉ return view name, status phản hồi trong ví dụ thông thường **200 OK** dù form chưa hợp lệ; API JSON có thể chọn error status khác.

Lệnh trên sử dụng Bash `\` nối dòng. Trong PowerShell, dùng `curl.exe` và đặt toàn bộ câu lệnh trên một dòng.

## 8. Đổi cùng controller sang FreeMarker

Muốn chuyển **engine** mà giữ logic controller, thay `spring-boot-starter-thymeleaf` bằng `spring-boot-starter-freemarker` và đổi templates sang `*.ftlh` với cú pháp FreeMarker. Một file danh sách tối thiểu `src/main/resources/templates/books.ftlh`:

```html
<!doctype html>
<html lang="vi">
<head><meta charset="UTF-8"><title>Danh sách sách</title></head>
<body>
  <h1>Sách đã thêm</h1>
  <#if notice??><p>${notice}</p></#if>
  <ul>
    <#list books as book>
      <li>${book}</li>
    </#list>
  </ul>
  <a href="/books/new">Thêm sách</a>
</body>
</html>
```

Nội dung trên thể hiện data model và list rendering nhưng **chưa phải bản port hoàn chỉnh của form**; cần thêm `book-form.ftlh`, input name `title`, giá trị cũ và validation error model/macro Spring. Tránh giữ nguyên th:object/th:field trong FreeMarker template. Link hardcode `/books/new` hợp lệ ở root context của demo; dự án có context path nên dùng URL helper theo engine/cấu hình.

Khi chỉ có FreeMarker, logical view name `"books"` tìm `books.ftlh` với default Boot 3.5. Nếu dùng file `.ftl`, hãy đặt `spring.freemarker.suffix=.ftl` **và** kiểm tra HTML escaping/output format.

## 9. Đổi sang JSP và triển khai WAR

Với JSP, giữ controller `@Controller` trả `"books"` nhưng thay hướng đóng gói: WAR + JSP engine/JSTL + `/WEB-INF/jsp/`. Đặt:

```properties
spring.mvc.view.prefix=/WEB-INF/jsp/
spring.mvc.view.suffix=.jsp
```

Ví dụ `src/main/webapp/WEB-INF/jsp/books.jsp`:

```jsp
<%@ page contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="vi">
<head><meta charset="UTF-8"><title>Danh sách sách</title></head>
<body>
  <h1>Sách đã thêm</h1>
  <c:if test="${not empty notice}">
    <p><c:out value="${notice}" /></p>
  </c:if>
  <ul>
    <c:forEach items="${books}" var="book">
      <li><c:out value="${book}" /></li>
    </c:forEach>
  </ul>
  <a href="<c:url value='/books/new' />">Thêm sách</a>
</body>
</html>
```

Để port trọn ứng dụng, còn phải xây `book-form.jsp` với JSP/Spring form tags, validation display, CSRF token và URL helpers. Build/test cần chạy trong Tomcat hoặc Jetty JSP-capable với WAR và version Jakarta đúng. Đừng chạy executable JAR và kỳ vọng `.jsp` sẽ tự hoạt động.

## 10. Bảo mật và ranh giới trách nhiệm của template

| Rủi ro | Cách phòng |
| --- | --- |
| XSS từ input user | Thymeleaf `th:text`, FreeMarker HTML auto-escape, JSP JSTL `c:out`; encode theo đúng context |
| Render template do user tự upload/sửa | Không chạy template không đáng tin cậy trong application context |
| Form POST bị CSRF | CSRF protection với token và xác minh HTML form đã render |
| Mass assignment | Request DTO/allowed fields, validation, explicit entity updates |
| Truy cập trái quyền | Authorization ở controller/service/security, không chỉ ẩn link trong HTML |
| Rò rỉ thuộc tính object | Chỉ đưa dữ liệu cần thiết vào Model, hạn chế model chứa thông tin nhạy cảm |
| URL/path injection | Tạo href/action qua engine URL helper, không nối input tùy ý vào đường dẫn |
| Escape sai ngữ cảnh | Không đưa giá trị chưa kiểm soát vào inline JS, HTML attributes, CSS hoặc raw HTML |
| Template cache | Tắt cache có mục đích trong dev, cân nhắc bật trong production |

**Quan trọng:** HTML escaping trong text context không thay thế CSP và các nguyên tắc an toàn ở URL/JavaScript context. Ví dụ đầu vào `<script>alert(1)</script>` nếu được đưa qua `th:text`, `${value}` trong FreeMarker HTML output format hoặc `<c:out>` trong JSP sẽ được hiển thị như text thay vì tạo script tag. Nhưng `th:utext`, `?no_esc`, raw JSP EL có thể làm mất lớp bảo vệ này.

## 11. Khi nào dùng engine nào trong dự án thật?

**Chọn Thymeleaf** khi bắt đầu một trang MVC mới, cần biểu mẫu tích hợp Spring, templates HTML người thiết kế có thể xem trước và triển khai executable JAR. Chi phí chính: engine cú pháp `th:*` và cách xử lý fragments/form errors.

**Chọn FreeMarker** khi đã có các macro/template phức tạp hoặc cần cùng hệ template cho nhiều loại nội dung; nó vẫn render HTML tốt khi output format và escaping chuẩn. Chi phí chính: cú pháp FreeMarker khác HTML tự nhiên và phải chủ động tích hợp form/validation behavior của MVC.

**Giữ JSP** khi hệ thống legacy có JSP/JSTL assets/tags lớn hoặc mục tiêu deployment là WAR trên servlet container có JSP support. Chi phí chính: điều kiện WAR/container, JSP compiler, JSTL Jakarta compatibility và bảo trì template. Không chọn JSP cho Spring Boot executable JAR.

**Không cần engine nào** nếu service cung cấp REST JSON cho React/Vue/mobile. Controller trả DTO bằng `@RestController` và response được serialize qua `HttpMessageConverter`; đó là một kiến trúc khác với server-rendered views.

## 12. Lỗi thường gặp và cách kiểm tra

| Triệu chứng | Điều cần xem | Sửa thế nào |
| --- | --- | --- |
| Trang in chữ `books` thay HTML | Method/class có `@ResponseBody`/`@RestController`? | Dùng `@Controller` cho view |
| Template không tìm thấy | Starter, prefix, suffix, file path | Đặt file đúng `templates/` và tên logical view |
| FreeMarker không đọc file `.ftl` | Boot 3.5 default `.ftlh` | Đổi filename/config và xác minh escaping |
| JSP không render trong executable JAR | JSP packaging không được hỗ trợ | Dùng WAR và container có JSP support |
| JSP taglib không tìm thấy | Thiếu JSTL hoặc taglib URI/version sai | Đồng bộ Jakarta JSTL API+implementation |
| `Circular view path` | View resolver forward trùng request path | Điều chỉnh view name/prefix/suffix |
| Có nhiều engine, trang dùng sai view | Các ViewResolver trùng tên/pattern | Chọn một engine hoặc phân tách view-names/order |
| CSS/JS trả 404 | Nhầm static resource với template | Đặt CSS/JS dưới `static/` và dùng URL đúng |
| Form gửi lỗi 403 | Có thể thiếu/không đúng CSRF token | Kiểm tra Spring Security filter và form HTML |
| Validation không hiện lỗi | Sai tên model attribute, thiếu starter, `BindingResult` sai vị trí | Kiểm tra `@Valid` và binding |
| Trang bị XSS | Raw/unescaped input trong HTML | Escape đúng context và không render template không tin cậy |
| Thêm JSP vẫn lỗi 500 | Container, WAR config, JSP compiler/classpath | Test build WAR và runtime JSP thực tế |

Khi debug, đi theo thứ tự: HTTP mapping → controller return value → ViewResolver → resource path/suffix → engine dependency → runtime packaging → model variables → escaping/security. Với JSP, xác minh **WAR và container** trước khi sửa controller. Với Thymeleaf/FreeMarker, xác minh **starter và classpath templates** trước khi chỉnh ViewResolver.

## 13. Câu hỏi tự kiểm tra

**1.** Method `@RestController` trả `"hello"` nhưng có `templates/hello.html`. Spring sẽ render template không?

**Đáp án:** Không, chuỗi được xử lý như response body. Muốn logical view name, dùng `@Controller` phù hợp.

**2.** Với Boot 3.5 mặc định, `return "list"` trỏ tới file gì trong Thymeleaf và FreeMarker?

**Đáp án:** Thymeleaf `classpath:/templates/list.html`, FreeMarker `classpath:/templates/list.ftlh` nếu các starter/resolver tương ứng được cấu hình.

**3.** Thay `spring-boot-starter-thymeleaf` bằng FreeMarker rồi giữ nguyên `th:field` trong HTML có hoạt động không?

**Đáp án:** Không. Phải chuyển sang FreeMarker template cú pháp thích hợp và xử lý form binding/validation.

**4.** Vì sao Spring Boot executable JAR là dấu hiệu không nên chọn JSP?

**Đáp án:** Theo tài liệu Spring Boot, JSP không được hỗ trợ trong executable JAR; dùng WAR với Tomcat/Jetty JSP-capable nếu cần JSP.

**5.** Đưa chuỗi từ user vào HTML bằng phương thức nào an toàn hơn?

**Đáp án:** Dùng `th:text`, FreeMarker HTML auto-escaping, hoặc `<c:out>` (cho text context), đồng thời encode đúng ngữ cảnh và kiểm soát input.

**6.** `return "redirect:/books"` khác `return "books"` như thế nào?

**Đáp án:** Redirect yêu cầu trình duyệt gửi GET mới; view name render template cho request hiện tại. PRG thường dùng redirect sau POST thành công.

## 14. Tài liệu chuẩn

- [Spring Framework — View Technologies](https://docs.spring.io/spring-framework/reference/web/webmvc-view.html): Spring MVC pluggable view technologies, ranh giới tin cậy template.
- [Spring Framework — View Resolvers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/view-resolvers.html): thứ tự và cấu hình ViewResolver.
- [Spring Framework — Thymeleaf](https://docs.spring.io/spring-framework/reference/web/webmvc-view/mvc-thymeleaf.html): nguyên lý Thymeleaf trong Spring MVC.
- [Spring Framework — FreeMarker](https://docs.spring.io/spring-framework/reference/web/webmvc-view/mvc-freemarker.html): tích hợp FreeMarker, macro và cấu hình.
- [Spring Framework — JSP and JSTL](https://docs.spring.io/spring-framework/reference/web/webmvc-view/mvc-jsp.html): JSP view resolver, JSTL, JSP tags.
- [Spring Boot 3.5 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html): giới hạn JSP cho WAR/JAR và servlet container.
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/): `spring.thymeleaf.*`, `spring.freemarker.*`, `spring.mvc.view.*`.
- [Thymeleaf documentation](https://www.thymeleaf.org/documentation.html): HTML templates, expressions, fragments.
- [Apache FreeMarker Manual](https://freemarker.apache.org/docs/): FreeMarker directives, macros và output escaping.

**Ghi nhớ:** cả ba công nghệ đều đảm nhiệm **View** trong Spring MVC nhưng khác cú pháp, dependency và điều kiện triển khai. Thymeleaf thuận lợi cho HTML/form với Spring, FreeMarker mạnh về template/macro, JSP phù hợp hệ Servlet legacy có WAR và JSP container. Khi triển khai, hãy kiểm tra đúng engine, file path/suffix, logical view names, HTML escaping, validation, CSRF và quyền truy cập thay vì chỉ nhìn vào HTML hiển thị.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-forms-004">Truyền dữ liệu từ controller tới view bằng @ModelAttribute</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền dữ liệu từ controller tới view bằng @ModelAttribute trong Spring MVC

## 1. Mục tiêu: controller chuẩn bị dữ liệu, view trình bày dữ liệu

Trong ứng dụng **Spring MVC render HTML phía server** (JSP, Thymeleaf và các template engine tương thích), controller chịu trách nhiệm chuẩn bị dữ liệu cho màn hình và trả về tên view hoặc `ModelAndView`. View sau đó đọc **model attributes** do controller cung cấp để hiển thị thông tin, điền giá trị mặc định của form và thông báo lỗi.

Ví dụ người dùng mở `GET /products/new`:

1. Controller tạo một `ProductForm` rỗng và đưa vào model với tên `productForm`.
2. Controller trả view name `"products/form"`.
3. Spring tìm view tương ứng, chẳng hạn `src/main/resources/templates/products/form.html` qua Thymeleaf.
4. Template truy cập `productForm.title` và `productForm.quantity`, render HTML và gửi trình duyệt.
5. Người dùng điền form, submit `POST /products`; Spring bind request parameters thành một `ProductForm` mới rồi kiểm tra validation.
6. Nếu có lỗi, controller trả lại view cùng model/form/errors để người dùng sửa. Nếu thành công, controller có thể redirect sang trang chi tiết.

**`@ModelAttribute` có hai cách sử dụng chính**:

- **Trên phương thức**: góp dữ liệu vào model **trước khi** request handler cùng controller thực thi; thường dùng các thông tin chung cho nhiều view.
- **Trên tham số phương thức**: lấy một object từ model hoặc tạo object khi chưa có, sau đó có thể bind dữ liệu HTTP vào object; object này đồng thời được đưa vào model cho view.

Thêm vào đó, controller có thể đưa dữ liệu vào model bằng `Model.addAttribute(...)`, `ModelMap` hoặc `ModelAndView`. Vì thế `@ModelAttribute` không phải con đường duy nhất để đưa dữ liệu sang view; giá trị của nó là kết hợp **model population** và **binding** theo vòng đời Spring MVC.

Phạm vi bài: **Spring MVC Servlet stack**, Java 17+, ví dụ Spring Boot 3.x, Thymeleaf và Jakarta Bean Validation. Spring WebFlux có annotation tương tự nhưng đường thực thi phản ứng khác. `@RestController` / `@ResponseBody` dùng để trả HTTP body trực tiếp (thường JSON), nên không xem việc trả một `String` từ REST controller như view name.

## 2. Model, ModelAndView, ViewResolver và template

Trong một request điển hình:

```text
Browser GET /products/new
          |
          v
DispatcherServlet -> HandlerMapping/HandlerAdapter
          |
          v
@Controller
  @ModelAttribute methods (chuẩn bị dữ liệu chung)
  @GetMapping handler (thêm productForm)
          |
          v
Model = {
  "catalogName": "Sổ sản phẩm",
  "productForm": ProductForm(...)
}
View name = "products/form"
          |
          v
ViewResolver (Thymeleaf/JSP tùy cấu hình)
          |
          v
Render HTML dùng model
          |
          v
HTTP 200 + text/html
```

- **Model** là cấu trúc chứa cặp *tên → giá trị* cho hoạt động render hiện tại. `org.springframework.ui.Model` là interface controller thường nhận qua tham số.
- **View name** là tên logic như `"products/form"`. View resolver và cấu hình engine quyết định nó tương ứng tài nguyên template nào.
- **View** dùng các model attributes để render HTML. Với Thymeleaf, cú pháp `${productForm}`, `${product.title}` hoặc `th:object` / `th:field` nhận dữ liệu model.
- **`ModelAndView`** gói cả model và view name trong một giá trị trả về khi cần.
- **`@Controller`** thường dùng cho HTML. `@RestController` tương đương `@Controller + @ResponseBody`, nghĩa là một `String` được viết vào HTTP response body, **không mặc nhiên là tên template**.

Model ở đây mang ngữ nghĩa **một lần render request**. Nó không tự duy trì dữ liệu giữa hai HTTP requests. Với redirect, cần `RedirectAttributes` / flash attributes; với workflow nhiều bước có thể cân nhắc `@SessionAttributes`. Những cơ chế này được phân biệt ở phần sau.

## 3. Ba cách chuyển dữ liệu sang view

### 3.1. Thêm vào `Model` ngay trong handler

```java
@Controller
class WelcomeController {

    @GetMapping("/welcome")
    String welcome(Model model) {
        model.addAttribute("message", "Chào mừng đến Spring MVC");
        model.addAttribute("visitors", 42);
        return "welcome";
    }
}
```

Mẫu trên là **class controller minh họa**, cần import `@Controller`, `@GetMapping` và `Model` cùng template `welcome`. Khi Thymeleaf render, template có thể dùng:

```html
<h1 th:text="${message}">Tiêu đề dự phòng</h1>
<p th:text="${visitors}">0</p>
```

`th:text` escape HTML theo mặc định khi hiển thị giá trị, an toàn hơn việc cố ý render raw HTML bằng `th:utext`. Tuy nhiên, escaping đầu ra không thay thế việc kiểm tra dữ liệu input hay phân quyền.

Tên attr phải khớp: `model.addAttribute("message", ...)` ứng với `${message}`. Nếu model đặt `"msg"` nhưng view đọc `"message"`, template sẽ không nhận được giá trị mong muốn.

### 3.2. `@ModelAttribute` ở cấp phương thức để chia sẻ dữ liệu

```java
@Controller
@RequestMapping("/products")
class ProductController {

    @ModelAttribute("catalogName")
    String catalogName() {
        return "Sổ sản phẩm";
    }

    @GetMapping("/new")
    String newForm(Model model) {
        model.addAttribute("productForm", new ProductForm());
        return "products/form";
    }
}
```

Method `catalogName()` được Spring MVC gọi để thêm thuộc tính `catalogName` vào model **trước handler `@GetMapping`** trong controller này. Bất kỳ template nào do những handler tương ứng render đều có thể đọc `${catalogName}`.

Khi nhiều controller cần dữ liệu chung, có thể khai báo `@ModelAttribute` method trong `@ControllerAdvice` với selector phù hợp. Không nên đặt các query database đắt tiền vào global method chạy cho mọi endpoint nếu nhiều endpoint không cần dữ liệu đó.

Một method cũng có thể thêm nhiều thuộc tính:

```java
@ModelAttribute
void populateFormOptions(Model model) {
    model.addAttribute("regions", List.of("north", "south"));
    model.addAttribute("pageTitle", "Create product");
}
```

Đây là **method fragment** trong controller/advice và cần import `java.util.List`. Method `void` nhận `Model`, không có một object return riêng để tự đặt tên.

Các method `@ModelAttribute` có thể dùng nhiều tham số được MVC hỗ trợ, nhưng không nhất thiết hỗ trợ tất cả tham số giống `@RequestMapping` (đặc biệt không dùng chúng làm thay thế handler đọc body). Chúng có thể gây side effect/performance nếu được gọi lặp lại trước nhiều handler; hãy giữ chúng phù hợp việc xây model.

### 3.3. Trả `ModelAndView` khi muốn đóng gói

```java
@GetMapping("/summary")
ModelAndView summary() {
    ModelAndView result = new ModelAndView("products/summary");
    result.addObject("count", 10);
    return result;
}
```

Đây là **method fragment**; cần import `org.springframework.web.servlet.ModelAndView` và template `products/summary`. Nó tương đương về ý định với việc thêm vào `Model` rồi trả tên view. Không bắt buộc phải dùng `ModelAndView` cho mọi handler; nhiều ứng dụng dùng `Model + String` gọn và dễ đọc hơn.

## 4. Phân biệt `@ModelAttribute` trên method và trên tham số

| Vị trí | Ví dụ | Điều gì xảy ra? | Dùng khi nào? |
| --- | --- | --- | --- |
| Method không phải request handler | `@ModelAttribute("catalogName") String name()` | Giá trị trả về được thêm vào model trước khi handler cùng phạm vi chạy | Tiêu đề, danh sách lựa chọn, dữ liệu chung |
| Method `void` | `@ModelAttribute void add(Model model)` | Method tự thêm một hay nhiều entries vào model | Nhiều dữ liệu phụ trợ cho form |
| Handler parameter | `save(@ModelAttribute("productForm") ProductForm form)` | Lấy/tạo model attribute, bind request values, sẵn cho view | Submit form HTML, data binding |
| Handler method | `@GetMapping @ModelAttribute("item") Item getItem()` | Return value của handler được diễn giải là model attribute, thay vì body/view name theo kiểu return | Muốn trả object trực tiếp vào model |
| Annotation trên `@RestController` | `@ModelAttribute` ở method có thể thêm model nhưng handler có `@ResponseBody` | HTTP response trực tiếp không tự render template | Thường không cần để truyền dữ liệu tới HTML view |

**Chú ý sự nhập nhằng của `String`:** Nếu một handler trong `@Controller` trả `"products/form"` không có `@ResponseBody` thì đó thường là **view name**; nhưng một method được đánh dấu `@ModelAttribute("label")` trả `String` nghĩa là **giá trị attribute** `label`, không phải tên view.

Spring suy ra tên model attribute từ kiểu khi không đặt tên tường minh. Nhưng khi form/template phụ thuộc tên cụ thể hoặc dùng `BindingResult`, đặt tên rõ ràng (ví dụ `"productForm"`) giúp tránh lệch tên khi refactor Java type và giúp test ổn định.

## 5. Mạch dữ liệu khi bind form vào `@ModelAttribute` tham số

Giả sử form HTML submit:

```http
POST /products HTTP/1.1
Content-Type: application/x-www-form-urlencoded

title=Notebook&quantity=3
```

Controller khai báo:

```java
@PostMapping("/products")
String create(@ModelAttribute("productForm") ProductForm form) {
    // form.getTitle() == "Notebook"
    // form.getQuantity() == 3
    return "products/result";
}
```

Đây là method fragment, cần có class `ProductForm`, controller và view `products/result`. Spring MVC thực hiện những bước cơ bản:

1. Tìm model attribute tên `productForm` đã có nếu tồn tại; tùy khai báo nó có thể đến từ `@ModelAttribute` method hoặc cơ chế model/session liên quan.
2. Nếu chưa tồn tại, Spring tạo object phù hợp, thường bằng constructor được hỗ trợ (mặc định hoặc primary constructor).
3. `WebDataBinder` dùng nguồn dữ liệu request để bind vào thuộc tính/constructor arguments. Servlet request parameters bao gồm query parameters và form-encoded data; đối với một số cấu hình, URI path variables/headers cũng có thể tham gia khi tên không bị parameter override.
4. `ConversionService`/property editors chuyển chuỗi thành kiểu Java, chẳng hạn `"3"` sang `Integer(3)`.
5. Nếu có `@Valid` hoặc `@Validated`, validator được kích hoạt; lỗi binding/validation được ghi nhận.
6. Object và metadata binding có thể được thêm vào model để view hiển thị lại giá trị người dùng vừa nhập.

**`@ModelAttribute` không tương đương `@RequestBody`**:

- `@ModelAttribute` phù hợp dữ liệu form/query/path/header theo cơ chế data binding.
- `@RequestBody` dùng `HttpMessageConverter` (ví dụ Jackson) đọc body JSON/XML theo content type.
- Gửi JSON `{"title":"Notebook"}` kèm `Content-Type: application/json` tới endpoint chỉ khai báo `@ModelAttribute ProductForm` **không khiến Jackson tự deserialize JSON thành form**.
- Với REST JSON API, thường chọn `@RequestBody` và trả `ResponseEntity`/DTO thay vì view name.

Khi method tham số là object phức tạp không được xử lý bằng resolver khác, Spring MVC có thể suy luận **implicit `@ModelAttribute`**. Tuy nhiên, nên khai báo tường minh để rõ contract và hỗ trợ phân tích AOT/native image.

## 6. Ví dụ có thể chạy: form tạo sản phẩm với Thymeleaf

Ứng dụng mẫu dùng **Spring Boot 3.x, Java 17+, Spring MVC Servlet + Thymeleaf + Jakarta Validation**. Bạn có thể thấy đủ chu trình:

```text
GET /products/new
    -> Model {"productForm", "catalogName"}
    -> render templates/products/form.html

POST /products
    -> @ModelAttribute("productForm") bind form data
    -> @Valid + BindingResult
       |
       +-- invalid -> render lại templates/products/form.html (HTTP 200)
       |
       +-- valid   -> lưu in-memory
                      addFlashAttribute("notice")
                      redirect:/products/{id} (HTTP 302)
                          |
                          v
                      GET /products/{id}
                      -> Model {"product", "catalogName", "notice"}
                      -> render templates/products/detail.html
```

HTTP 302 ở luồng Post-Redirect-Get là hành vi mặc định thông dụng của view redirect Spring MVC; có thể cấu hình redirect status riêng (ví dụ 303) khi hợp đồng yêu cầu, nhưng mẫu không tự đổi status.

### 6.1. Dependencies

Trong một dự án Spring Boot 3.x đã có plugin, repositories, Java toolchain và dependency management, thêm đoạn `build.gradle`:

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

Đây là **fragment cấu hình Gradle**, không phải toàn bộ build file. Starter Thymeleaf cung cấp view resolver/template integration; starter validation có provider Jakarta Bean Validation. Nếu dùng Maven, khai báo các starter tương ứng trong `pom.xml` do Spring Boot BOM quản lý.

### 6.2. Mã Java

Tạo **`src/main/java/demo/modelview/ModelViewApplication.java`**:

```java
package demo.modelview;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@SpringBootApplication
public class ModelViewApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModelViewApplication.class, args);
    }
}

// Form DTO: chỉ cho bind các field mà người dùng được nhập.
class ProductForm {
    @NotBlank
    private String title;

    @NotNull
    @Min(1)
    private Integer quantity;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}

// View object chỉ chứa dữ liệu được phép hiển thị.
class Product {
    private final long id;
    private final String title;
    private final int quantity;

    Product(long id, String title, int quantity) {
        this.id = id;
        this.title = title;
        this.quantity = quantity;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getQuantity() {
        return quantity;
    }
}

@Controller
@RequestMapping("/products")
class ProductController {

    private final AtomicLong sequence = new AtomicLong(1000);
    private final ConcurrentMap<Long, Product> products =
            new ConcurrentHashMap<>();

    @ModelAttribute("catalogName")
    String catalogName() {
        return "Sổ sản phẩm";
    }

    @GetMapping("/new")
    String newForm(Model model) {
        model.addAttribute("productForm", new ProductForm());
        return "products/form";
    }

    @PostMapping
    String create(
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult errors,
            RedirectAttributes redirects) {

        if (errors.hasErrors()) {
            return "products/form";
        }

        long id = sequence.incrementAndGet();
        Product saved = new Product(
                id, form.getTitle(), form.getQuantity());
        products.put(id, saved);

        redirects.addFlashAttribute(
                "notice", "Đã tạo sản phẩm thành công");
        return "redirect:/products/" + id;
    }

    @GetMapping("/{id}")
    String detail(@PathVariable long id, Model model) {
        Product product = products.get(id);
        if (product == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Product not found");
        }

        model.addAttribute("product", product);
        return "products/detail";
    }
}
```

Đây là **một file Java hợp lệ** trong project Boot 3, chỉ `ModelViewApplication` là public; các class còn lại là package-private. Spring Boot scan package `demo.modelview` để phát hiện `@Controller`.

Các điểm cốt lõi:

- `catalogName()` là method có `@ModelAttribute`, luôn thêm tiêu đề vào model trước các handler trong `ProductController`.
- `newForm(Model model)` đặt một `ProductForm` rỗng trong model, key tường minh là `"productForm"`.
- Khi submit, `@ModelAttribute("productForm") ProductForm form` bind form parameters; `@Valid` kiểm tra `@NotBlank`, `@NotNull` và `@Min(1)`.
- `BindingResult errors` phải đứng **ngay sau** `form`. Nếu không, validation errors thường thành exception trước khi controller có cơ hội trả lại form.
- Nếu lỗi, trả view `products/form` trong **cùng request**. Model giữ form và `BindingResult` để Thymeleaf đọc lỗi.
- Nếu thành công, controller lưu dữ liệu demo, rồi thêm **flash attribute** `notice` và redirect sang GET chi tiết. Dữ liệu render của response sau redirect được chuẩn bị từ **request GET mới**.
- Dùng `ProductForm` riêng với các field public bind được; không bind trực tiếp entity hoặc bean chứa `role`, `priceOverride`, `isAdmin` hay field nhạy cảm do client không được sửa.
- `ConcurrentHashMap` chỉ dùng để minh họa: không có database, transaction, lưu lâu dài, shared state giữa nhiều instance. Không đưa lựa chọn in-memory này vào production mà không thay kiến trúc persistence.

### 6.3. Thymeleaf form template

Tạo **`src/main/resources/templates/products/form.html`**:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Tạo sản phẩm</title>
</head>
<body>
  <h1 th:text="${catalogName}">Tên danh mục</h1>
  <h2>Tạo sản phẩm</h2>

  <form th:action="@{/products}" th:object="${productForm}" method="post">
    <div>
      <label for="title">Tên sản phẩm</label>
      <input id="title" type="text" th:field="*{title}">
      <small th:if="${#fields.hasErrors('title')}"
             th:errors="*{title}">Lỗi tên</small>
    </div>

    <div>
      <label for="quantity">Số lượng</label>
      <input id="quantity" type="number" th:field="*{quantity}">
      <small th:if="${#fields.hasErrors('quantity')}"
             th:errors="*{quantity}">Lỗi số lượng</small>
    </div>

    <button type="submit">Tạo</button>
  </form>
</body>
</html>
```

Giải thích Thymeleaf:

- `th:object="${productForm}"` chọn object form trong model làm ngữ cảnh cho biểu thức `*{...}`.
- `th:field="*{title}"` đọc giá trị thuộc tính và tạo các thuộc tính HTML name/id/value phù hợp cho form binding.
- `#fields.hasErrors('title')`/`th:errors` dựa vào binding/validation errors của attribute `productForm`. Spring cũng đưa metadata lỗi vào model với key dạng `org.springframework.validation.BindingResult.productForm`.
- `th:text="${catalogName}"` lấy dữ liệu do `@ModelAttribute("catalogName")` method cung cấp.
- Khi form submit sai, template được render lại và hiển thị lỗi thay vì redirect, giúp giữ thông tin người dùng vừa nhập.

**Nếu có Spring Security với CSRF protection bật**, form POST trong ứng dụng thật phải có CSRF token hợp lệ; Thymeleaf/Spring Security integration có thể tự thêm hidden token tùy cấu hình. Ví dụ dependency trên không bao gồm Spring Security nên chưa có yêu cầu đó. Không tắt CSRF bừa bãi chỉ để demo chạy.

### 6.4. Thymeleaf detail template

Tạo **`src/main/resources/templates/products/detail.html`**:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Chi tiết sản phẩm</title>
</head>
<body>
  <h1 th:text="${catalogName}">Tên danh mục</h1>
  <p th:if="${notice}" th:text="${notice}">Thông báo</p>

  <dl>
    <dt>ID</dt>
    <dd th:text="${product.id}">0</dd>
    <dt>Tên</dt>
    <dd th:text="${product.title}">Tên mẫu</dd>
    <dt>Số lượng</dt>
    <dd th:text="${product.quantity}">0</dd>
  </dl>

  <a th:href="@{/products/new}">Thêm sản phẩm</a>
</body>
</html>
```

`${product.id}`, `${product.title}` và `${product.quantity}` được Thymeleaf/Spring EL đọc từ các JavaBean getter của `Product`. Model ở request GET hiện tại có `product` được đặt bằng `model.addAttribute("product", product)`.

`notice` không đến từ `@ModelAttribute("catalogName")`: nó là **flash attribute** ở redirect từ POST trước đó. Nếu người dùng mở detail trực tiếp, `notice` có thể không tồn tại; `th:if="${notice}"` ngăn hiển thị phần thông báo.

### 6.5. Chạy và kiểm tra kết quả

Chạy dự án Spring Boot đã thiết lập:

```bash
./gradlew bootRun
```

Mở `http://localhost:8080/products/new` trên trình duyệt. View form phải hiển thị:

```text
Sổ sản phẩm
Tạo sản phẩm
Tên sản phẩm: [             ]
Số lượng:    [             ]
[Tạo]
```

Phần này là **mô phỏng nội dung giao diện**, không phải stdout của Java. Điền tên “Sổ tay”, số lượng “2” rồi submit. Trình duyệt nhận redirect tới dạng `/products/1001` (nếu chưa có POST khác kể từ khi start), sau đó hiển thị detail kèm thông báo.

Có thể quan sát response bằng curl:

```bash
curl -i 'http://localhost:8080/products/new'

curl -i -X POST 'http://localhost:8080/products' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'title=So tay' \
  --data-urlencode 'quantity=2'
```

POST hợp lệ trả status redirect, thường **302 Found**, cùng header `Location` trỏ đến `/products/{id}`. `curl` không theo redirect nếu không thêm `-L`, nên response ban đầu sẽ chưa là HTML chi tiết. Nếu muốn theo request tiếp theo, dùng `curl -L`, cần chú ý HTTP method follow-redirect theo client; với form browser thông thường sẽ GET trang đích.

POST không hợp lệ:

```bash
curl -i -X POST 'http://localhost:8080/products' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'title=' \
  --data-urlencode 'quantity=0'
```

Khi validation thất bại, method trả `"products/form"` trực tiếp: thường HTTP **200 OK**, có HTML form với thông báo lỗi ở title/quantity. Đây là cách xử lý form HTML điển hình, nhưng **không mặc nhiên thích hợp cho JSON API**; REST API thường dùng 400 với error body thay cho render form.

## 7. `@ModelAttribute` method được gọi khi nào?

Theo cơ chế Spring MVC, method `@ModelAttribute` trong cùng controller được thực thi **trước `@RequestMapping` method tương ứng** để chuẩn bị model. Có thể đặt method như vậy trong `@ControllerAdvice` để dùng chung nhiều controller, tùy selectors và advice ordering.

Ví dụ:

```java
@ControllerAdvice(assignableTypes = ProductController.class)
class ProductViewAdvice {

    @ModelAttribute("footerText")
    String footer() {
        return "© Demo Catalog";
    }
}
```

Đây là fragment bổ sung cho project ví dụ phần 6; cần import `org.springframework.web.bind.annotation.ControllerAdvice` và `ModelAttribute`. Nếu thêm class này vào cùng package, các view của `ProductController` có thể đọc `footerText`.

Không sử dụng `@ModelAttribute` method như interceptor điều khiển mọi HTTP lifecycle, vì nó chỉ tham gia xử lý controller qua MVC theo điều kiện invocation. Nó không bảo đảm chạy với static assets, request bị filter/security chặn trước MVC, hoặc một số lỗi phát sinh trước khi handler được chọn.

### Có bắt buộc phải dùng `Model` parameter trong method `@ModelAttribute`?

Không. Có hai mẫu chính:

```java
@ModelAttribute("applicationName")
String applicationName() {
    return "Spring Training";
}
```

Giá trị return được thêm dưới tên `applicationName`, hoặc:

```java
@ModelAttribute
void setup(Model model) {
    model.addAttribute("applicationName", "Spring Training");
    model.addAttribute("displayCurrency", "VND");
}
```

Ở mẫu thứ hai, method tự thêm nhiều entries. Đây là **các fragment thay thế nhau**, không nên đặt cả hai cùng tên và ghi đè chéo nếu không có quy tắc rõ.

### `@ModelAttribute` trên chính handler method

Spring cũng cho phép:

```java
@GetMapping("/current-product")
@ModelAttribute("product")
Product currentProduct() {
    return productService.current();
}
```

Đây là fragment dùng service do ứng dụng định nghĩa. `@ModelAttribute("product")` đặt trên handler method làm **giá trị trả về được hiểu như model attribute**, thay vì coi một `String` là tên view hay dùng `@ResponseBody` để ghi JSON. Nếu method không khai báo view name rõ, Spring có thể sử dụng cơ chế mặc định suy ra view name từ request URL; cần bảo đảm view resolver/template tương ứng.

Đừng dùng kiểu này khi một controller trả API JSON và vô tình mong response được serialize; trong REST controller hãy thể hiện `@ResponseBody`/`ResponseEntity` theo hợp đồng.

## 8. `@ModelAttribute` method và `Model.addAttribute`: nên chọn gì?

| Tình huống | Giải pháp | Lý do |
| --- | --- | --- |
| Mỗi page cần dữ liệu riêng do handler vừa tải | `Model.addAttribute("product", product)` | Nguồn dữ liệu rõ trong method |
| Nhiều view của cùng controller đều cần tên danh mục | Method `@ModelAttribute("catalogName")` | Tránh lặp logic |
| Nhiều controller cùng cần một bộ chọn ngôn ngữ/nhãn | `@ControllerAdvice` có method `@ModelAttribute` | Chia sẻ theo selector phù hợp |
| Handler trả cả model và view name | `ModelAndView` | Một return object rõ ràng |
| Form submit cần bind và hiển thị lỗi | `@Valid @ModelAttribute("productForm")` + `BindingResult` | Spring quản lý binding/errors |
| Redirect sau POST thành công | `RedirectAttributes.addFlashAttribute(...)` | Truyền thông báo ngắn tới request kế tiếp |
| Dữ liệu dùng qua nhiều bước của một wizard | `@SessionAttributes` (cẩn trọng) | Model attributes được giữ ở HTTP session cho workflow |
| API REST trả JSON | `@RequestBody`, `@ResponseBody`, `ResponseEntity` | Không qua view template |

Quy tắc thiết kế: không đưa toàn bộ cơ sở dữ liệu hay những object nhạy cảm vào model chỉ vì template cần vài trường. Truyền **view model/DTO vừa đủ** giúp tránh dữ liệu thừa và nguy cơ vô tình render trường không được phép công khai.

## 9. BindingResult phải đi liền form object

Với form validation, mẫu chuẩn:

```java
@PostMapping("/save")
String save(
        @Valid @ModelAttribute("form") ProductForm form,
        BindingResult result,
        Model model) {

    if (result.hasErrors()) {
        return "products/form";
    }

    // Thực hiện thao tác lưu thành công, sau đó redirect.
    return "redirect:/products";
}
```

Đây là **fragment** trong controller; biến `form`, view path và destination cần gắn với ứng dụng thật.

`BindingResult` hoặc `Errors` phải khai báo **ngay sau** tham số được kiểm tra. Spring sử dụng nó để báo lỗi chuyển kiểu và validation, đồng thời cung cấp các lỗi cho view engine. Trong view dùng:

```html
<form th:object="${form}">
  <input th:field="*{title}">
  <span th:if="${#fields.hasErrors('title')}"
        th:errors="*{title}">Lỗi</span>
</form>
```

Nếu `BindingResult` thiếu hoặc sai vị trí, lỗi có thể gây exception trước khi body controller chạy. Với Spring Framework 6.1+, nếu handler có constraint trực tiếp trên method parameters làm bật native method validation, đường exception có thể là `HandlerMethodValidationException`; không cứng nhắc cho rằng mọi validation form đều chỉ có một exception type.

### Bind sai kiểu trước khi validate

Nếu form có `Integer quantity` nhưng client gửi `quantity=abc`, Spring không thể chuyển thành Integer. Lỗi **type mismatch** được ghi trong `BindingResult` (với cấu hình xử lý phù hợp), dù annotation `@Min(1)` chưa nhận được một số hợp lệ để so sánh. Template cần hiển thị lỗi chuyển kiểu thay vì chỉ xem constraint annotations.

Tương tự, một `@NotNull` đặt trên `Integer` không bắt lỗi khi input là chuỗi không convert được theo cùng cách như một giá trị Java `null`; cả binding errors và validation errors đều cần hiển thị.

## 10. Bảo mật data binding: tránh mass assignment

`@ModelAttribute` có thể bind nhiều thuộc tính của object từ request; tính thuận tiện này dẫn đến **mass assignment / over-posting** nếu bind trực tiếp domain entity:

```java
// Ví dụ không nên dùng trực tiếp với entity chứa trường nhạy cảm.
@PostMapping("/users/update")
String update(@ModelAttribute UserEntity user) {
    repository.save(user);
    return "redirect:/users";
}
```

Client có thể thử gửi thêm parameter như `isAdmin=true` hoặc `balance=999999` nếu object có property writable cùng tên. Khi bind thẳng entity, attacker có thể sửa fields mà UI không hiển thị.

Các biện pháp:

1. Dùng **form DTO riêng** chỉ chứa dữ liệu cần nhận từ client, như `ProductForm` ở ví dụ.
2. Chỉ chấp nhận những fields được phép sửa, tránh writable setter cho secret fields.
3. Khi cần dùng property binding trên object phức tạp, cấu hình `WebDataBinder#setAllowedFields(...)` bằng `@InitBinder` để whitelist field cho phép.
4. Không coi `@ModelAttribute(binding=false)` là bộ lọc authorization tổng quát; nó **tắt binding** cho object đã chọn, còn cập nhật database vẫn phải kiểm tra quyền.
5. Kiểm tra authorization, ownership và business rules ở service/domain, không tin dữ liệu form do client gửi.
6. Sử dụng CSRF protection phù hợp với HTML form có cookie authentication.

Ví dụ giới hạn field:

```java
@InitBinder("productForm")
void productBinder(WebDataBinder binder) {
    binder.setAllowedFields("title", "quantity");
}
```

Đây là **method fragment** trong controller hoặc advice, cần import `org.springframework.web.bind.annotation.InitBinder` và `org.springframework.web.bind.WebDataBinder`. Nó minh họa cách chặn việc bind thêm ngoài hai field được cho phép. Nếu DTO đã chỉ có title/quantity, biện pháp này bổ sung lớp bảo vệ, nhưng cần test vì mọi form có nested properties/collection có thể cần allowed field patterns chi tiết.

**Constructor binding** cũng là lựa chọn an toàn khi thiết kế immutable form DTO rõ ràng, nhưng cần version/framework hỗ trợ và cách đặt tên constructor parameters phù hợp. Với bean setters của ví dụ, binding đơn giản để quan sát; không mặc định áp dụng kiểu dữ liệu thực thể giàu thuộc tính.

## 11. Redirect không giữ nguyên model của request trước

Khi controller trả `"redirect:/products/1001"`, Spring gửi HTTP 3xx và trình duyệt tạo **HTTP request mới**. Model của POST **không mặc nhiên còn nguyên** ở GET. Có ba cách cung cấp dữ liệu cho đích redirect:

1. **Path/query parameters**: dùng URL như `/products/1001` hoặc `?view=compact` để GET tự tải dữ liệu cần thiết.
2. **`RedirectAttributes.addAttribute(...)`**: thêm URI variables / query attributes vào redirect URL theo contract. Dữ liệu có thể hiển thị trong address bar, logs, history.
3. **`RedirectAttributes.addFlashAttribute(...)`**: tạm lưu thông điệp hoặc dữ liệu cho request sau redirect; thông thường lưu tạm trong HTTP session rồi đưa vào model request đích.

Trong ví dụ chính:

```java
redirects.addFlashAttribute("notice", "Đã tạo sản phẩm thành công");
return "redirect:/products/" + id;
```

Đây là fragment đã có ở phần 6. Sau redirect, controller GET tự thêm `product` vào model; `notice` được Spring lấy từ **input FlashMap** và đưa vào model cho request đích. Flash attribute **không phải session state dài hạn** và thường được sử dụng một lần.

Mặt trái của cách gửi dữ liệu qua URL:

```java
redirects.addAttribute("notice", "Đã tạo sản phẩm");
```

Nếu tham số được dùng để dựng redirect URL, text này có thể xuất hiện trong query string và bị lưu vào browser history/referrer/log. Hãy chọn flash attribute cho thông báo hiển thị tạm thời, không đưa thông tin bí mật vào URI.

**Cảnh báo về redirect model:** cấu hình MVC có thể cho phép một số model attributes tự xuất hiện trong redirect URI nếu không dùng `RedirectAttributes`. Vì vậy, để tránh vô tình phát lộ dữ liệu trong URL, nên chỉ định rõ những attributes được phép dùng cho redirect và cân nhắc cấu hình `ignoreDefaultModelOnRedirect` theo contract framework đang chạy.

## 12. `@ModelAttribute`, `@SessionAttributes` và `@SessionAttribute`

Ba annotation này có tên gần nhau nhưng không giống nhau:

| Annotation | Vị trí | Ý nghĩa |
| --- | --- | --- |
| `@ModelAttribute` | Method/parameter của controller | Bổ sung hoặc truy cập/bind dữ liệu model cho MVC |
| `@SessionAttributes` | **Class controller** | Lưu **một số model attributes** trong HTTP session cho workflow controller |
| `@SessionAttribute` | **Method parameter** | Đọc một giá trị đã tồn tại trong HTTP session, không trực tiếp quản lý model workflow |

Ví dụ khi có form nhiều bước:

```java
@Controller
@SessionAttributes("draft")
class OrderWizardController {

    @ModelAttribute("draft")
    OrderDraft draft() {
        return new OrderDraft();
    }

    @GetMapping("/wizard")
    String show() {
        return "orders/wizard";
    }

    @PostMapping("/wizard/finish")
    String finish(
            @ModelAttribute("draft") OrderDraft draft,
            SessionStatus sessionStatus) {
        // Thực hiện save hợp lệ.
        sessionStatus.setComplete();
        return "redirect:/orders";
    }
}
```

Đây là **fragment minh họa**, cần khai báo `OrderDraft` và templates; thêm imports `@SessionAttributes`, `SessionStatus`, `@ModelAttribute`, `@Controller`, `@GetMapping`, `@PostMapping`. Thực tế cần `@Valid` và bảo vệ CSRF/auth/transactions trước khi hoàn tất.

`@SessionAttributes("draft")` nói Spring giữ model attribute `draft` trong session của workflow controller; `SessionStatus#setComplete()` đánh dấu hoàn thành để Spring dọn những model attributes đã quản lý qua `@SessionAttributes`. Nó **không** có nghĩa invalidate toàn bộ user session hay đăng xuất.

Không dùng `@SessionAttributes` chỉ vì nhiều trang đều cần tên website hoặc currency label. Các thông tin đọc chung có thể nạp từ config/service qua `@ModelAttribute` hoặc global advice; session giữ state người dùng và tăng độ phức tạp concurrency/lifecycle.

## 13. `@ModelAttribute(binding=false)` và các nguồn object đã tồn tại

Đôi khi controller cần một object từ DB đặt vào model, nhưng **không muốn request parameters tự sửa object đó**.

Ví dụ:

```java
@ModelAttribute("product")
Product product(@PathVariable long id) {
    return catalogService.getRequired(id);
}

@PostMapping("/products/{id}/review")
String review(
        @ModelAttribute(binding = false) Product product,
        @Valid @ModelAttribute("reviewForm") ReviewForm form,
        BindingResult result) {
    // Kiểm tra quyền, hợp đồng và lưu đánh giá.
    return "redirect:/products/" + product.getId();
}
```

Đây là các fragment mô tả pattern; `catalogService`, `ReviewForm`, đường mapping và authorization cần được ứng dụng tự triển khai. `binding=false` ngăn việc lấy parameters của request để mutate trực tiếp `product`, còn `reviewForm` vẫn được bind và validate.

Thứ tự đưa object vào model và nguồn object phải được kiểm tra theo request mappings thực tế. Với một số kiểu dữ liệu, Spring có thể tạo model attribute bằng converter/constructor khi chưa có; không giả định nó tự tìm object trong repository chỉ vì có `@ModelAttribute("product")`.

## 14. Kiểm thử với MockMvc cho cả model, view và binding

Tạo **`src/test/java/demo/modelview/ModelViewApplicationTests.java`**:

```java
package demo.modelview;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ModelViewApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void getFormIncludesAttributesAndResolvesView() throws Exception {
        mvc.perform(get("/products/new"))
            .andExpect(status().isOk())
            .andExpect(view().name("products/form"))
            .andExpect(model().attributeExists(
                    "catalogName", "productForm"))
            .andExpect(model().attribute(
                    "catalogName", "Sổ sản phẩm"));
    }

    @Test
    void invalidFormKeepsFormAndValidationErrors() throws Exception {
        mvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("title", "")
                .param("quantity", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("products/form"))
            .andExpect(model().attributeHasFieldErrors(
                    "productForm", "title", "quantity"));
    }

    @Test
    void validFormRedirectsToCreatedProduct() throws Exception {
        MvcResult created = mvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("title", "Notebook")
                .param("quantity", "2"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute(
                    "notice", "Đã tạo sản phẩm thành công"))
            .andReturn();

        String destination = created.getResponse().getRedirectedUrl();
        assertNotNull(destination);
        assertTrue(destination.matches("/products/[0-9]+"));

        // MockMvc không tự follow redirect; kiểm tra GET riêng.
        mvc.perform(get(destination).flashAttrs(created.getFlashMap()))
            .andExpect(status().isOk())
            .andExpect(view().name("products/detail"))
            .andExpect(model().attributeExists(
                    "product", "catalogName"))
            .andExpect(model().attribute(
                    "notice", "Đã tạo sản phẩm thành công"));
    }

    @Test
    void unknownProductReturns404() throws Exception {
        mvc.perform(get("/products/9223372036854775807"))
            .andExpect(status().isNotFound());
    }
}
```

Chạy:

```bash
./gradlew test
```

Với các file phần 6 và dependency Boot 3 đã cấu hình, **bốn test dự kiến pass**. Đây là kỳ vọng dựa trên code mẫu, không phải kết quả đã chạy Gradle. Test xác minh:

- `model().attributeExists(...)` khẳng định controller đã truyền dữ liệu vào model.
- `view().name(...)` xác nhận view name được chọn thay vì trả dữ liệu JSON trực tiếp.
- `attributeHasFieldErrors(...)` kiểm tra binding/validation xuất hiện trong model khi input sai.
- `flash().attribute(...)` xác nhận flash message được thêm trước redirect.
- POST thành công tạo URI resource mới; GET kế tiếp có model `product` và view đúng. `flashAttrs(created.getFlashMap())` mô phỏng việc cấp lại flash attributes cho request GET trong bài test độc lập.

**Giới hạn MockMvc:** không xác minh hoàn toàn hành vi proxy, browser thật, CSRF khi tích hợp Security, session store đa node hoặc JavaScript trên trang. Với sản phẩm có giao diện quan trọng, kết hợp view/MockMvc tests với browser integration tests.

### Test khi form bị type mismatch

Thêm case `quantity=abc` và assert `model().attributeHasFieldErrors("productForm","quantity")`. Điều này chứng minh Spring giữ lỗi conversion để render view thay vì để mã Java ném `NumberFormatException` sau khi controller đã chạy. Không nên assert thông điệp lỗi tiếng Anh cụ thể vì chúng phụ thuộc MessageSource/locale.

## 15. So sánh truy cập model trong Thymeleaf, JSP và REST

### Thymeleaf

Thông thường dùng:

```html
<span th:text="${catalogName}">Fallback</span>
<input th:field="*{title}">
```

Đầu tiên đọc model attr. Dòng thứ hai cần nằm trong form có `th:object="${productForm}"` để sử dụng selection expression `*{title}`. Spring integration hỗ trợ binding/error state.

### JSP + Spring Form Tags

Ví dụ fragment trong JSP đã cấu hình JSTL/taglib:

```jsp
<spring:eval expression="catalogName" var="catalogNameText"/>
<p>${catalogNameText}</p>
```

Đây là **fragment minh họa ý tưởng JSP**, không phải mã trực tiếp áp dụng nguyên xi mà không khai báo taglib phù hợp. Với JSP, JSP Expression Language thực tế thường dùng `${catalogName}` đọc model attribute đã expose trong request scope; Spring form tags có binding và errors riêng theo form model. Khi dùng Thymeleaf trong ứng dụng mẫu thì **không cần cấu hình JSP**.

### JSON REST

```java
@RestController
class CatalogApi {
    @GetMapping("/api/catalog")
    java.util.Map<String, String> catalog() {
        return java.util.Map.of("name", "Sổ sản phẩm");
    }
}
```

Ở đây `Map` là **response body** được message converter serialize (thường JSON), không phải model dành cho template. Nếu trong `@RestController` bạn trả `"products/form"`, body có thể chứa đúng text `products/form` thay vì render trang HTML. Đây là lỗi thường gặp khi chuyển code từ MVC view sang REST API.

## 16. Lỗi phổ biến khi dùng `@ModelAttribute`

| Hiện tượng | Nguyên nhân khả dĩ | Hướng xử lý |
| --- | --- | --- |
| Template không thấy `productForm` | Controller quên `model.addAttribute("productForm", ...)` hoặc tên khác | Đặt tên model attr tường minh và assert bằng MockMvc |
| Form submit không bind vào object | Input `name` khác property hoặc content type không phù hợp | Dùng `th:field` hoặc kiểm tra form names / setters / constructors |
| Trả chữ `products/form` trên trình duyệt | Dùng `@RestController`/`@ResponseBody` | Chuyển controller phục vụ HTML sang `@Controller` |
| HTML form có lỗi nhưng không hiển thị | Thiếu `BindingResult` cạnh form hoặc template không dùng `th:errors` | Kiểm tra thứ tự tham số và name `th:object` |
| Invalid form redirect làm mất lỗi/value | Dùng redirect trong nhánh validation failed | Render trực tiếp cùng request hoặc thiết kế flash model phù hợp |
| Success message mất sau redirect | Đặt notice trong Model của POST | Dùng `RedirectAttributes.addFlashAttribute` |
| Model chứa dữ liệu cũ qua GET | Kỳ vọng request Model tự persist | GET tải lại data; session/flash nếu thực sự cần |
| Người dùng sửa field admin ngoài form | Bind trực tiếp entity có setters công khai | Form DTO riêng và allowed fields |
| Method `@ModelAttribute` chạy nhiều lần bất ngờ | Method góp model cho nhiều handler | Giảm side effect, cache nơi phù hợp và giới hạn scope |
| Model không được render | Method trả `@ResponseBody`/`ResponseEntity` | Chỉ render template khi handler trả view name hoặc ModelAndView |
| `@SessionAttributes` không được dọn | Quên `SessionStatus#setComplete()` | Hoàn thành wizard đúng lifecycle |
| Biến thể URL sau reverse proxy trả sai redirect | Context path/gateway prefix chưa đồng bộ | Kiểm tra `RedirectView`, forwarded headers đáng tin, test deploy path |

Các biểu hiện có thể chịu ảnh hưởng cấu hình khác (Spring Security, view resolver, filter, lỗi input); nên test và logs là nguồn xác nhận thay vì phỏng đoán.

## 17. Những điểm cần nắm khi nâng phiên bản Spring

**Spring Framework 5.x / Boot 2.x:** `@Controller`, `Model` và `@ModelAttribute` có semantics cơ bản như bài. Với Bean Validation, namespace thường là `javax.validation.Valid`. Tích hợp JSP/Thymeleaf phụ thuộc Boot starter và version.

**Spring Framework 6.x / Boot 3.x:** baseline Java 17+, Jakarta EE namespace chuyển sang `jakarta.validation.Valid` và `jakarta.servlet.*`. Spring 6 hỗ trợ constructor binding, Servlet data binding và cơ chế model ở MVC. Ví dụ bài dùng Boot 3.x với Thymeleaf. Native MVC method validation từ Framework 6.1 có thể ảnh hưởng exception trên một số handler khi constraints trực tiếp xuất hiện ở tham số method.

**Spring Framework 7.x / Boot 4.x:** các ý tưởng `@ModelAttribute` method, parameter binding, model/view và flash attributes vẫn còn. Tuy nhiên, dependency coordinates, starter module và các integration khác có thể thay đổi; cần xác nhận template engine / Spring MVC BOM theo version. Không sao chép build snippet Boot 3 vào Boot 4 mà bỏ qua migration guide.

Một lưu ý riêng với **GraalVM native image/AOT**: Spring khuyến nghị **khai báo `@ModelAttribute` tường minh** trên complex-type method arguments để phát hiện reflection hints cho data binding đúng; reliance on implicit `@ModelAttribute` có thể hạn chế AOT inference.

Những thay đổi version này không làm biến `@ModelAttribute` thành JSON parser hoặc cơ chế session tự động. Luôn đặt bài toán dữ liệu trong bối cảnh **view rendering vs request binding vs API response**.

## 18. Checklist xây form an toàn và dễ bảo trì

- [ ] Controller phục vụ HTML dùng `@Controller` và trả tên view hợp lệ.
- [ ] Form GET thêm object vào model với key tường minh; template `th:object` sử dụng đúng key.
- [ ] Tài nguyên template tồn tại trong thư mục được engine cấu hình và thuộc đúng view name.
- [ ] Các model attributes chung qua method `@ModelAttribute` không chạy query thừa hoặc làm thay đổi trạng thái ngoài ý muốn.
- [ ] POST chỉ bind vào DTO/form object cho phép, không bind trực tiếp entity chứa dữ liệu nhạy cảm.
- [ ] `@Valid`/`@Validated` phù hợp với constraints; `BindingResult` đứng ngay sau form parameter.
- [ ] Nếu lỗi, trả lại form cùng request, giữ errors và giá trị đã nhập.
- [ ] Nếu thành công, lưu xong rồi redirect theo Post-Redirect-Get, dùng flash attrs cho thông báo ngắn.
- [ ] Không đưa dữ liệu bí mật vào URL qua redirect attributes.
- [ ] Nếu dùng `@SessionAttributes`, lifecycle và `SessionStatus#setComplete()` rõ ràng.
- [ ] Form có CSRF protection khi dùng cookie-based authenticated actions.
- [ ] Có test cho model attributes, view name, field errors, redirect và flash attributes.
- [ ] Không giả định model/flash là persistence store hoặc cache dùng giữa mọi request.

## 19. Bài tập vận dụng

1. Thêm một method `@ModelAttribute("currentYear")` trong `ProductController`, hiển thị năm ở cả form và detail. Vì sao không cần thêm `model.addAttribute("currentYear", ...)` vào từng handler?
2. Trong form template, đổi `th:object="${productForm}"` thành `${product}`. Điều gì xảy ra nếu model chỉ có `productForm`? Làm sao MockMvc phát hiện lỗi này?
3. Bỏ `BindingResult` khỏi POST create rồi gửi `quantity=0`. Phân tích đường đi của validation, exception và status mặc định.
4. Chuyển POST create thành `@RestController`, trả `"products/detail"`. Vì sao trình duyệt không còn render view?
5. Một client gửi thêm `isAdmin=true` ngoài các field trong HTML. Giải thích vì sao bind entity nhiều setters tiềm ẩn rủi ro; thêm `@InitBinder#setAllowedFields` hoặc chuyển sang form DTO.
6. Viết test `POST /products` thành công, lấy URL từ `Location`, gửi request GET tiếp và kiểm tra model có thuộc tính `product`.
7. Thử chuyển thông báo thành công từ `addFlashAttribute` sang `addAttribute`. Phân biệt flash/session và query string, các rủi ro URL logging.
8. Xây một wizard ba bước dùng `@SessionAttributes("draft")`. Khi hoàn tất, tại sao nên gọi `SessionStatus#setComplete()` thay vì invalidate toàn bộ session?
9. Sửa form để nhận số lượng bằng `Integer` và gửi `quantity=abc`. Kiểm tra field error do conversion, giải thích vì sao nó khác lỗi `@Min(1)`.
10. Dùng `@ControllerAdvice` để thêm danh sách vùng lựa chọn cho hai controller. Làm sao giới hạn advice chỉ áp dụng cho nhóm view phù hợp và tránh chạy DB query trên mọi REST endpoint?

## 20. Tài liệu chính thức

- [Spring Framework — Model và `@ModelAttribute` methods](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-modelattrib-methods.html): method-level attribute population.
- [Spring Framework — `@ModelAttribute` method arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html): binding, validation, object creation, `binding=false` và AOT.
- [Spring Framework — Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html): `Model`, `BindingResult`, `RedirectAttributes`, `@SessionAttributes`.
- [Spring Framework — Return Values](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html): String view name, ModelAndView, Model/Map, ResponseBody.
- [Spring Framework — Redirect Attributes](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/redirecting-passing-data.html): truyền dữ liệu qua URI và flash attributes, `ignoreDefaultModelOnRedirect`.
- [Spring Framework — Flash Attributes](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/flash-attributes.html): FlashMap, lifecycle, concurrency.
- [Spring Framework — `@SessionAttributes`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/sessionattributes.html): model/session workflow.
- [Spring Framework — Data Binding and Validation](https://docs.spring.io/spring-framework/reference/core/validation/beans-beans.html): `DataBinder`, binding và allowed fields.
- [Thymeleaf — Spring Integration Tutorial](https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html): `th:object`, `th:field`, form errors và Spring model.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-forms-005">Liên kết biểu mẫu, hiển thị BindingResult và thông báo lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Liên kết biểu mẫu, hiển thị BindingResult và thông báo lỗi trong Spring MVC

## 1. Mục tiêu và phạm vi

Trong ứng dụng Spring MVC có **HTML view**, người dùng nhập dữ liệu trong form, gửi POST, rồi mong được giữ lại những gì đã nhập và thấy lỗi tại đúng ô nếu sai. Bài này bao quát toàn bộ chu trình form GET/POST, data binding, validation, hiển thị lỗi qua Thymeleaf và Post/Redirect/Get.

Sau bài học, bạn sẽ biết:

- Dùng `@Controller`, `Model`, `@ModelAttribute` và form backing object/DTO để nối HTML với Java.
- Phân biệt lỗi chuyển kiểu (`typeMismatch`), lỗi Bean Validation (`NotBlank`, `Min`, `Email`), lỗi business và lỗi toàn object.
- Đặt `BindingResult` đúng ngay sau form argument; đọc `FieldError`, `ObjectError`, rejected values và suppressed fields.
- Giữ dữ liệu form trong nhánh trả lại view khi có lỗi; hiển thị lỗi bằng `th:object`, `th:field`, `th:errors`, `th:errorclass` và `#fields`.
- Tạo ví dụ **Java 17+, Spring Boot 3.x/Spring Framework 6.x, Thymeleaf 3.1/Spring 6, Jakarta Bean Validation** có thể chạy trong một dự án Spring Web/Thymeleaf/Validation.
- Test bằng MockMvc để biết cả model có lỗi **và người dùng thực sự nhìn thấy thông báo trong HTML**.

Spring WebFlux dùng cơ chế và API khác; ví dụ ở đây là Spring MVC trên Servlet stack. Khi dùng Spring Boot 4 hoặc Spring Framework 7, nên đối chiếu API/dialect/test với phiên bản thực tế.

## 2. Toàn bộ vòng đời biểu mẫu

```text
Browser GET /register
    -> @Controller.showForm(Model)
    -> model["registration"] = RegistrationForm mới
    -> Thymeleaf templates/registration.html
       th:object="${registration}", th:field="*{fullName}"
    -> HTML có input name="fullName", name="age", ...

Browser POST /register
    -> application/x-www-form-urlencoded
    -> @ModelAttribute("registration") RegistrationForm
    -> WebDataBinder: parameter String -> String, Integer, LocalDate
    -> @Valid: kiểm tra Jakarta constraints
    -> BindingResult lưu binding errors + validation errors
    -> @Controller.submit(form, result, redirectAttributes)
         |
         +-- result.hasErrors()
         |      -> return "registration"
         |      -> render lại giá trị nhập, th:errors cho field/global
         |
         +-- không có errors
                -> service xử lý nghiệp vụ
                -> addFlashAttribute(...)
                -> redirect:/register/success
                -> Browser GET /register/success
```

Không phải mọi lớp đều chạy validation tự động. `@Valid` hoặc `@Validated` kích hoạt validator nếu đã cấu hình provider, còn binding/conversion xảy ra khi Spring resolve argument. Form HTML thường gửi `application/x-www-form-urlencoded`; JSON `@RequestBody` đi qua `HttpMessageConverter` khác với luồng property binding.

## 3. Form backing object và tên property

Một form DTO chứa **những trường browser được phép gửi**, không nhất thiết là JPA entity hoặc domain object. Ví dụ DTO `RegistrationForm` gồm `fullName`, `email`, `age`, `startDate`.

```html
<form method="post" action="/register">
  <input name="fullName" value="Lan">
  <input name="age" value="20">
</form>
```

Trình duyệt gửi `fullName=Lan&age=20`. `WebDataBinder` tìm các property tên tương ứng và gán chúng vào Java object, dùng type conversion khi cần. `name` của input quyết định tên request parameter; `id` dành cho label/CSS/JS và không thay thế `name`.

Lý do dùng DTO form riêng:

- Client không thể tùy ý bind vào `isAdmin`, `role`, `ownerId` hoặc các field persistence không thuộc hợp đồng.
- Form có thể có validation và message phù hợp UX mà database entity không nên mang theo.
- Khi validation sai, object vẫn mang những giá trị bind được; những giá trị lỗi chuyển kiểu có thể nằm trong `BindingResult`.
- DTO nhỏ dễ thay đổi trình bày mà không khiến view phụ thuộc schema database.
- Dù dùng DTO, **authorization** ở service vẫn bắt buộc; một field có mặt trong DTO không đồng nghĩa mọi client đều được sửa.

### Missing, empty và invalid khác nhau

| Form parameter | Target Java | Kết quả điển hình |
| --- | --- | --- |
| Không gửi `age` | `Integer age` | `null`, có thể lỗi `@NotNull` |
| `age=` | `Integer age` | Có thể convert thành `null`, rồi lỗi `@NotNull` |
| `age=abc` | `Integer age` | Không convert được, `FieldError` với `typeMismatch` |
| `age=15` | `Integer age` | Convert thành công nhưng `@Min(18)` lỗi |
| `age=20` | `Integer age` | Convert và validation thành công |
| `fullName=   ` | `String` | Chuỗi có khoảng trắng; `@NotBlank` lỗi |
| `startDate=tomorrow` | `LocalDate` với ISO date | Lỗi formatter/conversion |

Một form HTML không có `Content-Type: application/json` chỉ vì một field nhìn giống JSON. Dùng `@ModelAttribute` cho form-urlencoded, dùng `@RequestBody`/Jackson cho JSON. Nếu POST gửi sai media type, có thể gặp `415` trước khi validation.

## 4. @ModelAttribute và BindingResult

`@ModelAttribute("registration")` chỉ định tên model object để Spring tạo/lấy đối tượng và bind request parameters. Ví dụ method xử lý POST:

```java
@PostMapping("/register")
public String submit(
        @Valid @ModelAttribute("registration") RegistrationForm form,
        BindingResult result,
        RedirectAttributes redirectAttributes) {

    if (result.hasErrors()) {
        return "registration";
    }
    redirectAttributes.addFlashAttribute("notice", "Đã nhận đăng ký");
    return "redirect:/register/success";
}
```

Đây là đoạn method minh họa trong controller; mã controller hoàn chỉnh ở phần dưới.

**`BindingResult` phải nằm ngay sau argument đang được bind/validate.** Đặt `Model`, `RedirectAttributes`, `@RequestParam` hoặc argument khác chen vào giữa có thể khiến Spring không thể gắn lỗi đúng target và dẫn tới exception. Nếu nhiều form object trong một handler, mỗi BindingResult cần đi ngay sau object tương ứng.

Các phương thức thực tế:

| Phương thức | Công dụng |
| --- | --- |
| `hasErrors()` / `getErrorCount()` | Phát hiện lỗi và đếm lỗi |
| `getFieldErrors()` | Lấy lỗi field (có thể nhiều lỗi một field) |
| `hasFieldErrors("age")` | Kiểm tra field tuổi |
| `getGlobalErrors()` | Lỗi object không gắn field riêng |
| `getAllErrors()` | Tất cả `FieldError` và `ObjectError` |
| `FieldError.getField()` | Tên field tương ứng |
| `FieldError.getCode()` | Mã lỗi chính như `typeMismatch`, `Min`, `NotBlank` |
| `FieldError.getCodes()` | Danh sách mã cụ thể tới tổng quát dùng cho message resolution |
| `FieldError.getRejectedValue()` | Giá trị bị từ chối, phải tránh log/lộ dữ liệu nhạy cảm |
| `FieldError.isBindingFailure()` | Gợi ý lỗi binding/conversion hay lỗi do validator |
| `rejectValue("field", "code", "fallback")` | Thêm field error theo quy tắc tùy chỉnh |
| `reject("code", "fallback")` | Thêm global error |
| `getSuppressedFields()` | Những field bị binder từ chối, không nhất thiết là FieldError |

Trong nhánh lỗi, controller trả lại `"registration"`. Spring MVC giữ attribute `registration` và companion BindingResult trong model. **Không tạo DTO mới sau khi POST đã có lỗi**, vì bạn có thể xóa giá trị người dùng vừa nhập và đánh mất rejected values mà Thymeleaf cần để hiển thị lại.

### Conversion error cũng có thể nằm trong BindingResult

`age=abc` không phải số. Binder không thể gán `Integer` nên ghi lỗi field với code chính thường là `typeMismatch`. `age=15` chuyển thành Integer được, nhưng `@Min(18)` thất bại, tạo mã `Min`. Hai tình huống khác nhau; hãy hiển thị thông điệp có ích riêng cho từng loại.

Nếu JSON body chứa `"age":"abc"` và được xử lý bằng `@RequestBody`, Jackson có thể báo `HttpMessageNotReadableException` trước Bean Validation, **không nhất thiết tạo cùng FieldError của @ModelAttribute**.

## 5. Jakarta Bean Validation trong form

Spring Boot 3 dùng namespace `jakarta.validation`, và dự án cần `spring-boot-starter-validation`. Những constraint phổ biến:

| Annotation | Ý nghĩa |
| --- | --- |
| `@NotBlank` | Chuỗi phải có ký tự không phải khoảng trắng |
| `@Email` | Kiểm tra cú pháp email theo validator, không xác nhận hộp thư tồn tại |
| `@Size(max=80)` | Giới hạn độ dài hợp lệ |
| `@NotNull` | Giá trị không được `null` |
| `@Min(18)` | Số >= 18; null thường được phép riêng bởi @Min |
| `@PastOrPresent` | Ngày không ở tương lai khi nghiệp vụ yêu cầu |

Nếu cần tuổi bắt buộc, dùng `Integer age` với `@NotNull @Min(18)`. `int` primitive có thể mang giá trị mặc định 0 trước kiểm tra, làm mất dấu vết thiếu/null. Với ngày tháng, `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` cho binder biết format `yyyy-MM-dd` của `LocalDate`; lỗi `startDate=tomorrow` là lỗi conversion. `@NotNull` không xác định được một chuỗi sai format có nghĩa ngày đã qua hay chưa.

Một field có thể có nhiều constraint và nhiều lỗi. Không dựa vào thứ tự thông báo khi test; nên xác định code hoặc xuất hiện ít nhất một thông báo phù hợp.

## 6. Thymeleaf form binding

Thymeleaf Spring dialect cung cấp:

```html
<form th:action="@{/register}"
      th:object="${registration}" method="post">
  <label for="fullName">Họ tên</label>
  <input id="fullName" type="text" th:field="*{fullName}">
</form>
```

- `th:object="${registration}"` gắn form backing object mà controller đặt vào model.
- `th:field="*{fullName}"` sinh và quản lý các thuộc tính `name`, `id`, `value` phù hợp Spring form binding.
- `th:action="@{/register}"` xây URL theo ứng dụng/context path thay vì hardcode một địa chỉ tuyệt đối.
- Controller GET và POST phải thống nhất tên model `registration`; thay tên thành `form` ở một nơi mà không đổi các nơi khác gây lỗi bind/render.
- `@Controller` trả tên view; `@RestController` ngầm `@ResponseBody`, nên return `"registration"` thường được ghi thành text body thay vì render HTML.

Khi POST lỗi, Thymeleaf có thể dùng rejected value của BindingResult để khôi phục raw input chưa convert được. Nếu dùng `<input type="number">` cho `age=abc`, một số browser chặn hoặc bỏ giá trị không phải số. Ví dụ học tập dùng `type="text" inputmode="numeric"` để thấy chính xác `abc` và error; trong production chọn loại input theo UX sau khi đã test.

## 7. th:errors, th:errorclass, #fields và thông báo lỗi

```html
<label for="email">Email</label>
<input id="email" type="text" th:field="*{email}"
       th:errorclass="invalid" aria-describedby="emailError">
<p id="emailError" class="error"
   th:if="${#fields.hasErrors('email')}"
   th:errors="*{email}">
  Email không hợp lệ
</p>
```

Ý nghĩa:

- `#fields.hasErrors("email")`: có lỗi cho field này?
- `th:errors="*{email}"`: render một hoặc nhiều messages của field đã được resolve.
- `th:errorclass="invalid"`: thêm class CSS khi field lỗi.
- `#fields.errors("email")`: danh sách messages nếu bạn muốn tự lặp bằng `th:each`.
- `#fields.hasGlobalErrors()` và `#fields.globalErrors()`: lỗi không gắn một field cụ thể.
- `#fields.hasErrors("all")` / `#fields.allErrors()`: tất cả lỗi nếu muốn làm tổng kết trên đầu form.

Lỗi global có thể hiển thị:

```html
<ul th:if="${#fields.hasGlobalErrors()}">
  <li th:each="err : ${#fields.globalErrors()}"
      th:text="${err}">Lỗi toàn biểu mẫu</li>
</ul>
```

Hãy kết hợp thông báo văn bản và CSS, không chỉ dùng viền đỏ. Đặt `label` và `aria-describedby` để hỗ trợ screen reader; khi có lỗi, `aria-invalid` có thể được gắn bằng Thymeleaf. Text do người dùng gửi phải được HTML-escape: dùng `th:text`/`th:errors`, tránh `th:utext` với nội dung không tin cậy.

## 8. Ví dụ runnable: Spring Boot 3 + Thymeleaf + Validation

Tạo project Maven Java 17+ với `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-validation` và `spring-boot-starter-test`. Ví dụ không cần database; hoạt động đăng ký chỉ mô phỏng chấp nhận một form hợp lệ.

Cấu trúc tệp:

```text
src/main/java/com/example/demo/
  DemoApplication.java
  RegistrationForm.java
  RegistrationController.java
src/main/resources/templates/
  registration.html
  registration-success.html
src/test/java/com/example/demo/
  RegistrationControllerTest.java
```

### 8.1. DemoApplication.java

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

### 8.2. RegistrationForm.java

```java
package com.example.demo;

import java.time.LocalDate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

public class RegistrationForm {
    @NotBlank(message = "Vui lòng nhập họ tên")
    @Size(max = 80, message = "Họ tên tối đa 80 ký tự")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email chưa đúng định dạng")
    private String email;

    @NotNull(message = "Vui lòng nhập tuổi")
    @Min(value = 18, message = "Phải từ 18 tuổi trở lên")
    private Integer age;

    @NotNull(message = "Vui lòng nhập ngày bắt đầu")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    public RegistrationForm() {}

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
}
```

Dùng `Integer` cho nullable/blank input và `LocalDate` cho ngày không có timezone. Sau POST, tất cả các getter/setter cho phép binder xử lý giá trị. DTO không có role, ID, trạng thái admin hoặc field từ database.

### 8.3. RegistrationController.java

```java
package com.example.demo;

import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/register")
public class RegistrationController {

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("registration", new RegistrationForm());
        return "registration";
    }

    @PostMapping
    public String submit(
            @Valid @ModelAttribute("registration") RegistrationForm form,
            BindingResult errors,
            RedirectAttributes redirectAttributes) {

        // Chỉ để minh họa global error, không phải quy tắc chung.
        if (form.getEmail() != null
                && form.getEmail().toLowerCase(Locale.ROOT)
                    .endsWith("@blocked.example")) {
            errors.reject(
                    "registration.emailDomainBlocked",
                    "Địa chỉ email này không được phép đăng ký");
        }

        if (errors.hasErrors()) {
            return "registration";
        }

        // Demo không ghi DB. Ứng dụng thực gọi service tại đây,
        // sau khi đã validate và kiểm tra quyền nghiệp vụ.
        redirectAttributes.addFlashAttribute(
                "notice", "Đăng ký đã được tiếp nhận");
        return "redirect:/register/success";
    }

    @GetMapping("/success")
    public String success() {
        return "registration-success";
    }
}
```

Controller minh họa:

- GET đặt một object vào model, POST nhận đúng tên model.
- `BindingResult errors` ở ngay sau form argument.
- `errors.reject(...)` tạo global error. Rule domain ở đây chỉ là demo; production nên dùng validator/service riêng để giữ controller tập trung vào HTTP/view.
- Có lỗi: return cùng view, không tạo object mới và không gọi service lưu.
- Không lỗi: dùng Post/Redirect/Get. `addFlashAttribute` chuyển message sang GET kế tiếp, không phải persistence.
- `redirect:/register/success` được Spring tạo Location, giữ browser ở URL GET khi refresh.

### 8.4. registration.html

Tệp `src/main/resources/templates/registration.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Đăng ký</title>
  <style>
    body { max-width: 40rem; margin: 2rem auto; padding: 1rem;
           font-family: system-ui, sans-serif; line-height: 1.5; }
    label { display: block; font-weight: 600; margin-top: 1rem; }
    input { width: 100%; box-sizing: border-box; padding: .5rem; }
    input.invalid { border: 2px solid #b42318; }
    .error { color: #b42318; margin: .3rem 0 .6rem; }
    .global-error { border: 2px solid #b42318; padding: .6rem; }
    button { margin-top: 1.5rem; padding: .6rem 1rem; }
  </style>
</head>
<body>
  <h1>Đăng ký</h1>
  <p>Hãy nhập đầy đủ thông tin. Tuổi tối thiểu là 18.</p>

  <form method="post" th:action="@{/register}"
        th:object="${registration}">

    <div class="global-error" role="alert"
         th:if="${#fields.hasGlobalErrors()}">
      <p>Biểu mẫu chưa được chấp nhận:</p>
      <ul>
        <li th:each="err : ${#fields.globalErrors()}"
            th:text="${err}">Lỗi toàn biểu mẫu</li>
      </ul>
    </div>

    <label for="fullName">Họ tên</label>
    <input id="fullName" type="text" th:field="*{fullName}"
           th:errorclass="invalid"
           th:attr="aria-invalid=${#fields.hasErrors('fullName')}"
           aria-describedby="fullNameError">
    <p id="fullNameError" class="error"
       th:if="${#fields.hasErrors('fullName')}"
       th:errors="*{fullName}">Họ tên không hợp lệ</p>

    <label for="email">Email</label>
    <input id="email" type="text" th:field="*{email}"
           th:errorclass="invalid"
           th:attr="aria-invalid=${#fields.hasErrors('email')}"
           aria-describedby="emailError">
    <p id="emailError" class="error"
       th:if="${#fields.hasErrors('email')}"
       th:errors="*{email}">Email không hợp lệ</p>

    <label for="age">Tuổi</label>
    <input id="age" type="text" inputmode="numeric"
           th:field="*{age}"
           th:errorclass="invalid"
           th:attr="aria-invalid=${#fields.hasErrors('age')}"
           aria-describedby="ageError">
    <p id="ageError" class="error"
       th:if="${#fields.hasErrors('age')}"
       th:errors="*{age}">Tuổi không hợp lệ</p>

    <label for="startDate">Ngày bắt đầu (yyyy-MM-dd)</label>
    <input id="startDate" type="text" placeholder="2026-10-21"
           th:field="*{startDate}"
           th:errorclass="invalid"
           th:attr="aria-invalid=${#fields.hasErrors('startDate')}"
           aria-describedby="dateError">
    <p id="dateError" class="error"
       th:if="${#fields.hasErrors('startDate')}"
       th:errors="*{startDate}">Ngày không hợp lệ</p>

    <button type="submit">Gửi đăng ký</button>
  </form>
</body>
</html>
```

`th:field` đảm bảo các giá trị đã nhập được khôi phục khi render lại form, kể cả trường hợp `age=abc` chưa chuyển thành Integer (nằm trong rejected value của BindingResult). Để quan sát lỗi này, ví dụ dùng input `type="text" inputmode="numeric"`; nhiều trình duyệt tự từ chối giá trị không phải số trong input `type="number"`.

Thymeleaf `th:errors` hiển thị message đã resolve. `th:errorclass` chỉ thêm class CSS nếu field lỗi. `th:attr` đặt thuộc tính ARIA theo `#fields.hasErrors`; trang có thông báo bằng chữ, không phụ thuộc riêng màu đỏ.

Khi ứng dụng bật Spring Security, HTML POST có thể cần CSRF token. Với Thymeleaf Spring integration, Spring Security có thể tự thêm hidden input qua `RequestDataValueProcessor` khi render `th:action`; **kiểm tra HTML thực tế** để chắc chắn đã có token, hoặc tích hợp CSRF theo hướng dẫn security. Không tắt CSRF toàn cục chỉ để form POST chạy.

### 8.5. registration-success.html

Tệp `src/main/resources/templates/registration-success.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Đăng ký thành công</title>
</head>
<body>
  <h1>Đã tiếp nhận biểu mẫu</h1>
  <p th:if="${notice != null}" th:text="${notice}">
    Đăng ký đã được tiếp nhận
  </p>
  <a th:href="@{/register}">Gửi biểu mẫu khác</a>
</body>
</html>
```

Template vẫn hiển thị được khi GET trực tiếp mà không có flash message. `th:text` escape thông điệp an toàn; tránh dùng `th:utext` với message chứa dữ liệu client không tin cậy.

### 8.6. Chạy ứng dụng

Từ project Maven có wrapper:

```bash
./mvnw spring-boot:run
```

Trên Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Mặc định truy cập `http://localhost:8080/register` nếu `server.port` chưa thay đổi. Các tệp Java này cần Spring Web, Thymeleaf và Bean Validation trên classpath; chúng không biên dịch bằng `javac` độc lập với không có dependencies.

## 9. Thực hành HTTP: valid, validation fail, conversion fail, global fail

### 9.1. GET biểu mẫu

```bash
curl -i http://localhost:8080/register
```

Kỳ vọng HTTP 200, `Content-Type` tương thích `text/html` và HTML có các input `name="fullName"`, `name="email"`, `name="age"`, `name="startDate"`. Client nhận HTML đã render, không còn `th:field` nguyên văn.

### 9.2. POST hợp lệ

```bash
curl -i http://localhost:8080/register \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "fullName=Lan" \
  --data-urlencode "email=lan@example.com" \
  --data-urlencode "age=20" \
  --data-urlencode "startDate=2026-10-21"
```

Kỳ vọng response redirect HTTP 302 (URL mới `/register/success`), vì controller không có lỗi và thực hiện Post/Redirect/Get. Header Location thực tế có thể theo context path/gateway.

### 9.3. POST validation thất bại

```bash
curl -i http://localhost:8080/register \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "fullName=" \
  --data-urlencode "email=bad-email" \
  --data-urlencode "age=15" \
  --data-urlencode "startDate=2026-10-21"
```

Trong demo, controller trả lại view `registration`; thông thường HTTP 200 khi render lại HTML có lỗi. Điều này **không có nghĩa đã lưu dữ liệu**, vì handler kiểm tra `hasErrors()`. Ứng dụng khác có thể cố ý trả 400/422 theo UX/HTTP contract, nhưng form view mặc định thường trả 200.

### 9.4. Lỗi conversion, giá trị vẫn hiển thị

```bash
curl -i http://localhost:8080/register \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "fullName=Lan" \
  --data-urlencode "email=lan@example.com" \
  --data-urlencode "age=abc" \
  --data-urlencode "startDate=2026-10-21"
```

`age=abc` không parse được Integer, `BindingResult` lưu `typeMismatch`; Thymeleaf thường render lại text input với `value="abc"` và message nhập số hợp lệ. Nếu POST method tạo `new RegistrationForm()` sau khi có lỗi, rejected value có thể bị mất.

### 9.5. Lỗi global

```bash
curl -i http://localhost:8080/register \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "fullName=Lan" \
  --data-urlencode "email=lan@blocked.example" \
  --data-urlencode "age=20" \
  --data-urlencode "startDate=2026-10-21"
```

Tình huống này thỏa Bean Validation về cú pháp email nhưng vi phạm rule demo; `errors.reject(...)` thêm lỗi global và template hiển thị ở đầu form.

### 9.6. Lỗi định dạng ngày

```bash
curl -i http://localhost:8080/register \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "fullName=Lan" \
  --data-urlencode "email=lan@example.com" \
  --data-urlencode "age=20" \
  --data-urlencode "startDate=tomorrow"
```

`startDate=tomorrow` không khớp định dạng ISO date mà formatter mong đợi; binding lỗi `typeMismatch` cho ngày, khác việc có LocalDate hợp lệ nhưng business rule cho rằng ngày đó không cho phép.

## 10. Kiểm thử MockMvc cả model và HTML

Tệp `src/test/java/com/example/demo/RegistrationControllerTest.java`, sử dụng `spring-boot-starter-test`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationControllerTest {
    @Autowired
    MockMvc mvc;

    @Test
    void getFormShowsFields() throws Exception {
        mvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("registration"))
            .andExpect(model().attributeExists("registration"))
            .andExpect(content().string(containsString("name=\"fullName\"")));
    }

    @Test
    void invalidInputIsShownInHtml() throws Exception {
        mvc.perform(post("/register")
                .param("fullName", "")
                .param("email", "bad-email")
                .param("age", "15")
                .param("startDate", "2026-10-21"))
            .andExpect(status().isOk())
            .andExpect(view().name("registration"))
            .andExpect(model().attributeHasFieldErrors(
                    "registration", "fullName", "email", "age"))
            .andExpect(content().string(
                    containsString("Vui lòng nhập họ tên")));
    }

    @Test
    void invalidIntegerRetainsRejectedText() throws Exception {
        mvc.perform(post("/register")
                .param("fullName", "Lan")
                .param("email", "lan@example.com")
                .param("age", "abc")
                .param("startDate", "2026-10-21"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrorCode(
                    "registration", "age", "typeMismatch"))
            .andExpect(content().string(containsString("value=\"abc\"")));
    }

    @Test
    void globalRuleAddsGlobalError() throws Exception {
        mvc.perform(post("/register")
                .param("fullName", "Lan")
                .param("email", "lan@blocked.example")
                .param("age", "20")
                .param("startDate", "2026-10-21"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasErrors("registration"))
            .andExpect(content().string(containsString(
                    "Địa chỉ email này không được phép đăng ký")));
    }

    @Test
    void validFormRedirects() throws Exception {
        mvc.perform(post("/register")
                .param("fullName", "Lan")
                .param("email", "lan@example.com")
                .param("age", "20")
                .param("startDate", "2026-10-21"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/register/success"))
            .andExpect(flash().attributeExists("notice"));
    }
}
```

Chạy `./mvnw test` (PowerShell: `.\mvnw.cmd test`). Bộ test này là **mẫu nguồn dành cho project Boot 3 có dependencies đúng**, chưa được thực thi trong bất kỳ repository người dùng nào. Nếu ứng dụng bật Spring Security, phải cấu hình test với `spring-security-test` và CSRF/token/role phù hợp; thiếu CSRF có thể gây HTTP 403 trước controller.

Vì sao cần cả model và HTML assertions? `model().attributeHasFieldErrorCode(...)` xác nhận Spring phát hiện `typeMismatch`, nhưng người dùng có thể vẫn không thấy thông báo nếu template sai. Kiểm tra HTML giúp phát hiện biểu mẫu bỏ quên `th:errors`/th:object hoặc không giữ rejected input.

## 11. MessageSource và thông báo tiếng Việt

Thông báo `@NotBlank(message="...")` có thể đặt trực tiếp trong annotations khi demo. Trong ứng dụng lớn, nên dùng mã lỗi và resource bundle để quốc tế hóa. Spring có thể sinh các code cho lỗi chuyển kiểu tuổi như:

```text
typeMismatch.registration.age
typeMismatch.age
typeMismatch.java.lang.Integer
typeMismatch
```

Ví dụ tệp `src/main/resources/messages.properties`:

```properties
typeMismatch.registration.age=Tuổi phải là số nguyên hợp lệ
typeMismatch.registration.startDate=Ngày phải theo yyyy-MM-dd
typeMismatch=Giá trị không đúng định dạng
```

Khi cần nhiều ngôn ngữ, dùng `messages_vi.properties`/`messages_en.properties` và cấu hình locale thích hợp. `MessageSource`/Bean Validation message interpolation giúp tách câu chữ hiển thị khỏi logic; tránh lấy `exception.getMessage()` hoặc rejected values nhạy cảm đưa thẳng tới UI.

Mã `typeMismatch` đến từ binding, còn mã `NotBlank`, `Email`, `Min` từ validation. `getCodes()` thường chứa mã cụ thể cho object/field/type để resolve message theo mức ưu tiên. Không hardcode một chuỗi message trong test nếu ứng dụng đổi locale; với test HTML có thể chọn locale cố định.

## 12. Lỗi global, lỗi chéo field và custom validation

Một điều kiện liên quan nhiều field, ví dụ `startDate` trước `endDate`, có thể tạo global error:

```java
errors.reject(
    "registration.invalidPeriod",
    "Ngày kết thúc phải sau ngày bắt đầu");
```

Đây chỉ là snippet trong method có `BindingResult errors`; DTO demo không chứa `endDate`. Nếu biết chính xác input nào phải sửa, dùng:

```java
errors.rejectValue(
    "startDate",
    "registration.startDateInvalid",
    "Ngày bắt đầu không được chấp nhận");
```

`reject(...)` tạo global `ObjectError`. `rejectValue(...)` tạo `FieldError`. Thymeleaf hiển thị chúng bằng `#fields.globalErrors()` hoặc `th:errors="*{startDate}"` tương ứng. Business validation phức tạp nên ở validator/service thích hợp để tránh controller chứa nhiều quy tắc hoặc query database tốn chi phí trước khi form đã pass kiểm tra định dạng.

## 13. WebDataBinder, allowed fields và bảo mật

Form DTO bảo vệ input surface, nhưng nested binding hoặc mass assignment vẫn cần xem xét. Có thể đặt trong controller:

```java
@InitBinder("registration")
public void customizeBinder(WebDataBinder binder) {
    binder.setAllowedFields(
        "fullName", "email", "age", "startDate");
}
```

Đây là một **đoạn method có thể thêm vào controller mẫu** với imports `org.springframework.web.bind.annotation.InitBinder`, `org.springframework.web.bind.WebDataBinder`. Không tự động bắt buộc dùng khi DTO đã nhỏ, nhưng có thể bổ sung defense-in-depth.

Field bị chặn bởi allowlist thường được ghi tại `BindingResult.getSuppressedFields()`; không phải lúc nào tạo FieldError và `hasErrors()` cũng không nhất thiết thành true. Nếu policy yêu cầu từ chối form có field ngoài danh sách:

```java
if (errors.getSuppressedFields().length > 0) {
    errors.reject(
        "registration.unexpectedFields",
        "Biểu mẫu có trường không được phép gửi");
}
```

Chỉ dùng sau khi thực sự cấu hình allowlist. `@InitBinder` còn có thể cấu hình `Formatter`, `PropertyEditor`, `StringTrimmerEditor`. Trimmer chuyển chuỗi trắng thành null có thể làm thay đổi phân biệt missing, empty, blank; hãy test lại tất cả trường hợp.

## 14. Form lỗi và Post/Redirect/Get

| Kết quả POST | Hành động thông dụng | Lý do |
| --- | --- | --- |
| Binding/validation lỗi | `return "registration"` | Giữ object và BindingResult, render lỗi và rejected input |
| Business rule lỗi trình bày trong form | `errors.reject` hoặc `rejectValue` rồi cùng view | Hiển thị lỗi đúng cấp field/global |
| POST đã được xử lý thành công | `redirect:/register/success` | Browser refresh GET, tránh gửi POST lần hai |
| Business conflict sau khi kiểm tra DB | Contract ứng dụng quyết định: form error/409/redirect | Tách lỗi người dùng và server |
| Hệ thống/backend thất bại | Error handling thích hợp, không xác nhận thành công | Không nuốt lỗi thành success |

HTTP 200 với HTML có lỗi form chỉ có nghĩa render view thành công, **không** nghĩa đã lưu hay chấp nhận nghiệp vụ. Nếu API khách hàng yêu cầu 400 hoặc 422, có thể chủ động thiết kế status đó; form MVC mặc định trả view thường dùng 200. Redirect ở nhánh lỗi có thể làm mất BindingResult nếu không triển khai đúng flash/error preservation; vì vậy demo không redirect khi lỗi.

Flash attribute lưu message ngắn sang request GET kế tiếp, không phải session dữ liệu bền hay nơi lưu password. Khi GET success được gọi trực tiếp, template vẫn nên an toàn khi `notice` vắng mặt.

## 15. Accessibility và an toàn nội dung

- Mỗi input có label rõ, text error cạnh đúng field, id/`aria-describedby` phù hợp.
- Kết hợp text với CSS/class, tránh chỉ dùng màu để báo lỗi.
- Có thể thêm error summary/link tới field và focus management cho form lớn, đặc biệt khi validation server-side.
- Dữ liệu do client nhập được escape khi hiển thị. `th:text`/`th:errors` an toàn hơn chèn unescaped HTML bằng `th:utext`.
- Không log `rejectedValue` chứa password, token, thẻ thanh toán hoặc PII; nên gắn code và field name nếu cần observability.
- Nếu dùng email hoặc tên trong flash message, cân nhắc quyền riêng tư. Ví dụ demo chọn thông điệp chung để không echo PII.
- HTML5 `required`/`min` hỗ trợ UX nhưng không thay validation server; attacker có thể gửi request thủ công.
- Nếu dùng Spring Security, hãy xác nhận CSRF token của form POST, quyền truy cập GET/POST và tránh cho phép sửa object ngoài quyền.

## 16. Lỗi hay gặp và cách debug

1. **GET không thêm form object:** template `th:object` không có target.
2. **GET/POST/template dùng ba tên khác nhau:** lỗi/giá trị không nối được.
3. **BindingResult đặt sai vị trí:** Spring không lấy đúng errors, có thể ném exception.
4. **Quên @Valid/validation dependency:** constraint không chạy như mong muốn.
5. **Tạo RegistrationForm mới trong nhánh POST lỗi:** xóa input và BindingResult.
6. **Redirect khi lỗi form mà không giữ flash state đúng:** người dùng mất error messages.
7. **Chỉ hiển thị generic error:** không biết ô nào cần sửa.
8. **Sai type conversion thành một message Min:** `abc` không phải số dưới 18.
9. **`th:value` tự đặt ghi đè `th:field`:** rejected value có thể không được tái hiện.
10. **Đổi input text sang type number rồi đòi browser hiển thị `abc`:** browser có thể bỏ giá trị invalid.
11. **Quên thông báo global:** `reject` lỗi object không xuất hiện cạnh field nào.
12. **Dùng @RestController để trả logical view name:** trình duyệt thấy chữ `registration` thay vì HTML.
13. **Bind JPA entity trực tiếp:** overposting và lộ trường không cho phép.
14. **Bỏ qua getSuppressedFields():** field bị từ chối không nhất thiết gây hasErrors.
15. **Không kiểm tra security/CSRF:** POST nhận 403 trước binder.
16. **Không test HTML:** model có lỗi nhưng template vẫn không hiển thị.
17. **Phát message lỗi chứa exception nội bộ:** lộ class, SQL hoặc dữ liệu client.
18. **Thực hiện side effect trước khi hasErrors():** lưu form sai dữ liệu.
19. **Thay đổi locale mà test cứng câu chữ:** test đổ vỡ không liên quan behavior.
20. **Không test multi-value params:** client gửi field lặp có thể có hành vi không mong muốn tùy binder.
21. **Form action hardcode sai context path:** POST vào URL khác với mapping.
22. **Cho rằng browser validation đủ:** curl và client tự viết vẫn gửi request sai.

## 17. Checklist kiểm tra

- [ ] GET hiển thị đúng template và model attribute tên `registration`.
- [ ] POST dùng `@Valid @ModelAttribute("registration")` và `BindingResult` ngay kế.
- [ ] DTO chỉ có trường client được phép nhập; không bind entity nhạy cảm.
- [ ] Có validation cho required, blank, range, date format và email syntax.
- [ ] Lỗi form giữ object/BindingResult, không gửi dữ liệu vào service.
- [ ] Template dùng `th:object`, `th:field`, `th:errors`, `th:errorclass`.
- [ ] Lỗi global được hiển thị trong khu vực dễ nhận biết.
- [ ] Lỗi conversion có thể giữ rejected input (phù hợp loại HTML input).
- [ ] Có test model BindingResult và HTML đã render.
- [ ] Valid POST redirect và dùng flash message phù hợp.
- [ ] Có CSRF token và quyền khi dùng Spring Security.
- [ ] Text/error/input được HTML-escape, không để lộ dữ liệu nhạy cảm.
- [ ] Resource bundles/message codes phù hợp locale nếu hỗ trợ đa ngôn ngữ.

## 18. Tự kiểm tra

**1.** Vì sao `BindingResult` phải đứng ngay sau `@Valid @ModelAttribute`?

**Đáp án:** Để Spring liên kết errors với đúng model argument. Đặt sai vị trí có thể dẫn đến lỗi resolve argument hoặc mất handling tại controller.

**2.** `age=abc` tạo lỗi gì khi bind vào `Integer`?

**Đáp án:** Lỗi conversion trong binding, thường mã `typeMismatch`. Nó khác `@Min` vì chưa có giá trị số.

**3.** `age=15` thì sao?

**Đáp án:** Convert thành Integer thành công rồi fail constraint `@Min(18)`.

**4.** Dùng gì để hiển thị lỗi field email?

**Đáp án:** `#fields.hasErrors('email')` để hỏi, `th:errors="*{email}"` để render messages, `th:errorclass` để thêm CSS class.

**5.** Global error hiển thị thế nào?

**Đáp án:** Dùng `errors.reject(...)` và `#fields.hasGlobalErrors()`/`#fields.globalErrors()` trong template.

**6.** Vì sao không tạo object mới khi POST lỗi?

**Đáp án:** Nó có thể ghi đè giá trị đã bind và làm mất rejected values/messages trong BindingResult.

**7.** Vì sao valid POST nên redirect?

**Đáp án:** Post/Redirect/Get đưa browser sang GET, refresh không gửi lại POST.

**8.** `hasErrors()==false` có bảo đảm không có field lạ không?

**Đáp án:** Không. Kiểm tra `getSuppressedFields()` nếu dùng allowlist và có chính sách từ chối trường lạ.

**9.** MockMvc test field error đã đủ chứng minh người dùng thấy lỗi chưa?

**Đáp án:** Chưa. Cần kiểm tra rendered HTML, field value và error text/class.

**10.** Nếu có CSRF filter, POST không token có thể trả gì trước controller?

**Đáp án:** Thường 403, tùy cấu hình. Đó là security filter failure, không phải Bean Validation của form.

## 19. Tài liệu tham khảo

- Spring Framework — `@ModelAttribute`: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html
- Spring Framework — MVC validation và BindingResult: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html
- Spring Framework — `WebDataBinder` / `@InitBinder`: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-initbinder.html
- Spring Framework — Type conversion/formatting: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/typeconversion.html
- Thymeleaf 3.1 + Spring integration, form binding và error messages: https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html
- Thymeleaf `#fields` API: https://www.thymeleaf.org/apidocs/thymeleaf-spring6/3.1.1.RELEASE/org/thymeleaf/spring6/expression/Fields.html
- Spring Framework — RedirectAttributes và FlashMap: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/redirecting-passing-data.html
- Spring Security — CSRF: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
- Jakarta Bean Validation: https://jakarta.ee/specifications/bean-validation/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-forms-006">Redirect, forward và mẫu Post/Redirect/Get</a>

<details>
<summary>Xem chi tiết</summary>

# Redirect, forward và mẫu Post/Redirect/Get trong Spring MVC

## 1. Vì sao cần phân biệt redirect, forward và Post/Redirect/Get?

Trong ứng dụng web dùng **Spring MVC** và các biểu mẫu HTML, sau khi xử lý một HTTP request, controller có thể:

1. Trả về một **view** để render ngay trong response hiện tại.
2. **Redirect**: yêu cầu trình duyệt gửi một request mới tới URL khác.
3. **Forward**: chuyển cùng request sang một tài nguyên/handler khác trong server qua Servlet `RequestDispatcher`.
4. Áp dụng **Post/Redirect/Get (PRG)**: xử lý dữ liệu của `POST`, sau đó redirect tới một `GET` để người dùng xem kết quả an toàn hơn khi refresh.

Ba hành vi này không tương đương. Nhầm lẫn có thể dẫn tới người dùng vô tình gửi biểu mẫu lần nữa, mất thông báo thành công, để lộ dữ liệu trên URL hoặc có các test trả HTTP status không như mong đợi.

Ví dụ người dùng mở form tạo sách tại `GET /books/new`, nhập tiêu đề rồi nhấn nút “Lưu”. Nếu handler `POST /books` ghi vào database rồi **trả HTML thành công trực tiếp cho POST**, nút Refresh có thể khiến trình duyệt cảnh báo resubmit form. PRG đổi bước hiển thị kết quả thành một GET riêng, ví dụ `GET /books/42`.

**Phạm vi:** Spring Web MVC **Servlet stack**, controller trả view (Thymeleaf/JSP) và browser form. Các ví dụ đầy đủ dùng **Java 17, Spring Boot 3.3.5, Spring Framework 6.1.x**. Khái niệm redirect của HTTP cũng liên quan REST API, nhưng đây không phải cơ chế điều hướng tự động của SPA JavaScript hoặc Spring WebFlux.

## 2. Ba luồng xử lý HTTP nhìn từ client

### 2.1. Render một view ngay

```text
Browser                    Spring MVC
   | GET /books/new              |
   |---------------------------->|
   |                             | controller return "books/new"
   | 200 OK + HTML               | -> ViewResolver -> Thymeleaf
   |<----------------------------|
   |
Address bar: /books/new
```

Không có chuyển hướng. Controller chỉ định **tên view** `books/new`; Thymeleaf render HTML. Trình duyệt vẫn hiển thị URL ban đầu.

Nếu controller xử lý `POST` và cũng trả `"books/result"` trực tiếp, response ấy thuộc request POST; refresh trang có thể dẫn đến trình duyệt đề nghị gửi lại POST.

### 2.2. Redirect: có hai request độc lập

```text
Browser                           Spring MVC
   | POST /books                        |
   |---------------------------------->|
   |                                    | validate + save
   | 302 Found                          |
   | Location: /books/42                |
   |<----------------------------------|
   |                                    |
   | GET /books/42                      |
   |---------------------------------->|
   |                                    | load + render detail view
   | 200 OK + HTML                      |
   |<----------------------------------|
   |
Address bar: /books/42
```

Controller có thể trả `"redirect:/books/42"`. Spring MVC xử lý prefix đặc biệt `redirect:`, tạo redirect view để trả status `3xx` và header `Location` cho browser. Browser quyết định có theo redirect và gửi request mới không; `curl` không tự theo redirect trừ khi yêu cầu `-L`.

**Một redirect không phải forward:** request GET mới có request parameters, headers, body và lifecycle riêng. Những object request-scoped, `Model` cũ, `BindingResult` cũ không tự động đi theo. Cần lưu dữ liệu cần thiết bằng URL params hoặc flash attributes tùy ý nghĩa.

### 2.3. Forward: cùng request được chuyển bên trong server

```text
Browser                          Spring MVC / Servlet
   | GET /books/forward-demo              |
   |------------------------------------->|
   |                                       | handler A
   |                                       | return "forward:/books/forward-target"
   |                                       | RequestDispatcher.forward(...)
   |                                       | handler B / target resource
   | 200 OK + HTML từ đích                 | (không HTTP 3xx)
   |<-------------------------------------|
   |
Address bar: /books/forward-demo
```

`forward:` trong Spring MVC tạo chuyển tiếp nội bộ bằng Servlet `RequestDispatcher.forward()` khi được một `UrlBasedViewResolver` phù hợp xử lý. Browser **không nhận Location redirect**, không tự gửi request mới và thanh địa chỉ vẫn hiển thị URL gốc.

Trong forward, request và response vẫn thuộc cùng vòng đời dispatch của Servlet. Các request attributes có thể được chia sẻ cho tài nguyên đích; bộ lọc/interceptor có thể có cách chạy khác tùy `DispatcherType.FORWARD`, mapping và container. Đây **không** là cách truy cập host ở bên ngoài bằng trình duyệt.

### So sánh ngắn

| Tiêu chí | Render view trực tiếp | Redirect | Forward |
| --- | --- | --- | --- |
| Trình duyệt tạo request mới? | Không | **Có**, nếu nó theo redirect | Không |
| HTTP response trung gian | `200` hoặc status của handler | `3xx` + `Location` | Không có `3xx` từ forward |
| URL trên thanh địa chỉ | Giữ URL gốc | Đổi sang URL đích | Giữ URL gốc |
| Request method đến đích | Không có request đích | Tùy `3xx` và client; PRG thường sang GET | Giữ request hiện tại (vd POST vẫn POST) |
| Request attributes | Có trong request hiện tại | Không tự mang theo | Có thể dùng lại |
| Model & flash data | Model render view ngay | URL attrs / flash attrs | Dữ liệu truyền qua request attributes/model tùy view |
| Tới domain khác | Không | Có thể, nếu cho phép | Không |
| Phù hợp sau POST thành công | Có thể gây resubmit | **Có, theo PRG** | Không thay được PRG |

## 3. Redirect trong Spring MVC: hai cách triển khai

### 3.1. Trả logical view name bắt đầu bằng redirect:

Controller dùng `@Controller` (không phải `@RestController`) có thể trả chuỗi:

```java
@PostMapping("/books")
public String create() {
    // Ghi dữ liệu thành công, đã nhận được ID 42.
    return "redirect:/books/42";
}
```

Đây là **method minh họa**, cần đặt trong class controller với annotations/imports. `redirect:` là **view name prefix**, không phải text để gửi làm HTTP response body. Nếu method thuộc `@RestController`, một `String` thông thường được xử lý như **response body**, nên client có thể thấy đúng chữ `redirect:/books/42` thay vì được điều hướng.

Đối với `redirect:/books/42`, `RedirectView` thường dùng HTTP `302 Found` theo mặc định tương thích HTTP/1.0 của Spring MVC 6.1. Browser hiện đại thường làm GET khi theo `302` sau POST, nhưng nếu cần semantics **rõ ràng bắt buộc GET**, HTTP `303 See Other` là lựa chọn phù hợp hơn (xem mục 4).

Spring MVC nhận diện prefix `redirect:` qua view resolution infrastructure, nên tên này **khác** `"books/42"` (được coi là tên template). URL tương đối, dấu `/`, context path và reverse proxy có thể ảnh hưởng URL Location cuối cùng. Hãy kiểm tra behavior với `server.servlet.context-path` nếu ứng dụng được triển khai dưới context khác `/`.

### 3.2. Trả RedirectView khi cần tùy chỉnh status

```java
@GetMapping("/go")
public RedirectView go() {
    RedirectView view = new RedirectView("/books/42");
    view.setContextRelative(true);
    view.setHttp10Compatible(false);
    return view;
}
```

Đây cũng là snippet nằm trong controller, với import `org.springframework.web.servlet.view.RedirectView`. Cờ `http10Compatible=false` làm `RedirectView` dùng **`303 See Other` mặc định** trong đường HTTP/1.1 thay vì dựa vào `sendRedirect` với 302. Cũng có thể thiết lập `setStatusCode(HttpStatus.SEE_OTHER)` trong cấu hình thích hợp.

Đối với PRG cần `303`, logic điển hình là sau POST lưu dữ liệu thành công rồi trả `RedirectView` hướng tới URL GET. Tuy nhiên không nên trộn tùy ý giữa `Model` và `RedirectView`; nên dùng `RedirectAttributes` để kiểm soát những giá trị được xuất lên URL/flash.

### 3.3. Với REST API không render view

Đối với một endpoint JSON thuần, HTTP redirect vẫn có thể trả bằng `ResponseEntity<Void>`:

```java
return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .location(URI.create("/api/orders/42"))
        .build();
```

Đây là snippet trong method trả `ResponseEntity<Void>`, cần imports `HttpStatus`, `ResponseEntity` và `java.net.URI`. `303` cho client biết nên dùng GET để truy cập representation khác. Không mặc định mọi `fetch()`/HTTP client sẽ tự điều hướng UI như browser; client JavaScript có cơ chế redirect/fetch riêng. REST API tạo resource cũng thường trả `201 Created` và `Location`; lựa chọn status phải theo contract ứng dụng, không ép mọi API viết JSON sang PRG.

## 4. Hiểu đúng 301, 302, 303, 307 và 308

| HTTP status | Ngữ nghĩa chính | Phương thức của request tiếp theo |
| --- | --- | --- |
| `301 Moved Permanently` | Resource được chuyển lâu dài | Với POST, một số user agent **có thể đổi sang GET** theo quy tắc HTTP |
| `302 Found` | Redirect tạm thời | Với POST, browser thường đổi GET, nhưng không nên dựa vào đó như bảo đảm phương thức |
| `303 See Other` | Xem resource/confirmation khác sau request trước | **Client thực hiện retrieval request (thường GET)**; phù hợp PRG |
| `307 Temporary Redirect` | Redirect tạm thời, bảo toàn method và body | **Không đổi method** theo semantics HTTP |
| `308 Permanent Redirect` | Redirect lâu dài, bảo toàn method và body | **Không đổi method** theo semantics HTTP |

**Với POST ghi dữ liệu, không dùng 307/308 làm cơ chế PRG**: client sẽ gửi POST đến URL mới và vẫn có nguy cơ thực hiện write ngoài ý định. `302` là lựa chọn mặc định phổ biến trong Spring MVC, nhưng `303` diễn tả ý định POST thành công → xem GET rõ ràng hơn.

Với URL GET cũ được chuyển lâu dài sang GET mới, `301/308` có thể phù hợp, nhưng việc cache redirect và bảo toàn method cần được cân nhắc. Không chọn mã redirect chỉ dựa trên số hàng `3xx`.

**Không được suy luận:** “HTTP 302 chắc chắn luôn GET”. HTTP client và các trường hợp method khác nhau có hành vi khác. Contract tests nên xác nhận server gửi đúng `Location` và status; browser/integration tests riêng nên xác nhận user agent thực sự theo đường bạn mong muốn.

## 5. Post/Redirect/Get (PRG): giải quyết form resubmission

### 5.1. Vấn đề khi trả view trực tiếp từ POST

Luồng **không có PRG**:

```text
GET /books/new -> form
POST /books   -> lưu sách và trả HTML thành công ngay
Refresh       -> có thể gửi lại POST /books
                  -> tạo bản ghi trùng / thanh toán trùng / xử lý lần nữa
```

Nếu form chỉ trình bày thông tin không thay đổi server thì rủi ro thấp, nhưng với tạo đơn, chuyển tiền, đặt hàng, đăng ký tài khoản, việc gửi lại có thể gây hậu quả. Browser hiện đại đôi khi cảnh báo trước resubmit, nhưng không được coi cảnh báo UI là rào chắn nghiệp vụ.

### 5.2. PRG đổi trang cuối thành GET

```text
1. GET /books/new
   -> HTML form

2. POST /books với title=Clean+Code
   -> validate
   -> nếu hợp lệ: lưu sách ID 42
   -> HTTP 302 (hoặc 303), Location: /books/42

3. GET /books/42
   -> lấy sách vừa lưu
   -> render view chi tiết, HTTP 200

4. Refresh
   -> GET /books/42
   -> không chạy lại POST /books
```

Đây là **mẫu tương tác hai bước cho thao tác write thành công**. URL GET cuối cùng có thể lưu bookmark, chia sẻ, refresh, và về mặt HTTP là truy vấn một resource đã có.

**PRG không bảo đảm exactly-once processing.** Người dùng có thể double-click gửi hai POST trước khi nhận redirect; proxy/client retry hoặc timeout trước redirect cũng có thể tạo hai yêu cầu. Với giao dịch quan trọng cần **idempotency key**, unique constraints, transactional guards, optimistic locking hoặc các biện pháp tương ứng. PRG chủ yếu giải quyết UX và trường hợp refresh form response sau POST, không thay thế cơ chế chống xử lý trùng ở server.

### 5.3. Thành công redirect, thất bại validation thì render lại form

Quy tắc thường dùng:

- **POST hợp lệ:** save → redirect tới GET.
- **POST không hợp lệ:** giữ `BindingResult` và object form trong **cùng request**, trả lại tên view form để hiển thị lỗi; không cần redirect.
- **GET chi tiết:** đọc dữ liệu mới từ service/database rồi render; không trông đợi toàn bộ form object từ request POST vẫn tồn tại.

Vì `BindingResult` là dữ liệu request/model, tự redirect khi validation fail sẽ làm mất lỗi trừ khi bạn **chủ động** vận chuyển form và binding errors qua flash/session. Cách trực tiếp là `return "books/new"` khi có errors.

## 6. RedirectAttributes: dữ liệu trên URL và flash attributes

Spring MVC hỗ trợ `org.springframework.web.servlet.mvc.support.RedirectAttributes` để kiểm soát dữ liệu đi theo redirect. Có hai nhánh **phải phân biệt**:

```java
redirectAttributes.addAttribute("id", book.id());
redirectAttributes.addFlashAttribute(
        "success", "Đã tạo sách thành công");
return "redirect:/books/{id}";
```

Đây là đoạn trong POST handler, không phải chương trình Java độc lập.

### 6.1. addAttribute: URL path/query

`addAttribute("id", 42)` có thể điền placeholder `{id}` của `redirect:/books/{id}`, tạo Location `/books/42`. Nếu còn primitive/string redirect attributes không dùng để expand URL, Spring có thể biến chúng thành **query parameters**.

Ví dụ:

```java
redirectAttributes.addAttribute("tab", "details");
return "redirect:/books/{id}";
```

Nếu `id=42` đã có và cả `tab` được thêm, URL có thể thành `/books/42?tab=details`. URL-encoded query phù hợp với dữ liệu **không nhạy cảm** có ý nghĩa điều hướng. Không dùng query params cho password, token, thông tin thẻ, session secrets hoặc thông báo nội bộ không muốn lưu browser history/proxy logs.

Lưu ý Spring MVC có hành vi lịch sử về việc đưa **default model attributes** vào URL khi redirect: những giá trị đơn giản trong model có thể thành query parameters nếu không kiểm soát. Khai báo `RedirectAttributes` và chỉ add những trường cần thiết giúp giảm rủi ro. Với ứng dụng phức tạp có thể xét tùy chọn `ignoreDefaultModelOnRedirect` trong `RequestMappingHandlerAdapter`, nhưng không nên sửa cấu hình toàn cục nếu chưa kiểm tra tác động.

### 6.2. addFlashAttribute: dữ liệu tạm cho GET tiếp theo

`addFlashAttribute("success","Đã tạo sách")` lưu tạm thông báo thành công vào output flash map. Sau redirect, Spring đặt dữ liệu flash vào model của request đích khi khớp; template có thể đọc `${success}`.

Ưu điểm:

- Không để thông báo trong URL, browser history và log truy cập.
- Không phải gửi cả dữ liệu vừa tạo qua query string.
- Thích hợp cho thông báo một lần kiểu “Đã lưu”, “Cập nhật thành công”.

Giới hạn cần nhớ:

- Flash attributes **thường được lưu tạm trong HTTP session** và được lấy ra khi request đích khớp; không phải cơ sở dữ liệu.
- Sau khi được tiêu thụ, message không có bảo đảm tồn tại khi refresh lần thứ hai hoặc mở bookmark về sau.
- Spring dùng `FlashMap` / `FlashMapManager`. RedirectView có thể “đóng dấu” path/query target lên flash map để giảm việc request khác lấy nhầm, nhưng **không loại bỏ hoàn toàn race conditions** nếu có request đồng thời ở nhiều tab hoặc polling.
- Không lưu đối tượng nghiệp vụ lớn, mật khẩu, token hoặc kết quả cần độ bền vững cao trong flash.
- Với session phân tán/multiple instances, cấu hình lưu session phải phù hợp; flash không thay database.
- Nếu client tắt/mất session cookie hoặc redirect tới môi trường khác không chia sẻ session, flash message có thể không đến đích.

### 6.3. Flash không phải session attribute dài hạn

`@SessionAttributes` và `HttpSession` dùng cho bài toán state qua nhiều request; flash dùng cho trao đổi tạm của một redirect. Nếu muốn thông báo bền vững hoặc kiểm tra quyền resource, GET đích phải truy cập service/database. Không coi flash là chứng cứ rằng người dùng đã được phép xem resource.

## 7. Ví dụ Spring Boot hoàn chỉnh: form, redirect, flash và forward

Ví dụ này có một ứng dụng Spring MVC hoạt động với Java 17 và Maven, gồm:

- `GET /books/new`: hiển thị form HTML.
- `POST /books`: validate title; thành công tạo sách trong bộ nhớ và trả `redirect:/books/{id}`.
- `GET /books/{id}`: render trang chi tiết, hiển thị flash message khi vừa redirect.
- `GET /books/forward-demo`: đặt một request attribute rồi forward nội bộ tới `/books/forward-target`.
- `GET /books/forward-target`: render view dựa trên request attribute.

Demo dùng in-memory `ConcurrentHashMap` và `AtomicLong` chỉ để chạy độc lập, **không phải persistence production**. Với hệ thống thật phải dùng service/repository, transactions, authorization, CSRF protection (nếu có web login/session) và idempotency cho thao tác quan trọng.

### 7.1. Cấu trúc project

```text
prg-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/example/prg/
    │   │   ├── PrgDemoApplication.java
    │   │   ├── BookCatalog.java
    │   │   ├── BookForm.java
    │   │   └── BookController.java
    │   └── resources/templates/books/
    │       ├── new.html
    │       ├── detail.html
    │       └── forward-target.html
    └── test/java/example/prg/
        └── BookControllerTests.java
```

Mỗi public Java class phải được lưu đúng tên file và package như cây thư mục.

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
    <groupId>example</groupId>
    <artifactId>prg-demo</artifactId>
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
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
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
```

**`src/main/java/example/prg/PrgDemoApplication.java`**

```java
package example.prg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PrgDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrgDemoApplication.class, args);
    }
}
```

### 7.2. Catalog lưu trong bộ nhớ

**`BookCatalog.java`**

```java
package example.prg;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class BookCatalog {
    public record Book(long id, String title) {}

    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, Book> books = new ConcurrentHashMap<>();

    public Book create(String title) {
        long id = sequence.incrementAndGet();
        Book book = new Book(id, title);
        books.put(id, book);
        return book;
    }

    public Optional<Book> find(long id) {
        return Optional.ofNullable(books.get(id));
    }
}
```

`AtomicLong` tạo ID tăng theo từng instance; `ConcurrentHashMap` an toàn cho truy cập đồng thời trong phạm vi demo. Khi restart server, dữ liệu biến mất. Đừng dùng cách này để triển khai đơn hàng thật: cần lưu bền vững, kiểm soát concurrency, transaction và quy tắc duplicate/idempotency.

### 7.3. Form DTO với Bean Validation

**`BookForm.java`**

```java
package example.prg;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BookForm {
    @NotBlank
    @Size(max = 80)
    private String title;

    public BookForm() {}

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
```

Đây là **input DTO** riêng của form, không phải database entity. `@NotBlank` từ chối chuỗi rỗng hoặc toàn whitespace, `@Size(max=80)` đặt giới hạn. `@Valid @ModelAttribute` sẽ chạy validation khi POST được bind, và `BindingResult` cho controller đọc các lỗi.

### 7.4. Controller: POST hợp lệ redirect, POST sai render lại form

**`BookController.java`**

```java
package example.prg;

import example.prg.BookCatalog.Book;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/books")
public class BookController {

    private final BookCatalog catalog;

    public BookController(BookCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("bookForm", new BookForm());
        return "books/new";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("bookForm") BookForm form,
            BindingResult errors,
            RedirectAttributes redirectAttributes) {

        if (errors.hasErrors()) {
            return "books/new";
        }

        Book book = catalog.create(form.getTitle().strip());

        redirectAttributes.addAttribute("id", book.id());
        redirectAttributes.addFlashAttribute(
                "success", "Đã tạo sách thành công");

        return "redirect:/books/{id}";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        Book book = catalog.find(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Book not found"));
        model.addAttribute("book", book);
        return "books/detail";
    }

    @GetMapping("/forward-demo")
    public String forwardDemo(HttpServletRequest request) {
        request.setAttribute("forwardMarker", "from-first-handler");
        return "forward:/books/forward-target";
    }

    @GetMapping("/forward-target")
    public String forwardTarget(
            HttpServletRequest request, Model model) {
        Object marker = request.getAttribute("forwardMarker");
        model.addAttribute("marker",
                marker == null ? "none" : marker);
        return "books/forward-target";
    }
}
```

Các điểm cần quan sát trong file:

1. `@Controller` (không phải `@RestController`) để `String` được hiểu là **view name**.
2. `@Valid @ModelAttribute("bookForm")` nhận dữ liệu form, không yêu cầu JSON body. `BindingResult` đứng **ngay sau** đối tượng cần validate.
3. Khi lỗi, controller trả `"books/new"` trong **POST hiện tại** để Thymeleaf lấy `BindingResult` và hiển thị lỗi. Không tạo sách và không redirect.
4. Khi hợp lệ, `catalog.create` chạy **một lần trong request POST**, tạo ID.
5. `addAttribute("id", id)` phục vụ placeholder `{id}`; `addFlashAttribute("success",...)` giữ notification một lần.
6. `"redirect:/books/{id}"` tạo 3xx + Location; browser theo đến `GET /books/{id}`.
7. `detail` nạp lại dữ liệu bằng ID, không lấy Book từ flash. Flash `success` được Spring tự đưa vào model của GET tương ứng.
8. `forwardDemo` đặt request attribute và `forward:` chuyển cùng request tới handler thứ hai; `forwardTarget` nhận `marker` rồi render view.
9. Nếu ID không có, `ResponseStatusException` cho phản hồi `404` theo cấu hình Spring Boot. Template `detail` không được render với object giả.
10. URL `/books/new` và `/books/forward-demo` cụ thể hơn pattern `/books/{id}`; Spring chọn mapping phù hợp, không cần viết điều kiện `id` thủ công cho các path cố định.

Trong hệ thống thật, `@ControllerAdvice` hoặc error view có thể được dùng để trình bày `404` nhất quán; điều này nằm ngoài trọng tâm PRG.

### 7.5. Template form Thymeleaf

**`src/main/resources/templates/books/new.html`**

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Tạo sách</title>
</head>
<body>
    <h1>Tạo sách</h1>
    <form th:action="@{/books}" th:object="${bookForm}"
          method="post">
        <label for="title">Tiêu đề</label>
        <input id="title" type="text" th:field="*{title}"
               maxlength="80" required>
        <p th:if="${#fields.hasErrors('title')}"
           th:errors="*{title}">Lỗi tiêu đề</p>
        <button type="submit">Lưu</button>
    </form>
</body>
</html>
```

Các biểu thức `th:object="${bookForm}"` và `th:field="*{title}"` kết nối template với model/form DTO; Spring MVC + Thymeleaf có tích hợp lấy BindingResult. HTML `required` và `maxlength` hỗ trợ trải nghiệm người dùng, **không thay thế** Bean Validation phía server (client có thể bỏ qua mọi kiểm tra HTML).

### 7.6. Template chi tiết và flash message

**`src/main/resources/templates/books/detail.html`**

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Chi tiết sách</title>
</head>
<body>
    <p th:if="${success}" th:text="${success}">
        Thông báo
    </p>
    <h1 th:text="${book.title}">Tiêu đề sách</h1>
    <p>
        ID: <span th:text="${book.id}">0</span>
    </p>
    <a th:href="@{/books/new}">Tạo sách khác</a>
</body>
</html>
```

Khi vừa redirect sau POST hợp lệ, GET đầu tiên thường thấy flash `success`. Khi refresh GET hoặc truy cập link chi tiết trực tiếp, phần thông báo không bắt buộc xuất hiện vì flash đã được tiêu thụ; thông tin sách vẫn đến từ `catalog.find(id)`.

Với Thymeleaf, `th:text` escape HTML output thông thường; không dùng `th:utext` cho nội dung do user nhập trừ khi đã có chiến lược sanitize phù hợp. Đây là một lợi ích của template escaping, nhưng không thay authorization hay các quy tắc bảo mật.

### 7.7. Template minh họa forward

**`src/main/resources/templates/books/forward-target.html`**

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Forward target</title>
</head>
<body>
    <h1>Forward đã tới đích</h1>
    <p th:text="${marker}">none</p>
</body>
</html>
```

Mở `GET /books/forward-demo`. Controller ban đầu đặt request attribute `forwardMarker`. Đường `forward:/books/forward-target` thực hiện chuyển tiếp nội bộ; handler đích đọc attribute và đưa `marker` vào view. Browser vẫn hiển thị URL `/books/forward-demo`; không có Location 302.

**Tại sao không dùng forward sau POST tạo sách?** Forward **giữ method POST**, khiến trang cuối vẫn là kết quả của POST ban đầu, không tạo request GET mới. Refresh có thể resubmit POST hoặc browser cảnh báo. PRG phải dùng redirect.

## 8. Kiểm tra thủ công bằng browser và curl

Từ thư mục `prg-demo`, cần cài Java 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Hai lệnh là các bước **chạy tuần tự**; `mvn test` kết thúc rồi mới chạy `mvn spring-boot:run`. Ví dụ mặc định sử dụng `localhost:8080` nếu không thay `server.port`.

### 8.1. Xem form

Truy cập browser:

```text
http://localhost:8080/books/new
```

Dự kiến response `200 OK` với form có trường `title`. Chưa có database nên form không hiển thị danh sách sách; nó chỉ tạo một bản ghi in-memory khi POST.

### 8.2. POST hợp lệ: quan sát redirect mà không tự follow

```bash
curl -i -X POST http://localhost:8080/books \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'title=Clean Code'
```

Dự kiến có:

```http
HTTP/1.1 302
Location: http://localhost:8080/books/1
```

Spring/container có thể ghi `Location` thành URL tuyệt đối hoặc tương đối theo cấu hình, reverse proxy, context path. Phần chắc chắn cần test ở mức hợp đồng là **3xx và đích `/books/1`**, không phải bắt buộc host/định dạng Location y nguyên trong mọi môi trường. Với instance sạch, ID đầu tiên là `1`; nếu trước đó đã tạo sách, ID sẽ lớn hơn.

Browser tự follow redirect khi submit form. `curl` **mặc định không theo redirect**; dùng `curl -L` nếu muốn follow và quan sát response cuối. Khi POST qua curl, tránh ép `-X POST` kết hợp `-L` nếu đang kiểm tra việc đổi method, vì option `-X` khiến cách hiển thị/gửi method khác hành vi tự động mong muốn. Dùng:

```bash
curl -i -L \
  -c cookies.txt -b cookies.txt \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'title=Domain-Driven Design' \
  http://localhost:8080/books
```

`-c/-b` giúp lưu/gửi session cookie giữa các requests do curl theo redirect, cần thiết nếu muốn nhận flash attribute qua session. Nếu nghi ngờ chuyển POST sang GET không đúng với client/tool, kiểm tra redirect method bằng `curl -v` và status; với PRG nghiêm ngặt, dùng 303 như mục 10.

### 8.3. GET trang chi tiết

Sau khi xác định ID được tạo, gọi:

```bash
curl -i http://localhost:8080/books/1
```

Response `200 OK` có HTML chứa sách ID 1 **nếu sách đã được tạo trong instance hiện tại**. Nếu chưa tạo hoặc server restart, `/books/1` có thể trả `404`, đúng với in-memory catalog demo.

### 8.4. POST không hợp lệ

```bash
curl -i -X POST http://localhost:8080/books \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'title=   '
```

Dự kiến HTTP `200 OK` với view form có thông báo lỗi validation của `@NotBlank`, **không** có header `Location` redirect và **không** có bản ghi mới. Mẫu này có thể không gửi status `400` vì form HTML truyền thống thường render lỗi trong cùng trang `200`; với REST JSON API thì thường nên trả `400` theo contract.

### 8.5. Forward nội bộ

```bash
curl -i http://localhost:8080/books/forward-demo
```

Response dự kiến `200 OK`, không có redirect 3xx; HTML có chữ `from-first-handler`. `curl` không cần `-L` vì request không phát sinh redirect ra client. Thanh địa chỉ browser giữ `/books/forward-demo`.

## 9. Kiểm thử với MockMvc

`MockMvc` kiểm tra status, view name, redirect location, flash map và forward dispatch mà không cần một HTTP server port thật. Với ví dụ Thymeleaf có thể dùng `@SpringBootTest` để nạp đủ application context, renderer và bean `BookCatalog`.

**`src/test/java/example/prg/BookControllerTests.java`**

```java
package example.prg;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerTests {

    @Autowired
    MockMvc mvc;

    @Test
    void getFormRendersView() throws Exception {
        mvc.perform(get("/books/new"))
           .andExpect(status().isOk())
           .andExpect(view().name("books/new"))
           .andExpect(model().attributeExists("bookForm"));
    }

    @Test
    void validPostRedirectsAndStoresFlashMessage() throws Exception {
        mvc.perform(post("/books")
                    .param("title", "Clean Code"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("/books/*"))
           .andExpect(flash().attribute(
                    "success", "Đã tạo sách thành công"));
    }

    @Test
    void invalidPostRendersFormWithBindingError() throws Exception {
        mvc.perform(post("/books")
                    .param("title", "   "))
           .andExpect(status().isOk())
           .andExpect(view().name("books/new"))
           .andExpect(model().attributeHasFieldErrors(
                    "bookForm", "title"));
    }

    @Test
    void forwardDoesNotReturnRedirectStatus() throws Exception {
        mvc.perform(get("/books/forward-demo"))
           .andExpect(status().isOk())
           .andExpect(forwardedUrl("/books/forward-target"));
    }

    @Test
    void missingBookReturnsNotFound() throws Exception {
        mvc.perform(get("/books/999999999"))
           .andExpect(status().isNotFound());
    }
}
```

Run:

```bash
mvn test
```

Nếu tất cả 5 test methods pass, Maven kết thúc thành công với exit code `0` và có thông tin đại ý `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`. Thứ tự log, thời gian và cấu trúc report cụ thể phụ thuộc môi trường.

**Lưu ý về MockMvc forward:** Matcher `forwardedUrl(...)` kiểm tra URL mà MVC đã chuyển tiếp, **không đồng nghĩa** nó tự chạy lại toàn bộ request tới controller B giống một browser và servlet container thật. Nếu muốn kiểm tra HTML do target thực sự render qua forward, chạy integration test với embedded server `RANDOM_PORT` và HTTP client/browser.

**Lưu ý về flash:** Assertion `flash().attribute(...)` kiểm tra output flash map của response redirect. Để kiểm tra message xuất hiện trong request GET tiếp theo, test phải mô phỏng redirect **và duy trì flash/session đúng cách**, không tự cho rằng `MockMvc` đã follow redirect như browser. Trong hệ thống quan trọng, bổ sung một integration test giữa hai request để bảo vệ flash behavior.

## 10. Cách dùng 303 See Other rõ ràng cho PRG

Trong chương trình mẫu, `return "redirect:/books/{id}"` thường dẫn đến 302. Nếu API cần bảo đảm chuẩn HTTP method switching rõ ràng, sau khi lưu thành công có thể trả `RedirectView` cấu hình 303.

Snippet **thay thế logic sau khi tạo sách** trong POST handler:

```java
RedirectView redirect =
        new RedirectView("/books/" + book.id());
redirect.setContextRelative(true);
redirect.setHttp10Compatible(false);
redirect.setExposeModelAttributes(false);
return redirect;
```

Đoạn này cần thay **return type của method từ `String` sang `RedirectView` hoặc `View`**, và import `org.springframework.web.servlet.view.RedirectView`. Đây không phải một patch có thể thêm bên dưới `return "redirect:..."` mà không chỉnh method; cần làm theo phương án riêng. `RedirectAttributes.addFlashAttribute(...)` vẫn được xử lý khi return là một `RedirectView` thích hợp do Spring MVC, nhưng sau khi tắt expose model, URL phải được dựng rõ như ở snippet.

Một lựa chọn API-oriented khác là trả `ResponseEntity.status(HttpStatus.SEE_OTHER).location(...).build()`. Nếu bạn đang phục vụ HTML form với view + flash, `RedirectView` thường thuận tiện hơn. **Đừng đổi 302 sang 307/308** khi mục tiêu là GET sau POST.

## 11. Redirect và forward: lỗi phổ biến

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Browser hiện chữ `redirect:/books/1` | Controller là `@RestController`/`@ResponseBody` | Dùng `@Controller` trả view name, hoặc trả HTTP redirect rõ bằng `ResponseEntity` |
| Refresh tạo record thứ hai | POST trả view ngay hoặc client gửi lại POST | Dùng PRG; thêm idempotency/transaction/unique constraint cho logic quan trọng |
| GET sau redirect không có dữ liệu từ POST | Redirect là request mới | GET tra database/service bằng ID; dữ liệu hiển thị tạm dùng flash |
| Flash message không hiện | Client không follow Location, không giữ session cookie, flash đã tiêu thụ | Test HTTP client/session và đường redirect |
| Flash message đôi khi biến mất giữa các tab | FlashMap có giới hạn về concurrency/race | Không dùng flash cho state bền vững, giảm request cạnh tranh |
| Form lỗi validation mất sau redirect | `BindingResult` thuộc request cũ | Khi invalid, render lại form; nếu phải redirect, truyền binding errors một cách có chủ đích |
| Thông tin nhạy cảm xuất hiện trong Location | `addAttribute` thêm query param vào redirect | Dùng ID an toàn, flash cho message tạm, không đặt secrets trên URL |
| Redirect tới URL sai khi dùng context path | URL tương đối/absolute hoặc proxy config sai | Kiểm tra context path, forwarded headers, `RedirectView.contextRelative` |
| Return `forward:` không chạy như mong đợi | View resolver không hỗ trợ prefix hoặc resource không dispatch được | Kiểm tra `UrlBasedViewResolver` và mapping Servlet đích |
| Refresh vẫn là POST sau forward | Forward giữ request/method | Dùng redirect cho PRG |
| Redirect 307/308 giữ POST ở destination | Hai status giữ method/body | Dùng 303 hoặc 302 tùy yêu cầu PRG |
| Browser báo redirect loop | A redirect tới B và B redirect về A | Kiểm tra auth guards/mapping, Location chain và cookie |
| Trang chi tiết không thấy flash sau bookmark/refresh | Flash chỉ dùng một lần | Lấy dữ liệu chính từ service; message chỉ là notification |
| Open redirect sang domain lạ | Đưa trực tiếp `next`/`returnUrl` của client vào `redirect:` | Allowlist đích nội bộ, xác minh URI và chuẩn hóa |
| POST thành công nhưng GET trả 404 | Chưa commit dữ liệu, read-after-write delay, sai ID, mất memory state | Kiểm tra transactions, consistency, persistence, đường Location |
| Spring Security báo 403 khi submit form | CSRF token thiếu hoặc không đúng | Bật CSRF đúng policy, đưa token vào HTML form và test với CSRF |

### Dùng POST/Redirect/Get với Spring Security

Trong project mẫu không thêm `spring-boot-starter-security` nên không có CSRF filter do Spring Security tự cài. Trong ứng dụng có đăng nhập bằng session/cookie và Spring Security, POST thay đổi trạng thái thường được bảo vệ bằng **CSRF token**. Khi dùng Thymeleaf với Spring Security tích hợp, Spring có thể hỗ trợ chèn token vào form; vẫn cần kiểm tra markup thực và test `MockMvc` có `csrf()` tương ứng. Không tắt CSRF chỉ để làm cho ví dụ PRG chạy.

**PRG không thay authorization**: GET `/books/{id}` phải kiểm tra quyền đối với sách nếu đó là tài nguyên riêng tư. Thông báo flash “đã lưu” không chứng minh người đang GET có quyền xem nội dung.

### Open redirect: rủi ro bảo mật quan trọng

Không làm như sau:

```java
// PHẢN VÍ DỤ: redirect đến URL client tự gửi vào mà chưa xác minh.
@GetMapping("/go")
public String unsafe(@RequestParam String next) {
    return "redirect:" + next;
}
```

Kẻ tấn công có thể dựng đường dẫn redirect đến trang giả mạo. Nếu phải nhận đích từ client, xác minh đích theo **allowlist** host/path, chỉ cho đường nội bộ đáng tin, chuẩn hóa và từ chối `//external.example`, scheme bất thường, URL encoding nhiều lần hoặc biến thể bypass. Tốt nhất lưu server-controlled destination key rồi map tới URL cố định, thay vì nhận raw target URL.

## 12. Phân biệt PRG với idempotency và transaction

PRG tạo request GET sau POST, nhưng trong hệ thống phân tán **POST có thể chạy hai lần trước redirect**. Một luồng thanh toán cần nhiều lớp bảo vệ:

```text
Browser -> POST /payments (Idempotency-Key: unique-operation-id)
             |
             v
      Check existing operation / uniqueness
             |
        +----+---------+
        |              |
        v              v
   first request    duplicate request
   transactional    return prior result
   write                 |
        |----------------|
        v
   303 See Other
   Location: /payments/{id}
        |
        v
   GET /payments/{id}
```

Mô hình này chỉ là ý tưởng; không có code thanh toán runnable ở bài. Key phải có scope theo principal/operation, lưu bền vững đủ lâu, xử lý races và consistent replay. Nếu client gửi hai request có body khác nhau cùng key, server cần quy tắc báo lỗi phù hợp. Unique constraint ở database thường bảo vệ invariant tốt hơn check-then-insert không có transaction.

Đối với form CRUD bình thường, PRG + unique constraint có thể đủ. Với thanh toán, đặt vé, chuyển tiền, order placement, cần idempotency key và giao dịch đúng nghiệp vụ. Không nhầm “refresh GET không resubmit POST” với “server đảm bảo không bao giờ xử lý POST trùng”.

## 13. Chọn cách nào cho từng yêu cầu?

| Tình huống | Cách nên dùng |
| --- | --- |
| GET trang HTML hoặc hiển thị lỗi form ngay | Trả view name thông thường |
| POST biểu mẫu hợp lệ, tạo resource và chuyển sang trang chi tiết | **PRG: redirect** |
| POST biểu mẫu không hợp lệ và cần hiển thị lỗi binding | Trả lại cùng view, giữ `BindingResult` |
| Cần thông báo thành công một lần sau redirect | `RedirectAttributes.addFlashAttribute` |
| Cần ID hoặc filter hiển thị công khai ở URL | `RedirectAttributes.addAttribute` |
| Cần chuyển tiếp cùng request sang servlet/handler nội bộ | `forward:...` khi thật sự cần |
| Cần trình duyệt chắc chắn dùng GET sau write | HTTP **303 See Other** |
| Cần redirect tạm nhưng bảo toàn POST method/body | `307` cho yêu cầu chuyên biệt; **không dùng cho PRG** |
| Cần chuyển URL GET cũ sang URL mới lâu dài | Cân nhắc `301/308` và ảnh hưởng cache/method |
| REST JSON tạo resource rồi trả JSON | `201 Created` + `Location` thường phù hợp |
| Cần chống POST trùng nghiêm ngặt | Idempotency/DB constraint/transaction **ngoài PRG** |

## 14. Bài tập tự kiểm tra

1. Khi handler `@Controller` trả `"redirect:/books/42"`, browser nhận gì? Nó có tạo request mới không?
2. Khi handler trả `"forward:/books/forward-target"`, URL trên thanh địa chỉ và HTTP method của request đích thay đổi thế nào?
3. Vì sao PRG giảm rủi ro resubmit sau refresh nhưng không thay idempotency key?
4. Trong `RedirectAttributes`, `addAttribute` khác `addFlashAttribute` ở vị trí và thời gian tồn tại dữ liệu?
5. Vì sao validation fail nên trả lại view form thay vì mặc định redirect?
6. Vì sao `303` diễn đạt mục tiêu PRG tốt hơn `307`?
7. Một method nằm trong `@RestController` trả string `"redirect:/books/42"`. Điều gì có thể xảy ra?
8. Nếu flash success message biến mất sau refresh GET, đây có phải lỗi cần sửa bằng cách lưu flash lâu dài không?
9. Khi controller dùng `return "redirect:/books/{id}"`, `addAttribute("id",42)` có mục đích gì?
10. Nếu client gửi `?next=https://untrusted.example` và server ghép vào redirect URL, rủi ro gì?
11. Tại sao `GET /books/42` nên tự tải thông tin sách từ service thay vì phụ thuộc `BookForm` của POST?
12. Khi test `forwardedUrl` bằng `MockMvc`, điều đó có khẳng định toàn bộ HTML đích đã được render qua Servlet container thật không?

**Gợi ý trả lời:** (1) HTTP 3xx + Location, client theo tạo request mới. (2) URL browser không đổi, cùng request/method được chuyển nội bộ. (3) Hai POST vẫn có thể đến trước redirect. (4) Attribute dùng URL/path/query, flash ở vùng lưu tạm (thường session) cho request đích. (5) BindingResult còn trong request hiện tại. (6) 303 yêu cầu retrieval/GET, 307 giữ method. (7) Có thể trả chuỗi làm body thay vì redirect. (8) Không, flash thiết kế tạm một lần. (9) Điền placeholder `{id}`. (10) Open redirect/phishing. (11) GET là contract độc lập/bookmarkable. (12) Không; cần integration test thực cho full forward dispatch.

## 15. Tài liệu tham khảo

- [Spring Framework 6.1 — Redirect Attributes](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/mvc-controller/ann-methods/redirecting-passing-data.html)
- [Spring Framework 6.1 — Flash Attributes](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/mvc-controller/ann-methods/flash-attributes.html)
- [Spring Framework — View Resolution, Redirecting và Forwarding](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/viewresolver.html)
- [Spring Framework 6.1 — RedirectView Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/view/RedirectView.html)
- [Spring Framework 6.1 — RedirectAttributes Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/mvc/support/RedirectAttributes.html)
- [Spring Framework — MVC Model và View](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/modelattrib-methods.html)
- [Spring Boot 3.3 — Spring MVC and Thymeleaf](https://docs.spring.io/spring-boot/3.3/reference/web/servlet.html)
- [RFC 9110 — HTTP Semantics, redirection 3xx](https://www.rfc-editor.org/rfc/rfc9110.html)

**Tóm tắt:** Redirect trả về hướng đi mới để client tự gửi request mới; forward chuyển tiếp nội bộ trong server; PRG là quy trình *POST lưu thành công → 3xx redirect → GET hiển thị*. Trong Spring MVC, `redirect:`, `forward:`, `RedirectAttributes` và các view resolvers cung cấp cơ chế triển khai. Dùng flash cho thông báo ngắn hạn, dùng service/database cho dữ liệu thật, và luôn kết hợp validation, authorization, CSRF/idempotency khi bài toán có yêu cầu bảo mật hoặc xử lý trùng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-forms-007">RedirectAttributes, flash attributes và vòng đời dữ liệu chuyển hướng</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — RedirectAttributes, flash attributes và vòng đời dữ liệu chuyển hướng

## 1. Mục tiêu

Sau khi người dùng gửi form POST và server lưu dữ liệu thành công, trang kết quả thường được mở qua một URL GET. Spring MVC hỗ trợ cách làm này bằng **Post/Redirect/Get (PRG)**, `RedirectAttributes` và **flash attributes**.

Bạn sẽ học cách:

- Phân biệt `addAttribute(...)` (giá trị trong URL) và `addFlashAttribute(...)` (dữ liệu tạm dành cho request đích).
- Hiểu quy trình HTTP POST → 302/303 redirect → GET và giới hạn của PRG.
- Theo dõi `FlashMap` đầu ra, việc lưu tạm (mặc định thường qua HTTP session), khớp URL đích, `FlashMap` đầu vào và hành vi tiêu thụ một lần.
- Xử lý form validation, thông báo thành công và redirect tới trang chi tiết với Thymeleaf.
- Đánh giá các tình huống flash mất sớm, session hết hạn, nhiều tab cùng lúc, API/AJAX và dữ liệu nhạy cảm.
- Chạy dự án Spring Boot 3.5, Java 17 và kiểm thử redirect/flash bằng MockMvc.

Bài viết tập trung **Spring MVC trên Servlet stack**. Không tự áp dụng cơ chế session FlashMap của MVC sang Spring WebFlux.

## 2. Post/Redirect/Get giải quyết vấn đề gì?

Nếu server trả HTML trực tiếp cho POST:

~~~text
Browser                         Server
 POST /messages --------------->
                 lưu dữ liệu
 <---------------- HTTP 200 HTML
 Refresh: trình duyệt có thể hỏi "gửi lại form" và POST lần nữa
~~~

Một lần refresh có thể tạo bản ghi trùng. Cách thiết kế PRG:

~~~text
Browser                         Spring MVC
 POST /messages --------------> Validate, save: id=42
 <-------------- HTTP 302 Location: /messages/42
 GET /messages/42 ------------> Lấy dữ liệu theo id
 <-------------- HTTP 200 HTML
 Refresh trang kết quả --------> GET /messages/42
~~~

Ví dụ handler minh họa:

~~~java
@PostMapping("/messages")
public String create(RedirectAttributes redirectAttributes) {
    long id = 42; // ID thực tế lấy từ service sau khi lưu thành công.
    redirectAttributes.addAttribute("id", id);
    redirectAttributes.addFlashAttribute("notice", "Tạo thành công");
    return "redirect:/messages/{id}";
}
~~~

`redirect:` là prefix của một **view name** dành cho redirect. Spring MVC tạo HTTP redirect với header `Location`. Sau khi browser thực hiện GET đích, người dùng refresh sẽ lặp GET, không tự động lặp POST cũ.

**Giới hạn:** PRG giảm nguy cơ lặp POST do refresh nhưng **không bảo đảm idempotency** khi người dùng double-click, client retry request, mạng timeout sau khi server đã commit hoặc có hai POST đồng thời. Với đơn hàng, thanh toán hoặc thao tác ghi quan trọng, cần thêm idempotency key/unique constraints/transaction thích hợp.

### 2.1. Chọn 302, 303, 307 hay 308?

| Status | Ý nghĩa cần chú ý | Vai trò khi PRG |
| --- | --- | --- |
| `302 Found` | Redirect tạm, lịch sử client thường chuyển POST thành GET | `redirect:` thông thường trả 302 |
| `303 See Other` | Client nên truy xuất tài nguyên đích, thường bằng GET | Rõ nghĩa nhất cho POST thành công rồi mở trang kết quả |
| `307 Temporary Redirect` | Giữ method và body khi redirect | Không phù hợp nếu muốn đổi POST thành GET |
| `308 Permanent Redirect` | Giữ method và body, redirect mang tính lâu dài | Không dùng cho PRG thông thường |

Để tạo 303 một cách tường minh, có thể trả một `RedirectView` được cấu hình status:

~~~java
RedirectView view = new RedirectView("/messages/42");
view.setStatusCode(HttpStatus.SEE_OTHER);
return view;
~~~

Đoạn này minh họa trong handler có return type `RedirectView`, cần các import Spring MVC/HTTP tương ứng. Nếu dùng `return "redirect:..."` như dự án phía dưới, không khẳng định mặc định là 303; test cần xác minh 3xx/Location phù hợp.

## 3. RedirectAttributes: dữ liệu cho chuyển hướng

Interface cần import:

~~~java
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
~~~

Hai thao tác trung tâm:

~~~java
attrs.addAttribute("id", 42);
attrs.addAttribute("tab", "history");
attrs.addFlashAttribute("notice", "Đã lưu thành công");
~~~

| Thao tác | Nơi giá trị xuất hiện | Vòng đời | Cách dùng |
| --- | --- | --- | --- |
| `addAttribute("id", 42)` | URI path hoặc query parameter | Tồn tại trong URL, bookmark/share được | Mã tài nguyên, trang, bộ lọc |
| `addFlashAttribute("notice", "...")` | FlashMap tạm, không được gắn vào URL | Dành cho request đích phù hợp rồi bị tiêu thụ | Thông báo thành công một lần |
| `model.addAttribute(...)` trong handler trả view thường | Model của request hiện tại | Phục vụ render hiện tại | Form/view data |
| `HttpSession.setAttribute(...)` | HTTP session | Tới khi bị xóa/hết hạn | Trạng thái nhiều request |
| `@SessionAttributes` | Model attributes được Spring lưu qua session | Tới khi kết thúc flow hoặc session hết hạn | Wizard nhiều bước |

### 3.1. addAttribute đi vào path hoặc query

~~~java
@PostMapping("/orders")
public String create(RedirectAttributes attrs) {
    attrs.addAttribute("id", 42);
    attrs.addAttribute("tab", "history");
    return "redirect:/orders/{id}";
}
~~~

Trong kịch bản này Location có dạng:

~~~text
/orders/42?tab=history
~~~

`id` mở rộng placeholder `{id}`, còn `tab` không có placeholder tương ứng nên có thể được đưa thành query parameter. Redirect attributes được format/encode phù hợp để dùng trong URI; tránh tự nối chuỗi query từ đầu vào không tin cậy.

Vì URL xuất hiện trong browser history, bookmarks, analytics, referer và access logs, **không** truyền access token, mật khẩu, email riêng tư hoặc thông tin nhạy cảm bằng `addAttribute`. Một ID có thể được xuất công khai vẫn cần kiểm tra quyền truy cập ở GET handler.

### 3.2. addFlashAttribute đưa dữ liệu vào request GET đích

~~~java
@PostMapping("/orders")
public String create(RedirectAttributes attrs) {
    attrs.addAttribute("id", 42);
    attrs.addFlashAttribute("notice", "Tạo đơn thành công");
    return "redirect:/orders/{id}";
}

@GetMapping("/orders/{id}")
public String detail(@PathVariable long id, Model model) {
    model.addAttribute("orderId", id);
    return "order-detail";
}
~~~

Đây là các fragment của controller đã khai báo mapping và view. Spring MVC sẽ tự chuyển flash `notice` vào `Model` của GET đích nếu flash còn hiệu lực và request khớp.

Ở template Thymeleaf:

~~~html
<p th:if="${notice != null}" th:text="${notice}"></p>
~~~

Không cần `@RequestParam("notice")`, vì notice **không phải query parameter**. Và không cần GET handler tự gọi session.getAttribute, vì Spring quản lý input FlashMap tự động.

### 3.3. RedirectAttributes không có hiệu lực giống Model thường

Handler:

~~~java
@PostMapping("/preview")
public String preview(Model model, RedirectAttributes attrs) {
    model.addAttribute("preview", "Hello");
    attrs.addFlashAttribute("notice", "Saved");
    return "preview";
}
~~~

Vì handler trả view `preview`, **không có redirect**, nên không nên trông đợi `notice` được chuyển cho request sau. Model thông thường phục vụ render request hiện tại.

Spring MVC có quy tắc tương thích cũ về default Model khi redirect: thuộc tính đơn giản của model có thể xuất hiện trên URL redirect nếu không dùng redirect attributes tường minh. `RequestMappingHandlerAdapter` có tùy chọn `ignoreDefaultModelOnRedirect` để điều khiển trường hợp này. Việc chỉ định `RedirectAttributes` rõ ràng giúp hạn chế lộ thuộc tính không mong muốn từ model.

## 4. FlashMap và FlashMapManager

Spring MVC có hai abstraction quan trọng:

- **`FlashMap`:** một map chứa các thuộc tính flash cộng với metadata nhận diện request đích và expiration.
- **`FlashMapManager`:** quản lý lưu/lấy/xóa FlashMap; implementation MVC mặc định thường là `SessionFlashMapManager`, lưu tạm trong HTTP session.

Spring MVC cung cấp khái niệm input/output map theo từng request:

- **Output FlashMap:** dữ liệu được đặt ở request nguồn để chuyển tới request tương lai.
- **Input FlashMap:** dữ liệu nhận ở request đích từ output trước đó.

**Quy trình chi tiết:**

~~~text
A. POST /orders
   Controller:
      attrs.addAttribute("id",42)
      attrs.addFlashAttribute("notice","OK")
      return "redirect:/orders/{id}"
   MVC/RedirectView:
      Expand URL thành /orders/42
      Stamp output FlashMap với target path và query
      FlashMapManager lưu output, thường trong HTTP session
   Server -> Client:
      HTTP 302, Location: /orders/42
      Set-Cookie: JSESSIONID=... (nếu session mới được tạo)

B. GET /orders/42, giữ cookie session
   FlashMapManager tìm map trùng target
   Lấy làm input FlashMap, loại map đã tiêu thụ khỏi nơi lưu
   MVC thêm notice="OK" vào Model
   Controller GET load order 42
   Template render notice và order detail

C. Browser refresh GET /orders/42
   Flash đã tiêu thụ, model không có notice từ A
   Trang vẫn load order 42 nhờ URL và dữ liệu bền vững
~~~

Một flash attribute **không được bảo đảm** sẽ xuất hiện đúng một lần trong mọi hoàn cảnh. Nó có thể mất vì timeout/session hoặc request khác; do đó phải hiểu là **nỗ lực chuyển dữ liệu tạm tới một request phù hợp**, không phải message queue có delivery guarantee.

### 4.1. Request matching và URL stamping

Đặc điểm nguy hiểm của flash là "request kế tiếp" có thể là polling/AJAX hoặc tải resource trước khi browser GET đích. Spring MVC giảm rủi ro bằng cách `RedirectView` **đánh dấu URL đích** (path và query parameters) trên FlashMap, và manager kiểm tra khi lấy:

~~~text
POST /orders -> Location: /orders/42?tab=summary
GET /api/notifications       -> thường không khớp target
GET /orders/42?tab=summary   -> khớp target, lấy flash
~~~

Spring khuyến nghị flash chủ yếu cho redirect scenarios. Stamping giảm nguy cơ bị lấy nhầm nhưng **không loại bỏ mọi race**, nhất là khi nhiều tab cùng session cùng redirect tới một target path/query. Request đích bị lỗi sau khi flash đã lấy cũng có thể khiến thông báo không còn cho lần refresh.

### 4.2. Flash có tạo session không?

Hỗ trợ flash **luôn có sẵn** trong Spring MVC, nhưng nếu không sử dụng flash, riêng việc bật hỗ trợ này không bắt buộc tạo session. Khi thực sự có output flash cần lưu, implementation mặc định thường dùng HTTP session.

Hệ quả:

- Client phải giữ **session cookie** giữa POST và GET.
- Nếu session expired/invalidated hoặc cookie bị chặn, notice có thể mất.
- Ứng dụng nhiều node phải cấu hình session sharing/sticky session nếu dữ liệu flash cần di chuyển giữa nodes.
- Object được lưu trong distributed session có thể đòi hỏi serialization phù hợp; ưu tiên flash value nhỏ, đơn giản.
- Flash không thay database và không phải chỗ lưu biên nhận duy nhất cho giao dịch.

### 4.3. Expiration và điều kiện dữ liệu biến mất

FlashMap có thời hạn tồn tại. Có thể mất trước khi trình duyệt mở URL đích nếu TTL hết; thời gian thực tế phụ thuộc manager/config. Nếu GET đích nhận được flash, manager thường **loại entry khi lấy**, không chờ người dùng click xác nhận đọc.

Đừng thiết kế điều kiện nghiệp vụ "chỉ thành công nếu trang đọc được flash": thao tác POST có thể đã thành công dù flash message bị mất. Nguồn dữ liệu kết quả phải là database/resource GET.

### 4.4. Khi cần dùng FlashMap trực tiếp

Có thể truy cập qua:

~~~java
Map<String, ?> input = RequestContextUtils.getInputFlashMap(request);
FlashMap output = RequestContextUtils.getOutputFlashMap(request);
~~~

Cần `org.springframework.web.servlet.support.RequestContextUtils`, `org.springframework.web.servlet.FlashMap` và Servlet `request`. Đây là fragment minh họa cho trường hợp nâng cao. Với controller thông thường, **ưu tiên `RedirectAttributes`** vì Spring lo việc lấy/lưu URL và flash.

Khi tự tạo redirect với tầng thấp (ví dụ tự set header Location), output FlashMap không nhất thiết được stamp/lưu theo cùng cơ chế view redirect. `RequestContextUtils.saveOutputFlashMap(location,request,response)` tồn tại cho các trường hợp như vậy, nhưng chỉ dùng nếu bạn chủ động kiểm soát redirect flow.

## 5. Vòng đời biểu mẫu hợp lệ và không hợp lệ

Một xử lý form MVC điển hình:

~~~java
@PostMapping("/messages")
public String submit(
        @Valid @ModelAttribute("form") MessageForm form,
        BindingResult errors,
        RedirectAttributes attrs) {

    if (errors.hasErrors()) {
        return "message-form";
    }

    long id = saveMessage(form); // Hàm service minh họa.
    attrs.addAttribute("id", id);
    attrs.addFlashAttribute("notice", "Đã lưu thành công");
    return "redirect:/messages/{id}";
}
~~~

**Nhánh thành công:** lưu dữ liệu → URL GET mới chứa ID → flash success message → redirect.

**Nhánh lỗi validation:** giữ form và `BindingResult` trong **cùng request** → render lại form; người dùng thấy field errors và dữ liệu vừa nhập. `BindingResult` bắt buộc đứng **ngay sau** tham số `@ModelAttribute` tương ứng.

Vì sao thường không redirect khi form lỗi?

- Các thông tin lỗi và rejected values nằm trong binding/model của request hiện tại.
- Redirect là một request mới: nếu không chủ động lưu form/errors vào flash, các thông tin này mất.
- Việc flash toàn bộ form chứa password, file upload, dữ liệu lớn hoặc nhạy cảm có thể không an toàn.
- Không cần thêm session flow phức tạp cho một form đơn giản.

### 5.1. Có thể dùng flash để mang lỗi validation qua redirect?

Có, nếu kiến trúc yêu cầu strict PRG ngay cả khi lỗi. Fragment nâng cao:

~~~java
attrs.addFlashAttribute("form", form);
attrs.addFlashAttribute(
        BindingResult.MODEL_KEY_PREFIX + "form", errors);
return "redirect:/messages/new";
~~~

Chú ý key `BindingResult.MODEL_KEY_PREFIX + "form"` phải khớp tên model attribute. GET form cũng phải tránh ghi đè form/errors nhận từ flash. Nhưng flash có giới hạn session, TTL, concurrency, dữ liệu cần bảo vệ và trạng thái BindingResult phức tạp: **render lại form cùng request khi lỗi** thường dễ bảo trì hơn.

## 6. So sánh RedirectAttributes, session và URL

| Nhu cầu | Cách làm phù hợp | Hệ quả |
| --- | --- | --- |
| Đường dẫn tới tài nguyên mới | `addAttribute("id", id)` và `redirect:/resources/{id}` | Dễ bookmark, reload và chia sẻ |
| Bộ lọc/tab/page hiện tại | `addAttribute` | Được lưu trong URL |
| Notice một lần sau POST | `addFlashAttribute` | Dữ liệu tạm, có thể mất do timeout |
| Dữ liệu nhiều bước wizard | `@SessionAttributes` hoặc session/state backend có kiểm soát | Cần quản lý lifecycle và cleanup |
| Kết quả nghiệp vụ bền vững | Database/repository | GET lại được sau reload và session hết hạn |
| API REST trả kết quả tạo mới | `201 Created` + Location/JSON | Không nhất thiết dùng redirect HTML |
| SPA/AJAX tự điều hướng | Response JSON và client-side navigation | Không dựa vào việc fetch tự follow redirect để chuyển màn hình |

`@SessionAttributes` không phải flash: đó là tính năng MVC giữ các model attributes qua nhiều request trong một controller flow và thường kết thúc bằng `SessionStatus.setComplete()`. `@RequestAttribute` cũng khác: đọc attribute của **request hiện tại**, không tự lưu qua redirect.

## 7. Cẩn thận với trạng thái HTTP và URL

### 7.1. RedirectView và 303 See Other

Nếu muốn 303 thay vì 302:

~~~java
@PostMapping("/submit")
public RedirectView submit(RedirectAttributes attrs) {
    long id = save(); // Minh họa.
    attrs.addAttribute("id", id);
    RedirectView view = new RedirectView("/messages/{id}");
    view.setStatusCode(HttpStatus.SEE_OTHER);
    return view;
}
~~~

Fragment này cần imports cho `RedirectView`, `HttpStatus` và service. Xác minh URL expansion/encoding/contextPath theo cấu hình sử dụng. Cách return `redirect:` ngắn gọn thường đủ với form MVC, còn 303 rõ ý nghĩa HTTP hơn nếu API contract muốn enforce GET.

### 7.2. Không mở open redirect

Sai:

~~~java
return "redirect:" + request.getParameter("next");
~~~

Nếu `next=https://evil.example`, ứng dụng có thể redirect người dùng sang domain do attacker chọn và tạo phishing. Dùng route nội bộ đã xác định, whitelist đích, canonicalization và kiểm tra scheme/host nếu thật sự cho phép return URL.

### 7.3. Không đưa bí mật vào URL hoặc flash tùy tiện

`addAttribute("token", secret)` lộ token qua history/referer/log. Flash không nằm trong URL nhưng có thể được giữ trong session, được render lên trang đích và chịu rủi ro session fixation/hijacking nếu quản trị session kém. Không lưu credentials, access tokens hay object domain có dữ liệu nhạy cảm trong flash chỉ vì nó "ẩn khỏi URL".

## 8. Dự án Spring Boot 3.5 hoàn chỉnh

Dự án Java 17 + Maven: mở form nhập nội dung, gửi POST, lưu memory, redirect đến trang detail với flash notice. Refresh GET không còn notice, còn dữ liệu message tồn tại trong bộ nhớ ứng dụng.

### 8.1. Cấu trúc

~~~text
redirect-flash-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/RedirectFlashApplication.java
    │   └── resources/templates/
    │       ├── message-form.html
    │       └── message-detail.html
    └── test/java/demo/MessageControllerTest.java
~~~

### 8.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>redirect-flash-demo</artifactId>
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
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
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

### 8.3. RedirectFlashApplication.java

Toàn bộ Java code dưới đây thuộc **một file** `src/main/java/demo/RedirectFlashApplication.java`. Có một public top-level class trùng tên file, các class khác package-private.

~~~java
package demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@SpringBootApplication
public class RedirectFlashApplication {
    public static void main(String[] args) {
        SpringApplication.run(RedirectFlashApplication.class, args);
    }
}

class MessageForm {
    @NotBlank
    @Size(max = 100)
    private String text;

    public MessageForm() {}

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}

record Message(long id, String text) {}

@Controller
@RequestMapping("/messages")
class MessageController {
    private final Map<Long, Message> store = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    @GetMapping("/new")
    String newForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new MessageForm());
        }
        return "message-form";
    }

    @PostMapping
    String create(
            @Valid @ModelAttribute("form") MessageForm form,
            BindingResult errors,
            RedirectAttributes attrs) {

        if (errors.hasErrors()) {
            return "message-form";
        }

        long id = nextId.incrementAndGet();
        store.put(id, new Message(id, form.getText().trim()));

        attrs.addAttribute("id", id);
        attrs.addFlashAttribute(
                "notice", "Đã tạo thông báo thành công");

        return "redirect:/messages/{id}";
    }

    @GetMapping("/{id}")
    String detail(@PathVariable long id, Model model) {
        Message message = store.get(id);
        if (message == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Message not found");
        }

        model.addAttribute("message", message);
        // notice đã được đưa vào model nếu input FlashMap tồn tại.
        return "message-detail";
    }
}
~~~

**Giải thích:** POST thành công thêm hai loại attribute: `id` thay vào URL, `notice` đi theo flash. POST lỗi render lại form nên `BindingResult` vẫn khả dụng. GET detail tự tải data theo id, không cần notice để hoạt động; refresh GET vẫn hiển thị message.

`ConcurrentHashMap`/`AtomicLong` cho phép demo trong RAM có nhiều request, nhưng đây không phải storage bền vững; khởi động lại ứng dụng sẽ mất messages. Ứng dụng thật cần repository/service, transaction và authorization.

### 8.4. message-form.html

Đặt vào `src/main/resources/templates/message-form.html`:

~~~html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Tạo thông báo</title>
</head>
<body>
  <h1>Tạo thông báo</h1>
  <form th:action="@{/messages}" th:object="${form}" method="post">
    <label for="text">Nội dung</label>
    <input id="text" type="text" th:field="*{text}" maxlength="100">
    <div th:if="${#fields.hasErrors('text')}"
         th:errors="*{text}">Lỗi nhập liệu</div>
    <button type="submit">Tạo</button>
  </form>
</body>
</html>
~~~

`th:object` chọn form bean, `th:field` bind input vào property `text`. Khi `@NotBlank` hoặc `@Size` không đạt, Thymeleaf lấy field errors từ `BindingResult` của model mà Spring đã chuẩn bị.

### 8.5. message-detail.html

Đặt vào `src/main/resources/templates/message-detail.html`:

~~~html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Chi tiết thông báo</title>
</head>
<body>
  <p th:if="${notice != null}" th:text="${notice}">Đã tạo</p>
  <h1 th:text="${message.text}">Nội dung</h1>
  <p>ID: <span th:text="${message.id}">0</span></p>
  <a th:href="@{/messages/new}">Tạo thông báo khác</a>
</body>
</html>
~~~

Dùng `th:text` để tự HTML-escape nội dung khi xuất ra view. Không dùng `th:utext` với dữ liệu người dùng chưa sanitize vì có thể gây XSS. Sau refresh, `notice` thường không tồn tại nên đoạn thông báo ẩn; `message` được tải lại từ store.

### 8.6. Khởi chạy và quan sát qua browser

Trong thư mục `redirect-flash-demo`:

~~~bash
mvn spring-boot:run
~~~

Mở `http://localhost:8080/messages/new`. Nhập một nội dung hợp lệ rồi submit. Browser được redirect tới `/messages/1` nếu đó là lần tạo đầu; thông báo flash chỉ xuất hiện trong lượt GET đích đầu. Refresh trang: nội dung message vẫn có, còn notice biến mất. Quay lại form, nhập chuỗi trống hoặc toàn dấu cách để kiểm tra validation: controller render form trực tiếp và không redirect.

### 8.7. Thử với curl và cookie session

~~~bash
curl -i -c cookies.txt -b cookies.txt \
  -X POST 'http://localhost:8080/messages' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'text=Hello Spring'
~~~

Khi là bản ghi đầu tiên, response redirect có Location `/messages/1` (và có thể có Set-Cookie cho HTTP session). Sau đó:

~~~bash
curl -i -c cookies.txt -b cookies.txt \
  'http://localhost:8080/messages/1'
~~~

GET kế tiếp cùng session:

~~~bash
curl -i -c cookies.txt -b cookies.txt \
  'http://localhost:8080/messages/1'
~~~

GET đầu có HTML notice, GET thứ hai không còn notice. Nếu không giữ cookie, flash có thể mất. Trên Windows PowerShell, dùng `curl.exe` và chạy trên một dòng hoặc dùng cú pháp nối dòng PowerShell; `\` trong ví dụ dành cho Bash.

Không dùng `curl -L -X POST` để suy diễn PRG: tùy tùy chọn, cURL có thể tiếp tục dùng POST khi theo redirect. Hai lệnh POST và GET tách biệt giúp thấy rõ redirect URL, HTTP status và session cookie.

### 8.8. Kiểm thử MockMvc

Tạo `src/test/java/demo/MessageControllerTest.java`:

~~~java
package demo;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void validPostRedirectsAndStoresFlash() throws Exception {
        mvc.perform(post("/messages").param("text", "Hello"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/messages/*"))
            .andExpect(flash().attribute(
                    "notice", "Đã tạo thông báo thành công"));
    }

    @Test
    void invalidPostRendersTheForm() throws Exception {
        mvc.perform(post("/messages").param("text", "  "))
            .andExpect(status().isOk())
            .andExpect(view().name("message-form"))
            .andExpect(model().attributeHasFieldErrors("form", "text"));
    }

    @Test
    void noticeExistsOnFirstMatchingGetOnly() throws Exception {
        MvcResult postResult = mvc.perform(
                post("/messages").param("text", "Once"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        String location = postResult.getResponse().getRedirectedUrl();
        assertNotNull(location);
        HttpSession session = postResult.getRequest().getSession(false);
        assertNotNull(session);
        MockHttpSession mockSession = (MockHttpSession) session;

        mvc.perform(get(location).session(mockSession))
            .andExpect(status().isOk())
            .andExpect(view().name("message-detail"))
            .andExpect(model().attribute(
                    "notice", "Đã tạo thông báo thành công"));

        mvc.perform(get(location).session(mockSession))
            .andExpect(status().isOk())
            .andExpect(model().attributeDoesNotExist("notice"));
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

Test kiểm tra redirect Location, flash ở output FlashMap, việc load flash ở GET đầu và sự biến mất ở GET thứ hai. Lấy redirect URL thực tế nên không giả định ID cố định giữa tests. Đây là **MockMvc test** của MVC; muốn kiểm tra browser session cookie, nhiều node, proxy, timeout hoặc concurrent tabs cần integration/E2E tests bổ sung.

## 9. Các sai lầm thường gặp

1. **Dùng `addAttribute` cho thông báo riêng tư:** thông báo có thể xuất hiện trên query string và lộ qua history/log. Dùng flash cho notice nhỏ; không dùng flash để lưu secrets.
2. **Quên redirect:** gọi `addFlashAttribute` nhưng handler trả view thường. Flash không được tự chuyển sang request đích.
3. **Không giữ session cookie:** POST và GET diễn ra ở hai session khác nhau, nên GET không thấy flash.
4. **Tự tạo Location header mà không lưu FlashMap:** cách redirect thấp tầng có thể bỏ qua lifecycle của RedirectView.
5. **GET detail phụ thuộc vào flash:** refresh hoặc mất session làm trang hỏng. GET phải truy xuất resource theo ID URL.
6. **Validation lỗi vẫn redirect mà không mang form/BindingResult:** người dùng không thấy lỗi; với lỗi form thông thường render lại cùng view.
7. **Dùng flash làm queue hoặc dữ liệu bền vững:** dữ liệu có TTL, bị tiêu thụ và có thể mất do concurrency.
8. **Đặt cùng target cho redirect từ nhiều tab:** URL stamping không phân biệt hoàn toàn các flash maps có cùng URL đích.
9. **Tin PRG ngăn mọi duplicate write:** cần idempotency protection ở server cho tác vụ ghi quan trọng.
10. **Dùng `@SessionAttributes` thay cho flash:** session attribute sống qua nhiều requests và cần kết thúc rõ, nên notice có thể hiển thị lặp.
11. **Tự nối redirect URL từ giá trị không tin cậy:** nguy cơ open redirect và encoding sai.
12. **Dùng browser fetch tự follow redirect rồi chờ full page navigation:** AJAX/SPA thường cần client navigation và trạng thái riêng.
13. **Quên CSRF protection cho form POST khi có Spring Security:** PRG không ngăn CSRF; bật CSRF theo policy, không bỏ chỉ vì demo.
14. **Không kiểm tra URL/query stamping qua reverse proxy:** rewrite URL hoặc thay query có thể làm flash không khớp request đích.
15. **Dùng flash chứa entity/lượng dữ liệu lớn:** tạo rủi ro session serialization, memory và thông tin riêng tư.

## 10. Câu hỏi luyện tập

**Bài 1.** POST tạo đơn id 123, GET kết quả `/orders/123`. Viết đoạn dùng `addAttribute("id", 123)` và `addFlashAttribute("notice", "Đã tạo đơn")`. Cho biết field nào nằm ở URL, field nào chỉ được render một lần.

**Bài 2.** Vẽ chuỗi POST → 302 → GET → refresh GET. Xác định lúc nào output FlashMap được lưu, lúc nào input FlashMap được lấy và lúc nào dữ liệu bị loại bỏ.

**Bài 3.** Vì sao trong form validation `BindingResult` phải đứng ngay sau `@ModelAttribute`? Khi lỗi nên trả lại form hay redirect, và nếu redirect thì cần giữ những gì?

**Bài 4.** So sánh `addFlashAttribute` với `@SessionAttributes` đối với wizard cần giữ dữ liệu qua bốn bước. Tại sao flash có thể mất trước khi đến bước 4?

**Bài 5.** Hai tab cùng session POST rồi redirect tới `/profile` với hai notices khác nhau. Giải thích vì sao URL stamping chưa đảm bảo đúng notice ở từng tab và đề xuất hướng điều chỉnh flow.

**Bài 6.** Khách hàng gửi `next=https://attacker.example`. Vì sao `return "redirect:" + next` có thể nguy hiểm? Thiết kế allowlist các đích nội bộ.

**Bài 7.** Trong MockMvc, làm sao assert output flash, lấy URL redirect thực tế, truyền cùng MockHttpSession cho GET thứ nhất và xác minh flash không còn ở GET thứ hai?

**Bài 8.** Nếu POST tạo đơn thành công nhưng cookie không được gửi trong GET, kết quả domain và thông báo flash khác nhau như thế nào? Trang GET nên hoạt động ra sao?

## 11. Ghi nhớ

- **PRG** ngăn refresh trang kết quả vô tình gửi lại POST, nhưng không bảo đảm idempotency tuyệt đối.
- **`RedirectAttributes.addAttribute`** tạo URL/path/query state; chỉ dùng cho dữ liệu được phép công khai.
- **`RedirectAttributes.addFlashAttribute`** tạo dữ liệu tạm cho **request đích phù hợp**; mặc định dựa trên HTTP session và có hạn sử dụng.
- **`FlashMap` + `FlashMapManager`** chịu trách nhiệm lưu, khớp target, đưa vào model rồi tiêu thụ; URL stamping giảm nhưng không xóa hết race.
- **Validation lỗi thường render form cùng request** để giữ BindingResult. **Thành công thường redirect** và flash thông báo.
- Flash là UX convenience, không phải database, session wizard, secret store hay queue bảo đảm giao hàng.

## 12. Tham khảo

- [Spring Framework 6.2 — Redirect Attributes](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/redirecting-passing-data.html).
- [Spring Framework 6.2 — Flash Attributes](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/flash-attributes.html).
- [Spring MVC — RedirectAttributes API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/support/RedirectAttributes.html).
- [Spring MVC — FlashMap API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/FlashMap.html).
- [Spring MVC — RedirectView API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/view/RedirectView.html).
- [Spring MVC — Thymeleaf Views](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc-view/mvc-thymeleaf.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="views-forms-008">Giới hạn nội dung động, escape HTML và nguy cơ XSS khi kết xuất view</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn nội dung động, escape HTML và nguy cơ XSS khi kết xuất view trong Spring MVC

## 1. Mục tiêu và bối cảnh

Spring MVC thường đưa dữ liệu từ database, request parameter hoặc form vào `Model`, rồi chuyển model tới Thymeleaf/JSP để render HTML. Những dữ liệu như tên, nội dung bình luận, mô tả sản phẩm hoặc đường dẫn do người dùng nhập đều có thể chứa ký tự mang ý nghĩa đặc biệt với trình duyệt. **Nếu nội dung không đáng tin cậy bị kết xuất thành mã HTML hoặc JavaScript, trình duyệt có thể thực thi nó**: đó là nền tảng của lỗ hổng Cross-Site Scripting (XSS).

Sau bài này, bạn có thể:

1. Phân biệt **dữ liệu văn bản** với **HTML được phép render**, và giải thích vì sao dữ liệu trong `Model` không tự trở nên an toàn.
2. Sử dụng Thymeleaf `th:text`, `th:value`, `th:href`, biểu thức inline có escape và DOM `textContent` phù hợp; nhận diện rủi ro của `th:utext`, inline unescaped và `innerHTML`.
3. Áp dụng **output encoding theo ngữ cảnh** HTML text, HTML attribute, URL, JavaScript và CSS; hiểu khi nào encoding không đủ và phải kiểm soát cấu trúc/allowlist.
4. Giới hạn dữ liệu động (độ dài, định dạng, quyền hiển thị, danh sách URL cho phép), sanitize HTML giàu định dạng khi thực sự cần, và đặt CSP như lớp phòng vệ bổ sung.
5. Tạo một ví dụ **Spring Boot 3.x + Thymeleaf + Java 17+**, rồi kiểm thử rằng payload có dấu `<`, `>`, dấu nháy hoặc nội dung trông giống HTML vẫn được hiển thị dưới dạng text mà không biến thành thẻ/thuộc tính có thể thực thi.

**Phạm vi:** Spring Framework 6.x, Spring MVC trên Servlet stack và các trang HTML render phía server. Nhiều nguyên tắc cũng áp dụng cho trang frontend SPA; nhưng Spring MVC không tự bảo vệ những DOM update do JavaScript ở trình duyệt thực hiện.

## 2. XSS hình thành ở đâu trong luồng render view?

Một form nhận tên hiển thị hoặc lời bình:

~~~http
GET /pages/preview?message=Hello HTTP/1.1
Host: localhost:8080
~~~

Dữ liệu đến controller:

~~~java
@GetMapping("/preview")
public String preview(@RequestParam String message, Model model) {
    model.addAttribute("message", message);
    return "preview";
}
~~~

Đây là đoạn **minh họa trong một `@Controller`**, cần import annotation và khai báo class. `Model` chứa đúng giá trị mà client gửi; `model.addAttribute(...)` **không phải sanitizer**.

Sau đó view sử dụng attribute `message`. Nếu template cho trình duyệt hiểu dữ liệu ấy như **HTML source**, các thẻ và thuộc tính có thể làm thay đổi cấu trúc DOM và gây XSS. Ví dụ chuỗi kiểm thử vô hại để nhận biết khác biệt:

~~~text
<img src=x onerror=alert(1)>
~~~

Đoạn trên chỉ là **test payload**. Khi được escape và render làm text, trình duyệt hiển thị nguyên dòng ký tự. Khi được đưa trực tiếp vào HTML parser, nó tạo thẻ `img` có event handler, gây nguy cơ thực thi JavaScript.

Ba điểm cần kiểm soát:

- **Nguồn dữ liệu không đáng tin cậy:** request parameter, form, cookie, header, dữ liệu từ database do user tạo, nội dung bên thứ ba, Markdown/HTML từ CMS. Dữ liệu đã lưu trong database vẫn có thể gây **stored XSS** khi hiển thị lại.
- **Chặng đưa dữ liệu vào response:** Thymeleaf/JSP template, `ResponseEntity<String>` trả HTML tự ghép, JavaScript nhúng trong trang, attribute `href`, CSS, `iframe`, hoặc DOM update sau khi trang tải.
- **Ngữ cảnh parser đích:** HTML text, HTML attribute, JavaScript code/string, URL, CSS hoặc DOM API. **Mỗi ngữ cảnh cần quy tắc khác nhau**; một hàm “escape chung” không bảo vệ được mọi vị trí.

**Reflected XSS** xảy ra khi input từ request bị phản chiếu vào response nguy hiểm; **stored XSS** xảy ra khi nội dung đã lưu được render nguy hiểm; **DOM-based XSS** liên quan JavaScript phía client đưa dữ liệu vào các DOM sink có khả năng diễn giải như code/markup. Các dạng có thể kết hợp.

## 3. Escape khác sanitize và validate như thế nào?

| Thao tác | Giải quyết vấn đề | Ví dụ |
| --- | --- | --- |
| **Output encoding / escaping** | Chuyển ký tự đặc biệt thành biểu diễn an toàn cho **đúng ngữ cảnh output** | Trong HTML text, `<` thành `&lt;` |
| **Input validation** | Xác minh dữ liệu đúng contract/nghiệp vụ (độ dài, cấu trúc, tập giá trị) | Tên dài tối đa 200 ký tự |
| **HTML sanitization** | Loại bỏ/thay thế phần markup nguy hiểm nhưng giữ một số HTML hợp lệ | Chỉ cho phép `<b>`, `<em>`, link an toàn |
| **Authorization** | Xác nhận người xem có quyền nhận dữ liệu | Không đưa email riêng tư vào model công khai |
| **CSP / Trusted Types** | Hạn chế một số hướng thực thi script/DOM injection | `Content-Security-Policy` chặn inline scripts không được cho phép |

**Đầu ra thường phải được escape tại điểm render**, dù đầu vào đã qua validation. Validation không thể thay thế encoding: một tên sản phẩm hợp lệ có thể chứa ký tự `<`, dấu `&` hoặc dấu nháy. Ngược lại, HTML escaping không giới hạn độ dài, kiểm tra quyền hay biến một URL nguy hiểm thành URL đáng tin.

Một ví dụ HTML text được escape:

~~~html
<!-- Source trong response -->
<p>&lt;img src=x onerror=alert(1)&gt;</p>
~~~

Trình duyệt hiển thị đúng chuỗi ký tự `<img src=x onerror=alert(1)>`; nó **không tạo ra một `img` child** từ chuỗi đó. Browser đã giải mã entity thành text trong DOM, nhưng text node không được parse lại thành HTML.

Đừng escape khi ghi vào database rồi escape lại một lần nữa trong view nếu contract lưu trữ yêu cầu văn bản thô: có thể phát sinh `&amp;lt;` và hiển thị sai. Một hướng bền vững là **lưu canonical text**, validate theo nghiệp vụ, và encode **mỗi lần đưa ra một sink cụ thể**.

## 4. Thymeleaf: `th:text` an toàn cho HTML text, `th:utext` là lối thoát nguy hiểm

### 4.1. `th:text`: mặc định escape HTML

~~~html
<p id="comment" th:text="${comment}">Bình luận</p>
~~~

Nếu `comment = "<b>Xin chào</b>"`, Thymeleaf kết xuất:

~~~html
<p id="comment">&lt;b&gt;Xin chào&lt;/b&gt;</p>
~~~

Trình duyệt **hiển thị** ký tự `<b>Xin chào</b>` dưới dạng chữ, không tạo thẻ bôi đậm. Khi muốn hiển thị nội dung do user nhập chính xác như text, đây là lựa chọn phù hợp.

Trong Thymeleaf 3.1, biểu thức inline dùng **ngoặc kép** `[[...]]` tương ứng escaped text:

~~~html
<p>Nhận xét: [[${comment}]]</p>
~~~

### 4.2. `th:utext` và `[(...)]`: HTML không được escape

~~~html
<!-- KHÔNG dùng với comment do người dùng cung cấp trực tiếp -->
<p th:utext="${comment}">Bình luận</p>
<p>[(${comment})]</p>
~~~

`th:utext` là *unescaped text*. Nếu `comment` chứa thẻ HTML, thẻ có thể được parser của browser diễn giải. Biểu thức inline `[(...)]` cũng cho output không escape. Thymeleaf 3.1 có một số giới hạn đánh giá expression để bảo vệ template engine, nhưng **không phải sanitizer cho HTML output**.

Chỉ cân nhắc unescaped HTML khi nghiệp vụ thực sự yêu cầu HTML giàu định dạng và bạn đã xây pipeline sanitization với chính sách allowlist phù hợp. Không cho rằng “dữ liệu từ database” hay “chỉ admin nhập” thì tự động an toàn: tài khoản quản trị cũng có thể bị chiếm quyền hoặc nội dung được import từ nguồn khác.

### 4.3. HTML attribute là ngữ cảnh khác

~~~html
<input type="text" th:value="${comment}">
<span th:title="${comment}">Rê chuột để xem</span>
<div th:attr="data-comment=${comment}"></div>
~~~

Thymeleaf xử lý biểu thức gắn attribute, encode các ký tự thích hợp và giữ cấu trúc thuộc tính trong trường hợp thông thường. **Vẫn phải sử dụng thuộc tính cố định và đúng mục đích**. Nếu tự ghép HTML string, bỏ dấu quote quanh attribute, hoặc dùng dữ liệu người dùng làm tên thuộc tính, nguy cơ injection tăng mạnh.

Một giá trị chứa dấu nháy như `" autofocus onfocus="...` không được phép “thoát” ra khỏi attribute đã được framework encode đúng. Với HTML tự ghép, cần encoder chuyên dụng cho quoted HTML attribute và luôn quote giá trị.

**Không nhầm điều này với event handler attribute** như `onclick` hoặc `onerror`: giá trị của chúng là JavaScript. HTML attribute encoding thông thường **không làm code JavaScript trở thành dữ liệu an toàn** sau khi browser giải mã entity. Tránh ghép dữ liệu user vào `th:onclick` hoặc bất kỳ `on*` attribute nào.

## 5. Output encoding phải theo đúng ngữ cảnh

| Vị trí đưa dữ liệu vào | Cách làm phù hợp | Những điều phải tránh |
| --- | --- | --- |
| Text nằm giữa thẻ HTML | Thymeleaf `th:text` / `[[...]]` | `th:utext`, `[(...)]` với input thô |
| Quoted HTML text attribute | `th:value`, `th:title`, `th:attr` với tên attribute cố định | Ghép tag string, attribute không quote, tên attribute động |
| Query parameter của `href` | Thymeleaf URL link expression với parameter; encode URL component và HTML attribute đúng ngữ cảnh | Nối chuỗi `?q=` trực tiếp rồi render raw |
| Toàn URL do user nhập | Parse/validate scheme và đích, allowlist trước khi render | Tin URL chỉ vì được HTML-escaped |
| JavaScript string trong HTML | Inlining JavaScript có serializer theo đúng ngữ cảnh hoặc truyền qua data attribute và đọc bằng DOM | Chèn trực tiếp template expression vào JS code/event handler |
| CSS property value | Giá trị hữu hạn được validate/allowlist, tên thuộc tính CSS cố định | Cho input làm selector, property name hay raw `style` |
| DOM text update | `textContent` | `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write` với input thô |
| HTML do user biên soạn | HTML sanitizer theo allowlist nghiêm ngặt rồi render theo contract | HTML parser/render raw trực tiếp từ user |
  
**Không tồn tại một “hàm escape HTML” dùng được cho tất cả JavaScript, CSS, URL và HTML attribute**. Browser đi qua nhiều parser liên tiếp. Một chuỗi đã HTML-encoded vẫn có thể nguy hiểm nếu sau đó được diễn giải như JavaScript hoặc URL executable scheme.

### 5.1. URL: encode parameter và kiểm soát scheme

Với một route nội bộ cố định, Thymeleaf hỗ trợ:

~~~html
<a th:href="@{/pages/search(q=${comment})}">Tìm kiếm</a>
~~~

`@{...}` là link expression; tham số `q` được xử lý như một URL parameter và giá trị kết quả được đặt vào HTML attribute. Dấu `&`, dấu cách hay dấu hỏi trong comment không nên được nối thô làm thay đổi cấu trúc query.

**Nhưng** nếu input là toàn bộ URL, việc encode ký tự HTML thôi **không ngăn** một giá trị bắt đầu bằng `javascript:` hoặc một URL ngoài miền không được phép. Khi nghiệp vụ cho phép link do người dùng nhập, bạn cần **parse URI, kiểm tra scheme `http`/`https`, host/đích theo allowlist phù hợp**, rồi render bằng cơ chế attribute an toàn. Tránh kiểm tra URL bằng phép `startsWith("https")` đơn giản, vì còn cần xác thực cú pháp và các biến thể bất thường.

Nếu frontend tạo URL bằng JavaScript, `encodeURIComponent(value)` chỉ dùng để encode **một thành phần** như query parameter; nó không kiểm tra an toàn của cả URL. Dùng `new URL(...)` và chính sách scheme/host cho URL động.

### 5.2. JavaScript embedded trong HTML

Không dùng:

~~~html
<!-- Không an toàn: giá trị động tự ghép vào JavaScript source -->
<script>
  const message = '/* USER_INPUT_GHEP_THO_VAO_DAY */';
</script>
~~~

Một input chứa dấu nháy, backslash hay đoạn trông giống đóng script có thể phá ngữ cảnh nếu thao tác escape không phù hợp. Cách ưu tiên là **không tạo inline script từ dữ liệu user**: đặt text vào attribute do template escape, rồi dùng một JavaScript file tĩnh đọc qua `dataset` và đưa ra UI bằng `textContent` (ví dụ thực hành ở phần 7).

Nếu buộc phải dùng script do Thymeleaf render, chế độ `th:inline="javascript"` và **escaped inlining** `[[...]]` cho phép trình tuần tự hóa Thymeleaf tạo JavaScript literal, bao gồm dấu quote và escaping. Dạng minh họa:

~~~html
<script th:inline="javascript">
  const message = /*[[${comment}]]*/ "default";
</script>
~~~

**Không chuyển sang** `[(...)]` để bỏ escape. Cũng không tạo raw JavaScript statement, event-handler code hoặc tên biến từ untrusted input. Tính an toàn của inline script phải xét cả ngữ cảnh **HTML script element** và **JavaScript string**; kiểm thử các chuỗi đặc biệt như dấu quote, dấu `<` và chuỗi kết thúc script. Thiết kế dùng external JS + escaped DOM attribute thường dễ kiểm soát CSP hơn.

### 5.3. CSS và các ngữ cảnh nguy hiểm

Không đặt input tự do vào CSS selector, tên property, toàn bộ `style` hoặc cú pháp `url(...)`. Nếu cho user chọn màu nền, hãy giới hạn một tập giá trị:

~~~java
Set<String> allowedColors = Set.of("red", "blue", "green");
String chosen = allowedColors.contains(color) ? color : "blue";
~~~

Đoạn trên chỉ minh họa nằm trong method Java có `color` và import `Set`. Cách tốt hơn là ánh xạ giá trị nghiệp vụ sang **CSS class cố định** ở server hoặc frontend, thay vì tự ghép style text. Dữ liệu dynamic trong HTML comment, tên thẻ, tên attribute, raw JavaScript hay raw style block cũng là các **ngữ cảnh nguy hiểm**; không cố giải quyết bằng cách escape một vài ký tự.

## 6. Giới hạn nội dung động ở nhiều lớp

“Giới hạn” không chỉ là cắt chuỗi. Một view an toàn nên đưa ra **đúng lượng dữ liệu cần thiết và đúng định dạng được phép**:

1. **Giới hạn độ dài:** ví dụ bình luận preview tối đa 200 ký tự hoặc nội dung bài viết tối đa theo nghiệp vụ. Giới hạn giúp kiểm soát UX, chi phí render, kích thước response và các hình thức lạm dụng tài nguyên; nó **không tự loại bỏ XSS**.
2. **Giới hạn field và data exposure:** controller chỉ đưa những thuộc tính cần hiển thị vào `Model`. Không đưa password hash, access token, internal error details, nguyên entity có dữ liệu nhạy cảm vào template.
3. **Validation theo schema:** kiểu dữ liệu, phạm vi, enum, ID, quyền sở hữu, độ dài; không loại bỏ tùy tiện mọi ký tự `<` hoặc `&` nếu chúng hợp lệ về nghiệp vụ.
4. **Giới hạn loại markup:** nếu cho phép rich text, chỉ hỗ trợ một subset HTML cần thiết; không dùng regex thay thế sanitizer parser-based.
5. **Giới hạn URL/media:** kiểm soát scheme, host, loại MIME/file, đường dẫn tài nguyên và quyền xem; một link được escape vẫn có thể điều hướng người dùng đến trang lừa đảo.
6. **Giới hạn script và resource của trình duyệt:** đặt CSP, tránh inline script, quản lý third-party script có chủ đích. CSP bổ sung cho template escaping, không thay thế nó.
7. **Giới hạn lúc truy cập:** authorization kiểm tra người xem có được lấy dữ liệu hay không trước khi đưa dữ liệu vào model.

Với plain text hiển thị từ database, việc lưu và truyền dưới dạng string thông thường rồi render bằng `th:text` gần như luôn hợp lý hơn cơ chế tự build HTML string. Với nội dung nhiều dòng, có thể dùng CSS `white-space: pre-wrap` cố định cho vùng text; không cần chuyển `\n` thành `<br>` bằng `th:utext`.

## 7. Ví dụ Spring Boot 3.x chạy được: render text an toàn

Tạo project bằng Spring Initializr: **Java 17+**, **Spring Boot 3.x**, **Maven**, dependencies **Spring Web**, **Thymeleaf** và **Spring Boot Starter Test**. Với Maven, các starter tương ứng là `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-test`. Package là `com.example.demo`.

Cấu trúc file:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/SafeViewController.java
src/main/resources/templates/preview.html
src/main/resources/static/js/preview.js
src/test/java/com/example/demo/SafeViewControllerTest.java
~~~

### 7.1. File khởi động

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

### 7.2. Controller: giới hạn độ dài và chỉ đưa text vào model

**`src/main/java/com/example/demo/SafeViewController.java`**

~~~java
package com.example.demo;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/pages")
public class SafeViewController {

    @GetMapping("/preview")
    public String preview(
            @RequestParam(defaultValue = "Xin chào") String message,
            Model model) {

        return render(message, model);
    }

    @GetMapping("/search")
    public String search(@RequestParam("q") String q, Model model) {
        return render(q, model);
    }

    private String render(String message, Model model) {
        if (message.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Text is too long");
        }

        model.addAttribute("message", message);
        return "preview";
    }
}
~~~

**Giải thích:**

- Cả hai endpoint dùng **`@Controller`** và trả logical view `"preview"` để Thymeleaf tạo HTML.
- Tham số `message` hoặc `q` có thể mang văn bản nhập từ trình duyệt. `render` từ chối input dài hơn 200 UTF-16 code units theo quy tắc của `String.length()`; giới hạn này **chỉ là ví dụ UX/tài nguyên**, không phải điều kiện an toàn XSS.
- Giá trị lưu trong model **vẫn là text gốc**; Thymeleaf thực hiện encoding đúng lúc đưa ra HTML.
- App không chứa database hoặc hệ thống phân quyền; production cần kiểm tra quyền và data exposure nếu hiển thị dữ liệu riêng tư.

### 7.3. Template: text, attribute, URL và data attribute an toàn

**`src/main/resources/templates/preview.html`**

~~~html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <title>Xem trước nội dung</title>
  <script th:src="@{/js/preview.js}" defer></script>
</head>
<body>
  <h1>Xem trước</h1>

  <!-- HTML text context: Thymeleaf escape dữ liệu. -->
  <p id="safe-text" th:text="${message}">Văn bản xem trước</p>

  <!-- HTML attribute context: giữ tên attribute cố định. -->
  <label for="input-preview">Dữ liệu</label>
  <input id="input-preview" type="text" th:value="${message}">

  <!-- URL parameter context, đích /pages/search cố định. -->
  <a id="search-link" th:href="@{/pages/search(q=${message})}">
    Xem kết quả tìm kiếm
  </a>

  <!-- Truyền dữ liệu đã encode qua attribute, không chèn JS source. -->
  <div id="client-data" th:attr="data-message=${message}"></div>
  <p id="client-text"></p>
</body>
</html>
~~~

**Vì sao template này an toàn đối với dữ liệu đang xử lý?**

- `th:text` render dữ liệu thành HTML text đã escape.
- `th:value` và `th:attr` đưa dữ liệu vào attribute **được định tên cố định**, có quoting/encoding theo template engine.
- `th:href="@{/pages/search(q=${message})}"` dùng một đường dẫn nội bộ đã quyết định ở server; dữ liệu chỉ nằm ở query parameter.
- Không có `th:utext`, không có inline JavaScript ghép input, không có `href` động nguyên URL, và không có HTML tự nối từ user input.

### 7.4. JavaScript external: dùng `textContent`

**`src/main/resources/static/js/preview.js`**

~~~javascript
"use strict";

document.addEventListener("DOMContentLoaded", () => {
  const source = document.getElementById("client-data");
  const output = document.getElementById("client-text");

  if (!source || !output) {
    return;
  }

  const text = source.dataset.message ?? "";
  output.textContent = text;
});
~~~

`dataset.message` lấy giá trị đã được browser phân tích từ HTML attribute. `textContent` tạo **text node**, không parse HTML trong chuỗi dữ liệu.

**Không thay bằng:**

~~~javascript
// KHÔNG AN TOÀN cho dữ liệu không đáng tin cậy:
output.innerHTML = text;
~~~

`innerHTML` yêu cầu browser parse text như HTML source; nó có thể tạo element/attribute nguy hiểm. Các API như `outerHTML`, `insertAdjacentHTML` và `document.write` cũng cần được kiểm soát nghiêm ngặt. Nếu muốn tạo DOM node có cấu trúc, dùng `document.createElement`, thiết lập attribute cố định và gán text qua `textContent`.

### 7.5. Chạy và quan sát payload

Ở thư mục dự án Maven:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows dùng `mvnw.cmd spring-boot:run`. Nếu không có Maven Wrapper, dùng `mvn spring-boot:run` khi Maven đã cài.

Gửi test string có thẻ HTML bằng `curl`:

~~~bash
curl -G -i "http://localhost:8080/pages/preview"   --data-urlencode 'message=<img src=x onerror=alert(1)>'
~~~

Cú pháp xuống dòng trên dùng dấu `\` trong Linux/macOS/Git Bash. Trên Windows PowerShell có thể dùng `curl.exe` trên một dòng và xử lý quoting theo PowerShell.

**Kết quả cần quan sát:**

- Status 200, `Content-Type` là HTML.
- HTML response ở vùng `id="safe-text"` sẽ có các entity escape tương ứng, chẳng hạn:

~~~html
<p id="safe-text">&lt;img src=x onerror=alert(1)&gt;</p>
~~~

- Trong trình duyệt, đoạn đó hiển thị **như chữ**, không xuất hiện phần tử `img` do user input tạo ra.
- JavaScript external cũng gán chuỗi vào `#client-text` bằng `textContent`, nên không tạo HTML element mới.
- Link `/pages/search?q=...` có tham số được encode phù hợp; đích liên kết vẫn là đường dẫn nội bộ cố định.

Giới hạn của ví dụ: `String.length()` đếm UTF-16 code units chứ không phải số grapheme nhìn thấy của tiếng Việt/emoji. Với sản phẩm cần giới hạn theo ký tự người dùng nhìn thấy hoặc byte trên wire, phải định nghĩa quy tắc và dùng công cụ đếm phù hợp.

## 8. Kiểm thử HTML đã render bằng MockMvc

Chỉ test `model().attribute("message", input)` **chưa đủ** để khẳng định HTML được escape; controller có thể bind đúng nhưng template lại dùng `th:utext`. Bài test dưới đây chạy **cả ứng dụng + template Thymeleaf** bằng `@SpringBootTest` và `@AutoConfigureMockMvc`.

**`src/test/java/com/example/demo/SafeViewControllerTest.java`**

~~~java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SafeViewControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void untrustedMarkupIsRenderedAsText() throws Exception {
        String payload = "<img src=x onerror=alert(1)>";

        String html = mockMvc.perform(get("/pages/preview")
                        .param("message", payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains(
                "&lt;img src=x onerror=alert(1)&gt;"));
        assertFalse(html.contains(
                "<img src=x onerror=alert(1)>"));
    }

    @Test
    void quotesStayInsideInputAttribute() throws Exception {
        String payload = "\" autofocus onfocus=\"alert(1)";

        String html = mockMvc.perform(get("/pages/preview")
                        .param("message", payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Dấu nháy bên trong dữ liệu không trở thành attribute mới.
        assertFalse(html.contains(
                "value=\"\" autofocus onfocus="));
        assertTrue(html.contains("id=\"input-preview\""));
    }

    @Test
    void rejectsExcessiveInputLength() throws Exception {
        mockMvc.perform(get("/pages/preview")
                        .param("message", "a".repeat(201)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchUsesSameSafeView() throws Exception {
        mockMvc.perform(get("/pages/search")
                        .param("q", "<b>Hello</b>"))
                .andExpect(status().isOk());
    }
}
~~~

Chạy:

~~~bash
./mvnw test
~~~

Các assert trên kiểm chứng HTML response bằng việc **render template thật** trong MockMvc integration. Test đầu yêu cầu `<img...>` bị escape trong HTML, test thứ hai kiểm tra dữ liệu có dấu nháy không trở thành attribute mới, test cuối xác minh giới hạn độ dài.

**Giới hạn kiểm thử:** MockMvc không chạy JavaScript của browser. Để xác nhận DOM-based XSS và việc không có script thực thi, bổ sung browser integration/security tests với dữ liệu đặc biệt và môi trường cô lập. Có thể dùng DOM assertions dựa trên parser HTML (thay vì so khớp substring) để chứng minh không có element hoặc event-handler do input user tạo ra. Test nguồn là chỉ báo quan trọng, nhưng bảo vệ XSS cần xem toàn bộ sink và đường dữ liệu.

## 9. Rich text thật sự cần HTML: sanitize bằng allowlist

Một công cụ nhập nội dung WYSIWYG có thể cần cho phép **in đậm, in nghiêng, danh sách và link**. Nếu áp dụng `th:text`, tag sẽ hiển thị dưới dạng chữ, không giữ format. Trong trường hợp này, giải pháp là **HTML sanitization** trước khi render unescaped, bằng một thư viện được duy trì và chính sách cho phép thật rõ.

Ví dụ **minh họa độc lập** với thư viện [OWASP Java HTML Sanitizer](https://github.com/OWASP/java-html-sanitizer) (phải tự thêm dependency tương ứng với phiên bản dự án):

~~~java
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

public class RichTextSanitizer {
    private static final PolicyFactory POLICY =
            Sanitizers.FORMATTING.and(Sanitizers.LINKS);

    public static String sanitize(String input) {
        return POLICY.sanitize(input == null ? "" : input);
    }
}
~~~

Chính sách có sẵn ở đây chỉ để giới thiệu API: `FORMATTING` và `LINKS` cho phép tập markup nhất định, đồng thời thư viện lọc theo quy tắc của nó. Trong production, **thiết kế allowlist cụ thể** cho tag/attribute/protocol cần dùng, thường cấm script, event-handler attributes và URL scheme không được phép. Nên kiểm thử cả link, SVG, MathML, tag lồng nhau và những trường hợp parse phức tạp; cập nhật bản vá thư viện đều đặn.

Sau khi sanitize:

~~~java
String safeHtml = RichTextSanitizer.sanitize(rawHtml);
model.addAttribute("safeHtml", safeHtml);
~~~

Chỉ khi `safeHtml` đã qua chính sách đáng tin, template có thể chủ động render:

~~~html
<div th:utext="${safeHtml}"></div>
~~~

**Quan trọng:** `th:utext` ở đây vẫn là một sink rủi ro; nó chỉ phù hợp khi giá trị vào đã được sanitize đúng yêu cầu. Không để code path khác đưa `rawHtml` trực tiếp vào cùng attribute. Không nối thêm HTML thô không đáng tin vào **sau** sanitization. Không coi sanitize HTML là đủ cho dữ liệu sau đó đưa vào JavaScript/CSS/URL; mỗi output context vẫn có quy tắc riêng.

Đối với phần lớn trang hiển thị tên sản phẩm, địa chỉ, bình luận văn bản, trạng thái, thông báo, **không cần `th:utext`**. Giữ dữ liệu như text và render bằng `th:text` là an toàn và dễ bảo trì hơn.

## 10. JSP, Spring `HtmlUtils` và những đường render khác

Nếu dự án dùng JSP/JSTL, một cách render text có escape là:

~~~jsp
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<p><c:out value="${comment}" /></p>
~~~

Ví dụ này dành cho môi trường Jakarta Tags 3.x/JSP tương thích Spring Boot 3 và có dependency/taglib được cấu hình. `c:out` với mặc định escape XML giúp hiển thị dữ liệu như text trong nhiều ngữ cảnh HTML text đơn giản. Không sử dụng output text/XML escaping này để tự xây JavaScript source, CSS hoặc URL executable.

Spring Framework cũng có `org.springframework.web.util.HtmlUtils.htmlEscape(String)`, ví dụ trong Java:

~~~java
String escaped = HtmlUtils.htmlEscape("<b>Hello</b>");
System.out.println(escaped);
~~~

Khi có import `HtmlUtils` và chạy trong project có Spring Web, stdout về mặt ý nghĩa sẽ là:

~~~text
&lt;b&gt;Hello&lt;/b&gt;
~~~

Đây là **HTML escaping cho văn bản**, không thay thế JavaScript encoding, URL validation hay sanitizer. Thông thường bạn không cần gọi `HtmlUtils.htmlEscape` trước Thymeleaf `th:text` vì có thể gây **double-encoding**.

Nếu tự trả HTML từ REST controller:

~~~java
return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .body("<p>" + untrusted + "</p>");
~~~

Đây là **mẫu phản diện**: concatenation có thể đưa `untrusted` trực tiếp vào HTML source. Chọn Thymeleaf, hoặc dùng API encoder đúng ngữ cảnh trước khi build HTML thật sự. Không gọi escaping thủ công tại ngẫu nhiên một điểm rồi tái sử dụng encoded string ở các ngữ cảnh khác.

## 11. CSP, cookie flags và Trusted Types

Output encoding và HTML sanitization xử lý nguyên nhân trực tiếp. Các cơ chế khác bổ sung lớp kiểm soát:

### Content Security Policy (CSP)

Ví dụ **ý tưởng chính sách**, cần kiểm thử với các resource thực tế của ứng dụng:

~~~http
Content-Security-Policy: default-src 'self'; script-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'
~~~

Chính sách này giới hạn script tới origin hiện tại, không cho object/plugin, kiểm soát base URI và embedding. Với ứng dụng có inline script, CDN, iframe cần thiết hoặc chính sách tương thích browser khác, phải chỉnh policy, sử dụng nonce/hash khi cần và triển khai dần bằng report-only mode.

**CSP không phải “thuốc chữa XSS”**: cấu hình quá rộng như `unsafe-inline` có thể làm suy yếu lợi ích; script từ origin được cho phép hoặc logic DOM sai vẫn có thể nguy hiểm. Kiểm thử template escaping trước; CSP là defense in depth.

### Trusted Types

Một số browser dựa trên Chromium hỗ trợ **Trusted Types** kết hợp CSP `require-trusted-types-for 'script'`, giúp kiểm soát việc đưa plain string vào những DOM sink nguy hiểm như `innerHTML`. Nếu triển khai, cần chuẩn bị policy được kiểm duyệt và kế hoạch tương thích với thư viện frontend. Trusted Types không thay quyền kiểm tra input hoặc output encoding.

### Cookie flags

`HttpOnly`, `Secure`, `SameSite` là các flag cookie có ích cho quản lý session và giảm hậu quả một số tấn công. Chúng **không ngăn việc mã JavaScript độc hại thực thi trên trang** nếu XSS đã xảy ra. Đừng cho rằng cookie HttpOnly làm HTML render không cần escape.

## 12. Cách kiểm thử XSS một cách có hệ thống

Tạo ma trận **nguồn → sink** cho các dữ liệu động thực sự xuất hiện:

| Nguồn | Sink | Kiểm tra quan trọng |
| --- | --- | --- |
| Query parameter `message` | Thymeleaf `th:text` | Chuỗi chứa tag không tạo element mới |
| Form field `name` | `input th:value` | Dấu nháy không thoát attribute |
| Text tìm kiếm | `th:href` với query param | Đường dẫn giữ nguyên, tham số được encode |
| Dữ liệu từ DB | Comment/list trong template | Stored markup hiển thị an toàn |
| Dữ liệu trong `data-*` | DOM `textContent` | Không dùng `innerHTML` sau khi đọc |
| Rich text WYSIWYG | Vùng `th:utext` có kiểm soát | Sanitizer chỉ giữ subset HTML đã định nghĩa |
| URL do user cung cấp | `href` động | Scheme, host, redirect target thuộc allowlist |
| Dữ liệu cần gửi vào script | JS serializer hoặc external JS | Quote, slash, closing script và ngữ cảnh được xử lý |

Với quy trình QA cho ứng dụng, kết hợp:

1. **Unit tests** cho validator, URL allowlist, rich-text sanitizer.
2. **MockMvc / integration tests** render Thymeleaf thật với dấu `<`, `>`, `&`, quote và chuỗi giống markup; xác nhận DOM hoặc output encoding.
3. **Browser tests** cho DOM sink, link điều hướng và CSP trong trình duyệt thật.
4. **Code review** tập trung vào `th:utext`, `[(...)]`, `innerHTML`, `outerHTML`, `document.write`, raw HTML concatenation, dynamic `href`, event-handler attribute và template injection.
5. **Dependency updates** đối với template engine, sanitizer và frontend libraries, đặc biệt khi xử lý rich text.

Không nên chỉ tìm một payload cố định rồi tuyên bố hết XSS. Mỗi đường dữ liệu mới, đặc biệt các tính năng user-generated content, cần được đối chiếu ngữ cảnh output của nó.

## 13. Những lỗi thường gặp

### Lỗi 1. Nghĩ rằng `Model` sẽ tự loại bỏ HTML

`model.addAttribute("comment", untrusted)` chỉ lưu thuộc tính vào model. **Thymeleaf `th:text`** mới encode giá trị cho HTML text ở lúc render. Nếu template dùng `th:utext`, kết quả khác hoàn toàn.

### Lỗi 2. Dùng `th:utext` để giữ xuống dòng

Nếu mục tiêu chỉ là giữ newline, dùng text escaping và CSS:

~~~html
<p class="comment" th:text="${comment}">Bình luận</p>
<style>
  .comment { white-space: pre-wrap; }
</style>
~~~

Không cần đổi text thành `<br>` bằng HTML string. CSS này cố định, không lấy từ user.

### Lỗi 3. HTML-escape rồi đưa sang JavaScript string

HTML entity encoding không đảm bảo an toàn của JavaScript context. Dùng serializer được thiết kế cho JS literal và ngữ cảnh HTML script, hoặc external script đọc dữ liệu từ DOM rồi dùng `textContent`.

### Lỗi 4. Escape `href` là đủ

Một URL sai scheme hoặc trỏ đến site lừa đảo có thể vẫn nguy hiểm khi attribute được HTML-escaped hợp lệ. **Validate URL theo scheme/host**, chỉ cho phép những đích nghiệp vụ yêu cầu.

### Lỗi 5. Sanitize HTML bằng regex

HTML có nested markup, attribute encoding, namespace, URL scheme và nhiều cú pháp parser phức tạp. Regex thường không mô phỏng chính xác browser parser. Dùng sanitizer được duy trì với allowlist và bộ test dành cho rich text.

### Lỗi 6. Gọi `innerHTML` cho text từ response API

Ngay cả khi response API là JSON (media type `application/json`) và dữ liệu lấy từ server, chuỗi vẫn có thể chứa HTML nguy hiểm. **`textContent`** hoặc DOM APIs có kiểm soát giúp render như dữ liệu. JSON không tự sanitize mọi sink ở trình duyệt.

### Lỗi 7. Encode sẵn rồi encode lại

Lưu `&lt;b&gt;` vào DB rồi `th:text` tiếp có thể hiển thị `&lt;b&gt;` dưới dạng chữ do double encoding. Cần phân biệt raw data, sanitized HTML và context-specific encoded output trong hợp đồng dữ liệu.

### Lỗi 8. Cắt ngắn chuỗi rồi tưởng chống XSS

Giới hạn 200 ký tự giúp kiểm soát UX/tài nguyên, nhưng nhiều payload nguy hiểm rất ngắn. Validation về độ dài **bổ sung** cho encoding theo output context; nó không thay thế biện pháp chống XSS.

### Lỗi 9. Dựa hoàn toàn vào CSP hoặc WAF

CSP là defense in depth; WAF có thể bỏ sót DOM XSS hoặc payload mới. Kiểm soát output encoding/sanitization, nguồn dữ liệu và DOM sink vẫn là chính.

### Lỗi 10. Chỉ kiểm tra model, bỏ qua template

MockMvc `model().attribute("comment", payload)` chỉ chứng minh controller gửi dữ liệu đúng. Muốn kiểm tra XSS ở server-rendered view cần **render template** và kiểm tra response/DOM output. Đây là lý do ví dụ sử dụng `@SpringBootTest` + `@AutoConfigureMockMvc`.

## 14. Câu hỏi ôn tập

**1. Vì sao `th:text` thường phù hợp hơn `th:utext` cho bình luận người dùng?**

Vì `th:text` encode HTML và hiển thị ký tự dữ liệu như text, còn `th:utext` cho phép HTML parser diễn giải markup. Nếu cần rich text, phải sanitize bằng allowlist đáng tin trước.

**2. Nếu một input chỉ chứa 20 ký tự thì chắc chắn không gây XSS?**

Không. Giới hạn độ dài là quy tắc tài nguyên/UX; XSS phụ thuộc vào việc dữ liệu được giải thích như code/markup ở ngữ cảnh output hay không.

**3. Dùng Thymeleaf link expression `@{/search(q=${query})}` giải quyết được mọi nguy cơ URL?**

Không. Nó giúp xây URL parameter trong đường dẫn cố định. Nếu cả `href` là URL do user điều khiển, phải kiểm tra scheme/host/chính sách redirect.

**4. Vì sao `output.textContent = value` phù hợp khi chỉ hiển thị text?**

Vì nó tạo hoặc thay **text content** của DOM node, không yêu cầu parser đọc chuỗi như HTML. `innerHTML` thì có.

**5. Khi nào cần sanitizer?**

Khi sản phẩm thật sự cho phép người dùng cung cấp một subset HTML được render, ví dụ editor có format. Sanitizer parser-based phải lọc theo chính sách allowlist và được bảo trì; plain text thông thường nên escape tại view.

**6. `HttpOnly` cookie có loại bỏ XSS không?**

Không. Nó hạn chế JavaScript đọc cookie được đánh dấu HttpOnly, nhưng không làm JS injection bất khả thi và không bảo đảm thao tác trang không bị chiếm quyền.

## 15. Checklist trước khi đưa giao diện lên production

1. Xác định mọi trường động từ request/database/third-party và **vị trí cuối** chúng được render.
2. Với HTML text dùng `th:text`/`[[...]]`; với attribute thường dùng Thymeleaf attribute processors và quote; không ghép HTML raw từ input.
3. Tránh event-handler attributes, `th:utext`, `[(...)]` và các DOM HTML sinks cho dữ liệu không đáng tin.
4. Với URL, xác thực scheme/host/đích trước, sau đó encode tham số và attribute theo ngữ cảnh.
5. Với JavaScript, dùng external script + escaped DOM data và `textContent` khi phù hợp; nếu cần inline, dùng serializer đúng và test HTML script context.
6. Với rich text, dùng HTML sanitizer theo allowlist, có test, kiểm soát nơi dữ liệu sanitized được đưa vào; cập nhật thư viện.
7. Giới hạn input và dữ liệu đưa vào model theo nghiệp vụ, nhưng **vẫn escape output** và kiểm tra quyền truy cập.
8. Bật CSP phù hợp, xem xét Trusted Types nếu có frontend DOM phức tạp; coi chúng là biện pháp bổ sung.
9. Test HTML template thực tế, browser DOM, những chuỗi đặc biệt và các trường hợp sai quyền; không chỉ test controller/model.

## Tài liệu chính thức và tham khảo bảo mật

- [Thymeleaf 3.1 — Using Thymeleaf: escaped/unescaped text, attributes, JavaScript inlining](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html)
- [OWASP — Cross Site Scripting Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [OWASP — DOM Based XSS Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/DOM_based_XSS_Prevention_Cheat_Sheet.html)
- [OWASP — HTML Sanitization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/HTML_Sanitization_Cheat_Sheet.html)
- [Spring Framework 6.2 — View Technologies](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc-view.html)
- [Spring Framework Javadoc — HtmlUtils](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/util/HtmlUtils.html)
- [OWASP Java HTML Sanitizer](https://github.com/OWASP/java-html-sanitizer)


</details>

- [Quay lại đầu trang](#back-to-top)
