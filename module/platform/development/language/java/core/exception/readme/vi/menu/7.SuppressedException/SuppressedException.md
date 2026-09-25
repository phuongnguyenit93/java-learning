# Suppressed Exception

## <a id="primary-vs-suppressed">Primary và suppressed exception</a>
Nếu body của try-with-resources throw và close resource cũng throw, Java giữ body failure làm primary exception và attach close failure thành suppressed. Như vậy failure làm control rời body được ưu tiên nhưng cleanup evidence vẫn còn.

## <a id="get-suppressed">Inspect suppressed exception</a>
`Throwable.getSuppressed()` trả các cleanup failure được attach. Logging framework thường print chúng cùng primary stack trace, nhưng custom reporting/translation nên preserve chúng khi truyền diagnostic information.

## <a id="close-failure">Close failure trong khi đã có failure khác</a>
Với nhiều resource, nhiều `close()` có thể cùng fail và cleanup failure sau được suppressed theo resource order. Không nên thay bằng manual code catch/ignore close failure; cleanup failure có thể là evidence quan trọng của partial write, flush/transaction problem hoặc resource corruption.
