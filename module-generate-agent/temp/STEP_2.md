# STEP 2 — PRIME ONLY: Điều phối Roadmap Title / Purpose / Objectives

## Mục tiêu và phân tách PRIME / WORKER

Giao cho **đúng một sub-worker** thực hiện **toàn bộ Roadmap của một tab module** từ **nguyên văn câu hỏi `C2`**. Kết quả gồm các milestone `F = Title`, `G = Purpose`, `H = Objectives` **và phần giới thiệu cơ bản của module tại `B17`**, dùng cho `content/readme/docs/vi/BASE.md`. Không chia roadmap giữa nhiều worker, không tạo đề bài mới thay cho `C2`, không ép WHAT/WHY/HOW hoặc số milestone trái với câu hỏi.

- **File `STEP_2.md` này chỉ dành cho Prime. Worker KHÔNG đọc.**
- **Toàn bộ chỉ dẫn thực thi dành cho worker nằm trong `/java-learning/module-generate-agent/temp/worker/STEP_2_WORKER.md`.** Mỗi worker chỉ đọc **toàn văn đúng file worker-only đó**; không đọc file Prime, STEP khác, General Rules, rules folder, TXT hoặc câu trả lời từ task khác.
- Prime phải tự đọc `module-generate-agent/temp/GENERAL_AGENT_RULES.md`, **liệt kê và đọc đầy đủ mọi file hiện có trong `module-generate-agent/rules/`**, `module-generate-info.txt`, toàn văn **cả `STEP_2.md` và `worker/STEP_2_WORKER.md`** trước khi giao việc. Nếu mất context hoặc không chắc chắn, đọc lại.
- **Một lần giao = toàn bộ Roadmap một tab = một lượt làm trọn** (đọc → tạo/hoàn thiện → kiểm tra → ghi → readback → báo cáo → SLEEPING). Không cần PREPARED, pha chờ thứ hai, hoặc Prime bổ sung giải thích giữa chừng.
- **Tin nhắn Prime gửi worker CHỈ có bốn dòng:** link tab, **tọa độ `C2`, `A17`, `B17`, ô tiêu đề `Special Note` và ô giá trị của nó**, vùng Roadmap giới hạn chính xác và đường dẫn `worker/STEP_2_WORKER.md`. **Tuyệt đối không chép, diễn giải, tóm tắt hoặc chuyển tiếp nội dung Special Note trong tin giao việc**; không kèm lời chào, nội dung đề bài, rule, hint, ghi chú hay ví dụ. Worker tự đọc trực tiếp tất cả ô được giao.

## 1. Bố cục và giới hạn của STEP 2

| Vị trí | Ý nghĩa | Quyền |
| --- | --- | --- |
| `C2` | Câu hỏi Roadmap gốc, bao gồm mọi lưu ý | Chỉ đọc |
| Ô có tiêu đề `Special Note` và ô giá trị ngay dưới nó (hiện tại là `K2`/`K3`) | Ghi chú riêng của module, có thể có nội dung hoặc để trống | Worker **phải đọc nguyên văn cả hai ô**, không ghi |
| **`A17`** | Nhãn/ngữ cảnh giới thiệu module | Worker **phải đọc, chỉ đọc, không sửa** |
| `F1:H1` | Nhãn/khu vực Roadmap | Giữ nguyên |
| `F2:H2` | Header `Tiêu đề` / `Mục đích` / `Mục tiêu học tập` | Chỉ bổ sung nếu cả ba ô trống; nếu đã có, giữ nguyên |
| `F3:F<END>` | **Title**, ngắn để hiển thị trên sơ đồ | Chỉ fill mới; đã có nội dung trong vùng đích → BLOCKER |
| `G3:G<END>` | **Purpose**, thông thường 1–2 câu | Chỉ fill mới; đã có nội dung trong vùng đích → BLOCKER |
| `H3:H<END>` | **Objectives**, thông thường 2–4 mục tiêu, mỗi mục một dòng **trong cùng ô** | Chỉ fill mới; đã có nội dung trong vùng đích → BLOCKER |
| **`B17`** | **Introduction** — bài giới thiệu Markdown phù hợp Roadmap, `A17` và Special Note, dành cho `BASE.md` | Worker **phải đọc; chỉ fill nếu trống**, có nội dung → BLOCKER và dừng |

`<END>` là **hàng cuối Prime đã xác minh còn thuộc Roadmap**, không vượt sang Menu/Knowledge. Prime phải xác định từ **bố cục thật** của tab (vị trí nhãn Menu, header và số hàng có thể dùng). **`B17` phải được Prime xác minh không thuộc Menu, không có header/giá trị khác cần bảo toàn; nếu trùng vùng cấu trúc hiện hữu thì `BLOCKER: INTRODUCTION TARGET`, không tự dịch ô.** Cột `I` trở đi, Knowledge `C:N` dưới vùng Menu, Difficulty `O` và Video `X:AA`, `J3` và vùng `Special Note` không thuộc vùng worker được ghi.

Mỗi milestone = **một bộ ba F/G/H trên cùng hàng**. Nếu **bất kỳ ô nội dung `F3:H<END>` hoặc `B17`** đã có nội dung, Prime/worker phải báo `BLOCKER: EXISTING TARGET CONTENT` và dừng toàn nhiệm vụ, **không điền nốt những ô còn trống**. Chỉ chỉ thị đặc biệt của người dùng mới cho phép xử lý ngoại lệ. Không dời/thêm hàng hoặc thay đổi nội dung cũ.

## 2. Prime — xác minh tab và giao việc

1. **Đọc đầy đủ context/rules** ở đầu file. Mỗi tab STEP 2 chỉ giao cho **một worker tại một thời điểm**. Kiểm tra pool `ACTIVE + SLEEPING` theo **Mode A/B/C của session** (mặc định A=3); **ưu tiên tái sử dụng SLEEPING** và tuyệt đối không spawn khi còn worker SLEEPING. Không giao nhiệm vụ thứ hai cho worker ACTIVE; tôn trọng lệnh dừng/loại trừ worker.
2. Đọc `module-generate-agent/temp/module-generate-info.txt`: lấy `Excel Page URL` và mapping **`<TÊN MODULE> sheet id : <SHEET_ID>`** đúng module. `SHEET_ID` là **gid của tab**, không phải ID file spreadsheet. Chuẩn hóa URL và lập `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>`.
3. **Xác minh** spreadsheet, gid, tên tab và `B1` đúng module. Nếu mapping không có/trùng/sai hoặc không đọc được tab: **BLOCKER**, không chọn tab theo suy đoán.
4. **Xác định từ Sheet thực tế hai tọa độ Special Note:** tìm **ô tiêu đề có đúng nhãn `Special Note`** trong vùng `Module Project` của tab đích; xác định **ô giá trị ngay bên dưới, cùng cột** (ví dụ hiện tại `K2` và `K3`). Xác minh duy nhất, đúng cấu trúc, cả hai ô tồn tại và đọc được. Nếu thiếu nhãn, vị trí mơ hồ hoặc ô giá trị không truy cập được: **`BLOCKER: SPECIAL NOTE LOCATION`**, không tự giả định `K2/K3` hay giao thiếu tọa độ. **Prime chỉ gửi địa chỉ hai ô, TUYỆT ĐỐI không tóm tắt, sao chép, viết lại hay chuyển nội dung Special Note cho worker, kể cả khi nội dung dài.** Ô giá trị trống vẫn là vị trí hợp lệ và phải gửi để worker tự kiểm tra.
5. Đọc **metadata/bố cục tab** để xác định giới hạn `F2:H<END>` nằm hoàn toàn phía trên Menu; kiểm tra header `F2:H2` và toàn vùng nội dung `F3:H<END>`, kể cả giá trị/công thức/ghi chú/chip. **Prime đọc và xác minh cả `A17` và `B17`**: A17 là nhãn/ngữ cảnh chỉ đọc, B17 phải là đích Introduction hợp lệ, không trùng Menu/header và chưa có nội dung/metadata cần bảo toàn. Không đoán từ tab khác.
6. **Nếu bất kỳ ô nội dung `F3:H<END>` hoặc `B17` đã được điền** (kể cả Roadmap chỉ có Title, có dữ liệu một phần hoặc đã hoàn chỉnh), báo `BLOCKER: EXISTING TARGET CONTENT` và **dừng, không giao worker fill phần còn lại**. Header đúng schema ở F2:H2 và nhãn A17 là nguồn/ngữ cảnh, không phải content cần sinh. Header xung đột, scope không an toàn hoặc thiếu chỉ thị thay thế rõ ràng thì báo BLOCKER.
7. Chỉ giao **bốn dòng định vị** theo mẫu dưới. Không gửi nội dung `C2`, **nội dung/diễn giải/tóm tắt Special Note**, roadmap cũ, câu trả lời gợi ý, cách viết hay bất kỳ văn bản khác. Worker tự đọc `C2`, **cả nhãn và giá trị Special Note**, **`A17`, `B17`**, `F2:H<END>` trực tiếp, dùng `worker/STEP_2_WORKER.md` làm chỉ dẫn duy nhất.
8. Nhận báo cáo; **Prime tự đọc lại F2:H<END>, A17 và B17** để kiểm tra Roadmap, nhãn A17 không đổi **và giới thiệu Markdown** (có tiêu đề module, phạm vi, mục tiêu, định hướng học; không bịa ngoài roadmap/Special Note). Chỉ chấp nhận `COMPLETED` khi worker `READBACK PASS` và **Prime QA PASS** cho cả Roadmap và B17; nếu có blocker/failed, không retry ghi mù.
9. Sau khi **toàn bộ Roadmap và B17** hoàn chỉnh, không còn worker ACTIVE/ownership và Prime QA PASS, **Prime chạy hai task RIÊNG cho ĐÚNG MỘT module**: (1) `generateModuleRoadmapFromSheetByLocation` (DRY_RUN → APPLY), kiểm tra `content/roadmap/vi/roadmap.yml`; (2) `fillIntroduction` (DRY_RUN → APPLY), đọc B17 để ghi **`content/readme/docs/vi/BASE.md`**. Cả hai dùng cùng URL/gid và module Gradle path đã xác minh `I3` theo `GENERAL_AGENT_RULES.md §6`; không gọi ByFiles/task tổng hợp. Nếu B17 trống, `SKIPPED_*`, lỗi, output sai hoặc chỉ DRY_RUN: `BLOCKER: MODULE INTRODUCTION SYNC`, giữ nguyên J3. **Chỉ khi cả Roadmap và Introduction PASS/UNCHANGED** và Prime exact readback mới cập nhật `J3 = STEP 2`.

### Task độc lập của Prime sau khi hoàn thiện B17

```powershell
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleRoadmapFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=DRY_RUN
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleRoadmapFromSheetByLocation -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=APPLY
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:fillIntroduction -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=DRY_RUN
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:fillIntroduction -PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=APPLY
```

`fillIntroduction` là task module-local **riêng**, không nằm trong importer tổng hợp. Task chỉ đọc B17, từ chối I3 không khớp, nguồn trống/quá giới hạn hoặc sai cấu trúc đích; APPLY thay toàn văn `BASE.md` khi nội dung khác, DRY_RUN không ghi, không chỉnh Sheet và không can thiệp `content/readme/docs/vi/menu/`. Prime phải đọc lại file đích và đối chiếu **exact** với B17 sau APPLY.

### Tin nhắn Prime gửi một worker — CHỈ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
C2, <SPECIAL_NOTE_TITLE_CELL>, <SPECIAL_NOTE_VALUE_CELL>, A17, B17
F2:H<END_ROW_ĐÃ_XÁC_MINH>, B17
/java-learning/module-generate-agent/temp/worker/STEP_2_WORKER.md
```

`<END_ROW_ĐÃ_XÁC_MINH>` là **số hàng cụ thể**, ví dụ `17` nếu Menu bắt đầu ở hàng 18. Hai tọa độ Special Note phải được thay bằng **địa chỉ ô thực**, ví dụ `K2, K3` nếu đã xác minh đúng; không gửi ký hiệu `...`, placeholder hay nội dung ô khi giao thật. Mọi yêu cầu thực thi nằm trong file worker-only.

## 3. Báo cáo và nghiệm thu

Prime yêu cầu/đối chiếu các trường báo cáo của worker:

- `STATUS=COMPLETED/BLOCKER/FAILED`, tab/gid, `SOURCE=C2`.
- `OUTPUT`: chính xác ô/vùng đã ghi, số ô, số milestone; tách rõ Title cũ được giữ với Title/Purpose/Objectives mới được điền.
- `PREFLIGHT`: header/nhãn nguồn không thay đổi; **toàn vùng nội dung Roadmap F3:H<END> và B17 trống trước WRITE**; có sẵn nội dung → BLOCKER/dừng toàn nhiệm vụ, không ghi một phần.
- `INTRODUCTION_LABEL`: đã tự đọc `A17` và xác minh nhãn giữ nguyên; `B17` là ô Introduction được đọc trước/sau ghi.
- `SPECIAL_NOTE`: xác nhận worker **đọc trực tiếp cả ô tiêu đề và ô giá trị**, ghi trạng thái có nội dung/trống và đã áp dụng vào kết quả nếu có; **không cần chuyển lại nguyên văn ghi chú trong báo cáo**.
- `CONSISTENCY`: số bộ ba F/G/H hoàn chỉnh, thứ tự và chất lượng mục tiêu.
- `READBACK=PASS/FAIL`, `CONTENT_QA`, `PRESERVED`, `BLOCKERS`; Prime tự QA nội dung và các ô không thuộc phạm vi.
- `CURRENT_STEP`: chỉ Prime báo kết quả ghi/readback `J3 = STEP 2` sau khi toàn STEP đạt yêu cầu.
- `MODULE_SYNC`: module Gradle path/I3, task `generateModuleRoadmapFromSheetByLocation`, kết quả `DRY_RUN` → `APPLY`, `SYNC_STATUS`, file đích thực và blocker nếu có. **Không dùng `generateModuleContentFromSheetByFiles`.**
- `INTRODUCTION`: `B17` Markdown/readback/QA; task độc lập `fillIntroduction` với `DRY_RUN` → `APPLY`, `INTRODUCTION_SYNC_STATUS`, `content/readme/docs/vi/BASE.md` và blocker nếu có.

## 4. Phạm vi và điều kiện hoàn thành

- STEP 2 **chỉ** tạo mới Roadmap trong `F:H` khi vùng nội dung đích còn trống, header `F2:H2` khi cả ba ô trống, milestone từ hàng 3 tới trước Menu, **đồng thời điền giới thiệu module ở B17** khi trống. Worker **phải đọc A17 và B17**, không sửa A17. Có nội dung cũ tại F3:H<END> hoặc B17 → BLOCKER/dừng. Không sinh Menu, Knowledge hoặc Video; không sửa `C2`, `B1`, `K3`, vùng khác/tab khác.
- Worker độc lập từ `C2` **và Special Note tự đọc nguyên văn trong Sheet**, không dùng lịch sử/context cũ, không tự tìm tab, không spawn, không commit/push, không chạy STEP tiếp theo.
- **Toàn STEP 2 chỉ COMPLETED** khi Roadmap **và B17** đúng yêu cầu, dữ liệu cũ bảo toàn, worker/Prime readback QA PASS, **cả Roadmap import và `fillIntroduction` PASS**, `J3` được cập nhật/readback an toàn. Nếu chưa đủ phải báo `BLOCKER/FAILED` thực tế.
