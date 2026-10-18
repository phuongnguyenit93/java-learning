<a id="back-to-top"></a>

# Encoding, băm và mã hóa khác nhau thế nào?

## Menu
- [Encoding dùng để làm gì?](#encoding-purpose)
- [Hàm băm dùng để làm gì?](#hashing-purpose)
- [Mã hóa dùng để làm gì?](#encryption-purpose)
- [MAC và chữ ký số bổ sung điều gì?](#authentication-purpose)

## <a id="encoding-purpose">Encoding dùng để làm gì?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

**Encoding** đổi cách biểu diễn dữ liệu để phù hợp với format hoặc transport, không tạo ranh giới bí mật. Base64 là ví dụ quen thuộc: binary bytes được biểu diễn thành text an toàn cho nhiều kênh truyền, nhưng bất kỳ ai cũng có thể decode.

~~~java
String encoded = Base64.getEncoder()
        .encodeToString("hello".getBytes(StandardCharsets.UTF_8));

byte[] original = Base64.getDecoder().decode(encoded);
~~~

Nếu một token chỉ được Base64-encode, token vẫn là secret ở dạng có thể đảo ngược mà không cần key. Vì vậy “không đọc được bằng mắt” không đồng nghĩa với encryption.

Encoding vẫn quan trọng trong hệ thống mật mã vì ciphertext, digest, signature hoặc certificate thường cần được biểu diễn dưới dạng text. Nó là **lớp biểu diễn**, không phải primitive bảo mật.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hashing-purpose">Hàm băm dùng để làm gì?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Hàm băm mật mã nhận dữ liệu đầu vào có độ dài bất kỳ và tạo digest có kích thước cố định. Ý tưởng sử dụng chính là: cùng dữ liệu đầu vào tạo cùng digest, trong khi việc tìm dữ liệu khác cho cùng digest hoặc đảo ngược digest phải khó theo các đặc tính bảo mật của thuật toán.

Hash phù hợp để tạo fingerprint, kiểm tra file với giá trị tham chiếu đáng tin cậy hoặc làm thành phần bên trong nhiều primitive khác. Hash **không dùng key**, nên nếu kẻ tấn công có thể sửa cả dữ liệu lẫn digest thì họ cũng có thể hash lại dữ liệu mới.

Do đó hash thuần không phải cơ chế authentication. Password cũng không nên chỉ được hash một lần bằng MessageDigest; password storage cần cơ chế dẫn xuất khóa/password hashing có salt và work factor phù hợp, được học ở chapter Key Derivation.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="encryption-purpose">Mã hóa dùng để làm gì?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Encryption giải quyết confidentiality bằng cách biến plaintext thành ciphertext dưới sự kiểm soát của key. Bên có key phù hợp có thể decrypt để khôi phục plaintext.

Điểm khác căn bản so với hashing là **encryption được thiết kế để đảo ngược có kiểm soát**. Hashing không có phép giải mã. Encoding thì đảo ngược được nhưng không cần secret.

~~~text
plaintext + key + parameters
        ↓ encrypt
ciphertext
        ↓ decrypt với key/parameters phù hợp
plaintext
~~~

Tuy nhiên confidentiality không tự kéo theo integrity. Nếu ứng dụng cần cả hai, authenticated encryption như GCM thường là mô hình phù hợp hơn việc tự ghép encryption và integrity primitives theo cảm tính.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="authentication-purpose">MAC và chữ ký số bổ sung điều gì?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

MAC và digital signature cùng trả lời câu hỏi “dữ liệu này có bị sửa và có gắn với key holder dự kiến không?”, nhưng trust model khác nhau.

**MAC** dùng một secret key mà các bên cùng biết. Bất kỳ bên nào giữ secret đều có thể tạo MAC hợp lệ, nên MAC phù hợp cho trust domain chia sẻ secret.

**Digital signature** dùng private key để ký và public key để verify. Bên verify không cần biết private key; vì vậy mô hình này phù hợp khi nhiều verifier cần kiểm tra dữ liệu mà không được quyền ký.

Không cơ chế nào trong hai cơ chế trên mặc định che nội dung. Nếu thông điệp vừa cần bí mật vừa cần xác thực, hãy dùng một protocol/primitive kết hợp các bảo đảm đúng cách, chẳng hạn AEAD hoặc TLS, thay vì giả định signature hay MAC là encryption.
Từ đây, bước tiếp theo là hiểu Java biến các tên primitive/thuật toán đó thành implementation thực tế như thế nào qua JCA/JCE và Provider.

</details>

- [Quay lại đầu trang](#back-to-top)
