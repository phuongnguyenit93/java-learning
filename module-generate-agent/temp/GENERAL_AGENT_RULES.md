# GENERAL AGENT RULES

Các quy định chung áp dụng cho các tác vụ generate module trong thư mục `module-generate-agent/temp/`.

## 1. Nắm rõ rule context trước mỗi STEP — bắt buộc

- **Nguồn rules bắt buộc là toàn bộ thư mục `module-generate-agent/rules/`**, không phải các file rules đặt trong `module-generate-agent/temp/`. Đặc biệt, quy định dành riêng cho Prime nằm ở **`module-generate-agent/rules/PRIME_RULES.md`**.
- **Trước khi bắt đầu MỖI STEP**, Prime phải đọc `module-generate-agent/temp/GENERAL_AGENT_RULES.md`, **liệt kê toàn bộ file hiện có trong `module-generate-agent/rules/` và đọc đầy đủ TỪNG file rules** (bao gồm `PRIME_RULES.md`, `HEARTBEAT.md` và mọi file rules được bổ sung sau này), rồi đọc file `module-generate-agent/temp/STEP_<N>.md` tương ứng cùng những ngữ cảnh đầu vào mà STEP đó yêu cầu. Không chọn đọc chỉ một rules file và không mặc định context từ STEP/phiên chat trước còn chính xác.
- Nếu đã quên, không còn chắc chắn, context bị rút gọn/compaction, có thay đổi trong `rules/` hoặc chưa đọc hết nội dung liên quan, **phải đọc/đọc lại toàn bộ rules folder và tài liệu STEP cần thiết trước khi giao worker hoặc thực hiện thao tác**. Không được viện lý do đã đọc ở một lần trước để bỏ qua bước xác nhận này.
- Prime phải xác định rõ **phạm vi toàn STEP, thứ tự thực hiện, điều kiện hoàn thành, vùng đọc/ghi, ownership, giới hạn worker và cách xác minh** trước khi phân công. Khi chuyển sang STEP mới, lặp lại cổng kiểm tra context này.
- Worker phải được giao đủ quy định áp dụng cho nhiệm vụ. Nếu STEP quy định worker chỉ đọc trực tiếp các ô trên Sheets và **không đọc file MD/TXT**, Prime chịu trách nhiệm nắm rules và truyền **chính xác những chỉ dẫn cần thiết**; không tự ý yêu cầu worker đọc thêm file trái với STEP. Nếu STEP bắt buộc worker đọc rules trực tiếp (ví dụ STEP 5), worker phải tuân thủ.
- `module-generate-agent/rules/PRIME_RULES.md` là quy định **bắt buộc dành cho Prime**, áp dụng xuyên suốt tất cả STEP nhưng **không thay thế việc đọc các file rules khác trong cùng thư mục**. Khi có mâu thuẫn, không được âm thầm bỏ qua; phải đối chiếu ràng buộc cụ thể của STEP và báo blocker nếu không thể thực hiện an toàn.

## 2. Giới hạn và tái sử dụng sub-worker

- **Mặc định tối đa 3 sub-worker trong pool tại mọi thời điểm.** Tính cả worker đang `ACTIVE` và worker đang `SLEEPING` (có thể tái sử dụng).
- **Luôn ưu tiên tái sử dụng worker hiện có** cho nhiệm vụ phù hợp. Worker `SLEEPING` phải được đánh thức/giao nhiệm vụ mới thay vì khởi tạo một worker khác.
- **Tuyệt đối không spawn worker mới khi vẫn còn bất kỳ worker `SLEEPING` nào**, kể cả khi tổng số worker hiện tại chưa đạt giới hạn cho phép.
- Chỉ xem xét spawn worker mới khi **không còn worker `SLEEPING`**, có nhu cầu thực sự và tổng số worker `ACTIVE` + `SLEEPING` **nhỏ hơn giới hạn cho phép (mặc định 3)**. Worker `FAILED` hoặc `FINISHED` không còn là phiên có thể tái sử dụng thì được phép thay thế trong giới hạn này.
- **Ngoại lệ theo session:** nếu người dùng quy định rõ giới hạn khác cho một session đang chạy, chỉ áp dụng giới hạn đó **trong đúng session ấy**; không sửa giới hạn mặc định của tài liệu, không tự mang ngoại lệ sang session/tác vụ khác.
- **Phải kiểm tra trạng thái pool trước mỗi quyết định spawn.** Nếu worker hiện tại không phù hợp để nhận nhiệm vụ, chờ hoặc điều phối lại; không dùng lý do đó để vượt giới hạn hay bỏ qua quy tắc tái sử dụng.

## 3. Nguồn dữ liệu Excel / Google Sheets

- Với **mọi tác vụ cần đọc hoặc ghi thông tin vào Excel/Google Sheets**, **Prime** phải đọc file `module-generate-agent/temp/module-generate-info.txt` trước khi giao việc.
- File TXT chứa URL gốc dưới nhãn `Excel Page URL` và danh sách mapping **`<TÊN MODULE> sheet id : <SHEET_ID>`** được STEP 1 ghi sau khi clone tab. `sheet id` là **ID của tab (gid)**, tuyệt đối không nhầm với spreadsheet/file ID nằm trong URL gốc.
- Prime lấy **URL gốc + sheet id đúng tên module** từ TXT để lập link đến tab cụ thể: `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>` (chuẩn hóa dấu `/` hoặc phần `/edit` nếu URL gốc đã có). **Không hardcode URL hoặc tự suy đoán sheet id**.
- Prime tự kiểm tra rằng tab trong link có tên/module đúng như mapping, rồi **gửi trực tiếp link chính xác cho sub-worker, kèm địa chỉ ô/vùng cần đọc và ô/vùng được phép ghi**. Không giao chung chung kiểu “tự đọc file MD/TXT rồi tìm sheet”, không để worker tự dò tab.
- Prime **không gửi nội dung câu hỏi, đáp án, suy luận hay context các lượt trước** nếu nhiệm vụ yêu cầu worker độc lập; chỉ gửi thông tin định vị tác vụ (link tab, địa chỉ ô nguồn/đích và yêu cầu thực hiện tối thiểu).
- Worker dùng **link và tọa độ Prime giao** để đọc trực tiếp dữ liệu từ tab. Worker **không phải đọc file Markdown/TXT để tìm đường dẫn**, không lấy link từ trí nhớ hay tự chọn tab khác.
- Nếu thiếu mapping, sheet id sai, hoặc tên module có nhiều mapping xung đột, Prime phải xác minh/sửa nguồn cấu hình theo STEP phù hợp hoặc báo blocker **trước khi giao worker**; không gửi link phỏng đoán.
- Chỉ đọc/ghi đúng phạm vi được giao. Không chỉnh sửa `Template`, các tab khác hoặc ô ngoài phạm vi nhiệm vụ nếu chưa được yêu cầu.
- Sau thao tác ghi, đọc lại vị trí đích để xác minh kết quả; nếu không truy cập được file TXT, spreadsheet hoặc tab đích thì báo lỗi, không tuyên bố hoàn thành.

## 4. Phạm vi

Áp dụng đồng thời cổng kiểm tra context, giới hạn/tái sử dụng worker, quy tắc định vị Google Sheets và **toàn bộ rules hiện hành trong `module-generate-agent/rules/`** (bao gồm `PRIME_RULES.md`). Các quy định mới sẽ được bổ sung khi người dùng yêu cầu.
