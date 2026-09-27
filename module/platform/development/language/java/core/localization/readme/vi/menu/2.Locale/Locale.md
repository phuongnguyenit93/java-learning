# Locale

Sau khi đã tách dữ liệu nghiệp vụ khỏi cách trình bày, ứng dụng cần một cách trả lời câu hỏi: **“đang trình bày cho người dùng theo ngôn ngữ/vùng nào?”**. Trong Java, object trung tâm biểu diễn ngữ cảnh đó là `Locale`.

## <a id="locale-model">Locale mô hình hóa điều gì?</a>

`Locale` mô tả một **ngữ cảnh văn hóa/ngôn ngữ** thông qua các thành phần như:

```text
language
→ ngôn ngữ, ví dụ vi, en, ja

script
→ hệ chữ, ví dụ Latn, Cyrl, Hans, Hant

region/country
→ vùng/quốc gia, ví dụ VN, US, GB

variant / extensions
→ thông tin bổ sung cho các trường hợp chuyên biệt
```

### Nói đơn giản: Locale là cái gì và vai trò của nó là gì?

`Locale` là một **object mô tả preference/context**, không phải object tự dịch câu chữ và cũng không tự format dữ liệu.

Vai trò của nó là làm **đầu vào cho những API phụ thuộc ngôn ngữ/vùng**:

```text
Locale
  ├─→ ResourceBundle      → chọn resource
  ├─→ NumberFormat        → cách hiển thị số
  ├─→ DateTimeFormatter   → tên tháng/ngày + pattern hiển thị
  ├─→ Currency display    → symbol/tên tiền tệ
  └─→ Collator            → thứ tự so sánh text
```

Vì vậy hãy nghĩ `Locale` như **“bộ ngữ cảnh để Java biết nên áp dụng convention nào”**, chứ không phải bản thân convention hay bản dịch.

Ví dụ:

```java
Locale vietnameseVietnam = Locale.forLanguageTag("vi-VN");
Locale englishUS = Locale.forLanguageTag("en-US");
Locale chineseTraditionalTaiwan = Locale.forLanguageTag("zh-Hant-TW");
```

### Locale không phải vị trí địa lý tuyệt đối

`Locale` không có nghĩa “thiết bị đang ở quốc gia này”. Nó là ngữ cảnh dùng để chọn quy ước ngôn ngữ và cách trình bày.

Một người đang sống ở Việt Nam vẫn có thể chọn `en-US`. Một server ở Singapore vẫn có thể format response cho người dùng `vi-VN`.

### Locale thường đến từ đâu?

Trong ứng dụng thực tế, locale có thể được quyết định từ nhiều nguồn:

```text
user preference đã lưu trong profile
HTTP/browser language preference
tham số hoặc cấu hình của ứng dụng
locale mặc định do sản phẩm quyết định
JVM default locale — chỉ nên dùng khi đó thật sự là policy mong muốn
```

Điểm quan trọng không phải nguồn nào “luôn đúng”, mà là ứng dụng phải có **một policy rõ ràng để chọn locale** thay vì để JVM environment vô tình quyết định.

Nếu cần hiển thị chính tên locale cho người dùng, `Locale` cũng cung cấp các API như `getDisplayLanguage(...)`, `getDisplayCountry(...)` và `getDisplayName(...)`; locale truyền vào các method này quyết định ngôn ngữ dùng để hiển thị tên đó.

### Locale không chứa múi giờ

Đây là ranh giới cực kỳ quan trọng:

```text
Locale
→ ngôn ngữ, tên tháng/ngày, pattern hiển thị, number symbols...

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

Với dữ liệu đến từ language tag, cách tự nhiên là:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
```

Khi code đã có từng thành phần riêng, Java 21 hỗ trợ factory `Locale.of(...)`:

```java
Locale vi = Locale.of("vi");
Locale viVN = Locale.of("vi", "VN");
```

Khi cần script, extension hoặc cấu hình chi tiết hơn, dùng `Locale.Builder`:

```java
Locale locale = new Locale.Builder()
        .setLanguage("zh")
        .setScript("Hant")
        .setRegion("TW")
        .build();
```

`Builder` hữu ích khi muốn validation chặt hơn: subtag không hợp lệ có thể gây `IllformedLocaleException` thay vì âm thầm tạo một object khó hiểu.

Các hằng số phổ biến như `Locale.US`, `Locale.UK`, `Locale.JAPAN` tiện cho những locale được Java định nghĩa sẵn, nhưng không nên ép mọi locale của ứng dụng vào một danh sách ghi cứng.

### Không dùng Locale để suy diễn quá nhiều

Một `Locale` có region có thể giúp Java chọn default currency hoặc format conventions, nhưng nó không phải hồ sơ người dùng:

```text
Locale.US
≠ địa chỉ người dùng
≠ quốc tịch
≠ múi giờ
≠ currency mà giao dịch bắt buộc phải dùng
```

Nghiệp vụ nào cần tiền tệ, múi giờ hay quốc gia riêng thì phải mô hình hóa chúng riêng.

## <a id="default-locale">Default Locale và các category</a>

Java có default locale của JVM:

```java
Locale current = Locale.getDefault();
```

Giá trị mặc định này tiện cho ứng dụng desktop/local, nhưng trong backend phục vụ nhiều người dùng nó có thể trở thành **phụ thuộc ẩn**: cùng một đoạn mã nhưng chạy trên máy có cấu hình khác sẽ cho kết quả khác.

Java phân biệt hai category chính:

```java
Locale.Category.DISPLAY
→ dùng khi cần hiển thị tên của language/country/locale

Locale.Category.FORMAT
→ dùng cho format number/date/currency...
```

Đọc riêng từng category:

```java
Locale displayLocale = Locale.getDefault(Locale.Category.DISPLAY);
Locale formatLocale = Locale.getDefault(Locale.Category.FORMAT);
```

Có thể thay đổi default:

```java
Locale.setDefault(Locale.Category.FORMAT, Locale.US);
```

Nhưng đây là trạng thái ở mức JVM và có thể ảnh hưởng mã khác trong cùng tiến trình. Backend production thường an toàn hơn khi truyền `Locale` **tường minh** theo ngữ cảnh request/người dùng thay vì thay đổi giá trị mặc định toàn cục.

## <a id="locale-equality">Identity và equality của Locale</a>

`Locale` là value object bất biến. Hai locale bằng nhau khi các thành phần tạo nên identity của chúng bằng nhau.

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

`en-US` mang region cụ thể còn `en` không có. Resource resolution có thể fallback từ locale cụ thể về bundle tổng quát hơn, nhưng đó là **quy tắc lookup**, không phải equality.

Khi dùng `Locale` làm key trong cache/map, hãy nhớ định danh của nó có thể bao gồm language, script, region, variant và extensions. Nếu ứng dụng chỉ muốn gom theo language, hãy lấy rõ `locale.getLanguage()` thay vì dựa vào `equals` của toàn bộ locale.

Chương tiếp theo chuyển từ object `Locale` trong Java sang **language tag**, dạng text chuẩn giúp locale đi qua HTTP, config, database và các hệ thống khác.
