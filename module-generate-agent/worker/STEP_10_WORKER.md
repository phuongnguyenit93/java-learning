# STEP 10 — WORKER ONLY: Một chương, một Video Script YAML

> Chỉ dành cho worker. Prime điều phối trong STEP_10.md; bạn chỉ đọc TOÀN VĂN file này. Prime gửi link tab, tọa độ nguồn và **vùng X:AA tại đúng hàng Menu Cấp 1** cùng đường dẫn file worker. Không chia hai pha.

**FILL-ONLY VỀ NỘI DUNG — CHO PHÉP FORMAT_FIX:** Đọc/preflight toàn `X<PARENT_ROW>:AA<PARENT_ROW>`. Trống hết → viết Video YAML mới. Đã có script → **không thay title/sections/scenes, lời thoại, visual, purpose, timeline, code hoặc metadata** nếu không có chỉ thị đặc biệt tường minh từ người dùng; chỉ được sửa tối thiểu cú pháp/định dạng YAML và Markdown nhúng khi có lỗi thực và chứng minh không thay đổi nội dung. YAML hợp lệ → `UNCHANGED`, không ghi lại; lỗi không thể sửa thuần định dạng → BLOCKER.

## PHẦN RIÊNG DÀNH CHO WORKER

**Một lượt công việc trọn vẹn, một chương, một Video YAML trên cùng hàng Menu Cấp 1.** Không đọc STEP khác, GENERAL, rules/MD/TXT hoặc chat cũ làm nội dung. Không tạo file hoặc sửa repository.

### W1 — Nguồn và quyền sở hữu

1. Xác minh URL tab/gid do Prime giao. Đọc trực tiếp **chỉ** `A1,B1,A<PARENT>,B<PARENT>`, các `B<child>` và toàn bộ `C:N` trên đúng hàng con được giao. Ghép nguyên văn các ô Knowledge C→N **không separator**; đọc đủ mọi bài.
2. `A<PARENT>` là tên Menu Cấp 1 / `title` YAML. `B<PARENT>` phải là **filename Knowledge `.md` hợp lệ** đã hoàn tất STEP 5; không tự đổi thành `.yml`. Mỗi `B<child>` là một `sections[].title` đúng nguyên văn và đúng thứ tự. Tên file thực tế khi nhập module được code suy ra từ B cha: `<ordinal>.<stem>/video-script.yml`. Worker **không đặt path** và không ghi tên file trong YAML.
3. Chỉ ghi `X<PARENT_ROW>:AA<PARENT_ROW>` của hàng cha. Không chọn số hàng từ chỉ số chương, không viết các hàng con, không sửa A:W, J3, Template, tab khác hoặc repo.
4. Nếu Knowledge thiếu/truncated, Menu cha sai, filename B cha chưa QA hoặc header `X19:AA19 = Video Script` không đúng theo Prime: báo BLOCKER, không đoán. Vùng có script sẵn chỉ được đánh giá `UNCHANGED` hoặc sửa đúng lỗi `FORMAT_FIX` theo quy tắc đầu file, không sinh lại.

### W2 — Chuẩn Video YAML phải tạo

Tạo **một chuỗi YAML hoàn chỉnh, parse được bằng YAML parser**. Nó sẽ tương ứng với `project-build/skeleton/video/video-script.yml` khi nhập module, gồm các field duy nhất `title`, `video`, `sections` ở gốc:

```yaml
title: "Tư duy OOP"
video:
  url: ""
sections:
  - title: "OOP là gì?"
    items:
      - type: SCENE
        title: "Mở đầu"
        time: "00:00–00:35"
        visual: |-
          Hiển thị sơ đồ đối tượng và liên kết giữa chúng.
        script: |-
          Bắt đầu từ một tình huống thực tế để giải thích mô hình OOP.
        purpose: |-
          Xây dựng trực giác cho người học.
  - title: "Các nguyên lý OOP"
    items:
      - type: TRANSITION
        time: "00:35–00:45"
        visual: |-
          Chuyển từ sơ đồ tổng quát sang bốn khái niệm chính.
        script: |-
          Sau khi hiểu đối tượng, ta cùng xem các nguyên lý tổ chức chúng.
        purpose: |-
          Nối nội dung giữa hai phần.
      - type: SCENE
        title: "Ví dụ"
        time: "00:45–01:20"
        visual: |-
          Cho thấy ví dụ code, đầu vào và kết quả khi phù hợp.
        script: |-
          Giải thích từng nguyên lý qua hành vi quan sát được.
        purpose: |-
          Giúp người xem vận dụng khái niệm.
```

Ví dụ chỉ minh họa cấu trúc: thay toàn bộ tên, lời thoại, ví dụ và thời gian bằng nội dung thật của chương. Không dùng bản minh họa này như script thực tế.

- `title` đúng tuyệt đối `A<PARENT>`; **mỗi B con đúng một section** theo đúng thứ tự, không thêm/bớt.
- Mỗi section có `items` gồm ≥1 `SCENE`; từ section thứ hai **item đầu tiên phải là `TRANSITION`**, tiếp theo có ≥1 SCENE. Không áp đặt số Scene nếu Knowledge cần nhiều hơn.
- Mỗi item có `type` (SCENE hoặc TRANSITION), `time`, `visual`, `script`, `purpose`; `title` tùy chọn cho Scene. Không sử dụng field `order`, `categoryId`, `moduleId`, `sourcePath` hoặc `language` trong YAML nguồn; code sẽ tính.
- Thời gian `MM:SS–MM:SS`: bắt đầu `00:00`, nối liên tục giữa mọi item/section, mỗi đoạn có độ dài dương; không trùng hoặc nhảy thời gian. Đặt trong **dấu ngoặc kép** để giữ nguyên kiểu String.
- Dùng `|-` cho narration nhiều dòng; giữ thụt lề YAML đúng để chứa Markdown, code fence, tiếng Việt và dòng trống. Nếu chuỗi có dấu `:` hoặc ký tự YAML đặc biệt, dùng ngoặc kép hoặc block `|-` để tránh parser diễn giải sai.
- `video.url: ""` khi chưa xuất bản, không bịa URL. Không thêm metadata Knowledge/API trong file này.
- **Script** là lời thoại nói tự nhiên, không đọc nguyên Knowledge; **Visual** mô tả hình/thao tác/dữ liệu có thể quay; **Purpose** nêu mục tiêu sư phạm. Không bịa hành vi Java/runtime/API; không để TODO, placeholder, field rỗng. Không xuất YAML sang module; chỉ ghi Sheet.

**QA chất lượng tương đương README nhưng giữ đúng bản chất Video:** với mọi Java code, lệnh, API, thuật ngữ, dữ liệu mẫu hoặc stdout được nhắc trong `script`/`visual`, tự kiểm tra tính đúng theo phiên bản/ngữ cảnh, tên class/method, cơ chế và kết quả thực tế; phân biệt code giả lập với chương trình runnable. Giải thích có nguyên nhân, điều kiện, giới hạn và tình huống áp dụng khi hữu ích. Với Markdown/code fence được nhúng trong YAML block `|-`, giữ cú pháp và thụt lề hợp lệ, code fence cân bằng. **Không** biến lời thoại thành README chép lại, không ép heading/bảng/code cho mọi Scene; `visual` là những thứ có thể hiện lên màn hình, `script` là lời người xem nghe được.

### W3 — Soạn → kiểm tra → ghi → đọc lại

1. Đọc toàn bộ Knowledge. **Chỉ `NEW_FILL` mới soạn Video YAML cả chương**; script có sẵn chỉ QA `UNCHANGED/FORMAT_FIX`, tuyệt đối không tái sinh Scene/Transition. Kiểm tra syntax, `title`, sections, Scene/Transition, fields và timeline; không chia pha hoặc chờ duyệt số Scene.
2. Đếm ký tự Unicode toàn chuỗi; chia **các đoạn liên tục không đổi ký tự**, mỗi đoạn **≤30.000 Unicode code points**, vào X→Y→Z→AA trên **cùng hàng cha**. Nối lại **không separator** phải y nguyên 100% Unicode, whitespace và newline; không cắt đôi surrogate pair.
3. Nếu hơn bốn ô: `BLOCKER: VIDEO CAPACITY`, nêu tổng số Unicode và số ô cần; không ghi một phần hoặc tự mở rộng ra ngoài X:AA.
4. Preflight **toàn vùng X:AA trước khi soạn** và ngay trước WRITE cùng với `A<PARENT>`, `B<PARENT>`; kiểm tra `userEnteredValue/formula`, `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns` và ownership. **Trống hết** → `NEW_FILL`. **Có script** → snapshot ghép X→AA, QA để kết luận `UNCHANGED` hoặc phát hiện lỗi định dạng cụ thể cho `FORMAT_FIX`. Không thêm Scene/Transition vào script cũ, không fill nội dung thiếu; source đổi, formula/chip, xung đột ownership → BLOCKER. Header do Prime kiểm tra; worker không đọc ô ngoài phạm vi.
5. Với `FORMAT_FIX`, chỉ chỉnh ký tự YAML/Markdown nhúng cần thiết, **không đổi title/sections/items, type, time, visual, script, purpose, URL, thứ tự/số lượng hoặc code**. Nếu YAML parse được, so toàn bộ object trước–sau; nếu parser lỗi, đối chiếu thủ công từng scalar với bản gốc. Không chứng minh bảo toàn nội dung → `BLOCKER: FORMAT_FIX_NOT_PROVABLY_CONTENT_PRESERVING`, không ghi. `NEW_FILL` hoặc `FORMAT_FIX` đã được preflight lại → chỉ ghi fragment vào X:AA, không đổi format/notes/validation; không retry mù.
6. Readback **từng ô** X:AA, nối không separator và so khớp chính xác với bản YAML cuối đã QA. QA thêm lần nữa gồm schema, **code Java/API/đầu ra và Markdown nhúng như README**, timeline, lời thoại và kiểm tra `FORMAT_DIFF/CONTENT_PRESERVED` khi sửa. `COMPLETED` chỉ khi READBACK + QA PASS; `UNCHANGED` chỉ khi YAML cũ đạt chuẩn và không write.
7. Nếu lỗi/partial write/mismatch, báo đúng tình trạng/ô và BLOCKER, không sửa dữ liệu lạ.

### W4 — Báo cáo và SLEEPING

Gửi Prime đúng một báo cáo: `STATUS=COMPLETED/UNCHANGED/BLOCKER/FAILED`, `MODE=NEW_FILL/FORMAT_FIX/UNCHANGED`, tab/gid, `PARENT_CELL_A`, `PARENT_FILENAME_CELL_B`, `CHILD_ROWS`, `SCRIPT_RANGE_X_TO_AA_PARENT`, `READ_COVERAGE`, `SECTIONS`, `SCENES`, `TRANSITIONS`, `TOTAL_UNICODE_CHARS`, `FILLED_CELLS`, `YAML_PARSE=PASS/FAIL`, `READBACK=PASS/FAIL`, `CHAPTER_QA=PASS/FAIL`, `FORMAT_DIFF`, `CONTENT_PRESERVED=PASS/FAIL`, `PRESERVATION`, `BLOCKERS`.

**Một lần giao, một chương, làm trọn rồi SLEEPING.** Không nhận chương khác, không spawn, không sửa source/commit hoặc J3. Prime tự QA độc lập.
