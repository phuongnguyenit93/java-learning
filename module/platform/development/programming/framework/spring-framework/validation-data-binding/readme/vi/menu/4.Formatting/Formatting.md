<a id="back-to-top"></a>

# Định dạng trường dữ liệu

## Menu
- [Vì sao Formatting tách khỏi Conversion tổng quát?](#formatting-purpose)
- [Hợp đồng Printer và Parser](#printer-parser-contract)
- [Formatter kết hợp Parse và Print](#formatter-contract)
- [FormatterRegistry và đăng ký tập trung](#formatter-registry)
- [Formatting theo Annotation](#annotation-formatting)
- [FormattingConversionService](#formatting-conversion-service)
- [Ranh giới Formatting phụ thuộc Locale](#formatting-locale-boundary)

## <a id="formatting-purpose">Vì sao Formatting tách khỏi Conversion tổng quát?</a>

<details>
<summary>Xem chi tiết</summary>

Conversion tổng quát trả lời câu hỏi của type system: "giá trị A trở thành giá trị B như thế nào?" Formatting trả lời câu hỏi về cách trình bày: "giá trị nên được biểu diễn thành text thế nào cho người dùng/field này, và text đó được parse ngược ra sao?"

Khác biệt này quan trọng khi dạng biểu diễn text phụ thuộc ngữ cảnh. Số `1234.5` có thể hiển thị thành `1,234.5` ở một locale và `1.234,5` ở locale khác. Date có thể dùng mẫu riêng theo field annotation hoặc chính sách trình bày của ứng dụng. Đây không chỉ là `String -> T` conversion; đây là chính sách trình bày text.

Vì vậy Spring mô hình hóa formatting bằng `Printer`, `Parser` và `Formatter`, đồng thời tích hợp chúng với cùng hạ tầng conversion mà `ConversionService` sử dụng.

```text
typed value
   ↓ Printer<T> + Locale
display text

input text
   ↓ Parser<T> + Locale
typed value
```

Việc tách này giữ conversion của domain sạch. Ví dụ `Converter<String, OrderId>` có thể định nghĩa một identifier syntax ổn định, trong khi formatter cho tiền hoặc ngày tháng có thể tuân theo quy tắc trình bày phụ thuộc locale.

Formatting còn hướng tới field: cùng một Java type có thể được trình bày khác nhau tùy annotation hoặc cách đăng ký. Vì thế tầng formatting của Spring làm việc với field metadata và `TypeDescriptor`, thay vì coi mọi phép biến đổi `String <-> T` là giống nhau trên toàn hệ thống.

Dùng formatting khi cách trình bày text là một phần của yêu cầu. Dùng conversion tổng quát cho phép đổi kiểu không phụ thuộc trình bày. Giữ ranh giới này rõ giúp mối quan tâm về locale/hiển thị không rò vào logic converter tái sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="printer-parser-contract">Hợp đồng Printer và Parser</a>

<details>
<summary>Xem chi tiết</summary>

`Printer<T>` và `Parser<T>` tách formatting thành hai hợp đồng theo hai chiều.

- `Printer<T>.print(T, Locale)` tạo text hiển thị từ object có kiểu.
- `Parser<T>.parse(String, Locale)` tạo object có kiểu từ text.

Cả hai đều nhận `Locale` hiện tại; đây là điểm khác biệt cốt lõi so với converter đơn giản.

```java
public final class PercentagePrinter implements Printer<BigDecimal> {
    @Override
    public String print(BigDecimal value, Locale locale) {
        NumberFormat format = NumberFormat.getPercentInstance(locale);
        return format.format(value);
    }
}

public final class PercentageParser implements Parser<BigDecimal> {
    @Override
    public BigDecimal parse(String text, Locale locale) throws ParseException {
        NumberFormat numberFormat = NumberFormat.getPercentInstance(locale);
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
            throw new ParseException("Invalid percentage: " + text, errorOffset);
        }
        return decimal;
    }
}
```

Hai hợp đồng có thể đăng ký riêng khi ứng dụng thật sự chỉ cần một chiều, nhưng phần lớn trường hợp chỉnh sửa field cần cả hai. Khi đó `Formatter<T>` là mô hình trừu tượng đơn giản hơn.

Parsing nên từ chối text sai định dạng thay vì âm thầm tạo value tùy tiện. Hợp đồng `Parser` cho phép `ParseException` và `IllegalArgumentException`; tầng binding phía sau có thể biểu diễn lỗi thành field error có cấu trúc.

Việc in nên cho kết quả xác định với value và locale được truyền vào. Tránh giữ trạng thái có thể thay đổi riêng cho từng yêu cầu trong formatter object vì formatting service thường là hạ tầng dùng chung.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `Printer`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Printer.html)
- [Spring Framework 6.1.14 `Parser`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Parser.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="formatter-contract">Formatter kết hợp Parse và Print</a>

<details>
<summary>Xem chi tiết</summary>

`Formatter<T>` kết hợp `Printer<T>` và `Parser<T>` cho cùng một object type. Đây là lựa chọn thường dùng khi cùng một chính sách trình bày phải hỗ trợ cả hiển thị value lẫn nhận text đầu vào.

```java
public final class LocalDateFormatter implements Formatter<LocalDate> {
    private static final DateTimeFormatter PATTERN =
            DateTimeFormatter.ofPattern("dd MMM uuuu");

    @Override
    public LocalDate parse(String text, Locale locale) {
        return LocalDate.parse(text, PATTERN.withLocale(locale));
    }

    @Override
    public String print(LocalDate value, Locale locale) {
        return PATTERN.withLocale(locale).format(value);
    }
}
```

Formatter nên mô tả một hợp đồng text nhất quán. Nếu `print` sinh dạng biểu diễn mà `parse` không thể hợp lý đọc lại, hành vi của field có thể chỉnh sửa sẽ gây bất ngờ.

Điều đó không có nghĩa mọi formatted value bắt buộc round-trip hoàn hảo. Việc làm tròn chỉ để hiển thị hoặc định dạng thân thiện với người dùng có thể cố ý mất thông tin. Quan trọng là chính sách phải rõ; nếu hai chiều thực sự bất đối xứng, tách printer/parser riêng đôi khi dễ hiểu hơn.

`Formatter` nằm ở ranh giới trình bày của module này. Hạ tầng binding có thể sử dụng nó, nhưng formatter không quyết định controller lấy dữ liệu đầu vào từ đâu hay chọn locale bằng cách nào. MVC/WebFlux sở hữu vòng đời riêng của tầng truyền tải đó.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `Formatter`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/Formatter.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="formatter-registry">FormatterRegistry và đăng ký tập trung</a>

<details>
<summary>Xem chi tiết</summary>

`FormatterRegistry` là phía cấu hình của hệ thống formatting trong Spring. Nó mở rộng `ConverterRegistry`, nên một registry có thể giữ cả converter tổng quát lẫn quy tắc field-formatting.

Registry hỗ trợ nhiều phạm vi:

- đăng ký formatter mà generic type xác định field type;
- đăng ký formatter tường minh cho một field type;
- đăng ký printer/parser riêng;
- đăng ký `AnnotationFormatterFactory` cho annotation-driven formatting.

```java
FormattingConversionService service = new FormattingConversionService();

service.addFormatter(new LocalDateFormatter());
service.addFormatterForFieldType(
        BigDecimal.class,
        new NumberStyleFormatter()
);
```

Đăng ký tập trung giúp chính sách formatting nhất quán cho mọi phía sử dụng service đó. Đồng thời, đăng ký quá rộng sẽ có ảnh hưởng rộng. `Formatter<BigDecimal>` dùng toàn cục có thể tác động mọi matching field nếu không có quy tắc annotation cụ thể hơn, vì vậy quy tắc toàn cục nên thể hiện chính sách của toàn ứng dụng chứ không phải lựa chọn riêng của một màn hình.

Đăng ký theo field annotation là cách đưa ngữ cảnh cục bộ vào mà không làm mặc định toàn cục trở nên phức tạp. Chính sách mặc định vẫn đơn giản, còn field có annotation tường minh sẽ chủ động dùng formatting đặc biệt.

Code cấu hình nên thay đổi registry trong lúc thiết lập ứng dụng; component ứng dụng sau đó sử dụng nó qua `ConversionService`. Cách phân vai này tương ứng với `ConverterRegistry` và `ConversionService` ở hệ thống conversion cốt lõi.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `FormatterRegistry`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/FormatterRegistry.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-formatting">Formatting theo Annotation</a>

<details>
<summary>Xem chi tiết</summary>

Đôi khi chỉ Java type chưa đủ để chọn quy tắc formatting. Hai field `LocalDate` có thể cần mẫu khác nhau; một numeric field có thể là percentage trong khi field khác là decimal thường. Annotation-driven formatting gắn chủ đích này trực tiếp vào field metadata.

Spring mô hình hóa bằng `AnnotationFormatterFactory<A>`. Factory khai báo field type nào hỗ trợ annotation và trả printer/parser phù hợp với annotation cụ thể.

```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface YearMonthText {
    String pattern() default "MM/uuuu";
}

public final class YearMonthTextFactory
        implements AnnotationFormatterFactory<YearMonthText> {

    @Override
    public Set<Class<?>> getFieldTypes() {
        return Set.of(YearMonth.class);
    }

    @Override
    public Printer<?> getPrinter(YearMonthText ann, Class<?> fieldType) {
        return (Printer<YearMonth>) (value, locale) ->
                DateTimeFormatter.ofPattern(ann.pattern(), locale).format(value);
    }

    @Override
    public Parser<?> getParser(YearMonthText ann, Class<?> fieldType) {
        return (Parser<YearMonth>) (text, locale) ->
                YearMonth.parse(text, DateTimeFormatter.ofPattern(ann.pattern(), locale));
    }
}
```

`getFieldTypes()` giới hạn nơi annotation hợp lệ. Spring cũng có thể dùng hệ thống conversion để coerce type nếu printer/parser trả type tương thích gián tiếp, nhưng thiết kế rõ nhất vẫn là giữ annotation và field type trực tiếp tương thích.

Dùng annotation cho chính sách trình bày khai báo theo từng field. Tránh annotation ẩn validation nghiệp vụ hoặc hành vi của tầng truyền tải; formatting annotation nên mô tả dạng biểu diễn text, không phải quy tắc chấp nhận của domain.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `AnnotationFormatterFactory`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/AnnotationFormatterFactory.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="formatting-conversion-service">FormattingConversionService</a>

<details>
<summary>Xem chi tiết</summary>

`FormattingConversionService` là nơi mô hình conversion và mô hình formatting của Spring gặp nhau. Nó mở rộng `GenericConversionService` và implement `FormatterRegistry`, nên cùng một object vừa có thể đăng ký converter/formatter vừa phục vụ yêu cầu conversion.

Ở runtime, formatter được thích nghi vào hệ thống conversion. Bên gọi vẫn dùng API quen thuộc của `ConversionService`, trong khi field metadata và quy tắc formatting phụ thuộc locale có thể tham gia phía sau.

```java
DefaultFormattingConversionService service =
        new DefaultFormattingConversionService();

String display = service.convert(
        LocalDate.of(2026, 10, 4),
        String.class
);
```

`DefaultFormattingConversionService` thêm converter mặc định và một bộ formatter chuẩn phù hợp với môi trường phổ biến. Trong Spring Framework 6.1.14, nhóm này gồm number formatting và Java time formatting khi API tương ứng có mặt. Ứng dụng có thể bổ sung chính sách bằng converter, formatter hoặc annotation formatter factory riêng.

Lợi ích là có một service hạ tầng nhất quán cho cả phép đổi kiểu và cách trình bày field. Rủi ro cũng đến từ tính tập trung: formatter đăng ký quá rộng có thể ảnh hưởng nhiều điểm binding. Mặc định toàn cục nên ít gây bất ngờ; ngoại lệ nên dùng field annotation khi phù hợp.

Không nên giả định `DefaultFormattingConversionService` biết format riêng của domain ứng dụng. Mặc định tích hợp sẵn chỉ cung cấp mức cơ sở hữu ích; value object riêng của nghiệp vụ và hợp đồng text tùy biến vẫn cần đăng ký tường minh.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `FormattingConversionService`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/support/FormattingConversionService.html)
- [Spring Framework 6.1.14 `DefaultFormattingConversionService`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/format/support/DefaultFormattingConversionService.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="formatting-locale-boundary">Ranh giới Formatting phụ thuộc Locale</a>

<details>
<summary>Xem chi tiết</summary>

Locale-sensitive formatting là bài toán về *dạng biểu diễn text*, không phải phạm vi sở hữu của toàn bộ internationalization. `Printer` và `Parser` nhận `Locale` để cùng một formatter áp dụng đúng quy ước như dấu thập phân, phân nhóm chữ số, tên tháng hoặc cách hiển thị tiền tệ mà không mã hóa cứng một quy ước văn hóa.

```java
NumberStyleFormatter formatter = new NumberStyleFormatter();

String us = formatter.print(new BigDecimal("1234.50"), Locale.US);
String de = formatter.print(new BigDecimal("1234.50"), Locale.GERMANY);
```

Formatter không quyết định locale đến từ đâu. Bên gọi độc lập có thể truyền trực tiếp; web framework có thể resolve từ ngữ cảnh request của nó. Vì vậy locale resolution trong MVC/WebFlux nằm ngoài phạm vi controller binding của module này.

Formatting cũng khác `MessageSource` localization. Formatter chuyển giá trị có kiểu sang/từ text phụ thuộc locale. `MessageSource` resolve message code thành localized message. Một validation error vì thế có thể dùng cả hai theo trình tự: formatting hiển thị rejected value, còn `MessageSource` resolve error message ở bước sau.

Lỗi thường gặp:

- dùng locale mặc định của JVM một cách ngầm định khiến hành vi thay đổi theo môi trường;
- coi số thập phân đã format theo locale là định dạng serialization máy ổn định;
- giả định quy tắc parsing giống nhau giữa các locale;
- nhét validation message đã dịch vào formatter code.

Với định dạng bền vững cho giao tiếp máy với máy, nên dùng dạng biểu diễn tường minh và không phụ thuộc locale. Dùng Spring formatting khi text phục vụ dữ liệu người dùng nhập hoặc hiển thị cho người dùng.

</details>

- [Quay lại đầu trang](#back-to-top)
