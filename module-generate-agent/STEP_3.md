# STEP 3 — PRIME ONLY: Điều phối Menu hai cấp



## Mục tiêu và phân tách PRIME / WORKER

Giao cho **đúng một sub-worker** tự đọc **nguyên văn câu hỏi tại `C3`** của tab module, tạo **toàn bộ Menu hai cấp** đúng đề và ghi từ hàng 20 trong **`A20:B...`**. **Một worker chịu trách nhiệm cả Menu Cấp 1 và Menu Cấp 2**, không chia đôi hoặc giao các nhóm cho worker khác.

- **File `STEP_3.md` này dành riêng cho Prime. Worker KHÔNG đọc file này.**
- **Toàn bộ quy tắc thực thi của worker nằm trong `/java-learning/module-generate-agent/worker/STEP_3_WORKER.md`.** Worker chỉ đọc toàn văn **một file worker-only này**, không đọc file STEP 3 Prime, STEP khác, General Rules, rules folder, TXT hay lịch sử bài cũ.
- Prime trước khi điều phối **đọc đầy đủ** `module-generate-agent/GENERAL_AGENT_RULES.md`, **liệt kê và đọc mọi file hiện có trong `module-generate-agent/rules/`**, `module-generate-info.txt`, `STEP_3.md` và **`worker/STEP_3_WORKER.md`**. Nếu context bị rút gọn hoặc không chắc chắn, đọc lại.
- **Một lần giao = toàn bộ Menu một tab = một lượt hoàn chỉnh**: worker tự đọc, soạn, QA, preflight, ghi, readback, báo cáo rồi SLEEPING; không chia PHA A/B hoặc chờ Prime cấp hướng dẫn lần hai.
- **Tin nhắn Prime gửi worker CHỈ có bốn dòng:** link tab; tọa độ `C3`, header `A18:B19` **và hai tọa độ ô tiêu đề/ô giá trị `Special Note`**; vùng ghi Menu `A20:B<END>`; đường dẫn `worker/STEP_3_WORKER.md`. **Tuyệt đối không chép, tóm tắt, diễn giải hay chuyển tiếp nội dung Special Note cho worker.** Không kèm nội dung C3, mô tả Menu mong muốn, gợi ý, ví dụ, lời dẫn hay chữ thừa. Worker tự đọc nguyên văn hai ô Special Note trực tiếp từ Sheet.

## 1. Bố cục và quyền ghi

| Ô/vùng | Vai trò | Quyền |
| --- | --- | --- |
| `C3` | Câu hỏi Menu gốc, bao gồm mọi lưu ý/ràng buộc | Chỉ đọc |
| Ô nhãn `Special Note` và ô giá trị bên dưới cùng cột (hiện tại `K2`/`K3`) | Ghi chú riêng cho module, có thể trống | Worker **phải đọc cả hai ô trực tiếp**, không ghi |
| `A18` | Nhãn khu vực Menu | Giữ nguyên |
| `A19:B19` | Header `Menu Cấp 1` và `Menu Cấp 2` | Giữ nguyên |
| `A20:A<END>` | **Menu Cấp 1** — mỗi chủ đề lớn trên một hàng **riêng**, B cùng hàng trống | Chỉ ghi ô trống |
| `B20:B<END>` | **Menu Cấp 2** — các mục con nằm **dưới** hàng cha ở cột B, A cùng hàng trống | Chỉ ghi ô trống |

Menu do STEP 3 tạo **bắt đầu đúng hàng 20** (không ghi `A19:B19`). Mỗi nhóm lớn ở cột A; mục con đầu tiên ở cột B **ngay hàng kế tiếp**, các mục con tiếp theo liên tiếp; nhóm lớn sau bắt đầu ngay sau mục con cuối, không xen hàng trắng và không viết cả A/B trên một hàng. Không tạo cấp 3.

`<END>` là **hàng cuối thực tế của lưới tab**, do Prime xác minh từ metadata, không tự suy đoán hoặc gửi ký hiệu `...`. Khu vực Menu chỉ `A:B`; **cột C:N là Knowledge trên hàng Menu Cấp 2**, `O` là Difficulty trên hàng Menu Cấp 2, `X:AA` là Video Script trên hàng Menu Cấp 1, Roadmap `F:H` phía trên, `CURRENT_STEP.VALUE` là Current STEP và các ô `Special Note` (chỉ đọc); **worker không được sửa những vùng này**.

## 2. Prime — xác minh nguồn và điều phối

1. Tuân thủ context/rules ở đầu file. STEP 3 **chỉ có một worker cho toàn bộ Menu trên mỗi tab**. Kiểm tra pool `ACTIVE + SLEEPING` theo **Mode A/B của session** (`PRIME_RULES.md §0`; A=3 mặc định, B=N do user cung cấp, thiếu N → BLOCKER); **tái sử dụng worker SLEEPING trước**, tuyệt đối không spawn nếu còn SLEEPING; nếu phải spawn thì tạo lần lượt mỗi lệnh một worker. Không giao nhiệm vụ trùng lặp, không giao worker ACTIVE, tôn trọng lệnh dừng/loại trừ.
2. Đọc `module-generate-agent/module-generate-info.txt`; lấy `Excel Page URL` và dòng mapping `<TÊN MODULE> sheet id : <SHEET_ID>`. `SHEET_ID` là **gid của tab**, không phải spreadsheet ID. Lập link `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>`, xác minh tên tab, gid và `B1` đúng module; mapping thiếu/trùng/sai thì **BLOCKER**.
3. **Tìm ô tiêu đề có đúng nhãn `Special Note` trong khu vực `Module Project` của chính tab**, xác định **ô giá trị ngay bên dưới cùng cột** (hiện tại ví dụ `K2` và `K3`). Xác minh nhãn duy nhất, cả hai tọa độ hợp lệ và có thể đọc; **giá trị trống vẫn gửi tọa độ để worker tự đọc**. Thiếu nhãn, nhiều nhãn gây mơ hồ hoặc không truy cập được ô: `BLOCKER: SPECIAL NOTE LOCATION`, không tự giả định luôn là K2/K3. Prime **chỉ gửi tọa độ, không truyền hay tự tóm tắt/diễn giải nội dung Special Note**, dù ghi chú dài hoặc nhiều dòng.
4. Kiểm tra trên đúng tab `A18:B19` là vùng header Menu hợp lệ, **xác định `<END>` từ rowCount thật** và đọc hiện trạng `A20:B<END>` đủ rộng để nhận biết **mọi Menu có sẵn**, cả dữ liệu nằm sâu dưới hàng 20, kể cả formula/notes/validation/chips có khả năng xung đột. Không suy ra rằng toàn vùng trống chỉ vì vài chục hàng đầu trống.
5. Nếu có Menu đã tồn tại, ghi nhận là dữ liệu phải bảo toàn: không tạo phiên bản thứ hai hoặc làm lại đè lên. Nếu nguồn/header sai, vùng không đủ chỗ, có dữ liệu không thể tránh, hoặc không xác minh được tab: `BLOCKER`, không giao ghi.
6. Gửi một worker **duy nhất bốn dòng tọa độ và đường dẫn** theo mẫu dưới. Không gửi `C3`, **nội dung/diễn giải/tóm tắt Special Note**, Menu do Prime viết, context/hướng dẫn nội dung, lịch sử trò chuyện hay tóm tắt rules. Worker tự đọc `C3`, **hai ô Special Note**, `A18:B19`, `A20:B<END>` trực tiếp, chỉ đọc hướng dẫn `worker/STEP_3_WORKER.md`.
7. Khi worker báo xong, Prime **đọc lại toàn bộ vùng Menu**, đối chiếu số lượng và thứ tự nhóm/nhánh với yêu cầu `C3` và Special Note (nếu có), cấu trúc hàng A/B, header và dữ liệu ngoài vùng được bảo toàn. **Không chỉ tin báo cáo worker**; `COMPLETED` cần `READBACK PASS` và `PRIME_QA PASS`. Nếu blocker/failed, xem trạng thái thực và điều phối phần còn thiếu an toàn, không retry mù.
8. Chỉ sau khi **toàn bộ Menu của tab thực sự hoàn thành**, mọi worker đã báo cáo/release và Prime QA PASS, Prime chạy **đúng module** `generateModuleReadmeFromSheetByLocation` **DRY_RUN** với `-PfileUrl=<URL>` và `-PsheetID=<GID>` theo `GENERAL_AGENT_RULES.md §6`. STEP 3 để B cha trống và Knowledge chưa có, nên trạng thái `SKIPPED_MISSING_FILENAME`/chưa đủ Knowledge phải báo **`MODULE_SYNC=DEFERRED_UNTIL_STEP_5`, `APPLY=NOT_RUN`**, không coi đã đồng bộ. Không APPLY README chưa đầy đủ. Nếu config/task lỗi thì báo BLOCKER. Khi phần Sheet QA PASS và trạng thái DEFERRED được ghi rõ, Prime mới kiểm tra `CURRENT_STEP = Current STEP` và `CURRENT_STEP.VALUE`, cập nhật an toàn **`CURRENT_STEP.VALUE = "3"** và đọc lại exact. Nếu còn worker/blocker, không ghi CURRENT_STEP.VALUE. Worker không chạy task. Hai ô Special Note được giữ nguyên.

### Tin nhắn Prime gửi một worker — CHỈ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
C3, A18:B19, <SPECIAL_NOTE_TITLE_CELL>, <SPECIAL_NOTE_VALUE_CELL>
A20:B<END_ROW_ĐÃ_XÁC_MINH>
/java-learning/module-generate-agent/worker/STEP_3_WORKER.md
```

`<END_ROW_ĐÃ_XÁC_MINH>` phải được thay bằng số hàng thực của tab (ví dụ `1000` nếu metadata cho biết lưới có 1000 hàng). Hai tọa độ Special Note phải được thay bằng **địa chỉ ô đã xác minh** (ví dụ `K2, K3`), không gửi `...`, placeholder hay **nội dung ô**. Mọi chi tiết thực thi ở file worker-only.

## 3. Báo cáo và nghiệm thu

Prime kiểm tra báo cáo worker gồm:

- `STATUS=COMPLETED/BLOCKER/FAILED`, `SHEET/gid`, `SOURCE=C3`.
- `OUTPUT_RANGE` thực ghi, số Menu Cấp 1 và Cấp 2, hàng bắt đầu/kết thúc, hoặc `NO WRITE NEEDED/BLOCKER` nếu vùng đã có Menu.
- `PREFLIGHT`: `A18:B19` hợp lệ, các ô cần ghi trống thực sự, không có dữ liệu cũ bị ghi đè.
- `SPECIAL_NOTE`: worker xác nhận **đọc nguyên văn ô tiêu đề và ô giá trị** tại địa chỉ Prime gửi; báo có nội dung/trống và đã áp dụng nếu có, **không cần chuyển nội dung ghi chú vào báo cáo**.
- `STRUCTURE_QA`: mỗi A là hàng cha riêng (B trống), con ở B hàng tiếp theo (A trống), không hàng trắng xen kẽ, không vượt `<END>`.
- `READBACK=PASS/FAIL`, `CONTENT_QA`, `PRESERVED`, `BLOCKERS`; Prime tự đọc lại dữ liệu trực tiếp.
- `CURRENT_STEP`: chỉ Prime báo ghi/readback `CURRENT_STEP.VALUE = "3" khi đủ điều kiện.
- `MODULE_SYNC`: đúng task `generateModuleReadmeFromSheetByLocation`, module/I3/gid, DRY_RUN status, `APPLY=NOT_RUN`, `DEFERRED_UNTIL_STEP_5` hoặc blocker thật; không dùng ByFiles.

## 4. Phạm vi và điều kiện hoàn thành

- STEP 3 chỉ tạo **Menu hai cấp** từ đề `C3` **và Special Note do worker tự đọc**, ghi `A20:B<END>` của đúng tab. Giữ nguyên header `A18:B19`, `C3`, `B1`, `CURRENT_STEP.VALUE` (đến khi Prime cập nhật trạng thái), **cả hai ô Special Note**, Roadmap, Knowledge, Video, Template và tab khác.
- Không viết Knowledge Markdown vào cột C trở đi, không tự chạy STEP 4, không sửa source hoặc commit/push.
- **STEP 3 Sheet COMPLETED** khi Menu đầy đủ/đúng C3, readback chính xác, Prime QA PASS, module-local README DRY_RUN đã được kiểm tra **không có lỗi task/config** và `CURRENT_STEP.VALUE` được cập nhật/readback an toàn. `DEFERRED_UNTIL_STEP_5` **không có nghĩa module đã sync**; STEP 5 chịu trách nhiệm APPLY thực. Nếu còn blocker hoặc thiếu mục, **không cập nhật CURRENT_STEP.VALUE**, phải nêu rõ.
