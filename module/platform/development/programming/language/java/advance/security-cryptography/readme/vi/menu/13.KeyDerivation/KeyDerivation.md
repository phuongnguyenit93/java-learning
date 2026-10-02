<a id="back-to-top"></a>

# Dẫn xuất khóa

## Menu
- [Dẫn xuất khóa giải quyết bài toán gì?](#key-derivation-purpose)
- [Mật khẩu như vật liệu đầu vào](#password-key-material)
- [SecretKeyFactory và PBEKeySpec trong dẫn xuất khóa](#password-kdf-java-api)
- [Salt, work factor và chi phí dẫn xuất](#salt-and-work-factor)
- [Sử dụng khóa đã dẫn xuất đúng mục đích](#derived-key-usage)

## <a id="key-derivation-purpose">Dẫn xuất khóa giải quyết bài toán gì?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

**Dẫn xuất khóa (key derivation)** biến vật liệu đầu vào thành vật liệu khóa có đặc tính phù hợp cho một mục đích cụ thể.

Đầu vào có thể là password hoặc secret material từ protocol. Chapter này tập trung vào password-based derivation vì đó là phạm vi đã chốt trong Curriculum.

Password thường có entropy thấp và phân bố không đều. Dùng trực tiếp password bytes làm AES/HMAC key khiến kẻ tấn công thử các phương án password với chi phí quá thấp.

Cơ chế KDF/PBE thêm salt và chi phí tính toán để mỗi lần thử password trở nên đắt hơn, đồng thời hạn chế việc tái sử dụng bảng precomputation giữa nhiều record.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="password-key-material">Mật khẩu như vật liệu đầu vào</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Password là **đầu vào do con người chọn**, không phải cryptographic key đã sẵn sàng.

Java `PBEKeySpec` dùng `char[]` thay vì `String` để ứng dụng có cơ hội ghi đè password sau khi dùng. Constructor tạo bản sao nội bộ của password; `clearPassword()` xóa bản sao đó, còn mảng gốc vẫn do bên gọi quản lý.

~~~java
char[] password = ...;
PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
try {
    // derive key
} finally {
    spec.clearPassword();
    Arrays.fill(password, '\0');
}
~~~

Việc xóa dữ liệu nhạy cảm là thói quen tốt nhưng không bảo đảm rằng chưa từng có bản sao nào tồn tại trong stack, provider hoặc runtime. Mục tiêu là giảm thời gian dữ liệu tồn tại không cần thiết, không hứa “xóa tuyệt đối khỏi memory”.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="password-kdf-java-api">SecretKeyFactory và PBEKeySpec trong dẫn xuất khóa</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Trong JCA/JCE, password-based derivation thường đi qua **SecretKeyFactory + PBEKeySpec**:

~~~java
SecretKeyFactory factory =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

PBEKeySpec spec =
        new PBEKeySpec(password, salt, iterations, 256);

byte[] derived;
try {
    derived = factory.generateSecret(spec).getEncoded();
} finally {
    spec.clearPassword();
}
~~~

Tên thuật toán trong ví dụ là một lựa chọn provider phổ biến, không phải lời khuyên cố định cho mọi chính sách. Hệ thống phải chọn KDF và parameters theo threat model, yêu cầu compliance và mục tiêu hiệu năng hiện tại.

Nếu derived bytes được dùng làm AES key, có thể cần chuyển chúng thành cách biểu diễn khóa phù hợp. Cần ghi rõ encoding, độ dài và mục đích để các bên không dẫn xuất ra vật liệu khác nhau.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="salt-and-work-factor">Salt, work factor và chi phí dẫn xuất</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

**Salt** không cần bí mật. Nó cần đủ tính unique/ngẫu nhiên để cùng một password ở hai record không tạo cùng kết quả dẫn xuất và để phá precomputed lookup dùng chung.

**Work factor** làm mỗi lần thử password đắt hơn. Với PBKDF2, iteration count là một phần của parameter set. Giá trị “an toàn” không phải hằng số vĩnh viễn; phần cứng và chính sách thay đổi, nên hệ thống thực tế cần hiệu chỉnh và version hóa parameters.

Một record thường phải lưu cùng:

~~~text
KDF id/version
salt
work factor / parameters
derived verifier hoặc encrypted payload
~~~

Không tái sử dụng một fixed salt toàn cục chỉ vì dễ cấu hình. Không tăng work factor đến mức service tự gây DoS; tham số bảo mật cũng là bài toán hoạch định năng lực.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="derived-key-usage">Sử dụng khóa đã dẫn xuất đúng mục đích</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Khóa đã dẫn xuất nên có **mục đích rõ ràng**. Dùng cùng key bytes cho nhiều primitive/context có thể tạo liên kết không mong muốn giữa các giả định của protocol.

Nếu protocol cần nhiều key, nên dùng thiết kế derivation/key separation mà specification đã xác định thay vì tự cắt hoặc hash bytes tùy ý.

Salt và KDF parameters phải đi cùng dữ liệu để mã trong tương lai biết cách dẫn xuất lại. Password thì không được lưu cạnh ciphertext dưới dạng plaintext.

Cuối cùng, password-based derivation không biến password yếu thành secret có entropy cao. Nó chỉ làm việc đoán offline trở nên tốn kém hơn. Chất lượng password, rate limiting, MFA và chính sách thông tin xác thực nằm ngoài primitive này nhưng vẫn ảnh hưởng bảo mật toàn hệ thống.
Key được generate hoặc derive cuối cùng vẫn cần được nạp, lưu và bảo vệ theo vòng đời; đó là vai trò của KeyStore.

</details>

- [Quay lại đầu trang](#back-to-top)
