# Language Tag

`Locale` là object Java. Nhưng locale thường phải đi qua HTTP header, URL, file cấu hình hoặc database. Vì vậy cần một representation dạng text có thể trao đổi giữa nhiều hệ thống. Java dùng **IETF BCP 47 language tag** cho mục đích này.

## <a id="bcp47-language-tag">Mental model của BCP 47 language tag</a>

Một language tag ghép nhiều subtag, thường theo hướng từ tổng quát đến cụ thể:

```text
language[-Script][-REGION][-variant...][-extensions...]
```

Ví dụ:

```text
vi-VN
en-US
en-GB
zh-Hans-CN
zh-Hant-TW
```

Nói ngắn gọn, **language tag là chuỗi text chuẩn mô tả locale**, còn `Locale` là object Java biểu diễn thông tin đó trong chương trình.

```text
"vi-VN"
→ language tag dùng để trao đổi

Locale.forLanguageTag("vi-VN")
→ object Java dùng khi gọi API
```

Ta cần language tag vì object Java không thể được đặt trực tiếp vào HTTP header, URL hay file cấu hình. Một chuẩn text chung giúp browser, server và các service khác nhau cùng hiểu một ngữ cảnh locale.

Ý nghĩa không phải là “một string tùy ý có dấu gạch ngang”. Mỗi phần có vai trò chuẩn để các hệ thống hiểu tương thích với nhau.

Language tag hữu ích vì ranh giới trao đổi giữa các hệ thống thường là text:

```text
browser / HTTP / config / database
        ↓ language tag
Java application
        ↓ Locale
formatter / bundle / collator
```

## <a id="for-language-tag">Locale.forLanguageTag và toLanguageTag</a>

Chuyển tag thành `Locale`:

```java
Locale viVN = Locale.forLanguageTag("vi-VN");
```

Chuyển ngược lại:

```java
String tag = viVN.toLanguageTag();
System.out.println(tag); // vi-VN
```

Đây thường là representation tốt hơn cho config/API so với tự nối:

```java
locale.getLanguage() + "_" + locale.getCountry()
```

BCP 47 dùng dấu `-`, còn một số tên bundle/file lịch sử của Java dùng `_`; không nên nhầm hai convention.

```text
language tag
→ vi-VN

ResourceBundle suffix truyền thống
→ Messages_vi_VN.properties
```

### Khi phía client đưa nhiều locale ưu tiên thì sao?

Trong thực tế, một client có thể không nói “chỉ dùng đúng `vi-VN`”. Nó có thể gửi một danh sách preference theo thứ tự ưu tiên, ví dụ tư duy như:

```text
ưu tiên 1 → vi-VN
ưu tiên 2 → vi
ưu tiên 3 → en-US
```

Ứng dụng lúc đó cần **locale matching**: so danh sách client mong muốn với danh sách locale mà sản phẩm thật sự hỗ trợ rồi chọn kết quả phù hợp nhất.

Java Core cung cấp `Locale.LanguageRange` cùng các API `Locale.lookup(...)` / `Locale.filter(...)` cho bài toán matching theo language ranges:

```java
List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(
        "vi-VN,vi;q=0.9,en-US;q=0.8"
);

List<Locale> supported = List.of(
        Locale.forLanguageTag("vi-VN"),
        Locale.US
);

Locale matched = Locale.lookup(ranges, supported);
```

Mental model cần giữ là:

```text
language tag
→ biểu diễn một locale/preference

language priority list
→ nhiều lựa chọn có độ ưu tiên

locale matching
→ chọn locale được hỗ trợ phù hợp nhất
```

Framework web có thể thực hiện bước này thay application, nhưng hiểu khái niệm giúp developer biết locale đang dùng **đến từ quá trình lựa chọn**, không phải lúc nào cũng là một tag duy nhất do user truyền thẳng vào.

## <a id="language-script-region">Language, script và region khác nhau thế nào?</a>

Ba thành phần dễ bị trộn lẫn nhưng giải quyết ba câu hỏi khác nhau:

| Thành phần | Câu hỏi | Ví dụ |
| --- | --- | --- |
| language | ngôn ngữ nào? | `vi`, `en`, `zh` |
| script | hệ chữ nào? | `Latn`, `Cyrl`, `Hans`, `Hant` |
| region | biến thể/quy ước vùng nào? | `VN`, `US`, `GB`, `TW` |

Ngoài ba phần người mới gặp nhiều nhất, BCP 47 còn có thể chứa:

- **variant** — biến thể chuyên biệt hơn;
- **extension** — thông tin mở rộng có cấu trúc;
- **private use** — dữ liệu riêng bắt đầu bằng `x-`.

Không cần học thuộc toàn bộ cú pháp BCP 47 ở mức beginner. Điều quan trọng là hiểu tag **có cấu trúc**, vì vậy nên để API chuẩn parse/build thay vì tự cắt chuỗi.

Script đặc biệt quan trọng khi cùng language có nhiều hệ chữ:

```java
Locale simplified = Locale.forLanguageTag("zh-Hans-CN");
Locale traditional = Locale.forLanguageTag("zh-Hant-TW");
```

Không nên suy ra script chỉ từ region nếu dữ liệu đã có script rõ ràng.

Tương tự, `en-US` và `en-GB` cùng language `en` nhưng region khác nhau có thể dẫn tới khác biệt ở format date, currency conventions và một số từ vựng.

## <a id="canonicalization-boundary">Chuẩn hóa và ranh giới validation</a>

Language tag nên được parse bằng API chuẩn thay vì tự `split("-")`, vì BCP 47 còn có variant, extension và private-use subtags.

`Locale.forLanguageTag(...)` được thiết kế để chuyển một language tag sang `Locale`, nhưng nó khá khoan dung với dữ liệu đầu vào. Nếu ứng dụng cần **từ chối dữ liệu không hợp lệ một cách chặt chẽ**, dùng `Locale.Builder` sẽ tạo ranh giới validation rõ hơn:

```java
Locale locale = new Locale.Builder()
        .setLanguageTag(input)
        .build();
```

Dữ liệu đầu vào sai cấu trúc có thể gây `IllformedLocaleException`.

Điều này dẫn tới hai chính sách khác nhau:

```text
input từ nguồn tin cậy / muốn best-effort
→ Locale.forLanguageTag(...)

input public và contract yêu cầu strict validation
→ Locale.Builder.setLanguageTag(...)
→ bắt/convert IllformedLocaleException thành validation error phù hợp
```

### Canonical form không phải business validation

Một tag parse được về mặt cú pháp không có nghĩa ứng dụng phải hỗ trợ nó.

Ví dụ ứng dụng chỉ có bundle cho `vi-VN` và `en-US` thì `fr-FR` vẫn là language tag hợp lệ, nhưng ứng dụng có thể cần fallback hoặc từ chối theo chính sách của sản phẩm.

Vì vậy cần tách:

```text
syntax validity
→ tag có đúng cấu trúc không?

application support
→ hệ thống có resource/quy tắc cho locale đó không?
```

Chương tiếp theo dùng `Locale` đã xác định để giải quyết bài toán quan trọng nhất của localization: **lấy đúng message/resource mà không hard-code if/else theo từng ngôn ngữ**.
