# Thẻ ngôn ngữ

`Locale` là đối tượng Java. Nhưng thông tin Locale thường phải đi qua HTTP header, URL, tệp cấu hình hoặc cơ sở dữ liệu. Vì vậy cần một dạng biểu diễn văn bản có thể trao đổi giữa nhiều hệ thống. Java dùng **thẻ ngôn ngữ IETF BCP 47 (language tag)** cho mục đích này.

## <a id="bcp47-language-tag">Mô hình tư duy về thẻ ngôn ngữ BCP 47</a>

Một thẻ ngôn ngữ ghép nhiều subtag, thường theo hướng từ tổng quát đến cụ thể:

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

Nói ngắn gọn, **language tag là chuỗi văn bản chuẩn mô tả Locale**, còn `Locale` là đối tượng Java biểu diễn thông tin đó trong chương trình.

```text
"vi-VN"
→ thẻ ngôn ngữ dùng để trao đổi

Locale.forLanguageTag("vi-VN")
→ đối tượng Java dùng khi gọi API
```

Ta cần thẻ ngôn ngữ vì đối tượng Java không thể được đặt trực tiếp vào HTTP header, URL hay tệp cấu hình. Một chuẩn văn bản chung giúp trình duyệt, máy chủ và các dịch vụ khác nhau cùng hiểu một ngữ cảnh Locale.

Ý nghĩa không phải là “một String tùy ý có dấu gạch ngang”. Mỗi phần có vai trò chuẩn để các hệ thống hiểu tương thích với nhau.

Thẻ ngôn ngữ hữu ích vì ranh giới trao đổi giữa các hệ thống thường là văn bản:

```text
trình duyệt / HTTP / cấu hình / cơ sở dữ liệu
        ↓ thẻ ngôn ngữ
ứng dụng Java
        ↓ Locale
bộ định dạng / ResourceBundle / Collator
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

Đây thường là dạng biểu diễn tốt hơn cho cấu hình/API so với tự nối:

```java
locale.getLanguage() + "_" + locale.getCountry()
```

BCP 47 dùng dấu `-`, còn một số tên gói tài nguyên/tệp lịch sử của Java dùng `_`; không nên nhầm hai quy ước.

```text
thẻ ngôn ngữ
→ vi-VN

hậu tố ResourceBundle truyền thống
→ Messages_vi_VN.properties
```

## <a id="locale-matching">Khi phía máy khách đưa nhiều Locale ưu tiên thì sao?</a>

Trong thực tế, một máy khách có thể không nói “chỉ dùng đúng `vi-VN`”. Nó có thể gửi một danh sách lựa chọn theo thứ tự ưu tiên, ví dụ tư duy như:

```text
ưu tiên 1 → vi-VN
ưu tiên 2 → vi
ưu tiên 3 → en-US
```

Ứng dụng lúc đó cần **đối chiếu Locale (locale matching)**: so danh sách máy khách mong muốn với danh sách Locale mà sản phẩm thật sự hỗ trợ rồi chọn kết quả phù hợp nhất.

Java Core cung cấp `Locale.LanguageRange` cùng các API `Locale.lookup(...)` / `Locale.filter(...)` cho bài toán đối chiếu theo dải ngôn ngữ (language ranges):

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

Mô hình tư duy cần giữ là:

```text
thẻ ngôn ngữ
→ biểu diễn một Locale/lựa chọn

danh sách ưu tiên ngôn ngữ
→ nhiều lựa chọn có độ ưu tiên

đối chiếu Locale
→ chọn Locale được hỗ trợ phù hợp nhất
```

Khung làm việc (framework) web có thể thực hiện bước này thay ứng dụng, nhưng hiểu khái niệm giúp lập trình viên biết Locale đang dùng **đến từ quá trình lựa chọn**, không phải lúc nào cũng là một tag duy nhất do người dùng truyền thẳng vào.

## <a id="language-script-region">Ngôn ngữ, hệ chữ viết và khu vực khác nhau thế nào?</a>

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

## <a id="locale-extensions">Phần mở rộng của thẻ ngôn ngữ</a>

Một số phần mở rộng, đặc biệt nhóm **Unicode locale extension** bắt đầu bằng `u`, có thể mang tùy chọn mà các API bản địa hóa hiểu được.

Ví dụ:

```text
th-TH-u-ca-buddhist
       └───────────→ yêu cầu Phật lịch cho phần trình bày nếu API hỗ trợ

en-US-u-nu-thai
       └────────→ yêu cầu hệ chữ số Thái nếu API hỗ trợ
```

Mô hình tư duy cần giữ là:

```text
language / script / region
→ định danh ngữ cảnh Locale chính

phần mở rộng (extension)
→ tùy chọn bổ sung mà một số dịch vụ bản địa hóa có thể đọc

Locale
≠ ZoneId
≠ Currency của giao dịch
≠ dữ liệu nghiệp vụ tùy ý
```

Không phải mọi API đều hiểu mọi phần mở rộng. Ở chương Bản địa hóa ngày giờ theo Locale, ta sẽ thấy ví dụ cụ thể khi `DateTimeFormatter.localizedBy(locale)` đọc một số tùy chọn mở rộng từ Locale.

Không cần học thuộc toàn bộ cú pháp BCP 47 ở mức nhập môn. Điều quan trọng là hiểu tag **có cấu trúc**, vì vậy nên để API chuẩn phân tích/xây dựng thay vì tự cắt chuỗi.

Hệ chữ viết đặc biệt quan trọng khi cùng một ngôn ngữ có nhiều hệ chữ:

```java
Locale simplified = Locale.forLanguageTag("zh-Hans-CN");
Locale traditional = Locale.forLanguageTag("zh-Hant-TW");
```

Không nên suy ra hệ chữ chỉ từ khu vực nếu dữ liệu đã có script rõ ràng.

Tương tự, `en-US` và `en-GB` cùng ngôn ngữ `en` nhưng khu vực khác nhau có thể dẫn tới khác biệt ở định dạng ngày, quy ước tiền tệ và một số từ vựng.

## <a id="canonicalization-boundary">Chuẩn hóa và ranh giới kiểm tra dữ liệu</a>

Thẻ ngôn ngữ nên được phân tích bằng API chuẩn thay vì tự `split("-")`, vì BCP 47 còn có biến thể (`variant`), phần mở rộng (`extension`) và các subtag dùng riêng (`private-use`).

`Locale.forLanguageTag(...)` được thiết kế để chuyển một thẻ ngôn ngữ sang `Locale`, nhưng nó khá khoan dung với dữ liệu đầu vào. Nếu ứng dụng cần **từ chối dữ liệu không hợp lệ một cách chặt chẽ**, dùng `Locale.Builder` sẽ tạo ranh giới kiểm tra dữ liệu rõ hơn:

```java
Locale locale = new Locale.Builder()
        .setLanguageTag(input)
        .build();
```

Dữ liệu đầu vào sai cấu trúc có thể gây `IllformedLocaleException`.

Điều này dẫn tới hai chính sách khác nhau:

```text
đầu vào từ nguồn tin cậy / muốn cố gắng xử lý tối đa
→ Locale.forLanguageTag(...)

đầu vào công khai và hợp đồng yêu cầu kiểm tra chặt
→ Locale.Builder.setLanguageTag(...)
→ bắt/chuyển IllformedLocaleException thành lỗi kiểm tra dữ liệu phù hợp
```

### Dạng chuẩn không thay thế kiểm tra nghiệp vụ

Một thẻ ngôn ngữ phân tích được về mặt cú pháp không có nghĩa ứng dụng phải hỗ trợ nó.

Ví dụ ứng dụng chỉ có gói tài nguyên cho `vi-VN` và `en-US` thì `fr-FR` vẫn là thẻ ngôn ngữ hợp lệ, nhưng ứng dụng có thể cần cơ chế dự phòng hoặc từ chối theo chính sách của sản phẩm.

Vì vậy cần tách:

```text
độ hợp lệ cú pháp
→ thẻ ngôn ngữ có đúng cấu trúc không?

hỗ trợ của ứng dụng
→ hệ thống có tài nguyên/quy tắc cho Locale đó không?
```

Chương tiếp theo dùng `Locale` đã xác định để giải quyết bài toán quan trọng nhất của bản địa hóa: **lấy đúng thông điệp/tài nguyên mà không ghi cứng if/else theo từng ngôn ngữ**.
