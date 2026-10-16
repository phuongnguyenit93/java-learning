# STEP 3 — WORKER ONLY: Tạo Menu hai cấp

> **Chỉ dành cho worker.** Prime điều phối theo `STEP_3.md`; bạn **chỉ đọc toàn văn file `STEP_3_WORKER.md` này**, không đọc STEP Prime, STEP khác, General Rules, rules folder, TXT hoặc lịch sử bài cũ. Prime chỉ gửi **bốn dòng**: link tab; tọa độ `C3`, `A18:B19` và **hai ô tiêu đề/giá trị Special Note**; vùng ghi `A20:B<END>`; đường dẫn file này. **Prime không cung cấp nội dung hay tóm tắt Special Note; bạn BẮT BUỘC tự đọc nguyên văn hai ô đó trong Google Sheets.** Tự thực hiện trọn W1–W5.

## W1. Một lượt, một nguồn và một vùng đích

1. Một nhiệm vụ = **toàn bộ Menu hai cấp của đúng một tab**, do **một mình bạn** tạo/kiểm tra/ghi trong một lượt: đọc → soạn → QA → preflight → ghi → readback → báo cáo → SLEEPING. Không chia giữa hai worker, không PREPARED/PHA A-B.
2. Mở đúng **link tab/gid** Prime đưa, xác minh tab; đọc **nguyên văn toàn bộ `C3`** (giữ mọi dòng xuống hàng, lưu ý và ràng buộc; nếu là formula, lấy nội dung hiển thị), **hai ô Special Note ở đúng tọa độ Prime giao**, **header `A18:B19`** và **vùng Menu `A20:B<END>`** để preflight/readback. Đọc **đầy đủ từng ký tự, xuống dòng và ràng buộc trong giá trị Special Note**, không lấy lời kể/tóm tắt từ Prime hay context cũ thay thế. Nếu giá trị trống vẫn phải đọc và xác nhận. Không tự tìm tab, file TXT/MD khác, câu trả lời cũ, cột C trở đi, roadmap hay video.
3. Xác minh **ô tiêu đề đúng nhãn `Special Note` và ô giá trị nằm ngay bên dưới cùng cột**. Nếu nhãn/tọa độ không đúng, thiếu hoặc không đọc được: `BLOCKER: INVALID SPECIAL NOTE CELLS`; **không tự đoán K2/K3 hay dò ô thay thế**. `C3` trống/không khả thi → `BLOCKER: EMPTY/INVALID C3`; sai tab/gid → `BLOCKER: INVALID TARGET SHEET`; header thiếu/khác nghĩa/không khớp `A18=Khu vực Menu`, `A19=Menu Cấp 1`, `B19=Menu Cấp 2` → `BLOCKER: MENU HEADER CONFLICT`. Không tự sửa những ô này.
4. Chỉ được ghi **giá trị văn bản** trong `A20:B<END>` đúng tab. Tuyệt đối không sửa `C3`, `B1`, `A18:B19`, `J3`, **hai ô Special Note (chỉ đọc)**, cột C trở đi, vùng STEP khác, format, notes/validation, hàng/tab/file khác. Không spawn worker, sửa source, commit/push hoặc tự nhận nhiệm vụ kế tiếp.

## W2. Xây dựng toàn bộ Menu đúng yêu cầu C3

1. **`C3` là đề bài chính; giá trị Special Note là hướng dẫn/giới hạn bổ sung riêng cho module.** Tự đọc trực tiếp toàn văn **cả C3 và Special Note** trước khi thiết kế Menu. Nếu ghi chú trống thì thực hiện theo C3; nếu có, **áp dụng đầy đủ các ghi chú liên quan** vào phạm vi/độ chi tiết/số lượng/chủ đề Menu, không tự lược bỏ hay thay thế bằng bản tóm tắt. Nếu ghi chú mâu thuẫn C3 hoặc yêu cầu vượt quyền/vùng ghi STEP 3, báo `BLOCKER: SPECIAL NOTE CONFLICT`, không tự chọn một phía hoặc tự mở rộng quyền. Không áp đặt số chương/mục con, WHAT/WHY/HOW hoặc khuôn riêng. Không tạo nội dung câu trả lời bài học nếu đề chỉ yêu cầu Menu.
2. Tất cả nhóm/mục con ghi liên tục từ hàng **20** theo hai cột:
   - **Menu Cấp 1:** tên chương/chủ đề lớn trong **một ô A ở hàng riêng**; B của hàng đó phải **thật sự trống**.
   - **Menu Cấp 2:** mục con đầu tiên ở **ô B của hàng ngay dưới hàng cha**; những mục con tiếp theo ở các hàng B liên tiếp; A tương ứng **thật sự trống**.
   - Khi kết thúc nhóm, Menu Cấp 1 kế tiếp ở ô A của **hàng ngay sau mục con cuối**. Không chèn hàng trống, không merge, không lặp lại tên cha trong cột A của hàng con, không có hàng chứa cả A/B.
3. Ví dụ **chỉ minh họa vị trí**, không phải Menu bắt buộc:

   | A | B |
   | --- | --- |
   | A20: Chương 1 | B20 trống |
   | A21 trống | B21: Mục 1.1 |
   | A22 trống | B22: Mục 1.2 |
   | A23: Chương 2 | B23 trống |
   | A24 trống | B24: Mục 2.1 |

4. Phải tính số hàng của **toàn bộ Menu trước khi ghi**. Nếu nhiều hơn vùng `A20:B<END>`: `BLOCKER: MENU EXCEEDS ASSIGNED RANGE`, **không cắt ngắn, không ghi một phần, không lấn sang ngoài vùng**, không tự thêm hàng.
5. Nếu Menu cũ đã tồn tại dù ở hàng sâu trong vùng được giao: **không tạo Menu thứ hai chồng lên, không xóa/sửa Menu cũ**; báo `BLOCKER: EXISTING MENU` và các hàng/ô chiếm chỗ. Không lấy lịch sử câu trả lời thay cho C3.

## W3. Preflight và ghi an toàn

1. Trước WRITE, đọc lại `C3`, **cả hai ô Special Note**, `A18:B19` và **toàn bộ `A20:B<END>`**; đảm bảo đề bài, Special Note, header và các ô nguồn không thay đổi. Không chỉ kiểm tra các hàng đầu hoặc các ô đã định ghi; phải phát hiện dữ liệu/menu cũ nằm phía dưới.
2. **Toàn bộ ô trong vùng Menu thực sự dùng** phải trống: không có `userEnteredValue` (gồm formula), `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns`. Nếu có dữ liệu ở bất kỳ ô A/B trong **cả vùng được giao**, không tự overwrite/chèn bản mới; báo `BLOCKER: TARGET CELLS NOT EMPTY`.
3. Ghi một lần (có thể dùng một batch) bằng `updateCells`, **`fields=userEnteredValue`**, chỉ điền đúng các ô A hoặc B có Menu, giữ ô còn lại của mỗi hàng thật sự trống. Không gửi chuỗi rỗng nhằm ghi đè, không thay đổi format/note/validation, không insert/merge/delete hàng.
4. Nếu write trả lỗi hoặc phản hồi không rõ: **đọc lại trạng thái thực**, không retry ghi mù hoặc ghi thêm vào dữ liệu có sẵn.

## W4. Readback, kiểm tra cấu trúc và đề bài

1. Đọc lại **toàn vùng `A20:B<END>`**, đối chiếu chính xác từng giá trị đã ghi, thứ tự, Unicode/xuống dòng; các ô không ghi vẫn phải trống. Đọc lại **`C3`, cả hai ô Special Note và header `A18:B19`**, xác minh unchanged.
2. Đếm số Menu Cấp 1/Cấp 2; xác nhận mỗi nhóm có mục con phù hợp, hàng cha chỉ A, hàng con chỉ B, **con đầu tiên sát dưới cha**, nhóm mới ngay sau con cuối, không có hàng trống ở giữa. Nội dung phải bao quát đúng **C3 và Special Note khi có nội dung**; không có bài học ở cột khác.
3. Chỉ báo `COMPLETED` khi **READBACK PASS + CONTENT/STRUCTURE QA PASS**. Nếu write thành công nhưng dữ liệu thiếu, sai hoặc không xác minh được, báo `FAILED/BLOCKER` chính xác, không tự xóa/retry tùy tiện.

## W5. Báo cáo rồi SLEEPING

Báo Prime: `STATUS=COMPLETED/BLOCKER/FAILED`, `SHEET/gid`, `SOURCE=C3`, `SPECIAL_NOTE_CELLS` (hai tọa độ, xác nhận tự đọc trực tiếp, ghi chú trống/có nội dung và đã áp dụng hoặc blocker; **không cần chép ghi chú vào báo cáo**), `OUTPUT_RANGE`, `COUNT_LEVEL_1`, `COUNT_LEVEL_2`, `PREFLIGHT`, `READBACK=PASS/FAIL`, `STRUCTURE_QA=PASS/FAIL`, `CONTENT_QA=PASS/FAIL`, `PRESERVED`, `BLOCKERS` kèm tọa độ nếu có.

**Chỉ một Menu trên một tab được giao.** Báo cáo xong release ownership rồi SLEEPING; không nhận nhiệm vụ mới nếu chưa được Prime giao, không tự cập nhật `J3` hoặc hai ô Special Note, không làm STEP khác.
