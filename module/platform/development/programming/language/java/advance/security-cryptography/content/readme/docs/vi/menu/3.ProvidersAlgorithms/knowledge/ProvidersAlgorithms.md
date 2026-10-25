<a id="back-to-top"></a>

# JCA/JCE, thuật toán và Security Provider

## Menu
- [Kiến trúc JCA/JCE](#jca-jce-architecture)
- [Engine class, SPI và Provider](#engine-spi-provider)
- [Tên thuật toán và transformation chuẩn](#algorithm-names-transformations)
- [Cách Java lựa chọn Provider](#provider-selection)
- [Security properties và ràng buộc thuật toán](#security-properties-constraints)

## <a id="jca-jce-architecture">Kiến trúc JCA/JCE</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Java Cryptography Architecture (JCA) cung cấp một lớp API chung cho các dịch vụ bảo mật và mật mã. Ứng dụng thường làm việc với các **engine class** như MessageDigest, Signature, SecureRandom, KeyStore; JCE mở rộng cùng mô hình cho Cipher, Mac, KeyAgreement, KeyGenerator, SecretKeyFactory và các dịch vụ crypto khác.

Thay vì ứng dụng tự `new` một cách triển khai cụ thể, mã thường yêu cầu một dịch vụ bằng tên thuật toán:

~~~java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
~~~

JCA sau đó tìm cách triển khai qua các security `Provider` đã đăng ký. Nhờ vậy mã ứng dụng có thể phụ thuộc vào **hợp đồng dịch vụ + tên thuật toán** thay vì phụ thuộc trực tiếp vào một cách triển khai của nhà cung cấp cụ thể.

Đây là lý do provider model thuộc module này: nó quyết định Java biến một yêu cầu mật mã trừu tượng thành phần triển khai thực tế lúc chạy.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="engine-spi-provider">Engine class, SPI và Provider</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Ba lớp khái niệm cần tách rõ:

- **engine class** là API mà ứng dụng gọi, ví dụ Signature hoặc Cipher;
- **SPI** là hợp đồng dành cho cách triển khai của provider, ví dụ SignatureSpi hoặc CipherSpi;
- **Provider** đăng ký các cách triển khai dịch vụ mà runtime có thể lựa chọn.

~~~text
ứng dụng
    ↓
engine class
    ↓
JCA service lookup
    ↓
Provider
    ↓
phần triển khai SPI
~~~

Ứng dụng thông thường không tự triển khai SPI. Việc viết custom provider là một bài toán extensibility chuyên biệt và chỉ cần khi tích hợp cách triển khai, phần cứng hoặc dịch vụ đặc thù.

Tách engine khỏi SPI giúp mã ứng dụng giữ ổn định trong khi provider có thể thay đổi theo JDK, hệ điều hành hoặc chính sách triển khai.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="algorithm-names-transformations">Tên thuật toán và transformation chuẩn</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Các engine dùng **standard algorithm names** để mô tả service cần tìm. MessageDigest có thể dùng SHA-256; Mac có HmacSHA256; KeyStore có PKCS12.

Với Cipher, tên thường là một **transformation** gồm nhiều phần:

~~~text
algorithm / mode / padding
~~~

Ví dụ AES/GCM/NoPadding không chỉ nói “AES”. Nó còn khóa mode và padding semantics, từ đó kéo theo requirement về nonce/IV và authentication tag.

Không nên dựa vào chuỗi mơ hồ nếu **hành vi bảo mật** phụ thuộc mode/padding. Viết rõ transformation giúp quá trình review nhìn thấy hợp đồng mật mã và tránh phụ thuộc vào provider default không hiển nhiên.

Tên hợp lệ và mức bắt buộc hỗ trợ phải được kiểm tra theo Java Security Standard Algorithm Names và provider đang chạy, không suy từ ví dụ trên Internet.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-selection">Cách Java lựa chọn Provider</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Nếu gọi `getInstance` chỉ với tên thuật toán, JCA xét các `Provider` theo thứ tự ưu tiên. Đây thường là lựa chọn tốt vì ứng dụng giữ được tính portable:

~~~java
Signature signature = Signature.getInstance("SHA256withRSA");
~~~

Có overload cho phép chỉ định provider name hoặc `Provider` object. Chỉ nên khóa provider khi có **lý do kỹ thuật hoặc compliance rõ ràng**, chẳng hạn cần PKCS#11/HSM cụ thể hoặc tính năng chỉ provider đó cung cấp.

Một điểm quan trọng trong JDK là **delayed provider selection**. Với `Cipher`, `KeyAgreement`, `Mac` và `Signature`, việc gọi `getInstance(...)` không nhất thiết đã chốt provider cuối cùng. JDK có thể đợi đến lúc `init(...)` nhận `Key` để chọn provider có thể xử lý key đó, đặc biệt hữu ích với key không thể export từ token/HSM.

~~~java
Signature signature = Signature.getInstance("SHA256withRSA");
signature.initSign(privateKey); // provider thực tế có thể được chốt tại đây
Provider selected = signature.getProvider();
~~~

Sau khi provider đã được chọn cho instance đó, không nên giả định lần `init(...)` sau sẽ tự chuyển sang provider khác. Nếu cần chọn lại dựa trên một key khác, tạo engine instance mới rồi init lại với key mới.

Hard-code provider không cần thiết làm cách triển khai kém linh hoạt: cùng mã có thể chạy trên JDK/OS khác nhưng provider name hoặc tập service khác nhau.

Khi hành vi cần được kiểm soát, hãy kiểm tra algorithm availability lúc bootstrap và fail rõ ràng, thay vì âm thầm fallback sang một primitive khác.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-properties-constraints">Security properties và ràng buộc thuật toán</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Việc API tồn tại không có nghĩa mọi algorithm/key size/protocol đều được phép. JDK có security properties và algorithm constraints có thể vô hiệu hóa hoặc hạn chế các lựa chọn đã yếu hoặc không phù hợp chính sách.

Ví dụ các chính sách liên quan TLS và certificate path có thể chặn thuật toán/key size ngay trong handshake hoặc validation. Provider cũng có thể chỉ hỗ trợ một tập algorithm nhất định.

Ứng dụng nên coi lỗi do chính sách là một **security signal**, không phải lý do để tự động hạ cấp xuống thuật toán yếu hơn.

Thực tế vận hành cần phân biệt:

~~~text
API có class
≠
provider có phần triển khai
≠
chính sách cho phép sử dụng
~~~

Ba lớp này giải thích nhiều lỗi kiểu `NoSuchAlgorithmException`, `InvalidKeyException` hoặc lỗi handshake/validation mà chỉ đọc Java type system không thấy được.
Sau khi biết Java tìm implementation mật mã ra sao, cần đi xuống vật liệu đầu vào đầu tiên của nhiều phép toán: nguồn ngẫu nhiên khó dự đoán.

</details>

- [Quay lại đầu trang](#back-to-top)
