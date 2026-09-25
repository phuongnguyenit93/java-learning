# Exception Design

## <a id="exception-boundaries">Translate exception tại abstraction boundary</a>
Translate low-level exception khi đi sang abstraction mà caller nên reasoning bằng term khác. Repository có thể đổi JDBC-specific failure thành repository/domain failure; controller boundary đổi domain failure thành HTTP response. Preserve cause và không translate chỉ để rename semantics lặp lại.

## <a id="do-not-swallow">Không swallow failure</a>
Empty catch hoặc catch chỉ comment/log rồi continue có thể để system trong state mà caller tưởng operation thành công. Chỉ swallow khi ignore failure là policy explicit, safe và loss of information đã được hiểu rõ.

## <a id="logging-boundary">Log một lần ở responsible boundary</a>
Log rồi rethrow cùng exception ở mọi layer tạo duplicate stack trace mà không thêm information. Thêm context bằng exception chaining và log ở nơi application có đủ context để quyết định severity, user impact, correlation và response policy.

## <a id="exception-as-control-flow">Tránh exception như normal control flow</a>
Exception dành cho exceptional contract outcome, không phải branch bình thường như “không tìm thấy item” khi absence là expected. Exception-based loop/parsing cũng che intent và có thể tốn cost. Model expected alternative explicit khi đó là normal behavior.

## <a id="cleanup-and-recovery">Cleanup, recovery và propagation</a>
Cleanup giải phóng resource; recovery khôi phục/chọn alternative hợp lệ; propagation chuyển decision lên trên. Catch block nên biết nó đang làm vai trò nào. Nếu không đủ information/authority để recover thì cleanup + propagation thường là design đúng.
