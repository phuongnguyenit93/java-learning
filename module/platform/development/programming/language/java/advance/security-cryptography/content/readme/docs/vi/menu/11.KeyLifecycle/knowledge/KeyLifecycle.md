<a id="back-to-top"></a>

# 11. Vòng đời khóa

## Menu
- [11.1 Phân loại khóa dữ liệu, khóa bọc khóa và khóa ký theo trách nhiệm](#key-lifecycle-001)
- [11.2 Sinh khóa, cấp phát, lưu trữ, sử dụng, luân chuyển và thu hồi](#key-lifecycle-002)
- [11.3 Không đặt khóa bí mật trong mã nguồn, bản đóng gói hoặc nhật ký](#key-lifecycle-003)
- [11.4 Mã định danh khóa, phiên bản khóa và khả năng giải mã dữ liệu cũ](#key-lifecycle-004)
- [11.5 Envelope encryption và tách khóa dữ liệu khỏi khóa bảo vệ khóa](#key-lifecycle-005)
- [11.6 Quản lý IV hoặc nonce khi có nhiều luồng, nhiều tiến trình và khởi động lại](#key-lifecycle-006)
- [11.7 Khóa có thể xuất so với khóa chỉ được sử dụng thông qua provider](#key-lifecycle-007)
- [11.8 Sao lưu, phục hồi và xử lý sự cố mất hoặc lộ khóa](#key-lifecycle-008)
- [11.9 Giới hạn giữ bí mật trong byte&#91;&#93;, String, heap dump và thông báo lỗi](#key-lifecycle-009)

## <a id="key-lifecycle-001">11.1 Phân loại khóa dữ liệu, khóa bọc khóa và khóa ký theo trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

# 11.1. Phân loại khóa dữ liệu, khóa bọc khóa và khóa ký theo trách nhiệm

## Mục tiêu bài học

Trong một hệ thống Java dùng mật mã, từ “khóa” có thể chỉ nhiều loại bí mật với **trách nhiệm và hậu quả lộ lọt khác nhau**. Không nên dùng một khóa AES cho mọi việc, cũng không nên xem private key ký số là “khóa mã hóa mạnh hơn”. Thiết kế đúng cần phân chia rõ:

1. **Data Encryption Key (DEK)**: trực tiếp mã hóa/giải mã dữ liệu ứng dụng.
2. **Key Encryption Key (KEK)**, còn gọi *key-wrapping key*: bảo vệ DEK hoặc key material khác khi lưu trữ/vận chuyển.
3. **Signing key**: private key tạo chữ ký số, đi cùng public key dùng để xác minh nguồn gốc/tính toàn vẹn của thông điệp.

Sau bài này, bạn có thể nhận diện đúng vai trò của từng khóa, gắn thao tác JCA phù hợp, thiết kế một **envelope encryption** cơ bản, kiểm tra dữ liệu được ký, lựa chọn nơi giữ khóa và quyền truy cập, cũng như phân biệt **rewrap KEK**, **reencrypt DEK** và **thay signing key**.

Các ví dụ dùng **Java 17+**, AES-GCM để mã hóa dữ liệu, AES Key Wrap (RFC 3394) để bọc DEK và RSA-PSS để ký một bản ghi. Ví dụ dùng khóa phần mềm sinh trong RAM để minh họa API, **không phải** hệ thống quản lý khóa production hoàn chỉnh.

## 1. Ba loại khóa giải quyết ba câu hỏi khác nhau

| Vai trò | Câu hỏi nó giải quyết | Khóa và thao tác điển hình | Đối tượng được bảo vệ |
| --- | --- | --- | --- |
| **DEK** — khóa dữ liệu | “Ai có thể đọc nội dung bí mật?” | `SecretKey` AES; `Cipher.getInstance("AES/GCM/NoPadding")`, `ENCRYPT_MODE`/`DECRYPT_MODE` | Plaintext của file, bản ghi, blob hoặc field |
| **KEK** — khóa bọc khóa | “Ai có thể khôi phục/sử dụng DEK đã lưu dưới dạng bọc?” | AES-KEK cùng `Cipher.getInstance("AESWrap")`, `WRAP_MODE`/`UNWRAP_MODE`; hoặc cơ chế wrapping/transport khác đã phê duyệt | **Key material** của DEK hoặc khóa con |
| **Signing key** — khóa ký | “Thông điệp/bản ghi có được ký bởi chủ thể kiểm soát private key đã được tin cậy không?” | RSA/EC/EdDSA private key qua `Signature`; public key tương ứng dùng `verify` | Tính toàn vẹn và xác thực nguồn ký của thông điệp/metadata đã ký |

**Các phát biểu dễ nhầm:**

- DEK **mã hóa dữ liệu**, KEK **mã hóa/bọc khóa**. Dù cả hai có thể là khóa AES 128/256 bit, vai trò, phạm vi truy cập, thời hạn và cách xoay vòng **khác nhau**. “Cùng thuật toán AES” không làm chúng có thể hoán đổi trách nhiệm.
- **Private signing key** là bí mật; **public verification key** có thể được phân phối cho bên xác minh, nhưng **cách gắn nó với danh tính đáng tin** vẫn phải được kiểm chứng. Public key không giúp giải mã DEK.
- Một chữ ký số **không giữ bí mật** nội dung. Mã hóa xác thực AES-GCM bảo đảm ciphertext không thể bị sửa mà không bị phát hiện (khi key/nonce/AAD được dùng đúng), nhưng **không tự chứng minh danh tính tổ chức gửi** cho người nhận cùng nắm DEK. Hai dịch vụ bảo vệ khác nhau.
- **HMAC key** là khóa đối xứng xác thực dữ liệu qua `javax.crypto.Mac`. HMAC có tác dụng xác thực giữa các bên cùng giữ bí mật nhưng **không phải** private signing key dùng với `Signature` và public-key verification. Không tự gọi khóa HMAC là “khóa ký RSA” hoặc xem HMAC có tính chất xác minh công khai.
- **Key Derivation Key (KDK)/master key** có thể là loại thứ tư, dùng để dẫn xuất key qua KDF. Không mặc nhiên xem một master key đồng thời là DEK, KEK và signing key; phải thiết kế domain separation đúng theo primitive/protocol.

NIST SP 800-57 Part 1 phân loại các loại khóa theo **cryptographic function**, cùng các khía cạnh vòng đời và bảo vệ riêng. Một nguyên tắc quan trọng là **giới hạn khóa vào mục đích đã được cấp**, giúp thu hẹp thiệt hại khi khóa bị lộ và đơn giản hóa audit, thu hồi, phân quyền.

## 2. Hiểu DEK (Data Encryption Key) cho đúng

**Nhiệm vụ duy nhất theo chính sách:** mã hóa và giải mã dữ liệu ở một ranh giới cụ thể (tài liệu, đối tượng lưu trữ, tenant hoặc phiên; tùy kiến trúc).

Ví dụ một hệ thống lưu trữ tài liệu:

- Tạo một DEK ngẫu nhiên AES-256 hoặc AES-128 cho **một tài liệu**.
- Tạo nonce/IV thích hợp cho mỗi lần mã hóa bằng AES-GCM; một cấu hình thông dụng là **nonce 12 byte**, tag 128 bit.
- Mã hóa plaintext: `Cipher.getInstance("AES/GCM/NoPadding")`, `init(ENCRYPT_MODE, dek, new GCMParameterSpec(128, nonce))`, tùy chọn `updateAAD`, rồi `doFinal(plaintext)`.
- Chỉ giữ ciphertext, nonce và metadata cần thiết cùng **DEK đã được bọc**; không lưu plaintext DEK bên cạnh ciphertext dưới dạng không bảo vệ.
- Khi đọc, chỉ thành phần có quyền truy cập dữ liệu và quyền giải bọc phù hợp mới được lấy DEK rồi decrypt.

**Phạm vi DEK là lựa chọn bảo mật:** một DEK cho mỗi object giúp tách ảnh hưởng nếu một DEK bị lộ; một DEK cho cả tenant có thể giảm số key nhưng mở rộng phạm vi thiệt hại. Quyết định cần dựa vào kích thước dữ liệu, lưu lượng, quy tắc phân quyền, hiệu năng và quy trình reencrypt.

**AES-GCM lưu ý:** tuyệt đối tránh trùng **(DEK, nonce)** cho hai lần mã hóa khác nhau; tạo DEK ngẫu nhiên mới cho object giúp giảm nguy cơ nhưng **không loại bỏ yêu cầu nonce unique** nếu cùng DEK dùng nhiều lần. Truyền/lưu nonce cùng ciphertext là bình thường vì nonce không phải key; bảo vệ tính toàn vẹn và binding theo protocol. Nếu sai DEK/nonce/AAD/tag, decryption phải thất bại và ứng dụng không sử dụng plaintext trước khi `doFinal` xác thực thành công.

DEK thường cần có mặt ở nơi **thực sự thực hiện phép mã hóa/giải mã**. Nếu chạy phần mềm trong app, có thể có bản DEK plaintext trong RAM ngắn hạn, nên hạn chế log, heap dump, telemetry, truyền qua service và thời gian sống. HSM/KMS có thể thực hiện cryptographic operation trực tiếp hoặc cấp plaintext DEK cho client tùy API; đừng giả định cứ “sử dụng KMS” là DEK không bao giờ rời KMS.

## 3. Hiểu KEK (Key Encryption Key) cho đúng

**Nhiệm vụ:** bảo vệ khóa khác, ví dụ DEK, khi khóa này phải lưu trong database, object metadata hoặc chuyển qua một biên tin cậy. KEK thường được quản lý tập trung hơn DEK và có thể bọc **rất nhiều DEK**.

Một hệ thống có thể dùng:

- **KEK đối xứng** (AES) và chuẩn **AES Key Wrap** theo RFC 3394/NIST SP 800-38F, hoặc AES Key Wrap with Padding theo RFC 5649.
- **Public key wrapping/transport** cho trường hợp nhận dữ liệu bằng khóa công khai; cơ chế cụ thể (ví dụ RSA-OAEP) có format và ràng buộc riêng. Không dùng raw RSA hoặc RSA signing key chung cho “ký và bọc khóa”.
- **KMS/HSM wrapping key** có usage policy `wrap/unwrap`, key ID, version và audit, trong đó ứng dụng không có plaintext KEK khi provider/HSM hỗ trợ API này.

Trong Java 17, `Cipher.getInstance("AESWrap")` tương ứng AES-KW (RFC 3394), được tài liệu Java Standard Algorithm Names liệt kê. `Cipher.getInstance("AESWrapPad")` tương ứng AES-KWP (RFC 5649). Một môi trường/provider cụ thể có thể không triển khai tất cả tên; luôn kiểm tra hỗ trợ và test interoperability.

Sơ đồ wrapping đối xứng:

```text
DEK (secret key bytes / key handle)
      |
      | Cipher AESWrap + KEK (bí mật hơn / cấp quản lý)
      v
wrapped DEK (ciphertext dạng key blob)  ---> có thể lưu cùng ciphertext dữ liệu
      |
      | chỉ actor được cấp phép mới được UNWRAP qua KEK
      v
DEK khôi phục -> sử dụng với thuật toán mã hóa dữ liệu được cho phép
```

**Tính chất quan trọng:**

- **Wrapped DEK không phải mật khẩu**, không phải ciphertext dữ liệu và không thể đem làm DEK trực tiếp. Nó là blob bảo vệ **key material**.
- KEK được bọc bằng một tầng KEK khác (hierarchy) hoặc root key của KMS/HSM là mẫu thường gặp, nhưng **cần tránh chu trình phụ thuộc** khiến không có khóa gốc khả dụng để phục hồi.
- AES-KW chuẩn có **kiểm tra tính toàn vẹn** của key data khi unwrap; nó không tự gắn đầy đủ thông tin *tenant, object, algorithm, usage policy, key ID* trừ khi protocol thiết kế cách binding. Không thay thế xác thực metadata, quyền truy cập, logging hoặc trust model.
- RFC 3394 AES-KW yêu cầu đầu vào key data là ít nhất hai block 64 bit và là bội số 64 bit. AES-128 DEK dài 16 byte thỏa mãn, thường sinh wrapped blob 24 byte. Khi cần wrap dữ liệu key kích thước bất kỳ, chọn primitive phù hợp như AES-KWP, không tự padding tùy tiện.
- KEK có thể **không xuất được** plaintext từ HSM/KMS. Khi đó Java `Key.getEncoded()` có thể trả `null` và code dựa trên byte export của KEK sẽ không chạy; dùng key handle/provider integration được hỗ trợ, thay vì cố trích khóa.
- Một KEK quá rộng (dùng cho mọi tenant, mọi ứng dụng) tạo blast radius lớn. Phân vùng KEK theo chính sách và key hierarchy có thể giúp giới hạn tác động, nhưng cũng tăng số metadata/permissions phải quản lý.

Không gọi `Cipher.getInstance("AES")` với defaults provider không xác định để “tự bọc” key; dùng primitive wrapping có kiểm tra tính toàn vẹn rõ ràng và được hệ thống KMS/phương thức chuẩn hỗ trợ.

## 4. Hiểu signing key cho đúng

Một cặp **khóa ký bất đối xứng** gồm:

- **Private signing key:** chỉ signer đã được cấp quyền `sign` sử dụng. Nên lưu trong KeyStore/HSM/KMS hỗ trợ thuật toán và chính sách phù hợp. Nếu bị lộ, bên tấn công có thể tạo chữ ký mạo danh trong phạm vi trust của key.
- **Public verification key:** dùng `verify`, có thể phân phối rộng nhưng phải gắn với danh tính/key ID đáng tin qua certificate chain/trust store, key pinning hoặc phân phối cấu hình có kiểm soát.

Java:

```java
Signature signer = Signature.getInstance("RSASSA-PSS");
signer.initSign(privateSigningKey);
signer.setParameter(pssProfile);
signer.update(bytesToSign);
byte[] signature = signer.sign();
```

Đây là **snippet**, giả định `privateSigningKey`, `pssProfile` và `bytesToSign` đã được tạo; các import và xử lý exception cần theo application context. Một lựa chọn khác là `Signature.getInstance("Ed25519")` khi các hệ thống và chính sách liên quan hỗ trợ; không tự đổi algorithm giữa signer và verifier.

**Chọn đúng dữ liệu được ký:** nếu hệ thống cần chứng minh một phiên bản object cụ thể bao gồm cả metadata, nên định nghĩa **bản ghi canonical** chứa version, algorithm IDs, key IDs, nonce/AAD, wrapped DEK, ciphertext và ký **toàn bộ chuỗi byte canonical**. Nếu chỉ ký mỗi ciphertext nhưng bỏ qua các trường điều khiển, attacker có thể sửa metadata mà không làm đổi chữ ký. Cần quy định độ dài/encoding từng trường để tránh nhập nhằng: `"ab"+"c"` và `"a"+"bc"` cho cùng chuỗi byte nếu nối mù quáng.

**Chữ ký và AES-GCM tag khác nhau:** GCM tag xác nhận người giải mã nắm DEK nhận đúng ciphertext/AAD, nhưng không chứng minh “ai là người gửi” nếu nhiều actor cùng có DEK; chữ ký công khai gắn với private signing key và được verify bởi bên giữ public key tin cậy. Nếu dữ liệu chỉ cần bảo mật và chống sửa đổi ở kho nội bộ, AES-GCM với quyền truy cập chặt có thể đủ; không phải mọi object đều cần chữ ký. Cần quyết định theo trust boundary, không theo cảm giác “thêm thuật toán luôn tốt”.

**Signing key không nên dùng làm KEK.** RSA private key là một kiểu `PrivateKey` nên developer có thể nhầm “có RSA thì ký và giải mã đều được”. Việc dùng một cặp key cho ký và key transport khiến quyền, cryptoperiod và hậu quả compromise trộn lẫn. Dùng cặp key, thuật toán, certificate KeyUsage và chính sách riêng theo mục đích.

## 5. So sánh quyền, vòng đời và mức độ ảnh hưởng

| Khía cạnh | DEK | KEK | Signing private key |
| --- | --- | --- | --- |
| Principal sử dụng | Workload encrypt/decrypt đúng object | Dịch vụ wrapping/unwrapping được ủy quyền | Signer được ủy quyền |
| Tác vụ được phép | `encrypt`, `decrypt` payload | `wrapKey`, `unwrapKey`; có thể hạn chế theo tenant/app | `sign`; public counterpart dùng `verify` |
| Phạm vi điển hình | Object/field/segment/tenant | Nhóm DEK hoặc boundary của KMS | Danh tính ứng dụng/tổ chức và chính sách ký |
| Nơi lưu lâu dài | **Wrapped DEK**, không plaintext cạnh dữ liệu | KMS/HSM/KeyStore bảo vệ, key handle non-exportable nếu phù hợp | HSM/KMS/KeyStore bảo vệ; public key/cert có thể phân phối |
| Nếu bị lộ | Dữ liệu trong phạm vi DEK có thể bị giải mã; cần DEK mới/reencrypt | Nhiều wrapped DEK có thể bị lộ; có thể cần rekey/reencrypt hàng loạt tùy tác động | Có thể giả mạo chữ ký của chủ thể; thu hồi trust, thay key, điều tra các bản ghi đã ký |
| Khi rotation thông thường | Thay DEK rồi **reencrypt data** | Thay KEK và **rewrap DEK** (không nhất thiết reencrypt payload) | Thay signing key, cập nhật public key/certificate/trust và chính sách kiểm chứng |
| Khi cần archive/read lịch sử | Giữ đường phục hồi DEK theo retention | Giữ bản KEK cũ có giới hạn cho wrapped DEK chưa di trú | Giữ verify public key/certificate và lịch sử trust để xác minh signature cũ |

**Không nên coi bảng “rotation thông thường” là quy trình xử lý compromise.** Ví dụ nếu plaintext DEK đã bị lộ, chỉ thay KEK không thu hồi quyền đọc của kẻ đã có DEK; phải đánh giá **thay DEK và mã hóa lại payload**. Nếu KEK đã bị lộ, attacker có thể đã giải bọc các DEK trong quá khứ; rewrap bằng KEK mới **không xóa được bản DEK mà attacker đã lấy**. Cần đánh giá phạm vi dữ liệu, key versions và bước reencrypt/đáp ứng sự cố.

### Key ID, phiên bản và phạm vi

Các tên như `dekId`, `kekId`, `signingKeyId` là **metadata định tuyến**, không phải bí mật và không đại diện cho quyền truy cập. Một server không được cấp quyền unwrap chỉ vì client gửi đúng `kekId`. Mỗi thao tác phải được kiểm tra danh tính actor, tenant/object scope, key state (active/decrypt-only/retired), usage policy và audit.

Khi xoay vòng một KEK, có thể có `kek-v1` và `kek-v2` cùng tồn tại trong giai đoạn chuyển đổi; **key ID phải trỏ chính xác version** phù hợp với wrapped DEK. Khi signing key thay, verifier cũ có thể vẫn cần public key cũ để xác minh những signature hợp lệ đã tạo trước đó, theo chính sách retention và sự cố bảo mật.

## 6. Envelope encryption: ba vai trò phối hợp như thế nào?

Một sơ đồ một object:

```text
                 ┌─ DEK ngẫu nhiên ────────────────────┐
                 │                                      │
  plaintext ───> AES-GCM.encrypt(DEK, nonce, AAD)       │
                 │                   │                  │
                 v                   v                  v
         ciphertext+tag         nonce/AAD         AESWrap(KEK, DEK)
                                                      │
                                                      v
                                                  wrappedDEK
                 │                   │                  │
                 └──────────┬────────┴──────────────────┘
                            v
               canonical envelope bytes
          (version, algorithms, key IDs,
           nonce, AAD, wrappedDEK, ciphertext)
                            |
          Sign(privateSigningKey, envelope)
                            |
                            v
       Lưu: envelope + signature + signer key reference

Bên nhận:
  - kiểm tra quyền truy cập + signer public-key trust
  - verify signature trên EXACT envelope bytes theo policy
  - chọn KEK theo server-side authorization, unwrap DEK
  - AES-GCM.decrypt(DEK, nonce, AAD, ciphertext+tag)
  - chỉ công bố plaintext sau khi tag xác minh thành công
```

Các bước cụ thể:

1. **Tạo DEK** bằng CSPRNG/`KeyGenerator` phù hợp; quyết định scope của DEK (một object hay khác).
2. **Mã hóa payload** bằng AEAD như AES-GCM với nonce unique cho cặp (DEK, nonce), AAD có context ổn định cần xác thực (chẳng hạn object/tenant/version).
3. **Bọc DEK** bằng KEK có quyền wrap; lưu `wrappedDEK`, `kekId` và `wrapAlgorithm`.
4. **Tạo envelope có định dạng byte chính tắc** với version, algorithm IDs, identifiers, nonce, AAD, wrapped key và ciphertext. Không dựa vào phép nối string có ranh giới mơ hồ.
5. **Ký envelope** nếu bài toán cần xác thực nguồn ký. Bên ký cần private signing key; signature được lưu kèm signer key ID và thông tin tin cậy cần thiết.
6. **Khi đọc**, kiểm tra authorization và signature **trên đúng bytes đã ký** rồi mới giải bọc DEK theo KEK đã được server ủy quyền. Sau đó giải mã data, kiểm tra GCM tag rồi mới đưa plaintext vào hệ thống.
7. **Ghi log có chọn lọc:** actor, key IDs, operation, object reference, timestamp và kết quả. Không log plaintext, raw DEK, KEK, signing private key hoặc wrapped key blob không cần thiết.

**Lưu ý quan trọng với rewrap:** nếu bạn đưa `kekId` hoặc `wrappedDEK` vào **AAD của AES-GCM payload**, việc thay KEK/wrappedDEK sẽ đổi AAD và làm tag cũ không hợp lệ. Khi cần rewrap **mà không reencrypt data**, thiết kế AAD payload chỉ chứa metadata **bất biến qua rewrap** (ví dụ tenant, object, content version), còn các trường wrapping có thể được xác thực bằng **chữ ký/hệ thống metadata có bảo vệ khác**. Vì signature ở ví dụ bao phủ wrapping fields, sau rewrap cần **tạo signature manifest mới có ủy quyền**; đừng sửa metadata signed mà vẫn giữ signature cũ.

### Ví dụ bản ghi lưu lâu dài

```json
{
  "formatVersion": 1,
  "objectId": "doc-42",
  "contentVersion": 3,
  "dataAlgorithm": "AES/GCM/NoPadding",
  "nonce": "<base64url>",
  "aadContext": "tenant-7|doc-42|v3",
  "ciphertextAndTag": "<base64url>",
  "wrappedDek": "<base64url>",
  "kekId": "tenant-7-kek-v2",
  "wrapAlgorithm": "AESWrap",
  "signatureAlgorithm": "RSASSA-PSS",
  "signingKeyId": "signer-v4",
  "signature": "<base64url>"
}
```

Các giá trị trong dấu `<...>` là **ký hiệu ví dụ**, không phải một credential có thật. Bản ghi này chỉ trình bày **các trường**; trước khi ký phải quy định **canonical byte serialization**, quy tắc length-prefix/canonical JSON, version và thứ tự, không ký một JSON object mơ hồ rồi mong việc serialize lần sau giữ nguyên bytes. Nếu dùng nhiều môi trường, lưu rõ PSS digest/MGF1/saltLen/trailer hoặc dùng một profile được protocol xác định cứng (PS256).

Một signature được lưu trong record **không tự xác minh chính nó**: phải có public key tin cậy độc lập với input không tin cậy và không được chấp nhận tùy tiện algorithm/key ID do attacker chọn.

## 7. Ví dụ Java 17+ đầy đủ: DEK + KEK + signing key

Ví dụ sau minh họa đồng thời **ba vai trò** trong một tiến trình:

- Hai khóa AES 128 bit **được sinh độc lập**: `dek` cho AES-GCM data, `kek` cho AES Key Wrap.
- Một cặp RSA 2048 bit độc lập cho chữ ký RSASSA-PSS SHA-256, MGF1-SHA256, salt 32 byte.
- `pack(...)` dùng **length-prefix 4 byte big-endian** trên từng trường để giảm nguy cơ nhập nhằng input signature. Bản ghi đã ký bao gồm algorithm labels, key IDs, nonce, AAD, wrapped DEK và ciphertext.
- Cả khôi phục DEK, giải mã data và kiểm chứng signature đều được thực hiện. Sửa byte của bản ghi đã ký phải khiến verify trả false.

Lưu file **`KeyRolesDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class KeyRolesDemo {
    private KeyRolesDemo() {}

    private static byte[] pack(byte[]... parts) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            for (byte[] part : parts) {
                out.writeInt(part.length);
                out.write(part);
            }
        }
        return bytes.toByteArray();
    }

    private static boolean verify(PublicKey key, PSSParameterSpec spec,
            byte[] message, byte[] signature) throws Exception {
        Signature verifier = Signature.getInstance("RSASSA-PSS");
        verifier.initVerify(key);
        verifier.setParameter(spec);
        verifier.update(message);
        return verifier.verify(signature);
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator keys = KeyGenerator.getInstance("AES");
        keys.init(128);
        SecretKey dek = keys.generateKey();
        SecretKey kek = keys.generateKey();

        byte[] payload = "secret document".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "tenant=42;purpose=report".getBytes(StandardCharsets.UTF_8);
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);

        Cipher dataEncrypt = Cipher.getInstance("AES/GCM/NoPadding");
        dataEncrypt.init(Cipher.ENCRYPT_MODE, dek,
                new GCMParameterSpec(128, nonce));
        dataEncrypt.updateAAD(aad);
        byte[] sealed = dataEncrypt.doFinal(payload);

        Cipher keyWrap = Cipher.getInstance("AESWrap");
        keyWrap.init(Cipher.WRAP_MODE, kek);
        byte[] wrappedDek = keyWrap.wrap(dek);

        Cipher keyUnwrap = Cipher.getInstance("AESWrap");
        keyUnwrap.init(Cipher.UNWRAP_MODE, kek);
        SecretKey restoredDek = (SecretKey) keyUnwrap.unwrap(
                wrappedDek, "AES", Cipher.SECRET_KEY);

        Cipher dataDecrypt = Cipher.getInstance("AES/GCM/NoPadding");
        dataDecrypt.init(Cipher.DECRYPT_MODE, restoredDek,
                new GCMParameterSpec(128, nonce));
        dataDecrypt.updateAAD(aad);
        byte[] restored = dataDecrypt.doFinal(sealed);

        KeyPairGenerator signingKeys = KeyPairGenerator.getInstance("RSA");
        signingKeys.initialize(2048);
        KeyPair signingPair = signingKeys.generateKeyPair();

        PSSParameterSpec pss = new PSSParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                32, PSSParameterSpec.TRAILER_FIELD_BC);

        byte[] record = pack(
                "AES/GCM/NoPadding".getBytes(StandardCharsets.US_ASCII),
                "AESWrap".getBytes(StandardCharsets.US_ASCII),
                "kek-v1".getBytes(StandardCharsets.US_ASCII),
                "sign-v1".getBytes(StandardCharsets.US_ASCII),
                nonce, aad, wrappedDek, sealed);

        Signature signer = Signature.getInstance("RSASSA-PSS");
        signer.initSign(signingPair.getPrivate());
        signer.setParameter(pss);
        signer.update(record);
        byte[] signature = signer.sign();

        byte[] modified = record.clone();
        modified[modified.length - 1] ^= 1;

        System.out.println("dekBytes=" + dek.getEncoded().length);
        System.out.println("kekBytes=" + kek.getEncoded().length);
        System.out.println("wrappedDekBytes=" + wrappedDek.length);
        System.out.println("ciphertextBytes=" + sealed.length);
        System.out.println("keyRecovered=" +
                Arrays.equals(dek.getEncoded(), restoredDek.getEncoded()));
        System.out.println("dataRecovered=" + Arrays.equals(payload, restored));
        System.out.println("signatureBytes=" + signature.length);
        System.out.println("signatureValid=" +
                verify(signingPair.getPublic(), pss, record, signature));
        System.out.println("modifiedRecordAccepted=" +
                verify(signingPair.getPublic(), pss, modified, signature));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 KeyRolesDemo.java
java KeyRolesDemo
```

**Stdout đã kiểm tra với JDK hỗ trợ các thuật toán nêu trên:**

```text
dekBytes=16
kekBytes=16
wrappedDekBytes=24
ciphertextBytes=31
keyRecovered=true
dataRecovered=true
signatureBytes=256
signatureValid=true
modifiedRecordAccepted=false
```

Kích thước giải thích được: `"secret document"` có 15 byte UTF-8/ASCII; AES-GCM với tag 128 bit tạo **15 + 16 = 31 byte** ciphertext+tag. Wrapped AES-128 DEK có 16 byte key material, AES-KW theo RFC 3394 thêm 8 byte overhead thành **24 byte**. RSA-2048 signature dài **256 byte**. Nội dung bí mật/khóa/nonce và signature bytes **thay đổi giữa các lần chạy**, nhưng các giá trị boolean/length nêu trong stdout ổn định trong provider tương thích.

### Phân tích theo ranh giới trách nhiệm

1. `KeyGenerator` sinh **hai AES keys độc lập**. Dù thuộc cùng interface `SecretKey`, `dek` chỉ đi qua data cipher và `kek` chỉ đi qua wrapping cipher. Java object type `SecretKey` **không tự thực thi lệnh cấm sử dụng sai mục đích**; đây mới là quy ước trong code. Production cần IAM/KMS/HSM policy và phân quyền.
2. `dataEncrypt` sử dụng **DEK**, nonce 12 byte và AAD. `doFinal` sinh ciphertext+tag. Kết quả không bao gồm plaintext DEK.
3. `keyWrap` sử dụng **KEK** ở `WRAP_MODE`, input là `dek`, output là wrapped key blob. `keyUnwrap` dùng cùng KEK ở `UNWRAP_MODE` và dựng lại `SecretKey` AES. Nó không cần biết plaintext của tài liệu.
4. `dataDecrypt` sử dụng **DEK vừa được giải bọc**, cùng nonce và AAD. Chỉ sau khi GCM tag được kiểm chứng mới chấp nhận `restored`.
5. `signingPair` là cặp key RSA **không liên quan** đến DEK/KEK. Private key ký bytes của envelope; public key verify. Code không dùng RSA để wrap AES key và không dùng AES key để giả lập chữ ký số.
6. `pack` mã hóa ranh giới fields bằng tiền tố `int`. Đây là minh họa cho input byte xác định; version/header, trường tin cậy và giới hạn kích thước khi parse vẫn cần được bổ sung trong protocol production.
7. `modifiedRecordAccepted=false` chứng minh sửa byte cuối bản ghi đã ký không phù hợp với signature gốc. **Verify bằng public key được truyền từ attacker là vô nghĩa về danh tính**; demo giữ public key trong cùng tiến trình chỉ để chứng minh cách gọi API.
8. Vì khóa AES trong demo có thể xuất bằng `getEncoded()`, việc so sánh raw bytes hoạt động. Với HSM-backed nonexportable keys, `getEncoded()` có thể là `null`; hãy dùng operation qua key handle, không ép export key để làm test.

Trong code minh họa, bên giải mã đã có `kek` và private signing key chỉ nằm ở signer. Khi phân tách thành nhiều service, **không chuyển private signing key sang verifier** và **không cấp KEK plaintext cho service chỉ cần verify signature**.

## 8. Thay KEK không nhất thiết phải mã hóa lại dữ liệu

**Rewrap** là thao tác thay lớp bảo vệ của DEK. Nếu DEK còn an toàn và ciphertext không đổi, có thể unwrap DEK bằng KEK cũ, wrap lại DEK bằng KEK mới, rồi **ghi atomically** metadata `wrappedDek`/`kekId` mới.

```text
Before:
 ciphertext = AES-GCM(DEK, plaintext, nonce, AAD)
 wrappedDekV1 = AESWrap(KEK-v1, DEK)

Rewrap:
 DEK = AESUnwrap(KEK-v1, wrappedDekV1)
 wrappedDekV2 = AESWrap(KEK-v2, DEK)

After:
 ciphertext giữ nguyên
 nonce + AAD của payload giữ nguyên
 wrappedDekV2 + kekId=v2 thay thế wrappedDekV1
```

Chỉ làm vậy khi AAD của payload **không bị đổi bởi KEK metadata**; nếu metadata thay đổi nằm trong AAD, cần điều chỉnh thiết kế hoặc thực hiện quy trình reencrypt phù hợp. Nếu toàn envelope được **ký**, thao tác rewrap thay `kekId`/`wrappedDek` cũng làm bytes đã ký thay đổi: phải tạo attestation/chữ ký mới có ủy quyền, không giữ signature cũ như thể không có gì đổi.

### Ví dụ Java độc lập về rewrap

Lưu **`RewrapOnlyDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class RewrapOnlyDemo {
    private RewrapOnlyDemo() {}

    private static SecretKey newAesKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        return generator.generateKey();
    }

    private static byte[] wrap(SecretKey kek, SecretKey dek)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AESWrap");
        cipher.init(Cipher.WRAP_MODE, kek);
        return cipher.wrap(dek);
    }

    private static SecretKey unwrap(SecretKey kek, byte[] wrapped)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AESWrap");
        cipher.init(Cipher.UNWRAP_MODE, kek);
        return (SecretKey) cipher.unwrap(wrapped, "AES", Cipher.SECRET_KEY);
    }

    public static void main(String[] args) throws Exception {
        SecretKey dek = newAesKey();
        SecretKey oldKek = newAesKey();
        SecretKey newKek = newAesKey();

        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        byte[] message = "stored record".getBytes(StandardCharsets.UTF_8);

        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
        encrypt.init(Cipher.ENCRYPT_MODE, dek,
                new GCMParameterSpec(128, nonce));
        byte[] ciphertext = encrypt.doFinal(message);

        byte[] oldWrapped = wrap(oldKek, dek);
        SecretKey recoveredDek = unwrap(oldKek, oldWrapped);
        byte[] newWrapped = wrap(newKek, recoveredDek);

        SecretKey afterRotation = unwrap(newKek, newWrapped);
        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
        decrypt.init(Cipher.DECRYPT_MODE, afterRotation,
                new GCMParameterSpec(128, nonce));
        byte[] restored = decrypt.doFinal(ciphertext);

        System.out.println("oldWrappedBytes=" + oldWrapped.length);
        System.out.println("newWrappedBytes=" + newWrapped.length);
        System.out.println("sameDataKey=" +
                Arrays.equals(dek.getEncoded(), afterRotation.getEncoded()));
        System.out.println("payloadUnchanged=" +
                Arrays.equals(message, restored));
        System.out.println("ciphertextBytes=" + ciphertext.length);
    }
}
```

```bash
javac -encoding UTF-8 RewrapOnlyDemo.java
java RewrapOnlyDemo
```

Stdout:

```text
oldWrappedBytes=24
newWrappedBytes=24
sameDataKey=true
payloadUnchanged=true
ciphertextBytes=29
```

`"stored record"` có 13 byte UTF-8 và tag GCM 16 byte, vì vậy ciphertext 29 byte. `sameDataKey=true` chứng minh DEK **không đổi** sau giải bọc/bọc lại, và `payloadUnchanged=true` cho thấy **cùng ciphertext cũ** có thể được decrypt bằng DEK đã khôi phục qua KEK mới.

Ví dụ không có database, signer, transaction hoặc kiểm tra quyền; nó chỉ minh họa nguyên lý rewrap. Trong production, bước commit phải nhất quán: `newWrappedDek` và `newKekId` cần cập nhật **cùng một giao dịch/phiên bản**, kiểm chứng unwrap thành công và bảo đảm rollback không làm mất khả năng đọc. Đừng xóa KEK cũ khi vẫn còn object trỏ tới phiên bản đó.

### So sánh ba quy trình rotation

| Khi thay đổi | Việc tối thiểu cần làm | Cái gì **không** được hiểu nhầm |
| --- | --- | --- |
| **KEK rotation thông thường** | Rewrap từng DEK còn phải lưu, cập nhật key ID/metadata atomically, kiểm tra đọc lại | Không nhất thiết phải đổi DEK hoặc reencrypt payload. |
| **DEK rotation** | Sinh DEK mới, **decrypt bằng DEK cũ** và **encrypt payload bằng DEK mới với nonce phù hợp**, wrap DEK mới | Chỉ đổi wrappedDEK không đổi DEK thực tế. |
| **Signing key rotation** | Key pair mới, phân phối public verification key/cert mới, cập nhật signer key ID và trust policy | Không thể chỉ “rewrap private signing key” rồi coi signer identity/validity đã chuyển. |
| **KEK bị compromise** | Đánh giá DEK nào có thể đã bị giải bọc; có thể cần DEK mới/reencrypt, thu hồi quyền và điều tra | Rewrap sau sự cố không làm attacker quên DEK cũ. |
| **Signing key bị compromise** | Thu hồi/đình chỉ quyền ký, công bố/truyền chính sách trust mới, đánh giá các chữ ký đã phát hành | Thay key không tự biến những chữ ký giả mạo trước đó thành không thể tồn tại. |

Không áp dụng “xóa khóa cũ ngay lập tức” như một quy tắc. Mỗi key có thể cần giai đoạn **active → decrypt/unwrap/verify only → retired → destroyed** theo retention, legal hold, key compromise và backup/restore requirements. Với chữ ký lưu trữ lâu dài, phải giữ đủ public verification material và bằng chứng về trạng thái tin cậy tại thời điểm ký nếu protocol yêu cầu.

## 9. Quyền truy cập và vị trí lưu khóa

Việc gắn nhãn “DEK”, “KEK”, “signing key” trong code là chưa đủ. Cần xây **permission boundary** sao cho actor chỉ thực hiện đúng chức năng.

| Actor/service | DEK usage | KEK usage | Signing private key | Signing public key |
| --- | --- | --- | --- | --- |
| Data writer | Encrypt dữ liệu được cấp phép | Xin wrap DEK (thông qua KMS/HSM nếu áp dụng) | Chỉ sign nếu được ủy quyền riêng | Verify metadata khi cần |
| Data reader | Decrypt object có quyền đọc | Xin unwrap DEK qua policy phù hợp | Không cần | Verify envelope khi cần |
| Key management service | Điều phối generate/wrap/unwrap/rotation, không cần đọc payload | Quản trị KEK theo policy | Không mặc nhiên | Có thể quản trị trust anchors |
| Signing service | Không cần plaintext DEK | Không cần KEK | Có quyền `sign` với key thuộc phạm vi được ủy quyền | Có thể kiểm chứng |
| Public verifier | Không cần | Không cần | **Không bao giờ** | Có, nhưng phải xác thực nguồn public key |

**Các điều kiện production nên có:**

- **Policy theo key purpose:** chỉ được phép encrypt/decrypt, wrap/unwrap hoặc sign/verify đúng key. KMS/HSM/API có thể áp dụng `KeyUsage`/operations; phải kiểm tra tài liệu provider, không suy diễn từ tên object Java.
- **Key identity và version:** mỗi key có ID ổn định, key version, algorithm, size, scope/tenant, trạng thái và quy tắc sử dụng. Không dùng user-supplied key ID làm authorization.
- **Key generation:** dùng RNG an toàn và quản lý keys bằng hệ thống phù hợp; không hard-code key trong code, image/container, Git, secret logs hoặc spreadsheet.
- **Không export khi không cần:** tránh truyền KEK và signing private key ra nhiều service; cân nhắc handle trong HSM/KMS. DEK nếu phải xuất cho application thì giới hạn lifetime/caching và quyền truy cập.
- **Audit & incident response:** ghi `who/what/which key/which object/when`, kết quả unwrap/sign/rotation, nhưng không ghi raw key, plaintext hoặc private material. Giám sát bất thường (vọt số lần unwrap, signer signing ngoài giờ/tenant).
- **Backup/recovery:** backup ciphertext mà **không có khả năng khôi phục DEK/KEK** có thể khiến dữ liệu không thể giải mã. Thiết kế khôi phục key có kiểm soát, không tạo bản plaintext backup trôi nổi.
- **Separation of duties:** người quản lý key policy không đương nhiên được đọc toàn bộ data, người có quyền đọc data không mặc nhiên được ký thay tổ chức, signer không tự động được unwrap mọi tenant.

### Java types không tương đương key policy

- `javax.crypto.SecretKey` đại diện khóa bí mật, có thể dùng cho DEK hoặc KEK. Chỉ dựa vào type không bảo đảm **đúng purpose**.
- `java.security.PrivateKey`/`PublicKey` là cặp khóa bất đối xứng, nhưng RSA private key không tự xác định quyền **chỉ được ký** nếu backend không kiểm tra policy.
- `java.security.KeyStore` có cơ chế lưu/truy xuất key và certificate (tùy loại/provider), **không thay thế** toàn bộ IAM, key lifecycle, KMS audit và separation of duties.
- `Key.getEncoded()` có thể trả `null` nếu key không thể xuất/opaque. Code phải xử lý các provider HSM/PKCS#11 mà không giả định có raw key bytes.
- Metadata X.509 `KeyUsage` có thể phân biệt `digitalSignature`, `keyEncipherment`, `keyAgreement`; nó có giá trị khi xử lý chứng chỉ, nhưng enforcement phụ thuộc verifier/protocol/policy. Không chỉ nhìn thấy certificate là coi key tự động đúng mục đích.

## 10. Đe dọa và sự cố: phản ứng theo loại khóa

### Nếu DEK của một object bị lộ

Cần đánh giá **đối tượng và phiên bản dữ liệu** liên quan đã có thể bị đọc. Nếu vẫn dùng DEK đó, phải sinh DEK khác, reencrypt dữ liệu với nonce mới, wrap DEK mới, cập nhật metadata/signature tương ứng và thu hồi quyền đọc nếu cần. Không giả định việc xóa bản key khỏi database sẽ xóa những bản đã bị sao chép.

### Nếu KEK dùng cho nhiều object bị lộ

Một KEK bị lộ có thể cho phép giải bọc **nhiều DEK** từ những bản ghi đã thu thập. Thay KEK chỉ bảo vệ các wrapped DEK **từ nay về sau**; không thể đảm bảo các DEK trước đó chưa bị đối phương thu thập. Đánh giá toàn bộ các object/version từng dùng KEK, khả năng cần reencrypt với DEK mới, thời gian lưu backup, lịch sử quyền truy cập và phạm vi thông báo sự cố. Ưu tiên có **key inventory** để truy ra đối tượng bị ảnh hưởng mà không phải quét mò.

### Nếu signing private key bị lộ

Đối phương có thể tạo một chữ ký mới phù hợp với public key mà verifier đang tin cậy. Cần thu hồi/ngưng key, cập nhật key registry/cert/trust policy, kiểm soát signer mới và rà soát các artefact đã ký. **AES-GCM ciphertext vẫn giữ bí mật nếu DEK/KEK không lộ**, nhưng giả mạo metadata/chữ ký có thể làm trust boundary bị xâm phạm. Thông tin thời điểm ký, transparency log hoặc timestamp authority (nếu có) có thể quan trọng khi đánh giá chữ ký lịch sử.

### Nếu bị thay metadata/key IDs

Không cho phép chuyển `wrappedDek` từ tenant A sang tenant B chỉ bằng sửa `kekId`. Dùng authorization phía server và bảo vệ/binding context (AAD bất biến, signature hoặc MAC phù hợp). Khi phục hồi từ backup hoặc migrate dữ liệu, nhất quán giữa `kekId`, `wrappedDek`, `dataAlgorithm`, `nonce`, `AAD` và ciphertext là điều kiện cần.

**Nguyên tắc:** cùng một crypto primitive có thể xử lý nhiều loại key về mặt thuật toán, nhưng **không loại bỏ** nhu cầu phân quyền và tách biệt trách nhiệm ở hệ thống quản lý khóa.

## 11. Những lỗi thường gặp và cách sửa

| Sai lầm | Rủi ro | Cách sửa |
| --- | --- | --- |
| Dùng cùng một AES key cho cả encrypt data và wrap key | Trộn mục đích, khó giới hạn blast radius và quyền sử dụng | Sinh DEK/KEK riêng; policy/handle riêng. |
| Lưu plaintext DEK cùng ciphertext | Ai lấy được database có thể giải mã ngay | Lưu wrapped DEK dưới KEK/KMS có policy. |
| Dùng signer private key để vận chuyển/bọc DEK | Vi phạm separation of use và ràng buộc certificate/protocol | Dùng KEK/key transport key riêng. |
| Coi signature như mã hóa | Data vẫn có thể đọc bằng plaintext | Encrypt bằng DEK; sign khi cần xác thực người ký. |
| Coi GCM tag như chữ ký nguồn gửi | Các actor cùng có DEK đều có thể tạo ciphertext/tag hợp lệ | Dùng signature nếu cần identity provenance. |
| Gửi public key trong record rồi tin verify=true | Attacker tự tạo key/signature của mình | Xác thực public key theo trust store/certificate/pinning. |
| Cho client chọn `kekId` rồi unwrap không kiểm tra | Vượt tenant hoặc ACL, tạo oracle | Authorize actor/tenant/object/key usage ở phía server. |
| Quên binding nonce, wrapped DEK, algorithm IDs khi ký metadata | Metadata có thể bị thay không nằm trong signature coverage | Canonical envelope và ký đủ các trường security-critical. |
| Dùng AES-GCM mà lặp (DEK, nonce) | Nguy cơ phá bảo mật | Nonce unique cho mỗi encryption với cùng DEK. |
| Rewrap KEK mà giữ nguyên signature trên wrapped key cũ | Signature không khớp với envelope mới | Re-sign version mới có ủy quyền, giữ audit chain. |
| Đặt `kekId` thay đổi được trong AAD payload rồi cố rewrap không reencrypt | AEAD tag không hợp lệ sau khi AAD thay đổi | Thiết kế AAD ổn định hoặc reencrypt đúng quy trình. |
| Đổi KEK sau khi đã lộ DEK rồi tuyên bố an toàn | Attacker vẫn giữ DEK cũ | Đánh giá phạm vi và rotate DEK/reencrypt nếu cần. |
| Xóa KEK cũ trước khi di trú hết wrapped DEK | Mất khả năng giải mã dữ liệu lịch sử | Inventory, migration checkpoint, rollback và retention. |
| Đưa raw key/private material vào logs | Lộ secrets qua observability | Log key ID, operation, status thay vì key material. |
| Cho rằng `SecretKey` Java tự enforcing “data-only” | Object Java không phải security policy đầy đủ | Dùng IAM/KMS/HSM usage restrictions và giới hạn service. |
| Giả định mọi khóa đều `getEncoded()!=null` | Lỗi với nonexportable HSM keys | Dùng provider-bound `Key` handles và cryptographic operations hỗ trợ. |
| Dùng `AESWrap` cho dữ liệu tùy ý/mọi độ dài | RFC 3394 có điều kiện input key data và purpose cụ thể | Chọn AES-KWP hoặc AEAD thích hợp theo chuẩn. |

## 12. Bài tập áp dụng

1. **Phân loại:** hệ thống quản lý 10.000 tài liệu có một KEK trong KMS và 10.000 DEK bọc. Một signing private key dùng tạo attestation của manifest. Hãy chỉ ra actor nào nên giữ quyền `encrypt/decrypt`, `wrap/unwrap`, `sign` và `verify`.
2. **Đọc code:** trong `KeyRolesDemo`, tại sao `dekBytes=16` và `kekBytes=16` không chứng minh hai khóa có cùng trách nhiệm? Tại sao `wrappedDekBytes=24` khác `ciphertextBytes=31`?
3. **Sửa code:** đổi một byte trong `record` đã ký và quan sát `modifiedRecordAccepted=false`. Nếu lấy public key mới do attacker tự tạo, vì sao verify=true với signature attacker không xác thực người gửi thật?
4. **Thực hành rewrap:** chạy `RewrapOnlyDemo` và giải thích tại sao ciphertext/AAD/nonce không cần sửa để giải mã qua KEK mới; nếu ký toàn envelope, bước nào bắt buộc bổ sung?
5. **Thiết kế metadata:** liệt kê trường phải lưu để giải mã một object sau 3 năm khi KEK đã rotate nhiều lần. Trường nào là secret? Trường nào phải được xác thực/binding?
6. **Compromise:** giả sử chỉ KEK bị lộ và attacker đã tải bản sao mọi wrapped DEK. So sánh tác dụng của (A) rewrap cùng DEK và (B) tạo DEK mới + reencrypt từng object.
7. **Sai policy:** một service chỉ cần verify chữ ký nhưng đang được cấp `UNWRAP_MODE` với KEK. Hãy điều chỉnh IAM/KeyStore/KMS policy để thu hẹp quyền.
8. **AAD và rewrap:** vì sao thiết kế dùng `kekId` trong AES-GCM AAD sẽ làm rewrap khó hơn? Đề xuất một tập metadata bất biến để đưa vào AAD và tập metadata wrapping nằm trong signature.
9. **Multi-tenant:** một API nhận `tenantId` và `kekId` từ request. Đề xuất ba bước xác thực/ủy quyền chống việc tenant A trỏ sang KEK của tenant B.
10. **Audit và retention:** signing key cũ chỉ còn verify-only nhưng DEK cũ vẫn phải decrypt dữ liệu lưu trữ. Thiết kế trạng thái key và quy trình thu hồi khác nhau cho signing public/private pair so với KEK.
11. **Cơ chế khác:** giải thích khi nào một hệ thống chỉ dùng AES-GCM với DEK/KEK mà không cần signing key, và khi nào cần chữ ký số để xác minh nguồn phát hành được tin cậy qua nhiều bên.

**Tiêu chí đạt:** giải thích chính xác ba vai trò; mô tả được cách DEK, KEK và signing private/public key đi qua envelope; chứng minh bằng ví dụ rằng thay KEK có thể không đổi ciphertext; biết rằng KEK compromise có thể kéo theo DEK compromise; và mô tả được enforcement, metadata, nonce, trust và rotation hợp lý theo từng loại.

## Tài liệu tham khảo

- [NIST SP 800-57 Part 1 Rev. 5 — Recommendation for Key Management](https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final)
- [NIST SP 800-38F — Methods for Key Wrapping](https://csrc.nist.gov/pubs/sp/800/38/f/final)
- [RFC 3394 — Advanced Encryption Standard (AES) Key Wrap Algorithm](https://www.rfc-editor.org/rfc/rfc3394)
- [RFC 5649 — AES Key Wrap with Padding](https://www.rfc-editor.org/rfc/rfc5649)
- [Java 17 Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html)
- [Java 17 `Cipher` — `WRAP_MODE`/`UNWRAP_MODE`, `wrap`/`unwrap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html)
- [Java 17 `Signature` — `initSign`/`initVerify`/`sign`/`verify`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html)
- [Java 17 `Key` — `getEncoded` có thể trả `null`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Key.html)
- [Java 17 `KeyStore` — quản lý key và certificate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-lifecycle-002">11.2 Sinh khóa, cấp phát, lưu trữ, sử dụng, luân chuyển và thu hồi</a>

<details>
<summary>Xem chi tiết</summary>

# 11.2. Sinh khóa, cấp phát, lưu trữ, sử dụng, luân chuyển và thu hồi

## Mục tiêu học tập

Kết thúc bài, bạn có thể:

1. Mô tả **vòng đời khóa mật mã** từ hoạch định, sinh khóa, cấp phát, kích hoạt, lưu trữ, sử dụng đến luân chuyển, vô hiệu hóa, thu hồi, lưu trữ lịch sử và hủy bỏ.
2. Phân biệt **khóa dữ liệu (DEK)**, **khóa bọc khóa (KEK)**, **khóa đối xứng**, **khóa riêng/khóa công khai**, chứng thư số và bí mật truy cập; xác định đúng mục đích, chủ sở hữu và quyền của từng khóa.
3. Tạo khóa an toàn bằng Java 17+ với `KeyGenerator`, `KeyPairGenerator` và `SecureRandom`; hiểu giới hạn của `KeyStore`, vai trò của KMS/HSM và nguyên tắc không làm lộ raw key.
4. Thiết kế **key version**, **cryptoperiod**, phân quyền decrypt-only đối với khóa cũ, rotation định kỳ và **emergency rotation** khi nghi ngờ lộ khóa.
5. Giải thích vì sao **disable/revoke**, **delete/destroy**, **re-encrypt**, **rewrap** và **certificate revocation** khác nhau; tránh khiến dữ liệu cũ không thể giải mã hoặc tin nhầm chữ ký của khóa đã bị lộ.
6. Chạy ví dụ Java 17+ mô phỏng sinh hai phiên bản AES key, lưu/đọc lại `PKCS12 KeyStore`, mã hóa AES-GCM có key-version, đọc ciphertext cũ sau rotation và từ chối khi phiên bản key bị retire.

**Phạm vi:** Bài thuộc **Java Advance – security-cryptography / 11. Vòng đời khóa**. Trọng tâm là kiểm soát *toàn vòng đời và các ranh giới tin cậy*, không phải chỉ chọn một thuật toán AES/RSA hoặc một đường dẫn keystore.

---

## 1. Vì sao quản lý khóa quan trọng hơn một dòng gọi `Cipher`?

Một ứng dụng có thể mã hóa chính xác bằng `AES/GCM/NoPadding` nhưng vẫn không an toàn nếu:

- khóa AES bị hard-code trong repository hoặc cấu hình được chia sẻ;
- cùng khóa dùng cho mọi tenant mà không có ranh giới truy cập;
- bản sao khóa ở log, cache, backup, crash dump không bị kiểm soát;
- khi khóa cần rotate, hệ thống xóa ngay khóa cũ và **mất khả năng giải mã dữ liệu lịch sử**;
- khóa ký bị lộ nhưng hệ thống tiếp tục coi mọi chữ ký mới như hợp lệ;
- người vận hành bấm **disable** rồi nhầm tưởng ciphertext đã bị mã hóa lại hoặc kẻ tấn công đã mất bản sao khóa.

Các primitive mật mã giải quyết những phép toán như encrypt/decrypt, sign/verify, HMAC; **key management** trả lời ai được tạo, sử dụng, lưu giữ, phân phối, đổi, thu hồi và xóa các khóa mà primitive sử dụng.

Nguyên tắc cơ bản: **độ an toàn của ciphertext không lớn hơn độ an toàn của khóa cùng quá trình sử dụng khóa**. Nếu đối thủ lấy được khóa plaintext hợp lệ, AES mạnh không tự bảo vệ được thông điệp đã mã hóa dưới khóa đó.

### 1.1. Khái niệm cần phân biệt

| Thuật ngữ | Định nghĩa thực hành | Cạm bẫy |
| --- | --- | --- |
| **Secret key** | Khóa đối xứng dùng chung cho thao tác như AES/HMAC | Ai nắm được khóa thường có thể thực hiện thao tác được thuật toán cho phép; policy phải hạn chế |
| **Private key** | Thành phần bí mật của key pair (RSA/ECDSA/Ed25519...) | Public key có thể phát hành, private key phải được bảo vệ |
| **Public key** | Thành phần công khai dùng verify chữ ký hoặc bước mật mã tương ứng | Public key chưa được xác thực danh tính không thể tự chứng minh “đúng chủ sở hữu” |
| **DEK – Data Encryption Key** | Khóa mã hóa payload/file/record | Có thể tạo riêng theo object/tenant và bọc bằng KEK |
| **KEK – Key Encryption Key** | Khóa bọc/giải bọc DEK hoặc key material khác | Compromise KEK có thể ảnh hưởng nhiều wrapped DEK tùy phạm vi |
| **Key ID / version** | Danh tính ổn định của đối tượng khóa và phiên bản key material | Không phải bí mật, nhưng không được tin version do client tùy ý đưa vào mà bỏ qua policy |
| **Cryptoperiod** | Khoảng thời gian khóa được phép sử dụng theo mục đích nhất định | Hết thời gian **encrypt new** không nhất thiết có nghĩa không được **decrypt old** |
| **Rotation** | Chuyển từ khóa/phiên bản cũ sang khóa/phiên bản mới | Rotation metadata chưa làm dữ liệu cũ tự đổi khóa |
| **Rewrap** | Bảo vệ lại DEK bằng KEK mới | Không thay bản mã payload nếu DEK giữ nguyên; không sửa được DEK đã lộ |
| **Re-encrypt** | Giải mã và mã hóa lại payload bằng DEK/key mới | Tốn I/O, cần kiểm integrity, atomic migration và backup |
| **Revoke / disable** | Rút quyền/đánh dấu không cho dùng key trong hệ thống quản lý | Không xóa bản sao raw key mà đối thủ đã lưu |
| **Destroy** | Hủy vật liệu khóa với bảo đảm/quy trình thích hợp | Xóa alias file đơn thuần không chứng minh mọi bản sao, backup, memory đã bị xóa |

### 1.2. Không phải mọi loại khóa có vòng đời giống nhau

**AES data key:** Dùng encrypt/decrypt, cần quản lý nonce riêng dưới cùng key, nhiều khi giải mã dữ liệu lịch sử sau khi ngừng encrypt new. 

**HMAC key:** Dùng tạo/verify MAC; sau rotation có thể cần verify thông điệp cũ trong thời hạn chấp nhận. Quyền tạo MAC mới không giống quyền verify MAC cũ trong mô hình shared-secret; bên có HMAC key vẫn có năng lực tạo MAC nên không thể coi “verify-only” là phân biệt mật mã nếu họ giữ raw key.

**Private signing key:** Dùng ký; public key tương ứng dùng verify. Sau ngừng ký, có thể vẫn cần public key/cert hoặc metadata để verify chữ ký lịch sử, nhưng phải xét **thời điểm ký**, trust chain và chính sách khi private key bị compromise.

**TLS private key/certificate:** Certificate có thời hạn và có thể bị thu hồi qua cơ chế PKI; việc thay certificate không tự xóa private key bị rò ở mọi nơi.

**KEK trong KMS/HSM:** Có thể không export được; rotate KEK liên quan những wrapped DEK, quyền unwrap và chính sách sử dụng của dịch vụ.

**Password-derived key:** Độ mạnh bị giới hạn bởi password, cần KDF có salt/work factor; không đánh đồng với khóa ngẫu nhiên mạnh. Nếu đổi password để bảo vệ DEK, có thể rewrap DEK thay vì re-encrypt toàn bộ dữ liệu.

## 2. Vòng đời khóa: giai đoạn công việc và trạng thái sử dụng

Một mô hình dễ nhớ:

```text
Định nghĩa mục đích / threat model
                 │
                 ▼
              Sinh khóa
                 │
                 ▼
       Đăng ký / cấp phát / phân quyền
                 │
                 ▼
            Kích hoạt (ACTIVE)
                 │
        ┌────────┴──────────┐
        ▼                   ▼
  Sử dụng hợp lệ       Backup / giám sát
        │
        ▼
  Rotation định kỳ / kết thúc originator use
        │
        ▼
  Không tạo dữ liệu mới bằng khóa cũ
  (DECRYPT-ONLY / DEACTIVATED theo policy)
        │
        ▼
  Hết nhu cầu đọc lịch sử + giữ chứng cứ
        │
        ▼
    Destroy / crypto-erase

Bất kỳ thời điểm nào phát hiện nghi lộ khóa:
  COMPROMISED → ngăn sử dụng, thông báo, thay khóa,
  điều tra phạm vi và xử lý dữ liệu/chữ ký bị ảnh hưởng
```

NIST SP 800-57 Part 1 Rev. 5 mô tả các trạng thái như **pre-activation, active, deactivated, compromised, destroyed** và các chuyển đổi, đồng thời phân biệt quyền dùng khóa để *bảo vệ dữ liệu mới* với quyền *xử lý dữ liệu đã bảo vệ*. Sơ đồ ở đây là mô hình ứng dụng học tập, không phải sao chép nguyên state machine của tiêu chuẩn.

| Trạng thái | Cho phép làm gì? | Phải chặn/đặc biệt cẩn trọng |
| --- | --- | --- |
| **PENDING / PRE-ACTIVATION** | Nhập metadata, xác thực key ownership, chuẩn bị và phê duyệt | Chưa dùng để encrypt/sign production |
| **ACTIVE** | Thao tác đúng mục đích, chính sách, cryptoperiod và quota | Không cấp tự do toàn quyền; audit, kiểm nonce, policy |
| **DECRYPT-ONLY / DEACTIVATED** | Chỉ đọc dữ liệu cũ hoặc verify lịch sử nếu chính sách/loại khóa cho phép | Không encrypt/sign dữ liệu mới bằng key cũ |
| **SUSPENDED** | Tạm ngưng hoạt động theo điều tra/chính sách, có thể phục hồi nếu không compromise | Không tiếp tục cấp phép ở lớp ứng dụng chỉ vì key material vẫn tồn tại |
| **REVOKED / COMPROMISED** | Chỉ thao tác điều tra/khôi phục đặc biệt đã được kiểm soát | Không tạo dữ liệu/chữ ký đáng tin mới; xử lý dữ liệu cũ theo incident plan |
| **DESTROYED** | Có thể giữ *metadata chứng cứ* không nhạy cảm | Không còn dùng key để decrypt/sign; không được tuyên bố destroyed nếu còn key copies |

**Cẩn thận ngôn ngữ:** “Đã ngừng encrypt bằng khóa cũ” là **deactivation/rotation** thường quy. “Đã phát hiện khóa bị lấy cắp” là **compromise**, đòi hỏi phản ứng khẩn cấp; không thể xử lý bằng cùng thủ tục đơn giản rồi coi rủi ro chấm dứt.

### 2.1. Cryptoperiod là một quyết định theo rủi ro

Cryptoperiod nên phụ thuộc thuật toán, mục đích khóa, lượng dữ liệu, tác động nếu key lộ, số lần sử dụng/giới hạn nonce, loại dữ liệu và yêu cầu compliance. **Không có một chu kỳ 90 ngày/1 năm phù hợp mọi khóa.** Đặt lịch tự động và sự kiện rotation có căn cứ; không copy một con số từ ví dụ sang tất cả hệ thống.

Một DEK chỉ được dùng để mã hóa trong một ngày có thể vẫn cần để giải mã bản ghi chưa hết thời hạn lưu trữ trong nhiều năm. Đây là lý do quản lý **originator-usage period** (sinh bảo vệ mới) tách khỏi **recipient-usage period** (xử lý dữ liệu cũ) theo NIST.

## 3. Bước 1 – Sinh khóa: đúng thuật toán, kích thước và nguồn entropy

### 3.1. Xác định ý định trước khi tạo key

Trước khi gọi API, cần có ít nhất:

- **Key purpose:** encryption, decryption, signing, verification, HMAC, key wrapping hay handshake;
- **Algorithm / size:** AES-128/256, HMAC-SHA256, RSA hoặc Ed25519 tùy protocol và chính sách;
- **Owner/tenant/service/asset:** khóa thuộc đối tượng nào và ai chịu trách nhiệm;
- **Exportability:** có được export key bytes không, hay phải giữ non-exportable trong HSM/KMS?
- **Allowed operations:** encrypt/decrypt, sign/verify, wrap/unwrap và context cụ thể;
- **Environment:** production/staging/test phải tách key material; không dùng cùng khóa trên cả ba;
- **Lifetime/volume:** thời hạn, quota, rotation trigger, giới hạn số lần mã hóa/nonce;
- **Recovery:** backup/escrow/wrapped form, ứng dụng có cần giải mã dữ liệu cũ hay không.

Một khóa mạnh được dùng sai mục đích, sai tenant hoặc sai algorithm vẫn là lỗi bảo mật. Không chọn RSA chỉ vì “khóa dài hơn”; không tái sử dụng một key để vừa encrypt vừa MAC hoặc nhiều protocol mà không có thiết kế key separation.

### 3.2. Tạo AES secret key với Java 17+

```java
import java.security.SecureRandom;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

SecureRandom rng = new SecureRandom();
KeyGenerator generator = KeyGenerator.getInstance("AES");
generator.init(256, rng);
SecretKey key = generator.generateKey();
```

Đây là **đoạn minh họa cần nằm trong method xử lý checked exception**. Với Java 17 và provider thông dụng, AES-256 được hỗ trợ; với môi trường/provider bị giới hạn, kiểm theo chính sách và khả năng cung cấp thuật toán thực tế. Trong chương trình chạy đầy đủ ở mục 12, dùng AES-128 để ví dụ rộng rãi trên các môi trường Java 17, không ngụ ý AES-128 luôn phù hợp mọi threat model.

Các ý quan trọng:

1. `KeyGenerator` biểu diễn yêu cầu **khóa cho thuật toán AES**, không phải mật khẩu.
2. `SecureRandom` là API tạo bit có chất lượng mật mã; không dùng `Random`, `ThreadLocalRandom`, timestamp, UUID ghép hay seed hard-code.
3. `SecretKey` là một đối tượng tham chiếu khóa. Một provider có thể giữ key ngoài phần mềm và `key.getEncoded()` có thể trả `null` đối với non-exportable key.
4. **Không in raw key** bằng `Base64.getEncoder().encodeToString(key.getEncoded())` rồi ghi vào log/config.
5. Độ dài output 256 bit từ một mật khẩu yếu đã băm không tương đương khóa 256 bit lấy từ CSPRNG; nếu đầu vào là password, cần KDF và chính sách password.

### 3.3. Sinh cặp khóa ký trong Java

```java
import java.security.KeyPair;
import java.security.KeyPairGenerator;

KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
KeyPair pair = generator.generateKeyPair();
// pair.getPrivate() phải được bảo vệ.
// pair.getPublic() có thể xuất bản theo cơ chế tin cậy.
```

Đây là đoạn minh họa, cần xử lý checked exception và khả năng hỗ trợ provider. `Ed25519` dành cho **chữ ký số**; không sử dụng key pair Ed25519 để làm khóa mã hóa AES.

`KeyPairGenerator.getInstance("RSA")` cũng có thể dùng trong giao thức/PKI tương thích, nhưng kích thước key và scheme/padding phải theo chính sách. Với TLS, việc tự tạo key pair chưa phát hành một **certificate được CA tin cậy**; quy trình CSR, cấp chứng thư, kiểm chain và chính sách tin cậy là một bước riêng.

### 3.4. Ưu tiên tạo khóa ở đúng ranh giới bảo mật

- **Software-generated/exportable:** Java `KeyGenerator` hoặc `KeyPairGenerator` trả đối tượng key, dễ tích hợp, nhưng RAM/process và backup phải được bảo vệ.
- **Provider-backed:** HSM/PKCS#11 có thể tạo khóa tại token và không cho xuất private/raw key, giúp giảm số bản sao key material.
- **Cloud KMS:** có thể tạo và sử dụng key trong dịch vụ quản lý, hoặc tạo DEK dạng plaintext tạm thời + wrapped key. Cách dùng và export policy khác nhau theo provider/API, **không mặc nhiên mọi khóa KMS đều exportable hay mọi lời gọi KMS trả raw key**.

Nếu chính sách yêu cầu non-exportable, không tạo khóa exportable trong ứng dụng rồi import một bản sao và tuyên bố “khóa chưa bao giờ rời HSM”. Cần kiểm chứng nơi key thực sự được sinh và những chỗ raw key đi qua.

### 3.5. Kiểm thử entropy không đồng nghĩa kiểm thử randomness bằng mắt

Một vòng lặp in 100 khóa khác nhau không chứng minh generator an toàn. Chất lượng `SecureRandom` phụ thuộc triển khai/provider và nguồn entropy; không dùng thời gian hoặc giá trị dễ đoán để thay nguồn seed. Trên hệ thống có yêu cầu cao, xem cấu hình JDK/provider, kiểm soát môi trường container/VM, chuẩn vận hành CSPRNG và chính sách compliance.

## 4. Bước 2 – Cấp phát (provisioning): đưa khóa đến đúng bên có quyền

**Cấp phát khóa** không phải việc gửi raw key qua email/chat hoặc copy file keystore cho mọi service. Nó là quy trình bảo đảm đúng đối tượng **được đăng ký, nhận quyền và có khả năng dùng khóa cho đúng purpose**.

Một workflow thường gồm:

1. **Yêu cầu:** service/owner khai báo mục đích, key type, algorithm, environment, tenant, retention, cần export hay không.
2. **Phê duyệt:** policy/IAM và separation of duties quyết định ai được create/activate/access/delete.
3. **Tạo hoặc import:** key được sinh ở ranh giới an toàn, gắn key ID/metadata. Với import phải xác minh nguồn, algorithm, format và đường truyền an toàn.
4. **Liên kết chủ sở hữu:** key ID ↔ service identity / tenant / dataset / operation / environment. Không tin tên alias tùy ý do request bên ngoài gửi.
5. **Phân quyền:** chỉ cấp `Encrypt`/`Decrypt`/`GenerateDataKey`/`Sign`/`Verify`/`Wrap`/`Unwrap` cần thiết; tránh quyền quản trị/disable/delete cho runtime ứng dụng.
6. **Kích hoạt:** chỉ dùng trong thời gian, quota và trạng thái cho phép; cấu hình monitoring/audit.
7. **Kiểm chứng:** chạy test không dùng dữ liệu thật để biết đúng identity, key version và expected operation.

Ví dụ mô hình phân quyền:

| Role | Quyền nên có | Quyền nên tránh |
| --- | --- | --- |
| Application writer | `Encrypt` hoặc `GenerateDataKey` với key/context được phép | `DeleteKey`, export KEK, quản lý key của tenant khác |
| Application reader | `Decrypt`/`Unwrap` đúng đối tượng và điều kiện | Create/rotate vô hạn, xóa key, key admin |
| Key administrator | Tạo, cấu hình policy, lên lịch rotation theo phê duyệt | Đọc plaintext dữ liệu nếu không có nhu cầu |
| Incident responder | Disable, audit, kích hoạt workflow khẩn cấp theo kiểm soát | Tự ý phá hủy key khi chưa đánh giá recovery/retention |
| Auditor | Read-only metadata, log, trạng thái và rotation history | Raw key material hoặc plaintext |

Một dịch vụ có quyền decrypt tất cả tenant tương đương “chìa khóa vạn năng” dù AES rất mạnh. Cần phối hợp IAM, policy theo resource, key alias/context, business authorization và log. KMS quyền thao tác mật mã không tự thay thế **kiểm tra quyền đối tượng nghiệp vụ**.

### 4.1. Đưa public key và secret key ra ngoài khác nhau

- **Public key:** có thể phân phối, nhưng cần bảo đảm **authenticity/binding** bằng certificate, pinned key, trusted registry hoặc kênh đáng tin; public key không phải bí mật.
- **Private/secret key:** chỉ vận chuyển qua cơ chế key transport/wrapping có bảo mật, quy trình HSM/KMS hoặc keystore được bảo vệ. Nếu raw key được xuất ra RAM, đánh giá cả process, log, crash dump, truyền mạng và bên nhận.
- **Wrapped DEK:** vẫn cần authenticated key wrapping và ràng buộc context (tenant, object, version), không phải “Base64 của key” hoặc XOR password hash.
- **Certificate:** chứa public key và thông tin danh tính/issuer/signature; **không** phải cùng thứ với private key. Certificate đã được CA cấp không đồng nghĩa private key đang được bảo vệ.

**Đường cấp phát cũng là một phần của lifecycle**, vì key rất mạnh vẫn có thể lộ trong một lần copy sai kênh.

## 5. Bước 3 – Lưu trữ: KeyStore, KMS, HSM và backup

| Phương án | Lưu gì? | Khi nào phù hợp? | Giới hạn |
| --- | --- | --- | --- |
| Java `KeyStore` loại `PKCS12` | Private key + certificate chain, `SecretKeyEntry` khi provider hỗ trợ, hoặc certs | App/tool cần lưu key có password/file control | File và password là hai tài sản cần bảo vệ; không có IAM/audit tập trung như KMS |
| Java `KeyStore` loại `PKCS11` | Tham chiếu key/token thông qua provider PKCS#11 | HSM/token và yêu cầu phần cứng | Phụ thuộc thiết bị, provider, session/login và cấu hình token |
| Cloud KMS | KEK/key object, key policy, version, encrypt/decrypt hoặc wrap/unwrap | Nhiều service, quyền tập trung, audit, rotation | Availability, permissions, network/latency, nhà cung cấp |
| HSM | Key operations trong module bảo mật, có thể non-exportable | Compliance, signing authority, KEK/tài sản nhạy cảm | Chi phí/quản trị, quota, backup/recovery theo cơ chế HSM |
| Envelope encryption | Ciphertext data + wrapped DEK; KEK ở KMS/HSM | Lượng dữ liệu lớn, rotation KEK, nhiều tenant | Phải giữ wrapped DEK, key ID/context, xử lý plaintext DEK tạm trong RAM |
| Secret manager | Secret được quản lý qua API và policy | Lưu các secret/phần mềm cần truy xuất | Không tự động thay mọi chức năng KMS/HSM và không bảo đảm non-exportability |

**Chú ý:** Có file `.p12` không tự động có security posture của HSM. `PKCS12` là định dạng/keystore được bảo vệ bằng password; nếu file và password cùng bị lộ, key có thể mất an toàn. Đừng lưu cả `keystore.p12` và password giải khóa trong cùng repository/container image rồi coi đã tách secret.

### 5.1. Phân biệt keystore và truststore

**Keystore cho identity của chính ứng dụng** thường giữ private key cùng certificate chain hoặc secret key. **Truststore** giữ trust anchor/chứng chỉ công khai mà ứng dụng tin cậy cho TLS/PKIX. Lưu certificate trong truststore **không** đồng nghĩa có private key tương ứng; truststore cũng cần bảo vệ tính toàn vẹn để ngăn attacker thay trust anchors.

Với Java:

```java
KeyStore store = KeyStore.getInstance("PKCS12");
store.load(inputStream, password);
Key key = store.getKey("data-v1", password);
```

Đây là đoạn **minh họa chưa độc lập**: cần import `java.security.KeyStore` và `java.security.Key`, `inputStream`, `password`; phải xử lý checked exception. Key có thể không tồn tại, password sai hoặc keystore/provider không hỗ trợ loại entry. `getKey` trả tham chiếu `Key`; không tự giải quyết permission/quota/cryptoperiod cấp ứng dụng.

### 5.2. Nguyên tắc lưu khóa trong production

1. **Hạn chế khả năng export:** nếu không cần raw key, ưu tiên thao tác trong KMS/HSM.
2. **Least privilege:** process runtime chỉ được dùng đúng key/operation; không cấp delete/rotate cho toàn bộ service.
3. **Access controls độc lập:** file permissions, secret distribution, token/service identity, network và IAM.
4. **Encryption + integrity:** wrapped DEK/PKCS12/password phải được bảo vệ, key metadata/alias cũng phải có integrity.
5. **Backup và recovery:** backup của key khác với backup của ciphertext. Mất key mà còn data ≠ khôi phục được data.
6. **Audit:** theo dõi create/import/activate/use/rotate/disable/delete/export, nhưng **không log raw key, password hay plaintext**.
7. **Tách môi trường:** dev/test/prod và tenant/scope; không chuyển file prod keystore sang laptop dev.
8. **Vòng đời bản sao:** cache, disaster-recovery replicas, snapshots, exported keystore, laptop, build pipeline, CI artifacts.
9. **Phê duyệt thao tác nguy hiểm:** delete/destroy cần kiểm retention, legal hold, phụ thuộc các phiên bản dữ liệu cũ và two-person control khi phù hợp.

### 5.3. Backup key không phải tạo nhiều bản sao vô quản lý

Backup key phải có encryption, ownership, access policy, inventory và định kỳ kiểm tra restore. Khả năng recovery phải được **diễn tập**, không chỉ dựa vào checkbox “backup enabled”. Nếu HSM/KMS key không export, phải hiểu khả năng backup/restore của chính sản phẩm và giới hạn về vùng địa lý/account.

Khi muốn **crypto-shredding/cryptographic erasure**, phải xác định mọi bản DEK/KEK cần hủy, wrapped copies, backups và nơi key được cache. Xóa một blob mật mã không có nghĩa data không thể khôi phục nếu key vẫn còn trong bản sao khác.

## 6. Bước 4 – Sử dụng khóa: đúng phạm vi và ràng buộc mật mã

### 6.1. Không dùng cùng khóa cho mọi nhu cầu

**Key usage separation:** Khóa AES mã hóa dữ liệu nên được tách khỏi key HMAC, key-wrapping, signing key, JWT secret và những protocol khác trừ khi thiết kế thuật toán chuẩn quy định cách derive subkeys an toàn. Khóa chứa 256 bit entropy không có nghĩa được tái sử dụng vô hạn giữa các mục đích.

Ví dụ:

- `data-v7`: AES-GCM cho record tenant A;
- `wrap-v3`: KEK bọc DEK của tenant A;
- `sign-v5`: private key ký message;
- `tls-server-v2`: private key cho TLS server identity.

Một key ID phải dẫn tới **một key material + purpose + policy + version** rõ ràng. Nếu một application user đổi `keyId` trong JSON để truy cập khóa tenant khác, backend phải từ chối dù lookup key theo ID có kết quả.

### 6.2. AES-GCM: nonce duy nhất dưới cùng key

Mọi lần `AES/GCM/NoPadding` dùng một **key giống nhau** phải tránh dùng lại **nonce giống nhau**. Nonce reuse phá nghiêm trọng confidentiality và authentication của GCM. Thông thường dùng nonce 12 byte (96 bit), tag 128 bit; cơ chế uniqueness phải tính số lượng encryption, concurrency, restart/snapshot và provider behavior.

```java
byte[] nonce = new byte[12];
secureRandom.nextBytes(nonce);

Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, aesKey,
        new GCMParameterSpec(128, nonce));
cipher.updateAAD(metadataBytes);
byte[] ciphertextAndTag = cipher.doFinal(plaintextBytes);
```

Đây là **đoạn minh họa chưa độc lập**: cần imports JCA/JCE, các biến `secureRandom`, `aesKey`, `metadataBytes`, `plaintextBytes` và exception handling. Trong production, AAD phải ràng buộc **key version, tenant, object, schema/operation** theo thiết kế, và bên decrypt phải truyền y hệt. Nonce là dữ liệu có thể công khai nhưng phải lưu cùng ciphertext nếu cần decrypt.

**Thêm key version vào AAD không tự ngăn nonce reuse.** Tương tự, rotation không hợp lệ hóa những ciphertext đã bị tạo bằng cặp key–nonce lặp trong quá khứ.

### 6.3. Giới hạn quyền: encrypt-only và decrypt-only

Khi app dừng sử dụng key v1 để **mã hóa mới**, nó vẫn có thể cần v1 để giải mã record cũ. Giao thức nên buộc:

- Encryption luôn lấy **active key version** từ key registry tin cậy, không lấy từ tham số caller.
- Decryption đọc key ID/version từ envelope đã được xác thực/bảo vệ, kiểm rằng version này được phép decrypt record/context tương ứng.
- Key v1 chuyển sang **decrypt-only** hoặc deactivated processing mode theo semantics của KMS/policy; không dùng để mint ciphertext mới.
- Retention và migration biết bao nhiêu dữ liệu v1 còn tồn tại và ở đâu, trước khi retire/destroy.
- Không dùng key version làm dữ liệu quyết định authorization duy nhất: version là metadata, phải có role/owner/tenant binding và integrity.

**Lưu ý HMAC:** Vì cùng secret key thực hiện được cả tạo MAC lẫn kiểm MAC, việc thiết lập “verify-only” ở hệ thống chia sẻ raw HMAC key không thể ngăn người cầm raw key tính MAC mới. Cần enforce ở ranh giới dịch vụ/HSM hoặc thay scheme khi cần tách năng lực.

### 6.4. Quan sát hoạt động mà không làm lộ bí mật

Log nên chứa **key ID/version, service principal, operation name, outcome, timestamp, request ID, policy decision**, các mã lỗi đã chuẩn hóa. Không log `SecretKey.getEncoded()`, password, token bearer, plaintext, IV+key cùng lúc như debug dump. Cần kiểm các đường bất thường: crash dump, heap dump, APM tracing, exception stack, metrics labels và test fixtures.

Không phải mọi quyền truy cập key thất bại đều là attack, nhưng gia tăng nhanh `Decrypt` sai key/tenant, `AccessDenied`, nonce duplication hoặc thay đổi policy không mong muốn phải được giám sát.

## 7. Bước 5 – Luân chuyển (rotation) bình thường

**Rotation** thường có nghĩa bắt đầu dùng **key material mới** hoặc một phiên bản mới cho hoạt động mới, trong khi có lộ trình xử lý dữ liệu cũ. Việc chuyển alias `current=v2` không tự tái mã hóa dữ liệu v1.

### 7.1. Quy trình đối xứng key version v1 → v2

```text
Trước:
  ACTIVE encryption key = v1
  Record cũ: [version=v1, nonce1, ciphertext1, tag1]

Tạo v2:
  Generate(v2) → Policy/QA → ACTIVE for new writes
  Deactivate v1 for new encryption; keep v1 for permitted reads

Trong migration:
  New record: [version=v2, nonce2, ciphertext2, tag2]
  Old record vẫn decrypt bằng v1 khi cần

Nếu mục tiêu là loại bỏ v1:
  Xác thực/giải mã record v1 → encrypt lại bằng v2 + nonce mới
  → kiểm readback + atomic switch → xác nhận không còn dependency
  → hủy v1 theo policy và kiểm backups
```

Bảng quyết định:

| Trạng thái | Encrypt record mới | Decrypt record v1 | Decrypt record v2 |
| --- | --- | --- | --- |
| Chưa rotate, v1 active | v1 | Có | Không có v2 |
| Đã activate v2 | **v2** | **Có** trong read window/policy | Có |
| Đã migrate toàn bộ + xác nhận không còn v1 | **v2** | Không nếu đã hủy key v1 | Có |
| V1 bị compromise | Không v1 | Chỉ xử lý đặc biệt trong incident plan | Có nếu v2 được tạo/giữ an toàn |

Điều kiện **“toàn bộ dữ liệu đã migrate”** gồm backup, replica, archive, delayed messages và bản ghi ít được truy cập; nếu bỏ sót, xóa v1 có thể làm data lịch sử không thể giải mã. Không để một node vẫn encrypt bằng v1 trong khi node khác đã chuẩn bị xóa v1; rotation phải phối hợp với rollout và active-version registry.

### 7.2. Envelope encryption: rewrap khác re-encrypt

Giả sử một file lớn được mã hóa bằng **DEK ngẫu nhiên**:

```text
payloadCiphertext = AES-GCM(DEK, plaintext, nonce)
wrappedDEK_v1    = Wrap(KEK_v1, DEK)
```

Khi chỉ thay **KEK**, ta có thể:

```text
DEK = Unwrap(KEK_v1, wrappedDEK_v1)
wrappedDEK_v2 = Wrap(KEK_v2, DEK)

payloadCiphertext vẫn như cũ vì DEK không đổi.
```

Đây là **rewrap**: thay cơ chế bảo vệ DEK, không cần xử lý lại hàng gigabyte ciphertext. Nhưng nếu **DEK đã lộ**, rewrap không làm payload an toàn trở lại: phải sinh DEK mới và **re-encrypt data**. Nếu KEK v1 đã lộ và attacker đã giữ wrappedDEK_v1, họ có thể đã khôi phục DEK; cần incident investigation thay vì chỉ rewrap rồi coi an toàn.

Một KMS có thể hỗ trợ các thao tác re-encrypt/wrap key chuyên dụng mà không trả plaintext DEK về ứng dụng. Kiểm đặc tả từng provider, policy và loại key; không tự suy ra mọi KMS cung cấp cùng API.

### 7.3. Rotation của signing key khác data encryption key

Với private signing key `sign-v1`:

1. Tạo và kích hoạt `sign-v2`, phát hành public key/certificate tương ứng qua kênh đáng tin.
2. Ngừng phát hành chữ ký **mới** bằng private key v1.
3. Verifier tiếp tục nhận chữ ký **lịch sử hợp lệ** của v1 theo chính sách thời điểm ký, validity và trạng thái compromise.
4. Sau thời gian cần thiết, ngừng chấp nhận v1 hoặc áp dụng quy trình lưu bằng chứng thời gian/chứng thực dài hạn.

Cần phân biệt **public key vẫn có thể được lưu để verify** với **private signing key vẫn được phép sign**. Đừng xóa mọi public key cũ nếu hệ thống còn chữ ký lịch sử cần xác minh. Nếu v1 bị compromise, vấn đề phức tạp hơn: chữ ký mới có thể bị giả mạo, và chữ ký lịch sử cần đánh giá với timestamp/trust evidence phù hợp.

### 7.4. Kịch bản zero-downtime có kiểm soát

Một rotation production tốt thường là:

1. **Prepare:** tạo v2, thiết lập quyền, backup/restore, kiểm compatibility và version-aware parser.
2. **Deploy reader first:** đảm bảo mọi instance có thể giải mã v1 và v2 (theo phân quyền), chưa chuyển writer.
3. **Activate v2 writers:** đổi active version qua registry/cấu hình đáng tin, theo dõi writer có đang còn tạo v1 không.
4. **Observe:** đo tỷ lệ encrypt v2, decrypt v1/v2, latency, error rate, audit logs, replication consistency.
5. **Re-encrypt/rewrap (nếu cần):** chạy migration có checkpoint/idempotency và verify tag, tránh mất data khi rollback.
6. **Retire old:** chỉ sau kiểm inventory/dependencies, backup/DR, retention, approvals và rollback policy.
7. **Document:** ghi actor, thời điểm, lý do, incident/ticket, key IDs và kết quả kiểm chứng.

Với multi-region, stale caches và asynchronous queues, có thể tồn tại cửa sổ cả v1/v2 đồng thời. Protocol phải quy định rõ quyền decrypt và **một nguồn chân lý cho active encryption version**, không sử dụng wall-clock time riêng từng máy để chọn key.

## 8. Bước 6 – Thu hồi (revoke) và phản ứng khi khóa lộ

Thu hồi không chỉ là “rotate nhanh hơn”. **Khi nghi ngờ khóa bị lộ**, phải giả định attacker có thể có bản sao raw key hoặc đã thực hiện các thao tác mật mã không được phép.

### 8.1. Những hành động khác nhau

| Hành động | Tác dụng thực tế | Không bảo đảm điều gì? |
| --- | --- | --- |
| **Disable key** trong KMS/policy | Ngăn thao tác mới qua hệ thống KMS đã áp dụng chính sách | Không xóa raw key đã export/cached hay giải mã được bằng bản sao |
| **Revoke access grant / IAM** | Rút quyền của identity thực hiện encrypt/decrypt/sign | Không hủy secret đã bị người đó sao chép trước đó |
| **Rotate key** | Tạo material mới và chuyển thao tác mới sang v2 | Không đổi ciphertext cũ, không thu hồi bản sao v1 |
| **Rewrap DEK** | Bảo vệ DEK bằng KEK mới | Không cứu dữ liệu nếu DEK đã bị lộ |
| **Re-encrypt payload** | Tạo bản mã mới dưới DEK mới, nonce mới | Không thể “lấy lại” plaintext đã bị attacker đọc trước đó |
| **Revoke certificate** | Thông báo certificate/key identity không còn được tin cho mục đích/từ thời điểm theo chính sách PKI | Không xóa private key hoặc khiến tất cả client kiểm revocation ngay |
| **Delete/destroy key** | Loại bỏ key material theo quy trình, có thể tạo cryptographic erasure | Không chứng minh mọi bản backup/cached copy đều biến mất |
| **Expire credentials/token** | Chặn việc gọi API bằng credential cũ qua hệ thống kiểm soát | Không thay key mã hóa nếu đó là hai bí mật khác nhau |

### 8.2. Incident response khi nghi lộ DEK

Ví dụ khóa AES `data-v1` được ghi vào log:

1. **Cô lập nguồn rò:** chặn xuất log và quyền đọc không cần thiết, bảo quản chứng cứ cho điều tra theo quy trình.
2. **Đánh giá phạm vi:** key version, tenant, data sets, wrapped DEKs, logs/backups, retention và khoảng thời gian lộ.
3. **Ngăn tạo dữ liệu mới:** disable v1 for new encrypt, activate v2 từ nguồn an toàn; cân nhắc chặn decrypt v1 qua API trừ luồng recovery hạn chế.
4. **Xử lý ciphertext cũ:** nếu cần phục hồi confidentiality, giải mã từ bản dữ liệu tin cậy rồi mã hóa lại bằng DEK mới/nonce mới; chú ý đối thủ có thể đã đọc bản mã cũ.
5. **Rút quyền/credential liên quan:** IAM keys, deployment secrets, service accounts, key grants và mọi bản sao v1.
6. **Migrate và xác minh:** kiểm tất cả primary/replica/backup theo phạm vi; giữ lịch sử và bằng chứng.
7. **Retire/destroy** v1 khi pháp lý/khả năng recovery/incident plan cho phép; báo cáo và cập nhật detection.

Không tự động delete v1 ngay khi thấy dấu hiệu compromise nếu hệ thống cần nó để khôi phục/migrate và chưa có bản backup tin cậy. Tuy nhiên cũng không được tiếp tục dùng v1 cho encryption mới chỉ vì “còn cần decrypt”. Đây là bài toán balance giữa containment và recovery, cần runbook đã được phê duyệt.

### 8.3. Thu hồi private signing key/TLS certificate

Nếu private signing key hoặc TLS private key lộ:

- Kích hoạt cặp khóa mới, phân phối public key/certificate mới từ kênh tin cậy.
- Ngừng **ký mới/handshake mới** bằng private key cũ trong hệ thống kiểm soát.
- Nếu là chứng thư X.509, thực hiện revocation tại CA hoặc cơ chế tương ứng (CRL/OCSP hoặc mô hình nhà cung cấp) và kiểm hành vi của relying parties.
- Xem xét **chữ ký đã phát hành**: việc verify signature toán học không trả lời chữ ký đã được tạo *trước hay sau khi compromise*. Signed timestamp hoặc bằng chứng thời gian được tin cậy có thể cần trong hệ thống lưu chữ ký dài hạn.
- Nếu verifier pin public key không qua PKI, phải cập nhật trusted-key registry/pin trực tiếp theo protocol; CRL/OCSP có thể không liên quan.

**Không nhầm:** thu hồi certificate không phải hủy private key trên máy đã bị tấn công. Và public key thường không có bí mật nên không phải mục tiêu xóa để “khóa kẻ tấn công”; cần thay chính sách tin cậy của verifier và rút quyền dùng private key.

### 8.4. Revocation và backup

Một backup hệ thống lưu **old key material** có thể làm key đã “xóa khỏi active store” xuất hiện lại sau disaster recovery. Runbook cần quản lý trạng thái revoked/compromised như metadata đáng tin cậy áp dụng **sau restore**, không chỉ restore raw keystore cũ rồi tự động tái kích hoạt v1.

Nếu một service bị compromise, phải xem cả bản sao secret trong container image, environment, CI logs, crash dumps, snapshots, terminal history và artifacts. Việc xoay key trên dashboard không xóa được các bản sao đó.

## 9. Bước 7 – Hủy bỏ, lưu trữ lịch sử và khả năng chứng minh

**Hủy khóa** có thể là một hành động gây mất dữ liệu không thể đảo ngược. Chỉ nên thực hiện khi:

- key đã hết cryptoperiod và không còn dữ liệu hợp lệ cần decrypt/sign/recover theo policy;
- dữ liệu đã được migrate hoặc được cho phép không khôi phục nữa;
- backup, archive, replicas, các bản sao khôi phục được đánh giá;
- có phê duyệt hoặc retention/legal-hold controls;
- thao tác hủy đáp ứng yêu cầu sản phẩm/môi trường, với audit/bằng chứng.

**Crypto-erasure** là việc làm dữ liệu mã hóa không thể giải mã bằng cách phá hủy vật liệu key cần thiết; hiệu quả phụ thuộc việc key **không còn bản sao khả dụng**, các đường recovery không còn và key thật sự là điểm kiểm soát duy nhất. Không được khẳng định `KeyStore.deleteEntry(alias)` trên file hoặc xóa một row DB là bằng chứng cryptographic erasure hoàn chỉnh.

Sau khi hủy, có thể vẫn cần giữ metadata không nhạy cảm: key ID, version, purpose, created/activated/retired time, owner, algorithm, policy approvals, các sự kiện rotate/revoke/destroy và đối chiếu audit; **không giữ raw secret** chỉ để audit.

### 9.1. Key destruction không phải xóa dữ liệu người dùng

Nếu xóa DEK của một record, ciphertext vẫn có thể tồn tại nhưng không đọc được bằng cơ chế hợp lệ. Đây là một chính sách cần quyết định rõ; nếu người dùng yêu cầu **xóa dữ liệu**, hệ thống còn cần xử lý bản sao plaintext, searchable indexes, caches, logs và các nghĩa vụ retention riêng. Mã hóa và key destruction không thay thế toàn bộ data-governance workflow.

### 9.2. Không kỳ vọng zeroization tuyệt đối từ Java object

`SecretKey` hoặc `PrivateKey` có thể là object nằm trên Java heap, trong provider native memory hoặc trên token. Không phải mọi implementation cho phép xóa ngay tất cả bản copy; GC không cam kết tức thời hay secure wipe. Có thể xóa buffer `byte[]` do ứng dụng kiểm soát bằng `Arrays.fill` khi thích hợp, nhưng việc đó **không** chứng minh mọi copy đã được xóa. Nếu yêu cầu kiểm soát nghiêm, ưu tiên non-exportable KMS/HSM và quy trình vận hành được thẩm định.

## 10. Sổ đăng ký khóa: key inventory và metadata

Một hệ thống trưởng thành cần có **key inventory/registry** thay vì đoán key qua tên file hoặc biến môi trường. Gợi ý schema nghiệp vụ:

| Trường | Ý nghĩa |
| --- | --- |
| `key_id` | Định danh key logic ổn định, không chứa material |
| `version` | Phiên bản key material, ví dụ 1, 2, 3 |
| `algorithm`, `length` | AES-256, Ed25519, RSA..., thông số cần biết |
| `purpose` | Encrypt, Decrypt, Sign, Verify, Wrap, Unwrap |
| `owner`, `tenant`, `environment` | Danh tính/ranh giới trách nhiệm |
| `state` | PRE-ACTIVE, ACTIVE, DECRYPT-ONLY, REVOKED, DESTROYED... |
| `created_at`, `activated_at`, `retired_at` | Mốc lifecycle và cryptoperiod |
| `provider_ref` | KMS key ARN/URI, HSM label, keystore alias... |
| `origin`, `exportable` | Nơi sinh/import; policy xuất raw key |
| `rotation_policy` | Ngày/số lượt/incident trigger; chủ sở hữu việc thay key |
| `dependencies` | Datasets, wrapped DEKs, certificates và backups |
| `approval/audit_ref` | Ticket, identity, action/outcome và evidence |

**Không** đặt raw key, private key bytes, password hoặc master secret vào registry metadata. Một key-version có thể được ghi trong ciphertext header, nhưng backend **không** chỉ dựa vào header đó để cấp quyền truy cập: phải kiểm key/tenant/object binding và integrity.

### 10.1. Envelope record mẫu

```text
version=2
tenant=TENANT-A
key_id=data-tenant-a
key_version=7
nonce=<12 bytes>
ciphertextAndTag=<...>
aad=canonical(version,tenant,key_id,key_version,record_id)
```

Đây là **định dạng khái niệm**, chưa phải parser/schema sản xuất. Trong AES-GCM, AAD được xác thực nhưng không mã hóa. Bên decrypt cần sử dụng cùng encoding/AAD và đúng key version; sau đó vẫn kiểm authorization để tránh tráo record giữa tenant/đối tượng.

Nếu nhiều record cùng encrypt bằng AES-GCM một key, thiết kế nonce phải bảo đảm không trùng cặp key/nonce; không dựa vào việc key version khác nhưng thực tế raw key giống nhau.

## 11. Mối liên hệ với Java `KeyStore`, keytool và TLS

### 11.1. Java `KeyStore` là nơi cất key, không phải key-lifecycle service

`KeyStore` có các entry như `SecretKeyEntry`, `PrivateKeyEntry` và `TrustedCertificateEntry` tùy loại/store/provider. Việc gọi:

```java
KeyStore ks = KeyStore.getInstance("PKCS12");
ks.load(null, password);
ks.setEntry("data-v1",
    new KeyStore.SecretKeyEntry(aesKey),
    new KeyStore.PasswordProtection(password));
```

chỉ mô tả việc tạo và thêm entry. **Không tự có** rotation scheduler, IAM, tenant isolation, revocation log, dữ liệu migration hay remote audit. `KeyStore` API có thao tác `deleteEntry`, nhưng đó chỉ là xóa một entry tại nơi quản lý hiện tại, không phải bằng chứng tất cả các bản sao v1 đều biến mất.

Cần bảo vệ password dùng với `PKCS12` khác với dữ liệu cần mã hóa; hard-code store password vào source chỉ phù hợp minh họa, không production.

### 11.2. `keytool` và chứng thư

Ví dụ tạo cặp RSA + chứng thư tự ký trong file PKCS12 để **thử nghiệm**:

```shell
keytool -genkeypair -alias service-sign-v1 -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -keystore service-sign.p12 -storetype PKCS12 -validity 365
```

Lệnh trên chạy tương tác hỏi password/subject; không có stdout cố định. **Self-signed certificate không tự được các client khác tin cậy**. Production cần cơ chế CA/PKI hoặc trusted-key distribution phù hợp, policy bảo vệ private key và kế hoạch rotate trước expiry.

Xem alias/public certificate trong keystore:

```shell
keytool -list -v -keystore service-sign.p12 -storetype PKCS12
```

**Không** truyền password thực bằng argument command-line nếu có nguy cơ lộ process list/shell history. Không đưa file private key và store password vào cùng repository, Docker image hoặc bộ sao lưu công khai.

### 11.3. TLS certificate rotation

TLS thường cần cập nhật key pair/certificate chain, cấu hình server/client trust, OCSP/CRL hoặc policy PKI và triển khai đồng bộ. Việc có certificate mới trên disk không có nghĩa các kết nối/server instance đã reload. Phải kiểm handshake thật, trust chain, hostname, notBefore/notAfter, private-key possession, rollback và thời hạn overlapping certificates.

**Nhớ:** đổi chứng thư hoặc alias không tự đổi khóa mã hóa dữ liệu trong database. Hai mục đích key khác nhau phải có inventory/lifecycle khác nhau.

## 12. Chương trình Java 17+ độc lập: sinh khóa, lưu trữ, mã hóa, rotate và retire

Tạo file **`KeyLifecycleDemo.java`**. Demo này mô phỏng vòng đời nhỏ của hai **AES data keys**:

1. Tạo v1 bằng `KeyGenerator`, đặt vào `PKCS12 KeyStore`, dùng v1 mã hóa một record.
2. Tạo v2 mới độc lập, lưu v2; serialized bytes của KeyStore được ghi ra buffer và nạp lại để minh họa **store/load** mà không tạo file thật.
3. Bắt đầu encrypt mới bằng v2; vẫn giải mã record v1 bằng v1 được lưu, chứng minh rotation không bắt buộc làm mất dữ liệu cũ.
4. Dùng `AES/GCM/NoPadding` với nonce 12 byte, tag 128 bit và AAD gắn với key version; thử sửa tag hoặc gán sai version.
5. Xóa alias v1 khỏi store *đang dùng*, xác nhận record v1 **không còn giải mã được qua store này**. Đây chỉ là mô phỏng retirement, **không phải quy trình destroy/compromise response production**.

**Giới hạn đã biết:** Password literal và KeyStore in-memory phục vụ demo, **không dùng để bảo vệ dữ liệu thật**. Trong ứng dụng thực tế, keystore password phải được đưa qua cơ chế secret management; runtime policy phải kiểm phép encrypt/decrypt theo state/tenant/authorization; cần persistence an toàn, backup, audit, nonce uniqueness ở quy mô lớn và key migration. JDK 17 với provider PKCS12 thông dụng hỗ trợ `SecretKeyEntry`; provider bên thứ ba có thể có giới hạn riêng.

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class KeyLifecycleDemo {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private record Envelope(
            int keyVersion, byte[] nonce, byte[] ciphertextAndTag) {}

    private static SecretKey newAesKey() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128, RNG); // Universal baseline for this demo.
        return generator.generateKey();
    }

    private static String alias(int version) {
        return "data-v" + version;
    }

    private static void put(KeyStore store, int version,
            SecretKey key, char[] password) throws Exception {
        store.setEntry(alias(version),
                new KeyStore.SecretKeyEntry(key),
                new KeyStore.PasswordProtection(password));
    }

    private static SecretKey read(KeyStore store, int version,
            char[] password) throws Exception {
        KeyStore.Entry entry = store.getEntry(alias(version),
                new KeyStore.PasswordProtection(password));
        if (!(entry instanceof KeyStore.SecretKeyEntry secret)) {
            throw new GeneralSecurityException(
                    "Key version unavailable");
        }
        return secret.getSecretKey();
    }

    // Domain separation + key version are authenticated as AAD.
    private static byte[] aad(int version) {
        return ("key-lifecycle-demo:v1:version=" + version)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static Envelope seal(int version, SecretKey key,
            byte[] plaintext) throws Exception {
        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad(version));
        return new Envelope(
                version, nonce, cipher.doFinal(plaintext));
    }

    private static byte[] open(Envelope envelope, KeyStore store,
            char[] password) throws Exception {
        SecretKey key = read(store, envelope.keyVersion(), password);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, envelope.nonce()));
        cipher.updateAAD(aad(envelope.keyVersion()));
        return cipher.doFinal(envelope.ciphertextAndTag());
    }

    public static void main(String[] args) throws Exception {
        // DEMO ONLY: password literal is NOT safe for production.
        char[] password = "demo-only-store-pass".toCharArray();
        try {
            KeyStore firstStore = KeyStore.getInstance("PKCS12");
            firstStore.load(null, password);

            SecretKey keyV1 = newAesKey();
            put(firstStore, 1, keyV1, password);

            byte[] payload =
                    "encrypted customer record".getBytes(
                            StandardCharsets.UTF_8);
            Envelope oldRecord = seal(1, keyV1, payload);

            // Rotation: new key for new encryption.
            SecretKey keyV2 = newAesKey();
            put(firstStore, 2, keyV2, password);
            Envelope newRecord = seal(2, keyV2, payload);

            // Demonstrate serialization + reload of the key store.
            ByteArrayOutputStream serialized =
                    new ByteArrayOutputStream();
            firstStore.store(serialized, password);
            KeyStore restored = KeyStore.getInstance("PKCS12");
            restored.load(new ByteArrayInputStream(
                    serialized.toByteArray()), password);

            boolean bothPresent =
                    restored.containsAlias(alias(1))
                    && restored.containsAlias(alias(2));
            boolean oldReadable = Arrays.equals(
                    payload, open(oldRecord, restored, password));
            boolean newReadable = Arrays.equals(
                    payload, open(newRecord, restored, password));

            // A different key version must NOT silently work.
            Envelope wrongVersion = new Envelope(
                    2, oldRecord.nonce(),
                    oldRecord.ciphertextAndTag());
            boolean wrongVersionRejected = false;
            try {
                open(wrongVersion, restored, password);
            } catch (AEADBadTagException expected) {
                wrongVersionRejected = true;
            }

            // GCM detects a changed authentication tag.
            byte[] changed = newRecord.ciphertextAndTag().clone();
            changed[changed.length - 1] ^= 1;
            Envelope tampered = new Envelope(
                    2, newRecord.nonce(), changed);
            boolean tamperRejected = false;
            try {
                open(tampered, restored, password);
            } catch (AEADBadTagException expected) {
                tamperRejected = true;
            }

            // Retire the v1 alias from the store used for lookups.
            restored.deleteEntry(alias(1));
            boolean v1RetiredFromStore =
                    !restored.containsAlias(alias(1));
            boolean oldUnavailable = false;
            try {
                open(oldRecord, restored, password);
            } catch (GeneralSecurityException expected) {
                oldUnavailable = true;
            }

            System.out.println("bothVersionsRestored=" + bothPresent);
            System.out.println("oldRecordReadableAfterRotation="
                    + oldReadable);
            System.out.println("newRecordReadable=" + newReadable);
            System.out.println("wrongVersionRejected="
                    + wrongVersionRejected);
            System.out.println("tamperRejected=" + tamperRejected);
            System.out.println("v1AliasRetired=" + v1RetiredFromStore);
            System.out.println("oldUnavailableThroughStore="
                    + oldUnavailable);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
```

Biên dịch và chạy tại thư mục chứa file:

```shell
javac KeyLifecycleDemo.java
java KeyLifecycleDemo
```

**Stdout kỳ vọng trên Java 17 thông dụng hỗ trợ PKCS12 `SecretKeyEntry` và AES/GCM:**

```text
bothVersionsRestored=true
oldRecordReadableAfterRotation=true
newRecordReadable=true
wrongVersionRejected=true
tamperRejected=true
v1AliasRetired=true
oldUnavailableThroughStore=true
```

### 12.1. Giải thích kết quả

- `bothVersionsRestored=true`: KeyStore chứa alias v1 và v2 và có thể nạp lại từ định dạng đã serialize. **Không** có nghĩa việc bảo vệ password, backup và quyền truy cập đã đáp ứng production.
- `oldRecordReadableAfterRotation=true`: v2 xuất hiện không tự vô hiệu hóa v1; record cũ vẫn cần v1 cho decrypt. Đây là trạng thái **decrypt-old / encrypt-new** quan trọng khi migrate.
- `newRecordReadable=true`: key v2 encrypt/decrypt thành công với AES-GCM, cùng AAD và nonce.
- `wrongVersionRejected=true`: lấy ciphertext v1 nhưng gán key version 2 khiến decryption dùng key v2/AAD v2, nên GCM tag không khớp. Không được dùng cách “thử mọi key cho đến khi giải mã được” để bỏ qua ownership và policy.
- `tamperRejected=true`: sửa authentication tag khiến `AEADBadTagException`, ứng dụng phải từ chối bản mã.
- `v1AliasRetired=true`: alias v1 bị xóa **trong đối tượng KeyStore `restored`**, không phải trong mọi bản sao.
- `oldUnavailableThroughStore=true`: lookup v1 qua **store đang dùng** thất bại, nên oldRecord không giải mã được qua đường đó. **Không phải bằng chứng crypto-erasure**: biến `keyV1`, `firstStore` và bytes đã serialize vẫn tồn tại trong chương trình demo. Sự khác biệt này rất quan trọng khi định nghĩa “destroy key”.

**Cảnh báo bài học:** Code tạo nonce ngẫu nhiên mới cho mỗi lần encrypt ở quy mô minh họa. Production với lượng ciphertext lớn phải thiết kế uniqueness/usage limits theo key và môi trường, không tin rằng ngẫu nhiên có nghĩa không bao giờ trùng. `Envelope` là record chứa mảng mutable và chưa phải parser/format sản xuất, không có defensive copy, tenant ID, record ID, key registry/IAM hay persistence dữ liệu thật.

### 12.2. Vì sao không “xóa v1 ngay sau khi tạo v2”?

Nếu gọi `deleteEntry("data-v1")` ngay khi active v2, record v1 chưa migrate sẽ không mở được qua store hiện tại. Với hệ thống thực, còn có hàng triệu bản ghi và backup chưa scan. Cần **migration plan, dữ liệu kiểm kê, read compatibility, restore test và retention policy** trước khi retire/destroy.

Nếu v1 **bị compromise**, ta có thể phải khóa quyền mới lập tức, nhưng vẫn cần đường recovery rất hạn chế để re-encrypt. Đó là một luồng khẩn cấp khác với rotation định kỳ.

### 12.3. Những việc demo cố ý không làm

Demo không cấp credential cho người dùng, không lưu key file lên disk, không ghi log audit, không dùng KMS/HSM, không tạo certificate/PKI, không bảo đảm xóa raw key khỏi RAM, không triển khai decrypt-only permissions hay trạng thái compromised thực sự. Nó cho phép bạn nhìn thấy **khác biệt giữa tạo phiên bản mới, đọc dữ liệu cũ và xóa alias khỏi lookup**; sản phẩm thực tế cần ranh giới quyền và quản lý trạng thái trên server.

## 13. Các lỗi phổ biến, hậu quả và cách khắc phục

| Lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Hard-code key/password trong source, image hoặc env chia sẻ | Rò cho nhiều bản deployment, khó rotate | KMS/HSM/secret manager, IAM/ACL, distribution an toàn |
| Sinh AES bằng `Random`, `Math.random()`, timestamp | Key có thể đoán được | `KeyGenerator`/`SecureRandom` hoặc key generation trong HSM/KMS |
| Cất private key và certificate vào public repository | Ai đọc repository có thể giả mạo chủ thể | Public certificate được phép phát, private key phải bí mật |
| Dùng cùng raw key giữa prod và test | Compromise môi trường test ảnh hưởng prod | Tách key material và policy theo môi trường |
| Dùng một key chung mọi tenant | Vượt ranh giới dữ liệu nếu quyền decrypt quá rộng | Key scoping/authorization theo tenant và context |
| Cấp quyền runtime `DeleteKey`/admin | Lỗi app có thể làm mất toàn bộ dữ liệu | Least privilege, separation of duties, approval |
| Mặc định mọi key version đều được encrypt | App ghi record mới bằng key đã retire | Trusted active-version registry, phép encrypt bị ràng buộc state |
| Rotation nhưng quên đưa key version vào envelope | Không biết dùng khóa nào để decrypt lịch sử | Versioned/authenticated metadata, lookup có policy |
| Xóa khóa cũ trước migration | Dữ liệu backup/record v1 không khôi phục | Reader hỗ trợ old versions, checkpoint và kiểm hết dependencies |
| Tưởng rewrap xong sẽ cứu dữ liệu nếu DEK lộ | DEK đã bị lộ vẫn decrypt bản mã cũ | Rotate DEK + re-encrypt + incident response |
| Dùng lại AES-GCM nonce dưới cùng key | Nguy cơ phá confidentiality và authenticity | Nonce uniqueness, giới hạn use, coordination qua restart |
| “Disable” rồi coi attacker không còn key | Bản copy/cached raw key ngoài KMS còn dùng được | Scope compromise, re-encrypt, revoke credentials và điều tra |
| Bấm delete ngay để chặn sự cố | Mất khả năng re-encrypt/recover trong incident | Containment có kiểm soát trước, hủy theo runbook |
| Coi `KeyStore.deleteEntry` là secure wipe | Key vẫn còn ở backup, memory, bản clone | Truy vết tất cả key material, hủy theo assurance mục tiêu |
| Chỉ đổi cert mà không kiểm TLS trust chain/reload | Server/client vẫn chạy cấu hình cũ, lỗi handshake | Test thật, rollout overlapping, revocation theo PKI |
| Log raw key hoặc `getEncoded()` | Mất bí mật và tạo bản sao khó xóa | Chỉ log key ID/version/operation/outcome |
| Dùng key ID client gửi để cho phép decrypt bất kỳ | Cross-tenant data access | Binding key↔tenant↔resource, server-side authorization |
| Không có backup key | Mất key = mất khả năng đọc ciphertext | Backup/restore drill theo chính sách, bảo vệ mọi bản sao |
| Không theo dõi expiration/cryptoperiod | Key sử dụng vượt hạn/không có owner | Inventory + alerts + scheduled/event-driven rotation |
| Mở lại backup keystore có key đã revoke | Vô tình tái kích hoạt key bị lộ | Restore phải áp dụng lại denylist/status từ registry đáng tin |

## 14. Tình huống luyện tập có đáp án

**Tình huống A – ứng dụng v1 → v2 nhưng không đọc được bản ghi cũ.**  
Nguyên nhân có thể là bỏ key version, xóa v1 quá sớm, hoặc parser chỉ hỗ trợ active key. Giải pháp: versioned envelope, v1 decrypt permission cho giai đoạn migration, reader-first rollout, dữ liệu inventory và QA trước retirement.

**Tình huống B – KEK trong KMS được rotate nhưng wrapped DEK cũ chưa rewrap.**  
Nếu policy hỗ trợ các key versions cũ cho unwrap thì dữ liệu có thể vẫn đọc, nhưng còn dependency với key material cũ; cần biết đúng semantic provider và lên kế hoạch rewrap/retire. Không đồng nhất “KMS auto-rotation” với “mọi ciphertext đã thay sang key material mới”.

**Tình huống C – một nhân viên cũ có bản sao raw AES key.**  
Rút IAM/KMS grant sẽ chặn API ở hệ thống hiện tại nhưng **không làm bản sao raw key mất tác dụng**. Cần xác định ciphertext liên quan, key exposure window, re-encrypt bằng key mới, hạn chế bản sao và xem quyền/secret bị lộ khác.

**Tình huống D – khóa ký đã hết hạn nhưng cần xác minh hợp đồng cũ.**  
Ngừng dùng private key ký mới; giữ public key/certificate và bằng chứng thời điểm ký theo policy lưu chữ ký dài hạn. Nếu private key bị lộ, không chỉ nhìn `Signature.verify == true` mà phải xét trạng thái compromise/time evidence/trust.

**Tình huống E – để “xóa dữ liệu” chỉ xóa DEK ở keystore chính.**  
Phải kiểm wrapped copies, backup, replicas, process memory, snapshot và chính sách recovery; còn cần xử lý bản copy plaintext/data indexes. Một thao tác delete alias không chứng minh tất cả bí mật đã biến mất.

**Tình huống F – khi deploy v2, một node cũ vẫn encrypt bằng v1.**  
Cần active-version registry, enforcement của key-management layer, deploy reader-first và quan sát audit số lần encrypt theo version; nếu có writer v1 phải fix trước khi retire.

**Tình huống G – có 10.000 record được mã hóa AES-GCM bằng cùng key, nonce ngẫu nhiên mỗi lần. Có thể mặc định an toàn mãi không?**  
Không. Phải tuân thủ giới hạn nonce/usage của GCM theo độ lớn hệ thống, xác suất trùng, concurrency/restart, threat model và tiêu chuẩn provider. Khi cần, dùng nonce counter được điều phối và rotate key trước giới hạn.

**Tình huống H – tenant A muốn decrypt record tenant B bằng cách thay `keyId` trong request.**  
Backend phải từ chối dựa trên tenant/resource authorization và key binding, dù keyId hợp lệ và key tồn tại trong KMS. Cryptography không tự làm business authorization.

## 15. Checklist vận hành trước khi go-live

- [ ] **Inventory:** mọi key có owner, purpose, algorithm, ID/version, môi trường, trạng thái, cryptoperiod, dependencies?
- [ ] **Generation:** được sinh bằng CSPRNG/HSM/KMS thích hợp, kích thước phù hợp, không seed cố định hoặc export không cần thiết?
- [ ] **Provisioning:** key được cấp tới đúng service/tenant, có IAM least privilege, approval và kênh phân phối bí mật?
- [ ] **Storage:** file keystore/password tách rời, backup được mã hóa, HSM/KMS policy rõ, restore đã được thử?
- [ ] **Usage:** encryption key được chọn bởi active-version registry, AAD/nonce hợp lệ, authentication tag được kiểm, quyền decrypt theo đối tượng?
- [ ] **Rotation:** có reader-first rollout, old decrypt-only, migration/rewrap/re-encrypt khi cần, xác nhận không còn old dependencies?
- [ ] **Revocation:** có incident runbook về suspected compromise, rút quyền, đánh giá bản sao và dữ liệu/chữ ký đã bị ảnh hưởng?
- [ ] **Destruction:** biết điều kiện hủy, retention/legal hold và cách chứng minh key không còn bản sao khả dụng?
- [ ] **Audit:** ghi metadata không nhạy cảm, giám sát use anomalies, truy vết create/activate/rotate/disable/delete?
- [ ] **Recovery:** có kế hoạch mất KMS/HSM, mất keystore password, disaster recovery, key escrow phù hợp và kiểm lại trạng thái revoked sau restore?

**Câu kết luận cần nhớ:** Sinh ra một khóa an toàn chỉ là bước đầu. **Cấp phát đúng đối tượng, lưu giữ đúng nơi, kiểm soát sử dụng, xoay phiên bản an toàn, thu hồi có ngữ nghĩa và hủy khi hết phụ thuộc** mới tạo thành vòng đời khóa đáng tin cậy.

## Tài liệu chính thống

- [NIST SP 800-57 Part 1 Rev. 5 – Recommendation for Key Management: General](https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final): key types, trạng thái vòng đời, cryptoperiod, tạo/backup/thu hồi/hủy key.
- [OWASP Key Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html): key inventory, least privilege, storage, key rotation và monitoring.
- [OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): key handling, AEAD, key storage, ciphertext và key lifecycle.
- [Java SE 17 – KeyGenerator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyGenerator.html): tạo secret keys theo thuật toán.
- [Java SE 17 – KeyPairGenerator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyPairGenerator.html): sinh keypairs.
- [Java SE 17 – SecureRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html): nguồn ngẫu nhiên mật mã cho key material và nonce.
- [Java SE 17 – KeyStore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): `SecretKeyEntry`, `PrivateKeyEntry`, `setEntry`, `getEntry`, `store/load` và `deleteEntry`.
- [Java SE 17 – Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html) và [GCMParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): AES-GCM, tag, nonce, AAD và authentication.
- [JDK 17 keytool documentation](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): tạo, quản lý key pair/certificate và keystore dạng PKCS12.
- [NIST SP 800-38D – Galois/Counter Mode](https://csrc.nist.gov/pubs/sp/800/38/d/final): đặc tính AEAD và yêu cầu về nonce/IV AES-GCM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-lifecycle-003">11.3 Không đặt khóa bí mật trong mã nguồn, bản đóng gói hoặc nhật ký</a>

<details>
<summary>Xem chi tiết</summary>

# 11.3 Không đặt khóa bí mật trong mã nguồn, bản đóng gói hoặc nhật ký

**Khóa bí mật không được xuất hiện dưới dạng giá trị có thể sử dụng trực tiếp trong source code, mã đã biên dịch, gói triển khai hay log.** Đây là nguyên tắc căn bản của **vòng đời khóa**: khóa phải được tạo, cấp phát, sử dụng, xoay vòng và thu hồi qua một kênh kiểm soát riêng với mã ứng dụng.

Quy tắc này bao gồm khóa mã hóa đối xứng (AES, HMAC), private key của cặp khóa, khóa ký JWT, API token, mật khẩu database, credential của cloud, secret webhook, session key và các bí mật xác thực khác. Một giá trị có tên `password` hoặc `key` chưa chắc là bí mật; ngược lại, chuỗi vô danh có quyền truy cập production vẫn là secret dù được đặt tên `configValue`.

Bài này tập trung vào ba nơi rò rỉ thường bị đánh giá thấp:

1. **Mã nguồn và lịch sử kiểm soát phiên bản:** hard-code literal, config, fixture và commit.
2. **Bản đóng gói và đường ống build:** `.class`, JAR/WAR, resource, container image/layer, artifact CI/CD.
3. **Nhật ký và hệ thống quan sát:** log, trace, dump, request/response, exception, metrics và công cụ hỗ trợ.

Đích đến không chỉ là “xóa một dòng chứa khóa” mà là **thiết kế để cùng một artifact có thể triển khai vào nhiều môi trường mà không chứa secret của môi trường nào**, đồng thời có thể thu hồi/rotate khóa khi cần.

## 1. Khái niệm: secret khác key reference, public key và metadata

| Thành phần | Có thường là bí mật? | Xử lý khuyến nghị |
| --- | --- | --- |
| **AES key / HMAC key / JWT signing secret** | **Có.** | Giữ trong KMS/HSM hoặc secret manager phù hợp; giới hạn quyền sử dụng; không serialize ra log. |
| **Private key** | **Có.** | Ưu tiên private key không thể export nếu có HSM/KMS phù hợp; chỉ cấp quyền ký/giải mã cần thiết. |
| **API key / bearer token / DB password** | **Có.** | Cấp phát động hoặc lấy từ secret store; scope hẹp, thời hạn ngắn, theo dõi và thu hồi được. |
| **Password-hashing pepper** | **Có.** | Giữ ngoài database của password verifier, thường trong secret manager/HSM. |
| **Public key / chứng chỉ công khai** | Thường **không**. | Có thể được phân phối công khai nhưng phải kiểm tra nguồn tin cậy, tính đúng đắn và vòng đời. |
| **Salt của password hashing / AES-GCM IV** | Thường **không**. | Có thể lưu cùng dữ liệu tương ứng; vẫn phải tuân thủ quy tắc thuật toán (như nonce uniqueness). |
| **Key ID / version / alias** | Thường **không**, nhưng có thể là metadata nhạy cảm theo nghiệp vụ. | Chỉ ghi vào log nếu chính sách cho phép; không nhầm ID với secret thực. |
| **Đường dẫn đến secret đã mount** | Không phải nội dung secret, nhưng có thể tiết lộ cấu trúc hệ thống. | Giữ trong cấu hình triển khai, không đưa giá trị khóa vào đường dẫn hay tên file. |

**Encoding không làm secret an toàn.** Base64, hex, URL encoding, nối chuỗi, đảo thứ tự ký tự hoặc XOR một hằng số rồi giải mã lúc runtime chỉ làm khó đọc qua mắt người. Nếu artifact chứa toàn bộ vật liệu cần để khôi phục khóa, bên có artifact cũng có thể khôi phục.

**Hash của secret không tự động vô hại.** Với token ngẫu nhiên entropy cao, lưu digest có thể là một dạng verifier thích hợp. Với mật khẩu entropy thấp, hash nhanh không thay password KDF. Hash có thể làm lộ khả năng so sánh bản ghi, định danh hoặc hỗ trợ tấn công offline tùy ngữ cảnh. Không ghi hash/digest của bí mật vào log chỉ để “chứng minh đã che”.

## 2. Mô hình đe dọa: ba nơi secret có thể bị sao chép

~~~text
Developer / hệ thống quản lý secret
          |
          +-- SOURCE REPO ---------------------+
          |   Java literals, YAML, .properties, .env,
          |   test fixture, Git history, pull request,
          |   forks, clones, code review, cache
          |
          +-- BUILD / PACKAGE -----------------+
          |   .class, JAR/WAR, resources, fat JAR,
          |   Docker image/layers, build arguments,
          |   CI logs, artifacts, SBOM metadata
          |
          +-- RUNTIME / OBSERVABILITY ---------+
              System.out, SLF4J/JUL, HTTP debug,
              APM spans, crash/heap dumps, traces,
              exception messages, request snapshots

Các nơi sao chép có thể tồn tại rất lâu sau khi source đã được sửa.
~~~

Đừng chỉ kiểm tra file `src/main/java` rồi kết luận ứng dụng an toàn. Rủi ro nằm ở **mọi nơi secret đã đi qua**, không chỉ nơi developer đang nhìn thấy.

### 2.1. Vì sao repository riêng tư vẫn không phải secret vault?

Private repository giới hạn một số người truy cập, nhưng không thay thế quản lý secret vì:

- Nhiều developer, CI runner, bot, công cụ kiểm tra, fork và bản sao local có thể đọc lịch sử.
- Pull request, diff, notification hoặc review tool có thể lưu bản sao riêng.
- Người được quyền đọc code **không nhất thiết** được quyền dùng API key production.
- Khi secret bị commit, việc xóa nó ở commit mới **không xóa commit cũ**; lịch sử, tag, branch, clone và artifact có thể vẫn chứa nó.
- Trường hợp repository từng công khai hoặc token bị gửi qua kênh ngoài, phải giả định secret có thể đã bị khai thác.

Hãy coi việc secret thực xuất hiện trong Git là **sự cố lộ secret cần đánh giá và xoay vòng/thu hồi**, không chỉ là vấn đề style.

## 3. Không hard-code khóa vào mã nguồn Java

### 3.1. Anti-pattern: literal bí mật trong code

**Ví dụ sai — chỉ dùng chuỗi giả minh họa, không có credential thật:**

~~~java
// ANTI-PATTERN: không triển khai kiểu này.
private static final String DB_PASSWORD = "DEMO_NOT_A_REAL_PASSWORD";
private static final String API_TOKEN = "DEMO_NOT_A_REAL_TOKEN";

// Không an toàn hơn: bí mật được ghép lại khi runtime.
String token = "DEMO_" + "NOT_A_REAL_TOKEN";
~~~

Trong mã thực, một hằng như thế có thể tồn tại trong source, Git history và bytecode `.class`. Mức `private`, `static final` hoặc đóng gói vào thư viện nội bộ **không cung cấp bảo vệ mật mã**.

Những biến thể vẫn sai:
- Lưu bí mật trong source bằng Base64 rồi `decode` lúc startup.
- Chia secret thành nhiều fragment ở các lớp khác nhau rồi nối lại.
- “Mã hóa” secret trong resource nhưng để **khóa giải mã** cùng artifact.
- Giấu mật khẩu trong annotation, enum, unit test, test resource hoặc SQL migration.
- Để fallback production secret trong code khi không lấy được từ secret store.
- Commit một `application.properties` hoặc `application.yml` chứa khóa thật, kể cả file chỉ được dùng ở một môi trường.
- Dùng biến môi trường nhưng **gán giá trị thật ngay trong Dockerfile, startup script được commit hoặc pipeline YAML**.

Một repo có thể chứa **tên cấu hình, key reference, mô hình cấu trúc và ví dụ giá trị giả**. Nó không nên chứa secret có hiệu lực.

### 3.2. Phân biệt cấu hình tham chiếu và giá trị bí mật

**Ví dụ cấu hình chỉ có định danh, không chứa secret:**

~~~yaml
security:
  crypto-key-ref: payments/data-key/current
  token-secret-ref: payments/api-token/current
  key-policy: AES-GCM-128
~~~

`payments/data-key/current` là **alias/reference giả lập** phục vụ minh họa. Trong hệ thống thật, ứng dụng dùng identity được cấp để gọi KMS/secret manager tương ứng, lấy secret tạm thời hoặc yêu cầu dịch vụ thực hiện thao tác mật mã mà **không xuất key raw**.

Điểm khác biệt:

~~~text
Sai:
  application.yml chứa raw AES key hoặc API token
  code đọc YAML rồi dùng raw secret

Đúng hướng:
  application.yml chứa reference, algorithm policy, key version
  workload identity -> KMS/secret manager -> quyền sử dụng phù hợp
  app chỉ nhận secret khi thật sự cần, không ghi lại giá trị
~~~

Không đưa **credentials để truy cập secret manager** vào cùng source; nếu làm vậy chỉ chuyển hard-code sang một lớp khác. Dùng workload identity, managed identity, service account được cấp quyền theo môi trường hoặc cơ chế trust/bootstrap đã được kiểm chứng.

### 3.3. `.gitignore` không phải giải pháp cho secret đã commit

`.gitignore` chỉ giúp Git **không tự đưa các file chưa được track** vào danh sách theo pattern, chứ không làm một file đã track tự biến mất khỏi lịch sử. Nó cũng không kiểm soát file copy vào JAR, ZIP hoặc Docker image.

Một hướng dùng hợp lý:

~~~gitignore
# Ví dụ chính sách bỏ qua local config có secret
.env
.env.*
!.env.example
local-secrets/
*.local.properties
~~~

Nhưng vẫn phải **kiểm tra cấu hình build**: một file nằm ngoài Git vẫn có thể bị task `processResources`, Docker `COPY . .` hoặc bước gom artifact khác đưa vào bản đóng gói. `.env.example` nên chứa **tên biến và mô tả**, không có secret hoạt động. Không đưa dữ liệu production thật làm test fixture.

Các file cấu hình an toàn cần được code review/scan. Tránh dùng heuristic “nó nằm dưới `test/` nên không sao”; CI artifact và test report cũng có thể được tải xuống.

## 4. Bí mật có thể lộ trong `.class`, JAR/WAR và container image

### 4.1. Java đã biên dịch vẫn có thể bị quan sát

Khi code chứa một chuỗi literal, trình biên dịch có thể đưa chuỗi đó vào constant pool hoặc cấu trúc bytecode khác. Người có `.class`, JAR hoặc ứng dụng chạy được có thể dùng công cụ xem/decompile để khôi phục những giá trị không được bảo vệ.

- JAR/WAR là những định dạng đóng gói có thể mở và liệt kê resource.
- `application.properties` trong `src/main/resources` thường trở thành resource trong artifact nếu build không loại trừ.
- `static final` làm code bất biến ở mức Java, không phải trở thành nơi lưu bí mật chống phân tích.
- Obfuscation, minification, shading hoặc đóng gói “fat JAR” không thay cho quản lý key.
- Chữ ký JAR/code signing chứng minh tính toàn vẹn/nguồn phát hành theo cơ chế trust phù hợp, **không mã hóa** nội dung JAR.

Các lệnh kiểm tra artifact có thể dùng trên **bản build do đội sở hữu**:

~~~bash
jar tf application.jar
javap -verbose -classpath application.jar com.example.AppConfig
~~~

`jar tf` liệt kê entry của JAR; `javap -verbose` giúp quan sát bytecode/constant pool trong class phù hợp. Chúng không thể khẳng định mọi secret đã/không xuất hiện: secret có thể ở resource, native library, image layer, artifact khác hoặc được xây dựng động. Dùng thêm hệ thống secret scanning phù hợp thay vì chỉ tìm chuỗi bằng mắt.

### 4.2. Lớp Docker image, Dockerfile và biến build

**Anti-pattern:**

~~~dockerfile
# SAI: không thay phần này bằng credential thật.
ARG APP_SECRET=DEMO_NOT_A_REAL_SECRET
ENV APP_SECRET=DEMO_NOT_A_REAL_SECRET
COPY local-secrets/ /app/secrets/
~~~

Hệ quả: giá trị hoặc file có thể xuất hiện trong định nghĩa build, metadata/image configuration, filesystem layer hoặc cache. Xóa file ở một `RUN` hoặc layer sau **không đảm bảo** xóa bản cũ khỏi mọi layer, cache và registry.

Các biện pháp tốt hơn:
1. Image được build **không chứa khóa của môi trường**.
2. Chỉ mount/provision secret **ở runtime** qua platform secret store, volume bảo vệ hoặc SDK.
3. Nếu build thật sự cần secret, sử dụng **secret mount dành cho build** (ví dụ BuildKit secret mounts) và xác nhận command không copy/log secret vào output layer, cache hay build artifacts.
4. Không truyền secret qua Docker `ARG`/`ENV` trong image definition để tiện build.
5. Kiểm tra cả image layers, base image, bản artifact được đẩy lên registry và pipeline logs.
6. Giới hạn quyền đọc secret cho đúng workload; một secret mount vẫn có rủi ro nếu container/service bị chiếm quyền.

**Đưa bí mật vào image được mã hóa** chỉ có ý nghĩa khi cơ chế giải mã/quản lý khóa được thiết kế đúng và khóa giải mã **không nằm cùng image**. Trong phần lớn ứng dụng, không cần đưa secret vào artifact ngay từ đầu.

### 4.3. CI/CD và đường rò phụ

- Pipeline YAML, script triển khai, command line và lịch sử shell có thể lưu secret. Truyền credential qua command argument như `-Ddb.password=...` có thể lộ trong process list/diagnostics/log; tránh nếu có cơ chế cung cấp an toàn hơn.
- CI/CD logs có thể hiển thị output của `echo`, `set -x`, stack trace, thông báo lỗi của lệnh bên thứ ba hoặc dump môi trường.
- Masking secret trong CI là **lớp phòng vệ phụ**: các bản biến đổi/encode/chia nhỏ secret, định dạng đa dòng và lỗi cấu hình có thể vượt qua masking.
- Debug artifact, test report, screenshot, coverage upload, crash dump, build cache hay SBOM metadata đều có thể chứa thông tin không chủ định.
- Workflow chạy từ PR/fork chưa tin cậy không được trao secret production nếu không có policy cô lập và phê duyệt thích hợp.
- Không dùng cùng credential quyền cao cho mọi pipeline, môi trường dev/staging/prod hoặc tất cả microservice.

Các secret được tạo bởi pipeline nên có **scope nhỏ, TTL ngắn**, chỉ có mặt ở job cần thiết, audit được và có khả năng thu hồi. Với tích hợp cloud, ưu tiên **federated workload identity/short-lived credentials** thay vì long-lived token hard-code trong CI khi hệ thống hỗ trợ.

## 5. Không ghi khóa bí mật vào nhật ký hoặc hệ thống quan sát

Log được nhân bản rất rộng: stdout container, tập trung log, agent shipper, SIEM, APM, service tracing, alert, ticket đính kèm và backup. Người có quyền xem log **không nhất thiết** được quyền đọc private key hoặc token production.

### 5.1. Anti-pattern Java

~~~java
// SAI: các ví dụ minh họa, đừng đưa vào production.
logger.info("apiToken={}", apiToken);
logger.debug("AES key={}", Arrays.toString(keyBytes));
logger.info("Auth header={}", request.getHeader("Authorization"));
logger.error("Connection string: {}", connectionUrlWithPassword);
System.out.println(secretKey.getEncoded());
~~~

Có cả rủi ro **gián tiếp**:

- `logger.debug("request={}", request)` nếu `toString()` của object bao gồm Authorization header, password hoặc body.
- Ghi toàn bộ HTTP request/response trong filter/interceptor, reverse proxy, HTTP client wire debug.
- Serializing object cấu hình thành JSON đưa vào log, trace span attributes hoặc MDC/ThreadContext.
- Lấy `Throwable.getMessage()` hoặc stack trace của thư viện chứa URL có `user:password@host`, connection string hoặc giá trị token.
- Ghi lỗi OAuth callback, URL reset password hoặc query string chứa credential.
- `printStackTrace()`, crash dumps, heap dumps, profiler có thể chứa bản sao của secret trong bộ nhớ; giới hạn quyền truy cập, retention và nơi lưu.
- Ghi một digest/partial secret theo cách cho phép liên kết thông tin nhạy cảm hoặc giúp người ngoài nhận biết credential đang dùng.
- Ghi full HTTP body khi thấy validation error, kể cả request chứa password, OTP, bearer token.

Không in key raw cả khi “chỉ chạy local một lần”. Local console có thể được IDE, terminal logging, screen-sharing, CI và nhật ký hệ thống lưu lại.

### 5.2. Thay logging giá trị bằng logging sự kiện có danh sách cho phép

Một sự kiện hữu ích cho giám sát key lifecycle có thể chứa:

| Trường nên cân nhắc | Mục đích | Lưu ý |
| --- | --- | --- |
| `event=key_use` | Phân biệt truy cập secret, rotate, deny, revoke. | Không kèm key raw. |
| `request_id` / correlation ID | Ghép vết ở các service. | ID phải sinh độc lập, không chứa token. |
| `key_ref` hoặc key version được phê duyệt | Xác định alias/version có vấn đề. | Chỉ dùng nếu metadata này được policy cho phép. |
| `operation=encrypt/decrypt/sign` | Quan sát loại thao tác. | Không log plaintext hay nội dung cần ký. |
| `outcome=success/denied/error` | Phân tích lỗi và bất thường. | Không dựa trên việc in exception chứa secret. |
| `error_code` đã chuẩn hóa | Chẩn đoán mà không lộ nội bộ. | Tránh free-form message từ thư viện chưa kiểm soát. |

Nguyên tắc: **chỉ log những trường đã được xét duyệt (allowlist)**, thay vì serialize toàn bộ object rồi cố xóa một vài trường nhạy cảm (denylist). Các định danh cũng cần kiểm tra/normalize để tránh log injection và tránh nhúng dữ liệu từ attacker.

**Không coi redaction là tuyệt đối.** Các secret có thể xuất hiện trong chuỗi đã encode, nested JSON, multiline message hoặc trường chưa từng được định nghĩa. Logging tối thiểu ngay từ nguồn đáng tin cậy hơn mask sau khi đã phát tán log.

### 5.3. Java 17+: audit an toàn, không in secret

Tệp **`AllowlistAuditDemo.java`** sinh một mảng ngẫu nhiên **chỉ để minh họa secret trong bộ nhớ**. Chương trình xuất một event gồm các field được chọn, tuyệt đối không đưa mảng secret vào formatter. Đây không phải secret-manager SDK và không chứng minh quy trình authentication hoàn chỉnh.

~~~java
import java.security.SecureRandom;
import java.util.Arrays;

public class AllowlistAuditDemo {
    static String safeEvent(
            String requestId, String keyReference, String outcome) {
        return "event=key_use request_id=" + requestId
                + " key_ref=" + keyReference + " outcome=" + outcome;
    }

    public static void main(String[] args) {
        byte[] demonstrationOnly = new byte[32];
        new SecureRandom().nextBytes(demonstrationOnly);
        try {
            if (demonstrationOnly.length != 32) {
                throw new IllegalStateException("Invalid demonstration material");
            }

            System.out.println(
                    safeEvent("req-001", "demo-key-v1", "success"));
        } finally {
            Arrays.fill(demonstrationOnly, (byte) 0);
            System.out.println("Buffer cleared: "
                    + Arrays.equals(demonstrationOnly, new byte[32]));
        }
    }
}
~~~

~~~bash
javac AllowlistAuditDemo.java
java AllowlistAuditDemo
~~~

Stdout chính xác:

~~~text
event=key_use request_id=req-001 key_ref=demo-key-v1 outcome=success
Buffer cleared: true
~~~

**Giải thích:** `demo-key-v1` chỉ là reference giả và các field đã được chọn. Chương trình không in nội dung 32 byte; `Arrays.fill` chủ động ghi đè **mảng byte do ứng dụng kiểm soát**, không có nghĩa đã xóa mọi bản sao bí mật trong JVM, native memory, swap hoặc thư viện khác. Không có cơ chế Java tổng quát bảo đảm mọi secret đã được xóa khỏi mọi nơi lưu.

Một hệ thống thật nên kiểm tra độ dài, cấu trúc và tính an toàn của `requestId`, `keyReference`, `outcome` trước khi ghi log, để attacker không chèn newline hay thông tin nhạy cảm trong những trường này.

## 6. Kiến trúc cấp secret đúng: tách bản đóng gói khỏi môi trường

Mô hình thực hành khuyến nghị:

~~~text
[Git repository]                  [CI/CD build]
  code, schema, key references ---> compile/test/package
  không có raw secret                 |
                                      v
                              [Image/JAR không chứa secret]
                                      |
                                      v
                      [Runtime: workload identity/service account]
                                      |
                                      v
                         [Secret Manager / KMS / HSM]
                          IAM, version, audit, TTL, rotate
                                      |
                         +------------+------------+
                         |                         |
           key handle / encrypt operation    runtime short-lived secret
             private key không export             cấp khi thật cần
                         |                         |
                         +------------+------------+
                                      |
                                [Ứng dụng]
                     chỉ thao tác được phép; không log secret
~~~

Hệ thống không nhất thiết phải **đưa raw AES/private key vào JVM**. Với HSM/KMS, ứng dụng có thể sử dụng API **sign/decrypt/encrypt/wrap** mà private key/master key không rời phần cứng hoặc dịch vụ. Nếu hệ thống cần data key trong bộ nhớ cho mã hóa local, phải quản lý thời hạn, quyền cấp, nonce, bộ nhớ, rotation và xóa bản sao có thể xóa theo policy.

### 6.1. Các phương án cấp bí mật

| Phương án | Ưu điểm | Giới hạn/rủi ro |
| --- | --- | --- |
| **KMS/HSM key handle** | Giới hạn việc export key, central audit và policy; ứng dụng dùng operation thay vì nhận raw key khi có hỗ trợ. | Phụ thuộc provider/latency/quota, cần IAM và quản lý key version/recovery. |
| **Secret manager qua SDK** | Cấp secret theo identity, version, TTL và audit; có thể rotate/revoke tập trung. | Raw secret vẫn có thể vào process memory; cần bảo vệ cache, log, retry và exception. |
| **Secret volume/file mount ở runtime** | Không cần đóng secret vào JAR/image; có thể dùng với nhiều framework. | Cần ACL, namespace, volume policy và xử lý rotation/mount updates. File vẫn có thể bị đọc nếu workload bị chiếm quyền. |
| **Environment variable do runtime inject** | Dễ cấu hình trong nhiều platform, giảm hard-code source. | Giá trị có thể hiện trong process environment, diagnostics, dump, subprocess và log; không phải kênh mặc định an toàn nhất cho mọi secret. |
| **External encrypted config** | Có thể hữu ích khi quản lý khóa giải mã và quyền tách biệt. | Nếu cùng artifact chứa ciphertext **và** khóa giải mã, bảo vệ gần như vô nghĩa. |
| **Khóa sinh lúc startup chỉ để dùng tạm** | Phù hợp với session/ephemeral key không cần persist, giảm lưu khóa dài hạn. | Không phù hợp dữ liệu cần giải mã sau restart nếu không có cơ chế lưu/truy xuất khóa đúng. |

Chọn theo mô hình đe dọa và yêu cầu tuân thủ. **Biến môi trường tốt hơn hard-code trong source** ở khía cạnh tách cấu hình, nhưng không tự động tương đương secret manager/HSM; cân nhắc SDK/identity hoặc secret mount được kiểm soát khi có thể.

### 6.2. Không hard-code thông tin đăng nhập để lấy secret

Sai lầm bootstrap phổ biến:

~~~text
Ứng dụng muốn lấy DB password từ secret manager
  -> hard-code SECRET_MANAGER_ADMIN_TOKEN trong source
  -> gọi secret manager bằng token đó

Kết quả: secret production vẫn bị nhúng, chỉ chuyển sang lớp khác.
~~~

Cách đúng hướng là dùng **định danh workload** do môi trường phát hành và được xác minh bởi nền tảng. Quyền phải hạn chế theo tên secret, version, operation, môi trường và tenant khi phù hợp. Không dùng cùng quyền quản trị kho secret để chạy mọi service.

### 6.3. Secret rotation không phải chỉ restart ứng dụng

- **Static secret** thường cần tạo version mới, cập nhật consumer và vô hiệu version cũ theo trình tự an toàn.
- **Dynamic credential** có thể hết hạn theo lease; cần làm mới đúng thời điểm và tự dừng truy cập khi lease bị revoke.
- Restart chỉ làm process lấy secret mới **nếu ứng dụng được thiết kế như vậy**; nó không vô hiệu khóa cũ đã lộ và không xóa mọi bản sao/cached artifact.
- Với encryption key, cần quản lý **key ID/version**, cho phép dữ liệu cũ được decrypt trong giai đoạn chuyển đổi thích hợp, cân nhắc re-encryption và thời hạn giữ khóa cũ. Không xóa key cũ khi còn dữ liệu cần phục hồi nếu chưa có kế hoạch migration.
- Với API token hoặc password đã lộ, thường phải **revoke/rotate càng sớm càng khả thi** và cập nhật hệ thống phụ thuộc theo quy trình sự cố.

## 7. Java 17: lấy secret từ runtime mà không đưa vào source

### 7.1. Không có API Java chung cho mọi secret manager

Java chuẩn cung cấp `KeyStore`, `KeyGenerator`, `Cipher`, `SecureRandom` và APIs liên quan, nhưng **không có một lệnh JDK duy nhất** để truy vấn mọi cloud secret manager. Ứng dụng dùng SDK/plugin của nhà cung cấp hoặc provider JCA/JCE/PKCS#11 đã cấu hình.

Tư duy tích hợp:

~~~text
Cấu hình non-secret: keyRef, algorithm, tenant, policy
      |
Workload identity (không embedded API key)
      |
Secret manager / KMS / HSM SDK hoặc JCA provider
      |
Secret lease / key handle / phép sign-encrypt được cho phép
      |
Use -> audit metadata -> release/expire
~~~

**Pseudocode mô tả kiến trúc**, không phải class SDK có thật:

~~~text
keyRef = config.getApprovedKeyReference()
identity = runtime.getWorkloadIdentity()
handle = keyService.openKey(identity, keyRef, allowedOperation="encrypt")
ciphertext = handle.encrypt(plaintext, authenticatedMetadata)
audit(event="key_use", keyRef=keyRef, outcome="success")
close handle/lease; không in raw key vào bất kỳ log nào
~~~

Nếu dùng provider HSM để tạo `SecretKey`/`PrivateKey`, đối tượng khóa có thể là **opaque handle** và `getEncoded()` có thể trả `null`. Không cố gọi `getEncoded()` để log/serialize/clone key; một số provider bảo vệ khóa bằng chính việc không cho export.

### 7.2. Khi bắt buộc nhận secret dưới dạng byte/char

- Tránh chuyển secret sang `String` nếu API cho phép dùng `byte[]` hoặc `char[]` trong vòng đời ngắn. `String` là immutable và việc GC giải phóng không bảo đảm xóa bản sao trên bộ nhớ ngay lập tức.
- Dùng `try/finally` hoặc `AutoCloseable` để có cơ hội xóa mảng tạm bằng `Arrays.fill` sau khi dùng; nhưng **không tuyên bố zeroization tuyệt đối** vì còn bản sao trong provider/native memory, dump và JVM.
- Không dùng `new String(secretBytes)` chỉ để ghi log hoặc debug.
- Không chuyển secret sang Base64 để lưu trong config hay stdout: Base64 là **encoding**, không phải encryption.
- Với secret file runtime, chỉ cho principal phù hợp đọc; bảo vệ file permission/volume, giới hạn kích thước input và xử lý thiếu file bằng **fail-closed**, không fallback sang giá trị nhúng.
- Không đính mật khẩu vào command-line parameter vì process list và shell history có thể lưu lại; tránh các bước truyền qua shell không an toàn.

Một phương pháp đọc file được cấp ở runtime có thể như sau; đây là **mảnh API tích hợp**, không phải chương trình độc lập và không thay authentication/authorization của secret manager:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

static void withMountedSecret(Path mountedFile) throws IOException {
    byte[] secret = Files.readAllBytes(mountedFile);
    try {
        if (secret.length == 0) {
            throw new IllegalStateException("Required secret is empty");
        }
        // Ứng dụng thực sử dụng secret trong SDK đã được cấu hình.
        // Không in secret hoặc chuyển nó thành biến cấu hình toàn cục.
    } finally {
        Arrays.fill(secret, (byte) 0);
    }
}
~~~

Mảnh này chỉ minh họa **điểm nhận secret và xóa buffer sau khi xử lý**; trước khi dùng production còn phải bổ sung giới hạn size, kiểm soát đường dẫn, ACL, kiểm tra định dạng và operation thật. Không tạo file chứa secret trong repo để “giả lập runtime mount”. Đường dẫn đến file nên được cấu hình riêng và không tự lấy từ input của user.

### 7.3. Environment variable: giới hạn thực tế

~~~java
String secret = System.getenv("SERVICE_API_TOKEN");
if (secret == null || secret.isBlank()) {
    throw new IllegalStateException("Service credential is not configured");
}
// Dùng qua client đã cấu hình, không log, không trả về trong lỗi.
~~~

Đây là **mảnh minh họa**, không chứa secret hay client implementation. `System.getenv` trả `String`, tức là không thể chủ động xóa giá trị này khỏi mọi nơi trong JVM. Giá trị environment còn có thể bị lộ qua cấu hình platform, crash dump, process debugging hoặc thao tác log sai.

Không cho phép:

~~~java
// SAI: secret dự phòng được hard-code (chuỗi bên dưới chỉ là ví dụ giả).
String token = System.getenv().getOrDefault(
        "SERVICE_API_TOKEN", "DEMO_NOT_A_REAL_TOKEN");
~~~

Nếu nguồn secret bắt buộc không sẵn có, **từ chối khởi tạo hoặc từ chối thao tác** theo thiết kế; không fallback sang credential tĩnh.

### 7.4. `KeyStore` có giải quyết toàn bộ không?

`KeyStore` giúp quản lý đối tượng khóa/chứng chỉ theo loại store và provider, nhưng **không tự biến một file chứa khóa và mật khẩu hard-code thành an toàn**. Nếu application JAR chứa cả keystore exportable và mật khẩu mở khóa, người sở hữu artifact vẫn có thể truy xuất bí mật.

Một HSM/PKCS#11 provider có thể đại diện khóa bằng handle không export; mức bảo vệ thực tế phụ thuộc cách key được tạo, key policy, PIN/session, hardware, provider và quyền của workload. Cần phân biệt `KeyStore` như API/abstraction với **hệ thống quản lý bí mật thực sự**.

## 8. Java 17+: tạo khóa dùng tạm, không nhúng hoặc log giá trị khóa

Ví dụ **`InMemoryKeyDemo.java`** minh họa một khóa AES được sinh ngẫu nhiên tại runtime chỉ để mã hóa/giải mã ngay trong tiến trình. Chương trình không dùng hard-coded key bytes, không gọi `key.getEncoded()` để in khóa, và chỉ in metadata không bí mật.

**Đây là khóa tạm thời để học.** Dữ liệu cần decrypt sau restart đòi hỏi kiến trúc quản lý/lưu key trong KMS/keystore hoặc key-wrapping thích hợp; không thay bằng cách hard-code key vào mã nguồn.

~~~java
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class InMemoryKeyDemo {
    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();

        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128, random);
        SecretKey ephemeralKey = generator.generateKey();

        byte[] iv = new byte[12];
        random.nextBytes(iv);

        byte[] message = "example".getBytes(StandardCharsets.UTF_8);
        Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
        enc.init(Cipher.ENCRYPT_MODE, ephemeralKey,
                 new GCMParameterSpec(128, iv));
        byte[] ciphertextAndTag = enc.doFinal(message);

        Cipher dec = Cipher.getInstance("AES/GCM/NoPadding");
        dec.init(Cipher.DECRYPT_MODE, ephemeralKey,
                 new GCMParameterSpec(128, iv));
        byte[] restored = dec.doFinal(ciphertextAndTag);

        System.out.println("Algorithm: "
                + ephemeralKey.getAlgorithm());
        System.out.println("IV bytes: " + iv.length);
        System.out.println("Ciphertext+tag bytes: "
                + ciphertextAndTag.length);
        System.out.println("Round-trip OK: "
                + Arrays.equals(message, restored));
    }
}
~~~

~~~bash
javac InMemoryKeyDemo.java
java InMemoryKeyDemo
~~~

Stdout kiểm chứng:

~~~text
Algorithm: AES
IV bytes: 12
Ciphertext+tag bytes: 23
Round-trip OK: true
~~~

Giải thích:
- `KeyGenerator.getInstance("AES")` tạo **key material mới từ CSPRNG**, không có literal AES key trong source.
- `generator.init(128, random)` xác định khóa AES 128 bit; đó **không phải** kích thước IV.
- `IV` có 12 byte và được sinh từ `SecureRandom`. Bài này chỉ thực hiện một lần encryption; trong hệ thống thực, AES-GCM **phải không lặp cặp (key, IV)** ở mọi lần encrypt với cùng khóa, kể cả giữa các node và restart.
- `doFinal(message)` tạo 7 byte ciphertext + 16 byte authentication tag (tLen=128 bit), nên tổng là 23 byte; không in plaintext hoặc key thực ngoài chuỗi demo không nhạy cảm.
- `key.getAlgorithm()` trả tên thuật toán (metadata); đây không phải phương pháp xác nhận quyền truy cập hay sự an toàn của kho khóa.
- Chương trình **không persist key**, nên không thể dùng để giữ ciphertext giải mã được sau khi tiến trình kết thúc. Với key dài hạn, cấu hình đúng KMS/HSM/keystore và quản lý version/rotation là bắt buộc.

### Tại sao không ghi `SecretKey.getEncoded()`?

Với key exportable, `getEncoded()` thường trả byte raw bí mật; `Base64.getEncoder().encodeToString(key.getEncoded())` rồi in ra console **tạo lộ khóa**. Với key không exportable, `getEncoded()` có thể trả `null`, nên ép lấy byte hoặc giả định khóa luôn exportable là sai. Không log `SecretKey` object tùy tiện vì provider có thể đưa metadata hoặc representation không mong muốn vào `toString()`.

## 9. Đọc, build, test, vận hành: kiểm soát nào ở mỗi giai đoạn?

| Giai đoạn | Nguy cơ | Kiểm soát thích hợp |
| --- | --- | --- |
| **Thiết kế** | Secret dùng chung rộng, tuổi thọ quá dài, không có owner | Inventory owner/purpose/scope, least privilege, rotation/revocation policy. |
| **Lập trình** | Hard-code credential trong Java/config/test/fixture | Secrets reference, dummy fixture vô hại, code review, linter + pre-commit scanning. |
| **Git push / pull request** | Secret vào history và snapshot/fork | Server-side push protection/secret scanning, branch policy, bot không lộ credential. |
| **CI build** | Pipeline in biến, artifact chứa secret, job có quyền cao | Identity ngắn hạn, cô lập runner, mask bổ sung, scan artifacts, tránh in command có secret. |
| **Package/JAR** | Secret lọt vào `.class`, resource, manifest, fat JAR | Kiểm tra dependencies/resources, build reproducible không cần production secret, scan package. |
| **Container image** | Dockerfile ENV/ARG, secret mount sai, layer/cache | Runtime injection, BuildKit secret mount nếu cần lúc build, kiểm tra layer/registry/cache. |
| **Runtime** | Secret trong dump, process env, file đọc được | Workload identity, KMS/HSM, runtime mounts có ACL, hạn chế dump/debug và quyền process. |
| **Observability** | In full request, Authorization header, SDK exception, traces | Allowlist logging, redaction, vô hiệu wire debug nhạy cảm, retention và ACL log. |
| **Rotation** | Key cũ vẫn có hiệu lực, rollback dùng version cũ | Versioned secrets, TTL, revoke, migration plan, đo lường consumer đã đổi. |
| **Incident** | Chỉ xóa secret khỏi code nhưng credential vẫn sống | Revoke/rotate, điều tra phạm vi, dọn bản sao/artefact có kiểm soát, audit & cảnh báo. |

### 9.1. Secret scanning có tác dụng và giới hạn nào?

Scanner có thể tìm pattern API key, private-key block, credential literal, entropy bất thường và tên file nhạy cảm. Dùng ở nhiều thời điểm: pre-commit, PR/CI, source history, artifact/image scanning và monitoring log khi chính sách cho phép.

Tuy nhiên:
- **False negative**: secret được mã hóa Base64/hex, chia đoạn, tạo động, mang format tùy chỉnh hoặc nằm ở bản sao ngoài tầm quét.
- **False positive**: chuỗi test/dummy hoặc dữ liệu entropy cao nhưng không phải secret.
- Scanner phát hiện secret không tự **revoke** secret. Khi có hit thật cần mở incident workflow.
- Đừng in nguyên secret phát hiện được vào **báo cáo của scanner** hoặc issue/ticket.
- Chỉ tính toán/hiển thị fingerprint đã được thiết kế an toàn khi cần deduplicate, không mặc định hash nào cũng vô hại.

Quy trình hay dùng là **chặn trước merge/commit nếu phát hiện secret**, thêm scan định kỳ artifact và thiết lập owner chịu trách nhiệm từng loại credential.

### 9.2. Phân quyền và đa môi trường

Không dùng một khóa production cho dev/staging, QA và CI. Một bản build trung lập có thể chạy ở mỗi môi trường nhờ được cấp **identity và secret reference riêng** ở runtime. Khi staging có lỗ hổng, sự cố không nên tự động cấp quyền tới production.

Sử dụng least privilege: service chỉ được `read` secret đúng scope hoặc `encrypt/sign` bằng key handle được cấp; không mặc định có quyền `list/all`, `delete`, `rotate` hoặc `export` mọi key. Runner build cũng không cần quyền decrypt dữ liệu sản xuất chỉ vì nó đóng gói code.

## 10. Nếu phát hiện khóa đã bị lộ: thứ tự xử lý

**Tình huống:** một developer từng commit một API token hoặc AES key, sau đó xóa dòng đó khỏi branch và tạo commit mới. Dòng đã xóa không bảo đảm secret chưa bị sao chép.

### Quy trình ứng cứu thực hành

1. **Phân loại và thông báo theo quy trình nội bộ.** Xác định secret gì, owner, phạm vi quyền, môi trường, ngày tạo, nơi có thể đã lộ (repo, artifact, log, CI cache, ticket, dump).
2. **Cô lập/thu hồi hoặc rotate càng sớm càng khả thi.** Với token/API key, revoke/replace để secret cũ không thể dùng nữa. Với private/signing/encryption key, lập kế hoạch thay khóa, điều chỉnh trust/certificate, re-sign/re-encrypt dữ liệu nếu cần; chú ý mất khả năng giải mã dữ liệu cũ.
3. **Kiểm tra access logs và usage history** để xác định có sử dụng trái phép không; không truy xuất/log lại secret gốc trong quá trình điều tra.
4. **Cập nhật consumer có kiểm soát**, xác nhận tất cả service dùng version mới và không còn phụ thuộc credential cũ; tránh fallback bí mật sang khóa đã lộ.
5. **Xử lý dấu vết rò rỉ** trên source history, PR, artifact, Docker registry/cache, logs, backup, các bản sao theo kế hoạch đã phê duyệt. Việc rewrite Git history có thể phá commit references và cần phối hợp với mọi clone; **không thay cho revoke**.
6. **Giám sát sau sự cố**: cảnh báo việc dùng credential cũ, thất bại bất thường, truy cập key version cũ, dấu hiệu abuse tài nguyên hoặc bypass quyền.
7. **Cải thiện cơ chế ngăn tái diễn**: bổ sung scanner/branch policy, bỏ secret khỏi build, audit logger, thu hẹp IAM, tăng TTL/rotation automation và tài liệu cho developer.

Không tự ý xóa log/audit trước khi đáp ứng yêu cầu điều tra hoặc pháp lý; cần phối hợp security/incident response để cách ly, hạn chế truy cập và loại bỏ bản sao nhạy cảm mà vẫn giữ bằng chứng cần thiết.

### Khi nào rotate khác revoke?

- **Rotate**: cấp secret mới, chuyển consumer sang bản mới, sau đó hủy/hạn chế bản cũ theo chính sách.
- **Revoke**: làm secret cũ mất quyền ngay hoặc theo thời gian kiểm soát của hệ thống. Trong tình huống lộ secret, revoke có thể cần được ưu tiên nếu có nguy cơ đang bị khai thác.
- **Expire**: giá trị tự hết hạn theo TTL/lease, không đồng nghĩa sự cố đã được xử lý nếu secret vẫn còn hiệu lực.
- **Re-encrypt/re-wrap**: khi key mã hóa bị lộ, chỉ thay API token không giải quyết dữ liệu đã được bảo vệ bằng key cũ; cần đánh giá phạm vi ảnh hưởng.

Đừng hứa “delete secret khỏi Git là an toàn 100%”: khi không thể kiểm soát mọi bản sao, **phải làm secret cũ không còn hữu dụng** và hạn chế rủi ro còn lại.

## 11. Bảng đối chiếu: cách sai và cách sửa

| Cách làm sai | Vấn đề bảo mật | Thay bằng |
| --- | --- | --- |
| `static final String AES_KEY = "...";` | Key được nhúng trong source/bytecode. | Cấp key qua KMS/HSM hoặc secret manager/runtime key handle. |
| Lưu secret Base64 trong properties | Ai có properties đều decode được. | Không để secret trong artifact; runtime injection có kiểm soát. |
| Đưa keystore + mật khẩu keystore vào cùng JAR | Artifact đủ vật liệu để giải mã khóa. | Keystore ngoài artifact, key-handle/HSM, mật khẩu/identity cấp riêng. |
| `.gitignore` được thêm sau khi commit | Không xóa secret khỏi history/clone. | Revoke/rotate và xử lý bản sao theo incident plan; bổ sung ignore/scanning. |
| Docker `ARG`/`ENV` chứa raw secret lúc build | Secret có thể nằm trong image metadata/layers/cache. | Build secrets chuyên dụng nếu buộc phải dùng và kiểm tra output; cấp tại runtime là ưu tiên. |
| `logger.debug("config={}", config)` | Có thể serialize toàn bộ mật khẩu và token. | Log event với trường allowlist, không log object nhạy cảm. |
| Bật HTTP wire logging trong production | Authorization header/body có thể bị phát tán. | Giữ logging tối thiểu, redaction và tắt log nhạy cảm. |
| Bắt lỗi rồi in `exception.getMessage()` thẳng | Error message của SDK có thể chứa request/URL/secret. | Mã lỗi chuẩn, log có kiểm soát, trace ID và bảo vệ log. |
| Đưa credential trong `-Dtoken=...` khi chạy JVM | Có thể lộ qua process list, shell history/diagnostics. | Cơ chế runtime injection an toàn hơn, file mount hoặc SDK. |
| Dùng cùng secret cho dev và prod | Sự cố môi trường thấp lan sang prod. | Tách identity, secret scope, rotation và quyền từng môi trường. |
| Khi KMS lỗi thì fallback về key nhúng | Fail-open, sử dụng key khó quản lý/lộ. | Fail-closed và cảnh báo; không có fallback secret trong code. |
| Chỉ xóa dòng hard-code sau phát hiện | Secret cũ còn dùng được, vẫn có thể ở history/artifact/log. | Revoke/rotate, kiểm tra toàn bộ nơi rò và giám sát hậu sự cố. |
| In một phần API key để debug | Có thể làm lộ thông tin hữu ích, tạo liên kết giữa log và secret. | Dùng key reference/version được policy cho phép hoặc correlation ID riêng. |
| KeyStore có mật khẩu = “đã an toàn” | Mật khẩu/key vẫn có thể cùng artifact, quyền đọc rộng hoặc backup lộ. | Xem xét toàn bộ nơi lưu và nơi nhận key; HSM/KMS/ACL phù hợp. |

## 12. Checklist PR/CI trước khi phát hành

### Kiểm tra cho lập trình viên

- [ ] Không có **secret có hiệu lực** trong Java source, Kotlin, properties, YAML, JSON, SQL migration, test fixtures hoặc README.
- [ ] `.env` hay local secret config không được track, nhưng hiểu rằng ignore **không sửa history**.
- [ ] Không có secret trong command line, Gradle/Maven task, Dockerfile `ARG`/`ENV` hoặc output của script.
- [ ] Mọi secret production được cấp qua identity/secret manager/runtime injection, **không có fallback hard-code**.
- [ ] Không có `println`/`logger`/`toString`/JSON serialization/exception message làm lộ key, password, Authorization header hoặc token.
- [ ] Không log raw object `SecretKey`, `PrivateKey` hoặc `getEncoded()`.
- [ ] Mọi test đều dùng **dummy credential vô hiệu**, dữ liệu test không được tái sử dụng ở production.
- [ ] Xác định key alias/version, rotation, quyền đọc/operation và trách nhiệm sở hữu.

### Kiểm tra cho build/security/ops

- [ ] Pre-commit/PR scanning và CI checks chặn credential lộ; những phát hiện được xử lý mà không tái phát tán nội dung secret.
- [ ] JAR/WAR/resources/image layers/artifacts được kiểm tra và không chứa secret môi trường.
- [ ] Pipeline có quyền tối thiểu; secret chỉ cấp cho job thực sự cần, không cấp cho PR/fork không tin cậy.
- [ ] Dev/staging/prod dùng secret và identity tách biệt; có key inventory, owner, expiry và runbook rotation.
- [ ] Audit của KMS/secret store bật đúng mức; log aggregation/tracing không giữ raw secret.
- [ ] Runtime secret file có ACL, owner, rotation và bảo vệ backup/mount.
- [ ] Có runbook khi lộ token/key, gồm revoke, chuyển consumer, kiểm tra history/artifact/log và điều tra.
- [ ] Đã kiểm tra đường lỗi như KMS outage, secret hết hạn hoặc permission denied: hệ thống **không fallback sang credential tĩnh**.

## 13. Bài tập tình huống (kèm đáp án)

**Câu 1.** Một class có `private static final String SERVICE_TOKEN = "..."`, code được đưa vào private repository. Có an toàn chưa?

**Đáp án:** Chưa. Token vẫn nằm trong source, history và có thể xuất hiện trong bytecode, artifact, clone. Không nên hard-code bất kể repo private hay public.

**Câu 2.** Developer chuyển giá trị secret từ Java literal vào `src/main/resources/application.properties` rồi build fat JAR. Đã giải quyết chưa?

**Đáp án:** Chưa. Resources thường được đưa vào JAR, vẫn có thể trích xuất được. Cần tách secret khỏi artifact, cấp ở runtime.

**Câu 3.** Đổi token thành Base64 và đặt hằng `BASE64_TOKEN` trong code có đủ bảo mật không?

**Đáp án:** Không. Base64 chỉ đổi biểu diễn; ai có binary và logic giải mã có thể khôi phục. Obfuscation không tạo bí mật thực.

**Câu 4.** Một Dockerfile dùng `ARG API_TOKEN`, rồi xóa file chứa token trong layer sau. Có chắc image sạch không?

**Đáp án:** Không. Token có thể còn ở build metadata, history, layers hoặc cache, và script đã sử dụng token có thể log giá trị. Không truyền raw secret qua ARG/ENV; dùng secret mount chuyên dụng khi cần lúc build và xác minh không persist vào output.

**Câu 5.** Mã Java gọi `logger.debug("headers={}", request.getHeaders())` nhưng log chỉ bật ở DEV. Có rủi ro không?

**Đáp án:** Có. Header có thể chứa Authorization/session token và log DEV/CI cũng có người đọc, backup, APM. Dùng allowlist field và chính sách log nhất quán giữa các môi trường.

**Câu 6.** `System.getenv("DB_PASSWORD")` có tốt hơn hard-code không? Có phải an toàn hoàn toàn không?

**Đáp án:** Tốt hơn về **tách cấu hình khỏi artifact** nếu giá trị được runtime inject, nhưng environment có thể lộ trong process metadata, dump, child process, platform UI hoặc log. Ưu tiên identity/secret manager hoặc runtime mount phù hợp, giảm scope/TTL.

**Câu 7.** Đã xóa secret khỏi nhánh hiện tại và thêm vào `.gitignore`. Có cần rotate không?

**Đáp án:** Nếu secret thật có thể đã bị lộ, **cần đánh giá và thường phải revoke/rotate**. Lịch sử và bản sao không tự biến mất; ignore không thu hồi credential.

**Câu 8.** Một key KMS không thể export, nhưng app log `keyId`. Có vi phạm cùng mức với log raw AES key không?

**Đáp án:** Không nhất thiết. `keyId` thường là metadata được phép audit, còn raw key là bí mật; tuy vậy keyId/alias vẫn phải được kiểm soát theo policy và không chứa thông tin nhạy cảm.

**Câu 9.** Khi secret manager lỗi, ứng dụng chuyển sang credential đã nhúng để luôn phục vụ request. Vì sao nguy hiểm?

**Đáp án:** Fallback làm secret khó quản lý tồn tại trong artifact, bypass quyền và rotation, có thể tiếp tục truy cập bằng khóa đã lộ. Cần fail-closed, alert và quy trình availability được thiết kế từ trước.

**Câu 10.** Ghi `key.getEncoded()` sang log rồi mã hóa tập log sau đó có an toàn tương đương không ghi không?

**Đáp án:** Không. Secret đã đi qua nhiều điểm thu gom/agent/buffer và có thể bị đọc trước khi mã hóa, sao chép sang nơi khác hoặc giữ trong backup. Tốt nhất **không log raw key từ đầu**.

**Câu 11.** Một AES key bị lộ nhưng chỉ dữ liệu mới dùng key mới, dữ liệu cũ vẫn được mã hóa bằng key cũ. Đã xử lý xong chưa?

**Đáp án:** Chưa chắc. Cần đánh giá nguy cơ đối với ciphertext cũ, key version còn dùng để decrypt, phương án re-encrypt/re-wrap, phạm vi và thời điểm thu hồi theo yêu cầu phục hồi dữ liệu. Rotation của khóa mã hóa có nhiều bước hơn thay một API token.

**Câu 12.** Scanner không báo gì trong code; có thể khẳng định image và log không có secret không?

**Đáp án:** Không. Scanner có false negative và chỉ nhìn phạm vi được cấu hình. Cần kiểm tra source history, package/resources, image layers, CI artifacts và logging path, đồng thời có giám sát runtime.

## 14. Tài liệu tham khảo

- [OWASP — Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html): quản lý tạo, phân quyền, lưu trữ, cấp phát, rotation, expiration, revocation và rủi ro của environment variables.
- [OWASP — Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html): các loại thông tin cần loại trừ khỏi log, including access tokens, authentication passwords, connection strings và encryption keys.
- [OWASP — CI/CD Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/CI_CD_Security_Cheat_Sheet.html): không hard-code trong pipeline, nguy cơ artifacts/container image và least privilege.
- [MITRE CWE-798 — Use of Hard-coded Credentials](https://cwe.mitre.org/data/definitions/798): vấn đề credential được gắn cố định trong mã/đóng gói.
- [MITRE CWE-532 — Insertion of Sensitive Information into Log File](https://cwe.mitre.org/data/definitions/532.html): rò rỉ bí mật qua log và hệ quả confidentiality.
- [Java SE 17 — KeyGenerator API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyGenerator.html): sinh khóa đối xứng qua provider.
- [Java SE 17 — SecretKey API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/SecretKey.html): giao diện khóa bí mật.
- [Java SE 17 — Cipher API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): AES/GCM và cách làm việc với ciphertext/tag.
- [Java SE 17 — SecureRandom API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html): nguồn ngẫu nhiên dùng trong ứng dụng bảo mật.
- [Java SE 17 — KeyStore API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): lưu trữ/đọc key và certificate theo keystore/provider.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-lifecycle-004">11.4 Mã định danh khóa, phiên bản khóa và khả năng giải mã dữ liệu cũ</a>

<details>
<summary>Xem chi tiết</summary>

# 11.4 Mã định danh khóa, phiên bản khóa và khả năng giải mã dữ liệu cũ

Khi một hệ thống Java mã hóa dữ liệu để lưu nhiều tháng hoặc nhiều năm, câu hỏi không chỉ là **“mã hóa bằng AES-GCM thế nào?”** mà còn là **“sau khi đổi khóa, làm sao xác định đúng khóa cũ để đọc bản mã đã lưu?”**. Nếu chỉ giữ một biến `CURRENT_KEY` và thay byte khóa mỗi lần triển khai, những ciphertext cũ thường **không thể giải mã** bằng khóa mới. Thêm một bản sao key mới không tự chuyển đổi được dữ liệu cũ.

Giải pháp cần một hợp đồng bền vững gồm **mã định danh khóa (key ID)**, **phiên bản/vòng đời khóa (key version)**, **phiên bản định dạng bản mã (envelope/format version)** và chính sách **giữ khả năng giải mã dữ liệu cũ (backward decryption)**. Hệ thống có thể dùng một khóa mới cho dữ liệu phát sinh, đồng thời duy trì quyền giải mã hạn chế cho bản ghi cũ cho đến khi chúng được chuyển đổi, hết hạn hoặc bị hủy theo chính sách.

Điều cốt lõi: **key ID là tham chiếu tới khóa, không phải khóa**; **số phiên bản không tự sinh ra khóa cũ**; và **giải mã bằng khóa đúng nhưng bỏ qua kiểm tra toàn vẹn/phân quyền vẫn không an toàn**. Bài này xây dựng tư duy quản lý khóa dài hạn trong Java, dùng AES-GCM như ví dụ cụ thể và có chương trình Java 17+ kiểm chứng quá trình rotation, giữ khóa cũ, re-encrypt và xử lý dữ liệu bị sửa.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt `keyId`, `keyVersion`, `formatVersion`, phiên bản dữ liệu nghiệp vụ và thuật toán mã hóa.
2. Thiết kế bản mã/envelope có đủ metadata để chọn đúng khóa về sau: định dạng, key ID, nonce, ciphertext, tag và ngữ cảnh xác thực.
3. Mô tả trạng thái **active-for-encryption**, **decrypt-only**, **disabled/revoked** và **destroyed**, không nhầm “không dùng mã hóa mới” với “không được giải mã nữa”.
4. Tạo chính sách “encrypt bằng phiên bản hiện hành, decrypt theo phiên bản gắn trên bản mã” mà không thử ngẫu nhiên tất cả khóa.
5. Hiểu cách dùng AES-GCM và `updateAAD` để ràng buộc thông tin header/key ID, chỉ sử dụng plaintext sau khi xác thực tag thành công.
6. So sánh rotation không chuyển dữ liệu, **lazy migration**, **batch re-encryption**, **rewrap DEK/KEK** và các giới hạn của mỗi cách.
7. Xây quy trình rollout, rollback, backup/restore, audit và thu hồi khóa mà không đánh mất dữ liệu ngoài ý muốn.
8. Viết test về khả năng đọc bản cũ, data migration, thiếu khóa, đổi key ID, sửa ciphertext/tag, đổi AAD và tính độc lập của từng tenant.

## 2. Khái niệm: key ID khác key bytes và key version

Giả sử một dịch vụ lưu tài liệu:

```text
Document A (2025)  -- encrypted with --> key version 1001
Document B (2026)  -- encrypted with --> key version 1002

Key registry:
  1001 -> Key material #1, status DECRYPT_ONLY
  1002 -> Key material #2, status ACTIVE
```

Khi đọc A, hệ thống lấy thông tin `keyId=1001` được lưu bên cạnh hoặc bên trong bản mã, tra đúng khóa #1 đã được cho phép và xác thực dữ liệu. Khi tạo C, hệ thống dùng khóa #2 và ghi `keyId=1002`. Không có lý do để bắt A phải giải mã bằng key #2 chỉ vì “đã đổi key hiện hành”.

| Thuật ngữ | Ý nghĩa | Ví dụ | Điều không được suy ra |
| --- | --- | --- | --- |
| **Key material** | Giá trị khóa bí mật thực sự, có thể nằm trong KMS/HSM | 32 byte khóa AES-256, key handle | Không thể tính lại từ key ID đơn thuần |
| **Key ID** | Định danh để tìm đúng khóa hoặc key reference | `document-prod/1002`, opaque UUID, số 1002 | Không phải secret; không tự chứng minh key đáng tin |
| **Key version** | Thế hệ/version của vật liệu khóa hoặc phiên bản trong một key resource | `v1`, `v2` hoặc provider key version | Không đồng nghĩa version định dạng ciphertext |
| **Active key** | Phiên bản được policy chọn để **mã hóa mới** | `1002` | Không có nghĩa mọi dữ liệu cũ đã dùng key 1002 |
| **Decrypt-only key** | Không dùng để mã hóa mới, vẫn có quyền giải mã dữ liệu hợp lệ trong phạm vi cho phép | `1001` | Không phải “key đã bị xóa” |
| **Envelope format version** | Phiên bản cấu trúc header/nonce/tag/serialization | `formatVersion=1` | Không phải tuổi/phiên bản khóa |
| **Algorithm/profile** | Thuật toán và tham số được phép | `AES/GCM/NoPadding`, nonce 12 byte, tag 128 bit | Không để request tự chọn thuật toán yếu hơn |
| **Data/schema version** | Cách hiểu plaintext nghiệp vụ | `documentSchema=4` | Không gắn trực tiếp với key rotation |
| **KEK / DEK** | Key-encryption key và data-encryption key trong envelope encryption | KEK tại KMS, DEK per object | Không tự động có rewrap nếu dùng một key mã hóa trực tiếp |

Một tên `keyId` có thể đã chứa version, hoặc hệ thống có thể lưu `keyAlias` + `keyVersion` thành hai trường. **Không bắt buộc tách thành hai field**, nhưng cần một tham chiếu **duy nhất và bất biến tới phiên bản vật liệu khóa**. Nếu alias “current” đổi sang phiên bản mới, bản mã cũ không thể chỉ lưu alias biến động rồi bỏ version.

### Tránh ba loại “v1” trùng nhau

Ví dụ một record có:

```json
{
  "envelopeVersion": 2,
  "keyId": "documents/production",
  "keyVersion": "7",
  "documentSchemaVersion": 4
}
```

Đây là **JSON mô tả metadata**, không phải format binary của chương trình ở mục 7. Giá trị `envelopeVersion=2` cho biết cách parse package; `keyVersion=7` là thế hệ key; `documentSchemaVersion=4` quyết định deserialize plaintext. Ba phiên bản có thể thay đổi **độc lập**. Đừng dùng cùng một field `version` cho cả ba mà không ghi rõ ý nghĩa.

### Key ID có cần bí mật không?

Thông thường **không**. Key ID là định danh hoặc tham chiếu, có thể hiện diện ở header của ciphertext. Tuy nhiên nó vẫn có thể tiết lộ tên hệ thống/tenant nếu dùng tên dễ đoán; dùng opaque identifier khi cần hạn chế lộ metadata. Dù công khai, **không bao giờ đưa raw key bytes, password hoặc secret token vào header**.

ID được đọc từ bản mã chưa xác thực chỉ là **dữ liệu do input cung cấp**. Nó có thể giúp route tới key đã nằm trong allowlist, nhưng không được dùng làm quyết định quyền: attacker không thể được quyền giải mã key của tenant khác chỉ bằng cách thay header.

## 3. Mô hình vòng đời khóa và quyền thao tác

Một hệ thống lưu trữ dài hạn thường cần tách quyền **encrypt mới** khỏi quyền **decrypt cũ**. Bảng trạng thái minh họa:

| Trạng thái | Encrypt dữ liệu mới | Decrypt dữ liệu cũ | Quyết định vận hành |
| --- | --- | --- | --- |
| `PENDING` / `STAGED` | Chưa | Thường chưa | Phân phối key, cấu hình, kiểm thử mà chưa activate |
| `ACTIVE` | **Có** | **Có** | Key hiện hành; kiểm nonce uniqueness và giới hạn sử dụng |
| `DECRYPT_ONLY` | **Không** | **Có**, có điều kiện | Cho phép đọc bản mã cũ trong giai đoạn retention/migration |
| `SUSPENDED` / `DISABLED` | Không | Không trong thời gian khóa | Đóng tạm hoặc thu hồi quyền truy cập theo chính sách |
| `RETIRED` / `DESTROYED` | Không | Không; key đã mất có thể không thể khôi phục | Chỉ sau khi đã quyết định dữ liệu phụ thuộc không cần đọc nữa, hoặc đã migrate |

Tên trạng thái ở bảng là **ví dụ policy ứng dụng**, không phải tên bắt buộc của API Java hay mọi KMS. Một nhà cung cấp có thể có các trạng thái và chế độ rotation khác nhau. Đặc biệt **`DECRYPT_ONLY` phải được thực thi bằng quyền/logic thực tế**, không chỉ ghi một cột trạng thái rồi vẫn để mọi service gọi encrypt bằng khóa cũ.

Luồng rotation bình thường:

```text
giai đoạn 1:
   v1 ACTIVE -> vừa encrypt vừa decrypt

giai đoạn 2:
   v1 DECRYPT_ONLY
   v2 ACTIVE -> mọi ciphertext mới ghi keyVersion=v2

giai đoạn 3 (nếu cần):
   decrypt + authenticate record v1
   encrypt lại bằng v2 và nonce mới
   commit record v2 an toàn

giai đoạn 4:
   kiểm hết live objects, backups, snapshots, jobs, DR copies theo policy
   -> có thể disable/destroy v1 nếu không còn phụ thuộc và retention cho phép
```

Trường hợp **key bị nghi lộ** khác planned rotation. Việc giữ key cũ để decrypt có thể tiếp tục tạo rủi ro nếu kẻ tấn công cũng có key. Phải ưu tiên containment, chặn quyền không hợp lệ, đánh giá dữ liệu đã lộ, re-encrypt theo mức khẩn cấp, rotate dependencies và xử lý theo quy trình incident response. **Re-encrypt bây giờ không thể thu hồi plaintext mà attacker có thể đã đọc trong quá khứ**.

## 4. Vì sao ciphertext phải có key reference bất biến?

Ví dụ lỗi thường gặp:

```text
2025: encrypt(payload, System.getenv("APP_ENCRYPTION_KEY")) -> lưu nonce+ciphertext+tag
2026: thay APP_ENCRYPTION_KEY bằng giá trị khác, không lưu key ID/version
2027: gọi decrypt trên record 2025 với key mới -> tag thất bại
```

Trong AES-GCM, giải mã bằng sai key thường thất bại ở bước xác thực tag; ứng dụng không thể đoán key cũ từ ciphertext. Nếu key cũ không còn ở bất kỳ kho bảo mật/backup hợp lệ nào, **dữ liệu có thể mất vĩnh viễn**.

Mô hình đúng:

```text
record = {
  formatVersion,
  immutableKeyReference,  <-- đúng phiên bản vật liệu khóa
  nonce,
  ciphertext,
  authenticationTag
}

decrypt(record):
   parse+validate formatVersion
   resolve key via trusted key registry and authorization
   verify AEAD with nonce, ciphertext, tag and AAD
   only then interpret plaintext
```

**Không lưu key reference mơ hồ** như `keyId="current"` nếu tên đó sẽ trỏ sang key bytes khác sau rotation. Nếu provider dùng alias chuyển tiếp, cần lưu **version cụ thể** hoặc đủ metadata từ cryptographic service để provider tìm đúng phiên bản lịch sử mà không dựa vào alias hiện tại. Một số KMS quản lý version trong ciphertext nội bộ; không suy ra đó là tính năng phổ quát, cần kiểm contract API.

### Key registry cần đảm bảo điều gì?

Một registry/kho key thích hợp trả lời được:

- `keyRef` này thuộc **environment** nào (prod/staging), tenant/trust domain nào, ứng dụng và mục đích nào?
- Nó dùng thuật toán nào, có còn **decrypt permission** không, và key material có còn khả dụng không?
- Nó có phải **tham chiếu bất biến** tới key generation cần thiết không?
- Caller đã được xác thực và được cấp quyền trên **record cụ thể** chưa?
- Nếu key bị disabled/destroyed/expired, hệ thống từ chối như thế nào và phản hồi/log ra sao?
- Khi key rotation, có thể audit key version của mỗi record và đếm được record còn phụ thuộc vào key cũ không?

Key ID chỉ là **lookup key**. **Không** cho request chỉ định một path đến secret file hoặc một key URI tùy ý rồi sử dụng trực tiếp. Key registry cần allowlist và kiểm quyền server-side, tránh cross-tenant decryption và key-enumeration abuse.

## 5. Đóng gói key ID và version trong authenticated envelope

Để dễ minh họa, bài dùng một binary envelope **demo v1** cho AES-256-GCM:

```text
offset   length   field                      meaning
0        4        magic "KVR1"               marker định dạng
4        1        formatVersion = 1          phiên bản envelope
5        4        keyId (int32 big-endian)   1001 hoặc 1002 trong ví dụ
9        12       nonce                      IV/nonce GCM, sinh mới mỗi lần
21       N        ciphertext                ciphertext của N byte plaintext
21+N     16       authentication tag         tag GCM, 128 bit

header = magic || formatVersion || keyId || nonce
envelope = header || ciphertext || tag
```

Mỗi record dùng nonce 12 byte mới, tag 16 byte. `ByteBuffer` của Java mặc định **big-endian**, và spec phải cố định thứ tự byte. Đây là **định dạng do bài học định nghĩa**, không phải tiêu chuẩn tương thích tự động với mọi dịch vụ. Không dùng y nguyên cho sản xuất nếu chưa có security review, KMS integration, nonce allocation policy, giới hạn size và lộ trình migration.

Điểm quan trọng là **header phải được authenticated**. Bên encrypt đưa chính byte header vào **AAD** của AES-GCM. Bên decrypt dùng lại chính byte header. Nếu ai đó đổi `keyId=1001` thành `1002` mà không có key và khả năng tạo tag hợp lệ, phép kiểm GCM phải thất bại. Ngay cả khi hai ID vô tình cùng trỏ tới cùng vật liệu khóa, việc đổi byte header đã được AAD ràng buộc sẽ làm tag thất bại.

Ví dụ profile v1:

```text
AAD = fixedDomain || headerBytes || uint32be(contextBytes.length) || contextBytes

fixedDomain = ASCII "document-envelope:v1"
contextBytes = ngữ cảnh đã được ứng dụng xác thực và serialize nhất quán
```

`fixedDomain` giúp phân tách mục đích sử dụng. `headerBytes` ràng buộc format, key ID và nonce. `contextBytes` có thể ràng buộc document/tenant vào ciphertext. **Dữ liệu AAD không được mã hóa**, nhưng được xác thực khi tag thành công; bên giải mã phải có đúng những byte đó.

Bài demo dùng một chuỗi context cố định `tenant-7/document-42` như opaque identifier. Với nhiều trường tùy ý từ user, không được nối text tùy tiện như `tenant + "/" + document` nếu các giá trị có thể chứa dấu phân cách. Cần **canonical framing từng field** (length-prefix, schema/version, charset UTF-8, null/empty) hoặc một serialization chuẩn đã được thỏa thuận.

**Quyền kiểm tra trước, tag kiểm sau:** header parse từ record chưa đáng tin; cần kiểm format, size, key namespace và authorization theo identity/resource đã được server xác lập **trước** khi lấy key. AEAD tag thành công mới chứng minh tính toàn vẹn ciphertext/AAD. Dù tag hợp lệ, vẫn phải kiểm quy tắc nghiệp vụ và chống replay/rollback khi cần.

## 6. Quy trình ghi và đọc trong hệ thống nhiều phiên bản

### 6.1 Ghi record mới

```text
1. Xác thực caller, tenant, quyền ghi và loại dữ liệu.
2. Chọn activeKeyRef từ cấu hình/trusted key registry của service.
3. Sinh nonce/IV đúng chính sách, độc nhất đối với cùng AES-GCM key.
4. Encode header chứa formatVersion, immutable key reference, nonce.
5. Canonicalize AAD từ metadata/ngữ cảnh đáng tin; cập nhật AAD.
6. Encrypt plaintext bằng AES-GCM -> ciphertext || tag.
7. Lưu toàn bộ envelope và metadata cần khôi phục AAD.
8. Ghi audit kỹ thuật (record ID, key version, result) không lộ secret.
```

**Không** nhận `activeKeyId` do client tùy ý yêu cầu; encrypt key phải do policy server chọn. Khi rollout, phân phối key mới và quyền truy cập cho các instance trước khi chuyển active pointer, tránh node cũ không giải mã được record vừa xuất hiện.

### 6.2 Đọc record cũ

```text
1. Xác thực caller, quyền đọc đúng record/tenant.
2. Nhận envelope có bounded length và kiểm version/profile.
3. Parse key ID như một untrusted routing hint.
4. Kiểm key ID thuộc allowed key namespace, không lấy key tùy ý.
5. Resolve đúng key material/version; nếu không còn thì fail closed.
6. Init AES-GCM bằng nonce; cung cấp authenticated header và context AAD.
7. Decrypt + xác minh toàn bộ tag trong doFinal.
8. Chỉ sau thành công mới trả plaintext, parse schema, thực thi nghiệp vụ.
```

**Không** vòng lặp “thử lần lượt v2, v1, v0 cho đến khi có text đọc được”. Cách thử brute-force keys làm lỗi vận hành mơ hồ, tăng attack surface, latency và có thể sinh decryption oracle. Thiết kế có key reference rõ ràng giúp **chọn chính xác một phiên bản được policy cho phép**.

Nếu record thiếu key ID (định dạng legacy), cần **migration path được xác định rõ theo nguồn/giai đoạn**, không coi đoán toàn bộ keyring là thiết kế dài hạn. Có thể có một đầu vào legacy-version-specific và key riêng phục vụ migration trong cửa sổ kiểm soát, nhưng phải giới hạn và tắt khi hoàn tất.

### 6.3 Tách nhận dạng key và xác minh AEAD

Key ID trên wire **không phải bằng chứng authentication**. Attacker có thể sửa key ID và làm tăng số yêu cầu KMS nếu service cho phép ID bất kỳ. Vì vậy:

- Chỉ chấp nhận format/version/ID nằm trong scope được cấu hình.
- Không dùng key ID trong input thay quyền kiểm tra tenant/resource dựa trên dữ liệu tin cậy.
- Không tiết lộ nguyên nhân chi tiết cho caller kiểu “key 1001 của tenant A có tồn tại hay không”.
- Đưa byte header vào AAD để ngăn tráo metadata không được xác thực.
- Coi `AEADBadTagException` / `GeneralSecurityException` là thất bại: không trả plaintext một phần, không fallback sang mode không xác thực.
- Với file lớn hoặc stream, không publish bất kỳ plaintext nào trước khi tag được xác minh; xem phần 11.

## 7. Ví dụ Java 17+ chạy được: rotation không làm mất dữ liệu cũ

Ví dụ dưới đây dùng:

- AES-256-GCM, IV ngẫu nhiên 12 byte, tag 128 bit.
- Một **keyring trong RAM** mô phỏng key registry: `1001` là v1, `1002` là v2.
- Record nhị phân có `formatVersion=1` khác với `keyId=1001/1002`.
- Header và context nằm trong AAD; mọi byte ciphertext/tag đều được xác thực trong `Cipher.doFinal`.
- Sau rotation, cả v1 và v2 còn trong keyring, nên **cả dữ liệu cũ và mới** đọc được. Khi migrate một record sang v2 rồi bỏ v1 khỏi map, record được migrate vẫn đọc được; record legacy v1 không còn đọc được.

**Khóa trong demo được sinh lại khi chạy**, không phải cấu hình lưu trữ thực. Chương trình chỉ mô phỏng retention của key trong một lần chạy. Ở hệ thống thật, key phải tồn tại và được quản lý bền vững trong KMS/HSM/keystore theo policy; không lưu key chỉ trong một `static Map` rồi kỳ vọng giải mã dữ liệu sau khi service restart.

Lưu thành file **`KeyVersionDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class KeyVersionDemo {
    private static final int MAGIC = 0x4b565231; // KVR1
    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;
    private static final int HEADER_BYTES = 4 + 1 + 4 + NONCE_BYTES;
    private static final int MAX_RECORD_BYTES = 1_000_000;
    private static final byte[] DOMAIN =
            "document-envelope:v1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RNG = new SecureRandom();

    private static SecretKey keyFor(Map<Integer, SecretKey> keys, int id)
            throws GeneralSecurityException {
        SecretKey result = keys.get(id);
        if (result == null) {
            throw new GeneralSecurityException("Key unavailable");
        }
        return result;
    }

    private static void setAad(Cipher cipher, byte[] header, byte[] context) {
        cipher.updateAAD(DOMAIN);
        cipher.updateAAD(header);
        cipher.updateAAD(ByteBuffer.allocate(4 + context.length)
                .putInt(context.length).put(context).array());
    }

    static byte[] encrypt(Map<Integer, SecretKey> keys, int activeKeyId,
                          byte[] plaintext, byte[] context)
            throws GeneralSecurityException {
        if (plaintext == null || context == null
                || plaintext.length > MAX_RECORD_BYTES - HEADER_BYTES - TAG_BYTES) {
            throw new GeneralSecurityException("Invalid input");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);
        byte[] header = ByteBuffer.allocate(HEADER_BYTES)
                .putInt(MAGIC).put(FORMAT_VERSION).putInt(activeKeyId)
                .put(nonce).array();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(keys, activeKeyId),
                new GCMParameterSpec(TAG_BITS, nonce));
        setAad(cipher, header, context);
        byte[] ciphertextAndTag = cipher.doFinal(plaintext);
        return ByteBuffer.allocate(HEADER_BYTES + ciphertextAndTag.length)
                .put(header).put(ciphertextAndTag).array();
    }

    static byte[] decrypt(Map<Integer, SecretKey> keys, byte[] record,
                          byte[] context) throws GeneralSecurityException {
        if (record == null || context == null
                || record.length < HEADER_BYTES + TAG_BYTES
                || record.length > MAX_RECORD_BYTES) {
            throw new GeneralSecurityException("Invalid record length");
        }
        ByteBuffer input = ByteBuffer.wrap(record);
        if (input.getInt() != MAGIC || input.get() != FORMAT_VERSION) {
            throw new GeneralSecurityException("Unsupported format");
        }
        int keyId = input.getInt();
        byte[] nonce = new byte[NONCE_BYTES];
        input.get(nonce);
        SecretKey key = keyFor(keys, keyId);

        byte[] header = Arrays.copyOfRange(record, 0, HEADER_BYTES);
        byte[] ciphertextAndTag = Arrays.copyOfRange(
                record, HEADER_BYTES, record.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        setAad(cipher, header, context);
        return cipher.doFinal(ciphertextAndTag);
    }

    static int recordKeyId(byte[] record) {
        return ByteBuffer.wrap(record, 5, 4).getInt();
    }

    @FunctionalInterface
    interface Attempt { void run() throws GeneralSecurityException; }

    static boolean rejected(Attempt attempt) {
        try {
            attempt.run();
            return false;
        } catch (GeneralSecurityException ex) {
            return true;
        }
    }

    public static void main(String[] args) throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        Map<Integer, SecretKey> keyring = new HashMap<>();
        keyring.put(1001, generator.generateKey());
        keyring.put(1002, generator.generateKey());

        byte[] context = "tenant-7/document-42".getBytes(StandardCharsets.UTF_8);
        byte[] payload = "Archive data".getBytes(StandardCharsets.UTF_8);
        byte[] oldRecord = encrypt(keyring, 1001, payload, context);
        byte[] newRecord = encrypt(keyring, 1002, payload, context);

        byte[] tamperedId = oldRecord.clone();
        ByteBuffer.wrap(tamperedId, 5, 4).putInt(1002);
        byte[] tamperedData = oldRecord.clone();
        tamperedData[HEADER_BYTES] ^= 1;
        byte[] tamperedTag = oldRecord.clone();
        tamperedTag[tamperedTag.length - 1] ^= 1;

        byte[] migratedRecord =
                encrypt(keyring, 1002, decrypt(keyring, oldRecord, context), context);
        Map<Integer, SecretKey> newOnly = Map.of(1002, keyring.get(1002));

        System.out.println("Old record key ID: " + recordKeyId(oldRecord));
        System.out.println("New record key ID: " + recordKeyId(newRecord));
        System.out.println("Old readable after rotation: " +
                Arrays.equals(payload, decrypt(keyring, oldRecord, context)));
        System.out.println("New readable: " +
                Arrays.equals(payload, decrypt(keyring, newRecord, context)));
        System.out.println("Tampered key ID rejected: " +
                rejected(() -> decrypt(keyring, tamperedId, context)));
        System.out.println("Wrong context rejected: " +
                rejected(() -> decrypt(keyring, oldRecord,
                        "tenant-8/document-42".getBytes(StandardCharsets.UTF_8))));
        System.out.println("Modified ciphertext rejected: " +
                rejected(() -> decrypt(keyring, tamperedData, context)));
        System.out.println("Modified tag rejected: " +
                rejected(() -> decrypt(keyring, tamperedTag, context)));
        System.out.println("Migrated record key ID: " + recordKeyId(migratedRecord));
        System.out.println("Migrated plaintext preserved: " +
                Arrays.equals(payload, decrypt(newOnly, migratedRecord, context)));
        System.out.println("Old key unavailable blocks legacy record: " +
                rejected(() -> decrypt(newOnly, oldRecord, context)));
    }
}
```

Chạy bằng **JDK 17 trở lên**:

```bash
javac --release 17 KeyVersionDemo.java
java KeyVersionDemo
```

**Stdout chính xác** (không in key/nonce/ciphertext ngẫu nhiên):

```text
Old record key ID: 1001
New record key ID: 1002
Old readable after rotation: true
New readable: true
Tampered key ID rejected: true
Wrong context rejected: true
Modified ciphertext rejected: true
Modified tag rejected: true
Migrated record key ID: 1002
Migrated plaintext preserved: true
Old key unavailable blocks legacy record: true
```

### 7.1 Giải thích luồng và bảo đảm thực sự

- `generator.generateKey()` tạo hai khóa AES khác nhau cho bản demo. Production phải có key registry/KMS bảo toàn vật liệu khóa và trạng thái version qua restart.
- `encrypt(..., 1001, ...)` ghi v1, `encrypt(..., 1002, ...)` ghi v2. **Format version đều bằng 1**; chỉ key generation khác nhau.
- `setAad` ràng buộc domain, header và length-prefixed context. Sửa `keyId` từ 1001 sang 1002 khiến service chọn key v2, nhưng không thể xác thực tag do key/AAD không phù hợp.
- `decrypt` kiểm kích thước, magic và format trước khi lấy key. Nó **chỉ return byte[] sau `Cipher.doFinal` thành công**, không publish plaintext chưa xác thực.
- Mã hóa lại `migratedRecord` **giải mã v1 đã xác thực rồi mã hóa lại bằng v2 và nonce mới**. Không chỉ cập nhật header key ID.
- Map `newOnly` mô phỏng v1 không còn sẵn cho caller. Record v1 bị từ chối, record đã migrate giải mã được. **Đây là mô phỏng thiếu khóa**, không phải thao tác KMS destroy hay xóa khóa thật.
- `recordKeyId` chỉ được dùng trong `main` với dữ liệu do chính chương trình sinh. Hàm này **không phải parser an toàn cho input bất kỳ** vì giả sử record đủ dài và đúng cấu trúc; code `decrypt` mới có kiểm tra tối thiểu.
- `rejected` bắt `GeneralSecurityException` để minh họa các lỗi parse/key/AEAD. Hệ sản xuất cần xử lý lỗi và audit rõ ràng, không phân biệt quá chi tiết cho client không tin cậy.
- Nonce ngẫu nhiên không được coi là cam kết toán học rằng **không bao giờ** trùng. Ứng dụng có tải lớn/nhiều node cần chính sách nonce uniqueness và giới hạn số lần mã hóa với cùng key, theo quy tắc AES-GCM.

### 7.2 Điều chương trình không khẳng định

Chương trình mô phỏng hai key với một map cục bộ; nó **không** dạy lưu private secret trong source code, không hỗ trợ persistence giữa hai lần chạy, không triển khai PKCS#11/KMS/HSM hoặc database migration, không có đa tenant isolation thực tế, không giải quyết khả năng mất key trong backup. Khi triển khai thật, phải thay `Map<Integer, SecretKey>` bằng bộ giải quyết key được xác thực/cấp quyền theo policy, thường là KMS/HSM hoặc keystore, và dùng immutable key version reference.

## 8. Những chiến lược rotation và bảo toàn dữ liệu cũ

Rotation không chỉ có một cách. Chọn tùy khối lượng, SLA, lưu trữ, compliance và mức độ rủi ro.

| Chiến lược | Dữ liệu cũ có phải đổi ngay? | Khóa cũ cần sẵn sàng decrypt? | Khi nào phù hợp? |
| --- | --- | --- | --- |
| **New-writes-only rotation** | Không | **Có** trong thời gian giữ bản mã v1 | Muốn đổi key ghi mới nhanh, volume cũ lớn |
| **Lazy re-encryption** | Đổi khi record được đọc/cập nhật | Có, cho đến khi toàn bộ records cũ được migrate/hết hạn | Trải tải migration, nhưng cần xử lý record ít truy cập |
| **Batch re-encryption** | Có, theo job có kiểm soát | Có trong suốt migration và rollback window | Muốn giảm phụ thuộc vào key cũ một cách chủ động |
| **Rewrap DEK bằng KEK mới** | Có thể **không** cần re-encrypt bulk payload | Cần key/KEK cũ đủ lâu để unwrap, trước khi rewrap hoàn tất | Hệ thống envelope encryption lưu wrapped DEK |
| **Xóa/hủy key cũ** | Không tự chuyển dữ liệu | **Không thể** decrypt bằng key đã mất | Crypto-shredding khi policy chấp nhận mất khả năng đọc |

### 8.1 New-writes-only: dễ triển khai nhưng kéo dài phụ thuộc

Thay active version `v1 -> v2` trong service. Mọi ciphertext mới chứa v2, nhưng tất cả ciphertext v1 vẫn tồn tại. Ưu điểm là rollout nhanh; nhược điểm là phải bảo vệ và giữ key v1 **miễn là còn dữ liệu hoặc backup cần giải mã**. Chính sách `DECRYPT_ONLY` hữu ích để tránh sản sinh thêm phụ thuộc vào v1.

Nếu giữ key 10 năm vì một object không bao giờ đọc, hệ thống có rủi ro vận hành và chi phí giữ khóa kéo dài. Cần inventory theo key version để ra quyết định có migrate hoặc cho hết hạn dữ liệu đó.

### 8.2 Lazy migration: đọc v1, ghi lại v2

Luồng:

```text
get(record v1)
   -> decrypt/authenticate with v1
   -> verify caller rights and business state
   -> re-encrypt with active v2 (fresh nonce, correct AAD)
   -> atomic compare-and-swap update if original version unchanged
   -> serve plaintext only after successful verification
```

Ưu điểm: tải migration dàn theo lưu lượng đọc. Rủi ro: record hiếm khi đọc không bao giờ tự chuyển; record bị cập nhật song song có thể bị overwrite nếu thiếu optimistic locking; migration không được làm ảnh hưởng latency hoặc vô tình phát event nghiệp vụ.

**Không** coi lazy migration là hoàn tất trước khi có báo cáo key-version inventory xác thực và kế hoạch cho những record chưa được truy cập.

### 8.3 Batch migration: phải idempotent và quan sát được

Một job migration tốt:

1. Snapshot/đọc **đúng record version** và metadata AAD cũ.
2. Lấy key v1 có quyền decrypt; `doFinal` phải xác thực thành công.
3. Mã hóa plaintext bằng key v2, IV mới và AAD/context theo policy mới.
4. Lưu ciphertext mới + key ID mới + nonce/tag + version/schema trong **một transaction hoặc atomic object replacement**.
5. Chỉ cập nhật checkpoint sau khi commit; nếu lỗi, bản v1 vẫn còn nguyên.
6. Đọc kiểm một phần hoặc toàn bộ records theo mức độ rủi ro; lưu metrics số lượng v1/v2 và lỗi.
7. Retry theo phiên bản ban đầu và optimistic lock; tránh mất cập nhật đồng thời.
8. Chỉ disable/delete v1 sau khi toàn bộ live data và backups/DR/archives phụ thuộc đã được đánh giá.

Nếu job bị ngắt giữa chừng, nó phải có thể chạy lại **không** biến record đã migrate thành bản không thể đọc. Với re-encryption, mỗi lần thử thành công tạo ciphertext mới vì nonce mới; nếu retry chưa commit, kết quả cũ chưa được coi là nguồn chính. Không lấy `keyId` trong request để quyết định key mà không đối chiếu registry/tenant đã tin cậy.

### 8.4 Rewrap: chỉ có khi kiến trúc tách DEK và KEK

**Envelope encryption** thường tạo một **DEK** để mã hóa nội dung và một **KEK** (thường do KMS giữ) để wrap/unwrap DEK:

```text
plaintext --encrypt with DEK--> ciphertext + tag
DEK ------wrap with KEK v1--> wrappedDEK-v1

record = ciphertext + nonce + tag + wrappedDEK + KEK reference + metadata
```

Khi thay KEK v1 bằng KEK v2:

```text
unwrap wrappedDEK-v1 using KEK v1 (có quyền)
   -> rewrap the same DEK with KEK v2
   -> update wrappedDEK + KEK reference atomically
   -> bulk ciphertext có thể giữ nguyên vì DEK không đổi
```

**Khác với re-encrypt:** nếu thay **DEK**, phải mã hóa lại nội dung bằng DEK mới và nonce/tag thích hợp. Nếu chỉ thay **KEK** theo cơ chế wrapping được hỗ trợ, ciphertext của dữ liệu có thể không đổi. Tuy nhiên:

- Phải giữ metadata/AD binding đúng, kiểm cơ chế key wrapping có integrity và quyền trước khi dùng DEK.
- Nếu ciphertext AAD trực tiếp ràng buộc KEK version/wrapped DEK metadata, chỉ rewrap có thể khiến tag cũ không khớp; format phải được thiết kế có chủ đích để hỗ trợ rewrap hoặc phải re-encrypt theo contract.
- KMS có thể hỗ trợ rotate/re-encrypt key hierarchy theo cách riêng; không giả định “đổi key alias” tự động chuyển bất kỳ record nào.
- Rewrap không giải quyết dữ liệu đã bị lộ do DEK/KEK cũ bị compromise trong thời gian trước đó; cần threat-model và incident response.

## 9. Ràng buộc metadata: đừng cho key ID mở đường đổi tenant

Một header có key ID là **untrusted input** cho đến khi phép xác thực mật mã thành công. Với SaaS đa tenant:

```text
Request identity -> trusted tenant & document permissions
                      |
                      v
Read object by trusted resource ID
                      |
                      v
Parse key reference (untrusted hint)
                      |
                      v
Check key reference belongs to allowed tenant/namespace
                      |
                      v
Decrypt + authenticate header/ciphertext/AAD
                      |
                      v
Validate plaintext schema and business authorization
```

Không cho user lấy ciphertext từ tenant A, sửa key ID thành một ID của tenant B, rồi buộc service gọi KMS với quyền tenant B. Kể cả tag sẽ thất bại, hành vi đó có thể tạo key-access side effects, tiết lộ tồn tại key hoặc vượt scope IAM nếu registry không kiểm.

Cần cân nhắc:

- KMS permission per workload/tenant, chặn cross-tenant key use từ identity tin cậy.
- AAD chứa tenant/document/usage theo một serialization không nhập nhằng.
- Record ID/version trong dữ liệu xác thực nếu cần chống tráo object.
- Domain separation giữa các mục đích mã hóa (document, token, backup), thay vì dùng một key không phân biệt.
- Kiểm key ID có đúng **loại thuật toán, mục đích và vùng** trước khi dùng; không chấp nhận bất kỳ ID format nào do attacker đặt.
- Tag hợp lệ không tự thay thế RBAC/ABAC; chỉ đảm bảo integrity cho byte đã được đưa vào phạm vi AEAD.

### “Rollback” khác “tampering”

GCM có thể chứng minh một ciphertext và AAD là hợp lệ dưới key đã chọn, **nhưng không tự chứng minh ciphertext đó là phiên bản mới nhất**. Một attacker có quyền thay object trong storage có thể phục hồi nguyên ciphertext/tag cũ mà vẫn hợp lệ, nếu ứng dụng không gắn một nguồn **trusted monotonic version** hoặc state kiểm rollback.

Nếu nghiệp vụ cần chống phục hồi phiên bản cũ (ví dụ số dư, quyền truy cập, thứ tự sự kiện), nên có object version/sequence/state được lưu trong DB tin cậy hay một cơ chế chống rollback có kiểm soát. Đưa objectVersion vào AAD chỉ hữu ích nếu giá trị phiên bản kỳ vọng được lấy từ nguồn **không thể bị rollback cùng ciphertext**.

## 10. Metadata cần lưu để giải mã sau nhiều năm

Một bản mã phải tự đủ dữ liệu công khai hoặc có khả năng truy ra chúng qua metadata bền vững để thực hiện:

1. Chọn parser đúng `formatVersion` và crypto profile.
2. Tìm **immutable key ID/version** hoặc wrapped DEK + KEK reference.
3. Lấy nonce và tag đúng độ dài.
4. Khôi phục **chính xác byte AAD** theo cùng policy/version đã dùng khi encrypt.
5. Đưa ciphertext vào AEAD đúng trình tự, chỉ lấy plaintext khi tag hợp lệ.
6. Biết cách deserialize plaintext theo schema version phù hợp.
7. Kiểm phân quyền trên record và mục đích sử dụng.

Ví dụ mô hình dữ liệu khái niệm (không phải schema bắt buộc):

```text
EncryptedObject {
    objectId: UUID
    tenantId: trusted identifier
    objectRevision: integer
    formatVersion: integer
    cryptoProfile: allowlisted identifier
    immutableKeyRef: opaque string
    nonce: byte[]
    ciphertextAndTag: byte[]
    aadSchemaVersion: integer
    schemaVersion: integer
    state: LIVE | MIGRATING | ARCHIVED | DELETED
}
```

Nếu key reference nằm trong **header AAD**, không được update riêng trong database mà bỏ qua việc tái tạo tag. Nếu metadata AAD nằm ngoài ciphertext, cần bảo đảm việc đọc lại nguyên giá trị và cách serialize không thay đổi sau migration. Đừng để thư viện JSON khác phiên bản sửa thứ tự field hoặc Unicode normalization rồi làm tag fail. Với dữ liệu dài hạn, nên lưu byte AAD đã xác thực hoặc một versioned canonical representation đủ để tái tạo 100%.

**Tag và IV không phải bí mật**, nhưng không được bỏ hoặc chỉnh sửa. **Key bytes phải bảo vệ riêng**, không đưa vào record hay log. Nếu dùng wrapped DEK, wrapped blob không phải plaintext key và vẫn cần bảo vệ thông tin truy cập, integrity và metadata theo thiết kế.

## 11. An toàn khi decrypt: chỉ đưa plaintext ra ngoài sau khi tag đúng

Khi dùng Java `Cipher` với `AES/GCM/NoPadding`:

```java
// Minh họa trong method đã có SecretKey key, byte[] nonce,
// byte[] authenticatedHeader, byte[] trustedContext và byte[] ciphertextAndTag.
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
cipher.updateAAD(authenticatedHeader);
cipher.updateAAD(trustedContext);
byte[] plaintext = cipher.doFinal(ciphertextAndTag);
```

Đoạn trên **không độc lập**; AAD và format cụ thể phải giống phía mã hóa. `doFinal` thất bại khi tag sai có thể ném `AEADBadTagException` (một dạng `BadPaddingException`) hoặc lỗi tương ứng; ứng dụng **không được trả byte được giải mã một phần** trong catch branch.

Một số provider/mode streaming có thể cho `Cipher.update` trả output **trước khi tag cuối được kiểm**. Tuyệt đối không ghi những byte đó vào HTTP response, event bus, database, log, cache hoặc file mà component khác có thể đọc. Nếu cần xử lý file rất lớn, cân nhắc staging được cô lập và atomic publish sau xác thực, hoặc định dạng **record-based AEAD** có tag cho từng record và commitment về thứ tự/EOF/truncation; không lấy một ví dụ in-memory rồi kéo dài tùy tiện để stream plaintext chưa xác thực.

**Lưu ý `CipherInputStream`:** tài liệu Java cảnh báo class này có thể bắt một số exception kiểm tra integrity, ví dụ `BadPaddingException`, và không tái ném. Nó **có thể không thích hợp** khi ứng dụng bắt buộc biết đầy đủ lỗi xác thực. Cần đánh giá API streaming được dùng và kiểm thử end-to-end việc không phát plaintext khi tag sai.

### Đơn vị độ dài

`new GCMParameterSpec(128, nonce)` dùng **128 bit tag**, tức **16 byte**. IV demo 12 byte. Metadata phải ghi rõ đơn vị và thuật toán. Đừng giả định mọi provider/giao thức đặt tag ở cùng vị trí; profile Java demo dùng `ciphertext || tag` từ `Cipher.doFinal`.

## 12. Kiểm soát nonce uniqueness qua nhiều thế hệ khóa

Đối với AES-GCM, **không được tái sử dụng cùng nonce với cùng key**. Rotation tạo key mới có thể giúp tách phạm vi nonce, nhưng **không** cho phép thiết kế nonce tùy tiện hoặc bỏ qua collision risk. Trong ví dụ, `SecureRandom` tạo IV 96-bit mới mỗi lần; ứng dụng thực tế phải đánh giá số lượng bản tin trên mỗi key, nhiều node cùng encrypt, snapshot restore, counter rollback và key lifetime theo chuẩn/khuyến nghị.

Những tình huống cần cảnh giác:

- Nhiều instance dùng chung key và cùng counter khởi động từ 0.
- VM snapshot restore khiến sequence/counter lặp.
- Retry mã hóa dùng lại nonce với **plaintext/AAD khác** trên cùng key.
- Job migration tái sử dụng nonce v1 khi re-encrypt v2 mà không có policy bảo đảm uniqueness; tốt hơn sinh IV mới theo profile.
- Một key version cũ vẫn được dùng để encrypt ở node chưa update sau switch sang decrypt-only.
- Key alias được reset trỏ lại key bytes cũ, làm hệ thống tưởng đó là key mới nhưng thực ra cùng key/nonce domain.
- Hệ thống dùng random 96-bit nonce ở quy mô vượt ngân sách message count đã được đánh giá, làm collision probability không còn được chấp nhận.

Trong luồng key rotation, mỗi key generation phải có **usage limits, nonce allocation model và metrics** độc lập. Với cơ chế counter, phải đồng bộ đa node và chống rollback; với nonce ngẫu nhiên, có giới hạn cryptoperiod/message count và chính sách rotation hợp lý.

## 13. Rollout và rollback khi nhiều service cùng đọc/ghi

Thay `activeKeyVersion` không chỉ là đổi một config:

**Giai đoạn chuẩn bị:**

1. Tạo key v2 trong KMS/keystore với đúng policy và quyền.
2. Cập nhật consumer/decryptor trước để **có khả năng resolve v2**, nhưng vẫn đọc v1.
3. Kiểm end-to-end test/health, khả năng encrypt/decrypt ở staging, failover region và backup service.
4. Chốt `formatVersion`/AAD schema để không vô tình đổi nhiều thứ cùng lúc.

**Giai đoạn kích hoạt:**

1. Chuyển active encrypt version sang v2 theo cơ chế cấu hình tin cậy.
2. Đảm bảo chỉ có **một chính sách chọn key được công bố**; với distributed rollout cần xử lý thời gian hệ thống có cả v1/v2 writer.
3. Xác nhận record mới chứa v2 và decrypt được trên các node hiện hành; không dùng v1 cho new writes sau khi đã chuyển hoàn tất.
4. Giữ v1 ở chế độ decrypt-only cho đến khi bảo đảm retention và migration.

**Giai đoạn hậu kiểm:**

1. Đếm live records theo key reference, theo tenant và theo môi trường; kiểm failed decrypt theo version.
2. Kiểm backup, DR, job chậm, message queue, archive và batch export.
3. Chạy migration có checkpoint/transaction nếu cần.
4. Đưa v1 vào trạng thái disabled/destroyed **chỉ sau khi có bằng chứng không còn phụ thuộc cần đọc** hoặc khi chính sách rủi ro buộc từ chối.

**Rollback:** Nếu writer v2 gặp lỗi, đừng xóa/disable v2 ngay — **những ciphertext đã được ghi bằng v2 có thể cần key v2 để đọc**. Quay lại writer v1 (nếu policy cho phép) chỉ thay đổi key dùng ghi mới, **không đảo ngược các record v2 đã tồn tại**. Kế hoạch rollback phải giữ cả bộ key versions mà dữ liệu đã tham chiếu.

### Multi-region và backup

Một bản backup dữ liệu chỉ hữu ích nếu có cách lấy đúng key để giải mã **theo quyền và chính sách**. Hệ thống phải có recovery plan cho metadata/key reference, availability của KMS, trust domain, quyền IAM, và lịch sử key versions.

Khi restore backup 6 tháng trước, các records có thể còn tham chiếu v1 dù production hiện tại đã chuyển v2. Nếu v1 bị hủy, có thể không restore được. Ngược lại, backup một cách bừa bãi toàn bộ plaintext key material lại phá vỡ mục tiêu quản lý khóa. Cần quy trình escrow/backup/restore trong vùng bảo vệ được kiểm soát, với audit và business continuity requirements.

**Kiểm thử restore thực tế**, không chỉ kiểm rằng file backup tồn tại.

## 14. Xóa khóa có thể đồng nghĩa xóa khả năng đọc dữ liệu

Khóa bị xóa/disable không còn dùng decrypt được. Nếu tất cả các bản sao DEK/KEK hoặc key material liên quan bị hủy thật và không có cách khôi phục, ciphertext tương ứng có thể trở thành **không thể giải mã** — đây là nguyên tắc *cryptographic erasure* trong một số thiết kế.

Tuy nhiên, không nên khẳng định “đã crypto-erase” chỉ vì xóa một entry khỏi config:

- Có thể còn bản sao key trong backup, cache, log, snapshot, instance sống hoặc hệ KMS khác.
- Có thể còn plaintext và bản sao dữ liệu đã được xuất ra.
- Với envelope encryption, xóa KEK có thể ảnh hưởng nhiều wrapped DEK; cần hiểu key hierarchy.
- Key rotation và xóa key không thay thế hủy dữ liệu, truy cập hạn chế và chính sách retention.
- Khi key bị compromise, xóa khỏi service không làm các bản ciphertext attacker đã sao chép trở lại an toàn.

Do đó, trước khi **destroy** key cũ phải có bằng chứng kiểm kê record dependencies, backup retention và quyết định pháp lý/nghiệp vụ. Nếu vẫn còn dữ liệu cần đọc mà key đã mất hoàn toàn, **không có thao tác Java nào tự tái tạo được key chỉ từ key ID hoặc ciphertext**.

## 15. Những lỗi thiết kế thường gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Dùng duy nhất `CURRENT_KEY` để decrypt mọi record | Đổi key làm record cũ fail tag | Lưu immutable key reference trên mỗi record |
| Lưu alias `latest` nhưng không version | Alias chuyển hướng sang key khác | Lưu version cụ thể hoặc blob KMS tự định tuyến lịch sử theo contract |
| Coi key ID là secret hoặc đưa key bytes vào ID | Lộ key hoặc nhầm trust boundary | Key ID chỉ là tham chiếu; secret trong key manager |
| Dùng chung một `version` cho format, key và schema | Không biết cần parse/dùng key nào | Tách ý nghĩa/version field |
| Tự động thử toàn bộ keyring khi decrypt | Lỗi mơ hồ, tốn tài nguyên, mở oracle | Lookup đúng một key version theo metadata/registry |
| Cho client chọn key tùy ý | Cross-tenant lookup/key abuse | Scope theo tenant, identity và IAM/allowlist server |
| Không xác thực key ID/header bằng AEAD | Metadata có thể bị tráo | Đưa header vào AAD, validate profile/key scope |
| Giữ v1 ACTIVE ở node cũ sau rotation | Dữ liệu mới tiếp tục phụ thuộc key cũ | Triển khai trạng thái decrypt-only và quan sát writers |
| Chỉ đổi header v1 thành v2 khi migrate | Ciphertext/tag vẫn gắn key/nonce/AAD cũ | Decrypt + verify rồi encrypt lại với v2 + nonce mới |
| Xóa v1 ngay khi đổi active v2 | Mất khả năng đọc/restore bản mã cũ | Retain decrypt-only tới khi hết phụ thuộc |
| Quên backup/DR/queue khi đếm key usage | Restore thất bại hoặc job còn dùng key cũ | Inventory đầy đủ theo nơi lưu và thời hạn |
| Migration ghi key ID mới trước ciphertext/tag | State nửa vời, mất dữ liệu | Atomic transaction/object replace/compare-and-swap |
| Rewrap KEK nhưng format AAD chứa KEK metadata | Tag payload có thể không còn đúng | Thiết kế envelope để rewrap đúng contract hoặc re-encrypt |
| Re-encrypt nhưng dùng lại nonce trên cùng key | Nguy cơ phá bảo mật GCM | Nonce uniqueness toàn vòng đời key |
| Coi tag hợp lệ là record mới nhất | Replay/rollback ciphertext cũ | Trusted monotonic revision hoặc anti-rollback state |
| Công bố plaintext từ `Cipher.update` trước tag | Dữ liệu chưa xác thực thoát khỏi vùng tin cậy | Chỉ commit/publish sau `doFinal` thành công |
| Đổi active pointer rồi rollback nhưng xóa key v2 | Record vừa tạo bằng v2 không đọc được | Retain mọi key version đã ghi dữ liệu |
| In key, plaintext, raw wrapped DEK vào log | Lộ dữ liệu nhạy cảm | Audit metadata có làm sạch và kiểm quyền |

## 16. Checklist kiểm thử và chấp nhận rotation

Bộ test tối thiểu nên chạy tự động trong CI/staging trước mỗi rollout:

| Trường hợp | Kết quả mong đợi |
| --- | --- |
| Ghi v1 rồi chuyển ACTIVE sang v2 | Record v1 vẫn decrypt với v1 nếu policy còn cho phép |
| Ghi record mới sau switch | Envelope chứa immutable v2 key ref; decrypt với v2 |
| v1 chuyển DECRYPT_ONLY | Không tạo new writes bằng v1; record v1 vẫn đọc được |
| v1 key bị disabled/mất | Record v1 bị từ chối rõ ràng, không thử key khác |
| Đổi key ID v1 thành v2 trong header | AEAD authentication thất bại |
| Đổi IV, ciphertext hoặc tag | Bị từ chối; không trả plaintext |
| Đổi tenant/document context AAD | Tag fail; không dùng plaintext |
| Format version không hỗ trợ | Parser từ chối, không fallback yếu |
| Migrate v1 -> v2 | Decrypt/authenticate v1 rồi encrypt v2 bằng IV mới, nội dung không đổi |
| Migration bị ngắt trước commit | Bản v1 còn nguyên; retry idempotent |
| Hai migration workers đụng cùng record | Optimistic lock/CAS chặn ghi đè phiên bản mới |
| Rollback writer sau khi đã có v2 records | v2 records vẫn decrypt được |
| Backup/DR restore | Hệ khôi phục đúng key version theo policy và role |
| Còn legacy record trong archive | Không được destroy key cũ nếu archive còn phải phục hồi |
| Tenant A header trỏ key tenant B | Scope authorization từ chối trước key use |
| Key ID giả/cực dài/không tồn tại | Bounded parsing, lookup từ chối, không lộ thông tin key |
| Nonce reuse attempt với cùng key | Test/quy trình nonce allocation và limits phát hiện/prevent theo thiết kế |
| Record cũ hợp lệ được replay | Anti-replay/anti-rollback policy xử lý theo state tin cậy |
| Performance migration | Có quota/rate limit, backoff, checkpoint và audit |
| Audit | Ghi record ID, key reference, migration result; không lộ key/plaintext |

**Điều kiện hoàn thành rotation:** không chỉ là “mã hóa mới được bằng v2”. Cần có bằng chứng decrypt các record v1/v2 đúng theo policy, migration không làm hỏng dữ liệu, backup có thể restore, old-key retirement không phá retention và không có key access ngoài quyền.

## 17. Bài tập thực hành

### Bài 1 — Phân biệt loại version

Cho record có `formatVersion=2`, `keyRef=orders/prod`, `keyVersion=5`, `documentSchemaVersion=3`. Hãy mô tả từng version quyết định phương thức nào, giá trị nào được phép đổi khi re-encrypt, và vì sao không thể chỉ đọc `keyRef=orders/prod` nếu alias trỏ tới key material mới.

### Bài 2 — Phân tích output chương trình

Chạy `KeyVersionDemo` và giải thích từng dòng stdout. Bổ sung test đổi một byte nonce/header, đổi một bit tag, thiếu toàn bộ record, và record có `formatVersion` chưa được allowlist. Chứng minh method `decrypt` không trả plaintext khi tag không hợp lệ.

### Bài 3 — Lập chính sách vòng đời

Một hệ thống có 3 triệu bản ghi v1, 10.000 bản ghi v2, snapshot hàng tuần giữ 180 ngày, archive giữ 7 năm. Hãy đề xuất states ACTIVE/DECRYPT_ONLY/DISABLED, KPI theo key version, kế hoạch batch migration và khi nào được phép destroy v1. Phân biệt số live records với số bản nằm trong backup còn phải restore.

### Bài 4 — Lazy migration và optimistic lock

Thiết kế bảng `EncryptedDocument(id, revision, keyVersion, encryptedEnvelope)`. Hai request đồng thời đọc record v1, cả hai muốn migrate sang v2. Viết giả mã transaction/CAS để chỉ một lần cập nhật được commit, request còn lại không được ghi đè revision mới hơn. Mô tả cách retry và bảo đảm không phát side effects nghiệp vụ.

### Bài 5 — Rewrap DEK và KEK

Một service mã hóa từng object bằng DEK riêng; KMS dùng KEK v1 để wrap DEK. Khi KEK chuyển sang v2, phần nào có thể được giữ nguyên và phần nào phải được cập nhật? Giải thích vì sao điều này khác trường hợp hệ thống dùng **một AES key trực tiếp mã hóa mọi object**, và xử lý ra sao nếu AAD của ciphertext bao gồm KEK version.

### Bài 6 — Khôi phục thảm họa

Sau khi đổi active key v2, một region bị lỗi và đội vận hành restore backup chứa cả v1/v2 ciphertext. Viết danh sách key versions, quyền IAM, metadata AAD, key availability và phiên bản code cần có. Nêu những tình huống mà restore không thể hoàn tất dù file backup còn nguyên.

### Bài 7 — Compromise và cryptographic erasure

Một key v1 bị nghi lộ khi vẫn còn 100.000 records phụ thuộc. Hãy lập thứ tự ưu tiên containment, kiểm phạm vi dữ liệu có thể bị lộ, migration, key disable/revoke, backup/key retention và thông báo sự cố theo policy. Tại sao re-encrypt không thể làm “bí mật trở lại” nếu đối thủ đã lấy plaintext trước đó?

**Kết quả mong đợi:** bạn có thể nhìn một ciphertext và xác định **đúng khóa cần dùng, đúng phiên bản format, metadata nào phải xác thực, cách đọc dữ liệu cũ khi rotation**, và khi nào **không được hủy key**. Một triển khai đạt yêu cầu luôn có test cho thiếu/sai key, tampering, replay/rollback theo threat model và migration transaction safety.

## 18. Tài liệu tham khảo

- [Java SE 17 — Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): `init`, `updateAAD`, `doFinal` và AEAD.
- [Java SE 17 — GCMParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): IV/nonce và độ dài tag tính bằng bit.
- [Java SE 17 — KeyGenerator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyGenerator.html): sinh secret key theo thuật toán/provider.
- [Java SE 17 — SecureRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html): nguồn byte ngẫu nhiên mật mã.
- [NIST SP 800-57 Part 1 — Recommendation for Key Management](https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final): vòng đời và quản lý khóa.
- [NIST SP 800-38D — Galois/Counter Mode (GCM)](https://csrc.nist.gov/pubs/sp/800/38/d/final): AEAD và yêu cầu IV/nonce.
- [OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): quản lý khóa, dữ liệu và rotation.
- [OWASP Key Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html): lifecycle, storage, access control và rotation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-lifecycle-005">11.5 Envelope encryption và tách khóa dữ liệu khỏi khóa bảo vệ khóa</a>

<details>
<summary>Xem chi tiết</summary>

# 11.5 Envelope encryption và tách khóa dữ liệu khỏi khóa bảo vệ khóa

## Mục tiêu

Bài học thuộc **11. Vòng đời khóa** trong Java Advance – security-cryptography. Sau bài này, bạn có thể:

1. Giải thích **envelope encryption (mã hóa phong bì)** và phân biệt **DEK (Data Encryption Key)** với **KEK (Key Encryption Key)** theo vai trò, nơi lưu, quyền truy cập và vòng đời.
2. Mô tả quy trình **sinh DEK → mã hóa dữ liệu → bọc DEK bằng KEK → lưu envelope → mở bọc DEK → giải mã dữ liệu**; hiểu phần nào được phép lưu công khai và phần nào phải được bảo vệ.
3. Hiểu vì sao **rewrap (bọc lại DEK)** khi thay KEK thường không cần giải mã/mã hóa lại toàn bộ dữ liệu, nhưng **khác** với thay DEK hoặc khắc phục sự cố lộ khóa.
4. Dùng **Java 17+ và AES-GCM** để viết mô phỏng độc lập có key separation, AAD, nonce, kiểm chứng tampering và tái bọc DEK.
5. Đưa thiết kế vào KMS/HSM, kiểm soát key ID, multi-tenant, quyền gọi dịch vụ mã hóa, lịch rotation, backup, kiểm thử phục hồi và audit mà không lưu khóa bí mật lộ thiên.

**Giới hạn của ví dụ:** chương trình Java bên dưới dùng hai `SecretKey` được tạo **trong bộ nhớ** để minh họa tầng DEK/KEK. Đây **không phải** một KMS thật, không cung cấp kiểm soát IAM, HSM, audit hay bảo vệ khóa trước tiến trình bị xâm phạm. Trong sản phẩm thực tế, KEK nên được quản lý bằng dịch vụ khóa/KMS hoặc thiết bị bảo mật phù hợp; code ứng dụng chỉ có quyền thực hiện những thao tác cần thiết theo policy.

## 1. Bài toán: vì sao không dùng một khóa AES cho mọi dữ liệu?

Giả sử dịch vụ lưu hàng triệu bản ghi hoặc tệp, mỗi tệp được mã hóa bằng AES-GCM. Cách đơn giản nhất là lấy **một khóa AES toàn hệ thống** và dùng nó mã hóa từng tệp. Nhưng khi làm vậy:

- Mỗi service cần giải mã đều có thể phải truy cập **cùng một khóa dùng cho tất cả dữ liệu**, làm tăng phạm vi thiệt hại nếu khóa bị lộ.
- Đổi khóa mã hóa dữ liệu có thể đòi hỏi đọc và mã hóa lại khối lượng lớn dữ liệu, tốn I/O, CPU và thời gian.
- Phân quyền theo tenant, tài liệu hoặc ứng dụng trở nên khó kiểm soát; một khóa sai phạm vi có thể giải mã nhiều thứ không liên quan.
- Việc theo dõi phiên bản khóa, thu hồi quyền, backup/restore và khôi phục sau sự cố trở nên phức tạp.
- Nếu nonce AES-GCM bị lặp lại dưới cùng khóa, bảo mật bị tổn hại nghiêm trọng. Dùng một khóa cho số lượng phép mã hóa khổng lồ làm tăng gánh nặng vận hành nonce và key lifetime.

**Envelope encryption** khắc phục một phần các vấn đề trên bằng **phân tầng khóa**. Thông thường, dữ liệu được mã hóa bằng **một DEK riêng**; chính DEK lại được **bọc (wrap/encrypt)** dưới một **KEK** có phạm vi bảo vệ/ủy quyền rộng hơn. Nhờ đó, phần dữ liệu lớn có thể được mã hóa cục bộ nhanh chóng, trong khi khóa tầng cao được quản lý tập trung và hạn chế truy cập.

Không có nghĩa mô hình này tự cung cấp mọi bảo đảm. Nếu ứng dụng được phép gọi KMS để mở mọi DEK và bị chiếm quyền, attacker vẫn có thể giải mã dữ liệu trong phạm vi quyền đó. Envelope encryption là một phần của kiến trúc an toàn, không thay thế least privilege, identity, audit, nonce policy và kiểm soát plaintext trong bộ nhớ.

## 2. Hai loại khóa: DEK và KEK

| Tiêu chí | **DEK — Data Encryption Key** | **KEK — Key Encryption Key** |
| --- | --- | --- |
| Mục đích | Mã hóa **nội dung dữ liệu** (file, bản ghi, object, blob) | **Bảo vệ DEK** (wrap/encrypt/unwrap) |
| Phạm vi | Có thể một object, một nhóm object hoặc một phiên, theo chính sách; thường ưu tiên phạm vi hẹp | Có thể bảo vệ nhiều DEK; nên ràng buộc tenant, app, môi trường, vai trò và key policy |
| Ví dụ thuật toán | AES-256-GCM (AEAD), với nonce duy nhất khi dùng cùng DEK | AES key wrap / AES-GCM được cấu hình đúng, hoặc wrap bằng KMS/HSM; có thể dùng cơ chế bất đối xứng phù hợp |
| Sinh khóa | Sinh bằng CSPRNG; thường sinh **DEK mới** khi mã hóa object mới | Sinh/quản lý trong KMS/HSM hoặc secret manager/keystore có kiểm soát |
| Lưu trữ | **Không lưu plaintext DEK cùng ciphertext**; có thể lưu **wrapped DEK** | Giữ KEK ở vùng tin cậy; nếu KMS/HSM giữ key không xuất được, ứng dụng chỉ thấy key ID/handle |
| Quyền sử dụng | Dùng tạm để mã hóa/giải mã nội dung cụ thể | Quyền wrap/unwrap, ràng buộc identity, key policy, audit và quota |
| Rotation | Đổi DEK thường **phải mã hóa lại dữ liệu** được bảo vệ bởi DEK cũ | Đổi KEK có thể **rewrap các DEK** mà không thay ciphertext dữ liệu |
| Khi lộ | Ảnh hưởng dữ liệu mã hóa bằng DEK đó; phạm vi phụ thuộc cách tái sử dụng | Có thể ảnh hưởng nhiều DEK đã được bọc bằng KEK đó, gồm bản sao lưu cũ |

**Thuật ngữ:**

- **Plaintext DEK:** byte khóa gốc, dùng trực tiếp với AES-GCM để mã hóa dữ liệu; là secret.
- **Wrapped/encrypted DEK:** dữ liệu mã hóa chứa DEK và tag/metadata cần thiết; có thể được lưu cùng ciphertext của nội dung, nhưng bản thân nó **không phải khóa có thể dùng trực tiếp**.
- **KEK ID / key version:** định danh để hệ thống **tìm đúng KEK tin cậy** khi unwrap; key ID không phải secret và **không tự cấp quyền**.
- **Master/root key:** tên gọi tùy hệ thống cho khóa ở tầng cao hơn, có thể chính là KEK do KMS bảo vệ. Không mặc định mọi hệ thống đều phải có đúng ba tầng.

Ví dụ dữ liệu dài 10 MiB được mã hóa bằng AES-256-GCM dưới DEK 32 byte. KEK chỉ cần bọc **32 byte DEK** (cộng overhead của scheme wrap), không phải nhận cả 10 MiB. Đây là điểm đem lại lợi ích hiệu năng và tách nhiệm vụ.

## 3. Envelope trông như thế nào?

Một thiết kế lưu trữ hợp lý phải ghi đủ metadata để giải mã **đúng** trong tương lai; ví dụ minh họa:

```text
Encrypted Object Envelope
+-------------------------------------------------------+
| envelopeVersion = 1                                   |
| tenantId / objectId / purpose (theo protocol)         |
| dataAlgorithm = AES-256-GCM                           |
| dataNonce (12 bytes)                                  |
| dataCiphertext || dataTag (GCM tag 16 bytes)           |
|                                                       |
| wrappingAlgorithm = KMS-based-wrap / AES-GCM-wrap     |
| kekId = "tenant-A/kek-v3"                              |
| wrapNonce (nếu wrap scheme yêu cầu)                    |
| wrappedDEK || wrapTag (nếu là AEAD-GCM wrap)           |
+-------------------------------------------------------+
```

Trong đó:

- **dataCiphertext** là bản mã của dữ liệu dài, được tạo bằng DEK.
- **wrappedDEK** là DEK sau khi được bảo vệ bằng KEK; không thể thay thế cho DEK để giải mã dữ liệu nếu chưa unwrap.
- **dataNonce** và **wrapNonce** là hai nonce của **hai phép mã hóa riêng**. Nếu dùng GCM, nonce cần duy nhất **theo từng khóa được sử dụng**. Việc thấy nonce trong envelope không tiết lộ khóa (nonce không phải secret).
- **dataTag / wrapTag** cung cấp xác thực AEAD tương ứng cho *mỗi tầng* khi dùng GCM. Tag không được cắt/bỏ tùy tiện.
- **kekId** cho biết dùng KEK nào **đã được đăng ký và cho phép**. Không dùng một URL/key do payload tự khai để thay thế trusted key registry.
- **tenantId**, **objectId** và **purpose** giúp gắn record với đúng ranh giới dữ liệu; các trường ảnh hưởng cách hiểu nội dung phải được **xác thực** qua AAD hoặc cơ chế tương đương.

Sơ đồ hai tầng:

```text
                  DATA PLANE                                KEY PLANE
plaintext data ----------> AES-GCM(plaintext DEK) --> dataCiphertext + dataTag
                                 ^                           |
                                 |                           v
CSPRNG --> plaintext DEK --------+                  lưu dataNonce + ciphertext
            |                    |
            v                    |
  wrap/encrypt under KEK         |
            |                    |
            v                    |
    wrapped DEK + metadata ------+------> lưu cùng encrypted object
            ^
            |
      KEK do KMS/HSM quản lý
       (không nằm trong object)
```

Sơ đồ minh họa chức năng, không phải phân chia máy chủ bắt buộc. Cả ciphertext dữ liệu, wrapped DEK, nonce và metadata có thể cùng nằm trong storage; **chính KEK và plaintext DEK phải được bảo vệ**.

**Cẩn thận với “encrypted DEK” versus “DEK”:** gọi `getEncoded()` của secret key AES có thể đưa **khóa thật** ra mảng byte. Không lưu mảng này vào envelope. Đối tượng envelope chỉ chứa **giá trị đã bọc**; plaintext DEK được xóa/hạn chế lưu giữ sau khi thao tác hoàn tất trong khả năng của runtime.

## 4. Luồng mã hóa: tạo DEK và bọc bằng KEK

Quy trình khi tạo một object:

1. **Xác định chính sách từ phía đáng tin:** tenant, object ID, mục đích, KEK được phép, phiên bản envelope và thuật toán. Không để client tùy ý áp đặt key ID bên ngoài allowlist.
2. **Sinh DEK mới** từ nguồn ngẫu nhiên mật mã, ví dụ 32 byte cho AES-256. Không suy DEK từ object ID, timestamp, mật khẩu yếu hay hash nội dung.
3. **Mã hóa dữ liệu với DEK**, thông thường bằng `AES/GCM/NoPadding` với nonce 12 byte duy nhất dưới DEK, tag 128 bit và AAD gắn context ổn định.
4. **Bọc DEK bằng KEK**: tốt nhất nhờ KMS/HSM hoặc một wrap primitive được chuẩn hóa. Nếu dùng AES-GCM làm thao tác bọc, phải sinh **nonce mới và duy nhất dưới chính KEK** và đưa vào AAD các thuộc tính ràng buộc cần thiết.
5. **Gắn metadata**: key ID phiên bản, thuật toán, nonce/tag, schema version, thông tin context. Lưu kết quả nhất quán với object và kiểm tra giới hạn kích thước trước/ sau mã hóa.
6. **Chỉ commit record hoàn chỉnh** (ciphertext data và wrapped DEK đi cùng nhau). Không công bố một object chưa có đủ tag hoặc key metadata; nếu lưu nhiều dịch vụ, cần giao thức commit/rollback tránh trạng thái không nhất quán.
7. **Giảm tồn tại plaintext DEK** trong bộ nhớ, log, crash dump, trace. Việc ghi zero vào `byte[]` trong Java là best effort; JIT, garbage collector và provider có thể giữ bản sao mà ứng dụng không điều khiển hoàn toàn.

### Cùng một KEK có thể bọc nhiều DEK

Đây là chức năng của KEK, nhưng **KEK là điểm tập trung quyền**. Mỗi lần wrap phải dùng scheme an toàn và tuân thủ nonce/usage limit. Nếu bảo vệ nhiều tenant, tốt nhất phân vùng key policy hoặc KEK theo tenant/rủi ro. Việc tách key theo tenant/object không thay thế xác thực quyền truy cập dữ liệu trước khi gọi unwrap.

### Data AAD và Wrap AAD phải có mục đích rõ

Có thể dùng hai AAD độc lập:

```text
dataAAD = Encode("DATA-v1", tenantId, objectId, dataSchemaVersion)
wrapAAD = Encode("WRAP-v1", tenantId, objectId, kekId, wrappingVersion)
```

`Encode` phải là serialization **không mơ hồ** (length prefix, schema canonical, các trường có kiểu và thứ tự cố định). Không nối tùy ý `("ab" || "c")` và `("a" || "bc")` để hai nhóm metadata có cùng bytes. AAD **không được mã hóa**, nên đừng đưa secret vào AAD với kỳ vọng nó sẽ được che giấu. Nhưng thay đổi AAD khiến tag xác thực GCM thất bại.

Một chi tiết quan trọng cho rotation: nếu chỉ đổi KEK, **dataAAD không nên phụ thuộc trực tiếp vào kekId** trong một mô hình mà bạn mong giữ nguyên data ciphertext/tag. Khi thay KEK, giữ nguyên context dữ liệu để **dataNonce + dataCiphertextAndTag** không đổi; chỉ tạo **wrapped DEK mới** với `wrapAAD` chứa key ID mới. Nếu chính sách của bạn muốn thay cả dataAAD, sẽ phải giải mã và mã hóa lại dữ liệu/tag (hoặc dùng cơ chế protocol khác có xác thực tương đương).

## 5. Luồng giải mã: xác thực hai lớp

Khi đọc object:

1. Kiểm tra **version, format, giới hạn kích thước** và mandatory fields. Xác định tenant/object ID mong đợi từ request được xác thực hoặc trusted storage path; không lấy mọi thông tin xác thực chỉ từ payload do attacker kiểm soát.
2. Tra cứu `kekId` trong **trusted registry/KMS** của tenant/mục đích đã xác định. Kiểm tra quyền gọi unwrap; key ID không phải secret/password.
3. Dùng KEK phù hợp **unwrap wrapped DEK**, kiểm tra tag/wrapping integrity và wrap AAD. **Không dùng DEK** nếu bước unwrap chưa xác thực thành công.
4. Kiểm tra byte DEK đã unwrap có **loại/độ dài đúng**; tạo tạm `SecretKeySpec` (nếu key bytes nằm trong process) hoặc dùng handle phù hợp của provider.
5. Giải mã `dataCiphertextAndTag` bằng DEK, nonce, tag và data AAD tương ứng. Chỉ xem plaintext là hợp lệ **sau khi `Cipher.doFinal` thành công và tag được xác nhận**.
6. Kiểm tra authorization ở mức **object/resource/action** và các ràng buộc nghiệp vụ; không trả plaintext nhạy cảm chỉ vì ai đó có thể lấy wrapped DEK.
7. Không log DEK, KEK, plaintext hoặc thông tin vượt quá yêu cầu vận hành; cố gắng xóa secret tạm trong mức khả thi.

Nếu wrapped DEK bị tráo từ một object khác, wrap AAD gắn `tenantId/objectId/purpose` giúp chặn khi không khớp. Nếu attacker có quyền thay cả metadata chưa xác thực và hệ thống tin ngay vào metadata đó, AAD có thể bị dùng sai ngữ cảnh; vì vậy cần **nguồn expected context đáng tin** ở phía server, không chỉ “dữ liệu tự mô tả” do client gửi.

**Hai tag bảo vệ hai thứ khác nhau** khi dùng GCM ở cả tầng: wrap tag xác thực DEK/metadata của wrap; data tag xác thực ciphertext/metadata của data. Chỉ verify một tag rồi bỏ tag còn lại là sai.

## 6. Java 17 — chương trình hoàn chỉnh: mã hóa envelope và rewrap KEK

Mã dưới đây cố tình không dùng SDK KMS ngoài JDK. Nó sinh **ba KEK trong bộ nhớ** (cũ, mới, sai) bằng `KeyGenerator`, tạo **một DEK 32 byte** qua `SecureRandom`, mã hóa nội dung bằng AES-GCM với DEK, rồi **mã hóa/bọc DEK bằng một KEK khác** cũng qua AES-GCM. Cách dùng GCM để bảo vệ DEK trong ví dụ là **key encryption với authentication**; các KMS/HSM hoặc giao thức thật có thể dùng **AES Key Wrap** hoặc wrapping primitive khác với API/metadata khác.

Mỗi phép `seal` sinh nonce ngẫu nhiên 12 byte mới và sử dụng tag 128 bit. Với KEK tồn tại lâu và số lượng thao tác lớn, không chỉ dựa vào “random thường là không trùng”: phải có **quy tắc không tái sử dụng nonce dưới cùng KEK** và giới hạn usage theo chính sách/thiết kế GCM. Mã demo có hai vùng AAD độc lập `DATA-v1` và `WRAP-v1`, cùng `objectId`; metadata được mã hóa theo length-prefix để tránh ranh giới mơ hồ.

Chương trình chủ ý chứa các test:

- Mở đúng envelope với KEK cũ: **đạt**.
- Dùng **KEK sai**, đổi object ID, sửa data ciphertext/tag, sửa wrapped DEK/tag, làm thiếu số byte tag của wrapped DEK: **từ chối**.
- **Rewrap DEK** sang KEK mới: data nonce và data ciphertext/tag **giữ nguyên**; KEK mới giải mã được, KEK cũ không còn được chấp nhận cho envelope mới.

Tạo đúng tệp **`EnvelopeEncryptionDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class EnvelopeEncryptionDemo {
    private static final int DEK_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Sealed(byte[] nonce, byte[] ciphertextAndTag) {
        Sealed {
            nonce = nonce.clone();
            ciphertextAndTag = ciphertextAndTag.clone();
        }
        @Override public byte[] nonce() { return nonce.clone(); }
        @Override public byte[] ciphertextAndTag() {
            return ciphertextAndTag.clone();
        }
    }

    private record Envelope(String keyId, Sealed wrappedDek, Sealed data) {}

    public static void main(String[] args) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey oldKek = keyGenerator.generateKey();
        SecretKey newKek = keyGenerator.generateKey();
        SecretKey wrongKek = keyGenerator.generateKey();

        String objectId = "invoice-42";
        String oldId = "kek-v1";
        String newId = "kek-v2";
        byte[] plaintext = "amount=250;currency=USD"
                .getBytes(StandardCharsets.UTF_8);

        Envelope before = encryptEnvelope(oldKek, oldId, objectId, plaintext);
        System.out.println("Original round-trip: " + Arrays.equals(
                plaintext, decryptEnvelope(before, oldKek, oldId, objectId)));
        System.out.println("Wrong KEK rejected: " + rejects(
                () -> decryptEnvelope(before, wrongKek, oldId, objectId)));
        System.out.println("Wrong object ID rejected: " + rejects(
                () -> decryptEnvelope(before, oldKek, oldId, "invoice-43")));

        byte[] changedData = before.data().ciphertextAndTag();
        changedData[0] ^= 1;
        Envelope forgedData = new Envelope(oldId, before.wrappedDek(),
                new Sealed(before.data().nonce(), changedData));
        System.out.println("Changed data rejected: " + rejects(
                () -> decryptEnvelope(forgedData, oldKek, oldId, objectId)));

        byte[] changedWrap = before.wrappedDek().ciphertextAndTag();
        changedWrap[changedWrap.length - 1] ^= 1;
        Envelope forgedWrap = new Envelope(oldId,
                new Sealed(before.wrappedDek().nonce(), changedWrap),
                before.data());
        System.out.println("Changed DEK wrap rejected: " + rejects(
                () -> decryptEnvelope(forgedWrap, oldKek, oldId, objectId)));

        Envelope tooShort = new Envelope(oldId,
                new Sealed(before.wrappedDek().nonce(), new byte[TAG_BYTES - 1]),
                before.data());
        System.out.println("Short wrapped DEK rejected: " + rejects(
                () -> decryptEnvelope(tooShort, oldKek, oldId, objectId)));

        Envelope after = rewrap(before, oldKek, oldId, newKek, newId, objectId);
        System.out.println("Rewrap keeps data bytes: "
                + Arrays.equals(before.data().nonce(), after.data().nonce())
                + " / " + Arrays.equals(before.data().ciphertextAndTag(),
                        after.data().ciphertextAndTag()));
        System.out.println("New KEK decrypts: " + Arrays.equals(
                plaintext, decryptEnvelope(after, newKek, newId, objectId)));
        System.out.println("Old KEK rejected after rewrap: " + rejects(
                () -> decryptEnvelope(after, oldKek, newId, objectId)));
    }

    private static Envelope encryptEnvelope(
            SecretKey kek, String kekId, String objectId, byte[] plaintext)
            throws GeneralSecurityException {
        byte[] dek = new byte[DEK_BYTES];
        RANDOM.nextBytes(dek);
        try {
            Sealed data = seal(new SecretKeySpec(dek, "AES"), plaintext,
                    associatedData("DATA-v1", objectId, ""));
            Sealed wrapped = seal(kek, dek,
                    associatedData("WRAP-v1", objectId, kekId));
            return new Envelope(kekId, wrapped, data);
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    private static byte[] decryptEnvelope(
            Envelope envelope, SecretKey kek, String expectedKekId,
            String objectId) throws GeneralSecurityException {
        if (!envelope.keyId().equals(expectedKekId)) {
            throw new IllegalArgumentException("Unexpected KEK identifier");
        }
        byte[] dek = open(kek, envelope.wrappedDek(),
                associatedData("WRAP-v1", objectId, expectedKekId));
        try {
            if (dek.length != DEK_BYTES) {
                throw new GeneralSecurityException("Unexpected DEK length");
            }
            return open(new SecretKeySpec(dek, "AES"), envelope.data(),
                    associatedData("DATA-v1", objectId, ""));
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    private static Envelope rewrap(
            Envelope original, SecretKey oldKek, String oldId,
            SecretKey newKek, String newId, String objectId)
            throws GeneralSecurityException {
        if (!original.keyId().equals(oldId)) {
            throw new IllegalArgumentException("Unexpected old KEK identifier");
        }
        byte[] dek = open(oldKek, original.wrappedDek(),
                associatedData("WRAP-v1", objectId, oldId));
        try {
            if (dek.length != DEK_BYTES) {
                throw new GeneralSecurityException("Unexpected DEK length");
            }
            Sealed wrapped = seal(newKek, dek,
                    associatedData("WRAP-v1", objectId, newId));
            return new Envelope(newId, wrapped, original.data());
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    private static Sealed seal(SecretKey key, byte[] input, byte[] aad)
            throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return new Sealed(nonce, cipher.doFinal(input));
    }

    private static byte[] open(SecretKey key, Sealed sealed, byte[] aad)
            throws GeneralSecurityException {
        byte[] nonce = sealed.nonce();
        byte[] ciphertextAndTag = sealed.ciphertextAndTag();
        if (nonce.length != NONCE_BYTES || ciphertextAndTag.length < TAG_BYTES) {
            throw new IllegalArgumentException("Invalid sealed field");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertextAndTag);
    }

    private static byte[] associatedData(
            String domain, String objectId, String kekId) {
        byte[] d = domain.getBytes(StandardCharsets.UTF_8);
        byte[] o = objectId.getBytes(StandardCharsets.UTF_8);
        byte[] k = kekId.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(12 + d.length + o.length + k.length)
                .putInt(d.length).put(d)
                .putInt(o.length).put(o)
                .putInt(k.length).put(k)
                .array();
    }

    @FunctionalInterface
    private interface Operation { void run() throws GeneralSecurityException; }

    private static boolean rejects(Operation operation) {
        try {
            operation.run();
            return false;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return true;
        }
    }
}
```

Biên dịch và chạy với JDK 17+ có AES-256-GCM:

```bash
javac --release 17 EnvelopeEncryptionDemo.java
java EnvelopeEncryptionDemo
```

**Stdout** (đã biên dịch/chạy kiểm thử bằng Java 17):

```text
Original round-trip: true
Wrong KEK rejected: true
Wrong object ID rejected: true
Changed data rejected: true
Changed DEK wrap rejected: true
Short wrapped DEK rejected: true
Rewrap keeps data bytes: true / true
New KEK decrypts: true
Old KEK rejected after rewrap: true
```

Chương trình dùng các khóa và nonce ngẫu nhiên, **không in ra key material**, nên chuỗi ciphertext thay đổi mỗi lần chạy nhưng 9 dòng boolean trên phản ánh kết quả mong đợi. Nếu Java/provider không hỗ trợ transformation hoặc key size theo chính sách hiện hành, các bước `getInstance`/`init` có thể báo lỗi; đừng lặng lẽ hạ key length/mode để “cho chạy”.

### Tại sao demo này thể hiện đúng nguyên lý?

- `encryptEnvelope` sinh **DEK riêng** và dùng nó mã hóa **plaintext data**; `kek` chỉ bọc **DEK**, không tham gia trực tiếp mã hóa nội dung.
- `Sealed` lưu nonce và `ciphertextAndTag`; mảng byte được clone khi tạo/trả về record để các phép thử thay đổi đầu vào **không âm thầm sửa envelope ban đầu**.
- `associatedData` mã hóa domain, object ID và key ID bằng UTF-8 với **ba độ dài int big-endian**. Mỗi bộ trường có ranh giới xác định; đây là minh họa encoding nội bộ, không tự nhận là chuẩn interoperable công khai.
- `DATA-v1` và `WRAP-v1` là domain separation: dùng cùng tên biến “context” không có nghĩa các byte AAD của hai tầng phải giống nhau. `kekId` chỉ ràng buộc vào wrap AAD nên rewrap không thay data ciphertext.
- `decryptEnvelope` từ chối nếu key ID envelope không bằng **expectedKekId** mà caller đã quyết định. Trong production, expected ID phải được chọn **qua trusted tenant/key registry**, không được “trust” chỉ bằng việc sao chép `envelope.keyId()` vào biến expected.
- Khi `Cipher.doFinal` kiểm tra GCM tag thất bại, Java thường ném `AEADBadTagException` (kế thừa `BadPaddingException`). Hàm test `rejects` gộp các lỗi chỉ để hiển thị; API thực phải phân loại nội bộ và trả lỗi public không tạo oracle.
- `rewrap` cần giải mã **DEK**, nhưng **không bao giờ gọi `open` trên data ciphertext**. Kết quả data nonce và data ciphertext/tag giữ nguyên, kiểm tra bằng `Arrays.equals`.
- `Arrays.fill(dek, (byte) 0)` cố gắng xóa bản byte[] tạm, **không bảo đảm** xóa mọi bản sao trong JVM/hardware; trong production, ưu tiên KMS/HSM với tính năng không xuất KEK và quản lý heap/secret lifecycle chặt.

### Giới hạn của ví dụ cần nhận diện

Mã chạy độc lập chỉ xử lý **một object và một lần chuyển KEK**. Nó không lưu envelope ra file/database, không kiểm tra IAM, không cung cấp transaction/rotation lock, không serialize toàn bộ cấu trúc để chống lỗi parse từ dữ liệu không đáng tin, và không thực hiện key discovery hay gọi KMS. `Operation`/`rejects` giúp trình bày trường hợp từ chối, không nên dùng trực tiếp làm API contract.

Đặc biệt, `Sealed` trong demo dùng AES-GCM cho **cả data lẫn wrap**. Với KMS/HSM thực, có thể sử dụng cơ chế wrapping chuyên dụng, chẳng hạn AES Key Wrap hay thao tác KMS `Encrypt`/`Decrypt`/`GenerateDataKey`/`ReEncrypt` tùy nhà cung cấp. **Không** tự chuyển đổi dữ liệu GCM-wrapped thành output AESWrap: thuật toán, IV, tag và format **khác nhau**.

## 7. Rewrap, re-encrypt và rotate: ba việc khác nhau

Từ “xoay khóa” dễ gây hiểu nhầm vì có nhiều tầng:

### 7.1 Rewrap (đổi KEK, giữ DEK)

```text
TRƯỚC:
plaintext data -- encrypt(DEK1) --> ciphertextData1
DEK1           -- wrap(KEK-A) --> wrappedDEK-A

SAU REWRAP:
ciphertextData1             [KHÔNG ĐỔI]
DEK1           -- wrap(KEK-B) --> wrappedDEK-B
keyId: KEK-A -> KEK-B
```

Quá trình cần quyền **unwrap với KEK-A** và **wrap với KEK-B**. Tùy dịch vụ KMS, thao tác có thể được thực hiện trong ranh giới dịch vụ khóa mà không xuất DEK ra ứng dụng, hoặc cần mở DEK trong một vùng tin cậy để rewrap.

**Ưu điểm:** không đọc/mã hóa lại data file 10 GiB; chỉ xử lý một DEK ngắn và metadata. Chi phí phụ thuộc số object/DEK phải chuyển, số lời gọi KMS và storage metadata. Nếu có 1 triệu object, vẫn phải rewrap khoảng 1 triệu wrapped DEK nếu mỗi object có DEK riêng: **không phải thao tác O(1) cho cả kho**.

**Yêu cầu vận hành:** ghi wrapped DEK mới + key ID + metadata liên quan **nguyên tử** hoặc bằng quy trình có thể recover. Không xóa KEK-A trước khi tất cả object/backup cần thiết đã chuyển và quy trình khôi phục xác nhận có thể dùng KEK-B.

### 7.2 Rotate DEK (đổi khóa mã hóa dữ liệu)

Nếu chính DEK bị lộ hoặc vượt giới hạn sử dụng, tạo **DEK2 mới**, giải mã data bằng DEK1 rồi **mã hóa lại** bằng DEK2 với nonce/AAD phù hợp và lưu ciphertext/tag mới. Sau đó bọc DEK2 bằng KEK đã được phê duyệt.

```text
dataCiphertext1 -- decrypt(DEK1) --> plaintext
plaintext       -- encrypt(DEK2) --> dataCiphertext2
DEK2            -- wrap(KEK-B)  --> wrappedDEK2
```

Công việc này tác động toàn bộ dữ liệu và phải xử lý cẩn thận **plaintext tạm**, storage, timeout, tính nhất quán, checksum/integrity và rollback.

**Không đánh đồng rewrap với DEK rotation.** Nếu DEK1 đã bị lộ, chỉ rewrap dưới KEK mới **không làm DEK1 trở lại bí mật**.

### 7.3 KMS-managed rotation (thay key material bên trong dịch vụ)

Một số KMS cung cấp **automatic rotation** cho khóa được quản lý, có thể giữ các phiên bản key material cũ để giải mã ciphertext lịch sử và tự áp dụng phiên bản mới cho mã hóa mới. Tùy provider/key type, **automatic rotation không có nghĩa mọi wrapped DEK cũ được rewrap ngay** và cũng không có nghĩa key ID của từng object thay đổi.

Phải đọc đúng semantics của nhà cung cấp: alias thay đổi, phiên bản key, ciphertext chứa metadata key nào, quyền decrypt bản cũ, thời gian chờ, cách backup/migration và giới hạn API. **Không tự suy** rằng bật một nút “rotation” sẽ xóa tác động của dữ liệu khóa từng bị lộ.

### 7.4 Sự cố lộ khóa và bản sao cũ

| Sự kiện | Hành động thường cần | Vì sao chỉ rewrap không đủ trong một số trường hợp |
| --- | --- | --- |
| Chuyển quản lý từ KEK-A sang KEK-B, chưa có dấu hiệu lộ | Rewrap DEK + chuyển policy, kiểm thử backup/restore | Đây là đúng use case hiệu quả của rewrap |
| Quyền một service bị thu hồi | Giảm quyền KMS + credential rotation + audit + đánh giá cache plaintext/DEK | Service có thể đã trích xuất DEK hoặc dữ liệu khi còn quyền |
| **KEK-A bị lộ** | Incident response, ngăn dùng KEK-A, đánh giá ciphertext/wrapped DEK cũ và backup, rewrap và có thể re-encrypt dữ liệu | Ai đã lưu bản sao **wrappedDEK-A + ciphertextData** và KEK-A có thể vẫn giải mã bản sao cũ; rewrap bản hiện tại **không thu hồi được snapshot** |
| **DEK bị lộ** | Re-encrypt dữ liệu liên quan bằng DEK mới, bọc lại, thu hồi bản cũ theo quy trình | KEK mới không làm thay đổi việc attacker biết DEK cũ |
| Xóa dữ liệu (“crypto-erasure”) | Quy trình hủy key liên quan, backup/cache/snapshot, kiểm soát retention | Xóa một wrapped DEK không bảo đảm không còn bản sao hoặc plaintext ở nơi khác |

**Tính bảo mật lịch sử không thể được tạo ngược bởi rotation.** Khi một khóa từng bị lộ, phải đánh giá dữ liệu attacker **đã lấy được**, backup, snapshot, log, các bản DEK/KEK được cache, chứ không chỉ nhìn vào khóa hiện hành.

## 8. KMS/HSM trong mô hình envelope encryption

### 8.1 Dịch vụ khóa thường làm gì?

Một API kiểu `GenerateDataKey` có thể trả:

- **Plaintext DEK** để ứng dụng mã hóa dữ liệu ngay tại chỗ (nếu API/policy cho phép).
- **Encrypted/wrapped DEK** để lưu cùng encrypted object.
- Metadata cần thiết như KMS key ID và encryption context, tùy dịch vụ.

Sau khi mã hóa dữ liệu, ứng dụng không lưu plaintext DEK. Lần giải mã về sau gửi **wrapped DEK** cùng **encryption context**/metadata thích hợp tới KMS theo quyền được cấp; KMS kiểm tra policy và trả về DEK plaintext hoặc thực hiện unwrap trong ranh giới bảo vệ tương ứng. Tên API, thành phần response và semantics phụ thuộc nhà cung cấp.

Một số HSM/KMS có thể giữ KEK **non-exportable**, giới hạn chỉ wrap/unwrap/reencrypt. Tuy vậy nếu API trả plaintext DEK, ứng dụng vẫn phải bảo vệ DEK khi nó đi qua process memory. **“KEK không xuất được” không đồng nghĩa plaintext DEK chưa từng ở trong JVM**.

### 8.2 Policy và audit quan trọng ngang thuật toán

- Chỉ service có quyền, tại đúng tenant/project và context, được phép gọi **wrap/unwrap**.
- Tách role **encrypt-only** (có thể chỉ wrap tạo bản mới) với **decrypt-capable** nếu use case cho phép.
- Bảo vệ key policy, alias/registry, IAM, service identities, network boundary, quota, key deletion schedule.
- Audit **key ID, caller identity, mục đích, object/tenant reference hợp lệ, thời gian và kết quả**. Không ghi DEK plaintext, KEK, plaintext data, request secret.
- Các API encryption context thường coi context là **AAD không bí mật**: đừng đặt mật khẩu/token hoặc PII nhạy cảm vào đó nếu KMS logging/API có thể hiển thị context.
- Kiểm thử permission denial và lỗi provider trước rollout. Không fallback sang khóa hardcoded/local yếu để “tiếp tục chạy”.

### 8.3 So sánh các cách bảo vệ DEK

| Cách bọc DEK | Mô hình | Lưu ý |
| --- | --- | --- |
| **AES-GCM với KEK** | Mã hóa DEK thành ciphertext + tag, cần nonce duy nhất dưới cùng KEK | Có thể làm theo JCA; phải serialize nonce/tag và ràng buộc AAD; demo dùng cách này |
| **AES Key Wrap (RFC 3394)** | Primitive dành riêng cho đóng gói key material | Có kiểm tra tính toàn vẹn theo scheme; format/độ dài đầu vào khác GCM; tên và hỗ trợ `Cipher` tùy provider |
| **AES Key Wrap with Padding (RFC 5649)** | Biến thể wrap hỗ trợ độ dài key material tổng quát hơn | Không nhầm với PKCS5Padding/GCM; hỗ trợ provider phải kiểm tra |
| **KMS/HSM wrap/unwrap** | KEK do dịch vụ/hardware quản lý, ứng dụng gọi API đã cấp phép | KMS key ID, encryption context, IAM, logging, latency và quota; không giả định thuật toán nội bộ của mọi nhà cung cấp giống nhau |
| **RSA-OAEP bảo vệ DEK** | Public key encrypt DEK ngắn; private key decrypt | Dùng khi kiến trúc bất đối xứng cần thiết, đúng OAEP params/key custody; không dùng raw RSA hoặc RSA PKCS#1 v1.5 cũ khi thiết kế mới |
| **Encrypt data trực tiếp bằng KMS** | Một số dịch vụ cho mã hóa đầu vào nhỏ trực tiếp | Dữ liệu lớn nên dùng DEK cục bộ và KMS chỉ xử lý key; kiểm tra giới hạn kích thước input cụ thể |

**Không** dùng `AES/ECB/NoPadding` thô để “mã hóa DEK”, không dùng phép XOR với một chuỗi cố định, không băm DEK thành “wrapped key”, và không coi Base64/hex là mã hóa. Một primitive mã hóa key phải có tính bảo mật và integrity đúng scheme.

## 9. Nguyên tắc về nonce, AAD và thuật toán

### 9.1 Dữ liệu mã hóa và DEK được bọc đều cần integrity

Nếu thuật toán wrap chỉ cung cấp confidentiality mà thiếu integrity, một DEK bị sửa có thể đi đến giải mã hoặc dẫn tới failure khó phân tích. Dùng AEAD hoặc key-wrap scheme có kiểm tra integrity để không chấp nhận key material bị thay đổi.

Với `AES/GCM/NoPadding`:

- GCM tag thường dài **128 bit**, Java nhận `GCMParameterSpec(128, nonce)` với **độ dài tính theo bit**.
- Nonce **12 byte** là cấu hình điển hình; phải duy nhất **dưới cùng key**. Data encryption dùng **DEK**, wrap encryption dùng **KEK**, nên có hai yêu cầu quản lý nonce riêng.
- `Cipher.updateAAD(...)` phải thực hiện trước `update/doFinal` đối với dữ liệu trong Java GCM; phía decrypt phải dùng **cùng bytes AAD**.
- `Cipher.doFinal(...)` chỉ xuất plaintext hợp lệ khi xác minh tag thành công. Không publish output tạm trước authentication.
- Nếu tag/nonce/AAD/key bị sửa, quá trình giải mã GCM thất bại (`AEADBadTagException` thường gặp). **Không thể biết chắc loại lỗi vật lý chỉ từ tên exception**.

Khi dùng 96-bit random nonce và KEK sống lâu, số lượng lần wrap phải được hạn chế sao cho xác suất va chạm nonce vẫn trong mức rủi ro cho phép, hoặc dùng cơ chế quản lý nonce duy nhất bảo đảm theo policy. **Không reuse nonce trong cùng KEK** chỉ vì hai DEK khác nhau.

### 9.2 Ràng buộc tenant/object/role

Một lỗi nguy hiểm là lấy wrapped DEK của tenant A gắn vào ciphertext tenant B rồi **trông đợi tag data tự giải quyết tất cả**. Tag data có thể fail khi key khác, nhưng chỉ riêng điều đó không tạo được mô hình ủy quyền đúng.

Các lớp bảo vệ nên đồng thời bao gồm:

1. **Access control:** chỉ được lấy object và gọi unwrap cho đúng tenant.
2. **Trusted key ID mapping:** `tenantId + purpose + kekId` ánh xạ tới KEK đã cấp quyền.
3. **Wrap AAD:** ràng buộc DEK được bọc với object/tenant/purpose và phiên bản thích hợp.
4. **Data AAD:** ràng buộc ciphertext với object/tenant/purpose, format/version thích hợp.
5. **Authenticated data format:** đảm bảo cả bên tạo và bên đọc thống nhất representation của metadata trước khi tính tag.

AAD không giữ bí mật metadata; cần thêm encryption nếu chính các trường metadata cũng phải được giấu. Nhưng không được bỏ AAD chỉ vì “đã có ciphertext tag”.

### 9.3 Đừng đưa `kekId` vào data AAD nếu cần giữ nguyên data khi rewrap

Hàm `associatedData("DATA-v1", objectId, "")` trong demo không chứa `kekId`; `associatedData("WRAP-v1", objectId, kekId)` có chứa. Nhờ vậy sau rewrap:

```text
dataNonce:              giữ nguyên
dataCiphertextAndTag:   giữ nguyên
wrappedDEK:             thay đổi
wrapNonce:              thay đổi
kekId:                  thay đổi
```

Đây không phải “bỏ xác thực key ID”: `kekId` vẫn **được kiểm tra ở trusted key mapping** và **được wrap AAD xác thực**. Việc phân tách đúng metadata theo mục đích giúp cả integrity và rotation hoạt động nhất quán.

## 10. Lưu trữ và giao dịch nhất quán khi rewrap hàng triệu object

Một hệ thống quản lý object có thể cần migrate từ KEK version 1 sang 2, trong khi vẫn phục vụ traffic bình thường. Những trường hợp khó thường nằm ở **tính nhất quán**, không phải ở `Cipher`:

1. **Đọc snapshot/version của object:** lấy data ciphertext/tag, wrapped DEK, key ID, context và version metadata trong một phiên bản nhất quán.
2. **Authorize old unwrap và new wrap:** không chuyển tất cả object sang key mới nếu thiếu quyền/điều kiện ở một tenant. Đặt giới hạn throughput/KMS quota.
3. **Rewrap và xác minh:** dùng provider/KMS đúng key IDs; ít nhất kiểm thử decrypt một tập mẫu với new KEK theo policy. Đối với mỗi object, nếu unwrap/authentication thất bại, **không ghi đè wrapped DEK cũ**.
4. **Atomic compare-and-swap:** chỉ cập nhật `(keyId, wrappedDEK, wrapMetadata, envelopeVersion)` nếu object chưa bị ghi đồng thời hoặc có cùng ETag/version. Nếu có concurrent update, re-read record và áp dụng retry có giới hạn thay vì ghi đè dữ liệu mới.
5. **Đảm bảo khả năng khôi phục:** có state máy migrate, retry idempotent, kiểm tra checksum/record counts, ghi log không chứa key, đủ dữ liệu khôi phục nếu job bị dừng nửa chừng.
6. **Hỗ trợ đọc cả hai KEK tạm thời:** trong thời gian rollout, service phải dùng đúng key ID để giải mã object phiên bản cũ/mới; khi không còn dependency, mới thu hồi old unwrap theo kế hoạch.
7. **Backup và restore:** snapshot có key IDs/wrapped DEKs đúng version; nếu đã hủy KEK cũ, những bản backup cũ cần được xem xét để tránh mất khả năng phục hồi. Không assume object hiện tại rewrap xong là mọi replica/backups cũng xong.
8. **Xác nhận điều kiện dừng:** đối chiếu inventory và audit log, đo tỷ lệ lỗi đọc, exception theo key version; áp dụng cutover/disable old key có kế hoạch.

**Ví dụ sai:** ghi `kekId="v2"` trước, nhưng vẫn giữ `wrappedDEK` của v1; khi đọc, service chọn KEK-v2 và unwrap thất bại. Ví dụ sai khác: bọc DEK bằng v2 xong nhưng lưu object thiếu tag hoặc wrap nonce. **Nhóm trường liên quan phải cập nhật cùng nhau**.

## 11. Xử lý ngoại lệ và phân loại lỗi

| Triệu chứng | Nguyên nhân có thể có | Cách xử lý đúng |
| --- | --- | --- |
| `AEADBadTagException` khi unwrap DEK bằng AES-GCM | Sai KEK, wrap AAD khác, nonce/tag/ciphertext wrap bị sửa hoặc cắt | Từ chối DEK; không thử sửa tag hoặc đoán key; kiểm tra metadata và source bytes nội bộ |
| `AEADBadTagException` khi giải mã data | Sai DEK, data AAD/nonce không khớp, data ciphertext/tag hỏng | Không xuất plaintext; kiểm tra object consistency, context và storage transport |
| `IllegalArgumentException` vì nonce/tag quá ngắn | Format không đủ minimum, metadata không hợp lệ | Chặn ở parser trước khi cấp phát lớn/gọi provider |
| `InvalidKeyException` khi `Cipher.init` | Key không hợp lệ, sai loại, provider/key policy không chấp nhận | Kiểm tra key type/size và policy; không fallback yếu |
| KMS báo access denied | IAM/key policy không cho phép unwrap | Từ chối và audit; **không log DEK** hay dùng một KEK local mặc định |
| KMS rate limit/timeout | Quota hoặc sự cố mạng; không liên quan trực tiếp ciphertext integrity | Retry với backoff/jitter, deadline và giới hạn; tránh giải mã sai hay leak secret |
| `kekId` không có trong registry | Metadata lỗi, key đã bị thu hồi hoặc sender không được dùng key đó | Không tải key ngoài allowlist; trả lỗi được chuẩn hóa |
| Rewrap thành công nhưng đọc lại bằng new KEK fail | Mismatch wrap AAD, key ID, format/serialization hoặc update không atomic | Chặn rollout, kiểm tra theo record version, rollback có kiểm soát |
| Decrypt thành công nhưng object không đúng tenant | Thiếu authorization/context binding; lỗi thiết kế logic | Kiểm tra quyền dữ liệu, tenant ID trong AAD và trusted resource mapping |
| Chỉ xóa KEK cũ, sau đó không restore được backup | Các bản sao cũ vẫn phụ thuộc KEK đã xóa | Diễn tập restore trước key deletion; quản lý retention theo phiên bản |

**Lỗi từ tầng bọc và lỗi từ tầng dữ liệu không nên bị trộn trong monitoring nội bộ**, nhưng phản hồi public không được tiết lộ chi tiết về key material hoặc trạng thái xác thực làm tăng lợi thế của đối thủ. Không giải mã một phần data khi tag chưa được xác nhận; không thử hoán đổi key ID/nonce/tag theo danh sách cho đến khi “giải được”.

## 12. Những câu hỏi thiết kế thường gặp

### 12.1 Có bắt buộc mỗi record một DEK không?

Không tuyệt đối. Một DEK có thể phục vụ nhiều record nếu giao thức có nonce management, usage limits và threat model đầy đủ. Tuy nhiên **DEK riêng theo object/nhóm nhỏ** thường giúp thu hẹp phạm vi khi lộ DEK, dễ phân quyền vòng đời, xóa object và re-encrypt. Đổi lại, chi phí là số lượng wrapped DEK, metadata và KMS calls tăng. Chọn độ hạt phù hợp với dung lượng, số lượng object, tốc độ truy cập và khả năng quản lý.

### 12.2 Có nên cache plaintext DEK để giảm độ trễ không?

Caching làm giảm số lời gọi KMS nhưng **kéo dài thời gian tồn tại và phạm vi lộ DEK**. Nếu cần, phải có TTL, giới hạn entry, tenant separation, quyền process, lifecycle eviction, giới hạn debug/heap dump, audit và đánh giá nguy cơ replay/stale key policy. **Cache encrypted DEK** không có nguy cơ tương tự như cache plaintext DEK; dù vậy cache metadata cũng cần xử lý nhất quán với rotation.

### 12.3 Có thể lấy một khẩu lệnh chung làm KEK không?

Không nên coi một chuỗi cấu hình dễ đoán như `"master-password-123"` là KEK. KEK phải có entropy, key management và policy thích hợp. Nếu mô hình thực sự buộc bắt đầu từ **mật khẩu người dùng**, cần password-based KDF (như Argon2id/scrypt/PBKDF2 theo kiến trúc và môi trường), salt, chi phí chống đoán và thiết kế phù hợp; không đơn giản gọi `password.getBytes()` làm AES key. Mô hình này khác với KMS-backed KEK có entropy ngẫu nhiên.

### 12.4 KEK và DEK có thể đều là AES-256 không?

**Có**, cùng loại thuật toán AES nhưng là **hai khóa độc lập với vai trò khác nhau**. Cụm “tách khóa” không chỉ là thay tên biến: phải sinh key material độc lập, giới hạn truy cập và chức năng, bảo vệ KEK bằng biện pháp mạnh hơn, quản lý version và tránh dùng lại cùng một key cho data và wrap. Nếu cả hai là một mảng 32 byte giống hệt thì bạn chưa đạt được key separation.

### 12.5 Bọc DEK với public key của người nhận được không?

Có, nếu thiết kế dùng **RSA-OAEP** hoặc KEM/HPKE được chuẩn hóa, bạn có thể mã hóa/bọc DEK ngắn cho public key người nhận; chỉ bên có private key mới unwrap được. Khi này “KEK” có thể là khái niệm **key-encryption mechanism** bất đối xứng thay vì một khóa AES đối xứng. Nhưng vẫn cần authentic public key của người nhận, đúng scheme, tham số OAEP/KEM và chính sách bảo vệ private key; **đừng dùng RSA raw hoặc tự chế hybrid protocol**.

### 12.6 Có thể dùng “wrapped DEK” làm token cấp quyền không?

**Không.** Wrapped DEK là ciphertext của key material, không phải capability token tự chứng thực quyền người gọi. Hệ thống vẫn phải xác thực người dùng/service, authorize object, giới hạn KMS unwrap và ràng buộc tenant/context. Ai được đọc envelope thường có thể thấy wrapped DEK; điều này **không đồng nghĩa** họ có quyền KMS để mở nó.

### 12.7 Crypto-erasure có đủ để xóa dữ liệu không?

Ý tưởng **crypto-erasure** là xóa vĩnh viễn material cần để giải mã; nếu mỗi object có DEK riêng và không còn bản sao DEK/wrapped DEK có thể unwrap, dữ liệu có thể trở nên không thể khôi phục về mặt mật mã. Nhưng không thể khẳng định hoàn tất chỉ vì xóa một row: cần đánh giá DEK cache, KEK version còn sống, snapshots, backups, bản sao plaintext, log và các máy chủ đã giải mã trước đó. Hủy KEK dùng chung để xóa một object có thể **vô tình xóa khả năng giải mã của toàn bộ object khác**.

### 12.8 Envelope encryption có tự làm dữ liệu “end-to-end encrypted” không?

Không. Nếu server xử lý plaintext DEK và plaintext data, server nằm trong vùng tin cậy của hệ thống. End-to-end encryption có mô hình key custody và endpoint khác; envelope encryption có thể là một thành phần nhưng không tự tạo E2EE. Cần chỉ rõ **ai có thể unwrap, ai nhìn thấy plaintext, ở đâu key được giải mã**, chứ không chỉ đếm số lớp encryption.

## 13. Checklist production và kiểm thử

### Bảo mật khóa

- [ ] DEK và KEK khác nhau cả về **key material, role, quyền sử dụng và vòng đời**.
- [ ] Plaintext DEK chỉ tồn tại tạm thời trong ranh giới được kiểm soát; KEK nằm trong KMS/HSM/keystore theo policy.
- [ ] CSPRNG đúng, nonce GCM **không tái sử dụng dưới cùng khóa** và có quản lý usage limits.
- [ ] Có trusted key ID registry; tenant, object, purpose/role được bind vào context xác thực.
- [ ] Không ghi DEK/KEK/plaintext vào log, trace, metrics, exception hoặc metadata envelope.
- [ ] Giao thức không dùng ECB/raw RSA/hash làm primitive bọc khóa.

### Envelope và integrity

- [ ] Format có version, algorithm allowlist, key ID, nonce/tag và canonical byte representation.
- [ ] Data AAD ổn định theo object/tenant/purpose; wrap AAD ràng buộc key ID/context, cho phép rewrap độc lập khi đó là mục tiêu thiết kế.
- [ ] Parser chặn thiếu header/tag và giới hạn độ dài trước khi cấp phát/gọi `Cipher`.
- [ ] Đọc/ghi object và wrapped DEK nhất quán; không expose plaintext trước khi xác minh tag cả tầng unwrap lẫn tầng data.
- [ ] Có test đổi 1 bit ciphertext/tag/nonce, dùng KEK sai, sai object ID/tenant/AAD, thiếu tag, thay key ID, sai version.
- [ ] Đối với KMS, kiểm thử key permission, status, quota/timeout, key rotation và policy revocation.

### Rotation và vận hành

- [ ] Phân biệt **KEK rewrap** và **DEK re-encryption**, có runbook riêng.
- [ ] Rewrap cập nhật atomic `wrappedDEK + keyId + wrap metadata` và có ETag/concurrency guard.
- [ ] Tạm hỗ trợ đúng key versions cần đọc; xóa khóa cũ **chỉ sau** khi xác minh các object, backup và restore path.
- [ ] Có migration metrics, audit trail, batch size, retry idempotent và rollback.
- [ ] Có kế hoạch incident response nếu KEK/DEK bị lộ, gồm bản sao lịch sử và backup.

## 14. Bài tập thực hành

1. Vẽ sơ đồ envelope cho hệ thống lưu hóa đơn của ba tenant. Chọn một DEK mỗi invoice hay mỗi tenant? KEK ở cấp nào? Giải thích trade-off về KMS calls và phạm vi lộ khóa.
2. Chạy `EnvelopeEncryptionDemo.java`. Chỉ ra chỗ DEK được sinh, dùng mã hóa dữ liệu, bọc, unwrap và xóa tạm. KEK có bao giờ trực tiếp mã hóa `plaintext` ban đầu không?
3. Tại sao test `Wrong object ID rejected` thành công dù `objectId` không nằm trong plaintext? Nếu lấy object ID tùy ý từ payload rồi dùng làm AAD, rủi ro identity binding còn gì?
4. Thêm `tenantId` vào `associatedData` bằng length prefix. Tạo test tráo wrapped DEK giữa hai tenant khác nhau rồi chứng minh không thể giải mã sai context.
5. Phân biệt `Rewrap keeps data bytes: true / true` với DEK rotation. Vì sao rewrap **không thay data ciphertext/tag** nhưng đổi DEK thường phải thay chúng?
6. Giả sử công ty làm lộ KEK-v1 và attacker từng lưu snapshot của `wrappedDEK-v1`. Rewrap record hiện tại sang v2 có làm snapshot cũ mất giá trị không? Hãy viết kế hoạch xử lý sự cố.
7. Xây dựng mô hình database với `object_version`, `kek_id`, `wrapped_dek`, `wrap_nonce`, `data_nonce` và `data_ciphertext_and_tag`. Phác thảo câu lệnh cập nhật compare-and-swap cho rewrap khi có writer đồng thời.
8. Thiết kế thử nghiệm KMS: unwrap permission denied, key rotation, key disabled, old backup restore, KMS unavailable. Chỉ rõ tình huống nào nên retry, tình huống nào phải fail closed.
9. Giải thích vì sao cùng một mã `AES/GCM/NoPadding` được demo dùng cho hai vai trò khác nhau vẫn là **key separation** và vì sao không nên reuse nonce dưới cùng KEK.
10. Nếu DEK của một object cần xóa vĩnh viễn, những bản sao plaintext key/encrypted key, backup/cache/snapshot nào phải xét trước khi tuyên bố crypto-erasure đã hoàn tất?

## Tài liệu tham khảo

- [NIST SP 800-38D — GCM/GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final): yêu cầu về authenticated encryption, IV/nonce và tag.
- [NIST SP 800-57 Part 1 Rev. 5 — Recommendation for Key Management](https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final): thuật ngữ, key management, lifecycle và cryptoperiod.
- [RFC 3394 — Advanced Encryption Standard (AES) Key Wrap Algorithm](https://www.rfc-editor.org/rfc/rfc3394): phương pháp bọc khóa AES.
- [RFC 5649 — AES Key Wrap with Padding Algorithm](https://www.rfc-editor.org/rfc/rfc5649): biến thể có padding phục vụ key material độ dài tổng quát hơn.
- [Java SE 17 — `Cipher`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): `AES/GCM/NoPadding`, `updateAAD`, `doFinal`, authentication failure và `WRAP_MODE`.
- [Java SE 17 — `GCMParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): tag length tính bằng bit và IV/nonce.
- [Java SE 17 — `KeyGenerator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyGenerator.html): sinh khóa AES bằng provider.
- [Java SE 17 — `SecretKeySpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/SecretKeySpec.html): khóa byte được đưa vào JCE; chỉ là wrapper trong bộ nhớ, không thay thế KMS.
- [Java SE 17 — Java Cryptography Architecture Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): providers, key generation, Cipher/JCE và kiến trúc dịch vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-lifecycle-006">11.6 Quản lý IV hoặc nonce khi có nhiều luồng, nhiều tiến trình và khởi động lại</a>

<details>
<summary>Xem chi tiết</summary>

# 11.6 Quản lý IV hoặc nonce khi có nhiều luồng, nhiều tiến trình và khởi động lại

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích vì sao **IV/nonce là tài nguyên phải quản lý theo vòng đời khóa**, chứ không chỉ sinh một mảng byte lúc gọi `Cipher.init(...)`.
2. Phân biệt yêu cầu **duy nhất (uniqueness)** của AES-GCM, AES-CTR, ChaCha20-Poly1305 và yêu cầu **khó dự đoán (unpredictability)** của IV CBC; không áp dụng một công thức cho mọi mode.
3. Xây cơ chế cấp nonce an toàn cho **nhiều thread trong một JVM**, hiểu giới hạn của `AtomicLong`/`synchronized` khi có nhiều process hoặc nhiều host.
4. Thiết kế **counter bền vững**, kho cấp dải số (range leasing) và phân vùng namespace nonce theo key/worker; biết thứ tự **reserve → durable commit → encrypt**.
5. Giải thích cách xử lý **crash, restart, retry, deploy song song, backup rollback và failover**, và khi nào bắt buộc **rotate sang khóa mới**.
6. Kiểm thử Java 17 với AES-GCM: 1.600 phép mã hóa chạy nhiều thread và ví dụ cấp sequence qua nhiều JVM bằng `FileChannel`/`FileLock` có giới hạn rõ ràng.

## 1. IV/nonce là gì và vì sao xuất hiện vấn đề vận hành?

**IV (Initialization Vector)** và **nonce (number used once)** là tham số khởi tạo của một số chế độ mã hóa. Trong nhiều mode, IV/nonce **được truyền công khai** bên cạnh ciphertext, không cần giấu như khóa. Nhưng **được công khai không đồng nghĩa tùy ý tái sử dụng**.

Xem bản ghi AEAD:

```text
  key K (bí mật)       nonce N (thường công khai)
        \                  /
         \                /
          AES-GCM encrypt(plaintext, AAD)
                        |
                        v
                  ciphertext || tag

  Dữ liệu lưu/truyền:
  [version] [keyId] [nonce] [ciphertext || tag]
```

Khóa `K` có thể dùng để mã hóa nhiều bản ghi theo **chính sách invocation/key usage**. Nhưng với AES-GCM, **cặp (K,N) không được tái sử dụng khi mã hóa hai tập đầu vào khác nhau**. Bản thân `Cipher.getInstance("AES/GCM/NoPadding")` chỉ thực hiện phép mật mã cho từng instance; nó không sở hữu kho cấp nonce toàn hệ thống.

Một hệ thống có thể triển khai đúng một lần mã hóa riêng lẻ nhưng vẫn nguy hiểm khi:

- Hai thread cùng đọc biến `counter = 7` rồi cùng tạo nonce `...0007`.
- Hai process/JVM trên cùng máy mỗi process khởi tạo counter từ `0`.
- Hai container ở hai node dùng cùng key nhưng không phân chia nonce namespace.
- Restart máy khiến counter in-memory trở về `0` trong khi **khóa cũ vẫn được dùng**.
- Backup/restore hoặc failover đưa database sequence về mốc trước đó.
- Retry request sau timeout tạo ciphertext khác nhưng tái sử dụng nonce đã cấp.
- Dùng cùng một nonce key-pair ở một API nội bộ và một job nền do thiếu quản trị chung.

Bài toán thực sự là **bảo đảm yêu cầu của mode trên tất cả phép mã hóa với cùng vật liệu khóa**, xuyên thread, process, node và thời gian tồn tại của khóa.

## 2. Không phải mode nào cũng yêu cầu IV giống nhau

| Chế độ / thuật toán | Yêu cầu chính đối với IV/nonce | Tái sử dụng gây gì? | Ghi nhớ |
| --- | --- | --- | --- |
| **AES-GCM** | Nonce/IV phải duy nhất theo key trong phạm vi và giới hạn chuẩn; 96 bit thông dụng | Có thể phá confidentiality và integrity, dẫn tới giả mạo tag | Chỉ riêng khác plaintext/AAD **không cứu được** việc reuse |
| **AES-CTR** | Không trùng **counter block** nào với cùng key, kể cả qua nhiều thông điệp | Trùng keystream, lộ quan hệ XOR plaintext | Quản lý cả khối counter bên trong một thông điệp |
| **ChaCha20-Poly1305** | Nonce duy nhất dưới cùng key | Tái sử dụng keystream và rủi ro integrity | Không suy ra an toàn chỉ vì nonce 12 byte |
| **AES-CBC** | IV phù hợp tiêu chuẩn CBC, đặc biệt **khó dự đoán**; dùng IV mới | IV lặp hoặc đoán trước có thể lộ thông tin/cấu trúc | Chỉ counter dễ đoán không phải giải pháp chung cho CBC |
| **AES-ECB** | Không dùng IV | Lộ mẫu khối lặp | Không chọn ECB cho mã hóa payload thông thường |

**Duy nhất** và **khó dự đoán** là hai thuộc tính khác nhau. Với cấu trúc GCM deterministic IV, counter tuần tự có thể an toàn nếu thực sự không lặp dưới cùng key và tuân thủ chính sách. Với CBC, một IV chỉ duy nhất nhưng dễ dự đoán **không tự thỏa yêu cầu CBC**. Vì thế đừng dùng cùng một “nonce generator” cho mọi cipher mode mà không phân tích riêng.

### 2.1 Hậu quả tái sử dụng nonce trong GCM và CTR

GCM dùng cơ chế giống CTR cho tính bí mật. Nếu cùng key/nonce tạo cùng keystream cho hai bản tin, ta gặp dạng:

```text
C1 = P1 XOR S
C2 = P2 XOR S

C1 XOR C2 = P1 XOR P2
```

Đây là phép mô tả phần mã hóa CTR tương ứng, không phải toàn bộ định dạng GCM bao gồm tag. Khi biết hoặc đoán một plaintext, có thể rò rỉ dữ liệu của bản tin khác. Với GCM, ảnh hưởng còn nghiêm trọng hơn vì cơ chế xác thực cũng có thể bị tấn công/giả mạo. **Một lần reuse cặp key/nonce có thể là sự cố bảo mật**, không phải chỉ là “xác suất nhỏ có thể bỏ qua”.

Với CTR, NIST SP 800-38A yêu cầu **mỗi counter block dùng để mã hóa dưới cùng key phải khác tất cả counter block khác**, kể cả ở các bản tin khác nhau. Điều này mạnh hơn yêu cầu “nonce đầu thông điệp khác”: còn phải kiểm soát **counter wrap/overflow và độ dài thông điệp**.

### 2.2 IV/nonce khác tag, AAD và key ID

- **Key**: bí mật mật mã; đổi **thực sự sang key material mới** tạo miền duy nhất mới.
- **Nonce/IV**: đầu vào khởi tạo; với GCM cần không lặp trong **mỗi key**.
- **AAD (Additional Authenticated Data)**: metadata được bảo vệ tính toàn vẹn nhưng không mã hóa; **AAD khác nhau không hợp thức hóa nonce trùng**.
- **Tag**: kết quả xác thực AEAD; **tag dài 128 bit không chống được lỗi tái sử dụng nonce**.
- **Key ID/version**: cho ứng dụng biết dùng key nào; **đổi nhãn key ID mà vẫn dùng chính byte key cũ không tạo miền mật mã mới**.

Đừng dựa vào việc `Cipher.init` đôi khi từ chối reuse một key/IV trên **cùng đối tượng Cipher**. API không có khả năng bảo đảm không process/host khác đã dùng cặp đó. Phòng vệ phải đặt tại tầng **quản lý khóa và cấp nonce toàn hệ thống**.

## 3. Hai chiến lược cấp nonce GCM phổ biến

### 3.1 Nonce ngẫu nhiên 96 bit từ CSPRNG

Một cách dùng thông thường:

```java
byte[] nonce = new byte[12];
new SecureRandom().nextBytes(nonce);
```

Đây là **đoạn mã nhúng**, không phải chương trình độc lập. Với nguồn ngẫu nhiên mật mã tốt, mỗi lần gọi có nonce 96 bit khó dự đoán. Nhưng **ngẫu nhiên không đồng nghĩa duy nhất tuyệt đối**. Sau nhiều phép mã hóa với cùng key, khả năng trùng tăng theo quy luật birthday bound.

Với `q` nonce được sinh gần đều độc lập từ không gian 96 bit:

```text
P(có ít nhất một cặp trùng)
  ≈ q(q-1) / (2 * 2^96)       khi q << 2^48
```

Ví dụ với `q = 1.000.000`, xác suất xấp xỉ `6,3 × 10^-18` nếu các giả định lý tưởng thực sự đúng. Con số này **không phải bảo đảm** của mọi PRNG/VM/provider và không dùng để bỏ qua giới hạn invocation của chuẩn.

NIST SP 800-38D đặt yêu cầu uniqueness và quy định **giới hạn tổng 2^32 lần gọi authenticated encryption trên một key cho construction dựa vào bộ sinh ngẫu nhiên** (và một số cấu hình khác; xem `8.3). Tổng phải tính **mọi instance và mọi process/host** dùng key đó, không phải “2^32 cho từng thread”.

Ưu điểm: không cần một counter toàn cục trên từng lời gọi. Nhược điểm: xác suất trùng không bằng 0, vẫn phải theo dõi tổng số lần gọi/key và kiểm soát số lượng instance, nguồn entropy, key rotation. Đừng trộn tùy hứng **nonce ngẫu nhiên** và **nonce deterministic** dưới cùng key nếu chính sách/tiêu chuẩn đã chọn một construction.

### 3.2 Nonce deterministic = fixed field || invocation counter

Với 96-bit nonce, một cấu trúc thường dùng là:

```text
96 bit nonce = 32 bit fixed field || 64 bit invocation field

bytes:
[ 4 byte fixed ] [ 8 byte sequence ]

N(0) = fixed || 0000000000000000
N(1) = fixed || 0000000000000001
N(2) = fixed || 0000000000000002
...
```

Nếu **fixed field không đổi** trong cùng namespace và sequence **tăng, không quay lại**, mọi nonce khác nhau. NIST SP 800-38D mô tả cấu trúc deterministic với **fixed field** và **invocation field**; phải phân phối các trường sao cho toàn bộ **cặp key + IV** được duy nhất. Cách chia 32+64 là ví dụ minh họa phổ biến, không mặc định phù hợp mọi giao thức và cấu hình.

Mọi writer dùng **cùng key và cùng fixed field** phải có **một nguồn sequence điều phối thực sự chung**. Nếu các writer được cấp **fixed field khác nhau** dưới cùng key, mỗi writer có thể có counter riêng, nhưng bộ cấp fixed field phải quản lý không trùng và ràng buộc với key/worker/epoch.

**Số 64 bit không phải quyền mã hóa vô hạn.** Vẫn phải kiểm tra capacity của invocation field, budget theo chuẩn, lượng dữ liệu mỗi key, tag length, chính sách key rotation và cảnh báo hoạt động. Không bao giờ để counter wrap về `0`.

## 4. Cấp nonce cho nhiều thread trong cùng một JVM

### 4.1 Race condition với biến thường

Ví dụ không an toàn:

```java
long seq = nextSequence;   // Thread A đọc 17; Thread B đọc 17
nextSequence = seq + 1;
return makeNonce(seq);
```

Ngay cả nếu `long` được đọc/ghi nguyên tử, thao tác **read-modify-write** như trên không phải một transaction. Hai thread có thể cùng lấy giá trị. `volatile` đơn thuần cũng không sửa race này.

Với một JVM, dùng:

- `AtomicLong` với tăng có kiểm tra giới hạn;
- hoặc `synchronized`/lock bao quanh **đúng vùng cấp sequence**;
- một allocator **duy nhất cho mỗi key + fixed field**; không tạo allocator mới theo mỗi HTTP request.

`AtomicLong` đảm bảo các thread trong JVM cấp sequence khác nhau, **không** giúp process/JVM khác nhìn chung counter, **không** lưu tiến độ qua restart.

### 4.2 Thực hành 1: nhiều thread dùng chung allocator và key mới

Chương trình sinh **một AES key mới** cho một lần chạy, một fixed field 4 byte cho allocator đó, cấp nonce dạng `4 + 8`, và thực hiện nhiều phép mã hóa AES-GCM với các thread cùng lúc. Đây là bài kiểm thử **một JVM và một vòng đời key ngắn**, chưa phải giải pháp persistence.

Tạo file **`ConcurrentGcmNonceDemo.java`** (Java 17 trở lên):

```java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class ConcurrentGcmNonceDemo {
    private static final int THREADS = 8;
    private static final int PER_THREAD = 200;
    private static final long KEY_BUDGET = 1_000_000L;

    private static final class Allocator {
        private final byte[] fixedField;
        private final AtomicLong next = new AtomicLong();

        Allocator(byte[] fixedField) {
            if (fixedField.length != 4) throw new IllegalArgumentException();
            this.fixedField = fixedField.clone();
        }

        byte[] take() {
            long sequence = next.getAndUpdate(old -> {
                if (old >= KEY_BUDGET) {
                    throw new IllegalStateException("Rotate key: nonce budget exhausted");
                }
                return old + 1;
            });
            return ByteBuffer.allocate(12)
                    .put(fixedField).putLong(sequence).array();
        }
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey freshKey = generator.generateKey();

        byte[] fixedField = new byte[4];
        new SecureRandom().nextBytes(fixedField);
        Allocator allocator = new Allocator(fixedField);
        Set<String> seen = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int t = 0; t < THREADS; t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < PER_THREAD; i++) {
                        byte[] iv = allocator.take();
                        if (!seen.add(HexFormat.of().formatHex(iv))) {
                            throw new IllegalStateException("Duplicate nonce!");
                        }
                        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(Cipher.ENCRYPT_MODE, freshKey,
                                new GCMParameterSpec(128, iv));
                        cipher.updateAAD("record:v1".getBytes(StandardCharsets.US_ASCII));
                        byte[] sealed = cipher.doFinal(
                                "record".getBytes(StandardCharsets.US_ASCII));
                        if (sealed.length != 6 + 16) {
                            throw new IllegalStateException("Unexpected GCM output");
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            pool.shutdown();
        }

        System.out.println("threads: " + THREADS);
        System.out.println("encryptions: " + THREADS * PER_THREAD);
        System.out.println("unique nonces: " + seen.size());
        System.out.println("nonce bytes: " + allocator.take().length);
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 ConcurrentGcmNonceDemo.java
java ConcurrentGcmNonceDemo
```

Đầu ra đã kiểm tra:

```text
threads: 8
encryptions: 1600
unique nonces: 1600
nonce bytes: 12
```

**Tại sao đúng trong phạm vi demo?**

1. `AtomicLong.getAndUpdate` làm thao tác lấy sequence/tăng sequence nguyên tử trong một JVM; lambda chặn khi đến `KEY_BUDGET`. Không bị cấp lại cùng số dù 8 thread chen nhau.
2. Nonce tạo bằng `ByteBuffer.put(fixedField).putLong(sequence)` có đúng **12 byte**, big-endian; fixed field 4 byte + sequence 8 byte.
3. `ConcurrentHashMap.newKeySet()` kiểm tra trùng nonce trong bài test; tập này không phải thành phần bắt buộc của thuật toán ở production khi có chứng minh uniqueness từ allocator.
4. Mỗi phép mã hóa tạo `Cipher` riêng (đối tượng mutable không mặc định thread-safe), dùng tag GCM 128 bit và AAD trước `doFinal`.
5. `Future.get()` lan truyền lỗi bên trong worker, thay vì im lặng in thành công nếu có task thất bại.
6. Dòng cuối `allocator.take().length` **dành thêm một nonce không dùng để mã hóa** chỉ để kiểm tra độ dài 12 byte. “Đốt bỏ” một số sequence là an toàn; không được tái sử dụng số đó về sau.

**Giới hạn cực kỳ quan trọng:** mỗi lần chạy code **tạo một key AES mới** rồi tạo lại counter từ `0`. Vì key mới, demo không chứng minh vấn đề restart khi giữ key cũ; đừng copy `new Allocator(...)` vào service nơi **cùng key được giữ qua restart**. Nếu process khác dùng cùng key, nó không chia sẻ `AtomicLong` và có nguy cơ đụng sequence.

## 5. Nhiều process/JVM hoặc nhiều node: phải phân chia namespace

Một server có thể có 16 thread, 4 JVM worker, nhiều container chạy đồng thời và job batch. Nếu tất cả cùng mã hóa dưới **một key**, thì namespace nonce phải bao phủ **toàn bộ** các writer đó.

Có ba cách tổ chức cơ bản:

### Cách A — Một bộ cấp sequence có giao dịch bền vững

```text
   Java process A ──┐
   Java process B ──┼──> durable/atomic sequence authority
   Java process C ──┘                |
                                    v
                        [nextSequence per keyId]
                                    |
                    unique reserved ranges / values
                                    |
                                    v
                        nonce = fixed || sequence
```

Cơ quan cấp số có thể là một bảng trong database với update/transaction thực sự **tuyến tính và durable**. Mọi process chỉ dùng số đã được **commit thành công**. Điểm then chốt không phải “đã gọi API tăng counter”, mà là **sau mọi crash/failover, hệ thống không thể cấp lại sequence đã được dùng để mã hóa dưới cùng key**.

Nếu dùng DB, phải đánh giá:

- Transaction atomic khi nhiều transaction cập nhật cùng hàng;
- commit bền vững theo mức chịu lỗi đã tuyên bố;
- việc restore snapshot, replica promotion hoặc failover có thể **lùi lại committed sequence** hay không;
- cơ chế giới hạn quota và key rotation;
- cache hoặc queue có lưu range chưa commit không.

Nếu database rollback sau khi một ciphertext với sequence mới đã được gửi ra ngoài, một lần cấp số mới trùng sequence cũ tạo **reuse**, dù lúc trước code gọi transaction thành công.

### Cách B — Cấp phát range trước, dùng trong process sau

Mỗi lần lấy khóa trong database cho từng thông điệp có thể làm tăng latency. Có thể cấp một **dải số không giao nhau**:

```text
   DB durable counter next = 1000

Process A: reserve [1000, 2000) => commit next = 2000
Process B: reserve [2000, 3000) => commit next = 3000

A dùng 1000, 1001, ...
B dùng 2000, 2001, ...

A bị crash ở số 1050:
[1051, 2000) bị bỏ, TUYỆT ĐỐI KHÔNG trả lại pool.
```

Quy tắc:

1. **Commit durable high-water mark trước khi giao range cho process**.
2. Trong mỗi process, cấp số trong range bằng `AtomicLong`/lock cục bộ.
3. Khi range hết, lấy range mới; **không wrap** hoặc lấy lại range cũ.
4. Sau crash, số trong range chưa dùng được xem như đã tiêu tốn; khoảng trống an toàn, còn reuse thì không.
5. Khi không chắc chắn trạng thái đã commit, **không mã hóa**. Không “fallback” sang counter tại RAM hay reset tạm thời.

Một ví dụ SQL **minh họa kiểu PostgreSQL**, không phải đoạn Java tự chạy:

```sql
-- Bảng nonce_state có một row duy nhất cho mỗi key_id đang hoạt động:
-- (key_id PRIMARY KEY, next_seq BIGINT NOT NULL)
-- :lease_size > 0, :per_key_limit là giới hạn theo chính sách.

BEGIN;
UPDATE nonce_state
SET next_seq = next_seq + :lease_size
WHERE key_id = :key_id
  AND next_seq >= 0
  AND next_seq <= :per_key_limit - :lease_size
RETURNING next_seq - :lease_size AS start_inclusive,
          next_seq               AS end_exclusive;
COMMIT;
```

Phải **lấy kết quả sau khi COMMIT thành công**; không có row hoặc commit lỗi thì **không cấp range**. Hợp đồng thực tế phải giải quyết concurrency, durability/replication và failover của database. Đừng đồng nhất một `RETURNING` xuất hiện trong transaction chưa commit với một range đã được cấp chắc chắn.

### Cách C — Fixed field riêng theo writer, counter riêng trong từng vùng

Nếu không muốn một sequence toàn cục trên mỗi lời gọi, có thể cấp **fixed field riêng** cho từng worker/tenant/node **theo cơ chế trung tâm**, rồi mỗi writer quản lý invocation counter của mình:

```text
same key K
  ├─ writer 01: fixed=0x00000001 || local sequence 0,1,2,...
  ├─ writer 02: fixed=0x00000002 || local sequence 0,1,2,...
  └─ writer 03: fixed=0x00000003 || local sequence 0,1,2,...
```

**Không** tự dùng `PID`, hostname, container ordinal hoặc thời gian để mặc định rằng fixed field là duy nhất. Một worker ID có thể được cấp lại khi autoscaling, pod restart hoặc failover. Nếu writer mới được gán lại cùng fixed field và cùng key, nó **phải tiếp tục counter bền vững trước đó**, không được khởi đầu `0`.

Khi fixed field có số bit hữu hạn, cũng phải quản lý hết namespace writer và giới hạn invocation của từng writer, cộng với giới hạn tổng theo key/chuẩn. Nếu mỗi writer có key vật liệu khác nhau được dẫn xuất bằng KDF có domain separation đúng thiết kế, uniqueness trở thành bài toán theo **từng key mới**, nhưng việc cấp key/rotation lại phải được quản lý nhất quán. Đổi keyId mà không đổi byte key không phải key mới.

## 6. Restart/crash: điều quan trọng nhất là thứ tự ghi trạng thái

Hãy xem hai thứ tự:

**Không an toàn:**

```text
read counter = 42
       |
       v
encrypt(key, nonce=42) --> ciphertext đã gửi
       |
       v
update counter = 43 vào storage     <-- crash trước bước này
       |
       v
restart: đọc counter = 42 --> REUSE nonce
```

**Đúng hướng:**

```text
read counter = 42
       |
       v
atomically reserve 42, persist/COMMIT next=43
       |
       v
chỉ khi commit thành công:
encrypt(key, nonce=42) --> ciphertext
       |
      crash
       |
       v
restart: next >= 43, không cấp lại 42
```

Một số **bị cấp nhưng chưa dùng** vì crash là bình thường; **bỏ số tốt hơn tái sử dụng nonce**.

### 6.1 Phân biệt crash, timeout và retry

- **Crash trước commit**: range/sequence chưa được cấp hợp lệ, không được mã hóa trước đó.
- **Crash sau commit, trước encryption**: sẽ có gap; không hoàn trả sequence.
- **Crash sau encryption, trước lưu ciphertext**: sequence đã tiêu tốn; mã hóa lại cần sequence **khác**, dù plaintext giống nhau.
- **Timeout không rõ kết quả**: không thể “lấy lại” nonce cũ với giả định lần trước chắc chắn chưa mã hóa. Hãy cấp nonce mới, còn idempotency của nghiệp vụ xử lý độc lập.
- **Retry khi nhận được kết quả encryption trước đó**: có thể **gửi lại chính ciphertext/tag cũ** theo chính sách retransmission, khác với **thực hiện encryption mới cùng key/nonce**. Chống replay là bài toán giao thức khác, thường dùng message ID/sequence và AAD.

Nếu kho cấp nonce chưa sẵn sàng hoặc không chứng minh được trạng thái mới nhất: **fail closed**, không bắt đầu encryption dưới key hiện tại. Có thể chuyển sang **key epoch mới với key material mới** theo quy trình phục hồi được kiểm soát.

## 7. Thực hành 2: counter bền vững qua nhiều JVM với file lock

Mục đích ví dụ này là quan sát nguyên tắc:

- Duy trì `nextSequence` trong **file lưu trạng thái**.
- Dùng `FileLock` độc quyền khi nhiều process truy cập cùng **một file trên hệ thống file hỗ trợ khóa đúng cách**.
- Dùng một monitor `synchronized` bổ sung để tránh hai thread **trong cùng JVM** tranh chấp `FileLock` (theo Java API, file locks thuộc về cả JVM).
- Ghi **sequence kế tiếp** và gọi `FileChannel.force(true)` trước khi phát nonce.
- Phát hiện size/magic/checksum hỏng và từ chối thay vì tự reset.
- Dùng `CREATE_NEW` khi **khởi tạo một epoch/key mới duy nhất**, tránh vô ý ghi đè file đang có.

**Quan trọng:** đây là mô hình giáo dục trên local filesystem, **không phải production allocator có bảo đảm atomic crash recovery trên mọi FS**. `FileChannel.force(true)` không thay thế database transaction/WAL, CRC32 không bảo vệ trước sửa đổi ác ý, update in-place có rủi ro torn write, việc tạo directory entry có semantics riêng, và `FileLock` phụ thuộc filesystem/OS. Backup rollback hoặc xóa file rồi `init` lại với **cùng key** vẫn có thể gây reuse. Ở production, cần transaction log/durable sequence authority/fencing được đánh giá rõ và trạng thái phải gắn với **đúng key epoch**.

Trong demo, fixed field `0x10203040` chỉ là một giá trị để thấy byte cấu trúc; **không hề quản lý hay lưu AES key**. Nếu áp dụng vào một khóa thực, phải ràng buộc bất biến fixed field, key ID và durable state với **cùng key material**, đồng thời kiểm soát mọi writer.

Tạo file **`DurableGcmSequenceDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.CRC32;

public class DurableGcmSequenceDemo {
    private static final Object IN_JVM_GUARD = new Object();
    private static final byte[] MAGIC =
            "GCMSEQ01".getBytes(StandardCharsets.US_ASCII);
    private static final int RECORD_SIZE = 8 + 8 + 4;
    private static final long KEY_BUDGET = 1_000_000L;
    private static final int FIXED_FIELD = 0x10203040; // Demo: one fresh key/epoch.

    private static int checksum(long sequence) {
        CRC32 crc = new CRC32();
        crc.update(ByteBuffer.allocate(8).putLong(sequence).array());
        return (int) crc.getValue();
    }

    private static byte[] record(long next) {
        return ByteBuffer.allocate(RECORD_SIZE)
                .put(MAGIC).putLong(next).putInt(checksum(next))
                .array();
    }

    private static long readNext(FileChannel channel) throws IOException {
        if (channel.size() != RECORD_SIZE) {
            throw new IOException("Invalid state size: fail closed");
        }
        ByteBuffer buf = ByteBuffer.allocate(RECORD_SIZE);
        while (buf.hasRemaining()) {
            int n = channel.read(buf, buf.position());
            if (n <= 0) throw new IOException("Truncated state");
        }
        buf.flip();
        byte[] magic = new byte[MAGIC.length];
        buf.get(magic);
        long next = buf.getLong();
        int expected = buf.getInt();
        if (!Arrays.equals(magic, MAGIC) ||
                expected != checksum(next) || next < 0) {
            throw new IOException("Corrupted state: fail closed");
        }
        return next;
    }

    private static void persist(FileChannel channel, long next)
            throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(record(next));
        while (buf.hasRemaining()) {
            int n = channel.write(buf, buf.position());
            if (n <= 0) throw new IOException("State write failed");
        }
        channel.force(true);
    }

    private static void initializeNewKeyState(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            persist(channel, 0);
        }
    }

    private static long reserve(Path path) throws IOException {
        synchronized (IN_JVM_GUARD) {
            try (FileChannel channel = FileChannel.open(
                    path, StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock lock = channel.lock()) {
                long sequence = readNext(channel);
                if (sequence >= KEY_BUDGET) {
                    throw new IOException("Rotate key: budget exhausted");
                }
                // Reserve and force the NEXT sequence before returning this one.
                persist(channel, sequence + 1);
                return sequence;
            }
        }
    }

    private static byte[] iv(long sequence) {
        return ByteBuffer.allocate(12)
                .putInt(FIXED_FIELD).putLong(sequence).array();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: init|next <state-file>");
        }
        Path path = Path.of(args[1]);
        switch (args[0]) {
            case "init" -> {
                initializeNewKeyState(path);
                System.out.println("initialized: true");
            }
            case "next" -> {
                long sequence = reserve(path);
                System.out.println("iv hex: " + HexFormat.of().formatHex(iv(sequence)));
            }
            default -> throw new IllegalArgumentException("Use init or next");
        }
    }
}
```

Biên dịch và chạy ở **thư mục thử nghiệm**, dùng một file state chưa tồn tại (ví dụ tên `demo-state.bin`):

```bash
javac --release 17 DurableGcmSequenceDemo.java
java DurableGcmSequenceDemo init demo-state.bin
java DurableGcmSequenceDemo next demo-state.bin
java DurableGcmSequenceDemo next demo-state.bin
```

Đầu ra đã kiểm tra bằng các lần gọi `java` độc lập:

```text
initialized: true
iv hex: 102030400000000000000000
iv hex: 102030400000000000000001
```

Đây là hai IV **12 byte**, bắt đầu bằng cùng fixed field `10203040` rồi sequence `0` và `1` big-endian. Khác với chương trình `AtomicLong` chỉ giữ trong RAM, hai lần `next` ở **hai JVM khởi chạy độc lập** đọc cùng file state nên chúng nhận hai sequence liên tiếp.

**Vì sao mã có hai loại lock?** `synchronized (IN_JVM_GUARD)` điều phối các thread cùng JVM; `FileChannel.lock()` điều phối các process trên một hệ thống file thực thi file lock tương thích. Java `FileLock` được giữ “on behalf of entire JVM”, không phải mutex thông thường giữa nhiều thread trong một process. Không dựa vào file lock để thay mọi cơ chế đồng bộ trong một JVM.

**Điểm không được bỏ qua:** chương trình có lệnh `init` nhằm trình diễn tạo state cho **một key epoch mới**, không phải “sửa lỗi” khi mất state. Nếu state file hỏng, mất hoặc quay ngược trong backup, **không khởi tạo lại counter từ `0` dưới key cũ**. Hãy dừng encrypt, điều tra, và nếu không thể phục hồi uniqueness thì rotate sang **key material mới**. Việc restore một bản state cũ nhưng checksum vẫn đúng **không bị CRC32 phát hiện**; cần generation/fencing/nguồn sự thật bền vững ở hệ thống ngoài.

### 7.1 Vì sao ví dụ file chưa thể dùng làm allocator sản phẩm?

`FileChannel.force(true)` cố yêu cầu đồng bộ nội dung/metadata xuống thiết bị; nó **không cung cấp atomic transaction bao trùm key state, ciphertext và mọi replica**. File có thể bị torn write, host crash, mất điện, thiết bị không tuân thủ flush, snapshot quay lui hoặc network filesystem không thực thi đúng khóa. Checksum CRC32 nhận biết một số loại hỏng ngẫu nhiên, không phải MAC bảo mật và không chống được rollback nguyên bản hợp lệ.

Nếu một lần `persist` báo lỗi, trong ứng dụng thật phải **fail closed** và đối chiếu durable state trước khi cấp số tiếp; không cố dùng biến `sequence` còn lưu trong RAM. Nếu không thể chứng minh trạng thái **không lùi**, chuyển key epoch mới. Với hệ phân tán và dữ liệu giá trị cao, hãy dùng database/consensus hoặc cơ quan cấp range có **transaction/fencing** thay vì tự chế counter file chia sẻ qua NFS.

## 8. Retry, backup và failover: những trường hợp rất dễ bỏ sót

| Sự kiện | Rủi ro | Chính sách an toàn |
| --- | --- | --- |
| Two threads đồng thời | Cùng thấy sequence | Một allocator atomic trong mỗi JVM |
| Two JVM cùng key | Mỗi process có counter 0 | Shared durable allocator hoặc prefix namespace được cấp phát |
| Container restart | RAM reset, key cũ vẫn dùng | Đọc durable high-water, không khởi động lại 0 |
| Job retry | Mã hóa lại cùng nonce cũ | Nonce mới cho **mỗi encryption mới**; retransmit ciphertext cũ là hoạt động khác |
| Counter/lease reserve trước rồi crash | Bỏ dở một số sequence | **Đốt bỏ range**, không tái sử dụng |
| DB replica failover | Replica thiếu commit nonce đã dùng | Fencing/consistency bảo đảm không mất committed high-water |
| Restore backup | Counter bị lùi | Dừng encryption dưới key cũ, xác minh trạng thái hoặc dùng **key mới** |
| Rollback phần mềm | Version cũ reset schema/key state | Migration có kiểm soát, bảo vệ bất biến counter |
| Blue-green/canary deploy | Hai version dùng cùng key độc lập | Cùng allocator hoặc writer namespaces không trùng |
| Nhiều region active-active | Cùng sequence/fixed field | Phân vùng prefix/key riêng hoặc single durable authority |
| Đổi keyId nhưng không đổi key bytes | Tưởng có namespace mới | **Không**: nonce uniqueness đi theo key material, không phải nhãn |
| Hết budget/counter wrap | Dùng lại nonce | Chặn mã hóa trước hạn, rotate key và version |

### 8.1 Dữ liệu đã mã hóa dưới key cũ thì sao?

Khi rotate key, bản ghi cũ vẫn cần được giải mã bằng **đúng key version cũ và nonce đã lưu** theo chính sách retention. Key rotation không yêu cầu reencrypt ngay mọi bản ghi, nhưng phải tránh lẫn lộn namespace:

```text
encrypted record:
[formatVersion] [keyId] [nonce] [ciphertext || tag]

current encryption key: key-v2
old record key:       key-v1, read-only decrypt
```

Khi key-v1 đã ngừng được phép **encrypt**, không có phép cấp nonce mới dưới key-v1. Có thể giữ quyền decrypt trong thời hạn được phép. Nếu bản ghi có metadata cần ràng buộc (tenant, keyId, record type, version), đưa canonical byte representation vào **AAD** để chống tráo ngữ cảnh; AAD khác không cho phép reuse nonce.

### 8.2 Fencing token và split brain

Nếu hai node cùng tự cho mình là leader của allocator, một distributed lock quá hạn hoặc một replica lag có thể làm **hai phía cấp cùng range**. Một **fencing token/epoch tăng đơn điệu** được kiểm tra bởi tài nguyên nhận ghi giúp chặn writer cũ tiếp tục cập nhật sau khi mất quyền. Chỉ có TTL lock mà không có kiểm tra epoch tại nơi cấp range có thể không đủ.

Nếu lưu counter trong database phân tán, cần biết rõ:
- transaction được commit ở đâu và durability cấp nào;
- failover có thể mất committed writes hay không;
- leader cũ bị ngăn phát range sau khi lease mất hiệu lực thế nào;
- state backup có đi kèm key epoch và high-water đủ mới hay không.

Cùng một key ở hai region chỉ an toàn khi phân phối nonce namespace **toàn cục**. Giải pháp khác là cấp **key material riêng** cho mỗi region/worker qua key management có phân tách miền (domain separation), vẫn cần quản lý uniqueness dưới mỗi key.

## 9. Công thức kiểm soát giới hạn và giám sát

Một hệ thống nên duy trì ít nhất các trường metadata:

| Thuộc tính | Mục đích |
| --- | --- |
| `keyId`, `keyEpoch`, algorithm/mode | Biết namespace của chính **key material** nào đang cấp nonce |
| `fixedField` / worker namespace | Bảo đảm vùng nonce không trùng |
| `nextSequence` / `reservedUntil` | High-water đã commit trước khi encrypt |
| `invocationsUsed` / `keyBudget` | Giới hạn tổng số lần encryption/key và chính sách rotate |
| `stateVersion` / allocator epoch/fencing token | Phân biệt những lần chuyển giao trách nhiệm |
| `lastDurableCommit` hoặc transaction evidence | Hỗ trợ điều tra khi crash/restart/failover |
| `algorithmParams` (IV length, tag length) | Giải mã đúng theo cùng format |
| `createdAt`/rotation state | Quyết định active-encrypt / decrypt-only / retired |

Bộ đếm chỉ để **giám sát** có thể cache bất đồng bộ, nhưng bộ đếm **dùng để đảm bảo uniqueness** không được dựa vào metric hệ thống cuối cùng nhất quán (eventual consistency) chưa commit.

### 9.1 Không có một “ngưỡng GCM cho mọi thiết kế”

NIST SP 800-38D `8 quy định:

- Yêu cầu **uniqueness của IV dưới cùng key** là thiết yếu cho an toàn GCM.
- Với **RBG-based IV** (random construction) và deterministic IV **không dài 96 bit**, giới hạn chung trong `8.3 là **tối đa 2^32 authenticated-encryption invocations/key** trên mọi instance.
- Với **chỉ deterministic IV 96 bit**, ngoại lệ khỏi giới hạn chung đó **không** có nghĩa không giới hạn: còn phải thỏa cấu trúc field, không wrap invocation field, giới hạn số devices/contexts, lượng dữ liệu/key và chính sách an toàn khác.

Ví dụ `KEY_BUDGET = 1_000_000` trong code là **quota giả định của bài học**, không phải định mức chuẩn bắt buộc hay mức tải sản phẩm nên đặt. Cấu hình thật phải được dẫn từ threat model, chuẩn của mode, phạm vi phân phối nonce và kế hoạch rotate.

## 10. Lựa chọn thiết kế theo hoàn cảnh

| Môi trường | Thiết kế khởi đầu có thể cân nhắc | Ràng buộc không thể thiếu |
| --- | --- | --- |
| CLI ngắn hạn, **key mới cho mỗi lần chạy** | CSPRNG 96-bit nonce với tổng số lần gọi/key nhỏ | Không tái dùng key cũ khi restart; tách key version rõ |
| Một JVM, key chỉ sống trong process | AtomicLong + 96-bit deterministic nonce | Một allocator cho mỗi key, không tạo lại trong cùng key epoch |
| Một JVM, key giữ qua restart | Counter durable hoặc cấp range từ DB | Persist/commit **trước encrypt**, không rollback |
| Nhiều JVM trên cùng host | DB atomic sequence hoặc file lock **chỉ khi FS/durability được chứng minh** | Không dùng `synchronized` để phối hợp process |
| Microservices/many nodes | Durable range authority + lease/fencing, hoặc unique key/prefix domain per writer | Không trùng vùng và state không lùi khi failover |
| Multi-region active-active | Region-specific key material, hoặc globally allocated fixed field/ranges | DR/restore, replica promotion và split-brain phải được kiểm thử |
| GCM qua API bên thứ ba | Theo rõ hợp đồng nonce/IV và invocation limits | Đừng assume provider/tầng transport tự cấp nonce duy nhất toàn cục |

Một **thiết kế phù hợp** thường ưu tiên giữ invariant đơn giản: mỗi encryption dưới cùng key có nonce duy nhất, và ta có bằng chứng vận hành rằng invariant đó còn đúng sau lỗi hệ thống. Hệ thống phức tạp không cần tự implement crypto primitive; chỉ cần quản lý đầu vào của primitive đúng cách.

## 11. Kiểm thử có ý nghĩa trước khi production

Test một lần encrypt/decrypt thành công **không chứng minh** nonce allocator an toàn. Nên thử:

1. **Nhiều thread cùng JVM:** cấp hàng nghìn nonce đồng thời; không có trùng; mọi `Future` lỗi phải làm test fail.
2. **Nhiều process/JVM:** chạy song song trên cùng key namespace/allocator. Không có cặp (key,nonce) trùng trong mọi output.
3. **Restart giữ key cũ:** cấp một số nonce, dừng, khởi động lại; số mới phải lớn hơn high-water đã dùng, không reset.
4. **Crash injection ở từng ranh giới:** trước reserve, sau transaction commit, trước encrypt, sau encrypt và trước ghi record. Gap được phép, reuse không được phép.
5. **Mất quyền leader / fencing:** node cũ không được cấp dải mới sau khi mất lease.
6. **Replica failover:** chỉ chấp nhận failover không làm lùi durable high-water cho key cũ; nếu không có bằng chứng, phải rotate key.
7. **Backup rollback:** giả lập restore bản state cũ. Bộ giám sát/thiết kế phải phát hiện hoặc chặn encryption; checksum file đơn giản **không đủ**.
8. **Counter exhaustion:** tại biên quota, hành động là throw/fail closed và rotate, tuyệt đối không wrap về `0`.
9. **Sai key ID/nonce format:** decrypt từ record có metadata bị tráo phải thất bại qua xác thực AAD hoặc policy.
10. **Giám sát:** cảnh báo tỷ lệ used/budget, lỗi cấp range/commit, tăng nhanh số encryption/key, thiếu khóa mới và hệ thống allocator không khả dụng.

Kiểm thử uniqueness theo một đợt hữu hạn là bằng chứng tích cực nhưng **không thay cho chứng minh thiết kế và kiểm thử recovery**. Một counter in-memory có thể qua mọi bài test multithread mà vẫn reuse ngay sau restart.

## 12. Lỗi thường gặp

- **“`SecureRandom.nextBytes(12)` luôn khác nhau.”** Sai: nonce ngẫu nhiên là uniqueness xác suất; cần giới hạn invocation và chính sách RNG/key.
- **“`AtomicLong` an toàn nên microservices tự dùng AtomicLong được.”** Sai: chỉ atomic giữa các thread chia sẻ cùng đối tượng trong một JVM.
- **“Mỗi container có PID khác, lấy PID làm prefix là đủ.”** Sai: PID/ordinal có thể tái sử dụng, bị trùng giữa host và không ràng buộc key epoch.
- **“Restart thì counter lại 0 nhưng dùng cùng key vẫn ổn.”** Sai: nguy cơ trùng cặp key/nonce đã dùng.
- **“Encrypt trước, lưu counter sau để tăng throughput.”** Sai: crash tạo cửa sổ reuse.
- **“Range đã reserve nhưng chưa dùng thì trả về pool.”** Sai: crash có thể khiến không biết đã dùng đến đâu; burn unused range.
- **“Có file lock thì dùng được trên mọi NFS.”** Sai: hành vi và độ bền phụ thuộc filesystem/OS; `FileLock` không phải distributed transaction.
- **“`force(true)` bảo đảm snapshot không thể rollback.”** Sai: flush không chống restore/replica lag/failover.
- **“GCM tag 128 bit nên nonce reuse không nghiêm trọng.”** Sai: reuse có thể làm suy yếu cả bí mật lẫn xác thực.
- **“Nonce là bí mật nên không nên lưu.”** Thường sai: nonce được lưu/truyền công khai với ciphertext để decrypt; điều cần bảo vệ là **uniqueness/key**.
- **“AAD khác nhau tạo nonce namespace khác.”** Sai: uniqueness vẫn đi theo key và IV.
- **“Đổi tên keyId là coi như có key mới.”** Sai: phải đổi **key material** thực sự.
- **“GCM và CBC dùng chung công thức IV tăng dần.”** Không đúng chung: CBC cần IV khó dự đoán theo đặc tính riêng.
- **“Nếu không biết counter có lùi hay không, cứ thử encrypt rồi đo.”** Không có phép test sau sự cố nào khôi phục tính an toàn của nonce đã tái sử dụng; khi nghi ngờ, **dừng mã hóa bằng key cũ**.

## 13. Câu hỏi tự kiểm tra

**1.** Hai thread dùng `long seq++` thông thường có thể cấp cùng nonce không?  
**Có.** Phép đọc-tăng-ghi không tự atomic; cần AtomicLong hoặc vùng khóa phù hợp.

**2.** Hai JVM trên cùng host dùng `AtomicLong` riêng có đủ an toàn nếu cùng key và fixed field không?  
**Không.** Mỗi JVM đều có thể cấp sequence 0,1,...; cần shared durable authority hoặc namespace thực sự không trùng.

**3.** Vì sao cần commit counter **trước** encrypt?  
Vì nếu crash sau encrypt nhưng trước persist, restart có thể cấp lại nonce đã dùng.

**4.** Một range [100,200) được commit cho process, crash khi mới dùng đến 120. Có được cấp lại [121,200) không?  
**Không.** Range đã reserve được coi là tiêu tốn, bỏ gap an toàn hơn reuse.

**5.** Có nên khởi tạo lại file counter từ 0 khi file cũ bị mất mà vẫn giữ AES key cũ?  
**Không.** Fail closed, đối chiếu durable authority; nếu không chứng minh được state chính xác thì rotate sang key mới.

**6.** Một nonce AES-GCM khác AAD nhưng giữ nguyên key/IV được dùng lại cho thông điệp khác có an toàn không?  
**Không.** AAD khác không cứu được lỗi trùng cặp key/IV.

**7.** Với deterministic nonce 32-bit fixed + 64-bit counter, điểm then chốt để tránh trùng trên nhiều process là gì?  
Duy trì **counter chung được cấp phát atomic và durable**, hoặc cố định field riêng không trùng theo writer/key và counter riêng bền vững trong mỗi namespace.

**8.** Dùng counter tăng cho IV CBC có tự bảo đảm an toàn không?  
**Không.** CBC còn yêu cầu IV khó dự đoán theo chính sách; quy tắc của GCM deterministic nonce không thể sao chép máy móc.

**9.** Chữ “96-bit random nonce” có nghĩa xác suất trùng bằng 0 không?  
**Không.** Xác suất nhỏ nhưng không bằng 0; NIST đặt giới hạn theo construction và mỗi key.

**10.** Việc rotate từ key-v1 sang key-v2 có cho phép xóa ngay key-v1 không?  
Tùy chính sách retention: các ciphertext cũ vẫn cần key-v1 để decrypt cho đến khi được reencrypt hoặc hết thời hạn. Tuy nhiên key-v1 nên chuyển sang **decrypt-only**, không mã hóa mới.

## Tóm tắt

Quy tắc cốt lõi đối với các mode yêu cầu nonce duy nhất:

```text
                    key material K
                          |
                 một namespace nonce
                          |
          +---------------+----------------+
          |                                |
   random IV construction           deterministic IV
   (collision probability)          fixed || counter
          |                                |
   theo dõi invocation/key          atomic allocation
   và giới hạn chuẩn                + durable commit
          |                                |
          +---------------+----------------+
                          |
                          v
              encrypt với nonce chưa dùng
                          |
                          v
           lưu [keyId,nonce,ciphertext,tag]
                          |
                          v
      restart / process / host / failover vẫn
      KHÔNG thể cấp lại nonce dưới cùng key
```

**Quản lý IV/nonce là một phần của vòng đời khóa, không phải chi tiết ngẫu nhiên bên trong một phương thức.** Cần đồng bộ trong JVM, đồng thuận/durability qua process, key-version và policy khôi phục qua restart. Nếu invariant không thể xác nhận, dừng encryption bằng key cũ và khôi phục theo hướng **key mới**.

## Nguồn tham khảo chính thức

- [NIST SP 800-38D — Galois/Counter Mode (GCM) and GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final): `8.1–8.3` về uniqueness IV/key, deterministic vs RBG construction và giới hạn invocation.
- [NIST SP 800-38A — Block Cipher Modes of Operation](https://csrc.nist.gov/pubs/sp/800/38/a/final): yêu cầu IV của CBC và uniqueness của counter blocks trong CTR (Appendix B).
- [Java 17 — Cipher API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): `AES/GCM/NoPadding`, `updateAAD`, việc thay đổi IV cho mỗi encryption.
- [Java 17 — GCMParameterSpec API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): tLen của tag (bit) và IV bytes.
- [Java 17 — AtomicLong API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html): cấp số nguyên tử trong một JVM.
- [Java 17 — FileLock API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileLock.html): file locks thuộc JVM, giới hạn giữa thread và filesystem/OS.
- [Java 17 — FileChannel API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): `lock()`, ghi theo vị trí và `force(true)`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-lifecycle-007">11.7 Khóa có thể xuất so với khóa chỉ được sử dụng thông qua provider</a>

<details>
<summary>Xem chi tiết</summary>

# 11.7 Khóa có thể xuất so với khóa chỉ được sử dụng thông qua provider

## 1. Tại sao hai đối tượng `Key` giống giao diện nhưng có mức kiểm soát khác nhau?

Trong Java Cryptography Architecture/Extension (JCA/JCE), các giao diện `java.security.Key`, `SecretKey`, `PublicKey`, `PrivateKey` chỉ mô tả một khóa và những thao tác có thể thực hiện thông qua provider. Chúng **không** bảo đảm material nằm trong JVM, file, hệ điều hành, token/HSM hay một dịch vụ KMS.

Hai khái niệm cần phân biệt:

- **Khóa có thể xuất (exportable):** có một biểu diễn byte của secret/private key được phép lấy ra, thường qua `key.getEncoded()`; Java process có thể giữ, sao chép, lưu hoặc nhập lại bằng API thích hợp. Mỗi bản sao mở rộng bề mặt rủi ro: heap, core dump, debug, log, file, backup, network, dịch vụ downstream.
- **Khóa chỉ được sử dụng qua provider (provider-bound/opaque):** Java giữ **handle** tham chiếu một key trong token/HSM/keystore đặc thù hoặc adapter. Backend sở hữu key material, có thể cấm việc lấy raw bytes nhưng vẫn cho phép gọi các phép `sign`, `decrypt`, `encrypt`, `MAC`, `derive` hoặc `wrap` theo policy. Mục đích là **sử dụng key mà không cần trao key material cho ứng dụng**.

```text
Ứng dụng Java
   |
   +---- exportable SecretKey/PrivateKey
   |          |
   |      getEncoded() -> byte[]
   |          |
   |      có thể import/copy/lưu nếu được phép
   |          |
   |      Cipher/Signature trên provider tương thích
   |
   +---- opaque provider key handle
              |
          getEncoded() -> thường null
              |
          Cipher/Signature/KeyAgreement
              |
          provider -> token/HSM/KMS adapter
                         |
                    operation đã cho phép
                         |
                    trả kết quả, không trả raw key
```

**Quan trọng:** `getEncoded()==null` chỉ chứng minh API Java không cung cấp encoding qua lời gọi đó. Nó **không đủ chứng minh khóa là non-extractable ở phần cứng** hoặc bất khả trích xuất bằng mọi API khác; cũng **không làm một key giả lập trở nên dùng được với provider**. Bảo đảm thực tế phụ thuộc key attributes, token policy, IAM, cơ chế import/generation, firmware và kiểm tra an ninh.

## 2. Hợp đồng của Key.getAlgorithm, getFormat và getEncoded

| API | Có nghĩa gì? | Không có nghĩa gì? |
| --- | --- | --- |
| `getAlgorithm()` | Tên thuật toán như `AES`, `RSA`, `EC` | Không nói key được tạo ở đâu, có đủ quyền sử dụng hay có thể export |
| `getFormat()` | Format encoding nếu có: `RAW`, `X.509`, `PKCS#8`, hoặc `null` | Không phải cơ chế bảo vệ dữ liệu; `null` không xác minh HSM |
| `getEncoded()` | Byte encoding nếu provider hỗ trợ, hoặc `null` | Không chứng minh danh tính chủ key; không cấp quyền bọc/xuất hay đảm bảo mọi bản sao được xóa |

Trong Java 17+ với phần mềm provider thông dụng:

- `KeyGenerator.getInstance("AES")` thường sinh `SecretKey` có `getFormat() == "RAW"`; `getEncoded()` trả 16, 24 hoặc 32 byte tùy AES-128/192/256.
- Khóa công khai RSA từ `KeyPairGenerator` thường có `getFormat()=="X.509"` và bytes DER `SubjectPublicKeyInfo`. Có thể dựng lại qua `X509EncodedKeySpec` và `KeyFactory("RSA")`.
- Khóa riêng RSA phần mềm thường có `getFormat()=="PKCS#8"` và DER `PrivateKeyInfo`. Có thể dựng lại qua `PKCS8EncodedKeySpec` và `KeyFactory("RSA")` nếu được phép.
- Khóa phần cứng có thể cho phép xuất **public key** nhưng cấm xuất **private key** cùng cặp. Public key cần xác thực nguồn/định danh trước khi dùng trong giao thức; bản thân nó không phải bí mật.

### Key export khác Key usage

Có thể key exportable nhưng bị giới hạn bởi policy để chỉ ký; ngược lại opaque key có thể ký và giải mã trong backend mà không trả raw material. Những quyền `sign`, `verify`, `encrypt`, `decrypt`, `derive`, `wrap` và `unwrap` là các quyền/chức năng **tách biệt**. Trong token có thể có key chỉ được phép `CKA_SIGN` mà không có `CKA_DECRYPT`; ứng dụng không được suy đoán từ tên thuật toán rằng mọi operation đều hợp lệ.

`getEncoded()` có thể trả `null` đối với key không hỗ trợ encoding. Không xử lý `null` bằng `new byte[0]`, không tự hash key ID thành raw key và không cố lấy private key từ serialized `toString()` hoặc các trường phản chiếu. Một handle khác raw key material.

## 3. Khi được phép xuất key, phải hiểu đúng định dạng

| Loại | `getFormat()` thường gặp | Bytes trả về | API dựng lại |
| --- | --- | --- | --- |
| AES/HMAC secret key phần mềm | `RAW` | Secret bytes có entropy thích hợp | `SecretKeySpec(raw,"AES")` hay tên algorithm tương ứng |
| RSA public | `X.509` | DER SubjectPublicKeyInfo | `KeyFactory("RSA").generatePublic(new X509EncodedKeySpec(der))` |
| RSA private | `PKCS#8` | DER PrivateKeyInfo | `KeyFactory("RSA").generatePrivate(new PKCS8EncodedKeySpec(der))` |
| EC public/private phần mềm | `X.509` / `PKCS#8` | DER có thuật toán/đường cong | `KeyFactory("EC")` với spec đúng loại |
| Khóa HSM không cho xuất | có thể `null` | `getEncoded()==null` | Dùng key handle qua provider, không nhập lại từ byte[] |

Độ dài đúng của AES chỉ là điều kiện cú pháp: 16/24/32 byte **không bảo đảm entropy**. `SecretKeySpec` không tạo randomness, không tự kiểm tra nguồn tin cậy và **không tạo key nằm trong HSM** từ một mảng thông thường. Dùng `KeyGenerator` và `SecureRandom` cho khóa mới, hoặc raw bytes đã được cung cấp an toàn.

Không nhầm **PEM** (Base64 text armor) với **DER** (mã hóa ASN.1 nhị phân): `PKCS8EncodedKeySpec` nhận DER, không nhận bytes ASCII của `-----BEGIN PRIVATE KEY-----`. Và `BEGIN PRIVATE KEY` là PKCS#8 **không mã hóa**; `BEGIN ENCRYPTED PRIVATE KEY` là container bảo vệ mật khẩu/thuật toán riêng, cần xử lý `EncryptedPrivateKeyInfo` trước. Base64/hex chỉ là encoding, không làm private key bí mật hơn.

### Rủi ro của việc export

Gọi `getEncoded()` trên private/secret key là **tạo thêm một bản sao dữ liệu nhạy cảm**. Tránh thực hiện chỉ để làm fingerprint, debug hay chuyển object giữa các module. Nếu phải export để backup hoặc migration: giới hạn người được phép, thiết lập kênh mã hóa, nguyên tắc least privilege, audit, key wrapping và xóa bản sao theo chính sách.

`Arrays.fill(bytes,(byte)0)` giúp giảm lifetime của mảng ứng dụng, nhưng không xóa bản sao key bên trong `SecretKeySpec`, provider, `Cipher`, JVM hoặc bản dump đã tạo. Chuyển private key thành `String` càng khó xóa vì immutable. Việc `getEncoded()==null` cũng không bảo đảm tất cả nội bộ JVM không chứa vật liệu nhạy cảm.

## 4. Chương trình Java 17+: export AES/RSA và ví dụ key opaque giả lập

Lưu thành **`ExportabilityDemo.java`**. Chương trình đầy đủ sau đây:

1. Sinh AES-256 bằng `KeyGenerator`, lấy encoding raw của software key.
2. Dựng `SecretKeySpec` từ bytes, dùng AES-GCM mã hóa bằng key gốc và giải mã bằng key nhập lại.
3. Xóa mảng bytes trả về từ `getEncoded()` rồi xác nhận key gốc **vẫn còn**.
4. Định nghĩa một `SecretKey` giả không có encoding (trả `null`); provider phần mềm từ chối dùng nó. Đây **không phải HSM mock hợp lệ**, mà là bằng chứng rằng API method đơn lẻ không đủ.
5. Export/import RSA private `PKCS#8` và RSA public `X.509`, ký một thông điệp và kiểm chứng chữ ký.

```java
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class ExportabilityDemo {
    private static final class OpaqueLookingKey implements SecretKey {
        public String getAlgorithm() { return "AES"; }
        public String getFormat() { return null; }
        public byte[] getEncoded() { return null; }
    }

    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256, random);
        SecretKey aes = kg.generateKey();

        byte[] exported = aes.getEncoded();
        if (exported == null) {
            throw new IllegalStateException("This demo requires exportable AES");
        }
        try {
            System.out.println("aesFormat=" + aes.getFormat());
            System.out.println("aesRawBytes=" + exported.length);
            SecretKey imported = new SecretKeySpec(exported, "AES");

            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            byte[] message = "key lifecycle".getBytes(StandardCharsets.UTF_8);
            Cipher encryption = Cipher.getInstance("AES/GCM/NoPadding");
            encryption.init(Cipher.ENCRYPT_MODE, aes,
                    new GCMParameterSpec(128, nonce));
            byte[] ciphertext = encryption.doFinal(message);
            Cipher decryption = Cipher.getInstance("AES/GCM/NoPadding");
            decryption.init(Cipher.DECRYPT_MODE, imported,
                    new GCMParameterSpec(128, nonce));
            System.out.println("aesRoundTrip=" +
                    Arrays.equals(message, decryption.doFinal(ciphertext)));

            Arrays.fill(exported, (byte) 0);
            byte[] checkCopy = aes.getEncoded();
            try {
                System.out.println("clearingCopyDoesNotDestroyKey="
                        + !MessageDigest.isEqual(exported, checkCopy));
            } finally {
                Arrays.fill(checkCopy, (byte) 0);
            }

            OpaqueLookingKey fake = new OpaqueLookingKey();
            System.out.println("mockEncodedNull=" + (fake.getEncoded() == null));
            try {
                Cipher unsupported = Cipher.getInstance("AES/GCM/NoPadding");
                unsupported.init(Cipher.ENCRYPT_MODE, fake,
                        new GCMParameterSpec(128, nonce));
                System.out.println("mockAccepted=false");
            } catch (InvalidKeyException expected) {
                System.out.println("mockRejectedByProvider=true");
            }
        } finally {
            Arrays.fill(exported, (byte) 0);
        }

        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048, random);
        KeyPair pair = rsa.generateKeyPair();
        byte[] publicDer = pair.getPublic().getEncoded();
        byte[] privateDer = pair.getPrivate().getEncoded();
        if (publicDer == null || privateDer == null) {
            throw new IllegalStateException("RSA example needs exportable keys");
        }
        try {
            System.out.println("rsaPublicFormat=" + pair.getPublic().getFormat());
            System.out.println("rsaPrivateFormat=" + pair.getPrivate().getFormat());
            KeyFactory factory = KeyFactory.getInstance("RSA");
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(factory.generatePrivate(
                    new PKCS8EncodedKeySpec(privateDer)));
            byte[] message = "check".getBytes(StandardCharsets.UTF_8);
            signer.update(message);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(factory.generatePublic(
                    new X509EncodedKeySpec(publicDer)));
            verifier.update(message);
            System.out.println("importedRsaSignatureValid="
                    + verifier.verify(signature));
        } finally {
            Arrays.fill(publicDer, (byte) 0);
            Arrays.fill(privateDer, (byte) 0);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 ExportabilityDemo.java
java ExportabilityDemo
```

Đầu ra trên JDK/SunJCE + RSA provider đã kiểm thử:

```text
aesFormat=RAW
aesRawBytes=32
aesRoundTrip=true
clearingCopyDoesNotDestroyKey=true
mockEncodedNull=true
mockRejectedByProvider=true
rsaPublicFormat=X.509
rsaPrivateFormat=PKCS#8
importedRsaSignatureValid=true
```

Giải thích: `getEncoded()` cung cấp bytes mà ứng dụng có thể sao chép; việc làm sạch bản sao không hủy đối tượng `aes` đã sinh. `OpaqueLookingKey` cố ý **không có provider nào quản lý**: `mockEncodedNull=true` không đồng nghĩa key bảo mật, còn `mockRejectedByProvider=true` là kết quả cụ thể của provider AES trong phép thử.

**GCM nonce:** `nonce` 12 byte chỉ được dùng **một lần để mã hóa**, rồi dùng lại **đúng cho phép giải mã ciphertext đó**. Với lần **mã hóa mới** dưới cùng AES key, phải chọn nonce **khác**; cặp key+nonce không được lặp.

**Ký RSA:** `KeyFactory` được chọn theo `RSA`. Với public key dùng `X509EncodedKeySpec`, private key dùng `PKCS8EncodedKeySpec`. Chữ ký được xác thực nghĩa là import hoạt động đúng, nhưng không chứng minh việc export private key là chính sách an toàn cho sản phẩm.

## 5. Java KeyStore có tự làm khóa non-exportable không?

**Không.** `java.security.KeyStore` là abstraction lưu và tìm `PrivateKeyEntry`, `SecretKeyEntry`, `TrustedCertificateEntry`. Khả năng raw export tùy **store type/provider, key attributes và nơi key material thực sự cư trú**.

| Loại | Đặc điểm | Có thể export key? |
| --- | --- | --- |
| `PKCS12` software | Container/file bảo vệ bằng password; entry được lấy khi mở store | Có thể export; demo dưới đây chứng minh |
| `JKS` truyền thống | Software keystore chủ yếu với private keys/certificates | Phụ thuộc entry/implementation; không phải HSM |
| `PKCS11` trên token/HSM | Provider gọi native PKCS#11, handle có thuộc tính security | Có thể không export theo policy hardware |
| Android Keystore | Key handle qua OS/backend hardware khi khả dụng | Nhiều private/secret keys không cho export; phụ thuộc API/device |
| Cloud KMS | Key ID, IAM, audit, API/RPC tới dịch vụ | Nhiều key không xuất raw; SDK có thể không cung cấp JCA `Key` |

Password-protected PKCS12 bảo vệ **bản lưu ở trạng thái nghỉ**. Nhưng sau khi unlock, software key có thể vẫn cho `getEncoded()`; điều này **khác** key chưa từng rời HSM.

### 5.1 Chương trình: PKCS12 có thể chứa AES SecretKeyEntry exportable

Lưu thành **`Pkcs12ExportabilityDemo.java`**. Ví dụ chạy trong memory, không tạo file key trên disk. Nó lưu một AES-256 key trong PKCS12, nạp lại bằng đúng password rồi so sánh raw bytes. Password mẫu chỉ phục vụ demo, không được hardcode trong code sản phẩm.

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class Pkcs12ExportabilityDemo {
    public static void main(String[] args) throws Exception {
        char[] storePassword = "demo-store-password".toCharArray();
        char[] entryPassword = "demo-entry-password".toCharArray();
        byte[] storedBytes = null;
        byte[] recoveredBytes = null;

        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256, new SecureRandom());
            SecretKey generated = generator.generateKey();
            storedBytes = generated.getEncoded();
            if (storedBytes == null) {
                throw new IllegalStateException("Demo requires exportable key");
            }

            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, storePassword);
            KeyStore.ProtectionParameter protection =
                    new KeyStore.PasswordProtection(entryPassword);
            store.setEntry("session-key",
                    new KeyStore.SecretKeyEntry(generated), protection);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            store.store(output, storePassword);

            KeyStore restored = KeyStore.getInstance("PKCS12");
            restored.load(new ByteArrayInputStream(output.toByteArray()),
                    storePassword);
            SecretKey restoredKey =
                    (SecretKey) restored.getKey("session-key", entryPassword);
            recoveredBytes = restoredKey.getEncoded();

            System.out.println("keystoreType=" + restored.getType());
            System.out.println("isSecretKeyEntry="
                    + restored.entryInstanceOf(
                            "session-key", KeyStore.SecretKeyEntry.class));
            System.out.println("recoveredExportable="
                    + (recoveredBytes != null));
            System.out.println("sameRawMaterial="
                    + (recoveredBytes != null
                    && MessageDigest.isEqual(storedBytes, recoveredBytes)));
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(entryPassword, '\0');
            if (storedBytes != null) Arrays.fill(storedBytes, (byte) 0);
            if (recoveredBytes != null) Arrays.fill(recoveredBytes, (byte) 0);
        }
    }
}
```

```bash
javac --release 17 -encoding UTF-8 Pkcs12ExportabilityDemo.java
java Pkcs12ExportabilityDemo
```

```text
keystoreType=PKCS12
isSecretKeyEntry=true
recoveredExportable=true
sameRawMaterial=true
```

**Diễn giải:** PKCS12 được mở và trích xuất `SecretKeyEntry` thành công; `recoveredExportable=true` chứng minh keystore **không tự tạo ràng buộc non-extractable**. Một provider khác có thể khác về hỗ trợ SecretKeyEntry/password; phải kiểm thử runtime thực tế. Mặc dù không ghi file key, `ByteArrayOutputStream` và các key objects vẫn giữ bản sao nhạy cảm trong heap, nên demo **không** chứng minh memory erasure an toàn.

## 6. Opaque key thật cần provider biết handle và key policy

Một key trên HSM có thể được đại diện bằng một đối tượng `PrivateKey` mà `getEncoded() == null`. Khi đó:

```text
Java KeyStore("PKCS11") --lookup alias--> HSM-backed PrivateKey handle
                                 |
Signature.initSign(handle) -------+-- token provider -- HSM sign()
                                            |
                                      signature bytes
```

Ở đây **private key material nằm trong HSM**, nhưng Java có thể nhận chữ ký. Muốn thay đổi thao tác cần dùng cơ chế được thiết bị hỗ trợ: một key chỉ cho `sign` không mặc nhiên cho `decrypt` hay `derive`. Nếu key nằm trên KMS cloud, hoạt động có thể được cung cấp qua REST/gRPC SDK chứ **không** qua JCA `Signature` hay `KeyStore`; không mặc định KMS nào cũng cung cấp JCA adapter.

**Provider matching:** `Signature.getInstance("SHA256withRSA",tokenProvider)` chỉ hữu ích nếu provider và key handle hỗ trợ thuật toán đó. `Cipher.getInstance("AES/GCM/NoPadding")` không bảo đảm chọn đúng provider cho một key opaque; nếu rơi vào software provider không hiểu handle, `Cipher.init` có thể ném `InvalidKeyException`. Cần chọn engine đúng nhà cung cấp, đúng transformation và đúng key usage.

### Khung tích hợp SunPKCS11 (không chạy độc lập)

```java
// CHỈ MINH HỌA: cần token/HSM, native PKCS#11 library, config,
// key alias, quyền và PIN thật. Không hardcode PIN.
java.security.Provider base =
        java.security.Security.getProvider("SunPKCS11");
if (base == null) {
    throw new IllegalStateException("SunPKCS11 unavailable");
}
java.security.Provider tokenProvider =
        base.configure("/secure/config/token-pkcs11.cfg");
java.security.KeyStore store =
        java.security.KeyStore.getInstance("PKCS11", tokenProvider);
store.load(null, pin);

java.security.Key key = store.getKey("signing-key", null);
if (!(key instanceof java.security.PrivateKey privateKey)) {
    throw new IllegalStateException("Signing key not found");
}
java.security.Signature signer =
        java.security.Signature.getInstance(
                "SHA256withRSA", tokenProvider);
signer.initSign(privateKey);
// signer.update(messageBytes); byte[] signature = signer.sign();
// Token/session cleanup và audit theo vendor policy.
```

`SunPKCS11` liên quan module `jdk.crypto.cryptoki` trong JDK có hỗ trợ. Có thể thiếu trên runtime tối giản; file cấu hình và native PKCS#11 library/slot/token phải được tạo chính xác. Token login/session và alias mapping có thể khác nhau. Đoạn này **không tạo được opaque key** nếu chưa có HSM và key generation policy thực; không dùng để giả vờ HSM hoạt động chỉ vì `getEncoded()==null`.

### Key usage vẫn có thể nguy hiểm

Non-extractable key chỉ giúp hạn chế **lấy raw material**. Nếu attacker chiếm quyền service gọi `sign`, họ có thể tạo chữ ký không được phép; nếu có quyền gọi `decrypt`, họ có thể lấy plaintext trả về. **HSM/KMS không thay thế authorization**. Hạn chế payload được xử lý, key usage, IAM, rate limits, context/tenant, audit, session lifetime và dual control theo rủi ro.

## 7. Các quyền và thuật ngữ phải phân biệt

| Thuật ngữ | Câu hỏi thực sự | Hệ quả |
| --- | --- | --- |
| Exportable qua `getEncoded` | Có thể nhận raw/DER bytes qua Java API không? | Cho phép sao chép/nhập lại khi có quyền |
| Non-extractable | Device có cấm trích xuất key bằng mọi mechanism được policy áp dụng không? | Phụ thuộc token/HSM và thuộc tính tạo key |
| Sensitive | Device có che giấu private attributes không? | Không đồng nghĩa mọi phép toán đều bị cấm |
| Sign/decrypt/derive permission | Key được thực hiện operation nào? | Từng quyền độc lập; không suy từ key type |
| Wrappable | Cho phép xuất **dưới dạng được bọc bằng KEK** không? | Có thể có policy riêng cho wrapped export |
| Migratable/backup | Có cơ chế backup giữa HSM/cluster hoặc restore không? | Không mặc định có ở key non-exportable |
| Destroyable/deletable | Có thể revoke/hủy key trong backend không? | Xóa object reference Java không bằng hủy key thật |

Trong PKCS#11, có các thuộc tính như `CKA_EXTRACTABLE`, `CKA_SENSITIVE`, `CKA_SIGN`, `CKA_DECRYPT`, `CKA_WRAP`, `CKA_UNWRAP`; **tập thuộc tính và ràng buộc** của token quyết định kết quả. Không khẳng định một flag duy nhất là đầy đủ nếu chưa xem standard/vendor policy và cơ chế wrap/trusted key.

`Cipher.wrap(key)` thuộc JCE engine wrap mode. Nó không thể **vượt** chính sách non-extractable bằng phép màu; backend sẽ từ chối nếu key không được export theo cơ chế wrap tương ứng. Ngược lại, một số key chỉ cho wrapped migration tới KEK đáng tin: đó là **export dưới lớp bảo vệ có kiểm soát**, không phải raw export tự do.

## 8. Vòng đời key trong hai mô hình

| Vòng đời | Software exportable | Provider-bound/non-extractable |
| --- | --- | --- |
| Tạo | `KeyGenerator`/`KeyPairGenerator` software hoặc raw material hợp lệ | Generate **trực tiếp trên token/HSM/KMS** với policy non-extractable từ đầu |
| Nhập | `SecretKeySpec`, `KeyFactory` theo định dạng | Import qua API backend và thuộc tính key; không chỉ tạo `KeySpec` |
| Lưu | PKCS12/wrapped bytes/secret storage, kiểm soát file/backup | Application lưu key ID/alias; backend giữ material |
| Sử dụng | Software provider; raw key có thể đến heap | Engine/provider hoặc SDK làm operations theo key handle và quyền |
| Backup/restore | Tạo bản sao/wrapped backup có bảo vệ | Cơ chế vendor HSM cluster/backup/HA, đôi khi không cho export |
| Rotation | Tạo version mới, rewrap/re-encrypt và quản lý key cũ | Backend cấp version/alias mới; key cũ vẫn cần để đọc dữ liệu được phép |
| Hủy | Xử lý tất cả raw copies, file, backup, phiên bản | Backend revoke/delete và session handling; GC không phải hủy vật liệu |

**Nếu yêu cầu “private key không bao giờ ra khỏi HSM”:** phải sinh key **trên HSM** ngay từ đầu với policy phù hợp. Không thể khẳng định ràng buộc ấy sau khi sinh key bằng Java software rồi export/import vào thiết bị: raw material đã từng tồn tại bên ngoài.

**Backup là yêu cầu kiến trúc:** key non-exportable không có kế hoạch HA/backup có thể mất vĩnh viễn khi token hỏng, khiến ciphertext cũ không giải mã được. Đừng đặt ràng buộc không-export rồi quên phương án phục hồi hợp lệ. Hỗ trợ backup có thể đòi nhiều thiết bị và vendor mechanisms, không đồng nghĩa cho phép export raw.

**Thay key:** mỗi ciphertext/signature nên có key ID/version hoặc metadata để xác định đúng key. Key mới không tự giải mã dữ liệu do key cũ mã hóa; phải giữ version cũ theo thời hạn hoặc thực hiện re-encryption/rewrapping có kiểm soát. Đừng xóa key cũ trước khi xác minh khối lượng dữ liệu đã migrate.

## 9. Thiết kế API không ép mọi key thành byte[]

Một service thường nên yêu cầu **operation**, không yêu cầu raw key:

```java
// API minh họa thiết kế, không phải chương trình độc lập.
interface SigningGateway {
    byte[] sign(String keyId, byte[] message) throws Exception;
}

interface DecryptionGateway {
    byte[] decrypt(String keyId, byte[] ciphertext,
                   byte[] associatedData) throws Exception;
}
```

Implementation có thể là phần mềm `Signature.initSign(privateKey)`, HSM provider với private key handle, hoặc cloud KMS SDK RPC. Caller không cần `privateKey.getEncoded()`. Khi cần fingerprint, hãy lấy **public key** hoặc key ID đáng tin; **không export private key chỉ để in hash/private fingerprint**.

Lưu metadata phù hợp: provider/backend identifier, algorithm, key ID, key version, allowed purposes và thông tin rotation. Tránh lưu class name của handle làm định danh bền vững; token session có thể hết hạn, provider object có thể không portable qua restart. Sau restart, lookup lại theo alias/key ID và chính sách xác thực backend.

`KeyStore` hay provider API không bắt buộc dùng chung một instance toàn hệ thống: cryptographic engines như `Cipher`, `Mac`, `Signature` có state riêng, không nên chia sẻ trạng thái giữa thread/request nếu không có hợp đồng thread-safety cụ thể. Khi provider timeout, xử lý retry/idempotency thích hợp cho từng operation và không âm thầm fallback ra software để lấy secret.

## 10. So sánh trade-off và chọn mô hình

| Tiêu chí | Exportable software key | Provider-only key |
| --- | --- | --- |
| Khả năng di chuyển giữa provider | Thường cao nếu format raw/DER chuẩn | Phụ thuộc vendor, sessions, SDK và migration policy |
| Kiểm soát trích xuất raw | Process có thể đọc/copy raw khi được phép | Backend có thể cấm raw extraction thực sự |
| Sao lưu/phục hồi | Dễ copy nhưng nhiều bản sao cần bảo vệ | Backup có thể phức tạp, phải có HA/cluster phù hợp |
| Tốc độ và độ trễ | Software local thường thuận lợi cho nhiều thao tác | Hardware/RPC có throughput/latency/quota riêng |
| Số thuật toán hỗ trợ | Theo JDK/provider phần mềm | Theo firmware/token mechanisms/service API |
| Khi process bị xâm nhập | Có thể bị đánh cắp key rồi dùng offline | Có thể lạm dụng sign/decrypt trong phạm vi quyền, khó lấy raw nếu policy đúng |
| Độ phức tạp quản trị | Keystore/secret manager, filesystem, backup, key rotation | IAM, PIN, sessions, audit, availability, key ceremony, disaster recovery |

**Không có lựa chọn tốt nhất cho mọi trường hợp.** Khóa ký của CA/gateway tài chính, master KEK hoặc root key quan trọng thường có yêu cầu kiểm soát mạnh hơn khóa phiên ngắn hạn trong một process cô lập. Chọn theo tài sản cần bảo vệ, threat model, vòng đời, latency, khả năng phục hồi và compliance.

## 11. Sai lầm phổ biến và cách xử lý

| Biểu hiện | Nguyên nhân | Biện pháp |
| --- | --- | --- |
| `NullPointerException` sau `getEncoded()` | Opaque key trả `null` hợp lệ | Kiểm tra `null`, chọn provider operation thay vì cố export |
| `InvalidKeyException` tại `Cipher.init` hoặc `Signature.initSign` | Sai key type/provider, policy không cho operation | Dùng đúng provider/mechanism và key usage |
| `UnrecoverableKeyException`, `KeyStoreException` | Sai alias, entry password/PIN, store type hay policy | Kiểm tra type, credentials và session |
| `NoSuchAlgorithmException` / `NoSuchProviderException` | Algorithm/provider không được cấu hình/hỗ trợ | Kiểm tra runtime và capabilities |
| `getFormat()==null` | Provider không cung cấp encoding portable | Không ép `KeyFactory` import từ dữ liệu không tồn tại |
| Coi `PKCS12` là HSM | Nhầm password-protected file với token | Kiểm tra key location và backend policy |
| Tự implement `SecretKey` trả `null` rồi mong sử dụng | Không có provider sở hữu handle | Cần API/provider thực sự |
| Bọc khóa bằng `Cipher.wrap` nhưng bị từ chối | Key có restriction non-extractable/wrapping | Theo policy; không bypass bằng export |
| Key mới không giải mã được ciphertext cũ | Không quản lý key ID/version hoặc xóa key cũ | Versioning/re-wrapping/re-encrypt có kế hoạch |
| HSM private key bị lạm dụng dù không export | Service được phép ký/giải mã đầu vào attacker chọn | Authorization theo nghiệp vụ, audit và rate limits |
| Xóa byte[] rồi nghĩ đã xóa key | Key object/provider vẫn giữ bản sao | Hạn chế export và quản lý lifecycle đúng backend |
| Token/session restart làm handle không hợp lệ | Handle gắn session/backend | Lookup lại theo alias/key ID và tái xác thực |
| KMS chỉ có RPC nhưng code ép vào `KeyStore` | Không có JCA bridge phù hợp | Dùng SDK/adapter chính thức |
| Downgrade tự động sang software khi HSM lỗi | Fallback phá vỡ policy non-extractable | Fail closed, báo lỗi và theo escalation process |

### Checklist triển khai

- [ ] Chọn exportable hay non-exportable **trước khi sinh key**, dựa trên threat model và backup requirement.
- [ ] Biết chính xác `KeyStore` type/provider/backend, không suy đoán từ tên API.
- [ ] `getEncoded()==null` được xử lý có chủ đích; không tự tạo key rỗng hay xuất raw bằng đường vòng.
- [ ] Không log/serialize raw private/secret key; hạn chế sao chép và xử lý memory.
- [ ] Chọn provider tương thích với key handle và operation; kiểm tra usage policy.
- [ ] Phân quyền ở mức key ID, tenant, thao tác và nội dung nghiệp vụ; có audit và rate limit.
- [ ] Biết wrapped export khác raw export; không dùng `wrap` để vượt policy token.
- [ ] Có key version, vòng đời, rotation, rollback, HA/backup và disaster-recovery test.
- [ ] Đã thử key disabled, HSM unavailable, PIN sai, alias không tồn tại và provider mismatch.
- [ ] Hủy/thu hồi theo backend, không đồng nhất xóa biến Java với hủy key trong thiết bị.

## 12. Câu hỏi tự kiểm tra

1. **`getEncoded()==null` có chứng minh key nằm trong phần cứng không?** Không; chỉ có nghĩa method không cung cấp encoded bytes.
2. **Một `SecretKey` giả trả `null` có được SunJCE dùng như HSM key không?** Không; nó thiếu key material/handle được provider nhận diện.
3. **AES-256 software key `RAW` dài bao nhiêu byte?** 32 byte.
4. **RSA public/private thường export theo định dạng gì?** Public `X.509` SPKI, private `PKCS#8` DER.
5. **Xóa mảng lấy từ `getEncoded()` đã xóa đối tượng key gốc chưa?** Chưa; key gốc/provider giữ state riêng.
6. **PKCS12 password-protected có đồng nghĩa non-extractable?** Không; key tải ra có thể `getEncoded()` như demo.
7. **Một key chỉ `CKA_SIGN` có tự cho phép `decrypt` không?** Không; policy thao tác riêng.
8. **`Cipher.wrap` có tự lấy được mọi opaque key không?** Không; phụ thuộc key extraction/wrap policy và engine mechanism.
9. **Private key chưa từng rời HSM có nên tạo bằng software trước rồi import vào HSM không?** Không, trái điều kiện “chưa từng”.
10. **Cloud KMS luôn trả `java.security.PrivateKey` dùng được với JCE không?** Không, có KMS chỉ cung cấp SDK/RPC.
11. **Không xuất được raw key thì attacker không gây hại được nữa?** Sai; họ có thể lạm dụng `sign/decrypt` nếu có quyền gọi.
12. **Nếu mất HSM chứa key non-exportable mà không có backup thì sao?** Có thể không phục hồi được dữ liệu mã hóa.
13. **Vai trò của key ID/version khi rotation?** Tra đúng key cho bản ghi cũ hoặc xác định bản ghi cần migrate.
14. **Làm sao biết key thật sự non-extractable?** Kiểm tra policy/backend/token và thử các thao tác được phép/không phép trong quy trình bảo mật, không chỉ dựa `getEncoded()`.
15. **Khi cần thiết kế API tích hợp HSM/KMS, nên truyền raw bytes hay yêu cầu phép toán?** Ưu tiên interface operation dùng key ID/handle, hạn chế raw key tiếp xúc ứng dụng.

## Tài liệu API chính thức (Java SE 21) và tham khảo

- [Key: getAlgorithm, getFormat, getEncoded](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Key.html)
- [SecretKey](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/SecretKey.html)
- [PrivateKey và PublicKey](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/PrivateKey.html)
- [KeyGenerator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KeyGenerator.html)
- [SecretKeySpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/SecretKeySpec.html)
- [KeyFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyFactory.html)
- [KeyStore, SecretKeyEntry và PasswordProtection](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.html)
- [Provider](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Provider.html)
- [Java PKCS#11 Reference Guide](https://docs.oracle.com/en/java/javase/21/security/pkcs11-reference-guide1.html)
- [Cipher và wrap/unwrap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
- [Signature](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Signature.html)
- [Destroyable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/security/auth/Destroyable.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-lifecycle-008">11.8 Sao lưu, phục hồi và xử lý sự cố mất hoặc lộ khóa</a>

<details>
<summary>Xem chi tiết</summary>

# 11.8. Sao lưu, phục hồi và xử lý sự cố mất hoặc lộ khóa

## 1. Vì sao sao lưu khóa khác với sao lưu dữ liệu?

Trong một hệ thống dùng mật mã, **khóa** có thể là điều kiện duy nhất để giải mã cơ sở dữ liệu, phục hồi tệp, xác thực thông điệp, kiểm tra chữ ký cũ hoặc mở các khóa dữ liệu đã được bọc. Một bản sao lưu ciphertext còn nguyên vẹn **không có ích cho việc giải mã** nếu khóa cần thiết đã mất và không tồn tại phương án phục hồi hợp lệ.

Đồng thời, sao lưu khóa làm tăng số vị trí bí mật có thể bị đánh cắp. Vì vậy quản lý khóa phải cân bằng **tính sẵn sàng (availability)** với **tính bí mật, toàn vẹn, xác thực và khả năng kiểm soát sử dụng**.

Ba tình huống phải phân biệt:

1. **Khóa không truy cập được nhưng chưa có dấu hiệu bị lộ:** ví dụ node KMS tạm thời ngừng hoạt động, keystore bị xóa nhầm, thiết bị lưu khóa hỏng. Mục tiêu là phục hồi bí mật đúng phiên bản trong môi trường tin cậy, sau đó kiểm tra dữ liệu và ứng dụng. Không tự động kết luận khóa bị xâm phạm.
2. **Khóa bị mất vĩnh viễn:** không có bản sao, escrow, khả năng reconstruct/re-derive được phê duyệt hoặc cơ chế recover từ nhà cung cấp. Nếu dữ liệu chỉ được mã hóa dưới khóa đó, về nguyên tắc **không thể lấy lại plaintext bằng cách tạo khóa mới**. Đây chính là hệ quả của bảo mật mật mã.
3. **Khóa bị lộ hoặc nghi bị lộ (compromised/suspected compromise):** attacker có thể biết khóa hoặc dùng được chức năng ký/giải mã. **Chỉ restore bản sao hoặc đổi alias không làm khóa cũ an toàn lại.** Cần cô lập, xác định phạm vi, thay khóa, thay biện pháp bảo vệ dữ liệu, thu hồi quyền liên quan và xử lý hậu quả.

Bài này tập trung vào **vòng đời khóa** trong Java/JCA/JCE, không chỉ cách gọi `KeyStore.load`. Mục tiêu là xây được chính sách backup/restore, phân loại đúng mất và lộ khóa, vận hành sự cố, chọn khi nào *rotate*, *rewrap*, *re-encrypt* hay *revoke*, và hiểu giới hạn của việc cứu dữ liệu bằng bản sao lưu.

## 2. Thuật ngữ cần dùng nhất quán

| Thuật ngữ | Ý nghĩa | Cần nhớ |
| --- | --- | --- |
| **Backup (sao lưu)** | Bản sao được bảo vệ để phục hồi sau sự cố | Có lịch tạo, bảo vệ, thử phục hồi, giữ/xóa theo chính sách |
| **Recovery/restore (phục hồi)** | Khôi phục khả năng dùng đúng khóa/phiên bản cho chức năng được phép | Phải xác minh tính toàn vẹn và quyền khôi phục |
| **Archive (lưu trữ dài hạn)** | Giữ vật liệu hoặc metadata phục vụ mục đích truy cập cũ/đối soát | Không mặc định được dùng lại để tạo dữ liệu bảo vệ mới |
| **Key rotation** | Chuyển sang khóa/phiên bản mới cho thao tác mới | Không tự thay lớp mã hóa của ciphertext cũ |
| **Rewrap** | Giải bọc DEK rồi bọc lại **cùng DEK** bằng KEK mới | Không thay bí mật DEK dùng mã hóa payload |
| **Re-encrypt/rekey** | Giải mã dữ liệu cũ và mã hóa lại với **DEK mới, nonce mới** theo giao thức | Cần dữ liệu có thể truy cập và quyền dùng khóa cũ trong môi trường an toàn |
| **Revoke/disable** | Cấm hoặc giới hạn việc sử dụng khóa/chứng thư/credential | Không khiến bản sao khóa đã bị kẻ tấn công lấy trở nên vô dụng |
| **Destroy/crypto-erasure** | Hủy các bản sao key material theo chính sách, khiến dữ liệu mã hóa không thể phục hồi nếu không còn đường khác | Phải xét backup, archive, snapshot, replica và khóa dẫn xuất |
| **KeyId/keyVersion** | Định danh và phiên bản giúp tìm đúng khóa để giải mã/verify | Không được tự coi giá trị keyId từ client là chứng cứ tin cậy |

**Backup không giống replication.** Một hệ thống nhiều replica có thể cùng xóa nhầm hoặc đồng bộ khóa bị phá hoại đến tất cả bản sao. Một backup độc lập, bất biến hoặc cách ly có thể chống lỗi đó tốt hơn, nhưng cũng phải quản lý quyền đọc và kiểm tra restore.

**Backup không giống quyền sử dụng vĩnh viễn.** Khóa hết thời gian dùng để **tạo** ciphertext/chữ ký mới có thể còn cần để **giải mã** dữ liệu cũ hoặc **xác minh** chữ ký lịch sử. Chính sách phải phân biệt *originator-usage period* với *recipient-usage period* theo loại khóa và quy định áp dụng; khôi phục không có nghĩa mọi quyền tạo dữ liệu mới tự trở lại.

## 3. Không phải khóa nào cũng nên sao lưu theo cùng cách

Câu hỏi đầu tiên của một kế hoạch khôi phục: **khóa gì, ai sở hữu, dùng cho việc gì, không còn khóa thì ảnh hưởng thế nào?**

| Loại khóa | Khi mất | Chiến lược backup/restore cần cân nhắc | Khi nghi bị lộ |
| --- | --- | --- | --- |
| **DEK** (*data-encryption key*) đối xứng | Không giải mã được các bản ghi chỉ dùng DEK đó | Bọc DEK bởi KEK, giữ wrapped DEK và metadata; backup DEK riêng chỉ theo chính sách | Tạo DEK mới và **mã hóa lại** dữ liệu bị ảnh hưởng; đánh giá dữ liệu có thể đã bị đọc |
| **KEK** (*key-encryption key*) | Không giải bọc được các DEK dưới KEK đó | KMS/HSM versioning, backup hoặc dự phòng theo cơ chế được nhà cung cấp cho phép | Tạo KEK mới; *rewrap* các DEK còn tin cậy; nếu old wrapped DEK có thể đã bị attacker lấy, phải đánh giá **re-encrypt bằng DEK mới** |
| **Master/root key** | Có thể mất khả năng phục hồi nhiều nhánh khóa | Quy trình DR riêng, quorum, HSM replication/backup chính thức, nhiều miền lỗi | Sự cố nghiêm trọng: xác định toàn bộ key hierarchy và phạm vi ảnh hưởng, thay thế có kiểm soát |
| **Private key ký số** | Không tạo được chữ ký **mới** bằng identity/key cũ | Nói chung **hạn chế sao lưu** và không archive private signing key chỉ để kiểm tra chữ ký cũ; policy có thể yêu cầu sinh cặp mới | Ngừng ký, thu hồi/đánh dấu key compromised, phát hành public key/chứng thư mới, rà soát chữ ký phát sinh trong thời gian nghi vấn |
| **Private key giải mã/transport** | Có thể không giải mã được key material đã gửi cho public key tương ứng | Phục hồi được theo policy, bảo vệ như secret; không đồng nhất với signing key | Thay cặp khóa; phục hồi dữ liệu và thay bảo vệ nếu cần |
| **Public key/chứng thư** | Mất khả năng verify/định danh nếu thiếu trust metadata | Giữ bản sao công khai, cert chain, trust anchors và thông tin phiên bản | Khi private key tương ứng lộ, đánh dấu/thu hồi trust, không chỉ thay file public key |
| **HMAC key/API shared secret** | Không verify tag/token cũ; có thể gián đoạn giao thức | Backup theo mục đích, quản lý thời hạn và phiên bản; tránh nhân rộng bí mật không cần thiết | Rotate, từ chối tag/token tạo dưới khóa bị lộ theo policy; phối hợp đối tác |
| **Ephemeral session key** | Mất phiên tạm thời; có thể phải handshake lại | Thường **không backup**; tạo phiên mới | Hủy phiên, đàm phán lại khóa, điều tra phạm vi |
| **Password-derived key** | Không dẫn xuất lại được nếu mất password, salt hoặc tham số cần thiết | Bảo vệ password/credential theo policy; luôn giữ salt, thuật toán, chi phí và metadata không bí mật cần cho derivation | Thay credential/derived keys khi có thể; xem dữ liệu đã bảo vệ có bị lộ hay chưa |

**Điểm quan trọng về signing key:** hướng dẫn quản lý khóa NIST SP 800-57 Part 1 Rev. 5 nêu rằng việc backup **private signature key** không được khuyến khích trong đa số trường hợp và việc archive private signature key không phù hợp chính sách tài liệu đó. Việc giữ public key/certificate cho verify chữ ký lịch sử là chuyện **khác** với giữ private key để có thể tiếp tục ký. Luôn theo quy định cụ thể của tổ chức và ngành.

**Khóa không xuất được (non-exportable):** nếu private/secret key được tạo trong HSM hoặc KMS với chính sách không export, đừng cố lấy `getEncoded()` hay copy raw key để “backup”. Nhiều provider trả `null` hoặc từ chối export. Backup/replication phải đi qua chức năng **được HSM/KMS hỗ trợ** (secure key backup, HSM-to-HSM clone, multi-region replicated key, disaster-recovery domain), nếu có; những giải pháp này có giới hạn quyền, loại khóa, vùng địa lý và các điều kiện riêng.

## 4. Thiết kế envelope encryption để giảm phạm vi phục hồi

Với dữ liệu có quy mô lớn, nên cân nhắc mô hình *envelope encryption*:

- **DEK:** khóa ngẫu nhiên cho một đối tượng, nhóm đối tượng hoặc phân vùng theo chính sách. DEK dùng để mã hóa dữ liệu bằng AEAD, ví dụ `AES/GCM/NoPadding`.
- **KEK:** khóa dùng để *wrap/unwrap* DEK. KEK có thể được quản lý bằng KMS/HSM, và DEK khi lưu thường ở **dạng đã được bọc**.
- **Metadata:** gắn bản ghi với `keyId`, `keyVersion`, thuật toán, tham số, nonce/IV, AAD, version của record, wrapped DEK và các thông tin cần để khôi phục.

```text
                 KMS/HSM: KEK phiên bản v1
                         |
DEK ngẫu nhiên --------- wrap ---------> wrappedDEK(v1)
     |                                           |
AES-GCM(payload, IV, AAD)                        |
     |                                           |
     +---- lưu cùng record: ciphertext || tag ---+
                                + IV + AAD + keyId/keyVersion

Khi đọc:
  metadata + wrappedDEK -> KMS/HSM unwrap bằng KEK v1 -> DEK
  DEK + IV + AAD + ciphertext/tag -> AEAD decrypt + xác thực
```

Lợi ích: dữ liệu lớn không phải mã hóa lại chỉ vì **KEK thường kỳ được xoay**; có thể dùng `KEK-v2` bọc lại **cùng DEK** và cập nhật metadata atomically. Nhưng nếu **DEK** đã lộ, hoặc attacker có thể đã thu cả **KEK-v1** lẫn **wrappedDEK-v1** trước khi rewrap, việc đổi lớp bọc **không xóa khả năng giải mã của kẻ tấn công từ dữ liệu cũ**. Lúc đó cần đánh giá mã hóa lại payload với **DEK mới** và giới hạn tác động quá khứ.

Một thành công của `unwrap` chỉ nói có key material dùng được; không tự chứng minh requester được phép đọc tenant hoặc record. Authorization, audit, chính sách scope và kiểm tra tag vẫn cần thiết.

## 5. Phải sao lưu những gì để phục hồi *đúng khóa*?

### 5.1. Không chỉ có một file keystore

Một **bộ recovery** hữu ích có thể bao gồm:

1. **Vật liệu khóa được phép backup:** khóa hoặc bản sao *đã bọc/mã hóa* theo đúng chính sách; không export khóa non-exportable.
2. **Key inventory và mapping:** keyId, keyVersion, alias, owner/service, tenant, purpose, thuật toán, kích thước, thời điểm tạo/kích hoạt/hết hạn và trạng thái compromised/revoked.
3. **Định dạng và tham số mã hóa:** `AES/GCM/NoPadding`, IV/nonce, tag length, AAD và serialization/schema. Mất IV/AAD/tag có thể làm dữ liệu không giải mã/xác thực được dù có key.
4. **Thông tin về bọc khóa:** wrappedDEK, wrappingKeyId/version, thuật toán wrap và chính sách access vào KMS/HSM.
5. **Thông tin KDF (nếu có):** salt, KDF name/version, iterations hoặc work factor, output length, normalization/encoding. Salt thường không bí mật nhưng **cần đúng** để tạo lại key.
6. **Chứng thư và trust metadata:** public certificate chain, trust anchors, CRL/OCSP/policy hoặc thông tin kiểm tra chữ ký dài hạn khi trường hợp sử dụng yêu cầu.
7. **Dữ liệu đã mã hóa cùng snapshot/version phù hợp:** backup database/ciphertext, file objects, manifest/checksum, schema migration và logs cần để tái dựng state.
8. **Quy trình recovery:** tài liệu có version, người có quyền, nơi lấy backup, cách verify integrity, yêu cầu dual approval, điểm dừng khẩn cấp, rollback, audit và phương án hoạt động khi KMS region mất.

Ví dụ: sao lưu riêng `KeyStore` nhưng quên **entry password**, alias hay byte `iv` tương ứng trong database vẫn có thể dẫn đến *không thể phục hồi*; ngược lại, sao lưu tất cả dữ liệu mà chỉ lưu KEK trong cùng hệ thống đang bị xóa cũng có thể gây vòng lặp không khởi động được.

### 5.2. Phải phá vòng phụ thuộc khi bootstrap

**Anti-pattern:** backup database được mã hóa bằng key A; backup key A lại nằm trong chính database đó, còn hệ thống chỉ giải mã được database sau khi đã lấy được key A. Đây là vòng phụ thuộc không có recovery path.

Thiết kế giải pháp khởi động độc lập:

- KMS/HSM có khả năng phục hồi theo chính sách bên ngoài database chính.
- Một bản key backup hợp lệ được bảo vệ bằng cơ chế độc lập có đủ người/quyền để khôi phục khi prod không hoạt động.
- Credentials/approval để khôi phục **không** chỉ nằm trong ứng dụng hoặc tài khoản có thể mất cùng sự cố.
- Quy trình có thể thực hiện trong một **môi trường sạch** khi hệ thống production nghi bị xâm nhập.

### 5.3. Bản backup phải khớp phiên bản dữ liệu

Thay đổi khóa và rewrap có thể làm metadata/wrappedDEK thay đổi. Sao lưu database tại thời điểm T nhưng chỉ có KEK ở thời điểm T+1 hoặc T−1 có thể làm một phần dữ liệu không mở được.

Do đó cần:

- Giữ các phiên bản KEK cần cho **giải mã dữ liệu hợp lệ** đến hết thời hạn truy cập/retention theo chính sách.
- Ghi chính xác `keyVersion`/`wrappingKeyVersion` vào record.
- Cập nhật wrappedDEK và metadata **atomically** hoặc có kế hoạch migrate/retry idempotent.
- Xác minh khôi phục **từ snapshot thực tế**, bao gồm cả bản ghi cũ trước rotation, không chỉ kiểm tra bản ghi mới nhất.

Không xóa bản KEK cũ khi còn dữ liệu hợp lệ không có cơ chế mở bằng khóa mới, trừ khi chính sách **crypto-shredding** chủ đích chấp nhận mất khả năng giải mã.

## 6. Bảo mật bản sao lưu khóa: backup là tài sản rất nhạy cảm

Một bản sao khóa hữu dụng cho người vận hành **cũng có thể hữu dụng cho kẻ tấn công** nếu bị lộ. Mục tiêu không phải “có thật nhiều bản copy”, mà là có đủ **bản sao độc lập, được bảo vệ và kiểm thử được**.

### 6.1. Bảo vệ bí mật và tính toàn vẹn

- **Mã hóa key backup/wrapping:** dùng KMS/HSM hoặc cơ chế bảo vệ mạnh phù hợp loại key. Với password-based keystore như PKCS#12, mật khẩu cần được tạo và bảo vệ tốt; không sử dụng mật khẩu demo, mật khẩu ngắn hoặc hardcode trong source.
- **Kiểm soát IAM:** riêng quyền backup, đọc backup, restore, import key, quản lý KEK và quyền dùng khóa cho nghiệp vụ. Áp dụng **least privilege** và phân tách trách nhiệm.
- **Dual control/quorum:** phục hồi khóa gốc quan trọng có thể yêu cầu hai hoặc nhiều người phê duyệt, được audit, không để một tài khoản đơn lẻ tự export mọi thứ.
- **Bảo đảm integrity:** checksum để phát hiện hỏng; cơ chế xác thực như MAC/chữ ký số hoặc kiểm tra tích hợp của keystore/backup service để phát hiện sửa trái phép. **Checksum không khóa không đủ** để bảo vệ trước người có quyền sửa cả backup và checksum.
- **Bảo mật vận chuyển:** dùng kênh có xác thực và mã hóa khi chuyển backup qua mạng; tránh gửi raw key vào chat, email, issue tracker hoặc log.

### 6.2. Phòng ransomware, xóa nhầm và thiên tai

- Giữ backup có **lịch sử phiên bản**, cơ chế bất biến/không sửa được trong thời gian retention nếu phù hợp, và quyền xóa độc lập với quyền vận hành hệ thống chính.
- Đặt bản sao ở miền lỗi khác: zone/region độc lập hoặc offline khi mô hình rủi ro yêu cầu.
- Thường áp dụng nguyên tắc có bản sao độc lập (ví dụ mô hình **3-2-1** về dữ liệu) nhưng không xem một con số là **bảo đảm an ninh khóa**. Mỗi bản sao bí mật cũng mở thêm bề mặt tấn công; policy khóa phải đánh giá riêng.
- Kiểm soát retention, truy cập vào snapshot cũ, đối tác lưu trữ và chu trình xóa/hủy backup hết hạn.

**Bản sao bất biến chỉ ngăn một số kiểu sửa/xóa**; nó không ngăn kẻ có quyền đọc snapshot lấy raw key hay ciphertext. Cần kết hợp kiểm soát đọc và mã hóa.

### 6.3. Split knowledge và chia sẻ bí mật

Với root recovery material có giá trị cao, có thể dùng kỹ thuật **secret sharing** hoặc cơ chế quorum do HSM/vendor hỗ trợ. Ví dụ lược đồ k-of-n cho phép khôi phục khi có đủ mảnh được bảo vệ, giảm phụ thuộc một người.

Tuy nhiên:

- Đừng chia AES key bằng cách cắt các đoạn byte rồi tự gọi đó là Shamir Secret Sharing; cần lược đồ toán học và implementation chuẩn có xử lý lỗi.
- Mảnh khóa vẫn là bí mật; phải bảo vệ, đánh số, quản lý người giữ, tránh hai người cùng một domain lỗi và có quy trình chống lộ mảnh.
- Phải kiểm thử khôi phục thật trong môi trường kiểm soát. Quorum không thay thế audit hay phê duyệt.
- Nếu mất đủ nhiều mảnh dưới ngưỡng khôi phục, dữ liệu có thể **mất vĩnh viễn**.

### 6.4. Chống rò rỉ qua vận hành

Không in `SecretKey.getEncoded()`, private key, plaintext recovery password hay bytes chứa key vào CI logs, APM, exception text, crash dump, support ticket. Hạn chế heap dump/snapshot trên máy xử lý khóa. Java `Arrays.fill` trên một mảng chỉ là thao tác dọn **mảng đó**, không bảo đảm mọi bản sao đã biến mất khỏi JVM/provider/OS.

## 7. RTO, RPO và kiểm tra phục hồi

- **RTO (Recovery Time Objective):** thời gian gián đoạn tối đa có thể chấp nhận cho chức năng mã hóa/giải mã/xác minh.
- **RPO (Recovery Point Objective):** mức mất dữ liệu/trạng thái cho phép, ví dụ bản ghi metadata hay mapping key version. Với key material **không tái tạo được**, việc mất **một phiên bản khóa cần thiết** có thể khiến dữ liệu tương ứng không bao giờ phục hồi, dù bản ghi dữ liệu có backup.
- **Recovery rehearsal:** diễn tập định kỳ để kiểm tra không chỉ “file backup tồn tại” mà cả **mở được keystore và giải mã được dữ liệu mẫu thật** dưới key version đúng trong môi trường cô lập.

Một bài kiểm thử phục hồi tốt nên đo các mốc: lấy bản backup, xác nhận tính toàn vẹn, phê duyệt quyền, khởi tạo KMS/HSM, load key, khôi phục metadata/data, giải mã/verify bản ghi cũ và mới, vận hành với traffic giới hạn, hoàn tất audit. Ghi lại thời gian thực tế so với RTO/RPO.

**Không đưa khóa production ra máy phát triển chỉ để thử backup.** Dùng môi trường DR tách biệt, role có giới hạn hoặc test keys; đối với khóa production chỉ thực hiện quy trình phục hồi được phê duyệt, có kiểm soát và log phù hợp.

## 8. Quy trình phục hồi khi khóa mất nhưng KHÔNG nghi bị lộ

### Bước 1 — Phân loại và khoanh vùng sự cố

Xác định đây là **mất khả năng truy cập** (KMS down, quyền bị thu hồi nhầm), **hỏng key store**, **xóa nhầm key version**, hay **mất mọi bản sao**. Kiểm tra telemetry, lịch rotation, thay đổi IAM, keyId/version và thông báo của nhà cung cấp. **Đừng tạo key mới dưới cùng alias rồi mong giải mã được ciphertext cũ.**

Nếu phát hiện dấu hiệu truy cập trái phép hoặc không xác định được tính tin cậy, chuyển sang quy trình **nghi compromised**, không vô điều kiện restore rồi đưa khóa cũ vào production.

### Bước 2 — Xác nhận một nguồn backup đáng tin

Lấy manifest đã ký/được xác thực, snapshot tương ứng với phiên bản dữ liệu, kiểm tra nguồn lưu trữ, quyền đọc và tính toàn vẹn. Xác định **đúng bí mật** cần phục hồi (DEK, KEK, private decrypt key, metadata).

Nếu key non-exportable từ HSM/KMS, chỉ dùng tính năng sao lưu/replication/restore chính thức được hỗ trợ; **không cố dump raw key** để lách bảo vệ.

### Bước 3 — Khôi phục có kiểm soát

Phục hồi trong môi trường sạch, với phê duyệt theo policy. Kiểm soát secret handling, không log material, gắn alias/key version chính xác, ngăn nhầm tenant và đặt lại IAM. Nếu khóa cũ đã hết kỳ **tạo** bảo vệ mới, chỉ cho phép mục đích **đọc/giải mã** đã được phép.

### Bước 4 — Xác minh ứng dụng

Dùng dữ liệu kiểm tra đã biết để xác nhận key phục hồi đúng: giải mã AEAD thành công **và kiểm tra tag**, mở được bản ghi từ trước/sau rotation, không dùng sai algorithm/IV/AAD, các phiên bản khóa vẫn tra được. Rà soát quyền và audit, rồi cho traffic trở lại dần.

### Bước 5 — Điều tra nguyên nhân gốc

Nếu lỗi do policy/IAM, automation xóa key quá sớm, thiếu snapshot key metadata hoặc vùng DR không đủ, cập nhật quy trình và kiểm thử lại. Không chỉ giải quyết một yêu cầu restore duy nhất.

**Trường hợp không còn bản sao đáng tin:** nếu ciphertext được bảo vệ bởi một khóa ngẫu nhiên mạnh đã bị hủy và không còn recovery path, không có API Java nào “đoán lại khóa”. Có thể tìm các đường khôi phục *đã tồn tại* như wrappedDEK dự phòng hoặc vùng KMS dự phòng; nếu không có, phải công bố mức ảnh hưởng và phục hồi dữ liệu từ **nguồn plaintext hợp lệ khác**, nếu tồn tại. Không hạ chuẩn mật mã hoặc đưa dữ liệu sai ra làm dữ liệu đã phục hồi.

## 9. Khi khóa bị lộ hoặc NGHI bị lộ: incident response khác hẳn

**Nguyên tắc:** khi private/secret key có thể đã bị attacker sao chép, việc thay password bảo vệ file hoặc vô hiệu hóa một alias **không thu hồi được byte khóa đã lộ**. Kẻ tấn công có thể đã có key/ciphertext/tag/bản backup cũ. Cần xác định tác động đối với cả dữ liệu hiện tại và lịch sử.

### 9.1. Phát hiện, kích hoạt và bảo toàn chứng cứ

Các chỉ dấu: key export bất thường, bản keystore trong repository/public bucket, KMS decrypt/sign gọi từ nguồn không quen, private key xuất hiện trong CI logs, quyền cloud bị chiếm, bản backup bị tải trái phép, chứng thư hoặc token ký xuất hiện ngoài hệ thống.

Khi nghi vấn:

1. Kích hoạt quy trình incident response, xác định **incident commander**, các nhóm Security, SRE, application, compliance/pháp lý và owner dữ liệu.
2. **Hạn chế ngay khả năng sử dụng khóa**: tạm dừng endpoint ký/giải mã không cần thiết, vô hiệu hóa credential đã bị đánh cắp, cô lập workload và hạn chế quyền KMS. Các bước phải cân nhắc ảnh hưởng availability và thứ tự cho hệ thống tối quan trọng.
3. Bảo vệ log, dấu vết truy cập, phiên bản backup, audit KMS và cấu hình để điều tra; không trì hoãn containment chỉ vì muốn thu thêm bằng chứng.
4. Ghi rõ **thời điểm nghi bắt đầu và kết thúc**, service/tenant/key versions bị ảnh hưởng và phạm vi người có thể đã đọc hoặc sửa dữ liệu.

### 9.2. Tạo khóa mới trong miền tin cậy mới

Sinh **key material mới độc lập**, ưu tiên KMS/HSM/vùng tin cậy chưa bị xâm nhập, không chỉ sửa tên file hoặc thay `alias`. Nếu control plane, máy chủ hoặc IAM đã bị attacker chiếm, phải xử lý root cause và credential trước/sóng song khi đưa khóa mới vào; nếu không attacker có thể lấy ngay key mới.

Đổi cấu hình writer/service để **dừng dùng key bị lộ cho thao tác bảo vệ mới**. Duy trì quyền chỉ-đọc/giải mã cũ (nếu thực sự cần phục hồi dữ liệu và được phê duyệt) trong môi trường cô lập với thời hạn rất ngắn, rồi giảm quyền/hủy theo kế hoạch.

### 9.3. Xử lý tài sản đã bảo vệ bằng khóa lộ

| Khóa bị lộ | Hậu quả tiềm năng | Biện pháp thường cần |
| --- | --- | --- |
| **DEK** mã hóa dữ liệu | Attacker có thể đọc ciphertext tương ứng họ có được và tạo ciphertext/tag giả nếu nắm các tham số | Tạo **DEK mới**, mã hóa lại dữ liệu cần bảo vệ bằng nonce mới, đánh giá dữ liệu từng bị đọc/sửa |
| **KEK** bọc DEK | Attacker có thể unwrap những wrappedDEK có thể truy cập, kể cả trong backups cũ | Rotate KEK; **rewrap** DEK còn tin cậy; nếu old KEK + old wrappedDEK có thể đã lộ, xem DEK như compromised và **re-encrypt payload** |
| **Private signing key** | Attacker có thể ký dữ liệu/token/phần mềm giả | Dừng sign, thu hồi cert/trust, phát hành key mới, rà soát và có thể tái phát hành artefact, invalidation token/allowlist theo policy |
| **HMAC/API shared secret** | Attacker có thể tạo tag/token hợp lệ | Rotate/revoke shared secret, cập nhật tất cả bên tham gia, loại bỏ token/tag cũ theo cửa sổ ảnh hưởng |
| **TLS private key** | Có nguy cơ giả mạo endpoint và ảnh hưởng một số phiên theo giao thức/cipher suite | Thay keypair/cert, thu hồi cert khi phù hợp, cập nhật trust/deploy; đánh giá phiên lịch sử dựa vào cơ chế forward secrecy |
| **Root/master key** | Nhiều nhánh key hierarchy có thể lộ | Kích hoạt DR/IR mức cao, liệt kê toàn bộ khóa con và dữ liệu, thay thế theo dependency graph, giảm phạm vi và phối hợp với vendor |

**Không thể “làm cho dữ liệu đã bị đọc trở lại bí mật”.** Re-encrypt giúp bảo vệ bản **mới** sau khi khóa cũ không còn được dùng, nhưng không xóa ciphertext cũ hay plaintext attacker đã lấy. Retention và quyền truy cập vào snapshot cũ là một phần của đánh giá.

### 9.4. Rewrap KHÔNG luôn tương đương re-encrypt

Ví dụ cần phân biệt:

- **Đổi KEK định kỳ, không có dấu hiệu lộ:** một DEK vẫn bí mật có thể được bọc lại bằng KEK mới. Ciphertext của payload không đổi; thời gian migration thường thấp.
- **Lộ KEK nhưng các DEK và wrappedDEK cũ chắc chắn không lọt vào tay attacker theo threat model:** rewrap có thể là một phần biện pháp, nhưng phải đánh giá bằng chứng và các bản backup cũ; không dễ khẳng định chắc chắn.
- **Lộ DEK, hoặc lộ KEK + các wrappedDEK mà attacker có thể giữ:** thay KEK **không lấy lại tính bí mật** của DEK cũ. Cần **DEK mới** và re-encrypt dữ liệu; dữ liệu lịch sử đã bị copy có thể vẫn truy cập được bằng key cũ.
- **Lộ signing private key:** không có “rewrap signatures”. Chữ ký đã sinh cần được xét theo thời gian, quy trình ký, timestamp/evidence và chính sách tin cậy; việc phát hành khóa mới không tự làm các chữ ký giả thời kỳ cũ biến mất.

### 9.5. Thu hồi, thông báo và xác minh kết thúc

- Với chứng thư cần phát hành/kiểm tra cơ chế thu hồi như CRL/OCSP theo PKI áp dụng; lưu ý người nhận có thể cache, bỏ qua hoặc áp dụng chính sách khác. Chỉ “đã thu hồi cert” không đảm bảo mọi verifier ngay lập tức dừng tin khóa.
- Với JWT/token ký hoặc HMAC, phải xét token đã phát hành, TTL, keyId, session, cache và những verifier vẫn chấp nhận khóa cũ. Thay `kid` hoặc khóa sign mới không tự hủy token cũ khi verifier vẫn cho phép public key/secret cũ.
- Với dữ liệu có thể đã bị đọc hoặc sửa, đánh giá nghĩa vụ thông báo theo hợp đồng/pháp lý/quy định ngành. Không tự khẳng định đã có breach dữ liệu chỉ từ một log bất thường; phải phân loại và ghi rõ mức độ chắc chắn.
- Kiểm thử lại quyền, đo hoạt động bất thường sau rotation, đối soát số bản ghi được migrate, tag AEAD và khả năng đọc, rà soát snapshot cũ, kế hoạch xóa khóa cũ và bằng chứng audit.
- Sau sự cố, thực hiện postmortem và cập nhật quyền export/backup, IAM, DR, chính sách lưu key, logging, automation và drill.

## 10. Java 17+: ví dụ PKCS#12 backup và restore một AES SecretKey

Java cung cấp `java.security.KeyStore` để quản lý các entry khóa/chứng thư. Với JDK/provider hỗ trợ **PKCS#12** chứa `SecretKeyEntry`, ta có thể đặt một khóa AES trong keystore có mật khẩu bảo vệ, serialize keystore và load lại.

**Đây là bài demo kỹ thuật, không phải giải pháp backup production:** khóa được tạo ngẫu nhiên, các password cố định trong mã **chỉ để minh họa**, bản backup chỉ ở bộ nhớ cùng process, không có backup bất biến/offline, trust, HSM/KMS, quorum, audit hay DR. Không dùng password hoặc mẫu lưu backup này nguyên xi trong ứng dụng thật.

Chương trình làm bốn phép thử:

1. Tạo AES key và dùng AES-GCM mã hóa một chuỗi đơn giản.
2. Đưa key vào `KeyStore.SecretKeyEntry` với alias `dek-v1` và `KeyStore.PasswordProtection`, ghi keystore dạng PKCS#12 thành bytes.
3. Khởi tạo KeyStore **mới từ backup bytes**, lấy lại key bằng entry password và giải mã ciphertext. Bên khôi phục phải có **IV**, ciphertext và tag từ bản ghi dữ liệu; key một mình không đủ.
4. Thử entry password sai và một AES key khác để thấy restore/decrypt bị từ chối.

Tạo `KeyBackupRestoreDemo.java`:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class KeyBackupRestoreDemo {
    private static byte[] encrypt(SecretKey key, byte[] iv, byte[] plaintext)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return cipher.doFinal(plaintext);
    }

    private static byte[] decrypt(SecretKey key, byte[] iv, byte[] encrypted)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return cipher.doFinal(encrypted);
    }

    public static void main(String[] args) throws Exception {
        // Demo-only passwords; never hardcode actual recovery credentials.
        char[] storePassword = {'d','e','m','o','-','s','t','o','r','e','-','p','a','s','s'};
        char[] entryPassword = {'d','e','m','o','-','e','n','t','r','y','-','p','a','s','s'};
        byte[] backupBytes = null;
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            SecretKey originalKey = generator.generateKey();
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            byte[] plaintext = "ho-so-du-lieu-v1"
                    .getBytes(StandardCharsets.UTF_8);
            byte[] ciphertextAndTag = encrypt(originalKey, iv, plaintext);

            KeyStore backupStore = KeyStore.getInstance("PKCS12");
            backupStore.load(null, storePassword);
            KeyStore.PasswordProtection protection =
                    new KeyStore.PasswordProtection(entryPassword);
            try {
                backupStore.setEntry("dek-v1",
                        new KeyStore.SecretKeyEntry(originalKey), protection);
            } finally {
                protection.destroy();
            }

            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            backupStore.store(sink, storePassword);
            backupBytes = sink.toByteArray();

            KeyStore restoredStore = KeyStore.getInstance("PKCS12");
            restoredStore.load(new ByteArrayInputStream(backupBytes), storePassword);
            SecretKey recoveredKey =
                    (SecretKey) restoredStore.getKey("dek-v1", entryPassword);
            if (recoveredKey == null) {
                throw new IllegalStateException("Missing key alias");
            }
            System.out.println("Recovered alias exists: " +
                    restoredStore.containsAlias("dek-v1"));
            System.out.println("Restored decrypts payload: " +
                    Arrays.equals(plaintext,
                            decrypt(recoveredKey, iv, ciphertextAndTag)));

            boolean rejectedWrongEntryPassword = false;
            char[] wrongPassword = {'w','r','o','n','g'};
            try {
                restoredStore.getKey("dek-v1", wrongPassword);
            } catch (UnrecoverableKeyException expected) {
                rejectedWrongEntryPassword = true;
            } finally {
                Arrays.fill(wrongPassword, '\0');
            }
            System.out.println("Wrong entry password rejected: " +
                    rejectedWrongEntryPassword);

            SecretKey unrelatedKey = generator.generateKey();
            boolean rejectedWrongKey = false;
            try {
                decrypt(unrelatedKey, iv, ciphertextAndTag);
            } catch (AEADBadTagException expected) {
                rejectedWrongKey = true;
            }
            System.out.println("Unrelated key decrypt rejected: " +
                    rejectedWrongKey);
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(entryPassword, '\0');
            if (backupBytes != null) Arrays.fill(backupBytes, (byte) 0);
        }
    }
}
```

Biên dịch và chạy (Java 17+ với provider hỗ trợ PKCS#12 SecretKeyEntry và AES-GCM):

```bash
javac KeyBackupRestoreDemo.java
java KeyBackupRestoreDemo
```

Stdout đã kiểm chứng với OpenJDK/provider thử nghiệm:

```text
Recovered alias exists: true
Restored decrypts payload: true
Wrong entry password rejected: true
Unrelated key decrypt rejected: true
```

### 10.1. Điểm cần hiểu sau ví dụ

- `KeyStore.getInstance("PKCS12")` chỉ định **loại keystore**; `SecretKeyEntry` giữ key, `PasswordProtection` biểu diễn thông tin bảo vệ entry. `KeyStore.store` serialize bản backup; `load` và `getKey` khôi phục key theo alias.
- `storePassword` và `entryPassword` là hai **vai trò** bảo vệ: keystore integrity/load và khả năng lấy từng entry; provider có thể có giới hạn riêng về password và hỗ trợ loại entry. Không coi việc code chạy trên một provider là bảo đảm hành vi mọi PKCS#12 provider đều giống nhau.
- PKCS#12 là **định dạng container bảo vệ dữ liệu khóa**, không phải tự động là quy trình DR đủ IAM, audit, anti-ransomware, retention, multi-region và escrow. Password demo rất yếu, **không dùng thật**.
- `AES/GCM/NoPadding` xác thực ciphertext/tag: giải mã bằng key không liên quan bị từ chối với `AEADBadTagException` trong ví dụ. Đừng giải mã mà bỏ qua lỗi tag.
- `IV` dài 12 byte chỉ cho **một lần mã hóa trong demo**. Với GCM, mỗi lần mã hóa bằng cùng key cần IV/nonce duy nhất theo policy; không tái dùng IV cho payload mới.
- `PasswordProtection.destroy()` giúp xóa bản sao password bên trong chính object protection đó. `Arrays.fill` ở `finally` xử lý một số array do caller sở hữu, nhưng **không xóa chắc chắn mọi bản sao trong KeyStore, ByteArrayOutputStream, provider, JIT/GC hoặc RAM**.
- `restoredStore.getKey` cần alias và entry password; trong production còn cần đúng key version, authorization và trust vào nguồn backup. Nếu missing alias hoặc key sai, **không tạo một AES key ngẫu nhiên thay thế rồi gọi đó là restore**.
- Nếu nhà cung cấp KMS/HSM không cho export, ví dụ PKCS#12 này **không áp dụng trực tiếp**. Sử dụng cơ chế backup/DR chính thức của vendor, và không giảm policy non-exportable chỉ để chạy được demo.

### 10.2. Điều ví dụ KHÔNG chứng minh

- Không có thao tác ghi bản backup ra môi trường lưu trữ ngoài process nên **không** chứng minh khả năng sống sót sau khi mất máy.
- Không test restore từ nhiều region, password escrow độc lập, snapshot tương thích, IAM hoặc đối chiếu audit.
- Chương trình giữ `originalKey` và `restoredStore` trong cùng process; nó kiểm thử **đường serialize/load/getKey** nhưng chưa mô phỏng mất toàn bộ original memory và chạy trên máy sạch.
- Không khẳng định backup container không thể bị brute force nếu password yếu; không dùng password trong mã thật.
- Không xác thực người được phép restore, không quản lý rotation hoặc key-compromise incident.
- Không bảo đảm xóa key/backup bytes khỏi mọi nơi trong JVM và OS.

## 11. Runbook ngắn cho một ca phục hồi production

Giả sử hệ thống có dữ liệu `AES-GCM`, DEK được bọc bằng KEK của KMS:

### A. Khi dịch vụ bị mất quyền giải mã nhưng không nghi lộ khóa

1. Mở incident, xác định keyId/version, tenant, số bản ghi và lỗi cụ thể; giữ nguyên dữ liệu và metadata.
2. Kiểm tra khả năng dùng lại KMS replica, quyền IAM hoặc key version vẫn tồn tại. **Không** tự sinh key mới dưới version cũ.
3. Lấy backup hợp lệ có integrity và phê duyệt, khôi phục trong vùng sạch bằng chức năng được phép.
4. Test decrypt AEAD một tập bản ghi từ nhiều kỳ rotation; kiểm tra tag, AAD, IV và expected plaintext/hash không nhạy cảm.
5. Khôi phục traffic có quan sát, ghi audit, sửa nguyên nhân và diễn tập lại.

### B. Khi nghi khóa bị lộ

1. Chặn export/use trái phép, cô lập nguồn rò, bảo toàn chứng cứ, xác định blast radius và mọi bản sao backup.
2. Tạo key mới trong trust domain được kiểm soát; thay IAM/credential có liên quan.
3. Ngừng **encrypt/sign mới** bằng key nghi lộ. Xử lý những dữ liệu đang được bảo vệ bởi key đó theo phân loại DEK/KEK/signing key.
4. Với dữ liệu có nguy cơ bị đọc từ ciphertext cũ, re-encrypt dưới DEK mới nếu khả thi; với KEK rotation đơn thuần, rewrap có thể đủ nếu DEK vẫn được bảo mật theo đánh giá.
5. Revoke/deprecate cert/verification keys, API tokens, shared secrets, trust pin và cache theo tác động thực tế.
6. Kiểm thử version mới, kiểm tra migration đầy đủ, ghi nhận hạn chế của dữ liệu lịch sử và nghĩa vụ thông báo.
7. Hủy/dừng dùng key cũ theo retention/khả năng đọc cũ **và** chính sách compromised; không giữ mặc định một key đã lộ chỉ vì “có thể cần sau này”.

### C. Khi chỉ còn ciphertext, mất mọi khóa cần thiết

1. Kiểm tra backup/escrow/wrappedDEK/khả năng rederive đã được thiết kế từ trước.
2. Nếu không có đường phục hồi mật mã hợp lệ, ghi nhận **không thể giải mã** bằng khóa mới; cân nhắc nguồn dữ liệu gốc khác (nếu có) và quy trình thông báo/business continuity.
3. Xem lại thiết kế DR, redundancy, lifecycle key version và chính sách crypto-erasure để tránh tái diễn.

## 12. Bảng lỗi phổ biến khi sao lưu/khôi phục

| Lỗi | Vì sao nguy hiểm | Hướng xử lý |
| --- | --- | --- |
| Chỉ backup ciphertext, không có KEK/DEK recover path | Có dữ liệu nhưng không thể giải mã | Thiết kế đầy đủ key hierarchy và DR bootstrap |
| Chỉ giữ “khóa hiện tại”, xóa mọi version cũ | Dữ liệu mã hóa trước rotation không mở được | Retain key versions cần cho recipient-use theo policy |
| Backup keystore nhưng mất password/alias/metadata | Không load/getKey được hoặc dùng sai key | Tách credential escrow, inventory và test restore |
| Lưu backup key ở cùng máy/tài khoản cloud với primary | Một lần xóa/ransomware làm mất cả hai | Tách fault domain, quyền xóa, bản sao bất biến/cách ly |
| Đưa private key raw vào repo/CI/log | Nhân bản bí mật khó kiểm soát | Cấm export không cần thiết, rotate/IR nếu lộ |
| Đổi alias và tưởng đã rotate key | Bytes secret cũ không đổi | Tạo **key material** mới và cập nhật writer/policy |
| Lộ KEK nhưng chỉ rewrap, bỏ qua backup cũ | Attacker có thể vẫn unwrap old wrappedDEK | Đánh giá lịch sử; re-encrypt bằng DEK mới khi cần |
| Lộ DEK nhưng chỉ đổi KEK | Dữ liệu vẫn có thể bị giải mã bằng DEK đã lộ | Re-encrypt payload dưới DEK mới |
| Private signing key mất và restore không theo policy | Tăng bề mặt giả mạo chữ ký, có thể vi phạm chính sách | Ưu tiên tạo cặp mới; lưu public cert cũ cho verify |
| Restore từ backup chưa kiểm chứng trong prod đang bị chiếm | Kẻ tấn công lấy luôn khóa vừa phục hồi | Kiểm chứng trust, làm sạch môi trường và quyền trước |
| Chỉ kiểm tra checksum của file backup | Người sửa được cả file và checksum vẫn qua test | Dùng integrity/authenticity có khóa hoặc kiểm soát tương đương |
| Tạo AES key mới và ghi đè alias của key bị mất | Ciphertext cũ không giải mã được, có thể làm hỏng mapping | Giữ keyId/version bất biến; tạo alias/version mới đúng |
| Xem password cố định trong demo là an toàn | File P12 dễ bị brute force hoặc bị lộ trong code | Sinh/bảo quản credential thật theo chính sách |
| Không diễn tập | Backup tồn tại nhưng không phục hồi được dưới áp lực | Test từ backup thực tế, đo RTO/RPO, audit định kỳ |
| Không cập nhật verifier hoặc token cache sau compromise | Key bị lộ vẫn được tin để ký/xác thực | Revoke/distribute trust thay thế và kiểm tra mọi consumer |
| Dùng lại nonce AES-GCM sau restore/counter rollback | Có thể phá hỏng bảo mật AEAD khi reuse (key,nonce) | Quản lý uniqueness trên mọi phiên bản/replica; khi không chắc thì rotate key |

**Chú ý khi rollback database:** nếu hệ thống cấp phát AES-GCM nonce bằng counter, khôi phục snapshot cũ có thể làm counter quay lại giá trị đã dùng, gây **reuse (key, nonce)** trên dữ liệu mới. Phải thiết kế bộ cấp phát nonce chống rollback/đa writer, hoặc **đổi khóa mã hóa** trước khi tiếp tục phát sinh record theo policy. Đừng coi backup restore chỉ là vấn đề đúng key.

## 13. Checklist vận hành và các bài thực hành

### Trước sự cố

- [ ] Có danh mục keyId/version, algorithm, key owner, dùng để encrypt/decrypt/sign/verify/wrap, dữ liệu phụ thuộc và vùng lưu.
- [ ] Quy định rõ loại key nào được backup, loại nào không export, loại nào chỉ giữ public certificate; retention theo use period.
- [ ] Key backup có mã hóa/xác thực, quyền restore độc lập, dual control phù hợp, audit và bảo vệ khỏi xóa nhầm/ransomware.
- [ ] Metadata gồm IV/tag/AAD/KDF params/wrappedDEK/KEK version; database và key snapshots phục hồi tương thích.
- [ ] Có kế hoạch phục hồi khi KMS/HSM/region/account unavailable, gồm credential escrow và bootstrap không vòng phụ thuộc.
- [ ] Có giới hạn thời gian RTO/RPO và restore rehearsal dưới môi trường DR tách biệt.
- [ ] Có playbook **khác nhau** cho mất key, key unavailable, key compromised và mất cả key backup.
- [ ] Có chiến lược rotate/rewrap/re-encrypt, keyId migration, token/certificate revocation và log chứng cứ.

### Sau khi xử lý

- [ ] Đã kiểm tra AEAD tag/verify, dữ liệu từ nhiều phiên bản và tenant, không chỉ một object mẫu.
- [ ] Đã xác minh writer không còn dùng key cũ để bảo vệ dữ liệu mới nếu key đã hết hạn/compromised.
- [ ] Đã định danh bản sao cũ còn lại (backup/snapshot/cache/replica) và rủi ro key material lộ.
- [ ] Đã đóng incident, audit hoạt động restore, lưu bài học về permission, automation và monitoring.

### Tự kiểm tra

1. Nếu ciphertext và IV còn đủ nhưng **DEK mất vĩnh viễn**, tại sao tạo AES key mới không giúp decrypt?
2. Vì sao backup **private signing key** không nên coi giống backup **DEK** để giải mã dữ liệu cũ?
3. Phân biệt **restore**, **rotate**, **rewrap**, **re-encrypt**, **revoke** và **destroy** bằng một ví dụ AES-GCM có KEK.
4. Khi lộ KEK-v1, đổi sang KEK-v2 và rewrap toàn bộ DEK có đủ ngăn kẻ tấn công đã giữ bản wrappedDEK-v1 không? Điều gì cần thêm?
5. Chỉ có file `backup.p12` nhưng không có store/entry password và key mapping thì còn thiếu những điều kiện phục hồi nào?
6. Khi mất khóa do KMS outage nhưng không có dấu hiệu lộ, vì sao không nhất thiết phải re-encrypt toàn bộ database?
7. Tại sao **khôi phục key vào production vẫn còn malware** có thể khiến sự cố nghiêm trọng hơn?
8. Sau khi restore từ snapshot cũ, vì sao AES-GCM nonce counter cần được kiểm tra trước khi encrypt mới?
9. Một chữ ký số tạo trước khi private key bị nghi lộ có còn đáng tin không? Cần thêm bằng chứng, thời gian, cert revocation và policy nào?
10. Làm sao kiểm tra được backup thành công theo nghĩa **có thể phục hồi và dùng đúng key**, chứ không chỉ “file tồn tại”?

**Bài thực hành A:** chạy `KeyBackupRestoreDemo.java` rồi thử đổi alias `dek-v1` khi gọi `getKey`, hoặc nhập entry password sai; kết luận theo lỗi trả về. Chỉ dùng khóa và password **demo**.

**Bài thực hành B:** mô phỏng records theo `keyVersion=v1` và `v2`; viết migration giả lập chỉ đọc/giải mã dữ liệu v1, ghi mới bằng v2; tránh xóa v1 khi còn record hợp lệ phụ thuộc.

**Bài thực hành C:** tạo bảng diễn tập giả định “mất KEK, không nghi lộ” và “lộ KEK + wrapped DEK”. Liệt kê nguồn tin cậy, blast radius, bước containment, số data objects cần rewrap/re-encrypt, kiểm thử và điều kiện cho phép quay lại hoạt động.

**Bài thực hành D:** diễn tập một bản backup metadata thiếu `iv` hoặc sai `keyVersion` và một lần rollback nonce counter. Ghi rõ vì sao chỉ khôi phục secret key là chưa đủ và chiến lược ngăn reuse (key, nonce).

## 14. Tài liệu tham khảo chính thức

- [NIST SP 800-57 Part 1 Rev. 5 — Recommendation for Key Management](https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final): quản lý vòng đời key material, backup, archive, recovery, compromised keys và khác biệt use period.
- [Java SE 17 — KeyStore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html): `KeyStore.getInstance`, `load`, `setEntry`, `store`, `getKey` và các loại entry.
- [Java SE 17 — KeyStore.SecretKeyEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.SecretKeyEntry.html): entry chứa `SecretKey`.
- [Java SE 17 — KeyStore.PasswordProtection](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.PasswordProtection.html): thông tin bảo vệ entry, sao chép password và `destroy`.
- [Java SE 17 — KeyGenerator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyGenerator.html): tạo khóa AES ngẫu nhiên với provider.
- [Java SE 17 — Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): AEAD, `AES/GCM/NoPadding`, xác thực tag và `AEADBadTagException`.
- [NIST SP 800-38D — GCM](https://csrc.nist.gov/pubs/sp/800/38/d/final): nguyên tắc về IV/nonce, mã hóa/xác thực dữ liệu bằng GCM.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-lifecycle-009">11.9 Giới hạn giữ bí mật trong byte&#91;&#93;, String, heap dump và thông báo lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# 11.9. Giới hạn giữ bí mật trong byte[], String, heap dump và thông báo lỗi

Trong mật mã ứng dụng Java, rất nhiều lỗi bảo mật **không nằm ở AES, RSA hay HMAC** mà xảy ra sau khi một khóa, mật khẩu hoặc plaintext đã đi vào tiến trình: giá trị bí mật bị chuyển thành `String`, bị sao chép vào những mảng khó kiểm soát, ở lại trên heap lâu hơn dự kiến, xuất hiện trong heap dump hoặc vô tình bị ghi ra log/thông báo lỗi.

Một chương trình có thể dùng thuật toán mật mã đúng chuẩn mà vẫn làm lộ khóa qua câu lệnh:

```java
logger.debug("AES key: " + keyAsHex);
```

Hoặc làm mất bí mật khi tạo lỗi từ dữ liệu do người dùng gửi:

```java
throw new IllegalArgumentException("Invalid token: " + token);
```

**Nguyên tắc cốt lõi:** trong JVM quản lý bộ nhớ tự động, chúng ta có thể **giảm số bản sao, thời gian tồn tại và bề mặt phơi lộ** của bí mật, nhưng không thể tuyên bố “xóa hoàn toàn mọi dấu vết” chỉ vì đã gọi `Arrays.fill(secret, 0)` hoặc bỏ tham chiếu. Bảo mật thực tế cần kết hợp quản lý vòng đời, API phù hợp, cấu hình runtime/observability, phân quyền vận hành, kiểm soát sự cố và threat model.

## 1. Mục tiêu học tập và phạm vi

Sau bài học, bạn có thể:
- Phân biệt **biểu diễn bí mật** trong `byte[]`, `char[]`, `String`, `SecretKeySpec`, `ByteBuffer` và các object của JCA/JCE.
- Giải thích tại sao `String` là lựa chọn không phù hợp khi ứng dụng muốn chủ động xóa một giá trị bí mật khỏi một buffer.
- Biết chính xác `Arrays.fill(...)` và `PBEKeySpec.clearPassword()` xóa **buffer nào**, nhưng cũng nhận diện những copy không thuộc quyền kiểm soát của caller.
- Xác định dữ liệu nào có thể bị thu thập bởi **heap dump, crash dump, core dump, profiler, debugger, log, tracing, error reporting và telemetry**.
- Thiết kế đường đi của password, AES key, token, ciphertext, plaintext với **mức quyền tối thiểu, vòng đời tối thiểu và báo lỗi an toàn**.
- Phân biệt **public error response** với **internal diagnostic**; tránh rò rỉ khóa, secret, token và lỗi kiểu oracle qua thông báo hoặc thời gian đáp ứng.
- Chạy một ví dụ Java 17+ cho thấy **xóa mảng của caller khác với xóa mọi bản sao bí mật**, và áp dụng checklist kiểm thử/vận hành.

Bài học này thuộc chủ đề **vòng đời khóa**: từ lúc tạo/nhận khóa, sử dụng, dẫn xuất, sao chép, lưu tạm, ghi log, xử lý lỗi, cho đến khi hết nhu cầu. Đây không phải hướng dẫn xây một secure-memory allocator mới hoặc tuyên bố Java/JVM tự bảo đảm zeroization ở mọi tầng.

## 2. Vì sao bí mật dễ rời khỏi “vị trí ta nghĩ”?

Một khóa có thể đi qua nhiều lớp:

```text
Secret source                  Java application                 JVM / Infrastructure
------------------------       -----------------------------    -----------------------------
Vault / KMS / HSM ----+-----> byte[] / char[] input --------> heap, stack, GC regions
Console / login -------+       String / parser / serializer --> extra objects, char/byte copies
Random key generator ---+       SecretKeySpec / PBEKeySpec ---> JCE/provider copies
                               Cipher / Mac / KDF ----------> native/JNI/provider buffers
                               HTTP request/response -------> framework buffers
                               Logging / exception ---------> log files, traces, APM
                               Debugging / profiling -------> heap dumps, crash artifacts
                               Retry / cache / queue -------> longer lifetime and replicas
```

Ứng dụng thường chỉ biết chắc nó đang giữ một tham chiếu như `byte[] secret`. Nhưng sau khi gọi `SecretKeySpec`, `Cipher.init`, thư viện HTTP, JSON serializer hay logger, có thể tồn tại **những bản sao khác** ở nhiều vùng nhớ và hệ thống khác nhau.

Ví dụ: phía client nhận một token dưới dạng header text; ứng dụng có thể giữ:
1. Buffer byte của network.
2. `String` do HTTP framework tạo.
3. Một `String` mới khi trim/substring hoặc nối header.
4. Một object request mang token.
5. Một log message hoặc stack trace chứa token.
6. Một bản copy trong trace collector.

Xóa một biến ở bước 4 **không đồng nghĩa** đã xóa các bản sao 1, 2, 3, 5 và 6. Vì vậy, nguyên tắc thực dụng là **hạn chế nhập bí mật vào tầng không cần bí mật**, đặc biệt logging, tracing, exception message và data serialization.

### 2.1. Có những gì được xem là bí mật?

| Dữ liệu | Mức nhạy cảm | Rủi ro khi lộ |
| --- | --- | --- |
| AES key, HMAC key, RSA private key, seed khóa | **Secret** | Có thể giải mã, giả MAC/chữ ký theo phạm vi khóa |
| Password/passphrase, PIN, recovery code | **Secret** | Đăng nhập, đoán khóa, truy cập tài khoản |
| Access token, refresh token, session cookie, API key | **Bearer secret** | Có thể chiếm phiên hoặc gọi API |
| CEK/DEK đã unwrap, traffic/session key | **Secret** | Truy cập dữ liệu được bảo vệ theo phiên/tệp |
| Plaintext sau giải mã | **Nhạy cảm theo dữ liệu** | Rò rỉ nội dung người dùng/doanh nghiệp |
| Salt, IV/nonce, public key, public certificate | Thường **không bí mật** | Vẫn cần kiểm soát tính đúng và ràng buộc ngữ cảnh |
| Password verifier, encrypted file, metadata KDF | Không phải plaintext secret, nhưng **security-sensitive** | Hỗ trợ tấn công đoán offline, phá quyền riêng tư |
| Log/trace có identifier | Tùy threat model và dữ liệu cá nhân | Liên kết hành vi, thông tin nhạy cảm, lộ metadata |

Không đơn giản nói “đã băm rồi thì không còn nhạy cảm”, hoặc “nonce công khai nên mọi log chứa nonce đều an toàn”. Việc bảo vệ còn phụ thuộc tập dữ liệu, quy mô lưu trữ và khả năng liên kết record.

## 3. `String`: tiện dụng nhưng không thể xóa có chủ đích

`String` trong Java là một kiểu **immutable** (bất biến về mặt API). Khi bạn có:

```java
String password = "...";
```

bạn không có một API công khai an toàn để ghi đè các byte chứa nội dung string bên trong JVM. Gán:

```java
password = null;
```

chỉ làm mất một tham chiếu trong chương trình. Nó **không bảo đảm** vùng nhớ cũ đã bị ghi bằng zero ngay lập tức; garbage collector quản lý thời điểm thu hồi và bộ nhớ có thể còn dấu vết hoặc bản sao.

### 3.1. Tại sao `String` tạo thêm khó khăn?

- `String` bất biến nên không có cơ chế “xóa password” theo API công khai.
- Chuỗi được nối, format, parse, encode, log hoặc truyền qua framework có thể phát sinh **nhiều object/buffer bổ sung**.
- String literal trong source có thể vào constant pool/interning; tuyệt đối **không hardcode production secret** thành literal.
- Các phiên bản JVM hiện đại có tối ưu lưu trữ string, ví dụ Compact Strings ở HotSpot, nhưng chi tiết nội bộ có thể khác nhau. Không nên dựa vào việc `String` dùng `char[]` hay `byte[]` bên trong để tìm cách sửa bằng reflection hoặc `Unsafe`.
- `StringBuilder` có thể giữ backing buffer và phát sinh `String` mới khi gọi `toString()`; dùng builder để “dễ xóa” không tự giải quyết toàn bộ vấn đề.
- Khi một thư viện chỉ nhận `String`, việc bạn tạo string từ `char[]` có thể đã tạo thêm bản sao khó xóa. Cần đánh giá API boundary thay vì cho rằng một bước chuyển đổi là vô hại.

**Tránh các thao tác sau với secret:**

```java
// KHÔNG DÙNG trong code production:
String passwordText = new String(passwordChars);
String secretAsHex = java.util.HexFormat.of().formatHex(aesKeyBytes);
System.out.println("Authorization: Bearer " + token);
throw new RuntimeException("Failed with password=" + passwordText);
```

Đây là các **anti-pattern** để nhận biết, không phải ví dụ cần chạy. In/log/format secret ra text làm tăng nguy cơ persistence và exfiltration.

### 3.2. `char[]` tốt hơn `String` đến mức nào?

`char[]` cho phép caller chủ động ghi đè các phần tử:

```java
char[] password = /* input from controlled source */;
try {
    // Use password with an API accepting char[].
} finally {
    java.util.Arrays.fill(password, '\0');
}
```

Điều này đem lại một lợi thế thực tế: **mảng mà bạn sở hữu** có thể được ghi đè sau khi dùng. Tuy nhiên, nó không bảo đảm:
- Thư viện đã không sao chép mảng.
- JIT/compiler, GC hoặc hệ điều hành không giữ dấu vết ở nơi khác.
- Mật khẩu chưa từng tồn tại dưới dạng `String` trong một tầng trước đó.
- Một heap dump hoặc logger đã không giữ bản sao trước thời điểm xóa.

`char[]` chủ yếu có ích khi **toàn bộ đường xử lý** hạn chế biến bí mật thành string hoặc log và dùng đúng lifetime. Một password đầu tiên đã đọc bằng `String` rồi chuyển qua `char[]` không “xóa” được `String` ban đầu.

### 3.3. Input password trong Java

Trong ứng dụng dòng lệnh có console thực, `Console.readPassword()` trả `char[]`, giúp tránh một số đường tạo `String` thông thường:

```java
java.io.Console console = System.console();
if (console == null) {
    throw new IllegalStateException("Secure console unavailable");
}
char[] password = console.readPassword("Password: ");
if (password == null) {
    throw new IllegalStateException("No password supplied");
}
try {
    // Use password with an API accepting char[].
} finally {
    java.util.Arrays.fill(password, '\0');
}
```

Đây là một **đoạn minh họa API**, không phải chương trình độc lập; `System.console()` thường là `null` trong IDE, nhiều môi trường container hoặc pipe. Không nên tự động fallback sang đọc password bằng `Scanner.nextLine()` mà không xem xét yêu cầu an ninh của ứng dụng.

Với HTTP, mobile, desktop UI hoặc SDK, dữ liệu thường có thể đã được framework decode vào `String`. Hãy kiểm soát **request logging, tracing, retention, memory lifetime** và tầng vận chuyển; không thể khẳng định caller đã “never held String” nếu framework ở phía trước đã tạo nó.

## 4. `byte[]`: có thể ghi đè, nhưng không đồng nghĩa xóa mọi bản sao

`byte[]` là dạng phổ biến cho AES key, nonce, plaintext, ciphertext, HMAC output và key derivation output. Với một secret byte array do chương trình sở hữu:

```java
byte[] secret = /* securely obtained secret bytes */;
try {
    // Use secret.
} finally {
    java.util.Arrays.fill(secret, (byte) 0);
}
```

Sau khi `Arrays.fill` hoàn tất, phần tử của **chính mảng được truyền vào** đã được gán zero theo mô hình Java bình thường. Nhưng đây không phải cam kết xóa hết bí mật trên toàn hệ thống.

### 4.1. Những lý do vẫn còn bản sao

1. **Constructor có thể copy.** Ví dụ `SecretKeySpec(byte[], algorithm)` lưu bản copy của key bytes. Zero hóa mảng đầu vào không phá hủy object secret key đã tạo.
2. **`getEncoded()` có thể trả bản sao** của khóa mã hóa được; một caller phải quản lý/xóa mảng trả về. Với một số key provider/HSM, `getEncoded()` có thể trả `null` vì khóa không exportable.
3. **Cipher, Mac, KeyAgreement, provider** có thể lưu dữ liệu trong object hoặc native memory; API không hứa xóa mọi bản copy khi caller xóa mảng.
4. **`Arrays.copyOf`, `clone`, `ByteArrayOutputStream.toByteArray()`** tạo mảng khác. `toByteArray()` thường tạo bản copy, còn buffer nội bộ của stream có thể tiếp tục tồn tại.
5. **String/hex/Base64 encoding** tạo text object có thể sống lâu hơn buffer ban đầu. Không dùng encoding này chỉ để “debug xem khóa có đúng không”.
6. **GC, stack và JIT** có quyền sắp xếp/di chuyển/ghi đè dữ liệu bên trong quy tắc runtime. Zeroization best-effort ở Java không cung cấp bảo đảm chống đối thủ có quyền đọc memory dump ở mọi thời điểm.

**Mệnh đề đúng:** “Tôi đã zeroize mảng `secret` do mình nắm giữ.”  
**Mệnh đề không có căn cứ:** “Sau đó không còn bất kỳ byte khóa nào trong tiến trình, swap, heap dump hay bộ nhớ provider.”

### 4.2. Cần dùng `finally` để giảm cửa sổ phơi lộ

Đừng chỉ xóa secret ở cuối happy path:

```java
byte[] keyBytes = loadSensitiveKeyBytes();
doCrypto(keyBytes); // Có thể throw exception.
java.util.Arrays.fill(keyBytes, (byte) 0);
```

Nếu `doCrypto` ném ngoại lệ, việc xóa ở cuối sẽ không chạy. Pattern an toàn hơn:

```java
byte[] keyBytes = loadSensitiveKeyBytes();
try {
    doCrypto(keyBytes);
} finally {
    java.util.Arrays.fill(keyBytes, (byte) 0);
}
```

Đây vẫn là **pseudocode dùng các hàm minh họa** `loadSensitiveKeyBytes()` và `doCrypto()`, không phải Java class độc lập. Nội dung hữu ích là cách đặt cleanup vào `finally`.

Nên kiểm soát thêm lifetime:
- Lấy key **gần nhất có thể** trước thời điểm dùng.
- Tránh giữ key trong static field, singleton lâu dài, session object hoặc global cache trừ khi có threat model và vòng đời cụ thể.
- Tránh tạo quá nhiều bản copy; giới hạn phạm vi secret qua các lớp service.
- Khi không cần key nữa, đóng session/handle của provider theo API được hỗ trợ.
- Xóa cả plaintext buffers cần thiết theo chính sách, không chỉ key bytes.

### 4.3. `Arrays.fill` là best effort chứ không phải secure erase có chứng minh

Trong một JVM managed runtime, ngoài mảng thấy được, dữ liệu có thể còn ở bản copy, register, stack, native state, swap hoặc dump đã chụp trước đó. Tối ưu compiler/JIT và cơ chế garbage collector còn khiến cam kết về **physical memory erasure** khó có thể đưa ra với Java thuần.

Khi threat model yêu cầu zeroization ở mức phần cứng/OS, không nên bịa rằng `Arrays.fill` bảo đảm được. Cần đánh giá secure-memory facilities, HSM/KMS, provider phù hợp, memory-locking nếu khả thi, quyền debug/dump và thiết kế không export secret. Các giải pháp native cũng không xóa được các bản sao mà ứng dụng từng tạo hoặc những secret đã xuất vào log.

Một số API có tên `Destroyable.destroy()` hoặc `isDestroyed()`, nhưng **không phải mọi loại `SecretKey`, `PrivateKey` hay provider đều hỗ trợ destroy thực sự theo cùng cách**. Phải kiểm tra tài liệu/behavior của implementation, xử lý lỗi và không đồng nhất `destroy()` với xóa một khóa nằm trong HSM hoặc bản backup.

## 5. `SecretKeySpec` và sao chép key material

Trong JCA/JCE, một mẫu thường gặp:

```java
byte[] keyBytes = /* AES or HMAC key bytes */;
javax.crypto.spec.SecretKeySpec key =
    new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
java.util.Arrays.fill(keyBytes, (byte) 0);
```

Lúc này **caller key array có thể đã được xóa**, nhưng `SecretKeySpec` vẫn cần chứa thông tin khóa để thực hiện phép mã hóa. Đó không phải bug: constructor đã quản lý key material riêng.

Hệ quả:

- Không xem zero hóa `keyBytes` là đã hủy toàn bộ `SecretKeySpec`/provider state.
- Tránh giữ `SecretKeySpec` lâu hơn cần thiết, đặc biệt trong object có vòng đời toàn ứng dụng.
- Không gọi `key.getEncoded()` chỉ để in hex hoặc so sánh; nó làm xuất thêm bản copy byte của secret.
- Nếu cần khóa không xuất được, dùng cơ chế provider, KeyStore, PKCS#11/HSM hoặc KMS có API handle phù hợp. **Không giả định** mọi `SecretKey` Java đều thực sự non-extractable; `SecretKeySpec` không phải khóa HSM.
- Quản lý rotation/expiration/revocation và backup của key theo hệ thống lưu khóa, không dựa vào việc GC thu hồi object.

### 5.1. Vì sao xóa mảng gốc không làm `Mac` ngừng hoạt động?

`SecretKeySpec` được tạo từ giá trị khóa đầu vào. Nếu ứng dụng xóa mảng gốc rồi gọi:

```java
javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
mac.init(secretKeySpec);
byte[] tag = mac.doFinal(messageBytes);
```

MAC vẫn có thể hoạt động bình thường vì object khóa còn giữ vật liệu cần thiết. Đây là điểm quan trọng trong ví dụ runnable ở phần 10: **còn dùng được key object** đồng nghĩa không thể kết luận rằng “mọi bản sao khóa đã bị xóa”.

Mặt khác, việc `Mac` có thể dùng object khóa sau đó **không chứng minh** bản thân MAC lưu khóa vô thời hạn. Lifetime phụ thuộc scope, implementation và cách ứng dụng giữ tham chiếu.

## 6. `PBEKeySpec` và password KDF: các bản sao thường bị bỏ qua

Java có `PBEKeySpec` để dùng password dạng `char[]` cùng salt và tham số dẫn xuất khóa:

```java
char[] password = /* user input */;
byte[] salt = /* random salt */;
javax.crypto.spec.PBEKeySpec spec =
    new javax.crypto.spec.PBEKeySpec(password, salt, 100_000, 256);
```

Constructor `PBEKeySpec` tạo bản sao thông tin password theo hợp đồng API. Vì vậy:
- Zero hóa mảng `password` của caller **không đồng nghĩa** `spec` đã mất khả năng dẫn xuất.
- Khi dùng xong, gọi `spec.clearPassword()` để xóa bản sao password do `PBEKeySpec` sở hữu.
- `spec.getPassword()` trả một **bản copy mới**; caller phải quản lý/xóa copy này nếu gọi.
- `SecretKeyFactory.generateSecret(spec)` và `getEncoded()` có thể tạo output/key material khác; quản lý lifetime của từng đối tượng và mảng output, không chỉ mảng password đầu vào.
- `PBEKeySpec` nhận `char[]`, nhưng provider có thể mã hóa password thành byte nội bộ; xóa `spec` không chứng minh tất cả bản sao provider đã bị xóa.
- Dữ liệu salt thường không bí mật, nhưng vẫn phải lưu/giữ đúng để verify password hoặc giải mã theo KDF profile.

Một ví dụ vòng đời:

```text
Caller char[] password
   |
   +-- PBEKeySpec constructor -> internal password copy
   |                                   |
   |                                   +-- SecretKeyFactory -> derived key material
   |                                                                |
   v                                                                v
Caller Arrays.fill(password, '\0')                     Derived byte[] / SecretKey / provider
             |                                                      |
             v                                                      v
Own array cleared                              Must separately scope/clear as supported
                                     |
                          spec.clearPassword() for spec's copy
```

Đối với lưu mật khẩu, dùng password-hashing scheme chuyên dụng và thư viện được thẩm định theo yêu cầu triển khai; `PBKDF2WithHmacSHA256` trong ví dụ chỉ giúp minh họa API và các bản sao. Không dùng direct SHA-256 như password verifier và không coi một giá trị PBKDF2 được lưu công khai là khóa AES có thể dùng lại.

### 6.1. Password từ framework đã là `String` thì sao?

Nếu HTTP framework trả password dạng `String`, việc chuyển sang `char[]` rồi xóa mảng **không xóa bản string đã sinh**. Khi không thể kiểm soát hết đường đi, giảm rủi ro bằng:
- Không log request body, form field hoặc authorization header chứa password.
- Không lưu password trong user/session DTO sống lâu, exception object hoặc async task queue.
- Không đưa password vào template/error message, metrics labels hay tracing span attributes.
- Thực hiện verify/dẫn xuất ngay trong scope ngắn, tránh lưu vào cache.
- Giới hạn quyền đọc heap dump, debugger và process memory.
- Tách dữ liệu cần audit khỏi dữ liệu bí mật; log trạng thái, correlation ID và loại lỗi thay vì giá trị password.

Việc thiết kế boundary phù hợp thường quan trọng hơn tranh luận “String hay char[]” nếu cả pipeline đã nhân bản bí mật qua nhiều nơi.

## 7. `ByteBuffer`, off-heap và native memory: không phải vùng an toàn mặc định

`ByteBuffer` có thể là heap-backed hoặc direct/off-heap. Cả hai cần phân biệt **position management** với **ghi đè dữ liệu**.

Ví dụ:

```java
java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(1024);
buffer.put(secretBytes);
buffer.clear(); // Chỉ đặt lại position/limit. KHÔNG zero memory!
```

`clear()` trong API buffer **không** có nghĩa xóa nội dung; nó chuẩn bị buffer để ghi/đọc theo vị trí mới. Tương tự, `flip()` và `rewind()` là thao tác con trỏ/giới hạn, không có bảo đảm wipe.

Nếu ứng dụng sở hữu một buffer mutable và muốn ghi zero:
- Phải ghi đè **đúng toàn bộ miền buffer nhạy cảm** bằng thao tác được hỗ trợ, chú ý position/limit/slice và các view cùng chia sẻ memory.
- Phải kiểm soát bản sao trong heap, native allocation, JNI/FFI, provider state.
- Phải xử lý lifetime deallocation; off-heap không tự chống crash dump, đọc process memory, swap hoặc privileged debugger.
- Không dựa vào reflection/`Unsafe` để sửa nội bộ `String` hoặc lớp JCE.
- Với yêu cầu bảo mật cao, đánh giá thư viện/native API đã được kiểm toán và chính sách hệ điều hành; bản chất direct memory **không biến nó thành HSM**.

Kể cả khi direct buffer được zero, secret có thể từng đi qua `byte[]` để gọi API hoặc được provider copy; do đó hãy nhìn toàn bộ **data flow**.

## 8. Heap dump, crash dump, profiler và các bản sao hạ tầng

### 8.1. Heap dump có thể chứa những gì?

Heap dump là ảnh chụp của các object Java trên heap vào một thời điểm. Tùy JVM/trạng thái runtime, có thể chứa:
- `String` còn giữ token/password/secret đã được chuyển sang text.
- `byte[]` và `char[]` chứa key, password, nonce, plaintext hoặc dữ liệu nhạy cảm khác.
- DTO request/response, cache entry, message payload, HTTP headers, exception object.
- Object `SecretKeySpec`, KDF context, buffers và các structure còn sống.
- Dữ liệu gián tiếp liên kết người dùng, session, tenant, hostname và hoạt động bảo mật.

Heap dump **không nhất thiết** phản ánh toàn bộ native memory/register/stack hoặc mọi bản sao trong hệ thống, nhưng chỉ riêng phần có thể chứa đã đủ để xem nó như một **tài sản nhạy cảm**. Không nên gửi heap dump production vào ticket, chat hoặc công cụ bên ngoài khi chưa có quyền, quy trình bảo vệ và redaction thích hợp.

### 8.2. Điều kiện tạo dump

Một số JVM có thể được cấu hình tạo heap dump khi out-of-memory, ví dụ `-XX:+HeapDumpOnOutOfMemoryError` và đường dẫn `-XX:HeapDumpPath=...`. Công cụ quản trị/diagnostic cũng có thể yêu cầu heap dump theo quyền tương ứng. Crash dump, core dump hoặc OS memory snapshot có phạm vi khác nhau và **có thể** chứa dữ liệu ngoài Java heap.

Rủi ro thực tế:
- Container ghi dump ra volume dùng chung, object store hoặc thư mục được backup tự động.
- Crash reporting/telemetry upload artifact lên dịch vụ ngoài vùng tin cậy.
- Một developer có quyền debug production tải dump về laptop không mã hóa.
- Dump tồn tại sau incident, không có TTL, access audit hoặc cơ chế xóa.
- Log file/ticket kèm stack trace hoặc command-line args lộ cấu hình/credentials.

**Không cần coi dump là “xấu” để cấm mọi debugging.** Heap dump có giá trị điều tra memory leak và incident. Điều đúng là phải có chính sách xử lý nó như dữ liệu nhạy cảm: quyền truy cập tối thiểu, lưu có mã hóa, retention ngắn, theo dõi truy cập, đường truyền được xác thực, redaction khi khả thi, xóa theo quy trình và không xuất ra môi trường không được phép.

### 8.3. Cấu hình vận hành nên cân nhắc

1. **Access:** giới hạn ai có quyền attach debugger/profiler, tạo hoặc download dump; đánh giá quyền hệ điều hành/container/hypervisor.
2. **Storage:** dump directory/volume với quyền hệ thống tệp chặt, mã hóa at rest và key management riêng nếu phù hợp.
3. **Transfer:** kênh vận chuyển bảo mật; tránh URL dump có quyền công khai hoặc link lâu hạn.
4. **Retention:** thời gian giữ đủ cho điều tra, tự động xóa theo policy, có audit.
5. **Automation:** biết rõ agent nào có thể kích hoạt và upload diagnostic bundles; vô hiệu hóa thu thập nhạy cảm không cần thiết.
6. **Incident response:** nếu có bằng chứng key/password/token đã xuất vào dump không kiểm soát, đánh giá **rotation/revocation** và ảnh hưởng dữ liệu theo scope.
7. **Backups:** kiểm tra dump bị copy sang hệ backup hoặc DR không; xóa primary file không nhất thiết xóa mọi bản backup.
8. **Testing:** dùng canary giả, môi trường staging và công cụ được phê duyệt để kiểm tra rủi ro; không cần thu thập secret production thật để chứng minh.

Heap dump là một **snapshot theo thời gian**. Xóa `byte[]` sau snapshot không xóa được artifact đã chụp. Một lỗi logging hoặc export dump có thể kéo dài tuổi thọ secret từ vài mili giây trong RAM thành nhiều tháng trên storage.

### 8.4. Heap dump khác với log và stack trace

- **Heap dump:** có thể chứa trạng thái object thô ngay cả khi code không chủ động log key.
- **Log:** do ứng dụng/framework tạo ra khi log request, error, object `toString()` hoặc diagnostic.
- **Stack trace:** chứa stack frames và exception messages/causes; nếu exception chứa thông tin nhạy cảm trong message, log stack trace sẽ phát tán nó.
- **Distributed tracing/APM:** có thể copy HTTP headers/body, SQL parameters và baggage/span attributes vào hệ thống khác.
- **Metrics:** label/tag có cardinality cao hoặc chứa token/user identifiers có thể rò rỉ ra dashboards và lưu rất lâu.

Thực tế phải có chính sách cho **từng loại dữ liệu quan sát**; chỉ tắt một logger không loại bỏ đường leak từ tracing, error reporting hoặc crash artifact.

## 9. Thông báo lỗi: nơi secret thường bị đưa ra khỏi tiến trình

### 9.1. Error message không nên chứa secret

Không nên làm:

```java
// Anti-pattern: KHÔNG sử dụng.
throw new IllegalArgumentException("Invalid API key: " + apiKey);
throw new SecurityException("Expected=" + expectedMac + ", actual=" + receivedMac);
logger.error("Failed to decrypt " + plaintext, exception);
```

Rủi ro:
- Exception message có thể đi vào access log, app log, APM, alert, support ticket.
- Framework có thể tự chuyển exception message thành HTTP response.
- Exception `toString()` và stack trace có thể bao gồm message/causes.
- Khi log một object bằng `{}`, `toString()` của nó có thể tiết lộ các field nhạy cảm.
- Exception được ném trong async/retry/queue có thể được lưu hoặc serialize bởi infrastructure.

**Đừng đưa secret vào exception ngay từ đầu.** “Log nội bộ thì được” không phải lý do để gắn password/key/token vào message rồi hy vọng không bị lộ.

### 9.2. Phân tách public error với internal diagnosis

Đối với client chưa tin cậy, trả một **mã lỗi ổn định và thông điệp chung**:

```json
{
  "code": "CRYPTO_OPERATION_FAILED",
  "message": "The requested operation could not be completed."
}
```

Với logic đăng nhập, có thể cần một thông điệp chung kiểu `AUTHENTICATION_FAILED` khi credential sai. Với mã hóa/giải mã, đừng trả “khóa AES đúng nhưng GCM tag sai tại byte X”, “private key đã unwrap được” hoặc giá trị tham số nhạy cảm cho caller bên ngoài.

Trong **internal telemetry có phân quyền**, có thể ghi:
- Correlation/request ID ngẫu nhiên.
- Tên component/op không nhạy cảm (ví dụ `decrypt-document`).
- Mã lỗi được whitelist (ví dụ `AUTH_TAG_INVALID`), mức độ nghiêm trọng và số lần xảy ra.
- Thời gian, phiên bản protocol, key identifier không nhạy cảm **nếu chính sách cho phép**.
- Stack trace đã được đánh giá, không chứa token/password hoặc dữ liệu decrypted.

Không log:
- AES/private/HMAC/traffic keys, seed, session cookie, bearer token, refresh token, password.
- Raw headers `Authorization`, `Cookie`, `Set-Cookie`, secret form fields.
- Plaintext từ tệp bí mật, KDF derived key, key wrap material chưa mã hóa.
- Exception cause hoặc object dump khi chưa biết chắc message và `toString()` không mang bí mật.

Đừng biến `keyId` hoặc hash một field nhạy cảm thành định danh công khai một cách tự động: chúng vẫn có thể lộ metadata, tương quan hoạt động hoặc hỗ trợ đoán nếu đầu vào ít entropy.

### 9.3. Phân biệt mã lỗi để vận hành nhưng không tạo oracle

Một API cần đủ thông tin cho bảo trì, nhưng lỗi mật mã có thể trở thành **oracle** nếu attacker quan sát được khác biệt về:
- Thông báo trả ra: “RSA unwrap succeeded” vs “AES tag invalid”.
- HTTP status/body tùy password tồn tại, token có prefix đúng, private key nào được chọn.
- Thời gian phản hồi phụ thuộc mức kiểm tra/chỗ thất bại.
- Kích thước response, đóng kết nối hay số lần retry khác nhau.
- Timing của so sánh secret bằng string thông thường.

Vì vậy, thiết kế error mapping cần theo **threat model của giao thức**, không chỉ ẩn text trong response. Các nhóm lỗi nhạy cảm có thể cần hợp nhất ở public API, giới hạn request, giữ thời gian hành vi hợp lý, áp rate limiting và kiểm tra khóa theo policy. Điều này **không có nghĩa** có thể dễ dàng bảo đảm constant-time cho toàn bộ HTTP stack; cần dùng thư viện mật mã đã được kiểm tra và tránh tạo nhánh tiết lộ secret rõ ràng.

Ví dụ, nếu một API kiểm tra HMAC và dùng:

```java
if (expectedTagHex.equals(receivedTagHex)) { ... }
```

thì cách so sánh text thông thường không được thiết kế là phép so sánh authentication tag bí mật chống timing leak. Nên **decode đúng wire format, kiểm tra độ dài và dùng API so sánh byte dành cho bảo mật** như `MessageDigest.isEqual(...)` khi thích hợp. Cũng không dùng “thử nhiều thuật toán/khóa tới khi đúng” mà thiếu version/policy, vì dễ có algorithm/key confusion và tạo nhiều đường quan sát lỗi.

## 10. Java 17+: kiểm chứng những giới hạn bộ nhớ và báo lỗi an toàn

Ví dụ runnable này **không cố đo nội dung heap dump** (vốn phụ thuộc JVM/GC/thời điểm), mà kiểm chứng những mệnh đề có thể quan sát qua API:

1. Mảng `char[]` password của caller được xóa; `PBEKeySpec` vẫn có thể cho KDF chạy vì đã sở hữu một bản copy.
2. `PBEKeySpec.clearPassword()` ngăn đọc password qua `getPassword()` sau khi đã clear.
3. Mảng chứa derived KDF output được ghi zero trong `finally`.
4. Mảng byte nguồn dùng tạo `SecretKeySpec` đã bị xóa, nhưng **key object vẫn dùng được** cho HMAC. Đây là minh chứng rằng việc xóa buffer nguồn không xóa hết key material.
5. Một thông báo public error chỉ trả **mã lỗi được allowlist**, không đưa nguyên văn message provider ra ngoài.

Tạo file **`SecretMemoryDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class SecretMemoryDemo {
    static boolean zeroed(char[] value) {
        for (char c : value) {
            if (c != '\0') return false;
        }
        return true;
    }

    static boolean zeroed(byte[] value) {
        for (byte b : value) {
            if (b != 0) return false;
        }
        return true;
    }

    static String publicError(Exception ignored) {
        return "CRYPTO_OPERATION_FAILED";
    }

    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();

        // Public fixture for demonstration only.
        // Do not hardcode real passwords in source.
        char[] password = {'d', 'e', 'm', 'o', '-', 'p', 'a', 's', 's'};
        byte[] salt = new byte[16];
        random.nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(password, salt, 100_000, 256);
        Arrays.fill(password, '\0');

        byte[] derived = null;
        try {
            derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();

            System.out.println("Caller password cleared: "
                    + zeroed(password));
            System.out.println("KDF still worked after caller clear: "
                    + (derived.length == 32));
        } finally {
            spec.clearPassword();
            if (derived != null) {
                Arrays.fill(derived, (byte) 0);
            }
        }

        boolean passwordAccessRejected = false;
        try {
            spec.getPassword();
        } catch (IllegalStateException expected) {
            passwordAccessRejected = true;
        }
        System.out.println("PBEKeySpec password access rejected: "
                + passwordAccessRejected);
        System.out.println("Derived buffer cleared: "
                + zeroed(derived));

        byte[] originalKeyBytes = new byte[32];
        random.nextBytes(originalKeyBytes);
        SecretKeySpec key = new SecretKeySpec(
                originalKeyBytes, "HmacSHA256"
        );
        Arrays.fill(originalKeyBytes, (byte) 0);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        byte[] tag = mac.doFinal(
                "public demo input".getBytes(StandardCharsets.UTF_8)
        );

        System.out.println("Original key array cleared: "
                + zeroed(originalKeyBytes));
        System.out.println("Key object still usable: "
                + (tag.length == 32));

        Arrays.fill(tag, (byte) 0);
        System.out.println("Tag buffer cleared: " + zeroed(tag));

        try {
            throw new GeneralSecurityException(
                    "provider-specific diagnostic"
            );
        } catch (GeneralSecurityException internal) {
            System.out.println("Public error: "
                    + publicError(internal));
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac SecretMemoryDemo.java
java SecretMemoryDemo
```

**Kết quả stdout đã kiểm chứng qua JDK tương thích Java 17:**

```text
Caller password cleared: true
KDF still worked after caller clear: true
PBEKeySpec password access rejected: true
Derived buffer cleared: true
Original key array cleared: true
Key object still usable: true
Tag buffer cleared: true
Public error: CRYPTO_OPERATION_FAILED
```

### 10.1. Diễn giải đúng các dòng output

**“Caller password cleared: true”** chỉ nói mảng `password` của chương trình đã được ghi toàn `'\0'`. Nó không nói password chưa từng có ở một object khác.

**“KDF still worked after caller clear: true”** cho thấy `PBEKeySpec` có dữ liệu password cần thiết dù caller đã xóa mảng input. Nếu code chỉ xóa caller `char[]` nhưng quên `spec.clearPassword()`, bản copy trong spec có thể còn tồn tại cho đến khi object được thu hồi. Một số bước trong provider có thể tạo thêm bản sao không được kiểm soát bởi `clearPassword()`.

**“PBEKeySpec password access rejected: true”** kiểm tra hợp đồng API: sau `clearPassword()`, gọi `getPassword()` bị từ chối. Điều này **không** chứng minh toàn bộ RAM đã được wipe.

**“Derived buffer cleared: true”** xác nhận ứng dụng ghi zero vào chính mảng `derived` trong `finally`. Nội bộ provider, object secret key hoặc JVM có thể còn bản sao. Mỗi buffer có lifetime và cleanup khác nhau.

**“Original key array cleared: true” + “Key object still usable: true”** là điểm quan trọng: `SecretKeySpec` có key material riêng; xóa mảng nguồn không hủy key object. Không nên tuyên bố “key đã được hủy an toàn” chỉ dựa trên mảng gốc đã zero.

**“Tag buffer cleared: true”** minh họa có thể quản lý output buffer. Một HMAC tag thông thường không nhất thiết là secret như key, nhưng rủi ro logging, context và chính sách retention vẫn cần xét. Đây là mẫu quản lý mảng, không phải bắt buộc mọi ứng dụng phải zero tất cả tag công khai.

**“Public error: CRYPTO_OPERATION_FAILED”** minh họa API trả một **mã lỗi ổn định**. Trong production, cần thêm correlation ID, telemetry có phân quyền và quy tắc error mapping theo threat model.

### 10.2. Những giới hạn cố ý của demo

- Password demo được viết trong source bằng các ký tự riêng lẻ. **Không** viết secret thực vào source/config/version control; đây là fixture công khai và không chứng minh input an toàn ở một hệ thống nhận password thực.
- `PBKDF2WithHmacSHA256` với 100.000 iterations **chỉ phục vụ demo memory lifecycle**, không phải tham số production khuyến nghị để lưu mật khẩu. Khi triển khai thật, chọn password-hashing/KDF scheme và cost theo chính sách/benchmark hiện hành, ưu tiên Argon2id khi phù hợp và thư viện tin cậy.
- `SecretKeySpec` tiếp tục tồn tại đến khi hết scope; demo không có API để wipe key material nội bộ của nó. Không suy ra “mảng nguồn zero là process hết secret”.
- Dữ liệu provider, JIT/GC, native heap, swap, profiler và dump **không thể kiểm tra đầy đủ** chỉ bằng unit test này.
- `Arrays.fill` ở cuối không ngăn một heap dump được chụp **trước đó** chứa bí mật.
- Mẫu `publicError` không tự cung cấp security logging, rate limiting, chống timing oracle, access control hoặc incident response.
- Nếu muốn chứng minh hệ thống **không hề log secret**, cần test cả framework, filters, tracing, APM, logging encoder và exception handler.

## 11. Thiết kế một vòng đời bí mật trong ứng dụng Java

Hãy thiết kế “đường đi của secret” thay vì chỉ chọn một container.

### 11.1. Nhận secret

- Lấy secret qua TLS/authenticated channel hoặc KMS/Vault API đã xác thực, không qua query string/URL/cmdline có thể được ghi log.
- Nếu password được nhập bởi người dùng, ưu tiên API nhận `char[]` khi khả thi; với HTTP, kiểm soát logging/DTO và thời gian tồn tại.
- Nếu key ngẫu nhiên do ứng dụng tạo, dùng `SecureRandom` và key-size/scheme đúng chuẩn. Không dùng `java.util.Random` cho khóa.
- Đừng biến secret thành `String`, hex/Base64 hoặc JSON text chỉ để truyền giữa các lớp; nếu phải có text vì wire format, hạn chế các bản sao và tắt logging nhạy cảm.

### 11.2. Sử dụng secret

- Phân biệt key được quản lý dưới dạng byte và **key handle không exportable** từ HSM/KMS; ưu tiên hạn chế việc xuất raw key khi threat model yêu cầu.
- Chỉ cấp quyền truy cập key cho component thực sự cần.
- Tránh lưu key trong field static, cache tổng quát, `toString()` hay DTO có thể serialize.
- Với cryptography, dùng JCA/JCE provider hoặc thư viện thẩm định; không tự viết AES, HMAC hoặc thao tác padding.
- Bảo đảm authorization của hành động dùng key, không chỉ “key tồn tại trong RAM”.

### 11.3. Kết thúc thao tác

- Dùng `try/finally` để clear caller-owned mutable secret buffers kể cả khi lỗi.
- Gọi API cleanup/close/destroy phù hợp cho secret holder và key session nếu implementation hỗ trợ; kiểm tra behavior cụ thể.
- Đừng giữ reference lâu hơn cần thiết. Tránh capture secret trong lambda, callback, async continuation hoặc thread-local không được clear.
- Với borrowed buffers/shared arrays, không tự zero hóa dữ liệu đang còn được phía khác sử dụng; thiết kế ownership rõ ràng ngay từ đầu.
- Giữ lifecycle của plaintext output và decrypted records ngắn; quyền/retention của plaintext cũng quan trọng như key.

### 11.4. Vận hành

- Kiểm soát heap-dump/crash-dump locations và quyền debugger/profiler.
- Chặn logging/tracing secret ở tầng ingress và response filters.
- Đặt retention cho diagnostic artifacts và có thủ tục xóa/thu hồi/rotation khi leak.
- Định kỳ kiểm tra cấu hình APM, exception reporting, HTTP logging, access logs và secret redaction.
- Bảo vệ cache, backup và snapshot chứa memory artifact hoặc plaintext.
- Kiểm thử không làm rơi secret từ lỗi bất thường như timeout, retry, OOM hoặc provider error.

## 12. Ranh giới quan trọng: giữ secret trong tiến trình hay không đưa secret vào đó?

Đối với các hệ thống mức độ nhạy cảm cao, hướng giảm rủi ro tốt nhất đôi khi là **không đưa raw long-term key vào Java heap**.

### 12.1. Khóa trong phần mềm

Một Java service tải AES key từ secret manager, giữ dưới dạng `byte[]` rồi `SecretKeySpec` để gọi `Cipher`. Đây là mô hình phổ biến, nhưng service process sẽ có khả năng sử dụng key và đôi khi có material thô trong heap/provider memory. Nếu process bị xâm nhập đủ sâu, các biện pháp memory hygiene không thể bảo đảm key vẫn bí mật.

### 12.2. Khóa không xuất qua HSM/KMS

Một service có thể gọi `Encrypt/Decrypt/Sign/Verify` qua KMS/HSM hoặc dùng một provider với key handle, để long-term private/key bytes không được export vào Java heap. Lợi ích:
- Giảm số bản sao long-term key trong app memory.
- Dễ phân quyền theo key/purpose và audit hoạt động sử dụng key.
- Hỗ trợ rotation, disable/revoke và chính sách truy cập tập trung.

**Nhưng không có ma thuật:** dịch vụ vẫn có thể đọc plaintext đầu vào/đầu ra và có thể **lạm dụng quyền gọi KMS** nếu bị chiếm. Nếu KMS trả một data key plaintext để app mã hóa dữ liệu, **data key đó vẫn ở process memory**. Cần thiết kế key usage scope, IAM, rate limits, encryption context, audit, blast radius và phòng vệ ứng dụng.

### 12.3. Tránh tuyên bố “secret chưa từng ở RAM”

Một API có thể giấu secret phía client nhưng thư viện TLS, KMS SDK hoặc OS vẫn xử lý dữ liệu trong buffer. Chỉ đưa ra bảo đảm đúng theo threat model và tài liệu provider. “Non-exportable” áp dụng cho **key object/handle cụ thể** không đồng nghĩa mọi plaintext hay derived key của quy trình đều non-exportable.

## 13. Bảng đối chiếu các chiến thuật bảo vệ

| Chiến thuật | Giảm được gì? | Không bảo đảm được gì? |
| --- | --- | --- |
| Dùng `char[]`/`byte[]` thay `String` khi khả thi | Caller có thể ghi đè buffer đang sở hữu | Không xóa các bản sao trước/ngoài buffer |
| `Arrays.fill` trong `finally` | Xóa mảng cụ thể kể cả khi throw | Không xóa `SecretKeySpec`/provider/dump cũ |
| `PBEKeySpec.clearPassword()` | Xóa password copy của `PBEKeySpec` theo API | Không xóa password từng ở `String` hay native provider |
| Giảm lifetime/scope của key | Giảm cửa sổ và số component có quyền thấy secret | Không ngăn attacker với process privilege đủ lớn |
| Tránh hex/Base64/String của key | Giảm object, log và bản sao text | Không loại hết secret material nội bộ JCE |
| HSM/KMS non-exportable key | Giảm việc đưa long-term key bytes vào app | Không ngăn service bị lạm dụng để ký/giải mã |
| Disable/redact request logging | Giảm secret trong log/trace | Không tự kiểm soát heap dump/crash artifact |
| Access controls + mã hóa dump | Giảm người có thể đọc snapshot | Không biến snapshot thành dữ liệu công khai an toàn |
| Error codes ổn định | Giảm lộ secret/diagnostic qua public API | Không tự loại timing oracle/rate-limit issues |
| Security tests/log scanning | Phát hiện nhiều đường rò rỉ quan sát được | Không chứng minh tuyệt đối không có bản sao trong RAM |

### 13.1. Nên ưu tiên thế nào?

Nếu có một ngày để cải thiện hệ thống:
1. Ngừng log request headers/body chứa credential, ngừng log raw key/plaintext; rà soát APM/error reporting.
2. Bảo đảm credential, key và decrypted data chỉ được giữ trong scope cần thiết; thêm `finally` để clear mutable buffers.
3. Kiểm tra các API tạo bản sao (`String`, `PBEKeySpec`, `getEncoded()`, stream, serialization).
4. Kiểm soát quyền tạo/download heap dump và chính sách retention.
5. Tách public error khỏi internal diagnostic, giảm oracle trên endpoint nhạy cảm.
6. Đối với khóa lâu dài có tác động lớn, đánh giá non-exportable key/KMS/HSM và điều chỉnh blast radius.

Không nên đầu tư trước vào mẹo sửa nội bộ `String` bằng reflection, pin memory không được hỗ trợ hoặc “trộn byte cho khó đọc” trong khi vẫn công khai key qua logs và stack traces.

## 14. Tình huống thực tế

### Tình huống A — mật khẩu trên REST API

Endpoint nhận password để login. Framework bind JSON field thành `String`. Dev chuyển qua `char[]` và zero sau verify, nhưng middleware đang log request body.

**Kết luận:** mảng caller được xóa không ngăn password bị ghi ra log, cũng không xóa `String` từ parser. Khắc phục ở ingress/logging, hạn chế DTO retention, dùng password-hashing chuẩn, áp rate limit, xem xét API xử lý secret theo framework.

### Tình huống B — mã hóa tài liệu bằng AES-GCM

Service nhận CEK từ KMS, tạo `SecretKeySpec`, xóa byte[] CEK rồi lưu `SecretKeySpec` vào singleton “để tránh gọi KMS nhiều lần”. Trong thời gian đó heap dump được upload lên ticket.

**Kết luận:** CEK có thể còn trong key object và provider; xóa mảng gốc không đủ. Cần cân nhắc phạm vi/lifetime key, kiểm soát dump và cách dùng KMS/DEK; không đăng dump nhạy cảm ra hệ không tin cậy.

### Tình huống C — lỗi giải mã

Server giải mã AES-GCM thất bại và trả `"Tag failed for keyId X with input token Y"` cho client. Debugging trở nên dễ hơn nhưng error phản hồi lộ metadata hoặc secret và có thể phân biệt trạng thái giải mã.

**Kết luận:** public response dùng code tổng quát; internal telemetry ghi correlation ID và nhóm lỗi an toàn, không chèn secret/token. Cần xem xét oracle/timing và rate limit.

### Tình huống D — debug bằng `toString()`

Một class `ApiRequest` sinh `toString()` chứa `authorizationHeader`; khi exception xảy ra, logger ghi toàn request.

**Kết luận:** sửa DTO/toString, serializer, log filters và test bảo đảm bí mật không xuất hiện; tránh dựa riêng vào regex redaction cuối pipeline vì có thể bỏ sót encoding/format.

### Tình huống E — direct `ByteBuffer.clear()`

Một developer gọi `directBuffer.clear()` rồi tin rằng dữ liệu private key đã bị zero. Một native diagnostic vẫn đọc được buffer.

**Kết luận:** `clear()` không xóa nội dung, chỉ reset position/limit. Cần ghi đè vùng nhạy cảm theo API được hỗ trợ và hiểu những bản sao khác; direct memory không phải vault.

### Tình huống F — mật khẩu có trong stack trace

`InvalidCredentialsException("password="+p)` được ném ở tầng auth; global handler in `exception.printStackTrace()` rồi trả 500.

**Kết luận:** secret đã được nhúng trong message ngay khi tạo exception. Chỉ dùng mã lỗi an toàn, tránh secret trong exception object, kiểm soát logging/cause/response và áp chính sách auth thống nhất.

### Tình huống G — khóa private trong HSM

Java nhận handle `PrivateKey` non-exportable, ký qua provider. App không nhìn thấy raw private key, nhưng vẫn có quyền gửi dữ liệu tới ký.

**Kết luận:** giảm leak của private-key material, nhưng phải ràng buộc quyền ký, nội dung/format ký, rate, tenant, audit và phát hiện lạm dụng. Trộm quyền gọi ký cũng rất nguy hiểm dù không export được key.

## 15. Kiểm thử bảo mật và vận hành có thể chứng minh điều gì?

### 15.1. Unit tests

- Đặt dữ liệu secret giả/fixture, gọi code đúng/sai; assert secret không xuất hiện trong exception message và public response.
- Dùng log-capture test để khẳng định một số đường lỗi không log password/token/khóa giả.
- Kiểm tra cleanup trong `finally` khi API throw.
- Kiểm tra `PBEKeySpec.clearPassword()` sau derive và việc xóa derived buffer do caller sở hữu.
- Kiểm tra DTO `toString()` không in các field nhạy cảm.
- Kiểm tra các phương thức base64/hex serialization chỉ xử lý dữ liệu được phép công bố.

### 15.2. Integration/security tests

- Giả lập HTTP pipeline có proxy, access logging, exception handler, APM instrumentation; tìm marker giả trong log/trace/metrics.
- Gây lỗi mật mã có kiểm soát như sai tag và sai credential; đảm bảo public error không tiết lộ nguyên nhân nhạy cảm, internal logs không chứa secret.
- Kiểm thử quyền tạo/download heap dump theo vai trò vận hành.
- Kiểm tra dump staging bằng marker **không phải secret thật** nếu được phê duyệt; chú ý một snapshot không có marker **không chứng minh** không thể leak ở thời điểm khác.
- Kiểm tra cấu hình container/runtime, core dump/heap dump upload/retention trong CI và production.
- Kiểm tra rotation/revocation nếu nghi ngờ secret xuất hiện trong artifact không an toàn.

### 15.3. Security QA không thể kết luận tuyệt đối

Một unit test cho thấy `byte[]` bằng zero không chứng minh:
- Không có key trong JIT register/stack/native heap.
- Không có `String` được framework tạo trước đó.
- Không có APM hoặc crash dump giữ snapshot cũ.
- Không có secret đã bị log sang server trung tâm.
- Không có copy nằm trong backing buffer của thư viện.

Do đó, báo cáo nên dùng ngôn ngữ chính xác: **“caller-owned buffer cleared”, “public error sanitized”, “no secret found in tested log paths”** thay vì “all secrets permanently erased from RAM”.

## 16. Checklist review code cho một khóa hoặc mật khẩu

### Input / representation
- [ ] Secret đến từ nguồn/truyền tải đã xác thực, không hardcode trong source.
- [ ] Dùng container mutable theo API phù hợp khi cần zeroization; tránh tạo `String` không cần thiết.
- [ ] Không chuyển khóa sang hex/Base64/debug text trừ khi bắt buộc bởi wire format và được bảo vệ.
- [ ] Có giới hạn độ dài input và quy tắc parse để tránh tạo object/bản sao không kiểm soát.

### Usage / lifetime
- [ ] Lifetime của `byte[]`, `char[]`, `SecretKey`, `PBEKeySpec`, `Cipher/Mac` và provider session được ghi nhận.
- [ ] Cleanup nằm trong `finally` hoặc lifecycle đúng API; có xử lý đường exception.
- [ ] Nếu gọi `getEncoded()` hoặc tạo bản copy, caller quản lý/xóa output thích hợp.
- [ ] Key không bị giữ lâu trong static field, cache hoặc DTO mà không có lý do và chính sách.
- [ ] Có ràng buộc IAM/authorization cho việc dùng key và nguyên tắc least privilege.
- [ ] Nếu KMS/HSM, hiểu rõ dữ liệu nào vẫn được đưa vào Java heap và những key nào không exportable.

### Observability / errors
- [ ] Không log `Authorization`, `Cookie`, password, private key, CEK/DEK hoặc plaintext nhạy cảm.
- [ ] `toString()`, JSON serialization và exception message đã được rà soát.
- [ ] Public error dùng mã lỗi phù hợp, không echo secret/provider diagnostics.
- [ ] Trace/APM/metrics/coredump/heap dump không vô tình thu secret.
- [ ] Có rate limiting và đánh giá padding/tag/timing oracle trên API crypto công khai.

### Operations / incidents
- [ ] Quyền debugger/profiler/dump chỉ dành cho người/công cụ được cấp phép.
- [ ] Dump/log có encryption, access audit, storage policy và retention/xóa phù hợp.
- [ ] Có kịch bản rotate/revoke key/token nếu artifact nhạy cảm bị lộ.
- [ ] Incident response bao gồm log backups, object stores, tickets, telemetry vendors, máy developer và tài khoản hỗ trợ.
- [ ] Kiểm thử bằng fixture/canary giả, không tự ý đưa production secret vào test.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Gán `secretString = null` có bảo đảm mật khẩu đã bị xóa khỏi heap chưa?

**Đáp án:** Không. Chỉ bỏ tham chiếu; `String` bất biến, GC quản lý thu hồi và có thể có nhiều bản sao khác.

**Câu 2.** Nếu gọi `Arrays.fill(keyBytes, (byte)0)` ngay sau tạo `SecretKeySpec(keyBytes,"AES")`, khóa trong `SecretKeySpec` có chắc bị xóa không?

**Đáp án:** Không. `SecretKeySpec` giữ key material riêng theo API; khóa vẫn có thể dùng. Phải quản lý lifetime/key provider và không kết luận xóa mọi bản sao.

**Câu 3.** Sau khi gọi `spec.clearPassword()` trên `PBEKeySpec`, còn có thể gọi `spec.getPassword()` không?

**Đáp án:** Không theo hợp đồng API; việc truy cập bị từ chối. Điều đó không nói mọi bản sao password khác đều đã biến mất.

**Câu 4.** Vì sao dùng `ByteBuffer.clear()` không phải zeroization?

**Đáp án:** Nó chỉ thiết lập lại position/limit, không ghi zero vào vùng dữ liệu.

**Câu 5.** Heap dump được chụp trước khi ứng dụng zero byte[]; sau đó ứng dụng zero thành công. Dump có tự an toàn không?

**Đáp án:** Không. Snapshot cũ có thể giữ dữ liệu như lúc chụp. Phải bảo vệ artifact bằng access, encryption, retention và incident process.

**Câu 6.** Có nên trả `"AES tag invalid, unwrap RSA successful"` cho caller không xác thực?

**Đáp án:** Tránh làm vậy; thông điệp có thể tạo oracle và lộ trạng thái xử lý. Dùng public error được thiết kế theo threat model, internal diagnostic an toàn và rate limits.

**Câu 7.** Dùng HSM non-exportable private key có nghĩa plaintext không bao giờ đi qua Java heap?

**Đáp án:** Không. Nó giới hạn đường xuất private key cụ thể; plaintext/hashed input/output và các key khác vẫn có thể xuất hiện trong process tùy thiết kế.

**Câu 8.** Một unit test không tìm thấy key giả trong dump staging có chứng minh production không bao giờ leak secret?

**Đáp án:** Không. Dump chỉ là một snapshot; GC, thời điểm và data flow khác có thể làm thay đổi kết quả. Cần threat model, kiểm thử nhiều tầng và vận hành tốt.

**Câu 9.** Tại sao không nên đưa access token vào log, dù log chỉ được đọc bởi nhân viên nội bộ?

**Đáp án:** Token có thể là bearer credential. Log có nhiều bản sao, retention, tài khoản đọc và đường export; lộ log có thể tương đương lộ quyền truy cập token.

**Câu 10.** Lý do chính để dùng `finally` khi xử lý secret mutable?

**Đáp án:** Đảm bảo logic cleanup của caller chạy cả khi thao tác mật mã/parse/IO throw, giảm thời gian buffer do caller sở hữu chứa secret.

**Câu 11.** Secret đã được mã hóa ở database có thể xuất hiện dưới dạng plaintext trong memory dump không?

**Đáp án:** Có, nếu app giải mã để xử lý rồi giữ plaintext trong heap hoặc native buffer. Encryption at rest không đồng nghĩa mã hóa trong memory của ứng dụng đang sử dụng dữ liệu.

**Câu 12.** Câu nào chính xác hơn: “đã xóa hết password khỏi RAM” hay “đã zero caller char[] và clear PBEKeySpec theo API”?

**Đáp án:** Câu thứ hai kiểm chứng được và giới hạn rõ đối tượng; câu thứ nhất vượt quá bảo đảm của Java managed memory.

## 18. Sơ đồ ghi nhớ

```text
                 SECRET / PASSWORD / KEY
                           |
              +------------+-------------+
              |                          |
          Representations             Infrastructure
          byte[]  char[]              HTTP, logs, APM
          String  objects             heap, crash dumps
          KeySpec provider            debugger, backups
              |                          |
       Limit copies/lifetime         Access control
       Avoid String/logging          Encrypt / retain / audit
       finally + clear own arrays    Secure diagnostic path
              |                          |
              +------------+-------------+
                           |
                  NO ABSOLUTE ZEROIZATION
                  CLAIM FOR ENTIRE JVM
                           |
             Use purpose-bound keys, KMS/HSM
             where appropriate, safe errors,
             least privilege and incident response
```

**Kết luận:** Trong Java, `byte[]` và `char[]` cho phép giảm thời gian tồn tại của dữ liệu **ở đúng mảng mà ứng dụng sở hữu**; `String` không có API xóa nội dung trực tiếp; `SecretKeySpec`, `PBEKeySpec`, provider và các framework có thể tạo bản sao. Heap dump, profiler, log, trace và exception có thể đưa secret ra khỏi vòng đời dự kiến. Một hệ thống an toàn không tuyên bố “RAM đã sạch tuyệt đối”, mà thiết kế **giảm sao chép, giới hạn quyền và lifetime, có cleanup best-effort, bảo vệ diagnostic artifacts, trả lỗi không lộ secret và lập kế hoạch thay khóa khi sự cố xảy ra**.

### Tài liệu tham khảo

- Java SE 17, `String`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html
- Java SE 17, `Arrays.fill`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html
- Java SE 17, `PBEKeySpec` và `clearPassword()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html
- Java SE 17, `SecretKeySpec`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/SecretKeySpec.html
- Java SE 17, `SecretKeyFactory`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/SecretKeyFactory.html
- Java SE 17, `ByteBuffer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 17, `Console.readPassword()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Console.html
- Java SE 17, `Destroyable`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/security/auth/Destroyable.html
- OWASP, *Logging Cheat Sheet*: https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html
- OWASP, *Secrets Management Cheat Sheet*: https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html
- OWASP, *Error Handling Cheat Sheet*: https://cheatsheetseries.owasp.org/cheatsheets/Error_Handling_Cheat_Sheet.html


</details>

- [Quay lại đầu trang](#back-to-top)
