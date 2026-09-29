# Collator và so sánh theo Locale

Nếu chỉ nhìn code, ta có thể nghĩ sắp xếp text đơn giản là gọi `String.compareTo`. Nhưng thứ tự mà máy dùng để so sánh code point/code unit không nhất thiết là thứ tự chữ cái mà người dùng của một ngôn ngữ mong đợi.

`Collator` tồn tại để thực hiện **so sánh text theo quy tắc ngôn ngữ/locale**.

## <a id="collator-model">Mental model của Collator</a>

`Collator` có thể hiểu là một **`Comparator<String>` chuyên cho ngôn ngữ của con người**. Nó nhận quy tắc collation tương ứng với locale và cho biết hai chuỗi nên được xem là bằng nhau hay đứng trước/sau nhau trong ngữ cảnh đó.

Các mảnh chính cần nhớ:

```text
Locale / collation rules
→ bộ quy tắc ngôn ngữ cần áp dụng

strength
→ mức khác biệt nào được coi là quan trọng

decomposition
→ cách xử lý các representation Unicode tương đương/gần nhau

compare(...)
→ thực hiện so sánh

CollationKey
→ representation tối ưu cho trường hợp phải so sánh lặp lại nhiều lần
```

Ta cần `Collator` khi **thứ tự dành cho người đọc** quan trọng. Nếu chỉ cần thứ tự kỹ thuật ổn định cho key/protocol, `String.compareTo` hoặc comparator locale-neutral thường phù hợp hơn.

`String.compareTo` so sánh theo thứ tự lexicographic dựa trên giá trị Unicode của chuỗi. Nó ổn cho nhiều bài toán máy móc, nhưng không phải một công cụ linguistic sorting hoàn chỉnh.

`Collator` nhận `Locale` để áp dụng quy tắc collation phù hợp:

```java
Collator collator = Collator.getInstance(
        Locale.forLanguageTag("vi-VN")
);

int result = collator.compare("An", "Ân");
```

Mental model:

```text
hai chuỗi user-facing
        ↓
Locale
        ↓
Collator
        ↓
so sánh theo quy tắc ngôn ngữ
```

`compare(a, b)` trả:

```text
< 0 → a đứng trước b
= 0 → bằng nhau ở mức strength hiện tại
> 0 → a đứng sau b
```

Giống `Comparator`, con số cụ thể không quan trọng; chỉ dấu âm/0/dương có ý nghĩa theo hợp đồng API.

## <a id="collation-strength">Strength và decomposition</a>

Không phải mọi khác biệt ký tự đều cần được xem là quan trọng như nhau. `Collator` có các mức strength để quyết định mức phân biệt.

Các mức quen thuộc:

```text
PRIMARY
→ khác biệt cơ bản của chữ cái

SECONDARY
→ thường xét thêm dấu/diacritic

TERTIARY
→ thường xét thêm case và khác biệt chi tiết hơn

IDENTICAL
→ mức phân biệt chặt nhất
```

Cách chính xác từng ngôn ngữ diễn giải các mức này phụ thuộc collation rules. Không nên suy ra một bảng universal cho mọi locale.

```java
Collator collator = Collator.getInstance(Locale.US);
collator.setStrength(Collator.PRIMARY);
```

Khi strength thấp hơn, hai string khác nhau về code point vẫn có thể `compare(...) == 0` theo collation.

Decomposition xử lý cách biểu diễn Unicode được chuẩn hóa trước/trong khi so sánh. Hai chuỗi nhìn giống nhau có thể được biểu diễn bằng code point đã ghép hoặc bằng ký tự cơ sở + combining mark. Khi bài toán cần so sánh text theo ý nghĩa ngôn ngữ, chính sách normalization/decomposition phải được cân nhắc thay vì chỉ nhìn byte/code unit.

## <a id="collator-vs-string-order">Collator khác String.compareTo thế nào?</a>

`String.compareTo` có một ưu điểm lớn: deterministic, không phụ thuộc locale, phù hợp cho nhiều key/protocol/internal ordering.

`Collator` phù hợp khi thứ tự cần phản ánh **mong đợi của người đọc**.

| Bài toán | Lựa chọn thường hợp lý |
| --- | --- |
| sắp xếp tên người cho UI | `Collator` theo locale của người dùng |
| sort nhãn sản phẩm localized | `Collator` |
| key kỹ thuật cần thứ tự ổn định | thứ tự không phụ thuộc locale |
| token của protocol | hợp đồng của protocol, không dùng locale người dùng |
| database index | theo collation của DB/schema, không giả định giống JVM |

Ví dụ:

```java
List<String> names = new ArrayList<>(List.of("An", "Ân", "Anh", "Ánh"));

Collator collator = Collator.getInstance(
        Locale.forLanguageTag("vi-VN")
);
names.sort(collator);
```

Đừng dùng `toLowerCase()` rồi `compareTo()` như một bản thay thế cho linguistic collation. Case folding và collation là hai bài toán khác nhau.

## <a id="sorting-user-text">Sắp xếp text hiển thị cho người dùng</a>

Khi sort một object theo tên đã localized:

```java
record Product(String code, String displayName) {}

Collator collator = Collator.getInstance(locale);

products.sort(
        Comparator.comparing(Product::displayName, collator)
);
```

Cần lưu ý ba ranh giới:

1. **Locale của người dùng** quyết định thứ tự sắp xếp khi hiển thị, không nhất thiết locale của máy chủ.
2. Nếu pagination/sort diễn ra trong database, collation của DB mới là thứ thực thi. Java-side `Collator` không thể tự động làm DB sort giống hệt.
3. Nếu sort key được cache/persist, phải biết nó được tạo theo locale/rule version nào; không nên coi kết quả collation là canonical identity.

Với workload sort lớn, `CollationKey` có thể được dùng để chuẩn bị key so sánh nhiều lần:

```java
CollationKey key = collator.getCollationKey(text);
```

Nhưng đó là optimization sau khi semantics đã đúng; không nên bắt đầu bằng cache key phức tạp khi chỉ sort một danh sách nhỏ.

Chương tiếp theo quay lại ví dụ đơn hàng và xử lý cách hiển thị date/time: `Locale` quyết định **cách hiển thị**, còn `ZoneId` vẫn quyết định **thời gian địa phương tương ứng với instant**.
