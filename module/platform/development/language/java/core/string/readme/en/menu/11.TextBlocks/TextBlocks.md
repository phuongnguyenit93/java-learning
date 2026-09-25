# Text Blocks

## <a id="text-block-syntax">Text block syntax</a>
Text blocks use triple quotes to express multiline String literals with less escaping. They still produce ordinary immutable `String` objects; they are source syntax, not a new runtime text type.

## <a id="incidental-whitespace">Incidental indentation</a>
The compiler removes incidental indentation based on the closing delimiter and common indentation rules. Essential indentation inside the content is preserved. Moving the closing delimiter can therefore change the resulting text even when the visible lines look similar.

## <a id="escape-processing">Escapes and line terminators</a>
Normal Java escape processing still applies after text-block indentation handling. Text blocks also support conveniences such as escaping a line terminator for continuation. If exact bytes/line endings matter, inspect/test the actual resulting String and encode with an explicit charset.

## <a id="text-block-not-template">Text blocks are not string templates</a>
A text block does not interpolate variables by itself. Concatenation, formatting, template facilities, or other APIs are still required for dynamic values. Avoid constructing SQL/JSON/HTML by raw interpolation when the target domain has a safer structured/binding API.
