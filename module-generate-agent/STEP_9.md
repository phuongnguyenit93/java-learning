# STEP 9 — Case-scoped Java Source Explorer (deterministic)

## 0. Contract

STEP 9 **không** refactor Java, không sửa Java/tests của STEP 8, không dùng AI inference.
Source runtime trong `src/main/java` bất biến. Output chỉ là bản Java **để đọc**
trong Monaco trên learning-platform. Không đồng bộ ngược Sheet, không sửa
Case Study YAML, Gradle metadata, Spring runtime hoặc Git.

Đơn vị logic là một cặp `(groupID, caseIndex)`. Task tự xác định tất cả cặp
từ `content/casestudy/vi/casestudy.yml` (groupID tuần tự, caseIndex 1-based).
Không dùng text tình huống hoặc AI để đoán class. Task đọc
`master.json` để xác định `JAVA_BASE_PACKAGE`, `SERVICE_NAME`.

## 1. Lọc Java

- Đọc `src/main/java/<JAVA_BASE_PACKAGE>/casestudy/controller/Group<g>Controller.java`,
  giữ duy nhất method `executeCase<c>()` cùng constructor, fields và annotations.
- Làm tương tự với `casestudy/service/Group<g>Service.java`.
- Trích imports còn được dùng trong bản Java đã lọc.
- Chọn file Java dưới `casestudy/<kind>/group<g>/case<c>/` không phân biệt
  kind (application/domain/model/port/utils/adapter/repository/dto...).
- Riêng case có sử dụng `CaseStudyJvmIsolation` giữ source launcher/bootstrap
  dùng chung để người học có thể mở class tương ứng.
- Không suy diễn/lần tìm toàn bộ dependency ngoài phạm vi trên. Không cố tạo
  một Java project có thể compile độc lập. Nếu thiếu/không duy nhất class/method,
  source không parse được hoặc đường dẫn không an toàn: **FAIL CLOSED**.

**Điều hướng:** Dùng JavaParser tạo lại `symbols.json` và
`references.json` từ **chính các byte source đã lọc**, không sử dụng
line/column của runtime gốc. Một unresolved reference không được tạo link
Ctrl+Click giả. Không cần Java Language Server.

## 2. Opt-in và task

Module chỉ tham gia khi có `content/code-explorer/casestudy.yml`:

```yaml
version: 1
type: CASE_STUDY_EXPLORER
```

Task Gradle của từng module:

```powershell
# Pilot một case: không ghi file
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:generateCaseStudyExplorer -PgroupID=1 -PcaseIndex=1 -Pmode=DRY_RUN

# Pilot một case: chỉ ghi output trong module/build
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:generateCaseStudyExplorer -PgroupID=1 -PcaseIndex=1 -Pmode=APPLY

# Full scope: tất cả case từ YAML, tạo manifest complete=true
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:generateCaseStudyExplorer -Pmode=APPLY

# Publish material của một module vào build/learning-data/java
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:publishCaseStudyExplorer -Pmode=APPLY
```

Root `generatePortalData` gọi `generatePortalCaseStudyExplorer`
cho các module opt-in. Không tự động publish GitHub/Cloudflare, commit hoặc push.

## 3. Material và FE

Build local full: `<MODULE>/build/code-explorer/casestudy`.
Pilot local: `<MODULE>/build/code-explorer/casestudy-pilot`; pilot không thể
publish hoặc ghi đè output full.
Publish: `build/learning-data/java/module/<SERVICE_NAME>/code-explorer/casestudy`.

```text
index.json                           # version=1, type=CASE_STUDY_EXPLORER
groups/<g>/cases/<c>/
  index.json                         # Code Explorer V1 file tree / hashes
  symbols.json
  references.json
  api-map.json                       # empty: Case Study không bắt buộc Swagger
  files/<JAVA_BASE_PACKAGE>/casestudy/...
```

Catalog ghi `caseStudyExplorer` riêng; API Docs vẫn dùng `codeExplorer`
cũ. Case Study panel dùng `groupID/caseIndex` để chọn material tương ứng
và tái sử dụng Monaco CodeExplorerPanel. Monaco model URI phải chứa
group/case để tránh cache nhầm nội dung giữa các source projection.

## 4. QA và checkpoint

1. DRY_RUN không ghi output; unknown group/case hoặc missing method fail.
2. APPLY pilot: kiểm chỉ có một executeCase trong Controller/Service, imports
   không còn các case khác, file tree đúng group/case.
3. Xác nhận Ctrl+Click Controller → Service → case class bằng symbol coordinates
   của source đã lọc; unresolved không bị gán nhầm.
4. Full module: toàn bộ case từ YAML phải có material, file SHA256 readback
   chính xác; manifest `complete=true`. Không publish manifest pilot.
5. Validate producer archive, consumer archive và FE build/test. Không ghi đè
   `src/main/java` hoặc trạng thái Git người dùng. Chỉ Prime cân nhắc cập nhật
   `CURRENT_STEP` nếu có provenance và tất cả gate của STEP 9 mới PASS.
