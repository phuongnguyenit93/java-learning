# STEP 8 — WORKER ONLY: Một chương, một Video Script YAML

> Chỉ dành cho worker. Prime điều phối trong STEP_8.md; bạn chỉ đọc TOÀN VĂN file này. Prime gửi link tab, tọa độ nguồn và **vùng P:S tại đúng hàng Menu Cấp 1** cùng đường dẫn file worker. Không chia hai pha.

## PHẦN RIÊNG DÀNH CHO WORKER

**Một lượt công việc trọn vẹn, một chương, một Video YAML trên cùng hàng Menu Cấp 1.** Không đọc STEP khác, GENERAL, rules/MD/TXT hoặc chat cũ làm nội dung. Không tạo file hoặc sửa repository.

### W1 — Nguồn và quyền sở hữu

1. Xác minh URL tab/gid do Prime giao. Đọc trực tiếp **chỉ** `A1,B1,A<PARENT>,B<PARENT>`, các `B<child>` và toàn bộ `C:N` trên đúng hàng con được giao. Ghép nguyên văn các ô Knowledge C→N **không separator**; đọc đủ mọi bài.
2. `A<PARENT>` là tên Menu Cấp 1 / `title` YAML. `B<PARENT>` phải là **filename Knowledge `.md` hợp lệ** đã hoàn tất STEP 5; không tự đổi thành `.yml`. Mỗi `B<child>` là một `sections[].title` đúng nguyên văn và đúng thứ tự. Tên file thực tế khi nhập module được code suy ra từ B cha: `<ordinal>.<stem>/video-script.yml`. Worker **không đặt path** và không ghi tên file trong YAML.
3. Chỉ ghi `P<PARENT_ROW>:S<PARENT_ROW>` của hàng cha. Không chọn số hàng từ chỉ số chương, không viết các hàng con, không sửa A:O, T:W, J3, Template, tab khác hoặc repo.
4. Nếu Knowledge thiếu/truncated, Menu cha sai, filename B cha chưa QA, header `P19:S19 = Video Script` không đúng theo Prime hoặc vùng ghi không trống: báo BLOCKER, không đoán.

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

1. Đọc toàn bộ Knowledge, soạn đầy đủ một YAML cho cả chương rồi kiểm tra syntax, `title`, các section, Scene/Transition, field và timeline. Không chia pha hoặc chờ duyệt số Scene.
2. Đếm ký tự Unicode toàn chuỗi; chia **các đoạn liên tục không đổi ký tự**, mỗi đoạn **≤30.000 Unicode code points**, vào P→Q→R→S trên **cùng hàng cha**. Nối lại **không separator** phải y nguyên 100% Unicode, whitespace và newline; không cắt đôi surrogate pair.
3. Nếu hơn bốn ô: `BLOCKER: VIDEO CAPACITY`, nêu tổng số Unicode và số ô cần; không ghi một phần hoặc tự mở cột T trở đi.
4. Trước bất kỳ write, đọc lại `A<PARENT>`, `B<PARENT>` và toàn bộ P:S để kiểm tra `userEnteredValue/formula`, `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns`. Nếu có dữ liệu hoặc nguồn đổi, báo `BLOCKER: TARGET OCCUPIED / SOURCE CHANGED`. Header P19:S19 do Prime kiểm tra; worker không tự đọc ô ngoài phạm vi.
5. Ghi chỉ fragment vào P:S hàng cha; không đổi định dạng/notes/validation. Nếu write lỗi hoặc không rõ kết quả, kiểm tra thực tế, không retry ghi đè.
6. Readback **từng ô** P:S, nối không separator và so khớp chính xác với YAML gốc. QA độc lập thêm một lần sau readback, gồm độ đúng sư phạm, **code Java/API/đầu ra và Markdown nhúng như README**, tính liên tục thời gian, YAML và tính tự nhiên của lời thoại. Chỉ `COMPLETED` nếu cả READBACK và QA PASS.
7. Nếu lỗi/partial write/mismatch, báo đúng tình trạng/ô và BLOCKER, không sửa dữ liệu lạ.

### W4 — Báo cáo và SLEEPING

Gửi Prime đúng một báo cáo: `STATUS=COMPLETED/BLOCKER/FAILED`, tab/gid, `PARENT_CELL_A`, `PARENT_FILENAME_CELL_B`, `CHILD_ROWS`, `SCRIPT_RANGE_P_TO_S_PARENT`, `READ_COVERAGE`, `SECTIONS`, `SCENES`, `TRANSITIONS`, `TOTAL_UNICODE_CHARS`, `FILLED_CELLS`, `YAML_PARSE=PASS/FAIL`, `READBACK=PASS/FAIL`, `CHAPTER_QA=PASS/FAIL`, `PRESERVATION`, `BLOCKERS`.

**Một lần giao, một chương, làm trọn rồi SLEEPING.** Không nhận chương khác, không spawn, không sửa source/commit hoặc J3. Prime tự QA độc lập.
