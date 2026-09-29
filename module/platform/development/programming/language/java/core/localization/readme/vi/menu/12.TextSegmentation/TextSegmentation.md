# Phân đoạn text theo ngôn ngữ

Sau khi biết cách dịch, format và sắp xếp text, còn một câu hỏi nền tảng khác: **“đoạn text này nên được chia ở đâu?”**

Nếu chỉ làm với English đơn giản, developer dễ nghĩ:

```text
character → một char
word      → split theo dấu cách
sentence  → split theo dấu chấm
line      → xuống dòng ở space gần nhất
```

Các giả định này không đúng cho mọi Unicode text và mọi ngôn ngữ. Java cung cấp `BreakIterator` để phân tích boundary của natural-language text.

## <a id="breakiterator-model">BreakIterator là gì và tại sao cần nó?</a>

`BreakIterator` là API xác định **các vị trí biên hợp lệ** trong một chuỗi text.

Nó có bốn vai trò chính:

```text
character boundary
→ ranh giới đơn vị ký tự người dùng nhìn thấy

word boundary
→ ranh giới từ/token ở mức natural-language

sentence boundary
→ ranh giới câu

line boundary
→ vị trí có thể ngắt dòng
```

Ta cần nó vì **boundary trong text không phải lúc nào cũng suy ra đúng bằng một ký tự ASCII cố định**.

Ví dụ:

```text
"hello world"
```

có space rõ ràng, nhưng nhiều hệ chữ/ngôn ngữ không dùng space theo cách giống English. Tương tự, dấu chấm không phải lúc nào cũng có nghĩa “kết thúc câu”.

Factory methods chính:

```java
BreakIterator.getCharacterInstance(locale);
BreakIterator.getWordInstance(locale);
BreakIterator.getSentenceInstance(locale);
BreakIterator.getLineInstance(locale);
```

Sau khi gắn text bằng `setText(...)`, iterator di chuyển qua các boundary bằng `first()`, `next()`, `previous()`, `following(...)`, `preceding(...)`.

## <a id="character-boundary">Character boundary và grapheme cluster</a>

Ở module `string`, learner đã biết:

```text
char
≠ Unicode code point
≠ luôn luôn là "một ký tự người dùng nhìn thấy"
```

Localization bổ sung bước tiếp theo: khi cần di chuyển/cắt text theo **user-perceived character**, không nên mặc định mỗi UTF-16 `char` là một character hoàn chỉnh.

```java
BreakIterator iterator = BreakIterator.getCharacterInstance(locale);
iterator.setText(text);

for (int end = iterator.first(), start = end;
     (end = iterator.next()) != BreakIterator.DONE;
     start = end) {
    String unit = text.substring(start, end);
}
```

`getCharacterInstance(...)` giúp tìm boundary tương ứng với các cụm ký tự mà người dùng cảm nhận như một đơn vị, thay vì cắt text mù theo index UTF-16.

Boundary này có quan hệ chặt với **grapheme cluster** trong Unicode. Phần lý thuyết code point/grapheme/normalization thuộc module `string`; chapter này chỉ sở hữu việc **dùng boundary analysis trong localization**.

Use case điển hình:

```text
cursor movement
text selection
safe truncation
UI character stepping
```

## <a id="word-sentence-boundary">Word và sentence boundary</a>

`BreakIterator.getWordInstance(locale)` giúp tìm các đoạn boundary của text theo rule của locale:

```java
BreakIterator words = BreakIterator.getWordInstance(locale);
words.setText(text);
```

Không nên hiểu API này đơn giản là “trả danh sách word đã lọc sạch punctuation”. `BreakIterator` chủ yếu trả **boundary positions**. Code sử dụng vẫn phải quyết định segment nào là word có ý nghĩa cho use case của mình.

Tương tự:

```java
BreakIterator sentences = BreakIterator.getSentenceInstance(locale);
sentences.setText(text);
```

giúp tìm boundary câu mà không hard-code rule `split(".")`.

Tại sao `split(" ")` / `split(".")` yếu?

```text
space có thể không phải word delimiter universal
punctuation có thể nằm trong abbreviation/number
sentence-ending punctuation khác nhau theo language
Unicode text có nhiều loại whitespace/punctuation
```

`BreakIterator` đưa knowledge về language/Unicode boundary xuống API chuẩn thay vì bắt application tự đoán.

## <a id="line-boundary">Line boundary không chỉ là newline</a>

`BreakIterator.getLineInstance(locale)` tìm các **line-break opportunities** — những vị trí mà text renderer có thể xem xét để xuống dòng.

Khác với:

```text
\n
→ newline đã tồn tại trong dữ liệu
```

line-break opportunity trả lời:

```text
Nếu UI phải wrap một dòng dài,
chỗ nào là boundary hợp lệ để cân nhắc ngắt?
```

Đây là kiến thức quan trọng cho UI/layout engine, document rendering và text editor. Backend thông thường hiếm khi tự layout text, nhưng developer nên biết việc “cắt dòng mỗi N ký tự” có thể phá natural-language text.

## <a id="breakiterator-boundary">Vai trò và giới hạn của BreakIterator</a>

`BreakIterator` giải quyết **boundary analysis**, không giải quyết mọi bài toán natural-language processing.

```text
BreakIterator làm tốt
→ tìm character/word/sentence/line boundaries

BreakIterator không tự làm
→ dịch ngôn ngữ
→ hiểu semantic meaning của câu
→ stemming / lemmatization
→ full NLP tokenization cho mọi domain
→ render text lên màn hình
```

Locale được dùng để chọn rule phù hợp:

```java
BreakIterator iterator = BreakIterator.getWordInstance(
        Locale.forLanguageTag("vi-VN")
);
```

Vì vậy boundary cần giữ là:

```text
String/Unicode module
→ text được biểu diễn như thế nào

Localization + BreakIterator
→ natural-language boundary nằm ở đâu

UI / NLP framework
→ dùng các boundary đó để selection/layout/analysis sâu hơn
```

Chapter tiếp theo xử lý một vấn đề khác của natural-language text: **thứ tự lưu text và thứ tự người dùng nhìn thấy có thể khác nhau khi LTR và RTL xuất hiện cùng nhau**.
