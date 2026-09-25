# Unicode, char và Code Point

## <a id="utf16-char-model">Java char và UTF-16</a>
`char` trong Java là UTF-16 code unit 16-bit, không guarantee là một Unicode character hoàn chỉnh. Code point trong BMP thường fit một char; supplementary code point cần surrogate pair. `String.length()` đếm UTF-16 code unit.

## <a id="code-point">Unicode code point</a>
Code point là Unicode value identifier như U+0041 hay U+1F600. Java có `codePointAt`, `codePointCount`, `offsetByCodePoints` và `String.codePoints()` để xử lý theo code point.

## <a id="surrogate-pairs">Surrogate pair</a>
Supplementary code point được encode bằng high-surrogate + low-surrogate. Index String bằng `charAt` có thể cắt đôi pair, nên algorithm iterate user text phải xác định unit cần xử lý là code unit hay code point.

## <a id="unicode-iteration">Iterate code point đúng cách</a>
Dùng `codePoints()` hoặc advance bằng `Character.charCount(codePoint)`/`offsetByCodePoints` khi supplementary character quan trọng. Đúng code point vẫn chưa chắc đúng user-perceived character.

## <a id="code-unit-code-point-grapheme">Code unit, code point và grapheme cluster</a>
Code unit là đơn vị storage; code point là Unicode abstract value; grapheme cluster gần với một character mà user cảm nhận và có thể gồm nhiều code point như base letter + combining mark hoặc emoji sequence. Cursor, truncate và UI length đôi khi cần grapheme-aware logic.

## <a id="unicode-normalization">Unicode normalization và Normalizer</a>
Unicode cho phép các code-point sequence khác nhau biểu diễn canonically equivalent text. `java.text.Normalizer` hỗ trợ NFC/NFD và NFKC/NFKD. Chọn normalization theo domain; compatibility normalization có thể cố ý làm mất một số distinction.

## <a id="canonical-equivalence">Canonical equivalence và String equality</a>
Precomposed `é` (U+00E9) và `e` + combining acute (U+0065 U+0301) nhìn giống nhau nhưng sequence khác, nên `String.equals` false trước normalization. Nếu identifier/search cần canonical equivalence, hãy normalize tại boundary đã định nghĩa.
