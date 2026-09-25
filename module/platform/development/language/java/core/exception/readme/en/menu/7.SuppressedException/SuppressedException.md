# Suppressed Exceptions

## <a id="primary-vs-suppressed">Primary vs suppressed exception</a>
If the try-with-resources body throws and closing a resource also throws, Java keeps the body failure as the primary exception and attaches the close failure as suppressed. This preserves the failure that caused control to leave the body while retaining cleanup evidence.

## <a id="get-suppressed">Inspecting suppressed exceptions</a>
`Throwable.getSuppressed()` returns the attached cleanup failures. Logging frameworks typically print them with the primary stack trace, but custom error/reporting code should preserve them when translating or serializing diagnostic information.

## <a id="close-failure">Close failure during another failure</a>
With multiple resources, several `close()` calls can fail and each later cleanup failure may be suppressed according to resource order. Do not replace this behavior with manual code that blindly catches/ignores close failures; cleanup failures can be important evidence of partial writes, transaction/flush problems, or resource corruption.
