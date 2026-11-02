<a id="back-to-top"></a>

# 1. Nền tảng

## Menu
- [1.1 An toàn thông tin và mật mã giải quyết những vấn đề nào trong ứng dụng Java](#security-fundamentals-001)
- [1.2 Mô hình đe dọa, tài sản cần bảo vệ và ranh giới tin cậy](#security-fundamentals-002)
- [1.3 Phân biệt tính bí mật, tính toàn vẹn, xác thực và phân quyền](#security-fundamentals-003)
- [1.4 Phân biệt mã hóa, hàm băm, MAC, chữ ký số và mã hóa biểu diễn](#security-fundamentals-004)
- [1.5 Quan hệ giữa khóa, thuật toán, tham số, giao thức và danh tính](#security-fundamentals-005)
- [1.6 Bảo vệ dữ liệu khi truyền, khi lưu trữ và trong bộ nhớ](#security-fundamentals-006)
- [1.7 Bản đồ JCA, JCE, JSSE, JAAS, PKI và các công cụ của JDK](#security-fundamentals-007)
- [1.8 Môi trường thực hành Java 21 và cách đối chiếu API với phiên bản JDK](#security-fundamentals-008)

## <a id="security-fundamentals-001">1.1 An toàn thông tin và mật mã giải quyết những vấn đề nào trong ứng dụng Java</a>

<details>
<summary>Xem chi tiết</summary>

# 1.1 An toàn thông tin và mật mã giải quyết những vấn đề nào trong ứng dụng Java?

Ứng dụng Java thường lưu tài khoản, xử lý giao dịch, truyền dữ liệu qua mạng và gọi các dịch vụ khác. Một chương trình chạy đúng chức năng vẫn có thể gây thiệt hại nếu người không được phép đọc dữ liệu, sửa thông điệp, giả mạo người gửi hoặc sử dụng thông tin bị đánh cắp. **An toàn thông tin** là mục tiêu bảo vệ hệ thống và dữ liệu trước các rủi ro đó; **mật mã học** cung cấp những cơ chế kỹ thuật quan trọng để đạt một số mục tiêu bảo vệ.

Bài này trả lời ba câu hỏi: *Cần bảo vệ điều gì? Cơ chế mật mã giải quyết được vấn đề nào? Chọn và kết hợp cơ chế đó ra sao trong một ứng dụng Java?* Đây là bài nền tảng về bài toán và cách tư duy; phần cấu hình thuật toán, giao thức hoặc hệ thống quản lý khóa ở quy mô lớn cần được thiết kế riêng theo yêu cầu thực tế.

## 1. Xác định tài sản, đối thủ và ranh giới tin cậy

Giả sử một ứng dụng Java phục vụ thương mại điện tử:

1. Người dùng đăng nhập và gửi đơn hàng từ trình duyệt đến Java API.
2. Java API đọc thông tin khách hàng trong cơ sở dữ liệu.
3. API gọi cổng thanh toán, nhận webhook thông báo giao dịch.
4. Hệ thống ghi log, tạo bản sao lưu và gửi thông báo cho người dùng.

Những tài sản cần bảo vệ gồm mật khẩu hoặc bộ xác minh mật khẩu, token phiên, dữ liệu cá nhân, số tiền/đơn hàng, khóa mật mã, cấu hình bí mật, log và bản sao lưu. Các nguy cơ có thể đến từ người nghe trộm đường truyền, bên sửa gói tin, kẻ có tài khoản bị chiếm đoạt, lỗi phân quyền, nhân sự vận hành hoặc máy chủ bị xâm nhập.

**Ranh giới tin cậy** là nơi dữ liệu chuyển giữa những thành phần có mức độ kiểm soát khác nhau: trình duyệt → API, API → cơ sở dữ liệu, API → dịch vụ thanh toán, hệ thống đang chạy → kho sao lưu. Mỗi lần dữ liệu đi qua ranh giới, cần tự hỏi:

- Ai được phép đọc, tạo, sửa hoặc gửi dữ liệu này?
- Người nhận chứng minh danh tính người gửi bằng cách nào?
- Nếu thông điệp bị thay đổi trên đường đi, ai sẽ phát hiện?
- Khóa và bí mật nằm ở đâu; nếu bị lộ thì hậu quả là gì?
- Điều gì vẫn cần làm dù đã mã hóa, chẳng hạn kiểm soát quyền truy cập hoặc chống gửi lại yêu cầu?

Không có một thuật toán đơn lẻ nào trả lời được toàn bộ những câu hỏi này.

## 2. Những mục tiêu an toàn thông tin mà ứng dụng cần đạt

| Mục tiêu | Câu hỏi cần giải quyết | Ví dụ trong ứng dụng Java |
| --- | --- | --- |
| **Tính bí mật (confidentiality)** | Người không có quyền có đọc được nội dung không? | Che dữ liệu khách hàng khi truyền hoặc lưu; dùng TLS và mã hóa dữ liệu nhạy cảm ở nơi cần thiết. |
| **Tính toàn vẹn (integrity)** | Nội dung có bị sửa trái phép mà không bị phát hiện không? | Phát hiện số tiền trong thông điệp đã bị đổi; dùng mã xác thực thông điệp, chữ ký số hoặc chế độ mã hóa có xác thực. |
| **Tính xác thực (authenticity)** | Thông điệp hoặc bên giao tiếp có thật sự đến từ chủ thể được tin cậy không? | Xác thực máy chủ HTTPS, kiểm tra chữ ký webhook và gắn khóa xác minh với danh tính đúng. |
| **Xác thực người dùng (authentication)** | Ai đang yêu cầu hành động này? | Đăng nhập bằng phương thức phù hợp, kiểm tra phiên/token và xác thực thêm khi rủi ro cao. |
| **Phân quyền (authorization)** | Chủ thể đã xác thực được phép làm gì? | Người dùng A không được đọc đơn hàng của B; kiểm tra quyền tại server, theo từng tài nguyên và thao tác. |
| **Khả dụng (availability)** | Dịch vụ có tiếp tục phục vụ hợp lệ khi gặp lỗi hay tấn công không? | Giới hạn tốc độ, dự phòng, khôi phục sao lưu, theo dõi và ứng phó sự cố. |
| **Khả năng quy trách nhiệm / chống chối bỏ có điều kiện** | Có bằng chứng đáng tin về một hành động hoặc chữ ký không? | Chữ ký số cùng quy trình bảo vệ khóa, gắn danh tính và lưu bằng chứng phù hợp; chỉ có chữ ký chưa tự bảo đảm tính pháp lý. |

Ba mục tiêu kinh điển là **CIA: Confidentiality – Integrity – Availability**. Trong hệ thống thực tế còn cần xác thực, phân quyền, truy vết và kiểm soát vận hành.

Phân biệt đặc biệt quan trọng: **xác thực** trả lời “bạn là ai?”, còn **phân quyền** trả lời “bạn được làm gì?”. Một token hợp lệ không có nghĩa người sở hữu token được truy cập mọi bản ghi. Mật mã có thể hỗ trợ xác thực nhưng không thay thế kiểm tra quyền theo nghiệp vụ.

## 3. Các cơ chế mật mã giải những bài toán khác nhau

### 3.1. Mã hóa đối xứng: giữ bí mật nội dung

Một **khóa bí mật chung** được dùng để mã hóa và giải mã. Trong Java, một lựa chọn phổ biến cho dữ liệu ứng dụng là **AES-GCM**, vì nó vừa bảo mật nội dung vừa kiểm tra tính toàn vẹn của bản mã và dữ liệu bổ sung được xác thực (AAD).

Dùng khi cần bảo vệ một trường dữ liệu nhạy cảm lưu trữ, file sao lưu, hoặc thông điệp mà hai phía đã chia sẻ khóa an toàn. Bản mã vẫn cần khóa mới đọc được. Các yếu tố cần quản lý: sinh khóa đủ mạnh, lưu khóa tách biệt khỏi dữ liệu, nonce/IV **không trùng với cùng một khóa**, lưu IV kèm bản mã, kiểm tra thẻ xác thực trước khi sử dụng kết quả giải mã.

**Giới hạn:** mã hóa không quyết định người dùng nào được đọc bản ghi sau khi server giải mã. Lộ khóa có thể khiến dữ liệu tương ứng bị lộ; mã hóa không sửa được lỗi phân quyền.

### 3.2. Mật mã bất đối xứng: khóa công khai và khóa riêng

Một cặp khóa gồm **public key** có thể chia sẻ và **private key** phải bảo mật. Các thuật toán khác nhau phục vụ mục tiêu khác nhau: RSA-OAEP có thể mã hóa lượng dữ liệu phù hợp; Ed25519 được dùng cho **chữ ký số**, không dùng để mã hóa; ECDH/X25519 phục vụ thỏa thuận khóa, không tự xác thực danh tính đối tác.

Trong ứng dụng thực tế, dữ liệu lớn thường được mã hóa bằng khóa đối xứng; cơ chế bất đối xứng có thể hỗ trợ trao đổi hoặc bao bọc khóa, xác minh chữ ký hay thiết lập phiên giao tiếp an toàn. Không nên tự ghép các phép toán thành một giao thức truyền tin riêng khi đã có TLS và thư viện đã được kiểm chứng.

### 3.3. Hàm băm mật mã: tạo dấu vân tay của dữ liệu

Hàm như **SHA-256** biến dữ liệu bất kỳ thành một giá trị có độ dài cố định. Dữ liệu thay đổi thường làm giá trị băm thay đổi; đây là công cụ cho fingerprint, kiểm tra tính toàn vẹn khi **giá trị băm kỳ vọng đến từ nguồn đáng tin**, và làm thành phần của những cơ chế khác.

**SHA-256 không phải mã hóa:** không có thao tác “giải mã hash”. Tuy nhiên, băm trần của thông điệp **không chứng minh ai đã gửi**: người sửa dữ liệu cũng có thể tính lại hash. CRC32 hay checksum thông thường còn không phải công cụ bảo vệ chống sửa đổi có chủ đích.

### 3.4. MAC/HMAC: phát hiện sửa đổi và xác thực bên biết khóa

**HMAC-SHA256** dùng khóa bí mật chung để tạo mã xác thực thông điệp. Người nhận có khóa kiểm tra được nội dung và MAC có khớp hay không. Phù hợp khi hai hệ thống tin cậy cùng giữ bí mật, ví dụ một kiểu webhook được nhà cung cấp quy định dùng HMAC.

HMAC **không mã hóa nội dung**. Vì cả hai bên cùng biết khóa, HMAC không chứng minh riêng một bên đã ký trước bên thứ ba. Bản thân HMAC cũng không ngăn việc gửi lại một thông điệp cũ hợp lệ; cần thêm thời gian, nonce, ID giao dịch, hạn hiệu lực hoặc tính idempotent theo giao thức.

### 3.5. Chữ ký số: xác minh nguồn và nội dung

Người ký sử dụng private key để tạo chữ ký trên **đúng chuỗi byte**; người kiểm tra sử dụng public key tương ứng để xác minh. Nếu nội dung bị thay đổi, chữ ký cũ sẽ không còn hợp lệ. Ví dụ: xác minh gói phát hành, tài liệu hoặc webhook được ký.

**Điều kiện bắt buộc:** public key phải được gắn với đúng danh tính qua kênh tin cậy, chứng chỉ hoặc cấu hình đã xác minh. Chữ ký không làm dữ liệu bí mật, không tự chứng minh thời điểm ký và không tự tạo giá trị pháp lý nếu khóa riêng bị chia sẻ, đánh cắp hoặc quy trình quản trị thiếu tin cậy.

### 3.6. Băm mật khẩu chuyên dụng: lưu bộ xác minh, không lưu mật khẩu

Mật khẩu người dùng không nên lưu dưới dạng plaintext, mã hóa có thể giải mã, hoặc SHA-256 một lần. Khi đăng ký, hệ thống tạo **salt ngẫu nhiên riêng cho mỗi mật khẩu** và tính hàm dẫn xuất/băm mật khẩu có chi phí phù hợp như **Argon2id** (thường qua thư viện được duy trì) hoặc **PBKDF2WithHmacSHA256** trong JDK nếu cần phương án phù hợp hạ tầng. Lưu salt, tham số thuật toán và kết quả; khi đăng nhập tính và đối chiếu theo cơ chế của thư viện.

Salt không cần giữ bí mật; vai trò là tránh các mật khẩu giống nhau có cùng kết quả và gây khó cho tấn công tính trước. **Pepper** nếu sử dụng là bí mật bổ sung cần quản lý tách khỏi cơ sở dữ liệu. Tham số chi phí cần điều chỉnh, kiểm thử và cập nhật theo thời gian; không tự thiết kế thuật toán băm mật khẩu.

### 3.7. Ngẫu nhiên an toàn, chứng chỉ và TLS

- **SecureRandom** tạo khóa, nonce, token ngẫu nhiên hoặc salt cần độ khó dự đoán. **Random**, thời gian hệ thống và số thứ tự tăng dần không phù hợp để sinh bí mật bảo mật.
- **Chứng chỉ X.509/PKI** giúp gắn public key với một danh tính khi chuỗi tin cậy và tên máy chủ được kiểm tra đúng.
- **TLS/HTTPS** bảo vệ kết nối giữa các điểm cuối cụ thể: xác thực máy chủ và cung cấp bảo mật, toàn vẹn đường truyền. Trong Java, thường sử dụng stack HTTP/TLS chuẩn (JSSE, HttpClient, framework) thay vì tự tạo giao thức mật mã.

TLS chỉ bảo vệ **từng kết nối**. Dữ liệu đã giải mã trong ứng dụng, ghi vào log hoặc chuyển tiếp qua kết nối khác vẫn cần bảo vệ tương ứng. Không vô hiệu hóa kiểm tra chứng chỉ hoặc hostname chỉ để hết lỗi kết nối.

## 4. Bản đồ “bài toán → cơ chế → trách nhiệm còn lại”

| Tình huống | Cơ chế phù hợp | Việc vẫn phải kiểm tra |
| --- | --- | --- |
| Trình duyệt gọi Java API qua Internet | HTTPS/TLS đúng chứng chỉ và hostname | Xác thực phiên, phân quyền, kiểm tra dữ liệu đầu vào, CSRF khi phù hợp, xử lý lỗi an toàn |
| Lưu mật khẩu tài khoản | Argon2id/PBKDF2 với salt riêng và tham số chi phí | Chống dò mật khẩu, MFA khi cần, chính sách đặt lại và bảo vệ phiên |
| Lưu số định danh hoặc file nhạy cảm | AES-GCM hoặc cơ chế mã hóa được quản lý | Quản lý khóa, quyền truy cập sau giải mã, lưu bản sao lưu, hạn chế log lộ dữ liệu |
| Nhận webhook thanh toán | Kiểm tra chữ ký số hoặc HMAC **theo đặc tả nhà cung cấp** | Xác thực đúng raw payload/canonical form, thời hạn, replay, idempotency, trạng thái giao dịch |
| Kiểm tra gói phần mềm tải xuống | Hash đối chiếu từ nguồn xác thực hoặc chữ ký phát hành | Tin cậy nguồn cấp hash/public key, quy trình phát hành, chống rollback |
| Phát hành và kiểm tra access token | Token opaque hoặc token có chữ ký phù hợp kiến trúc | Kiểm tra issuer/audience/expiry/thuật toán/khóa, thu hồi khi cần, quyền ở endpoint |
| Ghi log sự kiện nhạy cảm | Log tối thiểu, phân quyền, bảo toàn log; có thể dùng chữ ký/chuỗi băm khi có yêu cầu | Bảo vệ nơi lưu, đồng bộ thời gian, chống xóa/sửa, chính sách giữ dữ liệu |
| Chống nghẽn dịch vụ, DDoS hoặc lỗi logic | Rate limit, quota, timeout, cache, dự phòng, giám sát | Mã hóa và chữ ký **không giải quyết** tính khả dụng một cách trực tiếp |

Một yêu cầu có thể cần nhiều cơ chế cùng lúc. Ví dụ webhook có HMAC hợp lệ vẫn có thể là thông điệp cũ bị phát lại, hoặc yêu cầu xử lý nghiệp vụ đã hoàn tất. HMAC kiểm tra thông điệp, còn cơ chế nghiệp vụ phải kiểm tra trạng thái và xử lý lặp.

## 5. Hai câu hỏi bảo mật trên cùng một đơn hàng

Giả sử ứng dụng Java nhận lệnh thanh toán `order=42;amount=250000` và lưu thông tin giao dịch cho dịch vụ khác đọc. **Cùng một dữ liệu** nhưng có những câu hỏi khác nhau:

- **Người đọc trộm backup có biết số tiền không?** Đây là mục tiêu bí mật ở nơi lưu trữ. Có thể dùng mã hóa xác thực như AES-GCM với khóa tách khỏi backup; không lưu khóa cạnh bản mã dưới cùng quyền đọc.
- **Người nhận webhook có biết thông điệp bị sửa hoặc bên gửi có quyền xác thực không?** Đây là mục tiêu toàn vẹn và xác thực thông điệp. Nếu giao thức quy định một secret chung, HMAC-SHA256 phù hợp; nếu cần bên thứ ba độc lập xác minh, dùng chữ ký số với public key đã được ràng buộc vào người ký.
- **Một webhook có tag hoặc chữ ký đúng có được ghi nhận nhiều lần không?** Không. Giao thức/nghiệp vụ vẫn phải kiểm tra sự kiện đã xử lý, thời hạn/nonce và tính idempotent.
- **Người dùng đã đăng nhập có thể đọc đơn hàng bất kỳ không?** Không. Đây là bài toán phân quyền đối tượng; AES-GCM, HMAC, chữ ký và TLS không thể quyết định một mình.

Phải nêu được **đối thủ nào bị chặn**, **khóa nào do ai giữ** và **kiểm tra diễn ra ở đâu**. Một giải pháp bảo vệ tốt backup vẫn có thể thất bại nếu API giải mã rồi trả bản rõ cho người dùng khác; một webhook HMAC hợp lệ vẫn có thể là sự kiện cũ bị phát lại.

## 6. Phép thử chứng minh cơ chế đã giải đúng vấn đề

| Mục tiêu | Phép thử phù hợp | Điều không thể suy ra chỉ từ kết quả đó |
| --- | --- | --- |
| Bí mật dữ liệu lưu trữ | Đọc backup bằng tài khoản không có khóa giải mã phải không lấy được bản rõ | Người chạy API hợp lệ không thể lộ plaintext trong log/response |
| Phát hiện sửa đổi | Sửa ciphertext/tag/AAD thì giải mã AEAD phải từ chối | Client đã được phân quyền trên đúng bản ghi |
| Xác thực webhook | Sửa bytes payload nhưng giữ MAC/chữ ký cũ thì verification phải thất bại | Thông điệp chưa từng được xử lý trước đó |
| Phân quyền dữ liệu | User A đổi ID sang record của B phải bị từ chối ở backend | Các byte lưu trong DB đã được mã hóa |

Ở đây chỉ cần **định hướng lựa chọn và đánh giá**. Ví dụ Java triển khai từng primitive, cách so sánh hash/MAC/chữ ký và định dạng byte được trình bày riêng ở bài phân biệt các cơ chế; còn bài này giữ trọng tâm là **vấn đề an toàn nào buộc ứng dụng phải kết hợp cơ chế và quyền truy cập**.

## 7. Chọn đúng primitive trong Java và đặt vào đúng lớp bảo vệ

| Nhu cầu | API/cơ chế thường gặp | Điều cần nhớ |
| --- | --- | --- |
| Sinh giá trị khó đoán | **java.security.SecureRandom** | Dùng cho bí mật, IV/nonce/salt theo yêu cầu từng thuật toán. |
| Băm nội dung | **MessageDigest** với SHA-256 | Hash trần không chứng minh người gửi và không dùng làm password hash. |
| Xác thực thông điệp bằng khóa chung | **javax.crypto.Mac** với HmacSHA256 | So khớp MAC theo cách an toàn; gắn timestamp/nonce nếu cần chống replay. |
| Mã hóa dữ liệu | **javax.crypto.Cipher**, **KeyGenerator**, **GCMParameterSpec** | Dùng AEAD và quản lý khóa/IV/tag đúng cách. |
| Ký/xác minh | **java.security.Signature**, **KeyPairGenerator** | Kiểm tra nguồn public key; private key chỉ nằm ở bên ký. |
| Dẫn xuất khóa từ mật khẩu | **SecretKeyFactory** với PBKDF2WithHmacSHA256 hoặc thư viện Argon2id | Phân biệt password verifier với khóa mã hóa; chọn tham số theo threat model. |
| Lưu/đọc khóa theo thiết kế | **KeyStore** hoặc KMS/HSM của nền tảng | KeyStore là giao diện lưu khóa; chính sách truy cập, backup và rotation vẫn phải thiết kế. |
| Kết nối có TLS | **java.net.http.HttpClient**, JSSE, HTTPS của framework | Dùng kiểm tra chứng chỉ/hostname mặc định; bảo vệ từng chặng kết nối. |

Bảng trên là **bản đồ trách nhiệm**, không có nghĩa chỉ cần gọi một API là hệ thống an toàn. Kết quả bảo vệ còn phụ thuộc nguồn khóa, dữ liệu đầu vào, cách kiểm tra lỗi và ranh giới tin cậy của toàn hệ thống.

## 8. Ví dụ thiết kế ứng dụng: luồng đăng nhập và thanh toán

### Đăng ký và đăng nhập

1. Trình duyệt gửi thông tin qua HTTPS đến Java API.
2. Khi đăng ký, server sử dụng hàm băm mật khẩu chuyên dụng với salt riêng và tham số chi phí đã chọn; không lưu mật khẩu plaintext.
3. Khi đăng nhập, server xác minh mật khẩu dựa trên bản ghi đã lưu, áp dụng giới hạn thử sai và biện pháp chống chiếm đoạt tài khoản theo mức độ rủi ro.
4. Nếu hợp lệ, server cấp phiên/token với thời hạn, phạm vi, cách lưu và cơ chế thu hồi rõ ràng. Không log token hoặc password.
5. Mọi API đọc/sửa đơn hàng vẫn kiểm tra quyền sở hữu tài nguyên; không tin mỗi việc “đã đăng nhập”.

### Tạo đơn và nhận thông báo thanh toán

1. API xác thực người yêu cầu, kiểm tra số tiền và quyền thao tác theo nghiệp vụ.
2. API gọi đối tác thanh toán qua TLS, xử lý thông tin bí mật bằng kho bí mật hoặc KMS nếu có.
3. Khi webhook đến, hệ thống kiểm tra chữ ký/HMAC **trên đúng dữ liệu mà nhà cung cấp quy định**, xác minh nguồn khóa, thời hạn và dấu hiệu gửi lại.
4. Server dùng ID giao dịch duy nhất và kiểm tra trạng thái hiện tại để xử lý **idempotent**: nhận hai lần cùng một webhook không được ghi nhận thanh toán hai lần.
5. Log chứa thông tin đủ để tra soát nhưng không lộ số thẻ, token bí mật, khóa hoặc dữ liệu nhạy cảm không cần thiết.

Trong cả hai luồng, mật mã bảo vệ đường truyền, dữ liệu và thông điệp; **quy tắc nghiệp vụ và kiểm soát truy cập** vẫn là phần riêng không thể bỏ qua.

## 9. Quản lý khóa: nơi nhiều thiết kế “đúng thuật toán” vẫn thất bại

Một ứng dụng có AES-GCM chuẩn nhưng đặt khóa trong mã nguồn, ảnh container, log hoặc cùng cơ sở dữ liệu bị lộ có thể mất toàn bộ lợi ích bảo mật. Cần xác định vòng đời khóa trước khi triển khai:

- **Tạo khóa:** dùng nguồn ngẫu nhiên mật mã và kích thước/loại khóa tương thích thuật toán, không tự dựng từ thời gian hay chuỗi cố định.
- **Cấp phát quyền:** chỉ dịch vụ cần thiết được truy cập, tuân thủ nguyên tắc quyền tối thiểu; khóa ký riêng không cấp cho bên chỉ xác minh.
- **Lưu khóa:** chọn keystore được bảo vệ, secret manager, KMS hoặc HSM theo môi trường; phân biệt bí mật dùng khi phát triển và production.
- **Định danh và xoay vòng:** dữ liệu mã hóa dài hạn cần biết dùng phiên bản khóa nào; có kế hoạch mã hóa lại/khôi phục khi đổi khóa.
- **Rò rỉ và thu hồi:** có quy trình thay khóa, vô hiệu phiên/token, đánh giá dữ liệu bị ảnh hưởng và theo dõi sự cố.
- **Sao lưu và khôi phục:** mất khóa có thể làm mất dữ liệu vĩnh viễn; bản sao lưu khóa phải bảo vệ và kiểm thử tương ứng.
- **Vận hành:** hạn chế để khóa hoặc nội dung bí mật trong stack trace, metrics, crash dump, log và câu lệnh debug.

Không phải mọi dữ liệu đều cần mã hóa riêng từng trường: TLS, mã hóa đĩa, mã hóa cột và các biện pháp khác bảo vệ ở **những ranh giới khác nhau**. Chọn theo mô hình đe dọa và yêu cầu tuân thủ thực tế, tránh thêm mật mã mà không thể vận hành khóa an toàn.

## 10. Những nhầm lẫn dễ dẫn đến lỗ hổng

1. **“Base64 là mã hóa.”** Sai. Base64 chỉ đổi cách biểu diễn byte và có thể giải mã trực tiếp mà không cần bí mật.
2. **“SHA-256 mật khẩu là đủ.”** Sai. Hash nhanh cho phép thử đoán với tốc độ cao; cần hàm dành cho mật khẩu, salt riêng và chi phí phù hợp.
3. **“Đã dùng HTTPS nên dữ liệu ở database và log an toàn.”** Sai. TLS bảo vệ đường truyền giữa các điểm cuối; các bản sao dữ liệu khác có ranh giới riêng.
4. **“Có chữ ký số thì nội dung bí mật.”** Sai. Chữ ký hỗ trợ tính toàn vẹn/xác thực nguồn; muốn giữ bí mật phải dùng mã hóa phù hợp.
5. **“Kiểm tra HMAC một lần là webhook không thể bị phát lại.”** Sai. Thông điệp cũ vẫn có MAC hợp lệ; cần freshness, deduplication và idempotency.
6. **“Mã hóa dữ liệu là đủ để chặn đọc trái phép.”** Sai. Server có thể giải mã cho người dùng không được phép nếu thiếu kiểm tra quyền theo tài nguyên.
7. **“Mật mã sửa được SQL injection, XSS hoặc lỗi xử lý nghiệp vụ.”** Sai. Phải kiểm soát đầu vào/đầu ra, truy vấn có tham số, phân quyền và kiểm thử riêng.
8. **“Dùng AES-GCM với một IV cố định tiện hơn.”** Nguy hiểm. Tái sử dụng cặp khóa/IV phá vỡ các bảo đảm an toàn quan trọng của GCM.
9. **“Bỏ qua lỗi chứng chỉ TLS chỉ ảnh hưởng môi trường test.”** Nguy hiểm nếu cấu hình lọt sang môi trường khác; cần sửa trust chain hoặc chứng chỉ đúng cách.
10. **“Có thuật toán mạnh là có hệ thống an toàn.”** Sai. Cách cài đặt, quản lý khóa, xác thực danh tính, phân quyền, giám sát và quy trình xử lý sự cố đều quyết định kết quả.

## 11. Quy trình chọn giải pháp cho một yêu cầu bảo mật

Trước khi chọn API Java, hãy trả lời tuần tự:

1. **Tài sản gì?** Dữ liệu cá nhân, mật khẩu, thông điệp, tài liệu, token hay khóa?
2. **Đối thủ và quyền của họ?** Nghe trộm mạng, sửa message, đọc database backup, chiếm một tài khoản hay chiếm server?
3. **Mục tiêu nào?** Bí mật, toàn vẹn, xác thực nguồn, phân quyền, khả dụng hay bằng chứng truy vết?
4. **Ranh giới nào?** Trên đường truyền, trong bộ nhớ, nơi lưu trữ, giữa các dịch vụ hoặc với bên thứ ba?
5. **Ai giữ khóa và ai tin ai?** Khóa chung, cặp khóa, nguồn public key, phiên bản/rotation và đường xử lý khi lộ khóa?
6. **Giao thức đã có chưa?** Ưu tiên TLS và các cơ chế tiêu chuẩn đã được duy trì, kiểm thử; không tự sáng tác thuật toán hoặc tự dựng giao thức.
7. **Kiểm chứng thế nào?** Có test cho dữ liệu bị sửa, khóa sai, bản tin hết hạn, replay, từ chối quyền, lỗi TLS, khôi phục và rotation không?

Một yêu cầu cụ thể nên dẫn đến **mục tiêu kiểm chứng cụ thể**. Ví dụ: “người đọc được bản sao lưu database không xem được trường X” có thể dẫn đến mã hóa riêng trường X và tách quyền truy cập khóa khỏi database. Ngược lại, “người dùng A không xem đơn của B” đòi hỏi kiểm tra phân quyền trên server dù dữ liệu có được mã hóa hay không.

## 12. Tự kiểm tra kiến thức

**Câu 1.** Một client gửi số tiền và kèm SHA-256 của số tiền. Server nhận cả hai qua một kênh bị tấn công. Server có xác minh được người gửi không?  
**Trả lời:** Không. Đối thủ có thể thay số tiền và tính lại SHA-256. Cần cơ chế xác thực nguồn/thông điệp phù hợp như HMAC/chữ ký cùng kênh hoặc giao thức xác thực tin cậy.

**Câu 2.** HTTPS có ngăn user đã đăng nhập xem dữ liệu của user khác không?  
**Trả lời:** Không. HTTPS bảo vệ kết nối; server phải kiểm tra quyền trên từng bản ghi/hành động.

**Câu 3.** Nếu cần lưu mật khẩu để xác minh sau này, chọn AES hay SHA-256 một lần?  
**Trả lời:** Không chọn cả hai làm cơ chế chính. Dùng hàm băm mật khẩu chuyên dụng với salt và tham số chi phí phù hợp (ví dụ Argon2id hoặc PBKDF2 theo yêu cầu triển khai).

**Câu 4.** Chữ ký webhook hợp lệ có đủ để cộng tiền vào tài khoản ngay không?  
**Trả lời:** Chưa đủ. Phải xác minh khóa đúng đối tác, payload đúng, độ mới và trạng thái giao dịch; xử lý idempotent để tránh thao tác lặp.

**Câu 5.** Tại sao ví dụ AES-GCM vừa giải mã được bản gốc vừa từ chối bản mã đã bị sửa?  
**Trả lời:** AES-GCM là chế độ mã hóa có xác thực (AEAD). Tag ràng buộc nội dung và AAD với khóa/IV; việc xác minh tag thất bại khiến API từ chối dữ liệu không còn khớp.

**Điểm cần nhớ:** An toàn thông tin xác định những điều cần bảo vệ. Mật mã cung cấp những công cụ như mã hóa, MAC, hash, chữ ký, password hashing và TLS để bảo vệ những mục tiêu cụ thể. Một ứng dụng Java an toàn chỉ hình thành khi chọn đúng công cụ, quản lý khóa đúng và kết hợp với xác thực, phân quyền, kiểm tra đầu vào, vận hành và khả năng khôi phục.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-fundamentals-002">1.2 Mô hình đe dọa, tài sản cần bảo vệ và ranh giới tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

# 1.2 Mô hình đe dọa, tài sản cần bảo vệ và ranh giới tin cậy

Mô hình đe dọa (*threat modeling*) là cách phân tích một hệ thống từ góc nhìn an toàn: **đang bảo vệ điều gì, ai hoặc điều gì có thể gây hại, họ tiếp cận qua đâu, và biện pháp nào làm rủi ro còn ở mức chấp nhận được?** Đây là hoạt động thiết kế và đánh giá lặp lại theo thay đổi của hệ thống, không phải bảng liệt kê lỗ hổng hay một lần chạy công cụ quét bảo mật.

Trong module **Java Advance - security-cryptography**, mô hình đe dọa giúp quyết định chỗ nào cần xác thực, phân quyền, bảo vệ tính toàn vẹn, mã hóa, quản lý khóa, kiểm toán và giới hạn tài nguyên. Chọn thuật toán mã hóa trước khi xác định tài sản và đường tấn công dễ dẫn đến giải pháp an toàn trên một đường truyền nhưng vẫn làm lộ dữ liệu ở lớp ứng dụng.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt **tài sản**, **tác nhân đe dọa**, **đe dọa**, **lỗ hổng**, **kịch bản tấn công**, **kiểm soát** và **rủi ro còn lại**.
2. Liệt kê tài sản theo vòng đời dữ liệu, gồm dữ liệu gốc, khóa bí mật, token, quyền truy cập, log và bản sao lưu; xác định thuộc tính bảo mật cần giữ cho từng tài sản.
3. Vẽ được luồng dữ liệu và **ranh giới tin cậy** giữa client, dịch vụ Java, cơ sở dữ liệu, dịch vụ quản lý khóa và bên thứ ba.
4. Dùng STRIDE cùng kịch bản lạm dụng nghiệp vụ để tìm đường tấn công cụ thể, xếp ưu tiên, chọn kiểm soát và lập phép kiểm chứng.
5. Nhận ra vì sao **TLS, mã hóa dữ liệu và xác thực người dùng không thay thế cho kiểm tra quyền trên từng tài nguyên**.

## 2. Khái niệm cốt lõi: đe dọa khác gì lỗ hổng?

| Khái niệm | Ý nghĩa | Ví dụ trong API tải tài liệu |
| --- | --- | --- |
| Tài sản (*asset*) | Điều có giá trị cần được bảo vệ | Nội dung hợp đồng, quyền sở hữu tệp, khóa giải mã |
| Tác nhân đe dọa (*threat actor*) | Người hoặc thực thể có khả năng gây hại, có chủ ý hay vô ý | Người dùng hợp lệ muốn đọc tài liệu của tổ chức khác; tài khoản dịch vụ bị chiếm |
| Bề mặt tấn công (*attack surface*) | Các điểm có thể tương tác với hệ thống | Endpoint HTTPS, webhook, job queue, API KMS, pipeline CI/CD |
| Đe dọa (*threat*) | Sự kiện/hành vi có thể vi phạm thuộc tính bảo mật | Người dùng thay `documentId` để đọc tài liệu không thuộc quyền mình |
| Lỗ hổng/điểm yếu (*vulnerability*) | Thiếu sót khiến đe dọa có đường thực hiện | API chỉ kiểm tra đã đăng nhập, bỏ qua quyền trên `documentId` |
| Kiểm soát (*control*) | Biện pháp phòng ngừa, phát hiện hoặc giảm tác hại | Phân quyền theo tài liệu và tenant, xác thực token, audit, quota |
| Rủi ro (*risk*) | Kết hợp khả năng xảy ra và tác động đối với mục tiêu bảo vệ | Lộ tài liệu khách hàng gây thiệt hại tài chính và pháp lý |
| Rủi ro còn lại (*residual risk*) | Rủi ro sau khi áp dụng và kiểm chứng kiểm soát | Tài khoản có quyền hợp lệ bị chiếm vẫn có thể tải tài liệu được cấp phép |

**Phân biệt quan trọng:** một ý tưởng tấn công chưa chứng minh rằng hệ thống hiện có lỗ hổng; ngược lại, không thấy lỗi qua một bài kiểm thử không chứng minh hệ thống không có đe dọa. Mô hình đe dọa ghi rõ *giả định*, *kịch bản* và *bằng chứng kiểm tra*, rồi cập nhật khi có thông tin mới.

Cách đặt câu hỏi hữu ích cho một kịch bản:

> “Ai có thể đưa dữ liệu hoặc lệnh vào qua ranh giới nào, với quyền gì, nhằm thay đổi tài sản nào, gây hại ra sao? Kiểm soát ở đâu, và làm thế nào kiểm tra kiểm soát đó?”

## 3. Xác định tài sản cần bảo vệ

Không chỉ dữ liệu kinh doanh mới là tài sản. Hãy liệt kê cả những thứ **cho phép truy cập hoặc giải mã dữ liệu**. Với mỗi tài sản, ghi chủ sở hữu, nơi tồn tại, người được quyền sử dụng, thời gian lưu giữ, luồng di chuyển và tác động nếu bị lộ/sửa/xóa/không truy cập được.

| Tài sản | Thuộc tính cần bảo vệ | Ví dụ đường phơi lộ hoặc bị thay đổi |
| --- | --- | --- |
| Bản rõ (*plaintext*) của tài liệu, dữ liệu cá nhân | Bí mật, toàn vẹn, quyền riêng tư | Response API, bộ nhớ tiến trình, bản tạm, log, bản sao lưu |
| Bản mã (*ciphertext*) và metadata | Toàn vẹn, tính sẵn sàng, liên kết đúng với chủ sở hữu | Object storage, URL đối tượng, metadata tenant, nonce/IV |
| Khóa đối xứng, khóa riêng, secret KMS, mật khẩu dịch vụ | Bí mật và quyền sử dụng; khả năng thu hồi/luân chuyển | Biến môi trường, file cấu hình, mã nguồn, CI log, IAM role |
| Token phiên, refresh token, API key | Bí mật, hạn sử dụng, phạm vi quyền | Header, cookie, cache, báo cáo lỗi, công cụ quan sát |
| Danh tính, role, policy, ACL, mối quan hệ tenant–object | Toàn vẹn và tính xác thực | Trường `ownerId` do client gửi, dữ liệu phân quyền trong DB |
| Audit log, thời điểm, request ID | Toàn vẹn, truy vết, giới hạn tiết lộ | Log bị sửa/xóa, log chứa token hoặc plaintext |
| Dịch vụ, quota, dữ liệu sao lưu, cấu hình hạ tầng | Sẵn sàng, khả năng khôi phục | DDoS, thao tác xóa, cấu hình sai, lỗi khóa/giải mã |

Các thuộc tính thường được xem xét:

- **Confidentiality (bí mật):** chủ thể không được phép không thể đọc nội dung.
- **Integrity (toàn vẹn):** sửa đổi trái phép được ngăn hoặc phát hiện.
- **Availability (sẵn sàng):** người được phép có thể dùng dịch vụ, kể cả trong điều kiện lỗi hợp lý.
- **Authenticity (tính xác thực):** danh tính hoặc nguồn gốc thông điệp được xác minh đúng.
- **Authorization (quyền):** danh tính hợp lệ chỉ thao tác đúng tài nguyên và hành động được cấp.
- **Accountability (truy vết):** hoạt động quan trọng được ghi nhận đủ để điều tra; log đơn thuần không mặc nhiên chứng minh chống chối bỏ tuyệt đối.
- **Privacy (quyền riêng tư):** giới hạn thu thập, suy luận, liên kết, lưu trữ và chia sẻ dữ liệu về cá nhân.

### Phân loại tài sản theo vòng đời

Một bản rõ có thể đi qua `client -> HTTPS -> JVM heap -> DB -> backup -> log/monitoring`. Mã hóa bản ghi trong DB chưa đủ nếu endpoint cho người sai quyền lấy bản rõ, khóa nằm cùng nơi với dữ liệu dưới cùng quyền truy cập, hoặc debug log in ra request/response nhạy cảm. Đừng bỏ qua dữ liệu trong bộ nhớ, tệp tạm, snapshot, bản sao môi trường test và thông điệp lỗi.

Với khóa bí mật, hãy ghi riêng: **ai có thể yêu cầu mã hóa/giải mã**, ai có thể lấy/export khóa, chính sách IAM, vị trí tạo/lưu khóa, cơ chế luân chuyển/thu hồi, các bản khóa cũ cần để đọc dữ liệu cũ, cùng kế hoạch khi khóa bị lộ hoặc bị mất. Quyền gọi `Decrypt` trên KMS là một tài sản quyền lực dù byte khóa không bao giờ rời KMS.

## 4. Ranh giới tin cậy là gì?

**Ranh giới tin cậy (*trust boundary*)** là nơi dữ liệu hoặc lệnh đi giữa các vùng có chủ sở hữu, cơ chế xác thực, mức đặc quyền hay giả định tin cậy khác nhau. Ranh giới thường trùng với kết nối mạng nhưng **không nhất thiết**: hai thành phần cùng máy, cùng VPC hoặc cùng JVM vẫn có thể có quyền và nguồn dữ liệu khác nhau.

Ví dụ hệ thống Java cho phép người dùng tải tài liệu đã mã hóa:

```text
[Trình duyệt / người dùng]    (không tin đầu vào)
            |
            | B1: HTTPS + phiên/token
            v
[Gateway / API Java]          (xác thực danh tính, áp dụng quyền)
       |                 \
       | B2: SQL/TLS      \ B3: KMS API/TLS, IAM role
       v                   v
[DB: metadata/ACL]      [KMS: quản lý và sử dụng khóa]
       |
       | tham chiếu đối tượng
       v
[Object storage: bản mã]      (quyền và chính sách truy cập riêng)
```

Lưu ý: hình trên là **sơ đồ logic đơn giản**, không ngụ ý DB trực tiếp truy cập object storage; triển khai thực tế thường có luồng API Java -> object storage riêng, cũng cần vẽ thành một ranh giới. Vị trí kết thúc TLS tại gateway/load balancer phải được ghi rõ: nếu gateway giải mã TLS, chặng từ gateway đến backend là một luồng khác cần đánh giá.

Tại **mỗi lần vượt ranh giới**, đặt sáu câu hỏi:

1. Ai gửi và ai nhận? Có xác minh đúng danh tính dịch vụ/người dùng không?
2. Những byte nào là dữ liệu **không đáng tin** (ID, header, JWT, nội dung tệp, sự kiện, thông tin tenant)?
3. Bên nhận có thực hiện thêm quyền cao hơn, truy vấn DB, gọi KMS hoặc ghi tệp dựa trên dữ liệu đó không?
4. Kiểm tra đầu vào, **ủy quyền**, giới hạn kích thước/tốc độ, chống phát lại và xác minh toàn vẹn đặt ở đâu?
5. Lộ, sửa, mất hoặc gửi lại dữ liệu qua ranh giới sẽ ảnh hưởng tài sản nào?
6. Khi kiểm soát thất bại, có dấu hiệu kiểm toán và cách ngăn lan truyền thiệt hại không?

Một số ranh giới hay bị bỏ sót:

- **Client -> backend:** client kiểm soát ID, body, query, file upload; kể cả người dùng đã đăng nhập, toàn bộ trường do họ gửi vẫn không đáng tin.
- **Gateway -> service:** gateway xác thực không mặc nhiên đồng nghĩa backend đã phân quyền; backend cần hợp đồng xác thực có thể kiểm chứng, hạn chế tin header danh tính từ Internet.
- **Service -> DB/cache/object storage:** tài khoản dịch vụ có thể rộng quyền hơn người dùng cuối; cần tenant isolation, truy vấn ràng buộc quyền, least privilege và bảo vệ kết nối.
- **Service -> KMS/HSM:** việc có quyền gọi giải mã có thể biến server bị chiếm thành “cỗ máy giải mã”; giới hạn quyền theo key, điều kiện, mục đích và theo dõi lạm dụng.
- **Ứng dụng -> webhook/partner/queue:** dữ liệu hoặc sự kiện của bên ngoài phải kiểm tra nguồn, chữ ký/MAC khi được thiết kế, thời gian, nonce/idempotency và schema; xác thực kết nối chỉ giải quyết một phần.
- **Build/CI -> production secrets:** artifact, dependency và người có quyền triển khai có thể thay đổi chương trình chạy với đặc quyền sản xuất.

**Nguyên tắc:** “nội bộ”, “chạy trong Kubernetes”, “có HTTPS” hoặc “đã đăng nhập” không phải bảo đảm cho mọi hành động. Tin cậy phải có *phạm vi*, *điều kiện xác minh* và *thời hạn* cụ thể.

## 5. Quy trình xây dựng mô hình đe dọa

### Bước 1 — Chốt phạm vi và các giả định

Ghi tên chức năng, luồng nghiệp vụ, các actor (người dùng, admin, service, bên thứ ba), môi trường, điểm vào/ra, loại dữ liệu và điều gì **nằm ngoài** phạm vi. Ví dụ: “Xét `GET /api/documents/{id}` và thao tác giải mã phục vụ tải về; không phân tích quy trình thanh toán”. Ghi giả định kiểm chứng được, như “gateway xác minh JWT và backend chỉ nhận kết nối từ gateway”; không xem giả định là sự thật khi chưa kiểm thử.

### Bước 2 — Kiểm kê tài sản và phân quyền

Dùng bảng ở mục 3 để mô tả tác động nếu vi phạm từng thuộc tính. Tách **chủ sở hữu dữ liệu**, **người được phép xem**, **tài khoản máy thực thi** và **người quản trị khóa**; các danh tính này không đồng nhất.

### Bước 3 — Vẽ luồng dữ liệu (DFD) và đánh dấu ranh giới

DFD tối thiểu cần có **external entities**, **processes**, **data stores**, **data flows** và **trust boundaries**. Trên các mũi tên, ghi dữ liệu chuyển đi, giao thức, cơ chế xác thực, điểm kết thúc mã hóa và nơi ra quyết định quyền. Vẽ riêng đường đi ghi, đọc, xóa, sao lưu và luân chuyển khóa nếu mức rủi ro khác nhau.

### Bước 4 — Tìm các kịch bản đe dọa

Duyệt từng tài sản và mỗi điểm vượt ranh giới. Kết hợp STRIDE (mục 6) với các câu hỏi: “Nếu client thay ID?”, “Nếu token bị lấy cắp?”, “Nếu service role bị chiếm?”, “Nếu dữ liệu/ciphertext bị tráo?”, “Nếu retry cùng sự kiện nhiều lần?”, “Nếu KMS không phản hồi?”. Nêu **tiền điều kiện -> hành động -> kiểm soát bị vượt qua -> hậu quả**, tránh chỉ ghi “có nguy cơ hacker”.

### Bước 5 — Ưu tiên và gắn kiểm soát

Ước lượng **khả năng xảy ra** và **mức tác động** theo bối cảnh thực tế; cân nhắc mức phơi lộ, quyền cần có, độ khó khai thác và kiểm soát đang tồn tại. Ví dụ thang nội bộ 1–5: khả năng 4 và tác động 5 cho điểm 20; đây chỉ là phép sắp xếp thảo luận, **không phải xác suất định lượng hay chuẩn ngành**. Nêu chủ sở hữu rủi ro, người thực thi, thời hạn và trạng thái (giảm thiểu/chuyển giao/tránh/chấp nhận có lý do).

### Bước 6 — Kiểm chứng, lưu vết và cập nhật

Với mỗi kiểm soát, viết kiểm thử hoặc tiêu chí quan sát: request khác tenant bị từ chối, token giả bị loại, IAM không cho key trái phép, log không chứa bearer token, hạn mức không làm cạn tài nguyên. Lưu artifact mô hình, quyết định và rủi ro còn lại cùng version thiết kế. Cập nhật khi thêm endpoint, nhà cung cấp, cơ chế xác thực, vùng lưu trữ hoặc thay đổi luồng khóa.

## 6. STRIDE: gợi ý để không bỏ sót lớp đe dọa

STRIDE có sáu nhóm. Áp chúng lên thành phần và đường dữ liệu **cụ thể**; một thành phần có thể gặp nhiều nhóm, và một threat có thể liên quan nhiều nhóm.

| Nhóm | Câu hỏi kiểm tra | Kịch bản ở API tài liệu | Kiểm soát điển hình |
| --- | --- | --- | --- |
| **S — Spoofing** (mạo danh) | Ai có thể giả làm người/dịch vụ khác? | Kẻ xấu gửi token giả hoặc giả header `X-User` | Xác minh chữ ký token, issuer, audience, thời hạn; bảo vệ mTLS/identity giữa service; bỏ header không đáng tin |
| **T — Tampering** (sửa đổi) | Ai thay dữ liệu/lệnh ngoài ý muốn? | Bản mã/metadata bị sửa, tenant ID bị tráo | Kiểm tra ủy quyền và tính toàn vẹn; AEAD khi mã hóa; kiểm soát ghi DB/object |
| **R — Repudiation** (phủ nhận hành động) | Có truy vết ai đã làm gì không? | Người có quyền tải/xóa tài liệu phủ nhận hành động | Audit theo actor đã xác minh, resource ID, thời điểm; chống sửa log; hạn chế dữ liệu nhạy cảm trong log |
| **I — Information Disclosure** (tiết lộ) | Ai đọc được thông tin không được phép? | BOLA khiến tải tài liệu của tenant khác; token lọt vào log | Phân quyền cấp đối tượng, tenant isolation, TLS, mã hóa phù hợp, redaction log |
| **D — Denial of Service** (từ chối dịch vụ) | Ai có thể làm hệ thống cạn tài nguyên? | Spam request giải mã làm hết quota KMS/CPU | Rate limit, quota, timeout, backpressure, kiểm soát tài nguyên, circuit breaker theo nhu cầu |
| **E — Elevation of Privilege** (leo thang quyền) | Người dùng có thể hành động ngoài vai trò? | Dùng input `role=admin` hoặc service account quá rộng quyền | Quyền từ nguồn đáng tin, kiểm tra server-side cho từng action, IAM tối thiểu, phân tách nhiệm vụ |

STRIDE là **khung gợi ý**, không đảm bảo phát hiện hết tấn công nghiệp vụ, riêng tư hay chuỗi cung ứng. Với hành vi lạm dụng quy trình, thêm *abuse case*; với đường tấn công nhiều bước, dùng *attack tree*; với yêu cầu về quyền riêng tư, có thể bổ sung LINDDUN. Đừng biến việc điền đủ sáu hàng thành mục tiêu thay cho tìm ra rủi ro có thật.

## 7. Ví dụ xuyên suốt: tải tài liệu đã mã hóa theo tenant

**Bài toán:** Dịch vụ Java lưu tệp dưới dạng bản mã trong object storage; DB lưu metadata gồm `documentId`, `tenantId`, `ownerId` và tham chiếu đối tượng; KMS bảo vệ khóa. Người dùng gửi `GET /api/documents/{id}` để tải tệp. Người dùng hợp lệ **chỉ được xem tài liệu được cấp quyền trong tenant của mình**.

### 7.1 Luồng hợp lệ

1. Client gọi API qua HTTPS với thông tin phiên/token.
2. Thành phần xác thực kiểm tra bằng chứng danh tính; Java API nhận **principal đã xác minh**. Nếu JWT được dùng, phải kiểm tra chữ ký, thuật toán được cho phép, issuer, audience, thời hạn và các claim cần thiết theo giao thức; giải mã chuỗi JWT ra JSON không có nghĩa là đã xác thực.
3. Server nhận `id` từ URL **như dữ liệu do client kiểm soát**, lấy metadata tài liệu từ kho đáng tin.
4. Policy kiểm tra cả **tenant**, **quyền trên tài liệu** và **hành động đọc**. Không dùng `ownerId` hoặc `tenantId` do client tự khai làm quyết định quyền.
5. Chỉ khi policy cho phép, service mới yêu cầu lấy bản mã/giải mã qua KMS theo đặc quyền tối thiểu rồi trả nội dung. Response và log tuân theo chính sách bảo vệ dữ liệu.

### 7.2 Kịch bản lạm dụng đáng ưu tiên

| ID | Ranh giới và kịch bản | Tài sản / tác hại | Kiểm soát cần có | Cách xác minh |
| --- | --- | --- | --- | --- |
| T1 | B1: người đã đăng nhập đổi `id=doc-a` thành `id=doc-b` của người/tenant khác (**BOLA**) | Bí mật của tài liệu bị lộ | Policy theo từng đối tượng, tenant và hành động ở backend; mặc định từ chối | Kiểm thử cùng tenant khác owner, khác tenant, tài liệu không tồn tại; không trả bản rõ |
| T2 | B1: dùng token giả, hết hạn hoặc sai audience | Mạo danh, truy cập trái phép | Xác minh token đúng chuẩn, không tin header tùy ý từ client | Gửi token thiếu/sai chữ ký, sai issuer/audience, hết hạn và xác nhận từ chối |
| T3 | B2/B3: tài khoản dịch vụ hoặc IAM role bị chiếm để gọi giải mã rộng khắp | Lộ hàng loạt bản rõ dù storage đã mã hóa | IAM/KMS policy tối thiểu, tách quyền, giới hạn phạm vi key, audit và cảnh báo | Thử một role không được cấp phép gọi decrypt; kiểm tra audit KMS |
| T4 | API -> storage: sửa bản mã hoặc tráo metadata tenant/đối tượng | Trả dữ liệu sai, mất toàn vẹn | Kiểm soát ghi, ràng buộc bản mã với ngữ cảnh, dùng mã hóa xác thực (AEAD) khi thiết kế yêu cầu | Thử sửa bit bản mã/AAD hoặc thay liên kết đối tượng; giải mã phải thất bại hoặc bị chặn |
| T5 | B1/B3: spam tải/giải mã, file cực lớn | Gián đoạn API hoặc hết quota KMS | Rate limit, giới hạn kích thước, timeout, quota, backpressure | Load test có giới hạn, đo lỗi và ảnh hưởng lên người dùng hợp lệ |
| T6 | API -> log/observability: log token, plaintext, URL chứa secret | Rò rỉ dữ liệu qua hệ thống phụ trợ | Redaction, không ghi credential/bản rõ, phân quyền và retention log | Quét log ở tình huống thành công/lỗi; không thấy bearer token hay plaintext |

**Phân tích T1 theo chuỗi nguyên nhân:**

- **Tiền điều kiện:** Alice có tài khoản hợp lệ và biết/đoán được ID của tài liệu Bob. ID là tham số đầu vào, dù là UUID ngẫu nhiên vẫn có thể bị lộ qua log, link hoặc API khác.
- **Điểm vượt ranh giới:** client gửi `id`; API dùng ID đó để đọc metadata và giải mã bằng quyền của server.
- **Lỗi thiết kế có thể có:** API kiểm tra “đã login” rồi gọi `decrypt(documentId)`, không kiểm tra Alice có quyền trên tài liệu này.
- **Hậu quả:** tài liệu của Bob bị tiết lộ. **TLS không ngăn được** vì request được gửi qua kênh hợp lệ; **mã hóa storage không ngăn được** vì chính server có quyền giải mã.
- **Sửa đúng chỗ:** backend ra quyết định quyền theo principal đã xác minh và metadata/policy tin cậy **trước mọi thao tác lấy bản rõ**; deny by default; kiểm thử chéo người dùng và tenant.

Đây là nhóm **Broken Object Level Authorization (API1:2023)** trong OWASP API Security Top 10. Đổi ID tuần tự sang UUID hoặc chỉ che nút tải trên UI không giải quyết được gốc rễ.

### 7.3 Biên giới mật mã và quản lý khóa

Mật mã chỉ có ý nghĩa khi trả lời chính xác **bảo vệ tài sản nào trước tác nhân nào**:

- **TLS** bảo vệ kênh truyền đến điểm kết thúc TLS và xác thực endpoint theo cấu hình; TLS không xác định người dùng có quyền đọc tài liệu hay không. Khi TLS kết thúc tại proxy, vẽ lại đường đi proxy -> service.
- **Mã hóa dữ liệu lưu trữ** hỗ trợ bảo mật bản rõ nếu storage/bản sao lưu bị đọc trái phép. Nếu tiến trình có quyền giải mã bị chiếm, mối đe dọa chuyển sang quyền sử dụng khóa và đường xuất bản rõ.
- **Mã hóa xác thực** như AES-GCM có thể phát hiện bản mã/AAD bị sửa khi dùng đúng; với cùng một khóa, không tái sử dụng nonce/IV và cần lưu/đưa đúng AAD khi giải mã. AEAD không tự làm kiểm tra ủy quyền.
- **Chữ ký số/MAC** hỗ trợ xác minh nguồn và tính toàn vẹn theo giả định khóa/chứng chỉ được quản lý đúng; chúng không tự mã hóa nội dung để giữ bí mật.
- **Hash mật khẩu** khác mã hóa đảo ngược: dùng cơ chế password hashing có salt và cost phù hợp (ví dụ Argon2id theo chính sách) thay vì lưu mật khẩu bằng AES hay SHA-256 đơn thuần.
- **Khóa và secret** cần lưu giữ, phân quyền, rotation và audit riêng. Tránh nhúng khóa trong mã nguồn, image build, stack trace hoặc log; hạn chế quyền decrypt hơn cả quyền đọc bản mã.

Một kiểm soát có thể giảm một loại đe dọa nhưng để nguyên loại khác. Mô hình đe dọa tốt ghi rõ **điều kiện kiểm soát đúng**, vị trí kiểm chứng và rủi ro còn lại.

## 8. Ví dụ Java 17: kiểm tra quyền trước khi chạm bản rõ

Đoạn sau chỉ minh họa **quy tắc phân quyền đơn giản theo chủ sở hữu và tenant**. Nó không kết nối mạng/DB/KMS, không xác thực token và không mã hóa thật. Trong ứng dụng thực tế, `Principal` phải được tạo từ **ngữ cảnh xác thực đã kiểm chứng**, còn `Document` phải đến từ nguồn metadata tin cậy.

Lưu thành `TrustBoundaryDemo.java`:

```java
import java.util.Objects;

public class TrustBoundaryDemo {
    record Principal(String userId, String tenantId) {}
    record Document(String id, String ownerId, String tenantId) {}

    static boolean canRead(Principal actor, Document document) {
        if (actor == null || document == null
                || actor.userId() == null || actor.userId().isBlank()
                || actor.tenantId() == null || actor.tenantId().isBlank()) {
            return false;
        }

        return Objects.equals(actor.tenantId(), document.tenantId())
                && Objects.equals(actor.userId(), document.ownerId());
    }

    public static void main(String[] args) {
        Principal alice = new Principal("alice", "tenant-a");
        System.out.println(canRead(alice,
                new Document("doc-1", "alice", "tenant-a")));
        System.out.println(canRead(alice,
                new Document("doc-2", "bob", "tenant-a")));
        System.out.println(canRead(alice,
                new Document("doc-3", "alice", "tenant-b")));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac TrustBoundaryDemo.java
java TrustBoundaryDemo
```

Kết quả stdout chính xác:

```text
true
false
false
```

Hàng đầu cho phép Alice đọc tài liệu của mình. Hàng thứ hai từ chối tài liệu của Bob trong cùng tenant; hàng thứ ba từ chối tài liệu có cùng owner ID nhưng thuộc tenant khác. **Chỉ so sánh user ID mà bỏ qua tenant là thiếu**, cũng như chỉ so sánh tenant mà không kiểm tra quyền trên từng tài liệu.

Trong hệ thống thật, thay `canRead` bằng policy phù hợp (owner, chia sẻ có kiểm soát, vai trò, điều kiện thời gian, quyền hành động). Hàm nên trả **deny** khi metadata thiếu hoặc danh tính chưa xác minh. Thứ tự quan trọng là: **xác thực -> nạp thông tin đáng tin -> phân quyền tài nguyên -> truy xuất/giải mã -> trả dữ liệu**. Cách trả 403/404 và thông tin log phải theo chính sách tránh lộ sự tồn tại tài nguyên, không biến thông báo lỗi thành kênh rò rỉ.

### Chuyển mô hình thành phép kiểm thử

Đối với API thật, ít nhất phải có các ca:

1. User được cấp quyền, đúng tenant, đúng tài liệu: được tải và nội dung chính xác.
2. User khác cùng tenant nhưng chưa được cấp quyền: bị từ chối; service không gọi KMS/decrypt.
3. User có cùng tên/ID trong tenant khác: bị từ chối; truy vấn metadata luôn ràng buộc tenant theo chính sách.
4. Token sai chữ ký, sai audience, hết hạn, hoặc không có token: thất bại trước khi truy xuất nội dung.
5. Request chứa `ownerId`, `tenantId` hoặc `role` giả trong body/header: không thay đổi quyết định quyền.
6. Role dịch vụ chỉ được phép dùng một khóa: yêu cầu giải mã bằng khóa ngoài phạm vi bị từ chối, có audit.
7. Bản mã/AAD bị can thiệp, log lỗi, retry hoặc lượng request vượt quota: xử lý an toàn, không trả bản rõ sai hay rò secret.

Cần có cả kiểm thử unit policy, integration cho boundary API/DB/KMS (môi trường kiểm thử an toàn), và kiểm tra cấu hình IAM thực tế. Test chạy qua không thay thế rà soát giả định và thay đổi kiến trúc.

## 9. Ghi nhận quyết định, mức ưu tiên và rủi ro còn lại

Một bản ghi threat tối thiểu nên có:

| Trường | Nội dung mẫu |
| --- | --- |
| `ID` / phạm vi | `T1` — `GET /api/documents/{id}` |
| Tài sản, mục tiêu | Bản rõ hợp đồng; chỉ owner/được chia sẻ trong đúng tenant mới đọc |
| Tác nhân và tiền điều kiện | User hợp lệ có thể gửi ID của tài liệu khác |
| Ranh giới/đường tấn công | Client -> Java API -> metadata -> KMS/decrypt |
| Hậu quả | Lộ nội dung tài liệu trái phép |
| Đánh giá trước kiểm soát | Khả năng 4/5 x tác động 5/5 = 20/25 (ví dụ nội bộ) |
| Kiểm soát và vị trí | Policy kiểm tra tenant, action và quyền object ở backend, trước decrypt |
| Bằng chứng | Test chéo tenant/user, audit request bị từ chối, không gọi KMS |
| Owner, trạng thái | Nhóm sở hữu API; đang triển khai/đã kiểm chứng; ngày đánh giá |
| Rủi ro còn lại | Token hợp lệ bị đánh cắp vẫn có thể truy cập tài liệu trong quyền được cấp; cân nhắc session control, MFA và phát hiện bất thường |

Không cộng trừ điểm một cách máy móc: các thang số không mô tả đầy đủ mức phơi lộ, tác động pháp lý, mục tiêu của attacker hoặc tương quan giữa nhiều đe dọa. Nếu rủi ro được chấp nhận, cần người chịu trách nhiệm, lý do, thời hạn xem xét lại và tiêu chí mở lại đánh giá.

**Khi nào cập nhật mô hình?** Khi thêm luồng dữ liệu (webhook, export, analytics), dữ liệu nhạy cảm, tenant mới, thay đổi quyền IAM, phương thức xác thực, thư viện mật mã, vị trí TLS termination, logic chia sẻ đối tượng hoặc công đoạn backup/restore. Mô hình là tài liệu sống gắn với thiết kế, code review và kiểm thử.

## 10. Các lỗi tư duy thường gặp

- **“Đã dùng HTTPS nên an toàn.”** HTTPS bảo vệ kênh đến điểm TLS termination; request của người có quyền thấp vẫn có thể làm lộ dữ liệu nếu thiếu authorization.
- **“Đã mã hóa DB, kể cả admin/service bị chiếm cũng không đọc được.”** Phải xem ai có quyền decrypt và bản rõ xuất hiện ở đâu. Mã hóa không thay thế kiểm soát truy cập và phân tách quyền.
- **“Đã xác thực JWT thì mọi object ID do người dùng gửi đều đáng tin.”** Token xác thực người gọi; mỗi object/action cần kiểm tra quyền độc lập.
- **“UUID đủ ngẫu nhiên nên không cần policy.”** Khó đoán ID không phải ranh giới bảo vệ: ID có thể lộ hoặc được chia sẻ ngoài ý muốn.
- **“Trong cùng private network thì không có ranh giới.”** Các service/role có đặc quyền và đường dữ liệu khác nhau; tài khoản bị chiếm vẫn gây hại.
- **“Có đủ sáu ô STRIDE là xong.”** STRIDE chỉ là checklist tìm ý tưởng; cần kịch bản cụ thể, tác động, owner, kiểm soát và kiểm chứng.
- **“Threat = bug đã xảy ra.”** Threat là điều có thể sai; cần tách giả định, điểm yếu đã xác minh và rủi ro chưa được xử lý.
- **“Log càng nhiều càng tốt.”** Log có thể biến thành tài sản nhạy cảm, đặc biệt nếu chứa bản rõ, token, khóa hoặc dữ liệu cá nhân.
- **“Mã hóa xác thực tự đảm bảo dữ liệu thuộc đúng user.”** AEAD bảo vệ toàn vẹn trong ngữ cảnh khóa/AAD được chọn; việc user có quyền với object vẫn do authorization quyết định.

## 11. Bài luyện tập áp dụng

Một dịch vụ Spring/Java nhận webhook từ đối tác, ghi sự kiện vào queue, worker cập nhật trạng thái thanh toán trong DB, và API cho người dùng xem lịch sử. Hãy tự tạo một trang threat model:

1. Liệt kê ít nhất sáu tài sản: trạng thái/tham chiếu thanh toán, danh tính người dùng, khóa bí mật xác thực webhook, message ID, quyền cập nhật DB, audit log.
2. Vẽ ranh giới **đối tác -> webhook**, **API -> queue**, **worker -> DB**, **người dùng -> API đọc**, và đánh dấu ai xác thực ai.
3. Viết ít nhất năm kịch bản: webhook giả, phát lại webhook hợp lệ, sửa tenant/order ID trong payload, tiêu thụ queue lặp gây cập nhật sai, người dùng đọc giao dịch của người khác, hoặc queue bị quá tải.
4. Với mỗi kịch bản, ghi tiền điều kiện, tài sản bị ảnh hưởng, kiểm soát dự kiến và một test chứng minh. Ví dụ chống replay có thể cần xác thực nguồn, thời gian có giới hạn **và** idempotency theo event ID ở phía tiêu thụ; chỉ có HTTPS chưa đủ.
5. Ghi một giả định còn phải xác minh (ví dụ “đối tác có cơ chế ký webhook”) và một rủi ro còn lại sau kiểm soát.

**Tiêu chí hoàn thành:** người khác đọc artifact có thể tìm đúng điểm vượt ranh giới, thấy rõ đường từ dữ liệu không tin cậy tới quyền cao, biết tại đâu kiểm tra nó và cách xác nhận kết quả — không cần suy diễn “service này chắc đã an toàn”.

## Tài liệu tham khảo

- [OWASP — Threat Modeling Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Threat_Modeling_Cheat_Sheet.html): quy trình xây dựng mô hình, DFD, STRIDE, kiểm soát và review.
- [OWASP — API1:2023 Broken Object Level Authorization](https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/): ví dụ lỗi phân quyền từng đối tượng.
- [OWASP — Threat Modeling trong DevSecOps Guideline](https://owasp.github.io/DevSecOpsGuideline/2-Process/2-1-Design/2-1-1-Threat-modeling/): ví dụ DFD, ranh giới tin cậy và STRIDE.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-fundamentals-003">1.3 Phân biệt tính bí mật, tính toàn vẹn, xác thực và phân quyền</a>

<details>
<summary>Xem chi tiết</summary>

# 1.3 Phân biệt tính bí mật, tính toàn vẹn, xác thực và phân quyền

Bốn khái niệm **confidentiality**, **integrity**, **authentication** và **authorization** trả lời bốn câu hỏi khác nhau của bảo mật ứng dụng Java: **ai có thể biết dữ liệu**, **dữ liệu có bị thay đổi hay không**, **chủ thể đang tương tác là ai**, và **chủ thể đó được phép làm gì**. Một hệ thống có thể làm tốt ba việc đầu nhưng vẫn rò rỉ dữ liệu nếu kiểm tra phân quyền sai.

## 1. Bốn câu hỏi phải tách bạch

| Thuộc tính | Câu hỏi cốt lõi | Rủi ro nếu thiếu | Cơ chế điển hình |
| --- | --- | --- | --- |
| **Tính bí mật (Confidentiality)** | Ai có thể **đọc/biết** nội dung? | Người không được phép thấy thông tin nhạy cảm | TLS, mã hóa khi lưu trữ, quản lý khóa, giới hạn quyền đọc |
| **Tính toàn vẹn (Integrity)** | Nội dung có bị **sửa đổi** trái phép hoặc ngoài dự kiến? | Số tiền, lệnh, file hoặc thông điệp bị thay đổi mà không bị phát hiện | HMAC, chữ ký số, mã hóa có xác thực như AES-GCM, kiểm tra dữ liệu |
| **Xác thực (Authentication)** | Chủ thể đang yêu cầu là **ai**, và bằng chứng danh tính có đáng tin? | Kẻ khác giả làm người dùng/dịch vụ hợp lệ | Mật khẩu được xác minh an toàn, MFA, passkey, session, token, chứng chỉ |
| **Phân quyền (Authorization)** | Danh tính đã xác thực được **thao tác gì trên tài nguyên nào**, trong ngữ cảnh nào? | Người dùng hợp lệ truy cập dữ liệu/quyền của người khác | Kiểm tra sở hữu tài nguyên, RBAC, ABAC, policy, least privilege |

Ghi nhớ: **xác thực danh tính đi trước quyết định phân quyền**, nhưng cả hai khác với tính bí mật và tính toàn vẹn của dữ liệu. Khi nói về bộ ba **CIA** trong bảo mật, chữ A là **Availability (tính sẵn sàng)**, không phải Authentication.

### 1.1. Tính bí mật: ngăn việc tiết lộ dữ liệu

Tính bí mật yêu cầu thông tin chỉ lộ cho chủ thể có quyền biết. Cần xét nơi dữ liệu tồn tại và di chuyển:

- **Trên đường truyền:** HTTPS/TLS che nội dung khỏi người nghe lén trên đường truyền khi kết nối được thiết lập và xác minh chứng chỉ đúng cách.
- **Khi lưu trữ:** mã hóa dữ liệu nhạy cảm ở đĩa, bản sao lưu hoặc từng trường; khóa giải mã phải được bảo vệ và phân quyền riêng.
- **Trong ứng dụng:** không để token, mật khẩu, khóa bí mật hoặc dữ liệu cá nhân lọt vào log, thông báo lỗi, cache hay phản hồi API quá rộng.
- **Ở ranh giới truy cập:** dù server có thể giải mã, server vẫn phải xác định ai được phép xem kết quả.

Ví dụ, file được mã hóa bằng AES vẫn có thể bị lộ nếu endpoint giải mã rồi trả nội dung cho bất cứ người dùng nào đã đăng nhập. **Mã hóa không tự tạo ra quy tắc ai được đọc**.

Trong Java, **AES-GCM** là lựa chọn phổ biến cho mã hóa đối xứng có xác thực: nó bảo vệ bí mật và phát hiện sửa đổi ciphertext nếu khóa, nonce và dữ liệu xác thực liên quan được dùng đúng. Việc quản lý khóa và tránh dùng lại nonce với cùng khóa là điều kiện bắt buộc.

### 1.2. Tính toàn vẹn: phát hiện hoặc ngăn sửa đổi trái phép

Tính toàn vẹn quan tâm đến việc dữ liệu còn đúng như trạng thái được tin cậy hay không. Cần phân biệt các công cụ:

- **SHA-256 không dùng khóa** tạo dấu vân tay của nội dung. Nó có ích khi giá trị hash kỳ vọng được lấy từ một nguồn đáng tin cậy. Nếu kẻ tấn công sửa được cả file lẫn hash công bố, phép so sánh hash không chứng minh nguồn gốc hoặc chống giả mạo.
- **HMAC-SHA256** dùng khóa bí mật chung để tạo mã xác thực thông điệp. Người không biết khóa khó tạo HMAC hợp lệ cho nội dung đã sửa. HMAC không mã hóa nội dung; các bên cùng giữ khóa đều có thể tạo tag.
- **Chữ ký số** dùng khóa riêng để ký và khóa công khai để kiểm tra tính toàn vẹn, nguồn ký. Để gắn khóa công khai với một danh tính cụ thể, vẫn cần cơ chế tin cậy như chứng chỉ hoặc phân phối khóa xác thực.
- **AEAD**, chẳng hạn AES-GCM, kết hợp mã hóa với kiểm tra tính toàn vẹn/xác thực của bản mã và dữ liệu AAD được ràng buộc vào tag.

**Toàn vẹn không đồng nghĩa với tính mới.** Một thông điệp HMAC đúng có thể bị phát lại (replay). Tùy giao thức, cần thêm nonce, số thứ tự, thời hạn, idempotency key hoặc bộ nhớ chống replay và kiểm tra chúng trên server.

Không dùng phép so sánh chuỗi thông thường để kiểm tra MAC theo cách có thể làm lộ thông tin thời gian; Java có **MessageDigest.isEqual(byte[], byte[])** để so sánh byte array theo cách phù hợp hơn cho trường hợp này.

### 1.3. Xác thực: kiểm chứng danh tính trước khi tin yêu cầu

Xác thực xác định **principal** (người dùng, dịch vụ, thiết bị hoặc tiến trình) qua một bằng chứng mà hệ thống có thể kiểm tra, thay vì tin tên được tự khai trong request.

- Với người dùng: password verifier được lưu bằng thuật toán băm mật khẩu có salt và chi phí phù hợp (ví dụ Argon2id, scrypt hoặc PBKDF2), kết hợp MFA/passkey khi cần. Không lưu mật khẩu thô hoặc dùng SHA-256 đơn thuần như giải pháp băm mật khẩu.
- Với phiên đăng nhập: server kiểm tra session hoặc access token; cookie phiên cần thuộc tính bảo vệ phù hợp như Secure, HttpOnly, SameSite theo ngữ cảnh.
- Với token có chữ ký: ngoài chữ ký, phải kiểm tra nhà phát hành, đối tượng nhận, hạn dùng, loại token và các ràng buộc ứng dụng áp dụng. Một token có chữ ký hợp lệ nhưng sai audience vẫn không được chấp nhận.
- Với dịch vụ: mTLS có thể xác thực danh tính dựa vào chứng chỉ client; danh tính dịch vụ đã được kiểm tra vẫn phải chịu chính sách phân quyền.

Cũng cần tách **xác thực người gọi** khỏi **xác thực nguồn gốc thông điệp bằng mật mã**. HMAC chứng minh có bên sở hữu khóa chung tạo hoặc xác nhận thông điệp, nhưng nếu nhiều bên chia sẻ khóa thì không chứng minh được chính xác cá nhân nào gửi. Chữ ký số có thể gắn với một chủ thể khi khóa và chứng chỉ được quản trị đúng. Cả hai không thay thế toàn bộ quy trình đăng nhập người dùng.

**HTTPS/TLS** thường giúp client xác thực server thông qua chứng chỉ hợp lệ, đồng thời bảo vệ đường truyền; nó không mặc nhiên cho biết người dùng của ứng dụng đã đăng nhập.

### 1.4. Phân quyền: quyết định hành động trên đúng tài nguyên

Sau khi xác thực, backend dùng danh tính tin cậy và ngữ cảnh hiện tại để quyết định có cho phép **hành động cụ thể** hay không. Quyền cần được kiểm tra ở mức chức năng **và** mức đối tượng.

Ví dụ, Alice và Bob đều có vai trò USER và đều đăng nhập thành công. Nhưng với tài liệu thuộc Alice, Bob vẫn phải bị từ chối nếu chính sách chỉ cho chủ sở hữu hoặc quản trị viên đọc. Lỗ hổng bỏ qua kiểm tra này là một dạng **Broken Object Level Authorization (BOLA/IDOR)**.

Các cách mô tả chính sách thường gặp:

| Mô hình | Quyết định dựa trên | Ví dụ |
| --- | --- | --- |
| **RBAC** (role-based) | Vai trò gắn với principal | ADMIN được quản lý tài khoản |
| **ABAC** (attribute-based) | Thuộc tính principal, tài nguyên và môi trường | Chỉ cho đọc hồ sơ cùng tenant và chưa bị khóa |
| **Kiểm tra sở hữu/quan hệ** | Quan hệ với đối tượng được yêu cầu | Chủ tài liệu được đọc tài liệu của mình |

Trong hệ thống thực tế, thường **kết hợp** các mô hình: người gọi phải có quyền READ_DOCUMENT, thuộc cùng tenant, và là chủ sở hữu hoặc thuộc nhóm được chia sẻ. Nguyên tắc là **deny by default**, **least privilege**, và kiểm tra ở backend cho mọi thao tác nhạy cảm. Việc ẩn nút trên frontend, bật CORS, hoặc có token hợp lệ không thay thế kiểm tra này.

Danh tính, role và tenant tin cậy phải được lấy từ thành phần đã xác thực và dữ liệu backend; không lấy trực tiếp role hay ownerId do client tự gửi để quyết định quyền.

## 2. Một công nghệ có thể đáp ứng nhiều mục tiêu, nhưng không thể suy diễn tất cả

| Biện pháp | Điều nó thực sự cung cấp | Điều không tự cung cấp |
| --- | --- | --- |
| **HTTPS/TLS** | Bí mật và toàn vẹn khi truyền; xác thực endpoint tùy cấu hình chứng chỉ | Đăng nhập người dùng hoặc quyền truy cập từng tài liệu |
| **AES-GCM** | Bí mật và kiểm tra sửa đổi bản mã/AAD | Danh tính người dùng, phân quyền đối tượng, chống replay ở cấp nghiệp vụ |
| **SHA-256 của file** | Dấu vân tay để so sánh với hash kỳ vọng đáng tin | Chứng minh người gửi nếu hash cũng có thể bị sửa |
| **HMAC-SHA256** | Toàn vẹn và bằng chứng sở hữu khóa chung | Che nội dung, phân biệt từng bên cùng giữ khóa, quyết định quyền |
| **Chữ ký số** | Kiểm tra sửa đổi và bằng chứng gắn với khóa ký | Che nội dung, phân quyền ứng dụng |
| **Session/token được xác minh** | Bằng chứng định danh theo quy trình xác thực | Quyền đọc mọi bản ghi |
| **RBAC/ABAC + kiểm tra tài nguyên** | Quyết định cho phép/từ chối hành động | Mã hóa dữ liệu hoặc chứng minh tính toàn vẹn trên đường truyền |

**Những trường hợp dễ nhầm:**

1. **Đã đăng nhập nhưng đọc đơn hàng người khác:** xác thực thành công, **phân quyền thất bại**.
2. **Dùng HTTPS nhưng ghi mật khẩu vào log:** đường truyền được bảo vệ, nhưng **bí mật bị phá vỡ** ở tầng ứng dụng.
3. **File có hash SHA-256 công khai đặt cạnh file và cả hai cùng bị thay:** không có căn cứ tin cậy để kết luận file nguyên vẹn.
4. **Payload đã mã hóa nhưng dùng chế độ không xác thực:** tính bí mật không bảo đảm phát hiện mọi sửa đổi; nên dùng AEAD phù hợp.
5. **HMAC hợp lệ của lệnh chuyển tiền cũ được gửi lại:** tính toàn vẹn của thông điệp có thể vẫn đúng, nhưng nghiệp vụ phải chống replay.

## 3. Đi theo một request: bốn bước kiểm tra độc lập

Xét API **GET /documents/42**, với dữ liệu có **ownerId = alice**, người gọi là **bob**:

1. **Bảo vệ truyền tải:** client kết nối HTTPS, xác minh chứng chỉ server. TLS bảo vệ nội dung trong quá trình truyền, nhưng chưa quyết định Bob có được xem tài liệu 42 hay không.
2. **Xác thực Bob:** middleware kiểm tra session hoặc token, xác định principal là bob. Thiếu/sai thông tin xác thực thường trả **401 Unauthorized** (tên HTTP hơi dễ gây nhầm: mã này liên quan đến yêu cầu xác thực).
3. **Phân quyền đối tượng:** backend đọc metadata tài liệu và kiểm tra policy: Bob có quyền READ_DOCUMENT không, có đúng tenant không, có phải chủ sở hữu hoặc người được chia sẻ không? Nếu không, thường trả **403 Forbidden**; một số hệ thống chủ động trả **404** để không tiết lộ sự tồn tại tài nguyên, nhưng phải thống nhất chính sách.
4. **Lấy và trả nội dung:** nếu được phép, server trả đúng dữ liệu; mã hóa đường truyền và cơ chế toàn vẹn tiếp tục bảo vệ dữ liệu trên kênh. Nếu dữ liệu được lưu mã hóa, giải mã chỉ diễn ra sau các kiểm tra quyền cần thiết.

Một request thực tế có thể kiểm tra quyền trước và sau khi lấy metadata, hoặc gộp điều kiện quyền trực tiếp vào truy vấn, ví dụ giới hạn theo principal/tenant. Điểm bất biến là **không trả tài liệu khi chưa xác nhận quyền đối với chính tài liệu đó**; không kiểm tra đơn thuần “đã login” hoặc “có role USER”.

Các biến thể đáng chú ý:

- **POST /documents:** cần quyền tạo trong tenant/nhóm được chọn; không cho client tự đặt ownerId/tenantId để leo quyền.
- **PUT /documents/42:** quyền đọc không mặc nhiên bao gồm quyền sửa.
- **GET /documents?tenantId=...:** phải giới hạn kết quả ở tenant thực sự được phép truy cập; lọc riêng trên frontend là không đủ.
- **Dịch vụ gọi dịch vụ:** đã xác thực bằng mTLS/token vẫn phải kiểm tra service account có quyền gọi endpoint cụ thể.

## 4. Một request, bốn quyết định độc lập

Giả sử Bob gọi `GET /documents/42`; bản ghi này có `ownerId=alice`, còn nội dung được lưu bằng AES-GCM. Việc kiểm chứng từng thuộc tính diễn ra ở những nơi khác nhau:

| Thứ tự kiểm tra | Câu hỏi | Kết quả nếu thất bại |
| --- | --- | --- |
| **Bí mật trên đường truyền** | Client có kết nối đúng HTTPS server với chứng chỉ và hostname hợp lệ không? | Không trao đổi nội dung nhạy cảm qua endpoint chưa được xác minh |
| **Xác thực người gọi** | Session/token có hợp lệ, đúng issuer/audience/hạn và ánh xạ thành Bob không? | Từ chối yêu cầu chưa xác thực (thường 401) |
| **Phân quyền đối tượng** | Bob có quyền READ_DOCUMENT trên tài liệu 42, đúng tenant và điều kiện hiện hành không? | Từ chối trước khi lấy/trả bản rõ (theo policy dùng 403 hoặc 404) |
| **Toàn vẹn bản mã** | Sau khi có quyền giải mã, khóa, nonce, ciphertext/tag và AAD có xác thực thành công không? | Bỏ kết quả, báo lỗi có kiểm soát, không sử dụng plaintext chưa được xác thực |

Thứ tự triển khai có thể khác theo query và caching, nhưng **không được trả bản rõ cho Bob nếu chưa kiểm quyền trên chính tài liệu 42**. Thành công của `Cipher.doFinal` hay HMAC trên payload không phải bằng chứng Bob được xem tài liệu. Ngược lại, quyền đọc tài liệu cũng không cho phép bỏ kiểm tra tag của dữ liệu lưu trữ.

Bài toán cấp primitive của AES-GCM có thể được kiểm qua thử thay một byte ciphertext/AAD và bắt `AEADBadTagException`; bài này đi sâu vào **cách đặt các kiểm tra bảo mật tại các lớp khác nhau** thay vì lặp lại một chương trình mã hóa độc lập.

## 5. Java 17+: minh họa toàn vẹn HMAC và phân quyền theo chủ sở hữu

Ví dụ tiếp theo cố ý tách hai việc: **verifyHmac** xác minh thông điệp có bị sửa sau khi tạo tag hay không; **canRead** kiểm tra principal đã xác thực có được đọc một tài liệu hay không. Trong ứng dụng thật, principal phải đến từ middleware/session đã xác thực đáng tin, **không được tạo từ userId tự khai trong request**.

Lưu thành **HmacAuthorizationDemo.java**:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacAuthorizationDemo {
    record Principal(String userId, Set<String> roles) {}
    record Document(String ownerId, String content) {}

    static byte[] hmac(String message, byte[] secret)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    static boolean verifyHmac(String message, byte[] expectedTag, byte[] secret)
            throws GeneralSecurityException {
        return MessageDigest.isEqual(expectedTag, hmac(message, secret));
    }

    static boolean canRead(Principal principal, Document document) {
        return principal.roles().contains("ADMIN")
                || principal.userId().equals(document.ownerId());
    }

    public static void main(String[] args) throws Exception {
        byte[] secret = "demo-secret-change-in-production"
                .getBytes(StandardCharsets.UTF_8);
        String payload = "owner=alice;amount=900";
        byte[] tag = hmac(payload, secret);

        System.out.println("Thong diep goc hop le: "
                + verifyHmac(payload, tag, secret));
        System.out.println("Thong diep bi sua hop le: "
                + verifyHmac("owner=alice;amount=990", tag, secret));

        Document doc = new Document("alice", payload);
        Principal alice = new Principal("alice", Set.of("READER"));
        Principal bob = new Principal("bob", Set.of("READER"));

        System.out.println("Alice duoc doc: " + canRead(alice, doc));
        System.out.println("Bob duoc doc: " + canRead(bob, doc));
    }
}
```

Biên dịch và chạy:

```bash
javac HmacAuthorizationDemo.java
java HmacAuthorizationDemo
```

Kết quả stdout:

```text
Thong diep goc hop le: true
Thong diep bi sua hop le: false
Alice duoc doc: true
Bob duoc doc: false
```

**Giải thích kết quả:**

- Thông điệp gốc khớp HMAC; đổi **900** thành **990** trong khi giữ tag cũ làm kiểm tra thất bại. HMAC **không che nội dung** thông điệp.
- Alice là chủ tài liệu nên được đọc; Bob có cùng vai trò READER nhưng không sở hữu tài liệu nên bị từ chối. Trong chính sách minh họa, ADMIN cũng có thể đọc. Đây là **quy tắc phân quyền riêng**, hoàn toàn không dựa vào việc HMAC của payload có hợp lệ hay không.
- Hai object Principal được tạo trực tiếp để thử chính sách. Đây **không phải ví dụ xác thực đăng nhập**. Không bao giờ coi tên người dùng do client gửi là một principal đã xác minh.
- Khóa HMAC viết sẵn trong chương trình chỉ để minh họa. Trong sản phẩm, dùng khóa bí mật đủ mạnh từ kho quản lý secret, giới hạn người có quyền sử dụng khóa, có cơ chế xoay khóa và ràng buộc ngữ cảnh/thời hạn/chống replay theo giao thức.

## 6. Các lỗi thiết kế và suy luận phổ biến

| Nhận định sai | Vì sao sai và cách sửa |
| --- | --- |
| “Đã đăng nhập thì truy cập ID bất kỳ được.” | Xác thực chỉ biết danh tính. Kiểm tra quyền trên **từng hành động và từng đối tượng**, gồm owner và tenant. |
| “Có HTTPS thì API đã an toàn.” | TLS bảo vệ kênh, không sửa được lỗi quyền truy cập, dữ liệu nhạy cảm bị log hoặc ứng dụng lộ secret. |
| “Tự băm SHA-256 là bảo đảm dữ liệu do đúng server gửi.” | Hash không có khóa không chứng minh nguồn; cần hash kỳ vọng đáng tin, HMAC hoặc chữ ký tùy mô hình tin cậy. |
| “Mã hóa đồng nghĩa chống sửa dữ liệu.” | Không phải mọi chế độ mã hóa đều xác thực; ưu tiên AEAD như AES-GCM và xử lý tag sai bằng cách từ chối dữ liệu. |
| “JWT có chữ ký hợp lệ là đủ.” | Còn phải xác thực issuer, audience, expiry, key và các policy liên quan; quyền đối tượng vẫn kiểm tra riêng. |
| “Ẩn nút sửa hoặc dùng CORS là phân quyền.” | Client và CORS không phải cổng kiểm soát quyền của backend; phải từ chối thao tác trái phép ngay trên server. |
| “HMAC chứng minh Alice đã ký.” | HMAC dùng khóa chia sẻ: mỗi bên giữ khóa đều có thể tạo MAC; muốn ràng buộc từng bên cần cơ chế danh tính/khóa phù hợp. |
| “Thông điệp có chữ ký đúng thì chắc chắn là yêu cầu mới.” | Chữ ký/MAC không tự chống phát lại; cần timestamp, nonce, sequence hoặc idempotency control. |
| “Mật khẩu nên được mã hóa để có thể giải mã khi đăng nhập.” | Mật khẩu nên được lưu dạng password hash có salt/cost; khi đăng nhập kiểm tra verifier, không cần giải mã mật khẩu cũ. |

Khi rà soát ứng dụng Java, có thể tự hỏi lần lượt:

1. **Confidentiality:** dữ liệu nào nhạy cảm, nó xuất hiện ở transport, storage, log, cache và response nào? Ai giữ khóa?
2. **Integrity:** làm sao nhận biết dữ liệu bị sửa? Ai tin cậy hash, MAC, chữ ký hoặc tag? Có chống replay không?
3. **Authentication:** principal được xác minh bằng chứng cứ nào? Session/token/chứng chỉ có được kiểm tra đầy đủ và có hết hạn không?
4. **Authorization:** cho phép **ai + hành động gì + tài nguyên nào + tenant/ngữ cảnh nào**? Có chính sách mặc định từ chối và kiểm tra phía server không?

## 7. Tự kiểm tra bằng tình huống

**Tình huống A.** Bob đăng nhập thành công. API trả hóa đơn của Alice khi Bob đổi ID trong URL. Thuộc tính thiếu là gì?  
**Đáp án:** **Authorization**, cụ thể là kiểm tra quyền ở mức đối tượng (BOLA/IDOR). Authentication có thể hoàn toàn đúng.

**Tình huống B.** Người tải file so sánh SHA-256 với giá trị lấy từ cùng một trang đã bị chiếm quyền điều khiển. Đã chứng minh toàn vẹn chưa?  
**Đáp án:** **Chưa có căn cứ tin cậy**. Kẻ tấn công có thể thay cả file lẫn hash. Cần hash/khóa xác minh đến từ nguồn đáng tin.

**Tình huống C.** Hai dịch vụ chia sẻ một khóa HMAC; tag đúng. Có thể khẳng định duy nhất dịch vụ A đã gửi không?  
**Đáp án:** **Không.** Tag chứng minh bên tạo biết khóa chung, không phân biệt các bên cùng sở hữu khóa. Cần ràng buộc danh tính và quản lý khóa riêng nếu nghiệp vụ đòi hỏi.

**Tình huống D.** Server dùng AES-GCM lưu tài liệu. Người dùng hợp lệ gọi API giải mã tài liệu của người khác. Có nên trả nội dung vì giải mã thành công?  
**Đáp án:** **Không.** AES-GCM giải quyết bảo vệ bản mã và kiểm tra sửa đổi; backend còn phải xác thực người dùng và kiểm tra quyền với tài liệu.

**Tình huống E.** Một lệnh đã ký được gửi lại y nguyên sau một giờ. Chữ ký vẫn đúng. Hệ thống cần gì thêm?  
**Đáp án:** Kiểm tra thời hạn, nonce/số thứ tự và trạng thái xử lý để chống **replay**, tùy yêu cầu giao thức.

Điểm cần mang sang các bài mật mã và bảo mật ứng dụng tiếp theo: **bí mật bảo vệ việc nhìn thấy nội dung; toàn vẹn bảo vệ việc bị sửa; xác thực thiết lập danh tính đáng tin; phân quyền áp chính sách lên hành động/tài nguyên cụ thể.** Thiết kế an toàn yêu cầu ghép các lớp bảo vệ này tại đúng ranh giới, không dùng thành công của lớp này để suy ra lớp khác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-fundamentals-004">1.4 Phân biệt mã hóa, hàm băm, MAC, chữ ký số và mã hóa biểu diễn</a>

<details>
<summary>Xem chi tiết</summary>

# 1.4 Phân biệt mã hóa, hàm băm, MAC, chữ ký số và mã hóa biểu diễn

Trong bảo mật Java, năm khái niệm thường cùng biến dữ liệu thành một chuỗi byte trông khó hiểu, nhưng giải quyết **những bài toán khác nhau**. Gọi chung tất cả là “mã hóa” dẫn tới các thiết kế nguy hiểm: dùng Base64 để giấu bí mật, dùng SHA-256 thay cơ chế xác thực, hoặc mã hóa dữ liệu mà không phát hiện được sửa đổi.

## 1. Mục tiêu và mô hình tư duy

Sau bài này, bạn cần trả lời được:

- Muốn **giữ bí mật** dữ liệu trước người không có quyền đọc thì dùng gì?
- Muốn tạo **dấu vân tay** của dữ liệu để kiểm tra thay đổi thì dùng gì, và khi nào dấu vân tay đó có thể tin cậy?
- Muốn biết thông điệp đến từ bên **cùng giữ một khóa bí mật** và chưa bị sửa thì dùng gì?
- Muốn cho một bên thứ ba dùng **khóa công khai** kiểm tra ai đã ký thì dùng gì?
- Muốn biến byte nhị phân thành chuỗi thuận tiện để lưu/truyền (Base64, hex, UTF-8) thì dùng gì?

Trước hết, phân biệt các mục tiêu an toàn:

| Mục tiêu | Câu hỏi cần trả lời | Ví dụ |
| --- | --- | --- |
| **Confidentiality – tính bí mật** | Người không có khóa có đọc được nội dung không? | Che thông tin khách hàng trong bản sao lưu |
| **Integrity – tính toàn vẹn** | Nội dung có bị thay đổi so với bản được chấp nhận không? | Phát hiện request bị sửa |
| **Authenticity – tính xác thực nguồn gốc** | Có bằng chứng thông điệp xuất phát từ chủ thể/khóa được tin cậy không? | Server xác thực webhook |
| **Public verifiability – xác minh công khai** | Người không biết khóa bí mật có thể kiểm tra bằng khóa công khai không? | Xác minh bản phát hành được nhà phát triển ký |
| **Representation – biểu diễn dữ liệu** | Cần chuyển dữ liệu sang dạng tương thích với kênh truyền/lưu trữ nào? | Đưa byte vào JSON dưới dạng Base64 |

**Quy tắc:** Đầu ra khó đọc **không có nghĩa** đầu ra được bảo vệ. Muốn chọn đúng kỹ thuật, hãy bắt đầu từ mục tiêu, đối tượng tấn công và ai được nắm khóa.

## 2. Bảng phân biệt năm kỹ thuật

| Kỹ thuật | Khóa và thao tác chính | Kết quả | Đảm bảo cốt lõi | Không tự đảm bảo |
| --- | --- | --- | --- | --- |
| **Mã hóa (encryption)** | Thường dùng khóa bí mật chung; cũng có mã hóa khóa công khai. `encrypt → decrypt` | Bản mã; người có khóa thích hợp khôi phục bản rõ | **Bí mật**; chế độ AEAD như AES-GCM còn xác thực và bảo vệ toàn vẹn dữ liệu đã mã hóa | Mã hóa đơn thuần không nhất thiết xác thực người gửi hoặc chống sửa đổi |
| **Hàm băm mật mã (cryptographic hash)** | **Không có khóa**; `hash(message)` | Digest dài cố định, không được thiết kế để đảo ngược | Dấu vân tay, hỗ trợ đối chiếu thay đổi và nhiều cấu trúc mật mã | Không bí mật, không chứng minh ai gửi, không chống kẻ tấn công tự thay dữ liệu rồi băm lại |
| **MAC (Message Authentication Code)** | Hai bên cùng giữ **khóa bí mật**; `MAC(key, message)` và `verify` | Thẻ xác thực/tag | **Toàn vẹn + xác thực theo khóa chia sẻ** | Không che nội dung, không cho bên thứ ba biết bên giữ khóa nào đã tạo MAC |
| **Chữ ký số (digital signature)** | **Khóa riêng** để ký; **khóa công khai** để xác minh | Chữ ký kèm thông điệp hoặc tham chiếu tới thông điệp | **Toàn vẹn + xác thực theo khóa ký**, bên thứ ba có thể kiểm tra nếu tin đúng khóa công khai | Không che nội dung; không tự chứng minh danh tính nếu khóa công khai chưa được gắn đáng tin với chủ thể |
| **Mã hóa biểu diễn (encoding)** | **Không có khóa bảo mật**; `encode ↔ decode` | Dạng byte/text khác, khôi phục được nếu biết chuẩn | Tương thích định dạng và truyền tải | **Không** bí mật, toàn vẹn hay xác thực |

Có hai điểm cần tách bạch:

1. **Hash khác MAC:** một digest SHA-256 không có bí mật. Ai cũng băm lại được dữ liệu bị sửa. HMAC-SHA256 thêm khóa bí mật vào một cấu trúc mật mã được thiết kế riêng.
2. **Mã hóa khác mã hóa có xác thực:** một thuật toán/chế độ chỉ cung cấp bí mật chưa chắc phát hiện sửa đổi. Với dữ liệu ứng dụng mới, thường ưu tiên **AEAD (Authenticated Encryption with Associated Data)**, ví dụ AES-GCM.

## 3. Mã hóa: bảo vệ nội dung cần giữ bí mật

### 3.1. Mã hóa đối xứng và bất đối xứng

**Mã hóa đối xứng** dùng cùng khóa bí mật (hoặc khóa liên hệ trực tiếp) để mã hóa và giải mã. Các ví dụ thực tế gồm AES-GCM, ChaCha20-Poly1305. Nó phù hợp để mã hóa phần dữ liệu lớn. Trong Java, `Cipher` của JCA/JCE hỗ trợ `AES/GCM/NoPadding`.

**Mã hóa bất đối xứng** có cặp khóa công khai/riêng. Ví dụ, một bên dùng khóa công khai của người nhận để bảo vệ một khóa phiên và người nhận dùng khóa riêng để mở. RSA-OAEP là ví dụ cho thao tác mã hóa khóa công khai, nhưng không phù hợp để mã hóa trực tiếp nội dung lớn. Hệ thống thường dùng **mã hóa lai (hybrid/envelope encryption)**: bảo vệ khóa phiên bằng kỹ thuật khóa công khai hoặc KMS, dùng AES-GCM để mã hóa payload. Cặp khóa dùng **ký** cũng thuộc mật mã bất đối xứng nhưng là **thao tác khác** với mã hóa.

Không suy ra “mã hóa bằng private key, giải mã bằng public key” là chữ ký số. Chữ ký dùng thuật toán và quy tắc ký/xác minh riêng, không phải phép đảo khóa của mã hóa.

### 3.2. Vì sao AES-GCM thường là lựa chọn phù hợp

Một lần mã hóa AES-GCM về mặt ý tưởng:

~~~text
Đầu vào:  khóa bí mật K, bản rõ P, nonce N, dữ liệu liên kết AAD
AES-GCM(K, N, P, AAD)  → ciphertext C và authentication tag T
AES-GCM-decrypt(K, N, C, AAD, T) → P hoặc lỗi xác thực
~~~

- **Ciphertext** che nội dung. **Tag** phát hiện sửa đổi ciphertext, tag hoặc AAD khi kiểm tra thành công với khóa đúng.
- **AAD (Associated Authenticated Data)** là dữ liệu **được xác thực nhưng không mã hóa**, ví dụ mã phiên bản giao thức, loại thông điệp, tenant ID, nếu những thông tin đó phải được ràng buộc vào ciphertext.
- **Nonce/IV không phải khóa và không cần giấu**, nhưng **tuyệt đối không dùng lại cùng nonce với cùng khóa** trong AES-GCM. Nonce trùng có thể làm mất cả tính bí mật lẫn khả năng xác thực. Mẫu phổ biến là nonce 96 bit (12 byte); với lưu lượng lớn phải thiết kế chiến lược cấp nonce bảo đảm duy nhất thay vì chỉ dựa vào xác suất không trùng.
- Khi lưu/truyền cần có đủ `version/keyId + nonce + ciphertext + tag` (và AAD hoặc cách tái tạo AAD chính xác). Các byte này có thể **được biểu diễn** bằng Base64; Base64 ở đây chỉ là lớp đóng gói.

**Sai lầm thường gặp:** chọn `AES/ECB/PKCS5Padding` (lộ mẫu dữ liệu) hoặc mã hóa với chế độ không xác thực rồi cho rằng “đã mã hóa thì không thể sửa”. Với hệ thống mới, dùng AEAD theo giao thức/thư viện đã được thẩm định thay vì tự ghép thuật toán.

### 3.3. Khóa cần vòng đời quản lý

Tạo khóa bằng nguồn ngẫu nhiên an toàn; lưu ở secret manager/KMS, không hard-code trong repository hay log. Xác định ai có quyền giải mã, cách xoay khóa, cách đọc bản mã cũ theo `keyId` và cách thu hồi. Khóa thử nghiệm sinh trong chương trình mẫu bên dưới **mất khi tiến trình kết thúc**, nên không dùng để lưu dữ liệu bền vững.

## 4. Hàm băm mật mã: dấu vân tay, không phải hộp khóa

Với `H = SHA-256`:

~~~text
m = "abc"
SHA-256(m) = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
~~~

SHA-256 nhận dữ liệu có độ dài tùy ý và trả **256 bit (32 byte)**. Chuỗi hex trên có 64 ký tự vì mỗi byte được biểu diễn bằng hai ký tự hex. Hàm băm mật mã tốt hướng tới các tính chất:

- **Preimage resistance:** biết digest, việc tìm một thông điệp có digest đó phải rất khó về mặt tính toán.
- **Second-preimage resistance:** biết một thông điệp, việc tìm thông điệp khác có cùng digest phải rất khó.
- **Collision resistance:** việc tìm **bất kỳ hai** thông điệp khác nhau có cùng digest phải rất khó. Va chạm về mặt toán học vẫn tồn tại vì không gian đầu vào lớn hơn không gian đầu ra.

Dùng hash để kiểm tra file có bị thay đổi **chỉ có ý nghĩa an toàn trước kẻ tấn công** nếu giá trị digest mong đợi đến từ kênh đáng tin. Nếu kẻ tấn công sửa cả file và digest đăng cùng chỗ, phép so khớp vẫn thành công. Digest tốt cho phát hiện hỏng file ngẫu nhiên, định danh nội dung và làm thành phần của nhiều giao thức; nó **không chứng minh nguồn gốc** khi đứng một mình.

**Hash không phải encryption:** không có khóa giải mã để “khôi phục hash”. Tấn công vét cạn/tra bảng vẫn có thể đoán giá trị đầu vào có ít khả năng, như mật khẩu yếu, dù SHA-256 không thể đảo ngược trực tiếp.

**Lưu mật khẩu:** không dùng SHA-256/MD5/SHA-1 trực tiếp, kể cả thêm một chuỗi salt bằng phép nối tự thiết kế. Dùng bộ dẫn xuất khóa/mật khẩu thích hợp như **Argon2id** (khi môi trường hỗ trợ), **scrypt** hoặc **PBKDF2** với tham số chi phí phù hợp và **salt riêng, ngẫu nhiên cho mỗi mật khẩu**. Đây là bài toán password hashing/KDF, khác kiểm tra checksum file. MD5 và SHA-1 cũng không còn phù hợp cho những ứng dụng yêu cầu chống va chạm như chữ ký số.

## 5. MAC: xác thực bằng bí mật chung

Một **MAC** nhận khóa bí mật `K` và thông điệp byte `M`, sinh tag `T = MAC(K, M)`. Bên nhận biết cùng khóa tính tag kỳ vọng và so sánh:

~~~text
Client giữ K                            Server giữ K
M + K → HMAC-SHA256 → tag             M nhận + K → HMAC-SHA256 → tag'
Gửi M và tag  ────────────────────→   So sánh tag và tag' → chấp nhận/từ chối
~~~

**HMAC-SHA256** là MAC dựa trên SHA-256 nhưng **không phải** `SHA-256(key + message)` do tự chế. Một số cách nối khóa vào hash có vấn đề như **length-extension attack**; hãy dùng `Mac.getInstance("HmacSHA256")`.

Điều kiện để MAC có ý nghĩa:

1. Khóa phải **bí mật, đủ mạnh và phân phối an toàn**; nếu khóa lộ, kẻ tấn công tạo tag hợp lệ.
2. Hai bên phải tính MAC trên **cùng byte chính xác**: quy ước UTF-8, thứ tự trường, canonicalization, đường dẫn HTTP, body, timestamp, số phiên bản. Hai JSON tương đương về ý nghĩa có thể khác byte và cho tag khác nhau.
3. So sánh tag bằng hàm chống rò rỉ thời gian theo chuẩn thư viện, ví dụ `MessageDigest.isEqual(...)` cho các byte tag có độ dài cố định; không dùng phép so sánh ngừng ngay ở byte khác đầu tiên.
4. **MAC không tự chống replay.** Một request có tag hợp lệ có thể bị phát lại nguyên vẹn. Giao thức cần ràng buộc thời gian, nonce/request ID, cửa sổ hiệu lực hoặc bộ lưu trạng thái chống dùng lại theo nhu cầu.

Vì **cả hai phía cùng giữ khóa**, một bên thứ ba không thể kết luận phía nào đã tạo MAC. Đây là khác biệt trọng yếu với chữ ký số: **MAC phù hợp xác thực giữa các bên chia sẻ bí mật; chữ ký phù hợp khi cần xác minh bằng khóa công khai**. MAC không giấu nội dung message.

## 6. Chữ ký số: ký bằng khóa riêng, kiểm tra bằng khóa công khai

Quy trình khái niệm:

~~~text
Người ký:
  M + privateKey → Sign → signature
  Gửi M + signature (+ thông tin nhận diện khóa)

Người kiểm tra:
  M + signature + trustedPublicKey → Verify → true/false
~~~

- **Khóa riêng (private key)** chỉ người được phép ký giữ. **Khóa công khai (public key)** có thể phân phối để kiểm tra.
- Chữ ký của một thông điệp phải **không còn hợp lệ** nếu thông điệp thay đổi, khi xác minh với khóa công khai đúng và thuật toán an toàn.
- **Ed25519** là thuật toán chữ ký hiện đại, được Java 17 hỗ trợ qua `Signature.getInstance("Ed25519")`. Các hệ thống khác còn dùng RSA-PSS hoặc ECDSA theo yêu cầu tương thích.
- Chữ ký số **không mã hóa thông điệp**. Bất cứ ai nhận bản rõ vẫn đọc được nó.
- Xác minh toán học chỉ nói chữ ký tương ứng với **một khóa**. Muốn nói “do công ty X ký”, cần **tin rằng khóa công khai thực sự thuộc công ty X** qua chứng chỉ/PKI, khóa được pin, registry được bảo vệ hoặc quy trình phân phối đáng tin.
- Mất khóa riêng, khóa bị đánh cắp, thu hồi chứng chỉ, thay đổi người sở hữu và thời điểm ký là các vấn đề quản trị **bên ngoài phép Verify**. “Không thể chối bỏ” (non-repudiation) là mục tiêu có điều kiện, **không phải kết luận pháp lý tự động** chỉ vì `verify == true`.

Ví dụ: phát hành file `app.jar` kèm chữ ký. Người dùng lấy **khóa công khai đã được tin cậy** để kiểm tra đúng file đó. Khác với HMAC, người dùng **không cần sở hữu khóa có khả năng tạo chữ ký**.

## 7. Encoding: biểu diễn để chuyển/lưu, không phải bảo mật

**Encoding** là quy tắc chuyển cách biểu diễn, ví dụ:

- **UTF-8:** biến chuỗi ký tự thành byte; phải thống nhất bộ mã khi cả hai bên tính hash/MAC/signature.
- **Base64:** chuyển byte nhị phân thành ký tự ASCII để đưa vào JSON, cấu hình hoặc văn bản. `"hello"` (UTF-8) → `aGVsbG8=`; ai cũng giải mã được.
- **Base64URL:** biến thể phù hợp với URL/token, dùng `-` và `_` thay một số ký tự; thông thường bỏ padding tùy giao thức. Chữ ký của JWT/JWS thường được **biểu diễn** bằng Base64URL, nhưng Base64URL không làm JWT được mã hóa.
- **Hex:** biểu diễn mỗi byte bằng hai chữ số hệ 16; digest SHA-256 thường hiển thị bằng hex.

Phân biệt rõ ba tầng:

~~~text
Thông điệp text  --UTF-8-->   byte gốc
byte gốc        --SHA-256-->  digest byte    --hex/Base64--> chuỗi có thể in
byte gốc        --AES-GCM-->  ciphertext     --Base64-->     chuỗi có thể gửi
byte gốc        --HMAC-->     tag            --Base64-->     chuỗi có thể gửi
~~~

Giải mã Base64 chỉ trả lại **byte đầu vào của Base64**. Nếu byte đó là ciphertext, vẫn phải có khóa và thuật toán phù hợp mới giải mã được ciphertext; nếu là digest, không có phép “giải băm”.

**Cách tự kiểm tra:** Nếu người lạ có thể giải mã chuỗi bằng công cụ web/local mà không cần khóa bí mật, chuỗi đó **không được bảo mật chỉ nhờ lớp encoding**.

## 8. Ví dụ Java 17+ chạy được: cùng dữ liệu, năm thao tác khác nhau

Ví dụ này dùng các API có sẵn trong JDK: `MessageDigest` (hash), `Mac` (MAC), `Cipher` với AES-GCM (mã hóa có xác thực), `Signature` với Ed25519 (chữ ký), và `Base64` (encoding). Nó tự tạo khóa thử nghiệm bằng nguồn ngẫu nhiên an toàn, nên **không hard-code khóa**.

Lưu nguyên chương trình thành **`CryptoPrimitivesDemo.java`**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class CryptoPrimitivesDemo {
    static final byte[] DATA = "hello".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        // 1) Hash: bất cứ ai cũng có thể tính cùng digest.
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest("abc".getBytes(StandardCharsets.UTF_8));
        System.out.println("SHA-256(abc) = "
                + HexFormat.of().formatHex(digest));

        // 2) HMAC: hai bên cùng giữ một khóa bí mật.
        SecureRandom random = new SecureRandom();
        byte[] macKey = new byte[32];
        random.nextBytes(macKey);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
        byte[] tag = mac.doFinal(DATA);
        System.out.println("HMAC valid = "
                + MessageDigest.isEqual(tag, mac.doFinal(DATA)));
        System.out.println("HMAC altered valid = "
                + MessageDigest.isEqual(tag, mac.doFinal(
                        "HELLO".getBytes(StandardCharsets.UTF_8))));

        // 3) AES-GCM: mã hóa + xác thực; dùng nonce mới cho mỗi lần mã hóa
        // với cùng khóa. AAD phải giống nhau khi mã hóa/giải mã.
        KeyGenerator aesKeys = KeyGenerator.getInstance("AES");
        aesKeys.init(128, random);
        SecretKey aesKey = aesKeys.generateKey();
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        byte[] aad = "header:v1".getBytes(StandardCharsets.UTF_8);

        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
        encrypt.init(Cipher.ENCRYPT_MODE, aesKey,
                new GCMParameterSpec(128, nonce));
        encrypt.updateAAD(aad);
        byte[] ciphertextAndTag = encrypt.doFinal(DATA);

        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
        decrypt.init(Cipher.DECRYPT_MODE, aesKey,
                new GCMParameterSpec(128, nonce));
        decrypt.updateAAD(aad);
        System.out.println("AES-GCM restored = "
                + new String(decrypt.doFinal(ciphertextAndTag),
                        StandardCharsets.UTF_8));

        // 4) Ed25519: ký với private key, kiểm tra bằng public key.
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519")
                .generateKeyPair();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(DATA);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pair.getPublic());
        verifier.update(DATA);
        System.out.println("Ed25519 valid = "
                + verifier.verify(signature));

        verifier.initVerify(pair.getPublic());
        verifier.update("HELLO".getBytes(StandardCharsets.UTF_8));
        System.out.println("Ed25519 altered valid = "
                + verifier.verify(signature));

        // 5) Base64: không cần khóa, ai cũng có thể encode/decode.
        String encoded = Base64.getEncoder().encodeToString(DATA);
        System.out.println("Base64 = " + encoded);
        System.out.println("Base64 decoded = "
                + new String(Base64.getDecoder().decode(encoded),
                        StandardCharsets.UTF_8));
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac CryptoPrimitivesDemo.java
java CryptoPrimitivesDemo
~~~

**Stdout** (không phụ thuộc vào các khóa ngẫu nhiên vì chỉ in kết quả kiểm tra):

~~~text
SHA-256(abc) = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
HMAC valid = true
HMAC altered valid = false
AES-GCM restored = hello
Ed25519 valid = true
Ed25519 altered valid = false
Base64 = aGVsbG8=
Base64 decoded = hello
~~~

Giải thích những gì vừa quan sát:

1. Hash của `abc` cố định; nó không có khóa và **không thể nói ai đã tạo dữ liệu**.
2. HMAC hợp lệ với đúng message và khóa; thay `hello` thành `HELLO` làm sai tag. Lưu ý ví dụ dùng lại đối tượng `Mac` sau `doFinal` vì JCA reset nó về trạng thái đã khởi tạo.
3. AES-GCM khôi phục `hello` bằng đúng khóa, nonce và AAD. Nếu ciphertext/tag/AAD bị sửa, `doFinal` khi giải mã sẽ thất bại xác thực (thường là `AEADBadTagException`), không được bỏ qua lỗi để nhận plaintext.
4. Chữ ký Ed25519 hợp lệ cho message gốc, không hợp lệ cho message bị thay. Người kiểm tra chỉ cần **public key** đã tin cậy.
5. Base64 chuyển `hello` thành `aGVsbG8=` và ngược lại, hoàn toàn **không cần khóa**.

**Giới hạn mẫu:** Chương trình trình diễn primitive, không phải giao thức lưu trữ/trao đổi hoàn chỉnh. Trong sản phẩm thực tế cần lưu nonce và tag cùng ciphertext; truyền/tra khóa tin cậy; chọn định dạng thông điệp, giới hạn độ dài, xác thực danh tính và xử lý lỗi không làm lộ thông tin nhạy cảm.

## 9. Chọn kỹ thuật qua tình huống thực tế

| Bài toán | Nên chọn | Vì sao và điều kiện |
| --- | --- | --- |
| Lưu bản sao dữ liệu chứa bí mật | **AES-GCM / AEAD** với khóa do KMS/secret manager quản lý | Người không có khóa không đọc được; tag phát hiện sửa, nonce phải duy nhất theo khóa |
| Kiểm tra file tải xuống có đúng bản công bố không | **SHA-256** nếu digest nhận qua kênh đáng tin; **chữ ký số** nếu cần kiểm tra nhà phát hành | Hash riêng lẻ không cản người tấn công sửa cả file lẫn digest |
| Xác thực webhook giữa đối tác và server cùng giữ secret | **HMAC-SHA256** trên byte request được quy chuẩn | Bảo vệ toàn vẹn/xác thực theo secret; thêm timestamp, request ID hoặc nonce để chống replay |
| Phát hành bản cập nhật cho nhiều người độc lập xác minh | **Chữ ký số** (ví dụ Ed25519) | Ai có public key tin cậy đều xác minh được mà không có quyền ký |
| Biểu diễn byte chữ ký/tag trong JSON hoặc URL | **Base64/Base64URL hoặc hex** | Chỉ là định dạng; phải áp dụng kỹ thuật mật mã riêng ở tầng trước |
| Lưu bộ xác minh mật khẩu | **Argon2id/scrypt/PBKDF2** với salt riêng | Chống đoán mật khẩu ngoại tuyến tốt hơn hash nhanh trực tiếp |
| Gửi dữ liệu an toàn qua mạng | **TLS** do thư viện chuẩn triển khai | Giao thức kết hợp xác thực peer, trao đổi khóa và AEAD; không tự ghép hash/MAC/encryption thành “TLS riêng” |

Một hệ thống có thể cần **nhiều** kỹ thuật cùng lúc. Ví dụ đơn giản cho tài liệu bí mật có chữ ký:

~~~text
Tài liệu gốc → người phát hành ký → (tài liệu + chữ ký)
             → mã hóa bằng khóa người nhận → ciphertext
             → Base64 nếu cần JSON
Người nhận: Base64-decode → giải mã + kiểm tag → kiểm chữ ký
            bằng public key đáng tin → đọc tài liệu
~~~

Lớp nào chịu trách nhiệm nào? **Encryption/AEAD** giữ bí mật khi truyền/lưu; **signature** giúp xác minh tác giả đã ký; **Base64** đưa byte vào định dạng truyền tải. Trình tự và cấu trúc thực tế phải theo giao thức chuẩn để tránh lộ siêu dữ liệu, lỗi xác thực hoặc nhập nhằng đối tượng được ký.

**Góc nhìn TLS 1.3:** kết nối TLS dùng mật mã bất đối xứng và cơ chế xác thực chứng chỉ/chữ ký ở giai đoạn bắt tay, thỏa thuận bí mật phiên, rồi dùng mật mã đối xứng AEAD cho dữ liệu ứng dụng. Vì vậy “HTTPS dùng RSA để mã hóa mọi byte HTTP” không phải mô tả đúng của TLS hiện đại.

**Góc nhìn JWT:** Một token **JWS** thông thường có các phần header/payload/signature được biểu diễn bằng Base64URL; **payload vẫn có thể đọc được**, chữ ký chỉ bảo vệ tính xác thực/toàn vẹn trong phạm vi khóa và validation đúng. **JWE** mới là dạng token mã hóa. Ứng dụng phải cố định thuật toán được phép, xác minh đúng khóa và kiểm tra claims như `iss`, `aud`, `exp` theo ngữ cảnh; chỉ decode payload không phải xác thực token.

## 10. Những nhầm lẫn nguy hiểm và cách sửa

| Nhầm lẫn | Hậu quả | Sửa thế nào |
| --- | --- | --- |
| “Base64/hex là mã hóa bảo mật” | Ai cũng decode được secret | Dùng encryption cho bí mật; Base64 chỉ để biểu diễn ciphertext |
| “Có SHA-256 của file là biết ai phát hành” | Kẻ tấn công thay cả file và digest | Lấy digest từ kênh tin cậy hoặc kiểm chữ ký số bằng public key đã xác thực |
| “Băm mật khẩu bằng SHA-256 là đủ” | Hash nhanh cho phép thử rất nhiều mật khẩu đoán được | Dùng password KDF phù hợp, salt riêng, tham số chi phí cập nhật |
| “HMAC dùng cùng hàm SHA-256 nên giống hash” | Bỏ sót khóa, dễ chấp nhận digest giả | Dùng cấu trúc HMAC chuẩn với khóa bí mật và xác minh tag |
| “Có HMAC thì ngăn request lặp lại” | Attacker replay request đã ký hợp lệ | Ràng buộc timestamp/nonce/request ID và thực thi chính sách chống lặp |
| “AES-GCM có nonce thì cứ dùng lại cũng được” | Nonce trùng cùng khóa có thể phá nghiêm trọng bảo mật | Bảo đảm nonce duy nhất theo khóa, thiết kế cấp phát theo tải |
| “Ciphertext luôn không thể sửa” | Chế độ mã hóa không xác thực có thể bị thay đổi có chủ đích | Ưu tiên AEAD, từ chối dữ liệu nếu tag không hợp lệ |
| “`verify(signature) == true` nghĩa là công ty X đã ký” | Chấp nhận khóa công khai do kẻ giả mạo cung cấp | Xác minh chuỗi tin cậy/danh tính hoặc pin key theo quy trình rõ ràng |
| “Chuỗi khác nhau mới là thông điệp khác nhau” | Nhầm text, Unicode normalization, JSON serialization, charset | Quy chuẩn chính xác **byte được hash/MAC/ký** và encoding trước khi tính |

**Phân biệt khi debug:** `BadPaddingException`/`AEADBadTagException` ở tầng giải mã không giống HMAC mismatch, signature verification false hay Base64 decode error. Cùng biểu hiện “không đọc được dữ liệu” nhưng nguyên nhân và cách xử lý khác: thiếu khóa, sai nonce/AAD, dữ liệu bị sửa, tag sai, không tin public key, hoặc chuỗi truyền tải sai định dạng. Không “sửa” bằng cách tắt xác thực hay thử thuật toán ngẫu nhiên.

## 11. Bài tập tự kiểm tra có đáp án

**Tình huống A:** API trả một chuỗi Base64 chứa số thẻ thanh toán; người dùng nào đọc response cũng giải mã được. Dữ liệu đã được giữ bí mật chưa?

**Đáp án:** Chưa. Base64 là biểu diễn thuận nghịch công khai. Cần kiểm soát quyền truy cập và, nếu phải mã hóa ở tầng dữ liệu, dùng cơ chế encryption phù hợp với quản lý khóa.

**Tình huống B:** Một trang tải phần mềm đăng cùng thư mục file `app.zip` và `SHA256SUMS`. Nếu trang bị chiếm quyền, hacker sửa cả hai, SHA-256 có phát hiện được không?

**Đáp án:** Không nhất thiết. Digest mới có thể hoàn toàn khớp file độc hại. Cần digest từ kênh tin cậy độc lập hoặc chữ ký số xác minh qua public key được tin cậy.

**Tình huống C:** Server A và B cùng giữ secret để kiểm webhook. Người C không có secret nhưng biết HMAC tag của một request cũ. C có thể tạo request mới tùy ý hợp lệ không? Có thể gửi lại request cũ không?

**Đáp án:** Không thể tạo MAC hợp lệ cho request mới nếu HMAC/khóa an toàn; **có thể replay nguyên request cũ** nếu giao thức không chống phát lại.

**Tình huống D:** Khách hàng cần xác minh thông báo cập nhật do nhà phát hành ký mà không được quyền tự tạo thông báo ký hợp lệ. Chọn HMAC hay chữ ký số?

**Đáp án:** Chữ ký số. Nhà phát hành giữ private key; khách hàng chỉ giữ public key đã được xác thực.

**Tình huống E:** Bạn mã hóa bằng AES-GCM, giữ nguyên khóa nhưng dùng cùng nonce 12 byte cho mọi bản ghi. Hệ thống có an toàn không?

**Đáp án:** Không. Quy tắc nonce duy nhất theo khóa đã bị vi phạm. Phải đổi thiết kế cấp nonce và đánh giá lại tính an toàn của dữ liệu đã mã hóa dưới khóa bị sử dụng sai.

## 12. Ghi nhớ để áp dụng trong Java

- **Cần bí mật:** `Cipher` với AEAD như `AES/GCM/NoPadding`, cộng quản lý khóa và nonce đúng.
- **Cần dấu vân tay:** `MessageDigest` với SHA-256/SHA-3; tính xác thực của digest mong đợi phải được bảo đảm **bởi nguồn đáng tin khác**.
- **Cần xác thực bằng khóa chia sẻ:** `Mac` với `HmacSHA256`; quy chuẩn byte, bảo vệ secret và chống replay ở tầng giao thức.
- **Cần xác minh bằng public key:** `Signature` với Ed25519/RSA-PSS/ECDSA theo hệ sinh thái; xác minh danh tính/chứng chỉ gắn với khóa công khai.
- **Cần chuyển byte thành text:** `Base64` hoặc `HexFormat`, và thống nhất `StandardCharsets.UTF_8` khi chuyển text sang byte.

Mỗi thao tác tạo ra một loại bằng chứng khác nhau. Khi thiết kế API, lưu trữ, cập nhật phần mềm hay giao tiếp mạng, hãy mô tả riêng: **dữ liệu nào được giữ bí mật, byte nào được xác thực, khóa nào được ai tin cậy, và ai có thể kiểm tra**. Trả lời được bốn câu đó giúp tránh hầu hết sự nhầm lẫn giữa encryption, hash, MAC, chữ ký số và encoding.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-fundamentals-005">1.5 Quan hệ giữa khóa, thuật toán, tham số, giao thức và danh tính</a>

<details>
<summary>Xem chi tiết</summary>

# 1.5. Quan hệ giữa khóa, thuật toán, tham số, giao thức và danh tính

Trong mật mã ứng dụng, nói “hệ thống dùng AES-256” hoặc “API có chứng chỉ TLS” chưa đủ để kết luận hệ thống an toàn. Một cơ chế bảo mật thực sự hình thành khi **khóa đúng** được dùng với **thuật toán phù hợp**, **tham số hợp lệ**, trong **giao thức có thứ tự và quy tắc kiểm tra rõ ràng**, đồng thời gắn kết kết quả mật mã với **danh tính đã được tin cậy**.

Sau bài này, người học cần có khả năng:
- Phân biệt vai trò và quan hệ của khóa, thuật toán, tham số, giao thức và danh tính.
- Giải thích vì sao một chữ ký hợp lệ, bản mã giải mã được hoặc kết nối TLS thành công vẫn có thể chưa đủ để tin tưởng đối tác.
- Nhận ra cách các tham số như nonce, salt, AAD, độ dài tag, phiên bản giao thức ảnh hưởng đến tính an toàn.
- Thiết kế một luồng bảo vệ dữ liệu có kiểm tra danh tính, quản lý khóa, chống sửa đổi và chống phát lại phù hợp.
- Liên hệ mô hình đó với API mật mã trong Java 17+.

## 1. Năm thành phần và câu hỏi mà mỗi thành phần trả lời

| Thành phần | Câu hỏi chính | Ví dụ | Sai lầm thường gặp |
| --- | --- | --- | --- |
| **Khóa (key)** | Ai nắm bí mật/quyền mật mã nào? | Khóa AES phiên, khóa riêng Ed25519, khóa công khai máy chủ, PSK | Cho rằng biết tên thuật toán là đủ để giải mã; để lộ khóa trong log |
| **Thuật toán (algorithm)** | Phép toán bảo vệ thông tin là gì? | AES-GCM, Ed25519, SHA-256, HMAC-SHA256, HKDF, ECDHE | Dùng hash thường để xác thực dữ liệu trước đối thủ chủ động |
| **Tham số (parameters)** | Thuật toán được vận hành ở cấu hình nào, cho thông điệp nào? | Khóa 128/256 bit, nonce 96 bit, tag 128 bit, AAD, salt, curve, hash, work factor | Tái sử dụng nonce của GCM với cùng khóa |
| **Giao thức (protocol)** | Các bên gửi gì, theo thứ tự nào, kiểm tra gì và xử lý lỗi ra sao? | TLS 1.3 handshake, AEAD record format, quy tắc trao đổi và xoay khóa | Có thuật toán mạnh nhưng không xác thực handshake hoặc không chống replay |
| **Danh tính (identity)** | Khóa hoặc kết quả mật mã gắn với thực thể nào, dựa trên niềm tin nào? | Tên DNS trong SAN của chứng chỉ, service account, người dùng, thiết bị | Chấp nhận chữ ký của bất kỳ khóa công khai nào do chính phía gửi cung cấp |

Đọc theo chuỗi:

```text
Mục tiêu an ninh + mô hình đe dọa
             ↓
Xác định danh tính và mối quan hệ tin cậy cần chứng minh
             ↓
Chọn giao thức có quy tắc thương lượng, xác thực và chống lỗi
             ↓
Giao thức chọn thuật toán, khóa và tham số thích hợp
             ↓
Phép toán mật mã tạo kết quả; bên nhận kiểm tra rồi mới sử dụng
```

Các lớp ràng buộc lẫn nhau. Một khóa không có ý nghĩa thực tế nếu chưa biết mục đích, thuật toán và quyền sử dụng. Một chữ ký chỉ xác thực **sự sở hữu khóa ký**; muốn xác thực danh tính phải biết vì sao khóa công khai đối ứng được tin là của chủ thể cần tìm. Một giao thức không thể an toàn nếu bỏ qua việc xử lý lỗi xác thực hoặc chấp nhận tham số yếu.

## 2. Khóa: tài sản mật mã và phạm vi quyền hạn

### 2.1. Khóa đối xứng, bất đối xứng và khóa phiên

- **Đối xứng:** hai phía cùng biết một khóa bí mật `K`. AES-GCM dùng `K` để mã hóa và tạo authentication tag; bên giải mã cũng cần `K`. HMAC dùng khóa đối xứng để xác thực thông điệp, nhưng không mã hóa nội dung. Vì nhiều bên có thể cùng giữ `K`, một tag hợp lệ **không chứng minh duy nhất người gửi là ai**.
- **Bất đối xứng:** một cặp gồm khóa riêng `sk` và khóa công khai `pk`, có quan hệ theo thuật toán. Với chữ ký số, `sk` ký và `pk` xác minh; người xác minh không cần biết `sk`. Khóa công khai không phải “bí mật được mã hóa”.
- **Khóa dài hạn:** chẳng hạn khóa riêng TLS máy chủ hoặc khóa gốc được quản lý trong KMS. Chúng có nhiệm vụ và vòng đời riêng.
- **Khóa tạm/khóa phiên:** được thiết lập hoặc dẫn xuất cho một kết nối, phiên, mục đích hay chiều truyền. TLS 1.3 thông thường dùng bí mật trao đổi tạm thời và HKDF để dẫn xuất khóa lưu lượng; khóa dùng mã hóa bản ghi khác khóa riêng xác thực máy chủ.

**Không đồng nhất password với cryptographic key.** Mật khẩu thường có entropy thấp, cần hàm dẫn xuất/hashing chuyên cho mật khẩu với salt và chi phí tính toán phù hợp (ví dụ Argon2id qua thư viện được đánh giá; PBKDF2 trong môi trường cần tương thích). HKDF phù hợp để dẫn xuất từ vật liệu khóa đã có entropy đủ cao, không tự làm cho mật khẩu yếu trở thành mạnh.

### 2.2. Khóa phải gắn với mục đích và vòng đời

Thông tin quản trị tối thiểu cho một khóa gồm: `keyId`, loại/thuật toán được phép, mục đích (`encrypt`, `sign`, `derive`…), chủ sở hữu/miền tin cậy, phiên bản, trạng thái (active/retired/revoked), thời gian hiệu lực, vị trí lưu giữ và quy tắc xoay khóa.

`keyId` chỉ là **nhãn tra cứu**, không phải bằng chứng danh tính hay bí mật. Nếu gói dữ liệu nói `keyId=server-prod`, bên nhận vẫn phải lấy khóa từ kho tin cậy và kiểm tra khóa đó có quyền dùng cho nghiệp vụ đang xét. Không cho phép phía không tin cậy tự chọn bất cứ khóa nào để qua kiểm tra.

Các nguyên tắc thực hành:
1. Sinh khóa bí mật bằng nguồn ngẫu nhiên mật mã phù hợp; `java.util.Random` không dành cho sinh khóa.
2. Giới hạn phân phối và quyền dùng khóa; ưu tiên keystore/KMS/HSM theo mức rủi ro, tránh đặt khóa trong source, URL hay log.
3. Tách khóa theo môi trường, tenant, mục đích và chiều truyền khi giao thức yêu cầu; dẫn xuất bằng KDF có nhãn/ngữ cảnh thích hợp.
4. Xoay và thu hồi khóa bằng quy trình đã định; dữ liệu cũ có thể cần khóa giải mã cũ trong thời gian giữ liệu. Thu hồi chứng chỉ/khóa ký ảnh hưởng đến việc tin cậy chữ ký và phiên phát sinh sau đó.
5. Xác định rõ hậu quả khi khóa bị lộ: khóa đối xứng lộ có thể ảnh hưởng bí mật và tính toàn vẹn của dữ liệu dùng nó; khóa ký lộ cho phép giả mạo chữ ký trong phạm vi tin cậy liên quan.

## 3. Thuật toán: mỗi loại giải quyết một bài toán khác nhau

| Nhóm | Tính chất chính | Điển hình | Điều không tự bảo đảm |
| --- | --- | --- | --- |
| Mã hóa xác thực (AEAD) | Bí mật dữ liệu + phát hiện sửa đổi ciphertext/AAD khi kiểm tra tag thành công | AES-GCM, ChaCha20-Poly1305 | Không tự nhận diện một con người nếu nhiều bên chia sẻ khóa; không tự chống phát lại |
| Chữ ký số | Kiểm tra dữ liệu gắn với khóa công khai tương ứng và không bị sửa sau khi ký | Ed25519, ECDSA, RSA-PSS | Không giữ bí mật nội dung; không tự chứng minh `pk` thuộc người có thẩm quyền |
| MAC | Toàn vẹn và xác thực giữa những bên chia sẻ bí mật | HMAC-SHA256 | Không công khai xác minh; khó quy trách nhiệm một bên khi nhiều người cùng biết khóa |
| Hash mật mã | Tạo digest có tính kháng va chạm/kháng tiền ảnh theo thiết kế | SHA-256, SHA-384 | Không xác thực trước đối thủ có thể sửa cả nội dung lẫn digest |
| KDF | Dẫn xuất khóa có ngữ cảnh và độ dài phù hợp | HKDF, PBKDF2 (trường hợp mật khẩu) | Không thay được entropy tốt của đầu vào |
| Trao đổi/thỏa thuận khóa | Tạo bí mật chung mà không gửi khóa phiên trực tiếp | (EC)DHE, cơ chế KEM phù hợp | Chưa xác thực đối tác nếu thiếu ràng buộc danh tính với transcript |

Ví dụ: hàm `SHA-256(message)` có thể phát hiện sự thay đổi khi digest được lấy từ **kênh tin cậy**. Nếu đối thủ thay được cả `message` và digest, hash thuần không chống được giả mạo. Muốn bảo vệ dữ liệu trước đối thủ chủ động phải chọn MAC/chữ ký hoặc AEAD tương ứng với mô hình khóa.

**Tên thuật toán phải đủ cụ thể.** “AES” chỉ xác định block cipher, chưa chỉ ra mode và quy tắc bảo vệ toàn vẹn. Trong Java, `Cipher.getInstance("AES/GCM/NoPadding")` thể hiện transformation cụ thể; `NoPadding` ở đây không có nghĩa là bỏ authentication tag. Cũng vậy, “RSA” chưa nói là ký RSA-PSS hay mã hóa RSA-OAEP; không sử dụng “mã hóa bằng private key” như một cách thay cho chữ ký số.

## 4. Tham số: những giá trị nhỏ có thể quyết định an toàn

Mỗi thuật toán có tập tham số được định nghĩa riêng. Không có một cấu hình “an toàn cho mọi thứ”.

| Trường hợp | Tham số cần hiểu | Ý nghĩa |
| --- | --- | --- |
| AES-GCM | Độ dài khóa 128/256 bit; nonce/IV, thường 96 bit; tag thường 128 bit; AAD | Giữ bí mật và kiểm tra tag. **Không lặp cặp (khóa, nonce) cho hai lần mã hóa** |
| HKDF | Hash, `salt`, `info`, độ dài đầu ra | Dẫn xuất khóa cho từng ngữ cảnh; `info` có thể tách ứng dụng, phiên, chiều truyền |
| Dẫn xuất từ mật khẩu | `salt` riêng, work factor/chi phí tính toán, hàm KDF | Làm tăng chi phí thử đoán và tránh cùng mật khẩu tạo cùng kết quả khi salt khác |
| Ed25519 | Thuật toán và định dạng khóa/chữ ký; chính sách xác thực khóa công khai | Họ thuật toán đã xác định đường cong/chế độ; không trộn nhầm với ECDSA |
| ECDSA / ECDH | Đường cong/nhóm được chấp nhận, ví dụ P-256 | Hai phía phải thống nhất nhóm hợp lệ; xác thực vẫn là bước riêng |
| RSA-PSS | Hàm hash, MGF, độ dài salt, tham số kiểm tra | Bên ký và xác minh phải thống nhất cấu hình |
| TLS 1.3 | Phiên bản, AEAD/HKDF hash suite, nhóm key exchange, thuật toán chữ ký, chính sách certificate | Thương lượng có kiểm tra, không để đối thủ ép chọn cấu hình không được phép |

Ba khái niệm dễ nhầm:

- **Nonce/IV:** giá trị dùng cho một lần mã hóa theo quy tắc của mode; thông thường không phải bí mật. Trong AES-GCM, bảo đảm tính duy nhất cho mỗi khóa là yêu cầu sống còn. Một nonce ngẫu nhiên 96 bit dùng được trong ví dụ nhỏ, nhưng hệ thống lưu lượng lớn/phân tán cần quản lý xác suất va chạm, giới hạn số lần dùng khóa và chiến lược cấp nonce thống nhất; có thể dùng bộ đếm được phân vùng đúng cách.
- **Salt:** thường là giá trị công khai làm đầu vào dẫn xuất/hashing, ví dụ tránh việc hai mật khẩu giống nhau tạo cùng hash khi dùng salt khác. Salt **không** thay thế khóa hoặc entropy đầu vào.
- **Tag:** kết quả xác thực (ví dụ GCM authentication tag); bên giải mã phải kiểm tra thành công trước khi tin và sử dụng plaintext. Không bỏ qua ngoại lệ xác thực.

**AAD (Additional Authenticated Data)** là dữ liệu không cần mã hóa nhưng cần bảo vệ tính toàn vẹn, chẳng hạn phiên bản envelope, loại thông điệp, tenantId hoặc resourceId. Bên mã hóa và giải mã phải dùng đúng cùng AAD. AAD không bảo mật giá trị trước người quan sát và không tự quyết định ai được phép truy cập.

Hãy suy nghĩ về một sự cố: hai request của hai tenant được mã hóa bằng cùng khóa; nếu định dạng chỉ chứa ciphertext và nonce, một ciphertext hợp lệ có thể bị chuyển sang ngữ cảnh tenant khác khi ứng dụng không gắn/kiểm tra tenant ở tầng giao thức. Đặt định danh ngữ cảnh vào AAD **và** kiểm tra quyền nghiệp vụ giúp ngăn sự nhầm lẫn này. Không coi một trường client tự khai là danh tính đáng tin.

## 5. Giao thức: kết hợp phép toán thành lời bảo đảm có thể kiểm chứng

Một giao thức định nghĩa **vai trò, trình tự, định dạng, trạng thái, kiểm tra và xử lý lỗi**. Cùng AES-GCM và Ed25519 nhưng hai giao thức khác nhau có thể cho bảo đảm an ninh khác nhau.

Giao thức tối thiểu phải trả lời:
1. Ai khởi tạo? Ai là người gửi/người nhận? Danh tính mong đợi lấy từ cấu hình hoặc ngữ cảnh tin cậy nào?
2. Hai bên chọn thuật toán/phiên bản/tham số bằng quy tắc nào? Có danh sách cho phép và quy tắc từ chối thuật toán yếu/không rõ không?
3. Khóa đến từ đâu: khóa cấu hình, khóa tạm, KDF, trao đổi khóa hay KMS? Có gắn khóa với phiên, vai trò và đối tác không?
4. Những byte nào được mã hóa, ký hoặc đưa vào AAD? Dữ liệu có được mã hóa tuần tự (canonical encoding), phân định loại thông điệp và phiên bản rõ ràng không?
5. Đối tác kiểm tra tag/chữ ký/chứng chỉ/tên máy chủ và quyền sử dụng khi nào? Có từ chối mọi lỗi trước khi xử lý business logic không?
6. Có chống **replay**, **downgrade**, nhầm khóa/nhầm đối tác, thông điệp sai thứ tự và xử lý retry/idempotency không?

### 5.1. Ví dụ TLS 1.3: ràng buộc khóa, thuật toán, tham số và danh tính

Một kết nối TLS 1.3 dùng xác thực máy chủ bằng chứng chỉ thông thường có thể hình dung như sau:

```text
Client                                              Server
  |-- ClientHello: TLS version, suites, key_share --->|
  |<-- ServerHello: lựa chọn hợp lệ, key_share -------|
  |             thiết lập bí mật handshake           |
  |<-- Certificate, CertificateVerify, Finished ------|
  |  kiểm tra chain, SAN/hostname, chữ ký transcript   |
  |-- Finished --------------------------------------->|
  |<========== dữ liệu ứng dụng AEAD hai chiều ======>|
```

Đây là **sơ đồ đơn giản hóa** của đường xác thực bằng chứng chỉ, không phải toàn bộ các kiểu handshake TLS 1.3. TLS 1.3 còn hỗ trợ PSK và resumption; phía client có thể được xác thực bằng certificate khi cấu hình mTLS.

Vai trò của từng lớp:
- **Thuật toán/tham số:** các bên thương lượng AEAD, HKDF hash, key-share group và thuật toán chữ ký theo quy tắc TLS 1.3. Cipher suite như `TLS_AES_128_GCM_SHA256` biểu thị AEAD và hash liên quan, **không tự quyết định** nhóm trao đổi khóa hay thuật toán chữ ký.
- **Khóa:** bí mật thu được từ key establishment/PSK được đưa qua key schedule, dẫn xuất các khóa handshake và application traffic có mục đích khác nhau. Khóa riêng dài hạn của server được dùng để chứng minh danh tính qua chữ ký khi dùng certificate; nó không được gửi qua mạng.
- **Giao thức:** transcript handshake và các thông điệp xác nhận liên kết việc chọn tham số, khóa và quá trình xác thực; một đối thủ không thể tùy ý thay đổi chúng mà vẫn qua các kiểm tra mật mã.
- **Danh tính:** client xác minh đường tin cậy của chứng chỉ **và** tên server mong đợi với SAN DNS/IP, cùng các quy tắc hiệu lực/chính sách áp dụng. Chỉ nhìn thấy ổ khóa hay nhận được một certificate bất kỳ không đủ để kết luận đã kết nối đúng server.

Kết nối HTTPS bảo vệ một **kênh vận chuyển** tới endpoint đã xác thực theo chính sách; không tự bảo đảm người dùng được quyền thao tác, bảo vệ dữ liệu sau khi TLS termination, hay ngăn ứng dụng phía server xử lý sai quyền.

### 5.2. AEAD không tự ngăn phát lại

Một bản tin có `ciphertext + tag` hợp lệ vẫn có thể là bản tin **cũ** được gửi lại. Tag xác minh tính toàn vẹn và bên sở hữu khóa, không mặc định xác minh tính mới của sự kiện. Giao thức ứng dụng cần `messageId`/sequence number/counter/timestamp+window hoặc token dùng một lần, kết hợp trạng thái kiểm tra, phù hợp với retry và idempotency. Bản thân các byte được kiểm tra toàn vẹn phải bao gồm ngữ cảnh dùng để chống replay.

## 6. Danh tính: từ “đúng khóa” đến “đúng thực thể được phép”

Xét hai câu hỏi khác nhau:

- **Cryptographic authentication:** bản tin/handshake có do bên giữ khóa phù hợp tạo ra không?
- **Identity binding:** khóa được tin là thuộc về dịch vụ/người dùng/thiết bị nào, và điều đó được xác minh dựa trên nguồn tin cậy nào?

Sau đó còn câu hỏi **authorization**: thực thể đã xác thực có quyền thực hiện thao tác cụ thể trên tài nguyên này không? Xác thực thành công không thay cho phân quyền.

### 6.1. Chứng chỉ số và bản đồ danh tính–khóa

Chứng chỉ X.509 chứa khóa công khai, các thuộc tính nhận dạng (ví dụ `subjectAltName`), thời hạn và ràng buộc sử dụng; chữ ký của CA hỗ trợ kiểm tra ràng buộc ấy theo chuỗi tin cậy. Khi xác thực HTTPS, thông thường phải kiểm tra chain đến trust anchor phù hợp, hostname/SAN, validity và các chính sách sử dụng khóa/chứng chỉ có liên quan; kiểm tra thu hồi tùy chính sách và cấu hình của hệ thống. Một chứng chỉ tự ký không mặc nhiên được tin; nó chỉ phù hợp nếu được tin cậy/ghim bằng quy trình riêng đã thiết lập.

Một certificate hợp lệ cho `api.example.com` không đồng nghĩa mọi lời gọi từ server đó đều có quyền quản trị cơ sở dữ liệu. Certificate và danh tính kết nối là **đầu vào** cho chính sách phân quyền, không phải chính sách phân quyền.

Ví dụ ràng buộc:
- `service-account/orders-prod` → một hoặc nhiều khóa/certificate được registry đáng tin cấp/quản lý.
- `api.example.com` → certificate có SAN phù hợp và chain đến CA được tin; client đã có **tên mong đợi** từ URL/cấu hình.
- `user:alice` → được ứng dụng xác thực qua cơ chế danh tính (session/OIDC, v.v.) và ánh xạ vào quyền; không tự gán user chỉ từ keyId do client gửi.

Trong thực tế, một danh tính có thể thay khóa khi rotation; một khóa có thể phục vụ một scope hạn chế; khóa đã thu hồi hoặc hết hạn phải được xử lý theo chính sách của thời điểm xác minh.

### 6.2. Vì sao “verify(signature) = true” vẫn có thể bị lừa?

Đối thủ tạo cặp khóa riêng của họ, ký lệnh `transfer 100` và gửi cả chữ ký lẫn khóa công khai do họ tạo. Hàm verify đương nhiên trả `true`. Nếu server dùng khóa công khai vừa nhận làm nguồn duy nhất để quyết định người ký là giám đốc tài chính, hệ thống đã mắc **identity/key substitution**.

Cách đúng: lấy khóa/certificate từ registry, trust store hoặc quy trình xác thực đã được kiểm tra; xác định chủ thể được phép ký loại lệnh đó; bảo đảm chữ ký bao phủ chính xác nội dung và ngữ cảnh (phiên bản, loại lệnh, tài khoản, tenant, nonce/requestId) trước khi thực thi.

**Chữ ký không mã hóa thông điệp.** Chữ ký hợp lệ còn không mặc nhiên tạo ra “chối bỏ không thể” trong mọi bối cảnh pháp lý/vận hành: điều đó phụ thuộc việc bảo vệ khóa, chính sách định danh và chứng cứ hệ thống.

## 7. Bài toán xuyên suốt: gửi lệnh thanh toán giữa hai dịch vụ

Giả sử `orders-service` gửi `payment-service` yêu cầu thanh toán. Mục tiêu là: không lộ số liệu trên đường truyền, không bị sửa người nhận/số tiền, không chấp nhận request lặp và chỉ cho đúng dịch vụ/tenant gọi API.

Một thiết kế có thể gồm các lớp sau:

1. **Danh tính kết nối:** client đặt URL máy chủ mong đợi; dùng TLS 1.3/HTTPS với certificate verification. Nếu áp dụng mTLS, payment-service còn xác thực danh tính client dựa trên certificate và ánh xạ sang service principal.
2. **Quyền ứng dụng:** xác thực access token hoặc cơ chế service authorization theo chính sách; kiểm tra issuer, audience, thời gian hiệu lực, quyền và tenant; không coi mTLS hoặc token hợp lệ là quyền tuyệt đối trên mọi khoản thanh toán.
3. **Giao thức nghiệp vụ:** định nghĩa body và định dạng chính xác, `requestId` duy nhất, version và TTL. Server lưu/truy vấn idempotency key để xử lý retry an toàn và từ chối lệnh phát lại trái chính sách.
4. **Mã hóa bổ sung nếu cần:** nếu dữ liệu phải được bảo vệ vượt qua TLS termination, mã hóa AEAD ở tầng thông điệp; khóa chỉ tới được endpoint ứng dụng mong muốn, bao gồm `version|tenantId|requestId|messageType` trong AAD được kiểm tra.
5. **Xác nhận provenance nếu cần:** ký canonical representation của lệnh bằng khóa ký đã gắn với orders-service. Payment-service kiểm tra chữ ký bằng khóa đã đăng ký đáng tin, rồi kiểm tra quyền của principal trước khi xử lý.
6. **Vòng đời:** rotate key và certificate, giám sát lỗi xác thực, quản lý thời gian và lưu log audit phù hợp; không log bí mật, plaintext nhạy cảm hoặc private key.

Ví dụ envelope cho dữ liệu mã hóa ứng dụng (chỉ minh họa format, không phải tiêu chuẩn giao thức):

```json
{
  "version": 1,
  "algorithm": "AES-128-GCM",
  "keyId": "orders-data-v3",
  "nonce": "<base64-12-byte-nonce>",
  "aad": {
    "tenantId": "t-17",
    "messageType": "payment-request",
    "requestId": "r-204"
  },
  "ciphertextAndTag": "<base64-data>"
}
```

Trong thiết kế thực tế, cần quy định **serialization chuẩn** của AAD (thứ tự trường, encoding UTF-8, độ dài/phân cách, version), thuật toán cho phép cố định ở cấu hình đáng tin, nguồn dữ liệu mà server dùng xác định tenant/request, cách từ chối giá trị không hợp lệ và quản lý keyId. Không tin `algorithm` hoặc `keyId` chỉ vì client khai báo trong JSON. Việc chứa AAD rõ ràng trong envelope hỗ trợ kiểm tra tính toàn vẹn, nhưng không làm AAD trở thành bí mật.

## 8. Java 17+: chữ ký hợp lệ vẫn có thể đến từ khóa không đáng tin

Ví dụ chạy độc lập này mô phỏng hai cặp khóa Ed25519: một public key đã được bên nhận **tin cậy cấu hình trước**, và một cặp khóa **do người gửi không đáng tin tự sinh**. Kẻ tấn công ký chính nội dung bằng khóa của họ; bên nhận phải xác minh bằng **khóa của danh tính mong đợi**, không bằng public key tùy ý gửi kèm request.

Lưu thành `IdentityBindingDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

public class IdentityBindingDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("Ed25519");
        KeyPair trusted = generator.generateKeyPair();
        KeyPair attacker = generator.generateKeyPair();
        byte[] request =
                "pay:100".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(attacker.getPrivate());
        signer.update(request);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(attacker.getPublic());
        verifier.update(request);
        System.out.println("Untrusted key verifies own signature: "
                + verifier.verify(signature));

        verifier.initVerify(trusted.getPublic());
        verifier.update(request);
        System.out.println("Trusted key verifies attacker signature: "
                + verifier.verify(signature));
    }
}
```

Chạy bằng JDK 17+:

```bash
javac --release 17 IdentityBindingDemo.java
java IdentityBindingDemo
```

Kết quả khi chạy với provider Ed25519 chuẩn:

```text
Untrusted key verifies own signature: true
Trusted key verifies attacker signature: false
```

**Vì sao hai dòng khác nhau?** Phép toán chữ ký đúng với khóa của attacker, nhưng **khóa đó không thuộc danh tính mà ứng dụng dự định tin cậy**. Đây là lỗi nhầm khóa/danh tính (`key substitution`), không phải lỗi toán học Ed25519. Trong sản phẩm thực, bên nhận lấy public key từ registry/chứng chỉ được tin cậy, gắn với sender, tenant, purpose và trạng thái thu hồi. Còn phải kiểm tra rằng signer được **phân quyền** ký đúng loại lệnh và bytes của lệnh bao gồm thông tin ngữ cảnh cần xác thực.

Mô phỏng tạo cả hai key trong RAM, không có certificate/PKI và không chứng minh danh tính ngoài đời. Nó **chỉ chứng minh** vì sao `Signature.verify` không đủ để cấp quyền giao dịch.

## 9. Thêm tham số và giao thức để buộc quyền vào bản tin

Với lệnh thanh toán `tenant=t-17, requestId=r-204, pay=100`, đừng chỉ ký chuỗi `pay=100` rồi xem kết quả là chấp nhận toàn bộ thao tác. Một hợp đồng thông điệp có thể yêu cầu:

1. **Khóa và identity:** lookup `keyId` trong registry đáng tin, xác minh khóa thuộc `orders-service`, còn hiệu lực và được phép ký lệnh của tenant này.
2. **Thuật toán và tham số:** khóa ký/verify dùng cùng thuật toán và cấu hình được allowlist; với dữ liệu mã hóa AEAD, thống nhất khóa, nonce duy nhất, tag và AAD version/tenant/requestId.
3. **Giao thức:** quy định chính xác bytes được ký, dạng canonical encoding, message type, hạn hiệu lực, requestId và kiểm tra chống replay.
4. **Thực thi:** chỉ sau verification và authorization mới chạy nghiệp vụ; sử dụng idempotency key để không cộng tiền hai lần khi có retry.

Tách **xác minh phép toán** (`verify == true`, tag hợp lệ) khỏi **đánh giá ngữ cảnh** (đúng khóa, đúng người, đúng tenant, đúng thời điểm và quyền). Đó là đóng góp chính của quan hệ khóa–thuật toán–tham số–giao thức–danh tính.

## 10. Những lỗi thiết kế phổ biến nhìn qua năm thành phần

| Hiện tượng | Thành phần bị thiếu hoặc gắn sai | Hậu quả và hướng xử lý |
| --- | --- | --- |
| Dùng AES-ECB vì “AES mạnh” | Thuật toán/mode | Lộ mẫu dữ liệu, thiếu xác thực; dùng AEAD theo nhu cầu bảo mật |
| Dùng lại nonce GCM với cùng khóa | Tham số + quản lý khóa | Có thể phá bảo mật nghiêm trọng; quản lý nonce unique, giới hạn dùng khóa |
| Dùng SHA-256 thuần để “ký” dữ liệu | Thuật toán | Đối thủ sửa cả dữ liệu và hash; dùng MAC/chữ ký theo mô hình tin cậy |
| Verify chữ ký với public key do request tự gửi | Danh tính + trust | Xác minh chữ ký của kẻ tấn công; tra khóa từ nguồn tin cậy và kiểm tra quyền |
| Tắt hostname verification/TrustManager luôn tin | Danh tính + giao thức | TLS mã hóa tới sai đối tác; bật xác minh chain và endpoint identity |
| Chỉ kiểm tra tag AEAD, không kiểm tra messageId | Giao thức/trạng thái | Replay hợp lệ; thêm freshness, sequence và idempotency |
| Tin `keyId` hoặc `alg` do client cung cấp không hạn chế | Giao thức + cấu hình | Chọn nhầm khóa/thuật toán hoặc algorithm confusion; áp allowlist và binding |
| Dùng chung một khóa cho nhiều mục đích không cách ly | Khóa + KDF | Tăng phạm vi sự cố và nhầm ngữ cảnh; tách khóa/ngữ cảnh, kiểm soát usage |
| Xác thực user thành công nhưng không kiểm tra quyền tài nguyên | Danh tính + phân quyền | Truy cập chéo tenant/IDOR; kiểm tra authorization từng yêu cầu |
| Chấp nhận plaintext dù xác thực thất bại | Giao thức + xử lý lỗi | Đưa dữ liệu chưa đáng tin vào nghiệp vụ; fail closed trước khi xử lý |
| Ký JSON nhưng mỗi bên serialize khác nhau | Giao thức + encoding | Verify thất bại hoặc tạo ambiguity; chuẩn hóa byte representation, version |

Các lỗi này lý giải vì sao **chỉ thay AES-128 bằng AES-256** không thể tự sửa thiết kế thiếu xác thực, sai nonce hay sai danh tính.

## 11. Quy trình thiết kế và tự kiểm tra

Khi đọc hoặc xây một tính năng bảo mật, lần lượt trả lời:

1. **Mục tiêu:** cần confidentiality, integrity, peer authentication, freshness, authorization và audit đến mức nào? Đối thủ có thể đọc/chặn/sửa/phát lại thông điệp hoặc kiểm soát client không?
2. **Định danh:** ai được phép là sender/receiver? Expected identity lấy từ cấu hình đáng tin hay dữ liệu client tự khai? Cơ chế trust anchor/key registry là gì?
3. **Khóa:** sinh/trao đổi/dẫn xuất tại đâu, ai có quyền dùng, scope nào, cách lưu và rotate/revoke? Có tách khóa theo mục đích và session không?
4. **Thuật toán và tham số:** chọn primitive cho đúng mục tiêu; mode, hash, nonce, tag, salt, curve, KDF context và size được ràng buộc/kiểm tra ra sao?
5. **Giao thức:** định nghĩa version, role, canonical encoding, trình tự handshake/bản tin, AAD, signature scope, timeout, failure handling, chống downgrade và replay.
6. **Ủy quyền:** sau khi chứng minh danh tính, có kiểm tra tenant/resource/operation được phép tại đúng thời điểm không?
7. **Vận hành:** có theo dõi lỗi xác thực, vòng đời khóa, số lần dùng nonce, thu hồi danh tính, tương thích phiên bản và migration mà không làm giảm kiểm tra an ninh không?
8. **Kiểm thử:** thử sai khóa, sai nonce, sai tag, sai AAD, sai public key, certificate sai hostname/CA, message sửa đổi, replay, downgrade, chéo tenant và người dùng thiếu quyền. Các trường hợp phải bị từ chối theo chính sách.

### Câu hỏi kiểm tra nhanh

**Câu 1.** Alice và Bob chia sẻ cùng khóa AES-GCM. Bob giải mã thành công và kiểm tra tag đúng. Bob có bằng chứng mật mã rằng *chính Alice*, chứ không phải người khác cũng nắm khóa, đã gửi không?

**Trả lời:** Không. Tag cho biết bản tin phù hợp với khóa đối xứng và tham số đã kiểm tra. Nếu nhiều bên giữ khóa, không phân biệt duy nhất ai đã tạo bản tin; cần cơ chế nhận dạng và ràng buộc bổ sung.

**Câu 2.** Chữ ký Ed25519 `verify=true` với public key do request cung cấp có đủ để xác nhận “request của quản trị viên” không?

**Trả lời:** Không. Phải biết public key đó được gắn với quản trị viên bởi nguồn tin cậy nào và còn hiệu lực/quyền ký hay không; nếu không, đối thủ có thể tự tạo khóa và chữ ký.

**Câu 3.** Nonce và AAD đều có thể gửi công khai. Vì sao cần chúng?

**Trả lời:** Nonce cho phép tạo lần mã hóa khác nhau và, với GCM, không được lặp cặp khóa–nonce; AAD ràng buộc dữ liệu ngữ cảnh vào phép kiểm tra tag mà không mã hóa chúng. Cả hai là tham số an ninh, không cần giữ bí mật.

**Câu 4.** Kênh TLS đã bảo vệ request; có còn cần phân quyền và idempotency không?

**Trả lời:** Có. TLS bảo vệ kênh và xác thực endpoint theo chính sách TLS; quyền trên đơn hàng và xử lý request lặp thuộc giao thức/nghiệp vụ ứng dụng.

**Câu 5.** Khi rotate certificate TLS, khóa AES mã hóa dữ liệu lịch sử có bắt buộc thay theo không?

**Trả lời:** Không nhất thiết. Khóa xác thực TLS, traffic keys và khóa dữ liệu lưu trữ có mục đích/vòng đời khác nhau. Việc rotate được quyết định theo chính sách, mức độ sự cố và phạm vi sử dụng của từng khóa.

## 12. Tóm tắt mối quan hệ

**Thuật toán** xác định phép toán mật mã; **khóa** cấp năng lực mật mã cho bên nắm giữ; **tham số** xác định cách phép toán được dùng cho từng thông điệp; **giao thức** định nghĩa các bước tạo, thương lượng, kiểm tra và xử lý kết quả; **danh tính** gắn kết khóa và phiên với thực thể được tin cậy, còn **authorization** quyết định thực thể ấy được làm gì.

Một kết quả mật mã chỉ có ý nghĩa bảo mật trong **ngữ cảnh đã được kiểm tra**. Câu hỏi quan trọng nhất không phải “chúng ta đang dùng thuật toán nào?” mà là: **“Đúng khóa của ai, với tham số nào, trong giao thức nào, để chứng minh điều gì cho đúng đối tác và đúng hành động?”**

### Tham khảo chính thức

- RFC 8446 — TLS 1.3: https://www.rfc-editor.org/rfc/rfc8446
- RFC 5869 — HKDF: https://www.rfc-editor.org/rfc/rfc5869
- NIST SP 800-38D — GCM/GMAC và yêu cầu IV: https://csrc.nist.gov/pubs/sp/800/38/d/final
- Java Cryptography Architecture (JCA), Java SE 17: https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-fundamentals-006">1.6 Bảo vệ dữ liệu khi truyền, khi lưu trữ và trong bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# 1.6 Bảo vệ dữ liệu khi truyền, khi lưu trữ và trong bộ nhớ

## Mục tiêu học tập

Sau bài này, bạn có thể phân biệt ba trạng thái của dữ liệu (đang truyền, đang lưu trữ, đang được xử lý trong bộ nhớ), chỉ ra ranh giới bảo vệ của từng biện pháp, chọn cơ chế bảo vệ phù hợp trong ứng dụng Java và nhận diện những lỗi triển khai thường khiến dữ liệu nhạy cảm bị lộ dù hệ thống đã “bật mã hóa”.

## 1. Nhìn từ vòng đời dữ liệu và mô hình đe dọa

Một giá trị nhạy cảm như thông tin cá nhân, token, khóa bí mật hoặc dữ liệu thanh toán thường đi qua nhiều trạng thái:

1. **Data in transit (đang truyền):** dữ liệu di chuyển qua mạng, ví dụ trình duyệt → API, API → cơ sở dữ liệu, ứng dụng → message broker hoặc dịch vụ nội bộ.
2. **Data at rest (đang lưu trữ):** dữ liệu nằm trên ổ đĩa, bảng cơ sở dữ liệu, object storage, cache có lưu bền, bản sao lưu, snapshot hoặc tệp log.
3. **Data in use (đang xử lý trong bộ nhớ):** dữ liệu đã được giải mã/đọc vào Java heap, bộ nhớ native, buffer, cache trong tiến trình hoặc thanh ghi CPU để ứng dụng thực hiện nghiệp vụ.

| Trạng thái | Rủi ro điển hình | Kiểm soát cốt lõi | Giới hạn cần nhớ |
| --- | --- | --- | --- |
| Đang truyền | Nghe lén, giả mạo đầu cuối, sửa gói tin | TLS, xác thực chứng chỉ, mTLS khi cần, phân quyền API | TLS bảo vệ **đoạn kết nối** giữa hai đầu TLS; dữ liệu có thể được giải mã ở proxy/gateway |
| Đang lưu trữ | Lộ ổ đĩa, backup, snapshot, truy cập trái phép vào kho dữ liệu | Mã hóa ổ đĩa/kho lưu trữ; mã hóa cấp ứng dụng bằng AEAD; KMS; IAM | Tiến trình được cấp quyền đọc vẫn có thể lấy bản rõ; khóa đặt cạnh bản mã làm giảm giá trị bảo vệ |
| Trong bộ nhớ | Heap dump, debug trái phép, crash dump, ghi log, rò rỉ qua cache | Hạn chế bản sao và thời gian tồn tại; kiểm soát dump/log/debug; giảm quyền tiến trình | Java và GC không bảo đảm xóa sạch mọi bản sao của dữ liệu ngay lập tức |

**Mã hóa bảo vệ trước một nhóm mối đe dọa cụ thể; không thay thế xác thực, phân quyền, quản lý khóa, kiểm soát truy cập máy chủ hay giám sát.** Hãy hỏi: dữ liệu nào cần bảo vệ, ai có thể truy cập, điểm nào chuyển sang bản rõ, ai giữ khóa và bản sao nào còn tồn tại.

Ví dụ một luồng thực tế:

~~~text
Ứng dụng khách -- HTTPS/TLS --> API Java -- TLS/mTLS --> Cơ sở dữ liệu
                                    |
                               Java heap (bản rõ
                               khi xử lý nghiệp vụ)
                                    |
                              Lưu bản mã xuống DB
                              + backup mã hóa
                              + khóa trong KMS
~~~

Nếu TLS kết thúc tại load balancer, đường từ load balancer tới API là **một kết nối khác** và cần đánh giá bảo vệ riêng. Nếu DB lưu bản mã nhưng API phải đọc dữ liệu, API vẫn có lúc giữ bản rõ trong RAM.

## 2. Bảo vệ dữ liệu khi truyền: TLS và danh tính đầu cuối

### 2.1 TLS cung cấp điều gì?

**TLS (Transport Layer Security)** dùng để bảo vệ kết nối mạng bằng ba thuộc tính quan trọng:

- **Bí mật (confidentiality):** bên nghe lén đường truyền không đọc được nội dung ứng dụng.
- **Toàn vẹn (integrity):** thay đổi trái phép đối với dữ liệu được bảo vệ sẽ bị phát hiện.
- **Xác thực đầu cuối (authentication):** phía khách xác minh máy chủ đang kết nối qua chuỗi chứng chỉ và tên miền; mTLS bổ sung xác thực chứng chỉ phía khách nếu được cấu hình.

Ở mức khái niệm, TLS handshake thương lượng thuật toán, xác thực và thiết lập khóa phiên; sau đó dữ liệu ứng dụng được bảo vệ bằng cơ chế mã hóa có xác thực. Các phiên TLS hiện đại dùng trao đổi khóa tạm thời để đạt **forward secrecy**: rò rỉ khóa riêng của máy chủ về sau không tự động giải mã được toàn bộ lưu lượng cũ đã ghi lại, với điều kiện khóa phiên tạm thời không bị lộ.

Ưu tiên **TLS 1.3** khi các hệ thống hỗ trợ và duy trì **TLS 1.2 cấu hình an toàn** nếu cần tương thích. Không sử dụng SSL cũ, TLS 1.0/1.1 hay bộ mã yếu. Việc “dùng HTTPS” chỉ là điểm khởi đầu; cấu hình chứng chỉ, phiên bản giao thức và điểm kết thúc TLS vẫn quyết định mức an toàn thực tế.

### 2.2 Chứng chỉ, truststore, keystore và hostname verification

- **Certificate (chứng chỉ X.509):** liên kết khóa công khai với danh tính của máy chủ hoặc khách; phải kiểm tra chuỗi tin cậy, thời hạn và danh tính phù hợp.
- **Truststore:** tập CA/chứng chỉ mà chương trình tin để xác minh đối tác. Java thường sử dụng truststore mặc định của JVM nếu ứng dụng không cấu hình riêng.
- **Keystore:** nơi lưu khóa riêng cùng chứng chỉ của bên cần tự chứng minh danh tính; ví dụ máy chủ TLS hoặc client có chứng chỉ mTLS.
- **Hostname verification:** tên miền trong URL phải phù hợp với danh tính trong chứng chỉ (thường ở trường Subject Alternative Name). Chỉ tin CA mà bỏ kiểm tra tên miền là chưa đủ.

**mTLS** yêu cầu cả hai phía dùng chứng chỉ để xác thực ở tầng TLS. Nó hữu ích giữa các dịch vụ, nhưng không tự thay thế kiểm tra quyền nghiệp vụ: một client được TLS xác thực vẫn chỉ được phép thực hiện những thao tác mà danh tính đó được cấp quyền.

Tuyệt đối tránh các mẫu “trust all certificates”, bỏ qua lỗi chứng chỉ hoặc vô hiệu hóa hostname verification để sửa lỗi kết nối. Nếu hệ thống dùng CA nội bộ, nhập CA phù hợp vào truststore và quản lý vòng đời chứng chỉ; không phá bỏ bước xác thực.

### 2.3 Ví dụ Java 17+: HTTPS bằng HttpClient chuẩn

Tệp **TlsClientDemo.java**:

~~~java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TlsClientDemo {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("https://example.com"))
                .GET()
                .build();

        HttpResponse<Void> response = client.send(
                request, HttpResponse.BodyHandlers.discarding());

        System.out.println("HTTP status: " + response.statusCode());
    }
}
~~~

Chạy từ thư mục chứa tệp:

~~~bash
javac TlsClientDemo.java
java TlsClientDemo
~~~

Ví dụ dùng HTTPS và cơ chế kiểm tra chứng chỉ mặc định của Java. Giá trị HTTP status phụ thuộc phản hồi thực tế của máy chủ; nếu mạng, DNS hoặc xác thực chứng chỉ có vấn đề, lệnh có thể báo lỗi thay vì in trạng thái. Đừng bắt ngoại lệ TLS rồi chuyển sang HTTP thường. Khi cần cấu hình CA riêng hoặc mTLS, cung cấp SSLContext với trust manager/key manager phù hợp, đồng thời duy trì kiểm tra danh tính máy chủ.

**Phạm vi TLS:** TLS không bảo vệ nội dung khỏi chính đầu cuối đã giải mã, admin có quyền truy cập tiến trình, log chứa payload, hoặc chặng mạng phía sau một proxy kết thúc TLS. Vì vậy mọi kết nối giữa API, DB, broker, cache và dịch vụ nội bộ đều phải được xét riêng; dữ liệu cực nhạy cảm có thể cần thêm mã hóa cấp ứng dụng.

## 3. Bảo vệ dữ liệu khi lưu trữ: chọn đúng lớp mã hóa

### 3.1 Các lớp bảo vệ bổ sung cho nhau

- **Mã hóa ổ đĩa/volume hoặc dịch vụ lưu trữ:** bảo vệ khi ổ đĩa, snapshot, thiết bị hay phương tiện sao lưu bị lấy đi; thường trong suốt với ứng dụng. Tuy nhiên tài khoản hoặc tiến trình hợp lệ có quyền đọc dữ liệu sau khi ổ đĩa được mở khóa.
- **Mã hóa ở tầng cơ sở dữ liệu:** ví dụ mã hóa file dữ liệu/log/backup; bảo vệ kho lưu trữ vật lý, song truy vấn bằng tài khoản có quyền vẫn có thể trả bản rõ tùy kiến trúc.
- **Mã hóa cấp ứng dụng/cấp trường:** ứng dụng mã hóa một số giá trị **trước khi** lưu. Cơ sở dữ liệu chỉ giữ bản mã; quyền giải mã và khóa phải được kiểm soát riêng. Cần thiết kế việc tìm kiếm, sắp xếp và lập chỉ mục cho dữ liệu đã mã hóa.
- **Phân quyền, audit và backup:** giới hạn quyền đọc/xuất dữ liệu; mã hóa tất cả bản sao, kiểm tra khôi phục và thiết lập thời gian lưu giữ. Không để log, export CSV hay môi trường kiểm thử trở thành bản sao bản rõ ngoài kiểm soát.

Không phải thông tin nào cũng nên mã hóa thuận nghịch. **Mật khẩu đăng nhập** cần được xử lý bằng hàm băm mật khẩu chậm, có salt riêng cho từng mật khẩu, chẳng hạn Argon2id, scrypt, bcrypt hoặc PBKDF2 với tham số phù hợp; không lưu mật khẩu dưới dạng AES có thể giải mã và không dùng SHA-256 đơn thuần để lưu mật khẩu. **Hash** dùng cho mục tiêu kiểm chứng/đối chiếu không đồng nghĩa với **encryption**; Base64 chỉ là mã hóa biểu diễn, không cung cấp bí mật.

### 3.2 AES-GCM: vừa giữ bí mật vừa phát hiện sửa đổi

Với dữ liệu nhạy cảm cần giải mã, ưu tiên thuật toán **AEAD (Authenticated Encryption with Associated Data)** như AES-GCM thay vì tự ghép mã hóa và mã kiểm tra toàn vẹn.

Các thành phần của một bản ghi AES-GCM:

- **Key:** khóa AES bí mật, ví dụ 256 bit, được tạo từ nguồn sinh ngẫu nhiên an toàn và quản lý bằng KMS/secret manager phù hợp.
- **Nonce/IV:** thường dài 12 byte với GCM; **không được trùng khi dùng cùng một khóa**. Nonce không cần bí mật và có thể lưu kèm bản mã. Việc dùng ngẫu nhiên phải có giới hạn lưu lượng/chu kỳ xoay khóa để kiểm soát xác suất va chạm; hệ thống mã hóa số lượng rất lớn cần chiến lược cấp nonce duy nhất rõ ràng.
- **Ciphertext và authentication tag:** dữ liệu đã mã hóa cùng tag (thường 128 bit) cho phép phát hiện sai khóa, sai AAD hoặc sửa đổi bản mã. Trong JCA, Cipher.doFinal() của AES/GCM thường trả phần ciphertext kèm tag.
- **AAD (Additional Authenticated Data):** dữ liệu không cần bí mật nhưng phải gắn chặt với bản mã, ví dụ định danh bản ghi/tenant và phiên bản lược đồ. Bên giải mã phải cung cấp **đúng cùng AAD**. AAD không tự ngăn phát lại bản mã cũ; chống replay cần phiên bản/sequence và kiểm tra nghiệp vụ.
- **Version:** thông tin định dạng và phiên bản khóa (hoặc key ID) để hỗ trợ giải mã và thay đổi khóa về sau; không ghi khóa bí mật vào bản ghi.

**Không tái sử dụng nonce với cùng khóa AES-GCM**; điều đó có thể phá hỏng tính bí mật và toàn vẹn. Không dùng AES/ECB hoặc AES/CBC thiếu cơ chế xác thực cho dữ liệu có khả năng bị đối thủ sửa đổi. Không tự thiết kế thuật toán mã hóa.

### 3.3 Ví dụ Java 17+: mã hóa, giải mã và phát hiện bản mã bị sửa

Đây là chương trình độc lập minh họa một định dạng đơn giản: byte phiên bản + 12 byte nonce + ciphertext có tag. KeyGenerator tạo khóa mới **chỉ cho một lần chạy**; ứng dụng thực tế cần quản lý khóa bền vững và key ID riêng bằng KMS/HSM hoặc secret manager.

Lưu thành **AesGcmDemo.java**:

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class AesGcmDemo {
    private static final byte VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    static byte[] encrypt(byte[] plaintext, SecretKey key, byte[] aad)
            throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce); // Mới cho mỗi lần mã hóa cùng khóa.

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        byte[] ciphertextAndTag = cipher.doFinal(plaintext);

        return ByteBuffer.allocate(1 + NONCE_BYTES + ciphertextAndTag.length)
                .put(VERSION)
                .put(nonce)
                .put(ciphertextAndTag)
                .array();
    }

    static byte[] decrypt(byte[] payload, SecretKey key, byte[] aad)
            throws GeneralSecurityException {
        if (payload.length < 1 + NONCE_BYTES + TAG_BITS / 8) {
            throw new IllegalArgumentException("Payload khong hop le");
        }

        ByteBuffer input = ByteBuffer.wrap(payload);
        if (input.get() != VERSION) {
            throw new IllegalArgumentException("Version khong ho tro");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        input.get(nonce);
        byte[] ciphertextAndTag = new byte[input.remaining()];
        input.get(ciphertextAndTag);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertextAndTag);
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();

        byte[] aad = "record:42".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "Dữ liệu nhạy cảm"
                .getBytes(StandardCharsets.UTF_8);
        byte[] packed = encrypt(plaintext, key, aad);
        byte[] restored = decrypt(packed, key, aad);

        try {
            System.out.println("Khôi phục đúng: "
                    + Arrays.equals(plaintext, restored));

            packed[packed.length - 1] ^= 1; // Giả lập sửa một byte của tag.
            try {
                decrypt(packed, key, aad);
                System.out.println("Không phát hiện chỉnh sửa");
            } catch (AEADBadTagException expected) {
                System.out.println("Phát hiện chỉnh sửa: true");
            }
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            Arrays.fill(restored, (byte) 0);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac -encoding UTF-8 AesGcmDemo.java
java AesGcmDemo
~~~

Kết quả khi chạy với provider JCA AES-GCM thông dụng của JDK:

~~~text
Khôi phục đúng: true
Phát hiện chỉnh sửa: true
~~~

Diễn giải: dữ liệu mã hóa được khôi phục với khóa và AAD ban đầu. Khi sửa một byte tag, phép giải mã ném AEADBadTagException trước khi trả bản rõ. Trường hợp cung cấp sai AAD hoặc sai khóa cũng làm xác thực thất bại. Tag phát hiện chỉnh sửa **không chứng minh ai đã tạo bản mã** nếu nhiều chủ thể cùng giữ khóa và không tự chống replay.

**Giới hạn của ví dụ:** khóa chỉ sống trong RAM nên không thể giải mã dữ liệu từ lần chạy khác; việc dùng nonce ngẫu nhiên cần đánh giá quy mô trước khi đưa vào sản xuất; chương trình chưa giải quyết lưu trữ an toàn, xoay khóa, kiểm soát quyền, chống replay, xử lý lỗi và tránh bản sao dữ liệu trong bộ nhớ. Không in khóa, plaintext hay byte[] nhạy cảm ra console/log. Khi lưu bản ghi thật, quản lý version, key ID, thuật toán, nonce và ciphertext/tag theo định dạng được kiểm chứng và quy trình vận hành rõ ràng.

### 3.4 Quản lý khóa quan trọng không kém thuật toán

Một thiết kế thường dùng là **envelope encryption**:

1. KMS/HSM quản lý khóa gốc/khóa mã hóa khóa (KEK) theo quyền riêng.
2. Ứng dụng tạo hoặc yêu cầu **data encryption key (DEK)** để mã hóa dữ liệu.
3. Lưu ciphertext dữ liệu cùng DEK đã được bọc/mã hóa bằng KEK, key ID, nonce và metadata cần thiết; không lưu DEK bản rõ cạnh dữ liệu.
4. Khi cần đọc, thành phần có quyền yêu cầu KMS mở bọc DEK, giải mã trong thời gian tối thiểu rồi giải phóng bản sao kiểm soát được.

Áp dụng least privilege cho quyền sử dụng khóa; tách quyền đọc DB khỏi quyền giải mã nếu mô hình cho phép; quản lý audit, quota và thu hồi. **Rotation** cần tính cả dữ liệu cũ: đổi KEK, bọc lại DEK hoặc mã hóa lại dữ liệu theo kế hoạch. Phân biệt mất khóa (mất khả năng khôi phục dữ liệu) với lộ khóa (nguy cơ lộ dữ liệu). Backup khóa phải được bảo vệ và có quy trình phục hồi kiểm chứng.

Không hardcode khóa vào Java source, application.yml, Git, Docker image, biến môi trường được dump/log, URL hoặc file cấu hình phân phối rộng. Biến môi trường/secret injection có thể là kênh cấp phát, nhưng ứng dụng vẫn cần kiểm soát ai có quyền xem process configuration, dump và log.

## 4. Bảo vệ dữ liệu khi đang ở trong bộ nhớ JVM

Mã hóa đường truyền và ổ đĩa **không làm cho bản rõ tự biến mất trong bộ nhớ**: để xử lý nghiệp vụ, JVM thường phải có thời điểm nhìn thấy bản rõ hoặc giữ tham chiếu tới đối tượng chứa dữ liệu đó.

### 4.1 Những nơi dữ liệu có thể tồn tại

- **Java heap:** String, char[], byte[], DTO, collections, exception message, cache và object graph.
- **Bộ nhớ native/off-heap:** direct ByteBuffer, thư viện JNI/native, TLS provider, driver và các buffer I/O.
- **Bản sao ngoài ý muốn:** chuyển byte[] sang String, nối chuỗi, serialize JSON, tracing, log request/response, bộ nhớ của profiler.
- **Hiện vật chẩn đoán:** heap dump, core dump, crash dump, debugger/JMX/JFR hoặc bản sao do sự cố và công cụ vận hành tạo ra.

GC thu hồi đối tượng không đồng nghĩa với việc xóa mọi byte dữ liệu cũ trên RAM đúng thời điểm. **String là bất biến** nên ứng dụng không thể xóa nội dung String theo cách đáng tin; ngay cả khi xóa một mảng byte[]/char[] mà mình sở hữu, JVM, thư viện hoặc OS vẫn có thể giữ các bản sao khác. Bảo đảm zeroization tuyệt đối không phải là tính chất mặc định của Java.

### 4.2 Biện pháp thực tế

1. **Giảm dữ liệu và thời gian giữ bản rõ:** chỉ giải mã đúng trường cần xử lý, không giữ payload dài hạn trong session/cache và giải phóng tham chiếu khi xong. Không đọc toàn bộ hồ sơ nhạy cảm nếu nghiệp vụ chỉ cần một trường.
2. **Dùng vùng dữ liệu có thể xóa khi API cho phép:** với mật khẩu nhận từ terminal, Console.readPassword() trả char[]. Bọc sử dụng trong try/finally và gọi Arrays.fill(password, '\0') ở finally; không chuyển sang String không cần thiết. Với byte[] bản rõ do ứng dụng kiểm soát, dùng Arrays.fill(bytes, (byte) 0) sau sử dụng. Đây là giảm rủi ro, không phải cam kết xóa toàn bộ bản sao.
3. **Giảm số lần sao chép/serialize:** tránh toString() trên secret object, logging DTO, dump stack chứa tham số nhạy cảm; chỉ log mã định danh đã che hoặc metadata phi nhạy cảm.
4. **Bảo vệ công cụ vận hành:** hạn chế quyền attach debugger, JMX, heap dump, core dump; kiểm soát file dump và nơi lưu artifact CI/CD, support ticket, object storage. Với cờ tạo heap dump khi OOM, phải có quy trình bảo vệ/xóa dump vì nó có thể chứa plaintext và khóa.
5. **Hạn chế quyền runtime:** tài khoản OS/service riêng, container/host isolation, quyền truy cập secret và KMS tối thiểu, cập nhật thư viện/JDK và kiểm soát người có quyền truy cập môi trường.
6. **Thu hẹp quyền dùng khóa:** khóa có thể nằm trong RAM của ứng dụng khi cần mã hóa, nhưng HSM/KMS có thể giữ khóa gốc ngoài tiến trình; tránh lưu bản sao khóa bền vững trong singleton/cache khi không cần.

Ví dụ luồng nhập mật khẩu trong Java nên ưu tiên phương thức trả char[] như Console.readPassword(); luôn kiểm tra trường hợp không có console trong IDE hoặc khi chạy dưới service. Không dùng Arrays.fill cho một mảng sau khi đã để lộ nội dung qua String/log mà kỳ vọng các bản sao kia tự biến mất.

**Không có “bật một flag là mã hóa toàn bộ heap an toàn”.** Nếu đối thủ có quyền điều khiển tiến trình, attach debugger hoặc đọc bộ nhớ của service, phần lớn dữ liệu ứng dụng cần xử lý đều có nguy cơ bị lộ. Hãy xác định và giảm quyền truy cập đó theo mô hình đe dọa thực tế.

## 5. Ghép ba lớp bảo vệ trong kiến trúc Java

Giả sử một API Java nhận thông tin khách hàng, lưu trong DB và trả kết quả cho dịch vụ khác:

| Bước | Thiết kế đề xuất | Điều cần xác minh |
| --- | --- | --- |
| Client gọi API | HTTPS/TLS, xác thực user và kiểm tra quyền ở API | Chứng chỉ/tên miền hợp lệ; không có HTTP fallback; dữ liệu nhạy cảm không nằm trên URL |
| Gateway gọi ứng dụng | TLS riêng cho chặng gateway → service; mTLS nếu thích hợp | Điểm kết thúc TLS và danh tính hai phía |
| Service gọi DB/broker | TLS cho từng kết nối; tài khoản riêng, quyền tối thiểu | Không mặc nhiên tin mạng nội bộ; chứng chỉ và quyền truy cập đúng |
| Service lưu trường nhạy cảm | AES-GCM/AEAD cấp ứng dụng khi mô hình đe dọa yêu cầu; mã hóa disk, backup, snapshot | Nonce duy nhất theo khóa; AAD gắn đúng ngữ cảnh; không lộ key/DEK |
| Service dùng bản rõ | Giải mã sát nơi dùng, hạn chế cache và bản sao, che log/trace | Không để plaintext/khóa trong exception, heap dump, báo cáo lỗi |
| Vận hành và khôi phục | KMS/IAM/audit, rotation, backup/restore và xử lý sự cố | Có thể khôi phục dữ liệu hợp lệ, thu hồi quyền và phát hiện truy cập bất thường |

**Defense in depth:** TLS bảo vệ đường truyền; mã hóa at-rest bảo vệ bản sao lưu trữ; quản lý bộ nhớ và quyền runtime giảm khả năng lộ khi xử lý. Ba lớp phối hợp với xác thực, phân quyền, quản lý khóa và quy trình vận hành. Không một lớp nào tự bao phủ các lớp còn lại.

## 6. Những nhầm lẫn và lỗi phổ biến

| Nhầm lẫn | Điều đúng cần nhớ |
| --- | --- |
| “Có HTTPS thì dữ liệu an toàn ở mọi nơi.” | HTTPS bảo vệ các kết nối TLS được cấu hình; plaintext vẫn có thể ở đầu cuối, gateway, DB, cache và log. |
| “DB đã mã hóa ổ đĩa, nhân viên có quyền SQL cũng không đọc được dữ liệu.” | Mã hóa ổ đĩa thường trong suốt sau khi mở khóa; vẫn cần phân quyền và có thể cần mã hóa cấp trường. |
| “Dùng AES là đủ, không cần integrity.” | AES-GCM với tag/AAD cung cấp mã hóa có xác thực; AES/ECB và CBC không xác thực dễ bị lạm dụng. |
| “GCM nonce giống salt, tái sử dụng cũng được.” | Nonce **phải duy nhất theo từng khóa**; tái sử dụng với GCM có thể gây hậu quả nghiêm trọng. |
| “Base64 hoặc SHA-256 là mã hóa dữ liệu bí mật.” | Base64 chỉ đổi cách biểu diễn; hash không phải mã hóa thuận nghịch; mật khẩu cần password hashing chuyên dụng. |
| “Xóa char[] là chắc chắn không còn mật khẩu trong RAM.” | Chỉ xóa mảng đang quản lý; có thể còn bản sao trong JVM/native/OS. |
| “mTLS tự quyết định service nào được xem hồ sơ.” | mTLS xác thực danh tính kết nối; nghiệp vụ vẫn phải phân quyền từng tài nguyên/hành động. |
| “Khóa và bản mã nằm cùng DB vẫn bảo vệ tốt trước lộ DB.” | Nếu cùng quyền truy cập đọc được cả khóa lẫn dữ liệu, kẻ tấn công có thể giải mã; cần tách quyền quản lý khóa. |

## 7. Tự kiểm tra bằng tình huống

**Tình huống A:** API chạy HTTPS, DB kết nối không TLS trong cùng VPC, còn backup được xuất ra object storage. Hãy chỉ ra ba ranh giới cần đánh giá: đường API → DB, quyền đọc backup và trạng thái dữ liệu trong RAM của API. Bổ sung TLS cho chặng DB, mã hóa và phân quyền backup, rà soát log/dump và quyền tiến trình; HTTPS phía client chưa bao phủ các điểm này.

**Tình huống B:** một cột đã mã hóa bằng AES-GCM nhưng mỗi bản ghi dùng lại cùng IV cố định. Đây là lỗi thiết kế nghiêm trọng vì tái sử dụng nonce với cùng key. Phải thiết kế cơ chế tạo nonce duy nhất, quản lý key/rotation, đánh giá dữ liệu đã bị ảnh hưởng và mã hóa lại an toàn; không chỉ đổi tên biến hoặc thêm Base64.

**Tình huống C:** ứng dụng che mật khẩu trong giao diện nhưng ghi toàn bộ request body và lưu heap dump sau OOM. Dữ liệu vẫn có nguy cơ rò rỉ qua log/dump. Hãy bỏ secret khỏi log, giới hạn dữ liệu tồn tại trong JVM, bảo vệ/khoanh vùng artifact chẩn đoán và kiểm tra quyền truy cập.

**Bộ câu hỏi tự đánh giá:**

1. TLS bảo vệ đoạn nào của một kiến trúc có reverse proxy? → Từng đoạn kết nối TLS, theo vị trí điểm kết thúc.
2. Có thể lưu nonce AES-GCM cạnh ciphertext không? → Có; nonce không phải bí mật, nhưng bắt buộc duy nhất theo khóa.
3. Sai AAD ảnh hưởng gì khi giải mã bằng AES-GCM? → Xác thực tag thất bại, không được sử dụng bản rõ.
4. Tại sao mã hóa ổ đĩa chưa đủ cho một DB có nhiều tài khoản ứng dụng? → Tài khoản đã được cho phép truy vấn có thể đọc bản rõ; cần quyền chi tiết và đôi khi mã hóa cấp trường.
5. Vì sao Arrays.fill() là biện pháp giảm rủi ro chứ không phải bảo đảm tuyệt đối? → Nó chỉ ghi đè mảng được tham chiếu; bản sao do JVM/thư viện/OS tạo có thể còn tồn tại.

**Nguyên tắc mang đi:** xác định rõ dữ liệu ở đâu, ai có thể đọc, nó được giải mã tại điểm nào và khóa được bảo vệ thế nào. Dùng TLS cho từng chặng truyền, AEAD cùng quản lý khóa tốt cho dữ liệu cần mã hóa khi lưu, và giảm tối đa bản rõ cùng dấu vết nhạy cảm trong bộ nhớ, log và công cụ chẩn đoán.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-fundamentals-007">1.7 Bản đồ JCA, JCE, JSSE, JAAS, PKI và các công cụ của JDK</a>

<details>
<summary>Xem chi tiết</summary>

# 1.7. Bản đồ JCA, JCE, JSSE, JAAS, PKI và các công cụ của JDK

## Mục tiêu học tập

Sau bài này, bạn có thể (1) phân biệt các lớp công nghệ bảo mật của Java, (2) tìm đúng API khi cần băm, mã hóa, ký số, kết nối TLS hoặc xác thực người dùng, (3) hiểu vì sao `KeyStore`, certificate và truststore xuất hiện ở nhiều lớp, và (4) dùng `keytool`, `jarsigner` cùng cơ chế debug của JDK để kiểm tra vấn đề thực tế. Đây là **bản đồ kiến trúc và cách chọn công cụ**, không phải hướng dẫn tự thiết kế một giao thức mật mã.

## 1. Nhìn toàn cảnh: mỗi tên trả lời câu hỏi nào?

| Thành phần | Vai trò chính | API/khái niệm đại diện | Ví dụ tình huống |
| --- | --- | --- | --- |
| **JCA** – Java Cryptography Architecture | Kiến trúc API và **provider** cho các dịch vụ mật mã, khóa, chứng thư và kiểm tra chứng thư | `MessageDigest`, `Signature`, `SecureRandom`, `KeyPairGenerator`, `KeyStore`, `CertificateFactory` | Băm SHA-256, ký và kiểm chữ ký Ed25519, đọc chứng thư |
| **JCE** – Java Cryptography Extension | Tên lịch sử cho phần API mật mã như mã hóa, MAC, khóa bí mật; **ngày nay đã tích hợp trong JCA/JDK**, không phải hệ thống provider cạnh tranh với JCA | `javax.crypto.Cipher`, `Mac`, `KeyGenerator`, `SecretKey`, `GCMParameterSpec` | Mã hóa AES-GCM, tạo HMAC |
| **JSSE** – Java Secure Socket Extension | Cung cấp giao tiếp mạng bảo mật bằng TLS/DTLS, dùng primitive/provider và thông tin tin cậy bên dưới | `SSLContext`, `SSLSocket`, `SSLEngine`, `KeyManager`, `TrustManager` | Client HTTPS, server TLS, mTLS |
| **JAAS** – Java Authentication and Authorization Service | Mô hình **xác thực danh tính có thể cắm module** và `Subject`/`Principal` đại diện người dùng hoặc dịch vụ | `LoginContext`, `LoginModule`, `CallbackHandler`, `Subject` | Đăng nhập Kerberos hoặc một cơ chế xác thực tích hợp JAAS |
| **PKI** – Public Key Infrastructure | Hạ tầng tin cậy quanh khóa công khai, X.509, CA và chuỗi chứng thư; **không phải một thư viện độc lập duy nhất** | `X509Certificate`, `CertPath`, `CertPathBuilder`, `CertPathValidator`, `TrustAnchor` | Quyết định có tin chứng thư server hay không |
| **Công cụ JDK** | Tạo/kiểm tra khóa, certificate, CSR, chữ ký JAR và chẩn đoán bảo mật | `keytool`, `jarsigner`, `jar`, `java -D...debug=...` | Chuẩn bị keystore cho TLS, kiểm JAR ký số, tìm lỗi handshake |

**Điểm nối quan trọng:** JCE nằm trong hệ sinh thái JCA; JSSE sử dụng các dịch vụ/provider mật mã và thường kiểm chứng X.509 theo PKI; JAAS xử lý xác thực danh tính ở một tầng khác. Một kết nối HTTPS thành công **không tự động** nghĩa là người dùng đã đăng nhập JAAS.

```text
Ứng dụng Java
├─ Bảo vệ dữ liệu/nghiệp vụ
│  └─ JCA + JCE → provider → hash, MAC, mã hóa, khóa, chữ ký
├─ Giao tiếp mạng
│  └─ JSSE (TLS/DTLS) → KeyManager / TrustManager
│                       └─ PKI/X.509 + các dịch vụ JCA
├─ Đăng nhập/xác thực danh tính theo JAAS
│  └─ LoginContext → LoginModule → Subject/Principal
└─ Vận hành
   └─ keytool / jarsigner / security properties / TLS debug
```

Cùng một `KeyStore` có thể được ứng dụng JCA dùng để đọc khóa ký dữ liệu, hoặc JSSE dùng làm nguồn khóa riêng/chứng thư TLS. **Vai trò sử dụng** mới quyết định nó là kho danh tính hay kho tin cậy, không chỉ phần mở rộng tệp.

## 2. JCA: API engine tách khỏi triển khai provider

JCA định nghĩa các lớp *engine* với mô hình `getInstance(algorithm)`. Ứng dụng yêu cầu dịch vụ bằng tên thuật toán; JDK tra các **security provider** được đăng ký để tìm triển khai phù hợp. Điều này cho phép bổ sung provider phần mềm, HSM/PKCS#11 hoặc provider chuyên biệt mà không viết lại toàn bộ code sử dụng engine.

Ví dụ:

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] sha256 = digest.digest(data);

Signature verifier = Signature.getInstance("Ed25519");
// verifier.initVerify(publicKey); verifier.update(data); verifier.verify(signature);
```

Đây là **đoạn minh họa**, `data`/`publicKey`/`signature` phải được cung cấp; chương trình hoàn chỉnh nằm ở mục 8. `MessageDigest` chỉ băm dữ liệu, còn `Signature` dùng khóa bất đối xứng để chứng thực chữ ký.

Quy trình chọn provider:

1. Ứng dụng gọi `Cipher.getInstance("AES/GCM/NoPadding")` hoặc `Signature.getInstance("Ed25519")`.
2. JCA xét provider theo cấu hình/thứ tự ưu tiên và khả năng hỗ trợ dịch vụ; có thể chỉ định provider khi thật sự cần.
3. Engine trả về đối tượng triển khai; ứng dụng khởi tạo bằng khóa và tham số rồi mới xử lý dữ liệu.
4. Có thể truy vấn provider phục vụ đối tượng qua `getProvider()` để chẩn đoán.

Các nhóm engine nên nhận diện:

| Nhiệm vụ | Chọn API | Không nên nhầm với |
| --- | --- | --- |
| Băm không khóa | `MessageDigest` | HMAC/chữ ký: riêng hash **không xác thực người gửi** |
| Mã xác thực dùng bí mật chung | `Mac` | Chữ ký số: HMAC không cho bên thứ ba xác minh bằng public key |
| Ký/kiểm chữ ký bất đối xứng | `Signature` | Mã hóa: chữ ký không che giấu plaintext |
| Mã hóa/giải mã | `Cipher` | Hash: mã hóa có thể giải mã bằng khóa thích hợp |
| Sinh ngẫu nhiên an toàn | `SecureRandom` | `Random` cho mô phỏng/game |
| Sinh khóa đối xứng/bất đối xứng | `KeyGenerator` / `KeyPairGenerator` | `KeyFactory` biến đổi biểu diễn khóa đã có |
| Lưu/truy cập khóa/chứng thư | `KeyStore` | Certificate không chứa private key của chủ thể |
| Xây/kiểm chuỗi chứng thư | `CertPathBuilder` / `CertPathValidator` | Chỉ đọc certificate bằng `CertificateFactory` |

Đừng xem tên thuật toán là bảo đảm toàn bộ thiết kế an toàn: chế độ mã hóa, IV/nonce, độ dài khóa, vòng đời khóa, thuật toán bị vô hiệu, kích thước dữ liệu và cấu hình provider vẫn quan trọng. Đặc biệt không dựa vào mặc định mơ hồ như `Cipher.getInstance("AES")`; hãy yêu cầu transformation đầy đủ.

Có thể tự quan sát provider của JVM:

```java
import java.security.Provider;
import java.security.Security;

public class ProvidersDemo {
    public static void main(String[] args) {
        for (Provider p : Security.getProviders()) {
            System.out.println(p.getName() + " " + p.getVersionStr());
        }
    }
}
```

Chạy bằng `javac ProvidersDemo.java` rồi `java ProvidersDemo`. **Danh sách và phiên bản provider phụ thuộc bản JDK/nhà cung cấp/hệ điều hành**, nên không có stdout cố định. Đừng hard-code tên `SunJCE` hay `SunJSSE` nếu ứng dụng cần chạy trên nhiều JVM/provider, trừ khi có yêu cầu triển khai rõ ràng.

## 3. JCE: mật mã ứng dụng trong cùng kiến trúc JCA

Tên **JCE** xuất phát từ thời các tính năng mã hóa được phát hành như phần mở rộng. Trong Java hiện đại, `javax.crypto` là một phần bình thường của JDK và vận hành qua cơ chế provider của JCA. Không cần “cài JCE riêng” cho Java 17+ chuẩn để dùng AES/HMAC thông thường.

Các lựa chọn thực dụng:

- **Bảo mật bí mật dữ liệu lưu trữ**: thường dùng mã hóa có xác thực (*AEAD*) như `AES/GCM/NoPadding`. Dữ liệu giải mã phải được xác minh tag; bản mã chỉ được chấp nhận khi xác thực thành công.
- **Bảo đảm toàn vẹn và xác thực giữa các bên giữ cùng khóa bí mật**: dùng `HmacSHA256` (API `Mac`); người không biết khóa không tạo được MAC hợp lệ.
- **Chứng minh sở hữu private key cho dữ liệu**: dùng `Signature` với cặp khóa; người xác minh cần public key **và ngữ cảnh tin cậy** nếu muốn gắn chữ ký với một danh tính.
- **Lưu mật khẩu người dùng**: không dùng SHA-256/HMAC đơn thuần như password hash. Dùng cơ chế dẫn xuất mật khẩu chậm, có salt riêng và tham số chi phí phù hợp (ví dụ PBKDF2 qua `SecretKeyFactory` khi phù hợp chính sách; hệ thống có thể dùng giải pháp Argon2/bcrypt được kiểm chứng).

Với AES-GCM, hãy ghi nhớ bốn thành phần: **khóa bí mật**, **nonce/IV duy nhất cho mỗi lần mã hóa với cùng khóa** (thường 12 byte), **ciphertext** và **authentication tag** (thường 128 bit). Có thể thêm **AAD** (authenticated associated data): AAD được xác thực nhưng không được mã hóa. Bên giải mã cần cùng khóa, IV, AAD tương ứng và ciphertext kèm tag. Tuyệt đối không tái sử dụng cặp khóa–nonce trong GCM; không tự tạo nonce bằng `Random` hay hằng số.

Dùng `SecureRandom` cho vật liệu ngẫu nhiên an toàn và `KeyGenerator` cho khóa. Phân phối, lưu, thay mới và thu hồi khóa phải được thiết kế riêng; API mã hóa không tự giải quyết vấn đề quản lý khóa. Không ghi private key, secret key, token, plaintext nhạy cảm hoặc mật khẩu keystore vào log.

## 4. JSSE: dùng TLS cho dữ liệu trên đường truyền

JSSE nằm trong `javax.net.ssl`. Nó cung cấp TLS/DTLS qua `SSLContext`, `SSLSocket`/`SSLServerSocket`, `SSLEngine` và các factory. Các API cao hơn như `java.net.http.HttpClient` dùng TLS để gọi HTTPS mà phần lớn ứng dụng không phải tự quản lý từng bước handshake.

Các vai trò thường gặp:

| Thành phần | Trách nhiệm |
| --- | --- |
| `SSLContext` | Cấu hình/ngữ cảnh TLS dùng để tạo socket/engine, mang thông tin key/trust manager và trạng thái phiên |
| `KeyManager` | Chọn **private key + chuỗi certificate của chính mình** khi cần chứng minh danh tính với phía đối diện |
| `TrustManager` | Đánh giá **certificate của phía đối diện** theo tập chứng thư/anchor tin cậy và chính sách |
| `SSLEngine` | Xử lý TLS độc lập với cách ứng dụng thực hiện I/O; phù hợp mô hình nonblocking |
| `SSLSocket` | Socket có TLS để truyền dữ liệu qua kênh đã bắt tay bảo mật |

Luồng HTTPS thông thường (mô hình đơn giản hóa):

1. Client kết nối server và trao đổi tham số TLS; server xuất trình chứng thư X.509 cùng chuỗi cần thiết.
2. Client kiểm tra chuỗi theo các trust anchor được chấp nhận, thời hạn, ràng buộc và chính sách thuật toán; client HTTPS cũng phải kiểm tra **tên host** khớp danh tính chứng thư (thường ở SAN).
3. Handshake thiết lập khóa phiên; dữ liệu ứng dụng sau đó được mã hóa và xác thực trong kênh TLS.
4. Với **mTLS**, server còn yêu cầu certificate của client, và client chọn khóa/chứng thư tương ứng để xác thực hai chiều. Điều này không tự thay thế mọi yêu cầu phân quyền nghiệp vụ.

Trong ứng dụng HTTPS bình thường, ưu tiên để `HttpClient` hoặc thư viện HTTP có TLS chuẩn thực hiện xác minh certificate và hostname. Không tắt kiểm tra tên host, không dùng `TrustManager` “tin tất cả” để chữa lỗi. Với `SSLSocket` tự tạo, **không nên mặc định rằng mọi xác minh tên endpoint đều tự được bật**: cần cấu hình endpoint identification phù hợp, ví dụ `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` khi giao thức mục tiêu đúng là HTTPS.

Đoạn sử dụng thư viện HTTPS cấp cao (cần mạng; là đoạn minh họa, không có stdout cố định):

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder(
        URI.create("https://example.com/")).GET().build();
HttpResponse<Void> response =
        client.send(request, HttpResponse.BodyHandlers.discarding());
System.out.println(response.statusCode());
```

Các dòng trên cần đặt trong method có xử lý exception hoặc `throws Exception`. **JSSE không “là PKI”**: TLS là giao thức truyền thông, còn PKI là cơ sở để xác lập tin cậy cho khóa công khai/chứng thư dùng trong nhiều giao thức.

## 5. JAAS: xác thực Subject, không thay thế TLS hay mã hóa

JAAS dùng kiến trúc xác thực có thể cấu hình. Một ứng dụng gọi `LoginContext` theo tên cấu hình; cấu hình xác định các `LoginModule`; module có thể lấy thông tin qua `CallbackHandler`, xác thực bằng cơ chế thực tế (ví dụ Kerberos), rồi gắn các `Principal`/credential vào một `Subject` khi thành công.

```text
Ứng dụng → LoginContext("MyApp")
         → Configuration → các LoginModule
         → CallbackHandler (nếu cần nhập/chứng thực thông tin)
         → login thành công → Subject { Principal, credentials }
         → logic nghiệp vụ quyết định quyền truy cập
```

Đoạn API ở mức khái niệm:

```java
LoginContext context = new LoginContext("MyApp", handler);
context.login();
Subject authenticated = context.getSubject();
// Dùng principal/credential theo chính sách ứng dụng.
context.logout();
```

Đây **không phải chương trình chạy độc lập**: cần cấu hình login và `handler` phù hợp, cùng nhập `javax.security.auth.Subject` và `javax.security.auth.login.LoginContext`. Khi tự triển khai `LoginModule`, cần hiểu vòng đời `initialize`, `login`, `commit`, `abort` và `logout`; đừng tự tạo thành công giả chỉ vì có `Principal`.

Điều cần phân biệt:

- **TLS server authentication**: phía client biết kênh đang kết nối đến server nào theo certificate/hostname; đây là việc của JSSE/PKI.
- **JAAS authentication**: hệ thống xác thực người dùng hoặc một thực thể theo module đăng nhập; `Subject` biểu diễn thực thể đã xác thực.
- **Authorization**: ứng dụng quyết định principal đó được phép làm gì. JAAS cung cấp mô hình danh tính/credential để hỗ trợ quyết định này, nhưng không tự sinh toàn bộ chính sách phân quyền API, role nghiệp vụ, OAuth2/OIDC hay JWT.
- **Kerberos/JGSS**: có thể liên quan JAAS trong ứng dụng tích hợp Kerberos; không phải cứ dùng JSSE thì cần JAAS.

Trên JDK hiện đại, tránh hiểu “authorization của JAAS” là phải dựa vào SecurityManager cũ. Các ứng dụng nên xác định cơ chế phân quyền thực tế của framework và hệ thống đang dùng.

## 6. PKI/X.509: từ public key đến danh tính được tin cậy

**Cặp khóa bất đối xứng** gồm public key có thể chia sẻ và private key phải giữ bí mật. Một **chứng thư X.509** gắn public key với subject, issuer, serial, thời hạn hiệu lực, chữ ký của bên cấp và các extension. Chứng thư **không chứa private key** của subject.

Mô hình chuỗi thường gặp:

```text
Root CA (trust anchor đã được cấu hình tin cậy)
    ↓ ký/ủy quyền
Intermediate CA
    ↓ ký
Server leaf certificate (SAN: api.example.com)
    + private key tương ứng (chỉ server giữ)
```

Client không chỉ kiểm “certificate có chữ ký hợp lệ”; nó cần một đường chứng thực có thể kiểm tra đến **trust anchor được chấp nhận**. Trong PKIX, còn cần xét ràng buộc cơ bản của CA, key usage/extended key usage, hiệu lực thời gian, chính sách thuật toán, và kiểm tra thu hồi (CRL/OCSP) theo cấu hình. **Kiểm tra hostname là yêu cầu nhận diện endpoint của TLS/HTTPS, không được suy ra chỉ từ việc path validation thành công.**

Các API liên quan:

- `CertificateFactory.getInstance("X.509")`: đọc/biến đổi chứng thư và chuỗi từ dữ liệu mã hóa.
- `X509Certificate`: xem subject, issuer, SAN, validity, extension và public key.
- `CertPathBuilder.getInstance("PKIX")`: tìm đường chứng thực từ leaf theo dữ liệu/chính sách đầu vào.
- `CertPathValidator.getInstance("PKIX")`: kiểm đường chứng thực dựa trên `PKIXParameters` và `TrustAnchor`.
- `CertStore`: nguồn ứng viên certificate/CRL, không tự động là danh sách root được tin cậy.
- `PKIXRevocationChecker`: cấu hình kiểm tra thu hồi khi cần cơ chế phù hợp.

Những phân biệt hay sai:

| Khái niệm | Ý nghĩa thực tế |
| --- | --- |
| **CSR** – Certificate Signing Request | Yêu cầu cấp certificate; thường chứa public key và thông tin nhận diện, được ký bằng private key tương ứng; **CSR chưa phải chứng thư được CA tin cậy cấp** |
| **Self-signed certificate** | Tự ký bằng private key tương ứng; phù hợp thử nghiệm/CA riêng có kiểm soát, không tự nhiên được client bên ngoài tin |
| **Root CA/trust anchor** | Điểm tin cậy do người quản trị, hệ điều hành hoặc runtime chọn; có ý nghĩa vì được **cấu hình tin**, không vì xuất hiện trong chuỗi |
| **SAN** – Subject Alternative Name | Chứa danh tính DNS/IP dùng để đối chiếu endpoint hiện đại; đặt `CN=localhost` riêng thường không đủ |
| **Certificate chain** | Các chứng thư hỗ trợ chứng minh đường cấp phát; thiếu intermediate hoặc sai mục đích sử dụng có thể gây lỗi |
| **PEM, DER, PKCS#12** | Định dạng/bao gói dữ liệu; PEM thường là base64 có header, DER là nhị phân ASN.1, PKCS#12 là container có thể chứa key/certificate |

Đừng nhập certificate nhận được từ một server lạ vào truststore chỉ để hết lỗi. Làm vậy thay đổi trust anchor và có thể bỏ qua vấn đề thật: sai hostname, thiếu intermediate, chứng thư hết hạn hoặc CA không được tổ chức chấp thuận.

## 7. KeyStore, truststore và bộ công cụ JDK

`KeyStore` là API lưu/truy cập entry theo **alias**. Trong ứng dụng Java thường gặp:

- **Keystore danh tính (identity store)**: entry có **private key + certificate chain** của ứng dụng. JSSE `KeyManager` có thể đọc để chọn danh tính trình bày cho server/client.
- **Truststore**: chứa certificate được chọn làm điểm tin cậy; `TrustManager` dùng khi xác minh peer. Hai loại có thể cùng cấu trúc `KeyStore`, nhưng mục đích bảo mật khác nhau.
- **PKCS12 (`.p12`/`.pfx`)**: định dạng keystore mặc định điển hình từ JDK 9, dùng phổ biến để trao đổi key/certificate. `JKS` là định dạng Java cũ; `PKCS11` có thể trỏ tới token/HSM. Phần mở rộng tệp không tự bảo đảm dữ liệu bên trong đã được bảo vệ đúng.
- **`cacerts`**: kho CA được JDK cung cấp để xây dựng trust mặc định (các hệ thống/nhà cung cấp JVM có thể cấu hình khác). Thay đổi `cacerts` trên máy chung có ảnh hưởng rộng; ưu tiên truststore ứng dụng khi cần thiết.

### Bài thực hành local: tạo danh tính TLS tự ký và kho tin cậy riêng

Các lệnh dưới đây là **lệnh đơn dòng**, chạy trong thư mục thử nghiệm trên JDK có `keytool`. Chúng sẽ hỏi mật khẩu qua terminal; không đưa mật khẩu thật vào tham số dòng lệnh hay repository.

**Bước 1 – tạo cặp khóa RSA và certificate tự ký cho localhost:**

```shell
keytool -genkeypair -alias local-server -keyalg RSA -keysize 3072 -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1" -validity 30 -storetype PKCS12 -keystore server.p12
```

`server.p12` có private key và certificate gắn alias `local-server`. `-ext SAN=...` giúp mô hình hóa danh tính localhost đúng cho thử nghiệm TLS.

**Bước 2 – kiểm entry và xuất chỉ phần certificate công khai:**

```shell
keytool -list -v -keystore server.p12
keytool -exportcert -rfc -alias local-server -keystore server.p12 -file server.crt
keytool -printcert -v -file server.crt
```

`-exportcert` không xuất private key. Kiểm SAN, issuer = subject trong trường hợp tự ký, hiệu lực và fingerprint. Fingerprint chỉ có ý nghĩa khi được đối chiếu qua kênh tin cậy.

**Bước 3 – tạo truststore chỉ tin certificate local đó:**

```shell
keytool -importcert -alias local-server -file server.crt -keystore client-trust.p12 -storetype PKCS12
keytool -list -v -keystore client-trust.p12
```

Khi công cụ hỏi có tin certificate không, **chỉ xác nhận trong thí nghiệm bạn vừa tự tạo/đối chiếu fingerprint**. `client-trust.p12` chứa chứng thư được tin, không có private key của server. Bài này minh họa một trust anchor local; production nên dùng CA được tổ chức chấp thuận và quản trị quy trình cấp phát, gia hạn, thu hồi.

**Bước 4 – hình dung quy trình CA thật:** `keytool -certreq -alias local-server -keystore server.p12 -file server.csr` tạo CSR cho cặp khóa đang có. CA kiểm soát danh tính và cấp chứng thư; sau đó cần nhập chuỗi CA/certificate reply vào đúng entry có private key. Không nhầm việc tạo CSR với việc hệ thống đã được tin cậy.

**Các công cụ đáng nhớ khác:**

| Công cụ/lệnh | Khi dùng | Giới hạn cần nhớ |
| --- | --- | --- |
| `keytool -list -cacerts` | Xem kho CA mặc định của JDK đang chạy | Danh sách phụ thuộc distribution/cấu hình; không nói trước chính xác mọi JVM |
| `keytool -importkeystore ...` | Chuyển/copy entry giữa keystore | Cần kiểm alias, entry type, mật khẩu, certificate chain |
| `jarsigner -keystore signing.p12 -signedjar app-signed.jar app.jar signing-alias` | Ký **JAR** bằng private key thuộc alias ký | Cần có `app.jar` và keystore có private key; không phải thao tác cấp certificate TLS |
| `jarsigner -verify -verbose -certs app-signed.jar` | Xem chữ ký và nội dung đã được bảo vệ của JAR | JAR có chữ ký hợp lệ **không mặc nhiên** đồng nghĩa signer đã được hệ thống tin; đọc warning/chain |
| `jar tf app.jar` | Liệt kê nội dung JAR khi kiểm artifact | Không thay thế xác minh chữ ký |
| `java -Djavax.net.debug=ssl,handshake ...` | Quan sát bắt tay TLS khi chạy ứng dụng | Có thể in thông tin nhạy cảm; chỉ dùng lúc debug có kiểm soát |
| `java -Djava.security.debug=certpath ...` | Khảo sát quá trình kiểm chứng chứng thư PKIX | Log lớn; cần quản lý dữ liệu và quyền truy cập |

`jarsigner` là **ký mã phân phối (code signing)**; `keytool` chủ yếu quản lý material khóa/certificate; cả hai dùng các thành phần JCA/PKI. Chúng không thay thế TLS cho dữ liệu trao đổi qua mạng.

### Cấu hình bảo mật runtime cần nhận diện

JDK có file cấu hình bảo mật thường tại `<java-home>/conf/security/java.security`. Các property như `security.provider.N` xác định thứ tự provider; `jdk.tls.disabledAlgorithms` giới hạn thuật toán/protocol cho TLS; `jdk.certpath.disabledAlgorithms` ràng buộc kiểm đường chứng thư; `jdk.jar.disabledAlgorithms` liên quan chữ ký JAR. Cách nạp, override và giá trị mặc định phụ thuộc phiên bản/distribution. **Ưu tiên nâng JDK, sửa certificate/cấu hình đúng nguyên nhân; không nới lỏng các danh sách thuật toán yếu để xử lý nhanh lỗi.**

Một số thuộc tính hệ thống trong triển khai JSSE có thể chỉ định keystore/truststore ứng dụng, ví dụ `javax.net.ssl.keyStore`, `javax.net.ssl.trustStore` và các tùy chọn type/password tương ứng. Cần phân biệt **system property** này với **security property** trong `java.security`; không phải mọi API/framework đều dùng default `SSLContext` hoặc các property mặc định.

## 8. Java 17+: chọn engine theo provider và nhận diện ranh giới API

Ví dụ này **không mã hóa hay ký dữ liệu lần nữa**. Thay vào đó, nó kiểm chứng cách các engine phân giải nhà cung cấp thực tế và nhận diện những package tách biệt của JSSE/JAAS. Đây là câu hỏi riêng của **bản đồ JDK**, khác với ví dụ primitive ở bài so sánh mã hóa, hash, MAC và chữ ký.

Tạo file `SecurityApiMapDemo.java`:

```java
import java.security.MessageDigest;
import java.security.Provider;
import javax.crypto.Cipher;
import javax.net.ssl.SSLContext;
import javax.security.auth.login.LoginContext;

public class SecurityApiMapDemo {
    public static void main(String[] args) throws Exception {
        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");
        Cipher aesGcm = Cipher.getInstance("AES/GCM/NoPadding");

        Provider digestProvider = digest.getProvider();
        Provider cipherProvider = aesGcm.getProvider();

        System.out.println("Digest provider resolved: "
                + (digestProvider != null));
        System.out.println("Cipher provider resolved: "
                + (cipherProvider != null));
        System.out.println("JSSE package: "
                + SSLContext.class.getPackageName());
        System.out.println("JAAS package: "
                + LoginContext.class.getPackageName());
    }
}
```

Biên dịch/chạy trên JDK 17+ có provider SHA-256 và AES-GCM thông dụng:

```bash
javac --release 17 SecurityApiMapDemo.java
java SecurityApiMapDemo
```

Stdout:

```text
Digest provider resolved: true
Cipher provider resolved: true
JSSE package: javax.net.ssl
JAAS package: javax.security.auth.login
```

Dòng đầu/cuối chỉ cho biết engine đã có provider và lớp thuộc package tương ứng, **không** nói TLS đã xác minh certificate, JAAS đã login, hay phép mã hóa đã được thực hiện đúng. Muốn biết provider cụ thể, có thể in `digestProvider.getName()` và `cipherProvider.getName()`, nhưng tên phụ thuộc JDK/distribution/cấu hình. `Cipher.getInstance(...)` cũng mới xác nhận có transformation; dùng đúng khóa, nonce/AAD và xử lý tag vẫn là trách nhiệm của ứng dụng.

Qua các ví dụ và công cụ của chương, luôn tách: **engine Java**, **provider triển khai**, **cấu hình tin cậy PKI** và **quyền nghiệp vụ**.

## 9. Chọn đúng lớp trong tình huống thực tế

**Tình huống A – file cấu hình nội bộ cần giữ bí mật và phát hiện sửa đổi:** mã hóa bằng `Cipher` với AEAD (JCE/JCA); khóa cần có nguồn lưu an toàn, rotation, kiểm quyền và định dạng dữ liệu bao gồm nonce/tag. TLS không bảo vệ nội dung sau khi ghi xuống đĩa.

**Tình huống B – gọi API HTTPS:** dùng `HttpClient` hoặc HTTP client có TLS chuẩn (JSSE); xác minh certificate chain theo PKI và hostname; khi cần mTLS, cấu hình client private key/certificate cùng trust của server. Không dùng JAAS để “thay” việc xác minh certificate HTTPS.

**Tình huống C – tài liệu phải kiểm được tác giả ký:** `Signature` dùng private key ký và public key kiểm; nếu cần tin “ai là người ký”, phải phân phối public key đáng tin hoặc dùng PKI/chính sách certificate. `MessageDigest` đơn lẻ chưa đủ.

**Tình huống D – tích hợp đăng nhập Kerberos:** xem xét JAAS `LoginContext`/`LoginModule`, `Subject`, và tích hợp Kerberos/GSS liên quan; quyền vào endpoint vẫn phụ thuộc policy ứng dụng.

**Tình huống E – ký JAR nội bộ:** quản lý khóa ký bằng `keytool`/hệ thống quản lý khóa, dùng `jarsigner`, kiểm signature và chuỗi tin cậy. Ký JAR không tự mã hóa bytecode và không mặc nhiên bảo đảm tải/chạy JAR là an toàn.

## 10. Chẩn đoán lỗi và sai lầm phổ biến

| Triệu chứng | Nơi kiểm tra trước | Điều cần tránh |
| --- | --- | --- |
| `NoSuchAlgorithmException` / `NoSuchPaddingException` | Tên engine/thuật toán/transformation, provider được đăng ký, bản JDK | Đổi thuật toán bảo mật tùy tiện để “chạy được” |
| `AEADBadTagException` | Key, nonce, AAD, ciphertext/tag; có bị sửa, lỗi encoding/truyền tải hay không | Bỏ qua tag hoặc giải mã trả plaintext khi auth thất bại |
| `SSLHandshakeException` với `PKIX path building failed` | Truststore client, CA root/intermediate, chuỗi server gửi, thời hạn và chính sách PKIX | Tắt trust validation hoặc nhập bừa leaf certificate |
| Lỗi hostname/SAN | DNS/IP thực sự truy cập so với SAN certificate | Tắt hostname verification |
| `UnrecoverableKeyException` / không chọn được alias | Password, key entry, entry type, alias và chain | Log private key hoặc mật khẩu để debug |
| TLS không thương lượng được version/cipher suite | Chính sách TLS của hai phía, `jdk.tls.disabledAlgorithms`, provider/JDK | Bật lại SSL/TLS yếu trong production |
| JAAS `LoginException` | Tên app trong configuration, LoginModule, CallbackHandler, thông tin định danh và dependency xác thực | Xem thành công TLS như bằng chứng `login()` đã chạy |
| `jarsigner -verify` có warning | Certificate người ký, trust, thời gian hiệu lực, timestamp, thuật toán bị vô hiệu | Đồng nhất “có chữ ký” với “nhà phát hành đáng tin” |

Luồng debug hợp lý: **xác định tầng lỗi → kiểm đúng API/cấu hình → quan sát log có giới hạn → sửa nguyên nhân → chạy lại kiểm chứng**. Hạn chế bật debug TLS/CertPath ở production trong thời gian dài vì log có thể lộ metadata, tăng kích thước và làm khó tìm tín hiệu quan trọng.

## 11. Checklist ghi nhớ và câu hỏi tự kiểm tra

- **Cần hash?** `MessageDigest`. **Cần MAC?** `Mac`. **Cần mã hóa?** `Cipher`/AEAD. **Cần ký?** `Signature`. Tất cả sống trong hệ JCA (bao gồm phần lịch sử gọi là JCE).
- **Cần kênh mạng TLS?** JSSE/HTTP client chuẩn. **Cần đánh giá certificate/chuỗi?** PKI/X.509 và TrustManager/CertPath. **Cần đăng nhập có module cắm thêm?** JAAS.
- **Cần tạo/xem khóa, certificate, CSR?** `keytool`. **Cần ký/kiểm JAR?** `jarsigner`. **Cần debug handshake?** `javax.net.debug`; cần debug đường chứng thư thì xét `java.security.debug=certpath`.
- **Cần triển khai thật?** Dùng cấu hình trust/identity rõ ràng, thuật toán hiện đại, khóa lưu an toàn, xác thực hostname, rotation và quyền nghiệp vụ; không tin mọi certificate và không dùng các ví dụ local tự ký làm chính sách production.

Tự trả lời: (1) Vì sao `AES/GCM` và TLS đều có mã hóa nhưng không thay thế nhau? (2) Tại sao một certificate hợp lệ về chữ ký vẫn có thể bị HTTPS từ chối? (3) Điểm khác nhau giữa HMAC, chữ ký số và chỉ SHA-256 là gì? (4) Nếu server yêu cầu mTLS thì client cần thêm vật liệu gì? (5) Vì sao JAAS `Subject` không tự cấp quyền truy cập mọi API?

**Đáp án định hướng:** (1) AEAD bảo vệ payload theo khóa/nonce của ứng dụng; TLS bảo vệ phiên truyền mạng cùng xác thực peer. (2) Có thể sai trust anchor, thời hạn, SAN/hostname, key usage hoặc chính sách. (3) Hash không khóa; HMAC dùng secret chung; chữ ký dùng private/public key. (4) Client cần private key và certificate chain phù hợp để trình bày; vẫn phải tin server. (5) Ứng dụng phải thực thi authorization theo policy nghiệp vụ riêng.

## Tài liệu chính thống để đọc sâu

- [Java Cryptography Architecture (JCA) Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html): provider/engine, khóa và các phép mật mã; giải thích vì sao JCE thuộc JCA hiện đại.
- [Java Secure Socket Extension (JSSE) Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html): TLS, `SSLContext`, key/trust manager, endpoint identification.
- [JAAS Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-authentication-authorization-service-jaas-reference-guide.html): `LoginContext`, `LoginModule`, `Subject`, cấu hình xác thực.
- [Java PKI Programmer's Guide](https://docs.oracle.com/en/java/javase/21/security/java-pki-programmers-guide.html): `CertPathBuilder`, `CertPathValidator`, `TrustAnchor`, kiểm chứng PKIX.
- [keytool](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html) và [jarsigner](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jarsigner.html): cú pháp công cụ, chữ ký và cảnh báo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-fundamentals-008">1.8 Môi trường thực hành Java 21 và cách đối chiếu API với phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# 1.8. Môi trường thực hành Java 21 và cách đối chiếu API với phiên bản JDK

Bài này thiết lập môi trường thực hành **Java 21** cho module Security & Cryptography, đồng thời chỉ ra cách xác minh một lớp, phương thức hoặc thuật toán bảo mật thực sự có mặt trong **phiên bản Java và JDK đang dùng**. Đây là bước nền tảng trước khi học `MessageDigest`, `Cipher`, `Signature`, `KeyStore`, TLS/JSSE và các API mới.

## Mục tiêu sau bài học

- Cài và chọn đúng **JDK 21**, kiểm tra `java`, `javac`, `jshell`, IDE và build tool có đang sử dụng bản phù hợp.
- Biên dịch, chạy chương trình Java 21 đơn giản với thư viện chuẩn và một API mật mã.
- Tự đối chiếu API qua **tài liệu Java SE 21/JDK 21**, trường `Since`, module, chữ ký phương thức và lệnh `javac --release`.
- Phân biệt **API tồn tại**, **bytecode tương thích**, và **thuật toán/provider thực sự khả dụng tại runtime**; tránh hiểu nhầm khi phát triển ứng dụng mật mã.

## 1. Hiểu đúng ba tầng phiên bản

| Tầng cần kiểm tra | Công cụ hoặc nguồn kiểm tra | Ý nghĩa |
| --- | --- | --- |
| JDK dùng để phát triển | `javac -version`, cấu hình Project SDK/toolchain | Quyết định trình biên dịch và các công cụ có sẵn. |
| Java dùng để chạy | `java -version`, `Runtime.version()` | Quyết định runtime thực thi, thư viện/module và security provider có hiệu lực. |
| Java tối thiểu mà sản phẩm hỗ trợ | `javac --release 21`, kiểm thử trên runtime mục tiêu | Ràng buộc cú pháp, API nền tảng và phiên bản class file được phép phát hành. |

**JDK** gồm compiler (`javac`), runtime (`java`), công cụ phân tích (`javap`, `jdeps`), công cụ thử nhanh (`jshell`), `keytool` cùng thư viện/module chuẩn. Chỉ cài một runtime mà thiếu compiler không đủ cho bài thực hành. “Java 21” là dòng phiên bản chính; số cập nhật như `21.0.x`, nhà phân phối JDK và hệ điều hành/kiến trúc có thể khác nhau. Ưu tiên bản cập nhật bảo mật mới trong dòng 21 lấy từ nguồn phát hành đáng tin cậy.

**Lưu ý:** JDK được chọn trong terminal có thể khác JDK của IDE, Maven, Gradle, service hoặc container. Kết luận “đang chạy Java 21” phải dựa trên môi trường **thực sự biên dịch và chạy chương trình**.

## 2. Chuẩn bị JDK 21 và kiểm tra đường dẫn

1. Tải và cài một bản **JDK 21** tương thích hệ điều hành (ví dụ Oracle JDK 21 hoặc một bản OpenJDK 21 được hỗ trợ như Eclipse Temurin). Chọn đúng kiến trúc CPU; xác minh nhà phát hành và cập nhật bản vá bảo mật.
2. Thiết lập `JAVA_HOME` trỏ đến **thư mục gốc JDK** (thư mục chứa `bin`), và đưa `JAVA_HOME/bin` vào `PATH` theo cơ chế của hệ điều hành.
3. Mở terminal mới rồi chạy:

```text
java -version
javac -version
jshell --version
```

Kết quả kỳ vọng có **major version 21** ở cả ba lệnh (chuỗi đầy đủ khác tùy nhà phát hành, bản vá và hệ điều hành). `java -version` và `javac -version` phải tương thích với môi trường đã chọn.

Kiểm tra file thực thi mà terminal sẽ gọi:

```powershell
# Windows PowerShell
where.exe java
where.exe javac
$env:JAVA_HOME
```

```bash
# Linux / macOS
command -v java
command -v javac
printf '%s\n' "$JAVA_HOME"
```

Nếu máy có nhiều JDK, kết quả `where.exe` có thể liệt kê nhiều đường dẫn: đường dẫn được ưu tiên trên `PATH` thường quyết định lệnh thực tế. Trên Linux/macOS, `command -v` xác định điểm vào của lệnh; có thể là symlink và cần kiểm tra thêm liên kết/Java home theo hệ thống đang dùng.

```text
java -XshowSettings:properties -version
```

Trong phần properties, chú ý `java.home`, `java.vendor`, `java.version`, `os.arch`; cách này giúp kiểm tra runtime đang hoạt động. Lệnh có thể in nhiều dòng, nên chỉ cần xem những thuộc tính liên quan. Khi làm việc với IDE/build tool, kiểm tra cấu hình SDK/JDK và lệnh chạy thực tế của chúng, không chỉ terminal.

**Bộ công cụ tối thiểu:** terminal, editor/IDE, JDK 21, thư mục chứa file `.java`. Chưa cần thêm thư viện mật mã bên ngoài để thử các API chuẩn như `MessageDigest` hay `Cipher`.

## 3. Bài thực hành đầu tiên: biên dịch và kiểm tra API mật mã

Tạo file **`ApiProbe.java`** (tên phải trùng với `public class ApiProbe`):

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Cipher;

public class ApiProbe {
    public static void main(String[] args) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest("Java 21".getBytes(StandardCharsets.UTF_8));

        // Chỉ xác minh có implementation của transformation, chưa mã hóa dữ liệu.
        Cipher.getInstance("AES/GCM/NoPadding");

        System.out.println("Runtime feature: " + Runtime.version().feature());
        System.out.println("SHA-256 bytes: " + digest.length);
        System.out.println("Hex characters: "
                + HexFormat.of().formatHex(digest).length());
        System.out.println("AES/GCM available: true");
    }
}
```

Trong thư mục chứa file, chạy:

```text
mkdir out
javac --release 21 -encoding UTF-8 -d out ApiProbe.java
java -cp out ApiProbe
```

Trên **JDK 21 chuẩn có provider hỗ trợ AES/GCM**, kết quả là:

```text
Runtime feature: 21
SHA-256 bytes: 32
Hex characters: 64
AES/GCM available: true
```

Giải thích:

- `--release 21` buộc compiler kiểm tra cú pháp và tập API nền tảng tương ứng Java 21, đồng thời tạo bytecode cho Java 21. `-encoding UTF-8` tránh phụ thuộc encoding mặc định của máy; `-d out` đưa các `.class` vào thư mục riêng.
- `java -cp out ApiProbe` chạy class vừa tạo trên runtime đang được lệnh `java` trỏ tới. Nếu runtime là Java 24, dòng `Runtime feature` sẽ hiển thị `24` dù bytecode được biên dịch với `--release 21`.
- SHA-256 luôn cho 256 bit = **32 byte**; dạng hexadecimal không phân tách dùng **64 ký tự**. `HexFormat` là API chuẩn xuất hiện từ Java 17.
- `Cipher.getInstance("AES/GCM/NoPadding")` yêu cầu một **transformation** từ security provider. Đoạn này chỉ chứng minh provider có thể cung cấp transformation, **chưa** kiểm tra việc dùng khóa, IV/nonce, AAD hay tính an toàn của một quy trình mã hóa. Nếu provider không cung cấp transformation, lệnh sẽ ném exception thay vì in dòng cuối.

`Runtime.version().feature()` dùng để biết phiên bản runtime hiện hành; nó không tự xác nhận phiên bản đã dùng để biên dịch. Tách bạch hai việc khi báo cáo lỗi môi trường.

## 4. Đối chiếu một API với đúng bản JDK: quy trình năm bước

Giả sử bạn gặp một ví dụ dùng `javax.crypto.KEM` hoặc `java.util.HexFormat` và muốn biết có chạy được trên Java 17/21 hay không.

**Bước 1 — Xác định tên đầy đủ.** Ghi rõ package, tên lớp và phương thức/overload cần gọi. Ví dụ `javax.crypto.KEM.getInstance(String)` khác với chỉ nói “KEM”. Một lớp có thể tồn tại từ phiên bản cũ nhưng **phương thức riêng** lại được bổ sung sau.

**Bước 2 — Mở tài liệu gắn với phiên bản mục tiêu.** Với Java 21, dùng [Java SE 21 & JDK 21 API Specification](https://docs.oracle.com/en/java/javase/21/docs/api/). Tìm đúng lớp và đọc lần lượt:

- `Module` và `Package` (ví dụ `javax.crypto.KEM` thuộc `java.base`).
- Mô tả công dụng, phương thức, kiểu tham số, kiểu trả về và checked exceptions.
- `Since` của **lớp** và, khi có, của **constructor/phương thức/field** đang dùng.
- `Deprecated`, `forRemoval`, yêu cầu về preview/incubator (nếu có), điều kiện provider hoặc phần ghi chú triển khai.

**Bước 3 — Đối chiếu với bản cũ và bản đang chạy.** Tài liệu Java 17 và Java 21 có thể khác nhau. Ví dụ:

| API | Thông tin từ tài liệu chính thức | Cách kết luận |
| --- | --- | --- |
| `java.util.HexFormat` | `Since: 17` | Có trong API nền tảng từ Java 17 trở đi. |
| `javax.crypto.KEM` | `Since: 21` | Không thuộc API Java 17; mã tham chiếu lớp này cần baseline Java 21 hoặc mới hơn. |
| `java.security.MessageDigest` | API có từ lâu, nhưng thuật toán phụ thuộc provider | Có lớp chưa đồng nghĩa mọi tên thuật toán đều được hỗ trợ. |

Đọc `Since` giúp biết **mốc xuất hiện API**. Nó không bảo đảm hành vi giống nhau trong mọi bản vá, mọi provider hay mọi vendor. Nếu một yêu cầu gắn với version cụ thể, kiểm tra tài liệu của **đúng version** thay vì tìm kiếm chung trên web (có thể trả tài liệu cho Java 22/25/26...).

**Bước 4 — Để compiler kiểm chứng compatibility.** Dùng `--release` với mức Java mà ứng dụng muốn hỗ trợ. Cách này mạnh hơn việc chỉ xem một trang tài liệu.

**Bước 5 — Kiểm tra trên runtime mục tiêu.** Với API bảo mật, khâu runtime cần xác nhận provider, cấu hình chính sách bảo mật, thuật toán và tham số hoạt động. Build thành công mới chứng minh code tham chiếu được API trong thời điểm biên dịch; nó chưa bảo đảm thuật toán được triển khai khi chạy.

## 5. Thử nghiệm chính xác với `--release`: API chỉ có từ Java 21

Theo [Javadoc của `javax.crypto.KEM` cho Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.html), lớp `KEM` có `Since: 21`. Tạo file **`VersionGate.java`**:

```java
import javax.crypto.KEM;

public class VersionGate {
    public static void main(String[] args) {
        System.out.println(KEM.class.getName());
    }
}
```

Trên **compiler JDK 21**, thực hiện lần lượt:

```text
mkdir out17
javac --release 17 -d out17 VersionGate.java
```

Lệnh trên **phải thất bại** vì `javax.crypto.KEM` chưa nằm trong API Java 17 (thông báo cụ thể có thể khác; thường chứa `cannot find symbol`). Tiếp tục:

```text
mkdir out21
javac --release 21 -d out21 VersionGate.java
java -cp out21 VersionGate
```

Kết quả khi chạy bằng Java 21 trở lên:

```text
javax.crypto.KEM
```

Ví dụ chỉ tham chiếu **kiểu API**; nó chưa gọi `KEM.getInstance(...)` và không chứng minh một thuật toán KEM cụ thể khả dụng. Đây là khác biệt quan trọng giữa **có lớp `KEM`** và **có provider hỗ trợ thuật toán KEM**.

### Vì sao dùng `--release` thay cho `--source` và `--target`?

- `--source 21` kiểm tra ngữ pháp Java 21; `--target 21` chọn phiên bản bytecode đầu ra. Khi biên dịch bằng JDK mới hơn, cặp tùy chọn này **không tự giới hạn đầy đủ API chuẩn về đúng bản Java 21**.
- `--release 21` kết hợp giới hạn ngôn ngữ, bytecode và **các API công khai của platform release 21**. Với mã cần chạy trên Java 21, đây là lựa chọn phù hợp.
- `--release` chỉ chấp nhận các mức được **compiler hiện tại hỗ trợ**. JDK 17 không thể biên dịch `--release 21`; phải chọn JDK compiler đủ mới. Cũng không thể dùng JDK 21 để chọn tùy ý một release tương lai.
- Tùy chọn này không tự kiểm tra API của dependency ngoài JDK, native library, phản xạ, cấu hình runtime hoặc tính sẵn có của thuật toán mật mã.

Mã biên dịch cho Java 21 thường có class-file major version **65**. Có thể xem `javap -verbose out/ApiProbe.class` và tìm `major version`; tuy nhiên số version class file chỉ phản ánh định dạng bytecode, **không thay thế** kiểm tra thư viện/provider khi chạy. Runtime cũ hơn không đọc được class file mới có thể ném `UnsupportedClassVersionError`.

## 6. Ba câu hỏi riêng biệt khi làm việc với Java Cryptography

**Câu hỏi A: Java có lớp và phương thức này không?** Đọc Javadoc bản mục tiêu, `Since`, module/exports và thử `javac --release`. Ví dụ `java.security.MessageDigest`, `java.security.Signature` và `javax.crypto.Cipher` đều là API trong JDK chuẩn.

**Câu hỏi B: Runtime có thuật toán/provider cần thiết không?** JCA/JCE sử dụng cơ chế **provider**. `MessageDigest.getInstance("SHA-256")` và `Cipher.getInstance("AES/GCM/NoPadding")` là các truy vấn implementation. Tên thuật toán chuẩn, chính sách và provider có thể khác giữa các bản JDK hoặc cấu hình triển khai. Lỗi `NoSuchAlgorithmException` hoặc `NoSuchPaddingException` cần kiểm tra **tên service/transformation và provider thực tế**, không suy ngay rằng lớp Java không tồn tại.

**Câu hỏi C: Cách dùng thuật toán có an toàn và tương thích giao thức không?** Provider có thuật toán mới là điều kiện cần. Còn phải chọn key size, nonce/IV, mode, padding, encoding, quy tắc lưu khóa và quản lý lỗi phù hợp. Ví dụ AES/GCM cần nonce không được tái sử dụng với cùng khóa; thử `getInstance` chưa kiểm tra quy tắc đó.

Để quan sát provider mà không sửa cấu hình, tạo file **`ProviderProbe.java`**:

```java
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;
import javax.crypto.Cipher;

public class ProviderProbe {
    public static void main(String[] args) throws Exception {
        for (Provider provider : Security.getProviders()) {
            System.out.println("Provider: " + provider.getName());
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        System.out.println("SHA-256: " + digest.getProvider().getName());
        Cipher aesGcm = Cipher.getInstance("AES/GCM/NoPadding");
        System.out.println("AES/GCM: " + aesGcm.getProvider().getName());
    }
}
```

Chạy:

```text
javac --release 21 -d out ProviderProbe.java
java -cp out ProviderProbe
```

Tên và thứ tự provider trong output **phụ thuộc runtime**, không nên viết một stdout cố định. Trên một số môi trường có thêm provider của hệ điều hành, phần cứng, thư viện bên thứ ba hoặc cấu hình FIPS. Theo nguyên tắc portable, thường nên gọi `getInstance("...")` **không cố định tên provider**; chỉ chỉ định provider cụ thể khi yêu cầu hệ thống buộc làm vậy và đã kiểm thử môi trường triển khai. Tránh chỉnh `conf/security/java.security` chỉ để “làm cho ví dụ chạy”; nên hiểu cấu hình và tìm nguyên nhân trước.

**Ba loại tài liệu cần đọc cùng nhau:**

1. [Java 21 API Specification](https://docs.oracle.com/en/java/javase/21/docs/api/) — lớp, phương thức, `Since`, module và hợp đồng API.
2. [Java 21 JCA Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html) và [JDK Providers Documentation](https://docs.oracle.com/en/java/javase/21/security/oracle-providers.html) — cách chọn provider, danh mục service và những triển khai đi kèm Oracle JDK 21.
3. [Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html) — cách đặt tên thuật toán, transformation, key store và các quy ước của API bảo mật.

**Không đồng nhất “standard name” với “bảo đảm mọi provider hỗ trợ”.** Tài liệu tên chuẩn định nghĩa cách gọi; tài liệu provider cùng kiểm thử runtime cho biết implementation cụ thể.

## 7. Công cụ dòng lệnh hỗ trợ đối chiếu

| Công cụ | Ví dụ | Giá trị và giới hạn |
| --- | --- | --- |
| `java` | `java --version` | Nhận diện runtime được gọi từ terminal. |
| `javac` | `javac --release 21 ...` | Xác minh mã có nằm trong phạm vi API/cú pháp Java 21; tạo bytecode. |
| `jshell` | `jshell` rồi `Runtime.version()` | Thử nhanh biểu thức/API; dùng cùng JDK dự định kiểm tra. |
| `javap` | `javap -public javax.crypto.KEM` | Quan sát public members của lớp từ JDK đang dùng; không thay thế `Since`. |
| `jdeps` | `jdeps --jdk-internals out` | Tìm dependency tới JDK internal APIs trong class đã biên dịch; không kiểm tra độ mạnh thuật toán mật mã. |
| `keytool` | `keytool -help` | Làm quen công cụ quản lý key/certificate; chưa cần sinh khóa thật ở bài này. |

`java --list-modules` liệt kê module của runtime. `java --describe-module java.base` hoặc `javap` hỗ trợ xem module/class được cài; với API Java 21, hãy đối chiếu thêm Javadoc vì một JDK mới hơn có thể chứa API chưa tồn tại trong release 21.

Không dùng `sun.*`, `com.sun.*` nội bộ để thay cho API chuẩn chỉ vì tìm thấy class khi phân tích JDK. API nội bộ có thể bị đóng gói, thay đổi hoặc bị chặn khi chạy. `jdeps --jdk-internals` hữu ích khi rà soát vấn đề này, nhưng kết quả “không tìm thấy” không chứng minh hệ thống đã tương thích hoàn toàn với một release.

## 8. Đồng bộ IDE và build tool

Nếu dự án dùng Maven, cấu hình mức Java phát hành (đoạn trong `pom.xml`):

```xml
<properties>
    <maven.compiler.release>21</maven.compiler.release>
</properties>
```

Kiểm tra `mvn -version` để biết Maven đang chạy với Java nào và bảo đảm Maven Compiler Plugin đang dùng hỗ trợ `release`. Nếu dự án có cấu hình plugin/toolchain riêng thì cấu hình đó phải nhất quán với mức 21.

Nếu dùng Gradle Groovy DSL, có thể khai báo Java toolchain:

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

Toolchain chọn JDK phù hợp cho tác vụ liên quan; cần phiên bản Gradle/plugin tương thích và cài đặt hoặc khả năng resolve JDK 21. Runtime dùng để **chạy ứng dụng** vẫn cần được kiểm tra riêng. Với IDE, xem **Project SDK**, **language level**, **compiler/build JDK**, cấu hình test và cấu hình run. Sau khi sửa JDK, hãy chạy lại câu lệnh build thực tế rồi xác minh `java -version` trong môi trường chạy.

## 9. Lỗi phổ biến và cách khoanh vùng

| Triệu chứng | Nguyên nhân thường gặp | Bước kiểm tra/khắc phục |
| --- | --- | --- |
| `javac` không tìm thấy | Chưa cài JDK đầy đủ hoặc `PATH` chưa có `bin` của JDK | Kiểm tra `JAVA_HOME`, `where.exe javac` / `command -v javac`. |
| `release version 21 not supported` | Compiler hiện dùng cũ hơn Java 21 | Xem `javac -version` và JDK của IDE/Maven/Gradle; chọn JDK 21 hoặc mới hơn. |
| `cannot find symbol` với một API JDK | API có từ release cao hơn; sai package/chữ ký; hoặc build dùng `--release` thấp | Đọc `Since`, chữ ký overload và thử mức `--release` cần hỗ trợ. |
| `UnsupportedClassVersionError` | Runtime cũ hơn phiên bản class file | So sánh `java -version`, `javac --release` và `javap -verbose`; chạy bằng runtime tương thích hoặc build xuống baseline. |
| `NoSuchAlgorithmException` / `NoSuchPaddingException` | Provider hiện tại không cung cấp tên thuật toán/transformation hoặc gọi sai tên | Kiểm tra Java Security Standard Algorithm Names, các provider đã đăng ký và cấu hình JDK thực tế. |
| `ClassNotFoundException` / `NoClassDefFoundError` | Sai classpath/module-path hoặc thiếu dependency lúc chạy | Xác minh `-cp`, JAR/module và dependency được đóng gói. |
| Lỗi `package ... is not visible`, `InaccessibleObjectException` | Quyền truy cập theo module/reflection, thường liên quan internal API | Tìm public API được hỗ trợ; xem exports/opens và cấu hình module thay vì phụ thuộc workaround lâu dài. |
| Terminal chạy đúng nhưng IDE/build vẫn lỗi | Các tiến trình chọn JDK/toolchain khác nhau | Kiểm tra JDK ở từng tầng: IDE, compiler plugin, test runner, runtime container. |

**Phân biệt tên thư viện:** namespace `javax.crypto` là một phần của Java SE; không có nghĩa mọi gói bắt đầu bằng `javax.` đều thuộc Java SE. Thư viện `jakarta.*` hoặc thư viện mật mã bên thứ ba cần dependency và kiểm tra phiên bản **riêng**, ngoài phạm vi `--release`.

## 10. Bài tự kiểm tra và tiêu chí hoàn thành

1. Lưu output `java -version`, `javac -version` và đường dẫn thực thi. Giải thích vì sao IDE có thể dùng JDK khác terminal.
2. Biên dịch/chạy `ApiProbe.java` bằng `--release 21`. Giải thích vì sao 32 byte SHA-256 được chuyển thành 64 ký tự hexadecimal, và vì sao `Cipher.getInstance` chưa phải phép thử mã hóa an toàn.
3. Tra Javadoc Java 21 của `HexFormat` và `KEM`, chỉ ra `Since` tương ứng, rồi thử `VersionGate.java` với `--release 17` và `--release 21`.
4. Chạy `ProviderProbe.java`, ghi lại tên provider thực tế cho SHA-256 và AES/GCM trên máy của bạn. So sánh với JDK Providers Documentation và lý giải tại sao máy khác có thể ra provider khác.
5. Tự chọn một API bảo mật (chẳng hạn `java.security.Signature` hoặc `java.security.KeyStore`), ghi rõ **tên đầy đủ → phương thức → Since → module → release mục tiêu → provider/runtime test cần thiết**.

Hoàn thành bài khi bạn có thể trả lời rõ: **“Tôi đang biên dịch với JDK nào, chạy với JDK nào, API này xuất hiện từ release nào và runtime nào cung cấp thuật toán thực tế?”**

### Tài liệu tham khảo chính thức (cố định ở Java/JDK 21)

- [Java SE 21 & JDK 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/)
- [JDK 21 Tool Specifications, bao gồm `javac`, `javap` và `jdeps`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/index.html)
- [`javac` JDK 21: ý nghĩa `--release`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html)
- [`HexFormat` (Since 17)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HexFormat.html)
- [`KEM` (Since 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.html)
- [JCA Reference Guide JDK 21](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html)
- [JDK Providers Documentation JDK 21](https://docs.oracle.com/en/java/javase/21/security/oracle-providers.html)


</details>

- [Quay lại đầu trang](#back-to-top)
