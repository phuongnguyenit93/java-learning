# SECURITY_CONTENT.md — Tách Content Private & giảm khả năng crawl

> **Tài liệu bàn giao cho session sau — cập nhật 2026-10-20.**
>
> **Trạng thái:** ĐANG THIẾT KẾ / CHƯA TRIỂN KHAI. Đây là bản tổng hợp yêu cầu, những phát hiện đã kiểm tra trong code, phương án được đề xuất và các quyết định còn mở. **Không được xem đề xuất trong file này là yêu cầu tự động sửa code, di chuyển repository, đổi quyền public/private, xóa Git history hay deploy.**
>
> **Đọc cùng:** `AGENTS.md`, `ARCHITECTURE.md`, `module-generate-agent/GENERAL_AGENT_RULES.md` (theo routing hiện hành), và `learning-platform/ARCHITECTURE.md` trong repository riêng. Khi tài liệu khác với code đang chạy, **code hiện tại và yêu cầu mới nhất của người dùng** là căn cứ để xác minh lại.

## 1. Tóm tắt dành cho session mới

Người dùng muốn **tách toàn bộ content học tập khỏi `java-learning` thành một repository PRIVATE** (tên chưa quyết định), trong khi:

- `java-learning` vẫn là repository Java/Gradle module structure, Java source, metadata và build logic; dự kiến tiếp tục PUBLIC.
- `learning-platform` vẫn là website công khai, React/TypeScript + Cloudflare Worker/Static Assets.
- Người dùng **không kỳ vọng chống sao chép tuyệt đối**. Mục tiêu là **giảm khả năng lấy toàn bộ nội dung nhanh và dễ**, nhất là bằng `git clone`, tải một ZIP công khai, hoặc tải các **JSON index/catalog** rồi lấy ra URL của mọi bài.
- Người dùng **chấp nhận** việc người có kỹ năng lấy nội dung từ browser state/payload sau giải mã; không cần tuyên bố chặn được việc đó.
- Mong muốn cụ thể ở DevTools **Sources/Network**: không còn truy cập/tải trực tiếp **JSON nội dung/index ở dạng rõ nghĩa** chỉ bằng mở URL; nếu trực tiếp tải được thì ưu tiên để đó là **ciphertext** thay vì plaintext.
- Code mã hóa/giải mã có thể công khai; **secret/khóa giải mã chính không được công khai**.
- Nội dung trong Java có source format khác với dữ liệu dành cho website: **giữ tầng Gradle projection**, không ép private repo lưu theo frontend format.
- Cần xét ảnh hưởng tới **cả `java-learning` lẫn `learning-platform`**, CI/CD GitHub → Cloudflare, các STEP workers, Google Sheets sync và Local Run JAR.

**Thứ tự đã được đề xuất trong hội thoại:** (0) giảm điểm lộ dữ liệu hàng loạt lớn nhất (đặc biệt global Knowledge search JSON), (1) tách source content/private repo, (2) hoàn thiện mã hóa và phân phối an toàn, (3) tăng kiểm soát crawl có chọn lọc. Đây là **đề xuất thứ tự**, chưa có lệnh bắt đầu từng giai đoạn.

## 2. Phân biệt rõ mục tiêu và giới hạn bảo mật

### 2.1. Các lớp dữ liệu khác nhau

| Lớp | Mục tiêu |
| --- | --- |
| **Authored content (source)** | Private Git repo; người ngoài không thể clone/lấy các bản cập nhật, lịch sử biên soạn, Excel, Markdown/YAML nguồn |
| **Gradle projection (build-only)** | Từ source tạo catalog, Knowledge index/sections, Quiz, Interview, Roadmap, Video, Search...; validate contract; không vô tình public bản thô |
| **Published artifact** | Không còn một public bulk ZIP plaintext dễ tải; có thể mã hóa file/package |
| **Website asset/API** | Người dùng đọc/xem/tìm kiếm bình thường; tránh gửi toàn bộ corpus/index rõ nghĩa trong một request |
| **Browser** | Không thể ngăn tuyệt đối sao chép **dữ liệu đã được browser nhận ở dạng plaintext**, dù trước đó có mã hóa |

### 2.2. Những điều không thể được hứa

1. Nếu React nhận **toàn bộ JSON giải mã**, người dùng có thể lấy lại JSON từ Network hoặc JavaScript state, dù Worker đã giải mã bằng secret.
2. Nếu browser tự giải mã ciphertext, trình duyệt phải nhận khóa hoặc năng lực giải mã đủ dùng. Khóa phiên/capability cấp cho browser **không thể bí mật tuyệt đối đối với chính browser đó**. Việc công khai thuật toán/code không làm mất an toàn mật mã; **lộ khóa** hoặc thiết kế trả dữ liệu quá rộng mới là vấn đề.
3. `Origin`, `Referer`, `User-Agent`, `Sec-Fetch-*`, cookie/session, URL khó đoán... không phân biệt chắc chắn người thật và crawler. Dùng chúng làm tín hiệu phụ; bảo vệ thực tế dựa thêm vào lượng dữ liệu mỗi response, tần suất truy cập, bot protection và phân quyền.
4. Private source repo **không thu hồi được nội dung từng xuất hiện trong Git history public, Release cũ, Cloudflare bản cũ, cache hoặc bản sao của bên thứ ba**.
5. Mã hóa ZIP **chỉ lúc truyền/lưu**, nhưng giải mã ZIP tại GitHub Actions rồi deploy toàn bộ JSON plaintext vào `dist/`, **không đạt mục tiêu Sources/Network** của người dùng.
6. Không có cơ chế nào giữ bí mật một bài học vẫn được website công khai hiển thị cho mọi người; chỉ có thể làm quá trình thu thập hàng loạt tốn công hơn.

## 3. Kiến trúc đang chạy — đã kiểm tra trong source

### 3.1. Dữ liệu Java hiện tại

Ví dụ module:

    java-learning/
      module/platform/development/programming/paradigm/object-oriented/
        master.json
        properties.json
        build.gradle
        src/
        content/
          readme/docs/{vi,en}/...
          readme/metadata/...
          roadmap/{vi,en}/roadmap.yml
          quiz/{vi,en}/question.yml
          interview/{vi,en}/question.yml
          video/{vi,en}/...
          reference/references.yml
          sheet/vi/module.xlsx     # snapshot local, đang có ignore rule

- Thống kê **snapshot local ngày 2026-10-20**: khoảng **217 thư mục `content/`**, **2.539 file `.md`**, **1.073 file `.yml`**, **1 file `.xlsx`** (tổng ~**57,3 MiB**). Số liệu thay đổi theo checkout/trạng thái module.
- Các generator, setup và import tasks hiện hard-code đường dẫn dạng `moduleProject.projectDir/content/...`. Chuyển repo mà không thay đổi cơ chế resolve/staging sẽ khiến nhiều task không tìm thấy file.
- Các tác vụ Sheets có kiểm tra `Module Location` từ ô `I3`, module-relative path, quyền ghi; không được tự thay `I3` hoặc đánh đồng Git root với content root.

### 3.2. Publisher tại `java-learning`

    module/.../content/* + module metadata
                 |
                 v
      ./gradlew generatePortalData
                 |
                 v
      build/learning-data/java/
        module-catalog.json
        module/{ROUTE_ID}/knowledge/{lang}/index.json
        module/{ROUTE_ID}/knowledge/{lang}/content/...
        module/{ROUTE_ID}/quiz|interview|roadmap|video|reference/...
        search/knowledge-search.{vi,en}.json
                 |
                 v
      scripts/publish-java-data.py (validate/package)
                 |
                 v
      GitHub Release tag "java-data" (PUBLIC)
        java-learning-data.zip
        java-learning-data.zip.sha256
                 |
                 v
      repository_dispatch: java-data-published
        { source_commit, data_sha256 }

Các file liên quan:

- `.github/workflows/publish-java-data.yml`: checkout, JDK 21, `generatePortalData`, validate, compare, package, rolling GitHub Release, verify lại artifact, dispatch `learning-platform`.
- `project-build/gradle-runtime/src/main/groovy/com/example/learning/setup/root/structure/plugin/StructureSetupPlugin.groovy`: đăng ký các task projection.
- `.../service/ProjectStructureService.groovy` và các `Portal*ProjectionService.groovy`: đọc module metadata + authored content.
- `.../service/LearningDataPaths.groovy`: output root `build/learning-data/java`.
- `scripts/publish-java-data.py`: manifest hiện có `schemaVersion=1`, `producer=java-learning`, `sourceCommit`, `sourceCommitTime`, `fileCount`, `moduleCount`, `referenceCount`, `totalBytes`, `catalogSha256`. ZIP hiện **chưa mã hóa**.

### 3.3. Consumer tại `learning-platform`

- `scripts/sync-java-data.mjs` tải **public** `https://github.com/phuongnguyenit93/java-learning/releases/download/java-data/java-learning-data.zip` và `.sha256`; validate checksum, manifest, catalog và allowed paths; sync vào `dist/language/java/`.
- `.github/workflows/verify-and-preview.yml` build frontend, test + verify dataset, upload `dist` làm artifact, deploy Cloudflare khi đủ điều kiện.
- `scripts/build-module-navigation.mjs` tạo `dist/module-navigation.json` (tree rút gọn) và `dist/module-details/{ROUTE_ID}.json` từ catalog.
- `src/data/javaAsset.ts` ánh xạ `/module/...` hoặc `/search/...` thành `/language/java/...`.
- `src/data/knowledge.ts` tải **trọn** `knowledge/{lang}/index.json` về browser.
- `src/components/KnowledgePanel.tsx` tải `section.content` (file Markdown) khi mở section.
- `src/data/knowledgeSearch.ts` tải **toàn bộ** `search/knowledge-search.{lang}.json` về browser để search client-side.
- `wrangler.jsonc` đang có `assets.run_worker_first: ["/api/*"]`. `worker/index.ts` xử lý Local Run API; request ngoài `/api/` được phục vụ bằng `env.ASSETS.fetch`. Chưa có lớp policy riêng cho URL Knowledge/static indexes.
- Các URL `/learning/java/knowledge/{ROUTE_ID}` hiển thị trong DevTools **Sources** là **React routes**, không phải bằng chứng có JSON source file đúng tên đó; URL file dữ liệu thực thường thuộc `/language/java/...`.

### 3.4. Local Run là pipeline riêng

- `.github/workflows/local-run-build.yml` trong `java-learning`: `workflow_dispatch`, xác nhận module và fingerprint, build `bootJar`, publish rolling Release `local-run`.
- `scripts/local-run-fingerprint-v2.py` và `LocalRunFingerprintService.groovy` dùng Git objects tại `HEAD` của module + dependencies + shared Gradle inputs. Nếu `content/` bị xóa khỏi Git tree, module tree hash có thể đổi, làm JAR trước đó bị coi là stale.
- `learning-platform/src/data/githubPublicRelease.ts` sử dụng GitHub Public Release API cho JAR; Worker có token-based fallback/dispatch qua `worker/local-run/github.ts`. **Không được làm hỏng Local Run khi thay đổi publisher content.**

## 4. Điểm lộ nội dung hàng loạt đã xác minh

**Đây là phát hiện then chốt từ code + artifact generated local**, không phải đo live production:

| File output | Snapshot local | Rủi ro |
| --- | --- | --- |
| `module-catalog.json` | ~613.891 bytes (0,59 MiB) | Danh sách module, metadata và đường dẫn tới dữ liệu khác |
| `search/knowledge-search.vi.json` | ~17.735.309 bytes (16,91 MiB); **4.780 documents** | Có `searchText` được tạo từ **toàn bộ phần lớn văn bản của Markdown**, không chỉ title |
| `search/knowledge-search.en.json` | ~7.754.276 bytes (7,40 MiB); **4.315 documents** | Tương tự |
| `module/OBJECT_ORIENTED_PROGRAMMING/knowledge/vi/index.json` | ~110.989 bytes; **21 categories, 192 sections** | Từng `section.content` chỉ thẳng tới URL Markdown |

- Chỉ riêng `searchText` của các **SECTION tiếng Việt** trong snapshot đã chiếm khoảng **12.996.709 ký tự**.
- `PortalKnowledgeSearchProjectionService.groovy` đọc toàn bộ Markdown projected, qua `markdownToPlainText` rồi `normalizeSearchText` và đưa vào trường `searchText`. Vì vậy tải một global search JSON có thể trích được rất nhiều text mà không cần truy cập từng bài.
- `knowledge/{lang}/index.json` có `tree → category.sections[] → content`: một crawler có thể lần theo URL và tải hàng loạt.
- `module-navigation.json` là tree rút gọn nhưng vẫn thuận tiện để dò danh sách module.
- Public ZIP hiện tại là **đường tải toàn bộ dữ liệu nhanh nhất**. Private authored repository đơn lẻ **không xử lý được** lỗ hổng này nếu ZIP vẫn plaintext/public.

**Ưu tiên bảo mật thực tế:** không public full global search index/corpus; không public bulk ZIP plaintext; sau đó mới xét throttling, policy cho từng index/lesson.

## 5. Đích kiến trúc được đề xuất (CHƯA CHỐT THIẾT KẾ)

    [java-learning]                     [java-learning-content]
     PUBLIC, Gradle/Java                  PRIVATE, authored content
     module metadata + src/               module/.../content/...
               \                            /
                \-- CI content assembly ---/
                            |
                   Gradle projections
                            |
                      validate data
                            |
                package/encrypt/publish
                            |
                learning-platform CI/Worker
                            |
                 public UI + scoped content

### 5.1. Ranh giới source of truth

- **`java-learning`:** giữ `module/**/master.json`, `properties.json`, `gradle.properties`, `build.gradle`, `src/**`, Gradle plugins, module setup, Local Run build.
- **Repo content PRIVATE:** sở hữu `module/{same-relative-module-path}/content/**`, học liệu `readme`, `roadmap`, `quiz`, `interview`, `video`, `reference`, những đầu vào/tài liệu liên quan. Xác định lại quy tắc cho `sheet/*.xlsx` (hiện có snapshot local bị ignore), generated content/metadata và asset trước khi migrate.
- **`learning-platform`:** sở hữu UI/UX, frontend fetch layer, Cloudflare Worker, search/query interface, phiên bản contract consumer, triển khai website. Không tự trở thành nguồn tác giả cho nội dung Java.
- **Tên repo private CHƯA CHỐT:** người dùng từng cân nhắc `java-learning-knowledge` hoặc `java-content`; đề xuất thêm `java-learning-content` vì chứa nhiều loại học liệu hơn Knowledge.

Để giảm refactor, đề xuất **giữ nguyên relative module path** trong repo content. Ví dụ:

    java-learning/
      module/platform/development/programming/paradigm/object-oriented/
        master.json
        properties.json
        src/

    java-learning-content/  # private, tên chưa chốt
      module/platform/development/programming/paradigm/object-oriented/
        content/readme/docs/vi/...
        content/roadmap/...
        content/quiz/...
        content/interview/...

### 5.2. Hai cách ghép content cần lựa chọn

**A — Transitional overlay/staging:** CI checkout hai repo bằng credential đọc riêng, tạo **bản content tạm** tại layout `java-learning/module/.../content` trước khi chạy Gradle. Ưu điểm: ít phải sửa service đã hard-code path. Điều kiện: file staging không được commit vào public repo, không bị lộ trong log/artifact/cache; mọi writer ghi vào **repo private** hoặc staging có đồng bộ được kiểm soát, không tạo hai nguồn sự thật.

**B — ContentPathResolver lâu dài:** đưa `contentRoot`/`moduleContentDir(module)` vào Gradle services, tasks, module setup và sheet import/export. Đọc/ghi trực tiếp private workspace, không cần mount qua public module. Độ tách biệt tốt hơn nhưng tác động nhiều file Groovy + tests. Có thể thực hiện theo hai bước **A → B**, không cần refactor toàn bộ ngay từ đầu.

**Không mặc nhiên dùng private Git submodule** trong public repo: `actions/checkout`, local dev, CI và các workflow không có quyền private sẽ phức tạp. Nếu chọn submodule phải đánh giá permission/read-only deploy key và pinned revision trước.

### 5.3. STEP workers và Google Sheets

- STEP liên quan content, nhất là STEP 2/3/4/5/6/7/8/9 (theo rule-file hiện hành), phải nhận diện **content repo là nơi ghi output**, không ghi nhầm public Java source.
- **FILL-ONLY, BLOCKER/ownership, preflight và phạm vi ghi Sheet** vẫn là quy tắc cũ: không vì đổi repository mà cho phép ghi đè hay tiếp tục một nhiệm vụ bị BLOCKER.
- Module location/key giữa Google Sheets và `java-learning` phải ổn định; resolver ánh xạ đến content ở repo khác. Chỉ thay physical path, không đổi định danh chương/module tùy tiện.
- Rà các task `ReadmeSheetImportService`, `RoadmapSheetImportService`, `VideoSheetImportService`, `QuizSheetImportService`, `InterviewSheetImportService`, `FillIntroductionService`, metadata sync, Excel export/import, setup skeleton, `GenerateFinalReadmeService`, image/video refs và tests.
- Không tự thay đổi quy tắc Prime/Worker của một STEP khi chưa đọc đúng rule-file hiện hành.

## 6. Mã hóa dữ liệu — cần tách hai mục tiêu

### 6.1. Bảo vệ ZIP công khai khỏi bị tải và đọc nguyên corpus

Có thể mã hóa artifact **sau projection + validation và trước upload**, ví dụ AES-256-GCM với nonce/IV **duy nhất** cho mỗi lần mã hóa, authentication tag, format version và `keyId`. Hoặc dùng **envelope/public-key encryption** để publisher chỉ giữ public encryption key; decryption key chỉ do consumer/runtime quản lý.

- **Không dùng ZipCrypto hoặc password ZIP cũ** làm lớp bảo vệ chính.
- **Không commit secrets** vào Java source/React/Vite `VITE_*`/config; không in secret hay decrypted archive ra CI logs/artifacts.
- Phải xác minh checksum/ciphertext, tính xác thực AEAD, manifest nội bộ sau giải mã, đồng bộ artifact nguyên tử và chống dùng nhầm phiên bản rolling Release.
- Nếu khóa bí mật được lưu tại `learning-platform`, cân nhắc **public-key/envelope encryption** để tránh cấp decryption secret cho `java-learning`. Quyết định thuật toán, khóa, rotation, nơi publish và mô hình trust cần design review.
- Mã hóa ZIP mà **giải mã ngay trong frontend CI rồi deploy plaintext `dist/language/java/**` chỉ bảo vệ Release tải trực tiếp; **không bảo vệ JSON Sources/Network**.

### 6.2. Bảo vệ JSON/Index trong Sources/Network

| Cách | Browser thấy gì? | Giới hạn |
| --- | --- | --- |
| **Worker giữ master key, giải mã rồi trả nguyên JSON** | Response **plaintext JSON** vẫn hiện rõ trong Network | Không đạt mục tiêu cản copy JSON trực tiếp |
| **Browser nhận ciphertext, giải mã client-side bằng Web Crypto và khóa/session scoped** | Request file gốc trong Network là **ciphertext**; người dùng phổ thông không mở file để đọc ngay | Browser có năng lực giải mã; người có kỹ năng có thể trích khóa phiên, override JS/fetch/Web Crypto hoặc lấy state/plaintext sau giải mã |
| **Worker giữ corpus/index, chỉ trả projection tối thiểu theo chức năng** | Network chỉ thấy menu nhỏ hoặc các kết quả query/bài học cụ thể | Cần refactor search/navigation APIs, access/rate policy; nhiều request vẫn có thể gom lại |

**Định hướng kết hợp đáng cân nhắc**, chưa phải quyết định cuối cùng:

1. **Global search index**: chỉ lưu server-side (R2 private hoặc search store), cung cấp `/api/knowledge/search` trả kết quả giới hạn; **không bao giờ gửi cả `searchText` corpus** xuống browser.
2. **Navigation/menu**: chỉ gửi các trường cần cho UX; cân nhắc `per-module index` hoặc scoped API; không kỳ vọng giấu hoàn toàn tiêu đề/chỉ mục bài mà người học cần thấy.
3. **Lesson/section content**: có thể phân phối ciphertext theo bài + giải mã client-side với session/capability hạn chế nếu ưu tiên **Sources/Network không chứa plaintext**; hoặc Worker trả plaintext từng bài nếu ưu tiên đơn giản và rate control. **Không tuyên bố client-side decrypt làm bí mật nội dung**.
4. **Static file routing**: nếu cần kiểm soát Worker, `run_worker_first` phải bao gồm các content endpoints được bảo vệ, hoặc chuyển nội dung qua private R2/Worker. Không được giữ **đồng thời** đường plaintext static công khai làm bypass.

**Chú ý:** Gói dữ liệu mã hóa không nên bắt browser giải mã **toàn bộ** `knowledge-search.vi.json` (hàng chục MB text) trước khi search; việc này sẽ giữ nguyên điểm lộ corpus dù Network ban đầu thấy ciphertext.

### 6.3. Quản lý secrets và session

- Secret chính giữ ở **GitHub Actions secrets** hoặc **Cloudflare Worker secrets**, không phải source public, HTML, React state, downloadable JavaScript, source maps.
- Browser-scoped session keys/capabilities chỉ nên có **phạm vi hẹp, TTL ngắn và hạn mức**; không coi chúng là bí mật đối với người sở hữu browser.
- Không giả định `Origin`/`Referer`/`Sec-Fetch-*` hay một “request từ website thật” là bằng chứng bảo mật; bot có thể tái tạo request từ session hợp lệ.
- WAF/rate limit/challenge phục vụ **giảm tốc crawler**, cần điều chỉnh để không ảnh hưởng người học hoặc nhiều người dùng chung IP; đo tỷ lệ chặn sai.
- Mục tiêu chính là **tăng chi phí crawl đại trà**, không phải DRM hay ngăn người học lưu bài.

## 7. Versioning, CI/CD và các rủi ro tích hợp

### 7.1. Một lần publish lấy đầu vào từ HAI commits

Dataset mới cần biết `javaCommit` (structure/build/plugins) và `contentCommit` (private source) thay vì chỉ một `sourceCommit`. Cần **thiết kế migration tương thích** với manifest `schemaVersion=1` / `sourceCommit` và expectation `data_sha256` đang có, không tự ý đổi tên trường rồi làm consumer fail.

Ví dụ **ý tưởng**, chưa phải schema đã duyệt:

    manifest:
      schemaVersion: <migrated version>
      producer: java-learning
      javaCommit: <40-hex>
      contentCommit: <40-hex>
      plaintextDatasetSha256: <digest internal>
      encryption: { formatVersion, algorithm, keyId }
    dispatch:
      producerJavaCommit
      producerContentCommit
      encryptedArtifactSha256

- Rà trigger **commit content-only**: thay đổi private repo vẫn phải kích hoạt publisher; hiện publisher ở `java-learning` chủ yếu chạy khi push thay đổi đường dẫn Java.
- Checkout repo private bằng quyền **read-only, scope tối thiểu**, không dựa vào `GITHUB_TOKEN` của repo public để tự động đọc repo khác.
- Producer/consumer phải pin đúng hai revision, chống race khi rolling Release bị cập nhật giữa `dispatch` và `download`; giữ checksum verification, retry, fail-closed và rollback.
- Không đặt nội dung plaintext trong GitHub Actions artifacts công khai, PR builds không đáng tin, `dist/`, caches, logs hoặc URL public thừa. Cẩn thận khi thiết kế bước build chạy trên public fork/PR không được cấp secrets.
- Key rotation, phục hồi khi khóa bị mất, release retention và cutover/rollback cần quy trình rõ ràng.

### 7.2. Local Run độc lập

- Không yêu cầu `local-run-build.yml` phải checkout content private nếu `bootJar`/Gradle không cần nội dung đó.
- Rà tác động khi `module/**/content` biến mất khỏi Git module tree: fingerprint V2 có thể invalidation một lần; không nhập `contentCommit` vào **Java build fingerprint** nếu content không làm đổi JAR.
- Giữ các đường đi Github Public Release + Worker fallback và `local-run` rolling Release của `learning-platform`; không để token quản lý content thay thế token dispatch JAR.

### 7.3. Lịch sử cũ — hướng xử lý người dùng đã chọn (2026-10-20)

Chuyển `content/` khỏi HEAD hoặc mã hóa bản mới **không xóa** dữ liệu trong:

- Git commits/tags/history trước đây ở `java-learning` public;
- các `java-data` Release ZIP plaintext cũ (nếu còn truy cập được) và bản sao đã tải;
- Cloudflare deploy/cache/artifact cũ, công cụ tìm kiếm, mirror/fork.

**Cập nhật ý định từ người dùng:** Không cần phức tạp hóa việc rewrite toàn bộ Git history. Khi đã tách content, người dùng muốn **xóa repository GitHub `java-learning` public cũ và tạo lại một repository mới với lịch sử Git sạch**, có thể dùng lại cùng tên. Đây là **quyết định định hướng về phương pháp**, KHÔNG phải lệnh xóa repo ngay.

- Repository mới phải được tạo từ **snapshot file sạch với Git history mới hoàn toàn** (fresh `git init`/initial commit); **KHÔNG** `git push --mirror`, copy `.git` cũ, đẩy lại branches/tags/commits cũ hoặc vô tình import history chứa content.
- **Nên dựng và kiểm thử bản repo thay thế trước khi xóa repo cũ**: bảo toàn backup/private snapshot, uncommitted/untracked source mới của workers, module structure, pinned `learning-contract` submodule, build Gradle, Local Run, CI config và permissions. Xác nhận migration/rollback cụ thể rồi mới thực hiện thao tác phá hủy.
- Xóa/tạo lại repository làm **thay đổi GitHub repository ID** và mất/mất liên kết nhiều dữ liệu cấu hình: Actions secrets, repository settings, deploy keys, permissions, workflows/runs history, GitHub Releases `java-data`/`local-run` và các liên kết release/JAR. Cần tái cấu hình và republish artifacts; `learning-platform` đang trỏ tới URL/Release của repository Java.
- Nếu tái sử dụng đúng `OWNER/java-learning`, URL mới có thể giữ nguyên, **nhưng các release tags/assets, credentials, webhook/dispatch và references tới commit SHA/run ID cũ không tự phục hồi**; phải thực hiện E2E verify trên website.
- **GitHub nói rõ xóa repository public không xóa forks public của nó**, và bên thứ ba có thể giữ clone, ZIP, cache, bản triển khai cũ. Vì vậy cách này **xóa cửa truy cập history trên repo public chính mới**, không bảo đảm xóa sạch mọi bản sao trên Internet.
- Nếu cần rollback sau cutover, phải tính trước khả năng dựng lại configuration, data release và frontend deploy; không dựa vào việc xóa rồi lập tức khôi phục repo cũ. Không xóa, force-push hoặc rewrite history khi chưa có yêu cầu hành động tường minh.

## 8. Roadmap ưu tiên (đề xuất; chưa có lệnh triển khai)

### P0 — Giảm lộ corpus qua global search

- Xử lý ngay `knowledge-search.{vi,en}.json` đang chứa toàn văn chuẩn hóa. Hướng bền vững: server-side search; bước giảm rủi ro tạm thời có thể dùng metadata-only search **nếu người dùng chấp nhận thay đổi chất lượng tìm kiếm**.
- Đảm bảo website không tải global `searchText` index nguyên bản trong Browser Network.
- Kiểm tra cả ZIP Release, cloud asset path, old deployments/caches trước khi tuyên bố không còn đường tải hàng loạt.

### P1 — Tách authored content sang PRIVATE repository

1. Chốt tên repo, scope file nào là source/private, owner/source-of-truth.
2. Sao chép content theo đúng module path; đối chiếu số file, checksums và metadata; **không xóa nguồn trước khi nghiệm thu**.
3. Cấu hình checkout/staging hoặc `ContentPathResolver`; task đọc/ghi đúng repo; cập nhật STEP workers, Sheet imports, Excel and metadata.
4. Kiểm thử Gradle projections + content-only trigger, Local Run, `learning-platform` static dataset contract.
5. Cutover theo hai revisions được pin; chuẩn bị **repository Java mới với fresh Git history, không có content**, kiểm thử đầy đủ và có backup/rollback trước khi xóa repository cũ và tái tạo tên/đổi kết nối.

### P2 — Mã hóa artifact / JSON và thay đổi đường phục vụ

1. Chốt threat model: bảo vệ ZIP, JSON index và/hoặc cả sections; phân định **server-decrypt** vs **client-decrypt**.
2. Chọn AEAD/envelope format, quản lý key/rotation, manifest version/migration, checksum, CI secrets.
3. Thay publisher/consumer; đảm bảo không lộ decrypted output trên public ZIP, static assets, logs, artifacts.
4. Nếu dùng client-side decrypt, bảo đảm DevTools **raw Network response là ciphertext**, không nhầm lẫn điều này với bảo mật tuyệt đối.
5. Nếu dùng server-side API, chỉ trả dữ liệu cần thiết thay vì nguyên global index, có query limits và rate controls.

### P3 — Hardening, vận hành và đo hiệu quả

- Cloudflare Worker-first/routing hoặc private storage cho các URL được bảo vệ, WAF/bot/rate policy có chọn lọc.
- Thống kê request bất thường, mức ảnh hưởng người học, thời gian tải bài/tìm kiếm, cache, chi phí Cloudflare, key rotation, incident recovery.
- Audit URL public, GitHub assets, source maps, static `dist/` và stale releases.

## 9. Tiêu chí nghiệm thu nên dùng khi bắt đầu làm

**Repo/source:** private checkout chính xác; không còn authored content trong Git HEAD public (sau cutover); source files không bị mất/sai; `master.json`/module IDs/Gradle vẫn hoạt động; các STEP/Sheets viết đúng nơi.

**Build/projection:** chạy `generatePortalData` trên clean runner với input pin theo Java + content commits; validate Knowledge, Quiz, Interview, Roadmap, Search; smoke tests; lỗi content-only update vẫn kích hoạt publish; rollback được.

**Website:** navigation, module details, Markdown sections, search VI/EN, Quiz/Interview/Video, Local Run Build/Download vẫn hoạt động; client không phải tải corpus toàn phần để dùng search.

**An toàn phân phối:** direct GET tới public bulk ZIP **không trả plaintext corpus**; direct GET raw encrypted asset **không lộ readable JSON**; không còn plaintext shadow path trong static `dist/`; nếu dùng client-side decrypt, test Network raw ciphertext và render đúng; nếu dùng Worker API, không trả nguyên full index; cache/old versions được xử lý theo quyết định.

**Secrets:** không nằm trong repo, log, source map, frontend bundle hay client error response; decryption failures/authentication-tag mismatch fail-closed; ciphertext không bị đánh tráo khi release rollover; secret rotation được test.

**Crawler cost:** đo thực tế số request cần thiết để lấy N bài, mức độ lộ qua metadata và search, response sizes và 429/challenge; không tuyên bố “chống crawl tuyệt đối”.

## 10. Quyết định còn mở — phải hỏi trước khi chốt implementation

1. **Tên và nơi tạo repo PRIVATE:** `java-learning-knowledge` / `java-content` / `java-learning-content`; ai sở hữu credential + workflow?
2. **Phạm vi chuyển source:** chỉ `content/**` hay thêm generated metadata, hình ảnh, Excel snapshot, agent input files? Những file nào thực sự là authored source?
3. **Mode migration:** giữ Gradle old layout qua ephemeral overlay trước hay implement ContentPathResolver ngay?
4. **Tải JSON trong DevTools:** cần bảo vệ chỉ **global index** hay **mọi index/menu và Markdown**? Phương án browser-decrypt ciphertext hay Worker trả **scoped response**? Có chấp nhận trade-off UX/chi phí không?
5. **Search:** bắt buộc full-text trên tất cả bài hay chấp nhận metadata-only tạm thời? Server-side index ở R2/private search store nào?
6. **Anonymous public website:** có yêu cầu login/Turnstile/session không? Giới hạn request bao nhiêu mà không cản trở người học?
7. **Public Java Data Release:** giữ tại repo Java nhưng mã hóa, hay chuyển nguồn artifact và cấp quyền tải giữa repos?
8. **Tạo lại repo Java:** người dùng đã chọn hướng **xóa repo cũ và tạo repo mới với fresh Git history**, thay cho history rewrite. Chưa chốt lịch cutover, backup, xử lý forks/releases/cache và thời điểm được phép xóa. Không tự thực hiện.
9. **Plan test/deploy/cutover:** thời điểm kích hoạt, Preview/Production, rollback; không chuyển production chỉ dựa vào tài liệu này.

## 11. Hướng dẫn session mới (copy và gửi)

> Hãy đọc `/java-learning/SECURITY_CONTENT.md` trước, tiếp theo `AGENTS.md` và source hiện tại ở cả `java-learning` và `learning-platform`. Tóm tắt lại những **yêu cầu người dùng đã chốt** so với **đề xuất chưa quyết định**, kiểm tra Git status và pipeline thực tế, rồi tiếp tục từ giai đoạn tôi chỉ định. Không tự ý tạo repo, sửa rules, mã hóa dữ liệu, xóa content, rewrite Git history, commit, push hay deploy nếu tôi chưa yêu cầu cụ thể. Giữ Local Run/Google Sheets/STEP workflows và dữ liệu học tập đang phục vụ website không bị gián đoạn.

### Các entry point để khảo sát lại (đường dẫn thay đổi theo refactor)

`java-learning`:

- `.github/workflows/publish-java-data.yml`; `.github/workflows/local-run-build.yml`
- `scripts/publish-java-data.py`; `scripts/local-run-fingerprint-v2.py`
- `project-build/gradle-runtime/src/main/groovy/com/example/learning/setup/root/structure/service/`: `ProjectStructureService.groovy`, `PortalKnowledgeProjectionService.groovy`, `PortalKnowledgeSearchProjectionService.groovy`, `PortalQuizProjectionService.groovy`, `PortalInterviewProjectionService.groovy`, `PortalRoadmapProjectionService.groovy`, `LearningDataPaths.groovy`, `LocalRunFingerprintService.groovy`
- `project-build/gradle-runtime/src/main/groovy/com/example/learning/task/sheets/service/` và `module-generate-agent/`

`learning-platform`:

- `.github/workflows/verify-and-preview.yml`; `scripts/sync-java-data.mjs`; `scripts/build-module-navigation.mjs`
- `src/data/javaAsset.ts`, `knowledge.ts`, `knowledgeSearch.ts`, `moduleCatalog.ts`
- `src/components/KnowledgePanel.tsx`, `ModuleSidebar.tsx`; `src/pages/LearningPage.tsx`
- `worker/index.ts`, `worker/local-run/`, `wrangler.jsonc`

---

**Ghi chú cuối:** File này là **kế hoạch/bản ghi khảo sát**, không phải báo cáo đã triển khai. Khi quay lại session mới, trước hết xác minh lại repo state và các file bị thay đổi kể từ ngày 2026-10-20.
