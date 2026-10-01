# Phân tách văn bản theo ngôn ngữ

Sau khi biết cách dịch, định dạng và sắp xếp văn bản, còn một câu hỏi nền tảng khác: **“đoạn văn bản này nên được chia ở đâu?”**

Nếu chỉ làm với tiếng Anh đơn giản, lập trình viên dễ nghĩ:

```text
ký tự → một char
từ    → split theo dấu cách
câu   → split theo dấu chấm
dòng  → xuống dòng ở dấu cách gần nhất
```

Các giả định này không đúng cho mọi văn bản Unicode và mọi ngôn ngữ. Java cung cấp `BreakIterator` để phân tích ranh giới của văn bản ngôn ngữ tự nhiên.

## <a id="breakiterator-model">BreakIterator là gì và tại sao cần nó?</a>

`BreakIterator` là API xác định **các vị trí biên hợp lệ** trong một chuỗi văn bản.

Nó có bốn vai trò chính:

```text
ranh giới ký tự
→ ranh giới đơn vị ký tự người dùng nhìn thấy

ranh giới từ
→ ranh giới từ/token ở mức ngôn ngữ tự nhiên

ranh giới câu
→ ranh giới câu

ranh giới dòng
→ vị trí có thể ngắt dòng
```

Ta cần nó vì **ranh giới trong văn bản không phải lúc nào cũng suy ra đúng bằng một ký tự ASCII cố định**.

Ví dụ:

```text
"hello world"
```

có dấu cách rõ ràng, nhưng nhiều hệ chữ/ngôn ngữ không dùng dấu cách theo cách giống tiếng Anh. Tương tự, dấu chấm không phải lúc nào cũng có nghĩa “kết thúc câu”.

Các phương thức tạo chính:

```java
BreakIterator.getCharacterInstance(locale);
BreakIterator.getWordInstance(locale);
BreakIterator.getSentenceInstance(locale);
BreakIterator.getLineInstance(locale);
```

Sau khi gắn văn bản bằng `setText(...)`, bộ lặp di chuyển qua các ranh giới bằng `first()`, `next()`, `previous()`, `following(...)`, `preceding(...)`.

## <a id="character-boundary">Ranh giới ký tự và grapheme cluster</a>

Ở mô-đun `String`, người học đã biết:

```text
char
≠ Unicode code point
≠ luôn luôn là "một ký tự người dùng nhìn thấy"
```

Bản địa hóa bổ sung bước tiếp theo: khi cần di chuyển/cắt văn bản theo **ký tự mà người dùng cảm nhận**, không nên mặc định mỗi UTF-16 `char` là một ký tự hoàn chỉnh.

```java
BreakIterator iterator = BreakIterator.getCharacterInstance(locale);
iterator.setText(text);

for (int end = iterator.first(), start = end;
     (end = iterator.next()) != BreakIterator.DONE;
     start = end) {
    String unit = text.substring(start, end);
}
```

`getCharacterInstance(...)` giúp tìm ranh giới tương ứng với các cụm ký tự mà người dùng cảm nhận như một đơn vị, thay vì cắt văn bản mù theo chỉ số UTF-16.

Ranh giới này có quan hệ chặt với **grapheme cluster** trong Unicode. Phần lý thuyết code point/grapheme/chuẩn hóa thuộc mô-đun `String`; chương này chỉ sở hữu việc **dùng phân tích ranh giới trong bản địa hóa**.

Trường hợp sử dụng điển hình:

```text
di chuyển con trỏ
chọn văn bản
cắt ngắn an toàn
di chuyển theo ký tự trong UI
```

## <a id="word-sentence-boundary">Ranh giới từ và câu</a>

`BreakIterator.getWordInstance(locale)` giúp tìm các ranh giới của văn bản theo quy tắc của Locale:

```java
BreakIterator words = BreakIterator.getWordInstance(locale);
words.setText(text);
```

Không nên hiểu API này đơn giản là “trả danh sách từ đã lọc sạch dấu câu”. `BreakIterator` chủ yếu trả **vị trí ranh giới**. Mã sử dụng vẫn phải quyết định đoạn nào là từ có ý nghĩa cho trường hợp sử dụng của mình.

Tương tự:

```java
BreakIterator sentences = BreakIterator.getSentenceInstance(locale);
sentences.setText(text);
```

giúp tìm ranh giới câu mà không ghi cứng quy tắc tách theo dấu chấm trực tiếp như `split("\\.")`.

Tại sao `split(" ")` / `split("\\.")` vẫn là mô hình yếu?

```text
dấu cách không phải dấu phân cách từ chung cho mọi ngôn ngữ
dấu câu có thể nằm trong từ viết tắt hoặc số
dấu kết thúc câu khác nhau theo ngôn ngữ
văn bản Unicode có nhiều loại khoảng trắng/dấu câu
```

`BreakIterator` đưa kiến thức về ranh giới ngôn ngữ/Unicode xuống API chuẩn thay vì bắt ứng dụng tự đoán.

## <a id="line-boundary">Ranh giới dòng không chỉ là ký tự xuống dòng</a>

`BreakIterator.getLineInstance(locale)` tìm các **vị trí có thể ngắt dòng** — những vị trí mà bộ dựng hiển thị văn bản có thể xem xét để xuống dòng.

Khác với:

```text
\n
→ newline đã tồn tại trong dữ liệu
```

vị trí có thể ngắt dòng trả lời:

```text
Nếu UI phải tự ngắt một dòng dài,
chỗ nào là ranh giới hợp lệ để cân nhắc ngắt?
```

Đây là kiến thức quan trọng cho UI/bộ máy bố trí, dựng tài liệu và trình soạn thảo văn bản. Phần xử lý phía máy chủ thông thường hiếm khi tự bố trí văn bản, nhưng lập trình viên nên biết việc “cắt dòng mỗi N ký tự” có thể phá văn bản ngôn ngữ tự nhiên.

## <a id="breakiterator-boundary">Vai trò và giới hạn của BreakIterator</a>

`BreakIterator` giải quyết **phân tích ranh giới**, không giải quyết mọi bài toán xử lý ngôn ngữ tự nhiên.

```text
BreakIterator làm tốt
→ tìm ranh giới ký tự/từ/câu/dòng

BreakIterator không tự làm
→ dịch ngôn ngữ
→ hiểu ý nghĩa ngữ nghĩa của câu
→ stemming / lemmatization
→ tokenization NLP đầy đủ cho mọi miền bài toán
→ dựng văn bản lên màn hình
```

Locale được dùng để chọn quy tắc phù hợp:

```java
BreakIterator iterator = BreakIterator.getWordInstance(
        Locale.forLanguageTag("vi-VN")
);
```

Vì vậy ranh giới trách nhiệm cần giữ là:

```text
Mô-đun String/Unicode
→ văn bản được biểu diễn như thế nào

Bản địa hóa + BreakIterator
→ ranh giới của ngôn ngữ tự nhiên nằm ở đâu

UI / khung làm việc xử lý ngôn ngữ tự nhiên
→ dùng các ranh giới đó để chọn vùng/bố trí/phân tích sâu hơn
```

Chương tiếp theo xử lý một vấn đề khác của văn bản ngôn ngữ tự nhiên: **thứ tự lưu văn bản và thứ tự người dùng nhìn thấy có thể khác nhau khi LTR và RTL xuất hiện cùng nhau**.
