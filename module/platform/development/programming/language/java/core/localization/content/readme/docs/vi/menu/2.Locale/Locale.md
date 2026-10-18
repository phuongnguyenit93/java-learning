# Locale

Sau khi đã tách dữ liệu nghiệp vụ khỏi cách trình bày, ứng dụng cần một cách trả lời câu hỏi: **“đang trình bày cho người dùng theo ngôn ngữ/vùng nào?”**. Trong Java, đối tượng trung tâm biểu diễn ngữ cảnh đó là `Locale`.

## <a id="locale-model">Locale mô hình hóa điều gì?</a>

`Locale` mô tả một **ngữ cảnh văn hóa/ngôn ngữ** thông qua các thành phần như:

```text
ngôn ngữ (language)
→ ngôn ngữ, ví dụ vi, en, ja

hệ chữ viết (script)
→ hệ chữ, ví dụ Latn, Cyrl, Hans, Hant

khu vực/quốc gia (region/country)
→ vùng/quốc gia, ví dụ VN, US, GB

biến thể / phần mở rộng (variant / extensions)
→ thông tin bổ sung cho các trường hợp chuyên biệt
```

### Locale là gì và có vai trò gì?

`Locale` là một **đối tượng mô tả lựa chọn/ngữ cảnh**, không phải đối tượng tự dịch câu chữ và cũng không tự định dạng dữ liệu.

Vai trò của nó là làm **đầu vào cho những API phụ thuộc ngôn ngữ/vùng**:

```text
Locale
  ├─→ ResourceBundle      → chọn tài nguyên
  ├─→ NumberFormat        → cách hiển thị số
  ├─→ DateTimeFormatter   → tên tháng/ngày + mẫu hiển thị
  ├─→ Currency display    → ký hiệu/tên tiền tệ
  └─→ Collator            → thứ tự so sánh văn bản
```

Vì vậy hãy nghĩ `Locale` như **“bộ ngữ cảnh để Java biết nên áp dụng quy ước nào”**, chứ không phải bản thân quy ước hay bản dịch.

Ví dụ:

```java
Locale vietnameseVietnam = Locale.forLanguageTag("vi-VN");
Locale englishUS = Locale.forLanguageTag("en-US");
Locale chineseTraditionalTaiwan = Locale.forLanguageTag("zh-Hant-TW");
```

### Locale không phải vị trí địa lý tuyệt đối

`Locale` không có nghĩa “thiết bị đang ở quốc gia này”. Nó là ngữ cảnh dùng để chọn quy ước ngôn ngữ và cách trình bày.

Một người đang sống ở Việt Nam vẫn có thể chọn `en-US`. Một máy chủ ở Singapore vẫn có thể định dạng phản hồi cho người dùng `vi-VN`.

### Locale thường đến từ đâu?

Trong ứng dụng thực tế, locale có thể được quyết định từ nhiều nguồn:

```text
ngôn ngữ/vùng người dùng đã chọn trong hồ sơ
ưu tiên ngôn ngữ từ HTTP/trình duyệt
tham số hoặc cấu hình của ứng dụng
locale mặc định do sản phẩm quyết định
Locale mặc định của JVM — chỉ nên dùng khi đó thật sự là chính sách mong muốn
```

Điểm quan trọng không phải nguồn nào “luôn đúng”, mà là ứng dụng phải có **một chính sách rõ ràng để chọn Locale** thay vì để môi trường JVM vô tình quyết định.

Nếu cần hiển thị chính tên Locale cho người dùng, `Locale` cũng cung cấp các API như `getDisplayLanguage(...)`, `getDisplayCountry(...)` và `getDisplayName(...)`; Locale truyền vào các phương thức này quyết định ngôn ngữ dùng để hiển thị tên đó.

### Locale không chứa múi giờ

Đây là ranh giới cực kỳ quan trọng:

```text
Locale
→ ngôn ngữ, tên tháng/ngày, mẫu hiển thị, ký hiệu số...

ZoneId
→ quy tắc offset của một vùng thời gian
```

`Locale.forLanguageTag("vi-VN")` **không tự động có nghĩa** `Asia/Ho_Chi_Minh`.

Khi hiển thị thời điểm, ứng dụng thường cần cả hai:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
```

## <a id="locale-construction">Tạo Locale đúng cách</a>

Với dữ liệu đến từ thẻ ngôn ngữ (language tag), cách tự nhiên là:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
```

Khi mã nguồn đã có từng thành phần riêng, Java 21 hỗ trợ các phương thức tạo `Locale.of(...)`:

```java
Locale vi = Locale.of("vi");
Locale viVN = Locale.of("vi", "VN");
```

Khi cần hệ chữ viết, phần mở rộng hoặc cấu hình chi tiết hơn, dùng `Locale.Builder`:

```java
Locale locale = new Locale.Builder()
        .setLanguage("zh")
        .setScript("Hant")
        .setRegion("TW")
        .build();
```

`Builder` hữu ích khi muốn kiểm tra dữ liệu chặt hơn: subtag không hợp lệ có thể gây `IllformedLocaleException` thay vì âm thầm tạo một đối tượng khó hiểu.

Các hằng số phổ biến như `Locale.US`, `Locale.UK`, `Locale.JAPAN` tiện cho những locale được Java định nghĩa sẵn, nhưng không nên ép mọi locale của ứng dụng vào một danh sách ghi cứng.

### Không dùng Locale để suy diễn quá nhiều

Một `Locale` có khu vực có thể giúp Java chọn tiền tệ mặc định hoặc quy ước định dạng, nhưng nó không phải hồ sơ người dùng:

```text
Locale.US
≠ địa chỉ người dùng
≠ quốc tịch
≠ múi giờ
≠ tiền tệ mà giao dịch bắt buộc phải dùng
```

Nghiệp vụ nào cần tiền tệ, múi giờ hay quốc gia riêng thì phải mô hình hóa chúng riêng.

## <a id="locale-equality">Định danh và phép so sánh bằng của Locale</a>

`Locale` là một đối tượng giá trị bất biến. Hai Locale bằng nhau khi các thành phần tạo nên định danh của chúng bằng nhau.

```java
Locale a = Locale.forLanguageTag("vi-VN");
Locale b = Locale.of("vi", "VN");

System.out.println(a.equals(b)); // true
```

Nhưng “có thể phục vụ cùng bản dịch” không đồng nghĩa với `equals`:

```java
Locale en = Locale.forLanguageTag("en");
Locale enUS = Locale.forLanguageTag("en-US");

System.out.println(en.equals(enUS)); // false
```

`en-US` mang khu vực cụ thể còn `en` không có. Quá trình phân giải tài nguyên có thể đi từ Locale cụ thể tới gói tài nguyên tổng quát hơn, nhưng đó là **quy tắc tra cứu**, không phải phép so sánh bằng.

Khi dùng `Locale` làm khóa trong bộ nhớ đệm/map, hãy nhớ định danh của nó có thể bao gồm ngôn ngữ, hệ chữ viết, khu vực, biến thể và phần mở rộng. Nếu ứng dụng chỉ muốn gom theo ngôn ngữ, hãy lấy rõ `locale.getLanguage()` thay vì dựa vào `equals` của toàn bộ Locale.

Chương tiếp theo chuyển từ đối tượng `Locale` trong Java sang **thẻ ngôn ngữ (language tag)**, dạng văn bản chuẩn giúp thông tin Locale đi qua HTTP, cấu hình, cơ sở dữ liệu và các hệ thống khác.
