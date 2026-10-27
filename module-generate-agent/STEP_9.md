# STEP 9 — PRIME ONLY: Interview + Independent Summary Questions

## 0. Mục tiêu và thứ tự

Mỗi Menu Cấp 1 có **hai nhóm câu hỏi**:
- **Interview**: câu hỏi phỏng vấn chuyên sâu, tình huống, cơ chế, trade-off, debug. Worker được đọc toàn bộ Knowledge C:N của chương.
- **Summary Questions**: tự kiểm tra kiến thức **toàn chương**, do worker độc lập chỉ nhận tên module, tên các Menu Cấp 1 toàn module, tên Menu Cấp 2 của chương. **Tuyệt đối không đọc Knowledge/README, Summary STEP 5, Swagger/API, Interview/Quiz, worker khác hoặc lịch sử chat.**
STEP 8 dành riêng Real-world Problems & Exercises; STEP 9 không làm Video hay filename. Một chapter có hai công việc sáng tác độc lập; Prime chủ trì toàn bộ việc ghép và nghiệm thu.

## 1. Hợp đồng Google Sheet

A1/B1 = module; A cha = tên chương; B cha = filename thuộc STEP 5; B hàng con = tên bài. Interview nguồn nằm **trọn trong bốn ô P:S ở cùng hàng cha**. Header chuẩn: `P18=Interview`, `P19:S19=Interview YAML`; chấp nhận **đồng nhất** legacy `Interview JSON`. Không chia cứng P:Q/R:S; P→Q→R→S là **một chuỗi liên tục không separator**; mỗi ô tối đa 30.000 Unicode code points.

```yaml
questions:
  - question: "Khi nào nên dùng composition thay inheritance?"
    answer: |-
      Trả lời phỏng vấn có giải thích trade-off.
summaryQuestions:
  - question: "Composition giải quyết vấn đề gì?"
    answer: |-
      Tổ hợp hành vi thông qua các đối tượng.
```

`questions[]` bắt buộc không rỗng. Interview worker chỉ tạo `question`/`answer`; pure-code Normalize thêm `readmeRelated.anchor` và `apiRelated{controller,methodSignature}`. `summaryQuestions[]` có đúng **question/answer**, không metadata/related. Dữ liệu lịch sử chỉ có `questions[]` được giữ nguyên, **không tự chế Summary**; với chương STEP 9 được làm mới đầy đủ, cần đủ hai nhóm.

## 2. Prime giao việc và ownership

Trước giao đọc GENERAL_AGENT_RULES, toàn bộ rules/, file STEP này, `worker/STEP_9_WORKER.md`, mapping module-generate-info; xác minh URL/gid/I3/module và tất cả hàng Menu Cấp 1/Cấp 2. Giữ worker pool Mode A/B và worker-only đúng role; không tự gửi nội dung hay gợi ý câu hỏi.

- **ROLE=INTERVIEW**: quyền đọc A/B chương + C:N tất cả hàng con + Difficulty O nếu được cấp. Quyền ghi P:S hàng cha **chỉ khi trống/FORMAT_FIX được chứng minh**; không đọc Summary staging, không chỉnh YAML hợp lệ đã Normalize.
- **ROLE=SUMMARY**: quyền đọc **chỉ A/B tên module, Menu Cấp 1 toàn module, Menu Cấp 2 của chương được giao**. Quyền ghi **duy nhất** `<MODULE>/build/step9/summary/<chapter>/summary.yml` mà Prime đã xác nhận/khởi tạo ownership. Không đọc hay ghi P:S, dữ liệu Knowledge, file README, API, phỏng vấn/quiz, metadata, chapter khác. File staging là một YAML với root `summaryQuestions[]`, từng item chỉ question/answer.
- Prime giao bốn dòng: (1) tab URL/gid, (2) role + tọa độ đọc được phép, (3) vùng ghi/staging thuộc ownership, (4) file worker-only. Hai role không bao giờ cùng sở hữu vùng P:S.

## 3. Merge — Normalize — Import (thuần CODE)

1. Prime QA toàn tab và kết quả từng worker, exact readback P:S cùng staging; kiểm tra notes/formulas/chips/validation, 30k codepoints mỗi fragment, fingerprint trước ghi và tình trạng concurrent edits. Không ghi nếu có BLOCKER hoặc Sheet thay đổi.
2. Sau toàn bộ chương PASS, Prime chạy root `normalizeInterviewQuestionsFromSheet` **DRY_RUN → APPLY**, bằng `-PfileUrl=<URL> -PsheetID=<GID> -PinterviewMode=...`. **Chính task này** đọc Interview P:S và Summary staging tại `<I3>/build/step9/summary/<chapter>/summary.yml`, gọi pure-code `InterviewSheetNormalizeService.mergeSummaryQuestions`, kiểm tra Interview giữ nguyên, từ chối overwrite Summary đã có khác, rồi normalize/serialize/split fragments và ghi P:S **một batch có fingerprint + exact readback**. Không yêu cầu Prime tự ghép nội dung, không AI tham gia parse/merge/split/validation/import. Khi YAML >120k code points → BLOCKER; không lấn sang Quiz/Video.
3. Normalize chỉ thêm defaults cho `questions[]`, giữ `summaryQuestions[]` nguyên ý nghĩa và hai scalar; không tạo/dịch câu hỏi. Nếu staging đổi trong quá trình preflight hoặc staging có mà Interview trống → BLOCKER.
4. Prime chạy module-local `:<MODULE_GRADLE_PATH>:generateModuleInterviewFromSheetByLocation` DRY_RUN → APPLY, tham số `-PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=...`. Import code thuần, **mỗi Menu Cấp 1** ghi:
   - `content/readme/docs/vi/menu/<chapter>/questions/interview.yml`: root `questions[]`, có relations.
   - `content/readme/docs/vi/menu/<chapter>/questions/summary.yml`: root `questions[]`, chỉ question/answer; nếu chưa có nguồn Summary (legacy) thì không tạo.
5. CLEAN/FILL atomic và rollback: chỉ sở hữu `questions/` mới và `interview/` cũ, loại file Interview cũ sau QA, không sửa Knowledge, Quiz, Video, EN. STEP 11 gắn related cho Interview, không cho Summary.
6. Sau ALL PASS/UNCHANGED, đối chiếu exact readback, Prime cập nhật `CURRENT_STEP.VALUE="9"` trong master.json đúng module. Không dùng J3, không tự chạy STEP 10, Video, commit/push.

## 4. Completion gate

Hai role độc lập và toàn bộ chương đã QA; nguồn YAML một document, header hợp lệ, không orphan dữ liệu P:S hàng con, không vượt capacity, không drift/race; Normalize DRY_RUN/APPLY/readback PASS, importer DRY_RUN/APPLY/readback PASS (SKIPPED không đủ); Interview schema và Summary Questions schema PASS; output và cleanup/rollback đúng scope; đếm Interview và Summary riêng; CURRENT_STEP readback PASS. STEP 13 clone cả hai file VI→EN, STEP 14 dịch riêng, STEP 15 ghép hai file lại về YAML P:S trong Sheet EN. Existing Interview chính là nguồn truth; không tái tạo phỏng vấn hợp lệ để chèn Summary.
