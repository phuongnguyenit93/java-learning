# STEP 7 — Prime: Liên kết Knowledge toàn module cho Interview, Case Study, Quiz

## 0. Mục đích và ranh giới

STEP 7 **mới** chạy sau STEP 6 hiện hành (Interview/Case Study/Quiz bốn Phase). Mục tiêu là đánh giá mức độ liên quan **theo nội dung thực** giữa từng bài Knowledge trong toàn module và từng câu hỏi/tình huống của STEP 6, rồi điền `readmeRelated.anchorList` **khi có bằng chứng**. Đây **không phải** STEP 7 Swagger lịch sử hay STEP 11 enrichment Quiz/Interview **legacy theo chương**.

- Nguồn canonical: cùng tab Google Sheets đã được xác minh URL, gid, `A1/B1`, `I3` và đường dẫn module. Knowledge là A-child ID + B-child title + C:N-child Markdown ở vùng Menu từ hàng 20; A-parent/B-parent/C-parent dùng để phân chương. Đối chiếu exact ID với `content/readme/docs/vi/menu/` nếu cần.
- Nguồn câu hỏi STEP 6: **Interview `P20:R24`** (`interviewQuestion[].questions[]`), **Case Study `S20:U24`** (`caseStudy[].cases[]`), **Quiz `V20:W24`** (`quiz[].questions[]`). Ghép các ô trái→phải, trên→dưới, **không separator**. **Không đọc/ghi** Phase 1 hàng 25–26 hoặc Phase 4 từ hàng 27: hai phase STEP 7 chỉ sử dụng đúng YAML tại hàng 20–24 và Knowledge đã được cấp.
- Target duy nhất: `readmeRelated.anchorList` của từng **question/case** trong ba tài liệu Phase 2/3 trên. **Prime ghi lần đầu sau Phase 1; Phase 2 worker trực tiếp refactor và ghi lại đúng tài liệu YAML của loại mình**; Prime cuối STEP chỉ nghiệm thu/cleanup, không merge lại. Các item có thể trỏ **0..N bài Knowledge** thuộc **bất kỳ chương nào trong cùng module**; ID phải là A-child ID thật, không phải category/parent/filepath.
- **Cấm `apiRelated`, `readmeRelated.anchor/file` trong định dạng STEP 6/7.** Không viết nội dung Knowledge, group, câu hỏi, tình huống, đáp án, `resolve`, `knowledge`, `explanation`, Java, Summary, Swagger, Roadmap, Quiz/Interview legacy theo chương hoặc dữ liệu EN.
- `anchorList: []` là hợp lệ nếu kiểm tra và **không tìm thấy quan hệ phù hợp**. Không có chỉ tiêu số lượng link, không ép mỗi chương hay mỗi câu phải có link; không chọn dựa trên trùng từ khóa/chủ đề rộng. **Liên kết ít nhưng đúng quan trọng hơn liên kết nhiều.**
- Ý tưởng `null`/status cho câu hỏi/case **cần bổ sung Knowledge** vẫn **chưa chốt**: STEP 7 hiện tại dùng `REVIEW` trong proposal/báo cáo Prime, **không ghi `null` hay status mới vào YAML**, không tự bổ sung Knowledge. Nếu người dùng chốt schema riêng thì cập nhật contract và validator trước khi dùng.

## 1. Preflight toàn module — Prime ONLY

1. Prime đọc toàn bộ `GENERAL_AGENT_RULES.md`, tất cả file `rules/`, STEP này và **cả hai worker-only** bên dưới. Xác minh tab/gid/I3, module, phạm vi Sheet, metadata ô, formula/note/validation/chipRuns, writer khác và trạng thái STEP. Không dùng `CURRENT_STEP` trong Google Sheet.
2. Từ Menu thật tạo **Knowledge catalog** bất biến: `lessonId`, `chapterBaseId`, `chapterIndex/parentRow`, `childRow`, title, nội dung hoặc chỉ mục đến C:N. Mọi `lessonId` phải duy nhất, không rỗng; đối chiếu nội dung/anchor sau STEP 5. **Không tự bịa ID** và không dùng index thay ID.
3. Parse ba tài liệu STEP 6 thành **Question catalog**: `kind = I/C/Q`, `groupID`, `questionIndex/caseIndex` (1-based **trong group**), fingerprint của `question` hoặc `situation` **cộng các trường ngữ nghĩa liên quan** (`knowledge`, `resolve`, đáp án và `explanation`). Mỗi item có identity `kind|groupID|index` chỉ dùng **trong snapshot có hash**; xác nhận `groupID` duy nhất và chỉ số đầy đủ, không có field lạ, `readmeRelated.anchorList` đúng kiểu array ID string/[] và không có `apiRelated`.
4. Snapshot **exact bytes + SHA-256** của ba YAML ghép từ Sheet và catalog ID/identity, ghi ledger/manifest do Prime sở hữu (có thể đặt trong `<MODULE_ROOT>/build/step7/<RUN_ID>/`, Git ignored). **Phase 1 worker chỉ được ghi một ô staging `A14:AD15`; Phase 2 worker được ghi trực tiếp vùng YAML theo loại đã giao.** Dữ liệu hiện có không trống/hợp lệ phải được bảo toàn, không reset.
5. Lập ledger **mọi chương Knowledge** và **ba loại Interview, Case Study, Quiz**. Phase 1 phải bao phủ toàn bộ chương, Phase 2 phải có **đúng một owner cho toàn bộ mỗi loại**, không chia group. **Prime phải APPLY kết quả Phase 1 vào Sheet và exact readback PASS trước khi giao bất cứ worker Phase 2 nào.** Nếu catalog không đọc/parse được, layout sai, thiếu Knowledge hoặc có sửa đồng thời → `BLOCKER: STEP7_SOURCE_DRIFT`, không ghi đoán.
6. Kiểm tra vùng staging **chính xác `A14:AD15` = 60 ô** (30 cột từ A đến AD trên **mỗi** hàng 14 và 15), gồm giá trị thực/formula/note/validation/chipRuns/merged-cell/ownership. Vùng này phải trống và không bị người khác giữ trước khi chạy. Ngoài `A14:AD15` worker **không** có ô staging dự phòng.

## 2. Chỉ PHASE 1 dùng staging: MỘT ô/nhiệm vụ trong A14:AD15

- Prime độc quyền cấp ô theo **thứ tự hàng trước, cột sau**: `A14, B14, ... AD14, A15, B15, ... AD15`. Với Mode A mặc định 3 worker đầu là **A14/B14/C14**. Mode B dùng số worker N được người dùng cho phép nhưng **tối đa 60 ô staging đồng thời**; nếu hết ô thì chờ Prime thu nhận và giải phóng, không ghi sang AE/row 16. **Một ô chỉ có một worker/nhiệm vụ sở hữu tại một thời điểm.**
- Handoff Phase 1 **đúng bốn dòng**: (1) URL Sheet đúng gid; (2) `READ=PHASE1,UNIT=<CHAPTER_BASE_ID>,KNOWLEDGE=<EXACT_CHAPTER_A_TO_N_ROWS>,QUESTIONS=P20:W24,RUN=<PRIME_TOKEN>`; (3) `WRITE=<ONE_VERIFIED_CELL_IN_A14_AD15>`; (4) đường dẫn `STEP_7_WORKER_PHASE_1.md`. Prime **phải cấp chính xác vùng Knowledge của một chương cùng `P20:W24`**, không chỉ gửi tên chương/catalog suy đoán. Có thể đọc `A1,B1,I3` để xác minh tab; đọc đúng dữ liệu trong phạm vi giao. Không gửi lời giải hay ID gợi ý.
- **Chỉ Phase 1 worker được giao mới ghi đúng một ô staging của mình**, không được ghi Sheet chính hoặc ô khác, không dùng file staging khác. Mỗi ô chứa text thuần, tối đa **30.000 Unicode code points và 32.767 UTF-16 code units**. Không sử dụng MAX-FILL tràn sang ô khác: nếu quá dài, báo `BLOCKER: STEP7_STAGING_CAPACITY`; Prime chia nhỏ nhiệm vụ theo phạm vi phù hợp và cấp lại ô, không truncate.
- Worker chỉ được ghi khi ô trống, không formula/note/chip/validation và Prime đã cấp slot. Prime **đọc lại đúng ô**, kiểm header RUN/UNIT/phase, END/count/identity và lưu **toàn bộ proposal** vào ledger Prime có kiểm chứng trước khi giải phóng. Không được giao lại ô khi worker chưa kết thúc/Prime chưa thu nhận. Để dùng lại, Prime được xóa **chỉ ô đã thu nhận của run này** và exact-readback trống, không đụng format/note/validation khác; không xóa ô của worker còn ACTIVE.
- **Ranh giới hai phase:** khi tất cả Phase 1 hoàn tất, Prime thu đủ proposal, QA/merge và APPLY lần đầu vào cả ba YAML với MAX-FILL/readback PASS. **Chỉ sau đó giao Phase 2**, không phát sinh proposal/slot staging mới cho Phase 2. Sau Phase 2, Prime QA ba vùng đã được worker trực tiếp sửa rồi xóa **toàn bộ dữ liệu tạm còn lại trong A14:AD15 do run tạo** và kiểm tra 60 ô trống. Lỗi QA thì giữ bằng chứng/ledger, báo BLOCKER và giao lại đúng owner để sửa; Prime **không tự merge/ghi thêm Phase 2**.
- Staging không phải nguồn dữ liệu chính/publish. Không tin lời worker trong chat thay cho exact readback. Worker SLEEPING chỉ được giao việc mới sau khi slot giải phóng. Có dữ liệu ngoài run hoặc không chắc ownership → `BLOCKER: STEP7_STAGING_OWNERSHIP`, không xóa đoán.

### Format proposal tối giản, thống nhất

Text theo dòng, không bắt buộc YAML/JSON. ID của **item**: `I|groupID|questionIndex`, `C|groupID|caseIndex`, `Q|groupID|questionIndex`; mọi index bắt đầu từ **1** trong group. Mỗi dòng dữ liệu là:

```text
<KIND>|<GROUP_ID>|<INDEX>|<comma-separated-knowledge-ids OR - OR ?>|<lý do ngắn>
```

- Header chỉ cho Phase 1: `PHASE=1;UNIT=<CHAPTER_BASE_ID>;RUN=<TOKEN>`.
- **Phase 1** (Knowledge → questions): **chỉ các liên kết có cơ sở thật** và `lessonId` bắt buộc thuộc chương được giao. Nếu không có proposal: một dòng `NONE`. Không lập dòng cho câu hỏi không liên quan. `END=<COUNT>` là số dòng proposal, không tính `NONE`.
- **Phase 2 không dùng format proposal này**: worker đọc và refactor YAML trong đúng vùng Interview/Case Study/Quiz đã nhận, ghi `anchorList` trực tiếp với MAX-FILL và exact readback.
- `lý do` rất ngắn, dựa trên cơ chế/nguyên lý cụ thể; không chép nguyên nội dung Knowledge. Prime không coi danh sách ID là bằng chứng ngữ nghĩa nếu không có căn cứ. Cấm delimiter `|` hoặc xuống dòng trong lý do.
- `END=<COUNT>` là dòng cuối, bắt buộc; token RUN từ Prime chống nhận nhầm kết quả của lượt cũ.

Ví dụ Phase 1 (ID chỉ minh họa, **không dùng làm target thật**):

```text
PHASE=1;UNIT=encapsulation;RUN=<TOKEN>
C|2|5|encapsulation-003|Giải thích invariant và ranh giới sửa state
Q|4|8|encapsulation-007|Minh họa mức đóng gói được hỏi
END=2
```

## 3. Hai phase và checkpoint ghi giữa hai phase

**Phase 1 (K2Q): một nhiệm vụ = một chương Knowledge**, không phải một case. Prime giao **READ đúng vùng Knowledge của một chương (A-child ID, B-child title, C:N-child content) + `P20:W24`** để worker đọc toàn bộ YAML I/C/Q, nhận diện `kind|groupID|index`, đối chiếu nội dung và đề xuất vào **một ô staging** được cấp trong `A14:AD15`. Phải đọc đầy đủ bài Knowledge thuộc chương và xem toàn bộ ba loại câu hỏi, không ép một chương liên quan với cả I/C/Q; có thể `NONE`. Prime xử lý **toàn bộ chương**.

**Checkpoint bắt buộc: Prime APPLY PHASE 1.** Sau khi **mọi chương** Phase 1 được thu nhận, Prime gộp theo `kind|groupID|index`, chống duplicate và kiểm chứng từng ID + nội dung Knowledge; không union mù các link yếu. Prime **ghi kết quả đủ căn cứ vào `readmeRelated.anchorList`** của cả I/C/Q trong vùng nguồn STEP 6, rồi **MAX-FILL, exact readback và lưu snapshot/hash mới**. Không cần ép các item chưa có link nhận ID; giữ `[]` khi không có đề xuất phù hợp. Chỉ khi `PHASE1_APPLY=PASS`, nguồn ổn định và các proposal Phase 1 được bảo toàn trong ledger thì Prime mới được giao Phase 2. **Không chuyển Phase 2 bằng bản snapshot trước khi điền.**

**Phase 2 (Q2K): MỘT worker = TOÀN BỘ MỘT LOẠI**, đúng ba nhiệm vụ độc lập, tuyệt đối **không chia theo 5 group hoặc batch**:

| Worker owner | READ + WRITE chính thức | READ bổ sung, bắt buộc READ-ONLY |
| --- | --- | --- |
| Interview | `P20:R24` (toàn bộ `interviewQuestion`) | **Toàn bộ Knowledge** A-child/B-child/C:N-child tất cả chương cùng module |
| Case Study | `S20:U24` (toàn bộ `caseStudy`) | **Toàn bộ Knowledge** A-child/B-child/C:N-child tất cả chương cùng module |
| Quiz | `V20:W24` (toàn bộ `quiz`) | **Toàn bộ Knowledge** A-child/B-child/C:N-child tất cả chương cùng module |

Phase 2 worker **đọc và trực tiếp sửa chính YAML đã được Prime điền ở Phase 1** của trọn loại mình. Recheck mọi item/ID với Knowledge thực, **KEEP/ADD/REMOVE** chỉ khi có căn cứ; không ép link. Nếu thực sự không phù hợp thì giữ/đặt `anchorList: []`; nếu không chắc chắn thì **giữ nguyên và báo `REVIEW` cho Prime**, không tự `null`/status hoặc thay đổi nội dung ngoài related. **Worker tự dựng lại cả chuỗi YAML, MAX-FILL vào đúng vùng WRITE đã giao và exact readback**; không ghi bất cứ proposal/staging Phase 2 nào ở hàng 14/15. Phân quyền cả loại có nghĩa là ba worker **có thể ghi song song**, nhưng vùng WRITE tuyệt đối không chồng lấn, và cả ba chỉ đọc Knowledge.

Prime dùng pool Mode A/B của `PRIME_RULES.md`, reuse SLEEPING, spawn **từng worker một** và ledger `PHASE/UNIT → RUN → OWNED_WRITE → READBACK → QA`. Trước Phase 2, Prime snapshot/hash **sau Phase 1 APPLY**, để mỗi worker có baseline chính xác. Nếu một loại thiếu nội dung hoặc capacity thì xử lý `BLOCKER`, **không tự chia type cho hai worker cùng ghi**.

## 4. Quy trình APPLY và nghiệm thu cuối — ownership riêng

1. **Prime APPLY sau Phase 1, một lần duy nhất:** thu đủ proposal từng chương từ `A14:AD15`, parse và kiểm `RUN/UNIT/END`, đối chiếu `kind|groupID|index` + fingerprint, xác minh ID và nội dung Knowledge, không union mù. Prime re-read nguồn, chống drift/exclusive writer; cập nhật **chỉ `anchorList`** trong ba YAML, ghi MAX-FILL, exact readback và lưu hash/baseline **sau Phase 1**. `PHASE1_APPLY=PASS` là điều kiện bắt buộc để mở Phase 2.
2. **Phase 2 worker tự APPLY đúng vùng WRITE:** ba vùng `P20:R24`, `S20:U24`, `V20:W24` là **ba ownership tách biệt**, mỗi loại một writer. Mỗi worker đọc baseline YAML đã điền và **toàn bộ Knowledge cùng module**, kiểm chứng từng câu/case, tự viết lại `anchorList`, bảo toàn mọi trường khác. Chỉ được thay semantic `readmeRelated.anchorList`; không thêm `apiRelated`, `anchor/file/null`. Phải tự re-read ngay trước WRITE, chống source drift, writer khác và bảo toàn metadata/formula/notes/validation/chips; dùng MAX-FILL đúng giới hạn ≤30.000 code points & ≤32.767 UTF-16 units mỗi ô, không cắt surrogate pair, không separator, xóa giá trị dư ở cuối vùng WRITE khi cần. Không đủ sức chứa → `BLOCKER: STEP7_CELL_CAPACITY`; không partial write.
3. **Phase 2 worker tự exact readback:** từng ô đã ghi và chuỗi ghép, parse, kiểm count/group/index, ID tồn tại/không trùng, semantic diff chỉ `anchorList`; báo `PHASE2_WRITE=PASS/BLOCKER`, số item/link/REVIEW và SHA-256/result. Worker **không** ghi staging hay nguồn loại khác. Một khi bất kỳ item nào còn `REVIEW` cần xử lý, worker bảo toàn field đó và báo Prime rõ vị trí, không tự phát minh liên kết.
4. **Prime sau Phase 2 chỉ CONFIRM:** đợi **đủ ba worker kết thúc** và không writer nào còn hoạt động; đọc lại **ba vùng chính thức**, so baseline Phase 1 và semantic diff chỉ `anchorList`, count/ID, MAX-FILL, xác nhận coverage 100% ba loại, không unresolved `REVIEW`. **Prime không merge proposal, không tự refactor hoặc ghi lại YAML lần hai.** Nếu QA FAIL thì báo blocker, giao lại đúng owner Phase 2 để xử lý, không tuyên bố PASS.
5. **Prime cleanup:** chỉ sau `PHASE2_CONFIRM=PASS`, Prime xóa **toàn bộ dữ liệu staging do run tạo trong `A14:AD15`** và exact readback **60/60 ô trống**, không đụng dữ liệu/format/note/chip/validation của người khác. Sau xác nhận QA tổng và lưu lại kết quả/hash cần thiết, dọn staging/ledger/backup tạm đúng `<RUN_ID>` trong `build/step7/` (không đụng phiên khác). Cleanup lỗi → `BLOCKER: STEP7_TEMP_CLEANUP`; không xóa bằng chứng trước QA thành công.
6. **Không chạy** `normalizeQuizQuestionsFromSheet`, `normalizeInterviewQuestionsFromSheet`, `generateModuleQuizFromSheetByLocation`, `generateModuleInterviewFromSheetByLocation` hoặc importer STEP 10/11 legacy: không hỗ trợ group schema + `anchorList` toàn module. STEP 7 là **Sheet source enrichment**, chưa có importer/module-runtime tương thích, không đánh dấu đã publish Portal.

## 5. Completion gate

- Catalog: toàn bộ bài A-child/Knowledge đúng chapter, identity duy nhất; **100% chương Phase 1**, sau đó **Prime Phase 1 APPLY/readback PASS**, rồi **đủ ba Phase 2 worker**, mỗi worker **toàn bộ một loại**, tự WRITE/QA.
- Staging: chỉ **Phase 1** ghi ô trong `A14:AD15` do Prime cấp, không xung đột, đúng token/UNIT/count/END, readback; Prime thu đủ proposal trước khi dùng lại ô, **cuối STEP 60/60 ô trống**, toàn bộ staging tạm `<RUN_ID>` đã cleanup sau QA cuối.
- QA: không ép số link tối thiểu, mọi link được chọn có bằng chứng liên quan, no-match hợp lệ; không còn `REVIEW` unresolved, ID thiếu, lỗi dạng YAML hay thay đổi bất hợp lệ.
- WRITE: **Prime chỉ APPLY sau Phase 1**; **Phase 2 worker tự WRITE trực tiếp** toàn bộ YAML loại mình, MAX-FILL/readback PASS; **Prime cuối Phase 2 chỉ CONFIRM + CLEANUP**. Không đổi Phase 1/4 của STEP 6, nội dung nghiệp vụ, cột/ô ngoài scope, metadata hay module source.
- **CURRENT_STEP:** vì STEP 6/7 hiện chưa có importer/module validator tương thích, STEP 7 chỉ được báo **`SHEET_ENRICHMENT=PASS` hoặc `BLOCKER`**, **không cập nhật `CURRENT_STEP.VALUE`** và không coi Portal đã đồng bộ. Completion module-level/CURRENT_STEP cần importer/schema + QA riêng khi được triển khai. Không commit/push.

## 6. Handoff worker — đúng bốn dòng

```text
<VERIFIED_SHEET_URL_WITH_GID>
READ=PHASE1,UNIT=<CHAPTER_BASE_ID>,KNOWLEDGE=<EXACT_CHAPTER_A_TO_N_ROWS>,QUESTIONS=P20:W24,RUN=<TOKEN>
WRITE=A14
/java-learning/module-generate-agent/worker/STEP_7_WORKER/STEP_7_WORKER_PHASE_1.md
```

```text
<VERIFIED_SHEET_URL_WITH_GID>
READ=PHASE2,UNIT=INTERVIEW,KNOWLEDGE=<EXACT_FULL_MODULE_A_TO_N_ROWS>,QUESTIONS=P20:R24,RUN=<TOKEN>
WRITE=P20:R24
/java-learning/module-generate-agent/worker/STEP_7_WORKER/STEP_7_WORKER_PHASE_2.md
```

Phase 2 Case Study và Quiz dùng cùng file worker, lần lượt `UNIT=CASE_STUDY, QUESTIONS=S20:U24, WRITE=S20:U24` và `UNIT=QUIZ, QUESTIONS=V20:W24, WRITE=V20:W24`. Prime thay mọi placeholder bằng vùng Knowledge thực (tất cả parent/child của module) đã xác minh; **READ chỉ gồm vùng câu hỏi được giao cùng vùng Knowledge** và token, không đưa proposal Phase 1 vào handoff. Slot A14 trong ví dụ chỉ thuộc Phase 1. **Phase 2 chỉ được giao sau khi Prime Phase 1 APPLY/readback PASS.**
