# STEP 6 — Prime: Interview, Case Study, Quiz (4 Phase)

## 1. Sơ đồ vùng ô — hợp đồng hiện hành

| Loại | Phase 1 — thô, 2 hàng | Phase 2 + Phase 3 — dùng CHUNG 5 hàng | Phase 4 — 3 hàng/group |
| --- | --- | --- | --- |
| Interview | `P25:R26` | `P20:R24` | `P:R`, bắt đầu hàng 27 |
| Case Study | `S25:U26` | `S20:U24` | `S:U`, bắt đầu hàng 27 |
| Quiz | `V25:W26` | `V20:W24` | Không áp dụng |

- **Phase 1:** ba worker (một worker/loại) tạo nội dung thô từ `A1,B1`, không nhóm/YAML/giải thích. Quiz gồm câu hỏi, bốn đáp án A/B/C/D và một đáp án đúng. Hai hàng Phase 1 đã được dời xuống 25–26.
- **Phase 2:** ba worker (một worker/loại) đọc Phase 1 cùng loại, phân group và tạo **một tài liệu YAML khung đầy đủ các group** vào vùng năm hàng ở bảng. Không viết thông tin của Phase 3/4. Interview đặt `knowledge: ""`; Case Study đặt `resolve: ""`, `knowledge: ""`; Quiz đặt `explanation: ""` cho cả bốn lựa chọn. **Mỗi câu Interview/Quiz và mỗi case Case Study đều phải có `readmeRelated: {anchorList: []}`** ngay từ Phase 2; **không có `apiRelated`**.
- **Phase 3:** ba worker (một worker/loại) **đọc và cập nhật chính tài liệu YAML Phase 2 ở đúng vùng năm hàng đó**. Interview chỉ bổ sung `knowledge`; Case Study chỉ bổ sung `resolve` và `knowledge`; Quiz chỉ bổ sung `answers[].explanation` cho A/B/C/D. **Không tạo YAML riêng cho group, không sử dụng các hàng 27+ cho Phase 3**. Giữ nguyên `groupID`, `groupName`, thứ tự, câu hỏi/tình huống, lựa chọn, `correctAnswerId` **và toàn bộ `readmeRelated.anchorList`**. Prime kiểm tra semantic diff chỉ có các field được giao.
- **Phase 4:** chỉ Interview (`answer`) và Case Study (`code`); **mỗi worker một loại × một group**, mỗi group một tài liệu YAML riêng trong **3 hàng × 3 cột**. Không sửa khung Phase 2/3. Quiz hoàn tất sau Phase 3.

### Khung Related của Interview, Case Study và Quiz — từ STEP 6

- Mỗi phần tử `interviewQuestion[].questions[]`, `caseStudy[].cases[]` và `quiz[].questions[]` có **duy nhất** `readmeRelated.anchorList: []` làm khung liên kết Knowledge (ngoài các trường nghiệp vụ của câu hỏi/case); **không tạo `readmeRelated.anchor`, `readmeRelated.file` hay `apiRelated`** trong định dạng STEP 6 mới. Đây là hợp đồng cho dữ liệu group STEP 6, **không phải YAML Interview/Quiz legacy theo chương của STEP 9/10 cũ**.
- `anchorList` ở STEP 6 là **danh sách string**, mặc định `[]` và chưa điền ID trong các Phase 2/3/4. Khi được điền ở bước sau, mỗi phần tử là **ID của bài Knowledge ở A hàng con**, có thể trỏ tới **bất kỳ chương nào trong cùng module**; không suy từ filename, group hay vị trí, không tham chiếu module khác. Danh sách có thể gồm nhiều bài, không trùng ID.
- **STEP 7 mới** (`STEP_7.md`) đối chiếu Knowledge toàn module qua hai Phase: Phase 1 worker scan theo chương và ghi proposal tạm, **Prime tổng hợp và điền `anchorList` lần đầu**; Phase 2 mỗi worker phụ trách toàn bộ Interview/Case Study/Quiz, **trực tiếp recheck và ghi YAML chính thức**, Prime chỉ QA/xác nhận và dọn staging. Ý tưởng `null`/status báo câu hỏi/case cần bổ sung Knowledge **chưa được chốt schema/triển khai**; STEP 6 chỉ tạo `anchorList: []`, không đánh đồng với thiếu Knowledge.
- **Backfill OOP hiện hữu:** chỉ thêm khung `readmeRelated.anchorList: []` cho từng câu Interview/Quiz và từng case Case Study trong YAML Phase 2/3 đã có; giữ nguyên toàn bộ câu hỏi/tình huống, `resolve`, `knowledge`, đáp án, `explanation`, group và các block Phase 1/4. Không dùng importer legacy. Nếu tìm thấy relation cũ không trống/không tương thích, phải review trước, không xóa nội dung.

### Tọa độ Phase 4 (chỉ Interview / Case Study)

| Group | Interview | Case Study |
| --- | --- | --- |
| 1 | `P27:R29` | `S27:U29` |
| 2 | `P30:R32` | `S30:U32` |
| 3 | `P33:R35` | `S33:U35` |
| N | `P(27+3×(N−1)):R(29+3×(N−1))` | `S(27+3×(N−1)):U(29+3×(N−1))` |

Suy ra `groupID = (startRow−27)/3 + 1`. Vùng WRITE phải bắt đầu ở hàng ≥27, `(startRow−27) % 3 = 0`, **đúng 3 hàng và đúng 3 cột**. GroupID phải tồn tại trong YAML Phase 3 của chính loại đó. Ví dụ Interview group 4 = `P36:R38`; Case Study group 4 = `S36:U38`.

Block Interview là YAML `groupID` + `answers: [{questionIndex, answer}]`; block Case Study là YAML `groupID` + `codes: [{caseIndex, code}]`. Index bắt đầu từ 1 và đầy đủ từng câu/case của group; `answer`/`code` không rỗng. Không ép block Phase 4 quay về vùng năm hàng Phase 2/3.

## 2. Giao việc và nghiệm thu

Prime đọc `GENERAL_AGENT_RULES.md`, các tài liệu `rules/`, xác minh I3/gid/tab, và file worker phù hợp trong `module-generate-agent/worker/STEP_6_WORKER/`. **Chỉ gửi đúng ba dòng**, không kèm Phase/groupID hay giải thích:

~~~text
<VERIFIED_TAB_URL> | READ=A1,B1
WRITE=<EXACT_OWNED_RANGE>
/java-learning/module-generate-agent/worker/STEP_6_WORKER/<EXACT_WORKER_FILE>.md
~~~

Prime QA lần lượt: Phase 1 đủ nội dung; Phase 2 đủ nhóm và YAML khung; Phase 3 cập nhật đầy đủ trường cơ bản trong chính vùng Phase 2; Phase 4 đủ từng group theo index, code/answer có chất lượng và readback chính xác. Không giao hai writer đồng thời ghi chung vùng Phase 2/3. **Phase 3 được quyền thay đổi các giá trị do Phase 2 tạo trong chính vùng đã giao**, nhưng phải bảo toàn toàn bộ nội dung không thuộc field của Phase 3.

**MAX-FILL:** dựng toàn bộ chuỗi trước rồi điền lần lượt ô theo hàng trái→phải, trên→dưới. Mỗi ô là tiền tố **dài nhất** thỏa đồng thời ≤30.000 Unicode code points và ≤32.767 UTF-16 code units. **Chỉ chuyển ô khi thêm ký tự tiếp theo sẽ vượt giới hạn**; còn vừa ô đầu thì ô sau bắt buộc trống. Cấm chia đều, chia theo câu/group/line/YAML hoặc cắt surrogate pair. Ghép ô không thêm separator phải khôi phục đúng chuỗi nguồn. Vi phạm `STEP6_NON_MAX_FILL` → QA FAIL; không đủ chỗ `STEP6_CELL_CAPACITY` → BLOCKER.

**An toàn và tương thích OOP:** OOP đã có Phase 1 trong hàng 25–26, Phase 2/3 trong 20–24 và một số block Phase 4 từ 27. Trước khi chạy tiếp, Prime **đọc dữ liệu thực tế** và chỉ ghi vào block group chưa có dữ liệu; không tự migrate/xóa/ghi đè block cũ. Nếu có vùng chứa nội dung thuộc layout khác hoặc groupID/index sai, báo `STEP6_LAYOUT_MIGRATION_REQUIRED`. Giữ snapshot đã tạo trước thay đổi.

## 3. Module sync và CURRENT_STEP — một importer chung cho STEP 6/7

**Phân biệt hai mốc:** `SHEET_AUTHORING=PASS` chỉ xác nhận đủ bốn Phase và các dữ liệu trên đúng tab; **không phải** `MODULE_SYNC=PASS` hoặc STEP 6 đã hoàn thành ở cấp module. Prime có thể báo kết quả nghiệm thu Sheet, nhưng **không được ghi `CURRENT_STEP.VALUE = "6"` chỉ dựa trên nội dung Sheet**.

**Importer đã đăng ký:** module-local Gradle task `generateModuleAssessmentsFromSheetByLocation` (`ASSESSMENTS`), dùng **chung cho STEP 6 và STEP 7**. Thuần Groovy/Sheets API/SnakeYAML, không AI/thinking, không gọi importer Interview/Quiz legacy và không sửa Sheet. Đọc YAML chính `P20:R24`/`S20:U24`/`V20:W24`, đọc Phase 4 theo group từ hàng 27; ghép Interview `answer` theo `(groupID, questionIndex)` và Case Study `code` theo `(groupID, caseIndex)`; Quiz giữ nguyên cấu trúc group. Kiểm chính xác `readmeRelated.anchorList` với A-child ID toàn module, cho phép `[]`, không chuyển thành `anchor/file` và không thêm `apiRelated`.

**Destination thuộc đúng module `I3`:** `content/interview/vi/interview.yml`, `content/casestudy/vi/casestudy.yml`, `content/quiz/vi/quiz.yml`. Cả ba folder `vi` là **snapshot do importer sở hữu**: sau validate tất cả input và stage đầy đủ, một transaction **CLEAN toàn bộ nội dung cũ của cả ba folder → FILL ba file mới → exact readback**, rollback khi lỗi; tuyệt đối không xóa `en/`, Knowledge/Metadata/Summary, Video, Swagger, hoặc file module khác. Không import/publish Portal hay learning-platform trong STEP 6/7.

Sau khi toàn bộ worker Phase 1–4 và Sheet QA PASS, Prime chạy tại root `java-learning` với đúng URL/gid/I3/Gradle module path đã đối chiếu:

```powershell
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleAssessmentsFromSheetByLocation -PfileUrl=<VERIFIED_SHEET_URL> -PsheetID=<VERIFIED_GID> -PsheetMode=DRY_RUN
.\gradlew.bat :<EXACT_MODULE_GRADLE_PATH>:generateModuleAssessmentsFromSheetByLocation -PfileUrl=<VERIFIED_SHEET_URL> -PsheetID=<VERIFIED_GID> -PsheetMode=APPLY
```

Completion gate của Prime:

1. **Nguồn và ownership:** xác minh URL, tên tab, gid, `I3` và đúng một `<I3>/master.json` như `rules/PRIME_RULES.md §2`; chỉ dùng dữ liệu STEP 6 mới: Interview `P20:R24`, Case Study `S20:U24`, Quiz `V20:W24` và các group answer/code Phase 4 từ hàng 27. Phase 1 hàng 25–26 là nguồn soạn thảo, không tự dùng làm module output. Không đọc nhầm layout legacy P:S/T:W theo chương.
2. **QA toàn bộ bốn Phase:** ba tài liệu group parse được, groupID/groupName/index nhất quán, item và câu trả lời/giải pháp đầy đủ; Phase 4 có chính xác các block Interview answer và Case Study code cần thiết theo từng group/index, không thiếu/trùng, nội dung phù hợp với câu/case, MAX-FILL/exact readback PASS. Giữ nguyên `readmeRelated.anchorList` theo schema STEP 6, không thêm `apiRelated` hay `readmeRelated.anchor/file` và không lấy Phase 4 làm nguồn thay đổi Phase 2/3.
3. **Importer/validator thực:** phải parse/validate đủ 3 tài liệu group và toàn bộ Phase 4, chống duplicate/gap/extraneous group/index, unknown Knowledge A-child ID, công thức, non-MAX-FILL và lỗi dữ liệu. Không bỏ sót group hoặc lặng lẽ bỏ trường/câu/case. Bất kỳ `SKIPPED_*`, lỗi task/schema, thiếu output hoặc importer legacy chỉ xử lý theo chương là `BLOCKER: STEP6_MODULE_SYNC`. Không coi snapshot Sheet là module output.
4. **DRY_RUN → APPLY → exact readback:** Prime chạy task module-local tương thích đã được đăng ký, đúng module `I3`, kiểm status và semantic diff/backup/rollback; chỉ APPLY sau DRY_RUN PASS. Kiểm chứng output được lưu thực sự vào module, đủ cả Interview/Case Study/Quiz và answer/code, giữ đúng dữ liệu ngoài scope. Không có file output hoặc chỉ kiểm tra YAML trên Sheet → **không** tính là đồng bộ thành công.
5. **Cập nhật trạng thái sau sync:** chỉ khi toàn bộ worker đã release, QA Sheet + module đều PASS/UNCHANGED, cleanup và exact readback xong, **Prime phải cập nhật/readback `CURRENT_STEP.VALUE = "6"`** theo `rules/PRIME_RULES.md §2` nếu trạng thái hiện tại cho phép: snapshot, chống concurrent writer, **không ghi lùi**, không tự chuyển `"8"` lịch sử, chỉ đổi VALUE và exact readback. Nếu giá trị đã đi xa hơn hoặc provenance chưa rõ, bảo toàn và báo đúng trạng thái; nếu ghi bắt buộc thất bại, báo BLOCKER thay vì COMPLETED.

**Không chạy** task Interview/Quiz P:S/T:W legacy; **không commit/push** trong STEP 6. Chỉ có Sheet QA mà chưa chạy importer DRY_RUN → APPLY/readback PASS thì `MODULE_SYNC=NOT_RUN/BLOCKER`, **chưa được cập nhật CURRENT_STEP**. Importer dùng chung này sẽ chạy lại sau STEP 7 để lưu `anchorList` đã hoàn thiện; không gộp nghiệm thu STEP 6 và STEP 7 thành một checkpoint.
