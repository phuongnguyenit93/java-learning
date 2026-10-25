<a id="back-to-top"></a>

# Binding và validation

## Menu
- [WebDataBinder và quá trình chuyển dữ liệu chuỗi thành đối tượng](#binding-validation-001)
- [Constructor binding, property binding và thiết kế DTO đầu vào](#binding-validation-002)
- [Converter, Formatter, ConversionService và định dạng ngày giờ](#binding-validation-003)
- [@InitBinder: cấu hình cục bộ và dùng chung qua @ControllerAdvice](#binding-validation-004)
- [Giới hạn trường được binding để tránh sửa thuộc tính ngoài hợp đồng](#binding-validation-005)
- [Bean Validation với @Valid, @Validated và nhóm ràng buộc](#binding-validation-006)
- [Errors, BindingResult và vị trí tham số nhận lỗi](#binding-validation-007)
- [Phân biệt lỗi chuyển đổi kiểu, lỗi binding và lỗi kiểm tra dữ liệu](#binding-validation-008)
- [Kiểm tra tham số phương thức và phân biệt các ngoại lệ validation theo phiên bản](#binding-validation-009)

## <a id="binding-validation-001">WebDataBinder và quá trình chuyển dữ liệu chuỗi thành đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# WebDataBinder và quá trình chuyển dữ liệu chuỗi thành đối tượng trong Spring MVC

## 1. Học được gì sau bài này?

Khi trình duyệt gửi biểu mẫu `quantity=3&deliveryDate=2026-12-24&code=sku-007`, mọi giá trị đầu vào đều có thể xuất phát dưới dạng chuỗi, nhưng controller lại cần `Integer`, `LocalDate` và một kiểu miền nghiệp vụ như `ProductCode`. **Data binding** là quá trình kết nối tên tham số với thuộc tính hoặc tham số constructor của đối tượng Java, đồng thời thực hiện **type conversion** cần thiết. `WebDataBinder` là thành phần chuyên biệt cho tình huống web của Spring MVC.

Sau bài học, bạn sẽ biết:

- `WebDataBinder` tham gia luồng xử lý `@ModelAttribute` thế nào; đâu là nhiệm vụ của `DataBinder`, `ConversionService`, `Formatter` và `PropertyEditor`.
- Từ `String` đầu vào đến object Java: ánh xạ tên, tạo đối tượng, gán thuộc tính hoặc gọi constructor, chuyển kiểu, ghi nhận lỗi.
- Phân biệt cấu hình conversion **toàn cục** và cấu hình binder **theo controller/model attribute** bằng `WebMvcConfigurer` và `@InitBinder`.
- Đọc lỗi qua `BindingResult`; phân biệt lỗi chuyển kiểu, trường không được bind và kiểm tra hợp lệ nghiệp vụ.
- Bảo vệ object khỏi việc client gán vào thuộc tính không được phép (`mass assignment`) bằng DTO và `allowedFields`.
- Tự chạy và kiểm thử một ứng dụng Spring Boot 3.x / Spring MVC với Java 17+.

**Phạm vi:** Spring MVC chạy trên Servlet stack (Spring Framework 6.x). `WebFlux` có luồng HTTP khác; `@RequestBody` đọc JSON qua `HttpMessageConverter`, không tương đương với việc bind tham số form bằng `WebDataBinder`.

## 2. WebDataBinder nằm ở đâu trong chuỗi xử lý?

Để hiểu đúng cơ chế, cần phân biệt ba khái niệm:

1. **Binding:** tìm giá trị nguồn theo tên `customer`, `quantity` hoặc đường dẫn `address.city`, rồi đưa chúng vào đích tương ứng.
2. **Conversion/formatting:** biến `"3"` thành `Integer(3)`, `"2026-12-24"` thành `LocalDate`, `"sku-007"` thành `ProductCode`; khi hiển thị form có thể định dạng ngược đối tượng thành văn bản.
3. **Validation:** sau binding, đánh giá quy tắc như `quantity > 0` hoặc `customer` không rỗng. Giá trị chuyển kiểu thành công chưa chắc hợp lệ về nghiệp vụ.

Sơ đồ khái quát với `@ModelAttribute`:

```text
POST /orders/form
Content-Type: application/x-www-form-urlencoded

customer=Ada&quantity=3&deliveryDate=2026-12-24&code=sku-007
       │
       ▼
DispatcherServlet → RequestMappingHandlerAdapter
       │
       ▼
Bộ phân giải tham số @ModelAttribute
       │
       ├─ Tìm hoặc tạo object đích (OrderForm)
       ├─ Tạo và khởi tạo WebDataBinder
       │    ├─ Cấu hình ConversionService/Formatter/PropertyEditor
       │    ├─ Chạy @InitBinder phù hợp
       │    └─ Áp dụng allowedFields và các quy tắc binding
       ├─ Bind dữ liệu vào object
       │    └─ Chuyển String → kiểu Java, ghi lỗi nếu thất bại
       ├─ Chạy validation nếu được yêu cầu, ví dụ @Valid
       └─ Cung cấp OrderForm + BindingResult cho controller
       │
       ▼
Controller xử lý object đã bind / lỗi
```

`org.springframework.validation.DataBinder` xử lý binding và validation ở mức tổng quát. `org.springframework.web.bind.WebDataBinder` mở rộng cho ngữ cảnh web, gồm các quy tắc làm việc với tham số request. Spring MVC thường tạo binder cho **từng đối tượng cần bind ở request**, thông qua cơ sở hạ tầng `WebDataBinderFactory`; không lưu một `WebDataBinder` đang thao tác vào singleton service để tái dùng giữa nhiều request.

`BeanWrapper` hoặc cơ chế truy cập property của Spring hỗ trợ tìm thuộc tính JavaBean; `ConversionService`/formatter/editor xử lý bước biến đổi kiểu. `BindingResult` giữ cả lỗi binding và validation. Không coi `WebDataBinder` là JSON parser hoặc cơ chế routing URL.

### Những nguồn dữ liệu nào được bind?

Với `@ModelAttribute` trong Spring MVC, tên trường có thể lấy từ request parameters (query string và form-urlencoded data), URI variables và request headers ở những tình huống phù hợp. Tham số request có ưu tiên hơn URI variables/header nếu trùng tên. Một object có thể đến từ model/session, được tạo bằng constructor, hoặc được tìm qua converter trong một số trường hợp.

| Controller argument | Input điển hình | Cơ chế chính |
| --- | --- | --- |
| `@RequestParam Integer page` | `?page=3` | Chuyển giá trị tham số sang kiểu method argument |
| `@PathVariable UUID id` | `/orders/{id}` | Chuyển giá trị path variable |
| `@RequestHeader LocalDate date` | HTTP header tương ứng, nếu có converter phù hợp | Chuyển giá trị header |
| `@ModelAttribute OrderForm form` | `customer=Ada&quantity=3` | Tạo/tìm object, bind nhiều property, có thể validate |
| `@RequestBody OrderJson body` | JSON như `{"quantity":3}` | `HttpMessageConverter` (thường là Jackson), rồi có thể validate |

`@RequestParam` và `@PathVariable` cũng tận dụng hệ thống chuyển kiểu và có thể được tùy chỉnh thông qua cấu hình conversion hoặc binder phù hợp; nhưng chúng **không** tạo một object form nhiều thuộc tính như `@ModelAttribute`.

## 3. Từ chuỗi đến object: điều gì thực sự xảy ra?

Xét form:

```text
customer=Ada
quantity=3
deliveryDate=2026-12-24
code=sku-007
```

Và Java bean:

```java
public class OrderForm {
    private String customer;
    private Integer quantity;
    private LocalDate deliveryDate;
    private ProductCode code;

    public OrderForm() { }

    public void setCustomer(String customer) { this.customer = customer; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }
    public void setCode(ProductCode code) { this.code = code; }

    // Các getter được lược bỏ trong đoạn minh họa này.
}
```

*Đây là đoạn minh họa riêng của model; chương 7 có ví dụ project đầy đủ với import, getter, setter và test.*

Trình tự khái quát:

1. Spring xác định tên model attribute (`orderForm`), tìm object đã có hoặc tạo `OrderForm`.
2. `WebDataBinder` đối chiếu các trường nguồn với property đích cùng tên. Với binding kiểu JavaBean, setter/đường dẫn property phải phù hợp.
3. `customer` vốn là `String` nên gán trực tiếp; `quantity` cần converter sang `Integer`; `deliveryDate` cần quy tắc parse ngày; `code` cần quy tắc tùy biến sang `ProductCode`.
4. Nếu chuyển đổi thất bại, binder ghi `FieldError`, ví dụ field `quantity` với lỗi `typeMismatch`. Không được giả định Spring sẽ âm thầm đổi `"abc"` thành `0`.
5. Nếu method có `@Valid`/`@Validated` phù hợp, validation chạy sau khi có đối tượng đã bind và lỗi có thể được cộng thêm vào `BindingResult`.
6. Controller chỉ nên xử lý nghiệp vụ khi trạng thái binding và validation đã đạt yêu cầu.

Tên property có thể lồng nhau, ví dụ `address.city`, `items[0].sku`. Binding lồng nhau phụ thuộc vào cấu trúc object, setter/constructor, khả năng auto-grow và chính sách giới hạn của binder. Không mở rộng tùy tiện sang bất kỳ property nào client tự gửi: một field nhạy cảm như `role` hoặc `isAdmin` có thể bị gán trái phép nếu thiết kế object thiếu an toàn.

### Constructor binding và property binding

`@ModelAttribute` có thể khởi tạo object qua constructor có tham số; Spring cũng có thể bind các thuộc tính có thể ghi sau đó. Với Java record:

```java
public record SearchFilter(String keyword, Integer page) { }
```

Input `keyword=spring&page=2` có thể dùng để tạo `SearchFilter("spring", 2)` thông qua constructor binding. Với kiểu bất biến, tên tham số phải được framework nhận biết đúng (cấu hình biên dịch/metadata phù hợp), và tên input phải khớp với tham số; `@BindParam` hỗ trợ đổi tên tham số web trong Spring 6.1+.

Với JavaBean có no-args constructor/setters, property binding là con đường tự nhiên. Đó là cách ví dụ chính phía dưới minh họa. Đối với input không tin cậy, object chuyên dùng cho request và constructor binding hạn chế số điểm có thể gán thường an toàn hơn việc đưa thẳng entity persistence ra binder.

## 4. ConversionService, Converter, Formatter và PropertyEditor

`WebDataBinder` không tự hiểu tất cả các kiểu Java do bạn định nghĩa. Nó dựa trên hệ thống chuyển đổi đã cấu hình.

| Thành phần | Trách nhiệm | Khi nên dùng |
| --- | --- | --- |
| `ConversionService` | API trung tâm `canConvert`/`convert`; điều phối converters | Dùng chung cho nhiều nơi cần chuyển kiểu |
| `Converter<S,T>` | Chuyển một kiểu nguồn sang một kiểu đích | Ví dụ `String → ProductCode`, `String → UUID` (nếu cần tùy biến) |
| `ConverterFactory<S,R>` | Tạo converter cho một họ kiểu đích | Nhiều enum/kiểu con theo cùng quy tắc |
| `GenericConverter` | Quy tắc dựa trên `TypeDescriptor`/generic/annotation | Chuyển đổi cần biết kiểu phần tử hay metadata |
| `Formatter<T>` | Parse văn bản thành `T` và print `T` thành văn bản, có `Locale` | Số/tiền tệ/ngày theo ngôn ngữ/vùng |
| `PropertyEditor` | API JavaBeans cũ, có trạng thái và các thao tác `setAsText` | Tích hợp thư viện cũ hoặc custom editor cục bộ |

### Converter: khi quy tắc không phụ thuộc Locale

Ví dụ mã sản phẩm có định dạng `SKU-007`:

```java
record ProductCode(String value) {}

final class ProductCodeConverter
        implements org.springframework.core.convert.converter.Converter<String, ProductCode> {

    @Override
    public ProductCode convert(String source) {
        String normalized = source.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("SKU-[0-9]{3}")) {
            throw new IllegalArgumentException("Mã sản phẩm phải có dạng SKU-000");
        }
        return new ProductCode(normalized);
    }
}
```

`Converter` nhận `String` và trả về **object miền nghiệp vụ**, không chỉ kiểm tra string. Chuyển `"sku-007"` thành `ProductCode("SKU-007")`; `"abc"` làm quá trình conversion thất bại. `IllegalArgumentException` tại converter là một cách báo đầu vào sai. Converter dùng chung cần an toàn với nhiều thread; tránh cất dữ liệu request trong field mutable của converter.

### Formatter: khi ngày/số phụ thuộc định dạng nhập/xuất

`@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` có thể đặt trên thuộc tính `LocalDate` để biểu diễn ngày ISO `2026-12-24`. `@NumberFormat` hỗ trợ một số trường hợp định dạng số. Khi cần quy tắc phức tạp và phụ thuộc `Locale`, hãy viết `Formatter<T>` với `parse(String, Locale)` và `print(T, Locale)`.

Không lấy `LocalDate.parse(...)` trong từng controller để tự thay thế cơ chế binding chung. Khi viết converter/formatter, cần kiểm tra chuỗi rỗng, khoảng trắng, định dạng không hợp lệ, múi giờ (khi chuyển loại có thông tin thời điểm), và việc parse/print có đối xứng hay không.

### PropertyEditor: hiểu để bảo trì mã cũ

`PropertyEditor` thường được đăng ký trên một binder bằng `registerCustomEditor` trong `@InitBinder`. Một editor có thể giữ giá trị nội bộ, nên không tùy tiện chia sẻ cùng instance mutable giữa requests. Với ứng dụng Spring MVC hiện đại, ưu tiên `Converter`/`Formatter` cho nhu cầu mới.

## 5. Cấu hình conversion toàn cục và @InitBinder theo phạm vi

### Toàn cục qua WebMvcConfigurer

`WebMvcConfigurer#addFormatters(FormatterRegistry)` đăng ký converter hoặc formatter mà cơ chế chuyển kiểu của MVC dùng trên nhiều controller:

```java
@Configuration
class WebBindingConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new ProductCodeConverter());
    }
}
```

*Đoạn này là trích từ ứng dụng đầy đủ ở mục 7; thêm đúng import `@Configuration`, `WebMvcConfigurer` và `FormatterRegistry` như trong ví dụ.*

Ưu điểm là một định nghĩa `String → ProductCode` có thể dùng cho `@RequestParam ProductCode`, `@PathVariable ProductCode` hoặc property kiểu `ProductCode` trong `@ModelAttribute`. Không tạo `ConversionService` mới trong từng controller nếu muốn tận dụng cấu hình MVC hiện có.

### Theo controller/model attribute qua @InitBinder

`@InitBinder` được gọi để tùy chỉnh `WebDataBinder` trước quá trình binding. Có thể đặt trong `@Controller`/`@RestController` cho phạm vi controller; hoặc trong `@ControllerAdvice` khi cần áp dụng cho nhiều controller. `@InitBinder("orderForm")` chỉ nhắm vào model attribute có tên `orderForm`.

```java
@InitBinder("orderForm")
void configureOrderForm(WebDataBinder binder) {
    binder.setAllowedFields("customer", "quantity", "deliveryDate", "code");
}
```

`allowedFields` giúp chỉ cho phép các property đã chỉ định được set bằng property binding. Nếu client gửi thêm `admin=true`, Spring đưa `admin` vào danh sách `getSuppressedFields()` thay vì gán property đó (khi không nằm trong danh sách cho phép). `BindingResult#getSuppressedFields()` giúp bạn phát hiện và quyết định trả lỗi. `setDisallowedFields` cũng có, nhưng danh sách được **cho phép** rõ ràng thường dễ kiểm soát hơn danh sách các tên bị cấm.

Một số cấu hình binder cần biết:

- `setRequiredFields("customer")` yêu cầu trường xuất hiện khi bind; đây là quy tắc hiện diện ở tầng binding, **khác** `@NotBlank` hay kiểm tra ý nghĩa của giá trị.
- `setAllowedFields(...)` và `setDisallowedFields(...)` áp dụng cho tên field được bind, có thể nhận các pattern; không xem chúng là firewall thay cho DTO phù hợp.
- `setDeclarativeBinding(true)` (Spring 6.1+) cho phép thiên về constructor binding; property binding bị hạn chế nếu không cấu hình allowed fields. Hữu ích khi DTO bất biến hoặc muốn chặn sửa property ngoài khai báo.
- Có thể đăng ký `Formatter`/`PropertyEditor` và validator theo binder, nhưng chuyển kiểu dùng chung nên đặt trong cấu hình MVC.
- Nếu muốn lấy object sẵn có từ model mà không cho binding dữ liệu request vào, cân nhắc `@ModelAttribute(binding = false)`.

**Quan trọng về bảo mật:** không dùng persistence entity chứa `role`, `isAdmin`, `creditLimit` làm input model tự do nếu client không được quyền chỉnh các trường ấy. Phân quyền và cập nhật entity nên làm tại service sau khi DTO đã được kiểm tra; `allowedFields` là một lớp bảo vệ bổ sung cho property binding.

## 6. BindingResult: theo dõi lỗi chuyển đổi và validation

`BindingResult` giữ lỗi theo **object** và **field**. Khi controller khai báo nó, hãy đặt **ngay sau** tham số `@ModelAttribute` tương ứng:

```java
@PostMapping("/example")
String submit(@ModelAttribute("orderForm") OrderForm form,
              BindingResult result) {
    if (result.hasErrors()) {
        return "invalid";
    }
    return "ok";
}
```

*Đây là minh họa chữ ký method; mục 7 chứa chương trình chạy đầy đủ.*

Một `FieldError` có thể chứa `field`, `rejectedValue`, `codes` và thông điệp lỗi. Ví dụ `quantity=abc` trong khi đích là `Integer` thường ghi lỗi `typeMismatch` cho `quantity`. Nếu có `@Valid` và Jakarta Bean Validation được cấu hình, lỗi như `NotNull`, `Min`, `NotBlank` cũng được đưa vào kết quả. `@Valid` không phải điều kiện để Spring **chuyển kiểu**; binding và conversion vẫn xảy ra khi không có `@Valid`.

Phân loại:

| Request | Điểm hỏng | Cách đọc |
| --- | --- | --- |
| `quantity=abc` → `Integer` | Chuyển kiểu | `hasFieldErrors("quantity")`, mã `typeMismatch` |
| `deliveryDate=tomorrow` → `LocalDate` theo ISO | Chuyển kiểu | `FieldError` của `deliveryDate` |
| `quantity=-2` và `@Min(1)` | Validation, **không** phải lỗi chuyển kiểu | `FieldError` từ Bean Validation |
| `admin=true` nhưng chỉ cho bind 4 field khác | Bị chặn bởi allowed fields | `getSuppressedFields()` có `admin` |
| Không gửi `customer` | Có thể còn `null` nếu không đặt required/validation | Cần chính sách `setRequiredFields` hoặc `@NotBlank` tương ứng |

Khi không có `BindingResult` đi kèm để xử lý tại controller, một số lỗi binding/validation của `@ModelAttribute` có thể được Spring MVC đẩy thành exception, thường được ánh xạ sang phản hồi lỗi mặc định. Khi bind trong method đã có `BindingResult`, controller chịu trách nhiệm kiểm tra; **không tiếp tục chạy nghiệp vụ khi `result.hasErrors()`**.

Lưu ý: `@Valid` kiểm tra constraint đã khai báo trên object, không tự động đảm bảo nghiệp vụ hay quyền truy cập. Lỗi parser JSON của `@RequestBody` có thể ném exception như `HttpMessageNotReadableException` trước khi tới controller; cách chẩn đoán khác với `FieldError` của form binding.

## 7. Ví dụ Spring Boot 3 + Java 17 chạy được

Đây là ứng dụng hoàn chỉnh minh họa `WebDataBinder`, `@InitBinder`, `Converter<String, ProductCode>`, `@DateTimeFormat` và `BindingResult`. Chúng ta dùng Spring Boot **3.3.13**, Java **17+**, Maven, không cần cơ sở dữ liệu.

Cấu trúc project:

```text
web-data-binder-demo/
├── pom.xml
└── src/
    ├── main/java/example/BindingDemoApplication.java
    └── test/java/example/OrderControllerTest.java
```

### 7.1. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>web-data-binder-demo</artifactId>
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

### 7.2. BindingDemoApplication.java

Toàn bộ các class sau cùng nằm trong file `src/main/java/example/BindingDemoApplication.java`; chỉ `BindingDemoApplication` là public top-level class, phù hợp quy tắc biên dịch Java.

```java
package example;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class BindingDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(BindingDemoApplication.class, args);
    }
}

// Kiểu giá trị thuộc miền nghiệp vụ.
record ProductCode(String value) {}

// Converter dùng chung trong cấu hình MVC.
final class ProductCodeConverter implements Converter<String, ProductCode> {
    @Override
    public ProductCode convert(String source) {
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("SKU-[0-9]{3}")) {
            throw new IllegalArgumentException(
                    "ProductCode phải có dạng SKU-000");
        }
        return new ProductCode(normalized);
    }
}

@Configuration
class WebBindingConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new ProductCodeConverter());
    }
}

// Input DTO cho form; không dùng một entity persistence trực tiếp.
class OrderForm {
    private String customer;
    private Integer quantity;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deliveryDate;

    private ProductCode code;

    // Thuộc tính mô phỏng field client không được phép chỉnh.
    private boolean admin;

    public OrderForm() {}

    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public ProductCode getCode() { return code; }
    public void setCode(ProductCode code) { this.code = code; }

    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
}

@RestController
@RequestMapping("/orders")
class OrderController {

    // Chỉ áp dụng cho @ModelAttribute("orderForm").
    @InitBinder("orderForm")
    void initOrderBinder(WebDataBinder binder) {
        binder.setAllowedFields(
                "customer", "quantity", "deliveryDate", "code");
    }

    @PostMapping(path = "/form",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> submit(
            @ModelAttribute("orderForm") OrderForm form,
            BindingResult result) {

        // Đầu vào bị chặn bởi allowedFields là sự kiện cần xử lý.
        if (result.getSuppressedFields().length > 0) {
            String names = Arrays.stream(result.getSuppressedFields())
                    .sorted()
                    .collect(Collectors.joining(","));
            return ResponseEntity.badRequest().body("blocked:" + names);
        }

        if (result.hasErrors()) {
            String fields = result.getFieldErrors().stream()
                    .map(FieldError::getField)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(","));
            return ResponseEntity.badRequest().body("invalid:" + fields);
        }

        return ResponseEntity.ok(
                "order:" + form.getCustomer()
                + "|" + form.getQuantity()
                + "|" + form.getDeliveryDate()
                + "|" + form.getCode().value());
    }

    // Chứng minh Converter cũng dùng cho một tham số đơn.
    @GetMapping("/by-code")
    String byCode(@RequestParam ProductCode code) {
        return "found:" + code.value();
    }
}
```

Vì demo chủ động tập trung vào **chuyển kiểu**, nó không khai báo `@NotBlank`/`@NotNull` hay `@Min`. Vì vậy hãy cung cấp đủ bốn giá trị khi gọi endpoint thành công; ứng dụng thực cần validation cho trường bắt buộc trước khi dereference `deliveryDate` hoặc `code`.

### 7.3. Chạy và quan sát kết quả

Tại thư mục `web-data-binder-demo`:

```bash
mvn test
mvn spring-boot:run
```

Ở terminal khác, gọi lần lượt:

```bash
curl -s -X POST "http://localhost:8080/orders/form" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "customer=Ada" \
  --data-urlencode "quantity=3" \
  --data-urlencode "deliveryDate=2026-12-24" \
  --data-urlencode "code=sku-007"

curl -s "http://localhost:8080/orders/by-code?code=sku-007"

curl -s -X POST "http://localhost:8080/orders/form" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data "customer=Ada&quantity=abc&deliveryDate=2026-12-24&code=sku-007"

curl -s -X POST "http://localhost:8080/orders/form" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data "customer=Ada&quantity=3&deliveryDate=2026-12-24&code=sku-007&admin=true"
```

Kết quả **body** kỳ vọng, theo đúng thứ tự bốn lệnh (mỗi lệnh không tự thêm newline):

```text
order:Ada|3|2026-12-24|SKU-007
found:SKU-007
invalid:quantity
blocked:admin
```

Giải thích chính xác theo từng case:

- Form hợp lệ: `quantity` từ `"3"` thành `Integer`; ngày ISO thành `LocalDate`; mã `"sku-007"` thành `ProductCode("SKU-007")`. `OrderForm` chứa object đã được chuyển kiểu.
- Query `code=sku-007` tại `@RequestParam ProductCode` dùng converter đăng ký toàn cục.
- `quantity=abc` không đổi được thành `Integer`: `BindingResult` chứa `FieldError` của `quantity` và controller trả HTTP 400 `invalid:quantity`.
- Client gửi `admin=true` nhưng `@InitBinder` chỉ cho phép bốn property khác; field `admin` bị suppress và controller trả HTTP 400 `blocked:admin`. Đó không phải lỗi type conversion.

Với `curl -i`, HTTP status tương ứng là 200, 200, 400, 400. Với Windows PowerShell, nếu `curl` trỏ tới alias của `Invoke-WebRequest`, hãy dùng `curl.exe` hoặc công cụ HTTP tương đương.

### 7.4. Test MockMvc xác minh từng bước binding

File `src/test/java/example/OrderControllerTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void bindsFormFieldsWithDateAndCustomProductCode() throws Exception {
        mvc.perform(post("/orders/form")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("customer", "Ada")
                    .param("quantity", "3")
                    .param("deliveryDate", "2026-12-24")
                    .param("code", "sku-007"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                    "order:Ada|3|2026-12-24|SKU-007"));
    }

    @Test
    void usesGlobalConverterForRequestParam() throws Exception {
        mvc.perform(get("/orders/by-code")
                    .param("code", "sku-007"))
            .andExpect(status().isOk())
            .andExpect(content().string("found:SKU-007"));
    }

    @Test
    void reportsTypeMismatchOnQuantity() throws Exception {
        mvc.perform(post("/orders/form")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("customer", "Ada")
                    .param("quantity", "abc")
                    .param("deliveryDate", "2026-12-24")
                    .param("code", "sku-007"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("invalid:quantity"));
    }

    @Test
    void blocksUnexpectedAdminProperty() throws Exception {
        mvc.perform(post("/orders/form")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("customer", "Ada")
                    .param("quantity", "3")
                    .param("deliveryDate", "2026-12-24")
                    .param("code", "sku-007")
                    .param("admin", "true"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("blocked:admin"));
    }
}
```

Chạy `mvn test`. Bốn test kiểm tra kết quả end-to-end qua MVC: bind thành công, dùng converter ngoài form, lỗi chuyển kiểu và phát hiện field bị cấm. Demo không cần chạy Servlet server khi test; `MockMvc` xử lý request giả lập trong test context Spring Boot.

## 8. Lỗi phổ biến khi áp dụng vào dự án thật

| Hiện tượng | Nguyên nhân có thể | Cách sửa phù hợp |
| --- | --- | --- |
| `quantity=abc` gây lỗi | String không parse được thành số | Trả lỗi thân thiện từ `BindingResult`; hiển thị field và nguyên nhân cho người dùng |
| Ngày `24/12/2026` không parse theo ISO | Định dạng mong đợi là `yyyy-MM-dd` | Thống nhất định dạng hoặc dùng `@DateTimeFormat`/`Formatter` thích hợp |
| Field object vẫn `null` | Sai tên field, thiếu setter/constructor, conversion lỗi, field bị chặn | Kiểm tra input, tên property, `BindingResult` và `getSuppressedFields` |
| Converter tự viết không được gọi | Chưa đăng ký vào MVC `ConversionService`, sai source/target types | Đăng ký qua `WebMvcConfigurer#addFormatters`; test `@RequestParam` và form |
| `@InitBinder("orderForm")` không áp dụng | `@ModelAttribute` tên khác | Dùng cùng tên attribute chính xác |
| Hai `BindingResult` lẫn nhau | Đặt sai thứ tự method arguments | Đặt ngay sau tham số model attribute nó mô tả |
| Form trả 415 | `Content-Type` không đúng với `consumes` | Gửi form-urlencoded nếu endpoint khai báo form-urlencoded; JSON dùng endpoint phù hợp |
| JSON binding không theo custom formatter | `@RequestBody` đi qua `HttpMessageConverter`/Jackson | Cấu hình Jackson cho JSON hoặc chuyển đổi ở lớp riêng |
| Client có thể gửi `role=ADMIN` | Bind trực tiếp entity nhiều property ghi được | DTO giới hạn field, allowedFields hoặc constructor binding; phân quyền tại service |
| Converter cất request data trong field | Converter thường dùng chung, có thể có concurrency | Viết converter bất biến, không giữ trạng thái request |

Để debug, có thể đặt breakpoint ở `@InitBinder`, converter `convert()`, controller và kiểm tra `BindingResult`. Với dữ liệu lồng nhau, thử input như `address.city`/`items[0].sku` và kiểm tra cấu trúc DTO. Chỉ bật log chi tiết cần thiết trong môi trường phát triển; tránh in thông tin nhạy cảm từ request trong production.

### Quy trình xử lý lỗi thực tế

1. **Định nghĩa input contract:** field nào được phép nhận, kiểu dữ liệu, định dạng, trường bắt buộc, quy tắc nghiệp vụ.
2. **Chuyển kiểu:** dựa trên converter/formatter nhất quán, xử lý giá trị sai bằng lỗi thay vì default âm thầm.
3. **Chặn field không được phép:** DTO + `allowedFields` hoặc constructor binding; đọc suppressed fields khi cần.
4. **Validate:** dùng Jakarta Bean Validation/validator cho quy tắc ngoài type conversion.
5. **Xử lý lỗi:** phản hồi HTTP 400 rõ field nào sai, nhưng tránh tiết lộ thông tin nội bộ hoặc giá trị nhạy cảm.
6. **Gọi service:** chỉ sau khi DTO hợp lệ; service chịu trách nhiệm nghiệp vụ, phân quyền và thay đổi entity.

## 9. Câu hỏi tự kiểm tra

1. Khi gửi `quantity=7`, nhiệm vụ chuyển `"7"` thành `Integer` thuộc binding hay validation? **Đáp án:** type conversion bên trong quá trình binding; validation kiểm tra tính hợp lệ sau đó.
2. Khi gửi `quantity=abc` tới `Integer`, lỗi sẽ ở đâu nếu controller có `BindingResult`? **Đáp án:** lỗi field của `quantity`, thường mã `typeMismatch`.
3. `@RequestBody ProductInput` đọc JSON bằng thành phần nào? **Đáp án:** `HttpMessageConverter` (thường Jackson cho JSON); không nhầm với property binding từ form.
4. `@InitBinder("orderForm")` có tự áp dụng cho mọi DTO không? **Không.** Nó nhắm attribute `orderForm` trong phạm vi controller hoặc advice thích hợp.
5. Bạn nên đặt converter `String → ProductCode` cần dùng ở nhiều controller ở đâu? **Đáp án:** cấu hình MVC chung, ví dụ `WebMvcConfigurer#addFormatters`.
6. `@Valid` có cần để `String` biến thành `LocalDate` không? **Không.** Format/conversion và validation là hai bước khác nhau.
7. Vì sao `setAllowedFields` hữu ích dù một DTO có field `admin`? **Vì nó hạn chế property binding theo whitelist**; client không được tự gán field ngoài danh sách.
8. Khi dùng `@ModelAttribute("orderForm")` và `BindingResult`, phải đặt chúng thế nào? **Liền nhau** trong chữ ký controller method.

## 10. Tài liệu chính thức

- [Spring Framework 6.2 — @InitBinder](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-initbinder.html): tác dụng `WebDataBinder`, phạm vi `@InitBinder`, `allowedFields` và `declarativeBinding`.
- [Spring Framework 6.2 — @ModelAttribute](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html): quá trình tạo/bind model attribute, constructor/property binding, `BindingResult`.
- [Spring Framework 6.2 — MVC Type Conversion](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/conversion.html): đăng ký formatter/converter qua `WebMvcConfigurer`.
- [Spring Framework — Type Conversion](https://docs.spring.io/spring-framework/reference/core/validation/convert.html): `Converter`, `ConverterFactory`, `ConversionService`, `TypeDescriptor`.
- [Spring Framework — Field Formatting](https://docs.spring.io/spring-framework/reference/core/validation/format.html): `Formatter`, parsing/printing theo locale.
- [Spring Framework 6.2 — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/message-converters.html): phân biệt chuyển đổi HTTP body với WebDataBinder.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-validation-002">Constructor binding, property binding và thiết kế DTO đầu vào</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor binding, property binding và thiết kế DTO đầu vào trong Spring MVC

## 1. Bài toán: biến dữ liệu HTTP thành đối tượng Java như thế nào?

Khi client gửi query string, HTML form hoặc một số giá trị từ đường dẫn/header, Spring MVC có thể gắn dữ liệu vào đối tượng Java thông qua **data binding**. Hai con đường chính là:

- **Constructor binding:** dùng các giá trị đầu vào để gọi constructor và tạo đối tượng mới. Tên và kiểu tham số constructor thể hiện rõ dữ liệu được phép cung cấp.
- **Property binding (setter binding):** tạo hoặc lấy một đối tượng có sẵn, sau đó dùng setter/JavaBeans property để gán giá trị. Nếu không kiểm soát trường cho phép, client có thể sửa những property mà lập trình viên không định mở cho request.

Bài này tập trung vào **`@ModelAttribute`, `WebDataBinder` và thiết kế request DTO của Spring Web MVC (Servlet stack)**. JSON `@RequestBody` sẽ được đối chiếu riêng vì cơ chế tạo đối tượng của nó khác. Ví dụ chạy với **Java 17, Spring Boot 3.3.5 (Spring Framework 6.1.x)**; các API `@BindParam` và `setDeclarativeBinding` có từ Spring Framework 6.1.

Một HTTP form request có thể có dạng:

```http
POST /api/registration HTTP/1.1
Content-Type: application/x-www-form-urlencoded

email=lan%40example.com&age=21
```

Spring nhận các giá trị ban đầu dưới dạng chuỗi, chẳng hạn `"21"`, sau đó dùng bộ chuyển đổi kiểu của `WebDataBinder` để tạo thuộc tính `int age = 21`. Nếu gửi `age=abc`, Spring không thể chuyển chuỗi đó thành `int` và ghi lỗi vào `BindingResult`.

> **Phân biệt quan trọng:** `@ModelAttribute` thường bind từ request parameters (query/form), và trong các trường hợp phù hợp còn từ URI variables/request headers; `@RequestBody` đọc body qua `HttpMessageConverter`, thường sử dụng Jackson khi body là JSON. Cả hai có thể nhận DTO, nhưng **không được giả định cơ chế binding và quy tắc whitelist của chúng là giống nhau**.

## 2. Luồng xử lý của @ModelAttribute

Dưới đây là đường đi khái quát trong Spring MVC:

```text
Client gửi form/query/path/header
      |
DispatcherServlet tìm handler method
      |
Argument resolver xử lý @ModelAttribute
      |
Tìm đối tượng trong model hoặc tạo đối tượng mới
      |-- Constructor binding: ánh xạ tham số -> constructor
      |-- Property binding: ánh xạ tên property -> setter
      |
WebDataBinder chuyển String sang kiểu Java
      |
Validation (nếu có @Valid/@Validated và Validator)
      |
BindingResult ghi lỗi binding/validation
      |
Controller nhận DTO đã bind
```

Tùy tình huống, `@ModelAttribute` còn có thể tham chiếu đối tượng đã tồn tại trong model hoặc session thay vì luôn tạo mới. Spring MVC mặc định cho phép **cả constructor binding lẫn property binding** khi phù hợp; việc dùng constructor không tự động vô hiệu hóa mọi setter.

Ví dụ:

```java
@PostMapping("/search")
public String search(@ModelAttribute SearchCriteria criteria) {
    return "search";
}
```

Đây là đoạn minh họa controller, không phải chương trình Java độc lập. Spring cố tạo hoặc lấy `SearchCriteria` rồi bind dữ liệu vào đối tượng. Đối với tham số kiểu phức tạp chưa được resolver khác xử lý, Spring MVC có thể suy luận `@ModelAttribute` ngầm; **nên ghi annotation rõ ràng** để người đọc và công cụ AOT dễ hiểu.

### Binding khác validation

| Bước | Câu hỏi nó giải quyết | Ví dụ |
| --- | --- | --- |
| Constructor/property binding | Giá trị request đi vào field nào, theo cơ chế nào? | `age=21` gắn vào `int age` |
| Type conversion | Chuyển kiểu thành công không? | `"abc"` không chuyển được sang `int` |
| Bean Validation | Giá trị đã chuyển đổi có hợp lệ theo constraint không? | `@Min(18)` từ chối `age=16` |
| Business validation/authorization | Người dùng có quyền và nghiệp vụ có cho phép không? | Người dùng A không được đổi vai trò của B |

`@Valid` không giới hạn field nào được gán; `setAllowedFields` cũng không chứng minh dữ liệu đúng nghiệp vụ. Đây là các trách nhiệm riêng.

## 3. Constructor binding: DTO bất biến

Với Java 17+, `record` là lựa chọn tự nhiên cho DTO chỉ nhận giá trị một lần:

```java
public record RegistrationForm(
        @NotBlank @Email String email,
        @Min(18) int age
) {}
```

Đoạn khai báo này cần các imports `jakarta.validation.constraints.NotBlank`, `Email` và `Min`, cùng starter validation. Khi Spring bind `email=lan%40example.com&age=21`, nó lấy hai tham số, đổi `age` sang `int`, gọi canonical constructor của record và tạo ra đối tượng bất biến về cấu trúc.

Vì record không có setter, request gửi thêm `role=ADMIN` không có chỗ để property binding gán `role` vào DTO này. Constructor chỉ khai báo `email` và `age`. Tuy vậy, điều đó **không có nghĩa** mọi tham số dư mặc nhiên bị coi là lỗi; mặc định các giá trị không được bind có thể được bỏ qua.

Với một lớp thông thường cũng có thể dùng constructor binding:

```java
public final class SearchForm {
    private final String keyword;
    private final int page;

    public SearchForm(String keyword, int page) {
        this.keyword = keyword;
        this.page = page;
    }

    public String getKeyword() { return keyword; }
    public int getPage() { return page; }
}
```

Đây là đoạn mã minh họa. Khi dựa vào **tên tham số constructor** của lớp thường, cần đảm bảo compiler giữ metadata parameter names (ví dụ `javac -parameters` hoặc cấu hình tương đương). Record có tên component rõ ràng. Khi có nhiều constructor, quy tắc chọn constructor có thể thay đổi; nên thiết kế một constructor dữ liệu không gây nhập nhằng.

### Đổi tên tham số đầu vào bằng @BindParam

Tên request không nhất thiết trùng với tên Java. Trong Spring 6.1+, `@BindParam` hỗ trợ gắn tên request cho tham số constructor:

```java
public final class AddressForm {
    private final String postalCode;

    public AddressForm(@BindParam("postal-code") String postalCode) {
        this.postalCode = postalCode;
    }

    public String getPostalCode() { return postalCode; }
}
```

```http
GET /addresses?postal-code=700000
```

Với `@ModelAttribute AddressForm`, Spring lấy tham số `postal-code` để điền `postalCode`. Import annotation là `org.springframework.web.bind.annotation.BindParam`. Đây là minh họa cần đặt trong ứng dụng Spring MVC, không phải lớp độc lập có `main`. Đừng nhầm `@BindParam` dùng cho constructor `@ModelAttribute` với Jackson `@JsonProperty` dùng khi chuyển JSON thành object.

Constructor binding có thể xử lý đối tượng lồng nhau và `List`, `Map`, array bằng tên có cấu trúc như `addresses[0].city`; điều kiện là constructor/kiểu dữ liệu tương ứng có thể bind và các giới hạn tài nguyên thích hợp được áp dụng. Không để client tùy ý tạo đồ thị object quá sâu hoặc collection không giới hạn.

## 4. Property binding: bean mutable và setter

Property binding thường dùng lớp JavaBean với constructor không tham số và getter/setter:

```java
public class ProfileForm {
    private String displayName;
    private String timeZone;

    public ProfileForm() {}

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
}
```

Khi có form `displayName=Lan&timeZone=Asia%2FHo_Chi_Minh`, Spring tạo `ProfileForm` rồi gọi các setter tương ứng. Cách này hữu ích cho form mutable hoặc khi làm việc với thư viện yêu cầu JavaBean. Nó có một rủi ro: nếu sau này lớp xuất hiện setter nhạy cảm như `setRole`, việc bind một cách rộng rãi có thể khiến client sửa thêm trường ngoài ý định.

**Mass assignment / over-posting** xảy ra khi client cố gửi tên property nhạy cảm mà server vô tình cho bind, ví dụ `role=ADMIN`, `approved=true` hoặc `ownerId=...`. Nguyên tắc giảm thiểu:

1. Dùng **DTO đầu vào riêng** theo từng tác vụ, chỉ chứa trường client được phép cung cấp.
2. Ưu tiên constructor binding hoặc DTO bất biến khi phù hợp, tránh setter không cần thiết.
3. Nếu phải dùng property binding, cấu hình `WebDataBinder.setAllowedFields(...)` để whitelist tên property dự kiến.
4. Không bind trực tiếp request vào JPA entity/domain object có setter nghiệp vụ nhạy cảm.
5. Server tự quyết định `id`, `role`, `ownerId`, `createdAt`, `approved` từ quyền hiện tại và quy tắc nghiệp vụ.

**Lưu ý:** whitelist chỉ áp dụng cho **property binding**, không thay đổi các giá trị được chấp nhận bởi constructor đã khai báo. Vì thế không đưa tham số nguy hiểm vào constructor public của DTO chỉ để hy vọng `allowedFields` chặn lại.

## 5. Kiểm soát WebDataBinder bằng @InitBinder

`WebDataBinder` là binder cụ thể trong Spring MVC. Dùng `@InitBinder` để điều chỉnh cách bind cho một model attribute hoặc một controller:

```java
@InitBinder("profileForm")
void configureProfileBinder(WebDataBinder binder) {
    binder.setAllowedFields("displayName", "timeZone");
}
```

- `setAllowedFields` thiết lập **danh sách cho phép** đối với property names; pattern là case-sensitive. Ví dụ mở rộng có thể dùng `address.*` nhưng phải kiểm tra cả những nested property được mở.
- `setDisallowedFields` có tồn tại ở các phiên bản Spring liên quan, song blacklist dễ bỏ sót các property được thêm về sau. Ưu tiên whitelist.
- `setDeclarativeBinding(true)` (Spring 6.1+) buộc property binding chỉ thực hiện khi có `allowedFields`, trong khi constructor binding vẫn được hỗ trợ:

```java
@InitBinder("registrationForm")
void configureRegistrationBinder(WebDataBinder binder) {
    binder.setDeclarativeBinding(true);
}
```

Nếu dùng **cả** `setDeclarativeBinding(true)` và `setAllowedFields("displayName", "timeZone")` thì hai property trong whitelist vẫn được setter binding; các property khác không được gán qua setter. Với model chỉ cần constructor, chỉ cần bật declarative binding mà không khai báo allowed fields.

`@InitBinder("profileForm")` áp dụng cho model attribute có tên `profileForm`; `@InitBinder` không truyền tên có thể tác động các binding trong controller đó. `@ControllerAdvice` có thể dùng để chia sẻ chính sách giữa controller, nhưng hãy kiểm tra phạm vi áp dụng để không vô ý vô hiệu hóa những form khác. Nếu có yêu cầu cụ thể, binder còn có thể đăng ký formatter, converter hoặc validator.

### @ModelAttribute(binding = false) giải quyết việc khác

`@ModelAttribute(binding = false)` dùng khi cần lấy object từ model mà không thực hiện property binding từ request, chẳng hạn object domain đã được server tra cứu. Đó không phải cơ chế thay thế mọi kiểm soát constructor binding. Cách an toàn hơn cho API nhận dữ liệu là dùng request DTO tách biệt rồi gán có kiểm soát vào domain object.

## 6. Chương trình hoàn chỉnh: so sánh hai cơ chế binding

Ví dụ có hai endpoint:
- `POST /api/registration`: record DTO bất biến, constructor binding và validation.
- `POST /api/profile`: JavaBean có setter, property binding qua whitelist. Lớp minh họa cố tình có property `role` để cho thấy whitelist loại bỏ một tham số không được phép. **Trong ứng dụng thật, nên bỏ hẳn property `role` khỏi DTO đầu vào.**

Cấu trúc Maven:

```text
binding-demo/
├── pom.xml
└── src/main/java/example/binding/
    ├── BindingDemoApplication.java
    └── BindingController.java
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
    <groupId>example</groupId>
    <artifactId>binding-demo</artifactId>
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

**`src/main/java/example/binding/BindingDemoApplication.java`**

```java
package example.binding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BindingDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(BindingDemoApplication.class, args);
    }
}
```

**`src/main/java/example/binding/BindingController.java`**

```java
package example.binding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class BindingController {

    public record RegistrationForm(
            @NotBlank @Email String email,
            @Min(18) int age) {}

    public record RegistrationResponse(String email, int age) {}

    @InitBinder("registrationForm")
    void registrationBinder(WebDataBinder binder) {
        // Không bật setter binding cho RegistrationForm.
        binder.setDeclarativeBinding(true);
    }

    @PostMapping(path = "/registration",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(
            @Valid @ModelAttribute("registrationForm") RegistrationForm form,
            BindingResult errors) {
        if (errors.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                "invalidFields",
                errors.getFieldErrors().stream()
                    .map(FieldError::getField)
                    .distinct()
                    .sorted()
                    .toList()
            ));
        }
        return ResponseEntity.ok(
                new RegistrationResponse(form.email(), form.age()));
    }

    public static class ProfileForm {
        private String displayName;
        private String timeZone;
        private String role; // Giữ để minh họa field cần chặn.

        public ProfileForm() {}

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String value) {
            this.displayName = value;
        }

        public String getTimeZone() { return timeZone; }
        public void setTimeZone(String value) {
            this.timeZone = value;
        }

        public String getRole() { return role; }
        public void setRole(String value) {
            this.role = value;
        }
    }

    public record ProfileResponse(
            String displayName, String timeZone, String role) {}

    @InitBinder("profileForm")
    void profileBinder(WebDataBinder binder) {
        binder.setDeclarativeBinding(true);
        binder.setAllowedFields("displayName", "timeZone");
    }

    @PostMapping(path = "/profile",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ProfileResponse update(
            @ModelAttribute("profileForm") ProfileForm form) {
        return new ProfileResponse(
                form.getDisplayName(), form.getTimeZone(), form.getRole());
    }
}
```

**Cơ chế của từng endpoint:**

- `/registration`: record có canonical constructor nhận `email` và `age`; Spring chuyển chuỗi theo kiểu đích, tạo record, chạy Bean Validation vì có `@Valid`. `BindingResult` đứng **ngay sau** tham số được validate để controller đọc lỗi.
- `/profile`: JavaBean được tạo bằng constructor rỗng; binder chỉ cho phép gọi setters ứng với `displayName` và `timeZone`. Tham số `role` bị chặn bởi whitelist dù lớp minh họa có setter.
- Đây là ví dụ phân biệt cơ chế, **không lưu vào database**, không có authentication và không đại diện cho workflow chỉnh sửa role thật.

Chạy từ thư mục `binding-demo`:

```bash
mvn spring-boot:run
```

Gửi form hợp lệ (Bash, macOS/Linux/Git Bash):

```bash
curl -s -X POST 'http://localhost:8080/api/registration' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'email=lan@example.com' \
  --data-urlencode 'age=21'
```

HTTP status `200`; JSON có dữ liệu:

```json
{"email":"lan@example.com","age":21}
```

Thứ tự field JSON không nên được coi là hợp đồng API. Thử dữ liệu sai constraint:

```bash
curl -i -X POST 'http://localhost:8080/api/registration' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'email=invalid' \
  --data-urlencode 'age=16'
```

Kết quả mong đợi là HTTP `400`, body chứa tên field không hợp lệ (thứ tự đã được sort trong controller):

```json
{"invalidFields":["age","email"]}
```

Kiểm tra trường bị chặn:

```bash
curl -s -X POST 'http://localhost:8080/api/profile' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'displayName=Lan' \
  --data-urlencode 'timeZone=Asia/Ho_Chi_Minh' \
  --data-urlencode 'role=ADMIN'
```

Kết quả JSON:

```json
{"displayName":"Lan","timeZone":"Asia/Ho_Chi_Minh","role":null}
```

Mặc dù request có `role=ADMIN` và `ProfileForm` có setter, `setAllowedFields` chỉ chấp nhận hai field còn lại, nên `role` không được gán. Phần response chỉ giúp quan sát cơ chế; trên ứng dụng thật **không nên trả role nội bộ khi nó không cần thiết**. Nếu muốn phát hiện trường bị chặn trong xử lý form, có thể khai báo `BindingResult` ngay sau `@ModelAttribute` và kiểm tra `getSuppressedFields()`; hãy quyết định chính sách trả lỗi hay ghi nhận sự kiện dựa theo bảo mật API, không mặc nhiên coi việc bỏ qua là đủ an toàn.

> **Kiểm chứng runtime:** những body trên là kết quả logic của controller với request đã nêu; HTTP headers phụ thuộc môi trường. Code yêu cầu Maven và JDK 17+, cùng dependencies tải thành công. Nếu cổng `8080` đang được sử dụng, cấu hình `server.port` phù hợp trước khi gửi request.

## 7. Khi đầu vào là JSON @RequestBody

Gửi JSON:

```http
POST /api/registration-json HTTP/1.1
Content-Type: application/json

{"email":"lan@example.com","age":21}
```

Controller thường khai báo:

```java
@PostMapping(path = "/registration-json",
             consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> createJson(
        @Valid @RequestBody RegistrationForm form) {
    // ...
}
```

**Đoạn trên minh họa**, có thể thêm vào `BindingController` cùng imports `@RequestBody`; không nằm trong file chạy đầy đủ ở mục 6. Ở đây Spring MVC chọn `HttpMessageConverter` đọc JSON, và Jackson (nếu được cấu hình) tạo object Java. Validation vẫn có thể xảy ra sau khi đọc body, nhưng đây **không phải** quá trình `@ModelAttribute` gọi `WebDataBinder` để gán request parameters bằng property binding. Do đó `binder.setAllowedFields(...)` trong `@InitBinder` không phải cơ chế bảo vệ JSON khỏi mass assignment.

Với JSON, hãy dùng request DTO với đúng các trường cho phép, ánh xạ tường minh sang domain model và chỉ cho phép các JSON properties cần thiết. Nếu cần quy tắc chặt về trường JSON lạ, phải cấu hình Jackson phù hợp; mặc định chấp nhận hay từ chối field lạ phụ thuộc cấu hình ObjectMapper và môi trường. `@JsonProperty`/`@JsonCreator` phục vụ Jackson; `@BindParam` phục vụ constructor binding của Spring MVC cho `@ModelAttribute`.

| Đặc điểm | `@ModelAttribute` với form/query | `@RequestBody` với JSON |
| --- | --- | --- |
| Nguồn dữ liệu điển hình | Query/form parameters; cũng có thể lấy URI variables/headers theo quy tắc lookup | HTTP body theo `Content-Type` |
| Thành phần chính | `WebDataBinder`, conversion service | `HttpMessageConverter`, Jackson với JSON |
| Tạo object | Constructor binding hoặc bean/property binding | JSON deserialization theo cấu hình Jackson |
| Whitelist property qua `setAllowedFields` | Có tác dụng với **property binding** | Không phải biện pháp whitelist JSON |
| Bean Validation `@Valid` | Có, thường đi với `BindingResult` | Có, xử lý lỗi validation theo cơ chế request body |

## 8. Thiết kế DTO đầu vào an toàn, dễ bảo trì

Một endpoint cập nhật profile nên phân định rõ **client input**, **domain object** và **response DTO**:

```text
HTTP request
     |
CreateProfileRequest / UpdateProfileRequest
     |  validate + authorize + check business rules
     v
Application service
     |  chỉ ánh xạ các field được phép
     v
Domain entity (id, role, ownerId do server quản lý)
     |
     v
ProfileResponse (chỉ dữ liệu được phép hiển thị)
```

Một DTO input tốt thường có các đặc điểm:

1. **Theo hành động, không theo bảng database.** Ví dụ `CreateOrderRequest`, `UpdateProfileRequest`; hai thao tác có tập trường được phép khác nhau.
2. **Giới hạn dữ liệu ngay trong kiểu.** Dùng record/final fields cho DTO bất biến khi phù hợp, constraints như `@NotBlank`, `@Email`, `@Size`, `@Min`; không coi annotation là toàn bộ logic nghiệp vụ.
3. **Không nhận field do server sở hữu.** `id`, `createdAt`, `approved`, `role`, `tenantId`, `ownerId` thường phải lấy từ cơ sở dữ liệu hoặc security context sau khi xác thực/phân quyền.
4. **Chỉ map field hợp lệ.** Không sao chép tất cả thuộc tính request vào entity bằng reflection hoặc BeanUtils không kiểm soát. Có thể tự gán hoặc dùng mapper khai báo explicit mapping.
5. **Tách kiểu tạo và kiểu cập nhật.** Trong cập nhật từng phần, cần phân biệt **không gửi field**, **gửi null** và **gửi giá trị mới**; record có primitive `int` hay một nullable reference đơn giản có thể làm mất khác biệt này. Nếu semantics patch quan trọng, thiết kế command/patch representation phù hợp.
6. **Coi nested object/list là dữ liệu từ bên ngoài.** Giới hạn số phần tử, chiều sâu, và kiểm tra mọi ID tham chiếu thuộc phạm vi được phép.
7. **Chuẩn hóa lỗi.** Đặt `BindingResult` ngay sau tham số `@ModelAttribute` khi muốn phản hồi field errors ngay tại controller; nếu không, lỗi có thể được Spring xử lý qua exception handler. Đừng đưa giá trị nhạy cảm vào thông báo lỗi/log.

Ví dụ ánh xạ tường minh (**minh họa logic dịch vụ**, không phải lớp chạy độc lập):

```java
public void changeProfile(UpdateProfileRequest input,
                          Account account) {
    account.changeDisplayName(input.displayName());
    account.changeTimeZone(input.timeZone());
    // account.role, account.ownerId không đến từ input.
}
```

Đối tượng `account` phải được tải và kiểm tra quyền ở tầng service/security. Whitelist binder và DTO hẹp có thể giảm bề mặt tấn công, nhưng không thay được kiểm soát quyền sở hữu bản ghi.

## 9. Các lỗi và nhầm lẫn thường gặp

| Hiện tượng | Nguyên nhân | Hướng giải quyết |
| --- | --- | --- |
| Form gửi đúng tên nhưng field vẫn `null` | Getter/setter thiếu hoặc sai, tên request không khớp, `allowedFields` chặn | Kiểm tra JavaBean convention, tên param và binder |
| `age=abc` gây binding error | Không chuyển `String` thành `int` | Kiểm tra `BindingResult` và thông báo có cấu trúc |
| `age=16` bind được nhưng validation lỗi | Type conversion thành công, `@Min(18)` thất bại | Phân biệt validation với conversion |
| DTO có setter nhạy cảm được client sửa | Property binding mở quá rộng | Dùng DTO riêng, `setAllowedFields`/`setDeclarativeBinding` |
| Bật `setDeclarativeBinding(true)` khiến mutable form không nhận giá trị | Property binding cần `allowedFields` tường minh | Bổ sung chính xác whitelist hoặc chuyển sang DTO constructor |
| Đặt `BindingResult` sai vị trí | Spring yêu cầu nó ngay sau argument tương ứng | Chuyển `BindingResult` liền sau `@Valid @ModelAttribute` |
| Nhầm `@BindParam` với `@JsonProperty` | Hai pipeline khác nhau | Phân biệt binder của model attribute và Jackson |
| Nghĩ rằng bỏ `role` khỏi JSON là đã phân quyền | Authorization là kiểm tra riêng | Kiểm tra principal, resource ownership và policy ở service |
| Dùng cùng một request DTO cho create/update/patch | Quy tắc field và null khác nhau | Tách contract hoặc xây representation thể hiện rõ ý định |

Một số điểm tinh tế cần nhớ:

- `@ModelAttribute` có thể sử dụng giá trị từ request parameters, URI variables và headers; Spring ưu tiên request parameters khi tên trùng. Không nên dựa vào headers để cấp trường nhạy cảm chỉ vì binder có thể đọc được.
- `setAllowedFields` giới hạn setter/property binding, **không loại bỏ quyền bind tham số đã khai báo ở constructor**.
- `BindingResult` giữ cả lỗi binding/conversion và validation; nếu controller nhận nó, phải kiểm tra và xử lý, không tiếp tục gọi service khi có lỗi.
- Spring MVC thường trả `400 Bad Request` cho binding/validation không được xử lý riêng, nhưng cấu trúc JSON của lỗi do ứng dụng, phiên bản và exception handler quyết định.
- Các mối nguy về request size, danh sách lớn, nested binding và dữ liệu nhạy cảm cần giải quyết bằng giới hạn phù hợp ở cả HTTP và tầng nghiệp vụ.

## 10. Bài tập tự kiểm tra

1. Vì sao constructor của request DTO chỉ nên nhận những field client thực sự được phép kiểm soát?
2. `setDeclarativeBinding(true)` tác động thế nào đến constructor binding và property binding?
3. Nếu lớp JavaBean có setters `setName` và `setRole`, còn allowed fields chỉ có `name`, điều gì xảy ra với request `role=ADMIN`?
4. Vì sao `@Valid` không thay thế `setAllowedFields`? Và vì sao `setAllowedFields` không thay thế authorization?
5. Chọn cơ chế nào cho form HTML gồm `email` và `age`? Chọn cơ chế nào cho request JSON cùng hai field?
6. Khi thiết kế PATCH, vì sao `int age` trong record không đủ để phân biệt field không gửi và gửi `0`?

**Định hướng trả lời:** (1) Tham số constructor là tập đầu vào khai báo rõ; không để server-owned fields lọt vào. (2) Constructor binding vẫn hoạt động; setter binding chỉ khi có whitelist. (3) `role` bị loại khỏi property binding. (4) Validation kiểm tra giá trị, whitelist kiểm soát field được gán, authorization kiểm tra quyền. (5) Form/query: `@ModelAttribute`; JSON: `@RequestBody` qua converter. (6) Primitive có mặc định, nên cần mô hình biểu diễn trạng thái hiện diện nếu hỗ trợ cập nhật từng phần.

## 11. Nguồn tham khảo

- [Spring Framework — @ModelAttribute](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html)
- [Spring Framework — Data Binding (Web MVC)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-data-binding.html)
- [Spring Framework — @InitBinder](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-initbinder.html)
- [Spring Framework — Data Binding (Core)](https://docs.spring.io/spring-framework/reference/core/validation/beans-beans.html)
- [Spring Framework 6.1 — DataBinder API](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/validation/DataBinder.html)
- [Spring Framework 6.1 — @BindParam API](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/bind/annotation/BindParam.html)

Các ví dụ được giới hạn vào Spring MVC, binding đầu vào và thiết kế DTO; chúng minh họa xử lý request an toàn trong phạm vi bài học, không bao gồm persistence hoặc cơ chế đăng nhập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-validation-003">Converter, Formatter, ConversionService và định dạng ngày giờ</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — Converter, Formatter, ConversionService và định dạng ngày giờ

## 1. Mục tiêu và bối cảnh

Khi HTTP client gửi `?quantity=3&deliveryDate=2026-12-31`, dữ liệu ban đầu là **chuỗi**. Controller có thể muốn nhận `int`, `LocalDate` hoặc một kiểu nghiệp vụ như `ProductCode`. Spring thực hiện chuyển đổi dữ liệu trong quá trình binding trước khi gọi handler.

Sau bài này, bạn có thể:

- Phân biệt `Converter<S,T>` (chuyển kiểu), `Formatter<T>` (parse/print theo `Locale`), `ConversionService` (dịch vụ điều phối) và `FormatterRegistry` (đăng ký các thành phần chuyển đổi).
- Viết, đăng ký và sử dụng converter/formatter riêng cho tham số controller và trường của `@ModelAttribute`.
- Dùng `@DateTimeFormat`, `@NumberFormat` với Java Time và số; nhận biết vấn đề định dạng theo locale, múi giờ, offset và HTML form.
- Hiểu lỗi chuyển đổi dữ liệu, điểm khác với Bean Validation, cùng cách kiểm thử lỗi và kết quả thật.
- Cấu hình Spring MVC bằng `WebMvcConfigurer#addFormatters` trong ứng dụng Spring Boot mà không thay toàn bộ cấu hình MVC.

Phạm vi là **Spring MVC Servlet stack** với Java 17+; ví dụ đầy đủ dùng Spring Boot 3.5.0 (Spring Framework 6.2). Một số cơ chế chung của Spring Core cũng được áp dụng trong các tầng khác.

## 2. Data binding cần conversion như thế nào?

Xem handler:

~~~java
@GetMapping("/search")
public String search(
        @RequestParam int page,
        @RequestParam LocalDate date) {
    return page + " / " + date;
}
~~~

Đây là đoạn minh họa chữ ký, chưa có class/config và annotation định dạng ngày. Request:

~~~http
GET /search?page=2&date=2026-12-31
~~~

Chuỗi `"2"` phải trở thành `int 2`; chuỗi ngày phải thành `LocalDate`. Khi binding `@ModelAttribute`, việc chuyển đổi tương tự diễn ra trên các property:

~~~java
public class SearchForm {
    private Integer page;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
}
~~~

Đoạn form class cần các import `java.time.LocalDate` và `org.springframework.format.annotation.DateTimeFormat`. Có thể bind bằng `@ModelAttribute SearchForm form` ở controller.

Luồng chính:

1. Spring MVC xác định controller method và tên trường cần lấy từ request.
2. `WebDataBinder` xác định kiểu đích (`int`, `LocalDate`, `ProductCode`, v.v.).
3. Hệ thống `ConversionService`/formatting tra converter hoặc parser phù hợp với kiểu và annotation.
4. Nếu chuyển đổi thành công, Spring gán giá trị vào method argument/JavaBean property hoặc constructor argument.
5. Nếu thất bại, Spring ghi binding error hoặc xử lý exception tùy handler signature; Bean Validation nếu có sẽ xử lý các constraint sau đó.

**Chuyển đổi không đồng nghĩa xác thực nghiệp vụ:** `age=17` có thể chuyển sang `int 17` thành công nhưng vi phạm `@Min(18)`; `age=abc` sai ngay ở bước chuyển kiểu.

## 3. Bản đồ các API

| API | Tác dụng | Có Locale? | Ví dụ sử dụng |
| --- | --- | --- | --- |
| `Converter<S,T>` | Chuyển từ một kiểu nguồn S sang kiểu đích T | Không có tham số `Locale` | `String → ProductCode`, `String → Enum` |
| `ConverterFactory<S,R>` | Cấp converter cho nhiều kiểu đích thuộc một họ R | Không trực tiếp | Một factory cho các kiểu enum |
| `GenericConverter` | Chuyển kiểu phức tạp có `TypeDescriptor` | Không trực tiếp; có metadata kiểu/annotation | Collection, trường generic hoặc theo annotation |
| `Formatter<T>` | `parse(String, Locale) → T` và `print(T, Locale) → String` | Có | Số, tiền, ngày/giờ theo locale |
| `ConversionService` | API thống nhất: `canConvert`, `convert` | Không truyền Locale trực tiếp trong chữ ký thông dụng | Cung cấp chuyển kiểu cho binder/service |
| `FormatterRegistry` | Đăng ký converter, formatter, annotation formatter | Không phải công cụ convert trực tiếp | `addConverter`, `addFormatter` |
| `FormattingConversionService` | `ConversionService` có khả năng đăng ký và sử dụng formatter | Formatter tra locale qua ngữ cảnh định dạng | Spring MVC form binding |

`ConversionService` là **facade**; `Converter`/`Formatter` cung cấp phần thực thi. Spring đã có sẵn nhiều quy tắc chuyển đổi thường gặp (chuỗi, số, boolean, enum, collection, ngày giờ). Chỉ viết converter mới khi kiểu hoặc quy tắc nghiệp vụ chưa được hỗ trợ đúng ý.

### 3.1. Chọn Converter hay Formatter?

- Dùng `Converter` khi muốn quy tắc **theo kiểu**, không quan tâm ngôn ngữ/locale, ví dụ mã sản phẩm `ab-123 → ProductCode("AB-123")`.
- Dùng `Formatter` khi chuỗi hiển thị và chuỗi người dùng nhập phụ thuộc locale, ví dụ `1,234.50` (en-US) so với `1.234,50` (vi-VN).
- Với date/time, ưu tiên các formatter có sẵn và `@DateTimeFormat` thay vì tự viết parser nếu định dạng đủ chuẩn.
- Không viết lại `String → Integer` hay `String → LocalDate` khi cấu hình mặc định hoặc annotation đã đáp ứng.

## 4. Converter<S,T> — chuyển kiểu đơn giản

Khai báo giao diện (tóm lược):

~~~java
public interface Converter<S, T> {
    T convert(S source);
}
~~~

Ví dụ mã sản phẩm phải có ba chữ cái và ba chữ số, không phụ thuộc locale:

~~~java
package demo;

import java.util.Locale;
import org.springframework.core.convert.converter.Converter;

public final class ProductCodeConverter implements Converter<String, ProductCode> {
    @Override
    public ProductCode convert(String source) {
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}-[0-9]{3}")) {
            throw new IllegalArgumentException(
                    "Product code must match AAA-123");
        }
        return new ProductCode(normalized);
    }
}
~~~

Kiểu đích (Java 17 record):

~~~java
package demo;

public record ProductCode(String value) {
}
~~~

- `source="ab-123"` → `ProductCode("AB-123")`.
- `source="a-123"` → converter ném `IllegalArgumentException`.
- Spring thường gọi converter sau khi xác định giá trị nguồn; quy tắc API `Converter` bảo đảm tham số `source` trong mỗi lời gọi không null.
- Converter được dùng lại giữa nhiều request, vì vậy nên viết **stateless, thread-safe**; `Locale.ROOT` tránh xử lý uppercase phụ thuộc locale của máy chủ.

Một converter hỗ trợ `String → ProductCode`; nó **không** tự đổi kiểu ngược `ProductCode → String` theo cùng quy tắc. Nếu cần hai hướng trong ngữ cảnh thuần kiểu, đăng ký thêm converter ngược; nếu cần parse/print theo locale, xem `Formatter`.

### 4.1. ConverterFactory và GenericConverter dùng khi nào?

`ConverterFactory<S,R>` tạo converter cho các subtype trong cùng họ đích, ví dụ một factory xử lý các enum thay vì viết converter cho từng enum. Spring đã có cơ chế enum phổ biến, nên thường không phải tự làm.

`GenericConverter` có chữ ký khái quát:

~~~java
Object convert(
        Object source,
        TypeDescriptor sourceType,
        TypeDescriptor targetType);
~~~

Các `TypeDescriptor` nêu kiểu thật, thông tin generic, metadata của field hoặc annotation; hữu ích khi cách chuyển đổi phụ thuộc cấu trúc như `List<String> → List<Integer>`. Phức tạp hơn `Converter<S,T>`; chỉ dùng khi cần metadata đó. `ConditionalGenericConverter` còn có thể giới hạn việc áp dụng bằng điều kiện `matches(sourceType, targetType)`.

## 5. ConversionService — sử dụng và đăng ký

Giao diện chính:

~~~java
boolean canConvert(Class<?> sourceType, Class<?> targetType);
<T> T convert(Object source, Class<T> targetType);
boolean canConvert(TypeDescriptor sourceType, TypeDescriptor targetType);
Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType);
~~~

Ví dụ độc lập ở Spring Core (cần các thư viện Spring trong classpath):

~~~java
import org.springframework.core.convert.support.DefaultConversionService;

DefaultConversionService conversion = new DefaultConversionService();
conversion.addConverter(new ProductCodeConverter());

System.out.println(conversion.canConvert(String.class, ProductCode.class));
ProductCode code = conversion.convert("ab-123", ProductCode.class);
System.out.println(code.value());
~~~

Kết quả in ra:

~~~text
true
AB-123
~~~

Đây là đoạn chạy trong ngữ cảnh đã khai báo `ProductCodeConverter` và `ProductCode`, không phải một file Java độc lập chỉ chứa các dòng trên.

`canConvert` chỉ xác nhận có đường chuyển đổi phù hợp theo kiểu; **không bảo đảm** giá trị cụ thể hợp lệ. `canConvert(String.class, ProductCode.class) == true` nhưng chuỗi `"123"` vẫn làm converter báo lỗi.

Nếu làm việc với `List<String> → List<Integer>` trong code Java, chỉ chỉ định `List.class` sẽ thiếu generic element type. Hãy dùng `TypeDescriptor`:

~~~java
import java.util.List;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;

DefaultConversionService service = new DefaultConversionService();
Object converted = service.convert(
    List.of("10", "20"),
    TypeDescriptor.collection(
        List.class, TypeDescriptor.valueOf(String.class)),
    TypeDescriptor.collection(
        List.class, TypeDescriptor.valueOf(Integer.class)));
System.out.println(converted);
~~~

Output:

~~~text
[10, 20]
~~~

Trong Spring MVC, conversion được thực hiện tự động khi bind HTTP request. **Không mặc nhiên cho rằng một `ConversionService` bean do bạn tự tạo luôn là chính instance mà MVC dùng**; hãy đăng ký converter/formatter trong cấu hình MVC được hỗ trợ (mục 7) khi mục đích là chuyển đối số controller.

## 6. Formatter<T> — parse và print theo Locale

Giao diện:

~~~java
public interface Formatter<T> extends Printer<T>, Parser<T> {
}
~~~

Hai thao tác:

~~~java
String print(T value, Locale locale);
T parse(String text, Locale locale) throws ParseException;
~~~

`parse` đọc giá trị từ client vào đối tượng Java, còn `print` chuyển đối tượng thành chuỗi hiển thị cho client. Hai thao tác nên thống nhất nhau với cùng locale để người dùng có thể nhập lại giá trị vừa được hiển thị. `Formatter` có thể dùng trong MVC form binding, view rendering, hoặc khi gọi từ code sau khi được đăng ký.

Ví dụ cùng giá trị số `1234.50`:

| Locale | Chuỗi thường dùng | Ý nghĩa |
| --- | --- | --- |
| `en-US` | `1,234.50` | Dấu phẩy phân nhóm; dấu chấm thập phân |
| `vi-VN` | `1.234,50` | Dấu chấm phân nhóm; dấu phẩy thập phân |

Đừng tự viết `replace(",", ".")` để xử lý mọi locale: cách này dễ biến `1,234.50` thành dữ liệu không hợp lệ hoặc diễn giải nhầm nghìn/thập phân.

`java.text.NumberFormat` và `DecimalFormat` có thể biểu diễn quy tắc theo locale, nhưng **các formatter kiểu này không an toàn khi chia sẻ instance mutable giữa thread**. Tạo `DecimalFormat` mới trong từng lời gọi `parse`/`print` hoặc thiết kế cơ chế an toàn khác.

## 7. Đăng ký Converter và Formatter trong Spring MVC

Với Spring Boot và Java config:

~~~java
@Configuration
public class WebConversionConfig implements WebMvcConfigurer {
    private final AmountFormatter amountFormatter;

    public WebConversionConfig(AmountFormatter amountFormatter) {
        this.amountFormatter = amountFormatter;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new ProductCodeConverter());
        registry.addFormatter(amountFormatter);
    }
}
~~~

Các import tương ứng:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
~~~

Lớp ví dụ giả định `AmountFormatter` được đăng ký làm Spring bean (xem dự án đầy đủ ở mục 10). `addFormatters` là cách Spring MVC đề xuất để bổ sung converter/formatter mà vẫn dùng các mặc định cần thiết. Với Spring Boot MVC, thông thường **không cần `@EnableWebMvc`**; thêm nó có thể làm mất những cấu hình MVC tự động hữu ích do Boot cung cấp.

`FormatterRegistry` kế thừa khả năng đăng ký converter; các lời gọi `registry.addConverter(...)` và `registry.addFormatter(...)` góp các quy tắc vào conversion system dành cho MVC. Không nên vô tình đăng ký hai chiến lược cạnh tranh cho cùng kiểu nguồn/đích nếu không kiểm soát precedence.

### 7.1. Cấu hình tại controller bằng @InitBinder

Khi quy tắc đặc thù chỉ áp dụng một model/controller, có thể cấu hình binder:

~~~java
@InitBinder("filter")
public void bindFilter(WebDataBinder binder) {
    binder.addCustomFormatter(new CustomFilterFormatter());
}
~~~

Đây là đoạn minh họa (cần class `CustomFilterFormatter` và đầy đủ import). `@InitBinder` có thể đăng ký formatter/converter hay editor tùy API và phạm vi. Nếu kiểu dùng phổ biến khắp MVC, `WebMvcConfigurer#addFormatters` thường rõ và dễ tái sử dụng hơn.

Không cần dùng `PropertyEditor` cho code mới chỉ để chuyển đổi kiểu thông thường: hệ `ConversionService` và `Formatter` có API mạnh kiểu, dễ cấu hình. Nếu dùng `PropertyEditor` cũ, chú ý trạng thái và phạm vi tạo instance.

## 8. @DateTimeFormat với Java Time

Các kiểu java.time phổ biến:

| Kiểu | Có gì? | Dùng khi |
| --- | --- | --- |
| `LocalDate` | Ngày; không có giờ/múi giờ | Ngày sinh, ngày giao hàng |
| `LocalTime` | Giờ trong ngày; không có ngày/múi giờ | Giờ mở cửa |
| `LocalDateTime` | Ngày + giờ nhưng **không** có offset/zone | Giờ hẹn tại một địa điểm với timezone đã quy ước riêng |
| `OffsetDateTime` | Ngày + giờ + offset UTC (ví dụ +07:00) | API cần giữ offset của client |
| `ZonedDateTime` | Ngày + giờ + vùng thời gian có rule (ví dụ Asia/Ho_Chi_Minh) | Lịch hẹn gắn với timezone địa phương |
| `Instant` | Một thời điểm UTC trên timeline | Log, thời điểm tạo/cập nhật, giao tiếp giữa hệ thống |
| `Duration` / `Period` | Khoảng thời gian / khoảng ngày tháng | Thời lượng và phép tính thời gian, không phải ngày hẹn |

`LocalDateTime` không tự mang offset; không được suy đoán `2026-12-31T09:30` là UTC hay Việt Nam khi chưa có quy ước. Offset `+07:00` không phải một timezone có quy tắc DST lịch sử/tương lai.

### 8.1. Định dạng ISO rõ ràng

~~~java
@GetMapping("/events")
public String events(
        @RequestParam("day")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate day,
        @RequestParam("at")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime at) {
    return day + " | " + at;
}
~~~

Các import cần thiết:

~~~java
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
~~~

Dữ liệu tương ứng:

~~~text
/events?day=2026-12-31&at=2026-12-31T09:30:00.000%2B07:00
~~~

Lưu ý ký tự `+` phải được URL-encode thành `%2B` trong query. Nếu viết trực tiếp `+07:00` trong URL form-encoding, dấu `+` có thể được giải mã thành dấu cách và gây lỗi parse.

`@DateTimeFormat` dùng trên **field hoặc method parameter**, ví dụ:

~~~java
public class BookingForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    // Getter và setter...
}
~~~

Nếu client gửi JSON body và controller dùng `@RequestBody`, JSON được xử lý bởi HTTP message converter (thường Jackson). **`@DateTimeFormat` dành cho cơ chế formatting/binding của Spring**, không phải cách duy nhất hay mặc nhiên có hiệu lực để tùy biến định dạng JSON. Với JSON, xem cấu hình Jackson và `@JsonFormat` khi cần.

### 8.2. Pattern, ISO, style và fallbackPatterns

~~~java
@DateTimeFormat(pattern = "dd/MM/uuuu")
private LocalDate visitDate;
~~~

Với Java Time, chuỗi dự kiến là `31/12/2026`. Chữ `M` biểu thị **tháng**, còn `m` biểu thị **phút**. `HH` là giờ 0–23; `hh` là giờ 1–12, thường cần a.m./p.m. Ký hiệu pattern có chi tiết khác nhau giữa `java.time.format.DateTimeFormatter` và legacy `java.text.SimpleDateFormat`; đừng sao chép pattern tùy tiện giữa hai hệ.

Ba thuộc tính chính của `@DateTimeFormat`:

- `iso = DateTimeFormat.ISO.DATE`, `ISO.TIME` hoặc `ISO.DATE_TIME`: ưu tiên cho API cần định dạng tiêu chuẩn rõ ràng.
- `pattern = "..."`: dùng một cấu trúc do ứng dụng kiểm soát, ví dụ `dd/MM/uuuu` cho form ngày sử dụng `LocalDate`.
- `style = "..."`: dựa vào phong cách ngày/giờ theo locale; phù hợp UI nhưng biểu diễn có thể khác giữa môi trường/JDK.

Dùng **một quy tắc chính rõ ràng**; tránh khai báo `pattern`, `iso` và `style` trái nhau. Từ Spring 5.3.5, `fallbackPatterns` cho phép chỉ định thêm những pattern chấp nhận khi phân tích dữ liệu nếu pattern chính không khớp, ví dụ (minh họa, cân nhắc trước khi nới input contract):

~~~java
@DateTimeFormat(
        iso = DateTimeFormat.ISO.DATE,
        fallbackPatterns = {"dd/MM/uuuu"})
private LocalDate date;
~~~

Fallback cho phép parse thêm định dạng cũ, nhưng làm API chấp nhận nhiều chuỗi. Đối với đầu vào máy với máy, một chuẩn ISO nhất quán thường tốt hơn. Formatting dựa trên style chịu ảnh hưởng locale và JDK/CLDR; không dùng làm định dạng lưu trữ hoặc hợp đồng API ổn định.

### 8.3. HTML input date/datetime-local

~~~html
<input type="date" name="startDate">
<input type="datetime-local" name="appointment">
~~~

- Input `type="date"` thường gửi `yyyy-MM-dd` dù browser có thể hiển thị ngày theo cách địa phương.
- Input `type="datetime-local"` gửi ngày + giờ địa phương, **không kèm timezone**. Phù hợp `LocalDateTime` nhưng server phải có quy tắc riêng để xác định timezone nếu cần tạo `Instant`.
- Chọn `@DateTimeFormat(iso = DATE)` cho `LocalDate` nhận từ input date; với giờ địa phương, chọn `LocalDateTime` và ISO phù hợp; nếu cần thời điểm tuyệt đối, nhận offset/zone hoặc thông tin timezone bổ sung.

Nếu muốn cấu hình ISO mặc định cho Java Time trong MVC (không phải mọi endpoint phải dùng annotation):

~~~java
@Configuration
public class IsoDateMvcConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setUseIsoFormat(true);
        registrar.registerFormatters(registry);
    }
}
~~~

Imports thêm: `org.springframework.format.datetime.standard.DateTimeFormatterRegistrar`. Trong ví dụ cấu hình này, đăng ký ISO ở mức MVC; không cần ghép trực tiếp class vào project mục 10 vì các trường ở đó đã được chú thích format cụ thể.

### 8.4. Múi giờ và xử lý sai lệch

Ví dụ `2026-12-31T09:30:00+07:00` là cùng thời điểm với `2026-12-31T02:30:00Z`. Tuy vậy, `2026-12-31T09:30` (`LocalDateTime`) **chưa xác định một Instant duy nhất** khi thiếu offset/zone.

Với ngày thuần `LocalDate` (ngày sinh/ngày giao hàng), không tự ép thành `Instant` nếu nghiệp vụ không cần giờ; timezone conversion có thể vô tình đổi ngày. Với lịch hẹn, lưu quy tắc timezone và quyết định rõ cách xử lý giờ trùng/không tồn tại do DST ở vùng khác.

## 9. @NumberFormat và Locale

`@NumberFormat` hỗ trợ format/parse giá trị số theo kiểu và locale:

~~~java
public class PricingForm {
    @NumberFormat(pattern = "#,##0.00")
    private BigDecimal price;

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
~~~

Cần import:

~~~java
import java.math.BigDecimal;
import org.springframework.format.annotation.NumberFormat;
~~~

`pattern` của định dạng số được diễn giải trong bối cảnh locale. Tên pattern `#,##0.00` mô tả vị trí nhóm, số chữ số thập phân; dấu nhóm và dấu thập phân **được biểu diễn khác nhau** theo locale. Có thể dùng `@NumberFormat(style = NumberFormat.Style.NUMBER)` hoặc `Style.CURRENCY` khi phù hợp với kiểu dữ liệu và UX.

Cẩn thận: số tiền không nên lưu trữ/tính toán bằng `double` khi độ chính xác thập phân là yêu cầu nghiệp vụ; `BigDecimal` thường phù hợp hơn. Formatter phục vụ giao diện không tự định nghĩa quy tắc làm tròn, loại tiền, hay validation của nghiệp vụ. Không dùng dấu nhóm từ locale UI như giao thức API nội bộ nếu có thể truyền số thuần chuẩn (ví dụ JSON numeric hoặc decimal string có hợp đồng cố định).

## 10. Dự án chạy được: custom Converter + Formatter + ngày giờ

Ví dụ kết hợp cả bốn khái niệm với Spring MVC. Một endpoint nhận query parameters:
`code=ab-123` (custom `Converter`), `amount=1,234.50` (custom `Formatter` theo `Locale`), `deliveryDate=2026-12-31` và `meetingAt=2026-12-31T09:30:00.000+07:00` (`@DateTimeFormat`).

Sơ đồ:

~~~text
conversion-demo/
├── pom.xml
├── src/main/java/demo/
│   ├── ConversionDemoApplication.java
│   ├── ProductCode.java
│   ├── ProductCodeConverter.java
│   ├── Amount.java
│   ├── AmountFormatter.java
│   ├── WebConversionConfig.java
│   └── QuoteController.java
└── src/test/java/demo/
    └── QuoteControllerTest.java
~~~

### 10.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>conversion-demo</artifactId>
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

### 10.2. ConversionDemoApplication.java

~~~java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConversionDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConversionDemoApplication.class, args);
    }
}
~~~

### 10.3. ProductCode.java

~~~java
package demo;

public record ProductCode(String value) {
}
~~~

### 10.4. ProductCodeConverter.java

~~~java
package demo;

import java.util.Locale;
import org.springframework.core.convert.converter.Converter;

public final class ProductCodeConverter implements Converter<String, ProductCode> {
    @Override
    public ProductCode convert(String source) {
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}-[0-9]{3}")) {
            throw new IllegalArgumentException(
                    "Product code must match AAA-123");
        }
        return new ProductCode(normalized);
    }
}
~~~

### 10.5. Amount.java

~~~java
package demo;

import java.math.BigDecimal;

public record Amount(BigDecimal value) {
}
~~~

### 10.6. AmountFormatter.java

~~~java
package demo;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;

import org.springframework.format.Formatter;
import org.springframework.stereotype.Component;

@Component
public final class AmountFormatter implements Formatter<Amount> {

    @Override
    public Amount parse(String text, Locale locale) throws ParseException {
        DecimalFormat format = decimalFormat(locale);
        ParsePosition position = new ParsePosition(0);
        Number parsed = format.parse(text, position);

        if (parsed == null || position.getIndex() != text.length()) {
            int at = position.getErrorIndex() >= 0
                    ? position.getErrorIndex()
                    : position.getIndex();
            throw new ParseException("Invalid localized amount", at);
        }

        return new Amount((BigDecimal) parsed);
    }

    @Override
    public String print(Amount amount, Locale locale) {
        return decimalFormat(locale).format(amount.value());
    }

    private DecimalFormat decimalFormat(Locale locale) {
        DecimalFormat format =
                (DecimalFormat) NumberFormat.getNumberInstance(locale);
        format.setParseBigDecimal(true);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format;
    }
}
~~~

Giải thích:

- `parse` nhận đúng locale của request trong cơ chế MVC; nó tạo `DecimalFormat` riêng cho từng lời gọi nên không chia sẻ formatter mutable giữa thread.
- `setParseBigDecimal(true)` giúp nhận số dưới dạng `BigDecimal`, tránh chuyển qua `double`.
- `ParsePosition` cho phép từ chối chuỗi không parse hết; với dữ liệu tiền thật, còn cần kiểm tra tiền tệ, scale, khoảng giá trị và ngữ pháp chặt hơn.
- `print` dùng cùng quy tắc locale và cố định **hai chữ số thập phân** khi hiển thị.
- `@Component` giúp MVC configuration và controller dùng chung bean không trạng thái. Bean chỉ giữ code và không giữ instance `DecimalFormat` chung.

### 10.7. WebConversionConfig.java

~~~java
package demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConversionConfig implements WebMvcConfigurer {
    private final AmountFormatter amountFormatter;

    public WebConversionConfig(AmountFormatter amountFormatter) {
        this.amountFormatter = amountFormatter;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new ProductCodeConverter());
        registry.addFormatter(amountFormatter);
    }
}
~~~

Nhờ đăng ký này, Spring MVC biết cách nhận `ProductCode` và `Amount`. Các kiểu ngày ở ví dụ sử dụng formatter mặc định với `@DateTimeFormat`.

### 10.8. QuoteController.java

~~~java
package demo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QuoteController {
    private final AmountFormatter amountFormatter;

    public QuoteController(AmountFormatter amountFormatter) {
        this.amountFormatter = amountFormatter;
    }

    @GetMapping("/quote")
    public Map<String, String> quote(
            @RequestParam("code") ProductCode code,
            @RequestParam("amount") Amount amount,
            @RequestParam("deliveryDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate deliveryDate,
            @RequestParam("meetingAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime meetingAt,
            Locale locale) {

        Map<String, String> response = new LinkedHashMap<>();
        response.put("code", code.value());
        response.put("amount", amount.value().toPlainString());
        response.put("formattedAmount", amountFormatter.print(amount, locale));
        response.put("deliveryDate", deliveryDate.toString());
        response.put("meetingAt", meetingAt.toString());
        response.put("locale", locale.toLanguageTag());
        return response;
    }
}
~~~

Chữ ký handler cho thấy các bước parse **đã diễn ra trước khi vào method body**. `Locale` là argument MVC có thể resolve từ request; trong cấu hình MVC mặc định dựa trên `Accept-Language`. Thao tác `amountFormatter.print(...)` ở đây được gọi tường minh để quan sát chiều hiển thị; trong view MVC, cơ chế formatter có thể được dùng khi kết xuất field.

### 10.9. Chạy và quan sát

Tại thư mục `conversion-demo` có `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Gửi giá trị theo locale Mỹ (`--data-urlencode` tự encode dấu `+`, dấu phẩy, dấu chấm và phần còn lại của tham số):

~~~bash
curl -i -G 'http://localhost:8080/api/quote' \
  -H 'Accept-Language: en-US' \
  --data-urlencode 'code=ab-123' \
  --data-urlencode 'amount=1,234.50' \
  --data-urlencode 'deliveryDate=2026-12-31' \
  --data-urlencode 'meetingAt=2026-12-31T09:30:00.000+07:00'
~~~

HTTP **200 OK**, response body tương đương (JSON object có thể có thứ tự khác nếu cách serialize thay đổi):

~~~json
{
  "code": "AB-123",
  "amount": "1234.50",
  "formattedAmount": "1,234.50",
  "deliveryDate": "2026-12-31",
  "meetingAt": "2026-12-31T09:30+07:00",
  "locale": "en-US"
}
~~~

`OffsetDateTime.toString()` có thể rút gọn phần giây `:00` khi bằng 0; không nên so sánh với nguyên văn chuỗi đầu vào nếu hai cách biểu diễn cùng thời điểm.

Thử giá trị theo locale Việt Nam:

~~~bash
curl -i -G 'http://localhost:8080/api/quote' \
  -H 'Accept-Language: vi-VN' \
  --data-urlencode 'code=abc-123' \
  --data-urlencode 'amount=1.234,50' \
  --data-urlencode 'deliveryDate=2026-12-31' \
  --data-urlencode 'meetingAt=2026-12-31T09:30:00.000+07:00'
~~~

Hai trường chính trong JSON sẽ biểu diễn giá trị tương đương:

~~~json
{
  "amount": "1234.50",
  "formattedAmount": "1.234,50"
}
~~~

Các trường khác vẫn có trong response; đoạn JSON rút gọn ở đây chỉ minh họa hai field số. Hành vi ký hiệu nhóm/thập phân dựa trên dữ liệu locale của Java runtime; ví dụ này dùng dữ liệu locale chuẩn thông dụng.

Sai mã sản phẩm:

~~~bash
curl -i -G 'http://localhost:8080/api/quote' \
  -H 'Accept-Language: en-US' \
  --data-urlencode 'code=wrong' \
  --data-urlencode 'amount=1,234.50' \
  --data-urlencode 'deliveryDate=2026-12-31' \
  --data-urlencode 'meetingAt=2026-12-31T09:30:00.000+07:00'
~~~

Spring MVC không thể chuyển `"wrong"` thành `ProductCode` nên request thông thường trả **400 Bad Request**; handler `quote` không chạy. Tương tự, ngày `deliveryDate=31/12/2026` không khớp ISO.DATE; chuỗi `amount=abc` không phù hợp với `AmountFormatter`. Nội dung JSON lỗi cụ thể tùy cấu hình Spring Boot và xử lý exception, vì vậy không dựa vào message mặc định ổn định.

Trong Windows PowerShell, dùng `curl.exe` và viết lệnh trên một dòng hoặc thay dấu nối dòng kiểu Bash `\` bằng cú pháp PowerShell phù hợp.

### 10.10. Test HTTP binding bằng MockMvc

File `src/test/java/demo/QuoteControllerTest.java`:

~~~java
package demo;

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
class QuoteControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void acceptsEnglishLocale() throws Exception {
        mvc.perform(get("/api/quote")
                .header("Accept-Language", "en-US")
                .param("code", "ab-123")
                .param("amount", "1,234.50")
                .param("deliveryDate", "2026-12-31")
                .param("meetingAt", "2026-12-31T09:30:00.000+07:00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("AB-123"))
            .andExpect(jsonPath("$.amount").value("1234.50"))
            .andExpect(jsonPath("$.formattedAmount").value("1,234.50"))
            .andExpect(jsonPath("$.deliveryDate").value("2026-12-31"))
            .andExpect(jsonPath("$.locale").value("en-US"));
    }

    @Test
    void rejectsInvalidProductCode() throws Exception {
        mvc.perform(get("/api/quote")
                .header("Accept-Language", "en-US")
                .param("code", "bad")
                .param("amount", "1,234.50")
                .param("deliveryDate", "2026-12-31")
                .param("meetingAt", "2026-12-31T09:30:00.000+07:00"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidDate() throws Exception {
        mvc.perform(get("/api/quote")
                .header("Accept-Language", "en-US")
                .param("code", "abc-123")
                .param("amount", "1,234.50")
                .param("deliveryDate", "31/12/2026")
                .param("meetingAt", "2026-12-31T09:30:00.000+07:00"))
            .andExpect(status().isBadRequest());
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

Ba bài test kiểm tra **HTTP status và giá trị binding**; đây là kiểm thử nghiệp vụ của ứng dụng ví dụ, không phải kiểm thử chỉ đối chiếu nội dung phương thức converter. Khi test thất bại, nên xem cause của type conversion, config MVC và locale resolver trước khi thay đổi chuỗi expected một cách tùy tiện.

## 11. Conversion lỗi được báo ở đâu?

| Ngữ cảnh | Ví dụ lỗi | Đường xử lý thông thường |
| --- | --- | --- |
| `@RequestParam int quantity` | `quantity=abc` | Spring MVC ném exception không chuyển được argument, thường trả HTTP 400 |
| `@RequestParam ProductCode code` | `code=bad` | Converter ném `IllegalArgumentException`; quy trình argument binding thường trả HTTP 400 |
| `@ModelAttribute FormDto form` | `price=abc` trong field số | Lỗi binding có thể được lưu trong `BindingResult` nếu handler khai báo đúng |
| `@Valid @ModelAttribute FormDto` | `age=17` với `@Min(18)` | Đó là validation error, khác type mismatch |
| `@DateTimeFormat(iso=DATE)` | `31/12/2026` | Parse không khớp định dạng; lỗi binding |
| `@RequestBody JsonDto` | JSON ngày không hợp lệ | Jackson/HTTP message converter, không nhất thiết dùng Spring Formatter như form binding |

Ví dụ với form binding:

~~~java
@PostMapping("/submit")
public String submit(@Valid @ModelAttribute("form") FormDto form,
                     BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
        return "form";
    }
    return "success";
}
~~~

Đây là mẫu chữ ký dành cho ứng dụng MVC với views và class `FormDto`, không phải endpoint của dự án mục 10. `BindingResult` phải ngay sau model attribute mà nó xử lý. `BindingResult#getFieldErrors()` thường chứa lỗi `typeMismatch` khi không chuyển đổi được. Đừng dùng `@Valid` thay cho converter hoặc xem converter thay cho validation: chúng giải quyết các bước khác nhau.

## 12. Các lỗi triển khai thường gặp

1. **Tạo converter nhưng chưa đăng ký đúng registry MVC.** `new ProductCodeConverter()` trong service không làm controller tự biết converter. Đăng ký bằng `WebMvcConfigurer#addFormatters` (hoặc cơ chế tích hợp phù hợp).
2. **Dùng Converter khi cần Locale.** `Converter<String,Amount>` không nhận Locale trực tiếp; dùng `Formatter<Amount>` khi format của người dùng theo vùng.
3. **Chia sẻ `SimpleDateFormat`/`DecimalFormat` mutable trong singleton.** Những object này thường không thread-safe; nên tạo từng lần hoặc dùng `DateTimeFormatter` immutable cho Java Time.
4. **Nhầm `MM` với `mm`**, `HH` với `hh`, `yyyy` với `uuuu` trong Java Time, hoặc quên escape literal theo đúng pattern syntax.
5. **Dùng `LocalDateTime` như thời điểm UTC.** Cần offset/zone để ra thời điểm toàn cục; nếu dữ liệu có offset nên giữ `OffsetDateTime` hoặc chuyển rõ sang `Instant`.
6. **Quên mã hóa `+` trong query.** Khi gửi offset `+07:00`, sử dụng `--data-urlencode` hoặc `%2B07:00`.
7. **Giả định Locale client luôn giống Locale máy chủ.** Kiểm tra locale resolver, header `Accept-Language` hoặc giao diện chọn ngôn ngữ và giới hạn locale mà hệ thống chấp nhận.
8. **Gửi JSON tới controller nhưng chỉ đặt `@DateTimeFormat`.** JSON body tuân theo Jackson/message converters; định dạng request params/form fields thuộc MVC conversion/formatting.
9. **Dùng style locale làm chuẩn lưu trữ.** Format mặc định có thể thay đổi theo JDK/CLDR/locale; dùng ISO hoặc pattern cố định cho hợp đồng API.
10. **Nhầm type conversion với validation.** `BigDecimal` parse thành công không có nghĩa tiền ≥ 0 hoặc hợp lệ với đơn vị tiền.
11. **Để formatter âm thầm bỏ qua ký tự dư.** Khi tự parse, cần kiểm tra parser đã dùng hết toàn bộ input.
12. **Tạo `ConversionService` mới rồi nghĩ MVC đang dùng chính instance đó.** Cần phân biệt conversion service cho application/service layer và hệ conversion/formatting đang gắn với MVC data binder.

## 13. Bài tập tự luyện

**Bài 1 — Converter:** Tạo `Converter<String, OrderId>` nhận `ORD-12345`, chuẩn hóa uppercase, từ chối chuỗi không hợp lệ. Đăng ký bằng `addFormatters` và thử `@RequestParam OrderId orderId`.

**Bài 2 — Formatter:** Viết `Formatter<Area>` gồm `BigDecimal squareMeters`. Với `en-US` chấp nhận `1,234.50`, với `vi-VN` chấp nhận `1.234,50`. Dùng `parse` và `print`; kiểm tra dữ liệu dư sau số.

**Bài 3 — Date:** Tạo `BookingForm` có `LocalDate startDate` được chú thích `@DateTimeFormat(iso=DATE)`. Dự đoán điều gì xảy ra với `2026-02-28`, `2026-02-30`, `28/02/2026`; không nhầm lỗi ngày không tồn tại và lỗi format.

**Bài 4 — Timezone:** Giải thích vì sao `2026-12-31T09:30` chưa xác định một `Instant` duy nhất. Chuyển `2026-12-31T09:30+07:00` sang UTC (kết quả: `2026-12-31T02:30Z`).

**Bài 5 — Debug:** Có `@RequestParam Price price`, client gửi `price=1,234.50`, app dùng locale `vi-VN` và báo lỗi. Hãy kiểm tra loại formatter, locale resolver, chuỗi gửi, việc đăng ký converter/formatter và dữ liệu test; tránh sửa bằng thay ký tự không có quy tắc.

**Bài 6 — Phân biệt đường xử lý:** Viết một endpoint GET nhận `@RequestParam @DateTimeFormat LocalDate` và một endpoint POST nhận JSON có trường `LocalDate`. So sánh nơi cấu hình định dạng và lỗi parse của hai endpoint.

## 14. Ghi nhớ

- `Converter` giải quyết quy tắc chuyển **kiểu**; `Formatter` giải quyết parse/print theo **Locale**.
- `ConversionService` điều phối chuyển đổi; `FormattingConversionService` kết hợp các formatter; `FormatterRegistry` giúp đăng ký.
- `WebMvcConfigurer#addFormatters` tích hợp custom conversion cho Spring MVC; `@DateTimeFormat`/`@NumberFormat` áp dụng cho field hoặc method parameter.
- Chuẩn ngày giờ cần phân biệt **ngày**, **giờ địa phương**, **offset**, **timezone** và **instant**.
- Xử lý lỗi binding cùng Bean Validation và định nghĩa hợp đồng format rõ ràng cho client.

## 15. Tài liệu Spring Framework

- [Spring Core — Type Conversion](https://docs.spring.io/spring-framework/reference/6.2/core/validation/convert.html): Converter, ConverterFactory, GenericConverter, ConversionService, TypeDescriptor.
- [Spring Core — Field Formatting](https://docs.spring.io/spring-framework/reference/6.2/core/validation/format.html): Formatter, Printer, Parser, AnnotationFormatterFactory, registry.
- [Spring MVC — Type Conversion](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/conversion.html): đăng ký qua WebMvcConfigurer, formatters mặc định, cấu hình ISO cho form.
- [Spring MVC — Data Binding](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html): binding và chuyển kiểu đối số controller.
- [Spring API — DateTimeFormat](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/format/annotation/DateTimeFormat.html): iso, pattern, style, fallbackPatterns.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-validation-004">@InitBinder: cấu hình cục bộ và dùng chung qua @ControllerAdvice</a>

<details>
<summary>Xem chi tiết</summary>

# @InitBinder: cấu hình cục bộ và dùng chung qua @ControllerAdvice trong Spring MVC

## Mục tiêu và phạm vi

Trong Spring MVC, `@InitBinder` cho phép bạn tùy chỉnh **`WebDataBinder`** trước khi Spring bind dữ liệu từ HTTP request vào tham số handler. Bài này giải thích `WebDataBinder` làm gì, phạm vi cấu hình **cục bộ trong controller** và **dùng chung qua `@ControllerAdvice`**, cách đặt tên model attribute, đăng ký bộ chuyển đổi/formatter, giới hạn field được bind, phối hợp với validation và `BindingResult`, cũng như cách kiểm thử tránh lỗi và rủi ro mass assignment.

Ví dụ sử dụng **Java 17+**, **Spring MVC trên Servlet stack**, **Spring Boot 3.x**. Các đoạn có đường dẫn file tạo thành một ứng dụng mẫu; ví dụ riêng được đánh dấu là minh họa. Spring WebFlux có cơ chế data binding tương tự nhưng không phải chủ đề ở đây.

## 1. Hiểu data binding trước khi dùng `@InitBinder`

Một form gửi request:

~~~http
POST /appointments HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded

visitorName=Lan&date=2026-10-21
~~~

Handler có thể nhận một object:

~~~java
@PostMapping("/appointments")
public String create(@ModelAttribute("appointmentForm") AppointmentForm form) {
    return form.getVisitorName();
}
~~~

Đây chỉ là phần thân phương thức minh họa: `AppointmentForm` là JavaBean có property `visitorName` và `date`.

Luồng đơn giản hóa của Spring MVC:

1. Chọn handler method phù hợp request.
2. Tạo hoặc tìm model attribute cần bind, ví dụ `appointmentForm`.
3. Tạo `WebDataBinder` gắn với target và object name tương ứng.
4. Áp dụng các phương thức `@InitBinder` đủ điều kiện, gồm phần dùng chung từ `@ControllerAdvice` và phần khai báo trong controller.
5. Bind dữ liệu request vào object bằng cơ chế constructor binding và/hoặc property binding tương ứng, đồng thời chuyển kiểu dữ liệu (ví dụ chuỗi `2026-10-21` thành `LocalDate`).
6. Nếu handler yêu cầu validation (`@Valid`, `@Validated`), Spring tiến hành validation; handler có thể đọc lỗi qua `BindingResult` đặt đúng vị trí.
7. Gọi handler với tham số đã bind.

Mỗi lần bind được tạo **`WebDataBinder` riêng**, nên cấu hình editor/formatter hay danh sách field trên binder áp dụng cho lần bind đó. Phương thức `@InitBinder` **không phải HTTP endpoint**, không tự tạo response, không thay thế `@PostMapping` và thường có kiểu trả về `void`.

`@InitBinder` được khai báo trong lớp `@Controller`/`@RestController` hoặc `@ControllerAdvice`. Đối số thông dụng nhất là `WebDataBinder`. Tùy phiên bản và cấu hình, có thể dùng một số đối số ngữ cảnh được hỗ trợ như `WebRequest` hoặc `Locale`; không khai báo tùy ý một `@ModelAttribute` làm đối số cho chính phương thức `@InitBinder`.

## 2. Phạm vi cục bộ: đặt `@InitBinder` trong một controller

~~~java
@RestController
class AppointmentController {

    @InitBinder
    void configureAllBindersInThisController(WebDataBinder binder) {
        binder.setAllowedFields("visitorName", "date");
    }

    // Các handler @RequestMapping/@PostMapping ở đây...
}
~~~

Đoạn trên chỉ minh họa cấu trúc, chưa phải file Java chạy độc lập (thiếu import và các handler). `@InitBinder` **không có tên** có thể áp dụng cho các binder phù hợp bên trong controller này. Nếu controller xử lý nhiều form khác nhau, áp dụng danh sách field chung cho tất cả có thể vô tình chặn những trường hợp hợp lệ.

Để giới hạn theo đúng **object name** của model attribute, sử dụng `@InitBinder("appointmentForm")`:

~~~java
@InitBinder("appointmentForm")
void configureAppointment(WebDataBinder binder) {
    binder.setAllowedFields("visitorName", "date");
}
~~~

Khai báo tương ứng ở handler:

~~~java
@PostMapping("/appointments")
public String create(
        @ModelAttribute("appointmentForm") AppointmentForm form) {
    return form.getVisitorName();
}
~~~

Tên `appointmentForm` phải khớp. Tham số `value` của `@InitBinder` cũng nhận nhiều tên, chẳng hạn `@InitBinder({"appointmentForm", "bookingForm"})`. Tên này là **tên model attribute hoặc request parameter mà binder xử lý**, không phải URL, tên Java class hoặc tên một method.

Khi cùng controller có `@InitBinder("appointmentForm")` và `@InitBinder("profileForm")`, mỗi binder có thể nhận cấu hình khác nhau. Đừng đặt tên từ thói quen rồi bỏ kiểm tra: nếu model attribute có tên khác, cấu hình cục bộ có thể không chạy trên object mong muốn.

## 3. Phạm vi dùng chung: `@InitBinder` trong `@ControllerAdvice`

~~~java
@ControllerAdvice
public class SharedBindingAdvice {

    @InitBinder
    public void configure(WebDataBinder binder) {
        binder.registerCustomEditor(
                String.class, new StringTrimmerEditor(true));
    }
}
~~~

Đây là đoạn minh họa, cần import `WebDataBinder`, `StringTrimmerEditor` và `@ControllerAdvice`/`@InitBinder`. Editor được tạo trong lúc khởi tạo binder và sẽ cắt khoảng trắng đầu/cuối các property kiểu `String` được bind; đối số `true` biến chuỗi rỗng sau khi cắt thành `null`.

Nếu không chỉ định selector, `@ControllerAdvice` có phạm vi rộng, có thể áp dụng với các controller được Spring MVC quản lý. Có thể thu hẹp advice bằng:

~~~java
@ControllerAdvice(assignableTypes = {
        AppointmentController.class, ProfileController.class
})
~~~

Hoặc dùng selector theo package/annotation, ví dụ `basePackages = "com.example.demo.form"` hay `annotations = RestController.class`. Những selector ở đây là **cách chọn controller**, còn `@InitBinder("appointmentForm")` là **cách chọn target của binder trong controller đã được chọn**. Hai tầng phạm vi này bổ sung cho nhau.

Trong ứng dụng lớn, không nên tự động triển khai một cấu hình thay đổi mọi chuỗi hoặc mọi field lên tất cả controller. Ví dụ trim `String` có thể hợp lý với tên hiển thị nhưng lại làm thay đổi mật khẩu, mã ký, token hay chuỗi mà khoảng trắng có ý nghĩa. Hãy xác định rõ phạm vi từng advice.

**So sánh nhanh:**

| Khai báo | Phạm vi |
| --- | --- |
| `@InitBinder` trong `AppointmentController` | Các binder phù hợp của controller đó |
| `@InitBinder("appointmentForm")` trong `AppointmentController` | Binder có object name `appointmentForm` trong controller đó |
| `@InitBinder` trong `@ControllerAdvice` không selector | Các binder phù hợp ở các controller thuộc phạm vi advice |
| `@InitBinder("appointmentForm")` trong advice có selector | Controller khớp selector **và** binder khớp tên attribute |
| `WebMvcConfigurer#addFormatters(...)` | Đăng ký converter/formatter trong cấu hình MVC dùng chung |

Nếu nhiều advice cùng có thể áp dụng, cấu hình phải tránh xung đột (chẳng hạn hai nơi cùng đăng ký editor cho một kiểu). Đừng dựa vào thứ tự ngầm của những cấu hình trùng chức năng; tách phạm vi rõ ràng và kiểm thử kết quả cuối.

## 4. Bạn có thể cấu hình gì trên `WebDataBinder`?

| Cơ chế | Ý nghĩa | Khi dùng |
| --- | --- | --- |
| `setAllowedFields(...)` | Chỉ cho phép **property binding** những field khai báo | Phòng trường hợp client gửi thêm property không mong muốn |
| `setRequiredFields(...)` | Yêu cầu field xuất hiện khi bind input | Một số quy tắc có mặt của request field, không thay mọi validation |
| `registerCustomEditor(...)` | Đăng ký `PropertyEditor` chuyển đổi | Tình huống cần editor kiểu cũ hoặc logic cụ thể theo binder |
| `addCustomFormatter(...)` | Đăng ký `Formatter` cho kiểu/field | Chuyển giá trị có quy tắc hiển thị và parse phù hợp |
| `addValidators(...)` | Bổ sung `org.springframework.validation.Validator` | Kiểm tra nghiệp vụ bổ sung bên cạnh Bean Validation |
| `setDeclarativeBinding(true)` | Kiểm soát cách binding theo khai báo tường minh (nếu phiên bản hỗ trợ) | Giới hạn property binding trên những object cần bảo vệ |

`setAllowedFields` là **allowlist của setter/property binding**, đặc biệt cần cân nhắc khi bind những object có cả thuộc tính người dùng được nhập và thuộc tính nội bộ. `setRequiredFields` kiểm tra mức binding field có được cung cấp, **khác** với các annotation `@NotBlank`, `@NotNull`, `@Size` kiểm tra giá trị hợp lệ. Với API hiện đại, thường ưu tiên DTO chỉ chứa input được phép và Bean Validation.

Một cách chuyển kiểu ngày tháng thông thường là dùng ngay:

~~~java
@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
private LocalDate date;
~~~

Khi cần quy tắc riêng ở quy mô nhiều controller, cân nhắc đăng ký `Converter` hoặc `Formatter` qua `WebMvcConfigurer#addFormatters` và `FormattingConversionService`. Chỉ dùng `@InitBinder` để xử lý riêng tại binder khi phạm vi hẹp là chủ đích.

**Bảo mật:** Theo tài liệu Spring MVC, `allowedFields` là danh sách cho property binding, không nên hiểu là biện pháp kiểm soát constructor binding trong mọi trường hợp. Với object có constructor từ tham số request, hãy sử dụng DTO chuyên biệt, kiểm soát constructor input và cấu hình declarative binding thích hợp. Đừng bind trực tiếp entity/domain object nhiều thuộc tính nhạy cảm rồi trông chờ frontend “không gửi” những field đó.

## 5. Ứng dụng chạy được: một cấu hình local và một advice dùng chung

Tạo dự án Maven từ Spring Initializr với **Java 17+**, **Spring Boot 3.x**, dependencies **Spring Web**, **Validation** và **Spring Boot Starter Test**. Các dependency tương ứng là `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-test`. Giữ package Java là `com.example.demo`.

Cấu trúc:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/AppointmentController.java
src/main/java/com/example/demo/ProfileController.java
src/main/java/com/example/demo/FormBindingAdvice.java
src/test/java/com/example/demo/BindingControllerTest.java
~~~

### 5.1. Điểm khởi động

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

### 5.2. `@ControllerAdvice` cấu hình chung cho hai controller

**`src/main/java/com/example/demo/FormBindingAdvice.java`**

~~~java
package com.example.demo;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

@ControllerAdvice(assignableTypes = {
        AppointmentController.class, ProfileController.class
})
public class FormBindingAdvice {

    @InitBinder
    public void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(
                String.class, new StringTrimmerEditor(true));
    }
}
~~~

Advice này chỉ áp dụng cho hai controller được liệt kê. Khi bind các form object trong hai controller, `StringTrimmerEditor` sẽ chuẩn hóa khoảng trắng. Do đang minh họa một quy tắc cắt khoảng trắng, hãy **không** mở rộng advice cho form có secret/password nếu sản phẩm cần giữ nguyên từng ký tự.

### 5.3. `@InitBinder("appointmentForm")` giới hạn field cục bộ

**`src/main/java/com/example/demo/AppointmentController.java`**

~~~java
package com.example.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppointmentController {

    @InitBinder("appointmentForm")
    public void restrictFields(WebDataBinder binder) {
        binder.setAllowedFields("visitorName", "date");
    }

    @PostMapping(
            path = "/appointments",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> create(
            @Valid @ModelAttribute("appointmentForm") AppointmentForm form,
            BindingResult errors) {

        if (errors.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid appointment"));
        }

        return ResponseEntity.ok(new AppointmentResponse(
                form.getVisitorName(),
                form.getDate(),
                form.getRole()));
    }

    public record AppointmentResponse(
            String visitorName,
            LocalDate date,
            String role) {
    }

    public static class AppointmentForm {
        @NotBlank
        private String visitorName;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate date;

        // Thuộc tính cố ý để minh họa không bind từ request.
        private String role = "viewer";

        public AppointmentForm() {
        }

        public String getVisitorName() {
            return visitorName;
        }

        public void setVisitorName(String visitorName) {
            this.visitorName = visitorName;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
~~~

**Phân tích kỹ ví dụ:**

- `@ModelAttribute("appointmentForm")` khớp tên trong `@InitBinder("appointmentForm")`; do đó allowlist `visitorName`, `date` áp dụng đúng form.
- `AppointmentForm` có constructor không đối số và setter, nên ví dụ minh họa **property binding** cụ thể. Dù client gửi `role=admin`, property `role` không nằm trong allowlist và giữ giá trị `"viewer"`.
- `@DateTimeFormat(iso = ISO.DATE)` hướng dẫn chuyển chuỗi dạng `yyyy-MM-dd` sang `LocalDate`; format sai tạo lỗi binding.
- `@Valid` chạy Bean Validation sau binding. `@NotBlank` không chấp nhận `visitorName` rỗng, `@NotNull` không chấp nhận `date` null.
- `BindingResult errors` phải đặt **ngay sau tham số model attribute** tương ứng. Nếu đặt sai vị trí, Spring MVC không có cùng cơ chế đưa lỗi của attribute này cho handler.
- Nếu có lỗi, endpoint tự trả HTTP 400. Nếu không, endpoint trả JSON với `role` để **thấy hiệu ứng của allowlist**; sản phẩm thật không nên tiết lộ thông tin nội bộ chỉ để chứng minh cơ chế này.

`@InitBinder` không phải nơi phù hợp để xác thực danh tính hoặc quyết định quyền truy cập: phép kiểm tra `role` trong ví dụ là minh họa tránh **mass assignment**, còn phân quyền thực tế phải do tầng bảo mật/nghiệp vụ thực hiện.

### 5.4. Controller thứ hai nhận cùng quy tắc từ advice

**`src/main/java/com/example/demo/ProfileController.java`**

~~~java
package com.example.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {

    @PostMapping(
            path = "/profiles",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> create(
            @Valid @ModelAttribute("profileForm") ProfileForm form,
            BindingResult errors) {

        if (errors.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid profile"));
        }

        return ResponseEntity.ok(
                Map.of("displayName", form.getDisplayName()));
    }

    public static class ProfileForm {
        @NotBlank
        private String displayName;

        public ProfileForm() {
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}
~~~

`ProfileController` không hề khai báo `@InitBinder`, nhưng **vẫn được advice chọn** qua `assignableTypes`; input `displayName` vì vậy cũng được trim. Mặt khác, allowlist ở `AppointmentController` **không** ảnh hưởng form `profileForm`: hai controller có giới hạn cục bộ khác nhau.

## 6. Chạy request và giải thích kết quả

Sau khi tạo file theo cấu trúc trên, chạy:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows với Maven Wrapper: `mvnw.cmd spring-boot:run`. Nếu không có wrapper, dùng `mvn spring-boot:run` khi máy đã cài Maven.

Thử gọi bằng `curl`:

~~~bash
curl -i -X POST "http://localhost:8080/appointments"   -d "visitorName=%20%20Lan%20%20"   -d "date=2026-10-21"   -d "role=admin"
~~~

Đây là cú pháp shell dùng dấu `\` để nối dòng (Linux/macOS/Git Bash); trên PowerShell nên dùng `curl.exe` với toàn bộ tham số trên một dòng. `curl -d` mặc định gửi body `application/x-www-form-urlencoded`, khớp với `consumes` của controller.

Kết quả **200 OK** (thứ tự property JSON không quan trọng):

~~~json
{"visitorName":"Lan","date":"2026-10-21","role":"viewer"}
~~~

Lý do:

- Advice toàn cục có phạm vi chọn `AppointmentController` đã trim `"  Lan  "` thành `"Lan"`.
- Binder local cho `appointmentForm` cho phép `visitorName` và `date`, nhưng loại `role` khỏi property binding, nên `viewer` không bị thay bằng `admin`.
- `@DateTimeFormat` giúp bind ngày đúng định dạng; `@Valid` xác nhận dữ liệu hợp lệ.

Thử controller thứ hai:

~~~bash
curl -i -X POST "http://localhost:8080/profiles"   -d "displayName=%20%20Mai%20%20"
~~~

HTTP **200 OK**, body kỳ vọng:

~~~json
{"displayName":"Mai"}
~~~

Thử gửi tên toàn dấu cách hoặc ngày sai định dạng:

~~~bash
curl -i -X POST "http://localhost:8080/appointments"   -d "visitorName=%20%20%20"   -d "date=2026-10-21"

curl -i -X POST "http://localhost:8080/appointments"   -d "visitorName=Lan"   -d "date=21/10/2026"
~~~

Cả hai request đều nhận **400 Bad Request** trong ví dụ: request đầu có `visitorName` sau trim thành `null`, không đạt `@NotBlank`; request sau không chuyển được `date` theo ISO. Controller minh họa trả body:

~~~json
{"error":"invalid appointment"}
~~~

Những ví dụ này giả định ứng dụng chạy trên cổng Spring Boot mặc định `8080` và chưa có cấu hình Spring Security yêu cầu xác thực/CSRF. Nếu project thêm Spring Security, bạn phải cấu hình authentication và CSRF phù hợp trước khi sử dụng lệnh `curl` trên.

## 7. Kiểm thử bằng MockMvc để chứng minh phạm vi binder

Thêm **`src/test/java/com/example/demo/BindingControllerTest.java`**:

~~~java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AppointmentController.class, ProfileController.class
})
@Import(FormBindingAdvice.class)
class BindingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void localAllowlistAndSharedTrimmingWorkTogether() throws Exception {
        mockMvc.perform(post("/appointments")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("visitorName", "  Lan  ")
                        .param("date", "2026-10-21")
                        .param("role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitorName").value("Lan"))
                .andExpect(jsonPath("$.date").value("2026-10-21"))
                .andExpect(jsonPath("$.role").value("viewer"));
    }

    @Test
    void sharedAdviceAlsoAppliesToProfileController() throws Exception {
        mockMvc.perform(post("/profiles")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("displayName", "  Mai  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Mai"));
    }

    @Test
    void blankVisitorNameFailsValidation() throws Exception {
        mockMvc.perform(post("/appointments")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("visitorName", "   ")
                        .param("date", "2026-10-21"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidDateFailsConversion() throws Exception {
        mockMvc.perform(post("/appointments")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("visitorName", "Lan")
                        .param("date", "21/10/2026"))
                .andExpect(status().isBadRequest());
    }
}
~~~

Chạy:

~~~bash
./mvnw test
~~~

Bài kiểm thử thứ nhất là bằng chứng đồng thời cho **phạm vi local** (field `role` bị bỏ qua) và **phạm vi shared** (tên được trim). Bài thứ hai chứng minh advice có hiệu lực ở controller khác. Hai bài cuối giúp phát hiện lỗi giữa chuyển kiểu và validation. `@Import(FormBindingAdvice.class)` bảo đảm advice có mặt trong Spring MVC test slice, ngay cả khi cấu hình quét component của test khác cấu hình ứng dụng chính.

`MockMvc` mô phỏng request tới Spring MVC mà không cần mở cổng HTTP thật; nó kiểm tra binder, argument resolution, validation và response của controller. Khi có `SecurityFilterChain` tùy chỉnh, các test cần cấu hình an ninh phù hợp.

## 8. Đăng ký `PropertyEditor`, `Formatter` và `Validator`: nên dùng ở đâu?

### `PropertyEditor`: cách cổ điển

~~~java
@InitBinder
void initBinder(WebDataBinder binder) {
    binder.registerCustomEditor(
            String.class, new StringTrimmerEditor(true));
}
~~~

Đoạn này đã dùng trong ví dụ chính. Các `PropertyEditor` thường giữ trạng thái, vì vậy **không chia sẻ một instance mutable cho nhiều request/thread**. Tạo instance mới khi binder được khởi tạo như code trên. Với `java.util.Date`, Spring còn có `CustomDateEditor`, nhưng khi dùng `java.time` hiện đại, `@DateTimeFormat`/`Formatter` thường gọn hơn.

### `Formatter`: khi cần parse/print một kiểu nhất quán

Ví dụ minh họa dưới đây dành cho property kiểu `java.util.Date`, không phải `LocalDate`:

~~~java
@InitBinder("legacyForm")
void initLegacyDate(WebDataBinder binder) {
    binder.addCustomFormatter(new DateFormatter("yyyy-MM-dd"));
}
~~~

Trong ngữ cảnh controller, bạn cần import `org.springframework.format.datetime.DateFormatter` và `org.springframework.web.bind.WebDataBinder`. Nếu formatter cần áp dụng ở nhiều nơi theo cùng quy tắc, hãy cân nhắc đặt trong shared MVC `ConversionService` thay vì lặp các `@InitBinder`.

### `Validator`: bổ sung quy tắc nghiệp vụ

Có thể dùng `binder.addValidators(customValidator)` để thêm một validator tương thích `org.springframework.validation.Validator`. Một validator kiểu này cần thực hiện `supports(Class<?>)` và `validate(Object, Errors)`. Lúc chạy, Spring kiểm tra kiểu target có phù hợp validator không; đăng ký sai validator có thể gây lỗi cấu hình.

`@InitBinder` chỉ **đăng ký** validator; nó không có nghĩa mọi handler đều tự động kiểm tra khi không có kích hoạt validation thích hợp. Dùng `@Valid` hoặc `@Validated` cho model attribute khi muốn Spring kích hoạt validation, hoặc gọi kiểm tra rõ ràng nếu thiết kế yêu cầu. Với Bean Validation thông thường, có thể dùng `@NotBlank`, `@Size`, `@Email`, `@NotNull` và kiểm tra `BindingResult`.

Nếu muốn thay đổi validator đang có, cần hiểu khác biệt giữa `addValidators` (bổ sung) và `setValidator` (thiết lập/thay thế một validator). Đừng vô tình thay mất Bean Validation chuẩn đang dùng.

## 9. Giới hạn quan trọng: `@InitBinder` không thay thế JSON deserialization

`@ModelAttribute` thường nhận dữ liệu query/form từ request và bind bằng `WebDataBinder`, trong khi `@RequestBody` thường đọc JSON qua `HttpMessageConverter`/Jackson. Ví dụ:

~~~java
@PostMapping("/users")
public void create(@RequestBody CreateUserRequest request) {
    // Jackson đọc JSON của toàn request thành CreateUserRequest.
}
~~~

Đây là đoạn minh họa chưa độc lập. Một `StringTrimmerEditor` gắn vào `WebDataBinder` **không có nghĩa** mọi chuỗi mà Jackson đọc từ JSON đều được trim. Với `@RequestBody`, hãy dùng cơ chế phù hợp ở tầng Jackson/deserializer, normalizer dành riêng cho dữ liệu hoặc xử lý ở service/DTO. Cơ chế `WebDataBinder` vẫn có thể liên quan đến **validation** của đối tượng được `@RequestBody` tạo ra, nhưng đừng nhầm điều đó với setter binding từ form.

Tương tự, `@InitBinder` không thay cho `@ControllerAdvice @ExceptionHandler`: hai loại callback có nhiệm vụ khác nhau. `@InitBinder` **cấu hình binding**; `@ExceptionHandler` **xử lý ngoại lệ**. Một lớp advice có thể chứa cả hai, nhưng nên phân tách rõ trách nhiệm.

## 10. Bảo mật binding: allowlist, constructor binding và mass assignment

Giả sử một entity `User` có các property `displayName`, `email`, `isAdmin`. Nếu bind request trực tiếp vào entity bằng setter, attacker có thể thêm `isAdmin=true` ngay cả khi giao diện không hiển thị trường đó. Đó là **mass assignment**.

Các biện pháp ưu tiên:

1. Tạo **input DTO** chỉ chứa các field người dùng được quyền cung cấp; chuyển DTO sang entity qua service theo luật nghiệp vụ.
2. Nếu cần property binding, dùng `WebDataBinder#setAllowedFields("fieldA", "fieldB")` theo từng model attribute. Cấu hình này chủ yếu bảo vệ **setter/property binding**, không nên đánh đồng với kiểm soát constructor parameter.
3. Với Spring Framework có hỗ trợ `setDeclarativeBinding(true)`, cân nhắc chế độ declarative binding: constructor binding vẫn hoạt động, property binding cần allowlist được cấu hình. Cần hiểu constructor DTO nhận input nào và kiểm thử cụ thể.
4. Kiểm tra quyền tại service/security layer dựa trên người dùng đã xác thực và trạng thái phía server, không tin `role`, `isAdmin` hoặc `ownerId` do client gửi.
5. Đừng dùng `setDisallowedFields` làm giải pháp mặc định khi có thể dùng allowlist: denylist dễ bỏ sót thuộc tính mới. Một số phiên bản Spring mới cũng định hướng tránh API này.

Trong code mẫu, field `role` với setter tồn tại chỉ để thấy tác dụng của allowlist; thiết kế API thật nên bỏ field đó khỏi form DTO nếu không phải input hợp lệ.

## 11. Lỗi thường gặp, cách xác minh

| Dấu hiệu | Khả năng | Cách kiểm tra |
| --- | --- | --- |
| `@InitBinder` local không chạy | Sai tên `@ModelAttribute` hoặc binder không thuộc controller mong đợi | Đối chiếu `@InitBinder("...")` và `@ModelAttribute("...")` |
| Advice không có hiệu lực | Controller không khớp `assignableTypes`, `basePackages` hoặc `annotations` | Kiểm tra selector và vị trí bean được Spring quét |
| Editor chạy cho form ngoài ý muốn | Advice quá rộng hoặc binder không có tên hạn chế | Thu hẹp selector, tách rule theo controller/attribute |
| Chuyển ngày bị lỗi | Định dạng chuỗi không khớp formatter/`@DateTimeFormat` | Kiểm tra input và expected pattern |
| `BindingResult` không nhận lỗi cần thiết | Đặt sai vị trí hoặc handler không có attribute tương ứng | Đặt ngay sau tham số `@Valid @ModelAttribute` |
| Field nội bộ bất ngờ bị thay đổi | Bind trực tiếp vào object quá rộng; thiếu allowlist | Dùng DTO, `setAllowedFields`, kiểm thử gửi field lạ |
| Một field hợp lệ không bind | Allowlist thiếu tên property | Bổ sung đúng property sau khi xác nhận contract input |
| Trim không tác động lên JSON `@RequestBody` | Jackson xử lý JSON riêng | Cấu hình Jackson hoặc normalizer riêng |
| Custom editor gây lỗi ngẫu nhiên khi tải cao | Chia sẻ editor mutable không an toàn giữa thread | Tạo editor theo binder; cân nhắc formatter/converter stateless |
| Handler vẫn trả 400 dù không gọi service | Lỗi binding/validation đã được phát hiện trước | Xem log, `BindingResult` hoặc exception handler cấu hình |

Khi cần kiểm chứng field bị từ chối bởi allowlist, `BindingResult#getSuppressedFields()` có thể cho biết tên field request đã bị suppress. Đây là tín hiệu chẩn đoán hữu ích; không thay thế việc xác minh API không vô tình lưu giá trị trái phép.

## 12. Câu hỏi ôn tập

**1. Tại sao `@InitBinder("appointmentForm")` không tương đương với `@InitBinder`?**

Vì bản có tên chỉ áp dụng cho binder của attribute/request parameter có tên khớp; bản không tên có phạm vi rộng hơn trong controller hoặc advice sở hữu method.

**2. Nếu ba controller dùng chung một quy tắc chuẩn hóa form thì đặt ở đâu?**

Ưu tiên `@ControllerAdvice` có selector khớp chính xác ba controller, hoặc cơ chế converter/formatter dùng chung ở MVC config nếu đây là quy tắc conversion cấp toàn ứng dụng.

**3. Gửi `role=admin` vào form mẫu tại sao vẫn nhận `role=viewer`?**

Vì `AppointmentForm` được bind theo property và allowlist chỉ có `visitorName`, `date`; giá trị client gửi cho `role` bị bỏ qua. Quyền truy cập thực tế vẫn phải kiểm tra ở server.

**4. `@Valid` có thể thay thế allowlist không?**

Không. Validation xác nhận giá trị sau/bên cạnh binding; allowlist giới hạn property nào được phép bind. Chúng giải quyết hai câu hỏi khác nhau và có thể cùng cần thiết.

**5. Tại sao editor String trên WebDataBinder không tự động trim JSON `@RequestBody`?**

Vì JSON được `HttpMessageConverter` (thường Jackson) deserialization, khác với cơ chế property binding form/query qua `WebDataBinder`.

## 13. Checklist trước khi dùng trong dự án

- Đặt tên model attribute tường minh nếu binder chỉ dành cho một loại form; kiểm tra tên trong `@InitBinder` và `@ModelAttribute` khớp.
- Đưa quy tắc thực sự dùng chung vào advice có phạm vi phù hợp; dùng global `ConversionService` nếu nhu cầu chỉ là converter/formatter chung.
- Sử dụng DTO input, allowlist đối với setter binding khi cần, và validation với `@Valid`/`BindingResult`; xác thực và phân quyền tại server.
- Phân biệt lỗi conversion, lỗi validation và field bị suppress. Kiểm thử tình huống hợp lệ, thiếu, sai định dạng và gửi field không được phép.
- Không dùng `@InitBinder` để thay thế JSON deserializer, exception handler hoặc security filter.

## Tài liệu tham khảo chính thức

- [Spring Framework — `@InitBinder` trong Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-initbinder.html)
- [Spring Framework — Data Binding](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-data-binding.html)
- [Spring Framework — `@ModelAttribute`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html)
- [Spring Framework — Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- [Spring Framework — Controller Advice](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-validation-005">Giới hạn trường được binding để tránh sửa thuộc tính ngoài hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn trường được binding để tránh sửa thuộc tính ngoài hợp đồng trong Spring MVC

> **Bối cảnh:** Spring Web MVC (Servlet stack), Java 17+ và Spring Boot 3.x. Trọng tâm là ngăn **mass assignment / over-posting**: client gửi thêm tên trường ngoài hợp đồng API và framework vô tình gán chúng vào thuộc tính mà người dùng không được phép sửa. Bài học hướng dẫn thiết kế DTO, `@InitBinder` với `WebDataBinder#setAllowedFields`, kiểm tra trường bị loại, giới hạn constructor binding, phân biệt form binding với JSON deserialization, và kiểm soát cập nhật ở tầng nghiệp vụ.

## 1. Vấn đề: client có thể gửi nhiều hơn những gì giao diện hiển thị

Giả sử endpoint chỉ cho phép người dùng cập nhật tên hiển thị và phần giới thiệu:

```http
POST /profiles/42
Content-Type: application/x-www-form-urlencoded

displayName=Lan&bio=Java+learner
```

Một client tùy ý có thể thêm:

```http
POST /profiles/42
Content-Type: application/x-www-form-urlencoded

displayName=Lan&bio=Java+learner&admin=true&ownerId=7
```

Trường `admin` và `ownerId` **không có trong hợp đồng chỉnh sửa profile**. Việc giao diện HTML không tạo input tương ứng không ngăn client tự gửi các tham số ấy.

Ví dụ **không an toàn**:

```java
// Minh họa rủi ro, không dùng cho ứng dụng thực.
@PostMapping("/profiles/{id}")
public String update(@PathVariable long id,
                     @ModelAttribute Account account) {
    accountRepository.save(account);
    return "saved";
}
```

Nếu `Account` có setter công khai cho `admin`, `role` hoặc `ownerId`, quá trình property binding có thể gán các trường mà client gửi vào object. Không phải cứ đưa ID vào URL là tự động kiểm tra quyền sở hữu; `save` cũng không bảo đảm người dùng được phép sửa toàn bộ object.

**Nguyên tắc:** kiểm soát **tập trường client được phép cung cấp** và **tập thuộc tính nghiệp vụ được phép thay đổi** là hai ranh giới riêng. Binding an toàn không thay thế authentication, authorization và kiểm tra quyền sở hữu bản ghi.

## 2. Spring MVC binding hoạt động ở đâu?

Với `@ModelAttribute`, Spring MVC tạo hoặc lấy một model object, rồi dùng `WebDataBinder` để nhận dữ liệu từ request và chuyển kiểu cho object. Nguồn input có thể gồm request parameters (query string/form data), URI variables hoặc request headers theo cơ chế `@ModelAttribute`; request parameters có mức ưu tiên thích hợp khi trùng tên.

Ví dụ:

```java
@PostMapping("/profiles")
public void update(@ModelAttribute ProfileForm form) {
    // form được Spring tạo/gắn dữ liệu trước khi method chạy.
}
```

Với JavaBean mutable, các trường phù hợp với setter công khai có thể được gán theo **property binding**. Spring cũng hỗ trợ **constructor binding**, trong đó input phù hợp với tham số constructor được dùng để tạo object. Với object có thể chứa dữ liệu đặc quyền, dùng một object rộng có nhiều setter mà không giới hạn đầu vào là rủi ro.

Sơ đồ luồng:

```text
HTTP form/query/path inputs
          |
          v
Handler mapping chọn controller method
          |
          v
@ModelAttribute tạo/lấy đối tượng
          |
          v
WebDataBinder
    ├─ constructor binding (tham số constructor)
    └─ property binding (setter/field theo cấu hình)
          |
          v
BindingResult (lỗi chuyển kiểu, validation, suppressed fields)
          |
          v
Controller kiểm tra lỗi và quyền -> cập nhật đúng thuộc tính
```

**Phân biệt ba câu hỏi:**

1. **Bind field nào?** Giải quyết bằng DTO/form chuyên dụng, `setAllowedFields`, hoặc constructor binding chủ đích.
2. **Giá trị có hợp lệ?** Giải quyết bằng validation (`@Valid`, Bean Validation, logic ứng dụng).
3. **Ai được phép thay đổi gì trên tài nguyên nào?** Giải quyết bằng authorization, kiểm tra ownership và quy tắc nghiệp vụ ở server.

Một giá trị có thể hoàn toàn **hợp lệ về kiểu** (ví dụ `admin=true`) nhưng vẫn **bị cấm cập nhật**.

## 3. Cách ưu tiên: input DTO chỉ chứa các trường được phép

Tạo object dành riêng cho thao tác chỉnh sửa, khác với entity lưu trong database:

```java
public record EditProfileRequest(String displayName, String bio) {
}
```

Hợp đồng chỉ định hai trường `displayName` và `bio`. Không có `admin`, `role` hoặc `ownerId` nên code nghiệp vụ không thể vô tình sao chép các thuộc tính ấy từ DTO.

Ví dụ cách cập nhật có chủ đích:

```java
// Phần mã minh họa nghiệp vụ, không phải chương trình độc lập.
@PostMapping("/profiles/{id}/edit")
public String edit(@PathVariable long id,
                   @ModelAttribute EditProfileRequest input,
                   Principal principal) {
    Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND));

    authorizationService.requireCanEdit(principal, account);
    account.setDisplayName(input.displayName());
    account.setBio(input.bio());
    accountRepository.save(account);
    return "saved";
}
```

Đoạn trên giả định tồn tại `Account`, `accountRepository` và `authorizationService` của ứng dụng. Điều cần nhìn là **chỉ gán hai property được cho phép** trên entity server đã xác định và đã kiểm tra quyền.

DTO là lựa chọn ưu tiên vì cấu trúc type thể hiện hợp đồng ngay trong mã nguồn. Tuy nhiên cần hiểu đúng rằng với `@ModelAttribute`, **constructor binding chỉ giới hạn những tham số được khai báo trong constructor**; nếu DTO vẫn có setter khác hoặc cấu hình cho phép property binding sau đó, hãy kiểm soát cơ chế này như ở mục 5.

**Tránh bind trực tiếp vào JPA entity** chứa các thuộc tính như `id`, `tenantId`, `owner`, `status`, `balance`, `approved`, `isAdmin`, `permissions`. Một entity phục vụ persistence thường có phạm vi rộng hơn hợp đồng API.

## 4. `@InitBinder` + `setAllowedFields`: danh sách trường được phép

Khi cần property binding vào một JavaBean mutable, hãy chủ động đăng ký **allowlist**:

```java
@InitBinder("profileForm")
void initProfileBinder(WebDataBinder binder) {
    binder.setAllowedFields("displayName", "bio");
}
```

- `@InitBinder("profileForm")`: áp dụng tùy chỉnh binder cho model attribute có tên `profileForm` trong phạm vi controller. Tên phải trùng với `@ModelAttribute("profileForm")` của method xử lý.
- `WebDataBinder`: binder do Spring MVC tạo **riêng cho request/target phù hợp**, dùng để xử lý binding và conversion.
- `setAllowedFields("displayName", "bio")`: cho phép property binding **chỉ** hai tên này. Trường khác, kể cả `admin`, không được ghi qua setter/property binding.
- Chuỗi tên trường trong allowlist **phân biệt chữ hoa chữ thường**; hãy dùng đúng tên property JavaBean. Wildcard được hỗ trợ nhưng cần dùng rất dè dặt.

Ví dụ HTTP form:

```text
displayName=Lan&bio=Java+learner&admin=true
```

Với binder đã giới hạn, `displayName` và `bio` được nhận; `admin` bị loại khỏi property binding. Nếu thuộc tính `admin` mặc định là `false`, sau binding nó vẫn là `false`.

### 4.1. `setDisallowedFields` có phải lựa chọn tốt?

```java
// Không khuyến nghị làm hàng rào bảo vệ chính.
binder.setDisallowedFields("admin", "role", "ownerId");
```

Blocklist chỉ liệt kê các trường đã biết là nguy hiểm. Một property mới như `discountLimit` hoặc `securityFlags` có thể được thêm vào model nhưng không ai cập nhật blocklist. Vì vậy **allowlist rõ ràng** an toàn và dễ review hơn. Trong các tài liệu Spring Framework mới, `setDisallowedFields` còn được xem là cách tiếp cận mong manh và đang trên lộ trình deprecated ở phiên bản tương ứng; ưu tiên `setAllowedFields`.

### 4.2. Tên property lồng nhau, collection và wildcard

Spring có thể bind vào property paths, không chỉ property đơn:

```text
contact.email
addresses[0].city
settings[theme]
```

Allowlist có thể nêu đích danh path cần thiết, ví dụ:

```java
binder.setAllowedFields(
        "displayName",
        "contact.email",
        "addresses[0].city");
```

Đây là minh họa cho form object thực sự có các property lồng nhau tương ứng. Không tự động dùng `addresses*` hoặc `*` nếu hợp đồng chỉ cho phép một trường cụ thể: wildcard mở rộng tập input, đôi khi cho phép sửa phần tử khác hoặc các property phụ. Nếu một danh sách cần chỉnh sửa linh hoạt, hãy đặt giới hạn số phần tử, chỉ số hợp lệ và kiểm tra từng thuộc tính trong DTO thay vì để client quyết định bất kỳ path nào.

**Lưu ý giới hạn:** `setAllowedFields` kiểm soát **property binding**. Nó không kiểm soát mọi cơ chế tạo object hay mọi đường ghi dữ liệu khác, đặc biệt không phải hàng rào mặc định cho JSON `@RequestBody`. Xem mục 7.

## 5. Constructor binding và `setDeclarativeBinding(true)`

Từ Spring Framework **6.1**, `WebDataBinder` hỗ trợ:

```java
@InitBinder
void initBinder(WebDataBinder binder) {
    binder.setDeclarativeBinding(true);
}
```

Khi cờ này bật:

- **Constructor binding** vẫn hoạt động, dựa trên các tham số constructor đã khai báo.
- **Property binding** chỉ xảy ra nếu binder có `allowedFields` được cấu hình.

Như vậy có thể thiết kế record/immutable DTO có constructor chỉ nhận trường hợp đồng và không cho setter binding ngoài ý muốn. Khi constructor nhận `admin` hoặc `ownerId` thì giá trị ấy **vẫn có thể được bind qua constructor**; cờ không thể biến constructor thiếu an toàn thành an toàn. Chỉ khai báo các thành phần thực sự chấp nhận từ client.

Ví dụ dùng immutable input:

```java
// Đoạn code minh họa khi dùng Spring Framework 6.1+.
public record UpdateContact(String displayName, String phone) {
}

@ControllerAdvice
class BindingPolicy {

    @InitBinder
    void bindOnlyDeclaredFields(WebDataBinder binder) {
        binder.setDeclarativeBinding(true);
    }
}
```

Đây là lựa chọn cấu hình rộng cho các controller chịu ảnh hưởng của `@ControllerAdvice`; không áp dụng bất ngờ lên cả ứng dụng mà chưa đánh giá những form trước đây phụ thuộc setter binding. Với các form JavaBean mutable cần property binding, có thể cấu hình `allowedFields` tại controller/attribute liên quan. Có thể giới hạn phạm vi `@ControllerAdvice` theo package hoặc controller type.

So sánh:

| Cách triển khai | Đặc điểm | Đề xuất |
| --- | --- | --- |
| Entity mutable + bind trực tiếp | Rộng hơn hợp đồng, dễ lộ setter nhạy cảm | Tránh |
| DTO mutable + `@InitBinder` allowlist | Kiểm soát property binding theo trường | Tốt nếu cần JavaBean/form |
| Record/immutable DTO chỉ chứa trường hợp đồng | Bề mặt input gọn và dễ review | Ưu tiên khi phù hợp |
| Declarative binding + DTO an toàn | Chỉ constructor binding; setter binding chỉ khi được cho phép | Phù hợp Spring 6.1+ |
| Blocklist `disallowedFields` | Dễ bỏ sót trường mới | Không dùng làm biện pháp chính |

## 6. Ví dụ Spring Boot 3.x có thể chạy: chặn `admin=true` từ HTML form

Ví dụ này **cố tình** đặt thuộc tính `admin` trong form bean để minh họa rủi ro và khả năng chặn của allowlist. Trong code production nên **loại hẳn property nhạy cảm khỏi input DTO**, theo mục 3.

### 6.1. Chuẩn bị dự án

Tạo Spring Boot 3.x project với Java 17+ và dependency **Spring Web** trên [Spring Initializr](https://start.spring.io/). Trong file `src/main/java/com/example/demo/DemoApplication.java`, đặt toàn bộ mã sau (một public class, hai package-private classes; không cần database hay Spring Security để chạy minh họa):

```java
package com.example.demo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@RestController
class ProfileController {

    @InitBinder("profileForm")
    void initProfileFormBinder(WebDataBinder binder) {
        binder.setAllowedFields("displayName", "bio");
    }

    @PostMapping(
            path = "/profiles/{id}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable("id") long id,
            @ModelAttribute("profileForm") ProfileForm form,
            BindingResult bindingResult) {

        // BindingResult phải đặt ngay sau @ModelAttribute tương ứng.
        String[] suppressed = bindingResult.getSuppressedFields();
        if (suppressed.length > 0) {
            Map<String, Object> rejected = new LinkedHashMap<>();
            rejected.put("error", "Fields not allowed");
            rejected.put("fields", Arrays.asList(suppressed));
            return ResponseEntity.badRequest().body(rejected);
        }

        if (bindingResult.hasErrors()) {
            Map<String, Object> rejected = new LinkedHashMap<>();
            rejected.put("error", "Invalid form input");
            return ResponseEntity.badRequest().body(rejected);
        }

        // Demo chỉ phản hồi object đã bind; không ghi database.
        Map<String, Object> accepted = new LinkedHashMap<>();
        accepted.put("id", id);
        accepted.put("displayName", form.getDisplayName());
        accepted.put("bio", form.getBio());
        accepted.put("admin", form.isAdmin());
        return ResponseEntity.ok(accepted);
    }
}

class ProfileForm {
    private String displayName = "";
    private String bio = "";
    private boolean admin;

    public ProfileForm() {
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }
}
```

**Phân tích mã:**

1. `@ModelAttribute("profileForm")` tạo JavaBean `ProfileForm` bằng constructor không tham số và thực hiện property binding.
2. `@InitBinder("profileForm")` cấu hình binder cho đúng model attribute. Chỉ `displayName`, `bio` được phép gán, kể cả `ProfileForm` có `setAdmin(...)` công khai.
3. `getSuppressedFields()` trả các property path đã bị binder loại khỏi property binding. Demo chọn **từ chối request với HTTP 400** nếu có trường không được phép; đây là quyết định chính sách ứng dụng. **Spring không mặc định tự biến mỗi suppressed field thành HTTP 400**.
4. `bindingResult.hasErrors()` xử lý lỗi binding/conversion thông thường; không đồng nghĩa với `getSuppressedFields().length > 0`. Trong ví dụ này các trường hợp lệ đều có kiểu `String`.
5. Method **không lưu dữ liệu**. Output cho thấy `admin` giữ `false` và chỉ khi request không chứa trường ngoài hợp đồng thì được chấp nhận.

Với hệ thống thật, sau khi qua bước kiểm tra bạn còn phải xác định người dùng hiện tại, tải bản ghi server bằng ID, kiểm tra quyền và chỉ cập nhật những thuộc tính cho phép. Không dùng `form.isAdmin()` để quyết định quyền hay trạng thái admin, dù demo cho thấy nó bị chặn.

### 6.2. Chạy và kiểm tra HTTP output

Trong thư mục gốc của project Spring Initializr:

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows PowerShell hoặc Command Prompt
mvnw.cmd spring-boot:run
```

Request hợp lệ:

```bash
curl -sS -X POST 'http://localhost:8080/profiles/42' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'displayName=Lan' \
  --data-urlencode 'bio=Java learner'
```

Với cấu hình mặc định và không có filter làm thay đổi response, status **200 OK**, body JSON:

```json
{"id":42,"displayName":"Lan","bio":"Java learner","admin":false}
```

Request tấn công (có field ngoài hợp đồng):

```bash
curl -i -X POST 'http://localhost:8080/profiles/42' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'displayName=Lan' \
  --data-urlencode 'bio=Java learner' \
  --data-urlencode 'admin=true'
```

Status dự kiến **400 Bad Request** do nhánh kiểm tra suppressed fields **do chúng ta viết**, body:

```json
{"error":"Fields not allowed","fields":["admin"]}
```

Các header như `Date` hoặc `Connection` phụ thuộc servlet container. Nếu chạy bằng PowerShell, đặt lệnh cURL trên một dòng và dùng `curl.exe` thay cho alias có thể tồn tại:

```powershell
curl.exe -i -X POST "http://localhost:8080/profiles/42" -H "Content-Type: application/x-www-form-urlencoded" --data-urlencode "displayName=Lan" --data-urlencode "bio=Java learner" --data-urlencode "admin=true"
```

Kết quả cho thấy server **phát hiện** input bị cấm, không chỉ âm thầm bỏ qua. Nếu bỏ nhánh kiểm tra suppressed fields, bản thân `setAllowedFields` vẫn có thể ngăn `admin` được gán; status có thể vẫn là 200, tùy code xử lý.

### 6.3. Vì sao `BindingResult` cần đứng ngay sau form?

```java
public ResponseEntity<?> update(
        @ModelAttribute("profileForm") ProfileForm form,
        BindingResult errors,
        @PathVariable long id) { /* ... */ }
```

Đây là **chữ ký minh họa**, cho thấy `BindingResult` đứng ngay sau object nó theo dõi. Nếu chèn tham số khác giữa `@ModelAttribute` và `BindingResult`, Spring không thể liên kết lỗi binding với object theo hợp đồng giải quyết đối số. Có thể đặt các tham số độc lập trước hoặc sau cặp này.

## 7. Phân biệt form `@ModelAttribute` và JSON `@RequestBody`

Đây là điểm bảo mật rất dễ nhầm:

| Đường vào | Cơ chế chuyển dữ liệu | `@InitBinder#setAllowedFields` kiểm soát trực tiếp? |
| --- | --- | --- |
| Form/query được bind vào `@ModelAttribute` mutable bean | `WebDataBinder` / property binding | **Có**, đối với các property được bind qua cơ chế đó |
| `@ModelAttribute` immutable record/constructor | Constructor binding | **Không** giới hạn constructor parameters bằng `allowedFields`; phải thiết kế constructor an toàn |
| JSON `@RequestBody` | `HttpMessageConverter`, thường Jackson deserialization | **Không** dùng `allowedFields` làm danh sách khóa JSON được phép |
| Cập nhật JPA entity đã lấy trong service | Code nghiệp vụ / persistence | Không; phải gán field có chủ đích và kiểm tra quyền |

Ví dụ JSON nguy hiểm:

```java
// KHÔNG NÊN: không deserialize JSON trực tiếp vào entity rộng.
@PatchMapping("/accounts/{id}")
public void patch(@PathVariable long id,
                  @RequestBody Account entity) {
    accountRepository.save(entity);
}
```

Jackson có thể điền những property nhận diện được trên `Account` theo cấu hình của nó. Một `@InitBinder#setAllowedFields` dành cho `@ModelAttribute` **không tự biến thành allowlist cho quá trình JSON deserialize này**.

Cách sửa cho endpoint JSON:

```java
// Đoạn minh họa: service dùng entity từ database và kiểm tra quyền.
public record EditAccountJson(String displayName, String bio) {
}

@PatchMapping("/accounts/{id}")
public ResponseEntity<Void> patch(
        @PathVariable long id,
        @RequestBody EditAccountJson input,
        Principal currentUser) {

    Account saved = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND));

    authorizationService.requireCanEdit(currentUser, saved);
    saved.setDisplayName(input.displayName());
    saved.setBio(input.bio());
    accountRepository.save(saved);
    return ResponseEntity.noContent().build();
}
```

Đoạn code trên giả định có `Account`, repository và service bảo mật tương ứng; nó **không chạy độc lập** nếu thiếu các thành phần đó. Ưu điểm là JSON DTO chỉ có hai trường, và service cập nhật chính xác hai property. Nếu yêu cầu **từ chối** mọi JSON key lạ (ví dụ `admin`), cần cấu hình chính sách unknown properties của Jackson phù hợp với DTO và viết bài kiểm thử riêng; một số cấu hình mặc định có thể bỏ qua unknown keys. Bỏ qua key lạ và trả 2xx **khác** với chủ động từ chối request.

Với API dùng JSON, `@JsonIgnore`, `@JsonProperty(access = READ_ONLY)` hoặc Jackson views có thể là lớp bổ sung trong một số trường hợp, nhưng không nên thay cho thiết kế request DTO và kiểm soát quyền. Các field đặc quyền không nên xuất hiện trong request DTO chỉ để rồi phải nhớ chặn.

## 8. Không cho bind vào entity đã tồn tại: `@ModelAttribute(binding = false)`

Một pattern khác là để Spring cung cấp object đã có trong model, nhưng **không áp request parameters lên nó**:

```java
// Mã minh họa, yêu cầu repository và service do ứng dụng định nghĩa.
@ModelAttribute("account")
public Account loadAccount(@PathVariable long id) {
    return accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND));
}

@PostMapping("/accounts/{id}/edit")
public String update(
        @ModelAttribute("editForm") EditProfileRequest form,
        @ModelAttribute(value = "account", binding = false)
        Account account,
        Principal principal) {

    authorizationService.requireCanEdit(principal, account);
    account.setDisplayName(form.displayName());
    account.setBio(form.bio());
    accountRepository.save(account);
    return "saved";
}
```

`binding = false` nói Spring không dùng request values để data-bind **model attribute `account` trong method argument này**. Controller vẫn phải kiểm tra quyền và sao chép field đúng hợp đồng từ `editForm`. Chú ý một attribute khác hoặc code ngoài controller vẫn có thể tác động vào entity; đây là biện pháp ở ranh giới handler argument, không phải cơ chế khóa object tuyệt đối.

Khi method có nhiều `@ModelAttribute`, đặt tên rõ ràng giúp tách binder cho input form và binder cho domain entity. `@InitBinder("editForm")` nên được giới hạn theo đúng input nếu bạn cần allowlist khác.

## 9. Khi nào cấu hình ở controller, khi nào cấu hình chung?

### 9.1. Phạm vi controller và attribute

```java
@Controller
class SettingsController {
    @InitBinder("settingsForm")
    void allowSettings(WebDataBinder binder) {
        binder.setAllowedFields("theme", "notificationsEnabled");
    }
}
```

Áp dụng cho `settingsForm` trong controller. Tên thuộc tính hợp đồng khác nhau giữa các form thường nên có allowlist khác nhau. Không cấu hình cùng một danh sách field cho mọi domain object chỉ vì chúng đều đi qua MVC.

### 9.2. `@ControllerAdvice`

```java
@ControllerAdvice(basePackages = "com.example.web")
class WebBindingPolicy {
    @InitBinder
    void configure(WebDataBinder binder) {
        binder.setDeclarativeBinding(true);
    }
}
```

Đây là lựa chọn chính sách cho phạm vi package đã xác định, áp dụng tốt khi ứng dụng thiết kế các DTO theo constructor binding hoặc đã khai báo allowlist riêng nơi cần property binding. **Kiểm thử các form cũ** trước khi bật cho toàn bộ controller: form trông như bind đúng trước đây có thể không còn được gán setter khi bật declarative binding mà không có `allowedFields`.

Nếu nhiều `@InitBinder` cùng tác động lên một target, kiểm tra cấu hình thực tế của binder và tránh cho các binder ghi đè allowlist bằng thứ tự hoặc chính sách không rõ ràng. Đặt tên form và bảo vệ phạm vi rõ ràng giúp review đơn giản hơn.

## 10. Allowlist là một lớp bảo vệ; quy tắc nghiệp vụ vẫn cần thiết

Những giá trị không được phép sửa thường nằm **ngoài hợp đồng input**, ví dụ:

| Thuộc tính | Vì sao không cho client bind tùy ý? | Xử lý an toàn |
| --- | --- | --- |
| `id` | Chuyển sang tài nguyên khác | ID từ route, kiểm tra quyền và load bản ghi server |
| `ownerId`, `tenantId` | Chiếm quyền sở hữu/cross-tenant | Xác định từ identity và ngữ cảnh tổ chức đáng tin |
| `role`, `isAdmin` | Leo thang đặc quyền | Quản trị riêng, authorization nghiêm ngặt |
| `price`, `balance` | Thay đổi giá trị tài chính | Service nghiệp vụ được bảo vệ, tính toán phía server |
| `status`, `approved` | Bỏ qua quy trình phê duyệt | State transition do service kiểm soát |
| `createdAt`, `createdBy` | Sửa audit trail | Ghi từ server/persistence |
| `version` | Bỏ qua optimistic locking hoặc nhầm trạng thái | Cơ chế concurrency có kiểm soát |

Ngay cả field nằm trong allowlist vẫn có thể yêu cầu điều kiện nghiệp vụ. Ví dụ `displayName` cần độ dài hợp lệ, `email` có thể cần xác minh, trạng thái `published` có thể yêu cầu quyền riêng.

Ở tầng service nên thực hiện theo thứ tự: xác định current principal → truy vấn entity theo scope được phép → kiểm tra quyền thao tác → xác thực giá trị → gán các field được phép → lưu trong transaction khi cần. Tránh tin bất kỳ `ownerId`/`role` client gửi dù ở URL, query, form hay JSON.

## 11. Validation, suppressed fields và unknown fields khác nhau

- `@Valid` / `@Validated`: kiểm tra ràng buộc giá trị trên object (ví dụ `@NotBlank`, `@Size`); **không phải allowlist**. Nếu muốn dùng Bean Validation với Spring Boot, thêm dependency `spring-boot-starter-validation`.
- `BindingResult.hasErrors()`: phản ánh lỗi binding hoặc validation đã thực hiện. Không nên coi nó là phép kiểm tra mọi trường bị chặn.
- `BindingResult.getSuppressedFields()`: danh sách field/property path bị loại qua binder vì không được phép bind. Có thể chọn policy ghi log có kiểm soát, trả 400, hoặc chỉ bỏ qua; quyết định phụ thuộc hợp đồng API.
- `Unknown field`: tên không có property tương ứng, có thể được binder bỏ qua tùy cấu hình. Đừng giả định mọi tham số lạ đều có mặt trong `getSuppressedFields()` hay luôn gây lỗi.
- `setIgnoreUnknownFields(false)` chỉ điều chỉnh cách binder xử lý property không tồn tại trong những thao tác áp dụng, không phải danh sách trường nhạy cảm hay phương án thay thế allowlist.

Nếu cần hợp đồng **strict** (từ chối mọi tham số thừa) thì ngoài allowlist có thể cần đối chiếu tập key request cho phép và key thực tế, xử lý cả nested path, multi-value params và trường do framework gửi (ví dụ token CSRF). Đừng tự tạo kiểm tra chuỗi đơn giản rồi kết luận đã bảo vệ được toàn bộ đường binding; xây dựng test HTTP cho input hợp lệ, bị cấm và bất thường.

## 12. Checklist code review và các bài kiểm thử có giá trị

Khi review một endpoint update có `@ModelAttribute` hoặc `@RequestBody`, trả lời:

1. Object nhận input là **request DTO** hay entity/domain object?
2. Những trường nào thuộc hợp đồng update? Có `role`, `admin`, `ownerId`, `status` hoặc JPA relationship có thể chỉnh sửa ngoài ý muốn không?
3. Với mutable `@ModelAttribute`, có `setAllowedFields` chính xác không? `@InitBinder("...")` có trùng tên attribute không?
4. Có constructor binding không? Constructor có vô tình khai báo trường nhạy cảm? Có cần `setDeclarativeBinding(true)`?
5. Nếu dùng JSON `@RequestBody`, có DTO và chính sách xử lý unknown JSON keys phù hợp không?
6. Khi binder bỏ field, handler có kiểm tra `getSuppressedFields()` và đưa ra quyết định chính sách rõ ràng không?
7. Authorization có xác minh **user + target resource + action** trước khi sửa và lưu không?
8. Có kiểm tra các property lồng nhau/collection/indexed path, giá trị rỗng, kiểu lỗi và trường thừa không?

Bộ kiểm thử nên gồm cả request bình thường và tấn công:

| Input | Kỳ vọng |
| --- | --- |
| `displayName=Lan&bio=Java+learner` | Chấp nhận, chỉ cập nhật hai thuộc tính |
| `displayName=Lan&admin=true` | Bị từ chối hoặc admin không thay đổi, đúng policy |
| `ownerId=7` | Không chuyển quyền sở hữu |
| `role=ADMIN` | Không thay đổi vai trò |
| `addresses[0].isPrimary=true` ngoài allowlist | Không bind property lồng nhau ngoài hợp đồng |
| JSON `{"displayName":"Lan","admin":true}` | JSON DTO không cho gán admin; xử lý key thừa theo policy |
| User A sửa `/profiles/{id}` của User B | Bị từ chối ở tầng authorization, dù body chỉ có allowed fields |

Trong ứng dụng thực, dùng `MockMvc` hoặc HTTP integration test để xác minh cả status, response và **trạng thái database sau request**. Chỉ kiểm tra output JSON mà không kiểm tra record được lưu có thể bỏ sót mass assignment ở service.

## 13. Những lỗi dễ gặp

| Lỗi | Hậu quả | Cách phòng tránh |
| --- | --- | --- |
| Bind trực tiếp vào JPA entity rộng | Client có thể gán thuộc tính nhạy cảm | DTO riêng và explicit update |
| Chỉ ẩn field trong HTML | Client vẫn tự gửi field | Allowlist phía server |
| Chỉ thêm `@Valid` | Giá trị bị cấm vẫn có thể hợp lệ | Validation **kèm** giới hạn binding |
| Dùng `setDisallowedFields` làm lớp chính | Bỏ sót property mới | `setAllowedFields` hoặc constructor DTO |
| Cho phép wildcard quá rộng | Vô tình mở đường cho nested properties | Liệt kê field/path cụ thể |
| Nhầm `setAllowedFields` bảo vệ JSON | Jackson vẫn deserialize object theo cấu hình | JSON request DTO và mapping rõ ràng |
| Quên xem `getSuppressedFields()` | Client gửi field cấm mà không bị phát hiện | Xác định policy cho suppressed field |
| `@InitBinder("name")` khác `@ModelAttribute("name")` | Allowlist không áp đúng target | Đồng nhất tên, kiểm thử HTTP |
| Cho phép bind `id`/`tenantId` từ body | Cross-record/cross-tenant update | Xác định resource ở server, kiểm tra scope |
| Bật `declarativeBinding` toàn cục không kiểm thử | Form cũ ngừng bind property | Rà soát DTO/form và config theo phạm vi |

## 14. Tự kiểm tra

**Câu 1.** Form HTML chỉ có hai input `displayName` và `bio`. Có cần bảo vệ trước `admin=true` không?

**Đáp án:** Có. Client không buộc phải dùng HTML form của ứng dụng; họ có thể tự gửi bất kỳ tham số HTTP nào.

**Câu 2.** `binder.setAllowedFields("displayName", "bio")` có tự trả 400 nếu gửi `admin=true`?

**Đáp án:** Không mặc định. Nó ngăn property binding ngoài allowlist. Ứng dụng có thể đọc `BindingResult#getSuppressedFields()` và chủ động quyết định từ chối, như ví dụ ở mục 6.

**Câu 3.** Có thể chỉ dùng `@InitBinder` để ngăn JSON body gán `role` lên entity của `@RequestBody` không?

**Đáp án:** Không. `@RequestBody` dùng `HttpMessageConverter` (thường là Jackson) để deserialize; hãy nhận JSON bằng request DTO có phạm vi hẹp và chỉ cập nhật field được phép.

**Câu 4.** `setDeclarativeBinding(true)` có chặn mọi input trong constructor không?

**Đáp án:** Không. Constructor binding vẫn hoạt động. Constructor/record phải chỉ chứa trường mà client được phép gửi.

**Câu 5.** Vì sao đã có allowlist `displayName`, `bio` vẫn cần kiểm tra quyền người dùng với ID trên URL?

**Đáp án:** Vì allowlist chỉ quyết định **field** nào được bind, không quyết định **ai** được sửa **bản ghi** nào.

## 15. Tài liệu chính thức

- [Spring Framework — MVC Data Binding](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-data-binding.html): hướng dẫn model design, allowlist, constructor binding, declarative binding.
- [Spring Framework — `@InitBinder`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-initbinder.html): phạm vi cấu hình binder ở controller và `@ControllerAdvice`.
- [Spring Framework — `@ModelAttribute`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html): cách tạo model attribute, nhận request values và tùy chọn `binding=false`.
- [Spring Framework — `@RequestBody`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html): chuyển JSON/body bằng `HttpMessageConverter` và validation.
- [Spring Framework — MVC Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html): vị trí `BindingResult` và các loại tham số controller.

**Kết luận áp dụng:** Định nghĩa request DTO tối thiểu theo từng thao tác cập nhật, ưu tiên constructor/immutable DTO khi phù hợp, giới hạn property binding qua `@InitBinder#setAllowedFields` khi dùng mutable form, và kiểm tra `getSuppressedFields()` nếu chính sách yêu cầu từ chối trường thừa. Với JSON `@RequestBody`, áp dụng chính sách deserialize riêng. Mọi thay đổi entity phải đi qua kiểm tra quyền và mapping thuộc tính do server kiểm soát.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-validation-006">Bean Validation với @Valid, @Validated và nhóm ràng buộc</a>

<details>
<summary>Xem chi tiết</summary>

# Bean Validation với @Valid, @Validated và nhóm ràng buộc trong Spring MVC

## 1. Mục tiêu và phạm vi

Ứng dụng web phải xác minh dữ liệu đầu vào trước khi dùng cho nghiệp vụ. Spring MVC hỗ trợ tích hợp **Jakarta Bean Validation** để khai báo các quy tắc ngay trên đối tượng và kích hoạt kiểm tra khi bind dữ liệu vào tham số controller.

Sau bài này, bạn có thể:

1. Phân biệt **binding**, **validation** và **business validation**.
2. Biết vai trò của `@Valid`, `@Validated`, các annotation ràng buộc như `@NotBlank`, `@NotNull`, `@Size`.
3. Tạo nhóm `Create`, `Update`, chọn nhóm chính xác cho từng endpoint và hiểu vai trò nhóm `Default`.
4. Kiểm tra đối tượng lồng nhau bằng cascade validation và tránh bỏ sót group.
5. Đọc lỗi bằng `BindingResult`, phân biệt `MethodArgumentNotValidException` với `HandlerMethodValidationException` trong Spring MVC 6.1+.
6. Viết integration test với `MockMvc` cho cả dữ liệu hợp lệ và không hợp lệ.

Phạm vi là **Spring MVC trên Servlet stack**, sử dụng Java 17+, Spring Boot 3.x và namespace `jakarta.validation`. Spring WebFlux, validator custom ngoài Bean Validation và nghiệp vụ dữ liệu có thể có quy trình bổ sung; không suy ra cùng hành vi exception cho mọi framework hay mọi phiên bản.

## 2. Spring MVC kiểm tra dữ liệu ở bước nào?

Ví dụ một client gửi JSON:

```http
POST /api/users HTTP/1.1
Content-Type: application/json

{"name":"","email":"khong-phai-email","password":"abc"}
```

Luồng xử lý rút gọn:

```text
HTTP request
     |
DispatcherServlet -> chọn controller/handler
     |
@RequestBody -> HttpMessageConverter (ví dụ Jackson)
     |          chuyển JSON thành đối tượng Java
     v
WebDataBinder / Validator
     |          áp dụng @Valid hoặc @Validated và constraints
     +---- có lỗi -> xử lý validation errors (thường HTTP 400)
     |
     v
Controller method -> xử lý nghiệp vụ -> HTTP response
```

Các khái niệm khác nhau:

| Khâu | Nhiệm vụ | Ví dụ |
| --- | --- | --- |
| **Binding / conversion** | Đọc body/form/query rồi chuyển kiểu, gán vào Java object | JSON string sang `UserPayload`; `"7"` sang `long` |
| **Bean Validation** | Kiểm tra các constraint đã khai báo | `@NotBlank`, `@Email`, `@Size(min=8)` |
| **Business validation** | Kiểm tra quy tắc phụ thuộc trạng thái hệ thống và transaction | Email đã tồn tại trong DB, user có quyền thay đổi bản ghi không |
| **Persistence constraints** | Ràng buộc của cơ sở dữ liệu, bảo vệ tính nhất quán cuối cùng | `UNIQUE(email)`, `NOT NULL`, foreign key |

**Bean Validation không thay thế ràng buộc DB hoặc authorization**. Trên hai request đồng thời, cả hai có thể vượt qua kiểm tra “email chưa tồn tại”, nên database vẫn cần unique constraint và ứng dụng cần xử lý lỗi tranh chấp.

### Các thành phần liên quan

- `jakarta.validation` định nghĩa annotation, `Validator`, `ConstraintViolation`, group và contract chuẩn.
- **Hibernate Validator** là một implementation phổ biến của Jakarta Bean Validation, thường được Spring Boot chọn qua `spring-boot-starter-validation`. Nó không đồng nghĩa với Hibernate ORM/JPA.
- `org.springframework.validation.Validator` là interface validation của Spring. `LocalValidatorFactoryBean` làm cầu nối Jakarta Bean Validation với Spring `SmartValidator` và `Errors`.
- `@Valid` được định nghĩa bởi **Jakarta Validation**: `jakarta.validation.Valid`.
- `@Validated` được định nghĩa bởi **Spring Framework**: `org.springframework.validation.annotation.Validated`.

Nếu dự án chỉ thêm API annotation mà **thiếu implementation Validator**, việc khai báo constraint không đủ để bảo đảm chúng thực sự được kiểm tra. Trong Spring Boot 3.x, khai báo starter validation tường minh.

## 3. Các constraint cơ bản và ý nghĩa thực tế

| Constraint | Mục đích | Điều cần nhớ |
| --- | --- | --- |
| `@NotNull` | Giá trị khác `null` | Chuỗi `""` và `"   "` vẫn hợp lệ |
| `@NotEmpty` | Không `null` và không rỗng: chuỗi/collection/map/array phù hợp | Chuỗi chỉ chứa khoảng trắng vẫn có thể hợp lệ |
| `@NotBlank` | Chuỗi khác `null` và chứa ít nhất một ký tự không phải whitespace | Dùng cho trường text bắt buộc |
| `@Size(min=…, max=…)` | Độ dài chuỗi hoặc kích thước collection/map/array | `null` thường được xem là hợp lệ; kết hợp `@NotNull`/`@NotBlank` khi bắt buộc |
| `@Email` | Kiểm tra cú pháp email theo validator | Không chứng minh email tồn tại hoặc thuộc về người gửi; thường kết hợp `@NotBlank` |
| `@Min` / `@Max` | Giới hạn giá trị số nguyên/ngữ nghĩa số | Với số thập phân cần xem `@DecimalMin`/`@DecimalMax` |
| `@Positive` / `@PositiveOrZero` | Số dương / không âm | Không buộc khác `null` |
| `@Pattern(regexp="…")` | Kiểm tra regex trên chuỗi | Chỉ phù hợp một định dạng có ranh giới rõ; tránh regex phức tạp dễ sai |
| `@Past` / `@Future` | Ngày/giờ so với hiện tại | Liên quan clock/time zone, cần test với clock phù hợp |
| `@Null` | Yêu cầu giá trị là `null` | Hữu ích cho ID chỉ do server sinh ở request tạo mới |
| `@AssertTrue` | Giá trị Boolean phải đúng | Không thay thế rule nghiệp vụ phụ thuộc database |

Ví dụ:

```java
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

record ProductRequest(
        @NotBlank String title,
        @Positive long stockQuantity,
        @NotBlank @Email String contactEmail,
        @Size(max = 200) String description) {
}
```

Đây là **DTO mẫu**, chưa có HTTP endpoint. Có hai điểm cần thận trọng:

- `@Positive long stockQuantity` chặn `0`, trong khi nhiều nghiệp vụ cho phép tồn kho `0`; khi đó hãy dùng `@PositiveOrZero`.
- `@Size(max=200)` không làm `description` bắt buộc. Nếu được phép bỏ trống, `null` là giá trị hợp lệ; nếu bắt buộc, bổ sung constraint riêng.

Các annotation chỉ có hiệu lực ở **điểm kích hoạt validation**: Spring MVC không tự kiểm tra mọi object vừa được tạo bằng `new`, gán bằng setter hay tải từ DB.

## 4. `@Valid` và `@Validated` khác nhau thế nào?

### `@Valid` — đánh dấu cần kiểm tra đối tượng và cascade

`@Valid` là annotation của Jakarta Validation. Trên DTO tham số MVC, nó thông báo Spring kiểm tra object khi thích hợp. Mặc định dùng **nhóm `Default`**. Đặt `@Valid` trên tham chiếu object lồng nhau giúp validation đi tiếp xuống object đó (cascade).

```java
@PostMapping("/users")
public void create(@Valid @RequestBody UserPayload body) {
    // Chỉ vào được method khi validation/binding thành công
    // hoặc khi có BindingResult xử lý lỗi theo cách chủ động.
}
```

Đoạn method là fragment minh họa. Nếu chỉ dùng `@Valid` như trên, constraint khai báo **riêng `groups=Create.class`** sẽ không tự được chọn. Và `@Valid` **không chứa tham số `groups`**: cú pháp `@Valid(Create.class)` không hợp lệ.

`@Valid` bản thân **không phải constraint yêu cầu object khác null**. Để object lồng nhau bắt buộc hiện diện, dùng đồng thời `@NotNull` và `@Valid`.

### `@Validated` — Spring chọn nhóm validation

`@Validated` của Spring mở rộng cơ chế tích hợp bằng cách nhận một hoặc nhiều nhóm:

```java
@PostMapping("/users")
public void create(@Validated(Create.class) @RequestBody UserPayload body) {
    // Kiểm tra các constraint thuộc nhóm Create.
}
```

Nếu chọn `Create.class` riêng, **không mặc nhiên kiểm tra các constraint chỉ thuộc `Default`**. Muốn chạy cả quy tắc chung và quy tắc tạo mới, khai báo:

```java
@Validated({Default.class, Create.class})
```

`Default` ở đây là `jakarta.validation.groups.Default`. Việc chọn nhiều group **không bảo đảm thứ tự đánh giá**; muốn có thứ tự thì dùng `@GroupSequence`.

### Bảng so sánh nhanh

| Khía cạnh | `@Valid` | `@Validated` |
| --- | --- | --- |
| Package | `jakarta.validation` | `org.springframework.validation.annotation` |
| Mục đích phổ biến trên tham số MVC | Kích hoạt validate DTO theo Default; cascade ở thuộc tính lồng nhau | Kích hoạt validate DTO với group được chọn |
| Chọn `Create`, `Update` ngay trên annotation | Không | Có, ví dụ `@Validated(Update.class)` |
| Cascade sang object lồng nhau khi đặt trên field | **Có** | **Không thay thế `@Valid` trên field** |
| Đặt ở class level để kích hoạt AOP method validation cho Spring bean | Không theo nghĩa marker AOP của Spring | Có thể, với cơ chế `MethodValidationPostProcessor` |
| Trên controller Framework 6.1+ | Vẫn dùng cho đối tượng | Dùng ở **tham số để chọn group**; tránh class-level nếu muốn Spring MVC native method validation |

Với DTO HTTP input, bạn thường **chọn một trong hai** annotation trên tham số. Không cần đặt cả `@Valid` và `@Validated` trên cùng một tham số chỉ để chọn group; nested DTO vẫn cần `@Valid` ở vị trí cascade.

## 5. Nhóm ràng buộc (*validation groups*)

Validation group là interface marker để gắn từng constraint vào kịch bản. Một DTO có thể phục vụ nhiều hành động với quy tắc khác nhau.

Ví dụ:

- **Tạo mới**: body chưa được chứa ID; password bắt buộc và đủ độ dài.
- **Cập nhật**: body phải có ID hợp lệ; password không bắt buộc; tên/email vẫn được kiểm tra.
- **Quy tắc chung**: tên không trống, email đúng cú pháp, địa chỉ lồng nhau hợp lệ.

Khai báo nhóm:

```java
public interface Create {}
public interface Update {}
```

Dạng trên là **hai khai báo interface minh họa**, mỗi public interface phải được đặt trong tệp Java riêng nếu viết nguyên dạng. Trong ví dụ ứng dụng đầy đủ ở phần sau, chúng là interface package-private được đặt chung một tệp.

Gắn group vào constraint:

```java
record UserPayload(
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        Long id,

        @NotBlank String name,

        @NotBlank @Email String email,

        @NotBlank(groups = Create.class)
        @Size(min = 8, groups = Create.class)
        String password) {
}
```

Khi constraint không có `groups` hoặc `groups={}`, nó thuộc **`Default`**. Trong đoạn này:

| Constraint | Nhóm |
| --- | --- |
| `id` phải `null` | `Create` |
| `id` không được `null` | `Update` |
| `name` không trống | `Default` |
| `email` không trống, đúng cú pháp | `Default` |
| `password` không trống, ít nhất 8 ký tự | `Create` |

Kết quả:

| Nhóm được kích hoạt | Kiểm tra được chạy |
| --- | --- |
| Chỉ `Default`, ví dụ `@Valid` | Tên và email |
| Chỉ `Create` | ID phải null; password bắt buộc/đủ dài |
| Chỉ `Update` | ID không được null |
| `Default` + `Create` | Tên/email + ID phải null + password |
| `Default` + `Update` | Tên/email + ID bắt buộc |

Cảnh báo: `@Validated(Create.class)` **không tự chạy Default**. Đây là lỗi thiết kế phổ biến khi “thêm group” rồi vô tình vô hiệu hóa ràng buộc chung.

Nếu `@Validated` nhận group interface **kế thừa `Default`**, quy tắc từ group cha có thể được bao gồm theo semantics của Bean Validation. Tuy nhiên, chọn `{Default.class, Create.class}` là cách đọc rõ nhất khi cần cả hai nhóm.

### Group là lựa chọn quy tắc, không phải nhóm field cho JSON serializer

Bean Validation group **không mặc định quyết định field nào được Jackson nhận, trả hoặc cho phép cập nhật**. Các field không gửi vẫn có thể được deserialize thành `null` rồi được kiểm tra theo group. Không sử dụng group validation như cơ chế “ẩn trường nhạy cảm” khỏi response; cần DTO/view/security policy riêng.

## 6. Ứng dụng hoàn chỉnh: tạo và sửa người dùng

Ví dụ sau dùng **Spring Boot 3.2+ với Spring Framework 6.1+**, Java 17+, Spring MVC Servlet, Jakarta Validation. Chọn baseline này để minh họa đồng thời validation DTO và **native method validation** của Spring MVC 6.1+. Nó không giả định bạn có sẵn DB hay repository.

### Dependencies

Với dự án Gradle đã cấu hình Spring Boot plugin, repository và test runtime, thêm:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là mảnh `build.gradle`, không phải file build độc lập. Ở Maven, hai dependency runtime tương ứng cũng là `spring-boot-starter-web` và `spring-boot-starter-validation`, cộng starter test với scope `test`.

### Mã nguồn

Tạo file **`src/main/java/demo/validation/ValidationApplication.java`**:

```java
package demo.validation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class ValidationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ValidationApplication.class, args);
    }
}

interface Create {}
interface Update {}

record Address(@NotBlank String city) {}

record UserPayload(
        @Null(groups = Create.class)
        @NotNull(groups = Update.class)
        @Positive(groups = Update.class)
        Long id,

        @NotBlank String name,

        @NotBlank @Email String email,

        @NotBlank(groups = Create.class)
        @Size(min = 8, groups = Create.class)
        String password,

        @Valid @NotNull Address address) {
}

@RestController
@RequestMapping("/api/users")
class UserController {

    @PostMapping
    ResponseEntity<Map<String, String>> create(
            @Validated({Default.class, Create.class})
            @RequestBody UserPayload body) {

        // Minh họa validation; ứng dụng thực cần ghi DB và hash password.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("name", body.name()));
    }

    @PutMapping("/{id}")
    Map<String, Object> update(
            @PathVariable long id,
            @Validated({Default.class, Update.class})
            @RequestBody UserPayload body) {

        // Validation cấu trúc không tự đảm bảo path ID == body ID.
        if (!Long.valueOf(id).equals(body.id())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "path id differs from body id");
        }

        return Map.of("id", id, "name", body.name());
    }

    @GetMapping("/page")
    Map<String, Integer> page(@RequestParam @Min(1) int number) {
        return Map.of("number", number);
    }
}
```

Code là **một tệp Java hợp lệ về cấu trúc**: chỉ `ValidationApplication` là public; những interface, record và controller còn lại là package-private. Đặt trong một project Boot như mô tả và chạy qua Boot plugin; không dùng `javac ValidationApplication.java` mà không cung cấp classpath Spring/Jakarta.

### Phân tích từng endpoint

**`POST /api/users`**

- `@Validated({Default.class, Create.class})` kích hoạt constraint chung và constraint cho lúc tạo mới.
- `id` phải `null` do `@Null(groups=Create.class)`.
- `name` và `email` bắt buộc; `email` còn phải đúng định dạng.
- `password` không blank và ít nhất 8 ký tự.
- `address` bắt buộc (`@NotNull`), và `address.city` được kiểm tra nhờ `@Valid` cascade; vì `Default` đang được chọn, constraint `@NotBlank` của `city` được áp dụng.
- Dữ liệu hợp lệ trả 201 cùng JSON `{"name":"An"}`. Đây là API demo: chưa có DB, chưa lưu password và chưa trả ID mới.

**`PUT /api/users/{id}`**

- `@Validated({Default.class, Update.class})` chạy constraint chung và constraint cập nhật.
- `id` body phải khác `null` và dương (`@NotNull`, `@Positive` thuộc `Update`).
- `password` được phép `null` vì constraint password chỉ nằm trong `Create`.
- Tên, email, address vẫn được kiểm tra do có `Default`.
- Controller **kiểm tra riêng** ID trên URL phải bằng ID trong JSON. Bean Validation field đơn lẻ không thể tự suy ra quan hệ giữa path variable và DTO này.
- Dữ liệu hợp lệ với ID 7 trả 200 cùng JSON chứa `"id":7` và `"name":"An"`.

**`GET /api/users/page?number=…`**

- `@RequestParam @Min(1) int number` kiểm tra **constraint trực tiếp trên tham số controller**, không phải DTO.
- Với Spring Framework 6.1+ và provider Bean Validation, đây là trường hợp **built-in method validation**; vi phạm có thể dẫn tới `HandlerMethodValidationException`.
- Controller ở ví dụ này **không đặt `@Validated` trên class**, để cơ chế MVC native xử lý method constraints. Trên Spring Framework trước 6.1, cơ chế validation cho tham số kiểu này khác và thường dựa vào AOP `@Validated` cấp class.

## 7. Chạy thử và đọc kết quả

Trong dự án Gradle cấu hình chuẩn, chạy:

```bash
./gradlew bootRun
```

Dùng Bash/curl tại terminal khác (ứng dụng được giả định chạy trên `localhost:8080`):

```bash
curl -i -X POST http://localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"id":null,"name":"An","email":"an@example.com","password":"secret123","address":{"city":"Ha Noi"}}'

curl -i -X POST http://localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"id":10,"name":"","email":"sai-email","password":"abc","address":{"city":""}}'

curl -i -X PUT http://localhost:8080/api/users/7 \
  -H 'Content-Type: application/json' \
  -d '{"id":7,"name":"An","email":"an@example.com","address":{"city":"Ha Noi"}}'

curl -i 'http://localhost:8080/api/users/page?number=0'
```

Các kết quả **mong đợi trong ứng dụng mẫu** (không có security/filter tùy chỉnh):

| Gọi | HTTP status | Tại sao |
| --- | --- | --- |
| POST hợp lệ | 201 Created | Cả `Default` và `Create` đều đạt |
| POST chứa ID, tên trống, email sai, password ngắn, city trống | 400 Bad Request | Vi phạm nhiều constraint thuộc `Create` và `Default` |
| PUT hợp lệ với ID 7, không có password | 200 OK | `Update` không yêu cầu password |
| GET number=0 | 400 Bad Request | `@Min(1)` trên tham số method bị vi phạm |
| GET number=2 | 200 OK | Trả `{"number":2}` |

Body lỗi **không cố định** nếu chưa thiết kế `@ControllerAdvice`: có thể là `ProblemDetail`, cấu trúc lỗi của Boot hoặc output từ custom exception handler; không suy từ status 400 rằng sẽ có JSON liệt kê mọi field. Bean Validation cũng **không cam kết thứ tự các lỗi**.

Mã demo không xử lý password thật. Trong hệ thống production không lưu hoặc log plain-text password; phải hash bằng cơ chế an toàn thích hợp (ví dụ password encoder trong hệ thống xác thực) và áp dụng chính sách bảo mật nhất quán.

## 8. Cascade validation — kiểm tra object lồng nhau

Một DTO lồng nhau có constraint trên field **chỉ được kiểm tra khi validation đi vào object đó**. `@Valid` làm nhiệm vụ cascade:

```java
record Address(@NotBlank String city) {}

record OrderRequest(
        @NotBlank String reference,
        @NotNull @Valid Address shippingAddress) {
}
```

- `@NotNull` kiểm tra `shippingAddress` hiện diện.
- `@Valid` đi tiếp vào `Address` nếu object có giá trị, nhờ đó kiểm tra `city`.
- Nếu bỏ `@Valid` thì kiểm tra `@NotNull` ở cha **không tự kiểm tra** `city` của object con.
- Nếu bỏ `@NotNull` thì `shippingAddress=null` thường được bỏ qua bởi cascade `@Valid`.
- Khi validation group được yêu cầu, các group đó cũng được xét khi cascade; một constraint con chỉ ở `Default` sẽ không tự chạy nếu phía cha chỉ chọn `Create` (trừ khi có group inheritance/conversion/sequence liên quan).

Với container trong Bean Validation 2.0+/Jakarta Validation:

```java
record BulkOrder(
        @NotNull
        @Size(min = 1)
        java.util.List<@Valid OrderRequest> orders) {
}
```

Các phần tử `OrderRequest` không-null được cascade. Nếu **không được phép chứa phần tử null**, đặt constraint thích hợp trên phần tử:

```java
java.util.List<@NotNull @Valid OrderRequest> orders
```

Ở cả hai đoạn trên, đây là **khai báo DTO minh họa**, không kèm endpoint. Hành vi validation container element phụ thuộc provider và metadata hợp lệ; phải có một điểm gọi validator trên `BulkOrder`.

### `@ConvertGroup` khi group ngoài và group trong không tương ứng

Giả sử khi tạo `Order`, bạn cần validate địa chỉ ở nhóm `AddressChecks`, dù bên ngoài đang gọi group `Create`:

```java
import jakarta.validation.Valid;
import jakarta.validation.groups.ConvertGroup;

record OrderPayload(
        @Valid
        @ConvertGroup(from = Create.class, to = AddressChecks.class)
        AddressPayload destination) {
}

interface AddressChecks {}

record AddressPayload(
        @NotBlank(groups = AddressChecks.class) String city) {
}
```

Khi request chọn `Create.class`, cascade trên `destination` chuyển nhóm sang `AddressChecks.class`. `@ConvertGroup` **chỉ có ý nghĩa trên cascade có `@Valid`**; nó không có tác dụng tự động cho mọi field của DTO cha và không làm group cha thay đổi.

Phần này có tên interface/record đơn giản để minh họa cơ chế; nếu đặt cùng project với các ví dụ khác có tên type trùng, cần đổi tên hoặc tách package.

## 9. `@GroupSequence` — thứ tự thực hiện nhóm

Khi truyền nhiều nhóm cho `@Validated({A.class, B.class})`, Jakarta Bean Validation **không đảm bảo** `A` được kiểm tra trước `B`. Điều này quan trọng nếu một nhóm kiểm tra điều kiện cơ bản còn nhóm sau kiểm tra logic nặng hơn.

Khai báo sequence:

```java
import jakarta.validation.GroupSequence;
import jakarta.validation.groups.Default;

interface ExpensiveChecks {}

@GroupSequence({Default.class, ExpensiveChecks.class})
interface OrderedChecks {}
```

Khi yêu cầu `OrderedChecks.class`, validator:

1. Kiểm tra constraints nhóm `Default` trước.
2. Nếu có vi phạm ở `Default`, **không chạy** nhóm `ExpensiveChecks`.
3. Nếu `Default` đạt, mới kiểm tra nhóm `ExpensiveChecks`.

Ví dụ sử dụng ở controller dưới dạng fragment:

```java
@PostMapping("/verify")
public void verify(
        @Validated(OrderedChecks.class) @RequestBody SomeDto body) {
}
```

`SomeDto` phải là DTO do ứng dụng định nghĩa có constraints thuộc các nhóm liên quan; đoạn trên là fragment, không phải một ví dụ chạy riêng. Đừng dùng `@GroupSequence` chỉ để sắp thứ tự **field errors** trả về client; mục đích là thứ tự **đánh giá nhóm** và dừng sớm giữa nhóm.

**Không nhầm hai cách dùng sequence:**

- `@GroupSequence` trên **interface nhóm** (như `OrderedChecks`) tạo nhóm tổng hợp có thứ tự.
- `@GroupSequence` trên **class DTO** có thể **định nghĩa lại `Default` cho class đó**. Khi định nghĩa lại, sequence phải chứa **chính class DTO** thay cho `Default.class` trong danh sách ở cấp class; cấu hình sai có thể ném `GroupDefinitionException`. Việc redefining Default là cục bộ với class, không mặc định truyền nguyên cách định nghĩa lại sang object con.

Để tránh phức tạp không cần thiết, hãy bắt đầu với `Default` và một số group marker rõ ràng. Dùng sequence chỉ khi việc kiểm tra theo giai đoạn có ý nghĩa.

## 10. `BindingResult` và xử lý lỗi ngay trong controller

Không phải endpoint nào cũng nên ném exception ngay khi DTO vi phạm. Với luồng form-render (MVC truyền thống), controller có thể cần giữ input và trả lỗi lên view:

```java
@PostMapping("/register")
public String register(
        @Valid @ModelAttribute("form") RegisterForm form,
        BindingResult bindingResult) {

    if (bindingResult.hasErrors()) {
        return "register-form";
    }

    return "redirect:/registered";
}
```

Trong đó `RegisterForm` là JavaBean đã khai báo constraints và template view `register-form` tồn tại. Đây là **fragment controller sử dụng view rendering**, không phải code độc lập trong ứng dụng REST mẫu ở phần 6.

Các lưu ý chính:

- `BindingResult` hoặc `Errors` phải đặt **ngay sau tham số được validate** mà nó đại diện.
- Có thể đọc `bindingResult.getFieldErrors()`, `getGlobalErrors()`, `hasErrors()` để hiển thị lỗi phù hợp.
- Khi không có `BindingResult`, lỗi validation đầu vào thường được Spring chuyển thành exception và HTTP 400 theo error handling mặc định.
- Với `@RequestBody`, cũng có thể đặt `BindingResult` ngay sau DTO để xử lý theo cách của ứng dụng; tuy nhiên với API REST, nhiều đội chọn central error handler cho response đồng nhất.
- Các lỗi parse JSON hoặc không convert được type thường xảy ra **trước validation**, nên không phải lúc nào cũng nằm trong `BindingResult`. Ví dụ body malformed có thể gây `HttpMessageNotReadableException`.
- Với **method validation** khi có constraint trực tiếp trên tham số controller, Spring MVC 6.1+ có quy tắc phối hợp `Errors`/`BindingResult`: method chỉ được gọi nếu **mọi lỗi validation đều nằm ở các tham số có `Errors` ngay sau chúng**; nếu tham số khác cũng lỗi, `HandlerMethodValidationException` được ném.

## 11. Validation đầu vào DTO và method validation trong Spring MVC 6.1+

Spring MVC có hai đường đi khác nhau, cần nhận biết để cấu hình xử lý lỗi đúng.

### Đường 1 — Kiểm tra argument kiểu DTO

Ví dụ:

```java
@PostMapping("/users")
public void create(@Valid @RequestBody UserPayload body) {
}
```

Nếu chỉ có việc validate object argument theo cơ chế riêng của `@RequestBody`/`@ModelAttribute`, không có constraint trực tiếp đòi hỏi method validation, Spring thường ném **`MethodArgumentNotValidException`** khi input không hợp lệ và không có `BindingResult` ngay sau.

### Đường 2 — Kiểm tra constraint trực tiếp trên tham số/giá trị trả về

Ví dụ:

```java
@GetMapping("/page")
public int page(@RequestParam @Min(1) int number) {
    return number;
}
```

`@Min(1)` là constraint trực tiếp trên tham số method. Từ **Spring Framework 6.1**, Spring MVC có **built-in method validation** để validate những constraint dạng này; khi có lỗi, thường ném **`HandlerMethodValidationException`**. Ràng buộc trực tiếp như `@NotNull` trên tham số method (kể cả tham số có `@Valid`) có thể khiến toàn bộ handler phải đi qua method validation, bao gồm phần validate DTO.

**`@Valid` tự nó không phải constraint** nên chỉ thêm `@Valid` trên DTO không tự ép method validation. Điều này khác với `@NotNull`, `@Min`, `@Size` đặt trực tiếp trên tham số method.

### Tại sao không đặt `@Validated` cấp class trên controller mới?

Spring Framework 6.1 bổ sung built-in method validation cho handler MVC. Nếu controller được đánh dấu **`@Validated` ở cấp class**, method validation có thể diễn ra qua **AOP proxy** thay vì cơ chế native MVC. Để tận dụng API lỗi `HandlerMethodValidationException` và integration được Spring khuyến nghị, **bỏ `@Validated` ở cấp class controller** khi chuyển sang cách mới; vẫn đặt `@Validated(Group.class)` trên tham số DTO để chọn group.

Với **service bean**, `@Validated` cấp class vẫn là cách phổ biến để bật Spring AOP method validation qua `MethodValidationPostProcessor`, miễn ứng dụng có proxy/validator phù hợp. Những cơ chế proxy chịu ảnh hưởng của self-invocation, public method/proxy type và tổ hợp annotation; không suy ra rằng `new Service()` hoặc gọi từ cùng bean luôn đi qua AOP.

### Khác nhau giữa các mốc phiên bản

| Nền tảng | Điều cần nhớ |
| --- | --- |
| Spring 5.x / Boot 2.x | Thường dùng namespace `javax.validation`; MVC có validation object argument, còn method validation thường cần cơ chế AOP riêng. |
| Spring 6.0 / Boot 3.0–3.1 | Chuyển sang `jakarta.validation`; chưa có cơ chế method validation native mới của Spring MVC 6.1. |
| Spring 6.1+ / Boot 3.2+ | Spring MVC hỗ trợ native method validation với `HandlerMethodValidationException`; tránh class-level `@Validated` trên controller nếu muốn hành vi native. |

Bảng nêu **baseline thường gặp**. Luôn kiểm tra phiên bản `spring-webmvc` và provider validation thực tế trong dependency tree, đặc biệt khi dự án override BOM hoặc dùng dependency riêng.

## 12. Xử lý exception và thiết kế lỗi HTTP rõ ràng

Trong API REST, người dùng cần biết field nào vi phạm, nhưng response không nên lộ stack trace hoặc implementation detail.

Một handler mẫu cho lỗi DTO (`MethodArgumentNotValidException`):

```java
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ValidationAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, List<String>>> invalidBody(
            MethodArgumentNotValidException ex) {

        List<String> messages = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest()
                .body(Map.of("errors", messages));
    }
}
```

Đoạn này là **class đầy đủ nhưng cần được đặt trong project Spring**, và chỉ minh họa một loại exception. Nếu đặt chung package với controller mẫu phần 6, nó sẽ thay đổi cấu trúc body lỗi thành object chứa `errors` (thứ tự danh sách vẫn không bảo đảm).

Với Spring MVC 6.1+, có thể xử lý `HandlerMethodValidationException` bằng `@ExceptionHandler` hoặc override phương thức tương ứng trong `ResponseEntityExceptionHandler`. Ví dụ fragment:

```java
@ExceptionHandler(HandlerMethodValidationException.class)
ResponseEntity<Map<String, Object>> invalidMethod(
        HandlerMethodValidationException ex) {

    List<String> messages = ex.getAllValidationResults().stream()
            .flatMap(result -> result.getResolvableErrors().stream())
            .map(error -> error.getDefaultMessage())
            .toList();

    return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of("errors", messages));
}
```

Fragment này cần imports `org.springframework.web.method.annotation.HandlerMethodValidationException`, `java.util.List`, `java.util.Map`, `org.springframework.http.ResponseEntity` và được đặt **bên trong `@RestControllerAdvice`**. `HandlerMethodValidationException` có thể biểu diễn cả vi phạm tham số và **vi phạm giá trị trả về**; status thường là **400 cho input** và **500 cho lỗi return value**. Không hardcode 400 cho mọi trường hợp rồi vô tình che lỗi chương trình.

Trong hệ thống thực, bổ sung xử lý lỗi JSON sai cú pháp (`HttpMessageNotReadableException`), lỗi type conversion, business errors, lỗi nhóm/metadata validator và response format chuẩn (ví dụ RFC 9457 `ProblemDetail`), cùng correlation ID khi cần.

Thông điệp từ constraint có thể chứa nội dung do developer tạo; tránh đưa giá trị bí mật trong rejected values ra JSON/log. Với i18n, Spring cung cấp `MessageSource`/message codes và Bean Validation message interpolation để địa phương hóa thông báo.

## 13. Integration test có thể đặt vào dự án ví dụ

Tạo **`src/test/java/demo/validation/ValidationApplicationTests.java`**, dùng `spring-boot-starter-test` đã khai báo:

```java
package demo.validation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ValidationApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void createAcceptsDefaultAndCreateGroups() throws Exception {
        mvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"An",
                      "email":"an@example.com",
                      "password":"secret123",
                      "address":{"city":"Ha Noi"}
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("An"));
    }

    @Test
    void createRejectsInvalidDefaultAndCreateConstraints() throws Exception {
        mvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id":10,
                      "name":"",
                      "email":"invalid",
                      "password":"abc",
                      "address":{"city":""}
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateDoesNotRequireCreateOnlyPassword() throws Exception {
        mvc.perform(put("/api/users/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id":7,
                      "name":"An",
                      "email":"an@example.com",
                      "address":{"city":"Ha Noi"}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void updateRequiresId() throws Exception {
        mvc.perform(put("/api/users/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"An",
                      "email":"an@example.com",
                      "address":{"city":"Ha Noi"}
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void directMethodConstraintIsValidated() throws Exception {
        mvc.perform(get("/api/users/page").param("number", "0"))
            .andExpect(status().isBadRequest());

        mvc.perform(get("/api/users/page").param("number", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.number").value(2));
    }
}
```

Chạy:

```bash
./gradlew test
```

Bộ test mong đợi **5 method pass** với baseline Boot 3.2+ Spring 6.1+, Jakarta provider đã bật và không có bảo mật/filter tùy chỉnh. Các assertions tập trung HTTP status/body xác định trong ví dụ, **không phụ thuộc thứ tự constraint violations hoặc error format mặc định**.

Để nâng chất lượng kiểm thử, thêm các case:

- Tạo mới thiếu `address`: 400 do `@NotNull`.
- `address.city=""`: 400 nhờ `@Valid` cascade.
- Tạo mới `id=7`: 400 bởi `Create`; cập nhật `id=null`: 400 bởi `Update`.
- Bỏ `@Valid` ở `address` trong một nhánh test minh họa: chứng minh lỗi city không còn được phát hiện bởi cascade (nhưng `@NotNull` ở address vẫn chạy).
- Đặt `@Validated(Create.class)` thay cho `@Validated({Default.class,Create.class})` trong một test độc lập: thấy rõ ràng buộc chỉ thuộc `Default` bị bỏ qua.
- Đưa `@NotNull` trực tiếp lên tham số method cần kiểm tra: xác nhận khi nào lỗi đi qua `HandlerMethodValidationException`.
- Nếu có `@ControllerAdvice` custom, assert schema body, message codes, localization và trạng thái đúng cho lỗi return value.

## 14. Tự validate trong service và không phụ thuộc vào HTTP

Bean Validation không dành riêng cho controller. Bạn có thể gọi `jakarta.validation.Validator` tại ranh giới dữ liệu phù hợp:

```java
package demo.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Set;

public class StandaloneValidation {
    record Invoice(@NotBlank String customer, @Positive int total) {}

    public static void main(String[] args) {
        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            Invoice invoice = new Invoice("", -2);
            Set<ConstraintViolation<Invoice>> errors =
                    validator.validate(invoice);

            System.out.println(errors.size());
        }
    }
}
```

Đây là **một tệp Java 17+ độc lập về Spring MVC**, nhưng để biên dịch/chạy cần classpath có `jakarta.validation-api` và implementation (ví dụ Hibernate Validator) cùng các dependency runtime của provider. Tạo tệp `src/main/java/demo/validation/StandaloneValidation.java` trong dự án Boot ở phần 6 (đã có starter validation); có thể chạy bằng IDE với classpath Gradle hoặc task JavaExec thích hợp.

Trong trường hợp hai constraint `@NotBlank` và `@Positive` của `Invoice("", -2)` cùng được đánh giá bởi provider tương thích, stdout là:

```text
2
```

Không dựa vào thứ tự phần tử của `Set<ConstraintViolation<Invoice>>` để xây response. Nếu cần validate group:

```java
validator.validate(invoice, Default.class, Create.class);
```

Lời gọi này là fragment nằm trong scope có `validator`, `invoice` và hai type `Default`/`Create` phù hợp. `Validator#validate(object)` mặc định chọn `Default`; truyền nhóm chỉ thực hiện những group được nêu và kế thừa/sequence/conversion liên quan.

Đối với **method validation trên service**, có thể sử dụng `@Validated` cấp class và `@NotNull`, `@Min` trực tiếp trên tham số method trong một bean do Spring quản lý. Hãy phân biệt với `Validator#validate(bean)` (kiểm tra constraint trên trạng thái object); validator chuẩn còn có API `ExecutableValidator` để kiểm tra tham số/return value của method nếu cần thao tác chủ động.

## 15. Các lỗi phổ biến cần tránh

1. **Thiếu implementation**: chỉ thêm `jakarta.validation-api` rồi chờ `@Valid` tự chạy; với Boot dùng `spring-boot-starter-validation`.
2. **Import sai namespace**: Boot 3.x sử dụng `jakarta.validation.*`, không phải `javax.validation.*` từ thế hệ Boot 2.x.
3. **Nhầm `@Valid` với constraint bắt buộc**: `@Valid` chỉ trigger/cascade; object con `null` cần `@NotNull` nếu bắt buộc.
4. **Nhầm `@Validated(Create.class)` tự áp dụng `Default`**: group khác không tự kéo `Default` vào.
5. **Gắn annotation constraint vào DTO nhưng quên kích hoạt validation ở boundary**: body đã deserialize không có nghĩa đã được validate.
6. **Dùng `@NotNull` cho chuỗi bắt buộc**: `""` vẫn vượt qua; cân nhắc `@NotBlank`.
7. **Quên `@Valid` trên field lồng nhau / collection elements**: child constraint không chạy như mong đợi.
8. **Đặt `BindingResult` sai vị trí**: Spring yêu cầu theo ngay sau tham số được validate tương ứng.
9. **Xử lý mọi lỗi thành một exception**: Spring MVC 6.1+ có cả `MethodArgumentNotValidException` và `HandlerMethodValidationException`; parse/type error còn có exception khác.
10. **Áp dụng `@Validated` ở class-level controller rồi mong native MVC method validation**: class-level có thể chuyển sang đường AOP, làm exception khác kỳ vọng.
11. **Tưởng group bảo vệ security/business rule**: role, owner, uniqueness, concurrency vẫn phải kiểm tra ở layer đúng.
12. **Tin rằng list lỗi có thứ tự cố định**: mặc định không có cam kết thứ tự và lỗi đầu tiên; nếu thứ tự nhóm cần thiết dùng `@GroupSequence`.
13. **Để DB constraint phụ thuộc vào validation đầu vào**: phải bảo vệ cả phía persistence và xử lý race condition.
14. **Trả giá trị rejectedValue nhạy cảm cho client**: thận trọng với password/token/log.
15. **Dùng chung một DTO cho quá nhiều hành động**: khi groups chằng chịt, DTO riêng theo use case thường rõ và an toàn hơn.

## 16. Chọn `@Valid`, `@Validated` hay DTO riêng?

| Tình huống | Gợi ý |
| --- | --- |
| Một DTO và bộ quy tắc chung duy nhất | `@Valid @RequestBody Dto` |
| Có ràng buộc khác nhau giữa create/update nhưng cấu trúc gần giống | `@Validated({Default.class,Create.class})` hoặc `{Default.class,Update.class}` |
| Object chứa object/list con có constraint | Thêm `@Valid` tại vị trí cascade; `@NotNull` nếu bắt buộc |
| Group phải chạy theo giai đoạn | `@GroupSequence` trên group interface, test tình huống dừng sớm |
| Input có hình dạng, quyền thay đổi, field bắt buộc rất khác nhau | Tạo DTO riêng như `CreateUserRequest` và `UpdateUserRequest` |
| Kiểm tra tham số scalar trực tiếp trên controller, Framework 6.1+ | Constraint `@Min`, `@NotBlank`... + native MVC method validation |
| Kiểm tra method của service bean | `@Validated` cấp class, cấu hình AOP method validation phù hợp |
| Quy tắc phụ thuộc DB, người dùng đã xác thực hoặc trạng thái hệ thống | Validation nghiệp vụ trong service/domain + transaction/database constraints |

Tư duy khi áp dụng: chọn **hợp đồng input của endpoint** trước; đặt constraints ngay tại DTO thích hợp; chọn group có chủ ý; bảo đảm nested validation; xử lý lỗi nhất quán và viết test cho cả đường thành công lẫn thất bại.

## 17. Bài tập tự kiểm tra

1. Tạo `OrderRequest` có `quantity` lớn hơn 0, `email` bắt buộc và `shippingAddress.city` không trống. Gọi `@Valid` trên body: trường hợp thiếu `shippingAddress` có bị chặn nếu chỉ đặt `@Valid` trên field không? Vì sao?
2. Thêm group `Publish` để yêu cầu `description` ít nhất 30 ký tự khi phát hành. Vì sao `@Validated(Publish.class)` có thể bỏ qua `@NotBlank` mặc định trên `title`? Hãy sửa annotation tham số.
3. Xây dựng sequence `Default` rồi `Publish`. Nếu title trống, nhóm `Publish` có chạy không? Viết test chứng minh.
4. Với `GET /products?page=0` và `@Min(1)` trên tham số controller ở Framework 6.1+, exception nào có thể được ném? Nếu đồng thời thêm `@Valid @RequestBody` thì exception handling cần lưu ý gì?
5. Viết test cập nhật user: password không truyền vẫn hợp lệ, email bỏ trống phải lỗi, id body khác path phải lỗi nghiệp vụ. Phân biệt ba loại quy tắc đó.
6. Trong method controller có `BindingResult` ngay sau DTO nhưng còn một tham số `@RequestParam @Min(1)` khác bị lỗi, controller có nhất thiết được gọi không? Hãy giải thích native method validation.
7. Viết một `@RestControllerAdvice` trả mã lỗi ổn định và không làm lộ rejected password cho cả hai exception validation phổ biến.

## 18. Tài liệu chính thức

- [Spring Framework — Spring MVC Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html): phân biệt argument validation và native method validation, `@Valid`, `@Validated`, `BindingResult`, exception.
- [Spring Framework — Validated Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/validation/annotation/Validated.html): nhóm validation và AOP method validation.
- [Spring Framework — Spring MVC Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html): tham số controller được hỗ trợ.
- [Spring Framework 6.1 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes): mốc bổ sung built-in MVC method validation.
- [Jakarta Bean Validation 3.0 Specification](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html): ràng buộc chuẩn, `Default`, nhóm, cascade, `@GroupSequence`, `@ConvertGroup`.
- [Spring Boot — Validation](https://docs.spring.io/spring-boot/reference/io/validation.html): autoconfiguration và method validation cho Spring bean.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-validation-007">Errors, BindingResult và vị trí tham số nhận lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Errors, BindingResult và vị trí tham số nhận lỗi trong Spring MVC

## 1. Vấn đề cần giải quyết

Giả sử một trang đăng ký nhận dữ liệu sau:

```text
name=
age=abc
email=ada@example.com
```

Controller mong đợi `age` là số nguyên và `name` không được rỗng. Nếu thiếu cơ chế xử lý, request có thể kết thúc bằng lỗi 400 mà người dùng không biết nên sửa trường nào. Spring MVC cung cấp `Errors` và `BindingResult` để controller xem **những lỗi đã được ghi nhận khi gắn dữ liệu và kiểm tra một đối tượng request**.

Bài này tập trung vào bốn vấn đề:

1. `Errors` khác `BindingResult` ở đâu; cách đọc `FieldError`, `ObjectError`, mã lỗi, giá trị bị từ chối và tên đối tượng.
2. Vì sao tham số `Errors`/`BindingResult` phải **đứng ngay sau** `@ModelAttribute`, `@RequestBody` hoặc `@RequestPart` mà nó gắn với.
3. Khi có/không có tham số nhận lỗi, Spring gọi controller hay ném exception; phân biệt lỗi chuyển kiểu, lỗi Bean Validation, lỗi giải mã JSON và method validation.
4. Cách trả lỗi HTTP rõ ràng, trình bày lỗi ở form HTML và kiểm chứng bằng `MockMvc`.

**Bối cảnh:** Spring MVC trên Servlet stack, Spring Framework **6.x** / Spring Boot **3.x**, Java **17+**. Các ví dụ `@Valid` sử dụng `jakarta.validation.Valid` của Jakarta Bean Validation (không dùng package `javax.validation` của dự án cũ).

## 2. Từ request đến đối tượng và danh sách lỗi

Khi controller khai báo một form object:

```java
@PostMapping("/register")
String register(@Valid @ModelAttribute("signup") SignupForm form,
                BindingResult result) {
    if (result.hasErrors()) {
        return "register-form";
    }
    return "register-success";
}
```

*Đây là minh họa chữ ký controller trả về tên view; ví dụ REST chạy được nằm ở mục 8.*

Luồng xử lý tổng quát:

```text
Request: name="", age="abc", email="ada@example.com"
  │
  ▼
RequestMappingHandlerAdapter / method argument resolver
  │
  ├── Tạo hoặc lấy SignupForm trong model
  ├── WebDataBinder nhận các giá trị theo tên property
  │     ├── name="" → String (bind được)
  │     ├── age="abc" → Integer (chuyển kiểu THẤT BẠI)
  │     └── email="ada@example.com" → String (bind được)
  │
  ├── BindingResult ghi lỗi typeMismatch của age
  ├── @Valid yêu cầu Bean Validation
  │     └── @NotBlank trên name → lỗi NotBlank
  │
  ├── Tham số BindingResult đứng ngay sau SignupForm?
  │     ├── Có → controller có thể đọc result.hasErrors()
  │     └── Không → lỗi được xử lý theo nhánh exception thích hợp
  ▼
Controller hoặc cơ chế xử lý exception
```

**Binding lỗi** khi dữ liệu không chuyển được sang kiểu mong đợi (ví dụ `"abc"` → `Integer`). **Validation lỗi** khi dữ liệu đã được xử lý nhưng vi phạm quy tắc (ví dụ `age=15` với `@Min(18)`). Cả hai có thể được quan sát thông qua `BindingResult` đối với form object.

`@Valid` không thực hiện chuyển kiểu; nó yêu cầu chạy các constraint tương ứng sau khi đối tượng được bind/deserialized. Muốn Bean Validation hoạt động trong một project Spring Boot 3, thêm `spring-boot-starter-validation`, hoặc cung cấp Jakarta Validation provider tương đương. Chỉ có `spring-boot-starter-web` không nên được coi là đã bao gồm provider validation.

Đối với `@RequestBody` JSON, việc giải mã được thực hiện trước bởi `HttpMessageConverter` (thường Jackson), **không phải** property binding của một HTML form. Nếu JSON không thể parse hoặc giá trị không deserialize được, lỗi có thể xuất hiện **trước khi tạo đối tượng và BindingResult để controller xử lý**. `Errors` đặt sau `@RequestBody` dùng hữu ích cho lỗi validation của đối tượng đã deserialize thành công.

## 3. Errors và BindingResult: quan hệ và API

### 3.1. Quan hệ kế thừa

`Errors` và `BindingResult` là hai **interface** trong package `org.springframework.validation`:

```text
org.springframework.validation.Errors
                   ▲
                   │ extends
org.springframework.validation.BindingResult
```

`BindingResult extends Errors`. Vì vậy, object triển khai `BindingResult` cũng có đầy đủ phương thức của `Errors`.

- **`Errors`**: xem, đăng ký và tra cứu lỗi theo object hoặc field; dùng khi code chỉ cần làm việc với lỗi.
- **`BindingResult`**: bổ sung thông tin đặc thù quá trình binding như target object, raw field value, danh sách suppressed fields và cách đưa binding result vào model.

Có thể dùng `Errors` thay cho `BindingResult` khi chỉ cần `hasErrors()`, `getFieldErrors()` hoặc `rejectValue(...)`. Chọn `BindingResult` khi bạn cần `getTarget()`, `getSuppressedFields()`, `getModel()` hoặc các thông tin binding chi tiết khác.

### 3.2. Các phương thức nên nhớ

| API | Ý nghĩa | Tình huống |
| --- | --- | --- |
| `errors.hasErrors()` | Có bất kỳ lỗi object/field nào | Quyết định xử lý tiếp hay trả lỗi |
| `errors.getErrorCount()` | Tổng số lỗi đã ghi nhận | Thống kê lỗi |
| `errors.hasFieldErrors("age")` | Có lỗi riêng của field `age` | Đánh dấu input cần sửa |
| `errors.getFieldError("age")` | Một lỗi đầu tiên của field `age`, có thể `null` | Lấy code/message của lỗi |
| `errors.getFieldErrors()` | Danh sách tất cả `FieldError` | Trả lỗi theo field |
| `errors.getGlobalErrors()` | Lỗi của object, không thuộc một field riêng | Quy tắc liên trường |
| `errors.getAllErrors()` | Gồm lỗi global và field | Xử lý chung |
| `errors.getObjectName()` | Tên object được bind, như `signup` | Biết lỗi thuộc form nào |
| `errors.reject("code")` | Đăng ký lỗi global theo mã | Quy tắc cả object |
| `errors.rejectValue("age", "code")` | Đăng ký lỗi ở một field | Validator tùy chỉnh |
| `bindingResult.getTarget()` | Object được bind, có thể `null` | Debug/kiểm tra mục tiêu |
| `bindingResult.getRawFieldValue("age")` | Giá trị raw bên dưới theo binding implementation | Debug binding |
| `bindingResult.getSuppressedFields()` | Tên các trường bị chặn trong binding | Phát hiện yêu cầu gán ngoài allowlist |
| `bindingResult.getModel()` | Map gồm object và binding result của object đó | Phục vụ model/view và binding state |

Không phải mọi lỗi đều chỉ có một `FieldError`. Một field có thể vi phạm nhiều constraint, như `@NotNull` và `@Min`, tùy tình huống. Vì thế `getFieldError("age")` lấy một lỗi nhưng `getFieldErrors("age")` có thể có nhiều lỗi.

### 3.3. FieldError và ObjectError

`FieldError` mở rộng `ObjectError`. Chúng thực thi ý tưởng hai phạm vi lỗi:

- **`FieldError`:** gắn với property cụ thể, ví dụ `age` không phải số hoặc nhỏ hơn 18. Các thông tin thường cần: `getField()`, `getRejectedValue()`, `getCode()`, `getCodes()`, `getDefaultMessage()` và `isBindingFailure()`.
- **`ObjectError` (global):** gắn với toàn bộ object, ví dụ khoảng thời gian bắt đầu phải trước kết thúc, nhưng lỗi cần xét nhiều thuộc tính. Xem `getObjectName()`, `getCode()` và `getDefaultMessage()`.

Ví dụ đoạn xử lý lỗi, dùng trong controller/validator nơi đã có `BindingResult result`:

```java
for (FieldError e : result.getFieldErrors()) {
    System.out.println(
        e.getField() + ": " + e.getCode()
        + ", rejected=" + e.getRejectedValue());
}

for (ObjectError e : result.getGlobalErrors()) {
    System.out.println(
        "object=" + e.getObjectName()
        + ", code=" + e.getCode());
}
```

*Đoạn này minh họa cách đọc lỗi; để biên dịch riêng cần import `FieldError`, `ObjectError` và biến `result` có kiểu `BindingResult`. Khi đưa vào production, **không log tùy tiện** `getRejectedValue()` vì có thể chứa dữ liệu cá nhân, token hoặc bí mật.*

### 3.4. Mã lỗi và thông điệp hiển thị

Mã lỗi như `typeMismatch`, `NotBlank`, `Min` là **khóa tra thông điệp**, không phải văn bản cuối bắt buộc phải hiển thị. `MessageCodesResolver` có thể sinh dãy mã từ chi tiết đến tổng quát. Chẳng hạn với form tên `signup`, field `age` không đổi được sang `Integer`, một số mã có thể gồm:

```text
typeMismatch.signup.age
typeMismatch.age
typeMismatch.java.lang.Integer
typeMismatch
```

*Các mã là ví dụ của cơ chế phân giải mặc định; kiểm tra `FieldError#getCodes()` để biết chính xác cấu hình/loại field hiện hành.*

Khi render thông điệp, dùng `MessageSource` hoặc tích hợp form rendering của Spring để tra theo locale và mã lỗi. `getDefaultMessage()` hữu ích như fallback nhưng không cam kết luôn được bản địa hóa/ổn định cho hợp đồng API. API thực tế nên tạo cấu trúc phản hồi có `field` và `code` ổn định; tránh trả nguyên exception message.

## 4. Quy tắc then chốt: đặt tham số Errors/BindingResult sát bên đối tượng nhận lỗi

Spring MVC ánh xạ `Errors`/`BindingResult` vào kết quả của **tham số request được resolve ngay trước nó**. Vì vậy, nó **phải đứng ngay sau** đối tượng `@ModelAttribute`, `@RequestBody` hoặc `@RequestPart` có binding/validation cần xử lý.

### 4.1. Đúng: BindingResult ngay sau @ModelAttribute

```java
@PostMapping("/save")
String save(@Valid @ModelAttribute("profile") ProfileForm profile,
            BindingResult errors,
            Model model) {
    if (errors.hasErrors()) {
        return "profile-form";
    }
    return "profile-saved";
}
```

Ở đây `BindingResult errors` nói về `profile`. Tham số `Model model` có thể đặt **sau** BindingResult, không phá cặp.

### 4.2. Sai: một tham số khác chen giữa

```java
// SAI: Model nằm giữa object cần bind và kết quả nhận lỗi.
@PostMapping("/save")
String save(@Valid @ModelAttribute("profile") ProfileForm profile,
            Model model,
            BindingResult errors) {
    return "profile-saved";
}
```

Lỗi có thể biểu hiện bằng exception về thứ tự `Errors/BindingResult` hoặc thất bại trong quá trình giải quyết argument. Không dựa vào việc “Spring sẽ dò ngược đến object đầu tiên có lỗi”. Đưa `BindingResult` sát ngay sau `profile` là cách đúng.

**Không đặt** `BindingResult` ở đầu method và không đặt sau một `@RequestParam` khác nếu nó thuộc `@ModelAttribute` trước đó. Tên biến Java là `errors` hay `bindingResult` không ảnh hưởng; **vị trí** và đối tượng liền trước mới quan trọng.

### 4.3. Hai đối tượng input: mỗi đối tượng có BindingResult riêng

```java
@PostMapping("/compose")
String compose(@Valid @ModelAttribute("shipping") ShippingForm shipping,
               BindingResult shippingErrors,
               @Valid @ModelAttribute("billing") BillingForm billing,
               BindingResult billingErrors) {
    if (shippingErrors.hasErrors() || billingErrors.hasErrors()) {
        return "compose-form";
    }
    return "composed";
}
```

*Đây là chữ ký minh họa cho controller có hai DTO và view tương ứng; không phải chương trình độc lập.* Kết quả thứ nhất thuộc `shipping`, kết quả thứ hai thuộc `billing`. Nếu gom hai `BindingResult` xuống cuối method, mapping có thể sai hoặc ném lỗi. Cặp argument theo từng object quan trọng hơn cách đặt tên biến.

### 4.4. @RequestBody và @RequestPart

Cặp đúng cho JSON đã deserialize:

```java
@PostMapping("/json")
ResponseEntity<?> json(@Valid @RequestBody CreateOrderRequest request,
                       Errors errors) {
    if (errors.hasErrors()) {
        return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok().build();
}
```

Đối với part JSON trong multipart, có thể dùng cặp `@Valid @RequestPart("metadata") Metadata metadata, BindingResult errors` tương tự. **Lưu ý:** file part `MultipartFile` là dữ liệu upload; không đồng nhất việc validate file với Bean Validation của một DTO deserialize từ part.

Điểm khác biệt cần nhớ:

- Với `@ModelAttribute`, lỗi **binding/conversion** và lỗi validation của form object thường cùng vào `BindingResult`.
- Với `@RequestBody`/`@RequestPart`, JSON/XML được đọc bằng message converter trước. `Errors`/`BindingResult` ngay sau có thể cho bạn **lỗi Bean Validation** của object đã đọc thành công; **không** thu được mọi lỗi JSON cú pháp, thiếu request body, multipart parse hay không hỗ trợ media type.
- `@RequestParam Integer age` và `@PathVariable Long id` là trường hợp tham số đơn; lỗi chuyển kiểu của chúng thường đi qua exception resolver như `MethodArgumentTypeMismatchException`, không phải bằng cách thêm một `BindingResult` ngay sau chúng.

## 5. @Valid, @Validated và hành vi khi không có tham số nhận lỗi

### 5.1. Không có Errors/BindingResult sát bên

```java
@PostMapping("/register")
String register(@Valid @ModelAttribute("signup") SignupForm form) {
    return "saved";
}
```

Nếu input vi phạm validation hoặc binding thất bại, Spring MVC mặc định không tiếp tục vào method như thể form hợp lệ. Với validation/binding của một object xử lý ở mức tham số, `MethodArgumentNotValidException` là nhánh ngoại lệ phổ biến; MVC có thể chuyển nó thành HTTP 400.

Đối với `@Valid @RequestBody SignupForm form` không có `Errors`, object JSON đã được deserialize nhưng vi phạm constraint cũng thường gây `MethodArgumentNotValidException` và mặc định HTTP 400.

**Có tham số ngay sau:**

```java
@PostMapping("/register")
String register(@Valid @ModelAttribute("signup") SignupForm form,
                Errors errors) {
    if (errors.hasErrors()) {
        return "register-form";
    }
    return "saved";
}
```

Spring có thể gọi method để bạn xử lý lỗi ngay trong controller. Bản thân `@Valid` **không** tự trả HTML hoặc JSON lỗi cho bạn; controller phải dùng `hasErrors()` hoặc các API tương ứng. Không bỏ qua lỗi rồi chạy service lưu dữ liệu.

### 5.2. Không phải mọi validation đều là một tham số riêng

Từ Spring MVC **6.1**, nếu controller method có constraint trực tiếp trên method parameter, như `@Min(1) @RequestParam int page`, validation có thể áp dụng ở **mức method** thay vì mức một object. Khi đó có thể gặp `HandlerMethodValidationException`, khác với `MethodArgumentNotValidException`.

```java
@GetMapping("/search")
String search(@RequestParam @Min(1) int page) {
    return "page:" + page;
}
```

*Đoạn minh họa yêu cầu Jakarta Validation provider, import của MVC và constraint `@Min`.* Khi client gửi `page=0`, validation mức method không có một form object `@ModelAttribute` để đặt `BindingResult` thông thường. Khi thêm constraint trực tiếp trên một parameter vào một method đã có `@Valid @ModelAttribute`, quy tắc xử lý lỗi có thể đổi vì method validation bao phủ cả nested constraints.

Trong cơ chế method validation tích hợp của MVC 6.1+, controller có thể được gọi khi **mọi lỗi validation liên quan đến những tham số đã có `Errors` ngay sau chúng**. Nếu còn lỗi method parameter khác không có nơi nhận tương ứng, Spring ném `HandlerMethodValidationException`. Không giả định một `BindingResult` của form sẽ bắt mọi lỗi ở những parameter khác trong cùng method.

`@Validated` có thể dùng chọn validation groups. Nhưng class-level `@Validated` trên controller có thể kích hoạt AOP method validation; với Spring MVC 6.1+, nếu muốn dùng method validation tích hợp của MVC, tài liệu Spring khuyến nghị bỏ `@Validated` ở cấp class trong trường hợp đó. Cần kiểm tra cả cấu hình và phiên bản trước khi giải thích kết quả exception.

### 5.3. Bảng chẩn đoán

| Tình huống | Controller có nhận kết quả bằng Errors ngay sau? | Phản ứng điển hình |
| --- | --- | --- |
| `@Valid @ModelAttribute Form`, dữ liệu sai | Có | Controller chạy và kiểm tra `errors.hasErrors()` |
| `@Valid @ModelAttribute Form`, dữ liệu sai | Không | `MethodArgumentNotValidException` / 400 (nếu không có method validation khác) |
| `@Valid @RequestBody Dto`, JSON hợp lệ nhưng vi phạm constraint | Có | Controller nhận lỗi validation |
| `@Valid @RequestBody Dto`, JSON hợp lệ nhưng vi phạm constraint | Không | `MethodArgumentNotValidException` / 400 |
| `@RequestBody`, JSON sai cú pháp hoặc không deserialize được | Có hoặc không | `HttpMessageNotReadableException` / thường 400, trước khi controller chạy |
| `@RequestParam Integer age` nhận `age=abc` | Không dùng BindingResult cho param đơn | Thường `MethodArgumentTypeMismatchException` / 400 |
| Method-level `@Min(1) @RequestParam int page` | Không có result riêng cho param này | `HandlerMethodValidationException` trong cơ chế MVC tích hợp |
| Client gửi field bị cấm bởi `allowedFields` | Có `BindingResult` | Đọc `getSuppressedFields()`, có thể không xuất hiện trong `hasErrors()` |

Các trạng thái HTTP ở đây là mặc định phổ biến, có thể thay đổi qua `@ExceptionHandler`, `@ControllerAdvice`, security và cấu hình exception resolvers. Phân biệt **exception trước khi controller chạy** với **lỗi đã ghi vào BindingResult được controller xử lý**.

## 6. Lỗi theo field, lỗi global và thông điệp đưa về giao diện

### 6.1. Field errors

Ví dụ request có `age=abc`, `name=""`:

```text
objectName: signup
FieldError(field=age, code=typeMismatch, rejectedValue=abc)
FieldError(field=name, code=NotBlank, rejectedValue="")
```

*Đây là mô tả logic, không phải chuỗi `toString()` được đảm bảo nguyên văn; số lượng/mã chi tiết có thể đổi theo constraint và resolver.* `errors.hasFieldErrors("age")` trả `true`, `errors.getFieldErrors()` trả danh sách những lỗi tương ứng.

### 6.2. Global errors

Một quy tắc như “ngày bắt đầu phải trước ngày kết thúc” phụ thuộc vào **cặp** field. Validator hoặc controller có thể đăng ký lỗi toàn object:

```java
if (!startDate.isBefore(endDate)) {
    errors.reject("dateRange.invalid", "Ngày bắt đầu phải trước ngày kết thúc");
}
```

*Đoạn này là phần logic minh họa; các biến `startDate`, `endDate` phải lấy từ object request trong một validator hoặc controller.* Lỗi global xuất hiện trong `getGlobalErrors()`. Nếu muốn lỗi hiện dưới một trường nhất định, có thể dùng `rejectValue("endDate", "dateRange.invalid", "...")` khi field đó có mặt trong binding target. Phương án hiển thị thuộc về UX, còn việc kiểm tra rule nên nằm ở lớp phù hợp và được kiểm thử.

### 6.3. Dùng lại lỗi trong trang HTML

Với controller trả view (không phải `@RestController`), một `@ModelAttribute("signup")` và `BindingResult` thường được Spring đưa vào model cho render lại form. Key tương ứng của BindingResult có dạng:

```text
org.springframework.validation.BindingResult.signup
```

Đây là tiền tố `BindingResult.MODEL_KEY_PREFIX` ghép với object name `signup`. Một engine template như Thymeleaf và Spring form tags sử dụng binding state để đánh dấu input lỗi và hiển thị thông điệp.

Khi form sai, thông thường **return tên view** để giữ trạng thái form và errors trong cùng request:

```java
@PostMapping("/register")
String register(@Valid @ModelAttribute("signup") SignupForm form,
                BindingResult result) {
    if (result.hasErrors()) {
        return "register-form";
    }
    return "redirect:/registered";
}
```

*Đây là ví dụ chữ ký controller có view resolver; không áp vào `@RestController`.* Nếu redirect ngay khi lỗi, request mới không còn `BindingResult` cũ trừ khi chủ động dùng flash attributes theo đúng cấu trúc. Tránh redirect lỗi một cách vô tình khiến input và thông báo biến mất.

### 6.4. JSON API: trả lỗi có cấu trúc

Khi làm REST API, có thể tạo response kiểu:

```json
{
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "age", "code": "typeMismatch", "message": "Tuổi phải là số" },
    { "field": "name", "code": "NotBlank", "message": "Tên không được trống" }
  ]
}
```

Schema trên là **gợi ý hợp đồng API**, không phải response mặc định của Spring Boot. Có thể tổng hợp từ `FieldError` và resolve thông điệp theo `Locale`. Đừng phát tán stack trace, SQL, chi tiết nội bộ của converter, hoặc rejected value nhạy cảm. Tránh coi `getDefaultMessage()` là mã API ổn định; frontend nên dựa trên `code`/`field` đã chuẩn hóa nếu cần tương thích lâu dài.

## 7. Ba chữ ký cần nhớ trước khi thực hành

```java
// 1. Form binding + Bean Validation: nhận lỗi ngay tại method.
@PostMapping("/form")
ResponseEntity<?> form(
        @Valid @ModelAttribute("signup") SignupForm form,
        BindingResult errors) {
    // ...
}

// 2. JSON deserialize thành công + Bean Validation: nhận lỗi tại method.
@PostMapping("/json")
ResponseEntity<?> json(
        @Valid @RequestBody SignupForm form,
        Errors errors) {
    // ...
}

// 3. SAI: Errors bị tách khỏi đối tượng cần validation.
@PostMapping("/wrong")
ResponseEntity<?> wrong(
        @Valid @ModelAttribute("signup") SignupForm form,
        @RequestParam(required = false) String trace,
        BindingResult errors) {
    // ...
}
```

*Đây là đoạn chữ ký minh họa, không tự biên dịch độc lập; xem ứng dụng đầy đủ ngay sau.* Trong hai chữ ký đúng, dù tên biến là `errors`, kiểu interface có thể là `Errors` hoặc `BindingResult`. Với JSON, nhớ rằng lỗi parse xảy ra ở layer khác.

## 8. Ví dụ Spring Boot 3 và Java 17 chạy được

Ứng dụng minh họa cả `@ModelAttribute`, `@RequestBody`, `Errors`, `BindingResult`, lỗi field, lỗi global và test chính xác HTTP status/body. Dùng Spring Boot **3.3.13**, JDK **17+** và Maven.

Cấu trúc:

```text
mvc-binding-errors/
├── pom.xml
└── src/
    ├── main/java/example/
    │   ├── BindingErrorsApplication.java
    │   ├── SignupForm.java
    │   └── SignupController.java
    └── test/java/example/
        └── SignupControllerTest.java
```

### 8.1. File pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>mvc-binding-errors</artifactId>
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

Starter validation cung cấp Bean Validation implementation như Hibernate Validator trong môi trường Spring Boot 3. Mã import `jakarta.validation.*` và `jakarta.validation.constraints.*`.

### 8.2. File BindingErrorsApplication.java

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BindingErrorsApplication {
    public static void main(String[] args) {
        SpringApplication.run(BindingErrorsApplication.class, args);
    }
}
```

### 8.3. File SignupForm.java

```java
package example;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SignupForm {
    @NotBlank
    private String name;

    @NotNull
    @Min(18)
    private Integer age;

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

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
```

Giải thích:

- `@NotBlank` từ chối `null`, chuỗi rỗng hoặc chỉ có whitespace.
- `@NotNull` đảm bảo `age` phải có giá trị sau binding.
- `@Min(18)` áp dụng cho giá trị số đã chuyển kiểu thành công; `"abc"` là lỗi `typeMismatch` chứ không phải so sánh số nhỏ hơn 18.
- `@Email` kiểm tra chuỗi theo quy tắc email của provider; ví dụ `not-an-email` bị từ chối.

### 8.4. File SignupController.java

```java
package example;

import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/signup")
public class SignupController {

    @PostMapping(path = "/form",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> form(
            @Valid @ModelAttribute("signup") SignupForm form,
            BindingResult errors) {

        // Ví dụ quy tắc nghiệp vụ toàn object.
        if (!errors.hasErrors() && "admin".equalsIgnoreCase(form.getName())) {
            errors.reject("signup.nameReserved", "Tên đã được dành riêng");
        }

        if (errors.hasErrors()) {
            return ResponseEntity.badRequest().body(describe(errors));
        }

        return ResponseEntity.ok(describeSuccess(form));
    }

    @PostMapping(path = "/json",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> json(
            @Valid @RequestBody SignupForm form,
            Errors errors) {

        // Đây là lỗi validation trên DTO JSON đã deserialize.
        // JSON sai cú pháp có thể bị từ chối trước khi method này được gọi.
        if (errors.hasErrors()) {
            return ResponseEntity.badRequest().body(describe(errors));
        }

        return ResponseEntity.ok(describeSuccess(form));
    }

    private static String describe(Errors errors) {
        String fields = errors.getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));

        if (!fields.isEmpty()) {
            return "invalid:" + fields;
        }

        if (errors.hasGlobalErrors()) {
            return "invalid:global";
        }

        return "invalid:unknown";
    }

    private static String describeSuccess(SignupForm form) {
        return "created:" + form.getName()
                + "|" + form.getAge()
                + "|" + form.getEmail();
    }
}
```

Vì `Errors` chứa các API `hasErrors`, `getFieldErrors` và `hasGlobalErrors` nên helper `describe(Errors)` sử dụng được cho cả `BindingResult` ở form và `Errors` ở JSON. Chỉ controller form minh họa `reject(...)` cho lỗi global; đây là ví dụ giúp hiểu API, còn hệ thống thật thường đặt rule nhiều bước vào custom `Validator` hoặc service.

Helper trong ví dụ chỉ trả **tên field** sau khi sắp xếp để đầu ra ổn định; nó không phơi `rejectedValue`. Một API production nên trả thêm mã và thông điệp an toàn, như cấu trúc ở mục 6.

### 8.5. Lệnh chạy và output có thể kiểm chứng

Ở thư mục chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Từ terminal khác, gọi bốn request form sau (trên Linux/macOS hoặc shell hỗ trợ cách nối dòng bằng `\`; với PowerShell, dùng `curl.exe` và đặt lệnh thành một dòng nếu cần):

```bash
curl -s -X POST http://localhost:8080/signup/form \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "name=Ada" \
  --data-urlencode "age=21" \
  --data-urlencode "email=ada@example.com"

curl -s -X POST http://localhost:8080/signup/form \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "name=" \
  --data-urlencode "age=abc" \
  --data-urlencode "email=ada@example.com"

curl -s -X POST http://localhost:8080/signup/form \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "name=Ada" \
  --data-urlencode "age=15" \
  --data-urlencode "email=ada@example.com"

curl -s -X POST http://localhost:8080/signup/form \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "name=admin" \
  --data-urlencode "age=22" \
  --data-urlencode "email=admin@example.com"
```

Body kỳ vọng tương ứng (mỗi body không tự thêm newline):

```text
created:Ada|21|ada@example.com
invalid:age,name
invalid:age
invalid:global
```

Vì sao?

1. Form hợp lệ tạo object có `age=21` dạng số và trả HTTP **200**.
2. `name` rỗng vi phạm `NotBlank`, `age=abc` gây lỗi chuyển kiểu; cả hai được ghi vào `BindingResult` và trả **400** với hai tên field.
3. `age=15` chuyển kiểu thành công nhưng vi phạm `Min(18)`; trả **400** với field `age`.
4. `name=admin` hợp lệ theo Bean Validation nhưng controller đăng ký lỗi toàn object bằng `reject`; trả **400** `invalid:global`.

Gửi JSON hợp lệ và JSON đã parse được nhưng vi phạm email:

```bash
curl -s -X POST http://localhost:8080/signup/json \
  -H "Content-Type: application/json" \
  -d '{"name":"Ada","age":21,"email":"ada@example.com"}'

curl -s -X POST http://localhost:8080/signup/json \
  -H "Content-Type: application/json" \
  -d '{"name":"Ada","age":21,"email":"not-an-email"}'
```

Body kỳ vọng:

```text
created:Ada|21|ada@example.com
invalid:email
```

Request JSON với `"age":"abc"` không thể deserialize thành `Integer` sẽ thường nhận HTTP **400** qua `HttpMessageNotReadableException` **trước** lời gọi `SignupController.json`. Không kỳ vọng body `invalid:age` từ helper `describe` cho lỗi đó: đây là khác biệt quan trọng giữa JSON deserialize và form property binding.

### 8.6. MockMvc test: chứng minh hành vi chứ không đoán exception

File `src/test/java/example/SignupControllerTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SignupControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void validFormIsAccepted() throws Exception {
        mvc.perform(post("/signup/form")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("name", "Ada")
                    .param("age", "21")
                    .param("email", "ada@example.com"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                    "created:Ada|21|ada@example.com"));
    }

    @Test
    void bindingAndBeanValidationErrorsAreCollected() throws Exception {
        mvc.perform(post("/signup/form")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("name", "")
                    .param("age", "abc")
                    .param("email", "ada@example.com"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("invalid:age,name"));
    }

    @Test
    void numericConstraintIsDifferentFromConversionError() throws Exception {
        mvc.perform(post("/signup/form")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("name", "Ada")
                    .param("age", "15")
                    .param("email", "ada@example.com"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("invalid:age"));
    }

    @Test
    void globalErrorCanBeAddedToBindingResult() throws Exception {
        mvc.perform(post("/signup/form")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("name", "admin")
                    .param("age", "22")
                    .param("email", "admin@example.com"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("invalid:global"));
    }

    @Test
    void requestBodyValidationCanBeHandledLocally() throws Exception {
        mvc.perform(post("/signup/json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Ada\",\"age\":21,"
                        + "\"email\":\"not-an-email\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("invalid:email"));
    }

    @Test
    void malformedJsonConversionFailsBeforeControllerErrors() throws Exception {
        mvc.perform(post("/signup/json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Ada\",\"age\":\"abc\","
                        + "\"email\":\"ada@example.com\"}"))
            .andExpect(status().isBadRequest());
    }
}
```

Dùng `mvn test` chạy sáu test. Những test này kiểm tra: nhận lỗi tại controller khi có `BindingResult` sát argument, phân biệt type mismatch với constraint, object error tự thêm, Bean Validation sau deserialize JSON và JSON parse/type conversion failure ở layer message converter. Test cuối chỉ kiểm tra HTTP status vì body error mặc định tùy Spring Boot error handling, không phải chuỗi do `describe()` tạo.

Không đưa các chữ ký **cố ý sai** ở mục 4 vào project demo: chúng được trình bày để học cách xác định lỗi, không phải code ứng dụng nên triển khai.

## 9. Cách kiểm tra lỗi trong code production

Mẫu xử lý tối thiểu đúng:

```java
if (bindingResult.hasErrors()) {
    // Với form HTML: render lại view kèm input và messages.
    // Với API: chuẩn hóa FieldError/ObjectError thành body an toàn.
    return ...; // Chọn kiểu trả về phù hợp controller thực tế.
}

// Chỉ gọi service khi DTO đã qua binding/validation.
```

*Đây là pseudocode minh họa, không phải Java độc lập; dấu `...` cần thay bằng giá trị trả về hợp lệ.*

Nên tuân thủ thứ tự:

1. **Bind** dữ liệu sang DTO chuyên dụng. Tránh bind trực tiếp entity nhiều thuộc tính nhạy cảm.
2. **Validate** bằng `@Valid`/`@Validated` hoặc validator được cấu hình; với quy tắc nhiều field, ghi global/field error một cách chủ đích.
3. **Kiểm tra lỗi** trước khi gọi service. `hasErrors()` bao gồm field và global errors.
4. **Kiểm tra suppressed fields** khi đã dùng `allowedFields`: `getSuppressedFields()` là thông tin riêng trong `BindingResult`, có thể cần xử lý như request không hợp lệ ngay cả khi `hasErrors()` là false.
5. **Tạo response/view** với mã và thông điệp phù hợp; không công khai rejected values nhạy cảm và không dựa vào thứ tự tự nhiên của danh sách lỗi để làm API contract.
6. **Xử lý exception ngoài binding result** ở nơi thích hợp: JSON sai cú pháp, mismatch của tham số đơn, lỗi method-level validation, content type không phù hợp, v.v.

Với response lỗi kiểu RFC Problem Details, có thể chuẩn hóa thông qua `ProblemDetail`, `ResponseEntityExceptionHandler` và `@RestControllerAdvice`. Nếu tự viết `@ExceptionHandler` cho `MethodArgumentNotValidException` hoặc `HandlerMethodValidationException`, hãy giữ định dạng thống nhất với errors trả từ controller; mỗi ngoại lệ có cấu trúc dữ liệu hơi khác.

## 10. Các lỗi phổ biến khi học Errors và BindingResult

| Sai lầm | Hệ quả | Cách đúng |
| --- | --- | --- |
| Đặt `Model` hoặc `@RequestParam` giữa form và `BindingResult` | Không gắn đúng binding result | Đặt `BindingResult` **ngay sau** form DTO |
| Hai form nhưng chỉ một `BindingResult` cuối method | Không có kết quả nhận lỗi riêng cho từng form | Ghép từng cặp object/result liền nhau |
| Chỉ gọi `@Valid` nhưng bỏ qua `errors.hasErrors()` | Có thể tiếp tục lưu DTO sai | Kiểm tra trước nghiệp vụ |
| Tưởng `Errors` và `BindingResult` là hai object hoàn toàn độc lập | Dùng API rườm rà hoặc sai | `BindingResult extends Errors`; chọn interface theo nhu cầu |
| Dùng `getFieldError("x")` rồi gọi method không kiểm tra null | `NullPointerException` khi field hợp lệ | Kiểm tra `hasFieldErrors` hoặc `null` |
| Cho rằng `getFieldErrors()` bao gồm lỗi global | Bỏ sót quy tắc liên trường | Kiểm tra thêm `getGlobalErrors()`/`getAllErrors()` |
| Tưởng `BindingResult` bắt mọi lỗi từ JSON | Bỏ sót parse failure | Xử lý riêng `HttpMessageNotReadableException` |
| Tưởng `BindingResult` bắt lỗi `@RequestParam int` sai định dạng | Đặt receiver sai vị trí | Dùng exception handling cho mismatch tham số đơn |
| Không thêm validation starter | Constraint không được thực thi như dự kiến | Cài provider Bean Validation, kiểm thử bằng MockMvc |
| Trả `getRejectedValue()` trong lỗi public | Rò rỉ dữ liệu input | Chỉ trả metadata/message đã chuẩn hóa |
| Dùng `hasErrors()` để phát hiện suppressed fields | Không thấy field bị chặn | Gọi `getSuppressedFields()` nếu có allowlist |
| Redirect ngay khi form HTML lỗi | Mất binding state/messages ở request mới | Render lại form hoặc dùng flash attributes đúng cách |
| Dựa trên thứ tự `getFieldErrors()` để kiểm thử response | Test dễ thay đổi theo provider/cấu hình | Sắp xếp và/hoặc chuẩn hóa error code |

## 11. Tình huống luyện tập

**Tình huống A.** Method:

```java
@PostMapping("/register")
String register(@Valid @ModelAttribute("form") Form form,
                @RequestParam(required = false) String campaign,
                BindingResult result) {
    // ...
}
```

Vấn đề là gì? **Đáp án:** `BindingResult` bị tách khỏi `Form` bởi `campaign`. Chuyển `BindingResult result` lên ngay sau `Form form`, sau đó mới tới `@RequestParam`.

**Tình huống B.** Form gửi `age=seventeen`, DTO có `Integer age` và `@Min(18)`. Đây là lỗi gì? **Đáp án:** conversion/type mismatch khi chuyển `String` thành `Integer`, xảy ra trước khi có thể so `Min` trên số hợp lệ.

**Tình huống C.** DTO JSON có `@Email`; JSON `{"email":"invalid"}` vẫn deserialize thành `String`. Nếu method có `@Valid @RequestBody Dto dto, Errors errors` thì sao? **Đáp án:** method có thể nhận lỗi Bean Validation qua `Errors`. Nếu thiếu `Errors`, có thể có `MethodArgumentNotValidException` mặc định.

**Tình huống D.** JSON `{"age":"not-number"}` đi vào DTO có `Integer age`. `BindingResult` ngay sau `@RequestBody` có chắc bắt được không? **Không.** Jackson có thể thất bại khi deserialize và Spring ném `HttpMessageNotReadableException` trước khi method chạy.

**Tình huống E.** Không có field error nhưng `errors.reject("dateRange")` đã chạy. `hasErrors()` trả gì? **`true`**, vì global error cũng là một lỗi. `getFieldErrors()` vẫn có thể rỗng.

**Tình huống F.** `@Min(1) @RequestParam int page` vi phạm trong Spring MVC 6.1+. Có nên thêm `BindingResult` sau `page` như một form object? **Không.** Đây là constraint mức method parameter; cần hiểu `HandlerMethodValidationException` và cơ chế xử lý method validation.

## 12. Ghi nhớ và nguồn tra cứu

- `Errors` là API làm việc với lỗi; `BindingResult extends Errors`, bổ sung thông tin quá trình binding.
- Trong chữ ký controller, **mỗi `Errors`/`BindingResult` phải đặt sát ngay sau argument mà nó nhận lỗi**.
- Form property binding và JSON HTTP message conversion là hai luồng đầu vào khác nhau; lỗi xuất hiện trước khi controller chạy không mặc nhiên chảy vào `BindingResult` của controller.
- `@Valid` yêu cầu constraint validation; `BindingResult` quyết định cách nhận lỗi tại controller trong các tình huống được hỗ trợ; controller phải kiểm tra lỗi.
- Dùng `FieldError` cho field, `ObjectError` cho object; thiết kế response ổn định, an toàn và có thể bản địa hóa.

Tài liệu chính thức để tham khảo theo phiên bản Spring Framework 6.2:

1. [Spring MVC — Supported Method Arguments](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/arguments.html): ràng buộc vị trí `Errors`/`BindingResult` đối với đối tượng cần validation.
2. [Spring MVC — @ModelAttribute Method Arguments](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html): binding và xử lý lỗi của command object.
3. [Spring MVC — @RequestBody](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/requestbody.html): message converter, Bean Validation và Errors.
4. [Spring MVC — Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html): `MethodArgumentNotValidException`, `HandlerMethodValidationException` và method validation từ 6.1.
5. [Errors Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/validation/Errors.html): các API `hasErrors`, `getFieldErrors`, `reject` và `rejectValue`.
6. [BindingResult Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/validation/BindingResult.html): `getTarget`, `getSuppressedFields`, `getModel`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-validation-008">Phân biệt lỗi chuyển đổi kiểu, lỗi binding và lỗi kiểm tra dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt lỗi chuyển đổi kiểu, lỗi binding và lỗi kiểm tra dữ liệu trong Spring MVC

## 1. Mục tiêu và bức tranh tổng thể

Khi một request gửi dữ liệu tới controller, **lỗi chuyển đổi kiểu (type conversion)**, **lỗi binding (data binding)** và **lỗi kiểm tra dữ liệu (validation)** mô tả các vấn đề ở **những công đoạn liên quan nhưng khác nhau**. Một response `400 Bad Request` không tự cho biết công đoạn nào thất bại. Bạn cần đọc loại tham số controller, exception, `BindingResult` và cấu hình để chẩn đoán chính xác.

Sau bài này, bạn có thể:

- Mô tả luồng nhận request từ chuỗi đầu vào, chuyển kiểu, gắn vào object rồi áp constraint nghiệp vụ.
- Phân biệt conversion error của `@RequestParam`/`@PathVariable` với field binding error của `@ModelAttribute`.
- Phân biệt lỗi parse/deserialization JSON của `@RequestBody` với lỗi Jakarta Bean Validation xảy ra *sau khi* tạo object.
- Hiểu `BindingResult`, `FieldError`, `ObjectError`, `typeMismatch`, `@Valid`, `@Validated` và cơ chế báo lỗi mặc định.
- Chọn cách trả lỗi HTTP, kiểm thử theo giai đoạn và tránh để dữ liệu lỗi đi vào nghiệp vụ.

**Phạm vi:** Spring MVC trên Servlet stack, Java 17+, Spring Framework 6.1+/Spring Boot 3.x với Jakarta Validation. Một số chi tiết exception thay đổi giữa phiên bản hoặc phụ thuộc handler tùy chỉnh; bài sẽ chỉ rõ các trường hợp phổ biến và ngoại lệ quan trọng. Đây không phải mô tả riêng cho Spring WebFlux.

## 2. Ba lớp xử lý: conversion, binding, validation

### 2.1. Luồng tư duy

```text
HTTP request
  |
  +--> Tìm handler và resolver phù hợp
  |       (@RequestParam / @PathVariable / @ModelAttribute / @RequestBody)
  |
  +--> Đọc dữ liệu đầu vào
  |       query/form/header/path thường là chuỗi;
  |       JSON body là byte -> JSON token
  |
  +--> Chuyển đổi dữ liệu
  |       "42" -> Integer(42)
  |       "2026-10-21" -> LocalDate
  |       JSON -> object Java qua HttpMessageConverter/Jackson
  |
  +--> Binding nếu là model attribute
  |       gắn các property vào target object;
  |       ghi lỗi property trong BindingResult
  |
  +--> Validation nếu được kích hoạt
  |       @NotBlank, @Min, @NotNull, custom validator,
  |       constraint cho nhiều field hoặc method parameter
  |
  +--> Handler controller và business logic khi hợp lệ
```

Đây là sơ đồ **khái niệm**, không phải mọi loại tham số đều trải qua đủ tất cả các bước. Ví dụ `@RequestParam Integer` được một argument resolver chuyển kiểu trực tiếp, không cần một object form để bind; `@RequestBody` dùng `HttpMessageConverter`/Jackson để deserialize JSON, không bind JSON bằng cùng cơ chế property của `@ModelAttribute`.

### 2.2. Định nghĩa chính xác

**Chuyển đổi kiểu (conversion)**: biến một giá trị nguồn thành kiểu Java đích, thường qua `ConversionService`, `Converter`, `Formatter` hoặc `PropertyEditor`. Ví dụ `"abc"` không đổi được thành `Integer`, còn chuỗi ngày sai format không đổi được thành `LocalDate`. Conversion là một **bước** có thể xảy ra trong binding.

**Data binding**: nối các giá trị request với các tham số constructor/thuộc tính của object, thường do `WebDataBinder` thực hiện với `@ModelAttribute`. Binding bao gồm việc xác định property, áp conversion và ghi nhận các vấn đề phát sinh. Vì thế **một conversion error có thể được biểu diễn như một binding error** ở field cụ thể, chứ không phải hai lỗi loại trừ nhau.

**Validation**: xét dữ liệu đã có kiểu Java theo các ràng buộc hợp lệ. Ví dụ `Integer age = 0` là giá trị hoàn toàn chuyển kiểu được nhưng vi phạm `@Min(1)`; `name = ""` vẫn là `String` hợp lệ theo Java nhưng vi phạm `@NotBlank`. Spring hỗ trợ Jakarta Bean Validation qua `@Valid`/`@Validated` và Spring `Validator`.

### 2.3. So sánh nhanh

| Tình huống | Bước thất bại thường gặp | Minh họa | Kết quả HTTP thường gặp |
| --- | --- | --- | --- |
| Query `?age=abc` gắn vào `@RequestParam Integer age` | Type conversion của method argument | Chuỗi không thể thành Integer | `400`, thường `MethodArgumentTypeMismatchException` |
| Form `age=abc` gắn vào `@ModelAttribute Form.age` | Field conversion **trong data binding** | `BindingResult` có `FieldError` và mã `typeMismatch` | Controller tự xử lý nếu có `BindingResult`; nếu không, thường `400` |
| Form có property không thể ghi hoặc constructor không bind được | Binding/khởi tạo object | Setter, constructor, field không khớp | Tùy kiểu lỗi/cấu hình; có thể thành lỗi binding hoặc exception khác |
| Form `age=0` và `@Min(1)` | Validation | Giá trị Integer hợp lệ nhưng sai constraint | Có `FieldError` mã `Min`, hoặc mặc định `400` |
| JSON `{"age":"oops"}` với `Integer age` | JSON deserialization | Jackson không tạo được object | Thường `400`, `HttpMessageNotReadableException` |
| JSON `{"age":0}` với `@Min(1)` | Validation sau deserialize | Object được tạo nhưng vi phạm constraint | `400`, thường `MethodArgumentNotValidException` |
| Query `?age=0` với constraint `@Min(1)` trực tiếp trên tham số | Handler **method validation** | Giá trị chuyển kiểu được, vi phạm constraint method | Spring MVC 6.1+ thường `HandlerMethodValidationException`, `400` cho lỗi đầu vào |

**Không nên viết quy tắc “conversion = một exception, binding = một exception, validation = một exception” cho mọi kiểu controller argument.** Exception phụ thuộc *nguồn dữ liệu* và *giai đoạn*.

## 3. Conversion: lỗi biến chuỗi thành kiểu Java

### 3.1. Tham số đơn giản: `@RequestParam` và `@PathVariable`

```java
@GetMapping("/products")
public String products(@RequestParam("page") int page) {
    return "page=" + page;
}
```

| Request | Diễn giải | Kết quả phổ biến |
| --- | --- | --- |
| `GET /products?page=3` | `"3"` chuyển thành `int 3` | Controller chạy |
| `GET /products?page=abc` | Không parse được số | `400`: conversion error |
| `GET /products` | Thiếu parameter required | `400`: thiếu parameter, **không phải conversion** |
| `GET /products?page=` | Rỗng có thể chuyển thành null; `int` không nhận null | `400`, có thể được mô tả là thiếu sau conversion |
| `GET /products?page=-1` | Chuyển số `-1` thành công | Controller chạy nếu chưa bật constraint `@Min(1)` |

Các exception thường gặp:

- `MethodArgumentTypeMismatchException`: conversion thất bại cho một method argument như `@RequestParam` hoặc `@PathVariable`.
- `MissingServletRequestParameterException`: thiếu tham số bắt buộc; Spring cũng có cơ chế phân biệt trường hợp **có tên parameter nhưng trở thành null sau conversion**.
- `MissingPathVariableException`: một URI template variable bắt buộc không resolve được; cần phân biệt với URL không match handler.
- `TypeMismatchException` của Spring là loại nền tảng cho lỗi chuyển kiểu; không nên mặc định ngoại lệ quan sát được tại tất cả tầng luôn là một subclass cố định.

Một URL `/products/abc` với `@PathVariable Long id` thường thất bại lúc convert thành Long; ngược lại `/products` có thể không khớp mapping `/products/{id}` và trả **404**, trước khi method argument conversion xảy ra.

### 3.2. Ngày giờ, enum và formatter

```java
@GetMapping("/reports")
public String report(
        @RequestParam
        @org.springframework.format.annotation.DateTimeFormat(iso =
                org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
        java.time.LocalDate from) {
    return from.toString();
}
```

`?from=2026-10-21` hợp lệ; `?from=tomorrow` hoặc `?from=2026-99-99` không đúng định dạng/giá trị ngày và thường gây 400 trước khi gọi controller. Với enum, tên giá trị thường phân biệt hoa/thường theo converter mặc định, trừ khi bạn đăng ký converter tùy chỉnh.

Chỉnh cách chuyển kiểu bằng `FormattingConversionService`/`Converter`/`Formatter` toàn cục hoặc `@InitBinder`/`WebDataBinder` cho phạm vi controller. Không viết custom converter để “nuốt lỗi” rồi trả `null` nếu ứng dụng cần biết input không hợp lệ; việc đó dễ biến conversion error thành missing.

### 3.3. Lỗi kiểu dữ liệu và lỗi nghiệp vụ khác nhau

`?amount=abc` không thể parse thành `BigDecimal` là **conversion error**. `?amount=-50` chuyển thành số thành công; nếu giao ước yêu cầu amount dương thì đây là **validation error**. `?currency=VND` parse thành enum hợp lệ nhưng tài khoản không hỗ trợ VND có thể là **business rule error**, nằm ngoài hai loại trên. Không suy ra mọi `400` là Bean Validation.

## 4. Data binding: gắn form/query vào object

### 4.1. `@ModelAttribute` đi qua `WebDataBinder`

```java
public class RegisterForm {
    private String email;
    private Integer age;

    public RegisterForm() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
}
```

```java
@PostMapping("/register-form")
public String register(
        @ModelAttribute("form") RegisterForm form,
        BindingResult errors) {
    if (errors.hasErrors()) {
        return "INVALID";
    }
    return "OK";
}
```

Đây là **hai đoạn minh họa**, giả định các import Spring cần thiết. Request form với `email=lan@example.com&age=abc` có thể tạo object `RegisterForm`, gắn `email` thành công, nhưng conversion sang `Integer age` lỗi. `BindingResult` chứa `FieldError` cho `age`, thường có code chính `typeMismatch`; handler trên vẫn được gọi vì đã đặt `BindingResult` **ngay sau** `@ModelAttribute`.

Nếu form có `age=20`, binder gắn vào `Integer` thành công; **không có validation ràng buộc tuổi** chỉ vì field tên là `age`. Cần khai báo constraint và kích hoạt validation nếu muốn giới hạn.

Trường `BindingResult` bao gồm dữ liệu quan trọng:

- `hasErrors()`, `getErrorCount()`: có lỗi hay không, số lỗi.
- `getFieldErrors()`: các `FieldError` liên quan property, ví dụ `age`.
- `getGlobalErrors()`: `ObjectError` ở mức toàn object, ví dụ mối quan hệ giữa hai field.
- `FieldError.getField()`: tên property như `"age"`.
- `FieldError.getCode()`: mã lỗi tổng quát, ví dụ `typeMismatch` hoặc `Min`; `getCodes()` có danh sách mã có độ ưu tiên từ cụ thể tới chung.
- `FieldError.getRejectedValue()`: giá trị bị từ chối; **không tự đưa nguyên giá trị này vào log/response** vì có thể chứa dữ liệu nhạy cảm.
- `FieldError.isBindingFailure()`: đánh dấu lỗi liên quan binding; lỗi constraint validation thường có `isBindingFailure() == false`. Đây là dấu hiệu hữu ích, nhưng đừng dùng một flag đơn độc để quyết định tất cả lỗi hạ tầng.
- `getSuppressedFields()`: danh sách field bị bỏ qua do không được phép bind; **không nhất thiết tạo FieldError**. Kiểm tra khi có cơ chế allowlist để tránh mass assignment.

### 4.2. Binding gồm nhiều hơn lỗi parse số

Một data binding failure còn có thể liên quan tên property không hợp lệ, setter/constructor, nested field, indexed property, required field do `WebDataBinder` cấu hình hoặc conversion/formatting. Với `ignoreUnknownFields` mặc định của `DataBinder`, field lạ có thể bị bỏ qua thay vì thành `FieldError`. Tùy chính sách `allowedFields`/`disallowedFields`, field không được bind có thể bị suppressed mà **không được xem là validation error**.

Nếu request là API nhận form, nên dùng DTO input riêng với những field được phép sửa. Đừng bind trực tiếp vào JPA entity chứa `id`, `role`, `isAdmin` hoặc các field nhạy cảm mà client không được đổi; kết hợp allowlist và kiểm tra `getSuppressedFields()` nếu cần.

### 4.3. `BindingResult` phải nằm ngay sau argument tương ứng

Đúng:

```java
@PostMapping("/save")
public String save(@Valid @ModelAttribute("form") RegisterForm form,
                   BindingResult result) {
    return result.hasErrors() ? "INVALID" : "SAVED";
}
```

Sai vì không đặt cạnh đối tượng được binding/validation:

```java
// Ví dụ KHÔNG hợp lệ: đừng sao chép vào controller thực tế.
@PostMapping("/save")
public String save(@Valid @ModelAttribute("form") RegisterForm form,
                   @RequestParam String source,
                   BindingResult result) {
    return "UNRELIABLE";
}
```

Spring yêu cầu `Errors` hoặc `BindingResult` **ngay sau** tham số được bind/validate tương ứng. Nếu có nhiều model attribute, mỗi `BindingResult` phải theo ngay sau đúng đối tượng. Khi đặt sai, có thể gặp `IllegalStateException` liên quan đến BindingResult/Errors hoặc framework ném exception thay vì đưa lỗi vào argument mong muốn.

### 4.4. Không có `BindingResult` thì sao?

Với Spring MVC hiện đại, binding/validation error của một `@ModelAttribute` có thể dẫn đến `MethodArgumentNotValidException` và thường status 400 khi không có `BindingResult`/`Errors` phù hợp. Trong tài liệu cũ hoặc đường xử lý đặc thù, bạn có thể gặp `BindException`. **Đừng tự cố định một lớp exception cho mọi phiên bản Spring và mọi kiểu argument.** Với ứng dụng dùng template MVC, việc thêm `BindingResult` cho phép hiển thị lỗi bên cạnh form thay vì nhận 400 mặc định.

## 5. Validation: dữ liệu có kiểu hợp lệ nhưng vi phạm constraint

### 5.1. Cần khai báo và kích hoạt ràng buộc

Ví dụ dùng Jakarta Bean Validation:

```java
public record CreateUser(
        @jakarta.validation.constraints.NotBlank String name,
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.Min(18) Integer age) {}
```

- `name=""` hoặc `name="   "`: chuyển thành String được, nhưng `@NotBlank` thất bại.
- `age=17`: chuyển thành Integer được, nhưng `@Min(18)` thất bại.
- `age=null`: vi phạm `@NotNull`. Riêng `@Min` thường coi null là hợp lệ; cần phối hợp `@NotNull` nếu bắt buộc phải có tuổi.
- `age="abc"` trong JSON: có thể thất bại lúc Jackson deserialize, **trước** khi `@Min` đánh giá được một số nguyên.
- `@Valid` kích hoạt kiểm tra constraint ở argument thích hợp; đặt annotation constraint lên DTO nhưng không kích hoạt validation đúng cách có thể khiến request sai vẫn qua controller.

Dependency thường cần trong Spring Boot 3: `spring-boot-starter-validation`. Spring MVC Web dependency không mặc định đảm bảo mọi provider validation đã sẵn sàng. Khi dùng Jakarta Validation trong Boot 3, import `jakarta.validation.*`, không dùng `javax.validation.*` từ các ví dụ Boot 2 cũ.

### 5.2. `@NotNull`, `@NotEmpty`, `@NotBlank`

| Constraint | null | Chuỗi `""` | Chuỗi `"   "` |
| --- | --- | --- | --- |
| `@NotNull` | Không hợp lệ | Hợp lệ | Hợp lệ |
| `@NotEmpty` | Không hợp lệ | Không hợp lệ | Hợp lệ |
| `@NotBlank` | Không hợp lệ | Không hợp lệ | Không hợp lệ |

`@NotEmpty` áp dụng cho những kiểu container/`CharSequence` được hỗ trợ, trong khi `@NotBlank` dùng cho chuỗi. `@Size(min=1)` riêng lẻ không có nghĩa “không null”; thông thường null vẫn qua constraint `@Size`.

### 5.3. Field-level và object-level errors

Một constraint trên property thường tạo `FieldError` với code như `NotBlank` hoặc `Min`. Constraint ở mức class, chẳng hạn “ngày kết thúc phải sau ngày bắt đầu”, có thể tạo `ObjectError`/global error. Giao diện API nên có khả năng biểu diễn cả **field errors** và **global errors**, tránh giả định mọi lỗi có một tên field.

### 5.4. Validation groups

`@Validated(Create.class)`/`@Validated(Update.class)` cho phép áp những nhóm constraint phù hợp với thao tác tạo/cập nhật. Tránh ép yêu cầu “tạo mới phải có mọi field” lên PATCH vốn cho phép bỏ qua field. Groups không thay thế việc phân biệt trường JSON **vắng mặt** và **explicit null**: khi DTO đã deserialize thành cùng Java null, validator không thể tự khôi phục sự hiện diện của field.

## 6. `@RequestBody`: deserialization là một đường xử lý khác

Ví dụ:

```java
@PostMapping(path = "/users", consumes = "application/json")
public String create(@Valid @RequestBody CreateUser input) {
    return "OK";
}
```

Khi có Jackson, `MappingJackson2HttpMessageConverter` đọc JSON rồi chuyển thành `CreateUser`. Quá trình này **không đồng nhất** với việc `WebDataBinder` gắn `age` từ form parameter.

| JSON body | Giai đoạn | Kết quả thường thấy |
| --- | --- | --- |
| `{"name":"Lan","age":25}` | Deserialize và validate đều thành công | Handler chạy |
| `{"name":"Lan","age":"oops"}` | Jackson không chuyển `"oops"` thành Integer | `HttpMessageNotReadableException`, 400 |
| `{"name":"Lan","age":17}` | Deserialize thành công, `@Min(18)` lỗi | `MethodArgumentNotValidException`, 400 |
| `{"name":"","age":25}` | Deserialize thành công, `@NotBlank` lỗi | `MethodArgumentNotValidException`, 400 |
| `{"name":"Lan","age":25` | JSON sai cú pháp | `HttpMessageNotReadableException`, 400 |
| Không gửi body với `@RequestBody` bắt buộc | Thiếu nội dung request body | Thường `HttpMessageNotReadableException`, 400 |
| Gửi `text/plain` tới endpoint chỉ `consumes="application/json"` | Media type không được hỗ trợ | `415 Unsupported Media Type`, **không phải validation** |

Nếu ứng dụng cấu hình Jackson custom coercion (ví dụ đổi chuỗi số thành số, hay coi chuỗi rỗng là null), kết quả có thể khác. Hãy kiểm tra ObjectMapper thực tế. Không bắt `HttpMessageNotReadableException` rồi mô tả mọi trường hợp là “vi phạm `@Min`”; validation chưa chắc đã chạy.

**`@Valid @RequestBody DTO, BindingResult`** được Spring MVC hỗ trợ nếu đặt `BindingResult` ngay sau DTO, cho phép controller nhận lỗi validation của object đã đọc thành công. Nhưng khi JSON không deserialize được, controller thường **không có object** để kèm `BindingResult`; request vẫn lỗi trước khi tới method.

## 7. Method validation: điểm khác biệt từ Spring MVC 6.1+

Có hai chế độ quan trọng:

1. **Argument validation**: `@Valid` hoặc `@Validated` trên đối tượng `@RequestBody`, `@ModelAttribute`, `@RequestPart`, và không cần method validation do constraint trực tiếp trên tham số. Lỗi mặc định thường là `MethodArgumentNotValidException` nếu không có `BindingResult` ngay sau đối tượng.
2. **Method validation**: constraint như `@Min`, `@NotBlank`, `@NotNull` đặt **trực tiếp** trên controller method parameter (hoặc return value) khiến Spring MVC 6.1+ xác thực ở mức phương thức. Lỗi đầu vào thường là `HandlerMethodValidationException`, thường HTTP 400. Method validation bao gồm cả các ràng buộc lồng qua `@Valid` khi cần.

Ví dụ:

```java
@GetMapping("/min-page")
public String minPage(
        @RequestParam("page")
        @jakarta.validation.constraints.Min(1) int page) {
    return "page=" + page;
}
```

- `?page=abc`: conversion thất bại **trước validation**.
- `?page=0`: conversion thành int thành công rồi `@Min(1)` lỗi; Spring MVC 6.1+ thường ném `HandlerMethodValidationException`.
- `?page=2`: hợp lệ.

`@Valid` **không phải** constraint trực tiếp để tự kích hoạt method validation; nó dùng để cascade constraint bên trong object. Ngược lại `@NotNull` là một constraint. Đặc biệt, nếu controller đặt `@Validated` ở **mức class**, một số phiên bản/config sẽ dùng AOP method validation qua proxy thay vì cơ chế built-in của Spring MVC 6.1+, dẫn đến exception khác (ví dụ `ConstraintViolationException`) và cách mapping HTTP khác. Khi muốn dùng cơ chế MVC 6.1+, hãy kiểm tra việc bỏ `@Validated` class-level theo tài liệu phiên bản đang chạy. Không gộp mọi lỗi validation vào một `@ExceptionHandler(MethodArgumentNotValidException.class)` rồi cho rằng đã bao phủ hết.

## 8. Ví dụ Spring Boot 3 + Java 17+ có thể chạy

Ứng dụng ví dụ phân biệt ba đường xử lý trong một controller:

- `GET /input-errors/query?age=abc`: conversion lỗi ở method argument.
- `POST /input-errors/form` với `age=abc`: binding error được giữ trong `BindingResult`.
- `POST /input-errors/form` với `age=0`: validation error (`@Min`), binder đã chuyển kiểu thành công.
- `POST /input-errors/json` với JSON không chuyển được kiểu: deserialize lỗi.
- `POST /input-errors/json` với tuổi `0`: Bean Validation lỗi.
- `GET /input-errors/method?age=0`: direct method constraint lỗi trên Spring MVC 6.1+.

Cấu trúc Maven Spring Boot 3.x, package `com.example.demo`; cần `spring-boot-starter-web` và `spring-boot-starter-validation`.

Tạo `src/main/java/com/example/demo/DemoApplication.java`:

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

Tạo `src/main/java/com/example/demo/InputErrorController.java`:

```java
package com.example.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/input-errors")
public class InputErrorController {

    @GetMapping("/query")
    public Map<String, Integer> query(@RequestParam("age") Integer age) {
        return Map.of("age", age);
    }

    @GetMapping("/method")
    public Map<String, Integer> method(
            @RequestParam("age") @Min(1) int age) {
        return Map.of("age", age);
    }

    @PostMapping(path = "/form",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> form(
            @Valid @ModelAttribute("form") FormInput input,
            BindingResult binding) {

        if (binding.hasErrors()) {
            List<FieldIssue> issues = binding.getFieldErrors()
                    .stream()
                    .map(this::toIssue)
                    .toList();
            return ResponseEntity.badRequest()
                    .body(new ErrorPayload("INVALID_FORM", issues));
        }

        return ResponseEntity.ok(Map.of("status", "ACCEPTED"));
    }

    @PostMapping(path = "/json",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> json(
            @Valid @RequestBody JsonInput input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "CREATED"));
    }

    private FieldIssue toIssue(FieldError error) {
        // Không đưa rejectedValue hoặc thông tin nhạy cảm ra client.
        return new FieldIssue(error.getField(), error.getCode());
    }

    public record FieldIssue(String field, String code) {}
    public record ErrorPayload(String error, List<FieldIssue> fields) {}

    public static class FormInput {
        @NotBlank
        private String name;

        @NotNull
        @Min(1)
        private Integer age;

        public FormInput() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
    }

    public record JsonInput(
            @NotBlank String name,
            @NotNull @Min(1) Integer age) {}
}
```

Giải thích code:

- `@RequestParam Integer age`: Spring chuyển String parameter thành Integer **trước khi** gọi `query`.
- `@ModelAttribute FormInput`: binder gắn các property; với `age=abc`, `FieldError` thường có `getCode() == "typeMismatch"`. Với `age=0`, lỗi validation thường có code `Min`.
- `@Valid` được áp trên object form; `BindingResult` ngay sau object cho phép tự trả JSON lỗi có cấu trúc. `ResponseEntity.badRequest()` là lựa chọn của **ứng dụng**, không phải hành vi bắt buộc khi nhận `BindingResult`.
- `@Valid @RequestBody JsonInput`: nếu Jackson đọc được body nhưng Bean Validation thất bại, chưa có `BindingResult` ngay sau nên framework xử lý thành 400.
- `@Min(1)` gắn trực tiếp lên parameter `age` ở `method` kích hoạt method validation trên Spring MVC 6.1+ với cấu hình mặc định.

Cần lưu ý `ErrorPayload` trên chỉ biểu diễn **field errors** cho demo. Sản phẩm thực nên còn hỗ trợ `getGlobalErrors()`, mã theo hợp đồng API, message được nội địa hóa và một request correlation id; không expose nguyên exception hay dữ liệu bí mật.

Chạy từ thư mục dự án Maven có wrapper:

```bash
./mvnw spring-boot:run
```

Windows PowerShell: `.\mvnw.cmd spring-boot:run`. Cổng mặc định là 8080 nếu không cấu hình khác. Lớp controller không thể biên dịch bằng lệnh `javac InputErrorController.java` độc lập vì cần Spring/Jakarta dependencies. Mã minh họa được viết để biên dịch trong dự án Boot có đúng dependency nêu trên.

## 9. Dùng curl để thấy ranh giới các lỗi

Các lệnh `curl` dưới đây chạy khi ứng dụng đã khởi động ở `http://localhost:8080`.

### 9.1. Conversion ở `@RequestParam`

```bash
curl -i "http://localhost:8080/input-errors/query?age=abc"
```

Thường nhận **400 Bad Request**, method `query` không chạy; Spring thường báo `MethodArgumentTypeMismatchException` ở phía server.

```bash
curl -i "http://localhost:8080/input-errors/query?age=20"
```

Nhận **200 OK**, body JSON chứa `"age":20`.

### 9.2. Conversion bên trong form binding

```bash
curl -i "http://localhost:8080/input-errors/form" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data "name=Lan&age=abc"
```

Controller `form` chạy vì có `BindingResult` ngay sau `FormInput`; ví dụ trả **400** cùng JSON có ý nghĩa như:

```json
{
  "error": "INVALID_FORM",
  "fields": [
    {"field": "age", "code": "typeMismatch"}
  ]
}
```

**Ví dụ body có thể chứa thêm lỗi field khác** tùy cách validation chạy và cấu hình; test nên tìm mã `typeMismatch`, không bắt số lượng/thứ tự lỗi tuyệt đối. `age=abc` không có cách nào thành Integer; đây là conversion failure được ghi trong `BindingResult`.

### 9.3. Validation của form đã bind được

```bash
curl -i "http://localhost:8080/input-errors/form" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data "name=Lan&age=0"
```

`age=0` chuyển kiểu **thành công**; `@Min(1)` lỗi. Ví dụ trả **400** với field code `Min`. Tương tự `name=` gây code `NotBlank`. Hai lỗi này không được gọi là `typeMismatch`.

### 9.4. JSON không deserialize được

```bash
curl -i "http://localhost:8080/input-errors/json" \
  -H "Content-Type: application/json" \
  --data '{"name":"Lan","age":"abc"}'
```

Thường **400 Bad Request**, `HttpMessageNotReadableException`, không có `FieldError` của form trong `form`. Nếu body JSON **sai cú pháp**, cùng nhánh đọc dữ liệu có thể báo lỗi trước validation.

### 9.5. JSON hợp lệ nhưng constraint sai

```bash
curl -i "http://localhost:8080/input-errors/json" \
  -H "Content-Type: application/json" \
  --data '{"name":"Lan","age":0}'
```

Thường **400 Bad Request**, `MethodArgumentNotValidException`. JSON đọc được, nhưng `@Min(1)` lỗi.

```bash
curl -i "http://localhost:8080/input-errors/json" \
  -H "Content-Type: application/json" \
  --data '{"name":"Lan","age":20}'
```

Nhận **201 Created** và JSON:

```json
{"status":"CREATED"}
```

### 9.6. Method validation

```bash
curl -i "http://localhost:8080/input-errors/method?age=0"
```

Trên Spring MVC 6.1+ với cơ chế built-in, constraint `@Min(1)` của method parameter thất bại: thường 400 với `HandlerMethodValidationException`. Nếu gửi `age=abc`, đây lại là **type conversion error**, chưa tới constraint.

Gợi ý Windows: dùng `curl.exe` một dòng khi PowerShell cũ ánh xạ `curl` thành alias; dấu `\` ở cuối dòng trong các ví dụ là cú pháp nối dòng của bash/zsh.

## 10. Kiểm thử MockMvc để nhận diện đúng loại lỗi

Đặt `src/test/java/com/example/demo/InputErrorControllerTest.java` với dependency `spring-boot-starter-test` và `spring-boot-starter-validation`. Test không giả định body lỗi Spring Boot mặc định giống nhau trên mọi phiên bản. Khi form có nhiều lỗi, dùng matcher tìm theo code thay vì phụ thuộc thứ tự.

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InputErrorController.class)
class InputErrorControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void queryConversionFailsBeforeController() throws Exception {
        mvc.perform(get("/input-errors/query").param("age", "abc"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/input-errors/query").param("age", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").value(20));
    }

    @Test
    void formConversionFailureAppearsAsBindingError() throws Exception {
        mvc.perform(post("/input-errors/form")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Lan")
                .param("age", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_FORM"))
            .andExpect(jsonPath("$.fields[*].code",
                    hasItem("typeMismatch")));
    }

    @Test
    void formValidationHasDifferentCode() throws Exception {
        mvc.perform(post("/input-errors/form")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Lan")
                .param("age", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields[*].code", hasItem("Min")));
    }

    @Test
    void jsonDeserializationErrorIs400() throws Exception {
        mvc.perform(post("/input-errors/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lan\",\"age\":\"abc\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void jsonValidationErrorIs400() throws Exception {
        mvc.perform(post("/input-errors/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lan\",\"age\":0}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void directParameterConstraintRejectsZero() throws Exception {
        mvc.perform(get("/input-errors/method").param("age", "0"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void validJsonRequestCanCreate() throws Exception {
        mvc.perform(post("/input-errors/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lan\",\"age\":20}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("CREATED"));
    }
}
```

Chạy:

```bash
./mvnw test
```

Windows: `.\mvnw.cmd test`. Đây là kiểm thử **đề xuất để chạy trong dự án mẫu có dependency phù hợp**; status có thể bị Spring Security/filter/controller advice tùy chỉnh thay đổi. Nếu ứng dụng có security bắt buộc xác thực/CSRF, cấu hình test security hoặc mock user hợp lệ trước khi diễn giải lỗi input.

Muốn kiểm chứng chính xác exception class trong một phiên bản, có thể kết hợp `andExpect(result -> ...)` trên `MvcResult.getResolvedException()` trong một test riêng, nhưng chỉ khi exception do MockMvc resolver xử lý. Với ví dụ production, nên kiểm tra **mã lỗi công khai của API** thay vì ràng buộc client vào Java class nội bộ.

## 11. Exception, status và xử lý tập trung

Các exception thường đáng nhận biết trong Spring MVC:

| Loại | Nguyên nhân điển hình | Phân loại hữu ích |
| --- | --- | --- |
| `MissingServletRequestParameterException` | Thiếu query/form parameter required | Thiếu input |
| `MethodArgumentTypeMismatchException` | `@RequestParam`/`@PathVariable` không convert được | Conversion |
| `MethodArgumentNotValidException` | Validation của `@RequestBody`, `@ModelAttribute` hoặc binding/validation argument không có `BindingResult` | Binding/argument validation |
| `HandlerMethodValidationException` | Constraint trực tiếp trên handler method parameter, Spring MVC 6.1+ | Method validation |
| `HttpMessageNotReadableException` | JSON body parse/deserialize lỗi hoặc body bắt buộc không đọc được | Message conversion |
| `HttpMediaTypeNotSupportedException` | Request `Content-Type` không được endpoint/converter hỗ trợ | HTTP content type, thường `415` |
| `BindException` / `TypeMismatchException` | Binding/conversion theo đường xử lý cụ thể, đặc biệt khi làm việc với binder riêng hoặc phiên bản khác | Xem vị trí và phiên bản |

**Không tất cả đều là 400**: `415` là vấn đề media type; lỗi phân quyền có thể là `401`/`403`; handler không tồn tại có thể là `404`. Một `ConstraintViolationException` từ service hoặc database cũng không tự động có nghĩa HTTP 400: có thể là lỗi nghiệp vụ/hệ thống, cần phân loại theo nguồn gốc.

Spring MVC có `DefaultHandlerExceptionResolver` và `ResponseEntityExceptionHandler` hỗ trợ xử lý nhiều exception mặc định. Với API REST, có thể mở rộng `ResponseEntityExceptionHandler` trong `@RestControllerAdvice` và override handler phù hợp để xuất dạng lỗi nhất quán (ví dụ `ProblemDetail` theo RFC 9457). Nhưng chữ ký override thay đổi theo phiên bản Spring; đọc đúng Javadoc release trước khi sao chép. Khi dùng `@ExceptionHandler` tùy chỉnh, phải đảm bảo không trả 200 cho input lỗi và không vô tình nuốt exception hạ tầng thành 400.

Một response lỗi hợp lý có thể chứa mã lỗi ổn định, loại lỗi, path field, thông điệp đã kiểm soát và request id; **không** đưa stack trace, tên class nội bộ, giá trị mật khẩu, access token hoặc raw SQL ra client. Dùng `MessageSource`/validation message bundles nếu ứng dụng cần nhiều ngôn ngữ. Phân biệt giữa code dùng để xử lý (như `INVALID_FIELD`) và message dành cho người dùng.

### 11.1. Đọc `BindingResult` chuẩn hơn chỉ nhìn status

```java
if (binding.hasErrors()) {
    for (org.springframework.validation.FieldError field :
            binding.getFieldErrors()) {
        String name = field.getField();
        String code = field.getCode();
        boolean conversionOrBinding = field.isBindingFailure();

        // Ghi log/định tuyến bằng name + code ở mức an toàn.
        // Không log giá trị người dùng nhập nếu có dữ liệu nhạy cảm.
    }

    for (org.springframework.validation.ObjectError global :
            binding.getGlobalErrors()) {
        String code = global.getCode();
        // Lỗi liên quan toàn object (ví dụ hai ngày không hợp lệ cùng nhau).
    }
}
```

Đây là đoạn minh họa **bên trong method đã khai báo `BindingResult binding`**, không phải một chương trình Java độc lập. `FieldError.getCode()` thường là mã ngắn (ví dụ `Min`), còn `getCodes()` cung cấp những mã cụ thể kèm object/field/type để phục vụ `MessageSource`.

## 12. Các bẫy thực tế

1. **Lỗi conversion không nhất thiết nằm ngoài binding.** Trong `@ModelAttribute`, conversion thành Integer thất bại được ghi nhận như field binding error, thường với `typeMismatch`.
2. **Không bật `@Valid` thì không nên mong constraint chạy.** Đối tượng có annotation `@Min` không tự bị từ chối trong mọi ngữ cảnh nếu không có validation được kích hoạt.
3. **Validation không sửa lỗi JSON không đọc được.** Nếu Jackson thất bại trước khi có object, `@NotBlank`/`@Min` chưa có điều kiện đánh giá.
4. **`BindingResult` nằm sai vị trí gây sai hành vi.** Đặt ngay sau argument được validation/binding tương ứng.
5. **Một field có thể có nhiều lý do lỗi.** Đừng giả định chỉ có một `FieldError` cho mỗi field hoặc thứ tự lỗi cố định.
6. **`@NotNull` khác `@NotBlank`.** Nếu muốn chặn tên toàn dấu cách, dùng `@NotBlank`; `@NotNull` chỉ chặn null.
7. **Chuỗi rỗng có thể thành null sau conversion.** Với `Integer`/`Long`, `?age=` có thể bị xem là missing sau conversion; không tự suy ra typeMismatch cho mọi empty input.
8. **Đừng nuốt lỗi conversion thành giá trị mặc định âm thầm.** Nếu client gửi `age=abc`, trả về `age=0` có thể gây lỗi nghiệp vụ kín đáo.
9. **Đừng bind JPA entity trực tiếp từ request tùy ý.** Dùng input DTO, allowlist field và phân quyền ở service; binder có thể chạm tới nested properties nếu cho phép.
10. **`required=false` không có nghĩa dữ liệu hợp lệ.** Nó chỉ thay đổi yêu cầu có/không đầu vào; giá trị có mặt vẫn phải chuyển kiểu và validation phù hợp.
11. **Các annotation thuộc đúng tầng.** `@RequestBody` dùng Jackson/message converter, `@ModelAttribute` dùng binding của Spring, constraint của Bean Validation được chạy theo ngữ cảnh; sửa một converter không nhất thiết sửa cả ba tầng.
12. **Không dùng exception class làm API contract.** Framework có thể đổi wrapper exception; contract nên dùng status, error code và cấu trúc lỗi của ứng dụng.
13. **Chú ý cơ chế method validation mới.** Nếu thêm một constraint trực tiếp lên parameter, exception có thể đổi từ `MethodArgumentNotValidException` sang `HandlerMethodValidationException` trong Spring MVC 6.1+.
14. **Dữ liệu hợp lệ về cú pháp vẫn có thể sai nghiệp vụ.** Age 20 có thể hợp lệ về chuyển kiểu và `@Min` nhưng không đủ điều kiện đăng ký một sản phẩm; business validation riêng vẫn cần thiết.

## 13. Quy trình debug một request 400

Dùng chuỗi câu hỏi thay vì đoán:

1. **Handler đã match?** Nếu chưa match, kiểm tra URL/method/content negotiation trước.
2. **Dữ liệu đến từ đâu?** Query/path/header/form → Spring argument conversion/data binder; JSON/XML body → HTTP message converter.
3. **Input có đúng syntax/kiểu không?** `abc` → Integer, JSON lỗi dấu ngoặc, ngày không đúng format đều cần xem conversion/deserialize.
4. **Object đã được tạo và property bind chưa?** Với form, xem `BindingResult.getFieldErrors()` và `getSuppressedFields()`.
5. **Validation đã chạy chưa?** Xem `@Valid`/`@Validated`, dependency và constraint, hoặc trực tiếp constraint trên method.
6. **Lỗi đến từ method validation hay argument validation?** Xem exception và vị trí annotation.
7. **Ai tạo HTTP response cuối cùng?** `@ControllerAdvice`, custom exception resolver, security filter hoặc gateway có thể thay mặc định.
8. **Nên sửa gì?** Chỉnh HTTP client khi sai kiểu dữ liệu; chỉnh DTO/converter nếu hợp đồng định dạng khác; chỉnh constraint nếu quy tắc nghiệp vụ sai; không tắt toàn bộ validation để “hết 400”.

## 14. Bài tập tự kiểm tra

**Bài 1.** `GET /input-errors/query?age=xyz` gặp lỗi gì?

**Đáp án:** Lỗi chuyển kiểu String → Integer trong argument resolver; thường `MethodArgumentTypeMismatchException` và HTTP 400. Controller không chạy.

**Bài 2.** `POST /input-errors/form` với `age=xyz` và `BindingResult` ngay sau `@ModelAttribute`?

**Đáp án:** Lỗi chuyển kiểu **bên trong binding**, được thể hiện trong `BindingResult` dưới dạng `FieldError` với code thường là `typeMismatch`. Controller có cơ hội xem lỗi và trả response.

**Bài 3.** `age=0` bind vào `Integer`, có `@Min(1)` và validation đã bật?

**Đáp án:** Chuyển kiểu thành công; validation fail. Code lỗi field thường `Min`, không phải `typeMismatch`.

**Bài 4.** JSON `{"age":"xyz"}` vào DTO `Integer age` có chắc tạo `FieldError` ở `BindingResult` không?

**Đáp án:** Không. Jackson có thể không deserialize được object và ném `HttpMessageNotReadableException` trước Bean Validation.

**Bài 5.** JSON `{"age":0}` vào `@Valid @RequestBody` với `@Min(1)`?

**Đáp án:** Deserialize được object; Bean Validation fail; mặc định thường `MethodArgumentNotValidException` và 400 (khi không có `BindingResult` ngay sau).

**Bài 6.** Vì sao handler có `@RequestParam @Min(1) int age` khi gọi `?age=0` có thể không ném `MethodArgumentNotValidException`?

**Đáp án:** Đây là constraint trực tiếp của **method parameter**; Spring MVC 6.1+ thường xử lý qua method validation và `HandlerMethodValidationException`.

**Bài 7.** `@ModelAttribute` đã áp allowed fields nhưng client gửi property ngoài danh sách; `hasErrors()` luôn thành true?

**Đáp án:** Không. Field bị chặn có thể nằm trong `BindingResult.getSuppressedFields()` mà không tạo field error; cần thiết kế kiểm tra riêng.

## Tài liệu tham khảo

- Spring Framework — Spring MVC Type Conversion: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/typeconversion.html
- Spring Framework — Data Binding: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-data-binding.html
- Spring Framework — `@ModelAttribute` và `BindingResult`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html
- Spring Framework — Method Arguments: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html
- Spring Framework — `@RequestBody`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html
- Spring Framework — Validation, `MethodArgumentNotValidException`, `HandlerMethodValidationException`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html
- Spring Framework — `@InitBinder` và `WebDataBinder`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-initbinder.html
- Spring Framework — MVC Error Responses: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- Jakarta Bean Validation: https://jakarta.ee/specifications/bean-validation/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-validation-009">Kiểm tra tham số phương thức và phân biệt các ngoại lệ validation theo phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra tham số phương thức và phân biệt các ngoại lệ validation theo phiên bản Spring MVC

## 1. Vấn đề thực tế: cùng một lỗi đầu vào, vì sao nhận các exception khác nhau?

Trong Spring MVC, hãy xem ba khai báo sau:

~~~java
// (A) Kiểm tra thuộc tính của một DTO request.
public Receipt create(@Valid @RequestBody CreateOrder request) { ... }

// (B) Kiểm tra ràng buộc trực tiếp trên một tham số HTTP.
public Receipt find(@RequestParam("page") @Min(1) int page) { ... }

// (C) Kiểm tra cả tham số trực tiếp lẫn DTO trong cùng một handler.
public Receipt create(@RequestParam("batch") @Min(1) int batch,
                      @Valid @RequestBody CreateOrder request) { ... }
~~~

Ba đoạn trên **chỉ là chữ ký minh họa**, không phải các phương thức Java độc lập. Điều đáng chú ý: với **Spring Framework 6.1+**, (A) thường đi theo **validation của argument riêng lẻ** và có thể ném `MethodArgumentNotValidException`; (B), (C) thường đi theo **method validation tích hợp của Spring MVC** và có thể ném `HandlerMethodValidationException`. Trong (C), cả lỗi thuộc tính của DTO cũng có thể được gom vào method validation. Khi lớp controller còn `@Validated` ở **mức class**, đường đi có thể thay đổi qua proxy AOP và exception lại khác.

Hiểu được các cơ chế này giúp bạn:
- Biết trường hợp nào kích hoạt kiểm tra argument, trường hợp nào kích hoạt toàn bộ phương thức.
- Dùng `@Valid`, `@Validated` và các constraint như `@NotBlank`/`@Min` đúng chỗ.
- Phân biệt lỗi JSON, lỗi chuyển kiểu, lỗi field validation, lỗi method validation, và lỗi phía server khi giá trị trả về vi phạm contract.
- Thiết kế `@ControllerAdvice` xử lý ổn định khi dự án nâng Spring Framework/Spring Boot.

**Phạm vi:** Spring MVC trên Servlet stack. Bài đặc biệt phân biệt các nhánh trong **Spring Framework 6.0, 6.1, 6.2**; Java 17+, Jakarta Bean Validation và Spring Boot 3.x. Spring Boot 2.x/Spring 5.x được nhắc để nhận diện migration. Spring WebFlux có exception/adapter riêng, không lấy bảng của MVC áp dụng nguyên xi.

## 2. Phân biệt ba tầng xử lý

Với request JSON ví dụ:

~~~http
POST /api/registrations?batch=0 HTTP/1.1
Content-Type: application/json

{"name":"","email":"not-an-email","tickets":0}
~~~

Một handler có thể gặp lỗi ở các tầng khác nhau:

~~~text
HTTP request
  │
  ▼
DispatcherServlet tìm handler + handler argument resolvers
  │
  ├── Đọc/parse/bind request, đổi kiểu, kiểm tra điều kiện required
  │     ├── JSON sai / thiếu body                → HttpMessageNotReadableException
  │     ├── query thiếu                         → MissingServletRequestParameterException
  │     └── query không đổi được kiểu          → MethodArgumentTypeMismatchException
  │
  ├── Bean Validation cho từng @RequestBody/@ModelAttribute/@RequestPart
  │     └── MethodArgumentNotValidException (khi phù hợp)
  │
  ├── Built-in MVC method validation (Spring 6.1+, khi kích hoạt)
  │     └── HandlerMethodValidationException
  │
  └── Gọi handler, có thể xác minh constraint trên return value
        └── HandlerMethodValidationException (isForReturnValue = true)
~~~

Sơ đồ trên là **đường ra quyết định**, không có nghĩa một request phải đi qua đủ từng loại validation. Khi method validation áp dụng, Spring MVC phối hợp với argument validation để **tránh kiểm tra trùng**, và có thể phát sinh `HandlerMethodValidationException` cho các lỗi lồng trong DTO. Khi sử dụng **AOP method validation** với `@Validated` trên class, một proxy khác tham gia và có thể phát sinh `ConstraintViolationException`/`MethodValidationException`.

Đừng đánh đồng mọi HTTP 400 với `MethodArgumentNotValidException`. Request sai cú pháp, sai Content-Type hay không chuyển được kiểu có thể bị từ chối **trước khi Bean Validation chạy**.

## 3. Nền tảng Jakarta Bean Validation

### 3.1. Constraint là gì?

Các annotation của `jakarta.validation.constraints` chứa quy tắc được validator kiểm tra:

| Annotation | Áp dụng thường gặp | Ghi chú |
| --- | --- | --- |
| `@NotNull` | reference khác null | Cho phép chuỗi rỗng, khác `@NotBlank` |
| `@NotBlank` | `CharSequence` có ký tự không phải whitespace | Không phù hợp cho `long`/`int` |
| `@NotEmpty` | chuỗi, collection, map, array | Không tự loại whitespace-only cho chuỗi |
| `@Size(min=..., max=...)` | chuỗi/collection/array | Kiểm tra độ dài/kích thước, không phải giá trị số |
| `@Min(1)`, `@Max(10)` | số tương thích | So sánh giá trị số |
| `@Positive`, `@PositiveOrZero` | số | 0 chỉ hợp lệ với `PositiveOrZero` |
| `@Email` | chuỗi dạng địa chỉ email | Nên kết hợp `@NotBlank` nếu không cho phép vắng/rỗng |
| `@Pattern(regexp="...")` | chuỗi | Regex là constraint riêng, không chuyển đổi input |
| `@Valid` | đối tượng lồng nhau / collection elements | **Kích hoạt cascade**, tự nó **không phải constraint** |
| Spring `@Validated` | chọn validation groups, kích hoạt AOP class/method ở ngữ cảnh phù hợp | Không đồng nghĩa `@Valid` ở mọi vị trí |

Ví dụ DTO có Bean Validation metadata:

~~~java
package com.example.validationdemo.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Email String email,
        @Min(1) int tickets) {
}
~~~

Mẫu này dùng Java record. Nếu gửi JSON `{"name":"","email":"bad","tickets":0}`, có thể có nhiều violations: `name` blank, `email` sai định dạng và `tickets` nhỏ hơn 1. **Bắt buộc có Bean Validation provider** (thường Hibernate Validator qua `spring-boot-starter-validation`); chỉ import các annotation không tự đảm bảo Spring chạy được validation.

Với kiểu primitive `int`, thuộc tính JSON không được cung cấp có thể thành giá trị mặc định trong một số đường deserialize/bind, phụ thuộc cấu hình; nếu cần phân biệt “vắng mặt” với số 0, chọn `Integer` với `@NotNull` và kiểm thử JSON thực tế.

### 3.2. `@Valid` không tự kích hoạt method validation

Cần phân biệt:

~~~java
public String save(@Valid @RequestBody SignupRequest input)
~~~

với

~~~java
public String save(@NotNull @Valid @RequestBody SignupRequest input)
~~~

Trong Spring MVC 6.1+, ví dụ đầu **chỉ** yêu cầu cascade validation của đối tượng riêng lẻ khi đủ điều kiện; ví dụ sau có `@NotNull` là **constraint trực tiếp trên method parameter**, từ đó kích hoạt method validation. Việc thêm một annotation có thể làm đổi kiểu exception dù cùng DTO và cùng request JSON.

Không thêm `@NotNull` vô thức chỉ để “tăng độ an toàn”. `@RequestBody` mặc định `required=true` đã có cơ chế kiểm tra thiếu body. Hai cách kiểm tra diễn ra ở tầng khác nhau và có thể cho ra error response khác.

### 3.3. Groups và vị trí của `@Validated`

`@Validated(Group.class)` của Spring có thể dùng ở tham số để định nghĩa validation groups cho argument. `@Validated` **mức class controller** có ý nghĩa khác: nó là tín hiệu cho cơ chế AOP method validation nếu có post-processor tương ứng. Từ Spring 6.1, nếu muốn dùng **built-in MVC method validation**, hãy **bỏ `@Validated` ở mức class**. Tài liệu Spring nêu rõ yêu cầu này.

Trong Spring Boot 3.x, hãy kiểm tra dependency/runtime thực sự có validator và cấu hình MVC, thay vì giả định chỉ cần annotation thì tất cả constraints đều có hiệu lực.

## 4. Hai kiểu validation trong Spring MVC 6.1+

### 4.1. Individual argument validation

Một method chỉ có DTO cần validate và **không có method constraints trực tiếp**:

~~~java
@PostMapping("/object")
public Receipt object(@Valid @RequestBody SignupRequest request) {
    return new Receipt(request.name(), request.tickets());
}
~~~

Với Bean Validation provider và không có `Errors`/`BindingResult` ngay sau argument, lỗi constraint trên record thường tạo:

~~~text
org.springframework.web.bind.MethodArgumentNotValidException
HTTP status mặc định: 400 Bad Request
~~~

`MethodArgumentNotValidException` mang một `BindingResult`. Bạn có thể đọc:
- `getFieldErrors()`: lỗi thuộc tính như `name` và `tickets`.
- `getGlobalErrors()`: lỗi mức object.
- `getAllErrors()`: tổng hợp errors.
- `getBindingResult()`: binding/validation result để render response lỗi.

Áp dụng tương tự về nguyên tắc cho `@Valid @ModelAttribute` và `@Valid @RequestPart` (kiểu DTO thích hợp). Điều kiện chính xác còn phụ thuộc annotation, object type, method signature và có method validation hay không.

### 4.2. Built-in MVC method validation

Có constraint **trực tiếp trên tham số method** hoặc **trên method return value**:

~~~java
@GetMapping("/page")
public PageView page(@RequestParam("page") @Min(1) int page) {
    return new PageView(page);
}
~~~

Với Spring Framework **6.1+**, không đặt `@Validated` ở class và có validator phù hợp, `GET /page?page=0` vi phạm `@Min(1)` thường sinh:

~~~text
org.springframework.web.method.annotation.HandlerMethodValidationException
HTTP status mặc định: 400 Bad Request (input violation)
~~~

Cùng method có thể kiểm tra DTO lồng:

~~~java
@PostMapping("/method")
public Receipt method(
        @RequestParam("batch") @Min(1) int batch,
        @Valid @RequestBody SignupRequest request) {
    return new Receipt(request.name(), request.tickets());
}
~~~

Vì `@Min(1)` trên `batch`, method validation **bao phủ** cả lỗi `batch` và lỗi cascade từ `@Valid SignupRequest`. Do đó, khi gửi `batch=1` nhưng DTO sai, vẫn có thể nhận **`HandlerMethodValidationException`**, không phải `MethodArgumentNotValidException`. Đây là điểm migration dễ bỏ sót: **kiểu exception phụ thuộc toàn bộ chữ ký handler, không chỉ annotation của DTO**.

`HandlerMethodValidationException` gom các kết quả theo tham số (`ParameterValidationResult`; lỗi bean có thể được biểu diễn qua `ParameterErrors`). Nó có `getAllValidationResults()` và `visitResults(Visitor)` để phân loại các nguồn như `@RequestParam`, `@PathVariable`, `@RequestHeader`, `@ModelAttribute`; không giả định chỉ có một field error của DTO.

### 4.3. Return value validation

Method cũng có thể khai báo constraint trên **kết quả trả về**:

~~~java
@GetMapping("/returned")
@NotBlank
public String returned() {
    return "";
}
~~~

Trong demo, handler trả chuỗi blank nên vi phạm contract `@NotBlank`. Với built-in MVC method validation (Spring 6.1+), có thể phát sinh `HandlerMethodValidationException` với `isForReturnValue() == true`. **Status mặc định là 500 Internal Server Error**, vì đây là lỗi hợp đồng **phía server**, khác với dữ liệu request không hợp lệ (400). Không viết handler bắt tất cả validation exception rồi ép thành HTTP 400.

Sự khác biệt này rất quan trọng khi monitoring: 4xx thường đại diện vấn đề đầu vào từ client; 5xx do method return violation phải được phát hiện và sửa trong code/service.

### 4.4. `BindingResult` / `Errors` ngay sau tham số

~~~java
@PostMapping("/form")
public ResponseEntity<?> form(
        @Valid @ModelAttribute SignupForm form,
        BindingResult errors) {
    if (errors.hasErrors()) {
        return ResponseEntity.badRequest().body("Form invalid");
    }
    return ResponseEntity.ok("Accepted");
}
~~~

Đây là fragment minh họa; `SignupForm` phải là DTO form bind được. Với `BindingResult`/`Errors` **ngay sau** đối tượng được validate, controller có thể tự xử lý lỗi của đối tượng đó. Nếu method validation cũng áp dụng, Spring chỉ tiếp tục gọi handler khi các lỗi có thể được xử lý trong các tham số `Errors` phù hợp; **có lỗi ở một tham số khác không có `Errors` ngay sau** thì vẫn ném `HandlerMethodValidationException`. Không đặt `BindingResult` tùy ý cuối chữ ký hoặc dùng nó như trình bắt lỗi cho `@RequestParam @Min`.

Tình huống hay nhầm:

~~~java
// Đây không phải bộ bắt lỗi chung cho mọi method parameter:
public String submit(
        @Valid @ModelAttribute SignupForm form,
        BindingResult result,
        @RequestParam("page") @Min(1) int page) {
    ...
}
~~~

Nếu `page=0`, controller không đương nhiên được gọi chỉ vì có `BindingResult` cho `form`.

## 5. Ma trận exception theo phiên bản và cơ chế

| Ngữ cảnh | Spring Framework trước 6.1 (đặc biệt 5.x/6.0) | Spring Framework 6.1+/6.2 | Trạng thái điển hình |
| --- | --- | --- | --- |
| `@Valid @RequestBody DTO`, không có direct method constraint và không có `BindingResult` | `MethodArgumentNotValidException` | `MethodArgumentNotValidException` | 400 |
| `@Valid @ModelAttribute DTO`, không có `BindingResult` | Có thể gặp `BindException` (nhất là MVC cũ); trên Spring 6.0 đã chuyển nhiều nhánh sang `MethodArgumentNotValidException` | `MethodArgumentNotValidException` khi individual validation áp dụng | 400 |
| Direct `@Min`/`@NotBlank` trên method parameter, không dùng AOP `@Validated` class | Trước 6.1 **chưa có built-in MVC method validation mới**; phải dựa cấu hình khác, thường AOP | `HandlerMethodValidationException` | 400 nếu input vi phạm |
| Có direct method constraint **và** `@Valid` DTO trên cùng handler | Validation của DTO có thể chạy ở argument resolver; direct constraints cần cơ chế AOP phù hợp | Method validation bao phủ cả direct và cascade constraints → `HandlerMethodValidationException` | 400 |
| Constraint trên **return value** của handler | Thường cần AOP/executable validation riêng | Built-in MVC method validation → `HandlerMethodValidationException` | 500 nếu server trả sai |
| `@Validated` **ở class**, AOP method validation hoạt động | Phổ biến: `ConstraintViolationException` | Vẫn có thể đi theo AOP: `ConstraintViolationException`, hoặc `MethodValidationException` nếu AOP cấu hình adapt | Phụ thuộc exception handler; **không mặc định suy ra 400** |
| JSON sai cú pháp hoặc thiếu required body | `HttpMessageNotReadableException` | `HttpMessageNotReadableException` | Thường 400 |
| `@RequestParam` bắt buộc bị thiếu | `MissingServletRequestParameterException` | `MissingServletRequestParameterException` | Thường 400 |
| `@RequestParam int` nhận `abc` | `MethodArgumentTypeMismatchException`/conversion error | Tương tự | Thường 400 |

**Cách đọc bảng:** lỗi validation thực tế phụ thuộc chữ ký, resolver, Bean Validation provider, filter/AOP, `BindingResult`, handler và cấu hình ngoại lệ. Không kết luận kiểu exception chỉ từ HTTP status hoặc chỉ từ một annotation.

### 5.1. Spring Boot phiên bản nào tương ứng?

Để định hướng migration (không dùng bảng này thay cho việc xem dependency tree):
- **Boot 2.x** dùng Spring Framework **5.x**, namespace Bean Validation phổ biến là `javax.validation.*`.
- **Boot 3.0–3.1** dùng Spring Framework **6.0.x**; dùng `jakarta.validation.*`, **chưa có built-in MVC method validation kiểu 6.1**.
- **Boot 3.2–3.3** dùng Spring Framework **6.1.x** trong baseline phổ biến.
- **Boot 3.4–3.5** thường dùng Spring Framework **6.2.x** theo dependency management chuẩn.

Dự án override dependencies có thể khác. **Kiểm tra phiên bản Spring thực sự chạy** (ví dụ `./gradlew dependencies --configuration runtimeClasspath` hoặc Maven dependency tree) và dùng tài liệu versioned tương ứng. Đừng đổi code chỉ vì thấy tên Spring Boot major version.

### 5.2. AOP `@Validated` và `ConstraintViolationException`

Trong các phiên bản cũ, method validation bằng Spring AOP thường yêu cầu:
1. Có `jakarta.validation.Validator` (hoặc `javax.validation.Validator` theo thế hệ).
2. Có `MethodValidationPostProcessor` / auto-configuration tương đương.
3. Controller/service bean được proxy và method call **đi qua proxy**.
4. Thường có `@Validated` ở mức class cần proxy.

Vi phạm method constraint qua `MethodValidationInterceptor` **mặc định** được ném bằng `jakarta.validation.ConstraintViolationException`. Từ Spring 6.1, có khả năng cấu hình `MethodValidationPostProcessor.setAdaptConstraintViolations(true)` để tạo `org.springframework.validation.method.MethodValidationException`. Hai exception này **khác** `HandlerMethodValidationException` của MVC built-in.

Đối với controller Spring MVC 6.1+, nếu giữ `@Validated` tại class, bạn có thể nhận `ConstraintViolationException` sau khi nâng cấp ngay cả khi mong đợi `HandlerMethodValidationException`. Tài liệu Spring khuyến nghị **bỏ class-level `@Validated`** để dùng MVC method validation mới. **Không bỏ `@Validated` trên service** chỉ vì controller cần thay: AOP method validation vẫn hữu ích cho tầng service, nơi không có HTTP method argument resolver.

### 5.3. Lịch sử `BindException` và chú ý khi nâng cấp

Nhiều ứng dụng MVC lâu năm viết:

~~~java
@ExceptionHandler(org.springframework.validation.BindException.class)
public ResponseEntity<?> handleForm(BindException ex) { ... }
~~~

Đoạn trên là minh họa ý tưởng; phải import/khai báo handler đầy đủ trong dự án thật. Trong Spring MVC 6.0, nhiều trường hợp `@ModelAttribute` đã dùng `MethodArgumentNotValidException` (là subclass của `BindException` ở nhánh tương ứng). Khi migration, kiểm tra lại class exception thực tế, phạm vi handler và tương tác với `ResponseEntityExceptionHandler`. Đừng giữ nhiều `@ExceptionHandler` có phạm vi chồng lấn trên cùng advice nếu dẫn đến ambiguity.

**`MethodValidationException` không phải tên mới của `MethodArgumentNotValidException`.** Nó đại diện đường method validation API/AOP và có cách xử lý riêng. Đừng dùng string matching như `ex.getClass().getSimpleName()` hoặc catch toàn bộ `Exception` rồi trả 400.

## 6. Ví dụ Spring Boot 3.3 / Spring Framework 6.1: chạy và tái hiện hai exception chính

Ví dụ dưới đây dùng **Spring Boot 3.3.5** (baseline Spring Framework 6.1.x) với Java 17+. Các tệp có package/class độc lập và có thể chạy trong một project Gradle Spring Boot tương ứng. Không đặt `@Validated` ở class controller để đường built-in MVC method validation được kích hoạt.

Cấu trúc:

~~~text
validation-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/validationdemo/
    │   ├── ValidationDemoApplication.java
    │   ├── api/SignupRequest.java
    │   └── web/
    │       ├── ValidationController.java
    │       └── ValidationErrorAdvice.java
    └── test/java/com/example/validationdemo/web/
        └── ValidationControllerTest.java
~~~

### 6.1. Build config

`settings.gradle`:

~~~groovy
rootProject.name = 'validation-demo'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.example'
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
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

Phần `spring-boot-starter-validation` rất quan trọng: Hibernate Validator cung cấp việc thực thi các annotation `@NotBlank`/`@Email`/`@Min`. Ví dụ giả định Gradle 8.x tương thích Java 17 và Spring Boot 3.3, có Gradle wrapper do bạn tạo cho project. Nếu dùng dự án đã có plugin/dependency management khác, chỉ thêm starter cần thiết và giữ phiên bản do dự án quản lý.

### 6.2. Main application

`src/main/java/com/example/validationdemo/ValidationDemoApplication.java`:

~~~java
package com.example.validationdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ValidationDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ValidationDemoApplication.class, args);
    }
}
~~~

`@SpringBootApplication` ở package root để component scan tìm controller và advice.

### 6.3. DTO request có constraints

`src/main/java/com/example/validationdemo/api/SignupRequest.java`:

~~~java
package com.example.validationdemo.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Email String email,
        @Min(1) int tickets) {
}
~~~

### 6.4. Controller: đối chứng individual vs method validation

`src/main/java/com/example/validationdemo/web/ValidationController.java`:

~~~java
package com.example.validationdemo.web;

import com.example.validationdemo.api.SignupRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/validation")
public class ValidationController {

    public record Receipt(String name, int tickets) {}
    public record PageView(int page) {}

    // Chỉ validate DTO: individual argument validation.
    @PostMapping("/object")
    public Receipt object(@Valid @RequestBody SignupRequest request) {
        return new Receipt(request.name(), request.tickets());
    }

    // @Min là direct constraint: bật MVC method validation.
    @PostMapping("/method")
    public Receipt method(
            @RequestParam("batch") @Min(1) int batch,
            @Valid @RequestBody SignupRequest request) {
        return new Receipt(request.name(), request.tickets());
    }

    @GetMapping("/page")
    public PageView page(@RequestParam("page") @Min(1) int page) {
        return new PageView(page);
    }

    // Return-value constraint: lỗi phía server phải thành 500.
    @GetMapping("/returned")
    @NotBlank
    public String returned() {
        return "";
    }

    // Có BindingResult ngay sau DTO, xử lý lỗi tại controller.
    @GetMapping("/form")
    public ResponseEntity<Map<String, Boolean>> form(
            @Valid @ModelAttribute FormInput form,
            BindingResult result) {
        return ResponseEntity.ok(Map.of("valid", !result.hasErrors()));
    }

    public static class FormInput {
        @NotBlank
        private String keyword;

        public FormInput() {}

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }
    }
}
~~~

Chú ý **không dùng `@Validated` ở class**. `@Min(1)` trên `batch` và `page` là những constraint trực tiếp. `@NotBlank` trên `returned()` là constraint trên return value. `FormInput` là DTO dành cho form/query với field `keyword`, và `BindingResult` đứng sát DTO.

### 6.5. Xử lý hai exception của MVC theo một hợp đồng JSON

`src/main/java/com/example/validationdemo/web/ValidationErrorAdvice.java`:

~~~java
package com.example.validationdemo.web;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ValidationErrorAdvice extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, Object> body = Map.of(
                "kind", "argument",
                "fieldErrors", ex.getBindingResult().getFieldErrorCount());

        return new ResponseEntity<>(body, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, Object> body = Map.of(
                "kind", "method",
                "resultGroups", ex.getAllValidationResults().size(),
                "returnValue", ex.isForReturnValue());

        return new ResponseEntity<>(body, headers, status);
    }
}
~~~

Ưu điểm:
- Override **đúng hook** của `ResponseEntityExceptionHandler`, thay vì khai báo trùng một `@ExceptionHandler` đã được superclass quản lý.
- Dùng status do framework tính sẵn; với method validation, status có thể là **400 cho input** hoặc **500 cho return value**.
- Dùng `getBindingResult()` ở trường hợp individual và `getAllValidationResults()` ở trường hợp method, phù hợp cấu trúc dữ liệu lỗi của từng loại.
- Không đưa `rejectedValue` (có thể chứa bí mật) trực tiếp vào JSON trả cho client.

Mẫu JSON này là **hợp đồng minh họa của ứng dụng**, không phải body lỗi mặc định của mọi Spring Boot project. Trong sản phẩm thực tế, nên dùng `ProblemDetail`/RFC 9457, message code, localization, trace ID và field paths ổn định. Có thể viết một lớp chuyển đổi riêng để hai nhánh đều xuất ra error schema thống nhất mà vẫn phân biệt được nguyên nhân trong log/telemetry.

## 7. Chạy ứng dụng và xem lỗi thực tế

Từ thư mục dự án đã có wrapper tương ứng:

~~~bash
./gradlew bootRun
~~~

Gọi request sai DTO nhưng **không có direct method constraint**:

~~~bash
curl -i -X POST 'http://localhost:8080/api/validation/object' \
  -H 'Content-Type: application/json' \
  -d '{"name":"","email":"bad","tickets":0}'
~~~

Kết quả dự kiến là **HTTP 400** với response do `ValidationErrorAdvice` tạo, dạng:

~~~json
{"kind":"argument","fieldErrors":3}
~~~

Với default provider/constraints trong ví dụ, `name` blank, `email` invalid, `tickets` dưới 1 tạo ba field violations. **Không dựa vào thứ tự** của field errors để xây UI.

Gọi method có direct parameter constraint:

~~~bash
curl -i -X POST 'http://localhost:8080/api/validation/method?batch=0' \
  -H 'Content-Type: application/json' \
  -d '{"name":"An","email":"an@example.com","tickets":2}'
~~~

Dự kiến **HTTP 400**, body:

~~~json
{"kind":"method","resultGroups":1,"returnValue":false}
~~~

`resultGroups` đếm các **tham số có errors**, không phải số field violations. Nếu gửi `batch=1` nhưng DTO invalid, cũng là lỗi method validation với `kind=method`; số nhóm tùy tham số vi phạm.

Gọi return-value validation:

~~~bash
curl -i 'http://localhost:8080/api/validation/returned'
~~~

Dự kiến **HTTP 500** (mẫu response phía ứng dụng):

~~~json
{"kind":"method","resultGroups":0,"returnValue":true}
~~~

Giá trị `resultGroups=0` trong ví dụ return-only phản ánh danh sách **parameter** results có thể rỗng; return-value violation có API riêng và không nên suy diễn “không có lỗi” từ số nhóm tham số. Nếu triển khai API công khai, hãy giữ thông tin chi tiết của lỗi return ở server logs và trả thông điệp 500 an toàn cho client.

Gọi form query lỗi nhưng có `BindingResult`:

~~~bash
curl -i 'http://localhost:8080/api/validation/form?keyword='
~~~

Dự kiến:

~~~json
{"valid":false}
~~~

HTTP là **200 OK** vì chính controller chủ động chọn `ResponseEntity.ok(...)` để minh họa việc được gọi. Trong hệ thống thực tế, lựa chọn `400` hoặc render form với HTTP 200 tùy loại ứng dụng; ở đây 200 không có nghĩa dữ liệu hợp lệ.

**Phân biệt loại lỗi:** JSON cú pháp sai hoặc thiếu body có thể bị `HttpMessageNotReadableException` **trước khi** vào validation. Query `page=abc` có thể bị conversion error **trước khi** `@Min` được kiểm tra. Không kỳ vọng `ValidationErrorAdvice` hai override ở trên sẽ đổi cả những lỗi này thành `kind=method`/`kind=argument`.

## 8. Kiểm thử MockMvc bắt đúng exception theo hành vi

`src/test/java/com/example/validationdemo/web/ValidationControllerTest.java`:

~~~java
package com.example.validationdemo.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ValidationController.class)
@Import(ValidationErrorAdvice.class)
class ValidationControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void invalidDtoWithoutDirectConstraintUsesArgumentValidation() throws Exception {
        mvc.perform(post("/api/validation/object")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"bad\",\"tickets\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kind").value("argument"))
                .andExpect(jsonPath("$.fieldErrors").value(3));
    }

    @Test
    void directConstraintUsesBuiltInMethodValidation() throws Exception {
        mvc.perform(post("/api/validation/method")
                        .param("batch", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"An\",\"email\":\"an@example.com\","
                               + "\"tickets\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kind").value("method"))
                .andExpect(jsonPath("$.returnValue").value(false));
    }

    @Test
    void nestedDtoUsesMethodValidationWhenDirectConstraintExists() throws Exception {
        mvc.perform(post("/api/validation/method")
                        .param("batch", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"bad\",\"tickets\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kind").value("method"));
    }

    @Test
    void invalidReturnValueIsServerFailure() throws Exception {
        mvc.perform(get("/api/validation/returned"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.kind").value("method"))
                .andExpect(jsonPath("$.returnValue").value(true));
    }

    @Test
    void adjacentBindingResultHandlesFormErrorsLocally() throws Exception {
        mvc.perform(get("/api/validation/form").param("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void validDtoWorks() throws Exception {
        mvc.perform(post("/api/validation/object")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lan\",\"email\":\"lan@example.com\","
                               + "\"tickets\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lan"))
                .andExpect(jsonPath("$.tickets").value(2));
    }
}
~~~

Bỏ import không dùng `ObjectMapper` nếu IDE/linter cảnh báo; test đang gửi JSON string trực tiếp nên không cần serialize Java object. Mỗi test xác minh **status lẫn loại exception được advice phân loại**, tránh test body lỗi mặc định thay đổi theo configuration.

Chạy:

~~~bash
./gradlew test
~~~

Các test giả định ứng dụng mẫu, Bean Validation provider và Spring MVC test auto-configuration đầy đủ. Nếu project có Spring Security, custom `@ControllerAdvice`, custom validator hoặc filter, `@WebMvcTest` có thể cần cấu hình thêm. Không đem test với cấu hình khác rồi kết luận Spring 6.1 xử lý sai mà chưa xác minh method validation và proxy thực sự được bật.

### 8.1. Test chi tiết exception nếu cần

Ngoài body, có thể assert resolved exception:

~~~java
// Trích từ một @Test đã cấu hình MockMvc:
// .andExpect(result ->
//     assertInstanceOf(MethodArgumentNotValidException.class,
//                      result.getResolvedException()));
~~~

`assertInstanceOf` từ JUnit 5 và `MethodArgumentNotValidException` từ Spring. Đây là đoạn minh họa được comment, cần imports tương ứng. Với lỗi method, thay bằng `HandlerMethodValidationException.class`. Chọn kiểm tra chính xác exception khi mục tiêu là **migration hoặc regression về cơ chế**, còn với API client nên ưu tiên test schema/status ổn định.

## 9. Chuẩn hóa lỗi cho frontend mà không mất ngữ nghĩa

Một error contract thực tế có thể là:

~~~json
{
  "type": "https://example.org/problems/validation",
  "title": "Invalid request",
  "status": 400,
  "violations": [
    {"field": "name", "message": "must not be blank"},
    {"field": "tickets", "message": "must be greater than or equal to 1"}
  ]
}
~~~

JSON trên là **ví dụ thiết kế**, không phải body tự động của Spring. Việc nhận diện field/method parameter tùy:
- `MethodArgumentNotValidException`: dùng `BindingResult` / `FieldError` để truy ra object path.
- `HandlerMethodValidationException`: dùng `getAllValidationResults()` hoặc `visitResults(Visitor)`; có thể có lỗi trực tiếp trên parameter, lỗi nested bean và cross-parameter.
- `ConstraintViolationException`: dùng tập `ConstraintViolation<?>`; path của Jakarta Bean Validation có thể chứa method/parameter names khác với tên HTTP bên ngoài.
- `MethodValidationException`: có `MethodValidationResult` tương ứng; đây không phải HTTP-specific exception.

Không đưa trực tiếp path nội bộ `methodName.arg0` làm API contract nếu muốn ổn định qua refactor. Map tên theo ý nghĩa HTTP (`@RequestParam`/`@PathVariable`) hoặc DTO field khi hợp lệ và phù hợp privacy policy.

Trong Spring MVC 6.1+/6.2, `ResponseEntityExceptionHandler` có hook `handleMethodArgumentNotValid` và `handleHandlerMethodValidationException`, đủ để xử lý hai đường validation built-in. Nếu subclass đã override hook, đừng thêm `@ExceptionHandler` trùng signature trong cùng advice vì superclass đã dùng `@ExceptionHandler` để dispatch.

Trường hợp `ConstraintViolationException` từ AOP cần một đường xử lý khác, chẳng hạn advice chuyên cho service boundary với mapping mã lỗi có chủ đích. Không tự động biến tất cả `ConstraintViolationException` thành 400: có thể là lỗi được tạo khi gọi service nội bộ, không phải dữ liệu client.

## 10. Những lỗi dễ nhầm với validation

| Lỗi quan sát | Ngoại lệ thường gặp | Ý nghĩa | Hướng xử lý |
| --- | --- | --- | --- |
| Body JSON thiếu/sai cú pháp | `HttpMessageNotReadableException` | Converter không tạo được object | Kiểm tra raw request, `Content-Type`, JSON parser |
| DTO tồn tại nhưng constraint sai | `MethodArgumentNotValidException` hoặc `HandlerMethodValidationException` | Bean Validation / method validation | Xem signature, Spring version, AOP, `BindingResult` |
| Query `page=xyz` với `int` | `MethodArgumentTypeMismatchException` | Không chuyển kiểu được | Kiểm tra converter/formatter và HTTP contract |
| Thiếu `@RequestParam(required=true)` | `MissingServletRequestParameterException` | Thiếu nguồn bắt buộc | Thêm input / đổi default/required có chủ đích |
| Unsupported Content-Type | `HttpMediaTypeNotSupportedException` | Không có mapping/converter tương thích | Điều chỉnh header/payload/`consumes` |
| Constraint trên return value sai | `HandlerMethodValidationException` với `isForReturnValue=true` ở 6.1+ | Server trả sai contract | Trả 500, ghi log và sửa producer |
| Có `@Validated` mức class và method AOP | `ConstraintViolationException` hoặc `MethodValidationException` | Đường proxy validation | Xem proxy, `MethodValidationPostProcessor` và version |
| `BindingResult` không nhận được lỗi | Exception vẫn phát sinh | Đặt sai vị trí hoặc lỗi thuộc tham số khác | Đặt ngay sau DTO và tách xử lý method violations |
| `@Valid` không có tác dụng | Không có provider, thiếu constraint/cascade, hoặc sai nơi đặt | Validation chưa được kích hoạt như dự kiến | Kiểm dependency, imports, DTO và test |
| Handler không bao giờ vào | Bị reject trước controller hoặc method validation | Đúng với input lỗi trong nhiều trường hợp | Log có kiểm soát exception class + status + route |

Đặc biệt, Spring Boot có thể render body lỗi khác nhau phụ thuộc cấu hình `spring.mvc.problemdetails.enabled`, custom advice, MessageSource và phiên bản. Để bảo vệ contract frontend, test output API của ứng dụng; để chẩn đoán cơ chế, test/ghi nhận **exception class**.

## 11. Nâng cấp Spring MVC an toàn: trình tự kiểm tra

1. **Ghi nhận phiên bản trước và sau**: `spring-webmvc`, `spring-context`, `jakarta.validation-api` và provider thực tế. Từ Boot 2 sang 3 phải đổi namespace `javax.validation` → `jakarta.validation`, đồng thời dùng phiên bản thư viện tương thích.
2. **Liệt kê controller có `@Validated` ở class** và direct constraint trên method parameters/return. Với Spring 6.1+, cân nhắc bỏ class annotation để chọn built-in MVC validation. Giữ AOP validation ở những service thực sự cần.
3. **Đối chiếu chữ ký từng handler**, không chỉ DTO: `@Min` ở `@RequestParam` có thể chuyển cả `@Valid @RequestBody` trong cùng method sang `HandlerMethodValidationException`.
4. **Kiểm tra `BindingResult` đứng sát argument** nếu controller chủ động xử lý form/body errors. Nếu muốn tất cả lỗi đi qua advice, bỏ đường xử lý cục bộ có chủ đích và thống nhất contract.
5. **Cập nhật advice** để xử lý cả `MethodArgumentNotValidException` lẫn `HandlerMethodValidationException`. Tách return-value violations (500) khỏi client input errors (400).
6. **Xác minh phương thức/proxy và nhóm validation**. AOP self-invocation không được proxy theo cơ chế thông thường; dùng interface/class proxy thay đổi điểm chặn. Đừng quy mọi vấn đề này thành bug của Bean Validation.
7. **Chạy test đối chứng**: valid request, invalid DTO, direct query constraint, hai loại cùng lúc, return-value violation, JSON sai, thiếu required query và binding error.
8. **Quan sát exception thật** trong log/test khi có khác biệt, vì custom `ControllerAdvice` và phiên bản framework có thể ảnh hưởng kết quả. Không truy xuất sensitive `rejectedValue` khi ghi log.
9. **Giữ format lỗi công khai ổn định**: status, type, machine-readable code, field names, locale/message source; không để class name của exception thành API contract.
10. **Kiểm tra validation xảy ra ở ranh giới thích hợp**: request validation cho HTTP input; business invariant và transaction validation ở service/domain; response validation failure là lỗi server.

### Câu hỏi tự kiểm tra

**1.** `@Valid @RequestBody Dto` mà không có direct method constraint, thiếu `BindingResult`, thường phát sinh lỗi gì ở Spring 6.1+?

**Đáp án:** `MethodArgumentNotValidException` khi Bean Validation phát hiện DTO vi phạm.

**2.** Cũng DTO đó, nhưng method nhận thêm `@RequestParam @Min(1) int quantity` thì sao?

**Đáp án:** Built-in method validation được kích hoạt; lỗi direct parameter hoặc lỗi cascade DTO có thể tạo `HandlerMethodValidationException`.

**3.** Tại sao có project nâng Boot rồi lại thấy `ConstraintViolationException`?

**Đáp án:** Có thể controller đang được method validate bởi AOP vì class-level `@Validated`/`MethodValidationPostProcessor`; nên kiểm tra kiểu proxy và version trước khi thay logic xử lý exception.

**4.** `@Valid` đã đủ để kích hoạt method validation khi không có constraint trực tiếp chưa?

**Đáp án:** Không. `@Valid` yêu cầu cascade validation; nó không phải một `@Constraint` trực tiếp như `@NotNull`.

**5.** Tại sao return-value constraint vi phạm nên là 500?

**Đáp án:** Client không kiểm soát được việc server trả một giá trị sai với contract mà server đã công bố; đây là lỗi phía server.

**6.** `BindingResult` sau `@Valid Dto` có xử lý được lỗi `@Min` trên tham số thứ ba không?

**Đáp án:** Không. Nó gắn với DTO đứng ngay trước, không đại diện mọi lỗi của method.

## 12. Tài liệu chính thức để tra theo phiên bản

- [Spring Framework 6.2 — Validation trong Spring MVC](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html): phân biệt individual argument vs method validation, `BindingResult`, class-level `@Validated` và hai MVC exception.
- [Spring Framework 6.1 — HandlerMethodValidationException Javadoc](https://docs.spring.io/spring-framework/docs/6.1.12/javadoc-api/org/springframework/web/method/annotation/HandlerMethodValidationException.html): API `isForReturnValue`, `getAllValidationResults`, `visitResults`, status 400/500.
- [Spring Framework 6.1 — ResponseEntityExceptionHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.10/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html): hook xử lý `MethodArgumentNotValidException`/`HandlerMethodValidationException`.
- [Spring Framework 6.1 — MethodValidationPostProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.7/javadoc-api/org/springframework/validation/beanvalidation/MethodValidationPostProcessor.html): AOP method validation, `ConstraintViolationException` và `setAdaptConstraintViolations`.
- [Spring Framework — `@RequestBody` validation](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/requestbody.html): validation, `BindingResult` và xử lý converter.
- [Spring Framework 6.x Upgrade Notes](https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x): hướng dẫn migration lên 6.x và tích hợp method validation.
- [Jakarta Bean Validation specification](https://jakarta.ee/specifications/bean-validation/3.0/): phân biệt constraint, cascade, groups và executable validation.

**Ghi nhớ:** hãy xác định **(1) thời điểm giải mã/bind, (2) có direct method constraint hay không, (3) có `BindingResult` sát argument không, (4) controller có class-level `@Validated`/AOP không, (5) phiên bản Spring MVC cụ thể**. Năm điều này thường giải thích vì sao cùng một lỗi đầu vào lại đi qua các exception khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)
