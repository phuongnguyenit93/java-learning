# Text Block

## <a id="text-block-syntax">Syntax text block</a>
Text block dùng triple quote để biểu diễn multiline String literal với ít escaping hơn. Nó vẫn tạo ordinary immutable `String`; đây là source syntax chứ không phải runtime text type mới.

## <a id="incidental-whitespace">Incidental indentation</a>
Compiler loại incidental indentation dựa trên closing delimiter/common indentation rule. Essential indentation trong content được giữ. Di chuyển closing delimiter vì vậy có thể làm resulting text đổi dù source nhìn gần giống.

## <a id="escape-processing">Escape và line terminator</a>
Java escape processing bình thường vẫn áp dụng sau text-block indentation handling. Text block còn có convenience như escape line terminator để continue line. Nếu exact byte/line ending quan trọng, hãy test resulting String và encode bằng charset explicit.

## <a id="text-block-not-template">Text block không phải string template</a>
Text block không tự interpolate variable. Concatenation, formatting, template facility hoặc API khác vẫn cần cho dynamic value. Không build SQL/JSON/HTML bằng raw interpolation nếu target domain có structured/binding API an toàn hơn.
