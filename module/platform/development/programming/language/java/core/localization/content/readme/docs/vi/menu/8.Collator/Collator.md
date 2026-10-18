# Collator và so sánh theo Locale

Nếu chỉ nhìn mã, ta có thể nghĩ sắp xếp văn bản đơn giản là gọi `String.compareTo`. Nhưng `String.compareTo` so sánh theo thứ tự từ điển của các đơn vị mã UTF-16 (`char`), không nhất thiết trùng với thứ tự chữ cái mà người dùng của một ngôn ngữ mong đợi.

`Collator` tồn tại để thực hiện **so sánh văn bản theo quy tắc ngôn ngữ/Locale**.

## <a id="collator-model">Mô hình tư duy của Collator</a>

`Collator` có thể hiểu là một **`Comparator<String>` chuyên cho ngôn ngữ của con người**. Nó nhận quy tắc sắp xếp ngôn ngữ (collation) tương ứng với Locale và cho biết hai chuỗi nên được xem là bằng nhau hay đứng trước/sau nhau trong ngữ cảnh đó.

Các mảnh chính cần nhớ:

```text
Locale / quy tắc collation
→ bộ quy tắc ngôn ngữ cần áp dụng

strength
→ mức khác biệt nào được coi là quan trọng

decomposition
→ cách xử lý các dạng biểu diễn Unicode tương đương/gần nhau

compare(...)
→ thực hiện so sánh

CollationKey
→ dạng biểu diễn tối ưu cho trường hợp phải so sánh lặp lại nhiều lần
```

Ta cần `Collator` khi **thứ tự dành cho người đọc** quan trọng. Nếu chỉ cần thứ tự kỹ thuật ổn định cho khóa/giao thức, `String.compareTo` hoặc comparator không phụ thuộc Locale thường phù hợp hơn.

`String.compareTo` so sánh theo thứ tự từ điển của các `char` UTF-16 trong chuỗi. Nó phù hợp cho nhiều bài toán kỹ thuật cần thứ tự ổn định, nhưng không phải công cụ sắp xếp theo quy tắc ngôn ngữ hoàn chỉnh.

`Collator` nhận `Locale` để áp dụng quy tắc collation phù hợp:

```java
Collator collator = Collator.getInstance(
        Locale.forLanguageTag("vi-VN")
);

int result = collator.compare("An", "Ân");
```

Mô hình tư duy:

```text
hai chuỗi dành cho người dùng
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
= 0 → bằng nhau ở mức `strength` hiện tại
> 0 → a đứng sau b
```

Giống `Comparator`, con số cụ thể không quan trọng; chỉ dấu âm/0/dương có ý nghĩa theo hợp đồng API.

## <a id="collation-strength">Mức so sánh (strength) và chế độ phân rã (decomposition)</a>

Không phải mọi khác biệt ký tự đều cần được xem là quan trọng như nhau. `Collator` có các mức `strength` để quyết định mức phân biệt.

Các mức quen thuộc:

```text
PRIMARY
→ khác biệt cơ bản của chữ cái

SECONDARY
→ thường xét thêm dấu/diacritic

TERTIARY
→ thường xét thêm chữ hoa/chữ thường và khác biệt chi tiết hơn

IDENTICAL
→ mức phân biệt chặt nhất
```

Cách chính xác từng ngôn ngữ diễn giải các mức này phụ thuộc quy tắc collation. Không nên suy ra một bảng chung cho mọi Locale.

```java
Collator collator = Collator.getInstance(Locale.US);
collator.setStrength(Collator.PRIMARY);
```

Khi `strength` thấp hơn, hai String khác nhau về code point vẫn có thể `compare(...) == 0` theo quy tắc collation.

Chế độ phân rã quyết định cách `Collator` xử lý các biểu diễn Unicode trước/trong khi so sánh. Hai chuỗi nhìn giống nhau có thể được biểu diễn bằng code point đã ghép hoặc bằng ký tự cơ sở + combining mark.

```java
collator.setDecomposition(Collator.NO_DECOMPOSITION);
collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
collator.setDecomposition(Collator.FULL_DECOMPOSITION);
```

`NO_DECOMPOSITION`, `CANONICAL_DECOMPOSITION` và `FULL_DECOMPOSITION` biểu diễn các mức xử lý khác nhau. Không nên học chúng như “mức mạnh hơn/yếu hơn” của `strength`: **strength** quyết định khác biệt nào có ý nghĩa khi so sánh, còn **decomposition** quyết định cách chuẩn bị biểu diễn Unicode cho phép so sánh.

## <a id="collator-vs-string-order">Collator khác String.compareTo thế nào?</a>

`String.compareTo` có một ưu điểm lớn: cho kết quả xác định và không phụ thuộc Locale, phù hợp cho nhiều khóa/giao thức/thứ tự nội bộ.

`Collator` phù hợp khi thứ tự cần phản ánh **mong đợi của người đọc**.

| Bài toán | Lựa chọn thường hợp lý |
| --- | --- |
| sắp xếp tên người cho UI | `Collator` theo Locale của người dùng |
| sắp xếp nhãn sản phẩm đã bản địa hóa | `Collator` |
| khóa kỹ thuật cần thứ tự ổn định | thứ tự không phụ thuộc Locale |
| token của giao thức | hợp đồng của giao thức, không dùng Locale người dùng |
| chỉ mục cơ sở dữ liệu | theo collation của DB/schema, không giả định giống JVM |

Ví dụ:

```java
List<String> names = new ArrayList<>(List.of("An", "Ân", "Anh", "Ánh"));

Collator collator = Collator.getInstance(
        Locale.forLanguageTag("vi-VN")
);
names.sort(collator);
```

Đừng dùng `toLowerCase()` rồi `compareTo()` như một bản thay thế cho sắp xếp theo ngôn ngữ. Chuyển đổi chữ hoa/chữ thường và collation là hai bài toán khác nhau.

## <a id="sorting-user-text">Sắp xếp văn bản hiển thị cho người dùng</a>

Khi sắp xếp một đối tượng theo tên đã bản địa hóa:

```java
record Product(String code, String displayName) {}

Collator collator = Collator.getInstance(locale);

products.sort(
        Comparator.comparing(Product::displayName, collator)
);
```

Cần lưu ý ba ranh giới:

1. **Locale của người dùng** quyết định thứ tự sắp xếp khi hiển thị, không nhất thiết Locale của máy chủ.
2. Nếu phân trang/sắp xếp diễn ra trong cơ sở dữ liệu, collation của DB mới là thứ thực thi. `Collator` phía Java không thể tự động làm DB sắp xếp giống hệt.
3. Nếu khóa sắp xếp được lưu vào bộ nhớ đệm/lưu bền, phải biết nó được tạo theo Locale/phiên bản quy tắc nào; không nên coi kết quả collation là định danh chuẩn.

Với khối lượng sắp xếp lớn, `CollationKey` có thể được dùng để chuẩn bị khóa so sánh nhiều lần:

```java
CollationKey key = collator.getCollationKey(text);
```

Nhưng đó là tối ưu sau khi ngữ nghĩa đã đúng; không nên bắt đầu bằng khóa bộ nhớ đệm phức tạp khi chỉ sắp xếp một danh sách nhỏ.

Chương tiếp theo quay lại ví dụ đơn hàng và xử lý ngày giờ: `Locale` quyết định **quy ước trình bày/phân tích**, còn `ZoneId` vẫn quyết định **thời gian địa phương tương ứng với một instant**.
