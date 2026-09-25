# Throwable

## <a id="throwable-hierarchy">Throwable hierarchy</a>
`Throwable` là root của throw/catch hierarchy trong Java. Hai branch lớn là `Error` và `Exception`; `RuntimeException` là unchecked branch dưới `Exception`. Chỉ instance của `Throwable` mới được throw trực tiếp bởi exception mechanism.

## <a id="error-vs-exception">Error và Exception</a>
`Error` thường biểu diễn JVM/environment condition nghiêm trọng hoặc linkage/assertion failure mà application code không nên generic-recover. `Exception` biểu diễn condition API/application có thể model, handle, translate hoặc propagate. Catch `Throwable` quá rộng dễ swallow fatal condition.

## <a id="stack-trace-cause">Stack trace, cause và causal chain</a>
Throwable ghi stack trace với call frame gần thời điểm create/throw và có thể giữ cause. Khi wrap exception nên preserve original throwable làm cause để diagnostic chain vẫn nối high-level context tới low-level failure. Stack trace là diagnostic evidence, không phải stable machine API.
