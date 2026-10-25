# Văn bản hai chiều và LTR/RTL

Phần lớn ví dụ lập trình cơ bản dùng tiếng Anh nên lập trình viên quen với việc văn bản chạy **trái sang phải (left-to-right/LTR)**. Nhưng nhiều ngôn ngữ như tiếng Ả Rập và tiếng Do Thái chủ yếu được viết **phải sang trái (right-to-left/RTL)**.

Khó hơn nữa, một dòng có thể trộn cả hai:

```text
câu RTL + 12345 + EnglishProductCode
```

Lúc đó thứ tự ký tự trong dữ liệu và thứ tự hiển thị trên màn hình không thể được hiểu chỉ bằng “đọc từ trái sang phải”.

## <a id="bidi-model">Bidi là gì? Thứ tự logic và thứ tự hiển thị</a>

**Văn bản hai chiều (bidirectional/bidi text)** là văn bản chứa hoặc có thể chứa các đoạn có hướng viết khác nhau.

Hai khái niệm quan trọng:

```text
thứ tự logic (logical order)
→ thứ tự ký tự được lưu trong String / thứ tự nội dung logic

thứ tự hiển thị (visual order)
→ thứ tự/vị trí mà hình dạng ký tự (glyph) được trình bày trên màn hình sau khi áp dụng quy tắc bidi
```

Ta cần phân biệt chúng vì văn bản Unicode nên giữ **thứ tự logic** để xử lý dữ liệu nhất quán, còn bộ dựng hiển thị áp dụng Unicode Bidirectional Algorithm để quyết định cách hiển thị.

Nếu lập trình viên tự đảo ngược chuỗi để “hỗ trợ tiếng Ả Rập”, dữ liệu rất dễ hỏng — đặc biệt khi văn bản chứa số, dấu câu hoặc đoạn tiếng Anh nằm giữa tiếng Ả Rập/tiếng Do Thái.

## <a id="ltr-rtl-runs">LTR, RTL và các đoạn có hướng</a>

Một đoạn văn bidi có thể được chia thành các **đoạn có hướng (directional runs)**: các đoạn liên tiếp có cùng mức nhúng/hướng trong kết quả phân tích.

Mô hình tư duy:

```text
đoạn văn
  ├─ đoạn A → RTL
  ├─ đoạn B → LTR (ví dụ số/mã)
  └─ đoạn C → RTL
```

Các khái niệm người mới cần biết:

```text
hướng nền (base direction)
→ hướng nền của đoạn văn

đoạn LTR
→ đoạn được xử lý theo hướng trái sang phải

đoạn RTL
→ đoạn được xử lý theo hướng phải sang trái

mức nhúng (embedding level)
→ mức dùng bởi thuật toán bidi để xác định thứ tự của các đoạn
```

Lập trình viên không cần tự triển khai Unicode Bidirectional Algorithm. Điều quan trọng là biết **văn bản trộn nhiều hướng có cấu trúc riêng**, không phải chỉ có một boolean `isRtl` cho toàn chuỗi.

## <a id="java-bidi">java.text.Bidi trong Java</a>

Java cung cấp `java.text.Bidi` để phân tích văn bản hai chiều.

Ví dụ:

```java
Bidi bidi = new Bidi(
        text,
        Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
);

boolean baseLtr = bidi.baseIsLeftToRight();
int runCount = bidi.getRunCount();
```

Có thể kiểm tra từng đoạn:

```java
for (int i = 0; i < bidi.getRunCount(); i++) {
    int start = bidi.getRunStart(i);
    int limit = bidi.getRunLimit(i);
    int level = bidi.getRunLevel(i);
}
```

Một số câu hỏi API có thể trả lời:

```text
hướng nền của đoạn văn là gì?
văn bản có thật sự cần xử lý bidi không?
có bao nhiêu đoạn có hướng?
mỗi đoạn bắt đầu/kết thúc ở đâu?
mức nhúng của đoạn là bao nhiêu?
```

`Bidi.requiresBidi(...)` có thể giúp kiểm tra một phạm vi ký tự có cần xử lý bidi hay không.

## <a id="bidi-boundary">Phân tích Bidi khác với việc dựng hiển thị văn bản</a>

`Bidi` **không phải bộ dựng hiển thị văn bản**.

Nó phân tích cấu trúc/hướng để bộ dựng hiển thị hoặc bộ công cụ UI biết cách xử lý thứ tự. Việc chọn phông chữ, tạo hình ký tự, đo chiều rộng, bố trí điểm ảnh (pixel) và vẽ văn bản thuộc tầng dựng hiển thị/UI.

Ranh giới trách nhiệm nên hiểu là:

```text
String
→ giữ văn bản theo thứ tự logic

java.text.Bidi
→ phân tích hướng / các đoạn

bộ dựng UI / bộ máy bố trí
→ tạo kết quả hiển thị thực tế
```

Trong nhiều ứng dụng Java hiện đại, khung làm việc/bộ công cụ UI xử lý bidi tự động. Phần xử lý phía máy chủ thường không cần tự gọi `Bidi`. Nhưng lập trình viên làm quốc tế hóa vẫn cần hiểu nguyên tắc để tránh các cách làm sai như:

```text
đảo ngược chuỗi tiếng Ả Rập/tiếng Do Thái bằng tay
giả định mọi văn bản hiển thị trái sang phải
gắn dấu câu bằng thao tác chuỗi theo vị trí hiển thị
coi chuỗi trộn nhiều hướng như một khối RTL đồng nhất
```

Một nguyên tắc cuối cùng:

```text
nội dung logic
→ giữ đúng dữ liệu Unicode

phân tích hướng
→ để quy tắc bidi Unicode / API chuẩn xử lý

bố trí hiển thị
→ để bộ dựng hiển thị chịu trách nhiệm
```

Sau khi hoàn tất phần xử lý văn bản dành cho con người, cột mốc cuối quay lại các **chính sách an toàn của toàn mô-đun**: cơ chế dự phòng của `ResourceBundle`, Locale mặc định, dữ liệu dành cho máy và các lỗi bản địa hóa thường gặp.
