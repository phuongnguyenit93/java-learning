# STEP 6 — Prime: Interview, Case Study, Quiz (4 Phase)

## 1. Sơ đồ vùng ô — hợp đồng hiện hành

| Loại | Phase 1 — thô, 2 hàng | Phase 2 + Phase 3 — dùng CHUNG 5 hàng | Phase 4 — 3 hàng/group |
| --- | --- | --- | --- |
| Interview | `P25:R26` | `P20:R24` | `P:R`, bắt đầu hàng 27 |
| Case Study | `S25:U26` | `S20:U24` | `S:U`, bắt đầu hàng 27 |
| Quiz | `V25:W26` | `V20:W24` | Không áp dụng |

- **Phase 1:** ba worker (một worker/loại) tạo nội dung thô từ `A1,B1`, không nhóm/YAML/giải thích. Quiz gồm câu hỏi, bốn đáp án A/B/C/D và một đáp án đúng. Hai hàng Phase 1 đã được dời xuống 25–26.
- **Phase 2:** ba worker (một worker/loại) đọc Phase 1 cùng loại, phân group và tạo **một tài liệu YAML khung đầy đủ các group** vào vùng năm hàng ở bảng. Không viết thông tin của Phase 3/4. Interview đặt `knowledge: ""`; Case Study đặt `resolve: ""`, `knowledge: ""`; Quiz đặt `explanation: ""` cho cả bốn lựa chọn.
- **Phase 3:** ba worker (một worker/loại) **đọc và cập nhật chính tài liệu YAML Phase 2 ở đúng vùng năm hàng đó**. Interview chỉ bổ sung `knowledge`; Case Study chỉ bổ sung `resolve` và `knowledge`; Quiz chỉ bổ sung `answers[].explanation` cho A/B/C/D. **Không tạo YAML riêng cho group, không sử dụng các hàng 27+ cho Phase 3**. Giữ nguyên `groupID`, `groupName`, thứ tự, câu hỏi/tình huống, lựa chọn và `correctAnswerId`. Prime kiểm tra semantic diff chỉ có các field được giao.
- **Phase 4:** chỉ Interview (`answer`) và Case Study (`code`); **mỗi worker một loại × một group**, mỗi group một tài liệu YAML riêng trong **3 hàng × 3 cột**. Không sửa khung Phase 2/3. Quiz hoàn tất sau Phase 3.

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

STEP 6 hiện chưa có importer tương thích. Không chạy task Interview/Quiz P:S/T:W legacy, không cập nhật `CURRENT_STEP.VALUE` và không commit/push.
