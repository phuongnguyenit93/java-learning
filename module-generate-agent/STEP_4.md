# STEP 4 — Tạo Knowledge README cho từng mục Menu Cấp 2



## Mục tiêu

Tạo câu trả lời **Markdown README hoàn chỉnh** cho từng mục **Menu Cấp 2** trong Google Sheets. **Một nhiệm vụ của một sub-worker chỉ xử lý đúng một mục cấp 2 (một hàng)**. Prime điều phối theo **Mode A/B của session: A tối đa 3 worker, B tối đa N worker do user chỉ định (thiếu N → BLOCKER)**, tính cả ACTIVE + SLEEPING và tạo lần lượt từng worker theo `PRIME_RULES.md §0`. Các nhiệm vụ khác nhau có thể chạy song song khi không đụng cùng hàng.

STEP 4 **dùng trực tiếp nội dung Menu Cấp 2 làm đề bài**. Không tạo bước trung gian “Câu hỏi cho AI” và không ép đổi tiêu đề chủ đề thành một câu hỏi mới. Nếu mục cấp 2 đã là câu hỏi thì trả lời chính câu đó; nếu là tên chủ đề/yêu cầu thì trình bày bài học đúng nội dung đó.

**QUY TẮC BẮT BUỘC — STEP 4 CHỈ FILL-ONLY:** STEP 4 chỉ viết README cho Menu Cấp 2 mà **toàn bộ 12 ô C:N cùng hàng đều trống**. Khi **bất kỳ ô C:N đã có nội dung** (kể cả một fragment hoặc nội dung chưa hoàn chỉnh), Prime ghi `SKIPPED_EXISTING`, **không giao Worker, không đọc/review chất lượng, không sửa, không bổ sung và không ghi đè**. Các bài đã có được STEP 5 review/refactor. Worker chỉ được tạo nội dung cho hàng đã được Prime xác minh trống và phải tự kiểm tra lại đủ C:N trước khi ghi. Nếu dữ liệu xuất hiện trong lúc chạy, dừng ghi và trả `SKIPPED_EXISTING`/`BLOCKER`, không overwrite.

**Không giới hạn số ký tự khi sáng tạo/soạn nội dung.** README phải **thể hiện đầy đủ toàn bộ kiến thức, yêu cầu và phạm vi của mục được giao**, không lược bỏ nội dung cần thiết, tóm tắt cho vừa một ô, hoặc tự đặt giới hạn độ dài. Mốc **30.000 ký tự chỉ là giới hạn kỹ thuật của MỖI ô khi ghi**; 360.000 ký tự là sức chứa hiện tại của **12 ô C:N trên một hàng**, **không phải mục tiêu hay hạn mức để rút ngắn nội dung khi viết**. Nếu bài hoàn chỉnh vượt sức chứa, giữ nguyên bài và báo blocker, tuyệt đối không ghi thiếu.

### Phân tách tài liệu PRIME / WORKER (STEP 4)

- **File này `STEP_4.md` là tài liệu điều phối cho PRIME. Worker không đọc file này.**
- **Toàn bộ chỉ dẫn thực thi dành riêng cho worker nằm trong `module-generate-agent/worker/STEP_4_WORKER.md`.** Prime bắt buộc giao đúng đường dẫn đó; mỗi worker đọc **toàn văn file worker-only**, không đọc file STEP 4 của Prime, STEP khác, rules/MD/TXT khác. Đây là ngoại lệ đọc **duy nhất một file hướng dẫn worker** ngoài đúng sáu ô nguồn Sheets (bốn ô ngữ cảnh bài học và hai ô Special Note) và vùng đích được giao.
- Prime vẫn phải đọc đầy đủ `GENERAL_AGENT_RULES.md`, **liệt kê và đọc mọi file đang có trong `module-generate-agent/rules/`**, `module-generate-info.txt`, **toàn văn STEP 4 này và file worker/STEP_4_WORKER.md** để kiểm soát đúng contract. Đọc lại khi context bị rút gọn hoặc không còn chắc chắn.
- **Một lần giao = một bài = một lượt hoàn chỉnh**, không PREPARED / PHA A-B / chờ lượt chat thứ hai để được ghi. Prime cung cấp sẵn chính xác tab, sáu tọa độ nguồn, một hàng đích và file worker-only. Nội dung menu/README/Special Note không được copy từ Prime sang worker.
- **QUY TẮC GIAO VIỆC TUYỆT ĐỐI:** Nội dung tin nhắn Prime gửi worker **CHỈ gồm bốn dòng: link định vị tab; tọa độ bốn ô ngữ cảnh bài học cùng hai ô tiêu đề/giá trị Special Note; tọa độ vùng đích; đường dẫn file `worker/STEP_4_WORKER.md` cần đọc**. **KHÔNG viết thêm bất kỳ câu lệnh, nội dung hoặc diễn giải/tóm tắt Special Note, tóm tắt rules, yêu cầu QA, nhắc nhở, đáp án, ví dụ, nhãn worker, tiêu đề hay lời dẫn nào** trong tin nhắn giao việc. Worker tự đọc nguyên văn Special Note trên Sheet và file quy định để biết toàn bộ việc phải làm.

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
| Phần tiếp theo nếu dài | `D<ITEM_ROW>:N<ITEM_ROW>` | Chia liên tục trên đúng hàng của Menu Cấp 2, tối đa đến cột N. Cột O dành cho Difficulty, X:AA dành cho Video Script trên hàng Menu Cấp 1. |

- Ví dụ về **tọa độ**, không phải gợi ý nội dung: Menu Cấp 1 ở `A20`, Menu Cấp 2 ở `B21` thì câu trả lời bắt đầu tại `C21`; nếu dài, ghi lần lượt `D21`, `E21`, ... đến tối đa `M21`. **Không ghi vào cột O trở đi.**
- **Chia README hoàn chỉnh thành từng phần 30.000 ký tự theo thứ tự nguyên văn, rồi ghi lần lượt C→N trên CÙNG hàng.** Phần cuối nếu còn **dưới 30.000 ký tự thì giữ NGUYÊN thành đúng một phần/ô cuối**, không chia nhỏ hoặc phân phối lại cho các ô khác; nếu tổng bài ≤30.000 thì chỉ ghi C. Ví dụ 62.000 ký tự → C=30.000, D=30.000, E=2.000; 60.000 → C=30.000, D=30.000. Không tách đôi Unicode; mỗi ô tối đa 30.000 ký tự, vùng C:N có sức chứa 360.000. Nếu vượt sức chứa, báo `BLOCKER: CONTENT EXCEEDS C:N CAPACITY`, không cắt bài, không ghi một phần, không lấn sang Difficulty O/Video X:AA.
- **Chỉ xử lý hàng trống:** Nếu C:N có bất kỳ văn bản hoặc dữ liệu đã nhập nào, dù C trống nhưng D:N có nội dung, hàng đó là `SKIPPED_EXISTING`. Không đánh giá tính đúng/sai/đủ/thiếu, không sửa cú pháp hay dọn fragment cũ trong STEP 4. STEP 5 sẽ đọc và cải thiện nội dung đã tồn tại. Đối với hàng thật sự trống, soạn một bài hoàn chỉnh, ghi từ C sang N nếu cần và chỉ QA chính bài mới tạo.
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
   - **Phân loại bắt buộc trên toàn tab:** `PENDING_EMPTY` khi cả 12 ô C:N không có nội dung; `SKIPPED_EXISTING` khi ít nhất một ô C:N có nội dung, bất kể nội dung đúng/sai, một phần/toàn phần. Ô có formula/chip hoặc trạng thái ghi không xác định không được đưa vào hàng đợi an toàn: báo `BLOCKER: PROTECTED_OR_UNKNOWN_TARGET` và không ghi. Giữ thống kê `TOTAL_MENU_ITEMS`, `SKIPPED_EXISTING`, `PENDING_EMPTY`, `ACTIVE`, `NEW_FILL_DONE`, `BLOCKER`, `FAILED`. **Chỉ PENDING_EMPTY được vào hàng đợi, theo thứ tự hàng tăng dần; mỗi hàng được giao tối đa một Worker cùng lúc.** Khi mở session mới, kiểm kê lại từ trạng thái thực trên Sheet; **không tự quay về bài cũ để review, không cần replay lịch sử để xác định bài trống**.
5. **Kiểm tra trạng thái toàn bộ worker pool trước khi phân công/spawn** và tuân thủ `GENERAL_AGENT_RULES.md`:
   - Ưu tiên **tái sử dụng worker SLEEPING**; không spawn mới khi còn worker SLEEPING.
   - Không vượt quá **giới hạn pool Mode A/B của session (A=3 mặc định; B=N do user chỉ định)** gồm ACTIVE và SLEEPING. Mỗi lệnh spawn chỉ tạo một worker, chờ kết quả trước khi tạo tiếp. Nếu pool có sẵn vượt giới hạn áp dụng cho session, dừng việc tăng số worker và báo vấn đề cần điều phối lại; không tự kết thúc hoặc can thiệp vào worker của tác vụ khác.
   - Worker đang ACTIVE không được nhận thêm câu hỏi thứ hai. Có thể giao đồng thời tối đa **số hàng khác nhau bằng giới hạn worker cho session**; khi một worker hoàn thành và trở lại SLEEPING, mới giao một hàng kế tiếp.
6. **Prime gửi DUY NHẤT dữ liệu định vị cho từng worker:** link tab, tọa độ `A1`, `B1`, `A<PARENT_ROW>`, `B<ITEM_ROW>`, `<SPECIAL_NOTE_TITLE_CELL>`, `<SPECIAL_NOTE_VALUE_CELL>`, vùng đích `C<ITEM_ROW>:N<ITEM_ROW>` trên cùng hàng và đường dẫn file `/java-learning/module-generate-agent/worker/STEP_4_WORKER.md`. **Không kèm một chữ hướng dẫn hoặc nội dung ghi chú nào khác.** Worker tự đọc file này để lấy toàn bộ quy tắc và tự đọc Special Note nguyên văn từ Sheet; không đọc `STEP_4.md`, MD/TXT/rules khác hoặc chọn bài khác.
7. **Giữ worker độc lập về nội dung:** Prime **không gửi nguyên văn nội dung các ô, đặc biệt là Special Note**, tài liệu STEP trước, đáp án gợi ý, lịch sử hội thoại, cấu trúc bài học, tóm tắt quy định hay câu lệnh thực thi. **Không giải thích thêm kể cả khi định nhắc preflight, giới hạn 30.000, chia ô hoặc readback**; mọi yêu cầu đã nằm trong `worker/STEP_4_WORKER.md`.
8. **Quản lý kết quả theo hàng trống:** Trước giao Worker, Prime xác minh cả C:N hoàn toàn trống (đọc entered/effective/formatted value, formula, chipRuns, note, validation và ownership để phòng xung đột). **Không giao hàng SKIPPED_EXISTING cho Worker, không QA hoặc xếp lại lịch review bài cũ**. Worker tạo mới bằng `MODE=NEW_FILL`, kiểm tra lại 12 ô và source/ownership trước khi ghi, sau đó đọc lại. Nếu Worker phát hiện có bất kỳ nội dung từ trước hoặc concurrent edit, dừng và báo `SKIPPED_EXISTING`/`BLOCKER`; Prime đưa hàng ra khỏi hàng đợi, không ép Worker sửa.
9. Khi Worker hoàn tất một `NEW_FILL` và release ownership, Prime exact readback đủ C:N, nối không separator và so với bản Markdown vừa tạo; kiểm tra chất lượng, code/stdout, Special Note, giới hạn 30.000 ký tự/ô, source và metadata không đổi. **QA chỉ bài mới của phiên hiện tại; tuyệt đối không review lại bài SKIPPED_EXISTING.** `COMPLETED` chỉ khi readback và Prime QA PASS. Nếu Worker báo `SKIPPED_EXISTING`, Prime xác minh chỉ trạng thái đã có dữ liệu và không có write, không chấm chất lượng nội dung.
10. Không dừng sau đợt giao Worker đầu: tiếp tục lấy **PENDING_EMPTY** theo thứ tự hàng tăng dần đến khi không còn bài trống an toàn; tuyệt đối không quay lại hàng `SKIPPED_EXISTING` để review/refactor. **Không làm STEP khác**, không tự sửa source, không commit/push. Tổng hợp `TOTAL_MENU_ITEMS`, `SKIPPED_EXISTING`, `NEW_FILL_DONE`, `PENDING_EMPTY`, `ACTIVE`, `BLOCKER`, `FAILED` cùng tọa độ hàng còn thiếu. Nếu có nội dung cũ thiếu chất lượng, để STEP 5 xử lý.
11. **Sau khi mọi Menu Cấp 2 đều có dữ liệu C:N, tất cả các bài mới của STEP 4 QA PASS và mọi Worker release**, Prime kiểm tra module-local README **DRY_RUN** và báo kết quả/`DEFERRED_UNTIL_STEP_5` theo mục 5. **Không cần QA lại bài đã có; STEP 5 chịu trách nhiệm review/refactor toàn tab, Difficulty, filename và README APPLY.** Không APPLY ở STEP 4.

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
- `W4`: Preflight đủ 12 ô **trống trước khi tạo và ngay trước WRITE**; nếu đã có bất kỳ nội dung thì `SKIPPED_EXISTING` và **không sửa**. Chỉ ghi `NEW_FILL` vào hàng vẫn trống, exact readback toàn C:N rồi Prime QA.
- `W5`: Báo cáo duy nhất, giải phóng quyền sở hữu rồi SLEEPING.

Prime **phải kiểm tra trực tiếp worker đã tuân thủ W1–W5**, không chấp nhận việc worker chỉ báo `COMPLETED` trong khi nội dung ≤30.000 ký tự bị chia qua nhiều ô hoặc chỉ kiểm tra một phần vùng C:N.

## 4. Báo cáo từ sub-worker và tổng hợp của Prime

Mỗi worker trả về kết quả **chỉ cho một mục cấp 2**:

- `STATUS`: `COMPLETED`, `SKIPPED_EXISTING`, `BLOCKER` hoặc `FAILED`; `MODE=NEW_FILL` chỉ khi ghi bài mới, `MODE=SKIPPED_EXISTING` khi vùng đích có nội dung và không ghi.
- `SHEET`: tên tab và `gid`.
- `SOURCE`: `A1`, `B1`, `<PARENT_CELL>`, `<ITEM_CELL>` đã đọc.
- `SPECIAL_NOTE`: hai tọa độ nhãn/giá trị, xác nhận worker tự đọc trực tiếp cả hai ô, trạng thái ghi chú trống/có nội dung và đã áp dụng hoặc blocker; không cần trích nguyên văn ghi chú.
- `OUTPUT`: các ô thực tế đã ghi (ví dụ `C21:E21`, không vượt `N21`), số ô và **tổng số ký tự** của Markdown gốc.
- `CHARS_PER_CELL`: ký tự thực trong từng ô đã ghi. Nếu `TOTAL_CHARS ≤ 30.000` thì `OUTPUT` **phải chỉ có một ô C**, và `D:N` vẫn trống.
- `PREFLIGHT`: 12/12 ô C:N đã đọc và xác nhận **không có nội dung nào** trước khi tạo và trước ghi; sáu ô nguồn unchanged, không formula/chip/ownership conflict. Khi phát hiện đã có nội dung, ghi `SKIPPED_EXISTING` và `WRITE=NOT_RUN`, không chấm chất lượng.
- `READBACK`: `PASS` hoặc `FAIL` — có/không khớp chính xác 100% sau khi ghép.
- `CONTENT_QA`: Worker kiểm tra **chỉ bài mới** vừa sinh (kiến thức, Markdown, code, Special Note); `PRIME_QA` chỉ nghiệm thu bài mới đó sau readback. Không review, so diff chất lượng hay yêu cầu cải thiện các bài đã có.
- `PRESERVED`: xác nhận các ô nguồn và dữ liệu ngoài hàng/vùng được giao không bị thay đổi.
- `DETAILS`: blocker/lỗi cụ thể, phạm vi ô bị chiếm hoặc lý do không thể hoàn thành.

Prime quản lý hàng đợi theo từng `ITEM_CELL` **chỉ cho hàng C:N trống**, nhận kết quả và nghiệm thu `NEW_FILL` bằng readback + Prime QA. Hàng `SKIPPED_EXISTING` chỉ thống kê có dữ liệu, **không đánh giá hay sửa**. `BLOCKER`/`FAILED` phải báo rõ; không coi STEP 4 hoàn tất khi vẫn còn hàng thật sự trống chưa được xử lý.

## 5. Phạm vi và điều kiện hoàn thành

- Chỉ generate **Knowledge dạng README Markdown** cho các mục cấp 2 trong Menu, một worker/một mục mỗi lượt, với pool **theo Mode A/B của session** bao gồm cả `ACTIVE` và `SLEEPING`; A=3 mặc định, B=N do user chỉ định, không áp dụng mode của phiên khác.
- Prime xác định **đúng link, A1, B1, tọa độ Menu Cấp 1, tọa độ Menu Cấp 2, hai ô Special Note và ô bắt đầu C**, rồi giao đúng **file `worker/STEP_4_WORKER.md`**. Worker **tự đọc trực tiếp cả hai ô Special Note**, không tự dò nhiệm vụ, không đọc `STEP_4.md` và không dùng context cũ. Chỉ chia qua D:N khi bài thực sự vượt 30.000 ký tự.
- Không sửa Menu A/B, hai ô Special Note, Roadmap, header, tab Template, tài liệu source hoặc STEP khác; không tự commit/push.
- Với **bài C:N trống**, hoàn thành khi README mới đúng đề, đầy đủ, chia C:N đúng chuẩn và **readback 100% + Prime QA PASS**. Với **bài C:N đã có bất kỳ dữ liệu**, ghi `SKIPPED_EXISTING`, không đánh giá chất lượng và không ghi; STEP 5 chịu trách nhiệm review/refactor. Nếu bài mới vượt 360.000 ký tự thì báo blocker, không cắt hoặc lấn cột.
- Toàn bộ STEP 4 **hoàn thành** chỉ khi đã kiểm kê mọi Menu Cấp 2, **không còn hàng C:N trống**, các hàng `NEW_FILL` đều QA PASS và không còn blocker/worker pending. `SKIPPED_EXISTING` là bằng chứng có dữ liệu, **không phải đã review chất lượng**. Đảm bảo không bỏ sót bài trống phía dưới.
- **Sau khi kiểm kê toàn tab xác nhận không còn bài trống và mọi `NEW_FILL` đã QA PASS**, mọi Worker release, Prime chạy task đúng module `generateModuleReadmeFromSheetByLocation` ở `-PsheetMode=DRY_RUN`, đối chiếu I3/Gradle path theo General Rules. B cha còn thiếu filename do STEP 5 mới đảm nhiệm hoặc Knowledge chưa review STEP 5 thì báo `MODULE_SYNC=DEFERRED_UNTIL_STEP_5`, `APPLY=NOT_RUN`; **không yêu cầu STEP 4 review bài cũ trước khi nâng CURRENT_STEP.VALUE**. Nếu Gradle/config lỗi, báo blocker. Prime chỉ cập nhật `CURRENT_STEP.VALUE = "4" khi đủ gate, CURRENT_STEP/CURRENT_STEP.VALUE preflight và readback theo PRIME_RULES; không ghi lùi CURRENT_STEP.VALUE của module đã qua STEP 4. STEP 5 review toàn bộ nội dung và mới APPLY README; không dùng ByFiles/combined importer.
