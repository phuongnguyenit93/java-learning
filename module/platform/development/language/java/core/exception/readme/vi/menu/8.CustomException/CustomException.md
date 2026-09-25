# Custom Exception

## <a id="custom-exception-purpose">Khi nào custom exception có giá trị</a>
Tạo custom exception khi failure biểu diễn stable domain/application concept mà caller, log hoặc handler cần nhận diện. Không tạo một class mới cho từng message; type nên truyền đạt category/contract có ý nghĩa.

## <a id="exception-context">Giữ context hữu ích và cause</a>
Đưa information cần để hiểu operation fail như order ID hoặc requested state, nhưng tránh secret và object dump quá lớn. Khi wrap low-level failure, constructor nên nhận original cause và preserve nó.

## <a id="exception-hierarchy-design">Thiết kế domain exception hierarchy nhỏ</a>
Giữ hierarchy nông và có mục đích. Common domain base exception có thể hỗ trợ một handling policy, còn subtype cụ thể chỉ nên tồn tại khi recovery/status decision thật sự khác. Checked hay unchecked phải theo API contract, không theo naming convention.
