# STEP 7 — WORKER ONLY: Hoàn thiện Swagger VI/EN trong staging của MỘT chương

**STEP status:** This worker must never read or update progress in Google Sheets/Excel or `master.json`. Prime alone records `CURRENT_STEP.VALUE` after whole-STEP QA.


**BẮT BUỘC tự đọc TOÀN VĂN file này trước khi làm việc và sau khi mất context.** Prime gửi đúng **bốn dòng định vị**: (1) URL Google Sheets tab đúng gid, (2) `A1, B1, I3`, một Menu Cấp 1 và toàn bộ Menu Cấp 2/Knowledge thuộc nó, (3) cặp đường dẫn Java main/test, seed Swagger **đã lọc riêng chương**, staging output chapter được cấp, (4) đường dẫn file worker-only này. Tin nhắn không chứa nội dung hay hướng dẫn; không tự đòi Prime gửi bản nháp.

## W1. Nguồn đọc và ownership CHẶT

1. **Một worker = một Menu Cấp 1**. Từ Sheet được giao: đọc `A1`, `B1`, `I3`, `A<PARENT>` (chủ đề chương), `B<PARENT>` (filename .md), **tất cả** `B<CHILD_ROWS>` và `C<CHILD_ROWS>:N<CHILD_ROWS>`. Ghép Knowledge từng hàng **C→N không thêm separator**, đọc đầy đủ trước khi biên soạn; không tự mở thêm hàng/chương/tab.
2. Đối chiếu I3 chỉ vào đúng module Java trong repo `java-learning`. **Chỉ được đọc Java source trong `<I3>/src/main/java/.../<chapter_package>/` và test trong `<I3>/src/test/java/.../<chapter_package>/` mà Prime chỉ định ở dòng 3.** Không tìm kiếm/đọc file trong package cha, package anh em, Application/root, module khác, Gradle root hoặc thư mục khác dù có quyền truy cập toàn workspace. Không theo junction/symlink/traversal vượt vùng.
3. Ngoại lệ ngoài package source: (a) vùng Sheet Prime cấp; (b) **đọc** `<I3>/src/main/resources/step7-temp/_seed/<chapter_package>/{vi,en}/*.yml` do Prime tách **chỉ Controller/method của chương**; (c) **đọc/ghi** duy nhất `<I3>/src/main/resources/step7-temp/<chapter_package>/{vi,en}/*.yml`; (d) đọc file worker-only này. Không đọc `_seed` chương khác, `_prime-manifest.yml`, `_backup`, file `resources/swagger/{vi,en}` chính, hay MD/TXT/rules/STEP khác. Không viết Java, Sheet, CURRENT_STEP.VALUE, Swagger chính hoặc staging package khác.
4. Kiểm tra package trong main/test/seed/output cùng tên `chapter_package`, đều thuộc module I3, seed chỉ chứa Controller/method thuộc Java chapter. Nếu có shared Controller, method lạ, source nằm ngoài package hoặc seed chưa đủ để lý giải behavior → **`BLOCKER: STEP7 OWNERSHIP/SOURCE`**, không tự đọc vượt scope, không đoán output.

## W2. Chỉ tạo 8 YAML output thuộc chapter package

Chính worker tạo `<I3>/src/main/resources/step7-temp/<chapter_package>/` với hai thư mục `vi/` và `en/`. Trong **mỗi ngôn ngữ tạo đúng bốn file**:

```text
<chapter_package>/
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

**Đọc seed tương ứng trước khi tạo output.** Giữ nguyên cấu trúc các key Controller/method, metadata kỹ thuật và case hợp lệ đã có. Chỉ thay các field liệt kê dưới đây; không tạo `api-params.yml`, README, mapping file, JS/script, manifest hoặc Java code.

| File | Key cụ thể | Chỉ thay field | Tuyệt đối giữ nguyên |
| --- | --- | --- | --- |
| `controller-description.yml` | `<ControllerName>` | `description`: HTML giới thiệu vai trò, nghiệp vụ và các API/chức năng của Controller trong chapter | `readmeRelated`, tên Controller |
| `api-descriptions.yml` | `<ControllerName>.<methodSignature>` | `summary` (ngắn, rõ), `description` (nêu API làm gì, ý nghĩa nghiệp vụ/kiến thức) | `methodName`, `params`, `path`, `mapping`, `httpMethods`, `mappingPaths`, `produces`, `consumes`, `usage`, `readmeRelated`, `enableVideoYoutube`, `videoYoutubeId`, `videoYoutubeTitle` |
| `api-execution.yml` | `<ControllerName>.<methodSignature>` | `execution`: HTML giải thích code flow và cơ chế thực thi có ý nghĩa | `usage`, Controller/method keys |
| `api-cases.yml` | `<ControllerName>.<methodSignature>.cases[]` | Case cụ thể: `id`, `title`, `description`, `input`, `status`, `response`, `detailDescription` (HTML) | `methodName`, `httpMethods`, `mappingPaths`, `usage`, Controller/method keys |
| `api-params.yml` (chỉ tồn tại ở Swagger chính) | `request`, `input` và key dùng chung | **KHÔNG ĐƯỢC ĐỌC/GHI/SINH** | Prime chịu trách nhiệm độc quyền ở checkpoint khác |

Các YAML output chỉ được chứa Controller/method thuộc seed của chapter, không chứa root Controller ngoài chương. Nếu chương có hơn một Controller được Prime cấp, giữ đủ từng Controller. Không bỏ method có `usage: false` hay tự chuyển thành true. Không xóa key seed, không tự thêm method/route/source path.

### W2.1. Chất lượng từng loại mô tả

- **Controller description (`description`)**: giải thích phạm vi và trách nhiệm của Controller, nhóm endpoint và kiến thức chính chương muốn minh họa. HTML có cấu trúc rõ, không kể chung chung "`Đây là mô tả`", không bịa luồng ngoài Java chapter.
- **API description (`summary`, `description`)**: `summary` là một câu/ngữ ngắn nêu tác dụng API, `description` chỉ rõ tính năng hoặc nguyên lý áp dụng. Không chép lại annotation/URL để đủ chữ; không lặp nguyên `execution` hoặc toàn case response.
- **Execution (`execution`)**: mô tả chính xác luồng nhận request → kiểm tra → gọi Service/Model/DTO → những nhánh có ý nghĩa → response/error; nêu các method/class **thực sự tồn tại trong Java chapter**. Trọng tâm là *vì sao API được thiết kế vậy / dùng để giảng giải cơ chế gì*. Không ép mọi API theo cùng mẫu liệt kê 4 bước. Không trộn mô tả một case đặc thù vào giải thích chung nếu API còn nhiều case.
- **Cases**: với mỗi method có API thật và `usage: true`, soạn ít nhất case tiêu biểu giúp hiểu behavior; thêm boundary/invalid/error case **khi source thực sự có nhánh đó và đem lại giá trị**, không quota vô nghĩa. `input` phải thể hiện body/path/query/header thực, `status` là số HTTP đúng, `response` đúng DTO và exception handler. Không bịa các giá trị hay tình huống chưa thể suy ra source; nếu cần chạy API ngoài package để xác minh mà không được phép → báo blocker.
- **Case `detailDescription`**: viết **bằng HTML**, giải thích **tại sao input của case này lại cho ra kết quả/status/response đó** dựa trên Java source thực tế. Worker tự lựa chọn mức độ chi tiết và cách trình bày phù hợp với từng case; không bắt buộc một cấu trúc HTML cố định.
- HTML **không phải Markdown** ở các field HTML; nội dung text/code escape `&`, `<`, `>`, quote thích hợp. Không có script, iframe, style, event handler hoặc URL không được xác minh. YAML dùng scalar `|-` cho HTML dài, không chứa fenced Markdown bao quanh YAML.
- **Hai ngôn ngữ đầy đủ**: `vi` viết tiếng Việt tự nhiên, `en` tiếng Anh đúng thuật ngữ. Controller key, methodSignature, case `id`, cấu trúc API và dữ liệu kỹ thuật **giống nhau** ở VI/EN. Không dịch Java identifier, path, JSON property, tên exception hay thông báo response gốc nếu API không có localization. Không để TODO, "Chưa có", "not provided", hay placeholder trong field đã được giao hoàn thiện.

### W2.2. Giữ dữ liệu cũ và phân quyền từng field

- Seed có **case/field đã được biên soạn hợp lệ** → giữ nguyên. Chỉ sửa nội dung cũ nếu kiểm chứng sai so với Java source hoặc không đáp ứng nội dung học tập, và **nêu chính xác lý do/diff**; không xóa/ghi đè để sinh lại từ đầu.
- Case cũ phải giữ ID ổn định. Với case mới, dùng `case_001`, `case_002`... **không trùng case ID trong cùng method**, không reuse ID để chỉ tình huống khác; nếu seed đã có case_001 đầy đủ, bắt đầu từ ID tiếp theo.
- `methodName`/`path`/`mapping`/`httpMethods`/`mappingPaths`/`params`/`usage` và toàn bộ metadata ngoài whitelist là **nguồn kỹ thuật do Prime seed**, không được chỉnh ngay cả khi nhận thấy lỗi; báo Prime `BLOCKER: TECHNICAL_METADATA_MISMATCH`.
- `readmeRelated`, `enableVideoYoutube`, `videoYoutubeId`, `videoYoutubeTitle` và metadata liên kết khác **thuộc STEP khác/người review**, không chạm.

## W3. Trình tự thao tác và preflight/QA

1. Xác minh URL/tab, A1/B1/I3, toàn bộ hàng con và Knowledge. Xác minh main/test/seed/staging theo **dòng 3**. Root staging có sẵn do Prime tạo; folder chương của bạn chưa có hoặc có bản cũ hợp lệ từ lần resume. Nếu staging thuộc owner khác, seed thay đổi sau preflight, chapter mơ hồ → BLOCKER, không xoá/overwrite mù.
2. Đọc tất cả source và test **trong package** để xác định hành vi thực tế, Controller, Service, DTO, Model, error handler (nếu có); dùng seed làm mẫu kỹ thuật chỉ của chapter. Đối chiếu từng method trong cả vi/en; thiếu source hoặc thông tin behavior không xác minh được → báo blocker.
3. Tạo riêng `<chapter_package>/vi` và `/en` trong staging. Tạo/cập nhật đúng 8 YAML bằng cách giữ root Controller/method và protected fields từ seed, thay nội dung whitelist. Không ghi lẫn sang `_seed` và Swagger chính.
4. Parse **từng YAML** bằng parser an toàn; kiểm tra root là mapping, method/key đầy đủ, duplicate ID không có, `case.input` hợp lệ, `case.status` đúng kiểu/status, `case.response` phù hợp, `detailDescription` là HTML hợp lệ. So sánh sâu seed → output: **chỉ editable fields thay đổi**, mọi metadata khác giữ y nguyên; so VI↔EN case IDs và input/status/response.
5. **Exact readback tất cả 8 output files**, xác nhận file không còn placeholder ở các field phải hoàn thiện, HTML đúng và không phá serialization/YAML. Giữ mọi source và Sheet không thay đổi. Báo `BLOCKER` nếu chưa đủ hai ngôn ngữ/kiểm chứng, không báo COMPLETED sớm.
6. Báo Prime: `STATUS=COMPLETED/BLOCKER`, `TAB_GID`, `PARENT_ROW`, `CHAPTER_PACKAGE`, `MAIN/TEST_READ_SCOPE`, `SEED_SCOPE`, `STAGING_OUTPUT`, `CONTROLLERS`, `METHOD_SIGNATURES`, `CASES_BEFORE/AFTER`, `VI_QA`, `EN_QA`, `PROTECTED_FIELDS=UNCHANGED`, `SOURCE_EVIDENCE`, `CHANGES_TO_EXISTING_CASES`, `BLOCKERS`. **Giải phóng ownership rồi SLEEPING**; Prime tự QA/merge, không gửi yêu cầu Prime chỉnh Java hay auto-merge chỉ vì báo xong.

**CẤM TUYỆT ĐỐI:** commit/push/deploy; quét `java-learning` ngoài hai chapter packages; ghi Google Sheets/CURRENT_STEP.VALUE; đọc/sửa Swagger canonical `/swagger/vi`/`/en`; chạm shared `api-params.yml`; xóa staging/manifest/backup; chạy generator `generateApiSwaggerDescription` hoặc tool merge. Prime là writer duy nhất với Swagger chính.
