# STEP 7 — WORKER PHASE 2: Recheck và refactor TRỰC TIẾP toàn bộ một loại (WORKER ONLY)

**Đọc toàn văn file này trước mỗi nhiệm vụ.** Prime giao đúng **bốn dòng**: (1) URL tab đã xác minh; (2) `READ=PHASE2,UNIT=<INTERVIEW|CASE_STUDY|QUIZ>,KNOWLEDGE=<EXACT_FULL_MODULE_A_TO_N_ROWS>,QUESTIONS=<EXACT_OWNED_YAML_RANGE>,RUN=<TOKEN>`; (3) `WRITE=<EXACT_OWNED_YAML_RANGE>`; (4) đường dẫn file này. **Không có ô staging Phase 2.** Nếu READ/WRITE không đúng hoặc chưa xác nhận Prime đã APPLY Phase 1 thì BLOCKER, không ghi.

## 1. Chỉ có ba assignment loại, mỗi assignment một owner

| UNIT | READ QUESTIONS = WRITE (cố định) | READ KNOWLEDGE (chỉ đọc) |
| --- | --- | --- |
| `INTERVIEW` | `P20:R24` | Toàn bộ Knowledge thuộc module |
| `CASE_STUDY` | `S20:U24` | Toàn bộ Knowledge thuộc module |
| `QUIZ` | `V20:W24` | Toàn bộ Knowledge thuộc module |

- `KNOWLEDGE` phải là toàn bộ các dòng parent/chapter + child có A-child ID, B-child title và C:N-child Markdown, **tất cả chương của đúng module được Prime xác minh**; không chỉ một chương hoặc catalog tên bài. Đọc `A1,B1,I3` để kiểm tra module. Nếu một Knowledge ứng viên chưa đủ thông tin, đọc trọn nội dung thật trước khi quyết định; không suy ID hoặc nội dung.
- Đọc **toàn bộ YAML trong vùng QUESTIONS của đúng UNIT**; ghép các ô **trái→phải, trên→dưới, không separator**. Parse đủ mọi `groupID`, mọi `questions[]` / `cases[]` và `readmeRelated.anchorList` đã được **Prime điền giữa hai phase**. Không phân chia 5 group; worker là owner **tất cả group** trong loại.
- **Chỉ đọc vùng QUESTIONS và KNOWLEDGE đã được giao** (ngoài `A1,B1,I3` để xác minh module); không mở rộng sang Phase 1/4 STEP 6. Nếu dữ liệu được cấp không đủ căn cứ thì `REVIEW`, không phỏng đoán. Tuyệt đối không ghi Phase 1/4 của STEP 6, Knowledge, loại khác, `A14:AD15`, file/module, git/Gradle/importer/CURRENT_STEP.
- Có thể chạy song song ba worker vì `P20:R24`, `S20:U24`, `V20:W24` **không giao nhau**. Nếu có writer khác đang giữ một trong các ô của WRITE, hoặc nguồn bị sửa đồng thời, `BLOCKER: STEP7_CONCURRENT_WRITER`.

## 2. Recheck từng item: quyền refactor `anchorList` trực tiếp

1. Với từng câu Interview xét question/knowledge; từng case xét situation/resolve/knowledge; từng Quiz xét question/answers/correctAnswerId/explanations. Kiểm tra mọi ID đã điền trong `anchorList` với **Knowledge A-child thực** trên toàn module, rồi tìm các ứng viên có thể bị bỏ sót ở bất cứ chương nào.
2. Chỉ **KEEP** link nếu bài Knowledge thực sự giải thích/hỗ trợ trực tiếp; **ADD** khi có thêm bài thực sự phù hợp; **REMOVE** link sai/yếu với lý do xác đáng. Không gán link chỉ vì giống từ khóa, cùng lĩnh vực OOP hoặc để tăng độ bao phủ. Một item có thể cần **0, 1 hoặc nhiều ID**. Không ép mỗi chương góp link hoặc mỗi item phải có link.
3. Nếu đã kiểm tra và không có Knowledge phù hợp thì giữ/đặt **`readmeRelated.anchorList: []`**; đây là kết quả hợp lệ, **không đồng nghĩa Knowledge thiếu**. Nếu không chắc hoặc nghi cần bổ sung Knowledge, giữ `anchorList` hiện có cho item đó, ghi rõ identity `kind|groupID|index` trong báo cáo `REVIEW`; **không tạo `null`, status mới, `apiRelated`, `readmeRelated.anchor/file`**.
4. `anchorList` chỉ là list string ID A-child duy nhất trong module, không trùng ID, không category/base ID, không đường dẫn/filename, không ID module khác. Giữ thứ tự ổn định cho link đang hợp lệ; ID mới thêm theo thứ tự xác định. Nếu relation cũ có dữ liệu đáng nghi, không âm thầm mất dữ liệu: REVIEW/BLOCKER và làm rõ trước khi xác nhận PASS.
5. **Semantic diff tuyệt đối chỉ là `readmeRelated.anchorList`**: bảo toàn chính xác mọi `groupID/groupName`, số lượng/thứ tự item, `question/situation`, `knowledge`, `resolve`, `answers`, `correctAnswerId`, `explanation`. Không sửa câu chữ, đáp án, case, code Phase 4 hoặc layout. Worker **chịu trách nhiệm tự refactor nội dung related trong YAML chính thức**, không gửi proposal để Prime merge.

## 3. WRITE nguyên YAML được giao — MAX-FILL như STEP 6

1. Trước WRITE, đọc metadata từng ô WRITE (`userEnteredValue`, formula, note, chipRuns, dataValidation), ghép nội dung và ghi nhận snapshot/hash. Check baseline ngay trước khi ghi: nguồn phải là YAML đã được Prime APPLY Phase 1, không có drift/writer khác. Dữ liệu Phase 1 sẵn có là **nguồn hợp lệ để cập nhật**, không phải blocker chỉ vì ô không trống.
2. Dựng **toàn bộ chuỗi YAML sau refactor** và parse thử trước ghi; so semantic với snapshot, chỉ `anchorList` thay đổi. Chia theo thứ tự **hàng trước, cột sau** trong đúng WRITE bằng **MAX-FILL**: mỗi ô lấy tiền tố **dài nhất** thỏa đồng thời ≤30.000 Unicode code points và ≤32.767 UTF-16 code units. Chỉ chuyển ô khi ký tự tiếp theo không vừa; không chia theo dòng/group/câu, không thêm separator, không cắt surrogate pair. Các ô đuôi không sử dụng phải để trống. Không đủ chỗ → `BLOCKER: STEP7_CELL_CAPACITY`, không ghi thiếu/tràn sang loại khác.
3. Ghi lại **đúng toàn vùng WRITE** với giá trị thuần văn bản; không đổi formatting, note, validation, chip hay metadata ngoài `userEnteredValue`. Bảo đảm chỉ một writer/loại, chống sửa đồng thời và giữ snapshot trước khi APPLY để phục hồi nếu lỗi. Không ghi `A14:AD15` ở Phase 2.
4. Sau WRITE, **tự đọc lại mọi ô** trong WRITE, so từng ô + chuỗi ghép; parse lại YAML, kiểm đủ group/item, valid A-child ID, danh sách không trùng, semantic diff chỉ `anchorList`, MAX-FILL chính xác. Không chỉ tin vào response của API. Nếu sai/ghi thiếu → `BLOCKER`, không báo hoàn thành.

## 4. Bàn giao Prime để CONFIRM (không merge lần hai)

Báo `PHASE=2`, `UNIT`, `READ=KNOWLEDGE+QUESTIONS`, `WRITE_RANGE`, `GROUP_COUNT`, `ITEM_COUNT`, `KEEP/ADD/REMOVE/NO_MATCH`, `REVIEW`, `SEMANTIC_DIFF`, `MAX_FILL`, `READBACK=PASS/BLOCKER` và `SOURCE_HASH/RESULT_HASH`. Với REMOVE hoặc REVIEW nêu identity và căn cứ ngắn, không chép đầy đủ YAML.

**Worker tự ghi và tự kiểm toàn bộ loại.** Prime chỉ xác nhận kết quả của đủ ba loại và xóa staging Phase 1 khi tất cả QA PASS; worker không yêu cầu Prime copy/merge/refactor proposal Phase 2. Sau báo cáo, trở về SLEEPING, không tự nhận loại khác hoặc ghi thêm vùng chung.
