<a id="back-to-top"></a>

# 10. Thỏa thuận và bọc khóa

## Menu
- [10.1 Phân biệt trao đổi khóa, thỏa thuận khóa, bọc khóa và đóng gói khóa](#key-establishment-and-wrapping-001)
- [10.2 DH, ECDH, X25519 và X448 trong mô hình KeyAgreement](#key-establishment-and-wrapping-002)
- [10.3 Chu trình init, doPhase, generateSecret và kiểm tra khóa của đối tác](#key-establishment-and-wrapping-003)
- [10.4 Bí mật chung, bước dẫn xuất khóa, ngữ cảnh sử dụng và tách khóa theo mục đích](#key-establishment-and-wrapping-004)
- [10.5 Xác thực đối tác để tránh đánh tráo khóa trong quá trình thỏa thuận](#key-establishment-and-wrapping-005)
- [10.6 Cipher.WRAP_MODE, UNWRAP_MODE và thuật toán bọc khóa](#key-establishment-and-wrapping-006)
- [10.7 API KEM từ Java 21 với Encapsulator, Decapsulator và thông điệp đóng gói](#key-establishment-and-wrapping-007)
- [10.8 Thực nghiệm DHKEM và phân biệt khóa sinh ra với dữ liệu encapsulation](#key-establishment-and-wrapping-008)
- [10.9 Quan hệ giữa cơ chế nguyên thủy và một giao thức hoàn chỉnh có xác thực](#key-establishment-and-wrapping-009)

## <a id="key-establishment-and-wrapping-001">10.1 Phân biệt trao đổi khóa, thỏa thuận khóa, bọc khóa và đóng gói khóa</a>

<details>
<summary>Xem chi tiết</summary>

# 10.1 Phân biệt trao đổi khóa, thỏa thuận khóa, bọc khóa và đóng gói khóa

## Mục tiêu

Khi xây dựng một ứng dụng Java dùng AES-GCM, TLS, kho khóa hoặc mã hóa tài liệu cho người nhận, bạn cần biết **bí mật được tạo ra hay đã tồn tại**, **bên nào sở hữu khóa**, **thứ gì được truyền qua mạng** và **API nào giải quyết đúng bài toán**. Bốn thuật ngữ dễ lẫn là *trao đổi khóa* (key exchange), *thỏa thuận khóa* (key agreement), *bọc khóa* (key wrapping) và *đóng gói khóa* (key encapsulation, KEM).

Bài này là **bản đồ khái niệm và lựa chọn cơ chế**: học cách nhận diện yêu cầu trước khi triển khai, hiểu đường đi của khóa và những bảo đảm còn thiếu. Các thao tác chuyên sâu được trình bày trong các bài riêng về DH/ECDH/X25519/X448, vòng đời `KeyAgreement`, HKDF, xác thực đối tác, `Cipher.WRAP_MODE`, API KEM Java 21 và thiết kế giao thức.

Sau khi đọc, bạn cần phân loại được một hệ thống theo **key có sẵn hay key mới**, **khóa đối xứng hay bất đối xứng**, **người gửi/nhận biết gì**, và **xác thực danh tính nằm ở bước nào**.

## 1. Bốn khái niệm không đồng nghĩa

| Thuật ngữ | Câu hỏi mà cơ chế giải quyết | Vật liệu bí mật | Đầu ra nổi bật | Java API điển hình |
| --- | --- | --- | --- | --- |
| **Key exchange — trao đổi khóa** | Làm thế nào hai đầu thiết lập được khóa để giao tiếp? | Tùy giao thức; có thể phối hợp nhiều primitive | Các khóa phiên và bằng chứng theo giao thức | Một chuỗi `KeyAgreement`, `KEM`, KDF, chữ ký/MAC, AEAD |
| **Key agreement — thỏa thuận khóa** | Hai bên có thể **tự tính chung một secret** từ private key của mình và public key đối tác không? | Mỗi bên có private key riêng; không gửi secret | **Raw shared secret** (thường đưa qua KDF) | `javax.crypto.KeyAgreement` |
| **Key wrapping — bọc khóa** | Làm thế nào bảo vệ **một khóa đã tồn tại** để lưu/chuyển nó? | Data Encryption Key (DEK) đã có; Key Encryption Key (KEK) hoặc khóa nhận được phép | **Wrapped key bytes**, mở ra lại key cũ | `Cipher.WRAP_MODE`, `Cipher.UNWRAP_MODE`, AESWrap/KMS |
| **Key encapsulation — đóng gói khóa/KEM** | Làm sao sender dùng public key recipient tạo **shared secret mới** để recipient khôi phục? | Recipient giữ private key; sender tạo secret mới nội bộ | **Secret + encapsulation bytes** | `javax.crypto.KEM` (**Java 21+**) |

**Key exchange** thường là tên của **mục tiêu hoặc một giao thức đầy đủ**. Nó có thể sử dụng key agreement, KEM, vận chuyển/bọc khóa hoặc nhiều thành phần cùng lúc. Không nên dịch “key exchange” thành tên riêng duy nhất của Diffie–Hellman.

**Key agreement** dùng đóng góp của *hai phía*, thí dụ X25519/ECDH. **Key wrapping** xuất phát từ *một DEK đã có* và bảo vệ nó dưới KEK. **KEM encapsulation** tạo **secret mới** và **thông điệp đóng gói** cho public key của người nhận; nó không có phương thức mặc định để nhận một DEK tùy ý làm input. Đây là điểm phân biệt quan trọng nhất khi đọc API Java.

## 2. Gọi đúng tên các loại khóa

- **DEK (Data Encryption Key):** khóa dùng để mã hóa **nội dung** của file hoặc bản ghi, ví dụ AES-256 cho AES-GCM.
- **KEK (Key Encryption Key):** khóa dùng để **wrap/unwrap DEK**; thường được quản lý bởi Key Management Service (KMS), Hardware Security Module (HSM) hoặc một hệ thống key hierarchy.
- **Public/private key pair:** cặp khóa bất đối xứng dùng cho DH, KEM, RSA transport hoặc chữ ký, tùy thuật toán. Private key phải được bảo vệ; public key **có thể công khai nhưng phải xác thực đúng chủ sở hữu** trước khi tin.
- **Raw shared secret:** kết quả của DH hoặc bí mật do KEM khôi phục; đây là **vật liệu dẫn xuất khóa**, chưa mặc nhiên là AES key cho một phiên.
- **Session/traffic key:** khóa đã được dẫn xuất cho một thuật toán, chiều truyền, phiên bản và ngữ cảnh cụ thể; ví dụ client→server AES-GCM key.

Một secret dài 32 byte **không mặc nhiên** là AES-256 key phù hợp về mặt giao thức. Cần thiết kế **KDF** như HKDF với context/labels, đồng thời xác minh nguồn key và bảo vệ việc tái sử dụng khóa/nonce. Đừng lấy raw secret hoặc một wrapped blob rồi trực tiếp biến nó thành dữ liệu gửi cho client nếu chưa hiểu tính chất của cơ chế.

## 3. Key agreement: hai bên tự tính cùng bí mật

Mô hình X25519 hai bên:

~~~text
Alice                            Mạng                             Bob
private a, public A                                               private b, public B

        A ----------------------->                                  |
        | <------------------------ B                               |
        |                                                           |
Z_A = X25519(a, B)                                     Z_B = X25519(b, A)
                  Z_A == Z_B, nếu đúng khóa và tham số
        |                                                           |
    HKDF(context)                                             HKDF(context)
        |                                                           |
          traffic key giống nhau theo từng vai trò/nhãn
~~~

Alice và Bob **không truyền Z** qua mạng. Mỗi phía chỉ gửi public key theo định dạng quy định, rồi dùng private key **của chính mình** kết hợp với public key đối tác.

Java dùng state machine:

~~~java
// Snippet trong phương thức có PrivateKey myPrivate,
// PublicKey peerPublic và xử lý GeneralSecurityException.
KeyAgreement agreement = KeyAgreement.getInstance("X25519");
agreement.init(myPrivate);
agreement.doPhase(peerPublic, true);
byte[] rawSecret = agreement.generateSecret();
~~~

- Với hai bên, `doPhase(peerPublic, true)` đánh dấu pha cuối.
- `generateSecret()` trả **secret bytes**, không tự mã hóa payload, không chứng minh peer là ai và không áp KDF theo mục đích.
- DH hữu hạn cần nhóm `p, g` tin cậy và tương thích; ECDH P-256 cần đúng curve; X25519/X448 là nhóm thuật toán/format khác, không hoán đổi bừa bãi.
- Phải kiểm tra key encoding, type/curve/group, public input theo provider/chuẩn, và **xác thực peer identity** từ trust anchor/pinning/chữ ký/giao thức.

Nếu một người ở giữa tráo public key, cả hai phía vẫn có thể tính secret **với attacker** mà API không báo lỗi. X25519 giải bài toán tính toán bí mật, **không tự giải bài toán tin cậy danh tính**.

## 4. Key transport và key wrapping: DEK được chọn trước

Giả sử một file đã được mã hóa bằng DEK. Ứng dụng cần lưu file lâu dài nhưng không thể lưu DEK dạng thô cùng ciphertext:

~~~text
DEK được sinh ngẫu nhiên
     |                    +---------------------------+
     +--> AES-GCM(file) -> | dataCiphertext + tag      |
     |                    +---------------------------+
     |
     +--> AES-KW(KEK, DEK) --> wrappedDEK
                               |
                        lưu kèm keyId, phiên bản
                               |
                được cấp quyền --> unwrap bằng KEK --> DEK cũ
~~~

Đây là **envelope encryption**. `AESWrap` (AES Key Wrap, RFC 3394) bảo vệ **key bytes**; nó không có AAD cho metadata như tenant, record ID hoặc thời hạn. Trường hợp đầu vào dài không phù hợp AES-KW có thể cần AESWrapPad (AES-KWP) nếu provider hỗ trợ. Các field metadata phải được **ràng buộc/kiểm tra ở lớp record/giao thức**.

Trong Java, `Cipher.WRAP_MODE` và `UNWRAP_MODE` là **chế độ hoạt động**, chưa phải tên thuật toán:

~~~java
// Snippet với SecretKey kek và SecretKey dek có sẵn:
Cipher wrap = Cipher.getInstance("AESWrap");
wrap.init(Cipher.WRAP_MODE, kek);
byte[] wrappedDek = wrap.wrap(dek);

Cipher unwrap = Cipher.getInstance("AESWrap");
unwrap.init(Cipher.UNWRAP_MODE, kek);
Key originalDek = unwrap.unwrap(
        wrappedDek, "AES", Cipher.SECRET_KEY);
~~~

Ở lệnh `unwrap(...)`, `"AES"` là **thuật toán của khóa được khôi phục**, khác với thuật toán bọc `"AESWrap"`. Với software key, `getEncoded()` có thể trả raw bytes; với HSM/KMS, key có thể **non-extractable** và phải thao tác qua provider/key handle. Đừng cố export KEK chỉ để dùng đoạn demo.

**Key transport** chỉ trường hợp bên gửi **chọn một DEK trước** rồi chuyển nó cho người nhận đã xác thực, ví dụ mã hóa DEK bằng public key RSA của người nhận theo **RSA-OAEP**. Bên nhận dùng private key tương ứng để khôi phục. Đây cũng là một cách *bảo vệ/vận chuyển khóa đã có*, nhưng khác key agreement và KEM: sender chọn key trước và không cần một phép đóng góp DH tạm từ cả hai bên.

**RSA-OAEP và AESWrap bảo vệ khóa, không tự chứng minh người gửi là ai.** Người biết RSA public key đều có thể mã hóa một DEK cho người nhận. Với lưu trữ, phải phân quyền unwrap, quản lý KEK, rotation/rewrap và chống tráo key giữa object/tenant. Với truyền nhận, phải xác thực **đúng public key recipient**.

## 5. KEM: secret mới, thông điệp encapsulation công khai

KEM dùng public key người nhận để tạo **hai kết quả**:

~~~text
Recipient Bob: (pkR, skR)
                       |
Sender Alice: Encap(pkR)
             /          \
  sharedSecret_A      encapsulation = enc   ---> Bob
       (BÍ MẬT)               (CÔNG KHAI)       |
                                              Decap(skR, enc)
                                                    |
                                               sharedSecret_B

                    sharedSecret_A == sharedSecret_B
~~~

- **Secret:** không gửi, không log; cần đưa qua **KDF theo ngữ cảnh** để lấy key thực sự dùng cho AES-GCM/MAC.
- **Encapsulation (hay KEM ciphertext):** dữ liệu công khai cần gửi; với **DHKEM trên X25519**, nó thường là biểu diễn **ephemeral public key** theo định dạng KEM (32 byte), **không phải raw secret** hoặc ciphertext file.
- KEM không tự mã hóa nội dung lớn, không tự xác thực sender và không chống replay. Base-mode KEM cho **bất kỳ ai có pkR** tạo thông điệp gửi recipient.
- Java 21 mới có `javax.crypto.KEM`. Java 17 có `KeyAgreement` X25519 nhưng không tự có `KEM` hoặc quy tắc DHKEM của RFC 9180.

Snippet Java 21+:

~~~java
// Snippet: public key Bob phải được xác thực trước.
// recipientPublic: PublicKey, recipientPrivate: PrivateKey.
KEM kem = KEM.getInstance("DHKEM");
KEM.Encapsulated result =
        kem.newEncapsulator(recipientPublic).encapsulate();
SecretKey localSecret = result.key();        // giữ bí mật
byte[] message = result.encapsulation();     // gửi cho Bob

SecretKey recipientSecret =
        kem.newDecapsulator(recipientPrivate)
           .decapsulate(message);
~~~

Đừng dùng `message` làm key AES dù nó có thể có cùng độ dài với secret; đây là **public transport data**. Một message KEM đủ độ dài nhưng bị sửa có thể làm bên nhận sinh **secret khác**, không nhất thiết ném exception. Khi dùng KEM → KDF → AEAD, **tag AEAD** mới phải xác nhận payload và AAD; một KEM hợp lệ không đương nhiên có sender authentication.

## 6. Một chương trình Java 21 so sánh ba cơ chế trên cùng dữ liệu thử

Chương trình dưới đây chỉ thử **hình dạng đầu vào/đầu ra** của KeyAgreement, AES Key Wrap và DHKEM, giúp thấy một thao tác tạo secret, một thao tác bảo vệ key **đã có**, và một thao tác tạo secret cùng encapsulation. Chương trình không tạo giao thức mạng, không dẫn xuất traffic key, không xác minh danh tính và không dùng key để mã hóa payload. Trong sản phẩm thực, ưu tiên giao thức tiêu chuẩn, KDF và AEAD đúng thiết kế.

Tạo file **MechanismComparisonDemo.java**:

~~~java
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KEM;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class MechanismComparisonDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator x = KeyPairGenerator.getInstance("X25519");
        KeyPair alice = x.generateKeyPair();
        KeyPair bob = x.generateKeyPair();

        // 1. Agreement: private của mình + public của đối tác.
        KeyAgreement a = KeyAgreement.getInstance("X25519");
        a.init(alice.getPrivate());
        a.doPhase(bob.getPublic(), true);
        byte[] za = a.generateSecret();

        KeyAgreement b = KeyAgreement.getInstance("X25519");
        b.init(bob.getPrivate());
        b.doPhase(alice.getPublic(), true);
        byte[] zb = b.generateSecret();
        System.out.println("Agreement secret equal: "
                + MessageDigest.isEqual(za, zb));

        // 2. Wrap: bảo vệ chính một DEK đã có bằng KEK.
        KeyGenerator aes = KeyGenerator.getInstance("AES");
        aes.init(128);
        SecretKey dek = aes.generateKey();
        SecretKey kek = aes.generateKey();
        Cipher wrapper = Cipher.getInstance("AESWrap");
        wrapper.init(Cipher.WRAP_MODE, kek);
        byte[] wrapped = wrapper.wrap(dek);

        Cipher unwrapper = Cipher.getInstance("AESWrap");
        unwrapper.init(Cipher.UNWRAP_MODE, kek);
        Key restored = unwrapper.unwrap(
                wrapped, "AES", Cipher.SECRET_KEY);
        System.out.println("Wrap restores old DEK: "
                + MessageDigest.isEqual(
                        dek.getEncoded(), restored.getEncoded()));

        // 3. KEM: tạo secret mới và encapsulation công khai.
        KeyPair recipient = x.generateKeyPair();
        KEM kem = KEM.getInstance("DHKEM");
        KEM.Encapsulated enc =
                kem.newEncapsulator(recipient.getPublic()).encapsulate();
        SecretKey received =
                kem.newDecapsulator(recipient.getPrivate())
                   .decapsulate(enc.encapsulation());
        System.out.println("KEM secret equal: "
                + MessageDigest.isEqual(
                        enc.key().getEncoded(), received.getEncoded()));
        System.out.println("KEM message present: "
                + (enc.encapsulation().length > 0));

        Arrays.fill(za, (byte) 0);
        Arrays.fill(zb, (byte) 0);
    }
}
~~~

Chạy bằng **JDK 21+** có provider hỗ trợ DHKEM/X25519 và AESWrap:

~~~bash
javac --release 21 -encoding UTF-8 MechanismComparisonDemo.java
java MechanismComparisonDemo
~~~

Stdout:

~~~text
Agreement secret equal: true
Wrap restores old DEK: true
KEM secret equal: true
KEM message present: true
~~~

Kết quả này chứng minh **ba contract API khác nhau** trong một tiến trình. `Arrays.fill` chỉ giảm thời gian tồn tại của các mảng tự sở hữu; không chứng minh xóa mọi secret bên trong provider/JVM. `getEncoded()` được dùng trong demo với khóa phần mềm xuất được; HSM/provider có thể trả `null`. Chưa có xác thực danh tính, key confirmation, nonce, chống replay hoặc policy, nên không coi đoạn mã là một secure channel.

## 7. Chọn cơ chế theo bài toán thực tế

| Yêu cầu | Cơ chế thường phù hợp | Điều phải làm thêm |
| --- | --- | --- |
| Hai service cần tự thiết lập bí mật phiên trên kết nối | Key agreement/KEM **bên trong giao thức chuẩn** như TLS 1.3 | Xác thực danh tính, transcript, KDF, traffic keys, nonce, policy |
| Một bên đã tạo AES-DEK và cần Bob có lại đúng key đó | Key transport, RSA-OAEP hoặc wrap qua KEK phù hợp | Xác thực đúng public key/key owner, metadata, quyền |
| Mã hóa file lớn và lưu key dài hạn | DEK + AEAD, bọc DEK dưới KEK trong KMS/HSM | Key ID, quyền unwrap, rotation/backup, tenant/object binding |
| Gửi file cho recipient có public key, tạo session secret mới | KEM + KDF + AEAD; cân nhắc HPKE | Xác thực recipient, sender auth khi yêu cầu, replay, wire format |
| Thay KEK mà vẫn giữ DEK | Unwrap/rewrap DEK | Quyền KMS, cập nhật phiên bản nguyên tử và rollback policy |
| Giao tiếp tương thích nhiều ngôn ngữ/provider | Một protocol/suite đã đặc tả và test vectors | Định dạng public key, OAEP parameters, HKDF info, AEAD AAD/nonce |

**Cần xác thực public key ở cả key agreement, KEM và public-key transport.** Nếu attacker tráo public key, bạn có thể chia sẻ secret hoặc bọc DEK **cho chính attacker** dù phép toán hoàn tất hợp lệ. Certificate, pin đáng tin, chữ ký trên transcript và các cơ chế được giao thức chuẩn hóa mới ràng buộc khóa với identity.

## 8. Những điều dễ nhầm và hậu quả

1. **“Key exchange = thuật toán DH duy nhất.”** Sai: đây là mục tiêu/giao thức, có thể kết hợp nhiều primitive.
2. **“Shared secret X25519 đủ dài nên dùng trực tiếp làm mọi AES/MAC key.”** Thiếu KDF và key-purpose/role separation.
3. **“Wrap và encapsulate đều mã hóa một DEK chọn trước.”** Không: wrap nhận key có sẵn, còn KEM mặc định sinh secret mới.
4. **“Encapsulation 32 byte là AES-256 key.”** Không: dù cùng kích thước, encapsulation là dữ liệu công khai.
5. **“AESWrap có AAD tenant giống AES-GCM.”** Không: AES-KW xác thực key material theo thuật toán riêng, metadata cần cơ chế khác.
6. **“Nhận public key là đã biết người gửi.”** Không: phải kiểm chứng nguồn/danh tính; base KEM không xác thực sender.
7. **“Bọc DEK thành công tức ciphertext file đã xác thực.”** Không: file cần tag/MAC theo thuật toán mã hóa payload.
8. **“Có secret chung là không thể bị MITM.”** Không: kẻ đứng giữa có thể lập hai phiên thỏa thuận với khóa của mình.
9. **“Java 17 có KEM vì đã có X25519.”** Không: API `javax.crypto.KEM` xuất hiện từ Java 21.
10. **“SecretKey.getEncoded() luôn cho bytes.”** Không: khóa trong HSM/KMS có thể non-extractable.
11. **“DHKEM + HKDF + AES-GCM tự viết = HPKE chuẩn.”** Không: HPKE còn định nghĩa key schedule, suite, mode, record context và serialization.
12. **“Wrapped key hợp lệ không thể bị tráo sang file khác.”** Không: cần authorization, policy, record binding và chống replay/rollback.

## 9. Bài tập tự kiểm tra

**1.** Một file đã có DEK AES-256. Cần bảo vệ DEK trong KMS để sau này mở đúng DEK cũ. Cơ chế nào phù hợp, và vì sao KEM không phải một lời gọi `encapsulate(existingDek)`?

**2.** Alice và Bob tạo cùng bí mật bằng X25519. Dữ liệu nào được gửi qua mạng, và bước nào còn thiếu để Alice biết đó thật sự là Bob?

**3.** Đối với một thông điệp KEM, phần nào được gửi công khai, phần nào giữ bí mật và phần nào là ciphertext của dữ liệu ứng dụng?

**4.** Tại sao `unwrap(bytes, "AES", SECRET_KEY)` yêu cầu chuỗi `"AES"` dù transformation đã là `"AESWrap"`?

**5.** Nếu recipient RSA private key dài hạn bị lộ, dữ liệu khóa được vận chuyển bằng RSA-OAEP trước đây có thuộc tính forward secrecy mặc nhiên không? Giải thích.

**6.** Một DEK được unwrap hợp lệ nhưng người dùng không thuộc tenant của bản ghi. Hệ thống có được phép giải mã và trả plaintext không? Lớp nào phải từ chối?

**7.** Một encapsulation DHKEM bị sửa nhưng có độ dài hợp lệ. Vì sao chỉ kiểm exception của `decapsulate()` chưa đủ để bảo vệ payload?

**8.** Khi nào nên dùng TLS/HPKE được kiểm chứng thay vì tự ghép `KeyAgreement`, `KEM`, `Cipher` và HKDF?

## 10. Checklist triển khai

- [ ] Xác định key **sinh mới** hay **đã có**; cần trao đổi secret, bảo vệ DEK hay mã hóa payload.
- [ ] Chọn đúng primitive và API, ghi rõ JDK version, provider, thuật toán và key types.
- [ ] Public key người nhận/peer phải được xác thực theo policy; raw shared secret phải qua KDF phù hợp.
- [ ] Bảo vệ payload bằng AEAD với nonce/tag/AAD đúng; bọc khóa không thay xác thực dữ liệu.
- [ ] Với DEK/KEK, quản lý key ID, quyền wrap/unwrap, HSM/KMS, rewrap, backup và rotation.
- [ ] Chống MITM, replay, downgrade và nhầm context bằng **giao thức đã đặc tả và được kiểm thử**, không chỉ một phép toán thành công.
- [ ] Không log private key, shared secret, traffic key, KEK/DEK; kiểm tra provider có key non-exportable hay không.
- [ ] Test lỗi sai key/suite, public key bị thay, tampered capsule/ciphertext, metadata tráo và phiên bản không được phép.

## Tài liệu chuẩn

- [Java SE 17 — KeyAgreement](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyAgreement.html)
- [Java SE 17 — Cipher wrap/unwrap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html)
- [Java SE 21 — KEM API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.html)
- [RFC 7748 — X25519/X448](https://www.rfc-editor.org/rfc/rfc7748)
- [RFC 3394 — AES Key Wrap](https://www.rfc-editor.org/rfc/rfc3394)
- [RFC 5869 — HKDF](https://www.rfc-editor.org/rfc/rfc5869)
- [RFC 9180 — HPKE KEM/KDF/AEAD](https://www.rfc-editor.org/rfc/rfc9180)
- [RFC 8446 — TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-establishment-and-wrapping-002">10.2 DH, ECDH, X25519 và X448 trong mô hình KeyAgreement</a>

<details>
<summary>Xem chi tiết</summary>

# 10.2 DH, ECDH, X25519 và X448 trong mô hình KeyAgreement

## 1. Bài toán: hai bên tạo cùng một bí mật mà không gửi bí mật đó

**Thỏa thuận khóa** (key agreement) cho phép Alice và Bob, mỗi người giữ một **khóa riêng** và trao đổi **khóa công khai**, tính ra **cùng một shared secret** qua mạng. Bên nghe lén có thể thấy khóa công khai và gói tin trao đổi, nhưng không dễ tính được bí mật nếu thuật toán, nhóm toán học, ngẫu nhiên và triển khai đáp ứng yêu cầu bảo mật.

Java Cryptography Architecture / Java Cryptography Extension (JCA/JCE) biểu diễn phép tính này bằng engine `javax.crypto.KeyAgreement`. Bốn biến thể trong bài:

- **DH** (finite-field Diffie–Hellman, thường gọi FFDH): phép lũy thừa modulo với tham số nhóm `p, g`.
- **ECDH** (Elliptic-Curve Diffie–Hellman): phép nhân vô hướng điểm trên một đường cong elliptic, ví dụ `secp256r1`/P-256.
- **X25519**: hàm trao đổi khóa Diffie–Hellman dựa trên Curve25519, thiết kế theo Montgomery ladder; triển khai Java thường dùng JCA algorithm `X25519` hoặc `XDH` với `NamedParameterSpec.X25519`.
- **X448**: tương tự với Curve448 và mức bảo mật nhắm tới cao hơn; JCA algorithm `X448` hoặc `XDH` với tham số `NamedParameterSpec.X448`.

**Điều không được nhầm:** KeyAgreement **không tự mã hóa dữ liệu, không tự xác thực đối tác, không tự cấp chứng thư và không tự tạo khóa AES an toàn theo giao thức**. Sau khi lấy shared secret, cần **KDF** có định danh ngữ cảnh (ví dụ HKDF) để dẫn xuất key cho AES-GCM hoặc key-encryption key (KEK) dùng bọc khóa. Muốn chống người ở giữa (MITM), phải **xác thực khóa công khai, chữ ký transcript hoặc danh tính đối tác**.

Luồng hai bên:

```text
 Alice                                               Bob
 private a                                           private b
 public A                                            public B
    |                                                    |
    |---------------------  A  ------------------------->|
    |<--------------------  B  --------------------------|
    |                                                    |
 DH/ECDH/X25519/X448(private a, B)      DH/ECDH/X25519/X448(private b, A)
    |                                                    |
    +--> shared secret S                    shared secret S <--+
                    |                                  |
             HKDF(salt, S, info)               HKDF(salt, S, info)
                    |                                  |
                session key  <------- bằng nhau ------> session key
                    |                                  |
                      AEAD (chẳng hạn AES-GCM)
```

Các khóa `a` và `b` không bao giờ được gửi đi. Chữ `A`, `B` trong sơ đồ là **khóa công khai được mã hóa đúng định dạng**, không phải raw shared secret.

## 2. So sánh DH, ECDH, X25519 và X448

| Thuật toán | Nhóm toán học / tham số chính | JCA KeyPairGenerator | JCA KeyAgreement | Shared secret trong ví dụ |
| --- | --- | --- | --- | --- |
| DH | Nhóm hữu hạn với `p, g`; ví dụ tham số DH 2048 bit | `"DH"` (alias `"DiffieHellman"`) | `"DH"` | Thường **256 byte** với tham số 2048 bit |
| ECDH | Đường cong EC có tên, ví dụ P-256 (`secp256r1`) | `"EC"` + `ECGenParameterSpec("secp256r1")` | `"ECDH"` | **32 byte** trên P-256 trong provider ví dụ |
| X25519 | X25519/Curve25519, giao thức theo RFC 7748 | `"X25519"` + `NamedParameterSpec("X25519")` | `"X25519"` | **32 byte** |
| X448 | X448/Curve448, theo RFC 7748 | `"X448"` + `NamedParameterSpec("X448")` | `"X448"` | **56 byte** |

Bảng cho biết **độ dài output của phép thỏa thuận**, **không** phải số byte AES key bắt buộc. KDF có thể tạo khóa AES-128 (16 byte) hoặc AES-256 (32 byte) nhưng **độ dài output dài hơn không làm tăng mức an toàn vượt entropy/security strength của đầu vào**. Ví dụ X25519 thường hướng tới mức bảo mật tương đương khoảng 128 bit; việc dẫn xuất AES-256 không tự biến toàn giao thức thành an toàn 256 bit.

### 2.1 DH: nhóm hữu hạn phải giống nhau

Với một nhóm DH hợp lệ có số nguyên tố `p` và phần tử sinh `g`, Alice chọn private exponent `a`, xuất `A = g^a mod p`; Bob chọn `b`, xuất `B = g^b mod p`. Sau khi nhận public của nhau, cả hai tính cùng:

```text
Alice: S = B^a mod p = g^(ab) mod p
Bob:   S = A^b mod p = g^(ab) mod p
```

**Điều kiện:** cả hai phải dùng **cùng nhóm p/g**, có kiểm tra độ an toàn của nhóm và public value. Không được tạo hai `DHParameterSpec` ngẫu nhiên độc lập rồi kỳ vọng DH thành công. Với `KeyPairGenerator("DH")`, có thể lấy tham số từ `((DHPublicKey) alice.getPublic()).getParams()` rồi khởi tạo generator bên còn lại bằng cùng tham số. Trong **hệ thống thực tế**, không chỉ tin một bộ `p,g` bất kỳ do phía mạng đề xuất: ưu tiên nhóm FFDHE chuẩn hóa, đủ mạnh, kiểm tra public key/subgroup và ràng buộc params vào handshake đã xác thực.

**2048 bit DH** được dùng trong ví dụ vì dễ triển khai bằng JDK phổ biến; đây là độ dài modulus, không đồng nghĩa mức bảo mật 2048 bit. Các hệ thống có yêu cầu bảo mật cao hơn có thể cần nhóm lớn hơn hoặc chọn thuật toán khác theo chính sách.

### 2.2 ECDH: cùng đường cong, khóa khác nhau

ECDH hoạt động với private scalar `a`/`b` và public points `A=aG`, `B=bG` trên cùng domain parameters. Cả hai tính shared point `abG`; provider chuyển thành byte shared secret theo quy tắc của thuật toán. `ECGenParameterSpec("secp256r1")` tạo key pair trên **P-256**, tên cũng thường gọi `prime256v1` ở nhiều công cụ.

**Không** dùng public P-384 hay khóa X25519 làm peer public cho một `KeyAgreement("ECDH")` khởi tạo từ private P-256. Algorithm, curve và tham số phải tương thích. Một object `KeyPairGenerator("EC")` **không** đồng nghĩa X25519: X25519 nằm trong họ `XDH` với key interfaces và phép tính khác.

### 2.3 X25519 và X448: tên thuật toán rõ ràng

X25519/X448 được chuẩn hóa trong RFC 7748. Chúng cho phép triển khai phép scalar multiplication trên các đường cong Montgomery với quy tắc cụ thể; thiết kế và key format không hoán đổi với `ECGenParameterSpec("secp256r1")`.

Java 17+ với provider hỗ trợ có thể dùng:

```java
// Đoạn minh họa; trong phương thức Java với các import tương ứng.
KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
generator.initialize(new NamedParameterSpec("X25519"), new SecureRandom());
KeyPair myPair = generator.generateKeyPair();
KeyAgreement ka = KeyAgreement.getInstance("X25519");
ka.init(myPair.getPrivate());
ka.doPhase(peerPublicKey, true);
byte[] sharedSecret = ka.generateSecret();
```

`NamedParameterSpec.X25519` và `NamedParameterSpec.X448` là các constant tiêu chuẩn có sẵn; ví dụ dùng constructor chuỗi để áp dụng cùng mã cho cả hai. Provider/phiên bản Java có thể không hỗ trợ một thuật toán/curve nhất định, khi đó cần xử lý `NoSuchAlgorithmException` hoặc `InvalidAlgorithmParameterException` một cách rõ ràng; không tự chuyển xuống nhóm yếu hoặc bỏ xác thực.

X25519 public key trên wire thường được biểu diễn bằng **raw 32 bytes** theo một giao thức cụ thể *hoặc* X.509 SPKI (ví dụ Java `getEncoded()`). X448 raw public value 56 byte. **Không trộn hai encoding**: `X509EncodedKeySpec` cần SPKI DER có ASN.1 header, không phải raw 32/56 byte.

## 3. Vòng đời KeyAgreement trong JCA

Mỗi bên thực hiện bốn bước cơ bản:

```text
KeyAgreement.getInstance(algorithm)
       |
       v
init(ourPrivateKey)
       |
       v
doPhase(peerPublicKey, true)   // lần cuối trong trao đổi 2 bên
       |
       v
generateSecret()               // shared secret byte[]
       |
       v
KDF + context + salt -> symmetric key / KEK
```

- **`getInstance`:** chọn phép thỏa thuận và provider; không tạo cặp khóa.
- **`init(privateKey)`:** cung cấp private key của **chính mình**, khác với public key cần được gửi cho đối tác. Sai key type sẽ ném `InvalidKeyException`.
- **`doPhase(peerPublic, true)`:** tiếp nhận public key của **đối tác**; flag `true` nói đây là pha cuối cùng. Trong giao thức DH **hai bên**, gọi đúng một lần. Các giao thức multi-party có thể có pha trung gian với `false`, không suy diễn rằng mọi thuật toán/provider đều hỗ trợ multi-party.
- **`generateSecret()`:** lấy shared secret **mới trong byte[]** sau khi pha cuối hoàn tất. Hai bên phải dùng khóa/params đúng và byte secret giống nhau. Nếu chưa `init`, chưa `doPhase` hoặc chưa đủ pha, `IllegalStateException` có thể xảy ra.

Theo Javadoc, `generateSecret()` đưa `KeyAgreement` về trạng thái sau lần `init` gần nhất; về mặt API có thể gọi `doPhase` và `generateSecret` lần nữa với cùng private key. **Nhưng** trong giao thức ephemeral, tái sử dụng private key thường là sai chính sách phiên: tốt hơn sinh cặp khóa mới cho mỗi handshake và không chia sẻ `KeyAgreement` stateful giữa thread/request.

Ngoài `generateSecret()` trả `byte[]`, JCA có overload `generateSecret(byte[], offset)` và `generateSecret(String algorithm)`; overload thứ ba phụ thuộc khả năng provider, *không thay thế một KDF có salt/info và nhãn phân tách domain theo giao thức*. **Không lấy các byte shared secret rồi dùng `new SecretKeySpec(secret, "AES")` một cách tùy tiện.** Độ dài có thể trùng kích thước AES nhưng kết quả cần qua KDF, binding ngữ cảnh và chính sách bảo mật giao thức.

## 4. Chương trình Java 17+: bốn thuật toán chung một mẫu KeyAgreement

Chương trình tự sinh hai cặp khóa (Alice/Bob) tương thích, mỗi bên gọi `init → doPhase → generateSecret`, so sánh byte bằng `MessageDigest.isEqual` và in kích thước secret. Không in private key hoặc shared secret.

Với **DH**, Bob nhận đúng `DHParameterSpec` (p/g) từ public key Alice trong **phạm vi demo**. Ngoài thực tế, việc trao nhóm DH phải theo chính sách nhóm chuẩn và được xác thực; không tin mù quáng params do người ngoài gửi.

Lưu thành **`KeyAgreementFamiliesDemo.java`**:

```java
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.NamedParameterSpec;
import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import java.util.Arrays;

public final class KeyAgreementFamiliesDemo {
    private static void test(String name, String agreement,
                             KeyPair alice, KeyPair bob) throws Exception {
        KeyAgreement a = KeyAgreement.getInstance(agreement);
        a.init(alice.getPrivate());
        a.doPhase(bob.getPublic(), true);
        byte[] sa = a.generateSecret();

        KeyAgreement b = KeyAgreement.getInstance(agreement);
        b.init(bob.getPrivate());
        b.doPhase(alice.getPublic(), true);
        byte[] sb = b.generateSecret();
        try {
            System.out.println(name + " equal="
                    + MessageDigest.isEqual(sa, sb)
                    + " bytes=" + sa.length);
        } finally {
            Arrays.fill(sa, (byte) 0);
            Arrays.fill(sb, (byte) 0);
        }
    }

    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();

        KeyPairGenerator dhAliceGen =
                KeyPairGenerator.getInstance("DH");
        dhAliceGen.initialize(2048, random);
        KeyPair aliceDh = dhAliceGen.generateKeyPair();
        DHParameterSpec params =
                ((DHPublicKey) aliceDh.getPublic()).getParams();
        KeyPairGenerator dhBobGen =
                KeyPairGenerator.getInstance("DH");
        dhBobGen.initialize(params, random);
        KeyPair bobDh = dhBobGen.generateKeyPair();
        test("DH", "DH", aliceDh, bobDh);

        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp256r1"), random);
        KeyPair aliceEc = ec.generateKeyPair();
        KeyPair bobEc = ec.generateKeyPair();
        test("ECDH-P256", "ECDH", aliceEc, bobEc);

        for (String name : new String[] { "X25519", "X448" }) {
            KeyPairGenerator x = KeyPairGenerator.getInstance(name);
            x.initialize(new NamedParameterSpec(name), random);
            test(name, name, x.generateKeyPair(), x.generateKeyPair());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 KeyAgreementFamiliesDemo.java
java KeyAgreementFamiliesDemo
```

Kết quả đã kiểm tra với runtime OpenJDK hỗ trợ bốn thuật toán:

```text
DH equal=true bytes=256
ECDH-P256 equal=true bytes=32
X25519 equal=true bytes=32
X448 equal=true bytes=56
```

Kết quả `equal=true` nghĩa là **hai phép thỏa thuận với private/public tương ứng đã sinh cùng các byte**, không chứng minh tính xác thực của một peer ngoài mạng. `DH bytes=256` là với modulus DH 2048 bit trong ví dụ; độ dài bytes có thể khác nếu sử dụng nhóm DH khác. `ECDH-P256 bytes=32` là encoding của shared result cho P-256; X25519/X448 có output 32/56 byte theo thuật toán.

**Bộ nhớ:** chương trình dùng `Arrays.fill(sa, (byte) 0)` để giảm lifetime của các mảng secret nó sở hữu, nhưng Java GC, provider, `KeyPair` objects và các bản sao nội bộ có thể tồn tại lâu hơn. Không xem `Arrays.fill` là bảo đảm xóa tuyệt đối mọi bản sao cryptographic secret.

## 5. Khóa công khai trên mạng: cách encode và validate

Để gửi khóa công khai giữa các tiến trình Java, thường dùng `getEncoded()` lấy **DER của X.509 SubjectPublicKeyInfo** rồi gửi kèm metadata thuật toán, version, curve/group, role và handshake ID. Bên nhận dùng `KeyFactory` với đúng thuật toán để tái tạo:

```java
// Đoạn minh họa trong phương thức Java; publicBytes lấy từ bản tin
// đã được parse và giới hạn kích thước, kèm hợp đồng thuật toán rõ ràng.
java.security.PublicKey imported =
        java.security.KeyFactory.getInstance("X25519")
                .generatePublic(new java.security.spec
                        .X509EncodedKeySpec(publicBytes));
```

Đoạn trên cố tình **không độc lập**: `publicBytes` đến từ giao thức. Với ECDH P-256, dùng `KeyFactory("EC")`; với DH dùng `KeyFactory("DH")`; với X448 dùng `KeyFactory("X448")`. Raw X25519/X448 point bytes của RFC 7748 không phải SPKI DER. Nếu wire protocol gửi raw u-coordinate, phải parse bằng API/format đúng (ví dụ provider hỗ trợ `XECPublicKeySpec`), không gắn nhãn X.509 một cách giả tạo.

Đối với khóa riêng: `PrivateKey.getEncoded()` nếu được phép export thường là **PKCS#8 DER**; **không** bao giờ truyền private key giữa Alice/Bob để thực hiện phép DH. Provider phần cứng có thể trả `null` cho `getEncoded()`; dùng key handle tương ứng thay vì ép export.

**Validation public key là ranh giới quan trọng:**

- **DH:** kiểm tra nhóm `p,g` nằm trong danh sách/tiêu chuẩn cho phép, độ mạnh và public DH value thuộc miền hợp lệ; với subgroup cần kiểm tra theo giao thức. Các giá trị `1`, `0`, `p-1` hoặc phần tử nhóm nhỏ có thể gây secret yếu/tấn công tùy cấu trúc nhóm. Không tin cấu hình nhóm do attacker tự đưa.
- **ECDH:** đường cong phải là curve được phép và khớp với private key; điểm công khai hợp lệ trên curve, không phải infinity/điểm sai subgroup (tùy curve/cofactor); dùng provider đáng tin có kiểm tra thích hợp và policy bổ sung khi nhập khóa từ untrusted input.
- **X25519/X448:** cần tuân thủ RFC 7748 và kiểm soát peer keys không hợp lệ/low-order, kể cả việc kiểm tra và từ chối **shared secret toàn số 0** theo yêu cầu giao thức. Một số JDK/provider sẽ từ chối key yếu trong `doPhase`/`generateSecret` bằng `InvalidKeyException`; đừng giả định mọi provider xử lý giống nhau. Không chấp nhận giá trị rỗng/nhóm sai như một secret bình thường.
- Mọi wire format phải có **giới hạn kích thước**, thuật toán/curve được allow-list và parser xử lý lỗi đúng. Public key có thể công khai nhưng vẫn là dữ liệu đầu vào **không đáng tin** đối với parser.

**Nhận diện peer:** ngay cả khi public key hợp lệ về toán học, nó vẫn có thể là khóa của Mallory. Một certificate chain đã xác minh, khóa pinned được phân phối an toàn, chữ ký trên transcript hoặc PSK-based authenticated handshake mới có thể liên kết khóa với danh tính.

## 6. Từ shared secret sang AES key: HKDF với salt và info

JCA `generateSecret()` trả vật liệu chung `S`, không phải format ciphertext. Shared secret của DH/ECDH có cấu trúc và phân bố riêng; không nên lấy `S` trực tiếp làm key AES hoặc lạm dụng `Arrays.copyOf(S, 16)`, `SHA256(S)` không có domain separation. Dùng **HKDF** chuẩn RFC 5869 với PRF như HMAC-SHA256:

```text
IKM  = KeyAgreement.generateSecret()
salt = 32 byte được chia sẻ giữa hai phía, không cần bí mật
PRK  = HKDF-Extract(salt, IKM) = HMAC_SHA256(salt, IKM)
info = protocol label || version || roles || transcript || key-purpose
OKM  = HKDF-Expand(PRK, info, requestedLength)
AES  = SecretKeySpec(OKM, "AES")
```

- **IKM** là input keying material. `PRK` là pseudorandom key. `OKM` là byte khóa đầu ra.
- **Salt** của HKDF không cần bí mật; có thể là một public handshake nonce hoặc một giá trị được hai bên thống nhất. Nếu protocol không cung cấp salt, HKDF còn có quy tắc salt toàn zero theo RFC 5869; **không** biến salt thành một private key thứ hai.
- **Info** ràng buộc *giao thức, phiên bản, hai khóa công khai, vai trò client/server, hướng truyền và mục đích tạo key*. Cả hai bên phải encode giống nhau **theo một chuẩn duy nhất**. Một chuỗi nối thiếu độ dài/ngăn cách có thể mơ hồ; nên dùng length prefixes hoặc format nhị phân có schema.
- Với nhiều khóa phiên, dùng label/info **khác nhau** cho `client→server key`, `server→client key`, nonce-base, key wrapping hoặc exporter secret. Không tái sử dụng cùng output HKDF cho nhiều mục đích.
- AES-256 cần 32 byte **OKM**. Với HMAC-SHA256, nếu chỉ cần 32 byte thì `HKDF-Expand` dùng **một block**: `T(1)=HMAC(PRK, info || 0x01)`. Nếu cần nhiều byte, phải thực hiện vòng lặp `T(i)=HMAC(PRK, T(i-1)||info||i)`, giới hạn tối đa 255 block; **không** dùng mã một-block bên dưới để sinh nhiều key cùng lúc mà không sửa.
- Key agreement không thay đổi tính chất **xác thực nguồn**. HKDF có info là transcript chưa được ký vẫn không chống MITM nếu Mallory thay các public keys và làm hai giao thức riêng.

Chương trình tiếp theo sẽ tự triển khai **trường hợp HKDF một block 32 byte** bằng `Mac.getInstance("HmacSHA256")` để hiểu cơ chế. Trong sản phẩm nên dùng thư viện HKDF được đánh giá, giữ metadata và test vectors, thay vì tự phát triển một KDF tổng quát.

## 7. Chương trình Java 17+: X25519 → HKDF-SHA256 → AES-GCM

Đây là ví dụ **end-to-end trong cùng một JVM** để chứng minh Alice/Bob tạo cùng shared secret, dẫn xuất cùng AES-256 key với **HKDF-Extract + một block HKDF-Expand**, mã hóa/giải mã bằng AES-GCM, và phát hiện sửa ciphertext/tag.

**Cảnh báo mô hình đe dọa:** chương trình chỉ có hai peer tin cậy trong một JVM; nó **chưa** triển khai xác thực peer, chữ ký transcript, certificate/pinning, framing mạng hay replay protection. Không xem đây là giao thức trao đổi khóa hoàn chỉnh có thể chép vào sản phẩm Internet. Khi triển khai thực, ưu tiên dùng TLS 1.3 hoặc một giao thức authenticated key exchange đã được đánh giá.

Lưu thành **`X25519HkdfAesGcmDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class X25519HkdfAesGcmDemo {
    private static byte[] agree(KeyPair ours,
                                java.security.PublicKey theirs) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(ours.getPrivate());
        agreement.doPhase(theirs, true);
        return agreement.generateSecret();
    }

    private static byte[] transcript(byte[] a, byte[] b) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            byte[] domain = "demo-x25519-aes-gcm-v1"
                    .getBytes(StandardCharsets.US_ASCII);
            out.writeInt(domain.length);
            out.write(domain);
            out.writeInt(a.length);
            out.write(a);
            out.writeInt(b.length);
            out.write(b);
        }
        return bytes.toByteArray();
    }

    private static byte[] deriveAes256(
            byte[] shared, byte[] salt, byte[] info) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(shared);  // HKDF-Extract
        try {
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(info);
            mac.update((byte) 1);          // T(1), 32 bytes for AES-256
            return mac.doFinal();
        } finally {
            Arrays.fill(prk, (byte) 0);
        }
    }

    private static Cipher gcm(int mode, byte[] key, byte[] nonce)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, nonce));
        return cipher;
    }

    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        generator.initialize(new NamedParameterSpec("X25519"), random);
        KeyPair alice = generator.generateKeyPair();
        KeyPair bob = generator.generateKeyPair();

        byte[] secretA = agree(alice, bob.getPublic());
        byte[] secretB = agree(bob, alice.getPublic());
        byte[] salt = new byte[32];
        random.nextBytes(salt);
        byte[] info = transcript(
                alice.getPublic().getEncoded(),
                bob.getPublic().getEncoded());

        byte[] keyA = null;
        byte[] keyB = null;
        byte[] otherContextKey = null;
        try {
            System.out.println("sharedEqual="
                    + MessageDigest.isEqual(secretA, secretB));

            keyA = deriveAes256(secretA, salt, info);
            keyB = deriveAes256(secretB, salt, info);
            byte[] otherInfo = Arrays.copyOf(info, info.length + 1);
            otherInfo[otherInfo.length - 1] = 1;
            otherContextKey = deriveAes256(secretA, salt, otherInfo);
            System.out.println("derivedEqual="
                    + MessageDigest.isEqual(keyA, keyB));
            System.out.println("contextSeparated="
                    + !MessageDigest.isEqual(keyA, otherContextKey));
            System.out.println("derivedBits=" + (keyA.length * 8));

            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            byte[] aad = "record:1".getBytes(StandardCharsets.UTF_8);
            byte[] message = "Hello secure channel"
                    .getBytes(StandardCharsets.UTF_8);

            Cipher encrypt = gcm(Cipher.ENCRYPT_MODE, keyA, nonce);
            encrypt.updateAAD(aad);
            byte[] ciphertextAndTag = encrypt.doFinal(message);

            Cipher decrypt = gcm(Cipher.DECRYPT_MODE, keyB, nonce);
            decrypt.updateAAD(aad);
            byte[] recovered = decrypt.doFinal(ciphertextAndTag);
            System.out.println("roundTrip="
                    + Arrays.equals(message, recovered));

            byte[] modified = ciphertextAndTag.clone();
            modified[modified.length - 1] ^= 1;
            try {
                Cipher reject = gcm(Cipher.DECRYPT_MODE, keyB, nonce);
                reject.updateAAD(aad);
                reject.doFinal(modified);
                System.out.println("tamperRejected=false");
            } catch (AEADBadTagException expected) {
                System.out.println("tamperRejected=true");
            }
        } finally {
            Arrays.fill(secretA, (byte) 0);
            Arrays.fill(secretB, (byte) 0);
            if (keyA != null) Arrays.fill(keyA, (byte) 0);
            if (keyB != null) Arrays.fill(keyB, (byte) 0);
            if (otherContextKey != null)
                Arrays.fill(otherContextKey, (byte) 0);
        }
    }
}
```

Biên dịch và chạy độc lập:

```bash
javac --release 17 -encoding UTF-8 X25519HkdfAesGcmDemo.java
java X25519HkdfAesGcmDemo
```

Đầu ra đã xác minh:

```text
sharedEqual=true
derivedEqual=true
contextSeparated=true
derivedBits=256
roundTrip=true
tamperRejected=true
```

### 7.1 Tại sao chương trình cho kết quả này?

`sharedEqual=true`: X25519(private Alice, public Bob) và X25519(private Bob, public Alice) sinh cùng 32 byte. `derivedEqual=true`: HKDF nhận **cùng secret, salt và info**, nên hai phía tạo cùng 32 byte AES-256 material. `contextSeparated=true`: đổi một byte `info` tạo key khác (với xác suất áp đảo), minh họa domain separation.

`transcript(...)` tạo bytes gồm **length-prefixed** protocol label và SPKI DER của Alice/Bob theo một thứ tự cố định. Trong giao thức thật, cả hai phía phải đồng ý **vai trò và thứ tự**, thêm message transcript, algorithm negotiation và session identification, đồng thời **xác thực** nội dung transcript. Bản demo không tự làm việc đó.

`roundTrip=true`: `Cipher("AES/GCM/NoPadding")` nhận key byte từ HKDF, IV ngẫu nhiên 12 byte, tag 128 bit và AAD giống nhau ở phía giải mã. `tamperRejected=true`: thay byte cuối của ciphertext+tag khiến `doFinal` giải mã ném `AEADBadTagException`; không được sử dụng plaintext nếu tag không hợp lệ. `Cipher.updateAAD` phải xảy ra trước phần dữ liệu mã hóa/giải mã theo yêu cầu AEAD.

**IV GCM không được lặp dưới cùng key.** Trong ví dụ chỉ có một phép mã hóa, nên IV mới ngẫu nhiên 12 byte. Đối với phiên thực với hàng triệu bản ghi, cần nonce allocation/sequence number rõ ràng theo thiết kế giao thức và các giới hạn sử dụng AES-GCM. Không chỉ gọi `new SecureRandom` tùy tiện cho từng message khi đã có quy tắc nonce đếm của protocol; tránh tái sử dụng key+IV qua restart và retry.

**Bảo vệ secret:** `Arrays.fill` giảm tồn tại của các mảng do ứng dụng sở hữu, nhưng không đảm bảo xóa toàn bộ bản sao trong `KeyAgreement`/`Mac`/`Cipher`/`SecretKeySpec` hoặc các object khác. Không log raw shared secret, PRK, derived key, private keys hoặc ciphertext nhạy cảm. Chú ý `salt` và `info` trong ví dụ là public và không cần xóa để bảo mật, nhưng phải được truyền đúng giữa hai phía.

## 8. KeyAgreement không chống MITM: xác thực handshake là bắt buộc

Nếu chỉ trao public keys qua kết nối chưa xác thực:

```text
Alice                     Mallory                       Bob
  A ------> [chặn]           M1 -------->                B
  A <------  M2             [chặn] <--------             B
    |                       |                              |
 S_Alice-Mallory      hai shared secrets            S_Mallory-Bob
```

Mallory có thể tự tạo key pair cho mỗi phía rồi giải mã, đọc/sửa và mã hóa lại dữ liệu. **Thuật toán DH/ECDH/X25519/X448 vẫn tính đúng; thứ bị thiếu là liên kết public key với danh tính peer.** HMAC/HKDF hoặc AES-GCM chỉ xác thực **người biết key đã dẫn xuất**; không giúp Alice biết key đó thuộc Bob nếu Mallory là bên đang thỏa thuận.

Các cách khắc phục phải thuộc **một giao thức authenticated key exchange** rõ ràng:

1. **Xác thực chữ ký khóa/handshake:** peer dùng private signing key dài hạn ký transcript gồm cặp ephemeral keys, roles, algorithm/curve negotiation, random nonces và session context; phía còn lại xác minh bằng signing public key đã gắn danh tính tin cậy.
2. **PKI/certificate:** xác minh certificate chain, trust anchor, hostname/identity, validity, key usage và các ràng buộc giao thức; nhận public signing key từ certificate đã xác minh, không chỉ parse certificate rồi tin ngay.
3. **Pinned key / PSK:** khi có distribution channel đáng tin, dùng cơ chế pinning hoặc authenticated PSK handshake có key confirmation và chống downgrade; không tự nghĩ ra phép ghép MAC thiếu transcript binding.
4. **Giao thức sẵn có:** với truyền thông ứng dụng thực, ưu tiên **TLS 1.3** (hoặc framework/protocol authenticated đã đánh giá). Việc tự xây dựng giao thức trao đổi khóa từ primitive JCA có nhiều rủi ro về replay, reflection, downgrade, context confusion và quản lý phiên.

**Forward secrecy** chỉ đạt được theo điều kiện: dùng ephemeral private keys mới, xác thực handshake đúng, không lộ ephemeral secret trước/sau phiên, và xóa các vật liệu không cần giữ. Static DH có trường hợp sử dụng nhưng không tự có tính chất của ephemeral DH; việc key bị compromise về sau có thể ảnh hưởng phiên cũ theo cấu trúc giao thức.

## 9. Kết nối thỏa thuận khóa với bọc khóa và phiên dữ liệu

Mục “Thỏa thuận và bọc khóa” thường gồm hai bước tách nhau:

1. **Agreement:** Alice và Bob dùng DH/ECDH/XDH sinh shared secret, dẫn xuất khóa theo HKDF.
2. **Protection:** dùng key dẫn xuất làm **key-encryption key (KEK)** để bọc **data-encryption key (DEK)**, hoặc dùng trực tiếp làm key cho AEAD session theo giao thức.

Ví dụ mô hình bọc DEK:

```text
ECDH/X25519/X448 shared secret
          |
          v
 HKDF(info="app/KEK/v1", salt,...)
          |
          v
   256-bit key-encryption key
          |
          +----> AES Key Wrap (theo định dạng/giao thức)
          |      hoặc AES-GCM wrap kèm IV/tag/AAD đúng
          v
    wrapped DEK  --- gửi/lưu ---> bên nhận
                                |
                           cùng KEK hợp lệ
                                |
                            unwrap DEK
                                |
                         dùng DEK cho data
```

**Không** lấy raw shared secret làm DEK/KEK một cách trực tiếp chỉ vì mảng có độ dài 32 byte. Nếu bọc bằng AES-GCM, cần authenticated ciphertext, IV duy nhất và dữ liệu AAD phù hợp. Nếu dùng AES-KW, dùng transformation/format phù hợp nhà cung cấp và kiểm tra quy tắc độ dài, integrity của AES-KW. Tách nhãn `"KEK"` và `"AEAD session key"` bằng `info` trong HKDF; không dùng **cùng raw key cho mọi vai trò**.

Bản ghi lưu/truyền nên chỉ rõ: algorithm suite, curve/group, ephemeral public keys, salt/KDF version/info schema, IV/nonce, ciphertext+tag hoặc wrapped key, key ID/session ID và các trường đã được xác thực. Việc chứa public key, salt và IV trong metadata **không cần giữ bí mật**, nhưng phải bảo đảm integrity của những metadata quan trọng để tránh bị thay đổi trong handshake.

## 10. Bảo mật và lỗi triển khai thường gặp

| Lỗi / hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `InvalidKeyException` ở `init` / `doPhase` | Dùng public thay private, sai algorithm/curve, peer key không hợp lệ hoặc weak public key | Validate type/algorithm, nhóm và public key; dùng đúng private/public |
| `InvalidAlgorithmParameterException` | Key generator nhận curve/param không hợp lệ hoặc provider không chấp nhận | Dùng named curve/group allow-list, kiểm tra khả năng hỗ trợ |
| `NoSuchAlgorithmException` | Provider không có `ECDH`/`X25519`/`X448` | Kiểm tra Java/provider; không tự fallback nhóm yếu |
| `IllegalStateException` ở `generateSecret` | Chưa `init`, chưa đủ pha, hoặc dùng state sai | Theo đúng thứ tự `init → doPhase(..., true) → generateSecret` |
| DH tính bí mật không khớp / lỗi phase | Hai bên khởi tạo `DHParameterSpec` khác nhau | Chia sẻ cùng nhóm **đã xác thực**, kiểm tra p/g |
| Không khớp output HKDF dù shared secret trùng | Salt, info, role order hoặc encoding transcript khác nhau | Xác định wire format dài/byte order/labels một cách duy nhất |
| Dùng ECDH P-256 với peer public X25519 | Đây là hai thuật toán/curve không tương thích | Dùng EC/P-256 cho ECDH, XDH/X25519 cho X25519 |
| Gửi private key/`generateSecret()` qua mạng | Nhầm shared secret với public key | Chỉ gửi **public** material theo giao thức |
| Dùng `SecretKeySpec(shared, "AES")` ngay | Không có KDF/domain separation | HKDF với salt + context; derive key cho đúng mục đích |
| Không xác thực public key | MITM tạo 2 phiên agreement riêng | Dùng TLS/authenticated handshake/PKI/pinning |
| Chia sẻ cùng `KeyAgreement` giữa threads | Engine state bị trộn giữa nhiều session | Tạo engine theo mỗi handshake |
| Reuse ephemeral private key quá lâu | Ảnh hưởng forward secrecy và unlinkability | Sinh mới per session theo protocol và xóa vật liệu cũ |
| Dùng lại nonce AES-GCM với cùng key | Phá vỡ bảo mật AEAD | Nonce allocation không trùng và lifecycle theo protocol |
| Không kiểm tra shared secret toàn zero XDH | Peer input không hợp lệ/low-order có thể tạo bí mật yếu | Tuân thủ RFC/provider validation; không chấp nhận all-zero |
| Chỉ xem `equal=true` là đã an toàn | Chưa có xác thực danh tính hoặc KDF/AEAD | Bổ sung authentication, KDF, replay protection |
| Tự xây handshake không có transcript binding | Downgrade, reflection, role confusion, unknown key-share | Dùng giao thức đã được đánh giá, ký/bind toàn transcript |

**Lưu ý output length:** X25519 trả 32 byte, X448 56 byte; **không** cho rằng 56 byte là AES-448 key. AES chỉ nhận 16, 24 hoặc 32 byte và security strength của toàn hệ thống là mức yếu nhất trong các thành phần liên quan (group, KDF, AEAD, authentication, RNG, quản lý khóa).

**Khi gặp lỗi cryptographic:** không tiếp tục dùng shared secret/key chưa hoàn chỉnh hoặc plaintext AEAD chưa xác thực. Xóa/vô hiệu phiên, trả thông báo thất bại nhất quán cho tầng trên; tránh để lỗi chi tiết trở thành oracle giúp kẻ tấn công đoán private state. Không log bí mật, đừng “tạm chuyển” sang cipher không xác thực.

## 11. Hướng dẫn lựa chọn thuật toán

- **Ứng dụng mới cần trao đổi khóa:** thường ưu tiên **X25519** nếu toàn bộ hạ tầng/protocol hỗ trợ. Nó có API rõ ràng và cơ chế trao đổi khóa hiện đại; vẫn cần authentication + KDF + AEAD.
- **Liên thông với hệ thống EC/NIST:** **ECDH P-256** là lựa chọn phổ biến với `ECGenParameterSpec("secp256r1")`, thường được hỗ trợ tốt bởi PKI và hardware/enterprise systems.
- **Yêu cầu profile 448-bit curve:** **X448** cho mức bảo mật nhắm tới cao hơn nhưng tiêu tốn tài nguyên hơn, có thể hỗ trợ kém hơn; chọn khi protocol và tương thích peer yêu cầu.
- **Liên thông DH hữu hạn truyền thống:** **DH/FFDHE** với nhóm chuẩn tin cậy, tối thiểu độ dài theo chính sách (tránh nhóm yếu/khóa ngắn). Không dùng params nhận tùy tiện từ phía chưa xác thực; FFDHE-2048 có mức security strength thấp hơn X25519/P-256.
- **Ứng dụng có dữ liệu cần giữ bí mật dài hạn trước máy tính lượng tử:** DH, ECDH, X25519, X448 đều là key agreement **cổ điển**, không chống được máy tính lượng tử có quy mô đủ lớn. Cần đánh giá giao thức **hybrid post-quantum KEM + classical agreement** theo tiêu chuẩn tương ứng; không thể chỉ tăng độ dài HKDF hay thay AES-256 để khắc phục.

Dù chọn thuật toán nào, hệ thống vẫn cần test liên thông hai endpoint, kiểm tra key validation, xác thực transcript, xử lý replay/downgrade, nonce uniqueness và key rotation. Đừng benchmark chỉ phép `generateSecret()` mà bỏ qua key generation, serialization, network latency và provider overhead.

## 12. Tự kiểm tra kiến thức

1. **DH/ECDH/X25519/X448 truyền thứ gì qua mạng?** Khóa **công khai**, không truyền private key hay shared secret.
2. **Bên Alice gọi `KeyAgreement.init` với khóa nào?** Private key của **Alice**; `doPhase` với public key của **Bob**.
3. **Vì sao `doPhase(peer, true)` dùng `true`?** Đây là **pha cuối** của thỏa thuận hai bên, có thể gọi `generateSecret()` sau đó.
4. **Hai bên DH có thể tự tạo độc lập hai nhóm p/g khác nhau không?** Không; phải dùng **cùng nhóm phù hợp** và có validation.
5. **`ECDH` P-256 có phải X25519 không?** Không. Thuật toán, đường cong và API tham số khác nhau.
6. **X25519 và X448 shared secret dài bao nhiêu?** Lần lượt **32 byte** và **56 byte**.
7. **`generateSecret()` trả về AES key đã xác thực chưa?** Chưa; đây là raw shared secret, cần **KDF + protocol context + authentication**.
8. **Dùng HKDF với cùng IKM/salt nhưng khác info thì điều gì xảy ra?** Đầu ra khác (với xác suất áp đảo), hỗ trợ phân tách domain/key purpose.
9. **Vì sao người trung gian vẫn có thể đọc khi DH tính đúng?** Vì public keys **chưa được xác thực**, attacker thay bằng khóa của mình và duy trì hai secret.
10. **X25519 + HKDF + AES-GCM tự thành TLS an toàn không?** Không; còn thiếu authentication, transcript binding, replay/downgrade protection, quản lý phiên và giao thức đầy đủ.
11. **Sau `generateSecret()` KeyAgreement có thể tái sử dụng object không?** JCA API đưa về trạng thái sau `init`, nhưng với **ephemeral DH** nên dùng private key và engine mới cho từng handshake.
12. **GCM nhận key 56 byte của X448 trực tiếp như AES-448 không?** Không; derive ra AES-128/192/256 key qua KDF.
13. **DH hữu hạn dùng nhóm 2048 bit có tương đương 2048-bit security không?** Không; modulus length không bằng security strength.
14. **Muốn bọc DEK sau ECDH nên lấy khóa nào?** Derive một **KEK** bằng HKDF với purpose label và dùng cơ chế wrap có xác thực theo protocol, không dùng shared secret thô.
15. **Dùng private ephemeral mới có tự bảo đảm forward secrecy hoàn chỉnh không?** Chưa: cần authenticated handshake, key erasure và tránh lộ secret theo threat model.

## Tài liệu chuẩn và API chính thức

- [KeyAgreement, Java SE 21: init, doPhase, generateSecret và vòng đời reset](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KeyAgreement.html)
- [KeyPairGenerator, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyPairGenerator.html)
- [DHParameterSpec và DHPublicKey](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/DHParameterSpec.html)
- [ECGenParameterSpec, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/ECGenParameterSpec.html)
- [NamedParameterSpec: X25519 và X448](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/spec/NamedParameterSpec.html)
- [Mac, Java SE 21: HmacSHA256](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html)
- [Cipher: AES/GCM/NoPadding](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
- [RFC 7748: Elliptic Curves for Security (X25519, X448)](https://www.rfc-editor.org/rfc/rfc7748.html)
- [RFC 5869: HMAC-based Extract-and-Expand Key Derivation Function (HKDF)](https://www.rfc-editor.org/rfc/rfc5869.html)
- [RFC 7919: Negotiated Finite Field Diffie-Hellman Ephemeral Parameters for TLS](https://www.rfc-editor.org/rfc/rfc7919.html)
- [RFC 8446: TLS 1.3 và authenticated key exchange](https://www.rfc-editor.org/rfc/rfc8446.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-establishment-and-wrapping-003">10.3 Chu trình init, doPhase, generateSecret và kiểm tra khóa của đối tác</a>

<details>
<summary>Xem chi tiết</summary>

# 10.3. Chu trình init, doPhase, generateSecret và kiểm tra khóa của đối tác

## 1. Vì sao cần KeyAgreement?

Khi hai bên A và B liên lạc qua một mạng có thể bị quan sát, họ cần thiết lập **cùng một bí mật chung** để từ đó dẫn xuất khóa phiên dùng cho mã hóa đối xứng hoặc bọc khóa. Nếu A và B có thể làm việc đó mà không gửi trực tiếp private key hoặc bí mật chung trên đường truyền, ta có **thỏa thuận khóa** (*key agreement*).

Trong Java/JCA/JCE, `javax.crypto.KeyAgreement` cung cấp engine cho các thuật toán như **Diffie–Hellman (DH)**, **Elliptic Curve Diffie–Hellman (ECDH)** và **X25519/X448** (thuộc họ XDH). Mô hình hai phía thường là:

- A sinh cặp `privateA/publicA`, B sinh cặp `privateB/publicB` với cùng thuật toán và tham số tương thích.
- A nhận `publicB`, dùng **privateA** để tính bí mật chung.
- B nhận `publicA`, dùng **privateB** để tính bí mật chung.
- Theo tính chất thuật toán, với khóa hợp lệ và cùng tham số, **hai phía thu được cùng giá trị** mà không cần truyền nó qua mạng.

Nhưng có ba điều không được đánh đồng:

1. **Đúng chu trình API**: `init` → `doPhase` → `generateSecret`.
2. **Khóa công khai đối tác hợp lệ về kỹ thuật**: đúng kiểu, tham số/curve, biểu diễn và các kiểm tra mật mã cần thiết.
3. **Khóa thật sự thuộc về đối tác mong muốn**: phải được **xác thực qua cơ chế tin cậy** như khóa đã pin, chứng thư được kiểm tra hoặc trao đổi khóa có xác thực.

Bước (1) và (2) **không tự bảo đảm bước (3)**. Một kẻ đứng giữa (*man-in-the-middle*, MITM) có thể thay public key trên đường truyền và tạo **hai phiên thỏa thuận khóa khác nhau** nếu hệ thống không xác thực khóa của bên kia.

**Mục tiêu bài học:** hiểu trạng thái của `KeyAgreement`, đặt đúng `lastPhase`, quản lý kết quả `generateSecret`, kiểm tra khóa của đối tác ở nhiều tầng, rồi dẫn xuất khóa đối xứng một cách an toàn thay vì coi raw shared secret là khóa AES sẵn sàng sử dụng.

## 2. Cơ chế ở cấp giao thức: bí mật chung không được gửi đi

Minh họa mô hình **hai bên**:

```text
Alice                                              Bob
-----                                              ---
(privateA, publicA)                                (privateB, publicB)
         |                                            |
         |  ----- publicA (được xác thực) ----------> |
         | <----- publicB (được xác thực) ----------- |
         |                                            |
KeyAgreement.init(privateA)               KeyAgreement.init(privateB)
doPhase(publicB, true)                     doPhase(publicA, true)
generateSecret() -> sharedA                generateSecret() -> sharedB
         |                                            |
         +------------ sharedA == sharedB -------------+
                              |
                  KDF (HKDF + ngữ cảnh)
                              |
              Khóa phiên cho AEAD / bọc khóa
```

**Public key không phải bí mật.** Tuy nhiên, public key nhận từ mạng **chưa tự đáng tin** chỉ vì parse thành công, có chuỗi Base64 đúng hoặc được `KeyAgreement.doPhase` chấp nhận.

Ở phía A, giá trị đưa vào `init` là **khóa riêng của chính A**; khóa đưa vào `doPhase` là **khóa công khai của B**. Không đưa private key của B vào `doPhase`; không gửi private key của A cho B. Hệ thống thực tế cần xác thực thông điệp chứa public keys hoặc đối chiếu chúng với một kênh đăng ký/trust đã được bảo vệ.

**Lưu ý thuật ngữ:** `KeyAgreement` thường *tính* raw shared secret; nó **không mã hóa** payload, **không tạo chữ ký số** và **không tự bọc khóa**. Sau khi xác thực trao đổi và dẫn xuất khóa phiên, ứng dụng mới có thể dùng `Cipher`/AEAD hoặc thuật toán bọc khóa phù hợp.

## 3. Vòng đời KeyAgreement trong JCA

### 3.1. Tạo engine bằng getInstance

```java
KeyAgreement ka = KeyAgreement.getInstance("X25519");
```

Cần import `javax.crypto.KeyAgreement`. Chuỗi `"X25519"` là **tên thuật toán**, không phải provider. Từ Java 17, JDK thông dụng hỗ trợ X25519, nhưng hệ thống thay provider/chính sách mật mã có thể khác; ứng dụng cần cấu hình thuật toán được phép và kiểm tra hỗ trợ.

Không dùng `"AES"` để tạo `KeyAgreement`: AES là thuật toán **mã hóa đối xứng**, không phải phép thỏa thuận khóa. Tương tự, `Ed25519` dùng để ký/xác minh, **không** được coi như khóa X25519 có thể đổi tên tùy ý.

### 3.2. init(privateKey): đặt trạng thái ban đầu

```java
ka.init(ownPrivateKey);
```

`init` chuẩn bị engine với **thông tin bí mật của bên cục bộ** và các tham số cần thiết. Với X25519, private key tương ứng với keypair X25519 đã sinh; với classic DH/ECDH cần các tham số nhóm/curve tương thích. Các overload cho phép truyền `AlgorithmParameterSpec` và/hoặc `SecureRandom` khi thuật toán/provider cần.

Lỗi cần biết:

- `InvalidKeyException` nếu đưa khóa không thích hợp: sai loại, sai thuật toán hoặc không tương thích.
- `InvalidAlgorithmParameterException` nếu sử dụng overload có tham số không phù hợp.
- Có thể phát sinh lỗi tạo engine như `NoSuchAlgorithmException` nếu thuật toán không tồn tại ở provider khả dụng.

**`init` không tự lấy public key đối tác qua mạng** và cũng không tự kiểm tra đối tác là ai. Nó đặt trạng thái của engine để xử lý giai đoạn thỏa thuận.

### 3.3. doPhase(peerPublicKey, lastPhase): xử lý từng bên tham gia

Trong thỏa thuận khóa **hai bên**, thường gọi:

```java
ka.doPhase(peerPublicKey, true);
```

- `peerPublicKey` là khóa cho giai đoạn hiện tại, thường là **public key của bên kia**.
- `lastPhase=true` cho biết **đây là giai đoạn cuối**.
- Với hai bên, gọi **một lần** và đặt `true`.
- `doPhase` trả một `Key` trung gian hoặc `null` nếu giai đoạn đó không tạo ra khóa trung gian cần dùng. Trong demo X25519 hai bên, không cần lấy giá trị trả về.

Các thuật toán đa bên có thể dùng nhiều giai đoạn, trong đó `false` được dùng trước giai đoạn cuối; nhưng **không** được tự suy đoán mọi provider/thuật toán đều hỗ trợ cấu hình nhiều bên. Với X25519/ECDH hai bên thông thường, đừng đặt `false` rồi gọi `generateSecret` như thể đã hoàn tất.

Nếu engine chưa `init`, `doPhase` có thể ném `IllegalStateException`. Khóa đối tác không phù hợp có thể gây `InvalidKeyException`. **Việc doPhase chấp nhận một public key không đồng nghĩa public key đó đã được xác thực về danh tính.**

### 3.4. generateSecret(): lấy raw shared secret

Sau khi xử lý đủ giai đoạn:

```java
byte[] rawSharedSecret = ka.generateSecret();
```

`generateSecret()` tạo **một byte[] mới** chứa bí mật chung. Đừng in ra console, gửi qua mạng, thêm vào log hoặc hardcode nó trong cấu hình. Ngay cả khi public keys công khai, bí mật chung phải được xử lý như **secret key material**.

Java 17 có các overload:

| Phương thức | Ý nghĩa |
| --- | --- |
| `generateSecret()` | Tạo `byte[]` mới chứa bí mật chung. |
| `generateSecret(byte[] target, int offset)` | Ghi secret vào buffer caller cung cấp; trả số byte đã ghi; có thể ném `ShortBufferException` nếu không đủ chỗ. |
| `generateSecret(String algorithm)` | Yêu cầu provider tạo `SecretKey` thuộc thuật toán được chỉ định; phụ thuộc việc thuật toán/provider hỗ trợ và tính tương thích của key material, có thể ném lỗi. |

**Không coi `generateSecret("AES")` là HKDF chuẩn hóa có ràng buộc phiên trên mọi provider.** Đây là overload tạo khóa theo cơ chế của engine/provider, không tự cung cấp toàn bộ salt, transcript hash, context và key separation mà một giao thức hiện đại có thể đòi hỏi. Khi thiết kế protocol mới, nên quy định **KDF rõ ràng**, ví dụ HKDF-HMAC-SHA256, theo đặc tả và thư viện được kiểm chứng.

**State sau `generateSecret`:** tài liệu `KeyAgreement` quy định engine được reset về trạng thái sau lần `init` gần nhất. Có thể gọi `doPhase`/`generateSecret` lần nữa với cùng private key, hoặc gọi `init` để thay thông tin riêng; **việc reset state không sinh một private key mới**. Nếu protocol dùng *ephemeral ECDH* để có forward secrecy, application phải thực sự sinh keypair mới theo chính sách phiên, không trông chờ reset engine tự làm vậy.

Gọi `generateSecret` khi chưa `init` hoặc khi chưa hoàn thành các `doPhase` cần thiết sẽ gây `IllegalStateException`.

### 3.5. Bảng thứ tự gọi

| Thứ tự | Hợp lệ? | Kết quả/chú ý |
| --- | --- | --- |
| `getInstance` → `init(ownPrivate)` → `doPhase(peerPublic,true)` → `generateSecret()` | **Có**, hai bên | Trả raw shared secret nếu khóa, thuật toán và các kiểm tra hợp lệ. |
| `getInstance` → `generateSecret()` | **Không** | Chưa init/chưa xử lý giai đoạn, lỗi trạng thái. |
| `init(ownPrivate)` → `generateSecret()` | **Không** | Chưa có public key đối tác; lỗi trạng thái. |
| `init(ownPrivate)` → `doPhase(peerPublic,true)` → `generateSecret()` → `generateSecret()` | **Không**, nếu thiếu giai đoạn mới | Sau lần đầu engine reset, phải nạp lại key của bên kia. |
| `init(ownPublic)` → `doPhase(peerPrivate,true)` | **Sai loại khóa** | Private key phải ở phía `init`; public key đối tác ở `doPhase`. |
| `init(ownPrivate)` → `doPhase(peerPublic,false)` → `generateSecret()` | **Không phải chu trình hai bên hoàn chỉnh** | `false` chỉ có ý nghĩa khi còn giai đoạn và thuật toán hỗ trợ. |

## 4. Kiểm tra public key đối tác: ba tầng khác nhau

**Kiểm tra public key không phải một phép kiểm đơn lẻ.** Để trả lời “đây có phải khóa đáng tin của B không?”, tách ít nhất ba tầng.

### Tầng 1 — Kiểm tra biểu diễn và input

- Dữ liệu public key có nằm trong giới hạn kích thước, format, version và encoding giao thức cho phép không?
- Nếu giao thức truyền `SubjectPublicKeyInfo` DER, dùng `X509EncodedKeySpec` với `KeyFactory` tương ứng thay vì tùy tiện parse bytes.
- Xác định **algorithm allowlist**, không để client tùy ý chọn kiểu khóa/curve từ header mà không có policy.
- Nếu đầu vào malformed, không được chấp nhận hoặc fallback sang thuật toán yếu hơn.

Java `PublicKey.getEncoded()` trong nhiều provider trả **X.509 SubjectPublicKeyInfo (SPKI)**, *không phải* một certificate X.509 đã được CA xác nhận, và *không nhất thiết là raw 32 byte X25519* của một wire protocol. `X509EncodedKeySpec` cần byte encoding dạng SPKI; nếu giao thức truyền raw X25519 public key, phải dùng cách decode đúng cấu trúc và byte order của **chính giao thức**, không bọc tùy tiện.

Tầng này chỉ trả lời “đây là một key object parse được đúng định dạng?”, **chưa** trả lời “key hợp lệ cho nhóm?” và “nó thuộc đối tác B?”.

### Tầng 2 — Kiểm tra thuật toán, tham số và giá trị khóa

**DH cổ điển:** phải tuân theo tham số nhóm `p, g` (và cấu trúc nhóm/subgroup) được duyệt, kiểm tra public value của đối tác trong miền hợp lệ và các kiểm tra subgroup khi thuật toán/nhóm yêu cầu. Đừng tự tạo nhóm số nguyên tố không rõ nguồn gốc.

**ECDH truyền thống trên curve Weierstrass** (ví dụ `secp256r1`): kiểm tra khóa đối tác đúng kiểu, **đúng curve và các tham số**, điểm không phải point at infinity, tọa độ hợp lệ, điểm nằm trên curve; với nhóm/curve có yêu cầu còn cần kiểm tra order/subgroup. Không chấp nhận arbitrary curve do bên ngoài tự chỉ định. Dùng implementation/provider có validation đúng, nhưng vẫn phải áp dụng **policy curve** ở ứng dụng; không nên chỉ so tên string `"EC"`.

**X25519:** dùng đúng tên `X25519`, loại `XECPublicKey` và tham số `NamedParameterSpec.X25519` nếu giao thức yêu cầu. Không lẫn với `X448` hay `Ed25519`. X25519 được thiết kế để xử lý biểu diễn điểm theo quy tắc riêng của RFC 7748, vì vậy **không áp dụng máy móc công thức kiểm tra điểm ECDH Weierstrass** lên public key X25519. RFC 7748 cho phép kiểm tra raw shared secret **toàn byte zero** và từ chối nếu xảy ra; ví dụ Java bên dưới có bước từ chối này. Provider có thể từ chối public key/đầu ra không hợp lệ ở `doPhase` hoặc `generateSecret`; đây là **kiểm tra hợp lệ mật mã**, không thay thế kiểm tra danh tính.

**Quan trọng:** cùng tên thuật toán hoặc cùng độ dài public key **không đủ** để chứng minh hợp lệ hoặc đáng tin. Các kiểm tra giá trị nhóm/curve cần được giao cho thư viện/provider mật mã đáng tin, theo đúng RFC/chuẩn của thuật toán; không tự bỏ qua ngoại lệ nếu provider báo lỗi.

### Tầng 3 — Xác thực khóa thuộc đúng đối tác

Ngay cả một X25519 public key hoàn toàn hợp lệ về toán học cũng có thể thuộc về **kẻ tấn công**. Cần một ràng buộc tin cậy, ví dụ:

1. **Key pinning:** ứng dụng lưu byte SPKI/fingerprint public key của đối tác từ một **quy trình đăng ký tin cậy**; lúc kết nối, so sánh đúng với khóa nhận được. Cần chiến lược rotation/thu hồi và cập nhật pin có kiểm soát.
2. **Chứng thư X.509/PKI:** kiểm tra chain đến trust anchor, danh tính/hostname/service identity, key usage, effective validity, revocation/chính sách khi cần. `certificate.getPublicKey()` đơn thuần **không thực hiện các bước tin cậy đó**.
3. **Signature trên key exchange transcript:** một identity signing key đã được xác thực ký public key tạm, nonce, danh tính vai trò và ngữ cảnh giao thức. Bên còn lại verify chữ ký **bằng key tin cậy** trước khi dùng kết quả.
4. **PSK/authenticated protocol:** tích hợp trao đổi khóa vào một giao thức đã được phân tích như TLS 1.3 (đúng cấu hình và kiểm tra chứng thư/PSK), thay vì tự chế giao thức trên raw ECDH.

**Không có kiểm tra tầng 3 thì không có peer authentication.** `KeyAgreement` một mình cung cấp khả năng tính toán bí mật chung, **không** biết đối tác tên gì, thuộc tenant nào hoặc có quyền ký hợp đồng hay không.

## 5. Demo Java 17+ chạy được: X25519 và kiểm tra khóa công khai

Bài demo tạo ba keypair: Alice, Bob và một bên thay thế khóa. Nó kiểm tra được những điểm sau:

- A và B mỗi người dùng **private key của mình**, **public key của người kia** theo chu trình `init` → `doPhase(..., true)` → `generateSecret()`.
- A và B có cùng **32 byte** raw shared secret X25519.
- Public key truyền ở dạng **SPKI encoded** được parse bằng `KeyFactory`, kiểm tra kiểu và tham số thuật toán.
- “Alice public key đáng tin” trong demo là một **pin giả lập được thiết lập ngoài đường truyền**. Public key của bên thay thế không khớp pin.
- Bob dùng public key bên thay thế sẽ tính **bí mật khác** với bí mật A–B. Điều này mô phỏng nguy cơ MITM, không phải một giao thức xác thực hoàn chỉnh.
- Nếu `generateSecret` được gọi khi chưa `doPhase`, engine từ chối do sai trạng thái.

Tạo file `KeyAgreementLifecycleDemo.java`:

```java
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.KeyAgreement;

public final class KeyAgreementLifecycleDemo {
    private static PublicKey parsePeerX25519(byte[] spki)
            throws GeneralSecurityException {
        if (spki == null || spki.length == 0 || spki.length > 1024) {
            throw new InvalidKeyException("Invalid peer key encoding size");
        }
        PublicKey result = KeyFactory.getInstance("X25519")
                .generatePublic(new X509EncodedKeySpec(spki));
        if (!(result instanceof XECPublicKey xec)
                || !(xec.getParams() instanceof NamedParameterSpec params)
                || !"X25519".equals(params.getName())) {
            throw new InvalidKeyException("Unexpected peer key parameters");
        }
        return result;
    }

    private static byte[] agree(PrivateKey ownPrivate, PublicKey peerPublic)
            throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(ownPrivate);
        agreement.doPhase(peerPublic, true);
        byte[] shared = agreement.generateSecret();
        int nonzero = 0;
        for (byte b : shared) nonzero |= b & 0xff;
        if (nonzero == 0) {
            Arrays.fill(shared, (byte) 0);
            throw new InvalidKeyException("Rejected all-zero shared secret");
        }
        return shared;
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        KeyPair alice = generator.generateKeyPair();
        KeyPair bob = generator.generateKeyPair();
        KeyPair attacker = generator.generateKeyPair();

        // In production this pin must be provisioned via a trusted channel.
        byte[] trustedAliceSpki = alice.getPublic().getEncoded();
        PublicKey aliceFromWire = parsePeerX25519(
                alice.getPublic().getEncoded());
        PublicKey bobFromWire = parsePeerX25519(
                bob.getPublic().getEncoded());
        PublicKey fakeAlice = parsePeerX25519(
                attacker.getPublic().getEncoded());

        System.out.println("Trusted peer pin matches: " +
                Arrays.equals(trustedAliceSpki, aliceFromWire.getEncoded()));
        System.out.println("Substituted peer passes pin: " +
                Arrays.equals(trustedAliceSpki, fakeAlice.getEncoded()));

        byte[] secretAlice = agree(alice.getPrivate(), bobFromWire);
        byte[] secretBob = agree(bob.getPrivate(), aliceFromWire);
        byte[] wrongSecretBob = agree(bob.getPrivate(), fakeAlice);
        try {
            System.out.println("X25519 secret bytes: " + secretAlice.length);
            System.out.println("Both parties agree: " +
                    MessageDigest.isEqual(secretAlice, secretBob));
            System.out.println("Wrong peer yields same secret: " +
                    MessageDigest.isEqual(secretAlice, wrongSecretBob));

            KeyAgreement unfinished = KeyAgreement.getInstance("X25519");
            unfinished.init(alice.getPrivate());
            boolean rejected = false;
            try {
                unfinished.generateSecret();
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            System.out.println("No doPhase rejected: " + rejected);
        } finally {
            Arrays.fill(secretAlice, (byte) 0);
            Arrays.fill(secretBob, (byte) 0);
            Arrays.fill(wrongSecretBob, (byte) 0);
        }
    }
}
```

Biên dịch và chạy (JDK 17+ có provider X25519):

```bash
javac KeyAgreementLifecycleDemo.java
java KeyAgreementLifecycleDemo
```

Stdout được kiểm chứng:

```text
Trusted peer pin matches: true
Substituted peer passes pin: false
X25519 secret bytes: 32
Both parties agree: true
Wrong peer yields same secret: false
No doPhase rejected: true
```

### 5.1. Tại sao stdout luôn theo cấu trúc này?

Các keypair được tạo ngẫu nhiên mỗi lần chạy, do đó raw public/private/shared secret **thay đổi** và không được in. Nhưng:

- Bản sao `aliceFromWire` parse từ chính SPKI của Alice sẽ khớp với pin demo `trustedAliceSpki`.
- Public key của cặp khóa bên thay thế khác pin Alice, nên xác minh pin cho `false` (với xác suất trùng key cực nhỏ về mặt toán học, không phải công cụ kiểm thử bảo mật tuyệt đối).
- Do tính chất X25519, Alice và Bob tính cùng shared secret dài 32 byte. Khi Bob tính với public key của keypair khác, kết quả thông thường không khớp secret A–B.
- Chưa gọi `doPhase` nhưng đã gọi `generateSecret` là lỗi trạng thái theo hợp đồng API.

Dòng `MessageDigest.isEqual` chỉ dùng để **chứng minh hai byte array ở demo giống/khác nhau**. Trong ứng dụng production, không cần đem raw secrets của hai bên lên mạng để so sánh; việc đó **làm lộ bí mật phiên**. Hãy dùng phương thức xác nhận phiên có xác thực (ví dụ finished message/MAC trên transcript) theo giao thức được thiết kế và kiểm chứng.

### 5.2. Điều demo kiểm tra và không kiểm tra

Hàm `parsePeerX25519` áp dụng một **giới hạn kích thước demo 1.024 byte** (đây là policy minh họa, **không** phải ngưỡng chuẩn của RFC 7748); parse DER-SPKI bằng `KeyFactory` rồi kiểm tra `XECPublicKey` có `NamedParameterSpec` là X25519. Nó **không** xác nhận key thuộc Alice/Bob; bước pin mới minh họa trust.

Đặc biệt, `trustedAliceSpki` đang lấy trực tiếp từ keypair Alice trong cùng chương trình; điều này **không tái hiện việc provision key giữa hai máy thật**. Ngoài đời, bên nhận phải có public key đáng tin từ trước/qua trust anchor, không được tạo “pin” bằng cách lưu chính public key vừa nhận từ kết nối chưa xác thực rồi tuyên bố đã chống MITM.

Việc dùng `Arrays.equals` cho **SPKI public key** là hợp lý ở minh họa vì các byte public này không phải bí mật. Đối với raw shared secrets, demo dùng `MessageDigest.isEqual` thay vì log/display. Hai mảng raw secret do application nhận được được `Arrays.fill` khi không còn cần; việc xóa này chỉ là best-effort đối với **những byte[] cụ thể**, không chứng minh mọi bản sao trong JVM/provider đã được xóa.

### 5.3. Kiểm tra all-zero shared secret

RFC 7748 cho X25519 nói rằng có thể kiểm tra kết quả toàn zero và abort để giảm rủi ro từ một số input nhỏ bậc (*small-order*). Hàm `agree` dùng phép OR qua **toàn bộ byte** thay vì dừng ở byte đầu không zero, rồi từ chối nếu tất cả bằng zero. Nếu provider từ chối key không hợp lệ ngay trong `doPhase`/`generateSecret` thì luồng cũng phải được coi là lỗi và **không** fallback sang shared secret khác.

Kiểm tra all-zero **không phải peer authentication**: một public key X25519 hợp lệ do kẻ tấn công kiểm soát vẫn có thể tạo shared secret khác zero. Phải kết hợp với tầng trust/identity trước khi coi phiên là đáng tin.

## 6. Tấn công man-in-the-middle nếu chỉ chạy KeyAgreement

Giả sử Eve đứng giữa Alice và Bob, chặn public key và gửi key của Eve thay thế:

```text
Alice                 Eve (MITM)                      Bob
  |                        |                           |
  | ---- publicAlice ----> |                           |
  |                        | -- publicEve1 ----------> |
  | <---- publicEve2 ----- |                           |
  |                        | <-------- publicBob ----- |
  |                        |                           |
secret(Alice, Eve2)    2 secret với hai bên       secret(Bob, Eve1)
  |                        |                           |
  +----- mỗi cặp tính được shared secret hợp lệ -------+
      NHƯNG Alice và Bob KHÔNG cùng một secret với nhau
```

Eve có thể dùng khóa phiên với Alice để đọc/ghi lại thông điệp, rồi tạo thông điệp mới bảo vệ bằng khóa phiên khác với Bob, nếu protocol không xác thực trao đổi khóa và không có bảo vệ bổ sung.

**Phân biệt hai kết quả:**

- `doPhase` chạy thành công: chỉ chứng minh public key tương thích thuật toán, **không chứng minh identity**.
- `generateSecret` trả nonzero bytes: chứng minh phép thỏa thuận với một public key có thể hoàn thành, **không chứng minh đối tác muốn kết nối đã tham gia**.

**Biện pháp:** xác thực public keys và các thông điệp bắt tay bằng chứng thư/chữ ký/PSK tin cậy, kiểm tra danh tính người tham gia và **ràng buộc toàn bộ transcript** (vai trò A/B, public keys, algorithm, session ID/nonce, version và ngữ cảnh) vào việc dẫn xuất/kiểm tra khóa. Trong ứng dụng thông thường, ưu tiên **TLS 1.3 hoặc protocol đã được đánh giá** thay vì phát minh giao thức trao đổi khóa riêng.

## 7. Từ raw secret sang khóa AES, MAC hoặc khóa bọc

### 7.1. Vì sao không dùng trực tiếp raw shared secret?

Bí mật thỏa thuận là **keying material**, không phải mặc nhiên một `SecretKey` đúng quy chuẩn cho mọi mục đích. Giao thức phải:

1. Xác nhận thuật toán, tham số và input hợp lệ.
2. Xác thực các public keys/ngữ cảnh theo threat model.
3. Dùng **KDF** được đặc tả để trích xuất và dẫn xuất một hoặc nhiều khóa từ raw secret.
4. Đưa **thông tin transcript/ngữ cảnh** vào quá trình dẫn xuất: tên thuật toán, phiên bản, public keys, vai trò A/B, nonces/session ID, mục đích sử dụng.
5. Dẫn xuất **khóa riêng theo chức năng**: chẳng hạn khóa AES-GCM gửi A→B, khóa AES-GCM B→A, key confirmation MAC, hoặc khóa phục vụ bọc khóa.

Cách phổ biến là **HKDF** (`Extract` và `Expand` theo RFC 5869) trên shared secret, với salt và `info` được thỏa thuận rõ. Ví dụ ở mức khái niệm (không phải mã Java chạy trực tiếp):

```text
sharedSecret = X25519(privateLocal, publicPeer)

transcriptHash = Hash(protocolVersion || algorithms || roleIds
                   || publicAlice || publicBob || nonces || context)

PRK = HKDF-Extract(salt, sharedSecret)
clientWriteKey = HKDF-Expand(PRK, "client_write" || transcriptHash, keyLen)
serverWriteKey = HKDF-Expand(PRK, "server_write" || transcriptHash, keyLen)

Xóa raw sharedSecret / PRK tạm do ứng dụng sở hữu sau khi dùng.
```

Các chuỗi trong dấu ngoặc kép ở đây là **nhãn mục đích minh họa**, không phải format chuẩn của TLS hay bất kỳ giao thức hiện hữu nào. Trong thực tế, phải dùng encoding không mơ hồ và định nghĩa rõ `salt`/`info`, hướng khóa, version, độ dài khóa, lựa chọn AEAD và các trạng thái chống replay. Nếu chưa có giao thức được kiểm chứng, đừng tự triển khai HKDF/handshake chỉ dựa trên sơ đồ này.

Java 17 **không có API HKDF chung bắt buộc mọi provider phải cung cấp**. Có thể sử dụng thư viện mật mã được duyệt để thực hiện HKDF đúng chuẩn hoặc một giao thức chuẩn đã tích hợp sẵn. Đừng dùng `new SecretKeySpec(rawSharedSecret, "AES")` như một giải pháp mặc định cho key schedule của một giao thức mới.

### 7.2. Liên hệ với bọc khóa

Sau khi đã có **key-encryption key (KEK)** được dẫn xuất đúng ngữ cảnh từ thỏa thuận khóa, ứng dụng có thể dùng một thuật toán **key wrap** hoặc AEAD được chỉ định để bảo vệ **data-encryption key (DEK)**, rồi gửi dữ liệu đã bọc cho phía kia.

`KeyAgreement.generateSecret()` **không tự thực hiện wrap/unwrap**, không tạo ciphertext và không thay thế `Cipher`. Việc unwrap thành công phải được kết hợp với xác thực peer/key transcript và chính sách sử dụng khóa; một khóa được dẫn xuất đúng mật mã nhưng thuộc nhầm peer vẫn có thể gây lỗi an ninh nghiêm trọng.

## 8. Giữ đúng trạng thái, thời hạn khóa và phân biệt các loại key

### 8.1. Ephemeral và static private key

- **Ephemeral keypair:** sinh cho phiên/lượt handshake; sau khi xong, hạn chế tái sử dụng và dọn references/buffers theo khả năng provider. Đây là thành phần quan trọng của nhiều giao thức có forward secrecy.
- **Static keypair:** có thể phục vụ nhận dạng/thỏa thuận nhiều phiên tùy thiết kế; cần quản lý vòng đời, bảo vệ private key và đánh giá lại forward secrecy.
- **Signing key ≠ key-agreement key:** Ed25519 dùng `Signature`; X25519 dùng `KeyAgreement`. Không nên “đổi nhãn” hoặc tái dùng cùng vật liệu khóa giữa các thuật toán cho các mục đích khác nhau mà thiếu đặc tả được phân tích.

**Forward secrecy** không xuất hiện chỉ nhờ gọi `generateSecret`. Nó phụ thuộc giao thức, cách kết hợp khóa ephemeral và xác thực, việc tiêu hủy private ephemeral và những giả định về an ninh của thuật toán.

### 8.2. Không chia sẻ mutable engine qua thread

`KeyAgreement` là đối tượng trạng thái: `init` và `doPhase` thay đổi tiến trình xử lý. Đừng dùng **một instance** chung cho nhiều session đồng thời hoặc các peer khác nhau rồi coi rằng engine tự cô lập. Mỗi handshake nên có engine và key material riêng. Sau `generateSecret`, muốn chạy lượt mới phải gọi `doPhase` (và nhiều khi `init` với key mới) theo thiết kế.

### 8.3. Không log private key hoặc shared secret

Có thể log metadata không nhạy cảm (algorithm, key identifier, kết quả chấp nhận/từ chối, lỗi đã làm sạch) nhưng không log `privateKey.getEncoded()`, raw secret, session key, KDF intermediate key hoặc request payload nhạy cảm. Nếu key được export thành `byte[]`, wipe bản sao do caller sở hữu sau khi dùng; việc này vẫn **không chứng minh** provider/GC/JVM đã xóa mọi vết trong memory.

### 8.4. Kiểm tra keyId không phải xác thực

Trường `keyId` được gửi qua mạng có thể dùng để **tìm** public key trong registry tin cậy. Nhưng bản thân chuỗi `keyId` có thể do attacker đặt; chỉ chấp nhận key khi registry, trust policy, authentication và authorization cùng khớp. Cần chống nhầm tenant, nhầm mục đích ký/thỏa thuận, nhầm algorithm và tấn công downgrade.

## 9. Các lỗi Java thường gặp và cách xử lý

| Triệu chứng | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| `NoSuchAlgorithmException` tại `getInstance("X25519")` | Provider/JDK không hỗ trợ hoặc policy không cho phép | Chọn JDK/provider đã được kiểm tra; cấu hình danh sách thuật toán được duyệt. |
| `InvalidKeyException` tại `init` | Đưa public key thay private key, sai loại hoặc sai tham số | Chỉ đưa private key cục bộ thuộc đúng thuật toán. |
| `InvalidKeyException` tại `doPhase` | Peer key sai thuật toán/curve, input không hợp lệ hoặc provider từ chối | Kiểm tra DER/curve/type/policy và từ chối; không tắt validation. |
| `IllegalStateException` tại `doPhase` | Engine chưa init | Bắt đầu bằng `init`. |
| `IllegalStateException` tại `generateSecret` | Chưa init hoặc chưa hoàn tất giai đoạn | Hai bên dùng `doPhase(peer,true)` trước. |
| `ShortBufferException` từ `generateSecret(buffer,offset)` | Buffer caller quá nhỏ | Dùng buffer đủ cỡ hoặc `generateSecret()` tạo byte[] mới; không dùng secret nửa chừng. |
| `InvalidKeySpecException` từ `KeyFactory.generatePublic` | SPKI DER sai định dạng hoặc key không parse được | Từ chối dữ liệu malformed; không cố đoán hay bỏ qua lỗi. |
| Secret khác nhau giữa hai phía | Dùng sai public/private, peer bị thay thế, sai group hoặc keypair không khớp | Kiểm tra identity/trust của peer public key và transcript; không truyền secrets để so sánh trực tiếp. |
| Shared secret toàn zero trong X25519 | Peer public key có thể liên quan tới input bậc nhỏ hoặc bị thay thế | Từ chối shared secret và phiên; để provider thực hiện validation theo RFC. |
| Tạo AES key trực tiếp từ raw secret | Thiếu KDF, key separation và ràng buộc phiên | Dùng KDF được thiết kế theo protocol, ví dụ HKDF. |
| Handshake chạy được nhưng MITM vẫn thành công | Chỉ validate format/curve, không xác thực identity | Pin/trust certificate, signature hoặc dùng TLS có xác thực. |
| Reuse engine/private key dù mong forward secrecy | Hiểu sai việc `generateSecret` reset state | Sinh ephemeral keypair mới theo session/policy. |

**Phân biệt lỗi kỹ thuật với kết luận bảo mật:** từ chối input không hợp lệ là đúng. Nhưng nếu một public key giả hoàn toàn hợp lệ và `doPhase` không báo lỗi, kết quả đó **không** cho phép bỏ qua bước xác thực nguồn khóa.

## 10. Tình huống before–after: kiểm tra đối tác đúng cách

### Before: chỉ parse key và hoàn thành thỏa thuận

```text
Nhận bytes publicKey từ socket
-> KeyFactory.generatePublic(bytes)
-> KeyAgreement.init(myPrivate)
-> KeyAgreement.doPhase(peerPublic,true)
-> byte[] secret = generateSecret()
-> Dùng secret như khóa mã hóa
```

Vấn đề: public key có thể do attacker thay thế; thiếu xác thực danh tính, thiếu KDF, thiếu ngữ cảnh protocol và thiếu key separation. Phép thỏa thuận có thể hoàn tất với kẻ tấn công.

### After: xác minh cả cấu trúc và nguồn khóa

```text
Nhận envelope handshake có kích thước/format giới hạn
-> Kiểm tra version + algorithm allowlist
-> Parse peerPublic theo encoding được quy định
-> Kiểm tra key type, tham số, curve/group và giá trị theo chuẩn/provider
-> Xác thực peer key qua pin/truststore/chữ ký transcript/PSK
-> Xác minh role/identity/scope/tenant và chống downgrade
-> Sinh private ephemeral, KeyAgreement.init(privateLocal)
-> doPhase(authenticatedPeerPublic,true) -> generateSecret()
-> Từ chối output bất thường như all-zero X25519 (nếu provider chưa từ chối)
-> KDF ràng buộc transcript + derive session keys tách biệt
-> Key confirmation/finished message theo protocol
-> Chỉ khi handshake và policy hợp lệ mới xử lý payload
```

**Đây là lưu đồ khái niệm, không phải protocol hoàn chỉnh.** Việc xác thực các khóa và nội dung transcript có thể có thứ tự/choreography khác nhau trong TLS và các giao thức chuẩn. Ứng dụng nên dựa trên một giao thức đã có thiết kế an ninh đầy đủ, không tự ráp các bước thành giao thức tùy ý.

## 11. Câu hỏi kiểm tra và bài tập thực hành

1. Hai bên DH/ECDH cần truyền những gì để cùng tính shared secret? Vì sao không gửi private key?
2. Trong `KeyAgreement`, `init` nhận khóa nào và `doPhase` nhận khóa nào? Nếu đảo ngược, lỗi có thể xuất hiện ở đâu?
3. Với thỏa thuận khóa hai bên, tại sao `doPhase(peer,true)` thay vì `false`? Khi nào `false` có thể được dùng?
4. `generateSecret` trả dữ liệu gì? Có tự mã hóa message, xác thực peer hoặc tạo AES key với KDF hoàn chỉnh không?
5. Sau `generateSecret`, engine ở trạng thái nào? Nó có tự tạo ephemeral private key mới không?
6. Vì sao `KeyFactory.generatePublic` thành công **không chứng minh** key thuộc server mong đợi?
7. So sánh những kiểm tra cần thiết với classic DH, ECDH trên `secp256r1` và X25519; điều gì khác nhau về group/point validation?
8. Nếu Alice và Bob bị MITM đổi public keys, liệu `doPhase` có nhất thiết ném exception không? Vì sao?
9. Pin một public key bằng cách lưu ngay khóa vừa nhận trên kênh chưa tin cậy có bảo vệ được lần kết nối đầu tiên không? Cần một nguồn trust nào?
10. Tại sao nên kết hợp raw shared secret với HKDF và transcript để dẫn xuất các khóa có mục đích riêng?
11. Vì sao kiểm tra all-zero của X25519 không chứng minh khóa đến từ đúng đối tác?
12. Muốn dùng kết quả thỏa thuận cho **key wrapping**, bước nào thuộc `KeyAgreement`, bước nào thuộc KDF và `Cipher`/key-wrap algorithm?

**Bài tập A:** chạy `KeyAgreementLifecycleDemo`. Đổi public key của Bob bằng một keypair X25519 mới, kiểm tra secret Alice/Bob không còn khớp, nhưng engine có thể vẫn hoàn thành phép thỏa thuận với key mới. Giải thích vì sao chỉ kiểm tra thuật toán không đủ.

**Bài tập B:** thử gọi `generateSecret` trước `doPhase` và sau khi đã gọi `generateSecret` lần đầu mà chưa thực hiện phase mới. Quan sát `IllegalStateException` theo vòng đời của engine.

**Bài tập C:** mô phỏng registry chứa SPKI của đối tác được provision bằng kênh đáng tin; kiểm tra public key hợp lệ về format nhưng **không trùng pin** bị từ chối trước khi dùng secret. Thử dùng certificate chain có trust anchor phù hợp trong một bài lab riêng để thấy kiểm tra trust khác với `KeyFactory` parse.

**Bài tập D:** thiết kế transcript có protocol version, tên thuật toán, role A/B, public key của cả hai và session nonce. Đặc tả serialization không mơ hồ cùng quy tắc HKDF `info`; phân biệt key A→B và B→A; kiểm thử rằng đổi bất kỳ trường ngữ cảnh nào được xác thực sẽ khiến kết quả xác nhận phiên không khớp.

## 12. Tài liệu chính thức

- [KeyAgreement (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyAgreement.html): `getInstance`, `init`, `doPhase`, `generateSecret`, `lastPhase` và hành vi reset state.
- [KeyFactory (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyFactory.html): dựng public key từ `KeySpec`, kiểm tra format.
- [X509EncodedKeySpec (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/X509EncodedKeySpec.html): public key encoded theo dạng X.509 SubjectPublicKeyInfo.
- [XECPublicKey (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/interfaces/XECPublicKey.html): public key cho họ XEC/XDH, tham số curve và tọa độ.
- [NamedParameterSpec (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/spec/NamedParameterSpec.html): tên tham số `X25519`, `X448` và các nhóm thuật toán liên quan.
- [RFC 7748 — Elliptic Curves for Security](https://www.rfc-editor.org/rfc/rfc7748): X25519/X448, Diffie–Hellman, byte representation, các trường hợp small-order và all-zero shared secret.
- [RFC 5869 — HMAC-based Extract-and-Expand Key Derivation Function](https://www.rfc-editor.org/rfc/rfc5869): HKDF-Extract, HKDF-Expand và mục đích tách khóa.
- [Java Security Standard Algorithm Names (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): tên thuật toán `X25519`, `ECDH` và các chuẩn khóa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-establishment-and-wrapping-004">10.4 Bí mật chung, bước dẫn xuất khóa, ngữ cảnh sử dụng và tách khóa theo mục đích</a>

<details>
<summary>Xem chi tiết</summary>

# 10.4 Bí mật chung, bước dẫn xuất khóa, ngữ cảnh sử dụng và tách khóa theo mục đích

Trong một giao thức mã hóa, hai bên có thể thực hiện thỏa thuận khóa như **X25519/ECDH** và thu được cùng một **bí mật chung (shared secret)**. Nhưng từ việc có cùng bí mật đó đến việc dùng an toàn các khóa cho **mã hóa hai chiều, MAC, bọc khóa dữ liệu (key wrapping)** và những mục đích khác còn một bước bắt buộc: **dẫn xuất khóa (key derivation) với ngữ cảnh và nhãn mục đích đúng**.

Không nên lấy thẳng các byte shared secret làm khóa AES, không cắt tùy ý thành nhiều khóa, và càng không sử dụng cùng một khóa cho nhiều thuật toán/hướng truyền. Bài học đi sâu vào **HKDF Extract/Expand, transcript/context binding, domain separation, phân chia vai trò gửi–nhận, phân biệt DEK/KEK**, cùng ví dụ Java 17+ chạy độc lập.

## 1. Mục tiêu và các thuật ngữ

Khi học xong, bạn có thể:

1. Mô tả vì sao **key agreement không đồng nghĩa đã có session key an toàn** và không tự xác thực danh tính.
2. Phân biệt **shared secret/IKM**, **PRK** sau HKDF-Extract và **OKM** sau HKDF-Expand; hiểu vai trò của `salt` và `info`.
3. Ràng buộc khóa với **phiên bản giao thức, thuật toán, transcript, vai trò, kênh, chiều truyền và mục đích** để tránh sử dụng nhầm khóa.
4. Tạo **khóa AES-GCM theo chiều c2s/s2c**, khóa HMAC và **key-encryption key (KEK)** tách biệt; biết khi nào cần dùng AES key wrap.
5. Triển khai ví dụ X25519 + HKDF-HMAC-SHA256 + AES-GCM + AESWrap trong Java 17+, kiểm chứng được đầu ra và những trường hợp thất bại.
6. Nhận diện lỗi MITM, sai ngữ cảnh, trùng nhãn, nonce reuse, replay, rekey, key confirmation và quản lý vòng đời secret.

Thuật ngữ chính:

| Thuật ngữ | Ý nghĩa | Có cần bí mật? |
| --- | --- | --- |
| **ECDH / X25519** | Cơ chế thỏa thuận bí mật từ private key của một bên và public key của bên kia | Private key phải bí mật; public key có thể công khai |
| **Z / shared secret** | Đầu ra raw của thỏa thuận khóa; hai bên hợp lệ thu cùng byte | **Có**; không phát tán hoặc log |
| **IKM (Input Keying Material)** | Vật liệu bí mật đưa vào HKDF-Extract, thường là shared secret | **Có** |
| **PRK (Pseudorandom Key)** | Khóa giả ngẫu nhiên do HKDF-Extract tạo ra | **Có**; là bí mật trung gian |
| **OKM (Output Keying Material)** | Byte khóa đã dẫn xuất theo `info`, dùng làm traffic key, MAC key, KEK... | **Có** |
| **Salt của HKDF** | Tham số đầu vào Extract; có thể công khai nhưng phải theo giao thức | Không cần bí mật |
| **Info/context** | Nhãn và ngữ cảnh dùng trong Expand để tạo các khóa tách biệt | Thường công khai, nhưng phải nhất quán và được ràng buộc đúng |
| **DEK** | Data Encryption Key: mã hóa dữ liệu thực | **Có** |
| **KEK** | Key Encryption Key: dùng bọc/bảo vệ một DEK hoặc khóa khác | **Có**, phải cách ly mục đích |

**Điểm phân biệt:** HKDF không làm mạnh lên một password yếu thành shared secret có entropy cao; password cần **password KDF/hash** chuyên dụng. HKDF thích hợp cho đầu vào đã có độ ngẫu nhiên/bí mật phù hợp như ECDH secret hoặc một secret master được sinh an toàn.

## 2. Đường đi từ thỏa thuận khóa đến các khóa ứng dụng

Sơ đồ tổng thể:

~~~text
Client                                       Server
  private a                                  private b
  public A = X25519(a, G)                    public B = X25519(b, G)
       \                                     /
        ---- trao đổi public keys A, B ----
       /                                     \
  Zc = X25519(a, B)                     Zs = X25519(b, A)
                    Zc == Zs
                         |
                HKDF-Extract(salt, Z)
                         |
                        PRK
                         |
    +--------------------+-------------------------+
    |                    |                         |
HKDF-Expand(         HKDF-Expand(             HKDF-Expand(
  info="c2s/enc")     info="s2c/enc")          info="wrap/kek")
    |                    |                         |
 client→server key   server→client key             KEK
                         |
               ...MAC/confirm/export keys riêng...
~~~

Ở cấp API, đừng coi `Z` là “khóa AES đã sẵn sàng” dù nó dài 32 byte với X25519. Độ dài đúng **không có nghĩa** byte đã được chuẩn hóa, gắn ngữ cảnh, ràng buộc danh tính hay có thuộc tính thích hợp cho mọi primitive. Đặc biệt, cùng một `Z` có thể xuất hiện ở các mục đích/giao thức khác nhau khi thiết kế thiếu domain separation.

**Key schedule** là tập quy tắc chính xác về cách sinh, đặt tên, phân phát và sử dụng khóa: từ đầu vào bí mật, transcript, vai trò đến thuật toán và thời hạn sử dụng. Một key schedule đúng cần được hai bên tính **giống hệt về byte**, không chỉ “cùng gọi HKDF”.

## 3. Vì sao shared secret không nên dùng trực tiếp?

### 3.1. Shared secret là vật liệu đầu vào, không phải khóa đa năng

Ví dụ X25519 trả một chuỗi byte 32 byte qua `KeyAgreement.generateSecret()`. Dù 32 byte trông vừa đủ cho AES-256, vẫn cần làm rõ:

- **Bí mật đó thuộc phiên nào?** Nếu dùng lại trong nhiều ngữ cảnh, khóa sẽ không tự tách theo phiên/giao thức.
- **Hai vai trò đối xứng hay có hướng?** Cùng một khóa c2s và s2c có thể dẫn đến rủi ro nonce trùng nếu cách phân phối nonce không được phối hợp.
- **Khóa sẽ dùng cho mục đích nào?** Encryption, MAC, xác thực handshake và key wrapping có yêu cầu và vòng đời khác.
- **Thuật toán nào đang được thương lượng?** Một suite mới không nên vô tình tạo cùng khóa với suite cũ.
- **Ngữ cảnh/đối tượng nào được bảo vệ?** Nếu khóa dùng đa tenant, đa channel hoặc nhiều loại thông điệp, cần tránh cross-context/key-confusion.

Dùng HKDF giúp **chuẩn hóa vật liệu, hỗ trợ xuất nhiều khóa với nhãn riêng và ràng buộc vào ngữ cảnh**. KDF không sửa được các lỗi như lộ private key, public key giả hoặc cấu hình giao thức thiếu xác thực.

### 3.2. Điều gì xảy ra nếu chỉ cắt secret?

Sai lầm ví dụ:

~~~java
// Không nên làm kiểu này:
byte[] raw = agreement.generateSecret();
byte[] encryptionKey = Arrays.copyOfRange(raw, 0, 16);
byte[] macKey = Arrays.copyOfRange(raw, 16, 32);
~~~

Đây không phải key schedule đã thẩm định. Hai đoạn không có nhãn mục đích, không được dẫn xuất theo transcript/role, và bạn đã tự giả định rằng các đoạn byte raw có thuộc tính độc lập phù hợp với mọi thuật toán. Cách đúng là **HKDF-Extract một lần**, sau đó **HKDF-Expand có nhãn đầy đủ và chiều dài riêng** cho từng khóa.

Nếu giao thức dùng một khóa làm đầu vào cho nhiều KDF khác nhau mà không có domain separation, có nguy cơ lặp đầu ra hoặc key confusion. Đừng tự thiết kế chuỗi `SHA-256(Z + "enc")` theo thói quen; dùng HKDF hoặc KDF chuẩn theo đặc tả.

## 4. HKDF-HMAC-SHA256: Extract và Expand

**HKDF** (HMAC-based Extract-and-Expand Key Derivation Function, RFC 5869) có hai giai đoạn:

### 4.1. Extract: chuẩn hóa bí mật đầu vào

~~~text
PRK = HKDF-Extract(salt, IKM)
    = HMAC-Hash(key = salt, message = IKM)
~~~

Với `HmacSHA256`, PRK dài **32 byte**. `salt` có thể công khai, không phải mật khẩu/pepper và không cần giữ bí mật. RFC 5869 cho phép salt vắng mặt bằng cách sử dụng chuỗi `HashLen` byte 0; tuy nhiên khi giao thức quy định salt, **hai bên phải sử dụng cùng byte**, không tùy tiện thay nó bằng chuỗi rỗng, timestamp hay digest khác.

Salt có thể là giá trị công khai theo handshake hoặc được dẫn xuất theo đặc tả từ các thành phần transcript; ví dụ phía dưới chọn một giá trị SHA-256 có nhãn từ transcript để hai bên tính giống nhau. **Salt không xác thực danh tính**, không thay kiểm chữ ký/certificate hoặc chống MITM.

Mục đích của Extract là đưa IKM có thể có cấu trúc vào PRK phù hợp cho bước Expand, không tăng entropy thực chất của một đầu vào yếu. Raw ECDH secret vẫn cần được xử lý theo quy tắc thuật toán, kiểm đầu vào public key và trường hợp shared secret bất thường theo giao thức/provider.

### 4.2. Expand: tạo khóa theo mục đích

~~~text
OKM = HKDF-Expand(PRK, info, L)

T(0) = empty
T(1) = HMAC(PRK, T(0) || info || 0x01)
T(2) = HMAC(PRK, T(1) || info || 0x02)
...
OKM  = first L bytes of T(1) || T(2) || ...
~~~

Trong đó:

- `PRK`: bí mật đầu vào của Expand, chỉ nằm phía các bên được phép.
- `info`: byte mô tả ngữ cảnh và mục đích xuất khóa, ví dụ `"c2s/aes-gcm"`; **không phải khóa bí mật**.
- `L`: độ dài đầu ra **tính bằng byte**, có giới hạn tối đa `255 × HashLen`; với SHA-256 là **8160 byte**. Một traffic key AES-128 cần 16 byte, HMAC-SHA256 thường dùng khóa được thiết kế với độ dài phù hợp như 32 byte.
- Các block có counter tăng từ **`0x01`**, không phải chuỗi ASCII `"1"` hoặc số nguyên 4 byte.

Nếu cùng `PRK`, `info` và `L`, HKDF Expand **trả cùng byte**. Đó là tính chất xác định để cả hai bên làm việc. Muốn khóa khác cho mục đích khác, phải dùng **info/nhãn khác nhau**; PRK không được đưa lên client logs hoặc lưu cùng ciphertext công khai.

### 4.3. Phân biệt HKDF với PBKDF2

| HKDF | PBKDF2/Argon2id/scrypt |
| --- | --- |
| Dùng khi đã có **key material bí mật đủ mạnh**, như X25519 secret hoặc random master key | Dùng cho **mật khẩu do con người tạo**, thường entropy thấp |
| Mục tiêu extract/expand và tách khóa theo ngữ cảnh | Mục tiêu làm chậm/đắt việc đoán password offline |
| `info` và nhãn mục đích giữ vai trò quan trọng | Salt, cost và cơ chế password hashing là trọng tâm |
| Không thể tự làm một password dễ đoán thành secret chống brute force | Được thiết kế để tăng chi phí tấn công đoán password |

Không dùng HKDF đơn lẻ làm password hashing mới, và không cần tự chạy PBKDF2 trên ECDH secret theo thói quen nếu giao thức đã quy định HKDF.

## 5. `info`, transcript và context binding

Một thiết kế trưởng thành cần thống nhất **không chỉ thuật toán HKDF, mà cả byte chính xác của `info` và salt**. Ví dụ một `info` có thể chứa:

| Thành phần | Tại sao quan trọng? |
| --- | --- |
| **Protocol ID/version** | Tránh vô tình dùng cùng key trong các protocol hoặc version khác nhau |
| **Algorithm suite** | Gắn khóa với lựa chọn X25519, HKDF-SHA256, AES-GCM/AEAD mode |
| **Handshake transcript hash** | Ràng buộc với thông điệp thương lượng, public keys, nonces, role và các trường đã định nghĩa |
| **Identity hoặc peer binding** | Phân biệt giao tiếp với đúng đối tác đã xác thực |
| **Hướng truyền/role** | `c2s` khác `s2c`, initiator khác responder |
| **Purpose label** | `enc`, `mac`, `wrap`, `confirm` tạo output riêng |
| **Tenant/channel/record context** | Chặn nhầm khóa giữa các miền sử dụng nếu nghiệp vụ cần |
| **Epoch/rekey counter** | Chuyển sang khóa mới có ngữ cảnh/chu kỳ rõ ràng |

**Đừng nối string một cách mơ hồ**, như:

~~~text
info = tenant + ":" + channel + ":" + purpose

("a:b", "c", "enc") và ("a", "b:c", "enc")
  → có thể tạo cùng chuỗi nếu chỉ nối bằng dấu ":"
~~~

Thay vào đó, quy định một binary encoding **không nhập nhằng**: mỗi trường có độ dài cố định hay tiền tố length, thứ tự trường, charset UTF-8, version, dạng ID chuẩn. Ví dụ Java dưới dùng `DataOutputStream.writeInt(length)` (big-endian 4 byte) rồi ghi đúng byte trường đó.

### 5.1. Handshake transcript là gì?

Một transcript là trình tự **chính xác các thông điệp/giá trị của phiên thỏa thuận khóa**, ví dụ version, suite, hai public keys, identities đã được xác thực, nonce và các lựa chọn thương lượng. Hai bên tính cùng một `transcriptHash` từ byte đã canonicalize, rồi gắn nó vào key schedule.

~~~text
transcriptBytes = encode(
    protocol/version,
    offered/chosen cipher suite,
    initiator role and public key,
    responder role and public key,
    relevant identities/nonces ...
)
transcriptHash = SHA-256(transcriptBytes)
info = encode("key-schedule/v1", suite, transcriptHash, purpose)
~~~

**Đừng nhầm:** gắn transcript hash vào HKDF **không tự xác thực transcript**. Nếu attacker kiểm soát quá trình trao đổi và hai bên không xác minh công khai/danh tính, họ vẫn có thể thực hiện hai phiên X25519 khác nhau rồi ngồi giữa. Transcript binding hỗ trợ chống key confusion và làm nền cho bước xác thực, nhưng **phải có cơ chế xác thực thật** (TLS đã cấu hình đúng, signature trên transcript, PSK authentication hoặc giao thức AKE đã được thẩm định).

### 5.2. Một handshake có thể cần key confirmation

Hai bên tính cùng shared secret không có nghĩa bên kia đã thực sự nhận đúng thông điệp và có cùng khóa. Giao thức có thể sử dụng **handshake Finished/confirmation MAC** từ khóa chuyên dụng, ràng buộc với transcript **đúng chiều**. Điều này phát hiện mismatch và xác nhận bên kia biết secret hiện tại, nhưng **nếu không có xác thực danh tính trước/đúng trong giao thức**, key confirmation **không ngăn MITM** giả lập hai phiên.

Không dùng traffic key AES-GCM để ký tùy tiện các thông điệp Finished; nếu cần confirmation key hãy **dẫn xuất nhãn `confirm` riêng**, hoặc dùng key schedule chuẩn của giao thức như TLS 1.3 thay vì tự ghép.

## 6. Tách khóa theo hướng và theo mục đích

Từ một PRK của phiên, tạo **đầu ra khác nhãn**:

~~~text
K_c2s_enc   = HKDF-Expand(PRK, info("c2s/aes-gcm"),     16)
K_s2c_enc   = HKDF-Expand(PRK, info("s2c/aes-gcm"),     16)
K_wrap      = HKDF-Expand(PRK, info("wrap/aes-kw"),     16)
K_hmac      = HKDF-Expand(PRK, info("auth/hmac-sha256"), 32)
K_confirm_c = HKDF-Expand(PRK, info("confirm/client"),   32)
K_confirm_s = HKDF-Expand(PRK, info("confirm/server"),   32)
~~~

Đây là **minh họa nhãn** cho một giao thức giả định. Trong thiết kế thực, hai bên phải theo một đặc tả duy nhất và **không mở thêm khóa/mục đích tùy hứng**. HKDF có thể tạo các giá trị khác nhau theo nhãn dưới giả định HMAC hoạt động như PRF; không có lời hứa toán học tuyệt đối “không bao giờ trùng”, nhưng xác suất trùng ngoài ý muốn khi dùng đúng kích thước là cực nhỏ.

| Key | Bên sử dụng | Primitive | Mục đích |
| --- | --- | --- | --- |
| `K_c2s_enc` | Client encrypt → Server decrypt | AES-GCM | Traffic client → server |
| `K_s2c_enc` | Server encrypt → Client decrypt | AES-GCM | Traffic server → client |
| `K_wrap` | Bên được phép bọc/mở DEK | AES Key Wrap / KEK | Bảo vệ khóa dữ liệu khác |
| `K_hmac` | Bên cần xác thực thông điệp ngoài AEAD theo giao thức | HMAC-SHA256 | Xác thực một loại message riêng, không mặc định dùng chung với GCM |
| `K_confirm_*` | Bên có trách nhiệm xác nhận handshake | MAC/protocol Finished | Kiểm tra handshake với nhãn/hướng riêng |

**Định danh hướng truyền:** Client và server phải **cùng hiểu** `c2s`/`s2c`, không phải mỗi bên tự gắn nhãn `"send"` rồi cho ra cùng khóa. Trong một kết nối peer-to-peer không có client/server cố định, đặt role `initiator`/`responder` hoặc quy tắc sắp xếp được xác thực. Nếu hai bên tự đảo role, key mismatch hoặc key reflection có thể xảy ra.

**Tách theo mục đích không đồng nghĩa tách theo nonce:** Dù có hai AES-GCM keys khác nhau, **mỗi key vẫn phải dùng nonce duy nhất cho từng lần mã hóa** dưới chính key đó. Nonce/replay sequencing là bài toán tiếp theo. Nếu dẫn xuất cả nonce base từ secret, phải dùng nhãn **`iv` khác `key`**, cộng sequence number theo giao thức đã được thẩm định.

### 6.1. Key lifecycle và rekey

Không nên dùng một traffic key cho số lượng message vô hạn. Hệ thống cần chính sách giới hạn theo thuật toán và mô hình nonce, ví dụ:

- Cấp nonce 96 bit duy nhất cho mỗi lần AES-GCM encrypt dưới một key; phối hợp counter giữa nhiều luồng/process hoặc dùng chiến lược nonce đã được thẩm định.
- Đếm/giới hạn số bản tin và lượng dữ liệu dưới cùng traffic key, xoay khóa theo chính sách an toàn.
- Nếu rekey từ secret trước, dùng **KDF epoch/sequence label** đã định nghĩa, thay đổi ngữ cảnh để không tái xuất cùng key; tránh rollback key.
- Quản lý retention/zeroization của secret cũ khi không còn cần để decrypt, đồng thời đáp ứng yêu cầu vận hành/khôi phục.
- Nếu dùng lại một master secret để bọc DEK lưu trữ, **vòng đời KEK khác traffic key**; không tùy tiện buộc lifetime của cả hai chỉ vì chúng cùng được dẫn xuất từ một PRK.

## 7. Shared secret có xác thực không? Rủi ro MITM

**X25519/ECDH thuần túy là thỏa thuận bí mật, không có chữ ký số.** Hai bên có thể tạo cùng một secret nếu public key đầu vào thật thuộc về đối phương, nhưng một attacker trên kênh trao đổi có thể tráo public keys:

~~~text
Alice ---- A_pub ----> Mallory ---- M1_pub ----> Bob
Alice <--- M2_pub ---- Mallory <--- B_pub ----- Bob

Alice ↔ Mallory: Z1 (bí mật của kết nối giả)
Mallory ↔ Bob:   Z2 (bí mật của kết nối giả)
Alice và Bob không trực tiếp có cùng shared secret.
Mallory có thể giải mã rồi mã hóa lại dữ liệu giữa hai phía.
~~~

Trong mô hình này, HKDF chạy đúng và key separation hoàn hảo **không ngăn attacker** nếu không xác minh danh tính công khai của bên kia. Cần một **authenticated key exchange (AKE)**: chữ ký trên transcript với khóa tin cậy, certificate xác minh đúng danh tính, PSK đã được phân phối an toàn hoặc một giao thức thỏa thuận khóa có xác thực được đánh giá độc lập. Đối với ứng dụng mạng, **TLS 1.3** đã triển khai key schedule và xác thực; thường nên dùng TLS thay vì tự xây handshake X25519.

Ngoài MITM, còn những kiểm tra quan trọng:

- Dữ liệu public key nhập từ mạng phải **được parse/validate đúng thuật toán**, không chấp nhận trộn curve/thuật toán, độ dài sai hoặc giá trị đặc biệt gây secret không an toàn theo chuẩn/provider.
- Với X25519, các triển khai/giao thức cần giải quyết trường hợp **low-order public input / all-zero shared secret** theo quy tắc thuật toán. Hãy dựa vào kiểm tra của provider/thư viện đáng tin và yêu cầu đặc tả, không coi chỉ cần public key parse được là đủ.
- Nếu private key dài hạn được dùng cho nhiều phiên, cân nhắc **forward secrecy**: khóa ephemeral riêng cho phiên thường là yếu tố quan trọng trong giao thức có xác thực.
- Xác thực identity không có nghĩa có quyền truy cập tài nguyên; vẫn cần **authorization**, tenant boundary, endpoint binding và logging an toàn.

Ví dụ chạy độc lập ở phần 9 chỉ mô phỏng hai bên trong cùng một tiến trình, **chưa chứng minh kết nối mạng chống MITM hoặc đã xác thực Alice/Bob**.

## 8. Bọc khóa: KEK, DEK và tách quyền hạn

Trong envelope encryption, hệ thống thường tạo một **DEK ngẫu nhiên** để mã hóa dữ liệu khối lượng lớn, rồi dùng **KEK** để bọc DEK:

~~~text
                 DEK (AES random)
                       |
             +---------+---------------------+
             |                               |
    AES-GCM Encrypt(data)            AES-KW Wrap(DEK, KEK)
             |                               |
    ciphertext + tag                wrapped DEK
             \                              /
              +---- lưu cùng metadata ------+
~~~

**DEK (Data Encryption Key)** và **KEK (Key Encryption Key)** khác vai trò, không nên là cùng byte. Khi key schedule có quyền xuất KEK, dùng nhãn riêng như `"wrap/aes-kw"`; không lấy `K_c2s_enc` dùng làm KEK.

**AES Key Wrap (AES-KW)** là thuật toán bọc vật liệu khóa, Java 17 có provider hỗ trợ transformation `"AESWrap"` trong JCE phổ biến. Nó không phải mode mã hóa bất kỳ metadata/payload dài tùy ý; với AES-KW theo RFC 3394, đầu vào khóa bọc phải tuân theo yêu cầu độ dài/khối của thuật toán. Các hệ thống cần bảo vệ **associated metadata** hoặc khóa với độ dài bất kỳ có thể cần AES-KWP hoặc AEAD wrapping theo đặc tả riêng.

- **Không xuất wrapped DEK rồi bỏ metadata định danh KEK.** Cần key ID/version/algorithm để giải bọc sau rotation.
- **Không giả định AESWrap tự ràng buộc tenant/record ID**. AES-KW có phép kiểm tính toàn vẹn của vật liệu được bọc, nhưng không nhận AAD như AES-GCM. Nếu metadata quyết định đối tượng/thuật toán, phải ràng buộc bằng thiết kế giao thức/metadata xác thực đúng.
- Với khóa lưu trữ lâu dài, thông thường **KEK lấy từ KMS/HSM hoặc một key hierarchy được quản lý**, không phải một traffic shared secret ngắn hạn bị hủy sau phiên mạng. Ví dụ dưới bọc DEK bằng khóa dẫn xuất từ phiên chỉ để minh họa **key-purpose separation**, không đề xuất quy trình lưu trữ production.
- Không nhầm **key wrapping** với gửi plaintext DEK qua kênh “đã mã hóa” nhưng không xác thực, hoặc với việc Base64-encode khóa.

## 9. Java 17+ hoàn chỉnh: X25519, HKDF, AES-GCM, AESWrap

Chương trình sau dùng JCA/JCE trong JDK 17+; không cần Maven. Nó thực hiện:

1. Kiểm chứng mã HKDF Extract/Expand theo **test vector số 1 của RFC 5869** (không in byte secret).
2. Tạo hai cặp khóa X25519, mỗi bên tính shared secret; in kết quả **so khớp** chứ không lộ secret.
3. Tạo hash của handshake transcript được encode có length-prefix; tạo một public salt từ transcript theo **quy tắc giả định của demo**.
4. Extract hai PRK bằng HMAC-SHA256; Expand các khóa `c2s/aes-gcm`, `s2c/aes-gcm`, `wrap/aes-kw` và `auth/hmac-sha256` theo **label/context độc lập**.
5. Dùng key c2s AES-GCM để mã hóa và phía kia giải mã; dùng sai key hướng/khác context sẽ lỗi authentication tag.
6. Bọc AES DEK bằng key `wrap` và bên còn lại mở bọc thành công.

**Lưu thành file `KeyScheduleDemo.java`:**

~~~java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class KeyScheduleDemo {
    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // Framing không nhập nhằng: 4 byte độ dài (big-endian) + bytes.
    static byte[] frame(byte[]... fields) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        for (byte[] f : fields) {
            out.writeInt(f.length);
            out.write(f);
        }
        out.flush();
        return bytes.toByteArray();
    }

    static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    static byte[] agree(PrivateKey me, PublicKey peer) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(me);
        agreement.doPhase(peer, true);
        return agreement.generateSecret();
    }

    // HKDF-Extract (RFC 5869), SHA-256 HashLen = 32.
    static byte[] extract(byte[] salt, byte[] ikm) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                salt == null ? new byte[32] : salt, "HmacSHA256"));
        return mac.doFinal(ikm);
    }

    // HKDF-Expand với đúng các block T(i), counter từ 0x01.
    static byte[] expand(byte[] prk, byte[] info, int length)
            throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        int hashLen = mac.getMacLength();
        if (length < 1 || length > 255 * hashLen) {
            throw new IllegalArgumentException("Bad HKDF length");
        }
        byte[] okm = new byte[length];
        byte[] last = new byte[0];
        int offset = 0;
        for (int counter = 1; offset < length; counter++) {
            mac.update(last);
            mac.update(info);
            mac.update((byte) counter);
            byte[] block = mac.doFinal();
            int n = Math.min(block.length, length - offset);
            System.arraycopy(block, 0, okm, offset, n);
            offset += n;
            Arrays.fill(last, (byte) 0);
            last = block;
        }
        Arrays.fill(last, (byte) 0);
        return okm;
    }

    static byte[] info(String purpose, byte[] transcriptHash)
            throws Exception {
        return frame(
                utf8("lesson-key-schedule/v1"),
                utf8("X25519-HKDF-SHA256"),
                transcriptHash,
                utf8(purpose));
    }

    static byte[] seal(byte[] aesKey, byte[] nonce, byte[] aad, byte[] plain)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(plain);
    }

    static byte[] open(byte[] aesKey, byte[] nonce, byte[] aad,
            byte[] cipherAndTag) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(cipherAndTag);
    }

    static boolean rejects(byte[] aesKey, byte[] nonce, byte[] aad,
            byte[] encrypted) throws Exception {
        try {
            open(aesKey, nonce, aad, encrypted);
            return false;
        } catch (AEADBadTagException expected) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        // Vector RFC 5869 TC1 (IKM=0x0b * 22), kiểm tra HKDF.
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        byte[] testSalt = HexFormat.of().parseHex(
                "000102030405060708090a0b0c");
        byte[] testInfo = HexFormat.of().parseHex(
                "f0f1f2f3f4f5f6f7f8f9");
        byte[] known = HexFormat.of().parseHex(
                "3cb25f25faacd57a90434f64d0362f2a"
                + "2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
                + "34007208d5b887185865");
        System.out.println("HKDF RFC5869       = "
                + MessageDigest.isEqual(known,
                        expand(extract(testSalt, ikm), testInfo, 42)));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        KeyPair alice = generator.generateKeyPair();
        KeyPair bob = generator.generateKeyPair();
        byte[] aliceSecret = agree(alice.getPrivate(), bob.getPublic());
        byte[] bobSecret = agree(bob.getPrivate(), alice.getPublic());
        System.out.println("Shared match       = "
                + MessageDigest.isEqual(aliceSecret, bobSecret));
        System.out.println("Shared bytes       = " + aliceSecret.length);

        // Transcript giả định: hai public key và role được cố định thứ tự.
        byte[] transcriptHash = sha256(frame(
                utf8("handshake-demo-v1"),
                utf8("initiator=Alice"),
                alice.getPublic().getEncoded(),
                utf8("responder=Bob"),
                bob.getPublic().getEncoded()));
        byte[] salt = sha256(frame(utf8("extract-salt/v1"), transcriptHash));
        byte[] aPrk = extract(salt, aliceSecret);
        byte[] bPrk = extract(salt, bobSecret);

        byte[] aC2s = expand(aPrk,
                info("c2s/aes-gcm", transcriptHash), 16);
        byte[] bC2s = expand(bPrk,
                info("c2s/aes-gcm", transcriptHash), 16);
        byte[] s2c = expand(aPrk,
                info("s2c/aes-gcm", transcriptHash), 16);
        byte[] aWrap = expand(aPrk,
                info("wrap/aes-kw", transcriptHash), 16);
        byte[] bWrap = expand(bPrk,
                info("wrap/aes-kw", transcriptHash), 16);
        byte[] macKey = expand(aPrk,
                info("auth/hmac-sha256", transcriptHash), 32);

        System.out.println("C2S key bytes      = " + aC2s.length);
        System.out.println("Wrap key bytes     = " + aWrap.length);
        System.out.println("MAC key bytes      = " + macKey.length);
        System.out.println("Same role keys     = "
                + MessageDigest.isEqual(aC2s, bC2s));
        System.out.println("C2S != S2C         = "
                + !MessageDigest.isEqual(aC2s, s2c));
        System.out.println("C2S != wrap        = "
                + !MessageDigest.isEqual(aC2s, aWrap));

        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        byte[] sealed = seal(aC2s, nonce, transcriptHash,
                utf8("hello key schedule"));
        System.out.println("Decrypt C2S        = "
                + new String(open(bC2s, nonce, transcriptHash, sealed),
                StandardCharsets.UTF_8));
        System.out.println("Wrong role rejected= "
                + rejects(s2c, nonce, transcriptHash, sealed));
        byte[] wrongContext = sha256(utf8("different transcript"));
        byte[] wrongKey = expand(bPrk,
                info("c2s/aes-gcm", wrongContext), 16);
        System.out.println("Wrong ctx rejected = "
                + rejects(wrongKey, nonce, transcriptHash, sealed));

        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        SecretKey dataKey = keyGenerator.generateKey();
        Cipher wrapper = Cipher.getInstance("AESWrap");
        wrapper.init(Cipher.WRAP_MODE, new SecretKeySpec(aWrap, "AES"));
        byte[] wrapped = wrapper.wrap(dataKey);
        Cipher unwrapper = Cipher.getInstance("AESWrap");
        unwrapper.init(Cipher.UNWRAP_MODE,
                new SecretKeySpec(bWrap, "AES"));
        Key restored = unwrapper.unwrap(
                wrapped, "AES", Cipher.SECRET_KEY);
        System.out.println("Wrapped DEK matches= "
                + MessageDigest.isEqual(
                        dataKey.getEncoded(), restored.getEncoded()));

        // Giảm vòng đời secret do ứng dụng giữ; provider có thể giữ copy.
        Arrays.fill(aliceSecret, (byte) 0);
        Arrays.fill(bobSecret, (byte) 0);
        Arrays.fill(aPrk, (byte) 0);
        Arrays.fill(bPrk, (byte) 0);
        Arrays.fill(aC2s, (byte) 0);
        Arrays.fill(bC2s, (byte) 0);
        Arrays.fill(s2c, (byte) 0);
        Arrays.fill(aWrap, (byte) 0);
        Arrays.fill(bWrap, (byte) 0);
        Arrays.fill(macKey, (byte) 0);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac KeyScheduleDemo.java
java KeyScheduleDemo
~~~

Output kỳ vọng **ổn định dù cặp khóa, nonce và DEK được sinh ngẫu nhiên**:

~~~text
HKDF RFC5869       = true
Shared match       = true
Shared bytes       = 32
C2S key bytes      = 16
Wrap key bytes     = 16
MAC key bytes      = 32
Same role keys     = true
C2S != S2C         = true
C2S != wrap        = true
Decrypt C2S        = hello key schedule
Wrong role rejected= true
Wrong ctx rejected = true
Wrapped DEK matches= true
~~~

### 9.1. Giải thích theo thứ tự

1. **`HKDF RFC5869 = true`:** phép thử đối chiếu đầu ra 42 byte với vector chuẩn, giúp phát hiện sai công thức, byte counter hoặc copy block. Đừng tự triển khai HKDF trong production nếu đã có thư viện/provider chuẩn phù hợp.
2. **`Shared match = true`:** `KeyAgreement("X25519")` cho hai bên cùng byte khi dùng đúng cặp private/public. `Shared bytes = 32` là độ dài X25519 output; không phải tuyên bố đây đã là traffic key.
3. **`transcriptHash`:** encode các trường có độ dài, thứ tự role và hai public keys rồi SHA-256. Demo đặt `salt` từ hash này theo một quy ước riêng để hai bên cùng làm được. Giao thức thực tế phải có **thông điệp và quy tắc salt/transcript chính xác được đặc tả**, không sao chép demo như một handshake tiêu chuẩn.
4. **`aPrk/bPrk`:** hai bên Extract bằng cùng IKM/salt cho cùng PRK; mỗi `expand` có cùng PRK nhưng **info purpose khác** sẽ cho output khác.
5. **`Same role keys = true`:** Alice gửi với `aC2s`, Bob nhận với `bC2s`. Bob không dùng nhãn `"s2c"` khi nhận c2s. `C2S != S2C` chứng minh labels khác nhau trên chính lượt demo; không coi một ví dụ trùng/khác là chứng minh mật mã về mọi input.
6. **`Wrong role rejected` / `Wrong ctx rejected`:** AES-GCM từ chối bản mã khi dùng khóa khác; `Wrong ctx` được tạo bằng Expand với transcript khác. Điều này minh họa key binding, **không chứng minh đã chống MITM**.
7. **`Wrapped DEK matches`:** khóa `wrap` do hai phía dẫn xuất được dùng với `AESWrap`. Phép wrap bảo vệ bản thân DEK khi gửi trong phạm vi khóa KEK này; không thay chính sách KMS, phân quyền hoặc bảo vệ metadata.

Các mảng byte được `Arrays.fill` sau khi dùng để giảm thời gian tồn tại secret ở bộ nhớ do ứng dụng kiểm soát. Điều này không bảo đảm xóa các bản copy bên trong provider/JVM hoặc mọi secret còn tồn tại trong object `PrivateKey`/`SecretKey`. Demo không có giao tiếp mạng, certificate, key confirmation, chống replay hoặc persistence; đó là các lớp cần bổ sung bằng **giao thức chuẩn**.

## 10. KDF và bản tin thực tế: chọn đúng ngữ cảnh AAD, IV, role

Tách khóa mục đích mới là một phần. Nếu lưu/truyền encrypted data, bên nhận còn cần biết đúng **nonce, ciphertext, authentication tag, suite, key epoch và sequence**:

~~~text
handshake metadata:
    protocol version, suite, roles, public keys, identity/auth evidence
    -> transcript hash -> HKDF context

application record:
    version | epoch | sequence | nonce | ciphertext || tag
    AAD = canonical(version, epoch, direction, sequence, type, ...)
    key = K_direction_for_epoch
~~~

**Key derivation context và AEAD AAD là hai lớp khác nhau.** KDF ràng buộc key với phiên/vai trò; AAD ràng buộc **từng bản tin** với metadata không mã hóa. Có transcript binding trong KDF vẫn không tự bảo vệ mọi field header ứng dụng, và AAD đúng vẫn không sửa được việc cấp nonce trùng.

Một record đầy đủ có thể cần:

- **Key epoch/generation**: lựa chọn đúng khóa sau rekey; chống dùng epoch cũ trái phép theo policy.
- **Sequence / message ID**: dùng để xác thực thứ tự/chống replay; tag hợp lệ của message cũ vẫn hợp lệ nếu protocol không nhớ đã thấy.
- **Nonce**: 96 bit theo GCM phổ biến; duy nhất dưới cùng traffic key.
- **Ciphertext + tag**: không chấp nhận plaintext nếu `AEADBadTagException`/verification failure.
- **Header/AAD**: ràng buộc với đúng channel, tenant, type hoặc direction cần bảo vệ khỏi thay đổi.
- **Algorithm/key ID**: sử dụng mapping và chính sách xác thực chống downgrade, tránh attacker điều khiển chọn key/suite tùy tiện.

**Không nối dữ liệu không tin cậy vào HKDF info rồi kỳ vọng bảo mật tự xuất hiện**: cần xác thực/kiểm ngữ cảnh quan trọng trước khi coi nó là claim tin cậy, hoặc ràng buộc nó qua protocol transcript đã xác thực.

## 11. Tình huống thiết kế thường gặp

### 11.1. Ứng dụng chat hai chiều

Một phiên client/server dùng X25519 để tạo Z. Nếu chọn cùng `K_enc` cho cả gửi và nhận, client và server có thể dùng lại cùng nonce counter bắt đầu ở 0 rồi vô tình tái sử dụng **cùng (key, nonce)** trong AES-GCM. Chia `K_c2s_enc` và `K_s2c_enc` bằng labels khác làm giảm nguy cơ chồng miền sử dụng, nhưng **mỗi hướng vẫn phải bảo đảm nonce unique**. Dùng protocol sequence/AAD và chống replay.

### 11.2. Cùng thư viện dùng cho hai sản phẩm

Sản phẩm A có `"sync/v1"` và B có `"backup/v1"`, cùng nguồn secret trong key hierarchy. Nếu cả hai gọi `HKDF-Expand(PRK, "enc", 16)` thì có thể lấy cùng key. Hãy đưa **protocol/product ID, version, usage label** vào info khác nhau, tránh cross-protocol key reuse. Nếu thiết kế yêu cầu tổ chức tenant hoặc device, đưa những ID có ý nghĩa và đã xác minh vào context.

### 11.3. Bọc DEK để lưu hồ sơ

Hệ thống lưu ciphertext hồ sơ bằng DEK, wrapped DEK bằng KEK trong KMS. Khi rotation, cần metadata `keyId` để biết đúng KEK; nếu rewrap DEK, có thể không cần decrypt toàn bộ dữ liệu. **KEK storage dài hạn không nên mặc nhiên được dẫn xuất từ ephemeral X25519 session Z**, vì sau phiên KEK đó biến mất hoặc không có cơ chế khôi phục. Thiết kế key hierarchy, quyền unwrap, auditing và backup riêng cho lưu trữ.

### 11.4. Xác thực handshake

Alice/Bob cùng tính shared secret và PRK, sau đó tạo confirmation MAC trên transcript bằng `K_confirm_client`/`K_confirm_server`. Nó giúp phát hiện một bên thiếu secret hoặc transcript khác nhưng **không chứng minh danh tính** nếu public keys không được xác thực: MITM có thể có confirmation MAC hợp lệ ở từng phiên do chính họ tham gia. Cần thiết kế AKE có identity binding thật.

### 11.5. Chuyển thuật toán và rekey

Hệ thống chuyển từ AES-128-GCM sang AES-256-GCM hoặc một suite khác, không chỉ đổi độ dài từ 16 lên 32 byte mà quên **suite ID trong transcript/info**. Thỏa thuận thuật toán phải chống downgrade, hai bên derive đúng key length, key ID và được xác thực. Một server có nhiều bản cũ/mới phải có version rõ ràng và chính sách ngừng chấp nhận suite yếu.

## 12. Những lỗi bảo mật hay gặp

| Sai lầm | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Dùng trực tiếp 16/32 byte đầu của ECDH secret làm AES key | Không có context/purpose/role separation; key schedule tự phát | HKDF Extract/Expand theo giao thức, nhãn đầy đủ |
| Lấy SHA-256(Z) rồi dùng cho mọi mục đích | Không tách encryption, MAC, wrap | HKDF Expand với `info` riêng mỗi purpose |
| Client `"send"`, Server cũng `"send"` nhưng nhầm hướng | Hai bên derive sai key hoặc cùng key ở hai hướng | Định nghĩa `c2s/s2c` hoặc initiator/responder bất biến |
| Cùng HKDF `info` cho MAC và AES | Tái sử dụng khóa chéo primitive | Domain separation bằng label/thuật toán/role |
| `info = a + ":" + b` không đóng khung trường | Hai tuple ngữ cảnh có thể cùng byte string | Length prefix/canonical binary encoding |
| Không ràng buộc phiên bản và suite | Khóa giữa version/protocol có thể trùng hoặc bị nhầm | Thêm version, suite, transcript hash vào info |
| Cho rằng transcript hash là chữ ký số | Không ngăn MITM tráo public key | AKE có certificate/signature/PSK auth |
| Coi HKDF salt là bí mật bắt buộc | Khó đồng bộ giữa hai bên, sai giả định | Salt thường công khai, phải đúng quy tắc giao thức |
| Coi HKDF là password KDF | Bẻ password yếu rất nhanh | Argon2id/scrypt/PBKDF2 cho password |
| Dùng một AES-GCM key, nonce 0 cho cả hai phía | Nonce reuse gây hỏng bảo mật GCM | Khóa tách hướng + nonce duy nhất mỗi key |
| Dẫn xuất KEK từ phiên ngắn hạn rồi lưu DEK lâu dài | Sau restart/phiên kết thúc không còn KEK để unwrap | Dùng KMS/HSM/key hierarchy lưu trữ |
| Tưởng AES-KW có AAD như AES-GCM | Không có binding metadata tự động | Xác thực context/metadata bằng protocol riêng |
| Lưu log `generateSecret()` hoặc PRK | Lộ toàn bộ traffic/wrap keys liên quan | Secret không in/log; giảm tồn tại trong RAM |
| Không kiểm public key hoặc lỗi X25519 all-zero | Có thể nhận input bất thường/không an toàn | Parse/validate đúng algorithm và provider/standard |
| Không giới hạn HKDF `L` | Sai hoặc vượt HKDF output bound | Kiểm `1 ≤ L ≤ 255×HashLen` trong demo |
| Chỉ kiểm encrypt/decrypt đúng, bỏ negative tests | Không phát hiện sai context, role, length và tamper | Test expected failures, key separation và interoperability |
| Dùng code demo như giao thức mạng production | Thiếu danh tính, replay, metadata, rotation | Dùng TLS 1.3 hoặc AKE/record protocol chuẩn |

## 13. Kiểm thử key schedule và cross-system interoperability

**Kiểm tra byte không chỉ so người dùng thấy output giống nhau.** Hai triển khai Java/Python/Go có thể cùng gọi X25519/HKDF nhưng khác cách đóng khung public keys hoặc labels, dẫn tới khóa khác.

Bộ test nên có:

1. **RFC 5869 HKDF known-answer vectors:** input IKM/salt/info/length cố định và expected PRK/OKM. Chương trình minh họa chạy vector số 1.
2. **Role symmetry:** Alice c2s encryption key phải bằng Bob c2s decryption key; riêng c2s khác s2c và wrap.
3. **Purpose separation:** thay nhãn `"enc"` thành `"wrap"` phải tạo key khác; dùng nhãn cũ để giải bọc sai phải thất bại.
4. **Context separation:** thay transcript, suite version, tenant/channel hoặc identity được ràng buộc phải tạo khóa khác.
5. **Transcript canonicalization:** cùng các field nhưng serialization/byte order khác có thể ra khác hash; phải định nghĩa và test chính xác thứ tự/length.
6. **Negative AEAD tests:** dùng sai key, nonce, AAD, tag hoặc sửa ciphertext phải từ chối; không phát plaintext chưa xác thực.
7. **Public key validation:** peer key malformed, wrong curve, wrong encoding, low-order issues, error mapping an toàn.
8. **Replay/sequence:** gói hợp lệ cũ không được coi là message mới nếu giao thức đòi chống replay; state phải kiểm.
9. **Rekey/rotation:** epoch mới derive key khác, key ID chọn đúng và không chấp nhận rollback trái chính sách.
10. **Wrap/unwrap:** DEK wrap bằng KEK đúng mở được; KEK sai hoặc wrapped bytes sửa phải fail theo primitive/provider.
11. **Cross-provider:** Java JCE provider, thư viện C/Go/Python phải thống nhất X25519 encoding, HKDF info, nonce format, AES-KW variant và test vectors; không chỉ test ASCII labels đơn giản.

**Lưu ý khi test:** Mẫu known-answer có thể chứa byte giả cố định công khai, nhưng **không bao giờ log secret production** để đối chiếu. Test `MessageDigest.isEqual` trên byte để kiểm kết quả; giao thức xác thực sản xuất nên dùng verification API của primitive và phân tích thời gian/timing tùy mô hình đe dọa.

## 14. Bài tập (có đáp án)

**Bài 1.** Hai bên X25519 có cùng shared secret 32 byte. Có nên dùng toàn bộ secret làm khóa AES-256 rồi dùng lại làm HMAC key?

**Đáp án:** Không. Cần key schedule HKDF Extract/Expand, tách nhãn và chiều dài riêng cho AES và HMAC. Cùng kích thước không bảo đảm đúng mục đích hay ngữ cảnh.

**Bài 2.** `K_c2s = HKDF-Expand(PRK, "c2s", 16)` và `K_s2c = HKDF-Expand(PRK, "s2c", 16)` khác nhau để làm gì?

**Đáp án:** Để **tách hai hướng sử dụng**, tránh vô tình dùng cùng key cho traffic hai chiều và nhầm quyền/nonce. Vẫn cần bảo đảm nonce unique cho từng key.

**Bài 3.** `salt` HKDF và `info` có nhất thiết phải bí mật không?

**Đáp án:** Thường **không**. Chúng có thể công khai nhưng phải nhất quán/được ràng buộc vào giao thức, trong khi IKM, PRK và OKM phải giữ bí mật. Salt không thay xác thực identity.

**Bài 4.** Thay `info` từ `"wrap"` sang `"enc"` có đảm bảo có đúng cùng key không?

**Đáp án:** Không. HKDF Expand với nhãn khác được thiết kế để cho **key độc lập về mặt tính toán** dưới giả định PRF. Hai bên phải dùng cùng nhãn cho cùng mục đích; nhầm nhãn sẽ khiến decrypt/unwrap thất bại.

**Bài 5.** Nếu Alice/Bob dùng transcript hash trong HKDF info nhưng không ký hoặc xác minh public key, họ có tránh MITM chưa?

**Đáp án:** Chưa. Mallory có thể thực hiện hai phiên riêng với Alice và Bob, mỗi phiên có transcript/key hợp lệ. Phải có cơ chế xác thực identity/public key theo AKE/giao thức chuẩn.

**Bài 6.** Hai ứng dụng cùng dùng `info = "enc"` với cùng PRK. Lỗi gì có thể xảy ra?

**Đáp án:** **Cross-protocol key reuse/confusion**. Cần thêm protocol ID/version, cipher suite, purpose, hướng và ngữ cảnh phân biệt.

**Bài 7.** Trong Java, `new GCMParameterSpec(128, nonce)` dùng tag 128 bit. Có liên quan gì đến việc key AES-128 là 16 byte?

**Đáp án:** Đây là **hai thông số khác nhau**: AES-128 dùng key 128 bit (16 byte); tag GCM 128 bit là độ dài thẻ xác thực. Ngoài ra nonce 96 bit (12 byte) thường được dùng. Không lấy nhầm tag size làm key size.

**Bài 8.** DEK được bọc bằng KEK sinh từ shared secret X25519 ephemeral, rồi lưu 5 năm. Vấn đề gì?

**Đáp án:** KEK biến mất cùng phiên nếu không được duy trì/khôi phục; không thích hợp với lưu trữ dài hạn nếu chưa có thiết kế key hierarchy. Nên dùng KEK quản lý qua KMS/HSM hoặc hệ thống lưu khóa bền vững có rotation/backup/authorization.

**Bài 9.** Cặp key AES-GCM c2s/s2c đã tách riêng; hai process gửi cùng hướng lại dùng nonce counter khởi tạo từ 0 dưới cùng key. Có an toàn không?

**Đáp án:** Không. Nonce phải duy nhất **toàn miền các lần mã hóa dưới một key**, không chỉ theo từng process. Cần phân phối counter/partition hoặc chiến lược nonce được đánh giá.

**Bài 10.** Một record AES-KW có wrapped key hợp lệ, nhưng metadata tenant/record ID bị thay. AES-KW có tự kiểm chính xác tenant không?

**Đáp án:** Không. AES-KW kiểm vật liệu khóa theo thiết kế wrap, **không có AAD tenant/record ID** như AES-GCM. Cần binding metadata vào hệ thống xác thực/phân quyền hoặc định dạng wrapping có AAD theo đặc tả.

## 15. Checklist thiết kế và triển khai

- [ ] Public-key agreement dùng X25519/ECDH theo thư viện chuẩn; **peer key/identity được xác thực** bởi AKE/TLS/certificate/PSK policy.
- [ ] Không dùng raw shared secret trực tiếp làm AES/MAC/KEK; xử lý điều kiện public-key malformed/all-zero theo chuẩn/provider.
- [ ] HKDF-Extract đúng HMAC hash/salt/IKM; HKDF-Expand đúng byte info/counter/length; có known-answer tests.
- [ ] Dữ liệu `info` được đóng khung không nhập nhằng, có **protocol/version/suite/transcript/role/purpose** và những miền sử dụng cần ràng buộc.
- [ ] Các key cho `c2s`, `s2c`, MAC, confirmation và wrap có nhãn khác nhau, độ dài đúng primitive.
- [ ] Với AES-GCM, **nonce duy nhất** cho mỗi lần encrypt dưới từng key, tag được xác minh trước khi dùng plaintext, AAD gắn metadata message khi cần.
- [ ] Có chống replay, sequence/epoch, giới hạn sử dụng key và rekey/rotation theo giao thức.
- [ ] DEK để mã hóa dữ liệu, KEK để bọc DEK; KEK dài hạn dùng key management phù hợp, không buộc vào ephemeral session.
- [ ] Không log hoặc lưu công khai private key, shared secret, PRK/OKM; giảm vòng đời secret và quản lý quyền giải bọc.
- [ ] Chạy interoperability vectors trên các triển khai, kiểm cả negative cases và metadata/algorithm negotiation.
- [ ] Nếu ứng dụng cần kết nối an toàn thực tế, ưu tiên **giao thức chuẩn đã được thẩm định** (điển hình TLS 1.3) thay vì chắp vá key agreement, KDF và AEAD thành một handshake riêng.

## 16. Tóm tắt

**Shared secret chỉ là đầu vào cho key schedule**, không phải khóa chung dùng cho mọi việc. Với X25519, hai bên có thể có cùng byte bí mật; **HKDF-Extract** tạo PRK, **HKDF-Expand** với `info` có định dạng chuẩn và nhãn mục đích sinh ra các key **đúng thuật toán, chiều truyền và phạm vi sử dụng**. Ngữ cảnh handshake/transcript phải được ràng buộc, nhưng vẫn cần **xác thực danh tính và thỏa thuận thuật toán** theo giao thức. Tách `c2s/s2c`, AES-GCM, HMAC và KEK giúp giảm key confusion; kết hợp với nonce, AAD, chống replay và vòng đời khóa đúng mới tạo thành một hệ thống mã hóa an toàn.

Tài liệu tham khảo định nghĩa thuật toán: **RFC 5869 (HKDF)** https://www.rfc-editor.org/rfc/rfc5869 ; **RFC 7748 (X25519/X448)** https://www.rfc-editor.org/rfc/rfc7748 ; **RFC 3394 (AES Key Wrap)** https://www.rfc-editor.org/rfc/rfc3394 . Các RFC này định nghĩa primitive, không tự thay thế việc thiết kế/kiểm định một giao thức có xác thực đầu-cuối.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-establishment-and-wrapping-005">10.5 Xác thực đối tác để tránh đánh tráo khóa trong quá trình thỏa thuận</a>

<details>
<summary>Xem chi tiết</summary>

# 10.5. Xác thực đối tác để tránh đánh tráo khóa trong quá trình thỏa thuận

**Thỏa thuận khóa (key agreement)** cho phép hai phía tạo ra bí mật chung qua một kênh truyền có thể bị quan sát. Nhưng có một điều kiện rất dễ quên: **hai phía cần biết mình thực sự đang thỏa thuận với ai**. Nếu kẻ tấn công có thể thay khóa công khai được trao đổi trong quá trình bắt tay, Diffie–Hellman/ECDH/X25519 đơn thuần **không ngăn được tấn công người đứng giữa (Man-in-the-Middle, MITM)**.

Một kẻ đứng giữa có thể tự tạo hai phiên thỏa thuận khóa: một phiên với Alice và một phiên với Bob. Alice tưởng đã tạo khóa với Bob; Bob tưởng đã tạo khóa với Alice; thực tế cả hai đang chia sẻ hai khóa riêng biệt với kẻ tấn công. Ngay cả khi mỗi phía dùng AES-GCM đúng chuẩn để bảo vệ dữ liệu, kẻ đứng giữa vẫn có thể giải mã rồi mã hóa lại, bởi hắn có khóa hợp lệ cho **hai phiên giả** đó.

**Giải pháp không phải là làm khóa DH dài hơn hoặc thêm hash cho khóa trao đổi.** Cần **xác thực khóa công khai và danh tính đối tác**, ràng buộc bằng chứng xác thực vào **đúng phiên bắt tay, đúng vai trò, đúng thuật toán và đúng các public key tạm thời**, rồi chỉ cho phép dùng khóa phiên sau khi việc xác minh đạt yêu cầu.

## 1. Mục tiêu bài học

Sau khi học xong, bạn có thể:

- Mô tả vì sao DH/ECDH/X25519 không xác thực danh tính chỉ nhờ phép tính bí mật chung.
- Phân biệt **agreement**, **authentication**, **authorization**, **key confirmation** và **key wrapping**.
- Vẽ lại tấn công MITM thay thế public key, biết chính xác vì sao mỗi bên vẫn tính được một shared secret nhưng không có kênh an toàn Alice–Bob.
- Xây dựng các bước xác thực đối tác bằng chữ ký số gắn với identity, certificate/PKI, public-key pinning hoặc khóa PSK đã phân phối an toàn.
- Xác định những byte cần gắn vào **handshake transcript**: protocol version, cipher suite/KDF, role, identity, ephemeral public keys, nonce và context.
- Viết và chạy một minh họa Java 17+ kết hợp **X25519 + Ed25519 + HKDF-SHA256** với khóa công khai danh tính được tin cậy từ trước.
- Kiểm thử thay public key, giả mạo người ký, đổi tên đối tác và tái sử dụng chữ ký từ phiên cũ; hiểu giới hạn của demo so với TLS 1.3.
- Nhận diện các sai sót liên quan đến certificate validation, TOFU, key substitution, replay, downgrade, UKS và xác minh quyền truy cập.

## 2. Ba câu hỏi riêng biệt về khóa

Trong thực tế, từ “xác thực khóa” đôi khi gom nhiều việc khác nhau. Nên tách thành ba câu:

**Câu hỏi A — có tạo được cùng bí mật không?**  
Nếu Alice giữ private key tạm thời `a` và Bob giữ private key tạm thời `b`, phép X25519 cho phép:

\[
Z_A = X25519(a, B_{\text{ephemeral}})
\]

\[
Z_B = X25519(b, A_{\text{ephemeral}})
\]

Với hai cặp khóa hợp lệ và các giá trị public key tương ứng được trao đổi nguyên vẹn, hai bí mật `Z_A` và `Z_B` bằng nhau. Đây là **khả năng thỏa thuận khóa**, chưa cho biết public key thật sự đến từ ai.

**Câu hỏi B — người đứng sau khóa công khai là ai?**  
Alice cần biết `B_ephemeral` có thuộc **đúng phiên thỏa thuận với Bob** không. Để biết điều đó, Alice phải có một bằng chứng gắn `B_ephemeral` với **định danh Bob mà Alice đang kỳ vọng**, dựa trên một nền tảng tin cậy như khóa ký đã pin, certificate đã kiểm tra, PSK được cấp phát đúng chủ thể hoặc một quy trình xác thực out-of-band.

**Câu hỏi C — khóa được dùng cho mục đích nào?**  
Sau khi hai phía tạo shared secret và xác thực transcript, thường cần KDF (ví dụ HKDF) để lấy key dùng cho ứng dụng và, khi cần, các key/nonce riêng theo hướng. Sau đó server vẫn phải kiểm tra **quyền truy cập** của principal, không chỉ kiểm tra rằng client biết một khóa.

Một phiên có thể qua A nhưng thất bại B (MITM), hoặc qua A và B nhưng thất bại C (khách hàng đã xác thực nhưng không được phép thao tác tài khoản khác).

## 3. Diffie–Hellman không xác thực: tấn công đánh tráo khóa

Giả sử Alice và Bob muốn thiết lập kênh bảo mật qua mạng mà Mallory có thể đọc, chặn và sửa gói tin.

### 3.1. Trường hợp chỉ nghe lén, không sửa dữ liệu

```text
Alice                                             Bob
------                                            ------
Sinh (aPriv, aPub)                               Sinh (bPriv, bPub)
        |                                             |
        +------------- aPub ------------------------->|
        |<------------ bPub --------------------------+
        |                                             |
Z_A = X25519(aPriv, bPub)             Z_B = X25519(bPriv, aPub)
        |                                             |
        +-------- HKDF -> session key -----------------+
```

Nếu attacker chỉ **quan sát** hai public key mà không biết private key, X25519 được thiết kế để khiến việc tính shared secret trở nên khó. Đây là tính chất nền tảng của Diffie–Hellman.

**Nhưng Mallory không nhất thiết chỉ nghe lén.** Nếu đường truyền không xác thực, Mallory có thể thay cả hai public key.

### 3.2. Tấn công MITM bằng hai phiên DH

```text
  Alice                         Mallory                         Bob
  -----                         -------                         ---
  aPub -----------> [chặn]       mPub1 ------------->         Bob
  Alice <----------- mPub2       [chặn] <------------- bPub

 Alice tính:                        Mallory tính:
 X25519(aPriv, mPub2)               X25519(mPriv2, aPub)
                   -> Z_AM = Z_MA

                                  Mallory tính:                 Bob tính:
                                  X25519(mPriv1, bPub)          X25519(bPriv, mPub1)
                                         Z_MB = Z_BM <-
```

Kết quả:
- Alice có **một shared secret với Mallory**, không phải Bob.
- Bob có **một shared secret khác với Mallory**, không phải Alice.
- Mallory biết cả hai secrets, có thể tạo traffic keys cho cả hai chiều và làm trung gian decrypt/modify/reencrypt.
- Không có phép toán X25519 nào tự cảnh báo rằng tên đối tác mà giao diện hiển thị không trùng với chủ thể tạo ra public key.

Đây là lý do phát biểu “dùng ECDH nên dữ liệu đã an toàn trước MITM” **không đúng nếu thiếu peer authentication**.

### 3.3. Vì sao hash public key cũng chưa đủ?

Ví dụ Alice nhận `bPub` rồi tính `SHA-256(bPub)` và thấy một fingerprint. Fingerprint chỉ tóm tắt các byte của key Alice **đã nhận**. Nếu attacker thay `bPub` bằng `mPub`, Alice cũng có thể tính một fingerprint hoàn toàn hợp lệ của `mPub`.

Fingerprint chỉ giúp khi Alice **so sánh với một fingerprint đáng tin đã có từ trước**, nhận bằng kênh xác thực độc lập. Gửi public key và fingerprint của chính nó trong **cùng một gói không xác thực** không giải quyết việc đánh tráo.

Đổi X25519 sang curve khác, dùng RSA nhiều bit hơn hay chuyển SHA-256 sang SHA-512 cũng không sửa được gốc lỗi: **không có chứng cứ ràng buộc khóa vào đúng đối tác**.

## 4. Xác thực đối tác thực sự nghĩa là gì?

Đối với Alice muốn giao tiếp với Bob, có ít nhất bốn thành phần:

1. **Expected identity:** Alice biết trước mình muốn gặp ai, ví dụ `bob@example.internal`, một tên dịch vụ, tenant + service ID hoặc hostname `api.example.com`.
2. **Trusted authentication key:** Alice có cách lấy **khóa xác minh danh tính Bob từ nguồn đáng tin**, không chỉ từ gói tin Mallory kiểm soát.
3. **Proof of possession:** Bob tạo một bằng chứng mà chỉ chủ thể giữ private key/PSK được cấp phép mới tạo hợp lệ, như chữ ký số trên transcript bắt tay.
4. **Session binding:** bằng chứng này gắn **đúng phiên** và các tham số liên quan, đặc biệt các ephemeral public keys, roles, nonce, protocol version và thuật toán đã chọn.

Nếu chứng cứ chỉ là “Bob biết một private key nào đó”, vẫn thiếu việc chứng minh đó là **private key thuộc đúng Bob**. Nếu certificate hợp lệ cho một domain khác nhưng Alice không kiểm tra hostname, đối thủ có thể trình ra certificate của domain họ sở hữu và vẫn vượt qua kiểm tra chain. Nếu chữ ký chỉ ký một chuỗi tĩnh như `"I am Bob"`, attacker có thể phát lại chữ ký ở phiên khác.

**Định danh phải đến từ ý định của bên kiểm tra**, không được chấp nhận chỉ vì phía bên kia tự khai trong payload.

### 4.1. Xác thực server và xác thực hai chiều

- **Server authentication:** client xác minh server; server có thể chưa xác minh danh tính client ở handshake và sẽ dùng một bước khác (token, tài khoản, OAuth, client certificate...) sau đó.
- **Mutual authentication:** cả hai phía đều chứng minh danh tính bằng cơ chế thích hợp, ví dụ mTLS hoặc hai chữ ký trên một authenticated handshake.
- **Authorization:** sau khi danh tính đã được chứng minh, hệ thống xét chủ thể đó có được gọi API, xem resource, dùng tenant hoặc thực hiện operation cụ thể không.

Vì vậy, **authenticated key agreement không đồng nghĩa với cho phép truy cập mọi dữ liệu**.

## 5. Một handshake cần ký hoặc MAC những byte nào?

Các protocol hoàn chỉnh thường xây dựng một **handshake transcript**: chuỗi byte biểu diễn có thứ tự các thông điệp và tham số đã trao đổi. Chữ ký/MAC/Finished được tính trên một digest hoặc cấu trúc rõ ràng liên quan đến transcript này.

Một mô hình cho bài học:

```text
transcript_v1 =
    protocolId
    || suiteId
    || "initiator" || aliceExpectedIdentity
    || "responder" || bobExpectedIdentity
    || aliceNonce
    || bobNonce
    || aliceX25519EphemeralPublic
    || bobX25519EphemeralPublic
```

Các ký hiệu `||` chỉ **ghép theo định dạng đã quy định**. Phải có encoding không nhập nhằng, như length-prefix hoặc một chuẩn serialization xác định. Không đơn giản nối string tự do.

Bên Alice xác minh rằng Bob đã ký **chính xác transcript của phiên Alice đang tham gia** bằng khóa xác thực tin cậy của Bob. Tương tự, Bob xác minh bằng chứng Alice nếu cần mutual authentication.

### 5.1. Ý nghĩa của từng thành phần

| Thành phần | Nếu không ràng buộc thì sao? |
| --- | --- |
| Protocol name/version | Chữ ký/khóa có thể bị đưa sang ngữ cảnh khác hoặc hiểu bằng parser khác |
| Cipher suite và KDF | Có nguy cơ negotiation confusion hoặc downgrade xuống thuật toán yếu |
| Initiator/responder roles | Có thể gây lỗi reflection/role confusion khi dùng cùng dữ liệu ở hai chiều |
| Expected peer identity | Có thể gán cùng khóa phiên cho hai danh tính khác nhau (unknown key-share) |
| Cả hai ephemeral public keys | Attacker thay một key mà chữ ký không phát hiện |
| Nonce/challenge mới của phiên | Chữ ký/bản tin phiên trước có thể bị phát lại |
| Cấu trúc thông điệp có độ dài rõ ràng | Cặp trường khác nhau có thể tạo cùng byte input |
| Application context/tenant | Khóa phiên có thể bị dùng nhầm sang dịch vụ, tenant hoặc operation khác |

Cần phân biệt **điều hai bên ký/verify** với **điều hai bên hiển thị trên màn hình**. Nếu UI ghi `Bob` nhưng transcript và verifier đang dùng khóa của `Mallory`, việc ký đúng một transcript không đúng identity vẫn không đem lại peer authentication mong muốn.

### 5.2. Ký public key thôi có đủ không?

Ký mỗi `bEphemeralPublic` có thể chứng minh bên giữ private signing key nào đó đã từng ký public key ấy, **nhưng chưa đủ ràng buộc**:
- Alice và nonce của Alice có thuộc phiên cụ thể không?
- Chữ ký dành cho client/server role nào?
- Bob có biết đang kết nối với Alice chứ không phải Mallory không?
- Hai bên có cùng protocol version và thuật toán không?

Thiết kế vững hơn là **ký transcript đã được xác định rõ**, hoặc dùng một protocol chuẩn có cách tạo và xác minh handshake authentication riêng. Không tự phát minh “ký một vài field là đủ” mà thiếu mô hình an ninh.

### 5.3. Freshness và replay

Một challenge/nonce mới được đưa vào transcript giúp chữ ký phiên cũ **không còn khớp transcript của phiên mới**, miễn cả hai bên thực sự kiểm tra đúng challenge vừa sinh. Nhưng việc có nonce trong bản tin không tự động cấm replay toàn bộ một request ở tầng ứng dụng.

Ứng dụng thực tế còn có thể cần:
- Sequence numbers hoặc unique record nonces cho AEAD;
- Replay cache, expiry và idempotency cho lệnh nhạy cảm;
- Session identifiers gắn với principal, scope, deadline và resource;
- Quy tắc chấp nhận lại handshake/resumption theo protocol cụ thể.

Không suy ra “có signature hợp lệ” nghĩa là “giao dịch chắc chắn mới và chưa từng thực thi”.

## 6. Những cơ chế xác thực khóa công khai phổ biến

### 6.1. Certificate và PKI

Bên gửi có thể trình một certificate chứa public key và subject/SAN; certificate này được một CA hoặc chuỗi trung gian ký. Bên nhận dùng trust anchors đã cấu hình để kiểm tra đường tin cậy.

Với một kết nối HTTPS/TLS, phía client cần kiểm tra **ít nhất theo chính sách triển khai**:
1. Certificate path có thể xây dựng tới trust anchor được phép.
2. Các chữ ký, hiệu lực thời gian, algorithm/key strength và constraints phù hợp.
3. **Danh tính server kỳ vọng khớp SAN/hostname** theo chuẩn ứng dụng; không lấy `CN` hay string từ server làm chứng cứ thay thế hostname validation.
4. Key usage và extended key usage phù hợp với mục đích server authentication nếu được áp dụng.
5. Chính sách revocation/CT/enterprise trust và các ràng buộc khác khi hệ thống yêu cầu.
6. Bắt tay chứng minh server đang giữ khóa bí mật tương ứng và transcript không bị sửa, theo chính protocol.

**Chain valid ≠ correct peer identity.** Một certificate hợp lệ của `attacker.example` không được chấp nhận khi client định kết nối `api.company.example`.

Đừng bật chế độ **trust-all certificates**, “insecure hostname verifier”, hoặc chấp nhận mọi self-signed certificate trong production để xử lý lỗi chứng thư.

### 6.2. Public-key pinning và khóa ký cấu hình trước

Nếu hai dịch vụ nội bộ đã phân phối khóa công khai xác thực qua kênh tin cậy, bên nhận có thể **pin** key hoặc fingerprint/SPKI hash theo chính sách.

Quy tắc:
- Khóa tin cậy được lấy từ một cấu hình/chính sách đã xác thực, **không** từ chính bản tin bên kia đang muốn được xác minh.
- Pin được so sánh trước khi chấp nhận chữ ký, theo identity và key usage đúng.
- Cần chiến lược dự phòng/rotation và xử lý compromise; pin sai hoặc hết hạn có thể gây outage.
- Với SPKI pin, phải thống nhất encoding/fingerprint format và chống nhầm lẫn với fingerprint của raw key encoding khác.

Pinning là lựa chọn phụ thuộc deployment, không mặc định tốt hơn PKI cho mọi hệ thống. Trên ứng dụng quy mô lớn, quản lý vòng đời pin sai có thể gây mất kết nối hàng loạt.

### 6.3. Trust on First Use (TOFU)

Mô hình TOFU ghi nhớ public key nhìn thấy ở **lần kết nối đầu tiên**, rồi cảnh báo nếu nó thay đổi. Nó có ích trong một số workflow như SSH khi người dùng/nhân viên xác minh fingerprint qua kênh phụ.

Giới hạn rất quan trọng: nếu attacker đang MITM **ngay lần đầu**, ứng dụng có thể pin nhầm khóa attacker. TOFU **không tương đương** với việc đã kiểm tra certificate/identity từ một nguồn tin cậy. Nếu public key thay đổi sau này, không tự chấp nhận key mới mà không có quy trình rotation/xác thực.

### 6.4. PSK, password-authenticated key exchange và kênh độc lập

Khi hai phía đã có **pre-shared key (PSK) đủ mạnh và được phân phối bí mật, đúng danh tính**, một protocol có thể dùng MAC/handshake binder để xác thực phiên. Phải ràng buộc PSK identity, transcript, cipher suite và quyền vào context; không lấy PSK từ dữ liệu do đối thủ tự khai.

Với password do con người nhớ, **không tự biến nó thành PSK** bằng `SHA-256(password)` rồi dùng như MAC key trong một handshake tự chế: có thể mở cửa tấn công từ điển/offline. Khi cần xác thực dựa trên password trong trao đổi khóa, nên dùng một **PAKE** (ví dụ một giao thức đã chuẩn hóa và triển khai được thẩm định) thay vì DH + password hash tự ghép.

Với thiết bị hai bên có thể so sánh một số xác minh ngắn hoặc quét QR trực tiếp, cơ chế **out-of-band authenticated comparison** cũng có thể ràng buộc public keys, nếu giao thức thiết kế đúng và kênh so sánh thực sự đáng tin.

## 7. Khóa phiên phải được dẫn xuất từ shared secret và transcript

Sau khi xác thực đối tác và thống nhất ephemeral public keys, X25519 tạo một shared secret. Không dùng trực tiếp raw X25519 bytes làm **key AES-GCM và nonce** theo quy tắc tự nghĩ.

Thường dùng KDF có context:

\[
Z = X25519(\text{own ephemeral private},\ \text{peer ephemeral public})
\]

\[
PRK = HKDF\text{-}Extract(\text{salt}, Z)
\]

\[
K_{app} = HKDF\text{-}Expand(PRK,\ \text{info},\ L)
\]

- `salt` của HKDF là input **không bắt buộc bí mật**, có thể liên quan tới transcript hash theo thiết kế protocol.
- `info` nên tách protocol/version, mục đích khóa và hướng dùng: ví dụ `client-to-server key` và `server-to-client key` là hai mục đích khác nhau.
- `L` là số byte output cần lấy, ví dụ **32** cho AES-256 key.
- Transcript hash giúp hai phía ràng buộc key derivation vào cùng handshake, nhưng **bản thân HKDF không chứng minh peer identity**: phải verify chữ ký/MAC và nguồn khóa xác thực trước khi sử dụng key.

Trong bài minh họa Java ở dưới, ta dùng `salt = SHA-256(transcript)` và một chuỗi `info` cố định cho **một key demo**, nhằm dễ kiểm tra hai phía tính giống nhau. **Ứng dụng hai chiều thực tế** cần tách key/nonce/traffic secret cho từng hướng và quản lý sequence numbers chống nonce reuse.

### 7.1. Key confirmation không đồng nghĩa identity proof

Sau khi derive key, hai phía có thể trao đổi một **confirmation MAC/Finished** chứng minh đối tác giữ secret phù hợp với transcript và lịch trình key.

Ví dụ khái niệm:

```text
confirmAlice = HMAC(K_confirm_A, "alice-finished" || transcriptHash)
confirmBob   = HMAC(K_confirm_B, "bob-finished"   || transcriptHash)
```

Bên nhận kiểm tra tag tương ứng trước khi cho ứng dụng gửi dữ liệu. Với protocol có xác thực tích hợp, một phần chứng cứ Finished/record integrity giúp phát hiện bất đồng secret hoặc transcript.

Nhưng nếu Mallory đã tạo **hai phiên DH giả** mà không xác thực identity, hắn cũng biết khóa confirmation cho hai phiên và có thể tạo hai MAC hợp lệ. **Key confirmation chỉ có giá trị đúng khi được kết hợp với việc xác thực đối tác và context của phiên.**

Trong ví dụ Java, ta so sánh HMAC tính ở hai phía **trong cùng một process** để minh họa khả năng đồng nhất key. Đó chưa phải một **wire-level key confirmation exchange** đầy đủ.

## 8. Java 17+: X25519 + Ed25519 + transcript binding

Dưới đây là một ví dụ **tự chạy được** bằng JDK 17+, có chủ đích phân biệt hai loại khóa:

- **X25519 ephemeral key pairs:** dùng riêng cho key agreement từng phiên.
- **Ed25519 identity key pairs:** dùng riêng để ký và xác minh transcript. Public keys dùng kiểm tra được giả lập **đã phân phối qua kênh xác thực/pin từ trước**.
- **Transcript encoding:** ghi từng trường bằng 4-byte length prefix (big-endian), giữ thứ tự cố định, tránh nhập nhằng chuỗi.
- **HKDF-SHA256:** minh họa bước Extract/Expand của RFC 5869 cho output 32 byte.
- **Kiểm thử tấn công:** thay ephemeral public key, đổi expected identity, ký bằng khóa impostor, dùng lại chữ ký trên challenge mới.

Tệp đầy đủ: `AuthenticatedKexDemo.java`.

```java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class AuthenticatedKexDemo {
    static final SecureRandom RNG = new SecureRandom();

    static byte[] random(int n) {
        byte[] data = new byte[n];
        RNG.nextBytes(data);
        return data;
    }

    // Framing: 4-byte length prefix followed by exactly that many bytes.
    static void field(DataOutputStream out, String value) throws IOException {
        field(out, value.getBytes(StandardCharsets.UTF_8));
    }

    static void field(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length);
        out.write(value);
    }

    static byte[] transcript(
            String initiator, String responder,
            byte[] aliceNonce, byte[] bobNonce,
            PublicKey aliceEphemeral, PublicKey bobEphemeral
    ) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            field(out, "auth-kex-demo:v1");
            field(out, "X25519+Ed25519+HKDF-SHA256");
            field(out, "initiator");
            field(out, initiator);
            field(out, "responder");
            field(out, responder);
            field(out, aliceNonce);
            field(out, bobNonce);
            // Java public key encoding: X.509 SubjectPublicKeyInfo format.
            field(out, aliceEphemeral.getEncoded());
            field(out, bobEphemeral.getEncoded());
        }
        return buffer.toByteArray();
    }

    static byte[] sign(PrivateKey key, byte[] message)
            throws GeneralSecurityException {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(key, RNG);
        s.update(message);
        return s.sign();
    }

    static boolean verify(PublicKey trustedKey, byte[] message, byte[] signature)
            throws GeneralSecurityException {
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(trustedKey);
        s.update(message);
        return s.verify(signature);
    }

    static byte[] agree(PrivateKey own, PublicKey peer)
            throws GeneralSecurityException {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(own);
        ka.doPhase(peer, true);
        return ka.generateSecret();
    }

    static byte[] hmac(byte[] key, byte[] message)
            throws GeneralSecurityException {
        Mac h = Mac.getInstance("HmacSHA256");
        h.init(new SecretKeySpec(key, "HmacSHA256"));
        return h.doFinal(message);
    }

    static byte[] applicationKey(byte[] sharedSecret, byte[] transcript)
            throws GeneralSecurityException {
        // RFC 5869 HKDF-Extract(salt, IKM) and first Expand block.
        byte[] salt = MessageDigest.getInstance("SHA-256").digest(transcript);
        byte[] prk = hmac(salt, sharedSecret);

        byte[] info = "auth-kex-demo:v1|app-key"
                .getBytes(StandardCharsets.UTF_8);
        byte[] input = Arrays.copyOf(info, info.length + 1);
        input[input.length - 1] = 1; // HKDF block counter 0x01

        try {
            return hmac(prk, input); // First full 32-byte HKDF-Expand block.
        } finally {
            Arrays.fill(prk, (byte) 0);
        }
    }

    public static void main(String[] args) throws Exception {
        // DEMO ONLY: Identity public keys are treated as pre-authenticated.
        // Real applications get them from validated certificates or trusted pins.
        KeyPairGenerator identity = KeyPairGenerator.getInstance("Ed25519");
        KeyPair aliceIdentity = identity.generateKeyPair();
        KeyPair bobIdentity = identity.generateKeyPair();
        PublicKey pinnedAliceKey = aliceIdentity.getPublic();
        PublicKey pinnedBobKey = bobIdentity.getPublic();

        KeyPairGenerator ephemeral = KeyPairGenerator.getInstance("X25519");
        KeyPair a = ephemeral.generateKeyPair();
        KeyPair b = ephemeral.generateKeyPair();
        byte[] aNonce = random(16);
        byte[] bNonce = random(16);

        byte[] t = transcript(
                "alice", "bob",
                aNonce, bNonce,
                a.getPublic(), b.getPublic()
        );

        byte[] aliceSignature = sign(aliceIdentity.getPrivate(), t);
        byte[] bobSignature = sign(bobIdentity.getPrivate(), t);

        boolean aliceTrustedBob = verify(pinnedBobKey, t, bobSignature);
        boolean bobTrustedAlice = verify(pinnedAliceKey, t, aliceSignature);

        System.out.println("Alice verifies Bob: " + aliceTrustedBob);
        System.out.println("Bob verifies Alice: " + bobTrustedAlice);
        if (!aliceTrustedBob || !bobTrustedAlice) {
            throw new GeneralSecurityException("Unauthenticated peer");
        }

        byte[] aliceShared = agree(a.getPrivate(), b.getPublic());
        byte[] bobShared = agree(b.getPrivate(), a.getPublic());
        byte[] aliceKey = applicationKey(aliceShared, t);
        byte[] bobKey = applicationKey(bobShared, t);

        System.out.println("Derived keys equal: "
                + MessageDigest.isEqual(aliceKey, bobKey));

        // Mallory changes Bob's ephemeral X25519 public key in transit.
        KeyPair maliciousEphemeral = ephemeral.generateKeyPair();
        byte[] substituted = transcript(
                "alice", "bob", aNonce, bNonce,
                a.getPublic(), maliciousEphemeral.getPublic()
        );
        System.out.println("Substituted X25519 key accepted: "
                + verify(pinnedBobKey, substituted, bobSignature));

        // Even with the original signature, changing the expected peer ID fails.
        byte[] wrongIdentity = transcript(
                "alice", "mallory", aNonce, bNonce,
                a.getPublic(), b.getPublic()
        );
        System.out.println("Changed peer identity accepted: "
                + verify(pinnedBobKey, wrongIdentity, bobSignature));

        // Impostor may sign the correct bytes, but does not hold Bob's key.
        KeyPair imposter = identity.generateKeyPair();
        System.out.println("Impostor signature accepted: "
                + verify(
                    pinnedBobKey, t,
                    sign(imposter.getPrivate(), t)
                ));

        // Bob's old signature is invalid over a new session challenge.
        byte[] fresh = transcript(
                "alice", "bob", random(16), bNonce,
                a.getPublic(), b.getPublic()
        );
        System.out.println("Replayed old response accepted: "
                + verify(pinnedBobKey, fresh, bobSignature));

        // Local demonstration only, not a real network Finished exchange.
        byte[] confirmA = hmac(
                aliceKey,
                "auth-kex-demo:v1|confirm:alice".getBytes(StandardCharsets.UTF_8)
        );
        byte[] confirmB = hmac(
                bobKey,
                "auth-kex-demo:v1|confirm:alice".getBytes(StandardCharsets.UTF_8)
        );
        System.out.println("Key confirmation valid: "
                + MessageDigest.isEqual(confirmA, confirmB));

        Arrays.fill(aliceShared, (byte) 0);
        Arrays.fill(bobShared, (byte) 0);
        Arrays.fill(aliceKey, (byte) 0);
        Arrays.fill(bobKey, (byte) 0);
    }
}
```

Biên dịch và chạy:

```bash
javac AuthenticatedKexDemo.java
java AuthenticatedKexDemo
```

Kết quả stdout đã được kiểm tra với các API Java 17 tương thích:

```text
Alice verifies Bob: true
Bob verifies Alice: true
Derived keys equal: true
Substituted X25519 key accepted: false
Changed peer identity accepted: false
Impostor signature accepted: false
Replayed old response accepted: false
Key confirmation valid: true
```

### 8.1. Vì sao thay X25519 public key bị phát hiện?

Bob ký transcript `t` chứa **cả** `aPub` và `bPub`. Khi Mallory thay `bPub` bằng `mPub` trên đường gửi cho Alice, Alice tái tạo `substituted` từ key mình thấy và thử xác minh chữ ký cũ của Bob:

```java
verify(pinnedBobKey, substituted, bobSignature)
```

Kết quả là `false` vì bytes đã ký không còn giống transcript Alice đang xác minh. Mallory không có Ed25519 private signing key đã được gắn tin cậy với định danh Bob để tạo lại chữ ký hợp lệ cho transcript mới.

Nếu Mallory thay **aPub** ở chiều Alice → Bob, Bob cũng cần tạo/kiểm tra transcript từ đúng giá trị nhận được và Alice phải xác minh transcript của phiên mình đang tham gia. Khi transcript hai phía khác nhau, cơ chế ký/chứng thực và confirmation đúng thiết kế sẽ làm handshake thất bại.

**Chú ý:** chữ ký hợp lệ có ý nghĩa chỉ vì `pinnedBobKey` đã được giả định tin cậy và ràng buộc vào identity Bob. Nếu chương trình nhận `pinnedBobKey` từ chính gói tin do Mallory gửi rồi không kiểm tra, Mallory chỉ việc thay luôn key và tự ký: vẫn MITM.

### 8.2. Vì sao đổi peer identity bị phát hiện?

Transcript không chỉ chứa key, mà còn chứa chuỗi identity **theo đúng role**. Trong demo, Alice có ý định giao tiếp với `bob`, nên thử xác minh chữ ký Bob trên transcript ghi `mallory` ở vị trí responder phải bị từ chối.

Trong production, identity phải được lấy từ **cấu hình hoặc kết quả resolve tin cậy của ứng dụng**, không được dùng trường `responder` do bên kia tự khai làm “expected identity”. Bảo đảm này quan trọng để tránh **unknown key-share (UKS)**: một phía tin rằng mình chia sẻ key với danh tính khác mặc dù các bí mật mật mã có thể khớp.

### 8.3. Vì sao chữ ký impostor bị từ chối?

Mallory có thể tạo Ed25519 key pair hợp lệ và ký transcript hợp lệ. Nhưng **bất kỳ key pair đúng thuật toán nào** không đồng nghĩa với key của Bob. Alice phải verify bằng `pinnedBobKey`, không verify bằng public key Mallory đính kèm trong cùng thông điệp.

Đây là chỗ PKI hoặc pinning giải quyết vấn đề **thuộc tính khóa gắn với danh tính**, còn Ed25519 Signature giải quyết việc **người ký có giữ private key hay không**.

### 8.4. Giới hạn của demo — không dùng như production handshake

Ví dụ minh họa một số nguyên tắc cốt lõi, **không phải một wire protocol hoàn chỉnh đã được chứng minh an toàn**:

- Tất cả key pairs được sinh và trao trong **một process**, nên không mô tả network framing, key distribution thực tế, trust anchor hay quá trình certificate validation.
- `pinnedAliceKey` và `pinnedBobKey` chỉ được gán từ các key vừa tạo; demo **giả định** bước tin cậy đã xảy ra. Không dùng pattern này để “học khóa” từ network rồi xem là đã authenticated.
- Không mô tả trạng thái handshake, retransmission, timeout, key update, retranscript negotiation hoặc xử lý cùng lúc nhiều phiên.
- Chưa verify untrusted X25519 public key bytes sau parse từ network; cần cơ chế decode/validate theo provider và chuẩn curve, từ chối malformed/low-order/all-zero secret nếu ứng dụng/provider chưa xử lý đầy đủ.
- Transcript demo dùng `getEncoded()` của Java public key; một implementation khác có thể dùng raw 32-byte X25519 keys. **Phải chỉ định một wire encoding duy nhất** và kiểm thử chéo, không ghép các encoding khác nhau.
- Code không triển khai đầy đủ bảo vệ replay, bảo vệ identity trong hệ thống nhiều tenant, authorization hoặc certificate revocation.
- Hai chiều traffic hiện dùng một output `app-key` demo. Protocol sản xuất phải dẫn xuất **khóa gửi/nhận tách biệt**, nonce/sequence numbers riêng, record framing, AEAD authentication và limits.
- Local `confirmA`/`confirmB` chỉ so sánh hai kết quả trong cùng chương trình; muốn key confirmation qua mạng phải thực sự **gửi, xác minh MAC/Finished có role/ transcript đúng**, không chỉ in `true`.
- Việc gọi `Arrays.fill(...)` làm giảm thời gian giữ các mảng tự sở hữu, nhưng không bảo đảm private key và mọi bản sao trung gian trong JVM/provider bị xóa ngay.
- Không tự thiết kế protocol mã hóa lai/mTLS thay cho TLS 1.3 hoặc một protocol được kiểm toán khi chỉ cần một kênh giao tiếp thông thường.

## 9. TLS 1.3 là ví dụ thực tế của authenticated key agreement

Đối với giao tiếp HTTPS hoặc dịch vụ nội bộ, **TLS 1.3** là lựa chọn chuẩn thay vì tự lắp X25519 + Ed25519 + HKDF rồi gửi dữ liệu. Một handshake TLS 1.3 ở chế độ xác thực server bằng certificate (mô tả khái niệm) gồm:

```text
Client                                              Server
------                                              ------
ClientHello
  supported versions, cipher suites,
  key_share (ECDHE), random    --------------------->
                                 <------------------ ServerHello
                                                      selected suite, key_share
                      [Hai bên dẫn xuất handshake traffic secrets]
                                 <------------------ EncryptedExtensions
                                 <------------------ Certificate
                                 <------------------ CertificateVerify
                                 <------------------ Finished
Client:
  validate certificate chain + expected DNS name
  verify CertificateVerify over TLS handshake transcript
  verify Finished + transcript integrity
  if all pass -> send Finished  -------------------->
                     [Application traffic keys bound to handshake]
Client/Server exchange authenticated encrypted application records
```

Điểm quan trọng:
- **Key share ECDHE** tạo shared secret, **không** xác thực identity riêng nó.
- **Certificate** cung cấp public key danh tính của server được ràng buộc theo PKI; client phải xác minh chain và **hostname đã kỳ vọng**.
- **CertificateVerify** chứng minh private signing key phù hợp với certificate và ký một cấu trúc liên quan handshake transcript, với context do TLS 1.3 định nghĩa.
- **Finished** là MAC xác thực transcript với khóa được dẫn xuất từ handshake secrets; phát hiện bất đồng/đổi handshake và bảo đảm key confirmation theo thiết kế TLS.
- **HKDF** và key schedule tạo traffic secrets cho từng giai đoạn và chiều, không dùng raw DH secret trực tiếp làm một khóa AES duy nhất.
- **mTLS** bổ sung xác thực certificate/client proof nếu ứng dụng yêu cầu; server authentication không có nghĩa client đã được cấp quyền API.
- TLS 1.3 có thêm các chế độ PSK, resumption, HelloRetryRequest và nhiều quy tắc khác; sơ đồ trên chỉ minh họa **certificate-based full handshake** phổ biến, không thay thế RFC.

### 9.1. Điều client thật phải kiểm tra

Giả sử client kết nối `https://payments.example.com`. Một certificate CA-signed cho `files.example.com` không nên được chấp nhận chỉ vì cùng một tổ chức hoặc cùng CA. Client có một **expected reference identity** là `payments.example.com`, phải so sánh certificate identity theo quy tắc phù hợp.

Trong Java, ưu tiên HTTP/TLS client chuẩn (như `java.net.http.HttpClient` hoặc cấu hình TLS provider đã kiểm tra) và các trust manager mặc định/được cấu hình đúng, thay vì tự cài:

```java
// Anti-pattern (KHÔNG dùng):
// TrustManager that accepts every certificate
// HostnameVerifier that always returns true
```

Không dùng các anti-pattern này để khắc phục lỗi môi trường development. Hãy thêm CA tin cậy đúng cách, bảo đảm server trình đúng certificate, hostname và certificate chain; không đánh đổi xác thực peer để “kết nối cho chạy”.

### 9.2. Server chỉ chứng minh khóa của mình, vậy client là ai?

TLS server authentication chứng minh client đang kết nối tới một server có identity đúng theo PKI. Nó **không tự** chứng minh user hoặc client application có quyền gọi API. Sau handshake, ứng dụng vẫn cần xác thực token/session/client cert hoặc cơ chế khác, rồi áp authorization tới resource.

Mô hình mTLS có thể giúp ràng buộc **workload identity** với certificate phía client, nhưng authorization vẫn phải xác minh certificate identity có quyền thực hiện đúng hành động/tenant. Đừng dùng một boolean “TLS connected” làm điều kiện đủ cho mọi quyền.

## 10. Ràng buộc identity và key tránh những tấn công nào?

### 10.1. Key substitution / classic MITM

Đối thủ thay ephemeral public key; nếu transcript được ký và verify với trusted identity key, thay thế đó bị phát hiện. Nếu không có authentication, hai phía tạo shared secrets với attacker.

### 10.2. Unknown key-share (UKS)

Một cấu hình hoặc protocol có thể khiến Alice tin khóa phiên thuộc Bob trong khi Bob tin mình đang giao tiếp với Mallory; hoặc nhiều key được ánh xạ vào sai identity. Điều này nguy hiểm dù attacker không trực tiếp biết shared secret.

Phòng tránh bằng:
- Ký/xác thực cả hai identities và roles;
- Kiểm tra peer identity **được kỳ vọng** thay vì identity tự khai;
- Ràng buộc transcript/traffic keys với protocol và endpoints;
- Kiểm tra mapping key → principal/tenant và quyền.

### 10.3. Downgrade/cipher-suite confusion

Nếu thuật toán do đối thủ thay đổi ngoài phạm vi đã được xác thực, hai bên có thể rơi vào thuật toán yếu hoặc hiểu nhầm bản tin. Ký/bind selected suite, version, KDF, curve và cipher options; reject không nằm trong allowlist. Không tự thử “nhiều thuật toán tới khi một signature pass”.

### 10.4. Replay và identity của phiên

Chữ ký trên một transcript cũ không còn hợp lệ khi một bên tạo nonce/ephemeral key mới và xây transcript mới. Nhưng không đủ để nói mọi tác vụ sẽ chống replay: ứng dụng vẫn cần anti-replay cho request và AEAD record nonces/sequence numbers theo giao thức.

### 10.5. Key compromise impersonation (KCI)

Trong một số protocol, lộ long-term authentication key của Alice có thể ảnh hưởng tới nhiều tính chất khác nhau, không chỉ quyền giả làm Alice với người khác. Phân tích KCI đòi hỏi xét chính xác cấu trúc authenticated key exchange, ai bị lộ khóa và ai đang cố impersonate ai.

Không tự tuyên bố một mẫu “DH + chữ ký” là chống mọi dạng KCI hoặc chống tất cả lộ khóa; nên chọn giao thức có phân tích bảo mật phù hợp threat model.

### 10.6. Forward secrecy

Nếu mỗi phiên dùng **ephemeral X25519 key pairs mới** và các khóa tạm thời được xóa đúng lúc, lộ **long-term identity signing key** về sau không tự đưa attacker về các shared secrets cũ từ bản ghi công khai, theo giả định an ninh của scheme và protocol. Đây là một thành phần của **forward secrecy**.

Tuy nhiên, nếu một bản ghi session key, ephemeral private key hoặc plaintext từng bị lộ trước đó, chữ ký không thể làm dữ liệu ấy “chưa từng lộ”. Mô hình forward secrecy đầy đủ cần một protocol được thiết kế và chứng minh phù hợp; đừng suy ra chỉ từ việc mỗi lần gọi `KeyPairGenerator` đều tạo key mới.

### 10.7. Invalid curve / malformed public keys

Khi nhận X25519 public key từ network, không mặc định mọi dãy byte đều là public key an toàn. Phải dùng decoder/provider và key agreement implementation phù hợp chuẩn, xử lý input malformed, kiểm tra kết quả shared secret không thuộc trường hợp cấm (ví dụ all-zero theo hướng dẫn X25519) và từ chối lỗi.

**Không tự thiết kế** bước chuyển/giải mã public key hoặc bỏ qua ngoại lệ provider để “đồng ý khóa bằng mọi giá”.

## 11. Liên hệ với key wrapping

Chủ đề cha của bài học là **thỏa thuận và bọc khóa**. Hai cơ chế liên quan nhưng không đồng nhất:

- **Key agreement:** hai phía đóng góp khóa tạm thời, cùng tính shared secret (X25519/ECDH), sau đó HKDF dẫn xuất traffic keys. Cần xác thực ephemeral keys/peer identity để chống MITM.
- **Key wrapping/transport:** một bên tạo khóa đối xứng (CEK/DEK), rồi bảo vệ khóa đó để bên nhận khôi phục, ví dụ RSA-OAEP với public key người nhận hoặc KEM/AEAD key wrap phù hợp. Cần xác thực **khóa công khai người nhận** để tránh wrap CEK cho kẻ tấn công.

Nếu attacker thay public key của người nhận trong scheme wrap, sender có thể mã hóa CEK cho attacker; ở key agreement, attacker có thể tạo hai shared secrets giả. **Gốc vấn đề chung là thiếu authenticated binding giữa public key và đúng đối tác**.

Trong các framework như HPKE, đã có những cấu hình bảo mật và cách xác thực khác nhau (base/auth/PSK tùy profile). Không được gọi mọi hình thức mã hóa bằng public key là “đã xác thực đối tác”; hãy đọc đúng mode và mô hình chứng thực mà scheme cung cấp.

## 12. Các lỗi phổ biến và cách sửa

| Lỗi/nhận định | Vì sao nguy hiểm | Cách làm đúng |
| --- | --- | --- |
| “X25519 cho cùng secret thì đã biết đó là Bob” | Attacker có thể tạo hai phiên với Alice/Bob | Verify trusted identity key và transcript |
| “Public key có fingerprint, vậy đã an toàn” | Fingerprint của key attacker cũng hợp lệ | So sánh với fingerprint/key từ kênh tin cậy |
| “Gửi certificate rồi không cần kiểm tra hostname” | Cert của host khác vẫn có chain hợp lệ | Kiểm tra expected DNS/application identity |
| “Accept all certificates để không bị SSL lỗi” | Tắt peer authentication, mở MITM | Cấu hình CA/PKI đúng |
| “Ký mỗi public key là đủ cho mọi handshake” | Không bind role, nonce, suite, partner | Ký/chứng thực toàn transcript theo spec |
| “Chữ ký hợp lệ với key gửi kèm chứng minh key thuộc Bob” | Attacker tự sinh cả signing key và signature | Trusted key được provision độc lập |
| “Nonce có thể giữ nguyên qua nhiều phiên” | Tạo cơ hội replay/nhầm phiên | Fresh nonce/ephemeral và kiểm tra state |
| “HKDF(sharedSecret) tự chống MITM” | Mallory có hai DH secrets riêng | Xác thực trước và bind transcript/KDF |
| “MAC Finished nghĩa là client là Alice” | MITM không xác thực cũng tính MAC cho phiên của hắn | Finished + identity authentication + binding |
| “TLS encryption bật là đủ, hostname verification không quan trọng” | Kênh có thể kết thúc tại endpoint sai | Full TLS certificate & name validation |
| “Sau khi handshake đúng thì mọi API đều được phép” | Authentication không thay authorization | Map identity tới quyền và resource |
| “Đổi thuật toán tới khi verify được” | Downgrade, protocol confusion | Version/cipher allowlist, fail closed |
| “TOFU là PKI đáng tin ngay từ lần đầu” | Lần đầu vẫn có thể bị MITM | Verify initial fingerprint out-of-band nếu cần |
| “Private identity signing key lộ không sao vì có ECDHE” | Attacker có thể giả identity trong phiên tương lai | Bảo vệ/rotate/revoke signing key |
| “Xóa Java byte[] là đủ để bảo đảm tất cả key đã biến mất” | JVM/provider có thể giữ bản copy | Memory hygiene, scope, KMS/HSM theo threat model |
| “Ciphertext AEAD có tag nên cũng biết người gửi là Bob” | Ai biết khóa hoặc public key có thể tạo message hợp lệ tùy scheme | Kiểm tra principal/sender authentication riêng |

## 13. Các bước kiểm thử một authenticated handshake

### 13.1. Positive tests

1. Alice mong gặp Bob; trusted Bob identity key đúng. Bob mong gặp Alice; trusted Alice key đúng.
2. Hai phía ghi transcript theo cùng version, encoding, roles, identities, nonces và ephemeral public keys.
3. Cả hai chứng cứ ký/MAC xác minh thành công với **keys được pin/cert validate đúng**.
4. Shared secret và traffic keys dẫn xuất đúng; record AEAD với mỗi hướng có nonce/sequence hợp lệ.
5. Application kiểm tra quyền của principal trước khi xử lý request.

### 13.2. Negative tests

| Ca thử | Điều kiện | Kỳ vọng |
| --- | --- | --- |
| Thay `bEphemeralPublic` | Mallory chèn một X25519 key khác | Chữ ký/transcript check fail |
| Thay `aEphemeralPublic` | Mallory sửa key phía initiator | Phiên Alice/Bob không thể thống nhất authenticated transcript |
| Sai trusted identity key | Verify bằng key Mallory thay vì Bob nhưng chính sách trông đợi Bob | Không chấp nhận identity Bob |
| Chữ ký impostor | Mallory dùng Ed25519 private key của mình ký | Verify bằng trusted Bob key thất bại |
| Sai tên đối tác | `bob` bị thay thành `mallory` | Identity/transcript binding thất bại |
| Sai role | Initiator/responder bị đảo | Fail theo transcript/signature rule |
| Sai version/suite | Chuyển X25519/HKDF profile sang thuật toán khác | Fail, không downgrade |
| Chữ ký từ phiên cũ | Nonce/challenge mới với signature cũ | Fail |
| Public key malformed/weak | Untrusted bytes, parser/curve input bất thường | Fail closed, không ra khóa phiên |
| Cert hợp lệ nhưng sai SAN | Server cert thuộc host khác | Hostname verification fail |
| Cert chain không được tin cậy | Self-signed unknown CA | Reject nếu không có trust policy phù hợp |
| Cert hết hạn/bị thu hồi | Không đạt certificate policy | Reject theo validation policy |
| Finished sai | Shared secrets hoặc transcript khác nhau | Reject trước app data |
| Replayed business request | Handshake đã xác thực nhưng lệnh bị gửi lại | Replay/idempotency policy xử lý |
| Unauthorized resource | Client đã xác thực nhưng không có quyền tenant/resource | Reject authorization |

Trong test automation, không chỉ assert “hai phía cùng derive một key” mà phải assert **đúng expected peer identity** và **các thay đổi attack-controlled bị từ chối**.

### 13.3. Debug interop khi hai bên không thể xác thực

Trình tự kiểm tra hợp lý:

1. Confirm protocol version, suite và handshake state tương đương.
2. Confirm **expected peer identity** và nguồn cấp trusted identity public key/certificate.
3. So sánh canonical transcript bytes hoặc transcript hash từ **fixture không nhạy cảm**; kiểm tra thứ tự trường, encoding public key, UTF-8, role, nonce và length prefix.
4. Nếu transcript khớp mà verify signature fail, kiểm tra signature algorithm/key format, certificate identity key, byte encoding của signature và policy.
5. Nếu chữ ký đúng mà session keys khác, kiểm tra đúng cặp ephemeral public/private keys, X25519 provider, HKDF extract salt/info và transcript hash.
6. Nếu key bằng nhau nhưng record fail, kiểm tra AEAD AAD, sequence/nonce management và traffic-key direction.
7. Nếu crypto pass nhưng API bị từ chối, kiểm tra policy authorization, principal mapping, tenant/resource và replay.

**Không log private keys, raw shared secrets, traffic keys hoặc production plaintext** để debug. Nếu cần trace, ưu tiên IDs phiên, thuật toán, độ dài key, trạng thái policy và transcript hash được quản lý theo quy định dữ liệu nhạy cảm.

## 14. Checklist triển khai trước production

### Client/Initiator

- [ ] Expected server identity được xác định bởi endpoint/application configuration đáng tin.
- [ ] Trusted public key của server được xác thực bằng certificate/path/name validation hoặc pin đã provision đúng.
- [ ] Server chứng minh private key tương ứng trên **đúng transcript** của phiên, bao gồm client nonce, server nonce, cả hai key shares, suite và roles.
- [ ] Không chấp nhận đổi key/cert/hostname một cách im lặng hay “trust all” khi có lỗi.
- [ ] Derive traffic keys từ authenticated handshake theo key schedule chuẩn và tách hướng.
- [ ] Không gửi dữ liệu nhạy cảm trước khi hoàn thành các bước handshake được yêu cầu.

### Server/Responder

- [ ] Key/certificate dùng xác thực server có vòng đời an toàn, lưu trong Keystore/KMS/HSM nếu phù hợp.
- [ ] Key shares/identities client được parse và validate đúng tiêu chuẩn, giới hạn kích thước/DoS.
- [ ] Nếu cần client authentication, xác minh client certificate/signature/PSK tương ứng với principal đã cấp.
- [ ] Chỉ map client identity tới tenant/role/resource sau khi chứng thực và kiểm tra chính sách.
- [ ] Chống replay và quản lý session/key update, sequence numbers và nonce theo protocol.

### Cả hai phía

- [ ] Không tự ghép DH+hash+AES rồi gọi là secure channel nếu chưa có formal authenticated key exchange design.
- [ ] Protocol/version, suite, roles, identity, transcript và encoding được định nghĩa, kiểm thử chéo.
- [ ] Có negative tests: MITM key replacement, wrong identity, wrong signer, replay, downgrade, malformed key, wrong Finished.
- [ ] Có chính sách certificate/key rotation, revocation, compromise response và audit.
- [ ] Có giới hạn số phiên, timeout, payload/key lengths, error behavior, resource consumption.
- [ ] Đảm bảo authentication không bị nhầm với authorization hoặc end-to-end identity across proxies.

## 15. Bài tập tự kiểm tra

**Câu 1.** Alice và Bob chạy X25519 thành công, nhưng public key trao đổi qua HTTP không có xác thực. Kẻ tấn công có thể nghe lén dữ liệu sau khi cả hai bật AES-GCM không?

**Đáp án:** Có thể khi attacker chủ động MITM bằng cách thay public keys và tạo hai phiên khóa riêng. Hắn có keys cho cả hai phiên nên có thể giải mã và mã hóa lại mà không phá X25519/AES-GCM.

**Câu 2.** Bên gửi gửi `bPub` và `SHA-256(bPub)` trong cùng gói. Alice có chắc `bPub` thuộc Bob không?

**Đáp án:** Không. Kẻ tấn công thay cả hai. Fingerprint phải được so với giá trị tin cậy từ trước hoặc xác minh qua một kênh xác thực độc lập.

**Câu 3.** Bob ký `bPub` nhưng không đưa `aPub` và client nonce vào bằng chứng. Vấn đề tiềm ẩn?

**Đáp án:** Bằng chứng không gắn đủ vào phiên cụ thể và public key của Alice, có thể gây replay/misbinding/unknown-key-share trong thiết kế thiếu ràng buộc. Nên ký transcript có cả hai key shares, identities, roles, suite và freshness.

**Câu 4.** TLS server đưa certificate hợp lệ CA nhưng cho hostname khác. Client có nên tiếp tục?

**Đáp án:** Không; certificate chain valid chưa chứng minh **đúng expected hostname**. Cần kiểm tra SAN/reference identity cùng certificate policy.

**Câu 5.** Nếu Mallory tự sinh Ed25519 key và ký đúng transcript của Bob thì có qua verify không?

**Đáp án:** Không khi Alice verify bằng **trusted Bob public key**. Nếu Alice lại dùng public key Mallory gửi kèm làm trust source, thiết kế đã sai và có thể bị MITM.

**Câu 6.** Vì sao cùng một shared secret không chứng minh hai bên muốn giao tiếp với cùng identity?

**Đáp án:** Secret chứng minh phép key agreement phù hợp, không chứng minh name/key binding. Phải ràng buộc expected identities và roles trong authenticated transcript và trusted-key mapping.

**Câu 7.** Key confirmation MAC đúng có chứng minh ngay rằng người đối diện là Bob không?

**Đáp án:** Không nếu chưa có peer authentication. MITM có thể tính confirmation MAC cho hai phiên DH do hắn lập. Confirmation chỉ có ý nghĩa trong ngữ cảnh xác thực đúng.

**Câu 8.** Khóa ký Ed25519 dài hạn của Bob bị lộ hôm nay. Điều gì xảy ra với phiên mới và phiên cũ?

**Đáp án:** Attacker có thể giả identity Bob trong handshake tương lai cho đến khi khóa được thu hồi/đổi; tính bí mật dữ liệu cũ phụ thuộc protocol, ephemeral private-key lifecycle, traffic keys và các secret có bị lộ hay không. Ephemeral DH đúng thiết kế có thể góp phần cho forward secrecy, nhưng không được hứa hẹn tuyệt đối chỉ dựa vào một chi tiết.

**Câu 9.** TOFU an toàn trước MITM ngay lần kết nối đầu tiên không?

**Đáp án:** Không. Nếu attacker xuất hiện lúc đầu, ứng dụng có thể lưu nhầm key attacker. Cần xác minh fingerprint qua kênh phụ khi mô hình an ninh yêu cầu.

**Câu 10.** Trong Java 17, `KeyAgreement.getInstance("X25519")` tạo shared secret; còn `Signature.getInstance("Ed25519")` phục vụ gì?

**Đáp án:** X25519 tạo shared secret qua hai key shares; Ed25519 ký và xác minh transcript để ràng buộc handshake vào trusted identity signing key. Hai thuật toán có nhiệm vụ khác nhau, không thay thế nhau.

**Câu 11.** Có cần thêm authorization sau khi mTLS đã chứng minh client certificate không?

**Đáp án:** Có. Chứng minh identity không cấp tự động mọi quyền; server vẫn phải map principal tới policy cho từng tenant/operation.

**Câu 12.** Khi cần bảo vệ CEK gửi cho Bob bằng RSA-OAEP thay vì key agreement, có cần xác thực public key Bob không?

**Đáp án:** Có. Nếu attacker thay public key Bob bằng key của mình, CEK có thể bị mã hóa cho attacker. Mô hình khác nhưng cùng vấn đề key–identity binding.

## 16. Sơ đồ ghi nhớ

```text
KEY AGREEMENT ALONE:
 Alice --- aPub --> [Mallory swaps] --- mPub --> Bob
 Alice <-- mPub --- [Mallory swaps] <-- bPub --- Bob
     |                      |                     |
  secret A-M          two shared secrets        secret M-B
     |                      |                     |
  encrypted channel <---- attacker ----> encrypted channel
  (not to Bob)           can decrypt            (not to Alice)

AUTHENTICATED KEY AGREEMENT:
 Alice <-> exchanged ephemeral public keys <-> Bob
   |                                              |
   |-- expected identity "bob"                    |-- expected identity "alice"
   |-- trusted Bob signing key                    |-- trusted Alice signing key
   |                                              |
   +---- same canonical transcript:
          version, suite, roles, identities,
          nonces, aPub, bPub
                    |
             Sign/verify transcript
                    |
             Reject substituted key
                    |
            X25519 + HKDF transcript-bound
                    |
          Direction-separated traffic keys
                    |
          Finished/key confirmation + AEAD
                    |
          Authenticate principal + authorize action
```

**Điểm quan trọng nhất:** *Thỏa thuận được cùng một khóa không nói lên rằng khóa đó thuộc đúng đối tác*. Muốn chống đánh tráo public key, phía kiểm tra phải có một **gốc tin cậy về danh tính/khóa**, xác minh **bằng chứng trên đúng transcript phiên hiện tại**, rồi dẫn xuất/sử dụng khóa phiên theo protocol được chuẩn hóa. Với kết nối ứng dụng thực tế, ưu tiên **TLS 1.3/mTLS được cấu hình xác thực certificate và hostname đúng cách** thay cho một handshake tự chế.

### Tài liệu tham khảo

- RFC 7748 — *Elliptic Curves for Security* (X25519): https://www.rfc-editor.org/rfc/rfc7748
- RFC 8032 — *Edwards-Curve Digital Signature Algorithm (EdDSA)* (Ed25519): https://www.rfc-editor.org/rfc/rfc8032
- RFC 5869 — *HMAC-based Extract-and-Expand Key Derivation Function (HKDF)*: https://www.rfc-editor.org/rfc/rfc5869
- RFC 8446 — *The Transport Layer Security (TLS) Protocol Version 1.3*: https://www.rfc-editor.org/rfc/rfc8446
- RFC 5280 — *Internet X.509 Public Key Infrastructure Certificate and CRL Profile*: https://www.rfc-editor.org/rfc/rfc5280
- NIST SP 800-56A Rev. 3 — *Pair-Wise Key-Establishment Schemes Using Discrete Logarithm Cryptography*: https://csrc.nist.gov/pubs/sp/800/56/a/r3/final
- Java SE 17 API, `KeyAgreement`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyAgreement.html
- Java SE 17 API, `Signature`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html
- Java SE 17 API, `KeyPairGenerator`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyPairGenerator.html
- Java SE 17 API, `Mac`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Mac.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-establishment-and-wrapping-006">10.6 Cipher.WRAP_MODE, UNWRAP_MODE và thuật toán bọc khóa</a>

<details>
<summary>Xem chi tiết</summary>

# 10.6 Cipher.WRAP_MODE, UNWRAP_MODE và thuật toán bọc khóa

## Mục tiêu học tập

Sau bài này, bạn sẽ:

1. Giải thích **key wrapping** là gì, phân biệt **Key Encryption Key (KEK)** và **Data Encryption Key (DEK)** trong kiến trúc mã hóa khóa.
2. Hiểu chính xác cách dùng **Cipher.WRAP_MODE**, **Cipher.UNWRAP_MODE**, **wrap(Key)** và **unwrap(byte[], String, int)** trong Java Cryptography Architecture (JCA/JCE).
3. Lựa chọn đúng thuật toán bọc khóa: **AESWrap (RFC 3394 / AES-KW)**, **AESWrapPad (RFC 5649 / AES-KWP)**, **RSA-OAEP**, hoặc cơ chế KMS/HSM theo mục tiêu và điều kiện triển khai.
4. Viết Java 17+ để bọc và mở bọc khóa AES, kiểm tra toàn vẹn wrapped key, và cấu hình tường minh OAEP khi dùng RSA.
5. Hiểu giới hạn đối với khóa không thể xuất ra ngoài, metadata cần mang theo, khả năng tương thích giữa provider, luân chuyển KEK, sai lầm phổ biến và xử lý lỗi an toàn.

**Thông điệp cốt lõi:** Bọc khóa là thao tác bảo vệ **một khóa mật mã khác** bằng khóa/phương tiện bảo vệ phù hợp; nó không phải chỉ “Base64-encode khóa”. Trong Java, hai mode WRAP/UNWRAP đặt **Cipher** vào trạng thái chuyên dùng cho đối tượng **Key**. Độ an toàn phụ thuộc vào **thuật toán bọc, khóa bọc, tham số, provider, quản lý metadata và quyền truy cập**; chỉ chọn đúng hằng số WRAP_MODE chưa làm mọi cơ chế bọc trở nên an toàn.

## 1. Bọc khóa là gì, tại sao cần?

Giả sử dịch vụ lưu hồ sơ bằng **AES-GCM**. Mỗi hồ sơ hoặc mỗi nhóm dữ liệu có một khóa AES ngẫu nhiên **DEK** để mã hóa nội dung. Nếu lưu DEK dưới dạng plaintext cạnh ciphertext, kẻ chiếm được cả hai sẽ giải mã được hồ sơ. Hệ thống cần một cách lưu hoặc chuyển **DEK ở trạng thái được bảo vệ**.

Giải pháp phổ biến là dùng **KEK** để bọc DEK:

~~~text
TẠO VÀ LƯU

Plaintext hồ sơ
      |
 [AES-GCM encrypt] <--------- DEK (Data Encryption Key)
      |                           |
Ciphertext + tag             [KEY WRAP] <------ KEK
      |                           |
      +---- lưu record ------> wrappedDEK
                             (không phải raw DEK)

ĐỌC VÀ GIẢI MÃ

wrappedDEK --------> [KEY UNWRAP] <------ KEK được cấp quyền
                           |
                          DEK
                           |
Ciphertext + tag ----> [AES-GCM decrypt + verify tag]
                           |
                   Plaintext đã xác thực
~~~

Hai tầng bảo vệ có hai vai trò riêng:

- **DEK:** mã hóa nội dung (ví dụ AES-GCM), thường sinh ngẫu nhiên và có phạm vi sử dụng hẹp.
- **KEK:** bọc/giải bọc DEK; thường nằm trong **KMS/HSM/keystore**, có chính sách truy cập và rotation riêng.

Đây là nguyên lý cốt lõi của **envelope encryption**: dùng mã hóa đối xứng hiệu quả cho dữ liệu lớn, và cơ chế bọc khóa/trao đổi khóa để bảo vệ DEK nhỏ.

**Quan trọng:** wrappedDEK vẫn là dữ liệu nhạy cảm về mặt quản trị, nhưng không tương đương raw DEK khi thiết kế an toàn. Mức an toàn không đến từ tên biến hoặc chuỗi Base64, mà từ thực chất thuật toán và quyền truy cập KEK.

### 1.1 Bọc khóa khác gì mã hóa dữ liệu thông thường?

Mã hóa dữ liệu biến **byte[] plaintext → byte[] ciphertext**. Bọc khóa nhận **Key → byte[] wrappedKey** và mở bọc nhận byte[] cùng thông tin thuật toán/loại khóa để tạo lại **Key**.

Bản chất mật mã có thể dùng primitives gần nhau, nhưng **API và mục tiêu khác**:

| Nhu cầu | Cơ chế thường dùng | Đầu vào → đầu ra |
| --- | --- | --- |
| Mã hóa hồ sơ/ảnh/file | AES-GCM | plaintext byte[] → ciphertext + tag |
| Bảo vệ một khóa AES để lưu trữ | AES-KW/AES-KWP hoặc KMS WrapKey | Key DEK + KEK → wrapped key bytes |
| Gửi một khóa AES cho người có public key RSA | RSA-OAEP | Key DEK + public key → ciphertext của khóa |
| Dẫn xuất khóa từ secret chung | HKDF/KDF theo yêu cầu | keying material → khóa có phân tách ngữ cảnh |
| Chuyển byte thành text | Base64, hex | byte[] ↔ text, **không thêm bảo mật** |

**Key wrapping không phải key derivation** và không đồng nghĩa key agreement. Bọc khóa dùng để **vận chuyển/lưu một khóa hiện có** dưới dạng được bảo vệ; ECDH/X25519 là cơ chế để các bên **thỏa thuận material chung**, còn KDF chuyển material đó thành khóa thích hợp.

## 2. Cipher trong Java: bốn chế độ và cách gọi

Class **javax.crypto.Cipher** cung cấp bốn hằng opmode quan trọng:

| Opmode | Phương thức làm việc điển hình | Mục tiêu |
| --- | --- | --- |
| **Cipher.ENCRYPT_MODE** | update(...), doFinal(...) | Mã hóa dữ liệu byte |
| **Cipher.DECRYPT_MODE** | update(...), doFinal(...) | Giải mã dữ liệu byte |
| **Cipher.WRAP_MODE** | **wrap(Key key)** | Bọc một đối tượng Key, trả byte[] |
| **Cipher.UNWRAP_MODE** | **unwrap(byte[] wrapped, String algorithm, int keyType)** | Mở bọc để tạo lại Key |

Cấu trúc điển hình:

~~~java
// Đoạn minh họa; các biến kek, dek, wrapped đã có sẵn.
Cipher wrapper = Cipher.getInstance("AESWrap");
wrapper.init(Cipher.WRAP_MODE, kek);
byte[] wrapped = wrapper.wrap(dek);

Cipher unwrapper = Cipher.getInstance("AESWrap");
unwrapper.init(Cipher.UNWRAP_MODE, kek);
Key recovered = unwrapper.unwrap(
        wrapped, "AES", Cipher.SECRET_KEY);
~~~

Đây là đoạn mã phụ thuộc các biến/nhập khẩu từ ngữ cảnh bên ngoài, **không phải chương trình độc lập**. Chương trình Java 17 có thể biên dịch/chạy được nằm ở mục 6.

### 2.1 Điều gì xảy ra với wrap(Key)?

**Cipher.wrap(Key key)** yêu cầu một đối tượng Key cần được bọc. Provider dùng thuật toán, mode, wrapping key và tham số đã cấu hình để tạo **byte[]**. Với nhiều provider phần mềm, thao tác này sử dụng **dạng encoded của Key** để bảo vệ bằng mật mã. Tuy nhiên một số HSM/provider có thể hỗ trợ bọc khóa trong phần cứng mà không bao giờ xuất khóa ở dạng plaintext cho JVM.

Không được giả định mọi Key đều có thể wrap:

- **key.getEncoded() có thể trả null** nếu khóa không cho phép xuất ra ngoài hoặc không có dạng encoding được hỗ trợ.
- **Cipher.wrap(...)** có thể ném **InvalidKeyException** khi không thể hoặc không an toàn để bọc khóa; **IllegalBlockSizeException** có thể xảy ra tùy thuật toán và độ dài encoded key.
- Provider có thể không hỗ trợ wrap/unwrap đối với thuật toán hoặc loại Key cụ thể, kể cả khi hỗ trợ encrypt/decrypt.
- Quyền sử dụng KEK có thể bị giới hạn trong HSM/KMS (chỉ wrap, chỉ unwrap, chỉ cho một loại khóa).

**WRAP_MODE không bỏ qua chính sách bảo vệ private key hoặc quy định extractability của HSM.**

### 2.2 unwrap(...) không thể chỉ nhận một mảng byte

Chữ ký phương thức:

~~~java
Key unwrap(byte[] wrappedKey,
           String wrappedKeyAlgorithm,
           int wrappedKeyType)
~~~

Hai đối số cuối có ý nghĩa:

- **wrappedKeyAlgorithm:** tên thuật toán của **khóa sau khi mở bọc**, chẳng hạn **"AES"**, **"HmacSHA256"**, hoặc **"RSA"**. Đây **không phải** tên thuật toán bọc "AESWrap" hoặc "RSA/ECB/OAEPPadding".
- **wrappedKeyType:** một trong ba hằng **Cipher.SECRET_KEY**, **Cipher.PUBLIC_KEY**, **Cipher.PRIVATE_KEY** để provider biết kiểu Key cần tái tạo.

Ví dụ đúng:

~~~java
Key dataKey = unwrapper.unwrap(
        wrappedBytes, "AES", Cipher.SECRET_KEY);
~~~

Ví dụ sai về mặt ý nghĩa API:

~~~java
// Sai: AESWrap là thuật toán BỌC, không phải thuật toán của DEK.
Key dataKey = unwrapper.unwrap(
        wrappedBytes, "AESWrap", Cipher.SECRET_KEY);
~~~

Đừng tự chọn thuật toán/loại khóa từ các chuỗi không tin cậy. **Algorithm name và key type truyền cho unwrap không tự được xác thực bởi phép bọc trong mọi định dạng.** Với nhiều provider, byte key đã mở bọc có thể được gán một nhãn thuật toán theo tham số caller; phải kiểm tra schema, key length và mục đích khóa.

### 2.3 Cipher unwrap trả về Key, không nhất thiết trả SecretKeySpec

Kết quả khai báo kiểu **java.security.Key**. Sau khi kiểm tra đúng loại, ứng dụng có thể dùng như SecretKey, PublicKey hoặc PrivateKey nếu provider trả kiểu tương ứng.

~~~java
Key recovered = unwrapper.unwrap(
        wrapped, "AES", Cipher.SECRET_KEY);
if (!(recovered instanceof javax.crypto.SecretKey)) {
    throw new IllegalStateException("Unexpected key type");
}
javax.crypto.SecretKey dek = (javax.crypto.SecretKey) recovered;
~~~

Đoạn trên là minh họa, cần biến unwrapper và wrapped có sẵn.

**Không gọi getEncoded() chỉ để sử dụng lại khóa nếu provider có thể thao tác trực tiếp bằng đối tượng Key.** Với khóa trong HSM, encoded bytes có thể không bao giờ xuất hiện. Nếu cần bảo vệ dữ liệu, tốt hơn là truyền Key cho Cipher hợp lệ và giới hạn phạm vi lộ khóa.

## 3. Các thuật toán bọc khóa quan trọng

### 3.1 AESWrap / AES-KW (RFC 3394, NIST SP 800-38F)

**AESWrap** là tên thuật toán Java cho **AES Key Wrap (AES-KW)** theo RFC 3394. Nó dùng khóa AES làm **KEK** để bảo vệ bytes của khóa khác, kèm cơ chế kiểm tra integrity theo cấu trúc thuật toán.

Ví dụ tương thích điển hình:

~~~text
KEK:      AES key 128 hoặc 256 bit
DEK:      AES key 128 bit (16 byte)
Algorithm: AESWrap
wrapped:  24 byte = 16 byte DEK + 8 byte overhead
~~~

Với **AES-KW RFC 3394**, dữ liệu đầu vào cần dài tối thiểu **16 byte** và có độ dài là bội số của **8 byte** (cho thuật toán chuẩn). Đầu ra dài hơn đầu vào **8 byte**; ví dụ 16 byte thành 24 byte, 32 byte thành 40 byte.

Cơ chế có **kiểm tra integrity** bằng giá trị kiểm tra theo RFC 3394; kẻ không biết KEK rất khó thay đổi wrapped key rồi vẫn được unwrap chấp nhận. Tuy vậy:

- AES-KW **không phải** một cơ chế AEAD có AAD tùy ý như AES-GCM.
- Nó **không ràng buộc tự động** tenant ID, key ID, thời hạn hay các trường metadata bên ngoài.
- Đừng gọi overhead 8 byte là **tag GCM 128 bit**; tính chất và định dạng khác nhau.
- Nó không tự xác thực danh tính người gửi hoặc chứng minh khóa được phép dùng cho mọi nghiệp vụ.

Oracle Java SE 17 liệt kê **AESWrap** tương ứng AES-KW RFC 3394; provider SunJCE hỗ trợ **AESWrap**. Tùy provider, có thể dùng transformation **"AESWrap"** hoặc những tên định dạng tương đương như **"AES/KW/NoPadding"** khi được hỗ trợ. Cần kiểm tra tên thuật toán và khả năng tương thích thực tế, không tự giả định các alias luôn tương đương ở mọi provider.

### 3.2 AESWrapPad / AES-KWP (RFC 5649)

**AES-KWP** mở rộng AES-KW để xử lý cả dữ liệu khóa có **độ dài không phải bội số của 8 byte** hoặc ngắn hơn phạm vi của AES-KW, bằng cấu trúc padding/integrity chuyên dụng.

Tên Java chuẩn là **AESWrapPad** theo RFC 5649 và NIST SP 800-38F. Đừng tự lấy một byte[] ngắn rồi thêm zero thủ công để “vừa AESWrap”: bên unwrap sẽ cần biết chính xác độ dài gốc và chuẩn hóa, và thêm padding tự chế có thể làm sai thuộc tính bảo mật/giao thức.

**Không thể trộn AESWrap và AESWrapPad** chỉ vì cả hai đều bắt đầu bằng chữ AES. Chúng là hai thuật toán với cấu trúc wrapped bytes khác nhau.

Java có danh mục tên chuẩn cho AESWrapPad, nhưng **việc provider cụ thể trong môi trường triển khai hỗ trợ hay không phải được kiểm tra**. Không suy từ việc provider có AESWrap mà kết luận AESWrapPad cũng hoạt động. Ví dụ chạy được bên dưới dùng AESWrap đã được tài liệu hóa cho SunJCE.

### 3.3 RSA-OAEP dùng public key để bọc secret key

Nếu bên gửi chỉ có **public key RSA** của bên nhận, có thể dùng **RSA-OAEP** để wrap một DEK AES nhỏ; người nhận dùng private key để unwrap.

~~~text
Sender                                Recipient
Public RSA key                       Private RSA key
     |                                     |
WRAP_MODE + RSA-OAEP                  UNWRAP_MODE + RSA-OAEP
     |                                     |
wrap(AES DEK)                         unwrap(bytes, "AES", SECRET_KEY)
     |                                     |
wrapped DEK ----------- gửi ----------> AES DEK
~~~

Cấu hình OAEP cần thống nhất tường minh:

- **OAEP digest** (ví dụ SHA-256).
- **MGF** (thường MGF1).
- **MGF1 digest** (ví dụ SHA-256).
- **Label bytes** (ví dụ empty label hoặc UTF-8 của "key:v1").
- RSA key pair tương ứng và ciphertext encoding.

Trong Java 17, có thể dùng:

~~~java
OAEPParameterSpec spec = new OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA256,
        new PSource.PSpecified(
                "key:v1".getBytes(StandardCharsets.UTF_8))
);
~~~

Đây là đoạn cấu hình minh họa, không phải chương trình độc lập. **Không suy luận MGF1 digest từ tên transformation có SHA-256**: với một số provider, các mặc định có thể khác nhau. Truyền **OAEPParameterSpec** giống nhau vào **Cipher.init** cho cả hai bên.

Với RSA-2048, kết quả wrap thường là **256 byte**; với OAEP SHA-256, plaintext tối đa **190 byte** (k - 2hLen - 2), đủ chứa khóa AES 16/32 byte nhưng không đủ để mã hóa cả file. RSA-OAEP có ngẫu nhiên hóa nên wrapped key khác nhau mỗi lần wrap cho cùng DEK.

**RSA-OAEP không tự xác thực người gửi**: ai biết public key của người nhận đều có thể tạo ciphertext mới. Nếu cần chứng minh nguồn gốc, dùng cơ chế chữ ký/xác thực giao thức độc lập.

### 3.4 AES-GCM có thể mã hóa key bytes không?

Về mặt mật mã, **AES-GCM với nonce duy nhất và AAD** có thể bảo vệ các byte bí mật nhỏ, kể cả encoded key, khi được sử dụng qua một giao thức được thiết kế đúng.

Nhưng **không suy ra mọi provider hỗ trợ Cipher.wrap khi Cipher được cấu hình ở AES/GCM/NoPadding**; khả năng wrap/unwrap là chức năng của từng CipherSpi/provider. Nếu chọn AES-GCM để bảo vệ key bytes, phải quản lý **nonce, tag, AAD**, tuân thủ điều kiện **nonce duy nhất theo KEK**, và đưa metadata vào định dạng kết quả. Với tác vụ key wrap thông thường, AES-KW/KWP hoặc dịch vụ wrap key chuyên dụng giúp tránh tự thiết kế giao thức không cần thiết.

**Không dùng AES/ECB/NoPadding, AES/CBC/PKCS5Padding hoặc XOR tùy ý để tự bọc khóa**, rồi cho rằng độ bí mật và integrity tương đương AES-KW. CBC thuần không cung cấp xác thực toàn vẹn; ECB làm lộ cấu trúc. Những thuật toán này không trở thành key wrap an toàn chỉ nhờ gọi WRAP_MODE.

## 4. So sánh thuật toán theo loại khóa và giới hạn

| Thuật toán | Khóa bọc (KEK) | Đối tượng phù hợp | Điều kiện nổi bật | Khả năng integrity |
| --- | --- | --- | --- | --- |
| **AESWrap / AES-KW** | AES đối xứng | Encoded secret key bytes phù hợp độ dài | Ít nhất 16 byte, bội số 8 | Có kiểm tra theo chuẩn AES-KW |
| **AESWrapPad / AES-KWP** | AES đối xứng | Encoded key bytes có độ dài linh hoạt | Provider phải hỗ trợ đúng chuẩn KWP | Có kiểm tra theo chuẩn AES-KWP |
| **RSA-OAEP** | Public key RSA của người nhận | DEK nhỏ; dùng private key tương ứng để unwrap | OAEP hash, MGF1 digest, label, giới hạn modulus | Có kiểm tra cấu trúc OAEP khi giải mã; không xác thực người gửi |
| **KMS/HSM WrapKey** | Key nội bộ KMS/HSM theo quyền | Key được quản lý bởi hạ tầng khóa | API/policy/key handle của hệ thống, quyền và audit | Theo thuật toán và bảo đảm của dịch vụ |
| **AES-GCM trên encoded key bytes** | AES đối xứng | Key bytes trong protocol tự thiết kế đúng | Nonce duy nhất, AAD, tag, framing | Có AEAD khi xác minh tag thành công |

**Không có “một thuật toán bọc tốt cho mọi loại khóa”.** Cần xét mô hình tin cậy, yêu cầu tương thích, khả năng export key, giới hạn kích thước và loại metadata phải được xác thực.

### 4.1 Tại sao tên AESWrap cũng không giải thích được DEK là AES?

**"AESWrap"** là tên cipher dùng để bảo vệ key bytes. DEK được bọc có thể là một loại secret key khác nếu dạng byte và thuật toán/độ dài hợp lệ với cơ chế bọc.

Sau unwrap, Java cần biết tên thuật toán và loại Key cần khôi phục:

~~~text
Wrapping algorithm: AESWrap
Wrapping key:       KEK là AES
Wrapped key type:   Cipher.SECRET_KEY
Wrapped algorithm:  "AES" hoặc "HmacSHA256" theo hợp đồng
~~~

Không được nhầm wrapping algorithm với **wrappedKeyAlgorithm** trong chữ ký unwrap.

## 5. SecretKey, PublicKey, PrivateKey và encoding

JCA có interface **java.security.Key** cùng các interface con:

- **javax.crypto.SecretKey:** khóa đối xứng; dạng mã hóa thường là **RAW** khi export được.
- **java.security.PublicKey:** khóa công khai; dạng mã hóa phổ biến **X.509 SubjectPublicKeyInfo**.
- **java.security.PrivateKey:** khóa bí mật bất đối xứng; dạng mã hóa phổ biến **PKCS#8** nếu được phép xuất.

Các constant type lúc unwrap:

~~~java
Cipher.SECRET_KEY
Cipher.PUBLIC_KEY
Cipher.PRIVATE_KEY
~~~

Đây là **hằng chỉ loại Key**, không phải thuật toán mật mã và không phải giá trị mode. Sai loại Key hoặc tên algorithm có thể làm unwrap thất bại, hoặc tạo đối tượng key không phù hợp với mục đích sử dụng sau đó.

### 5.1 getAlgorithm(), getFormat(), getEncoded()

**Key.getAlgorithm()** nói tên thuật toán (ví dụ "AES"). **Key.getFormat()** cho biết định dạng encoded key khi được hỗ trợ. **Key.getEncoded()** trả byte[] encoding hoặc có thể **null**.

Với secret key phần mềm xuất được, có thể gặp:

~~~text
key.getAlgorithm(): AES
key.getFormat():    RAW
key.getEncoded():   [16, 24 hoặc 32 byte]
~~~

Các byte này là **secret**, không phải metadata công khai. Không ghi chúng vào log, metrics, file cấu hình hoặc response. Việc chúng có thể được trả về cho test **không có nghĩa luôn nên export** trong production.

### 5.2 Khóa HSM/KMS không export được

Một HSM có thể lưu key material dưới một key handle và từ chối getEncoded(). Một Cipher phần mềm không thể tự wrap khóa mà nó không được đọc byte; ngược lại, HSM có thể có cơ chế wrap bên trong phần cứng theo policy.

JavaDoc của **Cipher.wrap(Key)** mô tả InvalidKeyException khi việc wrap không thể hoặc không an toàn, ví dụ một khóa bảo vệ bởi phần cứng được đưa cho cipher chỉ chạy bằng phần mềm.

**Không tìm cách phá rào extractability** bằng reflection, in-memory dump hay tải private key ra JVM để “chạy ví dụ”. Hãy dùng API wrap/unwrap của provider/HSM hoặc workflow KMS chính thức, với kiểm soát vai trò và key usage thích hợp.

### 5.3 Có nên wrap private key không?

Có các trường hợp **private key backup/escrow/di chuyển** được phép và cần bảo vệ rất nghiêm ngặt. Tuy nhiên private key thường được khuyến nghị **giữ non-exportable** trong HSM khi mô hình bảo mật yêu cầu.

Nếu một private key thực sự được cho phép export, format như PKCS#8, thuật toán bọc, quản lý KEK, quyền và audit phải được quy định rõ; không giả định gọi **wrap(privateKey)** sẽ thành công trên mọi provider. Đối với private key không exportable, giải pháp thích hợp thường là key handle và chức năng bảo vệ nội bộ của HSM.

## 6. Java 17+ hoàn chỉnh: AESWrap, kiểm tra dữ liệu bị sửa và RSA-OAEP

Chương trình dưới đây minh họa:

1. Sinh **KEK AES-128** và **DEK AES-128** trong cùng lần chạy bằng KeyGenerator.
2. Bọc DEK bằng **AESWrap**, mở bọc bằng cùng KEK.
3. Đổi một bit của wrapped bytes để kiểm tra **integrity** của AESWrap.
4. Sinh keypair RSA-2048, bọc chính DEK bằng public key với **RSA-OAEP SHA-256/MGF1-SHA-256/label "key:v1"**.
5. Mở bọc bằng private key và cố tình dùng label khác để chứng minh cấu hình phải thống nhất.

Đây là chương trình **độc lập**, dùng API hiện diện từ Java 17 và SunJCE/SunRsaSign trên JDK phù hợp. Demo tạo khóa ngẫu nhiên trong một tiến trình; ứng dụng thực tế dùng KEK/DEK được cấp phát và quản lý bởi hệ thống khóa phù hợp.

Tạo **KeyWrapDemo.java** (UTF-8):

~~~java
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class KeyWrapDemo {
    private static OAEPParameterSpec oaep(String label) {
        return new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
            new PSource.PSpecified(
                label.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey kek = generator.generateKey();
        SecretKey dek = generator.generateKey();

        Cipher aesWrap = Cipher.getInstance("AESWrap");
        aesWrap.init(Cipher.WRAP_MODE, kek);
        byte[] wrappedAes = aesWrap.wrap(dek);

        Cipher aesUnwrap = Cipher.getInstance("AESWrap");
        aesUnwrap.init(Cipher.UNWRAP_MODE, kek);
        Key restoredAes = aesUnwrap.unwrap(
            wrappedAes, "AES", Cipher.SECRET_KEY);

        System.out.println("AESWrap bytes: " + wrappedAes.length);
        System.out.println("AES key restored: "
            + Arrays.equals(dek.getEncoded(), restoredAes.getEncoded()));

        byte[] tampered = wrappedAes.clone();
        tampered[tampered.length - 1] ^= 1;
        boolean tamperRejected;
        try {
            aesUnwrap.unwrap(tampered, "AES", Cipher.SECRET_KEY);
            tamperRejected = false;
        } catch (GeneralSecurityException expected) {
            tamperRejected = true;
        }
        System.out.println("AESWrap tamper rejected: " + tamperRejected);

        KeyPairGenerator rsaGenerator = KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);
        KeyPair recipient = rsaGenerator.generateKeyPair();

        Cipher rsaWrap = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsaWrap.init(Cipher.WRAP_MODE, recipient.getPublic(), oaep("key:v1"));
        byte[] wrappedRsa = rsaWrap.wrap(dek);

        Cipher rsaUnwrap = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsaUnwrap.init(Cipher.UNWRAP_MODE, recipient.getPrivate(),
            oaep("key:v1"));
        Key restoredRsa = rsaUnwrap.unwrap(
            wrappedRsa, "AES", Cipher.SECRET_KEY);

        System.out.println("RSA-OAEP wrapped bytes: " + wrappedRsa.length);
        System.out.println("RSA-OAEP restored: "
            + Arrays.equals(dek.getEncoded(), restoredRsa.getEncoded()));

        Cipher wrongLabel = Cipher.getInstance("RSA/ECB/OAEPPadding");
        wrongLabel.init(Cipher.UNWRAP_MODE, recipient.getPrivate(),
            oaep("key:v2"));
        boolean wrongLabelRejected;
        try {
            wrongLabel.unwrap(wrappedRsa, "AES", Cipher.SECRET_KEY);
            wrongLabelRejected = false;
        } catch (GeneralSecurityException expected) {
            wrongLabelRejected = true;
        }
        System.out.println("RSA-OAEP wrong label rejected: "
            + wrongLabelRejected);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 KeyWrapDemo.java
java KeyWrapDemo
~~~

Stdout mong đợi trong JDK có hỗ trợ các transformation nêu trên:

~~~text
AESWrap bytes: 24
AES key restored: true
AESWrap tamper rejected: true
RSA-OAEP wrapped bytes: 256
RSA-OAEP restored: true
RSA-OAEP wrong label rejected: true
~~~

**Cơ sở của các con số:**

- AES DEK 128 bit = **16 byte**. Với AES-KW RFC 3394, wrapped key dài **24 byte**, tức 16 + 8.
- RSA modulus 2048 bit = **256 byte**. Wrapped RSA-OAEP dài **256 byte**.
- Hai phép so sánh restored dùng **getEncoded() chỉ trong demo** cho khóa AES phần mềm do KeyGenerator sinh. Trong production, có thể phải thao tác trực tiếp với Key object mà không export raw bytes.
- Đổi byte wrapped AES-KW làm integrity check thất bại với xác suất áp đảo; demo nhận ra thông qua exception.
- OAEP SHA-256/MGF1-SHA-256 đúng nhưng **label "key:v2"** thay vì **"key:v1"** khiến unwrap không thành công.

Vì khóa AES và keypair RSA được tạo ngẫu nhiên mỗi lần chạy, raw wrapped bytes sẽ khác nhau. Ví dụ chỉ in **độ dài và điều kiện đúng/sai**, không in raw key, private key, ciphertext hay tag.

**Giới hạn của ví dụ:** Đây là kiểm thử chức năng local, không có KMS, không có giao thức truyền hay quyền truy cập thực; việc bắt GeneralSecurityException nhằm minh họa đường từ chối khi unwrap lỗi, không nên dùng exception message để trả thông tin mật mã ra client.

### 6.1 Vì sao bắt GeneralSecurityException trong demo?

**Cipher.wrap(...)** có thể ném InvalidKeyException hoặc IllegalBlockSizeException; **Cipher.unwrap(...)** thường báo InvalidKeyException hoặc NoSuchAlgorithmException theo JavaDoc. Việc unwrap sai không nhất thiết có cùng loại exception trên mọi provider và thuật toán.

Dùng **GeneralSecurityException** ở ranh giới minh họa cho phép bắt các lỗi mật mã đã được khai báo, rồi xem việc unwrap không thành công là một trạng thái từ chối. Trên dịch vụ thật, có thể phân loại nội bộ theo nguyên nhân hợp lệ (khóa không có quyền, định dạng sai, thuật toán không hỗ trợ, integrity check thất bại), nhưng **trả lỗi tổng quát** cho đối tượng không tin cậy và tránh tạo một oracle tiết lộ quá nhiều thông tin.

Lưu ý **UnsupportedOperationException** là runtime exception nếu provider không triển khai chức năng wrap/unwrap. Đây là vấn đề hỗ trợ API/cấu hình triển khai, không phải tín hiệu cho phép fallback sang mã hóa không an toàn.

### 6.2 Việc cùng key bytes được khôi phục chứng minh gì?

Trong demo, **AES key restored: true** cho thấy byte[] encoded key khớp với DEK ban đầu. Đây là một **round-trip test**, chưa chứng minh:

- KEK đang được bảo vệ trong môi trường thực;
- wrapped blob thuộc đúng tenant/record;
- người gửi có quyền tạo wrapped key;
- wrapped key chưa bị replay hoặc tráo giữa tài nguyên;
- dữ liệu mã hóa dưới DEK đã vượt kiểm tra tag AEAD.

Để chứng minh hệ thống đúng, cần cả **key recovery test** lẫn **policy, metadata binding, key lifecycle và data encryption test**.

## 7. Bọc khóa, metadata và thiết kế envelope encryption

### 7.1 Định dạng record phải rõ ràng

Một record mã hóa ứng dụng thường cần nhiều hơn wrapped bytes. Ví dụ một định dạng do ứng dụng tự thiết kế:

~~~text
envelopeVersion:   1
tenantId:          tenant-A
recordId:          report-2026-07
wrappingAlg:       AESWrap
kekId:             kms-key/finance-v3
wrappedDek:        Base64( AES-KW(KEK, DEK) )
dataAlg:           AES/GCM/NoPadding
nonce:             Base64(12-byte nonce)
dataCiphertext:    Base64(ciphertext || GCM tag)
keyPurpose:        record-encryption
~~~

Đây là **ví dụ cấu trúc**, không phải giao thức tiêu chuẩn đã được xác thực. Mỗi trường có vai trò:

- **envelopeVersion:** chọn schema và các thuật toán được chính sách cho phép.
- **tenantId/recordId/keyPurpose:** ràng buộc quyền, chủ sở hữu dữ liệu, loại khóa.
- **wrappingAlg:** xác định cách unwrap wrappedDEK.
- **kekId:** tham chiếu KEK trong KMS/HSM; không chứa raw KEK.
- **wrappedDek:** binary wrapped key được biểu diễn Base64 cho JSON/text, không có quyền năng bảo mật nhờ Base64.
- **dataAlg/nonce/dataCiphertext:** mô tả dữ liệu được mã hóa bởi DEK.

**AES-KW không có AAD**, nên những metadata như tenantId, recordId, keyPurpose **không tự được kiểm tra bằng AESWrap**. Cần ràng buộc metadata vào giao thức có xác thực (ví dụ AES-GCM AAD cho dữ liệu và metadata tương ứng, hoặc record được ký/MAC theo mô hình tin cậy), kiểm tra authorization và gắn wrappedDEK với đúng ngữ cảnh. Không nhận metadata do client tùy ý thay đổi rồi chỉ kiểm tra “unwrap thành công”.

### 7.2 Luồng đọc dữ liệu được bảo vệ

Quy trình hợp lý:

1. **Xác thực request** và kiểm tra người gọi có quyền đọc record cụ thể.
2. Parse và kiểm tra **envelopeVersion, thuật toán cho phép, kích thước, key ID**, các trường metadata và mối quan hệ tenant/record.
3. Tra cứu KEK theo key ID trong **KMS/HSM** theo quyền, không cấp KEK cho mọi client.
4. **Unwrap DEK** bằng thuật toán và tham số chính xác. Nếu integrity check thất bại, từ chối.
5. Kiểm tra loại Key, algorithm, độ dài DEK và **key usage**; không tin nhãn algorithm từ payload một cách mù quáng.
6. Dùng DEK giải mã dữ liệu theo **AES-GCM**, cung cấp đúng nonce/AAD, xác thực tag; không sử dụng plaintext nếu xác thực thất bại.
7. Chỉ sau các bước kiểm tra mới đưa plaintext cho phần nghiệp vụ được cấp quyền.
8. Ghi audit tối thiểu (key ID, record ID, outcome, request ID), không log raw KEK/DEK hoặc plaintext.

Unwrap thành công **không bảo đảm payload dataCiphertext hợp lệ**: còn cần giải mã và xác thực dữ liệu. Ngược lại, payload GCM hợp lệ trong một ngữ cảnh cũng **không cấp thêm quyền truy cập** cho người gọi khác.

### 7.3 Khi dùng RSA-OAEP thay AESWrap trong envelope

Với RSA-OAEP:

~~~text
wrappingAlg:       RSA-OAEP
rsaRecipientKeyId: recipient-key-2026
oaepDigest:        SHA-256
mgf:               MGF1
mgfDigest:         SHA-256
oaepLabel:         Base64(UTF8("key:v1"))
wrappedDek:        Base64(RSAES-OAEP(publicKey, DEK))
~~~

Người nhận chọn **private key có quyền** và cùng bộ tham số. Không để các field chưa được xác thực buộc server tự hạ cấp thuật toán hoặc thử key không thuộc tenant. Nếu các field có thể bị sửa, ứng dụng phải kiểm tra/chặn ở tầng policy hoặc ràng buộc chúng vào cơ chế xác thực của record/giao thức.

**RSA-OAEP không chứng minh nguồn gốc của key**: người giữ public key có thể tự tạo wrapped key. Muốn biết ai gửi, cần chữ ký số hoặc xác thực kết nối và chính sách phân quyền phù hợp.

## 8. Vòng đời KEK và DEK: rotation, rewrap và quyền truy cập

### 8.1 Đổi KEK không nhất thiết phải mã hóa lại file lớn

Nếu DEK vẫn còn hợp lệ và không bị lộ, có thể luân chuyển **KEK** theo cách:

~~~text
wrappedDEK_old --[UNWRAP với KEK_old]--> DEK
DEK            --[WRAP với KEK_new]----> wrappedDEK_new
~~~

Các thao tác này phải được bảo vệ bởi KMS/HSM hoặc môi trường tin cậy, hạn chế thời gian DEK xuất hiện trong bộ nhớ. Cập nhật **wrappedDEK + kekId/version** nhất quán và đảm bảo kế hoạch phục hồi nếu quá trình rotation bị ngắt.

**Rewrap không tạo DEK mới** và không mã hóa lại dataCiphertext. Nó chỉ thay cách bảo vệ cùng DEK. Nếu chính DEK bị lộ, chỉ đổi KEK không làm ciphertext đã mã hóa dưới DEK trở nên bí mật trở lại: cần đánh giá cấp DEK mới, mã hóa lại dữ liệu, thu hồi quyền và phạm vi ảnh hưởng.

### 8.2 Quyền “wrap” và “unwrap” nên tách

Một hệ thống có thể cấp cho dịch vụ ghi quyền tạo wrapped DEK, còn dịch vụ đọc có quyền mở bọc để giải mã. Không phải tiến trình nào cần lưu ciphertext cũng phải được cấp quyền unwrap mọi record.

KMS/HSM thường cung cấp:

- Key ID/alias và version.
- Tách quyền encrypt/wrap/decrypt/unwrap theo vai trò và chính sách.
- Audit các thao tác sử dụng key.
- Rotation, disable, revoke và backup/restore phù hợp.
- Khả năng giữ root/KEK không rời phần cứng.

**Đừng nhầm “ứng dụng được quyền wrap” với “được quyền unwrap”.** Việc cấp quyền dư thừa làm tăng tác động khi một service bị xâm nhập.

### 8.3 Chính sách hết hạn và phục hồi

Không xóa KEK cũ trước khi các wrapped DEK phụ thuộc vào nó đã được rewrap hoặc không còn cần giải mã, trừ khi có lý do thu hồi bảo mật mạnh hơn. Cần lưu key version, trạng thái rotation, dữ liệu đã migrate và chính sách phục hồi.

Đồng thời, đừng giữ KEK cũ hoạt động vô hạn để tương thích nếu khóa có thể đã lộ. Kế hoạch chuyển đổi phải cân bằng khả năng phục hồi với yêu cầu thu hồi và bảo mật.

## 9. Đúng thuật toán nhưng vẫn có thể sai an toàn

### 9.1 Sai lầm: lưu KEK cạnh wrapped DEK

Nếu DB có wrappedDEK và cùng hệ thống cấp cho tiến trình bất kỳ quyền lấy KEK plaintext, giá trị bảo vệ giảm mạnh. **Tách miền quyền** giữa storage và key service là phần quan trọng của envelope encryption.

Có thể đặt KMS key ID cùng record; **key ID thường không phải secret**. Nhưng raw KEK không nên được lưu/ghi log trong cùng mức quyền truy cập với dữ liệu nó bảo vệ.

### 9.2 Sai lầm: dùng cùng KEK cho mọi tenant và mục đích

Một KEK chung làm phạm vi ảnh hưởng khi compromise rộng hơn. Thiết kế theo **tenant/ứng dụng/mục đích** khi phù hợp, kết hợp policy ngăn một client unwrap DEK của client khác. Việc tách KEK còn giúp rotation độc lập và audit rõ ràng.

**Thay khóa cho từng tenant không tự thay thế authorization**; vẫn phải kiểm tra user có quyền trên record hay không.

### 9.3 Sai lầm: không kiểm tra “khóa trả về có dùng đúng mục đích?”

**Cipher.unwrap(..., "AES", SECRET_KEY)** giúp provider dựng Key có algorithm "AES" nhưng không phải một cơ chế chứng thực mục đích sử dụng từ phía người tạo record. Nếu dữ liệu có thể đưa vào dưới ngữ cảnh khác, cần key ID/version, key type, độ dài, tenant, purpose và chính sách rõ ràng.

Ví dụ DEK AES-128 được phép mã hóa hồ sơ nội bộ không có nghĩa service có quyền dùng nó để mã hóa payload thuộc tenant khác.

### 9.4 Sai lầm: xem integrity của wrapped key như integrity của mọi thứ

AES-KW kiểm tra integrity **wrapped key bytes** theo lược đồ. Nó không biết username, nội dung file, quyền truy cập, bản ghi gốc hay timestamp nếu các thông tin đó không được bảo vệ bằng cơ chế khác.

Hãy phân biệt ba lớp:

1. **Key wrapping integrity:** wrappedDEK có thể được mở bọc hợp lệ theo KEK.
2. **Payload integrity:** ciphertext dữ liệu được xác thực bằng AES-GCM tag/MAC phù hợp.
3. **Authorization/business integrity:** đúng tenant, đúng người, đúng thao tác và không bị replay/tráo record.

Cần cả ba khi hệ thống có yêu cầu tương ứng.

### 9.5 Sai lầm: tự fallback sang thuật toán yếu hơn

Nếu unwrap bằng AESWrap thất bại, không nên thử các transformation tùy ý như AES/ECB/NoPadding hay RSA/ECB/PKCS1Padding với hy vọng ra một Key “đọc được”. Với giao thức có version, server chỉ chấp nhận **thuật toán và tham số được phép cho version đó**, từ chối trường hợp không phù hợp.

### 9.6 Sai lầm: wrap là thay thế cho xác thực người gửi

Một public RSA key được công khai. RSA-OAEP cho phép gửi wrapped key bí mật tới private-key holder, nhưng **bất kỳ ai có public key** cũng có thể tạo wrappedDEK. Nếu bên nhận cần tin “đúng Alice tạo key này”, phải có chữ ký số, MAC với khóa chung phù hợp, mTLS hoặc xác thực giao thức độc lập.

## 10. Những lỗi và exception cần xử lý

| Lỗi/tình huống | Biểu hiện thường gặp | Cách xử lý |
| --- | --- | --- |
| Cipher chưa init WRAP_MODE, gọi wrap | IllegalStateException | Sửa trạng thái Cipher, không tiếp tục |
| Provider không hỗ trợ wrap | UnsupportedOperationException hoặc không lấy được transformation | Kiểm tra khả năng/provider, không fallback yếu |
| Key không thể export hoặc không tương thích | InvalidKeyException khi wrap | Dùng provider/HSM phù hợp, không phá extractability |
| Dữ liệu key bytes không thỏa giới hạn AES-KW | Lỗi wrap hoặc khi init theo provider | Chọn KWP/hệ thống phù hợp khi đã được hỗ trợ |
| Wrapped bytes sai/bị sửa, KEK sai | Unwrap thất bại (thường InvalidKeyException) | Từ chối, không sử dụng key nào |
| Sai RSA private key/OAEP digest/MGF1 digest/label | Unwrap không hợp lệ | Từ chối, kiểm tra cấu hình và key ID đã thỏa thuận |
| wrappedKeyAlgorithm không được hỗ trợ | NoSuchAlgorithmException hoặc lỗi provider | Kiểm tra whitelist algorithm, không tin text tùy ý |
| Sai wrappedKeyType | Lỗi dựng Key hoặc dựng kiểu khác không dùng được | Đối chiếu schema, không thử mọi loại tự động |
| Key ID/version không tồn tại | Lỗi policy/KMS | Từ chối có kiểm soát; audit metadata tối thiểu |
| Mở bọc được nhưng GCM tag của dữ liệu sai | AEADBadTagException lúc giải mã payload | Không công bố plaintext; điều tra theo chính sách |

**Không lấy thông điệp exception của provider làm API công khai.** Những lỗi phân biệt sâu về padding, key length, key validity có thể tạo oracle hữu ích cho đối thủ. Tạo mã lỗi tổng quát phía client và log nội bộ đã kiểm soát; tránh log raw key hoặc payload bí mật.

Trên hệ thống đa luồng, **Cipher có trạng thái và không nên dùng chung đồng thời giữa các request**. Khởi tạo riêng theo operation hoặc có cơ chế quản lý instance không dùng chung trạng thái. Không giữ Cipher đã init WRAP_MODE rồi gọi unwrap; phải init UNWRAP_MODE với đúng key và parameters.

## 11. Checklist triển khai an toàn

Trước khi đưa chức năng key wrapping vào sản xuất, kiểm tra:

- [ ] Xác định rõ **KEK/DEK**, thuật toán và mục đích của từng khóa.
- [ ] Sinh khóa bằng **SecureRandom/KeyGenerator** hoặc KMS/HSM an toàn, không dùng password plaintext làm KEK trực tiếp.
- [ ] Chọn **AESWrap**, **AESWrapPad**, **RSA-OAEP** hoặc dịch vụ KMS/HSM theo đặc tả; không tự chế key wrapping.
- [ ] Kiểm tra **provider thực tế** có hỗ trợ transformation/mode/loại Key định dùng.
- [ ] Với AES-KW, kiểm tra encoded key bytes có kích thước hợp lệ; với KWP, kiểm tra đúng chuẩn/provider.
- [ ] Với RSA-OAEP, cấu hình **OAEP hash, MGF1 digest, label** tường minh và thống nhất hai bên.
- [ ] Với HSM, tuân thủ tính **non-exportable**, quyền wrap/unwrap và key handle.
- [ ] Ghi rõ **algorithm/key type/key ID/version/encoding** trong giao thức và kiểm tra whitelist.
- [ ] Không log raw KEK, DEK, private key, plaintext hoặc các buffer bí mật.
- [ ] Không xem Base64/hex là lớp bảo vệ mật mã.
- [ ] Metadata và quyền truy cập phải được **kiểm tra/ràng buộc với ngữ cảnh**; chống tráo wrapped key giữa tenant/record.
- [ ] Chỉ dùng DEK để giải mã dữ liệu sau khi unwrap hợp lệ, xác thực payload AEAD thành công và authorization được xác nhận.
- [ ] Có kế hoạch **KEK rotation / DEK rotation / backup / recovery / revocation**.
- [ ] Test corrupted wrapped bytes, sai KEK, sai key type, sai thuật toán/label, provider không hỗ trợ và lỗi KMS.
- [ ] Giám sát lỗi, giới hạn request, tách quyền và không xây logic fallback hạ cấp thuật toán.

### 11.1 Test matrix tối thiểu

| Test | Kết quả mong đợi |
| --- | --- |
| AESWrap: KEK đúng, DEK AES-128 16 byte | wrap → unwrap khôi phục đúng Key |
| AESWrap: lật một bit trong wrapped bytes | Integrity check thất bại |
| AESWrap: đổi sang KEK khác | Không mở bọc thành công |
| AESWrap: đầu vào key encoding có độ dài không hợp lệ | Reject hoặc dùng KWP đúng chuẩn nếu chính sách cho phép |
| AESWrapPad: provider không hỗ trợ | Phát hiện ở khâu capability, không tự fallback |
| RSA-OAEP: đúng private key/hash/MGF1/label | Mở bọc DEK thành công |
| RSA-OAEP: sai private key hoặc label | Mở bọc thất bại |
| Gửi raw wrapped bytes qua Base64 rồi decode | Giữ nguyên byte, không tăng bảo mật |
| Nhầm wrappedKeyAlgorithm với wrapping algorithm | Phát hiện mismatch qua schema/key validation |
| Non-exportable key đưa cho software-only provider | Reject; dùng hardware wrapping nếu được phép |
| Tráo wrappedDEK từ tenant khác | Reject qua authorization/context binding |
| Unwrap thành công nhưng payload GCM tag sai | Từ chối plaintext |
| KEK rotation | DEK được rewrap, dữ liệu cũ giải mã được theo chính sách mới |

**Đừng chỉ đo “unwrap trả Key”.** Một bộ test an toàn phải kiểm chứng cả loại Key, độ dài, quyền, metadata và xử lý lỗi.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** WRAP_MODE khác ENCRYPT_MODE như thế nào?

**Trả lời:** WRAP_MODE dành cho **Cipher.wrap(Key)** để bảo vệ đối tượng Key và trả byte[]; ENCRYPT_MODE dành cho mã hóa byte dữ liệu qua doFinal/update. Chọn WRAP_MODE không tự bảo đảm thuật toán an toàn.

**Câu 2.** UNWRAP_MODE cần những thông tin nào?

**Trả lời:** Cipher phải được init với key/params đúng; unwrap nhận wrapped bytes, **tên thuật toán của khóa được khôi phục** và **loại Key (SECRET_KEY/PUBLIC_KEY/PRIVATE_KEY)**.

**Câu 3.** Wrapped DEK có phải dữ liệu gốc đã mã hóa không?

**Trả lời:** Không. Nó là **bản bảo vệ khóa DEK**, khác với ciphertext của bản ghi/hồ sơ được mã hóa bằng DEK.

**Câu 4.** Vì sao bọc AES-128 bằng AESWrap thường tạo 24 byte?

**Trả lời:** AES-128 key có 16 byte; AES-KW RFC 3394 thêm overhead 8 byte khi bọc.

**Câu 5.** Khi nào dùng AESWrapPad?

**Trả lời:** Khi cần bọc key bytes không đáp ứng điều kiện độ dài AES-KW, **với điều kiện provider hỗ trợ AES-KWP và giao thức thống nhất đúng thuật toán**.

**Câu 6.** Key.getEncoded() trả null có phải lỗi phải sửa bằng cách xuất secret cưỡng bức không?

**Trả lời:** Không. Nó có thể là thuộc tính bảo vệ của HSM/provider. Cần sử dụng API wrap/unwrap ở phần cứng hoặc KMS được phép.

**Câu 7.** RSA-OAEP dùng khóa gì để wrap và unwrap?

**Trả lời:** Wrap bằng **public key** người nhận; unwrap bằng **private key** tương ứng, cùng OAEP digest/MGF1 digest/label.

**Câu 8.** Bản mã RSA-OAEP bọc DEK AES có chứng minh Alice đã gửi DEK không?

**Trả lời:** Không. Public key có thể được biết bởi nhiều người; cần xác thực nguồn gốc riêng.

**Câu 9.** Nếu mở bọc DEK thành công, có thể bỏ qua tag AES-GCM của dữ liệu không?

**Trả lời:** Không. Unwrap bảo vệ DEK, còn integrity của payload phải được xác thực ở tầng mã hóa dữ liệu.

**Câu 10.** Vì sao AESWrap không tự bảo vệ tenantId trong JSON envelope?

**Trả lời:** AES-KW không có AAD tùy ý. Cần xác thực metadata ở lớp giao thức/record và kiểm tra quyền.

**Câu 11.** Rewrap DEK khi đổi KEK có cần mã hóa lại cả file dữ liệu không?

**Trả lời:** Không nhất thiết, nếu DEK vẫn an toàn và thiết kế cho phép; mở bọc bằng KEK cũ rồi bọc lại bằng KEK mới. Nếu **DEK bị lộ**, có thể phải cấp DEK mới và mã hóa lại dữ liệu.

**Câu 12.** Nếu AESWrap unwrap báo lỗi, có nên thử AES/ECB/NoPadding để được Key không?

**Trả lời:** Không. Phải từ chối và chẩn đoán theo version/thuật toán được phép, không downgrade.

## 13. Tài liệu chuẩn để tra cứu

- [Oracle Java SE 17 — javax.crypto.Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): WRAP_MODE, UNWRAP_MODE, wrap(Key), unwrap(...), exception và vòng đời Cipher.
- [Oracle Java SE 17 — Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): AESWrap, AESWrapPad, RSA và tên transformation.
- [Oracle Java SE 17 — JDK Providers Documentation](https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html): những transformation được provider cụ thể hỗ trợ.
- [RFC 3394 — Advanced Encryption Standard (AES) Key Wrap Algorithm](https://www.rfc-editor.org/rfc/rfc3394): thuật toán AES-KW, cấu trúc, độ dài và kiểm tra integrity.
- [RFC 5649 — Advanced Encryption Standard (AES) Key Wrap with Padding](https://www.rfc-editor.org/rfc/rfc5649): AES-KWP, mở rộng điều kiện độ dài.
- [NIST SP 800-38F — Methods for Key Wrapping](https://csrc.nist.gov/pubs/sp/800/38/f/final): khuyến nghị phương thức bảo vệ khóa.
- [RFC 8017 — RSAES-OAEP](https://www.rfc-editor.org/rfc/rfc8017.html#section-7.1): giới hạn và cấu trúc RSA-OAEP.

## Kết luận

**Cipher.WRAP_MODE và Cipher.UNWRAP_MODE là hai chế độ API xử lý đối tượng Key, không phải thuật toán bảo mật tự thân.** Để lưu trữ hoặc chuyển DEK, hãy chọn cơ chế bọc được thiết kế cho khóa (AES-KW/KWP, RSA-OAEP hoặc KMS/HSM), quản lý KEK và tham số chặt chẽ, kiểm tra loại/độ dài khóa, bảo vệ metadata và phân quyền. **wrap(Key)** tạo wrapped bytes; **unwrap(..., wrappedKeyAlgorithm, wrappedKeyType)** tái tạo Key khi điều kiện mật mã và provider cho phép. Chỉ coi dữ liệu ứng dụng là hợp lệ khi cả **key protection**, **payload authentication** và **authorization** đã đạt yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-establishment-and-wrapping-007">10.7 API KEM từ Java 21 với Encapsulator, Decapsulator và thông điệp đóng gói</a>

<details>
<summary>Xem chi tiết</summary>

# 10.7 API KEM từ Java 21 với Encapsulator, Decapsulator và thông điệp đóng gói

Từ **Java 21**, JDK có API chuẩn **`javax.crypto.KEM`** (JEP 452) để thực hiện **Key Encapsulation Mechanism — KEM**. Đây là cầu nối giữa mật mã khóa công khai và mã hóa đối xứng: bên gửi dùng **public key của người nhận** để tạo một **shared secret** và một **thông điệp đóng gói** (encapsulation); bên nhận dùng **private key tương ứng** và thông điệp đóng gói để khôi phục **cùng shared secret**. Hai bên dùng bí mật này làm đầu vào cho cơ chế dẫn xuất khóa hoặc mã hóa dữ liệu.

Mục tiêu của bài là hiểu chính xác **`KEM`, `KEM.Encapsulator`, `KEM.Encapsulated`, `KEM.Decapsulator`**, ý nghĩa của `key()`, `encapsulation()`, `params()`, luồng byte truyền trên mạng, cách xác minh tính đúng và cách ghép KEM với **HKDF + AES-GCM** thành một envelope minh họa. Đồng thời, phải phân biệt **KEM không tự mã hóa file, không tự xác thực người gửi và không tự tạo một giao thức bảo mật hoàn chỉnh**.

Ví dụ chính dùng **DHKEM + X25519**, tương thích với API có từ Java 21 khi provider JDK hỗ trợ cấu hình này. Các API và cấu hình mật mã khả dụng còn phụ thuộc phiên bản JDK/provider; không mặc định rằng mọi thuật toán KEM, đặc biệt các họ hậu lượng tử, đều đã tích hợp trong **JDK 21**.

## 1. KEM giải quyết bài toán gì?

Giả sử Bob muốn nhận dữ liệu bí mật từ Alice. Bob tạo hoặc sở hữu cặp khóa:

- **`pkR`**: public key có thể phân phối cho người gửi sau khi xác minh nguồn gốc;
- **`skR`**: private key Bob giữ bí mật.

Alice chưa có một khóa AES chia sẻ trước với Bob. Nếu phải truyền một khóa AES dạng plaintext, kẻ nghe lén sẽ đọc được khóa. KEM cho phép Alice tạo bí mật chung mà không cần gửi bí mật đó qua mạng.

```text
                   BOB (người nhận)
               KeyPair(pkR, skR)
                     |
           pkR đã được xác thực
                     |
                   ALICE (người gửi)
          Encapsulate(pkR) tạo:
             sharedSecret_A
             encapsulationBytes
                     |
      gửi encapsulationBytes (không gửi sharedSecret_A)
                     |
                   BOB
    Decapsulate(skR, encapsulationBytes)
                     |
             sharedSecret_B

    sharedSecret_A == sharedSecret_B
```

KEM gồm ba ý niệm nền tảng:

1. **Key generation (`KeyGen`):** tạo cặp khóa công khai/riêng phù hợp thuật toán KEM. Trong ví dụ `DHKEM` với X25519, dùng `KeyPairGenerator.getInstance("X25519")`.
2. **Encapsulation (`Encap`):** nhận public key, trả về **bí mật chia sẻ và thông điệp đóng gói**. Bí mật phải giữ riêng; thông điệp đóng gói gửi được.
3. **Decapsulation (`Decap`):** nhận private key và thông điệp đóng gói, trả về bí mật tương ứng hoặc lỗi theo thuật toán/provider.

**Điểm mấu chốt:** `encapsulationBytes` là dữ liệu để người nhận **khôi phục cùng bí mật**, chứ **không phải** file đã mã hóa và cũng không phải shared secret dưới dạng Base64. Một người nhìn thấy encapsulation nhưng không có private key đúng không nên có khả năng suy ra shared secret trong mô hình bảo mật của KEM.

### 1.1. Vì sao gọi là “key encapsulation”, không gọi đơn thuần là “RSA encrypt”?

Với RSA-OAEP kiểu key transport truyền thống, bên gửi thường **tạo sẵn một khóa ngẫu nhiên nhỏ** rồi mã hóa khóa đó bằng RSA. Với nhiều KEM (ví dụ DHKEM), thao tác encapsulation **đồng thời tạo** shared secret và encapsulation, chứ không phải một hàm tổng quát nhận byte khóa tùy ý rồi mã hóa byte ấy. API Java thể hiện đúng khác biệt này: `Encapsulator.encapsulate()` **không nhận message plaintext**.

Các loại KEM khác nhau có thể dựa vào DH/ECDH, cấu trúc lattice hoặc primitive khác; Java `KEM` là **giao diện trừu tượng** để làm việc qua provider. Chọn thuật toán và public/private key phải khớp nhau; tên `KEM` không phải bảo đảm mọi khóa bất đối xứng đều tương thích với mọi KEM.

### 1.2. KEM và key agreement/AEAD không giống nhau

| Thành phần | Đầu vào/đầu ra điển hình | Mục đích |
| --- | --- | --- |
| `KeyPairGenerator` | Algorithm → public/private key pair | Tạo khóa người nhận |
| `KEM` | Algorithm name + provider | Chọn primitive KEM |
| `Encapsulator.encapsulate()` | Public key đã cấu hình → `SecretKey` + encapsulation bytes | Tạo bí mật chung từ phía người gửi |
| `Decapsulator.decapsulate(bytes)` | Private key đã cấu hình + encapsulation → `SecretKey` | Khôi phục bí mật chung bên nhận |
| KDF/HKDF | Shared secret + context → key(s) phục vụ ứng dụng | Tách miền sử dụng, tạo khóa/nonce phù hợp |
| AEAD (AES-GCM, v.v.) | Key + nonce + plaintext + AAD → ciphertext + tag | Mã hóa dữ liệu và xác thực ciphertext/metadata |
| Chữ ký số hoặc giao thức authenticated | Khóa định danh và thông điệp | Xác thực nguồn gửi nếu nghiệp vụ yêu cầu |

Không dùng `KEM.encapsulate()` thay `Cipher.doFinal(file)`. KEM **chỉ xử lý phần thiết lập bí mật chung**; file MB/GB vẫn nên đi qua cơ chế mã hóa đối xứng hoặc authenticated streaming encryption đã được thiết kế phù hợp.

## 2. API Java 21: bốn kiểu dữ liệu quan trọng

JEP 452 bổ sung các lớp trong module `java.base`, package **`javax.crypto`**:

- **`KEM`:** factory/controller cho thuật toán KEM, ví dụ `KEM.getInstance("DHKEM")`; có các phương thức tạo encapsulator hoặc decapsulator.
- **`KEM.Encapsulator`:** đối tượng phía gửi được tạo từ **public key người nhận**; gọi `encapsulate()` để sinh một kết quả mới.
- **`KEM.Encapsulated`:** object chứa **`SecretKey key()`**, **`byte[] encapsulation()`**, **`byte[] params()`** (tham số tùy chọn, có thể `null`).
- **`KEM.Decapsulator`:** đối tượng phía nhận được tạo từ **private key tương ứng**; gọi `decapsulate(byte[])` để lấy shared secret.

### 2.1. Các lời gọi tối thiểu

```java
// Ví dụ trích đoạn Java 21+, chưa bao gồm key provisioning/try-catch.
KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
KeyPair receiverKeys = generator.generateKeyPair();

KEM kem = KEM.getInstance("DHKEM");

// Sender: chỉ cần public key đã xác thực của receiver.
KEM.Encapsulator sender = kem.newEncapsulator(receiverKeys.getPublic());
KEM.Encapsulated result = sender.encapsulate();
SecretKey senderSecret = result.key();
byte[] messageToSend = result.encapsulation();

// Receiver: phải có private key tương ứng.
KEM.Decapsulator receiver = kem.newDecapsulator(receiverKeys.getPrivate());
SecretKey receiverSecret = receiver.decapsulate(messageToSend);
```

Đây là trích đoạn minh họa quan hệ API, không phải một file Java đầy đủ (cần `import` và xử lý lỗi). Có hai chương trình hoàn chỉnh ở các mục sau.

### 2.2. Phân biệt `KEM`, `Encapsulator`, `Encapsulated` và `encapsulation()`

Tên gần nhau nhưng vai trò rất khác:

| Tên Java | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `KEM` | Đối tượng API | Biết loại thuật toán KEM, tạo worker có khóa phù hợp |
| `KEM.Encapsulator` | Đối tượng phía gửi | Gắn với public key người nhận và provider |
| `KEM.Encapsulated` | Giá trị kết quả | Có shared secret, encapsulation byte array và optional params |
| `result.key()` | `SecretKey` | **Phải giữ bí mật**, không đưa vào network envelope |
| `result.encapsulation()` | `byte[]` | **Thông điệp có thể truyền** cho receiver; không phải secret |
| `result.params()` | `byte[]` hoặc `null` | Tham số tùy chọn để hai phía cấu hình tương thích |
| `KEM.Decapsulator` | Đối tượng phía nhận | Gắn với private key người nhận |
| `decapsulate(encapsulation)` | `SecretKey` | Kết quả secret phía receiver |

API cho phép gọi `Encapsulator.encapsulate()` nhiều lần; **mỗi lần** tạo một shared secret và thông điệp mới. Không nên tái sử dụng secret và nonce mã hóa dữ liệu như thể đây là “khóa dài hạn được định trước”. Đặc biệt, không lấy `encapsulation()` rồi truyền qua `SecretKeySpec` để sử dụng như khóa AES: đó là **dữ liệu công khai**, không phải secret.

### 2.3. Các overload quan trọng

```java
KEM.getInstance("DHKEM");
kem.newEncapsulator(recipientPublicKey);
kem.newEncapsulator(recipientPublicKey, secureRandom);
kem.newEncapsulator(recipientPublicKey, parameterSpec, secureRandom);

kem.newDecapsulator(recipientPrivateKey);
kem.newDecapsulator(recipientPrivateKey, parameterSpec);

encapsulator.encapsulate();
encapsulator.encapsulate(from, to, keyAlgorithm);

decapsulator.decapsulate(encapsulationBytes);
decapsulator.decapsulate(encapsulationBytes, from, to, keyAlgorithm);
```

Các dòng trên chỉ là chữ ký gọi minh họa; `secureRandom`/`parameterSpec`/key là biến cần tạo đúng kiểu, không phải một đoạn chương trình độc lập. Với overload `encapsulate(int from, int to, String algorithm)`, `from`/`to` là **chỉ số byte** của phần shared secret muốn nhận; `to` là **exclusive**, và `algorithm` là **tên thuật toán gán cho SecretKey trả về**. Overload tương ứng của decapsulator phải dùng phạm vi và thuật toán **phù hợp**. Provider có thể không hỗ trợ mọi tổ hợp.

**Cách nên nhớ:** `encapsulate()`/`decapsulate(bytes)` dạng không tham số phụ trả `SecretKey` với `getAlgorithm()` thường là **`"Generic"`**. Giá trị Generic thường thích hợp làm đầu vào cho **KDF**, rồi mới lấy khóa AES/HMAC theo ngữ cảnh ứng dụng. Đừng hiểu `"Generic"` nghĩa là thuật toán mã hóa dữ liệu tên Generic.

### 2.4. Kích thước dữ liệu: `secretSize()` và `encapsulationSize()`

- `Encapsulator.secretSize()` và `Decapsulator.secretSize()` trả **số byte** của shared secret chuẩn của provider/thuật toán.
- `Encapsulator.encapsulationSize()` và `Decapsulator.encapsulationSize()` trả **số byte** của thông điệp encapsulation.
- Đây là hai kích thước khác nhau về ý nghĩa, dù một số cấu hình có thể cho cùng một giá trị.
- Trong **DHKEM(X25519, HKDF-SHA256)**, encapsulation thường **32 byte** và shared secret chuẩn **32 byte**; giá trị này đến từ thuật toán/profile, không phải do file plaintext dài 32 byte.
- Dùng metadata size của decapsulator để kiểm tra input trước khi parse/chuyển vào provider; tránh cấp phát hoặc xử lý mảng không giới hạn từ mạng.

Nếu khóa nằm trong HSM/provider không cho xuất, `SecretKey.getEncoded()` **có thể trả `null`**. Các phương thức size giúp hỏi kích thước mà không phải trích raw key. Khi cần KDF từ non-exportable secret, hãy sử dụng chức năng derivation của provider/HSM phù hợp; không ép ứng dụng phải log/xuất secret.

### 2.5. Encapsulation message khác ciphertext của file

Trong tài liệu KEM, encapsulation message **cũng có thể được gọi là KEM ciphertext**. Dù vậy, nó không phải ciphertext dữ liệu mà AES-GCM trả ra:

```text
encapsulationBytes              : public KEM message, giúp receiver tạo secret
sharedSecret                    : chỉ tồn tại nội bộ phía gửi/nhận
payloadCiphertextAndTag         : dữ liệu ứng dụng đã mã hóa bằng AEAD
envelope                        : metadata + encapsulationBytes + AEAD payload
```

Vì hai loại byte có thể cùng được gọi là “ciphertext” ở các lớp giao thức khác nhau, nên trong code và schema nên đặt tên rõ ràng: **`kemEncapsulation`** và **`aeadCiphertext`**. Không gửi nhầm `key().getEncoded()` thay vì `encapsulation()`.

## 3. Luồng gửi–nhận và vị trí của ba trường `key`, `encapsulation`, `params`

Một KEM exchange chuẩn theo API Java:

```text
Sender                                                   Receiver
  |                                                          |
  | <-- authentic public key pkR / key ID / algorithm --------|
  |                                                          |
  | KEM.getInstance("DHKEM")                                |
  | enc = kem.newEncapsulator(pkR).encapsulate()             |
  |   senderSecret = enc.key()      [GIỮ BÍ MẬT]             |
  |   encBytes     = enc.encapsulation()                     |
  |   optParams    = enc.params()                            |
  |                                                          |
  | --- protocol header + encBytes (+ optParams nếu cần) --->|
  |                                                          |
  |                               kem.newDecapsulator(skR)   |
  |                               .decapsulate(encBytes)     |
  |                                  -> receiverSecret       |
  |                                                          |
  | HKDF(senderSecret, context)   HKDF(receiverSecret, context)
  |          |                                  |
  |       AEAD key ------------------------> cùng AEAD key    |
  |     mã hóa dữ liệu                   xác thực và giải mã  |
```

### 3.1. Bên gửi biết gì?

Người gửi phải có **public key đích đã được xác thực**, biết **KEM algorithm** được giao thức cho phép, biết suite/hash và tham số kèm theo nếu cần. Người gửi **không cần private key** của người nhận. Vì vậy, chỉ dùng KEM theo public key người nhận **không thể tự chứng minh danh tính người gửi**: bất kỳ ai có public key hợp lệ cũng có thể thực hiện encapsulation mới.

### 3.2. Bên nhận cần gì?

Người nhận cần **private key tương ứng** và đúng KEM/suite/parameter configuration. Đừng chọn private key dựa tùy tiện vào một `keyId` không đáng tin; server phải xác minh tenant, recipient, phiên bản khóa, trạng thái rotation và allowlist thuật toán. Khi private key bị mất, các encapsulation cũ phụ thuộc private key đó có thể không còn giải mã được theo kiến trúc lưu trữ.

### 3.3. `params()` là gì?

`KEM.Encapsulated.params()` là optional byte array chứa **tham số thuật toán** nếu provider/algorithm cần truyền thêm dữ liệu để receiver tạo cấu hình tương ứng. Nó **có thể `null`**. Với ví dụ DHKEM/X25519 mặc định của JDK, giá trị thường `null`.

Nếu thuật toán yêu cầu `AlgorithmParameterSpec`, Java có overload `newEncapsulator(publicKey, spec, random)` và `newDecapsulator(privateKey, spec)`. Khi có params, receiver phải **parse theo định dạng mà provider/giao thức định nghĩa**, kiểm tra giới hạn/độ hợp lệ và khôi phục `AlgorithmParameterSpec` tương ứng (khi provider hỗ trợ `AlgorithmParameters`). Không thể đưa `params()` tùy ý vào `decapsulate(byte[])` rồi mong nó được tự nhận; `decapsulate` chỉ nhận **encapsulation bytes**.

### 3.4. Không log `SecretKey` hoặc binary encapsulation như secret

- **Shared secret/KDF output/AEAD key:** phải giữ bí mật; không log base64, hex, byte array, debug dumper.
- **Encapsulation:** thường không cần bí mật, nhưng phải bảo vệ **integrity/context binding** ở lớp envelope và giới hạn kích thước đầu vào.
- **Metadata:** version, key ID, suite, optional params có thể không bí mật nhưng không được mặc định là **đã xác thực** chỉ vì chúng nằm cùng bản tin.

## 4. Ví dụ độc lập Java 21+: `Encapsulator` và `Decapsulator`

Ví dụ này dùng **X25519** tạo cặp khóa nhận, **DHKEM** tạo encapsulator và decapsulator, kiểm tra hai phía thu được cùng shared secret. Nó cũng kiểm tra `encapsulationSize()`, `secretSize()` và lỗi khi truyền thông điệp encapsulation bị cắt ngắn. **Không in byte secret**.

Lưu đúng tên **KemBasicDemo.java**:

```java
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.SecretKey;

public class KemBasicDemo {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        KeyPair receiverKeys = generator.generateKeyPair();

        KEM kem = KEM.getInstance("DHKEM");
        KEM.Encapsulator sender = kem.newEncapsulator(receiverKeys.getPublic());
        KEM.Encapsulated result = sender.encapsulate();

        byte[] encapsulation = result.encapsulation();
        SecretKey senderKey = result.key();

        KEM.Decapsulator receiver =
                kem.newDecapsulator(receiverKeys.getPrivate());
        SecretKey receiverKey = receiver.decapsulate(encapsulation);

        System.out.println("KEM algorithm: " + kem.getAlgorithm());
        System.out.println("encapsulation bytes: " + encapsulation.length);
        System.out.println("shared secret bytes: " + sender.secretSize());
        System.out.println("key algorithm: " + senderKey.getAlgorithm());
        System.out.println("same secret: "
                + MessageDigest.isEqual(
                        senderKey.getEncoded(), receiverKey.getEncoded()));
        System.out.println("optional params absent: "
                + (result.params() == null));

        boolean invalidLengthRejected = false;
        try {
            receiver.decapsulate(
                    Arrays.copyOf(encapsulation, encapsulation.length - 1));
        } catch (DecapsulateException expected) {
            invalidLengthRejected = true;
        }
        System.out.println("truncated encapsulation rejected: "
                + invalidLengthRejected);
    }
}
```

Dùng **JDK 21 trở lên** với provider hỗ trợ `DHKEM` và `X25519`:

```bash
javac -encoding UTF-8 KemBasicDemo.java
java KemBasicDemo
```

Stdout của cấu hình DHKEM/X25519 phổ biến trong JDK (đã chạy kiểm tra):

```text
KEM algorithm: DHKEM
encapsulation bytes: 32
shared secret bytes: 32
key algorithm: Generic
same secret: true
optional params absent: true
truncated encapsulation rejected: true
```

### 4.1. Phân tích từng lời gọi

**Bước 1 — tạo key pair:** `KeyPairGenerator.getInstance("X25519")` dùng thuật toán cặp khóa phù hợp với DHKEM trên X25519. Trong hệ thống thật, key pair của người nhận thường được tạo, lưu và cấp phát theo chính sách, không phải sinh mới mỗi khi nhận file cũ.

**Bước 2 — chọn KEM:** `KEM.getInstance("DHKEM")` chọn API KEM trên provider. `DHKEM` là tên thuật toán, còn profile cụ thể (ở đây X25519 + KDF phù hợp) do key/provider xác định theo hỗ trợ. Có thể phát sinh `NoSuchAlgorithmException` hoặc `InvalidKeyException` nếu thuật toán/key không được hỗ trợ; việc tồn tại lớp `KEM` không đảm bảo mọi provider đều dùng được mọi key type.

**Bước 3 — encapsulation:** `sender.encapsulate()` trả **`KEM.Encapsulated`**, gồm secret phía gửi và message `byte[]`. Trong code chỉ truyền byte message.

**Bước 4 — decapsulation:** `kem.newDecapsulator(receiverKeys.getPrivate())` tạo phía nhận; `receiver.decapsulate(encapsulation)` trả secret cùng giá trị. Demo dùng `getEncoded()` để so sánh cho mục đích học tập; production cần lưu ý khóa **không xuất được** trong HSM/provider.

**Bước 5 — kích thước:** 32 byte encapsulation và 32 byte shared secret là giá trị của profile DHKEM/X25519 trong ví dụ; **không** tự suy ra rằng mọi thuật toán KEM luôn có cùng output 32 byte.

**Bước 6 — dữ liệu thiếu:** `Arrays.copyOf(..., len - 1)` làm message thành 31 byte. `DecapsulateException` cho thấy input sai kích thước bị từ chối.

### 4.2. Một điều rất dễ hiểu nhầm

**Không phải cứ sửa một byte trong encapsulation rồi `decapsulate()` là chắc chắn ném exception.** Với một số KEM, chuỗi byte đã bị sửa nhưng vẫn đúng khuôn dạng có thể cho ra **shared secret khác** thay vì lỗi cú pháp. Vì vậy, receiver phải dùng secret đó trong **AEAD được xác thực**; lỗi tag mới chặn việc chấp nhận plaintext không đúng. Đừng xem “decapsulation trả SecretKey” là bằng chứng sender đáng tin hoặc payload hợp lệ.

## 5. Dùng shared secret: KDF, domain separation và AEAD

KEM cung cấp shared secret; ứng dụng thường cần **khóa đối xứng theo mục đích**. Nếu trực tiếp lấy toàn bộ raw secret làm AES key ở mọi giao thức, dễ phát sinh **key reuse** hoặc thiếu ràng buộc context, nhất là khi cùng một KEM outcome cấp khóa cho encryption, MAC hoặc nhiều mục đích khác nhau.

Một kiến trúc có kiểm soát:

```text
KEM encapsulate/decapsulate
        |
   shared secret (Generic)
        |
   HKDF-Extract/Expand + suite/context/version/recipient binding
        |
     +---------+----------+
     |                    |
   AEAD key           other purpose key (nếu giao thức có thiết kế)
     |
 AES-GCM(key, nonce, plaintext, AAD)
     |
 ciphertext || authentication tag
```

### 5.1. KDF có tác dụng gì ở đây?

- **Chuyển đổi/điều chỉnh vật liệu khóa** theo độ dài của thuật toán đích, ví dụ AES-128 cần 16 byte, AES-256 cần 32 byte.
- **Domain separation:** đưa protocol name, version, algorithm suite, purpose, recipient/tenant vào `info` KDF để tránh lấy một secret cho nhiều mục đích ngoài thiết kế.
- **Context binding:** hai bên đồng ý chính xác cách encode `info`. Khi context không khớp, khóa dẫn xuất không khớp và giải mã AEAD thất bại.
- **Giới hạn quyền:** key dùng cho encryption không tự nhiên được dùng làm HMAC hoặc signing key cho giao thức khác.

DHKEM của RFC 9180 **đã có nội bộ cơ chế dẫn xuất secret từ DH**, nhưng điều đó không có nghĩa đầu ra là key schedule đầy đủ cho mọi ứng dụng. Bước HKDF riêng theo context của ứng dụng vẫn hữu ích để dẫn xuất AES key và tách miền sử dụng.

Trong Java 21, có thể triển khai HKDF theo RFC 5869 bằng `Mac.getInstance("HmacSHA256")` hoặc dùng thư viện mật mã đã được kiểm định. **Không giả định `javax.crypto.KDF` là API chuẩn có sẵn trong Java 21**; bài này cố tình chỉ dùng API chắc chắn có ở Java 21.

### 5.2. Vì sao phải có AES-GCM sau KEM?

KEM chỉ tạo **khóa/secret** và encapsulation message. Nó **không** mã hóa plaintext file, không tự có nonce, không tự tạo authentication tag và không phát hiện việc ai đó thay đổi payload ciphertext. Với AES-GCM:

- `Cipher.getInstance("AES/GCM/NoPadding")` là transformation AEAD.
- `GCMParameterSpec(128, nonce)` chọn **tag 128 bit**, nonce là một `byte[]` riêng; nonce 96 bit/12 byte thường là lựa chọn đơn giản theo profile.
- `Cipher.updateAAD(...)` đưa metadata/encapsulation vào phần dữ liệu được xác thực nhưng **không mã hóa**.
- `Cipher.doFinal(plaintext)` trả ciphertext + tag trong dạng Java provider thông dụng.
- Khi giải mã, **phải để `doFinal()` kiểm tra tag**; lỗi `AEADBadTagException` nghĩa là không chấp nhận plaintext.

Nonce AES-GCM **phải không lặp dưới cùng AES key**. Ví dụ tạo shared secret/KDF key mới cho một message rồi sinh nonce ngẫu nhiên 12 byte; khi thiết kế cho nhiều message trên một key, phải có chiến lược nonce và giới hạn số lượng message theo giao thức, không chỉ dựa vào “nonce dài đúng 12 byte”.

### 5.3. Mã hóa không đồng nghĩa xác thực người gửi

Chỉ biết public key người nhận, **bất kỳ ai** cũng có thể encapsulate và mã hóa một payload mới mà receiver giải được. Do đó, dữ liệu được mã hóa bởi KEM + AEAD đúng có thể **giữ bí mật và toàn vẹn của ciphertext cho một key context**, nhưng **không tự chứng minh tác giả**. Nếu cần xác thực danh tính người gửi, dùng giao thức authenticated, chữ ký số trên envelope, mutual TLS hoặc xác thực bên gửi theo thiết kế; public key của bên ký/xác thực phải có hệ thống trust riêng.

## 6. Ví dụ độc lập Java 21+: KEM → HKDF → AES-GCM

Chương trình này minh họa **một envelope đơn giản trong bộ nhớ**. Alice encapsulate bằng public key X25519 của Bob; hai bên dẫn xuất AES-128 key thông qua một vòng **HKDF-Extract + HKDF-Expand (16 byte output)**, dùng AES-GCM để mã hóa một payload và kiểm tra:

- Hai phía giải mã đúng plaintext.
- Sửa ciphertext sẽ bị GCM từ chối.
- Sửa AAD/header sẽ bị GCM từ chối.
- Dùng sai private key phía nhận sẽ không giải mã/xác thực được payload.

**Lưu ý:** HKDF helper chỉ triển khai **1 block Expand** để lấy **16 byte AES key**; nó **không phải** một triển khai tổng quát cho mọi `L` của RFC 5869. Example giữ mọi byte trong RAM, không serialize wire format hoặc quản lý key ID thật.

Lưu **KemEnvelopeDemo.java**:

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KEM;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class KemEnvelopeDemo {
    private static final byte[] HEADER =
            "demo:v1|DHKEM-X25519|AES-128-GCM|recipient-01"
                    .getBytes(StandardCharsets.UTF_8);

    private static byte[] hmac(byte[] key, byte[]... inputs)
            throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        for (byte[] value : inputs) mac.update(value);
        return mac.doFinal();
    }

    // HKDF-SHA256 Extract, rồi Expand 1 block lấy 16 byte AES-128 key.
    private static SecretKeySpec deriveKey(SecretKey kemSecret)
            throws Exception {
        byte[] raw = kemSecret.getEncoded();
        if (raw == null) {
            throw new IllegalStateException(
                    "non-exportable KEM secret needs provider KDF support");
        }
        byte[] prk = hmac(new byte[32], raw); // salt zero theo HKDF
        byte[] block = hmac(prk, HEADER, new byte[]{1}); // T(1)
        SecretKeySpec key =
                new SecretKeySpec(Arrays.copyOf(block, 16), "AES");
        Arrays.fill(raw, (byte) 0);
        Arrays.fill(prk, (byte) 0);
        Arrays.fill(block, (byte) 0);
        return key;
    }

    private static Cipher newGcm(int mode, SecretKeySpec key,
                                 byte[] nonce, byte[] enc, byte[] header)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(header);
        cipher.updateAAD(enc);
        return cipher;
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator keyGenerator =
                KeyPairGenerator.getInstance("X25519");
        KeyPair recipient = keyGenerator.generateKeyPair();
        KEM kem = KEM.getInstance("DHKEM");

        // Sender: có public key đã được xác thực của receiver.
        KEM.Encapsulated result =
                kem.newEncapsulator(recipient.getPublic()).encapsulate();
        byte[] encapsulation = result.encapsulation();
        SecretKeySpec senderAesKey = deriveKey(result.key());

        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        byte[] plain =
                "file-content-from-sender".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertextAndTag =
                newGcm(Cipher.ENCRYPT_MODE, senderAesKey,
                        nonce, encapsulation, HEADER).doFinal(plain);

        // Receiver: lấy encapsulation + nonce + ciphertext và private key.
        SecretKey recoveredSecret =
                kem.newDecapsulator(recipient.getPrivate())
                   .decapsulate(encapsulation);
        SecretKeySpec recipientAesKey = deriveKey(recoveredSecret);
        byte[] recovered =
                newGcm(Cipher.DECRYPT_MODE, recipientAesKey,
                        nonce, encapsulation, HEADER)
                        .doFinal(ciphertextAndTag);

        System.out.println("encapsulation bytes: " + encapsulation.length);
        System.out.println("AES key bytes: "
                + senderAesKey.getEncoded().length);
        System.out.println("nonce bytes: " + nonce.length);
        System.out.println("GCM tag overhead bytes: "
                + (ciphertextAndTag.length - plain.length));
        System.out.println("round-trip: "
                + Arrays.equals(plain, recovered));

        byte[] edited = ciphertextAndTag.clone();
        edited[0] ^= 1;
        boolean tamperedCiphertextRejected = false;
        try {
            newGcm(Cipher.DECRYPT_MODE, recipientAesKey, nonce,
                   encapsulation, HEADER).doFinal(edited);
        } catch (AEADBadTagException expected) {
            tamperedCiphertextRejected = true;
        }
        System.out.println("changed ciphertext rejected: "
                + tamperedCiphertextRejected);

        boolean changedHeaderRejected = false;
        try {
            newGcm(Cipher.DECRYPT_MODE, recipientAesKey, nonce,
                   encapsulation, "demo:v2".getBytes(StandardCharsets.UTF_8))
                    .doFinal(ciphertextAndTag);
        } catch (AEADBadTagException expected) {
            changedHeaderRejected = true;
        }
        System.out.println("changed AAD rejected: "
                + changedHeaderRejected);

        KeyPair wrongRecipient = keyGenerator.generateKeyPair();
        SecretKey wrongSecret =
                kem.newDecapsulator(wrongRecipient.getPrivate())
                   .decapsulate(encapsulation);
        SecretKeySpec wrongAes = deriveKey(wrongSecret);
        boolean wrongPrivateKeyRejected = false;
        try {
            newGcm(Cipher.DECRYPT_MODE, wrongAes, nonce,
                   encapsulation, HEADER).doFinal(ciphertextAndTag);
        } catch (AEADBadTagException expected) {
            wrongPrivateKeyRejected = true;
        }
        System.out.println("wrong recipient key rejected: "
                + wrongPrivateKeyRejected);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 KemEnvelopeDemo.java
java KemEnvelopeDemo
```

Stdout kỳ vọng với DHKEM/X25519 và AES-GCM của provider JDK thông dụng:

```text
encapsulation bytes: 32
AES key bytes: 16
nonce bytes: 12
GCM tag overhead bytes: 16
round-trip: true
changed ciphertext rejected: true
changed AAD rejected: true
wrong recipient key rejected: true
```

### 6.1. Đọc luồng mã hóa

1. **`recipient`:** cặp khóa X25519 của Bob. Demo sinh cặp khóa trong tiến trình để dễ thử; hệ thống thực phải cấp phát public key từ nguồn đáng tin và lưu private key đúng chính sách.
2. **`KEM.Encapsulated`:** chứa shared secret mới và **32 byte encapsulation**. Chỉ encapsulation được đặt vào envelope; `result.key()` không truyền.
3. **`deriveKey`:** lấy shared secret, dùng HKDF-Extract với salt rỗng/zero theo biểu diễn HMAC, rồi một bước HKDF-Expand có `info = HEADER` và counter `0x01` để lấy **16 byte** cho AES-128. Cả sender và receiver dùng **cùng HEADER** mới tạo cùng key.
4. **`nonce`:** 12 byte ngẫu nhiên cho lần mã hóa này. Nonce không cần bí mật, nhưng phải tránh trùng dưới cùng AES key.
5. **`cipher.updateAAD(HEADER)` rồi `updateAAD(encapsulation)`:** ràng buộc **header và encapsulation** vào tag GCM. Khi receiver thay header, nonce hoặc ciphertext, hoặc dùng sai key, xác minh không thành công.
6. **`ciphertextAndTag`:** payload đã mã hóa + tag GCM; demo không tự lưu thành file. Độ dài tăng **16 byte** vì tag 128 bit; nonce và encapsulation được giữ riêng.
7. **`doFinal` khi decrypt:** chỉ khi tag đúng mới coi plaintext là hợp lệ. Không lấy một phần plaintext từ stream trước xác minh rồi dùng trong nghiệp vụ.

### 6.2. Vì sao có hai chỗ dẫn xuất?

Trong DHKEM của RFC 9180, shared secret vốn được dẫn xuất từ quá trình Diffie–Hellman theo cách của KEM. Sau đó, ứng dụng **tiếp tục dùng HKDF với application context** để thu được key phục vụ một giao thức cụ thể. Điều này giúp phân biệt “bí mật thỏa thuận” với “khóa AES dùng cho payload của phiên bản giao thức X”.

**Không được** thay helper HKDF bằng `new SecretKeySpec(encapsulation, "AES")`, vì encapsulation không bí mật. Cũng không lấy một shared secret và dùng song song làm nhiều khóa độc lập mà không có key schedule/domain separation.

### 6.3. Hạn chế quan trọng của demo

- **DHKEM + một HKDF nhỏ + AES-GCM không tự biến thành triển khai HPKE đầy đủ**. HPKE RFC 9180 định nghĩa key schedule, suite IDs, nonce management, modes và điều kiện sử dụng cụ thể; không thể gọi code mẫu là “HPKE-compatible” nếu chưa triển khai đầy đủ đúng chuẩn.
- Header demo là **hằng số do chương trình tin cậy định nghĩa**, chưa phải serializer/parser production. Nếu header có trường động, hãy dùng cấu trúc length-prefix/TLV/format canonical, không ghép chuỗi tùy ý có delimiter mơ hồ.
- Không có xác thực danh tính sender, anti-replay, key rotation, giới hạn message, streaming hoặc chính sách access. Đây là minh họa thành phần mật mã, **không phải một protocol triển khai nguyên mẫu**.
- `getEncoded()` trong `deriveKey` có thể trả `null` nếu secret không xuất được; provider/HSM có thể yêu cầu KDF nội bộ và cơ chế key handle khác.
- `Arrays.fill` chỉ xóa mảng tạm đang sở hữu ở mức best effort; nó **không bảo đảm** xóa mọi bản sao bên trong JVM/provider. Demo không in raw key nhưng application thật phải xem xét secret retention, log và crash dump.
- Phép thử private key sai với X25519 có thể tạo một secret khác thay vì ném lỗi decapsulation; **AES-GCM** mới bác bỏ ciphertext dùng key sai. Với KEM/provider khác, sai encapsulation có thể bị từ chối ngay trong decapsulation.
- AEAD tag đúng không chứng minh sender là ai, vì **ai cũng có thể dùng public key đích** để tạo một encrypted message mới.

## 7. “Thông điệp đóng gói” khi gửi trên mạng phải chứa gì?

Bản thân `encapsulation()` chỉ có byte KEM; nó không tự có version, người nhận, nonce hoặc ciphertext. Một giao thức thực cần một **envelope** mô tả tất cả byte đủ để receiver chọn đúng thuật toán/khóa và xác minh toàn bộ payload.

Ví dụ schema ý tưởng:

```text
EnvelopeV1:
  magic/version                 :  định dạng được hỗ trợ
  suiteId                       :  KEM + KDF + AEAD được allowlist
  recipientKeyId                :  ID chọn private key, scope đã kiểm tra
  kemParamsLength/kemParams     :  nếu thuật toán cần
  encapsulationLength           :  độ dài binary, giới hạn
  encapsulationBytes            :  KEM message
  nonceLength/nonce             :  nonce của AEAD
  ciphertextLength/ciphertext   :  AEAD ciphertext || tag
  associatedData                 :  metadata đã xác thực (theo quy ước)
```

**Đây chỉ là sơ đồ dữ liệu**, không phải một wire format đã tiêu chuẩn hóa và không phải code Java chạy độc lập. Một định dạng production phải quyết định byte order, length-prefix, phiên bản, kích thước tối đa, xử lý unknown fields, backward compatibility và cách canonicalize dữ liệu được AAD bảo vệ.

### 7.1. Trường nào công khai, trường nào bí mật?

| Dữ liệu | Truyền được? | Ý nghĩa |
| --- | --- | --- |
| Recipient public key | Có, nhưng phải **xác thực nguồn** | Giúp sender encapsulate đúng người nhận |
| Recipient private key | **Không** | Chỉ receiver giữ hoặc HSM sử dụng |
| `KEM.Encapsulated.key()` | **Không** | Shared secret phía sender |
| `encapsulation()` | **Có** | Bắt buộc gửi để receiver decapsulate |
| `params()` nếu được thuật toán yêu cầu | Có nếu giao thức/provider quy định | Receiver dùng để tạo cấu hình tương ứng |
| KDF-derived AES key | **Không** | Chỉ sử dụng nội bộ |
| AEAD nonce | Có | Tham số giải mã, phải duy nhất dưới cùng key |
| AES-GCM ciphertext + tag | Có | Dữ liệu đã mã hóa và authentication tag |
| Suite, version, key ID, payload ID | Thường có | Cho phép chọn đúng cấu hình; phải kiểm tra và ràng buộc integrity |

### 7.2. `keyId` không phải bằng chứng tin cậy

`recipientKeyId` trong envelope đến từ nguồn bên ngoài. Receiver có thể dùng nó để **tìm đúng khóa đã được phê duyệt**, nhưng phải kết hợp tenant/đối tượng, version, chính sách và trạng thái. Không tự động lấy khóa từ đường dẫn/tên file/URL do `keyId` do attacker cung cấp. Không cho client chọn thuật toán yếu hoặc vượt scope chỉ bằng cách sửa `suiteId`.

### 7.3. Ràng buộc metadata vào AAD và KDF context

Nếu header mô tả **recipient, protocol, suite, version, file ID, tenant** nhưng không được xác thực, attacker có thể thay đổi ý nghĩa nghiệp vụ mà ciphertext vẫn hợp lệ dưới một cách diễn giải sai. Nên canonicalize và đưa metadata liên quan tính toàn vẹn vào **AEAD AAD** theo giao thức, đồng thời đưa phần context phù hợp vào **KDF info**.

KDF info và AAD phục vụ hai mục đích bổ trợ:

- **KDF info:** tách miền khóa (domain-separated key schedule).
- **AAD:** bảo vệ integrity của byte metadata gắn với ciphertext.

Nếu header có nhiều trường động hoặc có thể chứa ký tự `|`, không sử dụng nối chuỗi “`a|b|c`” không escape/không length-prefix để làm giao thức thật; hai bộ tham số khác nhau có thể vô tình sinh cùng chuỗi. Thống nhất schema canonical với test vectors liên ngôn ngữ.

### 7.4. `params()` không phải `encapsulation()`

Nếu `result.params() == null`, không cần trường params có nội dung. Nếu thuật toán yêu cầu params, envelope phải mô tả byte params **tách riêng** và receiver khôi phục spec tương ứng trước khi tạo decapsulator.

Không gộp raw `params` vào `encapsulation` rồi hy vọng provider tự phân tích. Dùng `Decapsulator.encapsulationSize()` để kiểm tra phần KEM message thực, không coi chiều dài **toàn bộ envelope** là chiều dài encapsulation.

## 8. Lỗi KEM, kết quả sai và xử lý thất bại

### 8.1. Lỗi khi tạo KEM/Encapsulator

| Lỗi hoặc trạng thái | Nguyên nhân thường gặp | Xử lý |
| --- | --- | --- |
| `NoSuchAlgorithmException` | KEM `"DHKEM"` hoặc thuật toán key không có provider phù hợp | Kiểm tra JDK/provider và policy, không fallback ngầm sang primitive yếu |
| `InvalidKeyException` | Public/private key sai hoặc không phù hợp KEM | Từ chối, kiểm tra loại khóa, trust và phiên bản |
| `InvalidAlgorithmParameterException` | `AlgorithmParameterSpec` sai hoặc thiếu | Thực hiện theo đặc tả thuật toán/provider |
| `UnsupportedOperationException` | Một overload/combination chưa được provider hỗ trợ | Chọn profile được phép hoặc sửa cấu hình, không đoán |
| `DecapsulateException` | Encapsulation hỏng, sai chiều dài hoặc quá trình decapsulation thất bại | Từ chối request, ghi lỗi chung không lộ private-key state |

### 8.2. Không nhầm cú pháp hợp lệ với message hợp lệ

Một encapsulation 32 byte chỉ vượt qua kiểm tra **độ dài**, chưa chắc do người gửi đáng tin tạo. Một decapsulation không ném exception cũng **không chứng minh** payload/metadata hợp lệ. Cần:

1. Chọn KEM, key và context từ cấu hình đáng tin.
2. Kiểm tra độ dài byte và format theo allowlist, giới hạn bộ nhớ.
3. Decapsulate bằng private key có quyền.
4. Dẫn xuất AEAD key đúng context.
5. Xác minh ciphertext/tag/AAD.
6. Sau xác minh còn kiểm tra quyền nghiệp vụ, chống replay, hạn dùng và tính hợp lệ của dữ liệu.

Đặc biệt tránh trả lỗi với thông tin quá chi tiết làm phân biệt các trường hợp private key, encapsulation, tag hoặc policy. Kiểu phản hồi chung như “Không thể xử lý thông điệp bảo mật” thường tốt hơn việc tiết lộ rằng một bí mật cụ thể đã “gần đúng”.

### 8.3. Dữ liệu bị sửa và replay

**Tamper:** sửa ciphertext/AAD/tag phải bị AEAD từ chối. Sửa encapsulation có thể dẫn đến lỗi decapsulation **hoặc** shared secret khác, khiến AEAD tag sai. Không được coi thành công của `decapsulate()` là đủ.

**Replay:** một envelope đúng, được gửi lại nguyên vẹn, vẫn có thể giải mã thành công. Nếu nghiệp vụ yêu cầu chống phát lại (thanh toán, tác vụ thay đổi trạng thái), cần timestamp, nonce/sequence, idempotency key, replay cache hoặc protocol state có kiểm tra nguyên tử. AEAD và KEM **không tự chống replay**.

**Truncation:** kiểm tra payload length, framing, tổng số chunk, finalization marker nếu stream; mỗi chunk có tag đúng không tự bảo đảm toàn file đã đầy đủ trừ khi giao thức ràng buộc rõ.

## 9. KEM trong Java 21 và các thuật toán khác: giới hạn hỗ trợ

`KEM.getInstance("DHKEM")` trong ví dụ là thuật toán **DHKEM** (Diffie–Hellman-based KEM). Với `X25519`, cơ chế tương ứng được mô tả trong **RFC 9180**. Các thuật toán khác có thể được provider bổ sung trong các JDK tương lai; **không suy ra** Java 21 bao gồm mọi KEM hậu lượng tử chỉ vì lớp `KEM` tồn tại.

| Khái niệm | Có thể dùng với Java 21 KEM API? | Lưu ý |
| --- | --- | --- |
| `javax.crypto.KEM`, `KEM.Encapsulator`, `KEM.Decapsulator` | **Có**, API chuẩn kể từ Java 21 | Thuộc `java.base` |
| `DHKEM` với X25519 | Có trong profile/provider JDK thông dụng | Public/private key X25519; encapsulation và secret 32 byte ở cấu hình ví dụ |
| DHKEM với curve khác | Tùy profile/provider | Không giả định cùng kích thước |
| KEM hậu lượng tử như ML-KEM | **Phụ thuộc JDK/provider/version** | Không mô tả là có sẵn trong JDK 21; cần xác minh nơi chạy |
| `javax.crypto.KDF` dạng API cấp cao | **Không phải API Java 21** | Ví dụ dùng `Mac` để minh họa HKDF |
| AES-GCM với `javax.crypto.Cipher` | Có | Phụ thuộc key/nonce/tag và policy |
| HPKE đầy đủ | Không được tạo tự động chỉ bằng `KEM.getInstance` | Phải có đúng key schedule, AEAD, mode, framing và profile chuẩn |

### 9.1. Provider selection ảnh hưởng đến điều gì?

Nếu không chỉ định provider trong `KEM.getInstance(algorithm)`, Java có thể chọn provider khác nhau lúc tạo **encapsulator** và **decapsulator** tùy key/spec. Có thể hỏi:

```java
String kemAlgorithm = kem.getAlgorithm();
String senderProviderName = encapsulator.providerName();
String receiverProviderName = decapsulator.providerName();
int kemMessageBytes = encapsulator.encapsulationSize();
int sharedBytes = decapsulator.secretSize();
```

Đây là trích đoạn để giải thích API chứ không phải chương trình hoàn chỉnh. Trước triển khai, kiểm thử **cùng key type, profile, version, params và wire-format** của cả hai phía. Không ép tên provider chỉ để “khớp” trong khi keys nằm trong HSM: provider cụ thể có thể có key handle riêng và yêu cầu sử dụng API phù hợp.

### 9.2. Provider khác không có nghĩa secret khác

KEM chuẩn và profile phải tạo **shared secret tương thích** cho đúng khóa và encapsulation. Tuy nhiên, việc hai runtime đều có class `KEM` không bảo đảm chúng hỗ trợ cùng suite. Nếu sender Java 21 và receiver hệ thống khác, hai bên phải cùng hiểu X25519 public-key encoding, DHKEM profile, encapsulation bytes, thuật toán KDF, `info` và AEAD wire format.

Dùng **test vector liên nền tảng**; tuyệt đối không dựa vào `toString()` hoặc Java serialization của `SecretKey`/`KEM.Encapsulated` để truyền giao thức.

## 10. Trách nhiệm bảo mật khi dùng KEM

### 10.1. Xác minh public key người nhận

KEM chỉ tốt nếu Alice nhận đúng `pkR` của Bob. Nếu attacker thay public key Bob bằng public key của attacker, Alice sẽ encapsulate một shared secret mà attacker giải được, rồi mã hóa data cho sai người. Cần certificate/PKI, authenticated directory, key pinning theo policy hoặc quy trình cấp phát khóa tin cậy.

### 10.2. Bảo vệ private key dài hạn

Private key của người nhận có thể ở Java KeyStore, HSM/KMS hoặc cơ chế quản lý khóa theo yêu cầu. Phải hạn chế quyền sử dụng, audit, backup và rotation. Nếu private key tĩnh bị lộ, nhiều envelope lịch sử còn giữ encapsulation và ciphertext có thể bị giải mã. Do đó **KEM với một private key người nhận tĩnh không mặc định mang forward secrecy**, dù sender dùng ephemeral key trong DHKEM.

### 10.3. Key rotation/version

Envelope nên có `recipientKeyId` và `suiteVersion`. Khi rotate, sender dùng public key mới đã xác thực; receiver có thể giữ key cũ ở phạm vi cho phép để xử lý dữ liệu còn phải đọc. Nếu mất private key cũ trước khi rewrap/reencrypt dữ liệu, dữ liệu cũ có thể không khôi phục được. Nếu private key bị lộ, phải thu hồi/truyền đạt tác động và chuyển khóa theo incident response, không chỉ đổi tên key ID.

### 10.4. Secret handling

- Không in `result.key().getEncoded()` hoặc derived AES key trong log.
- Không gửi shared secret dưới dạng hex/Base64 vì encoding không phải encryption.
- Không giữ key hơn thời gian cần thiết; giới hạn quyền debug, heap dump, secret exports.
- Java `Arrays.fill` dọn byte array ở mức best effort, không có bảo đảm xóa mọi bản sao trong provider/JVM.
- Nếu provider giữ secret non-exportable, ưu tiên KDF/AEAD theo provider/key handle thay vì ép xuất raw key.

### 10.5. KEM không giải quyết mọi yêu cầu hệ thống

Một hệ thống dùng KEM + AEAD vẫn phải có **authentication sender**, authorization, anti-replay, expiry, quản trị nonce, input validation, cấu hình thuật toán được allowlist, giới hạn tài nguyên, giám sát và kiểm tra nâng cấp. Không xem mã hóa thành công là cho phép thao tác nghiệp vụ.

## 11. Lỗi phổ biến khi học và sử dụng `javax.crypto.KEM`

| Sai lầm | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| Dùng `encapsulation()` như AES key | Encapsulation là message có thể công khai | Dùng `key()` / `decapsulate()` tạo secret, rồi KDF |
| Gửi `result.key().getEncoded()` cho receiver | Làm lộ shared secret, vô hiệu bảo mật | Chỉ gửi encapsulation và metadata cần thiết |
| Gọi `encapsulate(fileBytes)` để mã hóa file | Java KEM không có API encrypt arbitrary message | KEM cho secret; AEAD cho payload |
| Coi KEM là thuật toán mã hóa dữ liệu khối lớn | KEM chỉ thiết lập khóa nhỏ | Hybrid KEM + KDF + AES-GCM/streaming |
| Chỉ cần `KeyPairGenerator("X25519")` là đảm bảo đúng Bob | Public key có thể bị tráo | Xác thực danh tính/nguồn public key |
| Xem `decapsulate()` thành công là xác thực sender | Ai có public key cũng có thể gửi KEM message | Thêm sender authentication nếu cần |
| Xem `params()` là ciphertext | Optional params có nghĩa cấu hình | Parse thành tham số đúng spec/giao thức |
| Nhầm `secretSize()` là bit | API trả byte | Đổi byte × 8 khi cần hiển thị bit |
| Cố định 32 byte cho mọi KEM | Kích thước phụ thuộc thuật toán/provider | Hỏi `encapsulationSize()`, `secretSize()` |
| Dùng `getEncoded()` mà không xét `null` | Key non-exportable có thể không xuất được | KDF nội bộ provider hoặc interface hỗ trợ |
| Coi Java 21 có mọi post-quantum KEM | API ≠ tập thuật toán của provider | Kiểm tra JDK, provider, policy thực tế |
| Dùng `javax.crypto.KDF` và tuyên bố code là Java 21 | API này không thuộc Java 21 | Dùng `Mac`/thư viện HKDF tương thích Java 21 |
| Sửa một byte encapsulation mà không throw nghĩa là hợp lệ | Decapsulation đôi khi tạo secret khác | Dùng AEAD để xác minh payload cuối |
| Không ràng buộc version/keyId/AAD | Context confusion và downgrade | Canonical header, allowlist suite, authenticated metadata |
| Dùng cùng AEAD key/nonce nhiều lần | AES-GCM nonce reuse có thể nguy hiểm nghiêm trọng | Quản lý cặp key/nonce duy nhất và giới hạn |
| Nghiễm nhiên gọi tự ghép KEM + AES là HPKE RFC 9180 | HPKE còn định nghĩa key schedule, modes, format | Theo đúng HPKE suite hoặc thư viện chuẩn |
| Không quản lý private key rotation | Dữ liệu cũ có thể mất hoặc dễ lộ | Key ID, version, backup/re-encrypt/rewrap theo policy |

## 12. Checklist kiểm tra code và giao thức KEM

1. **JDK/API:** ứng dụng đang dùng Java 21+? `javax.crypto.KEM` tồn tại và KEM/provider được chọn hỗ trợ đúng profile?
2. **Key pair:** private key được bảo vệ an toàn và public key người nhận đến từ nguồn đã xác thực?
3. **Thuật toán:** `KEM.getInstance("DHKEM")` có tương thích loại key (ví dụ X25519) ở cả hai phía?
4. **Encapsulation:** chỉ `encapsulation()` được gửi, không có shared secret hoặc derived AES key bị lộ?
5. **Params:** nếu `params()` khác null, receiver có thiết kế parse/validate và tạo spec tương ứng không?
6. **Kích thước:** validate `encapsulationSize()`, giới hạn byte và version trước khi decapsulate/cấp phát?
7. **KDF:** shared secret được đưa qua key schedule/domain separation với context rõ, không dùng enc bytes hoặc một key cho nhiều purpose tùy tiện?
8. **AEAD:** nonce duy nhất với key, tag đúng, AAD ràng buộc metadata và encapsulation, plaintext chỉ được chấp nhận sau verify?
9. **Authentication:** có yêu cầu xác thực bên gửi không, và đã dùng cơ chế riêng thay vì coi public-key encryption là chữ ký?
10. **Envelope:** byte encoding, key ID, suite, params, lengths, AAD và compatibility giữa runtime/ngôn ngữ đã được mô tả và kiểm thử?
11. **Vòng đời:** key rotation, backup/restore, incident response, private-key compromise, secret log/heap dump và error handling đã tính đến?
12. **Kiểm thử âm:** truncated enc, sai key, sai version, sửa ciphertext/tag/header, replay, input quá lớn và thuật toán không hỗ trợ bị từ chối đúng cách?

## 13. Bài tập tự kiểm tra

**Câu 1.** Java 21 dùng class nào để khởi tạo KEM và hai nested class nào ở hai phía?  
**Đáp án:** `javax.crypto.KEM`; phía gửi là `KEM.Encapsulator`, phía nhận là `KEM.Decapsulator`.

**Câu 2.** `sender.encapsulate()` trả về byte array encapsulation trực tiếp hay một object?  
**Đáp án:** `KEM.Encapsulated` object; dùng `result.key()` lấy secret, `result.encapsulation()` lấy byte message, `result.params()` lấy optional params.

**Câu 3.** Trong DHKEM/X25519, ví dụ trả encapsulation 32 byte và shared secret 32 byte. Hai mảng này có giống nhau không?  
**Đáp án:** **Không**. Trùng kích thước không có nghĩa trùng ý nghĩa. Encapsulation có thể công khai; shared secret phải giữ bí mật.

**Câu 4.** Alice gửi `result.key().getEncoded()` qua mạng “để Bob dễ giải mã”. Vấn đề là gì?  
**Đáp án:** Lộ trực tiếp shared secret. Bob phải dùng private key và `encapsulation()` để `decapsulate`, không nhận raw secret.

**Câu 5.** Một `encapsulation()` bị sửa đúng một byte nhưng vẫn có độ dài 32. Có bảo đảm `decapsulate()` throw không?  
**Đáp án:** Không. Tùy KEM/provider, có thể ra secret khác. Phải xác minh AEAD tag của payload và metadata để bác bỏ.

**Câu 6.** Nếu code `encapsulate(0, 16, "AES")` hoạt động, hai tham số số học có nghĩa bit hay byte?  
**Đáp án:** Phạm vi theo **byte** trong shared secret, `from=0`, `to=16` exclusive. Overload decapsulation cần tương thích; không được hiểu là từ bit 0 đến bit 16.

**Câu 7.** Vì sao receiver không thể khôi phục secret từ `encapsulation()` nếu chỉ có public key?  
**Đáp án:** Decapsulation cần private key tương ứng; public key không đủ để khôi phục shared secret trong mô hình bảo mật dự kiến.

**Câu 8.** Dùng KEM + AES-GCM có đủ để Bob biết Alice là người đã gửi không?  
**Đáp án:** Không. Ai có public key Bob cũng có thể tạo secret/envelope mới. Cần xác thực người gửi riêng.

**Câu 9.** Một envelope có `keyId`, `nonce` và `encapsulation`. Chỉ cần parse là có thể tin những trường đó?  
**Đáp án:** Không. Chúng là dữ liệu ngoài; phải validate policy/key scope/độ dài và bind metadata quan trọng vào AAD/KDF context.

**Câu 10.** Có thể đổi Java 21 sang JDK khác rồi mặc định `KEM.getInstance("ML-KEM")` luôn chạy không?  
**Đáp án:** Không. Hỗ trợ thuật toán phụ thuộc JDK/provider phiên bản cụ thể; lớp KEM có từ Java 21 không đồng nghĩa mọi KEM có sẵn.

**Câu 11.** Khi đã có encapsulation, có cần nonce và tag AES-GCM không?  
**Đáp án:** **Có** nếu dùng AES-GCM: KEM không thay thế nonce/tag và không mã hóa payload.

**Câu 12.** Vì sao KEM + KDF + AES-GCM tự viết chưa mặc định tương thích HPKE?  
**Đáp án:** HPKE định nghĩa đầy đủ suite IDs, KDF key schedule, mode, context, nonce policy và các quy tắc giao thức. Phải theo đặc tả HPKE mới có interoperability đúng.

## 14. Tài liệu tham khảo API

- [JEP 452 — Key Encapsulation Mechanism API](https://openjdk.org/jeps/452): giới thiệu lý do thiết kế API mới trong JDK 21.
- [Java SE 21 — javax.crypto.KEM](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.html): contract, factory, overload, provider selection.
- [Java SE 21 — KEM.Encapsulator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.Encapsulator.html): `encapsulate` và hai loại size.
- [Java SE 21 — KEM.Encapsulated](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.Encapsulated.html): `key`, `encapsulation`, `params`.
- [Java SE 21 — KEM.Decapsulator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.Decapsulator.html): `decapsulate`, lỗi và input size.
- [RFC 9180 — Hybrid Public Key Encryption](https://www.rfc-editor.org/rfc/rfc9180): DHKEM, KDF, AEAD và các giao thức HPKE chuẩn.
- [RFC 5869 — HMAC-based Extract-and-Expand Key Derivation Function](https://www.rfc-editor.org/rfc/rfc5869): HKDF-Extract/Expand dùng trong ví dụ dẫn xuất key.

### Ghi nhớ

**Java 21 `KEM` cung cấp primitive đóng gói khóa; `Encapsulator` tạo secret và encapsulation message; `Decapsulator` khôi phục secret bằng private key. Chỉ gửi encapsulation, không gửi secret.** Từ shared secret, thiết kế thêm **KDF có context + AEAD có nonce/AAD/tag**, kiểm chứng public key, chọn đúng key ID/suite và xử lý lỗi. KEM giúp thiết lập khóa an toàn theo mô hình thuật toán, **không thay thế mã hóa dữ liệu lớn, xác thực bên gửi hoặc toàn bộ giao thức bảo mật**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-establishment-and-wrapping-008">10.8 Thực nghiệm DHKEM và phân biệt khóa sinh ra với dữ liệu encapsulation</a>

<details>
<summary>Xem chi tiết</summary>

# 10.8 Thực nghiệm DHKEM và phân biệt khóa sinh ra với dữ liệu encapsulation

## Mục tiêu và yêu cầu môi trường

**DHKEM** (*Diffie–Hellman Key Encapsulation Mechanism*) là một cơ chế thiết lập **bí mật chung** bằng mật mã khóa công khai. Khi bên gửi đã có **public key đáng tin cậy của bên nhận**, họ có thể sinh đồng thời:

1. Một **shared secret** để hai phía sử dụng nội bộ làm vật liệu khóa.
2. Một **encapsulation message** có thể truyền công khai cho bên nhận.

Bên nhận dùng **private key tương ứng** và encapsulation message để tính lại **cùng shared secret**, không cần bên gửi chuyển secret lên mạng.

Bài học này giải thích chính xác vai trò hai kết quả, thử nghiệm API `javax.crypto.KEM` của Java, quan sát độ dài/kiểu dữ liệu, rồi dùng shared secret để dẫn xuất khóa cho AES-GCM. Nội dung nằm trong nhóm **Thỏa thuận và bọc khóa**, nhưng cần phân biệt KEM với **key agreement thuần**, **key wrapping** và **HPKE hoàn chỉnh**.

> **Yêu cầu phiên bản:** `javax.crypto.KEM`, `KEM.Encapsulated`, `KEM.Encapsulator` và `KEM.Decapsulator` xuất hiện từ **Java 21** (JEP 452). Chương trình runnable trong bài yêu cầu **JDK 21 trở lên có provider hỗ trợ `DHKEM` và `X25519`**. Nó **không biên dịch trên Java 17**, dù X25519 và `KeyAgreement` đã tồn tại ở Java 17. Không tự thay bằng `KeyAgreement` rồi gọi đó là cùng DHKEM.

## 1. Bài toán: chia sẻ một bí mật mà không gửi bí mật đó

Giả sử Bob muốn nhận dữ liệu mã hóa từ Alice:

- Bob giữ cặp khóa `(pkR, skR)`: `pkR` được công bố **sau khi xác thực nó thực sự thuộc Bob**, còn `skR` phải được bảo vệ.
- Alice biết `pkR`, nhưng **không biết `skR`**.
- Alice cần tạo một khóa phiên mà Bob cũng phục hồi được; khóa này không được xuất hiện trong thông điệp công khai.

Quy trình **base DHKEM**:

```text
                         BÊN GỬI (ALICE)              BÊN NHẬN (BOB)
                         -----------------            -----------------
Đã có public key Bob:    pkR -----------------------  pkR / skR
                                |                           |
                    KEM.newEncapsulator(pkR)                |
                                |                           |
                         encapsulate()                      |
                          /          \                      |
             shared secret K          encapsulation enc      |
                (GIỮ KÍN)                      |             |
                    |                           | -------->  |
       Dẫn xuất khóa AEAD                     (CÔNG KHAI)    |
                    |                                         |
                    |                             newDecapsulator(skR)
                    |                             decapsulate(enc)
                    |                                         |
               shared secret K <------- cùng giá trị --------+
                    |                                         |
          encrypt với khóa dẫn xuất                 decrypt với khóa dẫn xuất
```

**Quan trọng:** `encapsulation` **không phải shared secret**. Nó là dữ liệu cần để *bên sở hữu private key* tính ra shared secret. Bất kỳ ai thấy `enc` và `pkR` đều biết dữ liệu vận chuyển; họ **không được suy ra khóa bí mật** khi scheme và kích thước khóa còn an toàn.

Với API Java, có thể ghi nhớ cặp kết quả:

```java
// Đoạn minh họa nằm trong phương thức có xử lý checked exceptions:
KEM.Encapsulated result = sender.encapsulate();
SecretKey secret = result.key();        // nội bộ, phải giữ bí mật
byte[] enc = result.encapsulation();    // truyền cho bên nhận
```

Đừng đặt tên cả hai biến là `encryptedKey`: nó làm mờ sự khác nhau giữa **bí mật** và **thông điệp encapsulation**.

## 2. Phân biệt bằng kiểu dữ liệu, nơi sử dụng và mức độ bí mật

| Thuộc tính | `KEM.Encapsulated.key()` | `KEM.Encapsulated.encapsulation()` |
| --- | --- | --- |
| Giá trị trả về | `SecretKey` | `byte[]` |
| Bản chất | **KEM shared secret / keying material** | **KEM encapsulation message** (thường gọi là KEM ciphertext) |
| Nơi sử dụng | Bên gửi giữ kín; dẫn xuất khóa phiên/KDF | Bên gửi **truyền công khai** cho bên nhận |
| Bên nhận làm gì? | Không được nhận `key()` qua mạng; tự thu lại qua `decapsulate` | Đưa nguyên byte vào `decapsulate(enc)` |
| Mức độ bí mật | **Bí mật**: không log, không đặt trong header | **Không phải secret**: có thể lưu/chuyển cùng ciphertext |
| Biểu diễn | `SecretKey` thường có algorithm `"Generic"` khi dùng `encapsulate()` mặc định | Byte encoding theo KEM được chọn, **không tự mang khóa AES** |
| Độ dài | `encapsulator.secretSize()` | `encapsulator.encapsulationSize()` |
| Mục đích bảo mật | Vật liệu để KDF tạo khóa AEAD/MAC | Giúp recipient có `skR` **tái tạo bí mật** |
| Nếu bị công khai | Rò rỉ bí mật; có thể phá bảo mật dữ liệu dùng khóa đó | Bình thường trong thiết kế KEM; **không được giả sử là bí mật** |

**Cùng chiều dài không có nghĩa cùng dữ liệu.** Theo RFC 9180, đối với `DHKEM(X25519, HKDF-SHA256)`, `Nsecret = 32 byte` và `Nenc = 32 byte`. Sự trùng kích thước này khiến người mới học dễ gán nhầm `enc` cho khóa AES-256. Chúng có **ý nghĩa hoàn toàn khác**. Các suite khác cho thấy khác biệt rõ hơn:

| RFC 9180 KEM | KEM ID | `Nsecret` (byte) | `Nenc` (byte) |
| --- | --- | ---: | ---: |
| DHKEM(P-256, HKDF-SHA256) | `0x0010` | 32 | 65 |
| DHKEM(P-384, HKDF-SHA384) | `0x0011` | 48 | 97 |
| DHKEM(P-521, HKDF-SHA512) | `0x0012` | 64 | 133 |
| DHKEM(X25519, HKDF-SHA256) | `0x0020` | 32 | 32 |
| DHKEM(X448, HKDF-SHA512) | `0x0021` | 64 | 56 |

Đây là **giá trị của các suite chuẩn RFC 9180**, không có nghĩa provider Java đang cài hỗ trợ mọi suite. `KEM.getInstance("DHKEM")` kết hợp với **loại khóa** phù hợp sẽ cho provider xác định biến thể; giao thức giữa hai hệ thống vẫn phải thống nhất suite cụ thể.

Đối với `DHKEM(X25519, HKDF-SHA256)`, `enc` là **public key tạm thời của bên gửi được serialize theo quy tắc KEM**, không phải byte của shared secret và **không phải `pkR` của Bob**. Cũng đừng thay `enc` bằng `recipient.getPublic().getEncoded()`: Java public key encoded thường dùng **X.509 SubjectPublicKeyInfo (DER)**, có header/định dạng khác với `enc` raw 32 byte của X25519 DHKEM.

## 3. DHKEM bên trong làm gì?

RFC 9180 §4.1 định nghĩa DHKEM bằng **Diffie–Hellman (DH)** và **HKDF** với domain separation. Trong **base mode**:

- Bob đã có cặp khóa `(skR, pkR)`.
- Alice sinh một cặp khóa **ephemeral** `(skE, pkE)` cho lần encapsulation.
- Alice tính `dh = DH(skE, pkR)`.
- Alice gửi `enc = SerializePublicKey(pkE)`; không gửi `skE` hay `dh`.
- Bob nhận `enc`, tạo `pkE = DeserializePublicKey(enc)`, rồi tính `dh = DH(skR, pkE)`.
- Hai phía dùng **HKDF LabeledExtract/LabeledExpand** trên `dh` và **kem_context** được quy định (`enc || SerializePublicKey(pkR)` trong base mode) để nhận cùng **`shared_secret`**.

Dạng khái niệm (rút gọn để hiểu cấu trúc, **không phải mã triển khai HKDF**):

```text
Encap(pkR):
    (skE, pkE) = GenerateKeyPair()
    enc = SerializePublicKey(pkE)
    dh = DH(skE, pkR)
    kem_context = enc || SerializePublicKey(pkR)
    shared_secret = ExtractAndExpand(dh, kem_context)
    return (shared_secret, enc)

Decap(enc, skR):
    pkE = DeserializePublicKey(enc)
    dh = DH(skR, pkE)
    kem_context = enc || SerializePublicKey(pkR)
    shared_secret = ExtractAndExpand(dh, kem_context)
    return shared_secret
```

Trong thực tế, RFC còn gắn `suite_id`, `"KEM"`, các nhãn và kích thước vào HKDF; không được thay bằng một lệnh `KeyAgreement.generateSecret()` đơn giản rồi tuyên bố cho kết quả **cùng byte DHKEM**. `KeyAgreement` có thể sinh **DH raw secret** nhưng chưa thực hiện đúng toàn bộ **DHKEM KDF và context binding**.

**Điểm cần nhớ:**

1. `enc` thường là dữ liệu công khai đại diện cho **ephemeral public key**, không phải AES-encrypted shared secret.
2. `shared_secret` là đầu ra **sau bước dẫn xuất của DHKEM**, không nhất thiết giống byte raw DH.
3. Nếu gọi lại `encapsulate()` với cùng `pkR`, một ephemeral key mới được sinh, nên thường nhận **enc mới và secret mới**. Đừng dựa vào ngẫu nhiên để tạo test vector cố định trong production.
4. `decapsulate(enc)` với một private key khác có thể tạo **một bí mật khác** thay vì luôn ném exception. `decapsulate` thành công **chưa phải phép xác thực nội dung của ứng dụng**.
5. Base DHKEM không chứng minh “Alice là người gửi”, vì bất kỳ ai có `pkR` đều có thể `encapsulate`. Muốn xác thực danh tính, phải dùng giao thức/chứng chỉ/chữ ký hoặc các mode xác thực phù hợp, không mặc định KEM làm việc đó.

## 4. API Java 21+: cấu trúc và thứ tự gọi

Java 21 cung cấp `javax.crypto.KEM` như một **engine API** trong module `java.base`.

| Bước | Java API | Ý nghĩa |
| --- | --- | --- |
| Sinh cặp recipient | `KeyPairGenerator.getInstance("X25519")` | Tạo khóa DH của Bob; `getPublic()` phân phối sau xác thực, `getPrivate()` giữ kín |
| Chọn KEM | `KEM.getInstance("DHKEM")` | Chọn thuật toán Key Encapsulation Mechanism |
| Tạo đối tượng bên gửi | `kem.newEncapsulator(pkR)` | Chuẩn bị encapsulate bằng public key người nhận |
| Sinh kết quả | `encapsulator.encapsulate()` | Tạo `KEM.Encapsulated` chứa **secret + encapsulation** |
| Lấy secret người gửi | `result.key()` | `SecretKey`, giữ kín và đưa vào KDF |
| Lấy thông điệp truyền | `result.encapsulation()` | `byte[]`, gửi cho bên nhận |
| Tham số nếu có | `result.params()` | Byte tham số **tùy chọn**, có thể là `null`; không giả định mọi KEM đều cần hoặc mọi suite đều có |
| Tạo đối tượng bên nhận | `kem.newDecapsulator(skR)` | Chuẩn bị decapsulation bằng private key người nhận |
| Khôi phục secret | `decapsulator.decapsulate(enc)` | Trả `SecretKey` trùng với bên gửi khi input/key hợp lệ |
| Kiểm tra độ dài | `secretSize()`, `encapsulationSize()` | Số **byte** của bí mật và encapsulation, **không thay thế nhau** |

`KEM` cùng các đối tượng `Encapsulator`/`Decapsulator` trong API được thiết kế immutable và có thể gọi ở các thao tác concurrent theo contract, nhưng **vật liệu secret trả về vẫn phải được bảo vệ**. Khóa từ provider/HSM có thể không xuất được; `getEncoded()` có thể trả `null`. Vì vậy dùng `secretSize()` khi cần biết kích thước mà không phụ thuộc khả năng xuất key.

Thông thường `encapsulate()` tương đương dạng `encapsulate(0, secretSize(), "Generic")`. `SecretKey` mang nhãn **`Generic`**, vì nó là vật liệu khóa dùng cho KDF chứ chưa tự có thông số/loại để đưa thẳng vào AES.

API còn cung cấp overload `encapsulate(from, to, algorithm)` và `decapsulate(enc, from, to, algorithm)` để nhận một đoạn key material được gắn tên thuật toán (ví dụ `"AES"`). Chỉ dùng khi **hai phía thống nhất chính xác phạm vi byte, thuật toán và chính sách dẫn xuất**; cách an toàn, linh hoạt cho giao thức là đưa full KEM secret vào **KDF có domain separation** để tạo key chuyên dụng.

### 4.1 Không pin provider vô cớ

```java
// Đoạn minh họa trong phương thức có xử lý checked exception:
KEM kem = KEM.getInstance("DHKEM");
KEM.Encapsulator sender = kem.newEncapsulator(receiverPublicKey);
String senderProvider = sender.providerName();
```

Khi gọi `KEM.getInstance("DHKEM")` không chỉ định provider, việc chọn implementation có thể phụ thuộc public/private key lúc tạo encapsulator/decapsulator. Có thể tra `providerName()` trên từng đối tượng. **Không giả định hai phía sử dụng cùng provider**; chúng cần đồng thuận về algorithm/variant và encoding chuẩn, rồi kiểm thử interoperability. `DHKEM` là **tên KEM**, còn `X25519` là **thuật toán tạo keypair / nhóm DH**, không phải hai chuỗi có thể thay cho nhau ở `getInstance`.

## 5. Chương trình Java 21 chạy được: phân biệt secret, encapsulation và dùng AES-GCM

Mã bên dưới tạo một recipient X25519, chạy DHKEM, in **chỉ tên/độ dài/kết quả so sánh** (không in hex secret), kiểm tra hai bên tính cùng secret, rồi dẫn xuất một khóa AES-128 qua **HKDF-SHA256** để mã hóa/xác thực một thông điệp bằng **AES-GCM**. Cuối cùng, thử sửa ciphertext và cắt ngắn encapsulation để quan sát sự từ chối.

**Giới hạn có chủ đích:** HKDF ứng dụng và AES-GCM ở đây là **minh họa một tổ hợp KEM → KDF → AEAD**, **không phải implementation HPKE chuẩn RFC 9180**; HPKE còn có key schedule, mode, suite identifier, nonce/sequence và context đầy đủ. Trong ứng dụng thật, ưu tiên thư viện/giao thức HPKE được kiểm chứng khi cần wire compatibility với HPKE.

Tạo file **`DhkemExperiment.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class DhkemExperiment {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String MAC = "HmacSHA256";

    // HKDF-SHA256 Extract + one Expand block, L = 16 bytes.
    // Lesson-specific info: this is NOT the full HPKE key schedule.
    private static SecretKey deriveAes128(SecretKey kemSecret)
            throws GeneralSecurityException {
        byte[] ikm = kemSecret.getEncoded();
        if (ikm == null) {
            throw new GeneralSecurityException("Non-exportable key in demo");
        }
        Mac mac = Mac.getInstance(MAC);
        mac.init(new SecretKeySpec(new byte[32], MAC)); // no salt
        byte[] prk = mac.doFinal(ikm);                  // HKDF-Extract
        mac.init(new SecretKeySpec(prk, MAC));
        mac.update("DHKEM-demo/AES128GCM/v1"
                .getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 1);
        byte[] okm = mac.doFinal();                    // HKDF-Expand T(1)
        return new SecretKeySpec(Arrays.copyOf(okm, 16), "AES");
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        KeyPair recipient = generator.generateKeyPair();

        KEM kem = KEM.getInstance("DHKEM");
        KEM.Encapsulator sender = kem.newEncapsulator(recipient.getPublic());
        KEM.Encapsulated packet = sender.encapsulate();
        SecretKey senderSecret = packet.key();         // keep private
        byte[] enc = packet.encapsulation();           // send to receiver

        KEM.Decapsulator receiver = kem.newDecapsulator(
                recipient.getPrivate());
        SecretKey receiverSecret = receiver.decapsulate(enc);

        System.out.println("KEM: " + kem.getAlgorithm());
        System.out.println("Secret algorithm: " + senderSecret.getAlgorithm());
        System.out.println("Secret bytes: " + sender.secretSize());
        System.out.println("Encapsulation bytes: " + sender.encapsulationSize());
        System.out.println("Secrets match: " + MessageDigest.isEqual(
                senderSecret.getEncoded(), receiverSecret.getEncoded()));

        SecretKey writeKey = deriveAes128(senderSecret);
        SecretKey readKey = deriveAes128(receiverSecret);
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        byte[] message = "Hello DHKEM".getBytes(StandardCharsets.UTF_8);

        Cipher encryptor = Cipher.getInstance("AES/GCM/NoPadding");
        encryptor.init(Cipher.ENCRYPT_MODE, writeKey,
                new GCMParameterSpec(128, nonce));
        encryptor.updateAAD(enc);
        byte[] body = encryptor.doFinal(message);

        Cipher decryptor = Cipher.getInstance("AES/GCM/NoPadding");
        decryptor.init(Cipher.DECRYPT_MODE, readKey,
                new GCMParameterSpec(128, nonce));
        decryptor.updateAAD(enc);
        byte[] recovered = decryptor.doFinal(body);
        System.out.println("AES-GCM round-trip: " + Arrays.equals(
                message, recovered));

        byte[] modified = body.clone();
        modified[0] ^= 1;
        Cipher verifier = Cipher.getInstance("AES/GCM/NoPadding");
        verifier.init(Cipher.DECRYPT_MODE, readKey,
                new GCMParameterSpec(128, nonce));
        verifier.updateAAD(enc);
        boolean tamperRejected = false;
        try {
            verifier.doFinal(modified);
        } catch (AEADBadTagException expected) {
            tamperRejected = true;
        }
        System.out.println("Tampered payload rejected: " + tamperRejected);

        boolean shortEncRejected = false;
        try {
            receiver.decapsulate(Arrays.copyOf(enc, enc.length - 1));
        } catch (DecapsulateException expected) {
            shortEncRejected = true;
        }
        System.out.println("Short encapsulation rejected: " + shortEncRejected);
    }
}
```

Biên dịch/chạy trong thư mục chứa file (JDK 21 trở lên):

```bash
javac --release 21 DhkemExperiment.java
java DhkemExperiment
```

Stdout trên JDK hỗ trợ `DHKEM(X25519, HKDF-SHA256)` như môi trường thử nghiệm:

```text
KEM: DHKEM
Secret algorithm: Generic
Secret bytes: 32
Encapsulation bytes: 32
Secrets match: true
AES-GCM round-trip: true
Tampered payload rejected: true
Short encapsulation rejected: true
```

### 5.1 Đọc kết quả đúng cách

**Hai dòng 32 byte có nghĩa gì?**

- `Secret bytes: 32`: kích thước KEM shared secret của X25519/HKDF-SHA256 là 32 byte.
- `Encapsulation bytes: 32`: kích thước public ephemeral X25519 được serialize dưới dạng `enc` là 32 byte.
- **Không thể suy ra** `senderSecret.getEncoded()` bằng `enc`, hoặc có thể dùng `enc` như khóa AES. Việc **cùng length là trùng hợp của biến thể**.

`Secret algorithm: Generic` cho biết KEM default trả về **generic keying material**, không tự tuyên bố đó là khóa AES-128/256. `Secrets match: true` chứng tỏ khi dùng đúng public/private key và cùng `enc`, hai bên sinh ra cùng secret. Trong demo, so sánh byte được vì provider trả khóa **extractable**; code production làm việc với HSM có thể cần API/KDF của provider mà không xuất secret bytes.

**Vì sao dùng HKDF một lần nữa dù DHKEM đã dùng HKDF nội bộ?** DHKEM dùng HKDF để chuyển **DH shared secret** thành **KEM shared secret**, gắn `kem_context`; còn ứng dụng cần dẫn xuất các **khóa có mục đích sử dụng riêng** (ví dụ AES-GCM, MAC, exporter) theo thông tin phiên bản/ngữ cảnh. Hàm `deriveAes128` minh họa HKDF-SHA256 với extract có salt 32 byte zero (cách biểu diễn trường hợp không có salt trong RFC 5869) và một expand block chứa `info` riêng rồi `0x01`, cắt lấy 16 byte làm AES-128. Đây **không phải HPKE key schedule** và không phải mã nguồn thư viện HKDF tổng quát.

**AES-GCM kiểm tra gì?** Cả hai bên dẫn xuất cùng AES key, dùng cùng nonce 12 byte và `enc` làm **associated authenticated data (AAD)**, do đó giải mã thành công. Sửa byte ciphertext làm GCM tag kiểm tra thất bại. Cắt encapsulation từ 32 thành 31 byte bị `DecapsulateException` vì sai kích thước.

> Trong demo, AAD mới ràng buộc `enc` nhằm làm rõ ý tưởng. Giao thức thực phải ràng buộc thêm **version, suite ID, key ID, recipient/context/metadata cần xác thực** bằng encoding duy nhất. Nonce phải **duy nhất theo mỗi khóa AES-GCM**, không tái sử dụng khi dùng cùng key. KEM base mode + AES-GCM không xác thực tên/danh tính **người gửi**.

### 5.2 Vì sao không in raw secret hoặc cố tạo output hex cố định?

Mỗi lượt `encapsulate()` tạo ephemeral key mới và dùng randomness. Vì vậy `enc` và `shared_secret` thay đổi giữa các lần chạy; in hex ra console/log có thể rò rỉ secret và không giúp chứng minh correctness. Bài chỉ in kiểu, độ dài, equality và kết quả AEAD. Nếu cần **test vectors** tái lập, dùng bộ vector chuẩn RFC 9180 và công cụ dành riêng cho test; không tái dùng ephemeral private key/nonce cố định trong production.

Khi thử `KEM` ở provider khác, đừng mặc định `SecretKey.getEncoded()` luôn trả byte. API có `secretSize()` để biết độ dài và cơ chế provider-native để sử dụng key không xuất được. Nếu provider không hỗ trợ khả năng xuất key, demo sẽ dừng bằng lỗi rõ ràng ở hàm HKDF; ứng dụng thực cần thay phần KDF để đáp ứng chính sách khóa.

## 6. Thiết kế dữ liệu truyền: cái gì có thể gửi, cái gì phải giữ kín?

Một bản ghi dùng KEM để mã hóa payload bằng AEAD có thể được khái niệm hóa như:

```text
+------------------+----------------+----------------+-------------------+
| version/suite ID | key ID (Bob)   | enc (public)   | nonce (public)    |
+------------------+----------------+----------------+-------------------+
| ciphertext (encrypted payload)            | AEAD tag (authenticated)   |
+--------------------------------------------------------------------------+

KHÔNG bao giờ có trường: senderSecret / receiverSecret / privateKey.
```

Thành phần và nơi giữ:

| Dữ liệu | Nơi phát sinh | Có thể truyền? | Lưu ý |
| --- | --- | --- | --- |
| Bob public key `pkR` | Bob/PKI/registry | Có | Alice phải xác thực khóa thuộc Bob và đúng loại/mục đích. |
| Bob private key `skR` | Bob/KMS/HSM | **Không** | Giữ kín, không lưu cùng ciphertext. |
| Sender ephemeral private `skE` | Nội bộ quá trình DHKEM | **Không** | Không được tiết lộ; provider tạo và quản lý. |
| `enc` | `packet.encapsulation()` | **Có** | Là thông điệp KEM, phải được phân loại theo suite và kiểm tra size. |
| KEM shared secret | `packet.key()` / `decapsulate(enc)` | **Không** | Vật liệu dẫn xuất khóa ứng dụng. |
| AEAD nonce | Bên mã hóa dữ liệu | **Có** | Phải unique theo AEAD key, không cần bí mật. |
| AEAD ciphertext/tag | Bên mã hóa dữ liệu | **Có** | Không sử dụng plaintext khi tag sai. |
| Suite/version/key ID | Giao thức ứng dụng | Có | Không được để đầu vào chưa xác thực buộc fallback xuống thuật toán yếu. |

**Dữ liệu encapsulation có thể là đầu vào không tin cậy của parser/crypto provider.** Cần kiểm tra giới hạn độ dài, format, algorithm/key ID và resource policy, bắt lỗi giải mã an toàn. Việc `decapsulate` trả `SecretKey` không đồng nghĩa bản ghi nguồn đã được xác thực hoặc ciphertext ứng dụng có integrity. Chỉ sau khi chứng thực payload/context bằng cơ chế AEAD/chữ ký đúng mới dùng dữ liệu để ra quyết định nghiệp vụ.

## 7. `enc` có phải “bản mã chứa shared key” theo nghĩa thông thường?

Tài liệu KEM đôi lúc gọi `encapsulation` là **KEM ciphertext**. Thuật ngữ này có thể gây hiểu nhầm vì nó **không phải** ciphertext của dữ liệu người dùng và cũng không nhất thiết là một **key đã được encrypt trực tiếp**.

Đối với **DHKEM trên DH group**, `enc` là **serialized ephemeral public key**. Shared secret được hình thành nhờ toán học DH giữa ephemeral private của sender và private của recipient, sau đó qua KDF. Điều này khác với:

- Dùng `Cipher.getInstance("AES/GCM/NoPadding")` để mã hóa message: đầu ra là **AEAD ciphertext + tag**.
- Dùng `Cipher.wrap(aesKey)` với một key-encryption algorithm: tạo representation được wrap của **một khóa đã có trước**. Ngữ nghĩa/định dạng phụ thuộc wrap scheme.
- Dùng `KeyAgreement.getInstance("X25519")`: tạo **raw DH shared secret** từ hai khóa phía mỗi bên; API **không tự tạo encapsulation format + RFC 9180 KDF**.
- Dùng `KEM.encapsulate()`: **sinh vật liệu khóa mới cùng message cho phép decapsulation**; người nhận lấy cùng key material bằng private key.
- Dùng **HPKE RFC 9180**: tổ hợp **KEM + một KDF/key schedule riêng + AEAD** trong một ciphersuite/mode được chuẩn hóa, có quy tắc context và nhiều message.

Đặt tên các biến theo mục đích:

```text
KemOutput = {
    secret: KEM shared secret,         // private
    encapsulation: KEM transport bytes // public
}

Envelope = {
    kemId, recipientKeyId, enc,
    aeadNonce, encryptedPayloadAndTag
}
```

Đừng dùng `secret = enc`, `AES(enc, plaintext)` hoặc `recipientPrivateKey = packet.key()`. Cả ba cách hiểu đều sai về vai trò/thuật toán.

## 8. Lỗi thực nghiệm cần tự kiểm chứng

| Tình huống | Kết quả đúng | Giải thích |
| --- | --- | --- |
| `encapsulate` với Bob.public, `decapsulate` bằng Bob.private | Secret giống nhau | Đúng cặp recipient và biến thể KEM. |
| Dùng `enc` như AES key vì có 32 byte | **Sai thiết kế** | `enc` public, attacker biết; không có confidentiality. |
| Chuyển `packet.key().getEncoded()` cho bên nhận | **Sai thiết kế** | Tiết lộ shared secret lên đường truyền, phá lợi ích KEM. |
| Dùng private key của người khác để decapsulate | Không thu được đúng secret của sender; có thể tạo secret khác hoặc lỗi | **Đừng đòi một exception cố định** cho mọi wrong-key/tamper case. |
| Cắt `enc` từ 32 còn 31 byte (X25519) | `DecapsulateException` | Sai `encapsulationSize`; test minh họa trong code. |
| Lật 1 bit trong `enc` nhưng độ dài vẫn đúng | Có thể trả secret khác hoặc bị từ chối; **không đảm bảo tag-style rejection** | KEM base không có authentication tag của dữ liệu ứng dụng. |
| Lật 1 bit của AES-GCM body/tag | Bị từ chối khi tag verification thất bại | Điều kiện integrity do AEAD cung cấp, không phải KEM tự có. |
| Đổi AAD `enc` sau khi encrypt, giữ nguyên body/tag | GCM verification thất bại | Ràng buộc `enc` vào ciphertext đã xác thực. |
| Gọi `decapsulate()` nhưng quên chuyển đủ `enc` | Lỗi hoặc sai secret | Phải truyền nguyên byte của encapsulation theo format chuẩn. |
| In ra key material để kiểm tra equality | Có nguy cơ lộ secret | Chỉ so sánh nội bộ hoặc dùng vector kiểm thử riêng, không log secret. |
| Chạy trên JDK 17 rồi import `javax.crypto.KEM` | **Lỗi biên dịch** | API bắt đầu Java 21; dùng JDK 21+ hoặc thuật toán/giao thức khác được đặc tả. |
| Đưa raw X25519 public key SPKI vào `decapsulate` thay `enc` | Sai encoding/size | `enc` không phải `PublicKey.getEncoded()` tùy ý. |

**Một điều đặc biệt quan trọng:** Không viết test “mọi encapsulation bị sửa đều khiến `decapsulate` ném lỗi” hoặc “`decapsulate` thành công thì peer đã được xác thực”. Cả hai kết luận đều sai. Kiểm tra **AEAD tag hoặc authentication của giao thức** sau khi thiết lập key material.

## 9. Lựa chọn thuật toán, provider và tương thích

### 9.1 Java 21 và chuẩn RFC 9180

`DHKEM` là tên trong **Java Security Standard Algorithm Names**. Các nhóm DHKEM được RFC 9180 xác định có KEM ID, KDF và encoding riêng. Java `KEM.getInstance("DHKEM")` có thể lựa chọn implementation/variant tương thích dựa trên key type/params; Java 21 API không tự tạo wire-format ciphersuite đầy đủ cho ứng dụng.

Thực hành tốt:

- Ghi **protocol version**, **KEM suite ID** theo chuẩn bạn đã chọn, key ID người nhận và định dạng encapsulation đã thống nhất.
- Trước khi decode, kiểm tra `encapsulationSize()`, kích thước bản ghi và cận trên của mọi length field.
- Nếu application cần tương tác với ngôn ngữ khác, kiểm tra theo **RFC 9180 test vectors** đúng suite, không chỉ dựa vào “cùng X25519”.
- Khi đổi provider, kiểm thử `encapsulate` ở provider A và `decapsulate` ở provider B với cùng public/private key, đúng encoding và params.
- Không ép provider cụ thể như `SunJCE` trừ khi yêu cầu kỹ thuật/compliance/HSM bắt buộc; lúc đó cần mô tả dependency.
- Nếu `KEM.getInstance()` hoặc `newEncapsulator()`/`newDecapsulator()` báo không hỗ trợ algorithm/key, coi đó là lỗi cấu hình; **không tự fallback** sang KEM không được phê duyệt.

### 9.2 Một số ngoại lệ/API behavior

| Vị trí | Lỗi hoặc trạng thái | Hướng xử lý |
| --- | --- | --- |
| `KeyPairGenerator.getInstance("X25519")` | `NoSuchAlgorithmException` | JDK/provider không hỗ trợ tên tạo khóa. |
| `KEM.getInstance("DHKEM")` | `NoSuchAlgorithmException` | KEM chưa khả dụng trên runtime/provider. |
| `newEncapsulator(pkR)` / `newDecapsulator(skR)` | `InvalidKeyException` hoặc thiếu tham số/provider hỗ trợ | Kiểm tra loại/nguồn khóa và policy; public/private key không được hoán đổi. |
| `encapsulate()` | Lỗi provider/randomness | Không tái sử dụng output lỗi hoặc thay bằng key/enc tự chế. |
| `decapsulate(enc)` | `DecapsulateException` khi input có lỗi như size không hợp lệ | Từ chối bản ghi; không log secret, không fallback KEM khác. |
| `secret.getEncoded() == null` | Khóa không xuất được | Dùng API provider-native/KDF phù hợp; **không mặc định byte[] luôn có**. |
| `AES/GCM/NoPadding` `doFinal()` | `AEADBadTagException` | Ciphertext/key/nonce/AAD bị sửa hoặc không khớp; từ chối mọi plaintext. |

`KEM.Decapsulator.decapsulate(encapsulation)` kiểm tra kích thước `enc`; trong ví dụ sai 31 byte được từ chối. Nhưng **validation của cấu trúc KEM không thay thế authentication của payload**.

### 9.3 Vòng đời key pair và tính chất bảo mật

- Recipient public key phải được **xác thực** (PKI, pinning, registry đáng tin). Nếu attacker thay `pkR` trước khi sender encapsulate, sender sẽ chia sẻ secret với attacker chứ không phải Bob.
- Recipient private key phải được bảo vệ bởi keystore/KMS/HSM phù hợp, có key usage/policy, rotation và chiến lược lưu lịch sử khi còn cần giải mã bản ghi cũ.
- Dùng ephemeral sender key mới từng lần giúp phiên hóa bí mật, nhưng nếu Bob dùng **recipient private key tĩnh** và sau này nó bị lộ, attacker có thể decapsulate các `enc` cũ lưu được. **Không mặc nhiên có forward secrecy đối với compromise của khóa nhận tĩnh.**
- Base DHKEM (bất kỳ bên nào biết `pkR` cũng encapsulate được) **không xác thực sender**. AES-GCM chỉ chứng minh ciphertext hợp lệ với key phiên; cần cơ chế xác thực nguồn/identity riêng nếu ứng dụng yêu cầu.
- Không coi KEM X25519 là hậu lượng tử. Nếu threat model yêu cầu hậu lượng tử/hybrid PQ, phải chọn một scheme được đặc tả và triển khai phù hợp, không tự nối bytes của hai KEM rồi gọi là an toàn.
- Chữ ký số, key agreement, KEM, key wrapping và AEAD giải quyết các lớp khác nhau. **Đúng API không tự bảo đảm đúng protocol**.

## 10. Bài tập thực nghiệm mở rộng

**Bài tập 1 — Quan sát độ dài:** Chạy chương trình nhiều lần với X25519. Ghi nhận `Secret bytes=32`, `Encapsulation bytes=32`, kết quả equality. Không in secret. Giải thích vì sao cùng độ dài **không** cho phép gán `enc` làm key.

**Bài tập 2 — Sai khóa nhận:** Sinh thêm một cặp X25519, dùng private key mới gọi `decapsulate(enc)` từ cặp cũ. Nếu provider trả secret, so sánh **nội bộ** với sender secret; không giả định bắt buộc ném exception. Sau đó thử dùng khóa sai với AES-GCM để xác minh tag không chấp nhận ciphertext.

**Bài tập 3 — Sai độ dài:** Sửa `enc` thành 31 hoặc 33 byte; dự đoán `DecapsulateException` và đối chiếu `encapsulationSize()` trước khi gọi để parser không chấp nhận dữ liệu thừa/thiếu.

**Bài tập 4 — Tách nguồn lỗi:** Thử riêng (a) hỏng encapsulation, (b) hỏng AEAD body, (c) sai AEAD nonce, (d) sai AAD. Phân biệt lỗi KEM với lỗi GCM tag; không in ra private key hoặc plaintext chưa xác thực.

**Bài tập 5 — Chuẩn hóa format:** Đề xuất binary envelope có magic/version, KEM suite, recipient key ID, `enc` length + bytes, AEAD nonce, ciphertext length + body/tag. Định nghĩa encoding/endianness, max sizes và phạm vi AAD. Nêu rõ **không có trường truyền KEM shared secret**.

**Bài tập 6 — Interoperability:** Nếu có hai provider, thử encapsulate/decapsulate chéo. Giữ nguyên suite/encoding và so sánh kết quả với test vector RFC 9180, tránh chỉ kiểm tra một runtime.

## 11. Checklist thiết kế và review

- [ ] Runtime dùng **Java 21+** cho `javax.crypto.KEM`; không gắn nhầm vào API Java 17.
- [ ] Recipient key pair phù hợp `DHKEM` (ví dụ X25519); public key được **xác thực danh tính**.
- [ ] `KEM.Encapsulated.key()` được bảo vệ như **secret**, không gửi/log/lưu công khai.
- [ ] `KEM.Encapsulated.encapsulation()` được gửi/lưu như **public transport message**, không coi nó là raw secret hoặc khóa AES.
- [ ] Bên nhận dùng **private key recipient** và đúng byte `enc` cho `newDecapsulator(...).decapsulate(...)`.
- [ ] Protocol biết suite/algorithm, encoding và `encapsulationSize()`, có max-length và version/key ID rõ ràng.
- [ ] KEM shared secret được dẫn xuất thành key cho mục đích riêng bằng **KDF/HPKE key schedule phù hợp**; không dùng raw DH secret thay cho output DHKEM.
- [ ] Payload dùng AEAD có nonce/tag/AAD đúng; tag thất bại thì **fail closed**.
- [ ] Không khẳng định `decapsulate` tự xác thực người gửi, chống replay hoặc luôn báo lỗi khi encapsulation bị thay bit.
- [ ] Có kế hoạch quản lý/luân chuyển recipient private key và threat model compromise/forward secrecy.
- [ ] Có kiểm thử đúng/sai key, truncated `enc`, tampered payload, mismatched AAD, cross-provider và encoding.

## 12. Câu hỏi tự kiểm tra

**1. `packet.key()` và `packet.encapsulation()` khác nhau thế nào?**  
`key()` là **secret `SecretKey`**, phải giữ kín, thường đưa vào KDF. `encapsulation()` là **byte thông điệp KEM công khai** gửi cho recipient, không chứa bản sao raw secret.

**2. Với X25519, vì sao cả hai đều dài 32 byte?**  
Bởi RFC 9180 quy định **`Nsecret = Nenc = 32`** cho suite này. Hai giá trị cùng độ dài nhưng khác vai trò và cơ chế tạo; nhiều suite khác có Nenc và Nsecret khác nhau.

**3. Bob cần dữ liệu gì để lấy cùng shared secret?**  
Private key `skR` và byte `enc` của sender, thêm lựa chọn KEM/params nhất quán. Bob **không cần nhận secret từ sender**.

**4. Đã `decapsulate(enc)` được `SecretKey` có chứng minh Alice gửi không?**  
**Không.** Base DHKEM cho bất kỳ ai có `pkR` làm encapsulation; cần xác thực sender theo giao thức nếu cần.

**5. Có thể dùng `getEncoded()` của X25519 public key thay cho `enc` không?**  
**Không tùy tiện.** `enc` tuân theo serialization của KEM (raw ephemeral public key X25519 32 byte), còn `PublicKey.getEncoded()` trong Java thường là X.509 SPKI.

**6. Vì sao Java 17 không chạy ví dụ?**  
API `javax.crypto.KEM` được bổ sung từ **Java 21**. `KeyAgreement` X25519 từ phiên bản cũ không tự thực hiện cùng `DHKEM ExtractAndExpand`.

**7. Dữ liệu encapsulation bị thay đổi có luôn gây `DecapsulateException` không?**  
**Không.** Input sai kích thước bị lỗi, còn input đủ kích thước nhưng khác byte có thể tạo secret khác. Cần **AEAD tag hoặc authentication riêng** để phát hiện sai lệch của dữ liệu ứng dụng.

**8. Ví dụ Java trong bài có phải HPKE RFC 9180 đầy đủ không?**  
**Không.** Nó dùng chuẩn DHKEM, thêm một HKDF đơn giản có info riêng và AES-GCM để học cách kết hợp. HPKE chính thức có key schedule/context/modes/nonce quy định chi tiết và cần implementation riêng.

## Nguồn tham khảo chính thức

- [Java SE 21 — `javax.crypto.KEM`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.html): mô hình sender/receiver, `newEncapsulator`, `encapsulate`, `newDecapsulator`, `decapsulate`, lựa chọn provider.
- [Java SE 21 — `KEM.Encapsulated`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.Encapsulated.html): `key()`, `encapsulation()`, `params()` và kiểu dữ liệu của từng trường.
- [Java SE 21 — `KEM.Encapsulator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.Encapsulator.html): `secretSize`, `encapsulationSize`, overload `encapsulate`.
- [Java SE 21 — `KEM.Decapsulator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.Decapsulator.html): `decapsulate` và các điều kiện lỗi.
- [Java SE 21 — `DecapsulateException`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/DecapsulateException.html): lỗi trong quá trình decapsulation.
- [Java SE 21 — Standard Algorithm Names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html): tên `DHKEM` trong nhóm KEM và chuẩn hóa thuật toán.
- [OpenJDK JEP 452 — Key Encapsulation Mechanism API](https://openjdk.org/jeps/452): lý do ra đời và phạm vi API (JDK 21).
- [RFC 9180 — Hybrid Public Key Encryption](https://www.rfc-editor.org/rfc/rfc9180.html): §4.1 DHKEM, §7.1 các KEM IDs và độ dài Nsecret/Nenc, §5 HPKE key schedule/context.
- [RFC 5869 — HMAC-based Extract-and-Expand Key Derivation Function (HKDF)](https://www.rfc-editor.org/rfc/rfc5869): extract/expand và context binding ở mức KDF.

**Ghi nhớ:** `KEM.encapsulate()` **sinh ra hai thứ**: **`SecretKey` để giữ kín và dẫn xuất khóa**, và **`encapsulation` để gửi công khai cho người nhận**. `decapsulate(enc)` kết hợp dữ liệu công khai đó với **private key** để tái lập shared secret. Dùng `enc` như khóa bí mật là lỗi thiết kế nghiêm trọng; đồng thời không nhầm KEM base với mã hóa AEAD, xác thực danh tính, hay full HPKE.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-establishment-and-wrapping-009">10.9 Quan hệ giữa cơ chế nguyên thủy và một giao thức hoàn chỉnh có xác thực</a>

<details>
<summary>Xem chi tiết</summary>

# 10.9. Quan hệ giữa cơ chế nguyên thủy và một giao thức hoàn chỉnh có xác thực

Một **cơ chế mật mã nguyên thủy** (*cryptographic primitive*) thực hiện một chức năng có phạm vi cụ thể: Diffie–Hellman tạo bí mật chung, KEM đóng gói bí mật cho người nhận, chữ ký số xác minh dữ liệu do bên giữ private key ký, HKDF dẫn xuất các khóa, AEAD mã hóa có xác thực, còn key wrapping bảo vệ khóa khi vận chuyển hoặc lưu trữ. **Một giao thức bảo mật hoàn chỉnh** phải xác định cách các cơ chế đó phối hợp với danh tính, tin cậy public key, trạng thái bắt tay, phiên bản, tham số, bản ghi, vòng đời khóa, chống phát lại và xử lý lỗi.

Đây là bài 10.9 trong chủ đề **“10. Thỏa thuận và bọc khóa”** của Java Advanced – Security & Cryptography. Hai câu hỏi xuyên suốt là:

1. *“Hai phía có cùng một byte secret”* có chứng minh họ đang nói chuyện đúng người không?
2. *“Đã ký, bọc khóa và mã hóa bằng thuật toán an toàn”* có tự tạo nên một giao thức an toàn không?

Câu trả lời cho cả hai câu hỏi đều là **không**, nếu còn thiếu ràng buộc danh tính, bối cảnh, transcript, kiểm tra khóa và trạng thái. Bài này giải thích vì sao, kèm hai chương trình Java 21 chạy được và sơ đồ tư duy để đánh giá giao thức.

## Mục tiêu học tập

Sau bài học, bạn có thể:

- Phân biệt chức năng của **X25519/ECDH, KEM, key wrapping, Ed25519/Signature, HKDF, HMAC và AES-GCM**.
- Trình bày một luồng **man-in-the-middle (MITM)** nơi Diffie–Hellman chạy đúng thuật toán nhưng client trao secret với attacker.
- Hiểu cách một handshake xác thực cần **trusted identity key**, vai trò client/server, thuật toán đã thương lượng, ephemeral public key, nonce, **transcript binding**, key confirmation và kiểm tra phiên.
- Giải thích *forward secrecy*, replay protection, unknown-key-share, downgrade, key/algorithm confusion, nonce reuse, và authentication/authorization khác nhau thế nào.
- Chạy Java 21 với `KeyAgreement("X25519")`, `Signature("Ed25519")`, `Mac("HmacSHA256")` và `Cipher("AES/GCM/NoPadding")`; đọc kết quả và nhận diện chính xác giới hạn của demo.
- Xác định khi nào nên dùng **TLS 1.3, JSSE, HPKE, một giao thức đã chuẩn hóa** thay vì tự ghép primitives thành protocol mới.

## 1. Bản đồ: primitive trả lời câu hỏi nào?

| Thành phần | Ví dụ Java 21 / chuẩn | Nó cung cấp | Nó **không tự cung cấp** |
| --- | --- | --- | --- |
| Thỏa thuận khóa (key agreement) | `KeyAgreement.getInstance("X25519")`; ECDH | Hai bên có cặp khóa phù hợp tính ra một **shared secret** | Danh tính của đầu bên kia, transcript đã thương lượng, chống MITM |
| KEM (key encapsulation) | Khái niệm KEM, `javax.crypto.KEM` (API từ Java 21, phụ thuộc provider/algorithm) | Encapsulate tạo ciphertext + secret; decapsulate khôi phục secret với khóa tương ứng | Mặc định xác thực người gửi, bảo vệ toàn bộ transcript, replay/authorization |
| Bọc khóa (key wrapping) | `Cipher.WRAP_MODE/UNWRAP_MODE` với scheme được hỗ trợ; AES-KW hoặc RSA-OAEP dùng đúng mục đích | Bảo vệ một khóa được vận chuyển hoặc lưu | Xác nhận ai đề xuất chính sách, key thuộc phiên/recipient nào, toàn bộ tính đúng đắn giao thức |
| Chữ ký số | `Signature.getInstance("Ed25519")`, `RSASSA-PSS` | Một public key xác minh được một thông điệp đã được ký bằng private key tương ứng | Tin cậy rằng public key thực sự thuộc Alice/Bob; freshness nếu không ký và kiểm tra ngữ cảnh |
| MAC/HMAC | `Mac.getInstance("HmacSHA256")` | Kiểm tra integrity và nguồn gốc theo bí mật chung | Phân biệt hai người cùng biết khóa, tính mới, bí mật nội dung |
| KDF/HKDF | HKDF-Extract/Expand, HMAC-SHA256 | Chuyển input secret thành các khóa riêng theo **label/context** | Tự xác thực bên gửi hoặc sửa lỗi một shared secret đã lộ |
| AEAD | `Cipher.getInstance("AES/GCM/NoPadding")` | Bảo vệ nội dung và AAD theo secret key, IV và tag | Danh tính người gửi nếu key chưa được xác thực, chống phát lại nguyên bản mã |
| Random nonce/sequence | `SecureRandom`, bộ đếm bản ghi | Dữ liệu duy nhất/đánh số theo chính sách | Chống phát lại nếu không được bảo vệ và không có kiểm tra trạng thái |

**Điều kiện cần khác điều kiện đủ:** mã hóa có xác thực có thể chứng minh message dùng đúng **traffic key**, nhưng nếu attacker đã lừa bạn thiết lập traffic key với mình, tag AEAD hoàn toàn hợp lệ mà **peer sai**.

### 1.1 Ba lớp rất dễ bị gộp nhầm

- **Bí mật đã đồng thuận (secret agreement):** `K_A == K_B` chỉ khi hai bên tham gia đúng cuộc trao đổi khóa. Chưa có phép kiểm danh tính.
- **Xác thực peer (peer/entity authentication):** xác định đúng người sở hữu key đã được tin cậy. Signature có thể chứng minh quyền sử dụng private key **nếu public key đã được gắn với danh tính**; muốn biết peer thực sự hiện diện còn cần các bước về freshness/possession/liveness theo giao thức.
- **Xác thực message (message authentication):** dữ liệu/ciphertext không bị sửa và được tạo bởi bên nắm khóa MAC/AEAD. Đây không phải quyền thực hiện giao dịch (*authorization*) và không đồng nghĩa request chưa từng xuất hiện.

Một giao thức tốt kiểm tra **đúng peer, đúng message, đúng phiên, đúng phiên bản, đúng thời điểm và đúng quyền** trước khi gây ra tác động nghiệp vụ.

## 2. Các thành phần của một giao thức trao đổi khóa có xác thực

Một handshake thực tế có thể cần các lớp sau; không nhất thiết được triển khai bằng đúng thứ tự code ở một thư viện cụ thể.

1. **Vai trò và mục tiêu:** ai là client/server, cần xác thực một chiều hay hai chiều, cần confidentiality, forward secrecy, replay protection đến đâu?
2. **Chính sách và thương lượng:** version, nhóm key agreement/KEM, signature algorithms, AEAD, KDF, allowed extensions. Thỏa thuận phải được bảo vệ chống sửa/downgrade.
3. **Định danh và trust anchor:** chứng thư X.509 + truststore, key fingerprint được pin, hoặc public key đáng tin lấy từ registry. Phải ràng buộc danh tính với khóa đang xác minh.
4. **Ephemeral key share / encapsulation:** tạo một session secret mới theo thuật toán phù hợp. Có thể dùng (EC)DHE/X25519, KEM hoặc kết hợp theo protocol.
5. **Authentication và transcript integrity:** ký/MAC đúng transcript, bao gồm role, phiên bản, lựa chọn thuật toán, danh tính, key shares và các metadata có ý nghĩa.
6. **Key derivation + separation:** dùng KDF được thiết kế cho key material để sinh **traffic key, IV/base nonce, finished/key-confirmation key**, với label riêng cho từng hướng/bước.
7. **Key confirmation:** xác minh hai phía đang sở hữu secret/phát triển đúng key trên **cùng transcript**, thường qua Finished MAC hoặc authenticated record; chỉ cho sử dụng data khi state machine cho phép.
8. **Record protection:** AEAD với nonce/sequence không trùng dưới cùng key, AAD đầy đủ, giới hạn record, anti-replay theo thiết kế.
9. **Vòng đời phiên:** timeout, rekey, đóng phiên, khôi phục kết nối, xử lý mất gói, xác định lỗi và kiểm soát tài nguyên.
10. **Application policy:** principal, authorization, idempotency, chống phát lại lệnh nghiệp vụ; không tự cho phép giao dịch chỉ vì TLS/handshake thành công.

### 2.1 Vì sao “chạy từng API đều thành công” chưa đủ?

Giả sử một ứng dụng gọi:

```text
X25519 agreement: success
HKDF: success
AES/GCM decryption: success
```

Các lệnh này nói rằng *toán học và key có thể tương thích*, **không chứng minh** server đang nói chuyện là server mục tiêu. Nếu key share của peer bị attacker thay từ đầu, client có thể tạo secret với attacker; attacker cũng giải mã và tạo tag AES-GCM hợp lệ. Đây là lỗ hổng **giao thức**, không phải lỗi thuật toán X25519 hay AES-GCM.

## 3. Tấn công MITM khi chỉ dùng Diffie–Hellman

Alice muốn trao đổi khóa với Bob; Mallory có thể chặn, thay public key trên đường truyền.

```text
 Alice                    Mallory                       Bob
   |                         |                           |
   | --- ephemeral pk_A ---->|                           |
   |                         |---- fake pk_M2 ---------->|
   |<-- fake pk_M1 ----------|                           |
   |                         |<-------- ephemeral pk_B ---|
   |                         |                           |
   | K_A = DH(sk_A, pk_M1)   |                           |
   |                         | K_MA = DH(sk_M1, pk_A)     |
   |                         | K_MB = DH(sk_M2, pk_B)     |
   |                         |                  K_B = DH(sk_B, pk_M2)
   |                         |                           |
 K_A == K_MA                   true           K_MB == K_B
```

Alice và Bob đều có thể tin “đã bắt tay bằng X25519”. Thực tế Mallory biết **hai secret độc lập**; nếu các phía dùng chúng để mã hóa, Mallory có thể giải mã và mã hóa lại giữa hai kênh. Không cần phá mật mã, chỉ cần thay public key chưa được xác thực.

### 3.1 Demo Java 21: thuật toán đúng nhưng peer sai

Tạo file **`UnauthenticatedDhDemo.java`**:

```java
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.KeyAgreement;

public class UnauthenticatedDhDemo {
    static byte[] agree(PrivateKey mine, PublicKey peer) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(mine);
        ka.doPhase(peer, true);
        return ka.generateSecret();
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("X25519");
        KeyPair alice = g.generateKeyPair();
        KeyPair bob = g.generateKeyPair();

        // Mallory thay public key riêng khi nói chuyện với mỗi đầu.
        KeyPair malloryToAlice = g.generateKeyPair();
        KeyPair malloryToBob = g.generateKeyPair();

        byte[] aliceSecret = agree(alice.getPrivate(),
                                   malloryToAlice.getPublic());
        byte[] malloryAlice = agree(malloryToAlice.getPrivate(),
                                    alice.getPublic());
        byte[] bobSecret = agree(bob.getPrivate(),
                                 malloryToBob.getPublic());
        byte[] malloryBob = agree(malloryToBob.getPrivate(),
                                  bob.getPublic());

        System.out.println("Alice shares with Mallory: " +
                MessageDigest.isEqual(aliceSecret, malloryAlice));
        System.out.println("Bob shares with Mallory: " +
                MessageDigest.isEqual(bobSecret, malloryBob));
    }
}
```

Biên dịch/chạy với JDK 21:

```text
javac --release 21 -encoding UTF-8 UnauthenticatedDhDemo.java
java UnauthenticatedDhDemo
```

Stdout:

```text
Alice shares with Mallory: true
Bob shares with Mallory: true
```

**Giải thích:** X25519 không sai: mỗi phía đã chia sẻ secret với **đúng public key mà nó nhận**. Sai lầm nằm ở chỗ Alice/Bob **không biết public key nhận được thuộc ai**. Code không triển khai TCP hay sửa gói trên mạng; nó mô phỏng việc attacker thay key ngay tại ranh giới nhận. Thử `MessageDigest.isEqual` chỉ kiểm tra hai byte array giống nhau, không kiểm danh tính. Demo không quản lý bộ nhớ secret để giữ ví dụ ngắn; production cần chính sách lifecycle key/secret nghiêm ngặt.

## 4. Xác thực exchange: tin đúng khóa trước khi tin vào chữ ký

Một cách giáo dục để ngăn thay key là mỗi phía có một **identity signing key** đã được phân phối tin cậy trước đó, và ký dữ liệu bắt tay bao gồm ephemeral public key.

```text
Alice có skA_sign, pkA_sign đã được Bob tin cậy
Bob   có skB_sign, pkB_sign đã được Alice tin cậy

ClientOffer = version, suite, role=client, id=Alice, nonceA, ephemeralPubA
sigA = Sign(skA_sign, Encode(ClientOffer))

ServerOffer = version, suite, role=server, id=Bob, nonceB, ephemeralPubB
sigB = Sign(skB_sign, Encode(ServerOffer))

Alice xác minh sigB với pkB_sign ĐÁNG TIN và expected id=Bob
Bob   xác minh sigA với pkA_sign ĐÁNG TIN và expected id=Alice
Cả hai xác nhận một transcript có thứ tự và bind shared secret vào transcript
```

**Nếu Mallory thay ephemeral public key mà giữ signature cũ, `verify` thất bại.** Mallory cũng không thể tự ký một offer mới dưới danh tính Bob nếu không có private key của Bob và Bob’s public key tin cậy được Alice kiểm đúng.

### 4.1 Xác thực khóa khác xác thực danh tính

Một public key được gửi kèm chữ ký tự tạo bởi key đó **chưa đủ**: kẻ tấn công có thể tạo cặp khóa của mình, tự ký và tự nhận là Bob. Client phải có **trust anchor** như certificate chain đúng policy/hostname, certificate pin hợp lệ, hoặc key registry đáng tin. Kiểm tra `Signature.verify() == true` chỉ chứng minh *private key tương ứng với public key đã đưa vào bộ kiểm* tạo ra chữ ký, **không chứng minh khóa thuộc Bob nếu chưa xác thực nguồn**.

**Dữ liệu cần được ký/bind:** protocol version, role, identity hoặc subject, key/algorithm identifiers, ephemeral key share, nonce, lựa chọn thuật toán, mục tiêu/audience. Nếu chỉ ký public key mà không ký **vai trò và phiên bản**, attacker có thể gây *role confusion*, *downgrade* hoặc phát lại dữ liệu giữa những giao thức gần giống.

### 4.2 Vì sao ràng buộc transcript?

**Transcript** là toàn bộ chuỗi handshake messages theo thứ tự và encoding đã định, gồm các trường an ninh có ý nghĩa. Một transcript hash như `SHA-256(Encode(clientOffer) || Encode(serverOffer) || ...)` được đưa vào key schedule hoặc chữ ký/MAC giúp hai phía phát hiện nếu chúng không nhất trí:

- ai là client/server và danh tính mong đợi;
- đã thương lượng thuật toán, version, extension nào;
- đã nhận những ephemeral public keys, nonces và context nào;
- session này dành cho **dịch vụ, tenant, ứng dụng, tài nguyên** nào (theo protocol);
- có thông điệp bị thêm, bỏ, sắp xếp lại hoặc tráo ngữ cảnh hay không.

Không nên ghép text tùy ý: `"A" + "BC"` và `"AB" + "C"` cho kết quả trùng. Hãy dùng length-prefix, encoding nhị phân đã công bố, hoặc canonical serialization được kiểm thử. Ở giao thức chuẩn, **transcript construction là một phần đặc tả**, không tự chọn tùy tiện.

### 4.3 HKDF dùng shared secret trong đúng bối cảnh

Với X25519, `generateSecret()` cung cấp byte secret (input key material – IKM). Thường dùng HKDF:

```text
PRK = HKDF-Extract(salt, X25519_secret)
K_c2s = HKDF-Expand(PRK, label(client-to-server) || context, 32)
K_s2c = HKDF-Expand(PRK, label(server-to-client) || context, 32)
K_finished = HKDF-Expand(PRK, label(key-confirmation) || context, 32)
```

HKDF chuẩn hóa trong [RFC 5869](https://www.rfc-editor.org/rfc/rfc5869). `salt` và `info/context` được dùng đúng mục đích theo thiết kế; **không dùng bytes shared secret trực tiếp làm khóa AES chỉ vì có đúng độ dài**, cũng không lấy SHA-256 ngẫu nhiên không có phân tách mục đích để tạo mọi loại key. Labels/context của từng hướng giúp tránh dùng cùng key cho các kênh hoặc nhiệm vụ khác nhau.

Dùng transcript hash làm input công khai trong key schedule giúp **key binding** với cuộc bắt tay. Đừng nhầm nó với kiểm tra chữ ký: nếu không có identity authentication, một attacker đang tham gia exchange vẫn có thể tính cùng secret và transcript với endpoint. **HKDF không thay thế chữ ký hoặc một phương thức peer authentication chuẩn.**

## 5. Bài thực hành Java 21: minh họa *các* lớp phối hợp

Chương trình tiếp theo dùng:

- **Ed25519 identity key pairs** được tạo trước và giả định public keys đã được trao tin cậy ngoài băng (demo giữ cùng process để dễ kiểm tra).
- **X25519 ephemeral key pairs** dùng để tạo shared secret tạm thời. Bên nhận xác minh chữ ký trên offer, role/id, suite/nonce trước khi đồng ý sử dụng key share.
- **Length-prefix encoding** của các trường handshake để tránh nhập nhằng; `SHA-256` transcript trong một thứ tự cố định.
- **HKDF-HMAC-SHA256** ở mức **một khối Expand 32 byte** cho **một khóa AES-256 duy nhất hướng client→server**, đủ cho một tin nhắn demo.
- **AES-GCM** với IV/nonce ngẫu nhiên 12 byte mới và transcript hash làm AAD. Thay AAD khiến tag kiểm tra thất bại.

Đây là **thí nghiệm minh họa các mối nối giữa primitives**. Chưa phải giao thức để triển khai production: thiếu network framing/parser, certificate-chain verification, finished/key-confirmation **hai chiều**, replay cache, session state, sequence/counter cho nhiều record, quản lý thời gian/rotate/rekey, xử lý đồng thời, secret erasure bảo đảm toàn diện, state machine chống downgrade và nhiều tiêu chí chuẩn khác. Để xây dựng kênh thực tế, dùng TLS 1.3/JSSE hoặc protocol được chuẩn hóa, đánh giá.

Tạo file **`AuthenticatedChannelDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AuthenticatedChannelDemo {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SUITE =
            "demo-v1:X25519:Ed25519:HKDF-SHA256:AES-256-GCM";

    record Offer(String role, String identity, byte[] nonce,
                 byte[] publicKey, byte[] signature) {}

    static void put(DataOutputStream out, byte[] bytes) throws Exception {
        out.writeInt(bytes.length);
        out.write(bytes);
    }
    static void put(DataOutputStream out, String string) throws Exception {
        put(out, string.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] offerBytes(Offer offer) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        put(out, SUITE);
        put(out, offer.role());
        put(out, offer.identity());
        put(out, offer.nonce());
        put(out, offer.publicKey());
        out.flush();
        return buffer.toByteArray();
    }

    static Offer createOffer(String role, String identity,
                             PublicKey ephemeral, PrivateKey signingKey)
            throws Exception {
        byte[] nonce = new byte[32];
        RANDOM.nextBytes(nonce);
        Offer unsigned = new Offer(role, identity, nonce,
                                   ephemeral.getEncoded(), new byte[0]);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signingKey);
        signer.update(offerBytes(unsigned));
        return new Offer(role, identity, nonce,
                         unsigned.publicKey(), signer.sign());
    }

    static boolean verifyOffer(Offer offer, String requiredRole,
                               String requiredIdentity, PublicKey trustedKey)
            throws Exception {
        if (!requiredRole.equals(offer.role())
                || !requiredIdentity.equals(offer.identity())
                || offer.nonce().length != 32) {
            return false;
        }
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(trustedKey);
        verifier.update(offerBytes(offer));
        return verifier.verify(offer.signature());
    }

    static byte[] transcriptHash(Offer client, Offer server)
            throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        put(out, "handshake-transcript-v1");
        put(out, offerBytes(client));
        put(out, client.signature());
        put(out, offerBytes(server));
        put(out, server.signature());
        return MessageDigest.getInstance("SHA-256")
                .digest(buffer.toByteArray());
    }

    static byte[] agree(PrivateKey mine, byte[] peerPublicKey)
            throws Exception {
        PublicKey other = KeyFactory.getInstance("X25519")
                .generatePublic(new X509EncodedKeySpec(peerPublicKey));
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(mine);
        agreement.doPhase(other, true);
        return agreement.generateSecret();
    }

    // HKDF-Extract, then one-block HKDF-Expand for a 32-byte AES key.
    static byte[] channelKey(byte[] shared, byte[] context)
            throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(context, "HmacSHA256"));
        byte[] prk = mac.doFinal(shared);
        try {
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update("client-to-server AES key v1".getBytes(
                    StandardCharsets.UTF_8));
            mac.update((byte) 1);
            return mac.doFinal();
        } finally {
            Arrays.fill(prk, (byte) 0);
        }
    }

    static byte[] seal(byte[] key, byte[] aad, byte[] iv, byte[] plain)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(plain);
    }

    static byte[] open(byte[] key, byte[] aad, byte[] iv, byte[] encrypted)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(encrypted);
    }

    public static void main(String[] args) throws Exception {
        // The trusted identity public keys were provisioned beforehand.
        KeyPairGenerator identityGenerator =
                KeyPairGenerator.getInstance("Ed25519");
        KeyPair clientIdentity = identityGenerator.generateKeyPair();
        KeyPair serverIdentity = identityGenerator.generateKeyPair();

        KeyPairGenerator ephemeralGenerator =
                KeyPairGenerator.getInstance("X25519");
        KeyPair clientEphemeral = ephemeralGenerator.generateKeyPair();
        KeyPair serverEphemeral = ephemeralGenerator.generateKeyPair();

        Offer client = createOffer("client", "Alice",
                clientEphemeral.getPublic(), clientIdentity.getPrivate());
        Offer server = createOffer("server", "Bob",
                serverEphemeral.getPublic(), serverIdentity.getPrivate());

        boolean clientAuthenticated = verifyOffer(client, "client", "Alice",
                clientIdentity.getPublic());
        boolean serverAuthenticated = verifyOffer(server, "server", "Bob",
                serverIdentity.getPublic());
        System.out.println("trusted client offer: " + clientAuthenticated);
        System.out.println("trusted server offer: " + serverAuthenticated);

        byte[] replaced = client.publicKey().clone();
        replaced[replaced.length - 1] ^= 1;
        Offer tampered = new Offer(client.role(), client.identity(),
                client.nonce(), replaced, client.signature());
        System.out.println("substituted key accepted: " +
                verifyOffer(tampered, "client", "Alice",
                            clientIdentity.getPublic()));

        if (!clientAuthenticated || !serverAuthenticated) {
            throw new SecurityException("Untrusted handshake");
        }

        byte[] context = transcriptHash(client, server);
        byte[] leftShared = agree(clientEphemeral.getPrivate(),
                                  server.publicKey());
        byte[] rightShared = agree(serverEphemeral.getPrivate(),
                                   client.publicKey());
        byte[] leftKey = channelKey(leftShared, context);
        byte[] rightKey = channelKey(rightShared, context);
        System.out.println("derived keys match: " +
                MessageDigest.isEqual(leftKey, rightKey));

        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        byte[] plaintext = "hello Bob".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = seal(leftKey, context, iv, plaintext);
        byte[] opened = open(rightKey, context, iv, ciphertext);
        System.out.println("authenticated message: " +
                new String(opened, StandardCharsets.UTF_8));

        byte[] wrongContext = context.clone();
        wrongContext[0] ^= 1;
        boolean rejected = false;
        try {
            open(rightKey, wrongContext, iv, ciphertext);
        } catch (AEADBadTagException expected) {
            rejected = true;
        }
        System.out.println("changed transcript rejected: " + rejected);

        Arrays.fill(leftShared, (byte) 0);
        Arrays.fill(rightShared, (byte) 0);
        Arrays.fill(leftKey, (byte) 0);
        Arrays.fill(rightKey, (byte) 0);
    }
}
```

Biên dịch và chạy với **JDK 21** (hoặc JDK mới hơn có hỗ trợ `--release 21`):

```text
javac -version
javac --release 21 -encoding UTF-8 AuthenticatedChannelDemo.java
java AuthenticatedChannelDemo
```

Stdout mong đợi (các key và nonce được sinh ngẫu nhiên, không in ra):

```text
trusted client offer: true
trusted server offer: true
substituted key accepted: false
derived keys match: true
authenticated message: hello Bob
changed transcript rejected: true
```

### 5.1 Phân tích từng kết quả

- **Trusted offers:** hai bên giả định đã có **đúng identity public keys** của nhau từ kênh đáng tin; signatures kiểm tra chính xác các bytes offer có role/id/suite/nonce/key share.
- **Substituted key rejected:** đổi một byte public key nhưng giữ signature cũ khiến phép xác minh thất bại; attacker không thể tùy tiện thay exchange key mà không có signing private key.
- **Derived keys match:** `KeyAgreement` tạo shared secret tương thích; HKDF-Extract dùng transcript hash làm public salt, HKDF-Expand một block gồm `info || 0x01` để tạo 32 byte c2s key. Đây là cách dùng **một block của RFC 5869**, không phải implementation HKDF tổng quát cho nhiều block/độ dài.
- **Authenticated message:** AES-GCM dùng **cùng derived key, IV và AAD (transcript hash)** ở hai phía để kiểm tra tag và mở plaintext. Nếu chỉ đo lường kết quả này mà bỏ trust of identity, MITM vẫn có thể tạo kênh hợp lệ với chính attacker.
- **Changed transcript rejected:** đổi AAD nhưng giữ ciphertext/tag làm `AEADBadTagException`. Điều đó minh họa dữ liệu bối cảnh đã được xác thực ở tầng record; nó **không thay** việc xác minh signature/peer trust.

**Giới hạn quan trọng:** demo không chứng minh có một *handshake Finished* qua lại. Kết quả `derived keys match: true` được so trong cùng process, không phải giao thức key confirmation trên mạng. Một hệ thống thật cần mỗi bên gửi và xác minh bằng chứng possession of derived handshake key theo đúng state machine, trước khi tin phiên đã hoàn tất.

### 5.2 Chi tiết liên quan Java và encoding

- `KeyPairGenerator.getInstance("X25519")` và `KeyAgreement.getInstance("X25519")` thực hiện key agreement; `Ed25519` thuộc nhóm ký số, dùng `Signature`. Không đổi `Ed25519` thành `X25519` rồi dùng để ký.
- `ephemeral.getEncoded()` trong ví dụ trả X.509/SubjectPublicKeyInfo encoding của public key; `KeyFactory` phục hồi public key từ `X509EncodedKeySpec`. Đây là **định dạng khóa**, không phải chứng thư X.509 đã được tin cậy.
- `DataOutputStream.writeInt(length)` giúp phân biệt ranh giới các trường. Production phải đặt giới hạn tối đa độ dài, parser chống dữ liệu lỗi, canonical encoding, kiểm tra thuật toán/key type và xử lý mạng an toàn.
- `Signature.verify()` kiểm tra signature với **public key đã biết trước**. Nếu thay trusted public key bằng key do request đưa lên chưa kiểm tra, bài học về MITM quay lại.
- `new SecretKeySpec(context, "HmacSHA256")` dùng transcript hash công khai làm HKDF-Extract salt; secret thật vẫn là `shared`. Với salt không bí mật, HKDF không dựa vào việc che giấu transcript.
- Demo sinh một IV 12 byte cho **một** bản tin. Hệ thống nhiều record cần nonce/counter duy nhất theo key, key/nonce per direction, giới hạn lượng dữ liệu và rotation. Tuyệt đối tránh dùng lại IV AES-GCM với cùng key.
- `Arrays.fill` dọn những mảng byte do ứng dụng sở hữu ở cuối demo. Java provider/JVM có thể tạo bản sao trong bộ nhớ; đây không phải cam kết xóa mọi dấu vết của secret.

## 6. KEM, bọc khóa và thỏa thuận khóa đứng ở đâu trong giao thức?

“Thỏa thuận khóa” và “bọc khóa” đều giúp các bên có key material, nhưng **cấu trúc và giả định khác nhau**.

| Cơ chế | Người gửi thực hiện | Người nhận thực hiện | Thu được |
| --- | --- | --- | --- |
| X25519/ECDH | Dùng private key của mình và public key peer để tính secret | Làm tương tự theo vai trò đối xứng | Shared secret nếu các public keys đúng cặp |
| KEM | `Encapsulate(pk_receiver)` sinh ciphertext KEM và secret | `Decapsulate(sk_receiver, kemCiphertext)` lấy lại secret | Cùng shared secret; chưa tự xác thực sender |
| RSA-OAEP key transport | Mã hóa/wrap một secret hoặc key phiên bằng public key người nhận | Giải mã/unwrap bằng private key người nhận | Key material truyền đến người nhận |
| AES-KW (symmetric key wrap) | Wrap một key bằng **key-encryption key** đã chia sẻ | Unwrap bằng cùng KEK | Key được bảo vệ khi lưu/chuyển, nhưng chính KEK vẫn phải có nguồn đáng tin |
| HKDF | Từ shared secret, transcript/context và labels dẫn xuất các traffic keys | Tính tương ứng với input/context giống nhau | Key phân biệt theo chức năng và hướng |

### 6.1 KEM không tự xác thực danh tính người gửi

Một KEM public-key base mode hướng vào khóa của Bob có thể giữ bí mật encapsulated secret với Bob; **bất kỳ ai** có public key Bob phù hợp cũng có thể encapsulate để gửi Bob. Nếu ứng dụng cần biết sender là Alice, phải có authentication/mode được thiết kế đúng, xác thực public key Bob, transcript/associated data và quản lý giao thức.

**Java 21:** có API `javax.crypto.KEM` (được đưa vào từ Java 21), nhưng loại KEM và provider được hỗ trợ phải được xác minh theo JDK/runtime. Không nhầm sự hiện diện của **lớp KEM** với việc một thuật toán KEM cụ thể **được cài đặt và sẵn dùng**. Đọc tài liệu Java 21 và provider trước khi chọn tên thuật toán.

### 6.2 Key wrapping giải quyết bước vận chuyển/lưu khóa

`Cipher.WRAP_MODE`/`UNWRAP_MODE` là một cách dùng API ở Java; thuật toán wrap cụ thể quyết định định dạng ciphertext, tính toàn vẹn và ràng buộc key. Ví dụ:

```text
Sender: sessionKey = SecureRandom(...)
        wrapped = Wrap(KEK_or_pkRecipient, sessionKey)
        send wrapped + algorithmId + associated metadata

Receiver: sessionKey = Unwrap(keyRecipient, wrapped)
          use sessionKey for records under agreed policy
```

Đây là **pseudocode**, không phải lời mời tự thiết kế định dạng wrap. “Unwrap trả về một SecretKey” không chứng minh sender được phép gửi, key dành cho phiên/ứng dụng này, thuật toán không bị hạ cấp hoặc key chưa từng được replay. Bảo vệ metadata và identity bằng protocol binding hoặc container chuẩn.

Phân biệt **RSA-OAEP bọc secret**, **AES Key Wrap bọc key bằng KEK**, và **AES-GCM mã hóa dữ liệu**: mỗi primitive có giới hạn input, requirement nonce/IV và cách kiểm tra riêng. Đừng dùng raw RSA, đừng tự ghép IV/key/ciphertext không có format được định nghĩa, và đừng dùng bytes secret từ key exchange ở nhiều mục đích mà không qua KDF/key separation.

### 6.3 Vì sao cần hybrid encryption cho dữ liệu lớn?

RSA-OAEP/KEM thường chỉ dùng để truyền hoặc thiết lập **key material**. Một mô hình hybrid:

```text
Shared key or KEM secret → HKDF(context, labels) → traffic key
                                                    |
Plaintext ---------------------------------------> AEAD encrypt
                                                    |
send: key encapsulation/wrapped key + nonce + ciphertext + tag
```

AEAD có thể mã hóa lượng dữ liệu lớn theo giới hạn scheme/nonce/key và policy. Nhưng **hybrid encryption tự nó chưa chắc có sender authentication, replay protection và context binding**. Ví dụ HPKE (RFC 9180) quy định các mode và KDF/AEAD; base mode không xác thực danh tính sender, authenticated mode có giả định khóa và mức bảo vệ riêng. Đọc profile/giao thức để biết phải kiểm tra danh tính và phép gán quyền như thế nào.

## 7. Key confirmation: cùng secret chưa chứng minh cùng một phiên

Để một bên kết luận đối phương **sở hữu khóa vừa dẫn xuất** và chấp nhận đúng transcript, giao thức thường dùng một bước MAC trên transcript hoặc một authenticated handshake record. Ví dụ khái niệm:

```text
clientFinished = HMAC(K_finished_c, transcriptHash)
serverFinished = HMAC(K_finished_s, transcriptHash)

client xác minh serverFinished với key phía server
server xác minh clientFinished với key phía client
chỉ khi hợp lệ mới chuyển state → ESTABLISHED
```

Đây là **sơ đồ**, không phải bản sao chính xác của TLS. Quan trọng là hai hướng phải có labels/keys khác nhau, transcript đúng ranh giới state, và fail-closed khi MAC mismatch. Không dùng “server giải mã được một message bất kỳ” như bằng chứng danh tính nếu traffic key chưa được xác thực từ trước.

### 7.1 TLS 1.3 thực hiện những lớp gì?

Theo [RFC 8446](https://www.rfc-editor.org/rfc/rfc8446), TLS 1.3 có khung giao thức rõ cho phiên bản, key exchange, certificate-based hoặc PSK authentication, transcript hash, HKDF key schedule và **Finished**. Khi xác thực bằng certificate, `CertificateVerify` ký nội dung ràng buộc với transcript; `Finished` MAC nội dung transcript theo khóa bắt tay. Key/nonce và record AEAD được quản lý theo state và direction; lỗi authentication chấm dứt handshake.

**Không đồng nhất demo Java với TLS 1.3.** Một protocol đạt chuẩn còn xử lý certificate path/hostname, algorithms/suites, resumption/PSK, 0-RTT replay caveats, alerts, key update, framing, handshake ordering và nhiều yêu cầu kiểm thử. Nếu chương trình Java cần kết nối TLS, hãy ưu tiên **JSSE** (`SSLContext`, `SSLSocket`/`SSLEngine`, HTTPS client/server) với cấu hình xác thực/tin cậy đúng thay vì xây một TLS giản lược bằng `KeyAgreement` + `Cipher`.

### 7.2 TLS server authentication và client authentication

HTTPS thường xác thực **server** qua certificate/hostname, còn **client** có thể được xác thực ở tầng ứng dụng (OAuth/session/token) hoặc bằng mutual TLS tùy hệ thống. Một handshake “xác thực” không có nghĩa bắt buộc hai đầu đều dùng certificate.

Trong thiết kế, ghi rõ **ai tin ai** và **xác thực chiều nào**. Nếu Bob không cần xác thực Alice bằng certificate, protocol phải quy định cơ chế client identity khác và cách gắn nó với channel/transaction. Đừng tự gán rằng có TLS là mọi request từ Alice đã được phép chuyển tiền.

## 8. Những thuộc tính không thể suy ra từ một primitive

### 8.1 Forward secrecy

Nếu sử dụng ephemeral (EC)DH và xóa bí mật phiên đúng cách, việc private key danh tính bị lộ **về sau** có thể không làm lộ secret của các phiên đã kết thúc (*forward secrecy*, với điều kiện threat model và thiết kế đúng). Trái lại, key transport RSA lưu traffic secret được mã hóa dưới một RSA private key lâu dài có thể khiến các ciphertext cũ bị giải nếu private key đó bị lộ trong tương lai.

**Không tự tuyên bố forward secrecy** chỉ vì tên thuật toán là X25519. Phải xác minh ephemeral key mới cho phiên, không tái sử dụng trái thiết kế, secret được xóa đúng lúc, KDF và protocol binding, và rằng metadata/log/backup không lưu lại secret phiên.

### 8.2 Chống replay và freshness

Signature, MAC và AEAD vẫn hợp lệ với message/ciphertext cũ được gửi lại nguyên vẹn. Nếu server nhận một yêu cầu “thanh toán 100” hai lần, có thể thực thi nghiệp vụ hai lần mặc dù tag hoàn toàn hợp lệ. Giao thức cần nonce/sequence number, timestamp/expiration, **replay state** hoặc chống phát lại thích hợp; tầng nghiệp vụ vẫn cần **idempotency key** cho retry.

Với giao thức stream, sequence number và key epoch thường đi cùng record protection. Với API độc lập, signed nonce + store có TTL hợp lý/atomic insert có thể cần thiết. Đừng tự dùng timestamp mà không kiểm tra trùng request trong cùng cửa sổ.

### 8.3 Unknown-key-share / identity misbinding

Trong *unknown-key-share* (UKS), hai bên có thể tính cùng secret nhưng ít nhất một bên **hiểu sai danh tính của người bên kia**. Đây không đơn thuần là byte secret bị lộ; lỗi do message/identity/context không được bind rõ. Bảo đảm rằng key schedule, signatures/MAC và xác minh public key đều ràng buộc với **role, local peer identity, intended recipient, phiên bản và context phù hợp**.

### 8.4 Downgrade và algorithm confusion

Nếu attacker có thể sửa trường “supported versions” hoặc “selected suite” mà các trường đó không được transcript-authenticated, hai bên có thể bị hạ xuống một thuật toán yếu hơn hoặc bất đồng thuật toán. Luôn có **allowlist** và chính sách tối thiểu; kiểm tra negotiation transcript, không đọc thuật toán do request tự khai rồi dùng tùy ý.

Không dùng cùng key cho nhiều schemes hoặc roles không có phân tách miền; không đổi hướng client→server thành server→client dưới cùng key/counter. Các hàm `Signature`/`Cipher` có thể nhận tham số hợp lệ riêng lẻ nhưng một **cấu hình giao thức** vẫn sai.

### 8.5 AEAD nonce và bản ghi

AES-GCM cần IV/nonce **không bị tái sử dụng với cùng khóa**. Nếu record direction dùng một key chung cho nhiều thông điệp mà hai node cùng chọn nonce trùng, confidentiality/integrity có thể bị phá. Đặc tả phải nêu cách tính nonce từ sequence/epoch, key update, giới hạn lượng record, cơ chế tránh overlap khi concurrent workers và hành vi khi counter cạn.

`AAD` dùng để xác thực metadata không cần mã hóa như version, stream ID, sequence, header. Cả encrypt/decrypt phải đưa **chính xác cùng bytes AAD** vào phép kiểm; metadata nào bị thay mà không nằm trong AAD/tag có thể gây routing/context confusion.

### 8.6 Lỗi, parse và tài nguyên

Không để attacker ép server cấp phát mảng khổng lồ, thực hiện hàng loạt phép ký/giải mã đắt tiền, hoặc rò rỉ khác biệt lỗi cho oracle. Bắt giới hạn kích thước record/trường/chain, timeouts, số handshake đồng thời và rate limit. Trong code Java, xử lý `GeneralSecurityException` có phân loại nội bộ nhưng không phản hồi chi tiết khóa, padding, tag hay certificate cho người gọi bên ngoài.

Nếu bắt tay thất bại ở bất cứ bước authentication, **không khởi tạo session thành công** và không thực hiện side effects. Lỗi phải kết thúc/rollback trạng thái theo quy tắc protocol.

## 9. Kiến trúc thực tế: phân tách primitive, handshake và ứng dụng

Một mô hình các lớp:

```text
Trusted identity / certificate / key registry
                |
                v
ClientHello / ServerHello --version, role, suite, key shares, nonces-->
                |
                v
Handshake transcript + signature / PSK authentication / Finished MAC
                |
                v
X25519 or KEM secret ----- HKDF(context/role/direction) ----+
                                                            |
                         +----------------------------------+
                         |
                    traffic keys + base nonces
                         |
                         v
             AEAD protected records + sequence/epoch
                         |
                         v
      Application identity + authorization + replay/idempotency
```

| Lớp | Điều ứng dụng cần kiểm tra | Lỗi nếu bỏ qua |
| --- | --- | --- |
| Trust/identity | Public key/certificate thực sự của peer mong đợi | MITM dùng khóa giả rồi vẫn ký hợp lệ |
| Handshake negotiation | Version, suite, roles và policy trong transcript đã xác thực | Downgrade, role confusion |
| Key establishment | Shared secret đúng peer, ephemeral và KDF tách mục đích | Key substitution, key reuse, không có forward secrecy kỳ vọng |
| Key confirmation | Cả hai phía kiểm tra kết quả handshake/secret | Chấp nhận phiên chưa hoàn tất hoặc khác transcript |
| Record AEAD | Nonce không trùng, đúng key/direction/AAD/sequence | Lộ dữ liệu, sửa record hoặc phát lại |
| Application | Identity, quyền, request ID, idempotency, audit | Kênh an toàn nhưng cho phép giao dịch sai |

### 9.1 Giao thức bảo mật và nghiệp vụ không cùng phạm vi

Ví dụ một client dùng TLS 1.3 đến server hợp lệ, kênh đã được mã hóa và certificate đúng. Vẫn cần kiểm tra user có được phép thao tác `POST /transfer` hay không; user ID có thể đến từ access token/session, không tự suy ra từ việc kết nối TLS thành công. Một request hợp lệ được retry qua kết nối TLS **mới** cũng có thể làm trùng nghiệp vụ nếu thiếu idempotency.

Đó là lý do review kiến trúc cần câu hỏi: “Giao thức nào bảo vệ **bytes trên kênh**, cơ chế nào xác định **principal**, và thành phần nào quyết định **quyền và tính duy nhất của transaction**?”

## 10. Lựa chọn giải pháp theo mục đích

| Nhu cầu | Lựa chọn thường phù hợp | Điều vẫn phải xác minh |
| --- | --- | --- |
| HTTPS giữa Java service và server | TLS 1.3 qua JSSE/HTTPS stack được cập nhật | Hostname verification, truststore, ALPN/versions/suite policy, client auth nếu cần |
| Java service nội bộ mTLS | TLS 1.3 + certificate client/server | Trust anchors, danh tính workload, policy key rotation/revocation, authorization |
| Mã hóa dữ liệu gửi cho recipient, không duy trì kết nối | Chuẩn hybrid encryption / HPKE profile phù hợp | Recipient public key đáng tin, sender authentication mode, context/AAD, replay |
| Bọc một data-encryption key trong hệ thống KMS | KMS/HSM wrap/unwrap hoặc AES-KW theo chuẩn | Key ID, quyền unwrap, tenant/context binding, rotation, metadata integrity |
| Thiết kế một protocol tùy biến | Bắt đầu từ đặc tả đã review/chuẩn hóa và threat model | State machine, crypto review, vectors, fuzzing, interop, rollback/replay/error paths |
| Chỉ cần học cơ chế | Ví dụ Java 21 trong bài | Chỉ kết luận giới hạn mà demo thật sự kiểm chứng |

**Quy tắc thực dụng:** nếu cần kênh bảo mật thật, sử dụng protocol và thư viện đã được triển khai, kiểm thử, cập nhật. Học các primitives giúp bạn **cấu hình đúng, nhận ra sai kiến trúc và đọc đặc tả**, không có nghĩa bạn nên tự viết handshake thay TLS.

## 11. Checklist kiểm tra một protocol có xác thực

| Thử nghiệm / câu hỏi review | Hành vi phải thấy |
| --- | --- |
| Thay ephemeral public key khi giữ signature cũ | Peer authentication thất bại trước khi gửi traffic |
| Thay public key identity bằng key của attacker | Trust/certificate/pinning từ chối |
| Thay client/server role hoặc peer ID | Signature/transcript authentication thất bại |
| Hạ version, thay cipher suite, bỏ extension cần thiết | Transcript mismatch hoặc policy reject |
| Dùng cùng shared secret dưới hai ngữ cảnh khác | Key derivation có labels/context thích hợp, không trùng key ngoài ý muốn |
| Một bên nhận transcript khác bên kia | Finished/key confirmation không hợp lệ |
| Gửi message record với AAD sai | AEAD tag reject; không chuyển plaintext cho ứng dụng |
| Replay một bản ghi/mệnh lệnh | Sequence/replay/idempotency state chặn hiệu ứng trùng theo chính sách |
| Reset nonce/counter khi reconnect/rotate | Epoch và key schedule ngăn nonce reuse |
| Lộ lâu dài signing private key sau phiên đã đóng | Đánh giá forward secrecy của phiên trước; phải có ephemeral secret lifecycle đúng |
| Gửi input rất lớn / keyShare sai loại / ciphertext lỗi | Parse giới hạn, fail-closed, không tạo oracle hoặc DoS dễ khai thác |
| Khóa certificate dùng sai mục đích hoặc hết hạn | Trust/policy/usage validation reject |
| Server đã xác thực nhưng client thiếu quyền | Application authorization từ chối thao tác |

### 11.1 Các sai lầm phổ biến

1. **“Hai shared secret bằng nhau = đã xác thực peer.”** Sai: DH chỉ biết key math; MITM có thể tạo hai shared secrets riêng.
2. **“Đã mã hóa AES-GCM là không bị MITM.”** Sai nếu session key được thiết lập với attacker; AEAD tag chỉ có ý nghĩa theo key đã tin.
3. **“KEM hoặc RSA-OAEP chứng minh sender.”** Sai với base public-key recipient encryption/encapsulation: ai có recipient public key đều gửi được.
4. **“Đã ký ephemeral key là đủ.”** Chưa đủ nếu public signing key không đáng tin, không bind đúng role/negotiation/identity, hoặc chưa hoàn tất key confirmation.
5. **“HKDF tự xác thực danh tính.”** Sai: KDF nhận key material và context, không biết public key thuộc ai.
6. **“Dùng cùng một traffic key cho mọi hướng vì đã có nonce.”** Dễ sai về separation/nonce collision; giao thức cần phân tách key và counter rõ.
7. **“Wrapped key đã an toàn là packet hoàn chỉnh.”** Metadata, key ID, quyền unwrap, nonce/record và chống phát lại vẫn cần được bảo vệ.
8. **“Chữ ký/AEAD hợp lệ nghĩa là gói mới.”** Replay nguyên gói không thay đổi bytes/tag.
9. **“X.509EncodedKeySpec là certificate được CA ký.”** Sai: đó là định dạng encode public key, không chứng minh danh tính.
10. **“Một chương trình lab mô phỏng 2 endpoint trong cùng JVM đủ chứng minh protocol production.”** Sai: demo thiếu state, network adversary, trust lifecycle và kiểm thử liên thông.

## 12. Bài tập củng cố

1. **Phân tích MITM:** vẽ lại đường đi của `pk_A`, `pk_B` và hai public keys Mallory. Giải thích vì sao Alice và Bob đều có thể gọi `KeyAgreement.generateSecret()` thành công dù không chia sẻ secret với nhau.
2. **Chữ ký:** chạy `UnauthenticatedDhDemo` rồi `AuthenticatedChannelDemo`. Cho biết ở bước nào một key share bị thay bị từ chối, và public key nào phải được tin trước khi kiểm chữ ký.
3. **Transcript:** nếu client ký `pkA` mà bỏ `suite`, attacker có thể khai thác sự thiếu ràng buộc nào? Nếu đổi AAD thành một byte khác, AES-GCM biểu hiện ra sao?
4. **HKDF:** giải thích khác biệt giữa `shared`, `PRK` và `traffic key`; vì sao cần hai labels `client-to-server` và `server-to-client`? Vì sao implementation one-block trong demo chưa phải HKDF tổng quát?
5. **Key wrapping:** mô tả một DEK được bọc bằng RSA-OAEP, AES-KW hoặc KMS key. Nêu chính xác nơi thực hiện authorization, xác minh recipient và bảo vệ metadata.
6. **Key confirmation:** thiết kế *bằng lời* một finished exchange hai chiều có MAC trên transcript, không viết protocol production. Nếu một bên không kiểm MAC phía còn lại thì thuộc tính nào còn thiếu?
7. **Replay:** một thanh toán đã xác thực được gửi lại y hệt qua TLS mới. Vì sao TLS đúng certificate không đủ để tránh charge hai lần? Nêu replay cache/idempotency cần thiết.
8. **Forward secrecy:** so sánh ephemeral X25519 và key transport chỉ dựa private key giải mã dài hạn; điều kiện nào phải đúng để một khóa bị lộ về sau không tiết lộ dữ liệu cũ?
9. **Threat model:** viết danh sách attacker có thể sửa/cắt/delay/reorder những trường nào trong handshake và record; ánh xạ từng sửa đổi tới bước xác minh phát hiện.
10. **Quyết định thiết kế:** dịch vụ Java cần TLS client/server và quyền theo tenant. Nêu vai trò `SSLContext`/truststore, certificate identity, application authorization và cách rotate key mà không mất trạng thái giao dịch.

**Tiêu chí hoàn thành:** từ một sơ đồ giao thức, bạn có thể chỉ ra đâu là primitive tạo secret/bọc khóa, đâu là xác thực danh tính, đâu là ràng buộc transcript và KDF, đâu là record protection/key confirmation, và cơ chế nào chịu trách nhiệm replay/authorization. Nếu một lớp bị bỏ đi, bạn phải mô tả được **điều gì vẫn đúng về toán học nhưng trở nên không an toàn ở cấp giao thức**.

## Tài liệu tham khảo

- [Java SE 21 — `KeyAgreement`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KeyAgreement.html): khởi tạo và tạo shared secret.
- [Java SE 21 — `Signature`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Signature.html): ký và xác minh với Ed25519/RSA-PSS.
- [Java SE 21 — `Cipher`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html): AEAD, `ENCRYPT_MODE`/`DECRYPT_MODE` và key wrapping `WRAP_MODE`/`UNWRAP_MODE`.
- [Java SE 21 — `javax.crypto.KEM`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KEM.html): khái niệm encapsulate/decapsulate và phạm vi API.
- [Java SE 21 — Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html): tên X25519, Ed25519, HmacSHA256 và các transformations.
- [RFC 7748](https://www.rfc-editor.org/rfc/rfc7748): Elliptic Curves for Security, X25519/X448.
- [RFC 5869](https://www.rfc-editor.org/rfc/rfc5869): HKDF-Extract/Expand và labels/context.
- [RFC 8446](https://www.rfc-editor.org/rfc/rfc8446): TLS 1.3 key schedule, CertificateVerify, Finished và transcript binding.
- [RFC 9180](https://www.rfc-editor.org/rfc/rfc9180): Hybrid Public Key Encryption (HPKE), KEM/KDF/AEAD và các chế độ xác thực.
- [Java 21 Secure Socket Extension (JSSE) Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html): TLS trong Java, cấu hình trust, xác thực peer.


</details>

- [Quay lại đầu trang](#back-to-top)
