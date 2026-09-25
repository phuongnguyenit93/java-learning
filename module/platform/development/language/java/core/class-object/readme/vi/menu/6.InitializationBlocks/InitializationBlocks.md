# Initialization Block

## <a id="static-initializer">Static initializer</a>
Static initializer block chạy trong class initialization và có thể thực hiện multi-statement setup cho static state. Nó nên deterministic và nhẹ; expensive I/O, environment-dependent work hay business initialization recoverable thường nên nằm nơi khác.

## <a id="instance-initializer">Instance initializer</a>
Instance initializer block chạy cho mỗi object creation sau superclass construction và trước constructor body, xen với instance field initializer theo textual order. Nó có thể share setup giữa constructor, nhưng helper method hoặc constructor delegation thường rõ hơn.

## <a id="initializer-use-cases">Use case và readability của initializer</a>
Initializer hữu ích khi syntax cần setup gần declaration hoặc anonymous/local-class pattern cần shared setup. Vì initialization order khá subtle, ưu tiên simple field initializer và constructor nếu block không làm code rõ hơn đáng kể.
