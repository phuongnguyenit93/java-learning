# STEP_OPTIONAL_VIDEO_TRANSLATE — WORKER ONLY: Translate exactly one EN Video YAML

**Chỉ đọc file worker-only này và `EN_TARGET` do Prime giao.** Không đọc source VI, GENERAL, STEP Prime, rules, Sheet, module khác hay Git. Mỗi lần giao **một file EN Video** và SHA-256 expected. Worker tự làm trọn một file, readback, rồi báo SLEEPING.

1. Xác minh file EN_TARGET thuộc `content/video/en/menu/<chapter>/video-script.yml`; kiểm tra SHA-256 đúng Prime giao và không có writer khác. Khác → BLOCKER, không ghi.
2. Toàn bộ nội dung ban đầu đã được Prime seed từ Video VI nếu cần. **Dịch chính xác các trường văn bản được phép**: `title`, `sections[].title`, `sections[].items[].title` nếu hiện hữu, `visual`, `script`, `purpose`. Không bỏ scene/transition, không cắt ngắn lời thoại, không thêm nội dung mới, không rút gọn hoặc sáng tác.
3. **Giữ bất biến** YAML key/type/list order, `video.url`, `type`, `time`, số section/item, code fence, Java code/commands/output, URLs, placeholder, HTML tags/attributes và data kỹ thuật. Không biến đoạn Java trong `visual` thành bản dịch sai code. Dịch tự nhiên cho voiceover và mô tả hình, tuyệt đối không chép README.
4. Chỉ ghi `EN_TARGET` và không ghi file VI, bất kỳ Sheet cell, metadata/related, config, test, report file, backup hay nơi khác. Kiểm tra file trước WRITE và hash; nếu concurrent edit hoặc EN đã dịch trước đó thì không overwrite mù: giữ nguyên, QA và báo `UNCHANGED` hoặc `REVIEW`.
5. Parse lại YAML, kiểm tra tất cả section/scene, timeline, code fence và Unicode; so số lượng và type/time trước–sau; exact readback file sau ghi. Nội dung EN phải đủ ý, đúng chuyên môn và dễ nói. Không tự sửa lỗi kỹ thuật nguồn hoặc câu hỏi/đáp án liên quan.
6. Báo: `STATUS=COMPLETED/UNCHANGED/REVIEW/BLOCKER`, `EN_TARGET`, `SHA_BEFORE/AFTER`, `SCENES`, `TRANSITIONS`, `TRANSLATED_FIELDS`, `PRESERVED_FIELDS`, `YAML_PARSE`, `READBACK`, `QA`, `BLOCKERS`. Sau đó SLEEPING và giải phóng ownership. Prime nghiệm thu toàn module độc lập.
