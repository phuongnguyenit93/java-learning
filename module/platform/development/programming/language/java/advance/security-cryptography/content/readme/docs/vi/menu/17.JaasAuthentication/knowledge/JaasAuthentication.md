<a id="back-to-top"></a>

# 17. Xác thực JAAS

## Menu
- [17.1 Phân biệt xác thực danh tính với quyết định phân quyền trong ứng dụng](#jaas-authentication-001)
- [17.2 Subject, Principal và public hoặc private credentials](#jaas-authentication-002)
- [17.3 LoginContext, Configuration và mô hình LoginModule có thể thay thế](#jaas-authentication-003)
- [17.4 CallbackHandler tách thu thập thông tin đăng nhập khỏi cơ chế xác thực](#jaas-authentication-004)
- [17.5 Vòng đời initialize, login, commit, abort và logout của LoginModule](#jaas-authentication-005)
- [17.6 required, requisite, sufficient, optional và phối hợp nhiều LoginModule](#jaas-authentication-006)
- [17.7 Subject.current, Subject.callAs và cách đọc mã cũ sử dụng doAs](#jaas-authentication-007)
- [17.8 Quản lý credential, Subject chỉ đọc và xử lý LoginException](#jaas-authentication-008)
- [17.9 Ranh giới giữa cấu hình JAAS với mô hình security provider của JCA](#jaas-authentication-009)

## <a id="jaas-authentication-001">17.1 Phân biệt xác thực danh tính với quyết định phân quyền trong ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# 17.1 Phân biệt xác thực danh tính với quyết định phân quyền trong ứng dụng

## Mục tiêu

Sau bài này, bạn có thể:

1. Trả lời rõ hai câu hỏi bảo mật khác nhau: **Authentication — “Bạn là ai?”** và **Authorization — “Bạn có được thực hiện hành động này đối với tài nguyên cụ thể này, trong ngữ cảnh hiện tại không?”**
2. Hiểu JAAS (**Java Authentication and Authorization Service**) trong Java 17, đặc biệt **LoginContext**, **LoginModule**, **Configuration**, **CallbackHandler**, **Subject**, **Principal**, các pha **login/commit/abort/logout**.
3. Phân biệt việc **LoginContext.login() thành công** với quyết định cho phép đọc hóa đơn, sửa hồ sơ, gọi API quản trị hay truy cập tài nguyên thuộc tenant khác.
4. Thiết kế authorization kiểm tra **actor + action + resource + context**, áp dụng **deny-by-default**, **least privilege**, **tenant isolation** và kiểm tra **quyền ở từng object** thay vì chỉ kiểm tra đăng nhập hoặc vai trò.
5. Chạy ví dụ Java 17+ có **LoginModule tùy chỉnh**, Subject nhận một Principal sau khi xác thực, và **policy của ứng dụng** từ chối truy cập sai tenant/chủ sở hữu dù người gọi đăng nhập hợp lệ.
6. Nhận biết quan hệ giữa JAAS Authorization truyền thống với **Java Security Manager/Policy** trong tài liệu cũ; hiểu vì sao không được coi **Subject.doAs** là cơ chế phân quyền nghiệp vụ tự động trên các JDK hiện đại.
7. Xây test và audit đúng cho các trường hợp **chưa xác thực**, **đã xác thực nhưng không đủ quyền**, **quyền bị thu hồi**, **mạo danh**, **IDOR/BOLA** và **session context bị trộn**.

**Ý chính:** Xác thực cho ứng dụng một **identity đã được kiểm chứng qua cơ chế đáng tin**; phân quyền là **một quyết định khác, lặp lại tại từng ranh giới tài nguyên và thao tác**. **Đã đăng nhập không đồng nghĩa được phép làm mọi việc.** JAAS có cả các API và tài liệu authorization lịch sử, nhưng **kết quả authentication không tự động cấp quyền nghiệp vụ** cho mọi invoice, tenant, database row hoặc API.

## 1. Hai câu hỏi: Authentication và Authorization

### 1.1 Authentication — xác thực danh tính

**Authentication (AuthN)** trả lời: một thực thể (người dùng, máy chủ, service hoặc tiến trình) có thực sự chứng minh được identity mà nó đang khai báo hay không?

Ví dụ bằng chứng:

- Mật khẩu được xác minh theo đúng password verifier có salt và KDF phù hợp.
- Token/session đã được xác thực chữ ký/MAC, issuer, audience, hiệu lực và ràng buộc cần thiết.
- Private key chứng minh qua mTLS hoặc một giao thức ký thách thức đúng.
- Kerberos ticket được xác minh theo cơ chế JAAS/GSS-API.
- MFA/passkey, theo chính sách của identity provider và application.

Sau khi chứng cứ hợp lệ, hệ thống có thể lập một **authentication context**: user ID ổn định, cơ chế xác thực, thời điểm, độ tin cậy, tenant được liên kết, session ID và các claim đã kiểm chứng. Không có bằng chứng hợp lệ thì **không được tự nhận actor theo dữ liệu do client gửi**.

Ví dụ:

~~~text
HTTP request
Authorization: Bearer <token>

   Token/credential validation
       |
       +--- Không hợp lệ ---> Reject (chưa được xác thực)
       |
       +--- Hợp lệ --------> Authenticated identity:
                             userId = "alice"
                             authMethod = "oidc"
                             session = ...
~~~

**Authentication không nói Alice có được xem hóa đơn số 127 của Bob.** Nó chỉ nói hệ thống có cơ sở tin người gọi là Alice theo quá trình kiểm tra tương ứng.

### 1.2 Authorization — quyết định quyền trên tài nguyên

**Authorization (AuthZ)** trả lời: actor đã được xác thực (hoặc một public/anonymous actor được chính sách cho phép) có quyền thực hiện **một action** trên **một resource** trong **một context** không?

Mô hình:

~~~text
Authorize(
    actor    = Alice,
    action   = "invoice:read",
    resource = Invoice 127,
    context  = {tenant: "tenant-A", time, authStrength, ...}
)
    => ALLOW hoặc DENY
~~~

Policy có thể yêu cầu đồng thời:

- Alice đã được xác thực từ một nguồn tin.
- Alice có quyền **INVOICE_READER** theo role/permission store đáng tin.
- Invoice 127 thuộc **tenant-A** và Alice có membership hợp lệ với tenant này.
- Alice là **owner** hoặc được ủy quyền đọc tài nguyên đó.
- Invoice chưa bị khóa/tịch thu hoặc có các trạng thái nghiệp vụ cho phép đọc.
- Phiên còn hiệu lực, MFA gần đây hoặc thiết bị đạt điều kiện nếu thao tác nhạy cảm.
- Không có explicit deny/thu hồi quyền nào vượt các điều kiện allow.

Nếu một điều kiện bắt buộc không đạt, kết quả phải là **DENY**.

### 1.3 Authentication và Authorization độc lập nhưng liên quan

| Khía cạnh | Authentication (AuthN) | Authorization (AuthZ) |
| --- | --- | --- |
| Câu hỏi | Ai đang gọi? Bằng chứng identity hợp lệ? | Actor này được làm gì, với tài nguyên nào, lúc nào? |
| Dữ liệu đầu vào | Credential, session/token, client certificate, challenge | Identity đã xác thực, policy/role, tenant, quyền đối tượng, context |
| Thành phần điển hình | LoginContext/LoginModule, IdP, session verifier | Authorization service, policy decision point, ACL/RBAC/ABAC/ReBAC |
| Kết quả | Identity đáng tin hoặc thất bại | ALLOW/DENY cho một action/resource |
| Thời điểm | Khi đăng nhập hoặc xác minh request/session | **Mỗi thao tác/tài nguyên nhạy cảm**; có thể có cache ngắn hạn theo policy |
| Thay đổi thường gặp | Logout, hết phiên, token sai, MFA | Vai trò đổi, quyền bị thu hồi, resource đổi owner, chuyển tenant |
| Sai lầm | Chấp nhận userId do client khai | Cho phép vì user đã login mà không kiểm tra object-level access |
| Bảo mật | Ngăn giả mạo identity | Ngăn vượt quyền, xem nhầm dữ liệu, leo thang quyền và cross-tenant access |

**Một tài nguyên công khai có thể được truy cập không cần login** nếu policy cố ý cho phép actor anonymous thực hiện action cụ thể. Ngược lại, ngay cả admin đã xác thực cũng **không nhất thiết được quyền đọc mọi dữ liệu** nếu chính sách quy định ranh giới tenant, phân tách nhiệm vụ hoặc dữ liệu đặc biệt.

### 1.4 Ví dụ ngắn: login thành công vẫn bị từ chối

~~~text
Alice đăng nhập đúng mật khẩu            -> AuthN: SUCCESS
Alice mở invoice do Alice sở hữu          -> AuthZ: ALLOW (nếu policy cho phép)
Alice mở invoice thuộc tenant-B           -> AuthZ: DENY
Alice sửa cấu hình hệ thống chỉ admin      -> AuthZ: DENY
Alice vừa bị thu hồi vai trò invoice.read  -> AuthZ: DENY ở lần check tiếp theo
~~~

**AuthN thành công + AuthZ thất bại là hành vi hoàn toàn bình thường và phải được kiểm thử.**

## 2. JAAS xuất hiện ở đâu trong câu chuyện này?

**JAAS** là thành phần trong Java SE cho cơ chế **xác thực có thể cắm nhiều provider/module**. JAAS còn có mô hình **authorization dựa trên Subject/Principal** tích hợp với hệ thống permission/security policy Java truyền thống. Vì vậy sẽ sai nếu nói “JAAS chỉ có authentication và không hề có authorization”; nhưng cũng sai nếu nói “JAAS login tự cấp quyền API”.

Trong ứng dụng hiện đại, cần phân biệt rõ:

- **JAAS authentication layer**: xác định và kiểm chứng identity, xây Subject/Principals theo module được cấu hình.
- **Authorization policy của ứng dụng**: quyết định user/service được thực hiện action trên resource cụ thể hay không.
- **Lịch sử JAAS permission-based authorization**: dùng Subject/Principal, Subject.doAs, AccessControlContext, Policy và Security Manager trong kiến trúc Java cũ; **không phải lớp kiểm tra RBAC/tenant nghiệp vụ tự động**.

### 2.1 Những class/interface chính của JAAS

| API | Trách nhiệm |
| --- | --- |
| **javax.security.auth.login.LoginContext** | Điều phối đăng nhập theo tên cấu hình, gọi LoginModule; login/logout và lấy Subject |
| **javax.security.auth.login.Configuration** | Chọn LoginModule nào, thứ tự, control flag và options của từng module |
| **javax.security.auth.spi.LoginModule** | Xác thực theo cơ chế cụ thể: Kerberos, password backend, smartcard, v.v. |
| **javax.security.auth.callback.CallbackHandler** | Cung cấp câu trả lời cho Callback (tên, mật khẩu, PIN...), tách UI/source credential khỏi module |
| **NameCallback / PasswordCallback** | Phương tiện truyền tên và mật khẩu cho LoginModule |
| **javax.security.auth.Subject** | Tập các Principal/credentials liên quan đến một thực thể đã hoặc đang được xác thực |
| **java.security.Principal** | Danh tính/thuộc tính nhận dạng được biểu diễn bằng đối tượng có getName() |
| **AppConfigurationEntry** | Tên LoginModule, control flag và options trong cấu hình |
| **LoginException / FailedLoginException** | Báo thất bại login/module; ứng dụng không chấp nhận identity khi login thất bại |

Một Subject có thể có **nhiều Principal** đại diện những danh tính/kiểu nhận dạng khác nhau. Ví dụ account user, Kerberos principal và nhóm/danh tính khác. Không được mặc định rằng Subject chỉ có một Principal hoặc mọi Principal đều là **role**.

### 2.2 Luồng JAAS authentication điển hình

~~~text
Ứng dụng gọi LoginContext("appName")
              |
        Đọc Configuration
              |
       Chọn một hoặc nhiều LoginModule
              |
    LoginModule.initialize(subject, callbackHandler,
                           sharedState, options)
              |
    LoginModule.login()     -> xác thực credential
              |
       Thành công toàn cục?
          /              \
        NO                YES
        |                  |
  abort()/exception   commit() của modules
        |                  |
    Không tin          Subject chứa Principals /
    danh tính          credentials được module thêm
                           |
                       LoginContext.getSubject()
                           |
                Application authentication context
                           |
                   Authorization trên từng request
~~~

**Hai pha quan trọng:**

1. **login()**: module xác minh bằng chứng identity, giữ trạng thái tạm trong module; chưa nên thêm Principal vào Subject trước khi biết toàn bộ authentication đã thành công.
2. **commit()**: sau khi các module cần thiết vượt qua, module thêm Principal/credential thích hợp vào Subject. Nếu toàn bộ login fail, hệ thống gọi **abort()** để dọn trạng thái; **logout()** dùng để dọn các thông tin module đã commit.

Việc Subject chứa một Principal được tạo từ một module đáng tin sau LoginContext.login() thành công **có ý nghĩa identity**. Nó không tự chứng minh actor được xem mọi object.

### 2.3 Configuration và control flag

Một file cấu hình JAAS theo cú pháp truyền thống có thể như sau:

~~~text
BusinessApp {
    com.example.auth.CompanyLoginModule required;
    com.example.auth.AdditionalCheckModule requisite;
};
~~~

Đây là **minh họa cấu hình**, không thể chạy nếu các class com.example... chưa có trong ứng dụng. Tên lớp và options phải trỏ tới LoginModule hợp lệ.

Các flag của JAAS:

| Flag | Vai trò |
| --- | --- |
| **REQUIRED** | Module cần thành công; dù module thất bại vẫn tiếp tục xử lý những module tiếp theo, cuối cùng login thất bại |
| **REQUISITE** | Module cần thành công; thất bại khiến việc đánh giá dừng ngay |
| **SUFFICIENT** | Nếu thành công và các REQUIRED/REQUISITE trước nó không thất bại, có thể kết thúc login sớm; nếu thất bại tiếp tục module khác |
| **OPTIONAL** | Thành công/thất bại có thể không quyết định tổng thể; nếu không có module bắt buộc thì cần ít nhất một module tùy chọn/đủ điều kiện thành công |

Các flag điều khiển **kết quả xác thực hợp thành**, **không phải** quyền “invoice:read”, “admin:write” hay membership tenant.

**Sai cấu hình JAAS** có thể vô tình khiến một module yếu trở thành đường đăng nhập thay thế cho module mạnh. Nhưng kể cả cấu hình login đúng, authorization trên tài nguyên vẫn phải tự kiểm tra.

## 3. Subject, Principal và claim: không phải quyền cấp sẵn

### 3.1 Principal là identity, không phải permission toàn cục

**Principal** là một đối tượng biểu diễn một danh tính được gắn với Subject. Việc Principal có tên “alice” không có ý nghĩa “Alice được đọc tất cả bảng hóa đơn”.

Ví dụ:

~~~text
Subject:
  Principals:
    UserPrincipal("alice")
    KerberosPrincipal("alice@EXAMPLE.TEST")
  PublicCredentials:   ...
  PrivateCredentials:  ... (có thể chứa bí mật)
~~~

Đây là **biểu diễn khái niệm**, không phải output cố định của JAAS. Các module/provider thực sự có loại Principal riêng.

Subject có thể chứa credentials nhạy cảm. Đừng đưa toàn bộ **Subject.toString()**, getPrivateCredentials() hoặc raw ticket/token vào log/response. Thu thập chỉ những thông tin được phép phục vụ audit (userId, mechanism, session reference, outcome).

### 3.2 Có một Principal trong object chưa đủ chứng minh đã đăng nhập

Một **Subject có thể được tạo/chỉnh sửa bằng code Java** trong các ngữ cảnh được phép. Do đó code không nên tin bất kỳ Subject object hoặc chuỗi Principal nào **được client tùy ý cung cấp** là bằng chứng login.

Một sự kiện “đã xác thực” phải đến từ **đường kiểm tra credential/token có tin cậy**. Khi service tự tạo identity từ header:

~~~text
X-User-Id: admin
X-Role: OWNER
X-Tenant: tenant-A
~~~

mà không xác minh header có đến từ một upstream identity gateway đã xác thực và được bảo vệ khỏi spoofing, kẻ gọi bên ngoài có thể tự chọn identity/role. Tương tự, **new UserPrincipal("admin")** trong một lớp request không tạo đặc quyền nào ngoài những gì code sai có thể tự gán.

**Quy tắc:** chỉ lấy authenticated principal từ một ranh giới được kiểm soát như JAAS login thành công, framework security context được chứng thực, hoặc token/session verifier hợp lệ.

### 3.3 Khi nào role/nhóm trong Principal hoặc JWT có thể dùng?

Một hệ thống JAAS/IdP có thể gắn **group/role/claim** vào Subject hoặc token. Nhưng ứng dụng cần quyết định:

- Nguồn nào được phép phát hành các role/claim đó?
- Claim đã được kiểm chứng chữ ký/issuer/audience/timestamp chưa?
- Claim có đúng **tenant, application, resource, audience** hiện tại?
- Role có hết hạn/đã bị thu hồi không?
- Role có đủ cho action trên object cụ thể không?
- Quyền có bị explicit deny, ownership, separation-of-duties hoặc policy khác hạn chế?

**Role đã xác thực là đầu vào của authorization policy**, không phải mọi lúc là quyết định allow tự động. Ví dụ “MANAGER” của tenant-A không có nghĩa được xem tài liệu bí mật của tenant-B.

### 3.4 Authentication strength và step-up

Một số thao tác cần điều kiện mạnh hơn chỉ “đã đăng nhập”: MFA gần đây, thiết bị quản lý, phiên chưa vượt thời gian, mạng/vùng địa lý được cấp phép hoặc phê duyệt hai người. Đó là **điều kiện context** của authorization.

Ví dụ:

~~~text
Alice đã login bằng mật khẩu hôm qua.
Action: đổi thông tin nhận tiền của doanh nghiệp.

Policy:
  user đã xác thực
  AND có quyền billing:update
  AND thuộc đúng tenant
  AND MFA step-up trong 5 phút gần đây
  AND tài khoản không bị khóa
=> nếu thiếu MFA mới: yêu cầu step-up, không cho UPDATE ngay.
~~~

**Không nhầm yêu cầu MFA** (bước tăng độ tin cậy của xác thực) với **quyền nghiệp vụ**. Hai điều kiện có thể phải đồng thời đúng.

## 4. Mô hình quyết định quyền cho ứng dụng Java

Thiết kế một điểm kiểm tra logic:

~~~text
AUTHZ(actor, action, resource, context):
    nếu actor chưa xác thực và resource không public:
        DENY

    lấy chính sách/role/tenant từ nguồn đáng tin
    tải resource đúng tenant/owner và trạng thái
    áp dụng explicit deny & business constraints
    chỉ ALLOW nếu tất cả điều kiện bắt buộc đạt
    còn lại DENY
~~~

**Deny-by-default** nghĩa là không có rule cho phép rõ thì từ chối. Không viết:

~~~text
if (user != null) {
    return ALLOW;
}
~~~

đối với mọi thao tác nhạy cảm. User được xác thực là **điều kiện đầu**, không phải toàn bộ chính sách.

### 4.1 Actor + action + resource + context

| Trường | Ví dụ | Vì sao cần |
| --- | --- | --- |
| **Actor** | userId "alice", tenant membership | Người thật sự đang yêu cầu |
| **Action** | invoice.read, invoice.approve, invoice.delete | Mỗi thao tác có quyền khác nhau |
| **Resource** | invoice ID 127, tenant-A, owner alice | Quyền phải gắn với object cụ thể |
| **Context** | trạng thái hóa đơn, MFA age, thời gian, môi trường | Quyết định phụ thuộc điều kiện hiện tại |
| **Policy** | role grant + owner + tenant + explicit deny | Tách quy tắc khỏi code chỉ xử lý HTTP |
| **Decision** | ALLOW/DENY + reason code nội bộ | Dễ test/audit, không tiết lộ nhạy cảm |

Một người được **invoice.read** không nhất thiết được **invoice.delete** hoặc **invoice.approve**. Một tài nguyên public cũng có thể chỉ được đọc, không được sửa.

### 4.2 RBAC: role-based access control

RBAC cấp permission cho **vai trò**, vai trò gắn với actor:

~~~text
Role INVOICE_READER -> invoice:read
Role INVOICE_EDITOR -> invoice:read, invoice:update
Role AUDITOR        -> invoice:read (trong phạm vi được giao)
~~~

RBAC hữu ích vì dễ quản trị, nhưng không tự xử lý đủ ranh giới object/tenant:

- Alice có role INVOICE_READER trong tenant-A.
- Invoice X thuộc tenant-B.
- RBAC role check một mình có thể ALLOW sai.
- **Tenant check** phải bổ sung, và có thể thêm owner, purpose hoặc resource ACL.

### 4.3 ABAC: attribute-based access control

ABAC dùng thuộc tính của actor, resource, action và môi trường:

~~~text
allow if
    actor.active
    AND actor.tenantId == resource.tenantId
    AND action == "invoice.read"
    AND "INVOICE_READER" in actor.permissions
    AND resource.status != "SEALED"
~~~

Không tin attribute từ request khi attribute đó phải do server kiểm soát. Ví dụ **tenantId** gửi trong URL chỉ là **target identifier**, không có nghĩa người gọi là member của tenant đó. Kiểm tra membership từ directory/token đáng tin và trạng thái database.

### 4.4 ReBAC: relationship-based access control

ReBAC dựa trên quan hệ giữa actor và resource:

~~~text
Alice --owns--> Invoice 127
Bob   --reviewer-of--> Invoice 127
Carol --member-of--> Team Finance --owns--> Folder F
~~~

Nó đặc biệt hữu ích với tài nguyên có owner/share/team hierarchy. Nhưng vẫn phải kiểm tra nguồn quan hệ và tình huống revoke; không dùng một danh sách ID do client gửi làm bằng chứng.

### 4.5 ACL, capability và các mô hình khác

- **ACL** trên tài nguyên định nghĩa ai hoặc nhóm nào có quyền; cần bảo vệ việc cập nhật ACL.
- **Capability token** có thể trao quyền truy cập tài nguyên cụ thể khi được xác thực và giới hạn rõ ràng, ví dụ signed short-lived download URL. Capability phải có phạm vi/expiry/audience thích hợp.
- **Policy-as-code/authorization service** có thể tập trung quyết định; bản thân policy engine không bảo vệ endpoint nếu backend quên gọi nó.

Bạn có thể kết hợp RBAC + ABAC + ReBAC; không phải chọn đúng một mô hình mới an toàn.

## 5. Phân quyền theo object: chống IDOR/BOLA và rò tenant

Một sai lầm nghiêm trọng:

~~~text
GET /api/invoices/127
    |
Security filter: "User đã login? YES"
    |
db.findInvoiceById(127)
    |
return invoice
~~~

Nếu hóa đơn 127 thuộc Bob hoặc tenant-B, Alice có thể truy xuất bằng cách đoán ID. Đây là một dạng **Broken Object Level Authorization (BOLA)** / **Insecure Direct Object Reference (IDOR)**.

Phiên bản đúng:

~~~text
GET /api/invoices/127
    |
AuthN: xác thực identity Alice
    |
AuthZ: Alice có invoice:read trong tenant-A?
    |
Data layer: lấy invoice 127 trong tenant được phép
    |
AuthZ: owner/ACL/status của object có cho Alice đọc?
    |
ALLOW -> serialize đúng field được phép
DENY  -> trả lỗi theo chính sách, không lộ dữ liệu
~~~

**Lưu ý:** kiểm tra quyền ở đầu request là cần nhưng chưa đủ nếu quyền thay đổi hoặc resource bị chuyển owner giữa lần check và lần sử dụng. Với transaction quan trọng, gắn điều kiện vào truy vấn cập nhật, transaction hoặc cơ chế lock/version phù hợp.

### 5.1 Query bị thiếu tenant filter

Code không an toàn:

~~~java
// Ví dụ anti-pattern; không phải chương trình Java độc lập.
Invoice invoice = repository.findById(request.invoiceId());
return invoice;
~~~

Có thể sửa theo pattern:

~~~java
// Ví dụ minh họa, phụ thuộc repository và actor context.
Invoice invoice = repository.findByIdAndTenantId(
        request.invoiceId(), authenticatedActor.tenantId());
authorization.requireCanRead(authenticatedActor, invoice);
return invoice;
~~~

Với nhiều tenant có thể dùng cùng danh sách role, thiếu tenant condition trong truy vấn làm rò dữ liệu. Chỉ role-check ở controller rồi gọi findById không tự tạo tenant isolation.

### 5.2 Admin role cũng có phạm vi

Quyền **ADMIN** có thể có scope:

- Admin trong một project;
- Admin trong tenant-A;
- Tenant administrator;
- System administrator;
- Compliance reader chỉ đọc;
- Break-glass account theo quy trình đặc biệt.

Không dùng một boolean **isAdmin** toàn cục nếu policy thực tế có nhiều phạm vi; đây là nguyên nhân hay gặp của **privilege escalation**.

## 6. Ví dụ Java 17+ hoàn chỉnh: JAAS login + kiểm tra quyền riêng

Ví dụ này dùng **một chương trình tự chứa**. Nó thể hiện:

1. **LoginContext** nạp **DemoLoginModule** qua Configuration do Java code tạo, thay vì cần file cấu hình ngoài.
2. **CallbackHandler** đưa username/password cho LoginModule.
3. **LoginModule.login()** xác minh credential đào tạo, còn **commit()** mới thêm UserPrincipal vào Subject.
4. Sau xác thực, **canRead(...)** là **chính sách ứng dụng độc lập**, lấy role/tenant từ một directory nội bộ mô phỏng và kiểm tra **tenant + owner** của hóa đơn.
5. Test Alice được xem hóa đơn của mình; bị chặn khi chuyển tenant hoặc chủ sở hữu, dù login thành công.
6. **logout()** xóa Principal mà module đã thêm. Login sai mật khẩu bị từ chối.

**Giới hạn an toàn:** Credential **test-only** được hardcode để làm ví dụ chạy độc lập. **Tuyệt đối không dùng LoginModule này trong production**. Không có password hashing, user store thật, rate limiting, MFA, session manager hoặc audit. Directory Map cố định là **mô phỏng dữ liệu quyền từ backend đáng tin**, không phải nguồn claim do browser truyền vào.

### 6.1 Code độc lập: JaasDecisionDemo.java

Tạo tệp **JaasDecisionDemo.java** bằng UTF-8:

~~~java
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import javax.security.auth.spi.LoginModule;

public class JaasDecisionDemo {
    public record UserPrincipal(String name) implements Principal {
        @Override
        public String getName() { return name; }
    }

    public record Invoice(String tenant, String owner) { }

    public record Entitlement(
            Set<String> roles, Set<String> tenants) { }

    // Provider xác thực đào tạo: không dùng mật khẩu hardcode trong production.
    public static final class DemoLoginModule implements LoginModule {
        private Subject subject;
        private CallbackHandler handler;
        private UserPrincipal user;
        private boolean loggedIn;
        private boolean committed;

        @Override
        public void initialize(Subject subject, CallbackHandler handler,
                               Map<String, ?> sharedState,
                               Map<String, ?> options) {
            this.subject = subject;
            this.handler = handler;
        }

        @Override
        public boolean login() throws LoginException {
            if (handler == null) {
                throw new LoginException("No callback handler");
            }

            NameCallback name = new NameCallback("Username: ");
            PasswordCallback pass =
                    new PasswordCallback("Password: ", false);
            char[] received = null;

            try {
                handler.handle(new Callback[]{name, pass});
                received = pass.getPassword();

                if (!"alice".equals(name.getName())
                        || received == null
                        || !Arrays.equals(received,
                            new char[]{'t','e','s','t','-',
                                       'o','n','l','y'})) {
                    throw new FailedLoginException(
                            "Invalid credentials");
                }
                user = new UserPrincipal("alice");
                loggedIn = true;
                return true;
            } catch (IOException | UnsupportedCallbackException e) {
                throw new LoginException("Cannot obtain credentials");
            } finally {
                pass.clearPassword();
                if (received != null) {
                    Arrays.fill(received, '\0');
                }
            }
        }

        @Override
        public boolean commit() throws LoginException {
            if (!loggedIn) return false;
            if (subject.isReadOnly()) {
                throw new LoginException("Read-only Subject");
            }
            subject.getPrincipals().add(user);
            committed = true;
            return true;
        }

        @Override
        public boolean abort() throws LoginException {
            if (committed) return logout();
            user = null;
            loggedIn = false;
            return true;
        }

        @Override
        public boolean logout() throws LoginException {
            if (committed && !subject.isReadOnly()) {
                subject.getPrincipals().remove(user);
            }
            committed = false;
            loggedIn = false;
            user = null;
            return true;
        }
    }

    private static CallbackHandler credentials(
            String name, char[] secret) {
        return callbacks -> {
            for (Callback cb : callbacks) {
                if (cb instanceof NameCallback n) {
                    n.setName(name);
                } else if (cb instanceof PasswordCallback p) {
                    p.setPassword(secret);
                } else {
                    throw new UnsupportedCallbackException(cb);
                }
            }
        };
    }

    // Configuration nằm trong code để chương trình độc lập và dễ chạy.
    private static Configuration config() {
        return new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(
                    String name) {
                if (!"demo".equals(name)) return null;
                return new AppConfigurationEntry[]{
                    new AppConfigurationEntry(
                        DemoLoginModule.class.getName(),
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        Map.of())
                };
            }
        };
    }

    // Giả lập entitlement store SERVER-SIDE đáng tin.
    // Không xây Map này từ user-supplied request/header.
    private static final Map<String, Entitlement> directory = Map.of(
        "alice", new Entitlement(
                Set.of("INVOICE_READER"), Set.of("tenant-A"))
    );

    // QUYẾT ĐỊNH PHÂN QUYỀN ỨNG DỤNG, KHÔNG PHẢI LOGIN.
    private static boolean canRead(
            Subject authenticated, Invoice invoice) {
        if (authenticated == null || invoice == null) return false;

        Set<UserPrincipal> users =
                authenticated.getPrincipals(UserPrincipal.class);
        if (users.size() != 1) return false;

        UserPrincipal user = users.iterator().next();
        Entitlement rights = directory.get(user.getName());

        return rights != null
                && rights.roles().contains("INVOICE_READER")
                && rights.tenants().contains(invoice.tenant())
                && invoice.owner().equals(user.getName());
    }

    public static void main(String[] args) throws Exception {
        char[] goodSecret = "test-only".toCharArray();
        LoginContext login = new LoginContext(
            "demo", null,
            credentials("alice", goodSecret), config());

        login.login();               // AuthN thành công.
        Arrays.fill(goodSecret, '\0');

        Subject subject = login.getSubject();
        System.out.println("JAAS authenticated: "
                + subject.getPrincipals(UserPrincipal.class)
                        .contains(new UserPrincipal("alice")));

        System.out.println("Own invoice allowed: "
                + canRead(subject, new Invoice("tenant-A", "alice")));

        System.out.println("Cross-tenant denied: "
                + !canRead(subject, new Invoice("tenant-B", "alice")));

        System.out.println("Other owner denied: "
                + !canRead(subject, new Invoice("tenant-A", "bob")));

        login.logout();
        System.out.println("Principals removed on logout: "
                + subject.getPrincipals(UserPrincipal.class).isEmpty());

        char[] badSecret = "wrong".toCharArray();
        LoginContext badLogin = new LoginContext(
            "demo", null,
            credentials("alice", badSecret), config());

        boolean rejected = false;
        try {
            badLogin.login();
        } catch (LoginException expected) {
            rejected = true;
        } finally {
            Arrays.fill(badSecret, '\0');
        }
        System.out.println("Wrong password rejected: " + rejected);
    }
}
~~~

Chương trình dùng **record** và pattern matching **instanceof**, đều là tính năng chuẩn trong **Java 17**. Class **DemoLoginModule** là lớp public static nằm trong class public bên ngoài, có constructor public không đối số mặc định, cho phép LoginContext khởi tạo module bằng tên class được Configuration cung cấp.

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 JaasDecisionDemo.java
java JaasDecisionDemo
~~~

Stdout dự kiến:

~~~text
JAAS authenticated: true
Own invoice allowed: true
Cross-tenant denied: true
Other owner denied: true
Principals removed on logout: true
Wrong password rejected: true
~~~

**Giải thích từng bước:**

1. **JAAS authenticated: true:** LoginModule nhận callback, xác minh username/password demo, commit UserPrincipal("alice") vào Subject; LoginContext.login() trả về bình thường.
2. **Own invoice allowed: true:** policy canRead nhìn entitlement server-side Alice có role INVOICE_READER, membership tenant-A và owner Alice, nên cho phép.
3. **Cross-tenant denied: true:** Alice vẫn đăng nhập nhưng invoice thuộc tenant-B, không đạt điều kiện membership.
4. **Other owner denied: true:** Invoice thuộc tenant-A nhưng owner Bob, còn chính sách ví dụ yêu cầu actor là owner.
5. **Principals removed on logout: true:** LoginModule.logout() bỏ UserPrincipal mà module đã thêm.
6. **Wrong password rejected: true:** một LoginContext khác được đưa mật khẩu sai, login() thất bại và không tạo authenticated context hợp lệ.

**Tất cả kiểm tra quyền trong ví dụ nằm ở canRead(...), không phải LoginModule.login().** Việc tách hàm minh họa ranh giới trách nhiệm: module xác minh “Alice là ai”, policy service quyết định “Alice có được đọc object này không”.

### 6.2 Vì sao demo dùng một Map directory thay vì Principal role?

Map **directory** đại diện database/identity directory đáng tin của ứng dụng. Dù một LoginModule thực có thể thêm **RolePrincipal** từ nguồn tin, ví dụ muốn minh họa rằng **authorization không chỉ nhìn một claim hoặc đã login**.

Trong production, directory cần:

- Cập nhật role, trạng thái membership và quyền được ủy quyền từ backend quản trị.
- Kiểm tra tenant/project scope, resource owner, ACL và explicit deny.
- Có chính sách cache/TTL/invalidation để hạn chế quyền đã thu hồi vẫn được dùng.
- Phân tách nguồn role đáng tin với dữ liệu user đưa vào form/header/URL.
- Không chấp nhận người dùng tự sửa Map/policy hoặc tạo Subject tùy ý để bỏ qua LoginContext.

### 6.3 Những gì demo chưa làm được

Ví dụ được cố tình giới hạn:

- Không có user DB, password hash, salt, MFA, session cookie, JWT hoặc OIDC.
- Mật khẩu đào tạo **test-only** hardcode, không an toàn cho production.
- AuthZ policy cố định, không load từ database thực, không có TTL/cache/revoke.
- Invoice chỉ là record gồm tenant và owner; không có ID, status, ACL, amount.
- Không làm HTTP server, không thực hiện input validation, CSRF/XSS/CORS hay audit storage.
- Không giải quyết concurrency hoặc TOCTOU khi quyền/object thay đổi giữa kiểm tra và ghi DB.
- Subject trong demo được tạo qua đường đăng nhập do ứng dụng tin. **Bản thân Java Subject không phải một security boundary nếu mã không đáng tin có thể tự tạo/chỉnh nó**.
- **LoginContext.logout() không hủy mọi API token, session cookie hoặc quyết định quyền đã cache** ở một lớp khác. Quản lý session/revocation là trách nhiệm bổ sung của ứng dụng.

Không lấy ví dụ làm “template đăng nhập production” bằng cách thay chuỗi password thật vào source; hãy dùng cơ chế danh tính được duy trì/kiểm định và framework security phù hợp.

## 7. Đưa AuthN/AuthZ vào request pipeline

Một pipeline web/API lý tưởng phân tách trách nhiệm rõ ràng:

~~~text
Client Request
      |
[Transport / TLS / input-size and syntax validation]
      |
[Authentication Filter]
  validate session / JWT / mTLS / JAAS-backed mechanism
      |
      +--> no valid identity -> reject or public policy
      |
[Trusted Authentication Context]
  actorId, authMethod, tenant memberships, session metadata
      |
[Authorization Decision]
  actor + action + resource + context + current policy
      |
      +--> DENY --> reject; no read/write side effect
      |
[Business Service]
  enforce resource-level policy close to data operation
      |
[Repository / DB]
  tenant-scoped query + transaction/row-level guard
      |
[Response]
  expose only fields within approved scope
~~~

**Không tin rằng mọi API controller đều sẽ nhớ kiểm tra quyền**. Hãy đặt policy enforcement ở lớp dùng chung (method boundary/service/repository) và có integration tests để phát hiện endpoint nào bỏ sót kiểm tra.

Tuy nhiên, một security filter chỉ có thể kiểm tra “người gọi đăng nhập hợp lệ” **không thể tự quyết định quyền với object chưa được load**. Nhiều ứng dụng cần kiểm tra coarse-grained ở route và **fine-grained sau khi biết resource**.

### 7.1 AuthN context phải đúng cho từng request

Với thread pool, async tasks, messaging hoặc reactive pipelines, cần tránh **principal của request A bị dùng nhầm cho request B**. Dù framework có thread-local security context, không được mặc định nó được sao chép đúng qua CompletableFuture, executor, callback hoặc message consumer.

An toàn hơn:

- Truyền authentication context **đã được kiểm tra** vào nơi cần sử dụng theo cơ chế được framework hỗ trợ.
- Ở consumer của queue, xác thực service/producer hoặc xác minh message provenance trước khi nhận actor context.
- Không dùng biến static global currentUserId.
- Dọn context khi request kết thúc và kiểm thử cross-request isolation.
- Không tin userId/tenantId được gửi qua job payload nếu chưa có ràng buộc/quyền phù hợp.

### 7.2 401, 403 và 404

Đối với HTTP API, các mã thường dùng:

- **401 Unauthorized**: request thiếu hoặc chưa có chứng cứ xác thực hợp lệ; có thể kèm challenge WWW-Authenticate theo giao thức.
- **403 Forbidden**: server đã hiểu request, identity có thể hợp lệ, nhưng policy **không cho phép** action/resource.
- **404 Not Found**: có thể được dùng theo chính sách để **không tiết lộ sự tồn tại** của tài nguyên mà actor không có quyền biết.

Việc chọn 403 hay 404 nhằm tránh object enumeration cần **nhất quán**, nhưng không thay thế kiểm tra quyền thật. Không trả nội dung nhạy cảm rồi mới đổi HTTP status thành 403; side effect đã xảy ra.

Với API JSON, đừng trả chi tiết private policy, danh sách role bị thiếu của người khác hoặc tên tài nguyên không được phép trong thông báo lỗi. Ghi reason code nội bộ ở mức kiểm soát, không tiết lộ qua response.

## 8. Xác thực thành công nhưng phiên hoặc quyền đã thay đổi

### 8.1 Logout không đồng nghĩa thu hồi tất cả phiên ở mọi hệ thống

**LoginContext.logout()** gọi logout các LoginModule và dọn các Principal/credential mà module quản lý. Nó **không tự vô hiệu** một access token JWT đã phát hành, không xóa server-side session store bên ngoài, không gửi thông báo thu hồi role tới các service khác và không xóa authz cache một cách kỳ diệu.

Hệ thống có thể cần:

- Session invalidation và cookie deletion đúng.
- Token expiry/refresh rotation/revocation phù hợp threat model.
- Cập nhật quyền theo nguồn tin sau khi role/membership bị thu hồi.
- Audit user/session logout và xử lý multi-device.
- CSRF protection đối với session cookie khi tương ứng.
- Xử lý các background jobs đang giữ principal/entitlements đã cũ.

### 8.2 Quyền có thể bị thu hồi giữa hai request

~~~text
10:00 Alice login thành công.
10:01 Alice được quyền invoice:read tenant-A.
10:05 Admin thu hồi quyền invoice:read của Alice.
10:06 Alice gửi request mới đọc invoice.

AuthN có thể vẫn VALID (session còn).
AuthZ phải đọc quyết định theo quyền hiện hành/policy cache hợp lệ:
       DENY.
~~~

Nếu toàn hệ thống chỉ đọc role tại lúc login rồi cache vô thời hạn, quyền đã thu hồi có thể tiếp tục được dùng. Thiết kế freshness phù hợp với sensitivity: cache ngắn hạn, version/policy epoch, event invalidation hoặc kiểm tra nguồn authoritative cho action nhạy cảm.

**Không đồng nhất expiry của chứng cứ xác thực với expiry của entitlement**. Hai thời điểm có thể khác nhau.

### 8.3 Token chứa role không bảo đảm role còn hiệu lực mãi

JWT hoặc token ký có thể mang claim roles/permissions; sau khi verify, chúng là claim **được issuer công bố tại thời điểm phát hành**, không chắc phản ánh mọi thay đổi kể từ đó. Phải xét:

- Token lifetime và rotation/revocation.
- Audience, issuer, signature/MAC và thuật toán được phép.
- Tenant scope và resource scope.
- Có cần kiểm tra entitlement động cho dữ liệu nhạy cảm không?
- Giao thức nào cho phép service tin upstream identity context?

**Không đọc payload JWT bằng Base64 decoder rồi tin claim mà không xác minh chữ ký, issuer/audience/expiry**. Base64 chỉ là cách biểu diễn, không xác thực.

## 9. Legacy JAAS authorization và giới hạn trên Java hiện đại

### 9.1 JAAS từng hỗ trợ principal-based access control

Tài liệu JAAS Authorization của Java SE 17 mô tả cách sử dụng **Subject/Principal** cùng **java.security.Policy**, **AccessControlContext**, **Subject.doAs** và **Security Manager** để kiểm tra các permission trong sandbox Java truyền thống.

Ý tưởng lịch sử:

~~~text
Authenticated Subject
        |
   Subject.doAs(subject, action)
        |
AccessControlContext gắn Subject/Principals
        |
Security Manager + Policy quyết định Permission
        |
Java runtime có thể chặn thao tác được bảo vệ
~~~

Đây là mô hình **truy cập API/tài nguyên Java theo permission policy** thuộc kiến trúc Security Manager, **không tự tương đương** với policy kiểu “Alice được xóa invoice nào của tenant nào”.

### 9.2 Java 17 và JDK 24+ cần phân biệt

- **Java 17:** Security Manager bị **deprecated for removal** theo JEP 411. Một số API JAAS authorization truyền thống vẫn hiện diện và các tutorial cũ vẫn mô tả chúng.
- **JDK 18+:** nhiều API phụ thuộc AccessControlContext/Security Manager như Subject.doAs bị đánh dấu deprecated for removal, có API chạy với Subject khác trong đời sau.
- **JDK 24+:** theo **JEP 486**, Security Manager **bị vô hiệu hóa vĩnh viễn**; không thể bật lại bằng property startup hoặc cài custom SecurityManager như trước. Các đường kiểm tra quyền dựa trên Security Manager không còn là nền tảng đáng tin để bảo vệ tài nguyên.
- **JAAS authentication APIs không vì vậy mà đồng loạt biến mất**. LoginContext, LoginModule và Subject vẫn có vai trò với các công nghệ cần cơ chế xác thực pluggable, tùy JDK/provider.

Vì vậy **không thiết kế một hệ thống phân quyền REST/API mới chỉ dựa trên Subject.doAs + java.policy với kỳ vọng JDK tương lai tự ngăn dữ liệu cross-tenant**. Thay vào đó, làm **authorization check tường minh ở service/repository/policy layer**, với enforcement có test và quan sát được.

### 9.3 Subject.doAs không tự thực hiện requirePermission

Ngay cả trong code Java cũ:

~~~java
// Minh họa ý tưởng legacy; KHÔNG phải code khuyến nghị
// cho cơ chế phân quyền API mới trên JDK 24+.
Subject.doAs(subject, (java.security.PrivilegedAction<Void>) () -> {
    readSomeResource();
    return null;
});
~~~

Code trên **không tự chứa điều kiện kiểm tra quyền invoice/tenant**. Tác dụng permission-based còn phụ thuộc policy, Security Manager và API tạo ra permission checks đúng. Không được suy diễn:

~~~text
"Chạy dưới Subject của Alice"
           =>
"Alice tự động không xem được invoice của Bob"
~~~

Đó là suy luận **sai** đối với authorization nghiệp vụ nếu bạn không thực hiện riêng rule kiểm tra object.

### 9.4 Có nên dùng Principal như role không?

Một framework có thể xây **RolePrincipal** hoặc **GroupPrincipal**, nhưng cần rõ:

- Module nào có thẩm quyền khẳng định role?
- Role có ràng buộc tenant và expiry không?
- Có quyền truy cập object riêng hay không?
- Chỗ nào của code thật sự thực hiện **require** quyền?
- Khi Principal bị bỏ đi/đổi, session/policy cache có còn giữ quyền cũ không?

**Principal là dữ liệu identity/security context, không tự là policy enforcement point.** Không dựa vào class name hoặc getName() tùy ý như một security check hoàn chỉnh.

## 10. Bảo vệ tài nguyên ở lớp service và DB

### 10.1 Check gần nơi thực hiện hành động

Ví dụ dịch vụ chuyển tiền:

~~~text
POST /transfers
   -> authn hợp lệ
   -> authz account.transfer trên account nguồn
   -> xác minh owner/membership từ DB
   -> kiểm tra trạng thái account, hạn mức, MFA
   -> trong transaction xác nhận điều kiện vẫn đúng
   -> ghi transfer & audit
~~~

Không chỉ check endpoint là “có role TRANSFER_USER” rồi cho caller truyền **fromAccountId** của bất kỳ người nào. Nhiều lỗi nghiêm trọng bắt nguồn từ **IDOR trong thao tác ghi**, chứ không chỉ đọc.

### 10.2 TOCTOU: Time of Check vs Time of Use

Giả sử service:

1. Kiểm tra Alice là owner invoice.
2. Một request khác chuyển owner hoặc thu hồi membership.
3. Service vẫn UPDATE invoice vì đã check xong.

Nếu action cần bảo đảm nhất quán, cách tiếp cận có thể là:

- Thực hiện policy check và UPDATE trong **cùng transaction** với isolation phù hợp.
- Gắn điều kiện tenant/owner/status vào **UPDATE ... WHERE ...**; kiểm tra affected row count.
- Dùng optimistic locking/version để tránh thay đổi state ngoài dự đoán.
- Đọc lại entitlement hoặc policy epoch tại điểm commit khi cần.
- Không dùng cached role/owner quá lâu cho thao tác quan trọng.

Cách chính xác phụ thuộc DB và mô hình cạnh tranh; điều cốt lõi là **không để kết quả kiểm tra quyền cũ bị tách rời khỏi thao tác bảo vệ**.

### 10.3 Field-level authorization

Một actor được xem Invoice không nhất thiết được xem mọi trường:

~~~text
Invoice:
  id, date, status, amount            -> reader thông thường
  customerPersonalId, bankAccount     -> cần permission nhạy cảm
  internalRiskNotes, fraudFlags       -> compliance only
~~~

Sau object-level allow, response serialization có thể phải **lọc field** theo policy. Đừng trả toàn bộ object entity từ ORM rồi hy vọng front-end ẩn trường bằng CSS.

### 10.4 Multi-tenant data access

Trong hệ thống SaaS:

- Mọi read/list/update/delete phải thực thi tenant boundary.
- Tenant header/subdomain/URL chỉ xác định **tenant được yêu cầu**, không chứng minh membership.
- Dữ liệu policy/role có thể phụ thuộc tenant.
- Background jobs/scheduled workflows cũng cần actor/service authorization, không được bypass vì “nội bộ”.
- DB row-level security (RLS), tenant-scoped repository và API authorization có thể hỗ trợ nhau; không coi một lớp là lý do bỏ hết lớp khác.

## 11. Logging và lỗi phải thể hiện đúng sự khác biệt

### 11.1 Log authn và authz riêng

Các sự kiện gợi ý:

~~~text
AUTHN_SUCCESS user=alice mechanism=JAAS sessionRef=...
AUTHN_FAILURE reasonCode=INVALID_CREDENTIALS ...
AUTHZ_ALLOW actor=alice action=invoice.read resource=127 tenant=A
AUTHZ_DENY  actor=alice action=invoice.read resource=127 reasonCode=TENANT_SCOPE
AUTHZ_DENY  actor=alice action=invoice.update reasonCode=MISSING_PERMISSION
~~~

Đây là **minh họa log structure**, không phải dữ liệu thật và không phải định dạng bắt buộc. Ở production, tránh ghi password, access token, private credentials, raw Subject, session secret, PII nhạy cảm, hay giá trị resource mà caller không có quyền biết. Quy định correlation ID, thời hạn lưu, phân quyền log và phát hiện xâm nhập.

**Không phân biệt cụ thể “username không tồn tại” và “password sai” trong phản hồi đăng nhập công khai** nếu việc đó giúp user enumeration. Có thể lưu reason code nội bộ thích hợp.

### 11.2 Authz denial không phải login failure

Nếu Alice đăng nhập hợp lệ mà bị chặn invoice:delete, không nên tự động báo **“invalid password”** hoặc logout người dùng chỉ vì action không được phép. Xử lý đúng là **authorization denied** cho action đó, trừ khi có dấu hiệu session compromise hoặc chính sách rủi ro yêu cầu bước khác.

Phân biệt giúp support, audit và test không bị sai tín hiệu.

## 12. Test matrix thiết yếu

| Test case | AuthN | AuthZ | Kết quả mong đợi |
| --- | --- | --- | --- |
| Không có cookie/token và endpoint yêu cầu login | FAIL | Không cấp quyền | 401/challenge theo giao thức |
| Login sai password demo | FAIL | Không tạo actor tin cậy | Không được truy cập protected resource |
| Login đúng với Alice | PASS | Chưa được quyết định cho object | Có authenticated Subject/context |
| Alice đọc invoice tenant-A do Alice sở hữu | PASS | ALLOW | Đọc được field đã cấp quyền |
| Alice đọc invoice tenant-B | PASS | DENY | Không trả dữ liệu |
| Alice đọc invoice của Bob cùng tenant-A | PASS | DENY theo policy ví dụ | Không trả dữ liệu |
| Alice không có role editor nhưng gọi invoice:update | PASS | DENY | Không cập nhật |
| Alice có role reader nhưng chưa được cấp MFA step-up cần thiết | PASS | DENY hoặc yêu cầu step-up | Không tiến hành hành động nhạy cảm |
| Alice có session còn hạn nhưng role vừa bị thu hồi | PASS | DENY sau khi policy cập nhật có hiệu lực | Quyền thay đổi không bị bỏ qua |
| Client gửi X-User-Id: admin mà không có trust boundary | FAIL | Không tin header | Không mạo danh |
| Caller tự tạo Subject/Principal không qua login tin cậy | Không chứng minh identity | Không chấp nhận | Không nâng quyền |
| Object đổi owner giữa check và write | PASS | Phải được kiểm tra nhất quán | Không ghi nếu điều kiện không còn đúng |
| User được đọc invoice nhưng không được xem bankAccount | PASS | Object ALLOW, field DENY | Response không rò field |
| Service/queue consumer không có actor context hợp lệ | FAIL hoặc theo service identity policy | DENY nếu policy đòi caller | Không chạy như admin mặc định |
| Logout JAAS, nhưng session store ngoài còn tồn tại | Trạng thái tùy kiến trúc | Revoke session đúng | Không đánh đồng logout với invalidation toàn hệ |
| Một developer quên gọi authz ở endpoint mới | PASS | Không được phép bypass | Integration/security test thất bại |

### 12.1 Test trực tiếp quyền ở các tầng

Đừng chỉ test service function với boolean **isAdmin=true**. Nên kiểm tra:

- Policy đơn vị với actor/action/resource/context kết hợp hợp lệ và bất hợp lệ.
- Endpoint integration: login và quyền object, status/response/body, không có side effect khi deny.
- Database query/transaction: tenant scope, row count, race ownership/permission changes.
- Multi-tenant negative tests: cùng ID nhưng khác tenant, ID đoán được, list/export endpoints.
- Role revocation và expired sessions.
- Async message/job propagation và cross-request context leakage.
- Audit events không chứa secrets.
- Unauthorized field serialization và bulk endpoints.

Bài test quan trọng là **“đã xác thực nhưng không được quyền”**, vì nhiều API chỉ kiểm tra trường hợp “chưa đăng nhập” và bỏ sót object-level policy.

## 13. Những sai lầm phổ biến và cách sửa

1. **“LoginContext.login() không ném exception nên mọi API đều ALLOW.”** Sai; cần AuthZ trên từng action/resource.
2. **“Principal có tên admin nên là admin toàn hệ thống.”** Sai; phải kiểm tra nguồn role, scope và chính sách.
3. **“Có role USER thì được đọc mọi user.”** Sai; cần owner/ACL và tenant scoping.
4. **“Client gửi tenantId nên đó là tenant membership của họ.”** Sai; membership phải tới từ backend/token đáng tin.
5. **“Dùng Subject.doAs là JAAS sẽ tự chặn SQL SELECT sai tenant.”** Sai; cần policy enforcement cụ thể.
6. **“Security Manager còn hoạt động như cũ trên mọi JDK.”** Sai; JDK 24+ đã vô hiệu hóa vĩnh viễn.
7. **“Để admin bypass toàn bộ check cho nhanh.”** Rủi ro vượt quyền; cần phân biệt admin scope, break-glass và audit.
8. **“Role lúc login không bao giờ đổi.”** Sai; cần xử lý revoke/freshness.
9. **“Đã có JWT signed nên resource ID trong URL chắc hợp lệ.”** Sai; JWT identity không tự cấp quyền object.
10. **“Mọi trường invoice đều được trả vì người gọi được invoice.read.”** Sai; có thể cần field-level authorization.
11. **“Service nội bộ không cần authz.”** Sai; service principal cũng cần least privilege và tenant scope.
12. **“Lấy user từ thread-local bất kỳ async task nào cũng đúng.”** Sai; context propagation và dọn sạch phải được kiểm chứng.
13. **“Chỉ controller cần check, repository không cần tenant filter.”** Sai; nhiều code path có thể bypass controller.
14. **“Kiểm tra quyền xong thì ghi DB bao giờ cũng được.”** Sai trong race/TOCTOU; cần transaction và điều kiện tương ứng.
15. **“Logout LoginContext chắc chắn revoke JWT bên IdP.”** Sai; session/token lifecycle độc lập.
16. **“Các permission Java Policy trong tutorial cũ là thay thế tương đương cho API RBAC.”** Không; chức năng và trạng thái Security Manager đã khác.
17. **“401 và 403 dùng thay nhau được.”** Không; lỗi nhận dạng khác lỗi thiếu quyền, dù policy có thể trả 404 khi cần che resource.
18. **“Authz DENY nên in hết mọi claim, resource và policy cho client biết.”** Sai; chỉ log tối thiểu có phân quyền và tránh rò dữ liệu.
19. **“Endpoint list có tenant filter, endpoint export/bulk thì không cần.”** Sai; mọi đường đọc dữ liệu phải enforce scope.
20. **“LoginModule tự quyết định invoice nào Alice được đọc.”** Không phù hợp phân tách trách nhiệm; module xác thực, service policy đánh giá resource/action.

## 14. Câu hỏi ôn tập

**Câu 1.** Xác thực và phân quyền khác nhau thế nào?

**Trả lời:** Xác thực kiểm tra actor thực sự là ai; phân quyền kiểm tra actor có được làm action này trên resource cụ thể trong context hiện tại hay không.

**Câu 2.** LoginContext.login thành công có tự cấp quyền invoice.read không?

**Trả lời:** Không. Cần chính sách quyền từ nguồn đáng tin và kiểm tra resource-level scope.

**Câu 3.** LoginModule.commit có vai trò gì?

**Trả lời:** Khi login toàn cục đạt điều kiện, commit thêm Principal/credential liên quan vào Subject; login phase trước đó chỉ kiểm chứng credential và giữ trạng thái tạm.

**Câu 4.** Subject chứa UserPrincipal("alice") có chắc Alice đã login không?

**Trả lời:** Chỉ khi Subject đến từ authentication flow được server tin và chưa bị mạo danh/tamper; Subject object tự tạo không phải bằng chứng mật mã.

**Câu 5.** Vì sao role INVOICE_READER không đủ để xem invoice tenant-B?

**Trả lời:** Role có thể được cấp trong tenant-A; phải kiểm tra tenant/resource scope và owner/ACL khác.

**Câu 6.** RBAC khác ABAC ra sao?

**Trả lời:** RBAC dùng role→permission; ABAC kiểm tra thuộc tính actor/resource/action/context. Có thể kết hợp với ReBAC để bảo vệ object.

**Câu 7.** Tại sao code chỉ tìm invoice theo ID dễ bị BOLA/IDOR?

**Trả lời:** User đã đăng nhập có thể đoán ID của tài nguyên người khác; phải kiểm tra owner/tenant/ACL trước khi trả dữ liệu.

**Câu 8.** Có cần đánh giá quyền lại sau khi user vừa bị thu hồi role?

**Trả lời:** Có. Chính sách cache/revalidation phải phản ánh thu hồi trong thời gian thích hợp với độ nhạy của action.

**Câu 9.** Subject.doAs có tự bảo vệ mọi resource của ứng dụng không?

**Trả lời:** Không. Mô hình permission-based JAAS lịch sử phụ thuộc Policy/Security Manager và các permission checks; JDK 24 đã disable Security Manager. API nghiệp vụ cần authorization enforcement tường minh.

**Câu 10.** Logout JAAS có tự hủy một JWT đang có ngoài hệ thống không?

**Trả lời:** Không. Token/session manager và policy revocation là trách nhiệm hệ thống liên quan.

**Câu 11.** HTTP 401 và 403 thường được dùng thế nào?

**Trả lời:** 401 khi thiếu/chưa có authentication hợp lệ; 403 khi không được phép thực hiện thao tác. Có thể dùng 404 theo chính sách che sự tồn tại tài nguyên.

**Câu 12.** Vì sao authz cần actor/action/resource/context chứ không chỉ username?

**Trả lời:** Quyền phụ thuộc vào việc đang làm gì, object thuộc ai/tenant nào, trạng thái nào và điều kiện hiện tại; username đơn lẻ không quyết định được.

**Câu 13.** Một admin tenant-A có thể đọc dữ liệu tenant-B không?

**Trả lời:** Không mặc nhiên. Admin thường có scope; quyền global chỉ tồn tại khi policy đã cho phép rõ và có kiểm soát.

**Câu 14.** Authentication có thể chưa cần cho tài nguyên nào?

**Trả lời:** Với resource/action được chính sách xác định là public, anonymous actor có thể được cho phép; không có nghĩa mọi action trên cùng resource là public.

**Câu 15.** Vì sao cần test race giữa check quyền và update DB?

**Trả lời:** Resource owner/membership có thể đổi sau check; transaction, row condition, locking/version giúp giữ quyết định phù hợp thời điểm sử dụng.

## 15. Checklist triển khai

- [ ] Có nguồn **identity** đáng tin (JAAS LoginModule/IdP/session verifier).
- [ ] Tách AuthN context khỏi AuthZ decision; không tin userId/role từ header không bảo vệ.
- [ ] Có mapping rõ **actor → roles/permissions theo scope**, quản lý bằng backend có thẩm quyền.
- [ ] Định nghĩa policy cho từng **action/resource**, bao gồm public access có chủ đích.
- [ ] **Default DENY** khi thiếu actor/policy/tenant/ownership.
- [ ] Kiểm tra **object-level** và **field-level** khi cần, không chỉ route-level.
- [ ] Mọi read/list/update/delete/export/bulk endpoint đều enforce tenant scope.
- [ ] Dữ liệu quyền bị thu hồi có thời hạn hiệu lực, cache invalidation hoặc kiểm tra nguồn đáng tin.
- [ ] Check và hành động ghi quan trọng được bảo vệ khỏi TOCTOU.
- [ ] Async/thread/queue context không làm rò principal giữa người dùng.
- [ ] Xử lý session/token/JAAS logout đúng theo từng tầng.
- [ ] Logging phân biệt authn failure và authz denial, không ghi credentials/Subject nhạy cảm.
- [ ] Không dựa vào **SecurityManager/Subject.doAs** như bộ máy RBAC mới cho REST API.
- [ ] Có test **đã login nhưng bị từ chối**, cross-tenant, missing role, impersonation, revoke, field leak.
- [ ] Triển khai least privilege ở service, DB, network và secret store.

## 16. Tài liệu chính thức để tra cứu

- [Oracle Java SE 17 — JAAS Authentication](https://docs.oracle.com/en/java/javase/17/security/jaas-authentication.html): giải thích pluggable authentication, LoginContext, Subject, Principal.
- [Oracle Java SE 17 — JAAS Authentication Tutorial](https://docs.oracle.com/en/java/javase/17/security/jaas-authentication-tutorial.html): luồng login với JAAS.
- [Oracle Java SE 17 — JAAS LoginModule Developer's Guide](https://docs.oracle.com/en/java/javase/17/security/java-authentication-and-authorization-service-jaas-loginmodule-developers-guide1.html): các pha login/commit/abort/logout và CallbackHandler.
- [Oracle Java SE 17 — JAAS Authorization](https://docs.oracle.com/en/java/javase/17/security/jaas-authorization.html): authorization legacy dựa trên Principal và Java permission policy.
- [Oracle Java SE 17 — LoginContext API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/LoginContext.html): constructor, login, getSubject, logout.
- [Oracle Java SE 17 — Configuration API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/Configuration.html): các flag REQUIRED/REQUISITE/SUFFICIENT/OPTIONAL.
- [Oracle Java SE 17 — Subject API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/Subject.html): Principal và credential sets.
- [OpenJDK JEP 411](https://openjdk.org/jeps/411): deprecate Security Manager trong Java 17.
- [OpenJDK JEP 486](https://openjdk.org/jeps/486): permanently disable Security Manager từ JDK 24.
- [Oracle JDK 24 — Security Manager Permanently Disabled](https://docs.oracle.com/en/java/javase/24/security/security-manager-is-permanently-disabled.html): tác động tới các API permission/security legacy.

## Kết luận

**Xác thực danh tính là điểm khởi đầu, không phải giấy phép toàn quyền.** JAAS có thể xác thực người dùng/service qua LoginContext và LoginModule, đặt Principal/credential đáng tin vào Subject. Nhưng ứng dụng vẫn phải đánh giá **actor, action, resource và context** theo policy rõ ràng, kiểm tra scope/tenant/owner/ACL cùng thời điểm sử dụng và mặc định từ chối khi điều kiện không đủ.

**JAAS authorization dựa trên Java Security Manager là một cơ chế lịch sử có giới hạn lớn trên JDK hiện đại**, không thay thế cho phân quyền nghiệp vụ. Với Java 17+ và các service có REST API, thực hiện policy enforcement tường minh, kiểm thử các tình huống người dùng đã login nhưng **không đủ quyền**, và quản lý session/role revocation thay vì chỉ kiểm tra LoginContext.login().


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jaas-authentication-002">17.2 Subject, Principal và public hoặc private credentials</a>

<details>
<summary>Xem chi tiết</summary>

# 17.2 Subject, Principal và public hoặc private credentials

Trong **JAAS (Java Authentication and Authorization Service)**, kết quả xác thực không nhất thiết được mô tả bằng một chuỗi username hoặc boolean isLoggedIn. JAAS gom các **danh tính**, thuộc tính định danh và **thông tin xác thực** liên quan vào một đối tượng **javax.security.auth.Subject**. Một Subject có thể chứa **nhiều Principal**, nhiều **public credentials** và nhiều **private credentials**. Hiểu đúng ba tập hợp này là nền tảng cho LoginContext, LoginModule, Kerberos/GSS-API và code Java dùng khóa hoặc chứng thực theo chủ thể.

Bài học tập trung vào **cấu trúc Subject và ý nghĩa Principal/public/private credentials**, đồng thời giải thích lifecycle an toàn. Hai chương trình Java 17+ bên dưới kiểm tra các API, từ getPrincipals(Class) đến setReadOnly và Destroyable.destroy, cùng một luồng JAAS login → commit → logout tối giản.

## 1. Subject là ai, Principal biểu diễn điều gì?

Theo JAAS, **Subject** đại diện cho **một thực thể** như người dùng, service hoặc process; Subject có thể có nhiều danh tính trong các hệ thống hoặc theo các vai trò khác nhau. Một **Principal** là một đối tượng triển khai java.security.Principal, đại diện cho **một danh tính hoặc khía cạnh danh tính**.

~~~text
Subject (thực thể sau xác thực)
  |
  +-- Set<Principal>
  |      UserId("alice")
  |      Role("auditor")
  |      RealmIdentity("alice@EXAMPLE.REALM")
  |
  +-- Set<Object> publicCredentials
  |      Chứng chỉ public X.509, thông tin có thể chia sẻ
  |
  +-- Set<Object> privateCredentials
         Kerberos credential nhạy cảm, secret key,
         proof of possession hoặc credential bí mật
~~~

Các lớp UserId, Role, RealmIdentity trong sơ đồ là **tên minh họa**, không phải lớp Java chuẩn. Concrete types có sẵn thường tùy JAAS LoginModule/provider, ví dụ KerberosPrincipal hoặc các principal của môi trường đăng nhập. Ví dụ runnable tự định nghĩa Java records UserId và Role.

**Các nguyên tắc chính:**

- Subject đại diện cho **chủ thể**, không phải chỉ một username.
- Principal mô tả **danh tính** thuộc Subject, không phải password, session token hoặc bản thân private key.
- Một Subject **có thể có nhiều Principal**: người dùng, role, group, realm, dịch vụ hoặc các định danh tương ứng.
- Subject **không tự xác thực** một chuỗi Principal được thêm thủ công. Code có quyền sửa Subject có thể tạo Principal; độ tin cậy đến từ **quá trình authentication**.
- Subject không phải permission engine độc lập, không thay thế RBAC/ABAC và không tự phát hành JWT hay HTTP session.

### 1.1. Principal interface nhỏ nhưng rất quan trọng

~~~java
package java.security;

public interface Principal {
    String getName();
}
~~~

Đây là hình dạng API rút gọn để giải thích, không phải toàn bộ source interface của JDK. getName() trả **tên đại diện danh tính**. Concrete implementations cần có **equals và hashCode** phù hợp, vì Principal được lưu trong **Set**. Hai object cùng định danh cần được so sánh ổn định theo chính sách của type đó; Java 17 record có equality dựa trên các thành phần record, phù hợp cho Principal minh họa.

~~~java
public record UserId(String name)
        implements java.security.Principal, java.io.Serializable {
    @Override
    public String getName() {
        return name;
    }
}
~~~

Mảnh record trên giúp hiểu interface; chương trình thực hành có class bao quanh và kiểm thử. Với production, cần xác định **issuer/realm/tenant** trong identity nếu cùng username xuất hiện ở nhiều hệ thống; không so sánh chỉ dựa trên tên hiển thị không duy nhất.

### 1.2. Principal về người dùng và role có giống nhau?

Không. UserId("alice") chỉ **người dùng cụ thể**, còn Role("admin") mô tả một **khía cạnh vai trò/nhóm** đã được gắn bởi hệ thống tin cậy. Nếu Java service thêm role từ HTTP header không tin cậy, nó không được biến thành quyền quản trị chỉ nhờ nằm trong Subject.

Phân biệt:

- **Identity Principal:** username, account ID, service account ID hoặc Kerberos principal.
- **Group/Role Principal:** nhóm/vai trò thuộc danh tính đã xác minh.
- **Realm/Tenant Principal:** ranh giới định danh, giúp tránh nhầm user trùng tên ở nhiều tenant.

**Role Principal không tự đưa ra quyết định authorization.** Code phải kiểm tra **chính sách quyền riêng**, resource, action, tenant và trạng thái yêu cầu.

## 2. Ba bộ dữ liệu trong Subject

Java SE 17 Subject duy trì ba Set:

| Tập hợp | Method Java | Kiểu trả về | Ý nghĩa |
| --- | --- | --- | --- |
| **Principals** | getPrincipals() | Set<Principal> | Danh tính/khía cạnh danh tính của Subject. |
| **Public credentials** | getPublicCredentials() | Set<Object> | Dữ liệu xác thực được thiết kế để chia sẻ theo mô hình ứng dụng, ví dụ certificate công khai. |
| **Private credentials** | getPrivateCredentials() | Set<Object> | Dữ liệu xác thực nhạy cảm cần bảo vệ riêng, như secret key hoặc token. |

Mỗi tập là **Set**, không phải List:

- Không có thứ tự có ý nghĩa ổn định để lấy “principal đầu tiên” hoặc “credential số 0”.
- Không giữ duplicate theo equals/hashCode của phần tử.
- Một tên Principal có thể trùng ở hai concrete classes mà vẫn là hai object khác nhau.
- Credential chứa Object tùy loại; ứng dụng cần biết **class được chấp nhận** và ràng buộc trust.

### 2.1. Public credential là gì?

Tài liệu JAAS nêu ví dụ **public-key certificates** hoặc một số vé dịch vụ được thiết kế để chia sẻ. Tuy nhiên chữ **public trong tên collection không có nghĩa bất kỳ ai cũng được đọc/log/chuyển ra ngoài**. An toàn công bố tùy loại credential và policy.

Ví dụ về mặt ý tưởng:

- X509Certificate là certificate công khai; private key **không** nằm trong certificate.
- Metadata của certificate/realm đã xác minh có thể hỗ trợ lựa chọn public key.
- Một public credential trong demo là PublicProfile("training-cert-label"): **chỉ là nhãn**, không phải certificate thật.

**Bearer token vẫn là bí mật** dù endpoint khác có thể “nhìn thấy” token trong giao thức. Không đặt API key/session secret vào publicCredentials chỉ vì bạn dự định truyền nó giữa services. Phân loại dựa trên khả năng **dùng lại để giả mạo** và rủi ro lộ.

### 2.2. Private credential là gì?

Private credential là thông tin cần bảo vệ bổ sung, ví dụ:

- Secret key/HMAC key có thể dùng để chứng minh danh tính hoặc thực hiện nghiệp vụ được cấp phép.
- Kerberos credential/ticket nhạy cảm theo profile, có thể thực hiện operation dưới danh tính tương ứng.
- Token/proof of possession hoặc private-key handle cần giới hạn quyền.
- Các loại credential hỗ trợ **javax.security.auth.Destroyable** hoặc **Refreshable** để quản lý vòng đời.

Private credential **không phải password hash trong database** và không nhất thiết là một PrivateKey. Nó có thể là bất kỳ object login provider dùng để chứng minh quyền. Chọn **concrete credential class** và quản lý quyền truy cập phù hợp.

### 2.3. Public/private là phân loại trong JAAS, không phải mã hóa

**Thêm object vào getPrivateCredentials không tự mã hóa object đó**, không đặt khóa vào HSM, không tự xóa memory khi session hết hạn, cũng không tự phân quyền cho mọi code trong cùng process. Trong môi trường dùng **Security Manager** (mô hình lịch sử), Java kiểm tra PrivateCredentialPermission khi truy cập private credential. Nhưng Security Manager đã **deprecated for removal trong Java 17**; kiến trúc bảo mật mới không nên dựa duy nhất vào các kiểm tra này.

Để bảo vệ thực tế cần **ranh giới trust của ứng dụng, không chia sẻ object bừa bãi, IAM/KMS/HSM, tránh logging, lifecycle logout/expiry và authorization độc lập**.

## 3. Tạo Subject và thêm Principal/credentials

Trong Java 17:

~~~java
Subject subject = new Subject();
subject.getPrincipals().add(new UserId("alice"));
subject.getPrincipals().add(new Role("reader"));
subject.getPublicCredentials().add(new PublicProfile("training"));
subject.getPrivateCredentials().add(secretCredential);
~~~

Đây là **mảnh minh họa** cần các class UserId, Role, PublicProfile và biến secretCredential; chương trình hoàn chỉnh nằm phía dưới. Constructor new Subject() tạo ba tập rỗng.

Constructor khác có dạng:

~~~java
new Subject(
    false,
    Set.of(new UserId("alice")),
    Set.of(new PublicProfile("training")),
    Set.of(secretCredential)
);
~~~

Tham số boolean đầu là **readOnly**. Constructor sao chép các phần tử vào **Set nội bộ mới**, không giữ trực tiếp Set gốc; **nhưng không deep-copy object** bên trong. Constructor và các method từ chối null collections/null elements.

**Cần phân biệt:**

- new Subject() → writable, chưa chứng thực danh tính.
- new Subject(true, ...) → có dữ liệu ban đầu nhưng các Set không cho add/remove sau đó.
- new Subject(false, ...) → có dữ liệu ban đầu và cho phép sửa theo quyền/policy.
- LoginContext.login() + LoginModule.commit() → con đường JAAS thông dụng để **gắn dữ liệu đã xác thực**, thay vì tin Subject tự lắp.

### 3.1. Add/remove và duplicate Principal

~~~java
subject.getPrincipals().add(new UserId("alice")); // phần tử mới
subject.getPrincipals().add(new UserId("alice")); // không thêm duplicate
~~~

Với Java record một trường name, hai UserId cùng "alice" có equals/hashCode tương đương. Do Set, size chỉ tăng một lần. Điều đó **không ngăn một Subject có hai UserId khác nhau**; nếu nghiệp vụ yêu cầu chỉ một account ID, ứng dụng cần validate bất biến.

Không dùng Principal.getName() đơn thuần để kết luận Principal khác type hoặc realm đều là cùng danh tính. Cần namespace/issuer/tenant rõ ràng.

## 4. Live Set và bản sao lọc theo class

Một điểm dễ hiểu sai của Java 17 Subject API:

| Method | Tập trả về được backed bởi Subject? | Hệ quả |
| --- | --- | --- |
| getPrincipals() | **Có** | add/remove trên tập trả về sửa Principal set của Subject. |
| getPublicCredentials() | **Có** | add/remove trên tập trả về sửa public credential set. |
| getPrivateCredentials() | **Có** | add/remove trên tập trả về sửa private credential set. |
| getPrincipals(UserId.class) | **Không**, tập mới đã lọc | clear/add/remove trên tập lọc không sửa Subject. |
| getPublicCredentials(MyClass.class) | **Không**, tập mới đã lọc | Tập kết quả lọc không phải cách cập nhật Subject. |
| getPrivateCredentials(MyClass.class) | **Không**, tập mới đã lọc | Không deep-copy secret object, và vẫn chịu kiểm tra quyền liên quan. |

**Tập typed mới** không tạo **bản deep copy của credential objects**: nó giữ **tham chiếu đến cùng objects**. Sửa/hủy private credential mutable có thể ảnh hưởng mọi nơi đang giữ tham chiếu, dù tập kết quả lọc không được backed bởi Subject.

### 4.1. Rủi ro nếu làm lộ live Set

Giả sử service trả thẳng subject.getPrincipals() cho plugin hoặc logic không đáng tin. Plugin có thể tự add Role("admin") khi Subject writable hoặc xóa role. Điều này làm hỏng identity data mà code phía sau có thể tin.

**Thiết kế tốt:** dùng facade chỉ đọc/DTO chỉ chứa thông tin an toàn; chỉ cho module xác thực/trust layer thêm/xóa Principal. Collections.unmodifiableSet(subject.getPrincipals()) **không phải snapshot immutable**: wrapper chặn sửa qua chính nó nhưng phản ánh thay đổi bên trong Subject. Muốn snapshot identity ổn định, tạo tập mới gồm những Principal bất biến hoặc DTO.

### 4.2. Truy xuất private credential và PrivateCredentialPermission

Trong Java 17 **nếu có Security Manager**:

- Sửa Principal/public/private sets có thể yêu cầu **AuthPermission** tương ứng.
- Lặp qua untyped getPrivateCredentials() có thể ném **SecurityException** nếu thiếu **PrivateCredentialPermission** cho một credential.
- getPrivateCredentials(SomeClass.class) yêu cầu quyền đối với các credentials phù hợp theo contract; nếu thiếu có thể ném SecurityException.

Các kiểm tra này phụ thuộc môi trường **có Security Manager** và policy. Chúng là chi tiết tương thích JAAS, **không thay thế least privilege, phân vùng process và authorization ở ứng dụng**.

## 5. Read-only Subject: khóa tập hợp, không khóa mọi object

Khi JAAS đã hoàn thành authentication và role/credentials không nên tiếp tục thay đổi, ứng dụng có thể gọi **subject.setReadOnly()**.

~~~java
subject.setReadOnly();
boolean locked = subject.isReadOnly();
~~~

Sau khi setReadOnly:

1. Không thể thêm/xóa Principal hoặc public/private credentials **qua các Set của Subject**; một thao tác sửa sẽ ném **IllegalStateException**.
2. Không có API chuyển một Subject read-only trở lại writable.
3. Những **credential objects đã có** không tự trở thành bất biến sâu. Một private credential có thể thay đổi nội dung hoặc được **Destroyable.destroy()** dù Subject read-only.
4. Object đã bị destroy vẫn **có thể còn nằm trong Set** vì destroy nội dung không tương đương remove khỏi Subject; trong Subject read-only, việc remove credential object có thể bị từ chối.
5. **Read-only không phải security boundary bảo vệ secrets**: code còn giữ tham chiếu secret object hoặc có khả năng sử dụng khóa vẫn cần kiểm soát riêng.

~~~text
Subject writable:
  + add/remove Principal
  + add/remove credentials
  + mutate/destroy credential object (theo contract của object)

subject.setReadOnly()

Subject read-only:
  - add/remove trong ba Subject sets -> IllegalStateException
  + destroy credential object (nếu class hỗ trợ)
  ? object có thể vẫn mutable nếu không bất biến
~~~

**Lưu ý khi dùng LoginContext.logout:** LoginModule thường cần remove chính những Principal/credentials mà nó đã thêm ở commit. Nếu bạn tự setReadOnly quá sớm, logout có thể **không gỡ được entry khỏi Set**; một implementation phù hợp có thể destroy được credentials nhưng không loại được object. Phải thiết kế rõ lifecycle: ai có quyền đóng phiên, khi nào làm read-only, và làm sao không biến dữ liệu phiên hết hạn thành đối tượng vẫn được ứng dụng tin tưởng.

### 5.1. Read-only khác immutable DTO

Một **DTO immutable** thường chỉ mang những field cần thiết cho nghiệp vụ, ví dụ userId, tenantId, roles, issuedAt. Nó không giữ private secret. **Subject.setReadOnly** chỉ cấm thay đổi tập hợp và vẫn chứa credential objects. Đừng đưa trực tiếp Subject vào HTTP response, Redis, JSON logger hoặc cache phân tán.

Nếu muốn xuất thông tin danh tính đã được phép, hãy **map từ Subject sang DTO an toàn theo allowlist**. Không serialize hoặc stringify toàn bộ Subject để tiết kiệm code.

## 6. Private credentials và Destroyable

**javax.security.auth.Destroyable** cung cấp hai method:

~~~java
void destroy() throws DestroyFailedException;
boolean isDestroyed();
~~~

Đây là cấu trúc giao diện minh họa, bỏ qua chi tiết về default methods. Destroyable **không bảo đảm tất cả implementations sẽ xóa bộ nhớ**, vì default destroy có thể ném **DestroyFailedException**; cần kiểm tra **implementation cụ thể**. Những object nhạy cảm có thể triển khai destroy để xóa char[]/byte[] hoặc giải phóng key handle.

**Một chiến lược quản lý credential cần:**
- Có **owner rõ ràng**: ai tạo, ai cấp, ai đóng/hủy.
- Hạn chế số bản sao, thời gian sống, nơi log hoặc truyền.
- Gọi destroy/logout/revoke khi session kết thúc theo contract của credential.
- Xử lý ngoại lệ destroy và không sử dụng credential sau khi bị hủy.
- Phân biệt **xóa reference khỏi Subject** với **xóa secret material**. Hai thao tác không tương đương.
- Không tự trông chờ JVM GC xóa ngay secret bytes; không cam kết rằng Arrays.fill đã xóa mọi bản sao trong framework/native heap.

### 6.1. Public credential vẫn cần quản lý vòng đời

Public-key certificate có thể được phép công bố nhưng vẫn có **hạn hiệu lực, key usage, issuer, revoked status** và policy. Một certificate public trong Subject không đồng nghĩa chain được xác minh tin cậy cho mọi nghiệp vụ. Ngược lại, nếu một loại "ticket" là bearer capability có thể dùng lại để đóng giả chủ thể, phải coi đó là **nhạy cảm** bất kể ví dụ JAAS tài liệu liệt kê loại vé có thể chia sẻ.

### 6.2. Không in Subject hoặc credentials bừa bãi

**Subject.toString(), Object.toString(), collection logging** có thể gọi toString của Principal hoặc credential và làm lộ thông tin. Cần:
- Tránh log toàn bộ Subject, nhất là private credentials.
- Với credential do bạn định nghĩa, override toString thành một **giá trị che giấu**.
- Audit bằng **account/tenant ID đã được phép, event type, trace ID, kết quả**; không in password, session secret, private key, token, Kerberos ticket.
- Không đưa credentials vào equals/hashCode dùng để cache hay log. Subject equality dựa trên các tập Principal/credentials; nó không phải stable account identity.

## 7. Java 17+ — chương trình 1: Subject, Principal và ba Set

Ví dụ độc lập dùng record UserId/Role, record PublicProfile và class SessionSecret triển khai Destroyable. SessionSecret **không phải credential thật**, chỉ minh họa cách hủy một char[] nội bộ. Chương trình cố ý thêm cùng một UserId hai lần để thấy Set khử duplicate, kiểm tra typed snapshot, live set và read-only.

Tạo file **SubjectCredentialDemo.java**:

~~~java
import java.io.Serializable;
import java.security.Principal;
import java.util.Arrays;
import java.util.Set;
import javax.security.auth.Destroyable;
import javax.security.auth.Subject;

public class SubjectCredentialDemo {
    public record UserId(String name) implements Principal, Serializable {
        @Override public String getName() { return name; }
    }
    public record Role(String name) implements Principal, Serializable {
        @Override public String getName() { return name; }
    }
    public record PublicProfile(String label) {}

    public static final class SessionSecret implements Destroyable {
        private char[] value;
        public SessionSecret(char[] input) { value = input.clone(); }
        @Override public synchronized void destroy() {
            if (value != null) {
                Arrays.fill(value, '\0');
                value = null;
            }
        }
        @Override public synchronized boolean isDestroyed() {
            return value == null;
        }
        @Override public String toString() {
            return "SessionSecret[REDACTED]";
        }
    }

    public static void main(String[] args) throws Exception {
        Subject subject = new Subject();
        subject.getPrincipals().add(new UserId("alice"));
        subject.getPrincipals().add(new UserId("alice"));
        subject.getPrincipals().add(new Role("reader"));
        subject.getPublicCredentials().add(new PublicProfile("training-cert-label"));

        char[] original = "fake-session-material".toCharArray();
        SessionSecret secret = new SessionSecret(original);
        Arrays.fill(original, '\0');
        subject.getPrivateCredentials().add(secret);

        System.out.println("Số Principal phân biệt: " + subject.getPrincipals().size());
        System.out.println("UserId: " + subject.getPrincipals(UserId.class).size());
        System.out.println("Public profile: " +
                subject.getPublicCredentials(PublicProfile.class).size());
        System.out.println("Private credential: " +
                subject.getPrivateCredentials(SessionSecret.class).size());

        Set<UserId> snapshot = subject.getPrincipals(UserId.class);
        snapshot.clear();
        System.out.println("Xóa snapshot không đổi Subject: " +
                (subject.getPrincipals(UserId.class).size() == 1));

        Set<Principal> live = subject.getPrincipals();
        live.add(new Role("auditor"));
        System.out.println("Live set thay đổi Subject: " +
                (subject.getPrincipals().size() == 3));

        subject.setReadOnly();
        boolean blocked = false;
        try {
            subject.getPrincipals().add(new Role("admin"));
        } catch (IllegalStateException expected) {
            blocked = true;
        }
        System.out.println("Read-only từ chối sửa set: " + blocked);
        secret.destroy();
        System.out.println("Vẫn hủy được private credential: " +
                secret.isDestroyed());
        System.out.println("Credential object còn trong set: " +
                (subject.getPrivateCredentials().size() == 1));
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 SubjectCredentialDemo.java
java SubjectCredentialDemo
~~~

Stdout kiểm thử:

~~~text
Số Principal phân biệt: 2
UserId: 1
Public profile: 1
Private credential: 1
Xóa snapshot không đổi Subject: true
Live set thay đổi Subject: true
Read-only từ chối sửa set: true
Vẫn hủy được private credential: true
Credential object còn trong set: true
~~~

### 7.1. Phân tích kết quả

- **Số Principal phân biệt: 2**: UserId("alice") bị thêm trùng, nhưng Set chỉ giữ một UserId và một Role("reader").
- **Public profile: 1**: nhãn training-cert-label là metadata minh họa, không chứa certificate/secret thực.
- **Private credential: 1**: một object SessionSecret nằm trong privateCredentials; không in char[] hoặc toString có thể lộ.
- **Xóa snapshot không đổi Subject: true**: getPrincipals(UserId.class) tạo Set mới, nên clear chỉ thay đổi tập lọc.
- **Live set thay đổi Subject: true**: Set<Principal> từ getPrincipals() được backed bởi Subject; thêm Role("auditor") làm tổng 3.
- **Read-only từ chối sửa set: true**: sau setReadOnly, không thêm được Role("admin").
- **Vẫn hủy được private credential: true**: SessionSecret.destroy xóa char[] nội bộ và cập nhật trạng thái.
- **Credential object còn trong set: true**: destroy khác remove; object đã hủy còn nằm trong Subject read-only.

**Giới hạn của bài test:** các Principal được thêm thủ công để kiểm tra collection semantics, **không chứng minh người dùng alice đã đăng nhập thật**. Chương trình không triển khai Kerberos, hệ thống IAM hay policy phân quyền. Chuỗi "fake-session-material" là dữ liệu thử, không phải credential production.

### 7.2. Vì sao cần Serializable trên Principal?

Java 17 Subject có hỗ trợ Serializable; theo Javadoc, **Principal trong Subject có thể được serialize**, còn **các credential sets không được serialize**. Vì interface java.security.Principal **không tự extends Serializable**, concrete Principal phải triển khai Serializable nếu bạn muốn serialize Subject. **Không sử dụng Subject serialization làm cách sao lưu phiên đăng nhập đầy đủ hoặc vận chuyển private credential**.

Chủ động hạn chế serialization của Subject vì nó có thể chứa **danh tính nhạy cảm và những Principal classes không đáng tin**. Ưu tiên DTO đã xác minh và schema có phiên bản.

### 7.3. Thông điệp read-only thực tế

**Subject.setReadOnly không xóa bí mật** và không tự động logout. Nếu yêu cầu chấm dứt phiên, cần gọi đúng **LoginContext.logout** hoặc cơ chế revoke/credential.destroy riêng, ở một thời điểm còn có thể sửa các Set nếu LoginModule cần remove. Một credential đã destroy không nên được tiếp tục sử dụng dù một tham chiếu object còn tồn tại.

## 8. LoginContext và LoginModule: Subject nhận Principal khi nào?

Một Subject được tạo bằng new Subject rồi tự thêm Role chỉ là object data. Trong JAAS **authentication có cấu trúc**, **LoginContext** điều phối một hoặc nhiều **LoginModule** theo **Configuration**. Thông thường, module xác minh bằng cơ chế của mình trong pha login, rồi **chỉ khi toàn bộ authentication thành công** mới gắn Principal và credentials vào Subject trong pha commit.

~~~text
Application tạo LoginContext(name, Subject/handler/config)
                    |
                    v
               LoginContext.login()
                    |
                    v
      LoginModule.initialize(Subject, CallbackHandler, ...)
                    |
                    v
      LoginModule.login() -- kiểm tra bí mật/identity
                    |
             đủ module thành công?
               /              \
             Có                Không
              |                  |
              v                  v
      LoginModule.commit()   LoginModule.abort()
      gắn Principal/cred     xóa state tạm, không gắn identity sai
              |
              v
    LoginContext.getSubject()
    identity/credential được xác thực
              |
              v
    LoginContext.logout()
    LoginModule.logout() gỡ và hủy chính những
    Principal/credentials mà module đã thêm
~~~

### 8.1. Các method của LoginModule

| Method | Thời điểm | Trách nhiệm |
| --- | --- | --- |
| **initialize** | LoginContext chuẩn bị module | Nhận Subject, CallbackHandler, sharedState, options. Chưa khẳng định login thành công. |
| **login** | Pha xác thực | Nhận/kiểm tra credentials, giữ trạng thái tạm; trả true/false hoặc ném LoginException theo contract. |
| **commit** | Toàn bộ login thành công theo module flags | Gắn **đúng Principal và credentials của module** vào Subject, hủy state xác thực không cần nữa. |
| **abort** | Authentication/commit không thành công | Gỡ/destroy state tạm hoặc dữ liệu đã commit dở; không để lại identity sai. |
| **logout** | Khi ứng dụng chủ động kết thúc JAAS session | Gỡ Principal và credentials **chính module đã tạo**, hủy secret thích hợp. |

Nếu có nhiều LoginModules trong cùng một LoginContext, **mỗi module chỉ nên quản lý phần tử mình đã thêm**. Xóa sạch toàn bộ Subject trong logout của một module có thể phá dữ liệu mà module khác hoặc ứng dụng đã xác thực độc lập.

### 8.2. Các module control flags

Trong **JAAS Configuration**, mỗi LoginModule có một flag:

- **REQUIRED**: phải thành công nhưng tiếp tục chạy các modules còn lại; nếu thất bại thì tổng thể login thất bại.
- **REQUISITE**: phải thành công; nếu thất bại thì kết thúc pha login sớm.
- **SUFFICIENT**: thành công có thể khiến hệ thống bỏ qua phần modules còn lại nếu không có REQUIRED/REQUISITE trước đó đã thất bại.
- **OPTIONAL**: có thể thành công hoặc thất bại; tổng thể vẫn tuân quy tắc về các modules còn lại.

Chi tiết điều phối nhiều modules và các corner cases nên tra Javadoc Configuration và JAAS LoginModule Developer's Guide. **Không tự giải nghĩa OPTIONAL thành “mọi user đều được đăng nhập”.** Trong mọi trường hợp, code nghiệp vụ chỉ được tin Subject sau khi **LoginContext.login() trả thành công**.

### 8.3. CallbackHandler và bí mật

**NameCallback** và **PasswordCallback** là các callback hay dùng cho demo password login; ngoài đời có thể dùng Kerberos, token, PKCS#11 hay provider-specific login. **PasswordCallback** nhận char[] và hỗ trợ clearPassword; caller và LoginModule nên dọn các char[] tạm. Không log password; không lưu nó trong Subject làm Principal. Module có thể thêm một private credential **sau xác thực** nếu cần, nhưng không nhất thiết phải lưu password gốc.

**Đừng coi LoginModule là password hasher**: việc xác thực password production cần KDF/verifier phù hợp, source identity đáng tin và rate limiting chống đoán online. Ví dụ bên dưới cố ý chỉ là **in-memory training module**, so sánh một password thử để minh họa lifecycle JAAS, tuyệt đối không dùng làm hệ thống đăng nhập thực.

## 9. Java 17+ — chương trình 2: LoginContext, commit và logout

Ví dụ tự cung cấp Configuration theo code (không cần sửa global JAAS login config), một DemoLoginModule và CallbackHandler. Khi username/password test đúng, module:
- Thêm **UserId Principal** đại diện danh tính đã xác thực.
- Thêm **PublicProfile** chỉ chứa nhãn công khai minh họa.
- Thêm **PrivateProof** bọc char[] giả, hỗ trợ **Destroyable**.

Khi logout, module xóa **chính ba phần tử nó sở hữu** và hủy proof. Khi sai password, login thất bại và không có authenticated Subject từ LoginContext mới. Chương trình không giữ password người dùng trong Subject.

Tạo file **JaasLoginSubjectDemo.java**:

~~~java
import java.io.IOException;
import java.io.Serializable;
import java.security.Principal;
import java.util.Arrays;
import java.util.Map;
import javax.security.auth.Destroyable;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

public class JaasLoginSubjectDemo {
    public record UserId(String name) implements Principal, Serializable {
        @Override public String getName() { return name; }
    }
    public record PublicProfile(String source) {}
    public static final class PrivateProof implements Destroyable {
        private char[] contents;
        public PrivateProof(char[] input) { contents = input.clone(); }
        @Override public synchronized void destroy() {
            if (contents != null) {
                Arrays.fill(contents, '\0');
                contents = null;
            }
        }
        @Override public synchronized boolean isDestroyed() {
            return contents == null;
        }
        @Override public String toString() {
            return "PrivateProof[REDACTED]";
        }
    }

    public static final class DemoLoginModule implements LoginModule {
        private Subject subject;
        private CallbackHandler handler;
        private boolean authenticated;
        private boolean committed;
        private String account;
        private UserId principal;
        private PublicProfile profile;
        private PrivateProof proof;

        @Override public void initialize(Subject subject,
                CallbackHandler callbackHandler, Map<String, ?> sharedState,
                Map<String, ?> options) {
            this.subject = subject;
            this.handler = callbackHandler;
        }

        @Override public boolean login() throws LoginException {
            NameCallback user = new NameCallback("User");
            PasswordCallback pass = new PasswordCallback("Password", false);
            char[] supplied = null;
            try {
                handler.handle(new Callback[] { user, pass });
                supplied = pass.getPassword();
                char[] expected = "training-pass".toCharArray();
                boolean valid;
                try {
                    valid = "alice".equals(user.getName())
                            && Arrays.equals(supplied, expected);
                } finally {
                    Arrays.fill(expected, '\0');
                }
                if (!valid) {
                    throw new FailedLoginException("Invalid credentials");
                }
                account = user.getName();
                authenticated = true;
                return true;
            } catch (IOException | UnsupportedCallbackException error) {
                throw new LoginException("Credential callback failed");
            } finally {
                if (supplied != null) Arrays.fill(supplied, '\0');
                pass.clearPassword();
            }
        }

        @Override public boolean commit() {
            if (!authenticated) return false;
            principal = new UserId(account);
            profile = new PublicProfile("training-directory");
            char[] material = "not-a-real-token".toCharArray();
            try {
                proof = new PrivateProof(material);
            } finally {
                Arrays.fill(material, '\0');
            }
            subject.getPrincipals().add(principal);
            subject.getPublicCredentials().add(profile);
            subject.getPrivateCredentials().add(proof);
            account = null;
            committed = true;
            return true;
        }

        private void cleanup() {
            if (subject != null) {
                if (principal != null) subject.getPrincipals().remove(principal);
                if (profile != null) subject.getPublicCredentials().remove(profile);
                if (proof != null) {
                    subject.getPrivateCredentials().remove(proof);
                    proof.destroy();
                }
            }
            principal = null;
            profile = null;
            proof = null;
            account = null;
            authenticated = false;
            committed = false;
        }

        @Override public boolean abort() {
            boolean hadState = authenticated || committed;
            cleanup();
            return hadState;
        }
        @Override public boolean logout() {
            boolean hadState = committed;
            cleanup();
            return hadState;
        }
    }

    private static Configuration configuration() {
        return new Configuration() {
            @Override public AppConfigurationEntry[] getAppConfigurationEntry(
                    String name) {
                if (!"Training".equals(name)) return null;
                return new AppConfigurationEntry[] {
                        new AppConfigurationEntry(
                                DemoLoginModule.class.getName(),
                                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                                Map.of())
                };
            }
        };
    }

    private static CallbackHandler callbacks(String userName, char[] password) {
        return entries -> {
            for (Callback entry : entries) {
                if (entry instanceof NameCallback name) {
                    name.setName(userName);
                } else if (entry instanceof PasswordCallback pass) {
                    pass.setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(entry);
                }
            }
        };
    }

    public static void main(String[] args) throws Exception {
        char[] accepted = "training-pass".toCharArray();
        try {
            LoginContext login = new LoginContext(
                    "Training", null, callbacks("alice", accepted),
                    configuration());
            login.login();
            Subject subject = login.getSubject();
            PrivateProof proof = subject.getPrivateCredentials(
                    PrivateProof.class).iterator().next();
            System.out.println("Login thành công: " + (subject != null));
            System.out.println("Principal alice: " +
                    subject.getPrincipals(UserId.class).iterator().next().getName());
            System.out.println("Public credentials: " +
                    subject.getPublicCredentials(PublicProfile.class).size());
            System.out.println("Private credentials: " +
                    subject.getPrivateCredentials(PrivateProof.class).size());
            login.logout();
            System.out.println("Logout xóa Principal: " +
                    subject.getPrincipals().isEmpty());
            System.out.println("Logout xóa credentials: " +
                    (subject.getPublicCredentials().isEmpty()
                            && subject.getPrivateCredentials().isEmpty()));
            System.out.println("Logout hủy private proof: " + proof.isDestroyed());
        } finally {
            Arrays.fill(accepted, '\0');
        }

        char[] rejected = "wrong".toCharArray();
        try {
            LoginContext bad = new LoginContext(
                    "Training", null, callbacks("alice", rejected),
                    configuration());
            boolean denied = false;
            try {
                bad.login();
            } catch (LoginException expected) {
                denied = true;
            }
            System.out.println("Sai mật khẩu bị từ chối: " + denied);
            System.out.println("Thất bại không có authenticated Subject: " +
                    (bad.getSubject() == null));
        } finally {
            Arrays.fill(rejected, '\0');
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 JaasLoginSubjectDemo.java
java JaasLoginSubjectDemo
~~~

Kết quả kiểm thử:

~~~text
Login thành công: true
Principal alice: alice
Public credentials: 1
Private credentials: 1
Logout xóa Principal: true
Logout xóa credentials: true
Logout hủy private proof: true
Sai mật khẩu bị từ chối: true
Thất bại không có authenticated Subject: true
~~~

### 9.1. Điều gì được chứng minh?

1. **Configuration** được cung cấp trực tiếp bằng overload LoginContext(name, subject, handler, config); không cần thay file cấu hình toàn cục của JVM.
2. **LoginContext.login** điều phối module với flag REQUIRED; vì chỉ có một module trong demo, login thành công khi module xác thực thành công.
3. **DemoLoginModule.login** dùng callback lấy username/password; đây là **so sánh chuỗi cố định phục vụ test**, không phải password verification an toàn cho production.
4. **commit** gắn UserId, PublicProfile và PrivateProof. Chúng nằm ở **ba Set khác nhau**, chứ không được nhập thành một Map.
5. **getSubject** trả Subject sau login thành công; không nên lấy đối tượng này trước khi login rồi tin nó đã xác thực.
6. **logout** chỉ gỡ ba phần tử DemoLoginModule đã thêm và gọi destroy() trên PrivateProof.
7. **Failed login** làm LoginContext không có authenticated Subject trong trường hợp constructor không nhận Subject và quá trình xác thực không thành công.
8. **char[] password** được clear ở PasswordCallback và caller theo phần bộ nhớ chúng sở hữu; không giả định đã xóa mọi bản sao trong JVM.

### 9.2. Mẫu tối giản cố ý thiếu gì?

- **Identity store thật, salt/password hashing KDF, rate limit, MFA, audit, anti-enumeration và account lockout**: ứng dụng production phải bổ sung qua provider/thành phần phù hợp.
- **Quản lý proof thật**: PrivateProof chỉ chứa chuỗi placeholder; không phải token/secret có thể dùng truy cập hệ thống.
- **Xử lý nhiều LoginModules**: demo một REQUIRED module; hệ thống thực có nhiều module và phải cleanup đúng cả commit/abort.
- **Transactional commit**: code demo không có rollback phức tạp nếu thêm phần tử thất bại giữa chừng. Implementation production phải đảm bảo cleanup khi commit lỗi và khi module bị gọi lại.
- **Subject read-only**: demo không đóng băng Subject trước logout vì cần remove các phần tử đã thêm.
- **Quản lý session HTTP**: JAAS Subject không tự lưu phiên trình duyệt, không cấp cookie hoặc access token.

**Quan trọng:** không copy so sánh Arrays.equals với chuỗi password hardcoded sang production. Đó chỉ là fixture chạy độc lập, không có khả năng chống dò mật khẩu. Thiết kế production phải dùng cơ chế xác thực tin cậy và kiểm soát lạm dụng.

## 10. Subject.doAs: thực thi gắn Subject và giới hạn mô hình bảo mật cũ

Trong Java SE 17, **Subject.doAs(subject, PrivilegedAction)** và **Subject.doAs(subject, PrivilegedExceptionAction)** cho phép thực thi một action trong **AccessControlContext** có gắn Subject. Mô hình lịch sử của JAAS dùng thông tin Principal này kết hợp Java Security Manager/policy để đánh giá một số quyền.

Ví dụ **mảnh code**, không phải một chương trình độc lập:

~~~java
String answer = Subject.doAs(authenticatedSubject,
        (java.security.PrivilegedAction<String>) () -> {
            // Code thực hiện operation trong ngữ cảnh JAAS của Subject.
            return "operation-finished";
        });
~~~

**Điều không được hiểu sai:**

- doAs **không tự xác thực** người dùng, không biến một Principal tự tạo thành danh tính đáng tin, và không cấp phép mọi thao tác.
- doAs **không tự động truyền bearer token/private credentials** sang dịch vụ khác. Giao thức Kerberos/GSS-API hoặc library riêng có thể sử dụng Subject credentials, nhưng cách dùng tuân API của provider.
- doAs trong Java 17 dựa trên **AccessControlContext/Security Manager**, là mô hình đã có các API deprecated-for-removal. Đặc biệt **Subject.getSubject(AccessControlContext)** bị đánh dấu deprecated-for-removal trong Java 17.
- Đừng giả định **context doAs tự truyền qua ExecutorService, CompletableFuture hoặc thread pool**. Cần xem xét behavior của API/JDK/provider cụ thể, truyền identity/authorization context qua ranh giới async một cách rõ và an toàn.
- Với service Java hiện đại, **authorization nên được kiểm tra rõ ràng theo identity, role, tenant và resource** ở tầng ứng dụng hoặc security framework đang duy trì; không coi doAs là lớp authorization phổ quát.

**Bài này chỉ dùng doAs để chỉ ra mối liên hệ của Subject với mô hình JAAS**, không yêu cầu cài Security Manager và không biến Security Manager thành tiền đề của hai ví dụ runnable.

## 11. Subject không thay thế ranh giới bảo mật của hệ thống

Một Subject có thể được sử dụng bởi code application để biết ai đang thực hiện yêu cầu. Nhưng:

1. **Đầu vào Subject phải đáng tin:** LoginModule/provider hoặc tầng xác thực đã xác minh identity; không tin dữ liệu trực tiếp từ cookie/header chưa kiểm tra.
2. **Chọn đúng Principal class:** nếu hệ thống cho nhiều realm, đừng chọn Principal đầu tiên hoặc lọc bằng tên không có namespace.
3. **Credential scope rõ ràng:** không giữ secret credential trong Subject dùng chung toàn bộ process; tránh đưa access token giữa tenant/service khác nhau.
4. **Dữ liệu trong Subject có vòng đời:** logout, session expiry, key rotation, credential refresh/revoke; một object vẫn được tham chiếu sau logout có thể tiếp tục tồn tại nếu caller giữ riêng.
5. **Kiểm tra quyền nghiệp vụ:** Subject có Role("admin") không tự bảo đảm caller có quyền xem record của tenant khác.
6. **Quản lý khóa riêng:** private-key handle từ HSM có thể không export được; chỉ thực hiện operation theo policy của provider, không cố đọc key bytes để debug.
7. **Audit tối thiểu:** ghi subject ID đã được phép, event type, issuer, action, outcome và trace ID; không log toàn bộ Subject hoặc private credentials.

### 11.1. Thiết kế ranh giới cho web service

~~~text
HTTP request (header/token/cookie chưa được tin)
             |
             v
Authentication layer (JAAS module, IdP, framework...)
       kiểm chứng chứng chỉ/ticket/token/password
             |
      xác thực thành công?
        /            \
       no             yes
       |               |
     reject            v
                  Subject + Principal(s)
                  public/private credentials đúng scope
                       |
                       v
                 Build safe identity DTO
                 userId, tenantId, roles đã kiểm chứng
                       |
                       v
                 Authorize action + resource
                       |
                       v
                    Business logic
                       |
                       v
                  session end / logout /
                  destroy secret / revoke
~~~

Nhiều hệ thống web hiện đại dùng các framework authentication/security riêng, không trực tiếp dùng JAAS LoginContext. Dù vậy, **phân biệt danh tính (Principal), chứng thực đã diễn ra (authentication) và quyền thực thi (authorization)** là kiến thức cốt lõi có thể áp dụng rộng.

### 11.2. Identity DTO khác credential store

Một DTO dành cho business logic chỉ nên mang dữ liệu cần thiết như **stable user ID, tenant ID, roles đã được phê duyệt, issuer và thời điểm xác thực khi cần**. Không chứa raw password, private key, Kerberos ticket hay bearer token. Việc tạo DTO có thể lọc theo Principal class cụ thể và kiểm tra tính duy nhất, nhưng phải nhớ typed Set **không phải deep-copy** các Principal mutable; nên chuyển sang immutable application values.

## 12. Threading, serialization và các chi tiết tinh tế

### 12.1. Không chia sẻ Subject mutable giữa request

Vì untyped sets được backed bởi Subject, hai threads có thể quan sát và sửa dữ liệu danh tính khi không có ownership rõ ràng. Với nhiều JAAS modules hoặc session lifecycle, một request đang đọc credentials có thể chạm credential vừa bị destroy/logout ở request khác.

Khuyến nghị:
- Mỗi phiên xác thực có **lifecycle riêng**; không dùng một Subject toàn cục rồi thay Principal theo request.
- Giai đoạn tạo/commit/logout phải được phối hợp phù hợp với provider/lifecycle.
- **Không dùng Subject equals/hashCode làm cache key ổn định**: các tập Principal/credentials là một phần equality và có thể thay đổi.
- Với async work, truyền **immutable security context/DTO** hoặc một handle có quyền rõ, không chia sẻ raw private credentials cho bất kỳ job nào.
- **Không trông chờ read-only ngăn credential bị destroy**: mọi consumer phải xử lý credential đã hết hạn hoặc bị hủy.

### 12.2. Serialization của Subject không có private credentials

Theo Javadoc Subject của Java 17, Subject có Serializable, **Principals được serialize** còn **credentials không được serialize**. Interface java.security.Principal không tự triển khai Serializable, nên Principal implementation cần Serializable nếu muốn serialize theo contract.

Bởi vậy:
- Serialize/deserialize Subject **không round-trip các credentials**. Không dùng như session persistence hoặc backup khóa.
- Không cho deserialization tùy tiện dữ liệu Java không đáng tin; việc deserialize object Java có những rủi ro độc lập.
- Hãy xây dựng schema DTO chuyên dụng, có version và kiểm soát field, nếu cần truyền danh tính qua service.

### 12.3. Secret bị hủy nhưng Set còn chứa object

Một credential triển khai Destroyable có thể bị hủy trong lúc Subject đang **read-only**, theo Subject contract. Object vẫn có thể nằm trong getPrivateCredentials() do không remove được. Điều đó không có nghĩa credential còn dùng được; cần tham khảo isDestroyed/lifecycle của type cụ thể. Với các lớp credentials không hỗ trợ destroy hoặc destroy thất bại, một login/logout framework cần chiến lược xử lý lỗi thích hợp, không nên im lặng và khẳng định secret đã bị xóa.

## 13. Bộ kiểm thử nên có cho Subject và LoginModule

| Nhóm | Test | Kỳ vọng |
| --- | --- | --- |
| **Subject mới** | new Subject() | Ba tập rỗng, writable ban đầu. |
| **Principal equality** | Thêm cùng UserId("alice") hai lần | Không có duplicate với record value equality. |
| **Nhiều Principal** | Thêm UserId và Role | Tập giữ cả hai concrete types. |
| **Public credential** | Thêm PublicProfile label | Đọc được từ public set; không bị nhầm với private set. |
| **Private credential** | Thêm SessionSecret | Đọc được theo type phù hợp; không log char[]/token. |
| **Live set** | Lấy getPrincipals() rồi add(Role) | Subject được cập nhật. |
| **Typed snapshot** | Lấy getPrincipals(UserId.class) rồi clear | Subject gốc không thay đổi. |
| **Reference aliasing** | Typed filtered private set trỏ đến cùng object | Hủy object được phản ánh mọi nơi giữ tham chiếu. |
| **Read-only** | setReadOnly rồi add/remove | IllegalStateException; không thể mở lại writable. |
| **Destroy** | Destroy private credential sau setReadOnly | Có thể hủy nếu type cho phép; không đồng nghĩa remove khỏi Set. |
| **Subject constructor** | Set null/null element, readOnly true | Reject null, giữ initial elements, readOnly enforcement. |
| **Login success** | Callback cung cấp thông tin đúng, login | Commit tạo Principal/public/private đúng scope. |
| **Login failure** | Callback sai password | LoginException; không có authenticated Subject khi không truyền Subject ban đầu. |
| **Abort** | Một module required thất bại hoặc commit lỗi | Xóa trạng thái tạm, không để leaked/partially committed credentials. |
| **Logout** | Login xong rồi logout | Gỡ đúng phần tử module đã thêm; destroy secret. |
| **Multi-module** | Hai LoginModules cùng dùng một Subject | Module A không xóa Principal/credential của B. |
| **Private permission** | Java 17 Security Manager + policy hạn chế | Access to private credentials được kiểm theo PrivateCredentialPermission, xử lý SecurityException. |
| **Serialization** | Subject chứa Principal Serializable và credentials | Credential không được serialize theo contract; không dùng làm session token. |
| **Concurrent logout/use** | Một thread logout/hủy, một thread đang xử lý | Không tiếp tục thao tác bằng secret bị hủy; ownership rõ. |
| **Log inspection** | Thử gọi logger với Subject | Audit không in password/private credential material. |

Với LoginModule thật, còn cần test **authentication source** (directory/Kerberos/IdP), account lockout/rate-limit, ticket expiry/refresh, permissions, revocation và audit. Hai demo chỉ kiểm tra **contracts cốt lõi của Subject/Principal/credentials**, không thay thế integration tests cho provider production.

## 14. Các lỗi phổ biến

1. **Coi Subject là một String username.** Nó có thể chứa nhiều danh tính và credentials khác nhau.
2. **Tự thêm Role("admin") rồi nghĩ người dùng đã được xác thực.** Principal chỉ đáng tin sau authentication và trust boundary phù hợp.
3. **Đặt password hoặc bearer token vào Principal.** Principal mô tả danh tính; bí mật cần quản lý ở lớp credential/secret phù hợp.
4. **Tưởng publicCredentials luôn an toàn để log.** Tùy type, một vé/token vẫn có thể là bearer capability nhạy cảm.
5. **Tưởng privateCredentials tự mã hóa hoặc khóa bí mật bằng quyền JVM hiện đại.** Subject chỉ là một tập object; bảo vệ thực sự phải được thiết kế riêng.
6. **Nhầm getPrincipals() và getPrincipals(Class).** Untyped là live; typed là tập mới không backed.
7. **Nhầm typed filtered credential set là deep copy secret.** Các phần tử là tham chiếu tới object gốc.
8. **Cho mọi layer giữ Subject mutable.** Chúng có thể sửa role/credentials nếu không có ranh giới.
9. **Dùng Subject.setReadOnly như logout.** Read-only không xóa Principal/secret.
10. **Giả định read-only khiến credential không thể destroy.** Contract cho phép destroy credential kể cả khi Subject read-only.
11. **Remove credential khỏi Set rồi nghĩ đã xóa secret bytes.** Reference removal và memory clearing là hai hành động khác nhau.
12. **Gọi destroy rồi nghĩ mọi bản sao secret trong JVM biến mất.** Vẫn có thể còn bản sao và implementation-dependent state.
13. **Ghi log Subject.toString() hoặc all credentials.** Có nguy cơ lộ identity/secret và thông tin phiên.
14. **Dùng principal.getName() không xét issuer/realm/tenant.** Dễ nhầm identity cùng tên.
15. **Dùng Subject.equals/hashCode làm user ID ổn định.** Equality phụ thuộc cả credential/Principal sets, không phải account identity.
16. **Gắn Principal vào Subject ngay trong login() trước khi overall authentication thành công.** Nên commit sau khi toàn bộ modules đáp ứng chính sách; abort dọn state.
17. **LoginModule.logout() xóa sạch Subject.** Có thể xóa thành phần thuộc module khác; chỉ dọn phần mình sở hữu.
18. **Set read-only trước khi LoginModule logout.** Có thể khiến module không remove được Principal/credential.
19. **Dùng LoginContext của một người để authenticate người khác.** Tạo LoginContext riêng cho Subject/session khác.
20. **Dùng Subject.doAs như authorization hoàn chỉnh cho web app mới.** Đây là mô hình JAAS/AccessControlContext lịch sử, không thay RBAC/ABAC của ứng dụng.
21. **Tưởng JAAS tự xử lý token refresh và network session.** Lộ trình refresh/logout/revoke phụ thuộc credential type, provider và ứng dụng.
22. **Serialize Subject để lưu trọn phiên cùng credentials.** Credentials không được serialize theo contract.

## 15. Câu hỏi tự kiểm tra

**1. Một Subject có thể có nhiều Principal không?**  
Có. Subject có Set<Principal> và mỗi Principal có thể biểu diễn identity/role/realm khác nhau.

**2. UserId("alice") và Role("alice") có nhất thiết là cùng một Principal không?**  
Không. Khác concrete type và ý nghĩa; cùng text chưa đủ để khẳng định cùng danh tính.

**3. getPrincipals(UserId.class).clear() có xóa UserId trong Subject không?**  
Không. Tập typed được trả về là tập mới, không backed bởi Subject.

**4. getPrincipals().add(new Role("admin")) có sửa Subject không?**  
Có, nếu Subject writable và caller được phép; vì đó là live set. Nhưng điều này **không làm role trở thành đã xác thực**.

**5. Private credential có tự được mã hóa vì nằm trong privateCredentials không?**  
Không. Subject tổ chức object; cách bảo vệ material tùy implementation, provider và chính sách.

**6. setReadOnly() có ngăn destroy() của credential không?**  
Không. Subject contract vẫn cho phép destroy credential; nhưng cấm add/remove trong sets.

**7. Hủy credential rồi private Set vẫn có một object, có nghĩa secret chưa bị hủy không?**  
Không nhất thiết. Object có thể vẫn tồn tại trong Set sau khi material bị destroy. Cần kiểm tra isDestroyed theo concrete type.

**8. Java 17 Security Manager bị deprecated, có nên coi PrivateCredentialPermission là hàng rào duy nhất không?**  
Không. Cần application trust boundary, least privilege, secret management và authorization rõ.

**9. LoginModule nên thêm Principal ở login() hay commit()?**  
Thông thường ở **commit() sau khi overall authentication thành công**. Abort dọn state khi login/commit thất bại.

**10. Logout có nên xóa hết mọi Principal/credential của Subject không?**  
Không. Mỗi LoginModule phải gỡ/destroy những gì **nó sở hữu**, không đụng dữ liệu của module khác.

**11. Một Principal record được tạo thủ công có chứng minh password hợp lệ không?**  
Không. Principal chỉ là object; bằng chứng đến từ kết quả authentication đáng tin.

**12. Có dùng Subject serialization để mang credentials qua network được không?**  
Không. Java 17 Subject serialize Principals nhưng không serialize credentials; cần giao thức bảo mật riêng.

## 16. Tổng kết và tài liệu tham khảo

**Subject** là container cho **Principal identities** và **public/private credentials**. Principal trả lời câu hỏi “**thực thể này được nhận diện như thế nào**”, trong khi credentials là dữ liệu phục vụ chứng thực và thao tác được phép. Phân loại public/private có ý nghĩa về quyền truy cập và độ nhạy, nhưng không tự tạo bảo mật nếu application làm lộ object hoặc quản lý secrets sai.

Những điều bắt buộc ghi nhớ trong Java 17:

- **getPrincipals/getPublicCredentials/getPrivateCredentials không typed** trả live sets; typed getters trả tập lọc mới.
- **setReadOnly** chặn sửa ba Set, nhưng **không deep-freeze** credential objects và vẫn có thể destroy credential.
- **LoginContext.login → LoginModule.commit** gắn Principal/credentials khi xác thực thành công; **abort/logout** cần cleanup.
- **Identity không đồng nghĩa authorization**; bí mật vẫn cần least privilege, lifecycle và logging an toàn.

Nguồn chính thức để đọc sâu:

- [Java SE 17 — javax.security.auth.Subject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/Subject.html)
- [Java SE 17 — java.security.Principal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Principal.html)
- [Java SE 17 — javax.security.auth.Destroyable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/Destroyable.html)
- [Java SE 17 — javax.security.auth.Refreshable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/Refreshable.html)
- [Java SE 17 — LoginContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/LoginContext.html)
- [Java SE 17 — LoginModule SPI](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/spi/LoginModule.html)
- [Java SE 17 — JAAS Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-authentication-authorization-service-jaas-reference-guide.html)
- [Java SE 17 — LoginModule Developer's Guide](https://docs.oracle.com/en/java/javase/17/security/java-authentication-and-authorization-service-jaas-loginmodule-developers-guide1.html)
- [Java SE 17 — Configuration](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/Configuration.html)
- [Java SE 17 — PrivateCredentialPermission](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/PrivateCredentialPermission.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jaas-authentication-003">17.3 LoginContext, Configuration và mô hình LoginModule có thể thay thế</a>

<details>
<summary>Xem chi tiết</summary>

# 17.3 LoginContext, Configuration và mô hình LoginModule có thể thay thế

Trong Java Authentication and Authorization Service (**JAAS**), ứng dụng muốn xác thực người dùng hoặc dịch vụ không nhất thiết phải tự viết một hàm đăng nhập gắn chặt với database, Kerberos, LDAP hay hệ thống SSO. JAAS đưa ra mô hình **xác thực có thể thay thế (*pluggable authentication*)**: ứng dụng gọi **`LoginContext`**, một **`Configuration`** quyết định các **`LoginModule`** được kích hoạt và thứ tự/chính sách kết hợp chúng, còn mỗi `LoginModule` hiện thực một cơ chế xác thực cụ thể.

Ba vai trò cốt lõi:

- **`LoginContext`** là bộ **điều phối quy trình đăng nhập/đăng xuất** theo tên ứng dụng. Nó đọc cấu hình, gọi các module qua vòng đời chuẩn và cung cấp một **`Subject`** sau khi đăng nhập thành công.
- **`Configuration`** là **bản thiết kế** xác thực: mỗi application name ánh xạ tới một **danh sách có thứ tự** các `AppConfigurationEntry` gồm tên class `LoginModule`, cờ điều khiển `REQUIRED`/`REQUISITE`/`SUFFICIENT`/`OPTIONAL`, và `options` cho module.
- **`LoginModule`** là **SPI (Service Provider Interface)** gồm năm phương thức `initialize`, `login`, `commit`, `abort`, `logout`. Module thực hiện việc kiểm tra credential và chỉ **gắn Principal/credential vào Subject ở bước commit** khi quá trình chung thành công.

Bài này thuộc phần **17. Xác thực JAAS** trong Java Advanced Security & Cryptography. Các ví dụ dùng **Java 17+**, không cần kết nối Internet hay server Kerberos. Ta sẽ chạy ba chương trình độc lập: xác thực qua callback và `Subject`, mô phỏng các **control flags**, và nạp JAAS `Configuration` từ file. Ví dụ cố tình dùng mật khẩu test/hành vi cho phép hoặc từ chối giả lập để minh họa **contract JAAS**, **không** phải thư viện xác thực password an toàn cho production.

## 1. Mô hình tổng quan và đường đi một lần login

```text
APPLICATION
    |
    | 1. new LoginContext("LearningApp", subject, callback, config)
    v
LoginContext
    |
    | tra cấu hình theo application name
    v
Configuration
    |
    +-- AppConfigurationEntry #1
    |      className = ...PasswordModule
    |      flag = REQUIRED
    |      options = {...}
    |
    +-- AppConfigurationEntry #2
    |      className = ...MfaModule
    |      flag = REQUIRED
    |      options = {...}
    |
    +-- AppConfigurationEntry #3
           className = ...AuditModule
           flag = OPTIONAL
           options = {...}
    |
    v
LoginContext.login()
    |
    +-- instantiate/initialize module(s)
    |       Subject + CallbackHandler + sharedState + options
    |
    +-- PHASE 1: call login() theo thứ tự + control flags
    |
    +-- Nếu xác thực chung thành công:
    |       PHASE 2: call commit()
    |       -> Subject chứa Principal/credentials phù hợp
    |
    +-- Nếu thất bại:
            PHASE 2: call abort()
            -> dọn state/credential tạm
    |
    v
Application receives success / LoginException
    |
    +-- login.getSubject(): danh tính đã xác thực khi thành công
    |
    +-- logout() -> từng LoginModule.logout()
```

**Điểm quan trọng:** `LoginContext` không tự biết mật khẩu đúng là gì, không tự nối LDAP, không sinh token, và không tự đưa user vào role được phép. Module xác thực trả kết quả; `LoginContext` áp dụng cờ và vòng đời; ứng dụng/phần authorization riêng **đánh giá quyền truy cập** từ danh tính và policy.

### 1.1 Bốn đối tượng dễ nhầm

| Thành phần | Vai trò | Có chứa password/credential không? | API |
| --- | --- | --- | --- |
| **`LoginContext`** | Điều phối login, commit/abort và logout; cung cấp Subject | Không tự làm kho password; chuyển CallbackHandler/Subject cho module | `javax.security.auth.login.LoginContext` |
| **`Configuration`** | Quyết định *những module nào*, thứ tự, cờ, option | **Không nên** chứa password/secret hardcode | `javax.security.auth.login.Configuration` |
| **`LoginModule`** | Xác thực theo công nghệ cụ thể; quản lý state/Subject của mình | Có thể **tạm thời** xử lý credential, phải bảo vệ và xóa phù hợp | `javax.security.auth.spi.LoginModule` |
| **`CallbackHandler`** | Cung cấp dữ liệu tương tác như username, password, OTP hoặc nguồn credential | Có thể đưa credential vào `Callback`; không phải nơi kết luận trust | `javax.security.auth.callback.CallbackHandler` |

**`Subject` không phải `LoginContext`.** `Subject` đại diện thực thể đã xác thực, có các tập `Principal`, public credentials và private credentials. Sau khi `login()` thành công, ứng dụng thường nhận nó qua `getSubject()`. Mỗi `Principal` biểu diễn một tên/thuộc tính identity; một `Principal` có tên `admin` không tự trao quyền quản trị nếu ứng dụng chưa có chính sách authorization tương ứng.

## 2. LoginContext: hợp đồng với ứng dụng

Cách dùng phổ biến:

```java
// Minh họa bên trong phương thức có callbackHandler hợp lệ.
LoginContext login = new LoginContext(
        "LearningApp", callbackHandler);
login.login();
Subject subject = login.getSubject();
try {
    // Chỉ dùng các thuộc tính identity đã được xác thực.
} finally {
    login.logout();
}
```

Đây là **đoạn minh họa**, chưa phải chương trình độc lập vì thiếu imports, `callbackHandler` và cách quản lý exception. Ví dụ đầy đủ ở mục 7 sẽ chạy được.

### 2.1 Các constructor đáng nhớ

| Constructor | Ý nghĩa |
| --- | --- |
| `new LoginContext(String name)` | Đọc installed `Configuration`, tìm entry của `name`, callback tùy default config |
| `new LoginContext(String name, CallbackHandler handler)` | Installed `Configuration`, handler do caller cung cấp |
| `new LoginContext(String name, Subject subject)` | Xác thực **Subject đã cung cấp**, callback theo default nếu cần |
| `new LoginContext(String name, Subject subject, CallbackHandler handler)` | Installed `Configuration`, Subject và handler tường minh |
| `new LoginContext(String name, Subject subject, CallbackHandler handler, Configuration config)` | **Configuration riêng** cho context; phù hợp ứng dụng muốn kiểm soát rõ module stack, không đổi global config |

**Không có** constructor tiêu chuẩn `LoginContext(String, CallbackHandler, Configuration)` trong Java 17; muốn truyền config riêng cần overload **bốn tham số** với `Subject` có thể đặt `null`. Mẫu chạy được:

```java
LoginContext ctx = new LoginContext(
        "LearningApp", null, callbackHandler, configuration);
```

Khi caller không cung cấp `Subject`, `LoginContext` tạo Subject mới theo contract; khi cung cấp Subject, hệ thống dùng chính Subject đó. **Không nên tái sử dụng một `LoginContext` cho nhiều người dùng/Subject khác nhau**; tài liệu API khuyến nghị context riêng cho mỗi Subject.

### 2.2 Application name là khóa tra cứu, không phải loại xác thực

`"LearningApp"`, `"MyService"` hay `"FileDemo"` là **tên entry** trong Configuration:

```text
LoginContext("LearningApp") --> Configuration.getAppConfigurationEntry("LearningApp")
                                     |
                                     +--> list of LoginModules + flags + options
```

Nếu không có entry cho `name`, `LoginContext` theo contract còn thử entry **`"other"`**. Nếu cả hai đều không có, constructor có thể ném `LoginException`. Chính sách fallback này hữu ích cho một số hệ thống, nhưng có thể gây **thay thế nhầm cơ chế xác thực** nếu cấu hình `other` quá dễ dãi. Với ứng dụng có nhiều trust domain, hãy kiểm tra tên entry và cấu hình fallback một cách có chủ ý.

### 2.3 login(), getSubject(), logout()

- **`login()`:** thực hiện các module theo cấu hình, hoàn thành cơ chế xác thực chung hoặc ném `LoginException`.
- **`getSubject()`:** lấy `Subject` tương ứng. Đừng dùng Subject như bằng chứng đăng nhập trước khi `login()` trả về thành công; trường hợp chưa đăng nhập có thể trả `null` theo điều kiện constructor/trạng thái.
- **`logout()`:** gửi yêu cầu logout đến các module trong context; **module tự dọn** Principal và credential mà nó đã thêm. Logout không tự revoke session token/JWT ở dịch vụ bên ngoài nếu module không triển khai.
- **Không tự động retry** khi password sai: rate limiting, lockout, backoff, CAPTCHA hoặc quy tắc retry thuộc application/auth service. Tài liệu JAAS hướng dẫn module không tự đặt delay/retry mơ hồ trong `login()`.

## 3. Configuration: cách chọn và thay LoginModule

`Configuration` là abstract class trả về **`AppConfigurationEntry[]`** cho application name. Mỗi entry có ba phần:

```text
AppConfigurationEntry {
    loginModuleName: "com.example.auth.PasswordModule"
    controlFlag: REQUIRED
    options: {"debug": "false", "realm": "example"}
}
```

Về mặt Java:

```java
// Đoạn API minh họa, không phải file độc lập.
AppConfigurationEntry item = new AppConfigurationEntry(
        PasswordModule.class.getName(),
        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
        Map.of("realm", "demo"));
```

**Các thuộc tính:**

1. **Class name**: fully qualified name của một `LoginModule` có thể load được, triển khai interface đúng và **có public no-argument constructor**. Khi dùng nested class trong demo, Java class name có dấu **`$`**.
2. **Control flag**: quy tắc kết hợp thành công/thất bại với module khác; không phải “mức độ bảo mật” hay “priority”.
3. **Options**: map **riêng cho module**, module quyết định tên và ý nghĩa. `debug`, `tryFirstPass`, `useFirstPass` không được bảo đảm có chung semantics cho mọi module.

### 3.1 Declarative JAAS configuration file

Một cấu hình file thường có cú pháp:

```text
ApplicationName {
    fully.qualified.ModuleClass required optionName="value";
    another.ModuleClass optional debug="false";
};
```

Các module trong cùng block chạy theo **đúng thứ tự được liệt kê**, tùy cờ có thể dừng sớm. Dấu chấm phẩy **sau từng module** và sau dấu đóng `}` có ý nghĩa cú pháp; option string được đặt trong dấu ngoặc kép theo default configuration syntax.

Đoạn minh họa cho một hệ thống xác thực nhiều bước:

```text
ServiceLogin {
    com.example.auth.PasswordLoginModule required realm="employees";
    com.example.auth.OtpLoginModule required issuer="company";
    com.example.auth.AuditLoginModule optional debug="false";
};
```

**Các class `com.example.auth.*` chỉ là tên minh họa, không phải class có sẵn trong JDK.** Ví dụ runnable thực tế ở mục 9 sử dụng class có thật được định nghĩa trong file Java kèm theo. Config chỉ định **module**, không tạo implementation và cũng không tự cài đặt LDAP/OTP.

Với default ConfigFile implementation của JDK, có thể cung cấp file theo system property:

```bash
java -Djava.security.auth.login.config=jaas.conf MainClass
```

Nếu application dùng installed config, nó truy vấn `Configuration.getConfiguration()`. Cấu hình cụ thể có thể đến từ system properties, các `login.config.url.n` security properties và provider, với precedence theo reference implementation và cách khởi động. Đừng suy đoán một file `jaas.conf` chắc chắn được app dùng khi code truyền **Configuration riêng** vào constructor bốn tham số.

**Không đặt password, secret token, private key hoặc thông tin kết nối nhạy cảm vào JAAS conf**: option không tự được mã hóa, có thể hiện trong file, memory, diagnostics và process config. Dùng secret management thích hợp, cấu hình file nên chứa **tên realm, issuer, mode, endpoint đã kiểm soát** và các tùy chọn không bí mật.

### 3.2 Configuration lập trình được

Thay vì dựa vào file toàn JVM, ứng dụng có thể subclass:

```java
// Đoạn minh họa; MyLoginModule phải tồn tại và implement LoginModule.
Configuration config = new Configuration() {
    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
        if (!"LearningApp".equals(name)) {
            return null;
        }
        return new AppConfigurationEntry[]{
            new AppConfigurationEntry(
                    MyLoginModule.class.getName(),
                    AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                    Map.of())
        };
    }
};

LoginContext context = new LoginContext(
        "LearningApp", null, callbackHandler, config);
```

**Đây là mã minh họa không độc lập** vì `MyLoginModule` và `callbackHandler` chưa khai báo. Bài code hoàn chỉnh ở mục 7 tự định nghĩa module và Configuration tương tự.

Ưu điểm: tách policy xác thực theo tenant, environment hoặc test mà **không thay global installed Configuration**. Cần cố định/cập nhật version config cho một login attempt; đừng thay module stack giữa chừng một cách không xác định.

### 3.3 Installed configuration và refresh

`Configuration.getConfiguration()` lấy installed JAAS configuration; `Configuration.setConfiguration(...)` thay cài đặt global trong runtime. Khi có nhiều app/library trong cùng JVM, việc gọi `setConfiguration` để sửa một bài toán cục bộ có thể ảnh hưởng những caller khác.

`Configuration.refresh()` là yêu cầu provider **nạp lại/cập nhật** cấu hình theo implementation. Không được suy ra rằng:
- Mọi provider đều hỗ trợ hot reload tức thời;
- Một `LoginContext` đã tạo sẽ tự thay module list giữa một login;
- `setConfiguration` hoặc `refresh` là một thao tác atomic migration mọi active sessions.

**Nếu cần cấu hình động**, xây quy trình versioning, đồng bộ thay đổi, tạo context mới và test consistency; sử dụng Configuration được truyền tường minh cho context sẽ dễ cô lập thay đổi hơn.

## 4. Mô hình LoginModule có thể thay thế

Một `LoginModule` là **plugin xác thực**, không phụ thuộc trực tiếp vào giao diện web/console. Hai module có thể triển khai cùng SPI nhưng xác thực bằng kỹ thuật rất khác:

```text
Application:
    LoginContext("ServiceLogin").login()

Configuration A:
    PasswordDatabaseLoginModule required

Configuration B:
    KerberosLoginModule required

Configuration C:
    CertificateLoginModule required
    AuditLoginModule optional

Configuration D:
    PasswordLoginModule required
    OneTimePasswordModule required
```

Ứng dụng gọi LoginContext vẫn như cũ, nhưng **cơ chế thực tế đổi theo config và module implementation**. Điều này gọi là **pluggable authentication**. Tuy nhiên, module thay thế phải tương thích với yêu cầu về `Subject`/Principal type, credential source, trust level, exception semantics và authorization contract của ứng dụng. Thay module không có nghĩa tự bảo đảm **bảo mật tương đương**.

### 4.1 LoginModule interface: năm phương thức

```java
package javax.security.auth.spi;

// Chữ ký giải thích, không phải file nguồn mới để tự tạo package javax.
public interface LoginModule {
    void initialize(
            Subject subject,
            CallbackHandler callbackHandler,
            Map<String, ?> sharedState,
            Map<String, ?> options);
    boolean login() throws LoginException;
    boolean commit() throws LoginException;
    boolean abort() throws LoginException;
    boolean logout() throws LoginException;
}
```

Bảng nhiệm vụ:

| Phương thức | Thời điểm | Công việc chính | Điều nên tránh |
| --- | --- | --- | --- |
| **`initialize`** | Sau khi JAAS tạo instance, trước các phase | Lưu Subject, CallbackHandler, sharedState, options cần dùng | Kết nối tốn kém không có timeout, tự thêm Principal trước khi kiểm tra |
| **`login`** | Phase 1 | Nhận credential, xác thực, lưu tạm identity/auth status **trong module** | Thêm Principal vào Subject quá sớm; log password; tự retry vô hạn |
| **`commit`** | Phase 2, khi tổng thể authentication thành công | Nếu module của mình thành công, thêm Principal/credential hợp lệ vào Subject; xóa state tạm | Commit identity của module thất bại; giữ plaintext password lâu |
| **`abort`** | Phase 2, khi authentication chung thất bại hoặc cần rollback | Hủy credential/state tạm, rollback những thay đổi module chịu trách nhiệm | Để lại Principal/token do một nửa quá trình sinh ra |
| **`logout`** | Khi `LoginContext.logout()` được gọi | Gỡ Principal/credential do **module** đã thêm và hủy session/resource nếu thuộc trách nhiệm | `subject.getPrincipals().clear()` bừa bãi, xóa dữ liệu của module khác |

Module phải có **public constructor không tham số** để được LoginContext tạo qua provider/class loading. Public **static nested class** với default no-arg constructor cũng dùng được cho demo.

### 4.2 Tại sao login và commit phải tách?

Giả sử stack có hai module:

```text
PasswordModule  REQUIRED  -> password đúng
OtpModule       REQUIRED  -> OTP sai
```

Nếu `PasswordModule.login()` **đã thêm Principal vào Subject**, sau khi OTP thất bại Subject có thể lưu một phần identity dù **overall login bị từ chối**. Do đó giao ước chuẩn:

```text
PHASE 1:
    PasswordModule.login() -> true (giữ pending state riêng)
    OtpModule.login()      -> throws FailedLoginException

OVERALL FAILURE:
    PasswordModule.abort()
    OtpModule.abort()
    Subject không có identity mới được commit
```

Nếu cả hai thành công:

```text
PHASE 1:
    PasswordModule.login() -> true
    OtpModule.login()      -> true

PHASE 2:
    PasswordModule.commit() -> thêm Principal vào Subject
    OtpModule.commit()      -> bổ sung credential/assurance nếu phù hợp
    LoginContext.login()    -> returns successfully
```

**Điểm quyết định:** `login() == true` của **một module** không đồng nghĩa **overall LoginContext success**. Chính các **control flags** của Configuration quyết định ngưỡng thành công, và `commit` chỉ chạy theo logic phase sau khi điều kiện chung thỏa.

## 5. REQUIRED, REQUISITE, SUFFICIENT, OPTIONAL

Bốn cờ có ý nghĩa kiểm soát **thứ tự xác thực và điều kiện thành công**. Đây là trọng tâm khiến JAAS hỗ trợ stack module thay vì một hàm boolean.

| Flag | Khi module thành công | Khi module thất bại | Ảnh hưởng tới kết quả chung |
| --- | --- | --- | --- |
| **`REQUIRED`** | Tiếp tục đánh giá các module sau | **Vẫn tiếp tục** đánh giá module sau | Module này **phải thành công**; lỗi của nó không bị một `SUFFICIENT` phía sau xóa bỏ |
| **`REQUISITE`** | Tiếp tục đánh giá module sau | **Dừng ngay** phase login stack | Module này **phải thành công**; khi thất bại trả lỗi sớm |
| **`SUFFICIENT`** | Có thể **dừng sớm thành công** nếu **chưa có lỗi REQUIRED/REQUISITE trước đó** | Tiếp tục các module sau | Không bắt buộc; một SUFFICIENT thành công có thể giúp kết thúc stack |
| **`OPTIONAL`** | Tiếp tục các module sau | Tiếp tục các module sau | Không bắt buộc, trừ trường hợp stack không có required/requisite thì cần ít nhất một sufficient/optional thành công |

**Quy tắc chung theo Java `Configuration`:** tất cả module **REQUIRED/REQUISITE đã được thực thi liên quan** phải thành công. Nếu một `SUFFICIENT` thành công, các module REQUIRED/REQUISITE **đứng trước nó** phải thành công để kết thúc sớm. Nếu không có REQUIRED hoặc REQUISITE nào, cần **ít nhất một SUFFICIENT hoặc OPTIONAL thành công**.

### 5.1 Thứ tự tạo ra sự khác biệt

Các stack sau cho kết quả khác nhau:

```text
A. OPTIONAL fail -> REQUIRED pass
   => SUCCESS

B. REQUIRED fail -> SUFFICIENT pass
   => FAILURE (required fail không được bỏ qua)

C. REQUISITE fail -> OPTIONAL pass
   => FAILURE; optional không được gọi

D. SUFFICIENT pass -> REQUIRED fail
   => SUCCESS sớm; required phía sau không được gọi

E. OPTIONAL fail (chỉ một module)
   => FAILURE vì không module nào thành công
```

Lưu ý khác biệt **lỗi ném `LoginException`** và **`login()` trả `false`**: LoginModule contract cho phép trả `false` khi module nên được bỏ qua; không dùng `return false` để diễn tả “mật khẩu sai” cho module xác thực bắt buộc trong code minh họa. Ví dụ cố ý ném **`FailedLoginException`** khi một module phủ quyết, để ý nghĩa test rõ ràng.

### 5.2 Vì sao không dùng OPTIONAL cho tất cả?

Một stack gồm `AuditModule OPTIONAL` và `PasswordModule OPTIONAL` có thể **chấp thuận** nếu audit module trả success dù password module thất bại. Cấu hình cờ sai có thể tạo **bypass xác thực**. Đặc biệt, module “audit/logging” không nên được thiết kế trả về `true` theo cách biến một stack toàn OPTIONAL thành thành công khi không có module xác thực thực sự.

**Nguyên tắc:** thiết kế cờ theo **threat model**, không theo ý muốn “login ít báo lỗi”; unit-test từng tổ hợp thành công/thất bại, bảo đảm mọi yếu tố bắt buộc thực sự là bắt buộc. MFA muốn **password và OTP đều đúng** thường cần các module có policy tương ứng, ví dụ cả hai REQUIRED hoặc xử lý logic xác thực tập trung đã kiểm định; không gắn một bước MFA là OPTIONAL chỉ vì đôi khi người dùng không có OTP.

## 6. Subject, Principal và Credentials

`javax.security.auth.Subject` đại diện thực thể được xác thực và có ba tập dữ liệu chính:

```text
Subject
  |
  +-- Set<Principal>
  |      ví dụ: UserPrincipal("student"),
  |              RolePrincipal("reader") (nếu trusted policy cấp)
  |
  +-- publicCredentials
  |      dữ liệu credential có thể công khai theo contract
  |
  +-- privateCredentials
         credential bí mật/nhạy cảm, phải bảo vệ quyền đọc và vòng đời
```

`Principal` là interface có `getName()`; trong Java 17 ta có thể dùng **`record AppPrincipal(String getName) implements Principal`** cho ví dụ nhỏ. Trong hệ thống thật, thiết kế Principal types ổn định, mapping identity → role, tenant, scope và authorization policy **cần được phân tích riêng**. Không suy ra `subject.getPrincipals().contains("admin")` là đủ quyền nếu không có Principal type/issuer/trust contract đúng.

### 6.1 Khi nào cập nhật Subject?

- **Trước login:** Subject mới/cũ có thể có Principals được cấp bởi module khác; module cần giữ nguyên nếu không sở hữu.
- **Trong login:** lưu identity tạm (`pendingPrincipal`) trong instance **không** thêm vào Subject.
- **Trong commit:** chỉ thêm identity nếu module mình đã xác thực thành công, overall flow thành công.
- **Trong abort:** xóa state/tokens tạm, rollback những thay đổi có thể đã phát sinh.
- **Trong logout:** gỡ **đúng những Principal/credential của module mình**. Không xóa toàn bộ `Subject.getPrincipals()` vì module khác có thể đã thêm data hợp lệ.
- **Subject read-only:** nếu caller khóa `Subject.setReadOnly()`, việc thêm/gỡ Principal sẽ bị từ chối; module nên xử lý fail-closed và báo `LoginException` phù hợp.

### 6.2 Đừng gắn password thô làm Principal

Principal biểu diễn danh tính (user, service hoặc role đã xác thực), **không phải password**. Mật khẩu chỉ dùng để kiểm tra, sau đó nên dọn memory tạm; không đưa `PasswordCallback` hoặc plaintext vào `Subject.getPublicCredentials()`, log hoặc error. Với token/session key trong privateCredentials, bảo đảm vòng đời phù hợp và `Destroyable` khi loại credential hỗ trợ.

## 7. Java 17+ chạy được: LoginContext + custom LoginModule + Subject

Ví dụ này không cần file cấu hình: ta tạo `Configuration` trong code và truyền trực tiếp cho `LoginContext`. `PasswordModule` minh họa vòng đời đầy đủ; `CallbackHandler` nhận user/password từ input test; `commit` thêm `AppPrincipal`, `logout` gỡ Principal, login sai ném `FailedLoginException`.

**Lưu ý bảo mật:** `"lab-only"` là mật khẩu test được hardcode để demo. Chương trình **không** có database password hashes, Argon2id/PBKDF2, throttle hay lưu trữ thông tin đăng nhập an toàn. So sánh `char[]` chỉ nhằm kiểm chứng JAAS callback lifecycle, **không phải một password authenticator dành cho production**.

Lưu **`JaasLoginDemo.java`**:

```java
import java.security.Principal;
import java.util.*;
import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import javax.security.auth.spi.LoginModule;

public final class JaasLoginDemo {
    public record AppPrincipal(String getName) implements Principal {}

    public static final class PasswordModule implements LoginModule {
        private Subject subject;
        private CallbackHandler handler;
        private AppPrincipal pending;
        private boolean loggedIn;
        private boolean committed;

        @Override
        public void initialize(Subject subject, CallbackHandler handler,
                               Map<String, ?> sharedState,
                               Map<String, ?> options) {
            this.subject = Objects.requireNonNull(subject);
            this.handler = Objects.requireNonNull(handler);
        }

        @Override
        public boolean login() throws LoginException {
            NameCallback name = new NameCallback("Username: ");
            PasswordCallback password =
                    new PasswordCallback("Password: ", false);
            try {
                handler.handle(new Callback[]{name, password});
                char[] received = password.getPassword();
                char[] expected = "lab-only".toCharArray();
                boolean correctUser =
                        "student".equals(name.getName());
                boolean correctPassword =
                        constantTimeEqual(received, expected);
                Arrays.fill(expected, '\0');

                if (!correctUser || !correctPassword) {
                    throw new FailedLoginException("Invalid credentials");
                }
                pending = new AppPrincipal(name.getName());
                loggedIn = true;
                return true;
            } catch (UnsupportedCallbackException
                     | java.io.IOException e) {
                LoginException ex = new LoginException("Callback failed");
                ex.initCause(e);
                throw ex;
            } finally {
                password.clearPassword();
            }
        }

        private static boolean constantTimeEqual(
                char[] left, char[] right) {
            if (left == null || right == null) return false;
            int diff = left.length ^ right.length;
            int max = Math.max(left.length, right.length);
            for (int i = 0; i < max; i++) {
                char a = i < left.length ? left[i] : 0;
                char b = i < right.length ? right[i] : 0;
                diff |= a ^ b;
            }
            return diff == 0;
        }

        @Override
        public boolean commit() throws LoginException {
            if (!loggedIn) return false;
            if (subject.isReadOnly()) {
                throw new LoginException("Subject read-only");
            }
            subject.getPrincipals().add(pending);
            committed = true;
            return true;
        }

        @Override
        public boolean abort() throws LoginException {
            if (committed) return logout();
            reset();
            return true;
        }

        @Override
        public boolean logout() throws LoginException {
            if (committed) {
                if (subject.isReadOnly()) {
                    throw new LoginException("Subject read-only");
                }
                subject.getPrincipals().remove(pending);
            }
            reset();
            return true;
        }

        private void reset() {
            pending = null;
            loggedIn = false;
            committed = false;
        }
    }

    public static final class UserCallbacks implements CallbackHandler {
        private final String user;
        private final char[] pass;

        UserCallbacks(String user, char[] pass) {
            this.user = user;
            this.pass = pass.clone();
        }

        @Override
        public void handle(Callback[] callbacks)
                throws UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback name) {
                    name.setName(user);
                } else if (callback instanceof PasswordCallback pwd) {
                    pwd.setPassword(pass);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }

        void clear() {
            Arrays.fill(pass, '\0');
        }
    }

    public static final class InMemoryConfiguration
            extends Configuration {
        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(
                String name) {
            if (!"LearningApp".equals(name)) return null;
            return new AppConfigurationEntry[]{
                    new AppConfigurationEntry(
                            PasswordModule.class.getName(),
                            AppConfigurationEntry
                                    .LoginModuleControlFlag.REQUIRED,
                            Map.of())
            };
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration config = new InMemoryConfiguration();

        UserCallbacks good = new UserCallbacks(
                "student", "lab-only".toCharArray());
        try {
            LoginContext login = new LoginContext(
                    "LearningApp", null, good, config);
            login.login();
            Subject subject = login.getSubject();
            System.out.println("Login successful: "
                    + (subject != null));
            System.out.println("Principals after login: "
                    + subject.getPrincipals().stream()
                            .map(Principal::getName)
                            .sorted().toList());

            login.logout();
            System.out.println("Principals after logout: "
                    + subject.getPrincipals().size());
        } finally {
            good.clear();
        }

        UserCallbacks bad = new UserCallbacks(
                "student", "wrong".toCharArray());
        try {
            LoginContext login = new LoginContext(
                    "LearningApp", null, bad, config);
            boolean rejected = false;
            try {
                login.login();
            } catch (LoginException expected) {
                rejected = true;
            }
            System.out.println("Wrong password rejected: "
                    + rejected);
        } finally {
            bad.clear();
        }
    }
}
```

Biên dịch/chạy trong thư mục chứa file:

```bash
javac -encoding UTF-8 JaasLoginDemo.java
java JaasLoginDemo
```

Stdout trên Java 17+ (đã thử nghiệm):

```text
Login successful: true
Principals after login: [student]
Principals after logout: 0
Wrong password rejected: true
```

### 7.1 Giải thích từng lớp

- **`AppPrincipal`** là `record` triển khai `Principal`. Tên người dùng được expose qua `getName()`. Java record `equals`/`hashCode` dựa trên state nên phù hợp minh họa trong `Set<Principal>`.
- **`PasswordModule`** là public static nested class, có no-arg constructor mặc định để `LoginContext` nạp thông qua `PasswordModule.class.getName()` (dạng `JaasLoginDemo$PasswordModule` ở runtime).
- **`initialize`** nhận `Subject`/`CallbackHandler` cùng hai maps. Demo không dùng `sharedState` hoặc `options`, hợp lệ vì module có quyền bỏ qua tùy nhu cầu.
- **`NameCallback` và `PasswordCallback`** là hai object giao thức callback. Module không cần biết credential đến từ console, GUI, HTTP request đã được validate hay test handler.
- **`login`** so sánh input của test, đặt `pending` và `loggedIn` riêng trong module; **chưa thêm Principal**.
- **`commit`** chỉ thêm `pending` vào `Subject` khi bản thân module login thành công và tổng thể được JAAS chuyển đến phase commit.
- **`abort`/`logout`** dọn state của module, remove Principal mà module quản lý; không gọi `subject.getPrincipals().clear()`.
- **`InMemoryConfiguration`** trả đúng một `AppConfigurationEntry` với flag REQUIRED; một login sai dẫn đến `LoginException`.
- **`main`** dùng **hai LoginContext riêng** cho hai lần login: thành công rồi logout, sau đó login sai bị từ chối.

### 7.2 Những giới hạn bảo mật có chủ ý

**Mật khẩu demo không an toàn:** `constantTimeEqual` ở đây chỉ tránh nhánh trả sớm dựa trên nội dung ký tự; runtime/độ dài input và nhiều yếu tố khác vẫn có thể bị quan sát. Nó không thay thế **password hashing chậm**, salt ngẫu nhiên và các kiểm soát login cần thiết. Production nên dùng identity provider hoặc password verification có **Argon2id/scrypt/bcrypt/PBKDF2** theo chính sách, TLS khi truyền credential, rate limiting, audit, MFA và bảo vệ credential trong memory.

**Không lưu password trong Subject:** demo gỡ password khỏi `PasswordCallback` bằng `clearPassword()`. Handler riêng vẫn có buffer được xóa ở finally; mảng do `PasswordCallback.getPassword()` trả và các bản sao trong JVM có thể có vòng đời khác. Không đảm bảo **xóa mọi bản sao heap** chỉ bằng `Arrays.fill`.

**Không dùng module để tự phát role:** xác thực `student` không tự suy ra `role=admin`. Nếu cần roles/entitlements, chúng phải đến từ nguồn đáng tin và được kiểm tra tại thời điểm authorization.

**Lưu ý lỗi commit:** nếu Subject read-only khiến `commit` thất bại, cách module xử lý exception/rollback phải fail closed. Dù code lab đơn giản, production module cần quản lý cleanup nhất quán, không để Principal/credential sót lại sau thất bại một phần.

## 8. Java 17+: kiểm thử thứ tự các cờ bằng một LoginModule dùng lại

Một LoginModule có thể **được cấu hình nhiều lần** trong cùng stack với các options khác nhau. Ví dụ sau định nghĩa **`StepModule`** luôn quyết định kết quả theo option `allow`, đồng thời ghi tên các module mà `login()` thực sự được gọi. Nhờ vậy có thể quan sát chính xác **REQUIRED, REQUISITE, SUFFICIENT, OPTIONAL** và vị trí của mỗi module.

**Chỉ dành cho kiểm thử logic cờ**: `allow="true"` trong configuration là hành vi giả lập, hoàn toàn **không** xác thực một người thật; tuyệt đối không dùng module như thế trong production.

Lưu **`JaasStackDemo.java`**:

```java
import java.util.*;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.*;
import javax.security.auth.spi.LoginModule;

public final class JaasStackDemo {
    private static final List<String> TRACE = new ArrayList<>();

    public static final class StepModule implements LoginModule {
        private String label;
        private boolean allow;
        private boolean authenticated;

        @Override
        public void initialize(
                Subject subject, CallbackHandler handler,
                Map<String, ?> sharedState, Map<String, ?> options) {
            label = (String) options.get("label");
            allow = Boolean.parseBoolean((String) options.get("allow"));
        }

        @Override
        public boolean login() throws LoginException {
            TRACE.add(label);
            if (!allow) {
                throw new FailedLoginException("Denied by " + label);
            }
            authenticated = true;
            return true;
        }

        @Override
        public boolean commit() {
            return authenticated;
        }

        @Override
        public boolean abort() {
            authenticated = false;
            return true;
        }

        @Override
        public boolean logout() {
            authenticated = false;
            return true;
        }
    }

    static AppConfigurationEntry entry(
            String label, boolean allow,
            AppConfigurationEntry.LoginModuleControlFlag flag) {
        return new AppConfigurationEntry(
                StepModule.class.getName(),
                flag,
                Map.of(
                        "label", label,
                        "allow", Boolean.toString(allow)));
    }

    public static void main(String[] args) throws Exception {
        var required =
                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;
        var requisite =
                AppConfigurationEntry.LoginModuleControlFlag.REQUISITE;
        var sufficient =
                AppConfigurationEntry.LoginModuleControlFlag.SUFFICIENT;
        var optional =
                AppConfigurationEntry.LoginModuleControlFlag.OPTIONAL;

        run("optional_then_required",
                new AppConfigurationEntry[]{
                        entry("optional-fail", false, optional),
                        entry("required-pass", true, required)
                });

        run("required_fail_before_sufficient",
                new AppConfigurationEntry[]{
                        entry("required-fail", false, required),
                        entry("sufficient-pass", true, sufficient)
                });

        run("requisite_short_circuit",
                new AppConfigurationEntry[]{
                        entry("requisite-fail", false, requisite),
                        entry("later-pass", true, optional)
                });

        run("sufficient_short_circuit",
                new AppConfigurationEntry[]{
                        entry("sufficient-pass", true, sufficient),
                        entry("later-fail", false, required)
                });

        run("only_optional_failed",
                new AppConfigurationEntry[]{
                        entry("only-optional-fail", false, optional)
                });
    }

    static void run(String scenario, AppConfigurationEntry[] entries)
            throws Exception {
        TRACE.clear();

        Configuration config = new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(
                    String name) {
                return scenario.equals(name)
                        ? entries.clone() : null;
            }
        };

        LoginContext ctx = new LoginContext(
                scenario, null, callbacks -> {}, config);
        boolean passed;
        try {
            ctx.login();
            passed = true;
        } catch (LoginException expected) {
            passed = false;
        }
        if (passed) ctx.logout();

        System.out.println(
                scenario + ": " + passed
                        + "; ran=" + String.join(",", TRACE));
    }
}
```

```bash
javac -encoding UTF-8 JaasStackDemo.java
java JaasStackDemo
```

Stdout đã kiểm tra trên JDK:

```text
optional_then_required: true; ran=optional-fail,required-pass
required_fail_before_sufficient: false; ran=required-fail,sufficient-pass
requisite_short_circuit: false; ran=requisite-fail
sufficient_short_circuit: true; ran=sufficient-pass
only_optional_failed: false; ran=only-optional-fail
```

### 8.1 Đọc năm case theo thứ tự

**Case 1 — OPTIONAL fail, REQUIRED pass:** OPTIONAL thất bại nhưng không bắt buộc. REQUIRED thành công nên overall login thành công.

**Case 2 — REQUIRED fail, SUFFICIENT pass:** SUFFICIENT vẫn được gọi, nhưng **không thể che lỗi REQUIRED trước đó**. Kết quả false dù module sau báo thành công.

**Case 3 — REQUISITE fail:** dừng ngay phase login; `later-pass` không xuất hiện trong `TRACE`. Điều này tránh chạy những module phía sau khi một điều kiện bắt buộc không đạt.

**Case 4 — SUFFICIENT pass:** khi không có lỗi required/requisite trước, JAAS có thể kết thúc phase login sớm; `later-fail` không được gọi. Thứ tự và flag quyết định đường đi.

**Case 5 — Chỉ có OPTIONAL fail:** không có module nào xác thực thành công, nên overall login vẫn thất bại. **OPTIONAL không đồng nghĩa “cho phép login không cần xác thực”.**

**Giới hạn test:** `StepModule` không thêm Principal vào Subject; nó chỉ mô phỏng control flow. `TRACE` là static list cho single-threaded unit test, **không** phù hợp theo dõi người dùng/concurrent logins trong production. Với module thật, cần test luôn commit/abort/logout và cleanup của từng plugin.

### 8.2 Những stack đáng nghi

```text
# NGUY HIỂM nếu AuditModule trả success mà PasswordModule fail:
Service {
    com.example.AuditModule optional;
    com.example.PasswordModule optional;
};

# NGUY HIỂM nếu SufficientPassModule có thể luôn success:
Service {
    com.example.SufficientPassModule sufficient;
    com.example.RequiredPasswordModule required;
};
```

Hai block trên là **mã cấu hình minh họa, không có implementation thực tế**. Chúng cho thấy vì sao việc thay module/cờ **là thay đổi security policy**, không chỉ thay file config vô hại. Có module optional ghi log lỗi không đồng nghĩa module nên trả `true` như một proof of authentication.

## 9. Java 17+: nạp Configuration từ file jaas.conf

Hai ví dụ trước truyền `Configuration` object tường minh. Tiếp theo ta kiểm tra **default JAAS configuration parser**: ứng dụng gọi constructor `LoginContext("FileDemo", handler)`, còn module class và option được lấy từ file `jaas.conf`.

Để demo chạy độc lập, ta định nghĩa `AllowModule` là static nested class. Nó **luôn chấp nhận** user — **chỉ để chứng minh file config thực sự được đọc**, không phải một authentication mechanism.

Tạo **hai file trong cùng thư mục**.

**File 1 — `jaas.conf`**:

```text
FileDemo {
    JaasFileConfigDemo$AllowModule required principalName="from-file";
};
```

Dấu **`$`** là một phần tên JVM của nested class `JaasFileConfigDemo.AllowModule`; với class có package trong production sẽ dùng **fully qualified class name** theo classpath/modulepath đúng. `principalName` là **option tùy biến** do module của ví dụ định nghĩa, không phải key JAAS hỗ trợ tự động.

**File 2 — `JaasFileConfigDemo.java`**:

```java
import java.security.Principal;
import java.util.*;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.*;
import javax.security.auth.spi.LoginModule;

public final class JaasFileConfigDemo {
    public record AppPrincipal(String getName) implements Principal {}

    // Module "cho qua" chỉ để minh họa cấu hình file;
    // tuyệt đối không sử dụng làm cơ chế xác thực thật.
    public static final class AllowModule implements LoginModule {
        private Subject subject;
        private AppPrincipal assigned;
        private boolean loggedIn;
        private boolean committed;

        @Override
        public void initialize(
                Subject subject, CallbackHandler cb,
                Map<String, ?> shared, Map<String, ?> options) {
            this.subject = subject;
            this.assigned = new AppPrincipal(
                    (String) options.get("principalName"));
        }

        @Override
        public boolean login() {
            loggedIn = true;
            return true;
        }

        @Override
        public boolean commit() {
            if (!loggedIn) return false;
            subject.getPrincipals().add(assigned);
            committed = true;
            return true;
        }

        @Override
        public boolean abort() {
            if (committed) {
                subject.getPrincipals().remove(assigned);
            }
            loggedIn = false;
            committed = false;
            return true;
        }

        @Override
        public boolean logout() {
            if (committed) {
                subject.getPrincipals().remove(assigned);
            }
            loggedIn = false;
            committed = false;
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        LoginContext login = new LoginContext(
                "FileDemo",
                (CallbackHandler) callbacks -> {});
        login.login();
        System.out.println("Principal from file config: "
                + login.getSubject().getPrincipals()
                        .iterator().next().getName());
        login.logout();
        System.out.println("Principal count after logout: "
                + login.getSubject().getPrincipals().size());
    }
}
```

Biên dịch và truyền property **trước tên main class**:

```bash
javac -encoding UTF-8 JaasFileConfigDemo.java
java -Djava.security.auth.login.config=jaas.conf JaasFileConfigDemo
```

Stdout:

```text
Principal from file config: from-file
Principal count after logout: 0
```

**Điều chứng minh được:**

1. `LoginContext("FileDemo", handler)` không truyền config tường minh nên dùng installed Configuration theo Java.
2. File `jaas.conf` ánh xạ tên `FileDemo` tới `JaasFileConfigDemo$AllowModule` và option `principalName="from-file"`.
3. `LoginContext` nạp class, gọi `initialize` với options, sau đó gọi `login`/`commit`.
4. Module tạo `AppPrincipal("from-file")` trong `commit` và gỡ khỏi Subject trong `logout`.
5. Không có password/OTP/CA trong bài test; **kết quả thành công không phải bằng chứng xác thực thật**.

Nếu đường dẫn `jaas.conf` sai, tên entry `FileDemo` không khớp, class không tìm được hoặc file syntax sai, constructor hoặc `login()` có thể thất bại (tùy nguyên nhân và thời điểm). Cần giữ nguyên **cause** để chẩn đoán nhưng không log credential.

## 10. CallbackHandler: tách giao diện nhập credential khỏi LoginModule

`CallbackHandler` là một **cầu nối** giữa module xác thực và nguồn dữ liệu của ứng dụng. Module đưa ra yêu cầu `Callback[]` và handler cung cấp giá trị.

```text
PasswordModule.login()
     |
     +-- NameCallback("Username")
     +-- PasswordCallback("Password")
                  |
                  v
          CallbackHandler.handle(callbacks)
                  |
            +-----+--------------+
            |                    |
      Console handler       GUI / server handler
            |                    |
       user input           credential đã nhận/validate
            |                    |
            +----------+---------+
                       v
                callbacks đã điền
                       |
                       v
             module.verify(credentials)
```

Một module có thể yêu cầu callback cho **username, password, text, confirmation**, hoặc callback đặc thù khác theo provider. Handler phải nhận đúng các `Callback` hoặc ném `UnsupportedCallbackException`; không lặng lẽ trả chuỗi rỗng và coi là xác thực thành công.

**Phân biệt trách nhiệm:**

- `CallbackHandler` cung cấp dữ liệu cho module; **không** tự tạo `Subject` đã xác thực.
- `LoginModule` xác minh credential, đưa trạng thái vào phase commit.
- `LoginContext` quản lý các module theo policy và trả success/failure chung.
- `Subject` là nơi chứa identity/credentials sau khi quá trình được xác nhận.

### 10.1 Vệ sinh PasswordCallback

- Dùng `char[]` thay vì giữ password dưới dạng `String` cố định khi API cho phép.
- Gọi `PasswordCallback.clearPassword()` và dọn các buffer mà ứng dụng thực sự sở hữu; nhận biết các bản sao có thể tồn tại trong heap.
- Không lưu callback object vào static fields, không cache password trong sharedState giữa request.
- Nếu xác thực dựa trên password storage, dùng **password hashing phù hợp** như Argon2id/PBKDF2 theo yêu cầu hệ thống; **không so sánh plaintext hardcode** như demo.
- Không để messages/exception tiết lộ user tồn tại, password đúng/sai hay secret. Có retry throttling và audit tại tầng auth service.

## 11. sharedState và options: cùng là Map nhưng mục đích khác

Khi `LoginContext` khởi tạo module:

```java
void initialize(
        Subject subject,
        CallbackHandler callbackHandler,
        Map<String, ?> sharedState,
        Map<String, ?> options)
```

**`sharedState`** là **Map chung** giữa các module trong cùng login context/stack; có thể dùng để chia sẻ một số thông tin cần thiết theo hợp đồng của các module. **`options`** là **cấu hình tĩnh riêng** của mỗi `AppConfigurationEntry`.

| Thuộc tính | sharedState | options |
| --- | --- | --- |
| Nguồn | LoginContext cung cấp để các module cùng tham chiếu | `Configuration` gắn từng module entry |
| Mục đích | Chia sẻ state/credential tạm giữa modules khi thật sự cần | Realm, endpoint, mode, debug flags, timeout hoặc policy được module định nghĩa |
| Biến đổi trong login | Có thể do module ghi/đọc; cần hợp đồng và cleanup | Thông thường đọc cấu hình, không dùng như vùng state chung |
| Rủi ro | Credential bị leak sang module khác, state còn sót sau abort/logout | Hardcoded password/token hoặc option không được module hiểu |
| API | `Map<String, ?> sharedState` | `Map<String, ?> options` |

**Tên options không phải tính năng JAAS chung**. Ví dụ `useFirstPass`, `tryFirstPass`, `storePass`, `clearPass` xuất hiện ở các module/provider khác nhau, có thể dùng state/password giữa các modules theo quy ước riêng. Phải kiểm tra **tài liệu của chính LoginModule** trước khi dùng; không tin rằng đặt `useFirstPass="true"` trong mọi module đều hoạt động.

Khi chia sẻ state, định nghĩa namespace cho key, tránh va chạm, xóa secret sau khi xong và không mở rộng phạm vi tin cậy tùy tiện: một module có quyền đọc sharedState có thể nhìn thấy thông tin của module khác. **Không coi JAAS sharedState như một key vault bảo mật**.

## 12. Thay module mà không sửa ứng dụng: khả năng và giới hạn

Sức mạnh JAAS nằm ở hợp đồng:

```java
// Caller chỉ biết application name.
LoginContext lc = new LoginContext("ServiceLogin", callbackHandler);
lc.login();
// ...
lc.logout();
```

Cấu hình có thể đổi từ:

```text
ServiceLogin {
    com.example.auth.DatabasePasswordModule required;
};
```

sang:

```text
ServiceLogin {
    com.example.auth.KerberosAuthModule required;
};
```

hoặc:

```text
ServiceLogin {
    com.example.auth.PasswordModule required;
    com.example.auth.TotpModule required;
};
```

Các class **`com.example.auth.*` chỉ là ví dụ tên plugin**, không thuộc JDK. Sau khi có implementation đúng và được triển khai trên classpath/modulepath, application caller có thể giữ nguyên. Tuy nhiên, đây là **khả năng thay bằng cấu hình**, không phải lời hứa mọi plugin có semantics tương đương hoặc có thể **hot swap giữa request đang login**.

### 12.1 Những điều phải ổn định khi thay plugin

- **Cấu trúc Subject/Principal:** ứng dụng downstream kỳ vọng loại Principal nào? Nếu đổi từ `UserPrincipal` sang `KerberosPrincipal`, code authorization có cần adapter không?
- **Mức độ bảo đảm danh tính:** password, Kerberos ticket, certificate hoặc MFA có trust assumptions và assurance khác nhau; đổi plugin có thể thay threat model.
- **Nguồn credential/callback:** plugin mới yêu cầu callback khác (password, token, smart card PIN), application handler phải hỗ trợ.
- **Lỗi và timeout:** LDAP/network failure có semantics khác sai password; phải fail closed và không leak tài khoản.
- **LoginModule options:** `debug`, `realm`, `issuer`, `useFirstPass` là provider-specific.
- **Lifecycle state:** module mới phải tuân thủ login/commit/abort/logout, rollback và không rò session.
- **Hiệu năng/concurrency:** authenticate đến service ngoài có timeout, circuit breakers, pool hoặc per-request sessions thích hợp.
- **Authorization mapping:** Principal đã xác thực không tự cấp quyền; phải mapping danh tính/roles từ nguồn đáng tin cậy.
- **Test/release:** không đưa module mới vào production chỉ bằng sửa JAAS config chưa có regression tests về flags và negative cases.

### 12.2 Java modules và class loading

Một LoginModule được nêu bằng **tên class fully qualified** trong Configuration. Runtime phải **load được class**, nó phải public hoặc truy cập được theo cơ chế của provider, triển khai đúng `LoginModule` và có public no-arg constructor. Khi Java Module System (JPMS) được sử dụng, còn cần packaging/access/exports/opens phù hợp theo cơ chế provider. Không nhầm **JAAS LoginModule** với **`module-info.java`** của JPMS: từ “module” ở đây chỉ **plugin xác thực**, không nhất thiết là Java Platform Module.

**Báo lỗi “module class not found”** cần kiểm tra JAR trên classpath/modulepath, tên class, quyền truy cập và provider; không phải lỗi password.

## 13. Luồng lỗi và fail-closed

| Lỗi/hiện tượng | Xuất hiện ở đâu | Ý nghĩa và xử lý |
| --- | --- | --- |
| `LoginException` khi tạo `LoginContext` | Tên ứng dụng không có entry và `other` không hợp lệ, config/class vấn đề tùy implementation | Kiểm tra `name`, file config, installed Configuration và class name |
| `FailedLoginException` | `LoginModule.login()` | Credential bị từ chối; hiển thị thông báo chung, không lộ user/password |
| `UnsupportedCallbackException` | `CallbackHandler.handle` | Handler không hỗ trợ callback type module yêu cầu; chỉnh bridge/UI, không bỏ qua |
| `IOException` khi handler lấy dữ liệu | Callback/input credential | Lỗi I/O; chuyển thành `LoginException` có cause an toàn |
| Exception khi `commit` | Subject read-only, quyền/provider, lỗi thêm Principal/credential | Fail closed, rollback/abort, không để lại identity nửa vời |
| Không gọi module sau trong stack | `REQUISITE` fail hoặc `SUFFICIENT` pass hợp lệ | Có thể là **đúng control flow**, không phải module “bị mất” |
| Module trả `true` nhưng `LoginContext.login()` thất bại | Có REQUIRED/REQUISITE khác thất bại hoặc phase commit lỗi | Kiểm tra cả stack, không quy kết riêng module |
| `logout()` thất bại | Module không dọn được token/session/Subject | Ghi lại và xử lý lifecycle an toàn, tránh dùng session như còn hợp lệ |
| App login thành công nhưng Principal rỗng | Module có thể không gắn Principal hoặc commit logic sai | Kiểm tra module contract, không tự coi success là quyền |
| Cấu hình file thay đổi nhưng kết quả không đổi | Context/provider dùng snapshot, cache hoặc config tường minh | Xác minh config source, `refresh`, context mới và strategy rollout |

**Fail closed:** khi thiếu module, không tìm được config, callback bị lỗi, identity provider không phản hồi hoặc password hashing backend unavailable, không tự tạo một `AllowAllLoginModule` để dịch vụ chạy được. Nếu app cần degraded mode, phải có threat model và policy tường minh **không giảm yêu cầu xác thực ngoài ý muốn**.

### 13.1 Cách ghi log an toàn

Có thể log (với policy bảo vệ): mã tình huống, service/realm, module class, login result, duration, error reason tổng quát, trace ID. **Không log** password/OTP/private credentials, toàn bộ `sharedState`, callback payload, raw token hoặc thông tin có thể trở thành oracle tài khoản. Tính năng `debug` của module cần được xem tài liệu provider trước khi bật.

### 13.2 Retry, lockout và chống brute force

JAAS quy định SPI và vòng đời, nhưng **không triển khai sẵn** rate-limit toàn ứng dụng, lockout, account recovery hay password policy. Cần thực hiện ở identity service/gateway/backend theo threat model:

- Giới hạn lần thử theo user và các tín hiệu phù hợp, chống enumeration;
- Dùng password hashing có salt/cost phù hợp, tránh plaintext hoặc fast SHA-256 đơn thuần;
- Xác thực credential qua TLS hoặc kênh được bảo vệ;
- Quản lý MFA, thiết bị tin cậy, recovery và revoke sessions;
- Audit các sự kiện cần thiết nhưng loại secret khỏi logs.

## 14. Authentication không đồng nghĩa Authorization

`LoginContext.login()` xác nhận **người hoặc service này đã vượt qua cơ chế xác thực được cấu hình**. Nó không tự cho phép đọc database hay chuyển tiền.

```text
Authentication:
    credentials + LoginModules -> Subject/Principals

Authorization:
    authenticated identity + resource/action + policy
        -> ALLOW or DENY
```

Một `Subject` có `AppPrincipal("student")` không chứng minh student được gọi admin API. Hệ thống cần authorization riêng, ví dụ RBAC/ABAC hoặc rule engine/identity policy phù hợp. Các khái niệm authorization cổ điển của JAAS từng liên quan `Subject.doAs`, `AccessControlContext` và SecurityManager; **Security Manager đã bị deprecated for removal trong JDK 17** và không nên chọn mô hình `java.security.Policy`/SecurityManager cũ làm nền tảng bảo vệ mới. Hành vi các API liên quan thay đổi theo bản JDK, vì vậy kiểm tra docs của runtime nếu gặp code legacy.

**Tư duy đúng trong ứng dụng hiện đại:** JAAS có thể xác thực và sinh Principal; sau đó framework/security layer độc lập đánh giá quyền truy cập theo token/identity đã được xác thực, không giả định principal name tự cấp quyền.

## 15. Kịch bản kiểm thử bắt buộc khi dùng LoginModule stack

| Kịch bản | Kết quả mong muốn |
| --- | --- |
| Password hợp lệ; module REQUIRED thành công | `LoginContext.login()` thành công, `Subject` có Principal đúng |
| Password sai | `LoginException`, không có Principal mới được commit |
| Một REQUIRED fail, SUFFICIENT sau thành công | **Overall fail**; lỗi required không bị che |
| Một REQUISITE fail | **Dừng** module phía sau, fail và cleanup |
| SUFFICIENT thành công trước các module phía sau và không có required lỗi trước | Có thể **kết thúc sớm**, test không bỏ qua bắt buộc ngoài ý muốn |
| OPTIONAL fail nhưng REQUIRED pass | Overall pass nếu mọi bắt buộc khác đạt |
| Tất cả OPTIONAL fail | Overall fail |
| `commit` thất bại giữa chừng | Rollback đúng, không để credential/Principal sót |
| `abort` sau một module đã login thành công | Dọn pending state và secret, Subject không bị cập nhật sai |
| `logout` | Remove đúng Principal của module; không xóa Principal của module khác |
| `CallbackHandler` thiếu callback type | Lỗi rõ, không bypass |
| Configuration sai app name hoặc fallback `other` | Kết quả theo policy mong muốn, không tự chuyển sang auth yếu |
| Config options không hợp lệ | Fail closed hoặc ghi lỗi cấu hình, không ngầm chấp nhận |
| Nhiều người dùng đăng nhập đồng thời | Không rò `sharedState`/credential giữa contexts, không dùng static state |
| Thay module/password provider | Kiểm thử contract Subject, flags, timeout, error và authorization |
| Subject read-only | Module không thể thêm Principal; phải thất bại an toàn và dọn state |

Các chương trình ở mục 7–9 là **unit/behavior demos**. Một dự án production cần thêm integration tests với identity provider thật, test mạng lỗi, load/concurrency, secret rotation, negative cases, kiểm thử phân quyền và audit.

## 16. Câu hỏi tự luyện có lời giải

**Câu 1.** Ứng dụng gọi `new LoginContext("Portal", handler)`. Class LoginModule nào được chọn?  
**Đáp:** Do **installed `Configuration`** trả cho entry `"Portal"` quyết định. Nếu không có, theo contract JAAS còn có thể tra entry `"other"`; không được đoán từ tên class trong ứng dụng.

**Câu 2.** `LoginContext` có tự kiểm tra username/password không?  
**Đáp:** Không. Nó điều phối các **LoginModule**; module thực hiện kiểm chứng credential theo công nghệ và policy của nó.

**Câu 3.** `login()` của PasswordModule trả true, nhưng OTP module REQUIRED thất bại. Có nên thêm `UserPrincipal` vào Subject không?  
**Đáp:** Không. **Overall login thất bại**, `abort` dọn state; chỉ `commit` sau overall success mới gắn Principal phù hợp.

**Câu 4.** `REQUIRED` thất bại rồi `SUFFICIENT` thành công, kết quả?  
**Đáp:** **Thất bại**. SUFFICIENT không bỏ qua REQUIRED thất bại trước đó.

**Câu 5.** `REQUISITE` thất bại, module kế tiếp có được gọi không?  
**Đáp:** Không trong phase login stack; fail sớm. Module lifecycle cleanup/abort vẫn cần được thực hiện đúng.

**Câu 6.** `SUFFICIENT` thành công đầu stack, phía sau có REQUIRED. Có thể module sau không chạy không?  
**Đáp:** Có. Nếu không có required/requisite thất bại phía trước, SUFFICIENT có thể kết thúc sớm. Đó là lý do **thứ tự và flag là security-critical**.

**Câu 7.** Có một `AuditModule OPTIONAL` trả true, còn `PasswordModule OPTIONAL` fail. Có thể login pass không?  
**Đáp:** Có thể theo quy tắc stack không có REQUIRED/REQUISITE nếu một OPTIONAL thành công. Đó có thể là **lỗ hổng policy nghiêm trọng**; phải thiết kế flag và semantics module đúng.

**Câu 8.** `sharedState` và `options` khác nhau thế nào?  
**Đáp:** `sharedState` là Map được **chia sẻ** giữa modules trong cùng context; `options` là cấu hình **riêng của từng AppConfigurationEntry**. Cả hai đều không phải secret vault.

**Câu 9.** Sau login, Subject có `AppPrincipal("student")`. Có đủ quyền admin?  
**Đáp:** Không. **Authentication** không phải **Authorization**; quyền cần xác minh theo policy đáng tin riêng.

**Câu 10.** Muốn thay module xác thực password bằng Kerberos, có thể chỉ đổi config?  
**Đáp:** Về kiến trúc **có thể**, nếu plugin Kerberos được triển khai và caller/callback/Subject/authorization contract phù hợp. Không phải cứ đổi tên class là tự có Kerberos, hay bảo đảm bảo mật tương đương.

**Câu 11.** Gọi `Configuration.setConfiguration(...)` cho một test trong JVM chạy nhiều app có tác động gì?  
**Đáp:** Có thể thay **installed global configuration** và ảnh hưởng những caller khác. Thường nên truyền Configuration riêng vào constructor bốn tham số.

**Câu 12.** Mật khẩu từ PasswordCallback đã được so sánh và login thành công. Có nên giữ nó trong publicCredentials?  
**Đáp:** Không. Dọn credential tạm; nếu cần session credential thì dùng đối tượng được bảo vệ với lifecycle/quyền truy cập rõ.

**Câu 13.** `LoginContext.login()` thành công thì `logout()` có tự revoke JWT do dịch vụ ngoài cấp không?  
**Đáp:** Không tự động. LoginModule/provider phải hiện thực cleanup/revocation hoặc application cần cơ chế lifecycle riêng.

**Câu 14.** JAAS `LoginModule` và Java `module-info.java` có phải cùng một khái niệm không?  
**Đáp:** Không. JAAS LoginModule là **plugin xác thực** theo SPI; JPMS module là **đơn vị đóng gói và encapsulation của Java Platform Module System**.

## 17. Checklist thiết kế LoginModule thay thế được

1. **Định nghĩa authentication contract**: application name, loại identity, Principal classes, callback types, assurance level và metadata cho authorization.
2. **Viết LoginModule có no-arg constructor** và tuân thủ đủ `initialize/login/commit/abort/logout`.
3. **Chỉ commit sau overall success**; login giữ pending state riêng; abort/logout dọn đúng state/Principals do module quản lý.
4. **Thiết kế Configuration có thứ tự và flags an toàn**: REQUIRED/REQUISITE/SUFFICIENT/OPTIONAL, không để module phụ trợ thay thế proof of authentication.
5. **Tránh secrets trong options, Source, logs**; dùng backend secret management, password hashing thích hợp, TLS và rate limiting.
6. **Cẩn thận với fallback `other`**, global Configuration và phiên bản/hot reload; ưu tiên config riêng cho ứng dụng khi cần isolation.
7. **Kiểm soát sharedState**: phân quyền/namespace/cleanup, không chuyển secret cho module không tin cậy.
8. **Phân biệt Subject identity và quyền**: bảo đảm authorization policy độc lập, kiểm tra role/tenant/resource/action ở mỗi điểm cần thiết.
9. **Kiểm thử đúng JDK/provider/packaging**: class loading, classpath/modulepath, callback, flags, phase rollback và concurrent requests.
10. **Bảo đảm failure-safe**: network/LDAP/Kerberos lỗi không bị biến thành success; exception sanitization, audit, lockout.
11. **Không dùng SecurityManager legacy làm mô hình bảo vệ mới**; đọc tài liệu JDK runtime khi duy trì legacy JAAS authorization.
12. **Có kế hoạch thay module**: test plugin mới, mapping Principal, dry-run/canary, rollback, rotation credential và giám sát lỗi.

## 18. Tài liệu chính thức

- [Java SE 17 — `LoginContext`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/LoginContext.html): constructor, configuration fallback, `login`/`logout` và `getSubject`.
- [Java SE 17 — `Configuration`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/Configuration.html): cú pháp module stack, control flags, `getAppConfigurationEntry`, installed configuration và `refresh`.
- [Java SE 17 — `AppConfigurationEntry`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/AppConfigurationEntry.html): module class name, cờ và options.
- [Java SE 17 — `LoginModule`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/spi/LoginModule.html): hợp đồng SPI của năm phương thức.
- [Java SE 17 — `Subject`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/Subject.html) và [`Principal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Principal.html): identity, principals và credentials.
- [Java SE 17 — `CallbackHandler`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/callback/CallbackHandler.html), [`NameCallback`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/callback/NameCallback.html), [`PasswordCallback`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/callback/PasswordCallback.html): cơ chế nhận thông tin từ nguồn credential.
- [Oracle JDK 17 — JAAS LoginModule Developer’s Guide](https://docs.oracle.com/en/java/javase/17/security/java-authentication-and-authorization-service-jaas-loginmodule-developers-guide1.html): cấu trúc một module, hai phase, callback, cleanup, testing.
- [Java SE 17 — `ConfigFile`](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.security.auth/com/sun/security/auth/login/ConfigFile.html): implementation cấu hình JAAS dựa trên file.
- [JEP 411 — Deprecate the Security Manager for Removal](https://openjdk.org/jeps/411): lưu ý về các mẫu JAAS authorization dựa vào SecurityManager ở JDK cũ.

**Ghi nhớ:** `LoginContext` là **bộ điều phối**; `Configuration` là **danh sách plugin + thứ tự + chính sách cờ**; `LoginModule` là **cơ chế xác thực thay thế được**; `Subject` mang danh tính được commit khi login chung thành công. Nhờ sự tách biệt này, Java có thể hỗ trợ nhiều công nghệ xác thực mà caller không cần ràng buộc trực tiếp vào công nghệ đó — nhưng **cấu hình flags, callback, xử lý credential, rollback và authorization** vẫn là các yêu cầu bảo mật cốt lõi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jaas-authentication-004">17.4 CallbackHandler tách thu thập thông tin đăng nhập khỏi cơ chế xác thực</a>

<details>
<summary>Xem chi tiết</summary>

# 17.4 CallbackHandler tách thu thập thông tin đăng nhập khỏi cơ chế xác thực

Trong Java Authentication and Authorization Service (**JAAS**), người lập trình thường muốn xác thực một người dùng nhưng **không muốn mô-đun xác thực phụ thuộc trực tiếp vào giao diện nhập liệu**. Một ứng dụng desktop có ô mật khẩu, một chương trình dòng lệnh có `Console`, một web service nhận request đã qua HTTPS, và một service có secrets từ vault có cách thu thập thông tin rất khác nhau. Nếu mô-đun xác thực tự gọi `System.in`, tự hiển thị dialog hoặc đọc HTTP request, logic kiểm credentials sẽ bị buộc cứng vào môi trường và khó kiểm thử/thay đổi.

**`javax.security.auth.callback.CallbackHandler` là ranh giới tách việc *cung cấp thông tin theo yêu cầu* khỏi việc *kiểm chứng thông tin và quyết định đăng nhập*.** `LoginModule` mô tả nó cần dữ liệu nào bằng các `Callback` như `NameCallback`, `PasswordCallback`; `CallbackHandler` của ứng dụng điền dữ liệu vào các callback đó; còn mô-đun xác thực kiểm chứng với nguồn tin cậy rồi báo thành công/thất bại cho `LoginContext`. Sau khi JAAS hoàn tất bước `commit()`, kết quả danh tính được thể hiện dưới dạng `Subject` và `Principal`.

Điểm mấu chốt:

> **Handler thu thập hoặc cung cấp credentials; LoginModule kiểm chứng credentials. Handler không tự biến dữ liệu đầu vào thành danh tính đáng tin.**

Bài học giải thích hợp đồng API, luồng đăng nhập, cách quản lý mật khẩu kiểu `char[]`, các tình huống callback không hỗ trợ, quan hệ với `login/commit/abort/logout`, trường hợp interactive và headless, và một chương trình **Java 17+** biên dịch/chạy độc lập. Ví dụ sử dụng dữ liệu giả **chỉ cho lab**, không phải hướng dẫn hard-code mật khẩu sản xuất.

## 1. Mục tiêu học tập

Hoàn thành bài này, bạn có thể:

1. Mô tả vai trò của `LoginContext`, `Configuration`, `LoginModule`, `CallbackHandler`, `Callback`, `Subject` và `Principal` trong JAAS.
2. Giải thích vì sao `NameCallback` chứa tên đăng nhập và `PasswordCallback` chứa credential dạng `char[]` nhưng **không làm nhiệm vụ xác thực**.
3. Triển khai `CallbackHandler.handle(Callback[])` hỗ trợ đúng các loại callback đã được thỏa thuận và ném `UnsupportedCallbackException` nếu gặp loại không hỗ trợ.
4. Xử lý `IOException`, `UnsupportedCallbackException`, `FailedLoginException`, `LoginException` và trường hợp người dùng hủy nhập liệu mà không trả thông báo lộ thông tin đăng nhập.
5. Giải thích `PasswordCallback.setPassword()` / `getPassword()` / `clearPassword()`, sự tồn tại của **nhiều bản sao char[]** và yêu cầu xóa từng bản sao theo quyền sở hữu.
6. Xây luồng JAAS `initialize → login → commit` hoặc `abort` và `logout`, biết khi nào `Principal` được thêm/loại khỏi `Subject`.
7. Thay đổi nguồn credentials từ CLI sang UI, request hoặc secret manager mà không sửa logic xác thực cốt lõi.
8. Phân biệt demo so sánh password trong RAM với hệ production phải có password hashing/KDF, rate limiting, TLS, MFA/challenge, audit và authorization.
9. Viết test cho đăng nhập đúng/sai, handler thiếu, callback lạ, cleanup, logout và khả năng tái sử dụng cơ chế xác thực với nhiều handler.

## 2. Vị trí của CallbackHandler trong kiến trúc JAAS

Một sơ đồ luồng điển hình:

```text
                     ỨNG DỤNG
       (CLI / desktop / web / worker / secret provider)
                          |
                          | tạo CallbackHandler thích hợp
                          v
                 JAAS LoginContext
                          |
                          | load LoginModule từ Configuration
                          | initialize(Subject, handler, sharedState, options)
                          v
                     LoginModule
                          |
                          | "Tôi cần tên đăng nhập và mật khẩu"
                          v
              Callback[] {NameCallback,
                          PasswordCallback}
                          |
                          | handler.handle(callbacks)
                          v
                   CallbackHandler
                          |
               thu thập / lấy dữ liệu đầu vào
                          |
                          | setName(...), setPassword(...)
                          v
                     LoginModule
                          |
                verify với nguồn tin cậy
                          |
          +---------------+----------------+
          |                                |
       thành công                        thất bại
          |                                |
         commit()                        abort()
          |                                |
          v                                v
    Subject có Principal             Không cấp danh tính
          |
       logout()
          |
    Dọn Principal/credentials theo chính sách
```

Luồng hoạt động ngược với cách lập trình UI trực tiếp: **mô-đun xác thực chủ động yêu cầu thông tin thông qua callback object, còn ứng dụng quyết định làm thế nào để lấy thông tin đó**. Nhờ vậy cùng một `LoginModule` có thể sử dụng nhiều `CallbackHandler` khác nhau.

### Ranh giới trách nhiệm

| Thành phần | Nhiệm vụ | Không nên làm |
| --- | --- | --- |
| `LoginContext` | Điều phối các LoginModule theo JAAS Configuration; thực hiện `login()` và `logout()` | Không tự hiển thị form hay xác minh password hash |
| `Configuration` | Liệt kê LoginModule, control flags, options cho ứng dụng login | Không dùng làm kho mật khẩu plaintext |
| `LoginModule` | Yêu cầu callbacks, xác minh credentials, xác định kết quả, đóng góp Principal/credentials vào Subject | Không gọi trực tiếp Swing/console/HTTP nếu muốn dùng lại |
| `CallbackHandler` | Điền dữ liệu cho callback types được hỗ trợ; giao tiếp UI/secret source của ứng dụng | Không tự cấp Principal “đã xác thực” chỉ vì được nhập tên |
| `NameCallback` | Mang prompt/default name và nhận tên bằng `setName` | Không thực hiện lookup tài khoản đáng tin |
| `PasswordCallback` | Mang prompt/echo setting và nhận mật khẩu kiểu `char[]` | Không kiểm password hash hay tự bảo vệ credential ở nguồn bên ngoài |
| `Subject` | Chứa tập Principal và public/private credentials đã được mô-đun thêm theo lifecycle | Không mặc định mọi Principal do bất kỳ code thêm đều đã được xác thực |
| `Principal` | Đại diện cho một danh tính/name theo loại cụ thể | Không đại diện cho mật khẩu; không bao hàm mọi quyền |

**Nguồn dữ liệu đầu vào chưa đáng tin:** Nếu một HTTP client gửi username và password, CallbackHandler có thể copy chúng vào callbacks; nhưng dữ liệu chỉ thành kết quả authentication sau khi `LoginModule` kiểm chứng dựa trên nguồn **server-side** có quyền và `LoginContext` hoàn tất theo control flags.

## 3. Interface CallbackHandler và hợp đồng gọi

Java 17 định nghĩa giao diện trung tâm:

```java
public interface CallbackHandler {
    void handle(Callback[] callbacks)
            throws IOException, UnsupportedCallbackException;
}
```

Đây là chữ ký API, không phải file Java độc lập. Mỗi phần tử trong `Callback[]` mô tả một nhu cầu dữ liệu hoặc tương tác. Một handler thường duyệt từng phần tử và xử lý theo kiểu:

```java
// Minh họa trong một method của CallbackHandler, có username/secret hợp lệ.
for (Callback callback : callbacks) {
    if (callback instanceof NameCallback name) {
        name.setName(username);
    } else if (callback instanceof PasswordCallback password) {
        password.setPassword(secret);
    } else {
        throw new UnsupportedCallbackException(callback);
    }
}
```

**Handler không return một object credentials riêng.** Nó cập nhật các `Callback` được truyền vào. `LoginModule` lấy dữ liệu từ `NameCallback.getName()` và `PasswordCallback.getPassword()` sau khi `handle` trả về. Đây là cách tách **nguồn của dữ liệu** khỏi **cơ chế kiểm chứng**.

### Vì sao `Callback[]` là một mảng?

LoginModule có thể cần nhiều thông tin trong một lần xử lý:

- Tên đăng nhập (NameCallback).
- Mật khẩu hoặc một bí mật nhạy cảm (PasswordCallback).
- Chọn một realm hoặc phương án xác thực (ChoiceCallback).
- Nhập thêm thông tin ít nhạy cảm (TextInputCallback).
- Hiển thị cảnh báo/hướng dẫn (TextOutputCallback).
- Xác nhận tiếp tục/hủy bỏ (ConfirmationCallback).
- Các callback chuyên biệt hoặc tùy biến theo provider.

Gọi handler với nhiều callback giúp UI có thể **gom thành một form duy nhất**. Tuy nhiên mỗi `CallbackHandler` phải công khai tập callback types nó thực sự xử lý. Việc nhận một callback lạ và âm thầm bỏ qua có thể khiến LoginModule tưởng đã nhận dữ liệu đầy đủ trong khi thực tế chưa có.

### Không làm gì khi callback không hỗ trợ?

Hợp đồng chuẩn cho callback không hỗ trợ là ném `UnsupportedCallbackException`, có thể kèm callback gây lỗi. LoginModule có thể bắt lỗi rồi chuyển thành một `LoginException` phù hợp. **Đừng** coi callback không hiểu là “đăng nhập thành công” hoặc lặng lẽ trả null cho phần tử quan trọng.

Nếu handler không thể lấy dữ liệu vì I/O, người dùng đóng màn hình, stream bị lỗi hoặc secret manager không khả dụng, nó có thể ném `IOException` hoặc cơ chế báo lỗi phù hợp với contract; mô-đun xác thực nên **fail closed**, không fallback sang tài khoản mặc định.

## 4. NameCallback: tên định danh không phải bằng chứng xác thực

`NameCallback` thường chứa prompt cho tên đăng nhập và có thể có default name. Các API:

```java
NameCallback callback = new NameCallback("User: ");
callback.setName("alice");
String submittedName = callback.getName();
```

Đây là ví dụ biểu diễn dữ liệu, chưa có phép xác thực. Tên `"alice"` do client cung cấp **không đủ** để kết luận caller là Alice.

Lưu ý:

- `getPrompt()` dành cho người dùng hoặc hệ thống cung cấp credentials; không để prompt điều khiển việc truy cập nguồn dữ liệu không tin cậy.
- `getDefaultName()` là gợi ý UI nếu constructor có default; không phải danh tính được chứng minh.
- Username là `String`, thường không được xem là password secret, nhưng có thể là PII; vẫn cần log/retention policy.
- Normalize/canonicalize tên đăng nhập theo chính sách danh tính (Unicode, case, realm, tenant) **ở phía verifier**, không tự trim/lowercase mọi username tùy tiện.
- Nếu credential store hỗ trợ nhiều tenant, không để handler chỉ đặt một tên rồi vô tình lookup sang tenant khác mà không xác thực tenant scope.
- Không gán `Subject.getPrincipals().add(new UserPrincipal(name))` từ UI handler trước khi credential được kiểm chứng.

## 5. PasswordCallback: truyền mật khẩu bằng char[] và giới hạn thực tế

`PasswordCallback` có constructor `PasswordCallback(String prompt, boolean echoOn)`. Cờ `echoOn` là chỉ dẫn giao diện về việc có hiển thị ký tự người dùng nhập hay không; nó **không phải cơ chế mã hóa** và không ép mọi GUI/terminal phải tuân tuyệt đối nếu handler bỏ qua. Với mật khẩu, thông thường dùng `false`.

```java
PasswordCallback callback = new PasswordCallback("Password: ", false);
char[] secret = {'p', 'a', 's', 's'};
try {
    callback.setPassword(secret);
    char[] provided = callback.getPassword();
    try {
        // Chỉ giao cho verifier hợp lệ; không log, không chuyển thành String.
    } finally {
        if (provided != null) Arrays.fill(provided, '\0');
    }
} finally {
    callback.clearPassword();
    Arrays.fill(secret, '\0');
}
```

Đoạn trên là mã minh họa, cần import `java.util.Arrays` và `javax.security.auth.callback.PasswordCallback`. Đây là **secret giả** trong bài học, không phải password mẫu dùng sản xuất.

### Sao chép và xóa mật khẩu: phải nắm rõ ownership

Với implementation JDK thông dụng:

1. `PasswordCallback.setPassword(char[])` lưu một **bản sao** của mảng truyền vào, không chiếm ownership mảng gốc.
2. `PasswordCallback.getPassword()` trả một **bản sao** để bên nhận xử lý; caller phải tự xóa mảng này.
3. `PasswordCallback.clearPassword()` xóa/ghi đè nội dung mật khẩu đang được giữ trong callback.
4. Việc gọi `clearPassword()` **không** xóa mảng ban đầu trong UI/handler hoặc những bản sao mà `getPassword()` đã trả.
5. Trong JDK 17 được thử nghiệm, sau `clearPassword()`, `getPassword()` có thể trả một mảng đã bị ghi đè bằng ký tự trắng thay vì `null`. Do đó **không kiểm “đã xóa” bằng biểu thức `getPassword() == null`**.
6. Java không hứa xóa mọi bản sao do GC/runtime/native/UI/encoding có thể tạo; sử dụng `char[]` là phương án **giảm thời gian tồn tại** của plaintext trong bộ nhớ, không phải cam kết memory erasure tuyệt đối.
7. Không nên chuyển mật khẩu sang `String` để tiện log/compare vì `String` bất biến và khó xóa có chủ đích. Hệ thống nhập liệu, thư viện HTTP hoặc framework có thể đã tạo String; cần hiểu nguồn và giảm bản sao ở những ranh giới bạn kiểm soát được.

### Trình tự cleanup đúng

```text
UI/source secret char[]   --clone--> Handler-owned char[]
                                  |
                                  | PasswordCallback.setPassword(...)
                                  v
                            Callback internal char[]
                                  |
                                  | getPassword()
                                  v
                            LoginModule local char[]
                                  |
                                  v
                             verifier

Sau xử lý:
  - LoginModule Arrays.fill(localCopy, '\0')
  - LoginModule PasswordCallback.clearPassword()
  - Handler.close() Arrays.fill(ownedCredential, '\0')
  - Application Arrays.fill(sourceSecret, '\0')
```

Không log nội dung của `PasswordCallback` hoặc dùng `Arrays.toString(password)`. Không đặt password trong exception message, `Subject.Principal`, JAAS config file, JVM system property hoặc field lưu dài hạn của LoginModule.

## 6. Sự khác nhau giữa handler và verifier

Xem hai cách viết.

**Thiết kế kết dính (không khuyến nghị):**

```java
// Chỉ minh họa anti-pattern, không phải chương trình độc lập.
boolean login() {
    String name = console.readLine("User: ");
    char[] password = console.readPassword("Password: ");
    return callCredentialDatabase(name, password);
}
```

Mô-đun xác thực ở đây phụ thuộc vào `console`. Khi ứng dụng chạy trong servlet không có console, một bài test không có terminal, hoặc worker dùng secret manager, bạn phải sửa code xác thực.

**Thiết kế tách biệt với JAAS:**

```text
LoginModule:
    NameCallback + PasswordCallback
        -> handler.handle(...)
        -> verifier.verify(name, password)
        -> commit Principal nếu authentication thành công

CLI CallbackHandler:
    console.readLine/readPassword

GUI CallbackHandler:
    input từ form hiển thị theo UI framework

Web CallbackHandler:
    dữ liệu từ request đã qua HTTPS và session/auth flow đúng

Service CallbackHandler:
    lấy secret từ vault hoặc identity bootstrap theo chính sách
```

Verifier được xây từ **credential store đáng tin**; không lấy expected password từ cùng request đầu vào của attacker. CallbackHandler có thể được thay mà không phải thay policy kiểm chứng. Đây là lợi ích về **separation of concerns**, testability, portability và quản trị trust boundary.

## 7. LoginContext và Configuration điều phối ra sao?

`LoginContext` điều phối một hoặc nhiều `LoginModule` dựa trên `Configuration`. Một entry thường chứa:

- Tên class của LoginModule.
- Control flag: `REQUIRED`, `REQUISITE`, `SUFFICIENT` hoặc `OPTIONAL`.
- Options của module, chẳng hạn tên realm hoặc identifier của credential service, nhưng **không phải mật khẩu plaintext**.

Ví dụ pseudo-config:

```text
sample {
    com.example.security.DirectoryLoginModule required
      realm="members";
};
```

Đây là **cú pháp ý tưởng** cho JAAS config; class trên chỉ là tên minh họa, không phải class có sẵn trong JDK. Chương trình hoàn chỉnh ở mục 10 dùng `Configuration` tạo bằng Java để không cần file ngoài.

Ví dụ khởi tạo:

```java
// Minh họa, giả sử subject/handler/config đều có nguồn tin cậy.
LoginContext loginContext =
        new LoginContext("sample", subject, handler, config);

loginContext.login();             // điều phối LoginModule.login + commit/abort
Subject authenticated = loginContext.getSubject();
loginContext.logout();            // gọi logout các modules đã tham gia
```

### Control flags ảnh hưởng kết quả tổng thể

| Flag | Ý nghĩa tóm tắt | Rủi ro khi hiểu sai |
| --- | --- | --- |
| `REQUIRED` | Module phải thành công; dù fail, các module khác trong chuỗi có thể vẫn được thử trước khi báo failure | Tưởng login thành công do module sau pass dù module required fail |
| `REQUISITE` | Module phải thành công; failure thường làm toàn login kết thúc ngay | Bỏ qua việc fail closed cho bước bắt buộc |
| `SUFFICIENT` | Thành công có thể cho phép kết thúc sớm nếu chưa có required/requisite failure cần xét | Dùng sai để bypass bước xác thực/kiểm chính sách bắt buộc |
| `OPTIONAL` | Đóng góp vào policy tổng thể theo quy tắc JAAS; không mặc nhiên bắt buộc | Tưởng optional module đảm bảo MFA hoặc policy bắt buộc |

Kết quả **của một LoginModule** không đồng nghĩa kết quả **toàn bộ LoginContext** khi có nhiều module. Đừng tự lắp nhiều bước password/OTP bằng các control flags mà không hiểu xử lý lỗi, thứ tự, short-circuit và `commit/abort`. Một mô hình MFA đúng cần protocol và binding giữa các bước, không chỉ có hai ô nhập.

### CallbackHandler có thể được truyền vào nhiều modules

`LoginContext` có thể sử dụng cùng handler cho các LoginModule trong cấu hình, nhưng callback types mà mỗi module yêu cầu có thể khác. Nếu module mới yêu cầu `TextInputCallback` mà handler chỉ hỗ trợ Name/Password, `handle` có thể ném `UnsupportedCallbackException`. Đây là tín hiệu tích hợp phải cập nhật hoặc cấu hình lại handler **có chủ đích**, không phải lý do âm thầm bỏ qua bước mới.

## 8. LoginModule lifecycle: initialize, login, commit, abort, logout

Đối với `javax.security.auth.spi.LoginModule`, những phương thức cốt lõi:

```java
void initialize(Subject subject, CallbackHandler handler,
        Map<String, ?> sharedState, Map<String, ?> options);
boolean login() throws LoginException;
boolean commit() throws LoginException;
boolean abort() throws LoginException;
boolean logout() throws LoginException;
```

Ý nghĩa:

1. `initialize` nhận `Subject`, handler, sharedState và options. Không nên lập tức công bố Principal chưa xác thực.
2. `login` yêu cầu callbacks, kiểm credentials và ghi trạng thái tạm của module. Thành công chưa nhất thiết đồng nghĩa nên thêm Principal ngay; LoginContext còn xử lý kết quả nhiều modules.
3. `commit` được gọi khi JAAS quyết định đăng nhập tổng thể thành công, mô-đun có thể thêm `Principal` và credentials thích hợp vào `Subject`.
4. `abort` được gọi khi đăng nhập tổng thể thất bại; mô-đun phải bỏ state tạm và rollback những thay đổi đã commit nếu áp dụng.
5. `logout` dọn identity/credentials mà module **chính nó** đã đóng góp, và kết thúc session theo hợp đồng.

**Chỉ commit sau xác thực.** Một nguyên nhân phổ biến của lỗi an ninh là handler được nhập username rồi thêm ngay Principal vào Subject, trong khi password check sau đó fail. Tách callback collection khỏi authentication và commit giúp tránh trạng thái danh tính nửa vời.

### Subject và Principal không phải nơi lưu mật khẩu

`Subject` có tập `Principal`, public credentials và private credentials. Mô-đun chỉ thêm những gì cần theo policy và có nghĩa rõ ràng. Đừng đưa mật khẩu plaintext vào Principal name hoặc permanent credential set chỉ vì dễ dùng lại. Với session/token, phải có lifecycle, quyền đọc/xóa, expiration, refresh và logout phù hợp.

Trong Java, `Subject` có thể bị đánh dấu read-only, khi đó thay đổi các tập dữ liệu có thể thất bại. LoginModule nên xử lý việc commit vào Subject read-only như lỗi cấu hình/policy thay vì âm thầm coi commit thành công.

## 9. Kiến trúc mẫu cho một ứng dụng thực

Giả sử hệ thống có ba nguồn:

```text
+---------------------+   +---------------------+
| Desktop UI Handler  |   | Web Request Handler |
+---------------------+   +---------------------+
          |                           |
          +-------------+-------------+
                        |
                        v
                NameCallback
                PasswordCallback
                        |
                        v
               DirectoryLoginModule
                        |
                        v
                CredentialVerifier
          (trusted credential database/service)
                        |
         +--------------+-------------+
         |                            |
      authenticated                 rejected
         |                            |
        Subject                     LoginException
        Principal                   no Principal
```

Bạn có thể thay `DirectoryLoginModule` bằng module khác nếu contract callbacks/verification khác, hoặc thay UI handler mà giữ nguyên module. Tuy nhiên, việc “dùng chung” không được làm nhòe trust boundary: web request là dữ liệu attacker-controlled, còn stored password hash/secret verifier là dữ liệu được bảo vệ riêng. Handler không nên chọn arbitrary credential database endpoint do user gửi.

### Các interface/phân tầng dễ kiểm thử

```text
CredentialsProvider (thuộc tầng application/UI)
    -> cung cấp username + char[] theo scope request

CallbackHandler (adapter JAAS)
    -> dịch yêu cầu callback thành dữ liệu

LoginModule (orchestration/JAAS integration)
    -> chọn verifier, biến kết quả thành JAAS state

CredentialVerifier (server-side policy)
    -> password hashing/KDF, account state, lockout/rate limits, MFA

Subject/Principal (authenticated identity)
    -> authorization layer kiểm quyền nghiệp vụ
```

Không bắt buộc triển khai tất cả thành class riêng trong mọi ứng dụng nhỏ; đây là cách nghĩ giúp thay đổi UI và credential backend mà không kéo thay đổi mật mã vào presenter.

## 10. Java 17+ — chương trình độc lập kiểm chứng CallbackHandler

Ví dụ dùng hoàn toàn **JDK 17**, không cần database, JAAS config file hay thư viện ngoài. Nó gồm:

- `UserPrincipal` kiểu Java record chứa **tên**, không chứa password.
- `LabLoginModule` thực hiện yêu cầu `NameCallback`/`PasswordCallback` và một kiểm tra **giả lập**.
- `FixedHandler` nạp thông tin vào callbacks, giữ bản sao `char[]` và triển khai `AutoCloseable` để xóa bản sao.
- `Configuration` tạo trực tiếp trong code với một module `REQUIRED`.
- `LoginContext` cho trường hợp đúng và sai; kiểm số Principal sau commit/logout/failure.
- Test `UnsupportedCallbackException` cho loại callback không được hỗ trợ.
- Test `PasswordCallback.clearPassword()` loại bỏ giá trị cũ mà **không dựa vào `getPassword() == null`**.

**Cảnh báo về credential giả:** chương trình có một password demo gắn trong source **chỉ để test sự tách biệt callback và login**. `Arrays.equals` và password hard-code **không** là thiết kế kiểm mật khẩu production. Một hệ thống thật phải gọi credential verifier đáng tin với password hashing/KDF, salt, rate limiting và account/MFA policy. Không đưa demo này vào Internet-facing authentication service.

Lưu file **`JaasCallbackDemo.java`**:

```java
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Map;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

public final class JaasCallbackDemo {
    public record UserPrincipal(String name) implements Principal {
        @Override public String getName() { return name; }
    }

    public static final class LabLoginModule implements LoginModule {
        private Subject subject;
        private CallbackHandler handler;
        private UserPrincipal pending;
        private boolean authenticated;
        private boolean committed;

        @Override
        public void initialize(Subject subject, CallbackHandler handler,
                Map<String, ?> sharedState, Map<String, ?> options) {
            this.subject = subject;
            this.handler = handler;
        }

        @Override
        public boolean login() throws LoginException {
            if (handler == null) throw new LoginException("No callback handler");
            NameCallback name = new NameCallback("User: ");
            PasswordCallback password = new PasswordCallback("Password: ", false);
            char[] submitted = null;
            char[] expected = {'d','e','m','o','-','p','a','s','s'};
            try {
                handler.handle(new Callback[] {name, password});
                submitted = password.getPassword();
                if (!"alice".equals(name.getName())
                        || submitted == null
                        || !Arrays.equals(submitted, expected)) {
                    throw new FailedLoginException("Invalid credentials");
                }
                pending = new UserPrincipal(name.getName());
                authenticated = true;
                return true;
            } catch (IOException | UnsupportedCallbackException e) {
                LoginException failure = new LoginException(
                        "Credential collection failed");
                failure.initCause(e);
                throw failure;
            } finally {
                if (submitted != null) Arrays.fill(submitted, '\0');
                Arrays.fill(expected, '\0');
                password.clearPassword();
            }
        }

        @Override
        public boolean commit() throws LoginException {
            if (!authenticated) return false;
            if (subject.isReadOnly()) throw new LoginException("Read-only subject");
            subject.getPrincipals().add(pending);
            committed = true;
            return true;
        }

        private void clearState() {
            authenticated = false;
            committed = false;
            pending = null;
        }

        @Override
        public boolean abort() throws LoginException {
            if (committed) return logout();
            clearState();
            return true;
        }

        @Override
        public boolean logout() throws LoginException {
            if (committed && !subject.isReadOnly()) {
                subject.getPrincipals().remove(pending);
            }
            clearState();
            return true;
        }
    }

    public static final class FixedHandler
            implements CallbackHandler, AutoCloseable {
        private final String username;
        private final char[] credential;
        private boolean closed;

        FixedHandler(String username, char[] credential) {
            this.username = username;
            this.credential = credential.clone();
        }

        @Override
        public void handle(Callback[] callbacks)
                throws IOException, UnsupportedCallbackException {
            if (closed) throw new IOException("Handler is closed");
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback name) {
                    name.setName(username);
                } else if (callback instanceof PasswordCallback password) {
                    password.setPassword(credential);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }

        @Override
        public void close() {
            Arrays.fill(credential, '\0');
            closed = true;
        }
    }

    private static Configuration labConfiguration() {
        return new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                if (!"sample".equals(name)) return null;
                return new AppConfigurationEntry[] {
                    new AppConfigurationEntry(
                        LabLoginModule.class.getName(),
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        Map.of())
                };
            }
        };
    }

    public static void main(String[] args) throws Exception {
        Configuration config = labConfiguration();
        char[] good = {'d','e','m','o','-','p','a','s','s'};
        try (FixedHandler handler = new FixedHandler("alice", good)) {
            LoginContext context = new LoginContext(
                    "sample", new Subject(), handler, config);
            context.login();
            System.out.println("Valid login: true");
            System.out.println("Authenticated principal: " +
                    context.getSubject().getPrincipals(UserPrincipal.class)
                            .iterator().next().getName());
            System.out.println("Principal count after login: " +
                    context.getSubject().getPrincipals().size());
            context.logout();
            System.out.println("Principal count after logout: " +
                    context.getSubject().getPrincipals().size());
        } finally {
            Arrays.fill(good, '\0');
        }

        char[] wrong = {'n','o','t','-','r','i','g','h','t'};
        try (FixedHandler handler = new FixedHandler("alice", wrong)) {
            LoginContext context = new LoginContext(
                    "sample", new Subject(), handler, config);
            try {
                context.login();
                System.out.println("Wrong password rejected: false");
            } catch (LoginException expected) {
                System.out.println("Wrong password rejected: true");
                System.out.println("Principal count after failure: " +
                        context.getSubject().getPrincipals().size());
            }
        } finally {
            Arrays.fill(wrong, '\0');
        }

        char[] placeholder = {'x'};
        try (FixedHandler handler = new FixedHandler("alice", placeholder)) {
            try {
                handler.handle(new Callback[] {new TextInputCallback("OTP: ")});
                System.out.println("Unsupported callback rejected: false");
            } catch (UnsupportedCallbackException expected) {
                System.out.println("Unsupported callback rejected: true");
            }
        } finally {
            Arrays.fill(placeholder, '\0');
        }

        PasswordCallback callback = new PasswordCallback("Password: ", false);
        char[] temporary = {'x'};
        callback.setPassword(temporary);
        char[] independentCopy = callback.getPassword();
        callback.clearPassword();
        char[] afterClear = callback.getPassword();
        boolean originalRemoved = afterClear == null
                || !Arrays.equals(independentCopy, afterClear);
        Arrays.fill(temporary, '\0');
        Arrays.fill(independentCopy, '\0');
        if (afterClear != null) Arrays.fill(afterClear, '\0');
        System.out.println("Original callback password removed: " +
                originalRemoved);
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 JaasCallbackDemo.java
java JaasCallbackDemo
```

**Stdout chính xác đã kiểm chứng với JDK 17 trong môi trường thử nghiệm:**

```text
Valid login: true
Authenticated principal: alice
Principal count after login: 1
Principal count after logout: 0
Wrong password rejected: true
Principal count after failure: 0
Unsupported callback rejected: true
Original callback password removed: true
```

### Vì sao chương trình hoạt động như vậy?

1. `FixedHandler` chỉ biết `username`/`credential` đầu vào và hai loại callback mà nó hỗ trợ. Không có logic “mật khẩu hợp lệ hay không” trong handler.
2. `LabLoginModule.login()` tự tạo `NameCallback` và `PasswordCallback`, yêu cầu handler điền dữ liệu, rồi mới so sánh với mock verifier. Đây là điểm đổi sang credential service thật trong ứng dụng production.
3. `NameCallback.setName` và `PasswordCallback.setPassword` đưa dữ liệu vào callback objects. Sau `handle`, module lấy `getName()`/`getPassword()`. Dù có password, `Subject` chưa có Principal cho đến `commit`.
4. `char[] submitted` do `getPassword` trả về được ghi đè trong `finally`. Mật khẩu callback được xóa bằng `clearPassword`, mật khẩu mà handler copy được xóa ở `close`, còn password nguồn do `main` giữ được xóa bằng `Arrays.fill`.
5. `expected` là mật khẩu giả được dựng cục bộ trong module và xóa trước khi trả. **Đây không phải mật khẩu lưu trong một secure credential store**, và dùng `Arrays.equals` không phải password-verification design thích hợp trên server sản xuất.
6. `commit` thêm `UserPrincipal("alice")` sau khi đã có kết quả xác thực. `logout` gỡ principal do module tạo. Nếu có nhiều module hoặc nhiều principal trùng nhau, code production cần quản lý ownership/phạm vi xóa cẩn thận hơn.
7. Trường hợp password sai ném `FailedLoginException` rồi LoginContext thất bại; `Subject` demo vẫn có **0 Principal**. Đây là kiểm thử không cấp quyền sau failed login.
8. `TextInputCallback` không được handler demo hỗ trợ; `handle` ném `UnsupportedCallbackException` đúng hợp đồng. Một handler thực muốn hỗ trợ OTP/challenge cần chính sách riêng, không thể chỉ thêm một nhánh trả text rồi coi đó là MFA.
9. `PasswordCallback.clearPassword` không được kiểm bằng `getPassword() == null`. Test tạo một bản sao trước khi clear và xác nhận giá trị cũ không còn trong callback sau clear; đồng thời xóa mọi bản sao được code nắm giữ.
10. `Configuration` tạo trực tiếp bằng Java chỉ để ví dụ dễ chạy. Trong hệ thống triển khai, configuration provider, module discovery, options và classpath có thể do application server/container quản lý.

### Giới hạn của ví dụ

Ví dụ không mở console hoặc kết nối mạng, không nạp user directory, không gọi password hashing service, không kiểm rate limit/MFA, không có concurrent sessions, không có thực thi authorization ngoài Subject, và không chứng minh credential lifecycle trong một web framework. Nó **kiểm chứng cơ chế callback và JAAS login lifecycle**, không phải xác thực người dùng Internet theo chuẩn production.

## 11. CallbackHandler cho CLI: Console và điều kiện không có terminal

Với CLI interactive, một handler có thể dùng `System.console()` để hỏi username/password. Ví dụ **mã minh họa**, không độc lập:

```java
Console console = System.console();
if (console == null) {
    throw new IOException("Interactive console unavailable");
}
String username = console.readLine("User: ");
char[] password = console.readPassword("Password: ");
try {
    nameCallback.setName(username);
    passwordCallback.setPassword(password);
} finally {
    if (password != null) Arrays.fill(password, '\0');
}
```

Cần import `java.io.Console`, `java.io.IOException`, `java.util.Arrays` và giả sử `nameCallback`/`passwordCallback` đúng kiểu đã được cung cấp. Đây là một đoạn **trong handler**, không phải logic verifier.

Các lưu ý:

- `System.console()` có thể trả `null` khi chạy trong IDE, CI, Docker, daemon, redirected stdin hoặc môi trường không có console tương tác. Không fallback sang `Scanner.nextLine()` đọc mật khẩu hiển thị trần một cách vô tình.
- `Console.readPassword` hỗ trợ đọc mật khẩu tránh echo trên terminal phù hợp, nhưng vẫn cần bảo vệ môi trường, quyền truy cập process và lịch sử shell.
- Có người dùng hủy hoặc EOF; không truyền `null` vào verifier rồi coi là password rỗng hợp lệ.
- Một CLI handler có thể được gọi trên thread khác tùy framework; nếu UI chỉ chạy một thread, xử lý luồng đồng bộ/khóa input an toàn.
- Không lưu password trong `String`, environment variable hoặc command-line arguments khi có thể tránh; các nguồn này có thể lộ qua process inspection, logs và core dumps.

## 12. CallbackHandler cho web và headless services

Trong ứng dụng web, thông tin đăng nhập có thể đến từ request đã qua HTTPS, browser challenge, reverse proxy hoặc một identity provider. **CallbackHandler không tự kiểm TLS**, không bảo đảm request chính chủ và không thay CSRF/anti-automation/rate limiting.

### Request-scoped handler

Một handler nhận username/char[] cho **một login attempt** nên có lifetime hẹp:

```text
HTTPS request -> input validation -> request-scoped CallbackHandler
      -> JAAS LoginContext.login()
      -> LoginModule -> credential verifier
      -> JAAS Subject -> session mapping
      -> request cleanup / wipe char[] / close handler
```

Không để singleton handler giữ `username`/`password` từ nhiều request; có thể gây **credential mix-up** giữa users. Nên tạo handler/`LoginContext` mới theo request/session context và không dùng ThreadLocal không kiểm soát với async execution hoặc thread pools.

### Headless service với vault

Một service có thể cần đăng nhập tới một hệ thống khác bằng secret đã được cấp từ vault/KMS/secret manager. Handler có thể xin password/token từ provider theo identity của workload, điền callback rồi xóa bản sao. LoginModule vẫn chịu trách nhiệm đưa dữ liệu đó tới verifier của hệ đích và bảo vệ trạng thái xác thực.

Không coi việc secret manager trả credential là chứng minh caller hiện tại đã được authorization. Dùng IAM, workload identity, scoping theo environment/tenant và audit retrieval. Nếu secret retrieval thất bại, handler báo lỗi rõ ràng ở kênh nội bộ và login fail closed, không dùng mật khẩu rỗng hoặc default backup credential.

### Sự khác biệt khi dùng modern authentication

JAAS `CallbackHandler` thường xuất hiện trong JDK security providers, application servers, Kerberos/SASL hoặc cơ chế login module tùy biến. Với OIDC/OAuth2/SAML, phần xác thực có thể do protocol/framework chuyên trách xử lý; đừng tự lấy access token và ép nó thành “password” cho `PasswordCallback` nếu không có adapter/protocol design rõ ràng.

Tách thu thập credentials khỏi verifier là một **nguyên tắc kiến trúc**, không phải yêu cầu mọi ứng dụng hiện đại phải dùng JAAS làm gateway cho tất cả identity protocols.

## 13. Callback chuyên biệt: OTP, lựa chọn phương thức và MFA

Ngoài Name/Password, JAAS cung cấp những callback khác. Chúng có **semantics khác nhau** và không nên diễn giải mọi giá trị nhập thành mật khẩu.

| Callback | Dữ liệu/vai trò | Ví dụ sử dụng |
| --- | --- | --- |
| `NameCallback` | Username hoặc identifier | Chọn danh tính người dùng |
| `PasswordCallback` | Secret dạng `char[]`, tùy policy yêu cầu echo off | Mật khẩu/challenge response bí mật |
| `TextInputCallback` | Input text thường | Nhập tên realm/thuộc tính; **cẩn thận nếu chứa OTP/secret** |
| `TextOutputCallback` | Trình bày thông tin/thông báo | Thông báo policy, hướng dẫn |
| `ChoiceCallback` | Chọn một/nhiều phương án trong danh sách | Chọn realm/auth method |
| `ConfirmationCallback` | Xác nhận/cancel | Người dùng đồng ý tiếp tục |
| `LanguageCallback` | Locale muốn dùng | UI nội địa hóa |
| Callback đặc thù provider | Dữ liệu không được chuẩn hóa cho mọi module | Hardware token, challenge, key handle |

Với OTP hoặc WebAuthn/FIDO2, không chỉ “thêm một ô nhập” là có MFA. Cần xác minh challenge nonce, thời hạn, binding với attempt/user/session, replay resistance, rate limiting và trust model của authenticator. Nếu thông tin OTP là secret nhạy cảm, dùng loại dữ liệu/handler phù hợp và cleanup theo threat model; `TextInputCallback` dùng `String` có thể làm tăng secret lifetime.

### Khi UI không hỗ trợ callback mới

Khi nâng cấp LoginModule để cần một callback bổ sung, handler cũ nên trả `UnsupportedCallbackException`. Hệ thống cần phát hiện mismatch trong integration tests và rollout handler/client tương ứng; không “mặc định OTP pass” hoặc bỏ qua optional-looking prompt. Nếu một module bắt buộc callback để xác thực mà không thể thu thập dữ liệu, **login phải thất bại**.

## 14. Ngoại lệ và xử lý lỗi an toàn

`CallbackHandler.handle` khai báo hai checked exceptions:

- `IOException`: nguồn nhập liệu I/O hoặc hoạt động thu thập thông tin không thể hoàn thành, chẳng hạn console/vault/network adapter lỗi.
- `UnsupportedCallbackException`: handler không hỗ trợ loại callback được yêu cầu.

Trong `LoginModule.login()`, thường chuyển ngoại lệ phù hợp thành `LoginException` (giữ cause nội bộ) rồi để LoginContext điều phối failure/abort. Credentials sai theo chính sách xác thực có thể ném `FailedLoginException`.

### Phân biệt ba loại thất bại

| Tình huống | Bản chất | Hành vi đúng |
| --- | --- | --- |
| Username/password sai | Authentication failed | Từ chối và áp rate limiting/lockout policy |
| Handler không hỗ trợ callback | Integration/configuration failure | Từ chối, ghi dấu lỗi nội bộ, sửa handler/module |
| Không đọc được secret source | Availability/I/O failure | Từ chối theo policy, không fallback thiếu kiểm soát |

Đối với client không tin cậy, tránh phản hồi chi tiết “username tồn tại nhưng password sai” hoặc “vault key cho tenant X tồn tại”. Thông báo bên ngoài nên nhất quán và không tiết lộ người dùng/secret inventory; log nội bộ có loại lỗi, correlation ID và metadata đã làm sạch.

### Cleanup trong mọi nhánh

Trong demo, `finally` luôn dọn `submitted` và `PasswordCallback`, kể cả khi `handle` ném `IOException` hoặc `UnsupportedCallbackException`. Khi login thất bại, `abort` phải dọn trạng thái module; handler vẫn phải `close` và xóa buffer nó sở hữu.

**Đừng chỉ xóa password khi login thành công.** Credential thất bại vẫn nhạy cảm, thậm chí có thể là mật khẩu đúng của người dùng nhưng nhập nhầm username.

### Tránh bắt ngoại lệ quá rộng để tuyên bố thành công

Không làm kiểu:

```java
// Anti-pattern, không phải chương trình độc lập.
try {
    loginContext.login();
} catch (Exception ignored) {
    session.setAuthenticated(true); // NGUY HIỂM.
}
```

Ngoại lệ bất kỳ trong login phải khiến kết quả auth thất bại theo policy. Nếu application cần thông tin kỹ thuật để khắc phục, ghi log server-side an toàn, không phơi exception chain có secret.

## 15. Thread safety, vòng đời và khả năng tái nhập

Không nên mặc định một `CallbackHandler` là thread-safe. Interface chỉ quy định `handle(Callback[])`; nó **không** cam kết:

- Handler không bị gọi đồng thời từ nhiều login attempts.
- Một callback chỉ được điền một lần.
- UI luôn có interactive thread.
- Handler có quyền tiếp tục đọc secret sau khi login attempt kết thúc.
- `Callback[]` chỉ chứa NameCallback và PasswordCallback mãi mãi.
- `LoginModule` có thể reuse an toàn giữa request khác nhau.

Một handler có field `username`/`credential` thường nên **request-scoped hoặc attempt-scoped**. Nếu cần global credential provider, nên truyền vào một service thread-safe có trust boundary rõ, nhưng giữ secret data ngắn hạn trong từng instance.

### Nguy cơ race conditions

Giả sử singleton handler có `currentUsername` và `currentPassword` dùng chung. Request A đặt Alice rồi bị scheduler tạm dừng; request B ghi Bob vào cùng fields; sau đó handler của A chạy và có thể trả username Bob/password Alice hoặc ngược lại. Đây không chỉ là lỗi logic mà có thể tạo sự cố xác thực/định danh nghiêm trọng. Giải pháp: **không giữ request credentials trong state toàn cục**, bảo đảm snapshot theo attempt và quản lý cleanup đúng scope.

### Không truy cập UI thread sai cách

Desktop UI frameworks như Swing/JavaFX có quy tắc thread riêng. Một JAAS LoginModule có thể gọi handler từ worker thread; handler muốn hiển thị dialog phải điều phối tới UI thread hợp lệ mà không deadlock/đóng băng giao diện. Không dùng `System.console()` trong server process chỉ vì interface yêu cầu password.

## 16. Password verifier sản xuất: đừng đặt niềm tin vào UI

Handler có thể lấy secret “đúng định dạng” nhưng **điều đó không xác thực credential**. LoginModule/verifier phải dựa vào nguồn được bảo vệ và chính sách mật khẩu, thường:

1. Lấy tài khoản từ directory/database đã xác định theo tenant/realm và bảo vệ chống user enumeration.
2. Lấy salted password verifier/hash, không lưu plaintext.
3. Dùng password hashing/KDF có thông số thích hợp, ví dụ Argon2id, scrypt, bcrypt hoặc PBKDF2 theo tiêu chuẩn và khả năng thư viện/tổ chức. Không dùng SHA-256 đơn lẻ của password làm verifier production.
4. So sánh theo cơ chế phù hợp, tránh nhánh timing leak đáng kể; đánh giá rehash/migration khi chính sách thay đổi.
5. Có rate limiting, backoff/lockout có kiểm soát, giám sát brute force/credential stuffing và MFA khi cần.
6. Kiểm trạng thái tài khoản/credential expiry/revocation, chống replay/challenge reuse theo protocol.
7. Khi thành công, tạo Principal/session với phạm vi danh tính và quyền cụ thể; session và authorization do tầng ứng dụng quyết định.
8. Không giữ password sau khi hoàn tất; clear callback và các bản sao do app kiểm soát.
9. Không ghi password, hash/raw challenge response hoặc token vào logs/traces/exception messages.
10. Đánh giá nguồn credentials đã đi qua HTTPS/secure channel; browser/password manager/IdP flows cần CSRF/session controls phù hợp.

`Arrays.equals(submitted, expected)` trong Java demo chỉ là thao tác so hai mảng **giả** dễ đọc để chứng minh CallbackHandler không verify. Nó **không** thay thế password verifier KDF an toàn và không phải constant-time password protocol.

## 17. Test strategy: kiểm module và handler độc lập

Để tận dụng kiến trúc tách biệt, nên có **ba lớp kiểm thử**.

### 17.1 Test riêng Handler

Dùng callbacks mẫu, kiểm handler:

- Điền đúng `NameCallback`.
- Điền `PasswordCallback` với dữ liệu của đúng login attempt.
- Ném `UnsupportedCallbackException` nếu gặp callback ngoài support.
- Fail closed nếu nguồn credential không có hoặc handler đã đóng.
- Không trả password của request khác khi có concurrent attempts.
- Không làm lộ dữ liệu trong log hoặc exception.
- Xóa buffer nó sở hữu sau `close`, kể cả khi `handle` thất bại.

### 17.2 Test riêng LoginModule

Cấp fake handler cho module và fake credential verifier (hoặc lab verifier). Kiểm:

- Login đúng -> `login()` thành công; `commit()` mới thêm Principal.
- Login sai -> `FailedLoginException`/`LoginException`, không có authenticated Principal.
- `PasswordCallback.getPassword()` trả null/empty -> authentication failure theo policy.
- Handler ném `IOException`/`UnsupportedCallbackException` -> LoginException và cleanup.
- `abort()` dọn state tạm khi module khác trong cùng LoginContext thất bại.
- `logout()` gỡ đúng Principal do module tạo và không xóa identity của module khác.
- Subject read-only -> commit thất bại có kiểm soát.
- Credential verifier bị outage -> fail closed; không fallback sang password mặc định.
- Password cleanup không chỉ chạy trong success branch.

### 17.3 Integration test LoginContext

Chạy JAAS Configuration thực:

| Tình huống | Kỳ vọng |
| --- | --- |
| Handler đúng username/secret, module REQUIRED | `LoginContext.login()` thành công; Subject có Principal |
| Handler sai password | LoginException; không cấp Principal |
| Handler trả username khác tenant | Server-side verifier từ chối hoặc xác định đúng namespace, không trộn session |
| Handler không hỗ trợ callback cần thiết | Login thất bại, log tích hợp an toàn |
| Handler bị đóng trước khi login | I/O/integration failure, không xác thực |
| Login thành công rồi logout | Principal/credentials do module sở hữu được dọn |
| Multiple modules REQUIRED | Một required fail -> kết quả tổng thể fail |
| MFA module bắt buộc | Không có callback/response hợp lệ -> fail |
| Nhiều request đồng thời | Không rò credentials giữa attempts |
| PasswordCallback clear | Giá trị cũ không còn trong callback-owned storage; caller-owned copies cũng được xóa |
| LoginModule verifier lỗi mạng | Không tạo Principal thành công giả |
| UI password hiển thị | Echo policy được handler/UI tuân, không log |
| Reusable UI | Cùng LoginModule dùng handler CLI và handler web hợp đồng giống nhau |

**Điều quan trọng:** test thành công không chỉ là “handler đã điền tên/password”; phải quan sát authentication result qua LoginContext/Subject. Ngược lại, một Subject chứa Principal do ứng dụng tự tạo không chứng minh LoginContext đã verify nếu dữ liệu được thêm ở sai chỗ.

## 18. Lỗi thường gặp và hướng sửa

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| `LoginModule` tự đọc `System.in`/UI trực tiếp | Không test/headless được, gắn chặt môi trường | Yêu cầu callbacks và truyền handler |
| CallbackHandler tự quyết định “password đúng” | Lẫn data collection với credential verification | Đưa xác minh vào LoginModule/verifier đáng tin |
| Tự thêm Principal trong handler | Có thể cấp danh tính trước xác thực | Chỉ commit Principal sau authentication |
| Không gọi `setSSLParameters`? | Không liên quan bài JAAS | Không pha lẫn network TLS configuration với JAAS callbacks |
| Handler bỏ qua callback lạ | Bước authentication mới có thể bị bypass/thiếu dữ liệu | Ném `UnsupportedCallbackException` |
| Chỉ xử lý NameCallback mà bỏ PasswordCallback | Login nhận null/empty secret hoặc lỗi không rõ | Handler hỗ trợ đúng contract hoặc fail |
| Chuyển char[] password thành String | Khó cleanup secret trong RAM | Giữ char[] khi API cho phép |
| Chỉ gọi `clearPassword` | Bản sao trong handler/module vẫn còn | Xóa mọi buffer theo ownership |
| Nghĩ `clearPassword` luôn cho `getPassword()==null` | Kiểm cleanup sai trong JDK17 | Kiểm contract overwrite, không đòi null |
| Dùng `Arrays.equals` làm password verifier production | Thiếu hash/KDF/mitigation và policy | Dùng trusted password verifier |
| Đặt password trong JAAS options | Rò secret qua config/log | Dùng secret manager/credential store |
| Dùng singleton handler chứa request credentials | Race/cross-user credential mix-up | Handler per attempt, no global secret state |
| Console null thì đọc secret bằng `Scanner` có echo | Lộ password trong console/log | Fail closed hoặc dùng input an toàn |
| Lộ lỗi “tài khoản X tồn tại” | User enumeration | Thông báo chung; log nội bộ đã làm sạch |
| `login()` fail vẫn gọi application authenticated | Bypass auth | Chỉ cấp session khi LoginContext.login thành công |
| Không triển khai `abort/logout` đúng | Principal/credential bị giữ sai lifecycle | Dọn state và chỉ gỡ identity mình đã thêm |
| Cho module tùy ý được handler request key/vault scope | SSRF/secret enumeration/tenant isolation lỗi | Allowlist trust realm/key lookup server-side |
| Coi JAAS login = authorization | Người dùng authenticated có thể làm quá quyền | RBAC/ABAC theo tenant/resource/action |
| Trộn handler đa mục đích không kiểm callback | Bỏ sót MFA/challenge và dễ misuse | Explicit supported callback types + integration tests |
| Reuse Subject mutable chung giữa request | Trộn principal/session | Isolation và Subject lifecycle per security context |

Trong hàng “`setSSLParameters`”, điểm nhấn là **không trộn logic các mô-đun an ninh khác nhau**: `SSLParameters` thuộc cấu hình JSSE/TLS, không phải API thu thập mật khẩu JAAS. Nếu cần TLS cho transport, cấu hình/kiểm tra ở tầng network, còn `CallbackHandler` phục vụ data handoff cho authentication module.

## 19. Checklist triển khai an toàn

Trước khi đưa adapter JAAS vào môi trường thật, hãy xác nhận:

1. **Thành phần nào yêu cầu callbacks?** LoginModule/provider phải có hợp đồng callback types, thứ tự/ý nghĩa rõ ràng và cơ chế failure.
2. **Ai tạo handler?** Ứng dụng tạo handler theo request/attempt và truyền vào LoginContext; handler không nên giữ secret global.
3. **Nguồn credential ở đâu?** UI, HTTPS request, vault hoặc token provider; scope, quyền và thời hạn dữ liệu rõ.
4. **Input validation ở đâu?** Giới hạn kích thước/encoding, normalization theo policy; không để prompt/username biến thành path/URL query tùy ý.
5. **Verifier có đáng tin?** Server-side credential store/password hash, policy độ mạnh, rate limits, lockout, MFA và monitoring.
6. **Password ownership ra sao?** Liệt kê mọi char[]/String/encoded copies ở nguồn, callback, module và provider; wipe phần kiểm soát được.
7. **Callback không được hỗ trợ xử lý thế nào?** Ném đúng exception và không cho login pass.
8. **Các LoginModule được cấu hình ra sao?** Control flags và required checks không tạo bypass.
9. **Principal được commit khi nào?** Chỉ sau successful authentication, không từ handler hoặc unauthenticated request.
10. **Abort/logout có idempotent và đúng ownership?** Không để lại Principal/credential khi login fail hoặc logout.
11. **Logging có an toàn?** Không in password, token, hash, credential store responses; có correlation ID, audit và giới hạn retention.
12. **Concurrent attempts có cách ly?** Không reuse mutable handler/Subject sai scope.
13. **Transport có an toàn?** HTTPS/TLS peer identity và session security áp dụng độc lập với JAAS.
14. **Test âm đã có?** Sai password, unknown user/tenant, unsupported callback, null handler, vault outage, cancellation và concurrency.
15. **UI hỗ trợ người dùng?** Echo off, accessibility/localization, cancel/timeout và thông báo lỗi không tiết lộ secret.
16. **Identity được cấp quyền đúng?** Sau JAAS authentication, ứng dụng vẫn kiểm authorization cho tenant/resource/action.
17. **Secrets có rotate/revoke?** Password reset, account disable, token/key rotation và login sessions phản ánh policy thực.
18. **Provider/JDK có đặc tính riêng?** Kiểm trên đúng Java 17+ build, app server/container và cách JAAS Configuration được nạp.
19. **Observability đo đúng?** Đo login result/failure reasons đã làm sạch, latency và callback integration errors, không thu password content.
20. **Không tự chế protocol MFA:** Nếu cần OIDC/WebAuthn/OTP, dùng implementation chuẩn được review và binding đầy đủ, không chỉ đọc một String làm OTP.

## 20. Bài tập thực hành

### Bài 1 — Thay nguồn credentials mà giữ nguyên verifier

Giữ `LabLoginModule` từ bài lab, viết một `CallbackHandler` khác nhận username/password từ một cấu trúc request object giả thay vì `FixedHandler`. Không sửa code xác minh của LoginModule. Kiểm login đúng/sai và các bản sao char[] được xóa theo đúng ownership.

### Bài 2 — Callback không hỗ trợ

Gọi handler với `NameCallback` + `TextInputCallback`. Yêu cầu xử lý rõ ràng `UnsupportedCallbackException`, không im lặng bỏ qua. Nêu cách một hệ thống có thêm OTP nên rollout handler mới và policy MFA mà không tạo bypass.

### Bài 3 — Console có thể không tồn tại

Triển khai handler tương tác bằng `System.console()` và `readPassword()` trong CLI lab. Chạy ở terminal và IDE/CI headless. Khi console trả null, handler phản ứng thế nào? Vì sao không tự động đọc mật khẩu bằng echo-on?

### Bài 4 — Kiểm password copies

Dùng `PasswordCallback` và nhiều `char[]` độc lập. Thử sửa mảng nguồn sau `setPassword` rồi xem callback có bị thay không; thử sửa mảng trả về từ `getPassword` rồi xem bản lưu trong callback; gọi `clearPassword` và giải thích vì sao phải wipe cả bản sao tự quản. Không dùng password thật trong test.

### Bài 5 — Lifecycle JAAS

Thêm một `LoginModule` thứ hai với control flag `REQUIRED` mà trả failure. Kiểm `abort` của module đầu tiên được gọi và Subject không chứa Principal được coi là authenticated. Thử logic `commit` và `logout` với nhiều Principal để bảo đảm mỗi module chỉ xóa thông tin do nó đóng góp.

### Bài 6 — Thiết kế CredentialVerifier sản xuất

Thay demo so sánh mảng bằng một fake interface `CredentialVerifier.verify(username, char[])`, sau đó phác thảo adapter thật dùng salted password hash/KDF, account state và rate limiting. Giải thích vì sao không nên truyền expected password qua CallbackHandler hay JAAS options và cách giảm user enumeration.

### Bài 7 — Cách ly multi-tenant/concurrency

Dựng hai login attempts Alice/tenant A và Bob/tenant B chạy song song. Chứng minh một singleton mutable handler có thể làm credential mix-up. Viết test dùng handler per-attempt và trusted tenant context để ngăn trộn danh tính.

### Bài 8 — Web application integration

Thiết kế luồng HTTPS request → input adapter → JAAS LoginContext → Subject → session principal → authorization cho API. Nêu cách dọn char[] sau request, xử lý LoginException an toàn, CSRF/session fixation/MFA khi cần và tại sao JAAS Subject không tự cấp quyền đọc dữ liệu nghiệp vụ.

### Bài 9 — CallbackHandler không đồng nghĩa authentication

Một attacker gửi `username="admin"` và một password bất kỳ; handler điền Name/Password callbacks hoàn hảo. Tại sao ứng dụng vẫn phải từ chối? Vạch rõ điểm cần verifier, điểm commit Principal và điểm kiểm authorization.

**Điều kiện đạt:** bạn có thể tạo một LoginModule **không phụ thuộc terminal, UI hoặc HTTP**, triển khai nhiều handlers cho những nguồn credentials khác nhau, xử lý callback types/exception đúng hợp đồng, bảo vệ dữ liệu nhạy cảm bằng cleanup theo ownership, và chỉ đưa Principal vào Subject sau khi JAAS xác thực thành công theo chính sách.

## 21. Tài liệu tham khảo

- [Java SE 17 — CallbackHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/callback/CallbackHandler.html): `handle(Callback[])`, `IOException` và `UnsupportedCallbackException`.
- [Java SE 17 — Callback](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/callback/Callback.html): marker interface cho yêu cầu tương tác.
- [Java SE 17 — NameCallback](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/callback/NameCallback.html): prompt và tên đăng nhập.
- [Java SE 17 — PasswordCallback](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/callback/PasswordCallback.html): `setPassword`, `getPassword`, `clearPassword`, echoOn.
- [Java SE 17 — UnsupportedCallbackException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/callback/UnsupportedCallbackException.html): callback types không được hỗ trợ.
- [Java SE 17 — LoginContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/LoginContext.html): đăng nhập, đăng xuất và điều phối JAAS.
- [Java SE 17 — LoginModule](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/spi/LoginModule.html): lifecycle `initialize/login/commit/abort/logout`.
- [Java SE 17 — Configuration](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/Configuration.html) và [AppConfigurationEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/AppConfigurationEntry.html): khai báo modules/control flags.
- [Java SE 17 — Subject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/Subject.html): Principal, credentials và danh tính JAAS.
- [Java SE 17 — Principal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Principal.html): đối tượng danh tính.
- [Java SE 17 — JAAS Authentication Tutorial](https://docs.oracle.com/en/java/javase/17/security/jaas-authentication-tutorial.html): quan hệ LoginContext, LoginModule và CallbackHandler.
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html): xác thực ứng dụng, responses, logging, MFA và brute-force controls.
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): salted password hashing/KDF, rehash và bảo vệ verifier.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jaas-authentication-005">17.5 Vòng đời initialize, login, commit, abort và logout của LoginModule</a>

<details>
<summary>Xem chi tiết</summary>

# 17.5. Vòng đời initialize, login, commit, abort và logout của LoginModule

**JAAS** (Java Authentication and Authorization Service) cho phép ứng dụng Java thay đổi cơ chế xác thực mà không phải viết lại phần điều phối. Ứng dụng gọi `javax.security.auth.login.LoginContext`; một hoặc nhiều thành phần cung cấp xác thực triển khai `javax.security.auth.spi.LoginModule`.

Điểm mấu chốt của bài học: **JAAS xác thực theo hai pha**. Pha `login()` kiểm credential, lưu trạng thái thành công **tạm trong module**. Sau khi các module được đánh giá theo các control flags, `LoginContext` gọi **`commit()` nếu tổng thể thành công** hoặc **`abort()` nếu tổng thể thất bại**. `logout()` là một thao tác riêng khi ứng dụng muốn kết thúc phiên đã đăng nhập. `initialize()` truyền bối cảnh để module bắt đầu làm việc.

Bài này thuộc **17. Xác thực JAAS**, tập trung vào vòng đời đủ năm phương thức, các đường đi khi thành công/thất bại, trách nhiệm cập nhật và dọn `Subject`, cách phối hợp nhiều modules, cùng ví dụ Java 21 có thể biên dịch và chạy.

## Mục tiêu học tập

- Biết `LoginContext`, `LoginModule`, `Configuration`, `AppConfigurationEntry`, `CallbackHandler`, `Subject` và `Principal` đóng vai trò gì.
- Giải thích đúng `initialize → login → commit/abort` và `logout`, kể cả trường hợp một module thành công nhưng module khác làm tổng thể thất bại.
- Phân biệt `true`, `false` và `LoginException` từ `login/commit/abort/logout`; không dùng `false` để che giấu mật khẩu sai.
- Hiểu các control flags `REQUIRED`, `REQUISITE`, `SUFFICIENT` và `OPTIONAL`.
- Viết module chỉ thêm principal sau `commit`, chỉ xóa những gì module thật sự sở hữu khi `abort`/`logout`, và dọn password.
- Chạy ba tình huống trong Java 21: đăng nhập đúng, password sai, module REQUIRED phía sau từ chối; diễn giải trace và số principal.
- Thiết kế test xử lý partial failure, callback không hỗ trợ, Subject read-only, duplicate principal, retry/logout và bí mật.
- Phân biệt JAAS authentication với session web, MFA, password hashing, authorization nghiệp vụ và rollback database.

## 1. Kiến trúc JAAS trước khi nói về vòng đời

`LoginContext` là cổng của ứng dụng. Nó đọc `Configuration` theo tên ứng dụng, tạo các `LoginModule` và quyết định module nào phải thành công. Mỗi `LoginModule` tự xác thực theo cơ chế được cắm vào, không tự tuyên bố kết quả của toàn bộ hệ thống.

```text
Application
  |
  +--> new LoginContext("demo", Subject?, CallbackHandler, Configuration)
  |
  +--> LoginContext.login()
          |
          +--> LoginModule A: kiểm username/password
          +--> LoginModule B: kiểm điều kiện khác nếu cấu hình yêu cầu
          |
          +--> JAAS tổng hợp kết quả theo control flags
                   |
                   +--> SUCCESS: commit()
                   +--> FAILURE: abort()
  |
  +--> LoginContext.getSubject() khi thành công
  |
  +--> LoginContext.logout() khi kết thúc phiên
```

| Thành phần | Trách nhiệm |
| --- | --- |
| `LoginContext` | Quản lý luồng login, lựa chọn modules, điều phối commit/abort/logout và cung cấp Subject |
| `LoginModule` | Thực hiện một phương pháp xác thực và giữ private state của nó |
| `Configuration` | Xác định tên ứng dụng, danh sách module, control flags và options |
| `AppConfigurationEntry` | Cấu hình một LoginModule với class, flag và map options |
| `CallbackHandler` | Cung cấp callback để lấy input như `NameCallback` và `PasswordCallback` |
| `Subject` | Đối tượng chứa các principals, public/private credentials của subject đã được xác thực |
| `Principal` | Định danh, nhóm hoặc vai trò theo mô hình của ứng dụng; không phải quyền tuyệt đối |
| `sharedState` | Map chia sẻ cho các modules trong cùng quá trình; phải có quy ước và cleanup |
| `options` | Map cấu hình riêng của từng module; không phải kho password |

Một ứng dụng có thể gọi `LoginContext.login()` mà không biết bên dưới đang dùng Kerberos, password, smart card hay một module tùy biến. Mọi mô hình vẫn phải tuân theo **quy tắc state của JAAS**.

## 2. Hợp đồng interface: năm methods không thể tráo vai trò

Interface chuẩn `javax.security.auth.spi.LoginModule` (đã có trong JDK, **không viết lại**) có chữ ký:

```java
void initialize(Subject subject,
                CallbackHandler callbackHandler,
                Map<String, ?> sharedState,
                Map<String, ?> options);

boolean login() throws LoginException;
boolean commit() throws LoginException;
boolean abort() throws LoginException;
boolean logout() throws LoginException;
```

| Method | Pha | Điều nhận/đọc | Điều thực hiện |
| --- | --- | --- | --- |
| `initialize` | Chuẩn bị | Subject, handler, shared state, options | Lưu context/cấu hình, chưa xác thực |
| `login` | Pha 1 | Credential/identity từ callback hoặc provider | Xác thực và giữ trạng thái **tạm** |
| `commit` | Pha 2, khi tổng thể chấp nhận | Kết quả `login` của module | Thêm Principal/Credential vào Subject, đánh dấu commit |
| `abort` | Pha 2, khi tổng thể từ chối | State login/commit cần rollback | Dọn pending state và những gì module đã thêm nếu có |
| `logout` | Sau khi đăng nhập được chấp nhận | State đã commit | Dọn Principal/Credential thuộc module và đóng phiên liên quan |

**Không có `LoginContext.initialize()` công khai cho ứng dụng gọi để kích hoạt từng module.** Chính `LoginContext` khởi tạo và gọi `LoginModule.initialize(...)` khi module được xử lý. Tài liệu JAAS mô tả hai pha, nhưng thứ tự initialize của các module khác nhau có thể xen kẽ với login, không nhất thiết “initialize tất cả trước rồi mới login bất kỳ module nào”.

`LoginModule` cần public constructor không tham số để `LoginContext` khởi tạo qua reflection. Nếu đặt class lồng nhau, nó cần là `public static` và tên binary class phải được cấu hình đúng.

## 3. Pha 1: `initialize` và `login`

### 3.1 `initialize(...)` nhận context, không cấp quyền

```java
@Override
public void initialize(Subject subject, CallbackHandler handler,
                       Map<String, ?> sharedState,
                       Map<String, ?> options) {
    this.subject = subject;
    this.handler = handler;
}
```

Đây chỉ là **đoạn minh họa trong một class**, không phải chương trình chạy riêng. `initialize` trả `void`, không được thêm Principal hoặc public/private credential vào Subject như thể user đã được xác thực.

- `subject` là đối tượng sẽ được cập nhật về sau, thường ở `commit`.
- `handler` có thể là `null`. Nếu module cần input tương tác thì phải xử lý trường hợp này bằng lỗi có kiểm soát trong `login`.
- `sharedState` có thể chứa dữ liệu do module khác chia sẻ; nếu chia sẻ secret, phải quy định ai sở hữu, ai xóa và tránh rò rỉ giữa các lần đăng nhập.
- `options` lấy từ cấu hình của module như realm, debug flag, endpoint. Cần validate và không ghi log password/secret.

Không dùng field `static` làm nơi lưu `Subject` hoặc credentials của user: những phiên khác nhau có thể chạy đồng thời, gây rò rỉ danh tính.

### 3.2 `login()` kiểm credentials và lưu private state

```java
boolean login() throws LoginException;
```

Một thực hiện đúng thường sẽ gọi `CallbackHandler.handle(...)`, lấy username/password hoặc token, kiểm bằng cơ chế xác thực đã thiết kế, rồi đánh dấu `authenticated=true` và giữ `pendingPrincipal` sau khi thành công.

**Không thêm principal vào Subject trong `login()`.** Nếu module A đã thành công nhưng module B `REQUIRED` thất bại, tổng thể cần `abort`; việc đã thêm principal quá sớm làm Subject chứa danh tính chưa được chấp nhận.

Ba kết quả cần phân biệt:

| Kết quả | Ý nghĩa |
| --- | --- |
| `return true` | Module đã xác thực thành công. Đây **chưa** bảo đảm toàn bộ LoginContext thành công. |
| `return false` | Module chọn **được bỏ qua** hoặc không áp dụng trường hợp hiện tại. Không tự coi là lỗi mật khẩu. |
| `throw FailedLoginException` hoặc `LoginException` | Xác thực thực sự thất bại hoặc không thể hoàn tất; LoginContext xử lý theo flag của module. |

Nếu module đã nhận mật khẩu và xác định mật khẩu **không đúng**, đừng `return false` để lặng lẽ bỏ qua. Trong stack có `OPTIONAL`/`SUFFICIENT`, việc dùng sai return value có thể khiến một cách xác thực khác được chấp nhận khi chính sách thực tế đòi yếu tố vừa bị bỏ qua.

`LoginModule.login()` không nên tự làm retry/backoff nhiều lần hay ngủ chờ. Theo hướng dẫn JAAS, retry thuộc caller/application và chính sách bảo vệ login; module phải báo thành công hoặc thất bại một cách rõ ràng.

### 3.3 Xóa dữ liệu từ `PasswordCallback`

`PasswordCallback.getPassword()` trả `char[]`. Sau khi sử dụng, code nên xóa bản sao tự giữ (`Arrays.fill`), gọi `PasswordCallback.clearPassword()` trong `finally` và không đưa password vào log. Việc cleanup một mảng không xóa được mọi bản sao tiềm ẩn trong JVM/provider. Khi input ban đầu là `String` bất biến, đặc biệt với framework web, không thể hứa xóa toàn bộ bí mật khỏi heap.

Một module production không được so sánh password với literal nằm trong source. Cần hệ thống lưu verifier an toàn (Argon2id hoặc PBKDF2 với salt/work factor theo policy), chống brute-force/rate limit và xác thực đa yếu tố nếu cần.

## 4. Pha 2 thành công: `commit()`

```java
boolean commit() throws LoginException;
```

Khi `LoginContext` xác định **tổng thể đăng nhập** thành công theo control flags, pha commit được thực hiện trên những module liên quan. Một module có `login() == true` sẽ chuyển identity đã xác thực thành các `Principal` và/hoặc credentials trong `Subject`.

Mẫu tư duy:

```text
login() -> authenticated=true, pendingPrincipal=UserPrincipal("alice")
overall LoginContext login succeeds
commit() -> subject.getPrincipals().add(pendingPrincipal)
           -> ghi nhớ phần đã thực sự thêm
           -> xóa pending state/secret không còn cần
           -> return true
```

Nếu module không có xác thực thành công hoặc đã được bỏ qua, `commit()` thường dọn state rồi `return false`. Nếu commit **cần thiết nhưng thất bại**, ví dụ Subject read-only hoặc provider không tạo được credential, hãy `throw LoginException`; không báo `true` khi Subject ở trạng thái không phù hợp.

### 4.1 Sở hữu principal rất quan trọng

Ví dụ:

```java
boolean added = subject.getPrincipals().add(new UserPrincipal("alice"));
```

Nếu Subject đã có một Principal bằng giá trị, set có thể trả `false`. Module **không được xóa** principal đó khi logout, vì nó không phải thứ module vừa thêm. Theo dõi object thuộc module như `ownedPrincipal`, và chỉ remove khi thực sự sở hữu. Cách này đặc biệt quan trọng khi nhiều LoginModule cùng bổ sung identities/roles.

`commit` không phải một database transaction. Những side effects tới API, database, token store phải có ranh giới giao dịch hoặc compensating actions riêng; JAAS **không tự hoàn tác** một token đã phát hoặc một dòng dữ liệu đã ghi.

## 5. Pha 2 thất bại: `abort()`

```java
boolean abort() throws LoginException;
```

Khi **tổng thể** thất bại, `LoginContext` chuyển sang nhánh abort. Điều này xảy ra ngay cả khi **bản thân module A đã xác thực thành công** nhưng module `REQUIRED`/`REQUISITE` khác thất bại.

`abort()` phải:

1. Xóa pending identities, sensitive data và authentication state được tạo ở `login`.
2. Nếu có state đã được commit một phần trước khi phát hiện lỗi, chỉ xóa những Principal/Credential **do module đó thêm**.
3. Đóng resource/revoke tạm theo policy; không xóa contribution của module khác.
4. Reset trạng thái nội bộ, không dùng lại kết quả login cũ cho lần khác.
5. Trả `true` khi cleanup phần đã tham gia thành công, `false` khi module vốn được bỏ qua/chưa xác thực, hoặc ném `LoginException` nếu cleanup cần thiết thất bại.

Ngay cả module thất bại trong `login` cũng có thể nhận `abort()`. Bởi vậy hàm cleanup phải an toàn khi state gần như trống.

**Không nhầm abort với logout:** abort là kết thúc một **lần đăng nhập không được chấp nhận**; logout là kết thúc một **phiên đã thành công**.

## 6. `logout()`: dọn phần đã commit của chính module

```java
boolean logout() throws LoginException;
```

Khi ứng dụng gọi `LoginContext.logout()`, JAAS gọi logout trên modules theo cơ chế đã cấu hình. Module cần remove Principal và credential do chính nó thêm ở `commit`, destroy các secret do nó quản lý và reset state.

Điểm dễ sai:

- **Không** gọi `subject.getPrincipals().clear()` vì có thể xóa identity từ module khác.
- **Không** xóa principal chỉ dựa vào class/name nếu không biết module có thật sự thêm nó.
- Với `Subject.isReadOnly() == true`, thao tác thay đổi set principals bị cấm; xử lý theo hợp đồng (destroy credentials có thể có quy tắc riêng), ném `LoginException` nếu không thể cleanup yêu cầu.
- `logout()` không tự kết thúc HTTP session, xóa browser cookie hoặc revoke OAuth token nếu module/ứng dụng chưa triển khai riêng.
- Nên kiểm thử cleanup nhiều lần và theo chính sách state thực tế, tránh double-release và rò secret.

| Tình huống | Method cần tập trung | Trạng thái Subject |
| --- | --- | --- |
| Password đúng, toàn bộ modules đúng | `commit` | Có principals/credentials đã xác thực |
| Password sai | `abort` | Không được có principal mới |
| Module A đúng, module B REQUIRED fail | `abort` của A và B | Không được còn state của lần login thất bại |
| Phiên thành công sau đó người dùng đăng xuất | `logout` | Contributions do module sở hữu được loại bỏ |
| Subject read-only | `commit/abort/logout` có thể báo lỗi cleanup | Không tự lách chính sách immutability |

## 7. Control flags quyết định đường đi tổng thể

`AppConfigurationEntry.LoginModuleControlFlag` có bốn loại:

| Flag | Khi thành công | Khi thất bại |
| --- | --- | --- |
| `REQUIRED` | Bắt buộc thành công, rồi tiếp tục xét module khác | Tổng thể sẽ thất bại, thường vẫn tiếp tục xét các module còn lại trước khi báo lỗi |
| `REQUISITE` | Bắt buộc thành công, rồi tiếp tục | Dừng sớm khi thất bại; tổng thể thất bại |
| `SUFFICIENT` | Có thể kết thúc sớm và thành công nếu không có lỗi REQUIRED/REQUISITE trước đó | Thất bại riêng không bắt buộc làm tổng thể thất bại |
| `OPTIONAL` | Có thể góp phần vào thành công | Thất bại riêng không bắt buộc làm tổng thể thất bại; khi không có module bắt buộc, vẫn cần ít nhất một SUFFICIENT/OPTIONAL thành công |

Chẳng hạn:

```text
PasswordModule REQUIRED  → login true
DenyModule     REQUIRED  → login throws FailedLoginException
----------------------------------------------
overall failure → PasswordModule.abort(), DenyModule.abort()
```

Dù PasswordModule đã trả `true`, **không được commit Principal** do một REQUIRED khác thất bại. Đây là mục tiêu của cơ chế hai pha.

`SUFFICIENT` có thể short-circuit; do đó **đừng giả định tất cả modules sẽ chắc chắn nhận mọi callback**. Thứ tự modules và flags thay đổi được control flow. `false` từ `login` nghĩa là module muốn được bỏ qua, khác với exception khi credential thật sự sai. Cấu hình flag là một phần chính sách bảo mật, cần integration tests, không chỉ unit test từng module.

## 8. Chương trình Java 21 độc lập: ba nhánh lifecycle

Ví dụ có hai nested modules:

- `PasswordModule` xác thực username `alice` cùng password **lab-only** `lab-secret`. Nó không thêm Principal trong `login`; chỉ thêm trong `commit`. `cleanup` chỉ xóa Principal do chính module sở hữu.
- `DenyModule` ném `FailedLoginException` để mô phỏng một module `REQUIRED` thứ hai từ chối, qua đó kích hoạt `abort` của cả stack.

`Configuration` được tạo **trong chương trình**, không cần file `jaas.conf`. Password literal và phép so sánh `Arrays.equals` là **mã kiểm tra luồng JAAS**, không phải thuật toán kiểm password an toàn cho production. Không có callback UI hay database thật.

Tạo file **`JaasLifecycleDemo.java`**:

```java
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Map;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

public final class JaasLifecycleDemo {
    public record UserPrincipal(String name) implements Principal {
        @Override public String getName() { return name; }
    }

    public static final class PasswordModule implements LoginModule {
        private Subject subject;
        private CallbackHandler handler;
        private UserPrincipal pending;
        private UserPrincipal owned;
        private boolean authenticated;
        private boolean committed;

        @Override public void initialize(Subject subject, CallbackHandler handler,
                                         Map<String, ?> sharedState,
                                         Map<String, ?> options) {
            System.out.println("password initialize");
            this.subject = subject;
            this.handler = handler;
        }

        @Override public boolean login() throws LoginException {
            System.out.println("password login");
            authenticated = false;
            pending = null;
            if (handler == null) throw new LoginException("Handler required");
            NameCallback name = new NameCallback("User: ");
            PasswordCallback password = new PasswordCallback("Password: ", false);
            try {
                handler.handle(new Callback[]{name, password});
                char[] supplied = password.getPassword();
                char[] expected = "lab-secret".toCharArray();
                try {
                    if (!"alice".equals(name.getName()) ||
                        !Arrays.equals(supplied, expected)) {
                        throw new FailedLoginException("Invalid credentials");
                    }
                    pending = new UserPrincipal(name.getName());
                    authenticated = true;
                    return true;
                } finally {
                    if (supplied != null) Arrays.fill(supplied, '\0');
                    Arrays.fill(expected, '\0');
                }
            } catch (IOException | UnsupportedCallbackException e) {
                LoginException failure = new LoginException("Callback error");
                failure.initCause(e);
                throw failure;
            } finally {
                password.clearPassword();
            }
        }

        @Override public boolean commit() throws LoginException {
            System.out.println("password commit");
            if (!authenticated) return false;
            if (subject.isReadOnly()) throw new LoginException("Read-only Subject");
            boolean added = subject.getPrincipals().add(pending);
            owned = added ? pending : null; // Never remove another module's Principal.
            committed = true;
            pending = null;
            return true;
        }

        private void cleanup() throws LoginException {
            if (owned != null) {
                if (subject.isReadOnly())
                    throw new LoginException("Cannot remove from read-only Subject");
                subject.getPrincipals().remove(owned);
            }
            pending = null;
            owned = null;
            authenticated = false;
            committed = false;
        }

        @Override public boolean abort() throws LoginException {
            System.out.println("password abort");
            boolean participated = authenticated || committed;
            cleanup();
            return participated;
        }

        @Override public boolean logout() throws LoginException {
            System.out.println("password logout");
            cleanup();
            return true;
        }
    }

    public static final class DenyModule implements LoginModule {
        @Override public void initialize(Subject s, CallbackHandler h,
                                         Map<String, ?> sharedState,
                                         Map<String, ?> options) {
            System.out.println("deny initialize");
        }
        @Override public boolean login() throws LoginException {
            System.out.println("deny login");
            throw new FailedLoginException("Second REQUIRED module rejects");
        }
        @Override public boolean commit() {
            System.out.println("deny commit");
            return false;
        }
        @Override public boolean abort() {
            System.out.println("deny abort");
            return false;
        }
        @Override public boolean logout() {
            System.out.println("deny logout");
            return true;
        }
    }

    static Configuration configuration(boolean addDeny) {
        return new Configuration() {
            @Override public AppConfigurationEntry[] getAppConfigurationEntry(
                    String name) {
                if (!"demo".equals(name)) return null;
                AppConfigurationEntry password = new AppConfigurationEntry(
                        PasswordModule.class.getName(),
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        Map.of());
                if (!addDeny) return new AppConfigurationEntry[]{password};
                AppConfigurationEntry deny = new AppConfigurationEntry(
                        DenyModule.class.getName(),
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        Map.of());
                return new AppConfigurationEntry[]{password, deny};
            }
        };
    }

    static CallbackHandler handler(String value) {
        return callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback name) {
                    name.setName("alice");
                } else if (callback instanceof PasswordCallback password) {
                    password.setPassword(value.toCharArray());
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };
    }

    public static void main(String[] args) throws Exception {
        System.out.println("-- success --");
        LoginContext good = new LoginContext("demo", null,
                handler("lab-secret"), configuration(false));
        good.login();
        System.out.println("principal count after commit: " +
                good.getSubject().getPrincipals(UserPrincipal.class).size());
        good.logout();
        System.out.println("principal count after logout: " +
                good.getSubject().getPrincipals(UserPrincipal.class).size());

        System.out.println("-- wrong password --");
        LoginContext wrong = new LoginContext("demo", null,
                handler("wrong"), configuration(false));
        try {
            wrong.login();
            throw new AssertionError("Unexpected success");
        } catch (LoginException expected) {
            System.out.println("wrong password rejected: true");
        }

        System.out.println("-- later REQUIRED failure --");
        LoginContext denied = new LoginContext("demo", null,
                handler("lab-secret"), configuration(true));
        try {
            denied.login();
            throw new AssertionError("Unexpected success");
        } catch (LoginException expected) {
            System.out.println("required module failure rejected: true");
        }
    }
}
```

### 8.1 Biên dịch/chạy

Sử dụng JDK 21 hoặc compiler hỗ trợ `--release 21`:

```text
java -version
javac -version
javac --release 21 -encoding UTF-8 JaasLifecycleDemo.java
java JaasLifecycleDemo
```

**Stdout đã kiểm thử chính xác:**

```text
-- success --
password initialize
password login
password commit
principal count after commit: 1
password logout
principal count after logout: 0
-- wrong password --
password initialize
password login
password abort
wrong password rejected: true
-- later REQUIRED failure --
password initialize
password login
deny initialize
deny login
password abort
deny abort
required module failure rejected: true
```

Ba kết quả cần hiểu:

**Thành công:** `login` xác thực tạm, `commit` mới thêm một `UserPrincipal` vào Subject. `logout` xóa đúng Principal do module thêm nên count từ **1 xuống 0**.

**Mật khẩu sai:** `FailedLoginException` được ném ngay trong `login`; `abort` dọn state, **không có dòng `password commit`**, caller nhận LoginException.

**Module sau thất bại:** `PasswordModule.login` đã thành công, `DenyModule.login` từ chối khi flag REQUIRED, rồi `abort` được gọi trên cả hai; không module nào được commit. Trace còn chứng minh `deny initialize` có thể xảy ra **sau** `password login`, vì JAAS có thể khởi tạo từng module khi cần.

Chương trình có public nested modules với no-arg constructor; `AppConfigurationEntry` lấy binary class name từ `PasswordModule.class.getName()`, tránh lỗi tên khi nạp class lồng nhau. Kết quả này đã được biên dịch và chạy với `javac --release 21` trên JDK có JAAS chuẩn.

### 8.2 Giải thích lựa chọn trạng thái trong code

- `pending` chỉ có sau khi credential đúng, không tự làm Subject có principal.
- `authenticated` lưu kết quả pha 1 trong instance, tránh truyền “thành công” qua shared static variable.
- `owned` chỉ được gán nếu `subject.getPrincipals().add(pending)` trả `true`. Nếu Principal bằng giá trị đã tồn tại, module không nhận ownership và không xóa người khác ở logout.
- `commit` kiểm `subject.isReadOnly()` trước khi sửa; nếu không thể commit thì ném LoginException.
- `cleanup` xóa `owned`, đặt `pending` và các boolean về trạng thái sạch. `abort` gọi cleanup ngay cả khi `login` chưa thành công.
- `DenyModule.abort` trả `false` bởi module không có successful state để commit; đây không phải một lần đăng nhập thành công bị đảo ngược.
- `PasswordCallback.clearPassword()` và `Arrays.fill` dọn những mảng có thể dọn; String literal trong demo vẫn tồn tại theo semantics JVM, không phải kỹ thuật xử lý secret production.

## 9. Cấu hình ngoài bằng JAAS file và khác biệt với ví dụ

Một cách phổ biến khác là dùng file cấu hình JAAS:

```text
demo {
    JaasLifecycleDemo$PasswordModule required;
    JaasLifecycleDemo$DenyModule required;
};
```

Dấu `$` là một phần **binary class name** của nested class trong ví dụ. Trong dự án thật, một LoginModule thường là class top-level ở package được quản lý. Mỗi stanza gồm tên class, flag và options; file phải nằm ở nơi deployment có quyền đọc, không chứa mật khẩu dạng hardcoded.

Khi dùng installed `Configuration` từ file, ứng dụng thường khởi tạo `new LoginContext("demo", callbackHandler)` hoặc tương đương. Có thể cấu hình location qua `java.security.auth.login.config` theo cơ chế JDK/triển khai. **Ví dụ Java ở phần 8 không sử dụng file này** vì truyền `Configuration` trong bộ nhớ trực tiếp vào constructor; đừng đặt file rồi kỳ vọng chương trình tự đọc nó.

Cấu hình `required` của file tương ứng `AppConfigurationEntry.LoginModuleControlFlag.REQUIRED`, nhưng phải kiểm thứ tự modules và các options. Không dùng quyền mặc định hoặc “other” entry mà chưa hiểu đối tượng được xác thực.

## 10. Trường hợp biên cần kiểm thử khi viết LoginModule

### 10.1 Một module thành công nhưng module khác fail

Đây là kịch bản quan trọng để chứng minh **tính nhất quán của Subject**. Test phải xác nhận `commit` **không** được gọi trên module trước khi tổng thể chấp nhận, và sau `abort` không còn pending identities hoặc external tokens.

### 10.2 Commit thất bại sau khi đã tạo một phần state

Ví dụ module đã thêm một group Principal rồi bước tạo credential thất bại. `commit` phải ném LoginException; cleanup/abort phải xóa group Principal **thuộc module đó**, đồng thời revoke resources ngoài hệ thống. JAAS không rollback database tự động. Hãy viết tests cho partial commit, không chỉ success/login fail.

### 10.3 Subject đã read-only

`Subject.setReadOnly()` ngăn thay đổi các tập principals/credentials theo cách thông thường. Nếu module phải ghi Subject ở commit, cần xử lý bằng LoginException. Logout có thể không remove được một Principal thuộc module; cần tuân thủ semantics read-only và các credential hỗ trợ destroy; không ép truy cập bằng cách bỏ kiểm tra.

### 10.4 Duplicate Principal từ module khác

Nếu Subject có sẵn `UserPrincipal("alice")`, lệnh `add` theo set semantics có thể trả false. Nếu module A không thêm phần tử mới nhưng vẫn `logout` và xóa theo tên/class, module có thể xóa state của B. Theo dõi **ownership object** và xử lý duplicate đúng.

### 10.5 Shared state và nhiều lần đăng nhập

`sharedState` là map chia sẻ giữa modules được cấu hình. Khi dùng để trao đổi tên user hoặc credential, phải giới hạn key, type, quyền đọc và xóa secret sau khi dùng. Không tái sử dụng module instance/`LoginContext` cho nhiều Subject; tài liệu `LoginContext` lưu ý không dùng một context để xác thực **nhiều Subject**.

Nếu ứng dụng retry, state của lần cũ phải được reset. Không giữ password hoặc pending principal khi lần đầu thất bại rồi lần hai bắt đầu, và không có static fields dùng chung cho mọi account.

### 10.6 Không biến JAAS thành nơi lưu session

JAAS quản lý quá trình xác thực và cập nhật Subject. Token, cookie, SSO, expiration, revocation, timeout, authorization, audit và network/TLS còn thuộc các tầng liên quan khác. `LoginContext.logout` không tự thu hồi JWT đã phát nếu hệ thống chưa triển khai cơ chế thu hồi tương ứng.

## 11. Bảng lỗi thường gặp

| Hiện tượng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Module `login` đúng nhưng app login vẫn fail | Module REQUIRED/REQUISITE khác thất bại | Kiểm cấu hình và trace từng module, xác nhận abort |
| `Subject` có Principal sau khi tổng thể login fail | Module thêm quá sớm trong login, thiếu abort cleanup | Chỉ thêm trong commit; theo dõi ownership |
| Không có `commit` dù PasswordModule đã true | Overall failure hoặc short-circuit theo flags | Kiểm `LoginContext` và control flags |
| `CallbackHandler` bị null/NPE | Constructor/config không cung cấp handler | Check null, ném LoginException có kiểm soát |
| `UnsupportedCallbackException` | Handler không hỗ trợ Name/Password callback | Kiểm hợp đồng callback, không log secret |
| Mật khẩu sai nhưng vẫn đăng nhập được | Trả false thay vì FailedLoginException, cấu hình OPTIONAL/SUFFICIENT yếu | Kiểm phân biệt ignored/failure và policy |
| `commit` ném lỗi read-only Subject | Set principal/credential bị khóa | Kiểm lifecycle setReadOnly và xử lý cleanup |
| Logout xóa Principal của module khác | Xóa theo class/name thay vì ownership | Chỉ xóa contribution thực sự đã thêm |
| Logout không dọn credential | Không implement cleanup hoặc thiếu destroy | Test Subject trước/sau, xử lý secret resources |
| Retry dùng lại kết quả cũ | Không reset boolean/pending identity | Mỗi lần xử lý state rõ ràng; dùng LoginContext riêng |
| `LoginModule` không được nạp | Sai binary name, JAR/classpath, no-arg constructor | Kiểm deployment và `Class.getName()` |
| Trace để lộ password/token | In callbacks/exception details hoặc debug state | Chỉ log phase/result, ẩn secret và PII |

**Không** tự thêm delay hoặc retry vòng lặp trong LoginModule để “chống brute force”. Đặt giới hạn tốc độ, MFA, kiểm soát lockout và giám sát ở những tầng được thiết kế; tránh làm module bị treo hoặc đưa thông tin sai về kết quả.

## 12. Bài tập và checklist hoàn thành

1. Chạy chương trình Java 21, vẽ ba luồng từ `initialize` đến `commit/abort/logout`. Vì sao không có `commit` khi mật khẩu sai?
2. Dùng `DenyModule REQUIRED` để giải thích trường hợp một module đã `login true` nhưng **không có Principal** mới trong Subject.
3. Thay `DenyModule` thành `OPTIONAL` rồi dự đoán kết quả tổng thể. Hãy lý giải cách flag thay đổi điều kiện success/failure.
4. Thiết kế một `LoginModule` trả `false` khi không nhận loại credential phù hợp. Vì sao làm như vậy khác với ném `FailedLoginException` khi password sai?
5. Thêm một `UserPrincipal("alice")` trước khi commit; xem `Set.add` trả true hay false. Khi logout, module có quyền xóa Principal đã tồn tại trước đó không?
6. Viết unit test `abort` trên state trống: module đã bị từ chối ngay trong `login` vẫn phải dọn an toàn.
7. Giả lập lỗi ở giữa `commit` sau khi thêm một role Principal. Xác định cách xóa role đó và bù trừ external side effect mà JAAS không tự thực hiện.
8. Gọi `Subject.setReadOnly` trước commit. Phải xử lý thế nào để không báo đăng nhập thành công giả?
9. Xây một `CallbackHandler` an toàn không dùng literal password; mô tả khi nào `clearPassword` và `Arrays.fill` được gọi.
10. Nếu user đã đăng xuất nhưng web JWT vẫn dùng được, phần nào của hệ thống chịu trách nhiệm sửa? `LoginContext.logout` có tự revoke JWT không?
11. Với `SUFFICIENT` thành công sớm, có được giả định mọi module sau đều được initialize/login không? Hãy giải thích ảnh hưởng của short-circuit.
12. Thiết kế test concurrency cho hai Subject khác nhau. Vì sao một mutable static `Principal` hay password buffer là lỗi nghiêm trọng?

**Tiêu chí hoàn thành:** bạn xác định được **initialize = nhận context; login = xác thực và giữ state tạm; commit = ghi Subject khi cả stack được chấp nhận; abort = dọn khi thất bại; logout = dọn contribution của phiên đã thành công**, và không nhầm JAAS với password hashing, quản trị session hoặc authorization.

## 13. Tài liệu tham khảo

- [Java SE 21 — `javax.security.auth.spi.LoginModule`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/security/auth/spi/LoginModule.html): hợp đồng năm phương thức, two-phase login và kết quả boolean/exception.
- [Java SE 21 — `javax.security.auth.login.LoginContext`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/security/auth/login/LoginContext.html): điều phối modules, constructor, Subject và logout.
- [Java SE 21 — `Configuration`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/security/auth/login/Configuration.html) và [`AppConfigurationEntry`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/security/auth/login/AppConfigurationEntry.html): module flags và configuration.
- [Java SE 21 — `Subject`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/security/auth/Subject.html): principal/credential sets và read-only.
- [Java SE 21 — `CallbackHandler`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/security/auth/callback/CallbackHandler.html) và [`PasswordCallback`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/security/auth/callback/PasswordCallback.html): lấy và dọn input.
- [Oracle Java 21 — JAAS LoginModule Developer's Guide](https://docs.oracle.com/en/java/javase/21/security/java-authentication-and-authorization-service-jaas-loginmodule-developers-guide1.html): hai pha, initialize/login/commit/abort/logout.
- [Oracle Java 21 — JAAS Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-authentication-and-authorization-service-jaas-reference-guide.html): control flags, JAAS config và Subject.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jaas-authentication-006">17.6 required, requisite, sufficient, optional và phối hợp nhiều LoginModule</a>

<details>
<summary>Xem chi tiết</summary>

# 17.6 required, requisite, sufficient, optional và phối hợp nhiều LoginModule

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích cách **JAAS (Java Authentication and Authorization Service)** dùng `LoginContext`, `Configuration`, `LoginModule`, `Subject`, `Principal` và `CallbackHandler` để kết hợp nhiều nguồn xác thực trong Java 17.
2. Phân biệt bốn **LoginModule control flags**: `required`, `requisite`, `sufficient` và `optional` — đặc biệt là quy tắc tiếp tục, dừng sớm và kết quả **toàn bộ stack**.
3. Dự đoán đúng các trường hợp khó: `required` thất bại nhưng `sufficient` phía sau thành công; `sufficient` thành công trước một `required`; nhiều `optional`; `requisite` thất bại giữa stack.
4. Hiểu hai giai đoạn của JAAS: `login()` để kiểm tra từng module, sau đó `commit()` hoặc `abort()`; dùng `logout()` để gỡ identity/credential do module thêm.
5. Tạo và chạy **hai chương trình Java 17 độc lập**: mô phỏng ma trận control flags và chứng minh cấu hình **MFA có OTP `optional`** có thể vô tình cho phép đăng nhập khi OTP sai.
6. Phân biệt **OR (các cơ chế thay thế)** với **AND (nhiều yếu tố bắt buộc)**; thiết kế cấu hình, callback, shared state, quản lý lỗi và kiểm thử để tránh đường vòng bảo mật.

## 1. JAAS hỗ trợ “stacked authentication” như thế nào?

JAAS là mô hình đăng nhập có thể thay thế và kết hợp nhiều thành phần xác thực. Ứng dụng không cần biết chi tiết đang kiểm password, Kerberos, smart card hay một cơ chế khác; nó gọi `LoginContext`. Các thành phần xác thực cụ thể triển khai interface `javax.security.auth.spi.LoginModule`.

Một sơ đồ:

```text
Application
    |
    | new LoginContext("AppName", handler)
    | context.login()
    v
JAAS LoginContext
    |
    +--> Configuration: AppName
    |       |
    |       +-- PasswordModule  required
    |       +-- OtpModule       required
    |       +-- AuditModule     optional
    |
    +--> LoginModule.login() theo thứ tự
    |
    +--> Đánh giá các control flags
    |
    +--> Nếu login phase thành công: commit()
    |      và cập nhật Subject/Principal
    |
    +--> Nếu login phase thất bại: abort()
           và xóa trạng thái tạm
    |
    v
Application nhận Subject nếu thành công
```

Các khái niệm:

| Thành phần | Vai trò |
| --- | --- |
| `LoginContext` | Đầu mối ứng dụng gọi `login()`, `getSubject()`, `logout()`; điều phối nhiều module |
| `Configuration` | Danh sách module, **thứ tự**, flag, options theo tên ứng dụng |
| `AppConfigurationEntry` | Mô tả một module class, một flag, options map |
| `LoginModule` | Kiểm một hình thức xác thực, quản lý lifecycle của trạng thái/Principal |
| `CallbackHandler` | Cơ chế module lấy thông tin đầu vào, ví dụ username/password hoặc mã xác thực |
| `sharedState` | Map cùng phiên đăng nhập được chia sẻ giữa các module; cần tránh chứa secret không cần thiết |
| `Subject` | Thực thể đã xác thực và tập `Principal`/credentials của nó |
| `Principal` | Danh tính/thuộc tính chủ thể ở mức identity; **không tự chứng minh quyền truy cập** |

Lời gọi ứng dụng có thể rất đơn giản:

```java
LoginContext context = new LoginContext("MyApplication", callbackHandler);
context.login(); // Thành công thì trả về; thất bại ném LoginException
Subject subject = context.getSubject();
// Sử dụng subject theo chính sách ứng dụng, rồi:
context.logout();
```

Đây là **đoạn code nhúng**, không phải file Java độc lập: cần imports, định nghĩa `callbackHandler` và cấu hình JAAS cho tên `MyApplication`. `LoginContext` quyết định module nào được gọi dựa trên cấu hình, không phải caller tự chạy từng `LoginModule.login()` để “lấy phiếu”.

**Thứ tự module là một phần của chính sách bảo mật.** Cùng danh sách nhưng đổi vị trí `sufficient` hoặc `requisite` có thể đổi kết quả và số cơ chế xác thực thực sự được yêu cầu.

## 2. Cấu trúc cấu hình và bốn control flags

Một login configuration file thường có cấu trúc:

```text
ApplicationName {
    module.class.Name required  optionA="value";
    module.class.Name requisite optionB="value";
    module.class.Name sufficient optionC="value";
    module.class.Name optional  optionD="value";
};
```

Đây là **cú pháp minh họa cấu trúc**, không phải cấu hình chạy được vì các class name trong đoạn này chỉ mô tả vị trí và không phải class đã được triển khai. Mỗi module entry kết thúc bằng `;`, nhóm ứng dụng kết thúc bằng `};`. Tên app được truyền vào `LoginContext`.

Tài liệu `Configuration` của Java 17 nêu chính xác:

| Flag | Phải thành công để toàn bộ stack hợp lệ? | Nếu `login()` thành công | Nếu `login()` thất bại |
| --- | --- | --- | --- |
| **`required`** | **Có** | Tiếp tục module phía sau | **Vẫn tiếp tục**, nhưng kết quả toàn stack sẽ fail |
| **`requisite`** | **Có** | Tiếp tục module phía sau | **Dừng phase login ngay**, trả failure |
| **`sufficient`** | Không bắt buộc | **Có thể kết thúc sớm thành công** nếu **chưa có `required`/`requisite` phía trước thất bại** | Tiếp tục module phía sau |
| **`optional`** | Không bắt buộc | Tiếp tục module phía sau | Tiếp tục module phía sau |

**Điều kiện tổng thể:** tất cả module `required` và `requisite` **được áp dụng trước khi kết thúc sớm hợp lệ** phải thành công. Nếu cấu hình **không có `required`/`requisite`**, ít nhất **một** `sufficient` hoặc `optional` phải thành công. `SUFFICIENT` thành công có thể chặn việc thực thi các module nằm phía sau; do đó không thể hiểu câu “mọi required luôn phải chạy” một cách tách rời thứ tự cấu hình.

### 2.1 `required` — bắt buộc nhưng không dừng ngay khi thất bại

Ví dụ:

```text
PasswordCheck  required  -> FAIL
DeviceCheck    optional  -> PASS
AuditCheck     optional  -> PASS

Kết quả cuối: FAIL
```

`LoginContext` vẫn gọi các module sau `PasswordCheck`. Ưu điểm: giúp tránh tiết lộ quá rõ việc thất bại xảy ra ở bước nào chỉ bằng **việc dừng ngay**, và cho phép các thành phần khác thực hiện công việc đã được thiết kế. Nhưng điều này **không tự đảm bảo thời gian phản hồi constant-time** vì các module có thể mất thời gian khác nhau.

**Không** xem kết quả `optional` thành công là “cứu” được một `required` đã thất bại.

### 2.2 `requisite` — bắt buộc và có khả năng short-circuit thất bại

Ví dụ:

```text
AccountExists  requisite -> FAIL
PasswordCheck  required  -> KHÔNG login()
OtpCheck       required  -> KHÔNG login()

Kết quả cuối: FAIL
```

Nếu `requisite` thất bại, JAAS dừng **phase kiểm tra `login()`** ngay. Tuy nhiên, cơ chế cleanup `abort()` vẫn được gọi theo lifecycle; “dừng sớm” không có nghĩa bỏ sạch xử lý hậu kỳ.

Đặt `requisite` ở phía trước có thể **tiết kiệm chi phí** khi không cần xử lý các bước sau lúc một điều kiện bắt buộc đã hỏng. Đổi lại, thời gian hoặc cách truy cập external system có thể lộ dấu hiệu lý do lỗi nếu không thiết kế thông báo và timing phù hợp.

### 2.3 `sufficient` — đủ để hoàn tất sớm nếu không có thất bại bắt buộc trước đó

Hai ví dụ cần phân biệt:

```text
Case A:
PasswordCheck  required   -> PASS
TrustedTicket  sufficient -> PASS
ExtraCheck     optional   -> KHÔNG login()

Kết quả: PASS (không có mandatory failure phía trước)
```

```text
Case B:
PasswordCheck  required   -> FAIL
TrustedTicket  sufficient -> PASS
ExtraCheck     optional   -> vẫn login()

Kết quả: FAIL (required failure đã xảy ra)
```

Điều kiện “không có lỗi bắt buộc trước đó” là **điểm dễ bị ghi thiếu** khi tóm tắt JAAS. `SUFFICIENT` không xóa một lỗi `REQUIRED`/`REQUISITE` đã phát sinh.

Ngược lại, `sufficient` **ở quá sớm** có thể tạo lỗ hổng policy:

```text
CachedTicket   sufficient -> PASS
PasswordCheck  required   -> KHÔNG login()

Kết quả: PASS
```

Nếu business rule yêu cầu password **luôn phải kiểm**, cách xếp trên là sai. Không thể nói “PasswordCheck được đánh dấu required, nên chắc chắn password bắt buộc” khi đã có `sufficient` được phép dừng sớm trước đó.

### 2.4 `optional` — không phải “cấm”, cũng không phải “phải kiểm”

`Optional` có thể thành công hoặc thất bại mà vẫn cho JAAS chạy tiếp:

```text
PasswordCheck  required -> PASS
OtpCheck       optional -> FAIL

Kết quả: PASS
```

Nếu team nghĩ OTP là **bắt buộc**, đây là một cấu hình MFA sai nghiêm trọng. `optional` thích hợp cho module bổ sung hợp lệ theo thiết kế (ví dụ optional environment identity enrichment), không phải cho yếu tố bảo mật quan trọng mà user **phải** vượt qua.

Khi chỉ có các module `optional`:

```text
A optional -> FAIL
B optional -> PASS
=> PASS

A optional -> FAIL
B optional -> FAIL
=> FAIL
```

Ít nhất một module phải thành công nếu không có module mandatory. Đây là quy tắc aggregate, **không phải `optional` nghĩa “luôn cho phép login”**.

## 3. Cách hình dung thuật toán tổng hợp

Để dự đoán kết quả, có thể dùng mô hình khái niệm sau (không phải source code nội bộ JDK):

```text
mandatoryFailure = false
anySuccess = false

for each LoginModule in Configuration order:
    result = module.login()

    nếu REQUIRED:
        thành công -> anySuccess = true
        thất bại  -> mandatoryFailure = true
        luôn tiếp tục

    nếu REQUISITE:
        thành công -> anySuccess = true, tiếp tục
        thất bại  -> dừng phase login, overall FAIL

    nếu SUFFICIENT:
        thành công:
            anySuccess = true
            nếu !mandatoryFailure: kết thúc phase login sớm PASS
            nếu mandatoryFailure: tiếp tục, overall cuối vẫn FAIL
        thất bại -> tiếp tục

    nếu OPTIONAL:
        thành công -> anySuccess = true
        thất bại  -> tiếp tục

sau phase login:
    nếu mandatoryFailure: overall FAIL
    nếu không mandatoryFailure và có đủ thành công: overall PASS
    nếu không có module nào xác thực thành công: overall FAIL

overall PASS -> commit phase
overall FAIL -> abort phase
```

Mô hình cố ý giản lược các chi tiết `LoginModule.login()` trả `false` (module xin **bị bỏ qua**, xem mục 8), thứ tự exception ưu tiên và cơ chế commit/abort; không dùng nó để tự viết engine JAAS thay `LoginContext`.

Một cách kiểm tra nhanh khi review config:

1. Đọc module **từ trên xuống**.
2. Đánh dấu lỗi `required`/`requisite` bắt buộc.
3. Nếu một `requisite` thất bại: dừng phase login ngay.
4. Nếu `sufficient` thành công: kiểm **trước nó** có mandatory failure không; nếu không có, các module **sau nó không còn là điều kiện login**.
5. Nếu hết stack: tất cả mandatory phải pass; trường hợp toàn optional/sufficient phải có ít nhất một pass.

## 4. Thực hành 1 — Java 17 kiểm chứng ma trận tám trường hợp

Chương trình sau tạo **một `Configuration` ở bộ nhớ**, không thay đổi global JAAS configuration hoặc file của máy. Nó dùng `ProbeModule` giả lập kết quả `login()` dựa trên options của từng entry: `pass`, `fail` hoặc `ignore`. Mục tiêu là nhìn thấy **module nào được chạy** và **kết quả toàn stack**.

Dữ liệu là giả lập, **không xác thực người dùng thật**. Không dùng cơ chế “`behavior=pass`” làm LoginModule production. Chương trình vẫn triển khai đầy đủ interface `LoginModule` và dùng `LoginContext` thật để kiểm nghiệm hành vi.

Tạo file **`JaasFlagsMatrixDemo.java`**:

```java
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

public class JaasFlagsMatrixDemo {
    static final List<String> CALLED = new ArrayList<>();

    static final class DemoPrincipal implements Principal {
        private final String name;
        DemoPrincipal(String name) { this.name = name; }
        public String getName() { return name; }
    }

    public static class ProbeModule implements LoginModule {
        private Subject subject;
        private String name;
        private String behavior;
        private boolean passed;
        private DemoPrincipal principal;

        public ProbeModule() {}

        public void initialize(Subject subject, CallbackHandler handler,
                Map<String, ?> sharedState, Map<String, ?> options) {
            this.subject = subject;
            name = (String) options.get("name");
            behavior = (String) options.get("behavior");
            passed = false;
        }

        public boolean login() throws LoginException {
            CALLED.add(name);
            if ("fail".equals(behavior)) {
                throw new LoginException(name + " rejected");
            }
            if ("ignore".equals(behavior)) return false;
            passed = true;
            return true;
        }

        public boolean commit() {
            if (!passed) return false;
            principal = new DemoPrincipal(name);
            subject.getPrincipals().add(principal);
            return true;
        }

        public boolean abort() {
            logout();
            passed = false;
            return true;
        }

        public boolean logout() {
            if (principal != null) subject.getPrincipals().remove(principal);
            principal = null;
            passed = false;
            return true;
        }
    }

    static AppConfigurationEntry entry(String name,
            AppConfigurationEntry.LoginModuleControlFlag flag, String behavior) {
        return new AppConfigurationEntry(ProbeModule.class.getName(), flag,
                Map.of("name", name, "behavior", behavior));
    }

    static void trial(String label, AppConfigurationEntry... stack)
            throws Exception {
        CALLED.clear();
        Configuration config = new Configuration() {
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                return "lab".equals(name) ? stack.clone() : null;
            }
        };
        LoginContext context = new LoginContext(
                "lab", new Subject(), (CallbackHandler) null, config);
        String result = "PASS";
        try {
            context.login();
        } catch (LoginException e) {
            result = "FAIL";
        }
        System.out.println(label + " -> " + result +
                "; login calls=" + String.join(",", CALLED));
        if ("PASS".equals(result)) context.logout();
    }

    public static void main(String[] args) throws Exception {
        var required = AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;
        var requisite = AppConfigurationEntry.LoginModuleControlFlag.REQUISITE;
        var sufficient = AppConfigurationEntry.LoginModuleControlFlag.SUFFICIENT;
        var optional = AppConfigurationEntry.LoginModuleControlFlag.OPTIONAL;

        trial("required-fail-then-sufficient-pass",
              entry("A", required, "fail"),
              entry("B", sufficient, "pass"),
              entry("C", optional, "pass"));
        trial("required-pass-sufficient-pass",
              entry("A", required, "pass"),
              entry("B", sufficient, "pass"),
              entry("C", optional, "fail"));
        trial("requisite-fail-stops",
              entry("A", optional, "pass"),
              entry("B", requisite, "fail"),
              entry("C", optional, "pass"));
        trial("only-optional-one-pass",
              entry("A", optional, "fail"),
              entry("B", optional, "pass"));
        trial("only-optional-all-fail",
              entry("A", optional, "fail"),
              entry("B", optional, "ignore"));
        trial("required-pass-optional-fail",
              entry("A", required, "pass"),
              entry("B", optional, "fail"));
        trial("sufficient-before-required",
              entry("A", sufficient, "pass"),
              entry("B", required, "fail"));
        trial("sufficient-fail-required-pass",
              entry("A", sufficient, "fail"),
              entry("B", required, "pass"));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 JaasFlagsMatrixDemo.java
java JaasFlagsMatrixDemo
```

**Stdout đã kiểm tra bằng JDK có Java 17-compatible APIs:**

```text
required-fail-then-sufficient-pass -> FAIL; login calls=A,B,C
required-pass-sufficient-pass -> PASS; login calls=A,B
requisite-fail-stops -> FAIL; login calls=A,B
only-optional-one-pass -> PASS; login calls=A,B
only-optional-all-fail -> FAIL; login calls=A,B
required-pass-optional-fail -> PASS; login calls=A,B
sufficient-before-required -> PASS; login calls=A
sufficient-fail-required-pass -> PASS; login calls=A,B
```

### 4.1 Phân tích từng dòng kết quả

| Kịch bản | Đọc thứ tự module | Kết quả và lý do |
| --- | --- | --- |
| `required-fail-then-sufficient-pass` | A required fail, B sufficient pass, C optional pass | **FAIL**: lỗi mandatory A tồn tại; B không thể short-circuit để xóa lỗi A |
| `required-pass-sufficient-pass` | A required pass, B sufficient pass, C optional fail | **PASS**: B đủ điều kiện kết thúc sớm, C không được gọi trong login phase |
| `requisite-fail-stops` | A optional pass, B requisite fail, C optional pass | **FAIL**: B dừng phase login ngay, C không được gọi |
| `only-optional-one-pass` | A optional fail, B optional pass | **PASS**: không có mandatory và ít nhất một optional pass |
| `only-optional-all-fail` | A optional fail, B optional return false | **FAIL**: không có module nào xác thực thành công |
| `required-pass-optional-fail` | A required pass, B optional fail | **PASS**: B không bắt buộc |
| `sufficient-before-required` | A sufficient pass, B required fail nếu được gọi | **PASS**: B **không được gọi** vì A kết thúc sớm; đây là nguy cơ sai cấu hình |
| `sufficient-fail-required-pass` | A sufficient fail, B required pass | **PASS**: A không bắt buộc, B pass |

**Chú ý:** bảng và stdout theo dõi **những lời gọi `login()`**. Khi một `sufficient` kết thúc phase login sớm, vòng đời phase hai của JAAS vẫn cần được giải quyết. Không dùng danh sách `login calls` để tự suy ra chắc chắn mỗi `commit()/abort()` trên mọi provider đã được gọi thế nào; `LoginModule` phải xử lý an toàn ngay cả khi module chưa có trạng thái login thành công.

### 4.2 Giải thích code

1. `AppConfigurationEntry` mang class name, control flag và options. Trong một stack, cùng class `ProbeModule` được dùng nhiều lần với tên/behavior khác nhau — các instance riêng vẫn có thể xuất hiện.
2. `Configuration#getAppConfigurationEntry("lab")` trả **danh sách entry đúng thứ tự**. Demo cung cấp cấu hình trực tiếp qua constructor `LoginContext(..., config)`, không đổi cấu hình bảo mật toàn JVM.
3. `ProbeModule.login()` minh họa **true** khi thành công, **LoginException** khi thất bại, **false** khi module chọn bị bỏ qua. Trên hệ thống thật phải kiểm credential/identity đúng nghĩa.
4. `commit()` chỉ thêm `Principal` vào `Subject` khi module có `passed=true`. Nếu module không login thành công, trả false để không thêm danh tính giả.
5. `abort()` và `logout()` dọn `Principal` do module thêm, không xóa `Principal` của module khác; vòng đời này cần được triển khai chính xác để tránh state leak.
6. `CALLED` là danh sách static chỉ dùng để **quan sát một process lab chạy tuần tự**, không phù hợp làm global request trace cho dịch vụ đa luồng.

## 5. Hai giai đoạn `login/commit/abort`: chi tiết bảo mật rất quan trọng

Nhiều người chỉ đọc bảng flag mà bỏ qua lifecycle. Theo `LoginModule` Developer's Guide:

**Giai đoạn 1 — thử xác thực:**
- `initialize(Subject, CallbackHandler, sharedState, options)` trước các lời gọi của module.
- `login()` xác minh credential hoặc trạng thái đối với cơ chế đó và ghi **trạng thái tạm riêng** (ví dụ `succeeded`).
- Module nên **không** vội công bố identity/credential lâu dài cho `Subject` ngay khi `login()` thành công: các module mandatory **khác có thể vẫn thất bại**.
- `login()` trả `true` nếu module xác thực thành công; trả `false` nếu module chọn “ignore”; ném `LoginException` nếu authentication thất bại.

**Giai đoạn 2 — hoàn tất hoặc hủy:**
- Nếu kiểm tra tổng thể thành công, `LoginContext` gọi `commit()` trên các module theo lifecycle: module có trạng thái thành công thêm `Principal`/credential được phép vào `Subject`.
- Nếu tổng thể thất bại, `abort()` được gọi để hủy trạng thái đăng nhập tạm, xóa password/token ngắn hạn và tránh rò rỉ state.
- Nếu `commit()` ở một module thất bại, engine phải xử lý failure/abort; bản thân module cần thực hiện cleanup đầy đủ.
- Sau phiên thành công, `LoginContext.logout()` gọi `logout()` để gỡ những identity/credentials **do module đó thêm**, không xóa của module khác.

```text
Module A.login() PASS ---+
                         |
Module B.login() FAIL ---+--> Overall FAIL
                         |
                         v
                 abort() / cleanup
                 (KHÔNG publish Subject được xác thực)
```

```text
Module A.login() PASS ---+
                         |
Module B.login() PASS ---+--> Overall PASS
                         |
                         v
                       commit()
                         |
                         v
                   Subject Principals
                         |
                         v
                       logout()
                         |
                         v
                Dọn identity/credential đã thêm
```

**Không được tự gọi `module.commit()` để “cứu” một stack thất bại.** Giao thức hai phase bảo vệ tính nhất quán của danh tính giữa nhiều module và hỗ trợ rollback.

### 5.1 Đừng nhầm `LoginModule.login()` trả `false` và ném exception

Theo interface `LoginModule`:

- `return true`: module xác thực thành công.
- `return false`: module muốn được **bỏ qua** (ignored), không phải hình thức báo rõ một lần kiểm tra credential đã thất bại.
- `throw new LoginException(...)`: module báo **authentication failed**.

Vì flag được đánh giá trên kết quả module, **không** tùy tiện thay việc ném `LoginException` bằng `return false` trong module dùng làm chính sách bắt buộc. Việc module tự tuyên bố ignored có semantics khác với explicit failed và có thể gây hậu quả không mong đợi cho quá trình tổng hợp.

Hãy tuân theo contract của module/provider thực tế và có unit/integration tests cho cả ba kiểu kết quả. Đừng dùng `catch (Exception ignored) { return true; }` hoặc nuốt lỗi network/credential rồi cho JAAS pass.

### 5.2 `Subject` sau login có đúng những danh tính nào?

`Subject` chứa tập `Principal`, public credentials và private credentials. Mỗi module có thể đóng góp danh tính (account ID, realm, role đã xác thực...) trong `commit()` nếu module đó thực sự thành công.

Hai vấn đề:

- **Conflicting identity**: nếu password module xác thực user A nhưng token module xác thực user B và cả hai cùng trả pass, hệ thống không nên tự xem đó là cùng một chủ thể. Cần ràng buộc identity/tenant/session xuyên module.
- **Privilege pollution**: nếu một module không bắt buộc thêm role/quyền một cách thiếu kiểm soát, `Subject` có thể mang Principal có ý nghĩa quá mạnh so với bằng chứng. Tách việc **xác thực danh tính** với **ủy quyền thao tác** dựa trên policy.

`Principal` chỉ là object đại diện danh tính. Bản thân việc có `Principal("admin")` trong `Subject` không phải chứng chỉ cho phép truy cập nếu ứng dụng không xác minh nguồn và quy tắc cấp quyền.

## 6. AND versus OR: chọn flag để biểu đạt chính sách

### 6.1 Chính sách AND — phải qua nhiều điều kiện

Ví dụ ứng dụng đòi **password và OTP**:

```text
PasswordModule required
OtpModule      required
```

Tổng thể pass nếu **cả hai** pass. Một trong hai fail thì phải reject, nhưng `required` vẫn cho module sau chạy. Nếu muốn dừng ngay khi password fail để tránh OTP request vô ích (với đánh đổi về timing), có thể:

```text
PasswordModule requisite
OtpModule      required
```

Cả hai vẫn là mandatory khi được gọi theo thứ tự. Tuy nhiên **đặt một `sufficient` trước các bước bắt buộc** có thể biến “AND” thành OR ngoài ý muốn.

### 6.2 Chính sách OR — một cơ chế đáng tin cậy có thể đủ

Ví dụ một ứng dụng có thể xác thực bằng **một trong hai cơ chế thay thế**, với policy cho phép:

```text
KerberosTicketModule sufficient
FallbackPasswordModule required
```

Nếu ticket hợp lệ, stack có thể kết thúc sớm, không kiểm password; nếu ticket không hợp lệ, module password trở thành đường thay thế bắt buộc. Chỉ dùng nếu **hai cơ chế thực sự được tổ chức đánh giá là đủ độc lập cho cùng mức bảo mật**, không phải một yếu tố yếu được phép bỏ qua yếu tố mạnh.

Không được viết “`sufficient` == MFA thành công” hay “`required` được bỏ qua vì đã có một cơ chế khác” như quy tắc tổng quát. Sự cho phép bypass phải được mô tả trong threat model và test.

### 6.3 Một module “audit” nên ở đâu?

Nếu audit/logging là **hành động bắt buộc**, không nên dựa vào một `AuditModule optional` đứng sau `SufficientModule`: module audit có thể không được gọi ở phase login khi sufficient short-circuit. Audit tập trung ở tầng điều phối trước/sau `LoginContext.login()` hoặc hệ thống eventing được thiết kế rõ có thể phù hợp hơn, và phải test các đường dừng sớm/failure.

Vì `LoginModule` dành cho authentication, việc nhét mọi side effect như audit, provisioning, billing vào callback đăng nhập có thể khiến các control flags bất ngờ thay đổi cách hoạt động của chúng.

## 7. Thực hành 2 — Minh họa MFA bị bỏ qua khi OTP chuyển thành OPTIONAL

Bài lab sau dùng hai `LoginModule`:

1. `PasswordModule` mô phỏng xác minh username/password cố định cho **user lab**.
2. `OtpModule` mô phỏng so mã OTP cố định trong bộ nhớ **chỉ để minh họa control flag**, không triển khai TOTP/HOTP/chống replay thật.

Hai module dùng chung một `Subject`, `CallbackHandler` và `sharedState`. `PasswordModule` ghi **username không bí mật** vào shared state sau khi xác minh; `OtpModule` đọc username này rồi xác minh OTP. Đừng lưu password, OTP hoặc khóa MFA vào shared state theo cách không kiểm soát.

Chương trình thử ba cấu hình:

- Password required, OTP required và mã đúng → **PASS** với hai Principal.
- Password required, OTP required và mã sai → **FAIL**.
- Password required, OTP **optional** và mã sai → **PASS** với **một** Principal: đây là bằng chứng rằng optional không thực thi MFA.

**Security notice:** password `lab-pass` và OTP `123456` cố định chỉ dành cho lab. `Arrays.equals` trên mảng password demo không thay thế password KDF/PBKDF2/Argon2id trong sản phẩm. Đây là bài về **JAAS orchestration**, không phải hướng dẫn viết hệ thống password hoặc OTP production.

### 7.1 Mã Java 17 `JaasTwoFactorPolicyDemo.java`

```java
import java.security.Principal;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import javax.security.auth.spi.LoginModule;

public class JaasTwoFactorPolicyDemo {
    public record UserPrincipal(String name) implements Principal {
        public String getName() { return name; }
    }
    public record FactorPrincipal(String name) implements Principal {
        public String getName() { return name; }
    }

    public static class PasswordModule implements LoginModule {
        private Subject subject;
        private CallbackHandler handler;
        private Map<String, Object> shared;
        private boolean success;
        private Principal added;

        @SuppressWarnings("unchecked")
        public void initialize(Subject subject, CallbackHandler handler,
                Map<String, ?> sharedState, Map<String, ?> options) {
            this.subject = subject;
            this.handler = handler;
            this.shared = (Map<String, Object>) sharedState;
        }

        public boolean login() throws LoginException {
            NameCallback name = new NameCallback("Username");
            PasswordCallback password = new PasswordCallback("Password", false);
            char[] typed = null;
            try {
                handler.handle(new Callback[] {name, password});
                typed = password.getPassword();
                char[] demoExpected = "lab-pass".toCharArray();
                boolean match = "student".equals(name.getName()) &&
                        typed != null && Arrays.equals(typed, demoExpected);
                Arrays.fill(demoExpected, '\0');
                if (!match) throw new LoginException("Password factor rejected");
                shared.put("user", name.getName()); // Không đưa secret vào shared state
                success = true;
                return true;
            } catch (Exception ex) {
                if (ex instanceof LoginException le) throw le;
                throw new LoginException("Callback failed");
            } finally {
                if (typed != null) Arrays.fill(typed, '\0');
                password.clearPassword();
            }
        }

        public boolean commit() {
            if (!success) return false;
            added = new UserPrincipal((String) shared.get("user"));
            subject.getPrincipals().add(added);
            return true;
        }

        public boolean abort() { return logout(); }

        public boolean logout() {
            if (added != null) subject.getPrincipals().remove(added);
            added = null;
            success = false;
            if (shared != null) shared.remove("user");
            return true;
        }
    }

    public static class OtpModule implements LoginModule {
        private Subject subject;
        private CallbackHandler handler;
        private Map<String, ?> shared;
        private boolean success;
        private Principal added;

        public void initialize(Subject subject, CallbackHandler handler,
                Map<String, ?> sharedState, Map<String, ?> options) {
            this.subject = subject;
            this.handler = handler;
            this.shared = sharedState;
        }

        public boolean login() throws LoginException {
            if (!"student".equals(shared.get("user"))) {
                throw new LoginException("Password factor must succeed first");
            }
            TextInputCallback input = new TextInputCallback("Demo OTP");
            try { handler.handle(new Callback[]{input}); }
            catch (Exception ex) { throw new LoginException("OTP callback failed"); }
            if (!"123456".equals(input.getText())) {
                throw new LoginException("OTP factor rejected");
            }
            success = true;
            return true;
        }

        public boolean commit() {
            if (!success) return false;
            added = new FactorPrincipal("otp");
            subject.getPrincipals().add(added);
            return true;
        }

        public boolean abort() { return logout(); }

        public boolean logout() {
            if (added != null) subject.getPrincipals().remove(added);
            added = null;
            success = false;
            return true;
        }
    }

    static final class Inputs implements CallbackHandler {
        private final String user, password, otp;

        Inputs(String user, String password, String otp) {
            this.user = user; this.password = password; this.otp = otp;
        }

        public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
            for (Callback cb : callbacks) {
                if (cb instanceof NameCallback nc) nc.setName(user);
                else if (cb instanceof PasswordCallback pc)
                    pc.setPassword(password.toCharArray());
                else if (cb instanceof TextInputCallback tc) tc.setText(otp);
                else throw new UnsupportedCallbackException(cb);
            }
        }
    }

    private static void test(String label, String otp,
            AppConfigurationEntry.LoginModuleControlFlag otpFlag)
            throws Exception {
        Configuration config = new Configuration() {
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                if (!"twoFactor".equals(name)) return null;
                return new AppConfigurationEntry[]{
                    new AppConfigurationEntry(PasswordModule.class.getName(),
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, Map.of()),
                    new AppConfigurationEntry(OtpModule.class.getName(),
                        otpFlag, Map.of())
                };
            }
        };

        LoginContext context = new LoginContext(
            "twoFactor", new Subject(),
            new Inputs("student", "lab-pass", otp), config);
        try {
            context.login();
            System.out.println(label + ": PASS, principals=" +
                    context.getSubject().getPrincipals().size());
            context.logout();
            System.out.println(label + ": after logout=" +
                    context.getSubject().getPrincipals().size());
        } catch (LoginException e) {
            System.out.println(label + ": FAIL");
        }
    }

    public static void main(String[] args) throws Exception {
        test("both-required-correct-otp", "123456",
                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED);
        test("both-required-wrong-otp", "000000",
                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED);
        test("otp-optional-wrong-otp", "000000",
                AppConfigurationEntry.LoginModuleControlFlag.OPTIONAL);
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 JaasTwoFactorPolicyDemo.java
java JaasTwoFactorPolicyDemo
```

Đầu ra **đã biên dịch và chạy kiểm chứng**:

```text
both-required-correct-otp: PASS, principals=2
both-required-correct-otp: after logout=0
both-required-wrong-otp: FAIL
otp-optional-wrong-otp: PASS, principals=1
otp-optional-wrong-otp: after logout=0
```

### 7.2 Tại sao thay một flag làm mất chính sách MFA?

Ở trường hợp OTP `REQUIRED`, khi OTP sai, `OtpModule.login()` ném `LoginException`. Dù password module đã pass, toàn `LoginContext.login()` phải thất bại. `PasswordModule.abort()` được dùng để xóa trạng thái tạm — `Subject` không được coi đã xác thực hai yếu tố.

Khi OTP `OPTIONAL`, lỗi OTP không phải mandatory. Password `REQUIRED` đã pass nên toàn stack **có thể PASS**. `Subject` khi đó có `UserPrincipal` nhưng **không có `FactorPrincipal("otp")`** (stdout có một Principal). Nếu ứng dụng cấp quyền như đã hoàn thành MFA chỉ vì `LoginContext.login()` thành công, nó đã tạo đường bỏ qua OTP.

**Cách xử lý:** nếu MFA thực sự bắt buộc, phải thể hiện bằng `REQUIRED/REQUISITE` hoặc policy điều phối tương đương không bị bypass, và áp dụng quy tắc chỉ cấp quyền “MFA-complete” khi đủ bằng chứng. Không nên chữa lỗi bằng cách “OTP optional nhưng login code sau đó đoán xem user đã nhập mã hay chưa” mà không có authorization policy rõ ràng.

### 7.3 Những chi tiết đáng học từ code

- `LoginContext(..., config)` nhận `Configuration` riêng nên không cần file global và không vô tình đổi cấu hình JAAS của ứng dụng khác.
- `CallbackHandler` chuyển username/password/OTP đến module qua các loại `Callback` tương ứng; không log credential.
- `sharedState` được `PasswordModule` ghi **sau khi xác minh**, rồi `OtpModule` đọc. Demo ép thứ tự Password trước OTP; đảo thứ tự này sẽ khiến `OtpModule` không thấy user.
- `commit()` mới thêm các `Principal` có ý nghĩa vào `Subject` sau khi tổng thể pass.
- `logout()` gỡ đúng những Principal do module thêm. Stdout `after logout=0` là kiểm tra cleanup trong lab.
- Một implementation thật phải dùng password hashing chuẩn, TOTP/HOTP/WebAuthn kiểm tra chống replay/rate limit, source of truth người dùng và session-bound MFA; không dùng mã cố định hoặc `String` immutable chứa secret như trong demo.
- Với nhiều module xác thực nhiều factor, cần **ràng buộc các bằng chứng với cùng principal/session**; không để account A dùng password và account B dùng OTP mà app vẫn coi là MFA cho một user.

## 8. Cấu hình JAAS dạng file và cấu hình programmatic

### 8.1 Cấu hình file

Có thể cung cấp file cấu hình cho JDK qua property:

```bash
java -Djava.security.auth.login.config=jaas.conf MyApplication
```

Đây là **mẫu lệnh cấu hình**, không chạy độc lập khi chưa có file `jaas.conf` và class ứng dụng `MyApplication`. Cách mở/config bảo mật có thể khác theo framework/container.

Ví dụ một file `jaas.conf` **tham khảo dùng chính các class được triển khai trong demo thứ hai** (các class phải nằm trên classpath):

```text
twoFactor {
    JaasTwoFactorPolicyDemo$PasswordModule required;
    JaasTwoFactorPolicyDemo$OtpModule      required;
};
```

Đối với cấu hình này, ứng dụng cần gọi `new LoginContext("twoFactor", handler)` với một `CallbackHandler` cung cấp username/password/OTP; nguồn class demo ở trên dùng `Configuration` programmatic nên **không tự đọc file này** khi chạy `JaasTwoFactorPolicyDemo.main`. Để tích hợp config file, caller phải không truyền `Configuration` tùy chỉnh vào constructor.

**Cẩn thận:** tên module là **tên binary class** (`Outer$Nested`), không phải `Outer.Nested` của mã nguồn; đó là lý do config trên dùng ký tự `$`. Trong hệ thống thật nên dùng class có package namespace rõ ràng, ví dụ tự xây `com.company.auth.PasswordLoginModule` và `com.company.auth.OtpLoginModule` thay vì default package của demo.

Không ghi credential, password hoặc private key làm `ModuleOptions` trong file config. Options phù hợp cho tham số không bí mật như realm, chế độ debug có kiểm soát hoặc cấu hình endpoint đã được bảo vệ.

### 8.2 Programmatic `Configuration`

Hai demo ở trên dùng:

```java
Configuration config = new Configuration() {
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
        return "lab".equals(name) ? stack.clone() : null;
    }
};
LoginContext ctx = new LoginContext(
        "lab", new Subject(), handler, config);
```

Đây là đoạn nhúng giải thích API; cần các biến `stack` và `handler`. Cách này hữu ích khi cần test **nhiều tổ hợp flags** trong một process, mỗi test có cấu hình độc lập, không chỉnh global `Configuration.setConfiguration(...)`.

Ngoài ra, khi caller dùng cấu hình cài sẵn qua `Configuration.getConfiguration()`, tên ứng dụng không có entry có thể được xử lý bằng entry `other` nếu đã định nghĩa; nếu không có entry tương ứng, `LoginContext` báo `LoginException`. Không vô tình dùng `other` quá rộng vì một lỗi đánh máy `appName` có thể đưa request tới **stack yếu hơn mong muốn**.

### 8.3 Thứ tự và việc nạp lại cấu hình

`Configuration` kiểm soát thứ tự entry theo đúng danh sách. Có thể có nhiều ứng dụng/realm với nhiều chính sách khác nhau. Một thay đổi flag hoặc vị trí module nên được code review tương tự thay đổi security policy:

- Không xếp cơ chế `sufficient` yếu trước yếu tố `required` mà business bắt buộc.
- Không chuyển `required` sang `optional` chỉ để giảm tỉ lệ login fail khi upstream down nếu yếu tố đó thuộc yêu cầu bảo mật.
- Nếu triển khai fallback, phân biệt **fallback thay thế được phép** với **bypass điều kiện bắt buộc**.
- Nếu config lấy từ file/DB/config service, kiểm soát quyền sửa, version/approval và hành vi reload; không cho user tự chọn cấu hình kém an toàn bằng request parameter.

## 9. CallbackHandler, options và sharedState: cách phối hợp an toàn

### 9.1 CallbackHandler và dữ liệu nhạy cảm

`CallbackHandler` là ranh giới giữa ứng dụng/UI và các module. Những callback JAAS phổ biến:

| Callback | Để lấy/đưa thông tin | Lưu ý |
| --- | --- | --- |
| `NameCallback` | Tên user | Không tin giá trị name nếu chưa được xác minh |
| `PasswordCallback` | Mảng `char[]` password | `getPassword()` và `clearPassword()`, tránh tạo bản sao String không cần thiết |
| `TextInputCallback` | Text phụ như OTP trong demo | Chỉ minh họa; secret thực cần policy bảo vệ memory/log riêng |
| `ConfirmationCallback` | Yêu cầu user xác nhận | Không đồng nghĩa bằng chứng xác thực cryptographic |
| `UnsupportedCallbackException` | Handler không hỗ trợ callback | Cần fail authentication theo policy, không tự pass |

Đừng đặt password/token vào log, trace, exception message hoặc `sharedState` kéo dài quá scope. Password phải được xác minh qua phương thức an toàn chuyên dụng; `PasswordCallback` chỉ là cơ chế truyền input, không thay thế password hashing/KDF hoặc rate limiting.

### 9.2 sharedState cho nhiều module

`initialize` nhận:

```java
public void initialize(
    Subject subject,
    CallbackHandler handler,
    Map<String, ?> sharedState,
    Map<String, ?> options)
```

Đây là chữ ký method ở mức **đoạn nhúng**, cần một class triển khai `LoginModule`. `sharedState` cho phép các module trong một lần `LoginContext` trao đổi state, còn `options` là cấu hình dành cho từng entry.

Các lưu ý:

- Chỉ đặt giá trị thật sự cần chia sẻ; tránh password/OTP/raw credential.
- Xác định **nguồn nào có quyền viết** một key và cách bên đọc xác thực nguồn.
- Không dùng `sharedState` để bỏ qua bước xác minh: module sau cần kiểm kết quả/identity đủ mạnh, không tin một khóa `"authenticated"` do module tùy ý set.
- Cleanup trong `abort/logout`, tránh dữ liệu cũ còn trong Map khi retry hoặc tái sử dụng.
- Thứ tự module quyết định dữ liệu nào đã được đưa vào shared state trước thời điểm module khác được gọi.
- Nếu module cung cấp Kerberos/SSO cùng password, kiểm identity có nhất quán; principal từ hai nguồn không khớp không được tự gộp quyền.

### 9.3 Exceptions và thông điệp lỗi

Một `requisite` có thể dừng sớm, trong khi `required` tiếp tục module phía sau dù fail. Điều này có thể làm thời gian phản hồi hoặc side effects khác nhau:

```text
A requisite -> FAIL:  B, C không login
A required  -> FAIL:  B, C vẫn có thể login
```

Việc chọn flag **không thay thế** kiểm soát account enumeration, rate limiting, audit và thông báo lỗi chung. Nếu ứng dụng trả về “OTP sai” khác “password sai”, kẻ tấn công có thể học thêm trạng thái tài khoản. Nên giữ error messages thống nhất ở bên ngoài, phân loại nguyên nhân trong audit nội bộ có kiểm soát, và bảo đảm mọi nhánh đều không cấp phiên trên failure.

## 10. Khi nào cần REQUIRED, REQUISITE, SUFFICIENT, OPTIONAL?

| Yêu cầu | Cách nghĩ về flag | Cảnh báo |
| --- | --- | --- |
| Phải có password và OTP | Cả hai `required` hoặc một số `requisite`/`required` theo thứ tự hợp lý | Không đặt OTP optional hoặc bị bypass bởi sufficient trước đó |
| Chỉ cần một trong hai phương thức SSO/Password **được phê duyệt là tương đương** | Dùng `sufficient` cho phương thức ưu tiên, fallback phù hợp | Cần kiểm độ mạnh/issuer/tenant, không mặc định mọi SSO đủ mạnh |
| Cần module kiểm mandatory trước khi làm việc tốn chi phí | `requisite` phía trước | Khác biệt timing/side effects và abort phải được đánh giá |
| Một phép enrichment không quyết định login | `optional` | Không cho phép module tùy ý thêm quyền nhạy cảm |
| Một điều kiện bắt buộc nhưng vẫn muốn chạy các module còn lại để giảm tiết lộ điểm lỗi | `required` | Vẫn phải có timeout và giới hạn chi phí |
| Audit/telemetry bắt buộc dù login thành công sớm | Tổ chức bên ngoài stack hoặc cơ chế đảm bảo audit riêng | `optional` đứng sau `sufficient` có thể không chạy login() |
| Nhiều nguồn identity trong cùng phiên | Stack có quy tắc identity binding rõ | Một Principal pass không có nghĩa user của các module khác là một người |

Không có flag nào tự giải quyết authorization, MFA phishing resistance hoặc credential storage. Đây chỉ là **control-flow và aggregation của nhiều LoginModule**.

## 11. Test matrix trước khi đưa cấu hình vào production

Không chỉ test “password đúng” và “password sai”. Ít nhất nên có:

1. **Một required fail, optional/sufficient khác pass:** toàn stack phải fail.
2. **Requisite fail giữa stack:** module phía sau không được gọi trong login phase; state tạm được dọn.
3. **Sufficient pass trước mandatory:** chứng minh các mandatory phía sau **bị skip**, kiểm xem đây có phải policy mong muốn.
4. **Sufficient pass sau mandatory pass:** skip phần không còn cần thiết, vẫn trả pass.
5. **Sufficient fail rồi required pass:** fallback hợp lệ nếu business rule cho phép.
6. **Chỉ optional, ít nhất một pass:** pass.
7. **Chỉ optional, tất cả fail/ignore:** fail.
8. **Required pass, optional fail:** pass — với module bảo mật quan trọng, đây có thể là lỗ hổng policy.
9. **Hai factor đều required, OTP sai:** fail và không cấp principal MFA.
10. **OTP bị cấu hình optional:** test phải phát hiện nếu yêu cầu MFA bắt buộc.
11. **Login succeeded, commit module sau thất bại:** abort/cleanup các state/Principal đã thêm đúng.
12. **Login failed ở module cuối:** không còn token, password buffer, session hoặc Principal bị rò qua retry.
13. **Logout sau success:** chỉ gỡ credential/Principal do đúng module thêm; không xóa dữ liệu thuộc module khác.
14. **CallbackHandler thiếu callback:** fail đúng chính sách, không ngầm trả true hoặc chấp nhận dữ liệu null.
15. **Hai module xác thực identity khác nhau:** reject hoặc rõ ràng map identity theo chính sách, không cộng quyền tùy ý.
16. **Config name sai/`other` fallback:** không vô tình dùng realm yếu.
17. **Config order thay đổi/rollout:** snapshot test bảo đảm policy mạnh không bị làm yếu vì reorder.
18. **Concurrency:** không chia sẻ mutable `LoginContext`/`LoginModule`/static sharedState giữa phiên độc lập.
19. **Timeout/upstream unavailable:** không chuyển mandatory module thành optional tự động.
20. **Audit/logging:** tất cả đường success/failure/short-circuit có record sự kiện đủ điều tra, không lộ secret.

**Bảo mật của stack là bảo mật của mọi đường đi có thể thành công**, không chỉ cấu hình nhìn “có required ở vài dòng”. Một `sufficient` thành công quá sớm có thể tạo đường đăng nhập không thực hiện yếu tố bắt buộc ở cuối.

## 12. Các sai lầm thường gặp

| Nhận định sai | Giải thích đúng |
| --- | --- |
| “`required` fail thì Java dừng ngay.” | Không; nó tiếp tục module phía sau nhưng kết quả toàn stack cuối cùng fail. |
| “`requisite` giống `required` hoàn toàn.” | Cả hai mandatory, nhưng requisite fail **dừng login phase ngay**. |
| “`sufficient` pass luôn thắng, kể cả required trước đó fail.” | Không; prior mandatory failure vẫn khiến tổng thể fail. |
| “Một `required` ở cuối file sẽ luôn chạy.” | Có thể không: `sufficient` thành công trước đó có thể short-circuit. |
| “`optional` không ảnh hưởng gì.” | Optional success có thể tạo tổng thể pass khi không có mandatory; nó cũng có thể thêm Principal khi commit đúng. |
| “Tất cả optional fail cũng cho qua.” | Không; nếu không có mandatory, phải có ít nhất một optional/sufficient success. |
| “OTP optional là MFA bắt buộc vì ứng dụng hiển thị ô nhập OTP.” | Sai; required password pass đủ làm toàn stack pass nếu OTP optional fail. |
| “`LoginModule.login` trả false và ném `LoginException` giống nhau.” | Không; `false` là ignored theo contract, exception báo failure. |
| “Ngay khi module pass thì ghi role vào Subject luôn.” | Nên đợi commit sau tổng thể pass, tránh publish identity từ stack thất bại. |
| “`Subject` chứa admin Principal là có quyền admin.” | Không; quyền phải dựa vào policy, nguồn Principal đáng tin và authorization. |
| “`sufficient` short-circuit nên không cần abort/commit nữa.” | Phase login dừng sớm nhưng lifecycle xử lý trạng thái vẫn cần đúng. |
| “`requisite` fail thì cleanup không chạy.” | Sai: abort phase vẫn có trách nhiệm dọn state. |
| “Các module dùng chung sharedState nên có thể tin mọi key trong đó.” | Không; cần kiểm provenance, scope và identity binding. |
| “Global static list trace dùng được trong multi-user server.” | Không; demo chạy tuần tự, production cần trace theo request/login context. |
| “Chỉ cần đặt `required` cho nhiều module là bảo vệ MFA khỏi phishing.” | Không; còn phụ thuộc loại factor, tính chống phishing, replay, session binding và chính sách. |
| “Thay config không phải thay code nên không cần security review.” | Sai; chỉ reorder/đổi flag có thể thay đổi toàn bộ login policy. |

## 13. Câu hỏi tự kiểm tra

**1.** `required` và `requisite` khác nhau ở điểm nào khi fail?  
**Đáp:** Cả hai làm tổng thể fail; required **tiếp tục** module phía sau, requisite **dừng ngay phase login**.

**2.** Một `required` trước đó đã thất bại, sau đó `sufficient` thành công. Có cho login không?  
**Đáp:** Không. Prior mandatory failure chưa được xóa, tổng thể vẫn fail.

**3.** Một `sufficient` thành công đứng trước `required`. Module required phía sau có chạy không?  
**Đáp:** Không trong phase login nếu sufficient short-circuit hợp lệ. Vì vậy placement có thể tạo bypass.

**4.** Có hai module `optional`; một fail, một pass. Tổng thể ra sao khi không có mandatory?  
**Đáp:** Pass vì có ít nhất một module xác thực thành công.

**5.** Cả hai optional đều fail/ignored, không có required/requisite; có thành công không?  
**Đáp:** Không; thiếu bất kỳ thành công nào.

**6.** Password required pass nhưng OTP optional fail, ứng dụng có thể login thành công không?  
**Đáp:** Có; đó là lý do không dùng optional cho yếu tố MFA bắt buộc.

**7.** `commit()` được dùng để làm gì?  
**Đáp:** Hoàn tất các identity/credential của module đã thành công sau khi **tổng thể** login phase đạt điều kiện.

**8.** `abort()` khi nào được gọi?  
**Đáp:** Khi quy trình xác thực tổng thể fail hoặc cần rollback trạng thái; nó xóa state tạm và các thay đổi phải hủy theo contract.

**9.** `CallbackHandler` và `sharedState` khác nhau thế nào?  
**Đáp:** Handler là giao diện yêu cầu/cung cấp input, sharedState là map trao đổi state **giữa các module cùng LoginContext**.

**10.** `LoginContext.logout()` nên làm gì với `Subject`?  
**Đáp:** Các module dọn Principal/credential do **chính module đó thêm**, không xóa tài nguyên của module khác.

**11.** Nếu một module xác thực user A và module khác xác thực user B, đều pass, có nên ghép quyền?  
**Đáp:** Không. Phải ràng buộc đúng cùng identity/session và áp dụng policy authorization.

**12.** Vì sao một cấu hình JAAS thay đổi từ `required` sang `optional` phải được review bảo mật?  
**Đáp:** Vì đó là thay đổi điều kiện thành công của login; có thể biến một yếu tố bắt buộc thành không bắt buộc.

## Tổng kết

```text
required  = bắt buộc, fail vẫn tiếp tục login() module sau
requisite = bắt buộc, fail dừng login() ngay
sufficient= không bắt buộc; pass có thể dừng sớm nếu
            không có required/requisite failure trước đó
optional  = không bắt buộc; luôn tiếp tục module sau

Nếu không có required/requisite:
    cần ít nhất một sufficient/optional thành công

JAAS authentication lifecycle:
    login() các module theo config order
                |
       aggregate flags/result
        /                \
     success             failure
       |                    |
     commit()             abort()
       |
     Subject/Principal
       |
     logout()

Bảo mật phụ thuộc vào:
    flags + THỨ TỰ + identity binding + cleanup + policy.
```

**Kết luận:** bốn control flags không chỉ là nhãn mô tả “mức quan trọng” của từng module; chúng quyết định **đường đi của toàn bộ quy trình đăng nhập**. `REQUIRED` và `REQUISITE` dùng cho điều kiện bắt buộc, `SUFFICIENT` thể hiện cơ chế có thể **đủ để dừng sớm**, còn `OPTIONAL` không thể thực thi một yếu tố MFA bắt buộc. Muốn đúng, phải **test các nhánh thành công/thất bại và thứ tự module**, không chỉ đọc một dòng cấu hình.

## Tài liệu tham khảo chính thức

- [Java SE 17 — Configuration API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/Configuration.html): định nghĩa chính xác bốn flags, quy tắc tổng hợp và thứ tự module.
- [Java SE 17 — AppConfigurationEntry.LoginModuleControlFlag](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/AppConfigurationEntry.LoginModuleControlFlag.html): các hằng `REQUIRED`, `REQUISITE`, `SUFFICIENT`, `OPTIONAL`.
- [Java SE 17 — LoginContext API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/LoginContext.html): stacked authentication, `login`, `getSubject`, `logout`, cấu hình và CallbackHandler.
- [Java SE 17 — LoginModule API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/spi/LoginModule.html): `initialize`, `login`, `commit`, `abort`, `logout`.
- [Oracle — JAAS LoginModule Developer's Guide (Java 17)](https://docs.oracle.com/en/java/javase/17/security/java-authentication-and-authorization-service-jaas-loginmodule-developers-guide1.html): hai phase, giá trị return, cleanup và Subject.
- [Java SE 17 — Subject API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/Subject.html): Principal/credentials và vòng đời subject.
- [Java SE 17 — NameCallback / PasswordCallback](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/callback/package-summary.html): callback framework và handling dữ liệu đăng nhập.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jaas-authentication-007">17.7 Subject.current, Subject.callAs và cách đọc mã cũ sử dụng doAs</a>

<details>
<summary>Xem chi tiết</summary>

# 17.7 Subject.current, Subject.callAs và cách đọc mã cũ sử dụng doAs

Trong **JAAS (Java Authentication and Authorization Service)**, `javax.security.auth.Subject` biểu diễn danh tính đã được xác thực của một người dùng hoặc dịch vụ: nó tập hợp các **`Principal`** và credentials liên quan. Một đoạn code nghiệp vụ có thể cần biết **`Subject` nào đang gắn với phạm vi thực thi** mà không phải truyền đối tượng qua từng hàm. Các API hiện đại phục vụ việc đó là:

- **`Subject.current()`:** đọc `Subject` đang được gắn với phạm vi thực thi hiện tại; có thể trả về `null`.
- **`Subject.callAs(subject, callable)`:** chạy một `Callable<T>` với `subject` là `Subject` hiện tại trong khoảng thời gian chạy action; sau đó khôi phục ngữ cảnh trước đó.

Trong code Java cũ thường gặp **`Subject.doAs(...)`** và **`Subject.getSubject(AccessController.getContext())`**. Chúng xuất hiện trong kiến trúc **Security Manager / AccessControlContext (ACC)**; từ **JDK 24** Security Manager bị **vô hiệu hóa vĩnh viễn** (JEP 486). Vì vậy, khi đọc hoặc nâng cấp một hệ thống JAAS, cần phân biệt **ràng buộc danh tính vào phạm vi chạy**, **xác thực thông tin đăng nhập**, và **cấp phép thao tác nghiệp vụ**: ba vấn đề **không đồng nghĩa**.

**Điều kiện phiên bản:** `Subject.current` và `Subject.callAs` có từ **Java 18**, nên chương trình sử dụng chúng **không biên dịch với `--release 17`**. Hai ví dụ đầy đủ trong bài được biên dịch bằng `javac --release 18` và chạy kiểm thử trên JDK 24. Java 17 vẫn có các API legacy, nhưng không có hai phương thức mới. Trong JDK 24–26, `doAs` vẫn có trong API ở trạng thái *deprecated for removal*, còn `getSubject(AccessControlContext)` ném `UnsupportedOperationException`.

## 1. Kết quả học tập

Sau bài này, bạn có thể:

1. Nhận diện một `Subject`, `Principal` và các bộ credentials trong JAAS; hiểu nguồn `Subject` đáng tin cậy.
2. Giải thích chính xác **phạm vi động** của `callAs`, cách `current` trả `Subject` hoặc `null`, cách nested scope và exception khôi phục context.
3. Viết và chạy chương trình Java 18+ dùng `Subject.callAs`, kể cả `Callable` có checked exception.
4. Đọc được `Subject.doAs(Subject, PrivilegedAction)` và `doAs(Subject, PrivilegedExceptionAction)`, phân biệt exception của code cũ.
5. Hiểu tại sao `Subject.getSubject(AccessController.getContext())` hỏng từ JDK 24 và tại sao không nên tìm cách bật lại Security Manager.
6. Migrate code cũ sang `current`/`callAs` và kiểm tra **luồng không đồng bộ/thread pool** tránh làm mất hoặc rò rỉ danh tính.
7. Giữ ranh giới giữa **JAAS authentication** và **application authorization**: `callAs` không xác thực password hoặc tự cho phép truy cập file/database.

## 2. Mô hình `Subject`: danh tính, không phải một lệnh “đăng nhập”

`javax.security.auth.Subject` là một đối tượng chứa:

- **`Set<Principal>`:** một hoặc nhiều danh tính/định danh, ví dụ tên người dùng, principal Kerberos, DN từ chứng thư hoặc service principal.
- **Public credentials:** thông tin có thể chia sẻ trong mô hình bảo mật phù hợp, ví dụ certificate.
- **Private credentials:** thông tin nhạy cảm như tickets, tokens hay khóa bí mật tùy công nghệ xác thực; cần hạn chế đọc và tuyệt đối không log tùy tiện.

`Principal` chỉ là interface với phương thức `getName()`. Một `Subject` có thể có **nhiều Principal**; ứng dụng không nên giả định `subject.getPrincipals().iterator().next()` luôn đại diện đúng user hay quyền truy cập.

```text
                   JAAS LoginContext
                 login() thành công
                         |
                         v
                Subject đã xác thực
               /         |         \
           Principals  Public     Private
                       Credentials Credentials
                         |
               Subject.callAs(subject, action)
                         |
                Subject.current()
                         |
                   business code
                         |
              authorization riêng
```

### 2.1. `Subject` đến từ đâu?

Trong hệ thống JAAS thông thường, `LoginContext` sử dụng `LoginModule` và `CallbackHandler` để thực hiện **authentication**; sau khi `login()` thành công, `LoginContext.getSubject()` trả `Subject` đã được population bởi các module liên quan.

Một ứng dụng có thể tự gọi `new Subject()` để viết test, nhưng **điều đó không chứng minh người dùng đã đăng nhập**, không chứng minh chứng thư/Kerberos ticket hợp lệ và cũng không chứng minh các `Principal` bên trong đáng tin. Tạo một `Subject` chứa `CN=Admin` không làm người gọi trở thành admin về mặt bảo mật.

### 2.2. Principal không tự là Role/Permission

Một `Principal` có thể là tên tài khoản, DN, service identity hoặc thông tin nhận diện khác. **Không có quy tắc universal** rằng principal tên `"admin"` đã được cấp quyền quản trị. Quyền thật được tính qua **authorization policy/IAM/ACL/resource ownership** của ứng dụng hoặc giao thức.

Từ JDK 24, cơ chế JAAS authorization dựa trên **Security Manager / policy-file permissions** không còn hoạt động theo nghĩa trước kia. Việc có current subject **không tự thay** các lệnh kiểm tra permission của mô hình cũ.

### 2.3. `setReadOnly` và bảo vệ credentials

`Subject.setReadOnly()` ngăn thêm/bớt `Principal` và credentials khỏi các tập trực tiếp của `Subject`. Nó **không tự đóng băng nội dung mọi đối tượng credential mutable**, không hủy secrets và không giải quyết vấn đề thiếu authentication.

Khi không cần chỉnh sửa Subject sau login, ứng dụng có thể cân nhắc read-only theo lifecycle của JAAS; nhưng phải tránh gọi trước khi `LoginModule` cần thêm hoặc xóa principals/credentials trong `commit/logout`. Quản trị vòng đời secret và `logout()` phụ thuộc công nghệ/login module.

## 3. `Subject.current()` đọc điều gì?

Chữ ký:

```java
public static Subject current()
```

Nó trả về **`Subject` đang được ràng buộc với phạm vi thực thi hiện tại**. Với JDK 24+, nếu đang ở ngoài mọi `callAs`/`doAs` phù hợp, giá trị thông thường là **`null`**.

```text
Subject.current()  -> null                  [ngoài scope]

Subject.callAs(alice, action) {
    Subject.current() -> alice
}

Subject.current()  -> null                  [sau action]
```

Đây là sơ đồ minh họa, **không phải cú pháp Java chạy trực tiếp**.

**Không nên dùng `Subject.current()` rồi lập tức gọi `getPrincipals()` mà không xét trường hợp `null`.** Nếu một API yêu cầu identity, nên từ chối một cách rõ ràng theo thiết kế, hoặc nhận một `Subject` đã được authenticate như tham số riêng.

### 3.1. Current subject có phải biến toàn cục?

**Không.** Nó không có nghĩa tất cả thread trong JVM đang dùng cùng một `Subject`. `current()` phản ánh ràng buộc danh tính trong **phạm vi chạy của thread hiện tại**, và hành vi lan truyền sang child tasks phụ thuộc mô hình concurrency.

- Không dùng một static field kiểu `static Subject activeUser` cho mọi request: request khác có thể nhìn sai danh tính.
- Không tự giả định task của `ExecutorService` sẽ kế thừa current subject từ thread submit.
- Không coi `Subject.current()` là bằng chứng identity đã được verify, vì code có thể chủ động gọi `callAs` với `Subject` do chính nó tạo.

### 3.2. Khi không có current subject

`Subject.current() == null` có thể có nghĩa:

- Đoạn code chạy ngoài bất kỳ scope `callAs`/`doAs` nào.
- `callAs(null, action)` chủ động bind `null` tạm thời.
- Công việc bị chuyển qua thread/executor mà không truyền context.
- Một framework đã authenticate user nhưng **không sử dụng `Subject` làm context**; user identity có thể nằm trong đối tượng request hoặc context của framework khác.

Vì thế đừng đánh đồng “current subject null” với “toàn hệ thống chưa authenticate”. Đây là tình trạng của **JAAS Subject binding**, không phải tình trạng xác thực tổng quát.

## 4. `Subject.callAs(Subject, Callable<T>)`: phạm vi động

Chữ ký API (có từ Java 18):

```java
public static <T> T callAs(Subject subject, Callable<T> action)
        throws CompletionException
```

`callAs` nhận hai đối số:

- `subject` là `Subject` cần gắn trong lúc chạy; **có thể `null`**.
- `action` là `java.util.concurrent.Callable<T>`; **không thể `null`**.

Nó **gọi action ngay trong scope**, lấy kết quả trả về và **khôi phục Subject trước đó** sau khi action kết thúc. Đây không phải API tự enqueue task hoặc tạo thread mới. Bên trong action, `Subject.current()` sẽ trả `subject` theo mô hình hiện đại.

### 4.1. Phạm vi lồng nhau: Alice → Bob → Alice

```text
outside: current=null
  callAs(Alice)
    current=Alice
    callAs(Bob)
      current=Bob
    done Bob
    current=Alice
  done Alice
outside: current=null
```

Khi lồng `callAs`, scope bên trong **che tạm** scope bên ngoài; sau khi kết thúc, scope bên ngoài được khôi phục. Không cần “`Subject.setCurrent`” vì API này **không tồn tại**.

### 4.2. Có thể bind `null` không?

Có. Ví dụ trong scope Alice, gọi `Subject.callAs(null, () -> ...)` sẽ làm `Subject.current()` trả `null` **trong action bên trong**, rồi trả lại Alice sau khi action kết thúc.

Điều này không được dùng như “xóa quyền” của Java Security Manager. Nó chỉ là **thay đổi context hiện tại**. Một code path cần principal cụ thể phải có kiểm tra authorization riêng.

### 4.3. Exception từ `Callable`

Khác `doAs` legacy, `callAs` nhận `Callable<T>` cho phép `call()` khai báo checked exception. Theo hợp đồng API, nếu action ném exception, `callAs` ném **`CompletionException`**, với `getCause()` là exception gốc. Điều này áp dụng cả khi exception xảy ra sâu trong action: **subject binding vẫn được khôi phục** theo scope.

Không dùng `catch (Exception e) { return allowed; }` hoặc bỏ qua `CompletionException` như thể action đã chạy thành công. Khi cần gửi lỗi ra API, xem cause và chuyển thành lỗi nghiệp vụ phù hợp mà không làm lộ credentials.

### 4.4. `callAs` không nâng đặc quyền

Tên phương thức có thể khiến người đọc tưởng đây là lệnh impersonation ở cấp hệ điều hành hoặc “elevate privileges”. Không phải: **`callAs` chỉ gắn `Subject` cho code có thể đọc được**. Nó không đổi user của process Windows/Linux, không cấp permission trong hệ điều hành, không tự tạo token, không tự mở kết nối Kerberos và **không tự xác nhận Principal**.

Các thư viện hỗ trợ JAAS Subject hoặc GSS/Kerberos có thể dùng context này để chọn credentials, nhưng trách nhiệm authentication/authorization và tương thích runtime vẫn thuộc về ứng dụng/thư viện.

## 5. Vì sao `Subject.doAs(...)` xuất hiện trong code cũ?

Trước mô hình API mới, một mẫu JAAS phổ biến là:

```java
Subject.doAs(authenticatedSubject,
        (PrivilegedAction<String>) () -> service.call());
```

Trong Java cũ có Security Manager, `doAs` tích hợp với **`AccessControlContext`/`SubjectDomainCombiner`** và cơ chế policy permissions để liên hệ `Subject` với các quyền của action. Trong những phiên bản hiện đại, Security Manager đã bị loại khỏi con đường vận hành thông thường, nên cách dùng này không còn ý nghĩa **“tạm chạy với đặc quyền dựa trên Security Manager”**.

Có hai overload quan trọng:

| Legacy API | Kiểu action | Kiểu exception điển hình |
| --- | --- | --- |
| `Subject.doAs(Subject, PrivilegedAction<T>)` | `PrivilegedAction<T>.run()` không khai báo checked exception | Runtime exception đi ra theo semantics của action |
| `Subject.doAs(Subject, PrivilegedExceptionAction<T>)` | `PrivilegedExceptionAction<T>.run()` có thể ném checked exception | `PrivilegedActionException` bọc checked exception |
| `Subject.doAsPrivileged(..., AccessControlContext)` | Biến thể nhận ACC | **Không có phương thức thay thế nào bảo toàn hành vi phân quyền của Security Manager** |
| `Subject.getSubject(AccessControlContext)` | Đọc `Subject` gắn với ACC | Trên **JDK 24+ ném `UnsupportedOperationException`** |

Khi lambda có thể khớp cả `PrivilegedAction` và `PrivilegedExceptionAction`, Java có thể báo **ambiguous method call**. Trong code legacy hãy đọc kỹ cast hoặc kiểu biến:

```java
String name = Subject.doAs(s,
        (PrivilegedAction<String>) () -> "Alice");
```

Đây là fragment minh họa, `s` là một `Subject` đã được tạo/phê duyệt. Khi migrate sang `callAs`, overload ambiguity không còn vì `Callable<T>` là một dạng tham số duy nhất.

### 5.1. `doAs` có bị xóa khỏi JDK 24 không?

**Chưa.** Tài liệu Java SE 24–26 vẫn liệt kê `doAs`, nhưng đánh dấu **deprecated for removal**. Trên JDK 24+, nó vẫn có thể bind `Subject` cho khoảng thời gian action chạy; **không còn cơ chế nâng đặc quyền Security Manager**. Tuy nhiên, không nên giữ mã mới phụ thuộc API deprecated; chuyển sang `Subject.callAs`.

### 5.2. Cái gì thực sự hỏng từ JDK 24?

Mẫu cũ:

```java
AccessControlContext context = AccessController.getContext();
Subject found = Subject.getSubject(context);
```

Trên JDK 24+, `Subject.getSubject(context)` **luôn ném `UnsupportedOperationException`**, kể cả khi gọi bên trong một `Subject.doAs(...)`. Đây là thay đổi API có chủ đích khi Security Manager bị vô hiệu hóa.

Thay bằng:

```java
Subject found = Subject.current();
```

Không chuyển `Subject.getSubject(AccessController.getContext())` thành `Subject.getSubject(null)` để thử vượt lỗi: API này **không hỗ trợ nữa**, không phải chỉ sai tham số.

## 6. Mốc phiên bản Java cần nhớ

| JDK | Tình trạng thực tế | Khuyến nghị khi đọc/nâng cấp |
| --- | --- | --- |
| **Java 8–17** | Legacy `Subject.doAs`/`getSubject(ACC)` tồn tại; **chưa có `current`/`callAs`** | Với code bắt buộc chạy Java 17, chưa thể viết trực tiếp API mới; tách tương thích runtime hoặc lên JDK hỗ trợ |
| **Java 18–22** | `Subject.current`/`Subject.callAs` được bổ sung **từ Java 18**; legacy API deprecated | Viết code mới theo API mới, kiểm thử theo phiên bản Security Manager thực tế |
| **Java 23** | Hành vi `Subject` phụ thuộc trạng thái Security Manager “allowed” hay không; khi không được phép, `getSubject(ACC)` ném UOE và current binding theo scope | Kiểm thử khi không cho phép Security Manager; loại bỏ giả định về ACC và thread inheritance |
| **Java 24–26** | Security Manager **vô hiệu hóa vĩnh viễn** từ JDK 24; `getSubject(ACC)` ném UOE; `doAs` deprecated vẫn bind theo scope | Dùng `current`/`callAs`; tự thực thi authorization, xử lý async context và migration exception |

Ở JDK 23 từng có cấu hình tương thích tạm thời `-Djava.security.manager=allow` trong một số trường hợp để giữ hành vi cũ. **Đó không phải giải pháp nâng cấp lâu dài và không khôi phục Security Manager trên JDK 24+.** Không hướng dẫn production bật lại một cơ chế đã vô hiệu hóa để sửa lỗi JAAS.

### 6.1. JEP 411 và JEP 486 liên quan thế nào?

- **JEP 411 (Java 17):** đánh dấu Security Manager **deprecated for removal**.
- **Java 18:** thêm `Subject.current` và `Subject.callAs` như hướng thay thế các phương thức JAAS Subject phụ thuộc ACC.
- **Java 23:** điều chỉnh hành vi khi Security Manager không được phép, giúp phát hiện sớm code đang phụ thuộc `getSubject(ACC)` hoặc việc truyền context qua child thread.
- **JEP 486 (Java 24):** vô hiệu hóa Security Manager vĩnh viễn; authorization dựa trên Security Manager/policy files không còn được hỗ trợ.

**Điểm phân biệt:** “đổi tên `doAs` thành `callAs`” chỉ là **một phần** migration. Nếu code trước đây dựa vào Security Manager để chặn truy cập file/network/API, bạn phải **thiết kế lại cơ chế kiểm tra quyền** tương ứng, không chỉ sửa cú pháp.

## 7. Ví dụ runnable Java 18+: current, nested callAs và ExecutorService

Ví dụ này **tạo Subject giả lập trong memory để học API**, không có JAAS LoginModule hay password thật. Nó kiểm tra: phạm vi rỗng → Alice → Bob → Alice, `callAs(null)`, khôi phục sau exception, và sự khác biệt giữa task thread pool **không được truyền context** so với task **được re-bind tường minh**.

Dùng `javax.security.auth.x500.X500Principal` làm kiểu `Principal` có sẵn trong JDK, thay vì viết thêm principal class. Constructor `new Subject(true, ...)` tạo một đối tượng **read-only** theo mục đích demo.

Lưu file **SubjectScopeDemo.java**:

```java
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

public class SubjectScopeDemo {
    static Subject user(String name) {
        return new Subject(true,
                Set.of(new X500Principal("CN=" + name)),
                Set.of(), Set.of());
    }

    public static void main(String[] args) throws Exception {
        Subject alice = user("Alice");
        Subject bob = user("Bob");

        System.out.println("outside null: "
                + (Subject.current() == null));

        String result = Subject.callAs(alice, () -> {
            boolean aliceVisible = Subject.current() == alice;
            boolean nestedBob = Subject.callAs(
                    bob, () -> Subject.current() == bob);
            boolean restoredAlice = Subject.current() == alice;
            boolean temporarilyNull = Subject.callAs(
                    null, () -> Subject.current() == null);
            boolean restoredAgain = Subject.current() == alice;
            return aliceVisible + "," + nestedBob + ","
                    + restoredAlice + "," + temporarilyNull
                    + "," + restoredAgain;
        });
        System.out.println("nested scope checks: " + result);
        System.out.println("after scope null: "
                + (Subject.current() == null));

        boolean checkedExceptionWrapped = false;
        try {
            Subject.callAs(alice, () -> {
                throw new IOException("test failure");
            });
        } catch (CompletionException expected) {
            checkedExceptionWrapped =
                    expected.getCause() instanceof IOException;
        }
        System.out.println("checked exception wrapped: "
                + checkedExceptionWrapped);
        System.out.println("after exception null: "
                + (Subject.current() == null));

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // Tạo worker trước khi submit trong phạm vi Alice.
            pool.submit(() -> Subject.current()).get();

            Future<Boolean> implicit = Subject.callAs(
                    alice,
                    () -> pool.submit(() -> Subject.current() == null));

            Future<Boolean> explicit = Subject.callAs(alice, () -> {
                Subject captured = Subject.current();
                return pool.submit(() ->
                        Subject.callAs(captured,
                                () -> Subject.current() == alice));
            });

            System.out.println("pool not inherited: "
                    + implicit.get());
            System.out.println("explicit propagation works: "
                    + explicit.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

**Phiên bản tối thiểu: Java 18.** Có thể biên dịch với JDK 24 bằng mức tương thích `--release 18`:

```bash
javac --release 18 -encoding UTF-8 SubjectScopeDemo.java
java SubjectScopeDemo
```

Stdout đã xác minh khi chạy trên JDK 24:

```text
outside null: true
nested scope checks: true,true,true,true,true
after scope null: true
checked exception wrapped: true
after exception null: true
pool not inherited: true
explicit propagation works: true
```

### 7.1. Giải thích chi tiết kết quả

**Trước `callAs`:** demo chưa gắn `Subject`, nên `Subject.current()==null` là `true`.

**Bên trong Alice:** `current()==alice` là `true`; so sánh bằng `==` ở đây chứng minh đúng **cùng object reference** được gắn, không phải so sánh danh tính nghiệp vụ.

**Trong scope Bob:** `callAs(bob, ...)` tạm thay Subject hiện tại. Khi Bob kết thúc, Alice được phục hồi. Đây là **cấu trúc scope lồng nhau**, không phải thay đổi vĩnh viễn một global singleton.

**`callAs(null)`:** tạm cho `current()==null`, rồi Alice được phục hồi ngay sau callback.

**Ngoài scope:** `current()==null` lại là `true`; không cần gọi phương thức `setCurrent(null)` không tồn tại.

**Checked exception:** `IOException` từ `Callable.call` được chuyển thành nguyên nhân của `CompletionException`. Sau khi catch, current vẫn được khôi phục về `null`.

**ExecutorService:** `pool.submit` không tự kế thừa subject đang gắn ở thread gọi. Demo đã **khởi tạo worker trước** để tránh nhầm với bất kỳ cơ chế thread creation nào; task chạy trong worker kiểm tra `current()==null`.

**Truyền tường minh:** capture `Subject` từ scope Alice, gửi object reference đến worker rồi **gọi `Subject.callAs(captured, ...)` trong worker**; task đó nhìn thấy Alice trong đúng phạm vi của chính nó. Hết task, worker được khôi phục context trước đó.

### 7.2. Những gì demo KHÔNG chứng minh

- Không chứng minh Alice/Bob đã authenticate: hai `Subject` được tạo thủ công chỉ để thử scoped API.
- Không chứng minh có quyền admin hay quyền file/network: **`callAs` không thực thi quyền Security Manager**.
- Không chứng minh mọi concurrency framework đều truyền context giống nhau: executor, CompletableFuture, parallel stream, reactive streams và structured concurrency có lifecycle khác nhau.
- Không bảo đảm worker không thể nhìn thấy một `Subject` ở bối cảnh khác nếu có custom wrapper/framework đặc thù; hãy kiểm thử **runtime và context propagation policy** của ứng dụng.
- Không chứng minh truyền một `Subject` mutable có private credentials giữa nhiều thread là an toàn; bảo vệ credential và lifetime riêng.

## 8. Ví dụ runnable: cách đọc legacy `doAs` và lỗi `getSubject` trên JDK 24+

Code cũ thường dùng hai overload `doAs`, và lấy `Subject` thông qua `AccessController.getContext()`. Chương trình này cho thấy **doAs vẫn bind subject trên JDK 24**, nhưng **lối đọc dựa trên ACC không còn dùng được**.

Lưu file **LegacySubjectDemo.java**:

```java
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

@SuppressWarnings("removal")
public class LegacySubjectDemo {
    public static void main(String[] args) throws Exception {
        Subject alice = new Subject(
                true, Set.of(new X500Principal("CN=Alice")),
                Set.of(), Set.of());

        String result = Subject.doAs(alice,
                (PrivilegedAction<String>) () ->
                        Subject.current() == alice
                                ? "Alice" : "not Alice");
        System.out.println("legacy doAs still binds: " + result);

        boolean checkedWrapped = false;
        try {
            Subject.doAs(alice,
                    (PrivilegedExceptionAction<Void>) () -> {
                        throw new IOException("legacy failure");
                    });
        } catch (PrivilegedActionException expected) {
            checkedWrapped =
                    expected.getException() instanceof IOException;
        }
        System.out.println("legacy checked wrapped: " + checkedWrapped);

        boolean oldReaderUnavailable = false;
        try {
            Subject.getSubject(AccessController.getContext());
        } catch (UnsupportedOperationException expected) {
            oldReaderUnavailable = true;
        }
        System.out.println("getSubject unsupported on JDK 24+: "
                + oldReaderUnavailable);
    }
}
```

Biên dịch **Java 18+** vì ví dụ có dùng `Subject.current()`; chạy trên **JDK 24+** để thấy `getSubject` không còn dùng được:

```bash
javac --release 18 -encoding UTF-8 LegacySubjectDemo.java
java LegacySubjectDemo
```

Stdout kiểm tra trên JDK 24:

```text
legacy doAs still binds: Alice
legacy checked wrapped: true
getSubject unsupported on JDK 24+: true
```

**`@SuppressWarnings("removal")` trong demo chỉ để giữ ví dụ tập trung, không phải khuyến nghị che cảnh báo trong production.** Trong dự án thực, hãy chuyển sang API mới, kiểm tra các phụ thuộc legacy và giữ cảnh báo deprecation trong CI khi có thể.

### 8.1. Vì sao hai exception wrapper khác nhau?

- **`doAs(PrivilegedExceptionAction)`:** checked exception từ `run()` được bọc bằng **`PrivilegedActionException`**; lấy exception thật qua `getException()` hoặc `getCause()`.
- **`callAs(Callable)`:** exception từ `call()` được bọc bằng **`CompletionException`**; lấy cause qua `getCause()`.
- **`doAs(PrivilegedAction)`:** bản thân `run()` không khai báo checked exception; cần phân biệt unchecked exception từ nghiệp vụ.
- **`Subject.getSubject(ACC)`:** ném **`UnsupportedOperationException`** trên JDK 24+, không phải “không có user nên trả `null`”.

Đừng đổi catch từ `PrivilegedActionException` thành `IOException` trực tiếp mà bỏ qua wrapper. Đây là nguồn gây lỗi khi migration code có checked exceptions.

## 9. Chuyển đổi code cũ sang API mới

### 9.1. Bản đồ thay thế

| Trong code legacy | Thay thế hiện đại | Điều phải kiểm tra |
| --- | --- | --- |
| `Subject.getSubject(AccessController.getContext())` | `Subject.current()` | Có thể `null`; không cần và không nên lấy ACC |
| `Subject.doAs(subject, PrivilegedAction<T>)` | `Subject.callAs(subject, Callable<T>)` | Bỏ cast `PrivilegedAction`; thay đổi wrapper khi action throw |
| `Subject.doAs(subject, PrivilegedExceptionAction<T>)` | `Subject.callAs(subject, Callable<T>)` | `PrivilegedActionException` → `CompletionException` |
| `Subject.doAsPrivileged(subject, action, acc)` | Gắn context bằng `callAs` nếu chỉ cần identity | Không có thay thế cho **giới hạn/nâng quyền** của Security Manager |
| `AccessController.doPrivileged(...)` | Thiết kế lại phần authorization nếu phụ thuộc SM | Không đổi tên trực tiếp để khôi phục permission enforcement |
| ACC chứa Subject được truyền đến child threads | Truyền `Subject` đã authenticate và bind lại trong task | Không kỳ vọng ExecutorService tự kế thừa |

### 9.2. Mẫu migrate lấy identity hiện hành

Trước (cũ, phụ thuộc Security Manager/ACC):

```java
// LEGACY - minh họa; code hiện đại không nên dùng.
Subject s = Subject.getSubject(AccessController.getContext());
if (s == null) {
    throw new SecurityException("Missing JAAS subject");
}
```

Sau (Java 18+):

```java
Subject s = Subject.current();
if (s == null) {
    throw new SecurityException("Missing JAAS subject");
}
```

Trong cả hai fragment, một `Subject` có mặt **không đảm bảo authentication**: vẫn cần chắc chắn caller chỉ tạo/bind `Subject` từ nguồn đã xác thực và kiểm tra authorization.

### 9.3. Migrate action trả kết quả

Trước:

```java
String value = Subject.doAs(authenticatedSubject,
        (PrivilegedAction<String>) () -> service.readValue());
```

Sau:

```java
String value = Subject.callAs(authenticatedSubject,
        () -> service.readValue());
```

Các biến `authenticatedSubject`/`service` là phần phụ thuộc của ứng dụng, nên đây là **fragment**, không phải chương trình độc lập. Khi action chỉ cần thấy `Subject.current`, thay thế về hành vi binding là phù hợp. Nếu code cũ phụ thuộc Security Manager cho phép file/URL/permission, phải xử lý ranh giới đó riêng.

### 9.4. Migrate checked exception an toàn

Mẫu cũ:

```java
try {
    return Subject.doAs(authenticatedSubject,
            (PrivilegedExceptionAction<String>)
                    () -> service.loadFromDisk());
} catch (PrivilegedActionException e) {
    Throwable original = e.getException();
    // Phân loại original theo hợp đồng API ứng dụng.
    throw new IllegalStateException("Operation failed", original);
}
```

Mẫu mới:

```java
try {
    return Subject.callAs(authenticatedSubject,
            () -> service.loadFromDisk());
} catch (CompletionException e) {
    Throwable original = e.getCause();
    // Không nuốt exception; chọn type/lỗi an toàn theo nghiệp vụ.
    throw new IllegalStateException("Operation failed", original);
}
```

Hai đoạn là **fragments**, cần nằm trong method có return type tương ứng và import `CompletionException`, `PrivilegedExceptionAction`, `PrivilegedActionException`. Với lỗi `IOException` cần giữ nguyên ở API cấp trên, có thể xét:

```java
try {
    return Subject.callAs(authenticatedSubject,
            () -> service.loadFromDisk());
} catch (CompletionException e) {
    if (e.getCause() instanceof IOException io) {
        throw io;
    }
    throw e;
}
```

Fragment này chỉ compile trong một method `throws IOException` (hoặc xử lý checked exception phù hợp); dùng Java 18+ và các biến dịch vụ đã có.

### 9.5. Không sửa code chỉ bằng search-and-replace

Một migration đúng đòi rà soát:

1. Chỗ nào lấy `Subject` từ `LoginContext.getSubject()` hoặc nguồn xác thực khác?
2. Chỗ nào tạo `Subject` giả chỉ bằng `new Subject()` mà code đang nhầm là authenticated?
3. Các đoạn gọi `doAs` có thật sự chỉ cần **identity binding**, hay còn dựa vào Security Manager policy?
4. Có code đọc `Subject.getSubject(AccessController.getContext())` trong thư viện/JAR không?
5. Có code giả định Subject **tự truyền vào thread mới, executor, reactive callback** không?
6. Các checked exceptions được bắt đúng wrapper mới và audit log không lộ credential không?
7. Người dùng bị thiếu `Subject` có bị fail closed ở các thao tác yêu cầu authentication không?
8. Các tests trước/ngoài/sau `callAs`, nested scope và concurrency đã kiểm tra chưa?

## 10. JAAS `LoginContext` và `Subject.callAs` nối với nhau như thế nào?

**Authentication** phải hoàn tất trước khi bind một `Subject` để xử lý yêu cầu yêu cầu danh tính.

Ví dụ logic tích hợp (`LoginContext` phải được cấu hình bằng `LoginModule` và `CallbackHandler` thật; fragment không chạy độc lập):

```java
LoginContext login = new LoginContext("ApplicationLogin", callbackHandler);
try {
    login.login();
    Subject authenticated = login.getSubject();
    if (authenticated == null) {
        throw new SecurityException("JAAS login returned no Subject");
    }
    Subject.callAs(authenticated, () -> {
        service.processAuthenticatedRequest();
        return null;
    });
} finally {
    // Theo lifecycle thực: logout và cleanup credentials.
    login.logout();
}
```

Trong hệ thống thật, xử lý `LoginException`, timeout, retry và callback secrets; đồng thời tránh gọi `logout` khi login chưa thành công nếu module không hỗ trợ. Fragment trên mô tả thứ tự ý tưởng, **không thay thế code vận hành đầy đủ**.

### 10.1. `callAs` không tự gọi `login`

Không có thứ tự ngầm:

```text
Subject.callAs(subject, action)
        !=
LoginContext.login() + authorization
```

Bạn có thể gọi `callAs` với `Subject` trống hoặc tự tạo. Vì vậy, một service không nên dùng điều kiện `Subject.current()!=null` làm **bằng chứng duy nhất** để cho phép rút tiền, tải dữ liệu cá nhân hoặc gọi API đặc quyền.

### 10.2. Đọc principal một cách có mục đích

Mã minh họa cho một ứng dụng đã xác thực, muốn đọc X.500 principal:

```java
Subject s = Subject.current();
if (s == null) {
    throw new SecurityException("No authenticated JAAS context");
}
Set<X500Principal> names =
        s.getPrincipals(X500Principal.class);
for (X500Principal name : names) {
    System.out.println(name.getName(X500Principal.RFC2253));
}
```

Đoạn trên chỉ phù hợp nếu X.500 principal thật sự được công nghệ xác thực của ứng dụng sử dụng; không tự cho rằng mọi `Subject` có `X500Principal`. Trong production, không log danh tính nhạy cảm trừ khi policy cho phép và không in private credentials.

### 10.3. Logout và scope khác nhau

`callAs` luôn **khôi phục current subject binding** khi action hoàn tất; điều đó **không tự đăng xuất** khỏi KDC, không hủy Kerberos tickets/token và không xóa secrets khỏi `Subject`.

`LoginContext.logout()` dùng để yêu cầu các `LoginModule` cleanup theo contract của chúng. Bạn phải quản lý **cả hai vòng đời**: phạm vi thực thi và thời hạn credentials/phiên đăng nhập.

## 11. Async, executor, virtual threads và context propagation

### 11.1. Vì sao Subject “biến mất” khi chuyển sang worker?

Code phía request thread:

```java
Subject.callAs(requestSubject, () -> {
    executor.submit(() -> service.handleRequest());
    return null;
});
```

Đây là fragment. Nếu `handleRequest` gọi `Subject.current()` trên một **executor worker thread**, nó **không mặc định** nhận `requestSubject` từ thread submit. Worker có thể được tạo trước đó và phục vụ nhiều request khác nhau; việc mong Java tự sao chép per-thread context sẽ dễ sai.

Điểm này đặc biệt quan trọng khi hệ thống hiện đại hóa từ Java cũ vốn dựa vào `AccessControlContext` hoặc library propagation đặc thù.

### 11.2. Truyền tường minh và bind theo scope trong worker

Một mẫu rõ ràng hơn:

```java
Subject captured = Subject.current();
Future<Result> future = executor.submit(() ->
        Subject.callAs(captured, () -> service.compute()));
```

Đây là fragment. Để dùng an toàn:

- Chỉ capture `Subject` **được authenticate/ủy quyền đúng** trong request hiện tại.
- Hạn chế lifetime của task, không giữ object chứa private credentials quá lâu trong queue.
- `callAs` đặt context trong **worker** và khôi phục sau khi task kết thúc; đây là cách tránh rò rỉ giữa các request khi dùng thread pool.
- `Future.get()`/`CompletableFuture` có các wrapper exceptions riêng (`ExecutionException`/`CompletionException`); nhớ xử lý **nhiều tầng cause** thay vì chỉ thay một catch.
- Nếu task tạo thêm task khác, phải thiết kế chính sách truyền context xuyên qua **toàn bộ cây task**, hoặc dùng framework context propagation đã được kiểm định.

### 11.3. Tại sao không tự dùng một `ThreadLocal<Subject>` global?

Có thể có lý do legacy, nhưng một custom `ThreadLocal` dễ bị:

- Quên `remove()` trong pool → request sau nhìn danh tính request trước.
- Copy reference của private credentials vào những thread không có quyền.
- Dùng một thread cho nhiều request reactive/coroutine mà semantics không đúng.
- Lẫn với `Subject.current()` của JDK, khiến thư viện đọc một context còn ứng dụng ghi context khác.

Ưu tiên scope **`Subject.callAs`** nếu thư viện đã tiêu thụ `Subject.current`; đảm bảo context được khôi phục có cấu trúc. Nếu app có framework security context riêng, hãy tích hợp qua API được hỗ trợ, không tự biến mọi identity thành Subject.

### 11.4. Structured concurrency

Tài liệu JDK mới mô tả rằng current Subject **có thể được kế thừa bởi child threads khởi chạy và hoàn tất trong scope cha khi sử dụng structured concurrency** phù hợp. Tuy nhiên:

- Đây **không** là cơ chế tự truyền cho mọi `new Thread` hoặc mọi `ExecutorService`.
- Structured concurrency có lịch sử **preview API** ở các JDK khác nhau; không đưa mã preview vào ví dụ Java 18+ ở bài này.
- Nếu một framework dùng structured concurrency, kiểm tra phiên bản JDK, giới hạn scope, cách hủy task và rules inheritance cụ thể.
- Với thread pool và tác vụ không có quan hệ cha–con có cấu trúc, dùng **truyền tường minh** hoặc adapter context propagation đáng tin.

## 12. Authorization trong thế giới không còn Security Manager

### 12.1. Chỉ bind Subject không đủ

Trên JDK 24+, `Subject.callAs` gắn thông tin danh tính cho code đọc, nhưng **không có bộ permission file/`SecurityManager.checkPermission` hoạt động như thời cũ**.

Ví dụ conceptual:

```text
User authenticator  -> Subject trusted by application
Subject.callAs(...) -> Scope so service can read identity
Authorization        -> Check identity/roles/tenant/resource policy
Sensitive operation  -> Execute only if policy explicitly allows
Audit                -> Record safe metadata, not credentials
```

Nếu đoạn code cần đọc file hoặc thao tác database, **OS ACL, IAM credentials, DB privileges và authorization logic** vẫn là các lớp kiểm soát thực sự. Không dựa vào lời gọi `callAs` để tự động chống việc một user không được phép đọc dữ liệu.

### 12.2. `Subject.current` có thể là input cho policy, không phải policy

Một ứng dụng có thể có code kiểu:

```java
Subject current = Subject.current();
if (current == null) {
    throw new SecurityException("Authentication required");
}
authorizationService.requireAccess(
        current, "orders:read", requestedOrderId);
```

Đây là fragment: `authorizationService` và `requestedOrderId` phải do ứng dụng cung cấp. Quan trọng là `authorizationService` phải:

1. Chỉ chấp nhận một `Subject` có **nguồn xác thực đáng tin** từ boundary được kiểm soát.
2. Phân biệt principal là identity, không tự đọc tên role giả do caller tự thêm.
3. Kiểm tra **quyền trên tài nguyên cụ thể** (tenant, owner, scope, action).
4. Fail closed khi không có đủ context, credentials hết hạn hoặc policy không rõ.
5. Không để caller untrusted tự chọn `Subject` rồi mượn `callAs` để vượt quyền.

### 12.3. Giải pháp thay `doAsPrivileged`

Nếu code cũ làm:

```java
// LEGACY: từng sử dụng AccessControlContext/policy permissions.
Subject.doAsPrivileged(subject, action, context);
```

Bạn **có thể** thay phần “bind identity trong một scope” bằng `Subject.callAs(subject, callable)` nếu service chỉ cần current subject. Nhưng **không có API thay thế tương đương cho việc nâng/quy định permission của Security Manager**. Quyền thao tác file/network/API phải được chuyển sang ranh giới khác (IAM/OS permissions/ACL, service boundary, explicit policy), có kiểm thử regressions và threat model.

## 13. Lỗi và triệu chứng hay gặp khi nâng JDK

| Triệu chứng | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| `cannot find symbol: Subject.current` ở `javac --release 17` | API chỉ có từ Java 18 | Nâng target JDK hoặc giữ code compatibility riêng, không giả là API Java 17 |
| `UnsupportedOperationException` trong `Subject.getSubject(...)` JDK 24 | ACC-based reader đã bị vô hiệu hóa | Chuyển sang `Subject.current()` và scope `callAs` |
| `doAs` warning deprecated for removal | API cũ phụ thuộc Security Manager concept | Migrate sang `callAs`, kiểm thử exception |
| `reference to doAs is ambiguous` | Hai overload với functional interface gần nhau | Cast `PrivilegedAction`/`PrivilegedExceptionAction` trong legacy; code mới dùng Callable |
| Catch `PrivilegedActionException` không bắt lỗi sau migrate | `callAs` dùng `CompletionException` | Đổi handling và preserve root cause |
| `Subject.current()==null` trong executor | Subject không tự kế thừa qua thread pool | Capture trusted subject, bind trong task bằng `callAs` |
| Subject xuất hiện sai user ở worker | Custom ThreadLocal/context bị rò, thiếu cleanup | Dùng scoped binding; kiểm thử isolation giữa request |
| `Subject.current()!=null` nhưng API bị truy cập trái phép | Đang coi presence of Subject là authorization | Kiểm tra policy/tenant/action/resource riêng |
| Kerberos/GSS lỗi sau nâng JDK | Thư viện dùng ACC/getSubject hoặc phụ thuộc inheritance cũ | Nâng dependency, kiểm thử JAAS login, delegation và thread propagation |
| Dùng `-Djava.security.manager=allow` trên JDK 24 để cứu code | Security Manager đã vô hiệu hóa vĩnh viễn | Sửa code/API, không dùng flag cũ làm giải pháp |
| Mất private credentials sau `logout` | LoginModule đã cleanup session hoặc code giữ Subject quá lâu | Quản lý lifecycle và tác vụ bất đồng bộ đúng |
| Nhiều `Principal` nhưng app lấy phần tử đầu tiên | Không có thứ tự/semantics universal của Set | Lọc đúng Principal class và policy của domain |
| Debug in `Subject.toString()` | Có nguy cơ ghi lộ metadata/credential theo implementation | Log tối thiểu, không in private credentials |
| Context bị giữ quá lâu trong queue | Captured Subject mang credential đã hết hạn/thu hồi | TTL, hủy task, tái xác minh tại boundary đúng |

### 13.1. Diagnostic checklist

Khi thấy lỗi “Subject not found” sau nâng JDK:

1. In phiên bản JDK trong môi trường test (không log thông tin đăng nhập): `java -version`.
2. Kiểm tra source/classpath có `getSubject(AccessController.getContext())` hay không.
3. Kiểm tra có `Subject.doAs`/`doAsPrivileged`, hoặc `AccessController.doPrivileged` phụ thuộc permission của Security Manager.
4. Xem `LoginContext.login()` có thật sự thành công và tạo `Subject` đúng không.
5. Xác định callback chạy **cùng scope/thread** với `callAs` hay đã chuyển sang executor/async.
6. Kiểm tra root cause của `CompletionException` và những wrapper khác.
7. Chạy test trước/ngoài/sau scope, nested calls, exception, executor và many concurrent users.
8. Kiểm tra các authorization decisions bằng test có Subject đúng, sai, null, hết hạn và giả mạo.

## 14. Dùng `jdeprscan` để phát hiện API deprecated

JDK có công cụ **`jdeprscan`** hỗ trợ rà soát class/JAR có gọi API deprecated. Ví dụ:

```bash
jdeprscan --release 24 path/to/legacy-app.jar
```

Đây là lệnh minh họa; thay đường dẫn JAR bằng **artifact của chính ứng dụng**. Nó có thể chỉ ra các dùng `AccessController`/`Subject.getSubject` hoặc API Java khác deprecated. **Không xem output trống là bằng chứng đã migrate hoàn toàn**, vì không phải mọi đường gọi động/reflection hoặc hành vi Security Manager đều được phát hiện bằng quét bytecode.

Trong kiểm thử local của chương trình `LegacySubjectDemo`, `jdeprscan --release 24` đã cảnh báo việc dùng `AccessController` và `Subject.getSubject`. Cần kết hợp với tìm kiếm mã nguồn và kiểm thử thực tế.

### 14.1. Các bước migrate cho dự án nhiều module

1. **Inventory:** liệt kê mọi lời gọi `doAs`, `doAsPrivileged`, `getSubject`, `AccessController`; xem dependencies/frameworks có dùng JAAS/GSS không.
2. **Version policy:** xác định minimum JDK. Nếu vẫn cần Java 17, không đưa thẳng `Subject.callAs` vào source được compile `--release 17`; có thể tách adapter theo runtime hoặc nâng minimum JDK có kế hoạch.
3. **Authentication boundary:** xác định nguồn `Subject` sau JAAS login; không chấp nhận Subject tự tạo từ user input.
4. **Context scope:** thay `doAs` bằng `callAs` khi chỉ cần binding, thay `getSubject(ACC)` bằng `current`.
5. **Authorization boundary:** chuyển mọi logic từng dựa vào Security Manager sang policy/IAM/ACL explicit.
6. **Concurrency:** rà soát thread pools, `CompletableFuture`, async RPC, reactive flows, structured concurrency; truyền Subject đúng cách, không rò.
7. **Exceptions:** sửa `PrivilegedActionException` sang `CompletionException`, giữ cause chính xác; test checked/unchecked failure.
8. **CI:** chạy `javac --release <minimum>`, `jdeprscan`, unit/integration tests trên JDK mục tiêu và negative security tests.
9. **Production rollout:** quan sát logs an toàn về authentication/authorization failures, bật rollback plan không hạ security policy, nâng dependencies trước khi đổi JDK diện rộng.

## 15. Lưu ý an toàn về credentials, Subject và quyền truy cập

### 15.1. `Subject.current()` không phải chứng thực mật mã

Không đánh giá một service là trusted chỉ vì nó trả `Subject.current() != null`. Đối tượng đó có thể được tự tạo từ các `Principal` bất kỳ. Authentication phải được tiến hành bởi JAAS LoginModule hoặc boundary tin cậy, và việc cấp quyền phải xét identity/role/scopes đã được verified.

### 15.2. Không lan truyền private credentials một cách tùy tiện

`Subject` có thể chứa **Kerberos tickets**, tokens, keys, certificates hoặc credentials khác. Khi capture và gửi `Subject` đến executor:

- Chỉ truyền khi task thật sự cần current subject và được ủy quyền.
- Không lưu đối tượng trong global static cache hoặc queue không có TTL.
- Không serialize `Subject` làm payload mạng chỉ để “giữ context”.
- Không in `Subject` hoặc các credentials trong debug log.
- Dọn/làm hết hiệu lực credentials đúng theo `LoginModule.logout` hoặc chính sách của technology.

### 15.3. Quyền không tăng nhờ nesting

Nếu một task không có quyền thực hiện thao tác, `Subject.callAs(adminSubject, ...)` do **code không được tin cậy** thực hiện không nên là cách vượt quyền. Một lớp authorization đúng phải bảo vệ **nguồn Subject, khả năng bind, quyền của workload và chính sách tài nguyên**, không chỉ đọc một chuỗi tên principal.

### 15.4. Thông điệp lỗi không được lộ credentials

`CompletionException.getCause()` đôi khi chứa chi tiết service hoặc authentication. Khi trả lỗi ra API, đừng phản hồi nguyên nội dung exception từ JAAS/LDAP/Kerberos vào client. Log an toàn theo correlation ID, trạng thái, module và mã lỗi; hạn chế tên user, tokens, paths và private credentials theo policy.

## 16. Bài tập tự kiểm tra

**Câu 1.** `Subject.current()` được thêm từ Java 17 hay Java 18?  
**Đáp án:** **Java 18**; `--release 17` không biên dịch code gọi phương thức này.

**Câu 2.** Sau khi `Subject.callAs(alice, action)` hoàn tất, current subject có mặc định vĩnh viễn là Alice không?  
**Đáp án:** **Không.** Nó được khôi phục về giá trị trước khi gọi, thường `null` nếu ngoài scope.

**Câu 3.** Lồng `Subject.callAs(bob, action)` vào trong scope Alice thì trong Bob và sau Bob có gì?  
**Đáp án:** Trong action Bob, `current()==bob`; sau action Bob và trước khi scope Alice kết thúc, `current()==alice`.

**Câu 4.** `callAs` có nhận `Subject` là `null` không?  
**Đáp án:** **Có.** Bên trong action current subject sẽ là `null`; scope bên ngoài được khôi phục sau đó. `action` thì không được `null`.

**Câu 5.** Một `Callable` ném `IOException`. Caller `callAs` bắt loại exception nào?  
**Đáp án:** **`CompletionException`** và lấy `IOException` gốc qua `getCause()`.

**Câu 6.** `doAs(PrivilegedExceptionAction)` cũng ném `CompletionException` trên JDK cũ?  
**Đáp án:** **Không.** Checked exception được bọc bằng `PrivilegedActionException`, đây là khác biệt quan trọng khi migrate.

**Câu 7.** Trên JDK 24, `Subject.doAs(subject, action)` có còn trong API không?  
**Đáp án:** **Có**, nhưng deprecated for removal; nó bind current subject theo scope, **không nâng đặc quyền Security Manager**.

**Câu 8.** Trên JDK 24, code `Subject.getSubject(AccessController.getContext())` nếu chưa có Subject sẽ trả `null`?  
**Đáp án:** **Không.** API này luôn ném `UnsupportedOperationException`, không phụ thuộc có Subject hay không.

**Câu 9.** Thread pool task submit bên trong `Subject.callAs(alice, ...)` có tự kế thừa Alice không?  
**Đáp án:** **Không được giả định.** Executor worker thông thường không tự mang current subject từ submitter; capture Subject đáng tin và bind lại khi thực thi.

**Câu 10.** `Subject.callAs` có đủ để chấp nhận một user truy cập tài khoản ngân hàng không?  
**Đáp án:** **Không.** Cần authentication chính thống và authorization theo user/tenant/resource/action; context binding chỉ là đầu vào.

**Câu 11.** Một `Subject` có principal tên `"admin"` là tự chứng minh đã được cấp role admin?  
**Đáp án:** **Không.** Tên principal phải đến từ nguồn đã xác thực và quyền do policy xác định; tạo `new Subject()` không phải login.

**Câu 12.** Khi migrate `doAsPrivileged`, có thể chỉ thay bằng `callAs` rồi coi hệ thống vẫn bảo vệ file bằng Java Security Manager không?  
**Đáp án:** **Không.** Security Manager không còn hỗ trợ từ JDK 24; phải thiết kế lại authorization/OS permissions/IAM.

**Câu 13.** Tại sao cần thử lỗi trong nested `callAs`?  
**Đáp án:** Để kiểm tra context được **khôi phục đúng kể cả khi action throw**, không rò danh tính sang request hoặc task khác.

**Câu 14.** Dùng `jdeprscan` không thấy cảnh báo thì có chắc code không còn vấn đề JAAS/Security Manager?  
**Đáp án:** **Không.** Vẫn phải kiểm tra dynamic/reflection calls, dependencies, permission semantics, concurrency và integration tests.

## 17. Nguồn tham khảo chính thức

- [Java SE 26 — Subject API](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/javax/security/auth/Subject.html): `current`, `callAs`, `doAs`, `getSubject`, version và exception semantics.
- [Oracle — Migrating from Subject.getSubject/Subject.doAs to Subject.current/Subject.callAs](https://docs.oracle.com/en/java/javase/25/security/migrating-from-deprecated-removal-subject-getsubject-subject-doas-methods-subject-current-subject-callas-methods.html): migration và thay đổi sau JDK 24.
- [Oracle — Java Authentication and Authorization Service Reference Guide](https://docs.oracle.com/en/java/javase/24/security/java-authentication-and-authorization-service-jaas-reference-guide.html): JAAS authentication, Subject/Principals và giới hạn authorization hiện đại.
- [OpenJDK — JEP 486: Permanently Disable the Security Manager](https://openjdk.org/jeps/486): nguyên nhân thay đổi hành vi Java 24.
- [OpenJDK — JEP 411: Deprecate the Security Manager for Removal](https://openjdk.org/jeps/411): bối cảnh từ Java 17.
- [Oracle — JDK 23 Release Notes, Subject API changes](https://www.oracle.com/java/technologies/javase/23-relnote-issues.html): trạng thái Security Manager và cách Subject được truyền sang child threads.
- [Java SE 26 — LoginContext](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/javax/security/auth/login/LoginContext.html): tạo `Subject` từ cơ chế JAAS login.
- [Oracle — jdeprscan](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdeprscan.html): quét bytecode để nhận diện các API deprecated.

### Ghi nhớ

**`Subject.current()` đọc danh tính đang được gắn với scope; `Subject.callAs(subject, action)` chỉ bind trong khoảng chạy action và tự khôi phục sau đó.** Hai API được thêm từ **Java 18**. Code cũ dùng **`doAs`** cần migrate vì deprecated; đặc biệt, **`getSubject(AccessController.getContext())` luôn ném `UnsupportedOperationException` từ JDK 24**. Đừng mang tư duy Security Manager permissions sang `callAs`: **authentication, context propagation và authorization là ba trách nhiệm khác nhau**, cần kiểm soát nguồn `Subject`, ràng buộc thread/task và quyền tài nguyên một cách tường minh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jaas-authentication-008">17.8 Quản lý credential, Subject chỉ đọc và xử lý LoginException</a>

<details>
<summary>Xem chi tiết</summary>

# 17.8 Quản lý credential, `Subject` chỉ đọc và xử lý `LoginException`

## 1. Mục tiêu học tập

Trong **JAAS (Java Authentication and Authorization Service)**, đăng nhập thành công không chỉ là trả về `true`. Hệ thống còn phải quản lý **danh tính (`Principal`)**, **credential**, đối tượng **`Subject`**, vòng đời đăng nhập–đăng xuất và các trường hợp xác thực thất bại. Những thao tác tưởng đơn giản như đưa password vào `String`, đánh dấu `Subject` chỉ đọc quá sớm hoặc nuốt `LoginException` đều có thể khiến bí mật tồn tại lâu hơn cần thiết hoặc khiến ứng dụng chấp nhận trạng thái xác thực sai.

Sau bài học, bạn có thể:

1. Phân biệt `Principal`, **public credential**, **private credential** và secret chỉ dùng tạm trong lúc đăng nhập.
2. Hiểu vai trò của `LoginContext`, `LoginModule`, `CallbackHandler`, `PasswordCallback` và `Configuration`.
3. Quản lý credential từ lúc thu nhận, xác thực, đưa vào `Subject`, sử dụng, đăng xuất đến **hủy bí mật** bằng `Destroyable`.
4. Giải thích chính xác điều `Subject.setReadOnly()` làm và **không làm**, đặc biệt là khác biệt giữa **khóa cấu trúc các tập** và **xóa/hủy dữ liệu bên trong đối tượng credential**.
5. Xử lý `LoginException`/`FailedLoginException` theo nguyên tắc **fail closed**, rollback/cleanup đúng pha, logging tối thiểu và không làm rò rỉ password.
6. Chạy một ví dụ **Java 17+** minh họa đăng nhập, snapshot chỉ đọc, cleanup bình thường, logout với `Subject` đã khóa và đăng nhập thất bại.

**Phạm vi:** JAAS cấp ứng dụng / Java SE, không phải hướng dẫn viết một nhà cung cấp xác thực production. Chương trình mẫu dùng mật khẩu giả lập cứng chỉ để kiểm thử lifecycle và **không** thay thế Kerberos, LDAP, IAM, KMS, password hashing hay credential store thực.

## 2. JAAS có những thành phần nào?

```text
      Giao diện/ứng dụng                  Cơ chế xác thực (có thể cắm thêm)
      ------------------                 ---------------------------------
      Tạo LoginContext
           |
           | name + CallbackHandler + Configuration (+ Subject tùy chọn)
           v
      LoginContext.login()
           |
           +-------> LoginModule A.login()   <--- CallbackHandler
           |                 |                        NameCallback
           +-------> LoginModule B.login()            PasswordCallback
           |                 |
           |          Kết quả và control flags
           v
      Nếu overall success:
           LoginModule.commit()   --> Subject:
                                     - Principals
                                     - Public credentials
                                     - Private credentials

      Nếu overall failure:
           LoginModule.abort()    --> dọn trạng thái tạm/đã thêm

      Khi kết thúc phiên:
           LoginContext.logout()  --> LoginModule.logout()
                                       xóa credential/principal do module thêm
                                       và hủy secrets theo khả năng
```

| Kiểu/API | Vai trò | Ghi nhớ |
| --- | --- | --- |
| `javax.security.auth.Subject` | Vật chứa danh tính và các credential liên quan tới một thực thể | Không tự xác thực ai cả; **phải được cập nhật từ quy trình đáng tin** |
| `java.security.Principal` | Một danh tính/tên/role được biểu diễn bằng object | Không phải password hoặc token |
| `Subject.getPublicCredentials()` | Tập credentials có thể chia sẻ theo chính sách, tùy từng loại | “Public” không nghĩa bất kỳ ai cũng có quyền ghi |
| `Subject.getPrivateCredentials()` | Tập credential riêng tư, có thể chứa ticket/key/token | **Không bảo đảm tự mã hóa, tự xóa hay ngăn log** |
| `javax.security.auth.login.LoginContext` | Điểm vào của client tới chu trình đăng nhập/đăng xuất | Dựa vào cấu hình các `LoginModule` |
| `javax.security.auth.spi.LoginModule` | Plugin thực hiện `initialize/login/commit/abort/logout` | Trách nhiệm auth thực tế, thêm/xóa state do mình sở hữu |
| `CallbackHandler` | Kết nối module với nguồn username/password/OTP v.v. | Không để module phụ thuộc cách nhập dữ liệu |
| `NameCallback` | Xin/nhận username | Username chưa chứng minh danh tính |
| `PasswordCallback` | Nhận password bằng **`char[]`** | `clearPassword()` xóa bản lưu bên trong callback |
| `Configuration` / `AppConfigurationEntry` | Chọn module và JAAS control flag | Các flags quyết định overall success/failure |
| `LoginException` | Checked exception của JAAS login/logout | Không được chuyển lỗi thành đăng nhập thành công |
| `Destroyable` | Contract `destroy()` / `isDestroyed()` | Xóa tài nguyên bí mật ở mức credential implementation |

**Phân biệt authentication với authorization:** `LoginContext.login()` chỉ xác lập danh tính và credential theo module. Việc một `Subject` chứa `Principal("alice")` không tự cấp phép Alice được truy cập tài khoản, tài nguyên hoặc nghiệp vụ cụ thể. Authorization phải kiểm tra thêm quyền/role, resource, tenant, scope, policy và trạng thái phiên. Cũng không nên cho phép người dùng tự gọi `subject.getPrincipals().add(new Principal(...))` trên một `Subject` đang được coi là nguồn danh tính đáng tin.

## 3. `Principal`, public credential và private credential khác nhau thế nào?

Ví dụ một người dùng đăng nhập bằng Kerberos, một `Subject` có thể mang identity principal và các ticket/keys thích hợp. Với một module tùy chỉnh khác, thông tin có thể là:

```text
Subject (đối tượng gắn với login)
|
+-- Set<Principal>
|     +-- UserPrincipal("alice")              // danh tính
|
+-- Set<Object> publicCredentials
|     +-- chứng thư public / dữ liệu được phép công bố, nếu cần
|
+-- Set<Object> privateCredentials
      +-- phiên/ticket/token/secret object, nếu module cần
```

**Các tập `Subject` là những tập `Set`, không phải kho bí mật chuyên dụng.** Private credentials vẫn là các Java objects bình thường, chịu quy tắc `Subject`/SecurityManager theo runtime, nhưng **không phải encrypted vault**, không được xóa tự động bởi garbage collector đúng một thời điểm định trước, và không bảo đảm có cơ chế thu hồi trên server. Nếu một credential là token truy cập trong production, nó cần **scope, expiry, rotation, revoke** và chính sách riêng.

### 3.1 Phân loại theo độ nhạy cảm

| Dữ liệu | Thường đặt ở đâu? | Nguyên tắc |
| --- | --- | --- |
| Tên người dùng hoặc service identity | `Principal` sau khi xác thực | Không coi tên tự khai là authenticated identity |
| Certificate công khai đã được kiểm tra | `publicCredentials` nếu use case cần | Kiểm tra issuer/trust/usage trước khi tin |
| Kerberos ticket, private key handle, access token | `privateCredentials` **khi cần** và có policy | Giữ ngắn nhất, giới hạn quyền truy cập, tránh in/log |
| Password nhập vào login | `PasswordCallback` tạm thời / bộ nhớ xử lý auth | **Không** giữ như “credential phiên” sau login |
| Password raw dạng `String` | **Tránh** | `String` bất biến, không thể chủ động xóa nội dung trong heap |
| Mật khẩu/hệ số chứng minh được lưu bởi auth server | Dịch vụ identity chuyên biệt | Không lấy từ JAAS `Subject` để làm kho password |

Không nhất thiết module phải có cả ba loại tập: `Subject` chỉ chứa `Principal` là bình thường nếu không cần credential sau login. Ngược lại, giữ tất cả raw passwords dưới `privateCredentials` vì “đã là private” là cách thiết kế nguy hiểm.

### 3.2 Hai phương thức lấy private credentials không giống nhau

```java
// Mảnh code minh họa khi đã có một Subject đáng tin:
java.util.Set<Object> liveSet = subject.getPrivateCredentials();
java.util.Set<MyCredential> filtered =
        subject.getPrivateCredentials(MyCredential.class);
```

- `getPrivateCredentials()` trả **tập backed bởi `Subject`**. Nếu `Subject` còn sửa được và có quyền, thao tác add/remove vào tập này làm thay đổi subject.
- `getPrivateCredentials(Class<T>)` trả **một tập mới đã lọc theo kiểu**, không phải tập backed bởi subject; sửa tập lọc **không** thêm/xóa credential trong subject.
- Khi có SecurityManager, việc duyệt private credentials có thể chịu `PrivateCredentialPermission`/security checks; đừng dựa vào code mẫu chạy không có SecurityManager rồi suy ra mọi runtime đều có quyền.
- **Đừng** log `getPrivateCredentials().toString()`, iterate rồi in secret, hoặc serialize `Subject` tự do. Dù một credential hiện có `toString()` redacted, implementation khác có thể không như vậy.

**Quyền quản lý không tự đồng nghĩa quyền truy cập:** đối tượng private credential có thể tự thực hiện hạn chế hoặc là handle tới HSM/KMS. Một số key không xuất ra byte được; cố ép `getEncoded()` rồi log/fallback thường phá mô hình bảo vệ khóa.

## 4. Vòng đời credential: thu nhận, dùng tạm, commit, destroy

Một chu trình đúng cần phân biệt **password do người dùng nhập** và **credential được cấp sau khi xác thực**.

```text
[Thu nhận]           [Xác thực]                 [Đã đăng nhập]        [Logout]
char[] password ---> LoginModule.login() ---> LoginModule.commit() ---> logout()
                         |                           |                     |
                       kiểm tra                add Principal,       remove owned items
                       password                session credential   destroy secret
                         |                           |
                  clear callback & copies      KHÔNG lưu raw password
                         |
                  fail -> LoginException -> abort/cleanup
```

### 4.1 Vì sao dùng `char[]` thay vì `String`?

`PasswordCallback` dùng `char[]` để ứng dụng có thể chủ động xóa dữ liệu nhạy cảm sau khi dùng:

```java
// Mảnh minh họa trong LoginModule.login(), với callback đã cung cấp password.
javax.security.auth.callback.PasswordCallback pwd =
        new javax.security.auth.callback.PasswordCallback("Password", false);
callbackHandler.handle(new javax.security.auth.callback.Callback[]{pwd});
char[] supplied = pwd.getPassword(); // lấy một bản sao để xử lý
pwd.clearPassword();                 // xóa bản trong PasswordCallback
try {
    // Xác minh với dịch vụ identity đáng tin; tuyệt đối không log supplied.
} finally {
    if (supplied != null) java.util.Arrays.fill(supplied, '\0');
}
```

Với `PasswordCallback`, `setPassword()` sao chép mảng được truyền vào; `getPassword()` trả một mảng sao; `clearPassword()` zero hóa credential của callback. Vì thế phải xử lý **mọi bản sao** do ứng dụng tạo, không chỉ gọi `clearPassword()` rồi tin đã xóa toàn bộ dữ liệu trong process.

Chúng ta không kiểm soát chắc chắn mọi bản sao tạm do JVM/JIT/OS tạo trong bộ nhớ, nhưng cách dùng `char[]` với giới hạn thời gian tồn tại và xóa trong `finally` vẫn giảm rủi ro. **Không thể cam kết xóa tuyệt đối mọi byte trên hệ thống chỉ vì `Arrays.fill`.** Với credential production, cần provider/keystore/HSM/password verification service tùy threat model.

### 4.2 `CallbackHandler` có trách nhiệm riêng

Một `LoginModule` không nên tự đọc `System.console()` hoặc lấy password từ global variable. Nó yêu cầu các `Callback` cần thiết và để ứng dụng cấp một `CallbackHandler`:

- `NameCallback`: username.
- `PasswordCallback`: password char[].
- `UnsupportedCallbackException`: khi handler không đáp ứng callback cần thiết, cần biến thành failure có kiểm soát.
- `IOException`: lỗi đọc input/nguồn credential, cũng phải xử lý như authentication infrastructure failure.

**Không** in password ra console trong callback handler, không giữ `char[]` làm field dùng lâu dài. Trường hợp password lấy từ một request body, phải bảo đảm request framework và logging đã bảo vệ bí mật trước khi đến JAAS.

### 4.3 `Destroyable`: hủy **nội dung** credential

API `javax.security.auth.Destroyable` định nghĩa:

```java
// Định nghĩa contract, không phải lớp triển khai:
void destroy() throws javax.security.auth.DestroyFailedException;
boolean isDestroyed();
```

Một credential tự quản lý material có thể triển khai `destroy()` bằng việc zero hóa byte[]/char[] hoặc đóng handle native. `destroy()` cần được thiết kế không tiết lộ bí mật qua error message. Đối tượng đã destroyed không được dùng lại; cần kiểm tra `isDestroyed()` nếu logic ứng dụng phải quyết định trạng thái.

**Ranh giới trách nhiệm:** `Subject.getPrivateCredentials().remove(token)` chỉ **gỡ tham chiếu khỏi tập**, không tự bảo đảm memory chứa secret bị xóa. Ngược lại, `token.destroy()` có thể xóa material bên trong nhưng **không** tự gỡ object khỏi `Subject`. Muốn kết thúc phiên tốt phải xử lý **cả việc remove và destroy** theo khả năng và lifecycle thực tế.

### 4.4 Credential thật phải có lifecycle ngoài Java heap

Việc hủy `byte[]` không tự revoke một OAuth token phía server, không xóa ticket cache của nhà cung cấp, không thu hồi key trong HSM và không hoàn thành network logout. Những bước đó phụ thuộc credential provider. Khi xử lý sự cố phải xem xét:

- Secret có thể bị sao chép hoặc ghi vào heap dump/trace trước khi hủy.
- Token có thể còn hợp lệ phía server sau local destroy.
- Phiên đăng nhập ở một thread có thể đang bị thread khác sử dụng.
- Role/authorization đã cached có thể tồn tại sau khi `logout`.
- Expiry/revocation/rotation quyết định tính hợp lệ thực, không chỉ trường `isDestroyed()` của một Java object.

## 5. `Subject.setReadOnly()`: chính xác nó khóa điều gì?

Một `Subject` có ba `Set` principal/public/private credentials. Gọi:

```java
Subject subject = new Subject();
// Khi đã hoàn tất những thay đổi cấu trúc mà ứng dụng cho phép:
subject.setReadOnly();
boolean locked = subject.isReadOnly();
```

Khi `subject.isReadOnly() == true`:

1. **Không thể add/remove** phần tử trong `getPrincipals()`, `getPublicCredentials()` hoặc `getPrivateCredentials()`; thao tác sửa tập ném `IllegalStateException` (các kiểm tra permission khác có thể có trên runtime dùng SecurityManager).
2. `setReadOnly()` là thay đổi **một chiều**; Java không có `setWritable()` để đảo trạng thái đó.
3. Trạng thái read-only **không tự biến các object bên trong thành immutable**: một `Principal` có trường mutable hoặc một credential tùy chỉnh có thể vẫn bị thay đổi nội bộ nếu API cho phép.
4. Theo contract Java SE, thao tác **`destroy()` trên các credential vẫn được phép**. Đây là khác biệt quan trọng với add/remove trong các tập.
5. `setReadOnly()` **không xác thực** danh tính; nó chỉ ngăn việc sửa cấu trúc chủ thể sau khi được khóa.

```text
Before setReadOnly:
    Subject Principals: [Alice]
    Subject PrivateCredentials: [SessionCredential]
           |
        add/remove allowed (theo quyền và runtime)
           |
       setReadOnly()
           v
After setReadOnly:
    Subject Principals: [Alice]           -- không thể remove khỏi Subject
    Subject PrivateCredentials: [token]   -- không thể remove khỏi Subject
                                                  |
                                                  +-- token.destroy() vẫn có thể
                                                      xóa secret trong token
```

**Sai lầm phổ biến:** “Đã `setReadOnly` thì private credential an toàn tuyệt đối.” Sai: read-only không mã hóa secret, không giữ danh tính đáng tin tự động, không chặn việc gọi method thay đổi **bên trong object**, và không thay thế destroy/rotation/revocation.

### 5.1 Tại sao khóa `Subject` quá sớm dễ làm `LoginModule.logout()` khó xử lý?

Thông thường `LoginModule.commit()` thêm principal/credential do nó sở hữu; `logout()` **gỡ** các phần tử ấy và **hủy** secrets. Nếu application gọi `subject.setReadOnly()` ngay sau đăng nhập, các tập không thể bị sửa nữa. Hướng dẫn JAAS `LoginModule` của Oracle nêu mô hình: với subject read-only, module nên **hủy những credential do nó thêm nếu destroyable**, không thể remove chúng khỏi subject; nếu credentials không thể destroy, logout có thể phải báo `LoginException`.

Có nghĩa:

- Logout của **Subject mutable**: remove những principal/credential do module đã thêm, rồi destroy tài nguyên bí mật tương ứng.
- Logout của **Subject read-only**: không thể remove các entry đã gắn vào subject; module có thể destroy **nội dung credential**; principal/credential object vẫn tồn tại trong set dù credential đã bị hủy.
- `logout()` thành công trên một subject chỉ đọc **không bảo đảm** `subject.getPrincipals().isEmpty()` hoặc `getPrivateCredentials().isEmpty()`.
- Nếu một module tự viết cứ gọi `.remove()` trên subject read-only mà không kiểm tra trạng thái thì có thể bị `IllegalStateException`, cần xử lý failure thay vì âm thầm coi logout thành công.

### 5.2 Thiết kế an toàn hơn: subject sống và bản identity snapshot tách riêng

Nếu cần một representation danh tính **không cho caller tự sửa**, nhưng vẫn muốn JAAS logout có thể remove credentials, có thể:

1. **Giữ `Subject` thuộc `LoginContext` ở trạng thái mutable** trong vòng đời đăng nhập/log out theo ownership module.
2. Sau login thành công, tạo **một `Subject` khác chỉ sao chép principal bất biến đã kiểm chứng**, không sao chép private credentials.
3. Gọi `setReadOnly()` trên **snapshot chỉ chứa identity**, rồi đưa snapshot tới tầng đọc danh tính khi thực sự cần; không cho tầng đó quyền trực tiếp quản trị live Subject.
4. Khi hết phiên, gọi `LoginContext.logout()` trên **live Subject**, để module cleanup và destroy.

Điều này chỉ là một **pattern tùy ứng dụng**: một bản sao `Subject` không tự mang tất cả trạng thái/authorization JAAS, không tự cập nhật khi original thay đổi và không phải bằng chứng danh tính đã được cryptographically sealed. **Không** cho phép sử dụng snapshot để tiếp tục các phiên sau khi authorization thực đã hết hạn. Nên dùng principal objects **immutable**; read-only Subject không deep-freeze các objects bên trong.

Một chiến lược khác là đóng subject gốc khi đã chắc chắn module/provider có lifecycle hỗ trợ read-only logout và credentials đều destroyable; phải kiểm thử module cụ thể. Không lấy code mẫu `setReadOnly()` làm blanket best practice cho mọi JAAS provider.

## 6. Chu trình `LoginModule`: `login`, `commit`, `abort`, `logout`

JAAS có **hai pha** trong `LoginContext.login()`:

```text
Phase 1: LoginModule.login()
    - Đọc callback; xác minh credential
    - Trả true, false (ignore) hoặc ném LoginException
    - Không tự ý add Principal trước overall success

Overall success theo JAAS control flags?
    YES -> Phase 2: LoginModule.commit()
             - Đưa state đã xác thực vào Subject
    NO  -> Phase 2: LoginModule.abort()
             - Hủy trạng thái tạm, rollback nếu cần

Sau khi ứng dụng sử dụng phiên:
    LoginContext.logout() -> LoginModule.logout()
             - Dọn đúng Principal/credential module sở hữu
```

Các `LoginModule` trong cùng login context có thể chia sẻ **cùng object Subject** và `sharedState`; vì thế module A **không được** gọi `subject.getPrincipals().clear()` hay `getPrivateCredentials().clear()` để dọn phần của mình, vì có thể xóa cả dữ liệu module B hoặc dữ liệu đã có từ trước.

| Method | Khi dùng | Điều nên làm | Điều cần tránh |
| --- | --- | --- | --- |
| `initialize(...)` | JAAS dựng module | Nhận `Subject`, handler, shared state, options | Lưu bí mật lâu không cần thiết |
| `login()` | Pha xác thực | Lấy password qua callback, kiểm tra, ghi trạng thái **private của module**; `return true` khi thành công | Đưa raw password vào Subject/log; tự retry hàng loạt |
| `commit()` | Overall login thành công | Thêm **đúng principal/credential module sở hữu** vào Subject | Thêm khi module chưa thành công; nuốt lỗi commit |
| `abort()` | Overall login thất bại | Hủy state tạm, rollback các thay đổi sở hữu theo khả năng | Để lại session token hoặc principal từ login đã bị hủy |
| `logout()` | Application kết thúc phiên | Remove owned state (nếu mutable), destroy secret và gọi revoke của provider nếu cần | Xóa dữ liệu của module khác; giả định read-only subject có thể remove |

### 6.1 JAAS control flags ảnh hưởng overall success

Các `AppConfigurationEntry.LoginModuleControlFlag` thông dụng:

| Flag | Ý nghĩa chung |
| --- | --- |
| `REQUIRED` | Module **phải thành công** để overall success, nhưng các module tiếp theo vẫn thường được gọi |
| `REQUISITE` | Module **phải thành công**; thất bại có thể chấm dứt sớm quá trình auth |
| `SUFFICIENT` | Thành công có thể cho phép kết thúc sớm nếu không có `REQUIRED`/`REQUISITE` thất bại trước đó |
| `OPTIONAL` | Kết quả phụ thuộc tổ hợp các module và overall rules |

Không tự viết một `LoginContext` vòng lặp gọi `login()` rồi `commit()` bất kể các module khác thất bại. Dùng contract do JAAS quản lý, vì module có thể sinh tạm token trước khi được phép ghi vào subject.

## 7. `LoginException` và cách phân loại failure

`javax.security.auth.login.LoginException` là **checked exception** cho lỗi đăng nhập/đăng xuất trong JAAS. Các subclass như `FailedLoginException`, `AccountExpiredException`, `CredentialExpiredException` giúp phân loại lỗi khi provider thật hỗ trợ. Không phải lúc nào exception nhận ở application cũng là đúng subclass gốc: nhiều module và provider có thể bọc lỗi hoặc dùng `LoginException` chung.

### 7.1 Login failure không có nghĩa tiếp tục như guest đã xác thực

```java
// Mảnh code trong ứng dụng có LoginContext loginContext:
try {
    loginContext.login();
    javax.security.auth.Subject authenticated =
            loginContext.getSubject();
    // Chỉ sau thành công mới cấp quyền theo policy phù hợp.
} catch (javax.security.auth.login.LoginException ex) {
    // Ghi audit event không có password/token/secret.
    // Từ chối truy cập, hủy context tạm/cleanup theo API provider.
    throw new SecurityException("Authentication unsuccessful");
}
```

`login()` ném `LoginException` thì ứng dụng **không được** suy ra authentication thành công từ một `Subject` đang có tên hoặc từ việc callback trả password. `LoginContext.getSubject()` có thể trả `null` nếu overall login chưa thành công; khi truyền một `Subject` từ ngoài vào, vẫn cần kiểm tra success trước khi coi principal trong đó là authenticated.

### 7.2 Không để mất nguyên nhân kỹ thuật, nhưng cũng không tiết lộ bí mật

Trong module, khi `CallbackHandler.handle(...)` ném `IOException` hoặc `UnsupportedCallbackException`, có thể bọc vào `LoginException` và giữ cause:

```java
// Mảnh code trong catch (IOException | UnsupportedCallbackException cause):
javax.security.auth.login.LoginException ex =
        new javax.security.auth.login.LoginException("Callback failed");
ex.initCause(cause);
throw ex;
```

**Ứng dụng phía ngoài** có thể log mã lỗi và correlation ID, nhưng không nên in cause chứa raw password, token, callback content, full authentication headers hay các input secrets. Trả về client một thông điệp chung “Authentication unsuccessful”, không phân biệt quá chi tiết “user tồn tại nhưng sai password” nếu điều đó giúp dò tài khoản.

Phân loại đúng:

- `FailedLoginException`: thông tin chứng thực không khớp.
- `CredentialExpiredException` / `AccountExpiredException`: credential/tài khoản hết hạn theo chính sách provider.
- Lỗi callback, cấu hình, không kết nối identity backend: **infrastructure/authentication failure**.
- `LoginException` trong `commit`/`abort`/`logout`: lỗi lifecycle; cần cleanup hoặc phản hồi security event, không được nói rằng secret đã bị hủy khi chưa xác minh.

### 7.3 Ai chịu trách nhiệm retry?

Tài liệu JAAS `LoginModule` của Oracle hướng dẫn **module không tự retry hoặc sleep/delay trong `login()` khi xác thực thất bại**; chính application/gateway chịu trách nhiệm policy retry, rate limits, backoff, lockout và telemetry. Việc tự loop gọi callback nhiều lần trong module có thể làm sai state của một lần login, kéo dài password trong memory và tạo lỗ hổng brute force.

### 7.4 Khi `commit()` thất bại một phần

Nếu module đã thêm principal nhưng chưa thêm credential thì bị exception, phải rollback **phần do chính module vừa thêm**, hủy secret đã tạo và ném `LoginException`. Không tự `return true` rồi để subject mang identity mà credential chưa sẵn sàng.

Ngược lại, nếu `abort()`/`logout()` cần cleanup nhưng credential provider đã mất kết nối, module phải xử lý theo documented contract và báo lỗi phù hợp. Đối với một token còn hợp lệ ở server, local `Destroyable.destroy()` không thay thế remote revocation; cần đánh dấu phiên không còn được chấp nhận ở tầng application và theo dõi reconciliation.

## 8. Chương trình Java 17+ hoàn chỉnh: `Subject`, credential và `LoginException`

Chương trình sau độc lập, không cần cấu hình JAAS ngoài. Nó cung cấp một `Configuration` **in-memory** chọn `ExampleModule` với control flag `REQUIRED`, dùng `OneUseHandler` để đưa password vào `PasswordCallback`, và sinh một `SessionCredential` ngẫu nhiên sau khi xác thực mẫu.

Chương trình kiểm chứng các tình huống:

1. Đăng nhập với demo user `alice`/password tạm `demo`, ghi một principal và một **demo session secret** vào `Subject`.
2. Tạo **snapshot chỉ gồm principal** rồi `setReadOnly()`, thử sửa snapshot và quan sát `IllegalStateException`; snapshot **không** chứa private credentials.
3. `logout()` trên **live Subject mutable** gỡ principal/credential và zero hóa token.
4. Trường hợp khác: đánh dấu **live Subject read-only** trước `logout`; module chỉ có thể destroy credential, không gỡ được principal/credential khỏi các tập.
5. Đăng nhập sai `bad` bị từ chối bằng `LoginException`; không commit principal/credential.
6. Không in password, token bytes hoặc nội dung private credential.

> **Lưu ý an ninh:** Module dùng password `demo` cứng trong mã và `Arrays.equals` **chỉ để minh họa API/chu trình**. **Không dùng** cách này trong production: không hardcode password, không so sánh password plaintext như cơ chế auth thật, không dùng random bytes demo như session token có quyền truy cập. Ứng dụng thật cần provider xác thực đã kiểm chứng, salted password hashing/identity service, MFA nếu cần, secret rotation, KMS/HSM/keystore, rate limit và chính sách phiên.

Tạo file **`JaasCredentialLifecycleDemo.java`**:

```java
import java.io.IOException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Destroyable;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

public final class JaasCredentialLifecycleDemo {
    public record UserId(String value) implements Principal {
        @Override public String getName() { return value; }
    }

    public static final class SessionCredential implements Destroyable {
        private final byte[] material = new byte[16];
        private boolean destroyed;

        public SessionCredential() {
            new SecureRandom().nextBytes(material);
        }

        @Override public synchronized void destroy() {
            Arrays.fill(material, (byte) 0);
            destroyed = true;
        }

        @Override public synchronized boolean isDestroyed() {
            return destroyed;
        }

        @Override public String toString() {
            return "SessionCredential[redacted]";
        }
    }

    public static final class OneUseHandler implements CallbackHandler {
        private final String user;
        private final char[] password;

        public OneUseHandler(String user, char[] password) {
            this.user = user;
            this.password = password; // takes ownership of caller array
        }

        @Override public void handle(Callback[] callbacks)
                throws IOException, UnsupportedCallbackException {
            try {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback name) {
                        name.setName(user);
                    } else if (callback instanceof PasswordCallback pwd) {
                        pwd.setPassword(password); // PasswordCallback copies it
                    } else {
                        throw new UnsupportedCallbackException(callback);
                    }
                }
            } finally {
                Arrays.fill(password, '\0');
            }
        }
    }

    public static final class ExampleModule implements LoginModule {
        private Subject subject;
        private CallbackHandler handler;
        private boolean authenticated;
        private boolean committed;
        private UserId principal;
        private SessionCredential credential;

        @Override public void initialize(Subject subject,
                CallbackHandler handler, Map<String, ?> sharedState,
                Map<String, ?> options) {
            this.subject = subject;
            this.handler = handler;
        }

        @Override public boolean login() throws LoginException {
            NameCallback name = new NameCallback("User");
            PasswordCallback pwd = new PasswordCallback("Password", false);
            try {
                handler.handle(new Callback[] {name, pwd});
            } catch (IOException | UnsupportedCallbackException e) {
                LoginException failure = new LoginException("Callback failed");
                failure.initCause(e);
                throw failure;
            }
            char[] actual = pwd.getPassword();
            pwd.clearPassword();
            char[] expected = {'d','e','m','o'}; // demonstration ONLY
            try {
                if (!"alice".equals(name.getName())
                        || actual == null
                        || !Arrays.equals(actual, expected)) {
                    throw new FailedLoginException("Invalid credentials");
                }
                principal = new UserId(name.getName());
                credential = new SessionCredential();
                authenticated = true;
                return true;
            } finally {
                if (actual != null) Arrays.fill(actual, '\0');
                Arrays.fill(expected, '\0');
            }
        }

        @Override public boolean commit() throws LoginException {
            if (!authenticated) return false;
            try {
                subject.getPrincipals().add(principal);
                subject.getPrivateCredentials().add(credential);
                committed = true;
                return true;
            } catch (RuntimeException e) {
                try {
                    if (!subject.isReadOnly()) {
                        subject.getPrincipals().remove(principal);
                        subject.getPrivateCredentials().remove(credential);
                    }
                } finally {
                    credential.destroy();
                }
                LoginException failure = new LoginException("Commit failed");
                failure.initCause(e);
                throw failure;
            }
        }

        private void cleanup() {
            if (committed && !subject.isReadOnly()) {
                subject.getPrincipals().remove(principal);
                subject.getPrivateCredentials().remove(credential);
            }
            if (credential != null) credential.destroy();
            credential = null;
            principal = null;
            authenticated = false;
            committed = false;
        }

        @Override public boolean abort() {
            if (!authenticated && !committed) return false;
            cleanup();
            return true;
        }

        @Override public boolean logout() {
            cleanup();
            return true;
        }
    }

    private static Configuration configuration() {
        return new Configuration() {
            @Override public AppConfigurationEntry[] getAppConfigurationEntry(
                    String name) {
                if (!"Example".equals(name)) return null;
                return new AppConfigurationEntry[] {
                    new AppConfigurationEntry(
                            ExampleModule.class.getName(),
                            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                            Map.of())
                };
            }
        };
    }

    private static LoginContext context(Subject subject, char[] password)
            throws LoginException {
        return new LoginContext("Example", subject,
                new OneUseHandler("alice", password), configuration());
    }

    public static void main(String[] args) throws Exception {
        Subject live = new Subject();
        LoginContext ok = context(live, new char[] {'d','e','m','o'});
        ok.login();
        SessionCredential token = live.getPrivateCredentials(
                SessionCredential.class).iterator().next();
        System.out.println("Login principal count: " +
                live.getPrincipals().size());
        System.out.println("Private credential count: " +
                live.getPrivateCredentials().size());

        Subject snapshot = new Subject(false,
                new HashSet<>(live.getPrincipals()), Set.of(), Set.of());
        snapshot.setReadOnly();
        boolean modificationBlocked = false;
        try {
            snapshot.getPrincipals().add(new UserId("mallory"));
        } catch (IllegalStateException expected) {
            modificationBlocked = true;
        }
        System.out.println("Identity snapshot read-only: " +
                snapshot.isReadOnly());
        System.out.println("Snapshot mutation rejected: " +
                modificationBlocked);
        System.out.println("Snapshot has private credentials: " +
                !snapshot.getPrivateCredentials().isEmpty());

        ok.logout();
        System.out.println("Mutable Subject cleared at logout: " +
                (live.getPrincipals().isEmpty()
                        && live.getPrivateCredentials().isEmpty()));
        System.out.println("Session credential destroyed: " +
                token.isDestroyed());

        Subject frozen = new Subject();
        LoginContext frozenContext = context(
                frozen, new char[] {'d','e','m','o'});
        frozenContext.login();
        SessionCredential frozenToken = frozen.getPrivateCredentials(
                SessionCredential.class).iterator().next();
        frozen.setReadOnly();
        frozenContext.logout();
        System.out.println("Frozen Subject still holds identity: " +
                !frozen.getPrincipals().isEmpty());
        System.out.println("Frozen credential destroyed: " +
                frozenToken.isDestroyed());

        Subject rejected = new Subject();
        LoginContext wrong = context(rejected, new char[] {'b','a','d'});
        boolean failed = false;
        try {
            wrong.login();
        } catch (LoginException expected) {
            failed = true;
        }
        System.out.println("Wrong password rejected: " + failed);
        System.out.println("Failed login Subject empty: " +
                (rejected.getPrincipals().isEmpty()
                        && rejected.getPrivateCredentials().isEmpty()));
    }
}
```

Biên dịch với **Java 17+** (không dùng preview features; ví dụ có `record` và pattern matching `instanceof` đã là tính năng chuẩn):

```bash
javac --release 17 JaasCredentialLifecycleDemo.java
java JaasCredentialLifecycleDemo
```

**Stdout được kiểm chứng trên JDK hỗ trợ Java 17 syntax/API:**

```text
Login principal count: 1
Private credential count: 1
Identity snapshot read-only: true
Snapshot mutation rejected: true
Snapshot has private credentials: false
Mutable Subject cleared at logout: true
Session credential destroyed: true
Frozen Subject still holds identity: true
Frozen credential destroyed: true
Wrong password rejected: true
Failed login Subject empty: true
```

### 8.1 Giải thích từng phần và kết quả

**`UserId implements Principal`:** `record` giữ identity string ở dạng immutable. `getName()` trả tên để các JAAS API làm việc được. Chỉ module thêm `UserId` **sau khi xác thực thành công**; không được coi một object `UserId` tự tạo từ request là bằng chứng của authentication.

**`SessionCredential implements Destroyable`:** chứa 16 byte giả lập, tạo mới với `SecureRandom`. Trong demo nó **không phải access token production**, không được bên nào sử dụng để cấp quyền. `destroy()` zero hóa buffer và đánh dấu destroyed, `isDestroyed()` cho phép kiểm chứng; `toString()` cố ý chỉ trả `[redacted]`. Bảo vệ bộ nhớ không tuyệt đối, và zero hóa không hủy các bản sao ngoài object nếu đã tồn tại.

**`OneUseHandler`:** nhận ownership của mảng password do caller truyền, đưa password vào `PasswordCallback` rồi xóa mảng gốc ở `finally`. `PasswordCallback` tự có bản sao, được module xóa bằng `clearPassword()`. Module cũng xóa mảng `actual` mà `getPassword()` trả về. Handler được thiết kế **dùng một lần**; đừng dùng nó để retry nhiều login với password đã được xóa.

**`ExampleModule.login()`:** kiểm tra `name` và `password` giả lập, throw `FailedLoginException` khi sai; không add Principal lúc này. Password expected từ char array trong bài là ví dụ **không an toàn cho production**, chỉ minh họa việc phải hủy các mảng tạm. Với provider thật, việc kiểm tra có thể được thực hiện bởi identity service, Kerberos/KDC, PAM, LDAP qua TLS, salted password verifier hoặc thiết bị bảo mật.

**`commit()`:** chỉ khi `authenticated == true` mới add `UserId` và `SessionCredential` vào Subject. Nếu một bước add ném runtime exception thì rollback phần do module vừa thử thêm, destroy token và bọc failure dưới `LoginException`. Demo không có multi-thread, nên việc kiểm tra `subject.isReadOnly()` rồi thao tác tập được minh họa trong một luồng; hệ thống thực cần quản lý ownership/concurrency và provider-specific cleanup.

**`snapshot.setReadOnly()`:** snapshot chỉ có principal đã xác thực (được sao chép qua constructor `Subject(false, ...)`). Thêm principal mới vào snapshot ném `IllegalStateException`. Snapshot không chứa private credentials, nên không làm rò rỉ demo token. Nó không phải tài khoản đăng nhập mới và không tự sống mãi sau phiên gốc.

**`ok.logout()` trên live Subject mutable:** module remove đúng principal/token đã add rồi `token.destroy()`. Kết quả `Mutable Subject cleared at logout: true` và `Session credential destroyed: true` xác nhận **gỡ tham chiếu khỏi subject** cùng **hủy material**.

**`frozen.setReadOnly()` trước logout:** Subject đã khóa cấu trúc, module **không thể remove** phần tử. Module demo dùng `isReadOnly()` để chỉ destroy nội dung token; vì vậy `Frozen Subject still holds identity: true` và `Frozen credential destroyed: true` **không mâu thuẫn**. Tình huống này cho thấy vì sao đóng live Subject quá sớm có thể khiến identity cũ tồn tại sau logout và cần quản lý trust/lifetime ở tầng khác.

**Sai password:** module throw `FailedLoginException`; `LoginContext.login()` báo failure, không có `commit` thành công. `Subject rejected` vẫn trống. Ứng dụng chỉ biết **đăng nhập bị từ chối**, không nên trao quyền hay hiển thị chi tiết có thể giúp dò username.

### 8.2 Không suy rộng demo thành production JAAS module

Mã trên cố ý đơn giản: một module, một user giả, một luồng, `Configuration` in-memory và secret không được sử dụng cho giao dịch. Nó **không** gồm distributed revocation, bảo vệ cache/ticket, multi-module shared state, logout race giữa threads, account locking, rate limiting, secure password store, remote backend, audit logging, authorization hoặc credentials do provider quản lý. Một triển khai `LoginModule` production còn cần thiết kế exception cleanup ngay cả khi revoke thất bại hoặc subject bị thay đổi bởi các module khác.

Đặc biệt, không được copy `Arrays.equals(actual, expected)` để thay cho cơ chế kiểm tra password production; **không hardcode password** hoặc chọn scheme hash yếu.

## 9. So sánh hai chiến lược “Subject chỉ đọc” lúc logout

| Vấn đề | Khóa live `Subject` sau login | Giữ live mutable, tạo snapshot read-only |
| --- | --- | --- |
| Người dùng code bên ngoài có sửa principals trên live object? | Không thể sửa các tập sau `setReadOnly()` | Chỉ snapshot bị khóa; cần tránh lộ tham chiếu live Subject |
| Logout có remove được principal/credential? | **Không**: Subject đã khóa | Có, nếu module đúng và không có ràng buộc khác |
| Credential `Destroyable` có bị hủy được? | Có thể, theo contract và implementation module | Có thể, module remove và destroy |
| Trạng thái principals còn trong Subject sau logout? | Có thể còn | Có thể trống nếu dọn đủ |
| Snapshot có credential riêng? | Không áp dụng | Tùy thiết kế: nên **chỉ sao chép identity cần thiết**, tránh secrets |
| Dễ dùng nhầm identity cũ sau logout? | Rủi ro cao nếu vẫn giữ tham chiếu live read-only Subject | Vẫn cần invalidate snapshot/token ở application |
| Phù hợp khi nào? | Chỉ khi module/provider được thiết kế cho read-only cleanup | Khi muốn tách trách nhiệm auth lifecycle và identity view |

**Nguyên tắc bảo mật:** Không dựa vào `Subject.isReadOnly()` làm bằng chứng người dùng **đang đăng nhập**. Một `Subject` có thể read-only dù chưa từng xác thực, và có thể read-only mà chứa principal cũ sau logout. Phải dựa vào **authentication result, thời hạn phiên và policy**.

## 10. Lỗi thực tế và cách khắc phục

| Lỗi/triệu chứng | Nguyên nhân | Hướng khắc phục |
| --- | --- | --- |
| Login thành công nhưng Subject không có Principal | Module không `commit`, commit trả sai, hoặc flags fail | Dùng đúng flow `LoginContext` và kiểm tra `commit` |
| `FailedLoginException` | Username/password/credential không khớp | Từ chối, áp policy retry, không tiết lộ chi tiết |
| `CredentialExpiredException` | Credential hết hạn theo provider | Yêu cầu re-auth/renew có kiểm soát |
| `AccountExpiredException` | Account hết hạn theo policy | Từ chối, không tự bỏ qua expiration |
| `LoginException` khi `CallbackHandler` chạy | Callback không hỗ trợ, I/O failure | Bọc cause an toàn; không log secret |
| `LoginException` trong `commit` | Read-only Subject, permission denial, lỗi add state | Rollback owned changes, destroy secret, fail closed |
| `IllegalStateException` khi `getPrincipals().add()` | `Subject.setReadOnly()` đã được gọi | Không chỉnh sửa read-only Subject; thiết kế snapshot hoặc lifecycle khác |
| `logout()` xong nhưng Principal còn | Live Subject đã read-only, không gỡ entry được | Đừng dùng Subject ấy làm session active; invalidate authorization/session |
| `logout()` xong nhưng secret còn | `remove` không đồng nghĩa zero hóa; provider không destroy/revoke | Hủy `Destroyable`, remote revoke và audit |
| Credential đã `destroy` vẫn trong `getPrivateCredentials()` | Destroy không tự remove, hoặc subject read-only | Dùng module cleanup tương thích, không coi presence là active |
| Bị `SecurityException` khi đọc private credentials | Runtime có SecurityManager/credential permissions | Kiểm tra security policy, không tắt kiểm soát truy cập |
| Password vô tình xuất hiện trong logs | Logger in callback hoặc `Subject`/credential `toString` | Redact, chỉ log status/correlation ID |
| `getPrivateCredentials(MyType.class).clear()` không làm gì với subject | Typed accessor trả copy, không backed set | Module phải remove từ raw backed set khi mutable và được phép |
| Tài khoản vẫn có quyền sau logout | Cache/token external chưa bị revoke | Kiểm tra session registry, TTL, invalidation, audit |
| User mượn `Subject` rồi tự thêm role | Live Subject writable bị leak | Encapsulation, immutable principal snapshot, auth boundaries |
| `LoginModule.abort()` không dọn token tạm | Cleanup thiếu khi overall login thất bại | Dọn state của module trong abort và `finally` |

**Lưu ý đặc biệt:** `Subject.setReadOnly()` khóa *các tập* chứ không deep-freeze `Principal`/credential, nên một credential do application tự viết có setter vẫn có thể đổi nội dung sau khi subject read-only. Dùng immutable principal, credential có lifecycle rõ ràng và không cung cấp setter tùy tiện cho secret.

## 11. Checklist review code JAAS credential và logout

- [ ] `LoginContext` được dựng với đúng tên cấu hình, handler và `Configuration`/flags do hệ thống quản lý.
- [ ] `LoginModule.login()` chỉ xác thực và giữ state tối thiểu, không commit Principal trước overall success.
- [ ] Raw password không ghi vào `String`, logs, Subject hoặc exception messages.
- [ ] `PasswordCallback.clearPassword()` được gọi; bản sao từ `getPassword()` và từ handler được xóa trong `finally`.
- [ ] Module không tự loop retry khi thất bại; application áp dụng rate limit/backoff/lockout.
- [ ] `commit()` thêm **chính xác owned Principal/credential**; xử lý partial commit failure an toàn.
- [ ] `abort()` dọn state tạm nếu overall authentication fail; `logout()` dọn state module sở hữu.
- [ ] Private credentials có scope/expiry và `Destroyable`/provider cleanup phù hợp; remove và destroy không được coi là một thao tác.
- [ ] Không hiển thị raw `Subject`/credential trên logger; `toString` của credential không tiết lộ material.
- [ ] Nếu Subject read-only, biết `getPrincipals`/credential sets không remove được, nhưng credential destroy vẫn có thể.
- [ ] Không đặt `setReadOnly()` lên live Subject trước khi biết module có hỗ trợ logout read-only.
- [ ] Snapshot identity, nếu dùng, **không** chứa private credentials; principal objects immutable.
- [ ] `LoginException` dẫn tới fail closed, không coi Subject nonempty là authenticated.
- [ ] Audit logs tách user-safe error khỏi diagnostic nội bộ, không lộ account existence/cause có secret.
- [ ] Có integration tests cho success, wrong password, callback failure, read-only mutation, logout cleanup, abort, credential destroy, expired/revoked token và race conditions.

## 12. Bài tập thực hành

**Bài 1 — Phân biệt remove và destroy.**  
Sửa tạm code `ExampleModule.cleanup()` chỉ remove token mà không gọi `destroy()`, rồi quan sát object `SessionCredential` bạn còn giữ ngoài subject. Vì sao `isDestroyed()` vẫn `false`? Sửa lại để cleanup thực sự xóa secret.

**Bài 2 — Read-only trước commit.**  
Thử `live.setReadOnly()` trước `LoginContext.login()`, quan sát commit không thể thêm principal/token. Trình bày vì sao **chỉ callback password đúng không đồng nghĩa login thành công** khi commit thất bại. Không để module `return true` sau exception.

**Bài 3 — Snapshot không giữ secret.**  
Sau login, tạo snapshot chỉ gồm principal. Thử đổi set của snapshot và kiểm tra `IllegalStateException`; kiểm tra `snapshot.getPrivateCredentials().isEmpty()` vẫn đúng. Sau logout live Subject, cân nhắc vì sao snapshot cũ không được xem là active session.

**Bài 4 — Logout Subject đã đóng.**  
Gọi `frozen.setReadOnly()` trước logout. Vì sao module không thể `.remove()` nhưng vẫn có thể `destroy()`? Thử một credential không hỗ trợ `Destroyable` và phân tích failure policy; đừng tuyên bố nó chắc chắn được hủy.

**Bài 5 — Callback failure.**  
Cho `CallbackHandler` ném `UnsupportedCallbackException` khi gặp `PasswordCallback`. Module nên trả `false`, `true` hay ném `LoginException`? Vì sao phải dọn state trong `abort` khi overall login fail?

**Bài 6 — Chính sách timeout và revocation.**  
Với credential dạng access token thật, mô tả tại sao zero hóa mảng trong JVM chưa hủy quyền truy cập của token ở server. Bổ sung TTL, revoke event và invalidation của session registry.

**Bài 7 — Multi-module ownership.**  
Tưởng tượng hai modules cùng thêm principals vào một Subject. Vì sao `subject.getPrincipals().clear()` trong logout của module A là sai? Sửa để A chỉ gỡ principal/credential do chính A thêm.

**Bài 8 — Ranh giới authorization.**  
Một request gửi `"user": "admin"`; application tự tạo `new UserId("admin")` và đưa vào Subject. Vì sao `getPrincipals` chứa admin **không** đủ để cấp quyền admin? Hãy nêu cách identity phải đến từ login success và policy server.

## 13. Câu hỏi tự kiểm tra

**1. `Subject.getPrivateCredentials()` có phải một kho token đã tự mã hóa không?**  
Không. Nó là một tập object có cơ chế quyền truy cập theo JAAS/runtime, **không bảo đảm encryption, zeroization hoặc remote revocation**.

**2. `PasswordCallback.clearPassword()` đã xóa mọi bản sao password chưa?**  
Chưa. Nó xóa bản lưu của callback; mảng nguồn ở handler và bản sao trả về từ `getPassword()` phải được xử lý riêng, tốt nhất trong `finally`.

**3. `Subject.setReadOnly()` chặn thao tác nào?**  
Chặn thêm/xóa phần tử trong principal/public/private credential sets. Nó **không** deep-freeze object, không ngăn destroy credential, không chứng minh user đã xác thực.

**4. Vì sao `logout()` có thể thành công nhưng read-only Subject vẫn giữ principal?**  
Vì các tập không thể bị sửa sau `setReadOnly`; module có thể chỉ hủy secret trong credential. **Presence của principal sau logout không xác nhận session còn hợp lệ.**

**5. Khi `LoginModule.login()` thành công thì có thể add principal luôn không?**  
Thông thường phải chờ **overall login success** và `commit()`. Nếu một module khác fail, `abort()` được gọi và state tạm phải được dọn.

**6. `LoginException` chỉ báo sai password?**  
Không. Nó cũng đại diện lỗi callback, cấu hình, provider hoặc lifecycle; `FailedLoginException` là một trường hợp thường dùng cho credentials không hợp lệ.

**7. Tại sao typed `getPrivateCredentials(MyType.class)` không phải nơi gỡ credentials khỏi subject?**  
Vì accessor đã lọc trả một **tập mới**; thay đổi tập mới không sửa tập backed bởi Subject.

**8. Khi nào dùng snapshot `Subject` chỉ có principals?**  
Khi muốn cung cấp danh tính đã kiểm tra ở dạng **không sửa cấu trúc** cho code bên ngoài, mà vẫn cho live Subject của `LoginContext` có thể logout/cleanup theo lifecycle. Snapshot không tự duy trì thời hạn phiên.

**9. `Destroyable.destroy()` có thể thay thế remote revoke không?**  
Không. Nó chỉ là lifecycle của local credential object; quyền ở server/IdP cần cơ chế revoke/expiry riêng.

**10. Ứng dụng có thể tiếp tục xử lý request sau khi `login()` ném `LoginException` không?**  
Chỉ có thể tiếp tục theo nhánh **unauthenticated** phù hợp, không được cấp quyền dựa vào trạng thái login không được xác nhận.

## 14. Tài liệu chính thức

- [Java SE 17 — `Subject`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/Subject.html): principal/public/private credentials, `setReadOnly`, `isReadOnly`, typed credentials accessor và hành vi destroy khi read-only.
- [Java SE 17 — `LoginContext`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/LoginContext.html): `login`, `getSubject`, `logout`, constructors và configuration.
- [Java SE 17 — `LoginModule`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/spi/LoginModule.html): `initialize`, `login`, `commit`, `abort`, `logout`.
- [Java SE 17 — `LoginException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/LoginException.html): checked authentication/login exception.
- [Java SE 17 — `FailedLoginException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/FailedLoginException.html): xác thực thất bại.
- [Java SE 17 — `PasswordCallback`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/callback/PasswordCallback.html): `setPassword`, `getPassword`, `clearPassword`.
- [Java SE 17 — `Destroyable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/Destroyable.html): destroyable secrets/credential lifecycle.
- [Java SE 17 — `Configuration`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/login/Configuration.html): cấu hình JAAS modules.
- [Oracle Java SE 17 — JAAS Authentication](https://docs.oracle.com/en/java/javase/17/security/jaas-authentication.html): kiến trúc Subject, LoginContext, CallbackHandler và JAAS config.
- [Oracle Java SE 17 — JAAS LoginModule Developer's Guide](https://docs.oracle.com/en/java/javase/17/security/java-authentication-and-authorization-service-jaas-loginmodule-developers-guide1.html): two-phase login, `commit/abort/logout`, ownership và credential destroy khi Subject read-only.

**Tóm tắt:** `Subject` giữ **identity và credentials** nhưng không tự xác thực/giữ bí mật cho ứng dụng. `LoginModule` phải quản lý `login → commit/abort → logout`, xóa password tạm và hủy credential có thể hủy. **`setReadOnly()` chỉ khóa cấu trúc các tập, không khóa hay thu hồi quyền của secret và không thể thay thế `logout`.** Khi `LoginException` xuất hiện, phải **từ chối quyền**, dọn trạng thái thuộc module và giữ logs sạch bí mật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jaas-authentication-009">17.9 Ranh giới giữa cấu hình JAAS với mô hình security provider của JCA</a>

<details>
<summary>Xem chi tiết</summary>

# 17.9 Ranh giới giữa cấu hình JAAS với mô hình security provider của JCA

## Mục tiêu học tập

Sau bài này, bạn có thể chỉ ra chính xác thành phần nào quyết định **cách xác thực một chủ thể** và thành phần nào cung cấp **hiện thực thuật toán/dịch vụ bảo mật** trong Java. Bạn cũng có thể giải thích vì sao hai cơ chế đều được gọi là “provider” nhưng không dùng chung một danh sách đăng ký, nhận ra điểm giao đặc biệt ở `Configuration.getInstance(...)` và chẩn đoán lỗi ở đúng lớp.

**Ý chính:** Cấu hình JAAS chọn và phối hợp các `LoginModule` cho một ứng dụng đăng nhập. Mô hình `java.security.Provider` của JCA chọn hiện thực của một dịch vụ theo loại dịch vụ và tên thuật toán. Một `LoginModule` có thể *sử dụng* dịch vụ JCA, nhưng việc đăng ký provider JCA không tự đăng ký hay thay thế `LoginModule`.

## 1. Hai trục mở rộng khác nhau

| Câu hỏi | JAAS (Java Authentication and Authorization Service) | JCA (Java Cryptography Architecture / Java Security provider) |
|---|---|---|
| Đối tượng cần giải quyết | Ai đang đăng nhập, dùng cơ chế xác thực nào, kết hợp các bước xác thực ra sao? | Ai cung cấp hiện thực cho `MessageDigest`, `Signature`, `Cipher`, `KeyStore`, `Mac`, `SecureRandom`…? |
| API khởi đầu thường gặp | `new LoginContext("AppName")` rồi `login()` | `MessageDigest.getInstance("SHA-256")`, `Signature.getInstance("SHA256withRSA")`… |
| Nơi chọn hiện thực | `javax.security.auth.login.Configuration` ánh xạ tên ứng dụng sang danh sách lớp `LoginModule` cùng control flag/options | Danh sách `java.security.Provider` đăng ký các `Provider.Service` theo loại dịch vụ và thuật toán |
| Đơn vị được “cắm” | Lớp triển khai `javax.security.auth.spi.LoginModule` | Một provider đăng ký các hiện thực SPI như `MessageDigestSpi`, `SignatureSpi`, `CipherSpi` |
| Kết quả điển hình | Trạng thái đăng nhập và `Subject` chứa `Principal`/credentials sau khi xác thực thành công | Một engine object có hiện thực thuật toán được chọn, ví dụ `MessageDigest` hoặc `Signature` |
| Quy tắc kết hợp | Thứ tự module và `required`, `requisite`, `sufficient`, `optional` | Thứ tự ưu tiên provider, dịch vụ/thuật toán được hỗ trợ, provider chỉ định rõ nếu có |
| Khi thay đổi cấu hình | Đổi phương pháp hoặc chuỗi xác thực cho entry đăng nhập | Đổi hiện thực cryptography/security service được gọi bởi API JCA |

Vì vậy, “thay đổi nơi kiểm tra danh tính” và “thay đổi thư viện thực hiện chữ ký/giải mã” là hai thao tác khác nhau. Chúng có thể phối hợp trong cùng một luồng xác thực, nhưng không thay thế nhau.

## 2. Cấu hình JAAS điều khiển `LoginContext` như thế nào?

Một cấu hình JAAS thường có dạng sau (ví dụ minh họa cho môi trường **đã có Kerberos**, không tự chạy nếu máy chưa được cấu hình Kerberos):

```text
BackendLogin {
    com.sun.security.auth.module.Krb5LoginModule required
        useTicketCache="true";
};
```

- `BackendLogin` là **tên entry JAAS**. Lời gọi `new LoginContext("BackendLogin")` tra cứu chính tên này, không tra cứu tên provider JCA hay tên thuật toán.
- `com.sun.security.auth.module.Krb5LoginModule` là **tên lớp LoginModule**; cơ chế của nó là xác thực Kerberos. Một module khác có thể kiểm tra tài khoản, token, chứng chỉ hoặc tích hợp hệ thống bên ngoài.
- `required` là **control flag** cho toàn bộ tiến trình đăng nhập; `useTicketCache` là option do *chính* `Krb5LoginModule` hiểu. JAAS không suy luận option này thành một dịch vụ JCA.
- `LoginContext` đọc `Configuration`, tạo các `LoginModule` được khai báo, gọi `initialize` và điều phối các bước. Nếu entry được yêu cầu không tồn tại, nó có thể thử entry mặc định `other`; nếu cả hai thiếu, việc tạo `LoginContext` thất bại.

Các control flag có ý nghĩa bảo mật rõ ràng:

| Flag | Nếu module thất bại | Nếu module thành công |
|---|---|---|
| `required` | Tiếp tục chạy các module sau nhưng toàn bộ lần đăng nhập cuối cùng vẫn thất bại | Tiếp tục chạy module sau; thành công của module này là điều kiện bắt buộc |
| `requisite` | Kết thúc xử lý đăng nhập ngay với thất bại | Tiếp tục chạy module sau; cũng là điều kiện bắt buộc |
| `sufficient` | Thường tiếp tục chạy module sau | Có thể kết thúc sớm với thành công nếu chưa có `required`/`requisite` trước đó thất bại |
| `optional` | Thường tiếp tục chạy module sau | Thường tiếp tục chạy module sau; chỉ góp phần vào kết quả chung |

Nếu không có module `required` hay `requisite` thì ít nhất một module `sufficient` hoặc `optional` phải thành công. Đặt sai flag hay thứ tự có thể tạo luồng xác thực yếu hơn dự định. Một `sufficient` thành công **không** xóa lỗi của `required` đã chạy trước đó.

Luồng xử lý điển hình:

```text
Ứng dụng
  -> LoginContext("BackendLogin")
  -> Configuration.getAppConfigurationEntry("BackendLogin")
  -> các LoginModule theo thứ tự và control flag
  -> login() từng module (kiểm chứng dữ liệu đầu vào)
  -> nếu toàn bộ đăng nhập đạt: commit() các module
     nếu thất bại: abort() và thu dọn trạng thái
  -> Subject với Principal/credentials sau commit thành công
  -> logout() khi kết thúc phiên xử lý
```

`CallbackHandler` cho phép module yêu cầu dữ liệu đầu vào qua `NameCallback`, `PasswordCallback`… thay vì phụ thuộc giao diện nhập liệu cụ thể. `Subject` là nơi tập hợp danh tính/credential sau xác thực; một `Principal` có thể là tên người dùng hoặc danh tính khác. **Có `Subject` không đồng nghĩa mọi API sẽ tự kiểm tra quyền:** ứng dụng/hệ thống còn phải thực thi quy tắc authorization phù hợp.

Quan trọng về ranh giới: dù `LoginModule` là một SPI trong package `javax.security.auth.spi`, nó **không phải** một `java.security.Provider`. Việc thêm `LoginModule` vào file JAAS không đưa module đó vào `Security.getProviders()`.

## 3. JCA chọn `Provider` như thế nào?

JCA tách API mà ứng dụng dùng khỏi hiện thực của các dịch vụ bảo mật. Với ví dụ bên dưới, ứng dụng yêu cầu **loại dịch vụ `MessageDigest` + thuật toán `SHA-256`**, không yêu cầu đăng nhập một người dùng:

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
System.out.println(digest.getAlgorithm());
System.out.println(digest.getProvider().getName());
```

Đây là **đoạn mã đặt trong một method**; cần import `java.security.MessageDigest`. Tên provider ở dòng thứ hai phụ thuộc JDK và cấu hình runtime, vì vậy không nên giả định luôn là một tên cố định.

Cơ chế lựa chọn:

1. `Security.getProviders()` trả về các `Provider` đã đăng ký và thứ tự ưu tiên của chúng.
2. Mỗi provider khai báo các `Provider.Service`, ví dụ dịch vụ `MessageDigest` hỗ trợ `SHA-256` hay dịch vụ `Signature` hỗ trợ `SHA256withRSA`.
3. `getInstance("thuật-toán")` không chỉ định provider sẽ tìm một hiện thực phù hợp theo cơ chế ưu tiên của runtime; JDK có thể áp dụng ưu tiên riêng theo thuật toán qua `jdk.security.provider.preferred`.
4. `getInstance("thuật-toán", "ProviderName")` yêu cầu provider cụ thể; provider hoặc dịch vụ không tồn tại sẽ dẫn tới exception tương ứng.
5. Engine API gọi xuống SPI của hiện thực đã chọn, ví dụ `MessageDigestSpi`. Provider có thể triển khai bằng Java, thư viện native hay phần cứng bảo mật.

Có thể cấu hình provider từ file security của JDK bằng `security.provider.N` hoặc quản lý tại runtime bằng `Security.addProvider(...)` / `Security.insertProviderAt(...)`. Đây là thay đổi **danh sách dịch vụ JCA trong JVM**, không phải chỉnh chuỗi `LoginModule` của một entry JAAS. Đừng thay đổi thứ tự provider toàn cục một cách tùy tiện: mọi thành phần dùng JCA trong JVM đều có thể bị ảnh hưởng.

JCA/security provider không chỉ có primitive mật mã. Cơ chế provider còn hỗ trợ một số service bảo mật khác, trong đó có `Configuration` của JAAS (xem phần 4). Đây là nguồn gốc của sự nhầm lẫn tên gọi.

## 4. Điểm giao tinh tế: provider của `Configuration` không phải provider của `LoginModule`

Từ “provider” có ít nhất ba ngữ cảnh cần tách bạch:

| Cách gọi | Thành phần thực sự | Được chọn bằng gì? |
|---|---|---|
| Authentication provider trong tài liệu JAAS | Một lớp `LoginModule` thực hiện công nghệ xác thực | Entry `Configuration`, tên lớp, thứ tự và flag |
| Login configuration provider | Một hiện thực `javax.security.auth.login.Configuration` cung cấp danh sách `LoginModule` | `Configuration.getConfiguration()`/`setConfiguration(...)` hoặc một factory `getInstance(...)` |
| JCA security provider | `java.security.Provider` đăng ký các dịch vụ/thuật toán bằng SPI | Danh sách security providers và factory `getInstance(...)` của service |

Hai con đường tạo `Configuration` rất dễ bị lẫn:

**Đường 1 — cấu hình JAAS mặc định/được cài đặt cho JVM.** `Configuration.getConfiguration()` lấy đối tượng `Configuration` đã được cài bằng `Configuration.setConfiguration(...)`, hoặc khởi tạo hiện thực mặc định. Security property `login.configuration.provider` có thể chỉ định **tên lớp** của hiện thực `Configuration`. Với hiện thực đọc file, đối tượng này đọc entry trong file JAAS. Việc đổi property này không tương đương `Security.addProvider(...)`.

**Đường 2 — factory dùng chính mô hình provider của JCA.** `Configuration.getInstance("JavaLoginConfig", null)` tra cứu service **`Configuration`/`ConfigurationSpi`** trong danh sách security providers đã đăng ký, trả về một đối tượng `Configuration` do provider hỗ trợ. Đây đúng là một điểm tích hợp với JCA. Nếu service/type được yêu cầu không có, factory có thể ném `NoSuchAlgorithmException`. Không nên mặc định mọi vendor/JDK sẽ chọn cùng provider.

```java
import javax.security.auth.login.Configuration;

// Trích đoạn đặt trong method; provider hỗ trợ JavaLoginConfig phải có trong JVM.
Configuration config = Configuration.getInstance("JavaLoginConfig", null);
System.out.println(config.getProvider().getName());
```

Sau khi có đối tượng `config`, việc xác thực vẫn phải dựa vào các `AppConfigurationEntry` mà `config` trả về. Chính các entry ấy xác định **lớp `LoginModule`**. `Configuration.getInstance(...)` không biến danh sách `LoginModule` thành danh sách `Provider.Service`.

Lập trình viên còn có thể truyền trực tiếp `Configuration` cho constructor `LoginContext(String, Subject, CallbackHandler, Configuration)` để chủ động chọn cấu hình của lần đăng nhập đó, thay vì chỉ phụ thuộc cấu hình JAAS toàn cục.

Một `LoginModule` có thể dùng các engine JCA bên trong: ví dụ kiểm tra chữ ký số bằng `Signature`, truy cập khóa qua `KeyStore`, hay chạy thao tác mã hóa qua `Cipher`. Lúc ấy **JAAS chọn module**, còn **JCA chọn hiện thực cryptography/security service mà module gọi**:

```text
LoginContext -> Configuration -> LoginModule -> kiểm tra credential -> Subject
                                       |
                                       +-> Signature/KeyStore/Cipher.getInstance(...)
                                                -> JCA Provider -> SPI hiện thực
```

Điểm chung là khả năng thay thế hiện thực; ranh giới là **chọn quy trình xác thực** so với **chọn hiện thực một dịch vụ bảo mật**.

## 5. Phân biệt các tên cấu hình rất giống nhau

| Tên/API | Loại | Quyết định điều gì? |
|---|---|---|
| `-Djava.security.auth.login.config=demo-jaas.conf` | JVM **system property** | Vị trí file entry JAAS cho hiện thực cấu hình file mặc định |
| `login.config.url.1`, `login.config.url.2`… | **Security properties** trong `conf/security/java.security` | Các URL cấu hình JAAS cho hiện thực đọc file |
| `login.configuration.provider` | **Security property** | Tên lớp hiện thực `Configuration` được dùng cho cấu hình JAAS mặc định |
| `Configuration.setConfiguration(config)` | Java API | Thay đối tượng `Configuration` JAAS toàn cục đã cài đặt |
| `Configuration.getInstance(type, params)` | Java API sử dụng provider | Tìm hiện thực `ConfigurationSpi` của type qua JCA provider registry |
| `security.provider.1`, `security.provider.2`… | **Security properties** | Đăng ký/thứ tự ưu tiên provider JCA |
| `Security.addProvider(provider)` / `insertProviderAt(...)` | Java API | Thêm provider JCA tại runtime |

Các system property bắt đầu bằng `-D` không đồng nhất với security properties lấy qua `Security.getProperty(...)`. File `java.security` là file cấu hình security của JDK; file `demo-jaas.conf` chứa **entry LoginModule**. Hai file phục vụ mục đích khác nhau.

Khi hỏi “tôi đã cài provider rồi sao JAAS không đổi?”, cần kiểm tra bạn vừa cài **`java.security.Provider`**, đổi **`Configuration`**, hay sửa **entry `LoginModule`**. Ba thao tác không hoán đổi cho nhau.

## 6. Ví dụ Java 17+: một lần JAAS login và một lần chọn provider JCA độc lập

Ví dụ này chạy với JDK có JAAS/JCA tiêu chuẩn, không cần Kerberos. Để tập trung vào **cơ chế chọn module**, `DemoLoginModule` cố ý chấp nhận mọi lần đăng nhập và gắn principal giả lập. **Nó không xác thực credential thật, tuyệt đối không dùng để bảo vệ ứng dụng.**

Tạo ba file cùng thư mục.

**File `demo-jaas.conf`** — JAAS chọn lớp và control flag:

```text
Demo {
    DemoLoginModule required;
};
```

**File `DemoLoginModule.java`** — triển khai JAAS SPI:

```java
import java.security.Principal;
import java.util.Map;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.spi.LoginModule;

public class DemoLoginModule implements LoginModule {
    private Subject subject;
    private Principal principal;
    private boolean passed;

    @Override
    public void initialize(Subject subject, CallbackHandler handler,
                           Map<String, ?> sharedState, Map<String, ?> options) {
        this.subject = subject;
    }

    @Override
    public boolean login() {
        passed = true; // Chỉ minh họa cơ chế; không xác thực người dùng thật.
        return true;
    }

    @Override
    public boolean commit() {
        if (!passed) return false;
        principal = () -> "demo-user";
        subject.getPrincipals().add(principal);
        return true;
    }

    @Override
    public boolean abort() {
        return logout();
    }

    @Override
    public boolean logout() {
        if (principal != null) subject.getPrincipals().remove(principal);
        principal = null;
        passed = false;
        return true;
    }
}
```

**File `BoundaryDemo.java`** — ứng dụng gọi hai hệ thống:

```java
import java.security.MessageDigest;
import javax.security.auth.login.LoginContext;

public class BoundaryDemo {
    public static void main(String[] args) throws Exception {
        LoginContext context = new LoginContext("Demo");
        context.login();
        System.out.println("JAAS principal: " +
                context.getSubject().getPrincipals().iterator().next().getName());

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        System.out.println("JCA algorithm: " + digest.getAlgorithm());
        System.out.println("JCA provider assigned: " + (digest.getProvider() != null));

        context.logout();
        System.out.println("JAAS principals after logout: " +
                context.getSubject().getPrincipals().size());
    }
}
```

Biên dịch và chạy trong thư mục chứa ba file:

```bash
javac BoundaryDemo.java DemoLoginModule.java
java -Djava.security.auth.login.config=demo-jaas.conf BoundaryDemo
```

**Kết quả chuẩn** (đã kiểm chứng bằng biên dịch/chạy trên OpenJDK; phần provider chỉ in boolean nên không phụ thuộc tên vendor):

```text
JAAS principal: demo-user
JCA algorithm: SHA-256
JCA provider assigned: true
JAAS principals after logout: 0
```

Hãy lần theo đúng **hai đường chọn hiện thực**:

1. `LoginContext("Demo")` tìm entry `Demo` trong file cấu hình JAAS. Entry chỉ định `DemoLoginModule` với flag `required`. `login()` rồi `commit()` thêm principal vào `Subject`; `logout()` gỡ principal.
2. `MessageDigest.getInstance("SHA-256")` tra registry JCA để tìm provider của thuật toán SHA-256; không nhìn tên `Demo`, tên `DemoLoginModule` hay control flag `required`.
3. Thay `DemoLoginModule` trong `demo-jaas.conf` sẽ thay luồng JAAS. Thay provider cung cấp `SHA-256` sẽ ảnh hưởng lần tạo `MessageDigest`. Mỗi thao tác độc lập trừ khi module xác thực tự gọi JCA ở bên trong.

Ví dụ chỉ kiểm chứng wiring/luồng gọi. Trong hệ thống thật, `login()` phải kiểm tra bằng chứng xác thực qua dịch vụ đáng tin, xử lý bí mật phù hợp, sau đó mới `commit()` dữ liệu danh tính; không được xem một `Principal` do ứng dụng tự thêm là bằng chứng đã xác thực.

## 7. Định vị lỗi theo lớp thay vì sửa nhầm cấu hình

| Triệu chứng | Nên kiểm tra trước | Không nên kết luận vội |
|---|---|---|
| `LoginException` báo thiếu `LoginModule` cho entry | Tên trong `LoginContext("...")`, file JAAS được load, entry `other`, tên lớp/cú pháp | “Chưa cài cryptographic provider” |
| `LoginException` / `FailedLoginException` trong module | Credential, callback, tùy chọn, thứ tự và flag của module | “Sai thuật toán SHA-256” |
| `ClassNotFoundException` hoặc lỗi tạo `LoginModule` | Classpath/module path, tên lớp, visibility/constructor, dependency | “Phải gọi `Security.addProvider`” |
| `NoSuchAlgorithmException` từ `MessageDigest.getInstance`/`Signature.getInstance` | Tên thuật toán/dịch vụ và provider nào hỗ trợ nó | “Thiếu entry `LoginModule`” |
| `NoSuchProviderException` khi chỉ rõ provider JCA | Tên/đăng ký provider JCA thực sự hiện diện | “JAAS control flag sai” |
| `NoSuchAlgorithmException` từ `Configuration.getInstance` | Provider nào hỗ trợ service `Configuration` và type như `JavaLoginConfig` | “Mọi `LoginModule` đều chạy qua JCA” |
| Login thành công nhưng vẫn truy cập tài nguyên sai | Authorization của ứng dụng, mapping principal → quyền, phiên làm việc | “`Subject` tự động bảo vệ tất cả API” |

Hướng kiểm tra ngắn gọn: tìm **API ném lỗi đầu tiên**. Lỗi ở `LoginContext`/`LoginModule` thường thuộc đường JAAS; lỗi từ engine `getInstance` thuộc việc chọn JCA service; lỗi authorization là lớp quyết định quyền độc lập. Nếu `LoginModule` gọi JCA bên trong, stack trace có thể đi qua **cả hai**; xác định chính xác bước thất bại.

## 8. Khi hai hệ thống cùng xuất hiện trong ứng dụng thực tế

**Trường hợp A — thay phương thức đăng nhập:** chuyển từ module kiểm tra tài khoản sang Kerberos/LDAP bằng cách đổi cấu hình `LoginModule` và chuẩn bị hạ tầng tương ứng. Thêm provider SHA-256 không thực hiện chuyển đổi phương thức đăng nhập.

**Trường hợp B — khóa nằm trong HSM/PKCS#11:** đăng ký provider JCA/PKCS#11 để `KeyStore`/`Signature` dùng phần cứng. Một `LoginModule` có thể dùng nó khi xác thực dựa trên chữ ký/chứng chỉ, nhưng quy tắc “ai được đăng nhập” và cách ánh xạ sang `Principal` vẫn do luồng xác thực quản lý.

**Trường hợp C — ứng dụng đã xác thực nhưng cần ký tài liệu:** JAAS xác lập `Subject` của người dùng; API nghiệp vụ kiểm tra quyền được ký, rồi `Signature` lấy hiện thực từ JCA provider. “Xác thực thành công”, “có quyền ký” và “thuật toán ký hợp lệ” là ba kiểm tra riêng biệt.

**Trường hợp D — đổi provider JCA toàn JVM:** thay đổi có thể ảnh hưởng TLS, chữ ký, keystore và các engine khác cùng dùng provider. Hãy nêu lý do cụ thể khi pin tên provider; để JCA tự chọn khi không có yêu cầu tương thích, thiết bị hay tuân thủ đặc biệt.

## 9. Nguyên tắc an toàn và ghi nhớ

- **Không nhầm xác thực với phân quyền.** `Subject`/`Principal` mô tả danh tính sau JAAS; ứng dụng phải kiểm tra quyền trên tài nguyên, và một đối tượng `Subject` không tự khiến mọi API được bảo vệ.
- **Không coi sự hiện diện của provider là bằng chứng xác thực.** `Security.getProviders()` chỉ cho biết dịch vụ JCA sẵn có; `MessageDigest` tạo được không nói gì về danh tính người dùng.
- **Không đưa bí mật vào ví dụ demo hoặc log.** `CallbackHandler` nên chuyển dữ liệu nhập tới `LoginModule`; module phải kiểm chứng với hệ thống tin cậy và dọn credential khi không cần. Không dùng `SHA-256` đơn thuần để lưu mật khẩu; hệ thống mật khẩu cần phương pháp KDF phù hợp, salt và cấu hình công sức tính toán.
- **Đọc thứ tự và control flag JAAS như một chính sách bảo mật.** `required`, `requisite`, `sufficient`, `optional` thay đổi điều kiện thành công; review bằng ca thành công và thất bại cho từng module.
- **Phân biệt nơi cấu hình.** File JAAS chứa entry `LoginModule`; security properties cấu hình `Configuration` hoặc provider registry; mã ứng dụng gọi API quyết định bước nào được sử dụng.

Tự kiểm tra hiểu bài:

1. Gọi `Security.addProvider(new MyProvider())` có tự bổ sung `MyLoginModule` vào `LoginContext("App")` không? **Không**; phải cấu hình entry JAAS hoặc cung cấp `Configuration` tương ứng.
2. Đổi `required` thành `sufficient` có ảnh hưởng `MessageDigest.getInstance("SHA-256")` không? **Không trực tiếp**; nó đổi chính sách kết hợp các module xác thực.
3. Vì sao `Configuration.getInstance("JavaLoginConfig", null)` là ngoại lệ đáng nhớ? Vì **đối tượng cấu hình JAAS** trong API này được chọn qua cơ chế **JCA provider/`ConfigurationSpi`**; nội dung cấu hình ấy vẫn quyết định `LoginModule`.
4. Nếu `LoginModule` dùng `Signature.getInstance(...)` và thiếu thuật toán, bạn sửa đâu? Kiểm tra **JCA service/algorithm/provider** mà module yêu cầu, đồng thời giữ nguyên việc JAAS quyết định module nào được gọi.

## Tài liệu chính thức

- Oracle, *JAAS Reference Guide* (Java 21): https://docs.oracle.com/en/java/javase/21/security/java-authentication-authorization-service-jaas-reference-guide.html
- Oracle, *JAAS LoginModule Developer's Guide* (Java 21): https://docs.oracle.com/en/java/javase/21/security/java-authentication-and-authorization-service-jaas-loginmodule-developers-guide1.html
- Java SE 21 API, `Configuration`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/security/auth/login/Configuration.html
- Java SE 21 API, `LoginContext`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/security/auth/login/LoginContext.html
- Oracle, *JCA Reference Guide* (Java 21): https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html


</details>

- [Quay lại đầu trang](#back-to-top)
