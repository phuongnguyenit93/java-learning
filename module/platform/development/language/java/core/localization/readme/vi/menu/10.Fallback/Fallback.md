# Resource Fallback

Localization hiếm khi có một file riêng hoàn hảo cho mọi combination language-script-region. `ResourceBundle` vì vậy có cơ chế fallback: khi resource cụ thể không tồn tại, Java thử các candidate tổng quát hơn theo rule lookup.

Fallback hữu ích, nhưng cần hiểu rõ để không nhầm “ứng dụng vẫn chạy” với “bản dịch đã đầy đủ”.

Nói đơn giản, **fallback là chiến lược tìm một resource ít cụ thể hơn khi resource chính xác nhất không tồn tại**.

Nó tồn tại vì nếu không có fallback, hệ thống sẽ phải có một file hoàn chỉnh cho mọi locale cụ thể hoặc thất bại ngay khi thiếu một biến thể nhỏ. Fallback cho phép reuse resource tổng quát hơn, nhưng đổi lại developer phải hiểu rõ resource cuối cùng đến từ đâu.

Các mảnh tham gia vào quá trình này gồm:

```text
requested Locale
→ locale người dùng đang yêu cầu

candidate locales
→ các locale từ cụ thể đến tổng quát

default Locale fallback
→ nhánh fallback bổ sung theo policy mặc định của ResourceBundle

base bundle
→ resource không có locale suffix

missing-resource behavior
→ chuyện gì xảy ra nếu cuối cùng vẫn không có bundle/key
```

## <a id="bundle-candidate-chain">Candidate chain của ResourceBundle</a>

Giả sử base name là `Messages` và locale là `en-US`.

Các bundle có thể có:

```text
Messages_en_US.properties
Messages_en.properties
Messages.properties
```

Mental model:

```text
locale càng cụ thể
        ↓
candidate cụ thể hơn được ưu tiên
        ↓
nếu không có thì thử candidate tổng quát hơn
        ↓
cuối cùng có thể dùng base bundle
```

`ResourceBundle.Control` cho phép quan sát candidate locales:

```java
ResourceBundle.Control control = ResourceBundle.Control.getControl(
        ResourceBundle.Control.FORMAT_DEFAULT
);

List<Locale> candidates = control.getCandidateLocales(
        "Messages",
        Locale.forLanguageTag("en-US")
);
```

Với locale có script/variant, chain có thể phức tạp hơn ví dụ `en-US` đơn giản. Hãy dựa vào API thay vì viết lookup algorithm thủ công.

## <a id="default-locale-fallback">Fallback qua default Locale</a>

Ngoài candidate của locale được yêu cầu, cơ chế `ResourceBundle` mặc định còn có thể tham chiếu **default locale** như một fallback path khi không tìm được bundle phù hợp cho target locale.

Đây là lý do default locale trở thành hidden dependency nguy hiểm.

Ví dụ:

```text
request muốn fr-FR
        ↓
không có bundle tiếng Pháp
        ↓
JVM default là en-US
        ↓
có thể rơi vào resource tiếng Anh theo fallback policy
```

Ứng dụng có thể “không lỗi” nhưng người dùng lại nhận nội dung bằng ngôn ngữ không mong đợi.

Trong hệ thống cần chính sách chặt chẽ, có thể tùy biến `ResourceBundle.Control` để kiểm soát fallback:

```java
ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(
        ResourceBundle.Control.FORMAT_DEFAULT
);

ResourceBundle bundle = ResourceBundle.getBundle(
        "Messages",
        locale,
        control
);
```

Đừng custom chỉ vì có thể; hãy bắt đầu từ product rule: locale unsupported thì fallback sang language nào, base language nào, hay trả lỗi?

## <a id="base-bundle">Vai trò của base bundle</a>

Base bundle không có suffix locale:

```text
Messages.properties
```

Nó có thể đóng vai trò resource tổng quát cuối cùng.

Có hai strategy phổ biến:

```text
Strategy A
base bundle = ngôn ngữ mặc định thực tế, ví dụ English

Strategy B
base bundle = tập fallback tối thiểu / technical-safe values
localized bundle chứa ngôn ngữ đầy đủ
```

Điều quan trọng là strategy phải **nhất quán và được test**. Nếu base chứa English nhưng team tưởng base là “locale-neutral”, missing translation có thể âm thầm hiện English ở UI khác ngôn ngữ.

Key được tìm qua parent/fallback chain. Vì vậy một localized bundle không nhất thiết phải copy toàn bộ key nếu base/parent đã cung cấp fallback — nhưng product có thể vẫn yêu cầu completeness 100% và kiểm tra bằng test.

## <a id="missing-resource">MissingResourceException và missing key</a>

Hai failure cần phân biệt:

```text
không tìm được bundle hợp lệ
→ ResourceBundle.getBundle(...) có thể ném MissingResourceException

bundle tồn tại nhưng key không tồn tại trong chain
→ getString(key) có thể ném MissingResourceException
```

Ví dụ:

```java
try {
    String message = bundle.getString("order.created");
} catch (MissingResourceException ex) {
    // log/convert/fallback theo policy của application
}
```

Trong production, tốt hơn là phát hiện lỗi sớm bằng test:

```text
base keys
        ↓ compare
locale-specific keys
        ↓
report missing / unexpected keys
```

Không nên catch mọi `MissingResourceException` rồi trả key như text mà không logging/monitoring, vì điều đó có thể biến lỗi deployment thành UI xấu tồn tại lâu.

Fallback là safety net và reuse mechanism, không phải lý do để bỏ qua translation quality.

Chương cuối gom các lỗi thực tế khi developer để locale mặc định hoặc formatted text len vào những nơi cần dữ liệu ổn định.
