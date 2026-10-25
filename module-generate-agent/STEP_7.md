# STEP 7 — PRIME ONLY: Biên soạn Swagger VI/EN theo chương, staging riêng và merge an toàn

**CURRENT_STEP policy (2026-10-25):** Progress is owned ONLY by `CURRENT_STEP.VALUE` (string) in the verified module `master.json`, never by any Google Sheet/Excel cell. Prime updates after full QA using `module-generate-agent/CURRENT_STEP.md`; Worker does not write it. Any old cell, label, formula, or Sheet-readback instructions about progress below are superseded. The STEP-specific completion gates still apply.


## 0. Mục tiêu, phạm vi và điều kiện đầu vào

STEP 7 được thực hiện **sau STEP 6**: Java API/test của từng chương đã nghiệm thu, và Prime STEP 6 đã chạy `generateApiSwaggerDescription` để có skeleton Swagger kỹ thuật trong **đúng module được khai báo tại ô I3**.

- **Một worker = một Menu Cấp 1 = một chapter package** (source Java + test). Worker đọc toàn bộ Knowledge C:N của các Menu Cấp 2 cùng chương, đọc source/test **chỉ bên trong package đó**, và biên soạn Swagger VI + EN trong thư mục staging riêng. Không thêm endpoint/Java code để hợp thức hóa documentation.
- **Prime** định vị module qua I3, xác định ownership, tạo root staging, chuẩn bị snapshot nguồn **chỉ thuộc Controller của chương**, điều phối worker, nghiệm thu, merge có kiểm soát vào Swagger chính, xóa staging **chỉ khi đã merge + exact readback PASS**, rồi cập nhật trạng thái `CURRENT_STEP.VALUE = "7".
- Không ghi Knowledge lên Sheet, không gọi Sheets importer/ByFiles, không chạy lại `generateApiSwaggerDescription` trong khi worker đang biên soạn; không điền `readmeRelated`, API↔Knowledge/Quiz/Interview mapping hoặc video metadata (STEP khác). Không chỉnh source Java hay Swagger ở module khác.

## 1. Prime preflight — bắt buộc trước khi giao worker

1. Đọc đầy đủ `GENERAL_AGENT_RULES.md`, **toàn bộ file hiện có trong `module-generate-agent/rules/`**, file Prime này và `worker/STEP_7_WORKER.md`, cùng `module-generate-info.txt`. Dùng pool Mode A (3 worker ACTIVE+SLEEPING mặc định) hoặc Mode B N theo session; ưu tiên worker SLEEPING, chỉ spawn từng worker một như `rules/PRIME_RULES.md`.
2. Tra mapping tên module → workbook URL/gid từ TXT. Đọc `A1`, `B1`, `I3`, `CURRENT_STEP:CURRENT_STEP.VALUE` của tab; xác minh tên tab, I3 là **đường dẫn module tương đối hoặc tuyệt đối đã được chuẩn hóa nằm trong repo `java-learning/module/`**, có `src/main/java`, `src/test/java` (nếu dùng), `src/main/resources/swagger/vi` và `/en`; không đoán module theo tên tab. **Không theo symbolic link, `..`, junction hoặc đường dẫn vượt module**. Thiếu/sai → `BLOCKER: MODULE I3 INVALID`.
3. Đọc `A19:B<last>` để lập sổ **TOÀN BỘ** Menu Cấp 1, không chỉ nhóm worker đầu tiên. Với từng chương: `A<PARENT>` là tên/chủ đề; `B<PARENT>` là filename Markdown STEP 5; Menu Cấp 2 là `B<CHILD_ROWS>`; nội dung Knowledge là `C<CHILD_ROWS>:N<CHILD_ROWS>`. Chỉ định **đúng hàng con** đến trước hàng cha kế tiếp, không nuốt sang chương khác. Kiểm tra tất cả Knowledge đầy đủ, không chỉ tiêu đề; thiếu → BLOCKER.
4. Đối chiếu `B<PARENT>` với chapter package tiếng Anh do STEP 5/6 tạo. Xác minh cặp `<I3>/src/main/java/<base-package>/<chapter_package>/` và `<I3>/src/test/java/<base-package>/<chapter_package>/`. **Package không được là root chung**; không cấp quyền đọc source của chapter khác. Worker **không được quét toàn bộ repository**, dù root workspace là java-learning.
5. Prime (không phải worker) kiểm tra source Controller thật **trong package của chương**; từ `swagger/{vi,en}/api-descriptions.yml` đọc trường kỹ thuật `path` để tạo mapping **`PARENT_ROW → chapter_package → ControllerName → methodSignatures`**. Không suy đoán Controller từ tiêu đề hay thứ tự sheet. Nếu một chapter có nhiều Controller, cấp đủ các Controller cùng chapter cho **một worker**, không giao một Controller cho hai worker; nếu một Controller/route dùng chung nhiều chapter, báo `BLOCKER: CROSS_CHAPTER_OWNERSHIP` và giải quyết trước khi giao.
6. Kiểm tra 5 file YAML ở **cả VI/EN** parse được, root là mapping, Controller/method signatures trong các file kỹ thuật khớp source và hai ngôn ngữ. Không giao khi STEP 6 chưa có skeleton, API bị lệch, hoặc dữ liệu nguồn xung đột. **Snapshot SHA-256 của 10 file chính**, đặc biệt 8 file biên soạn theo Controller. Không cho writer nào khác sửa Swagger chính cho tới checkpoint merge; nếu có thay đổi ngoài ý muốn phải báo blocker, không ghi đè.

## 2. Staging duy nhất trong đúng module I3

Prime tạo **chỉ root**:

`<I3>/src/main/resources/step7-temp/`

Thư mục này được `.gitignore` bỏ qua bằng `**/src/main/resources/step7-temp/`. **Không commit, không đóng gói vào JAR/WAR**; Prime phải xóa trước khi build/publish cuối hoặc bảo đảm task package resources exclude thư mục này. Không xóa staging còn dữ liệu từ session trước: kiểm tra manifest/ownership/trạng thái và tiếp tục đúng phần còn thiếu; `STALE_STAGE` không phải quyền tự xóa.

Để worker không phải đọc file Swagger dùng chung, **Prime cấp snapshot riêng từng chương** trong:

`step7-temp/_seed/<chapter_package>/{vi,en}/`

Mỗi snapshot là *đúng subtree của Controller thuộc chương đó*, chép từ 4 YAML chính: không chứa Controller chương khác, chỉ key và metadata kỹ thuật hiện hữu. Prime lưu `step7-temp/_prime-manifest.yml` gồm workbook/gid, I3 canonical, chapter/package, Controller/method ownership, hash file nguồn và trạng thái QA; **Prime độc quyền sửa manifest và _seed**, worker chỉ được đọc `_seed/<chapter_package>` của mình. Seed có thể lấy các case đã viết để worker bảo toàn; không xóa/trắng dữ liệu hợp lệ.

**Chính worker** tạo folder output:

```text
<I3>/src/main/resources/
  swagger/
    vi/{controller-description,api-descriptions,api-execution,api-params,api-cases}.yml
    en/{controller-description,api-descriptions,api-execution,api-params,api-cases}.yml
  step7-temp/                          # Prime tạo và quản lý
    _prime-manifest.yml                 # Prime-only
    _seed/<chapter_package>/vi/...      # Prime-only, đúng Controller được cấp
    _seed/<chapter_package>/en/...
    <chapter_package>/                 # Worker chương này tạo và sở hữu
      vi/
        controller-description.yml
        api-descriptions.yml
        api-execution.yml
        api-cases.yml
      en/
        controller-description.yml
        api-descriptions.yml
        api-execution.yml
        api-cases.yml
```

**Không staging `api-params.yml` cho worker**: các key như `request`, `input` hiện là metadata **dùng chung toàn module**, không gắn với Controller/chapter. Prime giữ nguyên file chính trong lượt merge chapter; nếu cần hoàn thiện tham số dùng chung, Prime xử lý **một lượt độc quyền sau các worker** với cùng nguyên tắc preflight, VI/EN và bảo toàn `usage`. Không giao hai worker đồng thời ghi shared params, không giả định param key là riêng chapter.

## 3. Hợp đồng giao việc — đúng BỐN dòng, một chương/lượt

```text
<URL_TAB_CHINH_XAC_GID>
A1, B1, I3, A<PARENT>, B<PARENT>, B<CHILD_START>:B<CHILD_END>, C<CHILD_START>:N<CHILD_END>
READ: <ABSOLUTE_MAIN_CHAPTER_PACKAGE> ; <ABSOLUTE_TEST_CHAPTER_PACKAGE> ; <ABSOLUTE_SEED_CHAPTER_ROOT> | WRITE: <ABSOLUTE_STEP7_TEMP_CHAPTER_ROOT>
/java-learning/module-generate-agent/worker/STEP_7_WORKER.md
```

Prime thay placeholder bằng vùng thật/đường dẫn tuyệt đối, không gửi nội dung, gợi ý, Controller name, ví dụ đáp án hoặc giải thích rule. Nếu vùng con không liên tiếp, liệt kê đủ các vùng thực tế trong **dòng 2**, không cấp vùng bao trùm chương khác. **Dòng 3 là ranh giới quyền**: worker chỉ đọc source/test của hai package và seed chương mình; chỉ ghi staging chapter của mình. Không được đọc `_prime-manifest.yml` hay Swagger chính dùng chung, không đọc chapter hoặc module khác, không đọc MD/TXT khác ngoài file worker-only. Nếu thiếu read/write path, báo `BLOCKER: STEP7 OWNERSHIP`.

## 4. Các file và field cho phép thay (áp dụng độc lập ở VI/EN)

| File dưới `swagger/{vi,en}/` | Key thuộc chapter | Worker chỉ biên soạn | Cấm đổi |
| --- | --- | --- | --- |
| `controller-description.yml` | `<ControllerName>` | `description`: HTML giải thích nhiệm vụ Controller/chương | Controller key; `readmeRelated` |
| `api-descriptions.yml` | `<ControllerName>.<methodSignature>` | `summary`, `description` | `methodName`, `params`, `path`, `mapping`, `httpMethods`, `mappingPaths`, `produces`, `consumes`, `usage`, `enableVideoYoutube`, `videoYoutubeId`, `videoYoutubeTitle`, `readmeRelated` |
| `api-execution.yml` | `<ControllerName>.<methodSignature>` | `execution`: HTML giải thích đúng Controller → Service → domain/nhánh thực thi | `usage`, Controller/method keys |
| `api-cases.yml` | `<ControllerName>.<methodSignature>.cases[]` | `id`, `title`, `description`, `input`, `status`, `response`, `detailDescription` (HTML) theo tình huống thật | `methodName`, `httpMethods`, `mappingPaths`, `usage`, Controller/method keys |
| `api-params.yml` | `request`, `input`, các shared params khác | **Worker KHÔNG sửa**; Prime xử lý riêng sau merge nếu có nhu cầu | Toàn file ở phase worker |

Không được tự thêm/xóa Controller hoặc method signature. Không xóa case có nội dung đúng; nếu sửa case cũ phải có lý do và bằng chứng so với source. Giữ `id` của case đã có, thêm case mới bằng ID không trùng; không sinh case chỉ để đủ số lượng. Hai ngôn ngữ phải có **cùng Controller/method/case ID và cùng input/status/response đúng behavior**, chỉ dịch chuỗi mô tả; giá trị trong response chỉ khác khi API thực sự hỗ trợ localization.

`detailDescription` phải dùng **HTML** để giải thích vì sao case này có status/response như vậy; worker tự chọn nội dung và cấu trúc phù hợp theo Java source, không ép template dài. `controller-description` và `execution` cũng dùng HTML; kiểm tra ký tự `<`, `>`, `&` được escape trong text/code và không chèn script/style/event handler.

## 5. Phân phối, checkpoint worker và Prime QA

1. Prime lập ledger `PARENT_ROW → PACKAGE → CONTROLLERS/METHODS → SOURCE_HASH → OWNER → STATUS → VI_QA/EN_QA → MERGE`. Các trạng thái `PENDING`, `IN_PROGRESS`, `PENDING_QA`, `QA_PASS`, `BLOCKER`, `MERGED`. Một worker sở hữu tối đa một chapter một lượt; pool theo Prime Rules, reuse sleeping; không dừng sau batch đầu.
2. Worker tự đọc **toàn bộ** Knowledge trong Sheet, Java main/test đúng package, seed chương rồi tự tạo 8 YAML staging, không ghi file chính/Sheet. Worker tự parse/readback kết quả, báo coverage và `COMPLETED`/`BLOCKER`; `COMPLETED` chưa phải Prime `QA_PASS`.
3. Prime kiểm tra độc lập: mỗi Controller/method trong seed được phủ đủ ở **cả hai ngôn ngữ**; protected fields giữ nguyên; YAML parser PASS; không duplicate Controller/case id; `description` và `execution` có ý nghĩa, case minh họa đúng code/test và HTTP status thực. Kiểm tra những case có nội dung sẵn đã được bảo toàn, không có TODO ở field giao worker cần hoàn thiện. Nếu FAIL, giao **đúng worker chapter** sửa staging (không cho Prime viết hộ).
4. Prime chỉ bắt đầu merge **khi tất cả chương của toàn tab đã QA_PASS**, writer đã giải phóng ownership và không còn `PENDING`/`BLOCKER`. Trường hợp không có API phải có `API_NOT_REQUIRED` được Prime xác minh từ source, và không làm giả YAML chỉ để đủ chương.

## 6. PRIME-ONLY merge, rollback và cleanup (không ghi từng batch)

1. **Re-read SHA-256 toàn bộ các YAML chính** và so snapshot lúc seed; bất kỳ file chính nào đổi trong khi worker làm → `BLOCKER: SWAGGER SOURCE CHANGED`, giữ staging, không merge mù. Validate `_prime-manifest.yml`, chapter/controller map, và parse 8 staging YAML theo chapter; không chấp nhận key ngoài ownership.
2. Load YAML về dữ liệu cấu trúc (mapping, không nối text, không replace cả file bằng file worker). Với mỗi Controller/method chính xác trong manifest, cập nhật **chỉ field được phép ở mục 4**. Các root Controller khác và field kỹ thuật/mapping/video trong Controller thuộc chương phải giữ y nguyên. `api-params.yml` không nằm trong chapter merge. Cần giữ các case đã có; đối chiếu trước–sau theo ID. Không tạo duplicate hay drop dữ liệu.
3. **DRY_RUN toàn module**: tạo bản kết quả ra vùng staging của Prime, parse YAML kết quả (cả vi/en), đối chiếu checksum/semantic diff field-by-field, QA tất cả case với Java/source/test; đảm bảo toàn bộ Controller/method liên quan được cập nhật đúng và **các phần ngoài ownership giống hệt**. Khi có lỗi, không thay file chính.
4. **APPLY một lượt cuối**: lưu snapshot byte-for-byte file chính vào `step7-temp/_backup/` (manifest hash); tạo output hoàn chỉnh cho **cả 8 file** bên trong staging, recheck checksum, rồi thay các file chính bằng thao tác thay thế an toàn. Nếu ghi lỗi giữa chừng, **rollback tất cả file đã thay từ _backup**, xác minh hash sau rollback và giữ staging, báo `BLOCKER: STEP7 MERGE PARTIAL`. Không xóa seed/output khi rollback thất bại.
5. Sau APPLY: exact readback cả 8 file chính; so dữ liệu YAML với bản dự kiến, xác nhận field ngoài ownership bảo toàn; 2 file `api-params.yml` bảo toàn hoặc đã được Prime kiểm tra/cập nhật **độc quyền** đúng ngôn ngữ; không để URL/script không an toàn. Không chạy generator Swagger sau merge vì có nguy cơ sửa metadata người viết. Test/build/QA cần thực hiện trong trạng thái **staging đã được loại khỏi packaged resources**.
6. **Chỉ sau khi MERGE + readback + kiểm tra tài nguyên đóng gói PASS**, Prime xóa **duy nhất** `<I3>/src/main/resources/step7-temp/` (gồm mọi chapter, _seed, _backup, manifest). Xác minh folder không còn và `git status/diff` chỉ thay đúng 10 Swagger YAML chính (hoặc 8 nếu shared params giữ nguyên). Không dùng lệnh delete glob bao ngoài staging, không xóa folder khi còn lỗi QA hoặc merge chưa đạt.
7. Nếu session bị gián đoạn, khi resume **kiểm tra trạng thái file chính, manifest/hash, backup, staging và ledger** trước: phần đã merge xong → QA/readback, không APPLY lặp; phần chưa merge → tiếp tục đúng checkpoint. Không reset staging, không overwrite file chính đã cập nhật.

## 7. Completion gate, CURRENT_STEP.VALUE và bàn giao

**Chỉ COMPLETE khi**: đủ Menu Cấp 1, worker ownership giải phóng, VI/EN đã QA; 8 file Swagger chính merge/readback PASS, shared params được kiểm tra, dữ liệu ngoài ownership được bảo toàn, không có blocker/pending, staging đã xóa sạch, Git diff được nghiệm thu, test/build liên quan PASS.

Sau đó chính Prime xác minh đúng tab/gid, `I3`, `CURRENT_STEP = Current STEP`, đọc cả giá trị/công thức và trạng thái lịch sử `CURRENT_STEP.VALUE`, rồi **ghi `CURRENT_STEP.VALUE = "7" và đọc lại chính xác** theo `rules/PRIME_RULES.md §2`. Nếu CURRENT_STEP.VALUE đã mang `STEP 7` theo cách đánh số cũ, phải đối chiếu lịch sử; có xung đột/trạng thái cao hơn không được tự ghi đè, báo `BLOCKER: CURRENT STEP UPDATE`. Không ghi CURRENT_STEP.VALUE trước khi merge/cleanup hoàn chỉnh.

Báo cáo: `SESSION_WORKER_MODE`, `MAX_WORKERS`, `MODULE_I3`, `TAB_GID`, `TOTAL_CHAPTERS`, `CHAPTERS_QA_PASS`, `API_NOT_REQUIRED`, `STAGING_STATUS`, `DRY_RUN`, `MERGE_STATUS`, `ROLLBACK`, `VI_EN_QA`, `SHARED_PARAMS`, `STAGING_CLEANUP`, `BUILD/TEST`, `CURRENT_STEP.VALUE_READBACK`, `BLOCKER`, `GIT_STATUS`. **Không tự commit/push, không tự triển khai STEP 8 (chưa định nghĩa), STEP 9/10 hoặc Video optional**.
