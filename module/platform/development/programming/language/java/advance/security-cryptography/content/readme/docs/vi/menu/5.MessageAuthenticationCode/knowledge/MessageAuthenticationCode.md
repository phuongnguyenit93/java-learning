<a id="back-to-top"></a>

# 5. Mã xác thực

## Menu
- [5.1 MAC chứng minh điều gì khi hai bên cùng giữ một khóa bí mật](#message-authentication-code-001)
- [5.2 HMAC và khác biệt với băm dữ liệu có nối thêm khóa](#message-authentication-code-002)
- [5.3 Sử dụng Mac với init, update, doFinal và quản lý trạng thái](#message-authentication-code-003)
- [5.4 Sinh, bảo quản và tách khóa HMAC theo mục đích sử dụng](#message-authentication-code-004)
- [5.5 Thống nhất dữ liệu đầu vào, thứ tự trường và định dạng tag giữa hai bên](#message-authentication-code-005)
- [5.6 Kiểm tra tag, dữ liệu bị sửa, sai khóa và giới hạn cắt ngắn tag](#message-authentication-code-006)
- [5.7 Phân biệt xác thực thông điệp với chống phát lại bằng nonce hoặc số thứ tự](#message-authentication-code-007)

## <a id="message-authentication-code-001">5.1 MAC chứng minh điều gì khi hai bên cùng giữ một khóa bí mật</a>

<details>
<summary>Xem chi tiết</summary>

# 5.1 MAC chứng minh điều gì khi hai bên cùng giữ một khóa bí mật

## Mục tiêu học tập

Sau bài này, bạn có thể giải thích chính xác ý nghĩa của một **Message Authentication Code (MAC)** trong hệ thống có hai bên cùng biết khóa bí mật; chỉ ra những giả định phải đúng trước khi tin kết quả xác thực; phân biệt xác thực thông điệp với chứng minh danh tính và chống chối bỏ; triển khai HMAC-SHA-256 trong Java 17+; đồng thời thiết kế quy trình kiểm tra MAC, bảo vệ khóa và chống phát lại ở mức giao thức.

**Câu trả lời cốt lõi:** Khi Bob kiểm tra MAC của thông điệp M bằng khóa bí mật K mà Alice và Bob cùng giữ, kết quả hợp lệ cung cấp **bằng chứng mật mã, trong giới hạn giả định an toàn**, rằng M đã được xác thực bằng K và chưa bị sửa theo cách mà MAC không phát hiện. Bob có thể kết luận **một chủ thể biết K đã tạo tag hợp lệ** (hoặc đã có một sự giả mạo với xác suất rất nhỏ). Nhưng **Bob không thể dùng MAC để chứng minh cho người thứ ba chính Alice đã tạo tag**, vì Bob cũng có thể tự tạo đúng tag bằng cùng K.

Điểm phân biệt đó quyết định cách dùng MAC trong API, webhook, giao tiếp dịch vụ và hệ thống cần truy vết trách nhiệm.

## 1. MAC là gì, các bên chia sẻ điều gì?

Giả sử Alice là dịch vụ gửi yêu cầu và Bob là dịch vụ nhận yêu cầu. Hai bên đã nhận cùng một **khóa bí mật K** qua một quy trình phân phối khóa an toàn. K không được công khai cho người quan sát mạng.

MAC có hai thao tác khái niệm:

- **Tạo tag:** T = MAC(K, M), từ khóa K và chuỗi byte thông điệp M tạo ra mã xác thực T.
- **Xác minh tag:** Verify(K, M, T) trả hợp lệ hoặc không hợp lệ. Với HMAC, bên xác minh tính lại tag dự kiến từ K và M rồi so sánh an toàn với T nhận được.

Luồng điển hình:

~~~text
Alice                                          Bob
Có cùng khóa K                           Có cùng khóa K

M = dữ liệu yêu cầu
T = MAC(K, M)
              M, T  --------------------->
                                         T' = MAC(K, M)
                                         So sánh T' với T
                                         Khớp -> MAC hợp lệ
                                         Sai  -> Từ chối
~~~

**Thông điệp M không nhất thiết được mã hóa bí mật.** Trong sơ đồ này, người nghe lén có thể đọc M nếu không có TLS hay encryption. MAC bổ sung cơ chế kiểm tra tính toàn vẹn và tính xác thực dựa trên quyền sở hữu khóa, chứ không làm dữ liệu trở nên khó đọc.

Một đặc điểm quan trọng: **cùng khóa và cùng chuỗi byte đầu vào** cho cùng tag trong các MAC tất định như HMAC. Nếu Bob tự chạy MAC(K, M), anh ấy sẽ tạo kết quả đúng như Alice, vì hệ thống không có khóa ký riêng của Alice.

## 2. MAC hợp lệ thực sự cho biết điều gì?

### 2.1 Tính toàn vẹn của thông điệp (message integrity)

Nếu M bị sửa thành M' nhưng người sửa không biết K, tag T được tạo cho M thường sẽ **không xác thực được cho M'**. Vì vậy MAC giúp phát hiện sửa đổi trái phép đối với **những byte thực sự nằm trong phần đầu vào được MAC bảo vệ**.

Ví dụ: Alice gửi yêu cầu thanh toán có số tiền 100. Kẻ tấn công thay dữ liệu thành 900 nhưng giữ nguyên tag. Bob tính lại MAC cho thông điệp đã thay đổi; tag cũ không khớp và Bob từ chối.

Tuy nhiên, MAC không tự hiểu các trường nghiệp vụ. Nếu Alice chỉ MAC trường amount nhưng bỏ qua currency, recipient hoặc operation, kẻ tấn công có thể sửa những trường **ngoài phạm vi MAC** mà kết quả kiểm tra vẫn hợp lệ. Việc chọn chính xác dữ liệu được ký xác thực là một phần bắt buộc của thiết kế giao thức.

### 2.2 Tính xác thực dựa trên khóa chung (shared-key message authentication)

Với thuật toán MAC an toàn, K được giữ bí mật và không có lỗ hổng quy trình, đối thủ **không biết K** không thể tự tạo tag hợp lệ cho thông điệp mới với xác suất đáng kể, kể cả khi họ đã quan sát nhiều cặp thông điệp–tag hợp lệ.

Vì vậy kết quả kiểm tra thành công là bằng chứng rằng tag **phù hợp với một bên biết K**, hoặc là kết quả của một sự giả mạo thành công rất khó xảy ra. Đó là ý nghĩa của **message authentication** trong mô hình khóa đối xứng.

Cần nói chính xác:

- Bob biết **K và byte thông điệp M khớp với tag T**.
- Bob có cơ sở tin rằng **một chủ thể nắm K đã tạo hoặc có quyền tạo tag** trong mô hình đe dọa đã xác định.
- Bob **không có bằng chứng mật mã độc lập** xác định chính xác chủ thể nào trong nhóm giữ K đã tạo tag.

Nếu chỉ Alice và Bob giữ K, Bob biết bản thân không tạo tag và K chưa lộ, Bob có thể **suy luận theo ngữ cảnh vận hành** rằng Alice đã gửi. Nhưng đây là suy luận dựa trên các giả định ngoài phép toán MAC; bằng chứng MAC một mình không phân biệt được Alice với Bob.

### 2.3 MAC không tạo bằng chứng không thể chối bỏ (non-repudiation)

Giả sử Bob đem thông điệp “Alice đồng ý chuyển tiền” và tag MAC ra trước một trọng tài. Alice có thể phản biện: “Bob cũng biết K, Bob có thể tự tạo cặp M, T đó”. Vì cả hai cùng có khả năng tính MAC(K, M), trọng tài **không thể chỉ dựa vào MAC mà kết luận Alice đã tạo tag**.

Đây là giới hạn cơ bản của **mật mã đối xứng với khóa chung**, không phải lỗi riêng của HMAC.

Để cần bằng chứng có thể xác minh bằng khóa công khai và liên kết với một người ký, thường dùng **chữ ký số**: chỉ bên giữ private key ký; bên khác có public key để kiểm tra. Chữ ký số cho phép mô hình quy trách nhiệm mạnh hơn, nhưng tính chống chối bỏ thực tế còn phụ thuộc quyền kiểm soát khóa riêng, danh tính gắn với khóa, thời điểm, quy trình cấp chứng chỉ và xử lý sự cố. Không nên hứa rằng bất kỳ chữ ký số nào cũng tự động tạo giá trị pháp lý.

## 3. Những điều MAC không chứng minh

| Khẳng định thường gặp | Có được MAC chứng minh không? | Lý do |
| --- | --- | --- |
| “Thông điệp này khớp với một tag hợp lệ theo khóa K.” | **Có, nếu kiểm tra chính xác.** | MAC kiểm tra sự ràng buộc giữa K, byte thông điệp và tag. |
| “Dữ liệu được bảo vệ không bị thay đổi bởi người không biết K.” | **Có, trong giả định an toàn của MAC.** | Thay byte làm tag cũ không còn hợp lệ với xác suất áp đảo. |
| “Chính Alice, chứ không phải Bob, đã tạo tag.” | **Không.** | Alice và Bob đều biết K, đều có thể tạo tag. |
| “Alice không thể chối đã gửi thông điệp.” | **Không.** | Một bên cùng nắm K có thể tạo bằng chứng giả tương đương. |
| “Nội dung được giữ bí mật.” | **Không.** | MAC không mã hóa plaintext; cần encryption/TLS khi cần bí mật. |
| “Đây là yêu cầu mới, chưa từng gửi.” | **Không.** | Cặp M, T hợp lệ có thể bị phát lại nguyên vẹn. |
| “Dữ liệu được gửi đúng thứ tự và đúng thời điểm.” | **Không.** | MAC không tự có đồng hồ, số thứ tự hoặc trạng thái giao thức. |
| “Người gửi được phép thực hiện nghiệp vụ này.” | **Không.** | Xác thực thông điệp không thay thế authorization, policy và kiểm tra tài nguyên. |
| “Nội dung là thật, đúng nghiệp vụ và không có lỗi.” | **Không.** | Một bên biết K vẫn có thể gửi dữ liệu sai hoặc gian lận. |
| “Khóa chưa bao giờ bị lộ.” | **Không.** | MAC hợp lệ không nói trạng thái bảo mật của K; khóa lộ cho phép giả mạo. |

**Vì vậy, hãy luôn gọi đúng tên bảo đảm:** MAC hỗ trợ toàn vẹn thông điệp và xác thực bên **sở hữu khóa chung**. Nó không tự chứng minh danh tính duy nhất của người tạo, sự mới mẻ của yêu cầu hay tính đúng đắn của nghiệp vụ.

## 4. Điều kiện tiên quyết để tin kết quả MAC

Không nên diễn giải Verify = true độc lập với hệ thống. Ít nhất các điều kiện sau phải được xác minh:

1. **K thực sự bí mật và đủ mạnh.** Khóa cần được tạo từ nguồn ngẫu nhiên mật mã an toàn (hoặc KDF được thiết kế cho mục tiêu phù hợp), không hardcode vào source, không in trong log, không chia sẻ cho nhiều chủ thể vượt phạm vi tin cậy.
2. **Thuật toán và tham số an toàn.** Chọn MAC được nghiên cứu, chẳng hạn HMAC-SHA-256, với tag đủ dài và thuật toán được cố định bởi giao thức; không tạo MAC bằng cách tự nối secret với thông điệp rồi hash.
3. **Hai bên MAC cùng một chuỗi byte.** Charset, thứ tự trường, phép chuẩn hóa, header, đường dẫn, phương thức HTTP và body phải được xác định chính xác. Byte khác làm tag khác.
4. **Đã bảo vệ mọi trường ảnh hưởng đến ý nghĩa yêu cầu.** Ví dụ operation, path, tenant, user, amount, currency, recipient, payload version, request ID và timestamp nếu cần.
5. **Tag được kiểm tra trước khi thực hiện thao tác có tác dụng.** Xác minh thất bại phải từ chối xử lý. Không chấp nhận request rồi mới ghi lỗi MAC.
6. **Đã có cơ chế chống phát lại khi nghiệp vụ yêu cầu.** Một MAC hợp lệ cũ vẫn có thể hợp lệ hôm nay nếu hệ thống không kiểm tra timestamp/nonce/sequence.
7. **Kiểm tra quyền nghiệp vụ độc lập.** Bên sở hữu khóa K có thể được phép gọi API, nhưng vẫn có thể bị cấm chuyển tiền từ tài khoản cụ thể hoặc đọc dữ liệu của tenant khác.
8. **Biết phạm vi các bên đang dùng K.** Nếu nhiều dịch vụ dùng chung một khóa, MAC chỉ chứng minh chủ thể thuộc **nhóm giữ K**, không quy về một dịch vụ riêng.

Trong mô hình bảo mật tiêu chuẩn, một MAC an toàn thường được kỳ vọng chống **giả mạo tag cho thông điệp mới ngay cả khi kẻ tấn công từng thấy các tag hợp lệ** (được gọi là unforgeability under chosen-message attack). Đây không phải lời hứa tuyệt đối với mọi hệ thống: dùng khóa yếu, xử lý sai byte đầu vào hoặc lộ khóa có thể phá toàn bộ mô hình.

## 5. HMAC-SHA-256: MAC thường dùng trong Java

### 5.1 HMAC không phải SHA-256 thuần

**SHA-256** là hàm băm không có khóa: ai biết thông điệp đều có thể tự tính SHA-256(M). Vì vậy gắn một SHA-256 digest công khai vào request **không chứng minh nguồn gốc**; người sửa request chỉ việc tính digest mới.

**HMAC-SHA-256** dùng một **khóa bí mật K** kết hợp với SHA-256 theo cấu trúc HMAC chuyên dụng, nên chỉ các bên có K mới dự kiến tạo được tag hợp lệ cho thông điệp mới. Về trực giác:

~~~text
SHA-256(M)          : ai có M đều tính được.
HMAC-SHA-256(K, M)  : cần có K để tính đúng tag.
~~~

Cấu trúc khái niệm của HMAC:

~~~text
HMAC(K, M) = H( (K' XOR opad) || H( (K' XOR ipad) || M ) )
~~~

Trong đó H là hash bên dưới, K' là khóa được chuẩn bị theo kích thước block, ipad/opad là hai hằng theo định nghĩa HMAC và || là phép nối byte. **Không tự triển khai cấu trúc này** cho ứng dụng: Java đã có Mac của JCA/JCE.

Không thay HMAC bằng các mẫu tự chế như SHA256(K || M). Đặc biệt khi dùng hash cấu trúc Merkle–Damgård, cách nối khóa trước thông điệp có thể chịu tấn công length extension trong những thiết kế phổ biến. HMAC được xây dựng để tránh các sai lầm này.

### 5.2 API Java 17+

Java cung cấp:

- **javax.crypto.Mac**: khởi tạo thuật toán, cấu hình khóa, nhận byte và tạo tag.
- **javax.crypto.KeyGenerator**: sinh khóa cho thuật toán HMAC khi provider hỗ trợ.
- **javax.crypto.SecretKey**: kiểu biểu diễn khóa đối xứng.
- **javax.crypto.spec.SecretKeySpec**: bao bọc key bytes đã có nếu ứng dụng nhận khóa hợp lệ từ hệ thống quản lý khóa.
- **java.security.MessageDigest.isEqual**: so sánh hai mảng byte digest/tag theo cơ chế tránh phụ thuộc vào nội dung, thích hợp hơn phép so sánh thông thường cho tag.

Tên thuật toán chuẩn thường dùng trong JCA là **HmacSHA256**; HMAC-SHA-256 cho tag đầy đủ **32 byte (256 bit)**. Khi truyền dưới dạng text, tag có thể được biểu diễn bằng Base64 hoặc hex. Việc đổi sang Base64/hex chỉ để tương thích giao thức; **khóa K không được xuất ra text hay gửi kèm thông điệp**.

Một đối tượng **Mac có trạng thái và không nên dùng chung đồng thời giữa các luồng** nếu không có cách đồng bộ/quản lý riêng. Mỗi thao tác có thể tạo một instance mới, hoặc dùng cơ chế quản lý instance an toàn cho mỗi thread/request.

## 6. Ví dụ Java 17+: hai bên cùng có khóa, kiểm tra sửa đổi và phát lại

Ví dụ dùng **KeyGenerator** tạo khóa HMAC 256 bit cho một lần chạy; biến sharedKey tượng trưng cho khóa mà Alice và Bob đã trao đổi qua kênh an toàn từ trước. Trong hệ thống thật, cả hai tiến trình phải nhận **cùng khóa một cách an toàn** từ KMS/secret manager hoặc giao thức phân phối khóa; KeyGenerator chạy riêng trên hai tiến trình sẽ sinh **hai khóa khác nhau** và không thể xác thực lẫn nhau.

Tạo tệp **SharedKeyMacDemo.java** (UTF-8):

~~~java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public class SharedKeyMacDemo {
    private static final String ALGORITHM = "HmacSHA256";
    private static final int TAG_BYTES = 32;

    private static byte[] mac(SecretKey key, byte[] message)
            throws GeneralSecurityException {
        Mac hmac = Mac.getInstance(ALGORITHM);
        hmac.init(key);
        return hmac.doFinal(message);
    }

    private static boolean verify(SecretKey key, byte[] message,
                                  byte[] receivedTag)
            throws GeneralSecurityException {
        if (receivedTag == null || receivedTag.length != TAG_BYTES) {
            return false;
        }

        byte[] expectedTag = mac(key, message);
        return MessageDigest.isEqual(expectedTag, receivedTag);
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance(ALGORITHM);
        generator.init(256);
        SecretKey sharedKey = generator.generateKey();

        // Trong ví dụ, cả Alice và Bob dùng cùng sharedKey.
        byte[] original = "amount=100|currency=VND"
                .getBytes(StandardCharsets.UTF_8);
        byte[] modified = "amount=900|currency=VND"
                .getBytes(StandardCharsets.UTF_8);

        // Alice tính tag và gửi original + aliceTag.
        byte[] aliceTag = mac(sharedKey, original);

        // Bob dùng cùng khóa để kiểm tra.
        System.out.println("Bob kiểm tra bản gốc: "
                + verify(sharedKey, original, aliceTag));

        // Người trên mạng thay 100 bằng 900, nhưng không có khóa.
        System.out.println("Bob kiểm tra bản sửa: "
                + verify(sharedKey, modified, aliceTag));

        // Bob cũng có thể tự tạo đúng tag cho cùng byte thông điệp.
        byte[] bobTag = mac(sharedKey, original);
        System.out.println("Bob có thể tạo cùng tag: "
                + MessageDigest.isEqual(aliceTag, bobTag));

        // MAC không lưu trạng thái: phát lại nguyên thông điệp vẫn hợp lệ.
        System.out.println("Phát lại vẫn hợp lệ theo MAC: "
                + verify(sharedKey, original, aliceTag));

        // Tag có thể đóng gói dạng text; không hề chứa khóa K.
        String encodedTag = Base64.getEncoder().encodeToString(aliceTag);
        System.out.println("Độ dài tag byte: " + aliceTag.length);
        System.out.println("Độ dài Base64: " + encodedTag.length());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 SharedKeyMacDemo.java
java SharedKeyMacDemo
~~~

Kết quả thông thường của Java 17+:

~~~text
Bob kiểm tra bản gốc: true
Bob kiểm tra bản sửa: false
Bob có thể tạo cùng tag: true
Phát lại vẫn hợp lệ theo MAC: true
Độ dài tag byte: 32
Độ dài Base64: 44
~~~

Giải thích theo từng kết quả:

1. **Bản gốc hợp lệ:** Bob tính lại HMAC(K, original) bằng cùng K nên tag khớp.
2. **Bản sửa bị từ chối:** đổi một byte có thể làm tag khác; với HMAC-SHA-256 an toàn, xác suất một thông điệp sửa đổi vô tình có cùng tag là cực nhỏ.
3. **Bob tạo được tag giống Alice:** đây chính là lý do **không thể suy ra ai là người tạo tag chỉ từ bằng chứng MAC**.
4. **Phát lại hợp lệ về mặt MAC:** MAC tất định cho cùng K và M không có bộ nhớ về lần đã được sử dụng. Hệ thống cần quy tắc freshness/nonce/sequence bên ngoài.
5. **32 byte và 44 ký tự:** đó là byte tag và cách biểu diễn Base64 có padding; Base64 không phải bằng chứng an toàn hay khóa bí mật.

Ví dụ dùng payload văn bản đơn giản để dễ quan sát, **không phải định dạng request xác thực đủ an toàn cho sản xuất**: còn cần canonicalization, scope, thời gian, nonce, chống phát lại, quyền và chính sách lưu khóa. Chương trình không ghi hoặc hiển thị key bytes.

**Về tính xác thực kết quả:** các dòng true/false dựa trên cùng khóa và byte hoặc byte đã sửa; một trường hợp HMAC va chạm với thông điệp sửa là cực kỳ khó xảy ra về mặt mật mã. Giá trị tag Base64 cụ thể thay đổi giữa các lần chạy vì khóa được tạo ngẫu nhiên, nên ví dụ chỉ in **độ dài** của tag, không khẳng định tag cụ thể.

## 7. HMAC kiểm tra byte, không kiểm tra “ý nghĩa”

### 7.1 Vấn đề ghép trường nhập nhằng

Hãy xem hai bộ trường:

~~~text
Bộ 1: user="ab", action="c"
Bộ 2: user="a",  action="bc"
~~~

Nếu cả hai phía tạo message bằng phép nối thẳng **user + action**, thì hai bộ đều trở thành **abc**. MAC vẫn đúng với byte **abc**, nhưng không phân biệt được hai cách phân tách trường. Đây là lỗi **biểu diễn dữ liệu để xác thực**, không phải lỗi thuật toán HMAC.

Cách phòng tránh:

- Dùng định dạng có **độ dài từng trường**, tag kiểu rõ ràng hoặc serializer có canonicalization và schema được thỏa thuận.
- Khóa chặt **phiên bản giao thức** để hai bên dùng cùng cách diễn giải.
- Bảo vệ **mọi trường có thể đổi ý nghĩa hoặc quyền**: method, path, query, body, tenant, user ID, amount, currency, recipient, timestamp, nonce và operation type khi phù hợp.
- Phân biệt rõ các miền dùng khóa hoặc thông điệp, ví dụ yêu cầu và phản hồi. Khi chia sẻ khóa cho nhiều mục đích, nên dùng **khóa riêng** cho từng mục đích hoặc cơ chế phân tách miền (domain separation) được thiết kế rõ.

Ví dụ cấu trúc đầu vào MAC theo giao thức:

~~~text
version = 1
context = "payment-request"
method = "POST"
path = "/v1/payments"
timestamp = "1730000000"
nonce = "request-unique-id"
bodySha256 = SHA-256(rawBodyBytes)

MAC_input =
    encodeWithLengths(version, context, method, path,
                      timestamp, nonce, bodySha256)
tag = HMAC-SHA-256(K, MAC_input)
~~~

Đây là **mô tả thuật toán đóng gói**, không phải mã Java độc lập và không phải tiêu chuẩn giao thức cụ thể. Hàm encodeWithLengths phải được **định nghĩa một cách duy nhất cho cả hai bên**. Cách lấy path/query, encode Unicode, header, body và timestamp phải được thỏa thuận trước, không tùy ý thay đổi sau khi triển khai.

Nếu MAC dựa trên raw HTTP body, verifier cần xác thực **chính các byte body mà bên gửi đã MAC**. Parse JSON rồi serialize lại tùy thư viện có thể đổi thứ tự key, khoảng trắng hoặc biểu diễn số và gây sai tag. Nếu muốn ký nội dung đã chuẩn hóa, chọn một **quy tắc canonical JSON có đặc tả** dùng nhất quán ở cả hai phía.

### 7.2 Thứ tự xác minh và kiểm soát đầu vào

Một quy trình nhận request hợp lý:

1. Giới hạn kích thước request và đọc các trường metadata tối thiểu cần thiết.
2. Xác định key ID/thuật toán **chỉ từ danh sách được phép**; dữ liệu do client gửi không được ép server dùng thuật toán yếu hoặc khóa tùy ý.
3. Tái tạo chính xác byte được bảo vệ theo phiên bản giao thức và thuật toán đã thống nhất.
4. Tính tag kỳ vọng và so sánh với tag nhận được bằng cơ chế an toàn.
5. Kiểm tra cửa sổ thời gian, nonce/sequence hoặc idempotency theo chính sách chống replay; làm bước ghi nhận nonce **nguyên tử** khi có nhiều instance.
6. Kiểm tra danh tính phía gọi và **phân quyền tài nguyên/nghiệp vụ**.
7. Chỉ sau khi các điều kiện cần thiết đạt mới tiến hành tác dụng nghiệp vụ.

Cách sắp xếp bước 4–6 có thể phụ thuộc kiến trúc (ví dụ cần tra cứu khóa theo tenant trước khi xác minh), nhưng yêu cầu bất biến là **không thực thi tác dụng nhạy cảm trước khi các xác minh bắt buộc thành công**. Trả lỗi có kiểm soát; tránh đưa khóa, tag đầy đủ, thông điệp nhạy cảm hoặc payload gốc vào log.

## 8. MAC hợp lệ vẫn có thể bị phát lại

### 8.1 Replay attack

Giả sử Alice gửi:

~~~text
POST /transfer
amount=100
requestId=R1
tag=HMAC(K, protectedRequestBytes)
~~~

Kẻ tấn công không sửa gì, chỉ **ghi lại rồi gửi lại nguyên request và tag**. Bob vẫn tính đúng tag. Nếu nghiệp vụ chuyển tiền chạy hai lần, MAC đã làm đúng công việc của nó nhưng **giao thức chưa có cơ chế nhận biết yêu cầu cũ**.

Những biện pháp bổ sung thường phối hợp:

- **Timestamp được MAC bảo vệ:** giới hạn tuổi yêu cầu, ví dụ vài phút tùy môi trường và sai lệch đồng hồ cho phép. Timestamp chỉ hữu ích khi server **thực sự kiểm tra giới hạn thời gian**.
- **Nonce hoặc request ID duy nhất được MAC bảo vệ:** server nhớ nonce đã xử lý trong cửa sổ chống replay và từ chối sử dụng lại. Việc ghi nhận phải an toàn giữa nhiều server, tránh hai request chạy song song cùng được chấp nhận.
- **Sequence number:** phù hợp một phiên hoặc kênh có thứ tự; cần trạng thái và xử lý mất gói/khôi phục.
- **Idempotency key và ràng buộc nghiệp vụ:** đặc biệt với thanh toán, lặp lại cùng request ID không được thực hiện giao dịch lần hai. Cần lưu kết quả/khóa idempotency theo chính sách.

**Chỉ đưa timestamp hoặc nonce vào chuỗi MAC mà không kiểm tra tính mới/trùng lặp vẫn chưa chống replay.** Tương tự, MAC thành công không cho biết thông điệp đã được xử lý trước đó hay chưa.

### 8.2 Chống replay không thể thay quyền hạn

Một request mới, hợp lệ theo K và nonce, vẫn có thể yêu cầu truy cập tài khoản không thuộc về client. MAC bảo vệ nguyên vẹn yêu cầu đó, nhưng **authorization** phải từ chối. Trên API, không được xem “biết khóa” là quyền vô hạn với mọi tài nguyên.

## 9. Vì sao “ai giữ khóa” quyết định khả năng quy trách nhiệm?

### 9.1 Chỉ Alice và Bob giữ K

Bob nhận được tag hợp lệ: trong một quy trình mà Bob chắc chắn anh ấy không tạo request, K không bị lộ và không có bên thứ ba giữ K, Bob có thể dùng tag làm **cơ sở xác thực đối tác trong hệ thống**. Nhưng khi xảy ra tranh chấp, Bob không thể chứng minh cho người ngoài rằng chính Alice tạo tag vì Bob có cùng năng lực tạo bằng chứng.

**Đây là sự khác nhau giữa xác thực vận hành và chứng minh công khai.**

### 9.2 Một nhóm dịch vụ cùng giữ K

Nếu các dịch vụ A, B, C và D cùng dùng K, mọi dịch vụ đều tạo được tag hợp lệ. Nhận một tag chỉ cho phép nói rằng **một người nắm K** đã tạo nó (trong giả định chưa lộ khóa). Không thể quy chính xác cho A mà chỉ dựa trên tag.

Thiết kế tốt hơn khi cần phân biệt dịch vụ: dùng **khóa riêng theo từng client/service hoặc cặp quan hệ tin cậy**, key ID gắn với danh tính và quyền rõ ràng; không cấp khóa của client này cho client khác. Tuy nhiên, máy chủ verifier giữ cùng khóa với một client vẫn có khả năng tạo tag của client đó: MAC hai chiều **vẫn không tạo chống chối bỏ trước bên thứ ba**.

### 9.3 K bị đánh cắp hoặc người giữ khóa bị chiếm quyền

Nếu K bị lộ, kẻ tấn công tạo được MAC hợp lệ và có thể mạo danh bất kỳ chủ thể nào được hệ thống đồng nhất với K. Verifier không phát hiện điều này chỉ bằng phép so sánh tag. Khi có nghi ngờ lộ khóa, cần thu hồi, luân chuyển khóa, hạn chế quyền, rà soát request và sự cố.

Các bản ghi log và audit đáng tin có thể bổ sung điều tra (thời điểm, máy chủ, mTLS identity, request ID, luồng phân quyền), nhưng **log do cùng bên nắm khóa kiểm soát không tự biến MAC thành chứng cứ chống chối bỏ**.

## 10. MAC, hash, chữ ký số, AEAD và TLS: dùng đúng mục đích

| Cơ chế | Khóa và bên kiểm tra | Điều cung cấp chính | Điều không tự cung cấp |
| --- | --- | --- | --- |
| **SHA-256 thuần** | Không có khóa; ai cũng tính được | Digest để kiểm tra trùng/đối chiếu theo ngữ cảnh | Không xác thực nguồn gốc trước đối thủ có thể sửa dữ liệu và tính lại hash |
| **HMAC-SHA-256** | Các bên cùng giữ khóa bí mật | Toàn vẹn và xác thực thông điệp theo khóa chung | Không giữ bí mật, không phân biệt chính xác từng chủ thể giữ khóa, không chống replay |
| **Chữ ký số** (ví dụ Ed25519, RSA-PSS) | Người ký giữ private key, người kiểm tra dùng public key | Xác thực theo private key được gắn với danh tính, xác minh công khai | Không tự giữ bí mật; cần quản lý trust/danh tính/khóa, kiểm tra freshness theo ứng dụng |
| **AEAD** (ví dụ AES-GCM) | Bên mã hóa/giải mã dùng khóa đối xứng | Bí mật dữ liệu và xác thực toàn vẹn của ciphertext/AAD | Không chứng minh bên nào giữ khóa đã tạo bản mã; không tự chống replay |
| **TLS** | Thỏa thuận khóa phiên, xác thực đầu cuối theo cấu hình | Bảo vệ lưu lượng trong từng kết nối TLS | Không bảo vệ tự động dữ liệu sau khi kết thúc TLS ở proxy, log, DB hoặc hệ thống downstream |

Một request qua HTTPS có thể vẫn cần HMAC ứng dụng nếu hệ thống muốn xác thực **payload độc lập với kết nối** giữa các bên/qua nhiều trung gian. Ngược lại, HMAC ứng dụng **không thay thế TLS** khi payload hoặc credential cần giữ bí mật và cần xác thực kênh truyền.

Đối với dữ liệu cần vừa bí mật vừa toàn vẹn, ưu tiên **AEAD với giao thức được thiết kế đúng**. Không tự chế cơ chế phối hợp encryption và MAC tùy tiện hoặc dùng một key cho nhiều mục đích mà không đánh giá an toàn.

## 11. Quản lý khóa chung trong ứng dụng Java

### 11.1 Phân phối và giới hạn phạm vi khóa

Dùng **SecureRandom/KeyGenerator** hoặc hệ thống KMS/HSM phù hợp để tạo key bytes có entropy cao, với chính sách độ dài phù hợp. Cả hai bên cần nhận cùng K thông qua kênh bí mật có xác thực hoặc được cấp quyền sử dụng khóa qua dịch vụ quản lý khóa. Không đưa K vào URL, frontend, repository, artifact, image container, log, trace hay file cấu hình công khai.

Dùng **khóa riêng theo môi trường**, client/service và mục đích. Không tái sử dụng cùng khóa HMAC cho tất cả tenant, tất cả hệ thống và mọi vai trò. Nếu cần dẫn xuất nhiều khóa từ một secret gốc, dùng KDF được thiết kế cho dẫn xuất và ngữ cảnh phân tách khóa, ví dụ HKDF theo kiến trúc phù hợp.

**Key ID** (định danh khóa không bí mật) có thể đi cùng request để verifier chọn đúng khóa. Key ID phải nằm trong vùng kiểm soát của server, gắn với quyền/tenant và không trở thành cơ chế cho phép client tự yêu cầu khóa bất kỳ. Đưa những định danh ảnh hưởng ngữ nghĩa vào nội dung được xác thực khi giao thức yêu cầu để chống nhầm khóa hoặc nhầm ngữ cảnh.

### 11.2 Rotation, thời hạn và audit

- Định kỳ đổi khóa theo chính sách hoặc ngay khi nghi lộ; tránh dùng một khóa vô thời hạn.
- Khi luân chuyển, xác định thời gian cho phép key cũ, liên kết key ID với key hiện hành và key hết hạn.
- Tránh chấp nhận thuật toán yếu chỉ để tương thích ngược; danh sách thuật toán được server kiểm soát.
- Giám sát nhiều lần xác minh thất bại, replay và dùng khóa không đúng quyền. Nhật ký nên chứa **metadata cần thiết, đã lọc dữ liệu nhạy cảm**, không chứa secret hoặc toàn bộ payload nếu không cần.
- Thu hồi quyền của bên đã bị compromise; nếu nhiều bên dùng một K, sự cố của **một bên có thể buộc đổi khóa cho toàn nhóm**.
- Quy trình phục hồi cần đảm bảo hai bên không vô tình dùng key ID giống nhau nhưng trỏ tới key bytes khác nhau.

### 11.3 Thực hành JCA/JCE và so sánh tag

**MessageDigest.isEqual(expectedTag, receivedTag)** phù hợp hơn **Arrays.equals** khi so sánh tag vì không dừng sớm dựa trên vị trí byte đầu tiên sai. Vẫn cần:

1. Kiểm tra độ dài tag đúng chuẩn trước khi so sánh (ví dụ 32 byte nếu giao thức dùng HMAC-SHA-256 đầy đủ).
2. Giới hạn độ dài đầu vào trước khi tính HMAC; việc đọc một payload cực lớn vẫn tiêu tốn CPU/bộ nhớ.
3. Không log tag hoặc payload nhạy cảm trong thông báo lỗi. Tag có thể công khai về mặt thiết kế MAC nhưng request có thể chứa secret, PII và metadata nhạy cảm.
4. Xử lý lỗi giải mã Base64/hex của tag đúng cách; reject chuỗi sai alphabet/độ dài và không tự cắt/sửa dữ liệu.
5. Khởi tạo **Mac với key đúng cho từng request**; không chia sẻ instance mutable không đồng bộ giữa threads.
6. Không chấp nhận các biến thể MAC/tag length tùy ý do client tự công bố nếu không được giao thức thiết kế và server cho phép.

Độ dài tag có thể bị cắt ngắn theo một **giao thức được thiết kế và đánh giá rõ ràng**, nhưng việc tự rút tag thành vài byte hoặc chấp nhận mọi prefix sẽ làm tăng xác suất giả mạo. Với bài học này, ví dụ giữ nguyên 32 byte HMAC-SHA-256.

## 12. Những lỗi phổ biến cần nhận ra

1. **“Tag đúng, vậy chắc Alice tạo.”** Không đúng nếu Bob cũng giữ K; Bob tự tạo được tag.
2. **“MAC là một loại chữ ký số chống chối bỏ.”** Không; MAC là xác thực với khóa bí mật đối xứng, còn chữ ký số có khóa ký riêng và khóa kiểm tra công khai.
3. **“MAC che giấu thông tin.”** Không; muốn bí mật cần TLS/encryption thích hợp.
4. **“MAC đúng nghĩa là không bị replay.”** Không; tag cũ vẫn đúng với byte thông điệp cũ.
5. **“Đã ký amount, không cần ký currency/recipient.”** Sai: trường không được bảo vệ có thể bị sửa.
6. **“Dùng SHA256(secret || message) là HMAC.”** Sai: đó là cấu trúc khác, có thể không an toàn; dùng JCA HmacSHA256.
7. **“Đặt cùng khóa cho mọi service giúp dễ quản lý mà vẫn xác định người gửi.”** Sai: khả năng quy trách nhiệm giảm, vùng ảnh hưởng khi lộ khóa tăng.
8. **“Tag là Base64 thì khó giả mạo vì khó đọc.”** Sai: Base64 chỉ đổi biểu diễn, sức mạnh đến từ HMAC và khóa bí mật.
9. **“Hai bên hiểu JSON giống nhau thì byte MAC chắc chắn giống.”** Sai: thứ tự field, khoảng trắng, encoding, biểu diễn số và chuẩn hóa Unicode có thể khác.
10. **“Dùng Arrays.equals là đủ cho mọi ứng dụng bảo mật.”** Không nên; so sánh tag bằng cơ chế chống phụ thuộc nội dung như MessageDigest.isEqual và kiểm tra độ dài cố định.
11. **“MAC hợp lệ đồng nghĩa user được thực hiện mọi thao tác.”** Sai: authorization phải đánh giá quyền theo ngữ cảnh nghiệp vụ.
12. **“Không cần đổi khóa dù một client dùng K đã bị xâm nhập.”** Sai: mọi chủ thể có K đều có khả năng tạo tag hợp lệ; sự cố phải dẫn đến đánh giá thu hồi/rotation và tác động hệ thống.

## 13. Tình huống phân tích

### Tình huống A — Hai bên tranh chấp người tạo yêu cầu

Alice và Bob dùng K giống nhau. Bob đưa ra một request và tag hợp lệ rồi tuyên bố “Alice chắc chắn tạo request này”. Bằng chứng đó không đủ vì **Bob có thể tự tính MAC(K, request)**. Trong hệ thống nội bộ Bob vẫn có thể dùng MAC để lọc request giả từ người **không biết K**, nhưng không thể nâng nó thành chứng cứ độc lập rằng Alice là tác giả.

### Tình huống B — Webhook thanh toán

Nhà cung cấp gửi webhook kèm HMAC(K, canonicalRequest). Server xác minh thành công rồi xử lý thanh toán. Một kẻ khác ghi lại webhook thật và gửi lại. HMAC vẫn thành công vì cặp request/tag không đổi. Cách sửa là ràng buộc và **kiểm tra** event ID, thời gian, nonce/idempotency, đồng thời bảo vệ các trường này trong MAC input; server xử lý idempotently để tránh phát sinh giao dịch lặp.

### Tình huống C — Hai tenant dùng chung một khóa

Tenant A và tenant B cùng được cấp K. Nếu tenant A tạo tag cho request với tenantId=B, phía nhận có thể thấy MAC hợp lệ. Nếu server không kiểm tra quyền tenant hoặc không có khóa riêng, MAC **không phân biệt được** A/B. Cần khóa theo tenant/client và kiểm tra authorization; ký MAC cả tenantId cũng **không đủ** nếu cả hai vẫn có cùng K và được phép lựa chọn tenantId tùy ý.

### Tình huống D — Proxy biến đổi request

Alice tính tag trên byte JSON gốc; proxy parse rồi xuất lại JSON với thứ tự thuộc tính khác. Bob tính HMAC trên byte mới và thấy sai tag, dù object JSON về mặt logic có thể tương đương. Hãy quy định ký raw bytes đầu vào hoặc một chuẩn canonical được cả hai phía đồng ý; tránh tự loại bỏ những trường hoặc ký tự ảnh hưởng tới ý nghĩa nghiệp vụ.

## 14. Tự kiểm tra kiến thức

**Câu 1.** Nếu Bob xác minh HMAC(K, M) thành công, điều gì được bảo đảm trong mô hình K bí mật?

**Trả lời:** tag đúng theo K và chính xác những byte M đã được bảo vệ; đối thủ không có K không dễ tạo tag cho dữ liệu mới. Không biết duy nhất người tạo khi nhiều bên có K.

**Câu 2.** Vì sao Bob không thể đem MAC làm bằng chứng pháp lý thuần túy rằng Alice đã ký một thông điệp?

**Trả lời:** Bob cũng biết K nên có thể tạo thông điệp và tag tương tự. Bằng chứng MAC không có tính xác minh công khai phân biệt người tạo.

**Câu 3.** Nếu kẻ tấn công sửa 100 thành 900 trong thông điệp, MAC có phát hiện không?

**Trả lời:** Có, **nếu số tiền nằm trong byte được bảo vệ** và K không bị lộ; xác suất giả mạo thành công với HMAC an toàn là cực nhỏ.

**Câu 4.** Nếu kẻ tấn công phát lại nguyên thông điệp và tag thì sao?

**Trả lời:** MAC vẫn hợp lệ. Muốn từ chối replay, hệ thống phải kiểm tra timestamp, nonce/sequence, idempotency hoặc trạng thái nghiệp vụ.

**Câu 5.** SHA-256(M) khác HMAC-SHA-256(K, M) thế nào?

**Trả lời:** SHA-256 không cần khóa, ai có M cũng tính được; HMAC dùng K bí mật nên cung cấp xác thực theo khóa chung.

**Câu 6.** Gửi tag dưới dạng Base64 có làm MAC an toàn hơn không?

**Trả lời:** Không. Base64 chỉ biểu diễn tag dạng text; an toàn đến từ HMAC, key management, phạm vi dữ liệu được bảo vệ và verification đúng.

**Câu 7.** Nếu Alice, Bob, Carol cùng chia sẻ K, Bob có thể biết chính Alice gửi tag không?

**Trả lời:** Không thể từ MAC đơn lẻ. Tag chỉ phù hợp với việc một bên biết K đã tạo; cần chia khóa theo danh tính và bổ sung cơ chế định danh/xác thực phù hợp.

**Câu 8.** Tag hợp lệ có cho phép truy cập tài khoản của người khác không?

**Trả lời:** Không. Authorization độc lập phải xác minh quyền thao tác trên tài nguyên và ranh giới tenant.

**Câu 9.** Vì sao phải MAC những byte đã chuẩn hóa theo một quy tắc rõ ràng?

**Trả lời:** HMAC chỉ biết byte. Dữ liệu logic giống nhau nhưng serialize khác cho tag khác; phép ghép nhập nhằng còn cho phép những tập field khác nhau dẫn đến cùng byte được ký.

**Câu 10.** Khi nào cân nhắc chữ ký số thay vì MAC?

**Trả lời:** Khi cần người kiểm tra **không giữ quyền tạo chữ ký** và có thể xác minh theo public key của người ký, phục vụ mô hình quy trách nhiệm mạnh hơn; vẫn phải quản lý danh tính và private key đúng cách.

---

**Ghi nhớ:** Trong hệ thống hai bên chia sẻ một khóa bí mật, MAC cho phép xác minh rằng **thông điệp được bảo vệ phù hợp với khóa đó** và làm cho kẻ không có khóa rất khó giả mạo thông điệp mới. Vì **cả bên gửi lẫn bên nhận đều có thể tự tạo cùng tag**, MAC không một mình chứng minh ai là tác giả và không cung cấp chống chối bỏ. Chống replay, bảo vệ bí mật, phân quyền và quản lý khóa phải được thiết kế bổ sung.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-authentication-code-002">5.2 HMAC và khác biệt với băm dữ liệu có nối thêm khóa</a>

<details>
<summary>Xem chi tiết</summary>

# 5.2 HMAC và khác biệt với băm dữ liệu có nối thêm khóa

## Mục tiêu

Sau bài này, bạn có thể giải thích vì sao **hash thông thường** và **mã xác thực thông điệp (MAC)** giải quyết hai bài toán khác nhau; mô tả cấu trúc HMAC và lý do **HMAC-SHA256 khác với `SHA-256(key || message)`**; nhận diện điểm yếu length-extension của cách tự ghép khóa vào dữ liệu; tạo và kiểm tra HMAC bằng Java 17+; kiểm thử implementation bằng test vector chính thức; và biết cách quản lý khóa, định dạng thông điệp, chống phát lại khi ứng dụng vào API.

Chủ đề thuộc **5. Mã xác thực** trong Java Security/JCA. Trọng tâm là HMAC dựa trên hàm băm, nhất là `HmacSHA256`. Bài học không yêu cầu tự viết thuật toán mật mã hoặc xây dựng giao thức chữ ký API đầy đủ.

## 1. Vì sao chỉ băm dữ liệu chưa đủ để xác thực?

Hàm băm mật mã như SHA-256 nhận các byte `M` và tính `H(M)`. Bất kỳ ai biết `M` đều có thể tự tính cùng digest. Vì thế, nếu một bên truyền cả nội dung và digest qua kênh mà đối thủ có thể sửa, đối thủ có thể thay dữ liệu **và tính lại digest**, khiến kiểm tra `SHA-256` vẫn thành công.

Mã xác thực thông điệp (*Message Authentication Code*, MAC) nhận **khóa bí mật K** và **thông điệp M**, tạo **tag T**. Bên kiểm tra dùng cùng khóa để xác nhận tag tương ứng với thông điệp. Khi khóa còn bí mật và cấu trúc MAC an toàn, kẻ không biết khóa rất khó tạo tag hợp lệ cho một thông điệp mới.

| Tiêu chí | `SHA-256(message)` | `SHA-256(key \|\| message)` tự chế | `HMAC-SHA256(key, message)` |
| --- | --- | --- | --- |
| Có khóa bí mật | Không | Có | Có |
| Được thiết kế/chuẩn hóa như một MAC | Không | Không | Có (HMAC) |
| Chống việc bên thứ ba sửa cả nội dung lẫn digest | Không, nếu digest không được xác thực độc lập | Không bảo đảm | Có, với khóa đủ mạnh và triển khai đúng |
| Dễ mắc **length-extension** kiểu SHA-256 | Không phải bài toán MAC | **Có**, với cấu trúc tiền tố `H(K \|\| M)` | Không chịu kiểu tấn công mở rộng độ dài trực tiếp đó |
| Engine Java | `MessageDigest` | `MessageDigest` (dùng sai mục đích xác thực) | `Mac` |
| Tên chuẩn trong Java | `SHA-256` | `SHA-256` | `HmacSHA256` |

**Dùng khóa không tự biến một phép băm thành MAC an toàn.** Công thức `H(K || M)` và `H(M || K)` nhìn đơn giản nhưng không thừa hưởng những bảo đảm an toàn của một cấu trúc MAC đã nghiên cứu. Một số kiểu nối dữ liệu đặc biệt có những tấn công khác nhau; không kết luận mọi kiểu đều chịu chính xác cùng một đòn length-extension.

HMAC bảo vệ **tính toàn vẹn có xác thực và tính xác thực của bên biết khóa**; nó **không mã hóa nội dung**, không ngăn ai đó đọc plaintext, không ngăn bản tin hợp lệ bị phát lại và không tạo tính không thể chối bỏ như chữ ký số bất đối xứng vì cả hai bên cùng biết khóa.

## 2. Cấu trúc HMAC: hai lớp băm có khóa

HMAC được mô tả trong [RFC 2104](https://www.rfc-editor.org/rfc/rfc2104) và được thử nghiệm bằng các vector như [RFC 4231](https://www.rfc-editor.org/rfc/rfc4231). Với hàm băm `H`, ký hiệu `||` là phép **nối chuỗi byte**, `⊕` là XOR từng byte, công thức là:

\[
\mathrm{HMAC}(K,M)=H\big((K_0\oplus\mathrm{opad})\parallel H((K_0\oplus\mathrm{ipad})\parallel M)\big)
\]

Trong đó:

- `K` là khóa bí mật; `M` là dãy byte thông điệp.
- `B` là **block size của hàm băm**, không phải độ dài digest. Với HMAC-SHA256, `B = 64` byte; digest SHA-256 dài **32 byte**.
- Nếu khóa dài hơn `B`, HMAC trước hết băm khóa bằng `H`; sau đó thêm byte `0x00` vào cuối để tạo `K₀` dài đúng `B`. Nếu khóa đã ngắn hơn `B`, chỉ cần đệm zero; khóa đúng `B` không cần đệm.
- `ipad` là `B` byte `0x36`, còn `opad` là `B` byte `0x5c`.
- HMAC tạo một giá trị băm **bên trong** của `(K₀ ⊕ ipad) || M`, rồi băm **bên ngoài** của `(K₀ ⊕ opad) || innerDigest`.

Tóm tắt luồng:

```text
K --[normalize to block size B]--> K0
                 |                    |
                 v                    v
           K0 XOR ipad          K0 XOR opad
                 |                    |
                 v                    |
        hash((K0 XOR ipad) || M)      |
                 |                    |
                 +--------> hash((K0 XOR opad) || innerDigest)
                                        |
                                        v
                                    HMAC tag
```

Đầu ra của HMAC-SHA256 nguyên vẹn là **32 byte = 256 bit**. Độ dài tag không phụ thuộc độ dài thông điệp. Khi cần hiển thị, 32 byte trở thành **64 ký tự hex**; hex là cách mã hóa để lưu hoặc truyền, không phải bản thân tag.

**Tại sao có hai lớp?** HMAC không đưa trực tiếp trạng thái cuối của `H(K || M)` làm tag; giá trị băm bên trong được xử lý qua lớp băm khác với khóa đã biến đổi bởi `opad`. Kẻ chỉ biết tag ngoài không lấy được trạng thái nội bộ `H((K₀ ⊕ ipad) || M)` để thực hiện phép kéo dài thông điệp như với cấu trúc `H(K || M)`. Bảo đảm an toàn của HMAC dựa trên các giả định mật mã về hàm băm và thiết kế HMAC, chứ không chỉ dựa vào việc ghép khóa.

**Không tự hiện thực `ipad/opad` trong production.** Dùng `javax.crypto.Mac` và implementation của provider đã kiểm thử để tránh lỗi xử lý khóa dài, khối đệm, trạng thái hash và byte encoding.

## 3. Rủi ro thực sự khi băm dữ liệu có nối thêm khóa

### 3.1 Kiểu tiền tố: `SHA-256(key || message)`

SHA-256 sử dụng cấu trúc băm lặp với trạng thái có thể tiếp tục xử lý các block bổ sung. Nếu một giao thức ngây thơ định nghĩa:

```text
tag = SHA-256(secretKey || message)
```

và kẻ tấn công biết `message`, `tag` cùng độ dài khóa (hoặc đoán được một số độ dài ứng viên), họ có thể thực hiện **length-extension attack** về mặt mật mã: tính tag cho một chuỗi byte mở rộng tương ứng với

```text
message || gluePadding(secretKey || message) || attackerSuffix
```

mà không cần biết byte của `secretKey`. `gluePadding` là padding SHA-256 phụ thuộc **tổng độ dài byte** của `secretKey || message`, chứa dấu kết thúc và độ dài thông điệp theo đặc tả SHA-256. Việc một payload giả mạo như vậy còn được parser ứng dụng chấp nhận hay không phụ thuộc cách hệ thống diễn giải các byte; nhưng đây đã là lỗi thiết kế cơ bản của MAC tiền tố.

Đặc biệt, cách nối thêm một dấu phân cách cố định như `SHA-256(secretKey || ":" || message)` **không biến nó thành HMAC** và không loại trừ một cách tổng quát tấn công length-extension.

### 3.2 Kiểu hậu tố: `SHA-256(message || key)`

Với kiểu hậu tố, tag quan sát được là trạng thái sau khi băm **cả khóa ở cuối**. Do đó không thể đơn giản áp dụng cùng thao tác kéo dài rồi đặt phần mở rộng vào sau `message` mà vẫn giữ khóa ở cuối. Tuy nhiên, điều này **không chứng minh** công thức là MAC an toàn: các tính chất, rủi ro va chạm và bảo đảm an toàn còn phụ thuộc cấu trúc hash và mô hình tấn công. Không triển khai `hash(message || key)` hoặc biến thể tự chế để thay cho `Mac`.

### 3.3 Tại sao có HMAC nếu SHA-256 vẫn an toàn?

Một hash có tính chất khó tìm va chạm/chống tiền ảnh **không đương nhiên** tạo MAC chống giả mạo khi ghép khóa. HMAC được định nghĩa để giải quyết chính xác bài toán MAC, tránh cấu trúc tiền tố dễ bị length-extension và có phân tích mật mã độc lập. Với ứng dụng Java, lựa chọn mặc định khi cần MAC từ SHA-256 là **`HmacSHA256`**.

## 4. Java 17: HMAC, phép băm nối khóa và kiểm thử chính thức

Java JCA/JCE phân biệt rõ engine:

- `MessageDigest.getInstance("SHA-256")` tạo **hàm băm không khóa**.
- `Mac.getInstance("HmacSHA256")` tạo **HMAC có khóa**; phải khởi tạo bằng `mac.init(key)` rồi mới `update` hoặc `doFinal`.
- `SecretKeySpec(keyBytes, "HmacSHA256")` đóng gói byte khóa phù hợp cho `Mac` trong ví dụ; đây không phải cơ chế sinh hay lưu trữ khóa.
- `HexFormat` (Java 17+) chuyển `byte[]` sang hex và giải mã hex để đối chiếu test vector.

Chương trình bên dưới sử dụng **khóa thử nghiệm công khai của RFC 4231**, chỉ dành cho học tập và kiểm thử. Đừng dùng khóa này trong dịch vụ thật.

Tạo tệp **`HmacVsConcatDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacVsConcatDemo {
    private static final String ALGORITHM = "HmacSHA256";

    public static void main(String[] args) throws Exception {
        byte[] key = new byte[20];
        Arrays.fill(key, (byte) 0x0b);
        byte[] message = "Hi There".getBytes(StandardCharsets.UTF_8);

        byte[] tag = hmac(key, message);
        byte[] rfcExpected = HexFormat.of().parseHex(
                "b0344c61d8db38535ca8afceaf0bf12b"
                        + "881dc200c9833da726e9376c2e32cff7");
        System.out.println("HMAC-SHA256: " + HexFormat.of().formatHex(tag));
        System.out.println("Matches RFC 4231 case 1: "
                + MessageDigest.isEqual(tag, rfcExpected));

        byte[] naive = prefixedSha256(key, message);
        System.out.println("SHA-256(key || message): "
                + HexFormat.of().formatHex(naive));
        System.out.println("HMAC equals naive hash: "
                + MessageDigest.isEqual(tag, naive));

        System.out.println("Verify original: " + verify(key, message, tag));
        System.out.println("Verify modified: " + verify(
                key, "Hi There!".getBytes(StandardCharsets.UTF_8), tag));

        Mac streaming = newMac(key);
        streaming.update("Hi ".getBytes(StandardCharsets.UTF_8));
        streaming.update("There".getBytes(StandardCharsets.UTF_8));
        System.out.println("Streaming equals one-shot: "
                + MessageDigest.isEqual(tag, streaming.doFinal()));

        byte[] key2 = "Jefe".getBytes(StandardCharsets.US_ASCII);
        byte[] message2 = "what do ya want for nothing?"
                .getBytes(StandardCharsets.US_ASCII);
        System.out.println("RFC 4231 case 2: "
                + HexFormat.of().formatHex(hmac(key2, message2)));
    }

    private static Mac newMac(byte[] key) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(key, ALGORITHM));
        return mac;
    }

    private static byte[] hmac(byte[] key, byte[] message)
            throws Exception {
        return newMac(key).doFinal(message);
    }

    private static boolean verify(byte[] key, byte[] message, byte[] suppliedTag)
            throws Exception {
        byte[] expectedTag = hmac(key, message);
        return MessageDigest.isEqual(expectedTag, suppliedTag);
    }

    private static byte[] prefixedSha256(byte[] key, byte[] message)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(key);
        digest.update(message);
        return digest.digest();
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac --release 17 HmacVsConcatDemo.java
java HmacVsConcatDemo
```

**Stdout khi chạy trên JDK có implementation `HmacSHA256` và `SHA-256` đúng chuẩn** (đã đối chiếu khi chạy ví dụ):

```text
HMAC-SHA256: b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7
Matches RFC 4231 case 1: true
SHA-256(key || message): 9000e06f7d262eac9b27728faaaee3a54d901591a9262c1af171f25fe97c7717
HMAC equals naive hash: false
Verify original: true
Verify modified: false
Streaming equals one-shot: true
RFC 4231 case 2: 5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843
```

**Điều rút ra từ kết quả:**

1. Dữ liệu `"Hi There"` với khóa 20 byte `0x0b` cho HMAC-SHA256 trùng **RFC 4231 test case 1**. Tương tự, khóa ASCII `Jefe` và thông điệp ASCII `what do ya want for nothing?` khớp **test case 2**.
2. `SHA-256(key || message)` vẫn cho chuỗi dài 64 ký tự hex nhưng **khác HMAC-SHA256**. Cùng độ dài đầu ra **không** đồng nghĩa cùng thuật toán hoặc cùng tính an toàn.
3. Kiểm tra tag với cùng khóa/dữ liệu cho `true`; đổi **một byte** của thông điệp thành `"Hi There!"` nhưng giữ tag cũ cho `false`.
4. Gọi `Mac.update` với các đoạn `"Hi "` và `"There"` rồi `doFinal()` cho cùng tag với gọi `doFinal(message)` một lần, vì chuỗi byte cuối cùng giống hệt.

`prefixedSha256` chỉ nhằm **minh họa một thiết kế không nên dùng làm MAC**; không sử dụng hàm đó để ký request, phiên đăng nhập hay dữ liệu nhạy cảm.

Trong ví dụ, `newMac`/`hmac` khai báo `throws Exception` để dễ tập trung vào thuật toán. Ở ứng dụng thực, xử lý tường minh các lỗi như `NoSuchAlgorithmException` và `InvalidKeyException` theo chính sách của hệ thống. Khi provider không có `HmacSHA256`, chương trình sẽ không thể chạy đúng phần này; đó là lỗi cấu hình cần giải quyết, không phải tín hiệu để tự fallback về `SHA-256(key || message)`.

## 5. Sử dụng `Mac` đúng trong Java

### 5.1 Khởi tạo và cập nhật theo từng phần

API điển hình:

```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secretKeyBytes, "HmacSHA256"));
mac.update(part1);
mac.update(part2);
byte[] tag = mac.doFinal();
```

Đây là đoạn code **minh họa bên trong một phương thức**: `part1`, `part2` và `secretKeyBytes` là các `byte[]` đã được cung cấp, phương thức phải xử lý ngoại lệ bảo mật, và cần import `javax.crypto.Mac` cùng `javax.crypto.spec.SecretKeySpec`. `doFinal()` kết thúc lần tính toán và đưa đối tượng về trạng thái có thể dùng lại với khóa đã khởi tạo. Đừng gọi `doFinal()` giữa hai đoạn của **cùng một thông điệp**, vì như vậy bạn sẽ tính hai tag riêng. Không chia sẻ cùng một `Mac` đang được `update` giữa nhiều luồng: nó là engine có trạng thái; dùng instance riêng cho từng thao tác khi cần đồng thời.

Streaming giúp xử lý request body/tệp lớn mà không cần nạp toàn bộ vào bộ nhớ; `update(buffer, 0, count)` phải sử dụng đúng số byte thực đọc trong mỗi vòng. HMAC được tính trên **byte chính xác**, nên việc đổi encoding hoặc thứ tự chunk sẽ thay đổi tag nếu làm thay đổi byte đầu vào.

### 5.2 Sinh và bảo vệ khóa thật

Đừng dùng chuỗi dễ đoán như `"secret123"` hoặc khóa thử nghiệm RFC. Một ví dụ **tạo khóa ngẫu nhiên 32 byte (256 bit)** bằng Java là:

```java
byte[] secretKeyBytes = new byte[32];
new SecureRandom().nextBytes(secretKeyBytes);
```

Đoạn này cần import `java.security.SecureRandom`. Tạo khóa **một lần theo chính sách**, lưu trong secret manager/KMS/HSM hoặc kho khóa được kiểm soát, và phân phối an toàn cho các bên được phép xác thực. Không sinh khóa mới trước **mỗi lần verify**, vì lúc đó không còn cùng khóa với phía tạo tag.

Dùng khóa có entropy phù hợp với nhu cầu bảo mật, ví dụ **ít nhất 256 bit ngẫu nhiên** khi thiết kế mới với HMAC-SHA256. HMAC không đòi hỏi khóa bắt buộc dài 32 byte trong mọi giao thức; độ mạnh phụ thuộc entropy và quản lý khóa. Với HMAC-SHA256, khóa dài hơn block size của SHA-256 (64 byte) sẽ được chuẩn hóa bằng cách băm trước khi sử dụng; kéo dài khóa tùy tiện không tự tăng mức bảo mật.

Trong production: không ghi khóa hoặc toàn bộ tag bí mật vào log; không hardcode khóa trong mã nguồn; kiểm soát quyền truy cập, rotation, phạm vi sử dụng và phiên bản `keyId`. Nên **tách khóa theo mục đích** (ví dụ webhook và session token dùng khóa khác nhau), theo chiều gửi/nhận khi giao thức cần, và có quy trình thu hồi nếu lộ khóa. `SecretKeySpec` chỉ bọc byte khóa trong chương trình; không bảo vệ khóa khỏi người có quyền đọc bộ nhớ.

### 5.3 Xác minh tag an toàn

Bên nhận phải tính lại HMAC từ **đúng byte nội dung nhận được** và khóa phù hợp, rồi so sánh tag dưới dạng byte:

```java
byte[] expectedTag = hmac(secretKeyBytes, receivedMessageBytes);
boolean accepted = MessageDigest.isEqual(expectedTag, receivedTagBytes);
```

Đây là đoạn minh họa trong phạm vi class có phương thức `hmac` từ ví dụ hoàn chỉnh, import `java.security.MessageDigest`. `MessageDigest.isEqual` dùng để so sánh giá trị byte của hai digest/tag và được thiết kế để hạn chế rò rỉ timing so với phép so sánh ngây thơ từng byte; để đạt hành vi thời gian so sánh phù hợp, hãy **kiểm tra định dạng và dùng tag có độ dài cố định theo giao thức**. Với HMAC-SHA256 nguyên vẹn, expected tag dài **32 byte**. Không chấp nhận tag rỗng, độ dài bất kỳ hoặc đối chiếu chỉ vài byte đầu không có thiết kế truncation rõ ràng.

Không dùng:

```java
// SAI: so reference của 2 mảng byte, không so nội dung.
boolean equal = expectedTag == receivedTagBytes;
```

Không so hex string bằng logic tự viết có thể dừng ở ký tự đầu khác nhau rồi cố diễn giải là “constant time”; chuyển tag được truyền (ví dụ hex/Base64) về byte đúng định dạng, xác nhận độ dài theo giao thức và so byte. Nếu giao thức cho phép **tag cắt ngắn**, độ dài truncation và chính sách an toàn phải được xác định trước, thống nhất hai phía và không phụ thuộc ý muốn của người gửi.

**Đừng trả về thông báo chi tiết khác nhau cho “sai tag” và “sai khóa” nếu tạo ra oracle hữu ích cho đối thủ.** Ghi nguyên nhân chi tiết trong kênh logging nội bộ có kiểm soát; API có thể dùng phản hồi lỗi xác thực thống nhất.

## 6. Kiểm tra provider có hỗ trợ HMAC trong môi trường chạy

HMAC trong Java thuộc engine **`Mac`**, không phải engine `MessageDigest`. Hai tên chuẩn có liên quan nhưng không hoán đổi:

- `Mac.getInstance("HmacSHA256")`: tên HMAC SHA-256 của Java.
- `MessageDigest.getInstance("SHA-256")`: tên digest SHA-256, không dùng khóa.
- `Mac.getInstance("SHA-256")`: không phải cách gọi chuẩn để yêu cầu HMAC.

Để khám phá các tên thuật toán `Mac` mà runtime đăng ký, đoạn sau là **một phần của phương thức Java** có import `java.security.Security` và `java.util.Set`:

```java
Set<String> names = Security.getAlgorithms("Mac");
names.stream().sorted(String.CASE_INSENSITIVE_ORDER)
        .forEach(System.out::println);
```

Để tra cứu theo provider cụ thể, đặt đoạn sau trong một phương thức có import `java.security.Provider`, `java.security.Security` và `javax.crypto.Mac`, với xử lý `NoSuchAlgorithmException`:

```java
for (Provider provider : Security.getProviders()) {
    Provider.Service service = provider.getService("Mac", "HmacSHA256");
    if (service != null) {
        System.out.println(provider.getName() + ": " + service.getAlgorithm());
    }
}
```

`provider.getService` cho biết provider **quảng bá** một service trùng engine/tên (hoặc alias). Để biết nó dùng được với khóa thật, tạo `Mac.getInstance("HmacSHA256", provider)`, `init` bằng khóa theo chính sách và kiểm tra `doFinal` bằng vector. `Security.getAlgorithms("Mac")` không chứng minh mọi provider hỗ trợ mọi tên được liệt kê. Đừng hardcode tên `SunJCE` hoặc cho rằng tên/alias provider là đồng nhất trên mọi JDK, FIPS mode, thiết bị hoặc môi trường container.

Các lỗi đáng phân biệt:

| Lỗi/hiện tượng | Ý nghĩa và cách xử lý |
| --- | --- |
| `NoSuchAlgorithmException` từ `Mac.getInstance("HmacSHA256")` | Không có implementation được tìm thấy theo cơ chế chọn provider; xác minh tên chuẩn và provider thực tế |
| `NoSuchAlgorithmException` khi chỉ định provider | Provider được chỉ định không cung cấp tên thuật toán đó; thử kiểm tra `getService("Mac", "HmacSHA256")` rồi xác nhận factory |
| `InvalidKeyException` từ `mac.init(key)` | Khóa sai/không được provider chấp nhận; kiểm tra kiểu khóa, độ dài, policy, quyền HSM/keystore |
| `IllegalStateException` khi `update`/`doFinal` trước `init` | Engine chưa khởi tạo khóa; luôn `init` trước khi xử lý dữ liệu |
| HMAC không khớp dù thuật toán có hỗ trợ | Byte input/charset, phiên bản key, keyId, canonicalization, field order, tag encoding hoặc tag truncation khác nhau |
| Cùng tag cũ vẫn được dùng lại để gọi API | HMAC không tự chống replay; cần đưa thời gian/nonce/counter vào dữ liệu được xác thực và kiểm tra việc sử dụng lại |

Khi HMAC là yêu cầu bắt buộc của giao thức, **fail fast** nếu provider/khóa không sẵn sàng; không tự chuyển sang `SHA-256(key || message)` hay chọn một thuật toán MAC khác mà hai phía chưa thương lượng rõ ràng.

## 7. Thiết kế thông điệp được HMAC bảo vệ

Một tag chỉ có nghĩa khi hai phía thống nhất **chính xác byte nào được ký** và cách diễn giải của chúng. Những lỗi ứng dụng có thể làm hệ thống yếu đi dù `Mac` tính đúng.

### 7.1 Không nối nhiều trường mơ hồ

Nếu ký `HMAC(K, fieldA || fieldB)` mà không mã hóa ranh giới, cặp `("ab", "c")` và `("a", "bc")` cùng tạo byte `"abc"`, khiến tag hợp lệ cho một tổ hợp cũng hợp lệ cho tổ hợp khác. Dùng định dạng binary có **độ dài từng trường**, serialization chuẩn hóa, hoặc encoding đơn nghĩa có cấu trúc rõ ràng. Đừng ghép các chuỗi bằng một dấu phân cách mà input có thể chứa nếu chưa có cơ chế escape chính xác.

### 7.2 Bao phủ đủ ngữ cảnh cần xác thực

Nếu tag dùng để xác thực một request API, giao thức có thể cần bao phủ `method`, `path`, một tập header đã định nghĩa, timestamp, nonce/counter, phiên bản giao thức và **byte chính xác của body** (hoặc digest của body với thuật toán được quy định) trong một encoding không mơ hồ. Nếu chỉ ký body nhưng không ký phương thức hoặc đích đến, tag hợp lệ có thể bị dùng sai ngữ cảnh.

Giao thức cũng phải quy định cách xử lý query encoding, đường dẫn đã normalize, Unicode, JSON và line endings. Bên gửi và bên nhận phải tính HMAC trên **cùng một dãy byte**, không phụ thuộc thứ tự trường do thư viện serialize tùy ý.

### 7.3 HMAC không tự ngăn replay

Đối thủ có thể ghi lại cả thông điệp **và tag đúng**, rồi gửi lại nguyên cặp. Kiểm tra HMAC vẫn cho `true`. Để hạn chế replay, xác thực timestamp hoặc nonce/sequence **nằm bên trong dữ liệu được HMAC bảo vệ**, áp dụng giới hạn thời gian, đồng hồ phù hợp và **lưu trạng thái các nonce/counter đã sử dụng** trong cửa sổ xác định nếu cần từ chối request trùng.

### 7.4 Quy tắc xác thực và điều hành khóa

Bên nhận chọn đúng khóa theo `keyId`/phiên bản được cấu hình, không theo khóa do bên gửi tự cung cấp. Tránh cho người gửi tùy chọn thuật toán yếu; dùng danh sách thuật toán cho phép và xác thực cả ngữ cảnh thuật toán/phiên bản nếu giao thức có nhiều lựa chọn. Rotation cần một khoảng hỗ trợ có kiểm soát đối với khóa cũ, cùng cơ chế revoke khi bị lộ.

Trong một hệ thống có nhiều phía cùng biết khóa, HMAC xác nhận rằng tag được tạo **bởi ai đó biết khóa**, không chứng minh duy nhất một cá nhân đã ký; vì vậy nó không thay được chữ ký số cho yêu cầu non-repudiation.

## 8. Lỗi phổ biến cần tránh

| Cách làm sai hoặc ngộ nhận | Cách đúng |
| --- | --- |
| `SHA-256(K \|\| M)` “cũng là HMAC vì có key” | Dùng `Mac` với `HmacSHA256`; tránh length-extension của tiền tố keyed hash |
| `SHA-256(M \|\| K)` “chắc chắn an toàn vì không bị length-extension như tiền tố” | Không suy từ một kiểu tấn công sang chứng minh an toàn; dùng MAC đã chuẩn hóa |
| So `HMAC-SHA256` với output của `SHA-256(K \|\| M)` và cho rằng lỗi Java | Đó là hai công thức khác nhau; HMAC có inner/outer pad và hai lần hash |
| Đưa Java `String.getBytes()` không chỉ định charset | Dùng `StandardCharsets.UTF_8` và xác định schema byte ở hai phía |
| Dùng `new String(tagBytes)` để lưu tag | Dùng hex/Base64 với encoding chuẩn; giữ tag dưới dạng byte khi so |
| Dùng `==` so hai `byte[]` | So nội dung bằng `MessageDigest.isEqual`, xác thực format/độ dài tag |
| Tạo khóa từ mật khẩu dễ đoán rồi làm HMAC | Dùng khóa ngẫu nhiên đủ entropy và quản lý khóa; nếu đầu vào là password, cần KDF theo thiết kế riêng |
| Thấy HMAC hợp lệ rồi cho phép lặp giao dịch | Ràng buộc và kiểm tra nonce/timestamp/counter; HMAC không tự chống replay |
| Chia sẻ một `Mac` instance mutable qua nhiều request đồng thời | Tạo instance mới hoặc tổ chức state riêng cho từng thao tác |
| Log khóa, dùng khóa RFC trong production | Dùng secret manager/KMS/HSM, kiểm soát quyền và rotation |
| Dùng HMAC làm cơ chế lưu mật khẩu | HMAC là MAC cho thông điệp; lưu mật khẩu cần hàm password hashing phù hợp như Argon2id/scrypt/bcrypt/PBKDF2 theo hệ thống |

## 9. Tự kiểm tra

1. Từ cấu trúc SHA-256 tiền tố `H(K || M)`, giải thích vì sao một đối thủ biết tag và độ dài khóa có thể tính tiếp digest cho dữ liệu được mở rộng, trong khi HMAC không lộ trực tiếp trạng thái hash bên trong.
2. Chạy `HmacVsConcatDemo`. Tại sao output của `prefixedSha256` và `hmac` cùng dài 32 byte nhưng khác nhau? Hai test vector RFC 4231 giúp bắt lỗi gì?
3. Đổi chỉ một byte của thông điệp `"Hi There"` rồi giữ tag cũ. Tại sao `verify` trả `false`? Nếu thay **cả khóa lẫn tag**, hai phía sẽ xảy ra điều gì?
4. Giải thích ví dụ `("ab", "c")` và `("a", "bc")`. Đề xuất một format encoding rõ ràng để HMAC hai trường có ranh giới không mơ hồ.
5. Thiết kế quy trình xác thực webhook bằng HMAC-SHA256: trường nào cần được bao phủ, cách quản lý `keyId`, giới hạn timestamp, theo dõi nonce và xử lý rotation?
6. Thử kiểm tra danh sách provider trên môi trường Java của bạn; phân biệt “provider quảng bá `Mac.HmacSHA256`” với “`Mac` khởi tạo và cho đúng test vector”.

## Tài liệu tham khảo

- [RFC 2104 — HMAC: Keyed-Hashing for Message Authentication](https://www.rfc-editor.org/rfc/rfc2104): công thức HMAC, xử lý khóa, `ipad` và `opad`.
- [RFC 4231 — Identifiers and Test Vectors for HMAC-SHA-224, HMAC-SHA-256, HMAC-SHA-384, and HMAC-SHA-512](https://www.rfc-editor.org/rfc/rfc4231): test vector HMAC-SHA256 case 1 và 2.
- [Java SE 17 — `Mac`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Mac.html): engine MAC, `init`, `update`, `doFinal`.
- [Java SE 17 — `SecretKeySpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/SecretKeySpec.html): bao gói khóa byte cho `Mac`.
- [Java SE 17 — `MessageDigest`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html): hash SHA-256 và so sánh byte bằng `isEqual`.
- [Java SE 17 — Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): chuẩn hóa tên `HmacSHA256` và `SHA-256`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-authentication-code-003">5.3 Sử dụng Mac với init, update, doFinal và quản lý trạng thái</a>

<details>
<summary>Xem chi tiết</summary>

# 5.3 Sử dụng Mac với init, update, doFinal và quản lý trạng thái

Trong Java Cryptography Architecture (JCA), lớp `javax.crypto.Mac` tính **mã xác thực thông điệp** (*Message Authentication Code*, MAC): một giá trị ngắn được tính từ **khóa bí mật** và **đúng dãy byte của thông điệp**. Khi bên nhận có khóa tương ứng, tính lại MAC và so sánh đúng cách, họ có thể phát hiện thông điệp bị thay đổi hoặc được tạo bởi một bên không sở hữu khóa theo giả định an toàn của thuật toán.

Bài này tập trung vào **vòng đời và trạng thái** của `Mac` trong Java: `getInstance` → `init` → `update` → `doFinal`, khi nào dùng `reset`, cách xử lý nhiều thông điệp, cách cho dữ liệu đi theo từng khối, và cách xác minh MAC. Hiểu đúng trạng thái là điều thiết yếu: cùng một đối tượng `Mac` có thể được tái sử dụng tuần tự **sau khi hoàn tất và reset đúng cách**, nhưng rất dễ tạo MAC sai nếu lẫn byte giữa hai thông điệp hoặc chia sẻ đối tượng cho nhiều luồng.

## 1. Kết quả học tập

Sau bài này, bạn có thể:

1. Giải thích MAC bảo vệ **tính toàn vẹn và xác thực dựa trên khóa chung**, khác với hash thuần, chữ ký số và mã hóa dữ liệu.
2. Khởi tạo `Mac` với thuật toán HMAC như `HmacSHA256`, khóa `SecretKey` phù hợp và một giao thức xác định.
3. Dùng `update(byte)`, `update(byte[])`, `update(byte[], offset, len)` và `update(ByteBuffer)` để cấp dữ liệu theo từng khối.
4. Phân biệt `doFinal()`, `doFinal(byte[])`, `doFinal(byte[], int)` và hành vi **reset tự động** sau khi hoàn tất.
5. Biết `reset()` loại bỏ dữ liệu **đã update nhưng chưa final**, trong khi `init(...)` thiết lập khóa/tham số và bắt đầu trạng thái mới.
6. Tránh lỗi tái sử dụng `Mac` không an toàn giữa các request/luồng, xử lý exception và phân tách thông điệp.
7. Xác minh tag bằng tính lại MAC trên đúng byte và sử dụng phép so sánh phù hợp với ngữ cảnh bảo mật; tách MAC khỏi chống replay.
8. Kiểm thử một HMAC-SHA-256 với test vector chuẩn, rồi áp dụng vào xử lý file/API trong hệ thống Java.

## 2. MAC, HMAC, hash và chữ ký số

Ký hiệu khái quát:

```text
tag = MAC(secretKey, messageBytes)

sender:   messageBytes + tag
receiver: recomputedTag = MAC(secretKey, receivedMessageBytes)
          -> xác minh tag nhận được với recomputedTag
```

MAC là kết quả của phép tính **có khóa**, khác với `MessageDigest` không cần khóa. HMAC là một cách xây MAC dựa trên hàm băm được chuẩn hóa; `HmacSHA256` sử dụng SHA-256 bên trong cấu trúc HMAC. SHA-256 một mình **không thay thế HMAC** cho xác thực thông điệp và không nên tự thiết kế MAC bằng cách ghép `SHA256(key || message)`.

| Công cụ | Có khóa bí mật? | Mục tiêu | Giới hạn đáng nhớ |
| --- | --- | --- | --- |
| `MessageDigest` / SHA-256 | Không | Tạo digest của dãy byte | Ai cũng tính được digest mới nếu sửa dữ liệu; không xác thực người gửi |
| `Mac` / HMAC-SHA-256 | Có, khóa dùng chung | Phát hiện sửa đổi và xác thực thông điệp giữa các bên có khóa | Hai bên cùng có khóa đều tạo được MAC; không cung cấp tính bí mật hay bằng chứng chống chối bỏ như chữ ký bất đối xứng |
| `Signature` (ví dụ Ed25519) | Khóa riêng để ký, khóa công khai để kiểm | Chữ ký số có thể kiểm bằng khóa công khai | Yêu cầu quản lý cặp khóa và xác thực khóa công khai |
| `Cipher` / AEAD (ví dụ AES-GCM) | Có | Bảo mật và/hoặc xác thực dữ liệu theo cơ chế mã hóa xác thực | Phải quản lý khóa, nonce, AAD và tag đúng cách |

**MAC không mã hóa dữ liệu.** Thông điệp có thể vẫn đọc được khi truyền; dùng TLS hoặc mã hóa thích hợp nếu cần tính bí mật. MAC cũng **không tự ngăn phát lại**: kẻ tấn công có thể gửi lại nguyên thông điệp và tag hợp lệ nếu giao thức không có nonce, counter, thời điểm hoặc cơ chế idempotency và xác minh vòng đời.

Bởi vì hai bên đều giữ khóa HMAC, một MAC hợp lệ chứng tỏ dữ liệu đến từ **một bên có khóa** (trong giả định thuật toán và triển khai an toàn), không chứng minh duy nhất cá nhân nào đã gửi khi nhiều thành phần chia sẻ khóa. Để lập luận về quyền thực hiện nghiệp vụ, vẫn cần danh tính, authorization, phân tách khóa và audit theo ngữ cảnh.

## 3. Kiến trúc API Java: Mac là một đối tượng có trạng thái

Các API liên quan nằm trong JCA/JCE:

```java
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
```

Đây là tập import minh họa, không phải chương trình độc lập. Tên thuật toán thông dụng: `HmacSHA256`; có `HmacSHA384`, `HmacSHA512` và các thuật toán khác tùy provider. **Không dùng tên thuật toán do client tự chọn** mà không kiểm soát, và không dùng HMAC-SHA1 cho thiết kế mới nếu không có yêu cầu tương thích đã được đánh giá.

Chu trình logic:

```text
                  Mac.getInstance("HmacSHA256")
                               |
                               v
                    [Chưa khởi tạo với khóa]
                               |
                         init(secretKey)
                               |
                               v
                    [Đã khởi tạo, rỗng]
                       /             \
                 update(...)       doFinal(input)
                     |                   |
                     v                   |
                  [Đang tích lũy byte]   |
                   /           \         |
             update(...)     doFinal()    |
                 |              |         |
                 v              v         v
            [Tích lũy]       [Sinh tag, reset]
                                 |
                                 v
                         [Đã khởi tạo, rỗng]
```

Mô hình này mô tả **nội dung logic mà đối tượng đã xử lý**, không tiết lộ các buffer nội bộ của provider. MAC có thể tính dần theo từng khối; chương trình không cần tạo một mảng byte chứa toàn bộ file lớn.

### Phân biệt từng phương thức

| Phương thức | Tác dụng | Trạng thái sau khi gọi |
| --- | --- | --- |
| `Mac.getInstance("HmacSHA256")` | Lấy implementation từ provider | Có đối tượng nhưng **chưa dùng được để update/final nếu chưa init** |
| `mac.init(key)` | Cấp khóa và thiết lập trạng thái tính MAC | Đã init, chưa có byte thông điệp; dữ liệu pending trước đó bị loại bỏ khi khởi tạo lại thành công |
| `mac.init(key, params)` | Khởi tạo với tham số thuật toán khi được hỗ trợ | Trạng thái mới theo khóa/tham số; HMAC thông thường không cần tham số riêng |
| `mac.update(byte)` | Đưa một byte vào thông điệp hiện tại | Đang tích lũy dữ liệu |
| `mac.update(byte[])` | Đưa toàn bộ mảng byte vào thông điệp hiện tại | Đang tích lũy dữ liệu |
| `mac.update(data, offset, len)` | Đưa đúng đoạn byte vào thông điệp hiện tại | Đang tích lũy dữ liệu; tránh phải tạo array con |
| `mac.update(ByteBuffer)` | Đưa các byte từ vị trí hiện tại đến limit của buffer | Vị trí buffer tiến đến limit, MAC tích lũy phần dữ liệu đó |
| `mac.doFinal()` | Tính tag của **tất cả byte đã update** | Tự reset về trạng thái sau lần `init` gần nhất |
| `mac.doFinal(input)` | Update thêm **toàn bộ input**, final, trả tag | Tự reset về trạng thái sau `init` |
| `mac.doFinal(output, offset)` | Ghi tag vào mảng đầu ra từ offset; không trả mảng tag | Tự reset về trạng thái sau `init`; output phải đủ `getMacLength()` byte |
| `mac.reset()` | Hủy dữ liệu pending đang update | Đã init, rỗng, **giữ cùng khóa/tham số** |
| `mac.getMacLength()` | Lấy độ dài tag tính theo **byte** | Không thay đổi dữ liệu thông điệp |
| `mac.getAlgorithm()` | Trả tên thuật toán đối tượng đang sử dụng | Không thay đổi dữ liệu thông điệp |

**Hai hành vi cần nhớ nhất:**

- `doFinal()` và các biến thể sau khi hoàn tất sẽ **tự reset** đối tượng để có thể tính thông điệp mới với khóa/tham số đã dùng ở `init`. Không cần `init` lại với cùng khóa sau mỗi `doFinal` chỉ để reset, dù trong code xử lý request tách biệt thường dễ kiểm soát hơn nếu tạo instance mới.
- `reset()` **không trả về tag**, chỉ bỏ phần dữ liệu đã `update`. Gọi `reset` trước `doFinal` làm mất dữ liệu pending. `reset` không đổi khóa và không phải thao tác xóa khóa khỏi bộ nhớ.

Gọi `update` hoặc `doFinal` khi đối tượng chưa được khởi tạo có thể gây `IllegalStateException`. `init` với khóa sai loại/không được hỗ trợ có thể ném `InvalidKeyException`; việc dùng `init(key, params)` có thể liên quan `InvalidAlgorithmParameterException`. Với output buffer thiếu dung lượng, `doFinal(output, offset)` có thể ném `ShortBufferException`. Nên kiểm tra và xử lý lỗi tại ranh giới ứng dụng, không nuốt exception rồi xem như MAC hợp lệ.

## 4. Chuỗi ví dụ nhỏ: hiểu chính xác update và doFinal

Giả sử `mac` đã được khởi tạo bằng HMAC-SHA-256 và khóa hợp lệ.

### 4.1 Nhiều update là một thông điệp ghép nối

```java
mac.update("order=".getBytes(StandardCharsets.UTF_8));
mac.update("123".getBytes(StandardCharsets.UTF_8));
byte[] tag = mac.doFinal();
```

Đây là đoạn mã minh họa (biến `mac` và các import đã có). Tag ở đây tương đương với HMAC của đúng chuỗi byte UTF-8 `order=123`. **Hai lời gọi `update` không tạo hai tag độc lập**; chúng đang tạo một thông điệp duy nhất.

### 4.2 doFinal(input) nối input vào phần đã update

```java
mac.update("A".getBytes(StandardCharsets.UTF_8));
byte[] tag = mac.doFinal("B".getBytes(StandardCharsets.UTF_8));
```

Tag này xác thực `AB`, **không phải chỉ `B`**. Nếu ý định tính MAC chỉ cho `B`, không được giữ `A` pending từ một thao tác trước. Sau `doFinal`, một lần `mac.doFinal("B".getBytes(UTF_8))` mới trên cùng đối tượng đã reset sẽ xử lý riêng `B`.

### 4.3 reset hủy dữ liệu pending

```java
mac.update("draft".getBytes(StandardCharsets.UTF_8));
mac.reset();
byte[] tag = mac.doFinal("final".getBytes(StandardCharsets.UTF_8));
```

Tag cuối là HMAC của `final`. Đây là cách hủy một thao tác chưa hoàn tất trước khi dùng lại **cùng khóa**. Nó không thay thế việc tạo context mới cho request độc lập, cũng không có nghĩa là khóa nhạy cảm đã được xóa khỏi JVM.

### 4.4 init thiết lập một vòng đời mới

```java
mac.init(otherSecretKey);
mac.update(messageBytes);
byte[] tag = mac.doFinal();
```

Đây là đoạn mã minh họa; `otherSecretKey` là `SecretKey` phù hợp thuật toán và `messageBytes` là `byte[]`. Việc `init` lại thay khóa/tham số và bỏ trạng thái xử lý thông điệp cũ; đừng dùng chung một `Mac` rồi đổi khóa liên tục giữa nhiều request đồng thời.

## 5. Ví dụ Java 17+ chạy độc lập, có test vector HMAC-SHA-256

Ví dụ dưới đây dùng **test vector RFC 4231**: key gồm 20 byte `0b`, message ASCII `Hi There`, expected tag là `b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7`.

**Chú ý:** khóa cố định trong ví dụ **chỉ dành cho kiểm thử**. Hệ thống thật cần key bí mật được tạo ngẫu nhiên bằng nguồn phù hợp và được bảo vệ trong secret manager/KMS/HSM hoặc vùng bảo vệ tương đương.

Lưu thành file **`MacLifecycleDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MacLifecycleDemo {
    private static final String ALGORITHM = "HmacSHA256";

    static Mac newMac(byte[] key) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(key, ALGORITHM));
        return mac;
    }

    public static void main(String[] args) throws Exception {
        byte[] testKey = HexFormat.of().parseHex("0b".repeat(20));
        byte[] message = "Hi There".getBytes(StandardCharsets.US_ASCII);
        Mac mac = newMac(testKey);

        mac.update(message, 0, 2);
        ByteBuffer remainder = ByteBuffer.wrap(message, 2, message.length - 2);
        mac.update(remainder);
        byte[] chunksTag = mac.doFinal();

        byte[] oneShotTag = mac.doFinal(message);
        mac.update((byte) 0x01);
        mac.reset();
        byte[] afterResetTag = mac.doFinal(message);

        boolean valid = MessageDigest.isEqual(
                chunksTag, newMac(testKey).doFinal(message));
        boolean changedValid = MessageDigest.isEqual(
                chunksTag,
                newMac(testKey).doFinal("Hi there".getBytes(StandardCharsets.US_ASCII)));

        System.out.println("Algorithm: " + mac.getAlgorithm());
        System.out.println("MAC length: " + mac.getMacLength());
        System.out.println("Tag: " + HexFormat.of().formatHex(chunksTag));
        System.out.println("Buffer consumed: " + (remainder.position() == remainder.limit()));
        System.out.println("Chunks equal one-shot: " + Arrays.equals(chunksTag, oneShotTag));
        System.out.println("Reset discards pending input: " + Arrays.equals(chunksTag, afterResetTag));
        System.out.println("Fresh verifier: " + valid);
        System.out.println("Changed message accepted: " + changedValid);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên (ví dụ `javac --release 17`):

```bash
javac --release 17 MacLifecycleDemo.java
java MacLifecycleDemo
```

**Stdout chính xác:**

```text
Algorithm: HmacSHA256
MAC length: 32
Tag: b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7
Buffer consumed: true
Chunks equal one-shot: true
Reset discards pending input: true
Fresh verifier: true
Changed message accepted: false
```

Giải thích:

1. `newMac` tạo một đối tượng mới và khởi tạo bằng khóa HMAC.
2. `mac.update(message, 0, 2)` nạp `Hi`; `ByteBuffer.wrap(message, 2, message.length - 2)` cung cấp phần còn lại ` There`. `update(ByteBuffer)` xử lý từ `position` đến `limit` và tiến vị trí tới limit.
3. `doFinal()` sinh tag 32 byte đúng RFC 4231. Sau khi final, `Mac` tự reset, nên `doFinal(message)` ngay sau đó tính MAC riêng của đúng `Hi There` và khớp tag cũ.
4. `mac.update((byte) 0x01)` thử đưa một byte khác vào, nhưng `reset()` loại bỏ nó. Vì vậy tag sau reset vẫn khớp.
5. Bên xác minh tạo `Mac` mới với cùng key và tính MAC trên đúng message. Thay `Hi There` thành `Hi there` làm byte thay đổi; xác minh thất bại.
6. `HexFormat` có từ Java 17; `Arrays.equals` ở đây chỉ kiểm tra **tính tương đương trong demo**. Khi xác thực tag, ví dụ sử dụng `MessageDigest.isEqual` với hai dãy byte.

Đây là **test vector công khai**: không nên coi giá trị key hoặc tag ở ví dụ là bí mật thực tế. Khi dùng tag trong API, thường truyền dưới dạng Base64 hoặc hex theo giao thức và decode về byte trước khi kiểm tra.

## 6. Quản lý trạng thái giữa nhiều thông điệp

### 6.1 Một Mac không đồng nghĩa một request

`Mac` có trạng thái mutable. Một đối tượng không được thiết kế để nhiều luồng gọi `update` xen kẽ nhau mà không có đồng bộ hóa. Nếu request A và B cùng dùng một instance chia sẻ, trình tự có thể xảy ra:

```text
A: update(bytesA_1)
B: update(bytesB_1)
A: update(bytesA_2)
B: doFinal()
A: doFinal()
```

Khi đó tag của B có thể bao gồm byte từ A và trạng thái của A đã bị B reset; kết quả phụ thuộc interleaving, tạo lỗi xác thực khó tái hiện. Ngay cả khi code dùng `synchronized` để tránh data race, việc chia một phiên update của A bởi request B cũng không còn đúng ranh giới thông điệp nếu không khóa toàn bộ phiên. Cách triển khai đơn giản và dễ kiểm tra:

- **Tạo một đối tượng `Mac` mới cho mỗi phép tính** hoặc mỗi request; dùng khóa đã được cấp đúng quyền để init.
- Giữ `Mac` **cục bộ trong method** hay phạm vi xử lý tuần tự. Không giữ một `static Mac` để tất cả request tái sử dụng.
- Nếu triển khai pool chỉ vì hiệu năng, phải có ownership rõ ràng, checkout độc quyền, cleanup/reset, cách ly key và xử lý exception; chỉ tối ưu sau đo đạc và review bảo mật.
- `Mac.clone()` có thể được một số provider hỗ trợ, nhưng không phải cơ chế bảo đảm cross-provider, và clone một trạng thái đã update cũng mang theo dữ liệu pending; không dùng clone làm biện pháp thread safety mặc định.

### 6.2 DoFinal và reset theo đơn vị nghiệp vụ

Giả sử hệ thống xử lý hai file hoặc hai webhook:

```java
Mac mac = newMac(key);
byte[] tagA = mac.doFinal(fileA);
byte[] tagB = mac.doFinal(fileB);
```

Đây là đoạn code minh họa (biến `key`, `fileA`, `fileB` thuộc phạm vi method, `newMac` như bài mẫu). Hai tag riêng vì `doFinal` reset. Trái lại:

```java
Mac mac = newMac(key);
mac.update(fileA);
mac.update(fileB);
byte[] combinedTag = mac.doFinal();
```

Tag thứ hai là HMAC của `fileA || fileB` theo thứ tự cấp vào. Điều này có thể đúng nếu hợp đồng quy định một bản tin gồm nhiều khối, nhưng **sai nếu bạn muốn hai file có hai MAC độc lập**.

### 6.3 Xử lý khi thao tác bị lỗi giữa chừng

Nếu đọc file thất bại sau khi `update` một phần dữ liệu, **không** dùng `doFinal` rồi đánh dấu file là đã xác thực; kết quả chỉ thuộc byte đã đọc thành công. Hủy thao tác, bỏ instance, hoặc reset trước khi tính bản tin khác theo quy trình có kiểm soát.

Ví dụ stream bị timeout không làm trạng thái MAC tự biết bản tin “chưa đủ”. MAC không nắm kích thước file, message length hoặc semantic của bản tin nếu ứng dụng không cung cấp. Vì vậy, code nghiệp vụ phải xác nhận **đã nhận đủ toàn bộ byte theo giao thức** trước khi coi tag là MAC của bản tin hoàn chỉnh.

## 7. Xử lý thông điệp lớn bằng update từng khối

Để xác thực file lớn, không cần đọc toàn bộ file vào `byte[]`. Ví dụ sau là **đoạn code minh họa bên trong method**; giả sử đã có `Path path` và `SecretKey key` thích hợp, các import tương ứng và xử lý ngoại lệ:

```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(key);

byte[] buffer = new byte[8192];
try (var input = java.nio.file.Files.newInputStream(path)) {
    int n;
    while ((n = input.read(buffer)) != -1) {
        if (n > 0) {
            mac.update(buffer, 0, n);
        }
    }
}
byte[] tag = mac.doFinal();
```

Điểm cần quan sát:

- `update(buffer, 0, n)` chỉ đưa **n byte thực đọc** vào MAC, không đưa byte còn sót từ vòng trước trong buffer. Dùng `update(buffer)` ở đây là sai nếu lần đọc cuối nhỏ hơn `buffer.length`.
- Mọi vòng lặp cùng đóng góp vào **một thông điệp duy nhất**; chỉ gọi `doFinal` **sau** khi đã nhận hết file.
- Nên có giới hạn dung lượng, quyền đọc file, cơ chế xác định file không bị thay đổi ngoài ý muốn trong lúc đọc, timeout/cancellation, và chính sách khi xảy ra lỗi I/O. Không xác thực một file đang bị thay đổi giữa các bước theo giả định file là bất biến.
- Nếu MAC cũng phải bảo vệ tên file, tenant, content type hoặc metadata, phải đưa những trường đó vào thông điệp MAC theo serialization không mơ hồ; HMAC riêng byte file không tự bảo vệ metadata ở bên ngoài.
- `Files.newInputStream` cung cấp stream; ở API HTTP, luồng bytes ký phải đúng với phiên bản raw/canonical mà cả bên gửi và bên nhận thống nhất, không tự deserialize rồi serialize lại trước khi xác minh.

Ví dụ khi nhận webhook có JSON: bên gửi định nghĩa MAC trên **raw body bytes**, bên nhận cần lấy đúng raw bytes đó để kiểm MAC (và bổ sung các trường khác theo đặc tả chữ ký). Thêm khoảng trắng, đổi thứ tự field hoặc Unicode normalization trước kiểm MAC đều có thể làm tag không khớp.

## 8. Thiết kế key, thuật toán và ngữ cảnh sử dụng

### 8.1 Test key khác key sản xuất

Trong ví dụ học, `"0b".repeat(20)` là key của RFC 4231. Hệ thống sản xuất không lấy key từ ví dụ, password thường hoặc chuỗi cố định trong mã nguồn.

Có thể tạo khóa HMAC-SHA-256 mới bằng `KeyGenerator`:

```java
import java.security.SecureRandom;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256");
generator.init(256, new SecureRandom());
SecretKey secretKey = generator.generateKey();
```

Đây là đoạn code minh họa trong method có xử lý `GeneralSecurityException`. Kích thước 256 bit là lựa chọn phổ biến cho key HMAC-SHA-256 của một triển khai mới, nhưng yêu cầu key/thuật toán phải được ghi trong chính sách và phù hợp provider. Để sử dụng key, gọi `mac.init(secretKey)`. Với key thô lấy từ kho bí mật, có thể dùng `SecretKeySpec(keyBytes, "HmacSHA256")`, nhưng việc lớp này có thể tạo object từ byte **không** chứng minh nguồn key đủ ngẫu nhiên, có phân quyền hay được luân chuyển an toàn.

Quy tắc quản lý khóa:

- Sinh key bằng bộ sinh ngẫu nhiên mật mã phù hợp, hoặc nhận từ KMS/HSM có cơ chế được phê duyệt; hạn chế xuất byte key khi hệ thống quản lý khóa có API ký/MAC chuyên dụng.
- Bảo vệ key ở cấu hình, log, memory dump, backup và quyền của tài khoản dịch vụ; tránh commit hoặc gửi key qua message queue.
- Dùng **key ID/version** để xác định key khi xác minh message cũ. Có kế hoạch rotation, thu hồi, thời gian chấp nhận key cũ và ứng phó khi nghi lộ.
- Tách key theo mục đích/protocol, tenant hoặc trust domain khi yêu cầu cách ly; không dùng cùng key cho HMAC và AES hay nhiều giao thức khác nếu không có thiết kế và key derivation có domain separation rõ ràng.
- Nếu dùng key derivation, dùng KDF chuẩn đã được thiết kế cho mục đích đó, ví dụ HKDF theo tiêu chuẩn áp dụng; không tự ghép key với text rồi hash và gọi là quản lý khóa.
- Hạn chế chủ thể có quyền tạo MAC: bất kỳ ai có key đều có khả năng làm giả tag trong cùng phạm vi key. Việc chỉ đọc được tag không cho phép tự tạo tag hợp lệ nếu HMAC và key an toàn.

### 8.2 Thuật toán nằm trong hợp đồng

`HmacSHA256` tạo tag dài **32 byte** theo HMAC-SHA-256. `mac.getMacLength()` trả độ dài theo byte, không phải số ký tự Base64/hex. Hex của tag 32 byte dài 64 ký tự ASCII; Base64 thông thường biểu diễn cùng tag với độ dài khác.

Không tự đổi `HmacSHA256` sang `HmacSHA512` hoặc thay provider chỉ dựa trên ý thích ở phía receiver: hai bên phải thỏa thuận thuật toán, key, độ dài tag, cách serialization và policy. Khi giao thức có ID thuật toán trên wire, receiver phải **giới hạn danh sách thuật toán được phép** theo cấu hình đáng tin; không nhận mọi thuật toán client đề nghị.

Cắt ngắn tag (*truncation*) là một lựa chọn giao thức phải được phân tích, kiểm thử và chỉ định chính xác độ dài/thuật toán, không phải phép “lấy vài ký tự đầu cho gọn”. Bài này dùng **toàn bộ 32 byte tag**.

## 9. Cách xác minh MAC an toàn

Cách xác minh điển hình:

1. Nhận dãy byte message theo định dạng được công bố. Phân biệt **raw bytes** với dữ liệu đã parse/canonicalize.
2. Đọc key theo key ID/context đã được kiểm soát bởi server, không để request chỉ định key thô.
3. Tạo `Mac` mới, gọi `init` và tính `doFinal(messageBytes)` hoặc streaming với `update` rồi `doFinal`.
4. Decode tag đính kèm (ví dụ từ Base64) về byte; kiểm tra encoding hợp lệ và độ dài theo giao thức.
5. Dùng `MessageDigest.isEqual(calculatedTag, receivedTag)` để so sánh hai dãy tag; xử lý lỗi xác minh bằng phản hồi thống nhất theo chính sách.
6. Sau xác thực, vẫn kiểm tra **chống replay, quyền truy cập, thời hạn thông điệp và ràng buộc với tài nguyên/hành động**.

Ví dụ trong ngữ cảnh method đã có `key`, `messageBytes` và `receivedTag`:

```java
Mac verifier = Mac.getInstance("HmacSHA256");
verifier.init(key);
byte[] calculated = verifier.doFinal(messageBytes);

boolean accepted = receivedTag != null
        && receivedTag.length == verifier.getMacLength()
        && MessageDigest.isEqual(calculated, receivedTag);
```

Đây là đoạn code minh họa, không phải chương trình độc lập. Kiểm độ dài giúp từ chối dữ liệu sai giao thức; `MessageDigest.isEqual` là công cụ Java phù hợp hơn `Arrays.equals` khi so sánh tag bảo mật. Tuy nhiên, **không tự khẳng định toàn endpoint chạy constant-time**: chọn key, xử lý lỗi, length check, decode, truy vấn DB và logic nghiệp vụ vẫn có thể khác biệt về thời gian. Cần đánh giá toàn protocol nếu threat model coi side-channel là quan trọng.

Không dùng `Arrays.equals(tag, received)` làm phương pháp mặc định để xác thực MAC nhạy cảm, và không so sánh string Base64/hex trực tiếp bằng `String.equals` khi có thể decode và kiểm tra byte. Việc xác minh MAC không được coi là phép đo quyền: một tag hợp lệ có thể đến từ client có key chung nhưng **không được phép** thực hiện hành động trong tenant khác.

### 9.1 Không biến so sánh byte thành logic chống replay

Một request đã ký có thể được gửi lại nguyên vẹn. Ví dụ `POST /pay` với body và tag hợp lệ được replay sẽ có **tag vẫn hợp lệ**. Cần gắn timestamp/nonce/request ID/sequence number và phạm vi tác vụ vào dữ liệu MAC rồi thực thi policy hạn dùng, phát hiện nonce đã sử dụng, kiểm tra idempotency của xử lý nghiệp vụ và chống đua tranh trong kho dữ liệu.

Chỉ “ký nonce” mà không **lưu/kiểm tra nonce đã dùng** sẽ không ngăn phát lại. Chỉ gắn timestamp mà không có giới hạn thời gian hoặc xử lý clock skew cũng không hoàn chỉnh.

### 9.2 Đừng quên xác định chính xác thứ được MAC bảo vệ

Ví dụ token có HMAC trên `userId` nhưng không bao gồm `role` hay `tenantId`: sửa những trường nằm ngoài MAC có thể làm sai ý nghĩa xác thực. Với webhook, nếu chữ ký chỉ bao gồm body mà không có method/path/time trong khi protocol yêu cầu ràng buộc đường đi, kẻ tấn công có thể thử đem cùng body/tag tới endpoint khác.

Mỗi định dạng nên công bố: **thứ tự trường, encoding, cách biểu diễn null/empty, version, thuật toán, key ID, tag encoding, phạm vi thời gian, nonce và nơi lấy danh tính đã xác minh**.

## 10. Cấu trúc byte không mơ hồ khi dùng nhiều update

Vì `update(partA); update(partB)` tương đương xử lý byte nối nhau, một giao thức ghép chuỗi tùy tiện có thể tạo cùng byte cho các cặp trường khác nhau.

Ví dụ:

```text
("ab", "c") -> UTF8("abc")
("a", "bc") -> UTF8("abc")
```

Nếu đây là hai bộ dữ liệu nghiệp vụ khác nhau, HMAC của cùng dãy byte sẽ **giống hệt**; điều đó không phải điểm yếu của HMAC mà là **thiếu ranh giới serialization**. Cần định nghĩa format rõ ràng, ví dụ:

```text
version | domain | length(field1) | field1 | length(field2) | field2
```

Các trường length-prefix phải xác định width, endianness và đơn vị (byte), mọi giá trị được mã hóa theo charset rõ ràng, và các byte version/domain cũng được đưa vào MAC. Có thể dùng protobuf/CBOR canonical hoặc một format đã được đặc tả; tránh tự chế nếu có chuẩn thích hợp.

Giữa các dịch vụ, chốt quy tắc cho Unicode normalization, JSON number, URL decoding, newline, ký tự rỗng, thứ tự field và literal escape. **NFC, UTF-8, canonical JSON và canonical HTTP request là những lớp khác nhau.** Không giả định gọi `String.getBytes(UTF_8)` đủ để hai hệ thống ký cùng một đối tượng JSON.

### Domain separation

Dùng một key hoặc hệ key có phạm vi rộng, nếu thiếu ngữ cảnh, một tag hợp lệ từ giao thức A có thể bị diễn giải là hợp lệ cho thông điệp B có byte trùng nhau. Tách key theo mục đích là lựa chọn mạnh; đồng thời đưa **nhãn ngữ cảnh và version giao thức** vào đúng phần dữ liệu được MAC bảo vệ, ví dụ nhãn ASCII `"invoice-webhook:v1"` đi cùng bộ field có ranh giới rõ ràng.

Không cho user thay đổi tự do nhãn mục đích hoặc key ID để né policy. Với multi-tenant, ràng buộc tenant vào dữ liệu xác thực và **kiểm tra quyền** ở service. Domain separation hỗ trợ chống dùng nhầm tag, nhưng không thay thế authorization.

## 11. Những vấn đề đặc biệt của trạng thái và ByteBuffer

### 11.1 Trạng thái của ByteBuffer

`mac.update(buffer)` chỉ xử lý byte giữa `position` và `limit` của `buffer`. Sau khi thành công, vị trí buffer tiến đến limit; không được mặc định rằng dữ liệu từ đầu mảng backing array đã được xử lý nếu position không ở 0. Nếu muốn đọc lại cùng byte, cần `duplicate`, `rewind` hoặc chỉnh lại position/limit theo quy tắc ByteBuffer có kiểm soát.

Trong demo, `ByteBuffer.wrap(message, 2, message.length - 2)` tạo buffer có vị trí 2 và limit bằng 8, nên xử lý đúng **6 byte còn lại**. Không nhầm nó với mảng mới dài 6 byte. Nếu sử dụng buffer tái sử dụng trong pipeline I/O, kiểm tra `flip`/`clear` theo hợp đồng producer/consumer để không ký byte thừa hoặc bỏ byte.

### 11.2 Output buffer của doFinal

Nếu tránh cấp phát mảng mới, `doFinal(byte[] output, int outOffset)` ghi tag vào mảng đã cấp. Mảng phải đủ chỗ kể từ `outOffset` cho `mac.getMacLength()` byte; method này trả `void`. Ví dụ minh họa với `mac` đã init và update xong:

```java
byte[] output = new byte[mac.getMacLength()];
mac.doFinal(output, 0);
```

Không nhầm `doFinal(output, 0)` là “xác thực mảng output”; đây là **mảng nhận tag**, khác `doFinal(byte[] input)` nơi mảng là **thông điệp được MAC**. Đây là lỗi overload dễ gây nhầm khi đọc code.

### 11.3 reset, init và clear key khác nhau

- `reset()` chỉ hủy kết quả update pending và trả object về trạng thái MAC mới **với cùng key**.
- `doFinal()` làm final rồi reset, giữ khả năng tính thông điệp mới cùng key.
- `init(newKey)` thiết lập khóa/tham số mới và trạng thái mới khi thành công.
- Không có API `Mac.destroyKey()` tổng quát đảm bảo xóa tất cả bản sao key trong provider/JVM; `SecretKey` có thể do provider/HSM quản lý, và thao tác xóa/thu hồi phụ thuộc loại key, bộ nhớ, đời sống object và cơ chế quản lý khóa.

Với yêu cầu nghiêm ngặt về vòng đời secret, xác định rõ provider, key store/KMS, rotation và cơ chế dispose; đừng dựa vào `reset()` như hành động bảo mật xóa khóa.

### 11.4 Lỗi mid-stream và retry

Ví dụ hệ thống streaming tính HMAC của body được đọc dở, rồi retry bằng cách gọi `update` với body từ đầu **trên cùng Mac chưa reset**. Kết quả là HMAC của `partial || full`, không phải `full`. Khi retry thao tác, tốt nhất **khởi tạo instance mới** hoặc reset dưới ownership độc quyền, rồi tính lại từ đầu. Đảm bảo kết quả của lượt xử lý thất bại không được publish.

Không log byte key/tag nhạy cảm, nội dung payload có thông tin cá nhân hoặc toàn bộ file chỉ để debug thất bại. Có thể log request ID, algorithm đã được allowlist, key version không nhạy cảm, độ dài byte và mã lỗi theo policy.

## 12. Xác thực khác với bảo mật đường truyền và phân quyền

Một MAC hợp lệ trả lời câu hỏi: “Thông điệp này có được tạo bởi **bên có khóa** và còn nguyên vẹn trong phạm vi byte đã ký không?”. Nó **không** tự trả lời:

- User/client đang gọi API là ai? (authentication/identity)
- User đó có quyền làm gì với đối tượng/tenant? (authorization)
- Message này đã được xử lý trước đây chưa? (anti-replay/idempotency)
- Payload có hợp lệ về schema, logic nghiệp vụ và giới hạn tài nguyên không? (validation)
- Nội dung có bí mật trên kênh truyền và khi lưu trữ không? (confidentiality)
- Key có bị lộ hoặc bị chia sẻ vượt miền tin cậy không? (key management)

Ví dụ với webhook từ đối tác, sau khi verify MAC vẫn phải kiểm tenant mapping, business order ID, trạng thái đơn hàng và chống xử lý lặp. Với yêu cầu cấp quyền, đừng tin trường `role=admin` chỉ vì cùng một key HMAC dùng chung cho nhiều service; policy phải dựa trên ngữ cảnh đáng tin và quyền đã cấp.

## 13. Bảng lỗi thường gặp

| Lỗi | Hậu quả | Cách phòng |
| --- | --- | --- |
| Quên `init` trước `update`/`doFinal` | `IllegalStateException` hoặc luồng xử lý sai | Tạo và init Mac trong cùng phạm vi tính tag; fail fast |
| Dùng một `static Mac` chung cho request song song | Trộn byte, reset chéo, kết quả race | Một `Mac` riêng cho mỗi phép tính/request |
| Nghĩ `update` tạo MAC độc lập mỗi lần | Ghép nhiều bản tin thành một MAC | Gọi `doFinal` đúng ranh giới bản tin |
| Gọi `doFinal(input)` sau khi đã update prefix không chủ ý | Tag tính trên `prefix \|\| input` | Reset hoặc new instance trước input độc lập |
| Gọi `reset` trước khi final dữ liệu cần xác thực | Mất byte pending | Chỉ reset khi cố tình hủy phép tính |
| Retry mà không reset/new instance | HMAC của `partial \|\| retry` | Retry với context mới |
| Dùng `getBytes()` không charset | Byte thay đổi theo runtime | Chỉ định UTF-8 hay charset đã thỏa thuận |
| Serialize lại JSON trước verify raw-body signature | Byte khác body gốc | Verify trên byte theo đúng đặc tả bên gửi |
| Ghép field không có length/boundary | Hai tuple khác nhau cho cùng byte | Canonical serialization, length-prefix, version/domain |
| Dùng `Arrays.equals` / string equality khi verify tag | Không phù hợp mục tiêu kiểm soát timing | Decode byte và dùng `MessageDigest.isEqual`, đánh giá toàn pipeline |
| Cắt tag tùy tiện hoặc dùng thuật toán từ request | Hạ mức bảo vệ, giao thức không tương thích | Allowlist thuật toán, độ dài tag theo hợp đồng |
| Dùng key cứng hoặc key tái sử dụng quá rộng | Rủi ro giả mạo lớn khi key lộ | Sinh key an toàn, scope, rotation, key version |
| Dùng HMAC để “mã hóa” | Payload vẫn đọc được | TLS/mã hóa khi cần bí mật |
| Không bảo vệ replay | Bản tin/tag cũ có thể gửi lại | Nonce/counter/time window và kiểm soát xử lý lặp |
| Không MAC các trường quyết định quyền | Tag hợp lệ nhưng resource/action bị sửa | Bao phủ mọi field nhạy cảm đúng phạm vi và xác minh quyền riêng |
| Chỉ xem unit test là bằng chứng đủ | Provider, key và wire protocol có thể khác thật | Test tích hợp với provider, vectors và giao thức thực tế |

## 14. Checklist thiết kế và kiểm thử

Đối với một service Java tính hoặc xác minh HMAC, đi qua checklist sau:

1. **Thuật toán:** dùng `HmacSHA256` được chọn server-side và tương thích chuẩn của đối tác.
2. **Key:** key đủ entropy, không hard-code; quyền sử dụng và rotation rõ ràng, key ID không dẫn tới lấy nhầm key.
3. **Dữ liệu:** byte input đúng contract, charset/serialization rõ ràng, không thay đổi âm thầm ở phía xác minh.
4. **Ranh giới:** một tag cho **một thông điệp**; `update` theo thứ tự đúng, `doFinal` sau đúng byte cuối.
5. **Trạng thái:** không chia sẻ `Mac` qua thread; reset khi hủy pending; không tái sử dụng trạng thái lỗi.
6. **Lỗi:** fail closed khi tag sai, input không hợp lệ, key không tìm thấy hoặc provider lỗi; không coi payload chưa đọc hết là đã xác thực.
7. **Kiểm tag:** độ dài/encoding theo chuẩn; `MessageDigest.isEqual` trên byte, kiểm tra thời gian đáp ứng nếu cần.
8. **Chống replay:** timestamp/nonce/idempotency có trạng thái và hạn dùng theo thiết kế.
9. **Authorization:** kiểm actor, action và resource độc lập với MAC; ghi audit vừa đủ.
10. **Test:** RFC vector, chunked vs one-shot, reset, thay byte, lỗi stream, các trường metadata ngoài body, số lượng request đồng thời và key rotation.

Các test tối thiểu hữu ích:

| Ca | Thiết lập | Kết quả mong đợi |
| --- | --- | --- |
| Vector RFC 4231 | Key 20 byte `0b`, payload `Hi There`, HmacSHA256 | Tag bằng `b0344c61...e32cff7` |
| Chia chunk | Cấp cùng byte theo `update` nhiều lần rồi `doFinal` | Bằng tag của `doFinal(fullInput)` |
| Sau `doFinal` | Tính cùng input ngay lần kế | Tag bằng nhau vì trạng thái reset |
| `reset` trước final | `update(bad)`, `reset`, `doFinal(good)` | Tag bằng HMAC của `good` riêng |
| Thay một byte | Đổi `Hi There` thành `Hi there` | Xác minh không chấp nhận |
| Quên `init` | Cố update/final trên Mac chưa init | Lỗi; không sinh tag hợp lệ |
| Streaming | File 20.000 byte, read từng khối 8192 byte | Tag khớp one-shot của toàn bộ file |
| Lỗi streaming | Stream lỗi sau một phần nội dung | Không công nhận tag; hủy lượt xử lý |
| Hai request đồng thời | Cùng service, hai message khác nhau | Tag của từng message ổn định, không trộn trạng thái |
| Replay | Gửi lại request và tag hợp lệ | MAC vẫn hợp lệ nhưng anti-replay/idempotency phải chặn lặp khi policy yêu cầu |
| Key rotation | Verify tag cũ bằng key version cũ còn được cho phép | Đúng trong giai đoạn chuyển đổi; key đã thu hồi thì bị từ chối theo chính sách |

**Gợi ý test lớn:** Đừng chỉ kiểm tag của cùng thư viện với chính nó. Hãy lưu ít nhất một **golden vector độc lập** (RFC 4231 hoặc từ đối tác) để phát hiện lỗi algorithm, charset, key parsing và serialization.

## 15. Bài tập thực hành

### Bài 1 — Ba phương thức update

Sử dụng key test `0b` lặp 20 lần và message `Hi There`. Viết ba phép tính:

1. `mac.doFinal(messageBytes)`.
2. `mac.update(messageBytes, 0, 2)` rồi `mac.update(messageBytes, 2, messageBytes.length - 2)`, cuối cùng `mac.doFinal()`.
3. `mac.update(ByteBuffer.wrap(messageBytes))` rồi `mac.doFinal()`.

So sánh cả ba tag với RFC 4231. Giải thích tại sao chunking không thay đổi tag khi **thứ tự byte và nội dung không đổi**.

### Bài 2 — Bắt lỗi quản lý trạng thái

Một developer viết:

```java
static final Mac SHARED_MAC = createAndInitializeMac();
byte[] sign(byte[] requestBody) {
    SHARED_MAC.update(requestBody);
    return SHARED_MAC.doFinal();
}
```

Đây là **mã giả minh họa lỗi**, không phải chương trình Java độc lập: `createAndInitializeMac()` không được định nghĩa ở đây. Hãy chỉ ra vì sao code có thể sai trong web service xử lý nhiều request song song, kể cả khi một request riêng có vẻ chạy đúng. Sửa theo hướng tạo `Mac` mới cho từng phép tính và giữ khóa trong secret manager/KMS. Viết test hai luồng để chứng minh tính độc lập.

### Bài 3 — Thiết kế chữ ký webhook

Một đối tác gửi các trường `timestamp`, `eventId`, `tenantId`, `body` và tag HMAC.

- Định nghĩa byte chính xác đưa vào MAC, version, charset và ranh giới giữa field.
- Quyết định lúc nào xác minh MAC và lúc nào parse JSON.
- Đề xuất cách lưu nonce/event ID để chống xử lý lặp với các request concurrent.
- Xác minh tenant và quyền cập nhật một bản ghi nghiệp vụ cụ thể.
- Kiểm thử request hợp lệ, body thay một byte, tag sai, timestamp quá hạn, event ID trùng, thay `tenantId` và dùng key đã thu hồi.

### Bài 4 — Streaming file

Viết method tính HMAC-SHA-256 của một file theo từng block; kết quả phải bằng one-shot của toàn byte. Sau đó giả lập file 1 MB, file rỗng, đọc được một phần rồi lỗi I/O, hai file liên tiếp và retry. Đảm bảo failure không xuất tag như thể file đã xác thực thành công.

**Điều kiện hoàn thành bài:** bạn có thể giải thích chính xác byte nào đã đi qua từng `update`, vì sao `doFinal` tạo ranh giới thông điệp và reset trạng thái, khi nào nên khởi tạo lại `Mac`, và tại sao việc có một tag HMAC hợp lệ vẫn cần authorization cùng anti-replay riêng.

## 16. Tài liệu tham khảo

- [Java SE 17 — javax.crypto.Mac](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Mac.html): API, trạng thái init/update/doFinal/reset, các overload và exceptions.
- [Java SE 17 — SecretKeySpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/SecretKeySpec.html): biểu diễn khóa bí mật từ byte.
- [Java SE 17 — MessageDigest.isEqual](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html#isEqual(byte%5B%5D,byte%5B%5D)): so sánh byte trong trường hợp kiểm tag.
- [RFC 2104 — HMAC: Keyed-Hashing for Message Authentication](https://www.rfc-editor.org/rfc/rfc2104): mô tả cấu trúc HMAC.
- [RFC 4231 — Identifiers and Test Vectors for HMAC-SHA-224/256/384/512](https://www.rfc-editor.org/rfc/rfc4231): test vector chuẩn cho HMAC-SHA-256 và các biến thể.
- [OWASP — Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): quản lý key, thuật toán và các lưu ý bảo vệ dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-authentication-code-004">5.4 Sinh, bảo quản và tách khóa HMAC theo mục đích sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# 5.4 Sinh, bảo quản và tách khóa HMAC theo mục đích sử dụng

**HMAC (Hash-based Message Authentication Code)** cho phép các bên cùng biết một **khóa bí mật** tạo và xác minh mã xác thực cho thông điệp. Với `HmacSHA256`, kết quả đầy đủ thường là **32 byte (256 bit)**, nhưng mức độ an toàn phụ thuộc rất lớn vào **cách sinh khóa**, **ai được giữ khóa**, **khóa được dùng cho việc gì**, và **cách xoay vòng, thu hồi khóa**. Tạo HMAC đúng thuật toán nhưng dùng chung một khóa cho webhook, session và API nội bộ có thể khiến một sự cố nhỏ lan rộng thành nhiều sự cố.

Mục tiêu của bài: tạo khóa có entropy đủ mạnh; lưu và cấp phát khóa đúng ranh giới tin cậy; hiểu hai cách **tách khóa độc lập** hoặc **dẫn xuất khóa theo mục đích (key derivation/domain separation)**; và quản lý vòng đời từ phát hành đến thu hồi. Ví dụ dùng Java 17+ và JCA/JCE.

## 1. Nhận diện đúng vai trò của khóa HMAC

HMAC sử dụng hàm băm mật mã cùng một khóa bí mật. Với ký hiệu:

```text
tag = HMAC(key, message)
valid = constant_time_compare(received_tag, HMAC(key, received_message))
```

- **Người tạo tag** biết khóa bí mật; **người kiểm tra tag** cũng phải biết khóa bí mật, hoặc dùng dịch vụ mật mã được ủy quyền để kiểm tra.
- Nếu thông điệp đổi mà tag giữ nguyên, phép xác minh sẽ thất bại với xác suất áp đảo khi HMAC được sử dụng đúng cách.
- **HMAC không mã hóa**: thông điệp và các header được xác thực vẫn có thể đọc được. Khi cần bí mật trên đường truyền, sử dụng TLS; khi cần bảo vệ dữ liệu lưu trữ, dùng giải pháp mã hóa phù hợp.
- **HMAC không tự chống replay**: một cặp `message + tag` hợp lệ bị gửi lại vẫn có thể hợp lệ. Giao thức cần timestamp, nonce, sequence, idempotency hoặc cơ chế từ chối phát lại nếu có yêu cầu.
- **HMAC không chứng minh duy nhất một người đã ký**: tất cả bên cùng giữ khóa đều có khả năng tạo tag. Nó là xác thực dựa vào *shared secret*, không cung cấp tính không thể chối bỏ như chữ ký số trong mô hình khóa và danh tính được quản lý đúng.
- HMAC không quyết định **phân quyền**; một webhook có tag hợp lệ vẫn cần được kiểm tra loại sự kiện, nguồn/tenant, quyền nghiệp vụ và trạng thái xử lý.

### 1.1. Key size khác MAC length

Với `HmacSHA256`, tag đầy đủ có **256 bit = 32 byte**. Độ dài khóa là đại lượng riêng. Khóa có thể dài hơn hoặc ngắn hơn 32 byte tùy giao thức và nguồn entropy. Với **khóa bí mật tạo ngẫu nhiên**, **32 byte (256 bit)** là lựa chọn thực hành rõ ràng và thường phù hợp cho HmacSHA256; đây không phải quy tắc “HMAC-SHA256 bắt buộc khóa đúng 32 byte”.

Bên trong HMAC, khóa dài hơn block size của hash được xử lý theo cấu trúc HMAC, không làm tag SHA-256 dài hơn 32 byte. Không suy ra “khóa dài 512 bit thì MAC mạnh 512 bit”; giới hạn bảo mật còn phụ thuộc hàm băm, độ dài tag, mô hình tấn công, quản lý khóa và giao thức.

| Thành phần | Ví dụ | Có bí mật không? |
| --- | --- | --- |
| **HMAC key** | 32 byte từ bộ sinh ngẫu nhiên mật mã | **Có**; ai có khóa có thể tạo tag |
| **Message** | Body, path, timestamp, nonce | Thường không; phụ thuộc dữ liệu và TLS |
| **MAC/tag** | 32 byte đầu ra HmacSHA256 | Không cần giữ bí mật, nhưng phải bảo đảm gắn đúng message/ngữ cảnh |
| **Key ID (`kid`)** | `webhook-prod-v2` | Thường công khai; chỉ là định danh để tìm khóa, không phải bằng chứng tin cậy |
| **Key version** | `v1`, `v2` | Metadata không bí mật; giúp xoay khóa |
| **Purpose/context** | `session`, `webhook-inbound` | Không cần bí mật; dùng để ràng buộc và phân tách |
| **HKDF salt** | Chuỗi byte ngẫu nhiên có thể lưu công khai | Không cần bí mật; phải khôi phục đúng khi dẫn xuất lại |

## 2. Sinh khóa HMAC an toàn

### 2.1. Sinh từ CSPRNG, không sinh từ dữ liệu dễ đoán

Khóa HMAC phải khó đoán với đối thủ, kể cả khi biết thuật toán, nhiều message và tag đã được công bố. Java có `SecureRandom` là giao diện sinh ngẫu nhiên mật mã; một khóa mới 32 byte có thể tạo như sau:

```java
byte[] key = new byte[32];
new java.security.SecureRandom().nextBytes(key);
```

Đây là *đoạn minh họa* để nói rõ kích thước, chưa phải chương trình độc lập. Trong code thật cần đưa khóa vào kho an toàn ngay sau khi tạo, quản lý người giữ và bảo đảm có thể khôi phục khi còn phải xác minh các thông điệp đã phát hành.

Có thể dùng `KeyGenerator` của provider nếu hỗ trợ thuật toán khóa HMAC phù hợp; khi đó chú ý tham số kích thước khóa tính bằng **bit**, không phải byte. `SecretKeySpec(key, "HmacSHA256")` chỉ **đóng gói mảng byte có sẵn**, không tự tạo entropy hay biến password yếu thành khóa mạnh.

**Tránh các nguồn khóa sau:**

- `new Random()`, `Math.random()`, thời gian hệ thống, số thứ tự, tên người dùng hoặc định danh đoán được.
- Một chuỗi người dùng tự nhập rồi gọi `getBytes(UTF_8)`. Số byte dài không bảo đảm entropy tương ứng.
- Khóa hardcode trong Java, YAML, Docker image, biến CI được in ra log hoặc nằm trong lịch sử Git.
- Khóa dùng chung được sao chép từ môi trường development sang staging/production.
- Dùng kết quả `SHA-256(password)` trực tiếp làm khóa HMAC như thể password đã có entropy 256 bit.

Nếu buộc phải lấy bí mật từ mật khẩu con người, hãy xét thiết kế **password KDF** có salt và chi phí phù hợp (ví dụ Argon2id/scrypt/PBKDF2, tùy hệ sinh thái và chính sách), thay vì tạo khóa dài bằng cách thêm ký tự, hash nhanh hoặc dùng Base64. Trong hệ thống service-to-service, ưu tiên tạo và quản lý khóa **ngẫu nhiên thật**, không cần đi qua mật khẩu.

### 2.2. Không nhầm entropy, encoding và chiều dài

- 32 byte từ `SecureRandom` dùng đúng cách mang xấp xỉ **256 bit entropy**; 32 byte của chuỗi `"aaaaaaaa..."` có entropy rất thấp.
- `Base64`, hex hoặc PEM chỉ **đổi biểu diễn byte**, không làm khóa an toàn hơn. 32 byte nhị phân có thể hiện thành 44 ký tự Base64 chuẩn, nhưng vẫn là một khóa 32 byte cần giữ bí mật.
- Không in raw key hoặc chuỗi Base64 của key để kiểm thử. Chỉ cần log **key ID, thuật toán, version, trạng thái**, và các thông tin vận hành không chứa khóa.
- Một khóa HMAC **khác** một nonce/IV hoặc salt; việc nonce/salt có thể công khai không cho phép công khai HMAC key.

### 2.3. Ai được tạo và cấp phát khóa?

Chọn rõ **chủ sở hữu khóa** và đường cấp phát:

1. Tốt nhất, hệ thống quản lý khóa/secret tạo hoặc nhập khóa bằng kênh có kiểm soát, cấp quyền theo service/tenant/purpose và audit sử dụng.
2. Nếu chương trình phải tạo khóa, dùng CSPRNG, đưa khóa ngay vào cơ chế lưu an toàn, gắn metadata; không tự in ra terminal, gửi qua chat/email hay commit vào repo.
3. Khi hai hệ thống cần **cùng** khóa để xác minh HMAC (ví dụ sender và webhook receiver), chia sẻ qua quy trình provision/secret manager có quyền truy cập tối thiểu. Coi **cả hai đầu** là bên có thể tạo MAC, nên không thể dùng HMAC để phân biệt ai đã tạo nếu không có ràng buộc bổ sung.
4. Có thể dùng HSM/KMS có tính năng tạo/xác minh MAC để tránh đưa raw key vào tiến trình ứng dụng; cần kiểm tra API và thuật toán cụ thể của nhà cung cấp. **Không giả định mọi KMS đều hỗ trợ HMAC hoặc cùng một dạng `SecretKey`**.

## 3. Bảo quản khóa: bảo vệ bí mật và phạm vi truy cập

Khóa HMAC là tài sản nhạy cảm. Người biết khóa có thể giả mạo thông điệp trong mọi ngữ cảnh dùng chung khóa đó.

| Nơi/cách giữ | Đánh giá và lưu ý |
| --- | --- |
| **Secret manager/KMS/HSM** | Ưu tiên theo yêu cầu hệ thống; IAM tách quyền đọc/ghi/ký/xác minh, audit, rotation và bảo vệ khóa. Nếu có MAC API không xuất khóa, ứng dụng có thể không cần thấy raw key. |
| **Secret được ứng dụng nạp khi khởi động** | Chỉ cho workload identity cần thiết đọc; giới hạn file permissions/mount, tránh log, crash dump, thông báo lỗi và endpoint debug tiết lộ key. |
| **Biến môi trường** | Có thể dùng trong môi trường hạn chế nhưng biến môi trường không phải kho bí mật mặc định; hãy xét khả năng bị lộ qua tiến trình con, công cụ debug, log hoặc cấu hình triển khai. |
| **Cơ sở dữ liệu/bản sao lưu** | Nếu cần giữ key, mã hóa bằng khóa quản lý **riêng**, giới hạn truy cập và quản lý backup/restore; không lưu plaintext trong bản ghi dễ đọc. |
| **Mã nguồn, Dockerfile, `application.yml` được commit, tài liệu, ticket, chat** | **Không lưu khóa thực**. Lịch sử commit và bản sao có thể tồn tại cả sau khi xóa dòng. |

Các nguyên tắc chính:

- **Least privilege:** service chỉ cần xác minh MAC thì nên có quyền **verify** trên KMS nếu hạ tầng hỗ trợ tách quyền, thay vì mặc định được đọc raw key hoặc ký mọi message.
- **Tách môi trường:** development, test, staging, production có khóa độc lập. Cần chia tách theo tenant, đối tác, protocol hoặc service khi ranh giới tin cậy yêu cầu.
- **Kiểm soát vòng đời:** mỗi key có owner, purpose, algorithm, key ID, version, ngày kích hoạt/ngừng ký/ngừng xác minh, chính sách rotation/retention và quy trình thu hồi.
- **Không log hoặc phản hồi lỗi chứa khóa:** tránh `Arrays.toString(key)`, `HexFormat.of().formatHex(key)`, `Base64.getEncoder().encodeToString(key)` trong log. Tag cũng có thể cần quản lý dữ liệu/ẩn thông tin tùy nghiệp vụ, nhưng tag **không phải khóa**.
- **Chú ý bộ nhớ:** `Arrays.fill(key, (byte) 0)` có thể xóa một mảng byte tạm đang sở hữu, nhưng không bảo đảm xóa mọi bản sao trong `SecretKeySpec`, provider, heap dump, swap hoặc HSM. Không xem việc overwrite một mảng là cơ chế bảo vệ hoàn chỉnh.
- **Không có key hợp lệ thì từ chối xác minh (fail closed)**; không dùng khóa mặc định `"secret"`, không vô hiệu kiểm tra MAC, không fallback sang thuật toán yếu.

### 3.1. Khóa không xuất được và Java JCA/JCE

Trong Java, `SecretKey` là trừu tượng. Với khóa phần mềm, `getEncoded()` có thể trả byte; với khóa trong HSM/provider không xuất được, phương thức này có thể trả `null`. Một số provider yêu cầu chạy `Mac` qua provider tương ứng hoặc gọi MAC API riêng.

Do vậy kiến trúc **không nên phụ thuộc vào việc dump khóa ra byte** để xác minh “đúng 32 byte” trong sản phẩm. Cấu hình key size và chính sách quản lý ở điểm tạo khóa; code gọi HMAC qua một interface có thể triển khai bằng JCA hoặc KMS tùy hệ thống.

## 4. Tách khóa theo mục đích sử dụng (key separation)

### 4.1. Vì sao một “super HMAC secret” là nguy hiểm?

Giả sử một ứng dụng dùng một khóa cho:

- Ký session cookie (xác thực phiên);
- Xác minh webhook từ đối tác;
- Ký URL tải file;
- Xác minh tác vụ quản trị nội bộ.

Nếu khóa webhook bị lộ từ bên tích hợp, kẻ tấn công có thể tính HMAC cho các loại message khác. **Biết khóa = có khả năng tạo MAC ở mọi giao thức dùng khóa đó.** Một parser coi thông điệp có tag hợp lệ là “được tin” mà không kiểm tra scope sẽ mở đường cho tấn công chuyển ngữ cảnh (*cross-protocol confusion*).

Thiết kế tốt phải tách bí mật **và** tách cách diễn giải message. Ví dụ ranh giới:

| Mục đích | Định danh logic | Bên được phép sử dụng |
| --- | --- | --- |
| Session/cookie | `shop/prod/session/v3` | Service quản lý phiên |
| Inbound partner webhook | `shop/prod/webhook/partner-A/v2` | Gateway nhận webhook và bên đối tác tương ứng |
| URL có hạn | `shop/prod/download-link/v1` | Service phát và xác minh URL |
| Yêu cầu nội bộ | `shop/prod/internal-job/v4` | Service phụ trách tác vụ |

Không chỉ đổi **tên biến**; cần dùng **khóa khác** hoặc **khóa con được dẫn xuất độc lập**, chính sách cấp quyền theo mục đích và ràng buộc mục đích trong dữ liệu được MAC.

### 4.2. Cách A: sinh khóa ngẫu nhiên độc lập cho từng mục đích

Đơn giản nhất là sinh riêng:

```text
K_session     <- SecureRandom(32 bytes)
K_webhook     <- SecureRandom(32 bytes)
K_download    <- SecureRandom(32 bytes)
K_internal    <- SecureRandom(32 bytes)
```

Mỗi khóa được lưu, đánh nhãn, cấp phát và rotate riêng. Nếu lộ khóa của đối tác webhook, khóa session vẫn không tự bị suy ra. **Đây thường là cách dễ kiểm toán, cô lập sự cố và xoay khóa độc lập nhất** khi có cơ sở hạ tầng quản lý secret.

Nhược điểm là số khóa, bản sao và quyền cấp phát tăng theo số mục đích/tenant. Cần có registry/secret manager rõ ràng; tránh chuyển sang một khóa chung chỉ vì thấy nhiều entry.

### 4.3. Cách B: dẫn xuất khóa con từ một root secret bằng HKDF

Khi phải quản lý nhiều khóa có quan hệ từ một **root key entropy cao**, có thể dùng **HKDF-SHA256** (RFC 5869) để dẫn xuất các khóa con theo từng mục đích:

```text
root_secret  --HKDF-Extract(salt)--> PRK
PRK + info("shop|prod|session|v1") --> K_session (32 bytes)
PRK + info("shop|prod|webhook|v1") --> K_webhook (32 bytes)
PRK + info("shop|prod|download|v1")-> K_download (32 bytes)
```

Trong HKDF chuẩn:

- **IKM (input keying material):** secret đầu vào có entropy phù hợp. Ở đây là root ngẫu nhiên mạnh, **không** phải password người dùng chưa qua password KDF.
- **Salt:** giá trị thường ngẫu nhiên, có thể công khai, dùng trong pha `Extract` để tạo **PRK (pseudorandom key)**. Nếu muốn dẫn xuất lại cùng key sau restart, **giữ đúng salt** cùng metadata; đổi salt sẽ đổi khóa con. HKDF định nghĩa giá trị mặc định khi salt bị bỏ trống, nhưng nên thiết kế và ghi rõ lựa chọn.
- **Info:** chuỗi byte mô tả ngữ cảnh, mục đích và version, dùng trong pha `Expand`. Khác `info` dẫn tới các khóa con khác nhau dưới giả định an toàn của HKDF.
- **Length:** số **byte** đầu ra muốn tạo. Với HmacSHA256, một khóa con 32 byte tương ứng **256 bit**; HKDF Expand có giới hạn tổng độ dài không vượt **255 × HashLen** byte.
- **Root key có quyền lực bao trùm:** kẻ lộ root có thể dẫn xuất **mọi khóa con**. Tách bằng HKDF làm giảm việc tái dùng cùng khóa, nhưng **không đạt mức cô lập rủi ro root giống nhiều khóa ngẫu nhiên hoàn toàn độc lập**.

Thiết kế `info` nên ghi rõ **ứng dụng, môi trường, protocol, purpose, đối tác hoặc tenant nếu cần, thuật toán và version**. Các trường có thể do người dùng cung cấp phải được canonicalize và mã hóa không nhập nhằng, ví dụ TLV hoặc length-prefix; không ghép ngây thơ những chuỗi chứa delimiter để hai bộ tham số tạo cùng một `info`.

**Đừng tự “dẫn xuất khóa” bằng `SHA256(root || purpose)` hoặc bằng Base64 của root.** Hãy dùng một KDF đã được định nghĩa và kiểm chứng như HKDF theo đúng các bước và tham số của nó.

### 4.4. Tách khóa khác với thêm nhãn vào message

Có hai lớp phòng vệ khác nhau:

1. **Key separation:** `K_session != K_webhook`. Kẻ có khóa webhook không tự tính được MAC dùng khóa session.
2. **Domain separation của message:** ngay cả trong một phạm vi khóa, thêm dữ liệu có cấu trúc, không nhập nhằng như `protocol_version`, `purpose`, `tenant`, `operation` vào những byte được MAC, giúp ngăn tag của một loại thông điệp bị hiểu là loại khác.

Chỉ thêm `"session:"` vào trước message nhưng vẫn giữ **một shared secret chung có mặt ở mọi service** không cô lập được sự cố khi secret đó lộ. Ngược lại, dù dùng khóa độc lập, vẫn cần xác thực đầy đủ các trường có ý nghĩa nghiệp vụ.

Ví dụ metadata `purpose`, `tenant`, `environment` và `key_id` chỉ là **thông tin tham chiếu**, không phải bằng chứng tự xác minh; **server phải áp chính sách ràng buộc chúng với khóa và request**.

## 5. Thiết kế giao thức MAC và xác minh phù hợp

Một giao thức có thể dùng cấu trúc:

```text
version: 2
key_id: webhook-partner-A-v2
timestamp: 2026-10-31T12:00:00Z
nonce: 4f8b...     (giá trị ví dụ)
body: {"event":"payment.paid","id":"evt123"}
mac: <HMAC-SHA256 của biểu diễn canonical đã xác định>
```

Các giá trị trên chỉ là mô hình minh họa, **không phải một request có MAC tính sẵn**. Để dùng thật phải định nghĩa chính xác byte được ký (phương thức, path/query, timestamp, nonce, body, context) và quy tắc canonicalization. Nếu các byte có ý nghĩa bảo mật không được MAC thì kẻ tấn công có thể thay chúng mà tag không đổi.

Quy trình server hợp lý:

1. **Xác định giao thức và purpose tin cậy từ route/cấu hình**, không cho client tự khai purpose để được chọn một key scope có quyền rộng hơn.
2. Giới hạn định dạng/độ dài của `key_id`; tra key trong registry **đúng tenant + purpose + environment + trạng thái**. `key_id` là selector do client gửi nên phải coi là dữ liệu không tin cậy, không phải đường dẫn file, URL hay chỉ thị tải secret tùy ý.
3. Chỉ dùng **thuật toán do server quy định** (ví dụ HmacSHA256); không chấp nhận `alg=none` hay thuật toán khác dựa vào header không tin cậy.
4. Kiểm tra timestamp trong cửa sổ hợp lệ, nonce/idempotency chưa dùng và những giới hạn phù hợp **nếu giao thức yêu cầu chống replay**.
5. Tạo lại **chính xác các byte** đã ký, tính HMAC bằng khóa tương ứng, kiểm tra tag độ dài chuẩn và so sánh bằng phương pháp tránh tiết lộ thời gian phụ thuộc nội dung, ví dụ `MessageDigest.isEqual(byte[], byte[])` trong Java khi đã biết tag đúng độ dài.
6. Chỉ sau khi MAC hợp lệ mới xử lý nghiệp vụ, nhưng còn phải kiểm tra authorization, tenant, loại sự kiện và điều kiện trạng thái. Cơ chế replay phải được cập nhật theo cách nguyên tử nếu cần chống nhận hai request đồng thời.

**Lưu ý canonicalization:** chữ hoa/thường trong header, thứ tự query parameter, encoding UTF-8, ký tự xuống dòng, khoảng trắng JSON, chuẩn hóa Unicode và raw body có thể dẫn đến hai byte stream khác nhau. Trong webhook, nếu đối tác ký **raw HTTP body**, receiver phải dùng đúng byte raw đã nhận theo đặc tả, **không parse rồi serialize JSON lại** trước khi tính HMAC.

**So sánh tag:** tag đầy đủ HmacSHA256 dài 32 byte. Nếu giao thức cho phép truncate tag, phải có thông số độ dài rõ ràng, giới hạn phù hợp, kiểm tra độ dài và tính đúng chuỗi byte. Không tự cắt tag tùy tiện để “tiết kiệm băng thông”.

## 6. Vòng đời khóa: tạo, dùng, rotate, thu hồi, hủy

Khóa HMAC thường tồn tại lâu hơn một instance Java. Muốn thay khóa không làm hỏng request còn hiệu lực, cần **key ring có version** và trạng thái riêng cho ký mới/xác minh cũ.

| Giai đoạn | Đối tượng thực hiện | Quy tắc |
| --- | --- | --- |
| **Generate** | Quản lý khóa | Sinh khóa ngẫu nhiên mới, định danh, owner, purpose, thuật toán, thời gian; không tái sử dụng secret cũ |
| **Store/provision** | Secret manager/KMS | Mã hóa/giữ trong HSM khi phù hợp; cấp quyền tối thiểu cho workload và đối tác |
| **Active signing** | Bên tạo MAC | Chỉ **một version hiện hành cho việc ký mới** tại mỗi phạm vi cần thống nhất |
| **Verify overlap** | Bên xác minh | Có thể nhận **key mới và key cũ** trong cửa sổ chuyển tiếp giới hạn, mỗi key chỉ dùng đúng purpose/scope |
| **Retire** | Quản lý khóa | Ngừng ký bằng key cũ; ngừng verify khi message hợp lệ cũ đã hết hạn/được xử lý |
| **Revoke/compromise** | Incident response | Nếu lộ khóa, thu hồi nhanh, đánh giá dữ liệu đã được ký giả, không chờ hết thời hạn rotation định kỳ |
| **Destroy** | Quản lý khóa/backup | Xóa/crypto-shred theo chính sách, xét cả phiên bản trong backup và cache; ghi lại việc hủy mà không log secret |

### 6.1. Ví dụ rotation có `kid`

Giả sử service đang dùng `key_id=webhook-v1` rồi phát hành `webhook-v2`:

- **Chuẩn bị:** tạo secret v2 độc lập, cập nhật receiver để biết v2 trước khi sender chuyển sang ký v2.
- **Chuyển đổi:** sender bắt đầu phát `kid=v2` và HMAC bằng key v2. Receiver kiểm tra v2; tạm tiếp tục kiểm tra v1 cho thông điệp phát hành trước hạn chót.
- **Kết thúc:** sau cửa sổ hợp lệ và độ trễ phân phối, tắt verify v1, thu hồi quyền truy cập, dọn các cache/backup theo chính sách.
- **Nếu bị lộ v1:** thay khóa và **thu hồi ngay theo quy trình xử lý sự cố**, không mặc định giữ “overlap” chỉ để tránh lỗi khách hàng. Có thể cần hủy các session/token đã ký bằng v1 và thông báo bên liên quan.

**Không thử tất cả khóa, tất cả purpose và mọi tenant để “tìm MAC đúng”** khi request đã khai báo key ID rõ ràng. Điều đó làm rộng khả năng chấp nhận ngoài policy và khó kiểm soát thời hạn. Lookup đúng scope, kiểm tra trạng thái, rồi verify.

### 6.2. Vòng đời root key khi dùng HKDF

Rotation root tạo một tập khóa con mới (kể cả khi `info` giữ nguyên), do đó:

- Lưu **root key version + salt + cấu trúc info + thuật toán KDF + output length** như một phần thông tin không thể thiếu cho việc dẫn xuất lại.
- Thay root theo kế hoạch ở tầng quản lý khóa, phân phối dần khóa con mới; key ID phải cho phép xác định version thích hợp.
- Một root bị lộ đồng nghĩa các khóa con dẫn xuất từ nó có thể bị lộ; cần lập kế hoạch thay **toàn bộ miền khóa con** bị ảnh hưởng.
- Không chia sẻ raw root cho mọi microservice chỉ để chúng tự sinh subkey. Nếu có thể, chỉ phát subkey đúng scope hoặc cho gọi API derivation/MAC được giới hạn quyền.
- Không dùng lại raw root để trực tiếp HMAC message nghiệp vụ: root chỉ dành cho KDF theo thiết kế đó.

## 7. Java 17+: tạo khóa HMAC độc lập và ràng buộc purpose

Chương trình độc lập dưới đây tạo **hai khóa 32 byte ngẫu nhiên độc lập** cho `session` và `webhook`. Phần prefix `demo:v1|purpose|` bổ sung domain separation trong bytes được MAC; demo chỉ dùng hai purpose cố định do code tin cậy chọn. Trong hệ thống thật cần encoding không nhập nhằng và chính sách xác định scope tại server.

Lưu đúng tên **HmacPurposeDemo.java**:

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacPurposeDemo {
    static byte[] generateKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    static byte[] tag(byte[] key, String purpose, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update(("demo:v1|" + purpose + "|").getBytes(StandardCharsets.UTF_8));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }

    static boolean verify(byte[] key, String purpose, String payload,
                          byte[] expected) throws Exception {
        return expected != null && expected.length == 32
                && MessageDigest.isEqual(expected, tag(key, purpose, payload));
    }

    public static void main(String[] args) throws Exception {
        byte[] sessionKey = generateKey();
        byte[] webhookKey = generateKey();
        String payload = "user=alice";
        byte[] sessionTag = tag(sessionKey, "session", payload);

        System.out.println("session key bytes: " + sessionKey.length);
        System.out.println("session tag bytes: " + sessionTag.length);
        System.out.println("correct purpose: "
                + verify(sessionKey, "session", payload, sessionTag));
        System.out.println("wrong purpose: "
                + verify(sessionKey, "webhook", payload, sessionTag));
        System.out.println("wrong key: "
                + verify(webhookKey, "session", payload, sessionTag));
        System.out.println("tampered payload: "
                + verify(sessionKey, "session", "user=bob", sessionTag));

        Arrays.fill(sessionKey, (byte) 0);
        Arrays.fill(webhookKey, (byte) 0);
    }
}
```

Biên dịch, chạy:

```bash
javac -encoding UTF-8 HmacPurposeDemo.java
java HmacPurposeDemo
```

Stdout kỳ vọng (khóa ngẫu nhiên mỗi lần khác nhau nhưng các kết luận giữ nguyên trong thực hành):

```text
session key bytes: 32
session tag bytes: 32
correct purpose: true
wrong purpose: false
wrong key: false
tampered payload: false
```

**Giải thích:**

- `new SecureRandom().nextBytes(key)` tạo một mảng 32 byte khóa mới; chương trình không in raw key.
- `tag()` dùng `Mac.getInstance("HmacSHA256")` và `SecretKeySpec`. Tạo instance `Mac` riêng cho phép tính; `Mac` có trạng thái và **không nên chia một instance mutable cho nhiều thread mà không có cơ chế điều phối**.
- Kiểm tra `wrong purpose` dùng **cùng** session key nhưng dữ liệu purpose khác trong input của HMAC; `wrong key` dùng **khóa khác**; cả hai cho thấy hai lớp bảo vệ không nên lẫn lộn.
- `MessageDigest.isEqual` so sánh hai byte array của tag sau khi đã xác minh độ dài dự kiến. Đừng dùng `new String(tag)` hoặc phép so sánh có hành vi phụ thuộc nội dung để xác minh tag.
- `Arrays.fill` chỉ là dọn mảng tạm ở mức tốt nhất, **không bảo đảm xóa toàn bộ bản sao** mà provider/JVM có thể giữ. Code demo **không cất giữ key qua restart**, không quản lý rotation/key ID và không thực hiện xác thực session thực tế.

Đối với ứng dụng lớn, `generateKey()` thường là trách nhiệm của hệ thống quản lý khóa, còn logic nghiệp vụ chỉ nhận quyền thao tác HMAC phù hợp.

## 8. Java 17+: dẫn xuất khóa theo purpose bằng HKDF-SHA256

Ví dụ sau triển khai đầy đủ **HKDF-Extract + HKDF-Expand** với HmacSHA256 theo RFC 5869 để minh họa. Đầu tiên kiểm tra **RFC 5869 test vector case 1** bằng giá trị công khai; sau đó tạo root và salt ngẫu nhiên, dẫn xuất hai khóa 32 byte từ hai `info` khác nhau. Ví dụ không in root, PRK hay subkey.

Lưu **HkdfPurposeDemo.java**:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HkdfPurposeDemo {
    static final int HASH_LEN = 32;

    static byte[] hmac(byte[] key, byte[]... inputs)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        for (byte[] input : inputs) mac.update(input);
        return mac.doFinal();
    }

    static byte[] extract(byte[] salt, byte[] ikm)
            throws GeneralSecurityException {
        return hmac(salt == null || salt.length == 0
                ? new byte[HASH_LEN] : salt, ikm);
    }

    static byte[] expand(byte[] prk, byte[] info, int length)
            throws GeneralSecurityException {
        if (length < 1 || length > 255 * HASH_LEN)
            throw new IllegalArgumentException("invalid length");

        byte[] output = new byte[length];
        byte[] previous = new byte[0];
        int offset = 0;
        for (int counter = 1; offset < length; counter++) {
            previous = hmac(prk, previous, info, new byte[]{(byte) counter});
            int count = Math.min(HASH_LEN, length - offset);
            System.arraycopy(previous, 0, output, offset, count);
            offset += count;
        }
        Arrays.fill(previous, (byte) 0);
        return output;
    }

    static byte[] context(String purpose) {
        return ("app=shop|env=prod|alg=HmacSHA256|purpose="
                + purpose + "|version=1").getBytes(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        byte[] salt = HexFormat.of().parseHex("000102030405060708090a0b0c");
        byte[] info = HexFormat.of().parseHex("f0f1f2f3f4f5f6f7f8f9");
        byte[] okm = expand(extract(salt, ikm), info, 42);

        String expected =
                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c"
              + "5db02d56ecc4c5bf34007208d5b887185865";
        System.out.println("RFC 5869 case 1 valid: "
                + expected.equals(HexFormat.of().formatHex(okm)));

        byte[] root = new byte[32];
        byte[] appSalt = new byte[32];
        new SecureRandom().nextBytes(root);
        new SecureRandom().nextBytes(appSalt);
        byte[] prk = extract(appSalt, root);
        byte[] sessionKey = expand(prk, context("session"), 32);
        byte[] webhookKey = expand(prk, context("webhook"), 32);

        System.out.println("derived key bytes: " + sessionKey.length);
        System.out.println("same purpose stable: "
                + MessageDigest.isEqual(sessionKey,
                        expand(prk, context("session"), 32)));
        System.out.println("other purpose differs: "
                + !MessageDigest.isEqual(sessionKey, webhookKey));

        Arrays.fill(root, (byte) 0);
        Arrays.fill(prk, (byte) 0);
        Arrays.fill(sessionKey, (byte) 0);
        Arrays.fill(webhookKey, (byte) 0);
    }
}
```

Biên dịch, chạy:

```bash
javac -encoding UTF-8 HkdfPurposeDemo.java
java HkdfPurposeDemo
```

Stdout:

```text
RFC 5869 case 1 valid: true
derived key bytes: 32
same purpose stable: true
other purpose differs: true
```

**Phân tích kết quả:**

1. Test vector xác nhận rằng ví dụ dùng đúng thứ tự `HKDF-Extract(salt, IKM)` và `HKDF-Expand(PRK, info, L)`, trong đó mỗi khối Expand phụ thuộc khối trước, `info` và counter 1 byte. Giá trị kỳ vọng công khai từ RFC 5869 không phải khóa sản xuất.
2. Khóa con cho `session` và `webhook` đều 32 byte nhưng khác nhau do `info` khác nhau. Nếu cùng root, salt, info và length, HKDF tất định: dẫn xuất lại cho cùng kết quả.
3. Root và salt được tạo ngẫu nhiên khi chương trình chạy; một ứng dụng thật cần lưu/khôi phục **root và salt cùng version** theo thiết kế, nếu không các key sinh ra lần sau sẽ khác.
4. Ví dụ chỉ cho purpose cố định do code chọn. Với hệ thống đa tenant/đối tác, phải chuẩn hóa và mã hóa `info` không nhập nhằng, kiểm tra nguồn tin cậy của từng thuộc tính và quyền truy cập. **Không dùng `purpose` hay `tenant` tự khai để mở quyền ký/xác minh.**
5. Để dùng trong sản phẩm, ưu tiên thư viện hoặc dịch vụ mật mã đã kiểm định triển khai HKDF; ví dụ này nhằm giúp hiểu cấu trúc RFC và đo đúng kết quả. Việc xóa mảng tạm trong demo chỉ là best effort, không biến Java heap thành vùng bí mật được bảo đảm.

## 9. Tình huống thiết kế thực tế

### 9.1. Webhook từ hai đối tác khác nhau

Nếu Partner A và Partner B đều gửi `payment.paid`, tạo khóa `partner-A/prod/webhook/v1` và `partner-B/prod/webhook/v1` riêng biệt. Khi request đến, map đối tác qua endpoint/cấu hình và chọn key đúng scope. Một tag hợp lệ từ A **không** được dùng để xác thực message có tư cách B.

Nếu đối tác kiểm tra HMAC chung với server, cả hai nắm secret. Khi cần xác nhận nguồn theo danh tính không chia sẻ, cân nhắc chữ ký số hoặc cơ chế xác thực dịch vụ phù hợp thay vì cho rằng HMAC tự cung cấp non-repudiation.

### 9.2. Session và signed download URL

Một service ký cookie phiên, một service khác ký URL tải tài liệu. Hai purpose có chính sách khác nhau: cookie thuộc phiên đăng nhập; URL phải chứa hạn dùng, tài nguyên, hành động và có thể giới hạn người nhận. Dùng **khóa riêng** để không biến việc lộ khóa signed URL thành khả năng giả mạo session. Không cấp `K_session` cho service chỉ cần kiểm tra URL.

### 9.3. Nhiều tenant và microservice

Nếu mỗi tenant cần biên giới tin cậy riêng, cân nhắc **per-tenant keys** do secret manager giữ hoặc HKDF với context chuẩn hóa có tenant ID và purpose. Phải xét rõ ai có quyền root: tách theo HKDF **không** giảm ảnh hưởng của việc root bị chiếm toàn bộ. Khi yêu cầu cô lập chặt, dùng khóa độc lập và giới hạn IAM theo tenant.

### 9.4. Thay key v1 giữa lúc message còn hợp lệ

Cho phép receiver giữ v1 ở chế độ **verify-only** trong thời hạn tối đa đã thiết kế; sender chỉ phát v2. Gắn `kid` vào envelope và xác minh nó trong scope. Sau hạn, xóa v1 khỏi danh sách cho phép. Nếu v1 đã bị lộ, phải đánh giá **thu hồi ngay** và vô hiệu hóa message còn hiệu lực thay vì ưu tiên tương thích.

### 9.5. Phát hiện khóa nằm trong Git hoặc log

Không chỉ “xóa dòng” là đủ. Coi secret đã bị lộ: phát khóa mới, cập nhật bên sử dụng, thu hồi key cũ, điều tra phạm vi ký giả và lưu dấu audit. Dọn log/backup/history theo khả năng và chính sách, kiểm tra secret scanning/CI để ngăn tái diễn.

## 10. Lỗi thường gặp và cách sửa

| Lỗi | Rủi ro | Cách đúng |
| --- | --- | --- |
| Hardcode `"changeit"` làm HMAC key | Người có mã nguồn có thể giả MAC | Sinh khóa ngẫu nhiên đủ mạnh và giữ trong hệ thống secret |
| Dùng `Random`/timestamp/password làm khóa | Dễ đoán, entropy thấp | `SecureRandom`/KMS; nếu buộc dùng password, xử lý bằng password KDF phù hợp |
| Dùng một key cho session, webhook, URL | Lộ một hệ thống ảnh hưởng nhiều mục đích | Khóa độc lập hoặc HKDF theo purpose, ranh giới IAM rõ |
| Chỉ đổi tên biến `SESSION_KEY`/`WEBHOOK_KEY` nhưng bytes giống nhau | Chưa tách bí mật | Thực sự phát khóa khác hoặc dẫn xuất subkey với info khác |
| Dùng hex/Base64 để “giấu” secret | Encoding có thể giải mã tức thì | Quản lý access, encryption at rest/secret manager, tránh log |
| Đặt root HKDF trong tất cả microservice | Mỗi service có quyền suy ra mọi subkey | Chỉ phát key đúng scope, giữ root trong khu vực tin cậy |
| `SHA256(root \|\| purpose)` tự chế | KDF không có thiết kế và kiểm định rõ | Dùng HKDF theo RFC, context không nhập nhằng |
| Cho client chọn `alg`/`kid`/purpose tùy ý | Confusion/downgrade/secret lookup sai | Server cố định thuật toán, tra kid trong scope đáng tin và kiểm tra trạng thái |
| Ký JSON đã serialize nhưng verify JSON serialize lại khác | HMAC không khớp dù cùng ý nghĩa | Đặc tả byte canonical; webhook raw body thì xác minh raw body |
| Verify mọi version key vô thời hạn | Key cũ tiếp tục là điểm yếu | Cửa sổ overlap giới hạn, retire/revoke theo sự kiện |
| Cho rằng HMAC chặn replay | Request cũ vẫn có tag hợp lệ | Timestamp, nonce/sequence, TTL và replay cache/logic nguyên tử |
| `Arrays.fill` là đảm bảo không còn key ở RAM | JVM/provider có thể giữ bản sao | Giảm thời gian/phạm vi giữ key, hạn chế dump, ưu tiên non-exportable key |
| In key để debug lỗi xác minh | Tiết lộ khóa thực | Log `kid`/version/correlation ID, không log secret |
| Dùng cùng key giữa test và production | Rò môi trường thấp ảnh hưởng production | Mỗi môi trường có secret riêng, chính sách IAM riêng |

## 11. Checklist review trước khi triển khai HMAC

1. **Tạo khóa:** khóa sinh bằng CSPRNG/KMS? Độ dài và entropy có phù hợp? Có dùng secret mẫu, password hoặc khóa hardcode không?
2. **Ownership:** xác định service/đối tác/tenant, người quản trị và mục đích duy nhất của từng key chưa? Cấp quyền least privilege chưa?
3. **Lưu trữ:** key được bảo quản/backup/restore an toàn, không lọt vào source control, log, heap dump hoặc diagnostic output ngoài ý muốn?
4. **Tách miền:** dùng key độc lập hoặc HKDF theo context rõ ràng? Tránh chia sẻ root và tránh scope quá rộng?
5. **Thông điệp:** quy tắc byte canonical rõ chưa? Có MAC đầy đủ các trường bảo mật và cố định thuật toán không?
6. **Xác minh:** chọn key đúng purpose + tenant + version, kiểm tra tag dài đúng, so sánh an toàn, từ chối key không hợp lệ và xác minh thất bại?
7. **Replay và quyền:** có chống replay và kiểm tra authorization nghiệp vụ độc lập với HMAC không?
8. **Rotation:** có `kid`, keyring, lộ trình active/verify-only/retired, phương án khẩn cấp khi key bị lộ và thời điểm dọn v1 không?
9. **Vận hành:** có secret scanning, log không lộ key, audit quyền và kiểm thử sai key/sai purpose/sai message/cũ hạn không?

## 12. Bài tập tự kiểm tra

**Câu 1.** Hai hệ thống dùng cùng khóa 32 byte HmacSHA256 nhưng khác tên biến. Đã tách khóa chưa?  
**Trả lời:** **Chưa.** Đổi tên không thay đổi vật liệu khóa; cần khóa bí mật độc lập hoặc derivation theo purpose khác nhau.

**Câu 2.** Key HmacSHA256 được sinh bằng `SecureRandom` dài 32 byte. Tag đầy đủ có nhất thiết dài 32 byte vì key dài 32 byte không?  
**Trả lời:** Tag đầy đủ dài **32 byte do HmacSHA256**, không phải vì key tình cờ dài 32 byte.

**Câu 3.** Client gửi `kid="admin-prod"`. Server có nên tự động đọc secret và kiểm tra bằng khóa admin?  
**Trả lời:** **Không.** `kid` là dữ liệu không tin cậy; phải giới hạn lookup vào purpose, tenant, môi trường và key trạng thái hợp lệ do server xác định.

**Câu 4.** Ứng dụng lưu riêng khóa dẫn xuất `K_webhook`, không có root. Có thể từ đó suy ra `K_session` nếu HKDF được dùng đúng không?  
**Trả lời:** Trong mô hình mật mã dự kiến, **không thể thực tế suy ra** khóa khác từ một subkey. Nhưng **lộ root** cho phép dẫn xuất tất cả subkey, nên root cần mức bảo vệ cao hơn.

**Câu 5.** Một webhook có tag đúng nhưng timestamp cũ và nonce đã được xử lý. Nhận lại hay từ chối?  
**Trả lời:** **Từ chối** theo chính sách chống replay; MAC hợp lệ không đủ để chấp nhận lại nghiệp vụ.

**Câu 6.** Khi rotate root HKDF, giữ nguyên `info` nhưng dùng root mới. Các subkey có giống bản cũ không?  
**Trả lời:** **Không**, HKDF cho kết quả phụ thuộc root/PRK, salt, info và length. Cần quản lý version và cửa sổ chuyển đổi.

**Câu 7.** Một secret 32 byte được in thành Base64 để đưa vào log. Có an toàn vì người đọc không thấy byte gốc không?  
**Trả lời:** **Không.** Base64 có thể decode trực tiếp thành đúng 32 byte khóa. Log đã làm lộ secret.

**Câu 8.** HMAC hợp lệ từ Partner A chứng minh chắc chắn cá nhân Alice ở Partner A đã ký chưa?  
**Trả lời:** **Không.** Mọi người/hệ thống biết shared key đều có thể tạo MAC; muốn gắn danh tính cụ thể cần cơ chế khóa và xác thực thích hợp.

### Ghi nhớ

**Sinh khóa bằng nguồn ngẫu nhiên mật mã, giữ khóa trong phạm vi quyền tối thiểu, tách khóa thật sự theo mục đích, ràng buộc ngữ cảnh trong những byte được MAC, quản lý key ID và phiên bản, và có quy trình rotate/thu hồi.** HMAC-SHA256 là một primitive hữu ích; toàn bộ quy trình quản lý khóa và xác minh mới quyết định liệu ứng dụng có thực sự chống giả mạo thông điệp hay không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-authentication-code-005">5.5 Thống nhất dữ liệu đầu vào, thứ tự trường và định dạng tag giữa hai bên</a>

<details>
<summary>Xem chi tiết</summary>

# 5.5. Thống nhất dữ liệu đầu vào, thứ tự trường và định dạng tag giữa hai bên

Trong một hệ thống dùng **mã xác thực thông điệp (Message Authentication Code, MAC)**, việc cả hai phía cùng chọn `HmacSHA256` và cùng giữ một khóa bí mật vẫn **chưa đủ** để xác thực thành công. Phía gửi và phía nhận còn phải tính MAC trên **đúng cùng một dãy byte**, theo **cùng quy tắc sắp xếp trường và mã hóa**, rồi thống nhất **cách biểu diễn, truyền và kiểm tra authentication tag**.

Một lỗi tưởng nhỏ như đổi thứ tự hai trường JSON, dùng ký tự xuống dòng khác nhau, ghi số tiền ở định dạng khác, hoặc hiểu nhầm Base64 thường với Base64URL có thể làm hai phía tạo tag khác nhau. Nguy hiểm hơn, quy tắc ghép dữ liệu mơ hồ có thể khiến **hai thông điệp nghiệp vụ khác nhau tạo cùng chuỗi byte trước khi MAC**, dù bản thân HMAC hoàn toàn an toàn.

Mục tiêu của bài học:

- Xác định chính xác những byte phải được xác thực, các trường bắt buộc và ý nghĩa nghiệp vụ của từng trường.
- Thiết kế một định dạng đầu vào **xác định, không nhập nhằng** giữa hai ngôn ngữ/hệ thống.
- Quy định thứ tự trường, UTF-8, độ dài trường, kiểu số, version, domain và cách xử lý missing/null/empty.
- Phân biệt **tag thô dạng byte** với chuỗi hex/Base64/Base64URL trên đường truyền.
- Viết và kiểm tra một hợp đồng HMAC-SHA256 có **test vector tái lập** bằng Java 17+.
- Nhận diện các lỗi dẫn tới MAC mismatch, algorithm/key confusion, replay và thiếu phân quyền.

## 1. MAC tạo tag cho byte, không tạo tag cho “ý nghĩa” trừu tượng

Với khóa đối xứng bí mật `K` và thông điệp ở dạng byte `M`, thao tác có dạng:

\[
T = \operatorname{MAC}_{K}(M)
\]

Đối tác nhận sẽ tự tính `MAC_K(M_received)` rồi kiểm tra kết quả có phù hợp với tag nhận được hay không.

```text
Bên gửi                                         Bên nhận
--------                                        --------
Dữ liệu nghiệp vụ                               Dữ liệu nghiệp vụ
        |                                               |
Canonical encoding (quy tắc chung)            Canonical encoding (cùng quy tắc)
        |                                               |
Đúng dãy byte M                                Đúng dãy byte M'
        |                                               |
MAC(K, M)                                      MAC(K, M')
        |                                               |
Tag bytes T -------- wire encoding --------> Giải mã tag -> T'
                                                        |
                                               So sánh T' với MAC(K, M')
                                                        |
                                               Chỉ xử lý khi mọi kiểm tra đạt
```

Để xác thực thành công một cách có ý nghĩa, hai phía phải thống nhất ít nhất:

1. **Khóa và chính sách sử dụng khóa**: byte khóa nào, `keyId` nào, mục đích nào, ai được quyền giữ khóa và định danh nào gắn với khóa.
2. **Thuật toán và cấu hình**: chẳng hạn `HmacSHA256` cho tag đầy đủ 32 byte; quy tắc truncation nếu có.
3. **Input/schema**: những trường nào phải được bảo vệ (bao gồm tenant, tài nguyên, hành động, số tiền, requestId), thứ tự và kiểu dữ liệu.
4. **Canonical encoding**: đúng byte đầu vào, UTF-8, độ dài, integer endian, xử lý Unicode, JSON và null.
5. **Wire format của tag**: byte thô, hex, Base64 hay Base64URL; padding, độ dài, viết hoa/thường và nơi đặt tag.
6. **Version, ngữ cảnh và cách từ chối**: bản tin thuộc protocol nào; có chống replay, giới hạn thời gian, kiểm tra quyền và xử lý lỗi không.

**Tính xác định của HMAC**: với cùng thuật toán, cùng byte khóa và cùng byte thông điệp, tag sẽ trùng nhau. Nếu tag khác, đừng vội kết luận thuật toán sai; thường nên kiểm tra byte đã đưa vào MAC trước.

### 1.1. HMAC khác hash không khóa

`SHA-256(M)` là digest công khai; bất kỳ ai biết `M` đều có thể tính lại. `HMAC-SHA256(K, M)` sử dụng **bí mật `K`** theo một phép xây dựng MAC chuẩn, cho phép bên cũng giữ khóa kiểm tra tính toàn vẹn và nguồn tạo tag thuộc nhóm biết khóa.

Với khóa HMAC được chia sẻ cho nhiều dịch vụ, một tag đúng chứng minh **bản tin tương thích với khóa chia sẻ**, không xác định duy nhất dịch vụ nào đã tính tag. Muốn gắn với danh tính, bên nhận phải có ánh xạ khóa với principal và kiểm tra quyền nghiệp vụ. MAC không phải chữ ký số dùng public key, không có cơ chế xác minh công khai như chữ ký.

**AEAD** như AES-GCM cũng sinh authentication tag, nhưng tag GCM phụ thuộc cả nonce, ciphertext và AAD theo quy tắc AEAD. Không được lấy tag HMAC thay thế tag GCM, hoặc tự nghĩ “tag là một đoạn text nên hai tag nào cũng tương đương”.

## 2. “Cùng dữ liệu” nhưng byte khác nhau

MAC nhìn **byte**, không hiểu cấu trúc đối tượng, JSON, ý nghĩa số tiền hoặc người dùng. Các cặp hiển thị “gần giống” dưới đây có thể tạo byte khác nhau:

| Cùng ý định nghiệp vụ nhưng khác biểu diễn | Ví dụ | Tác động |
| --- | --- | --- |
| Thứ tự thuộc tính JSON | `{"amount":100,"currency":"USD"}` và `{"currency":"USD","amount":100}` | Nếu ký byte JSON thô, tag khác |
| Khoảng trắng JSON | `{"a":1}` và `{ "a": 1 }` | Byte khác dù object logic tương đương |
| Dấu xuống dòng | LF `\n` và CRLF `\r\n` | Hash/MAC khác |
| Encoding | UTF-8 và UTF-16LE | Byte khác |
| Unicode tổ hợp | `é` (U+00E9) và `e` + U+0301 | Có thể hiển thị giống nhau nhưng UTF-8 khác |
| Số tiền | `1`, `1.0`, `01`, `1e0` | String khác; nếu dùng JSON number còn có khác biệt kiểu/làm tròn |
| Thời gian | `2026-10-31T12:00:00Z` và `2026-10-31T19:00:00+07:00` | Cùng thời điểm nhưng string khác |
| Base64 | `+`/`/`/`=` so với `-`/`_` không padding | Cùng dữ liệu nhị phân có thể thành wire text khác |
| Null/missing/empty | Không có trường, `null`, `""` | Thường là ba trạng thái nghiệp vụ khác nhau |
| URL | `/a%2Fb` và `/a/b`; query order khác | Có thể có semantics khác qua proxy/router |
| HTTP header | Trình tự, viết hoa tên, khoảng trắng, header được gộp | Canonical string có thể khác |

Ví dụ:

```json
{"tenant":"shop-1","amount":1299}
```

và

```json
{
  "amount": 1299,
  "tenant": "shop-1"
}
```

có thể là cùng object nghiệp vụ, nhưng không là cùng chuỗi byte. Nếu phía gửi MAC trên JSON đã format còn phía nhận parse rồi serialize lại theo thứ tự khác, HMAC sẽ không khớp.

Có **hai chiến lược đúng** nhưng phải chọn một cách nhất quán:

- **MAC đúng byte đã gửi:** bên nhận tính trên đúng payload bytes được truyền và không serialize lại trước khi xác minh. Cần quy định rõ lúc nào byte được lấy, ví dụ trước/sau compression hay content transformation. Cách này tránh phải canonicalize lại JSON nhưng MAC vẫn phải bao phủ đầy đủ ngữ cảnh bên ngoài body nếu nghiệp vụ cần.
- **MAC trên canonical representation:** cả hai phía chuyển dữ liệu logic thành đúng một encoding theo schema đã công bố. Chọn binary length-prefix, CBOR deterministic profile hoặc quy tắc canonical JSON phù hợp; cả hai bên phải cùng thực hiện.

Không tự pha trộn hai chiến lược, chẳng hạn sender ký raw JSON còn receiver ký object đã parse.

## 3. Thứ tự trường và cách ngăn nhập nhằng

Một quy tắc nguy hiểm là nối chuỗi trực tiếp:

```text
M = tenantId + requestId

tenantId = "ab"; requestId = "c"   -> "abc"
tenantId = "a";  requestId = "bc"  -> "abc"
```

Hai bản tin **khác nhau** tạo **cùng byte input**. Vì thế chúng sẽ có cùng HMAC với cùng khóa, **không cần phá HMAC hay tìm collision mật mã**. Đây là lỗi định dạng trước khi tính MAC.

Nối trường bằng dấu phân cách đơn giản cũng chưa chắc an toàn nếu dấu ấy có thể xuất hiện trong dữ liệu. Ví dụ:

```text
tenantId + "|" + requestId

("a|b", "c") -> "a|b|c"
("a", "b|c") -> "a|b|c"
```

Những giải pháp tốt:

1. **Độ dài trước trường (length prefix):** ghi độ dài byte cố định 4 byte rồi ghi đúng số byte đó; parser có thể đọc lại ranh giới.
2. **Mã hóa cấu trúc có chuẩn:** dùng deterministic CBOR / canonical JSON profile được chỉ định và kiểm thử chéo.
3. **Delimiter + escape chặt chẽ:** chỉ nếu protocol công bố escape rules một nghĩa và kiểm thử đầy đủ, tránh tự nghĩ escaping.
4. **Trường có kiểu/độ rộng cố định:** integer 64-bit, version byte, boolean 0/1, enum có miền giá trị giới hạn.

### 3.1. Hợp đồng binary cụ thể cho ví dụ payment

Ta chọn một profile minh họa `PAYMENTS-HMAC-V1`, bao gồm các trường theo đúng thứ tự:

| Vị trí | Trường | Encoding | Ghi chú |
| --- | --- | --- | --- |
| 1 | `version` | 1 byte giá trị `0x01` | Cố định theo phiên bản protocol |
| 2 | `domain` | 4-byte length + UTF-8 bytes của `payments.request` | Không cho dùng cùng tag trong protocol khác |
| 3 | `tenantId` | 4-byte length + UTF-8 bytes | Ràng buộc tenant |
| 4 | `requestId` | 4-byte length + UTF-8 bytes | Dùng ở lớp replay/idempotency |
| 5 | `amountMinor` | Integer signed 64-bit, **big-endian**, chính xác 8 byte | 1299 nghĩa là 12,99 đơn vị khi tiền tệ có 2 chữ số thập phân |
| 6 | `currency` | 4-byte length + UTF-8 bytes | Ví dụ `USD`, chuẩn hóa/kiểm tra theo policy |
| 7 | `tag` | **Không** nằm trong MAC input | Tính HMAC sau khi đã serialize 6 trường trên |

Trong đó:
- Mỗi length prefix là unsigned không âm được biểu diễn bằng `writeInt` 4 byte big-endian và bị giới hạn tối đa **65.535 byte** ở ví dụ.
- `writeLong` của Java dùng **8 byte big-endian**. Ngôn ngữ khác phải tuân thủ cùng số byte và signed representation; không gửi số tiền dạng `double` hay chuỗi theo locale.
- Chuỗi được mã hóa bằng `StandardCharsets.UTF_8`. Trong minh họa, không thực hiện Unicode normalization ngầm. Hệ thống thật phải định nghĩa rõ policy chấp nhận/reject/normalize đầu vào trước khi serialize.
- `version` và `domain` làm rõ thông điệp này thuộc dạng “payment request, v1”, tránh cùng một chuỗi byte bị hiểu thành thông điệp loại khác (cross-protocol confusion).
- Các giá trị `tenantId` và `requestId` **phải được phía nhận kiểm tra với danh tính và trạng thái nghiệp vụ đáng tin**, không coi những giá trị client khai báo là tự động có quyền.

Sơ đồ byte:

```text
01
00000010 | UTF-8("payments.request")
00000008 | UTF-8("tenant-A")
00000005 | UTF-8("req-7")
0000000000000513
00000003 | UTF-8("USD")
   |
   +--> HMAC-SHA256(K, all preceding bytes) => 32 tag bytes
```

Độ dài `0x10 = 16` byte cho `payments.request`; `0x08 = 8` cho `tenant-A`; `0x05 = 5` cho `req-7`; `0x0513 = 1299` trong trường số 8 byte.

Cặp input cố định sẽ dùng làm test vector:

```text
version = 1
domain = payments.request
tenantId = tenant-A
requestId = req-7
amountMinor = 1299
currency = USD
key = UTF-8("0123456789abcdef0123456789abcdef")  // only for a public demo

Canonical input (hex):
01000000107061796d656e74732e726571756573740000000874656e616e742d41000000057265712d37000000000000051300000003555344
```

**Không dùng khóa cố định công khai này trong sản phẩm.** Nó là fixture để hai phía có thể so sánh từng byte trong bài học, tương tự một test vector.

### 3.2. Nguyên tắc với dữ liệu thiếu và kiểu dữ liệu

Định nghĩa protocol cần trả lời từng trường:

- Trường **bắt buộc** thiếu thì từ chối trước khi tính/kiểm tra; không tự coi bằng chuỗi rỗng.
- Nếu **nullable** có ý nghĩa, phải thêm byte đánh dấu trạng thái hoặc một encoding quy định rõ khác biệt `absent/null/value`.
- Nếu cho phép **mảng**, phải ghi count và encoding của từng phần tử, công bố thứ tự có nghĩa hay phải sort. Nếu sort, dùng cùng comparator theo byte/chuẩn, không dựa vào locale mỗi máy.
- Nếu cho phép **map**, phải quy định thứ tự key, cách xử lý duplicate key và Unicode. Không dùng thứ tự lặp của `HashMap` làm wire contract.
- Nếu có **số thập phân**, chọn đơn vị nhỏ nhất dạng integer (`amountMinor`) hoặc dạng decimal canonical rõ ràng; không phụ thuộc `BigDecimal.toString()` ở các hệ khác khi chưa có hợp đồng.
- Với **thời gian**, ưu tiên epoch UTC theo đơn vị xác định, chẳng hạn signed int64 milliseconds; nếu chọn ISO 8601, bắt buộc một dạng chính xác cùng fractional digits/timezone.

Dữ liệu ngoài miền hợp lệ phải bị từ chối có kiểm soát. **Canonicalization không thay thế schema validation hoặc kiểm tra quyền.**

## 4. Đối với JSON và HTTP, cần quyết định miền byte được ký

### 4.1. Có thể dùng JSON, nhưng cần một profile cụ thể

Nếu chọn canonical JSON, không chỉ nói chung “sort keys”. Cần công bố cụ thể chuẩn, ví dụ **JSON Canonicalization Scheme (JCS, RFC 8785)** khi phù hợp: quy tắc về property order, serialization của string và number, và các ràng buộc JSON/I-JSON. JSON interop còn cần chú ý độ chính xác số nguyên lớn vượt miền biểu diễn chính xác của IEEE 754, duplicate property, Unicode, escape sequences và kiểu dữ liệu.

Ví dụ truyền thống:

```json
{"currency":"USD","amount":1299,"requestId":"req-7"}
```

Tránh giả định một framework serializer Java hoặc JavaScript bất kỳ sẽ tạo byte giống nhau chỉ vì cả hai cho kết quả JSON có vẻ tương đương. Nếu chọn JCS, dùng implementation tuân thủ chuẩn và test vector giao tiếp. Nếu yêu cầu số nguyên 64 bit chính xác giữa Java và JavaScript, có thể phải biểu diễn theo **chuỗi số dạng chuẩn** hoặc dùng định dạng binary có hỗ trợ số nguyên xác định.

Đặc biệt, khi verify chữ ký/MAC trên raw JSON, parser ứng dụng **không nên chấp nhận các dạng nhập nhằng** (như duplicate property khác cách xử lý) khiến byte được xác thực và object thực thi có ngữ nghĩa khác nhau.

### 4.2. Đối với HTTP request

Khi xác thực API bằng MAC, “ký body” chỉ là một phần. Hợp đồng cần chỉ rõ những thông tin nào ảnh hưởng đến ý nghĩa request:

- Method (`POST` hay `GET`) và path, route, query parameter nào được bao gồm?
- Host, tenant, resourceId, user/service principal, content type và body nào phải bảo vệ?
- Có timestamp, nonce hoặc requestId được bảo vệ bằng MAC không?
- Chính xác body bytes ở lớp nào: sau parsing, trước compression hay sau reverse proxy transformation?
- Nếu một proxy đổi header ordering, rewrite URL hay convert line endings, hai bên có còn dùng cùng canonical representation không?

Không ký mọi header không kiểm soát rồi hy vọng client/server đồng ý, vì proxy có thể thêm/bớt header. Cũng không ký thiếu dữ liệu có ý nghĩa: nếu MAC chỉ bảo vệ body nhưng route chuyển từ `/orders` sang `/refunds` mà không được ràng buộc, bên nhận có thể hiểu request theo một hành động khác.

Với HTTP, nên cân nhắc dùng chuẩn request-signature phù hợp và công bố rõ canonicalization profile. Nếu thiết kế giao thức nội bộ riêng, phải có fixture từ cả hai phía và test các biến thể proxy/HTTP stack.

## 5. Tag bytes và tag trên đường truyền

Với **HMAC-SHA256 đầy đủ**, kết quả `Mac.doFinal(...)` gồm **32 byte = 256 bit**. Các cách biểu diễn cùng tag:

| Cách biểu diễn | Kích thước đối với tag 32 byte | Đặc điểm |
| --- | --- | --- |
| Raw bytes | 32 byte | Phù hợp binary protocol; không phải UTF-8 text tùy ý |
| Hex lowercase | 64 ký tự ASCII | Dễ đọc/debug, tốn dung lượng gấp đôi raw |
| Base64 chuẩn có padding | 44 ký tự (thường kết thúc `=`) | Dùng ký tự `+` và `/`, có padding |
| Base64URL không padding | 43 ký tự ASCII | Dùng `-` và `_`, thuận tiện URL/header |

Vì **cùng một dãy 32 byte** có nhiều cách mã hóa, hai phía cần thống nhất:

1. Dạng wire được phép (ví dụ Base64URL không padding), đúng bảng chữ cái và quy tắc padding.
2. Case của chuỗi hex; có cho phép chữ hoa không? Nếu có phải quy định nhất quán.
3. Độ dài tag sau giải mã (ví dụ **đúng 32 byte**). Không so sánh độ dài string để thay thế kiểm tra byte.
4. Có cho phép whitespace, xuống dòng, tiền tố `0x` hay ký tự phân cách không? Nên chọn dạng canonical duy nhất và từ chối định dạng khác.
5. Chuỗi tag được truyền ở trường nào, có phần khác cùng tên hoặc duplicate header không, có bị URL/form decoding biến đổi không?
6. Chính sách từ chối tag sai/hết hạn và phản hồi lỗi không tiết lộ chi tiết nội bộ.

**Đừng bao giờ** chuyển raw tag bytes bằng `new String(tagBytes, UTF_8)`: tag là dữ liệu nhị phân không bảo đảm tạo thành UTF-8 hợp lệ. Cũng đừng so sánh chuỗi hex bên gửi với chuỗi Base64 bên nhận: phải decode theo đúng profile trước khi so sánh.

### 5.1. Có nên rút ngắn HMAC tag?

Một số protocol quy định tag bị truncate còn `t` bit để tiết kiệm băng thông. Đây là **quyết định protocol** có hậu quả về độ khó giả mạo; không phải chuyện “cắt vài ký tự Base64 cho gọn”. Nếu truncate, phải quy định chính xác số **bit/byte đầu**, quy tắc encoding, độ dài tối thiểu, giới hạn số lần thử và threat model. Với thiết kế mới, ưu tiên **full 32-byte HMAC-SHA256** khi không có lý do chuẩn hóa đặc biệt để rút ngắn.

Nếu đối thủ có nhiều lần thử online với tag ngắn, cơ hội đoán đúng tăng theo số lần thử. Giới hạn rate, timeout, kiểm tra nonce/replay và xử lý thất bại an toàn vẫn cần thiết. Không chấp nhận client tự quyết định độ dài tag rồi server so sánh bao nhiêu byte client cung cấp.

### 5.2. Kiểm tra tag an toàn

Về nguyên tắc, verify:

1. Kiểm tra framing, format, độ dài, thuật toán/phiên bản được cho phép; từ chối dữ liệu malformed.
2. Xác định khóa đúng từ nguồn tin cậy và cấu hình chính sách; **không tin keyId tự khai như bằng chứng danh tính**.
3. Tự serialize đúng canonical bytes từ dữ liệu được phép xử lý.
4. Tính lại HMAC trên toàn bộ bytes, dùng API so sánh byte theo thời gian ít phụ thuộc nội dung, như `MessageDigest.isEqual(...)`, khi hai digest đã có độ dài mong đợi.
5. Chỉ sau khi MAC đúng mới xét freshness/replay và quyền nghiệp vụ trước khi tạo side effect.

Tránh `String.equals(...)` hoặc `Arrays.equals(...)` làm cơ chế so sánh cuối cùng giữa **tag kỳ vọng và tag bí mật do client gửi**, vì timing của phép so sánh thông thường có thể phụ thuộc nội dung/điểm lệch. Vẫn có các yêu cầu khác về xác thực, xử lý lỗi và mạng; một phép so sánh an toàn không bảo đảm toàn bộ giao thức không rò rỉ thông tin.

## 6. Java 17+: test vector HMAC trên binary canonical encoding

Ví dụ sau cố định thuật toán, thứ tự và format; kết quả HMAC trên một request giống nhau phải tái lập được giữa các hệ triển khai.

Tạo tệp `MacWireDemo.java`:

```java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MacWireDemo {
    record Payment(String tenantId, String requestId, long amountMinor, String currency) {}

    // Public demo key for reproducing a test vector; never use in production.
    private static final byte[] KEY = "0123456789abcdef0123456789abcdef"
            .getBytes(StandardCharsets.UTF_8);

    private static void field(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65_535) throw new IllegalArgumentException("field too long");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static byte[] canonical(Payment p) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeByte(1);                    // protocol version
            field(out, "payments.request");      // domain separation
            field(out, p.tenantId());
            field(out, p.requestId());
            out.writeLong(p.amountMinor());      // 8 bytes, big endian
            field(out, p.currency());
        }
        return buffer.toByteArray();
    }

    static byte[] hmac(Payment p) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
        return mac.doFinal(canonical(p));
    }

    static String base64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static boolean verify(Payment p, String wireTag) throws Exception {
        byte[] received;
        try {
            received = Base64.getUrlDecoder().decode(wireTag);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        if (received.length != 32 || !base64url(received).equals(wireTag)) return false;
        return MessageDigest.isEqual(hmac(p), received);
    }

    public static void main(String[] args) throws Exception {
        Payment original = new Payment("tenant-A", "req-7", 1299, "USD");
        String tag = base64url(hmac(original));
        System.out.println("Input hex: " + HexFormat.of().formatHex(canonical(original)));
        System.out.println("Tag (base64url): " + tag);
        System.out.println("Tag bytes: " + Base64.getUrlDecoder().decode(tag).length);
        System.out.println("Original verified: " + verify(original, tag));
        System.out.println("Amount changed verified: "
                + verify(new Payment("tenant-A", "req-7", 1399, "USD"), tag));
        System.out.println("Tenant changed verified: "
                + verify(new Payment("tenant-B", "req-7", 1299, "USD"), tag));
        System.out.println("Malformed tag verified: " + verify(original, "invalid+tag"));
        System.out.println("Same request replay verified: " + verify(original, tag));
    }
}
```

Biên dịch và chạy:

```bash
javac MacWireDemo.java
java MacWireDemo
```

Stdout chính xác của ví dụ với JDK 17+:

```text
Input hex: 01000000107061796d656e74732e726571756573740000000874656e616e742d41000000057265712d37000000000000051300000003555344
Tag (base64url): nEOzXhbbnYCVX0rn1leWKWA-OR9mZLAraX4FJOfZArU
Tag bytes: 32
Original verified: true
Amount changed verified: false
Tenant changed verified: false
Malformed tag verified: false
Same request replay verified: true
```

**Giải thích những điểm thiết yếu:**

- `canonical()` là **hợp đồng byte**, không phải serialization ngầm của Java record. DataOutputStream ghi theo thứ tự cố định; `writeInt` là length prefix 4 byte, `writeLong` là số 8 byte big-endian.
- `field()` dùng UTF-8 byte length, **không dùng Java `String.length()`** vì `String.length()` đếm UTF-16 code units. Với các ký tự ngoài ASCII, số byte UTF-8 thường khác độ dài chuỗi Java.
- `Mac.getInstance("HmacSHA256")` tính **HMAC-SHA256**; `SecretKeySpec` chỉ biểu diễn key bytes và tên thuật toán, không tự bảo vệ khóa khỏi lộ.
- `base64url()` thống nhất URL-safe alphabet và bỏ padding. Bên nhận decode, kiểm tra đúng 32 byte và mã hóa lại để yêu cầu **canonical wire representation** trước khi so sánh.
- `MessageDigest.isEqual(...)` so sánh **tag bytes**, không so sánh chuỗi biểu diễn hoặc bản tin text.
- Thay số tiền từ 1299 thành 1399, hoặc đổi tenant, tạo input MAC khác nên verify `false`.
- `"invalid+tag"` chứa ký tự `+` không thuộc alphabet Base64URL, bị từ chối. Các dạng wire sai encoding/length cũng phải bị từ chối.
- **Đáng chú ý:** dòng `Same request replay verified: true` là **đúng**. HMAC không tự chống replay. Hai request giống hệt nhau cho cùng tag; server thật phải xử lý `requestId` theo cơ chế freshness, chống replay hoặc idempotency có trạng thái.
- Khóa hardcoded, business validation chưa hoàn chỉnh và không có replay store vì ví dụ nhằm chứng minh **byte contract và tag format**. Trong môi trường thật, lấy khóa từ hệ thống quản lý bí mật, kiểm tra miền giá trị đầu vào và áp quyền/giới hạn thời gian.

### 6.1. Cách tạo test vector cho hệ thống thứ hai

Gửi cho đội implement bằng ngôn ngữ khác **đầy đủ**:

1. Phiên bản protocol và định dạng byte ở bảng phần 3, gồm byte order và length prefix.
2. Dữ liệu đầu vào đúng giá trị: `tenant-A`, `req-7`, integer `1299`, `USD`.
3. Khóa demo đúng **32 ASCII byte**, không phải hex-decoded key: `0123456789abcdef0123456789abcdef`.
4. Canonical input ở dạng hex: cả chuỗi phải khớp chính xác trước khi tính HMAC.
5. Tag HMAC-SHA256 ở dạng Base64URL **không padding**: `nEOzXhbbnYCVX0rn1leWKWA-OR9mZLAraX4FJOfZArU`.
6. Test âm: đổi amount/tenant, sai key, sai version, đảo thứ tự trường, tag sai format và tag sai độ dài.

Khi một bên không tạo đúng tag, bước đầu tiên là so sánh **hex của canonical bytes**, rồi mới xét key bytes, thuật toán và tag encoding. Không nên “sửa cho chạy” bằng cách trim, sort hoặc chuyển Base64/hex ngầm trên server.

### 6.2. Cùng data model nhưng khác thứ tự = khác MAC input

Giả sử một client viết các trường theo thứ tự `tenantId, requestId, currency, amountMinor`, trong khi server viết `tenantId, requestId, amountMinor, currency`. Dù chúng có cùng giá trị nghiệp vụ, byte sẽ khác nhau; tag từ client không thể xác minh theo quy tắc server.

Tương tự, nếu client ghi length prefix 4 byte theo little-endian còn server đọc big-endian, parser có thể hiểu độ dài sai hoặc xác minh HMAC trên byte khác. **Endian là một phần của giao thức**, không phải chi tiết để runtime tự chọn.

Không sửa vấn đề bằng việc cho server thử nhiều thứ tự, nhiều kiểu encoding hoặc nhiều thuật toán đến khi có một tag đúng. Cơ chế “thử mọi cách” có thể mở ra các biểu diễn nhập nhằng và làm mất ý nghĩa version/security policy.

## 7. Ranh giới giữa canonicalization, integrity, identity và replay

### 7.1. MAC chứng minh những byte được bảo vệ, không chứng minh mọi điều

Tag chỉ có thể bảo vệ **các trường thực sự đã đưa vào input**. Nếu amount được ký nhưng currency không ký, đối thủ có thể tìm cách đổi `USD` sang tiền tệ khác mà tag không phát hiện nếu server không ràng buộc currency bằng nơi khác.

Ví dụ trong hệ thống nhiều tenant:
- `tenantId` phải nằm trong input MAC **và** server phải kiểm tra tenant đó được phép dùng khóa/principal hiện tại.
- `resourceId` hoặc route định hướng quyền cũng nên được ràng buộc nếu chúng ảnh hưởng đến nghiệp vụ.
- Các thành phần như `keyId` và `algorithm` nếu truyền trong envelope phải đi theo profile tin cậy: keyId dùng tra khóa đã cấp quyền, algorithm được lựa chọn bằng allowlist/chính sách server; không cho client ép giải mã/verify bằng thuật toán khác.
- `version` phải được xác thực hoặc nằm trong signed/MAC input để ngăn đổi cách diễn giải payload mà vẫn giữ tag.

**Dữ liệu được bảo vệ toàn vẹn chưa chắc là dữ liệu đáng tin về nghiệp vụ.** Nếu client hợp lệ dùng khóa HMAC nhưng không có quyền thanh toán cho tenant khác, MAC vẫn có thể đúng; authorization phải độc lập kiểm tra quyền.

### 7.2. MAC hợp lệ không tự chứng minh request là mới

Hình dung một lệnh chuyển tiền có cùng `tenantId`, `requestId`, `amountMinor` và tag được gửi hai lần. Cả hai lần có thể vượt qua HMAC. Server phải kiểm tra request đã được xử lý hay chưa.

Các cơ chế thường phối hợp:
- **Request ID/idempotency key:** ghi nhận trong kho trạng thái với phạm vi principal/tenant; nhiều lần gửi cùng lệnh trả kết quả đã xử lý hoặc bị từ chối theo hợp đồng.
- **Timestamp + allowed clock skew:** đặt trong MAC input; từ chối request quá hạn nhưng timestamp một mình không ngăn replay bên trong cửa sổ thời gian.
- **Nonce hoặc sequence number:** đưa vào MAC input; receiver theo dõi tính duy nhất/thứ tự phù hợp (và xử lý retry/chạy nhiều instance).
- **Token dùng một lần hoặc challenge-response** khi workflow cần bảo đảm freshness mạnh hơn.

Trong hệ thống phân tán, replay store cần atomicity/consistency; nếu hai server xử lý đồng thời một `requestId` mà chỉ kiểm tra bằng cache cục bộ, lệnh có thể chạy hai lần dù MAC đúng.

## 8. Versioning, rotation và tương thích nhiều phiên bản

Giao thức MAC phải có **định danh phiên bản rõ ràng**. Khi thêm trường `merchantId`, đổi precision của `amountMinor` hoặc chuyển từ text delimiter sang length-prefix, cần tạo profile mới chứ không âm thầm thay encoding của v1.

Ví dụ envelope để mô tả thành phần truyền, không phải byte contract cuối cùng:

```json
{
  "version": 1,
  "keyId": "payments-prod-2026-10",
  "tenantId": "tenant-A",
  "requestId": "req-7",
  "amountMinor": 1299,
  "currency": "USD",
  "tag": "nEOzXhbbnYCVX0rn1leWKWA-OR9mZLAraX4FJOfZArU"
}
```

Tag ở trên là **test vector cho khóa demo, domain và canonical bytes của phần 6**, không phải tag do khóa production tương ứng với ví dụ `keyId` tạo. Trường `keyId` trong envelope chỉ minh họa metadata định tuyến; trong một profile production có key rotation, phải công bố cách `keyId` được bind vào input MAC và cách receiver ánh xạ nó tới đúng khóa tin cậy. **Không dùng envelope minh họa làm yêu cầu triển khai nguyên xi.**

Khi chuyển phiên bản:
1. Ghi hồ sơ độc lập cho v1/v2: algorithms, fields, byte order, domain, tag length, encoding.
2. Server chỉ chấp nhận các phiên bản được policy cho phép; xác thực version trong MAC input.
3. Map `keyId` tới khóa có scope, chủ thể, trạng thái và thuật toán được phép; hạn chế đợt chấp nhận đồng thời nhiều key cũ.
4. Duy trì tập test vectors cho mỗi version và test các trường hợp không hợp lệ.
5. Có thời điểm kết thúc support version/khóa cũ; tránh fallback tự động khi verify thất bại.

Xoay khóa và nâng cấp format là hai việc khác nhau. Một payload v1 có thể còn cần khóa v1 trong cửa sổ tương thích, nhưng server không nên nới lỏng canonicalization để cho “hầu hết client cũ chạy được”.

## 9. Kiểm thử giao tiếp hai bên: kiểm tra gì?

Tối thiểu nên có test matrix theo nhóm:

| Nhóm | Tình huống | Kỳ vọng |
| --- | --- | --- |
| Golden vector | Cùng key, cùng input, cùng version | Input hex và tag khớp 100% |
| Trường/thứ tự | Đảo `tenantId` và `requestId`, đổi amount/currency | Tag không xác minh theo profile v1 |
| Ranh giới | `("ab","c")` vs `("a","bc")` | Canonical bytes khác |
| Unicode | NFC/NFD, emoji, chuỗi rỗng, ký tự kết hợp | Theo policy; length tính bằng byte UTF-8 |
| Encoding | LF/CRLF, UTF-8/UTF-16, BOM | Từ chối/không khớp nếu không đúng profile |
| Số | Negative, overflow, 1.0 vs 1, big-/little-endian | Parser/schema rõ ràng; không chuyển kiểu âm thầm |
| Tag format | Hex thay Base64URL, padding, ký tự lạ, whitespace, sai length | Từ chối |
| Key/algorithm | Sai key, keyId revoked, thuật toán không cho phép | Từ chối |
| Protocol | Sai version, domain hoặc message type | Từ chối |
| Replay | Gửi lại request hợp lệ | Từ chối hoặc trả kết quả idempotent theo nghiệp vụ |
| Authorization | HMAC đúng nhưng tenant/resource không thuộc principal | Từ chối |
| Concurrency | Hai request cùng ID tới hai instance | Không tạo side effect hai lần |

**Test giao tiếp chéo ngôn ngữ:** ít nhất một bộ golden vector do hai implementation độc lập tính. Không chỉ kiểm tra rằng client Java tự verify được MAC do chính Java tạo: test này sẽ bỏ sót lỗi mà Java và đối tác dùng cách serialize khác nhau.

### 9.1. Phân tích từng byte khi gặp mismatch

Trình tự debug thực dụng, giữ bí mật khóa và dữ liệu nhạy cảm:

1. Xác nhận hai phía thực sự dùng **cùng profile** (version, domain, algorithm, tag length, encoding).
2. So sánh schema/validation và các trường được chọn đưa vào MAC; lưu ý route, tenant và requestId.
3. So sánh **độ dài và canonical input bytes** trong môi trường thử nghiệm dùng fixture không nhạy cảm. Nếu bytes khác, tìm điểm khác đầu tiên: delimiter, UTF-8, endianness, thứ tự, newline, số, escape.
4. Nếu bytes trùng, kiểm tra cách nạp khóa (raw text bytes so với hex/Base64 decoded bytes), key version và cách dẫn xuất khóa. **Không log production key** để debug.
5. Nếu MAC bytes trùng mà tag text không trùng, kiểm tra Base64/Base64URL/hex, padding, chữ hoa/thường và whitespace.
6. Nếu tag đúng nhưng request bị từ chối, xem chính sách thời gian, replay/idempotency, mapping identity, quyền và validation; đừng bỏ qua kiểm tra chỉ để “qua test”.

Trong production, chỉ ghi log thông tin cần thiết để định vị lỗi như profile id, độ dài input, tên nhóm trường, mã lỗi, correlation ID. Không log key, tag nhạy cảm khi không cần, body chứa dữ liệu cá nhân hoặc dữ liệu thanh toán. Hash của dữ liệu nhạy cảm cũng có thể lộ thông tin nếu đầu vào ít entropy; không coi logging digest là tự động an toàn.

## 10. Các lỗi điển hình và cách sửa

| Lỗi | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Client ký JSON gốc, server ký JSON đã parse rồi serialize lại | Byte khác do thứ tự/spacing | Cùng ký raw bytes hoặc áp canonical profile |
| Nối `tenantId + requestId` mà không phân cách | Hai cặp trường khác nhau cùng input | Length prefix/serialization không nhập nhằng |
| Dùng delimiter mà không escape | `a\|b` gây nhập nhằng | Length prefix hoặc quy tắc escape chuẩn |
| Dùng `String.length()` ghi độ dài cho chuỗi UTF-8 | Độ dài UTF-16 không bằng byte length | Lấy `value.getBytes(UTF_8).length` |
| Một phía ghi 1299 là 4 byte little-endian, phía kia ghi 8 byte big-endian | Kiểu và thứ tự byte khác | Thống nhất width, signedness, endian |
| String có Unicode tổ hợp khác | Input bytes khác | Chốt chính sách normalization và canonical encoding |
| Một phía nhận `null` là trống, phía kia bỏ trường | Schema khác | Định nghĩa absent/null/empty riêng |
| Server nhận cả truncated tag tùy độ dài client gửi | Tag length không ràng buộc | Kiểm tra độ dài cố định và profile |
| Phía gửi xuất hex, phía nhận giải Base64 | Wire tag sai codec | Một encoding canonical và test vector |
| So sánh tag bằng string thông thường | Có thể tạo timing leak | Decode, kiểm tra length, so sánh bytes bằng API phù hợp |
| Cho phép client tự chọn `alg` hoặc tự gán `keyId` | Algorithm/key confusion | Server allowlist, registry khóa và scope |
| Chỉ ký body, không ký tenant/action | MAC không bao phủ nghiệp vụ | Bind đủ context vào input, kiểm tra quyền |
| Nhận tag đúng nhưng xử lý lại giao dịch | Thiếu replay/idempotency | Trạng thái atomic theo principal/requestId |
| Chọn lại nhiều schema tới khi một tag pass | Fallback nhập nhằng | Version chặt, chỉ thử cấu hình được định nghĩa |
| Test chỉ toàn positive case | Bỏ lọt tamper, replay, encoding lỗi | Test vector âm và test giao tiếp chéo nền tảng |

## 11. Checklist thiết kế một profile MAC có thể triển khai

Trước khi hai đội bắt đầu code, viết thành văn bản **một hợp đồng kiểm chứng được**:

- **Purpose:** giao thức xác thực loại bản tin nào, danh tính nào, tenant nào, cấp quyền nào; giới hạn những bảo đảm của MAC.
- **Version/domain:** byte version, domain separation, algorithm, danh sách phiên bản được phép.
- **Keys:** nguồn sinh/lưu, độ dài byte, danh tính sở hữu, vòng đời, `keyId`, cách chọn key an toàn và policy rotation.
- **Input fields:** danh sách có thứ tự, bắt buộc/tùy chọn, phạm vi giá trị và các trường nghiệp vụ được bảo vệ.
- **Canonical encoding:** UTF-8/Unicode policy, length prefix, integer width/endian, decimal/time, null/missing/empty, thứ tự map/list, JSON hoặc binary format.
- **Tag:** raw length, algorithm, truncation policy, encoding, padding/case và vị trí trong request.
- **Verification:** chuẩn hóa/validate ở đâu, fail closed khi sai tag, so sánh an toàn, duplicate field/header xử lý ra sao.
- **Freshness và quyền:** timestamp/nonce/requestId, replay store, idempotency, binding với principal và authorization từng operation.
- **Conformance:** golden vectors được chạy bởi cả hai phía, negative cases, hành vi version/rotation/error có tài liệu.

### Câu hỏi ôn tập

**1. Hai phía đều dùng đúng HMAC-SHA256 và cùng khóa nhưng tag khác. Có phải HMAC bị lỗi?**

Không. Hãy kiểm tra canonical input bytes trước: serialization, trường, thứ tự, UTF-8, số, newline, version. Sau đó mới kiểm tra key bytes và wire tag encoding.

**2. Vì sao ký `tenantId + requestId` không có delimiter là nguy hiểm, ngay cả khi HMAC vẫn rất mạnh?**

Vì các cặp giá trị khác nhau có thể tạo **cùng byte đầu vào** (ví dụ `ab`+`c` và `a`+`bc`). Bản tin khác nhau theo nghiệp vụ lại có MAC bằng nhau theo thiết kế; lỗi ở encoding chứ không phải tấn công HMAC.

**3. Khi hai trường JSON đổi thứ tự, MAC có bắt buộc giống nhau không?**

Nếu MAC tính trên raw JSON bytes thì **khác**. Nếu cả hai dùng đúng một canonical JSON profile đưa về cùng bytes thì **giống** cho dữ liệu được định nghĩa là tương đương. Không có hành vi ngầm định độc lập với profile.

**4. Một tag HMAC-SHA256 đầy đủ dài bao nhiêu? Chuỗi Base64URL không padding dài bao nhiêu?**

Raw tag dài **32 byte**. Với 32 byte, Base64URL không padding dài **43 ký tự ASCII**. Chuỗi hex tương ứng dài **64 ký tự**.

**5. Vì sao server vẫn phải kiểm tra requestId sau khi MAC hợp lệ?**

Vì attacker có thể gửi lại **nguyên request và tag**. HMAC không phát hiện tính mới; cần chống replay/idempotency có trạng thái và kiểm tra quyền nghiệp vụ.

**6. Có thể chỉ kiểm tra HMAC đúng rồi cho tenant của payload toàn quyền không?**

Không. Một bên có khóa có thể tạo tag hợp lệ cho dữ liệu mà họ không có quyền thực hiện. Server phải ràng buộc khóa với principal tin cậy và kiểm tra authorization của principal trên tenant/action/resource cụ thể.

**7. Trong ví dụ Java, vì sao cần dùng `writeInt(bytes.length)`, không dùng `writeInt(value.length())`?**

`DataOutputStream` ghi length theo byte để parser đọc đúng ranh giới. `String.length()` đếm UTF-16 code units và không đồng nhất với số byte UTF-8 khi có ký tự ngoài ASCII.

**8. Muốn thêm trường mới vào thông điệp MAC, có nên thử cả format cũ và format mới cho từng request đến khi verify thành công?**

Không. Phải công bố version/schema mới, xác thực version trong MAC input, kiểm soát chính sách chấp nhận. Thử nhiều cách tùy tiện dễ gây nhập nhằng và suy yếu protocol.

## 12. Kết luận

Một MAC chỉ xác thực **đúng những byte mà hai bên đã thống nhất**. Độ mạnh của HMAC-SHA256 không bù được việc ghép trường nhập nhằng, serialize JSON khác nhau, mất field nghiệp vụ, nhầm tag encoding hay tin một request hợp lệ được phép thực thi lại.

Hợp đồng liên thông cần rõ từ **ngữ nghĩa dữ liệu → thứ tự/kiểu trường → canonical byte encoding → thuật toán/khóa → raw tag → wire tag → xác minh và chính sách ứng dụng**. Một test vector ghi đủ **key demo, canonical input hex và expected tag** là cách nhanh nhất để chứng minh hai phía hiểu giống nhau trước khi xử lý dữ liệu thật.

### Tài liệu tham khảo

- RFC 2104 — *HMAC: Keyed-Hashing for Message Authentication*: https://www.rfc-editor.org/rfc/rfc2104
- RFC 4231 — *Identifiers and Test Vectors for HMAC-SHA-224, HMAC-SHA-256, HMAC-SHA-384, and HMAC-SHA-512*: https://www.rfc-editor.org/rfc/rfc4231
- RFC 8785 — *JSON Canonicalization Scheme (JCS)*: https://www.rfc-editor.org/rfc/rfc8785
- Java SE 17, `javax.crypto.Mac`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Mac.html
- Java SE 17, `java.io.DataOutputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutputStream.html
- Java SE 17, `java.util.Base64`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Base64.html
- Java SE 17, `java.security.MessageDigest`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-authentication-code-006">5.6 Kiểm tra tag, dữ liệu bị sửa, sai khóa và giới hạn cắt ngắn tag</a>

<details>
<summary>Xem chi tiết</summary>

# 5.6 Kiểm tra tag, dữ liệu bị sửa, sai khóa và giới hạn cắt ngắn tag

Một ứng dụng Java nhận được thông điệp cùng một chuỗi byte được gọi là **tag**. Để quyết định có tin nội dung hay không, ứng dụng phải tính hoặc xác minh tag theo **đúng thuật toán, đúng khóa, đúng bytes, đúng tham số và đúng độ dài**. Nếu bỏ qua một trong các điều kiện đó, dữ liệu bị sửa có thể được chấp nhận hoặc thông điệp hợp lệ bị từ chối.

Bài này tập trung vào ba tình huống: (1) dữ liệu, tag hoặc metadata bị sửa; (2) bên xác minh sử dụng sai khóa; (3) tag bị cắt ngắn hoặc chương trình chấp nhận độ dài tag không đúng chính sách. Những tình huống này gặp trong HMAC của webhook, thông điệp giữa các dịch vụ, AES-GCM bảo vệ dữ liệu lưu trữ và các giao thức xác thực thông điệp khác.

Sau bài học, bạn cần phân biệt được **tag của MAC** với **tag của mã hóa có xác thực (AEAD)**; hiểu giới hạn của mỗi tag; triển khai kiểm tra bằng JCA/JCE; viết negative test cho dữ liệu sai, khóa sai và tag thiếu byte; và thiết kế quy tắc độ dài tag không cho phía gửi tự ý làm yếu kiểm tra.

## 1. Tag là gì? Tag chứng minh được điều gì?

### 1.1. MAC tag: dùng bí mật chung để xác thực thông điệp

Với **Message Authentication Code (MAC)**, bên gửi và bên nhận cùng nắm một khóa bí mật. Bên gửi tính tag từ khóa và thông điệp; bên nhận tính lại trên **chính chuỗi byte nhận được** rồi kiểm tra.

~~~text
Bên gửi                          Bên nhận
--------                         --------
Khóa K + thông điệp M            Khóa K + thông điệp M'
          |                                 |
          v                                 v
      MAC(K, M)                        MAC(K, M')
          |                                 |
       tag T ---------------------->  expected T'
                                             |
                                             v
                                kiểm tra độ dài + so khớp tag
                                             |
                               hợp lệ / từ chối thông điệp
~~~

Nếu dùng thuật toán MAC an toàn, khóa bí mật được bảo vệ và quy trình kiểm tra chính xác, tag hợp lệ cho biết nội dung đã được tạo bởi một chủ thể **biết khóa chung**, và nội dung đó không bị sửa theo cách vượt qua cơ chế MAC. Vì hai bên cùng giữ khóa, HMAC không cho bên thứ ba biết chính xác bên nào đã tạo tag.

**HmacSHA256** trong JCA tạo kết quả đầy đủ **32 byte = 256 bit**. Một giao thức có thể chọn một đoạn đầu cố định của tag theo thiết kế và giới hạn bảo mật, nhưng không để client tùy ý đưa độ dài cần so sánh.

### 1.2. AEAD tag: kiểm tra dữ liệu mã hóa trước khi sử dụng

Với **Authenticated Encryption with Associated Data (AEAD)** như AES-GCM, bên mã hóa dùng khóa, IV/nonce, plaintext và có thể cả AAD để tạo **ciphertext cùng authentication tag**. Bên giải mã phải kiểm tra tag bằng chính thuật toán AEAD. Nếu tag không hợp lệ, thao tác giải mã bị từ chối.

~~~text
AES-GCM encrypt:
  key + IV + plaintext + AAD
              |
              v
       ciphertext || tag

AES-GCM decrypt:
  key + IV + ciphertext || tag + AAD
              |
              v
       kiểm tra tag bằng Cipher.doFinal()
              |
       thành công: plaintext đã xác thực
       thất bại: không sử dụng plaintext
~~~

Trong JCE, **Cipher.getInstance("AES/GCM/NoPadding")** và **GCMParameterSpec** lựa chọn transformation và tag length; **Cipher.doFinal** kiểm tra authentication tag khi giải mã. Khi xác thực thất bại thường nhận **AEADBadTagException**. Không dùng MessageDigest.isEqual để tự thay thế phép xác minh GCM bên trong Cipher.

### 1.3. Phân biệt với SHA-256, chữ ký số và checksum

| Cơ chế | Có khóa bí mật không? | Kiểm tra được gì? | Không tự giải quyết gì? |
| --- | --- | --- | --- |
| SHA-256 thuần | Không | Dấu vân tay dữ liệu; hữu ích khi giá trị chuẩn đến từ nguồn tin cậy | Người sửa thông điệp có thể tính lại hash; không xác thực người gửi. |
| CRC/checksum | Không | Phát hiện một số lỗi truyền dẫn/ngẫu nhiên | Không chống đối thủ chủ động sửa dữ liệu. |
| HMAC-SHA256 | Có, khóa chung | Tính toàn vẹn và xác thực bên biết khóa | Không mã hóa nội dung, không chống replay, không quy trách nhiệm riêng một bên giữ khóa. |
| AES-GCM tag | Có, khóa AEAD | Xác thực ciphertext và AAD, kết hợp bảo mật plaintext | Không kiểm tra quyền người dùng, không ngăn phát lại bản mã hợp lệ. |
| Chữ ký số | Khóa riêng để ký, khóa công khai để kiểm tra | Tính toàn vẹn và nguồn ký khi public key được xác thực | Không tự giữ bí mật nội dung và không thay thế kiểm tra quyền. |

**Một tag hợp lệ không có nghĩa yêu cầu nghiệp vụ được phép thực hiện.** Nó chỉ chứng minh những thuộc tính mật mã cụ thể theo khóa, thuật toán và bytes đã bảo vệ. Ví dụ webhook thanh toán có HMAC đúng vẫn có thể là webhook đã xử lý trước đó hoặc một yêu cầu bị phát lại.

## 2. Điều kiện tiên quyết để kiểm tra tag đúng

Việc so sánh tag chỉ có ý nghĩa khi cả hai bên thống nhất một **hợp đồng mật mã**.

| Thành phần | Điều phải thống nhất | Hệ quả khi sai |
| --- | --- | --- |
| Thuật toán | HmacSHA256, AES-GCM, GMAC hay thuật toán được giao thức quy định | Tính ra tag khác hoặc không thể khởi tạo API. |
| Khóa và phiên bản | Đúng secret/key ID, đúng quyền sử dụng, đúng phiên bản sau rotation | Tag không khớp; đôi khi báo sai khóa với thông điệp hợp lệ. |
| Bytes đầu vào | UTF-8, thứ tự trường, dấu cách, xuống dòng, canonicalization, raw body | Cùng ý nghĩa nghiệp vụ nhưng tag khác. |
| Metadata xác thực | Loại thông điệp, tenant, phiên bản, timestamp, nonce, AAD | Kẻ tấn công có thể sửa phần không được tag bảo vệ. |
| Định dạng tag | Raw bytes, hex, Base64/Base64url, vị trí tag, quy tắc padding | Giải mã nhầm, mất byte 0 hoặc sai độ dài. |
| Độ dài tag | Chính sách cố định theo phiên bản giao thức/khóa | Cắt ngắn nhầm gây lỗi; tự động giảm độ dài gây rủi ro giả mạo. |
| AEAD IV/nonce | IV tương ứng với bản mã, độ dài và quy tắc duy nhất theo khóa | Sai tag hoặc phá vỡ bảo đảm an toàn khi tái sử dụng IV. |
| Thứ tự xử lý | Parse → kiểm tra chính sách → xác thực → xử lý nghiệp vụ | Tin dữ liệu chưa xác thực hoặc trả lỗi lộ thông tin. |

Đặc biệt, MAC được tính trên **byte**, không trên một đối tượng Java trừu tượng. Hai chuỗi JSON có cùng các field nhưng thứ tự field, khoảng trắng, escape, Unicode hay ký tự xuống dòng khác nhau có thể cho tag khác nhau. Với webhook, nếu tài liệu nhà cung cấp yêu cầu HMAC trên **raw request body**, hãy giữ raw bytes thay vì parse JSON rồi serialize lại.

Tránh xây payload bằng cách ghép các field tùy ý như "a" + "bc" và "ab" + "c": hai bộ field khác nhau có thể cho cùng byte sequence. Hãy dùng serialization xác định, tiền tố độ dài hoặc định dạng canonical được giao thức công nhận.

## 3. Quy trình xác minh an toàn

Một pipeline kiểm tra tag nên có thứ tự dễ kiểm chứng:

1. **Nhận dữ liệu như dữ liệu chưa tin cậy.** Giới hạn kích thước request, kiểm tra định dạng envelope, chỉ nhận version/algorithm/key ID trong danh sách cho phép.
2. **Giải mã biểu diễn tag một lần** theo quy tắc cố định (ví dụ Base64url). Giải mã lỗi, thiếu field hoặc dữ liệu rác phải bị từ chối; không tự “sửa” tag để được một giá trị hợp lệ.
3. **Kiểm tra chính sách độ dài trước khi so khớp.** Với HMAC-SHA256-128, tag phải đúng **16 byte sau decode**; không dùng số ký tự Base64/hex như số byte.
4. **Chọn khóa đáng tin cậy** theo phiên bản, tenant hoặc đối tác được cấu hình. Một key ID từ bên ngoài không được phép mở khóa tùy ý hoặc thay đổi thuật toán sang dạng yếu.
5. **Chuẩn bị đúng bytes đã được bảo vệ.** Với HMAC đó là message theo đặc tả; với AES-GCM phải cung cấp đúng IV, AAD, ciphertext và tag.
6. **Xác minh bằng primitive phù hợp.** Với HMAC tự tính tag theo chính sách và so sánh hạn chế timing leak; với AEAD để **Cipher.doFinal** kiểm tra tag.
7. **Nếu thất bại, từ chối trước khi dùng nội dung.** Không fallback từ tag dài sang tag ngắn, không trả plaintext sau khi GCM thất bại, không tự thử nhiều cách canonicalization cho tới khi khớp.
8. **Nếu thành công, tiếp tục kiểm tra freshness, replay, quyền và nghiệp vụ.** Tag không tự chứng minh giao dịch chưa được xử lý, tài nguyên thuộc người gọi hay timestamp còn hạn.

~~~text
Nhận message/tag
    |
    v
Kiểm tra format + version + tag length + key ID
    |
    +-- sai --> REJECT
    |
    v
Chọn khóa hợp lệ + chuẩn bị bytes/AAD chính xác
    |
    v
Verify MAC hoặc AEAD tag
    |
    +-- sai --> REJECT (không dùng nội dung)
    |
    v
Kiểm tra replay / timestamp / authorization / idempotency
    |
    +-- sai --> REJECT
    |
    v
Thực hiện nghiệp vụ
~~~

**Không có “xác thực một nửa”.** Phải hoàn tất toàn bộ xác minh trước khi chấp nhận hoặc thực thi một dữ liệu/thao tác được bảo vệ.

## 4. Dữ liệu bị sửa: những dạng sửa nào phải được phát hiện?

Giả sử bên gửi tính HMAC trên bytes tương ứng với thông điệp **order=42;amount=250000**. Sau đó có kẻ đổi số tiền, thay order ID, sửa timestamp hoặc thay chính tag.

| Thay đổi trên đường truyền | Kết quả mong đợi | Lưu ý |
| --- | --- | --- |
| Thay một byte thuộc message được HMAC bảo vệ | Từ chối | Tính lại bằng khóa đúng sẽ khác với tag đã nhận, trừ khả năng giả mạo cực nhỏ theo độ dài tag. |
| Thay chính một byte tag | Từ chối | So sánh tag phải kiểm tra **toàn bộ độ dài được quy định**. |
| Thay metadata được gắn vào HMAC/AAD | Từ chối | Nếu metadata không nằm trong phạm vi xác thực, tag **không bảo vệ** trường đó. |
| Đổi charset/serialization giữa hai bên | Từ chối tag, dù người dùng thấy nội dung “giống nhau” | Đây có thể là lỗi giao thức, không nhất thiết có tấn công. |
| Thay IV/ciphertext/AAD/tag của AES-GCM | Từ chối giải mã | Phải để AEAD kiểm tra tag; sai IV hoặc khóa cũng có thể gây lỗi tương tự. |
| Gửi lại nguyên thông điệp và tag cũ | Tag vẫn có thể hợp lệ | Chống replay cần timestamp, nonce, transaction ID và trạng thái nghiệp vụ, được bảo vệ bằng tag khi có liên quan. |

**Ranh giới bảo vệ phải trùng ranh giới tin cậy.** Nếu HMAC chỉ bao phủ body mà hệ thống lại tin vào header tenant-id không được bảo vệ, bên trung gian có thể đổi tenant-id mà tag vẫn đúng. Với AEAD, thông tin không cần mã hóa (như record type, version/tenant) nhưng cần xác thực có thể đặt trong **AAD** theo quy tắc chính xác.

### Phân biệt phát hiện sửa đổi và phục hồi nội dung

Xác minh tag trả lời **“nội dung này có hợp lệ theo khóa/giao thức không?”**, chứ không tự sửa nội dung bị hỏng. Khi kiểm tra thất bại, hệ thống thường yêu cầu truyền lại một thông điệp đúng, hoặc báo lỗi có kiểm soát. Không bỏ qua lỗi để “cứ xử lý tạm” vì thông điệp vẫn đọc được một phần.

## 5. Sai khóa: ý nghĩa và các nguyên nhân thường gặp

Dùng một khóa khác với khóa của bên tạo tag dẫn tới kết quả HMAC khác hoặc tag AEAD không hợp lệ với xác suất áp đảo. Dù triệu chứng giống bị sửa dữ liệu, **sai khóa không đồng nghĩa đã có tấn công**: cấu hình, xoay khóa, alias keystore hoặc chọn nhầm tenant cũng có thể là nguyên nhân.

Các trường hợp cần kiểm tra:

- **Key ID sai:** message ghi key-v2 nhưng verifier lấy key-v1; hoặc cơ chế lookup trả khóa của khách hàng khác.
- **Khóa đúng ID nhưng khác bytes:** hai môi trường có secret khác nhau, mã hóa/giải mã Base64 secret sai, copy có thêm newline.
- **Rotation không đồng bộ:** sender đã dùng khóa mới, receiver chưa phân phối/chấp nhận khóa mới theo chính sách.
- **Phân biệt mục đích khóa:** một khóa không nên dùng lẫn cho HMAC, mã hóa và các giao thức khác nếu không có thiết kế derivation/domain separation an toàn.
- **Key handle/provider:** HSM/KMS có thể quản lý khóa qua alias hoặc handle; khóa không xuất được không có nghĩa bị mất, nhưng verifier phải chọn đúng khóa cho đúng phép toán.
- **Khóa sai thuật toán hoặc kích thước:** API có thể ném InvalidKeyException ngay tại init, thay vì đợi bước so khớp tag.

**Phản ứng của hệ thống:** công khai thông điệp lỗi chung như “xác thực thông điệp không thành công”; bên vận hành ghi log an toàn gồm key ID hoặc version đã được phép tiết lộ nội bộ, request ID và loại lỗi kỹ thuật, **không log secret, plaintext nhạy cảm hoặc raw token**.

### Xoay khóa mà vẫn xác minh dữ liệu cũ

Một envelope có thể kèm **key ID/version** nằm trong giao thức đã công bố. Server ánh xạ ID này tới **tập khóa được phép sử dụng**, không làm theo URL/path hay tùy chọn thuật toán do người gửi cung cấp. Với dữ liệu lưu trữ, cần giữ khả năng giải mã các bản ghi cũ cho đến khi migrate hoặc hết thời gian lưu trữ. Với webhook, có thể có cửa sổ chuyển đổi giữa khóa cũ và mới, nhưng phải có hạn rõ và không âm thầm thử vô hạn khóa/thuật toán yếu.

Khi sai khóa, không thể “bỏ kiểm tra tag” để giải mã dữ liệu còn dùng được: chính bước kiểm tra đang ngăn dữ liệu chưa xác thực đi vào xử lý nghiệp vụ.

## 6. Tag dài bao nhiêu là đủ? Hiểu đúng việc cắt ngắn

### 6.1. Phân biệt cắt ngắn được thiết kế và tag bị mất byte

**Cắt ngắn có chủ đích (truncation)** là khi giao thức quy định ngay từ đầu rằng cả hai phía dùng **t bit đầu** của kết quả MAC đầy đủ, với t cố định và mức an toàn được chấp nhận.

**Tag bị thiếu ngoài ý muốn** là khi dữ liệu bị cắt trong lưu trữ, chuyển đổi, log, Base64, copy/paste, serialize hoặc truyền mạng. Tag thiếu byte **phải bị từ chối**, không được xem như một phiên bản bảo mật có độ dài thấp hơn.

Ví dụ với HMAC-SHA256:

| Chính sách | Kích thước nhận | Cách xác minh |
| --- | --- | --- |
| HMAC-SHA256 đầy đủ | 32 byte (256 bit) | Tính đủ 32 byte và so khớp đủ 32 byte. |
| HMAC-SHA256-128 do giao thức quy định | 16 byte (128 bit) | Tính HMAC đủ 32 byte, lấy **16 byte đầu**, buộc tag nhận **đúng 16 byte**, rồi so sánh. |
| Giao thức 16 byte nhưng nhận 15 byte | 15 byte | **Từ chối ngay**; không so sánh 15 byte đầu hoặc tự đổi chính sách. |
| Giao thức 16 byte nhưng nhận 0 byte | 0 byte | **Từ chối ngay**; không chấp nhận tag rỗng. |
| Giao thức 16 byte nhưng nhận 32 byte | 32 byte | **Từ chối**, trừ khi phiên bản giao thức khác được cấu hình rõ ràng. |

Độ dài tag HMAC không phụ thuộc số byte của message. HMAC-SHA256 sinh đủ 32 byte cho message rỗng hay dài; cắt ngắn là **chính sách giao thức**, không phải cách xử lý message ngắn.

### 6.2. Vì sao tag càng ngắn càng dễ bị đoán?

Nếu tag có **t bit đủ khó dự đoán** và thiết kế/giả thiết mật mã thích hợp, một lần thử đoán ngẫu nhiên có xác suất thành công xấp xỉ **2^(-t)**. Sau khoảng q lần thử độc lập, xác suất tổng thể có thể tăng gần mức **q / 2^t** khi q còn nhỏ so với 2^t. Đây chỉ là trực giác bảo mật cho MAC lý tưởng; rủi ro của một chế độ cụ thể còn phụ thuộc số thông điệp, kích thước dữ liệu, nonce, giới hạn thuật toán và thiết kế giao thức.

| Độ dài tag | Số byte | Độ khó đoán trong mô hình đơn giản |
| --- | ---: | --- |
| 32 bit | 4 | Khoảng một trong 2^32 lần thử; không phù hợp làm mặc định cho ứng dụng đa dụng. |
| 64 bit | 8 | Khoảng một trong 2^64; phải đánh giá rất chặt ngân sách giả mạo và vòng đời khóa. |
| 96 bit | 12 | Khoảng một trong 2^96, vẫn phụ thuộc giới hạn thuật toán/giao thức. |
| 128 bit | 16 | Khoảng một trong 2^128 cho một lần đoán trong mô hình lý tưởng. |
| 256 bit | 32 | Kết quả đầy đủ của HMAC-SHA256; không mặc nhiên nâng tổng an toàn hệ thống nếu khóa/quy trình yếu. |

**Không kết luận “tag 64 bit luôn an toàn” chỉ từ con số 2^64.** Một dịch vụ cho hàng tỷ request, nhiều tenant, nhiều khóa hoặc có khả năng thử lại liên tục phải xét ngân sách giả mạo, rate limit, giám sát và tuổi thọ khóa.

RFC 2104, mục 5, mô tả phép truncation HMAC bằng cách lấy các bit bên trái của output và đưa ra khuyến nghị lịch sử **không dưới một nửa độ dài hash và không dưới 80 bit**. Đó không phải giấy phép để tự chọn tag ngắn cho mọi hệ thống hiện đại. Trong bài này, **16 byte (128 bit)** là cấu hình ví dụ rõ ràng cho HMAC-SHA256 khi giao thức cho phép, còn dùng **32 byte đầy đủ** sẽ tránh một quyết định cắt ngắn không cần thiết.

### 6.3. AES-GCM: tag length là tham số của phép mã hóa có xác thực

Với GCM, **GCMParameterSpec(tagLengthBits, iv)** nhận **độ dài tag tính bằng bit**, không phải byte. Chẳng hạn:

- **new GCMParameterSpec(128, iv):** tag 128 bit = 16 byte.
- **iv.length == 12:** IV 96 bit; đây là độ dài IV, **không phải độ dài tag**.
- JCE GCM thường trả về **ciphertext nối tiếp tag** từ Cipher.doFinal khi mã hóa. Khi giải mã với cấu hình tag 128 bit, API xác minh phần tag tương ứng ở cuối input.

NIST SP 800-38D chỉ định các độ dài tag GCM thông dụng **128, 120, 112, 104, 96 bit**; **64 và 32 bit** chỉ dành cho ứng dụng đặc biệt với giới hạn nghiêm ngặt về độ dài dữ liệu và vòng đời khóa. Implementation có thể chỉ hỗ trợ một phần các độ dài đã được nêu. Đừng tự hạ tag length của GCM để giải mã một bản mã bị mất byte; nếu một giao thức hợp lệ dùng độ dài khác, đó phải là **quyết định cố định theo key/profile**, được kiểm thử và có đánh giá an toàn riêng.

**Giới hạn quan trọng khác:** với AES-GCM, không được lặp lại **cặp (khóa, IV)** khi mã hóa thông điệp mới. Lặp IV có thể phá hủy bảo đảm xác thực và bí mật, ngay cả khi tag dài 128 bit. Không dùng độ dài tag lớn để “bù” cho lỗi nonce/IV.

### 6.4. Không thương lượng mức an toàn từ input không đáng tin

Phản ví dụ nguy hiểm về mặt logic:

~~~text
expectedFullTag = HMAC(secret, message)
compareLength = receivedTag.length
accept = compare(first compareLength bytes of expectedFullTag, receivedTag)
~~~

Nếu compareLength do client quyết định, kẻ tấn công có thể giảm số byte phải đoán; **tag rỗng** thậm chí có thể được so sánh với một đoạn rỗng và được chấp nhận trong cách cài đặt thiếu kiểm tra.

Quy tắc đúng: **server chọn thuật toán và số byte tag theo version/khóa đã cấu hình**, kiểm tra tag nhận có đúng độ dài, rồi so sánh chính xác. Không dùng receivedTag.length để cắt expected tag. Không fallback kiểu “thử 16 byte, nếu sai thì thử 8 byte”.

## 7. So sánh HMAC tag trong Java: độ dài cố định và giảm rò rỉ timing

Với **HMAC**, Java cung cấp **javax.crypto.Mac** để tính tag. Khi bên nhận phải so sánh hai tag, nên dùng một cách so sánh không kết thúc sớm phụ thuộc nội dung đối với các mảng có độ dài cố định, chẳng hạn **MessageDigest.isEqual(expected, received)**.

Khác biệt cần hiểu:

- **Arrays.equals** là công cụ so sánh mảng thông thường, không được chọn làm cơ chế chống timing leak cho tag bí mật.
- **MessageDigest.isEqual** trong JDK hiện đại so sánh digest theo cách hạn chế phụ thuộc timing vào **nội dung** của các mảng. Không suy ra từ đó rằng toàn bộ request handler, decode, chọn khóa hay mọi nhánh lỗi là constant-time.
- **Độ dài phải được xác định trước**: từ chối tag null, ngắn hoặc dài hơn chính sách, rồi so sánh hai mảng cùng độ dài.
- Nếu protocol nhận hex hoặc Base64, **decode ra byte[]** trước khi kiểm tra độ dài. Chuỗi hex 32 ký tự chỉ biểu diễn 16 byte; số ký tự không phải số byte.

Trong một ứng dụng thực tế, thực hiện cùng kiểm tra cả hai chiều giao tiếp, áp dụng rate limit và không để bên ngoài phân biệt “key ID có tồn tại”, “tag sai” hay “tag length sai” bằng chi tiết lỗi không cần thiết.

## 8. Ví dụ Java 17+: HMAC-SHA256-128 từ chối tag bị sửa, sai khóa, quá ngắn

Chương trình tạo một khóa HMAC 32 byte bằng SecureRandom, dùng **HmacSHA256**, sau đó lấy **16 byte đầu** làm tag theo chính sách HMAC-SHA256-128. Phía xác minh yêu cầu **đúng 16 byte** và sử dụng MessageDigest.isEqual.

Để chạy, tạo tệp **TagCheckHmacDemo.java**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TagCheckHmacDemo {
    private static final int TAG_BYTES = 16;

    private static byte[] hmac(byte[] key, byte[] message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(message);
    }

    private static byte[] createTag(byte[] key, byte[] message)
            throws Exception {
        return Arrays.copyOf(hmac(key, message), TAG_BYTES);
    }

    private static boolean verify(
            byte[] key, byte[] message, byte[] receivedTag) throws Exception {
        if (receivedTag == null || receivedTag.length != TAG_BYTES) {
            return false;
        }
        byte[] expected = createTag(key, message);
        return MessageDigest.isEqual(expected, receivedTag);
    }

    public static void main(String[] args) throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        byte[] original = "amount=250000;order=42"
                .getBytes(StandardCharsets.UTF_8);
        byte[] tag = createTag(key, original);

        byte[] changed = original.clone();
        changed[0] ^= 1;

        byte[] otherKey = key.clone();
        otherKey[0] ^= 1;

        System.out.println("Tag đúng: " + verify(key, original, tag));
        System.out.println("Thông điệp bị sửa: " + verify(key, changed, tag));
        System.out.println("Khóa sai: " + verify(otherKey, original, tag));
        System.out.println("Tag ngắn 15 byte: " +
                verify(key, original, Arrays.copyOf(tag, 15)));
        System.out.println("Tag rỗng: " + verify(key, original, new byte[0]));
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac -encoding UTF-8 --release 17 TagCheckHmacDemo.java
java TagCheckHmacDemo
~~~

Kết quả:

~~~text
Tag đúng: true
Thông điệp bị sửa: false
Khóa sai: false
Tag ngắn 15 byte: false
Tag rỗng: false
~~~

### Giải thích từng điểm bảo mật

1. **Mac.getInstance("HmacSHA256")** tính HMAC đúng thuật toán; **SecretKeySpec** cung cấp khóa bí mật cho phép tính. Đầu ra HMAC gốc là 32 byte.
2. **Arrays.copyOf(..., TAG_BYTES)** chỉ dùng tại **bên tạo expected tag** theo policy cố định 16 byte. Độ dài nhận từ network không ảnh hưởng đến số byte phải đối chiếu.
3. **receivedTag == null hoặc length != 16** dẫn tới từ chối **trước khi so sánh**. Vì thế mảng 15 byte hoặc rỗng không thể làm hệ thống hạ ngưỡng kiểm tra.
4. **MessageDigest.isEqual** nhận hai mảng 16 byte. Với JDK hiện đại, nội dung byte đầu tiên khác nhau không phải điều kiện dừng ngay như một phép so sánh thông thường.
5. **Thay một byte message** làm expected tag khác; **thay khóa** cũng làm expected tag khác. Trong thực tế, xác suất trùng ngẫu nhiên lý thuyết không phải tuyệt đối bằng 0; kết quả kiểm thử trên chỉ mô tả lần chạy với khóa sinh ngẫu nhiên.
6. Mỗi lần gọi **hmac** tạo một **Mac instance riêng**, tránh trộn trạng thái giữa request song song. Khóa và nội dung test chỉ lưu trong bộ nhớ cho chương trình minh họa.

**Khi dùng thật:** đừng dùng khóa hard-coded. Lấy secret từ secret manager/KMS hoặc cơ chế được bảo vệ tương ứng, định danh phiên bản khóa, thêm replay protection theo giao thức và tuyệt đối không log secret. Đừng tự tạo một chính sách truncation khác với đặc tả webhook của nhà cung cấp.

## 9. Ví dụ Java 17+: AES-GCM từ chối ciphertext, khóa, AAD hoặc tag sai

Khác HMAC, **AES-GCM không chỉ phát ra tag** mà còn mã hóa plaintext. Việc so khớp tag do **Cipher.doFinal** thực hiện. Ví dụ dưới đây mã hóa một message với tag **128 bit (16 byte)**, sau đó thực hiện các phép giải mã đúng và sai.

Tạo tệp **TagCheckGcmDemo.java**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class TagCheckGcmDemo {
    private static final int TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private record Sealed(byte[] iv, byte[] ciphertextAndTag) {}

    private static Sealed encrypt(SecretKey key, byte[] plain, byte[] aad)
            throws Exception {
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(aad);
        return new Sealed(iv, cipher.doFinal(plain));
    }

    private static boolean opensAs(SecretKey key, Sealed sealed,
            byte[] aad, byte[] expectedPlain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, sealed.iv()));
        cipher.updateAAD(aad);
        try {
            byte[] plain = cipher.doFinal(sealed.ciphertextAndTag());
            return Arrays.equals(plain, expectedPlain);
        } catch (AEADBadTagException e) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey key = generator.generateKey();

        byte[] plain = "order=42;amount=250000"
                .getBytes(StandardCharsets.UTF_8);
        byte[] aad = "tenant=A;v=1".getBytes(StandardCharsets.UTF_8);
        Sealed sealed = encrypt(key, plain, aad);

        byte[] changed = sealed.ciphertextAndTag().clone();
        changed[0] ^= 1;

        byte[] wrongKeyBytes = key.getEncoded().clone();
        wrongKeyBytes[0] ^= 1;
        SecretKey wrongKey = new SecretKeySpec(wrongKeyBytes, "AES");

        byte[] wrongAad = "tenant=B;v=1".getBytes(StandardCharsets.UTF_8);
        byte[] shortTag = Arrays.copyOf(sealed.ciphertextAndTag(),
                sealed.ciphertextAndTag().length - 1);

        System.out.println("GCM đúng: " + opensAs(key, sealed, aad, plain));
        System.out.println("Dữ liệu bị sửa: " + opensAs(key,
                new Sealed(sealed.iv(), changed), aad, plain));
        System.out.println("Khóa sai: " +
                opensAs(wrongKey, sealed, aad, plain));
        System.out.println("AAD sai: " +
                opensAs(key, sealed, wrongAad, plain));
        System.out.println("Tag thiếu byte: " + opensAs(key,
                new Sealed(sealed.iv(), shortTag), aad, plain));
    }
}
~~~

~~~bash
javac -encoding UTF-8 --release 17 TagCheckGcmDemo.java
java TagCheckGcmDemo
~~~

Kết quả:

~~~text
GCM đúng: true
Dữ liệu bị sửa: false
Khóa sai: false
AAD sai: false
Tag thiếu byte: false
~~~

### Cách đọc kết quả cho chính xác

- **GCM đúng: true** vì cùng khóa, IV, AAD và ciphertext||tag. Trong ví dụ, opensAs còn so plaintext kết quả với expectedPlain để minh họa test. **Phép xác thực mật mã thực sự được Cipher.doFinal thực hiện**, còn Arrays.equals ở đây chỉ là bước assert của test.
- **Dữ liệu bị sửa: false** vì một byte ciphertext bị thay và tag cũ không còn khớp.
- **Khóa sai: false** vì verifier khởi tạo bằng khóa AES khác. Ứng dụng không nên nói cho client rằng “sai khóa” hay “có tấn công” dựa trên lỗi tag.
- **AAD sai: false** vì AAD được xác thực dù không bị mã hóa. Nếu tenant/version là metadata an ninh, phải đưa các byte tương ứng vào chính sách xác thực.
- **Tag thiếu byte: false** vì mảng đầu vào không còn chứa ciphertext/tag hợp lệ theo profile GCM 128 bit đã cấu hình; **Cipher.doFinal** từ chối.
- **new GCMParameterSpec(TAG_BITS, iv)** nhận **128 bit** và một IV 12 byte; đừng nhầm TAG_BITS với IV_BYTES.
- **new SecureRandom().nextBytes(iv)** tạo IV ngẫu nhiên cho một phép mã hóa. Ví dụ dùng một lần với khóa mới; với ứng dụng có số lượng message lớn hoặc nhiều node, cần thiết kế chiến lược nonce **duy nhất theo cùng khóa**, ngân sách va chạm và vòng đời khóa đúng chuẩn.
- **Sealed** ở đây là record đơn giản chứa mảng byte có thể sửa, để tiện kiểm thử. Record Java không làm mảng bất biến sâu; với production cần kiểm soát ownership/copy dữ liệu và đóng gói version, key ID, IV, ciphertext, tag rõ ràng.

Nếu một provider không hỗ trợ biến thể hoặc tham số đã yêu cầu, ứng dụng có thể ném exception khi khởi tạo. **Không nên bắt mọi Exception rồi trả false một cách im lặng**: phân biệt lỗi cấu hình/khả năng hỗ trợ để vận hành, nhưng phản hồi bên ngoài vẫn tránh lộ thông tin nhạy cảm.

## 10. Rủi ro khi giải mã theo nhiều bước và dùng plaintext trước khi kiểm tra tag

Trong AEAD, thiết kế luồng giải mã cần bảo đảm **không đưa plaintext chưa được xác thực tới nghiệp vụ, log, file hoặc hệ thống khác**. Một số provider/API có thể buffer dữ liệu giải mã để chỉ trả plaintext sau khi tag hợp lệ; đừng mặc định mọi provider và mọi cách dùng streaming đều mang lại tính chất đó.

Quy tắc dễ kiểm chứng:

1. Gọi **updateAAD** với toàn bộ AAD trước khi xử lý ciphertext cho GCM/CCM.
2. Cung cấp đúng ciphertext và tag với **độ dài tag theo giao thức**.
3. Hoàn tất và xác minh bằng **doFinal**.
4. **Chỉ sau khi doFinal thành công**, cho phép logic nghiệp vụ tiêu thụ plaintext.
5. Khi **AEADBadTagException** (hoặc lỗi xác thực theo API) xảy ra, bỏ dữ liệu tạm, không cố phân tích/khôi phục plaintext; không tái sử dụng Cipher đang ở trạng thái lỗi như một thao tác mới.
6. Nếu cần giải mã file lớn theo luồng, dùng định dạng AEAD theo chunk/record có nonce và tag riêng cùng quy tắc kết thúc rõ; không tự coi phần đã xử lý là hợp lệ trước khi có xác thực phù hợp.

Một **MAC-then-encrypt** hoặc **encrypt-then-MAC** tự thiết kế có thể sinh lỗi thứ tự, phân tách khóa, dữ liệu được MAC và xử lý lỗi. Với bài toán mới, ưu tiên cơ chế **AEAD tiêu chuẩn** được hỗ trợ thay vì tự lắp protocol mật mã không được đánh giá.

## 11. Độ dài tag, encoding và giao thức truyền tải: lỗi Java thường gặp

### 11.1. So sánh chuỗi thay vì tag bytes

Nếu API trả tag ở Base64url, server nên decode và so sánh **byte[]**. Base64 (và hex) chỉ mã hóa cách hiển thị; nó không tăng entropy hay biến tag thành bí mật khác. Không chuyển tag thành BigInteger vì có thể mất byte 0 ở đầu, thêm byte biểu diễn dấu hoặc thay đổi format.

Nếu hợp đồng quy định một canonical Base64url representation, hãy kiểm tra đúng format đó trước decode; đừng âm thầm chấp nhận một loạt biến thể đầu vào khác nhau nếu chúng không thuộc giao thức. Nếu hợp đồng nói rõ raw bytes, không tự chuyển sang String và ngược lại với charset mặc định của JVM.

### 11.2. Thay đổi thông điệp trước khi xác minh

Không tự trim, lower-case, bỏ ký tự xuống dòng, parse số tiền rồi stringify lại hoặc serialize JSON mới trước HMAC nếu giao thức ký raw body. Ví dụ:

~~~text
Raw body A: {"amount":250000,"order":42}
Raw body B: { "order": 42, "amount": 250000 }

Ý nghĩa dữ liệu có thể tương đương,
nhưng HMAC(raw A) và HMAC(raw B) thường khác nhau.
~~~

Để có canonicalization thật sự, hai phía phải dùng **cùng một quy tắc được định nghĩa rõ**. Không lặp lại nhiều phép biến đổi cho tới khi một cách tính “tình cờ” khớp tag.

### 11.3. Tag bị cắt bởi giới hạn cột, buffer hoặc serialization

Kiểm tra đơn vị đo:

- HMAC-SHA256 **32 byte** có **64 ký tự hex**, hoặc độ dài chuỗi Base64 tùy padding/profile.
- HMAC-SHA256-128 **16 byte** có **32 ký tự hex**; một chuỗi ngắn hơn có thể là lỗi truyền hay encoding, không tự đổi policy.
- AES-GCM với tag 16 byte thường trả **ciphertext||tag**; nếu cắt mất byte cuối mảng, cần coi envelope không hợp lệ.
- Với JCE, **GCMParameterSpec(128, iv)** chọn độ dài tag 128 **bit**, không phải 128 byte. Độ dài IV cần xét riêng.
- Trong API lưu trữ, giới hạn ký tự của field Base64 không bằng giới hạn byte đã decode.

Không dùng cách khôi phục bằng padding byte 0 hoặc tự thêm ký tự vào tag bị cắt. Phải sửa nguồn lỗi truyền/lưu trữ, xác minh lại với bên gửi.

### 11.4. Sai tham số khi thay đổi provider/JDK

Một provider có thể không hỗ trợ một tag length GCM cụ thể dù chuẩn cho phép; việc lựa chọn transformation, key type, IV length và AAD còn phụ thuộc implementation. Ghi nhận provider và kết quả kiểm thử tương thích ở môi trường triển khai; **không tự giảm độ dài tag khi một provider từ chối tham số**.

### 11.5. So sánh byte không đổi thời gian nội dung chưa đủ cho toàn hệ thống

Dù dùng MessageDigest.isEqual, thời gian xử lý vẫn có thể khác ở bước parse Base64, key lookup, đọc database, exception hoặc truy cập KMS/HSM. Giải pháp là thiết kế lỗi có kiểm soát, rate limit, không tiết lộ bí mật; không khẳng định cả endpoint constant-time chỉ nhờ một hàm so sánh mảng.

## 12. Ví dụ vận dụng: xác minh webhook thanh toán

Giả sử cổng thanh toán gửi request có timestamp, transaction ID, raw body và HMAC. Một luồng xác minh đúng cần:

1. Nhận **raw body bytes** với giới hạn kích thước; lấy tag, timestamp, key ID từ những header **được giao thức định nghĩa**.
2. Parse header an toàn, kiểm tra định dạng và độ dài decoded tag **đúng policy**.
3. Tìm secret đúng đối tác và key version từ cấu hình tin cậy; không lấy secret trực tiếp từ request.
4. Tạo byte sequence được HMAC bảo vệ theo đặc tả nhà cung cấp (ví dụ có timestamp + body với delimiter/canonicalization rõ). Không được tự chế nếu bên gửi có đặc tả khác.
5. Tính HMAC và so sánh đúng số byte; thất bại thì từ chối, **chưa xử lý thanh toán**.
6. Kiểm tra timestamp/freshness và ID giao dịch theo chính sách; đảm bảo các trường dùng để chống replay cũng nằm trong phạm vi xác thực hoặc được gắn với ngữ cảnh tin cậy.
7. Kiểm tra trạng thái giao dịch hiện tại và xử lý **idempotent**. Webhook hợp lệ gửi lại không được cộng tiền hoặc phát hành hàng nhiều lần.
8. Đưa ra kết quả chung phù hợp cho phía gửi, ghi log an toàn phục vụ truy vết nhưng không chứa khóa hoặc thông tin nhạy cảm.

Ví dụ: kẻ tấn công lấy được một webhook đã ký hợp lệ và gửi lại **nguyên văn**. HMAC vẫn hợp lệ vì bytes và tag không thay đổi; ứng dụng phải dùng transaction ID, timestamp, nonce hoặc dữ liệu nghiệp vụ để từ chối thao tác lặp. Ngược lại, nếu kẻ tấn công sửa amount nhưng không có khóa, tag không hợp lệ và bị từ chối tại bước xác minh.

Nếu giao thức dùng **chữ ký số** thay vì HMAC, luồng tương tự về nguyên tắc kiểm tra bytes và freshness, nhưng dùng **Signature.verify** cùng public key được xác minh nguồn; không tự ép thông điệp sang HMAC.

## 13. Thiết kế envelope và quản lý phiên bản cho dữ liệu lưu trữ

Với một ứng dụng Java lưu bản mã AES-GCM, có thể thiết kế một record versioned ở cấp ứng dụng:

~~~text
RecordV1:
  version       = 1
  keyId         = ID khóa dùng để chọn đúng khóa AES đã được cấp phép
  iv            = 12 byte theo chính sách nonce
  ciphertext    = bytes mã hóa
  tag           = 16 byte cuối của AES-GCM output
  aad           = bytes ràng buộc tenant + loại record + version
~~~

Đây là **ví dụ hợp đồng ứng dụng**, không phải định dạng lưu trữ tiêu chuẩn do JCA tự sinh. Những điểm phải chốt rõ:

- Nếu đưa version/keyId/tenant vào AAD, cần có **quy tắc serialization bytes chính xác**, tránh ghép chuỗi mơ hồ.
- Khóa giải mã phải được truy xuất theo **định danh được phép** và theo đúng tenant; người gửi không được điều khiển nơi tìm secret tùy ý.
- Bộ kiểm tra phải biết **tag length** từ profile đã được cấu hình, không lấy length tùy ý từ dữ liệu chưa xác thực.
- IV phải duy nhất **trong toàn bộ phạm vi cùng khóa AES khi mã hóa**, kể cả khi có nhiều node, restart và backup/restore.
- Bản ghi cũ cần có key version/format version đủ để giải mã hoặc migrate sau rotation; không ghi đè tag length/algorithm một cách mập mờ.
- Một record bị hỏng, tag sai hoặc không có khóa thích hợp phải được phân loại là không thể xác minh; không khôi phục plaintext bằng cách bỏ bước xác thực.

**MAC tag không bảo vệ thông tin không nằm trong input MAC/AAD.** Nếu cần bảo vệ key ID hoặc version khỏi bị đổi, có thể ràng buộc metadata đã được quy định với chính sách AAD/cấu trúc xác thực, đồng thời kiểm tra nó theo ngữ cảnh tin cậy của ứng dụng.

## 14. Kế hoạch kiểm thử: cả happy path lẫn các dạng từ chối

Chỉ test một tag đúng là chưa đủ. Các test cần thể hiện điều kiện **chấp nhận duy nhất khi đầy đủ**.

| Ca kiểm thử | Dữ liệu đưa vào | Kỳ vọng |
| --- | --- | --- |
| HMAC hợp lệ | Đúng message bytes + đúng key + đúng 16-byte tag theo profile | Accept. |
| HMAC sửa một byte message | Giữ tag cũ, đổi message bytes | Reject. |
| HMAC sửa một byte tag | Giữ message, đổi tag bytes | Reject. |
| HMAC sai khóa | Key khác key của bên tạo | Reject. |
| HMAC tag rỗng | byte[0] | Reject trước so khớp. |
| HMAC tag 15 byte | Thiếu một byte khi policy 16 byte | Reject, không so khớp prefix. |
| HMAC tag 17 hoặc 32 byte | Dài hơn policy 16 byte | Reject nếu không có profile khác đã thỏa thuận. |
| HMAC sai encoding | Body bị serialize lại khác bytes | Reject; sửa lỗi canonicalization ở giao thức. |
| GCM hợp lệ | Đúng key, IV, AAD, ciphertext và tag 128 bit | Decrypt thành công rồi dùng plaintext. |
| GCM sửa ciphertext/tag | Đảo bit nhưng giữ metadata cũ | Reject qua doFinal, không xử lý plaintext. |
| GCM sai AAD | Tenant/version không khớp | Reject qua doFinal. |
| GCM sai IV | IV không phải IV đã dùng mã hóa | Reject qua doFinal. |
| GCM sai khóa | Khóa khác | Reject hoặc báo lỗi khởi tạo có kiểm soát tùy nguyên nhân. |
| GCM ciphertext/tag bị cắt | Thiếu bytes so với envelope/tag policy | Reject; không tự giảm tagLength. |
| Replay | Gửi lại message + tag đã từng hợp lệ | MAC vẫn có thể đúng; replay/idempotency phải chặn xử lý lặp. |
| Key rotation | Message v1/v2 và key ID phù hợp | Dùng khóa được phép theo version và chính sách còn hiệu lực. |
| Tải đồng thời | Nhiều yêu cầu xác minh khác nhau qua thread pool | Không trộn trạng thái Mac/Cipher hay buffer giữa các request. |

Một bộ test thực tế nên chạy trên provider/JDK triển khai. Thử cả bytes không phải ASCII, dấu tiếng Việt, newline, ký tự Unicode kết hợp và input lớn để phát hiện bất đồng encoding/canonicalization. Test lỗi nên quan sát rằng **không có side effect nghiệp vụ** trước xác thực; chỉ assert exception mà vẫn đã lưu thanh toán là chưa đạt yêu cầu.

### Phân biệt lỗi chấp nhận sai và lỗi từ chối sai

- **False accept:** hệ thống chấp nhận tag không hợp lệ; nguy hiểm nhất vì có thể cho phép giả mạo hoặc thay nội dung.
- **False reject:** tag hợp lệ bị từ chối do chọn sai khóa, format, IV, AAD hoặc canonicalization; gây lỗi tích hợp nhưng không nên “sửa” bằng việc bỏ kiểm tra.
- **Provider/configuration error:** thuật toán/khóa/tham số không được hỗ trợ hoặc key store không hoạt động; cần xử lý như lỗi hệ thống và giữ nguyên nguyên tắc từ chối dữ liệu chưa xác thực.

## 15. Những lỗi tư duy và code cần tránh

1. **So sánh prefix theo tagLength do client gửi:** tạo nguy cơ downgrade, đặc biệt khi nhận tag rỗng hoặc rất ngắn.
2. **Cho phép 8, 12 hoặc 16 byte tùy message mà không có version/profile tin cậy:** không còn một ngưỡng xác thực rõ.
3. **Dùng equals/toString để so tag biểu diễn:** dễ sai encoding, mất byte, hoặc dùng so sánh không được thiết kế chống timing leak.
4. **Dùng SHA-256(message) làm MAC:** không có khóa bí mật và đối thủ có thể tính lại sau khi sửa message.
5. **Đưa chỉ một phần request vào HMAC trong khi tin toàn bộ request:** các field ngoài phạm vi tag có thể bị đổi.
6. **Bỏ qua AEADBadTagException để vẫn dùng plaintext:** làm mất chức năng bảo vệ cốt lõi của AEAD.
7. **Nhầm tag length với IV length:** GCMParameterSpec(128, iv) có tag 128 bit, còn 12 byte là IV thường gặp.
8. **Dùng lại cặp AES-GCM key/IV:** có thể dẫn tới tấn công nghiêm trọng dù tag đủ dài.
9. **Đổi từ tag 128 bit sang 64 bit để “đọc được dữ liệu cũ”:** hạ bảo đảm và có thể không thuộc policy/protocol; phải sửa định dạng hay key/parameter mismatch đúng nguyên nhân.
10. **Không phân biệt verify thất bại với lỗi kỹ thuật:** xử lý lỗi phải giữ tính an toàn, đồng thời giúp vận hành chẩn đoán mà không lộ secret.
11. **Chỉ test đầu vào đúng:** không phát hiện cắt tag, sai khóa, sai tenant/AAD hoặc replay.
12. **Tin tag đồng nghĩa đã phân quyền:** tag chỉ xác thực bytes theo khóa; nghiệp vụ vẫn cần authorization, kiểm tra trạng thái và chống thao tác lặp.

## 16. Tự kiểm tra kiến thức

**Câu 1.** Giao thức dùng HMAC-SHA256-128 nhưng nhận 15 byte. Có nên so sánh 15 byte đầu của expected tag không?  
**Trả lời:** Không. Chính sách yêu cầu **đúng 16 byte**. Từ chối tag 15 byte, dù 15 byte đầu có khớp, bởi độ dài không do client thương lượng.

**Câu 2.** HMAC của body hợp lệ nhưng header tenant ID bị thay đổi. Điều gì có thể xảy ra?  
**Trả lời:** Nếu tenant ID không được bảo vệ bởi MAC hoặc gắn với ngữ cảnh tin cậy, tag trên body vẫn có thể đúng. Phải xác định chính xác metadata nào được HMAC bao phủ và kiểm tra quyền theo tenant ở server.

**Câu 3.** Dùng đúng AES-GCM key và ciphertext, nhưng AAD khác. Java trả gì?  
**Trả lời:** Việc xác minh tag thất bại; với JCE GCM, **Cipher.doFinal** có thể ném **AEADBadTagException**. Không thể kết luận chắc chắn có tấn công chỉ từ exception, vì sai AAD cũng gây ra lỗi.

**Câu 4.** GCMParameterSpec(128, iv) với iv dài 12 byte có nghĩa tag dài 128 byte không?  
**Trả lời:** Không. 128 là **bit tag = 16 byte**; IV dài 12 byte là một tham số khác.

**Câu 5.** Nếu server thấy tag 8 byte, vì sao không tự chuyển sang GCM 64-bit tag để thử giải mã?  
**Trả lời:** Tag length là quyết định của giao thức và chính sách khóa; độ dài ngắn hơn có giới hạn bảo mật nghiêm ngặt và có thể không được provider hỗ trợ. Dữ liệu bị cắt không được hợp thức hóa bằng downgrade.

**Câu 6.** Tag hợp lệ có chứng minh webhook chưa từng được xử lý không?  
**Trả lời:** Không. Cần thêm timestamp/nonce, transaction ID, kiểm tra trạng thái và idempotency được tích hợp vào giao thức.

**Câu 7.** HMAC-SHA256 trả 32 byte nhưng hệ thống dùng 16 byte. Khi xác minh, có tính HMAC trên “nửa message” không?  
**Trả lời:** Không. **Luôn tính HMAC trên toàn bộ message bytes**; chỉ lấy **16 byte đầu output** theo profile HMAC-SHA256-128 được quy định.

**Câu 8.** MessageDigest.isEqual có làm toàn bộ endpoint trở thành constant-time không?  
**Trả lời:** Không. Nó hỗ trợ so sánh digest hạn chế timing leak theo nội dung trong điều kiện thích hợp, nhưng decode, key lookup, rate limit, exception và các bước xử lý khác vẫn có hành vi thời gian riêng.

## 17. Kết luận và tài liệu đối chiếu

Một cơ chế kiểm tra tag đúng đòi hỏi đủ ba bất biến: **(1) đúng khóa và đúng bytes được bảo vệ; (2) thuật toán/tham số và độ dài tag theo policy cố định; (3) xác thực thất bại thì không sử dụng nội dung**. HMAC xác minh bằng cách tính MAC và so sánh tag; AES-GCM xác minh bằng chính Cipher.doFinal. Tag dài bảo vệ tốt hơn trước đoán giả mạo trong các giới hạn thuật toán, nhưng không thay thế quản lý khóa, IV duy nhất, phân quyền, chống replay và kiểm soát vận hành.

Tài liệu kỹ thuật đáng tham khảo:

- [RFC 2104 — HMAC: Keyed-Hashing for Message Authentication, mục 5 về truncated output](https://www.rfc-editor.org/rfc/rfc2104.html)
- [NIST SP 800-38D — Galois/Counter Mode (GCM) and GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final)
- [Java SE 21 — Mac API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html)
- [Java SE 21 — Cipher API, GCM/AEAD](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
- [Java SE 21 — MessageDigest.isEqual](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html)
- [Java SE 21 — GCMParameterSpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-authentication-code-007">5.7 Phân biệt xác thực thông điệp với chống phát lại bằng nonce hoặc số thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# 5.7. Phân biệt xác thực thông điệp với chống phát lại bằng nonce hoặc số thứ tự

Trong giao thức trao đổi thông điệp, **xác thực (authentication)** trả lời câu hỏi “Thông điệp này có được tạo bởi bên nắm đúng khóa/định danh và còn nguyên vẹn không?”, còn **chống phát lại (anti-replay)** trả lời câu hỏi khác: “Một thông điệp hợp lệ như thế này **đã được chấp nhận trước đó** hoặc đã quá hạn chưa?”. Một thông điệp có HMAC hoặc chữ ký **đúng hoàn toàn vẫn có thể là thông điệp cũ bị gửi lại**. Đây là lý do hệ thống thanh toán, API thao tác trạng thái, webhook và giao thức trao đổi khóa thường cần thêm **nonce, số thứ tự, timestamp và trạng thái phía nhận**.

## Mục tiêu bài học

Sau bài này, bạn có thể:

1. Phân biệt kiểm tra nguồn gốc/tính toàn vẹn bằng **MAC, chữ ký số hoặc AEAD** với kiểm tra **freshness**, thứ tự và việc sử dụng một lần.
2. Mô tả một cuộc tấn công replay mà HMAC vẫn hợp lệ; giải thích vì sao hash thuần túy và timestamp đơn lẻ không thay được cơ chế chống phát lại.
3. Chọn giữa **nonce ngẫu nhiên/challenge** và **sequence number** theo yêu cầu truyền thông, thứ tự, đồng bộ trạng thái và khả năng retry.
4. Thiết kế dữ liệu bắt buộc phải được xác thực, replay cache/counter, giới hạn thời gian, và thao tác ghi trạng thái **atomic** trên nhiều máy chủ.
5. Viết và chạy ví dụ **Java 21** dùng `Mac`/HmacSHA256, `SecureRandom`, `MessageDigest.isEqual` và một replay set minh họa; hiểu chính xác giới hạn của ví dụ.

## 1. Hai thuộc tính bảo mật độc lập

Giả sử client hợp lệ gửi lệnh:

```text
POST /transfers
{"to":"Bob","amount":100}
timestamp = 1700000000
nonce = N1
tag = HMAC(secret, method | path | body | timestamp | nonce)
```

Server kiểm tra HMAC thành công và chuyển 100 đơn vị. Kẻ tấn công thu lại nguyên gói đã ký rồi gửi y hệt gói đó lần thứ hai. Vì **byte dữ liệu và tag không thay đổi**, HMAC lần hai vẫn đúng. Nếu server không nhớ `N1` đã dùng, lệnh chuyển tiền có thể được thực hiện lại.

| Thuộc tính/cơ chế | Chứng minh hoặc bảo vệ điều gì? | Tự ngăn replay? |
| --- | --- | --- |
| Hash không khóa, ví dụ SHA-256 | Phát hiện thay đổi khi có nguồn hash đáng tin để so sánh; tự nó không xác thực người gửi | Không |
| MAC/HMAC với khóa bí mật dùng chung | Tính toàn vẹn và chứng minh bên tạo tag biết khóa MAC | **Không** |
| Chữ ký số được xác minh bằng public key phù hợp | Tính toàn vẹn và nguồn gốc gắn với private key; kết quả còn phụ thuộc cách xác thực public key | **Không** |
| AEAD, ví dụ AES-GCM | Bí mật + toàn vẹn/xác thực bản mã và AAD đã chọn | **Không tự động** ở tầng thông điệp ứng dụng |
| Timestamp được xác thực | Giới hạn tuổi thọ/giai đoạn hợp lệ của thông điệp | Không ngăn gửi lại **trong cửa sổ** |
| Nonce đã xác thực **và** kiểm tra dùng một lần | Phát hiện cùng nonce đã được server chấp nhận | Có, trong phạm vi và thời gian lưu trạng thái |
| Sequence number đã xác thực **và** lưu trạng thái nhận | Phát hiện số thứ tự cũ hoặc trùng, tùy quy tắc và cửa sổ | Có, với quy tắc nhận phù hợp |

**Điểm mấu chốt:** tính hợp lệ về mật mã là điều kiện để tin dữ liệu đến từ bên giữ khóa; **tính mới** là ràng buộc bổ sung cần quy tắc giao thức và bộ nhớ của phía nhận. Chỉ sinh nonce rồi bỏ qua nó, hoặc đưa nonce vào header nhưng không đưa vào dữ liệu được HMAC/ký, sẽ không tạo ra bảo vệ chống replay.

### Điều mà anti-replay không hứa hẹn

Nếu kẻ tấn công lấy được một yêu cầu hợp lệ **trước khi** server chấp nhận nó, kẻ đó có thể gửi yêu cầu trước client thật. Hệ thống chống replay có thể bảo đảm **chỉ một yêu cầu được chấp nhận**, nhưng không biết bản nào là “bản gốc” trên mạng. TLS, bảo vệ khóa, xác thực kênh, ràng buộc phiên và các biện pháp giao thức khác vẫn cần thiết. Nếu kẻ tấn công **biết khóa HMAC/private key** và tự tạo thông điệp mới hợp lệ, chặn các nonce trùng cũng không ngăn được việc giả mạo đó.

## 2. Nonce: giá trị riêng cho mỗi yêu cầu

**Nonce** (*number used once*) là giá trị mà giao thức yêu cầu chỉ sử dụng một lần **trong một phạm vi xác định**, ví dụ cho mỗi định danh client + khóa + phiên. Nonce có thể được client sinh ngẫu nhiên hoặc server cấp dưới dạng challenge.

### 2.1 Nonce ngẫu nhiên do client sinh

Quy trình phổ biến cho HTTP API:

1. Client dùng bộ sinh số ngẫu nhiên mật mã (`SecureRandom`), tạo nonce đủ dài, chẳng hạn **128 bit = 16 byte** ngẫu nhiên.
2. Client gửi `clientId/keyId`, thao tác, payload, timestamp và nonce; **MAC/chữ ký phải bao phủ tất cả trường ảnh hưởng đến ý nghĩa thao tác**, nhất là nonce và timestamp.
3. Server xác định khóa theo định danh đã được kiểm soát, xác minh tag, kiểm tra timestamp, rồi tra **replay store** cho nonce trong phạm vi của định danh/khóa.
4. Server **ghi nhận nonce một cách nguyên tử** trước hoặc cùng lúc commit thao tác nghiệp vụ. Nếu nonce đã tồn tại, server từ chối hoặc trả lại kết quả của yêu cầu đã xử lý theo chính sách idempotency.
5. Server giữ bản ghi đủ lâu để một thông điệp cũ không trở lại hợp lệ sau khi record hết hạn.

Nonce ngẫu nhiên có xác suất va chạm rất nhỏ khi sinh đúng, nhưng **tính ngẫu nhiên chỉ làm giảm nguy cơ hai yêu cầu hợp lệ tự chọn cùng nonce**. Kẻ tấn công phát lại cố tình dùng **chính nonce cũ**, vì vậy server phải lưu và kiểm tra việc đã dùng.

### 2.2 Nonce do server cấp (challenge–response)

Trong một số giao thức, server tạo một challenge ngắn hạn, gắn với client/phiên, hành động và thời điểm hết hạn; client ký/MAC cả challenge và yêu cầu; server **đánh dấu challenge đã dùng một lần** khi xác thực. Giải pháp này cho server quyền kiểm soát vòng đời của nonce. Đổi lại, cần thêm lượt giao tiếp và bộ nhớ trạng thái challenge. Một challenge chỉ có giá trị trong phạm vi được cấp; không dùng challenge của phiên/đích này để hợp thức hóa thao tác khác.

### 2.3 Nonce + timestamp: vai trò khác nhau

- **Nonce + replay store:** bắt được cùng yêu cầu được gửi lại.
- **Timestamp đã xác thực:** giới hạn thời gian kiểm tra và thời gian cần giữ record.
- **Chỉ timestamp:** một gói ký hợp lệ có thể được phát lại nhiều lần trong cửa sổ vài phút.
- **Chỉ nonce nhưng không giữ trạng thái:** không phát hiện được việc sử dụng lại.
- **Nonce đặt ngoài MAC/chữ ký:** kẻ tấn công có thể sửa nonce rồi phát lại tag của nội dung khác; cách bố trí này sai thiết kế.

Ví dụ chính sách: chấp nhận `issuedAt` từ **5 phút trước** đến **30 giây trong tương lai** để chịu lệch đồng hồ đã được cân nhắc. Đó là chính sách minh họa, không phải tham số áp dụng máy móc cho mọi hệ thống. Nếu server nhận thông điệp với timestamp tương lai +30 giây, record nonce phải tồn tại ít nhất đến khi thông điệp **không còn hợp lệ** (ví dụ `issuedAt + 300 giây` cộng biên an toàn), chứ không chỉ xóa sau 300 giây tính từ lần nhận. Cần đồng bộ đồng hồ, theo dõi chênh lệch và giới hạn kích thước replay cache.

### 2.4 Phạm vi khóa tra cứu replay

Với nhiều tenant/khóa, lưu khóa replay theo dạng có ranh giới rõ, chẳng hạn:

```text
replayKey = protocolVersion | verifiedClientId | keyId | nonce
```

`verifiedClientId` phải là danh tính đã được xác thực hoặc ràng buộc bằng khóa, không phải chuỗi header do client tự nhận không kiểm tra. Nếu một nonce chỉ được bảo đảm duy nhất trong **từng phiên**, thêm session/epoch vào phạm vi; nếu yêu cầu bảo đảm dùng một lần **trên mọi endpoint** của cùng một khóa, đừng lưu replay cache tách rời từng endpoint. Định nghĩa phạm vi trước khi viết hệ thống.

## 3. Sequence number: số thứ tự có trạng thái

Sequence number (counter) là số tăng dần theo **mỗi sender, key hoặc session**, và bản thân counter cũng phải nằm trong dữ liệu được MAC/ký. Server ghi nhớ giá trị cuối hoặc một cửa sổ các giá trị đã thấy.

### 3.1 Chấp nhận tăng nghiêm ngặt

Giả sử server lưu `lastAccepted = 40`:

- Nhận số **41** với tag hợp lệ → chấp nhận và cập nhật `lastAccepted = 41`.
- Nhận lại **41** → từ chối trùng.
- Nhận **39** → từ chối cũ.
- Nhận **43** trước **42** → có thể chấp nhận 43 nếu quy tắc là `seq > lastAccepted`; sau đó 42 bị coi là cũ. Nếu quy tắc là `seq = lastAccepted + 1`, phải đợi 42 hoặc dùng cơ chế tái sắp xếp.

Phải chọn rõ giao thức yêu cầu **thứ tự tuyệt đối**, **chỉ chống số cũ/trùng**, hay **cho phép lệch thứ tự**. Counter chỉ tăng không tự giải quyết tính hoàn tất của những lệnh bị mất.

Với client có nhiều luồng/thiết bị, một counter chung có thể trở thành nút thắt hoặc gây out-of-order. Thông thường cần counter riêng cho mỗi **security association/session**, hoặc nonce ngẫu nhiên nếu thao tác độc lập và không đòi thứ tự. Counter phải được quản lý bền vững và không được reset thành giá trị cũ khi ứng dụng khởi động lại, trừ khi bắt đầu **epoch/phiên mới** được xác thực.

### 3.2 Sliding window cho thông điệp đến lệch thứ tự

Một lựa chọn cho datagram/stream đến lệch thứ tự là lưu `highestSeen` cùng bitmap các sequence vừa đi qua:

```text
highestSeen = 100; windowSize = 64
Cửa sổ xét: 37..100

seq = 98, bit 98 chưa có -> chấp nhận, đánh dấu 98
seq = 98 lần nữa          -> từ chối vì bit đã đánh dấu
seq = 36                  -> từ chối vì ngoài cửa sổ
seq = 101, tag hợp lệ      -> dịch cửa sổ và đánh dấu 101
```

Đây là **mô tả thuật toán**, chưa phải mã triển khai. Khi shift bitmap phải xử lý đúng overflow, giá trị đầu phiên, reset/rotate khóa, chuyển epoch, số thứ tự lớn hơn nhiều lần kích thước cửa sổ và truy cập đồng thời. Với HTTP API có yêu cầu tài chính, nonce + record nghiệp vụ có thể dễ kiểm toán hơn bitmap cửa sổ.

### 3.3 So sánh nonce và sequence number

| Tiêu chí | Nonce ngẫu nhiên + cache | Counter / sequence number |
| --- | --- | --- |
| Cần sinh dữ liệu mới | `SecureRandom` hoặc server challenge | Counter tăng với mỗi thông điệp |
| Trạng thái phía nhận | Tập nonce đã chấp nhận và TTL/phạm vi | High-watermark, có thể thêm bitmap cửa sổ |
| Gói đến lệch thứ tự | Có thể chấp nhận nếu nonce mới và timestamp hợp lệ | Cần thiết kế cửa sổ hoặc xử lý reorder |
| Client gửi song song | Tự nhiên nếu nonce độc lập | Phải cấp số tránh trùng và điều phối thứ tự |
| Khôi phục sau restart | Persist/cache dùng chung đủ lâu | Persist counter, bảo vệ reset/epoch |
| Hợp với | API request độc lập, webhook, challenge–response | Kênh có thứ tự, giao thức packet/session |
| Lỗi điển hình | Cache riêng mỗi node; TTL quá ngắn; nonce không ký | Counter reset; nhảy số; race cập nhật; drop gói hợp lệ |

Cả hai phương án đều phải chống **thao tác đồng thời**: hai request cùng nonce/sequence tới hai worker khác nhau chỉ được một worker nhận. Các phép “kiểm tra rồi ghi” tách rời là không an toàn.

## 4. Những trường phải được xác thực cùng thông điệp

Tối thiểu, khi sử dụng HMAC hoặc chữ ký cho một thao tác thay đổi trạng thái, bên ký và bên xác minh phải thống nhất **chính xác byte được ký**:

- Phiên bản giao thức và mục đích sử dụng (*domain separation*), ví dụ `payment-v1`.
- Danh tính sender đã ràng buộc với khóa, `keyId`/phiên nếu có; service/tenant/đích nhận để tránh chuyển tiếp chữ ký qua miền tin cậy khác (*cross-context replay*).
- HTTP method, tài nguyên/path, các tham số query hoặc phần message quyết định thao tác.
- Payload dưới một quy ước mã hóa xác định (body bytes, hoặc digest đã được bao phủ bởi chữ ký/MAC theo giao thức).
- Nonce hay sequence number, timestamp, expiration hoặc window khi được dùng.

Không nối chuỗi tùy tiện như `"AB" + "C"` và `"A" + "BC"` vì có thể tạo cùng byte. Dùng length-prefix, encoding nhị phân có định dạng, hoặc quy tắc canonicalization được định nghĩa và kiểm thử cho **cả hai đầu**. JSON có thứ tự field, khoảng trắng và cách biểu diễn số khác nhau; nếu phải ký JSON, cần quy ước canonical JSON hoặc ký nguyên byte body thực nhận. Phải xem ảnh hưởng của proxy, URL normalization và header được phép sửa theo giao thức.

**Sai phổ biến:** ký body mà bỏ method/path dẫn đến chữ ký của một body có thể được gắn sang endpoint khác; ký body và timestamp nhưng bỏ nonce khiến nonce không được bảo vệ; dùng cùng một khóa/MAC format cho nhiều loại thông điệp mà không có trường phiên bản/mục đích làm tăng nguy cơ nhầm ngữ cảnh.

## 5. Thực hành Java 21: HMAC đúng nhưng replay bị từ chối

Ví dụ dưới đây mô phỏng **một client và một server cùng chia sẻ khóa HMAC**, một process, thời gian cố định để có stdout ổn định. Nó minh họa ba phép kiểm: **tag đúng**, **timestamp trong cửa sổ**, **nonce chưa dùng**. Dữ liệu thật cần ánh xạ `clientId` sang khóa riêng, replay store chia sẻ/persist và xử lý nghiệp vụ có giao dịch; phần 7 trình bày.

Tạo đúng file **`ReplayDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public class ReplayDemo {
    record Request(String method, String path, String body,
                   long issuedAt, String nonce, String tag) {}

    // Mỗi trường có length-prefix; tránh nhập nhằng khi ghép chuỗi.
    static void field(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    // Tag không được ký lại chính nó. Mọi trường có ý nghĩa đều được ký.
    static byte[] signedBytes(Request request) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        field(out, "replay-demo-v1");
        field(out, request.method());
        field(out, request.path());
        field(out, request.body());
        out.writeLong(request.issuedAt());
        field(out, request.nonce());
        out.flush();
        return buffer.toByteArray();
    }

    static byte[] hmac(SecretKey key, Request request) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        return mac.doFinal(signedBytes(request));
    }

    static Request sign(SecretKey key, String body, long issuedAt)
            throws Exception {
        byte[] random = new byte[16];       // Nonce 128 bit.
        new SecureRandom().nextBytes(random);
        String nonce = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(random);
        Request unsigned = new Request(
                "POST", "/payments", body, issuedAt, nonce, "");
        String tag = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(key, unsigned));
        return new Request(unsigned.method(), unsigned.path(),
                unsigned.body(), unsigned.issuedAt(), nonce, tag);
    }

    static boolean verify(SecretKey key, Request request,
                          long now, Set<String> usedNonces)
            throws Exception {
        // Cửa sổ minh họa: tối đa 300 giây cũ, 30 giây tương lai.
        if (request.issuedAt() < now - 300 || request.issuedAt() > now + 30) {
            return false;
        }
        byte[] suppliedTag;
        try {
            suppliedTag = Base64.getUrlDecoder().decode(request.tag());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        // Kiểm tra MAC TRƯỚC khi chiếm nonce trong replay store.
        if (!MessageDigest.isEqual(hmac(key, request), suppliedTag)) {
            return false;
        }
        // Atomic trong một JVM: chỉ lần thêm nonce đầu tiên trả true.
        return usedNonces.add(request.nonce());
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256");
        generator.init(256);
        SecretKey sharedKey = generator.generateKey();
        Set<String> usedNonces = ConcurrentHashMap.newKeySet();
        long now = 1_700_000_000L; // Thời gian giả lập để test ổn định.

        Request first = sign(sharedKey, "{\"amount\":100}", now);
        System.out.println("new request: " +
                verify(sharedKey, first, now, usedNonces));
        System.out.println("exact replay: " +
                verify(sharedKey, first, now, usedNonces));

        Request modified = new Request(first.method(), first.path(),
                "{\"amount\":900}", first.issuedAt(),
                first.nonce(), first.tag());
        System.out.println("tampered body: " +
                verify(sharedKey, modified, now, usedNonces));

        Request expired = sign(sharedKey, "{\"amount\":100}", now - 301);
        System.out.println("stale request: " +
                verify(sharedKey, expired, now, usedNonces));
    }
}
```

Biên dịch và chạy bằng **JDK 21 hoặc compiler mới hơn có hỗ trợ `--release 21`**:

```text
javac -version
mkdir out
javac --release 21 -encoding UTF-8 -d out ReplayDemo.java
java -cp out ReplayDemo
```

Stdout từ chương trình (các nonce và khóa ngẫu nhiên **không** được in nên kết quả ổn định):

```text
new request: true
exact replay: false
tampered body: false
stale request: false
```

### Tại sao kết quả như vậy?

1. **New request = true:** tag đúng, timestamp trong cửa sổ, và `usedNonces.add(nonce)` thêm nonce lần đầu.
2. **Exact replay = false:** payload và HMAC vẫn đúng, nhưng `add` trên nonce đã tồn tại trả `false`. Đây là phần **anti-replay** mà HMAC một mình không cung cấp.
3. **Tampered body = false:** đối tượng giữ tag cũ trong khi `body` đã đổi; xác minh HMAC thất bại ngay trước khi xem replay set.
4. **Stale request = false:** request mới có tag đúng và nonce mới, nhưng timestamp quá cũ 301 giây nên bị từ chối.
5. `MessageDigest.isEqual` là API chuẩn để so sánh byte tag thay vì tự viết vòng lặp dừng ngay tại byte khác; xác minh vẫn phải giới hạn độ dài/định dạng đầu vào và tránh lộ thông tin lỗi nhạy cảm.

### Giới hạn có chủ ý của chương trình

- `ConcurrentHashMap.newKeySet()` có thao tác `add` atomic **trong một JVM**; dữ liệu mất khi process restart, không chia sẻ giữa server node và không có TTL. Đây là **demo**, chưa phải anti-replay triển khai production.
- `now` cố định chỉ để bài thực hành có kết quả lặp lại. Hệ thống thật dùng đồng hồ kiểm soát được (ví dụ `Clock` UTC được inject để test) và chính sách hết hạn/tolerance thích hợp.
- Khóa sinh tạm bằng `KeyGenerator`. Hệ thống thật cấp khóa cho mỗi principal theo quy trình quản lý khóa/KMS/keystore, lưu bí mật an toàn, hỗ trợ luân chuyển và không nhúng khóa thực vào source code/log.
- Demo dùng `String body`. Một HTTP service cần ký đúng **byte payload nhận được** hoặc canonical form đã định nghĩa, kiểm tra method/path/headers cần thiết và ràng buộc principal đã xác thực.
- Demo chưa thực thi nghiệp vụ, không có response idempotency và không giải quyết race giữa replay store với database. Không dùng hàm `verify` đơn lẻ này để bảo đảm giao dịch tài chính.
- Một request mới với **nonce mới** và body giống hệt request cũ có thể được chấp nhận: đó có thể là thao tác mới hợp lệ. Nếu nghiệp vụ yêu cầu “một đơn hàng chỉ bị trừ tiền một lần”, cần thêm **idempotency key/mã nghiệp vụ**.

## 6. Nếu chọn sequence number: quy tắc cập nhật phải atomic

Với giao thức chấp nhận mọi `seq > lastAccepted` và không cho replay, có thể biểu diễn khái niệm cập nhật trong một process bằng `AtomicLong`. **Chỉ chạy bước này sau khi xác minh tag bao gồm `seq` và đã xác định sender/session**:

```java
import java.util.concurrent.atomic.AtomicLong;

public class SequenceGate {
    private final AtomicLong lastAccepted = new AtomicLong(0);

    // Precondition: MAC/signature chứa seq đã được xác minh.
    public boolean accept(long seq) {
        while (true) {
            long last = lastAccepted.get();
            if (seq <= last) {
                return false;
            }
            if (lastAccepted.compareAndSet(last, seq)) {
                return true;
            }
        }
    }

    public static void main(String[] args) {
        SequenceGate gate = new SequenceGate();
        System.out.println(gate.accept(41));
        System.out.println(gate.accept(41));
        System.out.println(gate.accept(40));
        System.out.println(gate.accept(42));
    }
}
```

```text
javac --release 21 SequenceGate.java
java SequenceGate
```

```text
true
false
false
true
```

Ví dụ này là **gate in-memory** với một sender; do không xác minh MAC ở đây, không được gọi trực tiếp trên dữ liệu chưa được xác thực. Nó không đảm bảo thứ tự liên tiếp, không chấp nhận gói đến muộn sau một seq lớn hơn, và mất watermark khi restart. Với sản phẩm thật, lưu **(verified principal, key/session epoch, lastAccepted)** bằng compare-and-swap trong dữ liệu bền vững hoặc transaction có khóa; nếu cần out-of-order, dùng thuật toán sliding window được kiểm thử.

## 7. Thiết kế server thật: xác thực, chống replay và nghiệp vụ

Một pipeline thực tế có thể đi theo các bước:

```text
Nhận request
   |
   v
Parse có giới hạn kích thước, xác định keyId và principal
   |
   v
Lấy khóa từ registry đáng tin + kiểm tra quyền và bối cảnh
   |
   v
Tái tạo đúng bytes phải ký; xác minh HMAC/chữ ký
   | sai --> Reject
   v
Kiểm tra timestamp/expiry, nonce/seq, session/audience
   | sai --> Reject
   v
Giao dịch atomic: claim nonce/seq + xử lý một thao tác nghiệp vụ
   | duplicate --> Reject hoặc trả kết quả idempotent đã lưu
   v
Commit và trả response
```

Ở đây `claim` là thao tác **kiểm tra và ghi** replay key/counter nguyên tử: chỉ một request có cùng replay identity có thể giành được quyền xử lý, kể cả khi có nhiều server chạy đồng thời.

### 7.1 Ví dụ thiết kế dữ liệu cho nonce

Một bảng replay guard có thể dùng unique key:

```sql
CREATE TABLE replay_guard (
    principal_id  VARCHAR(128) NOT NULL,
    key_id        VARCHAR(128) NOT NULL,
    nonce         VARCHAR(128) NOT NULL,
    expires_at    TIMESTAMP NOT NULL,
    PRIMARY KEY (principal_id, key_id, nonce)
);
```

Khi request đã xác thực, mở transaction và thử `INSERT` một record; xung đột unique key nghĩa là nonce đã dùng. Trong **cùng ranh giới transaction**, ghi nhận trạng thái/kết quả và thay đổi nghiệp vụ cần bảo đảm một lần. Commit thành công mới coi request đã được chấp nhận bền vững. Với database khác, cú pháp UPSERT/transaction khác nhau nhưng ý nghĩa phải tương đương. Mã SQL trên thể hiện **schema**, không phải quy trình transaction hoàn chỉnh; production cần giới hạn input, kiểm soát quyền DB, cleanup theo expiry và xử lý lỗi cạnh tranh.

**Không dùng mẫu sai:**

```text
if (!replayStore.contains(key)) {
    businessAction();
    replayStore.put(key);
}
```

Hai luồng có thể cùng thấy “chưa tồn tại” rồi cùng chạy `businessAction()`. `SELECT` rồi `INSERT` tách rời không được bảo vệ bằng khóa/transaction cũng gặp race tương tự.

**Lưu ý lỗi giữa các bước:** nếu chiếm nonce ở Redis bằng `SET NX` rồi business transaction thất bại, client có thể không retry được dù chưa có hiệu ứng nghiệp vụ. Nếu business đã commit nhưng lưu nonce thất bại, có nguy cơ thực hiện lại. Thiết kế trạng thái `processing/succeeded/failed`, unique operation ID, response cache hoặc transaction chung để đạt ngữ nghĩa phù hợp. “Chỉ chấp nhận một lần” không đồng nghĩa “bảo đảm xử lý thành công đúng một lần”.

### 7.2 Đồng bộ nhiều server và TTL

- Mọi node xử lý cùng principal/phạm vi replay phải dùng **một nguồn trạng thái nhất quán**, ví dụ unique constraint trong DB hoặc thao tác atomic của shared store được thiết kế để chịu mất node/failover.
- TTL phải kéo dài **ít nhất đến thời điểm request không còn vượt qua điều kiện freshness**, có biên an toàn cho clock skew, replication và xử lý chậm. Không cho phép record bị xóa trong khi chính request đó vẫn nằm trong validity window.
- Giới hạn số request/nonce theo client, độ dài chuỗi và tốc độ gửi; nếu không, replay store có thể bị lợi dụng để làm cạn tài nguyên. Chỉ ghi nonce **sau khi xác thực thành công** để tránh yêu cầu rác chưa ký lấp bộ nhớ.
- Khi rotate key hoặc reset sequence, định nghĩa rõ `keyId`, epoch, yêu cầu xác thực và thời gian tồn tại của trạng thái cũ; không vô tình mở lại cửa cho thông điệp cũ.
- Lưu log theo event type, principal và request ID; hạn chế log khóa bí mật, raw token, bản rõ dữ liệu nhạy cảm hoặc toàn bộ MAC.

## 8. Replay, retry và idempotency là ba bài toán có liên quan

**Replay độc hại:** bên thứ ba phát lại gói hợp lệ để gây hiệu ứng phụ lặp. **Retry hợp lệ:** client gửi lại khi timeout vì không biết server đã thực hiện chưa. **Idempotency:** nhiều lần gọi cùng *logical operation* chỉ tạo ra một tác động nghiệp vụ theo quy tắc đã định.

Ví dụ client thanh toán gửi operation ID `order-123` với nonce `N1`. Server xử lý xong nhưng response bị mất; client gửi lại **chính request cũ**. Replay guard sẽ từ chối nonce `N1`, nhưng để hỗ trợ retry tốt, server có thể trả lại **response đã lưu** của `order-123` mà không thực hiện chuyển tiền lần hai. Nếu client gửi lại request với nonce **N2 mới**, chống replay tầng giao thức sẽ thấy đây là thông điệp mới; **idempotency key/unique business ID** vẫn phải ngăn thao tác nghiệp vụ bị lặp. Idempotency key phải được ràng buộc với principal và nội dung request: cùng key nhưng payload khác nên báo conflict thay vì âm thầm dùng kết quả cũ.

| Tình huống | Tag hợp lệ? | Nonce/seq check | Điều server nên làm |
| --- | --- | --- | --- |
| Request mới đúng chính sách | Có | Chưa thấy | Nhận và ghi nhận atomically, xử lý thao tác |
| Phát lại nguyên gói | Có | Đã thấy | Không thực thi lại |
| Sửa `amount` nhưng giữ tag cũ | Không | Không cần xét | Từ chối trước khi ghi replay state |
| Request có tag đúng nhưng timestamp hết hạn | Có | Có thể là nonce mới | Từ chối vì không còn freshness |
| Cùng đơn hàng, nonce mới, tag mới | Có | Chưa thấy | Kiểm tra idempotency nghiệp vụ để tránh charge hai lần |
| Hai node nhận cùng request đồng thời | Có | Cùng nonce | Claim atomic đảm bảo chỉ một node được xử lý |
| Counter cũ sau khi restart | Có | Cũ nếu state còn | Từ chối; mất state là lỗ hổng cần khắc phục |

### Ranh giới với TLS và AES-GCM

TLS bảo vệ dữ liệu trên đường truyền và có cơ chế record/sequence riêng. **Không suy ra** rằng một HTTP command đã được gửi lại qua một **kết nối TLS mới** sẽ bị lớp TLS tự loại bỏ. Yêu cầu idempotency/anti-replay ở tầng ứng dụng vẫn tùy thiết kế.

AES-GCM dùng **IV/nonce mật mã** và yêu cầu đặc biệt nghiêm ngặt về việc không tái sử dụng IV với cùng khóa. IV của AES-GCM là dữ liệu phục vụ mã hóa; **application replay nonce** là trường để bên nhận phát hiện thông điệp đã xử lý. Hai khái niệm có thể cùng xuất hiện trong một giao thức nhưng **không mặc định thay thế nhau**. Một bản mã AES-GCM hợp lệ được gửi lại, với tag hợp lệ, vẫn cần cơ chế replay state nếu ứng dụng muốn từ chối lần thứ hai.

## 9. Checklist kiểm thử và lỗi cần tránh

| Kiểm thử | Kỳ vọng |
| --- | --- |
| Gửi cùng request/tag/nonce hai lần | Lần hai không tạo hiệu ứng nghiệp vụ |
| Sửa body, method, path, timestamp hoặc nonce nhưng giữ tag | Xác thực thất bại |
| Timestamp vừa trong và ngay ngoài giới hạn | Xử lý đúng quy tắc lệch đồng hồ/hết hạn |
| Hai request cùng nonce tới hai node đồng thời | Tối đa một request được claim |
| Restart server khi request cũ còn hiệu lực | Replay vẫn bị từ chối nếu đã chấp nhận trước restart |
| Retry sau khi response bị mất | Không trừ tiền/ghi đơn hai lần; phản hồi phù hợp idempotency |
| Gửi sequence lệch thứ tự, thiếu, trùng, quá cửa sổ | Đúng chính sách strict hoặc sliding window đã công bố |
| Rotate key, đổi session/epoch, client nhiều thiết bị | Không mở lại state cũ hoặc trùng counter ngoài ý muốn |
| Replay sau TTL hoặc gần ranh giới skew | Bản cũ đã hết hạn trước khi record được xóa |
| Tag sai với nonce mới | Không chiếm nonce trong replay cache |
| Nonce dài bất thường/gửi liên tục | Giới hạn tài nguyên và có rate limit |

**Những sai lầm thường gặp cần nhớ:**

- “HMAC đúng thì chắc request mới”: **sai**, tag vẫn đúng khi phát lại nguyên gói.
- “Thêm UUID/nonce vào header là đủ”: **sai**, cần được xác thực và lưu trạng thái.
- “Timestamp ký đúng thì hết replay”: **sai**, còn cửa sổ chấp nhận thì còn khả năng gửi lại.
- “Lưu nonce vào `HashSet` trên từng server”: chỉ đủ cho demo một process; hệ thống nhiều node cần trạng thái chung atomic.
- “Kiểm tra nonce rồi xử lý rồi mới ghi”: có race giữa request đồng thời và có thể tạo double effect.
- “Counter tăng nhưng không ký”: kẻ tấn công có thể đổi thành số mới để vượt chốt.
- “Một lần xử lý cho mỗi nonce là một lần xử lý cho mỗi giao dịch”: **sai**, cùng giao dịch có thể được ký lại bằng nonce mới.
- “Nonce của HMAC và IV của AES-GCM đều gọi là nonce nên dùng thay nhau”: **sai**, chúng bảo vệ các thuộc tính khác nhau với ràng buộc khác nhau.

## 10. Bài tập củng cố

1. **Giải thích luồng replay:** vẽ 3 thông điệp giữa client, attacker và server; chỉ ra chính xác bước HMAC vẫn đúng và bước replay store từ chối nonce đã dùng.
2. **Thực hành Java:** chạy `ReplayDemo.java`. Thử bỏ dòng `usedNonces.add(...)` (chỉ trong bản lab) để thấy bản phát lại không còn được kiểm tra. Thử đổi `amount` nhưng giữ tag cũ rồi giải thích vì sao bị từ chối.
3. **Thiết kế theo tình huống:** một API nhận webhook từ nhiều nhà gửi, chạy 4 node và cho phép retry trong 24 giờ. Xác định ai cấp nonce, phạm vi replay key, TTL, nơi lưu shared state, cùng cách trả response cho duplicate.
4. **Chọn sequence hay nonce:** một luồng thông điệp UDP có thể đảo thứ tự, một API tạo đơn hàng song song, và một kênh command bắt buộc thứ tự. Với mỗi tình huống, chọn cơ chế, giải thích trường nào phải ký và cách xử lý thông điệp tới muộn.
5. **Bài toán khó:** nếu server kiểm tra timestamp `now-300 <= issuedAt <= now+30`, tại thời điểm nhận `now` đã chấp nhận message có `issuedAt=now+30`, record nonce cần sống đến lúc nào để chống replay? Giải thích vì sao “TTL đúng 300 giây từ lúc nhận” không luôn đủ.
6. **Đánh giá giao dịch:** mô tả điều gì xảy ra nếu cache đã đánh dấu nonce nhưng DB rollback, hoặc DB commit nhưng cache chưa ghi; đề xuất một unique operation ID + transaction/response cache để tránh double charge.

### Tài liệu tham khảo

- [Java SE 21 — `javax.crypto.Mac`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html): API HMAC, khởi tạo khóa, xử lý và xuất tag.
- [Java SE 21 — `java.security.SecureRandom`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html): nguồn byte ngẫu nhiên cho nonce.
- [Java SE 21 — `MessageDigest.isEqual`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html#isEqual(byte%5B%5D,byte%5B%5D)): so sánh byte digest/tag.
- [RFC 2104 — HMAC](https://www.rfc-editor.org/rfc/rfc2104): thuật toán message authentication code dựa trên hash.
- [RFC 9421, mục 7.2.2 — Signature Replay](https://www.rfc-editor.org/rfc/rfc9421.html#section-7.2.2): nonce/timestamp để giảm phát lại thông điệp ký.
- [NIST SP 800-38D — GCM và GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final): đặc tính của AEAD và yêu cầu IV/nonce cho GCM.

**Tự đánh giá:** Nếu một request với MAC/chữ ký hợp lệ xuất hiện lần thứ hai, bạn đã có cơ chế **đủ trạng thái, đủ atomic, đúng phạm vi và tồn tại đủ lâu** để quyết định có được thực thi lại hay không? Trả lời được câu hỏi này mới thực sự phân biệt xác thực thông điệp với chống phát lại.


</details>

- [Quay lại đầu trang](#back-to-top)
