<a id="back-to-top"></a>

# Validation, Binding, Chuyển đổi kiểu và Định dạng

## Menu
- [Vì sao các cơ chế này tồn tại?](#validation-binding-purpose)
- [Validation khác Data Binding như thế nào?](#validation-vs-binding)
- [Conversion khác Formatting như thế nào?](#conversion-vs-formatting)
- [Ranh giới module và các module lân cận](#validation-binding-module-boundary)

## <a id="validation-binding-purpose">Vì sao các cơ chế này tồn tại?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng hiếm khi nhận dữ liệu đúng ngay kiểu và cấu trúc mà domain object cần. Dữ liệu bên ngoài thường đến dưới dạng text, map ít kiểu, giá trị cấu hình, form field, command-line value hoặc dữ liệu đã deserialize. Trong khi đó object đích lại cần trạng thái có kiểu và có invariant: `int`, `LocalDate`, value object, enum hoặc object graph lồng nhau.

Spring tách bài toán này thành nhiều cơ chế phối hợp vì mỗi cơ chế giải một vấn đề khác nhau:

- **data binding** đưa các giá trị đầu vào có tên vào mô hình đối tượng;
- **chuyển đổi kiểu (type conversion)** chuyển một giá trị từ kiểu Java này sang kiểu Java khác;
- **định dạng (formatting)** phân tích và in giá trị với ngữ cảnh trình bày như `Locale`;
- **validation** kiểm tra object hoặc giá trị sau cùng có thỏa quy tắc hay không.

Việc tách trách nhiệm giúp cùng một conversion hoặc quy tắc validation tái sử dụng được ngoài một tầng truyền tải cụ thể. `Converter<String, OrderId>` có thể dùng trong binding, cấu hình hoặc hạ tầng. `Validator` có thể kiểm tra cùng một object dù bên gọi là bộ điều hợp web, batch job hay code ứng dụng thuần Java.

Nên hình dung toàn bộ cơ chế như một luồng xử lý thay vì một thao tác "bind thần kỳ":

```text
raw values
   ↓
binding chọn vị trí đích
   ↓
conversion / formatting trong quá trình binding
   ↓
gán giá trị tạo trạng thái object có kiểu
   ↓
validation
   ↓
lỗi có cấu trúc hoặc trạng thái được chấp nhận
```

`DataBinder` sẽ điều phối chính xác các bước này ở phần sau của lộ trình, nhưng mô hình tư duy cần giữ ngay từ đầu là: đổi kiểu không đồng nghĩa với quyết định ghi vào đâu, và ghi được giá trị vào object cũng chưa chứng minh object đó hợp lệ.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `DataBinder`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/validation/DataBinder.html)
- [Spring Framework 6.1.14 `ConversionService`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/core/convert/ConversionService.html)
- [Spring Framework 6.1.14 `Validator`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/validation/Validator.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-vs-binding">Validation khác Data Binding như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

**Binding** trả lời câu hỏi "giá trị đầu vào nào được áp vào vị trí nào trên đối tượng đích?" **Validation** trả lời "giá trị hoặc object sau khi bind có chấp nhận được không?" Nếu trộn hai câu hỏi này, code dễ nhầm chính sách an toàn/cấu trúc dữ liệu với quy tắc nghiệp vụ.

Giả sử đầu vào có `age = "abc"`. Nếu property đích là `int`, vấn đề đầu tiên là binding/conversion: Spring không tạo được giá trị integer. Không cần validator nghiệp vụ để phát hiện lỗi kiểu này. Ngược lại, `age = "15"` có thể convert thành công nhưng vẫn vi phạm quy tắc "tuổi khách hàng phải từ 18 trở lên". Trường hợp thứ hai mới thuộc validation.

```java
public final class Registration {
    private int age;

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}

public final class RegistrationValidator implements Validator {
    @Override
    public boolean supports(Class<?> type) {
        return Registration.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Registration registration = (Registration) target;
        if (registration.getAge() < 18) {
            errors.rejectValue("age", "age.tooYoung");
        }
    }
}
```

Sự khác nhau này cũng rất quan trọng với safe binding. Validation không thể thay thế việc kiểm soát field nào được phép ghi từ dữ liệu bên ngoài. Một field không bao giờ được phép nhận dữ liệu từ bên ngoài phải bị loại khỏi bề mặt binding ngay từ đầu, thay vì bind xong rồi trông chờ validator từ chối.

Quy tắc thực tế: xử lý lỗi ở đúng giai đoạn sinh ra nó. Type mismatch, thiếu bind value bắt buộc hoặc property không truy cập được là vấn đề của binding; object/value có thỏa quy tắc nghiệp vụ hay không là vấn đề của validation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="conversion-vs-formatting">Conversion khác Formatting như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Conversion tổng quát và formatting đều biến đổi giá trị, nhưng chúng phục vụ hai loại ngữ cảnh khác nhau.

`ConversionService` mô hình hóa chuyển đổi kiểu có thể tái sử dụng. Converter thường trả lời câu hỏi mang tính cấu trúc như "`String` trở thành `OrderId` như thế nào?" hoặc "một enum được chuyển sang dạng biểu diễn khác ra sao?" Hợp đồng conversion không tự mang nghĩa "hãy hiển thị giá trị này cho người dùng".

Formatting hướng tới cách trình bày. `Printer<T>` biến giá trị có kiểu thành text để hiển thị; `Parser<T>` biến text trở lại giá trị có kiểu. Cả hai đều nhận `Locale`, còn `Formatter<T>` gộp hai hợp đồng này lại. Vì vậy formatting phù hợp hơn khi dạng biểu diễn text phụ thuộc quy ước của người dùng như dấu thập phân, mẫu ngày tháng, kiểu hiển thị tiền tệ hoặc annotation trên field.

```java
ConversionService conversionService = new DefaultConversionService();
Integer quantity = conversionService.convert("12", Integer.class);

Formatter<BigDecimal> amountFormatter = new Formatter<>() {
    @Override
    public BigDecimal parse(String text, Locale locale) throws ParseException {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        if (!(numberFormat instanceof DecimalFormat format)) {
            throw new ParseException("No DecimalFormat for locale: " + locale, 0);
        }
        format.setParseBigDecimal(true);
        ParsePosition position = new ParsePosition(0);
        Number number = format.parse(text, position);
        if (!(number instanceof BigDecimal decimal)
                || position.getIndex() != text.length()) {
            int errorOffset = position.getErrorIndex() >= 0
                    ? position.getErrorIndex()
                    : position.getIndex();
            throw new ParseException("Invalid number: " + text, errorOffset);
        }
        return decimal;
    }

    @Override
    public String print(BigDecimal value, Locale locale) {
        return NumberFormat.getNumberInstance(locale).format(value);
    }
};
```

Thao tác đầu tiên chỉ quan tâm kiểu Java đích. Thao tác thứ hai mô tả rõ cách text được hiểu và hiển thị theo locale.

Dùng converter đơn giản khi phép biến đổi có một nghĩa ổn định, không phụ thuộc cách trình bày. Dùng formatter khi dạng biểu diễn text là một phần của bài toán. Tránh nhét locale-sensitive parsing vào `Converter<String, T>` dùng toàn cục vì API converter không có tham số locale và chính sách sẽ trở nên mơ hồ.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `Formatter`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Formatter.html)
- [Spring Framework 6.1.14 `Printer`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Printer.html)
- [Spring Framework 6.1.14 `Parser`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Parser.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-binding-module-boundary">Ranh giới module và các module lân cận</a>

<details>
<summary>Xem chi tiết</summary>

Module này sở hữu các hợp đồng dùng chung của Spring Framework cho validation, binding, conversion và formatting. Phạm vi chủ động dừng trước vòng đời controller/tầng truyền tải cụ thể.

Các ranh giới chính:

- **Spring Core Container** cung cấp hạ tầng dùng chung như đăng ký `ConversionService` trong application context và sở hữu `MessageSource`/i18n ở cấp container rộng hơn.
- **module này** giải thích `BeanWrapper`, `ConversionService`, converter SPI, formatter SPI, `Errors`/`BindingResult`, `Validator`, `DataBinder` và cách Spring tích hợp Jakarta Bean Validation.
- **Spring MVC** sở hữu `WebDataBinder`, `@InitBinder`, request parameter/model-attribute binding, controller argument validation và vòng đời Servlet request.
- **Spring WebFlux** sở hữu vòng đời controller binding và validation tương ứng trong reactive stack.

Ranh giới này giúp mô hình tư duy cốt lõi tái sử dụng được. `DataBinder` không phải kiểu chỉ dành cho web; web binder xây trên nó. Tương tự, `FormatterRegistry` là hạ tầng formatting tổng quát dù MVC và WebFlux đều có thể sử dụng nó.

Khi học các chương tiếp theo, có thể dùng hai câu hỏi để giữ đúng phạm vi sở hữu:

1. Đây có phải hợp đồng cốt lõi có thể tái sử dụng để chuyển đổi, bind hoặc validate giá trị không?
2. Hay đây là bộ điều hợp quyết định vòng đời HTTP/controller sẽ gọi hợp đồng đó như thế nào?

Loại thứ nhất thuộc module này. Loại thứ hai được chuyển giao sang web/reactive. Nhờ vậy ví dụ không vô tình biến hành vi riêng của controller thành hành vi của Spring core API.

Jakarta Bean Validation cũng theo nguyên tắc tương tự. Module này dạy cách Spring thích nghi đặc tả đó vào hệ sinh thái `Validator`; ngữ nghĩa riêng của constraint/provider vẫn thuộc đặc tả và provider Bean Validation.

</details>

- [Quay lại đầu trang](#back-to-top)
