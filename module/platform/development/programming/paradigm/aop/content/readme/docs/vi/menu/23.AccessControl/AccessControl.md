<a id="back-to-top"></a>

# Kiểm soát truy cập

## Menu
- [Đặt kiểm tra quyền tại ranh giới thao tác có ý nghĩa](#access-control-001)
- [Liên kết danh tính và ngữ cảnh quyền với lời gọi](#access-control-002)
- [Phân biệt kiểm tra quyền tổng quát và quyền trên từng đối tượng](#access-control-003)
- [Không suy diễn quyền chỉ từ tên phương thức hoặc kiểu tham số](#access-control-004)
- [Thứ tự kiểm tra quyền so với giao dịch và bộ nhớ đệm](#access-control-005)
- [Nguy cơ đường gọi không đi qua cơ chế can thiệp](#access-control-006)
- [Ghi nhận từ chối truy cập mà không làm lộ dữ liệu](#access-control-007)
- [Ngăn aspect bảo mật bị vô hiệu hóa ngoài ý muốn](#access-control-008)
- [Kiểm thử nhiều đường vào thay vì chỉ luồng sử dụng thông thường](#access-control-009)

## <a id="access-control-001">Đặt kiểm tra quyền tại ranh giới thao tác có ý nghĩa</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt kiểm tra quyền tại ranh giới thao tác có ý nghĩa bằng Aspect (AOP)

## 1. Mục tiêu và vấn đề

Một hệ thống có thể xác thực danh tính người dùng thành công nhưng vẫn thực hiện thao tác sai quyền: xem tài liệu của tenant khác, phê duyệt đơn do chính mình tạo, sửa một đơn đã khóa, hoặc gọi chức năng quản trị từ một đường thực thi nội bộ. Với **Aspect-Oriented Programming (AOP)**, ta có thể dùng **pointcut** chọn các thao tác nghiệp vụ và **advice** kiểm tra chính sách trước khi cho phép tác vụ chạy.

Khó khăn nằm ở **vị trí đặt kiểm tra**. Nếu chỉ kiểm tra khi người dùng mở giao diện hoặc truy cập một URL, những đường vào khác có thể bỏ qua. Nếu gắn kiểm tra cho mọi getter/setter, chính sách bị phân tán, khó hiểu và có thể kiểm tra sai đối tượng. Cách thiết kế tốt hơn là nhận diện **ranh giới của hành động có ý nghĩa nghiệp vụ**: tạo đơn, chấp thuận, chuyển tiền, xuất dữ liệu, mời thành viên, thay đổi quyền, xem hồ sơ riêng tư hoặc giải ngân.

Mục tiêu của bài:

- Phân biệt **authentication**, **authorization** và kiểm tra **business invariant**.
- Chọn đúng **đơn vị thao tác** và **join point** để một quyết định quyền tương ứng một hành động nghiệp vụ cụ thể.
- Xác định **chủ thể (subject)**, **hành động (action)**, **tài nguyên (resource)** và **ngữ cảnh (environment)** bằng dữ liệu đáng tin.
- Phối hợp kiểm tra ở **ranh giới thao tác** với kiểm tra sát dữ liệu/side effect để tránh lỗ hổng bypass và **time-of-check/time-of-use (TOCTOU)**.
- Xử lý quyền theo tenant, ownership, trạng thái, dữ liệu trả về, tác vụ bất đồng bộ và yêu cầu kiểm toán.
- Xây dựng ví dụ Java 17 chạy được sử dụng **JDK dynamic proxy** làm một hình thức interception kiểu AOP.

Đây là kiến thức **AOP tổng quát**, độc lập thư viện. Những giới hạn của proxy và weaving được nêu rõ vì không phải mọi cơ chế AOP đều có cùng tập join point.

## 2. Xác thực khác phân quyền

**Authentication (xác thực)**: trả lời *ai đang yêu cầu?*, có bằng chứng đăng nhập đáng tin hay không. Đầu ra có thể là định danh principal/actor, tenant và thuộc tính được hệ thống xác thực.

**Authorization (phân quyền)**: trả lời *principal đó có được thực hiện hành động này đối với tài nguyên này, trong trạng thái và ngữ cảnh hiện tại, hay không?*

**Business invariant (bất biến nghiệp vụ)**: quy tắc phải luôn đúng của miền dữ liệu. Một số quy tắc vừa có yếu tố quyền vừa phụ thuộc trạng thái, như “người duyệt không được duyệt yêu cầu của chính họ”. Một số quy tắc chỉ là hợp lệ nghiệp vụ, như “đơn đã hủy không thể giao hàng”. Đừng ép tất cả vào một nhãn vai trò đơn giản.

Ví dụ một request `POST /documents/doc-7/approve`:

| Câu hỏi | Dữ kiện cần xét | Hệ quả |
|---|---|---|
| Ai gọi? | Principal đã xác thực, tenant hiện tại | Không tin tên người dùng do body tự khai |
| Họ định làm gì? | Hành động `document.approve` | Khác với quyền đọc tài liệu |
| Đối tượng nào? | Document `doc-7` và tenant sở hữu | Không chỉ xét route hoặc loại Java |
| Tài nguyên đang ở trạng thái nào? | `SUBMITTED` hay `APPROVED` | Một quyết định dựa vào snapshot cũ có thể sai |
| Có ràng buộc bổ sung nào? | Người duyệt không phải người tạo, giới hạn thẩm quyền | Quyền theo vai trò chưa đủ |
| Cho phép tạo hiệu ứng nào? | Thay trạng thái, phát event, gửi thông báo | Cần ngăn toàn bộ hành động nếu denied |

Một principal có vai trò `REVIEWER` không mặc nhiên được duyệt *mọi tài liệu trên mọi tenant*. Quyết định thường có dạng:

```text
allow = authenticated(actor)
     AND action_is_authorized(actor, action)
     AND within_same_tenant(actor, resource)
     AND subject_resource_relationship_is_valid(actor, resource)
     AND business_state_allows(action, resource)
```

Điều kiện thực tế phải được biểu diễn trong một chính sách được kiểm thử. **Thiếu dữ liệu xác thực, tài nguyên không tìm thấy hoặc không đủ quyền ⇒ deny theo chính sách**.

## 3. "Ranh giới thao tác có ý nghĩa" là gì?

Đó là điểm tại đó mã ứng dụng bắt đầu hoặc cam kết thực hiện một **hành động mà người dùng/hệ thống xem như một đơn vị quyền**. Nó thường có tên nghiệp vụ rõ ràng và hiệu ứng quan sát được.

So sánh:

| Điểm chặn | Vấn đề | Điểm phù hợp hơn |
|---|---|---|
| `setStatus(APPROVED)` | Setter không chứa ai duyệt, tài liệu nào và vì sao | `approveDocument(documentId)` |
| `repository.save(entity)` | Một lệnh save có thể phục vụ hàng chục mục đích | `publishArticle(articleId)` hoặc `updateBillingAddress(...)` |
| `getUser()` | Getter có thể dùng cho cả truy cập công khai lẫn riêng tư | `viewPrivateProfile(userId)` |
| `executeSql(...)` | Khó xác định ý định và actor ở tầng truy cập dữ liệu | Thao tác domain + lọc dữ liệu ở tầng lưu trữ |
| `sendEmail(...)` | Hành vi kỹ thuật xuất phát từ nhiều workflow | `inviteTeamMember(teamId, email)` với quyền tương ứng |

Không có nghĩa mọi điểm chặn kỹ thuật đều vô dụng. **Kiểm soát truy cập theo bản ghi**, tenant filter và bảo vệ ở kho dữ liệu vẫn cần. Nhưng nếu hỏi “người này có quyền phê duyệt tài liệu này không?”, nơi dễ hiểu và dễ kiểm chứng nhất thường là **đầu vào của thao tác phê duyệt**, trước side effect.

Một thao tác có thể chứa nhiều bước hoặc nhiều side effect. Quyết định quyền phải bao phủ **toàn logical operation**. Nếu gọi trực tiếp bước con có thể bỏ qua kiểm tra ở đầu thao tác, cần thêm hàng rào tương ứng tại nơi có thể đi vòng.

## 4. AOP tổ chức kiểm tra quyền ra sao?

Các khái niệm chung:

- **Join point:** điểm thực thi có thể bị chặn, tùy cơ chế weaving/interception.
- **Pointcut:** biểu thức/quy tắc chọn những join point thuộc phạm vi kiểm soát.
- **Advice:** mã chạy ở join point; một `before advice` phù hợp với kiểm tra quyền trước lời gọi, còn `around advice` có quyền quyết định có tiếp tục lời gọi hay không.
- **Proceed:** hành động chuyển quyền điều khiển tới phần xử lý đích; khi không được phép, advice **không được** gọi proceed.
- **Policy Decision Point (PDP):** nơi tính quyết định allow/deny theo actor, action, resource, context.
- **Policy Enforcement Point (PEP):** nơi thật sự chặn việc thực thi khi quyết định là deny.

Trong kiến trúc nhỏ, advice có thể vừa đọc metadata vừa gọi policy và chặn. Trong hệ thống lớn, nên tách **PEP/interceptor** khỏi **PDP/chính sách** để dễ test, audit và dùng chung nhiều giao thức gọi.

Luồng minh họa:

```text
Entry point (HTTP / message / CLI / scheduler)
         |
         v
Trusted identity context
         |
         v
AOP interception: business operation join point (PEP)
         |
         +--> xác định semantic action
         +--> lấy resource ID từ tham số đã xác minh
         +--> nạp dữ liệu tối thiểu cần cho quyết định
         +--> PDP.evaluate(actor, action, resource, context)
                    |
             +------+------+
             |             |
           DENY           ALLOW
             |             |
             v             v
        dừng gọi        proceed()
                           |
                 Domain/data-level guard
                           |
                  business side effect
```

Advice phải chạy trước khi có side effect. Nếu policy chưa nhận đủ ngữ cảnh để quyết định, thông thường nên từ chối hoặc yêu cầu thêm dữ liệu đáng tin, không suy đoán quyền từ trạng thái mặc định.

## 5. Chọn pointcut theo ý định nghiệp vụ

Ba cách biểu diễn phổ biến:

### 5.1. Ranh giới khai báo rõ

Ví dụ một interface có các thao tác:

```java
// Mảnh minh họa API, không phải chương trình độc lập.
interface DocumentCommands {
    DocumentView viewDocument(String documentId);
    ApprovalReceipt approveDocument(String documentId);
}
```

Cơ chế interceptor gắn action `document.read` với phương thức `viewDocument` và `document.approve` với `approveDocument`. Đây là mapping có ngữ nghĩa: **tên thao tác** và **quyền** phải được review cùng nhau.

### 5.2. Annotation chính sách

Có thể gắn annotation cho phương thức/loại thao tác để công cụ AOP nhận diện điểm chặn. Annotation chỉ là **metadata**, không phải bản thân hàng rào bảo mật: nếu một lời gọi không đi qua interception thì annotation có thể không được thực thi.

Chú ý metadata trên **interface vs implementation**, method override, generic bridge method, method được gọi qua reflection và giới hạn proxy/weaver. Nếu phương thức mới được thêm mà không có annotation, cần chính sách **fail closed**: hoặc từ chối ở cấp boundary, hoặc xác nhận nó được phân loại public/không cần guard một cách tường minh. Chỉ lọc các phương thức *có annotation* còn lại chạy tự do là lỗ hổng nếu toàn interface lẽ ra phải được bảo vệ.

### 5.3. Pointcut theo namespace/loại

Quy tắc như “mọi phương thức thuộc `application.commands`” có thể dùng để cưỡng chế chính sách mặc định, nhưng phải tránh tự suy quyền chỉ từ prefix `get*` hay `admin*`. Kết hợp điều kiện về **vùng boundary** với metadata về semantic action và kiểm thử coverage.

Dù dùng cách nào, phải lập một bảng bao phủ:

| Hành động | Entry points | Guard bắt buộc | Dữ liệu cho quyết định |
|---|---|---|---|
| Xem tài liệu riêng | Web, job nội bộ, message consumer | `document.view` | Principal, tenant, owner, trạng thái chia sẻ |
| Duyệt tài liệu | Web, batch job | `document.approve` | Reviewer, owner, trạng thái submitted |
| Xuất dữ liệu | API, báo cáo theo lịch | `report.export` | Tenant, phạm vi dữ liệu, quota |
| Quản lý thành viên | API, CLI quản trị | `team.members.manage` | Người thao tác, team, quan hệ và role |

Mỗi đường vào hợp lệ phải chạm vào **một enforcement path** tương đương, hoặc được chặn riêng với cùng chính sách trước khi phát sinh hiệu ứng.

## 6. Dữ liệu đầu vào cho policy phải đáng tin

Một quyết định quyền thường cần bộ bốn:

```text
DecisionRequest(
    subject = authenticated principal,
    action = semantic business action,
    resource = resource identified and loaded by trusted code,
    context = tenant / time / state / ownership / attributes
)
```

Các quy tắc quan trọng:

1. **Subject:** không lấy vai trò, user ID, tenant ID hay danh sách quyền từ JSON do caller tự khai. Principal phải đến từ đường xác thực đáng tin; nếu có delegation, quyết định cần kiểm tra chuỗi ủy quyền.
2. **Action:** lấy từ hợp đồng phương thức/pointcut đã định nghĩa, không dùng tham số text `"approve"` của request làm chứng cứ cấp quyền.
3. **Resource:** không quyết định chỉ dựa vào ID do client gửi; cần nạp dữ liệu/metadata thật với tenant, owner, project, tổ chức, trạng thái và policy liên quan.
4. **Context:** thời gian, IP, thiết bị, yêu cầu hai người duyệt, quota hoặc phân vùng dữ liệu nếu policy sử dụng thì phải có nguồn đáng tin và ngữ nghĩa được ghi rõ.
5. **Unknown:** resource không tồn tại, policy lỗi, principal thiếu hoặc dữ liệu cần cho quyết định không đầy đủ thì thông thường **deny**; phản hồi bên ngoài có thể che sự tồn tại của tài nguyên nếu yêu cầu bảo mật đòi hỏi.

Một người có quyền trong `tenant-a` không được mở tài nguyên thuộc `tenant-b` chỉ bằng cách thay ID. Đó là lỗi **object-level authorization/IDOR**: xác thực thành công và thao tác route đúng vẫn chưa đủ.

**Nạp tài nguyên trước khi cho phép** cũng cần thận trọng: phép đọc metadata để ra quyết định phải có phạm vi và không tự phát side effect. Không xuất dữ liệu nhạy cảm của resource bị từ chối qua thông báo lỗi, log, trace hoặc thời gian phản hồi nếu có thể tránh.

## 7. RBAC, ABAC và quan hệ chủ thể–tài nguyên

AOP không quyết định nên dùng mô hình quyền nào. Nó đưa **điểm thực thi chính sách** tới ranh giới thao tác; PDP có thể áp dụng:

- **RBAC (Role-Based Access Control):** vai trò như `READER`, `EDITOR`, `REVIEWER`. Dễ hiểu nhưng riêng vai trò thường thiếu phạm vi tài nguyên.
- **ABAC (Attribute-Based Access Control):** kết hợp role, tenant, phòng ban, thuộc tính resource, thời gian, phân loại dữ liệu và trạng thái nghiệp vụ.
- **Quan hệ chủ thể–tài nguyên (ReBAC):** người dùng là chủ sở hữu, thành viên workspace, người được chia sẻ hoặc cấp phê duyệt trong sơ đồ quan hệ.

Ví dụ: `approveDocument(doc)` yêu cầu reviewer có quyền trong tenant sở hữu tài liệu, khác owner và tài liệu ở trạng thái `SUBMITTED`. Đây là phối hợp **role + tenant + quan hệ + trạng thái**, vượt xa một điều kiện “user có role reviewer”.

Chính sách có thể thay đổi theo tổ chức hoặc thời gian. Cache quyết định quyền phải xác định key đầy đủ (subject, action, resource, version/thuộc tính và tenant), thời hạn ngắn, khả năng vô hiệu hóa khi quyền bị thu hồi và xử lý khi policy service không sẵn sàng. Cache nhầm principal/resource hoặc dùng quyết định cũ có thể trở thành lỗ hổng nghiêm trọng.

## 8. Ví dụ Java 17: AOP proxy tại ranh giới đọc và duyệt tài liệu

Ví dụ chạy được sau dùng **JDK dynamic proxy** để chặn các lời gọi đến `DocumentActions`:

- Annotation `@Guarded(Action.VIEW)` và `@Guarded(Action.APPROVE)` mô tả ý nghĩa bảo vệ của mỗi method.
- `Policy.require` kiểm tra tài liệu tồn tại, cùng tenant, ownership/reviewer và trạng thái.
- `guarded` tạo proxy, tra chính sách trước khi gọi `Service`.
- `Store` cũng kiểm tra quyền **bên trong phương thức `synchronized`, ngay tại lúc đọc hoặc thay đổi dữ liệu**, để minh họa lớp bảo vệ thứ hai chống race giữa lần kiểm tra ở proxy và thao tác thực tế.
- Actor được **gắn sẵn khi tạo service/proxy trong ví dụ**. Trong hệ thống thật, chỉ thành phần đã xác thực và được tin cậy mới được gắn actor; không để request bên ngoài tự chọn một actor bất kỳ.

Lưu mã nguồn sau vào **AuthorizationAspectDemo.java**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class AuthorizationAspectDemo {
    enum Action { VIEW, APPROVE }
    enum State { SUBMITTED, APPROVED }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Guarded { Action value(); }

    record Actor(String id, String tenant, boolean reviewer) {}
    record Document(String id, String tenant, String owner, State state) {}

    static final class Denied extends RuntimeException {
        Denied() { super("Not available"); }
    }

    static final class Policy {
        void require(Actor actor, Action action, Document doc) {
            if (doc == null || !doc.tenant().equals(actor.tenant())) {
                throw new Denied();
            }
            boolean allowed = switch (action) {
                case VIEW -> doc.owner().equals(actor.id()) || actor.reviewer();
                case APPROVE -> actor.reviewer()
                    && !doc.owner().equals(actor.id())
                    && doc.state() == State.SUBMITTED;
            };
            if (!allowed) throw new Denied();
        }
    }

    static final class Store {
        private final Map<String, Document> documents = new HashMap<>();

        synchronized void add(Document doc) { documents.put(doc.id(), doc); }

        synchronized Document snapshot(String id) { return documents.get(id); }

        synchronized String view(Actor actor, String id, Policy policy) {
            Document doc = documents.get(id);
            policy.require(actor, Action.VIEW, doc);
            return "view=" + doc.id() + " status=" + doc.state();
        }

        synchronized String approve(Actor actor, String id, Policy policy) {
            Document doc = documents.get(id);
            policy.require(actor, Action.APPROVE, doc);
            documents.put(id, new Document(doc.id(), doc.tenant(), doc.owner(), State.APPROVED));
            return "approved=" + id;
        }
    }

    interface DocumentActions {
        @Guarded(Action.VIEW) String view(String id);
        @Guarded(Action.APPROVE) String approve(String id);
    }

    static final class Service implements DocumentActions {
        private final Actor actor;
        private final Store store;
        private final Policy policy;

        Service(Actor actor, Store store, Policy policy) {
            this.actor = actor;
            this.store = store;
            this.policy = policy;
        }

        public String view(String id) {
            return store.view(actor, id, policy);
        }

        public String approve(String id) {
            return store.approve(actor, id, policy);
        }
    }

    static DocumentActions guarded(Actor actor, Store store, Policy policy) {
        DocumentActions target = new Service(actor, store, policy);
        return (DocumentActions) Proxy.newProxyInstance(
            DocumentActions.class.getClassLoader(),
            new Class<?>[] { DocumentActions.class },
            (proxy, method, args) -> {
                Guarded rule = method.getAnnotation(Guarded.class);
                if (rule != null) {
                    policy.require(actor, rule.value(), store.snapshot((String) args[0]));
                }
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            });
    }

    static void checkDenied(String label, Runnable command) {
        try {
            command.run();
            System.out.println(label + "=UNEXPECTED_ALLOW");
        } catch (Denied ex) {
            System.out.println(label + "=DENIED");
        }
    }

    public static void main(String[] args) {
        Policy policy = new Policy();
        Store store = new Store();
        store.add(new Document("doc-7", "tenant-a", "alice", State.SUBMITTED));

        DocumentActions alice = guarded(
            new Actor("alice", "tenant-a", false), store, policy);
        DocumentActions bob = guarded(
            new Actor("bob", "tenant-a", true), store, policy);
        DocumentActions eve = guarded(
            new Actor("eve", "tenant-b", true), store, policy);

        System.out.println(alice.view("doc-7"));
        checkDenied("owner_approve", () -> alice.approve("doc-7"));
        System.out.println(bob.approve("doc-7"));
        checkDenied("cross_tenant_view", () -> eve.view("doc-7"));
        System.out.println(bob.view("doc-7"));
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 AuthorizationAspectDemo.java
java AuthorizationAspectDemo
```

Kết quả đã chạy và kiểm chứng:

```text
view=doc-7 status=SUBMITTED
owner_approve=DENIED
approved=doc-7
cross_tenant_view=DENIED
view=doc-7 status=APPROVED
```

Giải thích:

1. `alice` là chủ của `doc-7` trong `tenant-a` nên được xem tài liệu.
2. `alice` không có vai trò reviewer và không thể phê duyệt tài liệu của mình: `DENIED`.
3. `bob` có quyền reviewer trong cùng tenant, không phải owner và tài liệu đang ở `SUBMITTED`, nên được phê duyệt.
4. `eve` có vai trò reviewer nhưng thuộc `tenant-b`, vì vậy ngay cả quyền đọc tài liệu của tenant khác cũng bị từ chối.
5. Sau khi phê duyệt, `bob` đọc lại trạng thái `APPROVED`.

**Giới hạn của demo:** đây là mô hình in-memory trong một JVM; `synchronized` chỉ đồng bộ các luồng dùng **chung instance `Store`**, không đảm bảo nguyên tử giữa nhiều tiến trình hay với database bên ngoài. Một transaction/conditional update thích hợp ở nơi lưu trữ thật mới bảo vệ ranh giới dữ liệu trong môi trường phân tán.

Proxy chỉ áp dụng cho lời gọi đi qua `DocumentActions`; lời gọi trực tiếp đến `Service`/`Store` có thể không qua advice. Demo cố ý đặt guard thứ hai tại `Store` và không cung cấp endpoint cho client bên ngoài. Handler minh họa chỉ xem annotation **trên interface** và chỉ gọi policy đối với method có `@Guarded`; trong thiết kế production có yêu cầu “mọi command đều phải có policy”, hãy **deny method thiếu khai báo** hoặc dùng registry bắt buộc, đồng thời xử lý các phương thức `Object` của proxy theo hợp đồng rõ. Không được coi “thiếu annotation” là “tự động public”.

## 9. Ranh giới kiểm tra và time-of-check/time-of-use

Nếu đọc trạng thái để cấp quyền ở thời điểm `t1`, sau đó dùng trạng thái đó để ghi ở `t2`, các thay đổi đồng thời có thể làm quyết định trở nên lỗi thời:

```text
t1: advice đọc document.status = SUBMITTED -> allow
    |
    +-- worker khác hủy document hoặc thu hồi role
    |
t2: thao tác cập nhật document -> APPROVED
    (đã dùng quyết định dựa trên thông tin cũ)
```

Đây là lớp rủi ro **TOCTOU**. AOP check *trước* lời gọi rất hữu ích để từ chối sớm, nhưng không đủ đảm bảo bất biến phụ thuộc trạng thái được giữ đến commit.

Những kỹ thuật cần cân nhắc:

- **Kiểm tra sát side effect trong cùng transaction/critical section.** Đọc trạng thái, xác minh quyền/điều kiện và thay đổi dữ liệu trong một cơ chế đồng bộ phù hợp.
- **Conditional update:** ví dụ `UPDATE document SET state='APPROVED' WHERE id=? AND tenant_id=? AND state='SUBMITTED' AND version=?`; số dòng bị sửa giúp phát hiện trạng thái đã đổi. Đây chỉ là mảnh SQL minh họa; yêu cầu reviewer/ownership vẫn phải được chứng minh bằng chính sách và dữ liệu đáng tin.
- **Optimistic locking/version:** so sánh version khi ghi, xử lý xung đột thay vì âm thầm ghi đè.
- **Row lock/transaction isolation:** chọn khi điều kiện phải cố định trong một giao dịch và mức cô lập thực sự hỗ trợ.
- **Thu hồi quyền:** nếu policy có thể thay đổi giữa check và commit, cần xác định ranh giới thời gian được phép (đầu thao tác, lúc commit, hay tại cả hai). Một số thao tác nhạy cảm cần đánh giá lại hoặc dùng phiên bản policy ổn định trong transaction.

Dù kiểm tra ở nhiều lớp, phải dùng **cùng ngữ nghĩa chính sách** hoặc chỉ rõ kiểm tra nào là quyền và kiểm tra nào là bất biến. Hai lớp cho kết quả trái nhau thường gây khó vận hành và dễ làm người viết bỏ một lớp.

## 10. Vượt qua proxy, gọi nội bộ và phạm vi weaving

Đây là điểm đặc biệt quan trọng với AOP dùng cho bảo mật.

Một cơ chế **proxy interception** chỉ chặn lời gọi đi qua đối tượng proxy. Ví dụ:

```text
external caller -> proxy -> securedService.approve()    [có advice]
securedService.internalMethod() -> this.approve()       [có thể bỏ qua proxy]
external caller -> rawService.approve()                 [bỏ qua proxy]
```

Lời gọi `this.approve()` bên trong cùng object thường không đi qua proxy của kiểu proxy-based interception. Với các cơ chế weaving khác, tập join point có thể bao gồm nhiều lời gọi nội bộ hơn, nhưng vẫn phải xác minh rõ ngữ nghĩa của công cụ đang dùng. Không suy ra rằng thêm annotation đồng nghĩa mọi đường chạy đều được chặn.

Các đường bypass cần audit:

- Đối tượng raw/implementation bị giữ và gọi trực tiếp.
- Self-invocation, method private/final/static hoặc constructor mà interceptor không hỗ trợ.
- Interface có overload/generic bridge method và metadata không được đọc đúng.
- Batch job, event handler, message consumer, callback, test helper hoặc tác vụ quản trị gọi thẳng vào lớp thấp hơn.
- Truy cập dữ liệu trực tiếp bằng SQL/script mà không đi qua command được bảo vệ.
- Gọi từ module hoặc thư viện khác có thể truy cập public method ngoài đường chuẩn.

Các cách bảo vệ bổ sung:

1. **Đóng đường truy cập implementation** bằng API/module boundary và dependency wiring hợp lý.
2. **Có guard sát dữ liệu/side effect** cho invariant cốt lõi.
3. **Test coverage của interception**, chứng minh từng entry point/overload bắt buộc đi qua guard.
4. **Bảo vệ tài nguyên ở tầng dữ liệu** nếu có nhiều đường đọc/ghi, tenant filter, row-level security hoặc constraint phù hợp.
5. **Fail closed với command chưa khai báo policy**, có cơ chế xét duyệt rõ cho thao tác thực sự public.

Đừng giấu toàn bộ logic cấp quyền trong một aspect vô danh mà không có tài liệu/hợp đồng. Đường đi để biết ai có quyền thực hiện một operation phải dễ truy vết và test.

## 11. Kiểm tra dữ liệu trả về, danh sách và field-level access

Không phải mọi truy cập đều là lệnh ghi. Một lời gọi được phép “xem danh sách” không có nghĩa mọi dòng và cột trong danh sách đều được phép xuất:

- **Row/object-level:** lọc kết quả theo tenant, owner, ACL hoặc phạm vi tổ chức; không phân trang toàn bộ rồi mới bỏ item, vì sẽ làm lộ tổng số/nhịp dữ liệu và tính toán sai pagination.
- **Field-level:** cùng một tài liệu có thể công khai tiêu đề nhưng riêng tư số điện thoại, số tiền hoặc nội dung thẩm định; policy phải xác định trường nào được đọc.
- **Aggregate/report:** tổng hợp dữ liệu đôi khi vẫn tiết lộ một cá nhân nếu nhóm quá nhỏ; cần chính sách phạm vi và ngưỡng.
- **Export/bulk:** xuất nhiều tài nguyên là một thao tác riêng (ví dụ `report.export`), thường cần quyền mạnh hơn thao tác xem một item và có giới hạn số lượng/tốc độ.
- **Query filter:** cần áp quyền sớm trong truy vấn hoặc tầng lưu trữ; một advice chỉ kiểm tra “được gọi search” nhưng trả toàn bộ database không tạo an toàn.

AOP có thể chặn entrypoint xuất dữ liệu và áp dụng chính sách chung, nhưng **lọc bản ghi và trường** vẫn phải thực hiện ở nơi hiểu được dữ liệu, tránh biến interceptor thành một serializer phức tạp không thể chứng minh là an toàn.

## 12. Thực thi bất đồng bộ và ngữ cảnh danh tính

Một thao tác nghiệp vụ có thể đi qua executor, callback, queue hoặc nhiều dịch vụ. Quyết định cấp quyền ở luồng gọi không tự lan truyền sang nơi phát sinh side effect sau đó.

Các tình huống:

**Executor/callback:** `ThreadLocal` trên luồng gọi không tự xuất hiện ở worker. Nếu cần dùng actor trong tác vụ, truyền một snapshot danh tính đã được chuẩn hóa qua cơ chế đáng tin, gắn vào phạm vi thực thi và **restore/clear** khi kết thúc. Không để actor của request cũ lưu trên thread pool.

**Job lên lịch:** tác vụ chạy sau nhiều giờ không nhất thiết còn được phép theo vai trò lúc tạo job. Hãy xác định job chạy theo **service identity** với quyền hạn rõ ràng, hay theo **delegated authority** có thời hạn và phạm vi. Với thao tác nhạy cảm, cần đánh giá lại quyền trước khi thực hiện.

**Queue/message consumer:** message có thể đến từ nguồn không đáng tin. Trường `userId` trong payload không phải principal đã xác thực; consumer cần xác thực nguồn, bảo vệ toàn vẹn metadata được ủy quyền và quyết định quyền tại nơi xử lý.

**Remote calls:** thông tin actor chuyển qua mạng cần giao thức và ranh giới tin cậy. Không coi header `X-Role: admin` do caller tự đặt là bằng chứng được cấp quyền. Dịch vụ nhận phải áp chính sách của mình với principal/credential đã xác thực.

**Async completion:** kiểm tra trước khi submit có thể phù hợp với “quyền được xếp lịch”, nhưng chưa chắc đủ để “quyền thực thi/cập nhật” khi hàng đợi xử lý sau đó. Hãy phân biệt hai quyền và quyết định thời điểm hiệu lực.

Cần định nghĩa rõ **ai là subject của automation**: người tạo lịch, người bấm duyệt, hay tài khoản dịch vụ thay mặt tổ chức. Tránh chuyển mọi tác vụ nền sang một danh tính siêu quyền mà không có ràng buộc hoạt động.

## 13. Lỗi, phản hồi bị từ chối và kiểm toán

Một deny phải ngăn nghiệp vụ chạy và trả trạng thái/exception được phép công khai. Không trả thông điệp chi tiết như “Tài liệu này của tenant X do người Y tạo” cho actor vốn không được biết tài liệu tồn tại.

Phân biệt:

| Tình huống | Ý nghĩa nội bộ | Cách xử lý |
|---|---|---|
| Principal chưa xác thực | Chưa có danh tính tin cậy | Dừng tại ranh giới yêu cầu đăng nhập/xác thực |
| Đã xác thực nhưng không đủ quyền | Authorization deny | Không gọi proceed hoặc side effect |
| Resource không tồn tại/ngoài tenant | Không được công khai sự tồn tại | Có thể trả thông báo trung tính theo hợp đồng |
| Policy store tạm thời không truy cập được | Không thể xác định allow | Fail closed; có cơ chế vận hành/giám sát phù hợp |
| Business invariant thay đổi trong transaction | Conflict hoặc trạng thái không hợp lệ | Không commit effect sai; trả lỗi theo hợp đồng |
| Lỗi xuất audit | Không đồng nhất với lỗi quyền | Chọn chính sách bảo đảm audit và xử lý lỗi riêng |

Nếu có yêu cầu audit mạnh, cần đảm bảo bản ghi **who, action, resource, policy decision, thời điểm và request correlation** đáng tin, không đưa access token, dữ liệu cá nhân nhạy cảm hoặc toàn bộ payload vào log. Với nghiệp vụ cần audit không mất sự kiện, dùng cơ chế transaction/outbox hoặc log chuyên dụng theo hợp đồng; một logger không đồng bộ “best effort” không tạo bảo đảm ghi nhận bất biến.

Không đưa resource ID hoặc user ID cardinality cao làm nhãn metric không giới hạn. Có thể đo tỷ lệ allow/deny theo action chuẩn hóa, tenant class hoặc mã lỗi khi đã được duyệt.

**Bản thân việc phân quyền phải có thể quan sát**, nhưng không làm rò chính thông tin mà nó bảo vệ.

## 14. Kiểm thử đúng boundary, không chỉ kiểm tra hàm policy

Bộ test quan trọng:

1. **Đúng quyền, đúng resource:** allow thao tác hợp lệ và kiểm tra side effect thật.
2. **Sai quyền:** deny khi actor không có action; assert target không được gọi, không tạo dữ liệu, không phát event.
3. **Sai tenant/owner:** thử thay resource ID của tenant khác; deny thống nhất ở mọi entry point.
4. **Trạng thái thay đổi:** sau khi quyết định đầu tiên, concurrent worker chuyển trạng thái; kiểm tra bước commit không ghi sai.
5. **Principal không xác thực/không đầy đủ:** thiếu actor, tenant hoặc thông tin policy; deny.
6. **Phương thức mới chưa khai báo:** kiểm tra fail-closed theo hợp đồng của boundary.
7. **Self-invocation/raw object:** xác định mọi đường có thể bypass proxy; test trực tiếp đường vào thật để thấy guard ở tầng thấp hơn vẫn thực thi.
8. **Mảng/list/report:** chỉ xuất các hàng/trường được phép; kiểm tra count, pagination và export.
9. **Async/queue:** truyền metadata bị giả mạo, identity đã hết hạn hoặc role bị thu hồi; không thực thi với quyền cũ ngoài hợp đồng.
10. **Lỗi policy store:** policy evaluation timeout/exception; không vô tình allow.
11. **Tác dụng phụ của kiểm tra:** denied phải xảy ra **trước** side effect; kiểm tra không vô tình gọi getter/hành vi có side effect.
12. **Audit:** denied, allowed, conflict và error có sự kiện phù hợp; không chứa dữ liệu bí mật.
13. **Concurrency:** nhiều yêu cầu thao tác trên cùng resource; trạng thái cuối và số effect phải đúng. Dùng latch/barrier để tạo interleaving có chủ ý.
14. **Mọi đường vào:** HTTP, CLI, worker, callback, message consumer và đường quản trị đều được kiểm tra theo chính sách đúng.

Tách rõ test **policy** (actor/action/resource/context → decision), **interception** (guard có chặn đúng lời gọi không), **domain invariant** (không thể tạo trạng thái cấm) và **end-to-end** (mọi entry point thật đều bảo vệ). Một unit test trực tiếp gọi `Policy.require` thành công không chứng minh proxy đã được nối vào luồng ứng dụng.

## 15. Những sai lầm phổ biến

| Sai lầm | Hệ quả | Cách khắc phục |
|---|---|---|
| Chỉ kiểm tra role ở controller | Đường vào batch/message/direct invocation bị bỏ qua | Kiểm tra ở business command boundary, có thêm guard sát effect |
| Coi URL được bảo vệ là resource được bảo vệ | Đổi `documentId` để lấy dữ liệu của người khác | Kiểm tra object-level/tenant/owner tại policy |
| Annotation nhưng không có interception thật | Lời gọi đi qua raw instance, self-invocation hoặc method ngoài pointcut | Audit tập join point, wiring và coverage |
| Lời gọi mới không annotation được chạy tự do | Thao tác thiếu policy được ngầm cho phép | Fail closed hoặc yêu cầu explicit public classification |
| Lấy vai trò từ request body | Caller có thể giả role/tenant | Principal chỉ từ trusted authentication boundary |
| Cho phép trước rồi trạng thái bị đổi | TOCTOU cho phép thao tác đáng lẽ không hợp lệ | Kiểm tra sát commit, locking/conditional update |
| Cache quyết định theo mỗi user | Quyền trên tài nguyên/tenant khác bị dùng lẫn | Cache key đầy đủ, TTL/invalidation và kiểm tra thuộc tính |
| Cố giải quyết row-level access bằng check method | Method được phép nhưng dữ liệu trả về vượt phạm vi | Lọc tại tầng query/data và field projection |
| Dùng service account toàn quyền cho mọi job | Job nền vượt quyền người yêu cầu | Service identity/delegation có phạm vi, hạn và audit |
| Log nguyên payload khi deny | Rò dữ liệu qua hệ thống quan sát | Log mã action/quyết định an toàn, redact dữ liệu nhạy cảm |

## 16. Hướng dẫn áp dụng vào thiết kế thật

Một quy trình thực tế:

**Bước 1 – Liệt kê các thao tác cần bảo vệ.** Đọc kịch bản nghiệp vụ thay vì chọn pointcut từ tên getter, setter. Ghi rõ `actor + action + resource + context` cho từng thao tác.

**Bước 2 – Định nghĩa policy tập trung.** Mỗi quyền có tên ổn định, phạm vi tenant, quy tắc ownership/relationship và điều kiện trạng thái. Quy định trường hợp unknown/failure và hành vi deny.

**Bước 3 – Đặt PEP ở command/query boundary.** Dùng annotation, mapping hoặc registry để buộc mỗi thao tác đi qua cơ chế interception. Kiểm tra metadata interface/implementation và các đường có thể bỏ qua.

**Bước 4 – Bảo vệ bất biến ở dữ liệu/side effect.** Với trạng thái thay đổi đồng thời, dùng transaction, conditional update hoặc constraint phù hợp. Không trông chờ một pre-check bảo vệ trạng thái mãi mãi.

**Bước 5 – Kiểm tra cả read/export và background worker.** Kiểm tra row/field-level; xác định principal cho message, cron job và callback; không tin metadata do bên ngoài tự khai.

**Bước 6 – Audit và kiểm thử phủ toàn bộ boundary.** Test allow, deny, tenant crossing, race, policy failure, bypass, async và leak; rà soát khi thêm method hoặc nguồn dữ liệu mới.

### Checklist trước khi triển khai

- [ ] Mọi thao tác nhạy cảm có tên hành động nghiệp vụ, tài nguyên và principal rõ.
- [ ] Actor/tenant/role lấy từ nguồn xác thực đáng tin.
- [ ] Policy có **deny-by-default**, xử lý missing/unknown nhất quán.
- [ ] Mọi entry point bắt buộc đi qua PEP hoặc guard tương đương.
- [ ] Quyền theo đối tượng, owner và tenant được kiểm tra trên resource thật.
- [ ] Side effect có check nhất quán ở ranh giới lưu trữ/transaction.
- [ ] Không có self-invocation/raw-instance route vô tình bypass hàng rào.
- [ ] Kết quả tìm kiếm, export và dữ liệu theo field không vượt phạm vi.
- [ ] Async, queue, job chạy theo identity/ủy quyền hợp lệ.
- [ ] Event audit không lộ bí mật; policy failure không ngầm cấp quyền.
- [ ] Có test concurrency, policy changes, caller denied và các đường vào thật.

## 17. Kết luận

**Một kiểm tra quyền có giá trị khi nó bảo vệ đúng hành động nghiệp vụ và mọi đường có thể kích hoạt hành động đó.** AOP hữu ích để biểu diễn và áp dụng chính sách tại các ranh giới thao tác có ý nghĩa, nhưng interceptor không thay thế việc lấy principal đáng tin, xác minh resource/tenant thật, xử lý điều kiện trạng thái ngay khi ghi và bảo vệ các đường gọi có thể bypass. Khi kết hợp **business action boundary + policy quyết định rõ + enforcement sát side effect + kiểm thử phủ đường vào**, hệ thống mới có thể giữ đúng quyền kể cả khi kiến trúc có nhiều lớp và nhiều luồng thực thi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-002">Liên kết danh tính và ngữ cảnh quyền với lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Liên kết danh tính và ngữ cảnh quyền với lời gọi trong AOP

## 1. Bài toán

Lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**) cho phép một **aspect** áp dụng mối quan tâm xuyên suốt như kiểm soát truy cập vào các **join point** do **pointcut** lựa chọn. Khi một lời gọi được chặn, **advice** có thể xác định người gọi, hành động, tài nguyên và chính sách quyền trước khi chuyển quyền điều khiển sang nghiệp vụ.

Chủ đề **“Liên kết danh tính và ngữ cảnh quyền với lời gọi”** đòi hỏi hiểu một điều: **quyết định quyền thuộc về một lần gọi cụ thể, với danh tính đã xác thực và tài nguyên cụ thể tại thời điểm liên quan**. Không thể chỉ hỏi “method này có cần role ADMIN không?” mà bỏ qua tenant, quyền sở hữu tài nguyên, ủy quyền và vòng đời ngữ cảnh.

Ví dụ, phương thức publish("doc-1") có thể cho phép Alice, từ chối Bob và cũng từ chối Alice nếu “doc-1” chuyển sang tenant khác. Việc một method được đánh dấu là “cần quyền publish” mới xác định **action**; principal và resource vẫn phải được kiểm tra.

Mô hình khái niệm:

~~~text
Trusted entry point
  -> xác minh danh tính caller
  -> tạo invocation context đáng tin cậy
  -> Authorization aspect
       -> lấy subject + action + resource + environment
       -> kiểm tra policy trước side effect
       -> DENY hoặc proceed đúng một lần
  -> đóng/khôi phục scope context
~~~

Không có một phương thức truyền context duy nhất cho mọi kiến trúc. Điều quan trọng là **nguồn danh tính đáng tin, scope chính xác, ngữ cảnh không bị rò và authorization không bị bỏ qua**.

## 2. Những khái niệm phải phân biệt

**Authentication (xác thực)**: xác minh caller thật sự là ai, qua token, chứng chỉ hoặc phương thức xác thực phù hợp. Kết quả là danh tính đã được tin cậy theo chính sách.

**Authorization (phân quyền)**: quyết định subject đã xác thực có được thực hiện action cụ thể trên resource cụ thể theo policy hiện tại không.

**Principal/subject**: chủ thể cần xét quyền, có thể là người dùng, service identity hoặc một subject được ủy quyền hợp lệ.

**Invocation context**: dữ liệu gắn với một occurrence lời gọi, thường có principal, tenant, permissions/scope, correlation ID và các thuộc tính cần thiết. Nó không đồng nhất với HTTP request hay một thread cố định.

**Join point**: điểm thực thi mà cơ chế AOP có thể can thiệp, chẳng hạn method-call hoặc method-execution. Những join point hỗ trợ được phụ thuộc vào kiểu weaving, proxy hay interceptor.

**Pointcut**: điều kiện chọn các join point cần kiểm tra quyền; ví dụ các method đại diện cho use case nhạy cảm.

**Advice**: mã chạy trước/sau/quanh join point; với phân quyền chủ động, logic kiểm tra thường phải xảy ra **trước proceed()**.

**Policy decision**: ALLOW hoặc DENY sau khi áp dụng luật lên subject, action, resource và bối cảnh. Một context có principal không chứng minh là được phép.

| Câu hỏi | Thành phần trả lời | Ví dụ |
| --- | --- | --- |
| Ai đang gọi? | Xác thực | Alice, service-X |
| Muốn làm gì? | Operation/action metadata | document.publish |
| Làm trên cái gì? | Resource resolver | doc-1 |
| Phạm vi nào? | Context + resource metadata | tenant-a, team-a |
| Có được phép? | Policy evaluator | ALLOW/DENY |
| Gọi có đi qua kiểm tra? | Pointcut + AOP interception | advice thực sự chạy |
| Context còn hợp lệ? | Scope/expiry/revalidation | token expiry, revoke |

Điểm dễ nhầm: **đúng role** chưa chắc **đúng tài nguyên**, và **đúng tài nguyên** tại lúc kiểm tra chưa chắc còn đúng lúc commit nếu có thay đổi đồng thời.

## 3. Bắt đầu từ ranh giới tin cậy

Client có thể gửi subject, roles, tenant hoặc isAdmin trong payload, nhưng các trường đó chỉ là **claim từ nguồn không tin cậy**. Nếu advice tin ngay vào chúng, caller có thể tự nâng quyền.

Một đường xử lý tốt thường là:

1. Entry point xác thực bằng chứng danh tính.
2. Thông tin subject và scope được suy ra từ **nguồn được tin cậy**, có xét expiry và trạng thái phiên.
3. Tạo invocation context bất biến hoặc không thể sửa tùy tiện trong vòng đời đang xử lý.
4. Aspect đọc context và các tham số đã được kiểm tra cơ bản, rồi hỏi policy logic.
5. Với operation được phép, mới gọi nghiệp vụ.
6. Scope của invocation được tháo/khôi phục cả khi thành công lẫn khi ném ngoại lệ.

Không lấy quyền từ tên method một cách tự động; annotation mô tả action, không tạo credential. Không coi một correlation ID là bằng chứng xác thực. Khi danh tính được upstream service chuyển tiếp, cần xác minh upstream, định dạng/nguồn ủy quyền và trust boundary tương ứng.

Một service chạy định kỳ không có “người dùng hiện tại” nên phải dùng **service/job principal** rõ ràng, thay vì gán giả tên một người dùng đặc quyền.

## 4. Gắn quyền với đúng occurrence của lời gọi

Một ngữ cảnh khái niệm có thể là:

~~~text
InvocationAccessContext:
    authenticated_actor = alice
    effective_subject = alice
    action = document.publish
    target_resource = doc-1
    trusted_tenant = tenant-a
    permission_scope = document.publish
    correlation_id = req-42
    authorization_expiry = <time>
~~~

Đây là dữ liệu minh họa; không phải tất cả trường đều nên log. Khi đổi effective subject theo delegation, vẫn phải giữ actor đã xác thực.

Một invocation không nên lấy một instance mutable currentUser được chia sẻ cho mọi request. Hai request đồng thời có thể ghi đè nhau, làm Alice bị đánh giá theo quyền Bob hoặc ngược lại. Ngay cả context lưu theo thread cũng không tự theo được executor, callback hay lời gọi qua một thread khác.

**Một lời gọi, một scope xác định**: xác định lúc tạo, cách truy xuất và thời điểm kết thúc. Với nested invocation, phải restore ngữ cảnh cha; với async, phải tạo scope ở nơi task thực sự chạy.

## 5. Quy tắc viết authorization advice

Giả mã chung:

~~~text
around protectedOperation:
    context = requireTrustedContext()
    action = resolveActionFromMethodContract()
    resource = resolveResourceFromInvocationArguments()
    decision = policy.evaluate(context, action, resource)

    if decision != ALLOW:
        auditSanitizedDenial()
        throw AccessDenied

    return proceedExactlyOnce()
~~~

Cần chú ý:

- **Deny by default** nếu context không có, principal không hợp lệ, action không rõ, resource không xác định hoặc policy không cho phép.
- Không để lỗi kết nối policy server ngầm biến thành ALLOW; fail-closed cho thao tác cần bảo vệ.
- Không gọi proceed() trước policy check nếu có thể gây side effect.
- Không gọi proceed() hai lần vì side effect có thể bị lặp.
- Không nuốt exception nghiệp vụ hay thay kết quả bằng success giả.
- Không tự động log args/return vì chứa dữ liệu nhạy cảm.
- Không giả định proxy chặn mọi lời gọi, đặc biệt self-invocation, direct target access hoặc đường vào khác.
- Nếu method trả đối tượng biểu thị lỗi, chính sách phân quyền vẫn được thực hiện **trước** khi gọi; business result là một khái niệm khác authorization decision.

Một security aspect nên phối hợp với **business invariants**. Nếu bảo mật phụ thuộc hoàn toàn vào proxy nhưng code khác dễ dàng gọi target trực tiếp, thiết kế phải chặn đường bypass hoặc kiểm tra thêm tại domain/storage boundary.

## 6. Kết hợp permission, tenant và quyền trên tài nguyên

Ba mô hình thông dụng:

**RBAC (role-based access control):** người dùng có role, role ánh xạ permission. Phù hợp việc định nghĩa trách nhiệm nhưng không đủ nếu từng tài liệu có chủ sở hữu/tenant riêng.

**ABAC (attribute-based access control):** xét thuộc tính subject, resource, action và môi trường, như trạng thái tài liệu, tổ chức, nhãn nhạy cảm hoặc thời gian.

**Quan hệ tài nguyên:** cho phép nếu principal thuộc team sở hữu tài liệu hoặc được chia sẻ quyền hợp lệ. Thường kết hợp với RBAC/ABAC.

Ví dụ chính sách:

~~~text
ALLOW document.publish WHEN
    subject.authenticated = true
    AND "document.publish" in subject.permissions
    AND subject.tenant = resource.tenant
    AND subject.team in resource.publisherTeams
    AND resource.status = DRAFT
~~~

Cần kiểm tra metadata tài nguyên từ **nguồn có thẩm quyền**. Không coi chuỗi tenantId do client gửi là bằng chứng rằng resource thật thuộc tenant đó. Nếu resource không tồn tại hoặc bị ẩn đối với caller, chính sách phản hồi cần tránh vô tình tiết lộ sự tồn tại.

Một thao tác batch trên nhiều tài nguyên phải quy định rõ **all-or-nothing** hay partial success; quyền đối với tài nguyên thứ nhất không cho phép xử lý luôn tất cả phần tử còn lại.

## 7. Ba cách vận chuyển context

### 7.1. Truyền tham số tường minh

~~~java
// Chỉ là khai báo minh họa; AccessContext và Receipt phải được định nghĩa.
Receipt publish(AccessContext context, String documentId);
~~~

Ưu điểm: phụ thuộc vào identity hiện rõ, dễ kiểm thử, thuận tiện khi truyền sang task. Nhưng nếu caller không tin cậy tự khởi tạo AccessContext thì vẫn bị giả mạo; lớp xây dựng context và entry point phải được quản lý đúng trust boundary.

### 7.2. Scope theo thread

Có thể gắn context đã xác thực trong ThreadLocal cho lời gọi đồng bộ. Khi một method trả hoặc ném ngoại lệ, cần **remove/restore trong finally**. Với nested scope, ghi nhớ previous để khôi phục chính xác.

Không có gì trong ThreadLocal bảo đảm principal do bên đáng tin tạo ra. Nó chỉ là carrier. Trong cùng process, code được phép gọi setter không kiểm soát có thể tự đặt danh tính; cần hạn chế entry points và ranh giới có thẩm quyền.

### 7.3. Scope của asynchronous task

Với executor hoặc callback, tạo immutable context snapshot từ nguồn đáng tin rồi truyền qua tham số hoặc một cơ chế propagation được thiết kế cho task. Worker gắn scope riêng và đóng tại worker:

~~~text
Caller:
  verifiedContext = trustedSource()
  snapshot = immutableSubset(verifiedContext)
  submit(task, snapshot)
  closeCallerScope()

Worker:
  attachTaskScope(snapshot)
  try:
      recheckPolicyIfRequired()
      performWork()
  finally:
      detachTaskScope()
~~~

Đây là giả mã, không phải chương trình độc lập. Không giả định ThreadLocal tự theo thread mới. Không truyền scope có thread affinity từ caller rồi nhờ callback thread khác đóng. Task dài hạn cần chính sách expiry/revocation riêng, không giữ quyền vô hạn theo token lúc submit.

## 8. Ví dụ Java 17: ràng buộc subject, action và resource tại invocation

Ví dụ chạy được dùng **JDK dynamic proxy** để mô phỏng AOP ở ranh giới interface. Chương trình giả định hai principal đã được xác thực trước; nó minh họa permission checks, tenant/team restrictions và cleanup context, **không triển khai hệ thống xác thực hoàn chỉnh**.

Tạo tệp **InvocationAuthorizationDemo.java**:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class InvocationAuthorizationDemo {
    record Identity(String subject, Set<String> permissions,
                    Set<String> teams) {
        Identity {
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("subject required");
            }
            permissions = Set.copyOf(Objects.requireNonNull(permissions));
            teams = Set.copyOf(Objects.requireNonNull(teams));
        }
    }

    static final class Scope {
        private static final ThreadLocal<Identity> ACTIVE =
                new ThreadLocal<>();

        static Identity current() {
            return ACTIVE.get();
        }

        static <T> T call(Identity identity, Supplier<T> operation) {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(operation);
            Identity previous = ACTIVE.get();
            ACTIVE.set(identity);
            try {
                return operation.get();
            } finally {
                if (previous == null) {
                    ACTIVE.remove();
                } else {
                    ACTIVE.set(previous);
                }
            }
        }
    }

    interface DocumentService {
        String publish(String documentId);
    }

    static final class DocumentServiceImpl implements DocumentService {
        @Override
        public String publish(String documentId) {
            return "PUBLISHED:" + documentId;
        }
    }

    static final class AccessAdvice implements InvocationHandler {
        private final DocumentService target;
        private final Map<String, String> ownerTeam;

        AccessAdvice(DocumentService target,
                     Map<String, String> ownerTeam) {
            this.target = Objects.requireNonNull(target);
            this.ownerTeam = Map.copyOf(ownerTeam);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            if (method.getName().equals("publish")) {
                Identity identity = Scope.current();
                String documentId = (String) args[0];
                String team = documentId == null
                        ? null : ownerTeam.get(documentId);

                boolean allow = identity != null
                        && team != null
                        && identity.permissions().contains("document.publish")
                        && identity.teams().contains(team);

                if (!allow) {
                    System.out.println("DENY action=document.publish");
                    throw new SecurityException("access denied");
                }
                System.out.println("ALLOW action=document.publish");
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException wrapped) {
                throw wrapped.getCause();
            }
        }
    }

    public static void main(String[] args) {
        Identity alice = new Identity("alice",
                Set.of("document.publish"), Set.of("team-a"));
        Identity bob = new Identity("bob",
                Set.of("document.read"), Set.of("team-b"));

        DocumentService service = (DocumentService) Proxy.newProxyInstance(
                DocumentService.class.getClassLoader(),
                new Class<?>[] { DocumentService.class },
                new AccessAdvice(new DocumentServiceImpl(),
                        Map.of("doc-1", "team-a", "doc-2", "team-b"))
        );

        String result = Scope.call(alice,
                () -> service.publish("doc-1"));
        System.out.println("result=" + result);

        try {
            Scope.call(bob, () -> service.publish("doc-1"));
        } catch (SecurityException ex) {
            System.out.println("bob_denied=true");
        }

        try {
            Scope.call(alice, () -> service.publish("doc-2"));
        } catch (SecurityException ex) {
            System.out.println("wrong_team_denied=true");
        }

        System.out.println("scope_cleared="
                + (Scope.current() == null));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac InvocationAuthorizationDemo.java
java InvocationAuthorizationDemo
~~~

Kết quả stdout:

~~~text
ALLOW action=document.publish
result=PUBLISHED:doc-1
DENY action=document.publish
bob_denied=true
DENY action=document.publish
wrong_team_denied=true
scope_cleared=true
~~~

### Vì sao mỗi lần gọi có kết quả như vậy?

- **Alice + doc-1:** có permission publish, thuộc team-a và doc-1 do team-a sở hữu. Advice cho phép, target chạy đúng một lần.
- **Bob + doc-1:** Bob chỉ có quyền read, nên bị từ chối trước khi thực hiện nghiệp vụ.
- **Alice + doc-2:** có permission publish nhưng không thuộc team-b, nên bị từ chối theo resource scope.
- **Sau mọi lời gọi:** Scope.call khôi phục context trước đó hoặc xóa ThreadLocal trong finally, tránh giữ principal trong thread sau khi scope kết thúc.

**Giới hạn của demo:** Identity và Scope là mã học tập chạy trong một process; một caller có quyền thực thi mã Java cùng trust boundary có thể tự tạo Identity và gọi Scope.call. Trong hệ thống thực, nguồn tạo principal, đối tượng target và API attach scope phải được quản lý tại ranh giới xác thực. Map sở hữu tài liệu trong demo là dữ liệu bất biến giả định đáng tin, còn dữ liệu thật cần được tra cứu với tenant isolation và chính sách nhất quán. Proxy chỉ bảo vệ lời gọi đi qua nó: việc gọi trực tiếp DocumentServiceImpl có thể bỏ qua advice. ThreadLocal phù hợp demo đồng bộ, không tự bảo vệ task nền. Hệ thống thực nên dùng structured audit/logger và một policy service/logic được kiểm tra đầy đủ.

## 9. Lời gọi lồng nhau và scope stacking

Một invocation có thể gọi invocation khác. Nếu cần tạm dùng một context được ủy quyền, điều đúng là **khôi phục context của lời gọi cha**:

~~~text
outer invocation: subject Alice
    inner invocation: delegated subject Service-X
    inner ends -> restore Alice
outer ends -> remove context
~~~

Dùng remove() trong inner mà không restore sẽ khiến outer không còn principal. Ngược lại, quên remove() sau outer sẽ rò context sang tác vụ kế tiếp trên cùng pooled thread.

Nhưng thao tác push/pop principal không tự tạo quyền ủy nhiệm. Phải xác minh delegation scope, actor, effective subject, expiry và resource. **Không cho phép đổi principal sang admin chỉ bằng việc gọi một helper**.

## 10. Quyền ủy nhiệm và confused deputy

Phân biệt:

- **Authenticated actor:** thực thể đã xác thực thật ở entry point (ví dụ report-service).
- **Effective subject:** chủ thể thay mặt được dùng để đánh giá policy (ví dụ Alice).
- **Delegation:** bằng chứng hợp lệ rằng actor được phép thực hiện action cụ thể cho subject.
- **Resource scope:** tài nguyên được phép, có thể hẹp hơn quyền mặc định của actor.
- **Audit identity:** vẫn lưu actor thực và chủ thể được thay mặt, nếu phù hợp chính sách bảo mật.

Ví dụ:

~~~text
actor = report-service
effective_subject = alice
delegated_action = report.export
delegated_resource = report-17
delegation_expiry = 2026-10-20T03:00:00Z
~~~

Không dùng service account có quyền cao để tự động phê duyệt mọi yêu cầu của caller; điều này tạo nguy cơ **confused deputy**: thành phần đặc quyền bị dùng để làm việc mà caller không có quyền. Delegated capability phải hạn chế action, resource, tenant và thời hạn.

## 11. Quyền có thể thay đổi giữa check và use

**TOCTOU (time-of-check to time-of-use)** xuất hiện khi một kiểm tra ALLOW trở nên lỗi thời trước lúc có side effect:

~~~text
T1: advice xác minh Alice được publish doc-1
T2: quyền bị thu hồi hoặc doc-1 đổi owner
T3: nghiệp vụ publish dựa trên ALLOW cũ
~~~

Tùy hệ thống, có thể cần:

1. Đọc trạng thái tài nguyên có thẩm quyền và ghi trong cùng transaction/snapshot thích hợp.
2. Kiểm tra version khi update/commit, không chỉ kiểm tra lúc entry.
3. Re-authorize trước side effect sau một khoảng chờ dài.
4. Giới hạn thời gian hiệu lực hoặc policy version của authorization decision.
5. Đặt quy tắc rõ cho task đã lên lịch trước khi quyền bị thu hồi.
6. Phân biệt quyền **submit job** và quyền **thực hiện job**.

Around advice kiểm tra đầu method **không tự giải quyết tất cả race condition** ở database hoặc trong workflow phân tán. Bất biến bảo mật của tài nguyên cần được giữ ở điểm cập nhật có thẩm quyền khi cần.

## 12. Asynchronous workflow và vòng đời quyền

Khi một method trả Future/CompletionStage hoặc chỉ gửi việc vào queue, invocation trả về trước task hoàn tất. Một context được gắn với caller thread thường đã đóng trước khi worker chạy.

**Không nên:**

- Dùng ThreadLocal của caller trong executor mà không truyền ngữ cảnh.
- Capture request/session mutable rồi giữ nhiều giờ.
- Giữ scope của caller mở cho đến callback chạy trên thread khác.
- Tin snapshot permissions mãi mãi dù token hết hạn hoặc policy đã thay đổi.
- Dùng service admin rộng hơn phạm vi cần cho một task.

**Nên:**

- Chụp immutable context snapshot từ nguồn tin cậy, chỉ giữ các trường thật cần.
- Chuyển qua task/callback một cách tường minh.
- Attach/detach ở worker trong try/finally.
- Định nghĩa thời điểm policy check: lúc submit, lúc execute và khi commit tùy rủi ro.
- Áp dụng least privilege, expiry, revocation và audit actor/subject thích hợp.
- Kiểm tra cancellation và timeout: một handle đã timeout không chứng minh task nền đã dừng và cleanup xong.

Chú ý một CompletionStage hoàn tất exceptionally sau khi method return: quyết định **được phép gửi việc** không đồng nghĩa công việc **đã thành công**. Authorization log và business outcome log phải phân biệt.

## 13. Policy decision với cache và dữ liệu thiếu

Nếu authorization service, dữ liệu principal, permission hoặc resource metadata không sẵn sàng, nên có trạng thái **không thể xác nhận quyền** được xử lý theo fail-closed cho thao tác bảo vệ. Không mở rộng quyền mặc định chỉ để giảm latency.

Cache authorization decision cần cân nhắc khóa theo subject, action, resource, tenant, policy version và các thuộc tính liên quan. Một cache key chỉ chứa resource ID là nguy hiểm: ALLOW của Alice có thể bị tái sử dụng sai cho Bob. Cache phải có TTL và invalidation khi quyền bị thu hồi.

Khi policy phụ thuộc resource status, tenant hay owner, snapshot ở cache phải đủ mới theo yêu cầu an toàn. **Authentication cache** và **authorization cache** có mục tiêu/vòng đời khác nhau; token hợp lệ không bảo đảm quyền tài nguyên vẫn còn.

## 14. Lỗi thường gặp

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Tin role/tenant trong payload chưa xác minh | Caller tự nâng quyền | Chỉ lấy từ trusted authentication/identity source |
| Chỉ kiểm tra principal != null | Người dùng nào cũng truy cập được | Kiểm tra action + resource + policy |
| Chỉ check permission mà bỏ tenant | Cross-tenant access | Resource-scoped authorization |
| Context ở biến global mutable | Lẫn danh tính giữa request | Per-invocation scope |
| Không cleanup ThreadLocal | Thread pool giữ principal cũ | try/finally, restore/remove |
| Giả định ThreadLocal truyền qua async | Worker thiếu hoặc sai context | Truyền snapshot, attach ở worker |
| Proxy có đường bypass | Lời gọi chạy không qua advice | Kiểm tra mọi entry path; invariant ở domain |
| Chấp nhận ALLOW cũ vô thời hạn | Thu hồi quyền không có hiệu lực | Expiry/revalidation/version check |
| Policy service lỗi thì cho phép | Quyền không xác định thành ALLOW | Fail-closed phù hợp |
| Đổi principal thành admin nội bộ | Confused deputy | Delegation có scope và audit |
| Batch kiểm tra một phần tử | Truy cập trái phép phần tử khác | Check mọi resource |
| Log token và context đầy đủ | Rò credential, dữ liệu nhạy cảm | Audit allowlist, redaction, access control |

## 15. Audit quyết định quyền mà không lộ bí mật

Aspect có thể ghi nhận một quyết định, nhưng cần phân biệt:

- **Authorization decision:** ALLOW hoặc DENY tại thời điểm đánh giá.
- **Invocation execution outcome:** business method chạy thành công hay lỗi.
- **Durable business change:** thay đổi đã commit hay chưa, có thể xảy ra muộn hơn.

Một quyết định ALLOW **không phải** sự kiện “tài liệu đã xuất bản”. Nếu nghiệp vụ sau đó thất bại, audit cần mô tả đúng hai mốc.

Ví dụ sự kiện đã làm sạch:

~~~json
{
  "event": "authorization_decision",
  "operation": "document.publish",
  "outcome": "deny",
  "reason_code": "RESOURCE_SCOPE_MISMATCH",
  "correlation_id": "req-42"
}
~~~

Không ghi password, bearer token, private key, authorization headers hoặc toàn bộ request object. Subject ID cũng có thể là dữ liệu cá nhân; cần kiểm soát truy cập, retention và cách mask phù hợp. Khi audit có yêu cầu bắt buộc về tính đầy đủ, phải thiết kế lưu trữ và nhất quán riêng; advice gọi logger thông thường không tự tạo bảo đảm giao dịch.

## 16. Thứ tự advice và những đường gọi khác nhau

Một call chain có thể là:

~~~text
TracingAdvice
   -> AuthorizationAdvice
       -> TransactionAdvice
           -> businessOperation
~~~

Kiểm tra trước TransactionAdvice có thể tránh mở transaction cho một lệnh bị từ chối. Nhưng thứ tự thực tế phụ thuộc cấu hình và cơ chế AOP. Nếu retry advice nằm ngoài authorization, mỗi attempt có thể được xét quyền; nếu authorization ở ngoài retry, quyền có thể chỉ xét một lần. Với operation có độ trễ dài hoặc quyền có thể thay đổi, cần lựa chọn phù hợp và kiểm thử.

Những câu hỏi nên đặt:

- Pointcut chọn đúng method có side effect hay bỏ sót overload?
- Lời gọi nội bộ cùng object có đi qua proxy không?
- Có endpoint batch, job, message listener hay CLI gọi target trực tiếp không?
- Annotation đặt trên interface có được tìm thấy trên implementation trong cơ chế hiện tại không?
- Với quyền nhạy cảm, bất biến nào phải được kiểm tra gần storage commit?
- Một policy denial có bị retry vô hạn bởi lớp retry khác không?
- Code phản hồi lỗi có vô tình tiết lộ tài nguyên không tồn tại hay thuộc tenant nào không?

Thiết kế pointcut cần đi kèm test coverage, không chỉ dựa vào tên class hay annotation.

## 17. Kiểm thử những trường hợp có ý nghĩa

| Kịch bản | Kỳ vọng |
| --- | --- |
| Principal hợp lệ, đúng action và resource | Target được gọi đúng một lần |
| Principal thiếu quyền | DENY, target không được gọi |
| Đúng quyền nhưng sai tenant/owner | DENY, target không được gọi |
| Không có context | DENY |
| Context hết hạn/bị thu hồi | DENY hoặc re-authorize theo chính sách |
| Policy service lỗi | Không vô tình ALLOW |
| Nghiệp vụ ném exception | Advice giữ nguyên exception, cleanup scope |
| Hai caller đồng thời khác principal | Context không lẫn |
| Nested invocation có delegated subject | Restore context cha |
| Thread pool được tái sử dụng | Không rò principal cũ |
| Task async bắt đầu sau caller return | Snapshot đúng và worker scope riêng |
| Resource đổi owner giữa check và update | Không bỏ qua chính sách chống TOCTOU |
| Batch có resource bị cấm | Không cấp quyền cho cả batch sai |
| Direct call bypass proxy | Có biện pháp bảo vệ đường gọi |
| Input có forged role/tenant | Không được nâng thành trusted context |
| Access decision được log | Không chứa token hoặc dữ liệu bí mật |

Một kiểm thử đặc biệt hữu ích là spy đếm target invocation. Trong case DENY, count phải bằng **0**; ALLOW bình thường phải bằng **1**. Ngoài unit test advice, nên kiểm thử tích hợp với đường vào thật và nhiều thread để phát hiện bypass, mất context hoặc rò context.

## 18. Quy trình triển khai

**Bước 1. Xác định trust boundary:** danh tính được xác thực bởi ai? Module nào có quyền tạo/gắn context và nhận delegation?

**Bước 2. Liệt kê protected operations:** mỗi action tương ứng một nghiệp vụ và resource resolver rõ ràng; đánh dấu các side effects nhạy cảm.

**Bước 3. Thiết kế authorization model:** subject, permissions, tenant/resource metadata, trạng thái, scope ủy quyền và những điều kiện thời gian.

**Bước 4. Chọn context transport:** tham số tường minh khi phù hợp; thread scope có restore/remove cho xử lý đồng bộ; task-scoped immutable snapshot cho async.

**Bước 5. Đặt advice đúng thứ tự:** authorization trước side effect, proceed đúng một lần; phối hợp retry/transaction/audit theo ngữ nghĩa.

**Bước 6. Đảm bảo enforcement đầy đủ:** kiểm tra proxy/weaving và mọi entry point; giữ kiểm tra domain/storage phù hợp nơi cần bảo vệ invariant.

**Bước 7. Thiết kế revoke, expiry và TOCTOU:** policy cache không tồn tại vô hạn, re-check tại mốc phù hợp, đặc biệt khi workflow kéo dài.

**Bước 8. Kiểm thử behavior và vận hành:** allow/deny, nested, concurrency, thread reuse, async, direct call, failure của policy, audit dữ liệu nhạy cảm.

## 19. Ghi nhớ

- Danh tính phải đến từ **nguồn xác thực đáng tin**, không từ tham số caller tự khai.
- Context phải ràng buộc với **một invocation**, gồm principal, action và resource cụ thể.
- AOP giúp thực thi kiểm tra nhất quán, nhưng phải xác minh **mọi đường gọi đều được bảo vệ**.
- **Deny by default** khi không chứng minh được quyền; policy error không tự thành ALLOW.
- Với quyền trên tài nguyên, **permission không thay thế tenant/owner check**.
- Scope phải có **vòng đời rõ ràng** và cleanup cả khi exception; async cần context của riêng task.
- Delegation phải giữ **actor thật, effective subject và giới hạn ủy quyền**, tránh confused deputy.
- Quyền có thể hết hạn hoặc bị thu hồi; authorization ở method entry không tự khắc phục race ở commit.
- Logging ALLOW/DENY phải phân biệt với business outcome và bảo vệ dữ liệu nhạy cảm.

**Khi danh tính và quyền được liên kết chính xác với từng lời gọi**, aspect có thể bảo vệ nhiều use case một cách thống nhất. Tính đúng đắn đến từ sự kết hợp giữa nguồn context đáng tin, chính sách dựa trên tài nguyên, vòng đời invocation chặt chẽ và điểm enforcement khó bị bỏ qua.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-003">Phân biệt kiểm tra quyền tổng quát và quyền trên từng đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kiểm tra quyền tổng quát và quyền trên từng đối tượng bằng AOP

## 1. Một người được dùng chức năng chưa chắc được thao tác trên mọi dữ liệu

Trong kiểm soát truy cập, một người dùng có thể có quyền **đọc tài liệu**, nhưng không được phép đọc **mọi tài liệu** của hệ thống. Có hai lớp quyết định khác nhau:

- **Kiểm tra quyền tổng quát** (*action-level authorization*): chủ thể có quyền thực hiện **loại hành động** này không? Chẳng hạn Alice có quyền DOCUMENT_READ để sử dụng chức năng đọc tài liệu.
- **Kiểm tra quyền trên từng đối tượng** (*object/resource-level authorization*): chủ thể có quyền thực hiện **hành động ấy trên tài nguyên cụ thể** không? Chẳng hạn Alice được đọc tài liệu mình sở hữu hoặc tài liệu được chia sẻ trong tenant hiện tại, nhưng không được đọc tài liệu riêng tư của Bob.

Aspect-Oriented Programming (AOP) cho phép thực hiện các kiểm tra xuyên suốt tại những ranh giới đã chọn bằng advice. Tuy nhiên, chỉ nhìn vào tên phương thức hoặc sự hiện diện của quyền DOCUMENT_READ **không đủ để kết luận** người dùng được đọc tài liệu có ID bất kỳ.

Ví dụ: Alice có quyền sử dụng chức năng xem đơn hàng, rồi thay ID đơn hàng của mình thành ID đơn của Bob. Nếu hệ thống chỉ kiểm tra ORDER_READ rồi trả dữ liệu dựa trên ID, Alice có thể đọc thông tin không thuộc quyền của mình. Đây là lỗi *Broken Object Level Authorization* (BOLA), thường thể hiện dưới dạng *Insecure Direct Object Reference* (IDOR).

Bài học này giải thích hai lớp quyết định, cách gắn với AOP, cách xác định tài nguyên thật, vai trò của kiểm tra tại tầng dữ liệu, và những trường hợp dễ tạo đường vượt quyền.

## 2. Quyền tổng quát và quyền trên tài nguyên khác nhau thế nào?

### 2.1. Kiểm tra quyền tổng quát

Một chính sách tổng quát có thể yêu cầu:

- DOCUMENT_READ: quyền gọi thao tác đọc tài liệu.
- DOCUMENT_EDIT: quyền dùng tính năng chỉnh sửa tài liệu.
- INVOICE_APPROVE: quyền dùng chức năng duyệt hóa đơn.
- REPORT_EXPORT: quyền yêu cầu xuất báo cáo.

Quyết định ở cấp này dựa trên danh tính đã xác thực và các permission, capability hoặc thuộc tính chủ thể đáng tin. Nếu người dùng không có DOCUMENT_READ, một advice có thể từ chối trước khi truy xuất tài liệu.

Quyền tổng quát **không đồng nghĩa hoàn toàn với role-based access control (RBAC)**. Role có thể cung cấp các permission, nhưng hệ thống cũng có thể cấp permission trực tiếp, giới hạn theo scope hoặc điều kiện khác. Tương tự, user có role ADMIN không mặc định có quyền với mọi tenant hay mọi trạng thái tài nguyên; phạm vi admin phải được định nghĩa rõ.

### 2.2. Kiểm tra quyền trên từng đối tượng

Một quyết định object-level thông thường xét:

| Thành phần | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Subject | Alice trong tenant T1 | Ai yêu cầu |
| Action | READ, EDIT hoặc DELETE | Định làm gì |
| Resource | Tài liệu D-103 của Bob | Đối tượng thực bị tác động |
| Resource attributes | Owner, tenant, trạng thái, mức chia sẻ | Tính chất thực của đối tượng |
| Context | Thời điểm, nhóm, mục đích hoặc điều kiện khác | Điều kiện áp dụng chính sách |
| Decision | ALLOW hoặc DENY | Kết quả kiểm tra |

Các mô hình thường dùng là attribute-based access control (ABAC), relationship-based access control (ReBAC), access control list (ACL), ownership hoặc sự kết hợp giữa chúng. Kiểm tra object-level **không thay thế** kiểm tra quyền tổng quát; cả hai cùng đóng góp vào quyết định cuối.

Ví dụ chính sách của hệ thống tài liệu giả định:

> Chỉ chủ thể có DOCUMENT_READ mới được dùng chức năng đọc. Tài liệu phải thuộc cùng tenant; trong tenant ấy, chủ sở hữu hoặc người được phép xem tài liệu chia sẻ mới được nhận nội dung.

Bảng kết quả:

| DOCUMENT_READ | Tenant khớp | Có quan hệ được phép | Kết quả |
| --- | --- | --- | --- |
| Không | Có | Có | DENY vì thiếu quyền tổng quát |
| Có | Không | Có hoặc không | DENY vì khác tenant |
| Có | Có | Không | DENY ở đối tượng |
| Có | Có | Có | ALLOW |
| Có | Có | Tài nguyên không tồn tại | Không được cấp quyền |

Quy tắc này chỉ là ví dụ; ứng dụng thực cần xác định phạm vi quyền, các trường hợp ngoại lệ và mốc hiệu lực của chính sách theo domain.

## 3. Xác thực và nguồn dữ liệu đáng tin

**Authentication** xác định người đang thực hiện yêu cầu; **authorization** quyết định người đó được phép làm gì.

Một security context đáng tin thường chứa principal/user ID, tenant hiện tại và tập permission đã được xác minh. Không được sử dụng giá trị client tự gửi như tenantId, ownerId hoặc role để chứng minh quyền. Người dùng có thể thay những tham số ấy bằng chính công cụ gửi yêu cầu.

Đối tượng cần được xác định từ nguồn dữ liệu đáng tin. Ví dụ, nếu client gửi documentId=d3 và ownerId=alice, nhưng bản ghi d3 thực sự thuộc Bob, ứng dụng phải quyết định dựa trên owner của **bản ghi d3**, không dựa trên ownerId do client khai.

Các thuộc tính quan trọng ở cấp đối tượng có thể gồm tenant, owner, nhóm được chia sẻ, trạng thái tài liệu, đơn vị quản lý, mức độ bảo mật, assignment hoặc quyền được cấp riêng. Một permission tổng quát không tự mang theo những thuộc tính này.

Quyết định truy cập nên có cấu trúc rõ:

~~~text
authorize(subject, action, resource, context) -> ALLOW | DENY
~~~

Nếu không xác định được subject, resource hoặc policy, mặc định **DENY** theo nguyên tắc *fail closed*. Lỗi đọc quyền hoặc thiếu dữ liệu không được tự chuyển thành ALLOW.

## 4. AOP xử lý phần nào của kiểm soát truy cập?

### 4.1. Các khái niệm cần biết

- **Join point:** vị trí can thiệp, chẳng hạn khi một phương thức được gọi/thực thi.
- **Pointcut:** tiêu chí chọn những join point cần bảo vệ.
- **Advice:** logic kiểm tra quyền chạy trước hoặc quanh join point.
- **Aspect:** tập hợp pointcut và advice mang cùng trách nhiệm.
- **Weaving/interception:** cơ chế kết nối aspect với mã ứng dụng; những lời gọi nằm ngoài cơ chế này sẽ không được advice quan sát.

Một *around advice* cho quyền tổng quát có thể kiểm tra permission trước khi gọi phương thức gốc. Nếu DENY, nó từ chối và **không gọi proceed()**. Nếu ALLOW, nó chuyển điều khiển tới phương thức gốc theo đúng hợp đồng.

Việc kiểm tra object-level đôi khi cũng có thể được đóng gói trong advice, nhưng advice phải có đủ thông tin về **tài nguyên thực**, không chỉ resource ID hoặc tên phương thức.

### 4.2. Luồng hai lớp kiểm tra

~~~text
Người gọi yêu cầu đọc D-103
       |
       v
Xác thực danh tính và lấy security context đáng tin
       |
       v
AOP: kiểm tra quyền tổng quát DOCUMENT_READ
       |
       +--- DENY ---> không gọi phương thức đích
       |
       v
Tải đúng tài nguyên D-103 trong phạm vi tin cậy
       |
       +--- không tồn tại / không được tiết lộ ---> từ chối
       |
       v
Kiểm tra tenant, owner, ACL, trạng thái và điều kiện khác
       |
       +--- DENY ---> không tiết lộ nội dung
       |
       v
Sử dụng hoặc trả kết quả đúng phạm vi được phép
~~~

Một cách tổ chức hiệu quả là dùng AOP ở ranh giới chức năng để kiểm tra permission tổng quát, còn việc kiểm tra object-level được thực hiện nơi đã tải được đối tượng thật và có thể bảo đảm điều kiện cập nhật. Nếu áp dụng tất cả thông qua aspect, policy service của advice vẫn phải lấy dữ liệu chính xác và giải quyết nguy cơ thay đổi trạng thái sau lúc kiểm tra.

Chức năng READ, EDIT, APPROVE, DELETE không nhất thiết dùng chung điều kiện object-level. Cùng một tài liệu, người dùng có thể được xem nhưng không được sửa; với invoice, chỉ được duyệt khi được phân công và tài liệu còn trạng thái PENDING.

## 5. Ví dụ Java 17 chạy độc lập bằng dynamic proxy

Chương trình minh họa cơ chế can thiệp của AOP nói chung bằng **Java dynamic proxy**. Proxy đọc một annotation do chính ví dụ định nghĩa rồi kiểm tra DOCUMENT_READ trước khi gọi service. Service sau đó tải bản ghi thật, kiểm tra tenant và điều kiện owner/visibility. Không cần thư viện AOP bên ngoài để chạy ví dụ.

Giả định dữ liệu:

- d1 là tài liệu riêng của Alice thuộc T1.
- d2 là tài liệu được chia sẻ trong T1 nhưng của Bob.
- d3 là tài liệu riêng của Bob trong T1.
- d4 được chia sẻ trong T2, không được phép đọc từ T1.

Tạo tệp **AuthorizationAopDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public class AuthorizationAopDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface RequiresPermission {
        String value();
    }

    record UserContext(String username, String tenant, Set<String> permissions) {
        UserContext {
            Objects.requireNonNull(username);
            Objects.requireNonNull(tenant);
            permissions = Set.copyOf(permissions);
        }
    }

    record Document(
            String id,
            String tenant,
            String owner,
            boolean publicWithinTenant,
            String body) {}

    interface DocumentApi {
        @RequiresPermission("DOCUMENT_READ")
        String read(UserContext user, String documentId);
    }

    static final class DocumentService implements DocumentApi {
        private final Map<String, Document> store = Map.of(
                "d1", new Document("d1", "T1", "alice", false, "Alice draft"),
                "d2", new Document("d2", "T1", "bob", true, "Team guide"),
                "d3", new Document("d3", "T1", "bob", false, "Bob private"),
                "d4", new Document("d4", "T2", "carol", true, "Other tenant")
        );

        @Override
        public String read(UserContext user, String documentId) {
            Document document = store.get(documentId);
            if (document == null || !document.tenant().equals(user.tenant())) {
                throw new NoSuchElementException("document unavailable");
            }
            boolean owner = document.owner().equals(user.username());
            if (!owner && !document.publicWithinTenant()) {
                throw new SecurityException("object denied");
            }
            return document.body();
        }
    }

    static DocumentApi withGeneralAuthorization(DocumentApi target) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            RequiresPermission policy =
                    method.getAnnotation(RequiresPermission.class);
            if (policy != null) {
                UserContext user = (UserContext) args[0];
                if (user == null || !user.permissions().contains(policy.value())) {
                    throw new SecurityException("general denied");
                }
            }

            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        };

        return (DocumentApi) Proxy.newProxyInstance(
                DocumentApi.class.getClassLoader(),
                new Class<?>[] { DocumentApi.class },
                handler
        );
    }

    static void attempt(
            String label, DocumentApi api, UserContext user, String id) {
        try {
            System.out.println(label + "=" + api.read(user, id));
        } catch (SecurityException | NoSuchElementException ex) {
            System.out.println(label + "=" + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        DocumentApi api = withGeneralAuthorization(new DocumentService());
        UserContext alice = new UserContext(
                "alice", "T1", Set.of("DOCUMENT_READ"));
        UserContext noPermission = new UserContext(
                "alice", "T1", Set.of());

        attempt("own", api, alice, "d1");
        attempt("shared", api, alice, "d2");
        attempt("private-of-other", api, alice, "d3");
        attempt("other-tenant", api, alice, "d4");
        attempt("no-general-permission", api, noPermission, "d1");
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 AuthorizationAopDemo.java
java AuthorizationAopDemo
~~~

**Stdout theo thứ tự xác định:**

~~~text
own=Alice draft
shared=Team guide
private-of-other=object denied
other-tenant=document unavailable
no-general-permission=general denied
~~~

Đọc kết quả:

1. Alice có quyền tổng quát, là chủ của d1 và cùng tenant: ALLOW.
2. d2 không phải của Alice nhưng được chia sẻ trong tenant T1: ALLOW.
3. d3 của Bob và riêng tư: **có quyền tổng quát nhưng thiếu quyền trên đối tượng**, DENY.
4. d4 được chia sẻ ở T2 nhưng Alice ở T1: DENY vì khác tenant.
5. Alice sở hữu d1 nhưng không có DOCUMENT_READ: **thiếu quyền tổng quát**, DENY.

Proxy kiểm tra annotation trên phương thức của interface. Đây là ví dụ một cơ chế interception cụ thể; nó không tự bảo vệ constructor, các phương thức không đi qua proxy, hoặc lời gọi trực tiếp đến DocumentService. Ở hệ thống thật, cần đảm bảo mọi đường vào đáng tin đều được bảo vệ, và chính sách object-level vẫn hiện diện tại ranh giới truy cập tài nguyên.

**Giới hạn của ví dụ:** UserContext được truyền trực tiếp để người học dễ tái lập tình huống. Trong hệ thống thực, context phải đến từ thành phần xác thực đáng tin chứ không phải nội dung do người gọi tự quyết định. Map trong bộ nhớ không có transaction, persistence hoặc cập nhật quyền đồng thời.

## 6. Vì sao chỉ kiểm tra quyền tổng quát vẫn có thể lộ dữ liệu?

### 6.1. IDOR và thay ID

Giả sử người dùng có quyền đọc tài liệu rồi gửi hai yêu cầu:

~~~text
GET /documents/d1
GET /documents/d3
~~~

Nếu cả hai chỉ kiểm tra DOCUMENT_READ, hệ thống có thể trả d3 của người khác. ID có dạng UUID ngẫu nhiên cũng không thay thế authorization: người dùng có thể biết ID thông qua liên kết, log hoặc một nguồn dữ liệu khác. **Khó đoán ID khác với có quyền truy cập ID đó.**

### 6.2. Tin vào thuộc tính tự khai

Một yêu cầu chứa documentId=d3 và ownerId=alice có thể là dữ liệu giả mạo; bản ghi d3 vẫn thuộc Bob. Phải tải hoặc kiểm tra điều kiện truy vấn trên đối tượng đáng tin, rồi quyết định từ thuộc tính thật.

### 6.3. Coi quyền trên đối tượng là một thuộc tính duy nhất

Ownership có thể chưa đủ. Người sở hữu hóa đơn chưa chắc được tự duyệt hóa đơn; người từng được chia sẻ tài liệu có thể đã bị thu hồi quyền; quyền xem trong tenant có thể bị hạn chế bởi trạng thái tài liệu. Policy cần xét đúng action và trạng thái hiện tại, không chỉ owner ID.

### 6.4. Chỉ bảo vệ nút bấm trên giao diện

Ẩn nút EDIT, vô hiệu hóa liên kết hoặc lọc menu ở phía client giúp trải nghiệm người dùng nhưng không thay thế enforcement. Client có thể gọi trực tiếp endpoint hoặc đường nội bộ khác. Quyết định cuối phải diễn ra ở ranh giới phía hệ thống đáng tin.

## 7. Chọn nơi kiểm tra object-level

### 7.1. Advice tải resource trước khi proceed()

Một advice có thể đọc resource ID từ tham số, gọi policy service để nạp resource và kiểm tra quyền rồi cho phép proceed(). Điều này chỉ đúng khi có cách đảm bảo quyết định còn hiệu lực khi phương thức thực sự sử dụng đối tượng.

Vấn đề là resource có thể bị chuyển chủ, khóa hoặc thu hồi chia sẻ **sau khi advice kiểm tra** nhưng **trước khi service cập nhật**. Đây là nguy cơ *time-of-check to time-of-use* (TOCTOU).

### 7.2. Service kiểm tra ngay trên resource được sử dụng

Mẫu trong ví dụ đặt object-level check trong service sau khi tải tài liệu. Với thao tác ghi, cần phối hợp kiểm tra với transaction, phiên bản bản ghi hoặc câu lệnh cập nhật có điều kiện để tránh race. Một policy service riêng có thể giúp dùng chung quy tắc giữa nhiều use case mà không sao chép các điều kiện tenant/owner.

### 7.3. Ràng buộc ngay trong truy vấn hoặc tầng dữ liệu

Thay vì tải một resource bất kỳ rồi lọc, hệ thống có thể truy vấn trong tenant và phạm vi được phép; hoặc sử dụng chính sách ở tầng lưu trữ khi phù hợp. Điều này đặc biệt hữu ích với truy vấn danh sách, pagination và cập nhật có điều kiện. Tuy nhiên, chính sách ở tầng dữ liệu không mặc nhiên thay thế mọi quy tắc nghiệp vụ cần nhiều nguồn thông tin.

**Nguyên tắc:** điều kiện quyền phải được đánh giá trên dữ liệu có thẩm quyền, và quyết định phải còn hợp lệ tại thời điểm resource được sử dụng.

## 8. Quyền trên từng phần tử trong danh sách và batch

Một điểm dễ bỏ sót là list/export/search. Nếu người dùng có REPORT_EXPORT, điều đó mới cho phép gọi tính năng, không có nghĩa toàn bộ bản ghi trong báo cáo đều thuộc phạm vi được xem.

Cần kiểm tra hoặc ràng buộc **từng tài nguyên trong tập kết quả**. Ưu tiên lọc tại nguồn theo policy để số lượng, phân trang, thống kê và tổng hợp cũng đúng phạm vi. Trả tổng số bản ghi bị cấm, dù không trả nội dung, vẫn có thể lộ thông tin.

Với bulk edit/delete có nhiều resource ID, chính sách cần quyết định xử lý toàn bộ cùng thành công hay cho phép thành công một phần, và kiểm tra từng resource. Một lần kiểm tra quyền tổng quát trước vòng lặp không đủ.

Các luồng xử lý kéo dài như export nền, đăng ký stream hoặc tác vụ bất đồng bộ cũng cần xác định **lúc nào quyền được đánh giá và có cần kiểm tra lại không**. Việc được phép gửi một tác vụ không tự đảm bảo quyền vẫn còn hợp lệ tại thời điểm worker thực thi.

## 9. Tương tác với các aspect khác

AOP thường có nhiều aspect cùng áp dụng: authorization, cache, retry, tracing, transaction và xử lý lỗi. Thứ tự của chúng có thể thay đổi kết quả bảo mật:

- **Cache:** nếu cache trả dữ liệu trước khi kiểm tra object-level, người dùng có thể nhận dữ liệu không được phép. Khóa cache có thể cần scope/tenant/phạm vi quyền và chính sách invalidation khi quyền thay đổi; không coi cache là nguồn phân quyền.
- **Transaction:** nếu kiểm tra quyền trước một transaction nhưng thay đổi resource trong transaction sau, state có thể thay đổi giữa hai mốc. Thiết kế cần bảo đảm đúng mức nhất quán.
- **Retry:** mỗi lần cố gắng xử lý có thể chịu điều kiện quyền mới; không giả định quyền được đánh giá một lần mãi mãi.
- **Async:** permission tại bước gửi không tự chứng minh worker được phép đọc hay sửa đối tượng ở thời điểm khác.
- **Tracing/logging:** chỉ ghi dữ liệu cần thiết cho audit và chẩn đoán; tránh log nội dung resource, token hoặc thông tin quyền nhạy cảm.

Nếu cơ chế proxy không can thiệp self-invocation hoặc lời gọi trực tiếp đến đối tượng gốc, một phương thức được bảo vệ bằng advice có thể bị gọi qua đường khác. Vì vậy, cần kiểm thử chính các đường thực thi được dùng khi triển khai, bảo đảm ranh giới tin cậy không thể bị vượt qua.

## 10. Fail closed, lỗi và ghi nhận truy cập

Khi thiếu subject đáng tin, policy chưa xác định, resource không tải được hoặc authorization service thất bại, mặc định DENY theo hợp đồng bảo mật. Không nuốt lỗi kiểm tra quyền rồi cho phép truy cập.

Có thể tách phản hồi thành không xác thực, thiếu quyền thao tác, thiếu quyền trên resource, và resource không tồn tại. Đôi khi trả “không tìm thấy” cho tài nguyên ở tenant khác để tránh tiết lộ sự tồn tại, như ví dụ Java; đây là một lựa chọn của API contract, không thay thế kiểm tra quyền và audit.

Audit nên ghi loại hành động, định danh chủ thể đã kiểm chứng, quyết định ALLOW/DENY, lý do được phép công khai và thông tin tương quan ở mức cần thiết. Không ghi token hoặc toàn bộ nội dung dữ liệu nhạy cảm. Việc ghi audit bắt buộc theo pháp lý/nghiệp vụ có thể có hợp đồng độ bền riêng, cần phân biệt với log chẩn đoán best-effort.

## 11. Kiểm thử hai cấp quyền

| Trường hợp | Kỳ vọng |
| --- | --- |
| Thiếu quyền tổng quát dù là owner | DENY trước thao tác |
| Có quyền tổng quát và là owner trong tenant | ALLOW nếu các điều kiện khác hợp lệ |
| Có quyền tổng quát nhưng object thuộc người khác, riêng tư | DENY |
| Object được chia sẻ hợp lệ trong cùng tenant | ALLOW theo policy |
| Object ở tenant khác, dù đánh dấu công khai trong tenant đó | DENY |
| Client sửa ownerId hoặc tenantId trong request | Không vượt được quyền từ dữ liệu thật |
| Resource không tồn tại | Không trả dữ liệu, phản hồi đúng contract |
| Cùng role nhưng khác trạng thái tài liệu | Quyết định theo trạng thái và action |
| Quyền bị thu hồi trước khi ghi | Không được cập nhật trái phép |
| Danh sách chứa cả resource được phép và bị cấm | Chỉ trả dữ liệu trong phạm vi |
| Có đường gọi trực tiếp không qua proxy | Không tạo lối vòng qua kiểm tra bắt buộc |
| Cache hit từ một người dùng khác | Không tiết lộ dữ liệu vì thiếu ràng buộc quyền |

Ngoài happy path, nên ưu tiên các phép thử **DENY** để phát hiện BOLA/IDOR. Với thao tác thay đổi dữ liệu, kiểm thử race condition và transaction giúp xác minh quyết định còn hiệu lực tại lúc cập nhật. Khi dùng AOP runtime, cần kiểm tra pointcut, thứ tự advice và khả năng interception theo đường gọi thực tế.

## 12. Sai lầm thường gặp và hướng sửa

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Có role VIEWER thì đọc được mọi tài liệu | Lộ object không thuộc phạm vi | Kết hợp permission và object policy |
| So sánh ownerId do client gửi | Người dùng có thể giả mạo | Dùng thuộc tính từ resource đáng tin |
| Chỉ kiểm tra ở UI | Direct request vượt qua kiểm tra | Enforce tại server/resource boundary |
| Tin ID khó đoán là đủ an toàn | ID bị lộ vẫn bị dùng để xem dữ liệu | Kiểm tra từng resource |
| Kiểm tra quyền trước rồi cập nhật khi state đã đổi | TOCTOU | Kiểm tra và cập nhật nhất quán trong transaction/version |
| Coi mọi admin được vượt tenant | Vượt phạm vi quản trị | Định nghĩa rõ tenant scope và ngoại lệ |
| Chỉ kiểm tra một lần cho cả danh sách | Lẫn bản ghi trái phép | Filter/check từng resource |
| Cache trả nội dung trước policy | Bypass object authorization | Thiết kế thứ tự advice và phân vùng cache đúng |
| Nuốt lỗi authorization và tiếp tục | Biến DENY thành ALLOW | Fail closed |
| Coi annotation là tự bảo vệ mọi đường gọi | Self-call/direct call có thể bypass | Kiểm thử phạm vi weaving/interception |

## 13. Checklist khi thiết kế aspect kiểm soát truy cập

1. Xác định subject, action, resource và ngữ cảnh đáng tin cho từng operation.
2. Phân biệt permission tổng quát với policy xét resource cụ thể.
3. Lấy tenant/owner/state từ dữ liệu có thẩm quyền; không tin trường client tự khai.
4. Đặt điểm enforcement ở nơi thực sự có thể chặn mọi đường chạm tới dữ liệu.
5. Bảo đảm policy object-level được đánh giá trên resource đang được sử dụng, với mức nhất quán phù hợp.
6. Quy định DENY/NOT_FOUND, mức độ tiết lộ và audit.
7. Kiểm soát thứ tự authorization với cache, retry, transaction và async.
8. Bảo vệ truy vấn danh sách, export và bulk theo từng resource.
9. Viết kiểm thử quyền bị từ chối, IDOR, cross-tenant, race và bypass proxy.
10. Xem lại chính sách mỗi khi thêm một loại action, thay đổi model chia sẻ hoặc mở rộng ranh giới dữ liệu.

**Điểm cốt lõi:** kiểm tra quyền tổng quát trả lời **“người này có được dùng loại thao tác này?”**; kiểm tra quyền trên từng đối tượng trả lời **“người này có được dùng thao tác đó trên tài nguyên cụ thể trong trạng thái hiện tại?”**. AOP giúp tổ chức enforcement xuyên suốt, còn quyết định an toàn phải dựa trên ngữ cảnh danh tính đáng tin và dữ liệu thực của resource.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-004">Không suy diễn quyền chỉ từ tên phương thức hoặc kiểu tham số</a>

<details>
<summary>Xem chi tiết</summary>

# Không suy diễn quyền chỉ từ tên phương thức hoặc kiểu tham số trong AOP

## 1. Vấn đề bảo mật cần giải quyết

**Aspect-Oriented Programming (AOP)** thường được sử dụng để đặt kiểm soát truy cập tại những điểm thực thi dùng chung. Một *pointcut* xác định các *join point* cần can thiệp; *advice* kiểm tra quyền trước khi cho thao tác gốc chạy. Cách làm này hữu ích khi nhiều phương thức cùng cần một cơ chế xác thực và phân quyền nhất quán.

Tuy nhiên, có một sai lầm nguy hiểm: **coi tên phương thức hoặc kiểu tham số là bằng chứng người gọi có quyền thực hiện thao tác**. Những chuỗi tên như `getUser`, `readPublic`, `adminUpdate`, `deleteById` và những kiểu như `AdminRequest`, `UserId`, `DocumentDto` chỉ mô tả cấu trúc phần mềm. Chúng không chứng minh danh tính đã được xác thực, vai trò hợp lệ, quyền trên tài nguyên đích hay điều kiện sử dụng quyền tại thời điểm thực thi.

Ví dụ, hai lời gọi cùng có dạng `readDocument(String documentId)` có thể truy cập hai tài liệu thuộc các chủ sở hữu hoặc tenant khác nhau. Trong cùng một phương thức, tài liệu A có thể được phép xem còn tài liệu B phải bị từ chối. Ngược lại, một phương thức tên `getSecret` có thể trả dữ liệu được phép truy cập đối với một chủ thể đã được cấp quyền. Quyết định phụ thuộc **người thực hiện – hành động – đối tượng – ngữ cảnh**, không phụ thuộc vẻ ngoài của tên hàm.

Nguyên tắc chính:

> **Tên/kiểu là thông tin để xác định điểm cần bảo vệ hoặc cách trích xuất dữ liệu theo một hợp đồng đã kiểm chứng; chúng không tự trở thành nguồn cấp quyền.**

Trong bài này, “suy diễn quyền” nghĩa là lấy một tín hiệu cấu trúc không đáng tin về mặt quyền hạn rồi kết luận `allow` / `deny`. Dùng tên phương thức để **định vị một chính sách khai báo rõ** là hợp lệ; dùng quy tắc kiểu “method bắt đầu bằng `get` thì ai cũng được gọi” để **quyết định được phép hay không** là không an toàn.

## 2. Cấu trúc một quyết định phân quyền đúng

Một quyết định kiểm soát truy cập thường cần tối thiểu bốn yếu tố:

| Yếu tố | Câu hỏi | Ví dụ |
| --- | --- | --- |
| **Subject / principal** | Ai đang yêu cầu? Danh tính có được xác thực không? | Người dùng `alice` thuộc `tenant-1` |
| **Action / permission** | Người đó muốn làm thao tác gì? | `document.read`, `document.publish` |
| **Resource / object** | Thao tác tác động tới đối tượng cụ thể nào? | Tài liệu `doc-7` do `alice` sở hữu |
| **Context / environment** | Điều kiện gì phải đúng tại thời điểm kiểm tra? | Tenant trùng khớp, xác thực tăng cường, trạng thái tài liệu, thời hạn quyền |

Có thể biểu diễn chính sách:

`allow = policy(principal, action, resource, context)`

Các đầu vào phải có nguồn gốc đáng tin phù hợp:

- **Principal** đến từ ranh giới xác thực được tin cậy; tên người dùng, vai trò, tenant hoặc cờ xác thực tăng cường do client tự truyền lên không phải bằng chứng.
- **Action** được ánh xạ tường minh từ operation được bảo vệ sang quyền nghiệp vụ; không đoán từ prefix/suffix của tên phương thức.
- **Resource** phải được định danh theo hợp đồng API, rồi lấy thuộc tính quyền hạn từ nguồn dữ liệu có thẩm quyền. `String documentId` chỉ là khóa tra cứu, không thể hiện người gọi có quyền.
- **Context** gồm điều kiện phiên, chính sách tổ chức, thời gian, trạng thái, phạm vi tenant hay bằng chứng xác thực tăng cường; phải được bảo vệ khỏi giả mạo và cập nhật theo cơ chế phù hợp.

Một quy tắc cho phép có thể là:

`canPublish = authenticated && sameTenant && isOwner && strongAuthenticationVerified`

Hệ thống cũng cần quy định điều gì xảy ra khi thuộc tính không có, policy chưa được khai báo, backend phân quyền lỗi hoặc người gọi không có danh tính. Mặc định an toàn là **deny by default / fail closed** tại ranh giới có yêu cầu quyền. Lỗi trong quá trình lấy dữ liệu chính sách không được tự chuyển thành cho phép.

### Xác thực, phân quyền và xác nhận dữ liệu không phải một việc

- **Authentication:** xác định và kiểm chứng danh tính người gọi.
- **Authorization:** quyết định danh tính đó có thể thực hiện *hành động cụ thể trên tài nguyên cụ thể* hay không.
- **Validation:** dữ liệu đầu vào có đúng định dạng/phạm vi hay không, ví dụ ID không rỗng, số tiền dương, payload hợp lệ.

Một tham số có kiểu `AdminRequest` có thể vượt qua validation mà vẫn không được authorization. Ngược lại, role `ADMIN` trong một token cũng cần được xác minh chữ ký, issuer, audience, hiệu lực, phạm vi và điều kiện sử dụng theo hệ thống xác thực; nhìn thấy chuỗi `ADMIN` không đủ để tin.

## 3. Những suy diễn sai rất thường gặp

### 3.1 Đoán quyền theo tên phương thức

Ví dụ minh họa **phản ví dụ không an toàn**:

~~~java
// KHÔNG AN TOÀN: chỉ để minh họa sai lầm về chính sách.
boolean canInvokeByName(String methodName) {
    return methodName.startsWith("get")
        || methodName.startsWith("list")
        || methodName.contains("Public");
}
~~~

Một phương thức tên `getPayroll` hoặc `listCredentials` có thể truy cập dữ liệu nhạy cảm; tên bắt đầu bằng `get` không làm nó công khai. Một phương thức tên `deleteExpiredCache` có thể là công việc nền hợp lệ không cần quyền người dùng ở tầng áp dụng; tên bắt đầu bằng `delete` cũng chưa đủ để xác định quy tắc chính xác. Đổi tên phương thức hoặc tạo alias có thể vô tình thay đổi kết quả bảo mật nếu policy được suy từ tên.

Không giải quyết được vấn đề bằng cách viết thêm hàng trăm regex trên tên: cùng tên có overload, tên có thể thay đổi khi refactor, wrapper/decorator có thể dùng tên khác, và resource-level authorization vẫn vắng mặt.

### 3.2 Đoán quyền theo kiểu tham số

Ví dụ **phản ví dụ không an toàn**:

~~~java
// KHÔNG AN TOÀN: Class<?> không nói ai được cấp quyền.
boolean isPrivilegedByParameterType(Class<?> type) {
    return type.getSimpleName().equals("AdminRequest")
        || type.getSimpleName().endsWith("TrustedInput");
}
~~~

Một caller có thể tạo một `AdminRequest` từ JSON hoặc gọi trực tiếp constructor nếu lớp đó không bị kiểm soát; lớp dữ liệu mang tên quyền lực không trở thành chứng cứ người gọi đã được cấp đặc quyền. `String`, `UUID`, `long`, `DocumentDto` có thể chứa ID tài nguyên thuộc bất kỳ ai. Kiểu generic tại runtime còn có thể bị xóa thông tin tham số (`type erasure`), nên suy quyền từ thông tin reflection không đầy đủ càng thiếu cơ sở.

Tương tự, tên tham số `ownerId` không chứng minh ID đó là chủ thể đã xác thực; nhận `userId` từ request rồi so với một field khác có thể mở lỗ hổng giả mạo danh tính. Chỉ lấy tài nguyên, chủ sở hữu và tenant từ nguồn đã xác minh.

### 3.3 Đoán quyền từ annotation hoặc tên role một cách hời hợt

Annotation như `@RequiresPermission("document.read")` có thể là **khóa tra chính sách tường minh**. Nhưng annotation tự nó không chứng minh principal được phép. Advice còn phải thực hiện đánh giá policy trên principal và resource thực tế.

Role-based access control (RBAC) cho phép diễn đạt các quyền theo vai trò; attribute-based access control (ABAC) thêm thuộc tính chủ thể, tài nguyên và bối cảnh. Trong nhiều trường hợp có thể kết hợp RBAC và ABAC. Tuy nhiên, cả hai mô hình đều yêu cầu **nguồn gốc tin cậy của thuộc tính** và chính sách được đánh giá tại đúng thời điểm.

## 4. Vai trò thực sự của pointcut và advice trong kiểm soát truy cập

Một thiết kế AOP nên phân biệt rõ hai bước:

1. **Chọn điểm cần bảo vệ:** pointcut nhận diện join point dựa trên cấu trúc phần mềm, annotation hoặc một danh sách operation khai báo cụ thể.
2. **Quyết định quyền:** advice lấy principal từ context tin cậy, xác định action theo policy binding, tìm resource và đánh giá quyền. Chỉ khi được phép mới thực thi hành vi gốc.

Tổng quát:

~~~mermaid
flowchart TD
    A[Yêu cầu vào hệ thống] --> B[Xác thực tại ranh giới tin cậy]
    B --> C[Điểm thực thi qua pointcut AOP]
    C --> D[Resolve action từ mapping tường minh]
    D --> E[Resolve resource và ngữ cảnh thực]
    E --> F{Policy cho phép?}
    F -->|Không hoặc thiếu dữ liệu| G[Deny, không thực thi nghiệp vụ]
    F -->|Có| H[Thực thi nghiệp vụ đúng một lần]
    H --> I[Trả kết quả hoặc lỗi gốc]
~~~

**Trách nhiệm của advice:**

- Từ chối khi operation cần bảo vệ không có policy binding, hoặc mapping bị lỗi.
- Không lấy principal “đã xác thực” từ tham số do caller tự chọn.
- Không tự tin tưởng các thuộc tính quyền do DTO/HTTP body truyền vào.
- Đảm bảo `proceed` hoặc lời gọi nghiệp vụ gốc chỉ diễn ra sau khi được cấp quyền và không bị gọi lặp.
- Bảo toàn kết quả và lỗi nghiệp vụ khi đã được phép thực hiện.
- Phân biệt lỗi từ chối quyền với lỗi nghiệp vụ; không trả thông tin nhạy cảm qua thông điệp exception.
- Không để telemetry/audit biến một quyết định deny thành allow; xử lý lỗi quan sát theo chính sách riêng.

**Một hạn chế cần ghi rõ:** AOP là cách *áp dụng* chính sách tại join point, không tự cung cấp identity provider, kho chính sách, transactional consistency hoặc boundary bảo mật cho mọi đường gọi. Nếu có lời gọi đi vòng điểm chặn, cơ chế này không bảo vệ được đường đó.

## 5. Ví dụ Java 17+ chạy độc lập: action khai báo rõ, quyền phụ thuộc tài nguyên

Ví dụ minh họa một giao diện `Documents` được bảo vệ bằng JDK dynamic proxy và một annotation `@Guarded`. Annotation dùng để **chỉ ra khóa action**; advice không suy “được phép” từ tên `read`/`publish` hay kiểu tham số `String`. Policy đọc thông tin tài liệu có thật, principal hiện tại và điều kiện xác thực tăng cường.

Chương trình dùng context giả lập bằng `ThreadLocal` **chỉ để mô phỏng một ranh giới xác thực đã hoàn tất**. Trong hệ thống thật, principal phải được thiết lập bởi thành phần xác thực đáng tin, không được cho code không đáng tin tùy ý gọi hàm `withUser` để giả mạo danh tính. Proxy ở ví dụ cũng chưa thay thế được bảo vệ mọi đường truy cập kho dữ liệu; phần sau sẽ giải thích cách bảo vệ boundary thực tế.

**Tệp: `ExplicitPolicyAspectDemo.java`**

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ExplicitPolicyAspectDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Guarded {
        String value();
    }

    record User(String id, String tenant, boolean mfa, Set<String> roles) {}
    record Document(String id, String tenant, String owner, String body) {}

    static final class AccessDenied extends RuntimeException {
        AccessDenied() {
            super("Access denied");
        }
    }

    interface Documents {
        @Guarded("document.read")
        String read(String documentId);

        @Guarded("document.publish")
        String publish(String documentId);
    }

    static final class Repository {
        private final Map<String, Document> data = Map.of(
            "doc-7", new Document(
                "doc-7", "tenant-A", "alice", "draft contents"
            )
        );

        Document find(String id) {
            return data.get(id);
        }
    }

    static final class DocumentOperations implements Documents {
        private final Repository repository;

        DocumentOperations(Repository repository) {
            this.repository = repository;
        }

        @Override
        public String read(String id) {
            return repository.find(id).body();
        }

        @Override
        public String publish(String id) {
            // Thao tác ghi được biểu diễn bằng kết quả xác định trong demo.
            return "published " + repository.find(id).id();
        }
    }

    static final class Policy {
        static boolean isAllowed(User user, String action, Document doc) {
            if (!user.tenant().equals(doc.tenant())) {
                return false;
            }

            return switch (action) {
                case "document.read" ->
                    user.id().equals(doc.owner())
                    || user.roles().contains("reviewer");

                case "document.publish" ->
                    user.id().equals(doc.owner()) && user.mfa();

                default -> false;
            };
        }
    }

    // Trong demo, hàm withUser ở main giả lập đầu vào đã xác thực.
    // Production phải lấy identity từ ranh giới xác thực đáng tin.
    private static final ThreadLocal<User> CURRENT = new ThreadLocal<>();

    static void withUser(User user, Runnable work) {
        User old = CURRENT.get();
        try {
            CURRENT.set(user);
            work.run();
        } finally {
            if (old == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(old);
            }
        }
    }

    static Documents secured(Documents target, Repository repository) {
        InvocationHandler advice = (Object proxy, Method method,
                                    Object[] args) -> {
            if (method.getDeclaringClass() == Object.class) {
                // Hành vi an toàn của chính đối tượng proxy.
                return switch (method.getName()) {
                    case "toString" -> "SecuredDocumentsProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException();
                };
            }

            Guarded binding = method.getAnnotation(Guarded.class);
            User user = CURRENT.get();

            if (binding == null || user == null || args == null
                || args.length != 1 || !(args[0] instanceof String id)) {
                throw new AccessDenied();
            }

            // Chỉ dùng vị trí/kiểu tham số theo hợp đồng API để lấy ID.
            // Kiểu String không hề là căn cứ cấp quyền.
            Document doc = repository.find(id);
            if (doc == null
                || !Policy.isAllowed(user, binding.value(), doc)) {
                throw new AccessDenied();
            }

            try {
                // Cho phép rồi mới chuyển tiếp chính xác một lần.
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        };

        return (Documents) Proxy.newProxyInstance(
            Documents.class.getClassLoader(),
            new Class<?>[] { Documents.class },
            advice
        );
    }

    static void check(String label, Supplier<String> action) {
        try {
            System.out.println(label + "=" + action.get());
        } catch (AccessDenied denied) {
            System.out.println(label + "=DENIED");
        }
    }

    public static void main(String[] args) {
        Repository repository = new Repository();
        Documents documents = secured(
            new DocumentOperations(repository), repository
        );

        withUser(new User("alice", "tenant-A", false, Set.of()), () -> {
            check("alice-read", () -> documents.read("doc-7"));
            check("alice-publish-no-mfa", () -> documents.publish("doc-7"));
        });

        withUser(new User("alice", "tenant-A", true, Set.of()), () ->
            check("alice-publish-mfa", () -> documents.publish("doc-7"))
        );

        withUser(new User("bob", "tenant-A", false,
                          Set.of("reviewer")), () ->
            check("bob-read", () -> documents.read("doc-7"))
        );

        withUser(new User("eve", "tenant-B", true,
                          Set.of("reviewer")), () ->
            check("eve-read", () -> documents.read("doc-7"))
        );

        check("anonymous-read", () -> documents.read("doc-7"));
    }
}
~~~

Lệnh chạy bằng JDK 17+:

~~~bash
javac ExplicitPolicyAspectDemo.java
java ExplicitPolicyAspectDemo
~~~

Stdout xác định:

~~~text
alice-read=draft contents
alice-publish-no-mfa=DENIED
alice-publish-mfa=published doc-7
bob-read=draft contents
eve-read=DENIED
anonymous-read=DENIED
~~~

**Giải thích từng quyết định:**

1. `alice` thuộc `tenant-A` và là chủ sở hữu tài liệu `doc-7` nên được `document.read` cho phép.
2. Khi `alice` chưa đạt điều kiện `mfa`, `document.publish` bị từ chối **dù cùng người và cùng tài liệu**.
3. Khi `alice` đã có `mfa` hợp lệ trong giả lập, `document.publish` được phép.
4. `bob` có role `reviewer` trong cùng tenant nên được đọc tài liệu; role này chưa mang quyền publish.
5. `eve` mang role `reviewer` nhưng khác tenant; điều kiện tenant được kiểm tra trước và việc đọc vẫn bị từ chối.
6. Khi `CURRENT` không chứa principal, kết quả là deny.

Trong ví dụ, `String` chỉ giúp tìm ID trong tham số của operation đã khai báo. Quyền đọc/ghi đến từ `Guarded` và `Policy`, kết hợp với chủ sở hữu/tenant đã được tra từ `Repository`. Nếu một phương thức mới được đưa vào interface mà quên `@Guarded`, proxy **từ chối** thay vì mặc định cho phép.

**Phạm vi bảo đảm:** Đây là mô phỏng về chính sách và đường chuyển tiếp của một *dynamic proxy*, chưa phải hệ thống xác thực hoàn chỉnh. `withUser` đang được gọi trực tiếp trong `main` để thử tình huống, không tạo ra một chứng cứ xác thực. `DocumentOperations` và repository vẫn có thể bị gọi trực tiếp nếu ứng dụng vô tình cung cấp tham chiếu; thực thi tại proxy không tự bảo vệ mọi đường truy cập. Trong ứng dụng thật, cần chặn các biên liên quan và bảo vệ dữ liệu ở lớp sở hữu tài nguyên.

## 6. Thiết kế policy binding tường minh, không mở nhầm quyền

### 6.1 Mỗi operation có một bản mô tả chính sách

Với một codebase lớn, có thể cấu hình mapping khai báo rõ:

~~~text
operation: documents.read
permission: document.read
resourceResolver: documentId -> trustedDocumentRepository
rule: sameTenant AND (owner OR reviewer)

operation: documents.publish
permission: document.publish
resourceResolver: documentId -> trustedDocumentRepository
rule: sameTenant AND owner AND verifiedStrongAuthentication
~~~

Đây là **cấu hình khái niệm**, không phải cú pháp của một thư viện cố định. Điểm chính là mỗi operation nói rõ action và cách định danh resource. Resolver không được “nhìn thấy kiểu `String` thì coi là documentId” ở mọi API; muốn lấy ID từ vị trí tham số phải dựa trên hợp đồng đã biết và được kiểm thử.

Việc thiếu annotation hoặc mapping cần dẫn tới **configuration error hoặc deny**, cùng một cảnh báo khi triển khai, thay vì mở quyền. Cần kiểm tra những phương thức đã đổi tên/đổi chữ ký để không bỏ sót binding.

### 6.2 Policy engine không phụ thuộc tên phương thức

Một policy engine có thể nhận một cấu trúc chuẩn hóa:

~~~text
principal:
  id: alice
  tenant: tenant-A
  permissions: [document.read]
resource:
  type: document
  id: doc-7
  tenant: tenant-A
  owner: alice
action: document.read
environment:
  strongAuthentication: false
~~~

Các dữ liệu ví dụ ở trên **chỉ minh họa các đầu vào**, không phải sự thật do chữ ký method xác nhận. Policy engine có thể dùng quyền role, quan hệ sở hữu, tenant, trạng thái tài nguyên, điều kiện thời gian, phân loại bí mật hoặc quyền được ủy quyền. Trách nhiệm của AOP là cung cấp dữ liệu đã xác minh đúng nguồn và chỉ chuyển tiếp sau quyết định `allow`.

Một role cao không mặc nhiên được phép vượt mọi tenant hoặc classification. Nếu chính sách thực sự cho phép quyền quản trị xuyên tenant, hãy khai báo và kiểm thử ngoại lệ đó một cách tường minh, không suy ra từ từ `Admin` trong tên phương thức hoặc DTO.

### 6.3 Resource ownership và kiểm soát ngang hàng

Một mẫu sai phổ biến:

~~~text
GET /documents/doc-of-bob
authenticatedUser = alice
method = getDocument(String)
authorization = "method starts with get"
result = allow   <-- lộ tài liệu của bob
~~~

Một thiết kế đúng phải tra `doc-of-bob` từ nguồn tin cậy rồi kiểm tra quan hệ giữa người gọi và document ấy. Việc người dùng được **xem tài liệu của chính mình** không tạo quyền xem mọi tài liệu có cùng kiểu. Đây là nhóm lỗi thường được gọi là *broken object-level authorization* / truy cập vượt quyền theo đối tượng.

Cũng cần kiểm soát **field-level authorization**: cùng một resource có thể cho phép đọc tiêu đề nhưng không cho phép đọc số định danh riêng, lương hoặc khóa bí mật. Nếu API trả một DTO chứa các field khác nhau, chỉ kiểm tra “được phép gọi phương thức read” ở cấp phương thức có thể chưa đủ. Policy có thể cần chọn projection dữ liệu được phép hoặc kiểm soát cách serialize trường nhạy cảm.

## 7. Những đường vượt qua pointcut hoặc proxy

### 7.1 Gọi trực tiếp vào đối tượng gốc

Một interface proxy chỉ chặn lời gọi **đi qua chính proxy**. Nếu caller có tham chiếu tới target và gọi `target.read(...)` trực tiếp, advice không được thực thi. Vì thế:

- Không công khai target hoặc repository cho code không có thẩm quyền.
- Thiết kế điểm kiểm soát tại boundary mà mọi đường truy cập bắt buộc phải đi qua.
- Với dữ liệu rất nhạy cảm, có thể cần kiểm soát bổ sung ở tầng lưu trữ, transaction hoặc chính thao tác quản lý dữ liệu, không chỉ lớp API bên ngoài.

### 7.2 Self-invocation và lời gọi nội bộ

Nếu `serviceA` đã qua proxy nhưng một phương thức trong target gọi `this.sensitiveOperation()` trực tiếp, lời gọi nội bộ có thể không qua proxy. Tính chất cụ thể phụ thuộc cơ chế AOP; không được mặc định mọi lời gọi đều bị chặn.

Giải pháp cần dựa trên kiến trúc thực: chuyển các thao tác yêu cầu quyền sang một boundary được bảo vệ riêng, hoặc dùng cơ chế weaving thực sự bắt được join point mong muốn. Không chọn một phương án chỉ vì có vẻ “tự động”; phải kiểm chứng những đường truy cập thực tế.

### 7.3 Reflection, callback và lời gọi gián tiếp

Các lệnh gọi từ reflection, method handles, event consumer, background job, RPC handler hoặc task chạy trên executor có thể xuất hiện ở những nơi không nằm trong pointcut khai báo. Reflection không tự bỏ qua mọi cơ chế bảo vệ, nhưng **một pointcut không chọn đường đó** sẽ không thực thi advice. Cần lập bản đồ entrypoint và kiểm tra mọi đường xử lý tài nguyên nhạy cảm.

### 7.4 Overload, bridge method, interface và kế thừa annotation

Các chữ ký như `load(String id)` và `load(long id)` có thể cùng tên nhưng được xử lý khác nhau; kiểu tham số có thể bị xóa/generate bridge, annotation trên interface/implementation có thể không được nhìn thấy bằng cùng cách reflection. Một proxy có thể nhận `Method` mô tả phương thức interface, trong khi weaving có thể nhìn implementation method. Vì vậy policy resolver cần giải quyết annotation/mapping **theo semantics chính xác của cơ chế dùng**, kiểm thử overload, inherited method, default method và generic bridge, và từ chối khi mapping không rõ.

Không viết quy tắc `if annotation not found then allow` để tránh lỗi cấu hình: đây là fail-open.

### 7.5 Phương thức Object và đường phụ

`toString()`, `equals()`, `hashCode()` đôi khi đi qua proxy. Nếu ủy quyền nguyên trạng tới đối tượng nghiệp vụ, một `toString()` vô tình trả dữ liệu nhạy cảm có thể lộ thông tin mà policy chưa bảo vệ. Mã ví dụ xử lý chúng với hành vi trung tính và không dùng tên những phương thức ấy để xác định quyền nghiệp vụ.

Không để serializer, debugger hoặc logger có quyền gọi phương thức nhạy cảm mà không đánh giá chính sách. Thông tin debug là dữ liệu có thể cần bảo vệ riêng.

## 8. Lấy principal từ đâu và truyền context qua bất đồng bộ thế nào?

**Principal không được do tham số request tự nhận.** Ví dụ client gửi:

~~~json
{
  "userId": "administrator",
  "tenant": "tenant-A",
  "resourceId": "doc-7"
}
~~~

Hai field `userId` và `tenant` trong body không đủ để trở thành danh tính có quyền. Thành phần xác thực cần kiểm tra bằng chứng phù hợp (session, token đã được xác minh hoặc channel identity) rồi tạo context đáng tin. Tên người dùng và tenant từ payload có thể dùng làm **đối tượng được yêu cầu** hoặc dữ liệu nghiệp vụ, nhưng không thay thế danh tính actor.

Với Java, `ThreadLocal` có thể tiện để truyền context **trong cùng một thread và scope được quản lý**, nhưng không tự lan qua thread mới, executor, reactive callback hoặc completion handler. Một thread trong pool còn có thể được tái sử dụng cho request khác. Nếu không đóng/khôi phục context, request sau có thể thấy principal cũ, tạo lỗ hổng nghiêm trọng.

Trong xử lý bất đồng bộ:

1. Xác thực caller tại ranh giới đã tin cậy.
2. Chụp một context bất biến/có vòng đời rõ và truyền tới tác vụ được ủy quyền theo cơ chế propagation phù hợp.
3. Khôi phục hoặc đóng context trong `finally`/scope được quản lý.
4. Đánh giá lại policy tại nơi sử dụng resource khi các điều kiện có thể đổi (quyền bị thu hồi, chủ sở hữu thay đổi, tenant bị khóa).
5. Tránh gọi `allow` chỉ vì callback không tìm thấy context; thiếu thông tin là trường hợp từ chối hoặc lỗi cấu hình.

Đừng giả định “caller ban đầu được phép” đồng nghĩa một tác vụ chạy sau nhiều giờ vẫn được phép. Với các thao tác dài hoặc queue nền, cần xác định chính sách dùng quyền tại lúc gửi lệnh hay tại lúc thực thi và kiểm chứng quyền còn hiệu lực theo mô hình đe dọa.

## 9. Time-of-check to time-of-use (TOCTOU)

Ngay cả khi advice đánh giá policy đúng tại thời điểm kiểm tra, dữ liệu có thể đổi **giữa kiểm tra và sử dụng**:

~~~text
T1: Advice đọc document.owner = alice
T2: Policy cho alice quyền publish
T3: Tác vụ khác chuyển owner sang bob
T4: Nghiệp vụ publish document mà không kiểm tra lại
~~~

Khi đó authorization có thể trở nên lỗi thời. Nếu đây là thao tác nhạy cảm, có thể cần đặt kiểm tra và thay đổi trạng thái trong **cùng transaction**, dùng version/optimistic locking, kiểm tra có điều kiện tại kho dữ liệu, hoặc thực thi quyền tại chính tầng sở hữu resource. Chính sách có thể cần “điều kiện tại thời điểm commit” thay vì “điều kiện tại thời điểm vào phương thức”.

Một cách khái niệm khi lưu trữ hỗ trợ update có điều kiện:

~~~sql
UPDATE documents
SET published = TRUE
WHERE id = :document_id
  AND tenant_id = :authenticated_tenant
  AND owner_id = :authenticated_user
  AND status = 'DRAFT';
~~~

SQL này minh họa việc gắn ràng buộc resource ngay tại thao tác cập nhật. Trong hệ thật, vẫn cần kiểm tra các điều kiện bổ sung như xác thực tăng cường, version, đối tượng tổ chức, quy tắc quyền kế thừa và xử lý quyền đặc biệt. `:authenticated_user` phải đến từ authentication context đã tin cậy.

## 10. Chức năng cấp quyền và quan sát bảo mật không được trộn lẫn

AOP có thể có nhiều aspect cùng tác động lên một join point: authorization, metrics, tracing, retry, transaction hoặc audit. Thứ tự chạy là quan trọng.

- Authorization cần diễn ra **trước khi side effect nhạy cảm bắt đầu**.
- Retry có thể làm một thao tác được thực thi nhiều lần. Policy phải xác định kiểm tra quyền theo từng attempt hay request logic; quyền có thể đổi giữa các lần thử.
- Transaction có thể quyết định thời điểm lấy dữ liệu, lock và commit. Nếu cần atomicity, thiết kế authorization/check cần tương thích transactional boundary.
- Logging không nên ghi thẳng tham số chứa token, mật khẩu, dữ liệu riêng tư hoặc toàn bộ tài liệu chỉ vì advice nhìn thấy chúng.
- Nếu metrics hoặc audit thất bại, ứng dụng cần chính sách rõ ràng về cho tiếp hay chặn; yêu cầu audit bắt buộc có thể khác telemetry chỉ phục vụ vận hành.
- Thứ tự kết hợp aspect có thể do cấu hình hoặc weaving. Không phụ thuộc vào suy đoán thứ tự; hãy xác minh bằng integration test.

Một điểm cần phân biệt: **authentication aspect** có thể kiểm tra caller đã đăng nhập, nhưng **authorization aspect** vẫn phải kiểm tra quyền đối với action + resource cụ thể. “Đã đăng nhập” chưa phải “được xem mọi dữ liệu”.

## 11. Thiết kế default deny mà không làm mất khả năng vận hành

“Deny by default” không có nghĩa mọi phương thức đều bắt buộc có một annotation quyền theo cùng một mẫu. Các thao tác công khai thực sự như health check hoặc trang thông tin không cần danh tính có thể được gán **policy công khai được khai báo và kiểm thử riêng**, ví dụ `PermitAnonymous` cho endpoint đã được duyệt.

Điều quan trọng là:

- **Mọi điểm truy cập tài nguyên cần bảo vệ đều được bao phủ bởi một policy xác định.**
- Trạng thái “không tìm thấy rule”, “không tìm thấy principal”, “không rõ resource” hoặc “policy engine không phản hồi” không được tự chuyển thành allow.
- Bảo vệ những entrypoint ít gặp: batch job, timer, message consumer, administrative tooling, import/export, callback và dữ liệu tải từ cache.
- Chính sách đặc quyền (break-glass, superuser, delegated authority) cần bằng chứng tường minh, phạm vi và logging/audit, không được suy ra từ một tên gọi.

Khi deny, tránh tiết lộ resource có tồn tại hay không nếu thông tin đó nhạy cảm. Một hệ thống có thể trả thông báo từ chối chung trong cả trường hợp không có quyền và không tìm thấy tài nguyên. Cách ánh xạ sang trạng thái API do hợp đồng quyết định; tuy nhiên log nội bộ vẫn có thể phân loại nguyên nhân phù hợp mà không lộ dữ liệu nhạy cảm cho caller.

### Cẩn thận với “allow list theo phương thức”

Một danh sách các method được phép gọi không đồng nghĩa tất cả đối tượng truy cập từ method đó đều được phép. `documents.read` có thể là operation hợp lệ của một role, nhưng vẫn cần kiểm tra ID cụ thể và tenant. Authorization ở mức phương thức và authorization ở mức đối tượng là các lớp kiểm soát khác nhau, có thể cùng cần thiết.

## 12. Checklist kiểm thử quyền truy cập với AOP

Bài kiểm thử cần trả lời cả **“điểm chặn có hoạt động?”** lẫn **“chính sách có đưa ra quyết định đúng?”**:

| Tình huống | Kết quả mong đợi |
| --- | --- |
| Không có principal | Deny và không thực thi nghiệp vụ |
| Principal hợp lệ, đúng action, đúng resource | Allow nếu mọi điều kiện policy đạt |
| Đúng role, sai tenant | Deny |
| Đúng tenant, không phải chủ sở hữu và không có quyền chia sẻ | Deny |
| Đúng owner nhưng thiếu điều kiện tăng cường | Deny |
| Client tự gửi field `role: admin` hoặc `ownerId` | Không tự được cấp quyền |
| Resource không tồn tại hoặc thiếu metadata | Deny/không lộ thông tin theo hợp đồng |
| Action chưa khai báo policy | Deny và phát hiện cấu hình thiếu |
| Đổi tên method nhưng giữ action binding hợp lệ | Quyết định quyền không tự đổi |
| Overload cùng tên, kiểu tham số khác nhau | Phân giải policy theo binding rõ, không theo tên |
| Phương thức trả `CompletionStage`/stream | Đúng vòng đời và context, không bỏ check |
| Gọi trực tiếp target/self-invocation | Phát hiện đường bypass; bảo vệ boundary thật |
| Policy store chậm, timeout hoặc lỗi | Không fail-open; có fallback được thiết kế |
| Quyền/ownership thay đổi khi thao tác đang chạy | Kiểm tra lại hoặc đảm bảo atomicity theo yêu cầu |
| Advice bị gọi lặp, nhiều thread cùng truy cập | Không cho phép vì race hay context bị rò rỉ |
| Lỗi xảy ra sau khi đã allow | Trả exception nghiệp vụ gốc, không ghi nhầm thành lỗi authorization |

### Kiểm thử tính chất của policy

Ngoài các ví dụ riêng lẻ, có thể kiểm tra một số **bất biến**:

1. Đổi **tên phương thức** nhưng giữ cùng action và resource không được tự thay đổi quyền.
2. Đổi **kiểu bao tham số** mà nội dung nghiệp vụ đã được chuẩn hóa không được tự nâng quyền.
3. Đổi resource owner/tenant sang một đối tượng không được phép phải làm `allow` chuyển thành `deny` theo chính sách tương ứng.
4. Xóa một quyền khỏi tập role đã xác thực không được làm tăng số hành động được phép.
5. Thiếu thông tin bắt buộc dẫn tới từ chối; policy engine lỗi không mở quyền.
6. Khi bị deny, số lần nghiệp vụ gốc được thực thi là **0**; khi được allow theo advice thuần túy, số lần được thực thi là **1**.

Có thể kiểm thử theo ma trận principal × action × resource × context, và bổ sung property-based/fuzz testing để tìm tổ hợp tham số gây vượt quyền. Đừng chỉ dùng một user admin trong test, vì trường hợp đó có thể che giấu lỗi authorization theo đối tượng.

## 13. Rủi ro nâng cao: caching quyết định và quyền thay đổi

Khi policy evaluation tốn kém, một hệ thống có thể cache quyết định `allow/deny`. Cache phải chọn khóa đúng:

`cacheKey = (subject, action, resource, relevantContext, policyVersion)`

Đây là sơ đồ khái niệm; các thuộc tính như tenant, quyền uỷ quyền, phiên xác thực tăng cường, trạng thái tài nguyên và thời điểm cũng có thể ảnh hưởng kết quả. Nếu chỉ cache theo `methodName` hoặc `parameterType`, hệ thống có thể tái sử dụng quyết định của `alice` để cho `bob` truy cập.

Ngay cả khi cache key đủ chi tiết, quyền hoặc ownership có thể bị thu hồi. Cần TTL, invalidation, policy versioning hoặc một nguồn quyết định nhất quán. Không tối ưu chi phí phân quyền bằng cách bỏ qua sự thay đổi quyền mà chưa có hợp đồng về độ trễ thu hồi.

Một aspect có thể “có vẻ đúng” ở thử nghiệm đơn luồng nhưng hỏng khi chạy song song: dữ liệu principal trong biến static dùng chung, mutable context bị sửa, race cập nhật cache hoặc context rò qua thread pool. Mỗi quyết định cần gắn với đúng request/subject; ưu tiên context bất biến, scope rõ và kiểm thử concurrency.

## 14. Phân tích trường hợp trước–sau

**Trước:**

~~~text
Một người dùng đăng nhập được gọi:
  method = getInvoice(String invoiceId)
  invoiceId = invoice của người khác

Pointcut: tên method bắt đầu bằng get
Advice: cho phép vì có chữ get
Kết quả: dữ liệu hóa đơn bị đọc trái phép
~~~

**Sau:**

~~~text
Method binding:
  action = invoice.read
  resolver = lấy invoice theo invoiceId từ kho đáng tin

Advice:
  principal = lấy từ authentication context đã xác minh
  invoice = resolver(invoiceId)
  policy = kiểm tra tenant + owner hoặc grant truy cập
  nếu thiếu/thất bại -> deny
  nếu allow -> gọi nghiệp vụ đúng một lần

Kết quả:
  chỉ dữ liệu được chính sách cấp phép mới được trả
~~~

Trường hợp này minh họa lý do **chữ ký phương thức chỉ giúp tìm dữ liệu cần kiểm tra**, không đủ để kết luận ai được truy cập. Nếu invoice có nhiều field với mức nhạy cảm khác nhau, cần kiểm tra cả quyền xem từng phần dữ liệu hoặc xây projection tương ứng.

## 15. Quy trình thiết kế aspect phân quyền an toàn

1. **Xác định tài sản và mô hình đe dọa:** loại dữ liệu nào nhạy cảm, actor nào có thể gọi từ entrypoint nào, phương thức nào ghi side effect.
2. **Xác định action nghiệp vụ:** ví dụ `document.read`, `document.publish`, `account.transfer`; không dùng prefix của tên hàm như một đặc quyền.
3. **Xác định resource resolver:** từ ID được validate, tra metadata/quan hệ sở hữu ở nguồn đáng tin; hỗ trợ tenant và phân cấp tài nguyên nếu cần.
4. **Xây policy rõ ràng:** quyền role, ownership, grant, thuộc tính, điều kiện session, MFA, trạng thái tài nguyên và ngoại lệ được duyệt.
5. **Bố trí điểm cắt phù hợp:** verify proxy/weaving bắt đúng mọi đường vào cần bảo vệ, xử lý overload/inheritance/default methods.
6. **Cố định nguồn principal:** từ lớp xác thực, tránh tự lấy ID do client khai; truyền context qua async và dọn scope an toàn.
7. **Quy định fail closed:** thiếu annotation/rule/context/resource hoặc policy evaluation lỗi đều không tự tạo allow.
8. **Đảm bảo tính đúng ở data boundary:** cân nhắc transaction, version, conditional update, defense-in-depth khi ownership thay đổi.
9. **Kiểm thử cả positive lẫn negative:** tenant chéo, owner khác, role giả, policy thiếu, bypass, concurrent calls, quyền bị thu hồi.
10. **Theo dõi và rà soát:** audit quyết định quan trọng với dữ liệu tối thiểu; theo dõi deny/error và thay đổi chính sách, tránh log bí mật.

## 16. Những nhầm lẫn cần tránh

**“Tên bắt đầu bằng `get` nghĩa là chỉ đọc, nên an toàn.”** Sai. Đọc dữ liệu bí mật có thể gây thiệt hại lớn và “read-only” không đồng nghĩa “public”.

**“Tham số là `AdminRequest` nên chỉ admin mới tạo được.”** Sai. Một class DTO có thể do bất cứ caller nào dựng; kiểu dữ liệu không chứng minh quyền.

**“Người gọi đã đăng nhập, kiểm tra theo phương thức là đủ.”** Sai. Cần quyết định quyền đối với resource và action cụ thể.

**“Có annotation thì được phép.”** Sai. Annotation có thể định danh chính sách; việc được phép vẫn phải được đánh giá bằng dữ liệu đáng tin.

**“Proxy chặn được method này thì mọi lời gọi đều bị bảo vệ.”** Sai nếu tồn tại tham chiếu target, self-invocation hoặc một entrypoint không đi qua proxy.

**“ThreadLocal có principal nghĩa là đang ở trong request hợp lệ.”** Không chắc. Context có thể bị giả mạo ở lớp không tin cậy hoặc rò rỉ giữa các tác vụ nếu quản lý sai.

**“Đã kiểm tra owner trước khi ghi nên không cần xét gì nữa.”** Chưa chắc. Quyền hoặc metadata có thể thay đổi giữa kiểm tra và commit; thao tác nhạy cảm cần xử lý TOCTOU.

**“Cho phép khi không tìm được policy giúp hệ thống vẫn chạy.”** Đó là fail-open và có thể tạo lỗ hổng khi cấu hình thiếu, kho chính sách bị lỗi hoặc một phương thức mới được thêm.

## 17. Kết luận

AOP giúp **đưa kiểm soát truy cập tới các điểm thực thi một cách nhất quán**, nhưng tính đúng của phân quyền phụ thuộc vào chính sách và nguồn dữ liệu được tin cậy. **Tên phương thức và kiểu tham số chỉ mô tả API; chúng không xác thực danh tính, không chứng minh quyền sở hữu và không cấp đặc quyền.** Một aspect an toàn phải gắn operation với action được khai báo rõ, lấy principal từ ranh giới xác thực, lấy resource từ nguồn đáng tin, đánh giá đầy đủ subject–action–resource–context, từ chối khi thiếu dữ liệu và kiểm tra mọi đường thực thi có thể bỏ qua điểm chặn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-005">Thứ tự kiểm tra quyền so với giao dịch và bộ nhớ đệm</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự kiểm tra quyền so với giao dịch và bộ nhớ đệm trong Aspect (AOP)

## 1. Tại sao thứ tự Advice là một vấn đề bảo mật?

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, **AOP**), nhiều Aspect có thể cùng áp dụng cho một join point: kiểm soát truy cập, quản lý giao dịch (transaction), cache, truy vết, đo thời gian và xử lý lỗi. Mỗi Aspect có thể đúng khi đứng riêng, nhưng **thứ tự kết hợp** của chúng quyết định caller thực sự quan sát điều gì.

Hãy xét lời gọi `readDocument(user, id)`. Nếu một Aspect cache trả kết quả **trước khi** Aspect kiểm tra quyền chạy, người dùng không được phép có thể nhận dữ liệu mà người có quyền đã làm nóng cache. Ngược lại, kiểm tra quyền trước khi mở transaction đôi khi tiết kiệm tài nguyên, nhưng nếu quyết định quyền phụ thuộc dữ liệu đang thay đổi, kiểm tra ngoài transaction có thể tạo khoảng trống **time-of-check to time-of-use (TOCTOU)**.

Bài học chính: **mọi đường trả dữ liệu được bảo vệ phải đi qua kiểm tra quyền hợp lệ, trong khi trạng thái và transaction được quản lý đủ nhất quán theo hợp đồng nghiệp vụ**. Không có một thứ tự duy nhất luôn đúng cho mọi API. Phải chọn dựa trên dữ liệu nào được bảo vệ, nơi lưu policy, mức nhất quán, semantics của cache và các side effect.

Bài này trình bày AOP **tổng quát**, gồm join point, pointcut, around advice, composition, proxy/weaving và cách kiểm thử. Ví dụ chạy được bằng Java 17+ dùng decorator và interface proxy-like boundary để làm rõ thứ tự thực thi; không yêu cầu thư viện AOP cụ thể.

### 1.1. Những khái niệm cần phân biệt

- **Authentication (xác thực):** xác định danh tính caller. Principal/identity phải đến từ ngữ cảnh tin cậy, không tin một tham số `userId` do client tự khai.
- **Authorization (ủy quyền/kiểm tra quyền):** xác định principal có được phép làm **hành động** trên **tài nguyên** trong **ngữ cảnh hiện tại** hay không, ví dụ `(subject, action, resource, tenant, policyVersion)`.
- **Aspect/advice:** mã thực thi tại join point; around advice có thể gọi `proceed()`, đổi exception/return, hoặc thậm chí không gọi nghiệp vụ. Do đó advice có thể tạo lỗ hổng bảo mật nếu sai thứ tự.
- **Transaction:** phạm vi nhất quán/commit/rollback do nguồn dữ liệu hỗ trợ. Một transaction của database không tự làm thao tác từ xa hay hàng đợi trở thành nguyên tử.
- **Cache:** nơi lưu tạm kết quả hoặc quyết định. **Result cache** chứa dữ liệu nghiệp vụ; **authorization-decision cache** chứa quyết định allow/deny; hai loại có khóa, TTL và yêu cầu invalidation khác nhau.
- **Policy Enforcement Point (PEP):** nơi chặn và thực thi allow/deny; **Policy Decision Point (PDP):** nơi đánh giá luật. Một Aspect có thể là PEP nhưng không nên là nguồn duy nhất bảo vệ tất cả đường truy cập đến dữ liệu quan trọng.

## 2. Đọc thứ tự advice bằng mô hình lồng nhau

Ký hiệu `A -> T -> C -> B` tương ứng **Authorization -> Transaction -> Cache -> Business**. Bên trái là lớp *ngoài* và chạy trước trên đường vào; đường ra đảo chiều.

~~~text
A.before
  T.begin
    C.lookup
      B.execute (nếu cache miss)
    C.store (nếu hợp lệ)
  T.commit/rollback
A.after
~~~

Nếu `C` trả ngay khi cache hit, các lớp **bên trong C không chạy**. Nếu thứ tự là `C -> A -> T -> B`, cache hit có thể bypass cả A và T:

~~~text
C.lookup -> HIT -> return
            |
            +-- A.check KHÔNG chạy
            +-- T.begin KHÔNG chạy
            +-- B.execute KHÔNG chạy
~~~

Nếu cache miss thì chuỗi bên trong mới chạy, gây ra hành vi khác nhau giữa “cold cache” và “warm cache”. Đây là lý do một test với cache luôn trống **không đủ** để kiểm chứng kiểm soát truy cập.

### 2.1. Advice “outside” không tự có nghĩa “an toàn”

Cho `A` ở ngoài cùng chưa đủ nếu A chỉ kiểm tra kiểu tài khoản chung hoặc bỏ qua resource/action thực tế. Ví dụ “người dùng đã đăng nhập” không phải quyết định “người dùng được xem tài liệu có mã X”. Điều kiện cấp tài nguyên (object-level authorization), tenant boundary, phạm vi trường dữ liệu và thời điểm quyền bị thu hồi đều phải được xác định.

Tương tự, nếu A kiểm tra quyền theo metadata cũ, transaction bên trong A có thể làm việc trên dữ liệu đã thay đổi. Cần chọn biện pháp nhất quán tương ứng: kiểm tra lại sau khi đọc trạng thái mới, khóa/phiên bản, ràng buộc database hoặc cùng transaction khi cần.

## 3. Các cách sắp xếp phổ biến và trade-off

| Chuỗi từ ngoài vào trong | Cache hit có qua kiểm tra quyền? | Transaction được mở khi nào? | Nhận xét |
| --- | --- | --- | --- |
| `A -> T -> C -> B` | Có | Trước cache lookup | Dễ diễn giải; cache hit vẫn tốn chi phí transaction; phải kiểm soát dữ liệu được đưa vào cache trước commit |
| `A -> C -> T -> B` | Có | Chỉ khi cache miss | Tối ưu đọc nếu A kiểm tra được quyền đúng trước cache và cache hit đáp ứng contract nhất quán |
| `T -> A -> C -> B` | Có | Trước authorization | Phù hợp khi A cần đọc cùng snapshot/lock; denial có thể khiến transaction phải kết thúc/rollback |
| `C -> A -> T -> B` | **Không bảo đảm** | Chỉ khi miss | Nguy hiểm nếu C dùng chung và cache hit trả dữ liệu cần quyền mà không kiểm tra |
| `T -> C -> A -> B` | **Không bảo đảm** | Trước cache | Mở transaction không chữa việc A bị bỏ qua khi cache hit |
| `A -> T -> B -> C` | Phụ thuộc join point | Phụ thuộc phần còn lại | Ký hiệu này chỉ hợp lý nếu C được chặn ở chỗ trả dữ liệu/thao tác rõ ràng; không áp đặt máy móc cho mọi advice |

**Không dùng bảng như công thức cứng.** Một cache chỉ chứa nội dung công khai có thể không cần kiểm tra quyền theo từng người. Một cache được phân vùng theo principal vẫn phải đối phó quyền bị thu hồi. Một transaction dài mở trước tất cả kiểm tra có thể tạo chi phí/điểm chờ lock lớn. Quyết định đúng phải xuất phát từ ranh giới bảo vệ và đặc tả API.

### 3.1. Một thứ tự thường hợp lý cho truy vấn đơn giản

Khi một tài nguyên có policy ổn định và kiểm tra quyền không cần phụ thuộc transaction nghiệp vụ:

~~~text
trusted identity / policy
         |
         v
authorization (A)
         |
         v
shared result cache (C)
         |
         +-- HIT  -> result đã được kiểm tra quyền cho caller này
         |
         +-- MISS -> transaction/read (T + B) -> cache/store -> result
~~~

Đây là kiểu `A -> C -> T -> B`. Lợi ích: mọi lần trả kết quả đều qua A và cache hit có thể không mở transaction. Điều kiện: A phải thực sự đánh giá đúng quyền cho resource và action **hiện tại**, cache không để lộ metadata nhạy cảm trước A, và nội dung cached còn hợp lệ theo contract consistency.

Nếu dữ liệu/phân quyền thay đổi cùng một giao dịch, chuyển logic quyết định phù hợp vào vùng consistency bảo đảm và cân nhắc dạng `T -> A -> C -> B` hoặc `A -> T -> A_recheck -> C -> B` theo thiết kế. Việc kiểm tra lại phải dùng nguồn trạng thái tin cậy, không chỉ một decision cache quá hạn.

## 4. Kiểm soát quyền trước cache: điều kiện tối thiểu

Một Aspect cache thường hoạt động như sau:

~~~text
lookup(key):
    nếu tồn tại cached value: trả ngay
    nếu không: gọi proceed(), lưu value, trả về
~~~

Khi cached value là dữ liệu được bảo vệ, **không thể mặc nhiên đặt cache bên ngoài A** chỉ vì lần tạo cache ban đầu đã được phép. Cho ví dụ:

1. Alice có quyền đọc `report-7`, lần đầu cache miss; advice authorization cho phép và kết quả được cache.
2. Bob không có quyền đọc `report-7`.
3. Bob gọi cùng phương thức; một cache chỉ khóa theo `report-7` hit và trả dữ liệu, nên authorization advice ở phía trong không chạy.

Đây là **cache authorization bypass**. Việc thử nghiệm chỉ kiểm tra Alice thành công và Bob bị từ chối khi cache trống sẽ bỏ sót lỗi.

### 4.1. Có nên đưa principal vào cache key?

Khóa theo `(tenant, principal, resource, action, representation)` có thể giảm nguy cơ người này hit nội dung dành cho người kia; tuy nhiên **khóa đúng không thay thế kiểm tra quyền**:

- Quyền của cùng principal có thể bị thu hồi trong khi entry còn sống.
- Principal có thể thay đổi vai trò, thuộc tính, membership hoặc session scope.
- Hai user có cùng quyền nhưng được nhìn thấy các trường dữ liệu khác nhau.
- Cache key có thể thiếu tenant hoặc version của policy, tạo đụng độ dữ liệu.
- Nguồn backend có thể cập nhật trạng thái nhạy cảm trong khi TTL chưa hết.

Với dữ liệu chỉ khác theo tenant, cần phân vùng theo tenant và kiểm tra quyền trước trả kết quả. Với dữ liệu cá nhân hóa, phải có representation/authorization scope và chính sách invalidation phù hợp. Trong hệ thống phức tạp, an toàn hơn là xem cache như **nguồn kết quả không đáng tin cho đến khi đã qua chính sách truy cập hiện tại**.

### 4.2. Cache quyền và cache nội dung là hai quyết định

**Decision cache** lưu kết quả policy như `allow(subject, action, resource, context)`. Khóa có thể cần version policy, quyền hiện tại, tenant, resource version và ngữ cảnh (chẳng hạn thời gian hiệu lực). Quyết định “allow” cũ có thể gây lộ dữ liệu sau thu hồi.

**Result cache** lưu body/DTO/fragment; khóa có thể phụ thuộc resource version, locale, representation và tenant. Nó không tự biết principal có còn quyền hay không.

Nếu một A gọi decision cache bên trong nó, vẫn phải đảm bảo decision cache có TTL/invalidation phù hợp với yêu cầu thu hồi quyền. TTL ngắn giảm cửa sổ stale chứ không bảo đảm thu hồi tức thì. Với yêu cầu thu hồi ngay, cân nhắc policy version, event invalidation đáng tin, nguồn quyết định tập trung hoặc từ chối khi không thể lấy quyết định mới theo contract.

### 4.3. Không rò metadata trước kiểm tra quyền

Ngay cả khi kết quả được từ chối, cache key, thông báo “not found” và thời gian phản hồi có thể tiết lộ tài nguyên có tồn tại hay không. Nên thống nhất semantics 403/404 tùy hợp đồng và threat model, tránh log key nhạy cảm, kiểm soát response timing nếu kênh suy luận có ý nghĩa và không phát ETag/size/metadata trước khi kiểm tra quyền.

Ở cache phân tán hoặc CDN, metadata HTTP về dữ liệu cá nhân cần `Cache-Control` thích hợp và cơ chế phân vùng/cấm shared cache theo yêu cầu. Kiểm tra quyền ở lớp ứng dụng không bảo vệ một đường phân phối cache bên ngoài mà bỏ qua ứng dụng.

## 5. Transaction mở trước hay sau Authorization?

### 5.1. Kiểm tra trước transaction giúp từ chối sớm

Nếu policy có thể xác định từ thông tin tin cậy độc lập với state giao dịch (ví dụ caller không có vai trò cần thiết), kiểm tra trước `T.begin` giúp:

- Không chiếm connection, transaction slot hoặc lock cho request bị từ chối.
- Không gọi database nghiệp vụ nếu yêu cầu chắc chắn bất hợp lệ.
- Giảm side effect vô ích, giảm tải trong tấn công quá mức và thể hiện “deny before work”.

Khi authorization fail, mã nghiệp vụ, cache và transaction bên trong **không chạy**. Đây là một đặc tính nên kiểm thử bằng counter/mock cho cả đường cache hit/miss.

### 5.2. Kiểm tra cần dữ liệu nhất quán có thể nằm trong transaction

Cho nghiệp vụ duyệt lệnh chi tiền khi người duyệt còn là thành viên của nhóm phê duyệt và lệnh vẫn ở trạng thái `PENDING`. Hai điều kiện này có thể thay đổi đồng thời.

~~~text
t0: A đọc user là approver -> allow
t1: quản trị viên thu hồi quyền
t2: transaction thực hiện approve dựa trên quyết định t0
~~~

Nếu hợp đồng đòi việc thu hồi phải có hiệu lực đối với thao tác tại `t2`, quyết định `t0` có thể không đủ. Giải pháp phụ thuộc database và consistency: đọc/chốt điều kiện ngay trong transaction với isolation phù hợp; kiểm tra version và optimistic concurrency; khóa dữ liệu khi cần; thực thi predicate ngay trong lệnh cập nhật; hoặc áp dụng cơ chế bảo vệ ở datastore có ràng buộc. **Chỉ mở transaction không tự loại bỏ TOCTOU**: mức isolation, thời điểm đọc, bản ghi khóa và hợp đồng thứ tự sự kiện mới quyết định.

Một thứ tự hợp lý có thể gồm **hai tầng**:

1. Kiểm tra sơ bộ rẻ trước khi mở transaction (authenticated, tenant hợp lệ, vai trò nền tảng).
2. Mở transaction.
3. Đọc trạng thái hiện tại và kiểm tra authorization gắn với resource/action.
4. Thực thi nghiệp vụ với ràng buộc concurrency, commit, phát side effect theo quy tắc.

Không nhất thiết phải thực hiện cùng một hàm authorization hai lần; các lớp kiểm tra có thể có vai trò khác nhau. Quan trọng là quyết định cuối cần dữ liệu đang đổi phải nhất quán với thao tác được cho phép.

### 5.3. Denial và rollback

Nếu `T` nằm ngoài A, denial xảy ra sau `begin`. Transaction adapter cần kết thúc đúng trạng thái theo hợp đồng: rollback hoặc kết thúc không commit một thay đổi nào. Không “commit bất kỳ thứ gì” chỉ vì exception đã được advice khác nuốt.

Với giao dịch đã ghi tạm và sau đó fail authorization, rollback chỉ đảo ngược những thay đổi **thực sự thuộc transaction đó**; không thể tự động đảo ngược email, HTTP request hoặc message đã phát ngoài transaction. Hệ thống phải kiểm soát các side effect và phân phối event sau commit, dùng outbox/compensation nếu cần.

Nếu audit từ chối quyền buộc phải lưu ngay cả khi transaction nghiệp vụ rollback, việc ghi audit cần phạm vi lưu trữ độc lập được thiết kế rõ, không đặt audit trong transaction sẽ rollback rồi mặc nhiên tin đã lưu thành công.

## 6. Cache trong transaction: đọc, ghi và commit

### 6.1. Không publish kết quả chưa commit

Một advice cache có thể lưu kết quả khi `proceed()` trả về, nhưng transaction bao bên ngoài vẫn có thể rollback sau thời điểm cache lưu. Nếu cached value phản ánh trạng thái chưa commit, người khác có thể đọc **phantom success** hoặc dữ liệu bị rollback.

~~~text
Transaction T.begin
  Business.update -> giá trị mới, chưa commit
  Cache.put(newValue) -> người khác có thể thấy
T.commit -> thất bại, rollback
Cache vẫn chứa newValue sai
~~~

Cần phân biệt cache value **đọc** (có thể cache từ một snapshot ổn định theo chính sách) và cache value **ghi**. Với mutation, hãy phát lệnh invalidation/update cache **sau commit thành công** hoặc dùng cache/transaction integration hỗ trợ trì hoãn publish và xử lý lỗi. Việc flush cache phải có thứ tự và cơ chế phục hồi được nêu rõ; một callback chạy “sau commit” mà bị mất do process crash vẫn cần chiến lược nhất quán cuối cùng hoặc outbox nếu hệ thống yêu cầu.

### 6.2. Cache hit bỏ qua transaction có luôn được không?

Không. Nếu operation yêu cầu consistency mạnh và đọc dữ liệu liên quan đến một trạng thái transaction hiện tại (nhất là sau mutation vừa xảy ra), cache hit có thể không đáp ứng read-your-writes hoặc mức nhất quán đã hứa. Có trường hợp API chỉ chấp nhận dữ liệu từ database trong transaction cùng snapshot.

Nếu API cho phép eventual consistency, `A -> C -> T -> B` có thể hiệu quả: cache hit qua quyền rồi trả về mà không tốn transaction. Nếu API yêu cầu snapshot cụ thể, transaction có thể phải bao cả logic chọn/đọc dữ liệu; hoặc cache phải được ràng buộc theo snapshot/version. Không chọn thứ tự chỉ vì muốn “cache nhanh nhất”.

### 6.3. Invalidation sau khi quyền hay dữ liệu thay đổi

Khi một resource đổi owner, chuyển tenant, bị xóa, sửa ACL hoặc trở thành bí mật:

- Hủy/cập nhật **decision cache** của policy liên quan, không chỉ data cache.
- Hủy/cập nhật **result cache** hoặc dùng versioned keys khi representation thay đổi.
- Không dựa vào TTL như lời hứa thu hồi tức thì nếu contract đòi thu hồi ngay.
- Tránh race: callback invalidation cũ đến sau một lần cập nhật mới có thể làm cache trở về state cũ nếu không có version/ordering.
- Nếu dùng cache nhiều cấp (local + remote + CDN), kiểm tra đường nhận thay đổi policy ở **mọi cấp**.

Một phiên cache có thể cần namespace theo policy epoch hoặc tenant version. Đây là công cụ thiết kế; việc đặt version phải dựa trên một nguồn nhất quán được quản lý, không tự sinh version độc lập trên mỗi node.

## 7. Ví dụ Java 17+: thứ tự an toàn và thứ tự gây bypass

Ví dụ dưới mô phỏng một service đọc tài liệu. Tài liệu `report` chỉ được principal `alice` đọc. Các decorator đóng vai trò **around advice**: `Authorization` kiểm tra quyền, `Transaction` mô phỏng begin/commit, `Cache` lưu nội dung theo document key, `Store` đọc dữ liệu và tự bảo vệ như một lớp phòng tuyến bổ sung.

> **Lưu ý về phạm vi:** principal dạng chuỗi và transaction in log chỉ để học thứ tự. Đây không phải cơ chế xác thực thực tế hay transaction database. Trong ứng dụng thực, principal phải được lấy từ nguồn tin cậy và kiểm tra quyền ở datastore/domain nơi thích hợp.

Lưu thành `AspectOrderDemo.java`:

~~~java
import java.util.HashMap;
import java.util.Map;

public class AspectOrderDemo {
    interface Documents {
        String read(String principal, String documentId);
    }

    static final class Policy {
        static void requireRead(String principal, String documentId) {
            if (!"alice".equals(principal)
                    || !"report".equals(documentId)) {
                throw new SecurityException("access denied");
            }
        }
    }

    static final class Store implements Documents {
        @Override
        public String read(String principal, String documentId) {
            Policy.requireRead(principal, documentId);
            System.out.println("store read");
            return "confidential";
        }
    }

    static final class Authorization implements Documents {
        private final Documents next;
        Authorization(Documents next) {
            this.next = next;
        }
        @Override
        public String read(String principal, String documentId) {
            Policy.requireRead(principal, documentId);
            System.out.println("auth allow");
            return next.read(principal, documentId);
        }
    }

    static final class Transaction implements Documents {
        private final Documents next;
        Transaction(Documents next) {
            this.next = next;
        }
        @Override
        public String read(String principal, String documentId) {
            System.out.println("tx begin");
            try {
                String result = next.read(principal, documentId);
                System.out.println("tx commit");
                return result;
            } catch (RuntimeException error) {
                System.out.println("tx rollback");
                throw error;
            }
        }
    }

    static final class Cache implements Documents {
        private final Documents next;
        private final Map<String, String> values = new HashMap<>();
        Cache(Documents next) {
            this.next = next;
        }
        @Override
        public String read(String principal, String documentId) {
            String existing = values.get(documentId);
            if (existing != null) {
                System.out.println("cache hit");
                return existing;
            }
            System.out.println("cache miss");
            String loaded = next.read(principal, documentId);
            values.put(documentId, loaded);
            return loaded;
        }
    }

    public static void main(String[] args) {
        Documents safe = new Authorization(
                new Transaction(new Cache(new Store())));

        System.out.println("SAFE:");
        System.out.println("alice=" + safe.read("alice", "report"));
        try {
            safe.read("bob", "report");
        } catch (SecurityException rejected) {
            System.out.println("bob=DENIED");
        }
        System.out.println("alice-again=" + safe.read("alice", "report"));

        // Co y chon THU TU SAI de cho thay nguy co cache bypass.
        Documents unsafe = new Cache(
                new Authorization(new Transaction(new Store())));

        System.out.println("UNSAFE:");
        System.out.println("alice=" + unsafe.read("alice", "report"));
        System.out.println("bob=" + unsafe.read("bob", "report"));
    }
}
~~~

Biên dịch/chạy với JDK 17 trở lên:

~~~bash
javac AspectOrderDemo.java
java AspectOrderDemo
~~~

**Stdout chính xác:**

~~~text
SAFE:
auth allow
tx begin
cache miss
store read
tx commit
alice=confidential
bob=DENIED
auth allow
tx begin
cache hit
tx commit
alice-again=confidential
UNSAFE:
cache miss
auth allow
tx begin
store read
tx commit
alice=confidential
cache hit
bob=confidential
~~~

Ở chuỗi `Authorization -> Transaction -> Cache -> Store`, Bob bị từ chối **trước** transaction và cache. Alice gọi lần hai gặp cache hit nhưng vẫn đi qua authorization, vì lớp A ở bên ngoài.

Ở chuỗi **cố ý sai** `Cache -> Authorization -> Transaction -> Store`, Alice làm nóng cache trước; khi Bob gọi cùng key, `Cache` trả `confidential` tức thì. Cả lớp `Authorization` và kiểm tra bổ sung trong `Store` đều bị bỏ qua. Đây là ví dụ học tập về một lỗ hổng thực sự: **bảo vệ domain ở phía trong cũng có thể bị bypass nếu lớp ngoài trả nội dung được bảo vệ**.

Ví dụ dùng cache chung theo `documentId` để phơi bày lỗi; không triển khai cách bố trí `UNSAFE` trong hệ thống thật. Với một bộ cache phù hợp để production, còn phải xử lý phân vùng tenant, representation, cập nhật quyền, versioning, TTL, consistency, kích thước, eviction và thread-safety.

Trong đoạn demo, `Transaction` chỉ in thông báo; nó không thể commit/rollback bản ghi của một database. Vì vậy, những dòng `tx commit`/`tx rollback` chỉ minh họa thứ tự around advice, không mô tả độ bền hay isolation thực.

## 8. Khi Authorization cần transaction hoặc cần đọc tài nguyên

Không phải policy nào cũng kiểm tra được từ principal và ID trước khi truy cập dữ liệu. Có policy như:

- User phải là owner hiện tại của tài liệu, và owner nằm trong hàng dữ liệu.
- Lệnh chỉ được sửa trong trạng thái `DRAFT`.
- Membership hoặc phân quyền phụ thuộc một bảng ACL thay đổi theo thời gian.
- Chỉ một người đang giữ lease hợp lệ mới được cập nhật.
- Hai đối tượng đang được chuyển tenant cần tuân thủ một giao dịch nhất quán.

Khi đó “check before read” không có nghĩa không được đọc metadata: **có thể cần đọc một phần dữ liệu tối thiểu trong phạm vi tin cậy để ra quyết định**, nhưng chưa được trả body/metadata được bảo vệ cho caller. Nơi đánh giá policy nên được phép truy cập dữ liệu cần thiết, kể cả khi caller chưa có quyền xem đầy đủ đối tượng.

### 8.1. Hai lớp kiểm soát trong thực tế

~~~text
Authenticate + coarse policy
            |
            v
Open transaction / establish snapshot
            |
            v
Read resource state for authorization (trusted internal read)
            |
            v
Check object-level access using current state
            |
           / \
       Deny   Allow
        |       |
    rollback   operation + cache policy + commit
~~~

Lợi ích của kiểm tra sớm: loại bỏ request chắc chắn sai mà không cấp transaction. Lợi ích của kiểm tra trong transaction: quyết định theo state liên quan đến thao tác. Tuy nhiên, chính transaction cũng không bảo đảm quyền không bị thu hồi ở nơi khác nếu policy được lưu ngoài phạm vi transaction hoặc isolation không bảo vệ predicate. Phải nêu rõ **tại mốc nào** policy được coi là hợp lệ và chấp nhận cửa sổ thu hồi quyền bao lâu.

### 8.2. Tránh TOCTOU bằng điều kiện ở thao tác ghi

Nếu hành động “approve” chỉ được phép khi `owner = actor` và `status = PENDING`, việc tách `SELECT`/`authorize` rồi `UPDATE` không điều kiện có thể bị race. Có thể cân nhắc câu lệnh cập nhật có điều kiện, optimistic version check hoặc locking phù hợp, rồi xác minh số hàng thay đổi.

Ví dụ dạng SQL khái niệm (phải điều chỉnh theo schema/engine và policy thực tế):

~~~sql
UPDATE requests
SET status = 'APPROVED',
    version = version + 1
WHERE id = :request_id
  AND owner_id = :actor_id
  AND status = 'PENDING'
  AND version = :expected_version;
~~~

Nếu cập nhật được 0 hàng, chưa thể khẳng định ngay lý do là “không tồn tại”, “không có quyền”, “trạng thái không hợp lệ” hay “xung đột phiên bản”. API cần ánh xạ trạng thái đúng mà không tiết lộ sự tồn tại của dữ liệu bị bảo vệ ngoài hợp đồng. Ví dụ SQL không thay thế kiểm tra cấp tenant/permission khác nếu nghiệp vụ đòi chúng.

## 9. Khóa cache, phân vùng tenant và quyền theo trường

### 9.1. Định nghĩa cache key theo semantics

Một cache key của result không nên chỉ lấy tham số thứ nhất của method rồi giả định mọi caller giống nhau. Cân nhắc:

- **Tenant:** nếu ID chỉ duy nhất bên trong tenant, khóa phải chứa tenant ID được xác minh.
- **Resource:** ID, version hoặc revision của đối tượng.
- **Representation:** ngôn ngữ, định dạng hoặc các field được phép hiển thị.
- **Authorization context:** nếu nội dung thực sự khác theo role/user, phân vùng theo principal/role/scope đã xác thực hoặc không cache representation đó.
- **Consistency requirement:** phiên bản đã commit, policy epoch hoặc snapshot token khi contract yêu cầu.
- **Negative result:** cache lỗi “không tồn tại”/“bị từ chối” cẩn thận vì có thể stale và có nguy cơ phân biệt sự tồn tại tài nguyên.

Không nên lưu token truy cập, mật khẩu hay dữ liệu xác thực thô vào key hoặc log. Principal/tenant lấy từ ngữ cảnh tin cậy; với principal thay đổi theo phiên, key cần quy tắc ổn định và có kiểm soát vòng đời.

### 9.2. Cache các đối tượng được lọc theo quyền

Một API tìm kiếm trả danh sách tài liệu có thể lọc theo quyền của caller. Cache toàn bộ kết quả danh sách chỉ theo query string rồi trả cho mọi người sẽ lộ danh sách. Có thể cache tập ID public/metadata không nhạy cảm và lọc theo quyền tại thời điểm trả kết quả, hoặc cache theo scope chính xác. Nếu lọc theo quyền trước rồi cache “danh sách được phép”, policy revocation và scope thay đổi vẫn yêu cầu invalidation.

Với **field-level authorization**, cùng một resource có thể trả các trường khác nhau cho từng role. Cache DTO toàn bộ rồi áp filter ngoài phải chắc chắn mọi đường serialize/export đều đi qua filter, và không để object nhạy cảm lọt vào log hay callback. Nếu một field phụ thuộc thời điểm/token, cache representation có thể cần tránh hoàn toàn.

## 10. Transaction và cache trên đường ghi

Trong command như `updateDocument`, mục tiêu không chỉ là ngăn Bob đọc nhầm mà còn ngăn một lệnh ghi chưa được phép tạo trạng thái phụ.

### 10.1. Một vòng đời an toàn hơn cho mutation

1. Xác thực caller, kiểm tra sơ bộ role/tenant.
2. Mở transaction thích hợp.
3. Đọc trạng thái resource, thực thi object-level authorization theo trạng thái nhất quán.
4. Kiểm tra invariant và concurrency; ghi dữ liệu.
5. Commit; chỉ sau commit thành công mới thông báo cache invalidation/update và external event phù hợp hợp đồng.
6. Nếu rollback, không publish kết quả/cache mới; audit từ chối hoặc sự kiện lỗi nếu bắt buộc phải có cơ chế lưu độc lập.

Nếu advice cache viết ngay khi method domain trả về nhưng outer transaction chưa commit, cache có thể chứa state không tồn tại. Nếu advice transaction chạy sau callback phát external event, rollback cũng không rút lại event đó. Xác định **điểm commit thật** và **chủ sở hữu side effect**.

### 10.2. Sau commit cũng có race

Dạng “DB commit -> invalidate cache” có khoảng thời gian cache cũ vẫn phục vụ. Nếu yêu cầu consistency nghiêm ngặt, thiết kế version/lease/fencing token, phối hợp giao thức invalidation, hoặc chọn không cache đường quan trọng. Không coi việc gọi một callback sau commit là bảo đảm tuyệt đối khi process có thể chết ở giữa.

Dùng outbox cho sự kiện cần phát đáng tin là một lựa chọn, nhưng outbox không tự xử lý cache hit bị bypass authorization: hai vấn đề khác nhau.

## 11. Bảo vệ trước side channel và lỗi ứng dụng

**Thứ tự đúng về mặt control flow** vẫn chưa chắc không rò rỉ:

- **Timing:** cache hit/miss có thể khác độ trễ. Nếu kẻ gọi trái phép có thể quan sát chênh lệch trước khi deny, chúng có thể suy luận sự tồn tại của tài nguyên.
- **Exception mapping:** một advice bọc ngoài có thể chuyển `AccessDenied` thành fallback giá trị cached, làm mất ý nghĩa từ chối.
- **Logging/audit:** không in toàn bộ resource body, ACL, token hoặc user-specific cache key để chẩn đoán một denial. Audit cần lưu đủ loại action, actor đã xác thực, kết quả và thời điểm theo quy định riêng.
- **Status code:** một số API cố tình không phân biệt 403/404 để tránh tiết lộ object existence; quy tắc này phải nhất quán ở cache hit/miss.
- **Data serialization:** không cache object nhạy cảm rồi để một serializer khác tự xuất trước bước redact.
- **Error cache:** không dùng exception cache chung theo resource ID nếu thông báo lỗi chứa nội dung riêng của một người dùng.

Nếu rate limiting đặt ngoài authorization để chặn abuse sớm, nó phải có hợp đồng key/scope riêng và không được truy xuất hay phát nội dung bảo vệ. Một giới hạn IP hoặc session ở cửa vào khác với object-level authorization của tài nguyên.

## 12. Aspect composition, pointcut và đường bypass

### 12.1. Pointcut phải áp dụng ở đúng boundary

Một advice dùng pointcut theo quy ước tên method `get*` có thể bỏ lỡ `download`, `export` hoặc lời gọi nội bộ trực tiếp; một advice quá rộng có thể chặn cả thao tác chỉ đọc metadata public và tạo overhead. Với proxy, các lời gọi không đi qua proxy (ví dụ một self-call từ đối tượng mục tiêu) có thể không kích hoạt advice; cơ chế weaving có tập join point khác và cần kiểm tra riêng.

Không để bất biến ủy quyền sống **duy nhất** trong một lớp dễ bypass. Có thể cần kiểm tra ở service/domain và cơ chế enforcement ở datastore như row-level security khi phù hợp. Chúng là lớp phòng vệ bổ sung, vẫn phải được cấu hình/kiểm thử đúng, và cache bên ngoài chúng phải được kiểm soát.

### 12.2. Xác định thứ tự advice một cách tường minh

Nhiều Aspect có thể khai báo priority/order; chi tiết thứ tự phụ thuộc cơ chế AOP. Điều quan trọng là xác nhận bằng **integration test trên thứ tự thực**, bao gồm:

- Advice nào chạy trước `proceed()` và sau `proceed()`?
- Có advice nào bỏ qua `proceed()` do cache hit, denial, circuit open hoặc fallback không?
- Có retry gọi `proceed()` nhiều lần khiến authorization/transaction chạy nhiều lần hoặc không chạy trên từng attempt không?
- Exception bị bắt/chuyển ở lớp nào? Một denial có bị một outer fallback advice nuốt không?
- Callback async có phát kết quả từ một đường không được kiểm tra quyền không?

Đặc biệt, phép đổi thứ tự `A -> C` sang `C -> A` là **thay đổi hành vi bảo mật** dù thân mã của từng Aspect không đổi. Cần review và kiểm thử như thay đổi security-sensitive.

### 12.3. Vấn đề async và streaming

Với `Future`/`CompletionStage`, quanh lời gọi trả về handle chưa chắc bảo vệ mọi lúc tạo và phát kết quả. Nếu callback hoặc worker về sau truy cập resource với context khác, phải định nghĩa quyền dùng ở **thời điểm submit** hay **thời điểm thực thi**, cách truyền principal và thời điểm recheck. Không tự động “carry” `ThreadLocal` sang thread khác.

Với stream, quyền có thể cần kiểm tra khi mở subscription và/hoặc mỗi lúc dữ liệu được phát nếu policy có thể bị thu hồi trong suốt stream. Một cache phần tử stream đặt ngoài enforcement có thể lộ dữ liệu sau khi subscriber mất quyền. Xác định hủy/close stream và data redaction đúng vòng đời.

## 13. Kiểm thử trọng tâm: cache hit, denial và isolation

Để kiểm chứng thứ tự, kiểm thử ít nhất **cùng một resource với hai principal khác quyền** và ép chuyển trạng thái từ cache miss sang hit.

| Kịch bản | Điều phải chứng minh |
| --- | --- |
| Alice được phép, cache miss | A chạy; store chỉ được gọi sau allow |
| Bob bị từ chối khi cache còn trống | Không truy xuất dữ liệu không phép; không bắt đầu mutation |
| **Alice warm cache, Bob request cùng key** | **Bob vẫn bị từ chối, cache hit không bypass A** |
| Bob bị từ chối, Alice request lại | Không poison cache; Alice nhận kết quả hợp lệ |
| Alice mất quyền sau khi cache được làm nóng | Recheck/invalidation theo hợp đồng thu hồi; không trả dữ liệu trái phép |
| Hai tenant trùng document ID | Không cross-tenant cache hit |
| Cùng user nhưng scope/representation khác | Không lộ field khác scope |
| Cache miss rồi transaction rollback | Không publish value chưa commit |
| Update thành công rồi invalidation lỗi | Hệ thống phát hiện/khôi phục theo consistency contract |
| Authorization cần mutable ACL | Đánh giá trạng thái đúng tại điểm nhất quán, tránh TOCTOU |
| Advice ngoài trả fallback | Không biến denial thành success |
| Self-call hoặc đường async bypass proxy | Policy vẫn được thực thi ở boundary đáng tin |
| Load lớn, concurrency cao | Đồng bộ cache/transaction phù hợp; không leak qua race hoặc stale context |

### 13.1. Test thứ tự chính xác

Dùng fake `Authorization`, `Transaction`, `Cache` và `Business` ghi một event log có cấu trúc. Kiểm tra các chuỗi expected cụ thể:

~~~text
DENY:
A.check -> A.deny
(khong thay T.begin, C.hit, B.execute)

ALLOW + CACHE_HIT:
A.check -> A.allow -> C.hit -> return
(khong can T.begin neu thu tu A -> C -> T -> B)

ALLOW + CACHE_MISS:
A.check -> A.allow -> C.miss -> T.begin
  -> B.execute -> T.commit -> C.publish/return
~~~

Dòng `C.publish` là **logic minh họa**: với cache result từ một read-only transaction, có thể đặt lưu cache sau khi kết quả được xác nhận. Với mutation, đợi commit/invalidation theo cơ chế riêng; không ép mọi kiểu cache dùng cùng hook.

### 13.2. Dùng concurrency test thay vì ngủ đoán chừng

TOCTOU chỉ xuất hiện khi các thao tác đan xen. Dùng `CountDownLatch`, `Phaser`, fake clock và transaction boundary được điều khiển để:

1. Tạm dừng sau khi A đọc quyền.
2. Thu hồi hoặc thay đổi quyền/tài nguyên ở một luồng khác.
3. Cho phép transaction tiếp tục.
4. Xác nhận hệ thống xử lý theo contract đã chọn: deny, retry với version mới hoặc thực thi theo một snapshot đã được chấp nhận.

Phải chỉ rõ contract về revocation và snapshot, nếu không một test chỉ “mong deny” có thể sai với hệ thống cố tình cho phép operation đã bắt đầu hoàn tất.

## 14. Những anti-pattern dễ mắc

| Anti-pattern | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| `Cache -> Auth` với cache chung dữ liệu nhạy cảm | Cache hit bypass kiểm tra quyền | Đưa enforcement ra ngoài cache, hoặc xác thực quyền trong mọi đường trả |
| Chỉ tin cached “allow” không có invalidation | Quyền bị thu hồi nhưng vẫn truy cập được | Policy version/expiry/invalidation/kiểm tra authoritative theo mức bảo đảm |
| Đưa user ID do client gửi vào cache key làm “bảo mật” | Giả mạo danh tính, rò chéo người dùng | Principal tin cậy, check policy thực |
| Chỉ chặn ở proxy, datastore cho gọi trực tiếp | Đường bypass vi phạm chính sách | Tăng bảo vệ ở boundary tin cậy, kiểm tra self-call |
| Mở transaction là tự hết TOCTOU | Quyền vẫn có thể đổi sau check | Isolation/lock/version và ràng buộc trên lệnh ghi |
| Cache.put trước transaction commit | Dữ liệu rollback vẫn được phục vụ | Ghi/invalidate sau commit và có recovery |
| Mọi denial đều rollback “toàn hệ thống” | Side effect bên ngoài không bị thu hồi | Thiết kế scope thực tế, audit và compensation |
| Denial bị outer fallback đổi thành giá trị cached | Truy cập trái phép trông như thành công | Giữ nguyên access-denied semantics |
| Không test cache đã ấm | Không phát hiện lỗi cấp quyền | Test warm-cache cross-principal/cross-tenant |
| Chỉ đo số request thành công | Mất dấu deny, attack/abuse | Metric phân loại deny và audit theo policy, không rò dữ liệu |

## 15. Checklist thiết kế trước khi đưa vào hoạt động

1. **Đơn vị được bảo vệ:** xác định action, resource, tenant, field và nguồn trusted principal.
2. **Điểm ra quyết định:** A lấy policy từ đâu, dùng snapshot/version nào và thu hồi quyền có độ trễ cho phép bao nhiêu.
3. **Mọi đường trả dữ liệu:** cache hit, miss, fallback, streaming, async và error paths đều phải chịu chính sách.
4. **Thứ tự thực tế:** ghi rõ các advice bao ngoài/bao trong và ai có thể không gọi `proceed()`.
5. **Transaction boundary:** check quyền có cần trong transaction không; dùng isolation/locking/optimistic version nào; audit có tách scope không.
6. **Cache semantics:** result cache hay decision cache, key gồm những thành phần nào, TTL và invalidation sau policy/data change ra sao.
7. **Commit và publish:** tránh cached uncommitted results; bảo đảm callback sau commit và kế hoạch xử lý lỗi.
8. **Chống bypass:** caller trực tiếp, self-call, background worker, database export, CDN và endpoint khác đều đã được phân tích.
9. **Không lộ thông tin:** timing, error, log, ETag, cache key, body/metadata đều theo cùng disclosure policy.
10. **Regression tests:** warm-cache cross-principal, cross-tenant, policy revocation, rollback/invalidation, TOCTOU và composition order.

Một cách nhớ thực tế: **kiểm soát truy cập phải bao phủ cả đường lấy dữ liệu từ cache; transaction phải bao phủ các thao tác cần bảo đảm trạng thái nhất quán; và cache phải chỉ phát dữ liệu hợp lệ với quyền cũng như trạng thái đã được phép công bố**. Thứ tự Advice là một phần của mô hình bảo mật và nhất quán, nên phải được đặc tả và kiểm chứng như chính hành vi nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-006">Nguy cơ đường gọi không đi qua cơ chế can thiệp</a>

<details>
<summary>Xem chi tiết</summary>

# Nguy cơ đường gọi không đi qua cơ chế can thiệp trong AOP

## 1. Vấn đề: có advice bảo vệ không có nghĩa mọi đường gọi đều được bảo vệ

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), ta có thể đặt advice kiểm soát truy cập tại các **join point** do **pointcut** chọn. Ví dụ, trước khi thực thi thao tác rút tiền, sửa dữ liệu hoặc xóa tài nguyên, advice có thể xác minh danh tính, quyền và phạm vi đối tượng.

Tuy nhiên, **chính sách chỉ có tác dụng khi đường thực thi thực sự đi qua cơ chế can thiệp được cấu hình**. Nếu đối tượng nghiệp vụ được gọi trực tiếp, phương thức không nằm trong tập join point hỗ trợ, đường gọi đi qua một đối tượng khác hoặc một lối vào chưa được instrument, advice có thể không chạy. Khi đó lời gọi vẫn có khả năng thực hiện tác dụng phụ, mặc dù người phát triển tin rằng đã có một lớp kiểm tra quyền bao quanh.

Rủi ro đặc biệt nghiêm trọng nếu kiểm soát truy cập tồn tại **duy nhất** trong một advice. Một đường gọi bypass có thể thực thi cùng hành vi nghiệp vụ mà không kiểm tra quyền dự kiến.

Cần trả lời bốn câu hỏi khác nhau:

1. **Hành vi nào cần được bảo vệ?** Ví dụ: thay đổi số dư, đọc hồ sơ riêng tư, tạo yêu cầu thanh toán.
2. **Điều kiện nào cho phép thực hiện?** Danh tính được xác thực, vai trò, quyền trên đối tượng, trạng thái nghiệp vụ hoặc ranh giới tenant.
3. **Cơ chế AOP thực tế can thiệp tại đâu?** Lời gọi phương thức qua proxy, thực thi phương thức đã weaving, hay một tập join point khác?
4. **Có đường gọi nào dẫn đến tác dụng phụ mà không đi qua những điểm ấy không?** Đây là câu hỏi trung tâm của bài.

Một hệ thống chỉ an toàn nếu **mọi đường có thể tới hành vi nhạy cảm đều được kiểm soát bởi một ranh giới tin cậy tương ứng**. Advice là một cơ chế áp dụng chính sách; nó không tự chứng minh độ bao phủ của tất cả đường gọi.

## 2. Hiểu đúng các thuật ngữ trước khi đánh giá bypass

| Khái niệm | Ý nghĩa liên quan đến an toàn truy cập |
| --- | --- |
| Join point | Điểm trong thực thi chương trình mà mô hình AOP cho phép áp dụng advice |
| Pointcut | Quy tắc chọn tập join point cần can thiệp |
| Advice | Logic được chạy trước/sau/quanh join point được chọn |
| Target | Đối tượng hoặc hành vi thực tế thực hiện nghiệp vụ |
| Proxy | Đối tượng đại diện có thể chặn lời gọi trước khi chuyển tiếp tới target |
| Weaving | Ghép logic aspect vào cấu trúc hoặc luồng thực thi theo cơ chế hỗ trợ |
| Call site | Vị trí thực hiện lời gọi; có thể là nơi can thiệp khác với điểm thực thi của phương thức |
| Access-control boundary | Nơi hệ thống bắt buộc xác minh quyền trước khi cho phép một tác dụng phụ nhạy cảm |

**Method-call join point** và **method-execution join point** không nhất thiết đồng nhất. Một cơ chế có thể can thiệp vào lời gọi tại các call site đã chọn; cơ chế khác can thiệp vào phần thực thi của phương thức. Cách lựa chọn này ảnh hưởng đến trường hợp tự gọi, dispatch động và các đường được biên dịch hoặc tải sau.

Tương tự, AOP dựa trên proxy và AOP dựa trên biến đổi bytecode có phạm vi can thiệp khác nhau. Không nên lấy giới hạn của một cơ chế để tuyên bố rằng **mọi** hình thức AOP đều có cùng lỗ hổng. Việc đánh giá phải căn cứ vào **cơ chế và cấu hình thực tế**.

## 3. Mô hình đơn giản: đường đi qua proxy và đường đi thẳng tới target

Giả sử một giao diện cung cấp thao tác nhạy cảm:

~~~text
                 +----------------+
Caller --------> | Guarded proxy  | ---check---> Target.sensitive()
                 +----------------+

Caller -------------------------------> Target.sensitive()
                   đường trực tiếp
~~~

Ở đường thứ nhất, caller giữ tham chiếu đến **proxy**; advice gắn với proxy có thể kiểm tra quyền. Ở đường thứ hai, caller giữ **target gốc** và gọi thẳng, nên một interceptor chỉ nằm ở proxy không được kích hoạt.

Nếu tất cả điều kiện bảo vệ được thực hiện tại proxy, đường thứ hai có thể bỏ qua chúng. Trường hợp này thường xuất hiện khi:

- Một phần mã lấy đối tượng từ cơ chế tạo proxy, phần khác tự khởi tạo target.
- Target gốc được lưu hoặc chia sẻ cùng với proxy.
- Một phương thức của target gọi phương thức khác trên chính đối tượng target.
- Một callback, lambda hoặc method reference giữ tham chiếu đến target thay vì proxy.
- Có một entry point bổ sung không qua đường cấp phát/interception dự kiến.

Không nhất thiết kẻ tấn công có quyền gọi bất kỳ phương thức Java nào trực tiếp. Để thành vấn đề bảo mật khai thác được, phải tồn tại **đường vào hoặc thành phần có thể bị điều khiển đủ để đi tới nhánh không được bảo vệ**. Tuy vậy, ngay cả khi chỉ xảy ra do lập trình sai ở nội bộ, đây vẫn là lỗi kiến trúc nghiêm trọng cần được kiểm tra.

## 4. Tự gọi phương thức: lời gọi con có thể không đi qua advice

Ví dụ khái niệm:

~~~java
class OrderService {
    void updatePrice() {
        // thao tác nhạy cảm, được kỳ vọng có advice kiểm tra quyền
    }

    void refreshOrder() {
        updatePrice(); // lời gọi nội bộ trên chính đối tượng này
    }
}
~~~

Ở một cơ chế can thiệp dựa vào proxy bên ngoài, lời gọi tới refreshOrder() đi qua proxy, nhưng lời gọi updatePrice() bên trong có thể tương đương **this.updatePrice()** trên target. Lời gọi nội bộ ấy không quay lại proxy; nếu advice chỉ chặn updatePrice() tại proxy, advice không được kích hoạt.

Nhìn từ người gọi:

~~~text
caller -> proxy.refreshOrder()
             -> target.refreshOrder()
                  -> this.updatePrice()
                       (không qua proxy.updatePrice())
~~~

Nếu refreshOrder() không có cùng chính sách kiểm tra quyền, thao tác updatePrice() có thể diễn ra mà không qua kiểm tra dự kiến.

**Giới hạn của kết luận:** đây là rủi ro điển hình với một số mô hình **proxy interception**. Một cơ chế weaving can thiệp tại method-execution join point có thể áp dụng advice khi thân updatePrice() chạy, kể cả khi được gọi nội bộ. Cơ chế weaving kiểu call-site cũng phải xét call site đó có nằm trong phạm vi được dệt hay không. Không được khẳng định tự gọi luôn vượt qua advice trong mọi AOP.

Vấn đề thực sự là **điểm can thiệp nào bảo vệ hành vi** và **đường gọi nội bộ có đi qua điểm đó hay không**.

## 5. Ví dụ Java 17+: minh họa đường gọi không qua proxy

Ví dụ dùng **Java Dynamic Proxy** của thư viện chuẩn. Một proxy kiểm tra quyền khi ai đó gọi withdraw(...) **qua proxy**. Target có phương thức batch(...) gọi withdraw(...) trên chính target, nên lời gọi bên trong không đi qua InvocationHandler của proxy.

Mã minh họa này cố ý tạo một thiết kế thiếu kiểm soát ở target để thấy sự khác biệt. Nó là chương trình học thuật cục bộ, không phải mẫu bảo mật nên triển khai.

Tạo tệp **InterceptionGapDemo.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public final class InterceptionGapDemo {
    interface Ledger {
        void withdraw(int amount);
        void batch(int amount);
    }

    static final class UnsafeLedger implements Ledger {
        int balance = 100;

        @Override
        public void withdraw(int amount) {
            balance -= amount;
            System.out.println("TARGET withdraw " + amount);
        }

        @Override
        public void batch(int amount) {
            System.out.println("TARGET batch");
            withdraw(amount); // self-invocation trên target
        }
    }

    static final class SafeLedger implements Ledger {
        int balance = 100;

        @Override
        public void withdraw(int amount) {
            // Minh họa ranh giới kiểm tra bên trong dịch vụ tin cậy.
            // Trong hệ thống thật phải kiểm tra quyền từ nguồn danh tính tin cậy.
            throw new SecurityException("denied in service");
        }

        @Override
        public void batch(int amount) {
            withdraw(amount);
        }
    }

    public static void main(String[] args) {
        UnsafeLedger target = new UnsafeLedger();

        Ledger guarded = (Ledger) Proxy.newProxyInstance(
                Ledger.class.getClassLoader(),
                new Class<?>[]{Ledger.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("withdraw")) {
                        System.out.println("PROXY check withdraw");
                        throw new SecurityException("denied at proxy");
                    }
                    try {
                        return method.invoke(target, values);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });

        try {
            guarded.withdraw(10);
        } catch (SecurityException error) {
            System.out.println("caller: " + error.getMessage());
        }

        guarded.batch(20);
        System.out.println("unsafe balance=" + target.balance);

        SafeLedger safe = new SafeLedger();
        try {
            safe.batch(20);
        } catch (SecurityException error) {
            System.out.println("safe caller: " + error.getMessage());
        }
        System.out.println("safe balance=" + safe.balance);
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac InterceptionGapDemo.java
java InterceptionGapDemo
~~~

Stdout:

~~~text
PROXY check withdraw
caller: denied at proxy
TARGET batch
TARGET withdraw 20
unsafe balance=80
safe caller: denied in service
safe balance=100
~~~

**Giải thích kết quả:**

1. guarded.withdraw(10) đi qua proxy. InvocationHandler chặn và báo lỗi; target.withdraw() không chạy ở lượt này.
2. guarded.batch(20) đi qua proxy, nhưng proxy chỉ kiểm tra tên phương thức withdraw. Nó chuyển batch() sang target bằng reflection.
3. Bên trong target.batch(), lời gọi withdraw(20) thực thi trực tiếp trên target, không quay lại InvocationHandler của proxy. Số dư giảm từ 100 xuống 80.
4. SafeLedger đặt một kiểm tra tối thiểu **trong chính phương thức thay đổi trạng thái**. Lời gọi nội bộ tới withdraw() vẫn bị từ chối; số dư không đổi.

Trong chương trình thật, điều kiện kiểm tra cần lấy danh tính đã xác thực, quyền thao tác, đối tượng đích và trạng thái liên quan từ nguồn đáng tin cậy. Dòng throw trong SafeLedger chỉ giúp chứng minh **vị trí cưỡng chế** có thể bảo vệ cả đường gọi trực tiếp lẫn lời gọi nội bộ.

**Phạm vi ví dụ:** Java Dynamic Proxy chỉ đại diện cho một kiểu interception dựa trên interface; nó không chứng minh các giới hạn của mọi cơ chế bytecode weaving. InvocationHandler nhận lời gọi qua proxy và chuyển cho target; lời gọi trong thân target không tự chuyển qua proxy.

## 6. Những nguyên nhân gây thiếu bao phủ join point

### 6.1. Đường khởi tạo đối tượng khác nhau

Một nhóm đối tượng nhận proxy từ một factory/registry, nhưng nơi khác gọi new Target() trực tiếp. Khi đó hai nơi có thể nhìn thấy cùng một kiểu nghiệp vụ nhưng khác đường thực thi:

~~~text
caller A -> factory -> proxy -> target -> guard có thể chạy
caller B -> new Target() ---------> target -> không có proxy guard
~~~

Cần quản lý nguồn tạo đối tượng và loại bỏ việc cấp phát target thô tới các caller không được phép. Nếu code có thể tạo đối tượng mới ngoài container/interception boundary, cơ chế AOP dựa vào proxy không tự kiểm soát được nó.

Không nên chữa bằng cách “nhớ gọi proxy” trong từng phương thức; đó là một kỷ luật dễ bị phá vỡ. Tốt hơn là hạn chế quyền tạo/truy cập target và cưỡng chế quyền ở nơi thực hiện effect nhạy cảm.

### 6.2. Lời gọi nội bộ, đệ quy và phương thức ủy quyền

Các tình huống như this.sensitive(), helper.sensitive() hoặc đệ quy đều phải được xem xét:

- Nếu this.sensitive() ở trên target thô, một proxy đứng ngoài không nhận cuộc gọi.
- Nếu helper là proxy riêng, cuộc gọi có thể được instrument.
- Nếu helper là target thô, advice ở proxy của helper bị bỏ qua.
- Nếu code đã được weaving ở điểm thực thi tương ứng, lời gọi nội bộ có thể vẫn kích hoạt advice.
- Nếu advice được áp dụng chỉ cho một phần kiểu/method, một nhánh đệ quy hoặc overload khác có thể không match.

Chỉ dùng tên “internal call” chưa đủ kết luận. Phải truy theo **đối tượng thực nhận lời gọi**, **join point được chọn** và **phạm vi instrumentation**.

### 6.3. Method không được cơ chế can thiệp hỗ trợ

Khả năng can thiệp khác nhau tùy cơ chế:

- Java Dynamic Proxy chuẩn hoạt động trên các interface được proxy khai báo; một phương thức chỉ có trên lớp triển khai mà không lộ trong interface không thể được gọi qua kiểu proxy ấy như một phương thức interface bình thường.
- Cơ chế subclass proxy phụ thuộc khả năng override, nên phương thức final hoặc không thể override không thể được can thiệp bằng cách override tương ứng; một số phương thức private cũng không là điểm override.
- Bytecode weaving/instrumentation có thể can thiệp vào những điểm mà proxy subclass không thể, nhưng vẫn phụ thuộc phạm vi weave, loại join point được hỗ trợ và các giới hạn runtime.
- Các lời gọi tới static method, constructor, code native, default interface method hoặc method handle có hành vi interception phụ thuộc rất cụ thể vào mô hình AOP; không được suy luận chung là đều được bảo vệ hay đều bị bỏ qua.

Khi thiết kế pointcut, phải kiểm tra riêng từng kiểu phương thức sử dụng trong ứng dụng. Đặc biệt, không nên chọn AOP interceptor làm **điểm bảo vệ duy nhất** nếu không kiểm chứng được khả năng intercept đối với mọi biến thể cần bảo vệ.

### 6.4. Sai phạm vi pointcut hoặc điều kiện match

Một pointcut có thể bỏ sót phương thức do:

- Tên kiểu, package, chữ ký hoặc modifier không đúng biểu thức chọn.
- Chỉ chọn public methods trong khi effect thực nằm ở một helper khác.
- Chỉ chọn một overload, bỏ sót overload có khả năng ghi dữ liệu.
- Kiểu runtime khác kiểu mong đợi do kế thừa, implement interface hoặc dynamic dispatch.
- Aspect chỉ được áp dụng cho một module/classloader/namespace cụ thể.
- Điểm cắt chọn nơi gọi chứ không phải nơi thực thi, nên một call site khác nằm ngoài phạm vi đã dệt.

**Pointcut không phải bản khai chính sách truy cập.** Biểu thức chọn là công cụ định tuyến advice. Chính sách quyền nên được định nghĩa độc lập bằng operation, resource, principal và action để khi đổi cách instrument, yêu cầu bảo vệ vẫn nhất quán.

### 6.5. Bỏ qua một đường vào khác

Cùng thao tác có thể được kích hoạt từ:

- Yêu cầu tương tác từ người dùng.
- Job theo lịch.
- Handler nhận message hoặc event.
- Callback và background worker.
- Công cụ quản trị, tác vụ dữ liệu hoặc batch import.
- Một API nội bộ, entry point mới hoặc đường gọi được thêm sau này.

Nếu chỉ instrument đường qua một lớp xử lý request, các đường từ job hoặc worker có thể không có advice tương tự. Điều cần kiểm tra là **mọi entry point dẫn đến cùng effect nhạy cảm**, không chỉ endpoint chính.

Một request có kiểm soát quyền trước khi gọi service không tạo đảm bảo cho job đang gọi trực tiếp service. Ngược lại, job có danh tính hệ thống được cấp quyền đặc biệt phải được kiểm soát theo một principal và scope được xác định rõ, thay vì mặc định “nội bộ thì toàn quyền”.

### 6.6. Callback và thực thi bất đồng bộ

Một phương thức bị intercept có thể chỉ lập lịch công việc rồi trả về:

~~~text
caller -> proxy.submit()
             -> target.submit() tạo Runnable
             -> trả về
                               Runnable.run() trên worker
                                  -> sensitive effect
~~~

Nếu policy chỉ can thiệp vào submit(), chưa chắc nó can thiệp vào Runnable.run() hoặc lời gọi thực sự làm thay đổi tài nguyên. Điều này đặc biệt quan trọng khi quyền có thể đổi giữa lúc submit và lúc chạy.

Có hai câu hỏi phải tách:

1. Người gọi có quyền **đăng ký/khởi tạo** công việc không?
2. Tác vụ thực thi sau đó có quyền **thực hiện effect** trên tài nguyên tại thời điểm cần thiết không?

Cơ chế context propagation có thể chuyển identity/correlation tới worker, nhưng **mang theo identity không tương đương tự xác minh authorization**. Nếu quyền phải được kiểm tra lại ở thời điểm thực thi, worker cần thực hiện kiểm tra tại ranh giới đó.

### 6.7. Lời gọi qua reflection, method handle và code sinh động

Một lời gọi phản chiếu tới target vẫn có thể đi vào thân phương thức mà không qua proxy nếu nó dùng tham chiếu target thô. Ngược lại, reflection được gọi trên proxy có thể vẫn đi qua cơ chế dispatch của proxy. Vì vậy phát biểu “reflection luôn bỏ qua AOP” là sai.

Cần xác định **đối tượng đích của lời gọi reflection** và cách cơ chế interception tích hợp với reflection/method handle. Với runtime bytecode weaving, câu trả lời còn phụ thuộc vị trí và thời điểm mã được dệt.

Các lớp được tải muộn, sinh động hoặc tải qua classloader khác cũng có thể nằm ngoài scope weave nếu cấu hình không bao phủ chúng. Mỗi thay đổi về cách tải/mở rộng mã cần có kiểm tra độ bao phủ.

## 7. Phân biệt sự cố độ bao phủ và sự cố chính sách

Không phải mọi lỗi truy cập đều do bypass. Có hai loại nguyên nhân:

**Loại A — advice không chạy:** đường gọi không đi qua điểm can thiệp. Log cho thấy không có sự kiện “guard evaluated” ở operation nhạy cảm; dù chính sách đúng trên giấy, nó không được thực thi.

**Loại B — advice chạy nhưng quyết định sai:** principal bị giả mạo, thiếu tenant check, cache quyết định quyền lỗi thời, giá trị role được lấy từ nguồn không tin cậy, hoặc kiểm tra quyền chỉ dựa trên chức danh mà bỏ đối tượng cụ thể.

Cả hai có thể cùng tồn tại. Một kiểm thử chỉ xác nhận “advice đã chạy” không chứng minh quyết định quyền chính xác. Một kiểm thử chỉ xác nhận “người không có quyền bị từ chối ở endpoint A” không chứng minh endpoint B hay background handler an toàn.

Nên quan sát riêng:

~~~text
entrypoint -> sensitive operation
        |
        +-- instrumentation applied?  YES/NO
        |
        +-- authorization evaluated?  YES/NO
        |
        +-- resource/action checked?  YES/NO
        |
        +-- decision                ALLOW/DENY/ERROR
        |
        +-- effect occurred?         YES/NO
~~~

Điều kiện cần quan trọng: **không được có một trường hợp principal không được phép mà effect nhạy cảm vẫn xảy ra**, bất kể lời gọi đến qua đường nào.

## 8. Đường gọi nào thực sự cần được bảo vệ?

Đánh giá bắt đầu từ **tác dụng phụ nhạy cảm** và **dữ liệu cần bảo vệ**, rồi truy ngược các đường gọi dẫn đến chúng:

~~~text
External entry points             Internal pathways               Sensitive effect
----------------------            ------------------              ----------------
UI request ---------------------> controller ----------------\
Scheduled job ------------------> scheduler handler ---------+--> accountService.debit()
Message consumer ---------------> event handler -------------/
Admin tool ---------------------> import service -------------/
Internal call ------------------> helper ---------------------/
~~~

Nếu kiểm tra quyền chỉ xuất hiện ở controller, các nhánh khác chưa chắc được bảo vệ. Nếu đặt guard ở accountService.debit() nhưng dùng proxy và một caller lấy target trực tiếp, vẫn chưa có bảo đảm. Hãy đặt **ranh giới cưỡng chế quyền có độ tin cậy cao** gần thao tác nhạy cảm, đồng thời áp dụng guard ở các biên vào để từ chối sớm và nâng cao khả năng quan sát.

Đây không nhất thiết là yêu cầu kiểm tra trùng lặp mọi nơi. Mục tiêu là có một **điểm thực thi chính sách không thể bị bỏ qua bởi các đường hợp lệ trong kiến trúc**, với kiểm tra bổ sung ở các biên phù hợp. Ví dụ, dịch vụ bảo vệ tài nguyên có thể không cấp thao tác thay đổi trạng thái nếu thiếu dữ liệu authorization đã xác minh, bất kể ai là caller.

Cần thận trọng với các hàm “trusted”, “system”, “internal” chỉ dựa trên tên. Nếu chúng dẫn đến cùng effect mà bỏ qua xác thực/authorization, phải có một mô hình trust rõ ràng: ai được gọi, principal nào được sử dụng và cấp quyền theo cơ chế nào.

## 9. Các chiến lược thiết kế giảm rủi ro bypass

### 9.1. Đặt kiểm tra bất biến ở lớp có quyền thực hiện effect

Một chiến lược mạnh là biến quyền truy cập thành điều kiện bắt buộc của API thực hiện effect. Ví dụ, phương thức thực sự ghi một bản ghi phải đòi hỏi chính sách cho phép thao tác trên bản ghi đó; một proxy chỉ hỗ trợ áp dụng policy nhất quán và giảm lặp.

Phân biệt:

- **Advice tiền kiểm:** từ chối nhanh, ghi dấu truy cập, chuẩn hóa log, áp chính sách chung ở đường gọi được instrument.
- **Guard ở ranh giới tài nguyên/dịch vụ tin cậy:** đảm bảo effect không thể được kích hoạt bởi một đường đi không kiểm tra quyền.

Nếu hai tầng cùng kiểm tra, nên dùng cùng nguồn chính sách hoặc quy tắc được kiểm chứng để tránh lệch quyết định. Bảo vệ ở tầng dữ liệu như quyền truy cập của tài khoản database hay phân tách tenant có thể bổ sung rào chắn khi lỗi application xảy ra.

### 9.2. Kiểm soát vòng đời và tham chiếu đối tượng

Với mô hình proxy, bảo đảm caller bên ngoài chỉ nắm được tham chiếu tới proxy khi cần intercept; tránh trả target thô từ registry/factory hoặc public API. Hạn chế trực tiếp việc khởi tạo và chia sẻ các object cung cấp nghiệp vụ nhạy cảm.

Đừng cố sửa bằng cách ép mọi phương thức tự gọi một proxy toàn cục nếu điều đó sinh ra vòng phụ thuộc và khó kiểm soát. Nếu cần đường can thiệp nhất quán, có thể tái cấu trúc các thao tác nhạy cảm sang một thành phần riêng có ranh giới API rõ ràng, hoặc dùng cơ chế weaving phù hợp và kiểm thử độ bao phủ.

### 9.3. Dùng cơ chế can thiệp phù hợp với join point cần bảo vệ

Khi cần intercept method-execution bên trong target, một cơ chế weaving có khả năng bao phủ đúng join point có thể phù hợp hơn proxy interception. Tuy nhiên, weaving cũng không thay thế phân tích bảo mật:

- Phải biết tập class/method được weave.
- Phải kiểm tra thứ tự các advice.
- Phải kiểm tra lỗi tải/biến đổi bytecode có bị bỏ qua âm thầm không.
- Phải xem xét những thành phần nạp khác classloader hoặc chạy ở môi trường build/deployment khác.

Lựa chọn cơ chế là quyết định kỹ thuật; **chứng cứ bao phủ** mới là cơ sở khẳng định một đường đi được bảo vệ.

### 9.4. Ưu tiên fail closed ở các điểm quyết định truy cập

Nếu việc lấy principal hoặc dịch vụ kiểm tra quyền thất bại, một policy cho phép truy cập theo mặc định có thể biến sự cố hạ tầng thành bypass. Với thao tác nhạy cảm, cần quy định trạng thái thiếu thông tin là từ chối hoặc một outcome không thực thi effect theo chính sách.

Cẩn thận với fallback advice:

~~~text
Fallback(Authorization(operation))
~~~

Nếu fallback bắt lỗi authorization rồi trả hoặc thực hiện một nhánh thay thế không kiểm tra quyền, nó có thể che dấu từ chối. Một đường fallback trả dữ liệu công khai có kiểm soát khác với đường fallback tiếp tục thực hiện effect đặc quyền. Thứ tự các advice phải được xem xét theo loại lỗi được phép fallback.

### 9.5. Tách authentication, authorization và context

**Authentication:** người gọi là ai? **Authorization:** principal đó được làm gì trên resource nào? **Context propagation:** làm sao thông tin đã xác minh được mang tới điểm thực thi?

Một ThreadLocal lưu principal không tự bảo đảm độ tin cậy của principal. Trong tác vụ bất đồng bộ, context có thể bị mất hoặc giữ sót từ request khác nếu không capture/restore đúng scope. Ngược lại, chuyển context đầy đủ tới worker không có nghĩa guard đã chạy.

Một mô hình tốt:

~~~text
trusted ingress:
    verify identity
    construct trusted principal context
    invoke operation

sensitive boundary:
    resolve principal from trusted context
    evaluate permission for action + resource
    deny before side effect if unauthorized

async handoff:
    capture trusted context according to contract
    bind for worker scope
    re-evaluate permission if required at execution time
    restore worker's previous context
~~~

Khi quyền có thể đổi giữa lúc xếp hàng và lúc xử lý, cần quyết định rõ kiểm tra tại thời điểm **enqueue**, tại thời điểm **execute** hay cả hai. Điều này phụ thuộc hợp đồng nghiệp vụ, không tự giải quyết nhờ AOP.

## 10. Thứ tự advice có thể tạo một đường vòng logic

Không chỉ bypass theo cơ chế proxy, thứ tự nhiều advice cũng có thể làm một guard bị **bỏ qua trên một số nhánh**.

### 10.1. Cache ở ngoài authorization

~~~text
Cache(
  Authorization(
    readResource
  )
)
~~~

Nếu Cache trả dữ liệu ngay khi cache hit, nó không gọi Authorization bên trong. Với tài nguyên theo quyền cá nhân hoặc tenant, đây có thể là lỗi nghiêm trọng: người chưa được quyền có thể nhận một giá trị cache đã lưu từ người khác nếu cache key và chính sách không an toàn.

Cần đặt chính sách truy cập ở ranh giới nhìn thấy **cả hit và miss**, hoặc thiết kế cache có phân vùng theo principal/resource, kết hợp với kiểm tra quyền luôn đúng. Đổi thứ tự đơn thuần chưa đủ nếu bản thân resource access vẫn thiếu guard ở tầng tin cậy.

### 10.2. Retry ở ngoài guard

~~~text
Retry(
  Authorization(
    operation
  )
)
~~~

Nếu mỗi attempt đều đi qua Authorization, policy có thể được đánh giá lại mỗi lần. Nhưng nếu Retry chỉ bọc một thunk giữ sẵn target thô, hoặc guard chỉ chạy ở bước submit mà hành vi thực chạy ở callback khác, retry có thể không lặp việc kiểm tra. Phải xác định **cái gì thực sự được proceed() gọi**.

### 10.3. Fallback thay đổi outcome của lỗi truy cập

~~~text
Fallback(
  Authorization(
    operation
  )
)
~~~

Nếu fallback bắt SecurityException và chuyển thành đường xử lý “thành công” có effect mà không đánh giá quyền, chính sách truy cập bị vô hiệu trên nhánh lỗi. Không nên đưa lỗi từ chối quyền vào nhóm lỗi có thể retry/fallback theo kiểu hạ tầng tạm thời.

### 10.4. Recorder không chứng minh guard đã chạy

Một log “operation success” từ outer advice không thể là bằng chứng rằng guard trong inner advice đã chạy: cache hoặc fallback có thể trả kết quả mà không gọi inner advice. Nếu cần audit về quyền, phải ghi sự kiện tại **điểm ra quyết định quyền** với định danh resource, action và outcome phù hợp, đồng thời bảo đảm việc audit không tiết lộ dữ liệu nhạy cảm.

### 10.5. Advice khác gây lỗi hoặc có tác dụng phụ trước guard

~~~text
LoggingWithExternalSideEffect(
  Authorization(
    operation
  )
)
~~~

Nếu logging thực sự gửi dữ liệu nhạy cảm ra ngoài trước khi Authorization quyết định, dù operation bị từ chối, effect ghi log đã xảy ra. Trật tự phải dựa trên **những gì advice bên ngoài thực hiện**, không chỉ tên gọi “logging”.

## 11. Kiểm thử độ bao phủ như một thuộc tính bảo mật

Một test đơn giản “gọi proxy.sensitive() với người không có quyền và thấy exception” chỉ chứng minh **đường proxy đó** được bảo vệ. Cần bổ sung kiểm thử các đường dẫn khác có khả năng tới effect nhạy cảm.

### 11.1. Tạo danh mục các đường gọi

Bắt đầu từ thao tác nhạy cảm, lập bảng:

| Đường gọi | Đối tượng thực được gọi | Kiểm tra quyền đặt ở đâu? | Kỳ vọng |
| --- | --- | --- | --- |
| Entry point chính | Proxy hoặc đối tượng được weave | Guard được xác định | Người không có quyền bị chặn |
| Một phương thức gọi phương thức khác trên this | Target nội bộ | Kiểm tra ở ranh giới effect hoặc weaving thật | Không bỏ qua quyền |
| Thành phần khác giữ target gốc | Raw target | Guard độc lập với proxy | Không có đường thực thi effect trái quyền |
| Callback trên worker | Task/worker object | Guard và context tại lúc thực thi | Không dùng quyền sai hoặc đã hết hiệu lực |
| Job/consumer | Service hoặc handler riêng | Chính sách cho principal hệ thống | Chỉ quyền được cấp |
| Reflection/method handle | Proxy hay target cụ thể | Cơ chế instrument và guard tương ứng | Không có đường bỏ qua do thay kiểu gọi |
| Cache hit/fallback | Outer advice | Guard tại đường có thể trả kết quả | Không trả dữ liệu trái quyền |
| Đường gọi từ module/classloader khác | Object được dispatch thật | Weaving/interception có bằng chứng | Phạm vi bảo vệ vẫn đúng |

Bảng này chỉ là mẫu tư duy; phải điền bằng các đường gọi thực tế của ứng dụng, không suy đoán dựa trên tên lớp.

### 11.2. Kiểm thử âm tính ở cấp effect

Với một principal **không** được quyền:

1. Gọi từng entry point có thể kích hoạt hành vi nhạy cảm.
2. Xác nhận kết quả là từ chối đúng hợp đồng.
3. Quan trọng hơn: xác nhận **không có effect nhạy cảm nào xảy ra** — không đổi số dư, không tạo bản ghi, không gửi thông điệp, không lộ dữ liệu.
4. Nếu có transaction, xác nhận trạng thái dữ liệu sau rollback và effect ngoài transaction.
5. Nếu đường gọi bất đồng bộ, chờ hoặc theo dõi đến ranh giới hoàn tất thích hợp; không dừng kiểm tra ở thời điểm future vừa được trả về.

Một test chỉ assert exception nhưng không kiểm tra side effect có thể bỏ sót trường hợp thao tác được thực thi rồi mới bị chặn.

### 11.3. Kiểm thử dương tính

Với principal hợp lệ, xác nhận thao tác thành công và advice không vô tình chặn đường được cấp phép. Điều này giúp phân biệt “không có đường bypass” với một hệ thống vô tình từ chối mọi thứ.

### 11.4. Bằng chứng advice thật sự kích hoạt

Trong test, có thể dùng một probe để đếm số lượt advice tương ứng với join point nhạy cảm, rồi đối chiếu với số lượt hiệu ứng nghiệp vụ. Tuy nhiên:

- Đếm advice không đủ chứng minh policy quyết định đúng.
- Một method được advice chạy nhiều lần có thể chỉ tạo effect một lần.
- Một effect có thể xảy ra ngoài method đã được instrument.
- Một phần số liệu có thể bị thiếu do sampling hoặc telemetry lỗi.

Do đó, hãy kết hợp **dấu vết interception**, **quyết định authorization** và **trạng thái effect thực tế**.

## 12. Rà soát tĩnh và kiểm tra cấu hình build/deploy

Kiểm thử động không thể duyệt hết mọi đường gọi. Có thể bổ sung phân tích:

- Tìm những nơi tự khởi tạo lớp có hành vi nhạy cảm thay vì dùng thành phần đã được kiểm soát.
- Tìm chỗ cấp phát, lưu hoặc expose target gốc từ proxy factory/registry.
- Kiểm tra lời gọi this.method(), method references và callback được tạo từ target.
- Đối chiếu pointcut với các overload, modifiers, classloader và module thực tế.
- Kiểm tra những method không thể override nếu cơ chế đang dùng là subclass proxy.
- Với weaving, xác nhận instrumentation được áp dụng cho build artifact thực sự triển khai và không bỏ qua lỗi weave.
- Kiểm tra các cấu hình khác nhau giữa chạy test, chạy local và production, kể cả các thành phần tải động.
- Khi refactor hoặc thêm entry point mới, cập nhật danh mục đường tới effect nhạy cảm.

Một báo cáo “đã có aspect được khai báo” không phải bằng chứng rằng toàn bộ class đã được can thiệp. Cần quan sát cấu trúc runtime, test và cấu hình weaving/proxy thực tế.

## 13. Mối quan hệ với principle of least privilege

**Least privilege** yêu cầu mỗi caller, service account hoặc tác vụ chỉ có quyền tối thiểu cần thiết. Nếu một tầng nghiệp vụ được gọi qua đường bỏ advice mà vẫn giữ quyền database quá rộng, lỗi bypass có thể dẫn đến thay đổi tài nguyên vượt phạm vi dự kiến.

Các biện pháp bổ trợ:

1. Chia trách nhiệm thành các API có hành động và resource rõ ràng.
2. Dùng danh tính và quyền được xác minh tại biên đáng tin cậy.
3. Kiểm tra authorization gần tài nguyên cần bảo vệ, kể cả quan hệ sở hữu/tenant nếu có.
4. Hạn chế quyền của tài khoản dịch vụ/DB, tránh cho một đường gọi bypass có toàn quyền với mọi bản ghi.
5. Không cho caller tự xác nhận “isAdmin=true” hoặc tạo một context tự khai rồi vượt qua chính sách.
6. Giới hạn việc expose đối tượng thô có thể thực hiện hành vi nhạy cảm.
7. Tách read-only API khỏi mutating API khi điều đó giúp giảm bề mặt truy cập.

AOP có ích để áp chính sách nhất quán và giảm code lặp, nhưng không thay thế cấu trúc quyền thực tế của resource manager, hệ thống xác thực và ranh giới dữ liệu.

## 14. Những nhầm lẫn phổ biến cần tránh

**Nhầm lẫn 1: “Có annotation/aspect ở phương thức nên bất kỳ lời gọi nào cũng được kiểm tra.”** Điều này chỉ đúng khi cơ chế runtime thật sự intercept join point đó trên mọi đường cần thiết. Kiểm tra cơ chế proxy/weaving và điểm vào.

**Nhầm lẫn 2: “Tự gọi phương thức luôn bỏ qua AOP.”** Sai nếu khẳng định cho tất cả công cụ. Proxy interception có rủi ro self-invocation; method-execution weaving có thể xử lý khác. Phải xác định mô hình.

**Nhầm lẫn 3: “Đổi sang reflection là sẽ bỏ qua guard.”** Không có quy tắc chung: reflection trên proxy có thể vẫn đi qua handler; reflection trên target thô có thể không. Điều quan trọng là receiver và vị trí interception.

**Nhầm lẫn 4: “Chặn API đầu vào là đủ; không cần bảo vệ service.”** Khi có job, worker, event consumer hoặc API khác tới cùng effect, một ranh giới đầu vào có thể không bao phủ mọi đường.

**Nhầm lẫn 5: “ThreadLocal đã có principal nên worker tự được bảo vệ.”** Context có thể không được propagate; có context cũng không có nghĩa authorization đã được đánh giá. Phải xác thực nguồn và kiểm tra đúng thời điểm.

**Nhầm lẫn 6: “Cache hit không có effect nên không gây rủi ro.”** Trả dữ liệu mật cho principal sai là một effect bảo mật quan trọng, dù không có ghi database.

**Nhầm lẫn 7: “Chỉ cần đếm advice đã chạy.”** Một số effect vẫn có thể xuất hiện trên nhánh không instrument; ngoài ra guard có thể đưa ra quyết định sai dù được gọi. Phải xác minh dữ liệu và effect.

**Nhầm lẫn 8: “Hủy future hoặc timeout chứng minh không có effect.”** Tác vụ có thể vẫn chạy và ghi dữ liệu sau khi caller nhận timeout. Cần theo dõi completion và thiết kế cancellation/idempotency rõ ràng.

**Nhầm lẫn 9: “Chính sách nội bộ có thể luôn fail open.”** Nếu thiếu danh tính hoặc service xác minh quyền, thao tác nhạy cảm phải có chính sách thất bại an toàn theo hợp đồng.

**Nhầm lẫn 10: “Test một endpoint thành công là xác nhận toàn bộ bảo vệ.”** Cần test mọi nhóm đường vào có thể đến cùng thao tác, kết hợp static review và kiểm tra runtime thực tế.

## 15. Quy trình review từng hành vi nhạy cảm

Để giảm nguy cơ đường gọi không đi qua cơ chế can thiệp, áp dụng quy trình sau:

**Bước 1 — Chọn effect nhạy cảm.** Xác định thao tác thực sự thay đổi hoặc làm lộ tài nguyên, chẳng hạn debitAccount, exportPrivateData hoặc deleteDocument.

**Bước 2 — Định nghĩa quyền cần kiểm tra.** Nêu action, resource, principal, tenant, trạng thái và nguồn tin cậy của dữ liệu quyền.

**Bước 3 — Vẽ đồ thị đường gọi.** Từ entry point tương tác, job, message và callback, lần theo mọi con đường chính tới effect. Ghi rõ đối tượng thực được dispatch.

**Bước 4 — Gắn vị trí join point/pointcut.** Đánh dấu advice được kỳ vọng chạy ở method-call hay method-execution, trên proxy hay code được weave.

**Bước 5 — Kiểm tra khe hở.** Self-invocation, target thô, không match pointcut, method không intercept được, classloader khác, asynchronous handoff, cache/fallback và đường vào mới.

**Bước 6 — Chọn ranh giới cưỡng chế tin cậy.** Bảo đảm mọi đường hợp lệ cuối cùng phải qua điểm quyết định quyền trước effect, dù điểm interception phụ trợ bị bỏ qua.

**Bước 7 — Kiểm tra thứ tự advice.** Cache, fallback, retry, logging và authorization có thể bỏ qua proceed() hoặc biến đổi outcome. Đừng để một advice ngoài cùng vô tình trả kết quả nhạy cảm trước guard.

**Bước 8 — Kiểm thử mọi kiểu đường.** Dùng principal có quyền và không có quyền; kiểm tra cả kết quả, log kiểm tra quyền và trạng thái effect sau cùng.

**Bước 9 — Xác nhận build/deploy.** Verify cấu hình proxy/weaving, classloader và điểm cắt đang hoạt động trên artifact triển khai, không chỉ trong một unit test.

**Bước 10 — Duy trì khi hệ thống thay đổi.** Mỗi entry point mới, đường gọi mới, thay đổi interface hoặc thay đổi chính sách cache/async đều cần kiểm tra lại độ bao phủ.

## 16. Ma trận quyết định: cơ chế nào đang bảo vệ?

| Tình huống | Nếu chỉ can thiệp tại proxy bên ngoài | Nếu weaving được cấu hình đúng tại method execution | Ranh giới tin cậy vẫn cần |
| --- | --- | --- | --- |
| Gọi method qua proxy | Handler/advice có cơ hội chạy | Advice ở method có thể chạy | Quyết định quyền chính xác |
| Gọi trực tiếp target gốc | Có thể bypass proxy | Có thể vẫn chạy nếu target đã được weave | Không expose effect trái quyền |
| Target gọi this.sensitive() | Thường không quay qua proxy | Có thể được instrument tại execution join point | Cưỡng chế trước effect |
| Method final/private | Phụ thuộc loại proxy; subclass override có giới hạn | Phụ thuộc công cụ và phạm vi weave | Không dựa vào giả định chung |
| Worker chạy callback sau return | Proxy ở hàm submit không tự bảo vệ callback | Chỉ bảo vệ nếu callback/effect cũng nằm trong tập được weave | Kiểm tra quyền tại execution khi cần |
| Reflection trên target | Có thể bỏ qua proxy | Phụ thuộc điểm weave được hỗ trợ | Kiểm tra effect độc lập đường gọi |
| Cache hit trước guard | Advice bên trong cache không chạy | Thứ tự policy vẫn có thể bỏ qua guard ở nhánh cache | Quyền truy cập dữ liệu cache |
| Class không nằm trong scope instrumentation | Proxy không tồn tại hoặc không được dùng | Advice không xuất hiện tại class ấy | Quyền ở service/resource |

Không nên đọc bảng thành lời khẳng định về mọi implementation. Nó nhấn mạnh rằng độ an toàn phụ thuộc vào cả **cơ chế interception**, **cấu hình** và **thiết kế policy**.

## 17. Bài tập tự kiểm tra

**Tình huống A.** Một phương thức sensitive() bị chặn khi caller dùng proxy. Nhưng cùng target có hàm batch() gọi this.sensitive(). Tại sao bài test gọi proxy.sensitive() chưa đủ?

**Trả lời:** bài test chỉ kiểm tra đường qua proxy. Lời gọi this.sensitive() có thể không quay qua proxy, nên cần test batch() và kiểm tra effect. Nếu dùng method-execution weaving, phải xác nhận thật sự có advice tại execution join point.

**Tình huống B.** Có một job theo lịch gọi trực tiếp một service nhạy cảm, trong khi kiểm tra quyền chỉ ở request handler. Điều gì cần làm?

**Trả lời:** xác định principal và quyền của job, đưa kiểm tra quyền tới ranh giới mà cả request handler và job đều phải đi qua, hoặc xây một đường kiểm soát tương đương có thể chứng minh; test job với quyền hợp lệ và không hợp lệ.

**Tình huống C.** Reflection gọi method trên chính object proxy. Có thể khẳng định chắc chắn bypass advice không?

**Trả lời:** không. Nếu receiver là proxy và method thuộc cơ chế proxy, lời gọi có thể vẫn qua interceptor. Phải kiểm tra receiver thật, dispatch và điểm weave.

**Tình huống D.** Cache bao ngoài Authorization; người dùng không có quyền nhận dữ liệu từ cache hit. Điều gì sai?

**Trả lời:** cache trả dữ liệu trước khi Authorization chạy. Cần bảo đảm guard bao phủ cả cache hit/miss, cache key và phân vùng dữ liệu đúng principal/resource, đồng thời kiểm tra ở ranh giới dữ liệu tin cậy.

**Tình huống E.** Advice ghi “đã kiểm tra quyền” khi submit một tác vụ; worker xử lý sau nhiều phút với quyền đã bị thu hồi. Có tự coi tác vụ đã được phép thực thi không?

**Trả lời:** không tự động. Hợp đồng phải định nghĩa quyền được cố định tại lúc submit hay phải đánh giá lại khi execute; nếu phải đánh giá tại execution, worker cần dùng danh tính đáng tin cậy và guard đúng thời điểm.

**Tình huống F.** Một test assert rằng lời gọi trái quyền ném exception, nhưng database đã bị cập nhật trước khi exception được ném. Có đạt mục tiêu không?

**Trả lời:** không. Quy tắc truy cập cần ngăn effect trái quyền; phải kiểm tra trạng thái sau lời gọi và điểm quyết định quyền **trước** side effect, không chỉ loại exception.

**Tình huống G.** Một final method không thể bị subclass proxy override. Có nên kết luận mọi AOP đều không can thiệp được vào final method?

**Trả lời:** không. Giới hạn này liên quan cơ chế subclass/override. Một số cơ chế weaving có khả năng khác; cần xác minh phương thức và mô hình join point thực tế.

**Tình huống H.** Unit test cho thấy aspect được đăng ký, nhưng một đường tạo new Target() vẫn chạy. Kết luận?

**Trả lời:** sự tồn tại của aspect không chứng minh mọi instance được bảo vệ. Cần kiểm soát cách tạo và phân phối tham chiếu, thêm test cho direct target và bảo vệ tại ranh giới effect.

## 18. Kết luận kỹ thuật

Nguy cơ đường gọi không qua cơ chế can thiệp xuất hiện khi **điểm AOP được bảo vệ** không nằm trên **mọi đường thực thi tới một hiệu ứng nhạy cảm**. Nguyên nhân có thể là self-invocation qua target, tham chiếu đối tượng thô, giới hạn loại method, sai pointcut, class không được weave, các entry point khác, callback bất đồng bộ, hoặc advice ngoài cùng bỏ qua nhánh authorization.

Để kiểm soát rủi ro, cần làm rõ **đường gọi nào kích hoạt join point**, **receiver nào được sử dụng**, **authorization được cưỡng chế tại ranh giới nào**, và **effect nào có thể xảy ra nếu advice không chạy**. AOP nên giúp áp dụng kiểm soát một cách nhất quán và quan sát được; còn tính an toàn phải được xác minh trên mọi đường hợp lệ tới tài nguyên bằng thiết kế quyền tin cậy, kiểm thử âm tính ở cấp effect và kiểm tra cấu hình thực thi thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-007">Ghi nhận từ chối truy cập mà không làm lộ dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi nhận từ chối truy cập mà không làm lộ dữ liệu bằng Aspect (AOP)

## 1. Mục tiêu và phạm vi

Một hệ thống có kiểm soát truy cập cần biết **ai đã cố thực hiện loại thao tác nào, quyết định cho phép hay từ chối, và vì sao phải điều tra**. Ghi nhận này giúp phát hiện truy cập trái quyền, hiểu lỗi cấu hình chính sách và kiểm toán. Nhưng chính việc ghi log khi từ chối có thể trở thành một kênh rò rỉ mới nếu advice in nguyên đối số, tài nguyên, thông báo lỗi, token, nội dung bản ghi hoặc toàn bộ đối tượng principal.

Với **Aspect-Oriented Programming (AOP)**, kiểm tra quyền và ghi nhận kết quả có thể đặt tại **join point** của một thao tác có ý nghĩa nghiệp vụ. Advice đứng trước lời gọi, phối hợp với bộ đánh giá chính sách, từ chối thực thi khi không đủ quyền, rồi tạo **sự kiện audit đã làm sạch**. Điều thiết yếu là audit phải hữu dụng để điều tra nhưng **không chứa dữ liệu mà người bị từ chối không được phép biết**.

Sau bài này, bạn có thể:

- Đặt **Policy Enforcement Point (PEP)** tại ranh giới thao tác và ghi nhận `DENY` nhất quán.
- Phân biệt **thông báo trả caller**, **sự kiện audit nội bộ**, **log kỹ thuật** và **metric tổng hợp**.
- Xác định dữ liệu được phép ghi theo allowlist; loại bỏ secret, payload, resource metadata nhạy cảm và lỗi có nội dung chưa được kiểm duyệt.
- Thiết kế schema sự kiện `action/decision/reason/time/correlation` có kiểm soát danh tính, mức chi tiết và quyền truy cập.
- Xử lý các trường hợp **không tồn tại tài nguyên, khác tenant, sai owner, sai quyền, lỗi policy**, mà không vô tình tiết lộ sự tồn tại hoặc thuộc tính của tài nguyên.
- Bảo vệ khỏi log injection, cardinality quá cao, quá tải audit sink, gọi trùng advice, lỗi async, race condition và các đường gọi bypass.
- Kiểm tra một ví dụ **Java 17 chạy được** với JDK dynamic proxy để minh họa interception kiểu AOP.

Bài tập trung vào **AOP cơ bản và nâng cao nói chung**. Những điểm khác nhau giữa interception bằng proxy và weaving được giải thích theo khả năng thực tế của từng kỹ thuật; không giả định có một bộ API đặc thù.

## 2. Từ chối truy cập không chỉ là một ngoại lệ

Hệ thống thường có các bước:

```text
Caller
   |
   v
Xác thực danh tính (trusted identity)
   |
   v
[ AOP join point của business operation ]
   |
   +--> Xác định action và resource theo hợp đồng
   +--> Policy Decision Point (PDP) đánh giá
   |       |
   |       +--> ALLOW -> proceed() -> business operation
   |       |
   |       +--> DENY  -> tạo audit event được kiểm soát
   |                     -> không gọi proceed()
   |                     -> trả lỗi an toàn cho caller
   |
   +--> Hoàn tất scope, trạng thái, telemetry
```

Các vai trò:

| Thành phần | Trách nhiệm |
|---|---|
| **Authentication** | Xác minh chủ thể; cung cấp principal đáng tin, tenant và quyền/delegation |
| **PDP** | Tính allow/deny dựa trên subject, action, resource, context và policy |
| **PEP** | Thực thi quyết định: chỉ gọi `proceed()` khi hợp lệ |
| **Audit producer** | Dựng sự kiện tối thiểu, có schema và phân loại mức nhạy cảm |
| **Audit sink** | Lưu, bảo vệ, phân quyền đọc, kiểm soát tính toàn vẹn và vòng đời bản ghi |
| **Client error mapper** | Trả thông điệp/trạng thái không tiết lộ thông tin bị từ chối |

Một `DENY` là **quyết định chính sách**. Nó khác `AUTHENTICATION_FAILED`, lỗi nghiệp vụ, lỗi hệ thống, lỗi policy store hoặc lời gọi đã được cho phép nhưng thực thi thất bại. Phân loại đúng sẽ giúp tránh tình trạng mọi lỗi đều trở thành “người dùng không đủ quyền”, hoặc dùng thông tin kỹ thuật nhạy cảm trong thông báo công khai.

**Nguyên tắc trung tâm:** log của một quyết định từ chối không phải bản chụp toàn bộ request hay object graph. Nó là một **sự kiện an toàn được dựng có chủ đích** từ dữ liệu đã được phê duyệt.

## 3. Mô hình đe dọa đối với nhật ký từ chối

Kẻ tấn công có thể không xem được đối tượng đích nhưng vẫn quan sát được response, thời gian phản hồi, độ dài lỗi, ID trả về, rate-limit behavior hoặc dữ liệu gián tiếp ở log được chia sẻ. Một người vận hành hoặc công cụ thu thập log có thể có quyền xem các trường mà caller không được xem. Vì vậy phải xác định hai nhóm rủi ro:

1. **Rò rỉ ra bên ngoài:** thông điệp trả caller tiết lộ “tài liệu tồn tại nhưng thuộc user X”, phân biệt resource ID thật/không tồn tại, hoặc phản hồi khác nhau theo tenant. Đây là kênh suy đoán tồn tại tài nguyên.
2. **Rò rỉ vào hệ thống quan sát:** advice ghi token, URL chứa query nhạy cảm, email, số tài khoản, đối tượng người dùng, request body, SQL parameter, exception message hoặc stack trace có chứa dữ liệu cá nhân.

Một số nguồn vô tình lộ dữ liệu:

- `logger.info("Denied " + args)`; `Arrays.deepToString(args)`; JSON serializer tự duyệt DTO, getter và collection.
- `Throwable.getMessage()`, `toString()` của exception hay in stack trace tùy ý. Thông báo có thể nhúng toàn bộ payload hoặc đường dẫn dữ liệu.
- `principal.toString()` hoặc `resource.toString()`; đối tượng có thể chứa token, claims hoặc field riêng tư.
- Tự động lấy mọi attribute làm span tag, metric label, tracing baggage hoặc MDC.
- Log URL đầy đủ: path và query có thể chứa token, mã xác minh hay ID nhạy cảm.
- Mã hóa Base64 “để an toàn” nhưng thực chất giữ nguyên dữ liệu có thể giải mã.
- Trả về `403 Forbidden: record 987 belongs to alice@...` hay `404` khác biệt theo trạng thái tài nguyên mà caller không nên biết.
- Dữ liệu bị từ chối được đẩy sang queue rồi serializer xử lý sau, tạo thêm bản sao tồn tại lâu.

**Không có một phép redact theo regex duy nhất đủ bảo vệ mọi trường hợp.** Nên sử dụng allowlist, policy phân loại và các giới hạn rõ ràng ở cả producer lẫn consumer.

## 4. Chọn dữ liệu cần ghi: tối thiểu nhưng có giá trị điều tra

Một sự kiện audit tốt có thể chứa:

| Trường | Ví dụ được duyệt | Lưu ý |
|---|---|---|
| `event_type` | `AUTHZ_DECISION` | Tập hằng số cố định |
| `action` | `DOCUMENT_READ`, `PAYMENT_APPROVE` | Mã action định nghĩa sẵn; không lấy từ input tự do |
| `decision` | `DENY` | Không nhầm với outcome nghiệp vụ |
| `reason_code` | `NOT_PERMITTED`, `POLICY_UNAVAILABLE` | Enum có ý nghĩa nhưng tránh tiết lộ dữ liệu |
| `occurred_at` | Timestamp do hệ thống tạo | Đồng bộ thời gian phù hợp |
| `request_correlation` | ID ngẫu nhiên do hệ thống cấp | Chỉ dùng ở kênh được phân quyền; không đưa ID tùy ý vào metric label |
| `actor_ref` | Định danh nội bộ được kiểm soát | Có thể là pseudonym được quản lý; phân quyền/retention riêng |
| `resource_type` | `DOCUMENT` | Danh mục cho phép; có thể không ghi ID |
| `resource_ref` | Reference hạn chế và có bảo vệ | Chỉ khi nhu cầu điều tra đòi hỏi, không mặc định xuất |
| `policy_version` | Mã phiên bản cấu hình | Hữu ích khi kiểm toán thay đổi quyết định |

Ngược lại, **không tự động xuất** mật khẩu, access/refresh token, session cookie, nội dung document, email/số điện thoại, request/response body, toàn URL, raw principal claims, stack trace hoặc thông điệp exception chưa được lọc.

Phân biệt rõ ba cấp sự kiện:

- **Bản ghi audit bảo vệ nghiêm ngặt:** phục vụ điều tra có thẩm quyền; có thể dùng reference/tokens được quản lý, thời gian lưu và quyền truy cập riêng.
- **Log vận hành thông thường:** nên dùng những trường ít nhạy cảm, số lượng giá trị thấp, không phải kho dữ liệu danh tính.
- **Metric:** chủ yếu đếm số `DENY` theo action hoặc reason chuẩn hóa. **Không dùng actor ID, resource ID, email hoặc idempotency key làm label có cardinality không giới hạn.**

Nếu nghiệp vụ thực sự cần liên kết sự kiện với actor/resource, hãy xác định chủ sở hữu dữ liệu, mục đích, quyền truy cập, thời hạn và cách tra cứu an toàn. Hash thường của email hoặc ID có miền nhỏ **không tự tạo ẩn danh**; nhiều giá trị có thể bị đoán bằng từ điển, còn hash ổn định hỗ trợ liên kết hoạt động xuyên thời gian. Nếu cần pseudonym, cân nhắc HMAC có quản lý khóa và kiểm soát phạm vi/rotation theo nhu cầu thực tế.

## 5. Chính sách thông báo: không làm lộ sự tồn tại của tài nguyên

Ví dụ người dùng ở `tenant-a` cố đọc `doc-7`. Ba tình huống có thể có:

1. `doc-7` thực sự không tồn tại.
2. `doc-7` tồn tại nhưng nằm ở `tenant-b`.
3. `doc-7` thuộc `tenant-a` nhưng principal không được chia sẻ.

Nếu caller không được quyền biết tình trạng của tài nguyên, phản hồi cho cả ba trường hợp có thể dùng **một thông báo trung tính**, chẳng hạn `Not available`; loại trạng thái giao thức (ví dụ lựa chọn giữa 403/404) phải dựa trên hợp đồng bảo mật của API.

Một bản ghi nội bộ có thể biết chi tiết hơn, nhưng ngay cả audit cũng không nên ghi trực tiếp email owner, tenant khác hoặc tên tài liệu. Nếu cần mã nguyên nhân chi tiết để phân tích, phải phân loại quyền truy cập và ranh giới lưu trữ của audit: không để lý do `CROSS_TENANT_OWNER_EMAIL=...` nằm trong log công khai.

**Không hứa che giấu chỉ bằng thông điệp.** Dù text giống nhau, endpoint còn có thể rò tồn tại qua thời gian truy vấn, kích thước response, header, redirect, số lần thử, số truy vấn cache hoặc hành vi rate limit. Cần đánh giá các kênh phụ này theo mô hình đe dọa và thiết kế truy vấn phù hợp.

Một số hệ thống bắt buộc phân biệt “chưa đăng nhập” và “đã đăng nhập nhưng thiếu quyền”; điều đó có thể hợp lệ khi loại phản hồi không tiết lộ tài nguyên cấm. Hãy **định nghĩa phân loại** và kiểm thử thay vì quyết định tùy ý ở từng advice.

## 6. Điểm chặn AOP: ghi đúng một lần khi policy từ chối

Một `around advice` có thể:

1. Xác định thao tác nghiệp vụ từ metadata đáng tin.
2. Xác định principal đã xác thực và resource cần kiểm tra.
3. Gọi PDP; nếu allow, gọi `proceed()` đúng theo hợp đồng.
4. Nếu deny, dựng **một sự kiện `AUTHZ_DECISION/DENY` đã làm sạch**, không gọi `proceed()` và phát lỗi an toàn.
5. Khi rời lời gọi, dọn context và duy trì tính đúng đắn của ứng dụng.

Mã giả:

```text
around(securedOperation):
    action = operationPolicyRegistry.lookup(joinPoint)
    if action is missing:
        denySafely(reason = POLICY_MISSING)  // fail closed

    actor = trustedIdentity.current()
    resource = trustedResourceReference(args)
    decision = policy.evaluate(actor, action, resource)

    if decision == DENY:
        audit.emit(safeEvent(
            action = action,
            outcome = DENY,
            reason = boundedReasonCode(decision)))
        throw publicSafeDenial()

    if decision == INDETERMINATE:
        audit.emit(safeEvent(action, DENY, POLICY_UNAVAILABLE))
        throw publicSafeDenial()

    return proceed()
```

Đây là **pseudocode** minh họa ngữ nghĩa, không phải chương trình Java độc lập. Chính sách đối với lỗi audit sink sẽ được bàn riêng ở phần dưới.

**Ranh giới quan trọng:** chỉ dùng exception type đặc thù hoặc một quyết định PDP có cấu trúc để nhận biết **lỗi bị từ chối quyền**. Không nên bắt mọi `RuntimeException` hay mọi lỗi nghiệp vụ rồi ghi `DENY`, vì điều đó khiến audit sai và che giấu lỗi hệ thống.

Nếu chính sách được kiểm tra thêm ở tầng database/transaction, lỗi deny có thể xuất hiện **sau khi `proceed()` bắt đầu**. Hệ thống cần quyết định ai là nguồn ghi audit chính để không đếm hai lần cùng một decision. Có thể tạo một `decision_id` nội bộ và cơ chế dedup theo logical operation khi cần độ chính xác mạnh; không tự tin rằng mỗi join point chỉ chạy một lần.

## 7. Khi policy lỗi: fail closed và phân biệt nguyên nhân

PDP đôi khi không có đủ dữ liệu: principal thiếu, tenant chưa xác định, resource metadata lỗi, policy store timeout, hoặc việc đọc ACL thất bại. Đây không phải tín hiệu ALLOW.

Các kết quả nên phân biệt:

| Kết quả policy | Hành động tại PEP | Sự kiện an toàn |
|---|---|---|
| `ALLOW` | Cho phép đi tiếp | Có thể ghi allow nếu có yêu cầu audit |
| `DENY` | Không gọi tác vụ | `DENY / NOT_PERMITTED` |
| `INDETERMINATE` / policy unavailable | Không gọi tác vụ theo fail-closed | `DENY / POLICY_UNAVAILABLE` hoặc outcome chuyên biệt theo schema |
| Không có rule/metadata bắt buộc | Không gọi tác vụ | `DENY / POLICY_MISSING` |
| Principal không được xác thực | Dừng tại ranh giới xác thực | `AUTHN_FAILURE` hoặc event tương ứng, không gán bừa lý do quyền |

**Fail closed** là nguyên tắc cho quyết định quyền: không thể chứng minh được allow thì không thực hiện side effect. Tuy nhiên, fail closed khi PDP lỗi **khác** với chuyện có bắt buộc audit sink phải ghi thành công trước khi trả lỗi hay không. Hai chính sách có hệ quả vận hành khác nhau.

Không đẩy toàn bộ exception hay trạng thái nội bộ ra caller. Với observability, có thể ghi loại lỗi được kiểm duyệt và mã nguồn gốc ổn định vào một kênh có quyền phù hợp, nhưng không lấy chuỗi lỗi raw.

## 8. Ví dụ Java 17: chặn lời gọi và ghi một DENY event an toàn

Chương trình sau dùng **JDK dynamic proxy** làm cơ chế interception kiểu AOP trên interface `Documents`. Chương trình thể hiện:

- `@Guarded(Action.DOCUMENT_READ)` gắn mã thao tác ổn định vào method.
- `Actor` đại diện danh tính được cấp **từ phạm vi thử nghiệm tin cậy**; trong ứng dụng thật, actor phải do thành phần xác thực tạo và chuyển qua ranh giới tin cậy.
- `requireAccess` từ chối khi document thiếu, khác tenant hoặc khác owner, nhưng chỉ ném một loại lỗi có thông điệp trung tính.
- Khi bị từ chối, handler **không gọi method đích**; audit chỉ ghi `action`, `outcome` và `reason` từ enum cố định, không ghi document ID, nội dung, actor ID hay exception message.
- `Store.read` kiểm tra lại quyền khi thực thi thật trong phương thức đồng bộ của `Store`, minh họa lớp bảo vệ sát tài nguyên nếu có đường truy cập khác hoặc trạng thái thay đổi giữa lúc check và lúc đọc.

Lưu thành **DenialAuditAspectDemo.java**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DenialAuditAspectDemo {
    enum Action { DOCUMENT_READ }
    enum Outcome { DENY }
    enum Reason { NOT_PERMITTED }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Guarded { Action value(); }

    record Actor(String id, String tenant) {}
    record Document(String owner, String tenant, String content) {}

    static final class AccessDenied extends RuntimeException {
        AccessDenied() { super("Not available"); }
    }

    static final class Store {
        private final Map<String, Document> documents = new HashMap<>();
        final AtomicInteger targetCalls = new AtomicInteger();

        synchronized void add(String id, Document document) {
            documents.put(id, document);
        }

        synchronized Document snapshot(String id) {
            return documents.get(id);
        }

        synchronized String read(Actor actor, String id) {
            targetCalls.incrementAndGet();
            Document document = documents.get(id);
            requireAccess(actor, document);
            return document.content();
        }
    }

    static void requireAccess(Actor actor, Document document) {
        if (actor == null || document == null
                || !document.tenant().equals(actor.tenant())
                || !document.owner().equals(actor.id())) {
            throw new AccessDenied();
        }
    }

    interface Documents {
        @Guarded(Action.DOCUMENT_READ)
        String read(String id);
    }

    static Documents guarded(Actor actor, Store store) {
        Documents target = id -> store.read(actor, id);
        return (Documents) Proxy.newProxyInstance(
                Documents.class.getClassLoader(),
                new Class<?>[] { Documents.class },
                (proxy, method, args) -> {
                    Guarded rule = method.getAnnotation(Guarded.class);
                    if (rule == null) {
                        throw new IllegalStateException("Access policy missing");
                    }
                    try {
                        requireAccess(actor, store.snapshot((String) args[0]));
                    } catch (AccessDenied denied) {
                        System.out.println("audit action=" + rule.value()
                                + " outcome=" + Outcome.DENY
                                + " reason=" + Reason.NOT_PERMITTED);
                        throw denied;
                    }
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
    }

    static void tryRead(Documents documents, String id) {
        try {
            System.out.println("read=" + documents.read(id));
        } catch (AccessDenied denied) {
            System.out.println("client=" + denied.getMessage());
        }
    }

    public static void main(String[] args) {
        Store store = new Store();
        store.add("doc-7", new Document("alice", "team-a", "report-ready"));

        Documents owner = guarded(new Actor("alice", "team-a"), store);
        Documents colleague = guarded(new Actor("bob", "team-a"), store);
        Documents outsider = guarded(new Actor("eve", "team-b"), store);

        tryRead(owner, "doc-7");
        tryRead(colleague, "doc-7");
        tryRead(outsider, "doc-7");
        tryRead(owner, "missing");
        System.out.println("target_calls=" + store.targetCalls.get());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 DenialAuditAspectDemo.java
java DenialAuditAspectDemo
```

Kết quả đã xác minh:

```text
read=report-ready
audit action=DOCUMENT_READ outcome=DENY reason=NOT_PERMITTED
client=Not available
audit action=DOCUMENT_READ outcome=DENY reason=NOT_PERMITTED
client=Not available
audit action=DOCUMENT_READ outcome=DENY reason=NOT_PERMITTED
client=Not available
target_calls=1
```

Thứ tự kịch bản:

1. Chủ tài liệu `alice` trong `team-a` đọc `doc-7` thành công; nội dung trả về chỉ in ở **luồng kết quả được phép** của chương trình demo.
2. `bob` ở cùng tenant nhưng không phải owner bị từ chối, audit ghi một dòng cố định.
3. `eve` ở tenant khác cũng bị từ chối, không có thông tin của tenant đích trong dòng audit.
4. `alice` yêu cầu tài liệu `missing` và vẫn nhận cùng thông điệp bên ngoài.
5. `target_calls=1` chứng minh ba yêu cầu bị từ chối ở proxy **không chạy vào method `Store.read`**; chỉ lần hợp lệ chạy vào.

**Giới hạn cố ý:** đây là ví dụ đơn luồng, lưu trong bộ nhớ và in ra console để dễ thấy. Nó **không** cung cấp tính bền vững, chống sửa audit, atomicity xuyên tiến trình, quy tắc retention, kênh phân quyền hay bảo đảm non-leakage của thời gian phản hồi. `synchronized` chỉ bảo vệ một instance `Store` trong một JVM. Proxy chỉ intercept lời gọi đi qua interface; annotation thiếu làm handler ném `IllegalStateException` (fail closed trong demo), nhưng ứng dụng production phải ánh xạ lỗi này sang phản hồi công khai an toàn và có chính sách audit cho sự cố cấu hình.

Phương thức `tryRead` nằm **phía caller**, vì vậy các dòng `client=...` là mô phỏng phản hồi công khai; không phải lời khuyên in nguyên nội dung truy cập được trong audit. Việc đọc thành công trả `report-ready` chứng minh kết quả nghiệp vụ vẫn đúng, trong khi sự kiện từ chối không lấy dữ liệu đó.

## 9. Cách xây dựng schema audit có kiểm soát

Có thể định nghĩa một contract typed thay vì truyền map tùy ý:

```java
// Ví dụ cấu trúc minh họa; không phải chương trình độc lập.
enum AuditDecision { ALLOW, DENY }
enum AuditReason { NOT_PERMITTED, POLICY_MISSING, POLICY_UNAVAILABLE }
enum BusinessAction { DOCUMENT_READ, DOCUMENT_APPROVE }

record AuthorizationAudit(
    BusinessAction action,
    AuditDecision decision,
    AuditReason reason,
    java.time.Instant occurredAt,
    String safeCorrelationReference
) {}
```

Trong contract thực tế, `safeCorrelationReference` phải do thành phần đáng tin tạo/chuẩn hóa, có giới hạn kích thước và chính sách truy cập. **Không** cho caller truyền nguyên chuỗi log field hoặc tên event tùy ý.

Khuyến nghị thiết kế:

- **Enum/constant cho action và reason:** tránh code dùng nguyên tên phương thức hay message động làm lý do.
- **Timestamp có nguồn tin cậy:** chụp thời điểm quyết định tại nơi thực thi; bổ sung sequence/event ID khi thứ tự giữa nhiều instance quan trọng.
- **Correlation có phạm vi:** có thể tra cứu một điều tra được phép, nhưng không được tùy tiện lộ ID người dùng hoặc khai thác nó làm tracking identifier không kiểm soát.
- **Phân loại trường:** có catalog trường được phép, owner, hạn lưu và mức độ truy cập; schema mới phải được review.
- **Version schema và policy:** khi format hoặc logic cho phép/từ chối thay đổi, người điều tra cần biết điều kiện tương ứng; policy version thường là thông tin ít nhạy cảm hơn toàn policy document.
- **Giới hạn kích thước:** event phải bounded; mã lý do và action cố định giúp tránh input không tin cậy tạo bản ghi vô hạn.

Giữa các môi trường, không nên bật tùy chọn `debugAllArguments=true` trong production để “giải quyết nhanh” vì nó vô hiệu hóa toàn bộ nguyên tắc minimization. Những cuộc điều tra cần dữ liệu nhạy cảm phải đi qua cơ chế kiểm soát riêng.

## 10. Sanitization, escape và giới hạn tài nguyên

Ngay cả một trường cho phép ghi vẫn có thể bị kiểm soát bởi dữ liệu bên ngoài, chẳng hạn correlation ID hoặc resource reference. Trước khi xuất:

1. **Validate** độ dài, cú pháp và nguồn tạo. Ưu tiên ID do ứng dụng sinh.
2. **Normalize** nếu hợp đồng yêu cầu; không normalize bừa làm đổi danh tính.
3. **Escape** theo định dạng đích, bao gồm CR, LF, TAB, dấu nháy, backslash và ký tự điều khiển.
4. **Limit** theo byte biểu diễn cuối; tính cả tên trường và dấu phân cách, tránh cắt giữa mã Unicode/escape.
5. **Serialize** bằng writer có cấu trúc và kiểm soát schema; không nối string thô.
6. **Enforce quota/rate** để actor độc hại không thể tạo hàng triệu event chiếm tài nguyên.

Ví dụ một correlation string chứa:

```text
request-123
ERROR admin login accepted
```

Nếu được in nguyên thành hai dòng, log parser có thể hiểu dòng thứ hai là sự kiện riêng (**log injection**). Một event JSON/structured event qua serializer đúng sẽ escape ký tự điều khiển và giữ một bản ghi logic. Tuy nhiên, serializer không biến secret thành dữ liệu an toàn: **lọc quyền xuất trường phải xảy ra trước serialize**.

Nếu phải ghi payload sau khi redact, hãy xây dựng bản sao tối thiểu từ các field đã cho phép. Không dùng `obj.toString()` hoặc deep serializer trên đối tượng lạ; getter có thể truy vấn dữ liệu, có side effect hoặc lỗi, và quá trình serialize có thể quét tài nguyên rất lớn. Truncation của token vẫn có thể lộ token, nên secret cần **omit/redact hoàn toàn**.

Một `StackTrace` thường dài, có thể chứa đường dẫn nội bộ, thông tin request trong message và nhiều suppressed exceptions. Đừng coi việc log mọi throwable ở mức `WARN` là an toàn. Nếu cần trace kỹ thuật để điều tra, hãy sử dụng kênh có phân quyền, giới hạn, lọc thông điệp và cấu hình retention riêng.

## 11. Phản hồi công khai, audit nội bộ và side channel

Không nên dùng trực tiếp `decision.reason()` làm thông điệp cho người dùng. Cần một bảng ánh xạ được review:

| Ngữ cảnh | Phản hồi có thể công khai | Audit phân loại |
|---|---|---|
| Chưa xác thực | Yêu cầu xác thực | `AUTHN_REQUIRED` |
| Đã xác thực nhưng thiếu quyền đối với tài nguyên ẩn | `Not available` theo hợp đồng | `NOT_PERMITTED` |
| Tài nguyên không tồn tại và không được phép biết sự tồn tại | Có thể cùng `Not available` | Có thể dùng mã nội bộ chung |
| Chính sách không sẵn sàng | Thông báo lỗi trung tính | `POLICY_UNAVAILABLE`, phân quyền riêng |
| Tài nguyên có công khai nhưng thao tác bị cấm | Có thể trả “Forbidden” nếu hợp đồng cho phép | `NOT_PERMITTED` |

Tính không tiết lộ còn phụ thuộc các tín hiệu khác: thời gian xử lý, HTTP status, kích thước response, số lượng kết quả, location header, cache behavior, tốc độ rate limiting và khả năng đo sự tồn tại qua retry. Không thể hứa bảo vệ mọi side channel chỉ bằng một chuỗi thông điệp giống nhau. Khi sự tồn tại của tài nguyên là bí mật, cần thiết kế từ query/data access đến network response và thử nghiệm timing side channel theo mô hình đe dọa.

**Không ghi nội dung bị từ chối trong audit dù chỉ "để tìm lỗi".** Việc denied xảy ra thường là lúc độ tin cậy của nguồn dữ liệu thấp nhất; attacker có thể cố ý dùng input độc hại để phá log.

## 12. Đảm bảo việc từ chối thật sự chặn thao tác

Lập trình viên đôi khi ghi audit rồi vẫn gọi `proceed()` hoặc log một ngoại lệ sau khi side effect đã chạy. Một lời từ chối đúng nghĩa phải:

- Chặn trước side effect được bảo vệ khi chính sách không cho phép.
- Chuyển lỗi ra caller theo hợp đồng nhất quán, không biến deny thành kết quả hợp lệ.
- Không làm lộ object/args trong exception.
- Không phụ thuộc việc format/log thành công để tự nhiên đổi từ `DENY` thành `ALLOW`.
- Có chính sách riêng cho resource thay đổi trạng thái trong lúc xử lý.

**TOCTOU (time-of-check/time-of-use):** advice đánh giá quyền ở `t1`, đến `t2` mới có ghi/đọc dữ liệu; resource hoặc quyền có thể thay đổi giữa hai thời điểm. Pre-check ở AOP là lớp kiểm tra sớm. Với invariant quan trọng, cần xác minh ở sát commit bằng transaction, optimistic locking, conditional update, tenant predicate hay guard ở storage. Tránh ghi audit “DENY vì không được duyệt” cho một giao dịch đã thực sự commit trước khi lỗi khác xảy ra: audit phải phản ánh đúng thời điểm và lớp quyết định.

Một phương thức bị deny ngay tại AOP không được gọi target; có thể kiểm tra bằng counter/mock có kiểm soát. Trường hợp deny ở lớp dưới sau một pre-check allow cần được mô tả là **một quyết định sau**, tránh tạo hai sự kiện không thể phân biệt.

## 13. Các đường đi có thể bỏ qua advice

Một bảo vệ dùng proxy không tự áp dụng khi caller giữ tham chiếu đến object gốc hoặc một phương thức trong cùng object gọi trực tiếp phương thức khác bằng `this`. Cơ chế weaving có phạm vi khác nhưng vẫn cần xác minh join point được hỗ trợ. Vì audit gắn với enforcement, **bypass PEP có thể đồng thời bypass cả quyết định quyền và bản ghi DENY**.

Phải kiểm tra:

- HTTP endpoint, CLI, batch, message handler, callback, scheduler và đường quản trị.
- Self-invocation, raw implementation instance, reflection, overload, interface-vs-implementation annotation.
- Phương thức không có policy metadata và các join point không thuộc pointcut.
- Gọi trực tiếp storage hoặc API ngoài từ code không đi qua operation boundary.
- Library/worker dùng service identity hoặc delegated identity khác principal request.

Các lớp bảo vệ:

- Công khai API có ngữ nghĩa business operation thay vì cho phép gọi raw storage rộng khắp.
- Ràng buộc mọi command nhạy cảm với policy; **thiếu metadata ⇒ fail closed** tại boundary được quản lý.
- Tái kiểm tra quyền/tenant/bất biến sát dữ liệu, ngay cả khi AOP đã kiểm tra.
- Ghi audit tại enforcement layer phù hợp và dùng correlation/dedup để hiểu nhiều lớp quyết định.
- Test interception coverage thực tế bằng entrypoint, không chỉ unit test hàm PDP.

Không nên dựa trên một lời gọi tên phương thức hay annotation để chứng minh rằng mọi đường truy cập đã được bảo vệ.

## 14. Ghi audit dưới tải cao, lỗi sink và độ bền

Một sự kiện DENY có thể là **best-effort telemetry**, hoặc **audit bắt buộc** theo hợp đồng hệ thống. Cần quy định trước:

**Trường hợp best-effort:** khi collector không sẵn sàng, ứng dụng vẫn giữ quyết định `DENY` và trả lỗi an toàn cho caller; event có thể bị mất, nên hệ thống phải đo drop count và báo động. Nếu tác vụ *được phép* phụ thuộc tính sẵn sàng của audit cho quy định nghiệp vụ, không được tự áp dụng chính sách best-effort này.

**Trường hợp audit bắt buộc:** phải có một ranh giới lưu bền vững phù hợp để đáp ứng cam kết kiểm toán, ví dụ append-only store có bảo vệ, transaction/outbox nếu audit và effect thuộc cùng domain transaction, hoặc broker được cấu hình durability. Nếu ghi thất bại, quyết định tiếp tục hay dừng thao tác được xác định bởi chính sách bắt buộc, **không** để exception từ logger tự quyết định ngẫu nhiên.

Đối với riêng `DENY`, không được biến từ chối thành cho phép chỉ vì sink gặp lỗi. Nhưng cũng không nên bị treo vô thời hạn nếu hệ thống log đang đầy. Thiết kế giới hạn timeout, bounded queue, backpressure, sampling có điều kiện và đường cảnh báo. Một số audit bắt buộc **không thể sampling**; thay vì âm thầm bỏ, phải dùng cơ chế lưu phù hợp và quy trình sự cố.

Bảo vệ audit store:

- Giới hạn quyền đọc/ghi, tách quyền quản trị và truy vấn.
- Mã hóa khi lưu/truyền và bảo vệ khóa.
- Phòng sửa/xóa trái phép theo mức bảo đảm thực tế: append-only/WORM, integrity chain hoặc signed batches khi cần.
- Đồng bộ thời gian, event ID và policy version để phục vụ điều tra.
- Chính sách retention/xóa phù hợp với trách nhiệm dữ liệu; audit không được giữ mọi ID vô thời hạn chỉ vì “để dự phòng”.
- Bảo vệ bản sao ở export, alert, dashboard, index, backup và môi trường test.
- Theo dõi lỗi ghi audit, dropped/duplicated events và độ trễ xử lý mà không đưa raw principal/resource ID vào metric label.

## 15. Ghi một lần trong kiến trúc retry và bất đồng bộ

Đơn vị **logical operation** có thể qua nhiều attempt. Ví dụ HTTP client retry, message consumer redelivery, callback hoặc worker xử lý chậm. Một caller có thể bị deny nhiều lần thật; nhưng cùng một logical request bị quan sát ở nhiều interceptor cũng có thể tạo audit trùng.

Cần làm rõ các ngữ nghĩa:

- **Authorization attempt**: mỗi lần PDP/PEP quyết định.
- **Logical request**: một ý định nghiệp vụ gốc, có correlation được tạo đáng tin.
- **Business effect**: thay đổi dữ liệu thực sự xảy ra.
- **Audit event**: bản ghi một quyết định cụ thể, có event ID riêng.

Không nhầm “một event mỗi attempt” với “một event duy nhất mọi hoàn cảnh”. Có thể ghi mỗi attempt để phát hiện tấn công lặp; khi tổng hợp cần tránh tính mỗi lớp interceptor thành một request khác. Nếu yêu cầu *exactly-once audit* trong một miền lỗi cụ thể, cần thiết kế durable event identity và dedup ở đúng nơi, không thể chỉ dựa vào advice chạy một lần.

**Asynchronous context:** truyền principal/correlation qua executor hoặc message không được mặc định lấy từ `ThreadLocal` còn sót của request trước. Gắn và restore context quanh task/callback; xác định ai có quyền khi thực thi muộn (người khởi tạo, delegated principal, hay service identity). Nếu permission bị thu hồi trong thời gian queue chờ, chính sách có thể yêu cầu đánh giá lại tại nơi thực thi.

**Không giữ live reference đến args** trong event queue. Chụp các trường đã lọc và giới hạn vào một giá trị bất biến tại điểm quyết định, rồi mới enqueue. Đây là cách tránh vừa giữ object graph lớn, vừa phát sinh log dữ liệu thay đổi sau thời điểm policy.

## 16. Trường hợp cảnh báo và xử lý an ninh

Không phải DENY nào cũng là tấn công. Người dùng có thể thử URL cũ, có quyền bị thu hồi, làm sai thao tác hoặc gõ nhầm ID. Ngược lại, các chuỗi DENY lặp trên nhiều resource/tenant có thể báo hiệu enumeration.

Đối với cảnh báo, ưu tiên các mẫu có ngữ nghĩa:

- Tỷ lệ DENY tăng mạnh cho một action.
- DENY nhiều lần sau khi quyền vừa thay đổi.
- Nhiều request không có principal đáng tin, thiếu policy metadata hoặc policy service unavailable.
- Kết hợp DENY với rate-limit hits, dấu hiệu credential abuse hoặc activity bất thường.

Khi cần nhóm theo actor hoặc địa chỉ nguồn, dùng pipeline an ninh có phân quyền và retention rõ ràng. Không phát từng actor ID/IP vào metric label toàn hệ thống; nên dùng thống kê giới hạn cardinality và tham chiếu điều tra qua hệ thống riêng.

Một policy deny **không có nghĩa đã phát hiện xâm nhập**. Đặt nhãn “suspicious” phải dựa trên tiêu chí và dữ liệu được phép quan sát, tránh suy luận từ duy nhất một lỗi 403/404.

## 17. Kiểm thử để chứng minh không lộ dữ liệu

Các test hữu ích nhất kiểm tra **đầu ra thực tế của cả client và audit sink**, không chỉ xác minh `Policy.evaluate()` trả `DENY`:

1. **Sai owner:** caller cùng tenant nhưng không sở hữu tài nguyên; target không được gọi, response trung tính, audit không có chủ sở hữu/tài liệu.
2. **Khác tenant:** thử tài liệu của tenant khác; không có target invocation, không leak tenant hay resource ID.
3. **Resource không tồn tại:** response và audit theo cùng chính sách không tiết lộ tồn tại; xem cả status/header/độ dài phản hồi.
4. **Principal không xác thực:** phân loại đúng authentication vs authorization, không ngầm biến null principal thành allow.
5. **Policy unavailable/missing:** fail closed; lý do kỹ thuật chỉ trong kênh audit được phân quyền, caller không nhận stack trace.
6. **Canary bí mật:** truyền token/email/chữ ký giả có dấu nhận diện qua args, principal, resource, exception; assert chuỗi đó không xuất hiện ở log, trace, metric, audit và response bị từ chối.
7. **Log injection:** truyền CR/LF, ký tự điều khiển, dấu quote và separator vào những trường được phép xuất; event vẫn là một bản ghi hợp lệ sau serialization.
8. **Giới hạn kích thước:** dữ liệu rất dài, collection lớn, DTO có `toString()` độc hại; formatter không duyệt/chuyển đổi dữ liệu bị cấm và không vượt quota.
9. **Đường gọi bypass:** raw object, self-invocation, reflection, overload, async, queue và batch. Bảo đảm deny vẫn chặn tại enforcement layer.
10. **Race/TOCTOU:** quyền hoặc trạng thái tài nguyên thay đổi giữa pre-check và commit; side effect không vi phạm policy.
11. **Lỗi audit sink:** sink throw, timeout, queue đầy; quyết định từ chối vẫn giữ nguyên, ứng dụng tuân theo chế độ best-effort hoặc mandatory đã khai báo.
12. **Retry/multiple interceptors:** mỗi quyết định có event identity/correlation đúng; không nhân đôi nghiệp vụ hoặc đếm sai metric.
13. **Quyền truy cập kho audit:** người chỉ được xem log vận hành không xem được reference nhạy cảm ở audit bảo vệ riêng.
14. **Clock/sampling/retention:** sự kiện audit bắt buộc không biến mất vì sampling; thời gian lưu, xóa và export hoạt động theo chính sách.

Kiểm thử unit cho renderer và PDP cần đi cùng integration test đúng entrypoint. Nếu chỉ test một hàm tạo chuỗi log, bạn chưa chứng minh đường gọi thực tế luôn đi qua advice hoặc framework ghi log không bổ sung thông tin nhạy cảm.

## 18. Lỗi thường gặp và cách khắc phục

| Biểu hiện | Nguyên nhân | Cách xử lý |
|---|---|---|
| DENY log có email/token | Ghi nguyên principal, claims hoặc args | Allowlist field, redact/omit trước serialize |
| Caller biết tài liệu tồn tại | Thông điệp lỗi khác nhau theo owner/tenant/existence | Response trung tính và đánh giá side channel |
| Ghi thiếu sự kiện DENY | Raw implementation/self-invocation/worker bypass | Audit join-point coverage, guard sát dữ liệu |
| Mọi exception đều báo `ACCESS_DENIED` | Catch-all không phân biệt lỗi policy và lỗi nghiệp vụ | Enum quyết định/lỗi rõ ràng, preserve cause ở nơi phù hợp |
| DENY vẫn gọi method đích | Advice log rồi proceed | Nhánh DENY phải chặn, test invocation count |
| Log bị tách thành sự kiện giả | Chuỗi tùy ý chứa CR/LF/quote | Structured writer, escape theo format và bounded fields |
| Log nổ bộ nhớ | Serialize object lớn rồi cắt | Chỉ dựng schema an toàn, chặn trước serializer |
| Sink lỗi khiến thao tác bất ngờ được phép | Catch lỗi log và chạy đường mặc định | Giữ quyết định quyền độc lập với ghi telemetry |
| Sink lỗi làm hệ thống treo | Unbounded blocking/log queue | Timeout, bounded queue và chính sách degrade rõ |
| Event DENY bị trùng | Cùng logical request qua nhiều advice/retry | Event ID, correlation và aggregation semantics |
| Log chứa resource ID dù deny | “Để debug” tự ghi raw ID | Chỉ dùng reference an toàn trong kênh điều tra có quyền |
| Metric tăng cardinality | Dùng user/resource ID làm nhãn | Nhãn action/reason cố định, điều tra chi tiết ở kho khác |

## 19. Quy trình áp dụng

**Bước 1 — Liệt kê thao tác bảo vệ.** Chọn business operation, pointcut và chính sách cụ thể; không dựa chỉ vào getter, setter hay URL. Mỗi action có hợp đồng về dữ liệu kiểm tra và phản hồi khi deny.

**Bước 2 — Xây dựng PDP và PEP có phân loại.** Quyết định có dạng typed `ALLOW/DENY/INDETERMINATE`; khi không đủ bằng chứng allow thì chặn. Bảo đảm thiếu policy metadata bị phát hiện, không được mặc nhiên public.

**Bước 3 — Định nghĩa event contract.** Chỉ khai báo các trường và mã lý do đã duyệt, có giới hạn byte và ngữ nghĩa correlation. Tách protected audit khỏi log thường và metric.

**Bước 4 — Phát DENY đúng điểm.** Trước side effect, không proceed nếu bị từ chối, không ghi raw exception/args. Ở tầng khác có guard riêng cần xác định event owner để tránh trùng không kiểm soát.

**Bước 5 — Bảo vệ kho audit.** Xác định mức bền vững, quyền đọc, retention, integrity, cơ chế quá tải và lỗi sink. Với audit bắt buộc, chọn giao dịch/append-only mechanism phù hợp.

**Bước 6 — Kiểm thử theo mô hình đe dọa.** Thử sai tenant, sai owner, resource thiếu, policy unavailable, canary secret, bypass AOP, async/retry, log injection và sink failure. Kiểm tra cả client response, audit store và metric thực.

### Checklist rà soát cuối

- [ ] Không cho phép thao tác khi PDP trả `DENY` hoặc không thể quyết định allow.
- [ ] Mọi ranh giới thao tác nhạy cảm được enforce; có kiểm tra coverage.
- [ ] Nhánh bị từ chối **không gọi proceed/target**, không tạo side effect.
- [ ] Log DENY chỉ gồm trường allowlist; không có raw args, token, object, exception hay private resource data.
- [ ] Mã action/reason, kích thước event và nguồn correlation được kiểm soát.
- [ ] Caller không nhận thông tin về resource/tenant/owner bị cấm.
- [ ] Có kế hoạch giảm rò qua status, timing, cache và response length.
- [ ] Kênh audit có phân quyền, retention, integrity và chính sách khi sink lỗi.
- [ ] Dị bộ/retry không dùng sai principal, không nhân bản dữ liệu nhạy cảm hoặc làm sai số đếm.
- [ ] Test canary và integration chứng minh không leak ở response, log, trace, metric và audit.
- [ ] Kiểm tra quyền sát side effect đối với trạng thái có thể đổi giữa check và use.

## 20. Ghi nhớ

**Một quyết định từ chối có ích cho điều tra chỉ khi nó được ghi bằng dữ liệu tối thiểu, đáng tin và đúng ngữ nghĩa.** AOP giúp đặt PEP và audit ở cùng ranh giới nghiệp vụ, nhưng không tự bảo đảm rằng mọi lời gọi đều đi qua advice, rằng log không lộ secret, hoặc rằng audit store có độ bền cần thiết. Cần kết hợp **fail-closed enforcement, sự kiện allowlist, phản hồi công khai trung tính, bảo vệ sink và kiểm thử mọi đường đi** để ghi nhận `DENY` mà không biến hệ thống quan sát thành một kênh tiết lộ dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-008">Ngăn aspect bảo mật bị vô hiệu hóa ngoài ý muốn</a>

<details>
<summary>Xem chi tiết</summary>

# Ngăn aspect bảo mật bị vô hiệu hóa ngoài ý muốn trong AOP

## 1. Vì sao đây là một yêu cầu bảo mật cốt lõi?

Lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP) cho phép đặt chính sách bảo mật tại những điểm cắt (*join point*) mà không cần lặp toàn bộ logic kiểm tra quyền trong từng phương thức nghiệp vụ. Một advice có thể chặn lời gọi, kiểm tra danh tính, quyền hạn, đối tượng dữ liệu hoặc trạng thái phiên, rồi chỉ cho phương thức đích thực thi khi được phép.

Nhưng một chính sách bảo mật chỉ có ý nghĩa nếu **mọi đường thực thi cần bảo vệ đều bắt buộc đi qua điểm kiểm tra có hiệu lực**. Nếu phương thức có thể bị gọi bằng một đường khác không qua advice, cấu hình pointcut vô tình bỏ sót, thứ tự aspect khiến cache trả sớm, hoặc lỗi trong cơ chế áp advice làm phép kiểm tra bị bỏ qua, hệ thống có thể hoạt động như thể không có bảo vệ.

Có hai khái niệm cần phân biệt:

- **Aspect được viết đúng:** đoạn mã kiểm tra quyền có thể từ chối các lời gọi không hợp lệ khi chính nó được chạy.
- **Chính sách được thực thi đầy đủ:** tất cả các cách đi tới thao tác nhạy cảm đều buộc phải qua một điểm kiểm tra đủ thẩm quyền, đồng thời không có đường vòng cho caller thiếu quyền.

Chỉ chứng minh điều thứ nhất là chưa đủ. Mục tiêu của bài là thiết kế ranh giới bảo mật theo nguyên tắc **không cho phép đường đi không được bảo vệ** (*fail closed*), phân tích các trường hợp AOP bị vô hiệu hóa ngoài ý muốn và kiểm chứng rằng lời gọi nhạy cảm không thể chạy khi thiếu authorization.

## 2. Bản chất của một security aspect

Trong AOP tổng quát:

| Thành phần | Vai trò trong bảo mật | Rủi ro nếu dùng sai |
| --- | --- | --- |
| **Join point** | Nơi lời gọi/thực thi có thể được kiểm soát | Có đường truy cập không phát sinh join point được instrument |
| **Pointcut** | Chọn những vị trí cần áp advice | Điểm cắt quá hẹp, sai tên hoặc không khớp overload |
| **Before advice** | Kiểm tra điều kiện trước thực thi | Không nhất thiết kiểm soát kết quả/đường tiếp tục |
| **Around advice** | Có thể chặn `proceed()`, từ chối hoặc cho chạy target | Nhánh sớm gọi `proceed()` trước khi kiểm tra |
| **Proxy/decorator** | Bắt những lời gọi đi qua wrapper | Caller giữ tham chiếu target gốc rồi gọi trực tiếp |
| **Weaving/instrumentation** | Chèn advice tại join point do công cụ hỗ trợ | Mất cấu hình weaving, class không được instrument |
| **Security policy** | Quyết định ai được làm gì trên tài nguyên nào | Kiểm tra quyền không đầy đủ hoặc phụ thuộc metadata sai |
| **Trusted context** | Mang principal, tenant, scope và thuộc tính request | Caller giả mạo hoặc context bị lẫn giữa các luồng |

Security advice có thể được diễn tả bằng luồng:

~~~text
Caller
   |
   v
[Trusted entry point]
   |
   v
[Security advice / policy enforcement point]
   |
   +--> Không xác thực / thiếu quyền / lỗi chính sách
   |       |
   |       +----> DENY; target không được chạy
   |
   +--> Được phép theo policy hiện hành
           |
           +----> proceed() ---> Target / resource
~~~

**Không cho qua vì thiếu thông tin** khác với “cứ thử chạy target để xem sao”. Với một thao tác nhạy cảm, nếu không thể xác định principal, không đọc được policy bắt buộc hoặc không chứng minh được quyền, phương án mặc định phải từ chối hoặc dùng cơ chế dự phòng đã được thiết kế rõ, không bỏ qua kiểm tra.

Một security aspect chỉ là một **điểm thực thi chính sách**. Nó không tự bảo vệ những entry point, ngôn ngữ gọi native, direct database access hoặc đường thực thi khác mà công cụ AOP không chặn được.

## 3. Các kiểu “vô hiệu hóa” không chủ ý

Một aspect bảo mật có thể tồn tại trong source code nhưng không chạy ở một số đường đi.

### 3.1. Gọi thẳng đối tượng đích, bỏ qua proxy

Nếu caller được trao cả `secureProxy` lẫn `rawTarget`, họ có thể gọi `rawTarget.delete(...)` mà không qua invocation handler. Proxy chỉ bảo vệ những lời gọi đi qua nó.

~~~text
Caller ---> SecureProxy ---> Authorization ---> Target
   |
   +-------------------- trực tiếp ------------> Target
                           (BYPASS)
~~~

Cách phòng tránh: ràng buộc kiến trúc để target nhạy cảm chỉ được tạo và giữ trong phạm vi tin cậy; xuất ra interface đã bảo vệ. Không coi wrapper là ranh giới bảo mật nếu code thiếu tin cậy vẫn có tham chiếu trực tiếp hoặc có quyền gọi resource dưới nó.

### 3.2. *Self-invocation* trong lớp đích

Một phương thức đã được gọi qua proxy có thể gọi phương thức khác của chính đối tượng bằng `this.otherMethod()`. Nếu cơ chế AOP chỉ intercept qua proxy, lời gọi nội bộ không qua proxy đó.

~~~java
class DocumentService {
    void listDocuments() {
        deleteDocument("doc-7"); // gọi trực tiếp trên this
    }

    void deleteDocument(String id) {
        // Thao tác cần quyền xóa
    }
}
~~~

Đây là đoạn minh họa, chưa phải chương trình độc lập. Nếu chỉ phương thức `deleteDocument` được áp advice qua proxy, gọi nó từ `listDocuments` theo đường nội bộ có thể bỏ kiểm tra quyền xóa. Nếu weaving tại execution join point được áp đúng, kết quả có thể khác; phải dựa trên hành vi thật của nền tảng.

Giải pháp thiết kế không nên dựa vào việc “tự gọi lại proxy bằng mẹo”. Với thao tác nhạy cảm, có thể tách ra một dịch vụ chỉ có entry point đã bảo vệ, hoặc đặt kiểm tra tại lớp nghiệp vụ/resource không thể bị đường gọi nội bộ bỏ qua.

### 3.3. Pointcut không bao trùm đầy đủ

Ví dụ pointcut chỉ chọn các phương thức mang metadata `@RequiresRole`. Một method nhạy cảm mới được thêm nhưng người viết quên gắn marker; method đó không được advice chọn. Tương tự, đổi tên package, thêm overload, thay visibility hoặc refactor interface có thể thay tập join point mà không làm code advice bị lỗi biên dịch.

Biện pháp:

- Định nghĩa rõ **tập thao tác nhạy cảm cần bảo vệ** từ mô hình quyền nghiệp vụ.
- Dùng **allowlist**: method nhạy cảm không có policy phải bị từ chối hoặc làm ứng dụng không khởi động, thay vì tự do chạy.
- Có kiểm tra cấu hình/coverage lúc bootstrap hoặc kiểm thử; không chỉ test method đã được gắn annotation.
- Kiểm tra overload, inherited method, bridge method, generic type erasure, thay đổi package/signature và pointcut precedence.
- Không xem “advice được phát hiện trong metadata” là bằng chứng đủ; phải chứng minh advice chạy trên **lời gọi thật**.

### 3.4. Nền tảng không thể intercept join point đó

Các kỹ thuật AOP khác nhau có giới hạn:

| Kỹ thuật | Phạm vi thông dụng | Nguy cơ |
| --- | --- | --- |
| Decorator thủ công | Lời gọi qua wrapper | Có thể gọi thẳng object gốc |
| Dynamic proxy theo interface | Method gọi qua interface proxy | Method ngoài interface hoặc gọi thẳng target không được chặn |
| Subclass proxy | Method có thể override và đi qua proxy | `final`, `private`, constructor, static hoặc visibility có thể cản intercept |
| Compile/load-time weaving | Các join point được weaver hỗ trợ và đã cấu hình | Build/load thiếu weaving, thứ tự class loading hoặc classloader khác |
| Runtime instrumentation | Các điểm có hook/bytecode transformation hợp lệ | Module access, agent không nạp, lớp không được biến đổi, lỗi retransform |

Các giới hạn thực tế phụ thuộc bộ máy AOP và ngôn ngữ, không có luật “cứ có aspect là chặn mọi method”. Reflection gọi vào **đối tượng gốc** vẫn có thể đi vòng qua proxy. Việc reflection được kiểm soát ở runtime cũng không khiến proxy tự xuất hiện trên đường gọi đó.

## 4. Bảo mật là một bất biến trên đồ thị lời gọi

Thay vì hỏi “advice có được viết chưa?”, hãy xét đường đi từ các *entry point* tới hành động nhạy cảm:

~~~text
HTTP/CLI/Message/Job/Plugin entry
             |
             v
     Identity/context validated
             |
             v
      Authorization check
             |
             v
       Protected operation
             |
             v
       Data/resource write
~~~

Một yêu cầu đúng có dạng: **với mọi đường đi có thể tới protected operation, policy check hợp lệ phải đã được thực hiện cho đúng principal, action và resource trước khi side effect xảy ra**, trừ đường phục vụ nội bộ được ủy quyền tường minh và ràng buộc bằng quyền hạn riêng.

Có thể coi điều này như *complete mediation*: mỗi lần truy cập tài nguyên cần được kiểm soát tại một điểm có hiệu lực. Chỉ chèn một aspect ở một số service method chưa chứng minh được bất biến nếu còn API khác, job nền, callback, công cụ bảo trì hoặc direct resource access.

Ví dụ những đường thường bị bỏ sót:

- Endpoint mới hoặc command-line command đi thẳng tới repository/resource.
- Scheduled job chạy bằng identity hệ thống nhưng không có phân quyền thích hợp.
- Message consumer gọi implementation trực tiếp.
- Unit test/bootstrap tạo object bằng `new` và không dùng proxy như production.
- Callback sau khi lời gọi ban đầu đã kết thúc nhưng vẫn truy cập resource.
- Batch processor thực hiện thao tác tương tự qua API khác.
- Plugin/module được nạp động giữ tham chiếu tới raw service.

Đối với thao tác nhạy cảm, kiểm tra tại **nơi dữ liệu/tài nguyên được chạm thật** hoặc một boundary đáng tin cậy mà mọi đường đều đi qua sẽ chắc chắn hơn so với chỉ dựa vào hình dạng phương thức.

## 5. Thiết kế *fail closed* và policy allowlist

Một thiết kế nguy hiểm:

~~~text
if policyExistsFor(method):
    checkPolicy(method)
return proceed()
~~~

Khi ai đó thêm method mới và quên policy, lời gọi được cho phép mặc định. Với API nhạy cảm, hãy thay bằng mô hình:

~~~text
policy = resolvePolicy(method, resource, action)

if policy missing:
    deny("Unmapped protected operation")

identity = readTrustedIdentity()
if identity missing:
    deny("Unauthenticated")

decision = authorize(identity, action, resource, policy)
if decision != ALLOW:
    deny("Forbidden")

return proceed()
~~~

Đây là pseudocode, không phải API Java độc lập. Ba nguyên tắc:

1. **Default deny:** method mới hoặc metadata không khớp không mặc nhiên có quyền.
2. **Không thực thi rồi mới kiểm tra:** authorization phải hoàn tất **trước side effect**. Advice dạng after returning không phù hợp để ngăn hành động trái phép.
3. **Không biến lỗi kiểm tra thành allow:** thiếu principal, exception khi resolve policy, timeout policy source hoặc không thể chứng minh quyền không nên rơi vào nhánh `proceed()`.

Nếu tài nguyên là dữ liệu của một tenant hoặc object cụ thể, role chung có thể chưa đủ. Policy phải nhận diện **action** (read/update/delete), **resource ID**, **owner/tenant**, phạm vi dữ liệu và thuộc tính request theo contract.

Với authorization dựa trên thuộc tính (*attribute-based access control*), nên đánh giá trên ngữ cảnh đáng tin từ cơ chế xác thực; đừng nhận `isAdmin=true` từ một tham số mà caller không tin cậy tự gửi rồi coi nó là quyết định quyền cuối cùng.

## 6. Ví dụ Java 17 chạy độc lập: proxy mặc định từ chối

Ví dụ dùng `java.lang.reflect.Proxy` để thực hiện security advice trên một interface. Với phương thức không có policy trong bảng lựa chọn, handler ném `SecurityException`. Method `erase` đòi role ADMIN; `view` đòi READER. Đây là mô hình giáo dục: principal được cung cấp bằng `AtomicReference` để mô phỏng thay đổi người gọi trong chương trình một luồng, **không phải** cơ chế xác thực an toàn cho ứng dụng production.

Tạo tệp **`SecureAspectBoundaryDemo.java`**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class SecureAspectBoundaryDemo {
    enum Role { READER, ADMIN }

    record Caller(String name, Set<Role> roles) {
        Caller {
            roles = Set.copyOf(roles);
        }
    }

    interface DocumentApi {
        String view(String id);
        String erase(String id);
    }

    private static final class DocumentStore implements DocumentApi {
        @Override
        public String view(String id) {
            System.out.println("[target] view " + id);
            return "document:" + id;
        }

        @Override
        public String erase(String id) {
            System.out.println("[target] erase " + id);
            return "erased:" + id;
        }
    }

    static DocumentApi secure(Supplier<Caller> currentCaller) {
        DocumentApi target = new DocumentStore();

        return (DocumentApi) Proxy.newProxyInstance(
                DocumentApi.class.getClassLoader(),
                new Class<?>[] { DocumentApi.class },
                (proxy, method, args) -> {
                    Role required = switch (method.getName()) {
                        case "view" -> Role.READER;
                        case "erase" -> Role.ADMIN;
                        default -> throw new SecurityException(
                                "Unmapped protected operation");
                    };

                    Caller caller = currentCaller.get();
                    if (caller == null || !caller.roles().contains(required)) {
                        System.out.println("[guard] deny " + method.getName());
                        throw new SecurityException("Access denied");
                    }

                    System.out.println("[guard] allow " + method.getName());
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
    }

    public static void main(String[] args) {
        AtomicReference<Caller> identity =
                new AtomicReference<>(new Caller(
                        "alice", Set.of(Role.READER)));

        DocumentApi api = secure(identity::get);
        System.out.println("view=" + api.view("doc-7"));

        try {
            api.erase("doc-7");
        } catch (SecurityException error) {
            System.out.println("blocked=" + error.getMessage());
        }

        identity.set(new Caller(
                "root", Set.of(Role.READER, Role.ADMIN)));
        System.out.println("erase=" + api.erase("doc-7"));
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac SecureAspectBoundaryDemo.java
java SecureAspectBoundaryDemo
~~~

Đầu ra:

~~~text
[guard] allow view
[target] view doc-7
view=document:doc-7
[guard] deny erase
blocked=Access denied
[guard] allow erase
[target] erase doc-7
erase=erased:doc-7
~~~

**Điều ví dụ chứng minh:**

- Khi principal chỉ có READER, `view` được proceed tới `DocumentStore`, còn `erase` bị từ chối **trước khi target chạy**. Không xuất hiện dòng `[target] erase` sau lượt bị từ chối.
- Khi principal có ADMIN, `erase` được phép chạy và target mới in `[target] erase`.
- Nếu method được thêm vào interface nhưng quên thêm quy tắc trong switch, handler ném lỗi “Unmapped protected operation”, tức chính sách **default deny** tại proxy.
- `DocumentStore` là lớp `private` và được tạo bên trong `secure(...)`; caller của ví dụ chỉ nhận proxy `DocumentApi`. Mẫu kiến trúc này hạn chế lộ raw target cho code bên ngoài.
- Handler unwrap `InvocationTargetException` để exception nghiệp vụ không bị che bởi lớp reflection wrapper; đây không phải lý do để bắt rồi cho phép truy cập khi xảy ra lỗi kiểm tra quyền.

**Giới hạn bảo đảm:** ví dụ chứng minh việc kiểm tra ở những lời gọi **đi qua proxy**. Nó không biến dynamic proxy thành sandbox an toàn chống mã không đáng tin trong cùng JVM, không chứng minh dữ liệu lưu trữ được bảo vệ trước mọi đường trực tiếp, không thực thi per-document ownership/tenant policy và không có bước xác thực danh tính thật. Production cần policy đầy đủ (principal + action + resource), boundary tin cậy và kiểm thử tất cả entry path.

## 7. Vì sao không được chỉ dựa vào annotation?

Marker, annotation hay metadata là cách khai báo chính sách, không phải cơ chế enforcement. Một annotation như `@Restricted` chỉ có hiệu lực khi:

1. Metadata tồn tại ở nơi interceptor có thể đọc.
2. Pointcut xác định đúng method/join point mang metadata.
3. Nền tảng AOP đã cài aspect vào đúng đường gọi.
4. Advice thật sự chạy trước thao tác nguy hiểm.
5. Advice có dữ liệu danh tính và policy đúng để quyết định.
6. Nhánh deny không gọi `proceed()`.
7. Không tồn tại đường thực thi nhạy cảm đi vòng qua toàn bộ cơ chế đó.

Cần xác định rõ metadata đặt trên interface hay implementation, cách inheritance/override hoạt động, visibility runtime của annotation, bridge methods và overload. Không nên mặc định runtime proxy sẽ đọc annotation từ implementation nếu handler nhận một `Method` của interface.

**Quy tắc kiểm chứng tốt:** một method nhạy cảm không được bảo vệ phải là lỗi rõ ràng lúc khởi động/kiểm thử, thay vì mặc nhiên “không thấy annotation tức là public”. Với phương thức công khai thật sự, hãy khai báo policy `PUBLIC` tường minh ở cấu hình tin cậy, tránh nhầm “chưa gắn nhãn” với “ai cũng được gọi”.

## 8. Thứ tự các aspect có thể làm mất lớp bảo vệ

Giả sử một lời gọi đồng thời có security aspect và caching aspect.

~~~text
A. Security ở ngoài cache:

security {
    cache {
        target
    }
}

B. Cache ở ngoài security:

cache {
    security {
        target
    }
}
~~~

Trong cách A, bất kể hit hay miss, mọi lượt gọi phải đi qua `security`. Trong cách B, một cache hit ở lớp ngoài có thể trả dữ liệu ngay mà không gọi tiếp advice phía trong, nghĩa là **security không chạy**. Khi nội dung cache phụ thuộc quyền người gọi hoặc quyền thay đổi, đây có thể là lỗ hổng nghiêm trọng.

Tương tự, cần xét thứ tự với:

- **Retry:** mỗi attempt có cần đánh giá lại quyền hoặc có thể dùng quyết định quyền đã được xác lập tại boundary? Quy tắc phải rõ.
- **Fallback/circuit breaker:** một fallback trả dữ liệu từ nguồn khác vẫn phải chịu đúng policy nếu dữ liệu nhạy cảm.
- **Transaction:** authorization nên hoàn tất trước side effect. Nếu data authorization phụ thuộc trạng thái DB có thể biến đổi, cần thiết kế nhất quán và chống race.
- **Rate limit/quota:** nếu đặt chỉ ở trong cache thì hit có thể không tiêu thụ quota dù chính sách yêu cầu mọi request đều bị giới hạn.
- **Audit:** logging một lần mỗi caller truy cập phải được bảo đảm cả khi cache hit hoặc fallback.
- **Exception handler:** không biến `SecurityException` thành giá trị “thành công” hoặc lỗi chung khiến caller được tiếp tục qua một nhánh khác.

Tối ưu hiệu năng không được thay đổi vị trí của security boundary khi chưa phân tích an toàn. Chỉ chọn thứ tự bằng tiêu chí “nhanh hơn” là thiếu.

## 9. Danh tính, tenant và bối cảnh lời gọi

Một security aspect thường phụ thuộc **ai đang gọi**, **được phép làm gì**, **trên tài nguyên nào** và **trong bối cảnh nào**:

~~~text
AuthorizationDecision =
    policy(principal, action, resource, tenant, context)
~~~

Principal cần đến từ nguồn đã được xác thực, không lấy trực tiếp từ một tham số tự khai báo của caller thiếu tin cậy. Nếu dùng role-based access control (RBAC), role ADMIN không luôn đồng nghĩa có quyền trên mọi tenant hay mọi đối tượng; cần thêm ownership/tenant rules phù hợp.

Khi dùng context gắn theo luồng như `ThreadLocal`:

- Executor/callback có thể chạy trên luồng khác và **không tự có** context của caller.
- Pool có thể tái sử dụng luồng; context không cleanup đúng sẽ làm request sau nhận identity cũ.
- Một callback tiếp tục sau request có thể dùng identity đã hết hạn hoặc quyền đã thay đổi.
- Khi security context thiếu, không tự gán role đặc quyền để “cho công việc chạy”.
- Việc truyền ngữ cảnh phải do thành phần tin cậy thực hiện, với lifetime và phạm vi ủy quyền rõ ràng.

Đối với tác vụ chạy nền hợp lệ, nên dùng identity hệ thống có quyền **tối thiểu** cho công việc đó và được cấp có chủ đích. Không bê nguyên quyền của caller sang worker vô thời hạn mà không xét expiry, scope hoặc khả năng thu hồi.

## 10. Kiểm tra quyền theo dữ liệu: chống *time-of-check to time-of-use*

Giả sử advice xác minh caller có quyền sửa tài liệu rồi target tiếp tục công việc dài trước khi sửa dữ liệu. Trong lúc đó, ownership/permission có thể đổi. Đây là vấn đề *time-of-check to time-of-use* (TOCTOU).

~~~text
T0: advice kiểm tra caller có quyền trên document X
T1: quyền/owner của X thay đổi
T2: target ghi X dựa trên quyết định cũ
~~~

Không có một cách sửa chung cho mọi hệ thống. Với dữ liệu có yêu cầu nhất quán cao, cần đánh giá quyền trong cùng boundary dữ liệu có khả năng bảo vệ, dùng version check, transaction hoặc cơ chế cập nhật có điều kiện phù hợp. Cần xác định policy có cho dùng **quyền tại lúc bắt đầu request** hay bắt buộc **quyền tại lúc commit**.

Vì vậy một advice chỉ kiểm tra role ở đầu method thường **không đủ** nếu authorization phụ thuộc trạng thái resource có thể bị thay đổi đồng thời. AOP giúp tập trung policy, nhưng nơi ra quyết định phải ăn khớp với semantics dữ liệu.

## 11. Lỗi xử lý bảo mật và quy tắc fail closed

Một lỗi triển khai nguy hiểm:

~~~java
try {
    verifyPermission();
} catch (Exception error) {
    log(error);
}
return proceed(); // Vẫn chạy target khi phép kiểm tra lỗi.
~~~

Đây là mã minh họa, không phải chương trình độc lập. Nếu `verifyPermission()` lỗi do không lấy được policy hoặc identity, nhánh `proceed()` vẫn được chạy, dẫn tới *fail open* ngoài ý muốn.

Một thiết kế an toàn hơn:

~~~text
try:
    decision = evaluatePermission(trustedIdentity, action, resource)
catch policyFailure:
    recordFailureSafely()
    deny()

if decision != ALLOW:
    deny()

return proceed()
~~~

Đây cũng là pseudocode. Trong những hệ thống có mô hình quyết định phong phú (`PERMIT`, `DENY`, `INDETERMINATE`, `NOT_APPLICABLE`), chỉ `PERMIT` hợp lệ mới được proceed theo policy. Không đồng nhất `NOT_APPLICABLE` hay `INDETERMINATE` với quyền truy cập công khai.

Nếu không đọc được policy bắt buộc, cần có hành vi từ chối rõ ràng, ghi nhận sự cố và xử lý theo mức độ nghiêm trọng. Với lỗi hệ thống, có thể báo lỗi phù hợp mà không tiết lộ chi tiết policy, danh tính khác hoặc cấu trúc nội bộ cho caller.

### Lỗi ném trong target khác lỗi authorization

Nếu authorization đã cho phép rồi target ném lỗi nghiệp vụ, advice không nên nuốt lỗi đó rồi trả dữ liệu tùy ý. Bảo toàn cause và đường lỗi giúp người gọi không hiểu nhầm hoạt động đã thành công. Ngược lại, nếu caller thiếu quyền thì **không gọi target để tạo lỗi rồi dùng lỗi đó làm authorization**.

Cần phân biệt audit của “yêu cầu bị từ chối” với “phương thức chạy rồi thất bại”; chúng là sự kiện khác nhau về bảo mật.

## 12. Bất đồng bộ, callback và những lối đi sau return

Một method trả `CompletionStage<T>` có thể chỉ lên lịch công việc rồi trả ngay. Nếu advice kiểm tra quyền trước return, công việc thực tế có thể chạy sau trên executor khác.

Các câu hỏi bắt buộc:

1. Authorization ở T0 có đủ theo hợp đồng, hay worker phải kiểm tra lại quyền lúc truy cập resource?
2. Worker có nhận identity/tenant scope bằng cơ chế được tin cậy không?
3. Quyền có thể bị thu hồi trước lúc worker chạy không?
4. Kết quả/callback có gửi dữ liệu nhạy cảm cho subscriber khác không?
5. Nếu future bị cancel, side effect đã phát ra có được xử lý đúng không?
6. Nếu stage hoàn tất bất thường, nhánh fallback có phát dữ liệu ngoài quyền không?

Một advice đặt ở ranh giới method return **không tự trở thành interceptor của mọi continuation**. Với `Flow.Publisher<T>` hoặc luồng lazy, thao tác thực tế có thể chỉ bắt đầu khi subscriber đăng ký, và cùng publisher có thể có nhiều subscription. Cần chọn authorization boundary phù hợp tại subscription/resource access và quản lý quyền/tenant trên mỗi thực thi theo contract.

Tránh cache một `CompletionStage` hoặc publisher chứa dữ liệu theo tenant rồi chia sẻ với tenant khác chỉ vì tham số nghiệp vụ giống nhau. Không coi việc future được trả về bình thường là tín hiệu tác vụ đã an toàn hoặc thành công.

## 13. Đường vòng ngoài AOP: resource boundary và nguyên tắc đặc quyền tối thiểu

Ngay cả khi mọi method trong một interface đều có security advice, một thành phần có quyền trực tiếp với database, filesystem, network client hoặc bộ nhớ tiến trình vẫn có thể tác động resource mà không qua interface đó. Vì thế:

- Hạn chế việc lộ reference target và resource handle.
- Cấp quyền cho component/resource theo **least privilege**; code đọc dữ liệu không mặc định có quyền xóa.
- Đặt chính sách ở boundary có thể bao phủ mọi entry path, không chỉ ở UI/transport.
- Dùng kiểm soát quyền của chính resource khi có thể, nhất là đối với dữ liệu nhiều tenant hoặc hành động nguy hiểm.
- Tách public API và internal API; explicit authorization cho những lối đi nội bộ có quyền đặc biệt.
- Không dựa vào việc đổi tên method/package hay che annotation như một hàng rào an ninh.

Mã Java chạy trong cùng process và được cấp quyền đầy đủ có thể có nhiều đường truy cập khác; một dynamic proxy không phải ranh giới cô lập dành cho mã độc tùy ý. AOP bảo mật có giá trị lớn trong thực thi chính sách ứng dụng và hạn chế lỗi lập trình, nhưng cần kết hợp kiến trúc và quyền ở tài nguyên để có độ bảo đảm tương ứng.

## 14. Kiểm chứng rằng security aspect không thể bị bỏ qua

Kiểm thử security không chỉ là “advice đã được gọi” hoặc “một request thiếu quyền bị chặn”. Phải kiểm chứng **đường đi không có coverage**, **phương thức mới**, **cache hit**, **self-invocation** và **failure của policy engine**.

| Trường hợp kiểm thử | Kết quả cần chứng minh |
| --- | --- |
| Caller có quyền, gọi qua proxy | Advice cho phép và target thực thi đúng |
| Caller thiếu quyền, gọi qua proxy | Deny **trước** side effect; target execution count không tăng |
| Không có identity | Deny, không tự chọn principal đặc quyền |
| Role không đủ trên resource/tenant | Deny, kể cả khi cùng method/input đã cache |
| Method nhạy cảm mới nhưng thiếu policy | Startup fail hoặc default deny, không chạy trống policy |
| Self-invocation từ method ít quyền sang method nhạy cảm | Không có đường bypass; dùng boundary hoặc weaving thật được kiểm chứng |
| Gọi trực tiếp implementation/raw target | Không thể tiếp cận từ code không tin cậy hoặc resource vẫn kiểm tra quyền |
| Cache hit / fallback | Vẫn kiểm tra những policy bắt buộc |
| Có nhiều advice | Thứ tự bảo đảm kiểm tra quyền trước mọi đường short-circuit |
| Policy source lỗi/timeout | Fail closed, không proceed |
| Proxy/weaving không được kích hoạt | Health/startup check thất bại hoặc kiểm thử phát hiện ngay |
| Callback / executor / scheduled job | Identity và quyền đúng tại nơi thao tác thật |
| Quyền bị thu hồi trong lúc xử lý | Hành vi phù hợp consistency policy |
| Exception từ target | Không biến lỗi nghiệp vụ thành authorization success giả |
| Gọi overload/inherited/bridge method | Coverage đúng theo danh sách protected operations |

Tốt nhất nên có một **ma trận quyền** trên từng action/resource và những testcase negative tương ứng; sử dụng counter hoặc kiểm chứng dữ liệu thật để đảm bảo target không chạy khi bị deny. Với resource có side effect, chỉ log deny không đủ nếu side effect đã xảy ra trước khi ghi log.

### Kiểm tra lúc build/khởi động

Nếu một hệ thống có registry các protected operations, có thể dùng bài kiểm tra fail-fast:

~~~text
for each exposed operation:
    if operation requires protection:
        assert interceptable(operation)
        assert policyMapped(operation)
        assert targetNotExposedThroughUncheckedPath(operation)

assert securityAdviceRegistered()
assert aspectOrderSatisfiesPolicy()
assert policyRepositoryReachableOrFailClosed()
~~~

Đây là pseudocode diễn tả điều kiện kiểm tra. Việc xác định “đường unchecked” toàn cục có thể cần kiểm thử tích hợp, phân tích kiến trúc và rà soát mã; một vòng lặp reflection đơn giản không tự chứng minh được toàn bộ call graph. Dù vậy, kiểm tra fail-fast vẫn hữu ích để ngăn lỗi cấu hình phổ biến trước khi production nhận request.

### Quan sát khi chạy

Nên ghi nhận số lượt permit/deny và nhóm lỗi policy bằng dữ liệu audit phù hợp, nhưng tránh log token, secret hoặc chi tiết nhạy cảm. Có thể giám sát bất thường: thao tác nhạy cảm thành công mà không có dấu vết authorization tương ứng. Tuy nhiên, telemetry là tín hiệu phát hiện; **không thay thế enforcement**. Nếu audit thất bại, policy phải quy định rõ đó là lỗi chặn thực thi hay chỉ lỗi quan sát, dựa trên yêu cầu nghiệp vụ và tuân thủ.

## 15. Những sai lầm phổ biến và cách khắc phục

| Sai lầm | Vì sao nguy hiểm | Cách thiết kế đúng |
| --- | --- | --- |
| Coi annotation là bằng chứng đã được bảo vệ | Annotation không tự intercept | Kiểm tra proxy/weaving và protected call path |
| Tạo target rồi phát cả raw target và proxy | Caller có đường trực tiếp | Chỉ cấp protected interface, kiểm soát resource |
| Quên self-invocation | Lời gọi `this.method()` có thể bỏ proxy | Chuyển enforcement tới boundary chắc chắn hoặc dùng weaving hỗ trợ |
| Pointcut chỉ chọn vài tên method | Method mới/đổi tên không được kiểm tra | Allowlist/fail-fast coverage |
| Lỗi policy thì ghi log rồi `proceed()` | Fail open | Deny khi không chứng minh được quyền |
| Cache nằm ngoài security | Hit bỏ security advice phía trong | Bảo đảm authorization ở đường luôn chạy |
| Chỉ kiểm tra role, bỏ tenant/resource | Có thể đọc/sửa tài nguyên của người khác | Policy theo principal/action/resource/context |
| Truyền identity qua biến toàn cục | Context lẫn giữa requests | Scoped trusted context và cleanup |
| Tin `ThreadLocal` tự theo callback | Worker có thể không nhận hoặc nhận context cũ | Truyền context có kiểm soát, xác thực tại resource khi cần |
| Kiểm tra sau khi gọi target | Side effect đã xảy ra | Authorization trước thực thi |
| Test chỉ thấy “deny log” | Target có thể vẫn chạy | Kiểm chứng side effect và execution count |
| Cho default “public” khi policy thiếu | Thiếu metadata thành bypass | Default deny hoặc khai báo PUBLIC tường minh |
| Dùng proxy như sandbox an ninh tuyệt đối | Cùng JVM có thể còn đường truy cập khác | Least privilege và resource-level enforcement |
| Không xét TOCTOU | Quyền đổi giữa check và write | Policy nhất quán tại boundary tài nguyên |
| Chỉ thử đường HTTP | Job/consumer/CLI có thể bypass | Kiểm thử toàn bộ entry path |

## 16. Checklist áp dụng

Trước khi tin rằng một security aspect bảo vệ thao tác nhạy cảm, hãy trả lời:

1. Những **action/resource nào cần bảo vệ**, kể cả tác vụ nền và entry point nội bộ?
2. Tất cả đường thực thi đến action có đi qua policy enforcement point không? Nếu không, còn chốt kiểm tra nào ở resource?
3. Pointcut có khớp đúng method/interface/overload và kỹ thuật proxy/weaving đang dùng không?
4. Có method bị `final`/`private` hoặc đường self-invocation khiến advice không chạy không?
5. Target/raw resource có bị lộ cho code không được ủy quyền không?
6. Khi policy/identity vắng mặt, lỗi hoặc unknown, hệ thống có **deny mặc định** không?
7. Chỉ `ALLOW` có thẩm quyền mới đi tới `proceed()`, trước side effect?
8. Quyền có gắn đúng principal, tenant, action, resource và dữ liệu phiên bản không?
9. Cache/fallback/retry và thứ tự aspect có làm skip security không?
10. Với callback/async/publisher, policy có được kiểm tra ở thời điểm và luồng truy cập thật không?
11. Có kiểm tra fail-fast khi thêm method nhạy cảm hoặc thay đổi cấu hình interception không?
12. Kiểm thử negative đã chứng minh **target không chạy** và dữ liệu không thay đổi khi bị deny chưa?
13. Audit/metrics có giúp phát hiện lối đi không qua authorization mà không lộ dữ liệu nhạy cảm không?
14. Quyền tại resource có được giới hạn để giảm hậu quả nếu AOP coverage mắc lỗi không?

## 17. Kết luận

Aspect bảo mật có thể tập trung hóa chính sách kiểm soát truy cập, nhưng hiệu lực của nó phụ thuộc **đường gọi thật sự được intercept**, **pointcut đầy đủ**, **thứ tự advice**, **ngữ cảnh danh tính tin cậy** và **khả năng từ chối mặc định khi thiếu chính sách hoặc dữ liệu**. Một lời gọi không đi qua proxy, một method mới thiếu metadata hoặc một cache hit xảy ra trước security check đều có thể khiến aspect bị vô hiệu hóa ngoài ý muốn.

Cách thiết kế bền vững là đặt authorization tại một boundary mà mọi thao tác nhạy cảm phải đi qua, không lộ đường trực tiếp tới target/resource, kiểm tra fail-fast và kiểm thử negative trên mọi entry path, đồng thời giữ kiểm soát quyền tại tầng tài nguyên phù hợp. **Một security aspect chỉ bảo vệ được những lời gọi mà nó thực sự kiểm soát**, nên bảo đảm coverage và nguyên tắc fail closed quan trọng ngang với việc viết đúng logic kiểm tra quyền.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-009">Kiểm thử nhiều đường vào thay vì chỉ luồng sử dụng thông thường</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử nhiều đường vào thay vì chỉ luồng sử dụng thông thường trong AOP

## 1. Vấn đề: có advice trên đường chính vẫn chưa chứng minh mọi đường đều được bảo vệ

Aspect-Oriented Programming (AOP) cho phép tách kiểm soát truy cập thành một mối quan tâm cắt ngang: tại một join point, advice kiểm tra chủ thể, hành động, tài nguyên và bối cảnh trước khi cho phép thực thi phương thức nghiệp vụ. Điều này giảm trùng lặp, nhưng cũng dễ tạo một giả định nguy hiểm: nếu luồng sử dụng thông thường đã chạy qua advice, mọi lời gọi đến thao tác đó đều an toàn.

Ví dụ luồng quen thuộc:

~~~text
Người dùng -> HTTP handler -> access advice -> service -> tài nguyên
~~~

Test một người dùng trái quyền qua handler trên có thể trả thông báo từ chối đúng. Tuy nhiên, cùng thao tác nghiệp vụ có thể được gọi từ CLI, background job, message consumer, callback, API nội bộ, một endpoint khác, một đối tượng được tạo trực tiếp hoặc một lời gọi nội bộ cùng đối tượng. Nếu các lối này không đi qua điểm kiểm tra quyền, ta có **lỗ hổng phạm vi áp dụng AOP**.

Kiểm thử phải trả lời hai câu hỏi riêng:

1. **Policy correctness:** khi chính sách được gọi, nó quyết định đúng ALLOW/DENY trên chủ thể, hành động và tài nguyên cụ thể không?
2. **Enforcement coverage:** mọi đường có thể đi đến thao tác nhạy cảm có thực sự được kiểm tra quyền tại ít nhất một ranh giới bắt buộc trước khi dữ liệu bị lộ hoặc side effect xảy ra không?

Một bài kiểm thử chỉ đi qua luồng thông thường có thể chứng minh điều thứ nhất trong một tình huống, nhưng không chứng minh điều thứ hai.

Mục tiêu sau bài học là biết lập bản đồ entry point và join point, thiết kế kiểm thử nhiều đường vào, nhận diện bypass do self-invocation/proxy và bất đồng bộ, bảo vệ theo tài nguyên, xác minh hiệu ứng phụ và xây regression test khi kiến trúc thay đổi.

Bài học áp dụng cho **AOP nói chung**, không phụ thuộc vào một framework AOP cụ thể. Ví dụ Java 17 dùng wrapper để mô phỏng advice, đồng thời chỉ rõ nơi cần kiểm thử cơ chế interception/weaving thật.

## 2. Phân biệt các ranh giới

**Entry point:** nơi yêu cầu hoặc sự kiện bắt đầu đi vào một phần hệ thống. Ví dụ HTTP endpoint, CLI command, scheduler, message listener hoặc một lời gọi giữa các module.

**Join point:** điểm thực thi mà cơ chế AOP cho phép quan sát hoặc can thiệp, chẳng hạn method call hoặc method execution. Những loại join point được hỗ trợ phụ thuộc cách triển khai AOP.

**Pointcut:** biểu thức/quy tắc chọn join point cần áp dụng advice.

**Advice:** hành vi chạy tại join point, ví dụ kiểm tra quyền, ghi audit và quyết định có tiếp tục lời gọi hay không.

**Sensitive operation:** hành động đọc/ghi/xuất/xóa dữ liệu hoặc gọi dịch vụ tạo side effect cần bảo vệ. Nó có thể nằm sâu trong hệ thống và có nhiều caller.

**Policy decision point:** nơi đánh giá quyền dựa trên subject/action/resource/context.

**Policy enforcement point:** ranh giới thực sự ngăn thao tác khi không được phép. Advice có thể là một điểm enforcement, nhưng chỉ nếu mọi đường cần bảo vệ thực sự phải đi qua nó.

Ví dụ quyết định quyền đọc một tài liệu:

~~~text
ALLOW nếu:
  chủ thể đã xác thực hợp lệ
  AND chủ thể có quyền READ
  AND tài liệu nằm trong phạm vi được phép
  AND chính sách trạng thái/tenant/chia sẻ hiện tại thỏa mãn
DENY trong các trường hợp còn lại.
~~~

Chỉ kiểm tra một role chung chung chưa đủ: người có vai trò đọc không nhất thiết có quyền đọc mọi tài liệu hoặc mọi tenant. Mặt khác, yêu cầu kiểm tra quyền cũng không được tự động bỏ qua chỉ vì thao tác xảy ra trong backend.

## 3. Những lối vào có thể dẫn tới cùng thao tác

~~~text
                       HTTP endpoint -------+
                                            |
                       Admin endpoint ------+
                                            |
                       CLI / batch ---------+
                                            |
                       Scheduler -----------+---> service / action ---> dữ liệu
                                            |
                       Message consumer ----+
                                            |
                       Callback / future ---+
                                            |
                       Internal direct call +
~~~

Ma trận đầu tiên cần lập:

| Đường vào | Cách có thể bỏ qua điểm kiểm tra dự kiến | Kiểm thử tiêu biểu |
| --- | --- | --- |
| HTTP giao diện chính | Advice chỉ gắn một handler | Người khác tenant gọi handler chính bị từ chối |
| HTTP API khác | Handler mới gọi thẳng nghiệp vụ | Cùng hành động qua API mới vẫn bị từ chối |
| Endpoint quản trị | Cấp role quá rộng hoặc bỏ resource scope | Admin phạm vi hạn chế không truy cập toàn tenant |
| CLI/batch | Command không dùng object đã được instrument | CLI bị deny với tài nguyên ngoài quyền |
| Scheduler | System account được cấp toàn quyền mặc định | Job chỉ được xử lý phạm vi đã cấu hình |
| Message consumer | Tin role/tenant khai trong payload | Message giả metadata không mở được quyền |
| Callback | Chạy sau request context hoặc trên thread khác | Thiếu context thì deny; có context đúng mới allow |
| Future/queue | Advice chỉ bảo vệ submit, không bảo vệ task thực | Worker bị deny nếu chính sách tại execution yêu cầu |
| Self-invocation | Lời gọi nội bộ có thể bỏ qua call interception | Gọi method A dẫn đến sensitive method B vẫn bị kiểm tra |
| Direct reference | Caller cầm object đích thay vì proxy/wrapper | Gọi thẳng đối tượng cũng không bypass được quyền |
| Reflection/dynamic call | Cơ chế gọi không đi qua điểm interception được giả định | Xác minh behavior với runtime và loại join point thực tế |
| Overload/inheritance | Pointcut không khớp chữ ký mới | Mỗi overload/override nhạy cảm vẫn được bảo vệ |
| Retry/replay | Nhánh hồi phục bỏ qua kiểm tra hoặc dùng quyền cũ | Một retry/replay không mở đường đặc quyền |

Không cần tạo tất cả mô hình này trong một ứng dụng nhỏ. Cần liệt kê **đúng những đường vào đang tồn tại**, đặc biệt đường vận hành ít người nhìn thấy.

## 4. Vì sao proxy/interceptor và weaving có coverage khác nhau?

### 4.1. Gọi qua wrapper so với gọi thẳng target

Một dạng interception có thể biểu diễn bằng wrapper:

~~~java
// Fragment minh họa, các thành phần được định nghĩa nơi khác.
Result guardedCall(Request req) {
    policy.requireAllowed(req);
    return target.execute(req);
}
~~~

Caller gọi qua guardedCall sẽ được kiểm tra. Nhưng nếu caller có tham chiếu trực tiếp tới target:

~~~java
// Fragment minh họa lối khác.
Result directCall(Request req) {
    return target.execute(req);
}
~~~

Nếu target không có guard riêng, directCall có thể trở thành bypass. Test chỉ xác minh guardedCall hoạt động chưa chứng minh directCall an toàn.

Với một số hệ thống AOP dựa trên proxy, lời gọi qua proxy mới chạy advice. Với weaving tại method execution, một số lời gọi trực tiếp hoặc nội bộ có thể vẫn bị instrument. Vì vậy, **không khẳng định mọi AOP đều có cùng lỗ hổng proxy**; hãy đo coverage của cơ chế cụ thể.

### 4.2. Self-invocation

Ví dụ:

~~~java
// Fragment minh họa.
public void executeBatch() {
    changeSensitiveState();
}

public void changeSensitiveState() {
    writeRepository();
}
~~~

Lời gọi changeSensitiveState từ executeBatch trên **cùng đối tượng** có thể không đi qua proxy, nên một advice gắn với lời gọi external vào changeSensitiveState không chạy. Nếu AOP instrument tại method execution bằng weaving, kết quả có thể khác.

Bộ kiểm thử cần có ca từ chối đi vào executeBatch và quan sát số lần write thực tế. Không chỉ gọi changeSensitiveState từ client và kết luận mọi đường đều an toàn.

### 4.3. Overload, phương thức kế thừa, constructor và reflection

Khi thêm một overload, đổi package hoặc đổi annotation, biểu thức pointcut có thể không còn khớp. Một số hình thức proxy có giới hạn đối với method final/private hoặc đối tượng không được tạo qua bộ cấp phát phù hợp. Reflection và method handle cũng cần được xem theo cơ chế join point mà runtime hỗ trợ.

Tạo các test rõ ràng:

- Mỗi overload thực sự có thể gọi thao tác nhạy cảm.
- Method qua interface, superclass, override và default implementation.
- Object được xây qua factory/composition root và object được tạo trực tiếp khi điều đó có thể.
- Internal call và external call tới cùng method.
- Đường reflection/dynamic invocation được sản phẩm thật cho phép.
- Sau mỗi refactor đổi tên method/package, pointcut vẫn match đúng hoặc đã được điều chỉnh.

Không cần đoán hành vi của một công nghệ chưa xác định; kiểm thử integration trên runtime thật sẽ cho câu trả lời.

## 5. Chính sách quyền phải dựa trên cả tài nguyên

Tổ chức kiểm thử theo bộ dữ liệu có ý nghĩa:

| Chủ thể | Hành động | Tài nguyên | Kết quả |
| --- | --- | --- | --- |
| Owner có quyền | READ | Tài liệu thuộc phạm vi owner | ALLOW |
| Người cùng tenant nhưng thiếu quyền | READ | Tài liệu cùng tenant | DENY |
| Người có READ ở tenant A | READ | Tài liệu tenant B | DENY |
| Người được chia sẻ tường minh | READ | Tài liệu được cấp grant | ALLOW nếu grant hợp lệ |
| User đã bị thu hồi | UPDATE | Tài liệu từng được phép | DENY theo chính sách hiện hành |
| Anonymous | EXPORT | Tài liệu bất kỳ | DENY |
| Service account có scope A | DELETE | Tài liệu scope B | DENY |
| Chủ thể có READ | DELETE | Tài liệu được đọc | DENY nếu không có DELETE |

Mục đích là tránh lỗi **Broken Object-Level Authorization**: caller có quyền thực hiện một loại thao tác nhưng không có quyền với **đối tượng cụ thể**. Trong ứng dụng đa tenant, không được tin tenant do client tự khai là đủ; phải dựa trên danh tính và quyền được xác nhận tại trust boundary.

Một ma trận hữu ích có năm chiều:

1. **Subject:** anonymous, owner, non-owner, revoked, service account.
2. **Action:** read, update, delete, export, batch.
3. **Resource:** owner, foreign tenant, shared, archived, nonexistent.
4. **Entry:** HTTP, CLI, scheduler, callback, message, direct call.
5. **State/time:** trước/sau thu hồi quyền, trước/sau thay đổi sở hữu, callback muộn.

Không cần kiểm thử tích Descartes vô hạn. Chọn các cặp và tình huống rủi ro cao, đồng thời **mỗi đường vào quan trọng đều có ca DENY**.

## 6. Ví dụ Java 17+: kiểm thử nhiều đường vào có backstop ở nghiệp vụ

Chương trình sau dùng một policy tập trung: subject phải có quyền đọc và thuộc đúng tenant của tài liệu. Hai điểm cưỡng chế được minh họa:

- GuardAdvice kiểm tra trước khi gọi target, tương đương một dạng around advice.
- DocumentService kiểm tra lại ở **ranh giới nghiệp vụ**, vì đó là nơi dữ liệu được trả. Nhờ vậy, caller giữ tham chiếu trực tiếp tới service vẫn không được bypass quyền.

Tạo tệp **AspectAccessPathsDemo.java**:

~~~java
import java.util.function.Supplier;

public class AspectAccessPathsDemo {
    record Actor(String tenantId, boolean canRead) {}
    record Document(String id, String tenantId) {}

    static final class Denied extends SecurityException {
        Denied() {
            super("access denied");
        }
    }

    static final class Policy {
        void requireRead(Actor actor, Document document) {
            if (actor == null || document == null
                    || !actor.canRead()
                    || !actor.tenantId().equals(document.tenantId())) {
                throw new Denied();
            }
        }
    }

    interface DocumentApi {
        String read(Actor actor, Document document);
    }

    static final class DocumentService implements DocumentApi {
        private final Policy policy;

        DocumentService(Policy policy) {
            this.policy = policy;
        }

        @Override
        public String read(Actor actor, Document document) {
            policy.requireRead(actor, document);
            return "DATA:" + document.id();
        }
    }

    static final class GuardAdvice implements DocumentApi {
        private final DocumentApi target;
        private final Policy policy;

        GuardAdvice(DocumentApi target, Policy policy) {
            this.target = target;
            this.policy = policy;
        }

        @Override
        public String read(Actor actor, Document document) {
            policy.requireRead(actor, document);
            return target.read(actor, document);
        }
    }

    static void verify(
            String path, boolean shouldAllow, Supplier<String> action) {
        try {
            String value = action.get();
            if (!shouldAllow) {
                throw new AssertionError("Unexpected access: " + path);
            }
            System.out.println("ALLOW " + path + " -> " + value);
        } catch (Denied denied) {
            if (shouldAllow) {
                throw new AssertionError("Unexpected denial: " + path, denied);
            }
            System.out.println("DENY " + path);
        }
    }

    public static void main(String[] args) {
        Policy policy = new Policy();
        DocumentService core = new DocumentService(policy);
        DocumentApi guarded = new GuardAdvice(core, policy);

        Actor owner = new Actor("TENANT-A", true);
        Actor outsider = new Actor("TENANT-B", true);
        Actor noPermission = new Actor("TENANT-A", false);
        Document doc = new Document("DOC-7", "TENANT-A");

        verify("http-owner", true, () -> guarded.read(owner, doc));
        verify("http-cross-tenant", false,
                () -> guarded.read(outsider, doc));
        verify("cli-no-permission", false,
                () -> guarded.read(noPermission, doc));
        verify("scheduled-cross-tenant", false,
                () -> guarded.read(outsider, doc));

        // Cố ý đi thẳng vào core, bỏ qua GuardAdvice.
        verify("direct-cross-tenant", false,
                () -> core.read(outsider, doc));
        verify("direct-owner", true, () -> core.read(owner, doc));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AspectAccessPathsDemo.java
java AspectAccessPathsDemo
~~~

Stdout chính xác:

~~~text
ALLOW http-owner -> DATA:DOC-7
DENY http-cross-tenant
DENY cli-no-permission
DENY scheduled-cross-tenant
DENY direct-cross-tenant
ALLOW direct-owner -> DATA:DOC-7
~~~

**Đọc kết quả:**

- Chủ thể hợp lệ đi qua wrapper được cho phép.
- Người có quyền READ chung nhưng ở tenant khác vẫn bị từ chối.
- Đường CLI giả lập với người thiếu quyền bị từ chối.
- Đường job giả lập không có đặc quyền mặc định với tenant khác.
- Lời gọi **trực tiếp** vào DocumentService bỏ qua GuardAdvice nhưng vẫn bị deny vì core thực hiện cùng policy.
- Lời gọi trực tiếp hợp lệ vẫn nhận đúng dữ liệu, chứng minh backstop không gây deny tất cả.

Các tên đường HTTP, CLI, scheduler trong chương trình chỉ là **nhãn của các test driver** gọi cùng API; ví dụ không dựng máy chủ, parser hay scheduler thật. Test này minh họa semantics policy và wrapper. Để chứng minh AOP được gắn đúng ở từng entry point của sản phẩm, cần integration test chạy **runtime/cơ chế weaving hoặc interception thật**.

Trong ví dụ, một AccessDenied exception bắt nguồn từ policy; class Denied kế thừa SecurityException nhưng tên/kiểu exception trong ứng dụng thật do hợp đồng API quyết định. Dùng một exception riêng cũng giúp bộ kiểm thử phân biệt DENY do chính sách với các lỗi hệ thống khác.

### Vì sao cần chốt bảo vệ tại core?

Nếu DocumentService trả dữ liệu mà không kiểm tra policy, đường direct có thể bypass wrapper. Việc có chốt quyền ở nơi dữ liệu thực sự được truy cập giúp bảo toàn bất biến khi có caller nội bộ mới. Nếu toàn bộ đường gọi được hệ thống khác bảo đảm buộc phải đi qua advice, có thể chọn một điểm cưỡng chế duy nhất; nhưng bảo đảm đó phải có bằng chứng kiểm thử, chứ không chỉ là quy ước giữa lập trình viên.

## 7. Kiểm thử side effect: từ chối phải xảy ra trước hành động

Một bài test thấy ngoại lệ DENY chưa đủ nếu thao tác ghi đã diễn ra trước đó:

~~~text
caller -> update database -> publish event -> check permission -> DENY
~~~

Caller thấy từ chối, nhưng dữ liệu đã đổi và event đã phát. Với thao tác UPDATE, DELETE hoặc EXPORT, test cần assert đồng thời:

- Trạng thái kết quả là DENY đúng hợp đồng.
- Số lần ghi vào repository bằng 0.
- Số thông điệp hoặc callback ra ngoài bằng 0.
- Không có dữ liệu bí mật được trả, ghi vào log hay phát qua event.
- Không có tác dụng phụ bị retry hoặc compensation thiếu kiểm soát.

Ví dụ expected assertion:

~~~text
outcome = DENIED
repositoryWriteCount = 0
publishedEventCount = 0
externalRequestCount = 0
disclosedRecords = 0
~~~

Đây là checklist kiểm thử, không phải stdout của chương trình Java ở phần trước. Có thể dùng fake repository, fake event publisher và test spy để đo số lần gọi. Với tác vụ có transaction hoặc nhiều bước, cần kiểm tra trạng thái **sau transaction/commit** chứ không chỉ số lệnh được gọi.

Một around advice được thiết kế đúng nên từ chối trước khi gọi target nếu không có quyền; nhưng nếu target có lối khác trực tiếp vào phần cập nhật, cũng cần một điểm enforcement đủ mạnh tại nơi chịu trách nhiệm cho side effect.

## 8. Internal call và self-invocation

Tình huống minh họa:

~~~java
// Fragment của một class; chưa phải chương trình độc lập.
public void batchUpdate() {
    updateSensitiveRecord();
}

public void updateSensitiveRecord() {
    writeDatabase();
}
~~~

Với một số cơ chế proxy-based interception, lời gọi từ batchUpdate tới updateSensitiveRecord trong cùng đối tượng không đi qua proxy, nên advice gắn ở external entry của updateSensitiveRecord không được gọi lần nữa. Với method-execution weaving, kết quả có thể khác.

Bài kiểm thử cần chủ động gọi batchUpdate với người không được phép và assert writeCount bằng 0. Nếu test chỉ gọi updateSensitiveRecord từ một client đi qua proxy, trường hợp tự gọi này vẫn chưa được chứng minh.

Cách xử lý có thể là chuyển kiểm tra tới ranh giới nghiệp vụ bắt buộc, tổ chức caller đi qua một API có guard đáng tin, hoặc dùng cơ chế AOP hỗ trợ join point mong muốn. Không áp dụng một cách khắc phục chung cho mọi implementation; quan sát thực tế runtime và bảo toàn kiến trúc quyền là yêu cầu chính.

## 9. CLI, batch và scheduler: đừng coi nguồn nội bộ là toàn quyền

Một batch import có thể xử lý hàng nghìn bản ghi. Nếu nó gọi thẳng repository và không thực thi chính sách theo tài nguyên, một lỗi cấu hình hoặc một tệp nhập sai tenant có thể gây ảnh hưởng lớn.

Những câu hỏi cần kiểm thử:

1. Command được gọi bằng danh tính nào, và actor gốc có được lưu vết không?
2. Task dùng service account nào, scope được cấu hình tới đâu?
3. Mỗi record trong batch có được kiểm tra đúng resource/tenant không?
4. Batch chứa cả bản ghi hợp lệ và không hợp lệ sẽ atomic rollback hay partial success theo hợp đồng?
5. Retry một batch bị từ chối có gây mutation lặp lại không?
6. Một lệnh quản trị có được phép bỏ một số kiểm tra không; nếu có, ngoại lệ đó có được chính sách phê duyệt và ghi audit?

System account phải tuân theo nguyên tắc **quyền tối thiểu**. Một job chạy tự động không chứng minh mọi hành động của nó đều được ủy quyền. Nếu thiếu actor người dùng, chính sách có thể dùng một danh tính workload đã được xác thực và bị giới hạn, thay vì coi null principal là admin.

## 10. Message consumer và dữ liệu nguồn không đáng tin

Một message có thể chứa subjectId, role, tenantId, action và resourceId. Các trường trong payload **không tự chứng minh quyền**. Consumer phải xác định nguồn tin cậy và đánh giá chính sách theo resource thật.

Ví dụ payload:

~~~json
{
  "actorId": "user-17",
  "tenantId": "TENANT-A",
  "role": "ADMIN",
  "action": "DELETE",
  "resourceId": "DOC-7"
}
~~~

Nên kiểm thử:

- Role tự khai là ADMIN nhưng người gửi không có bằng chứng ủy quyền.
- Tenant trong payload khác tenant được xác nhận.
- Message bị replay sau khi role của user bị thu hồi.
- Consumer retry code qua nhánh recovery và bỏ qua một advice.
- Service account của consumer có quyền vận hành queue nhưng không có quyền xóa tùy ý mọi document.
- Message tới từ producer hợp lệ nhưng resource đã đổi ownership.

Mỗi hệ thống có thể chọn “quyền tại lúc yêu cầu được nhận” hoặc “quyền tại thời điểm consumer thực thi”. Nếu cần giữ quyền của thời điểm trước, phải có **ủy quyền có scope, thời hạn và nguồn tin cậy rõ ràng**. Không ngầm giữ một role trong message mãi mãi.

## 11. Callback, future và ngữ cảnh bảo mật qua luồng

Một lời gọi được advice kiểm tra quyền lúc t0 có thể chỉ enqueue công việc và trả về future. Worker thực sự đọc/ghi dữ liệu ở t1, sau khi caller đã rời đi hoặc quyền thay đổi.

~~~text
t0: request -> advice check -> submit task -> trả future
t1: context thay đổi / quyền bị thu hồi
t2: worker chạy task -> đọc hoặc cập nhật tài nguyên
t3: callback/future hoàn tất
~~~

Điều cần kiểm thử phụ thuộc policy:

- Task có phải kiểm tra quyền lại ở t2 không?
- Ai là principal đáng tin khi worker thực thi?
- ThreadLocal của caller được capture/restore/cleanup như thế nào?
- Nếu không có context hoặc context lỗi, hệ thống DENY hay tự cấp quyền hệ thống?
- Callback được gọi ngay trong thread caller hoặc sau này trên worker có khác hành vi không?
- Retry sau exceptional completion có làm lặp side effect đã xảy ra không?
- Khi cancellation được yêu cầu, code task còn có thể tiếp tục ghi dữ liệu không?

**ThreadLocal không tự động đi qua executor hoặc callback**. Nếu dùng context theo luồng, cần chuyển tường minh hoặc cơ chế propagation của mô hình thực thi; khôi phục context ở worker và dọn trong finally để không rò qua công việc sau.

Một completion callback báo lỗi không đồng nghĩa mọi side effect được rollback. Cancellation cũng không đồng nghĩa task đã thực sự dừng. Khi kiểm thử quyền cho tác vụ bất đồng bộ, assert trạng thái resource cuối cùng và khả năng truy cập dữ liệu, không chỉ trạng thái future.

## 12. TOCTOU: kiểm tra quyền rồi tài nguyên thay đổi

Time-of-check to time-of-use (TOCTOU) xảy ra khi quyền được kiểm tra ở một thời điểm nhưng resource hoặc quyền đổi trước khi thao tác thật diễn ra.

~~~text
t0: policy cho user A quyền sửa DOC-7
t1: quyền bị thu hồi hoặc DOC-7 chuyển owner
t2: worker cập nhật DOC-7 dựa vào kết quả cũ
~~~

Nếu hợp đồng yêu cầu quyền còn hiệu lực tại lúc commit, việc check từ t0 là chưa đủ. Có thể cần kiểm tra lại ở transaction boundary, khóa/version, điều kiện cập nhật dữ liệu hoặc token ủy quyền có thời hạn theo chính sách.

Kiểm thử phải chủ động thay đổi trạng thái giữa t0 và t2; nếu test luôn dùng dữ liệu cố định, nó không phát hiện race. Tình huống này không có đáp án chung cho mọi nghiệp vụ, vì một operation đã được chấp nhận có thể được cấp quyền tiếp tục theo một lease hợp lệ. Nhưng quy tắc về thời điểm quyết định quyền phải được công bố và kiểm chứng.

## 13. Thiết kế ba tầng kiểm thử để không nhầm phạm vi

### Tầng 1: Policy unit test

Gọi trực tiếp policy với nhiều subject/action/resource/context. Bao gồm: hợp lệ, khác tenant, thiếu quyền, null, anonymous, revoked, shared resource và hành động khác nhau. Tầng này chứng minh **logic quyết định**, chưa chứng minh advice thực sự được gắn.

### Tầng 2: Advice/interceptor test

Gọi qua một wrapper/proxy được thiết lập trong test; xác minh:

- Denied action không chạy target.
- Allowed action chạy target đúng một lần.
- Exception từ policy và target truyền đúng hợp đồng.
- Nhánh around/before/after thực thi đúng thứ tự.
- Logging/metrics không che mất quyết định quyền.
- Nếu có nhiều advice, thứ tự kiểm tra quyền so với side effect phù hợp.

Đây là bằng chứng cho **cấu hình interception đã test**, không mặc nhiên phủ hết entry point ngoài môi trường đó.

### Tầng 3: Integration test trên đường vào thực tế

Khởi động runtime AOP và các adapter cần thiết. Gọi đúng HTTP endpoint, CLI, scheduler handler, consumer, callback hoặc service trực tiếp như một caller thực. Ghi nhận:

- Đúng chủ thể/action/resource được đưa vào policy.
- Advice ở join point dự định có chạy hay không.
- Từ chối có giữ dữ liệu bí mật và side effect an toàn không.
- Những điểm direct/self-call có một điểm cưỡng chế tương đương không.
- Sau khi thay đổi phương thức/overload/pointcut, coverage còn đúng không.

Một HTTP test trả “forbidden” có thể là do handler chặn sớm; để biết advice ở service được áp dụng thật, dùng instrumentation/test spy thích hợp thay vì suy đoán từ status code.

## 14. Bản đồ coverage và cách lấy bằng chứng

Một bảng bằng chứng nên liên kết **sensitive operation → entry point → enforcement point → test allow/deny → side effect assertion**:

| Operation | Entry point | Enforcement | Deny test | Side effect |
| --- | --- | --- | --- | --- |
| Read document | HTTP | GuardAdvice + core policy | Cross-tenant | No disclosure |
| Read document | CLI | Core policy | Missing READ | No disclosure |
| Update document | Batch | Core policy / transactional boundary | Invalid resource grant | No write |
| Delete document | Consumer | Authenticated workload + policy | Fake role/foreign tenant | No delete/event |
| Export data | Scheduled task | Scoped service identity + policy | Tenant outside job scope | No export |
| Modify resource | Internal self-call | Guard at sensitive operation | Unauthorized caller | No mutation |

Việc gắn nhãn “enforcement point” bắt buộc đội ngũ chỉ ra **vị trí có thể ngăn hành động**, thay vì chỉ liệt kê test đã pass. Khi một entry point mới xuất hiện, cần thêm nó vào bản đồ và test tương ứng.

### Negative testing phải kiểm tra nguyên nhân từ chối

Nếu test gửi request trái quyền và thấy connection refused, đó không phải bằng chứng authorization hoạt động. Nếu CLI không khởi chạy vì thiếu cấu hình, test cũng chưa kiểm tra policy. Bộ kiểm thử phải phân biệt Denied từ chính sách với lỗi transport, validation, dependency và lỗi ngoài ý muốn.

Đối với thao tác UPDATE/DELETE, kiểm tra DB state sau lỗi. Đối với export/read, đảm bảo response/log/error không vô tình chứa dữ liệu nhạy cảm. Nếu có retry hoặc callback, kiểm tra sau khi toàn bộ tác vụ liên quan đã đến ranh giới hoàn tất/cleanup theo hợp đồng.

## 15. Hạn chế của chỉ một lớp AOP cho bảo mật

AOP có thể giảm lặp mã authorization, nhưng không thay thế thiết kế bảo mật tổng thể. Một chính sách chỉ gắn bằng proxy/wrapper có thể trở nên mong manh nếu code có cách tạo target trực tiếp hoặc chọn sai join point. Những biện pháp hỗ trợ gồm:

- Policy service tập trung để nhiều điểm enforcement sử dụng cùng quy tắc.
- Guard tại boundary sở hữu dữ liệu hoặc thao tác nhạy cảm.
- Least privilege ở data store, queue và service account.
- Identity/context đáng tin tại từng trust boundary.
- Kết quả DENY mặc định khi thiếu thông tin cần thiết.
- Giám sát việc pointcut không còn match sau refactor.
- Integration tests cho entry mới, self-invocation và async continuation.
- Không dùng log/audit thành chứng cứ duy nhất rằng quyền đã được kiểm tra.

Không bắt buộc kiểm tra trùng ở mọi tầng nếu có **một ranh giới duy nhất thực sự không thể bypass**. Tuy nhiên, tính không thể bypass ấy phải được xây dựng và kiểm chứng trên kiến trúc thật.

## 16. Các lỗi phổ biến khi lập bài test

**Chỉ test happy path.** Người hợp lệ đọc được dữ liệu chưa chứng minh người khác không thể đọc.

**Chỉ test policy object.** Unit test có thể pass khi pointcut không match ở runtime.

**Chỉ test endpoint chính.** CLI, consumer, scheduler hoặc lệnh trực tiếp vẫn có thể bypass.

**Chỉ assert exception.** Exception có thể bị ném sau khi mutation đã commit; phải kiểm tra side effect.

**Giả định mọi AOP đều có cùng coverage.** Proxy, wrapper và weaving có quy tắc khác nhau; kiểm tra behavior thật.

**Cho phép null principal như system admin.** Thiếu context phải được xử lý an toàn, thường deny-by-default.

**Tin role/tenant trong message.** Dữ liệu từ producer không tự tạo quyền truy cập.

**Không kiểm tra resource scope.** Role READ không có nghĩa có quyền với tất cả tài liệu.

**Không test overload hoặc method mới.** Pointcut chỉ khớp chữ ký cũ dẫn tới lỗ hổng khi refactor.

**Cho rằng submit job đã được check nghĩa là execution an toàn.** Worker có thể chạy sau khi context/permission đổi.

**Không test retry/replay.** Nhánh hồi phục có thể đi qua code khác hoặc giữ quyền cũ không còn hợp lệ.

## 17. Quy trình thực hành khi thêm một điểm vào mới

1. Xác định tài nguyên và hành động nhạy cảm mà entry point mới có thể chạm tới.
2. Vẽ lại đường gọi tới điểm đọc/ghi dữ liệu thật.
3. Xác định chủ thể và bằng chứng ủy quyền được lấy từ đâu.
4. So điểm gọi với pointcut và join point đang được runtime AOP áp dụng.
5. Tạo ít nhất một trường hợp ALLOW hợp lệ và một trường hợp DENY có ý nghĩa.
6. Với DENY, assert không có data disclosure, mutation, event hoặc external call.
7. Nếu có callback/queue, xác định quyền tại thời điểm đăng ký hay thực thi.
8. Nếu có self-call/direct-call, xác minh guard bắt buộc vẫn hoạt động.
9. Chạy integration test qua chính adapter thật, ghi nhận lý do từ chối.
10. Cập nhật ma trận khi có overload, tài nguyên mới, thay đổi pointcut hoặc chính sách chia sẻ.

## 18. Bài tập tự đánh giá

**Câu 1.** Một user không có quyền bị từ chối khi gọi HTTP, nhưng CLI gọi thẳng service và xem được cùng tài liệu. Điều gì chưa đúng?

**Đáp án:** Enforcement coverage thiếu. Đường HTTP chạy advice, CLI không có kiểm tra quyền tương đương. Cần buộc CLI đi qua guard và/hoặc bảo vệ tại thao tác nhạy cảm.

**Câu 2.** Tại sao test nhận Denied không đủ cho phương thức xóa dữ liệu?

**Đáp án:** Xóa có thể đã xảy ra trước khi policy từ chối. Phải kiểm tra trạng thái database, event, external request và ranh giới commit, đảm bảo không có side effect trái quyền.

**Câu 3.** Nếu một phương thức tự gọi một phương thức nhạy cảm trên cùng đối tượng, advice có luôn chạy không?

**Đáp án:** Phụ thuộc cơ chế AOP. Proxy-based call interception có thể bỏ qua self-invocation, method-execution weaving có thể khác. Test phải chạy trên runtime thật và giữ guard ở boundary phù hợp.

**Câu 4.** Người dùng có READ nhưng không thuộc tenant của tài liệu. Có được phép không?

**Đáp án:** Không, trừ khi có một grant/resource policy hợp lệ cho phép rõ ràng. Authorization cần dựa trên tài nguyên cụ thể, không chỉ role.

**Câu 5.** Một message chứa role ADMIN từ producer không đáng tin. Consumer có thể tin không?

**Đáp án:** Không. Cần xác thực nguồn, chủ thể, phạm vi ủy quyền và đánh giá policy tại trust boundary.

**Câu 6.** Task được submit khi user hợp lệ, nhưng chạy sau khi quyền bị thu hồi. Kiểm thử gì?

**Đáp án:** Tạo trường hợp thay quyền giữa submit và execution, rồi xác minh behavior theo chính sách thời điểm authorization đã công bố; không mặc định context ở thời điểm submit luôn còn hợp lệ.

**Câu 7.** Policy unit test đều pass nhưng một overload mới không có advice. Loại test cần thêm?

**Đáp án:** Integration test gọi overload qua runtime AOP thật, xác minh policy/enforcement hoạt động trước side effect. Có thể dùng spy để chứng minh advice được kích hoạt.

**Câu 8.** Vì sao đường direct trong chương trình Java minh họa vẫn bị từ chối dù không dùng GuardAdvice?

**Đáp án:** Vì DocumentService cũng gọi Policy.requireRead tại ranh giới dữ liệu. Đây là một điểm cưỡng chế bổ sung chống bypass wrapper.

## 19. Checklist trước khi công bố đã kiểm thử truy cập

- [ ] Có inventory sensitive operation và entry point hiện tại.
- [ ] Có ma trận subject/action/resource/entry/context tối thiểu.
- [ ] Có test cross-tenant và thiếu quyền cho mỗi đường vào quan trọng.
- [ ] Có bằng chứng pointcut match đúng join point trên runtime thật.
- [ ] Có kiểm thử self-invocation, direct reference và overload nếu kiến trúc cho phép.
- [ ] Có test deny với side effect bằng 0, không chỉ exception.
- [ ] Có xử lý an toàn khi principal/context thiếu hoặc giả mạo.
- [ ] Có test callback/future/worker khi context hoặc permission đổi.
- [ ] Có kiểm thử CLI/batch/message consumer với least-privilege system account.
- [ ] Có kiểm tra quyền theo resource, không chỉ role chung.
- [ ] Có regression khi thêm entry point, refactor pointcut hoặc thay policy.
- [ ] Có danh sách đường chưa kiểm chứng cùng phương án giảm thiểu rủi ro.

## Kết luận kiến thức

Một access-control aspect hoạt động trên luồng thông thường chưa chứng minh mọi lời gọi nghiệp vụ đã an toàn. Phải xuất phát từ **tài nguyên và thao tác nhạy cảm**, liệt kê tất cả đường có thể đi tới chúng, đối chiếu coverage của cơ chế AOP thực, rồi kiểm thử cả ALLOW, DENY và hậu quả ở mỗi đường. Bảo vệ cần kiểm tra subject/action/resource/context tại một ranh giới không thể bỏ qua; trực tiếp, nội bộ, callback, job và message phải được xem như những trường hợp kiểm thử thật.

</details>

- [Quay lại đầu trang](#back-to-top)
