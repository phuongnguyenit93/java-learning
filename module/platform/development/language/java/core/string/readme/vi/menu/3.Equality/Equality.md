# String Equality

## <a id="string-equals">String content equality</a>
`String.equals` compare sequence UTF-16 code unit. Hai String object khác identity nhưng cùng sequence vẫn equal. Hash collection dựa vào `hashCode` compatible của String.

## <a id="string-reference-equality">Vì sao == không phải content comparison</a>
`==` trên String reference hỏi hai reference có cùng object hay không. Pooling làm một số equal string cũng cùng identity, vì vậy code sai dùng `==` có thể trông đúng trong test rồi fail với runtime-created string.

## <a id="case-insensitive-boundary">Case-insensitive comparison và locale boundary</a>
Case-insensitive comparison không phải universal substitute cho domain normalization. `equalsIgnoreCase` dùng Unicode case concept không nhận Locale; locale-sensitive case conversion như Turkish I thuộc localization-aware processing. Cần định nghĩa identifier, user text hay search key dùng locale-neutral hay locale-aware rule.
