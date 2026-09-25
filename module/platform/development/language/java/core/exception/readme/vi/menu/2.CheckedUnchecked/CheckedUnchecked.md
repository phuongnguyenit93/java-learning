# Checked và Unchecked Exception

## <a id="checked-exception">Checked exception như compile-time contract</a>
Checked exception là subtype của `Exception` nhưng không phải `RuntimeException`. Nếu operation reachable có thể throw nó, source phải catch hoặc declare theo compile-time rule. Failure vì vậy trở thành một phần static contract của method.

## <a id="unchecked-exception">Semantics của RuntimeException</a>
`RuntimeException` và subtype là unchecked: caller không bị compiler ép catch/declare. Chúng thường biểu diễn programming/precondition/state problem nhưng library cũng có thể dùng cho operational failure. “Unchecked” mô tả compiler rule, không nói severity hay recoverability.

## <a id="checked-vs-unchecked-design">Chọn checked hay unchecked</a>
Chọn dựa trên API contract: caller điển hình có thể/nên recovery meaningful tại call site không, và forcing declaration có làm contract rõ hơn không. Không nên đổi mọi thứ sang unchecked chỉ để bỏ `throws`, cũng không nên tạo checked exception mà mọi layer chỉ rethrow máy móc.
