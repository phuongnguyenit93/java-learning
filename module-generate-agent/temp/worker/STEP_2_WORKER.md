# STEP 2 — WORKER ONLY: Roadmap Title / Purpose / Objectives

> **Chỉ dành cho worker.** Prime sử dụng `STEP_2.md` để điều phối; bạn **chỉ đọc toàn văn file `worker/STEP_2_WORKER.md` này**. Tin Prime giao gồm **bốn dòng: link tab; tọa độ `C2`, Special Note và B17; vùng ghi `F2:H<END>` và `B17`; đường dẫn file này**. **Prime không gửi nội dung/tóm tắt Special Note: bạn BẮT BUỘC tự đọc nguyên văn trực tiếp trên đúng tab Google Sheets.** Tự làm đầy đủ W1–W5.

## W1. Nguồn và phạm vi một nhiệm vụ

1. **Một lượt duy nhất = một Roadmap hoàn chỉnh của một tab.** Đọc → suy luận/soạn → QA → preflight → ghi → readback → báo cáo → SLEEPING. Không tách nhiều worker, không có pha PREPARED / PHA A-B hay đợi lượt chat sau.
2. Mở **đúng link tab/gid Prime giao**, xác nhận tên tab và gid khớp. Đọc **nguyên văn câu hỏi `C2`**, **ô tiêu đề Special Note và ô giá trị Special Note tại đúng HAI tọa độ Prime gửi**, cùng **vùng Roadmap `F2:H<END>` và `B17`** để nhận diện header, dữ liệu cũ, preflight và readback. **Đọc toàn bộ giá trị Special Note trực tiếp, không thay bằng tóm tắt**. Ô trống vẫn phải xác minh trống. Không tự dò tab hoặc nguồn khác, không đọc `C3` hay Menu/Knowledge/Video.
3. Xác minh **ô tiêu đề chứa đúng nhãn `Special Note`** và ô giá trị nằm ngay bên dưới cùng cột. Nếu nhãn/vị trí sai, thiếu tọa độ hoặc không đọc được một trong hai ô: `BLOCKER: INVALID SPECIAL NOTE CELLS`, **không tự đoán địa chỉ**. Nếu link/gid không đúng, `C2` trống/không khả thi hoặc vùng đích không an toàn: `BLOCKER: INVALID TARGET / EMPTY C2 / INVALID OUTPUT RANGE`.
4. Chỉ có quyền thay đổi **giá trị văn bản** của ô cần điền trong vùng `F2:H<END>` **và ô `B17` khi còn trống**. B17 phải được Prime xác minh không thuộc Menu/header hiện có; nếu ô đích đang bị chiếm, không ghi đè. Không sửa `C2`, `B1`, `J3`, **hai ô Special Note (chỉ đọc)**, cột I trở đi, Menu, Knowledge, Video, header ngoài vùng, notes, validations, format, các tab/file khác. Không spawn worker, cập nhật trạng thái STEP, sửa source hay commit/push.

## W2. Soạn hoặc hoàn thiện Roadmap đúng đề bài

1. **`C2` là đề bài chính; giá trị `Special Note` là chỉ dẫn/giới hạn bổ sung riêng cho module**. Đọc và hiểu **trực tiếp toàn văn cả hai ô** trước khi soạn. Nếu Special Note trống, chỉ áp dụng C2; nếu có nội dung, **áp dụng các ghi chú liên quan khi tạo Roadmap**, không lược bỏ, tự diễn giải lệch ý hoặc phớt lờ. Nếu Special Note mâu thuẫn C2 hoặc yêu cầu vượt vùng ghi/quyền STEP 2, báo `BLOCKER: SPECIAL NOTE CONFLICT`, không tự chọn một phía hay mở rộng quyền. Tự giải độc lập, không chép từ bài cũ hay lịch sử của worker; không mặc định WHAT/WHY/HOW hoặc tự thêm mốc.
2. Mỗi milestone là **một bộ ba đồng bộ trên cùng hàng**:
   - **F — Title:** tên mốc ngắn, phù hợp sơ đồ Roadmap, không biến thành đoạn văn dài.
   - **G — Purpose:** thông thường 1–2 câu nêu lý do/mục đích học.
   - **H — Objectives:** thông thường 2–4 mục tiêu cụ thể, có thể kiểm chứng, mỗi mục **một dòng trong cùng ô H**; không tự tạo hàng mới cho từng objective.
   Các đặc tả này là cách lưu dữ liệu, **không được lấn át ràng buộc nội dung/độ dài của chính C2**.
3. **Nếu Roadmap chưa có Title:** tạo toàn bộ các milestone theo C2, một bộ F/G/H mỗi hàng từ hàng 3; không vượt `<END>`. **Nếu đã có Title ở F:** giữ nguyên F và thứ tự/hàng; chỉ bổ sung những G/H tương ứng còn trống; không viết lại Title hay chèn milestone xen vào để đổi roadmap cũ. G/H đã có thì giữ nguyên, kiểm tra mức nhất quán thay vì tự ghi đè.
4. Kiểm tra `F2:H2`: nếu cả ba ô trống, được điền **`Tiêu đề` / `Mục đích` / `Mục tiêu học tập`**. Nếu header hiện có đúng thì giữ nguyên. Nếu header khác nghĩa, bị chiếm một phần bất thường hoặc xung đột, báo `BLOCKER: HEADER CONFLICT`, không tự sửa.
5. Nếu nội dung theo C2 cần nhiều hàng hơn vùng Prime giao, `BLOCKER: ROADMAP EXCEEDS ASSIGNED RANGE` với số hàng cần, không cắt nội dung, không ghi một phần và không lấn sang Menu.
6. **Sau khi hoàn thiện Roadmap, đồng thời soạn Introduction Markdown cho `B17`**, dùng làm **toàn văn `content/readme/docs/vi/BASE.md`**. Có H1 tên module, 1–3 đoạn giới thiệu rõ mục đích, phạm vi, tư duy nền tảng, đối tượng học và lộ trình học từ các milestone; ưu tiên nội dung đúng C2/Special Note, không sao chép nguyên bộ roadmap hay viết dài lan man. Nếu cần có thêm danh sách ngắn về mục tiêu. **Giới thiệu là bài tự đứng được, đúng cú pháp Markdown và đủ đầu mục cơ bản; không TODO/placeholder hoặc nội dung ngoài phạm vi.** Tối đa 30.000 Unicode code points trong **một ô B17**, không chia sang ô khác. Nếu B17 đã có nội dung, giữ nguyên, tự QA nội dung đó và báo `INTRODUCTION=EXISTING/UNCHANGED` hoặc `BLOCKER` nếu sai mà không được overwrite.

## W3. Preflight và ghi đúng những ô được phép

1. Chuẩn bị Roadmap hoàn chỉnh **và Introduction B17** hoặc danh sách **chính xác các ô còn trống** cần điền. Nếu đã có dữ liệu, không ghi đè; kiểm tra tính đúng đắn và báo rõ `NO WRITE NEEDED` khi phù hợp.
2. Ngay trước khi ghi, đọc lại **nguyên văn `C2`, cả hai ô Special Note, toàn vùng `F2:H<END>` và `B17`**; tất cả nguồn/ghi chú và mọi ô cần giữ phải unchanged. Mọi ô **dự định ghi** phải không chứa `userEnteredValue` (kể cả formula), `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns`. Nếu có ô bị chiếm/nguồn/ghi chú thay đổi: `BLOCKER: TARGET CELLS NOT EMPTY / SOURCE CHANGED`; **không ghi chồng hoặc ghi dở**.
3. Ghi duy nhất các ô trống thực sự cần dùng, với **`updateCells`, `fields=userEnteredValue`** (có thể batch nhiều ô thuộc cùng phạm vi), không gửi cập nhật lên các ô phải giữ nguyên. Không thay đổi định dạng, notes, validations hay các field khác; không insert/delete/merge hàng.
4. Nếu write lỗi hoặc kết quả không rõ: **đọc lại trạng thái thực tế trước**, không tự retry ghi mù hay overwrite.

## W4. Readback và QA

1. Đọc lại **toàn bộ `F2:H<END>` và `B17`** sau write; so khớp **nguyên văn từng ô** đã ghi (bao gồm Unicode, xuống dòng, khoảng trắng) và xác minh mọi ô cũ giữ nguyên. Kiểm tra lại **`C2` và hai ô Special Note unchanged**.
2. Đảm bảo mỗi Title có Purpose và Objectives khớp nội dung, các ô của cùng milestone nằm **cùng một hàng**, không có mốc/ô phát sinh sai, không vượt `<END>` và không có mục tiêu bị tách thành các hàng riêng. QA cả số mục, thứ tự, độ ngắn của Title, yêu cầu C2 **và ghi chú Special Note khi có nội dung**.
3. Kiểm tra thêm **Introduction B17**: đúng module, phù hợp roadmap/Special Note, Markdown hợp lệ, không vượt một ô, không thiếu giới thiệu cơ bản. Chỉ báo `COMPLETED` khi **ROADMAP + INTRODUCTION READBACK PASS và CONTENT_QA PASS**.

## W5. Báo cáo rồi SLEEPING

Báo Prime: `STATUS=COMPLETED/BLOCKER/FAILED`, `SHEET/gid`, `SOURCE=C2`, `SPECIAL_NOTE_CELLS` (đã đọc/applied), `OUTPUT_RANGE` + ô thực ghi, `MILESTONE_COUNT`, **`INTRODUCTION=B17`, `INTRODUCTION_QA`, `INTRODUCTION_READBACK`**, `PREFLIGHT`, `CONSISTENCY`, `READBACK=PASS/FAIL`, `CONTENT_QA=PASS/FAIL`, `PRESERVED` và `BLOCKERS`.

**Chỉ một roadmap cho tab được giao.** Báo xong, release ownership và SLEEPING; không nhận module khác, không chạy STEP khác, không chỉnh `J3` hoặc ô Special Note.
