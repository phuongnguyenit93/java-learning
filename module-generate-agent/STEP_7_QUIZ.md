# Step 7 - Quiz Rules

Step 7 xây hoặc refactor Quiz từ curriculum đã ổn định. Step number này **giống nhau cho new và refactor module**.

### General Agent Rules context guard

Trước khi thực hiện Step 7:

```text
Nếu context của GENERAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENERAL_AGENT_RULES.md trước khi tiếp tục
```

Không suy đoán các orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

Canonical source:

```text
module/.../src/main/resources/quiz/{lang}/question.yml
```

---

## Step 7 — Build Quiz from the established curriculum

Quiz is authored after Knowledge and API relationships are stable enough to serve as references.

Canonical workflow now runs Step 6 Video before Quiz. Video may help the learner rehearse the same concepts, but Quiz correctness and relations must still resolve against canonical Knowledge/API sources rather than presentation wording from the Video script.

Quiz should test understanding rather than repeat sentences from the README.

Aim for a useful mix such as:

```text
core concept recognition
runtime behavior
cause/effect reasoning
common misconception
comparison
edge case
pitfall
practical choice
API experiment observation when relevant
```

Avoid multiple questions that test the same distinction with superficial wording changes.

## Distractor quality và answer-leakage prevention

Một Quiz technically đúng vẫn có thể có assessment quality thấp nếu learner đoán được đáp án đúng từ **cách viết option** thay vì hiểu concept.

Đáp án đúng không được nổi bật chỉ vì:

```text
dài hơn rõ rệt so với các option còn lại
→ là option duy nhất giải thích đầy đủ / có nhiều technical detail
→ là option duy nhất dùng wording cân bằng, có điều kiện hoặc ít tuyệt đối hơn
→ có grammar / sentence structure khác hẳn các distractor
→ gần như copy nguyên wording từ Knowledge trong khi các option khác không có mức chi tiết tương đương
→ chứa thuật ngữ chính xác duy nhất còn distractor dùng các phát biểu hiển nhiên vô lý
```

Không áp dụng rule máy móc kiểu "mọi option phải dài bằng nhau". Mục tiêu thực sự là:

```text
correct answer must not be detectable
from length / style / wording / detail level alone
```

### Distractor phải plausible

Distractor tốt là đáp án **sai nhưng hợp lý ở lần đọc đầu**, thường phản ánh một misconception thật mà learner có thể mắc.

Ưu tiên distractor dựa trên:

```text
common beginner misconception
nearby concept bị nhầm lẫn
đúng trong một context khác nhưng sai trong context của câu hỏi
đảo cause/effect
nhầm compile-time với runtime
nhầm value với reference
nhầm expression với statement
nhầm language rule với implementation detail
```

Tránh distractor kiểu:

```text
vô lý ngay từ kiến thức Java cơ bản
không cùng conceptual domain với câu hỏi
cố tình cực đoan chỉ để learner loại nhanh
khác grammar/style quá rõ so với đáp án đúng
chứa từ khóa như "luôn luôn", "hoàn toàn", "không bao giờ" chỉ để tạo option sai dễ nhận biết
```

### Option symmetry

Các option trong cùng một câu nên có mức độ tương đồng hợp lý về:

```text
độ dài tương đối
sentence structure
mức technical detail
terminology level
scope của phát biểu
```

Không cần giống nhau tuyệt đối, nhưng không được để một option duy nhất trông như "câu trả lời của giáo viên" còn các option khác trông như filler.

### Blind-answer quality check

Trước khi chấp nhận một câu Quiz, thực hiện một lần review với `correctAnswerId` bị che đi và hỏi:

```text
Nếu không biết concept này,
learner có thể đoán đáp án chỉ từ wording / length / style không?
```

Nếu **YES**:

```text
rewrite distractors
hoặc
rewrite toàn bộ option set
```

cho đến khi learner phải dùng **domain knowledge / reasoning** để chọn đáp án.

### Misconception coverage > filler coverage

Nếu không tìm được ba distractor plausible cho một câu single-choice, ưu tiên:

```text
đổi cách hỏi
thu hẹp scope câu hỏi
chọn một misconception khác
hoặc bỏ câu hỏi
```

Không thêm distractor filler chỉ để đủ A/B/C/D.

Follow the canonical Quiz schema. Current important rules include:

```text
single-choice
exactly four stable internal answers A/B/C/D
correctAnswerId stored separately
every answer has answer + explanation
aiGenerated / reviewed governance
optional exact readmeRelated relation
optional exact apiRelated relation
```

Relations are optional, not quotas.

Use:

```yaml
readmeRelated:
  file: ''
  anchor: ''

apiRelated:
  controller: ''
  methodSignature: ''
```

when there is no exact target.

Do not create a new API solely so a Quiz question can have `apiRelated`. Do not create a meaningless Knowledge section solely so a Quiz question can have `readmeRelated`.

VI/EN variants should preserve conceptual parity and question order unless the task explicitly defines a different localization policy. Natural wording is preferred over literal translation, but the tested concept and correct answer must remain equivalent.

---

## Completion gate

Step 7 hoàn thành khi:

```text
[ ] Quiz đúng canonical schema
[ ] Không lặp coverage vô nghĩa
[ ] Relation là exact hoặc intentionally blank
[ ] Câu hỏi kiểm tra understanding thay vì chép lại README
[ ] Correct answer không bị lộ bởi length / wording / style / detail level
[ ] Distractor plausible và phản ánh misconception thực tế khi có thể
[ ] Các option có mức độ symmetry hợp lý
[ ] Blind-answer quality check đã được thực hiện
[ ] Không có distractor filler chỉ để đủ A/B/C/D
```
