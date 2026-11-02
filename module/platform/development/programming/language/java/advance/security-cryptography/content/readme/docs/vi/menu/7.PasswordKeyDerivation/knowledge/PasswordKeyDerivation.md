<a id="back-to-top"></a>

# 7. Mật khẩu và dẫn xuất khóa

## Menu
- [7.1 Phân biệt lưu bộ kiểm tra mật khẩu với mã hóa dữ liệu bằng khóa từ mật khẩu](#password-key-derivation-001)
- [7.2 Vì sao băm nhanh một lần không đủ cho mật khẩu có entropy thấp](#password-key-derivation-002)
- [7.3 Vai trò của salt, số vòng lặp, độ dài đầu ra và phiên bản cấu hình](#password-key-derivation-003)
- [7.4 PBKDF2 qua SecretKeyFactory và PBEKeySpec](#password-key-derivation-004)
- [7.5 Tạo, lưu và kiểm tra bản ghi mật khẩu với đầy đủ tham số](#password-key-derivation-005)
- [7.6 Hiệu chỉnh chi phí tính toán và nâng cấp tham số khi xác thực lại](#password-key-derivation-006)
- [7.7 Phân biệt password-based encryption với khóa sinh ngẫu nhiên](#password-key-derivation-007)
- [7.8 Xử lý char&#91;&#93;, clearPassword và giới hạn của việc xóa dữ liệu nhạy cảm trên JVM](#password-key-derivation-008)
- [7.9 Tương thích charset, Unicode và quy tắc chuyển đổi mật khẩu giữa các hệ thống](#password-key-derivation-009)

## <a id="password-key-derivation-001">7.1 Phân biệt lưu bộ kiểm tra mật khẩu với mã hóa dữ liệu bằng khóa từ mật khẩu</a>

<details>
<summary>Xem chi tiết</summary>

# 7.1. Phân biệt lưu bộ kiểm tra mật khẩu với mã hóa dữ liệu bằng khóa từ mật khẩu

**Mật khẩu có thể tham gia hai bài toán bảo mật rất khác nhau:**

1. **Lưu bộ kiểm tra mật khẩu (password verifier):** hệ thống muốn biết người đăng nhập có đưa ra đúng mật khẩu đã đăng ký hay không. Hệ thống **không cần khôi phục mật khẩu gốc** để làm việc này.
2. **Mã hóa dữ liệu bằng khóa dẫn xuất từ mật khẩu (password-based encryption, PBE):** hệ thống muốn bảo vệ dữ liệu sao cho người có mật khẩu thích hợp **có thể tạo lại khóa giải mã** và lấy lại plaintext.

Hai bài toán thường dùng một hàm dẫn xuất khóa từ mật khẩu, salt và tham số chi phí, nhưng **giá trị đầu ra, cách lưu, vòng đời, hậu quả quên mật khẩu và cơ chế xác minh hoàn toàn khác nhau**. Trộn lẫn chúng dễ dẫn đến những lỗi nghiêm trọng: lưu mật khẩu dưới dạng có thể giải mã, dùng verifier trực tiếp làm khóa mã hóa, tái sử dụng một khóa cho nhiều mục đích, hoặc làm mất khả năng giải mã dữ liệu sau khi đổi mật khẩu.

Bài này tập trung vào **phân biệt và kết nối đúng hai mô hình**, không tự thiết kế thuật toán mật mã mới. Sau khi học, bạn có thể:

- Giải thích password verifier, password hashing, password-based key derivation, encryption key và authentication tag bằng đúng vai trò của chúng.
- Thiết kế hồ sơ lưu verifier có salt, thuật toán, tham số chi phí và kết quả dẫn xuất; xác minh mật khẩu nhập lại mà không cần lưu plaintext.
- Thiết kế envelope lưu dữ liệu mã hóa có KDF salt, nonce/IV, ciphertext và authentication tag; khôi phục dữ liệu bằng mật khẩu và kiểm tra toàn vẹn.
- Phân biệt **salt của KDF**, **nonce của AEAD**, **pepper**, **master key/DEK**, và lý do chúng không thể thay thế nhau.
- Nhận diện rủi ro tấn công đoán mật khẩu ngoại tuyến, đổi/reset mật khẩu, mất khóa, replay, format/version, và triển khai Java 17+ với API tiêu chuẩn.

## 1. Đặt đúng câu hỏi: “xác minh bí mật” hay “khôi phục dữ liệu”?

Hãy xét cùng một người dùng đặt mật khẩu `p`, nhưng hai hệ thống dùng nó theo hai mục đích:

**Hệ thống A — đăng nhập:** ứng dụng chỉ cần trả lời *mật khẩu vừa nhập có phù hợp với tài khoản không?* Khi đăng ký, hệ thống tính một bộ kiểm tra từ mật khẩu và lưu bộ kiểm tra đó. Khi đăng nhập, nó tính lại theo cùng quy tắc và so sánh. Hệ thống không có bước “giải mã password verifier”.

**Hệ thống B — mở két dữ liệu:** người dùng có một tài liệu đã mã hóa. Muốn lấy lại nội dung, hệ thống phải tái tạo đúng khóa (hoặc khóa dùng mở khóa dữ liệu), giải mã ciphertext và **xác minh authentication tag**. Nếu quên mật khẩu và không có một phương án recovery khác, dữ liệu có thể mất khả năng truy cập vĩnh viễn.

| Câu hỏi | Lưu password verifier | Mã hóa dữ liệu với khóa từ mật khẩu |
| --- | --- | --- |
| Mục tiêu | **Xác thực mật khẩu** đã đăng ký | **Giữ bí mật và khôi phục plaintext** |
| Đầu vào nhạy cảm | Password người dùng cung cấp | Password/secret dùng để mở dữ liệu |
| KDF/password hashing | Tạo bộ kiểm tra khó đoán offline | Dẫn xuất khóa mã hóa hoặc key-encryption key |
| Thứ được lưu | Salt, thuật toán, tham số, verifier | Salt KDF, tham số, nonce, ciphertext, tag; có thể wrapped DEK |
| Có lưu mật khẩu gốc? | Không | Không |
| Có cần giải mã verifier? | Không, chỉ tính lại và so sánh | Không; phải giải mã **ciphertext** bằng khóa dẫn xuất |
| Kết quả khi nhập đúng | Login được chấp nhận **sau các kiểm tra khác** | Plaintext được giải mã và tag hợp lệ |
| Kết quả khi nhập sai | Verifier không khớp | Không tạo được khóa đúng; AEAD tag thường không hợp lệ |
| Quên mật khẩu | Có thể tạo lại verifier bằng quy trình reset đã xác thực | Không thể tự khôi phục dữ liệu cũ nếu không có khóa/recovery path |
| Lộ database | Verifier có thể bị đoán mật khẩu offline | Ciphertext có thể bị đoán password offline để thử giải mã |
| Bảo vệ chính | Password hashing có chi phí phù hợp + chống tấn công online | KDF phù hợp + AEAD + quản lý khóa + chiến lược recovery |
| Danh tính/quyền | Phải kết hợp account identity, rate limiting, MFA, authorization | Cần kiểm soát quyền truy cập ciphertext, đúng chủ sở hữu dữ liệu |

**Mật khẩu không phải sẵn một khóa AES.** Mật khẩu do người dùng nhớ thường ít entropy, có độ dài/encoding bất kỳ. Không được `new SecretKeySpec(password.getBytes(UTF_8), "AES")` rồi xem đó là thiết kế an toàn. Cần một cơ chế chuyên dụng để xử lý password, salt, tham số chi phí và mục đích sử dụng.

## 2. Lưu bộ kiểm tra mật khẩu: Password hashing

### 2.1. Luồng đăng ký và đăng nhập

Ký hiệu:

- `P`: mật khẩu gốc do người dùng nhập.
- `S_v`: salt ngẫu nhiên riêng cho một verifier.
- `params_v`: thuật toán/phiên bản và chi phí tính toán.
- `V = PasswordHash(P, S_v, params_v)`: bộ kiểm tra được lưu.

```text
Đăng ký / đổi mật khẩu hợp lệ              Đăng nhập
-----------------------------              ----------------------------
Password P nhập qua kênh an toàn            Password P' vừa nhập
             |                                         |
    Sinh salt S_v ngẫu nhiên                      Đọc record người dùng
             |                                  (salt, params, verifier)
 PasswordHash(P, S_v, params_v)                        |
             |                              PasswordHash(P', S_v, params_v)
             v                                         |
 Lưu: algorithm, params, salt, verifier                v
 Không lưu password P                      So sánh verifier theo API an toàn
                                                       |
                                               Trùng -> qua bước xác thực mật khẩu
                                               Sai   -> từ chối
```

Đối với mật khẩu, nên chọn **password-hashing/KDF có chi phí điều chỉnh được**, ví dụ **Argon2id** qua thư viện được thẩm định (thường ưu tiên khi chính sách cho phép), hoặc **scrypt/bcrypt/PBKDF2** tùy yêu cầu tương thích và quy định. Argon2id có khả năng yêu cầu chi phí bộ nhớ; PBKDF2 dùng nhiều lượt tính toán và phổ biến trong môi trường yêu cầu tương thích/FIPS. Không chọn `SHA-256(password)` trực tiếp, vì nó quá nhanh để chống tấn công thử nhiều mật khẩu.

Trong Java 17, JCA/JCE có `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`. Đây là ví dụ **tương thích với thư viện chuẩn**, không có nghĩa PBKDF2 luôn là lựa chọn tối ưu hơn Argon2id trong mọi hệ thống. Argon2id cần một implementation thư viện bảo mật phù hợp; không tự lập trình Argon2 từ đầu.

### 2.2. Record password verifier chứa những gì?

Mẫu logic:

```json
{
  "accountId": "user-42",
  "passwordVerifier": {
    "scheme": "PBKDF2WithHmacSHA256",
    "iterations": 600000,
    "salt": "base64-of-random-salt",
    "derivedVerifier": "base64-of-derived-bytes"
  }
}
```

Các giá trị `base64-of-...` minh họa vị trí dữ liệu nhị phân, **không phải record hợp lệ dùng ngay**. Trong production, định dạng có thể là chuỗi PHC hoặc một object được version hóa tùy scheme.

- **Salt:** mỗi bản ghi có salt độc lập do CSPRNG sinh, thông thường 16 byte hoặc hơn. Salt **không cần giữ bí mật**; cùng một password nhưng salt khác sẽ tạo verifier khác.
- **Cost:** iterations/memory/time/parallelism (tùy thuật toán) được lưu để hệ thống biết cách kiểm tra và có kế hoạch nâng cấp. Cần chọn theo khuyến nghị hiện hành, benchmark trên phần cứng triển khai và kiểm soát tải DoS.
- **Verifier:** kết quả được lưu để đối chiếu; không nên dùng nó như mật khẩu, API token, AES key hoặc bí mật phải gửi lại client.
- **Pepper (tùy thiết kế):** bí mật cấp hệ thống được giữ riêng khỏi database, ví dụ trong vault/HSM, có thể bổ sung bảo vệ khi database chỉ bị lộ. Pepper **không thay thế** salt hoặc password hashing; phải có chính sách rotation/recovery riêng.

Nếu hệ thống dùng một thư viện có định dạng password-hash chuẩn, hãy để thư viện quản lý record, verify, phiên bản và upgrade nếu phù hợp. Đừng viết parser tự chế dễ chấp nhận tham số quá lớn, giá trị malformed hoặc thuật toán yếu. Việc lưu verifier riêng cho từng user không tự kiểm soát việc ai được phép đặt lại mật khẩu.

### 2.3. Vì sao verifier “một chiều” vẫn có thể bị đoán?

Hàm password-hashing không phải phép biến đổi bất khả đảo về mặt tuyệt đối. Khi đối thủ lấy được `S_v`, `params_v` và `V`, họ có thể chọn ứng viên `P_i`, tính `PasswordHash(P_i, S_v, params_v)` rồi so sánh với `V`. Đây là **offline guessing**; mật khẩu yếu vẫn có thể bị tìm ra.

Năng lực bảo vệ đến từ **chi phí mỗi lần đoán**, salt riêng và độ mạnh mật khẩu thực tế, cộng với việc giảm quyền đọc dữ liệu và bảo vệ pepper khi dùng. Với tấn công **online** qua màn hình login, còn cần rate limiting, giám sát, khóa/ràng buộc phiên phù hợp, MFA và phản hồi không tiết lộ tài khoản có tồn tại hay không. KDF chậm một mình không hạn chế vô số request online.

Một ví dụ rất dễ nhầm:

```text
Password = "123456"
Salt random 128-bit
Verifier = PBKDF2(password, salt, cost)
```

Salt tốt khiến record của người khác không có cùng verifier, nhưng không biến `123456` thành một mật khẩu entropy cao. Kẻ có record vẫn có thể thử `123456` trong tập đoán của họ.

### 2.4. Chức năng reset password

Khi người dùng quên mật khẩu đăng nhập, ứng dụng có thể dùng quy trình khôi phục tài khoản đã xác thực để **thay bộ kiểm tra**:

```text
User proves recovery identity
        -> authorize password reset
        -> set NEW password
        -> generate NEW verifier salt
        -> store NEW verifier/params
        -> revoke sessions/tokens as policy requires
```

Ứng dụng không cần biết lại mật khẩu cũ. Đây là lợi ích thiết kế của verifier: **chỉ cần xác minh đúng/sai**, không cần bí mật cũ để tạo record mới.

Tuy nhiên, thay verifier **không tự đổi** dữ liệu đã mã hóa bằng khóa từ mật khẩu cũ. Đây là ranh giới quan trọng tiếp theo.

## 3. Mã hóa dữ liệu: Dẫn xuất khóa từ mật khẩu rồi dùng AEAD

Mô hình đơn giản hóa:

\[
K_e = KDF(P, S_e, params_e)
\]

\[
(C,T) = AEAD.Encrypt(K_e, nonce, plaintext, AAD)
\]

Bên giải mã phải có `P`, `S_e` và `params_e` để tái tạo đúng `K_e`, rồi đưa `C`, `T`, `nonce` và `AAD` vào AEAD decryption. **Chỉ chấp nhận plaintext sau khi tag xác thực thành công.**

```text
MÃ HÓA DỮ LIỆU                                    GIẢI MÃ DỮ LIỆU
----------------                                    ----------------
Password P                                          Password P'
   +-- KDF salt S_e / params_e                         +-- Đọc S_e / params_e
   |                                                       |
   +----> KDF --------> Encryption key K_e                 +----> KDF ---> K'_e
                             |                                             |
Plaintext -----> AES-GCM(K_e, fresh nonce, AAD)     Ciphertext+tag --> AES-GCM decrypt
                             |                                             |
         Lưu envelope (salt, params, nonce,          Auth tag đúng và metadata đúng?
         ciphertext, tag, version...)                    /              \
                                                       Đúng             Sai
                                                        |                |
                                                    Plaintext           Reject
```

Nên dùng một AEAD chuẩn như **AES-GCM** hoặc ChaCha20-Poly1305 theo thư viện và chính sách triển khai. AES-GCM cung cấp bí mật cho plaintext và kiểm tra tính toàn vẹn của ciphertext/AAD qua tag. Nếu giải mã trả `AEADBadTagException`, có thể do mật khẩu/khóa sai, nonce/AAD sai hoặc ciphertext/tag bị sửa: ứng dụng thường **không thể kết luận chắc chắn riêng nguyên nhân nào** chỉ từ exception đó.

### 3.1. Những gì phải lưu để có thể mở lại ciphertext

Một envelope minh họa (mang tính mô tả schema, không phải chuẩn wire chính thức):

```json
{
  "formatVersion": 1,
  "kdf": {
    "name": "PBKDF2WithHmacSHA256",
    "iterations": 600000,
    "saltBase64": "base64-of-independent-16-byte-salt",
    "outputBits": 256
  },
  "encryption": {
    "name": "AES-256-GCM",
    "nonceBase64": "base64-of-12-byte-nonce",
    "ciphertextAndTagBase64": "base64-of-ciphertext-and-16-byte-tag"
  }
}
```

Trong Java `Cipher.doFinal` với AES-GCM thường trả ciphertext kèm tag trong cùng mảng; định dạng trao đổi phải **quy định cụ thể** tag đặt ở đâu, độ dài bao nhiêu và encoding nào. Không được tùy tiện cắt/bỏ tag.

Thông tin như thuật toán, salt, tham số và nonce **không nhất thiết bí mật**. Tính an toàn dựa vào password có đủ entropy, KDF không dễ đoán quá nhanh, khóa đúng và xác thực dữ liệu đúng. **Bắt buộc giữ metadata cần để giải mã**: mất salt/nonce/format có thể khiến không thể mở lại ciphertext dù vẫn nhớ đúng password.

Một envelope do nguồn không tin cậy cung cấp không được tự động điều khiển thuật toán hoặc chi phí tính toán không giới hạn. Reader phải dùng allowlist phiên bản/thuật toán, kiểm tra kích thước/trường bắt buộc, giới hạn iteration và payload, và ràng buộc metadata an ninh trong AAD/format được xác thực khi phù hợp.

### 3.2. Salt của KDF không phải nonce của AES-GCM

| Giá trị | Dùng tại đâu? | Yêu cầu then chốt | Có thể công khai? |
| --- | --- | --- | --- |
| `S_v` — salt verifier | Password hashing để lưu bộ kiểm tra | Ngẫu nhiên, riêng cho verifier | Có |
| `S_e` — salt encryption KDF | Dẫn xuất khóa từ password cho dữ liệu | Tách biệt mục đích; lưu để dẫn xuất lại | Có |
| `nonce/IV` của GCM | Một lần mã hóa bằng khóa AEAD | **Không được dùng lại cùng (khóa, nonce)** | Có |
| `AAD` | AEAD trước khi mã hóa/giải mã | Cùng byte/ngữ cảnh ở hai phía; được kiểm tra toàn vẹn | Có |
| `authentication tag` | AEAD xác nhận ciphertext/AAD | Kiểm tra trước khi dùng plaintext | Có |
| `pepper` nếu có | Bảo vệ thêm credential/server policy | Bí mật, lưu riêng database | Không |
| `K_e` — encryption key | AES-GCM hoặc key wrap | Bí mật; đúng scope, vòng đời và bảo vệ | Không |

Salt được sinh lại khi tạo **một key derivation context mới**; nonce được sinh/cấp **cho mỗi lần mã hóa với cùng key** theo quy tắc mode. Không được lấy salt rồi dùng luôn làm nonce chỉ vì cả hai đều là byte ngẫu nhiên.

Nếu muốn mã hóa nhiều thông điệp bằng cùng password và salt (tức cùng khóa), phải quản lý **nonce duy nhất với khóa** và giới hạn số bản tin theo khuyến nghị của AEAD; hệ thống có nhiều instance cần cơ chế tránh collision. Nếu thiết kế luôn sinh salt mới cho mỗi envelope, khóa dẫn xuất thường khác; vẫn phải tạo nonce theo đúng yêu cầu của thuật toán và không được giả định salt luôn bảo đảm mọi thuộc tính của nonce.

### 3.3. “Có mật khẩu là giải mã được” chỉ đúng trong phạm vi nào?

Phải phân biệt **access control** với **cryptographic confidentiality**:

- Nếu server tự nắm khóa giải mã dữ liệu (KMS/DEK) và chỉ dùng password verifier để cấp quyền truy cập, **server vẫn có thể giải mã mà không cần password người dùng** theo chính sách.
- Nếu dữ liệu thực sự được mã hóa bằng khóa chỉ người dùng có thể tái tạo, thì server muốn giải mã cần password hoặc một khóa/recovery path có quyền tương đương.
- Nếu ứng dụng gửi password qua backend để derive key, backend có thể nhìn thấy password tại thời điểm xử lý. Không tuyên bố “zero knowledge” chỉ vì mật khẩu không được lưu vào database.
- Nếu chọn client-side encryption, cần xem xét nơi password đi qua, thiết bị nào giữ key, cách chia sẻ và phục hồi, cách lưu dữ liệu ở server.

Không có một mô hình mặc định nào vừa cho phép server đọc mọi dữ liệu vừa bảo đảm server không bao giờ có quyền giải mã. Cần ghi rõ **ai được tin cậy để sở hữu plaintext/key** trong threat model.

## 4. Vì sao không dùng cùng verifier làm khóa mã hóa?

Một thiết kế sai phổ biến:

```text
storedVerifier = PBKDF2(password, salt, cost)
encryptionKey  = storedVerifier
ciphertext     = AES-GCM(encryptionKey, data)
```

Nếu attacker đọc được database chứa `storedVerifier`, họ đã có **chính khóa mã hóa**, không cần biết password. Mục đích password verifier bị đảo ngược: bộ kiểm tra mật khẩu vốn phải có thể lưu để kiểm tra, không được đồng thời trở thành bí mật mở dữ liệu.

Một thiết kế sai khác là lấy `SHA-256(password)` làm verifier và AES key, rồi dùng cùng khóa cho HMAC, cookie signing và mã hóa. Việc tái sử dụng làm tăng phạm vi thiệt hại khi một giá trị bị lộ, gây nhầm vai trò khóa và khó xoay/thu hồi.

**Nguyên tắc:** tách **credential-verification material** khỏi **encryption key material**. Hai mục đích có thể cùng xuất phát từ một password nếu threat model cho phép, nhưng phải dùng **salt, tham số và ngữ cảnh dẫn xuất độc lập**, không bao giờ tái sử dụng giá trị verifier có thể đọc được làm key.

Một cách trình bày:

```text
                         Password P
                        /          \
           verifier purpose        encryption purpose
             salt S_v                  salt S_e
            cost_v                    cost_e
               |                        |
          PasswordHash               Password KDF
               |                        |
          Stored verifier            Secret K_e
           (can be stored)           (not stored as public verifier)
               |                        |
          Compare at login          AES-GCM / wrap DEK
```

Nhiều hệ thống còn không nên dùng password đăng nhập làm password mã hóa dữ liệu. Có thể sử dụng một khóa dữ liệu ngẫu nhiên được quản lý độc lập (KMS/HSM), hoặc một passphrase riêng cho encryption, tùy mục tiêu an ninh và usability.

## 5. Mô hình tốt hơn cho dữ liệu dài hạn: DEK và KEK

Để tránh mã hóa lại toàn bộ dữ liệu khi thay password, thường dùng **envelope encryption**:

- **DEK (Data Encryption Key):** khóa ngẫu nhiên đủ mạnh, sinh độc lập với password; dùng AEAD mã hóa dữ liệu.
- **KEK (Key Encryption Key):** khóa dẫn xuất từ password bằng password KDF, hoặc do hệ thống KMS cấp; dùng **wrap/mã hóa DEK** bằng một cơ chế xác thực.
- **Wrapped DEK:** DEK đã được mã hóa bảo vệ bằng KEK; lưu cùng metadata cho phép unwrap an toàn.

```text
Random DEK ────────────────> AEAD encrypt data ──> ciphertext + data nonce/tag
    |
    +-- AEAD wrap using KEK <──── KDF(password, independent salt, params)
                         |
                   wrappedDEK + wrap nonce/tag + KDF metadata

Open data:
password -> KDF -> KEK -> verify/unwrap DEK -> verify/decrypt ciphertext
```

Khi người dùng **biết cả password cũ và password mới**, ứng dụng có thể dùng password cũ unwrap DEK, sinh KEK mới từ password mới với salt mới, rồi **rewrap đúng DEK**. Ciphertext dữ liệu lớn có thể giữ nguyên nếu DEK không đổi. Cần bảo đảm thao tác update nguyên tử, metadata/version nhất quán và key không bị lộ.

Điểm này không có nghĩa password reset không biết mật khẩu cũ cũng làm được như vậy. Nếu **không còn password cũ hoặc recovery key**, không thể tạo lại KEK cũ để unwrap DEK chỉ từ verifier và ciphertext.

### 5.1. Đổi mật khẩu và reset mật khẩu không giống nhau

| Sự kiện | Verifier đăng nhập | Dữ liệu trực tiếp mã hóa từ password | Dữ liệu qua DEK/KEK |
| --- | --- | --- | --- |
| Đổi mật khẩu, biết password cũ | Tạo verifier mới | Phải giải mã bằng khóa cũ rồi mã hóa lại bằng khóa mới | Unwrap DEK bằng KEK cũ, rewrap bằng KEK mới |
| Quên password cũ, chỉ có quyền reset account | Có thể thay verifier sau quy trình recovery | **Không thể tự giải mã dữ liệu cũ** nếu không có recovery | Không unwrap được DEK nếu không có recovery path |
| Có recovery key được cấp trước | Reset verifier theo chính sách | Có thể giải mã nếu recovery key thực sự có đường mở dữ liệu | Có thể unwrap/rewrap DEK bằng recovery key/wrapped copy |
| Server giữ DEK trong KMS | Reset verifier độc lập | Phụ thuộc kiến trúc | Server có thể rewrap theo quyền KMS, nhưng không còn mô hình chỉ người dùng biết key |

**Reset account thành công không đồng nghĩa với khôi phục được dữ liệu mã hóa**. UX của sản phẩm cần thông báo rõ trước khi người dùng dựa vào password-only encryption. Nếu có recovery key, escrow, hoặc tổ chức có quyền khôi phục, phải nói rõ ai nắm quyền tương đương giải mã.

## 6. Java 17+: thực hiện cả hai luồng một cách tách biệt

Ví dụ sau sử dụng đúng **các API có sẵn trong Java 17+**:

- `SecretKeyFactory` + `PBEKeySpec` với `PBKDF2WithHmacSHA256` làm KDF có salt và chi phí.
- `SecureRandom` để tạo salt và nonce.
- `MessageDigest.isEqual` để so sánh verifier đã dẫn xuất.
- `Cipher` `AES/GCM/NoPadding` để mã hóa xác thực và kiểm tra tag trước khi lấy plaintext.
- **Hai salt độc lập:** một cho password verifier, một cho khóa mã hóa.

Đây là bản minh họa **PBKDF2 dùng để quan sát hai mục đích**. Với hệ thống thiết kế mới, hãy đánh giá Argon2id qua thư viện được kiểm toán và quy định hiện hành; không suy ra con số iteration trong demo là tham số an toàn vĩnh viễn. Mật khẩu trong code là dữ liệu demo công khai, **không phải secret để tái sử dụng**.

Tệp `PasswordVerifierVsEncryptionDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class PasswordVerifierVsEncryptionDemo {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 600_000;
    private static final byte[] CONTEXT =
            "private-note:v1".getBytes(StandardCharsets.UTF_8);

    static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    static byte[] derive(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    static boolean verify(char[] candidate, byte[] salt, byte[] storedVerifier)
            throws Exception {
        byte[] calculated = derive(candidate, salt);
        try {
            return MessageDigest.isEqual(storedVerifier, calculated);
        } finally {
            Arrays.fill(calculated, (byte) 0);
        }
    }

    static byte[] encrypt(char[] password, byte[] salt, byte[] nonce, byte[] plaintext)
            throws Exception {
        byte[] derivedKey = derive(password, salt);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derivedKey, "AES"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD(CONTEXT);
            return cipher.doFinal(plaintext);
        } finally {
            Arrays.fill(derivedKey, (byte) 0);
        }
    }

    static byte[] decrypt(char[] password, byte[] salt, byte[] nonce, byte[] ciphertext)
            throws Exception {
        byte[] derivedKey = derive(password, salt);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(derivedKey, "AES"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD(CONTEXT);
            return cipher.doFinal(ciphertext);
        } finally {
            Arrays.fill(derivedKey, (byte) 0);
        }
    }

    public static void main(String[] args) throws Exception {
        char[] password = "example demo password".toCharArray();
        char[] incorrect = "incorrect".toCharArray();

        // Mục đích A: lưu verifier; không cần giải mã mật khẩu.
        byte[] verifierSalt = randomBytes(16);
        byte[] storedVerifier = derive(password, verifierSalt);
        System.out.println("Correct login: "
                + verify(password, verifierSalt, storedVerifier));
        System.out.println("Wrong login: "
                + verify(incorrect, verifierSalt, storedVerifier));

        // Mục đích B: mã hóa rồi khôi phục plaintext với password.
        // Salt này độc lập với verifierSalt.
        byte[] fileSalt = randomBytes(16);
        byte[] nonce = randomBytes(12);
        byte[] ciphertext = encrypt(password, fileSalt, nonce,
                "Confidential note".getBytes(StandardCharsets.UTF_8));
        byte[] recovered = decrypt(password, fileSalt, nonce, ciphertext);
        System.out.println("Decrypted: " + new String(recovered, StandardCharsets.UTF_8));

        boolean wrongPasswordRejected = false;
        try {
            decrypt(incorrect, fileSalt, nonce, ciphertext);
        } catch (AEADBadTagException expected) {
            wrongPasswordRejected = true;
        }
        System.out.println("Wrong decryption password rejected: "
                + wrongPasswordRejected);

        byte[] tampered = ciphertext.clone();
        tampered[0] ^= 1;
        boolean tamperingRejected = false;
        try {
            decrypt(password, fileSalt, nonce, tampered);
        } catch (AEADBadTagException expected) {
            tamperingRejected = true;
        }
        System.out.println("Tampering rejected: " + tamperingRejected);

        Arrays.fill(password, '\0');
        Arrays.fill(incorrect, '\0');
        Arrays.fill(storedVerifier, (byte) 0);
    }
}
```

Biên dịch và chạy:

```bash
javac PasswordVerifierVsEncryptionDemo.java
java PasswordVerifierVsEncryptionDemo
```

**Stdout** (các salt, nonce và ciphertext được sinh ngẫu nhiên nhưng không in ra, nên các dòng kết quả vẫn như nhau):

```text
Correct login: true
Wrong login: false
Decrypted: Confidential note
Wrong decryption password rejected: true
Tampering rejected: true
```

### 6.1. Giải thích chi tiết từng phần

**Phần verifier:**

```java
byte[] verifierSalt = randomBytes(16);
byte[] storedVerifier = derive(password, verifierSalt);
boolean ok = verify(password, verifierSalt, storedVerifier);
```

`storedVerifier` là giá trị **dùng để so sánh khi xác thực**. Nếu lưu vào database, phải lưu kèm `verifierSalt`, `ITERATIONS` và id KDF. Dữ liệu cần giữ bí mật trong quá trình xử lý là password; record verifier/salt không được xem là tài liệu cho phép giải mã, nhưng vẫn là dữ liệu bảo mật nhạy cảm vì có thể hỗ trợ tấn công offline.

**Phần encryption:**

```java
byte[] fileSalt = randomBytes(16);
byte[] nonce = randomBytes(12);
byte[] ciphertext = encrypt(password, fileSalt, nonce, plaintext);
byte[] restored = decrypt(password, fileSalt, nonce, ciphertext);
```

Trong thực tế phải lưu ít nhất `fileSalt`, nonce, ciphertext kèm tag, profile version/KDF params và ngữ cảnh AAD cần thiết. Demo dùng AAD cố định `private-note:v1` để ràng buộc **kiểu dữ liệu/phiên bản**; khi thiết kế đa người dùng/tệp, nên ràng buộc cả id/tenant/resource với AAD theo schema rõ ràng và kiểm tra quyền tương ứng.

**Tại sao cùng password có thể cho ra hai giá trị KDF khác nhau?** `verifierSalt` và `fileSalt` được tạo độc lập. Ngay cả khi tình cờ cùng password, dữ liệu đầu vào KDF khác nên thường tạo kết quả khác. Trong thiết kế nâng cao nên có **domain separation** rành mạch cho verifier và encryption, chứ không chỉ trông chờ các call site dùng nhầm hay không. Với password hashing và password-based encryption, cũng có thể dùng các thuật toán/profile khác nhau.

**Xử lý sai password và dữ liệu bị sửa:** AEAD khi kiểm tra tag không khớp sẽ từ chối. Hệ thống không nên đưa plaintext được trả từ hoạt động chưa xác thực vào nghiệp vụ. Ngoài sai password, nguyên nhân tag sai còn có thể do dữ liệu, metadata, nonce hay AAD bị thay đổi. Không dùng ngoại lệ để tiết lộ cho client rằng phần nào của ciphertext đã bị chỉnh sửa.

### 6.2. Những giới hạn cố ý của demo

- Mật khẩu được viết trực tiếp trong source chỉ nhằm chạy lại bài; ứng dụng thật cần nhận qua kênh an toàn, không log và không hardcode.
- `PBKDF2WithHmacSHA256` với 600.000 lượt là **giá trị minh họa**, chưa thay thế benchmark/kế hoạch tham số hiện hành theo môi trường và chính sách. Thời gian chạy phụ thuộc CPU, quy mô dịch vụ và giới hạn DoS.
- `derive()` dùng một profile cố định, không serialize metadata. Sản phẩm thật phải lưu/version hóa thuật toán, cost, salt, nonce, AAD policy và ciphertext; áp kiểm tra input size và giới hạn cost khi đọc file từ bên ngoài.
- Mỗi lần mã hóa của demo sinh salt/nonce mới. Khi tái sử dụng một khóa dẫn xuất cho nhiều bản tin, phải có cơ chế cấp nonce duy nhất phù hợp với mode và quy mô lưu lượng. Việc AES-GCM nonce không được tái sử dụng với cùng khóa là yêu cầu riêng, **không được xem như được giải quyết vì có salt ở verifier**.
- Mảng `byte[]` được cố gắng xóa sau khi dùng và `PBEKeySpec.clearPassword()` được gọi; điều này **không bảo đảm tuyệt đối** mọi bản copy bên trong JCE/JVM được xóa ngay. Tránh tạo nhiều bản sao `String` của password trong code production khi có lựa chọn phù hợp.
- `SecretKeySpec` và `Cipher` có thể tạo bản sao key nội bộ; không coi Java heap làm vùng lưu khóa an toàn phần cứng. Với yêu cầu cao, cần xem xét KMS/HSM hoặc key service có chính sách phù hợp.
- Demo mã hóa dữ liệu nhỏ trực tiếp bằng khóa từ password để chỉ ra cơ chế. Dữ liệu dài hạn thường nên dùng **random DEK và KEK** như phần 5 để dễ quản lý key và hỗ trợ rotation.

## 7. So sánh hai cách lưu để tránh nhầm data model

### 7.1. Account credential record

```text
users
  accountId
  credentialVersion
  passwordHashScheme
  passwordHashParameters
  passwordHashSalt
  passwordVerifier
  passwordChangedAt
```

Record này phục vụ **xác thực tài khoản**. Không có `nonce`, `ciphertext` hay `authenticationTag` vì chúng không phải thành phần của password verifier. Một hệ thống có thể lưu verifier dưới dạng chuỗi tự mô tả của thư viện password hash thay vì tách trường; schema lựa chọn phải nhất quán.

Việc có `passwordChangedAt` không tự động tạo quyền truy cập tệp được mã hóa bằng password trước đó. Nếu password thay đổi, logic encryption có kế hoạch chuyển khóa riêng.

### 7.2. Password-encrypted file envelope

```text
encryptedFiles
  ownerId
  fileFormatVersion
  kdfAlgorithm
  kdfCostParameters
  encryptionSalt
  aeadAlgorithm
  nonce
  ciphertextAndTag
  authenticatedContext
```

Record này phục vụ **khôi phục dữ liệu**. `ownerId` và `authenticatedContext` là ngữ cảnh được ràng buộc vào format/AAD khi cần, không đồng nghĩa user đã đăng nhập là có quyền. Phía nhận phải kiểm tra authorization riêng.

Không có password verifier ở đây vẫn có thể giải mã bằng thử password rồi xem tag có đúng không. Trên thực tế, chính authentication tag của ciphertext tạo ra một kiểm tra offline cho mỗi password ứng viên: **encryption không xóa được nguy cơ đoán password nếu password yếu**.

### 7.3. Envelope encryption có wrapped DEK

```text
encryptedFiles
  ownerId
  fileFormatVersion
  dataCiphertextAndTag
  dataNonce
  wrappedDEK
  wrappingAlgorithm
  wrapNonce
  passwordKdfSalt
  passwordKdfCostParameters
  versionedAAD
```

Chủ thể có password/recovery key tương ứng có thể unwrap DEK rồi giải mã dữ liệu. Nếu hỗ trợ nhiều thiết bị hoặc chia sẻ nhiều người, có thể lưu **nhiều wrapped copy của cùng DEK** cho các người nhận hợp lệ, nhưng việc phân phối và thu hồi quyền phải được thiết kế cẩn thận. Thu hồi quyền với dữ liệu đã được người nhận tải/giải mã trước đó không thể đảm bảo chỉ bằng xóa một wrapped key.

## 8. Threat model và những trường hợp phải kiểm thử

### 8.1. Đối thủ chỉ có khả năng thử login online

- **Verifier:** áp rate limit, kiểm soát session, phát hiện credential stuffing, MFA theo nguy cơ, thông báo lỗi phù hợp. Password hashing có chi phí thích hợp nhưng phải có giới hạn tài nguyên để không tạo DoS.
- **Dữ liệu PBE:** nếu có API “thử password để decrypt”, API đó cũng có thể trở thành oracle thử đoán online. Cần giới hạn request và quyền truy cập, không trả thông tin giúp phân biệt lỗi cụ thể quá mức.

### 8.2. Đối thủ lấy được database hoặc file mã hóa

- **Verifier bị lộ:** có thể đoán offline theo `password + salt + cost`. Salt làm bảng tra dùng lại khó hơn; cost tăng công việc; không bảo đảm mật khẩu yếu tránh bị đoán.
- **Ciphertext PBE bị lộ:** có thể đoán offline bằng cách dẫn xuất khóa rồi thử AEAD tag, ngay cả khi không có một trường verifier tường minh.
- **Random DEK được wrap bởi KEK từ password:** nếu file chứa wrapped DEK và tag đủ kiểm tra, đối thủ cũng có thể thử password offline để unwrap. Random DEK giúp quản lý vòng đời, nhưng không tự làm passphrase yếu mạnh lên.

Không nên quảng cáo “ciphertext có AES-256 nên mật khẩu yếu cũng an toàn”. Khóa hiệu dụng từ password phụ thuộc vào **entropy của password**, chi phí KDF và mức độ kẻ tấn công có thể thử.

### 8.3. Đối thủ đọc được verifier lẫn encrypted file

Nếu hệ thống dùng **storedVerifier làm key**, attacker có thể giải mã ngay khi đọc DB. Nếu verifier và encryption material được tách đúng, attacker vẫn phải vượt qua các cơ chế riêng; tuy nhiên nếu cùng password được dùng, việc đoán thành công password từ một trong hai tài sản có thể làm lộ tài sản còn lại. Tốt nhất cân nhắc secret độc lập cho các mục đích hoặc dùng quản lý khóa server/KMS phù hợp threat model.

### 8.4. Password được đổi

Kiểm thử cả:
1. Biết password cũ, đổi password mới: verifier cũ ngừng nhận; encrypted data vẫn mở được theo kế hoạch re-encrypt/rewrap.
2. Reset mà không biết password cũ: login hoạt động, nhưng dữ liệu cũ phải **được báo mất quyền truy cập** hoặc có recovery path đã định; không hứa hẹn tự giải mã được.
3. Rotate KDF parameters: verifier có thể nâng cấp vào lúc login thành công; encrypted file phải được migrate/re-encrypt/rewrap theo quy trình thích hợp, không chỉ sửa metadata iterations rồi mong decrypt vẫn được.
4. Di chuyển môi trường/thư viện: bảo toàn salt, nonce, KDF id, encoding, iterations, tag format, AAD. Cần golden vectors để tránh lỗi interop.

## 9. Checklist thực hành thiết kế

### Khi thiết kế password verifier

- Chọn password-hashing scheme chuyên dụng có thư viện an toàn và chính sách tham số hiện hành; ưu tiên Argon2id khi phù hợp, PBKDF2 khi cần tương thích/FIPS theo chuẩn áp dụng.
- Sinh salt độc lập cho mỗi verifier, lưu salt + algorithm/version + cost + verifier. Không lưu password, không lưu reversible encryption của password.
- Thực hiện verification qua API chuyên dụng hoặc so sánh byte phù hợp sau khi dẫn xuất cùng tham số. Giới hạn đầu vào và chi phí, không cho dữ liệu client tùy ý chọn thuật toán yếu.
- Áp chính sách đăng nhập: rate limiting, MFA, bảo vệ reset token, session revocation, audit và kiểm tra danh tính/quyền.
- Có chiến lược rehash/upgrade sau khi người dùng login thành công, lưu ý migration không phá tài khoản.

### Khi thiết kế password-based encryption

- Xác định ai thực sự cần giải mã, ở đâu password xuất hiện, ai sở hữu key, có cần server/recovery access không.
- Chọn password KDF và cost phù hợp, salt ngẫu nhiên riêng cho encryption context. Dẫn xuất key length đúng yêu cầu thuật toán (ví dụ AES-256 cần 32 byte key).
- Mã hóa bằng AEAD đã được kiểm chứng, nonce duy nhất theo khóa; bảo vệ metadata/context trong AAD và kiểm tra tag trước khi dùng plaintext.
- Lưu tất cả tham số cần để giải mã (salt, KDF id/cost, nonce, ciphertext, tag, version), kiểm tra file/envelope format có giới hạn và policy.
- Nếu dữ liệu dài hạn/nhiều file, cân nhắc random DEK với KEK được dẫn xuất từ password, thiết kế rewrap và recovery trước khi sản phẩm có người dùng.
- Quy định rõ quá trình đổi/reset password, quên password, mất metadata/backup, rotate key, chia sẻ và thu hồi truy cập.
- Không lưu verifier như encryption key; không dùng password bytes trực tiếp làm AES key; không dùng `AES/ECB/PKCS5Padding` hay mã hóa không xác thực cho dữ liệu cần bảo vệ khỏi sửa đổi.

## 10. Những ngộ nhận nguy hiểm

| Nhận định sai | Thực tế |
| --- | --- |
| “Đã hash password thì muốn cũng decrypt được” | Password verifier không có phép giải mã khôi phục password; chỉ có thể thử đoán |
| “Muốn đọc lại password để login nên phải mã hóa password” | Login chỉ cần verify, không cần đọc lại password; không lưu reversible password |
| “Verifier PBKDF2 là 256 bit, dùng làm AES-256 key luôn” | Nếu DB lộ verifier thì attacker có ngay key; phải tách mục đích/secret |
| “Salt là bí mật, nên phải giấu cùng với key” | Salt thường công khai; bí mật thực sự là password/secret và key |
| “Salt mới nghĩa là không cần nonce GCM” | Nonce là tham số AEAD có quy tắc duy nhất theo key, khác vai trò KDF salt |
| “AES-GCM sai password sẽ cho ra rác, mình tự kiểm tra UTF-8 là đủ” | Phải xác minh tag; AEAD fail thì từ chối, không xử lý plaintext |
| “Mã hóa bằng password nên quên password có thể gửi email reset là đọc lại dữ liệu” | Reset verifier không khôi phục KEK/DEK cũ nếu không có recovery path |
| “Hash/password KDF tăng entropy của password” | KDF chỉ tăng chi phí mỗi lần thử; entropy đầu vào không tăng |
| “Pepper thay thế salt và password hashing” | Pepper là lớp bổ sung với vòng đời riêng, không thay salt/hash |
| “Lộ ciphertext thì an toàn tuyệt đối vì dùng AES-256” | Đối thủ có thể đoán password offline rồi kiểm tra tag nếu password yếu |
| “Đổi iterations trong envelope là đủ để migrate” | KDF output thay đổi; phải re-encrypt hoặc rewrap theo quy trình |
| “Dùng cùng password thì verifier và encryption bắt buộc cùng salt/key” | Phải tách context và key, có thể dùng salt riêng hoặc secret riêng |
| “Java `String` xóa password khỏi RAM khi đặt null” | Memory management có thể giữ bản sao; dùng API phù hợp, tránh log và giảm thời gian tồn tại của secret |
| “Password đúng là đủ quyền xem mọi file” | Xác thực không thay authorization đối với owner, tenant, resource |

## 11. Bài tập tự kiểm tra

**Câu 1.** Hệ thống chỉ cần xác định password login có đúng không. Nên lưu gì?

**Trả lời:** Lưu **password verifier** do password-hashing scheme tạo ra, kèm salt, cost, version/algorithm. Không cần lưu password hay dữ liệu mã hóa để có thể “giải mã password”.

**Câu 2.** Một người có file đã mã hóa bằng khóa dẫn xuất từ password. Tại sao họ cần lưu salt và nonce?

**Trả lời:** Salt KDF là input để tái tạo đúng key; nonce là input AEAD để giải mã/verify tag. Mất metadata cần thiết có thể khiến không mở lại file dù password đúng.

**Câu 3.** Nếu `storedVerifier = PBKDF2(password, salt)`, có nên làm `AES-GCM(storedVerifier, data)` không?

**Trả lời:** Không. Verifier được lưu nhằm xác minh và có thể lộ khi database bị rò rỉ; nếu cũng dùng làm key, attacker đọc verifier có thể giải mã trực tiếp. Dùng key material riêng và quản lý đúng scope.

**Câu 4.** Vì sao PBE có thể bị tấn công đoán password offline dù không lưu verifier?

**Trả lời:** Đối thủ có salt, params, nonce, ciphertext+tag có thể thử password ứng viên, derive key, rồi xác minh AEAD tag. Password yếu có thể bị đoán; KDF chậm làm tăng chi phí nhưng không xóa bỏ khả năng thử.

**Câu 5.** Người dùng quên password đăng nhập nhưng chứng minh được quyền tài khoản qua recovery email. Có thể reset login không? Có luôn đọc được file mã hóa bằng password cũ không?

**Trả lời:** Có thể reset verifier theo quy trình tài khoản; không tự giải mã file cũ nếu không có password cũ, DEK escrow/recovery key hoặc đường khôi phục khác đã thiết kế trước.

**Câu 6.** Tại sao đổi password với KEK/DEK có thể tiết kiệm hơn mã hóa trực tiếp?

**Trả lời:** Có thể unwrap một DEK ngẫu nhiên bằng KEK cũ rồi rewrap bằng KEK mới, giữ nguyên ciphertext dữ liệu lớn. Nhưng thao tác vẫn cần quyền truy cập DEK qua password cũ hoặc recovery path.

**Câu 7.** Trong ví dụ Java, tại sao sai password hoặc sửa byte ciphertext đều cho `AEADBadTagException`?

**Trả lời:** Cả hai có thể khiến tag không hợp lệ; AEAD chỉ xác nhận input/khóa/nonce/AAD tương thích với tag hoặc từ chối, không phân loại nguyên nhân một cách đáng tin cho ứng dụng.

**Câu 8.** Mã hóa dữ liệu dưới KMS server-controlled và lưu password verifier cho login có phải mô hình password-only encryption không?

**Trả lời:** Không. Server có thể mở data qua KMS mà không cần biết password người dùng; password chỉ là một phương tiện xác thực quyền truy cập, không phải secret duy nhất giải mã data.

## 12. Ghi nhớ ranh giới kiến trúc

```text
                    PASSWORD
                       |
           +-----------+-------------+
           |                         |
       XÁC THỰC                   MỞ DỮ LIỆU
           |                         |
  Password hashing              Password KDF
  + verifier salt               + encryption salt
  + cost parameters             + cost parameters
           |                         |
      STORE VERIFIER              SECRET KEK/KEY
      (cannot decrypt)                 |
           |                     AEAD / unwrap DEK
      COMPARE AT LOGIN                 |
           |                      CIPHERTEXT
      AUTHENTICATION                   |
           |                      RECOVER PLAINTEXT
   then AUTHORIZATION               verify tag
```

**Password verifier trả lời “mật khẩu này có đúng không?”; password-derived encryption key trả lời “với mật khẩu này có thể mở bản mã được không?”.** Cả hai dùng mật khẩu nhưng phục vụ mục tiêu, hồ sơ lưu trữ và cơ chế khôi phục khác nhau. Thiết kế vững chắc tách vai trò khóa, lưu metadata đúng mục đích, dùng thuật toán chuẩn và nói rõ hậu quả của việc quên/reset mật khẩu trước khi hệ thống lưu dữ liệu thực tế.

### Tài liệu tham khảo

- OWASP Password Storage Cheat Sheet — password hashing, salt, pepper, tham số chi phí: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- RFC 9106 — Argon2 Memory-Hard Function: https://www.rfc-editor.org/rfc/rfc9106
- RFC 8018 — PKCS #5 Password-Based Cryptography Specification, PBKDF2: https://www.rfc-editor.org/rfc/rfc8018
- NIST SP 800-132 — Recommendation for Password-Based Key Derivation, Part 1: https://csrc.nist.gov/pubs/sp/800/132/final
- NIST SP 800-38D — Galois/Counter Mode (GCM) và IV/nonce: https://csrc.nist.gov/pubs/sp/800/38/d/final
- Java SE 17 `PBEKeySpec`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html
- Java SE 17 `SecretKeyFactory`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/SecretKeyFactory.html
- Java SE 17 `Cipher`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html
- Java SE 17 `MessageDigest`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="password-key-derivation-002">7.2 Vì sao băm nhanh một lần không đủ cho mật khẩu có entropy thấp</a>

<details>
<summary>Xem chi tiết</summary>

# 7.2 Vì sao băm nhanh một lần không đủ cho mật khẩu có entropy thấp

## Mục tiêu

Hiểu vì sao `SHA-256(password)` dù dùng thuật toán hash mạnh vẫn **không phù hợp để lưu mật khẩu do con người chọn**; phân tích tấn công đoán mật khẩu offline, entropy thấp, salt, chi phí xử lý và lựa chọn password hashing scheme. Sau bài, bạn có thể dùng Java 17 để chứng minh giới hạn của hash nhanh và tạo/verifier bản ghi PBKDF2 có salt, iterations và so sánh an toàn.

## 1. Vì sao “SHA-256 rất mạnh” chưa phải câu trả lời?

SHA-256 là **cryptographic hash function** có đầu ra cố định 256 bit (32 byte, 64 ký tự hex). Đây là primitive tốt cho toàn vẹn dữ liệu và nhiều giao thức mật mã. Tuy nhiên, hash function được thiết kế để tính **hiệu quả**, còn lưu mật khẩu cần làm cho **mỗi lần thử password** tốn tài nguyên.

Xét:

```text
password = một bí mật do người dùng chọn

stored_hash = SHA-256(password)

Để xác minh:
SHA-256(password_đăng_nhập) == stored_hash ?
```

Nếu database bị rò rỉ, attacker có thể lấy `stored_hash` và tự thử password trên máy của họ. Không cần “đảo ngược” SHA-256. Họ chỉ cần chọn ứng viên `p`, tính `SHA-256(p)`, so sánh với hash bị lộ. Phép tính càng nhanh, họ thử được càng nhiều ứng viên trong cùng thời gian.

Không có lỗi mật mã mới trong SHA-256 ở đây: **bài toán yếu nằm ở phân phối password đầu vào và cách sử dụng**. SHA-512 hay SHA3-256 chạy một lần không giải quyết được bản chất đó.

| SHA-256 phù hợp cho | Kho lưu password cần |
| --- | --- |
| Digest nhanh, xác định, hỗ trợ nhiều giao thức | Chi phí **có chủ đích** cho từng lần đoán |
| Kháng tìm tiền ảnh trên dữ liệu đủ khó đoán | Chống tấn công danh sách password phổ biến |
| Đầu ra luôn 256 bit | Không đánh đồng độ dài hash với entropy đầu vào |
| Không có khái niệm salt/work factor bắt buộc | Salt riêng, cost điều chỉnh, version để nâng cấp |

**Thông điệp chính:** mật khẩu dễ đoán không tự trở thành bí mật 256 bit khi được băm thành 256 bit.

## 2. Entropy thấp là điều gì?

**Entropy** mô tả mức độ khó dự đoán một bí mật, dựa trên **cách nó được sinh/chọn** và phân phối xác suất. Nếu mật khẩu được lấy đồng đều ngẫu nhiên từ `N` lựa chọn độc lập, số bit thông tin xấp xỉ:

```text
H ≈ log2(N) bit
```

Ví dụ: một mã PIN gồm **6 chữ số, sinh đều ngẫu nhiên** có `10^6` khả năng, tương ứng khoảng **19,93 bit**, dù `SHA-256(PIN)` có 256-bit output.

Người dùng thường không chọn ngẫu nhiên đồng đều. Họ có thể dùng từ phổ biến, năm sinh, tên thú cưng, bàn phím, chuỗi số, mẫu thêm dấu `!` hoặc password đã bị lộ trên dịch vụ khác. Các ứng viên không đồng xác suất, nên attacker có thể ưu tiên **mẫu có xác suất cao** trước.

- Một password dài vẫn có thể yếu nếu là câu rất phổ biến hoặc mẫu dễ đoán.
- Nhiều ký tự đặc biệt không chứng minh entropy cao nếu mọi người đều thêm chúng theo mẫu.
- Passphrase nhiều từ **được lấy ngẫu nhiên độc lập** thường khó đoán hơn câu người dùng tự nghĩ dựa vào ngữ cảnh quen thuộc.
- Password manager tạo secret ngẫu nhiên độc lập có thể cung cấp entropy cao; password reuse trên nhiều dịch vụ làm mất lợi thế khi có vụ rò rỉ.

Đừng tính “entropy chính xác” của mật khẩu người dùng chỉ bằng số chữ cái, chữ số và dấu. Entropy phụ thuộc **phân phối lựa chọn**, không phải số ký tự hash hay vẻ phức tạp bên ngoài.

### 2.1 Kháng tiền ảnh (preimage resistance) có bị phá không?

**Không.** Kháng tiền ảnh mô tả việc tìm đầu vào cho digest nói chung khó đến mức nào khi đầu vào có không gian đủ lớn. Với mật khẩu do con người chọn, attacker lại làm một việc hẹp hơn:

```text
candidate_1 -> SHA-256 -> so sánh với hash bị lộ
candidate_2 -> SHA-256 -> so sánh với hash bị lộ
candidate_3 -> SHA-256 -> so sánh với hash bị lộ
...
```

Nếu ứng viên thứ hai chính là password, attacker đã tìm được password mà không cần giải phương trình ngược của SHA-256. Password entropy thấp làm **không gian phỏng đoán hữu ích** nhỏ và dễ ưu tiên.

## 3. Mô hình tấn công offline sau khi database bị lộ

Giả sử một bản sao database gồm các bản ghi:

```text
account_id | password_hash
-----------+-------------------------------------
user_a     | SHA-256(password_A)
user_b     | SHA-256(password_B)
```

Attacker lấy được bảng này. Từ đó họ không cần gửi request đăng nhập. Họ tự chạy thuật toán trên phần cứng mình kiểm soát:

```text
Bản sao password hashes đã bị lộ
                |
                v
Tập ứng viên mật khẩu phổ biến,
password từng rò rỉ, biến thể có quy luật...
                |
                v
Tính SHA-256(candidate) offline
                |
                v
So với hash trong database
        /                   \
     trùng                 không trùng
       |                       |
 tìm password          thử candidate khác
```

Những yếu tố khiến attacker thuận lợi:

1. **Không có giới hạn request từ hệ thống thật**: CAPTCHA, khóa tài khoản, rate limit ở login endpoint không chặn các phép thử offline.
2. **Tính song song rất lớn**: các hash nhanh được tính hiệu quả trên CPU/GPU và phần cứng chuyên dụng.
3. **Dữ liệu từ các lần rò rỉ trước**: dictionary chứa các password có xác suất cao, không cần duyệt đều tất cả chuỗi có thể.
4. **Cùng một password tạo cùng hash khi không có salt**: attacker có thể phát hiện trùng lặp và dùng kết quả tính toán cho nhiều tài khoản.

Mô hình thời gian đơn giản:

```text
thời_gian_đoán ≈ số_ứng_viên_cần_thử / tốc_độ_thử
```

Đây là quan hệ khái niệm; tốc độ thực tế phụ thuộc phần cứng và thuật toán. Không nên công bố “attacker chắc chắn crack trong X giây” khi không có dữ liệu đo. **Chi phí của mỗi phép thử** là đòn bẩy mà hệ thống có thể tác động bằng password KDF có work factor.

## 4. Hash nhanh không có salt, có salt và password KDF

### 4.1 Không có salt

```text
stored = SHA-256(password)
```

Các tài khoản cùng password có cùng digest. Một bảng tra cứu/dictionary tính trước có thể dùng lại. Việc lấy SHA-512 một lần hoặc đổi hex sang Base64 không thay đổi bản chất này.

### 4.2 Có salt nhưng vẫn hash nhanh

```text
salt = random_16_bytes()
stored = SHA-256(salt || password)
database = (salt, stored)
```

**Salt** là byte ngẫu nhiên **riêng cho mỗi bản ghi** (chẳng hạn 16 byte bằng `SecureRandom`), được lưu cùng verifier; **không cần bí mật**.

Salt mang lại lợi ích quan trọng: những tài khoản cùng password thường có hash khác nhau; giảm hiệu quả các bảng tra cứu hash dựng sẵn và việc tái sử dụng chung công sức tính cho nhiều account.

Nhưng attacker biết `salt` khi database bị lộ. Họ vẫn tính:

```text
SHA-256(salt_của_tài_khoản || candidate)
```

cho từng ứng viên. Đây vẫn là một SHA-256 **rất nhanh**. Vì vậy **salt là cần thiết nhưng chưa đủ**.

### 4.3 Salt kết hợp password hashing scheme chuyên dụng

```text
stored = PasswordKDF(password, unique_salt, work_factor)
record = (algorithm, version, parameters, salt, stored)
```

Giờ mỗi phép thử phải chạy công việc lặp lại hoặc tốn bộ nhớ theo tham số, thay vì một SHA-256. Attacker vẫn có thể đoán, nhưng **mỗi lần đoán trở nên đắt hơn**. Salt giúp chống precomputation và phân biệt bản ghi; KDF làm tăng chi phí cho từng candidate. Hai vai trò bổ sung nhau.

| Cách làm | Chống bảng tra cứu dựng sẵn chung? | Làm chi phí mỗi lần thử cao? | Phù hợp password storage? |
| --- | --- | --- | --- |
| `SHA-256(password)` | Không | Không | **Không** |
| `SHA-256(salt || password)` | Có, nếu salt riêng | Không đáng kể | **Không** |
| `PBKDF2(password, salt, iterations)` | Có | Có, theo số vòng | Có, với cấu hình phù hợp |
| `Argon2id(password, salt, m, t, p)` | Có | Có, CPU + memory-hard | Có, thường ưu tiên cho hệ thống mới |

**Không dùng chung một salt tĩnh toàn hệ thống**, không lấy username/time/PID làm salt thay CSPRNG và không cố giấu salt trong source code. Salt khác với **pepper** là bí mật bổ sung được bảo vệ ngoài database.

## 5. Thực hành 1: chứng minh salt không dừng offline guessing

Ví dụ này sử dụng **dữ liệu tự tạo để học tập**. Nó dùng SHA-256 một lần, hai salt **cố định để stdout có thể lặp lại**. Không sao chép cách sinh salt hay cách lưu verifier này sang production.

Tạo file **`FastHashGuessDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

public class FastHashGuessDemo {
    private static byte[] sha256(byte[] salt, String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        if (salt != null) {
            digest.update(salt);
        }
        return digest.digest(text.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        String example = "classroom-only";
        byte[] saltA = HexFormat.of().parseHex("00112233445566778899aabbccddeeff");
        byte[] saltB = HexFormat.of().parseHex("ffeeddccbbaa99887766554433221100");

        System.out.println("unsalted repeat equals: " +
                Arrays.equals(sha256(null, example), sha256(null, example)));
        System.out.println("different salts change digest: " +
                !Arrays.equals(sha256(saltA, example), sha256(saltB, example)));

        byte[] stolenHash = sha256(saltA, example);
        String[] dictionary = {"welcome", "password1", "classroom-only", "letmein"};
        int attempts = 0;
        boolean found = false;
        for (String guess : dictionary) {
            attempts++;
            if (MessageDigest.isEqual(stolenHash, sha256(saltA, guess))) {
                found = true;
                break;
            }
        }
        System.out.println("offline guess matched: " + found);
        System.out.println("candidate attempts: " + attempts);
    }
}
```

Biên dịch/chạy trên Java 17 trở lên:

```bash
javac --release 17 FastHashGuessDemo.java
java FastHashGuessDemo
```

Đầu ra đã kiểm chứng:

```text
unsalted repeat equals: true
different salts change digest: true
offline guess matched: true
candidate attempts: 3
```

Giải thích kết quả:

- `SHA-256` là hàm xác định, cùng input cho cùng output.
- Hai salt khác nhau tạo input khác nhau nên hai digest khác nhau; salt chống việc nhìn hash giống nhau và tra bảng tính trước.
- Attacker giả lập biết **salt công khai** và hash, rồi tính lại từng ứng viên. Ứng viên thứ ba khớp, dù hash có salt.
- `MessageDigest.isEqual` có ích khi so sánh byte, nhưng **không** làm SHA-256 một lần thành thuật toán password storage.

Điều này giải thích tại sao phải có **cả salt lẫn cơ chế làm chậm phép thử**, không chỉ thêm một chuỗi vào input SHA-256.

## 6. Vì sao phải dùng password KDF có cost?

**Password-based key derivation / password hashing scheme** nhận password, salt và tham số độ khó. Nó cố ý tiêu tốn công việc khi sinh verifier. Người dùng hợp pháp thường cần một lần verify; attacker muốn thử rất nhiều password, nên chi phí sẽ nhân lên.

```text
Đăng ký:
password + salt_mới + algorithm/params
               |
               v
     PasswordKDF(...)
               |
               v
lưu {scheme, version, params, salt, verifier}

Xác minh:
password nhập + salt/params lưu trữ
               |
               v
     cùng PasswordKDF(...)
               |
               v
so sánh verifier -> accept / reject
```

Không nên tự tạo “SHA-256 lặp 1.000 lần” làm sơ đồ riêng: các thuật toán password KDF có thiết kế/kiểm chứng, tham số, định dạng và các chi tiết an toàn cần tuân theo.

### 6.1 Các thuật toán quan trọng

| Phương án | Work factor | Đặc tính | Lưu ý |
| --- | --- | --- | --- |
| **Argon2id** | Bộ nhớ `m`, số passes `t`, mức song song `p` | **Memory-hard**; làm phép thử song song quy mô lớn đắt RAM | Thường ưu tiên cho hệ thống mới, cần thư viện được bảo trì |
| **scrypt** | `N`, `r`, `p` | Memory-hard | Tốt nếu không thể dùng Argon2id |
| **bcrypt** | Cost tăng theo cấp số mũ | Chủ yếu CPU | Hệ thống cũ; chú ý giới hạn đầu vào thường **72 byte** |
| **PBKDF2-HMAC-SHA256** | `iterations` | Chuỗi HMAC lặp; **không memory-hard** | Chuẩn hóa, Java 17 có sẵn, phù hợp chính sách/tương thích |
| **SHA-256 một lần** | Không có cost | Rất nhanh | Không lưu password trực tiếp |

Theo OWASP Password Storage Cheat Sheet, những **mốc tham khảo cần cập nhật theo thời gian** gồm:

- Argon2id: một cấu hình cơ sở **`m=19 MiB, t=2, p=1`**; tài liệu nêu các kết hợp RAM/iterations tương đương về mục tiêu.
- scrypt: chẳng hạn `N=2^17, r=8, p=1`.
- bcrypt: cost từ **10** cho trường hợp tương thích với hệ thống cũ; xử lý giới hạn 72 byte cẩn thận.
- PBKDF2-HMAC-SHA256: **600.000 iterations** hoặc hơn theo hướng dẫn OWASP hiện có.

Đây **không phải các con số có thể so trực tiếp** giữa thuật toán (2 vòng Argon2id không tương đương 2 vòng PBKDF2). Benchmark latency, throughput, RAM và khả năng chống DoS trên môi trường production; tăng cost/rehash khi phần cứng và hướng dẫn thay đổi.

**Memory-hard không có nghĩa không thể crack.** Argon2id buộc attacker bỏ thêm tài nguyên bộ nhớ cho từng ứng viên; nếu password quá phổ biến, nó vẫn có thể bị đoán. Work factor tăng chi phí, không tạo entropy mới cho mật khẩu yếu.

### 6.2 Vì sao ví dụ Java dưới đây dùng PBKDF2?

`SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` là API chuẩn Java 17. Nó cho phép minh họa toàn bộ vòng đời salt/iterations/verifier **không cần thêm thư viện**. PBKDF2 có thể cần dùng trong môi trường yêu cầu triển khai được kiểm định/tuân thủ FIPS; nhưng nếu có thư viện Argon2id an toàn và chính sách cho phép, OWASP thường ưu tiên Argon2id cho hệ thống mới.

## 7. Thực hành 2: PBKDF2-HMAC-SHA256 đầy đủ trong Java 17

Ví dụ này dùng:

- Salt **16 byte mới**, sinh bằng `SecureRandom` cho từng bản ghi.
- `PBKDF2WithHmacSHA256` với **600.000 iterations** và derived hash dài **32 byte**.
- Chuỗi lưu `scheme$iterations$Base64(salt)$Base64(derived)` để thuật toán và cost không bị mất.
- Verify bằng **cùng salt/iterations** lưu trong record, sau đó so sánh byte với `MessageDigest.isEqual`.
- `PBEKeySpec.clearPassword()` và xóa mảng nhạy cảm mà ví dụ sở hữu.
- Giới hạn iterations khi đọc bản ghi để tránh xử lý input cấu hình cực lớn; con số **2 triệu** là chính sách demo, không phải tiêu chuẩn bắt buộc.

Tạo file **`Pbkdf2PasswordDemo.java`**:

```java
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Pbkdf2PasswordDemo {
    private static final String SCHEME = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final SecureRandom RNG = new SecureRandom();

    private static byte[] derive(char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(
                password, salt, iterations, HASH_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    public static String register(char[] password) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        try {
            return SCHEME + "$" + ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(derived);
        } finally {
            Arrays.fill(derived, (byte) 0);
        }
    }

    public static boolean verify(char[] password, String stored)
            throws GeneralSecurityException {
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 4 || !SCHEME.equals(parts[0])) {
            return false;
        }
        final int count;
        try {
            count = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (count <= 0 || count > 2_000_000) {
            return false;
        }
        final byte[] salt;
        final byte[] expected;
        try {
            salt = Base64.getDecoder().decode(parts[2]);
            expected = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (salt.length != SALT_BYTES || expected.length != HASH_BYTES) {
            return false;
        }
        byte[] actual = derive(password, salt, count);
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    public static void main(String[] args) throws Exception {
        char[] correct = "classroom-demo-only".toCharArray();
        char[] incorrect = "classroom-demo-wrong".toCharArray();
        try {
            String first = register(correct);
            String second = register(correct);
            String[] fields = first.split("\\$", -1);
            System.out.println("scheme: " + fields[0]);
            System.out.println("work factor: " + fields[1]);
            System.out.println("salt bytes: " +
                    Base64.getDecoder().decode(fields[2]).length);
            System.out.println("derived bytes: " +
                    Base64.getDecoder().decode(fields[3]).length);
            System.out.println("different salts: " +
                    !fields[2].equals(second.split("\\$", -1)[2]));
            System.out.println("correct password: " + verify(correct, first));
            System.out.println("wrong password: " + verify(incorrect, first));
        } finally {
            Arrays.fill(correct, '\0');
            Arrays.fill(incorrect, '\0');
        }
    }
}
```

Chạy:

```bash
javac --release 17 Pbkdf2PasswordDemo.java
java Pbkdf2PasswordDemo
```

Ví dụ đầu ra đã kiểm tra:

```text
scheme: pbkdf2-sha256
work factor: 600000
salt bytes: 16
derived bytes: 32
different salts: true
correct password: true
wrong password: false
```

Hai salt 128 bit sinh độc lập gần như chắc chắn khác nhau (xác suất trùng cực nhỏ nhưng không tuyệt đối bằng 0). Các dòng còn lại phản ánh format và verify. Chương trình không in raw password, salt hoặc derived hash ra console.

### 7.1 Giải thích luồng đăng ký

1. `register` sinh 16 byte salt bằng `SecureRandom.nextBytes` **mỗi lần tạo verifier**.
2. `PBEKeySpec(password, salt, ITERATIONS, HASH_BYTES * 8)` dùng tham số độ dài key theo **bit**, không theo byte: 256 bit chỉ là kích thước verifier, không tăng entropy của password.
3. `SecretKeyFactory` thực hiện PBKDF2 với HMAC-SHA256. Muốn dùng Argon2id cần thư viện phù hợp; không thể đổi tên thuật toán rồi hy vọng API Java có sẵn mọi KDF.
4. Bản ghi chứa scheme, cost, salt và kết quả. Nó là **password verifier**, không phải encrypted password có thể giải mã.

### 7.2 Giải thích luồng xác minh

1. `verify` tách format và kiểm scheme/iterations/byte lengths.
2. Nó dùng **salt và iterations của bản ghi cũ**, không sinh salt mới. Nếu sinh salt mới khi verify, password đúng cũng thường không khớp.
3. Dẫn xuất lại bằng `PBEKeySpec` rồi so sánh byte bằng `MessageDigest.isEqual`.
4. Trả `false` khi password sai; cần xử lý lỗi và các trường hợp tài khoản không tồn tại theo một chính sách chung ở tầng ứng dụng.

Giới hạn iterations trong demo là cách tránh bản ghi lỗi buộc xử lý một lượng công việc không kiểm soát. Production cần thêm giới hạn độ dài record/input, xác thực encoding, giới hạn request/concurrency và chính sách nâng version. **Đây là code hướng dẫn**, ứng dụng thật nên dùng password encoder được bảo trì và định dạng bản ghi chuẩn.

### 7.3 Password trong bộ nhớ

`PBEKeySpec` giữ bản sao password ở dạng `char[]` và `clearPassword()` xóa bản sao **bên trong nó**; caller vẫn phải xóa `char[]` khi dùng xong. `Arrays.fill` có thể giảm thời gian secret tồn tại trong mảng do ứng dụng quản lý nhưng không chứng minh mọi bản sao trong heap đã bị loại bỏ.

Các literal `"classroom-demo-only"` chỉ phục vụ demo tự chạy; sản phẩm thật **không hard-code password**, không tạo chuỗi `String` thừa và không ghi thông tin nhạy cảm vào log, APM, crash report hay URL. `String` bất biến khó xóa chủ động.

## 8. Lưu bản ghi verifier như thế nào để có thể nâng cấp?

Nếu chỉ lưu một chuỗi 32 byte, sau nhiều năm bạn không biết đó là SHA-256 hay PBKDF2 bao nhiêu vòng. Cần **version/algorithm/parameters** riêng trong bản ghi, chẳng hạn:

```text
{ scheme, version, work_factor, salt, derived_hash }
```

Đối với Argon2id phải lưu đủ `m, t, p`; với bcrypt lưu cost/format của thuật toán; với PBKDF2 cần thuật toán HMAC, số vòng, salt và độ dài kết quả. Có thể dùng **PHC string hoặc format của thư viện** thay vì tự định nghĩa.

Khi tăng cost, verifier cũ vẫn phải được xác minh đúng: đọc params từ record. Nếu user đăng nhập thành công và record có cost cũ, dùng password vừa nhập để **rehash với salt mới và cost mới**, cập nhật lưu trữ atomically; không cần giữ plaintext password lâu dài.

**Không đánh đồng “PBKDF2 của SHA-256 hash cũ” với PBKDF2 của password gốc.** Nếu hash nhanh cũ đã bị lộ, attacker có thể đoán nó offline; việc bọc thêm một lớp mà không đổi password không xóa được thiệt hại. Với hệ thống đang có SHA-256/password cũ, cần kế hoạch di trú:

1. Ngừng tạo verifier SHA-256 đơn mới.
2. Hỗ trợ kiểm tra bản ghi legacy trong thời gian di trú có kiểm soát.
3. Sau login đúng với password gốc, tạo verifier mới bằng KDF chuyên dụng và salt mới.
4. Với tài khoản không đăng nhập, yêu cầu đặt lại password theo chính sách.
5. Nếu nghi ngờ database đã rò rỉ, đánh giá vụ việc, thu hồi phiên và reset password khi cần. Rehash sau sự cố **không làm cho password cũ từng bị crack trở lại an toàn**.

## 9. Salt, pepper, mã hóa và HMAC: đừng nhầm vai trò

| Cơ chế | Bí mật? | Công dụng | Có thay được password KDF? |
| --- | --- | --- | --- |
| **Salt** | Không, lưu cùng record | Chống precomputation, phân biệt mỗi bản ghi | Không |
| **Pepper** | **Có**, giữ ngoài DB | Thêm rào cản nếu chỉ database bị lộ | Không |
| **Password KDF** | Password là bí mật | Làm mỗi phép đoán tốn CPU/RAM, tạo verifier | Đây là thành phần cần dùng |
| **AES encryption** | Phải giữ encryption key | Bảo mật dữ liệu **có thể giải mã** khi có key | Không phù hợp thay verifier thông thường |
| **HMAC** | Có key | Xác thực dữ liệu; có thể tham gia sơ đồ pepper đúng thiết kế | Không dùng đơn lẻ |

Pepper có thể nằm trong vault/HSM/secret manager độc lập với database. Nó **không thay thế salt hay cost**. Nếu attacker cũng lấy pepper, lớp phòng vệ này không còn; việc rotate pepper cần thiết kế vì không có plaintext password để tính lại tùy ý.

Không lưu plaintext password hay mã hóa để phục hồi password người dùng nếu mục tiêu chỉ là **xác minh khi login**. Mã hóa có khóa giải mã tạo nguy cơ lộ toàn bộ plaintext khi key bị đánh cắp; password hashing được thiết kế cho nhu cầu khác.

**Lưu ý trường hợp ngoại lệ:** một token sinh ngẫu nhiên có entropy cao, đủ dài có thể được xử lý theo threat model khác. Điều đó không hợp thức hóa SHA-256 một lần với mật khẩu con người có entropy thấp.

## 10. Tấn công online và offline cần phòng vệ khác nhau

| Đặc điểm | Online guessing | Offline guessing |
| --- | --- | --- |
| Nguồn dữ liệu attacker có | Endpoint login | Bản sao verifier/salt/params |
| Có gửi request tới hệ thống thật? | Có | Không |
| Rate limit/CAPTCHA/khóa tạm giúp được? | Có thể | Không ngăn các phép tính offline |
| MFA giúp giảm đăng nhập trái phép? | Có, theo thiết kế | Không làm hash offline chậm hơn |
| Hàng rào chính | Rate limiting, MFA, giám sát và chống enumeration | Salt + password KDF/cost + bảo vệ DB/pepper |

Một hệ thống nghiêm túc cần cả hai: mật khẩu mạnh/duy nhất, **blocklist password phổ biến/rò rỉ**, hỗ trợ password manager, bảo mật đường truyền TLS, giới hạn request, MFA/passkeys phù hợp và password KDF chuẩn.

Cần tránh **tiết lộ account tồn tại** qua thông báo lỗi và chênh lệch thời gian quá lớn; dùng chính sách lỗi login thống nhất. `MessageDigest.isEqual` hỗ trợ so sánh an toàn hơn, nhưng không bảo đảm **toàn bộ hàm login** có thời gian bất biến: vẫn còn truy vấn DB, parse record, tính KDF và các nhánh khác.

## 11. Chọn work factor như thế nào?

Cost quá thấp giúp attacker thử nhiều ứng viên; cost quá cao gây CPU/RAM saturation khi login đồng thời, thậm chí DoS. Một cách làm có căn cứ:

1. Chọn KDF chuyên dụng, ưu tiên Argon2id khi môi trường hỗ trợ; dùng cấu hình xuất phát từ tài liệu bảo mật cập nhật.
2. Benchmark **trên hạ tầng production tương đương**, đo p50/p95/p99 latency, CPU/RAM và lượng login song song. Với Argon2id, tính tổng RAM khi có nhiều request đang chạy.
3. Đặt ngân sách CPU/memory, hàng đợi/concurrency limit, rate limit và giới hạn độ dài input.
4. Lưu scheme/version/work factor trên từng bản ghi để tăng dần. Đừng để metadata không tin cậy yêu cầu hàng tỷ iterations.
5. Định kỳ tăng cost khi phần cứng thay đổi; nâng cấp verifier qua lần login hoặc quy trình reset password.

Không “giải quyết” latency bằng cách bỏ KDF rồi dùng SHA-256 một lần. Cần tối ưu kiến trúc xác minh, thông lượng và bảo vệ request.

### 11.1 Unicode và password normalization

Java nhận password qua `char[]`. Cách chuyển ký tự thành byte của thuật toán/provider và quy tắc Unicode phải tương thích giữa các lần đăng ký/xác minh, giữa các runtime/ngôn ngữ nếu dữ liệu được chuyển hệ thống.

- Không tự động lowercase, `trim()` hoặc cắt password trong im lặng.
- Nếu chọn Unicode normalization, quy định rõ form/version và dùng nhất quán.
- Lưu ý giới hạn **72 byte** của nhiều triển khai bcrypt, không nhầm “ký tự” với “byte” khi input có dấu tiếng Việt/emoji.
- Không dùng Base64/hex như một hình thức tăng độ an toàn: đó chỉ là encoding.

Trong sản phẩm nên chọn password encoder thư viện đã kiểm thử thay vì tự viết nhiều biến thể password hashing/migration không cần thiết.

## 12. Những nhận định sai thường gặp

- **“SHA-256 có 256 bit nên mật khẩu đã có 256-bit security.”** Sai. Đầu ra dài không tạo entropy cho password yếu.
- **“Thêm một salt là đủ.”** Sai. Salt chống bảng dựng sẵn, vẫn cần password KDF.
- **“Cứ SHA-256 lặp vài vòng là ngang Argon2id.”** Sai. Không tự chế sơ đồ; Argon2id có memory-hardness và cơ chế được nghiên cứu riêng.
- **“Hash là một chiều nên không ai lấy được password.”** Sai. Tấn công từ điển chỉ cần hash và candidate, không cần đảo hàm.
- **“Rate limit chặn brute force offline.”** Sai. Offline thử ngoài server.
- **“Nhiều dấu đặc biệt đảm bảo entropy cao.”** Sai. Mẫu người dùng có thể dễ đoán.
- **“Salt cần bí mật; pepper có thể lưu ngay trong DB.”** Ngược lại: salt được công khai, pepper phải được bảo vệ ngoài DB.
- **“Băm password hai lần theo hai thuật toán nhanh là an toàn.”** Không tạo ra cost hoặc memory-hardness đủ mạnh để bảo vệ password yếu.
- **“MFA khiến stored password hashes không cần bảo vệ.”** Sai. MFA là lớp bổ sung cho login; database leak vẫn nguy hiểm, đặc biệt do password reuse.
- **“Chuyển hash cũ sang format mới là xong sự cố.”** Sai. Password cũ có thể đã bị đoán; cần điều tra và reset/thu hồi phiên tùy mức độ.

## 13. Bài tập tự kiểm tra

**Câu 1.** Một PIN ngẫu nhiên đều 6 chữ số có bao nhiêu bit entropy trước khi băm SHA-256?  
**Đáp:** Khoảng **19,93 bit**, không phải 256 bit.

**Câu 2.** Vì sao attacker offline không bị hạn chế bởi CAPTCHA?  
**Đáp:** Họ tự tính verifier từ dữ liệu đã lộ, không gọi login endpoint.

**Câu 3.** Salt cho hai tài khoản cùng password khác nhau sẽ giải quyết điều gì?  
**Đáp:** Phân biệt bản ghi, giảm hiệu quả bảng dựng sẵn và việc tái sử dụng cùng hash; **không** ngăn attacker thử từng candidate với salt đã biết.

**Câu 4.** PBKDF2 khác SHA-256 một lần ở đâu?  
**Đáp:** PBKDF2 có salt và work factor `iterations` khiến mỗi lần thử tốn nhiều vòng HMAC. Nó không memory-hard như Argon2id.

**Câu 5.** Vì sao không sinh salt mới mỗi khi user đăng nhập để so sánh?  
**Đáp:** Xác minh phải dùng salt lưu từ lúc đăng ký, sau đó chạy lại KDF trên password được nhập. Salt mới dùng khi tạo **verifier mới/rehash**.

**Câu 6.** Pepper là gì?  
**Đáp:** Secret bổ sung phía server, giữ ngoài DB; không thay thế salt và password KDF.

**Câu 7.** Nếu verifier cũ dùng 20.000 vòng PBKDF2 và chính sách mới đòi 600.000 vòng, nên xử lý thế nào?  
**Đáp:** Xác minh đúng theo tham số cũ; khi user login thành công, rehash từ password vừa nhập với salt mới/cost mới và lưu record có metadata.

**Câu 8.** Trong một vụ lộ password hashes SHA-256, chỉ thay hash hiện có bằng `SHA-256(SHA-256(password))` có giải quyết vấn đề không?  
**Đáp:** Không. Vẫn thiếu scheme chuyên dụng và không khôi phục an toàn cho password có thể đã bị attacker tìm ra.

## Tóm tắt mô hình

```text
Mật khẩu do con người chọn: thường ít entropy
                 |
                 v
SHA-256 một lần: thử ứng viên rất rẻ
                 |
                 v
Nếu verifier lộ, attacker thử offline

Phòng vệ đúng hướng:
CSPRNG salt riêng + password KDF có cost
                 |
       +---------+-----------+
       |                     |
   Argon2id/scrypt       PBKDF2/bcrypt
   CPU + memory          cost theo vòng
       |                     |
       +---------+-----------+
                 |
                 v
Lưu {scheme/version/params/salt/hash}
                 |
                 v
Login: KDF với salt lưu sẵn + so sánh an toàn
                 |
                 v
Kết hợp rate limit, MFA và quản lý nâng cấp
```

**Độ dài của hash không làm cho password yếu trở nên khó đoán.** Muốn bảo vệ password verifier khi database bị đánh cắp, phải khiến **mỗi ứng viên** trở nên tốn tài nguyên để kiểm tra, thêm salt độc lập và điều chỉnh cost theo thời gian.

## Tài liệu tham khảo chính thức

- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): Argon2id, scrypt, bcrypt, PBKDF2, salt/pepper và nâng cấp verifier.
- [NIST SP 800-63B-4 — Authentication and Authenticator Management](https://pages.nist.gov/800-63-4/sp800-63b.html): verifier chống offline attacks, salt và cost phù hợp.
- [Java 17 PBEKeySpec API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html): `char[]`, salt, iterations, key length và `clearPassword()`.
- [Java 17 SecretKeyFactory API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/SecretKeyFactory.html): factory cho cơ chế dẫn xuất khóa.
- [Java 17 SecureRandom API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html): sinh salt bằng bộ sinh ngẫu nhiên mật mã.
- [Java 17 MessageDigest API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html): SHA-256 và `isEqual`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="password-key-derivation-003">7.3 Vai trò của salt, số vòng lặp, độ dài đầu ra và phiên bản cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

# 7.3 Vai trò của salt, số vòng lặp, độ dài đầu ra và phiên bản cấu hình

Khi lưu mật khẩu hoặc dẫn xuất khóa từ mật khẩu trong Java, ta thường gặp các tham số **salt**, **iteration count (số vòng lặp)**, **derived key length (độ dài đầu ra)** và **configuration version (phiên bản cấu hình)**. Chúng trả lời bốn câu hỏi riêng biệt:

1. **Salt:** hai lần xử lý cùng một mật khẩu có tạo cùng kết quả hay không, và có thể dùng bảng tính sẵn hoặc chia sẻ kết quả tấn công cho nhiều tài khoản hay không?
2. **Số vòng lặp / chi phí:** một lần thử đoán mật khẩu đòi hỏi bao nhiêu công việc tính toán?
3. **Độ dài đầu ra:** kết quả nhị phân có bao nhiêu bit/byte để so sánh hoặc dùng làm vật liệu khóa?
4. **Phiên bản cấu hình:** làm sao biết kết quả cũ được tạo bằng thuật toán và tham số nào, xác minh được nó rồi nâng cấp an toàn khi chính sách thay đổi?

Bốn tham số **không thay thế nhau**. Salt không làm một mật khẩu yếu trở nên khó đoán; tăng output từ 256 lên 512 bit không làm entropy mật khẩu tăng; số vòng lặp cần được hiệu chỉnh theo phần cứng và nhu cầu vận hành; còn phiên bản cấu hình giúp hệ thống di chuyển từ chính sách cũ sang chính sách mới.

Bài này thuộc chủ đề **mật khẩu và dẫn xuất khóa**. Cần phân biệt hai mục tiêu: **password hashing để xác minh đăng nhập** và **password-based key derivation để tạo khóa mã hóa**. Cả hai có thể dùng KDF, nhưng vòng đời và cách lưu kết quả khác nhau.

## 1. Khung khái niệm: mật khẩu, KDF và verifier

Một hàm dẫn xuất khóa từ mật khẩu thường có dạng trừu tượng:

```text
derived = KDF(password, salt, cost_parameters, output_length, algorithm)
```

Với **PBKDF2-HMAC-SHA256**, mô hình có thể viết:

```text
DK = PBKDF2-HMAC-SHA256(P, S, c, dkLen)
P      = mật khẩu
S      = salt
c      = số vòng lặp (iteration count)
dkLen  = độ dài đầu ra mong muốn (byte theo đặc tả PBKDF2)
DK     = derived key (các byte kết quả)
```

Trong Java `PBEKeySpec(char[] password, byte[] salt, int iterationCount, int keyLength)` dùng `keyLength` **theo bit**, khác ký hiệu `dkLen` **theo byte** trong đặc tả PBKDF2. Vì vậy **256** trong `PBEKeySpec(..., 256)` tạo mục tiêu đầu ra **256 bit = 32 byte**, không phải 256 byte.

**KDF** là khái niệm rộng; từng họ có tham số chi phí riêng:

| Thuật toán | Tham số chi phí chính | Đặc tính | Nơi thường gặp |
| --- | --- | --- | --- |
| **PBKDF2-HMAC-SHA256** | `iterations` | Gia tăng chi phí CPU theo số vòng lặp | Java JCA/JCE, hệ thống cần PBKDF2 |
| **Argon2id** | `memory`, `time cost`, `parallelism` | Memory-hard, nhằm tăng chi phí tấn công song song/GPU | Hệ thống có thư viện Argon2id đáng tin |
| **scrypt** | `N`, `r`, `p` | Memory-hard với tham số tài nguyên | Một số hệ thống password hashing/KDF |
| **bcrypt** | Cost factor (logarithmic) | Cơ chế chi phí riêng, có giới hạn đầu vào cần chú ý | Password verifier của ứng dụng legacy/hiện hữu |

Không thể so sánh trực tiếp “PBKDF2 200.000 vòng” với “Argon2id 3 vòng” hoặc “bcrypt cost 12”: chúng đo **các công việc khác nhau**. Chọn thuật toán và cấu hình theo mô hình đe dọa, tài nguyên máy chủ và hướng dẫn bảo mật hiện hành, rồi **đo trên phần cứng thực tế**.

### 1.1. Password hashing khác mã hóa có thể giải mã

Để **xác minh đăng nhập**, backend lưu một **verifier** được dẫn xuất từ mật khẩu, không lưu mật khẩu gốc hoặc bản mã có thể giải mã bằng khóa ứng dụng. Khi người dùng đăng nhập, backend lấy *salt và tham số đã lưu*, tính lại KDF cho mật khẩu vừa nhập và so sánh kết quả. Không cần biết mật khẩu gốc.

Để **dẫn xuất khóa mã hóa từ mật khẩu**, output có thể dùng làm vật liệu khóa cho AES hoặc cơ chế khác. Khi mật khẩu và các tham số được nhập lại đúng, KDF trả cùng byte khóa để mở dữ liệu. Trường hợp này phải quản lý thêm IV/nonce, authenticated encryption, thay mật khẩu, khôi phục dữ liệu và tính ổn định của tham số. Đừng tự suy diễn rằng cùng một output KDF được phép tái sử dụng đồng thời làm verifier, AES key và HMAC key: các mục đích cần **tách khóa hoặc tách miền dẫn xuất**.

### 1.2. Các thành phần cần lưu cùng kết quả

Trong hệ thống xác minh mật khẩu, một bản ghi tối thiểu thường gồm **định danh thuật toán**, **tham số chi phí**, **salt**, **độ dài đầu ra/khuôn dạng**, **digest đã dẫn xuất** và **phiên bản cấu hình**. Các trường không chứa plaintext password.

Ví dụ sơ đồ bản ghi (tên trường là mô tả cấu trúc, không phải chuỗi verifier đã được tính sẵn):

```text
CredentialRecord
  formatVersion      : 2
  algorithm          : PBKDF2WithHmacSHA256
  iterations         : 180000
  derivedKeyBits     : 256
  salt               : binary, 16 bytes (stored as Base64URL)
  verifier           : binary, 32 bytes (stored as Base64URL)
```

**180.000 vòng ở ví dụ này chỉ là tham số minh họa cho luồng nâng cấp, không phải khuyến nghị cấu hình production.** Trong hệ thống thật cần kiểm tra yêu cầu chính sách và benchmark ở thời điểm triển khai.

## 2. Salt: giá trị ngẫu nhiên riêng cho mỗi lần tạo verifier

**Salt** là dữ liệu đầu vào bổ sung cho KDF. Với một mật khẩu cụ thể, thay salt sẽ làm kết quả dẫn xuất khác nhau trong thực hành mật mã:

```text
KDF("example-password", salt_A, params) != KDF("example-password", salt_B, params)
```

Hai tài khoản dùng chung mật khẩu **không nên có cùng verifier**. Nếu chúng có salt độc lập đủ dài được sinh ngẫu nhiên, các kết quả KDF hầu như chắc chắn khác nhau. Điều này cản trở việc phát hiện mật khẩu trùng chỉ bằng cách so sánh hash và buộc kẻ tấn công thực hiện công việc đoán mật khẩu riêng cho từng salt, thay vì dùng kết quả tính sẵn cho một salt chung.

### 2.1. Tại sao phải có salt?

- **Chống bảng tra cứu tính trước:** rainbow table/lookup table cho hash không salt không áp dụng nguyên vẹn cho mọi salt độc lập.
- **Ngăn verifier trùng hàng loạt:** hai người cùng dùng mật khẩu sẽ có giá trị verifier khác nhau khi salt khác nhau.
- **Hạn chế chia sẻ chi phí tấn công giữa nhiều mục tiêu:** kẻ tấn công phải xét salt tương ứng của từng bản ghi.
- **Giữ tính kiểm chứng:** bản ghi chứa salt cho phép backend tính lại cùng KDF với mật khẩu nhập sau này.

Salt **không ngăn** kẻ tấn công đoán một mật khẩu cụ thể theo kiểu offline nếu đã lấy được verifier và salt. Salt cũng không tạo thêm entropy cho mật khẩu. Một mật khẩu yếu vẫn dễ bị đoán tương đối khi dùng KDF rẻ hoặc số lượt thử không bị giới hạn.

### 2.2. Salt phải ngẫu nhiên, độc lập, đủ dài

Với hệ thống mới, ví dụ thực hành dễ hiểu là **16 byte (128 bit) salt sinh bằng `SecureRandom`** cho mỗi verifier. Một số thuật toán/định dạng có yêu cầu riêng; phải làm theo đặc tả của thuật toán, thư viện hoặc định dạng lưu trữ đang sử dụng.

```java
byte[] salt = new byte[16];
new java.security.SecureRandom().nextBytes(salt);
```

Đây là đoạn minh họa, chưa phải chương trình độc lập. **Tạo salt mới khi tạo verifier mới**, kể cả khi hai người dùng chọn cùng một mật khẩu. Khi nâng cấp cấu hình bằng rehash sau đăng nhập, nên tạo **salt mới** cùng bản ghi mới.

Tránh dùng một salt toàn cục cho mọi tài khoản, dùng username/email làm salt, chỉ lấy timestamp hoặc bộ đếm dự đoán được để sinh salt, hoặc tạo salt từ một bộ sinh ngẫu nhiên không phù hợp như `Random`. Dữ liệu định danh có thể là một phần cấu trúc giao thức, nhưng **không thay thế salt ngẫu nhiên** khi hệ thống yêu cầu salt ngẫu nhiên.

Với salt ngẫu nhiên 128 bit, khả năng va chạm giữa hai salt độc lập rất nhỏ ở quy mô thông thường. Tuy nhiên yếu tố quyết định là **bộ sinh ngẫu nhiên mật mã và việc tạo riêng cho từng lần**, không chỉ độ dài mảng byte.

### 2.3. Salt có bí mật không?

**Không. Salt thường được lưu công khai bên cạnh verifier** trong cơ sở dữ liệu. Người xác minh cần đọc đúng salt để tái tạo output. Bảo mật dựa vào mật khẩu khó đoán và KDF có chi phí phù hợp, **không dựa vào việc giấu salt**.

- **Salt:** không bí mật, riêng từng bản ghi, dùng để tạo đầu ra khác nhau.
- **Pepper (nếu có):** bí mật dùng chung hoặc theo scope, được bảo quản ngoài DB, ví dụ KMS/HSM/secret manager. Pepper không thể thay salt; việc thêm và thay pepper cần thiết kế thuật toán/rotation riêng.
- **Nonce/IV:** có vai trò khác trong giao thức mã hóa, không tự đồng nghĩa với salt.
- **Secret key:** khóa cần giữ bí mật; không biến salt thành secret hoặc dùng salt như khóa AES/HMAC.

Nếu bản ghi bị sao chép sang môi trường khác mà thiếu salt, việc xác minh lại có thể **không thực hiện được**, vì output KDF phụ thuộc chính xác vào salt.

### 2.4. Salt khác nhau và salt bị thay đổi

Với cùng mật khẩu, cùng thuật toán và cùng số vòng:

```text
KDF(P, S1, 100000, 32 bytes) -> output_1
KDF(P, S2, 100000, 32 bytes) -> output_2
```

Kết quả `output_1` và `output_2` khác nhau vì `S1 != S2`. Nếu backend vô tình dùng salt mới mỗi lần người dùng *đăng nhập* thay vì dùng salt đã lưu, việc xác minh **sẽ thất bại**. Salt mới chỉ được tạo lúc tạo verifier mới hoặc rehash được kiểm soát.

Không coi một salt “dài 16 byte” là bằng chứng đủ an toàn: phải biết nó **được tạo bằng gì**, có bị reuse quy mô lớn không và có được lưu/đọc đúng byte hay không.

## 3. Số vòng lặp: điều chỉnh chi phí thử mật khẩu

Với PBKDF2, `iterationCount` là số vòng thực hiện chuỗi PRF bên trong thuật toán. Khi các yếu tố khác giữ nguyên, tăng số vòng lặp làm mỗi lần dẫn xuất tốn thêm CPU và thời gian. Điều này có ích vì kẻ tấn công offline thường cần thử rất nhiều giả thuyết mật khẩu.

Tuy nhiên, **chi phí cao tác động cả phía hợp lệ**: đăng nhập, reset mật khẩu, batch migration và mở khóa dữ liệu đều có thể tốn thêm CPU, gây nghẽn hoặc tạo điều kiện cho DoS nếu không giới hạn tốc độ.

### 3.1. Vì sao không dùng hash nhanh để lưu mật khẩu?

SHA-256 là hash nhanh, được thiết kế để xử lý dữ liệu hiệu quả. Nếu hệ thống lưu `SHA256(password)` hoặc chỉ `SHA256(salt || password)`, kẻ tấn công có thể thử nhiều mật khẩu rất nhanh khi có cơ sở dữ liệu verifier.

Password KDF thêm **chi phí tính toán có chủ đích**. Với PBKDF2, mỗi phép thử phải chạy số vòng đã cấu hình. Với Argon2id/scrypt, mục tiêu còn có thể bao gồm chi phí bộ nhớ, khiến tấn công song song trên GPU/ASIC khó tối ưu hơn.

**Thêm salt vào SHA-256 không thay thế PBKDF2/Argon2id/scrypt** trong vai trò password KDF có cấu hình chi phí phù hợp.

### 3.2. Chọn số vòng bằng chính sách và benchmark

Một cấu hình sản xuất cần quyết định theo các bước:

1. Chọn thuật toán KDF được hỗ trợ, được đánh giá phù hợp với threat model và các yêu cầu tương thích/compliance.
2. Đọc hướng dẫn bảo mật **còn hiệu lực tại thời điểm triển khai**. Số vòng “tốt” có thể thay đổi theo tốc độ phần cứng và thuật toán.
3. Đo **p50/p95/p99** thời gian của một phép xác minh trên phần cứng/instance thực tế, trong điều kiện có tải đồng thời.
4. Đặt ngân sách latency/CPU và bảo vệ trước brute force/DoS; cân nhắc quota, rate limit, MFA và giới hạn số request xác minh song song.
5. Ghi cấu hình đã phê duyệt vào version/policy, giám sát hiệu năng và có kế hoạch tăng chi phí trong các lần nâng cấp.

**Không dùng các giá trị demo 100.000 hoặc 180.000 vòng trong ví dụ Java dưới đây làm con số khuyến nghị production.** Chúng chỉ giúp thấy cơ chế giữ cấu hình cũ, xác minh và chuyển sang cấu hình mới.

### 3.3. Tăng số vòng tác động thế nào?

Nếu thời gian PBKDF2 với 100.000 vòng trên một máy là khoảng 50 ms, 200.000 vòng **thường** sẽ gần gấp đôi về phần công việc CPU của KDF trên cùng điều kiện. Đây là **quan hệ xu hướng**, không phải cam kết thời gian thực; provider, JVM, CPU, tải, cache và môi trường đều ảnh hưởng benchmark.

Kẻ tấn công vẫn có thể tối ưu phần cứng, chạy song song và đoán mật khẩu yếu. Số vòng **không tạo ra** một mật khẩu có entropy cao hơn. Vì vậy cần mật khẩu/passphrase đủ mạnh, xác thực đa yếu tố khi phù hợp và bảo vệ các điểm thử trực tuyến.

### 3.4. Argon2id và bcrypt không dùng cùng ý nghĩa “số vòng”

- **Argon2id:** tham số chi phí thường gồm bộ nhớ (ví dụ KiB), số pass/time cost và độ song song. Đánh giá theo **bộ ba này**, không nhìn riêng một chữ “iterations”. Argon2id có tham số phiên bản thuật toán riêng; cấu hình must include cả bộ tham số.
- **scrypt:** dùng các tham số `N`, `r` và `p` liên quan công việc và bộ nhớ; `N` không thể so sánh trực tiếp với vòng PBKDF2.
- **bcrypt:** cost factor quy định mức công việc tăng theo hàm mũ; nhích thêm 1 cost khác với tăng thêm 1 vòng PBKDF2. Bcrypt cũng có giới hạn/đặc thù xử lý độ dài password theo implementation, nên cần xét quy tắc encoding và cách xử lý chuỗi dài.

Không thiết kế một cấu hình tên `rounds=3` chung cho mọi KDF rồi kỳ vọng nó có ý nghĩa giống nhau. Khi migrate thuật toán, **đổi schema/tham số và algorithm ID tương ứng**.

## 4. Độ dài đầu ra: số byte kết quả, không phải độ mạnh mật khẩu

**Derived key length** nói về kích thước vật lý của kết quả KDF. Với `PBKDF2WithHmacSHA256`, có thể yêu cầu đầu ra với số bit cấu hình, ví dụ 128, 256 hoặc 512 bit nếu phù hợp sử dụng và provider hỗ trợ.

| Đầu ra | Nhị phân | Biểu diễn hex | Base64 chuẩn có padding |
| --- | ---: | ---: | ---: |
| 128 bit | 16 byte | 32 ký tự ASCII | 24 ký tự |
| 256 bit | 32 byte | 64 ký tự ASCII | 44 ký tự |
| 512 bit | 64 byte | 128 ký tự ASCII | 88 ký tự |

**Cảnh báo:** 256-bit output **không đồng nghĩa 256-bit password security**. Nếu password chỉ được đoán từ một tập nhỏ, attacker có thể chạy KDF cho các password đoán được. Tăng độ dài kết quả không làm tăng số khả năng của mật khẩu.

### 4.1. Kích thước kết quả theo mục đích

- **Password verifier:** chọn độ dài output theo thiết kế thuật toán/policy. Cần lưu đủ byte để so sánh, tránh tự truncate mà không có đặc tả.
- **Khóa AES-128:** cần 16 byte key; **AES-256** cần 32 byte key. Nhưng chỉ nên dùng output KDF trực tiếp làm khóa khi thiết kế KDF, salt, context và key lifecycle phù hợp.
- **Khóa HMAC:** key material và MAC output length là các đại lượng riêng; output 32 byte của KDF không mặc nhiên yêu cầu MAC cũng 32 byte trong mọi thuật toán.
- **HKDF sau password KDF:** một thiết kế có thể tạo một secret trung gian phù hợp rồi dẫn xuất các key theo mục đích bằng HKDF; cần xác định rõ domain, context và bảo vệ root/intermediate key. Không tái sử dụng thẳng một output cho nhiều chức năng.

Với PBKDF2 theo RFC, `dkLen` trong thuật toán là **byte**, giới hạn bởi số block của PRF. Với Java `PBEKeySpec`, `keyLength` là **bit**. Kiểm tra cả tên tham số **lẫn đơn vị** trước khi truyền giá trị cho API.

### 4.2. Byte, bit, hex, Base64 không phải một con số

Một verifier 32 byte có thể được lưu vào DB dưới dạng **64 ký tự hex** hoặc **44 ký tự Base64 chuẩn có padding**. Nếu dùng Base64URL không padding, 32 byte thường thành **43 ký tự ASCII**. Đây là overhead do encoding, **không thay đổi thuật toán KDF**.

Ví dụ:

```java
byte[] derived = /* output KDF, ví dụ 32 byte */;
String encoded = java.util.Base64.getUrlEncoder()
        .withoutPadding().encodeToString(derived);
```

Đoạn minh họa chưa là chương trình độc lập. **Không được biến byte digest tùy ý trực tiếp thành `new String(derived, UTF_8)`**, vì digest có thể chứa byte không biểu diễn được bằng UTF-8 hợp lệ.

### 4.3. Độ dài salt và độ dài output độc lập

Một cấu hình có **salt 16 byte + output 32 byte** là hoàn toàn bình thường. Không có quy tắc salt phải dài bằng hash, hoặc output phải bằng password string length. Với record minh họa:

- Salt: 16 byte random = 128 bit, thường Base64URL không padding dài 22 ký tự.
- Derived verifier: 32 byte = 256 bit, Base64URL không padding dài 43 ký tự.
- Iterations: một số nguyên chi phí, **không phải bit/byte**.
- Version: mã định danh chính sách, **không phải mức bảo mật**.

## 5. Phiên bản cấu hình: xác minh được dữ liệu cũ và nâng cấp được dữ liệu mới

Thông số KDF phải thay đổi theo thời gian khi phần cứng nhanh hơn, thuật toán mới được hỗ trợ, chính sách thay đổi hoặc có yêu cầu bảo mật mới. Nếu chỉ lưu mỗi hash mà **không lưu salt/thuật toán/chi phí/kích thước**, hệ thống có thể không biết phải tính lại như thế nào cho những tài khoản cũ.

**Configuration version** là một nhãn chính sách giúp quản lý lịch sử. Nó **không thay thế** việc lưu các thông số quan trọng của từng record.

Ví dụ phân biệt:

| Trường | Vai trò |
| --- | --- |
| `formatVersion` | Phiên bản schema/record hoặc chính sách ứng dụng, ví dụ v1/v2 |
| `algorithm` | Chính xác thuật toán/KDF dùng khi tạo record |
| `iterations` hoặc `timeCost/memoryCost/parallelism` | Chi phí thực tế của record |
| `derivedKeyBits` | Độ dài đầu ra đã dùng |
| `salt` | Byte salt thực tế của record |
| `hash/verifier` | Byte kết quả đã lưu để so sánh |
| `encoding` | Cách biểu diễn byte sang text, ví dụ Base64URL |
| `pepperKeyId` (nếu dùng) | Định danh key ngoài DB, không phải giá trị pepper bí mật |

### 5.1. Phân biệt ba kiểu “version”

1. **Application policy version:** do đội vận hành xác định, ví dụ `policy=2` khi tăng cost hoặc chuyển KDF. Các tên policy là dữ liệu nội bộ và phải được hiểu theo schema cụ thể.
2. **Record format version:** version của cách serialize/deserialize record; có thể đổi mà KDF không đổi (ví dụ chuyển từ hai cột DB sang chuỗi mã hóa chuẩn).
3. **Algorithm version:** phiên bản *bản thân thuật toán* trong một số họ (ví dụ Argon2 có tham số version). Nó khác với version do ứng dụng tự đặt.

Không dùng một trường `v=2` với ý nghĩa nhập nhằng “chắc chắn đã mạnh”. Một record mang v2 nhưng bị sửa iterations xuống 1 vẫn nguy hiểm nếu code không kiểm tra policy và giới hạn tham số.

### 5.2. Mẫu record giàu thông tin

Một representation có thể dùng cấu trúc:

```text
formatVersion | algorithm | iterations | outputBits | saltBase64Url | verifierBase64Url
```

Hoặc các cấu trúc chuẩn do thư viện password hashing cung cấp, ví dụ **PHC string format** khi thích hợp. Điều quan trọng là server hiểu rõ và validate từng trường. Không tự đặt một chuỗi dùng delimiter có thể lẫn dữ liệu chưa escape; trong môi trường thật cần parser chặt chẽ, giới hạn độ dài và trường hợp có version không hỗ trợ.

**Không lưu pepper trong record**, nếu có dùng nó. Pepper phải thuộc ranh giới secret manager/KMS và được chọn bằng key ID/phiên bản phù hợp.

### 5.3. Luồng đăng nhập với record cũ và nâng cấp

Giả sử server đang nhận record v1 và policy mới là v2:

1. **Đọc record cũ** và kiểm tra format/algorithm/parameter bounds từ allowlist. Không chấp nhận số vòng quá lớn hoặc cấu hình không được hỗ trợ dẫn đến tiêu tốn tài nguyên khi verify.
2. **Xác minh password** nhập bằng **salt và tham số của chính record v1**, không thay tất cả bằng policy v2 trước khi tính lại; làm vậy sẽ khiến mọi password cũ thất bại.
3. Nếu không khớp, **từ chối đăng nhập** và không nâng cấp record.
4. Nếu khớp, tạo **salt mới**, chạy **KDF theo chính sách hiện hành v2** với **password nhập đang có**, tạo verifier mới.
5. **Ghi lại record mới** bằng transaction hoặc cơ chế compare-and-swap dựa vào version/etag của record cũ để tránh ghi đè nâng cấp đồng thời hoặc thay đổi mật khẩu vừa xảy ra.
6. Xóa dữ liệu password tạm theo khả năng, kiểm tra policy kết quả và giữ audit metadata phù hợp.

Đây là **rehash on successful login**. Không thể “nâng vòng lặp” bằng cách chỉ hash lại verifier cũ rồi coi tương đương KDF của password gốc; nếu không có password gốc thì không thể tính lại cùng biểu thức KDF mới. Với tài khoản không đăng nhập lâu, có thể phải giữ hỗ trợ verify legacy trong thời gian xác định hoặc yêu cầu reset password theo chính sách.

### 5.4. Thay thuật toán hoặc tăng chi phí không được làm mất khả năng đăng nhập

Phương án kiểm soát migration:

- **Mọi đăng ký/đổi mật khẩu mới:** tạo record bằng current policy.
- **Đăng nhập thành công của account cũ:** verify bằng old parameters, rehash bằng current policy, cập nhật atomically.
- **Đăng nhập sai:** giữ record cũ, báo lỗi chung và áp rate limiting.
- **Record dùng thuật toán không còn chấp nhận hoặc vi phạm policy tối thiểu:** xử lý theo nhánh migration/reset được duyệt, không tự động cho phép thuật toán yếu mãi mãi.
- **Record không parse được:** fail closed; không thử mọi thuật toán/cấu hình một cách không giới hạn.
- **Thay password:** hash password mới với salt mới, current parameters, đánh dấu phiên bản mới.
- **Rollback ứng dụng:** chỉ thực hiện nếu phiên bản rollback có thể hiểu các record mới hoặc đã có kế hoạch tương thích; không âm thầm downgrade record về cấu hình yếu hơn.

## 6. Java 17+: tạo verifier, xác minh và rehash theo phiên bản

Ví dụ độc lập dưới đây dùng **PBKDF2WithHmacSHA256** có sẵn trong JCA/JCE để minh họa **đủ bốn tham số**. Chương trình tạo salt 16 byte bằng `SecureRandom`, dẫn xuất output 256 bit, lưu cấu hình với record, xác minh bằng thông số được lưu và nâng một record v1 lên v2 **sau khi password chính xác được xác nhận**.

Các giá trị **100.000 và 180.000 vòng chỉ dùng cho demo chạy nhanh và thấy được sự khác biệt phiên bản; không phải mức cấu hình triển khai**. Ở sản phẩm cần chọn password KDF và cost theo hướng dẫn hiện hành cùng benchmark riêng.

Lưu thành **PasswordPolicyDemo.java**:

```java
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordPolicyDemo {
    static final String ALG = "PBKDF2WithHmacSHA256";
    static final int HASH_BITS = 256;
    static final int OLD_ROUNDS = 100_000;
    static final int CURRENT_ROUNDS = 180_000;
    static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    record Credential(int version, String algorithm, int iterations,
                      int bits, String saltBase64Url, String hashBase64Url) {}

    static byte[] pbkdf2(char[] password, byte[] salt,
                         int iterations, int bits)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bits);
        try {
            return SecretKeyFactory.getInstance(ALG)
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    static Credential enroll(char[] password, int version, int iterations)
            throws GeneralSecurityException {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, iterations, HASH_BITS);
        try {
            return new Credential(version, ALG, iterations, HASH_BITS,
                    ENCODER.encodeToString(salt),
                    ENCODER.encodeToString(hash));
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    static boolean verify(char[] password, Credential stored)
            throws GeneralSecurityException {
        // Chính sách đọc record: từ chối thuật toán/tham số không hỗ trợ.
        if (!ALG.equals(stored.algorithm()) || stored.version() < 1
                || stored.version() > 2 || stored.iterations() < 10_000
                || stored.iterations() > 1_000_000
                || stored.bits() != HASH_BITS) {
            return false;
        }

        byte[] salt;
        byte[] expected;
        try {
            salt = DECODER.decode(stored.saltBase64Url());
            expected = DECODER.decode(stored.hashBase64Url());
        } catch (IllegalArgumentException invalidEncoding) {
            return false;
        }
        if (salt.length != 16 || expected.length != HASH_BITS / 8) {
            return false;
        }

        byte[] actual = pbkdf2(password, salt,
                               stored.iterations(), stored.bits());
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    static boolean needsUpgrade(Credential c) {
        return c.version() < 2 || c.iterations() < CURRENT_ROUNDS;
    }

    public static void main(String[] args) throws Exception {
        char[] password = "example-password-only".toCharArray();
        Credential old = enroll(password, 1, OLD_ROUNDS);
        Credential another = enroll(password, 1, OLD_ROUNDS);

        System.out.println("salt bytes: "
                + DECODER.decode(old.saltBase64Url()).length);
        System.out.println("derived bytes: "
                + DECODER.decode(old.hashBase64Url()).length);
        System.out.println("correct password: " + verify(password, old));
        System.out.println("wrong password: "
                + verify("wrong-password".toCharArray(), old));
        System.out.println("distinct salts: "
                + !old.saltBase64Url().equals(another.saltBase64Url()));
        System.out.println("distinct hashes: "
                + !old.hashBase64Url().equals(another.hashBase64Url()));

        Credential changedSalt = new Credential(
                old.version(), old.algorithm(), old.iterations(), old.bits(),
                another.saltBase64Url(), old.hashBase64Url());
        System.out.println("changed salt rejected: "
                + !verify(password, changedSalt));
        System.out.println("old needs upgrade: " + needsUpgrade(old));

        if (verify(password, old)) {
            Credential fresh = enroll(password, 2, CURRENT_ROUNDS);
            System.out.println("new verifies: " + verify(password, fresh));
            System.out.println("new needs upgrade: " + needsUpgrade(fresh));
        }
        Arrays.fill(password, (char) 0);
    }
}
```

Biên dịch/chạy với Java 17+:

```bash
javac -encoding UTF-8 PasswordPolicyDemo.java
java PasswordPolicyDemo
```

Stdout kỳ vọng (đã kiểm tra hành vi KDF bằng Java; byte salt ngẫu nhiên không được in ra):

```text
salt bytes: 16
derived bytes: 32
correct password: true
wrong password: false
distinct salts: true
distinct hashes: true
changed salt rejected: true
old needs upgrade: true
new verifies: true
new needs upgrade: false
```

**Cách đọc code theo từng tham số:**

- **Salt:** `new SecureRandom().nextBytes(salt)` tạo salt 16 byte riêng khi `enroll()` chạy. Hai lần enroll cùng password có salt khác nhau và verifier khác nhau. Cả salt và verifier được encode Base64URL (không padding) để minh họa cách lưu text.
- **Số vòng:** `PBEKeySpec(..., iterations, bits)` truyền số vòng của từng record. `verify()` dùng lại `stored.iterations()`; rehash v2 dùng `CURRENT_ROUNDS`. Các ngưỡng kiểm tra trong `verify()` là giới hạn demo để không thực thi cấu hình không kiểm soát.
- **Độ dài output:** `HASH_BITS = 256` tương ứng output 32 byte; `HASH_BITS / 8` dùng để kiểm tra độ dài mảng hash sau khi decode.
- **Version:** `Credential` lưu `version` và `algorithm` cùng các tham số. `needsUpgrade()` so sánh với chính sách mới; bản ghi mới chỉ được tạo trong nhánh password đã được xác minh.
- **Giữ password:** `char[]` và `PBEKeySpec.clearPassword()` giảm một số tồn lưu không cần thiết, nhưng không bảo đảm toàn bộ password biến mất ngay khỏi JVM memory; input demo là chuỗi literal, tuyệt đối không áp dụng cách hardcode đó cho password thật.
- **So sánh:** `MessageDigest.isEqual(byte[], byte[])` dùng cho verifier byte array. Tránh chuyển output nhị phân thành chuỗi không rõ encoding; không log verifier hay password.
- **Quy tắc chống tấn công tài nguyên:** `verify()` giới hạn version, iteration, output size và salt length. Nếu xử lý record từ DB bị thao túng, phải áp **allowlist + giới hạn thực tế theo policy** trước khi gọi KDF để tránh DoS qua iterations cực lớn.
- **Lưu trữ:** ví dụ chỉ tạo các `record` trong memory; phần cập nhật **transaction/cas** của database không được giả làm đã triển khai. Trong ứng dụng thực, cần atomic update và bảo vệ trước thay đổi mật khẩu/rehash đồng thời.

Các mệnh đề “distinct salts/hashes” có tính xác suất rất cao khi nguồn ngẫu nhiên hoạt động đúng; việc hai salt 128 bit ngẫu nhiên tình cờ trùng nhau là cực kỳ hiếm, không phải bất khả thi về mặt toán học.

### 6.1. Cấu hình minh họa khác gì cấu hình production?

Chương trình nhằm làm rõ luồng dữ liệu nên cố ý giản lược:

- Demo chỉ có **hai version**, cùng một thuật toán PBKDF2; sản phẩm có thể cần **registry** cho nhiều thuật toán, key ID pepper và nhiều phiên bản cùng tồn tại.
- Demo mã hóa record thành các trường Java, chưa cung cấp parser DB hoặc giao thức serialized chống sửa đổi metadata; trong triển khai thật, ràng buộc schema và quyền sửa DB rất quan trọng.
- Demo so sánh password đúng/sai trong bộ nhớ, không có bảo vệ login endpoint (rate limiting, MFA, logging an toàn, abuse detection).
- Demo hiển thị số vòng để giải thích, không benchmarking và không cam kết một thời gian chạy cụ thể trên JVM khác.
- Demo không bàn giao output KDF làm khóa mã hóa; khi dùng KDF cho encryption cần thiết kế key lifecycle và bảo vệ data tương ứng.

## 7. Cách triển khai phiên bản và nâng cấp không làm mất dữ liệu

### 7.1. Mỗi record tự mô tả cấu hình đã tạo ra nó

Một credential lưu bằng PBKDF2 với 100.000 vòng và salt 16 byte phải **được xác minh bằng đúng thông số đó**; dù ứng dụng đã nâng policy lên 180.000 vòng, bạn không thể tự ý bỏ qua metadata cũ.

Tránh thiết kế chỉ có các biến global hiện hành:

```text
CURRENT_ROUNDS = 180000
verify(oldCredential, password) = PBKDF2(password, oldSalt, CURRENT_ROUNDS)
```

Dạng này sẽ **làm sai kết quả** với record v1. Thay vào đó, chọn thuật toán và chi phí lưu trong record sau khi parser đã validate, verify trước, rồi rehash với current policy.

### 7.2. Nâng cấp tại thời điểm xác thực thành công

Một quy trình triển khai rõ ràng:

```text
READ credential record
    -> VALIDATE supported schema, alg, cost and length limits
    -> VERIFY using stored parameters
       -> mismatch: reject, do not modify credential
       -> match:
          -> if current policy satisfied: sign in
          -> else: generate NEW salt + current parameters
                   derive NEW verifier from entered password
                   persist atomically (version/CAS condition)
                   sign in
```

Khi current policy mới dùng **Argon2id** nhưng record cũ là **PBKDF2**, verifier cũ vẫn dùng PBKDF2 để verify; sau khi thành công có thể chuyển sang Argon2id qua thư viện được phê duyệt. Không tự chọn thuật toán mới dựa trên `hash.length` hoặc chuỗi tự đặt.

### 7.3. Điều chỉnh tham số phải xét hiệu năng và abuse

Các rủi ro vận hành:

- Một lượng lớn account nâng cấp đồng thời có thể tăng CPU/memory usage; cần capacity planning.
- Nếu attacker có thể làm server xử lý một record với `iterations` cực lớn hoặc `memoryCost` quá mức, endpoint login dễ bị DoS. Validate bounds trước khi gọi KDF.
- Nếu tăng cost nhưng không giới hạn retry, attacker vẫn gây tải lớn cho dịch vụ xác minh.
- Nếu cho phép “downgrade” dễ dàng qua client header hoặc record do user sửa, một số luồng có thể tiếp tục dùng policy yếu vô hạn.
- Nếu thay đổi chuẩn hóa/encoding Unicode của password mà không version hóa, cùng chuỗi hiển thị có thể tạo byte đầu vào khác; người dùng hợp lệ bị từ chối.

## 8. Encoding password, Unicode và interoperability

Salt và verifier là byte array. Nhưng người dùng nhập **ký tự**; khi đưa password sang KDF, phải có cách chuyển ký tự thành byte rõ ràng theo thuật toán/provider.

- Với `PBEKeySpec`, input là `char[]` của Java (các đơn vị mã UTF-16); provider KDF xử lý theo định nghĩa thuật toán/provider. Khi cần **tương thích giữa Java và ngôn ngữ khác**, phải kiểm tra quy tắc encoding cụ thể bằng test vector và ghi cùng version; không tự giả định tất cả thư viện có cùng biến đổi Unicode.
- Một số hệ thống chuẩn hóa Unicode (ví dụ NFC) trước KDF; một số không chuẩn hóa. Đây là quyết định tương thích và bảo mật phải giữ ổn định cho record cũ. **Không tự thêm bước normalize ở phiên bản mới mà không có migration**.
- Không dùng `String.length()` để đo số byte UTF-8 của password; khi cần byte length, chuyển encoding đúng rồi đếm. Với bcrypt, chú ý giới hạn byte đầu vào theo implementation, đặc biệt với Unicode.
- Tốt hơn nên có test vector cho password chứa dấu tiếng Việt, ký tự ngoài BMP, salt và tham số biết trước để kiểm chứng toàn bộ hệ thống client/server/server mới.

Bảo vệ input password trên đường truyền bằng TLS, không đưa password vào URL, log, analytics, exception hoặc crash report. Ở mức ứng dụng, đừng trim hay đổi chữ hoa/thường của password theo ý thích: thay đổi này có thể ảnh hưởng tính đúng đắn và làm giảm không gian mật khẩu.

## 9. Salt, số vòng và version trong dẫn xuất khóa mã hóa

Nếu một password KDF tạo **khóa AES để mở dữ liệu**, các tham số có tác dụng tương tự nhưng hậu quả nâng cấp khác password verifier:

- Đổi salt, iterations, KDF hoặc output length sẽ **tạo khóa byte khác**, nên dữ liệu mã hóa cũ có thể không giải mã được.
- Phải lưu metadata KDF cùng envelope dữ liệu mã hóa, hoặc có hệ thống quản lý key version và salt đúng với từng payload.
- Khi muốn nâng cấu hình, thường cần lấy lại mật khẩu, dẫn xuất khóa cũ, **giải mã an toàn**, rồi mã hóa lại bằng khóa mới; hoặc dùng mô hình **key-encryption key / data-encryption key** để hỗ trợ rewrap theo thiết kế.
- Nếu vừa dùng cùng password cho verifier và mã hóa, không được sử dụng **cùng derived bytes** trực tiếp cho hai mục đích. Có thể thiết kế tách KDF purpose/context rõ ràng hoặc một bước HKDF dẫn xuất subkey có scope khác nhau sau khi tạo vật liệu thích hợp.
- Lựa chọn output 32 byte cho AES-256 **không chứng minh khóa có entropy 256 bit** nếu password yếu. Sự an toàn còn phụ thuộc việc attacker có offline oracle để kiểm tra password không.
- Quản lý **nonce/IV** của thuật toán mã hóa riêng biệt với salt của KDF. Với AES-GCM chẳng hạn, nonce cần duy nhất với cùng AES key; nó không được thay thế bằng “một salt bất kỳ” rồi bỏ qua quy tắc nonce.

Đối với password verifier, rehash sau đăng nhập thành công đơn giản hơn vì server chỉ cần lưu verifier mới. Đối với encryption, phải bảo đảm **khôi phục được dữ liệu cũ** trước khi thay khóa hoặc khóa bọc.

## 10. Các lỗi thường gặp và hậu quả

| Nhầm lẫn | Hậu quả | Cách sửa |
| --- | --- | --- |
| Một salt cho tất cả tài khoản | Cùng password tạo cùng verifier, tấn công đa mục tiêu dễ hơn | Sinh salt ngẫu nhiên riêng khi tạo mỗi record |
| Coi salt là secret key | Thiết kế sai ranh giới và không bảo vệ password phù hợp | Salt công khai; nếu có pepper, quản lý bí mật riêng |
| Salt tạo từ email, username hoặc timestamp | Dễ đoán, dễ lặp hoặc quá ít entropy | Dùng CSPRNG với độ dài theo KDF/policy |
| Tăng output 256 → 512 bit để “tăng entropy password” | Không cải thiện độ khó đoán password yếu | Dùng password/passphrase tốt và chi phí KDF hợp lý |
| Dùng SHA-256 nhanh trực tiếp | Dễ thử nhiều candidate offline | Chọn password KDF có cost phù hợp |
| Truyền `32` vào `PBEKeySpec` khi muốn 32 byte | Chọn nhầm 32 bit output | Truyền **256 bit**, kiểm tra byte length |
| Tăng PBKDF2 iteration chỉ dựa trên phỏng đoán | Có thể quá yếu hoặc gây chậm/DoS | Benchmark, policy, giới hạn concurrency và rate limiting |
| Đem cùng số iterations dùng cho Argon2id/bcrypt | Chi phí thuật toán hoàn toàn khác | Dùng bộ tham số đúng của từng KDF |
| Chỉ lưu digest, không lưu salt/chi phí/alg | Không xác minh/migrate được record cũ | Lưu self-describing format và policy version |
| Verify record cũ bằng parameters hiện hành | Người dùng đúng password vẫn bị từ chối | Verify bằng stored config, rehash sau khi pass |
| Chỉ sửa số vòng trong DB, giữ nguyên verifier | Verifier không khớp với password | Rehash lại với **password đúng** và salt mới |
| Cho record tự khai cost không giới hạn | DoS thông qua yêu cầu KDF quá nặng | Allowlist và giới hạn tham số trước khi thực thi |
| Truncate/encode digest tùy tiện | Verifier không khớp hoặc bị rút ngắn ngoài policy | Lưu chính xác output bytes và encoding |
| Bỏ qua Unicode normalization/version | Password hợp lệ có thể ngừng hoạt động | Giữ cùng quy tắc encoding theo version và test vector |
| Dùng cùng output KDF làm verifier/AES/HMAC key | Cross-purpose key reuse | Tách vật liệu khóa/context/purpose rõ ràng |
| Ghi plaintext password hoặc pepper trong DB/log | Rò rỉ thông tin xác thực/secret | Chỉ lưu verifier, salt và metadata; pepper bảo quản riêng |
| In toàn bộ verifier để debug | Tăng nguy cơ lộ dữ liệu hỗ trợ tấn công offline | Log metadata không nhạy cảm và correlation ID, hạn chế truy cập DB |

## 11. Checklist kiểm tra một cấu hình password KDF

1. **Thuật toán:** đã chọn KDF phù hợp với mục đích password hashing/password-based key derivation và điều kiện sử dụng chưa?
2. **Salt:** có **salt ngẫu nhiên độc lập** cho mỗi record, đủ dài và lưu đúng byte, không lẫn với pepper/nonce không?
3. **Chi phí:** PBKDF2 iterations hoặc Argon2id memory/time/parallelism được đo trên phần cứng triển khai và cập nhật theo policy chưa?
4. **Output:** hiểu rõ bit hay byte theo API; verifier có độ dài và encoding đúng không?
5. **Version:** record lưu alg, params, salt, output, format/policy version; parser có allowlist, bounds và xử lý version không hỗ trợ không?
6. **Verification:** dùng đúng thông số record cũ, so sánh byte an toàn, có rate limiting và tránh leak dữ liệu qua error/log không?
7. **Migration:** sau đăng nhập đúng có rehash theo current policy với salt mới và cập nhật atomically không? Account ít đăng nhập có kế hoạch migration/reset không?
8. **Encoding:** password Unicode được xử lý nhất quán giữa JVM/provider, phiên bản sản phẩm và các client không?
9. **Secret/pepper:** nếu có pepper, nó được quản lý ngoài DB và có quy trình rotation/recovery không?
10. **Ứng dụng crypto:** nếu output thành khóa mã hóa, đã thiết kế restore/rewrap/re-encrypt, key separation và IV/nonce độc lập chưa?

## 12. Câu hỏi tình huống để tự kiểm tra

**Câu 1.** Alice và Bob đều chọn cùng password; hệ thống dùng PBKDF2 với salt 16 byte sinh độc lập. Hai verifier có nhất thiết giống nhau không?  
**Trả lời:** **Không**. Salt khác nhau nên output KDF hầu như chắc chắn khác, dù password và parameters giống nhau.

**Câu 2.** Salt bị lộ nhưng password chưa lộ. Hệ thống có lập tức mất toàn bộ tính bảo mật không?  
**Trả lời:** Không chỉ vì salt lộ, vì salt vốn không cần giữ bí mật. Nhưng nếu attacker có cả verifier và salt, họ có thể thử password offline; độ mạnh password và KDF vẫn rất quan trọng.

**Câu 3.** Một policy mới tăng PBKDF2 iterations từ cũ lên mức mới. Khi kiểm tra login cho record cũ, dùng số nào?  
**Trả lời:** Dùng **iterations lưu trong record cũ** để verify, rồi sau khi password khớp, rehash bằng số vòng **policy mới** và salt mới.

**Câu 4.** `PBEKeySpec` cần đầu ra 32 byte. `keyLength` là bao nhiêu?  
**Trả lời:** **256**, vì Java nhận độ dài theo bit. `byte[].length` của output dự kiến là **32**.

**Câu 5.** Đổi output từ 32 byte sang 64 byte làm mật khẩu “password123” mạnh gấp đôi không?  
**Trả lời:** **Không**. Output dài hơn không làm mật khẩu dễ đoán tăng entropy. Chi phí thử và chất lượng password cần đánh giá riêng.

**Câu 6.** Người dùng có verifier v1 nhưng database bị đổi `iterations=999999999`. Backend có nên chạy KDF theo đó?  
**Trả lời:** **Không**. Phải validate version/algorithm và giới hạn cost trước khi thực thi; dữ liệu bất hợp lệ nên bị từ chối và xử lý theo chính sách.

**Câu 7.** Cùng password, salt và KDF parameters, nhưng hai implementation dùng quy tắc Unicode encoding khác nhau. Verifier có bảo đảm khớp không?  
**Trả lời:** **Không**. Cần ghi rõ cách chuyển password thành input, test vector liên nền tảng và version hóa thay đổi này.

**Câu 8.** Bản ghi chứa output 32 byte, salt 16 byte và tên thuật toán nhưng không lưu iteration count. Có đủ để xác minh nhiều version theo cách xác định không?  
**Trả lời:** **Không đủ** nếu các version dùng số vòng khác nhau. Phải có iteration count hoặc ánh xạ version → tham số tuyệt đối rõ ràng, không nhập nhằng, lâu dài.

**Câu 9.** Đổi password người dùng và salt nhưng giữ nguyên dữ liệu được mã hóa bằng khóa dẫn xuất từ password cũ. Dữ liệu có tự mở bằng khóa mới không?  
**Trả lời:** **Không**. Phải thiết kế giải mã/mã hóa lại hoặc key wrapping/rewrap theo kiến trúc quản lý khóa và phiên bản.

### Điều cần nhớ

**Salt** bảo đảm tính riêng biệt giữa các bản ghi; **iteration/cost** làm mỗi phép thử password tốn tài nguyên hơn; **output length** xác định kích thước byte kết quả; **configuration version** giữ được khả năng xác minh, nâng cấp và tương thích. Một password KDF được vận hành an toàn cần đủ cả bốn, cộng với mật khẩu đủ mạnh, giới hạn thử đăng nhập, xử lý Unicode nhất quán và quản lý vòng đời cấu hình rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="password-key-derivation-004">7.4 PBKDF2 qua SecretKeyFactory và PBEKeySpec</a>

<details>
<summary>Xem chi tiết</summary>

# 7.4. PBKDF2 qua `SecretKeyFactory` và `PBEKeySpec` trong Java 21

**PBKDF2** (*Password-Based Key Derivation Function 2*) biến một mật khẩu do người dùng chọn thành chuỗi byte dẫn xuất bằng cách kết hợp **salt**, **số vòng lặp** (*iteration count*) và một hàm giả ngẫu nhiên (*PRF*, thường là HMAC). Trong Java 21, có thể sử dụng PBKDF2-HMAC-SHA256 qua API chuẩn `SecretKeyFactory` và `PBEKeySpec` của JCA/JCE, không cần thêm dependency cho ví dụ cơ bản.

Đây là chủ đề thuộc **“7. Mật khẩu và dẫn xuất khóa”** trong module Java Advanced – Security & Cryptography. Trọng tâm là hiểu đúng các tham số, dùng API Java để dẫn xuất/kiểm tra mật khẩu, lưu bản ghi có thể xác minh về sau, chọn chi phí phù hợp, và nhận ra các giới hạn bảo mật của PBKDF2.

## Mục tiêu

Sau khi học xong, bạn có thể:

1. Giải thích `PBKDF2(P, S, c, dkLen)`, vai trò của **password**, **salt**, **iterations**, **PRF** và **derived-key length**.
2. Phân biệt **hash mật khẩu để xác minh đăng nhập** với **dẫn xuất khóa cho mã hóa**, và phân biệt salt với pepper.
3. Dùng `new PBEKeySpec(char[], byte[], int, int)` và `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` để tạo byte dẫn xuất.
4. Xây dựng bản ghi có phiên bản/thuật toán/cost/salt/hash, xác minh bằng cách dẫn xuất lại và so sánh an toàn.
5. Kiểm tra ví dụ Java 21 có kết quả chính xác, xử lý bộ nhớ chứa mật khẩu, tránh salt yếu và chọn tham số có cơ sở.
6. Giải thích khi nào PBKDF2 là lựa chọn phù hợp, khi nào cần cân nhắc Argon2id, và cách nâng cấp tham số mà không làm mất tài khoản.

## 1. Vấn đề PBKDF2 giải quyết

Một mật khẩu như `"my-password"` thường có entropy thấp hơn rất nhiều so với khóa ngẫu nhiên 256 bit. **Không nên** lưu mật khẩu bản rõ, không lưu bản mã có thể giải mã để đăng nhập, và không lưu trực tiếp `SHA-256(password)` như một password verifier: SHA-256 tính rất nhanh nên kẻ tấn công có bản sao database có thể thử rất nhiều ứng viên.

PBKDF2 tăng chi phí thử một mật khẩu bằng cách lặp lại PRF nhiều lần. Với cùng password và **cùng** salt/iterations/PRF/key length, kết quả là **xác định**; với salt khác, kết quả gần như chắc chắn khác.

```text
Password (P) --\
                +--> PBKDF2(PRF, iterations=c) --> Derived key (DK)
Random salt (S)-/                         |
                                         \--> lưu/verifier hoặc dùng làm key material
```

**PBKDF2 không tự “mã hóa mật khẩu”.** Đó là hàm dẫn xuất khóa; khi áp dụng để xác minh đăng nhập, ứng dụng lưu **giá trị dẫn xuất** và tham số, không lưu password. Người đăng nhập gửi mật khẩu ứng viên; server tính lại PBKDF2 với salt/cost được lưu rồi so sánh.

**Mật khẩu vẫn có thể bị đoán ngoại tuyến** nếu database hash bị lộ; PBKDF2 làm việc đoán tốn CPU hơn, nhưng không thay thế mật khẩu mạnh, MFA, kiểm soát truy cập, giám sát xâm nhập, giới hạn thử đăng nhập hoặc chống rò rỉ bí mật.

## 2. Năm thành phần đầu vào và đầu ra

Ký hiệu chuẩn:

```text
DK = PBKDF2(P, S, c, dkLen; PRF)
```

| Thành phần | Ý nghĩa | Ví dụ trong bài |
| --- | --- | --- |
| `P` | Password, dữ liệu bí mật người dùng biết. Với Java, API nhận `char[]`; quy tắc chuyển ký tự thành byte cần thống nhất khi liên thông hệ thống. | `char[] password` |
| `S` | Salt: byte ngẫu nhiên **riêng cho từng bản ghi**, không cần giữ bí mật. | `byte[16]` = 128 bit từ `SecureRandom` |
| `c` | Số lần lặp PBKDF2, **số nguyên dương**; tăng c làm mỗi lần đoán tốn thời gian hơn. | `600_000` vòng cho ví dụ lưu mật khẩu |
| `dkLen` | Độ dài đầu ra, **tính bằng byte trong công thức PBKDF2**. | 32 byte |
| `PRF` | Hàm giả ngẫu nhiên, thường là `HMAC-SHA256` hoặc `HMAC-SHA512`. | `HMAC-SHA256` |
| `DK` | Byte dẫn xuất có độ dài đã chọn. | `byte[32]` |

**Chi tiết rất dễ nhầm:** tham số `keyLength` trong constructor Java `PBEKeySpec(password, salt, iterationCount, keyLength)` có đơn vị **bit**, không phải byte. Muốn đầu ra 32 byte cần truyền **256**, chứ không phải 32.

Salt dài 16 byte được chọn mới cho **mỗi lần tạo hash/mỗi tài khoản** là lựa chọn thông dụng. Có thể lưu salt công khai cạnh hash; không cần “mã hóa salt” để tăng bảo mật. Salt giúp chống bảng tính trước và khiến hai người dùng cùng mật khẩu không có cùng bản ghi hash. Salt **không phải khóa bí mật** và không tự chặn thử mật khẩu yếu.

**Pepper** khác salt: pepper là bí mật cấp ứng dụng, thường lưu tách database (KMS/HSM hoặc secret manager). Pepper có thể bổ sung phòng vệ nhưng phải có kế hoạch rotate, mất khóa và khôi phục; pepper không thay được salt hay work factor.

## 3. PBKDF2 vận hành thế nào?

Với `PBKDF2WithHmacSHA256`, PRF là HMAC-SHA256 có output 32 byte. PBKDF2 tính các khối đầu ra bằng chuỗi PRF lặp lại và phép XOR.

Ở khối thứ `i` (đánh số từ 1), diễn đạt từ RFC 8018:

```text
U_1 = PRF(P, S || INT_32_BE(i))
U_2 = PRF(P, U_1)
U_3 = PRF(P, U_2)
...
U_c = PRF(P, U_(c-1))

T_i = U_1 XOR U_2 XOR ... XOR U_c
DK  = (T_1 || T_2 || ... ) lấy đúng dkLen byte
```

Trong đó `INT_32_BE(i)` là số khối biểu diễn bằng 4 byte big-endian; `||` là phép nối. Nếu cần 32 byte từ HMAC-SHA256, chỉ cần một khối; nếu cần 64 byte, sẽ có hai khối. Thời gian tính gần tỷ lệ với số vòng lặp và số khối đầu ra.

Một số tính chất quan trọng:

- Cùng `P, S, c, dkLen, PRF` phải cho cùng `DK` khi cách chuyển password thành byte giống nhau.
- Tăng `c` không làm mật khẩu tự có thêm entropy; mục đích là làm **mỗi lần thử** tốn thời gian hơn.
- Salt mới không làm password mạnh hơn, nhưng ngăn việc tái sử dụng kết quả dò tìm tiền tính cho mọi tài khoản.
- Sinh `DK` dài 256 bit không biến một mật khẩu có vài bit entropy thành khóa ngẫu nhiên có 256 bit entropy.
- PBKDF2 chủ yếu tốn CPU, **không memory-hard** như Argon2id; do đó không phải lựa chọn ưu tiên mặc định cho mọi hệ thống mật khẩu mới.

Thông thường **không tự triển khai** vòng lặp `U_i`/XOR trong ứng dụng Java. Dùng implementation JCA/JCE đã được kiểm thử, rồi tập trung thiết kế salt, cost, storage, so sánh và quản trị vòng đời.

## 4. API Java 21: `SecretKeyFactory` + `PBEKeySpec`

Các import chính:

```java
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
```

Luồng API cơ bản:

```java
char[] password = /* mật khẩu lấy từ input an toàn */;
byte[] salt = new byte[16];
new SecureRandom().nextBytes(salt);

PBEKeySpec spec = new PBEKeySpec(password, salt, 600_000, 256);
try {
    SecretKeyFactory factory =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    byte[] derived = factory.generateSecret(spec).getEncoded();
    // Dùng derived theo mục đích đã định; không log và xóa khi xong.
} finally {
    spec.clearPassword();
    // Caller còn trách nhiệm xử lý/xóa mảng password của chính mình.
}
```

**Đây là đoạn mã minh họa**, không phải file Java chạy độc lập: biến `password` phải lấy từ input thật và byte `derived` cần được quản lý trong scope thích hợp. Phần 5 và 6 có chương trình độc lập, biên dịch được.

Các thành phần:

- `PBEKeySpec` là đối tượng mô tả **password, salt, iteration count, key length**. Constructor kiểm tra salt không null/rỗng, count và key length dương; đối tượng **clone** password và salt để lưu nội bộ.
- `clearPassword()` xóa **bản sao password trong PBEKeySpec**, không xóa bản gốc do caller giữ. Khi không còn cần, caller phải `Arrays.fill(password, '\0')` trong `finally`. `getPassword()` trả bản sao; không nên gọi chỉ để kiểm tra hoặc log.
- `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` chọn implementation từ **security provider** có hỗ trợ tên thuật toán này. Tên này thuộc Java Security Standard Algorithm Names; SunJCE trong JDK 21 cung cấp implementation. Không phải mọi provider bên ngoài đều bắt buộc hỗ trợ.
- `generateSecret(spec)` tạo `SecretKey`. Với provider hỗ trợ xuất key material, `getEncoded()` trả mảng byte của khóa dẫn xuất (đối với demo PBKDF2). Provider có thể từ chối xuất khóa và trả `null`; code sản phẩm nên xử lý trường hợp đó.
- `getEncoded()` là byte nhạy cảm: không chuyển sang `String` để log, không trả về API response, và chỉ giữ trong RAM trong thời gian cần thiết.
- `NoSuchAlgorithmException` chỉ ra provider/runtime không cung cấp thuật toán; `InvalidKeySpecException` hoặc lỗi tham số liên quan mật khẩu/salt/cost cần được xử lý như lỗi cấu hình/triển khai, không đánh đồng với việc “user nhập sai password”.

**Chú ý Unicode và tính tương thích:** Javadoc của `PBEKeySpec` lưu ý các cơ chế password-based có thể sử dụng số bit khác nhau của ký tự; tài liệu provider còn có giới hạn riêng. Khi cần so khớp hash giữa Java, Node.js, Python hoặc giữa các provider, phải quy định rõ encoding/normalization và kiểm thử với mật khẩu **có dấu, emoji, ký tự kết hợp**; đừng mặc định hai nơi cùng truyền `"mật khẩu"` sẽ ra cùng byte đầu vào. Ví dụ deterministic ở phần tiếp theo dùng ASCII để không tạo giả định Unicode.

## 5. Thí nghiệm kiểm chứng: test vector PBKDF2-HMAC-SHA256

Ví dụ đầu tiên sử dụng password ASCII `password`, salt ASCII `salt`, output 256 bit và chỉ **1 hoặc 2 lần lặp** để tạo vector có thể kiểm tra từng byte. Chi phí này chỉ dành cho **kiểm thử chức năng**; tuyệt đối không sử dụng 1–2 iterations để lưu password người dùng.

Tạo file **`Pbkdf2Vector.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Pbkdf2Vector {
    public static void main(String[] args) throws Exception {
        char[] password = "password".toCharArray();
        byte[] salt = "salt".getBytes(StandardCharsets.US_ASCII);
        try {
            for (int iterations : new int[] {1, 2}) {
                PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
                try {
                    byte[] derived = SecretKeyFactory
                            .getInstance("PBKDF2WithHmacSHA256")
                            .generateSecret(spec).getEncoded();
                    System.out.println(iterations + ": " +
                            HexFormat.of().formatHex(derived));
                    Arrays.fill(derived, (byte) 0);
                } finally {
                    spec.clearPassword();
                }
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
```

Trong terminal đã dùng JDK 21 (hoặc compiler mới hơn hỗ trợ `--release 21`):

```text
javac -version
javac --release 21 -encoding UTF-8 Pbkdf2Vector.java
java Pbkdf2Vector
```

Stdout chính xác:

```text
1: 120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b
2: ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43
```

Bạn nên tự giải thích:

- Đầu vào chỉ khác **`iterations`**, nên output khác.
- Mỗi kết quả hexadecimal dài 64 ký tự = 32 byte = 256 bit.
- `HexFormat` dùng để **hiển thị kết quả thử nghiệm**; không có tác dụng làm PBKDF2 mạnh hơn.
- Code chỉ là vector chức năng; với mật khẩu thực, salt phải dài/ngẫu nhiên riêng cho từng bản ghi và count phải đủ lớn.

## 6. Ví dụ lưu và xác minh mật khẩu hoàn chỉnh

Mục tiêu: một API đăng ký tạo salt ngẫu nhiên và record lưu trữ; lúc đăng nhập, server đọc record rồi PBKDF2 lại mật khẩu ứng viên. Không có phép giải mã password.

Định dạng bản ghi sử dụng trong ví dụ:

```text
pbkdf2-sha256:600000:<salt-base64url>:<hash-base64url>
```

Trong đó `pbkdf2-sha256` xác định thuật toán, `600000` là work factor được dùng khi tạo, `salt` là 16 byte ngẫu nhiên mã hóa Base64URL và `hash` là 32 byte dẫn xuất mã hóa Base64URL. Ký tự `:` chỉ là delimiter do **ứng dụng này tự định nghĩa**, không phải format tiêu chuẩn chung cho mọi thư viện Java. Base64 dùng để lưu byte vào trường văn bản, **không phải mã hóa bí mật**.

Tạo file **`PasswordHashDemo.java`**:

```java
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHashDemo {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String ID = "pbkdf2-sha256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int ROUNDS = 600_000;
    private static final int MAX_ROUNDS = 3_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    static byte[] derive(char[] password, byte[] salt,
                         int iterations, int bits)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bits);
        try {
            byte[] derived = SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec).getEncoded();
            if (derived == null || derived.length != bits / 8) {
                throw new GeneralSecurityException("PBKDF2 output unavailable");
            }
            return derived;
        } finally {
            spec.clearPassword();
        }
    }

    static String createRecord(char[] password)
            throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ROUNDS, KEY_BITS);
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return ID + ":" + ROUNDS + ":" +
                    encoder.encodeToString(salt) + ":" +
                    encoder.encodeToString(derived);
        } finally {
            Arrays.fill(derived, (byte) 0);
        }
    }

    static boolean verify(char[] candidate, String record)
            throws GeneralSecurityException {
        String[] fields = record.split(":", -1);
        if (fields.length != 4 || !ID.equals(fields[0])) {
            return false;
        }
        int iterations;
        byte[] salt;
        byte[] expected;
        try {
            iterations = Integer.parseInt(fields[1]);
            if (iterations < ROUNDS || iterations > MAX_ROUNDS) {
                return false;
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            salt = decoder.decode(fields[2]);
            expected = decoder.decode(fields[3]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (salt.length != SALT_BYTES || expected.length != KEY_BITS / 8) {
            Arrays.fill(expected, (byte) 0);
            return false;
        }
        try {
            byte[] actual = derive(candidate, salt, iterations, KEY_BITS);
            try {
                return MessageDigest.isEqual(actual, expected);
            } finally {
                Arrays.fill(actual, (byte) 0);
            }
        } finally {
            Arrays.fill(expected, (byte) 0);
        }
    }

    public static void main(String[] args) throws Exception {
        // Chuỗi literal chỉ để demo; password thực lấy từ input an toàn.
        char[] registration = "correct horse battery staple".toCharArray();
        char[] correct = "correct horse battery staple".toCharArray();
        char[] wrong = "wrong password".toCharArray();
        try {
            String record = createRecord(registration);
            System.out.println("format: " + record.startsWith(ID + ":"));
            System.out.println("correct: " + verify(correct, record));
            System.out.println("wrong: " + verify(wrong, record));
        } finally {
            Arrays.fill(registration, '\0');
            Arrays.fill(correct, '\0');
            Arrays.fill(wrong, '\0');
        }
    }
}
```

Biên dịch và chạy:

```text
javac --release 21 -encoding UTF-8 PasswordHashDemo.java
java PasswordHashDemo
```

Stdout chính xác:

```text
format: true
correct: true
wrong: false
```

Chương trình **không in record**, do salt ngẫu nhiên nên chuỗi record khác nhau giữa các lần chạy. Hai lần dẫn xuất trong `verify` dùng lại **đúng salt và cost đã lưu**, vì vậy mật khẩu đúng được chấp nhận. Mật khẩu sai dẫn tới byte khác và trả `false`.

### 6.1 Phân tích bảo vệ và giới hạn của từng dòng

**`RANDOM.nextBytes(salt)`** tạo một salt mới 16 byte; không dùng `Random`, timestamp, user ID hoặc password làm nguồn “ngẫu nhiên”. Nếu cần salt cho người dùng thứ hai, gọi lại `createRecord` để nhận salt mới.

**`ROUNDS = 600_000`** là mức tối thiểu mà hướng dẫn OWASP Password Storage hiện nêu cho **PBKDF2-HMAC-SHA256** (tại thời điểm biên soạn). Đây không phải hằng số “đúng mãi mãi” hoặc bảo đảm an toàn trong mọi hoàn cảnh. Thực tế cần benchmark p95/p99 dưới tải cao, cập nhật theo khuyến nghị mới và giới hạn năng lực máy chủ. `MAX_ROUNDS = 3_000_000` chỉ là trần bảo vệ parsing/CPU **ví dụ**; hệ thống thật định nghĩa chính sách tối thiểu/tối đa có quản trị, cân nhắc migration và chống DoS.

**`PBEKeySpec` và `clearPassword()`:** `PBEKeySpec` giữ bản clone của `char[]` nên gọi `clearPassword()` trong `finally` kể cả khi `generateSecret` lỗi. Bản `char[]` truyền vào từ API vẫn thuộc người gọi và phải được xóa riêng.

**`MessageDigest.isEqual(actual, expected)`** cung cấp cách so sánh hai mảng byte cho kiểm tra tag/digest, hữu ích để tránh tự viết vòng lặp dừng khi gặp byte khác. Ví dụ giới hạn kích thước hash về 32 byte trước khi so. Không suy ra toàn bộ endpoint sẽ có thời gian cố định: thời gian parsing, số vòng lặp, sự tồn tại của tài khoản và error path vẫn khác nhau.

**`Arrays.fill(derived, (byte) 0)`** dọn mảng derived do mã ứng dụng nắm giữ. Java/JVM có thể tạo các bản sao trong provider, garbage collector hoặc log bên ngoài; không thể hứa “đã xóa mọi bản sao của bí mật trong RAM”. Tránh tạo `String` không cần thiết chứa password. Đoạn mã demo vẫn dùng string literal để test dễ đọc, **không áp dụng literal đó cho mật khẩu production**.

**`verify` kiểm tra format và giới hạn iteration/độ dài** trước khi thực hiện PBKDF2 để tránh chấp nhận record sai cấu trúc hoặc cost bất thường. Với record lấy từ database tin cậy, vẫn cần giới hạn kích thước chuỗi trước khi split/decode; khi record có thể bị chỉnh, phải xử lý input không tin cậy và phân biệt lỗi dữ liệu với mật khẩu sai. Những lỗi infrastructure/provider nên được báo qua cơ chế lỗi nội bộ, không trả chi tiết crypto ra client.

**Giới hạn còn lại:** đoạn demo chưa xử lý password policy, rate limiting, MFA, session/token, truy vấn user ID, account lockout, logging an toàn, Unicode policy, pepper/KMS, benchmark chi phí, hay nâng cấp hash cũ. Chỉ dùng làm mô hình API + record format, không coi như hệ thống authentication hoàn chỉnh.

## 7. Lưu gì trong cơ sở dữ liệu?

Một bản ghi verifier nên chứa đủ thông tin để kiểm tra sau này:

| Trường | Ví dụ | Giải thích |
| --- | --- | --- |
| `scheme/version` | `pbkdf2-sha256` | Xác định cách dẫn xuất/encoding và phiên bản format. |
| `iterations` | `600000` | Phải lưu với từng record, vì chính sách work factor sẽ đổi theo thời gian. |
| `salt` | Base64URL 16 byte | Mỗi record một salt ngẫu nhiên; công khai được. |
| `derivedKey` | Base64URL 32 byte | Giá trị dùng để kiểm chứng mật khẩu, cần bảo vệ như password verifier. |
| `createdAt/rehashPolicy` (tùy hệ thống) | Thời điểm tạo, phiên bản cấu hình | Giúp quản trị và nâng cấp, không phải đầu vào bắt buộc của PBKDF2. |

**Không lưu** password bản rõ, một “mật khẩu mã hóa để giải mã”, hay secret pepper trong cùng bảng. Tránh log cả record hash: dù không phải password gốc, record chứa đủ đầu vào công khai để kẻ có bản sao thử đoán ngoại tuyến. Bảo vệ backup, snapshot, quyền truy cập DB, bí mật cấu hình và luồng đăng nhập.

Ở lần đăng nhập:

1. Tra bản ghi theo danh tính/ID đã được quy định.
2. Kiểm tra scheme/format/giới hạn trước khi tính toán.
3. Tạo `PBEKeySpec` từ **password ứng viên** với salt/iterations/keyLength của record.
4. Dẫn xuất lại bằng **đúng PRF**, so sánh bằng byte (không so hash qua String nếu có thể tránh).
5. Nếu hợp lệ và record có work factor thấp hơn chính sách mới, **rehash** bằng salt mới + cost mới, ghi cập nhật an toàn (ví dụ optimistic concurrency trong giao dịch) trước hoặc sau khi tạo session theo thiết kế.
6. Trả lỗi đăng nhập chung cho mật khẩu sai/ID không có, áp dụng rate limit và quan sát sự kiện an ninh; không tiết lộ chi tiết record.

**Migration từ cost thấp:** demo đặt `verify` từ chối count thấp hơn 600.000 vì cố ý áp dụng chính sách chặt cho dữ liệu mới. Nếu hệ thống có bản ghi cũ 100.000 vòng, luồng thực tế cần **bộ xác minh legacy được kiểm soát** rồi nâng lên 600.000 sau đăng nhập thành công; không dùng nguyên hàm `verify` của demo cho migration và vô tình khóa tài khoản cũ. Khi đổi thuật toán, cần dispatcher theo version trong record và kế hoạch reset/migrate nếu thuật toán cũ quá yếu.

## 8. Chọn iteration count và thuật toán

PBKDF2 có thể phù hợp khi hệ thống yêu cầu **implementation có sẵn trong JDK** hoặc một chế độ tuân thủ tiêu chuẩn cụ thể; với hệ thống lưu password mới, [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) ưu tiên **Argon2id** và xem PBKDF2-HMAC-SHA256 là lựa chọn cần cấu hình chi phí đủ cao (đặc biệt trong môi trường có ràng buộc FIPS).

| Thuật toán | Tính chất | Đề xuất sử dụng |
| --- | --- | --- |
| `SHA-256(password)` hoặc `MD5(password)` | Rất nhanh, thiếu work factor/salt hợp lý | **Không** dùng làm password verifier mới. |
| `PBKDF2WithHmacSHA1` | PBKDF2 với HMAC-SHA1; thường để tương thích legacy | Ưu tiên `PBKDF2WithHmacSHA256` cho thiết kế mới nếu cần PBKDF2. |
| `PBKDF2WithHmacSHA256` | Work factor CPU + salt; có qua `SecretKeyFactory` Java 21/SunJCE | Sử dụng khi điều kiện triển khai phù hợp, benchmark và tuân theo chính sách hiện hành. |
| `scrypt` | Hàm dẫn xuất có chi phí bộ nhớ; không phải API JDK `SecretKeyFactory` chuẩn chung | Cân nhắc khi cần memory-hard và thư viện được kiểm thử. |
| `Argon2id` | Memory-hard, có tham số bộ nhớ/time/parallelism | Phương án được OWASP ưu tiên cho hệ thống mật khẩu mới; cần implementation tin cậy bên ngoài JDK chuẩn. |

**Cách benchmark hợp lý:**

- Thực hiện trên **cấu hình production** (CPU, số worker, container quota, virtualized hardware), đo độ trễ PBKDF2 lúc ít tải và lúc có nhiều phiên đăng nhập đồng thời.
- Chọn cost theo hướng dẫn bảo mật mới nhất, ngân sách độ trễ và khả năng chống credential stuffing/DoS. Tăng iterations làm cả **client hợp lệ và attacker** tốn CPU; cần giới hạn tần suất, queue và resource budget.
- Đặt giới hạn **tối đa** cho iteration được đọc từ record/đầu vào ngoài để tránh kẻ tấn công ép một yêu cầu tiêu thụ CPU khổng lồ.
- Lưu iterations với record, tăng cost cho record mới; sau khi người dùng đăng nhập đúng, nâng cấp từng record khi cần.
- Dùng salt mới khi rehash, ghi nhận scheme version; không sửa iterations trên record cũ mà giữ nguyên hash, vì như vậy mật khẩu đúng sẽ không còn khớp.

**Mức cost không phải bảo mật độc lập:** kẻ có GPU/ASIC có thể thực hiện nhiều phép thử; mật khẩu yếu vẫn có rủi ro. PBKDF2 không thay được chính sách mật khẩu mạnh và biện pháp bảo vệ tài khoản.

## 9. PBKDF2 cho password verifier khác PBKDF2 cho khóa AES

`PBKDF2` trả về *key material* và có thể được dùng cho nhiều mục đích; thiết kế phải xác định rõ **mục đích nào** ngay từ đầu.

**A. Xác minh mật khẩu đăng nhập:** lưu `salt + iterations + derivedHash`. Khi đăng nhập, so sánh output; không bao giờ cần giải mã để lấy lại password. Một record verifier không nên được tái sử dụng trực tiếp làm khóa mã hóa dữ liệu người dùng.

**B. Dẫn xuất khóa từ passphrase để mã hóa:** ứng dụng cần salt, cost, độ dài phù hợp thuật toán mã hóa (ví dụ AES-256 dùng 32 byte) rồi tạo khóa từ byte dẫn xuất. Chỉ dẫn xuất khóa **chưa đủ**: phải dùng mã hóa xác thực như AES-GCM với nonce/IV riêng, không lặp IV với cùng khóa, lưu metadata cần thiết và quản lý lỗi xác thực, backup và thay đổi mật khẩu. Nếu dùng chung passphrase cho nhiều mục đích, tách miền khóa bằng cách dùng salt/context độc lập hoặc KDF/key hierarchy được thiết kế rõ; không dùng cùng derived bytes làm password verifier lẫn encryption key.

Ví dụ minh họa **không độc lập** (giả sử `derived` là byte[32] đã dẫn xuất đúng cách):

```java
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

// Chỉ là bước chuyển key material sang key AES; chưa thực hiện mã hóa.
SecretKey aesKey = new SecretKeySpec(derived, "AES");
```

**Đừng nhầm salt PBKDF2 với IV/nonce của AES-GCM.** Salt làm mỗi lần dẫn xuất password độc lập; IV/nonce của cipher đảm bảo các lần mã hóa an toàn với cùng key. Chúng cần quy tắc quản lý riêng. Nếu dữ liệu mã hóa buộc phải giải mã bằng cùng passphrase về sau, phải lưu salt/cost/IV/format đã dùng; đổi salt sẽ dẫn tới khóa khác trừ khi có migration/rewrap.

## 10. Vấn đề thường gặp và cách xử lý

| Triệu chứng hoặc lỗi | Nguyên nhân hay gặp | Cách kiểm tra |
| --- | --- | --- |
| `NoSuchAlgorithmException: PBKDF2WithHmacSHA256` | JRE/provider không cung cấp algorithm, môi trường chạy khác JDK mong đợi | Xem `java -version`, provider, tên thuật toán và JDK Providers Documentation cho bản đang chạy. |
| `InvalidKeySpecException` | Spec không hợp lệ hoặc provider không thể chuyển đổi | Kiểm tra loại `KeySpec`, cost, length và provider. |
| `IllegalArgumentException` ở `PBEKeySpec` | Salt rỗng, iterations không dương hoặc keyLength không dương | Đọc constructor docs, dùng salt không rỗng/cost dương/bit length đúng. |
| `getEncoded() == null` | Key không được provider xuất raw byte | Bắt rõ lỗi, không mặc định mọi `SecretKey` đều extractable. |
| Xác minh mật khẩu luôn sai | Salt/cost/PRF/encoding không đúng giữa tạo và verify; đổi format mà không version | Lưu đủ tham số, kiểm thử vector, kiểm tra normalization/Unicode. |
| Hai account cùng mật khẩu có cùng hash | Dùng chung salt, salt cố định, không sinh lại | Mỗi lần tạo record sinh salt mới bằng `SecureRandom`. |
| Hiệu năng đăng nhập kém khi tải cao | Cost lớn, nhiều request song song, thiếu rate limit/resource budget | Benchmark trên hạ tầng thực, giới hạn đồng thời và tần suất. |
| Bản ghi cũ không đăng nhập được sau khi tăng cost | Chỉ đổi iterations cấu hình mà không rehash; verifier chặn legacy | Dùng record version và migration sau xác minh thành công. |
| Test vector 1–2 vòng chạy đúng nhưng chưa an toàn | Vector chỉ kiểm tra **đúng thuật toán**, không kiểm tra work factor | Dùng policy cost cao, salt riêng và kiểm thử an ninh. |
| Log chứa password/hash/key | Vô tình chuyển char[]/byte[] thành String hoặc in debug | Loại bỏ logging bí mật, giới hạn truy cập debug và xóa mảng nhạy cảm khi dùng xong. |

Lưu ý thêm: `PBEKeySpec` nhận `char[]` nhưng API web thường nhận byte từ UTF-8 rồi framework chuyển thành `String`. Không thể xóa hoàn toàn String bất biến đã đi qua JVM; làm sạch `char[]` vẫn có giá trị nhưng không nên tuyên bố đã xóa hết bí mật. Khi cần kiểm thử đa hệ thống, **xác định chính sách chuẩn hóa trước khi tạo record** và duy trì nó qua mọi lần đăng nhập.

## 11. Bài tập thực hành và checklist

1. **Tham số:** vì sao `new PBEKeySpec(password, salt, 600_000, 32)` không có nghĩa là “hash 32 byte”? Hãy sửa thành key length đúng.
2. **Kiểm thử vector:** chạy `Pbkdf2Vector.java` và so đúng hai giá trị hexadecimal. Thay `iterations=2` thành 3 và giải thích vì sao output đổi dù password/salt không đổi.
3. **Đăng ký và đăng nhập:** chạy `PasswordHashDemo.java`; tự thêm một lần tạo record thứ hai từ cùng password, xác nhận salt của hai record khác nhau. Chỉ hiển thị boolean so sánh, tránh log bản ghi thật.
4. **Mô hình dữ liệu:** thiết kế schema lưu `scheme, cost, salt, hash` và chính sách migration từ 200.000 lên 600.000 iterations mà không vô hiệu hóa tài khoản cũ.
5. **Tính toán:** salt 16 byte có bao nhiêu bit? DK 256 bit có bao nhiêu byte? 32 byte hiển thị dạng hex cần bao nhiêu ký tự? Những kích thước này có ý nghĩa gì đối với bảo mật?
6. **Rà soát:** chỉ ra các lỗi trong mã giả sau: dùng cùng salt cho mọi user, `SHA-256(password)`, so sánh hash bằng `String.equals`, hardcode key, và chỉ thay iterationCount khi nâng cấp record.
7. **Tình huống production:** nếu 600.000 iterations mất nhiều thời gian dưới tải đăng nhập, bạn đo những chỉ số nào và thêm những biện pháp nào trước khi giảm work factor? Nếu chính sách khuyến nghị thay đổi sau một năm, bạn cập nhật record bằng cách nào?
8. **Tính tương thích:** kiểm tra mật khẩu tiếng Việt có dấu và ký tự Unicode kết hợp giữa hai môi trường JDK/provider; mô tả cách đảm bảo cùng byte input, cùng cost, salt, PRF và DK length.

**Tiêu chí hoàn thành:** bạn có thể giải thích rõ tại sao phải dùng `SecretKeyFactory` + `PBEKeySpec` với **salt ngẫu nhiên, work factor đủ lớn, key length tính bằng bit**, lưu các tham số cần thiết, xác minh lại an toàn, và biết khi nào phải thay PBKDF2 bằng cơ chế password hashing memory-hard.

## 12. Tài liệu đáng tin cậy để đối chiếu

- [Java SE 21 – `javax.crypto.SecretKeyFactory`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/SecretKeyFactory.html): cách lấy instance, `generateSecret(KeySpec)`, provider và exception.
- [Java SE 21 – `javax.crypto.spec.PBEKeySpec`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html): constructor, đơn vị `keyLength`, clone password/salt, `clearPassword()` và lưu ý về password characters.
- [Java SE 21 – `java.security.SecureRandom`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html): nguồn ngẫu nhiên bảo mật để sinh salt.
- [Java SE 21 – Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html): tên thuật toán `PBKDF2WithHmacSHA256`.
- [Java SE 21 – JDK Providers Documentation](https://docs.oracle.com/en/java/javase/21/security/oracle-providers.html): khả năng hỗ trợ `SecretKeyFactory` PBKDF2 trong SunJCE và các ghi chú provider.
- [RFC 8018 – PKCS #5: Password-Based Cryptography Specification v2.1](https://www.rfc-editor.org/rfc/rfc8018): công thức PBKDF2, salt và iteration count.
- [OWASP – Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): lựa chọn Argon2id/scrypt/PBKDF2, salt, pepper, chi phí và rehash.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="password-key-derivation-005">7.5 Tạo, lưu và kiểm tra bản ghi mật khẩu với đầy đủ tham số</a>

<details>
<summary>Xem chi tiết</summary>

# 7.5 Tạo, lưu và kiểm tra bản ghi mật khẩu với đầy đủ tham số

Một ứng dụng Java không nên lưu mật khẩu dưới dạng văn bản thuần, mật khẩu được mã hóa có thể giải mã, hoặc kết quả băm nhanh như SHA-256 chạy một lần. Khi cơ sở dữ liệu bị lộ, đối thủ có thể thử rất nhiều mật khẩu ngoại tuyến. Mục tiêu của **password hashing / password-based key derivation** là tạo một **bộ xác minh mật khẩu** khó đoán lại bằng cách sử dụng thuật toán chuyên dụng có chi phí tính toán cao, **salt ngẫu nhiên riêng** và **các tham số được lưu đầy đủ**.

Bài này tập trung vào **vòng đời của một bản ghi mật khẩu**: tạo bản ghi khi đăng ký/đổi mật khẩu, lưu đúng định dạng, đọc và xác minh khi đăng nhập, phát hiện bản ghi cần nâng cấp, và xử lý dữ liệu/thiết lập bất hợp lệ. Ví dụ Java 17+ dùng **PBKDF2WithHmacSHA256** có sẵn trong JDK để biên dịch độc lập. Khi hệ thống có thư viện được duy trì phù hợp, **Argon2id** thường là lựa chọn ưu tiên cho mật khẩu vì có cơ chế chi phí bộ nhớ; phần sau sẽ giải thích đầy đủ tham số tương ứng.

## 1. Bản ghi mật khẩu là gì?

Một bản ghi mật khẩu là **dữ liệu cần thiết để xác minh mật khẩu đã nhập mà không lưu mật khẩu gốc**. Nó không chỉ là một chuỗi hash. Một thiết kế thực tế cần nhận diện chính xác thuật toán, tham số, salt và giá trị dẫn xuất; có thể cần version để đọc các bản ghi cũ sau khi nâng cấp.

~~~text
Khi tạo tài khoản / thay đổi mật khẩu:
    password + salt ngẫu nhiên + thuật toán + tham số chi phí
                            |
                            v
                 KDF / password hasher
                            |
                            v
            derived verifier + metadata
                            |
                            v
       lưu PASSWORD_RECORD vào database

Khi đăng nhập:
    password người dùng nhập + metadata/salt từ PASSWORD_RECORD
                            |
                            v
              chạy cùng KDF và tham số
                            |
                            v
         so sánh bộ xác minh một cách phù hợp
                            |
                 đúng / sai mật khẩu
~~~

Salt **không cần bí mật** và thường lưu cùng bản ghi. Giá trị dẫn xuất cũng không phải mật khẩu gốc, nhưng là dữ liệu bảo mật nhạy cảm: người đọc được database có thể thực hiện đoán mật khẩu ngoại tuyến. Cần kiểm soát quyền truy cập, sao lưu và xuất dữ liệu.

### Password hashing khác các cơ chế khác thế nào?

| Cơ chế | Mục đích | Dùng lưu mật khẩu đăng nhập? |
| --- | --- | --- |
| SHA-256 một lần | Tính dấu vân tay dữ liệu nhanh | **Không.** Tốc độ cao giúp kẻ tấn công thử hàng loạt mật khẩu. |
| HMAC-SHA256 một lần | Xác thực thông điệp bằng khóa bí mật | **Không tự đủ.** Chưa có chi phí chuyên dụng để chống đoán ngoại tuyến. |
| AES-GCM | Mã hóa dữ liệu có thể giải mã bằng khóa | **Không phải cơ chế chính cho password verifier.** Việc lấy lại mật khẩu gốc không cần thiết. |
| PBKDF2-HMAC-SHA256 | Dẫn xuất có nhiều vòng lặp, salt | **Có thể dùng** với cấu hình chi phí được hiệu chỉnh và các giới hạn thích hợp. |
| Argon2id | Password hashing có chi phí CPU và bộ nhớ cấu hình được | **Ưu tiên khi có thư viện phù hợp**, định cấu hình và kiểm thử đúng. |
| bcrypt / scrypt | Thuật toán password hashing chuyên dụng | Có thể dùng khi phù hợp yêu cầu; cần hiểu giới hạn độ dài/encoding và tham số. |

**Không “giải mã” password hash khi đăng nhập.** Server lặp lại phép dẫn xuất với mật khẩu người dùng vừa nhập rồi kiểm tra kết quả.

## 2. Mối đe dọa và giới hạn bảo vệ

Giả sử database gồm tên tài khoản và password record bị sao chép. Đối thủ có thể chọn mật khẩu thử, lấy salt/params từ record và tính hash ngoại tuyến để dò. **Mỗi lần thử càng tốn CPU/bộ nhớ, việc dò càng đắt**, nhưng không có thuật toán nào làm một mật khẩu quá phổ biến trở thành bí mật không thể đoán.

Những biện pháp cần có cùng lúc:

1. Password hasher đủ mạnh, work factor hợp lý, salt ngẫu nhiên riêng cho mỗi bản ghi.
2. Chính sách mật khẩu và quy trình đăng nhập phù hợp; tránh mật khẩu phổ biến/bị lộ và hỗ trợ MFA khi cần.
3. Rate limit, phát hiện credential stuffing và khóa/chờ có kiểm soát **ở bước đăng nhập trực tuyến**.
4. Giới hạn quyền đọc password record, mã hóa bản sao lưu khi cần và giữ bí mật vận hành.
5. Quy trình reset mật khẩu, đổi mật khẩu, nâng cấp hash và quản lý phiên an toàn.

**Rate limit không làm chậm việc tấn công ngoại tuyến trên database đã bị lấy cắp.** Ngược lại, một KDF rất chậm không tự bảo vệ endpoint khỏi request flood; phải hiệu chỉnh công suất đăng nhập và giới hạn concurrency.

## 3. Một bản ghi đầy đủ cần những tham số nào?

Không được phụ thuộc vào giá trị mặc định trong code đang chạy để giải thích một bản ghi tạo từ phiên bản cũ. Các tham số thiết yếu cần đi cùng bản ghi, hoặc được tham chiếu một cách ổn định qua **format version**.

| Thuộc tính | Ý nghĩa | Ví dụ PBKDF2 / Argon2id |
| --- | --- | --- |
| **Format version** | Cách parse bản ghi, chính sách encoding, phép tiền xử lý, sơ đồ thuật toán | p1 hoặc profile Argon2id có version xác định. |
| **Algorithm** | Thuật toán password hashing/KDF chính xác | PBKDF2WithHmacSHA256 / Argon2id. |
| **PRF / hash nội bộ** | Cơ chế bên trong KDF khi thuật toán yêu cầu | HMAC-SHA-256 với PBKDF2; không tự suy từ tên “PBKDF2”. |
| **Salt** | Dãy byte ngẫu nhiên độc lập, thường ≥16 byte cho thiết kế mới | 16 byte sinh bằng SecureRandom, lưu dạng Base64url. |
| **Work factor** | Chi phí theo thuật toán | PBKDF2: iteration count; Argon2id: memory m, passes t, parallelism p. |
| **Derived output length** | Số byte/bit của verifier sau dẫn xuất | 256 bit = 32 byte trong ví dụ PBKDF2. |
| **Hash/verifier** | Giá trị đã dẫn xuất được lưu để so sánh | Binary bytes biểu diễn bằng Base64/PHC format. |
| **Định dạng biểu diễn** | Quy tắc thứ tự field, delimiter, Base64, version và parser | Chuỗi có phân tách hoặc cấu trúc JSON/columns rõ ràng. |
| **Pepper key version** (nếu dùng) | Chỉ định bí mật bổ sung nằm ngoài database, để vận hành rotation | ID tham chiếu khóa, **không lưu chính pepper** trong password record. |

Salt, iteration count, bộ băm và hash length **không phải password** và có thể lưu cùng bản ghi. Không lưu hoặc log mật khẩu plaintext; cũng không gộp pepper secret vào record.

### 3.1. Ví dụ schema PBKDF2 của ứng dụng

Trong bài dùng định dạng do ứng dụng quy định:

~~~text
p1$PBKDF2WithHmacSHA256$600000$256$SALT_BASE64URL$VERIFIER_BASE64URL
 |              |              |    |          |                 |
version      algorithm      rounds bits     salt bytes      derived bytes
~~~

Dòng trên là **sơ đồ các trường**, không phải giá trị record đã được tính hợp lệ. Khi chạy Java demo, SALT_BASE64URL và VERIFIER_BASE64URL được thay bằng giá trị Base64url thực tế, khác nhau mỗi lần tạo record.

Ý nghĩa:

- **p1:** schema phiên bản 1. Parser phải biết p1 dùng thuật toán nào, cách chuyển ký tự mật khẩu thành input của KDF, độ dài salt và output, các dạng encoding được cho phép.
- **PBKDF2WithHmacSHA256:** tên thuật toán chuẩn được JDK sử dụng; HMAC-SHA256 là hàm PRF bên trong PBKDF2.
- **600000:** 600.000 vòng lặp, thuộc record cụ thể. Không lấy 600.000 từ biến “giá trị hiện tại” khi xác minh record cũ có 200.000 vòng.
- **256:** số **bit** đầu ra; 256 bit = 32 byte. Không nhầm số bit này với entropy của mật khẩu.
- **Salt:** 16 byte sinh ngẫu nhiên cho lần tạo record.
- **Verifier:** 32 byte dẫn xuất từ mật khẩu + salt + các tham số, mã hóa Base64url để lưu trong một trường text.

**Không lưu chuỗi String password trong record.** Một bản ghi mới phải có salt mới ngay cả khi người dùng nhập lại đúng cùng mật khẩu cũ.

### 3.2. Các lựa chọn lưu trữ

Có thể lưu toàn bộ record đã version hóa vào một cột VARCHAR/TEXT có giới hạn đủ lớn, hoặc tách các field (algorithm, iteration_count, salt, verifier, version) thành những cột typed trong database. Cả hai cách đều hợp lệ nếu:

- Có **hợp đồng định dạng** ổn định, kiểm tra length/encoding và không cắt mất ký tự Base64.
- Thao tác lưu khi đăng ký/đổi password là **atomic**, tránh trộn salt mới với verifier cũ.
- Khóa duy nhất của tài khoản và quyền truy cập database được kiểm soát.
- Có kế hoạch đọc record cũ khi nâng cấp algorithm, version, work factor.
- Không dùng hash làm token đăng nhập hoặc public identifier của tài khoản.

Độ dài của trường Base64 theo **ký tự**, còn chiều dài của salt/verifier theo **byte**. Ví dụ 16 byte salt mã hóa Base64url không padding thường dài 22 ký tự, 32 byte verifier thường dài 43 ký tự. Parser phải decode rồi kiểm tra chiều dài byte thực tế.

## 4. Chọn thuật toán và tham số: không mặc định cho mọi môi trường

OWASP Password Storage Cheat Sheet khuyến nghị Argon2id khi có hỗ trợ phù hợp và gợi ý cấu hình tối thiểu có ý nghĩa như **m=19456 KiB (19 MiB), t=2, p=1**. Argon2id có các tham số **memory (m)**, **passes (t)** và **parallelism (p)**; chúng cần đo hiệu năng trên môi trường thật. Tăng p không có nghĩa nhân số lần lặp và không tự nhân lượng RAM m.

Với PBKDF2-HMAC-SHA256, OWASP nêu **600.000 iterations** trong hướng dẫn liên quan FIPS; ví dụ Java sử dụng mốc này và vẫn phải benchmark theo CPU, lưu lượng đăng nhập, giới hạn timeout và nhu cầu tuân thủ thực tế. **Tham số bảo mật là quyết định có phiên bản**, không phải một hằng số bất biến mãi mãi.

| Thuật toán | Các tham số phải giữ | Cách đánh giá |
| --- | --- | --- |
| PBKDF2-HMAC-SHA256 | PRF, iterations, salt, output length, encoding | Benchmark thời gian verify trên runtime và phần cứng triển khai; kiểm soát giới hạn iteration trong parser. |
| Argon2id | variant, version (thường v=19), m KiB, t, p, salt, output length | Benchmark RAM và CPU mỗi lần verify, tổng concurrency, memory exhaustion và các limits. |
| scrypt | N, r, p, salt, output length | Đánh giá bộ nhớ, CPU và giới hạn cho mỗi request. |
| bcrypt | variant/prefix, cost, salt và chuỗi hash theo format thư viện | Kiểm tra giới hạn byte của password, cách xử lý encoding và khả năng nâng cấp cost. |

Ưu tiên thư viện password-hashing được duy trì, có format/encoder/verify API hoàn chỉnh. Không tự thiết kế phép băm bằng cách gọi SHA-256 trong vòng lặp thủ công. Thuật toán password hashing cần được triển khai và đánh giá theo chuẩn, không lắp từ các primitive tùy ý.

## 5. Tạo record mới khi đăng ký hoặc đổi mật khẩu

Một luồng tạo record nên làm theo thứ tự sau:

1. Nhận mật khẩu thông qua kênh HTTPS, kiểm tra chính sách đăng ký và giới hạn kích thước hợp lý để tránh tiêu tốn tài nguyên quá mức.
2. Nếu có quy tắc **Unicode normalization**, phải định nghĩa rõ (ví dụ NFC), áp dụng **nhất quán** lúc tạo và lúc xác minh, và gắn với format version. Không tự trim/lowercase mật khẩu nếu không có chính sách rõ.
3. Chọn **thuật toán/profile hiện tại** từ cấu hình server, không để browser chỉ định thuật toán hoặc giảm work factor.
4. Sinh **salt mới bằng SecureRandom**, độc lập với user ID, email, timestamp hoặc password. Salt nên riêng cho mỗi lần đổi password.
5. Thực thi password hasher với salt và **đầy đủ tham số profile**; nhận verifier bytes.
6. Đóng gói version, algorithm, iterations (hoặc m/t/p), salt, output length, verifier và key version nếu dùng pepper.
7. Lưu **atomic** cùng account record theo quyền của ứng dụng; không lưu mật khẩu plaintext hoặc derived key trong log.
8. Giới hạn thời gian tồn tại của password và verifier tạm trong bộ nhớ khi có thể; xóa dữ liệu trong char[]/byte[] do ứng dụng sở hữu khi không cần nữa.

Salt khác nhau khiến hai người có cùng password thường có verifier khác nhau, đồng thời ngăn sử dụng trực tiếp bảng hash tiền tính chung. **Salt không thay thế work factor**, không cần được giấu, và không giống pepper.

## 6. Xác minh record: dùng chính tham số đã lưu

Khi đăng nhập, **không được** chỉ gọi KDF với tham số hiện tại của application rồi so sánh. Cần lấy tham số từ record để xác minh đúng bản cũ.

~~~text
Tài khoản + password ứng viên
             |
             v
Tìm password record theo tài khoản
             |
             v
Parse version + algorithm + params + salt + verifier
             |
      kiểm tra allowlist, bounds, length
             |
        sai -> từ chối
             |
             v
KDF(candidate, storedSalt, storedParams)
             |
             v
So sánh bytes với storedVerifier
        |                |
      khớp            không khớp
        |                |
        v                v
Kiểm tra nâng cấp      từ chối
        |
        v
Xác thực + quản lý phiên theo chính sách
~~~

Các điểm quan trọng:

- **Chọn thuật toán theo allowlist và version đáng tin**, không để một chuỗi lưu trữ hoặc người dùng tùy ý kích hoạt bất kỳ thuật toán/giá trị cost nào.
- **Đọc salt đã lưu**, không sinh salt mới khi xác minh; salt mới sẽ tạo output khác.
- **Dùng iterations/m/t/p của record**, không dùng work factor mới cho record cũ.
- **Kiểm tra độ dài và upper bound** trước khi chạy KDF. Một record bị sửa thành “999.999.999 iterations” có thể khiến endpoint treo hoặc bị từ chối phục vụ.
- **So sánh toàn bộ derived verifier** với phép so sánh phù hợp như MessageDigest.isEqual cho mảng cố định; không dùng prefix hay so sánh chuỗi có thể lộ thông tin thời gian.
- **Xác minh đúng mới được đánh dấu authenticated**; công việc rehash và phát hành session phải đứng sau bước này.
- **Phân biệt lỗi record hỏng và sai mật khẩu trong log nội bộ**, nhưng phản hồi bên ngoài tránh tiết lộ tài khoản có tồn tại hay nguyên nhân mật mã nhạy cảm.

MessageDigest.isEqual chỉ hạn chế sự phụ thuộc timing vào **nội dung bytes** trong điều kiện thích hợp; nó không làm toàn bộ endpoint constant-time vì lookup, decode, KDF, network và xử lý lỗi vẫn có thời gian khác nhau.

## 7. Java 17+: tạo, lưu dạng chuỗi, xác minh và kiểm tra cần rehash

Ví dụ độc lập dưới đây dùng **SecretKeyFactory**, **PBEKeySpec**, **SecureRandom**, Base64url và **MessageDigest.isEqual**. Nó tạo hai record với tham số khác nhau, xác minh password đúng/sai, xác định record cũ cần rehash và kiểm tra giới hạn iterations trước khi thực thi KDF.

Với mục đích minh họa, profile **p1** chỉ hỗ trợ PBKDF2-HMAC-SHA256, salt đúng 16 byte, verifier đúng 32 byte và iterations trong khoảng **100.000–2.000.000**, trong đó **600.000** là cost hiện tại. Khoảng này là **quy tắc parse của bản demo**, không có nghĩa 100.000 là cấu hình nên dùng để tạo record mới; đường tạo record công khai dùng 600.000. Record lịch sử được tạo ở 200.000 chỉ để minh họa migration.

Lưu file **PasswordRecordDemo.java**:

~~~java
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordRecordDemo {
    private static final String VERSION = "p1";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int CURRENT_ITERATIONS = 600_000;
    private static final int MIN_ALLOWED_ITERATIONS = 100_000;
    private static final int MAX_ALLOWED_ITERATIONS = 2_000_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int KEY_BYTES = KEY_BITS / 8;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private record Parsed(int iterations, byte[] salt, byte[] expected) {}

    public static String create(char[] password) throws GeneralSecurityException {
        return createAtCost(password, CURRENT_ITERATIONS);
    }

    private static String createAtCost(char[] password, int iterations)
            throws GeneralSecurityException {
        requirePassword(password);
        checkIterations(iterations);
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, iterations);
        try {
            return String.join("$", VERSION, ALGORITHM,
                    Integer.toString(iterations), Integer.toString(KEY_BITS),
                    ENCODER.encodeToString(salt), ENCODER.encodeToString(derived));
        } finally {
            Arrays.fill(derived, (byte) 0);
        }
    }

    public static boolean verify(char[] candidate, String stored)
            throws GeneralSecurityException {
        requirePassword(candidate);
        Parsed p = parse(stored);
        byte[] actual = derive(candidate, p.salt(), p.iterations());
        try {
            return MessageDigest.isEqual(p.expected(), actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    public static boolean needsRehash(String stored) {
        Parsed p = parse(stored);
        return p.iterations() < CURRENT_ITERATIONS;
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static Parsed parse(String stored) {
        if (stored == null || stored.length() > 256) {
            throw new IllegalArgumentException("Malformed password record");
        }
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 6
                || !VERSION.equals(parts[0])
                || !ALGORITHM.equals(parts[1])
                || !Integer.toString(KEY_BITS).equals(parts[3])) {
            throw new IllegalArgumentException("Unsupported password record");
        }
        final int iterations;
        try {
            iterations = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad iteration count", e);
        }
        checkIterations(iterations);
        final byte[] salt;
        final byte[] expected;
        try {
            salt = DECODER.decode(parts[4]);
            expected = DECODER.decode(parts[5]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Bad Base64url", e);
        }
        if (salt.length != SALT_BYTES || expected.length != KEY_BYTES
                || !ENCODER.encodeToString(salt).equals(parts[4])
                || !ENCODER.encodeToString(expected).equals(parts[5])) {
            throw new IllegalArgumentException("Invalid salt or verifier encoding");
        }
        return new Parsed(iterations, salt, expected);
    }

    private static void checkIterations(int count) {
        if (count < MIN_ALLOWED_ITERATIONS || count > MAX_ALLOWED_ITERATIONS) {
            throw new IllegalArgumentException("Iteration count out of policy");
        }
    }

    private static void requirePassword(char[] chars) {
        if (chars == null || chars.length == 0) {
            throw new IllegalArgumentException("Missing password");
        }
    }

    public static void main(String[] args) throws Exception {
        char[] password = "Một mật khẩu ví dụ dài".toCharArray();
        char[] wrong = "Một mật khẩu khác".toCharArray();
        try {
            String saved = create(password);
            System.out.println("Số trường bản ghi: " + saved.split("\\$", -1).length);
            System.out.println("Mật khẩu đúng: " + verify(password, saved));
            System.out.println("Mật khẩu sai: " + verify(wrong, saved));
            System.out.println("Bản ghi hiện tại cần nâng cấp: " + needsRehash(saved));

            String old = createAtCost(password, 200_000);
            System.out.println("Bản ghi cũ xác minh được: " + verify(password, old));
            System.out.println("Bản ghi cũ cần nâng cấp: " + needsRehash(old));

            String[] fields = saved.split("\\$", -1);
            fields[2] = "999999999";
            boolean rejected = false;
            try {
                verify(password, String.join("$", fields));
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            System.out.println("Tham số vượt giới hạn bị từ chối: " + rejected);
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(wrong, '\0');
        }
    }
}
~~~

Biên dịch với JDK 17+ và chạy:

~~~bash
javac --release 17 -encoding UTF-8 PasswordRecordDemo.java
java PasswordRecordDemo
~~~

Đầu ra đáng tin cậy:

~~~text
Số trường bản ghi: 6
Mật khẩu đúng: true
Mật khẩu sai: false
Bản ghi hiện tại cần nâng cấp: false
Bản ghi cũ xác minh được: true
Bản ghi cũ cần nâng cấp: true
Tham số vượt giới hạn bị từ chối: true
~~~

Không in giá trị salt/hash thực tế nên stdout vẫn giống nhau về mặt kiểm tra dù mỗi lần chạy tạo salt và verifier khác nhau. Bản ghi có **sáu trường**: version, algorithm, iterations, output bits, salt Base64url, derived bytes Base64url. Trong demo, password được đặt bằng string literal để có chương trình độc lập; ứng dụng thật nhận từ request và nên tránh tạo thêm bản sao String không cần thiết.

### Giải thích các thành phần Java

- **SecureRandom** sinh 16 byte salt mới ở createAtCost. Không dùng java.util.Random, currentTimeMillis hoặc email làm salt.
- **PBEKeySpec(password, salt, iterations, KEY_BITS)** đóng gói đúng input của phép PBKDF2. **KEY_BITS là số bit output**, không phải số iteration.
- **SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")** lấy implementation KDF từ provider; **generateSecret(spec).getEncoded()** lấy 32 byte dẫn xuất trong provider phù hợp với ví dụ.
- **PBEKeySpec.clearPassword()** xóa bản sao char[] nằm trong key spec. Caller chịu trách nhiệm xóa char[] input khi dùng xong; đó là lý do main dùng **Arrays.fill**. Việc xóa không bảo đảm mọi bản sao trong JVM/HTTP stack đều biến mất tức thì.
- **Base64url không padding** chỉ là định dạng text để lưu byte; không mã hóa bí mật và không tăng độ mạnh của mật khẩu.
- **parse()** kiểm tra đúng version, tên thuật toán, output bits, số trường, iteration bounds, salt/verifier byte length và Base64url canonical trước khi chạy phép tính tốn kém.
- **MessageDigest.isEqual** so sánh đủ 32 byte verifier. Vì parser cố định output size, không có logic “khớp n byte đầu”.
- **needsRehash()** của demo kiểm tra iteration count cũ so với cấu hình mới. Khi triển khai nhiều thuật toán/phiên bản, cần mở rộng hàm này để xét cả algorithm, params, output length, salt policy và format version.

**Ranh giới ví dụ:** chương trình không triển khai HTTP endpoint, DB transaction, MFA, pepper, account lockout, nhiều scheme lịch sử hoặc unicode normalization tùy chọn. Những phần đó là trách nhiệm của dịch vụ xác thực và được giải thích bên dưới.

### Vì sao parse cần kiểm tra bounds?

Một record có iterations 2.000.000.000, salt decode dài hàng megabyte hoặc verifier không đúng 32 byte có thể làm cạn CPU/RAM nếu được đưa thẳng vào KDF. Parser kiểm tra **độ dài chuỗi**, **algorithm/version allowlist**, **iteration minimum/maximum**, **kích thước bytes đã giải mã** trước khi thực hiện công việc.

Giới hạn MAX_ALLOWED_ITERATIONS trong code là **chính sách triển khai cần cập nhật có kiểm soát**. Không tự thay bằng giá trị từ user request. Nếu hệ thống có record cũ với tham số ngoài policy, cần một đường migration được thiết kế riêng, không bỏ kiểm tra hoặc tự sử dụng bất kỳ giá trị tùy ý nào.

## 8. Kết nối ví dụ với database

Một service có thể lưu record như sau:

~~~text
users
----------------------------------------------
id               BIGINT / UUID
email            VARCHAR UNIQUE
password_record  VARCHAR / TEXT
password_changed_at TIMESTAMP
record_version   INTEGER (nếu cần optimistic locking)
----------------------------------------------
~~~

**password_record** chứa đủ metadata để kiểm tra. Cột **password_changed_at** phục vụ quản lý phiên/lịch sử vận hành theo chính sách, không được dùng làm salt. **record_version** là dữ liệu điều phối cập nhật DB và khác hoàn toàn với format version p1 trong nội dung password record.

### Đăng ký

1. Validate account, chính sách mật khẩu và xác thực email theo ứng dụng.
2. Gọi create(password char[]) để sinh salt và derived verifier.
3. Lưu record mới cùng user trong transaction; ghi audit event không chứa password, verifier hay bí mật.
4. Xóa password buffer do mình sở hữu trong finally; không đưa vào access log/exception.

### Đăng nhập

1. Tìm user theo identity, áp dụng rate limit và cơ chế chống dò mật khẩu.
2. Nếu có record hợp lệ, gọi verify(candidate, storedRecord).
3. Khi verify trả true, tiếp tục các bước kiểm soát trạng thái tài khoản, MFA khi cần, tạo session và cập nhật thông tin đăng nhập phù hợp.
4. Nếu record đã yếu theo policy, tạo record mới **với salt mới và tham số hiện tại** sau khi xác minh thành công; cập nhật database bằng điều kiện version/compare-and-swap.
5. Nếu verify sai hoặc record lỗi, không phát session; phản hồi chung để hạn chế user enumeration.

### Đổi và đặt lại mật khẩu

Không dùng thao tác “decode hash thành password cũ”. Sau khi thực hiện xác thực/ủy quyền cho luồng đổi hoặc reset, tạo **record hoàn toàn mới** với salt, params và verifier mới, cập nhật atomic, và thu hồi session/token theo chính sách. Không tái sử dụng record cũ hay salt cũ chỉ vì người dùng nhập cùng một mật khẩu.

## 9. Từ PBKDF2 sang Argon2id: record có m, t, p

Nếu dùng Argon2id, ngoài algorithm và salt/hash, bản ghi phải chứa **version thuật toán, memory cost, time cost, parallelism và output length**. Một chuẩn biểu diễn thường gặp là **PHC string format**, với cấu trúc khái niệm:

~~~text
$argon2id$v=19$m=19456,t=2,p=1$<salt đã mã hóa>$<hash đã mã hóa>
~~~

Đây là mô tả format để nhận diện trường, **không phải một bản hash hợp lệ để copy dùng xác minh**. Với Argon2id:

- **argon2id** xác định biến thể phù hợp cho password hashing.
- **v=19** thể hiện version thuật toán Argon2 (decimal 19 tương ứng version 0x13).
- **m=19456** là tổng bộ nhớ dùng trong phép tính, **đơn vị KiB**, tương đương 19 MiB.
- **t=2** là số lượt/passes, **không phải 2 giây**.
- **p=1** là số lanes/parallelism; không có nghĩa mỗi lane nhận m KiB riêng, vì m là tổng.
- **salt và hash** phải được lưu đủ bytes; trong PHC format thư viện thường mã hóa bằng Base64 không padding theo quy ước của format.
- **Output length** có thể được xác định từ hash bytes trong format; vẫn phải được profile/library xác định và validate.

Java 17 không có engine SecretKeyFactory Argon2id mặc định được bảo đảm trên mọi JDK. Dùng thư viện password hashing đáng tin, được cập nhật và cung cấp hàm **hash(password, params)** / **verify(encoded, password)** và parse PHC. Đừng tự viết Argon2id từ các phép SHA/bitwise.

**Migration giữa thuật toán:** khi chuyển PBKDF2 sang Argon2id, vẫn giữ khả năng verify các record PBKDF2 hợp lệ được phép; sau login thành công, hash lại bằng Argon2id với salt mới rồi thay record atomically. Không thể chuyển hash PBKDF2 thành hash Argon2id **mà không biết mật khẩu**; bản hash cũ không phải password input thích hợp cho hash mới.

## 10. Nâng cấp tham số và schema mà không mất khả năng đăng nhập

Password hashing cost cần có đường nâng cấp theo thời gian. Khi CPU/GPU và khả năng tấn công tăng, profile hiện tại có thể trở nên yếu. **Record mang đủ tham số** cho phép xác minh bản cũ rồi **rehash on successful login**.

~~~text
Người dùng nhập password
        |
        v
Verify với algorithm + params + salt của record đang lưu
        |
     thành công?
      /       \
    Không      Có
     |         |
    Reject     v
        Record có đạt profile hiện hành?
                /         \
              Có           Không
              |              |
        Auth session    Tạo salt mới + hash theo profile mới
                               |
                               v
                    Cập nhật atomic nếu record
                    cũ vẫn là bản đang lưu
                               |
                               v
                       Auth session
~~~

### Quy tắc migration quan trọng

1. **Chỉ nâng cấp sau khi verify đúng mật khẩu gốc**; đừng biến stored hash thành mật khẩu mới.
2. **Profile mới phải được cấu hình phía server**, không tin tham số do client gửi.
3. **Tạo salt mới**, tính record mới hoàn chỉnh; không sửa riêng field iterations và giữ nguyên verifier.
4. **Tránh race:** hai login song song hoặc login cùng password reset có thể tranh cập nhật. Dùng optimistic lock/compare-and-swap dựa trên record version hoặc hash của giá trị cũ theo chính sách DB, tránh ghi đè record mới hơn.
5. **Không hạ cấp:** nếu record đã có cost/algorithm mạnh hơn profile trên node cũ, không ghi lại theo profile thấp hơn.
6. **Đối với account lâu không đăng nhập**, migration on-login không cập nhật được ngay; có thể cần kế hoạch truyền thông/đặt lại mật khẩu phù hợp.
7. **Giữ bộ verifier cũ có thời hạn** khi đang chuyển đổi nếu policy cho phép, ghi nhận loại algorithm/params hỗ trợ; không giữ legacy format vô hạn mà không có quyết định an toàn.

### Khi cần đổi normalization hoặc encoding

Quy tắc xử lý mật khẩu ký tự Unicode là **một phần của hợp đồng format**. Chuyển từ “không chuẩn hóa” sang “Unicode NFC” có thể làm thay đổi input bytes dẫn xuất. Không đổi cách xử lý trong lúc xác minh record cũ rồi xem tất cả record cũ là sai. Gắn version với normalization policy và migrate khi có password gốc lúc đăng nhập.

Với PBKDF2 qua Java PBEKeySpec, cách provider diễn giải char[] thành byte input cần được kiểm thử trong môi trường triển khai, nhất là password có dấu tiếng Việt, ký tự ngoài ASCII và dấu kết hợp. Cần thống nhất encoding/normalization trong giao thức; không tự chuyển password thành bytes rồi thay thế bằng String không rõ charset hoặc trim.

## 11. Salt, pepper và những bí mật nằm ở đâu?

### 11.1. Salt

Salt là **giá trị ngẫu nhiên không bí mật**, giúp các mật khẩu bằng nhau có hash khác nhau và cản trở bảng tra cứu tính trước. Salt phải:

- Được sinh bằng **SecureRandom** hoặc RNG mật mã tương đương.
- Mới cho **mỗi bản ghi/mỗi lần đổi mật khẩu**, không dùng một salt chung toàn ứng dụng.
- Đủ dài để xác suất trùng rất nhỏ; ví dụ 16 byte (128 bit).
- Lưu kèm record để sử dụng lại khi verify; không thay đổi salt lúc kiểm tra.

Không dùng user ID, email, username hay thời gian như nguồn salt chính; chúng thường dự đoán được, thiếu tính ngẫu nhiên và có thể trùng.

### 11.2. Pepper

Pepper là **bí mật bổ sung** được quản lý **tách khỏi cơ sở dữ liệu password record**, ví dụ trong KMS/HSM hoặc secret manager được phân quyền. Nó giúp tăng defense in depth khi chỉ database bị lộ nhưng hệ thống quản lý pepper còn an toàn.

- **Không lưu pepper trong password_record**, không commit source code hoặc ghi log.
- Nếu dùng pepper, cần thiết kế chính xác vị trí kết hợp với password hash (chẳng hạn post-hash HMAC theo chính sách được đánh giá); đừng tự nối chuỗi một cách mơ hồ rồi gọi đó là peppering an toàn.
- Có thể lưu **pepper key ID** không bí mật để tìm đúng secret khi verify; quản lý rotation khác với salt.
- Mất pepper có thể khiến không thể kiểm tra mật khẩu cũ; lộ pepper cần xử lý sự cố/rotation và có thể yêu cầu reset mật khẩu tùy kiến trúc.
- Pepper không thay thế thuật toán memory-hard/slow, salt riêng hoặc MFA.

Ví dụ Java ở phần 7 **không dùng pepper** để giữ bài toán cơ bản minh bạch và tự chạy được.

## 12. Đầu vào không hợp lệ và xử lý lỗi

Một bản ghi bị hỏng hoặc được nhập từ một hệ thống cũ có thể thiếu fields, salt bị cắt, algorithm lạ hay iteration count không hợp lệ. Cần **từ chối sớm** và không thử sửa bằng heuristic khi đây là dữ liệu xác thực.

| Dạng lỗi | Điều hệ thống nên làm |
| --- | --- |
| Thiếu format version hoặc version không hỗ trợ | Reject với lý do vận hành nội bộ phù hợp; không tự thử mọi parser không giới hạn. |
| Algorithm ngoài allowlist | Reject, không fallback về MD5/SHA-1 hay hash nhanh. |
| Iterations = 0 / âm / vượt max | Reject **trước khi chạy PBKDF2**, tránh DoS. |
| Argon2 m/t/p quá cao hoặc vô lý | Reject theo profile/bounds trước phép tính nặng về RAM/CPU. |
| Salt rỗng, quá ngắn, bị cắt hoặc không đúng encoding | Reject; không phát sinh salt mới để “khôi phục” record cũ. |
| Verifier thiếu bytes / khác output length | Reject; không so sánh prefix hoặc bỏ qua byte bị thiếu. |
| Wrong password | KDF tạo bytes khác và verify false; không cấp session. |
| Không có user hoặc user bị vô hiệu | Ứng xử thống nhất và có rate limit, tránh lộ thông tin tài khoản. |
| Provider không hỗ trợ algorithm hoặc cấu hình | Xử lý lỗi hệ thống có kiểm soát và cảnh báo vận hành; không tự hạ security level. |

**Một record là dữ liệu cần validate, kể cả khi nằm trong database.** Có thể bị hỏng bởi migration, bug, thao tác quản trị hoặc dữ liệu bị sao chép/tráo. Upper bound iteration, output length và kích thước envelope là thiết yếu để tránh lạm dụng tài nguyên.

### Sai password và tính thời gian phản hồi

Một phép so sánh mảng đúng cách **không đảm bảo toàn bộ endpoint phản hồi cố định thời gian**. DB lookup, parse, KDF, MFA và rate limit có thời gian khác nhau. Cần chính sách chống user enumeration, quan sát thực tế, rate limit và xử lý thông điệp lỗi chung; không cố đưa cả hệ thống thành constant-time bằng vài lời gọi sleep tùy tiện.

## 13. Bảo mật bộ nhớ và xử lý dữ liệu nhạy cảm trong Java

**char[]** thường phù hợp hơn String để nhận password ở những API cho phép caller kiểm soát bộ nhớ: có thể dùng **Arrays.fill(password, '\0')** khi đã hoàn tất. **PBEKeySpec.clearPassword()** xóa bản sao bên trong key spec. Tuy nhiên:

- HTTP framework, JSON parser, logging hoặc các thư viện khác có thể đã tạo String/buffer riêng; xóa một char[] **không chứng minh** đã xóa mọi bản sao.
- Không in password, token, pepper hoặc derived verifier ra log, trace, error page, metrics labels hoặc telemetry.
- Không dùng password hoặc salt làm khóa Map/cache lâu dài; hạn chế password tồn tại qua nhiều thread hoặc async task không cần thiết.
- Các **byte[] của derived verifier tạm** nên xóa sau sử dụng khi có thể. Bản hash cần lưu dài hạn trong database nhưng không cần giữ lại nhiều bản sao bộ nhớ.
- Không dùng String.equals cho đối chiếu verifier dạng text; decode ra bytes đúng length rồi dùng cách so sánh phù hợp.
- SecretKeyFactory/PBEKeySpec phải được khởi tạo đúng lần dùng; không chia sẻ mutable password buffer giữa các request song song.

**Lưu ý:** việc in số lượng field và kết quả true/false trong demo là an toàn cho bài học; ở production, không ghi nội dung record vào access log, kể cả khi hash không phải plaintext.

## 14. Kiểm thử đầy đủ cho password record

| Ca kiểm thử | Thiết lập | Kỳ vọng |
| --- | --- | --- |
| Tạo record mới | Password hợp lệ, current profile | Có version/algorithm/params/salt/verifier; salt ngẫu nhiên; record parse được. |
| Đúng password | Record vừa tạo + cùng password | Verify true. |
| Sai password | Cùng record + password khác | Verify false. |
| Tạo lại cùng password | Hai lần create cùng chuỗi | Salt khác nhau với xác suất áp đảo, verifier cũng khác, cả hai record đều verify đúng password. |
| Wrong salt | Đổi một byte salt trong record nhưng giữ verifier | Verify false hoặc reject theo kiểm tra format. |
| Wrong verifier | Đổi một byte derived hash được lưu | Verify false. |
| Mất field | Cắt record hoặc bỏ delimiter | Reject trước KDF. |
| Base64url lỗi | Ký tự hoặc padding không hợp lệ theo profile | Reject; không silently normalize. |
| Iterations bằng 0 | Record PBKDF2 cost bất hợp lệ | Reject trước KDF. |
| Iterations cực lớn | 999.999.999 | Reject trước KDF, tránh CPU exhaustion. |
| Output length khác | Record ghi 256 bit nhưng hash thiếu byte | Reject; không so prefix. |
| Legacy profile | Bản ghi 200.000 iterations thuộc tập được cho phép | Verify vẫn đúng, needsRehash=true. |
| Current profile | Bản ghi 600.000 iterations | Verify đúng, needsRehash=false. |
| Concurrent login | Hai lần đăng nhập/chuyển đổi tham số cùng lúc | Không tạo trạng thái DB không nhất quán hoặc ghi đè reset password. |
| Unicode | Ký tự có dấu, emoji, tổ hợp dấu theo policy | Round-trip tạo/verify nhất quán với cùng encoding và normalization. |
| Rehash + failed password | Wrong password trên record legacy | Không đổi database record. |
| Rehash + successful password | Valid password trên record legacy | Record mới dùng salt và profile mới, cập nhật atomic. |

Không cần chứng minh “salt không bao giờ trùng” bằng một test chạy hai lần; với salt 128 bit, điều bảo đảm thực tế dựa trên CSPRNG và xác suất va chạm thấp, cộng thiết kế vận hành phù hợp.

### Kiểm thử khi chuyển JDK/provider

Một hệ thống có thể thay JDK, security provider, môi trường FIPS hoặc thư viện password hasher. Cần thử xác minh **những record lịch sử đã được tạo trên môi trường cũ**, đặc biệt với password có ký tự Unicode, thay vì chỉ kiểm tra create→verify trong cùng JVM phiên bản mới. Định dạng record đầy đủ là điều kiện cần, nhưng vẫn phải thống nhất cách chuyển password ký tự thành input KDF.

## 15. Những sai lầm điển hình

1. **Chỉ lưu hash mà bỏ salt/iterations/algorithm.** Khi thay config, record cũ không còn được xác minh đáng tin.
2. **SHA-256(password) hoặc SHA-256(password+salt) một lần.** Không đủ chi phí để chống offline guessing.
3. **Dùng cùng salt cho mọi user.** Làm mất lợi ích chính của unique salt.
4. **Dùng timestamp, email hoặc Random để sinh salt.** Thiếu ngẫu nhiên mật mã.
5. **Đổi iteration hiện tại rồi dùng nó verify mọi record cũ.** Kết quả không khớp dù password đúng.
6. **Nâng field iterations trong DB mà không tính lại verifier.** Record trở nên không thể xác minh.
7. **Nhận cost do client gửi.** Cho phép hạ bảo mật hoặc tạo request tiêu tốn CPU/RAM.
8. **Bỏ kiểm tra upper bound của KDF params.** Record lỗi/độc hại có thể gây từ chối dịch vụ.
9. **Lưu pepper ngay cạnh hash và salt.** Không còn lợi ích bảo vệ bổ sung khi chỉ database bị lộ.
10. **Log password, derived verifier hoặc record nhạy cảm.** Làm tăng số nơi chứa dữ liệu có thể dùng để đoán password.
11. **So sánh hash bằng prefix, equals không phù hợp hoặc chỉ kiểm tra vài byte.** Làm yếu tiêu chí xác minh.
12. **Đổi normalization/encoding không version hóa.** Người dùng nhập đúng password nhưng không đăng nhập được.
13. **Tin rằng PBKDF2 256-bit output khiến password 8 ký tự có 256-bit entropy.** Entropy phụ thuộc cách tạo password, không do output length.
14. **Đổi sang Argon2id bằng cách hash lại hash PBKDF2.** Không tạo được verifier đúng theo password gốc, dẫn đến bảo vệ và migration sai.
15. **Rehash trước khi verify thành công.** Có thể thay record của user khi chưa chứng minh người yêu cầu biết password.
16. **Chỉ đo latency từng verify mà không xét tổng tải song song.** KDF quá tốn tài nguyên có thể làm nghẽn dịch vụ.
17. **Dùng password hash như bearer token.** Ai lấy được record có thể giả mạo qua đường API sai kiến trúc; record chỉ phục vụ xác minh server-side.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Hai người dùng có cùng mật khẩu, tại sao password record của họ thường không bằng nhau?  
**Trả lời:** Mỗi record có salt ngẫu nhiên riêng; cùng password nhưng salt khác sẽ sinh verifier khác. Salt phải lưu cùng record để verifier biết dùng salt nào.

**Câu 2.** Sau khi tăng PBKDF2 từ 200.000 lên 600.000 vòng, có thể xác minh tất cả record bằng 600.000 vòng không?  
**Trả lời:** Không. Record cũ phải xác minh bằng 200.000 vòng đã lưu. Chỉ khi password đúng mới có thể tạo record mới bằng 600.000 vòng và salt mới.

**Câu 3.** PBEKeySpec(password, salt, 600000, 256) có nghĩa chương trình đã tạo “mật khẩu 256 bit” không?  
**Trả lời:** Không. 256 là **số bit output của phép dẫn xuất**, không đo entropy của password. Password yếu vẫn có thể bị đoán ngoại tuyến.

**Câu 4.** Bên nhận thấy record ghi 1.000.000.000 iterations. Có nên chạy SecretKeyFactory để “kiểm thử cho chắc” không?  
**Trả lời:** Không. Phải validate upper bound trước khi tốn CPU, từ chối record ngoài chính sách và cảnh báo vận hành phù hợp.

**Câu 5.** Salt có cần giữ bí mật như pepper không?  
**Trả lời:** Không. Salt công khai và lưu kèm record. Pepper là bí mật bổ sung tách khỏi database nếu kiến trúc chọn dùng.

**Câu 6.** Nếu đổi từ PBKDF2 sang Argon2id, có thể chuyển toàn bộ record offline mà không biết password gốc không?  
**Trả lời:** Không thể tạo verifier Argon2id của password từ verifier PBKDF2 một cách tương đương. Cần password gốc khi đăng nhập thành công hoặc quy trình reset.

**Câu 7.** MessageDigest.isEqual có làm endpoint login hoàn toàn constant-time không?  
**Trả lời:** Không. Nó hữu ích khi so sánh bytes verifier; các nhánh lookup, parse, KDF, rate limit và vận hành vẫn có đặc tính timing riêng.

**Câu 8.** Một record được lưu dạng Base64url có được “mã hóa” và không cần phân quyền đọc không?  
**Trả lời:** Không. Base64url là encoding, không phải encryption. Stored verifier vẫn cần bảo vệ quyền truy cập và sao lưu.

**Câu 9.** Trong Argon2id, m=19456,t=2,p=1 có nghĩa gì?  
**Trả lời:** 19 MiB bộ nhớ, 2 passes và 1 lane/degree of parallelism. Đây là những tham số thuật toán cần lưu kèm record, không phải salt/hash bytes.

**Câu 10.** Rehash on login có thể ghi đè password mới nếu người dùng vừa reset trong request khác?  
**Trả lời:** Có nguy cơ nếu update vô điều kiện. Dùng optimistic locking/compare-and-swap để chỉ thay record nếu bản đang lưu vẫn là record đã xác minh.

## 17. Tổng kết và nguồn tham khảo

Một **password record đúng** chứa đủ thông tin để tái thực thi phép kiểm tra: **format version, algorithm, work factor, salt, output length, verifier**, và mọi metadata bổ sung liên quan đến encoding/pepper. Tạo record bằng salt mới và current profile; xác minh bằng params **đã lưu**; chỉ rehash sau khi xác minh đúng và cập nhật DB atomic. Java 17 cung cấp PBKDF2WithHmacSHA256 để minh họa trực tiếp; Argon2id cần thư viện được duy trì phù hợp và phải giữ đầy đủ m/t/p cùng version.

Nguồn kỹ thuật có thể đối chiếu khi thiết kế thực tế:

- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) — thuật toán, salt, work factor, pepper, nâng cấp hash.
- [RFC 9106 — Argon2 Memory-Hard Function](https://www.rfc-editor.org/rfc/rfc9106.html) — Argon2id, m/t/p và cấu trúc tham số.
- [Java SE 21 — PBEKeySpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html) — password char[], salt, iteration, key length, clearPassword.
- [Java SE 21 — SecretKeyFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/SecretKeyFactory.html) — derive/generateSecret qua JCA.
- [Java SE 21 — SecureRandom](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html) — tạo salt ngẫu nhiên mật mã.
- [Java SE 21 — MessageDigest.isEqual](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html) — so sánh verifier bytes.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="password-key-derivation-006">7.6 Hiệu chỉnh chi phí tính toán và nâng cấp tham số khi xác thực lại</a>

<details>
<summary>Xem chi tiết</summary>

# 7.6. Hiệu chỉnh chi phí tính toán và nâng cấp tham số khi xác thực lại

## Mục tiêu và bối cảnh

Trong hệ thống xác thực mật khẩu, `password hashing` hoặc **password-based key derivation function (KDF)** được dùng để biến mật khẩu thành giá trị kiểm tra có salt và chi phí tính toán đáng kể. Kẻ tấn công có bản sao cơ sở dữ liệu có thể thử đoán mật khẩu **ngoại tuyến**; làm mỗi lần đoán tốn CPU, RAM và thời gian sẽ tăng chi phí tấn công. Ngược lại, phía máy chủ phải đủ nhanh để đăng nhập hợp lệ không bị nghẽn và không biến KDF thành điểm dễ bị tấn công từ chối dịch vụ.

Chi phí tính toán cần được **đo, hiệu chỉnh và tăng theo thời gian** cùng sự phát triển phần cứng và bối cảnh đe dọa. NIST SP 800-63B khuyến nghị chọn chi phí cao nhất có thể mà vẫn phù hợp hiệu năng verifier và tăng dần khi năng lực tính toán thay đổi. OWASP khuyến nghị các thuật toán chuyên dụng như Argon2id, scrypt hoặc PBKDF2 tùy điều kiện.

Sau bài này, bạn có thể:

1. Phân biệt tham số chi phí của **Argon2id**, **scrypt**, **bcrypt** và **PBKDF2**, cũng như ảnh hưởng lên CPU/RAM/latency.
2. Thiết kế bài **benchmark đo trên máy và tải thật**, dựa vào độ trễ p50/p95/p99, số xác thực đồng thời, bộ nhớ và giới hạn chống abuse.
3. Lưu **algorithm, version, parameters, salt và hash** kèm từng tài khoản để hỗ trợ kiểm chứng bản ghi cũ.
4. Thực hiện **verify bằng tham số cũ trước; rehash bằng tham số mới sau khi mật khẩu đúng**, với salt mới và cập nhật nguyên tử.
5. Xử lý tài khoản không đăng nhập lâu, thay đổi thuật toán, đồng thời với đổi mật khẩu và sự cố triển khai.

Phần code dùng **Java 17+** với `PBKDF2WithHmacSHA256` thuộc JCA vì chạy trực tiếp trên JDK. Đây là cách học toàn bộ luồng hiệu chỉnh và nâng cấp; nếu chọn Argon2id cho production, dùng thư viện được duy trì và kiểm chứng, không tự cài đặt thuật toán mật mã.

## 1. Vì sao không dùng một work factor cố định mãi mãi?

Mật khẩu do con người chọn thường có entropy thấp hơn khóa ngẫu nhiên. Salt riêng ngăn việc dùng lại bảng hash tính trước cho mọi tài khoản nhưng **không** ngăn thử đoán từng mật khẩu sau khi database bị rò rỉ. KDF tăng chi phí mỗi lần đoán, đặc biệt với thuật toán dùng nhiều bộ nhớ.

Giả sử hôm nay một cấu hình cho thời gian xác thực chấp nhận được. Khi phần cứng tấn công nhanh hơn, cấu hình cũ trở nên rẻ hơn đối với kẻ tấn công; trong khi đó máy chủ có thể đã có thêm năng lực xử lý để tăng chi phí. Vì vậy cần lưu **tham số riêng cho từng hash** và có kế hoạch **nâng cấp khi người dùng đăng nhập thành công**, thay vì giả sử mọi bản ghi luôn dùng chính sách hiện tại.

Ba khái niệm cần phân biệt:

- **Password verification:** từ mật khẩu người dùng nhập, tính KDF theo **tham số và salt của bản ghi đang lưu**, so sánh với hash đã lưu.
- **Cost calibration:** chạy thử các cấu hình trên môi trường mục tiêu để chọn tham số nằm trong ngân sách latency, CPU, memory và mức chống tấn công mong muốn.
- **Rehash-on-login (opportunistic upgrade):** sau một lần **xác thực thành công bằng cấu hình cũ**, dùng chính mật khẩu vừa nhập để tính hash mới với salt mới và chính sách mới; lưu lại nếu bản ghi vẫn là bản ghi cũ.

**Không thể** đổi trực tiếp một hash PBKDF2/Argon2id đã lưu sang tham số mạnh hơn chỉ bằng cách sửa số vòng hoặc “hash lại hash cũ” rồi coi kết quả là hash chuẩn của mật khẩu. Quá trình xác thực cần biết mật khẩu gốc (hoặc một giao thức chuyển đổi đặc biệt có thiết kế riêng); sau một lần login thành công bạn mới có mật khẩu vừa nhập để rehash. Không lưu mật khẩu plaintext chờ migration.

## 2. Hiểu đúng “chi phí” theo từng thuật toán

| Thuật toán | Tham số cốt lõi | Tài nguyên/đặc tính chính | Khi cân nhắc |
| --- | --- | --- | --- |
| **Argon2id** | `m`: bộ nhớ tính bằng KiB; `t`: số pass; `p`: số lane/song song | **Memory-hard**, làm đoán hàng loạt tốn RAM và băng thông bộ nhớ; cân đối thời gian/bộ nhớ | Ưu tiên cho hệ thống mới nếu có thư viện đáng tin và yêu cầu tuân thủ cho phép. |
| **scrypt** | `N`: CPU/memory cost; `r`: block size; `p`: parallelization | Dùng CPU và bộ nhớ; cần tính cả áp lực RAM khi nhiều request đồng thời | Phương án memory-hard khi không có Argon2id thích hợp. |
| **bcrypt** | `cost` (logarithmic work factor) | Chủ yếu chi phí CPU; tăng cost một đơn vị thường tăng khoảng gấp đôi công việc | Hệ thống hiện hữu; chú ý giới hạn input (phổ biến 72 byte), mã hóa và migration. |
| **PBKDF2-HMAC-SHA256** | `iterations`, số bit output, salt | Chủ yếu CPU, **không memory-hard** | Cần khả năng JCA sẵn có hoặc môi trường có yêu cầu FIPS phù hợp. |

**Không so sánh số work factor giữa các thuật toán như cùng một đơn vị.** Ví dụ `600000` vòng PBKDF2 không tương đương `t=600000` của Argon2id. Với Argon2id, `m` là **tổng bộ nhớ cho một phép băm**, không nhân trực tiếp với `p`; `p` là mức song song nội bộ và không được hiểu là “số vòng lặp”. Công thức tài nguyên thực tế phụ thuộc thư viện và máy chạy.

Các mức khởi điểm hiện được OWASP nêu trong *Password Storage Cheat Sheet* (cần kiểm tra lại tài liệu tại thời điểm triển khai):

- **Argon2id:** ví dụ `m=19456 KiB` (19 MiB), `t=2`, `p=1`; OWASP còn đưa các tổ hợp m/t khác có mức phòng thủ tương đương theo đánh giá của họ.
- **PBKDF2-HMAC-SHA256:** khuyến nghị tham chiếu **600.000 iterations**; phù hợp để xem xét khi cần đường triển khai PBKDF2/FIPS.
- **bcrypt:** tối thiểu cost 10 theo hướng dẫn hiện hành, nhưng cần xem đặc thù giới hạn mật khẩu và tình trạng ứng dụng.
- **scrypt:** tham khảo cặp `N=2^17, r=8, p=1` hoặc các lựa chọn tương đương trong hướng dẫn.

**Đây không phải “cấu hình bảo đảm an toàn” chỉ bằng việc copy số.** Hãy kiểm tra phiên bản nguồn khuyến nghị, sức chịu tải thực tế, khả năng nâng cấp, FIPS của *implementation cụ thể*, và dữ liệu rủi ro. Java 17 có `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`, nhưng **không có engine Argon2id tiêu chuẩn trong JCA core**; muốn dùng Argon2id cần thư viện/provider phù hợp.

## 3. Mô hình bản ghi tự mô tả, có version

Một password record cần đủ metadata để xác thực ngay cả khi chính sách mặc định đã thay đổi:

```text
algorithm | formatVersion | parameters | salt | derivedHash | optionalPepperKeyId
```

Ví dụ cấu trúc dạng chuỗi lưu trong database (chỉ là định dạng minh họa):

```text
pbkdf2-sha256$v1$600000$<salt_base64url>$<hash_base64url>
```

Trong code Java cuối bài, dấu `$` ngăn các trường và `v1` đánh dấu **phiên bản định dạng record**; nó không phải phiên bản thuật toán PBKDF2. Có thể dùng schema có cột riêng thay cho chuỗi; với Argon2id, ưu tiên định dạng chuẩn như **PHC string** nếu thư viện hỗ trợ. Không tự thay đổi thứ tự/ý nghĩa các trường ở bản ghi cũ.

Lưu:

- **Algorithm/version:** giúp chọn đúng verifier, duy trì tương thích và chặn định dạng không hỗ trợ.
- **Parameters per record:** đối với PBKDF2 là số `iterations` và độ dài kết quả; đối với Argon2id gồm `m,t,p`, phiên bản thuật toán và output length theo format.
- **Salt ngẫu nhiên riêng cho mỗi lần tạo hash:** ví dụ **16 byte (128 bit)** từ `SecureRandom`, không cần giữ bí mật. Nâng cấp thì tạo **salt mới**; không dùng salt cố định chung các tài khoản.
- **Derived hash:** lưu bytes dưới Base64/hex, **không** lưu mật khẩu, không log mật khẩu hoặc raw password-derived key.
- **Pepper (nếu có):** là secret chung hoặc quản lý tập trung, **không** đặt giá trị secret cạnh database hash; chỉ có thể lưu ID/version của khóa, quản lý thực tế tại KMS/HSM và thiết kế quy trình xoay pepper riêng.

Việc có salt ngẫu nhiên **không** cho phép bỏ rate limiting. Rate limiting bảo vệ xác thực trực tuyến; KDF làm chậm kiểm thử ngoại tuyến sau lộ dữ liệu. Cả hai giải quyết hai bề mặt tấn công khác nhau.

### Phải có giới hạn khi giải mã record

Các tham số trong database có thể bị hỏng hoặc không đáng tin (ví dụ account import lỗi hoặc database compromise). **Không bao giờ** đọc `iterations` hoặc `m` từ chuỗi rồi vô điều kiện cấp hàng GiB RAM/CPU hàng phút.

- Chỉ chấp nhận **danh sách algorithm và version** đã biết.
- Kiểm tra format, độ dài chuỗi, Base64, salt, hash output length và ranh giới giá trị iterations/m/t/p trước khi chạy KDF.
- Giới hạn input password hợp lý để giảm nguy cơ DoS, nhưng không cắt lặng lẽ mật khẩu; có chính sách nhập liệu và Unicode/encoding nhất quán.
- Trong quá trình tăng chính sách, mở rộng **giới hạn xác thực hash hợp lệ** có kế hoạch, tránh làm người dùng hiện hữu không thể đăng nhập.
- Record sai nên **fail closed**, ghi nhận lỗi theo cách không làm lộ thông tin nhạy cảm và đưa vào quy trình khắc phục/reset phù hợp.

## 4. Xây ngân sách hiệu năng trước khi tăng chi phí

Độ trễ một hash trên máy lập trình cá nhân không phản ánh trực tiếp hàng nghìn login đồng thời trên production. Quy trình hiệu chỉnh nên có các bước sau:

1. **Xác định ngân sách của endpoint đăng nhập:** ví dụ mục tiêu p95 tổng latency, tỷ lệ request thành công, SLO riêng cho CPU/RAM và khả năng bảo vệ khi nhận lưu lượng bất thường. Đây là mục tiêu sản phẩm, **không phải** một con số cố định do Java áp đặt.
2. **Đo baseline hiện tại**: tỷ lệ người dùng đang ở thuật toán/parameter version nào; p50/p95/p99 verify; CPU, RSS/memory, GC, queue time, throughput, số lần authentication đồng thời trên từng instance; phân biệt thời gian KDF thuần và toàn request.
3. **Chọn tập ứng viên** dựa trên khuyến nghị bảo mật và giới hạn hạ tầng. Với Argon2id, phải xem **m × số phép KDF đồng thời** cộng overhead memory; với PBKDF2 tập trung CPU time và số worker/luồng.
4. **Warm up trước khi lấy số**: chạy trên JDK/provider/library, container CPU quota, RAM limit, kiến trúc máy và cấu hình GC tương tự production. Đo nhiều lần, bỏ thời gian khởi động/JIT khỏi phép đo steady-state.
5. **Test tải đồng thời** với tỉ lệ login đúng/sai, tenant đông, các đợt đột biến, signup/đổi mật khẩu và những tài khoản đang cần rehash. Kiểm tra khi resource bị giới hạn: có queue/backpressure và rate limit phù hợp, không làm nghẽn hệ thống.
6. **Chọn cấu hình đủ mạnh nhưng còn dư tải** cho thời điểm cao điểm; đo thêm vòng cấp phát memory và chi phí khi thất bại. Đưa tham số/phiên bản chính sách vào config có version, thử nghiệm canary và quan sát trước rollout.
7. **Định kỳ rà soát** khi thay hardware/JDK/provider, xuất hiện yêu cầu tuân thủ mới, throughput tăng hoặc có hướng dẫn bảo mật mới.

Một phép tính kiểm tra áp lực bộ nhớ (minh họa): nếu một lần Argon2id cần khoảng **19 MiB**, hàng chục phép tính đồng thời có thể dùng hàng trăm MiB chỉ riêng vùng làm việc KDF, chưa gồm heap, native overhead và tác vụ khác. Việc tăng `p` không bảo đảm p95 sẽ tốt lên: nó có thể làm nhiều core cạnh tranh tài nguyên hơn. Phải đo trong cùng mô hình concurrency thực tế.

**Không lấy p95 của 11 lần hash một luồng làm p95 của dịch vụ dưới tải.** Microbenchmark là bộ lọc ứng viên; load test mới cho thấy queue, scheduling, contention, memory pressure và tác dụng của rate limiting.

### Bảng ghi nhận kết quả calibration

| Cấu hình ứng viên | p50 KDF | p95 KDF | p95 login end-to-end | CPU/RAM cao điểm | Throughput/queue | Kết luận |
| --- | --- | --- | --- | --- | --- | --- |
| Policy đang chạy | Đo | Đo | Đo | Đo | Đo | Baseline |
| Cấu hình đề xuất A | Đo | Đo | Đo | Đo | Đo | Chọn/từ chối có lý do |
| Cấu hình đề xuất B | Đo | Đo | Đo | Đo | Đo | Chọn/từ chối có lý do |

Bảng chỉ là mẫu ghi kết quả **phải tự đo**, không phải số liệu thực nghiệm giả định.

## 5. Java 17: microbenchmark một ứng viên PBKDF2

Lưu file **`Pbkdf2CostBench.java`**:

```java
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Pbkdf2CostBench {
    private Pbkdf2CostBench() {}

    private static byte[] run(SecretKeyFactory factory, char[] password,
            byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Pbkdf2CostBench <iterations>");
            return;
        }
        int iterations = Integer.parseInt(args[0]);
        if (iterations < 1_000 || iterations > 2_000_000) {
            throw new IllegalArgumentException("Iterations outside benchmark guardrails");
        }

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        char[] password = "benchmark-only-password".toCharArray();
        SecretKeyFactory factory =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        try {
            for (int i = 0; i < 3; i++) {
                byte[] hash = run(factory, password, salt, iterations);
                Arrays.fill(hash, (byte) 0);
            }
            long[] elapsed = new long[11];
            for (int i = 0; i < elapsed.length; i++) {
                long begin = System.nanoTime();
                byte[] hash = run(factory, password, salt, iterations);
                elapsed[i] = System.nanoTime() - begin;
                Arrays.fill(hash, (byte) 0);
            }
            Arrays.sort(elapsed);
            System.out.printf(
                    "iterations=%d samples=%d p50Ms=%.2f p95Ms=%.2f%n",
                    iterations, elapsed.length,
                    elapsed[elapsed.length / 2] / 1_000_000.0,
                    elapsed[(int) Math.ceil(0.95 * elapsed.length) - 1]
                            / 1_000_000.0);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 Pbkdf2CostBench.java
java Pbkdf2CostBench 10000
java Pbkdf2CostBench 600000
```

Định dạng output:

```text
iterations=<so_vong> samples=11 p50Ms=<ms_do_duoc> p95Ms=<ms_do_duoc>
```

Dòng trên là **mẫu định dạng**, không phải stdout số học cố định: thời gian phụ thuộc CPU, JDK, provider, JIT, tải, nhiệt độ và giới hạn container. `samples=11`, `p50Ms` và `p95Ms` lấy từ 11 mẫu đã sort (p95 theo nearest-rank). Chương trình có 3 lượt warmup và 11 phép đo đơn luồng; dùng để kiểm tra nhanh, **chưa đủ** để quyết định chính sách production.

**Giải thích code:** `SecretKeyFactory` cung cấp PBKDF2-HMAC-SHA256. `PBEKeySpec` chứa password, salt, iteration count và độ dài output **256 bit**; `clearPassword()` cố gắng xóa bản sao nội bộ sau khi dùng. Kết quả được xóa khỏi mảng tạm ngay sau phép đo để hạn chế dữ liệu nhạy cảm tồn tại trong heap. Mật khẩu demo là hằng số để tái lập phép thử; không dùng mật khẩu thật khi benchmark. Mức 10.000 vòng chỉ để chạy thử nhanh, **không phải policy lưu mật khẩu mới**.

Khi đánh giá Argon2id/scrypt thực tế, giữ cùng phương pháp warmup và đo nhiều mẫu, nhưng benchmark luôn bằng **thư viện thuật toán được chọn thực sự**, với `m,t,p` và giới hạn native memory trên server. Đừng suy luận thời gian Argon2id từ thời gian PBKDF2.

## 6. Khi nào phải nâng cấp một hash?

Tách chính sách **tạo hash mới** và **xác thực hash cũ**:

- **Policy hiện tại** nêu thuật toán, version, cost tối thiểu, salt length, output length và các ràng buộc của *credential mới*.
- **Verifier cũ** vẫn hiểu các format/thuật toán legacy còn được cho phép, dựa vào metadata **của bản ghi**, không tự ý dùng cost mới để verify hash cũ.
- **needsRehash(record, policy)** trả true khi algorithm/version đã cũ hoặc tham số thấp hơn policy hiện tại theo đánh giá *cụ thể từng thuật toán*. Không chỉ so sánh một số cost tổng quát giữa bcrypt/PBKDF2/Argon2id.
- Không tự động “hạ cấp” record có tham số mạnh hơn hiện tại; cần một chính sách migration cụ thể nếu thay thuật toán.

Ví dụ trạng thái:

| Bản ghi đang lưu | Password được nhập | Kết quả verify | Hành động |
| --- | --- | --- | --- |
| PBKDF2 12.000 vòng | Đúng | PASS bằng 12.000 vòng | Tạo salt mới, hash lại theo policy mới, cập nhật có kiểm soát. |
| PBKDF2 12.000 vòng | Sai | FAIL | Từ chối, **không rehash** hoặc đổi tham số. |
| PBKDF2 600.000 vòng | Đúng | PASS | Đăng nhập; không rehash nếu đạt policy và algorithm không đổi. |
| Record version không hỗ trợ | Bất kỳ | Không có verifier hợp lệ | Fail closed và xử lý khôi phục có xác thực; không đoán tham số. |
| Hai phiên đăng nhập đồng thời | Đều đúng với snapshot ban đầu | PASS bước verify | Chỉ một cập nhật compare-and-set thắng; bên còn lại cần xác minh trạng thái mới. |
| Password reset thắng trước cập nhật | Password cũ đã verify trên snapshot cũ | Có race | CAS thất bại; kiểm tra credential mới, không đè lại password reset. |

Việc **nâng cấp sau khi xác thực lại** thường có thể làm minh bạch với người dùng: mật khẩu vẫn như cũ, salt/hash/cost thay đổi. Tuy nhiên, tính chi phí của **verify cũ + rehash mới** trong lần đăng nhập đó, vì có hai phép KDF. Nếu hệ thống có nhiều hash legacy, cần chuẩn bị tải và có giới hạn concurrency riêng.

### Flow xác thực và nâng cấp

```text
Nhận password qua kênh bảo vệ
          |
          v
Tải credential record + version
          |
          v
Kiểm tra format/algorithm/cost trong ranh giới cho phép
          |
          v
KDF(password, salt cũ, params cũ)
          |
          v
So sánh kết quả không làm rò rỉ timing theo byte
          |
        Có khớp?
       /        \
     Không       Có
      |           |
 Từ chối       needsRehash?
                  /      \
                Không     Có
                 |         |
               Login    Salt mới + KDF policy mới
                           |
                     Atomic CAS/UPDATE
                           |
               Nếu conflict: đọc lại record
               và kiểm tra mật khẩu hiện hành
```

Một lỗi thiết kế thường gặp là **hash mới bằng cách đổi cost mà giữ derived hash cũ**, hoặc **rehash mọi lần login, kể cả đã đạt policy**, gây tốn tài nguyên không cần thiết. Một lỗi khác là **rehash ngay trước khi verify**, đồng nghĩa tự ghi đè credential khi người dùng nhập sai.

## 7. Java 17: ví dụ đầy đủ verify rồi nâng cấp nguyên tử

`PasswordUpgradeDemo` lưu một chuỗi dạng `pbkdf2-sha256$v1$iterations$salt$hash` trong `AtomicReference` để mô phỏng **một hàng database**. Ví dụ có hai policy:

- `OLD_WORK = 12000`: mô phỏng **record legacy yếu**, cố ý chỉ dùng cho bài học, không phải khuyến nghị production.
- `CURRENT_WORK = 600000`: policy mới minh họa theo mức PBKDF2-HMAC-SHA256 đang được OWASP tham khảo. Trong hệ thống thật, cần **benchmark và cấu hình hóa**, không hard-code vô thời hạn.

Lưu file **`PasswordUpgradeDemo.java`**:

```java
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUpgradeDemo {
    private static final String ID = "pbkdf2-sha256";
    private static final String VERSION = "v1";
    private static final int OLD_WORK = 12_000;
    private static final int CURRENT_WORK = 600_000;
    private static final int MAX_ACCEPTED_WORK = 2_000_000;
    private static final int SALT_SIZE = 16;
    private static final int HASH_SIZE = 32;
    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private record Stored(int work, byte[] salt, byte[] hash) {}
    private record LoginResult(boolean authenticated, boolean upgraded) {}

    private static byte[] pbkdf2(char[] password, byte[] salt, int work)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, work, HASH_SIZE * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(char[] password, int work)
            throws GeneralSecurityException {
        byte[] salt = new byte[SALT_SIZE];
        RNG.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, work);
        try {
            return String.join("$", ID, VERSION, Integer.toString(work),
                    ENCODER.encodeToString(salt),
                    ENCODER.encodeToString(hash));
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    private static Stored parse(String encoded) {
        if (encoded == null || encoded.length() > 256) {
            throw new IllegalArgumentException("Invalid credential record");
        }
        String[] f = encoded.split("\\$", -1);
        if (f.length != 5 || !ID.equals(f[0]) || !VERSION.equals(f[1])
                || !f[2].matches("[1-9][0-9]{0,6}")) {
            throw new IllegalArgumentException("Unsupported credential format");
        }
        int work = Integer.parseInt(f[2]);
        if (work > MAX_ACCEPTED_WORK) {
            throw new IllegalArgumentException("Unsupported work factor");
        }
        byte[] salt = DECODER.decode(f[3]);
        byte[] hash = DECODER.decode(f[4]);
        if (salt.length != SALT_SIZE || hash.length != HASH_SIZE) {
            throw new IllegalArgumentException("Invalid salt or hash size");
        }
        return new Stored(work, salt, hash);
    }

    private static boolean matches(char[] password, Stored stored)
            throws GeneralSecurityException {
        byte[] candidate = pbkdf2(password, stored.salt(), stored.work());
        try {
            return MessageDigest.isEqual(stored.hash(), candidate);
        } finally {
            Arrays.fill(candidate, (byte) 0);
        }
    }

    private static LoginResult signIn(AtomicReference<String> account,
            char[] password) throws GeneralSecurityException {
        String observed = account.get();
        Stored stored = parse(observed);
        if (!matches(password, stored)) {
            return new LoginResult(false, false);
        }
        if (stored.work() >= CURRENT_WORK) {
            return new LoginResult(true, false);
        }

        String replacement = encode(password, CURRENT_WORK);
        if (account.compareAndSet(observed, replacement)) {
            return new LoginResult(true, true);
        }

        // A concurrent change (including password reset) may have won.
        // Re-verify current state instead of trusting a stale password.
        return new LoginResult(matches(password, parse(account.get())), false);
    }

    public static void main(String[] args) throws Exception {
        char[] password = "example-only-password".toCharArray();
        char[] wrong = "incorrect".toCharArray();
        try {
            AtomicReference<String> row =
                    new AtomicReference<>(encode(password, OLD_WORK));
            Stored before = parse(row.get());
            System.out.println("oldWork=" + before.work());

            LoginResult first = signIn(row, password);
            Stored after = parse(row.get());
            System.out.println("authenticated=" + first.authenticated());
            System.out.println("upgraded=" + first.upgraded());
            System.out.println("newWork=" + after.work());
            System.out.println("freshSalt=" +
                    !Arrays.equals(before.salt(), after.salt()));
            System.out.println("upgradeAgain=" + signIn(row, password).upgraded());
            System.out.println("wrongPasswordAccepted=" +
                    signIn(row, wrong).authenticated());
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(wrong, '\0');
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 PasswordUpgradeDemo.java
java PasswordUpgradeDemo
```

**Stdout chính xác trong trường hợp chạy thông thường:**

```text
oldWork=12000
authenticated=true
upgraded=true
newWork=600000
freshSalt=true
upgradeAgain=false
wrongPasswordAccepted=false
```

Mặc dù `SecureRandom` tạo salt khác mỗi lần, chương trình chỉ in **kết quả logic** nên stdout không phụ thuộc vào giá trị salt. Xác suất trùng salt ngẫu nhiên 16 byte cực kỳ nhỏ; demo dùng phép so sánh để cho thấy đã tạo salt mới, không coi đây là chứng minh toán học tuyệt đối của tính không trùng.

### Điểm cần đọc kỹ trong code

1. **Tạo hash:** `encode` tạo **salt ngẫu nhiên 16 byte**, dẫn xuất output 256 bit bằng PBKDF2-HMAC-SHA256, rồi lưu algorithm/version/work/salt/hash theo thứ tự đã quy ước. Salt và hash được mã hóa Base64URL. `String.join` chỉ nối trường metadata, không chứa password.
2. **Parse có kiểm tra:** `parse` chỉ nhận format/algorithm/version cho phép, giới hạn iterations, salt 16 byte và hash 32 byte. Giới hạn `MAX_ACCEPTED_WORK` nhằm chặn bản ghi bất thường làm cạn CPU; mọi lần thay policy thật phải cập nhật guardrail có kiểm soát. Trong hệ thống lớn có nhiều algorithm, dùng dispatch rõ ràng theo allowlist và verifier tương ứng.
3. **Verify đúng tham số cũ:** `matches` gọi PBKDF2 bằng `stored.work()` và `stored.salt()`, tức **tham số của record đã lưu**, không dùng `CURRENT_WORK` để xác thực hash cũ. `MessageDigest.isEqual` so sánh byte array; không dùng `==` cho mảng.
4. **Không nâng cấp khi sai:** `signIn` trả `authenticated=false, upgraded=false` ngay khi hash không khớp; không tạo salt/hash mới.
5. **Nâng cấp đúng thời điểm:** chỉ nếu mật khẩu đúng và `work < CURRENT_WORK` thì `encode(password,CURRENT_WORK)` tạo **hash mới + salt mới**. Nếu work đã đạt/nhỉnh hơn policy, tránh hash lại lặp đi lặp lại.
6. **Đảm bảo cập nhật không ghi đè tùy tiện:** `compareAndSet(observed,replacement)` chỉ thành công nếu record vẫn là snapshot vừa xác thực. Nếu có thay đổi đồng thời (gồm password reset), demo **đọc lại và verify mật khẩu hiện hành** trước khi cho phép đăng nhập. Đây là mô phỏng logic CAS, không phải một cơ chế transaction cho database.
7. **Giảm thời gian giữ mật khẩu:** `PBEKeySpec.clearPassword()` xóa bản sao nội bộ; `Arrays.fill(password,'\0')` xóa mảng đầu vào demo sau cùng. Khi ứng dụng nhận password qua String/HTTP body, không thể đảm bảo mọi bản sao immutable bị xóa; hãy giảm log, sao chép và thời gian sống của secret.

**Về benchmark:** demo dùng 600.000 vòng có thể mất thời gian khác nhau trên từng máy. Dùng nó để hiểu luồng migration và kiểm tra API; đo tải thật bằng phương pháp mục 4–5 trước khi đưa production.

## 8. Đưa compare-and-set vào cơ sở dữ liệu và tránh race

`AtomicReference` chỉ mô phỏng dữ liệu một tài khoản trong một tiến trình. Hệ thống thực cần **cập nhật có điều kiện** ở database. Một mẫu SQL minh họa:

```sql
UPDATE accounts
SET password_record = :new_record,
    credential_version = credential_version + 1
WHERE id = :account_id
  AND credential_version = :version_read
  AND password_record = :old_record;
```

Chỉ khi **affected rows = 1** mới đánh dấu nâng cấp thành công. Nếu affected rows = 0, bản ghi đã bị đổi (do rehash từ login khác, đổi mật khẩu hoặc reset) hoặc đã bị xóa. **Không** chạy UPDATE vô điều kiện với password cũ; phải đọc lại và xử lý phù hợp. Một password reset thành công không được bị ghi đè bởi một luồng rehash đã bắt đầu trước đó.

Lưu ý: chỉ so khớp string `password_record` có thể khó tối ưu hoặc không phù hợp một số schema; dùng **credential_version** và optimistic locking, transaction isolation được thiết kế cho hệ thống thật. Nếu một luồng vẫn cấp session dựa trên snapshot cũ đúng lúc password reset, chính sách **session revocation** và điều phối auth/credential write cần được định nghĩa riêng, ngoài ví dụ tối giản này.

Có hai chiến lược thời điểm nâng cấp:

- **Đồng bộ ngay trên login:** dễ hiểu, có kết quả migration chắc chắn trước khi response, nhưng lần login legacy có chi phí **verify cũ + hash mới**. Đặt budget/timeout thích hợp.
- **Chuyển sang job nội bộ sau verify:** chỉ khi password vẫn sẵn trong phạm vi kiểm soát an toàn của operation; tránh lưu mật khẩu plaintext vào queue, log, event hoặc database. Trong nhiều hệ thống, không đưa password sang background queue là lựa chọn đơn giản và an toàn hơn.

Không bao giờ coi một bản ghi legacy là đã nâng cấp chỉ vì xác thực được. Cần **xác nhận UPDATE thành công**, đồng thời tiếp tục hỗ trợ verifier cũ cho bản ghi còn lại trong giai đoạn migration.

## 9. Triển khai nâng cấp toàn hệ thống

### Chính sách theo version và rollout

1. Tạo **bản ghi mới theo policy hiện tại** cho đăng ký và đổi mật khẩu kể từ thời điểm rollout.
2. Tiếp tục **verify hash cũ theo đúng params cũ** cho tài khoản còn được phép đăng nhập.
3. Sau lần verify thành công, nếu policy cho biết cần migration, **rehash với salt mới** và commit CAS.
4. Theo dõi tỷ lệ hash thuộc từng algorithm/params/version và số lần nâng cấp thành công/thất bại **không log password/hash/salt**. Ghi thông số tổng hợp, latency, overload và lỗi parse định dạng.
5. Nếu gặp CPU/memory pressure, giảm lưu lượng KDF bằng rate limit, admission control, queue có giới hạn, autoscaling có kiểm chứng; cân nhắc rollout từng phần hoặc hoãn migration không khẩn cấp theo policy. **Không** âm thầm giảm cost của credential mới để che lỗi tài nguyên.
6. Khi tỷ lệ legacy đã thấp, quyết định mốc ngừng hỗ trợ theo rủi ro và quyền lợi người dùng; tài khoản chưa đăng nhập lâu cần **quy trình reset mật khẩu có xác thực**, không thể tự “tăng iterations” cho hash cũ khi không có plaintext.
7. Đảm bảo rollback phiên bản ứng dụng vẫn **đọc được bản ghi mới**, nếu rollback là khả năng cần có; deploy verifier hỗ trợ version mới **trước** khi bắt đầu ghi hash mới.

### Chuyển thuật toán, không chỉ tăng iterations

Chuyển từ PBKDF2 sang Argon2id là **algorithm migration**: verifier nhận diện record PBKDF2 và xác thực; khi thành công, library Argon2id tạo một record **PHC** mới với m/t/p và salt mới. Không so `iterations` của PBKDF2 với `t` của Argon2id như cùng thước đo. Một hệ thống có thể giữ nhiều verifier cho giai đoạn chuyển đổi, nhưng **chỉ một policy tạo hash mới**.

Với hệ thống bcrypt legacy, chú ý **giới hạn 72 byte** và cách hệ thống cũ xử lý mật khẩu Unicode hoặc dữ liệu dài; thay encoding một cách tùy tiện có thể khiến mật khẩu hợp lệ trước đó bị từ chối. Khi migration thuật toán, phải kiểm thử case non-ASCII và mật khẩu dài theo verifier lịch sử.

### Khi có pepper

Pepper giúp thêm một lớp bảo vệ khi database hash bị lộ nhưng secret bên ngoài vẫn an toàn; thay đổi pepper có thể đòi hỏi **verify bằng pepper key ID cũ rồi tạo record mới dùng pepper mới** hoặc quy trình reset. Đừng lưu plaintext pepper cạnh DB và đừng nghĩ thêm pepper là thay thế cho salt/work factor. Cần thiết kế các failure mode khi HSM/KMS không sẵn sàng, audit truy cập, rotation, key identifier và xử lý tài khoản chưa đăng nhập.

## 10. Rủi ro từ abuse và dữ liệu không đáng tin

Càng tăng cost, một request đăng nhập hợp lệ càng tốn tài nguyên; kẻ tấn công có thể tận dụng endpoint này để gây **CPU/RAM exhaustion**. Cân nhắc các lớp bảo vệ:

- **Rate limit theo account và nguồn**, phát hiện credential stuffing, throttling phù hợp và bảo vệ endpoint khôi phục. Không tạo kênh thông báo cho phép liệt kê user bằng lỗi hoặc thời gian rõ rệt.
- **Bounded concurrency**: chỉ cho số phép KDF đồng thời phù hợp RAM/CPU; hàng chờ phải có giới hạn và timeout. Với Argon2id, đo native memory cùng heap/container memory.
- **Thông số từ record phải qua validate**: không cho account tự yêu cầu iterations cực lớn hoặc m=GiB, không cho attacker đặt algorithm tùy ý trong hệ thống nhận dữ liệu ngoài.
- **Độ dài mật khẩu đầu vào**: cho phép passphrase dài theo chính sách, nhưng đặt trần hợp lý và kiểm thử các thực thi PBKDF2/long password để tránh trường hợp xử lý đầu vào gây chi phí bất thường. Không cắt mật khẩu không thông báo.
- **Khi user không tồn tại**, tránh làm rò rỉ qua response và timing. Một phương án là verifier thực hiện dummy KDF với policy thích hợp trong luồng lỗi; phải tính chi phí vào năng lực chịu tải và kết hợp rate limit. Mục tiêu là hành vi quan sát được nhất quán, không hứa thời gian luôn đồng đều tuyệt đối.
- **Đừng tối ưu bằng fast hash** như SHA-256/MD5 cho password storage. Hash nhanh có ích ở bài toán khác nhưng khiến thử đoán ngoại tuyến quá rẻ.
- **Đầu vào sai, phiên bản không hỗ trợ, hỏng record** phải fail closed và có quy trình hỗ trợ người dùng, không fallback âm thầm sang thuật toán yếu.

Lưu ý về chi phí **người dùng thất bại liên tiếp**: luôn phải kiểm chứng password bằng KDF đối với account có tồn tại và record hợp lệ, nhưng kết hợp rate limit/lockout được thiết kế tránh lợi dụng account DoS; không cho phép bypass KDF qua một đường xác thực “nhanh” riêng.

## 11. Những lỗi hay gặp và cách sửa

| Lỗi | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| Tăng iterations trong config rồi dùng số mới để verify tất cả hash cũ | Hash lưu được tính với iterations cũ; xác thực đúng sẽ thất bại | Verify theo metadata của **từng record**. |
| Rehash hash cũ thay vì password | Kết quả không tương đương KDF(password, salt mới, params mới) | Chỉ rehash sau khi password đúng và vẫn sẵn trong request. |
| Rehash trước kiểm tra mật khẩu | Ghi đè credential cho request sai | Verify thành công trước, rồi mới tạo record mới. |
| Giữ salt cũ hoặc dùng salt chung | Làm suy yếu tính độc lập giữa bản ghi/lần hash | Salt ngẫu nhiên mới cho mỗi lần hash mới/upgrade. |
| Không lưu algorithm/version/params | Không thể đọc hash cũ khi policy đổi | Định dạng tự mô tả, có migration path. |
| Cho phép cost từ DB không giới hạn | CPU/RAM DoS khi record hỏng hoặc bị sửa | Guardrails và allowlist thuật toán, version. |
| Dùng `Arrays.equals` cho hash và nghĩ nó là constant-time | Không có bảo đảm timing phù hợp | Dùng primitive verify của thư viện hoặc `MessageDigest.isEqual` khi chuẩn hóa độ dài. |
| Cấp ủy quyền dựa trên snapshot cũ sau password reset cạnh tranh | Có thể cho người dùng dùng credential đã thay | CAS, re-check khi conflict, định nghĩa session revocation. |
| UPDATE vô điều kiện sau login | Ghi đè password reset/concurrent migration | Optimistic lock theo credential version/record. |
| Rehash mọi login | Gây CPU/RAM không cần thiết | Chỉ khi `needsRehash` thật, tránh downgrade. |
| Thêm Argon2id mới mà tắt ngay verifier PBKDF2 | Tài khoản legacy không thể login | Deploy verifier đọc cả cũ và mới trước write. |
| “Đo” bằng một request local | Bỏ qua p95/p99, queue, CPU quota, native memory | Warmup + nhiều mẫu + test tải production-like. |
| Nâng cost quá cao mà không có rate limiting | Kẻ tấn công tạo hàng đợi KDF quá tải | Giới hạn concurrency, rate limit và capacity plan. |
| Nghĩ PBKDF2 600k đủ vĩnh viễn | Hardware/khuyến nghị thay đổi | Định kỳ benchmark và xem lại tài liệu chuẩn. |
| Hard-code một giá trị cho mọi thuật toán | Các cost parameter khác ngữ nghĩa | Policy phân loại theo algorithm. |
| Log password hoặc hash để debug | Tăng nguy cơ lộ thông tin xác thực | Ghi log dạng metadata tổng hợp, không ghi secret. |

## 12. Bài tập tình huống

1. **Chọn policy:** bạn có hai server class với giới hạn CPU/RAM khác nhau. Lập bảng ứng viên Argon2id m/t/p hoặc PBKDF2 iterations và thiết kế thử nghiệm với tải 1, 10, 50 phép KDF đồng thời. Nêu rõ vì sao p95 đo đơn luồng không đủ.
2. **Bắt buộc kiểm chứng đúng cost:** tạo PBKDF2 record với 12.000 vòng, thử xác thực bằng 600.000 vòng trên cùng salt/hash. Giải thích vì sao không khớp và cách verify đúng trước khi upgrade.
3. **Thực hành Java:** chạy `PasswordUpgradeDemo`, giải thích lần đầu `upgraded=true`, lần sau `upgradeAgain=false` và vì sao salt thay đổi dù password giữ nguyên.
4. **Đọc lỗi:** thay field iterations của record thành `999999999` hoặc format `v9`; vì sao parser phải từ chối **trước khi gọi KDF**?
5. **Race condition:** hai login hợp lệ đọc cùng record legacy; một phiên cập nhật thành công. Phiên còn lại nên làm gì khi compare-and-set thất bại? Nếu đồng thời có password reset, điều gì thay đổi?
6. **Tài khoản ngủ đông:** 5% người dùng không đăng nhập 2 năm. Không có password plaintext, vì sao không thể rehash trực tiếp? Hãy đề xuất reset hoặc chính sách sunset verifier cũ có kiểm soát.
7. **Chi phí kép:** mỗi lần login legacy phải verify 1 lần với cost cũ và tạo hash 1 lần với cost mới. Dự đoán peak CPU/RAM trong giai đoạn rollout, đề xuất metric và rate-limit/bounded-concurrency phù hợp.
8. **Algorithm migration:** thiết kế pseudo-code verify cả PBKDF2 và Argon2id nhưng **chỉ hash mới bằng Argon2id**. Không đồng nhất số iterations của PBKDF2 với `t` của Argon2id.
9. **Bảo mật thực tế:** giải thích tác dụng riêng của salt, work factor, pepper, rate limit và TLS. Tại sao không thành phần nào thay thế hoàn toàn các thành phần còn lại?

**Tiêu chí nắm vững:** Bạn phân biệt được cost của từng KDF; đo trên hệ thống đại diện; lưu record có version/params/salt; xác thực bằng cost **đã lưu** trước khi upgrade; tạo hash mới bằng **password + salt mới + policy mới**, cập nhật nguyên tử và xử lý race; không gây downgrade hoặc làm cạn tài nguyên khi nhận dữ liệu record bất thường.

## Tài liệu tham khảo

- [OWASP Password Storage Cheat Sheet — Work Factors, Argon2id, PBKDF2 và Upgrading the Work Factor](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [NIST SP 800-63B (Revision 4) — Password Verifiers, salted hashes và cost factor](https://pages.nist.gov/800-63-4/sp800-63b.html)
- [Java SE 17: `PBEKeySpec` và `clearPassword`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html)
- [Java SE 17: `SecretKeyFactory`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/SecretKeyFactory.html)
- [Java SE 17: `MessageDigest.isEqual`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html)
- [RFC 9106 — Argon2 Memory-Hard Function for Password Hashing](https://www.rfc-editor.org/rfc/rfc9106)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="password-key-derivation-007">7.7 Phân biệt password-based encryption với khóa sinh ngẫu nhiên</a>

<details>
<summary>Xem chi tiết</summary>

# 7.7. Phân biệt password-based encryption với khóa sinh ngẫu nhiên

## Mục tiêu học tập

Khi hoàn thành bài, bạn có thể:

1. Giải thích vì sao **mật khẩu do con người chọn** và **khóa mật mã sinh ngẫu nhiên** có nguồn độ mạnh hoàn toàn khác nhau, ngay cả khi kết quả cuối đều là khóa AES-256 dài 32 byte.
2. Mô tả chính xác quy trình **password-based encryption (PBE)**: mật khẩu → KDF với salt, work factor → khóa → mã hóa có xác thực; phân biệt **salt**, **nonce**, **khóa**, **authentication tag** và thông tin cấu hình phải lưu.
3. Chọn khi nào nên **dẫn xuất khóa trực tiếp từ password**, khi nào nên **tạo data encryption key (DEK) ngẫu nhiên**, và khi nào cần **envelope encryption** với DEK được bọc bởi key encryption key (KEK) dẫn xuất từ password hoặc được quản lý bởi KMS/HSM.
4. Hiểu chi phí đoán mật khẩu ngoại tuyến, thay đổi mật khẩu, khôi phục dữ liệu, xoay khóa và các rủi ro khi hệ thống mất password hoặc key.
5. Thực hiện ví dụ **Java 17+** dùng `PBKDF2WithHmacSHA256`, `SecureRandom`, `KeyGenerator` và `AES/GCM/NoPadding`; kiểm tra mật khẩu sai và ciphertext bị sửa đều bị từ chối.

**Phạm vi:** Đây là bài trong phần *Mật khẩu và dẫn xuất khóa* của Java Advance security-cryptography. Trọng tâm là **kiến trúc bảo vệ dữ liệu bằng mật khẩu so với khóa sinh ngẫu nhiên**, không phải cách lưu mật khẩu đăng nhập của người dùng hoặc tự thiết kế thuật toán mật mã mới.

---

## 1. Hai nguồn khóa dẫn đến hai mức bảo mật khác nhau

Một hệ thống có dữ liệu nhạy cảm cần mã hóa bằng AES-GCM. Nhà phát triển có hai lựa chọn ban đầu:

- Người dùng nhớ một **mật khẩu/passphrase** để sau này giải mã. Chương trình không thể sử dụng chuỗi ký tự trực tiếp làm khóa AES đúng chuẩn; nó phải **dẫn xuất** khóa từ password bằng thuật toán như PBKDF2, scrypt hoặc Argon2id với các tham số đã thống nhất.
- Hệ thống dùng bộ sinh số ngẫu nhiên an toàn mật mã (**CSPRNG**) để tạo một **khóa AES ngẫu nhiên**. Khóa có thể được giữ trong KMS/HSM, keystore được bảo vệ hoặc được bọc bằng một khóa khác. Người dùng thường không cần nhớ khóa này.

Ví dụ một passphrase dễ đoán được đưa qua PBKDF2 để tạo ra 32 byte. Mặc dù đầu ra dài 256 bit và *trông* ngẫu nhiên, kẻ tấn công có thể thử danh sách mật khẩu, thực hiện lại KDF rồi kiểm chứng với ciphertext. **KDF tăng chi phí cho mỗi lần đoán; nó không biến mật khẩu có entropy thấp thành một bí mật 256 bit mạnh thực sự.**

Ngược lại, 32 byte sinh ngẫu nhiên đúng cách từ `KeyGenerator`/`SecureRandom` có không gian khóa lớn không bị giới hạn bởi khả năng ghi nhớ của người dùng. Điểm khó của cách này là **phân phối và bảo quản khóa**: nếu mất key thì không thể giải mã, nếu key lộ thì ciphertext có thể bị giải mã.

### 1.1. Mô hình luồng dữ liệu

**PBE trực tiếp:**

```text
Password do người dùng nhập
             │
             ├── Salt ngẫu nhiên riêng cho mỗi lần thiết lập mã hóa
             ├── KDF algorithm và work factor
             ▼
       Password KDF (chậm có chủ đích)
             │
             ▼
        Khóa AES được dẫn xuất
             │                 Nonce/IV mới
             └────────────┬─────────┘
                          ▼
                    AES-GCM
                          │
             Ciphertext + authentication tag
```

**Khóa ngẫu nhiên:**

```text
CSPRNG / KeyGenerator
           │
           ▼
   Khóa AES ngẫu nhiên ───────────────┐
           │                         │ lưu hoặc wrap an toàn
           ▼                         ▼
AES-GCM(data, nonce mới)        KMS / HSM / keystore
           │
Ciphertext + authentication tag
```

Có thể chọn cách thứ ba: **mã hóa dữ liệu bằng DEK ngẫu nhiên, rồi dùng KEK dẫn xuất từ password để bọc DEK**. Khi đổi password, trong một số kiến trúc chỉ cần bọc lại DEK; không cần mã hóa lại toàn bộ dữ liệu. Đây là **envelope encryption** sẽ được phân tích ở mục 6.

## 2. Bảng so sánh password-based encryption và khóa sinh ngẫu nhiên

| Khía cạnh | PBE trực tiếp (password → KDF → key) | Khóa sinh ngẫu nhiên (CSPRNG → key) |
| --- | --- | --- |
| Nguồn bí mật | Password/passphrase do con người hoặc cơ chế nhập khác cung cấp | Bộ sinh ngẫu nhiên có chất lượng mật mã |
| Độ mạnh thực tế | Bị giới hạn bởi entropy của password và chi phí tấn công KDF | Có thể đạt độ mạnh của khóa ngẫu nhiên và thuật toán khi generator đúng |
| Muốn lấy khóa | Phải có **password đúng**, **salt**, **KDF/parameters** | Phải truy cập được khóa đã lưu hoặc giải bọc bằng cơ chế được phép |
| Tốc độ thiết lập khóa | **Cố ý chậm** để gây khó cho brute force password | Thường nhanh hơn: tạo khóa bằng `KeyGenerator`, không cần KDF password chậm |
| Salt | **Bắt buộc/thiết yếu** cho dẫn xuất password, nên mới và riêng | Không cần salt để tạo raw AES key từ CSPRNG |
| Nonce AES-GCM | **Vẫn cần**, độc lập với salt | **Vẫn cần**, độc lập với việc khóa được sinh ngẫu nhiên |
| Authentication tag | **Vẫn cần** để phát hiện sửa đổi/sai key | **Vẫn cần** để phát hiện sửa đổi/sai key |
| Rủi ro đoán ngoại tuyến | Có: người giữ ciphertext+salt có thể thử password không cần liên hệ server | Không có “đoán password” nếu dữ liệu chỉ được bảo vệ bởi random key không phụ thuộc password |
| Lưu secret | Không nhất thiết lưu password; lưu metadata PBE để có thể tái tạo key | Phải lưu/bao bọc DEK hoặc không thể giải mã về sau |
| Đổi password | Thường phải giải mã và mã hóa lại nếu key dẫn xuất trực tiếp | Không bị ảnh hưởng nếu key độc lập với password |
| Rotation / thu hồi | Đổi password/cost/salt làm đổi key; cần kế hoạch migration | Rotate/re-encrypt hoặc rewrap key qua KMS/KEK |
| Khôi phục | Phụ thuộc khả năng nhớ/khôi phục password | Phụ thuộc backup/key escrow/KMS recovery controls |
| Phù hợp | File/vault cá nhân, backup mà người dùng mở bằng passphrase, yêu cầu user-held secret | Dịch vụ backend, DB/application encryption, khối lượng dữ liệu lớn, key lifecycle tập trung |
| Rủi ro điển hình | Password yếu, KDF quá nhanh, salt lặp, nhập password qua log, offline guessing | Hard-code key, lộ KMS credential, mất key/backup, dùng lại nonce hoặc chia sẻ key sai |

**Điểm giống nhau:** Cả hai cuối cùng đều có thể cho ra một `SecretKey` AES và dùng **cùng** `Cipher.getInstance("AES/GCM/NoPadding")`. Phân biệt PBE và random-key encryption chủ yếu ở **nguồn khóa, khả năng đoán, cách lấy lại khóa và quản trị vòng đời**, không phải ở việc “AES của PBE” là một thuật toán AES khác.

## 3. PBE là quy trình nhiều bước, không phải “AES(password)”

PBE cần hai thành phần tách biệt:

1. **Password-Based Key Derivation Function (KDF)** biến password thành vật liệu khóa, có salt và work factor.
2. **Encryption scheme** dùng khóa đó để mã hóa dữ liệu, tốt nhất là AEAD có authentication tag.

Ví dụ:

```text
password: char[]
salt: 16 byte ngẫu nhiên
KDF: PBKDF2-HMAC-SHA256, iterations = một tham số được cấu hình
derivedKey: 32 byte
nonce: 12 byte mới cho AES-GCM
ciphertextAndTag: doFinal(plaintext) qua AES/GCM/NoPadding
```

Những thông số như **KDF algorithm, số vòng lặp, kích thước khóa, salt, thuật toán mã hóa, nonce, phiên bản và định dạng tag** phải được lưu cùng bản mã hoặc có một cơ chế truy xuất đáng tin cậy. Người nhận dùng cùng password và chính xác cùng metadata để tạo lại khóa và giải mã.

### 3.1. Tại sao cần KDF chậm?

Password do con người chọn thường có mẫu dễ đoán. Nếu ứng dụng làm:

```java
// Sai cho PBE: SHA-256 quá nhanh, không có work factor hay salt.
byte[] aesBytes = java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(new String(password)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
```

Đây là **đoạn minh họa mẫu sai**, không phải code sản xuất. SHA-256 chạy rất nhanh, giúp kẻ tấn công thử hàng triệu mật khẩu. Việc tạo đối tượng `String` từ password cũng để lại bản sao khó xóa sạch khỏi bộ nhớ.

KDF dành cho password được thiết kế để làm mỗi phép đoán tốn **CPU và/hoặc RAM**:

- **PBKDF2-HMAC-SHA256:** có sẵn qua `SecretKeyFactory` trên các JDK 17 thông dụng; work factor là số lần lặp. Tiện cho ứng dụng thuần JDK và môi trường có yêu cầu chuẩn hóa/FIPS khi provider được chấp thuận.
- **scrypt:** KDF tiêu tốn bộ nhớ và CPU, gây khó cho phần cứng thử password hàng loạt. Không coi nó là API chuẩn sẵn trong mọi Java SE 17; dùng thư viện đã được đánh giá.
- **Argon2id:** KDF memory-hard thường được khuyến nghị cho nhiều ứng dụng hiện đại. Java SE 17 không có class chuẩn `Argon2id`; muốn dùng cần thư viện/provider phù hợp và kiểm soát tham số.
- Các thuật toán **PBE cũ** như `PBEWithMD5AndDES`, DES hay cấu hình PBKDF yếu không nên được chọn cho thiết kế mới chỉ vì tên có chữ `PBE`.

Độ lớn iteration của PBKDF2 phải được cập nhật theo chính sách tổ chức và phần cứng. Ví dụ OWASP Password Storage Cheat Sheet khuyến nghị **PBKDF2-HMAC-SHA256 600.000 vòng hoặc hơn cho bài toán lưu mật khẩu** ở thời điểm tài liệu tham khảo; đó **không phải hằng số áp dụng mọi PBE file**, và không thay thế benchmarking theo latency, threat model, tài nguyên và rủi ro DoS. Mỗi bản mã nên ghi rõ work factor thực sự đã dùng để giải mã và hỗ trợ nâng cấp sau này.

### 3.2. Salt có vai trò gì?

Một salt là byte ngẫu nhiên **không cần bí mật**, nên được tạo riêng cho mỗi lần thiết lập key từ password. Nếu người dùng dùng cùng password cho hai file nhưng mỗi file có salt riêng, KDF tạo ra hai khóa khác nhau với xác suất rất cao. Kẻ tấn công không thể dùng một kết quả hash/key derivation đã tính cho tất cả bản mã có salt khác nhau.

Trong Java:

```java
SecureRandom rng = new SecureRandom();
byte[] salt = new byte[16];
rng.nextBytes(salt);
```

Đây là **đoạn mã dùng bên trong method**, cần `import java.security.SecureRandom;`. Salt 16 byte là một lựa chọn thông dụng. **Salt không thay thế password có entropy tốt và không loại bỏ tấn công đoán ngoại tuyến**.

Tránh: salt hard-code cho mọi dữ liệu, dùng địa chỉ email làm salt duy nhất cho tất cả phiên bản file, dùng timestamp thay random salt hoặc nghĩ salt phải được giấu như secret. Khi dẫn xuất lại khóa để giải mã, phải dùng **salt đã lưu đúng với bản mã**, không tạo salt mới tùy ý.

### 3.3. Salt khác nonce/IV của AES-GCM

| Thuộc tính | Salt KDF | Nonce/IV AES-GCM |
| --- | --- | --- |
| Dùng trong | `PBKDF2WithHmacSHA256` / KDF password | `AES/GCM/NoPadding` |
| Mục đích | Làm mỗi lần dẫn xuất khóa khác nhau, chống tính toán sẵn/rộng trên nhiều bản mã | Bảo đảm các lần mã hóa với **cùng khóa** có đầu vào nonce không trùng |
| Bí mật? | Không | Không |
| Trong demo | 16 byte | 12 byte |
| Có thể lưu kèm ciphertext? | Có, cần để tái tạo khóa | Có, cần để giải mã |
| Có thể dùng cố định? | Không nên: mất tác dụng salt riêng | **Không được** tái sử dụng nonce khi mã hóa với cùng key |
| Thay đổi khi giải mã? | Không, phải dùng lại salt gốc | Không, phải dùng lại nonce gốc |

Salt và nonce dù đều có thể được sinh bằng `SecureRandom` **là hai trường riêng của giao thức**; không tự suy ra có thể thay thế cho nhau. Với AES-GCM, tái sử dụng cặp *key + nonce* là lỗi nghiêm trọng có thể phá confidentiality và authentication. Ngay cả khi mỗi file có salt mới, vẫn cần quản lý nonce đúng với khóa dùng trong mỗi lần mã hóa.

### 3.4. Mật khẩu đúng chưa đủ: phải xác thực bản mã

Mã hóa cần **confidentiality**, nhưng nếu không có integrity/authentication, dữ liệu đã mã hóa có thể bị sửa hoặc đánh tráo mà backend không phát hiện. Hãy ưu tiên **AEAD** như AES-GCM (hoặc cơ chế AEAD chuẩn phù hợp) khi xây PBE mới.

Một record khái niệm:

```text
version | kdf-id | iterations | salt | enc-id | nonce | ciphertext | tag
             \_______________ authenticated metadata ______________/
```

Việc xác thực metadata chỉ có tác dụng khi **quy trình thực sự đưa những bytes đó vào AAD hoặc phần được MAC/tag bảo vệ**. Nếu version/iteration/salt bị thay đổi mà không có validation có kiểm soát, hệ thống có thể bị lỗi, đổi hành vi hoặc bị lợi dụng tiêu tốn tài nguyên. Với GCM, ciphertext kèm tag có thể lưu nối nhau; bên nhận phải kiểm xác thực thành công trước khi dùng plaintext.

**Không dùng CBC/ECB không xác thực** để làm PBE cho dữ liệu mới. Một transformation có tên như `PBEWithHmacSHA256AndAES_256` không tự chứng minh rằng mode bên trong là AEAD có tag. Cần đọc đặc tả/provider và thực hiện riêng kiểm integrity nếu thuật toán PBE không cung cấp xác thực.

## 4. Tấn công đoán mật khẩu ngoại tuyến: giới hạn căn bản của PBE

Giả sử attacker lấy được:

```text
salt + iterations + nonce + ciphertext + authentication tag
```

Đối thủ không cần gửi yêu cầu đến server để đoán password. Họ có thể:

1. Chọn một mật khẩu ứng viên trong dictionary hoặc từ quy tắc biến đổi mật khẩu đã biết.
2. Chạy cùng `KDF(candidate, storedSalt, storedIterations)` để tạo khóa thử.
3. Thử giải mã/xác thực bản mã; nếu authentication tag không hợp lệ thì loại ứng viên, thử ứng viên tiếp theo.
4. Lặp nhiều lần trên GPU/CPU/thiết bị chuyên dụng tùy KDF cho phép.

Đây là **offline password guessing**. Rate limiting, captcha hoặc khóa tài khoản trên API **không ngăn được việc thử mật khẩu ngoại tuyến trên bản sao ciphertext bị đánh cắp**. Mỗi lần đoán có chi phí được làm tăng bởi KDF, song mật khẩu quá yếu vẫn có thể bị tìm ra.

Điều này tạo khác biệt lớn với **khóa ngẫu nhiên thực sự được giữ trong KMS/HSM**: nếu đối thủ chỉ có ciphertext và không có quyền lấy key/unwrap DEK, họ không có một password có thể thử từ từ điển để tái tạo khóa. Họ cần phá cơ chế quản trị key, đánh cắp quyền truy cập, khai thác hệ thống hoặc tấn công primitive, là một threat model khác.

**Kết luận kỹ thuật:** độ dài output của KDF **không đo trực tiếp entropy password**. Một khóa 256-bit dẫn xuất từ `"123456"` vẫn đối mặt với không gian đoán password rất nhỏ. KDF tốt + password đủ mạnh là điều kiện của PBE, không có đường tắt bằng hash hoặc Base64.

### 4.1. Pepper có cứu mật khẩu yếu không?

**Pepper** là một bí mật phía hệ thống (ví dụ được giữ trong KMS/HSM) có thể được kết hợp qua quy trình được thiết kế cẩn thận để tăng lớp phòng vệ. Pepper **khác salt**: salt công khai và riêng từng record; pepper phải bí mật. Nếu cả ciphertext, password yếu và pepper đều lộ, lớp này không còn lợi ích.

Với **PBE offline mà người dùng phải tự giải mã khi không kết nối server**, pepper bắt buộc có từ dịch vụ bên ngoài có thể làm mất khả năng sử dụng offline. Nếu mục tiêu là user-held backup, hãy quyết định trade-off rõ ràng thay vì bổ sung pepper vô điều kiện.

## 5. Khóa ngẫu nhiên: tại sao tránh được giới hạn entropy của password?

Trong Java:

```java
KeyGenerator generator = KeyGenerator.getInstance("AES");
generator.init(256, new SecureRandom());
SecretKey dataKey = generator.generateKey();
```

Đây là **đoạn Java minh họa**, cần `javax.crypto.KeyGenerator`, `javax.crypto.SecretKey`, `java.security.SecureRandom` và xử lý checked exception. Với provider JDK 17 phổ biến, đoạn này tạo AES key 256 bit từ CSPRNG thích hợp, không dẫn xuất từ password có thể đoán.

Nhưng **sinh ngẫu nhiên là một nửa câu chuyện**:

- Khóa phải được lưu/giữ kín trong hệ thống thích hợp; tuyệt đối không hard-code key vào source, JSON config hay lộ qua log.
- Xác định chính sách key ownership: user, tenant, service, object hay phiên bản dữ liệu?
- Có backup/rotation, quản lý quyền đọc/unwrap và quy trình khôi phục hay không?
- Có thể dùng KMS/HSM để giữ **KEK** hoặc tạo/unwrap **DEK**; khi mã hóa cục bộ, phải quản lý plaintext DEK trong bộ nhớ và xóa khi hợp lý.
- Nếu mất key và không có bản backup/escrow phù hợp, dữ liệu **có thể không khôi phục được**, kể cả còn đủ ciphertext.
- Mặc dù key ngẫu nhiên mạnh, mã hóa vẫn phải có IV/nonce không lặp, AEAD tag và schema/authorization đúng.

Trong môi trường backend xử lý hàng triệu record, **khóa ngẫu nhiên và envelope encryption** thường cho phép quản lý rotation, access control, audit và lưu trữ theo dịch vụ tốt hơn việc lấy một password chung cho tất cả dữ liệu.

### 5.1. Có nên dùng `SecureRandom.nextBytes` thay cho `KeyGenerator`?

Có thể sinh byte bằng `SecureRandom` rồi tạo `SecretKeySpec` khi thuật toán cho phép và chiều dài/chất lượng được bảo đảm. Tuy nhiên, `KeyGenerator` thường làm rõ **ý định tạo khóa cho thuật toán cụ thể**, kiểm tra kích thước khóa và dễ tích hợp provider. Khi HSM/KMS tạo key không cho xuất `getEncoded()`, phương thức quản lý key khác với raw byte trong ví dụ.

Không dùng `Random`, `ThreadLocalRandom`, `Math.random()` hoặc `UUID.toString()` một cách tùy tiện để tạo AES key. UUID v4 có cấu trúc/bit cố định, không thể coi bất kỳ chuỗi UUID nào là một khóa AES-256 đủ entropy. Đừng dùng secret lấy từ database ID hoặc timestamp như raw key.

## 6. Envelope encryption: phối hợp password và random key

**Envelope encryption** tách:

- **DEK (Data Encryption Key):** khóa sinh ngẫu nhiên để mã hóa dữ liệu.
- **KEK (Key Encryption Key):** khóa dùng để **wrap/bảo vệ DEK**. KEK có thể dẫn xuất từ password thông qua KDF hoặc được giữ bởi KMS/HSM.

Luồng mẫu cho file người dùng bảo vệ bằng password:

```text
                 User password
                      │ + salt + KDF parameters
                      ▼
                      KEK
                       │
Random DEK ────────────┼──> Wrap DEK bằng KEK ──> wrappedDEK
    │                  │
    │                  └──── Bảo vệ metadata/version/policy tương ứng
    ▼
AES-GCM mã hóa dữ liệu bằng DEK + dataNonce
    │
ciphertext + tag

Lưu: file format/version | KDF params | salt
     KEK wrapping nonce | wrappedDEK + wrap tag
     data nonce | data ciphertext + data tag | AAD/context
```

Ở đây, **wrap key** cần cơ chế có integrity chuẩn (ví dụ AES-KW hoặc AEAD đúng cách), nonce/key discipline và ràng buộc metadata; không chỉ XOR DEK với password hash. Nếu có hai lớp AEAD dùng cùng thuật toán, phải dùng khóa tương ứng độc lập và quản lý **nonce riêng cho từng khóa/lần mã hóa**.

### 6.1. Ưu điểm so với PBE trực tiếp

| Thao tác | PBE trực tiếp | Random DEK + password-derived KEK |
| --- | --- | --- |
| Mã hóa file lớn | Mã hóa bằng khóa dẫn xuất từ password | Mã hóa file bằng random DEK |
| Mở file | KDF password → key → decrypt data | KDF password → KEK → unwrap DEK → decrypt data |
| Thay password | Cần có khả năng decrypt/re-encrypt toàn bộ file (hoặc thiết kế migration khác) | Có thể unwrap DEK bằng KEK cũ và **rewrap DEK** bằng KEK mới, giữ nguyên ciphertext dữ liệu |
| Đổi work factor | Thường thay key dẫn xuất, cần tạo lại ciphertext | Có thể rewrap DEK với cost mới |
| Dữ liệu nhiều GB | Mã hóa lại khi thay password có thể rất tốn I/O | Rewrap DEK thường xử lý vật liệu nhỏ hơn |
| Thêm nhiều người được cấp quyền | Phức tạp nếu mọi người dùng chung password hoặc phải giữ nhiều bản mã | Có thể wrap cùng DEK cho mỗi recipient theo cơ chế/KEK khác nhau |
| Rủi ro đoán password ngoại tuyến | Có | **Vẫn có** nếu wrappedDEK và salt bị lấy; tách DEK không biến password yếu thành mạnh |

**Điểm quan trọng:** random DEK được wrap bằng KEK từ password **không loại bỏ offline dictionary attack trên password**, vì attacker có thể thử giải bọc DEK. Lợi ích chính của envelope ở trường hợp này là **tách khóa dữ liệu khỏi khóa truy cập**, đơn giản hóa đổi password, chia quyền và đổi cách bảo vệ KEK.

Nếu cần chống rủi ro đoán password với ciphertext bị đánh cắp, người dùng phải có password đủ mạnh và KDF đúng; hoặc hệ thống cần thêm bí mật độc lập được quản lý tập trung bằng KMS/HSM và kiểm soát truy cập.

### 6.2. Khi KEK ở KMS/HSM

Một backend có thể dùng random DEK để mã hóa payload; chỉ lưu **wrapped DEK** cùng ciphertext. Khi được phép giải mã, dịch vụ gọi KMS để unwrap DEK trong phạm vi quyền xác định. Đây là **kiến trúc dịch vụ quản lý khóa**, khác với việc người dùng nhớ một password.

Ưu điểm: kiểm soát IAM, policy, audit, khóa master, rotation/backup theo hệ thống. Giới hạn: phụ thuộc dịch vụ, network, availability, chi phí, quyền cấp cho ứng dụng và rủi ro key/credential trong RAM. **KMS không thay thế authorization của ứng dụng**: API vẫn phải kiểm tra tenant/record access trước khi yêu cầu unwrap.

### 6.3. Chú ý khi đổi password và rotate key

Nếu password cũ bị lộ và attacker đã giữ **bản sao wrappedDEK cũ**, việc thay password và ghi đè header **không làm bản sao đã bị đánh cắp trở nên an toàn**. Tương tự, KMS rotation không tự thu hồi toàn bộ ciphertext/DEK cũ đã lộ trước đó. Cần phân tích dữ liệu nào bị ảnh hưởng, hủy quyền, thay DEK hoặc mã hóa lại khi threat model đòi hỏi.

Có thể giữ DEK cũ để đọc dữ liệu cũ trong giai đoạn migration, nhưng phải có key-version và quy trình retire. Đừng chỉ tăng iteration count hoặc thay salt **trong metadata** mà không thực hiện derivation/rewrap/re-encrypt phù hợp: dữ liệu sẽ không còn giải mã được.

## 7. Định dạng dữ liệu phải lưu để giải mã được sau nhiều năm

Ví dụ PBE trực tiếp dùng PBKDF2-HMAC-SHA256 và AES-GCM:

```text
+----------+---------+------------+----------+----------+----------+----------------+
| format v | kdf id  | iterations | salt     | nonce    | enc id   | ciphertext+tag |
+----------+---------+------------+----------+----------+----------+----------------+
    1 byte    enum       int          16 B       12 B      AES-GCM     arbitrary
```

Sơ đồ chỉ để mô tả; **không phải chuẩn byte layout chính thức**, và các định dạng thực tế cần field lengths, endian, AAD, versioning, maximum payload, authenticated parsing và bảo vệ chống downgrades.

Trường metadata quan trọng:

| Trường | Có bí mật không? | Tại sao cần? |
| --- | --- | --- |
| Phiên bản format | Không | Biết cách parse/migrate, từ chối phiên bản không hợp lệ |
| Tên KDF và PRF | Không | Tạo lại đúng khóa từ password |
| Số vòng lặp / memory cost / parallelism | Không | Tái hiện chính xác work factor đã dùng |
| Salt | Không | Dẫn xuất cùng khóa từ password |
| Cipher mode, key size, tag size | Không | Chọn đúng transformation và tham số |
| Nonce/IV | Không | Giải mã đúng; khi mã hóa cần bảo đảm uniqueness dưới cùng key |
| Ciphertext + authentication tag | Không cần giấu tag; **plaintext được bảo vệ** | Lưu dữ liệu và kiểm nguyên vẹn |
| Key ID hoặc wrapped DEK (nếu envelope) | Không giống secret key raw; phải bảo vệ toàn vẹn | Giúp chọn key phù hợp và mở dữ liệu |
| Secret password / raw random key / KEK plaintext | **Có** | **Không** đặt vào bản mã/header/log dưới dạng raw |

Không giữ tất cả thông số PBE chỉ trong config “hiện tại” rồi thay đổi config sau vài tháng. Khi KDF cost nâng lên, file cũ cần **đọc bằng đúng tham số cũ**, sau đó migration/re-encrypt có kiểm soát. Nếu lưu KDF id trong header do attacker kiểm soát, cần **allowlist, giới hạn tài nguyên và authenticated metadata** để ngăn downgrade/DoS hoặc khai thác parser.

### 7.1. AAD bảo vệ ngữ cảnh

AEAD có **AAD (Additional Authenticated Data)**: metadata được **xác thực nhưng không mã hóa**. Ví dụ có thể đưa vào AAD: version, KDF parameters, salt/nonce nếu thiết kế, tenant ID, object ID, content type, quyền/ngữ cảnh ứng dụng. Bên decrypt phải cung cấp chính xác cùng AAD; mismatch sẽ làm tag check thất bại.

Đừng đặt secret vào AAD nếu kỳ vọng nó được mã hóa; AAD thường được lưu/truyền rõ. Không cho ứng dụng nhận plaintext trước khi xác thực GCM tag hoàn thành. Một ciphertext GCM hợp lệ vẫn có thể bị **phát lại** nguyên vẹn; chống replay phải dùng sequence/ID/trạng thái nghiệp vụ.

## 8. Ví dụ Java 17+: PBE với PBKDF2 + AES-GCM so với khóa AES sinh ngẫu nhiên

Tạo file **`PasswordVsRandomKeyDemo.java`**. Chương trình Java 17+ độc lập gồm hai nhánh:

- **PBE trực tiếp:** dẫn xuất AES-256 từ password, salt 16 byte ngẫu nhiên và PBKDF2-HMAC-SHA256; mã hóa AES-GCM với nonce 12 byte. Bản ghi chứa tham số KDF cần cho lần giải mã sau. Header version + iterations + salt + nonce được đưa vào AAD.
- **Random key:** tạo AES-256 trực tiếp bằng `KeyGenerator` và mã hóa cùng plaintext với AES-GCM dùng nonce mới. Khóa chỉ tồn tại trong ví dụ, không có lưu trữ/recovery.
- Thử password sai và sửa tag để chứng minh **không xuất plaintext khi authentication thất bại**. Stdout chỉ in boolean và độ dài, không tiết lộ password hoặc key.

**Lưu ý trước khi chạy:** Password chuỗi cố định dưới đây **chỉ là dữ liệu demo, tuyệt đối không hard-code password trong ứng dụng thực tế**. Số vòng lặp là ví dụ được cố ý đặt rõ ràng và nên benchmark/tune theo chính sách hiện hành. Mã dùng `PBKDF2WithHmacSHA256` có sẵn trên các JDK 17 thông dụng; một provider đặc biệt có thể yêu cầu cấu hình khác. `record` là cú pháp Java 17 hợp lệ.

```java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class PasswordVsRandomKeyDemo {
    private static final SecureRandom RNG = new SecureRandom();

    private static final int ROUNDS = 600_000; // Demo; tune theo hệ thống.
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;
    private static final byte VERSION = 1;

    // In-memory record: production cần serialization chuẩn và defensive copy.
    private record PasswordEnvelope(
            int iterations, byte[] salt, byte[] nonce, byte[] ciphertextAndTag) {}

    private static SecretKey derivePasswordKey(
            char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {

        PBEKeySpec spec =
                new PBEKeySpec(password, salt, iterations, KEY_BITS);
        byte[] material = null;
        try {
            SecretKeyFactory kdf =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            material = kdf.generateSecret(spec).getEncoded();
            // SecretKeySpec tạo bản sao key bytes trước khi xóa buffer tạm.
            return new SecretKeySpec(material, "AES");
        } finally {
            if (material != null) {
                Arrays.fill(material, (byte) 0);
            }
            spec.clearPassword();
        }
    }

    private static byte[] header(
            int iterations, byte[] salt, byte[] nonce) {
        return ByteBuffer.allocate(1 + 4 + salt.length + nonce.length)
                .put(VERSION)
                .putInt(iterations)
                .put(salt)
                .put(nonce)
                .array();
    }

    private static PasswordEnvelope encryptWithPassword(
            char[] password, byte[] plaintext)
            throws GeneralSecurityException {

        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(salt);
        RNG.nextBytes(nonce);

        SecretKey key = derivePasswordKey(password, salt, ROUNDS);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(header(ROUNDS, salt, nonce));
        byte[] ciphertextAndTag = cipher.doFinal(plaintext);
        return new PasswordEnvelope(ROUNDS, salt, nonce, ciphertextAndTag);
    }

    private static byte[] decryptWithPassword(
            char[] password, PasswordEnvelope stored)
            throws GeneralSecurityException {

        // Giới hạn tài nguyên và định dạng trước KDF.
        if (stored.iterations() < 100_000
                || stored.iterations() > 2_000_000
                || stored.salt().length != SALT_BYTES
                || stored.nonce().length != NONCE_BYTES
                || stored.ciphertextAndTag().length < TAG_BITS / 8) {
            throw new GeneralSecurityException("Invalid envelope");
        }

        SecretKey key = derivePasswordKey(
                password, stored.salt(), stored.iterations());
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, stored.nonce()));
        cipher.updateAAD(header(
                stored.iterations(), stored.salt(), stored.nonce()));
        return cipher.doFinal(stored.ciphertextAndTag());
    }

    private static SecretKey generateRandomKey()
            throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(KEY_BITS, RNG);
        return generator.generateKey();
    }

    private static boolean randomKeyRoundTrip(byte[] plaintext)
            throws GeneralSecurityException {
        SecretKey key = generateRandomKey();
        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);

        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
        encrypt.init(Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        byte[] ciphertextAndTag = encrypt.doFinal(plaintext);

        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
        decrypt.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        return Arrays.equals(plaintext, decrypt.doFinal(ciphertextAndTag));
    }

    public static void main(String[] args) throws Exception {
        // DEMO ONLY: production lấy password từ input bảo mật.
        char[] password = "classroom-demo-only".toCharArray();
        char[] wrong = "wrong-password".toCharArray();
        try {
            byte[] plaintext = "PBE vs random data key"
                    .getBytes(StandardCharsets.UTF_8);

            PasswordEnvelope stored =
                    encryptWithPassword(password, plaintext);
            boolean pbeRoundTrip = Arrays.equals(
                    plaintext, decryptWithPassword(password, stored));

            boolean wrongRejected = false;
            try {
                decryptWithPassword(wrong, stored);
            } catch (AEADBadTagException expected) {
                wrongRejected = true;
            }

            byte[] changed = stored.ciphertextAndTag().clone();
            changed[changed.length - 1] ^= 1;
            PasswordEnvelope tampered = new PasswordEnvelope(
                    stored.iterations(),
                    stored.salt(),
                    stored.nonce(),
                    changed);
            boolean tamperRejected = false;
            try {
                decryptWithPassword(password, tampered);
            } catch (AEADBadTagException expected) {
                tamperRejected = true;
            }

            System.out.println("pbeRoundTrip=" + pbeRoundTrip);
            System.out.println("wrongPasswordRejected=" + wrongRejected);
            System.out.println("pbeTamperRejected=" + tamperRejected);
            System.out.println("saltBytes=" + stored.salt().length);
            System.out.println("pbeNonceBytes=" + stored.nonce().length);
            System.out.println("randomKeyRoundTrip=" +
                    randomKeyRoundTrip(plaintext));
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(wrong, '\0');
        }
    }
}
```

Biên dịch và chạy với JDK 17+ hỗ trợ các thuật toán được dùng:

```shell
javac PasswordVsRandomKeyDemo.java
java PasswordVsRandomKeyDemo
```

**Stdout kỳ vọng** (giá trị khóa, salt, nonce đều ngẫu nhiên nên code cố ý không in các giá trị đó):

```text
pbeRoundTrip=true
wrongPasswordRejected=true
pbeTamperRejected=true
saltBytes=16
pbeNonceBytes=12
randomKeyRoundTrip=true
```

### 8.1. Đọc đúng từng kết quả

- `pbeRoundTrip=true`: cùng password + salt + iterations tái tạo cùng AES key; đúng nonce và metadata AAD cho phép GCM xác thực và trả plaintext gốc.
- `wrongPasswordRejected=true`: password khác tạo key khác; GCM authentication tag không hợp lệ, ném `AEADBadTagException`. Đây là cơ chế nhận biết giải mã thất bại, **không phải dịch vụ giới hạn số lượt thử password ngoại tuyến**.
- `pbeTamperRejected=true`: thay đổi byte tag làm xác thực thất bại. Không được bắt `AEADBadTagException` rồi vẫn trả plaintext cũ/chưa kiểm chứng cho nghiệp vụ.
- `saltBytes=16` và `pbeNonceBytes=12`: có **hai trường** khác chức năng, lưu kèm ciphertext khi cần giải mã.
- `randomKeyRoundTrip=true`: cùng AES-GCM nhưng khóa được tạo ngẫu nhiên bởi `KeyGenerator`, không cần PBKDF2 hay password.
- Chương trình **không chứng minh** rằng password demo có đủ entropy cho production; chính vì password hard-code công khai nên nó chỉ minh họa API và định dạng, tuyệt đối không dùng để bảo vệ dữ liệu thật.

### 8.2. Các giới hạn có chủ ý của chương trình

Đây là ví dụ Java **chạy độc lập**, không phải thư viện sản xuất. Không có lưu file, backup/key escrow, key IDs, rotation, tenant isolation, chống replay, identity/authorization, UI nhập password hoặc logging/audit. `PasswordEnvelope` là record chứa các mảng có thể thay đổi, **chưa phải immutable serialized envelope**; production nên có defensive copies, parser/serializer chuẩn, maximum total ciphertext length và phiên bản/algorithm identifiers được kiểm chứng.

Lời gọi `PBEKeySpec.clearPassword()` xóa **bản sao** password bên trong spec. `Arrays.fill(password, '\0')` xóa mảng caller đang giữ; **không thể đảm bảo xóa mọi bản sao password trong JVM, heap dumps hay thư viện**. Trong code demo, literal string của password có thể còn trong memory; đó là lý do phải lấy input qua cơ chế bảo mật của hệ thống trong production.

Việc đưa header vào AAD bảo vệ byte version/iterations/salt/nonce khi xác thực. Tuy nhiên, trước khi chạy KDF tốn CPU, bên nhận vẫn phải parse header và **áp đặt allowlist/giới hạn chi phí**. Nếu nhận từ mạng, cần thêm input size/rate limits để giảm DoS. Không tự thay đổi AAD encoding giữa các phiên bản; ví dụ `ByteBuffer` dùng thứ tự byte mặc định big-endian và chỉ giới thiệu một layout nội bộ.

Chương trình tạo random key nhưng **không lưu key**; khi JVM kết thúc sẽ không có cách giải mã bản mã random-key đã tạo nếu không có quản lý key thật. Với PBE, key có thể tái tạo từ password+tham số; với random key, phải có nơi giữ/bao bọc an toàn.

## 9. Khi nào chọn cách nào?

### Tình huống A – người dùng khóa một file backup để tự mở offline

**PBE với password mạnh** hợp lý nếu người dùng phải tự giữ bí mật và không muốn phụ thuộc máy chủ key. Lưu salt, work factor, nonce, ciphertext/tag, version; có quy trình reset/recovery rõ ràng. Nếu quên password và không có key recovery, hệ thống không thể “tự khôi phục” chỉ từ salt/ciphertext.

Với file lớn hoặc thay đổi password thường xuyên, cân nhắc **random DEK + password-derived KEK** để chỉ rewrap DEK thay vì mã hóa lại gigabyte. Mô hình này vẫn bị tấn công đoán password ngoại tuyến khi attacker lấy được wrapped DEK.

### Tình huống B – backend mã hóa trường dữ liệu khách hàng

Ưu tiên **random DEK**, có thể qua envelope encryption với KMS/HSM. Service cấp quyền truy cập DEK theo tenant/record, ghi audit và hạn chế quyền tài khoản dịch vụ. Một password hard-code chung trong code/config để “dẫn xuất khóa” thường tạo single point of failure; khi leak password, toàn bộ ciphertext liên quan có thể bị giải mã.

### Tình huống C – nhiều người có quyền mở cùng tài liệu

Nếu mỗi người giữ password riêng, random DEK có thể được wrap cho từng recipient với KEK/key riêng. Thu hồi quyền cần quản lý phiên bản và điều kiện phân phối DEK; chỉ xóa một wrapped key **không thu hồi bản DEK đã được người cũ lấy về và lưu lại**. Dữ liệu nhạy cảm cần phân tích quyền truy cập và rotation DEK theo sự kiện thu hồi.

### Tình huống D – khóa mã hóa cho dữ liệu dài hạn

Random key trong KMS với backup/rotation thường dễ tổ chức hơn một password phụ thuộc trí nhớ của cá nhân. Nhưng phụ thuộc availability của KMS, mạng và chính sách lưu bản sao. Quy trình khôi phục/escrow cần được kiểm thử thực tế; mất key có thể đồng nghĩa mất dữ liệu.

### Tình huống E – ứng dụng quản lý mật khẩu đăng nhập

**Không dùng PBE để lưu password đăng nhập rồi giải mã để so sánh.** Với authentication, lưu password verifier bằng thuật toán password hashing/KDF chuyên dụng như Argon2id, scrypt, PBKDF2 theo chính sách, salt riêng, thông số cost và kiểm chứng an toàn. Không cần khôi phục plaintext password để login; chức năng “quên mật khẩu” nên đặt lại bằng quy trình xác thực riêng.

PBE phục vụ tình huống **cần lấy lại plaintext dữ liệu** từ một bí mật người dùng cung cấp; password hashing phục vụ tình huống **cần xác nhận password đúng mà không khôi phục password gốc**.

## 10. Đổi mật khẩu, đổi KDF cost và rotate khóa

### 10.1. Đổi password của PBE trực tiếp

Giả sử file đang dùng `keyOld = KDF(oldPassword, oldSalt, oldCost)`. Sau khi người dùng nhập đúng password cũ:

1. Đọc metadata cũ; validate cấu hình/sizes.
2. Dẫn xuất `keyOld`, xác thực GCM tag và giải mã dữ liệu.
3. Tạo salt mới, cost/KDF version theo policy mới; dẫn xuất `keyNew` từ password mới.
4. Tạo nonce mới và mã hóa lại dữ liệu bằng AEAD, với AAD/version mới.
5. Ghi bản mới **theo cách atomic** và có recovery/backup phù hợp, xóa vật liệu nhạy cảm tạm trong khả năng.
6. Chỉ khi xác minh migration thành công mới cập nhật active version/retire old key.

Không thể chỉ đổi chuỗi password lưu trong UI rồi “giữ nguyên ciphertext PBE” vì key thay đổi.

### 10.2. Rewrap DEK của envelope encryption

Với `dataCiphertext = Encrypt(DEK, plaintext)` và `wrappedDEK = Wrap(KEK, DEK)`:

1. Xác thực/truy xuất DEK bằng password/KEK cũ.
2. Sinh salt mới và KEK mới từ password mới (hoặc chuyển sang KMS key theo chính sách).
3. Wrap cùng DEK bằng KEK mới, nonce/key-wrap format mới và tag hợp lệ.
4. Ghi metadata mới và wrapped DEK an toàn, bảo đảm chính sách rollback/backup.

Dữ liệu chính có thể không cần mã hóa lại khi chỉ thay cơ chế bảo vệ DEK. Nếu **DEK đã lộ**, rewrap không giải quyết được; cần rotate DEK và mã hóa lại dữ liệu được bảo vệ.

### 10.3. Nâng cấp PBKDF2/scrypt/Argon2id

Khi workload/phần cứng thay đổi, work factor cũ có thể không còn phù hợp. Thiết kế version giúp giải mã record cũ đúng tham số rồi re-encrypt hoặc rewrap bằng cấu hình mới. Nếu chọn Argon2id/scrypt ngoài JDK, phải kiểm version, thuật toán và tham số để các hệ thống tương thích. Không xem thay số iterations trong metadata là một upgrade hoàn tất.

## 11. Vận hành, performance và những lỗi thường gặp

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| `new SecretKeySpec(password.getBytes(), "AES")` | Password không đúng kích thước AES, hoặc đủ dài nhưng entropy yếu | Dùng KDF chuyên dụng cho password |
| `SHA-256(password)` làm AES key | KDF quá nhanh, cho phép offline guessing quy mô lớn | Dùng PBKDF2/scrypt/Argon2id theo policy |
| Salt cố định cho mọi user/file | Trùng key nếu password giống nhau, thuận lợi tính toán sẵn | Salt riêng và sinh bằng CSPRNG |
| Giấu salt như secret chính | Phân tích sai threat model, quên giữ true secret | Salt công khai; bảo vệ password/key |
| Không lưu KDF params | Sau khi đổi config, file cũ không giải mã được | Versioned, self-describing envelope |
| Dùng `Random` cho salt/key/nonce | Nguồn có thể dự đoán, nguy cơ lặp/weak key | `SecureRandom` hoặc engine mật mã chuẩn |
| Tái sử dụng AES-GCM nonce cùng key | Có thể phá confidentiality và authentication | Nonce unique dưới từng key, giới hạn số lần dùng khóa |
| Dùng AES-CBC không MAC | Không bảo vệ integrity, padding oracle | AEAD, hoặc Encrypt-then-MAC đúng chuẩn trong legacy |
| Chỉ kiểm “decrypt không exception” | Có thể chấp nhận dữ liệu bị thay đổi trong mode không xác thực | Dùng AEAD tag/MAC, không xử lý dữ liệu chưa auth |
| Đặt cost quá nhỏ | Attacker thử rất nhiều candidate/giây | Benchmark và cập nhật policy KDF |
| Tin rằng cost cực lớn luôn tốt | Tự gây latency/CPU DoS, nhất là service mở nhiều file | Giới hạn iterations/memory, kiểm resource policy trước KDF |
| Hard-code password hoặc key trong JAR | Ai có artifact/source có thể đọc bí mật | Secure input, secret manager/KMS/keystore |
| Log password, raw key, decrypted data | Mất bí mật dù thuật toán đúng | Redact log, hạn chế debug dumps và access |
| Dùng password mạnh rồi lưu plaintext cạnh ciphertext | Password bị lộ từ nơi lưu, PBE mất bảo vệ | Bảo vệ đường nhập và vòng đời secret |
| Dùng random DEK rồi quên lưu/backup | Mất khả năng giải mã mãi mãi | KMS/wrapped DEK, key escrow/recovery |
| Rewrap DEK nhưng không xét bản sao cũ đã lộ | Đối thủ giữ wrapped key hoặc DEK cũ vẫn truy cập | Threat-model rotation/re-encryption và backup |
| Dùng key truy cập để thay authorization | Người giữ ciphertext hoặc ID không đương nhiên có quyền nghiệp vụ | IAM, tenant policy, quyền đọc/unwrap độc lập |

### 11.1. Cẩn thận với thời gian và lỗi

KDF chậm có chủ đích; trong API có nhiều người dùng, không cho phép một client đẩy iterations lên hàng tỷ hoặc cung cấp ciphertext không giới hạn rồi yêu cầu backend tự xử lý. Áp đặt size limits, allowlist KDF, dải cost có thể chấp nhận, rate limiting, timeout/resource budget. Với GCM, lỗi key/nonce/AAD/tag cần được xử lý bằng **từ chối bản mã**, không trả plaintext hoặc tiết lộ nội dung. Đừng biến khác biệt `wrong password` và `bad ciphertext` thành thông báo phân biệt dựa trên các chi tiết mật mã ngoài luồng phù hợp.

KDF có thể tiêu tốn CPU đáng kể; nếu hệ thống cần mã hóa rất nhiều record, **không dẫn xuất khóa bằng password mới cho từng row/request** nếu kiến trúc này không cần thiết. Có thể dẫn xuất KEK khi mở vault rồi dùng random DEK cho các record, tùy threat model/session lifecycle. Nhưng caching plaintext key kéo dài cũng làm tăng rủi ro khi memory bị truy cập; phải cân nhắc cả hiệu năng và an ninh.

### 11.2. Java API cần nhớ

| API | Mục đích | Ghi chú |
| --- | --- | --- |
| `PBEKeySpec` | Giữ password dạng `char[]` + salt + iterations + key length | Có `clearPassword()` cho bản sao trong spec |
| `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` | Dẫn xuất key từ password | Không phải AES cipher hay memory-hard KDF |
| `SecretKeySpec` | Bao raw derived bytes thành key cho AES | Xóa buffer trung gian khi thích hợp; đừng log |
| `KeyGenerator.getInstance("AES")` | Sinh key AES ngẫu nhiên theo provider | Phân biệt với password derivation |
| `SecureRandom` | Sinh salt/nonce/key material an toàn | Không dùng `Random` cho secret |
| `Cipher.getInstance("AES/GCM/NoPadding")` | AEAD encryption/decryption | Nonce unique với cùng key, tag được kiểm khi giải mã |
| `GCMParameterSpec(128, nonce)` | Tag length và nonce cho AES-GCM | Nonce 12 byte phổ biến; tag 128 bit |
| `Cipher.updateAAD(...)` | Xác thực metadata ngoài plaintext | AAD phải cung cấp trước dữ liệu ciphertext |
| `AEADBadTagException` | Dấu hiệu giải mã AEAD không xác thực được | Từ chối toàn bộ bản mã, không dùng plaintext |

**Lưu ý interoperability password:** `char[]` trong `PBEKeySpec` không tự chứng minh toàn bộ hệ thống dùng cùng chuẩn hóa Unicode/encoding. Khi cần tương tác Java với thư viện/ngôn ngữ khác, phải xác định cách biến password thành byte string, Unicode normalization, KDF variant và test vector. Một password tiếng Việt có dấu có thể tạo key khác nếu hai hệ thống tiền xử lý Unicode khác nhau. Không tự chuyển password thành String nhiều bản sao chỉ để “dễ encode”.

## 12. Câu hỏi tự kiểm tra

**1. PBKDF2 xuất 256 bit có làm một password 6 chữ số thành khóa mạnh như random AES-256 không?**  
Không. Output dài 256 bit nhưng không gian password có thể bị duyệt ngoại tuyến. Work factor khiến mỗi lần đoán chậm hơn, không tạo thêm entropy cho mật khẩu.

**2. Nếu không lưu salt thì có giải mã PBE được không?**  
Thông thường không thể dẫn xuất lại đúng key nếu thiếu salt gốc, trừ khi còn bản key/metadata khác. Phải lưu salt kèm format/version/cost và ciphertext.

**3. Salt và nonce có giống nhau không?**  
Không. Salt đi vào KDF; nonce đi vào AES-GCM. Cả hai thường không bí mật nhưng có quy tắc riêng; đặc biệt GCM nonce không được lặp khi encrypt bằng cùng key.

**4. Khóa AES sinh ngẫu nhiên có cần PBKDF2 nữa không?**  
Không cần PBKDF2 để *tạo* raw random key. Nếu muốn **bọc** DEK bằng password thì có thể dùng KDF tạo KEK ở tầng riêng.

**5. Vì sao PBE phải dùng AEAD?**  
Để không chỉ giấu plaintext mà còn phát hiện dữ liệu bị thay đổi. Mật khẩu đúng mà ciphertext bị sửa vẫn phải bị từ chối.

**6. Dữ liệu có được bảo vệ trước đoán password ngoại tuyến chỉ vì server giới hạn 5 lần login không?**  
Không. Người giữ ciphertext có thể thử candidate offline; cần password mạnh và KDF được cấu hình đúng.

**7. Envelope encryption có loại bỏ việc phải tin password mạnh không?**  
Không khi KEK dẫn xuất từ password. Nó chủ yếu giúp tách DEK khỏi KEK, dễ rewrap/rotate và chia sẻ quyền.

**8. Nếu thay password trong envelope, ciphertext dữ liệu có luôn cần re-encrypt không?**  
Không, có thể rewrap DEK. Nhưng nếu DEK hoặc wrappedDEK cũ đã lộ, cần đánh giá rotate/re-encrypt theo mức độ compromise.

**9. Với hệ thống backend tự mã hóa dữ liệu, nên giữ key như thế nào?**  
Thường dùng random DEK với wrapped key/KMS hoặc HSM và IAM/audit/recovery; không hard-code một password chung trong source.

**10. PBE có phải là cách lưu mật khẩu đăng nhập tốt nhất?**  
Không. Password login nên lưu verifier băm bằng thuật toán password hashing phù hợp, không mã hóa đảo ngược chỉ để so sánh password.

## Tài liệu tham khảo

- [NIST SP 800-132 – Recommendation for Password-Based Key Derivation: Storage Applications](https://csrc.nist.gov/pubs/sp/800/132/final): nền tảng password-derived master key, salt và iteration.
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): lựa chọn Argon2id/scrypt/PBKDF2, chi phí đoán mật khẩu và khác biệt hashing/encryption.
- [Java SE 17 – PBEKeySpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html): mô hình password `char[]`, salt, iteration, key length và `clearPassword()`.
- [Java SE 17 – SecretKeyFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/SecretKeyFactory.html): tạo `SecretKey` từ `KeySpec`.
- [Java SE 17 – Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): PBKDF2, AES, PBE schemes và các tên thuật toán Java.
- [Java SE 17 – Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): `AES/GCM/NoPadding`, `updateAAD`, authentication tag và nonce uniqueness.
- [Java SE 17 – KeyGenerator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyGenerator.html) và [SecureRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html): tạo khóa ngẫu nhiên và nguồn entropy theo provider.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="password-key-derivation-008">7.8 Xử lý char&#91;&#93;, clearPassword và giới hạn của việc xóa dữ liệu nhạy cảm trên JVM</a>

<details>
<summary>Xem chi tiết</summary>

# 7.8. Xử lý char[], clearPassword và giới hạn của việc xóa dữ liệu nhạy cảm trên JVM

## 1. Vấn đề cần giải quyết: vòng đời mật khẩu trong bộ nhớ

Mật khẩu (password), passphrase, mã PIN hoặc nguyên liệu dùng để dẫn xuất khóa thường chỉ cần tồn tại trong bộ nhớ trong một khoảng thời gian ngắn: nhận từ người dùng, dùng để xác minh hoặc dẫn xuất khóa, rồi không cần giữ dạng rõ nữa. Trên Java/JVM, vấn đề không chỉ là **không log mật khẩu** mà còn là cách đại diện dữ liệu và **ai sở hữu từng bản sao**.

Ba mục tiêu thực tế:

1. **Giảm thời gian lưu trữ plaintext password:** giải phóng/ghi đè những bộ đệm có thể kiểm soát ngay khi hoàn thành công việc.
2. **Hạn chế tạo bản sao ngoài ý muốn:** đặc biệt là `String`, chuỗi được concat, buffer của encoder, biểu mẫu, object của thư viện, bản sao được trả từ API.
3. **Hiểu giới hạn:** thao tác xoá trên một `char[]` hoặc gọi `PBEKeySpec.clearPassword()` **không chứng minh** rằng mọi dấu vết mật khẩu trong tiến trình JVM, bộ nhớ vật lý, heap dump, swap, snapshot hay thư viện native đã biến mất.

Trong JCA/JCE, `char[]` và `javax.crypto.spec.PBEKeySpec` là các công cụ giúp kiểm soát *một phần* vòng đời mật khẩu. Đây là một biện pháp giảm thiểu rủi ro, **không phải cam kết xoá bí mật tuyệt đối**.

## 2. Vì sao ưu tiên char[] thay vì String cho mật khẩu dạng rõ?

### 2.1. String không có API xoá từng ký tự

`String` là immutable. Sau khi tạo `String password`, ứng dụng không có API công khai để ghi đè đáng tin cậy các byte/ký tự do `String` lưu trữ. Việc gán `password = null` chỉ bỏ một reference; thời điểm thu gom rác không xác định, và bộ nhớ cũ có thể còn dấu vết cho đến khi được tái sử dụng hoặc bị ghi đè. `String` có thể tồn tại lâu hơn dự kiến qua log, exception, cache, stack trace, request object, serializer hoặc thao tác nối chuỗi.

`char[]` thì mutable: ứng dụng có thể gọi:

```java
Arrays.fill(password, '\0');
```

Điều này **ghi giá trị NUL vào các phần tử của chính mảng `password`**, khi phép ghi được thực thi. Nó hỗ trợ hạn chế một số bản sao ở heap mà ứng dụng đang sở hữu.

| Tiêu chí | `String` | `char[]` |
| --- | --- | --- |
| Có thể sửa trực tiếp nội dung bằng API công khai? | Không | Có, dùng `Arrays.fill` hoặc tự đặt từng phần tử |
| Có thể chủ động xoá buffer do mình sở hữu? | Không bảo đảm bằng API chuẩn | Có thể yêu cầu ghi đè buffer cụ thể, với giới hạn của JVM |
| Có thể bị copy sang nhiều nơi? | Có | Có |
| Có bảo đảm xóa mọi dấu vết trong RAM/heap? | Không | **Không** |
| Dùng trực tiếp với `PBEKeySpec`? | Không; phải tạo `char[]` | Có |

**Lưu ý quan trọng:** `char[]` không “an toàn tuyệt đối” hoặc “được JVM bảo vệ bí mật”. Nó chỉ cho phép yêu cầu sửa nội dung một mảng cụ thể. Các bản sao khác không được xóa chỉ vì ta đã sửa mảng ban đầu.

### 2.2. Một số thao tác nên tránh

```java
// Ví dụ lỗi khi xử lý mật khẩu thật:
String immutable = new String(passwordChars);  // Tạo bản sao String khó chủ động xóa.
String text = String.valueOf(passwordChars);    // Cũng tạo String.
String logLine = "password=" + new String(passwordChars);
System.out.println(passwordChars);             // Không dùng để hiển thị bí mật.
```

Đây là **đoạn mã minh họa các anti-pattern**, không phải chương trình hoàn chỉnh. `String.valueOf(char[])` tạo `String` mới chứa ký tự; `System.out.println(char[])` cũng có thể in nội dung qua overload dành cho `char[]`, do đó **không log bất kỳ char array mật khẩu nào**. Thận trọng với `Arrays.toString(passwordChars)`, debugger/watch expression, metrics labels, log structured fields và message của exception.

Cũng nên tránh chuyển mật khẩu sang `StringBuilder`/`StringBuffer` chỉ để tiếp tục xử lý: chúng có bộ đệm riêng và không tự trở thành cơ chế zeroization đáng tin cậy. Ngược lại, nếu framework đầu vào **đã** tạo `String`, việc gọi `string.toCharArray()` chỉ tạo thêm mảng; **không xoá được String nguồn**.

## 3. Quyền sở hữu dữ liệu: một mật khẩu có thể có nhiều bản sao

Xét luồng đơn giản:

```text
Nguồn nhập (terminal / UI / HTTP framework)
   |
   v
char[] callerPassword  -----------------------------+
   |                                                |
   | new PBEKeySpec(callerPassword, ...)             | Arrays.fill(callerPassword, '\0')
   v                                                | chỉ xóa mảng bên gọi
PBEKeySpec: bản sao nội bộ                         <----+
   |
   | getPassword()
   v
char[] trả về: một bản sao mới
   |
   +-- SecretKeyFactory / provider tạo dữ liệu KDF trung gian
   +-- SecretKey và có thể có byte[] từ getEncoded()
   +-- heap, native provider, stack, log, snapshot (tùy hệ thống)
```

**Mỗi mũi tên copy là một vùng phải đánh giá riêng.** Xóa `callerPassword` không tự xóa vùng nhớ trong `PBEKeySpec`. Xóa `PBEKeySpec` không tự xóa những `char[]` đã trả từ `getPassword()` hay những byte array của khóa dẫn xuất. Xóa `byte[]` mã hóa đầu ra không có nghĩa đối tượng `SecretKey` hoặc provider đã xóa bản sao của chúng.

Đây là lý do không nên thiết kế phương thức nhận password mà âm thầm giữ lại mảng do caller sở hữu hoặc đưa vào cache. Cần xác định rõ: caller hay callee chịu trách nhiệm xóa, có copy hay không, thời điểm có thể xóa và thành phần nào không kiểm soát được.

## 4. PBEKeySpec: bản sao của password và clearPassword()

`javax.crypto.spec.PBEKeySpec` dùng để cung cấp password và tùy chọn salt, iteration count, key length cho các thuật toán *Password-Based Encryption/Key Derivation* (PBE/KDF) thông qua `SecretKeyFactory`.

Ví dụ tạo spec:

```java
PBEKeySpec spec = new PBEKeySpec(passwordChars, salt, iterations, 256);
```

Chữ ký tương ứng là `PBEKeySpec(char[] password, byte[] salt, int iterationCount, int keyLength)`; `keyLength` tính bằng **bit**, không phải byte.

### 4.1. Constructor copy dữ liệu

`PBEKeySpec` tạo **bản sao riêng của password** mà ứng dụng chuyển vào. Vì vậy:

- Sửa hoặc `Arrays.fill(passwordChars, '\0')` sau khi tạo spec **không xóa** password trong spec.
- Điều này hữu ích để tránh thay đổi ngoài ý muốn khi KDF đang chạy, nhưng cũng tạo thêm vùng plaintext password trong bộ nhớ.
- Spec có API xoá **bản sao của chính nó**, không có quyền tự động xoá mọi mảng bên ngoài.

Salt cũng được sao chép khi lưu vào spec; `getSalt()` trả một bản sao. Salt thường **không bí mật**, phải lưu cùng thông tin dẫn xuất khi cần xác minh lại, nhưng tính unique và random quan trọng. Không cần đối xử với salt như password.

### 4.2. getPassword() trả bản sao, không trao quyền sở hữu nội bộ

`spec.getPassword()` trả một `char[]` **mới** chứa password mà spec đang lưu. Nếu gọi lặp nhiều lần, ứng dụng có thể tạo nhiều bản sao nhạy cảm mà không nhận ra.

```java
char[] returnedCopy = spec.getPassword();
try {
    // Nếu thật sự cần nội dung, dùng càng ngắn càng tốt.
} finally {
    Arrays.fill(returnedCopy, '\0');
}
```

Đây là đoạn mã minh họa; `spec` đã được tạo trước đó. Việc xoá `returnedCopy` **không** xoá bản sao nội bộ của spec và không xoá mảng gốc. Tránh gọi `getPassword()` chỉ để log, debug, so sánh không cần thiết hoặc kiểm tra độ dài.

### 4.3. clearPassword() tác động vào đâu?

`spec.clearPassword()` yêu cầu xóa bản sao password **được lưu bên trong PBEKeySpec**. Theo hợp đồng API Java, sau khi clear, `getPassword()` sẽ ném `IllegalStateException`.

Điều `clearPassword()` **không làm**:

- Không sửa mảng `passwordChars` do caller truyền vào.
- Không tìm và sửa các bản sao `char[]` do `getPassword()` đã trả trước đó.
- Không xóa `String` nếu framework đã tạo String trước đó.
- Không bảo đảm xóa password bytes tạm do `SecretKeyFactory`/provider/native code tạo ra.
- Không xóa material trong `SecretKey` được KDF trả về và không tự hủy byte[] do `getEncoded()` trả về.
- Không hứa dọn sạch heap dump, CPU register, swap, OS page cache, core dump, backup/snapshot.

**Thứ tự sử dụng:** tạo `PBEKeySpec` → dùng nó để tạo key/derived bytes → gọi `clearPassword()` trong `finally`. **Đừng clear spec trước khi `SecretKeyFactory.generateSecret(spec)` nếu vẫn cần password để dẫn xuất khóa.**

Nếu dùng cùng một spec qua nhiều lần, cần giới hạn vòng đời; tốt nhất chỉ tạo khi cần và đóng “cửa sổ tồn tại” ngay sau công việc. Sau `clearPassword()`, phải tạo spec mới bằng password còn hợp lệ nếu muốn thực hiện derivation khác, không được tự mong spec tái sử dụng nội dung đã xóa.

### 4.4. So sánh API thường bị nhầm

| API/Thao tác | Đối tượng bị tác động | Điều **không** được suy ra |
| --- | --- | --- |
| `Arrays.fill(charArray, '\0')` | Phần tử của đúng `charArray` | Không xóa những bản sao khác, không bảo đảm phần cứng/RAM đã được tẩy sạch |
| `PBEKeySpec.clearPassword()` | Bản sao password nội bộ của spec | Không xoá caller `char[]`, array trả từ getPassword, khóa dẫn xuất |
| `PBEKeySpec.getPassword()` | Tạo và trả **bản sao** mới | Không phải reference trực tiếp để xóa nội bộ spec |
| `Arrays.fill(keyBytes, (byte) 0)` | Byte array cụ thể | Không xóa key material trong object/provider khác |
| `password = null` | Reference cục bộ | Không có hợp đồng xóa nội dung trong heap ngay |
| `System.gc()` | Chỉ là yêu cầu/hint cho GC | Không phải API zeroization, không bảo đảm chạy/xóa đúng lúc |

## 5. Demo Java 17+ chạy độc lập và stdout đã xác minh

Bài demo chứng minh chính xác ba tình huống: một mảng do caller sở hữu, bản sao password qua `PBEKeySpec.getPassword()`, và password nội bộ bị vô hiệu hóa sau `clearPassword()`. Chương trình còn tạo derived key bằng `PBKDF2WithHmacSHA256` để cho thấy spec vẫn hoạt động dù caller đã zero mảng gốc.

**Chú ý:** chuỗi ký tự cố định trong mã chỉ phục vụ ví dụ có kết quả xác định, **không phải mật khẩu thật**. Giá trị `10_000` iterations là cấu hình minh họa cú pháp và tốc độ chạy, **không** phải thông số an toàn production. Sản phẩm cần lựa chọn thuật toán và chi phí dẫn xuất phù hợp theo hướng dẫn hiện hành, benchmark và threat model.

Tạo file `PasswordMemoryDemo.java`:

```java
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordMemoryDemo {
    private static boolean allZero(char[] data) {
        for (char c : data) {
            if (c != '\0') return false;
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        // Fixed characters for a repeatable DEMO; never hardcode real passwords.
        char[] entered = {'d', 'e', 'm', 'o', '-', 'o', 'n', 'l', 'y'};
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        PBEKeySpec spec = null;
        char[] fromSpec = null;
        byte[] keyBytes = null;
        try {
            spec = new PBEKeySpec(entered, salt, 10_000, 256);
            fromSpec = spec.getPassword(); // A separate copy; wipe it too.
            System.out.println("Separate returned copy: " + (fromSpec != entered));

            Arrays.fill(entered, '\0'); // Clear caller-owned array.
            System.out.println("Caller cleared: " + allZero(entered));
            System.out.println("Spec copy still held password: " +
                    !allZero(fromSpec));

            Arrays.fill(fromSpec, '\0');
            fromSpec = null;

            SecretKey derived = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec);
            keyBytes = derived.getEncoded(); // Another sensitive buffer.
            System.out.println("Derived key size: " + keyBytes.length + " bytes");

            spec.clearPassword(); // Wipes internal password copy.
            boolean unavailable = false;
            try {
                spec.getPassword();
            } catch (IllegalStateException expected) {
                unavailable = true;
            }
            System.out.println("Spec inaccessible after clear: " + unavailable);
        } finally {
            if (fromSpec != null) Arrays.fill(fromSpec, '\0');
            if (keyBytes != null) Arrays.fill(keyBytes, (byte) 0);
            Arrays.fill(entered, '\0');
            if (spec != null) spec.clearPassword();
        }
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac PasswordMemoryDemo.java
java PasswordMemoryDemo
```

Stdout chính xác:

```text
Separate returned copy: true
Caller cleared: true
Spec copy still held password: true
Derived key size: 32 bytes
Spec inaccessible after clear: true
```

**Giải thích từng dòng:**

1. `spec = new PBEKeySpec(entered, salt, ...)` tạo bản sao nội bộ; `fromSpec = spec.getPassword()` tạo **một bản sao nữa**, khác reference với `entered`.
2. `Arrays.fill(entered, '\0')` làm mọi phần tử trong mảng caller bằng NUL. Thao tác này **không** làm thay đổi nội dung `fromSpec` đã copy trước đó; đó là lý do dòng `Spec copy still held password: true`.
3. Ta chủ động xóa mảng `fromSpec` ngay sau khi dùng. Spec vẫn giữ bản sao nội bộ để `SecretKeyFactory.generateSecret(spec)` có thể hoàn thành dẫn xuất.
4. Key length 256 **bit** tương ứng `32 bytes` từ `derived.getEncoded()` trong demo này. Byte array `keyBytes` cũng nhạy cảm, nên được `Arrays.fill` trong `finally`.
5. `spec.clearPassword()` xóa password nội bộ và các lời gọi `getPassword()` sau đó bị từ chối bằng `IllegalStateException` theo API.
6. `finally` bảo đảm gọi dọn dẹp trên các mảng đang sở hữu và spec ngay cả khi KDF hoặc bước khác ném exception. Nó **không** hứa xoá `SecretKey derived` hoặc các bản sao nội bộ của provider/JVM.

**Đừng đọc sai demo:** Kết quả `Caller cleared: true` chỉ kiểm tra **các phần tử của đúng một mảng trong ngữ cảnh chạy hiện tại**. Nó không phải phép đo chứng minh heap, native memory hoặc hệ điều hành không còn password. Việc chúng ta không in password/key cũng là chủ ý.

## 6. Xây dựng luồng nhập và xử lý password đúng hơn

### 6.1. Console.readPassword() cung cấp char[] (khác readLine())

`java.io.Console.readPassword(...)` hỗ trợ đọc ký tự mật khẩu (thường không echo ra màn hình) và trả về `char[]`. Đây là lựa chọn tốt cho ứng dụng CLI khi có interactive console.

Ví dụ **đoạn mã minh họa**, không phải một chương trình Java độc lập:

```java
Console console = System.console();
if (console == null) {
    throw new IllegalStateException("Interactive console unavailable");
}
char[] password = console.readPassword("Password: ");
if (password == null) {
    throw new IllegalStateException("End of input");
}
try {
    // Dùng password ngắn hạn với PBEKeySpec/SecretKeyFactory.
} finally {
    Arrays.fill(password, '\0');
}
```

Cần import `java.io.Console` và `java.util.Arrays`. `System.console()` có thể trả `null` khi chạy trong IDE, một số môi trường CI hoặc khi stdin/stdout được redirect; **không** tự động fallback sang `Scanner.nextLine()` mà không ý thức rằng cách đó tạo `String` và có thể echo/input handling khác.

Đừng làm `new String(console.readPassword())`: đoạn code đó tạo String dạng rõ và còn làm mất reference tới char[] vừa đọc nên không thể chủ động xóa mảng gốc.

### 6.2. JPasswordField và UI

Với Swing, `JPasswordField.getPassword()` trả `char[]` để tránh phải yêu cầu một String công khai. Sau khi sử dụng, `Arrays.fill(password, '\0')` đối với chính array nhận được và xóa nội dung trường UI theo chính sách (ví dụ khi form kết thúc). Không suy ra các bước đó đã xóa mọi bản sao từ UI document, clipboard, accessibility tooling, event queue hoặc desktop/swap. Không tự ý copy password vào clipboard hay đặt vào tooltip/exception.

### 6.3. Dữ liệu đã qua HTTP/JSON framework

Phần lớn API nhận JSON/web form thường đi qua decoder, request parser và các thư viện có thể tạo `String` hoặc byte buffer. Khi code ứng dụng nhận được `String password`, việc chuyển sang `password.toCharArray()` chỉ giúp bạn **quản lý mảng mới**; nó không điều khiển bộ nhớ giữ String ban đầu hoặc các bản sao của framework.

Cần đánh giá đường đi dữ liệu **từ lớp nhận request**, tắt log/trace chứa secret, cấu hình masking trong observability, giới hạn thời gian giữ request object và không đưa password vào session/cache/message queue ngoài nhu cầu. Không quảng cáo rằng chỉ thay DTO field `String` thành `char[]` là đã giải quyết zeroization.

## 7. Các bản sao nhạy cảm thường không nhìn thấy

| Nguồn sao chép | Vì sao có thể còn password? | Điều ứng dụng có thể làm |
| --- | --- | --- |
| `PBEKeySpec` constructor | Chủ động copy password để giữ nội dung dùng cho KDF | `clearPassword()` trong `finally` |
| `PBEKeySpec.getPassword()` | Trả về một array mới | Tránh gọi nếu không cần; xóa array đã nhận |
| `String`, nối chuỗi, formatter, logging | Immutable/copy, không có hợp đồng wipe | Tránh tạo và tuyệt đối không log dạng rõ |
| Charset/encoder và `byte[]` trung gian | Chuyển UTF-16 char sang bytes để mã hóa/KDF | Quản lý byte[] do mình tạo, `Arrays.fill(..., (byte)0)` |
| `SecretKeyFactory` và JCE provider | KDF có thể tạo bản sao/working buffers/private state | Giảm vòng đời object; kiểm tra API/provider, không hứa xóa được tất cả |
| `SecretKey.getEncoded()` | Thường trả mảng byte chứa key material mới; có thể `null` với khóa không export được | Wipe bản sao byte[] do caller sở hữu, tránh lấy key bytes nếu không cần |
| Heap dump, core dump, crash report | Snapshot có thể ghi lại object/buffer chưa xóa | Hạn chế quyền dump, giữ file dump an toàn, tránh dump môi trường production nếu không cần |
| Swap, hibernation, VM/container snapshot | OS/hạ tầng có thể ghi trang nhớ ra thiết bị lưu trữ | Chính sách bảo vệ host, swap/dump và ổ đĩa; threat model phù hợp |
| Native code/HSM/KMS/client library | Dữ liệu có thể ở ngoài heap và ngoài khả năng `Arrays.fill` của Java | Chọn provider/API với contract bảo vệ khóa rõ ràng; đừng mặc định mọi key bị xóa |

Một số key object có API `Destroyable.destroy()` hoặc triển khai đặc thù của provider. Nhưng **không được suy ra** rằng mọi `SecretKey` đều có cơ chế xóa vật liệu khóa đáng tin cậy, hỗ trợ destroy thành công hoặc dọn các bản sao đã từng tạo. Hãy đọc tài liệu của provider, kiểm tra hành vi và thiết kế hạn chế export bí mật.

## 8. Giới hạn căn bản của “xóa bộ nhớ” trên JVM

### 8.1. GC không phải công cụ zeroization

**Garbage Collection (GC)** có nhiệm vụ quản lý đối tượng không còn tham chiếu, không có hợp đồng đảm bảo ghi đè sạch mọi vùng nhớ ngay khi `password = null` hoặc `System.gc()`. `System.gc()` cũng chỉ là một yêu cầu; ứng dụng không có quyền yêu cầu JVM đi qua mọi vùng RAM và loại bỏ dấu vết bí mật.

JVM có thể di chuyển/copy object trong quá trình GC, để lại các vùng nhớ trước đó cho tới khi được ghi đè; trên nhiều hệ thống còn có dump/snapshot bên ngoài heap mà code Java không kiểm soát. Điều này làm cho “tôi đã fill array” khác với “không còn bản sao ở bất kỳ đâu”.

### 8.2. Trình biên dịch và tối ưu hóa

JIT/compiler có thể áp dụng các tối ưu hóa, ví dụ loại bỏ các phép ghi mà nó đánh giá không có ảnh hưởng quan sát được theo ngữ nghĩa chương trình. Không nên coi một `Arrays.fill` được viết ở source là **bảo đảm mật mã** rằng byte ở mọi cấp bộ nhớ chắc chắn đã được tẩy.

Điều đó **không** có nghĩa `Arrays.fill` vô dụng: nó là bước **best-effort** hợp lý để giảm thời gian tồn tại giá trị trong mảng do mình sở hữu. Khẳng định chính xác phải là “ứng dụng yêu cầu zero array cụ thể”, không phải “JVM/OS chứng minh toàn bộ secret đã bị tiêu huỷ”.

### 8.3. Biểu diễn dữ liệu và encoding

Java `char` là một **UTF-16 code unit**, không phải một byte UTF-8. Khi KDF hoặc giao thức cần password bytes, có thể phát sinh phép chuyển mã, bản sao của `byte[]`, chuẩn hóa Unicode và vùng nhớ của provider. Không coi `password.length` là số byte của password.

Để hai hệ thống dẫn xuất cùng khóa từ cùng mật khẩu, phải thống nhất **thuật toán KDF, salt, iterations/work factor, output length, encoding và normalization**; tránh tự chuyển đổi `char[]` sang String chỉ để “chuẩn hóa” mà không quản lý hậu quả. Các thuật toán PBE lịch sử có cách xử lý ký tự khác nhau; không tự suy diễn mọi provider đều chuyển char sang byte giống nhau.

### 8.4. Threat model quyết định mức cần thiết

Với một ứng dụng backend thông thường, ngăn mật khẩu trong logs, áp dụng KDF đúng, giới hạn quyền đọc heap dump và rút ngắn vòng đời bộ đệm có thể là những biện pháp thiết thực. Nhưng nếu đối thủ **đã chạy code trong cùng process hoặc có quyền đọc đầy đủ RAM**, việc zero một vài Java arrays không mang lại cách ly tuyệt đối.

Trong môi trường có yêu cầu rất cao, cân nhắc API/provider/HSM/KMS không xuất key material, tách tiến trình, giảm quyền tạo dump, mã hóa lưu trữ, hardening OS và thiết kế hạn chế nơi password dạng rõ được đưa tới. Java/JVM phổ thông không có contract tổng quát để “khóa trang nhớ và đảm bảo xóa mọi bản sao” chỉ bằng `char[]`.

## 9. Quy tắc triển khai thực tế: giảm lưu giữ bí mật có kiểm soát

### 9.1. Nguyên tắc ownership

- **Caller sở hữu** mảng do nó nhận từ UI/console/request: caller có trách nhiệm xóa sau khi xong, thường ở `finally`.
- **Callee chỉ giữ tạm** password khi xử lý và không lưu object/reference vào cache, field lâu dài, thread local, queue hoặc callback không cần thiết.
- Nếu thư viện tạo bản sao như `PBEKeySpec`, **đóng vòng đời bằng API chính thức** của thư viện (ở đây là `clearPassword`) thay vì tìm cách phản chiếu vào field private.
- Mảng `byte[]` từ encoding hay `getEncoded()` thuộc phạm vi caller khi đã nhận về: wipe khi không còn cần, nhưng hiểu rằng việc này **không** đụng tới material bên trong object khác.
- Không xóa buffer **trước khi** crypto API cần đọc nó. Sai thứ tự có thể khiến derive sai hoặc dẫn tới logic bảo mật sai. Tạo/derive xong, dọn buffer càng sớm càng tốt.

### 9.2. Vòng đời khuyến nghị

```text
NHẬN password vào char[] khi API đầu vào hỗ trợ
    |
    v
VALIDATE input tối thiểu (không log password)
    |
    v
Tạo PBEKeySpec (tạo bản sao nội bộ)
    |
    v
SecretKeyFactory / password verification / key derivation
    |
    +-- Dọn các byte[]/char[] tạm đã tạo và sở hữu
    +-- Dọn vật liệu khóa nếu API/provider có contract phù hợp
    |
    v
PBEKeySpec.clearPassword() trong finally
    |
    v
Arrays.fill(callerPassword, '\0') trong finally
    |
    v
Tiếp tục xử lý bằng kết quả xác minh/khóa được bảo vệ,
không giữ password dạng rõ lâu hơn cần thiết.
```

Không phải mọi hệ thống đều có đủ quyền kiểm soát để thực hiện toàn bộ quy trình; cần ghi rõ giới hạn của framework và thư viện.

### 9.3. Password hashing khác với quản lý password trong RAM

Việc chọn `char[]` và xoá bộ đệm **không thay thế** thuật toán password hashing chống dò offline. Nếu lưu thông tin xác thực người dùng, sử dụng password-hashing chuyên dụng có salt riêng cho mỗi tài khoản/bản ghi và chi phí đủ cao; ví dụ Argon2id hoặc lựa chọn phù hợp theo chính sách, thư viện và yêu cầu hệ thống. PBKDF2-HMAC-SHA256 có thể cần trong môi trường Java/JCA hay yêu cầu tương thích, nhưng cost factor phải được thiết kế/điều chỉnh, không lấy `10_000` trong demo làm khuyến nghị production.

Nếu dùng password để **mã hóa dữ liệu**, cần KDF phù hợp để dẫn xuất khóa và mode mã hóa được xác thực như AES-GCM với nonce đúng; không tự hash nhanh password bằng SHA-256 rồi dùng thẳng làm AES key nếu thiếu thiết kế và threat model. Salt, thông số KDF và IV thường **không bí mật**, nhưng cần lưu đúng để mở lại dữ liệu; **khóa dẫn xuất** và plaintext password phải được bảo vệ.

### 9.4. Log, telemetry và kiểm thử

- Không log password, salt+password, derived key bytes hay char[] bằng `toString()`/`Arrays.toString()`.
- Không đưa secret vào URL query, lỗi phản hồi, exception text, event bus hoặc environment variable nếu có lựa chọn an toàn hơn; môi trường và framework có thể giữ bản sao `String`.
- Khi test, dùng **giá trị giả**, không dùng tài khoản thật hoặc token production. Kiểm tra array đã zero được bằng logic test, nhưng **đừng** viết test khẳng định bộ nhớ native/heap dump hoàn toàn sạch.
- Kiểm tra `finally` trên cả đường thành công và thất bại: input invalid, exception từ KDF, timeout, cancellation, shutdown. Không để một exception làm bỏ qua bước cleanup của một buffer khác.
- Với xử lý bất đồng bộ, tránh giữ mật khẩu trong lambda/callback/`CompletableFuture`/queue lâu hơn mong muốn; tính lại ownership và thời điểm safe-to-clear.

## 10. Những ngộ nhận và lỗi thường gặp

| Ngộ nhận hoặc lỗi | Vì sao sai | Hướng khắc phục |
| --- | --- | --- |
| “Đã đổi `String` thành `char[]` nên password không thể bị lộ” | `char[]` vẫn ở heap, có thể bị copy, dump, log và bị đọc trong process | Quản lý vòng đời, quyền truy cập và threat model |
| “`clearPassword()` xoá mảng truyền vào constructor” | `PBEKeySpec` lưu bản sao nội bộ riêng | `clearPassword()` cho spec **và** `Arrays.fill` cho caller |
| “Gọi `getPassword()` nhiều lần không tốn bộ nhớ bí mật” | Mỗi lời gọi tạo một bản sao char[] mới | Tránh gọi thừa, wipe mọi bản sao đã nhận |
| “`Arrays.fill` xoá String được tạo trước đó” | String immutable/không cùng vùng nhớ | Tránh tạo String password ngay từ đầu khi có thể |
| “Đặt reference về `null` sẽ xoá password ngay” | GC không có hợp đồng wipe tức thời | Dọn buffer có thể sửa, giảm lifetime và bảo vệ dump |
| “`System.gc()` là API xóa secret” | Không có bảo đảm thời gian/ghi đè | Không dùng GC làm zeroization |
| “Đã xóa mảng `keyBytes` nên `SecretKey` chắc chắn bị xóa” | Object/provider có thể giữ key material riêng | Hạn chế export key và kiểm tra provider-specific cleanup |
| “Xoá password trước generateSecret sẽ an toàn hơn” | KDF cần dữ liệu đầu vào còn hợp lệ | Cleanup **sau** sử dụng, trong `finally` |
| “Password là char[] nên không thể xuất hiện trong log” | Logging, formatting hoặc debug có thể tạo String/ghi trực tiếp char[] | Thiết kế redaction và chính sách debug |
| “Xóa bộ nhớ thay thế được password hashing” | Dữ liệu mật khẩu bị rò rỉ từ DB vẫn có nguy cơ offline cracking | KDF/password-hashing có salt và cost phù hợp |
| “Một lần Arrays.fill chứng minh không còn password trong RAM” | Bản sao, provider, JIT/GC, native memory, swap, snapshots | Diễn đạt như **best-effort mitigation**, không cam kết tuyệt đối |

## 11. Bài tập và câu hỏi tự kiểm tra

1. `PBEKeySpec` được tạo từ `char[] original`. Nếu gọi `Arrays.fill(original, '\0')`, vì sao `SecretKeyFactory.generateSecret(spec)` vẫn có thể dẫn xuất khóa?
2. `spec.getPassword()` được gọi hai lần. Có bao nhiêu bản sao phải đánh giá về ownership? `spec.clearPassword()` có tự xóa cả hai mảng được trả về không?
3. Tại sao `new String(passwordChars)` là bước cần tránh trong luồng xử lý mật khẩu dù sau đó đã `Arrays.fill(passwordChars, '\0')`?
4. Nêu sự khác biệt giữa xóa mảng caller, xóa bản sao của spec, xóa `getEncoded()` result và phá hủy state của `SecretKey`/provider.
5. Vì sao `finally` quan trọng khi `SecretKeyFactory.generateSecret` có thể ném exception?
6. Nếu framework gửi password cho controller dưới dạng String, việc `toCharArray()` mang lại ích lợi gì và **không thể giải quyết** điều gì?
7. Vì sao “zero char[]” và “dùng Argon2id/PBKDF2 đúng chuẩn” giải quyết hai nhóm rủi ro khác nhau?
8. Những thông tin nào có thể còn tồn tại trong heap dump hoặc snapshot dù tất cả mảng Java bạn thấy đều đã fill NUL?

**Thực hành:** sửa demo để gọi `spec.getPassword()` lần thứ hai rồi kiểm tra hai bản copy không cùng reference và phải được wipe độc lập. Thêm nhánh giả lập exception trong `try`, bảo đảm `finally` chạy và không in mật khẩu. Viết test cho `spec.getPassword()` sau `clearPassword()` ném `IllegalStateException`. Không chạy các thí nghiệm với mật khẩu thật, không ghi heap dump chứa secret production.

## 12. Tài liệu API chính thức

- [PBEKeySpec — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html): constructor, `getPassword`, `clearPassword`, sao chép password/salt và ngoại lệ sau clear.
- [SecretKeyFactory — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/SecretKeyFactory.html): tạo khóa bí mật từ `KeySpec` qua provider.
- [Arrays — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html): `Arrays.fill(char[], char)` và `Arrays.fill(byte[], byte)`.
- [Console — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Console.html): `readPassword` và hành vi khi không có interactive console.
- [JPasswordField — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/swing/JPasswordField.html): `getPassword()` thay cho các cách xử lý String không cần thiết.
- [Java Cryptography Architecture (JCA) Reference Guide — Java SE 17](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): khái niệm key, provider, `SecretKeyFactory` và password-based derivation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="password-key-derivation-009">7.9 Tương thích charset, Unicode và quy tắc chuyển đổi mật khẩu giữa các hệ thống</a>

<details>
<summary>Xem chi tiết</summary>

# 7.9 Tương thích charset, Unicode và quy tắc chuyển đổi mật khẩu giữa các hệ thống

Người dùng có thể gõ **cùng một mật khẩu theo cảm nhận** trên web, ứng dụng Java, Python, Node.js hay một hệ thống cũ nhưng bị từ chối đăng nhập. Một nguyên nhân khó phát hiện là các nền tảng tạo ra **những dãy byte khác nhau trước khi chạy hàm dẫn xuất khóa (KDF) hoặc password hash**: charset khác nhau, Unicode có nhiều biểu diễn hợp lệ, chuỗi bị `trim`/chuyển chữ hoa thường, hoặc thư viện có quy tắc riêng cho giới hạn byte.

Bài này đi từ ký tự → Unicode → byte → KDF, cách thiết lập hợp đồng tương thích, kiểm thử xuyên hệ thống và di chuyển password verifier cũ. **Chính sách chuyển đổi mật khẩu phải được quy định, có phiên bản và kiểm thử bằng byte thật**; một thay đổi làm người dùng không thể đăng nhập, hoặc khiến hai mật khẩu vốn khác nhau trở thành một, đều là lỗi bảo mật.

## 1. Mục tiêu học tập

Sau bài học, bạn sẽ:

1. Giải thích được sự khác nhau giữa **ký tự hiển thị, Unicode code point, UTF-16 code unit, grapheme cluster và byte**.
2. Chỉ ra vì sao `"é"` dựng sẵn và `"e"` + dấu tổ hợp có thể trông giống nhưng tạo byte khác; phân biệt **NFC, NFD, NFKC, NFKD**.
3. Xây dựng một **password profile**: decode, validate, normalize, encode, KDF, salt, tham số, metadata, version và chính sách lỗi.
4. Sử dụng Java 17+ để kiểm thử UTF-8 strict, `Normalizer`, độ dài Unicode, `PBEKeySpec` và `PBKDF2WithHmacSHA256`.
5. Thiết kế **golden vectors** để kiểm tương thích Java/Python/JavaScript và kế hoạch chuyển đổi từ password hash cũ mà không mất tài khoản.

## 2. Phân biệt ba lớp dữ liệu mật khẩu

~~~text
Phím gõ / copy-paste / IME
          |
          v
Dãy Unicode người dùng nhập
          |
          | validate, policy đã định nghĩa, normalization (nếu có)
          v
Dãy Unicode theo password profile
          |
          | encode bằng charset quy định (thường UTF-8 strict)
          v
Dãy byte password
          |
          | password KDF/hash + salt + work factor (+ pepper nếu có)
          v
Verifier lưu trữ: algorithm, profile id, params, salt, hash
~~~

**Ký tự hiển thị** là điều mắt người đọc nhận thấy; **Unicode code point** là mã trừu tượng cho ký tự; **Java `String`** lưu các code point dưới dạng đơn vị UTF-16; còn **KDF** hoạt động theo hợp đồng đầu vào của thư viện, có thể là byte hoặc `char[]`. Đừng gộp cả ba thành một khái niệm “chuỗi password”.

Ví dụ:

| Chuỗi theo code point | Byte UTF-8 | Nhìn trên UI |
| --- | --- | --- |
| `U+00E9` (LATIN SMALL LETTER E WITH ACUTE) | `C3 A9` | é |
| `U+0065 U+0301` (e + COMBINING ACUTE ACCENT) | `65 CC 81` | e + dấu, thường thấy như é |

Hai chuỗi có **byte UTF-8 khác nhau** trước normalization. Vì vậy `hash(passwordBytes)` hoặc KDF trên byte nguyên bản sẽ cho kết quả khác, trừ trường hợp va chạm cực kỳ khó xảy ra của KDF. Khi cùng chọn NFC, cả hai biểu diễn chuyển về cùng một chuỗi Unicode rồi mới encode.

**Không dùng SHA-256 thô để lưu mật khẩu**: ví dụ `hash(passwordBytes)` ở đây là minh họa sự phụ thuộc vào byte, không phải khuyến nghị triển khai. Mật khẩu cần password hashing/KDF chuyên dụng như Argon2id, scrypt hoặc PBKDF2 theo tiêu chuẩn và ràng buộc ứng dụng.

## 3. Charset: Java String không phải byte UTF-8

**Charset** là quy tắc mã hóa ký tự thành byte và giải mã ngược lại. Cùng chuỗi Unicode có thể sinh byte khác nhau:

| Charset | Nhận xét |
| --- | --- |
| **UTF-8** | Mỗi Unicode scalar value mã hóa bằng 1–4 byte; phù hợp cho giao thức mới có tiếng Việt/emoji. |
| **UTF-16LE / UTF-16BE** | Thứ tự byte khác; ký tự ngoài BMP dùng surrogate pair (4 byte); không giống UTF-8. |
| **ISO-8859-1** | Bộ mã một byte, không thể biểu diễn toàn bộ tiếng Việt/emoji. |
| **Windows-1252** | Bộ mã legacy thường thấy; có khác biệt với ISO-8859-1 ở một số mã byte. |
| **Default charset** | Có thể thay đổi theo JVM version/hệ điều hành/cấu hình. Không đủ làm hợp đồng password lâu dài. |

Ví dụ Java sai về khả năng di chuyển:

~~~java
byte[] raw = password.getBytes(); // dùng charset mặc định của môi trường
~~~

Nếu contract là UTF-8 và đầu vào hợp lệ:

~~~java
byte[] raw = password.getBytes(StandardCharsets.UTF_8);
~~~

Cách thứ hai dùng đúng charset tường minh, nhưng phải hiểu **unpaired surrogate**: một `String` Java có thể chứa một code unit UTF-16 thuộc dải surrogate nhưng không có cặp đúng. Một số encoder/decoder tiện ích có thể âm thầm **thay** dữ liệu malformed thay vì từ chối. Để kiểm soát byte mật khẩu, hãy dùng `CharsetEncoder`/`CharsetDecoder` với `CodingErrorAction.REPORT` và **reject** chuỗi/byte Unicode không hợp lệ thay vì thay bằng `?` hoặc ký tự U+FFFD.

Với HTTP/JSON, bước **transport decode** phải rõ ràng. `%C3%A9` trong URL-form-encoded input là biểu diễn của byte UTF-8; nếu gateway đã decode thành chữ `é` thì ứng dụng **không decode thêm lần nữa** hoặc băm nguyên 6 ký tự `%C3%A9`. Không đưa mật khẩu vào URL query: URL thường bị ghi log, lưu lịch sử hoặc chia sẻ qua hệ thống phân tích. Truyền credential qua TLS và luồng xử lý xác thực phù hợp.

## 4. Unicode normalization và lý do phải chọn đúng form

Trong Java:

~~~java
String normalized = Normalizer.normalize(
        password, Normalizer.Form.NFC);
~~~

Unicode đưa ra bốn dạng normalization:

| Form | Cách chuẩn hóa | Tác động tới mật khẩu |
| --- | --- | --- |
| **NFC** | Canonical decomposition + composition | Hợp nhất nhiều chuỗi *canonically equivalent* như `é` dựng sẵn và `e` + dấu tổ hợp. |
| **NFD** | Canonical decomposition | Biểu diễn chuẩn dạng phân rã; khác byte NFC, phải dùng cùng profile. |
| **NFKC** | Compatibility decomposition + composition | Hợp nhất thêm nhiều khác biệt tương thích/hình thức, ví dụ fullwidth `Ａ` và ASCII `A`. |
| **NFKD** | Compatibility decomposition | Có tác động tương thích như NFKC nhưng giữ dạng phân rã. |
| **Không normalize** | Giữ nguyên code point người dùng nhập | Phân biệt cả các chuỗi canonically equivalent; nếu đây là policy lịch sử thì phải giữ khi xác minh hash cũ. |

Các ví dụ:

~~~text
NFC("\u00E9") == NFC("e\u0301")    → true
NFC("Ａ")     == NFC("A")          → false
NFKC("Ａ")    == NFKC("A")         → true
~~~

`Ａ` là FULLWIDTH LATIN CAPITAL LETTER A (U+FF21). NFKC không phải “NFC mạnh hơn nhưng luôn an toàn hơn”: nó **thay đổi tập cặp mật khẩu được coi là tương đương**, có thể gộp các mật khẩu mà người dùng tưởng khác. NFD/NFKD cũng không tự mang lại an toàn cao hơn; sự phù hợp phụ thuộc chính sách sản phẩm và hợp đồng đã lưu.

**NIST SP 800-63B hiện hành khuyến nghị NFC** cho hệ thống chấp nhận Unicode password, trước bước hashing: https://pages.nist.gov/800-63-4/sp800-63b.html . Theo Unicode, NFC xử lý canonical equivalence, trong khi NFKC thêm compatibility equivalence: https://www.unicode.org/faq/normalization.html . Lựa chọn cho hệ thống mới có thể dựa trên khuyến nghị này, nhưng **không được tự động đổi chính sách một cơ sở hash legacy** vì sẽ làm thay byte đầu vào khi login.

### 4.1. Nhìn giống nhau không có nghĩa Unicode tương đương

- Latin `a` (U+0061) khác Cyrillic `а` (U+0430), dù font có thể khiến chúng khó phân biệt. NFC không tự gộp.
- Unicode có **variation selector**, dấu tổ hợp, zero-width joiner và emoji nhiều code point; một hình vẽ trên UI có thể ứng với nhiều dãy code point.
- ASCII space `U+0020` khác non-breaking space `U+00A0` hoặc nhiều loại khoảng trắng Unicode.
- Bộ gõ tiếng Việt, bàn phím di động và copy-paste có thể sinh dạng Unicode khác nhau; không thể bảo đảm byte giống nhau bằng cách chỉ nhìn màn hình.
- Normalization không tự bảo đảm mọi chuỗi trông tương tự thành giống nhau, cũng không hợp thức hóa malformed Unicode.

**Chọn một form cho profile mới rồi dùng đúng form đó tại verifier.** Không normalize thêm “cho chắc” ở nhiều tầng theo những quy tắc khác nhau.

## 5. Những biến đổi nguy hiểm ngoài hợp đồng

Một ứng dụng có thể vô tình đổi password qua các thao tác quen thuộc:

| Biến đổi | Hậu quả |
| --- | --- |
| `trim()` / `strip()` | Xóa whitespace đầu/cuối: `" secret "` khác `"secret"`. |
| `toLowerCase()` / `toUpperCase()` | Làm mất phân biệt chữ hoa thường; quy tắc locale có thể khác nhau. |
| Bỏ dấu tiếng Việt / transliteration | Nhiều chuỗi khác nhau trở thành một; giảm số mật khẩu được phân biệt. |
| Chuyển sang ISO-8859-1 rồi tự thay ký tự ngoài bảng mã | Có thể làm mất thông tin, gây va chạm password sau chuyển đổi. |
| Cắt sau N byte hoặc N `char` | Có thể bỏ phần cuối bí mật hoặc làm hỏng surrogate pair. |
| Thêm BOM, newline, byte NUL kết thúc | Byte input KDF thay đổi; có thể tương tác sai với thư viện legacy. |
| Decode URL/Base64/JSON escape hai lần | Kết quả không còn là chuỗi người dùng gửi theo hợp đồng. |
| Normalize ở client NFKC nhưng server NFC | Dữ liệu qua nhiều đường truy cập khác nhau có thể tạo verifier khác nhau. |

**Một người có quyền dùng khoảng trắng đầu/cuối hoặc chữ viết hoa trong password phải được phân biệt đúng**, trừ khi giao thức được công bố rõ khác đi. Không sử dụng `String.toLowerCase(Locale.ROOT)` hay accent stripping để “ổn định” mật khẩu. Case folding có thể hữu ích với **username**, nhưng là thiết kế khác với password.

Nếu cần **từ chối** một lớp ký tự (ví dụ các control character không được ứng dụng hỗ trợ), công bố/kiểm tra chính sách lúc tạo mật khẩu và áp dụng nhất quán ở login. Từ chối rõ ràng tốt hơn âm thầm đổi dữ liệu. Tránh các quy tắc thành phần bắt buộc không cần thiết làm người dùng khó tạo password mạnh.

## 6. Độ dài phải đo đúng đơn vị

Một emoji khóa `🔒` có:

~~~text
Java String.length()                = 2 UTF-16 code units
String.codePointCount(0, length())  = 1 Unicode code point
UTF-8 encoded length                = 4 bytes
~~~

| Đơn vị | Ý nghĩa | Trong Java |
| --- | --- | --- |
| **UTF-16 code unit** | Đơn vị `char` 16 bit; ký tự ngoài BMP thường dùng một cặp surrogate | `String.length()` |
| **Unicode code point** | Giá trị U+0000...U+10FFFF đại diện một ký tự mã hóa; có thể nhiều code point cho một cụm hiển thị | `String.codePointCount(0, s.length())` với Unicode hợp lệ |
| **Grapheme cluster** | Cụm ký tự hiển thị người dùng thường cảm nhận là một đơn vị | Phân đoạn grapheme theo Unicode; không mặc nhiên bằng code point |
| **UTF-8 byte** | Số byte thực sẽ đưa cho KDF/giao thức có byte interface | `s.getBytes(StandardCharsets.UTF_8).length` khi chuỗi hợp lệ |

Theo NIST, **độ dài password có Unicode được tính theo Unicode code point**, còn giới hạn của thuật toán có thể theo **byte**; hai quy tắc đều cần được xem xét. Hệ thống phải có trần tài nguyên hợp lý để chống quá tải, nhưng **không được âm thầm truncate** password rồi chỉ hash phần đầu. Ở bước tạo/đổi password, báo lỗi chính sách rõ; ở đăng nhập, xử lý nhất quán với account verifier đã lưu.

**bcrypt legacy:** nhiều triển khai bcrypt truyền thống chỉ xử lý **72 byte đầu**, trong khi một số thư viện mới từ chối đầu vào dài hơn thay vì cắt. 72 byte **không phải 72 code point**. Hệ thống cũ còn có thể xử lý byte NUL đặc biệt. Khi di chuyển, phải kiểm thử đúng version/variant/provider và quy tắc xử lý vượt giới hạn; không tự tiếp tục cắt 72 byte trong profile mới.

## 7. Định nghĩa password profile xuyên hệ thống

Để Java, Python, JavaScript và dịch vụ legacy xác minh cùng password, hãy định nghĩa **byte-level contract**. Ví dụ một profile **mới**:

~~~text
profile_id: pwd-utf8-nfc-v1

Transport:
  - Request được giải mã theo protocol xác định, UTF-8 strict.
  - Reject malformed Unicode/UTF-8; không thay bằng U+FFFD.
Input policy:
  - Chấp nhận Unicode hợp lệ, áp dụng giới hạn độ dài đã định nghĩa.
  - Không trim/casefold/loại dấu, không tự decode lớp escape lần hai.
Transform:
  - Unicode normalization = NFC.
  - Encode UTF-8 strict, không BOM, không NUL terminator.
KDF:
  - Algorithm + implementation/variant + salt bytes + work factor
    + output length + pepper policy (nếu có) đều được quy định.
Stored verifier:
  - algorithm, profile_id, cost params, salt, derived verifier;
    không lưu plaintext hoặc normalized password.
~~~

Pipeline phải có **thứ tự rõ ràng**:

1. Decode input theo giao thức bằng decoder strict; reject malformed sequences.
2. Kiểm tra chính sách đầu vào như giới hạn tài nguyên và tập ký tự cho phép.
3. Normalize đúng form đã ghi trong profile; không dùng form khác khi login.
4. Encode thành byte UTF-8 strict; xác minh độ dài/limit theo thuật toán.
5. Thực hiện password KDF với chính xác salt và các tham số tương ứng account.
6. Gọi bộ xác minh của thư viện chuẩn; trả kết quả, không ghi lại mật khẩu.

**Số lần normalize**: dùng cùng một phép chuẩn hóa cố định là idempotent (áp dụng lại không đổi kết quả), nhưng các tầng không được sử dụng những phép biến đổi **khác nhau** ngoài contract. Chính sách mới cần rõ ai làm việc này: UI có thể hỗ trợ người dùng, nhưng **verifier phía server** phải là nơi quyết định và thực hiện chính sách mật mã.

### 7.1. KDF cũng có hợp đồng dữ liệu riêng

Với PBKDF2, cả hai bên phải thống nhất:

- PRF chính xác: `HmacSHA256` khác `HmacSHA1` hoặc `HmacSHA512`.
- **Password bytes** hoặc cách provider chuyển `char[]` thành password material.
- **Salt bytes**, cách giải mã salt hex/Base64 nếu lưu dưới dạng text; salt phải được sinh ngẫu nhiên riêng khi tạo verifier thật.
- **Iteration count**, length key và đơn vị: `PBEKeySpec(..., keyLength)` nhận **bit**, nhiều thư viện khác dùng byte.
- Provider/version và định dạng kết quả; xác minh qua **golden vectors Unicode**.
- Nếu có pepper: secret ngoài DB, pepper version/key ID, vị trí áp dụng trước/sau KDF theo thiết kế được thẩm định.

Với Argon2id, hợp đồng còn gồm memory cost, time cost, parallelism, version và length; với bcrypt cần variant `$2a$`/`$2b$`/`$2y$`, cost, giới hạn đầu vào, preprocessing. Không có lý do để tự viết lại KDF nhằm đạt tương thích: sử dụng thư viện chuẩn và kiểm byte chính xác.

### 7.2. Ranh giới `PBEKeySpec` của Java

`javax.crypto.spec.PBEKeySpec` nhận password bằng `char[]`, **không nhận byte UTF-8 trực tiếp**. Tài liệu JCA nêu các cơ chế password-based encryption khác nhau có thể sử dụng các phần bit khác nhau của từng `char`; vì vậy **không thể suy từ tên thuật toán rằng tất cả Java providers và thư viện Python/Node chuyển Unicode thành byte như nhau**. Với `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`, phải biết triển khai thực tế và xác minh các vector trên Unicode.

Java `PBEKeySpec` clone mảng password đầu vào; gọi `clearPassword()` xóa bản sao nội bộ, còn chủ sở hữu `char[]` phải xóa mảng của mình. `String` immutable và normalization thường tạo String trung gian nên việc xóa hết mọi bản sao memory không hoàn toàn bảo đảm. Hạn chế thời gian sống của secret, không in log, không cố giữ mật khẩu dưới dạng String lâu dài trong production.

## 8. Ví dụ Java 17+ chạy được

Chương trình dùng **các mẫu vô hại**, không dùng password người thật. Nó minh họa byte UTF-8, normalization, số code unit/code point, kiểm charset lỗi và kết quả PBKDF2 trong **cùng một provider Java**. Phần PBKDF2 đặt **10.000 iteration CHỈ để demo nhanh**; cấu hình production phải theo tiêu chuẩn và benchmark hiện hành với salt mới cho mỗi password.

Lưu tên file chính xác **`PasswordInteropDemo.java`**:

~~~java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordInteropDemo {
    static byte[] strictUtf8(char[] chars) throws CharacterCodingException {
        ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(chars));
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    // Tham số chỉ cho demo; không dùng 10.000 rounds trong production.
    static byte[] deriveNfc(String password, byte[] salt) throws Exception {
        char[] normalized = Normalizer
                .normalize(password, Normalizer.Form.NFC).toCharArray();
        PBEKeySpec spec = new PBEKeySpec(normalized, salt, 10_000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
            Arrays.fill(normalized, '\0');
        }
    }

    public static void main(String[] args) throws Exception {
        String composed = "\u00E9";
        String decomposed = "e\u0301";
        System.out.println("Raw equal           = " +
                composed.equals(decomposed));
        System.out.println("UTF-8 composed      = " +
                HexFormat.of().formatHex(
                        strictUtf8(composed.toCharArray())));
        System.out.println("UTF-8 decomposed    = " +
                HexFormat.of().formatHex(
                        strictUtf8(decomposed.toCharArray())));
        System.out.println("NFC equal           = " +
                Normalizer.normalize(composed, Normalizer.Form.NFC).equals(
                Normalizer.normalize(decomposed, Normalizer.Form.NFC)));

        String wideA = "\uFF21";
        System.out.println("NFC fullwidth == A  = " +
                Normalizer.normalize(wideA, Normalizer.Form.NFC)
                        .equals("A"));
        System.out.println("NFKC fullwidth == A = " +
                Normalizer.normalize(wideA, Normalizer.Form.NFKC)
                        .equals("A"));

        String emoji = "\uD83D\uDD12";
        System.out.println("Emoji UTF-16 units  = " + emoji.length());
        System.out.println("Emoji code points   = " +
                emoji.codePointCount(0, emoji.length()));
        System.out.println("Emoji UTF-8 bytes   = " +
                strictUtf8(emoji.toCharArray()).length);
        System.out.println("Spaces preserved    = " +
                !" pass ".equals("pass"));

        byte[] salt = HexFormat.of().parseHex(
                "000102030405060708090a0b0c0d0e0f");
        byte[] keyA = deriveNfc(composed, salt);
        byte[] keyB = deriveNfc(decomposed, salt);
        try {
            System.out.println("NFC PBKDF2 equal    = " +
                    MessageDigest.isEqual(keyA, keyB));
        } finally {
            Arrays.fill(keyA, (byte) 0);
            Arrays.fill(keyB, (byte) 0);
        }

        boolean malformedRejected = false;
        try {
            strictUtf8(new char[] { (char) 0xD800 });
        } catch (CharacterCodingException expected) {
            malformedRejected = true;
        }
        System.out.println("Unpaired rejected   = " +
                malformedRejected);
    }
}
~~~

Biên dịch và chạy với JDK 17 hoặc mới hơn:

~~~bash
javac PasswordInteropDemo.java
java PasswordInteropDemo
~~~

Stdout chính xác:

~~~text
Raw equal           = false
UTF-8 composed      = c3a9
UTF-8 decomposed    = 65cc81
NFC equal           = true
NFC fullwidth == A  = false
NFKC fullwidth == A = true
Emoji UTF-16 units  = 2
Emoji code points   = 1
Emoji UTF-8 bytes   = 4
Spaces preserved    = true
NFC PBKDF2 equal    = true
Unpaired rejected   = true
~~~

**Đọc kết quả:**

1. Hai cách biểu diễn `é` khác byte trước NFC; sau NFC chúng cho cùng chuỗi.
2. NFKC làm fullwidth `Ａ` thành ASCII A, NFC giữ khác biệt.
3. `String.length()` không phải số Unicode code point hoặc UTF-8 byte; emoji cần 2 code units và 4 byte UTF-8.
4. Hàm encoder strict từ chối unpaired surrogate; các luồng nhận UTF-8 từ mạng cũng cần **decoder strict** trước khi tạo String.
5. PBKDF2 bằng nhau vì đầu vào NFC, salt, iteration, keyLength và provider giống nhau. **Chưa chứng minh Java/Python/Node interop**; phải chạy test vector thật trên mọi implementation.
6. `10_000` iterations và **salt cố định** chỉ phục vụ kiểm thử bài học. Với password thật, tạo **salt mới, đủ dài và ngẫu nhiên bằng SecureRandom cho mỗi password**, chọn password KDF/work factor phù hợp; không in derived key trong log.

Trong demo, `Normalizer.normalize` sinh String trung gian không thể xóa trực tiếp. Các mảng `char[]` do chương trình quản lý được xóa và `PBEKeySpec.clearPassword()` xóa bản sao nội bộ; đây là giảm rủi ro, **không bảo đảm dọn mọi bản sao bí mật khỏi RAM**.

## 9. So sánh Java, Python và JavaScript

| Nền tảng | Biểu diễn nội bộ | Điểm cần kiểm |
| --- | --- | --- |
| **Java** | `String`/`char[]` chứa UTF-16 code units; `PBEKeySpec` nhận char[] | Charset mặc định, `Normalizer`, strict encoder, provider char→byte. |
| **Python 3** | `str` Unicode; `unicodedata.normalize`; `encode` | Cùng form Unicode, UTF-8 strict và KDF nhận đúng raw bytes. |
| **Node.js** | JS String dùng UTF-16; `String.normalize`; `Buffer.from(..., "utf8")` | Đếm `length` là code units, unpaired surrogate cần xử lý và library hash có byte rules riêng. |
| **Legacy** | Có thể dùng ISO-8859-1, Windows-1252, UTF-16LE hay byte array NUL-terminated | Xác định chính xác bytes trước hash, giới hạn độ dài/loại bỏ ký tự và cách normalize cũ. |

Đoạn chuyển Unicode thành UTF-8 theo profile NFC bên Python:

~~~python
import unicodedata

# password đã được kiểm tra tính hợp lệ theo giao thức.
password_bytes = unicodedata.normalize("NFC", password).encode(
    "utf-8", errors="strict"
)
~~~

Đoạn tương ứng bên Node.js:

~~~javascript
// Phải validate Unicode, đặc biệt unpaired surrogate, trước bước này.
const passwordBytes = Buffer.from(password.normalize("NFC"), "utf8");
~~~

Đó chỉ là **bước tạo byte**, không phải phương thức hash password hoàn chỉnh. Một số API JS sẽ thay surrogate lỗi khi tạo buffer; vì vậy phải có bước kiểm input strict trước đó. Khi Java dùng `PBEKeySpec` nhưng Python nhận raw bytes, hãy so **golden vectors chứa Unicode** để xác nhận provider Java thực sự dẫn xuất từ cùng dãy byte theo hợp đồng; đừng dựa vào tên `PBKDF2WithHmacSHA256` là đủ.

**Bẫy khác:** Bên A đưa byte của password vào KDF, bên B lại đưa **chuỗi hex hoặc Base64 của các byte**. Hex/Base64 là **biểu diễn**, không có lý do để dùng thêm một lớp encode trước KDF trừ khi profile lịch sử quy định chính xác như vậy.

## 10. Golden vectors: kiểm tương thích bit-for-bit

Dùng mẫu thử cố định **không chứa credential thật** và ghi cả dãy byte sau preprocessing:

| Vector | Điều kiện kiểm | Kết quả sau profile NFC + UTF-8 |
| --- | --- | --- |
| ASCII `abc` | Baseline | `61 62 63` |
| `é` = U+00E9 | Precomposed | `C3 A9` |
| `e` + U+0301 | Decomposed | **Cũng** `C3 A9` |
| `Ａ` = U+FF21 | NFC không compatibility-fold | **Khác** `A` ASCII |
| `" pass "` | Không trim | Khác `"pass"` |
| `🔒` | Ngoài BMP | 1 code point, 2 code units, 4 byte |
| Latin `a` và Cyrillic `а` | Confusable | Không bị NFC gộp |
| Unpaired surrogate | Malformed UTF-16 | Reject |
| Malformed UTF-8 từ transport | Malformed input | Reject, không thay U+FFFD |
| Chuỗi vượt giới hạn policy | Không truncate | Trả lỗi theo chính sách |

Golden vectors cho **KDF** còn chứa byte salt, PRF/algorithm, iteration hoặc memory/time cost, output length, variant, pepper giả cho test (nếu có) và **expected derived bytes**. Mỗi implementation phải trả **cùng byte** đối với cùng vector. Đừng chỉ dùng ASCII: UTF-8 và nhiều bộ mã cũ giống nhau trên ASCII, che giấu lỗi với password tiếng Việt.

Quy trình kiểm:

~~~text
1. Parse input theo protocol, reject malformed.
2. Validate Unicode và policy.
3. Normalize đúng profile.
4. Encode UTF-8 strict, so expected passwordBytes.
5. Gọi KDF với fixed test salt + params.
6. So derived result/verify với expected value.
7. Chạy negative tests: khác charset, khác normalization,
   whitespace, emoji, malformed input, key length, salt, cost.
~~~

Chạy lại vector khi nâng cấp JVM, JCE provider, Unicode tables, thư viện password hash hoặc thay đổi gateway/API. Một phép round-trip `decode(encode(s)) == s` **chưa đủ** để chứng minh hai hệ thống chọn cùng charset hoặc cùng KDF policy, và chưa phát hiện lỗi decoder thay ký tự đầu vào.

## 11. Lưu hash với profile để tương thích dài hạn

Một verifier lưu trữ nên có đủ metadata để kiểm bằng **đúng phiên bản đã tạo**:

~~~text
account_id
password_profile_id = pwd-utf8-nfc-v1
password_algorithm  = Argon2id / PBKDF2-HMAC-SHA256 / bcrypt...
salt                 = binary or documented Base64 form
cost_parameters      = iterations / memory / time / parallelism
hash_or_derived_key  = binary or documented encoded form
algorithm_variant    = ...
pepper_key_id        = ... (nếu có)
~~~

Một số thư viện tự chứa thuật toán/cost/salt trong encoded hash, chẳng hạn PHC-format cho Argon2id hoặc modular crypt format cho bcrypt. Tuy vậy, **charset và normalization policy không nhất thiết nằm trong đó**; hãy lưu `password_profile_id` nếu muốn tương thích/migrate rõ ràng.

- **Salt** công khai, riêng mỗi password và ngẫu nhiên; không nên dùng salt cố định chung cho mọi người.
- **Pepper** là bí mật server giữ ngoài DB nếu lựa chọn dùng; cần quản lý key/version, không thay salt.
- **Work factor** nên được chọn theo tham chiếu bảo mật và benchmark phù hợp hệ thống; nâng cost phải xử lý migration.
- Không lưu plaintext, normalized text, password bytes hay dấu vết debug của chúng.
- Không dùng `String.hashCode()`, checksum, SHA-256 thuần hoặc Base64 thay password hashing chuyên dụng.

## 12. Migration từ hệ thống đã có password hash

**Không thể biến password hash cũ thành hash mới chỉ bằng cách hash hash cũ** để tương ứng với byte Unicode mới: KDF một chiều, không chứa thông tin gốc cần normalize/encode. Cách làm thông dụng là **xác minh theo profile cũ trước**, sau đó rehash **candidate password người dùng vừa cung cấp** theo profile mới.

~~~text
login(account, candidate):
    record = loadVerifier(account)
    profile = lookupById(record.password_profile_id)

    if !verifyWithRecordedProfile(candidate, record, profile):
        denyAccess()

    if isEligibleForUpgrade(record):
        replacement = newVerifier(candidate, newProfile, newRandomSalt)
        atomicCompareAndSwap(record, replacement)

    allowLogin()
~~~

Đây là **pseudocode** giải thích luồng, không phải Java độc lập. Cần lưu ý:

1. **Chọn đúng một profile theo metadata account** để verify. Không thử hàng loạt encoding/normalization tùy tiện vì có thể **mở rộng tập mật khẩu được chấp nhận** và làm phức tạp audit/timing.
2. Với legacy chưa có metadata, phải dựa vào bằng chứng cấu hình/thời điểm tạo/tenant hoặc có lộ trình **đặt lại mật khẩu**. Không thể đoán byte password gốc chỉ từ hash.
3. Sau khi xác thực thành công, rehash bằng profile mới và **salt mới**; cập nhật atomically để không ghi đè lần đổi password đồng thời.
4. Nếu từ “raw bytes” sang NFC, một số cách gõ trước đây từng là **mật khẩu khác** nay có thể được coi là cùng password. Cần đánh giá tác động với người dùng và quy tắc xác thực.
5. Với bcrypt cũ có giới hạn 72 byte hoặc xử lý NUL, xác minh chính xác theo thư viện cũ và cân nhắc **yêu cầu đổi password** trước khi chuyển, tránh giữ quy tắc truncate không an toàn.
6. Không chuyển profile ID của DB khi chưa tạo verifier mới, không lưu plaintext để “migrate hàng loạt”.

Một ví dụ cụ thể: hệ cũ dùng Windows-1252 rồi PBKDF2; Java mới dùng NFC + UTF-8. Password ASCII có thể vẫn chạy. Password có tiếng Việt hoặc dấu tổ hợp có thể thất bại vì Windows-1252 không biểu diễn được. Cách giải quyết không phải **decode hash cũ sang UTF-8**, mà là kiểm đúng account bằng quy tắc cũ đã biết rồi thay verifier sau khi người dùng nhập đúng password, hoặc reset nếu hành vi cũ không thể tái hiện an toàn.

## 13. Lỗi vận hành và an toàn thường gặp

| Lỗi | Hậu quả | Cách tránh |
| --- | --- | --- |
| Dùng `password.getBytes()` với charset mặc định | Kết quả phụ thuộc runtime, đặc biệt hệ cũ | UTF-8 tường minh và test vector. |
| Đếm `String.length()` như số ký tự hiển thị | Sai giới hạn, emoji bị tính hai | Tách code unit, code point, byte. |
| `trim` trước hash | Hai password khác nhau bị gộp | Giữ nguyên whitespace trừ policy có chủ ý. |
| NFKC bất ngờ sau nâng cấp | Compatibility characters bị gộp với ASCII | Dùng profile/version và migration. |
| Encoder/decoder thay malformed input | Mất byte gốc, hai chuỗi khác thành giống | `CodingErrorAction.REPORT` và reject. |
| Nhầm hex/Base64 salt text với raw salt bytes | PBKDF2 luôn lệch | Decode salt đúng theo stored format. |
| Nhầm length 256 bit và 256 byte | Derived key mismatch | Quy định đơn vị tham số rõ ràng. |
| Băm JSON text chứa password thay vì password value | Byte khác hệ thống kia | Decode đúng JSON, xác định trường value. |
| Test chỉ `"password123"` | ASCII che giấu charset mismatch | Golden vectors chứa Unicode, emoji, combining mark. |
| Thử mọi legacy charset trên login | Chấp nhận thêm password ngoài dự kiến, khó kiểm toán | Profile định danh theo record và reset nếu không xác định. |
| Rehash hash cũ sang profile mới | Không thể tạo verifier của password input mới | Verify candidate rồi rehash, hoặc reset. |
| Log normalized password/derived key để debug | Rò rỉ credential | Chỉ log mã lỗi, metric không nhạy cảm. |
| Nhầm salt và pepper | Quản lý key/salt sai | Salt công khai riêng mỗi password; pepper bí mật tách khỏi DB. |
| Dùng mã SHA-256 thô để lưu password | Băm quá nhanh cho tấn công đoán offline | Dùng password hashing/KDF phù hợp và cost đủ mạnh. |

**Ghi nhớ lỗi phổ biến trên HTTP:** Một password đi qua browser, reverse proxy, framework request parser, authentication middleware, thư viện hash rồi DB. Nếu **mỗi tầng tự ý áp charset/normalize**, khó tìm nguyên nhân. Hãy đưa profile tập trung vào verifier và kiểm thử end-to-end cả JSON, form encoding, request length, byte payload, KDF và DB metadata. Không ghi mật khẩu vào trace khi điều tra.

## 14. Câu hỏi tự kiểm tra và đáp án

**Câu 1:** `é` (U+00E9) và `e` + U+0301 có chắc cho cùng KDF key không?

**Đáp án:** Chỉ khi **cùng profile** (chẳng hạn NFC trước UTF-8), cùng salt/cost/algorithm/provider và cùng input bytes theo định nghĩa KDF. Nếu băm raw UTF-8 bytes thì hai dạng khác nhau.

**Câu 2:** `Ａ` và `A` có như nhau theo NFC không? Theo NFKC thì sao?

**Đáp án:** NFC giữ khác; NFKC compatibility-fold có thể đưa `Ａ` về ASCII `A`, nên hai mật khẩu trở thành tương đương theo policy NFKC.

**Câu 3:** Emoji `🔒` có `String.length()==2`. Vậy nó dài 2 byte?

**Đáp án:** Không. Đó là 2 UTF-16 code units, **1 code point** và **4 UTF-8 byte**. Với ký tự tổ hợp, số code point cũng chưa chắc bằng số grapheme cluster hiển thị.

**Câu 4:** Có nên `strip()` password cho người dùng dễ nhập?

**Đáp án:** Không tự ý. Khoảng trắng có thể là thành phần password, và strip sẽ âm thầm gộp nhiều mật khẩu thành một.

**Câu 5:** Java và Python đều dùng PBKDF2-HMAC-SHA256 nhưng hash tiếng Việt khác nhau, nên điều tra gì?

**Đáp án:** Kiểm Unicode code points, normalization, UTF-8/legacy charset, provider char→byte, salt bytes, iteration, derived-key length bit/byte, pepper, version/variant và test vector cụ thể.

**Câu 6:** Có thể chuyển tất cả hash cũ từ Latin-1 sang UTF-8/NFC mà không biết password?

**Đáp án:** Không. Verifier là hàm một chiều. Cần verify theo profile cũ bằng candidate hợp lệ rồi tạo verifier mới, hoặc reset password khi cần.

**Câu 7:** bcrypt cũ nhận tối đa 72 byte, mật khẩu có 60 ký tự tiếng Việt chắc chắn nằm dưới 72 byte?

**Đáp án:** Không. UTF-8 có thể dùng nhiều byte cho mỗi ký tự; phải đo **byte** và xác định hành vi thật của bcrypt implementation. Không âm thầm cắt.

**Câu 8:** Vì sao encoder strict từ chối unpaired surrogate hữu ích cho tương thích bảo mật?

**Đáp án:** Nếu một bên thay surrogate lỗi bằng `?`/U+FFFD còn bên kia từ chối hoặc thay khác, dữ liệu mật khẩu bị mất thông tin và tạo byte khác. Reject rõ ràng giúp thống nhất hành vi.

**Câu 9:** Golden vector chứa ASCII và đúng 1 salt có đủ chứng minh Java/Node/legacy tương thích?

**Đáp án:** Không. Cần vector Unicode (composed/decomposed, fullwidth, emoji, spaces), malformed input, limits, salt/iterations/params và expected derived bytes, chạy cùng bộ test trên mọi runtime.

## 15. Checklist trước khi đưa vào production

- [ ] Có **profile ID/version** cho cách nhận Unicode, normalize, encode và KDF.
- [ ] HTTP/JSON/transport decode strict, không thay malformed UTF-8/UTF-16 bằng ký tự khác.
- [ ] NFC/NFD/NFKC/none được chọn rõ; không trim, casefold, loại dấu ngoài contract.
- [ ] UTF-8 byte input được kiểm bằng test vector; không thêm BOM/NUL/newline ngoài giao thức.
- [ ] Chiều dài code point, UTF-16 code unit và byte được phân biệt; không truncate password.
- [ ] Xác nhận giới hạn password KDF/bcrypt và hành vi của provider/version đang dùng.
- [ ] Salt riêng ngẫu nhiên, cost/algorithm/PRF/length/variant rõ ràng, metadata đủ verify.
- [ ] Các ngôn ngữ cùng chạy **Unicode golden vectors và negative cases**.
- [ ] Legacy account được verify theo **đúng policy lúc tạo**, chỉ migrate sau đăng nhập hợp lệ hoặc reset.
- [ ] Không lưu/log plaintext, normalized password, byte password, derived key hay secrets.
- [ ] Sử dụng password hashing/KDF theo tiêu chuẩn/thư viện đáng tin và cost production đã được benchmark.

**Kết luận:** **Byte đầu vào KDF là kết quả của cả một hợp đồng chuyển đổi**, không chỉ của hàm hash. Muốn password tương thích qua Java và các hệ thống khác, phải cố định và kiểm thử toàn bộ chuỗi **transport → Unicode → normalization → UTF-8 → password KDF + salt/params**, sau đó version hóa để nâng cấp hoặc chuyển đổi mà không phá vỡ cách xác minh của tài khoản cũ.


</details>

- [Quay lại đầu trang](#back-to-top)
