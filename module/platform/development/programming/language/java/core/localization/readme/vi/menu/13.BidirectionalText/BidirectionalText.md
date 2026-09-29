# Bidirectional Text và LTR/RTL

Phần lớn ví dụ lập trình cơ bản dùng English nên developer quen với việc text chạy **left-to-right (LTR)**. Nhưng nhiều ngôn ngữ như Arabic và Hebrew chủ yếu được viết **right-to-left (RTL)**.

Khó hơn nữa, một dòng có thể trộn cả hai:

```text
RTL sentence + 12345 + EnglishProductCode
```

Lúc đó thứ tự ký tự trong dữ liệu và thứ tự hiển thị trên màn hình không thể được hiểu chỉ bằng “đọc từ trái sang phải”.

## <a id="bidi-model">Bidi là gì? Logical order và visual order</a>

**Bidirectional text (bidi text)** là text chứa hoặc có thể chứa các đoạn có hướng viết khác nhau.

Hai khái niệm quan trọng:

```text
logical order
→ thứ tự ký tự được lưu trong String / thứ tự nội dung logic

visual order
→ thứ tự/position mà glyph được trình bày trên màn hình sau khi áp dụng bidi rules
```

Ta cần phân biệt chúng vì Unicode text nên giữ **logical order** để xử lý dữ liệu nhất quán, còn renderer áp dụng Unicode Bidirectional Algorithm để quyết định cách hiển thị.

Nếu developer tự reverse string để “hỗ trợ Arabic”, dữ liệu rất dễ hỏng — đặc biệt khi text chứa số, punctuation hoặc đoạn English nằm giữa Arabic/Hebrew.

## <a id="ltr-rtl-runs">LTR, RTL và directional runs</a>

Một paragraph bidi có thể được chia thành các **directional runs**: các đoạn liên tiếp có cùng embedding level/direction trong kết quả phân tích.

Mental model:

```text
paragraph
  ├─ run A → RTL
  ├─ run B → LTR (ví dụ number/code)
  └─ run C → RTL
```

Các khái niệm beginner cần biết:

```text
base direction
→ hướng nền của paragraph

LTR run
→ đoạn được xử lý theo hướng left-to-right

RTL run
→ đoạn được xử lý theo hướng right-to-left

embedding level
→ mức dùng bởi bidi algorithm để xác định ordering của các run
```

Developer không cần tự implement Unicode Bidirectional Algorithm. Điều quan trọng là biết **mixed-direction text có structure riêng**, không phải chỉ có một boolean `isRtl` cho toàn chuỗi.

## <a id="java-bidi">java.text.Bidi trong Java</a>

Java cung cấp `java.text.Bidi` để phân tích bidirectional text.

Ví dụ:

```java
Bidi bidi = new Bidi(
        text,
        Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
);

boolean baseLtr = bidi.baseIsLeftToRight();
int runCount = bidi.getRunCount();
```

Có thể inspect từng run:

```java
for (int i = 0; i < bidi.getRunCount(); i++) {
    int start = bidi.getRunStart(i);
    int limit = bidi.getRunLimit(i);
    int level = bidi.getRunLevel(i);
}
```

Một số câu hỏi API có thể trả lời:

```text
paragraph base direction là gì?
text có thật sự cần bidi processing không?
có bao nhiêu directional runs?
mỗi run bắt đầu/kết thúc ở đâu?
embedding level của run là bao nhiêu?
```

`Bidi.requiresBidi(...)` có thể giúp kiểm tra một range ký tự có cần bidi processing hay không.

## <a id="bidi-boundary">Bidi analysis khác rendering</a>

`Bidi` **không phải text renderer**.

Nó phân tích structure/direction để renderer hoặc UI toolkit biết cách xử lý ordering. Việc chọn font, shape glyph, đo chiều rộng, layout pixel và vẽ text thuộc text-rendering/UI stack.

Boundary nên hiểu là:

```text
String
→ giữ logical text

java.text.Bidi
→ phân tích direction / runs

UI renderer / layout engine
→ tạo visual output thực tế
```

Trong nhiều application Java hiện đại, framework/UI toolkit xử lý bidi tự động. Backend thường không cần tự gọi `Bidi`. Nhưng developer làm internationalization vẫn cần hiểu nguyên tắc để tránh các anti-pattern như:

```text
reverse() Arabic/Hebrew string bằng tay
giả định mọi text hiển thị left-to-right
gắn punctuation bằng string hack theo visual position
coi mixed-direction string như một RTL block đồng nhất
```

Một nguyên tắc cuối cùng:

```text
logical content
→ giữ đúng dữ liệu Unicode

direction analysis
→ để Unicode bidi rules / standard API xử lý

visual layout
→ để renderer chịu trách nhiệm
```
