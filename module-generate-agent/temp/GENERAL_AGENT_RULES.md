# GENERAL AGENT RULES

Các quy định chung áp dụng cho các tác vụ generate module trong thư mục `module-generate-agent/temp/`.

## 1. Nắm rõ rule context trước mỗi STEP — bắt buộc

- **Nguồn rules bắt buộc là toàn bộ thư mục `module-generate-agent/rules/`**, không phải các file rules đặt trong `module-generate-agent/temp/`. Đặc biệt, quy định dành riêng cho Prime nằm ở **`module-generate-agent/rules/PRIME_RULES.md`**.
- **Trước khi bắt đầu MỖI STEP**, Prime phải đọc `module-generate-agent/temp/GENERAL_AGENT_RULES.md`, **liệt kê toàn bộ file hiện có trong `module-generate-agent/rules/` và đọc đầy đủ TỪNG file rules** (bao gồm `PRIME_RULES.md`, `HEARTBEAT.md` và mọi file rules được bổ sung sau này), rồi đọc file `module-generate-agent/temp/STEP_<N>.md` tương ứng cùng những ngữ cảnh đầu vào mà STEP đó yêu cầu. **Nếu STEP có điều phối worker, Prime cũng phải đọc `module-generate-agent/temp/worker/STEP_<N>_WORKER.md` tương ứng.** Không chọn đọc chỉ một rules file và không mặc định context từ STEP/phiên chat trước còn chính xác.
- Nếu đã quên, không còn chắc chắn, context bị rút gọn/compaction, có thay đổi trong `rules/` hoặc chưa đọc hết nội dung liên quan, **phải đọc/đọc lại toàn bộ rules folder và tài liệu STEP cần thiết trước khi giao worker hoặc thực hiện thao tác**. Không được viện lý do đã đọc ở một lần trước để bỏ qua bước xác nhận này.
- Prime phải xác định rõ **phạm vi toàn STEP, thứ tự thực hiện, điều kiện hoàn thành, vùng đọc/ghi, ownership, giới hạn worker và cách xác minh** trước khi phân công. Khi chuyển sang STEP mới, lặp lại cổng kiểm tra context này.
- **Mọi STEP có điều phối worker bắt buộc dùng file worker-only đúng số STEP trong `temp/worker/`.** Worker phải tự đọc **TOÀN VĂN file đó** trước khi thực hiện công việc, không chờ Prime chép lại chỉ dẫn. Quy tắc không đọc file MD/TXT khác **không cấm đọc chính file worker-only này**; worker không đọc STEP Prime, STEP khác, rules/TXT/source ngoài quyền đã giao.
- `module-generate-agent/rules/PRIME_RULES.md` là quy định **bắt buộc dành cho Prime**, áp dụng xuyên suốt tất cả STEP nhưng **không thay thế việc đọc các file rules khác trong cùng thư mục**. Khi có mâu thuẫn, không được âm thầm bỏ qua; phải đối chiếu ràng buộc cụ thể của STEP và báo blocker nếu không thể thực hiện an toàn.

## 2. Quy tắc giao việc Prime → Worker cho mọi STEP có worker

**Số STEP hiện hành:** các STEP điều phối worker là **2, 3, 4, 5, 6, 7**. Phần đặt filename của STEP 6 cũ vẫn thuộc **STEP 5** (từng worker phụ trách chương tự ghi B hàng cha). **STEP 6 MỚI = tạo Interview JSON question/answer vào T:W hàng cha**, có worker-only contract `worker/STEP_6_WORKER.md`. **STEP 7 vẫn là Video**, không đổi số. Thiếu worker-only file là blocker với mọi STEP dispatch worker.

1. File nhiệm vụ duy nhất của worker tại STEP số `N` là **`/java-learning/module-generate-agent/temp/worker/STEP_N_WORKER.md`**. Thiếu file tương ứng → `BLOCKER: WORKER CONTRACT MISSING`; Prime không thay thế bằng cách nhắn nguyên quy trình trong chat.
2. **Prime chỉ được giao phạm vi đọc và phạm vi làm việc/ghi của worker**, kèm định danh nguồn/link cần thiết và **đường dẫn file worker-only**. Không gửi nội dung các ô, câu hỏi, đáp án, gợi ý, kết quả phân tích, bản nháp, rule, hướng dẫn thực thi, tóm tắt file worker hay lịch sử chat.
3. Với các STEP Google Sheets, tin giao gồm **đúng bốn dòng**: (1) link tab đã xác minh, (2) các tọa độ/vùng được phép đọc, (3) các tọa độ/vùng được phép ghi/làm việc, (4) đường dẫn tuyệt đối file worker-only trong `temp/worker/`. Prime phải thay placeholder bằng tọa độ thật; **không giao dải ghi vượt vùng quyền sở hữu**.
4. Prime tự xác định và xác minh đầy đủ vùng đọc/ghi, nguồn, ownership và worker pool trước khi giao. Worker chỉ đọc/ghi trong các vùng đã định vị, tự thực hiện các bước theo file nhiệm vụ. Nếu thiếu file nhiệm vụ hoặc chưa rõ phạm vi → `BLOCKER`, không giao phỏng đoán.
5. Prime có trách nhiệm đọc file worker-only để kiểm soát giao việc và nghiệm thu **nhưng không chuyển nội dung đó cho worker**. Worker tự đọc lại file khi bắt đầu nhiệm vụ mới hay mất context; không sử dụng nội dung phiên chat cũ để thay thế.
6. Áp dụng cho **mọi STEP hiện hành hoặc bổ sung sau này** nếu có worker. STEP không dùng worker không bắt buộc có file worker-only. STEP riêng có thể giới hạn phạm vi chặt hơn, **không được cho phép bỏ qua file worker-only hoặc thêm lời hướng dẫn vào tin giao**.

## 3. Giới hạn và tái sử dụng sub-worker

- **Mặc định tối đa 3 sub-worker trong pool tại mọi thời điểm.** Tính cả worker đang `ACTIVE` và worker đang `SLEEPING` (có thể tái sử dụng).
- **Luôn ưu tiên tái sử dụng worker hiện có** cho nhiệm vụ phù hợp. Worker `SLEEPING` phải được đánh thức/giao nhiệm vụ mới thay vì khởi tạo một worker khác.
- **Tuyệt đối không spawn worker mới khi vẫn còn bất kỳ worker `SLEEPING` nào**, kể cả khi tổng số worker hiện tại chưa đạt giới hạn cho phép.
- Chỉ xem xét spawn worker mới khi **không còn worker `SLEEPING`**, có nhu cầu thực sự và tổng số worker `ACTIVE` + `SLEEPING` **nhỏ hơn giới hạn cho phép (mặc định 3)**. Worker `FAILED` hoặc `FINISHED` không còn là phiên có thể tái sử dụng thì được phép thay thế trong giới hạn này.
- **Ngoại lệ theo session:** nếu người dùng quy định rõ giới hạn khác cho một session đang chạy, chỉ áp dụng giới hạn đó **trong đúng session ấy**; không sửa giới hạn mặc định của tài liệu, không tự mang ngoại lệ sang session/tác vụ khác.
- **Phải kiểm tra trạng thái pool trước mỗi quyết định spawn.** Nếu worker hiện tại không phù hợp để nhận nhiệm vụ, chờ hoặc điều phối lại; không dùng lý do đó để vượt giới hạn hay bỏ qua quy tắc tái sử dụng.

## 4. Nguồn dữ liệu Excel / Google Sheets

- Với **mọi tác vụ cần đọc hoặc ghi thông tin vào Excel/Google Sheets**, **Prime** phải đọc file `module-generate-agent/temp/module-generate-info.txt` trước khi giao việc.
- File TXT chứa URL gốc dưới nhãn `Excel Page URL` và danh sách mapping **`<TÊN MODULE> sheet id : <SHEET_ID>`** được STEP 1 ghi sau khi clone tab. `sheet id` là **ID của tab (gid)**, tuyệt đối không nhầm với spreadsheet/file ID nằm trong URL gốc.
- Prime lấy **URL gốc + sheet id đúng tên module** từ TXT để lập link đến tab cụ thể: `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>` (chuẩn hóa dấu `/` hoặc phần `/edit` nếu URL gốc đã có). **Không hardcode URL hoặc tự suy đoán sheet id**.
- Prime tự kiểm tra rằng tab trong link có tên/module đúng như mapping, rồi **chỉ gửi link tab, các vùng cần đọc, các vùng được phép làm việc/ghi và đường dẫn worker-only** đúng bốn dòng theo mục 2. Không để worker tự dò tab hoặc tài liệu khác.
- Prime **không gửi nội dung câu hỏi, đáp án, suy luận hay context các lượt trước**; chỉ gửi thông tin định vị và đường dẫn file nhiệm vụ, không gửi giải thích hoặc tóm tắt nhiệm vụ.
- Worker dùng **link và tọa độ Prime giao** để đọc trực tiếp dữ liệu từ tab, **tự đọc hướng dẫn ở `temp/worker/STEP_N_WORKER.md`**, và không đọc các MD/TXT khác để tự tìm link/tab.
- Nếu thiếu mapping, sheet id sai, hoặc tên module có nhiều mapping xung đột, Prime phải xác minh/sửa nguồn cấu hình theo STEP phù hợp hoặc báo blocker **trước khi giao worker**; không gửi link phỏng đoán.
- Chỉ đọc/ghi đúng phạm vi được giao. Không chỉnh sửa `Template`, các tab khác hoặc ô ngoài phạm vi nhiệm vụ nếu chưa được yêu cầu.
- Sau thao tác ghi, đọc lại vị trí đích để xác minh kết quả; nếu không truy cập được file TXT, spreadsheet hoặc tab đích thì báo lỗi, không tuyên bố hoàn thành.

## 5. Phạm vi

Áp dụng đồng thời cổng kiểm tra context, **hợp đồng giao việc Prime → Worker**, giới hạn/tái sử dụng worker, quy tắc định vị Google Sheets và **toàn bộ rules hiện hành trong `module-generate-agent/rules/`** (bao gồm `PRIME_RULES.md`). Các quy định mới sẽ được bổ sung khi người dùng yêu cầu.

## 6. Prime kiểm tra/đồng bộ Google Sheets → ĐÚNG MỘT module sau STEP 2, 3, 4, 5, 6

- Với **STEP 2, 3, 4, 5 và 6**, **chính Prime** chịu trách nhiệm gọi task **module-local ByLocation** ở checkpoint chỉ khi **tất cả sub-worker của TOÀN tab hoàn thành, giải phóng ownership, Prime exact readback + QA PASS**. Không gọi từ worker, không chạy sau từng hàng/chương hoặc với trial một phần tab.
- **TUYỆT ĐỐI KHÔNG gọi `generateModuleContentFromSheetByFiles`** (root toàn spreadsheet), không dùng task tổng hợp `generateModuleContentFromSheetByLocation`. STEP 2 → `generateModuleRoadmapFromSheetByLocation`; STEP 3/4/5 → `generateModuleReadmeFromSheetByLocation`; STEP 6 → `generateModuleInterviewFromSheetByLocation` (sau Normalize).
- Xác minh **fileUrl/gid/tên tab/B1**, **I3 = đường dẫn module thực tế** và Gradle module path được đăng ký; **không suy đoán path từ tên tab**. Lấy URL/gid từ mapping đã xác minh và truyền **`-PfileUrl=<URL> -PsheetID=<GID>`** vào task module-local (hoặc dùng extension đã cấu hình thật trong module). `task.gradle` chỉ là DSL tham khảo, không phải cấu hình thực thi. Thiếu module/task/config, I3 lệch hoặc lỗi xác thực → `BLOCKER: MODULE SYNC CONFIG`, không fallback ByFiles.
- Chạy tại **root `java-learning`**: `.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:<TASK_NAME> -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=DRY_RUN`; kiểm tra exit code + **status thực**, chỉ chạy cùng task/module với `-PsheetMode=APPLY` khi dữ liệu nguồn đầy đủ và DRY_RUN an toàn. Sau APPLY kiểm tra status và file đích đúng module. `DRY_RUN`/`SKIPPED_*` không phải sync thành công.
- **STEP 3/4 là checkpoint thử task an toàn, không được APPLY dữ liệu chưa hoàn chỉnh.** STEP 3 cố ý để B cha trống và Knowledge chưa được tạo; STEP 4 đã có Knowledge nhưng filename B cha chỉ được STEP 5 đặt. Khi importer báo `SKIPPED_MISSING_FILENAME` hoặc không đủ Knowledge/filename đã được review, báo **`MODULE_SYNC=DEFERRED_UNTIL_STEP_5`**, `APPLY=NOT_RUN`; không coi module đã đồng bộ. Nếu thiếu cấu hình/Gradle lỗi thì báo `BLOCKER`, không che bằng DEFERRED. Được cập nhật J3 của STEP 3/4 nếu riêng phần Sheet đã QA PASS và status DEFERRED được báo rõ; không được tuyên bố module đã sync.
- **README thực chỉ APPLY ở cuối STEP 5** sau toàn bộ Knowledge C:N đã review/QA PASS, B cha đầy đủ/duy nhất và importer DRY_RUN hợp lệ. Không tự điền filename thay worker. STEP 5 bắt buộc trả công nợ `DEFERRED_UNTIL_STEP_5` của STEP 3/4.
- **STEP 6 có hai task liên tiếp khác nhau:** (1) root `normalizeInterviewQuestionsFromSheet -PfileUrl=<URL> -PsheetID=<GID> -PinterviewMode=DRY_RUN` rồi `APPLY`, với exact readback JSON đầy đủ trên Sheet; (2) **module-local** `generateModuleInterviewFromSheetByLocation` với `-PsheetMode=DRY_RUN` rồi `APPLY` để sinh `interview/vi/question.yml`. Nếu importer báo `SKIPPED_NO_INTERVIEW_SCHEMA` / `SKIPPED_EMPTY_INTERVIEW`, không coi STEP 6 đã đồng bộ.
- Prime ghi **`MODULE_SYNC_TASK`, `MODULE_PATH`, `TAB_GID`, `DRY_RUN`, `APPLY`, `SYNC_STATUS`, `DESTINATION`, `BLOCKER/DEFERRED`** cho các STEP 2/3/4/5/6. STEP 2/5/6 chỉ nâng J3 khi import PASS/UNCHANGED được xác minh; STEP 3/4 có thể nâng J3 khi Sheet QA PASS và sync được báo **DEFERRED**, không báo đồng bộ thành công. **Không commit/push**, không dùng `ByFiles`.
