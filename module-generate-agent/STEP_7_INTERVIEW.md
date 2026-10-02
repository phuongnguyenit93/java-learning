# Step 7 - Interview Rules

Step 7 xây hoặc refactor Interview từ cùng Knowledge/API map đã ổn định. Step number này **giống nhau cho new và refactor module**.

### General Agent Rules context guard

Trước khi thực hiện Step 7:

```text
Nếu context của GENARAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENARAL_AGENT_RULES.md trước khi tiếp tục
```

Không suy đoán các orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

Canonical source:

```text
module/.../src/main/resources/interview/{lang}/question.yml
```

---

## Step 7 — Build Interview as explanation and reasoning practice

Interview is not a prose copy of Quiz.

It should exercise the learner's ability to explain and reason about the module:

```text
mental models
why / when
comparison
trade-offs
failure modes
pitfalls
framework behavior
design implications
production considerations
common interview questions
```

Example distinction:

```text
Quiz
→ Which behavior occurs when X happens?

Interview
→ Explain why X behaves that way, how it differs from Y,
  and when the distinction matters.
```

Follow the canonical Interview schema and preserve human-owned content. Interview items may use the same optional exact `readmeRelated` and `apiRelated` shapes as Quiz.

Do not invent relations for the sake of completeness. If a question spans several concepts or has no single exact API experiment, a blank relation is valid.

VI/EN content should preserve topic/order parity where the module maintains bilingual content.

---

## Completion gate

Step 7 hoàn thành khi Interview bổ sung explanation/reasoning depth, không chỉ đổi Quiz thành câu hỏi tự luận, và mọi optional relation đều exact hoặc intentionally blank.
