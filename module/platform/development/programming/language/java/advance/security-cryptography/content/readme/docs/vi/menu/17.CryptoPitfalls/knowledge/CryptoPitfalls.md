<a id="back-to-top"></a>

# Thiết kế mật mã an toàn và các lỗi thường gặp

## Menu
- [Bắt đầu từ mục tiêu bảo mật, không bắt đầu từ API](#security-goal-before-api)
- [Lựa chọn thuật toán và Provider có tính portable](#algorithm-provider-portability)
- [Quản lý secret, key, IV và nonce](#secret-key-nonce-handling)
- [Xử lý lỗi mà không phá bảo đảm bảo mật](#crypto-failure-handling)
- [Kiểm tra thiết kế mật mã từ đầu đến cuối](#end-to-end-crypto-review)

## <a id="security-goal-before-api">Bắt đầu từ mục tiêu bảo mật, không bắt đầu từ API</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Anti-pattern phổ biến là bắt đầu bằng “dùng AES hay RSA?” trước khi biết cần bảo vệ điều gì.

Một luồng kiểm tra tốt hơn:

~~~text
tài sản cần bảo vệ + khả năng của kẻ tấn công
        ↓
mục tiêu bảo mật
        ↓
trust/key model
        ↓
standard protocol/primitive
        ↓
Java API + provider
        ↓
tham số + vòng đời + chính sách xử lý lỗi
~~~

Ví dụ nếu chỉ cần fingerprint để cache/deduplicate, encryption là sai abstraction. Nếu cần payload vừa bí mật vừa chống chỉnh sửa, digest + encryption rời rạc có thể kém rõ ràng hơn AEAD.

Lựa chọn API là kết quả của thiết kế, không phải điểm bắt đầu.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="algorithm-provider-portability">Lựa chọn thuật toán và Provider có tính portable</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Algorithm/provider selection cần cân bằng chính sách bảo mật và portability.

Nên:

- dùng standard algorithm names khi có thể;
- tránh hard-code provider nếu không có requirement;
- validate required algorithms lúc bootstrap;
- version protocol/data format để future migration có chỗ biểu diễn algorithm/parameter change;
- để JDK constraints chặn lựa chọn yếu thay vì silently downgrade.

Không nên lưu ciphertext mà không lưu đủ metadata để biết transformation/version/nonce/KDF parameters cần thiết cho decrypt sau này.

“Works on my JDK” chưa chứng minh portability. Tập Provider và chính sách có thể khác giữa OS, JDK vendor, triển khai HSM và future JDK.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="secret-key-nonce-handling">Quản lý secret, key, IV và nonce</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Nhiều lỗi bảo mật không đến từ primitive mà từ **vòng đời của vật liệu bảo mật**:

- secret hard-code trong source/config public;
- key được log hoặc serialize;
- password nằm trong immutable String quá lâu;
- nonce reuse dưới cùng AEAD key;
- fixed salt cho mọi credential;
- một key dùng lại cho nhiều mục đích mà protocol không cho phép.

Hãy phân loại dữ liệu:

~~~text
secret: private key, symmetric key, password
non-secret nhưng security-relevant: certificate, salt, IV/nonce
public protocol metadata: algorithm/version identifiers
~~~

“Public” không nghĩa “không quan trọng”: nonce/salt vẫn phải đúng uniqueness/randomness contract dù không cần confidentiality.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="crypto-failure-handling">Xử lý lỗi mà không phá bảo đảm bảo mật</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Lỗi bảo mật không nên được biến thành fallback làm yếu bảo đảm.

Ví dụ cần reject khi:

- AEAD tag fail;
- signature/MAC verify false;
- certificate path hoặc endpoint identity fail;
- algorithm/key bị security constraint chặn;
- required provider/service không tồn tại.

Không retry bằng “NoPadding”, trust-all manager, disabled hostname verification hoặc legacy protocol chỉ để phép toán thành công.

Logging cũng cần thận trọng: log context, algorithm, alias, certificate subject/fingerprint khi phù hợp; không log plaintext secret, raw key, password hoặc full token.

Fail closed không có nghĩa crash vô tổ chức. Hãy chuyển lỗi thành domain/security error rõ ràng và quan sát được.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="end-to-end-crypto-review">Kiểm tra thiết kế mật mã từ đầu đến cuối</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Một lần review end-to-end nên đi qua toàn bộ vòng đời thay vì chỉ nhìn một dòng `Cipher.getInstance`:

1. **asset/threat** — đang chống đọc, sửa, giả mạo hay replay?
2. **primitive/protocol** — có standard mechanism phù hợp không?
3. **key origin** — generate, derive, agreement, KEM hay KeyStore?
4. **parameters** — nonce, salt, work factor, transformation, protocol version?
5. **trust** — public key/certificate được xác thực ra sao?
6. **storage/transport** — metadata nào đi cùng ciphertext/signature?
7. **lỗi** — verify/tag/trust lỗi được xử lý thế nào?
8. **rotation/migration** — algorithm/key/version thay đổi sau này bằng cách nào?

Nếu một câu không trả lời được, đó thường là gap thực sự dù code compile và happy-path test đều pass.

Mục tiêu cuối module không phải thuộc mọi class, mà là nhìn được chuỗi reasoning từ mục tiêu bảo mật đến Java mechanism và ranh giới vận hành thực tế.
</details>

- [Quay lại đầu trang](#back-to-top)
