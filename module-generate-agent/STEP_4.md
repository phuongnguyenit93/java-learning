# STEP 4 — Tạo Knowledge README cho từng mục Menu Cấp 2



## Mục tiêu

Tạo câu trả lời **Markdown README hoàn chỉnh** cho từng mục **Menu Cấp 2** trong Google Sheets. **Một sub-worker xử lý đúng một mục cấp 2/một hàng**. Prime điều phối theo Mode A/B của session: A tối đa 3 worker, B tối đa N do user chỉ định (thiếu N → BLOCKER), tính cả ACTIVE + SLEEPING, theo `PRIME_RULES.md §0`. **Worker ghi `QA PASS` hoặc `QA FAIL` vào P cùng hàng, chỉ khi FAIL mới ghi lý do vào Q. Sau khi mọi worker hoàn tất, Prime dùng kết quả đó sửa bài mới lỗi, QA lại, rồi xóa sạch toàn bộ giá trị trong hai cột P và Q từ hàng 20 đến hàng cuối của tab, không kiểm tra/phân loại dữ liệu trước khi xóa.**

STEP 4 **dùng trực tiếp nội dung Menu Cấp 2 làm đề bài**. Không tạo bước trung gian “Câu hỏi cho AI” và không ép đổi tiêu đề chủ đề thành một câu hỏi mới. Nếu mục cấp 2 đã là câu hỏi thì trả lời chính câu đó; nếu là tên chủ đề/yêu cầu thì trình bày bài học đúng nội dung đó.

**QUY TẮC BẮT BUỘC — STEP 4 CHỈ FILL-ONLY:** Chỉ tạo README cho Menu Cấp 2 mà **toàn bộ 12 ô C:N cùng hàng trống**. Hàng có bất kỳ dữ liệu cũ nào do Prime phân loại `SKIPPED_EXISTING`, **không giao Worker, không review hoặc ghi đè**; STEP 5 xử lý nội dung cũ. **Prime duy nhất kiểm kê, preflight C:N, chọn hàng an toàn và phân công; không preflight P/Q.** Worker chỉ đọc sáu ô nguồn, soạn/ghi Knowledge C:N, readback, ghi kết quả QA P/Q và readback QA; không kiểm tra Protected Ranges/ownership/writer. **Ngoại lệ sửa trong STEP 4 chỉ dành cho Prime sửa chính bài `NEW_FILL` bị QA FAIL của phiên này**, không sửa `SKIPPED_EXISTING`.

**Không giới hạn số ký tự khi sáng tạo/soạn nội dung.** README phải **thể hiện đầy đủ toàn bộ kiến thức, yêu cầu và phạm vi của mục được giao**, không lược bỏ nội dung cần thiết, tóm tắt cho vừa một ô, hoặc tự đặt giới hạn độ dài. Mốc **30.000 ký tự chỉ là giới hạn kỹ thuật của MỖI ô khi ghi**; 360.000 ký tự là sức chứa hiện tại của **12 ô C:N trên một hàng**, **không phải mục tiêu hay hạn mức để rút ngắn nội dung khi viết**. Nếu bài hoàn chỉnh vượt sức chứa, giữ nguyên bài và báo blocker, tuyệt đối không ghi thiếu.

### Phân tách tài liệu PRIME / WORKER (STEP 4)

- **File này `STEP_4.md` là tài liệu điều phối cho PRIME. Worker không đọc file này.**
- **Toàn bộ chỉ dẫn thực thi của worker nằm trong `module-generate-agent/worker/STEP_4_WORKER.md`.** Prime giao đúng đường dẫn; worker chỉ đọc toàn văn file worker-only (không đọc STEP khác/rules/MD/TXT khác), sáu ô nguồn và các ô được giao: Knowledge C:N, QA P và Q chỉ nếu FAIL. Worker chỉ đọc vùng đích để readback sau khi ghi.
- Prime vẫn phải đọc đầy đủ `GENERAL_AGENT_RULES.md`, **liệt kê và đọc mọi file đang có trong `module-generate-agent/rules/`**, `module-generate-info.txt`, **toàn văn STEP 4 này và file worker/STEP_4_WORKER.md** để kiểm soát đúng contract. Đọc lại khi context bị rút gọn hoặc không còn chắc chắn.
- **Một lần giao = một bài = một lượt hoàn chỉnh**, không PREPARED / PHA A-B / chờ lượt chat thứ hai để được ghi. Prime cung cấp sẵn chính xác tab, sáu tọa độ nguồn, một hàng đích **đã qua preflight của Prime** và file worker-only. Worker không kiểm tra lại tính an toàn của vùng đích. Nội dung menu/README/Special Note không được copy từ Prime sang worker.
- **QUY TẮC GIAO VIỆC TUYỆT ĐỐI:** Prime chỉ gửi **bốn dòng**: link tab; tọa độ sáu ô nguồn; vùng Knowledge C:N; file `worker/STEP_4_WORKER.md`. **Không thêm P/Q vào tin giao:** worker suy ra `P<R>`/`Q<R>` từ hàng C:N theo file hướng dẫn. Không kèm giải thích, nội dung/tóm tắt Special Note, lời dẫn hay nội dung QA.

## 1. Bố cục nguồn và đích trên sheet

| Dữ liệu | Vị trí | Ý nghĩa |
| --- | --- | --- |
| Nhãn chủ đề | `A1` | Đọc để xác nhận ngữ cảnh trường thông tin, không sửa. |
| Tên module | `B1` | Đọc để biết module hiện tại, không sửa. |
| Menu Cấp 1 | `A<PARENT_ROW>` | **Prime gửi chính xác ô** chứa chương/nhóm cha. |
| Menu Cấp 2 | `B<ITEM_ROW>` | **Prime gửi chính xác ô** chứa duy nhất mục cần giải đáp. |
| Nhãn `Special Note` | Ô nhãn thực tế trong khu vực `Module Project` (ví dụ `K2`) | Prime xác minh nhãn đúng `Special Note`; Worker phải đọc nguyên văn, chỉ đọc. |
| Giá trị `Special Note` | Ô ngay dưới nhãn, cùng cột (ví dụ `K3`) | Worker phải đọc nguyên văn kể cả khi trống; áp dụng chỉ dẫn liên quan cho bài Knowledge, chỉ đọc. |
| Knowledge README | `C<ITEM_ROW>` | Ô đầu tiên ghi toàn bộ câu trả lời nếu không quá **30.000 ký tự**. |
| Phần tiếp theo nếu dài | `D<ITEM_ROW>:N<ITEM_ROW>` | Chia trên cùng hàng, tối đa N. O dành cho Difficulty, X:AA dành cho Video Script trên hàng Menu Cấp 1. |
| QA status STEP 4 | `P<ITEM_ROW>` | Worker ghi đúng `QA PASS` hoặc `QA FAIL` sau khi xử lý bài; riêng ô P trên cùng hàng. |
| Reason QA Failed STEP 4 | `Q<ITEM_ROW>` | Worker chỉ ghi lý do cụ thể khi `QA FAIL`; khi PASS không đọc/ghi/xóa Q. Prime xóa sạch P:Q từ hàng 20 trở xuống sau khi sửa hết lỗi QA. |

- Ví dụ về tọa độ: Menu Cấp 1 `A20`, Menu Cấp 2 `B21` thì Knowledge ghi từ `C21`, nếu dài tiếp tục D21 ... tối đa N21. **Ngoại lệ duy nhất ngoài C:N cho worker là P21 (QA) và Q21 (lý do nếu FAIL)**; không ghi O hoặc R trở đi.
- **Chia nguyên văn Markdown hoàn chỉnh thành các phần tối đa 30.000 ký tự theo thứ tự C→N cùng hàng**, phần cuối dưới 30.000 giữ nguyên một ô. Nếu tổng bài ≤30.000, chỉ ghi C; 62.000 → C=30.000, D=30.000, E=2.000; 60.000 → C=30.000, D=30.000. Không cắt đôi Unicode; sức chứa Knowledge tối đa 360.000 ký tự. Nếu vượt sức chứa thì báo blocker, không cắt nội dung, không dùng O hoặc **P/Q là cột QA** để chứa Markdown.
- **Chỉ xử lý hàng trống:** Prime xác định nếu C:N có bất kỳ văn bản hoặc dữ liệu đã nhập nào, dù C trống nhưng D:N có nội dung, hàng đó là `SKIPPED_EXISTING` và không giao worker. Không đánh giá tính đúng/sai/đủ/thiếu, không sửa cú pháp hay dọn fragment cũ trong STEP 4. STEP 5 sẽ đọc và cải thiện nội dung đã tồn tại. Worker nhận hàng đã được Prime duyệt và chỉ soạn, ghi, readback bài mới của mình.
- Các hàng chứa **Menu Cấp 1** (`A` có tiêu đề; `B` cùng hàng ban đầu trống và được worker **STEP 5** điền filename .md về sau) **không phải nhiệm vụ STEP 4**; không tạo câu trả lời ở `C` của những hàng này.
- Giữ nguyên header Menu tại `A18:B19` và header “Câu trả lời” tại `C19:N19`. Các ô `O19:S19` không thuộc vùng câu trả lời. **Không sửa A/B, hai ô Special Note**, Roadmap, Video hay tab khác.
- Không tự suy ra hàng cha bằng cách quét lịch sử trong worker: **Prime trực tiếp xác định và cung cấp cả `A<PARENT_ROW>` lẫn `B<ITEM_ROW>`**. Hàng cha có thể không liền kề hàng con.

## 2. Quy định dành cho Prime — xác định sheet và phân công

1. **Đọc đủ bộ tài liệu ở cổng context bên trên** trước khi điều phối và tuân thủ quy tắc worker pool. STEP 4 dùng **Mode A/B của session (A=3 mặc định; B=N do user chỉ định, thiếu N → BLOCKER; tính cả ACTIVE + SLEEPING)**. Không mang mode sang session khác. **Nếu người dùng đã loại trừ worker cụ thể hoặc yêu cầu dừng, không sử dụng worker đó và không giao tiếp việc cho đến khi có yêu cầu tiếp tục.** Một worker chỉ nhận **một câu hỏi/mục cấp 2 tại một thời điểm**.
2. **Prime đọc `module-generate-agent/module-generate-info.txt`** để lấy URL gốc ở nhãn `Excel Page URL`, cùng mapping `<TÊN MODULE> sheet id : <SHEET_ID>`. Lập link trực tiếp đến tab bằng `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>` (chuẩn hóa URL trước khi ghép). `SHEET_ID` chính là **gid của tab**, không phải spreadsheet/file ID.
3. **Xác minh đúng module trước khi giao:** Đối chiếu tab/gid, ô `A1` và `B1`. Nếu thiếu mapping, tên tab hoặc `B1` không khớp, báo `BLOCKER`, không đoán tab.
   - **Xác định chính xác hai tọa độ Special Note trên chính tab:** tìm ô tiêu đề có đúng nhãn `Special Note` trong khu vực `Module Project`; ô giá trị phải nằm **ngay bên dưới, cùng cột**. Xác minh nhãn duy nhất, đúng cấu trúc, cả hai ô tồn tại và đọc được. `K2/K3` chỉ là ví dụ hiện tại, **không tự mặc định tọa độ**. Nếu nhãn thiếu/trùng/mơ hồ hoặc ô giá trị không đọc được: `BLOCKER: SPECIAL NOTE LOCATION`, không giao bài. Ô giá trị trống vẫn hợp lệ và **phải gửi hai tọa độ**. Prime chỉ gửi địa chỉ ô, **không chuyển nội dung, diễn giải hay tóm tắt Special Note** cho worker.
4. **Prime quét toàn bộ A/B của vùng Menu từ hàng 20 và tình trạng C:N trên đúng các hàng Menu Cấp 2 trước khi giao Worker** để lập danh sách nhiệm vụ. Đây chỉ là **kiểm kê ô đã có giá trị hay chưa**, không đọc, phân tích hoặc đánh giá nội dung bài cũ.
   - Gặp hàng có `A` là Menu Cấp 1: ghi nhận tọa độ ô cha **bất kể B cùng hàng trống hay đã có filename .md hợp lệ do STEP 5 đặt**. Không coi B hàng cha là một bài cấp 2.
   - Gặp hàng có `B` là Menu Cấp 2 và `A` trống: ghép với ô Menu Cấp 1 gần nhất ở phía trên, ghi nhận **`PARENT_CELL` và `ITEM_CELL`**, cùng `OUTPUT_START = C` của hàng mục con.
   - Nếu không xác định được cha, hàng có A/B cùng chứa dữ liệu **không phải một filename .md hợp lệ ở B cha**, hoặc menu mơ hồ: **báo blocker** cho hàng đó, không giao theo suy đoán.
   - **Phân loại toàn tab do Prime thực hiện:** `PENDING_EMPTY` khi cả C:N trống, `SKIPPED_EXISTING` khi bất kỳ C:N có dữ liệu. Formula/chip hoặc thực sự không đọc được vùng Knowledge đích → Prime báo `BLOCKER: TARGET_CONFLICT`/`TARGET_READ_FAILED`. **Không kiểm tra trạng thái hay nội dung P/Q trước khi giao hàng; P/Q là vùng ghi QA tạm thời của STEP 4 và được sử dụng trực tiếp.** Không yêu cầu Protected Ranges/quyền từng ô/người đang mở Sheet. Ownership là assignment ledger: một worker/hàng. Chỉ giao hàng đã preflight C:N theo thứ tự tăng dần. Thống kê `TOTAL_MENU_ITEMS`, `SKIPPED_EXISTING`, `PENDING_EMPTY`, `ACTIVE`, `NEW_FILL_DONE`, `QA_PASS`, `QA_FAIL`, `BLOCKER`, `FAILED`. Session mới kiểm kê lại vùng Knowledge theo trạng thái thực tế, không review bài cũ.
5. **Kiểm tra trạng thái toàn bộ worker pool trước khi phân công/spawn** và tuân thủ `GENERAL_AGENT_RULES.md`:
   - Ưu tiên **tái sử dụng worker SLEEPING**; không spawn mới khi còn worker SLEEPING.
   - Không vượt quá **giới hạn pool Mode A/B của session (A=3 mặc định; B=N do user chỉ định)** gồm ACTIVE và SLEEPING. Mỗi lệnh spawn chỉ tạo một worker, chờ kết quả trước khi tạo tiếp. Nếu pool có sẵn vượt giới hạn áp dụng cho session, dừng việc tăng số worker và báo vấn đề cần điều phối lại; không tự kết thúc hoặc can thiệp vào worker của tác vụ khác.
   - Worker đang ACTIVE không được nhận thêm câu hỏi thứ hai. Có thể giao đồng thời tối đa **số hàng khác nhau bằng giới hạn worker cho session**; khi một worker hoàn thành và trở lại SLEEPING, mới giao một hàng kế tiếp.
6. **Prime chỉ gửi bốn dòng cho Worker sau khi Prime preflight C:N (không kiểm tra P/Q):** link tab; tọa độ `A1`, `B1`, `A<PARENT_ROW>`, `B<ITEM_ROW>`, `<SPECIAL_NOTE_TITLE_CELL>`, `<SPECIAL_NOTE_VALUE_CELL>`; vùng Knowledge `C<ITEM_ROW>:N<ITEM_ROW>`; đường dẫn `/java-learning/module-generate-agent/worker/STEP_4_WORKER.md`. **P/Q được worker suy ra cùng hàng từ C:N, không thêm dòng giao việc.** Không kèm chỉ dẫn khác, tóm tắt hoặc nội dung Special Note.
7. **Giữ worker độc lập về nội dung:** Prime **không gửi nguyên văn nội dung các ô, đặc biệt là Special Note**, tài liệu STEP trước, đáp án gợi ý, lịch sử hội thoại, cấu trúc bài học, tóm tắt quy định hay câu lệnh thực thi. **Không giải thích thêm kể cả khi định nhắc preflight, giới hạn 30.000, chia ô hoặc readback**; mọi yêu cầu đã nằm trong `worker/STEP_4_WORKER.md`.
8. **Prime chỉ preflight vùng Knowledge C:N:** chỉ giao hàng Knowledge trống và không xung đột assignment ledger. **Không cần đọc, xác minh, phân loại nội dung hay quyền sở hữu của P/Q trước khi giao; STEP 4 sử dụng trực tiếp P để ghi QA và Q khi FAIL.** Không truy vấn Protected Ranges/quyền từng ô/người đang mở Sheet. Worker tin vào Prime, không preflight lại, chỉ đọc sáu ô nguồn, ghi C:N, readback, ghi `QA PASS`/`QA FAIL` tại P, Q chỉ nếu FAIL, rồi readback P/Q. Lỗi connector thực tế báo cho Prime.
9. **Prime QA và sửa lỗi chỉ đối với `NEW_FILL` của phiên này:** sau báo cáo, Prime readback C:N, P/Q và QA nội dung/code/stdout/Special Note. Nếu `P=QA FAIL`, lấy lý do từ Q vào ledger. **Sau khi toàn bộ worker đã hoàn tất**, Prime sửa các bài mới bị QA FAIL trong phạm vi C:N, không sửa `SKIPPED_EXISTING`/nội dung lịch sử. Sau sửa, Prime QA/readback lại rồi đặt P=`QA PASS`, xóa reason Q của bài đã sửa. Nếu Prime phát hiện FAIL dù worker ghi PASS, Prime đổi P=`QA FAIL`, ghi lý do tại Q và đưa vào hàng đợi sửa. Lỗi kỹ thuật cũng phải được xử lý; không chốt STEP khi còn FAIL/BLOCKER.
10. Tiếp tục giao hết hàng PENDING_EMPTY theo thứ tự tăng dần, đợi mọi worker release rồi Prime **tổng hợp QA FAIL từ P/Q và sửa từng bài mới**, QA lại đến khi PASS. Không thực hiện STEP khác, không review `SKIPPED_EXISTING`, không commit/push. Báo `TOTAL_MENU_ITEMS`, `SKIPPED_EXISTING`, `NEW_FILL_DONE`, `QA_PASS`, `QA_FAIL`, `PENDING_EMPTY`, `ACTIVE`, `BLOCKER`, `FAILED`. Nếu chưa sửa hết thì giữ nguyên QA P/Q phục vụ xử lý tiếp, không xóa bằng chứng lỗi.
11. **Sau khi mọi bài trống được xử lý, mọi worker đã release và tất cả `NEW_FILL` đều QA PASS**, Prime **xóa sạch giá trị của toàn bộ vùng P20:Q đến hàng cuối cùng của tab** bằng Google Sheets connector (chỉ clear giá trị, không sửa format, notes, validation hoặc cột khác). **Không cần preflight, không kiểm tra từng ô, không phân biệt QA STEP 4 với dữ liệu khác, không lọc theo nội dung/ownership và không yêu cầu readback cleanup.** Nếu thao tác clear thật sự thất bại, báo lỗi công cụ. Sau khi clear thành công Prime mới chạy `generateModuleReadmeFromSheetByLocation` DRY_RUN; thiếu filename cha/Knowledge chưa review STEP 5 thì `DEFERRED_UNTIL_STEP_5`, `APPLY=NOT_RUN`. Không APPLY ở STEP 4.

### Mẫu tin nhắn Prime gửi cho **một** worker — chỉ tọa độ và file

Thay placeholder bằng **link, tọa độ thực tế và đúng đường dẫn file**. Tin nhắn giao việc **chỉ có bốn dòng dưới đây, không thêm tiêu đề, lời dẫn, chỉ thị, chú giải, quy tắc hoặc bất kỳ nội dung nào khác**:

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL>, <ITEM_CELL>, <SPECIAL_NOTE_TITLE_CELL>, <SPECIAL_NOTE_VALUE_CELL>
<C_ITEM_ROW:N_ITEM_ROW>
/java-learning/module-generate-agent/worker/STEP_4_WORKER.md
```

Hai placeholder Special Note phải là **địa chỉ ô thực tế đã xác minh trên đúng tab**, ví dụ `K2, K3`; không gửi nguyên văn, tóm tắt hay diễn giải nội dung ô. **Giữ nguyên đúng bốn dòng** tin giao việc.

## 3. Quy định dành cho worker nằm **RIÊNG** trong worker/STEP_4_WORKER.md

**Không sao chép tài liệu worker vào file Prime** và không yêu cầu worker đọc đoạn dòng trong `STEP_4.md` như cơ chế marker nội bộ đã loại bỏ ở các STEP cũ. Với STEP 4, **toàn bộ W1–W5 được tách thành file riêng** `/java-learning/module-generate-agent/worker/STEP_4_WORKER.md`:

- `W1`: Một hàng/một lần giao; chỉ sáu ô nguồn gồm bốn ô ngữ cảnh bài và hai ô Special Note, cùng vùng đích của bài.
- `W2`: Soạn **đầy đủ nội dung được giao và áp dụng Special Note nếu có, không tự giới hạn số ký tự khi viết**, một chuỗi README Markdown tự chứa và QA nội dung.
- `W3`: Chia tuần tự từng phần 30.000 ký tự, **phần cuối <30.000 ký tự giữ nguyên một ô**, ghi liên tiếp C→N; giới hạn sức chứa của vùng đích 360.000 ký tự.
- `W4`: Tin vào preflight **chỉ C:N** của Prime; worker không đọc vùng C:N/P/Q trước ghi. Ghi Knowledge C:N, exact readback, ghi QA `QA PASS`/`QA FAIL` tại P và lý do Q chỉ khi FAIL qua Google Sheets connector, rồi readback QA. Không cần kiểm tra P/Q trước ghi.
- `W5`: Báo cáo `QA_RESULT`, `QA_STATUS_CELL`, `QA_REASON_CELL` (chỉ FAIL), readback và lỗi thực tế; release ownership rồi SLEEPING.

Prime **phải kiểm tra trực tiếp worker đã tuân thủ W1–W5**, không chấp nhận việc worker chỉ báo `COMPLETED` trong khi nội dung ≤30.000 ký tự bị chia qua nhiều ô hoặc chỉ kiểm tra một phần vùng C:N.

## 4. Báo cáo từ sub-worker và tổng hợp của Prime

Mỗi worker trả về kết quả **chỉ cho một mục cấp 2**:

- `STATUS`: Worker trả `COMPLETED`, `QA_FAILED`, `BLOCKER` hoặc `FAILED`; mọi nhiệm vụ được giao là `MODE=NEW_FILL`. Prime tự phân loại `SKIPPED_EXISTING`, không giao Worker.
- `SHEET`: tên tab và `gid`.
- `SOURCE`: `A1`, `B1`, `<PARENT_CELL>`, `<ITEM_CELL>` đã đọc.
- `SPECIAL_NOTE`: hai tọa độ nhãn/giá trị, xác nhận worker tự đọc trực tiếp cả hai ô, trạng thái ghi chú trống/có nội dung và đã áp dụng hoặc blocker; không cần trích nguyên văn ghi chú.
- `OUTPUT`: các ô thực tế đã ghi (ví dụ `C21:E21`, không vượt `N21`), số ô và **tổng số ký tự** của Markdown gốc.
- `CHARS_PER_CELL`: ký tự thực trong từng ô đã ghi. Nếu `TOTAL_CHARS ≤ 30.000` thì `OUTPUT` **phải chỉ có một ô C**, và `D:N` vẫn trống.
- `PREFLIGHT`: Worker ghi `PRIME_PREFLIGHT=TRUSTED`, không tự kiểm tra lại C:N/P/Q/Protected Ranges/ownership; Prime chỉ giữ bằng chứng preflight vùng Knowledge C:N.
- `READBACK`: `PASS` hoặc `FAIL` — có/không khớp chính xác 100% sau khi ghép.
- `CONTENT_QA` và `QA_RESULT`: Worker chấm chỉ bài mới, ghi chính xác `QA PASS`/`QA FAIL` tại P, lý do cụ thể ở Q chỉ nếu FAIL, readback kết quả. Prime QA lại bài mới; nếu FAIL thì Prime sửa sau khi worker hoàn tất. Không QA lại bài `SKIPPED_EXISTING`.
- `PRESERVED`: Worker chỉ gửi yêu cầu ghi tới các ô C:N cần dùng, P và Q chỉ khi FAIL; Prime kiểm tra việc bảo toàn vùng ngoài phạm vi.
- `DETAILS`: blocker/lỗi thực tế của connector và reason QA ở Q nếu FAIL; không suy đoán ô bị Protected hoặc từ chối vì metadata không được cung cấp.

Prime quản lý hàng đợi theo `ITEM_CELL` trống đã preflight C:N, nhận kết quả, **đợi đủ worker rồi dựa trên P/Q xử lý mọi QA FAIL của bài mới**. Các hàng `SKIPPED_EXISTING` chỉ thống kê, không review. Sau mọi bài mới PASS, Prime **clear sạch P20:Q đến hàng cuối tab, không kiểm tra/filter trước khi xóa**. Không tuyên bố hoàn thành khi còn FAIL/BLOCKER hoặc thao tác clear lỗi.

## 5. Phạm vi và điều kiện hoàn thành

- Chỉ generate **Knowledge dạng README Markdown** cho các mục cấp 2 trong Menu, một worker/một mục mỗi lượt, với pool **theo Mode A/B của session** bao gồm cả `ACTIVE` và `SLEEPING`; A=3 mặc định, B=N do user chỉ định, không áp dụng mode của phiên khác.
- Prime xác định **đúng link, A1, B1, tọa độ Menu Cấp 1, tọa độ Menu Cấp 2, hai ô Special Note và ô bắt đầu C**, rồi giao đúng **file `worker/STEP_4_WORKER.md`**. Worker **tự đọc trực tiếp cả hai ô Special Note**, không tự dò nhiệm vụ, không đọc `STEP_4.md` và không dùng context cũ. Chỉ chia qua D:N khi bài thực sự vượt 30.000 ký tự.
- Không sửa Menu A/B, Special Note, Roadmap, header, tab Template, tài liệu source hoặc STEP khác; **Worker được ghi C:N, P và Q chỉ khi FAIL** trên hàng được giao; Prime chỉ được sửa Knowledge `NEW_FILL` của phiên này để khắc phục QA FAIL, rồi **xóa sạch toàn bộ giá trị P/Q từ hàng 20 trở xuống, không cần kiểm tra dữ liệu ở đó**. Không commit/push.
- Với bài C:N trống được giao, worker tạo đầy đủ README, ghi/QA/readback và báo P/Q. `QA FAIL` phải có reason tại Q để Prime sửa; `QA PASS` không ghi Q. Các hàng có dữ liệu cũ là `SKIPPED_EXISTING`, không giao. Nếu bài vượt 360.000 ký tự thì blocker, không cắt bài, không lấn cột.
- STEP 4 chỉ hoàn thành khi Prime đã kiểm kê toàn tab, không còn bài trống, mọi `NEW_FILL` sau sửa đều QA PASS, không còn worker/blocker và **đã clear sạch toàn bộ giá trị P/Q từ hàng 20 tới hàng cuối tab**. `SKIPPED_EXISTING` không phải bài đã review.
- Sau khi mọi bài `NEW_FILL` QA PASS và **clear P/Q thành công**, Prime chạy task đúng module `generateModuleReadmeFromSheetByLocation` ở `-PsheetMode=DRY_RUN`, đối chiếu I3/Gradle path. Nếu chưa có filename cha hoặc chưa qua STEP 5 thì `MODULE_SYNC=DEFERRED_UNTIL_STEP_5`, `APPLY=NOT_RUN`. Lỗi Gradle/config báo blocker. Chỉ Prime cập nhật `CURRENT_STEP.VALUE="4"` khi đủ gate và exact readback, không ghi lùi. STEP 5 mới review toàn bộ và APPLY; không dùng ByFiles/combined importer.
