# Learning Module Refactor Rules

Tài liệu này mô tả quy ước dùng khi chuẩn hóa một **learning module** trong repository.

Mục tiêu chính:

- sửa lại learning flow trước khi sửa sâu code;
- giữ nguyên code/README hiện tại để có thể so sánh thủ công **khi module có nội dung cũ cần bảo toàn**;
- chỉ tạo staging area riêng khi thật sự cần so sánh bản cũ và bản refactor;
- đảm bảo README và experiment trong code liên kết trực tiếp với nhau;
- chọn đúng workflow ngay từ đầu: **staging + promotion** hoặc **direct edit**;
- với workflow staging, chỉ replace vào phần thật sau khi review xong.

---

## 1. Flow tổng quát

Trước tiên phải xác định module thuộc **một trong hai trường hợp** sau.

### Trường hợp A - Module đã có nội dung cần giữ để so sánh

Áp dụng khi:

- `menu` đã có learning content đáng kể;
- `module` đã có code/experiment cũ;
- cần refactor lớn và muốn giữ nguyên bản cũ để review/diff;
- user xác định cần có bản song song trước khi replace.

Khi đó dùng staging:

```text
README current : readme/<lang>/menu/
README staging : readme/<lang>/menu2/

Java current   : .../module/...
Java staging   : .../moduleb/...
```

Flow:

Làm theo từng vertical slice/topic:

```text
BASE.md
    ↓
chốt learning flow tổng
    ↓
Topic hiện tại
    ↓
đọc README cũ + code cũ
    ↓
viết README mới trong menu2
    +
viết code mới trong moduleb
    ↓
README ↔ Controller ↔ Service phải khớp nhau
    ↓
compile/run/verify
    ↓
review thủ công
    ↓
replace vào menu + module thật khi đã duyệt
```

Mỗi topic nên hoàn thiện tương đối trọn vẹn trước khi chuyển sang topic tiếp theo.

### Trường hợp B - Nội dung mới hoặc không cần giữ bản cũ

Áp dụng khi:

- module ban đầu chưa có nội dung đáng kể;
- README/code hiện tại chỉ là skeleton;
- đây là learning content mới;
- user xác định **không cần bản song song để so sánh**;
- thay đổi đủ nhỏ để sửa trực tiếp an toàn.

Khi đó **không tạo `menu2` và `moduleb`**.

Flow:

```text
BASE.md
    ↓
chốt learning flow
    ↓
Topic hiện tại
    ↓
sửa trực tiếp menu + module
    ↓
README ↔ Controller ↔ Service phải khớp nhau
    ↓
compile/run/verify
    ↓
review
```

Rule quan trọng:

```text
staging là công cụ để bảo toàn bản cũ và so sánh
staging không phải bước bắt buộc cho mọi learning module
```

Trong cả hai trường hợp, không sửa toàn bộ README trước rồi mới sửa toàn bộ Java. Vẫn ưu tiên vertical slice/topic-by-topic.

---

## 2. Ownership của README

Trong một module có README generation:

```text
readme/<lang>/BASE.md
→ learning flow / phần mở đầu tổng thể

readme/<lang>/LIST.md
→ generated
→ không sửa thủ công

readme/<lang>/menu/
→ nội dung hiện tại đang được sử dụng

readme/<lang>/menu2/
→ chỉ tồn tại trong workflow staging khi cần giữ bản cũ để so sánh
```

Rule:

```text
workflow staging:
menu  = original/current
menu2 = proposed/new

workflow direct edit:
menu  = current + nơi sửa trực tiếp
menu2 = không cần tạo
```

Không sửa `menu` khi đang dùng workflow staging và còn cần giữ bản cũ để review/diff.

---

## 3. Ownership của Java source

Code hiện tại:

```text
src/main/java/com/example/learning/module/...
```

Code staging, **chỉ khi workflow yêu cầu staging**:

```text
src/main/java/com/example/learning/moduleb/...
```

Ví dụ:

```text
code thật
com.example.learning.module.basic

code staging
com.example.learning.moduleb.basic
```

Không tạo top-level folder kiểu:

```text
<module>/moduleb/src/main/java/...
```

`moduleb` là **Java package staging**, không phải Gradle/module directory mới.

Nếu module thuộc workflow direct edit thì không tạo `moduleb`; sửa trực tiếp dưới:

```text
src/main/java/com/example/learning/module/...
```

---

## 4. Spring scan conflict trong moduleb chỉ là staging concern

`com.example.learning.moduleb` vẫn nằm dưới package scan của:

```java
@SpringBootApplication
package com.example.learning;
```

Vì vậy controller/service staging có thể bị Spring scan cùng code thật.

`@Profile("moduleb")` **không phải requirement mặc định**.

Chỉ dùng profile riêng nếu thực sự cần load application / chạy integration check với cả code current và staging cùng tồn tại:

```java
@Profile("moduleb")
```

cho các Spring bean mới như:

```text
@RestController
@Service
@Component
@Configuration
```

Nếu controller staging có endpoint giống controller thật **và cần chạy hai bản song song**, có thể dùng prefix riêng:

```text
/moduleb/...
```

Ví dụ:

```java
@RequestMapping("/moduleb/basic")
```

Mục đích duy nhất là có thể đặt code mới cạnh code cũ mà không phá application hiện tại trong lúc verify.

Rule:

```text
@Profile("moduleb")
/moduleb route prefix
moduleb bean/thread names

= temporary staging isolation
≠ final design
```

Nếu đã dùng các marker này để compile/runtime check thì **phải clear/normalize khi promote/replace**.

Nếu không cần chạy current + staging cùng lúc thì ưu tiên **không thêm chúng ngay từ đầu**.

---

## 5. Rule của generateInternalReadmeMenu

Trước khi viết file Markdown mới, cần hiểu contract của task:

```text
generateInternalReadmeMenu
```

Source of truth hiện tại:

```text
project-build/gradle-runtime/
src/main/groovy/com/example/learning/task/readme/internalMenu/
```

Task yêu cầu file Markdown tối thiểu có:

```md
# README Title

Đoạn giới thiệu nếu cần.

## <a id="section-one">1. Section One</a>

Nội dung...

## <a id="section-two">2. Section Two</a>

Nội dung...
```

Mỗi section dùng format:

```md
## <a id="stable-id">Tên section</a>
```

Task dùng:

```text
id
→ anchor/menu link

text bên trong <a>...</a>
→ menu label
```

`id` nên:

- duy nhất trong file;
- ổn định qua các lần refactor;
- ngắn, dễ đọc;
- không phụ thuộc số thứ tự nếu không cần thiết.

Ví dụ tốt:

```text
process-and-thread
start-vs-run
thread-state
daemon-thread
```

---

## 6. Không tự viết generated markup nếu chưa cần

`generateInternalReadmeMenu` tự tạo/rebuild:

```text
<a id="back-to-top"></a>

## Menu

<details>
<summary>Click for details</summary>

</details>

- [Quay lại đầu trang](#back-to-top)

---
```

Vì vậy khi authoring Markdown source mới, ưu tiên viết dạng tối giản. File đó có thể nằm trong `menu2` nếu dùng workflow staging, hoặc nằm trực tiếp trong `menu` nếu dùng workflow direct edit:

```md
# Title

Intro

## <a id="section-a">Section A</a>

Content A

## <a id="section-b">Section B</a>

Content B
```

Không cần chủ động thêm:

- `## Menu`;
- `back-to-top` anchor;
- `<details>`;
- `Quay lại đầu trang`;
- separator giữa các generated section.

Nếu file đã được chạy qua generator thì generated markup có thể tồn tại; không coi đó là human-owned source logic.

Generator phải giữ tính:

```text
run N times = run once
```

Không được duplicate menu/details/back-to-top/separator.

---

## 7. Structure nội dung cho một learning section

Mỗi section nên cố gắng đi theo flow:

```text
Mental Model / Theory
        ↓
Problem hoặc câu hỏi cần chứng minh
        ↓
Controller method tham khảo
        ↓
Endpoint / cách chạy experiment
        ↓
What to observe
        ↓
Explanation
        ↓
Conclusion
```

README không chỉ mô tả API.

Nó phải trả lời:

```text
Concept này là gì?
Vì sao cần biết?
Code nào đang chứng minh nó?
Khi chạy sẽ thấy gì?
Vì sao kết quả đó xảy ra?
Kết luận mental model là gì?
```

Ví dụ:

````md
### Demo trong module

Tham khảo controller:

```text
BasicThreadController#startVsRun()
```

Endpoint, ví dụ khi đang dùng workflow staging:

```text
GET /moduleb/basic/start-vs-run
```

Khi chạy, hãy quan sát tên thread...

**Kết luận:** ...
````

---

## 8. README ↔ Controller mapping

README phải chỉ rõ controller/method tương ứng khi có experiment.

Controller cũng phải truy ngược được về README section.

Path trong Javadoc phải trỏ tới **nơi README đang thực sự tồn tại trong workflow hiện tại**:

```text
workflow staging    → readme/.../menu2/...
workflow direct edit → readme/.../menu/...
after promotion      → readme/.../menu/...
```

Khuyến nghị Javadoc:

```java
/**
 * README: readme/vi/menu2/1.Basic/Basic.md#start-vs-run
 * Purpose: So sánh gọi run() trực tiếp với start() một Thread mới.
 */
@GetMapping("/start-vs-run")
public ... startVsRun() {
    ...
}
```

Controller comment nên ngắn.

Không copy toàn bộ theory vào Java source.

Controller chỉ cần cho biết:

```text
README section nào?
Experiment này chứng minh điều gì?
```

---

## 9. Vai trò của Controller và Service trong learning module

Mental model chung:

```text
README
→ Theory / learning contract

Controller
→ nút kích hoạt experiment

Service
→ implementation của experiment

Response / Console / State / Timing
→ observation
```

Controller không cần được thiết kế như business API nếu mục tiêu của module là học tập.

Endpoint nên phục vụ một experiment rõ ràng.

Service method nên cố gắng chứng minh **một concept chính** thay vì trộn nhiều kiến thức không liên quan.

---

## 10. Rule cho learning experiment

Mỗi experiment cần đạt các tiêu chí:

1. Mental model chính xác.
2. Code thật sự chứng minh điều README đang nói.
3. Output/state/timing đủ rõ để quan sát.
4. Chạy lặp lại vẫn có ý nghĩa.
5. Không vô tình leak thread/executor/resource.
6. README, controller name, method name và behavior không mâu thuẫn.

Nếu demo cố tình minh họa behavior xấu như leak/deadlock/race:

```text
Intentional unsafe demo
→ phải ghi rõ
→ nên bounded/controllable nếu có thể
```

Còn demo bình thường:

```text
Normal demo
→ cleanup toàn bộ resource đã tạo
```

---

## 11. Không dùng tên API để suy ra mental model sai

README phải ưu tiên concept trước API.

Ví dụ:

```text
Runnable / Callable
→ task abstraction

Thread
→ execution mechanism

Executor
→ abstraction quản lý việc thực thi task
```

Không gọi mọi thứ là "cách tạo Thread" chỉ vì chúng liên quan tới concurrency.

Tương tự, tránh các cách giải thích dễ tạo mental model sai như:

```text
Async = non-blocking
ThreadLocal = visibility solution
volatile = đọc thẳng RAM
Daemon thread = thread nền nên request kết thúc là nó chết
```

Ưu tiên semantics chính thức của Java/Spring hơn analogy đơn giản hóa quá mức.

---

## 12. BASE.md và learning flow

Trước khi refactor từng menu, nên chốt curriculum trong:

```text
readme/<lang>/BASE.md
```

`BASE.md` nên nói:

- module học về gì;
- thứ tự học;
- mục tiêu từng chapter;
- quan hệ giữa các chapter;
- cách sử dụng README/controller/service trong module.

Không cần nhồi toàn bộ kiến thức chi tiết vào `BASE.md`.

Chi tiết nằm ở từng file learning content: `menu2` trong workflow staging, hoặc `menu` trong workflow direct edit.

---

## 13. LIST.md và generated README

Nếu `LIST.md` hoặc README tổng được generate từ menu/file structure:

```text
không chỉnh generated output bằng tay
```

Flow đúng:

```text
BASE.md / menu source / generator input
        ↓
task generate
        ↓
LIST.md / final README
```

Nếu output sai, ưu tiên sửa source hoặc generator thay vì patch output.

---

## 14. Rule của generateApiSwaggerDescription

Task:

```text
generateApiSwaggerDescription
```

dùng để scan Java source hiện tại của learning module và đồng bộ Swagger metadata theo structure thật của:

```text
Controller
→ API method
→ parameter
```

Mục tiêu của task không phải tự viết nội dung tài liệu chi tiết thay cho người author.

Mental model:

```text
Java source
    ↓ scan
generateApiSwaggerDescription
    ↓
đồng bộ Swagger metadata structure
    ↓
human-owned description được giữ lại
```

### Ba file Swagger metadata

Mỗi language directory dùng ba file chính:

```text
src/main/resources/swagger/<lang>/api-descriptions.yml
src/main/resources/swagger/<lang>/api-params.yml
src/main/resources/swagger/<lang>/controller-description.yml
```

#### api-descriptions.yml

Dùng để mô tả **từng API method**.

Một entry có thể chứa các field human-owned như:

```text
summary
description
videoYoutubeId
videoYoutubeTitle
```

và các field generated từ Java source như method name, mapping, HTTP method, path, params, usage...

Rule quan trọng:

```text
API đang tồn tại trong source
→ usage=true
→ generated fields được sync theo source

API historical/stale không còn trong source
→ có thể được giữ lại với usage=false
→ human-owned metadata không được tự ý overwrite/xóa
```

#### api-params.yml

Dùng để mô tả **parameter name** được phát hiện từ các API method.

`summary` và `description` của parameter là human-owned metadata.

Generator chịu trách nhiệm đồng bộ parameter nào đang được sử dụng; nội dung mô tả đã có không được overwrite chỉ vì task chạy lại.

#### controller-description.yml

Dùng để mô tả **Controller ở cấp tổng quan**, tương ứng với nhóm API / Swagger tag của controller đó.

Ví dụ:

```yaml
BasicThreadController:
  description: |-
    <h2>Basic Thread Controller</h2>
    <p>Mô tả tổng quan về nhóm API Basic Thread.</p>
```

Rule:

```text
Controller mới xuất hiện trong source
→ tạo skeleton entry nếu chưa có

Controller vẫn tồn tại
→ giữ nguyên description đã có

Controller không còn tồn tại trong source
→ xóa entry khỏi controller-description.yml
```

`description` của controller là **human-owned content** sau khi được tạo skeleton.

### Khi nào được enrich summary/description

Việc tạo/refactor learning module, tạo menu README hoặc chạy generator **không đồng nghĩa** phải tự động viết nội dung chi tiết cho Swagger metadata.

Rule mặc định:

```text
tạo module/menu/refactor topic
→ KHÔNG tự động enrich toàn bộ Swagger summary/description
```

Chỉ khi user có **yêu cầu riêng** về Swagger documentation/enrichment thì mới thực hiện bước semantic authoring.

Khi có yêu cầu đó:

```text
api-descriptions.yml
→ đọc và hiểu behavior thật của từng Controller method
→ đối chiếu README / Service nếu cần
→ viết summary ngắn, đúng trọng tâm
→ viết description mô tả experiment/behavior/điểm cần quan sát

controller-description.yml
→ đọc vai trò và phạm vi của toàn Controller
→ viết description tổng quan cho nhóm API
→ không chỉ đổi CamelCase thành một câu placeholder
```

Nếu module có nhiều language:

```text
vi / en / ...
→ nội dung phải tương ứng theo từng language
→ không copy nguyên một language sang language khác
```

Khi enrich thủ công theo yêu cầu, phải giữ nguyên các metadata human-owned khác không nằm trong scope, đặc biệt:

```text
videoYoutubeId
videoYoutubeTitle
enableVideoYoutube
historical entry usage=false
```

Sau khi chỉnh nội dung nên chạy lại:

```text
generateApiSwaggerDescription
```

để xác nhận:

```text
task vẫn chạy thành công
summary/description vừa author không bị overwrite
historical metadata vẫn được preserve
controller structure vẫn khớp source hiện tại
```

---

## 15. Quy trình cho một topic

Ví dụ topic `Basic` trong **workflow staging**:

```text
1. Đọc:
   readme/vi/menu/1.Basic/...

2. Đọc code thật:
   src/main/java/com/example/learning/module/basic/...

3. Audit:
   - kiến thức đúng chưa?
   - demo có chứng minh đúng không?
   - tên endpoint/method có đúng behavior không?
   - resource lifecycle ổn không?

4. Viết README mới:
   readme/vi/menu2/1.Basic/...

5. Viết code mới:
   src/main/java/com/example/learning/moduleb/basic/...

6. Link hai chiều:
   README → Controller method
   Controller → README anchor

7. Compile/run experiment.

8. So sánh:
   menu  vs menu2
   module vs moduleb

9. Chỉ promote sau khi review xong.
```

Nếu topic/module thuộc **workflow direct edit** thì bỏ các bước `menu2`, `moduleb`, compare và promotion; thay bằng sửa trực tiếp `menu` + `module`, nhưng vẫn giữ đầy đủ audit, link hai chiều, compile/run và review.

---

## 16. Validation checklist

Trước khi coi một topic là hoàn thành:

```text
[ ] Learning flow của topic hợp lý.
[ ] Kiến thức không mâu thuẫn Java/Spring semantics.
[ ] Markdown có title `# ...`.
[ ] Section chính dùng anchor `<a id="...">` hợp lệ.
[ ] Anchor id duy nhất và ổn định.
[ ] README không phụ thuộc vào generated markup thủ công.
[ ] README chỉ đúng controller/method tương ứng.
[ ] Controller Javadoc chỉ đúng README anchor.
[ ] Đã xác định đúng workflow: staging hay direct edit.
[ ] Nếu dùng staging và cần chạy hai bản cùng lúc, endpoint staging dùng `/moduleb/...` khi cần tránh conflict.
[ ] Nếu dùng staging và cần runtime coexistence, Spring bean staging dùng `@Profile("moduleb")` khi thật sự cần.
[ ] Service experiment đúng với điều README mô tả.
[ ] Output đủ rõ để learner tự quan sát.
[ ] Thread/executor/resource được cleanup đúng.
[ ] Demo có thể chạy lặp lại mà không bị state cũ làm sai kết quả.
[ ] Code đang sửa compile với Java version của project.
[ ] Nếu dùng staging, code thật và README thật chưa bị sửa ngoài scope trước promotion.
[ ] Nếu module dùng Swagger generator, structure của 3 file Swagger metadata khớp source hiện tại.
[ ] Không tự động enrich Swagger summary/description trong lúc chỉ tạo/refactor module hoặc menu nếu user chưa yêu cầu.
[ ] Nếu user yêu cầu enrich Swagger content, summary/description phải dựa trên behavior thật của method/controller và không overwrite metadata human-owned ngoài scope.
```

---

## 17. Promote staging sang code thật

Section này **chỉ áp dụng cho workflow staging**.

Sau khi review thủ công:

```text
readme/<lang>/menu2/...
        ↓ review/diff
readme/<lang>/menu/...

com.example.learning.moduleb...
        ↓ review/diff
com.example.learning.module...
```

Khi promote cần kiểm tra lại staging-only concern:

```text
@Profile("moduleb")
/moduleb route prefix
package com.example.learning.moduleb
README path chứa menu2
```

Các giá trị này thường phải đổi về production/current package/path tương ứng.

Không copy mù toàn bộ file mà bỏ qua các staging marker này.

Nếu `@Profile("moduleb")`, `/moduleb`, bean name, thread name hoặc marker staging khác đã được thêm chỉ để verify coexistence thì promotion phải remove/normalize chúng.

Promotion mang nghĩa:

```text
replace staging → current
không phải overlay staging lên current rồi để file cũ còn sót lại
```

Nếu cấu trúc mới ít file hơn cấu trúc cũ, phải bảo đảm old-only file không còn tồn tại sau promotion.

---

## 18. Tóm tắt ngắn để đưa vào context AI khác

```text
Learning module refactor has two workflows.

A. Existing content must be preserved for comparison:
   use staging.

B. New/empty content or user says comparison is unnecessary:
   edit current menu/module directly; do not create menu2/moduleb.

README current:
readme/<lang>/menu/

README staging, only for workflow A:
readme/<lang>/menu2/

Java current:
src/main/java/com/example/learning/module/...

Java staging, only for workflow A:
src/main/java/com/example/learning/moduleb/...

LIST.md/generated README:
do not edit manually.

Swagger metadata task:
generateApiSwaggerDescription
→ sync Java Controller/API/parameter structure into:
   api-descriptions.yml
   api-params.yml
   controller-description.yml

Swagger content ownership:
generator syncs structure/generated fields;
existing human-owned descriptions must be preserved.

Do NOT automatically enrich Swagger summary/description while merely creating/refactoring a learning module or README menu.
Only do semantic Swagger authoring when the user explicitly requests it.
When requested, understand each method/controller first, then write meaningful localized summary/description while preserving unrelated human-owned metadata.

BASE.md:
defines overall curriculum/learning flow.

Markdown source contract for generateInternalReadmeMenu:
# Title
## <a id="stable-id">Section</a>
content

Generator owns:
Menu, details, back-to-top, separators.

Each topic is developed vertically:
README theory
→ controller experiment
→ service implementation
→ observation
→ conclusion.

README should reference controller methods/endpoints.
Controller Javadoc should reference README anchors.

@Profile("moduleb") and /moduleb/... are optional temporary isolation mechanisms,
only when current + staging must run together.
They must be removed/normalized during promotion.

Validate correctness, repeatability and resource cleanup before promotion.
```
