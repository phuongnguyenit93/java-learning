# STEP 4 — Tạo Knowledge README cho từng mục Menu Cấp 2

## Mục tiêu

Tạo câu trả lời **Markdown README hoàn chỉnh** cho từng mục **Menu Cấp 2** trong Google Sheets. **Một nhiệm vụ của một sub-worker chỉ xử lý đúng một mục cấp 2 (một hàng)**. Prime điều phối theo **Mode A/B/C gắn vào prompt của session: tối đa 3/6/8 worker ACTIVE + SLEEPING** (mặc định Mode A=3), theo `PRIME_RULES.md §0`. Các nhiệm vụ khác nhau có thể chạy song song khi không đụng cùng hàng.

STEP 4 **dùng trực tiếp nội dung Menu Cấp 2 làm đề bài**. Không tạo bước trung gian “Câu hỏi cho AI” và không ép đổi tiêu đề chủ đề thành một câu hỏi mới. Nếu mục cấp 2 đã là câu hỏi thì trả lời chính câu đó; nếu là tên chủ đề/yêu cầu thì trình bày bài học đúng nội dung đó.

**Không giới hạn số ký tự khi sáng tạo/soạn nội dung.** README phải **thể hiện đầy đủ toàn bộ kiến thức, yêu cầu và phạm vi của mục được giao**, không lược bỏ nội dung cần thiết, tóm tắt cho vừa một ô, hoặc tự đặt giới hạn độ dài. Mốc **30.000 ký tự chỉ là giới hạn kỹ thuật của MỖI ô khi ghi**; 360.000 ký tự là sức chứa hiện tại của **12 ô C:N trên một hàng**, **không phải mục tiêu hay hạn mức để rút ngắn nội dung khi viết**. Nếu bài hoàn chỉnh vượt sức chứa, giữ nguyên bài và báo blocker, tuyệt đối không ghi thiếu.

### Phân tách tài liệu PRIME / WORKER (STEP 4)

- **File này `STEP_4.md` là tài liệu điều phối cho PRIME. Worker không đọc file này.**
- **Toàn bộ chỉ dẫn thực thi dành riêng cho worker nằm trong `module-generate-agent/temp/worker/STEP_4_WORKER.md`.** Prime bắt buộc giao đúng đường dẫn đó; mỗi worker đọc **toàn văn file worker-only**, không đọc file STEP 4 của Prime, STEP khác, rules/MD/TXT khác. Đây là ngoại lệ đọc **duy nhất một file hướng dẫn worker** ngoài đúng sáu ô nguồn Sheets (bốn ô ngữ cảnh bài học và hai ô Special Note) và vùng đích được giao.
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
- **Các câu trả lời đã ghi trước khi cập nhật phạm vi được giữ nguyên; không audit, chia lại hoặc ghi đè chỉ vì thay đổi quy định.**
- Các hàng chứa **Menu Cấp 1** (`A` có tiêu đề; `B` cùng hàng ban đầu trống và được worker **STEP 5** điền filename .md về sau) **không phải nhiệm vụ STEP 4**; không tạo câu trả lời ở `C` của những hàng này.
- Giữ nguyên header Menu tại `A18:B19` và header “Câu trả lời” tại `C19:N19`. Các ô `O19:S19` không thuộc vùng câu trả lời. **Không sửa A/B, hai ô Special Note**, Roadmap, Video hay tab khác.
- Không tự suy ra hàng cha bằng cách quét lịch sử trong worker: **Prime trực tiếp xác định và cung cấp cả `A<PARENT_ROW>` lẫn `B<ITEM_ROW>`**. Hàng cha có thể không liền kề hàng con.

## 2. Quy định dành cho Prime — xác định sheet và phân công

1. **Đọc đủ bộ tài liệu ở cổng context bên trên** trước khi điều phối và tuân thủ quy tắc worker pool. STEP 4 dùng **Mode A/B/C của session (3/6/8 worker ACTIVE + SLEEPING, mặc định A)**. Không mang mode sang session khác. **Nếu người dùng đã loại trừ worker cụ thể hoặc yêu cầu dừng, không sử dụng worker đó và không giao tiếp việc cho đến khi có yêu cầu tiếp tục.** Một worker chỉ nhận **một câu hỏi/mục cấp 2 tại một thời điểm**.
2. **Prime đọc `module-generate-agent/temp/module-generate-info.txt`** để lấy URL gốc ở nhãn `Excel Page URL`, cùng mapping `<TÊN MODULE> sheet id : <SHEET_ID>`. Lập link trực tiếp đến tab bằng `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>` (chuẩn hóa URL trước khi ghép). `SHEET_ID` chính là **gid của tab**, không phải spreadsheet/file ID.
3. **Xác minh đúng module trước khi giao:** Đối chiếu tab/gid, ô `A1` và `B1`. Nếu thiếu mapping, tên tab hoặc `B1` không khớp, báo `BLOCKER`, không đoán tab.
   - **Xác định chính xác hai tọa độ Special Note trên chính tab:** tìm ô tiêu đề có đúng nhãn `Special Note` trong khu vực `Module Project`; ô giá trị phải nằm **ngay bên dưới, cùng cột**. Xác minh nhãn duy nhất, đúng cấu trúc, cả hai ô tồn tại và đọc được. `K2/K3` chỉ là ví dụ hiện tại, **không tự mặc định tọa độ**. Nếu nhãn thiếu/trùng/mơ hồ hoặc ô giá trị không đọc được: `BLOCKER: SPECIAL NOTE LOCATION`, không giao bài. Ô giá trị trống vẫn hợp lệ và **phải gửi hai tọa độ**. Prime chỉ gửi địa chỉ ô, **không chuyển nội dung, diễn giải hay tóm tắt Special Note** cho worker.
4. **Prime tự duyệt các ô A/B của vùng Menu từ hàng 20** để lập danh sách nhiệm vụ:
   - Gặp hàng có `A` là Menu Cấp 1: ghi nhận tọa độ ô cha **bất kể B cùng hàng trống hay đã có filename .md hợp lệ do STEP 5 đặt**. Không coi B hàng cha là một bài cấp 2.
   - Gặp hàng có `B` là Menu Cấp 2 và `A` trống: ghép với ô Menu Cấp 1 gần nhất ở phía trên, ghi nhận **`PARENT_CELL` và `ITEM_CELL`**, cùng `OUTPUT_START = C` của hàng mục con.
   - Nếu không xác định được cha, hàng có A/B cùng chứa dữ liệu **không phải một filename .md hợp lệ ở B cha**, hoặc menu mơ hồ: **báo blocker** cho hàng đó, không giao theo suy đoán.
   - Chỉ đưa hàng mục cấp 2 vào hàng đợi; **mỗi hàng được giao một lần**, không giao trùng cho nhiều worker.
5. **Kiểm tra trạng thái toàn bộ worker pool trước khi phân công/spawn** và tuân thủ `GENERAL_AGENT_RULES.md`:
   - Ưu tiên **tái sử dụng worker SLEEPING**; không spawn mới khi còn worker SLEEPING.
   - Không vượt quá **giới hạn pool Mode A/B/C của session (mặc định A=3)** gồm ACTIVE và SLEEPING. Nếu pool có sẵn vượt giới hạn áp dụng cho session, dừng việc tăng số worker và báo vấn đề cần điều phối lại; không tự kết thúc hoặc can thiệp vào worker của tác vụ khác.
   - Worker đang ACTIVE không được nhận thêm câu hỏi thứ hai. Có thể giao đồng thời tối đa **số hàng khác nhau bằng giới hạn worker cho session**; khi một worker hoàn thành và trở lại SLEEPING, mới giao một hàng kế tiếp.
6. **Prime gửi DUY NHẤT dữ liệu định vị cho từng worker:** link tab, tọa độ `A1`, `B1`, `A<PARENT_ROW>`, `B<ITEM_ROW>`, `<SPECIAL_NOTE_TITLE_CELL>`, `<SPECIAL_NOTE_VALUE_CELL>`, vùng đích `C<ITEM_ROW>:N<ITEM_ROW>` trên cùng hàng và đường dẫn file `/java-learning/module-generate-agent/temp/worker/STEP_4_WORKER.md`. **Không kèm một chữ hướng dẫn hoặc nội dung ghi chú nào khác.** Worker tự đọc file này để lấy toàn bộ quy tắc và tự đọc Special Note nguyên văn từ Sheet; không đọc `STEP_4.md`, MD/TXT/rules khác hoặc chọn bài khác.
7. **Giữ worker độc lập về nội dung:** Prime **không gửi nguyên văn nội dung các ô, đặc biệt là Special Note**, tài liệu STEP trước, đáp án gợi ý, lịch sử hội thoại, cấu trúc bài học, tóm tắt quy định hay câu lệnh thực thi. **Không giải thích thêm kể cả khi định nhắc preflight, giới hạn 30.000, chia ô hoặc readback**; mọi yêu cầu đã nằm trong `worker/STEP_4_WORKER.md`.
8. **Kiểm tra và quản lý kết quả theo hàng**: trước giao worker phải kiểm tra **đủ 12 ô C:N** (kể cả khi chỉ cần C): `userEnteredValue`/formula, `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns`. Phân biệt hàng có nội dung cũ với hàng trống; không overwrite, không xóa/chia lại dữ liệu cũ, không giao trùng hàng. Ghi nhận `ITEM_CELL → worker → status → actual output cells → readback → QA`.
9. Khi worker hoàn tất và release ownership, Prime đọc lại **cả C:N** của hàng: với README ≤30.000 ký tự phải chỉ có **một ô C**; nếu dài hơn thì các ô C→N liên tiếp, không có ô trống xen giữa, **mỗi ô trước ô cuối chứa đúng 30.000 ký tự (trừ trường hợp ranh giới Unicode cần xử lý an toàn)**; **phần dư cuối không được chia tiếp**. Prime tự QA Markdown, **tính đầy đủ theo đề bài và Special Note trực tiếp từ Sheet (khi có nội dung)**, nội dung đúng đề và phạm vi ghi; xác minh sáu ô nguồn được giữ nguyên, không chỉ tin trạng thái từ worker. **Một bài chỉ COMPLETED khi preflight hợp lệ, worker READBACK PASS và Prime QA PASS.** Những hàng có dữ liệu tồn tại được nghiệm thu theo trạng thái thật, không ghi đè.
10. Không dừng sau đợt giao worker đầu: tiếp tục phân công hàng trống theo quy tắc pool cho đến hết phạm vi, giải quyết mọi blocker. **Không làm STEP khác**, không tự sửa file source, không commit/push. Khi xử lý xong hàng đợi, Prime báo số hàng `COMPLETED`, `BLOCKER`, `FAILED` và các hàng còn lại.
11. **Sau khi TẤT CẢ bài Knowledge trong tab QA PASS và mọi worker release**, Prime bắt buộc kiểm tra module-local README **DRY_RUN** và báo kết quả/`DEFERRED_UNTIL_STEP_5` theo checkpoint ở mục 5. **Tuyệt đối không APPLY trước STEP 5** khi filename/Knowledge chưa hoàn chỉnh; STEP 5 chịu trách nhiệm refactor, Difficulty, filename và import README thực.

### Mẫu tin nhắn Prime gửi cho **một** worker — chỉ tọa độ và file

Thay placeholder bằng **link, tọa độ thực tế và đúng đường dẫn file**. Tin nhắn giao việc **chỉ có bốn dòng dưới đây, không thêm tiêu đề, lời dẫn, chỉ thị, chú giải, quy tắc hoặc bất kỳ nội dung nào khác**:

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL>, <ITEM_CELL>, <SPECIAL_NOTE_TITLE_CELL>, <SPECIAL_NOTE_VALUE_CELL>
<C_ITEM_ROW:N_ITEM_ROW>
/java-learning/module-generate-agent/temp/worker/STEP_4_WORKER.md
```

Hai placeholder Special Note phải là **địa chỉ ô thực tế đã xác minh trên đúng tab**, ví dụ `K2, K3`; không gửi nguyên văn, tóm tắt hay diễn giải nội dung ô. **Giữ nguyên đúng bốn dòng** tin giao việc.

## 3. Quy định dành cho worker nằm **RIÊNG** trong worker/STEP_4_WORKER.md

**Không sao chép tài liệu worker vào file Prime** và không yêu cầu worker đọc đoạn dòng trong `STEP_4.md` như cơ chế marker nội bộ đã loại bỏ ở các STEP cũ. Với STEP 4, **toàn bộ W1–W5 được tách thành file riêng** `/java-learning/module-generate-agent/temp/worker/STEP_4_WORKER.md`:

- `W1`: Một hàng/một lần giao; chỉ sáu ô nguồn gồm bốn ô ngữ cảnh bài và hai ô Special Note, cùng vùng đích của bài.
- `W2`: Soạn **đầy đủ nội dung được giao và áp dụng Special Note nếu có, không tự giới hạn số ký tự khi viết**, một chuỗi README Markdown tự chứa và QA nội dung.
- `W3`: Chia tuần tự từng phần 30.000 ký tự, **phần cuối <30.000 ký tự giữ nguyên một ô**, ghi liên tiếp C→N; giới hạn sức chứa của vùng đích 360.000 ký tự.
- `W4`: Preflight đủ 12 ô, ghi một lần đúng `userEnteredValue`, readback từng ô và chuỗi ghép exact.
- `W5`: Báo cáo duy nhất, giải phóng quyền sở hữu rồi SLEEPING.

Prime **phải kiểm tra trực tiếp worker đã tuân thủ W1–W5**, không chấp nhận việc worker chỉ báo `COMPLETED` trong khi nội dung ≤30.000 ký tự bị chia qua nhiều ô hoặc chỉ kiểm tra một phần vùng C:N.

## 4. Báo cáo từ sub-worker và tổng hợp của Prime

Mỗi worker trả về kết quả **chỉ cho một mục cấp 2**:

- `STATUS`: `COMPLETED`, `BLOCKER` hoặc `FAILED`.
- `SHEET`: tên tab và `gid`.
- `SOURCE`: `A1`, `B1`, `<PARENT_CELL>`, `<ITEM_CELL>` đã đọc.
- `SPECIAL_NOTE`: hai tọa độ nhãn/giá trị, xác nhận worker tự đọc trực tiếp cả hai ô, trạng thái ghi chú trống/có nội dung và đã áp dụng hoặc blocker; không cần trích nguyên văn ghi chú.
- `OUTPUT`: các ô thực tế đã ghi (ví dụ `C21:E21`, không vượt `N21`), số ô và **tổng số ký tự** của Markdown gốc.
- `CHARS_PER_CELL`: ký tự thực trong từng ô đã ghi. Nếu `TOTAL_CHARS ≤ 30.000` thì `OUTPUT` **phải chỉ có một ô C**, và `D:N` vẫn trống.
- `PREFLIGHT`: 12/12 ô C:N trống hoàn toàn tại thời điểm ngay trước ghi, **cả sáu ô nguồn unchanged**.
- `READBACK`: `PASS` hoặc `FAIL` — có/không khớp chính xác 100% sau khi ghép.
- `CONTENT_QA`: worker kiểm tra Markdown và code; `PRIME_QA`: Prime tự xác minh lại nội dung, số ô, độ dài, phạm vi và nguồn.
- `PRESERVED`: xác nhận các ô nguồn và dữ liệu ngoài hàng/vùng được giao không bị thay đổi.
- `DETAILS`: blocker/lỗi cụ thể, phạm vi ô bị chiếm hoặc lý do không thể hoàn thành.

Prime quản lý hàng đợi theo từng `ITEM_CELL`, nhận báo cáo, đối chiếu nội dung thực trên Sheet; chỉ đánh dấu `COMPLETED` khi `READBACK PASS` **và `PRIME_QA PASS`**. Những mục `BLOCKER`/`FAILED` được ghi rõ để xử lý sau; không đánh dấu hoàn thành toàn bộ STEP 4 khi vẫn còn hàng chưa được giải quyết.

## 5. Phạm vi và điều kiện hoàn thành

- Chỉ generate **Knowledge dạng README Markdown** cho các mục cấp 2 trong Menu, một worker/một mục mỗi lượt, với pool **theo Mode A/B/C của session** bao gồm cả `ACTIVE` và `SLEEPING`; mặc định A=3, không áp dụng mode của phiên khác.
- Prime xác định **đúng link, A1, B1, tọa độ Menu Cấp 1, tọa độ Menu Cấp 2, hai ô Special Note và ô bắt đầu C**, rồi giao đúng **file `worker/STEP_4_WORKER.md`**. Worker **tự đọc trực tiếp cả hai ô Special Note**, không tự dò nhiệm vụ, không đọc `STEP_4.md` và không dùng context cũ. Chỉ chia qua D:N khi bài thực sự vượt 30.000 ký tự.
- Không sửa Menu A/B, hai ô Special Note, Roadmap, header, tab Template, tài liệu source hoặc STEP khác; không tự commit/push.
- Một mục **hoàn thành** khi README đúng đề, được ghi nguyên văn **chỉ ô C nếu ≤30.000 ký tự**, hoặc C→N liên tiếp nếu dài hơn; **readback 100% và Prime QA đều PASS**. Nếu vượt 360.000 ký tự hoặc vùng C:N không đủ chỗ thì báo blocker, không ghi thiếu và không lấn sang Difficulty O/Video X:AA.
- Toàn bộ STEP 4 **hoàn thành** chỉ khi **tất cả mục Menu Cấp 2 trong phạm vi tab** đã được xác minh hợp lệ; mọi blocker phải được báo chính xác, không che giấu, không coi chỉ những mục đã giao là toàn bộ STEP.
- **Sau khi toàn bộ tab đạt QA PASS**, mọi worker giải phóng ownership, Prime chạy task **đúng module** `generateModuleReadmeFromSheetByLocation` với `-PsheetMode=DRY_RUN -PfileUrl=<URL> -PsheetID=<GID>`, xác minh I3/Gradle path theo `GENERAL_AGENT_RULES.md §6`. Nếu B cha còn thiếu filename do STEP 5 mới đảm nhiệm, hoặc Knowledge chưa qua review STEP 5, ghi **`MODULE_SYNC=DEFERRED_UNTIL_STEP_5`, `APPLY=NOT_RUN`**, tuyệt đối không báo đã đồng bộ hoặc APPLY snapshot README chưa hoàn chỉnh. Gradle/config lỗi là BLOCKER, không che bằng DEFERRED. Chỉ khi toàn tab QA PASS và trạng thái DEFERRED đã được báo trung thực, Prime mới kiểm tra `J2 = Current STEP`, đọc `J3`, cập nhật an toàn `J3 = STEP 4` và readback exact. Khi còn bài trống, worker đang chạy hoặc blocker, không cập nhật J3. **STEP 5 sẽ APPLY README** sau filename/review; không dùng ByFiles hoặc task tổng hợp.
