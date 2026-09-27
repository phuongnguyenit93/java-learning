# Currency

Số `100` tự nó không nói đó là 100 VND, 100 USD hay 100 EUR. Tiền tệ là **đơn vị của giá trị tiền**, còn locale là ngữ cảnh trình bày. Hai khái niệm liên quan nhưng không được đồng nhất.

## <a id="currency-model">Mô hình Currency</a>

`java.util.Currency` đại diện cho thông tin về một currency theo chuẩn tiền tệ mà JDK biết, thường được xác định bằng mã ISO 4217:

```java
Currency usd = Currency.getInstance("USD");
Currency vnd = Currency.getInstance("VND");
```

### Tại sao cần `Currency` thay vì chỉ giữ `"USD"` hoặc `"$"`?

Một string chỉ là text. `Currency` cho Java một object có ý nghĩa rõ ràng để mang **định danh và metadata của đơn vị tiền tệ**.

Các thông tin quan trọng thường gồm:

```text
currency code      → USD
numeric code       → mã số ISO tương ứng
symbol             → $, US$, ... tùy locale hiển thị
display name       → tên tiền tệ theo locale
default fraction digits
                    → số chữ số thập phân mặc định của currency
```

Đặc biệt, symbol **không đủ để làm identity** vì cùng một ký hiệu như `$` có thể liên quan tới nhiều currency. Domain nên dựa vào currency code/object rõ ràng, không dựa vào symbol hiển thị.

Một `Currency` có thể cung cấp:

```java
usd.getCurrencyCode();          // USD
usd.getNumericCode();
usd.getDefaultFractionDigits();
usd.getSymbol(Locale.US);
usd.getDisplayName(Locale.US);
```

`getDefaultFractionDigits()` là metadata về số chữ số thập phân thường dùng cho currency, không phải luật kế toán bắt buộc cho mọi thao tác nghiệp vụ. Việc tính toán tiền trong nghiệp vụ vẫn phải có chính sách scale và làm tròn rõ ràng.

`Currency` là metadata/unit identity; nó **không chứa amount**.

```text
Currency
→ USD

BigDecimal
→ 125.50

money domain value
→ cần kết hợp cả amount + currency
```

Java Core không có sẵn một value type `Money` trong `java.base` có thể thay thế toàn bộ việc mô hình hóa tiền trong nghiệp vụ.

## <a id="currency-vs-locale">Locale không phải Currency</a>

Java có thể suy ra currency thường gắn với region của locale:

```java
Currency currency = Currency.getInstance(Locale.US);
```

Nhưng điều đó không có nghĩa mọi giao dịch của người dùng chọn `en-US` đều là USD.

Ví dụ một người dùng giao diện tiếng Việt có thể thanh toán bằng USD:

```java
Locale displayLocale = Locale.forLanguageTag("vi-VN");
Currency transactionCurrency = Currency.getInstance("USD");
```

Hai biến trả lời hai câu hỏi khác nhau:

```text
displayLocale
→ trình bày cho người dùng theo convention nào?

transactionCurrency
→ amount này được định giá bằng đơn vị tiền nào?
```

Vì vậy entity nghiệp vụ nên lưu mã/đơn vị tiền tệ khi thông tin đó quan trọng, không suy dựng lại từ locale ở thời điểm hiển thị.

## <a id="currency-format">Format tiền bằng NumberFormat</a>

`NumberFormat.getCurrencyInstance(locale)` tạo formatter có convention hiển thị tiền của locale:

```java
Locale locale = Locale.US;
NumberFormat format = NumberFormat.getCurrencyInstance(locale);
format.setCurrency(Currency.getInstance("USD"));

String text = format.format(new BigDecimal("1234.50"));
```

Điểm cần chú ý là hai lựa chọn có thể độc lập:

```text
Locale.US
→ separators, placement, localized symbol convention

Currency.getInstance("EUR")
→ đơn vị cần hiển thị là EUR
```

Ta có thể cố ý format EUR cho người dùng US:

```java
NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
format.setCurrency(Currency.getInstance("EUR"));
System.out.println(format.format(new BigDecimal("25.00")));
```

Đừng tự nối symbol:

```java
"$" + amount
```

vì vị trí symbol, khoảng trắng, separators và symbol ambiguity đều có thể phụ thuộc locale.

## <a id="money-boundary">Ranh giới giữa format tiền và mô hình tiền</a>

Module Localization chịu trách nhiệm **trình bày**, không thay thế một mô hình tiền tệ hoàn chỉnh.

Các câu hỏi nghiệp vụ như:

```text
có cho cộng USD với EUR không?
tỷ giá lấy ở thời điểm nào?
rounding khi tính thuế?
scale khi lưu DB?
phân bổ phần dư khi chia tiền?
```

không thể giải quyết chỉ bằng `Currency` hoặc `NumberFormat`.

Một value object tối thiểu có thể có dạng:

```java
record Money(BigDecimal amount, Currency currency) {}
```

Sau khi tầng nghiệp vụ đã cho ra `Money`, tầng localization mới format:

```java
String display(Money money, Locale locale) {
    NumberFormat format = NumberFormat.getCurrencyInstance(locale);
    format.setCurrency(money.currency());
    return format.format(money.amount());
}
```

Đây chính là ranh giới đã học ở chapter Mental Model: **nghiệp vụ giữ ý nghĩa; locale quyết định cách trình bày**.

Chương tiếp theo chuyển sang một vấn đề ít rõ ràng hơn nhưng cùng bản chất: thứ tự chữ cái mà người dùng mong đợi không nhất thiết giống thứ tự code unit của `String`. Để sắp xếp text cho con người, Java có `Collator`.
