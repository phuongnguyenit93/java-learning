<a id="back-to-top"></a>

# 18. SASL và GSS-API

## Menu
- [18.1 SASL giải quyết xác thực trong giao thức ứng dụng như thế nào](#sasl-gss-api-001)
- [18.2 SaslClient, SaslServer và vòng trao đổi challenge-response](#sasl-gss-api-002)
- [18.3 Thương lượng mechanism, authentication identity và authorization identity](#sasl-gss-api-003)
- [18.4 Quality of protection: auth, auth-int, auth-conf và giới hạn tùy mechanism](#sasl-gss-api-004)
- [18.5 GSSManager, GSSName, GSSCredential và GSSContext](#sasl-gss-api-005)
- [18.6 Kerberos, KDC, service principal, ticket, keytab và quan hệ với Krb5LoginModule](#sasl-gss-api-006)
- [18.7 Thiết lập ngữ cảnh GSS, wrap, unwrap, MIC và kiểm tra replay hoặc thứ tự](#sasl-gss-api-007)
- [18.8 SPNEGO, ủy quyền credential và ranh giới bảo vệ của từng cơ chế](#sasl-gss-api-008)

## <a id="sasl-gss-api-001">18.1 SASL giải quyết xác thực trong giao thức ứng dụng như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# 18.1 SASL giải quyết xác thực trong giao thức ứng dụng như thế nào

## 1. Bài toán SASL giải quyết

Một giao thức ứng dụng như SMTP, IMAP, LDAP hoặc XMPP cần trả lời câu hỏi: **đầu kết nối này đang chứng minh danh tính nào, bằng cách nào, và sau khi xác thực thành công sẽ được phép hành động với danh tính nào?**

Nếu mỗi giao thức tự thiết kế lại phần kiểm tra mật khẩu, vé Kerberos, chứng chỉ hoặc token, chúng phải tự xử lý trao đổi thông điệp, thay đổi thuật toán, lỗi xác thực và chính sách bảo mật. **SASL (Simple Authentication and Security Layer)** giải quyết bài toán tái sử dụng: giao thức ứng dụng cung cấp *chỗ để xác thực*, còn một **SASL mechanism** cung cấp cách chứng minh danh tính. Có thể thay cơ chế xác thực mà không phải thiết kế lại toàn bộ giao thức.

SASL được chuẩn hóa ở **RFC 4422** và áp dụng chủ yếu cho các giao thức có kết nối. Bản thân SASL **không phải** một phương thức mã hóa duy nhất, một dịch vụ quản lý người dùng, cũng không tự biến một kết nối không an toàn thành kết nối an toàn. Mức bảo vệ thực tế phụ thuộc vào mechanism, cấu hình máy chủ, giao thức mang nó và lớp bảo mật như TLS.

**Mục tiêu sau bài:** phân biệt giao thức ứng dụng với SASL mechanism; mô tả được các bước thương lượng và xác thực; hiểu sự khác nhau giữa authentication identity và authorization identity; chọn được cơ chế phù hợp và nhận ra các lỗi triển khai hay gặp trong Java.

## 2. Kiến trúc: giao thức “chở” thông điệp, mechanism “hiểu” chứng thực

~~~text
Ứng dụng và giao thức: SMTP / IMAP / LDAP / XMPP / ...
    │ Lệnh AUTH / bind, quảng bá capabilities, báo SUCCESS/FAILURE
    ▼
Lớp tích hợp SASL
    │ Chọn mechanism, chuyển các challenge/response dạng byte
    ▼
SASL mechanism (ví dụ PLAIN, SCRAM-SHA-256, GSSAPI, EXTERNAL)
    │ Kiểm tra mật khẩu/proof, Kerberos ticket, identity ngoài kết nối...
    ▼
Máy chủ xác thực + ánh xạ danh tính + chính sách ủy quyền
    │
    └── Nếu xác thực VÀ ủy quyền thành công → phiên ứng dụng được dùng
~~~

Một giao thức tích hợp SASL phải định nghĩa **cách quảng bá hoặc chọn mechanism**, **cách bắt đầu phiên xác thực**, **cách truyền từng thông điệp challenge/response**, **cách hủy hoặc xử lý lỗi**, và **cách báo kết quả thành công/thất bại**. Ví dụ, SMTP dùng lệnh `AUTH`; LDAP dùng SASL bind. Cú pháp khung lệnh và cách mã hóa giá trị trên dây là việc của từng giao thức. SASL mechanism làm việc với các thông điệp byte logic ở bên trong khung đó.

Mechanism có thể nhận một thông điệp đầu tiên từ client (*client-first*), chờ câu đố từ server (*server-first*), hoặc trao đổi nhiều vòng. Vì vậy không có “một gói SASL chung” dùng nguyên xi cho mọi giao thức. **Tên mechanism** và ý nghĩa của nó là phần có thể tái sử dụng; **cách đóng khung, thứ tự lệnh và trạng thái phiên** do giao thức ứng dụng quy định.

Lợi ích kiến trúc là khi hai phía cùng hỗ trợ một mechanism mới và chính sách cho phép, ứng dụng có thể nâng cấp từ cơ chế cũ sang cơ chế mạnh hơn mà vẫn giữ luồng nghiệp vụ và cách chuyển thông điệp SASL của giao thức.

## 3. Luồng xác thực từ kết nối đến phiên đã đăng nhập

1. **Thiết lập kênh và xác thực máy chủ.** Với mechanism gửi bí mật có thể khôi phục như `PLAIN` hoặc bearer token, phải có TLS bảo vệ và kiểm tra đúng danh tính máy chủ trước khi gửi chứng thực. Nếu giao thức nâng cấp bằng STARTTLS, sau nâng cấp cần thực hiện lại phần khám phá capabilities theo đặc tả giao thức, không dùng danh sách cũ một cách mù quáng.
2. **Máy chủ công bố các mechanism hợp lệ.** Ví dụ server hỗ trợ `SCRAM-SHA-256` và `PLAIN` trên kênh TLS. Danh sách được công bố có thể phụ thuộc kênh, cấu hình và chính sách.
3. **Client chọn một mechanism có hỗ trợ ở cả hai đầu và đạt yêu cầu bảo mật.** Không nên “thấy cái nào server đưa trước thì dùng”. Nếu chính sách yêu cầu SCRAM có channel binding mà chỉ còn PLAIN, client phải từ chối hoặc báo lỗi, thay vì tự hạ cấp.
4. **Bắt đầu SASL exchange.** Client gửi tên mechanism cùng initial response nếu mechanism và giao thức cho phép. Nếu không, server có thể gửi challenge trước. Các vòng tiếp theo đưa challenge của server vào mechanism phía client để tạo response, và ngược lại.
5. **Máy chủ kiểm tra bằng chứng và danh tính.** Mechanism xác thực credential/proof; server xác định *authentication identity* (ai thực sự chứng minh được danh tính). Sau đó server kiểm tra *authorization identity* (danh tính mà phiên muốn hoạt động dưới quyền), kể cả trường hợp hai danh tính khác nhau.
6. **Máy chủ báo kết quả cuối cùng bằng thông điệp của giao thức.** Chỉ **server báo thành công** và các điều kiện bảo mật/ủy quyền được đáp ứng thì ứng dụng mới coi kết nối là đã xác thực. Client xử lý dữ liệu cuối của mechanism nếu đặc tả yêu cầu; việc một đối tượng thư viện báo hoàn thành một vòng nội bộ không thể thay thế phản hồi thành công từ server.
7. **Bắt đầu thao tác ứng dụng với quyền đã xác định.** Nếu mechanism thương lượng *SASL security layer* (không phải cơ chế nào cũng có), hai phía lắp lớp đó đúng thời điểm do giao thức quy định. Nếu không, kết nối tiếp tục dựa vào TLS hoặc lớp bảo vệ bên ngoài.

~~~text
Client                     Protocol server             SASL mechanism / verifier
  |--- TLS + capabilities ------->|                                |
  |<-- các mechanism -----------|                                |
  |--- chọn mechanism + data --->|------ validate/init --------->|
  |<-- challenge ---------------|<----- challenge ---------------|
  |--- response ---------------->|------ verify response ------->|
  |<-- SUCCESS/FAILURE ----------|<----- outcome -----------------|
  |--- lệnh ứng dụng ------------>|  (chỉ khi SUCCESS)             |
~~~

Số vòng và nội dung thông điệp thay đổi theo mechanism. Các khung `challenge`/`response` có thể được mã hóa Base64 khi đặt vào cú pháp văn bản của giao thức; **Base64 chỉ là biểu diễn dữ liệu, hoàn toàn không phải mã hóa bảo mật**.

## 4. Ví dụ dễ thấy: SMTP dùng SASL PLAIN qua TLS

Giả sử client đã thiết lập TLS an toàn tới máy chủ SMTP và kiểm tra tên máy chủ/chứng chỉ. Đoạn đối thoại dưới đây là **minh họa rút gọn**, dùng tài khoản giả `alice` và mật khẩu giả `secret`:

~~~text
S: 220 mail.example.test ESMTP ready
C: EHLO client.example.test
S: 250-mail.example.test
S: 250-AUTH PLAIN
S: 250 OK
C: AUTH PLAIN AGFsaWNlAHNlY3JldA==
S: 235 2.7.0 Authentication successful
~~~

Theo RFC 4616, phần dữ liệu logic của `PLAIN` có dạng:

~~~text
UTF-8(authzid)  0x00  UTF-8(authcid)  0x00  UTF-8(password)
~~~

Với `authzid` rỗng, `authcid=alice`, `password=secret`, chuỗi byte là `\0alice\0secret`. Chuỗi `AGFsaWNlAHNlY3JldA==` là Base64 của chính các byte ấy. Người nghe lén có thể giải mã Base64 rất dễ dàng; **PLAIN không bí mật hóa credential và không tự bảo vệ tính toàn vẹn**, vì vậy phải đặt trong kênh TLS phù hợp. Dữ liệu ví dụ chỉ để học, tuyệt đối không dùng tài khoản thật và không ghi credential/response vào log.

Điểm đáng chú ý: `EHLO` và `AUTH` thuộc **SMTP**, trong khi định nghĩa bố cục các byte `authzid/authcid/password` thuộc **SASL PLAIN**. Một giao thức khác có thể chở cùng mechanism nhưng dùng cú pháp lệnh hoàn toàn khác. Trong một phiên STARTTLS, việc chào ban đầu và đàm phán TLS có các bước riêng; phía client phải khám phá lại capabilities sau khi STARTTLS hoàn tất.

Nếu mật khẩu sai, server trả lỗi; nếu đúng mật khẩu nhưng yêu cầu hoạt động dưới danh tính không được phép, server vẫn phải từ chối. Hai tình huống này không đồng nghĩa.

## 5. Authentication identity khác authorization identity

- **Authentication identity (authcid):** danh tính gắn với bằng chứng xác thực, ví dụ `alice` chứng minh biết mật khẩu hoặc sở hữu vé Kerberos.
- **Authorization identity (authzid):** danh tính mà client đề nghị sử dụng quyền trong phiên, ví dụ `billing-agent`. Có thể bỏ trống để server chọn danh tính mặc định theo mechanism/chính sách.
- **Quyết định ủy quyền:** server phải kiểm tra liệu `alice` có được hoạt động như `billing-agent` hay không. Việc client tự khai `authzid=billing-agent` **không cấp quyền** cho client.

Ví dụ: một tác vụ tích hợp được xác thực là tài khoản dịch vụ `job-runner` và yêu cầu truy cập hộp thư với `authzid=alice`. Server cần chính sách delegation rõ ràng; nếu không có, trả thất bại cho yêu cầu đó. Sau khi SASL thành công, kiểm soát truy cập cụ thể (đọc thư nào, thao tác nào, giới hạn tài nguyên nào) vẫn là trách nhiệm của ứng dụng.

Đừng nhập nhằng ba khái niệm: **authentication** xác minh bằng chứng danh tính; **authorization identity mapping** xác định phiên hoạt động dưới danh tính nào; **authorization/permission checks** quyết định từng thao tác có được phép hay không.

## 6. Các mechanism phổ biến và ý nghĩa khi lựa chọn

| Mechanism | Bằng chứng/luồng chính | Lưu ý bảo mật và ứng dụng |
| --- | --- | --- |
| `PLAIN` | Gửi `authzid, authcid, password` (dạng byte) | Đơn giản, tương thích rộng; không có security layer. Dùng **chỉ qua kênh bảo vệ phù hợp**, thường TLS đã kiểm tra máy chủ. |
| `SCRAM-SHA-256` | Challenge/response dựa trên nonce, salt, phép dẫn xuất khóa và proof; có xác nhận máy chủ | Không gửi mật khẩu thô trên dây; vẫn cần thiết kế TLS/chính sách chống MITM và chống dò mật khẩu, quản lý verifier an toàn. RFC 5802/7677. |
| `SCRAM-SHA-256-PLUS` | SCRAM-SHA-256 cộng *channel binding* với kênh bảo mật bên ngoài | Ràng buộc chứng minh xác thực với kênh TLS đã dùng, nhằm giảm một số kiểu chuyển tiếp/MITM; hai bên phải hỗ trợ loại channel binding tương thích. |
| `GSSAPI` | Trao đổi token của GSS-API, thường dựa trên Kerberos | Phù hợp môi trường Kerberos/SSO; cần cấu hình principal, service name và credential đúng; có thể thương lượng mức bảo vệ sau xác thực. |
| `EXTERNAL` | Sử dụng danh tính đã xác thực bởi lớp hoặc ngữ cảnh bên ngoài, ví dụ TLS client certificate | Server phải ánh xạ và xác nhận danh tính bên ngoài đáng tin; SASL không tự thay thế bước xác minh chứng chỉ. |
| `OAUTHBEARER` | Đưa OAuth 2.0 bearer token vào trao đổi SASL | Phụ thuộc TLS, hạn token và kiểm tra issuer/audience/expiry/scope phù hợp; token bị lộ có thể bị sử dụng bởi bên khác. RFC 7628. |

Không nên chọn `CRAM-MD5` hoặc `DIGEST-MD5` cho thiết kế mới chỉ vì một JDK/provider cũ có hỗ trợ; đây là các lựa chọn lịch sử, không phải mặc định bảo mật hiện đại. Một hệ thống phải dựa vào **cơ chế được hỗ trợ thực tế**, khả năng client/server và chính sách bảo mật, không suy ra “SASL tự động hỗ trợ mọi mechanism”.

### SCRAM có gì khác với việc gửi password?

Trong SCRAM, client và server trao đổi nonce, salt, số vòng dẫn xuất và các proof/signature theo cơ chế chuẩn. Server kiểm tra bằng chứng rằng client biết mật khẩu mà client **không truyền mật khẩu thô** trong thông điệp SCRAM; client còn có dữ liệu để xác nhận server. Phần handshake rút gọn:

~~~text
C → S: tên user + client nonce
S → C: nonce kết hợp + salt + iteration count
C → S: client proof (và dữ liệu channel binding nếu có)
S → C: server signature hoặc thông báo thất bại
~~~

Đây là **sơ đồ khái niệm**, không phải chuỗi byte đủ để tự triển khai SCRAM. SCRAM-SHA-256 sử dụng quy tắc chi tiết của RFC 5802 và RFC 7677; biến thể `-PLUS` cần channel binding đúng với phiên bảo mật, kể cả các yêu cầu tương thích TLS hiện hành. Không tự viết thuật toán SCRAM từ bản tóm tắt này. SCRAM **không** tự cung cấp security layer mã hóa cho dữ liệu ứng dụng; dùng TLS hoặc một lớp bảo vệ phù hợp. Salt và phép dẫn xuất khóa giảm một số nguy cơ nhưng không loại bỏ hoàn toàn việc đoán mật khẩu ngoại tuyến khi thông tin verifier bị lộ.

### GSSAPI nằm ở đâu?

`GSSAPI` là **một mechanism SASL**, không phải tên khác của SASL. Khi dùng GSSAPI, giao thức mang các token xác thực GSS-API; GSS-API và cơ chế bên dưới (phổ biến là Kerberos) xử lý thông tin phiên và bằng chứng. SASL chuẩn hóa việc nhúng trao đổi đó vào giao thức ứng dụng. Chi tiết về ticket, service principal, keytab và ngữ nghĩa mutual authentication thuộc phần tìm hiểu GSS-API/Kerberos riêng.

## 7. SASL, TLS và phân quyền bổ sung nhau như thế nào?

| Lớp | Trả lời câu hỏi | Không tự đảm nhận |
| --- | --- | --- |
| **TLS** | Dữ liệu trên kết nối được bảo mật và peer phía máy chủ được xác thực theo chính sách TLS hay chưa? | Không mặc nhiên xác định người dùng ứng dụng; TLS client certificate chỉ là một khả năng nếu được cấu hình. |
| **SASL mechanism** | Client chứng minh danh tính gì, theo phương pháp nào, và mechanism có tạo security layer hay không? | Không định nghĩa nghiệp vụ, cú pháp lệnh của mọi giao thức hoặc quyền tới từng tài nguyên. |
| **Giao thức ứng dụng** | Gọi SASL lúc nào, chuyển challenge/response ra sao, báo kết quả bằng lệnh nào? | Không nên tự chế lại thuật toán xác thực. |
| **Authorization tại server** | Danh tính đã xác thực được phép hành động như ai và làm gì? | Không thể dựa hoàn toàn vào tên `authzid` do client khai. |

SASL có khái niệm **security layer tùy chọn**: tùy mechanism và thương lượng, lớp này có thể bảo vệ tính toàn vẹn hoặc cả bí mật của những thông điệp *sau xác thực*. Ví dụ các mức `auth`, `auth-int`, `auth-conf` thường được nhắc khi một mechanism có hỗ trợ QOP. **Không phải mọi mechanism có đủ ba mức**: `PLAIN` và `SCRAM-SHA-256` không cung cấp SASL security layer, trong khi một số cấu hình `GSSAPI` có thể hỗ trợ. Nếu không thương lượng lớp đó, kết nối vẫn có thể được TLS bảo vệ bình thường.

Vì TLS và SASL nằm ở các tầng khác nhau, việc “đã đăng nhập bằng SASL” **không chứng minh rằng đường truyền an toàn**, và việc “đã kết nối TLS” **không chứng minh rằng client đã đăng nhập tài khoản ứng dụng**.

## 8. Java 17+: dùng API `javax.security.sasl`

Java cung cấp các giao diện trong module `java.security.sasl`:

- `Sasl.createSaslClient(...)` / `Sasl.createSaslServer(...)` tìm factory theo mechanism đã đăng ký với security provider; có thể trả `null` nếu không tìm được implementation phù hợp.
- `SaslClient` cung cấp `hasInitialResponse()`, `evaluateChallenge(byte[])`, `isComplete()`, `getNegotiatedProperty(...)`, `wrap(...)`, `unwrap(...)` và `dispose()`.
- `SaslServer` nhận response bằng `evaluateResponse(byte[])`, đưa kết quả về phía giao thức và cho phép lấy danh tính ủy quyền sau khi hoàn tất theo hợp đồng API.
- `CallbackHandler` cung cấp thông tin mà mechanism yêu cầu, chẳng hạn `NameCallback` và `PasswordCallback`. Callback được yêu cầu khác nhau giữa các mechanism; không phải cái nào cũng dùng mật khẩu.

### Ví dụ chạy độc lập: tạo initial response cho PLAIN

Ví dụ này **không mở kết nối mạng và không đăng nhập SMTP thật**. Nó chỉ cho thấy một ứng dụng Java giao dữ liệu credential cho SASL client để mechanism tạo *initial response*. Trong sản phẩm thật, chỉ kích hoạt PLAIN **sau khi TLS của phiên đã được xác thực**, không in credential hay payload ra log. Mật khẩu ở đây là chuỗi giả phục vụ học tập.

~~~java
// File: SaslPlainDemo.java
import java.util.Base64;
import java.util.Map;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;

public class SaslPlainDemo {
    public static void main(String[] args) throws Exception {
        CallbackHandler callbacks = requested -> {
            for (Callback callback : requested) {
                if (callback instanceof NameCallback name) {
                    name.setName("alice");
                } else if (callback instanceof PasswordCallback password) {
                    password.setPassword("secret".toCharArray());
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };

        SaslClient client = Sasl.createSaslClient(
                new String[] {"PLAIN"},
                null,                     // authzid: không yêu cầu danh tính khác
                "smtp",                   // tên dịch vụ/giao thức
                "mail.example.test",      // máy chủ đích của dịch vụ
                Map.of(),
                callbacks);

        if (client == null) {
            throw new IllegalStateException("Không có SASL PLAIN client provider");
        }

        try {
            if (!client.hasInitialResponse()) {
                throw new IllegalStateException("Demo cần initial response");
            }
            byte[] response = client.evaluateChallenge(new byte[0]);
            System.out.println("mechanism=" + client.getMechanismName());
            System.out.println("payloadBase64=" + Base64.getEncoder().encodeToString(response));
            System.out.println("clientComplete=" + client.isComplete());
        } finally {
            client.dispose();
        }
    }
}
~~~

Chạy trên JDK có SASL PLAIN client provider (ví dụ triển khai SunSASL trong OpenJDK/JDK phổ biến):

~~~bash
javac SaslPlainDemo.java
java SaslPlainDemo
~~~

Đầu ra dự kiến của provider PLAIN tương thích RFC 4616:

~~~text
mechanism=PLAIN
payloadBase64=AGFsaWNlAHNlY3JldA==
clientComplete=true
~~~

Ý nghĩa của `clientComplete=true`: SASL client đã tạo xong dữ liệu theo **mechanism PLAIN**; **chưa có server nào xác nhận** trong demo này. Ứng dụng thật phải đưa `response` vào lệnh xác thực của giao thức, đọc kết quả server và chỉ cập nhật trạng thái phiên sau phản hồi thành công. Ví dụ cố ý in payload để quan sát byte format; mã sản xuất phải xóa việc in này, nhận credential từ kho bí mật phù hợp và xử lý vòng đời credential cẩn thận. Cơ chế PLAIN phía server cũng phải thực hiện kiểm tra credential và quyền; gọi `SaslClient` đơn độc không tạo ra hệ thống xác thực đầy đủ.

### Khi tích hợp vào giao thức thật

Phần mềm ứng dụng/driver chịu trách nhiệm:

1. Xác minh TLS, nhận danh sách mechanism server **trên kết nối hiện tại** và lọc theo chính sách bảo mật.
2. Tạo `SaslClient` với mechanism đã chọn; nếu `null`, xử lý thiếu provider, không âm thầm chuyển sang cơ chế yếu.
3. Nếu `hasInitialResponse()`, gọi `evaluateChallenge(new byte[0])` rồi đóng gói vào lệnh mở đầu đúng theo giao thức; nếu không, chờ challenge từ server.
4. Lặp trao đổi challenge/response cho đến khi giao thức báo thành công, thất bại hoặc bị hủy; mỗi message cần được chuyển đổi/framing theo quy định của protocol (ví dụ Base64 nếu protocol yêu cầu).
5. Kiểm tra kết quả cuối, chỉ xác lập phiên đăng nhập khi server xác nhận và các điều kiện về security layer, identity, authorization đều đúng; giải phóng client bằng `dispose()` khi kết thúc.

**Quan trọng:** `SaslClient`/`SaslServer` **không tự mở socket, thực hiện TLS hay gửi SMTP/LDAP command**. Nhiều driver Java đã tích hợp phần giao thức; khi dùng driver đó, nên cấu hình cơ chế mạnh và TLS của driver thay vì tự viết một giao thức mới chỉ để gọi SASL.

## 9. Checklist bảo mật cho một triển khai SASL

- **Không downgrade.** Client phải có policy về mechanism tối thiểu chấp nhận được; đối chiếu capabilities ở đúng thời điểm, chống thay đổi trước/sau nâng cấp TLS và xử lý các trường hợp channel binding bị hạ cấp.
- **Xác thực máy chủ và kênh.** Bật TLS theo cấu hình phù hợp, kiểm tra chứng chỉ/tên máy chủ; không gửi `PLAIN` hoặc `OAUTHBEARER` qua kết nối không được bảo vệ. Với Kerberos, xác định đúng service principal.
- **Phân biệt danh tính và quyền.** Kiểm tra ánh xạ `authcid → authzid` trên server; xác thực thành công không đồng nghĩa mọi thao tác được phép.
- **Bảo vệ bí mật.** Không log password, bearer token, challenge/response nhạy cảm; bảo vệ kho credential/verifier/keytab; làm sạch/giới hạn lưu trữ dữ liệu nhạy cảm khi khả thi.
- **Giới hạn tài nguyên và lỗi.** Chặn brute force/rate-limit theo ngữ cảnh, giới hạn kích thước thông điệp, số vòng, thời gian, và số lần thử; không tiết lộ thông tin giúp liệt kê tài khoản qua nội dung lỗi.
- **Kiểm tra trạng thái.** Nếu exchange thất bại, timeout hoặc kết nối ngắt, không cấp quyền; kiểm tra hành vi khi xác thực lại và khi security layer thay đổi theo đặc tả giao thức.
- **Không tự triển khai mật mã.** Dùng mechanism/provider và thư viện được duy trì; kiểm tra cấu hình và khả năng thực tế của cả client/server. Cần rà soát riêng chính sách với chuẩn, thuật toán và yêu cầu tổ chức.

## 10. Những hiểu nhầm cần tránh

**“Dùng SASL là tự động có mã hóa.”** Sai: SASL có thể chỉ làm authentication. `PLAIN` truyền password dưới dạng có thể khôi phục; `SCRAM-SHA-256` không cung cấp confidentiality cho dữ liệu ứng dụng.

**“Cứ Base64 là password được bảo vệ.”** Sai: Base64 biến đổi biểu diễn để đặt byte vào protocol; bất kỳ ai nhận được byte đều có thể giải mã.

**“Client.isComplete() nghĩa là login thành công.”** Sai: đó là trạng thái hoàn tất của đối tượng SASL client theo mechanism. Thành công của cả kết nối phải dựa vào phản hồi xác thực phía server của giao thức.

**“Có authzid là có quyền mạo danh tài khoản khác.”** Sai: authzid là yêu cầu; server quyết định có ủy quyền hành động dưới danh tính đó hay không.

**“SASL bắt buộc challenge/response nhiều vòng.”** Sai: `PLAIN` có thể dùng một initial response; SCRAM/GSSAPI thường có nhiều thông điệp.

**“SASL và GSS-API là một.”** Sai: SASL là framework nhúng authentication vào giao thức; `GSSAPI` là một mechanism sử dụng dịch vụ GSS-API (thường Kerberos).

## 11. Tình huống tự kiểm tra

1. Server SMTP công bố `PLAIN` và `SCRAM-SHA-256`. Client chưa xác minh TLS. Có nên gửi initial response `PLAIN` ngay không? **Không.** Trước hết bảo vệ/xác minh kênh; chọn mechanism đạt policy. Mặc dù SCRAM không gửi mật khẩu thô, vẫn phải xét TLS và MITM/channel binding.
2. Người dùng `alice` xác thực thành công nhưng yêu cầu `authzid=admin` mà không có quyền delegation. Server nên làm gì? **Từ chối yêu cầu xác thực/ủy quyền đó**, không để session hoạt động như `admin`.
3. Java in `clientComplete=true` nhưng server trả “authentication failed”. Trạng thái phiên là gì? **Chưa xác thực**. Dữ liệu từ `SaslClient` là một phần trao đổi, không thay được kết quả server.
4. Một giao thức chuyển từ `PLAIN` sang `GSSAPI`. Có bắt buộc thay toàn bộ lệnh nghiệp vụ? **Không.** Phần SASL integration vẫn chuyển challenge/response và nhận outcome, nhưng mechanism, số vòng, cấu hình identity và security policy thay đổi.
5. Đã bật TLS, vậy còn cần SASL không? **Có thể vẫn cần**, nếu ứng dụng phải xác định account/authorization identity. TLS phía server thường chứng minh máy chủ, không tự xác thực tài khoản người dùng.

## 12. Tài liệu chuẩn để tra cứu

- **RFC 4422 — Simple Authentication and Security Layer (SASL):** https://www.rfc-editor.org/rfc/rfc4422
- **RFC 4616 — PLAIN SASL Mechanism:** https://www.rfc-editor.org/rfc/rfc4616
- **RFC 5802 — SCRAM SASL and GSS-API Mechanisms:** https://www.rfc-editor.org/rfc/rfc5802
- **RFC 7677 — SCRAM-SHA-256 and SCRAM-SHA-256-PLUS:** https://www.rfc-editor.org/rfc/rfc7677
- **RFC 7628 — SASL Mechanisms for OAuth:** https://www.rfc-editor.org/rfc/rfc7628
- **Java SE 21 API — `Sasl`:** https://docs.oracle.com/en/java/javase/21/docs/api/java.security.sasl/javax/security/sasl/Sasl.html

**Ghi nhớ:** SASL tạo một hợp đồng tích hợp giữa *giao thức ứng dụng* và *cơ chế xác thực có thể thay thế*. Sự an toàn của phiên đến từ cách chọn mechanism, xác minh kênh, kiểm tra credential, ánh xạ danh tính, áp dụng quyền và chỉ chấp nhận kết quả cuối của server.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sasl-gss-api-002">18.2 SaslClient, SaslServer và vòng trao đổi challenge-response</a>

<details>
<summary>Xem chi tiết</summary>

# 18.2 SaslClient, SaslServer và vòng trao đổi challenge–response

## 1. Bức tranh tổng thể

**SASL (Simple Authentication and Security Layer)** là khung xác thực cho các giao thức ứng dụng, mô tả cách chọn *cơ chế* (mechanism) và trao đổi các thông điệp xác thực. SASL không tự định nghĩa TCP, HTTP, LDAP hay định dạng gói mạng: giao thức sử dụng SASL phải quy định lệnh bắt đầu xác thực, nơi mang token, trạng thái tiếp tục/thành công/thất bại và cách đóng kết nối. RFC 4422 là tài liệu nền tảng.

Trong Java, gói `javax.security.sasl` (module `java.security.sasl`) đưa cơ chế xác thực vào hai đối tượng có trạng thái:

| Thành phần | Phía sử dụng | Vai trò |
| --- | --- | --- |
| `SaslClient` | Client | Nhận challenge từ server, sinh response, theo dõi trạng thái phía client. |
| `SaslServer` | Server | Nhận response từ client, kiểm tra thông tin xác thực, có thể sinh challenge mới và xác định danh tính được ủy quyền. |
| `Sasl` | Cả hai | Tìm `SaslClientFactory`/`SaslServerFactory` qua các security provider đã đăng ký, tạo đối tượng phù hợp với mechanism và cấu hình. |
| `CallbackHandler` | Cả hai | Cung cấp dữ liệu mà mechanism yêu cầu (tên đăng nhập, mật khẩu, realm, quyết định ủy quyền...). |

**Phân biệt ba lớp:** (1) giao thức ứng dụng chuyên chở dữ liệu, (2) SASL điều phối xác thực, (3) mechanism thực hiện thuật toán cụ thể. Ví dụ, LDAP có thể mang token của cơ chế `GSSAPI`; SMTP có thể dùng cơ chế SASL khác. Việc chọn mechanism phải thuộc chính sách bảo mật và khả năng của cả hai đầu.

## 2. Hai giao diện Java và trạng thái của chúng

### 2.1. `SaslClient`

Tạo client bằng:

```java
SaslClient client = Sasl.createSaslClient(
    new String[] {"GSSAPI"},  // danh sách cơ chế chấp nhận, theo thứ tự ưu tiên
    null,                     // authorizationId mong muốn, nếu mechanism hỗ trợ
    "ldap",                   // tên giao thức/dịch vụ
    "ldap.example.com",       // tên server đích
    props,                    // thuộc tính SASL
    clientCallbackHandler
);
if (client == null) {
    throw new IllegalStateException("Không có SASL client mechanism phù hợp");
}
```

Đây là **đoạn minh họa API**, chỉ hoạt động khi `props`, `clientCallbackHandler`, provider và thông tin dịch vụ/Kerberos thích hợp đã được chuẩn bị; không phải chương trình hoàn chỉnh. Các phương thức quan trọng:

- `getMechanismName()`: trả tên mechanism thực sự được chọn. Khi truyền nhiều lựa chọn, phải gửi **đúng tên đã chọn** cho server; không giả định phần tử đầu tiên luôn có provider hỗ trợ.
- `hasInitialResponse()`: cho biết mechanism có dữ liệu client gửi ngay khi bắt đầu hay không. Nếu `true` và giao thức hỗ trợ initial response, gọi `evaluateChallenge(new byte[0])` để lấy token đầu tiên.
- `evaluateChallenge(byte[] challenge)`: nhận token của server, cập nhật state nội bộ và trả token response. Kết quả có thể là `null` trong trường hợp đặc biệt của dữ liệu cuối đi kèm trạng thái thành công, nghĩa là **không cần gửi response**; `new byte[0]` lại là **một response rỗng có thật**.
- `isComplete()`: cho biết state machine của client hoàn tất. Điều này **chưa đủ** để coi đăng nhập thành công: còn phải nhận trạng thái `SUCCESS` của giao thức/server.
- `getNegotiatedProperty(...)`, `wrap(...)`, `unwrap(...)`: dùng sau khi hoàn tất, nếu mechanism thương lượng được các thuộc tính hoặc lớp bảo vệ dữ liệu phù hợp.
- `dispose()`: giải phóng state và dữ liệu nhạy cảm do mechanism giữ.

### 2.2. `SaslServer`

Tạo server bằng:

```java
SaslServer server = Sasl.createSaslServer(
    selectedMechanism, "ldap", "ldap.example.com", props, serverCallbackHandler
);
if (server == null) {
    throw new IllegalStateException("Không có SASL server mechanism phù hợp");
}
```

Đây cũng là **đoạn minh họa API**: `selectedMechanism`, `props` và handler do ứng dụng cung cấp. Phương thức quan trọng:

- `evaluateResponse(byte[] response)`: xử lý token client và trả challenge tiếp theo; một số mechanism cần nhiều vòng. Giá trị trả về có thể là `null` nếu không còn challenge.
- `isComplete()`: state machine phía server đã kết thúc xác thực; với cơ chế/provider cụ thể, lỗi xác thực có thể được thông báo bằng `SaslException`.
- `getAuthorizationID()`: chỉ gọi **sau khi** `isComplete() == true`; trả danh tính mà client được phép sử dụng (authorization identity), không nhất thiết đồng nhất tên đăng nhập xác thực.
- `getNegotiatedProperty(...)`, `wrap(...)`, `unwrap(...)` và `dispose()` có vai trò tương ứng phía server.

Cả `SaslClient` lẫn `SaslServer` đều **có trạng thái, gắn với một phiên trao đổi**. Không chia sẻ một instance giữa các kết nối, không dùng lại instance đã `dispose()`, không giả định có thể gọi `evaluateChallenge`/`evaluateResponse` từ nhiều luồng đồng thời.

### 2.3. Callback và identity

Các mechanism hỏi thông tin qua `CallbackHandler.handle(Callback[])`. Những callback phổ biến gồm:

- `NameCallback`: tên xác thực (authentication identity, ví dụ `alice`).
- `PasswordCallback`: bí mật được dùng để chứng minh danh tính; phải quản lý vòng đời và không ghi log.
- `RealmCallback`/`RealmChoiceCallback`: realm, khi mechanism yêu cầu.
- `AuthorizeCallback`: phía server quyết định danh tính đã xác thực có được **hành động dưới authorization ID** được yêu cầu hay không.

**Authentication** trả lời “đây là ai?”; **authorization** trả lời “người này được phép hành động dưới danh tính/quyền nào?”. Cùng một tên ở hai bước trong demo đơn giản không có nghĩa ứng dụng thật được bỏ qua kiểm tra quyền. Các loại callback thực tế phụ thuộc mechanism/provider; handler phải từ chối callback không hỗ trợ bằng `UnsupportedCallbackException`.

## 3. Vòng trao đổi challenge–response

Một vòng tổng quát diễn ra như sau:

```text
Client (SaslClient)                     Server (SaslServer)
       |                                          |
       |-- AUTH <mechanism> [+ initial token] --->|  [giao thức ứng dụng]
       |<--------------- CHALLENGE ---------------|  evaluateResponse(...)
       |-- RESPONSE ----------------------------->|  evaluateChallenge(...)
       |<----------- CHALLENGE tiếp (nếu có) -----|
       |-- RESPONSE tiếp ------------------------>|
       |<----- SUCCESS / FAILURE [+ final data] --|  [giao thức ứng dụng]
       |                                          |
```

1. **Chọn và chấp nhận cơ chế.** Client chỉ chọn trong danh sách server công bố và chính sách cục bộ cho phép. Server xác nhận mechanism được phép rồi tạo `SaslServer`. Thông điệp chọn mechanism là một phần của giao thức ứng dụng, **không** do `SaslClient` tự gửi qua mạng.
2. **Khởi tạo đúng chiều.** Nếu `client.hasInitialResponse()` là `true`, client có thể tạo token đầu bằng `evaluateChallenge(new byte[0])`; nếu `false`, client chờ challenge đầu của server. Một giao thức không cho gửi initial response có thể yêu cầu một challenge rỗng đầu tiên với mechanism client-first.
3. **Trao đổi token.** Server chuyển response nhận được vào `server.evaluateResponse(...)`; client chuyển challenge nhận được vào `client.evaluateChallenge(...)`. Mỗi lần gọi có thể làm thay đổi state và sinh token để chuyển sang phía đối diện.
4. **Lặp khi cần.** Không giả định “một challenge, một response” cho mọi mechanism: một số có nhiều lượt, mutual authentication hoặc thông tin đàm phán bổ sung.
5. **Xác nhận kết thúc ở hai cấp.** Server phải báo `SUCCESS`/`FAILURE` theo giao thức. Client chỉ chấp nhận thành công khi trạng thái giao thức và trạng thái của `SaslClient` nhất quán; server phải kiểm tra xác thực và ủy quyền theo chính sách. Dữ liệu cuối kèm thông báo thành công, nếu có, vẫn có thể cần được client xử lý.
6. **Chỉ sau thành công** mới dùng authorization ID và các thuộc tính/lớp bảo vệ đã thương lượng để chuyển sang trao đổi dữ liệu ứng dụng.

`null` khác với mảng byte rỗng: `null` thường mang ý nghĩa “không có token để gửi”, còn `byte[0]` là token độ dài 0 mà giao thức vẫn có thể phải chuyển đi. Phải phân biệt thêm trạng thái `CONTINUE` và `SUCCESS`; chỉ nhìn thấy mảng rỗng không chứng minh xác thực hoàn tất.

Ở mức vận chuyển, SASL token là `byte[]` **nhị phân tùy ý**. Nếu giao thức dùng dòng văn bản, nó có thể quy định Base64; nếu dùng giao thức nhị phân, nó có thể truyền payload có length-prefix. Không tùy tiện chuyển token qua `new String(bytes)`/encoding mặc định, không tự thêm newline và không làm mất byte `0x00`.

## 4. Ví dụ Java 17+ chạy được: vòng CRAM-MD5 trong một JVM

Ví dụ chọn `CRAM-MD5` vì JDK thường có provider cho cả client và server và có thể mô phỏng một vòng challenge–response **mà không cần Kerberos hay server mạng**. CRAM-MD5 là cơ chế cũ, có hạn chế bảo mật; **chỉ dùng để học API/state machine, không đề xuất cho hệ thống mới**. Để mô phỏng, hai đầu được gọi trực tiếp trong cùng tiến trình; ứng dụng thực phải truyền token qua giao thức được thiết kế rõ ràng.

Lưu tệp `SaslRoundTripDemo.java`:

```java
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import javax.security.auth.callback.*;
import javax.security.sasl.*;

public class SaslRoundTripDemo {
    // Bí mật cố định và dùng chung chỉ nhằm mô phỏng, KHÔNG làm vậy ở production.
    private static final char[] SECRET = "demo-only-secret".toCharArray();

    public static void main(String[] args) throws Exception {
        CallbackHandler clientHandler = callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback name) {
                    name.setName("alice");
                } else if (callback instanceof PasswordCallback password) {
                    password.setPassword(SECRET);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };

        CallbackHandler serverHandler = callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback name) {
                    if (!"alice".equals(name.getDefaultName())) {
                        throw new IOException("Unknown user");
                    }
                    name.setName("alice");
                } else if (callback instanceof PasswordCallback password) {
                    password.setPassword(SECRET);
                } else if (callback instanceof AuthorizeCallback authz) {
                    authz.setAuthorized(
                        authz.getAuthenticationID().equals(authz.getAuthorizationID()));
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };

        SaslClient client = Sasl.createSaslClient(
            new String[] {"CRAM-MD5"}, null, "demo", "localhost",
            Map.of(), clientHandler);
        SaslServer server = Sasl.createSaslServer(
            "CRAM-MD5", "demo", "localhost", Map.of(), serverHandler);
        if (client == null || server == null) {
            throw new IllegalStateException("Mechanism not available");
        }

        try {
            // CRAM-MD5 là server-first. Phản hồi rỗng mô phỏng điểm khởi động.
            byte[] challenge = server.evaluateResponse(new byte[0]);
            int rounds = 0;

            while (!server.isComplete()) {
                if (challenge == null || rounds >= 10) {
                    throw new SaslException("Unexpected protocol state");
                }
                byte[] response = client.evaluateChallenge(challenge);
                if (response == null) {
                    throw new SaslException("Missing client response");
                }
                challenge = server.evaluateResponse(response);
                rounds++;
            }

            if (!client.isComplete()) {
                throw new SaslException("Client/server state mismatch");
            }

            System.out.println("Mechanism: " + client.getMechanismName());
            System.out.println("Authenticated: " + server.isComplete());
            System.out.println("Authorized ID: " + server.getAuthorizationID());
            System.out.println("Rounds: " + rounds);
        } finally {
            client.dispose();
            server.dispose();
            Arrays.fill(SECRET, '\0');
        }
    }
}
```

Biên dịch và chạy (JDK 17 trở lên có provider hỗ trợ `CRAM-MD5`):

```bash
javac --release 17 SaslRoundTripDemo.java
java SaslRoundTripDemo
```

Kết quả đã kiểm tra khi chạy với JDK có provider `CRAM-MD5`:

```text
Mechanism: CRAM-MD5
Authenticated: true
Authorized ID: alice
Rounds: 1
```

**Đọc mã theo đúng chiều:** server nhận lời bắt đầu giả lập và sinh challenge → client xử lý bằng `evaluateChallenge` để tạo response → server kiểm tra response → cả hai báo hoàn tất → ứng dụng mới lấy `getAuthorizationID()`. `rounds = 1` chỉ đúng với luồng CRAM-MD5 của ví dụ, không phải số vòng mặc định của SASL. Callback phía server tra cứu mật khẩu theo tên do mechanism cung cấp; chương trình dùng một user và một bí mật cố định cho dễ quan sát, còn hệ thống thật cần nguồn credential an toàn và kiểm tra quyền độc lập.

Nếu thay bí mật của một phía, trao đổi phải thất bại (`SaslException` hoặc lỗi xác thực tương ứng), tuyệt đối không chuyển sang trạng thái ứng dụng đã đăng nhập. Nếu provider bị vô hiệu hóa hoặc không có implementation, `createSaslClient`/`createSaslServer` có thể trả `null`; không được mặc định là thành công.

## 5. Phân biệt state machine với giao thức mạng thật

Đoạn Java trên gọi trực tiếp hai đối tượng nên **không** phải một server LDAP/SMTP hoàn chỉnh. Khi tích hợp vào giao thức thực, cần viết lớp điều phối riêng:

- Định nghĩa frame/lệnh chứa tên mechanism, initial response, challenge, response, trạng thái `CONTINUE`, `SUCCESS`, `FAILURE` và (nếu có) dữ liệu cuối.
- Bảo toàn **từng byte token**, ranh giới từng message và thứ tự lượt. Khi message dài hoặc bị chia khung, phải ghép đủ token trước khi đưa vào `evaluateChallenge`/`evaluateResponse`.
- Điều khiển vòng lặp theo cả trạng thái message **lẫn** `isComplete()`; giới hạn số vòng, kích cỡ token và thời gian; lỗi giao thức hoặc state lệch nhau thì hủy phiên.
- Nếu client có initial response, truyền nó ở phần được giao thức cho phép; nếu server-first, không tự gửi token client trước khi có challenge. Nếu server trả final data cùng `SUCCESS`, client xử lý đúng như đặc tả mechanism/giao thức mà không gửi response bổ sung sau khi đã kết thúc.
- Chỉ báo ứng dụng “đã xác thực” sau khi server thực sự công nhận thành công và client nhận được kết quả tương ứng. Trên thất bại, đóng/hủy phiên và dọn state bảo mật.

Một sơ đồ trạng thái giản lược:

```text
NEW
  -> MECHANISM_SELECTED
  -> EXCHANGING (challenge <-> response, lặp 0..n lần)
  -> AUTHENTICATED + AUTHORIZED  [server SUCCESS và state nhất quán]
  -> APPLICATION_TRAFFIC
                  \-> DISPOSED
  -> FAILED / ABORTED -> DISPOSED
```

Đây là mô hình để tư duy, **không phải** tập giá trị enum có sẵn trong Java SASL API.

## 6. QOP, authorization và mối liên hệ GSS-API

SASL có thể thương lượng **quality of protection** qua thuộc tính `Sasl.QOP`:

| QOP | Ý nghĩa |
| --- | --- |
| `auth` | Chỉ xác thực; **không tự cung cấp** tính toàn vẹn/bí mật cho dữ liệu ứng dụng. |
| `auth-int` | Xác thực cộng kiểm tra tính toàn vẹn của message. |
| `auth-conf` | Xác thực cộng bảo vệ tính bí mật (và toàn vẹn) của message. |

Sau khi cả hai phía hoàn tất, ứng dụng có thể kiểm tra `getNegotiatedProperty(Sasl.QOP)`. Chỉ dùng `wrap`/`unwrap` nếu mechanism và QOP thực sự hỗ trợ lớp bảo vệ (`auth-int`/`auth-conf`); các hàm này không phải thao tác luôn hợp lệ cho mọi cơ chế. Giao thức ứng dụng còn phải đóng khung các bản ghi được bảo vệ theo đúng quy định.

**GSSAPI** là một SASL mechanism sử dụng GSS-API, thường gắn với Kerberos trong JDK. Cấu hình GSSAPI cần các thông tin như principal/service name, ticket và môi trường Kerberos hợp lệ; không thể thay chuỗi `"CRAM-MD5"` bằng `"GSSAPI"` trong demo rồi mặc nhiên chạy được. Cơ chế có thể cung cấp mutual authentication và đàm phán QOP, nhưng kết quả phụ thuộc provider, cấu hình và chính sách của ứng dụng.

`AuthorizeCallback` và `getAuthorizationID()` xử lý **ủy quyền danh tính**, còn quyền đọc/sửa tài nguyên nghiệp vụ vẫn phải do application kiểm tra sau đó. Một phiên SASL thành công không có nghĩa người dùng được truy cập mọi tài nguyên.

## 7. Những lỗi phổ biến và lưu ý an toàn

| Sai lầm | Hậu quả và cách sửa |
| --- | --- |
| Gọi `evaluateChallenge` trước khi biết ai gửi token đầu | Sai thứ tự handshake; kiểm tra `hasInitialResponse()` và đặc tả mechanism/giao thức. |
| Dựa riêng vào `client.isComplete()` hoặc response không rỗng để kết luận đăng nhập thành công | Có thể chấp nhận state lệch/protocol failure; bắt buộc xử lý kết quả `SUCCESS`/`FAILURE` và kiểm tra phía server. |
| Coi `null` và `byte[0]` như nhau | Làm mất hoặc tạo thừa message; bảo toàn ý nghĩa token rỗng. |
| Chuyển token SASL thành chuỗi bằng charset mặc định | Hỏng dữ liệu nhị phân; dùng `byte[]` và encoding do giao thức quy định. |
| Giả định mọi JDK/provider có mọi cơ chế | Có thể nhận `null` từ `Sasl.createSaslClient/Server`; phải kiểm tra và fail closed. |
| Nhầm authentication ID với authorization ID | Có thể cấp nhầm quyền; áp dụng `AuthorizeCallback` và kiểm tra quyền ứng dụng. |
| Gọi `wrap`/`unwrap` lúc chưa hoàn tất hoặc với `QOP=auth` | Không có lớp bảo vệ phù hợp; kiểm tra negotiated QOP trước. |
| Ghi log password, token hoặc dùng lại state của kết nối khác | Rò rỉ bí mật/lỗi trạng thái; xóa bí mật tạm, `dispose()` và mỗi phiên một instance. |

Trong hệ thống thật:

1. **Cho phép có chọn lọc mechanism** theo chính sách hiện tại; CRAM-MD5/DIGEST-MD5 là cơ chế cũ, không nên lấy ví dụ giáo dục làm cấu hình production. Những mechanism như SCRAM có thể cần provider thư viện riêng; không mặc định JDK nào cũng hỗ trợ.
2. **Bảo vệ kênh và danh sách cơ chế** bằng TLS được xác thực hoặc lớp bảo vệ tương đương khi mô hình đe dọa yêu cầu. SASL không tự mã hóa lưu lượng và việc công bố/chọn mechanism có thể bị hạ cấp nếu không được bảo vệ. Không dùng `PLAIN` trên kênh chưa bảo vệ.
3. **Không hardcode credential**, không phản hồi lỗi tiết lộ user có tồn tại hay không, không lưu token nhạy cảm trong log. Handler phía server phải lấy bí mật đúng user từ nguồn được bảo vệ và kiểm tra authorization.
4. **Fail closed** trước lỗi token, timeout, response sai, QOP không đạt yêu cầu, mechanism không hỗ trợ hoặc hai bên báo trạng thái không nhất quán.
5. **Dọn phiên** bằng `dispose()`, đóng/loại bỏ kết nối thất bại; đặt giới hạn kích thước payload, số vòng và thời gian để tránh tiêu thụ tài nguyên vô hạn.

## 8. Tự kiểm tra hiểu bài

- Vì sao `Sasl.createSaslClient(...)` trả `null` không tương đương “mật khẩu sai”? (Vì chưa tạo được implementation cho cơ chế và chính sách đã yêu cầu.)
- Vì sao CRAM-MD5 demo gọi `server.evaluateResponse(new byte[0])` trước? (Mechanism server-first cần tạo challenge; chương trình mô phỏng phần khởi động của giao thức.)
- Sau khi `client.isComplete() == true` có được cấp quyền ngay không? (Chưa; cần server báo thành công, xác minh state phù hợp và kiểm tra quyền nghiệp vụ.)
- Khi negotiated QOP là `auth`, nên gọi `wrap` để “mã hóa” bản tin không? (Không; không có lớp bảo vệ thông điệp được thương lượng.)
- Hai luồng dùng chung một `SaslClient` cho hai kết nối liệu có an toàn? (Không; đối tượng giữ state theo một phiên.)

## 9. Tài liệu tham khảo

- [RFC 4422 — Simple Authentication and Security Layer](https://www.rfc-editor.org/rfc/rfc4422)
- [Java SE 21 — `Sasl` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.security.sasl/javax/security/sasl/Sasl.html)
- [Java SE 21 — `SaslClient` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.security.sasl/javax/security/sasl/SaslClient.html)
- [Java SE 21 — `SaslServer` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.security.sasl/javax/security/sasl/SaslServer.html)
- [Java SE 21 — Security Developer's Guide](https://docs.oracle.com/en/java/javase/21/security/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sasl-gss-api-003">18.3 Thương lượng mechanism, authentication identity và authorization identity</a>

<details>
<summary>Xem chi tiết</summary>

# 18.3. Thương lượng SASL mechanism, authentication identity và authorization identity

> Phạm vi: SASL (Simple Authentication and Security Layer) trong hệ thống Java, với bối cảnh GSS-API/Kerberos; tập trung vào việc chọn cơ chế xác thực và phân biệt người đã chứng minh danh tính với danh tính mà phiên được phép đại diện.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích vì sao máy khách và máy chủ phải thống nhất một SASL mechanism trước khi trao đổi challenge/response, và ai chịu trách nhiệm lựa chọn cơ chế.
2. Phân biệt **authentication identity** (`authcid` hoặc `authnId`) và **authorization identity** (`authzid`), kể cả khi hai chuỗi giống nhau.
3. Mô tả quy tắc mặc định khi `authzid` bị bỏ trống và điều kiện để thực hiện proxy/delegated authorization.
4. Nhận ra ranh giới giữa việc xác thực bằng một mechanism (ví dụ `GSSAPI`) và việc áp dụng chính sách phân quyền.
5. Dùng đúng các điểm tích hợp `SaslClient`, `SaslServer`, `CallbackHandler` và `AuthorizeCallback` trong Java; tránh downgrade và giả mạo `authzid`.

## 2. Ba lớp khái niệm thường bị gộp nhầm

**SASL là một framework**, không phải một thuật toán mật mã đơn lẻ. Nó đưa cơ chế xác thực vào một giao thức ứng dụng như IMAP, SMTP, LDAP hoặc một giao thức riêng. Giao thức ứng dụng quy định cách công bố danh sách cơ chế, cách đóng gói thông điệp, thông báo thành công/thất bại và hành vi phiên sau xác thực.

**Mechanism** là cách hai phía chứng minh danh tính và có thể thương lượng dịch vụ bảo vệ phiên, chẳng hạn `PLAIN`, `SCRAM-SHA-256`, `GSSAPI` hoặc `EXTERNAL`. Không phải mọi mechanism đều xuất hiện trong mọi giao thức hay mọi bản Java provider.

**Identity** gồm hai khái niệm khác nhau:

| Khái niệm | Câu hỏi mà nó trả lời | Nguồn gốc và cách dùng |
| --- | --- | --- |
| Authentication identity (`authcid` / `authnId`) | **Ai đã chứng minh sở hữu thông tin xác thực?** | Server suy ra/xác minh từ password, ticket Kerberos, certificate, token… theo mechanism; không tin một username gửi lên khi chưa kiểm chứng. |
| Requested authorization identity (`authzid`) | **Client muốn hành động dưới danh tính nào?** | Client có thể đề nghị một danh tính khác; cú pháp và chuẩn hóa do giao thức ứng dụng quy định. |
| Effective authorization identity | **Sau khi kiểm tra, phiên thực sự được phép đại diện cho ai?** | Server quyết định sau khi xác thực và kiểm tra quyền đại diện; dùng cho ngữ cảnh phiên và kiểm soát truy cập tiếp theo. |

Ví dụ: service account `svc-report` đăng nhập hợp lệ bằng Kerberos và đề nghị `authzid=reports-reader`. Server đã biết `authcid=svc-report`, nhưng chỉ được chấp nhận `reports-reader` nếu chính sách cho `svc-report` quyền đại diện cho danh tính đó. Nếu người dùng `alice` yêu cầu `authzid=admin` mà không có quyền, *bắt buộc từ chối cả trao đổi SASL*. Việc đã xác thực thành công `alice` không làm yêu cầu chuyển quyền hợp lệ.

**Quan trọng:** bước SASL kiểm tra quyền **đại diện cho một identity**; sau đó ứng dụng vẫn phải kiểm tra identity ấy được đọc, ghi, xóa tài nguyên nào (RBAC/ABAC/ACL). `authzid=admin` không tự tạo ra vai trò quản trị.

## 3. Thương lượng mechanism diễn ra ở đâu?

RFC 4422 nêu rằng **mechanism negotiation là phần riêng của giao thức ứng dụng**. Thông thường server quảng bá các mechanism đang bật; client giao tập này với khả năng của mình rồi chọn một mechanism thỏa chính sách bảo mật và điều kiện triển khai. Chính SASL không định nghĩa một gói tin chung kiểu “NEGOTIATE” cho mọi ứng dụng.

Một phiên điển hình:

```text
(0) Thiết lập kênh, kiểm chứng server/TLS nếu giao thức yêu cầu.
(1) S -> C: danh sách mechanism server chấp nhận (ví dụ: GSSAPI, SCRAM-SHA-256, PLAIN)
(2) C: lọc theo local policy + provider + credentials sẵn có.
(3) C -> S: yêu cầu AUTHENTICATE <mechanism đã chọn> [initial response nếu có]
(4) S: kiểm tra mechanism có được cho phép trên kết nối này.
(5) C <-> S: trao đổi response/challenge đặc thù của mechanism.
(6) S: xác minh credentials -> tìm authcid; xác định authzid được yêu cầu.
(7) S: kiểm tra authcid có được đại diện cho authzid không.
(8) S -> C: SUCCESS hoặc FAILURE; khi thành công mới có effective authzid.
(9) Ứng dụng dùng effective authzid để kiểm tra từng hành động tiếp theo.
```

### Ví dụ lựa chọn có chính sách

Giả sử server quảng bá `[PLAIN, GSSAPI, SCRAM-SHA-256]`, client hỗ trợ `[SCRAM-SHA-256, PLAIN]`, chính sách ứng dụng từ chối `PLAIN` và ưu tiên `SCRAM-SHA-256`; đồng thời có provider tương thích. Kết quả là `SCRAM-SHA-256`. Nếu client chỉ hỗ trợ `PLAIN` thì phải báo **không có mechanism phù hợp**, không âm thầm hạ yêu cầu bảo mật.

Đừng suy ra “mechanism mạnh nhất” chỉ từ thứ tự server công bố: lựa chọn còn phụ thuộc vào TLS đã xác thực, mức bảo vệ cần thiết, dạng credentials, server identity, hỗ trợ provider, chống downgrade và chính sách tổ chức.

**Nguy cơ downgrade:** danh sách mechanism ban đầu có thể nằm ngoài thông điệp đã được xác thực, nên kẻ tấn công trên đường truyền có thể tìm cách loại bỏ cơ chế mạnh để ép client chọn cơ chế yếu. Với giao thức hỗ trợ STARTTLS, thực hiện TLS theo yêu cầu, xác minh certificate/hostname, rồi hỏi lại capabilities theo đúng đặc tả giao thức; không gửi password/token trước khi kênh đã được bảo vệ đầy đủ. Tắt fallback ngoài chính sách. Nếu giao thức cho phép, kiểm chứng capabilities sau khi hình thành security layer có integrity để phát hiện thay đổi.

### Các bước kiểm tra trước khi chấp nhận lựa chọn

- **Phía client:** whitelist mechanism; local provider thực sự tạo được `SaslClient`; credentials/JAAS phù hợp; TLS và xác thực server đạt ngưỡng; không fallback tùy tiện khi cơ chế mạnh lỗi.
- **Phía server:** chỉ quảng bá/nhận mechanism đã cấu hình; áp dụng điều kiện theo kết nối (ví dụ chỉ cho `PLAIN` trên TLS hợp lệ); có `SaslServer` provider; áp dụng giới hạn kích thước, thời gian và số lần thử.
- **Cả hai:** nhất trí mechanism theo giao thức, xử lý thông điệp đúng thứ tự, không tiếp tục sử dụng phiên khi trao đổi thất bại.

## 4. Authentication identity và authorization identity trong trao đổi thực

### 4.1. Không đưa authzid: server tự suy ra

Client có thể **không gửi** authorization identity, hoặc gửi chuỗi rỗng khi mechanism/protocol cho phép. Theo mô hình SASL, điều này yêu cầu server xác định identity thích hợp từ credentials đã xác thực. Đó thường là cùng người dùng nhưng *không bắt buộc là cùng chuỗi ký tự*: ví dụ Kerberos principal `alice@EXAMPLE.COM` có thể được ánh xạ thành tài khoản ứng dụng `alice`.

### 4.2. Có authzid: yêu cầu đại diện, không phải mệnh lệnh

Khi gửi `authzid=bob`, client đang **đề nghị** server cho phép authcid của mình hành động với danh tính `bob`. Server phải kiểm tra quy tắc ủy quyền/proxy, không đơn giản gán session user bằng trường client cung cấp.

| Credentials đã xác thực thành | Authzid được yêu cầu | Chính sách ví dụ | Kết quả |
| --- | --- | --- | --- |
| `alice` | bỏ trống | Server map credentials thành `alice` | Phiên mang quyền `alice`. |
| `alice` | `alice` | Self-authorization được phép | Phiên mang quyền `alice`. |
| `alice` | `admin` | Không có quyền đại diện | Từ chối trao đổi SASL. |
| `svc-report` | `reports-reader` | Có rule ủy quyền đúng cặp | Phiên mang quyền `reports-reader`. |
| `svc-report` | `root` | Không có rule | Từ chối, dù credentials hợp lệ. |

Quy tắc phân quyền cần làm việc với **định danh chuẩn hóa đáng tin cậy** (realm, tenant, case-sensitivity, principal mapping); chuẩn hóa nhất quán cả hai phía để tránh `Alice`/`alice`, tên rút gọn/full principal hoặc ký tự Unicode gây nhầm. Không loại bỏ realm/tenant theo thói quen.

### 4.3. Nhìn ở mức wire: mechanism PLAIN

RFC 4616 định nghĩa payload UTF-8 của `PLAIN` theo dạng:

```text
[authzid] <NUL> authcid <NUL> password
```

`<NUL>` là byte `0x00`, không phải dấu cách hoặc chuỗi sáu ký tự “<NUL>”. Khi không muốn đề nghị authzid riêng, trường đầu tiên **rỗng**. Trong `PLAIN`, cả username/password được đưa vào thông điệp SASL; mechanism **không cung cấp bí mật hoặc toàn vẹn** cho thông điệp, vì vậy phải có bảo vệ kênh như TLS đã xác minh trước. Không log/trace payload, không tự mã hóa Base64 rồi coi như đã được bảo mật: Base64 chỉ là biểu diễn dữ liệu.

### 4.4. Với GSSAPI/Kerberos

Trong SASL mechanism `GSSAPI`, GSS-API xác lập security context dựa trên credentials Kerberos; thông tin này giúp server biết principal đã được xác thực. Service principal của đích thường có dạng `imap/mail.example.com@EXAMPLE.COM` (minh họa; chính xác phải khớp môi trường). Application vẫn phải quyết định cách ánh xạ principal đó thành authcid nội bộ, xử lý authzid rỗng/được yêu cầu và từ chối ủy quyền trái phép. Ticket hợp lệ **không** đồng nghĩa được phép làm người dùng tùy ý.

Phân biệt:

- `GSS-API` là API dùng để xác lập context bảo mật trừu tượng; Kerberos là một công nghệ thường đứng bên dưới.
- `GSSAPI` là tên một SASL mechanism sử dụng dịch vụ GSS-API; nó hoạt động trong vòng đời `SaslClient` / `SaslServer`.
- `javax.security.sasl.AuthorizeCallback` hỏi chính sách có cho authcid hành động như authzid không. Nó không thay việc kiểm chứng Kerberos ticket.

## 5. Một số mechanism để đặt lựa chọn vào đúng bối cảnh

| Mechanism | Dữ liệu dùng để xác thực | Lưu ý khi lựa chọn và xác định identity |
| --- | --- | --- |
| `GSSAPI` | Context GSS-API, thường là Kerberos ticket | Cần cấu hình realm, principal/service name, ticket/JAAS; hỗ trợ xác thực hai chiều và lớp bảo vệ tùy thương lượng. Bắt buộc kiểm tra mapping và quyền đại diện. |
| `SCRAM-SHA-256` / `SCRAM-SHA-256-PLUS` | Challenge/response dựa trên password và salt, không truyền password trần | `-PLUS` có channel binding; vẫn cần TLS và cấu hình đúng. Provider Java chuẩn không được mặc định coi là hỗ trợ mechanism này. |
| `PLAIN` | Authcid + password, có thể kèm authzid | Chỉ cho phép trong kênh được bảo vệ phù hợp; mechanism tự nó không bảo vệ password. |
| `EXTERNAL` | Danh tính được chứng minh ở lớp ngoài (ví dụ TLS client certificate) | Server phải tin cậy, xác minh và ánh xạ danh tính của lớp ngoài; authzid được đề nghị vẫn cần kiểm tra. |

Không đưa `CRAM-MD5`, `DIGEST-MD5` hoặc thuật toán cũ vào giải pháp mới chỉ vì provider hỗ trợ. Tên mechanism được đăng ký theo đặc tả, còn việc mechanism nào có trên **runtime hiện tại** do provider, phiên bản và cấu hình quyết định.

## 6. Ví dụ Java 17+ chạy độc lập: chọn mechanism và chặn giả mạo authzid

Ví dụ sau có hai phần: chọn mechanism từ tập đã được chính sách cho phép và mô phỏng **bước kiểm tra authorization sau khi authcid đã được xác thực** bằng `AuthorizeCallback`. Nó không thực hiện password/Kerberos handshake; không được dùng các chuỗi `authn` tự nhập từ request thực tế để thay cho kết quả xác thực.

Lưu tệp `SaslIdentityDemo.java`:

```java
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;

public class SaslIdentityDemo {
    static String choose(List<String> advertised,
                         List<String> preferred,
                         Set<String> allowed) {
        for (String mech : preferred) {
            if (allowed.contains(mech) && advertised.contains(mech)) {
                return mech;
            }
        }
        throw new IllegalStateException("No allowed common SASL mechanism");
    }

    // Quy tắc ví dụ: tự đại diện; riêng svc-report được đại diện reports-reader.
    static boolean mayAssume(String authn, String authz) {
        return authn.equals(authz)
                || (authn.equals("svc-report") && authz.equals("reports-reader"));
    }

    static boolean check(String authn, String authz)
            throws IOException, UnsupportedCallbackException {
        CallbackHandler handler = callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof AuthorizeCallback request) {
                    boolean grant = mayAssume(
                            request.getAuthenticationID(),
                            request.getAuthorizationID());
                    request.setAuthorized(grant);
                    if (grant) {
                        request.setAuthorizedID(request.getAuthorizationID());
                    }
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };

        AuthorizeCallback request = new AuthorizeCallback(authn, authz);
        handler.handle(new Callback[] {request});
        return request.isAuthorized();
    }

    public static void main(String[] args) throws Exception {
        List<String> advertised = List.of("PLAIN", "GSSAPI", "SCRAM-SHA-256");
        List<String> preferred = List.of("SCRAM-SHA-256", "GSSAPI", "PLAIN");
        Set<String> allowed = Set.of("SCRAM-SHA-256", "GSSAPI");

        System.out.println("mechanism=" + choose(advertised, preferred, allowed));
        System.out.println("alice -> alice: " + check("alice", "alice"));
        System.out.println("alice -> admin: " + check("alice", "admin"));
        System.out.println("svc-report -> reports-reader: "
                + check("svc-report", "reports-reader"));
    }
}
```

Chạy với JDK 17+:

```bash
javac --release 17 SaslIdentityDemo.java
java SaslIdentityDemo
```

Stdout:

```text
mechanism=SCRAM-SHA-256
alice -> alice: true
alice -> admin: false
svc-report -> reports-reader: true
```

Điều ví dụ chứng minh:

- Server quảng bá `PLAIN` không có nghĩa client bắt buộc dùng nó: chính sách loại bỏ `PLAIN`.
- `alice` đã xác thực vẫn không được lấy session identity `admin`.
- Cho phép proxy phải dựa trên một **quy tắc cụ thể**, ở đây chỉ cặp `svc-report -> reports-reader`; deny by default.
- Trong ứng dụng thật, principal đầu vào `AuthorizeCallback` phải đến từ mechanism đã xác minh credentials. Code mô phỏng không thay thế xác thực hoặc một hệ thống phân quyền sản xuất.

## 7. Ánh xạ sang Java SASL API thật

Java SE cung cấp package `javax.security.sasl` và các factory dựa trên Security Provider Framework. Sau khi protocol/client đã chọn mechanism hợp lệ, client tạo implementation:

```java
// Đoạn khung: các biến mechanism, requestedAuthzid và clientCallbacks
// đến từ negotiation, chính sách và cấu hình credential thực của ứng dụng.
SaslClient client = Sasl.createSaslClient(
        new String[] {mechanism}, requestedAuthzid,
        "imap", "mail.example.com",
        Map.of(Sasl.QOP, "auth"),
        clientCallbacks);
if (client == null) {
    throw new SaslException("No eligible SASL client provider: " + mechanism);
}
```

Các import cần cho đoạn khung là `javax.security.sasl.Sasl`, `SaslClient`, `SaslException` và `java.util.Map`. Đây **không phải chương trình độc lập**: cần transport, cơ chế discovery, credentials/JAAS và `CallbackHandler` thực tế. Argument `requestedAuthzid` có thể `null` để server suy ra danh tính. Với các provider khác nhau, cùng một tên mechanism có thể được hỗ trợ hoặc không.

Ở server, sau khi đã kiểm tra client chọn mechanism được bật trên kết nối này:

```java
// Đoạn khung; serverCallbacks phải đáp ứng callbacks của mechanism
// và thực hiện chính sách AuthorizeCallback trên identity đáng tin.
SaslServer server = Sasl.createSaslServer(
        mechanism, "imap", "mail.example.com", serverProperties,
        serverCallbacks);
if (server == null) {
    throw new SaslException("No eligible SASL server provider: " + mechanism);
}

// Với mỗi response nhận được theo framing của protocol:
// byte[] response = server.evaluateResponse(clientResponseBytes);
// Gửi challenge tiếp nếu chưa complete; xử lý ngoại lệ như thất bại.
if (server.isComplete()) {
    String sessionIdentity = server.getAuthorizationID();
    // Chỉ lúc này mới gắn sessionIdentity vào principal của phiên.
}
```

Đoạn khung server cần import `javax.security.sasl.Sasl`, `SaslServer`, `SaslException`. Lệnh `evaluateResponse(...)` (được chú thích) phải thực sự được gọi cho từng thông điệp theo giao thức; kiểm tra `isComplete()` chỉ có ý nghĩa sau vòng trao đổi. Client tương ứng dùng `hasInitialResponse()`, `evaluateChallenge(...)` và `isComplete()`. Luôn gọi `dispose()` cho client/server khi kết thúc để giải phóng tài nguyên. Không tự sáng tác challenge, response hoặc message framing: giao thức ứng dụng định nghĩa phần vận chuyển, implementation mechanism tạo/xử lý token.

### AuthorizeCallback được dùng ở đâu?

Khi một server mechanism cần quyết định authnId có thể hành động như authzid hay không, nó có thể gọi `CallbackHandler.handle(...)` với `AuthorizeCallback`:

1. `getAuthenticationID()`: identity đã xác thực, do implementation mechanism cung cấp.
2. `getAuthorizationID()`: identity được yêu cầu/đã suy ra theo cơ chế và giao thức.
3. Server áp dụng policy: cùng identity, delegation đúng quyền/tenant, hoặc từ chối.
4. `setAuthorized(true/false)` và, nếu chấp nhận, `setAuthorizedID(canonicalId)` để trả về identity được cho phép.

Handler thực tế có thể còn gặp `NameCallback`, `PasswordCallback`, `RealmCallback` hoặc callback đặc thù của provider. Không nên giả định mọi mechanism sẽ dùng cùng một bộ callback: triển khai theo provider và credentials được sử dụng. Không cấp quyền trong code `PasswordCallback` dựa vào username; password verification và delegation policy là hai trách nhiệm khác nhau.

### SASL security layer và QOP

Property `Sasl.QOP` có thể thương lượng các giá trị (nếu mechanism hỗ trợ):

| QOP | Ý nghĩa | Ứng dụng phải làm gì? |
| --- | --- | --- |
| `auth` | Chỉ xác thực | Dùng TLS/transport bảo vệ theo chính sách; không có SASL integrity/confidentiality layer. |
| `auth-int` | Xác thực + integrity | Dùng `wrap/unwrap` cho dữ liệu qua lớp SASL nếu layer này đã được thương lượng. |
| `auth-conf` | Xác thực + integrity + confidentiality | Dùng `wrap/unwrap` và framing/buffer size theo protocol; cũng cần xác minh các điều kiện triển khai. |

Sau khi hoàn tất, kiểm tra `getNegotiatedProperty(Sasl.QOP)` thay vì chỉ tin giá trị mong muốn lúc tạo client. Không gọi `wrap/unwrap` khi không có security layer thích hợp, và không tự thay transport protocol bằng cách gửi các byte bảo vệ mà peer không mong đợi. `PLAIN` không cung cấp lớp bảo vệ SASL; `SCRAM` thông thường dựa vào TLS để bảo vệ dữ liệu ứng dụng.

## 8. Tình huống GSSAPI/Kerberos hay gặp

Giả sử LDAP hoặc IMAP server xác thực Kerberos principal `svc-report@EXAMPLE.COM`. Client yêu cầu hoạt động dưới `reports-reader`:

1. Client xác định service name đúng với server, ví dụ `imap/mail.example.com@EXAMPLE.COM`, và dùng Kerberos credentials hợp lệ (ticket cache, JAAS hoặc cơ chế được provider hỗ trợ).
2. SASL `GSSAPI` xây dựng security context. Server kiểm chứng token, mục tiêu dịch vụ và principal; một số điều kiện như mutual authentication/QOP do cơ chế và cấu hình thương lượng.
3. Server ánh xạ `svc-report@EXAMPLE.COM` thành authcid nội bộ theo quy tắc của tổ chức. Không mặc nhiên bỏ realm hay tenant.
4. Server nhận requested authzid `reports-reader`, hỏi chính sách ủy quyền và chỉ cấp khi rule cho phép; nếu không, báo thất bại.
5. Sau khi thành công, quyền truy cập tài nguyên của `reports-reader` vẫn được ứng dụng kiểm tra trên từng thao tác.

Cấu hình Kerberos cần kiểm tra đồng bộ DNS/hostname, service principal name, keytab hoặc ticket, realm trust, clock skew và quyền đọc credential. Đăng nhập JAAS thành công hay `Subject` có principal không tự chứng minh rằng SASL server đã xác thực đúng peer hoặc đã chấp thuận một `authzid` cụ thể.

## 9. Failure modes, dấu hiệu và cách xử lý

| Triệu chứng | Nguyên nhân thường gặp | Phản ứng an toàn |
| --- | --- | --- |
| Không có mechanism chung | Server/client/policy/provider không giao nhau | Báo lỗi; không tự bật cơ chế yếu để “cho chạy được”. |
| Client chọn `GSSAPI` nhưng `createSaslClient` trả `null` | Provider hoặc cấu hình không hỗ trợ | Kiểm tra factories/JAAS và cấu hình triển khai; không đoán cơ chế đã tồn tại. |
| Kerberos token thất bại | Ticket hết hạn, realm/SPN sai, hostname không khớp, clock skew | Chẩn đoán cấu hình/credentials; tránh lộ ticket và secret trong log. |
| Credentials đúng nhưng SASL thất bại | Requested `authzid` không được ủy quyền | Trả lỗi authorization; không thay bằng identity yêu cầu nếu chưa được cấp. |
| Đăng nhập được nhưng sai người dùng | Dùng raw authzid hoặc map principal không nhất quán | Chỉ dùng effective authorization ID sau khi server hoàn tất, kiểm tra canonical mapping. |
| `PLAIN` xuất hiện trước TLS | Capability công bố/điều kiện kết nối sai | Chỉ quảng bá/chấp nhận khi kênh có bảo vệ thích hợp; refresh capabilities sau TLS theo protocol. |
| Nhầm QOP với TLS | `auth` bị tưởng là đã mã hóa dữ liệu | Kiểm tra negotiated QOP và trạng thái TLS thực tế. |

### Checklist triển khai

- Xác định rõ cơ chế nào **được phép** ở từng môi trường; không dựa vào “cơ chế đầu tiên server gửi”.
- Bảo vệ bước discovery/negotiation theo giao thức; chống downgrade và xác thực peer/kênh trước khi gửi bí mật.
- Tách ba bước: **verify credential -> map authcid -> authorize requested authzid**. Bất kỳ bước nào lỗi đều thất bại.
- Định danh có realm/tenant được ánh xạ/chuẩn hóa có chủ đích; không cấp proxy bởi so sánh chuỗi tùy tiện.
- Chỉ tin `getAuthorizationID()` sau khi `SaslServer.isComplete()`; không lấy requested authzid từ request làm principal phiên.
- Quyền hoạt động trên resource vẫn do application authorization kiểm tra; ghi audit **cả authcid lẫn effective authzid** (không ghi credential, secret hoặc SASL token).
- Kiểm thử các trường hợp sai mật khẩu/ticket, authzid rỗng, authzid khác cùng quyền, authzid khác không quyền, không có mechanism chung, lỗi TLS/certificate, và client cố chọn mechanism bị cấm.

## 10. Câu hỏi tự kiểm tra và bài tập

1. Server quảng bá `GSSAPI, PLAIN`, client chỉ có `PLAIN` nhưng đang kết nối không TLS. Nên chọn gì? **Không chọn gì** nếu chính sách đòi kênh bảo vệ; dừng hoặc thiết lập TLS đúng giao thức trước.
2. Alice chứng minh credentials hợp lệ và gửi `authzid=admin`. Có thể trả SASL success rồi cho role `alice` không? Theo logic ở đây, **không**: yêu cầu identity trái phép làm trao đổi thất bại; không âm thầm thay nghĩa của requested identity.
3. Kerberos principal `alice@EXAMPLE.COM` và session identity `alice` khác chuỗi, có đồng nghĩa proxy authorization không? **Không nhất thiết**: đó có thể chỉ là mapping định danh mặc định; phải theo quy tắc server.
4. Chỉnh chương trình demo để thêm rule `svc-report -> audit-reader`, rồi kiểm tra `svc-report -> admin` vẫn `false`. Thử xóa `SCRAM-SHA-256` khỏi whitelist và quan sát cơ chế được chọn nếu client/server hỗ trợ `GSSAPI`.
5. Viết một bảng test gồm (`authcid`, `requested authzid`, kết quả ủy quyền) cho hai tenant khác nhau; chứng minh tài khoản thuộc tenant A không thể đại diện cho tài khoản trùng tên ở tenant B.

## Tài liệu đối chiếu

- RFC 4422 — *Simple Authentication and Security Layer (SASL)*: https://www.rfc-editor.org/rfc/rfc4422
- RFC 4616 — *The PLAIN SASL Mechanism*: https://www.rfc-editor.org/rfc/rfc4616
- RFC 5802 — *SCRAM SASL and GSS-API Mechanisms*: https://www.rfc-editor.org/rfc/rfc5802
- RFC 7677 — *SCRAM-SHA-256 and SCRAM-SHA-256-PLUS*: https://www.rfc-editor.org/rfc/rfc7677
- Java SE 17 `javax.security.sasl.Sasl`: https://docs.oracle.com/en/java/javase/17/docs/api/java.security.sasl/javax/security/sasl/Sasl.html
- Java SE 17 `javax.security.sasl.AuthorizeCallback`: https://docs.oracle.com/en/java/javase/17/docs/api/java.security.sasl/javax/security/sasl/AuthorizeCallback.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sasl-gss-api-004">18.4 Quality of protection: auth, auth-int, auth-conf và giới hạn tùy mechanism</a>

<details>
<summary>Xem chi tiết</summary>

# 18.4. SASL Quality of Protection: `auth`, `auth-int`, `auth-conf` và giới hạn theo mechanism

## 1. Bản chất của Quality of Protection (QOP)

**SASL (Simple Authentication and Security Layer)** là framework để giao thức ứng dụng như LDAP, IMAP hoặc SMTP tích hợp các **mechanism xác thực** có thể thay thế. Ngoài việc chứng thực danh tính, *một số* mechanism còn hỗ trợ **SASL security layer** bảo vệ dữ liệu ứng dụng sau khi phiên xác thực thành công.

**Quality of Protection (QOP)** mô tả mức bảo vệ **được thương lượng thực tế cho phiên SASL**. Ba mức thường gặp:

| QOP | Xác thực | Integrity của dữ liệu qua SASL layer | Confidentiality của dữ liệu qua SASL layer | Ý nghĩa |
| --- | --- | --- | --- | --- |
| `auth` | Có | Không | Không | Chỉ xác thực ở SASL; dữ liệu ứng dụng không được SASL bảo vệ |
| `auth-int` | Có | Có | Không | Bảo vệ tính toàn vẹn; phát hiện sửa đổi trái phép trong phạm vi layer |
| `auth-conf` | Có | Có | Có | Bảo vệ tính toàn vẹn và tính bí mật trong phạm vi layer |

**Integrity không phải encryption.** Ở `auth-int`, thông điệp có thể vẫn đọc được dù việc sửa đổi sẽ bị phát hiện. Ở `auth-conf`, payload được cơ chế bảo vệ cả integrity lẫn confidentiality. Khả năng ngăn phát lại, bảo đảm thứ tự thông điệp, thuật toán mật mã và khóa cụ thể thuộc về mechanism/security context, không thể suy từ một chuỗi QOP mà không kiểm tra ngữ cảnh.

QOP mô tả lớp **SASL**, không thay thế việc đánh giá bảo mật *toàn bộ* kết nối. Ví dụ, PLAIN xác thực bên trong TLS vẫn có SASL QOP `auth` vì TLS mới là lớp mã hóa transport. Ngược lại, `auth-conf` bảo vệ những byte thực sự đi qua SASL security layer; nó không tự che IP, độ dài kết nối, metadata nằm ngoài layer hoặc toàn bộ trao đổi trước khi kích hoạt layer.

## 2. Thương lượng QOP: cấu hình là yêu cầu, không phải kết quả

Trong Java, hằng `Sasl.QOP` là khóa `javax.security.sasl.qop`. Giá trị cấu hình là **danh sách ưu tiên có thứ tự**, phân tách bằng dấu phẩy, ví dụ `"auth-conf,auth-int"`. Java SASL API quy định nếu không cung cấp thuộc tính này thì mặc định là `auth`.

Một QOP chỉ được chọn khi **đồng thời** đáp ứng:

1. Giao thức ứng dụng hỗ trợ SASL và quy định rõ cách đưa security layer vào luồng dữ liệu.
2. Mechanism đã chọn **có khả năng** cung cấp mức đó; SASL framework không tự thêm mã hóa cho mọi mechanism.
3. Client, server, provider, credentials và chính sách cho phép cùng một mức QOP.
4. Exchange xác thực kết thúc thành công; ứng dụng đọc **QOP thực tế** và đối chiếu với mức bảo vệ bắt buộc trước khi gửi dữ liệu nghiệp vụ.

```text
Client: yêu cầu auth-conf,auth-int
                 |
                 v
       Chọn SASL mechanism
                 |
    Exchange challenge / response
                 |
        +--------+--------+
        |                 |
  Không có mức chung   Xác thực thành công
        |                 |
     Thất bại       Đọc QOP được chọn
                          |
                  Kiểm tra chính sách
                          |
              Chấp nhận hoặc từ chối phiên
```

Nếu hai phía không có QOP tương thích, không được tự coi xác thực thành công là đủ để đáp ứng chính sách bảo mật. Nếu giao thức cho phép chọn `auth-int` nhưng ứng dụng yêu cầu confidentiality ở SASL layer, **từ chối phiên trước khi trao đổi dữ liệu ứng dụng**. Không tự đổi chính sách xuống `auth`. Nếu chính sách cho phép mã hóa bằng TLS thay vì security layer của SASL, cần xác minh TLS **riêng biệt** (đặc biệt xác thực máy chủ và cấm downgrade).

### Điều kiện của từng mức

- **`auth`:** phù hợp khi lớp khác đã cung cấp mức bảo vệ cần thiết hoặc chỉ cần xác thực; riêng SASL không bảo vệ nội dung các bản tin tiếp theo.
- **`auth-int`:** phù hợp khi cần chống sửa đổi của payload trong layer nhưng không bắt buộc bí mật dữ liệu **ở chính lớp đó**. Việc có `wrap` không chứng minh payload được mã hóa.
- **`auth-conf`:** dùng khi chính sách yêu cầu cả bí mật và toàn vẹn trong SASL layer. Phải xác thực QOP cuối cùng, đưa mọi thông điệp thuộc phạm vi bảo vệ qua `wrap/unwrap` và tôn trọng giới hạn kích thước.

## 3. Giới hạn phụ thuộc mechanism, provider và giao thức

**Không phải mọi SASL mechanism đều hỗ trợ cả ba mức.** Tên giao thức như LDAP hay SMTP không quyết định QOP, vì cùng một giao thức có thể tích hợp nhiều mechanism khác nhau.

| Mechanism / họ | Có SASL security layer không? | Lưu ý thực tế |
| --- | --- | --- |
| **PLAIN** | **Không** | RFC 4616 xác nhận PLAIN không có security layer. Chỉ sử dụng với kênh bảo vệ phù hợp, thường TLS; không thể cấu hình PLAIN thành `auth-conf` bằng `Sasl.QOP`. |
| **SCRAM** (SCRAM-SHA-256, SCRAM-*-PLUS) | **Không** theo RFC 5802/7677 | SCRAM xác thực; bản PLUS hỗ trợ channel binding với kênh ngoài. Channel binding không phải SASL encryption; cần TLS/SSH hoặc lớp bảo vệ tương ứng cho payload. |
| **EXTERNAL** | Không tự sinh security layer | Nhận danh tính từ kênh đã xác thực từ trước, ví dụ TLS client certificate; bảo vệ dữ liệu do kênh bên ngoài đảm nhiệm. |
| **GSSAPI/Kerberos V5** | **Có thể** có `auth`, `auth-int` và `auth-conf` | RFC 4752 mô tả trao đổi hỗ trợ security layer. Integrity/confidentiality chỉ được chào/chọn nếu GSS context và hai phía thực sự có khả năng. |
| **DIGEST-MD5** (di sản) | RFC 2831 từng mô tả đủ ba mức | **Không dùng cho triển khai mới:** RFC 6331 đã đánh dấu mechanism OBSOLETE và chuyển RFC 2831 sang Historic. |

Với **SASL GSSAPI**, thực tế thường dựa vào Kerberos V5: client cần credential/ticket phù hợp; máy chủ có service principal đúng; service name, hostname, chính sách, quyền ủy nhiệm và provider phải thống nhất. JDK có `JdkSASL` provider hỗ trợ GSSAPI cùng các thuộc tính QOP, nhưng điều đó **không bảo đảm** ứng dụng cuối cùng nhận `auth-conf`. RFC 4752 yêu cầu không quảng bá confidentiality security layer khi GSS context không có cờ khả năng tương ứng. Thư viện của giao thức bên ngoài còn có thể giới hạn các QOP mà nó cho phép.

SASL và **GSS-API** là hai lớp khái niệm: GSS-API cung cấp security context và các thao tác bảo vệ dữ liệu (GSS-Wrap/GSS-Unwrap); SASL quy định cách mechanism tích hợp các thao tác đó vào giao thức. Không lấy một ví dụ GSS-API bất kỳ rồi kết luận cơ chế SASL nào cũng có thể cung cấp cùng thuộc tính.

**Kết luận kiểm tra capability:** đọc tài liệu mechanism **và** provider cụ thể; kiểm tra chính sách hai đầu; xác minh QOP sau handshake; test thực tế trong giao thức ứng dụng. Tuyệt đối không gán `auth-conf` chỉ vì có JDK `javax.security.sasl` hoặc máy chủ hỗ trợ TLS.

## 4. Security layer sau xác thực và ý nghĩa của `wrap/unwrap`

RFC 4422 định nghĩa SASL security layer chỉ được kích hoạt **sau** outcome xác thực thành công, tại vị trí mà giao thức ứng dụng chỉ rõ. Khi lớp bảo vệ đang hoạt động, dữ liệu được xử lý thành các protected buffer.

- Với **`auth`**, không có lớp SASL integrity/confidentiality để xử lý các payload tiếp theo.
- Với **`auth-int`** hoặc **`auth-conf`**, client/server gọi `wrap(plain, offset, len)` để tạo protected buffer; phía nhận gọi `unwrap(protected, offset, len)` để lấy nội dung gốc hoặc phát hiện thất bại xác minh.
- Theo RFC 4422, mỗi protected buffer trên kết nối được đóng khung bằng **4 byte độ dài theo network byte order (big-endian)** trước dữ liệu đã bảo vệ. Giao thức hoặc thư viện cao hơn có thể đã quản lý framing: không được tự thêm một lần nữa.
- Nếu layer không thể bảo vệ hoặc giải mã một buffer, **đóng kết nối**; không chấp nhận payload chưa xác minh hoặc fallback sang truyền plaintext.

```text
Business payload
      |
      +-- QOP auth -----------> Truyền theo giao thức, không qua SASL layer
      |
      +-- QOP auth-int/conf --> wrap(...) --> [4-byte length][protected bytes]
                                                        |
                                                      mạng
                                                        |
                                         kiểm tra frame + unwrap(...)
                                                        |
                                                  Business payload
```

Cần hiểu **buffer maximum** và overhead. `Sasl.MAX_BUFFER` chỉ kích thước buffer nhận tối đa của SASL layer; `Sasl.RAW_SEND_SIZE` là lượng payload gốc tối đa gửi được trong một lần `wrap` do kết quả thương lượng/provider cung cấp. Protected output có thể dài hơn input vì MAC/tag/header/padding. Thuộc tính `Sasl.STRENGTH` cho phép ưu tiên mức mạnh của thuật toán đối với mechanism hỗ trợ, nhưng **không tạo** `auth-conf` nếu mechanism không có lớp mã hóa. Không dùng `MAX_BUFFER` hay `STRENGTH` như bằng chứng duy nhất để chấp nhận chính sách.

Khi dùng TLS **và** SASL layer, lớp ngoài TLS còn có thể bảo vệ transport; dữ liệu chỉ được bảo vệ bởi SASL trong phạm vi thực sự được đưa qua `wrap`. Thời điểm bắt đầu bảo vệ, thứ tự đóng gói, số bytes chấp nhận và chuyển trạng thái phải khớp giao thức ứng dụng; nếu thư viện đã trừu tượng hóa việc đó, ứng dụng nên dùng API chính thức của thư viện.

## 5. Java 17+: kiểm tra yêu cầu QOP an toàn

### 5.1. Ví dụ runnable: logic chính sách

Ví dụ đầu tiên kiểm tra quyết định **ACCEPT/REJECT** với QOP giả định đã được thương lượng. Nó **không thực hiện handshake hay mã hóa**; mục tiêu là kiểm thử quy tắc của ứng dụng trước khi tích hợp mechanism thật.

Tạo tệp `SaslQopPolicyDemo.java`:

```java
import java.util.Map;
import javax.security.sasl.Sasl;

public final class SaslQopPolicyDemo {
    private static boolean acceptable(
            String negotiated, boolean needIntegrity, boolean needPrivacy) {
        boolean valid = "auth".equals(negotiated)
                || "auth-int".equals(negotiated)
                || "auth-conf".equals(negotiated);
        if (!valid) {
            return false;
        }

        boolean hasIntegrity = "auth-int".equals(negotiated)
                || "auth-conf".equals(negotiated);
        boolean hasPrivacy = "auth-conf".equals(negotiated);
        return (!needIntegrity || hasIntegrity)
                && (!needPrivacy || hasPrivacy);
    }

    private static void check(
            String negotiated, boolean needIntegrity, boolean needPrivacy) {
        String result = acceptable(negotiated, needIntegrity, needPrivacy)
                ? "ACCEPT" : "REJECT";
        System.out.println(negotiated + " / I=" + needIntegrity
                + " / C=" + needPrivacy + " => " + result);
    }

    public static void main(String[] args) {
        Map<String, Object> props = Map.of(
                Sasl.QOP, "auth-conf,auth-int");
        System.out.println("QOP preferences: " + props.get(Sasl.QOP));
        check("auth-conf", true, true);
        check("auth-int", true, false);
        check("auth-int", true, true);
        check("auth", true, false);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac SaslQopPolicyDemo.java
java SaslQopPolicyDemo
```

Stdout chính xác:

```text
QOP preferences: auth-conf,auth-int
auth-conf / I=true / C=true => ACCEPT
auth-int / I=true / C=false => ACCEPT
auth-int / I=true / C=true => REJECT
auth / I=true / C=false => REJECT
```

Trong chương trình này, `props` là **ưu tiên yêu cầu**; tham số `negotiated` mới là **kết quả đầu vào của kiểm tra**. Trường hợp `auth-int` không đạt yêu cầu secrecy, và `auth` không đạt yêu cầu integrity. QOP không xác định (bao gồm `null`) cũng bị từ chối. Ở hệ thống thật, sau `REJECT` phải đóng phiên và không gửi dữ liệu nghiệp vụ nhạy cảm.

### 5.2. Điểm tích hợp thực với `SaslClient`

Java SASL API nằm trong module `java.security.sasl`: `Sasl.createSaslClient` chọn provider/mechanism, `SaslClient.evaluateChallenge` xử lý token từ server, `SaslClient.isComplete` phản ánh trạng thái exchange, `SaslClient.getNegotiatedProperty` đọc giá trị thương lượng và `SaslClient.wrap/unwrap` xử lý security layer khi có.

Đoạn sau **chỉ minh họa điểm tích hợp** trong một ứng dụng LDAP đã có callback handler, credentials và vòng trao đổi token do giao thức quản lý; đây **không phải chương trình độc lập**:

```java
Map<String, Object> props = Map.of(
        Sasl.QOP, "auth-conf,auth-int");
SaslClient client = Sasl.createSaslClient(
        new String[] {"GSSAPI"},
        null,
        "ldap",
        "directory.example.org",
        props,
        callbackHandler);
if (client == null) {
    throw new SaslException("Khong tim thay SASL mechanism phu hop");
}
```

Cần import `java.util.Map` và `javax.security.sasl.*`; biến `callbackHandler` và Kerberos security context phải được cung cấp bởi môi trường tích hợp thực. Nếu mechanism bị vô hiệu hóa, provider thiếu hoặc thông tin môi trường không hợp lệ, không nên giả định tạo client sẽ thành công. Hoàn tất trao đổi token và xác nhận outcome thành công theo **giao thức ứng dụng** trước khi truy vấn QOP.

Đọc QOP và chặn mọi phiên không đủ confidentiality tại ranh giới trước dữ liệu ứng dụng:

```java
static String requireConfidentialSaslLayer(SaslClient client)
        throws SaslException {
    if (!client.isComplete()) {
        throw new SaslException("SASL handshake chua hoan tat");
    }
    Object value = client.getNegotiatedProperty(Sasl.QOP);
    String qop = value instanceof String ? (String) value : null;
    if (!"auth-conf".equals(qop)) {
        throw new SaslException("QOP khong dap ung: " + qop);
    }
    return qop;
}
```

Đây là **phương thức tích hợp**, đặt trong class có import `Sasl`, `SaslClient`, `SaslException`. Theo hợp đồng API, gọi `getNegotiatedProperty` trước `isComplete()==true` có thể ném `IllegalStateException`; `null` nghĩa là thuộc tính không được thương lượng hoặc không áp dụng. Không so sánh `props.get(Sasl.QOP)` với chính sách rồi kết luận phiên an toàn: nó chỉ chứa danh sách mong muốn ban đầu.

Với phiên `auth-int`/`auth-conf`, sau khi đã kiểm tra QOP, mỗi protected payload phải đi qua `wrap/unwrap`. Phương thức Java SASL cho phép `wrap/unwrap` **chỉ sau khi** trao đổi xác thực hoàn tất *và* integrity/privacy đã được thương lượng. Dùng chúng ở QOP `auth` có thể ném `IllegalStateException`.

### 5.3. Framing minh họa theo RFC 4422

Hai phương thức dưới đây minh họa cơ chế **raw stream do ứng dụng tự xử lý**: prefix độ dài 4 byte, hạn chế kích thước nhận, gọi `wrap/unwrap`. Đoạn này biên dịch được như một class hỗ trợ, nhưng **không tự khởi tạo phiên SASL**, chỉ thích hợp khi peer đã thành công xác thực và protocol yêu cầu chuẩn framing này.

```java
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.security.sasl.SaslClient;

final class SaslFrameIO {
    static void send(OutputStream socket, SaslClient client, byte[] plain)
            throws IOException {
        byte[] protectedData = client.wrap(plain, 0, plain.length);
        DataOutputStream out = new DataOutputStream(socket);
        out.writeInt(protectedData.length);
        out.write(protectedData);
        out.flush();
    }

    static byte[] receive(InputStream socket, SaslClient client,
                          int maxFrameBytes) throws IOException {
        if (maxFrameBytes <= 0) {
            throw new IllegalArgumentException("maxFrameBytes");
        }
        DataInputStream in = new DataInputStream(socket);
        int size = in.readInt();
        if (size < 0 || size > maxFrameBytes) {
            throw new IOException("Invalid SASL frame size: " + size);
        }
        byte[] protectedData = new byte[size];
        in.readFully(protectedData);
        return client.unwrap(protectedData, 0, protectedData.length);
    }
}
```

`DataOutputStream.writeInt` sử dụng big-endian, tương ứng network byte order. Trong ứng dụng thật, **còn phải**: kiểm tra kích thước plaintext so với `Sasl.RAW_SEND_SIZE` trước `wrap`; lấy `maxFrameBytes` từ chính sách/kết quả cơ chế hoặc protocol chứ không đoán; xử lý lỗi bằng đóng kết nối; trên server dùng `SaslServer.wrap/unwrap`; và **không tự bọc thêm** nếu thư viện LDAP/IMAP/SMTP đã làm việc này. `SaslException` kế thừa `IOException` nên hai chữ ký `throws IOException` phù hợp.

## 6. Phân biệt rõ SASL QOP và TLS

| Ví dụ | QOP SASL thực tế | Payload được bảo vệ bởi | Kết luận |
| --- | --- | --- | --- |
| **PLAIN + TLS** có xác thực máy chủ | `auth` | TLS | Có thể mã hóa kết nối đúng cách, nhưng PLAIN không tạo SASL security layer. |
| **SCRAM-SHA-256-PLUS + TLS** | `auth` | TLS, kèm channel binding trong authentication | “PLUS” không đồng nghĩa `auth-conf`; channel binding liên kết xác thực với kênh bảo vệ. |
| **GSSAPI `auth-int`** không có TLS | `auth-int` | SASL integrity layer | Có chống sửa đổi trong phạm vi layer; **không có confidentiality** của payload từ riêng layer này. |
| **GSSAPI `auth-conf`** | `auth-conf` | SASL security layer | Dữ liệu thực sự đi qua `wrap` được bảo vệ integrity và confidentiality. |
| **GSSAPI `auth` + TLS** | `auth` | TLS | Có thể đáp ứng yêu cầu *transport encryption*; không đáp ứng điều kiện bắt buộc **SASL QOP phải là `auth-conf`**. |

Quy tắc bảo mật nên phát biểu chính xác: “phải có TLS được xác minh” hay “phải có SASL `auth-conf`”, hoặc cả hai. Mỗi yêu cầu phải được kiểm thử độc lập. Cách phòng tránh downgrade cũng phụ thuộc cơ chế lựa chọn mechanism, danh tính peer và việc bảo vệ kênh trước/sau xác thực, không thể chỉ kiểm tra QOP cuối cùng là đủ.

## 7. Lỗi phổ biến, dấu hiệu và cách sửa

1. **Dùng cấu hình làm bằng chứng:** thấy `Sasl.QOP=auth-conf,auth-int` rồi ghi log “encrypted”. Sửa: chỉ báo mức bảo vệ sau khi đọc `getNegotiatedProperty(Sasl.QOP)` khi authentication thành công.
2. **Yêu cầu privacy với mechanism không có layer:** PLAIN hay SCRAM không cho `auth-conf` từ SASL. Sửa: áp dụng TLS được xác thực hoặc chọn mechanism/protocol phù hợp, không tự hạ chính sách.
3. **Đánh đồng `auth-int` với mã hóa:** integrity không ẩn nội dung. Sửa: đặt yêu cầu confidentiality riêng và kiểm tra `auth-conf` nếu cần ở lớp SASL.
4. **Gọi `getNegotiatedProperty` khi exchange chưa hoàn tất:** API có thể ném `IllegalStateException`. Sửa: kiểm tra `isComplete()` và outcome thành công của giao thức.
5. **Gọi `wrap/unwrap` với QOP `auth`:** API chỉ cho phép sau khi integrity/privacy đã được thương lượng. Sửa: không tự gọi security layer khi nó không tồn tại.
6. **Sai framing hoặc kích thước:** thiếu 4-byte length, vượt max frame, hoặc giả định raw và protected buffer dài bằng nhau. Sửa: tuân thủ protocol, kiểm tra độ dài **trước** cấp phát và chia nhỏ payload đúng raw-send limit.
7. **Tiếp tục khi `unwrap` thất bại:** xử lý dữ liệu lỗi như plaintext làm mất bảo đảm integrity. Sửa: dừng xử lý, đóng kết nối.
8. **GSSAPI chưa sẵn sàng:** principal/Kerberos ticket/service name/hostname/provider không hợp lệ. Sửa: chẩn đoán xác thực/context trước khi xét negotiated QOP.
9. **Tin rằng SASL layer phủ mọi byte:** handshake và metadata ngoài phạm vi có thể không được layer bảo vệ. Sửa: đánh giá rõ phạm vi thông điệp, áp dụng TLS khi cần.
10. **Dùng DIGEST-MD5 vì từng hỗ trợ ba mức:** RFC 6331 đã obsoleted mechanism này. Sửa: chọn giải pháp được duy trì và kiểm chứng.

### Checklist kiểm thử tích hợp

- Ghi nhận mechanism chọn thực tế, provider/version, QOP yêu cầu của hai phía, **QOP thương lượng**, và trạng thái TLS; không log password, raw ticket/token hoặc plaintext nhạy cảm.
- Test một phiên đạt yêu cầu và một phiên bị downgrade/không đủ QOP; trường hợp không đủ **phải thất bại trước khi gửi payload nghiệp vụ**.
- Với `auth-int`, can thiệp protected frame để xác minh bị từ chối; không suy ra confidentiality từ việc test integrity thành công.
- Với `auth-conf`, xác minh dữ liệu đi qua `wrap/unwrap`, thử frame sai độ dài và frame lỗi, kiểm tra xử lý đóng kết nối.
- Test payload lớn để kiểm tra overhead, `MAX_BUFFER`, `RAW_SEND_SIZE`, chunking và giới hạn nhận.
- Kiểm tra TLS và danh tính máy chủ **riêng** nếu hệ thống dựa vào bảo vệ transport.

## 8. Bài tập tình huống và đáp án

**A.** Client yêu cầu `auth-conf,auth-int`, server chỉ có `auth-int`; chính sách ứng dụng bắt buộc bí mật dữ liệu trong SASL layer. **Đáp án:** Có thể chọn `auth-int` về mặt cơ chế, nhưng ứng dụng phải từ chối vì không có confidentiality.

**B.** PLAIN chạy qua TLS và Java trả QOP `auth`. Có mâu thuẫn không? **Không:** PLAIN chỉ xác thực; TLS bảo vệ transport. Hai khái niệm ở hai lớp khác nhau.

**C.** Phiên đã chọn `auth-conf` nhưng ứng dụng viết thẳng payload vào socket, bỏ qua security layer mà protocol yêu cầu. Dữ liệu đã an toàn chưa? **Chưa:** QOP không tự bảo vệ byte đi vòng qua `wrap`.

**D.** `getNegotiatedProperty(Sasl.QOP)` trả `null` sau authentication thành công. Có thể giả sử `auth-conf` không? **Không:** thuộc tính không có/không áp dụng; nếu cần SASL security layer thì từ chối hoặc điều tra provider.

**E.** `SCRAM-SHA-256-PLUS` có chữ “PLUS”. Nó có SASL `auth-conf` không? **Không:** PLUS là cơ chế channel binding với kênh bên ngoài, không phải security layer bảo vệ payload.

## 9. Tài liệu chuẩn và Java API

- [RFC 4422 – SASL, §3.7 Security Layers](https://www.rfc-editor.org/rfc/rfc4422.html): thời điểm cài đặt layer, framing 4 byte, xử lý lỗi và giới hạn buffer.
- [RFC 4752 – GSSAPI/Kerberos V5 SASL mechanism](https://www.rfc-editor.org/rfc/rfc4752.html): thương lượng no layer/integrity/confidentiality và điều kiện GSS context.
- [RFC 4616 – PLAIN](https://www.rfc-editor.org/rfc/rfc4616.html): PLAIN không cung cấp security layer.
- [RFC 5802 – SCRAM](https://www.rfc-editor.org/rfc/rfc5802.html), [RFC 7677 – SCRAM-SHA-256](https://www.rfc-editor.org/rfc/rfc7677.html): SCRAM không thương lượng security layer, channel binding là thuộc tính khác.
- [RFC 6331 – Moving DIGEST-MD5 to Historic](https://www.rfc-editor.org/rfc/rfc6331.html): cảnh báo đối với mechanism di sản.
- [Java SE 21 `Sasl` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.security.sasl/javax/security/sasl/Sasl.html), [`SaslClient` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.security.sasl/javax/security/sasl/SaslClient.html) và [Java 17 SASL programming guide](https://docs.oracle.com/en/java/javase/17/security/java-sasl-api-programming-and-deployment-guide1.html): tên thuộc tính, danh sách ưu tiên, kết quả negotiated, `wrap/unwrap` và provider GSSAPI.

**Ghi nhớ:** `auth` chỉ xác thực; `auth-int` thêm integrity; `auth-conf` thêm confidentiality. Mức QOP **thực nhận** phụ thuộc mechanism, provider, hai đầu và giao thức. Không thay thế kiểm chứng kết quả bằng cấu hình mong muốn, và không xem TLS security là một SASL QOP khác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sasl-gss-api-005">18.5 GSSManager, GSSName, GSSCredential và GSSContext</a>

<details>
<summary>Xem chi tiết</summary>

# 18.5. GSSManager, GSSName, GSSCredential và GSSContext

## Mục tiêu và bối cảnh

Sau bài này, bạn có thể giải thích vai trò của bốn đối tượng cốt lõi trong Java GSS-API, tạo tên và ngữ cảnh bảo mật đúng phía client/server, hiểu vòng trao đổi token, kiểm tra thuộc tính bảo mật sau bắt tay, và nhận biết các lỗi Kerberos thường gặp.

**GSS-API (Generic Security Services API)** cung cấp giao diện chung cho các cơ chế xác thực và bảo vệ thông điệp, phổ biến nhất là Kerberos V5. Trong Java 17+, API chuẩn ở package `org.ietf.jgss` thuộc module `java.security.jgss`. Ứng dụng có thể dùng GSS-API để xác thực hai phía, kiểm tra tính toàn vẹn và (nếu cơ chế/ngữ cảnh hỗ trợ) bảo mật nội dung thông điệp.

GSS-API **không tự mở kết nối hay truyền token**: chương trình hoặc giao thức như HTTP/SASL phải chuyển token giữa hai đầu. GSS-API cũng không thay thế bước phân quyền nghiệp vụ sau khi biết người dùng là ai.

## 1. Bản đồ bốn đối tượng

| Thành phần | Trách nhiệm chính | Bạn thường làm gì? |
| --- | --- | --- |
| `GSSManager` | Factory, biết các cơ chế (mechanism) GSS khả dụng | `getInstance()`, `getMechs()`, `createName(...)`, `createCredential(...)`, `createContext(...)` |
| `GSSName` | Biểu diễn danh tính principal theo một kiểu tên, có thể ánh xạ sang tên riêng của cơ chế | Xác định dịch vụ đích `HTTP@api.example.com`; chuẩn hóa bằng `canonicalize(...)` khi cần |
| `GSSCredential` | Tay cầm đối với thông tin xác thực đã có của principal, dành cho khởi tạo hoặc chấp nhận context | Lấy credential của client hoặc service; kiểm tra thời hạn, cơ chế, vai trò |
| `GSSContext` | Phiên bảo mật giữa hai peer, ban đầu chưa được thiết lập | Trao đổi `initSecContext`/`acceptSecContext`; sau đó xác thực danh tính, `wrap`/`unwrap`, `getMIC`/`verifyMIC` |

Luồng phụ thuộc điển hình:

```text
             GSSManager
             ├── tạo GSSName: tên peer/service
             ├── lấy GSSCredential: quyền của chính tiến trình
             └── tạo GSSContext (mỗi kết nối/phiên xác thực)
                         │
       initiator.initSecContext(...) ↔ acceptor.acceptSecContext(...)
                         │
                 isEstablished() == true
                         │
           getSrcName/getTargName, wrap/unwrap, MIC
```

Cần tách bạch ba câu hỏi: **mình là ai** (credential), **mình đang nói chuyện với ai** (peer name), và **hai phía đã xác thực/đồng ý thuộc tính bảo vệ nào** (context). `GSSManager` liên kết chúng.

## 2. GSSManager: điểm vào và lựa chọn cơ chế

Tạo factory mặc định bằng `GSSManager.getInstance()`. Factory có thể liệt kê các cơ chế hiện có qua `getMechs()`, loại tên qua `getNamesForMech(Oid)` và cơ chế tương thích một loại tên qua `getMechsForName(Oid)`.

**OID (Object Identifier)** xác định một cơ chế bằng số, không phải bằng tên lớp Java:

- Kerberos V5 GSS-API: `1.2.840.113554.1.2.2`.
- SPNEGO (cơ chế thương lượng GSS, nếu provider hỗ trợ): `1.3.6.1.5.5.2`. SPNEGO không đồng nghĩa Kerberos thuần; nó có thể thương lượng cơ chế bên trong.
- Tham số `mech = null` trong `createContext` yêu cầu cơ chế mặc định; **không** có nghĩa luôn tự thương lượng SPNEGO.

Ví dụ Java chạy độc lập, không cần KDC, keytab hay ticket vì chỉ kiểm tra cơ chế:

**Tệp `GssCapabilities.java`**

```java
import java.util.Arrays;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

public class GssCapabilities {
    public static void main(String[] args) throws Exception {
        GSSManager manager = GSSManager.getInstance();
        Oid kerberos = new Oid("1.2.840.113554.1.2.2");

        System.out.println("Kerberos OID: " + kerberos);
        System.out.println("Kerberos supported: "
                + Arrays.asList(manager.getMechs()).contains(kerberos));
    }
}
```

Chạy trên JDK Java SE 17+ có triển khai GSS-API chuẩn:

```bash
javac GssCapabilities.java
java GssCapabilities
```

Stdout dự kiến:

```text
Kerberos OID: 1.2.840.113554.1.2.2
Kerberos supported: true
```

Việc có OID Kerberos trong danh sách **không** chứng minh máy đã có cấu hình realm, vé Kerberos hay tài khoản để đăng nhập. Nó chỉ xác nhận cơ chế có sẵn trong triển khai JDK.

## 3. GSSName: tên logic, kiểu tên và tên theo cơ chế

`GSSName` không phải chuỗi bất kỳ. Khi gọi `manager.createName(value, nameType)`, tham số `nameType` nói cách diễn giải `value`:

| Kiểu tên | Trường hợp | Điểm cần nhớ |
| --- | --- | --- |
| `GSSName.NT_HOSTBASED_SERVICE` | `"HTTP@api.example.com"` | Dạng GSS host-based là `service@host`, **khác** cách viết principal Kerberos `HTTP/api.example.com@EXAMPLE.COM` |
| `GSSName.NT_USER_NAME` | Tên người dùng ở dạng mà provider chấp nhận | Không suy đoán realm/quyền từ một chuỗi hiển thị |
| `GSSName.NT_EXPORT_NAME` | Nhập tên ở định dạng binary đã export | Dùng cùng định dạng export chuẩn; không coi bytes là UTF-8 tự do |

```java
GSSManager manager = GSSManager.getInstance();
Oid kerberos = new Oid("1.2.840.113554.1.2.2");

// Cần cấu hình Kerberos/realm thích hợp ngay cả khi mới tạo tên
// ở một số provider JDK:
GSSName service = manager.createName(
        "HTTP@api.example.com",
        GSSName.NT_HOSTBASED_SERVICE);

// Chuyển về mechanism name (MN) cho Kerberos khi cần:
GSSName kerberosService = service.canonicalize(kerberos);
```

Đoạn trên **là minh họa tích hợp**, không phải chương trình độc lập: một số triển khai sẽ cố phân giải realm ngay tại `createName` và ném `GSSException` nếu chưa cấu hình Kerberos, dù chưa hề gửi token. Không giả định việc tạo tên chắc chắn hoạt động trên máy trống.

`canonicalize(mechOid)` tạo representation riêng của cơ chế; `isMN()` cho biết đối tượng có phải mechanism name hay không. `GSSName.export()` có thể xuất một MN thành bytes để nhập lại bằng `NT_EXPORT_NAME`; không dùng `getBytes()` trên `toString()` để thay thế. Khi so khớp danh tính bảo mật, dùng quy tắc so sánh tên theo cơ chế và chính sách phân quyền rõ ràng; đừng chỉ cắt chuỗi trước/dưới dấu `@`.

**Cẩn thận với principal dịch vụ:** client có thể tạo tên host-based `HTTP@api.example.com`, trong khi phía Kerberos thực tế có thể cần service principal `HTTP/api.example.com@EXAMPLE.COM` và keytab tương ứng. Việc ánh xạ phụ thuộc realm, DNS và cấu hình nhà cung cấp; tên sai sẽ khiến xác thực thất bại dù client có ticket.

## 4. GSSCredential: danh tính và quyền sử dụng

`GSSCredential` giữ handle tới credential của một principal đã được môi trường cung cấp. `GSSManager.createCredential(...)` **không phải thao tác đăng nhập bằng mật khẩu**: Kerberos ticket hoặc khóa chấp nhận (ví dụ keytab/`Subject` có `KerberosKey`) cần được cung cấp qua JAAS/cơ chế mặc định trước hoặc trong quá trình provider lấy credential.

Ba cờ vai trò:

- `GSSCredential.INITIATE_ONLY`: dùng mở context với peer khác (thường là client).
- `GSSCredential.ACCEPT_ONLY`: dùng chấp nhận context từ client (thường là service).
- `GSSCredential.INITIATE_AND_ACCEPT`: dùng cho cả hai nếu cơ chế và credential thực tế hỗ trợ.

Ví dụ minh họa lấy credential chấp nhận phía server sau khi môi trường Kerberos và JAAS đã chuẩn bị:

```java
GSSManager manager = GSSManager.getInstance();
Oid kerberos = new Oid("1.2.840.113554.1.2.2");
GSSName service = manager.createName(
        "HTTP@api.example.com",
        GSSName.NT_HOSTBASED_SERVICE);

GSSCredential acceptorCred = manager.createCredential(
        service,
        GSSCredential.DEFAULT_LIFETIME,
        kerberos,
        GSSCredential.ACCEPT_ONLY);
try {
    // Tạo context và nhận token trong phiên xác thực...
    System.out.println(acceptorCred.getRemainingLifetime());
} finally {
    acceptorCred.dispose();
}
```

Đây là fragment Java, không có `main` và **không thể chạy thành công nếu thiếu service credential tương ứng**. Đối với client, có thể chỉ định credential đã lấy qua `INITIATE_ONLY` hoặc truyền `null` tại `GSSManager.createContext(peer, mech, null, lifetime)` để provider dùng danh tính khởi tạo mặc định trong ngữ cảnh hiện tại.

Có thể kiểm tra `getName()`, `getMechs()`, `getUsage(...)`, `getRemainingLifetime()`. Tuổi thọ yêu cầu là một **yêu cầu**, không phải cam kết; kiểm tra giá trị thực. `dispose()` khi credential không còn được dùng, tránh giữ handle/tài liệu xác thực lâu không cần thiết. Không ghi credential, keytab, ticket hoặc token vào log.

## 5. GSSContext: trạng thái phiên và hai phía

`GSSContext` biểu diễn một phiên bảo mật với peer, **chưa xác thực ngay tại thời điểm tạo**. Hai cách tạo phải phân biệt:

- **Initiator/client:** `createContext(GSSName peer, Oid mech, GSSCredential myCred, int lifetime)`. Đưa tên peer đích và cơ chế; credential `null` nghĩa là dùng danh tính mặc định của client nếu sẵn có.
- **Acceptor/server:** `createContext(GSSCredential myCred)`. Đưa credential nhận của server; ngữ cảnh sẽ xử lý token mà initiator gửi tới.

Trước lần đầu `initSecContext`, initiator có thể yêu cầu:

| Lệnh | Ý nghĩa | Sau khi thiết lập cần kiểm tra |
| --- | --- | --- |
| `requestMutualAuth(true)` | Client muốn xác thực cả server | `getMutualAuthState()` |
| `requestInteg(true)` | Muốn bảo vệ toàn vẹn | `getIntegState()` |
| `requestConf(true)` | Muốn cho phép bảo mật nội dung | `getConfState()` |
| `requestReplayDet(true)` | Muốn cơ chế phát hiện phát lại thông điệp | `getReplayDetState()` |
| `requestSequenceDet(true)` | Muốn kiểm tra thứ tự thông điệp | `getSequenceDetState()` |
| `requestCredDeleg(true)` | Muốn ủy quyền credential cho server | `getCredDelegState()`; chỉ bật khi thực sự cần |

Các lệnh `request...` là đề nghị, không chứng minh tính năng đã được kích hoạt. Provider và peer có thể quyết định thuộc tính cuối cùng. Nếu ứng dụng yêu cầu mutual authentication/integrity/confidentiality, hãy **từ chối phiên** khi thuộc tính thực tế sau thiết lập không đáp ứng chính sách. Không yêu cầu credential delegation theo mặc định vì điều này mở rộng đặc quyền của bên nhận.

`setChannelBinding(ChannelBinding)` (nếu giao thức hỗ trợ) được gọi **trước bắt tay ở cả hai phía** để ràng buộc xác thực với thông tin kênh bên dưới và giảm nguy cơ chuyển tiếp token giữa kênh. Hai phía phải dùng dữ liệu binding tương thích; cấu hình sai có thể khiến xác thực thất bại.

Sau khi `isEstablished() == true`, context cung cấp `getSrcName()` (initiator), `getTargName()` (acceptor), `getMech()` và trạng thái dịch vụ bảo mật. **Xác thực danh tính không tự quyết định quyền truy cập:** server phải tự kiểm tra principal vừa được xác thực với ACL/role/tenant/endpoint phù hợp.

## 6. Minh họa tạo client và server context bằng Java 17+

Tệp dưới đây **biên dịch được** và thể hiện bốn đối tượng. Các phương thức chỉ được gọi trong ứng dụng đã cấu hình Kerberos và có cơ chế vận chuyển token; chúng không chứa `main` hay giả vờ xác thực thành công khi thiếu KDC/ticket/keytab.

**Tệp `GssSetup.java`**

```java
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

public final class GssSetup {
    private GssSetup() {}

    private static Oid kerberos() throws GSSException {
        return new Oid("1.2.840.113554.1.2.2");
    }

    public static GSSContext newClientContext(String serviceHost)
            throws GSSException {
        GSSManager manager = GSSManager.getInstance();
        GSSName server = manager.createName(
                "HTTP@" + serviceHost, GSSName.NT_HOSTBASED_SERVICE);

        // null: dùng initiator credential mặc định trong môi trường hiện tại.
        GSSContext context = manager.createContext(
                server, kerberos(), null, GSSContext.DEFAULT_LIFETIME);

        context.requestMutualAuth(true);
        context.requestInteg(true);
        context.requestConf(true);
        context.requestReplayDet(true);
        context.requestSequenceDet(true);
        return context;
    }

    public static GSSCredential newServerCredential(String serviceHost)
            throws GSSException {
        GSSManager manager = GSSManager.getInstance();
        GSSName service = manager.createName(
                "HTTP@" + serviceHost, GSSName.NT_HOSTBASED_SERVICE);
        return manager.createCredential(
                service,
                GSSCredential.DEFAULT_LIFETIME,
                kerberos(),
                GSSCredential.ACCEPT_ONLY);
    }

    public static GSSContext newServerContext(GSSCredential credential)
            throws GSSException {
        return GSSManager.getInstance().createContext(credential);
    }
}
```

Kiểm tra cú pháp:

```bash
javac GssSetup.java
```

Ví dụ luồng sở hữu tài nguyên ở service: chuẩn bị `GSSCredential` trong `Subject` đã đăng nhập, tạo một `GSSContext` **riêng cho mỗi phiên**, dùng context cho vòng trao đổi token và thao tác thông điệp, rồi `context.dispose()`. Chỉ `credential.dispose()` sau khi các phiên dùng credential đó đã kết thúc; không hủy credential còn đang dùng chung. Không chia sẻ một `GSSContext` cho nhiều client hoặc các luồng xử lý độc lập.

## 7. Thiết lập context là vòng trao đổi token, không phải một lời gọi

Mô hình hai phía:

```text
Client / initiator                          Server / acceptor
  initSecContext(input ban đầu rỗng)
         │ ---- token A ------------------> acceptSecContext(token A)
         │ <--------------- token B ------ (nếu có)
  initSecContext(token B)
         │ ---- token C (nếu có) --------> acceptSecContext(token C)
         · · · tới khi hai phía đều hoàn tất ngữ cảnh · · ·
```

Quy tắc triển khai:

1. Client gọi `initSecContext(byte[] inputBuf, int offset, int len)` lần đầu, thông thường với token đầu vào dài 0. Nếu có output token (khác `null`), **gửi sang server**, kể cả khi `isEstablished()` của client đã là `true`.
2. Server nhận nguyên vẹn một token, gọi `acceptSecContext(...)`, gửi output token nếu có; lặp lại nếu context còn cần token tiếp.
3. Client xử lý token mới qua `initSecContext(...)`; hai phía kiểm tra trạng thái của **context riêng**. Số lượt trao đổi phụ thuộc cơ chế, tùy chọn mutual auth và môi trường.
4. Ứng dụng tự định nghĩa cách chuyển bytes: ví dụ length-prefix trên TCP hoặc trường token của giao thức. Không chuyển nhị phân bằng `new String(token)` rồi khôi phục theo charset; nếu giao thức văn bản thì mã hóa Base64 đúng chuẩn.
5. `null` output nghĩa là không có token cần gửi trong lượt đó. Không tự gửi token giả/chuỗi rỗng thay cho `null`. Không mặc định cả hai phía luôn hoàn tất trong đúng một round-trip.

Ví dụ khung xử lý token trên client. `TokenChannel` là adapter **phải được ứng dụng hiện thực** (đóng gói, giới hạn kích thước, nhận/gửi đầy đủ và xử lý timeout), vì GSS-API không cung cấp socket:

```java
import java.io.IOException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;

public final class GssTokenFlow {
    private GssTokenFlow() {}

    public interface TokenChannel {
        void send(byte[] token) throws IOException;
        byte[] receive() throws IOException;
    }

    public static void initiate(GSSContext context, TokenChannel channel)
            throws GSSException, IOException {
        byte[] incoming = new byte[0];

        while (!context.isEstablished()) {
            byte[] outgoing = context.initSecContext(
                    incoming, 0, incoming.length);

            // Một phía có thể isEstablished() == true nhưng vẫn cần
            // gửi token cuối cùng cho phía kia.
            if (outgoing != null) {
                channel.send(outgoing);
            }
            if (!context.isEstablished()) {
                incoming = channel.receive(); // trả về đúng một token đầy đủ
                if (incoming == null) {
                    throw new IOException("Peer closed during GSS handshake");
                }
            }
        }
    }

    public static void accept(GSSContext context, TokenChannel channel)
            throws GSSException, IOException {
        while (!context.isEstablished()) {
            byte[] incoming = channel.receive();
            if (incoming == null) {
                throw new IOException("Peer closed during GSS handshake");
            }
            byte[] outgoing = context.acceptSecContext(
                    incoming, 0, incoming.length);
            if (outgoing != null) {
                channel.send(outgoing);
            }
        }
    }
}
```

`GssTokenFlow.java` biên dịch được bằng `javac GssTokenFlow.java` nhưng **không tự chạy**: còn cần hiện thực `TokenChannel`, credential, giao thức kết thúc handshake và chính sách timeout. Đây là khung tuần tự cho một phiên; khi tích hợp HTTP Negotiate, SASL hoặc giao thức có nhiều trạng thái, phải tuân thủ quy tắc điều khiển token của giao thức đó. Đừng giả định việc một đầu có `isEstablished() == true` chính là xác nhận ứng dụng ở đầu kia đã xử lý hết token cuối.

Sau khi hoàn tất, có thể kiểm tra:

```java
if (!context.isEstablished()
        || !context.getMutualAuthState()
        || !context.getIntegState()) {
    throw new SecurityException("GSS context does not meet policy");
}
GSSName authenticatedClient = context.getSrcName(); // ở phía server
GSSName authenticatedServer = context.getTargName(); // ở phía client
// Tiếp theo phải kiểm tra principal với chính sách ứng dụng.
```

Mã trên là fragment chạy **sau** handshake; nếu chính sách của ứng dụng bắt buộc confidentiality, kiểm tra thêm `getConfState()` và trạng thái privacy thực tế của từng thông điệp.

## 8. Bảo vệ dữ liệu sau khi xác thực

Khi context đã sẵn sàng (thông thường sau `isEstablished()`; API còn có `isProtReady()` cho một số cơ chế), các API xử lý dữ liệu gồm:

- `wrap(data, off, len, MessageProp)`: tạo token bảo vệ toàn vẹn và có thể mã hóa nội dung. `new MessageProp(0, true)` yêu cầu QOP mặc định và privacy; sau `wrap` phải xem lại `getPrivacy()` vì giá trị thực tế có thể khác yêu cầu.
- `unwrap(token, off, len, MessageProp)`: giải mã/xác minh token do context ở peer tạo và trả dữ liệu. Đọc trạng thái trong `MessageProp` để biết cách xử lý.
- `getMIC(data, ..., MessageProp)` tạo mã kiểm tra toàn vẹn (**không** mã hóa hay gói kèm plaintext); `verifyMIC(mic, ..., data, ..., MessageProp)` xác minh ở peer.
- `getWrapSizeLimit(...)` giúp ước lượng kích thước tối đa dữ liệu có thể đưa vào `wrap` với giới hạn token đầu ra của giao thức.

Minh họa hai context **đã được thiết lập và tương ứng với cùng một phiên**:

```java
import java.nio.charset.StandardCharsets;
import org.ietf.jgss.MessageProp;

// clientContext và serverContext là hai context ĐÃ XÁC THỰC.
// Đây là fragment, không phải chương trình chạy độc lập.
byte[] data = "Hello GSS".getBytes(StandardCharsets.UTF_8);

MessageProp outbound = new MessageProp(0, true);
byte[] protectedToken = clientContext.wrap(
        data, 0, data.length, outbound);
if (!outbound.getPrivacy()) {
    throw new SecurityException("Confidentiality was required");
}

// Chuyển protectedToken nguyên vẹn qua TokenChannel/protocol...
MessageProp inbound = new MessageProp(0, false);
byte[] plain = serverContext.unwrap(
        protectedToken, 0, protectedToken.length, inbound);
String received = new String(plain, StandardCharsets.UTF_8);
```

Với trường hợp chỉ cần MIC, tạo MIC trên một context và xác minh với **chính dữ liệu** ở context bên kia:

```java
byte[] mic = clientContext.getMIC(
        data, 0, data.length, new MessageProp(0, false));
serverContext.verifyMIC(
        mic, 0, mic.length,
        data, 0, data.length,
        new MessageProp(0, false));
```

Kết quả không phải chuỗi cố định để đưa vào stdout: token/MIC phụ thuộc khóa phiên, cơ chế và trạng thái context. Dữ liệu bị thay đổi hoặc token không hợp lệ phải được xử lý như lỗi xác minh, không âm thầm tiếp tục.

**GSS bảo vệ từng token theo các thuộc tính context**, còn bảo vệ toàn bộ kênh (metadata, framing, timeout, yêu cầu giao thức, những endpoint không dùng GSS) là quyết định kiến trúc riêng. Nếu dùng TLS cùng GSS, đừng tự suy rằng GSS đã ràng buộc với TLS khi chưa cấu hình channel binding tương thích.

## 9. Điều kiện môi trường thực tế và JAAS

Muốn chạy xác thực Kerberos thật, thường cần các điều kiện sau:

1. **KDC/realm và cấu hình máy:** `krb5.conf`/`krb5.ini` hoặc thuộc tính JVM tương ứng, realm có thể phân giải và đồng hồ máy không lệch vượt giới hạn Kerberos.
2. **Danh tính đích:** service principal phù hợp với host/canonicalization. Ví dụ service `HTTP/api.example.com@EXAMPLE.COM`; DNS, alias và principal phải nhất quán với chính sách triển khai.
3. **Client credential:** ticket từ quá trình login/SSO, cache hoặc `Subject` đã được JAAS cung cấp, tùy cách triển khai.
4. **Server credential:** khóa phục vụ principal phù hợp trong keytab hoặc credential an toàn khác. Tiến trình server phải có quyền đọc nguồn credential, nhưng keytab không được đưa vào Git hoặc log.
5. **Giao thức trao đổi:** vận chuyển token có framing, giới hạn kích thước, timeout, xử lý lỗi và kiểm tra danh tính sau xác thực.

Ví dụ **JAAS config minh họa cho môi trường Kerberos đã được quản trị**:

**Tệp `jaas.conf` (đường dẫn/keytab/principal chỉ là mẫu):**

```properties
KrbClient {
    com.sun.security.auth.module.Krb5LoginModule required
    useTicketCache=true
    doNotPrompt=true;
};

KrbService {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="/secure/keytabs/http-api.keytab"
    principal="HTTP/api.example.com@EXAMPLE.COM"
    storeKey=true
    doNotPrompt=true
    isInitiator=false;
};
```

Ứng dụng gọi `LoginContext("KrbClient")` hoặc `LoginContext("KrbService")` và thực hiện thao tác GSS trong `Subject` thích hợp (ví dụ `Subject.doAs(...)` trên Java 17) khi dựa vào mô hình JAAS/Subject. Đường dẫn `-Djava.security.auth.login.config=/secure/jaas.conf` chọn cấu hình JAAS; `-Djava.security.krb5.conf=/secure/krb5.conf` chọn cấu hình Kerberos. Các tùy chọn thực tế phụ thuộc môi trường chạy, JDK/provider và cách dùng ticket cache hoặc keytab.

Lưu ý: `javax.security.auth.useSubjectCredsOnly` ảnh hưởng cách triển khai Kerberos tìm credential trong Java. Không đổi nó thành `false` như một “cách chữa mọi lỗi”; trước hết xác định nguồn credential hợp lệ, `Subject` đang dùng và chính sách bảo mật.

## 10. Các lỗi và hiểu nhầm thường gặp

| Triệu chứng | Nguyên nhân cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| `GSSException: NO_CRED` hoặc “Failed to find any Kerberos tgt” | Ticket/keytab thiếu, JAAS chưa login, dùng sai `Subject`, sai usage initiator/acceptor | Kiểm tra login và credential thực tế; chọn đúng vai trò |
| “Cannot locate default realm” ngay khi `createName` | Realm Kerberos chưa được cấu hình | Kiểm tra `krb5.conf`/`krb5.ini` và cách ánh xạ realm |
| `BAD_NAME` hoặc principal không khớp | Dùng sai kiểu tên, service host hoặc realm | Phân biệt `HTTP@host` với `HTTP/host@REALM`; xác thực service principal có thật |
| `BAD_MECH` | Chỉ định OID không được provider hỗ trợ | Đối chiếu `manager.getMechs()` và cấu hình provider |
| `DEFECTIVE_TOKEN` / token không hợp lệ | Token bị cắt, sửa, mã hóa charset sai hoặc chuyển token của phiên khác | Framing nhị phân đúng, gói Base64 khi cần, không tái sử dụng context/token |
| Server không giải mã được ticket / lỗi integrity Kerberos | Keytab, SPN, kvno, encryption type hoặc realm không khớp | Kiểm tra service identity và khóa được cấp bởi quản trị Kerberos |
| Lỗi clock skew/ticket expired | Đồng hồ lệch hoặc ticket quá hạn | Đồng bộ thời gian, cấp lại ticket hợp lệ |
| `isEstablished()` nhưng thiếu thuộc tính bắt buộc | Lời gọi `request...` không bảo đảm mọi thuộc tính đã được chấp nhận | Kiểm tra `get...State()`, từ chối phiên không đạt policy |

Các trường hợp quan trọng khác:

- **Không so sánh token với chuỗi cố định.** Token có thể khác mỗi phiên, ngay cả khi cùng hai principal.
- **Không trộn context:** token từ phiên A không được đưa vào context của phiên B; không share context cho nhiều kết nối cùng lúc.
- **Không bỏ qua kết quả bảo vệ:** `wrap` có thể không cấp privacy như yêu cầu; đọc `MessageProp.getPrivacy()`; xử lý `GSSException` và các trạng thái supplementary của `MessageProp` liên quan replay/sequence nếu ứng dụng sử dụng.
- **Không dùng tên do client tự khai làm danh tính xác thực:** server chỉ tin `getSrcName()` sau khi chứng thực thành công và kiểm tra quyền.
- **Không mặc định có delegation:** `requestCredDeleg(true)` có tác động bảo mật lớn; chỉ sử dụng với yêu cầu rõ và giới hạn quyền phù hợp.
- **Không dùng API stream cũ chỉ vì tiện:** trên Java 17, ưu tiên phiên bản `byte[]` của `initSecContext`, `acceptSecContext`, `wrap` và `unwrap`; các overload stream đã bị deprecated từ Java 11.
- **Không nhầm GSS-API với SASL:** SASL có cơ chế tên `GSSAPI` và quy tắc giao thức riêng; nó có thể dùng GSS bên dưới nhưng `GSSContext` đơn lẻ không tự triển khai toàn bộ SASL, authorization identity hay thương lượng security layer.

## 11. Tình huống áp dụng và quy trình kiểm tra

**Tình huống:** service Java A cần gọi service B bằng Kerberos.

1. Xác định service B qua danh tính host-based đúng, ví dụ `HTTP@api.example.com`, và chọn cơ chế GSS Kerberos theo OID.
2. A có ticket khởi tạo hợp lệ, B có keytab/credential nhận phù hợp. B tạo `GSSCredential.ACCEPT_ONLY`, mỗi kết nối tạo một context nhận.
3. A tạo initiator context với `GSSName` của B, request mutual auth và các thuộc tính cần thiết.
4. Hai bên trao đổi binary token trong giao thức cho đến khi hoàn tất; chuyển mọi output token cần gửi ngay cả khi trạng thái local đã established.
5. A xác minh đã xác thực server và thuộc tính context cần thiết; B lấy `getSrcName()` rồi quyết định người gọi được phép thực hiện hành động nào.
6. Nếu phải bảo vệ nội dung ứng dụng, dùng `wrap/unwrap` hoặc `MIC` theo yêu cầu; kiểm tra privacy và lỗi xác minh. Cuối phiên gọi `dispose()`, hủy credential khi hết người dùng.

**Checklist QA khi triển khai:** đã dùng đúng kiểu tên và OID? credential đúng role? đã cấu hình realm, ticket, keytab? token được truyền nguyên vẹn và bounded? có timeout? đã xác minh thuộc tính thực tế và danh tính? có kiểm tra authorization? các context/credential được giải phóng hợp lý? có tránh log secret và tránh bật delegation vô cớ?

## 12. Câu hỏi tự kiểm tra

1. Vì sao tạo được `GSSManager` và thấy OID Kerberos vẫn chưa chứng minh đăng nhập Kerberos thành công?
2. `HTTP@api.example.com` là biểu diễn của loại tên nào? Vì sao khác `HTTP/api.example.com@EXAMPLE.COM`?
3. Sự khác nhau giữa `GSSCredential.ACCEPT_ONLY` và `INITIATE_ONLY` là gì? Server có thể dùng credential khởi tạo để `acceptSecContext` một cách mặc định không?
4. Tại sao phải gửi output token cuối ngay cả khi `isEstablished()` ở phía gửi đã là `true`?
5. `requestConf(true)` có đủ chứng minh thông điệp đã được mã hóa không? Cần kiểm tra hai nơi nào?
6. Vì sao xác thực thành công bằng `GSSContext` chưa đủ để quyết định user có quyền xem tài nguyên?
7. `getMIC/verifyMIC` khác `wrap/unwrap` ở điểm nào?
8. Khi phát sinh lỗi “Cannot locate default realm” ngay lúc tạo `GSSName`, bạn sẽ kiểm tra cấu hình nào trước?

## Tài liệu chuẩn

- [Java SE 17 — `org.ietf.jgss` package](https://docs.oracle.com/en/java/javase/17/docs/api/java.security.jgss/org/ietf/jgss/package-summary.html)
- [Java SE 17 — `GSSManager`](https://docs.oracle.com/en/java/javase/17/docs/api/java.security.jgss/org/ietf/jgss/GSSManager.html), [`GSSName`](https://docs.oracle.com/en/java/javase/17/docs/api/java.security.jgss/org/ietf/jgss/GSSName.html), [`GSSCredential`](https://docs.oracle.com/en/java/javase/17/docs/api/java.security.jgss/org/ietf/jgss/GSSCredential.html), [`GSSContext`](https://docs.oracle.com/en/java/javase/17/docs/api/java.security.jgss/org/ietf/jgss/GSSContext.html).
- [RFC 2743 — Generic Security Service API](https://www.rfc-editor.org/rfc/rfc2743); [RFC 2853 — Java bindings](https://www.rfc-editor.org/rfc/rfc2853); [RFC 4178 — SPNEGO](https://www.rfc-editor.org/rfc/rfc4178).

**Kết nối ý chính:** `GSSManager` tạo các đối tượng; `GSSName` xác định peer; `GSSCredential` đại diện credential của chính phía đang chạy; `GSSContext` thực hiện xác thực và, sau khi thiết lập, cung cấp thao tác bảo vệ thông điệp. Tính an toàn phụ thuộc cả việc cấu hình danh tính và việc kiểm tra trạng thái/chính sách sau xác thực.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sasl-gss-api-006">18.6 Kerberos, KDC, service principal, ticket, keytab và quan hệ với Krb5LoginModule</a>

<details>
<summary>Xem chi tiết</summary>

# 18.6. Kerberos, KDC, service principal, ticket, keytab và quan hệ với Krb5LoginModule

## 1. Mục tiêu và bối cảnh

Sau bài này, bạn cần giải thích được:

- Kerberos V5 xác thực **ai đang yêu cầu dịch vụ nào** bằng vé (ticket) và khóa đối xứng, thay vì gửi mật khẩu của người dùng tới từng dịch vụ.
- KDC, realm, client principal, service principal, TGT, service ticket, session key và keytab có trách nhiệm khác nhau.
- Luồng AS → TGS → application service, bao gồm bước chứng minh client sở hữu session key.
- Khi nào ứng dụng Java dùng ticket cache, khi nào service cần keytab, và cách JAAS Krb5LoginModule đưa thông tin vào Subject.
- Vì sao đăng nhập JAAS thành công chưa đồng nghĩa HTTP/SASL/GSS-API đã xác thực với dịch vụ đích.

Phạm vi là **Kerberos trong Java**, tập trung vào quan hệ giữa hạ tầng Kerberos và Krb5LoginModule. Ví dụ hostname, realm và đường dẫn dưới đây chỉ minh họa; chúng không đại diện cho một KDC thực sự.

## 2. Bức tranh tổng thể: danh tính, realm và KDC

**Principal** là tên danh tính trong hệ Kerberos. Các dạng điển hình:

~~~text
alice@EXAMPLE.COM                         # người dùng (client principal)
HTTP/api.example.com@EXAMPLE.COM          # dịch vụ HTTP (service principal)
host/server.example.com@EXAMPLE.COM       # dịch vụ host
krbtgt/EXAMPLE.COM@EXAMPLE.COM            # dịch vụ cấp vé của realm
~~~

- **Realm**: miền quản lý danh tính và khóa Kerberos; thường viết hoa theo quy ước, ví dụ EXAMPLE.COM. Realm không tự động đồng nghĩa DNS domain, dù nhiều hệ thống cố tình đặt tên giống nhau.
- **Client principal**: danh tính muốn sử dụng dịch vụ, ví dụ alice@EXAMPLE.COM.
- **Service principal**: danh tính server được client yêu cầu xác thực, thường có dạng service/hostname@REALM. Với HTTP Negotiate thường gặp HTTP/hostname@REALM.
- **KDC (Key Distribution Center)**: dịch vụ tin cậy phát hành vé, gồm logic Authentication Service (AS) và Ticket-Granting Service (TGS). KDC biết các principal và khóa thích hợp; với Active Directory, domain controller thường đảm nhiệm vai trò KDC.
- **Application server/acceptor**: dịch vụ nhận vé do client gửi, kiểm tra vé bằng khóa riêng của service principal.

Không phải mỗi bước đều gửi yêu cầu tới KDC. Khi client đã có TGT và service ticket còn hiệu lực, có thể tái sử dụng vé; server không cần liên hệ KDC cho từng request xác thực thông thường.

## 3. Luồng Kerberos V5 từ đăng nhập tới gọi dịch vụ

Giả sử Alice muốn gọi HTTP/api.example.com@EXAMPLE.COM:

~~~text
Alice / Java client                    KDC (AS + TGS)              HTTP service
       |                                      |                          |
       |--- AS-REQ (xin TGT) ---------------->|                          |
       |<-- AS-REP (TGT + khóa phiên) --------|                          |
       |                                      |                          |
       |--- TGS-REQ (TGT, xin vé cho HTTP) -->|                          |
       |<-- TGS-REP (service ticket + key) ---|                          |
       |                                      |                          |
       |--- AP-REQ (service ticket + authenticator) ------------------->|
       |<-- AP-REP (nếu yêu cầu xác thực hai chiều) --------------------|
       |<================= giao thức ứng dụng được bảo vệ =============>|
~~~

**Bước 1 — AS exchange:** client chứng minh danh tính với AS, thường bằng cơ chế pre-authentication liên quan tới khóa của principal hoặc một phương thức được KDC hỗ trợ. KDC trả về **TGT** (Ticket-Granting Ticket) cùng thông tin khóa phiên dành cho client. TGT chứa phần được mã hóa cho TGS, nên client không thể tự ý sửa nó.

**Bước 2 — TGS exchange:** client dùng TGT để xin một **service ticket** đúng cho HTTP/api.example.com@EXAMPLE.COM. TGS cấp service ticket có phần được mã hóa bằng khóa dài hạn của service principal và cung cấp session key tương ứng cho client theo cách được bảo vệ.

**Bước 3 — AP exchange:** client gửi service ticket **cùng authenticator mới** tới HTTP server. Server dùng khóa của chính service principal (thường lấy từ keytab) để giải mã phần ticket liên quan; sau đó dùng session key để kiểm tra authenticator, thời gian và các điều kiện khác. Một ticket đứng riêng chưa chứng minh người gửi sở hữu session key.

**Bước 4 — xác thực hai chiều (tùy chọn):** service có thể phản hồi AP-REP để client xác thực phía server. Sau khi cơ chế xác thực thiết lập xong, giao thức ứng dụng quyết định cách bảo vệ tính toàn vẹn/bảo mật của thông điệp: GSS-API có khả năng wrap/unwrap, còn HTTP có thể sử dụng TLS. Kerberos không mặc nhiên mã hóa mọi byte của HTTP payload.

**Lưu ý:** tên thông điệp trên mô tả giao thức. Ứng dụng dùng GSS-API/SASL thường không tự đóng gói thủ công AS-REQ, TGS-REQ hay AP-REQ.

## 4. Phân biệt TGT, service ticket, session key và keytab

| Thành phần | Nơi sử dụng chính | Ý nghĩa | Điều không nên nhầm |
| --- | --- | --- | --- |
| TGT | Client ↔ TGS | Cho phép xin vé tới những dịch vụ khác trong thời hạn chính sách | Không phải vé để trình trực tiếp cho HTTP service thông thường |
| Service ticket | Client → đúng service | Bằng chứng do KDC cấp cho service principal cụ thể | Không phải mật khẩu và không dùng tùy ý cho service khác |
| Session key | Client và bên kiểm tra ticket | Dùng chứng minh sở hữu vé, xác thực thông điệp và có thể bảo vệ phiên | Không phải khóa dài hạn trong keytab |
| Keytab | Thường tại server, đôi khi client không tương tác | Tệp chứa khóa dài hạn và metadata principal/KVNO/enctype để Kerberos dùng | Không chứa TGT hoặc danh sách service ticket |
| Ticket cache | Môi trường client/tiến trình | Lưu credential như TGT, service ticket để tái sử dụng | Không phải keytab; xóa cache không xoay khóa service |

Một **ticket** có thời hạn và thuộc tính như client, service, thời điểm hiệu lực và các cờ theo chính sách KDC. Đối với service ticket, phần ticket dành cho server được mã hóa để **service** kiểm tra. Client nhận được session key phù hợp nhưng không nhận khóa dài hạn của service.

**KVNO (Key Version Number)** giúp chọn phiên bản khóa đúng khi khóa của principal được đổi; **enctype** chỉ thuật toán mã hóa/kiểu khóa. Khi KDC cấp ticket theo một phiên bản khóa nhưng server chỉ có keytab cũ hoặc không hỗ trợ enctype tương thích, xác thực có thể thất bại. Không nên kết luận “có principal trong keytab là đủ” mà bỏ qua KVNO và enctype.

## 5. Service principal, hostname và ánh xạ tới keytab

Với dịch vụ ví dụ, ba phía phải thống nhất danh tính:

~~~text
Tên client yêu cầu:  HTTP/api.example.com@EXAMPLE.COM
Principal KDC biết:  HTTP/api.example.com@EXAMPLE.COM
Khóa server có:      keytab chứa khóa của HTTP/api.example.com@EXAMPLE.COM
~~~

Nếu client yêu cầu HTTP/alias.example.com nhưng KDC không có SPN đó, có thể không cấp được service ticket. Nếu KDC có principal nhưng server đọc keytab của principal hoặc phiên bản khóa khác, server không giải mã được ticket. DNS alias, reverse DNS, hostname canonicalization, load balancer và ánh xạ SPN của Active Directory cần được thiết kế nhất quán.

Một service principal có thể được đăng ký với tài khoản dịch vụ hoặc service account trong KDC/AD. Nó là **định danh Kerberos**; tên service principal không phải chính nó là user/password, URL đầy đủ hay chứng chỉ TLS.

**Nhiều instance:** các instance thực sự cùng phục vụ một SPN có thể cần quyền truy cập cùng tập khóa thích hợp, nhưng việc sao chép keytab phải được quản lý như phân phối bí mật. Đừng cho nhiều dịch vụ độc lập dùng chung principal chỉ vì tiện cấu hình.

## 6. Keytab hoạt động như thế nào?

Keytab chứa khóa bí mật của một hoặc nhiều principal, thường kèm KVNO và enctype. Server không cần nhúng mật khẩu vào mã Java; khi nhận AP-REQ, GSS/Kerberos có thể dùng khóa từ keytab để chấp nhận ticket.

Ví dụ quy trình quản trị ở môi trường có MIT Kerberos hoặc công cụ tương đương:

~~~bash
# Chạy trên host đã cấu hình Kerberos; tên file chỉ minh họa.
klist -k -e /etc/security/keytabs/http-api.keytab

# Kiểm tra vé hiện có của tài khoản client:
klist

# Client có quyền và TGT hợp lệ có thể xin service ticket:
kvno HTTP/api.example.com@EXAMPLE.COM
~~~

- Lệnh klist -k -e hiển thị danh tính, KVNO, enctype trong keytab; nó **không** kiểm tra rằng KDC hiện tại đã có đúng khóa.
- Lệnh kvno cần môi trường Kerberos thực, phân giải realm/KDC đúng và quyền lấy vé.
- Công cụ/tùy chọn cụ thể phụ thuộc MIT Kerberos, Heimdal, Windows hoặc hệ quản trị KDC.

Trong triển khai, chỉ tài khoản hệ điều hành chạy service cần đọc keytab; áp dụng permission tối thiểu (ví dụ 0600 trên Unix khi phù hợp), lưu bằng secret management và không commit vào Git, log hoặc gửi qua chat. Khi rotate key, đồng bộ KDC, KVNO và các instance; lập kế hoạch chồng lấp khóa trong thời gian ticket cũ còn hiệu lực theo khả năng KDC/keytab. Nếu keytab lộ, kẻ tấn công có thể giả danh service trong phạm vi khóa bị lộ.

## 7. Krb5LoginModule nằm ở đâu trong kiến trúc Java?

Trong Java, có ba lớp trách nhiệm cần tách bạch:

1. **JAAS LoginContext** kích hoạt các LoginModule theo tên cấu hình; javax.security.auth.login.LoginContext thực hiện login/commit/logout.
2. **com.sun.security.auth.module.Krb5LoginModule** làm việc với Kerberos (ticket cache, keytab, KDC hoặc callback tùy cấu hình), rồi đưa principal/credential vào javax.security.auth.Subject.
3. **GSS-API (org.ietf.jgss) hoặc SASL** dùng credential phù hợp để thiết lập phiên xác thực với dịch vụ thực tế. Ví dụ HTTP Negotiate/SPNEGO dùng GSS ở lớp giao thức HTTP; SASL GSSAPI dùng trong những giao thức có hỗ trợ SASL.

Krb5LoginModule **không phải KDC, không phát hành vé và không tự cài đặt HTTP Negotiate**. Nó là cầu nối giữa cơ chế Kerberos và môi trường JAAS của Java.

Khi login/commit thành công, Subject có thể có:

- **KerberosPrincipal** trong tập principals.
- **KerberosTicket** trong private credentials khi đăng nhập với vai trò initiator và có TGT.
- **KeyTab hoặc KerberosKey** trong private credentials nếu tùy chọn storeKey và nguồn khóa thích hợp được dùng.
- Những credential khác tùy cấu hình login và provider.

Chú ý private credentials là dữ liệu nhạy cảm. Không in ra nội dung Subject một cách tùy tiện, không serialize credential vào log hoặc API response.

## 8. Client (initiator) và server (acceptor) cần gì?

| Vai trò | Nguồn thông dụng | isInitiator | Mục tiêu |
| --- | --- | --- | --- |
| Người dùng/client đã kinit | Ticket cache có TGT | true (mặc định) | Có credential để xin service ticket và khởi tạo GSS context |
| Client daemon xác thực không tương tác | Keytab riêng và principal của daemon | true | Dùng khóa để lấy TGT từ KDC rồi gọi service |
| Server chỉ nhận Kerberos | Keytab của service principal, storeKey=true | false | Nạp khóa cho GSS acceptor; không cần lấy TGT chỉ để chấp nhận AP-REQ |

**TGT không thay thế keytab** trên server. Một HTTP acceptor thường cần service key để mở service ticket; việc server cũng chạy kinit không tự giải quyết được thiếu keytab hoặc SPN sai.

## 9. Tùy chọn quan trọng của Krb5LoginModule

| Tùy chọn | Ý nghĩa và điều kiện |
| --- | --- |
| principal | Danh tính muốn dùng; với acceptor nên chọn SPN cụ thể. Dấu * chỉ hợp lệ cho isInitiator=false và mở phạm vi chấp nhận theo các khóa trong keytab, cần cân nhắc kỹ |
| isInitiator | true để lấy credential khởi tạo giao tiếp; false cho acceptor-only, không thực hiện AS exchange để lấy TGT |
| useTicketCache | true để thử lấy TGT từ ticket cache (phù hợp client đã kinit) |
| ticketCache | Đường dẫn cache tùy chọn; chỉ dùng khi useTicketCache=true |
| useKeyTab | true để dùng khóa trong keytab; nếu chỉ khai báo keyTab mà thiếu useKeyTab=true là cấu hình không hợp lệ |
| keyTab | Đường dẫn keytab; nên nêu rõ và quản lý quyền file |
| storeKey | true để lưu keytab/khóa vào Subject private credentials, quan trọng cho acceptor |
| doNotPrompt | true để không hỏi mật khẩu tương tác; thất bại rõ ràng nếu không lấy được credential |
| renewTGT | Chính sách thử gia hạn TGT; chỉ hợp lệ cùng useTicketCache=true, không thay thế quy trình quản lý hạn vé |
| refreshKrb5Config | Cho phép nạp lại cấu hình Kerberos khi login (tùy yêu cầu vận hành) |
| debug | Thông tin chẩn đoán LoginModule; chỉ bật có kiểm soát và tắt sau khi xử lý |

Các ràng buộc dễ sai: **doNotPrompt=true** nhưng không có cache/keytab/nguồn credential hợp lệ; **storeKey=true** với chỉ ticket cache và không có nguồn khóa; **keyTab** nhưng thiếu **useKeyTab=true**; **renewTGT=true** nhưng thiếu **useTicketCache=true**. Cơ chế có thể thử cache rồi keytab rồi các nguồn khác tùy cấu hình; không nên cho phép nhắc password trong daemon production.

## 10. Cấu hình Kerberos và JAAS mẫu

Ví dụ krb5.conf: host KDC và realm phải được thay bằng thông tin do quản trị viên cung cấp.

~~~ini
[libdefaults]
    default_realm = EXAMPLE.COM
    dns_lookup_kdc = false

[realms]
    EXAMPLE.COM = {
        kdc = kdc.example.com
    }
~~~

Ví dụ jaas.conf gồm **hai entry riêng**, dùng tùy theo process:

~~~text
KrbClient {
    com.sun.security.auth.module.Krb5LoginModule required
    useTicketCache=true
    doNotPrompt=true
    isInitiator=true
    debug=false;
};

KrbAcceptor {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    keyTab="/etc/security/keytabs/http-api.keytab"
    principal="HTTP/api.example.com@EXAMPLE.COM"
    storeKey=true
    doNotPrompt=true
    isInitiator=false
    debug=false;
};
~~~

**KrbClient**: kỳ vọng trước đó đã có TGT hợp lệ trong cache mà JVM đọc được. Login thất bại nếu không có credential phù hợp; sample cố ý không hỏi password.

**KrbAcceptor**: nạp khóa của HTTP/api.example.com để ứng dụng có thể chấp nhận GSS token tới principal đó. Nó không tự tạo HTTP endpoint hoặc tự xử lý header Authorization; vẫn cần framework/handler hỗ trợ Negotiate hay giao thức tích hợp GSS.

Tùy runtime, đường dẫn ticket cache từ công cụ hệ điều hành và đường dẫn Java sử dụng có thể khác nhau. Trên Windows/AD, việc lấy credential có thể theo cơ chế hệ điều hành và JDK; hãy kiểm tra thực tế thay vì mặc định KRB5CCNAME hoạt động như mọi hệ Unix.

**Không đặt mật khẩu vào jaas.conf**, không lưu keytab trong source tree, không dùng account domain administrator để chạy service.

## 11. Ví dụ Java 17+: phân tích service principal mà không cần KDC

Tệp độc lập **PrincipalDemo.java**:

~~~java
import javax.security.auth.kerberos.KerberosPrincipal;

public class PrincipalDemo {
    public static void main(String[] args) {
        KerberosPrincipal principal = new KerberosPrincipal(
                "HTTP/api.example.com@EXAMPLE.COM",
                KerberosPrincipal.KRB_NT_SRV_HST);

        System.out.println(principal.getName());
        System.out.println(principal.getRealm());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PrincipalDemo.java
java PrincipalDemo
~~~

Stdout:

~~~text
HTTP/api.example.com@EXAMPLE.COM
EXAMPLE.COM
~~~

Constructor trên biểu diễn **tên danh tính và name type**, chưa xác thực với KDC, chưa mở keytab và chưa tạo ticket. Khởi tạo KerberosPrincipal thành công **không chứng minh** SPN đã được đăng ký trong KDC.

## 12. Ví dụ Java 17+: đăng nhập bằng JAAS và kiểm tra Subject

Tệp **JaasLoginDemo.java** (cần krb5.conf, jaas.conf và ticket cache thực mới login thành công):

~~~java
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import java.security.PrivilegedAction;

public class JaasLoginDemo {
    public static void main(String[] args) throws Exception {
        LoginContext context = new LoginContext("KrbClient");
        context.login();

        try {
            Subject subject = context.getSubject();

            subject.getPrincipals().forEach(principal ->
                    System.out.println("Principal: " + principal.getName()));

            Subject.doAs(subject, (PrivilegedAction<Void>) () -> {
                System.out.println("Run using authenticated Subject");
                return null;
            });
        } finally {
            context.logout();
        }
    }
}
~~~

Biên dịch:

~~~bash
javac JaasLoginDemo.java
~~~

Cách chạy tham khảo (trong máy đã cấu hình Kerberos):

~~~bash
java -Djava.security.auth.login.config=jaas.conf -Djava.security.krb5.conf=krb5.conf JaasLoginDemo
~~~

Nếu LoginContext.login() thất bại sẽ phát sinh LoginException (được khai báo qua throws Exception trong ví dụ); thường do thiếu TGT, cache không đọc được, lệch realm hoặc lỗi cấu hình. **Không có một stdout cố định cho ví dụ này**: principal phụ thuộc credential của môi trường, và thiếu KDC/cache hợp lệ sẽ không đi tới các lệnh in.

Subject.doAs chạy đoạn code dưới danh tính Subject; API tích hợp GSS có thể tra cứu credential liên quan. Mẫu chỉ in tên principal để minh họa, **chưa giao tiếp hoặc chứng thực với service HTTP**. Với môi trường Java đời mới hơn, cần kiểm tra hướng dẫn tương thích của phiên bản JDK cho API Subject và cơ chế gắn Subject vào ngữ cảnh thực thi.

## 13. Từ Subject tới GSS-API, HTTP Negotiate và SASL GSSAPI

Để thực sự dùng service ticket, ứng dụng cần cơ chế trao đổi token với server:

~~~text
LoginContext("KrbClient") → Subject có credential
        ↓
GSSManager / GSSContext (initiator)
        ↓ tạo GSS token cho service "HTTP@api.example.com"
HTTP client gửi Authorization: Negotiate <base64-token>
        ↓
HTTP server / GSSContext (acceptor) dùng keytab service
        ↓
Server xác thực principal; framework ánh xạ sang user/quyền ứng dụng
~~~

Tên host-based dạng **HTTP@api.example.com** trong GSS-API là một biểu diễn khác với service principal Kerberos **HTTP/api.example.com@EXAMPLE.COM**. GSS/Kerberos provider giải quyết ánh xạ thích hợp theo realm và cấu hình; không nên tự ghép chuỗi tùy tiện, nhất là khi có alias.

Một GSS security context có thể cần **nhiều vòng token** giữa client và server; đừng giả định đúng một AP-REQ/token cho mọi cơ chế (đặc biệt khi dùng SPNEGO). Sau context establishment, GSS có API bảo vệ thông điệp, còn việc ứng dụng có dùng chúng hay chỉ dựa trên TLS là quyết định của giao thức/triển khai.

Với **SASL GSSAPI**, một giao thức như LDAP hoặc messaging có thể đưa việc xác thực vào quá trình SASL negotiation. Thư viện/protocol driver điều khiển token; Krb5LoginModule vẫn phục vụ chuẩn bị credential. Không suy từ việc cấu hình JAAS thành công rằng LDAP/HTTP server đã cho phép thao tác: **authentication** và **authorization** là hai giai đoạn riêng.

## 14. Quy trình triển khai và kiểm tra theo thứ tự

1. **Xác định principal và realm**: chính xác client principal, service principal, hostname/SPN mà client yêu cầu, realm và đường đi tới KDC.
2. **Chuẩn bị KDC**: tài khoản/principal phải tồn tại; cấp khóa hoặc keytab bằng quy trình quản trị, theo chính sách enctype và KVNO.
3. **Chuẩn bị máy service**: đặt keytab dưới đường dẫn riêng, hạn chế quyền; xác minh nó có đúng SPN và các khóa hiện hành.
4. **Chuẩn bị máy client**: lấy TGT qua cơ chế tổ chức cho phép (ví dụ kinit) và kiểm tra bằng klist; không cần copy keytab của server sang client.
5. **Nạp cấu hình vào JVM**: realm, KDC, JAAS login entry đúng vai trò; xử lý môi trường ticket cache đúng với user chạy JVM.
6. **Login bằng JAAS**: kiểm tra principal trong Subject, không log private credentials.
7. **Thực hiện GSS/SASL/HTTP exchange**: client xin service ticket rồi gửi token; server dùng service key để nhận token.
8. **Xác thực quyền**: từ principal đã xác thực, ứng dụng ánh xạ sang role/ACL theo chính sách riêng.
9. **Kiểm tra gia hạn/rotation**: ticket hết hạn cần cơ chế tái lấy credential; keytab và KVNO phải phối hợp khi xoay khóa.

Các lệnh chẩn đoán tham khảo, chỉ chạy trong môi trường Kerberos được cấp quyền:

~~~bash
kinit alice@EXAMPLE.COM
klist
kvno HTTP/api.example.com@EXAMPLE.COM
klist -k -e /etc/security/keytabs/http-api.keytab
~~~

Trước khi debug Java, hãy xác định bước lỗi: **AS không cấp TGT**, **TGS không cấp service ticket**, hay **acceptor không giải mã/xác nhận AP-REQ**. Ba lỗi này cần ba hướng sửa khác nhau.

## 15. Các lỗi thường gặp và cách suy luận

| Triệu chứng | Nguyên nhân khả dĩ | Kiểm tra trước |
| --- | --- | --- |
| Không tìm thấy KDC / realm | krb5.conf hoặc DNS/KDC routing sai | Realm, host, cổng, khả năng kết nối |
| Client không có credential hợp lệ | Cache trống/hết hạn hoặc JVM đọc sai cache | klist dưới đúng user chạy ứng dụng; useTicketCache |
| Server not found in Kerberos database | SPN/realm client yêu cầu không được KDC nhận diện | Exact service principal, alias, realm, đăng ký SPN |
| KRB_AP_ERR_MODIFIED hoặc lỗi integrity khi accept | Server không có đúng service key, KVNO/enctype lệch, SPN trỏ sai instance | Ticket service principal, keytab, KVNO/enctype, hostname |
| Clock skew too great / ticket expired | Lệch giờ hoặc hạn vé | Đồng bộ NTP, thời gian KDC/client/server, ticket lifetime |
| Cannot find key of appropriate type | Thiếu khóa/enctype tương thích | Enctype keytab và chính sách KDC/JDK |
| JAAS LoginException doNotPrompt | Không có nguồn credential cho login mode đã chọn | Cấu hình useTicketCache/useKeyTab, principal, quyền file |
| JAAS login OK nhưng HTTP 401 / SASL fail | GSS negotiation, SPN, server key, proxy hoặc authorization chưa đúng | Token exchange, service principal, acceptor, user mapping |

Thông báo lỗi là dấu hiệu để khoanh vùng, không phải kết luận nguyên nhân duy nhất. Ví dụ KRB_AP_ERR_MODIFIED có thể xuất hiện khi keytab cũ **hoặc** client lấy ticket cho SPN của instance khác.

Để bật chẩn đoán JVM khi thật sự cần:

~~~bash
java -Dsun.security.krb5.debug=true -Djava.security.auth.login.config=jaas.conf -Djava.security.krb5.conf=krb5.conf JaasLoginDemo
~~~

Có thể bật debug=true cho Krb5LoginModule ở môi trường kiểm thử. **Không để debug Kerberos thường trực ở production**; bảo vệ log vì có thể lộ tên principal, hostname, cấu hình và thông tin nhạy cảm vận hành.

## 16. Những hiểu lầm nguy hiểm

- **“TGT và service ticket giống nhau.”** Sai. TGT dùng xin ticket ở TGS; service ticket dành cho một service cụ thể.
- **“Có keytab là chắc chắn Kerberos login sẽ thành công.”** Sai. Principal, KVNO, enctype, realm, quyền file và mode của LoginModule phải phù hợp. Client initiator cần một quá trình lấy credential, có thể liên quan KDC.
- **“Krb5LoginModule tự xác thực mọi HTTP request.”** Sai. Cần GSS/SPNEGO/HTTP integration cùng trao đổi token và kiểm tra server.
- **“Service có TGT thì không cần keytab.”** Sai với mô hình acceptor dựa trên khóa service để mở ticket được KDC cấp.
- **“Kerberos thành công nghĩa là user có mọi quyền.”** Sai. Kerberos xác thực identity, còn tài nguyên/quyền do ứng dụng hay hệ thống ủy quyền quyết định.
- **“Đã có Kerberos thì không cần TLS.”** Sai. HTTPS/TLS và Kerberos xử lý các khía cạnh an ninh khác nhau; cần xét bảo vệ transport, channel binding và threat model của giao thức cụ thể.
- **“Ticket cache và keytab đều là nơi lưu mật khẩu.”** Sai. Cache chứa ticket/credential phiên; keytab chứa khóa dài hạn, cả hai đều phải được bảo vệ.

## 17. Bài tập tự kiểm tra

**Tình huống A:** client có TGT hợp lệ nhưng khi gọi API nhận “Server not found in Kerberos database”. Bạn kiểm tra service principal client yêu cầu và bản ghi SPN/realm tại KDC trước; thay keytab ở server chưa giải quyết việc TGS không cấp được ticket.

**Tình huống B:** client lấy service ticket thành công, server trả KRB_AP_ERR_MODIFIED sau lần rotate key. Kiểm tra KVNO/enctype và liệu mọi instance đã nhận keytab mới, đồng thời xác minh client gửi ticket cho đúng SPN.

**Tình huống C:** một Java service chỉ cần nhận HTTP Negotiate token. Entry JAAS nên là acceptor-only (isInitiator=false), useKeyTab=true, storeKey=true, principal chính xác; không cấu hình kinit/TGT như điều kiện bắt buộc chỉ để nhận vé.

**Tình huống D:** LoginContext.login() trả thành công nhưng endpoint vẫn 403. Phân biệt hai câu hỏi: token GSS đã được xác thực và ánh xạ ra principal chưa? Principal đó có quyền gọi endpoint theo authorization policy không?

Bạn hoàn thành bài khi tự vẽ lại đường đi **AS → TGS → AP**, chỉ ra khóa nào dùng ở mỗi bước, giải thích credential nào được lưu trong **Subject** cho initiator/acceptor và xác định được nơi cần kiểm tra khi client có TGT nhưng vẫn không gọi được service.

## 18. Tài liệu kỹ thuật để đối chiếu

- Kerberos V5, RFC 4120: https://www.rfc-editor.org/rfc/rfc4120
- Java SE 21 Krb5LoginModule API (tùy chọn, ràng buộc và ví dụ): https://docs.oracle.com/en/java/javase/21/docs/api/jdk.security.auth/com/sun/security/auth/module/Krb5LoginModule.html
- Oracle Java SE 17 JAAS Login và Java GSS-API: https://docs.oracle.com/en/java/javase/17/security/use-jaas-login-utility-and-java-gss-api-secure-message-exchanges.html

Các đường dẫn trên mô tả chuẩn và JDK; ví dụ EXAMPLE.COM cần được thay bằng môi trường Kerberos hợp lệ để kiểm thử tương tác thật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sasl-gss-api-007">18.7 Thiết lập ngữ cảnh GSS, wrap, unwrap, MIC và kiểm tra replay hoặc thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# 18.7 — Thiết lập ngữ cảnh GSS, wrap/unwrap, MIC và kiểm tra replay hoặc thứ tự

## 1. Mục tiêu và mô hình cần nắm

Sau bài này, bạn có thể: (1) phân biệt xác thực khi thiết lập `GSSContext` với bảo vệ từng thông điệp sau xác thực; (2) triển khai luồng `initSecContext`/`acceptSecContext` qua kênh truyền có đóng khung token; (3) chọn `wrap`/`unwrap` hay `getMIC`/`verifyMIC` theo yêu cầu bí mật dữ liệu; (4) yêu cầu, xác minh và xử lý đúng replay/sequence detection; (5) tránh coi các cờ GSS là chính sách ứng dụng tự động.

**GSS-API (Generic Security Service API)** là giao diện chung cho cơ chế bảo mật như Kerberos. Trong Java, API chuẩn nằm ở module `java.security.jgss`, package `org.ietf.jgss`. GSS có hai phía: **initiator** (bên khởi tạo, thường là client) và **acceptor** (bên chấp nhận, thường là server). Hai phía trao đổi các **token nhị phân** để xây dựng ngữ cảnh bảo mật, rồi dùng chính ngữ cảnh ấy để bảo vệ dữ liệu. GSS **không tự cung cấp** kết nối TCP, định dạng frame, định tuyến thông điệp, retry hay phân quyền nghiệp vụ. SASL có thể sử dụng cơ chế GSSAPI, nhưng phần này thao tác trực tiếp bằng Java GSS-API.

Các đối tượng chính:

| Thành phần | Vai trò |
|---|---|
| `GSSManager` | Factory tạo `GSSName`, `GSSCredential`, `GSSContext`; liệt kê mechanism. |
| `GSSName` | Danh tính bên gửi/bên nhận hoặc service được nhắm tới. |
| `GSSCredential` | Handle tới thông tin xác thực đã được cấp; API không tự đăng nhập để tạo danh tính. |
| `GSSContext` | Trạng thái xác thực, khóa/sequence của **một phiên**; dùng để tạo/kiểm tra token. |
| `MessageProp` | Chọn QOP/privacy khi gửi; nhận QOP/privacy và trạng thái replay/order khi nhận. |
| `GSSException` | Lỗi token, credential, policy mechanism, timeout/expiry, kiểm tra MIC... |

## 2. Ngữ cảnh GSS được thiết lập như thế nào?

```text
Initiator                                          Acceptor
  |  createContext(target, mechanism, credential)    | createContext(acceptorCredential)
  |  requestMutualAuth/Conf/Integ/Replay/Sequence     |
  |                                                  |
  | -- initSecContext(empty) -> token #1 ----------> |
  |                                   acceptSecContext(token #1)
  | <--------- token #2 (nếu phát sinh) ------------- |
  | initSecContext(token #2)                          |
  | -- token #3 (nếu phát sinh) --------------------> |
  |                      ...                         |
  | isEstablished() trên cả hai phía                  |
  | check get*State(), peer identity, authorization   |
  | ====== ứng dụng bắt đầu trao đổi dữ liệu ======= |
  | -- wrap(plaintext) -> wrapped token ------------> | unwrap(token)
  | -- plaintext + getMIC(plaintext) ---------------> | verifyMIC(token, plaintext)
```

Một lần gọi `initSecContext` hoặc `acceptSecContext` **chưa chắc hoàn thành handshake**. Mỗi lời gọi nhận token trước đó và có thể trả một token mới; ứng dụng phải chuyển **đầy đủ byte của token**, kể cả khi phía tạo token đã báo `isEstablished() == true`. `null` nghĩa là lần gọi không phát sinh token để gửi. Kết quả `isEstablished()` là **trạng thái cục bộ**: giao thức truyền của ứng dụng phải điều phối để hai phía đều sẵn sàng trước khi gửi dữ liệu nghiệp vụ.

1. Chuẩn bị cơ chế và credential hợp lệ. Với Kerberos v5, OID chuẩn là `1.2.840.113554.1.2.2`; phía client thường cần TGT, phía server cần credential nhận cho service principal tương ứng.
2. Initiator tạo `GSSContext` cho **đúng service**, cấu hình các yêu cầu **trước lần `initSecContext` đầu tiên**: `requestMutualAuth(true)`, `requestInteg(true)`, `requestConf(true)`, `requestReplayDet(true)`, `requestSequenceDet(true)`. Không yêu cầu credential delegation nếu ứng dụng không thực sự cần.
3. Acceptor tạo context từ credential có quyền `ACCEPT_ONLY`. Nó không gọi các phương thức `request*` dành cho initiator; chính token thương lượng quyết định dịch vụ cuối cùng.
4. Đổi token qua transport; lặp đến khi cả hai context established. Token GSS **không được** đổi sang chuỗi bằng encoding mặc định, cắt ở byte 0 hoặc nhầm với dữ liệu UTF-8.
5. Khi thành công, kiểm tra trạng thái thực tế: `getMech()`, `getSrcName()`, `getTargName()`, `getMutualAuthState()`, `getIntegState()`, `getConfState()`, `getReplayDetState()`, `getSequenceDetState()`, `getLifetime()`. Một yêu cầu `request*` **không đảm bảo** mechanism sẽ bật tính năng tương ứng.
6. Server xác thực **và** phân quyền danh tính `getSrcName()` cho tài nguyên cụ thể. Client xác nhận service đích/đặc tính xác thực. Khi không đáp ứng chính sách bắt buộc, kết thúc context, không chuyển sang thông điệp nghiệp vụ.

Với `GSSName.NT_HOSTBASED_SERVICE`, Java nhận dạng dạng `service@hostname`, ví dụ `HTTP@app.example.com`; thư viện Kerberos có thể ánh xạ nó thành principal `HTTP/app.example.com@REALM`. Đừng nhầm ký hiệu `@` của host-based GSS name với phần `@REALM` trong principal Kerberos. DNS, realm, SPN và credential phải đồng nhất.

**Lưu ý về thứ tự gọi:** các phương thức `requestReplayDet`, `requestSequenceDet`, `requestConf`, `requestInteg`, `requestMutualAuth` chỉ được initiator yêu cầu **trước** lần `initSecContext` đầu tiên. `setChannelBinding(...)` (nếu dùng) cần đặt **trên cả hai phía, với cùng thông tin binding đã thỏa thuận**, trước khi bắt đầu context. Đối với phần lớn ứng dụng, chỉ cho phép message operations sau `isEstablished()`; `isProtReady()` tồn tại cho trường hợp cơ chế cho phép bảo vệ sớm nhưng cần quy tắc giao thức chặt chẽ.

## 3. Hai cách bảo vệ từng thông điệp

| Đường API | Thông điệp trên đường truyền | Đảm bảo chính |
|---|---|---|
| `wrap(plaintext, ..., new MessageProp(0, true))` → `unwrap(token, ..., prop)` | Một **wrapped token** chứa dữ liệu và thông tin kiểm tra | Integrity; thêm confidentiality **nếu thực tế privacy được áp dụng**. |
| `wrap(..., new MessageProp(0, false))` → `unwrap(...)` | Wrapped token có dữ liệu, không yêu cầu mã hóa | Integrity, dữ liệu không được giữ bí mật. |
| `getMIC(plaintext, ..., prop)` → `verifyMIC(micToken, ..., samePlaintext, ..., prop)` | **Plaintext và MIC token tách biệt** | Kiểm chứng integrity/authenticity; **không mã hóa plaintext**. |

`MessageProp(0, true)` có nghĩa QOP = 0 (mức mặc định) và **yêu cầu privacy**, không phải bằng chứng đã mã hóa. Sau `wrap`, kiểm tra `prop.getPrivacy()`; sau `unwrap`, kiểm tra `prop.getPrivacy()` của token thực nhận. Nếu ứng dụng bắt buộc bí mật mà `getConfState()` hoặc `getPrivacy()` không thỏa, phải từ chối. `getIntegState()` cần được bật cho cả wrap và MIC. MIC được tính trên **đúng chuỗi byte**: chỉ cần thay encoding, dấu xuống dòng hay một byte thì `verifyMIC` không còn khớp. Không dùng `getMIC` như API chữ ký chung độc lập với GSS context.

`getWrapSizeLimit(qop, confReq, maxTokenSize)` ước lượng lượng dữ liệu đầu vào tối đa để wrapped token nằm trong giới hạn transport. Nó **không** thay thế giới hạn frame, kiểm tra kích thước token thực nhận, hay phân mảnh ở lớp ứng dụng.

## 4. Ví dụ Java 17+: tạo context, đổi token, xử lý message

Tệp dưới đây **biên dịch và chạy được trên JDK 17+**. Lệnh `main` chỉ chạy một bài kiểm thử chính sách replay/order bằng `MessageProp` giả lập nên **không cần KDC**. Các phương thức `newInitiator`, `newAcceptor`, `establish...`, `protect`, `open`, `makeMic`, `verifyMic` là mã thực để tích hợp vào client/server **khi đã có credential Kerberos và transport**. Không có dữ liệu Kerberos thật được tạo tự động trong chương trình kiểm thử.

```java
// File: GssLesson.java
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.ietf.jgss.*;

public final class GssLesson {
    private GssLesson() {}

    // Chỉ trao đổi token GSS; lớp ứng dụng phải quản lý phase handshake/data.
    public interface TokenPipe {
        void send(byte[] token) throws IOException;
        byte[] receive() throws IOException;
    }

    // Ví dụ framing nhị phân: độ dài int 4 byte + token; hai phía dùng cùng quy ước.
    public static final class FramedPipe implements TokenPipe {
        private static final int MAX_TOKEN = 1_048_576;
        private final DataInputStream in;
        private final DataOutputStream out;

        public FramedPipe(InputStream input, OutputStream output) {
            in = new DataInputStream(input);
            out = new DataOutputStream(output);
        }

        @Override
        public void send(byte[] token) throws IOException {
            if (token == null || token.length > MAX_TOKEN) {
                throw new IOException("Invalid outgoing token length");
            }
            out.writeInt(token.length);
            out.write(token);
            out.flush();
        }

        @Override
        public byte[] receive() throws IOException {
            int size = in.readInt();
            if (size < 0 || size > MAX_TOKEN) {
                throw new IOException("Invalid incoming token length");
            }
            byte[] token = new byte[size];
            in.readFully(token);
            return token;
        }
    }

    private static Oid kerberosV5() throws GSSException {
        return new Oid("1.2.840.113554.1.2.2");
    }

    // Client: đã có default initiator credential, ví dụ TGT của Subject hiện hành.
    public static GSSContext newInitiator(String hostBasedService)
            throws GSSException {
        GSSManager manager = GSSManager.getInstance();
        GSSName server = manager.createName(
                hostBasedService, GSSName.NT_HOSTBASED_SERVICE);
        GSSContext context = manager.createContext(
                server, kerberosV5(), null, GSSContext.DEFAULT_LIFETIME);
        context.requestMutualAuth(true);
        context.requestInteg(true);
        context.requestConf(true);
        context.requestReplayDet(true);
        context.requestSequenceDet(true);
        context.requestCredDeleg(false);
        return context;
    }

    // Server: caller sở hữu acceptorCredential và phải dispose khi xong.
    public static GSSContext newAcceptor(GSSCredential acceptorCredential)
            throws GSSException {
        return GSSManager.getInstance().createContext(acceptorCredential);
    }

    public static void establishInitiator(GSSContext context, TokenPipe link)
            throws GSSException, IOException {
        byte[] incoming = new byte[0]; // Token rỗng chỉ cho lời gọi đầu.
        while (!context.isEstablished()) {
            byte[] outgoing = context.initSecContext(
                    incoming, 0, incoming.length);
            if (outgoing != null) {
                link.send(outgoing); // Gửi cả khi context vừa established.
            }
            if (!context.isEstablished()) {
                incoming = link.receive();
                if (incoming == null) throw new EOFException("Missing GSS reply");
            }
        }
    }

    public static void establishAcceptor(GSSContext context, TokenPipe link)
            throws GSSException, IOException {
        while (!context.isEstablished()) {
            byte[] incoming = link.receive();
            if (incoming == null) throw new EOFException("Missing GSS request");
            byte[] outgoing = context.acceptSecContext(
                    incoming, 0, incoming.length);
            if (outgoing != null) {
                link.send(outgoing); // Không bỏ final token.
            }
        }
    }

    // Kiểm tra chính sách bắt buộc, không chỉ tin vào request* của client.
    public static void requireServices(GSSContext context,
            boolean requirePrivacy, boolean strictSequence) {
        if (!context.isEstablished()
                || !context.getMutualAuthState()
                || !context.getIntegState()
                || !context.getReplayDetState()
                || (strictSequence && !context.getSequenceDetState())
                || (requirePrivacy && !context.getConfState())) {
            throw new SecurityException("Required GSS service unavailable");
        }
    }

    public static void checkArrival(MessageProp p,
            boolean requirePrivacy, boolean strictSequence) {
        if (p.isDuplicateToken()) {
            throw new SecurityException("Replay: duplicate token");
        }
        if (p.isOldToken()) {
            throw new SecurityException("Replay: old token");
        }
        if (strictSequence && p.isUnseqToken()) {
            throw new SecurityException("Sequence: unsequenced token");
        }
        if (strictSequence && p.isGapToken()) {
            throw new SecurityException("Sequence: gap token");
        }
        if (requirePrivacy && !p.getPrivacy()) {
            throw new SecurityException("Message was not encrypted");
        }
    }

    public static byte[] protect(GSSContext sender, String text,
            boolean requirePrivacy) throws GSSException {
        requireServices(sender, requirePrivacy, false);
        byte[] input = text.getBytes(StandardCharsets.UTF_8);
        MessageProp prop = new MessageProp(0, requirePrivacy);
        byte[] token = sender.wrap(input, 0, input.length, prop);
        if (requirePrivacy && !prop.getPrivacy()) {
            throw new SecurityException("wrap did not apply privacy");
        }
        return token;
    }

    public static String open(GSSContext receiver, byte[] wrappedToken,
            boolean requirePrivacy, boolean strictSequence)
            throws GSSException {
        requireServices(receiver, requirePrivacy, strictSequence);
        MessageProp prop = new MessageProp(0, false);
        byte[] plain = receiver.unwrap(
                wrappedToken, 0, wrappedToken.length, prop);
        checkArrival(prop, requirePrivacy, strictSequence);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public static byte[] makeMic(GSSContext sender, byte[] message)
            throws GSSException {
        requireServices(sender, false, false);
        return sender.getMIC(
                message, 0, message.length, new MessageProp(0, false));
    }

    public static void verifyMic(GSSContext receiver, byte[] micToken,
            byte[] originalMessage, boolean strictSequence)
            throws GSSException {
        requireServices(receiver, false, strictSequence);
        MessageProp prop = new MessageProp(0, false);
        receiver.verifyMIC(
                micToken, 0, micToken.length,
                originalMessage, 0, originalMessage.length, prop);
        checkArrival(prop, false, strictSequence);
    }

    // Chỉ kiểm tra logic chính sách; KHÔNG tạo/kiểm chứng token Kerberos.
    private static String policyResult(MessageProp p, boolean strict) {
        try {
            checkArrival(p, false, strict);
            return "ACCEPT";
        } catch (SecurityException rejected) {
            return "REJECT";
        }
    }

    public static void main(String[] args) {
        MessageProp normal = new MessageProp(0, false);
        MessageProp duplicate = new MessageProp(0, false);
        MessageProp gap = new MessageProp(0, false);
        // Giả lập trạng thái provider sẽ điền sau unwrap/verifyMIC.
        duplicate.setSupplementaryStates(true, false, false, false, 0, null);
        gap.setSupplementaryStates(false, false, false, true, 0, null);
        System.out.println("normal=" + policyResult(normal, true));
        System.out.println("duplicate=" + policyResult(duplicate, true));
        System.out.println("gap_strict=" + policyResult(gap, true));
        System.out.println("gap_relaxed=" + policyResult(gap, false));
    }
}
```

Chạy từ thư mục chứa `GssLesson.java`:

```bash
javac -encoding UTF-8 GssLesson.java
java GssLesson
```

Kết quả chuẩn xác của **bài test chính sách giả lập**:

```text
normal=ACCEPT
duplicate=REJECT
gap_strict=REJECT
gap_relaxed=ACCEPT
```

Giải thích: `setSupplementaryStates` ở `main` chỉ dùng **trong phép thử** để tạo dữ liệu giả; trong xử lý GSS thực, provider điền cờ sau `unwrap`/`verifyMIC`, ứng dụng **không tự đặt cờ**. Việc `gap_relaxed=ACCEPT` minh họa rằng chính sách không bắt buộc thứ tự có thể chấp nhận token đến thiếu predecessor, **không** có nghĩa mọi nghiệp vụ được phép mất hoặc đảo thông điệp.

### 4.1. Kết nối class vào Kerberos thực tế

Trên môi trường có KDC, cấu hình `krb5.conf`, principal/keytab, JAAS/`Subject` hoặc login session phù hợp:

- Client tạo `GSSContext client = GssLesson.newInitiator("HTTP@app.example.com")` sau khi đã có initiator credential/TGT (ví dụ từ `kinit` hoặc Java login flow).
- Server tạo `GSSCredential serviceCred = manager.createCredential(serviceName, GSSCredential.DEFAULT_LIFETIME, kerberosOid, GSSCredential.ACCEPT_ONLY)` bằng principal có keytab/ticket hợp lệ, rồi `GSSContext server = GssLesson.newAcceptor(serviceCred)`.
- Mỗi phía dùng một `TokenPipe` trên connection. Client gọi `establishInitiator(...)`, server gọi `establishAcceptor(...)`. Server gửi token trả về cho client **trước khi** ứng dụng chuyển phase. Khung ví dụ ở trên chỉ chứa length + bytes; nếu dùng chung kênh cho nhiều loại message, bổ sung frame type/state machine và thông điệp kết thúc handshake của ứng dụng để tránh hiểu nhầm final token với dữ liệu.
- Sau khi cả hai bên sẵn sàng, mỗi bên gọi `requireServices(...)`, kiểm tra `getSrcName()`/`getTargName()` đúng danh tính mong đợi; server phân quyền trước khi cho phép xử lý request.
- Sender dùng `protect(context, "lệnh nghiệp vụ", true)` để tạo token, gửi qua kênh; receiver gọi `open(context, token, true, true)` và **chỉ thực hiện tác động nghiệp vụ sau khi mọi kiểm tra thành công**.
- Trường hợp chỉ cần integrity, sender gửi **hai thành phần**: byte `message` và `makeMic(context, message)`. Receiver gọi `verifyMic(context, mic, receivedMessage, true)` rồi mới sử dụng `receivedMessage`. Byte message phải được bảo toàn 100%.

Gọi `dispose()` trên mọi context và credential do ứng dụng sở hữu trong `finally` hoặc cơ chế quản lý tài nguyên tương đương. Không chia sẻ một `GSSContext` cho nhiều luồng đồng thời mà thiếu đồng bộ, bởi các phương thức context không được đảm bảo synchronized. Mỗi phiên/peer có context riêng; context hết hạn cần thiết lập lại theo chính sách, không tự dùng token của context cũ.

**Vì sao ví dụ không giả lập handshake thành công ngay trong `main`?** Kerberos cần credential và KDC thực. In ra “authenticated” từ dữ liệu giả sẽ tạo hiểu lầm về tính an toàn. Có thể kiểm tra biên dịch và logic policy độc lập; muốn chạy handshake thực, cần môi trường Kerberos tương ứng.

## 5. Replay detection và sequence detection: khác nhau ở đâu?

Replay là **phát lại** token cũ; sequence là **thứ tự giao nhận**. Context có thể phát hiện cả hai nhờ metadata/sequence của cơ chế, nhưng kết quả phụ thuộc tính năng thực sự đã thương lượng, phạm vi cửa sổ kiểm tra và cách ứng dụng sử dụng một context.

| Cờ nhận sau `unwrap`/`verifyMIC` | Ý nghĩa thực tế | Chính sách thường dùng |
|---|---|---|
| `isDuplicateToken()` | Token đã được xử lý trước đó trong cửa sổ phát hiện. | Từ chối, không chạy lại side effect. |
| `isOldToken()` | Token quá cũ để kiểm tra chắc việc phát lại. | Từ chối khi yêu cầu chống replay. |
| `isUnseqToken()` | Một token có thứ tự **muộn hơn** đã được xử lý trước token này. | Từ chối nếu giao thức yêu cầu đúng thứ tự. |
| `isGapToken()` | Chưa thấy một hoặc nhiều token tiền nhiệm mong đợi. | Dừng/chờ/báo lỗi theo protocol; từ chối nếu strict ordering. |

Các cờ là **thông tin bổ sung** về token nhận, không đồng nghĩa với “ứng dụng đã an toàn”. Sau `verifyMIC` không ném lỗi và sau `unwrap` trả về plaintext, vẫn phải kiểm tra các cờ trước khi chấp nhận. Với token hỏng, MIC sai, context hết hạn hoặc credential lỗi, API có thể ném `GSSException`; phải fail closed (không giải mã hoặc nhận message lỗi rồi tiếp tục nghiệp vụ). Đừng coi mọi `GSSException` là replay; ghi nhận major code (`getMajor()`, `getMajorString()`) để chẩn đoán có kiểm soát, tránh log secret/token gốc.

**Thứ tự đánh giá hợp lý tại receiver:**

1. Đảm bảo context đã thiết lập, có integrity và replay detection; nếu yêu cầu strict order thì `getSequenceDetState() == true`. Khi muốn mã hóa, cần thêm confidentiality.
2. Đọc **trọn một token**; giới hạn độ dài frame trước cấp phát bộ nhớ.
3. `unwrap` hoặc `verifyMIC`, bắt `GSSException`; không dùng plaintext khi xác minh thất bại.
4. Kiểm tra `duplicate` và `old`; nếu bật strict order, kiểm tra thêm `unseq` và `gap`. Nếu cần privacy, kiểm tra `prop.getPrivacy()`.
5. Chỉ khi đạt các kiểm tra mới parse, authorize và thực hiện nghiệp vụ; kết hợp transaction/idempotency key cho request có side effect.

Ví dụ thứ tự token do sender phát là `T1, T2, T3`:

| Receiver nhìn thấy | Khả năng cờ | Ý nghĩa ứng dụng |
|---|---|---|
| `T1, T2, T3` | Không có cờ bổ sung | Giao nhận đúng thứ tự (khi mechanism hỗ trợ). |
| `T1, T1` | `duplicate` ở lần thứ hai | Request thứ hai không được chạy lại. |
| `T1, T3` | Có thể `gap` tại `T3` | `T2` chưa được xử lý; quyết định chờ hay từ chối. |
| `T1, T3, T2` | Có thể `gap` ở `T3`, `unseq` ở `T2` | Với strict order, không thể âm thầm áp dụng T2 sau T3. |
| Token nằm ngoài cửa sổ kiểm tra | Có thể `old` | Không thể kết luận token mới an toàn; nên từ chối. |

“Có thể” ở trên là chủ ý: chi tiết thời điểm gắn cờ, cửa sổ kiểm tra, việc ném exception và đường đi token phụ thuộc mechanism/provider. Test bảo mật thật cần phát lại/chuyển thứ tự **chính những token đã được provider tạo ra trên hai context đang hoạt động**, không chỉ thao tác `MessageProp` bằng tay. Các cờ cũng chỉ bao quát **một context**; request bị gửi lại ở phiên mới cần chính sách idempotency/replay ở tầng ứng dụng.

### 5.1. Test case cần có khi tích hợp

| Ca thử | Điều kiện | Kết quả mong đợi |
|---|---|---|
| Handshake hợp lệ | Client/service credentials đúng, mutual auth thành công | Cả hai established; identity và các `get*State()` đạt policy. |
| Service principal sai | Client trỏ sai service hoặc server không có key phù hợp | Không chấp nhận peer, không xử lý dữ liệu. |
| Wrapped token bị sửa byte | Thay một byte của token | `unwrap` lỗi MIC/token hoặc từ chối; không có side effect. |
| MIC so với message khác | `getMIC` trên A, `verifyMIC` trên B | Lỗi xác minh; không dùng B. |
| Chế độ bí mật không được cấp | `getConfState()==false` hoặc token `getPrivacy()==false` | Từ chối nếu chính sách cần confidentiality. |
| Phát lại cùng token | `unwrap`/`verifyMIC` hai lần trên cùng context | Cờ duplicate/old hoặc lỗi mechanism; từ chối lần hai. |
| Đảo thứ tự/gap | Gửi T2 trước T1 trên cơ chế có sequence detection | Xử lý theo chính sách strict; không chạy side effect của token sai thứ tự. |
| Context hết hạn | Gọi per-message API sau expiry | Từ chối, tạo phiên mới theo quy trình. |
| Kênh nhận frame dài bất thường | Length vượt giới hạn | Chặn trước khi cấp phát hoặc xử lý GSS token. |

## 6. Các lỗi thiết kế thường gặp và cách tránh

**Nhầm “request” với “đã được cấp”.** `requestReplayDet(true)` chỉ đề nghị dịch vụ. Quyết định nhận message phải dựa trên `getReplayDetState()`, `getSequenceDetState()` và trạng thái token sau khi xử lý.

**Cho rằng `wrap` luôn mã hóa.** Một wrapped token có thể chỉ có integrity. Muốn confidentiality, yêu cầu nó lúc thiết lập context, dùng `MessageProp(0, true)`, kiểm tra `getConfState()` và `getPrivacy()` ở cả bên gửi/bên nhận.

**Cho rằng MIC là mã hóa hoặc chữ ký không phụ thuộc phiên.** MIC không che plaintext; kết quả gắn với context và bytes của message. Khi protocol chuyển đổi JSON, Unicode normalization hay newline giữa sender và receiver, MIC có thể không hợp lệ. Chốt canonical representation hoặc giữ nguyên bytes.

**Bỏ qua cờ supplementary vì `unwrap` đã trả plaintext.** Plaintext có MIC hợp lệ vẫn có thể là replay hoặc sai thứ tự. Đặt check ngay sau GSS operation và trước parse/execute.

**Gộp mọi token vào một luồng mà không có framing/protocol phase.** GSS không đánh dấu kết thúc TCP stream hay phân biệt application frame. Dùng length prefix với giới hạn, phân loại message, xử lý token handshake trước message payload, và tránh deadlock do cả hai chờ nhận.

**Coi bảo vệ GSS là thay thế authorization và idempotency.** GSS xác nhận danh tính theo mechanism và bảo vệ token; ứng dụng vẫn phải kiểm tra role/quyền, request ID và chống thực thi lặp các thao tác như chuyển tiền/ghi trạng thái. Replay window của mechanism không thay thế nhật ký request đã xử lý.

**Dùng sai identity/credential.** Host-based `service@host` khác principal `service/host@REALM`. Credential mặc định có thể không tồn tại hoặc không có quyền `ACCEPT_ONLY`. Sử dụng keytab/JAAS/`Subject` đúng role, không nhúng password trong source hay log token nhạy cảm.

**Quên dọn tài nguyên và xử lý lỗi.** Khi `GSSException` xảy ra trong handshake, không được coi phiên established; đóng context và transport. Khi request bị từ chối do replay/order, đừng tự thực thi lại tác vụ chỉ vì transport retry. GSS context không an toàn để dùng song song tùy tiện; cần khóa hoặc phân chia context theo kết nối.

## 7. Checklist thực hành và tài liệu chuẩn

Trước khi bật GSS trên một dịch vụ, hãy kiểm tra đủ: identity client/service; Kerberos credential hợp lệ; token framing có kích thước tối đa; handshake hai chiều kết thúc rõ ràng; requested và negotiated flags được kiểm tra; `wrap`/`MIC` đúng yêu cầu confidentiality; receiver chặn token duplicate/old/out-of-order trước side effect; authorization/idempotency riêng; cleanup và negative tests.

Tài liệu đối chiếu:

- Java 17 `GSSContext`: https://docs.oracle.com/en/java/javase/17/docs/api/java.security.jgss/org/ietf/jgss/GSSContext.html
- Java 17 `GSSManager` và `GSSCredential`: https://docs.oracle.com/en/java/javase/17/docs/api/java.security.jgss/org/ietf/jgss/GSSManager.html và https://docs.oracle.com/en/java/javase/17/docs/api/java.security.jgss/org/ietf/jgss/GSSCredential.html
- Java 17 `MessageProp`: https://docs.oracle.com/en/java/javase/17/docs/api/java.security.jgss/org/ietf/jgss/MessageProp.html
- RFC 2743, GSS-API v2: https://www.rfc-editor.org/rfc/rfc2743.html
- RFC 8353, Java bindings update: https://www.rfc-editor.org/rfc/rfc8353.html

Ghi nhớ: **GSSContext xác lập phiên và danh tính; wrap/unwrap bảo vệ dữ liệu (có hoặc không có mã hóa); MIC kiểm tra nguyên vẹn; replay/order flags cho ứng dụng dữ liệu để quyết định nhận hay từ chối.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sasl-gss-api-008">18.8 SPNEGO, ủy quyền credential và ranh giới bảo vệ của từng cơ chế</a>

<details>
<summary>Xem chi tiết</summary>

# 18.8. SPNEGO, ủy quyền credential và ranh giới bảo vệ của từng cơ chế

## Mục tiêu học tập

Sau bài này, bạn có thể giải thích SPNEGO chọn cơ chế xác thực như thế nào; phân biệt xác thực, ủy quyền truy cập và **chuyển giao thông tin xác thực (credential delegation)**; nhận biết mức bảo vệ thật sự của Kerberos/GSS-API, SASL GSSAPI, HTTP Negotiate và TLS; đồng thời cấu hình một luồng Java GSS-API theo nguyên tắc tối thiểu quyền. Trọng tâm là **ranh giới tin cậy của từng bước**, đặc biệt với ứng dụng nhiều tầng (frontend → backend).

> Thuật ngữ: `initiator` là phía mở ngữ cảnh bảo mật (thường là client), `acceptor` là phía nhận (thường là service). `Credential` là khả năng chứng minh danh tính trong một cơ chế; nó có thể là ticket/khóa hay đối tượng đại diện cho chúng, **không đồng nghĩa mật khẩu**. `Delegation` cho phép một service dùng credential được ủy quyền để xác thực tiếp với service khác dưới danh tính người dùng, tùy cơ chế và chính sách.

## 1. Đặt SPNEGO đúng tầng

| Thành phần | Vai trò chính | Không tự bảo đảm |
| --- | --- | --- |
| **GSS-API / JGSS** (`org.ietf.jgss`) | API thống nhất để lập security context, xác thực đối tác và tùy cơ chế dùng MIC/wrap bảo vệ bản tin | Không tự vận chuyển token trên mạng, không quyết định quyền nghiệp vụ |
| **Kerberos V5 GSS mechanism** | Chứng minh danh tính bằng ticket/khóa phiên; có thể hỗ trợ xác thực hai chiều, kiểm tra toàn vẹn, mã hóa từng bản tin | Không tự mã hóa toàn bộ HTTP/TCP; không cấp quyền truy cập dữ liệu |
| **SPNEGO** | Cơ chế giả (pseudo-mechanism) để hai phía chọn một GSS mechanism chung | Không là thuật toán mật mã ứng dụng; không tự cấp quyền hay delegation |
| **HTTP Negotiate** | Chở các token GSS/SPNEGO qua header HTTP trong chuỗi challenge/response | Không bảo vệ HTTP headers/body; không tự bật TLS |
| **SASL GSSAPI** | Cơ chế SASL dùng Kerberos V5 GSS-API cho giao thức như LDAP; có thể thương lượng tầng `auth`/`auth-int`/`auth-conf` | Không phải SPNEGO; không đồng nghĩa mọi luồng dữ liệu đã được bảo vệ |
| **TLS** | Bảo vệ kết nối vận chuyển: mã hóa, toàn vẹn và xác thực endpoint theo chứng chỉ/cấu hình | Không mặc nhiên biết user Kerberos; không tự chuyển quyền qua reverse proxy |
| **Application authorization** | Kiểm tra principal đã xác thực được phép làm gì trên tài nguyên nào | Không thể thay bằng thành công của handshake SPNEGO |

**Ba câu hỏi khác nhau:** (1) “Ai đang kết nối?” là authentication. (2) “Người ấy được làm gì?” là authorization. (3) “Service trung gian có được đại diện người ấy kết nối nơi khác?” là credential delegation. Trả lời “đã SSO” không đủ cho (2) hoặc (3).

### OID và tên thường gặp

- Kerberos V5 GSS mechanism: `1.2.840.113554.1.2.2`.
- SPNEGO: `1.3.6.1.5.5.2`.
- GSS host-based service name ví dụ `HTTP@api.example.com`; cơ chế Kerberos thường ánh xạ sang service principal `HTTP/api.example.com@REALM`. Tên service, FQDN, realm và keytab phải thống nhất với KDC.
- Tên SASL `GSSAPI` theo RFC 4752 chỉ **Kerberos V5**, dù GSS-API nói chung hỗ trợ nhiều cơ chế. Tên HTTP `Negotiate` là tên scheme HTTP, không phải tên một principal hay một cipher.

Không đồng nhất một OID đã *yêu cầu* với cơ chế cuối cùng đã *thiết lập*. Khi chọn SPNEGO, xác thực phải đi tới ngữ cảnh hoàn tất; sau đó mới kiểm tra cơ chế thực, tên đối tác và các trạng thái bảo vệ mà triển khai cung cấp.

## 2. SPNEGO thương lượng cơ chế như thế nào?

RFC 4178 mô tả SPNEGO như một lớp thương lượng có bảo vệ nằm trên GSS-API:

1. Initiator tạo `NegTokenInit` chứa danh sách các cơ chế nó hỗ trợ theo thứ tự ưu tiên; có thể kèm **optimistic token** của cơ chế ưu tiên để giảm số lượt đi-về.
2. Acceptor chọn một cơ chế nằm trong danh sách mà nó hỗ trợ hoặc từ chối. Phản hồi `NegTokenResp` có trạng thái như `accept-incomplete`, `accept-completed`, `reject` hay `request-mic` cùng token của cơ chế đã chọn nếu cần.
3. Hai phía trao đổi tiếp token cho cơ chế bên dưới (ví dụ Kerberos AP-REQ/AP-REP) đến khi ngữ cảnh hoàn tất. Không được coi một token khởi đầu hay HTTP 401 đơn lẻ là bằng chứng xác thực xong.
4. Khi cơ chế đã chọn cung cấp integrity, SPNEGO dùng `mechListMIC` trong các trường hợp RFC yêu cầu để bảo vệ danh sách cơ chế khỏi sửa đổi/downgrade. MIC có thể là tùy chọn khi sự lựa chọn đã là ưu tiên của cả hai phía; nếu cơ chế không có integrity thì lớp MIC này không có sẵn.

`mechListMIC` bảo vệ **quá trình chọn cơ chế**, không mã hóa payload của ứng dụng. Nếu policy bắt buộc Kerberos thì phải từ chối các kết quả khác, dù SPNEGO thương lượng “thành công”. Đừng tự bật fallback sang cơ chế yếu hơn khi Kerberos thất bại.

### Ví dụ luồng HTTP Negotiate

```text
Browser/Java client                 Server HTTP (acceptor)        KDC
        | GET /private                     |                       |
        |--------------------------------->|                       |
        | 401 WWW-Authenticate: Negotiate  |                       |
        |<---------------------------------|                       |
        | lấy ticket cho HTTP/server       |                       |
        |<------------------------------------------------------->|
        | Authorization: Negotiate <base64 GSS token>              |
        |--------------------------------->|                       |
        |<---- 401/200 + WWW-Authenticate: Negotiate <token>? ----|
        | tiếp tục initSecContext() cho tới khi established       |
        |<================================>|                       |
        |     application authorization sau authentication         |
```

Đây là luồng khái quát: có thể có thêm vòng trao đổi; token cuối từ server đôi khi đi cùng phản hồi 200 và **client vẫn phải xử lý**. `Authorization: Negotiate` chứa token nhị phân đã Base64 hóa, **không phải** “Bearer token” tùy ý tái sử dụng. Server phải kiểm tra context, principal, target service và chính sách truy cập trước khi trả dữ liệu riêng tư.

Theo RFC 4559, HTTP Negotiate phục vụ authentication; nó không bảo vệ các HTTP header/body. Vì vậy triển khai HTTP thực tế cần **HTTPS/TLS** (và TLS đến từng hop nhạy cảm), giới hạn nơi nhận token, chống ghi log token và bảo vệ phản hồi HTTP theo chính sách.

### SPNEGO có phải Kerberos không?

Không. SPNEGO là bộ chọn; Kerberos có thể là cơ chế được chọn. Một số hệ sinh thái SPNEGO/HTTP Negotiate có thể tương tác với NTLM, nhưng tập cơ chế thực tế phụ thuộc provider/nền tảng. Nhà cung cấp SunJGSS trong JDK chủ yếu triển khai Kerberos và SPNEGO trên Kerberos; **không suy ra** Java mặc định sẽ hỗ trợ NTLM qua JGSS. Nếu ứng dụng yêu cầu Kerberos, hãy cấu hình allow-list và kiểm tra kết quả thiết lập; đừng dựa vào chuỗi “Negotiate” ở header làm bằng chứng cơ chế đã chọn.

## 3. Credential delegation: cho phép làm gì và không cho phép làm gì?

Xét luồng ba tầng:

```text
Alice (initiator)  -- GSS context A -->  Frontend (acceptor)
                                           |
                                           | GSS context B tới backend
                                           | dùng delegated credential của Alice
                                           v
                                        Backend
```

Ở context A, Alice xác thực với Frontend. Nếu Alice **chủ động yêu cầu**, cơ chế **hỗ trợ**, credentials **có khả năng chuyển giao** và policy **cho phép**, frontend có thể nhận một `GSSCredential` ủy quyền. Sau đó frontend tạo context B tới Backend dưới danh tính Alice. Backend vẫn phải xác thực frontend/ủy quyền, và **tự đánh giá Alice được làm gì**. Frontend nhận được credential có thể trở thành điểm rủi ro lớn: nếu bị chiếm quyền, kẻ tấn công có thể lạm dụng khả năng đại diện người dùng trong thời hạn và phạm vi được cấp.

Điều kiện trong thực tế phụ thuộc Kerberos và môi trường: ticket có khả năng forward (`forwardable`), chính sách client/KDC/service, thuộc tính delegation của service, cross-realm trust, credential cache và provider. Yêu cầu delegation trong API **không bảo đảm** đã được cấp. Trường hợp không có delegation là kết quả bình thường, không nên tự động “sửa” bằng tài khoản service toàn quyền.

### Phân biệt các loại quyền

| Khái niệm | Ví dụ | Điều cần nhớ |
| --- | --- | --- |
| Authentication | Alice đăng nhập vào Frontend bằng Kerberos | Chứng minh danh tính trong context A |
| Authorization | Frontend cho Alice đọc báo cáo số 42 | Quy tắc ứng dụng; Kerberos không ra quyết định nghiệp vụ |
| SASL authorization identity (`authzid`) | Client yêu cầu hành động theo một tên authorization nhất định | Server phải kiểm tra quyền ủy nhiệm; không phải GSS credential delegation |
| GSS credential delegation | Frontend dùng `getDelegCred()` để gọi Backend với credential được chuyển giao | Cho phép xác thực tiếp; phải giới hạn vòng đời, đích và quyền |
| Proxy identity/headers | Gateway gửi `X-User` cho microservice | Chỉ đáng tin khi có quan hệ tin cậy, xác thực và bảo vệ hop gateway → service |
| Kerberos constrained delegation | Cơ chế ví dụ S4U2proxy giới hạn service đích | Là mô hình cấu hình/protocol riêng; không đồng nhất với `requestCredDeleg(true)` trong JGSS |

Đừng nhầm delegation với “forward lại header `Authorization: Negotiate`”. Token Kerberos được tạo cho một target/context; gửi nguyên token sang service thứ hai không biến nó thành credential cho service thứ hai. Tương tự, tên `authzid` trong SASL không cho phép client tự lấy quyền của người dùng khác nếu server kiểm tra đúng.

### Quy tắc thiết kế delegation an toàn

1. Mặc định **không yêu cầu** delegation. Chỉ bật cho một luồng nghiệp vụ có nhu cầu rõ và người dùng/administrator cho phép.
2. Ràng buộc service đích (SPN/FQDN), principal được chấp nhận, phạm vi action và quyền backend. Ưu tiên constrained delegation hoặc cách thức cấp quyền phạm vi hẹp khi nền tảng hỗ trợ.
3. Sau khi context A hoàn tất, kiểm tra `getCredDelegState()`; acceptor chỉ gọi `getDelegCred()` sau khi established. `null` nghĩa là không có credential đã chuyển giao.
4. Không log, serialize ra JSON, lưu DB hoặc chuyển qua message queue credential/ticket. Không gộp credential của nhiều người dùng vào pool toàn cục. Đóng context và `dispose()` credential khi hết sử dụng.
5. Authorization ở Backend phải dùng identity đã xác thực tại Backend, không tin chuỗi tên do Frontend tự khai. Có audit theo principal, service đích và hành động, tránh ghi token hoặc secret.
6. Cần kế hoạch khi không được delegation: trả lỗi nghiệp vụ rõ ràng hoặc dùng flow cấp quyền khác được thiết kế riêng. Không tự “fallback” sang quyền cao hơn.

## 4. Java 17+: thiết lập context SPNEGO và kiểm tra điều kiện

API chuẩn: module `java.security.jgss`, package `org.ietf.jgss`. Class `GSSManager` tạo `GSSName`, `GSSCredential`, `GSSContext`. JGSS **không truyền token qua mạng**; ứng dụng hoặc HTTP/SASL library phải đóng gói/gửi/nhận đúng theo giao thức.

Ví dụ dưới đây là **chương trình Java 17 độc lập, biên dịch/chạy được** để xác nhận OID và cung cấp hàm tạo context phía client. Hàm tạo context chưa thực hiện handshake; kết nối Kerberos thực cần KDC, principal, credential và kênh trao đổi token.

```java
// SpnegoContextSetup.java
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

public final class SpnegoContextSetup {
    private static final Oid SPNEGO = oid("1.3.6.1.5.5.2");
    private static final Oid KERBEROS = oid("1.2.840.113554.1.2.2");

    private SpnegoContextSetup() {}

    private static Oid oid(String value) {
        try {
            return new Oid(value);
        } catch (GSSException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Gọi trong môi trường đã có Kerberos credentials phù hợp.
    public static GSSContext createClient(String host, boolean allowDelegation)
            throws GSSException {
        GSSManager manager = GSSManager.getInstance();
        GSSName target = manager.createName(
                "HTTP@" + host, GSSName.NT_HOSTBASED_SERVICE);
        GSSContext context = manager.createContext(
                target, SPNEGO, null, GSSContext.DEFAULT_LIFETIME);

        // Chỉ có hiệu lực khi gọi trước initSecContext() lần đầu.
        context.requestMutualAuth(true);
        context.requestInteg(true);
        context.requestConf(true);
        context.requestReplayDet(true);
        context.requestSequenceDet(true);
        context.requestCredDeleg(allowDelegation);
        return context;
    }

    // Chỉ gọi sau khi token exchange đã hoàn tất.
    public static void requireClientPolicy(GSSContext context)
            throws GSSException {
        if (!context.isEstablished()) {
            throw new SecurityException("GSS context not established");
        }
        if (!context.getMutualAuthState()) {
            throw new SecurityException("Mutual authentication unavailable");
        }
        // Provider phải báo được cơ chế Kerberos cuối cùng; nếu không, từ chối.
        if (!KERBEROS.equals(context.getMech())) {
            throw new SecurityException("Selected GSS mechanism is not Kerberos");
        }
    }

    public static void main(String[] args) {
        System.out.println("SPNEGO: " + SPNEGO);
        System.out.println("Kerberos: " + KERBEROS);
    }
}
```

Biên dịch và chạy (JDK 17+, không cần KDC vì `main` chỉ in OID):

```bash
javac SpnegoContextSetup.java
java SpnegoContextSetup
```

Stdout chính xác:

```text
SPNEGO: 1.3.6.1.5.5.2
Kerberos: 1.2.840.113554.1.2.2
```

Đối với hệ thống Kerberos thật, hàm `createClient` được gọi trong một identity/`Subject` đã có credential phù hợp hoặc cơ chế lấy credential của provider đã cấu hình. `null` ở tham số credential nghĩa là để provider dùng credential mặc định phù hợp, **không** tự động tạo danh tính mới. Tên `HTTP@host` phải trỏ đúng SPN của server, không lấy host từ input không tin cậy. Sử dụng `requestCredDeleg(false)` trừ khi có yêu cầu delegation cụ thể.

### Exchange token: hiểu đúng state machine

**Đoạn dưới đây là mã minh họa nằm trong phương thức xử lý kênh I/O do bạn tự viết**, không phải chương trình độc lập; `sendToken` và `receiveToken` là hai hàm của giao thức vận chuyển.

```java
// Initiator (phần thân phương thức; context đã được tạo):
byte[] incoming = new byte[0];
while (!context.isEstablished()) {
    byte[] outgoing = context.initSecContext(
            incoming, 0, incoming.length);
    if (outgoing != null) {
        sendToken(outgoing);             // Có thể vẫn phải gửi token cuối.
    }
    if (!context.isEstablished()) {
        incoming = receiveToken();       // Token trả về từ đúng acceptor.
    }
}
requireClientPolicy(context);
// Cuối phiên: context.dispose() trong finally.
```

Ở acceptor, tạo credential `ACCEPT_ONLY` phù hợp với principal/keytab (hoặc credential mặc định theo provider), dùng `manager.createContext(acceptorCredential)`, liên tục gọi `acceptSecContext(incoming, 0, incoming.length)`, trả token đầu ra nếu có, đến khi `isEstablished()`. Sau đó xác minh `getSrcName()`, `getMech()` và chính sách ứng dụng. Không trộn token giữa request, connection, user hoặc context.

Lưu ý: token GSS là **opaque**; đừng phân tích ASN.1 hay Base64 để tự suy ra authentication success hoặc principal. Định dạng truyền token (framing, thứ tự, timeout, kích thước tối đa) do giao thức bên ngoài chịu trách nhiệm. Trong HTTP Negotiate, library HTTP thường điều khiển vòng 401/200 và Base64; trong socket tùy chỉnh, developer phải thiết kế framing an toàn.

### Acceptor đọc delegated credential

**Fragment minh họa sau chỉ được gọi sau khi `acceptSecContext` hoàn tất thành công**, trên context phía acceptor và sau khi server đã quyết định yêu cầu này có được phép dùng delegation hay không:

```java
if (!acceptorContext.isEstablished()) {
    throw new SecurityException("Incomplete client authentication");
}
GSSName authenticatedUser = acceptorContext.getSrcName();
if (!isAuthorized(authenticatedUser)) { // Hàm policy của ứng dụng.
    throw new SecurityException("User is not authorized");
}

if (acceptorContext.getCredDelegState()) {
    GSSCredential delegated = acceptorContext.getDelegCred();
    if (delegated != null) {
        try {
            GSSManager manager = GSSManager.getInstance();
            GSSName backend = manager.createName(
                    "ldap@directory.internal.example",
                    GSSName.NT_HOSTBASED_SERVICE);
            GSSContext downstream = manager.createContext(
                    backend, new Oid("1.2.840.113554.1.2.2"),
                    delegated, GSSContext.DEFAULT_LIFETIME);
            try {
                downstream.requestMutualAuth(true);
                downstream.requestCredDeleg(false); // Không tự động chuyển tiếp.
                // Thực hiện exchange token với backend theo protocol thực tế.
                // Chỉ truy cập backend sau khi downstream.isEstablished().
            } finally {
                downstream.dispose();
            }
        } finally {
            delegated.dispose();
        }
    }
}
```

`isAuthorized` và token exchange tới backend là thành phần **phải được ứng dụng triển khai**, không có trong JDK. `delegated.dispose()` chỉ nên gọi khi application sở hữu vòng đời credential và không còn context nào cần nó. Trong hệ thống thật, thêm kiểm tra principal đích, policy delegation, account mapping, timeout và exception handling. Không coi `getCredDelegState()==true` là authorization của nghiệp vụ.

### Request flag khác negotiated flag

| Lời gọi phía initiator, **trước** `initSecContext` | Đọc kết quả **sau** `isEstablished` |
| --- | --- |
| `requestMutualAuth(true)` | `getMutualAuthState()` |
| `requestCredDeleg(true/false)` | `getCredDelegState()` |
| `requestInteg(true)` | `getIntegState()` |
| `requestConf(true)` | `getConfState()` |
| `requestReplayDet(true)` | `getReplayDetState()` |
| `requestSequenceDet(true)` | `getSequenceDetState()` |

Flag được **yêu cầu** không phải bằng chứng cơ chế đã đáp ứng. Nếu policy cần thuộc tính nào thì kiểm tra trạng thái đã thương lượng và fail closed khi thiếu. `getMech()` có thể thay đổi trong lúc thương lượng; kiểm tra sau khi context hoàn tất và xác nhận behavior của provider. `getCredDelegState()` chỉ có câu trả lời dứt khoát sau khi established; `getDelegCred()` được dùng ở acceptor, có thể trả `null`.

`requestConf(true)` chỉ yêu cầu khả năng confidentiality cho **GSS `wrap()`**; nó **không mã hóa HTTP body** chỉ vì đã gọi flag đó. Nếu ứng dụng dùng `wrap(..., new MessageProp(0, true))` để bảo vệ bản tin riêng, phải xác nhận context có confidentiality, bên nhận `unwrap()`, và giao thức truyền đúng token. `getMIC()/verifyMIC()` cung cấp integrity, không cung cấp secrecy. Trong HTTP, HTTPS/TLS vẫn cần được cấu hình đúng.

## 5. SASL GSSAPI và SPNEGO: không nhập làm một

Một ứng dụng có thể gặp cả hai API:

- **JGSS trực tiếp hoặc HTTP Negotiate:** chọn OID SPNEGO và trao token cho peer để lập `GSSContext`; cơ chế thực có thể là Kerberos tùy provider/policy.
- **SASL `GSSAPI` (RFC 4752):** cơ chế SASL **cố định Kerberos V5 GSS-API** và thương lượng tầng bảo vệ dữ liệu sau authentication. Các giao thức như LDAP quy định cách đóng gói/trao challenge-response; `SaslClient`/`SaslServer` chỉ xử lý logic SASL.
- **GS2 family (RFC 5801):** bridge GSS-API sang SASL khác với SASL `GSSAPI` kiểu RFC 4752. Ví dụ `GS2-KRB5` và `GS2-KRB5-PLUS`; biến thể PLUS dùng channel binding. Không giả định mọi SASL provider/giao thức đều hỗ trợ GS2.

Trong Java (`javax.security.sasl`), `Sasl.QOP` có các giá trị:

| QOP | Bảo vệ do SASL security layer |
| --- | --- |
| `auth` | Xác thực; **không** thêm tầng bảo vệ dữ liệu SASL |
| `auth-int` | Xác thực + toàn vẹn cho message được `wrap()/unwrap()` |
| `auth-conf` | Xác thực + toàn vẹn + bí mật cho message được `wrap()/unwrap()` |

Nếu không cấu hình `Sasl.QOP`, giá trị mặc định theo Javadoc Java là `auth`. Đặt danh sách ưu tiên không ép server chấp nhận lựa chọn cao nhất; phải đọc **`getNegotiatedProperty(Sasl.QOP)` sau khi `isComplete()`** và từ chối khi kết quả thấp hơn yêu cầu. Với `auth-int`/`auth-conf`, ứng dụng/giao thức phải dùng `SaslClient.wrap()/unwrap()` hoặc `SaslServer.wrap()/unwrap()` cho dữ liệu tiếp theo; chỉ thương lượng xong rồi gửi plaintext là sai ranh giới bảo vệ.

Fragment minh họa (nằm trong phương thức có xử lý `SaslException` và vòng challenge/response của giao thức):

```java
Map<String, String> properties = Map.of(
        Sasl.QOP, "auth-conf,auth-int,auth");

SaslClient client = Sasl.createSaslClient(
        new String[] { "GSSAPI" },
        null,             // authorizationId: không yêu cầu giả danh ai.
        "ldap",           // protocol theo SASL profile.
        "directory.example.com",
        properties,
        null);            // CallbackHandler tùy cấu hình provider.

if (client == null) {
    throw new SaslException("No GSSAPI SASL provider");
}

// Chuyển challenge/response đến khi client.isComplete() theo protocol LDAP.
if (!client.isComplete()) {
    throw new SaslException("SASL exchange incomplete");
}
String negotiatedQop = (String) client.getNegotiatedProperty(Sasl.QOP);
if (!"auth-conf".equals(negotiatedQop)) {
    throw new SaslException("Required SASL confidentiality unavailable");
}
// Tiếp theo: truyền dữ liệu qua client.wrap()/unwrap() theo protocol.
// Cuối phiên: client.dispose().
```

Fragment cần import `java.util.Map`, `javax.security.sasl.Sasl`, `SaslClient` và `SaslException`. Ở luồng thực, bạn phải **thực hiện** exchange qua LDAP hoặc protocol liên quan trước khi kiểm tra `isComplete()`; snippet không tự tiến hành authentication.

SASL authorization identity khác với credential delegation: máy chủ SASL phải tự xét client đã xác thực có được hành động dưới authzid yêu cầu không. Không tự động tạo `GSSCredential` chuyển tiếp khi chỉ sử dụng `Sasl.createSaslClient("GSSAPI")`.

## 6. Những ranh giới bảo vệ dễ bị hiểu nhầm

### 6.1. Xác thực đối tác không bằng mã hóa toàn bộ kết nối

Kerberos GSS có thể xác thực và tạo khóa cho context; `wrap`/`getMIC` chỉ bảo vệ **những bản tin ứng dụng chủ động đưa qua API tương ứng**. Một request HTTP chứa token SPNEGO nhưng truyền body không qua `wrap` vẫn cần TLS. TLS bảo vệ đường truyền giữa **hai TLS endpoints**, không tự bảo vệ dữ liệu sau khi proxy giải mã hoặc khi lưu vào log/DB.

### 6.2. SPNEGO negotiation protection không là chống downgrade toàn hệ thống

`mechListMIC` của RFC 4178 có điều kiện theo integrity của underlying mechanism và tình huống thương lượng. Nó không thay policy “chỉ cho Kerberos”, không bảo vệ việc server ứng dụng quảng cáo các scheme HTTP khác, và không tự bảo vệ HTTP redirect hoặc DNS. Khi Kerberos bắt buộc: chỉ chấp nhận cơ chế thực phù hợp, không retry im lặng sang NTLM/Basic, và xác minh service principal đích.

### 6.3. Mutual authentication khác với “đã có TLS”

Kerberos mutual auth (nếu được negotiated) chứng minh acceptor có thông tin phù hợp để kết thúc cơ chế Kerberos. TLS server authentication kiểm tra certificate của TLS endpoint. Hai thuộc tính gắn với **hai lớp/đối tượng** khác nhau, có thể trỏ tới hai chủ thể khác nhau khi có reverse proxy. Nếu yêu cầu ràng buộc xác thực ứng dụng vào chính TLS channel, cần channel binding được cả hai đầu hỗ trợ và cùng cấu hình; không mặc định JGSS hay HTTP client tự làm việc đó.

Java GSS cung cấp `GSSContext.setChannelBinding(ChannelBinding)` trước handshake; dữ liệu binding phải được lấy từ kênh đã xác thực và thống nhất ở hai phía. Với các tiêu chuẩn SASL hiện đại, RFC 9266 xác định `tls-exporter` cho TLS 1.3; RFC 5929 mô tả các loại binding trước đó. Chọn đúng loại và dữ liệu theo **protocol/provider thực tế**, không tự chế chuỗi chứng chỉ hoặc gắn cờ “channel bound” khi chưa được peer xác nhận.

### 6.4. Endpoint/identity scope

Ticket/service name phải khớp service đích. Credential của user cho `HTTP/front.example.com` không có nghĩa backend `ldap/directory.example.com` đã xác thực user. Khi proxy nhận Negotiate, backend chỉ thấy hop do proxy tạo; nếu backend cần danh tính user, phải có delegation đã được policy cho phép hoặc một giao thức ủy quyền/identity propagation được bảo vệ và xác minh riêng. Header `X-User` do bên ngoài tự gửi không đáng tin.

### 6.5. Replay, lifetime và token handling

Kerberos và GSS context có cơ chế phát hiện replay/sequence tùy cấu hình, nhưng không thay chống replay ở nghiệp vụ (ví dụ chuyển tiền hai lần), idempotency key, authorization từng request hoặc thời hạn session. Token có thể lộ cấu trúc/metadata và phải coi là dữ liệu nhạy cảm; không đưa `Authorization: Negotiate`, vé Kerberos, keytab, dump credential hoặc SASL challenge vào log. Giới hạn kích thước token, số vòng handshake, timeout và số context đồng thời để tránh DoS.

## 7. Ma trận quyết định theo nhu cầu

| Yêu cầu | Cơ chế/cấu hình cần thiết | Kiểm tra bắt buộc |
| --- | --- | --- |
| User đăng nhập SSO vào web nội bộ | HTTPS + HTTP Negotiate/SPNEGO với Kerberos được cho phép | Context hoàn tất, principal, SPN và authorization |
| Giữ bí mật HTTP headers/body | TLS đến các endpoint/hop cần bảo vệ | Certificate, hostname, TLS policy, nơi kết thúc TLS |
| Bảo vệ message của GSS socket tùy chỉnh | `GSSContext.wrap/unwrap` hoặc MIC thích hợp | `getIntegState/getConfState`, tính đúng framing và token |
| LDAP dùng SASL GSSAPI | SASL challenge/response với Kerberos | `isComplete`, `authzid`, QOP đã thương lượng; TLS nếu cần |
| Backend hành động thay user | Credential delegation hoặc giải pháp delegation được kiểm soát | Sự đồng ý/policy, `getCredDelegState`, identity, scope và audit |
| Chống MITM giữa GSS/SASL với TLS | Channel binding được giao thức/provider hỗ trợ | Hai phía bind cùng TLS channel, thất bại phải dừng |
| Chỉ cho phép Kerberos, tránh fallback | Allow-list GSS mechanism và HTTP authentication policy | Cơ chế cuối, không downgrade sang NTLM/Basic |

**Ví dụ lựa chọn:** Công ty có web portal dùng Negotiate. Người dùng Alice xem thông tin của mình từ database. Nếu portal chỉ đọc dữ liệu bằng service account với **authorization của portal theo Alice**, không nhất thiết cần Kerberos delegation. Nếu database bắt buộc xác thực từng user bằng Kerberos, mới xem xét chuyển giao credential/ủy quyền ràng buộc service đích. Hai mô hình đều cần policy ở database hoặc portal; việc delegation làm gia tăng phạm vi quyền nếu sai cấu hình.

## 8. Triển khai, xử lý lỗi và kiểm thử

### Checklist triển khai Java + Kerberos/SPNEGO

1. **Danh tính:** xác định user principal, FQDN và service principal thực (`HTTP/...`, `ldap/...`), realm và quan hệ trust. Bảo đảm DNS/canonicalization không khiến client tạo ticket cho host khác mong muốn.
2. **Credential store:** bố trí JAAS/Kerberos login, ticket cache, keytab và quyền đọc file cho đúng process. Chỉ bật debug khi điều tra, xóa log trước khi chia sẻ.
3. **Negotiate:** quyết định cơ chế được phép. Tạo context đúng OID, thiết lập yêu cầu mutual auth, integrity, delegation trước lần `initSecContext` đầu tiên. Không coi một HTTP 401 là lỗi cuối cùng; xử lý mọi token phản hồi.
4. **Policy sau handshake:** xác minh context established, negotiated mechanism, expected acceptor/service và authenticated user; kiểm tra quyền tài nguyên riêng.
5. **Bảo vệ dữ liệu:** xác định chính xác hop TLS, việc dùng GSS/SASL `wrap`, channel binding, proxy và log. Với dữ liệu nhạy cảm, kiểm tra confidentiality tại lớp **thực sự chuyển dữ liệu**.
6. **Delegation:** khai báo luồng nào cần, người nào được, service nào được nhận, lifetime nào; test “không được delegation”. Không tự động yêu cầu lại hoặc nâng quyền khi `getDelegCred()` bằng `null`.
7. **Vòng đời:** mỗi context gắn đúng phiên/identity; bảo vệ chống concurrency/race, đặt timeout và giới hạn token; dispose context/credential khi xong. Không dùng chung mutable `GSSContext` giữa các request độc lập.

### Tình huống lỗi và hướng chẩn đoán

| Dấu hiệu | Nguyên nhân cần kiểm tra | Cách xử lý |
| --- | --- | --- |
| `GSSException: No valid credentials` | Chưa login Kerberos, credential cache không đúng process/`Subject`, ticket hết hạn | Kiểm tra credential source và principal; không đưa password vào code |
| `KRB_AP_ERR_MODIFIED`/service ticket không chấp nhận | SPN trỏ nhầm service, keytab/kvno/key không khớp, duplicate SPN | So lại target FQDN, SPN, keytab và KDC |
| `Clock skew too great` | Client/server/KDC lệch giờ | Đồng bộ thời gian theo chính sách hệ thống |
| HTTP lặp 401 Negotiate | Không hoàn tất token exchange, server không có acceptor cred, proxy bỏ header, sai SPN | Trace trạng thái/hop ở mức an toàn; không log raw token |
| Auth thành công nhưng backend từ chối | Chưa delegation, backend kiểm tra role riêng, principal đích sai | Kiểm tra `getCredDelegState`, backend identity/authorization |
| Có `auth` nhưng cần confidentiality | SASL QOP đã thương lượng thấp hơn yêu cầu | Fail closed hoặc dùng kênh TLS được chấp nhận theo policy |
| Channel binding mismatch | Hai phía lấy binding từ TLS connection khác nhau, hoặc loại binding không tương thích | Kiểm tra TLS termination và hỗ trợ provider/protocol |

Lệnh vận hành như `klist`, `kinit` hoặc `setspn` tùy hệ điều hành và môi trường có thể giúp kiểm tra ticket/SPN; kết quả phải xử lý như thông tin bảo mật. Không suy ra cơ chế, quyền hay tính bảo mật từ việc một command trả exit code 0.

### Bộ tình huống QA tối thiểu

- **Đường tốt:** client/server Kerberos đúng SPN; context hoàn tất; user được authorization đúng tài nguyên.
- **Không có common mechanism:** từ chối; không fallback sang cơ chế bị cấm.
- **Sai SPN/keytab/realm hoặc token bị sửa:** không cấp quyền; không tạo session ứng dụng.
- **Không bật delegation:** acceptor không nhận delegated credential; backend được xử lý theo phương án không delegation.
- **Có yêu cầu delegation nhưng policy/ticket không cho phép:** không tự nâng quyền hoặc chia sẻ tài khoản service toàn quyền.
- **Delegation được phép:** chỉ gọi backend đã cho phép; principal và audit khớp; credential không còn được dùng sau kết thúc phiên.
- **SASL QOP thấp:** ứng dụng cần `auth-conf` phải từ chối `auth`/`auth-int`; kiểm tra rằng dữ liệu hậu xác thực thực sự đi qua bảo vệ đã chọn.
- **Proxy/TLS:** thử kết thúc TLS tại gateway, đổi channel binding, mất header ở hop sau và HTTP không TLS; xác nhận policy phát hiện sai phạm và bảo vệ đúng phạm vi.
- **Replay/concurrency:** gửi lại yêu cầu nghiệp vụ và token không hợp lệ, thử quá số vòng/token size; hệ thống không làm lộ credential, không nhầm lẫn danh tính giữa phiên.

## 9. Những kết luận cần nhớ

1. **SPNEGO chọn cơ chế GSS**; chất lượng authentication, integrity, confidentiality và delegation phụ thuộc cơ chế được chọn và policy đã được thực thi.
2. **Delegation khác authorization**: delegated `GSSCredential` có thể cho phép hop B xác thực tới hop C dưới danh tính người dùng, nhưng cả B và C vẫn phải giới hạn quyền. `requestCredDeleg(true)` là yêu cầu, không phải chứng nhận đã cấp.
3. **Mỗi lớp bảo vệ một phạm vi riêng:** HTTP Negotiate chở token; Kerberos xác thực và có khả năng bảo vệ message GSS; SASL GSSAPI có QOP cho dữ liệu đi qua security layer; TLS bảo vệ các TLS hop; ứng dụng tự quyết định quyền truy cập.
4. **Kiểm tra sau khi thương lượng, fail closed:** `isEstablished()`/`isComplete()`, cơ chế/QOP/flag thật, principal, TLS/channel binding và application authorization. Không thay bằng giả định “SSO thành công nên an toàn”.

## Tài liệu chuẩn để đọc sâu

- [RFC 4178 — SPNEGO](https://www.rfc-editor.org/rfc/rfc4178): danh sách cơ chế, token và `mechListMIC`.
- [RFC 4559 — HTTP Negotiate](https://www.rfc-editor.org/rfc/rfc4559): quy trình 401/Authorization và ranh giới bảo vệ HTTP.
- [RFC 4121 — Kerberos V5 GSS mechanism](https://www.rfc-editor.org/rfc/rfc4121): MIC, Wrap và bảo vệ theo message.
- [RFC 4752 — SASL GSSAPI (Kerberos V5)](https://www.rfc-editor.org/rfc/rfc4752): SASL GSSAPI và security layer.
- [RFC 5801 — SASL GS2 family](https://www.rfc-editor.org/rfc/rfc5801) và [RFC 9266 — TLS 1.3 channel binding](https://www.rfc-editor.org/rfc/rfc9266): biến thể channel binding.
- [Java 17 `GSSContext`](https://docs.oracle.com/en/java/javase/17/docs/api/java.security.jgss/org/ietf/jgss/GSSContext.html), [`GSSManager`](https://docs.oracle.com/en/java/javase/17/docs/api/java.security.jgss/org/ietf/jgss/GSSManager.html), [`Sasl`](https://docs.oracle.com/en/java/javase/17/docs/api/java.security.sasl/javax/security/sasl/Sasl.html): API và trạng thái negotiated.


</details>

- [Quay lại đầu trang](#back-to-top)
