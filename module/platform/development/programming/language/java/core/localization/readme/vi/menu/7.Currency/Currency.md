# Currency và tiền tệ

Số `100` tự nó không nói đó là 100 VND, 100 USD hay 100 EUR. Tiền tệ là **đơn vị của giá trị tiền**, còn Locale là ngữ cảnh trình bày. Hai khái niệm liên quan nhưng không được đồng nhất.

## <a id="currency-model">Mô hình Currency</a>

`java.util.Currency` đại diện cho thông tin về một loại tiền theo chuẩn mà JDK biết, thường được xác định bằng mã ISO 4217:

```java
Currency usd = Currency.getInstance("USD");
Currency vnd = Currency.getInstance("VND");
```

### Vì sao cần `Currency` thay vì chỉ giữ chuỗi `"USD"` hoặc `"$"`?

Một String chỉ là văn bản. `Currency` cho Java một đối tượng có ý nghĩa rõ ràng để mang **định danh và siêu dữ liệu của đơn vị tiền tệ**.

Các thông tin quan trọng thường gồm:

```text
mã tiền tệ          → USD
mã số               → mã số ISO tương ứng
ký hiệu             → $, US$, ... tùy Locale hiển thị
tên hiển thị        → tên tiền tệ theo Locale
số chữ số thập phân mặc định
                    → số chữ số thập phân mặc định của loại tiền
```

Đặc biệt, ký hiệu **không đủ để làm định danh** vì cùng một ký hiệu như `$` có thể liên quan tới nhiều loại tiền. Miền nghiệp vụ nên dựa vào mã/đối tượng `Currency` rõ ràng, không dựa vào ký hiệu hiển thị.

Một `Currency` có thể cung cấp:

```java
usd.getCurrencyCode();          // USD
usd.getNumericCode();
usd.getDefaultFractionDigits();
usd.getSymbol(Locale.US);
usd.getDisplayName(Locale.US);
```

`getDefaultFractionDigits()` là siêu dữ liệu về số chữ số thập phân thường dùng cho loại tiền, không phải luật kế toán bắt buộc cho mọi thao tác nghiệp vụ. Việc tính toán tiền trong nghiệp vụ vẫn phải có chính sách số chữ số thập phân và làm tròn rõ ràng.

`Currency` mang siêu dữ liệu/định danh đơn vị; nó **không chứa số tiền**.

```text
Currency
→ USD

BigDecimal
→ 125.50

giá trị tiền trong nghiệp vụ
→ cần kết hợp cả số tiền + loại tiền
```

Java Core không có sẵn một kiểu giá trị `Money` trong `java.base` có thể thay thế toàn bộ việc mô hình hóa tiền trong nghiệp vụ.

## <a id="currency-vs-locale">Locale không phải Currency</a>

Java có thể lấy tiền tệ từ một `Locale`, nhưng quy tắc không đơn giản chỉ là “lấy tiền tệ của khu vực”. Nếu Locale có Unicode extension `cu` và/hoặc `rg`, Java phản ánh các giá trị đó; khi có cả hai thì `cu` được ưu tiên hơn tiền tệ ngầm suy ra từ `rg`. Nếu không có các extension này, Java suy ra từ **thành phần quốc gia (country)** của Locale; thành phần ngôn ngữ (language) và biến thể (variant) không tham gia quyết định này:

```java
Currency currency = Currency.getInstance(Locale.US);

Currency euroPreference = Currency.getInstance(
        Locale.forLanguageTag("en-US-u-cu-eur")
); // EUR
```

Nếu thành phần quốc gia của Locale không phải mã ISO 3166 được hỗ trợ, `Currency.getInstance(locale)` có thể ném `IllegalArgumentException`; với lãnh thổ không có tiền tệ, phương thức có thể trả về `null`. Vì vậy Locale chỉ có ngôn ngữ như `Locale.ENGLISH` không đủ dữ liệu để suy ra tiền tệ của giao dịch.

Nhưng điều đó không có nghĩa mọi giao dịch của người dùng chọn `en-US` đều là USD.

Ví dụ một người dùng giao diện tiếng Việt có thể thanh toán bằng USD:

```java
Locale displayLocale = Locale.forLanguageTag("vi-VN");
Currency transactionCurrency = Currency.getInstance("USD");
```

Hai biến trả lời hai câu hỏi khác nhau:

```text
Locale hiển thị
→ trình bày cho người dùng theo quy ước nào?

tiền tệ giao dịch
→ số tiền này được định giá bằng đơn vị tiền nào?
```

Vì vậy thực thể nghiệp vụ nên lưu mã/đơn vị tiền tệ khi thông tin đó quan trọng, không suy dựng lại từ Locale ở thời điểm hiển thị.

## <a id="currency-format">Định dạng tiền bằng NumberFormat</a>

`NumberFormat.getCurrencyInstance(locale)` tạo bộ định dạng có quy ước hiển thị tiền của Locale:

```java
Locale locale = Locale.US;
NumberFormat format = NumberFormat.getCurrencyInstance(locale);
format.setCurrency(Currency.getInstance("USD"));

String text = format.format(new BigDecimal("1234.50"));
```

Điểm cần chú ý là hai lựa chọn có thể độc lập:

```text
Locale.US
→ dấu phân cách, vị trí ký hiệu và quy ước ký hiệu theo Locale

Currency.getInstance("EUR")
→ đơn vị cần hiển thị là EUR
```

Ta có thể cố ý định dạng EUR cho người dùng US:

```java
NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
format.setCurrency(Currency.getInstance("EUR"));
System.out.println(format.format(new BigDecimal("25.00")));
```

`setCurrency(...)` đổi tiền tệ mà bộ định dạng sử dụng, bao gồm định danh/ký hiệu tiền tệ, nhưng **không tự đặt lại** số chữ số thập phân tối thiểu/tối đa theo tiền tệ mới. Vì vậy một bộ định dạng được tạo cho USD rồi đổi sang JPY vẫn có thể giữ hai chữ số thập phân. Khi số chữ số hiển thị quan trọng, hãy cấu hình chúng có chủ đích theo tiền tệ giao dịch; chính sách làm tròn và số chữ số nghiệp vụ vẫn thuộc miền nghiệp vụ.

Đừng tự nối ký hiệu tiền tệ:

```java
"$" + amount
```

vì vị trí ký hiệu, khoảng trắng, dấu phân cách và sự mơ hồ của ký hiệu đều có thể phụ thuộc Locale.

## <a id="money-boundary">Ranh giới giữa định dạng tiền và mô hình tiền</a>

Mô-đun Bản địa hóa (Localization) chịu trách nhiệm **trình bày**, không thay thế một mô hình tiền tệ hoàn chỉnh.

Các câu hỏi nghiệp vụ như:

```text
có cho cộng USD với EUR không?
tỷ giá lấy ở thời điểm nào?
làm tròn khi tính thuế?
số chữ số thập phân khi lưu DB?
phân bổ phần dư khi chia tiền?
```

không thể giải quyết chỉ bằng `Currency` hoặc `NumberFormat`.

Một đối tượng giá trị tối thiểu có thể có dạng:

```java
record Money(BigDecimal amount, Currency currency) {}
```

Sau khi tầng nghiệp vụ đã cho ra `Money`, tầng bản địa hóa mới định dạng. Ví dụ sau **cố ý dùng số chữ số thập phân mặc định của `Currency` như một chính sách hiển thị**, không phải như quy tắc làm tròn nghiệp vụ:

```java
String displayUsingCurrencyDefaultDigits(Money money, Locale locale) {
    NumberFormat format = NumberFormat.getCurrencyInstance(locale);
    format.setCurrency(money.currency());

    int fractionDigits = money.currency().getDefaultFractionDigits();
    if (fractionDigits >= 0) {
        format.setMinimumFractionDigits(fractionDigits);
        format.setMaximumFractionDigits(fractionDigits);
    }

    return format.format(money.amount());
}
```

Ứng dụng thực tế có thể chọn chính sách hiển thị khác. Điều quan trọng là không giả định `setCurrency(...)` tự đồng bộ số chữ số thập phân, và không để `NumberFormat` quyết định thay quy tắc làm tròn/số chữ số của nghiệp vụ.

Đây chính là ranh giới đã học ở chương Mô hình tư duy: **nghiệp vụ giữ ý nghĩa; Locale quyết định cách trình bày**.

Chương tiếp theo chuyển sang một vấn đề ít rõ ràng hơn nhưng cùng bản chất: thứ tự chữ cái mà người dùng mong đợi không nhất thiết giống thứ tự code unit của `String`. Để sắp xếp văn bản cho con người, Java có `Collator`.
