<a id="back-to-top"></a>

# 6. Mã hóa đối xứng

## Menu
- [6.1 Block cipher, stream cipher, mode và padding](#symmetric-encryption-001)
- [6.2 Chỉ định đầy đủ transformation thay vì phụ thuộc giá trị mặc định của provider](#symmetric-encryption-002)
- [6.3 Tạo khóa AES bằng KeyGenerator và dựng khóa từ vật liệu hợp lệ](#symmetric-encryption-003)
- [6.4 Chu trình Cipher.init, update, doFinal và tính toán bộ đệm đầu ra](#symmetric-encryption-004)
- [6.5 Phân biệt ECB, CBC, CTR và yêu cầu bảo vệ tính toàn vẹn của bản mã](#symmetric-encryption-005)
- [6.6 AEAD, AES/GCM/NoPadding và vai trò của authentication tag](#symmetric-encryption-006)
- [6.7 GCMParameterSpec, độ dài tag và yêu cầu không lặp IV dưới cùng một khóa](#symmetric-encryption-007)
- [6.8 Truyền AAD trước dữ liệu; phân biệt dữ liệu được xác thực với dữ liệu được mã hóa](#symmetric-encryption-008)
- [6.9 ChaCha20, ChaCha20-Poly1305 và quản lý nonce](#symmetric-encryption-009)
- [6.10 Xử lý AEADBadTagException, BadPaddingException và bản mã bị cắt](#symmetric-encryption-010)
- [6.11 Giới hạn của CipherInputStream khi cần phát hiện lỗi xác thực AEAD](#symmetric-encryption-011)
- [6.12 Đóng gói IV, tham số, ciphertext, tag và chỉ sử dụng bản rõ sau khi xác thực thành công](#symmetric-encryption-012)

## <a id="symmetric-encryption-001">6.1 Block cipher, stream cipher, mode và padding</a>

<details>
<summary>Xem chi tiết</summary>

# 6.1 Block cipher, stream cipher, mode và padding

## 1. Vì sao cần phân biệt bốn khái niệm?

**Mã hóa đối xứng** sử dụng khóa bí mật để mã hóa và giải mã. Tuy nhiên, **thuật toán nguyên thủy**, **mode**, **padding** và **xác thực** không phải một thứ. Chọn khóa AES dài 256 bit chưa quyết định cách ứng dụng xử lý một file nhiều megabyte hoặc biết ciphertext có bị sửa hay không.

Bài này tạo nền tảng cho chương: nhận diện AES là block cipher, ChaCha20 là stream cipher, hiểu mode của AES và khi nào cần padding, rồi phân biệt mã hóa thuần với mã hóa có xác thực (AEAD). Những bài tiếp theo sẽ chuyên sâu vào transformation/provider, key management, chu trình Cipher, ECB/CBC/CTR, GCM và ChaCha20. Các ví dụ ở đây tập trung **hình thái dữ liệu và nguyên tắc lựa chọn** thay vì lặp lại triển khai chuyên sâu của từng mode.

Mục tiêu sau bài:

1. Giải thích AES có block 128 bit dù key có thể là 128/192/256 bit.
2. Mô tả stream cipher sinh keystream và XOR với dữ liệu, cùng hậu quả của việc tái sử dụng keystream.
3. Nêu ý nghĩa và khác biệt của ECB, CBC, CTR, GCM trong cách dùng AES nhiều block.
4. Tính đúng số byte padding CBC và phân biệt padding với authentication tag.
5. Phân biệt key, IV/nonce, counter, AAD, tag và biết khi nào cần AEAD.
6. Dự đoán kết quả demo Java 17+ cho block cipher với input không tròn block và stream cipher với input 17 byte.

## 2. Block cipher: mã hóa từng khối có kích thước cố định

**Block cipher** biến đổi một khối plaintext có độ dài xác định thành một khối ciphertext cùng độ dài theo khóa bí mật:

```text
EncryptBlock(K, P) -> C
DecryptBlock(K, C) -> P
```

**AES** có block size cố định **128 bit = 16 byte**. Ba biến thể thường dùng chỉ khác **kích thước khóa**:

| Tên AES | Khóa | Block AES |
| --- | ---: | ---: |
| AES-128 | 128 bit = 16 byte | 128 bit = 16 byte |
| AES-192 | 192 bit = 24 byte | 128 bit = 16 byte |
| AES-256 | 256 bit = 32 byte | 128 bit = 16 byte |

Vì vậy “AES-256” **không** có nghĩa AES xử lý một block 32 byte. Nếu plaintext có 11 byte, phép mã hóa một block thô không tự biết thêm hoặc ghép dữ liệu ra sao. Nếu plaintext là file 1 GB, thuật toán block cơ sở cũng không tự định nghĩa thứ tự và quan hệ giữa các block.

Để xử lý thông điệp dài cần một **mode of operation**. Một số mode dùng IV/nonce, một số dùng padding; những quyết định này ảnh hưởng trực tiếp đến bảo mật.

### Vì sao không mã hóa độc lập từng block?

Nếu cùng khóa AES được dùng cho cùng block plaintext thì đầu ra của phép mã hóa block cơ sở giống nhau. Nếu toàn bộ thông điệp được chia thành các block rồi mã hóa độc lập theo ECB, hai block plaintext bằng nhau sẽ tạo hai block ciphertext bằng nhau. Kẻ quan sát có thể phát hiện mẫu lặp dù không đọc được byte plaintext. **Tăng độ dài khóa không loại bỏ lỗi lộ mẫu của ECB.**

## 3. Stream cipher: luồng khóa và phép XOR

**Stream cipher** sinh ra một chuỗi byte giả ngẫu nhiên mật mã (keystream) từ khóa và các tham số, rồi XOR với plaintext:

```text
keystream = Stream(K, nonce, counter, ...)
C = P XOR keystream
P = C XOR keystream
```

Vì xử lý theo byte/luồng, plaintext 17 byte thường tạo 17 byte ciphertext, **không cần đệm cho đủ block AES 16 byte**.

**ChaCha20** là một stream cipher hiện đại: biến thể IETF dùng **khóa 256 bit**, **nonce 96 bit** và bộ đếm block. ChaCha20 thuần chỉ mã hóa; **ChaCha20-Poly1305** thêm Poly1305 để có xác thực AEAD. Hai tên này không được sử dụng thay thế cho nhau.

Nguy cơ nguy hiểm nhất ở mode dựa trên keystream là dùng lại cùng vùng keystream. Nếu hai plaintext khác nhau cùng XOR với `S`:

```text
C1 = P1 XOR S
C2 = P2 XOR S
=> C1 XOR C2 = P1 XOR P2
```

Kẻ quan sát có thể suy ra quan hệ giữa hai plaintext, và từ một phần plaintext đã biết có thể suy ra phần còn lại. Do đó, với stream cipher và AES-CTR, **nonce/counter phải được quản lý để không tái dùng vùng keystream dưới cùng khóa**. Không dùng IV/counter mặc định là toàn 0 lặp lại cho các message.

**Cảnh báo:** stream cipher có thể được giải mã thành công ngay cả khi ciphertext bị sửa một bit. Muốn chống sửa đổi, cần cơ chế xác thực như AEAD hoặc sơ đồ được thiết kế an toàn; không xem phép giải mã thành công là kiểm tra toàn vẹn.

## 4. Mode: cách sử dụng AES cho một thông điệp

Một **mode** xác định cách phối hợp nhiều block và các tham số như IV/counter, chứ không làm thay đổi kích thước block AES. Bảng so sánh chỉ nêu đặc điểm nền tảng:

| Mode | Cách xử lý | IV/nonce | Padding | Có authentication tag sẵn? |
| --- | --- | --- | --- | --- |
| ECB | Mỗi block độc lập | Không | Có nếu không tròn block | **Không** |
| CBC | Chaining với ciphertext block trước | IV mới, khó dự đoán cho encryption | Thường dùng PKCS5Padding | **Không** |
| CTR | Mã hóa counter block thành keystream rồi XOR | Counter block không trùng dưới cùng khóa | Không | **Không** |
| GCM | Counter mode kết hợp cơ chế xác thực GCM | IV/nonce duy nhất mỗi lần encrypt theo khóa | Không | **Có** |

**ECB:** đơn giản để minh họa một phép mã hóa block nhưng làm lộ các block plaintext lặp. **Không chọn ECB để mã hóa dữ liệu ứng dụng.**

**CBC:** dùng IV (với AES thường là 16 byte) và nối trạng thái các block, khắc phục lộ mẫu theo cách của ECB khi IV phù hợp. Tuy nhiên, CBC thuần **không xác thực dữ liệu**. Sửa IV/ciphertext có thể tác động đến plaintext; lộ lỗi padding có thể tạo padding oracle. Nếu bắt buộc dùng CBC legacy, cần cơ chế xác thực đúng như Encrypt-then-MAC và kiểm MAC **trước** khi giải mã.

**CTR:** lấy keystream từ các counter block; ciphertext dài bằng plaintext. Nếu counter block lặp dưới cùng khóa, keystream lặp và an toàn bị phá vỡ. CTR không tự phát hiện bit-flipping.

**GCM:** một chế độ **AEAD (Authenticated Encryption with Associated Data)**, bảo vệ bí mật và kiểm tra toàn vẹn/xác thực bằng **authentication tag**. Trong Java thường dùng `AES/GCM/NoPadding`, nonce 12 byte và tag 128 bit, với yêu cầu tuyệt đối **không lặp nonce khi mã hóa cùng khóa**. GCM không tự quản lý key, quyền nghiệp vụ hay chống phát lại một packet hợp lệ.

Điểm phân định: ECB/CBC/CTR giúp hiểu cơ chế mode và rủi ro của **encryption-only**; AES-GCM minh họa lựa chọn AEAD thường phù hợp hơn cho dữ liệu mới. Phân tích tấn công CBC/CTR và HMAC Encrypt-then-MAC được học sâu ở bài 6.5.

## 5. Padding chỉ xử lý độ dài, không phải xác thực

Một số mode block như ECB/CBC cần plaintext có độ dài bội số 16 byte nếu dùng `NoPadding`. `PKCS5Padding` trong Java JCE dùng quy tắc đệm block kiểu PKCS#7 tương thích AES: thêm `n` byte, mỗi byte có giá trị `n`. Nếu plaintext đã tròn block, thêm **cả một block padding** để có cách bỏ padding không mơ hồ.

| Plaintext | Padding thêm với AES/CBC/PKCS5Padding | Ciphertext |
| ---: | ---: | ---: |
| 0 byte | 16 byte | 16 byte |
| 1 byte | 15 byte | 16 byte |
| 11 byte | 5 byte | 16 byte |
| 15 byte | 1 byte | 16 byte |
| 16 byte | 16 byte | 32 byte |
| 17 byte | 15 byte | 32 byte |

Với 11 byte input, padding năm byte có giá trị hex `05 05 05 05 05`. Với 16 byte, thêm nguyên 16 byte có giá trị `10`. **Padding hợp lệ không chứng minh ciphertext chưa bị sửa**; người tấn công có thể tác động vào CBC ngay cả khi thao tác giải mã không báo exception.

**NoPadding có các nghĩa thực tế khác nhau:**

- `AES/ECB/NoPadding` và `AES/CBC/NoPadding` cần input bội số 16 byte; phần cuối không đủ block có thể ném `IllegalBlockSizeException`.
- `AES/CTR/NoPadding` dùng keystream và mã hóa được plaintext 17 byte thành 17 byte.
- `AES/GCM/NoPadding` không đệm block nhưng **vẫn tạo tag xác thực**; tag 128 bit thêm 16 byte vào output ciphertext.
- ChaCha20/ChaCha20-Poly1305 không cần PKCS block padding; biến thể AEAD có tag riêng.

**Padding, Base64 và tag là ba việc khác nhau.** Base64/hex chỉ là biểu diễn binary dưới dạng ký tự. Tag/MAC kiểm tính xác thực theo khóa, không phải ký tự được thêm để ciphertext có độ dài tròn block.

## 6. Key, IV/nonce, counter, AAD và tag

| Thành phần | Vai trò | Bí mật hay công khai? |
| --- | --- | --- |
| **Key** | Bí mật chi phối mã hóa/giải mã, tag | Phải giữ bí mật; sinh từ CSPRNG/KeyGenerator hoặc KMS/keystore; có rotation |
| **IV/nonce** | Tham số phân biệt lần mã hóa/trạng thái mode | Thường công khai; CBC cần IV mới khó đoán, GCM/ChaCha20 cần không lặp dưới cùng khóa |
| **Counter** | Chọn khối keystream tiếp theo trong CTR/ChaCha20 | Không cần bí mật nhưng không được tái dùng cùng vùng dưới một khóa |
| **AAD** | Metadata được xác thực nhưng không mã hóa | Có thể công khai; phía giải mã phải có **cùng byte** và đúng thứ tự |
| **Tag** | Giá trị xác thực ciphertext và AAD | Có thể công khai; tag sai thì **không được chấp nhận plaintext** |

Một bản ghi GCM có thể lưu `version || keyId || nonce || ciphertext || tag`; **không** lưu raw key cạnh ciphertext. Key ID chỉ giúp chọn khóa, không phải secret. Khi giải mã, cần đúng key/nonce/AAD/tag, và phải xác minh tag thành công trước khi sử dụng plaintext hoặc kích hoạt side effect.

GCM tag hợp lệ **không tự chống replay**: nếu một thông điệp đã được xác thực bị phát lại nguyên vẹn, phép kiểm tra tag vẫn có thể thành công. Chống replay cần sequence/expiry/request ID cùng trạng thái nghiệp vụ riêng.

## 7. Java 17+: kiểm tra sự khác nhau của block và stream

Ví dụ độc lập **chỉ minh họa kích thước**, **không dùng ECB làm cơ chế lưu dữ liệu thực tế**. Chương trình kiểm tra AES xử lý đúng một block 16 byte, từ chối plaintext 17 byte khi chọn NoPadding, và kiểm tra ChaCha20 mã hóa/giải mã 17 byte mà không cần padding.

Lưu tệp **`BlockVsStreamDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class BlockVsStreamDemo {
    public static void main(String[] args) throws Exception {
        KeyGenerator aesKeys = KeyGenerator.getInstance("AES");
        aesKeys.init(128);
        SecretKey aes = aesKeys.generateKey();
        byte[] block = "0123456789abcdef"
                .getBytes(StandardCharsets.US_ASCII);

        // ECB here only demonstrates one raw AES block.
        Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
        ecb.init(Cipher.ENCRYPT_MODE, aes);
        System.out.println("AES block bytes: " + block.length);
        System.out.println("AES one-block output: " + ecb.doFinal(block).length);

        boolean rejected = false;
        try {
            ecb.doFinal("seventeen-byte-xx"
                    .getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalBlockSizeException expected) {
            rejected = true;
        }
        System.out.println("AES incomplete block rejected: " + rejected);

        SecureRandom rng = new SecureRandom();
        byte[] rawKey = new byte[32];
        byte[] nonce = new byte[12];
        rng.nextBytes(rawKey);
        rng.nextBytes(nonce);
        SecretKey chacha = new SecretKeySpec(rawKey, "ChaCha20");
        ChaCha20ParameterSpec params =
                new ChaCha20ParameterSpec(nonce, 1);
        byte[] message = "1234567890abcdefg"
                .getBytes(StandardCharsets.US_ASCII);

        Cipher enc = Cipher.getInstance("ChaCha20");
        enc.init(Cipher.ENCRYPT_MODE, chacha, params);
        byte[] ciphertext = enc.doFinal(message);

        Cipher dec = Cipher.getInstance("ChaCha20");
        dec.init(Cipher.DECRYPT_MODE, chacha, params);
        byte[] recovered = dec.doFinal(ciphertext);

        System.out.println("Stream input bytes: " + message.length);
        System.out.println("ChaCha20 ciphertext bytes: " + ciphertext.length);
        System.out.println("Stream round-trip: "
                + Arrays.equals(message, recovered));
        Arrays.fill(rawKey, (byte) 0);
    }
}
```

Chạy bằng JDK 17+ có provider hỗ trợ AES và ChaCha20:

```bash
javac --release 17 BlockVsStreamDemo.java
java BlockVsStreamDemo
```

Stdout kiểm chứng bằng các lệnh JDK tương đương:

```text
AES block bytes: 16
AES one-block output: 16
AES incomplete block rejected: true
Stream input bytes: 17
ChaCha20 ciphertext bytes: 17
Stream round-trip: true
```

**Đọc kết quả:** AES/ECB/NoPadding không nhận một thông điệp 17 byte chưa được xử lý độ dài, còn ChaCha20 thuần tạo một keystream có đúng 17 byte để XOR với message. Key và nonce được sinh ngẫu nhiên, vì vậy ciphertext thật thay đổi nhưng các dòng độ dài và round-trip ổn định. ChaCha20 thuần ở đây **không xác thực ciphertext**; không dùng demo như encryption scheme production. `Arrays.fill` chỉ xóa buffer gốc chứ không chắc xóa bản sao trong `SecretKeySpec` hay provider.

## 8. Sơ đồ quyết định và lỗi thường gặp

**Nếu đang thiết kế tính năng mã hóa mới:** xác định trước cần chỉ bí mật hay cả toàn vẹn, phạm vi quản lý khóa, nonce và rủi ro sửa dữ liệu. Với nhiều ứng dụng, hãy chọn một AEAD chuẩn như AES-GCM hoặc ChaCha20-Poly1305, rồi xác định version/key ID/nonce/tag/AAD và kiểm thử tamper/round-trip. Không tự ghép mode và hash công khai để tạo cơ chế xác thực.

**Nếu đang đọc dữ liệu legacy:** kiểm tra chính xác mode/padding/IV/khóa/format đã dùng, không đoán provider mặc định từ `Cipher.getAlgorithm()`. Không thể chuyển ciphertext ECB/CBC cũ sang GCM bằng việc chỉ thay tên transformation ở reader. Cần chiến lược tương thích và migration có xác thực.

Các sai lầm cần nhận diện:

1. AES-256 có block 256 bit: **sai**, block AES luôn 128 bit.
2. ECB tự che mẫu dữ liệu khi dùng khóa mạnh: **sai**.
3. CBC/CTR giải mã được nghĩa là bản mã không bị sửa: **sai**.
4. NoPadding đồng nghĩa không có tag: **sai** với GCM.
5. Dùng lại IV/nonce/counter dưới cùng key không nguy hiểm: **sai**, đặc biệt đối với GCM và stream cipher.
6. AAD là dữ liệu đã được mã hóa: **sai**, AAD chỉ được xác thực.
7. Tag hợp lệ đồng nghĩa người dùng có quyền: **sai**, authorization độc lập.
8. Dùng `Cipher.getInstance("AES")` luôn cho GCM: **sai**, mode/padding mặc định phụ thuộc provider.

### Câu hỏi tự kiểm tra

1. AES-256 có kích thước khóa và block lần lượt bao nhiêu byte; vì sao?
2. Stream cipher xử lý 17 byte khác một phép AES block thô như thế nào?
3. Vì sao ECB lộ mẫu lặp dù khóa dài hơn?
4. Với CBC/PKCS5Padding, plaintext 16 byte vì sao tạo ciphertext 32 byte?
5. Điều gì xảy ra khi CTR hoặc ChaCha20 tái sử dụng cùng vùng keystream?
6. Vì sao GCM NoPadding vẫn có authentication tag, và tag sai phải dẫn tới hành vi gì?
7. Key, nonce, AAD và tag khác nhau về tính bí mật và khả năng xác thực ra sao?
8. Tại sao AEAD không tự chặn replay, phân quyền hoặc quản lý vòng đời khóa?

## Tài liệu tham khảo

- [Java SE 17 — Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): transformation, padding và AEAD.
- [Java SE 17 — ChaCha20ParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/ChaCha20ParameterSpec.html): nonce/counter.
- [Java SE 17 — Java Cryptography Architecture](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): thuật toán và provider.
- [NIST SP 800-38A](https://csrc.nist.gov/pubs/sp/800/38/a/final): ECB, CBC, CTR.
- [NIST SP 800-38D](https://csrc.nist.gov/pubs/sp/800/38/d/final): GCM và giới hạn nonce/tag.
- [RFC 8439](https://www.rfc-editor.org/rfc/rfc8439.html): ChaCha20 và ChaCha20-Poly1305.

**Kết luận:** block cipher mô tả primitive xử lý khối, stream cipher dùng keystream, mode định nghĩa cách mã hóa thông điệp, padding giải quyết độ dài ở những mode cần block. **Cần xác thực dữ liệu khi ciphertext có thể bị sửa**, không chỉ giải mã ra byte.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-002">6.2 Chỉ định đầy đủ transformation thay vì phụ thuộc giá trị mặc định của provider</a>

<details>
<summary>Xem chi tiết</summary>

# 6.2 Chỉ định đầy đủ transformation thay vì phụ thuộc giá trị mặc định của provider

## 1. Vấn đề: `"AES"` chưa mô tả một phép mã hóa hoàn chỉnh

Trong Java Cryptography Architecture (JCA/JCE), lớp `javax.crypto.Cipher` là **API (engine)** dùng chung cho nhiều phép mã hóa. Khi gọi `Cipher.getInstance(...)`, ứng dụng yêu cầu một **transformation**: thuật toán được dùng cùng cách vận hành và xử lý dữ liệu.

Với block cipher phổ biến như AES, ba thành phần quan trọng là:

1. **Algorithm** — thuật toán nguyên thủy, ví dụ `AES` (khối 128 bit).
2. **Mode** — cách xử lý nhiều khối và sử dụng IV/nonce, ví dụ `ECB`, `CBC`, `GCM`, `CTR`.
3. **Padding** — cách xử lý phần dữ liệu không đủ một khối, ví dụ `PKCS5Padding` hoặc `NoPadding`.

Java `Cipher` chấp nhận cả `"algorithm/mode/padding"` lẫn `"algorithm"`:

```java
// Các dòng minh họa trong phương thức có xử lý checked exception:
Cipher ambiguous = Cipher.getInstance("AES");
Cipher explicit = Cipher.getInstance("AES/GCM/NoPadding");
```

Hai lời gọi **không tương đương**. `"AES"` chỉ nêu thuật toán; **provider quyết định mode và padding mặc định**. `"AES/GCM/NoPadding"` yêu cầu chính xác mode GCM và không dùng padding khối. Khi chạy trên JDK/provider khác hoặc sau khi đổi thứ tự provider, `"AES"` có thể không còn mang ý nghĩa như người viết từng giả định.

**Quy tắc nền tảng:** Đối với thuật toán có nhiều mode/padding, nêu rõ transformation phù hợp giao thức, ví dụ `Cipher.getInstance("AES/GCM/NoPadding")`, và cấu hình khóa, IV/nonce, tag, AAD một cách chủ ý. Chỉ viết đủ ba phần của tên chưa tự động làm toàn bộ hệ thống an toàn.

Một số cipher có tên hoàn chỉnh mà **không dùng ba phần**, ví dụ `ChaCha20-Poly1305`: dùng tên chuẩn của chính thuật toán đó, không tự thêm `/mode/padding` vô nghĩa. “Đầy đủ” nghĩa là không để lại **mặc định quan trọng chưa được đặc tả**, không phải máy móc đếm số dấu `/`.

## 2. Provider ảnh hưởng đến transformation thế nào?

### 2.1 Engine API và provider

`Cipher.getInstance(transformation)` nhờ JCA/JCE tìm provider đang đăng ký có thể cung cấp phép mã hóa phù hợp. Provider có thể đi cùng JDK (chẳng hạn `SunJCE` trong Oracle/OpenJDK), thuộc thư viện riêng, tích hợp hệ điều hành hoặc truy cập HSM.

```text
Ứng dụng: Cipher.getInstance("AES/GCM/NoPadding")
                           |
                           v
                Cipher engine của JCA
                           |
             tìm implementation phù hợp
                           |
            +--------------+--------------+
            |                             |
     Provider A hỗ trợ              Provider B không hỗ trợ
            |                             |
        AES-GCM SPI                    bỏ qua
```

Nếu ứng dụng chỉ yêu cầu `"AES"`, provider có thể áp dụng **mode/padding mặc định**; Java SE không quy định một cấu hình mặc định an toàn chung cho mọi provider.

Tài liệu **Oracle JDK 17 JDK Providers Documentation** nêu `SunJCE` và `SunPKCS11` mặc định **ECB** với **PKCS5Padding** cho nhiều symmetric cipher. Trong trường hợp `SunJCE`, tài liệu JCA cho ví dụ `Cipher.getInstance("AES")` tương đương `Cipher.getInstance("AES/ECB/PKCS5Padding")`. Đó là mô tả của provider/bản JDK tương ứng, **không phải hợp đồng bắt buộc với mọi JDK**.

ECB tiết lộ mẫu lặp của các khối dữ liệu cùng khóa; vì vậy việc vô tình nhận ECB từ `"AES"` có thể tạo lỗ hổng dù code biên dịch, chạy và giải mã được trên máy lập trình viên.

### 2.2 Đừng dựa vào `getAlgorithm()` để suy ra mode mặc định

```java
Cipher unspecified = Cipher.getInstance("AES");
String requested = unspecified.getAlgorithm(); // Tên đã yêu cầu: "AES"
```

`getAlgorithm()` cung cấp **tên đã yêu cầu**, không phải báo cáo chuẩn hóa rằng provider đã chọn chính xác những thông số ngầm định nào. `getProvider()` cho biết provider của đối tượng (và ở một số implementation có thể kích hoạt lựa chọn provider), nhưng **tên provider không thay cho transformation**. Cần đọc tài liệu provider và kiểm thử mode/tham số thật khi kiểm toán hệ thống kế thừa.

Khi dùng overload `Cipher.getInstance("AES/GCM/NoPadding", "SunJCE")`, ứng dụng phụ thuộc vào provider có tên đó và đã được đăng ký. Chỉ pin provider khi có yêu cầu rõ như HSM hoặc kiểm định, còn lại nên yêu cầu **transformation chuẩn xác** và để JCA tìm provider tương thích.

## 3. So sánh các yêu cầu Cipher thường gặp

| Transformation | Ý nghĩa đã xác định | Lưu ý về bảo mật và khả chuyển |
| --- | --- | --- |
| `"AES"` | Chỉ thuật toán AES | **Mơ hồ**: provider tự chọn mode/padding. Tránh dùng để mã hóa dữ liệu ứng dụng. |
| `"AES/ECB/PKCS5Padding"` | AES, ECB, padding tương thích JCE | Rõ ràng về mặt cú pháp nhưng **không phù hợp cho dữ liệu nhiều khối**, vì ECB làm lộ quan hệ khối lặp. |
| `"AES/CBC/PKCS5Padding"` | AES, CBC, padding được nêu | Cần IV hợp lệ và **xác thực/toàn vẹn riêng**. CBC không tự phát hiện dữ liệu bị chỉnh sửa có chủ đích. |
| `"AES/GCM/NoPadding"` | AES, GCM, không có block padding | AEAD hỗ trợ mã hóa + tag xác thực và AAD. Cần nonce **duy nhất với mỗi khóa**. |
| `"AES/CTR/NoPadding"` | AES, counter mode, không padding | Cần counter/nonce đúng và **MAC hoặc cơ chế xác thực riêng** nếu cần chống chỉnh sửa. |
| `"AES/CFB/NoPadding"` | AES, CFB, không padding | Kích thước **feedback segment** có thể do provider quyết định; dùng ví dụ `AES/CFB8/NoPadding` nếu giao thức bắt buộc CFB8 và provider hỗ trợ. |

### 3.1 Rõ ràng chưa chắc an toàn

Thay `"AES"` bằng `"AES/ECB/PKCS5Padding"` chỉ giúp biết chắc mode/padding đang dùng, nhưng không chữa nguy cơ của ECB. Với hai block plaintext giống nhau, ECB và cùng khóa tạo hai block ciphertext giống nhau:

```text
      plaintext block P             plaintext block P
              |                             |
            AES(K)                        AES(K)
              |                             |
      ciphertext block C            ciphertext block C
```

CBC xóa bớt đặc điểm đó bằng chaining và IV, nhưng CBC **không cung cấp authentication tag**. Trường hợp thiết kế mới cần cả bí mật và toàn vẹn thường phù hợp hơn với AEAD như GCM.

`PKCS5Padding` là tên chuẩn dùng trong nhiều provider JCE cả với block AES 128 bit; không suy luận từ chữ “5” rằng AES chỉ có block 8 byte. `NoPadding` đối với GCM không có nghĩa “không xác thực”, cũng không yêu cầu plaintext phải chia hết cho 16 byte: `Cipher.doFinal` của GCM trả ciphertext **kèm tag**.

## 4. Full transformation còn cần tham số đầy đủ

`Cipher.getInstance("AES/GCM/NoPadding")` đã cố định mode/padding, nhưng một **bản ghi mã hóa có thể tương tác** còn phải quy định:

| Thành phần | Ví dụ nhất quán | Quy tắc quan trọng |
| --- | --- | --- |
| Transformation | `AES/GCM/NoPadding` | Không lấy mode/padding mặc định của provider. |
| Kích thước khóa | AES-128, khóa 16 byte | `KeyGenerator.getInstance("AES")` dùng tên ở engine **KeyGenerator**; không phải yêu cầu `Cipher` mode mặc định. |
| Nonce/IV | 12 byte | Phải **không lặp lại với cùng một key GCM**; lưu/truyền cùng dữ liệu mã hóa. |
| Tag length | 128 bit | `new GCMParameterSpec(128, nonce)` dùng đơn vị **bit**. |
| AAD | Byte như `"record:v1"` | Metadata xác thực nhưng không mã hóa. Phải trùng nguyên byte khi giải mã. |
| Output format | `ciphertext || authentication tag` | Theo API `Cipher` GCM, tag được nối vào ciphertext khi mã hóa và kiểm tra khi giải mã. |
| Encoding/layout | UTF-8 + binary format có version/length | Người đọc phải tái lập cùng byte, không đoán từ giá trị mặc định của hệ điều hành. |

**Bốn điểm rất dễ thiếu khi viết code:**

1. `getInstance` mới chỉ có cipher engine. Gọi `init` với key và tham số đúng trước khi dùng `doFinal`; `getInstance` thành công không chứng minh key/IV/tag hợp lệ.
2. Trong một số mode, `init(ENCRYPT_MODE, key)` có thể làm provider tự sinh IV. Không lưu/truyền lại IV sẽ khiến dữ liệu khó hoặc không thể giải mã sau đó. Với GCM, nên có chiến lược sinh nonce của ứng dụng và truyền `GCMParameterSpec` rõ ràng.
3. `updateAAD` của GCM phải gọi **trước** `update`/`doFinal`. Bên nhận xác thực bằng chính xác cùng AAD bytes; nếu tag sai (`AEADBadTagException`), từ chối toàn bộ bản ghi.
4. Nonce GCM cần duy nhất theo mỗi khóa. Sinh nonce ngẫu nhiên 96 bit có xác suất va chạm khi số lượng bản ghi đủ lớn; hệ thống nhiều writer hoặc high-volume cần giới hạn sử dụng khóa, quy trình phối hợp/tách khóa hoặc cơ chế uniqueness có kiểm soát, không mặc định `SecureRandom` loại bỏ được mọi va chạm.

## 5. Ví dụ Java 17+: mã hóa GCM rõ ràng và kiểm tra toàn vẹn

Tạo file **`ExplicitTransformationDemo.java`**. Chương trình sử dụng AES-128-GCM, nonce 12 byte, tag 128 bit, AAD và kiểm thử hai trường hợp bị sửa. Đây là **chương trình độc lập**, khác với các đoạn minh họa ngắn ở trên.

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class ExplicitTransformationDemo {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private static final SecureRandom RNG = new SecureRandom();

    private static byte[] encrypt(SecretKey key, byte[] nonce,
                                  byte[] aad, byte[] plaintext)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(plaintext);
    }

    private static byte[] decrypt(SecretKey key, byte[] nonce,
                                  byte[] aad, byte[] ciphertextAndTag)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertextAndTag);
    }

    private static boolean rejected(SecretKey key, byte[] nonce,
                                    byte[] aad, byte[] ciphertextAndTag)
            throws GeneralSecurityException {
        try {
            decrypt(key, nonce, aad, ciphertextAndTag);
            return false;
        } catch (AEADBadTagException badTag) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey key = generator.generateKey();

        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);
        byte[] aad = "record:v1".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "Hello AES-GCM".getBytes(StandardCharsets.UTF_8);

        Cipher chosen = Cipher.getInstance(TRANSFORMATION);
        System.out.println("Transformation: " + chosen.getAlgorithm());

        byte[] ciphertextAndTag = encrypt(key, nonce, aad, plaintext);
        byte[] recovered = decrypt(key, nonce, aad, ciphertextAndTag);
        System.out.println("Round-trip: " + Arrays.equals(plaintext, recovered));
        System.out.println("Ciphertext+tag bytes: " + ciphertextAndTag.length);

        byte[] changedBody = ciphertextAndTag.clone();
        changedBody[0] ^= 1;
        System.out.println("Tampered body rejected: " +
                rejected(key, nonce, aad, changedBody));

        byte[] changedAad = aad.clone();
        changedAad[0] ^= 1;
        System.out.println("Tampered AAD rejected: " +
                rejected(key, nonce, changedAad, ciphertextAndTag));
    }
}
```

Dùng JDK 17 trở lên:

```bash
javac --release 17 ExplicitTransformationDemo.java
java ExplicitTransformationDemo
```

**Stdout** (các byte khóa/nonce là ngẫu nhiên nhưng các dòng sau ổn định):

```text
Transformation: AES/GCM/NoPadding
Round-trip: true
Ciphertext+tag bytes: 29
Tampered body rejected: true
Tampered AAD rejected: true
```

Giải thích:

- `KeyGenerator.getInstance("AES")` ở đây chỉ chọn **loại khóa**, sau đó `generator.init(128)` xác định 128 bit. Không có lý do thêm `/GCM/NoPadding` vào tên thuật toán của **KeyGenerator**; cú pháp ba phần thuộc yêu cầu Cipher.
- `"Hello AES-GCM"` là 13 byte UTF-8. Với tag GCM 128 bit (16 byte), `doFinal` khi mã hóa trả **13 + 16 = 29 byte**.
- Cả `encrypt` và `decrypt` đều yêu cầu chính xác `AES/GCM/NoPadding`, sử dụng cùng key, nonce, AAD nên dữ liệu phục hồi trùng từng byte.
- Thay 1 byte ciphertext hoặc AAD khiến authentication tag không hợp lệ, nên `rejected(...)` trả `true`. Đừng bỏ qua hoặc cố bắt lỗi tag rồi đưa plaintext chưa xác thực vào nghiệp vụ.
- Mỗi phương thức tạo `Cipher` mới cho một lượt thao tác. Nếu tái sử dụng key cho bản ghi tiếp theo, phải sử dụng **nonce khác** và quy định số lần mã hóa tối đa theo thiết kế bảo mật.

**Đây là ví dụ in-memory:** khóa được sinh mới mỗi lần chạy; để đọc lại dữ liệu đã lưu qua restart, ứng dụng phải quản lý khóa trong KMS/HSM/keystore và gắn metadata như `version`, `suite ID`, `key ID`, nonce và ciphertext/tag vào format được đặc tả. **Không lưu raw secret key cạnh ciphertext.** Nếu cần chống đưa ciphertext sang ngữ cảnh khác, ràng buộc tenant/object/purpose vào AAD; nếu cần chống replay thì phải có thêm cơ chế trạng thái/sequence number ngoài authentication tag.

## 6. Cách chọn và duy trì transformation trong dự án

### 6.1 Khi viết code mới

- Xuất phát từ yêu cầu: cần cả bí mật và toàn vẹn thì ưu tiên một **AEAD** phù hợp, ví dụ AES-GCM.
- Chọn **tên chuẩn và cấu hình** của từng engine: `Cipher.getInstance("AES/GCM/NoPadding")`, khóa AES-128/256 theo chính sách, nonce 96 bit và tag 128 bit nếu giao thức yêu cầu các mức này.
- Định nghĩa **định dạng dữ liệu** với version/suite/IV/tag và encoding rõ ràng; chỉ dùng tên mà cả bên mã hóa lẫn giải mã cùng biết.
- Đặt kiểm tra khả dụng vào kiểm thử triển khai hoặc startup. Thử khởi tạo với khóa và tham số thực, rồi round-trip và tamper test; không dừng ở `Cipher.getInstance(...)`.
- Không cứng tên provider trong code trừ khi chính sách hoặc phần cứng bắt buộc; nếu pin phải tài liệu hóa dependency và kiểm tra trên runtime đích.

### 6.2 Khi sửa ứng dụng cũ đang dùng `"AES"`

Phải **xác định format hiện tại trước khi đổi code**. Không thể thay toàn bộ `Cipher.getInstance("AES")` bằng GCM rồi kỳ vọng giải mã tự động các bản ghi vốn được mã hóa với ECB/CBC và padding khác.

Quy trình an toàn:

1. Xác định provider/JDK lúc mã hóa cũ, mode, padding, khóa, IV/nonce và cách ghi ciphertext; sử dụng fixture hoặc dữ liệu kiểm thử có kiểm soát để xác nhận. Không dùng `getAlgorithm()` làm bằng chứng đủ.
2. Định nghĩa **reader phiên bản cũ** chỉ khi thực sự cần tương thích, giới hạn phạm vi và cơ chế lỗi; không thử lần lượt các mode ngẫu nhiên cho đến khi giải được.
3. Định nghĩa **writer mới** có transformation, key strategy và version/suite rõ ràng; ưu tiên AEAD.
4. Nếu cần di trú dữ liệu cũ, đọc bằng reader được phép, kiểm tra toàn vẹn nếu format cũ có cơ chế đó, rồi ghi bản mới bằng nonce/key phù hợp. Ghi nhận hạn chế xác thực của dữ liệu legacy.
5. Không tiếp tục phát sinh bản ghi mới với cấu hình mặc định; lên kế hoạch loại bỏ reader cũ khi đã hoàn tất di trú.

### 6.3 Mức hỗ trợ Java SE và provider

Java SE nêu **tên thuật toán chuẩn** và một tập con **bắt buộc implementation hỗ trợ**. Tên xuất hiện trong *Standard Algorithm Names* không tự động có nghĩa mọi runtime đều có implementation. Java SE 17 có mức yêu cầu tối thiểu liên quan AES-GCM với khóa AES 128 bit; đối với thuật toán hoặc key-size khác, kiểm tra đặc tả đúng phiên bản cùng provider mục tiêu.

Những điều có thể thay đổi giữa runtime là provider, availability, giới hạn key/parameter, tăng tốc phần cứng và chi tiết cấu hình. Tên transformation rõ ràng giảm mơ hồ về phép mã hóa, nhưng vẫn cần **test interoperability** nếu một phía ghi bằng provider A và phía kia đọc bằng provider B.

## 7. Chẩn đoán lỗi theo đúng lớp vấn đề

| Ngoại lệ/triệu chứng | Thường xuất hiện ở đâu? | Cách suy luận và xử lý |
| --- | --- | --- |
| `NoSuchAlgorithmException` | `Cipher.getInstance` | Thuật toán/transformation không được provider/runtime phân giải; kiểm tra spelling, tên chuẩn và provider đã đăng ký. |
| `NoSuchPaddingException` | `Cipher.getInstance` | Padding được yêu cầu không được hỗ trợ cho transformation/provider đó. Ví dụ `AES/GCM/PKCS5Padding` không phải tên GCM thích hợp. |
| `NoSuchProviderException` | Overload có tên provider | Tên provider không có trong registry; cần cài/đăng ký provider hoặc bỏ ràng buộc nếu không cần. |
| `InvalidKeyException` | `Cipher.init` | Kiểu/kích thước/thuộc tính khóa không phù hợp hoặc bị chính sách hạn chế. |
| `InvalidAlgorithmParameterException` | `Cipher.init` | IV/nonce/tag/algorithm parameters không được chấp nhận. |
| `AEADBadTagException` | `Cipher.doFinal` giải mã AEAD | Dữ liệu, khóa, nonce hoặc AAD không đúng; **từ chối**, không trả plaintext và không đổi mode để thử lại. |
| `BadPaddingException` (CBC) | `Cipher.doFinal` | Khóa/IV/ciphertext/padding có thể sai. Padding **không phải kiểm tra toàn vẹn** và không nên làm lộ chi tiết cho bên không tin cậy. |

**Không fallback kiểu “GCM không có thì thử `AES`”.** Vì `"AES"` có mode mặc định không xác định bởi ứng dụng, fallback có thể âm thầm chuyển sang ECB. Khi runtime không đáp ứng một suite được thiết kế, báo lỗi rõ hoặc chọn một suite thay thế **đã phê duyệt trong giao thức**, với phiên bản/ID riêng và quy tắc tương thích được định nghĩa trước.

## 8. Những lỗi review mã hay bỏ sót

1. **Chọn algorithm-only:** `Cipher.getInstance("AES")` để mode/padding tùy provider.
2. **Khắc phục nửa vời:** thay thành `AES/ECB/PKCS5Padding` để “ổn định” nhưng vẫn lộ mẫu dữ liệu nhiều khối.
3. **Dùng CBC mà không có MAC:** dữ liệu có thể bị chỉnh sửa và sinh lỗi padding; không có authenticity của AEAD.
4. **Không lưu IV:** có ciphertext nhưng thiếu nonce/IV để giải mã; ngược lại, IV có thể công khai và phải được lưu đúng cách.
5. **Tái dùng nonce GCM cùng khóa:** vi phạm điều kiện bảo mật, có thể dẫn đến tấn công giả mạo/tiết lộ dữ liệu.
6. **Nhầm `NoPadding` là “không có tag”:** GCM vẫn xác thực và trả tag; plaintext dài 13 byte hoàn toàn hợp lệ.
7. **Dùng `updateAAD` sau `update`:** trái với yêu cầu AEAD của API Cipher.
8. **Kết luận mode từ `getAlgorithm()` hoặc tên provider:** hai thông tin này không phải đặc tả đầy đủ của mode/padding ngầm định.
9. **Chỉ test `getInstance`:** không test `init`, `doFinal`, tag, nonce, key size và khả năng đọc từ môi trường khác.
10. **Đổi mode trong code nhưng không version hóa ciphertext:** bản ghi cũ không còn giải mã theo writer mới.
11. **Lấy transformation từ file/request chưa xác thực:** cho đầu vào quyết định phép mã hóa; chỉ dùng allowlist suite cố định.
12. **In key/nonce/cleartext nhạy cảm ra log:** ví dụ học tập không nên trở thành luồng log trong production. Nonce không bí mật nhưng vẫn cần bảo toàn và đúng ngữ cảnh.

## 9. Checklist tự kiểm tra

- [ ] Tất cả `Cipher.getInstance` cho dữ liệu lưu/truyền đều nêu transformation chuẩn, đủ chính xác theo giao thức.
- [ ] Transformation được **chọn vì tính an toàn**, không chỉ vì có thể gọi được trên JDK đang dùng.
- [ ] Cỡ khóa, IV/nonce, tag, AAD, padding/feedback parameters được quy định rõ và test với provider đích.
- [ ] Nonce của GCM duy nhất theo từng khóa; khóa được quản lý riêng, có kế hoạch luân chuyển và giới hạn số lần dùng.
- [ ] Không trả plaintext chưa được xác thực; tampering và sai AAD bị từ chối.
- [ ] Dữ liệu có version/suite/nonce/encoding, giải mã không lệ thuộc mode/padding mặc định.
- [ ] Không fallback tự động sang ECB/CBC khi suite yêu cầu bị thiếu; xử lý lỗi theo hợp đồng bảo mật.
- [ ] Có kiểm thử cross-provider/JDK và trường hợp legacy khi cần di trú.

**Tự đánh giá:**

- `Cipher.getInstance("AES")` có mặc định GCM ở mọi JDK? **Không.**
- `AES/ECB/PKCS5Padding` có là transformation đầy đủ? **Có**, nhưng không có nghĩa an toàn cho dữ liệu nhiều khối.
- `AES/GCM/NoPadding` đã bao gồm việc sinh và lưu nonce đúng cách? **Chưa.** Code và định dạng dữ liệu chịu trách nhiệm.
- Hai phía dùng cùng transformation nhưng khác AAD có giải mã GCM thành công? **Không** khi AAD được xác thực đúng.
- `KeyGenerator.getInstance("AES")` có cùng kiểu mơ hồ với `Cipher.getInstance("AES")`? **Không**: `KeyGenerator` là engine sinh khóa, không có mode/padding.

## Tài liệu tham khảo

- [Java SE 17 — `javax.crypto.Cipher`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): cú pháp transformation, mặc định provider, AEAD, thứ tự `updateAAD`, nonce GCM và exceptions.
- [Oracle JDK 17 — JDK Providers Documentation](https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html): phần **Cipher Transformations**, ví dụ `SunJCE`/`SunPKCS11` và khả năng provider.
- [Java SE 17 — Java Cryptography Architecture Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): engine API, SPI/provider, mode và padding.
- [Java SE 17 — Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): tên chuẩn và yêu cầu triển khai tối thiểu.
- [Java SE 17 — `GCMParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): `tLen` tính bằng bit, IV/nonce.
- [RFC 5116 — Authenticated Encryption with Associated Data](https://www.rfc-editor.org/rfc/rfc5116): cách hiểu nonce, AAD và AEAD.

**Kết luận học tập:** Yêu cầu `Cipher.getInstance("AES")` gửi một quyết định quan trọng cho provider. Yêu cầu `Cipher.getInstance("AES/GCM/NoPadding")` đặt lại quyết định đó trong mã nguồn và giao thức, sau đó phải hoàn thiện đúng key, nonce, AAD, tag, lưu trữ và kiểm thử.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-003">6.3 Tạo khóa AES bằng KeyGenerator và dựng khóa từ vật liệu hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# 6.3 Tạo khóa AES bằng KeyGenerator và dựng khóa từ vật liệu hợp lệ

## 1. Bản chất khóa AES

AES là thuật toán mã hóa **đối xứng**: bên mã hóa và giải mã cần cùng khóa bí mật. Trong JCA/JCE, khóa AES thường được đại diện bởi giao diện `javax.crypto.SecretKey` và được cung cấp cho `Cipher.init(...)`.

| AES | Độ dài tính bằng bit | Raw key bytes |
| --- | ---: | ---: |
| AES-128 | 128 bit | 16 byte |
| AES-192 | 192 bit | 24 byte |
| AES-256 | 256 bit | 32 byte |

AES luôn có kích thước **block 128 bit**, bất kể dùng AES-128/192/256. Độ dài key cũng khác IV/nonce (AES-GCM thường dùng IV 12 byte) và authentication tag (thường là 128 bit). **Key phải bí mật**, IV không cần bí mật nhưng **không được lặp khi mã hóa với cùng một key**.

Phân biệt ba đầu vào:

1. **Cần tạo khóa mới ngẫu nhiên:** dùng `KeyGenerator`.
2. **Đã có đúng raw AES key từ nguồn an toàn:** kiểm tra 16/24/32 byte rồi dựng `SecretKeySpec`.
3. **Chỉ có password hoặc master secret:** phải thực hiện **key derivation** thích hợp, không bọc trực tiếp byte của password thành key.

Chiều dài đúng mới chỉ chứng minh key có hình dạng hợp lệ. Mảng 32 byte toàn số 0 hoặc một password dễ đoán không bảo mật chỉ vì dài 32 byte.

## 2. Sinh khóa AES mới với KeyGenerator

`KeyGenerator` là engine của JCA để tạo khóa bí mật. Đoạn mã minh họa nằm trong một phương thức Java:

```java
KeyGenerator generator = KeyGenerator.getInstance("AES");
generator.init(256, new SecureRandom());
SecretKey aesKey = generator.generateKey();
```

Các import cần có: `javax.crypto.KeyGenerator`, `javax.crypto.SecretKey` và `java.security.SecureRandom`. Chương trình hoàn chỉnh ở phần 5 có đầy đủ mã nguồn.

- `getInstance("AES")` chọn engine/provider cung cấp AES; `NoSuchAlgorithmException` xảy ra nếu runtime không hỗ trợ.
- `init(256, random)` thiết lập **256 bit**, không phải 256 byte. AES có các độ dài khóa hợp lệ 128/192/256 bit; provider có thể áp dụng hạn chế riêng.
- `SecureRandom` tạo randomness dành cho mật mã. Không dùng `java.util.Random`, `Math.random()` hay seed cố định để cố tạo khóa tái lập.
- `generateKey()` trả `SecretKey` mới; không tự tạo IV cho từng bản ghi và không tự lưu key để dùng lại sau restart.
- Hãy `init` tường minh để tránh phụ thuộc lựa chọn kích thước mặc định của provider. Overload `init(int)` có thể sử dụng RNG do provider chọn, nhưng overload có `SecureRandom` làm rõ nguồn sinh.

Sai: `new SecretKeySpec(new byte[32], "AES")` vì `new byte[32]` toàn **zero**. Nếu giao thức riêng buộc phải tạo raw bytes, có thể dùng `SecureRandom.nextBytes(raw)` với đúng 16/24/32 byte; với JCA thông thường, `KeyGenerator` dễ đọc và bảo trì hơn.

## 3. Dựng khóa từ raw key hợp lệ bằng SecretKeySpec

Giả sử ứng dụng đã nhận raw key bytes từ một kho khóa hay giao thức được xác thực. Hãy kiểm tra trước khi dựng khóa:

```java
// Đoạn minh họa; raw là byte[] nguồn tin cậy.
if (raw == null ||
        (raw.length != 16 && raw.length != 24 && raw.length != 32)) {
    throw new IllegalArgumentException("Invalid AES key length");
}
SecretKey key = new SecretKeySpec(raw, "AES");
```

`SecretKeySpec` **sao chép** các key bytes và gắn tên thuật toán. Nó **không tự sinh randomness, không dẫn xuất key và không đánh giá entropy**. Constructor có thể chấp nhận nguyên liệu có độ dài không tương thích với AES; `Cipher.init` mới có thể ném `InvalidKeyException`. Vì thế phải validate length ở ranh giới nhận dữ liệu và xử lý cả lỗi provider.

**Encoding khác raw bytes:**

- Raw AES-256 key có **32 byte**; dạng **hex dài 64 ký tự** vì mỗi byte chiếm hai ký tự.
- Base64 của 32 byte thường có **44 ký tự**, kể cả padding `=`. Hex/Base64 **chỉ là biểu diễn**, không mã hóa bảo mật.
- Nhập từ hệ thống bên ngoài: `Base64.getDecoder().decode(base64Key)` hoặc `HexFormat.of().parseHex(hexKey)` (Java 17+), rồi validate **độ dài mảng đã decode**.
- Không gọi `base64Key.getBytes(UTF_8)` để tạo AES key: đó là bytes của **chữ mã hóa**, không phải raw key.

Mã minh họa decode Base64 (không phải chương trình độc lập, vì `base64Key` do caller cung cấp):

```java
byte[] raw = java.util.Base64.getDecoder().decode(base64Key);
try {
    if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
        throw new IllegalArgumentException("Wrong AES key size");
    }
    SecretKey imported = new SecretKeySpec(raw, "AES");
    // Dung imported trong pham vi can thiet.
} finally {
    java.util.Arrays.fill(raw, (byte) 0);
}
```

Chỉ nhập raw key nếu đã xác thực **nguồn, định dạng, quyền dùng và bảo mật kênh truyền**. Không ghi log raw key hay chuỗi Base64/hex của nó. Một số khóa do HSM/PKCS#11/KMS hoặc provider đặc biệt quản lý **không export được**, `SecretKey.getEncoded()` có thể trả `null`. Với loại khóa đó, dùng trực tiếp `SecretKey`/key handle và provider tương thích, không cố dựng `SecretKeySpec`.

**Giới hạn xóa bộ nhớ:** `SecretKeySpec` giữ bản sao key bytes; `Arrays.fill(raw, 0)` chỉ xóa mảng `raw` của ứng dụng, không bảo đảm xóa bản sao trong key object hoặc provider. `String` immutable khó làm sạch hơn mảng; hạn chế xuất/copy key, giữ thời gian sống ngắn và dùng KMS/HSM khi chính sách yêu cầu.

## 4. Password chưa phải AES key: cần một KDF

Sai lầm quen thuộc là lấy bytes UTF-8 của password đủ 16/32 byte rồi tạo `SecretKeySpec`. Một password dễ đoán như `"1234567890123456"` vẫn dễ bị brute force dù có **16 byte**. Một phép `SHA-256(password)` trực tiếp cũng chưa phải password KDF phù hợp: hàm băm nhanh cho phép thử nhiều mật khẩu offline.

Trong Java 17+, `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` cùng `PBEKeySpec` hỗ trợ dẫn xuất khóa từ password:

```text
password (char[]) -----+
                       +-- PBKDF2-HMAC-SHA256(salt, iterations, 256 bits)
random salt (16B+) ----+                         |
                                                 v
                                        32 raw key bytes
                                                 |
                                                 v
                                      SecretKeySpec(raw, "AES")
```

- **Salt** ngẫu nhiên, ví dụ 16 byte từ `SecureRandom`; không cần bí mật, nhưng phải lưu lại để có thể tái tạo cùng key khi giải mã. Salt mới với cùng password ⇒ key mới.
- **Iterations** làm tăng chi phí thử password. Giá trị `210_000` trong chương trình là minh họa API; cần điều chỉnh theo threat model, hiệu năng và chính sách cập nhật, không coi là hằng số được khuyến nghị vĩnh viễn.
- `PBEKeySpec(password, salt, iterations, 256)` yêu cầu **256 bit**, tạo 32 byte. Hai lần derive cùng password/salt/iterations/algorithm/length cho cùng key.
- Lưu **version, tên KDF, salt, iteration count và output length** với định dạng bản ghi. Nếu thay tham số rồi cố giải mã ciphertext cũ, key tái tạo sẽ khác.
- Dùng `char[]` thay vì giữ password lâu trong `String` khi ứng dụng cho phép; `PBEKeySpec.clearPassword()` và `Arrays.fill(...)` làm sạch các mảng có thể kiểm soát, nhưng không bảo đảm mọi bản sao nội bộ đã bị xóa.

Các thư viện hiện đại có thể hỗ trợ KDF tốn bộ nhớ như **Argon2id** theo chính sách; Java SE JCA không bảo đảm sẵn tên thuật toán này. Với master secret ngẫu nhiên cần tạo nhiều key con, dùng KDF/context separation theo giao thức thích hợp (chẳng hạn HKDF), không tự dùng byte của password hoặc một phép hash nhanh làm giải pháp chung.

## 5. Ví dụ chạy được: KeyGenerator, SecretKeySpec và AES-GCM

Lưu thành **`AesKeySourcesDemo.java`**, Java 17+. Chương trình tạo AES-256 key, **chỉ vì mục đích giáo dục** lấy raw bytes qua `getEncoded()`, dựng một `SecretKeySpec` từ chính raw bytes đó, rồi mã hóa bằng key gốc và giải mã bằng key nhập lại.

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
public class AesKeySourcesDemo {
    static SecretKey fromRawAesKey(byte[] raw) {
        if (raw == null || (raw.length != 16 && raw.length != 24 && raw.length != 32)) {
            throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes");
        }
        return new SecretKeySpec(raw, "AES");
    }
    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256, random);
        SecretKey generated = generator.generateKey();
        byte[] raw = generated.getEncoded();
        if (raw == null) throw new IllegalStateException("Key is not exportable");
        try {
            SecretKey imported = fromRawAesKey(raw);
            System.out.println("generatedBits=" + raw.length * 8);
            System.out.println("algorithm=" + imported.getAlgorithm());
            System.out.println("sameMaterial=" + MessageDigest.isEqual(raw, imported.getEncoded()));
            try {
                fromRawAesKey(new byte[17]);
                System.out.println("invalidLengthRejected=false");
            } catch (IllegalArgumentException expected) {
                System.out.println("invalidLengthRejected=true");
            }
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            byte[] aad = "record:7".getBytes(StandardCharsets.UTF_8);
            byte[] message = "Hello AES".getBytes(StandardCharsets.UTF_8);
            Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
            encrypt.init(Cipher.ENCRYPT_MODE, generated, new GCMParameterSpec(128, iv));
            encrypt.updateAAD(aad);
            byte[] ciphertextAndTag = encrypt.doFinal(message);
            Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
            decrypt.init(Cipher.DECRYPT_MODE, imported, new GCMParameterSpec(128, iv));
            decrypt.updateAAD(aad);
            byte[] recovered = decrypt.doFinal(ciphertextAndTag);
            System.out.println("roundTrip=" + Arrays.equals(message, recovered));
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }
}
```

```bash
javac --release 17 -encoding UTF-8 AesKeySourcesDemo.java
java AesKeySourcesDemo
```

```text
generatedBits=256
algorithm=AES
sameMaterial=true
invalidLengthRejected=true
roundTrip=true
```

Đầu ra ổn định dù key và IV ngẫu nhiên vì không in bytes bí mật. `sameMaterial=true` chứng minh bản sao raw key giống key gốc; `roundTrip=true` chứng minh cùng key/IV/AAD có thể mã hóa và giải mã đúng. **Không** chứng minh nguồn bên ngoài tin cậy, quyền truy cập hay lưu khóa đã an toàn.

`AES/GCM/NoPadding` là transformation được chỉ rõ; `Cipher.getInstance("AES")` ngắn gọn có thể để provider chọn mode/padding mặc định không mong muốn (chẳng hạn ECB). Với AES-GCM, nonce thường 12 byte và tag thường 128 bit; `GCMParameterSpec(128, iv)` dùng **128 bit cho tag**, không phải kích thước key. Khi mã hóa bản ghi **mới dưới cùng key**, phải chọn **IV mới không trùng** và gọi `Cipher.init` lại. Không được lấy việc engine reset sau `doFinal` làm lý do tái sử dụng cặp key+IV.

## 6. Ví dụ chạy được: tạo raw AES key từ password bằng PBKDF2

Lưu thành **`PasswordDerivedAesDemo.java`**. Password trong code chỉ phục vụ demo chạy độc lập, không hardcode như vậy trong ứng dụng thật.

```java
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
public class PasswordDerivedAesDemo {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static byte[] deriveRawKey(char[] password, byte[] salt) throws Exception {
        if (salt == null || salt.length < 16) {
            throw new IllegalArgumentException("Salt must be >= 16 bytes");
        }
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] derived = factory.generateSecret(spec).getEncoded();
            if (derived == null || derived.length != 32) {
                throw new IllegalStateException("Expected 256 bits of derived key material");
            }
            return derived;
        } finally {
            spec.clearPassword();
        }
    }
    public static void main(String[] args) throws Exception {
        char[] password = "example-password".toCharArray();
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] first = null;
        byte[] second = null;
        byte[] changedSalt = null;
        try {
            first = deriveRawKey(password, salt);
            second = deriveRawKey(password, salt);
            salt[0] ^= 1;
            changedSalt = deriveRawKey(password, salt);
            SecretKey aesKey = new SecretKeySpec(first, "AES");
            System.out.println("keyBits=" + first.length * 8);
            System.out.println("algorithm=" + aesKey.getAlgorithm());
            System.out.println("sameInputsSameKey=" + MessageDigest.isEqual(first, second));
            System.out.println("changedSaltChangesKey=" + !MessageDigest.isEqual(first, changedSalt));
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(salt, (byte) 0);
            if (first != null) Arrays.fill(first, (byte) 0);
            if (second != null) Arrays.fill(second, (byte) 0);
            if (changedSalt != null) Arrays.fill(changedSalt, (byte) 0);
        }
    }
}
```

```bash
javac --release 17 -encoding UTF-8 PasswordDerivedAesDemo.java
java PasswordDerivedAesDemo
```

```text
keyBits=256
algorithm=AES
sameInputsSameKey=true
changedSaltChangesKey=true
```

Cùng password + salt + iterations + KDF + output length sẽ tái tạo **cùng raw AES key**. Nếu khi giải mã dữ liệu cũ lại sinh salt mới, không còn cùng key. Khi đổi password, ứng dụng cần thiết kế re-encryption hoặc re-wrapping đúng giao thức; bản mã cũ không tự giải mã bằng key mới.

## 7. Khóa được lưu trữ hoặc quản lý ở đâu?

| Nguồn | Hành động đúng |
| --- | --- |
| Sinh khóa ngẫu nhiên trong process | Dùng `SecretKey` trực tiếp; cần chiến lược lưu/khôi phục nếu phải giải mã sau restart |
| Raw AES key đã được cấp an toàn | Decode (nếu có), validate 16/24/32 byte, tạo `SecretKeySpec` |
| Password | KDF salt/cost phù hợp, lưu tham số để có thể tái tạo |
| Java `KeyStore`, HSM hoặc PKCS#11 | Dùng `SecretKey` hay key handle qua provider; không giả định export được |
| Cloud KMS | Tuân thủ API, key ID/permissions và chính sách không xuất raw key |
| Master secret entropy cao | Tách khóa theo ngữ cảnh và mục đích bằng KDF của giao thức |

`KeyStore` là **API** quản lý key entries; mức bảo vệ phụ thuộc loại store và provider (ví dụ PKCS12, PKCS11), không mặc nhiên là HSM. Tách quyền **dùng key** khỏi quyền **xuất raw key**. Khi xoay khóa, lưu **key ID/version** cùng metadata bản mã; dữ liệu mã hóa bằng key cũ thường cần key cũ hoặc quy trình re-encrypt/rewrap có kiểm soát.

Một bản ghi AES-GCM cần có đủ **key ID, IV, ciphertext+tag, AAD (hoặc quy tắc tạo AAD)** và version định dạng. Nếu nguồn là password, bổ sung **KDF algorithm/salt/iterations/output bits**. Không lưu raw key như metadata ở nơi người đọc ciphertext có thể xem được.

## 8. Lỗi thường gặp và cách tránh

| Vấn đề | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `NoSuchAlgorithmException` | Provider thiếu thuật toán AES hoặc PBKDF2 | Xác minh tên và provider/runtime |
| `InvalidParameterException` tại `KeyGenerator.init` | Độ dài key không phù hợp/chính sách hạn chế | Chọn 128/192/256 bit được hỗ trợ |
| `IllegalArgumentException` khi import | Base64/hex sai hoặc raw key length sai | Decode đúng định dạng, kiểm tra **số byte** |
| `InvalidKeyException` tại `Cipher.init` | SecretKey không hợp lệ cho thuật toán/provider | Xác minh key algorithm, size, provider |
| `InvalidAlgorithmParameterException` | Tham số IV/tag/mode sai | Xác minh transformation và `GCMParameterSpec` |
| `AEADBadTagException` khi giải mã | Key, IV, AAD hoặc ciphertext/tag sai | Từ chối bản ghi, không dùng plaintext chưa xác thực |
| `getEncoded() == null` | Key không export được | Dùng trực tiếp key handle và provider hỗ trợ |
| Mất quyền giải mã sau restart | Không lưu đúng key/version/salt/IV | Thiết kế key management và metadata trước khi mã hóa |

Không chia sẻ cùng một `Cipher` đang xử lý giữa nhiều thread/request. Đừng log bí mật hoặc trả lỗi nội bộ quá chi tiết cho người ngoài; khi GCM xác thực thất bại thì loại bỏ plaintext chưa được xác thực.

### Checklist trước khi đưa vào ứng dụng

- Khóa mới do `KeyGenerator`/CSPRNG sinh, hoặc raw key đã xác thực nguồn và có chiều dài hợp lệ.
- Password luôn đi qua KDF với salt và cost; có chiến lược lưu/khôi phục tham số.
- `Cipher` dùng transformation xác định rõ, ví dụ `AES/GCM/NoPadding`; IV duy nhất trong phạm vi mỗi key mã hóa.
- Có key ID/version, chính sách lưu giữ, sao lưu, giới hạn quyền và rotation.
- Không hardcode, log, export vô cớ hoặc lưu raw key chung nơi có ciphertext.
- Có test key length sai, PBKDF2 cùng/khác salt, AES-GCM round-trip và GCM tag bị thay đổi.

## 9. Tự kiểm tra

1. AES-128/192/256 có bao nhiêu raw key bytes? **16/24/32**.
2. Tham số `256` của `KeyGenerator.init(256)` là bit hay byte? **Bit**.
3. `SecretKeySpec` có tăng entropy hoặc tự sinh key không? **Không**.
4. Chuỗi 64 ký tự hex của AES-256 cần decode thành bao nhiêu raw bytes? **32**.
5. Password 16 ký tự được dùng trực tiếp làm AES-128 key có an toàn không? **Không bảo đảm**; cần KDF thích hợp.
6. Vì sao phải lưu PBKDF2 salt? Để **dẫn xuất lại cùng key** khi giải mã, ngoài các tham số khác.
7. GCM có được tái dùng IV khi vẫn dùng cùng key không? **Không**.
8. `getEncoded()==null` có nghĩa gì? Key có thể **không export được**, cần provider/handle tương thích.
9. Xóa mảng `raw` có xóa bản sao trong `SecretKeySpec` không? **Không**.
10. Key mới có tự giải mã ciphertext của key cũ không? **Không**; cần version/key cũ hoặc quy trình di chuyển dữ liệu.

## Tài liệu Java SE 21

- [KeyGenerator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KeyGenerator.html)
- [SecretKey](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/SecretKey.html)
- [SecretKeySpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/SecretKeySpec.html)
- [SecureRandom](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html)
- [SecretKeyFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/SecretKeyFactory.html)
- [PBEKeySpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html)
- [Cipher](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
- [GCMParameterSpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html)
- [KeyStore](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyStore.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-004">6.4 Chu trình Cipher.init, update, doFinal và tính toán bộ đệm đầu ra</a>

<details>
<summary>Xem chi tiết</summary>

# 6.4. Chu trình `Cipher.init`, `update`, `doFinal` và tính toán bộ đệm đầu ra

## Mục tiêu

Bài này giải thích vòng đời **`javax.crypto.Cipher`** trong Java Cryptography Architecture (JCA) khi mã hóa và giải mã đối xứng. Trọng tâm là cách dữ liệu đi qua **`init` → `update` → `doFinal`**, cách tính chỗ chứa output bằng **`getOutputSize`**, và cách dùng đúng các overload nhận mảng/`ByteBuffer` khi không biết mỗi lần `update` sẽ phát ra bao nhiêu byte.

Sau khi học, bạn có thể:

- Khởi tạo `Cipher` đúng chiều mã hóa/giải mã, khóa, IV/nonce và tham số thuật toán.
- Xử lý thông điệp trong **một phần hoặc nhiều phần** mà không bỏ sót output của `update` và `doFinal`.
- Phân biệt output của block cipher có padding với output của AEAD như AES-GCM, bao gồm authentication tag.
- Cấp phát buffer dựa trên **`getOutputSize(inputLen)`** và theo dõi **số byte thực tế đã ghi**.
- Xử lý `ShortBufferException`, `BadPaddingException`, `AEADBadTagException` và tránh reuse IV/nonce, nhận plaintext trước khi được xác thực.

Mã trong bài dùng **Java 17+**, `AES/GCM/NoPadding` làm ví dụ chính; `AES/CBC/PKCS5Padding` chỉ dùng để minh họa block padding và sizing. **AES-GCM được ưu tiên cho dữ liệu cần đồng thời bảo mật và xác thực**, với key/nonce được quản lý đúng cách.

## 1. Một `Cipher` đi qua những trạng thái nào?

`Cipher` là **engine có trạng thái**, không phải hàm biến đổi byte độc lập. Trước khi xử lý payload, phải chọn transformation và gọi `init`:

```java
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
```

Đây là **snippet**, trong đó `key` là `SecretKey` AES và `nonce` là mảng 12 byte hợp lệ, đã tạo riêng cho thao tác. Cần import `javax.crypto.Cipher` và `javax.crypto.spec.GCMParameterSpec`, đồng thời xử lý checked exceptions.

`Cipher.getInstance("AES/GCM/NoPadding")` chọn **transformation đầy đủ** gồm thuật toán, mode, padding. Tránh chỉ ghi `"AES"` vì cách diễn giải mode/padding mặc định phụ thuộc provider và có thể không phù hợp về bảo mật.

Các bước chính:

```text
Cipher.getInstance(transformation)
       |
       v
chưa init: KHÔNG update/doFinal
       |
       v
init(ENCRYPT_MODE/DECRYPT_MODE, key, params)
       |
       +-- đối với AEAD: updateAAD(aad) trước dữ liệu payload
       |
       v
update(chunk 1) -> update(chunk 2) -> ... -> update(chunk n)
       |               có thể xuất 0 hoặc nhiều byte mỗi lần
       v
doFinal(last chunk hoặc không có thêm input)
       |
       +-- xử lý byte tồn đọng, padding hoặc tag
       +-- trả phần output CUỐI; với GCM decrypt, xác minh tag
       |
       v
hoàn tất: Cipher trở về trạng thái sau init nếu thành công
```

- `init(Cipher.ENCRYPT_MODE, ...)` khởi tạo để **mã hóa**; `init(Cipher.DECRYPT_MODE, ...)` khởi tạo để **giải mã**. Không gọi `update/doFinal` trước `init`; có thể nhận `IllegalStateException`.
- `init` còn có các mode `WRAP_MODE`/`UNWRAP_MODE` cho `wrap/unwrap` key, nhưng bài này tập trung `ENCRYPT_MODE`/`DECRYPT_MODE`.
- `update(...)` xử lý thêm một phần payload. **Số byte output một lần gọi không nhất thiết bằng số byte input**; provider có thể giữ block chưa đủ, chờ padding hoặc xác minh tag.
- `doFinal(...)` xử lý phần cuối và kết thúc phép biến đổi. Trong AES-GCM, mã hóa **gắn tag xác thực vào output**, giải mã **kiểm tra tag** và có thể ném `AEADBadTagException`.
- Sau `doFinal` thành công, theo API, `Cipher` được reset về **trạng thái đã init trước đó**. Tuy nhiên, với AES-GCM, **không được mã hóa thông điệp tiếp theo bằng cùng cặp key + nonce**: phải `init` cho mỗi encryption với nonce mới, ngay cả khi đối tượng có vẻ sẵn sàng tiếp tục.
- Nếu `doFinal` ném exception, không suy đoán state còn an toàn để sử dụng; xử lý lỗi và **khởi tạo lại** đối tượng cho một thông điệp mới.

Hình dung **một `init` và chuỗi các `update/doFinal` tương ứng với một message**. Không gọi `init` giữa các chunk của cùng message: làm vậy sẽ bỏ state của phần trước.

## 2. Vai trò của khóa, IV/nonce và AAD

`AES` dùng khóa đối xứng; để giải mã, bên nhận cần **cùng key**, mode, IV/nonce và các tham số liên quan. Với AES-GCM:

- **Key:** ví dụ AES-128 hoặc AES-256; phải lưu/truyền bằng cơ chế quản lý khóa phù hợp, **không đưa key vào log** và không nhúng key cố định trong source.
- **Nonce/IV:** thường dùng **12 byte (96 bit)** cho GCM. Mỗi lần mã hóa với **cùng key** phải có nonce **khác nhau**. Có thể tạo nonce từ `SecureRandom` cho minh họa/quy mô phù hợp; ở hệ thống sản lượng lớn cần thiết kế cấp phát nonce bảo đảm không trùng và chính sách xoay key.
- **Tag:** ví dụ `GCMParameterSpec(128, nonce)` đặt tag **128 bit = 16 byte**. Khi encryption kết thúc, `doFinal` trả thêm tag ở cuối ciphertext; khi decryption kết thúc, `doFinal` kiểm tra tag, và dữ liệu/khóa/nonce/AAD sai có thể dẫn đến xác thực thất bại.
- **AAD (Additional Authenticated Data):** dữ liệu **được xác thực nhưng không được mã hóa**, như version/header của protocol. Gọi `cipher.updateAAD(aad)` **trước** mọi `update/doFinal` xử lý payload, và bên giải mã phải cung cấp **chính xác cùng AAD**.
- **Ciphertext+tag:** đối với GCM, Java `Cipher` phát ra ciphertext cùng tag; nonce cần được truyền/lưu riêng (nonce không phải bí mật). Một định dạng đơn giản có thể là `version || nonce || ciphertext || tag` với quy tắc phân tích độ dài rõ ràng. Đừng lẫn lộn tag và nonce.

**Cảnh báo bắt buộc của GCM:** cùng key + nonce không được sử dụng cho hai lần mã hóa khác nhau. Việc `doFinal` “reset” state **không hợp thức hóa** việc reuse nonce. Khi đổi ciphertext/nonce/key/AAD hoặc tag, bên giải mã phải coi toàn bộ thông điệp **không hợp lệ** nếu kiểm tra tag thất bại.

## 3. Các overload của `update` và `doFinal`

| Phương thức | Input | Output | Điểm cần nhớ |
| --- | --- | --- | --- |
| `update(byte[] in)` | Cả mảng `in` | `byte[]`, **có thể `null`** nếu chưa phát ra block | Không được bỏ qua output khi nối nhiều chunk. |
| `update(byte[] in, int off, int len)` | Đoạn `[off, off+len)` | `byte[]`, có thể `null` | Tránh xử lý byte thừa của buffer tái dùng. |
| `update(in, off, len, out, outOff)` | Đoạn từ input | Ghi trực tiếp vào `out`; trả **số byte đã ghi** | Có thể trả 0; có thể ném `ShortBufferException`. |
| `update(ByteBuffer in, ByteBuffer out)` | Các byte còn lại của `in` | Ghi vào `out`, trả **số byte ghi** | Cập nhật position của cả hai buffer khi thành công. |
| `doFinal()` | Không thêm input; final dữ liệu đã `update` | `byte[]` | Bắt buộc để xử lý phần cuối, padding/tag. |
| `doFinal(byte[] in)` | Input cuối + dữ liệu đã buffer | `byte[]` | Có thể dùng **một lần** nếu không cần chia chunk. |
| `doFinal(in, inOff, inLen)` | Đoạn cuối | `byte[]` | Xử lý cả state từ những `update` trước. |
| `doFinal(out, outOff)` | Không thêm input | Ghi phần cuối vào `out`; trả số byte | Dùng sau các `update` đã ghi vào cùng output. |
| `doFinal(in, inOff, inLen, out, outOff)` | Input cuối | Ghi output cuối vào `out`; trả số byte | Phải còn đủ chỗ, tính đến byte đã buffer. |
| `doFinal(ByteBuffer in, ByteBuffer out)` | `in.remaining()` | Ghi trực tiếp vào `out` | Chú ý position/limit và space của output. |

**Khác `MessageDigest`:** `Cipher.update` có thể **phát output** ngay. Không thể chỉ gọi `update` rồi lấy output của `doFinal` cuối cùng nếu muốn toàn bộ ciphertext/plaintext. Khi dùng API trả `byte[]`, hãy **gom cả những mảng không null** theo đúng thứ tự. Khi dùng API ghi vào buffer có sẵn, hãy **cộng số byte đã ghi** sau mỗi lần.

Ví dụ sơ đồ xử lý ba chunk với output:

```text
in = [chunk1] [chunk2] [chunk3]
        |        |        |
     update   update    doFinal
        |        |        |
      out1     out2    out3 (gồm block cuối, padding/tag)
        \        |       /
         result = out1 || out2 || out3
```

Không giả định `out1.length == chunk1.length` hoặc `out2.length == chunk2.length`. Với block cipher có padding, `update` có thể trả `null`/0 vì chưa đủ dữ liệu; với GCM decrypt, một provider có thể giữ plaintext đến khi tag được xác minh. Ứng dụng **không được công bố/tiêu thụ plaintext chưa xác thực** trước khi `doFinal` thành công.

## 4. `Cipher.getOutputSize(inputLen)` là gì?

Phương thức:

```java
int capacity = cipher.getOutputSize(inputLen);
```

trả về kích thước buffer **đủ chứa output của lượt `update` hoặc `doFinal` sắp tới** với `inputLen` byte đầu vào, **có tính đến dữ liệu đã buffer ở các `update` trước**, padding và authentication tag của AEAD. Đây là **cận trên cần thiết**, **không phải** số byte chắc chắn sẽ ghi: kết quả thực có thể ngắn hơn.

**Các quy tắc áp dụng:**

1. **Phải `init` trước khi gọi `getOutputSize`**; nếu Cipher ở state không hợp lệ có thể ném `IllegalStateException`.
2. Khi đã biết số byte của toàn bộ message, có thể tính **một lần sau `init`, trước các `update`**: `byte[] output = new byte[cipher.getOutputSize(totalInputLength)]`. Sau đó nạp các chunk và cộng số byte được ghi qua các overload có output buffer.
3. Nếu đang giữa một quy trình, gọi `getOutputSize(remainingInputLength)` tại **state hiện tại** để dự trù kết quả cho thao tác tiếp theo, bao gồm dữ liệu đã tích lũy; **không dùng các cận trên tính từ thời điểm khác như thể chúng là số byte đã xuất ra**.
4. Dùng **số byte trả về thực tế** từ từng `update`/`doFinal` để xác định phần hợp lệ trong output. **Không** gửi/lưu toàn bộ mảng đã cấp phát nếu mới điền một phần.
5. Với `ByteBuffer`, `remaining()` là chỗ chứa còn lại, không phải `capacity()` tổng; `output.position()` tăng theo số byte ghi.
6. Nếu buffer không đủ, API ghi output có thể ném **`ShortBufferException`**. Theo Javadoc, sau lỗi thiếu buffer có thể gọi lại thao tác đó với output lớn hơn; nhưng sau lỗi xử lý/padding/authentication khác, phải thiết kế tái khởi tạo state rõ ràng.
7. Đừng tự suy `update` sẽ tạo bao nhiêu byte từ riêng `inputLen`. Kể cả khi biết block size, buffering/padding/AEAD khiến **thời điểm output xuất hiện** phụ thuộc implementation.

Mẫu tổng quát khi **đã biết `allInput.length`** (snippet trong phương thức đã khởi tạo `cipher`):

```java
byte[] allInput = /* bytes của một thông điệp */;
int split = allInput.length / 2;
byte[] output = new byte[cipher.getOutputSize(allInput.length)];
int written = 0;

written += cipher.update(allInput, 0, split, output, written);
written += cipher.update(
        allInput, split, allInput.length - split, output, written);
written += cipher.doFinal(output, written);

byte[] actualResult = Arrays.copyOf(output, written);
```

Snippet dùng `java.util.Arrays` và có xử lý exceptions; không phải chương trình độc lập. Với GCM, phải gọi `updateAAD` trước payload nếu protocol có AAD. Chỉ `actualResult` gồm `written` byte là dữ liệu hợp lệ; output dư sau `written` không thuộc thông điệp.

### Kích thước đầu ra ví dụ

| Transformation | Input plaintext | Output encryption thực tế | Lý do |
| --- | ---: | ---: | --- |
| `AES/GCM/NoPadding`, tag 128 bit | 0 byte | 16 byte | Tag 16 byte, ngay cả khi payload rỗng. |
| `AES/GCM/NoPadding`, tag 128 bit | 3 byte | 19 byte | 3 byte ciphertext + 16 byte tag. |
| `AES/GCM/NoPadding`, tag 128 bit | 16 byte | 32 byte | 16 byte ciphertext + 16 byte tag. |
| `AES/CBC/PKCS5Padding` | 0 byte | 16 byte | Padding lấp một block. |
| `AES/CBC/PKCS5Padding` | 3 byte | 16 byte | Padding lấp block AES 16 byte. |
| `AES/CBC/PKCS5Padding` | 16 byte | 32 byte | Khi input đã tròn block vẫn phải thêm block padding. |
| `AES/CBC/NoPadding` | 3 byte | Lỗi khi final hóa | Input chưa đủ bội số block 16 byte. |

Bảng mô tả **kích thước output cuối cùng** với cấu hình đã ghi và dữ liệu hợp lệ, không phải giả định kết quả `getOutputSize` của mọi provider ở mọi state luôn bằng các con số này. Với AES-CBC + `PKCS5Padding`, nếu input có `P` byte và block AES = 16 byte, output encryption sau hoàn tất có độ dài `16 × (floor(P / 16) + 1)`. Với AES-GCM tag 128 bit, output encryption có độ dài `P + 16`; tên `NoPadding` **không có nghĩa** không có tag.

Với decryption, kích thước plaintext chỉ biết chắc **sau khi đã bỏ padding/kiểm tra tag thành công**. `getOutputSize` cung cấp kích thước buffer đủ chứa, có thể **lớn hơn** số byte plaintext thực sự.

## 5. Ví dụ đầy đủ: AES-GCM qua nhiều `update` với output buffer

Chương trình sau tạo khóa AES-128 ngẫu nhiên và nonce GCM 12 byte cho **một lần mã hóa**. Nó chia plaintext thành hai chunk, cộng số byte `update` ghi, hoàn tất bằng `doFinal`, rồi dùng cùng key/nonce/AAD để **giải mã và kiểm tra tag**. Cipher giải mã được khởi tạo **riêng**. Một lần giải mã với AAD khác được thử để chứng minh xác thực thất bại.

Lưu **`CipherFlowDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class CipherFlowDemo {
    private CipherFlowDemo() {}

    public static void main(String[] args) throws Exception {
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(128);
        SecretKey key = keygen.generateKey();

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec params = new GCMParameterSpec(128, iv);

        byte[] aad = "header:v1".getBytes(StandardCharsets.UTF_8);
        byte[] plain = "Xin chao Cipher!".getBytes(StandardCharsets.UTF_8);

        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
        encrypt.init(Cipher.ENCRYPT_MODE, key, params);
        encrypt.updateAAD(aad);

        byte[] output = new byte[encrypt.getOutputSize(plain.length)];
        int split = 5;
        int used = encrypt.update(plain, 0, split, output, 0);
        used += encrypt.update(plain, split, plain.length - split, output, used);
        used += encrypt.doFinal(output, used);
        byte[] sealed = Arrays.copyOf(output, used);

        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
        decrypt.init(Cipher.DECRYPT_MODE, key, params);
        decrypt.updateAAD(aad);

        byte[] recovered = new byte[decrypt.getOutputSize(sealed.length)];
        int received = decrypt.update(sealed, 0, split, recovered, 0);
        received += decrypt.update(sealed, split, sealed.length - split,
                recovered, received);
        received += decrypt.doFinal(recovered, received);

        Cipher wrongHeader = Cipher.getInstance("AES/GCM/NoPadding");
        wrongHeader.init(Cipher.DECRYPT_MODE, key, params);
        wrongHeader.updateAAD("header:v2".getBytes(StandardCharsets.UTF_8));
        boolean rejected;
        try {
            wrongHeader.doFinal(sealed);
            rejected = false;
        } catch (AEADBadTagException expected) {
            rejected = true;
        }

        System.out.println("plainBytes=" + plain.length);
        System.out.println("sealedBytes=" + sealed.length);
        System.out.println("encryptionBoundEnough=" + (output.length >= sealed.length));
        System.out.println("roundTrip=" +
                Arrays.equals(plain, Arrays.copyOf(recovered, received)));
        System.out.println("wrongAadRejected=" + rejected);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 CipherFlowDemo.java
java CipherFlowDemo
```

**Stdout dự kiến (đã kiểm tra trên JDK 24, mã tương thích Java 17+):**

```text
plainBytes=16
sealedBytes=32
encryptionBoundEnough=true
roundTrip=true
wrongAadRejected=true
```

Dù khóa và nonce được tạo ngẫu nhiên nên **byte ciphertext mỗi lần khác nhau**, các dòng trên không in ciphertext, và vẫn ổn định với AES-GCM tag 128 bit.

**Phân tích chi tiết:**

1. `KeyGenerator` tạo khóa AES 128 bit; `SecureRandom` tạo nonce 12 byte. Demo **không xuất key/nonce**. Trong hệ thống thực tế phải bảo đảm nonce không trùng với cùng key và lưu/truyền nonce để giải mã.
2. `encrypt.init` thiết lập chiều mã hóa, khóa và `GCMParameterSpec(128, iv)`. `updateAAD` xuất hiện **trước** khi đưa byte plaintext.
3. `getOutputSize(plain.length)` cấp phát buffer đủ chỗ cho ciphertext cộng tag. `split=5` chia 16 byte thành phần đầu 5 và phần cuối 11; tổng output không đổi so với mã hóa một lần.
4. `used += encrypt.update(...)` lưu **số byte output**, không phải số byte input. `encrypt.doFinal(output, used)` ghi phần cuối và tag vào vị trí kế tiếp; `Arrays.copyOf(output, used)` loại phần buffer không sử dụng.
5. Phía nhận `decrypt.init` với **cùng key/nonce**, rồi `updateAAD` với **cùng bytes**. `decrypt.update` có thể trả 0 do buffering/authentication; không cố đoán output.
6. **Chỉ sau khi `decrypt.doFinal` thành công**, dữ liệu `recovered` mới được chấp nhận và so sánh với plaintext. Không đưa bytes từ `update` ra các hệ thống tin cậy trước khi xác minh tag.
7. `wrongHeader` giả lập AAD khác (v1 → v2). `doFinal` ném `AEADBadTagException` và demo in `wrongAadRejected=true`. Trong ứng dụng thật phải **từ chối toàn bộ thông điệp**, không xử lý plaintext chưa được xác thực.

Đây là mô phỏng trong **một tiến trình**. Nếu truyền dữ liệu qua mạng/file, sender phải gửi nonce (và thông tin phiên bản/định dạng, tag nằm trong `sealed`), receiver phải có key qua kênh bảo mật và xác định đúng AAD. **Không có bước nào được phép tái mã hóa thông điệp thứ hai bằng key + IV vừa dùng**.

## 6. Ví dụ thứ hai: CBC có padding và `ByteBuffer`

AES-CBC trong ví dụ này chỉ minh họa vì sao **3 byte input có thể tạo 16 byte output sau `doFinal`**, và cách buffer đầu ra được dùng với API `ByteBuffer`. **CBC không tự xác thực ciphertext**; đừng chọn CBC không có MAC cho dữ liệu cần chống sửa đổi. Với ứng dụng mới, ưu tiên AES-GCM có kiểm soát nonce.

Lưu **`CipherBufferSizeDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public final class CipherBufferSizeDemo {
    private CipherBufferSizeDemo() {}

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey key = generator.generateKey();

        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec params = new IvParameterSpec(iv);
        byte[] plain = "abc".getBytes(StandardCharsets.UTF_8);

        Cipher encrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        encrypt.init(Cipher.ENCRYPT_MODE, key, params);
        int estimate = encrypt.getOutputSize(plain.length);
        byte[] ciphertext = new byte[estimate];
        int first = encrypt.update(plain, 0, 2, ciphertext, 0);
        int finalBytes = encrypt.doFinal(plain, 2, 1, ciphertext, first);
        int produced = first + finalBytes;

        Cipher decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        decrypt.init(Cipher.DECRYPT_MODE, key, params);
        ByteBuffer input = ByteBuffer.wrap(ciphertext, 0, produced);
        ByteBuffer output = ByteBuffer.allocate(decrypt.getOutputSize(input.remaining()));
        int restoredCount = decrypt.doFinal(input, output);
        output.flip();
        byte[] restored = new byte[output.remaining()];
        output.get(restored);

        System.out.println("plainBytes=" + plain.length);
        System.out.println("cipherBytes=" + produced);
        System.out.println("estimateEnough=" + (estimate >= produced));
        System.out.println("decodedBytes=" + restoredCount);
        System.out.println("inputConsumed=" + !input.hasRemaining());
        System.out.println("roundTrip=" + Arrays.equals(plain, restored));
    }
}
```

```bash
javac -encoding UTF-8 CipherBufferSizeDemo.java
java CipherBufferSizeDemo
```

Stdout chính xác khi transform được cung cấp:

```text
plainBytes=3
cipherBytes=16
estimateEnough=true
decodedBytes=3
inputConsumed=true
roundTrip=true
```

Giải thích:

- Input `"abc"` chứa 3 byte UTF-8. AES block size = **16 byte**; `PKCS5Padding` của JCA cung cấp padding kiểu block tương thích AES, vì vậy ciphertext cuối là 16 byte. `getOutputSize(3)` cấp phát đủ chỗ.
- `update(plain,0,2,ciphertext,0)` nạp hai byte đầu. Vì chưa đủ một block, provider có thể giữ chúng và trả **0 byte**. `doFinal(plain,2,1,ciphertext,first)` nạp byte cuối, thêm padding rồi final hóa. **Luôn cộng `first + finalBytes`**.
- Khi giải mã, `ByteBuffer.wrap(ciphertext,0,produced)` đặt giới hạn đúng ciphertext thực tế; không giả định cả buffer luôn đầy. `ByteBuffer.allocate(decrypt.getOutputSize(input.remaining()))` tạo output đủ chỗ; `decrypt.doFinal(input,output)` xử lý các byte đang remaining.
- `ByteBuffer` đầu vào tiến đến limit, output.position tăng theo số byte ghi. Gọi `output.flip()` để chuyển output sang **chế độ đọc**, rồi lấy chính phần remaining hợp lệ. `restoredCount=3` chứ không phải kích thước đã cấp phát.
- Nếu chỉ có `AES/CBC/NoPadding` thì plaintext 3 byte không đủ block và `doFinal` encryption có thể ném `IllegalBlockSizeException`. Chuyển sang mode khác không được hiểu là tự động cung cấp xác thực.

Đối với `Cipher.update(ByteBuffer input, ByteBuffer output)` và `doFinal(ByteBuffer input, ByteBuffer output)`, hai tham số **không được là cùng một `ByteBuffer` object**; output buffer cũng phải writable và có đủ `remaining()`. Với output buffer quá nhỏ có thể ném `ShortBufferException`; API thay đổi `position` dựa trên số byte đã xử lý khi thành công.

## 7. Bộ đệm kết quả: cách tư duy đúng trong production

### Biết toàn bộ input trước khi bắt đầu

Với một thông điệp vừa/bé nằm trong mảng, chiến lược đơn giản là **tính cận trên một lần**, dùng overload có output buffer, và chỉ giữ `written` byte kết quả. Mọi `update`/`doFinal` nối output liên tiếp trong mảng. Chương trình `CipherFlowDemo` ở mục 5 chính là ví dụ đầy đủ.

### Input đến theo nhiều lần, chưa biết tổng độ dài

Nếu xử lý file/stream lớn hoặc nguồn dữ liệu liên tục, **không nên** cấp phát mảng chứa toàn bộ ciphertext/plaintext chỉ vì có `getOutputSize`. Có thể cấp output buffer phù hợp cho từng bước dựa trên state và số input còn lại, ghi những byte đã hoàn thành ra sink, và phải lưu/ghi **cả byte từ `doFinal`**. Cần chú ý thêm:

- Với **GCM encryption**, gửi ciphertext theo chunk có thể phù hợp, nhưng vẫn phải ghi đầy đủ **tag ở cuối** và truyền nonce/AAD theo protocol. Mỗi message dùng nonce riêng.
- Với **GCM decryption**, plaintext phát ra từ `update` (nếu có) **chưa được xác thực** trước `doFinal`. Thiết kế receiver phải **giữ cách ly** plaintext đến lúc tag thành công; không thực thi, hiển thị hoặc ghi đè dữ liệu tin cậy dựa trên plaintext chưa được xác thực. Với message rất lớn, có thể cần spool tạm vào vùng cách ly hoặc định dạng có khung AEAD riêng từng bản tin; không coi streaming giải mã là đã xác thực từng chunk.
- Với CBC có padding, output cuối có thể chứa dữ liệu được trả sau khi bỏ padding. Cần kiểm tra lỗi `BadPaddingException`; CBC vẫn cần phương án xác thực riêng nếu dùng trong protocol nhạy cảm.
- Dùng `CipherInputStream`/`CipherOutputStream` cần đọc kỹ contract về exception và cách final ở `close`. Đặc biệt, không dùng wrapper che khuất lỗi xác thực để mặc nhiên tin tưởng plaintext từ AEAD; khi mục tiêu là xác minh tag, ưu tiên luồng kiểm soát rõ ràng quanh `Cipher.doFinal`.

### Khi nào có `ShortBufferException`?

Ví dụ với API `doFinal(input, inOff, len, output, outOff)`, nếu `output.length - outOff` không đủ cho byte kết quả, có thể bị `ShortBufferException`. Kiểm tra offset/length và hỏi lại `getOutputSize(remainingInputLen)` từ state hiện tại trước khi cấp buffer. `getOutputSize` luôn tính cả dữ liệu trước đó còn buffer; không chỉ tính theo chunk vừa đưa vào. Khi thao tác lỗi do authentication hoặc padding, **không xử lý output từng phần như dữ liệu thành công**.

### Một số lỗi state thường gặp

| Tình huống | Kết quả | Giải pháp |
| --- | --- | --- |
| `update` trước `init` | `IllegalStateException` hoặc thao tác sai state. | `getInstance`, rồi `init` chính xác. |
| `update` nhiều lần nhưng bỏ output của chúng | Ciphertext/plaintext thiếu phần đầu. | Thu cả `update` và `doFinal`, theo đúng thứ tự. |
| Giả định `update` trả đúng số byte input | Sai với buffering, padding, AEAD. | Dùng số byte trả về và `getOutputSize` để cấp phát. |
| Cấp output bằng `new byte[input.length]` | Không đủ cho padding/tag hoặc buffered bytes. | Dùng `getOutputSize`. |
| In toàn bộ mảng cấp phát dù chỉ ghi một phần | Ghi thêm zero/dữ liệu dư, phá protocol. | Chỉ dùng `[0,written)` hoặc vị trí byte thực tế. |
| Không gọi `doFinal` | Thiếu bytes cuối, thiếu GCM tag hoặc chưa xác minh tag. | Final hóa đúng một lần cho mỗi thông điệp. |
| Reuse GCM key + nonce sau `doFinal` | Phá vỡ an toàn của AEAD. | Tạo nonce **duy nhất cho mỗi lần encryption** với cùng key. |
| `updateAAD` sau payload | `IllegalStateException` hoặc sai quy trình. | Cung cấp toàn bộ AAD trước payload. |
| Tiêu thụ plaintext GCM trước tag verification | Có thể xử lý dữ liệu bị sửa/giả mạo. | Chỉ công bố plaintext sau `doFinal` thành công. |
| Dùng `Cipher.getInstance("AES")` | Mode/padding không được chỉ định rõ. | Dùng đầy đủ `AES/GCM/NoPadding`. |
| Tưởng `NoPadding` nghĩa là ciphertext không thêm byte | Quên GCM tag. | Tính cả tag trong cận trên. |
| Dùng `ByteBuffer.capacity()` thay `remaining()` | Đánh giá sai vùng có thể đọc/ghi. | Dựa trên position/limit/remaining. |
| Dùng chung một Cipher cho nhiều thread | State có thể bị trộn. | Instance riêng cho từng operation. |
| Bắt lỗi `BadPaddingException` rồi dùng plaintext dở | Dữ liệu không đáng tin, state không chắc. | Hủy output, báo lỗi và khởi tạo lại. |

## 8. API exception và trạng thái sau lỗi

Các lỗi đáng nhận diện:

- `NoSuchAlgorithmException` hoặc `NoSuchPaddingException`: transformation không được hỗ trợ theo provider đã chọn.
- `InvalidKeyException`: key không phù hợp với thuật toán hoặc provider.
- `InvalidAlgorithmParameterException`: tham số IV/nonce/mode không hợp lệ hoặc không được chấp nhận.
- `IllegalStateException`: sử dụng Cipher không đúng state (chưa init, sai mode, thứ tự AAD sai...).
- `ShortBufferException`: buffer output không đủ; với overload ghi ra buffer, API chỉ rõ trường hợp cần tăng kích thước.
- `IllegalBlockSizeException`: input không đáp ứng yêu cầu block size khi không padding, hoặc thuật toán không xử lý được input.
- `BadPaddingException`: padding không hợp lệ trong decryption của các mode có padding.
- `AEADBadTagException`: tag không khớp khi decryption trong AEAD; lớp này **kế thừa `BadPaddingException`**. Nếu cần xử lý riêng, catch `AEADBadTagException` **trước** `BadPaddingException`.

Không sử dụng lỗi tag như “một biến thể dữ liệu gần đúng”; mọi kết quả giải mã từ lần thất bại đều phải bị loại. Không giả định có thể tiếp tục gọi `update` trên đối tượng đã ném exception ở `doFinal` mà không init lại. Ghi log loại lỗi, tên transformation và ngữ cảnh không nhạy cảm; **không log key, plaintext bí mật hay thông tin tạo oracle về padding**.

## 9. Bài tập kiểm tra hiểu bài

1. **Thay split.** Trong `CipherFlowDemo` đổi `split` từ 5 thành 1, 8 và 16. Vì sao output length và `roundTrip=true` vẫn không đổi? Quan sát riêng số byte của từng `update` trên provider đang chạy và giải thích vì sao không nhất thiết bằng input chunk.
2. **Tính cận trên.** Mã hóa AES-GCM với tag 128 bit, plaintext 100 byte. Giải thích vì sao ciphertext+tag có 116 byte, nhưng code vẫn nên gọi `getOutputSize(100)` trước khi cấp buffer.
3. **Padding.** Với AES/CBC/PKCS5Padding, tính output encryption cho plaintext 0, 1, 15, 16, 17 và 32 byte. Giải thích vì sao 16 byte input cho 32 byte ciphertext.
4. **Đổi AAD.** Thay `header:v1` thành `header:v2` chỉ ở bên decrypt. Xác nhận `AEADBadTagException` và giải thích vì sao không được sử dụng bất kỳ plaintext nào từ lần decrypt đó.
5. **Đổi nonce.** Mã hóa một message, lưu nonce và sealed bytes. Với cùng key, thử giải mã bằng nonce khác; giải thích vai trò của nonce và tag. **Không tái mã hóa hai message bằng cùng key + nonce** khi làm bài.
6. **Kiểm tra output buffer.** Chủ động cấp mảng bé hơn `getOutputSize` để quan sát `ShortBufferException`; sau đó tạo Cipher mới/khởi tạo lại và cấp đủ buffer. Xác định vì sao cần lưu số byte trả về của mỗi `update`.
7. **ByteBuffer.** Thay `ByteBuffer.allocate` bằng buffer có limit hẹp để thấy `remaining` là giới hạn thực sự. Sau khi `doFinal` thành công, thực hành `flip` và so output với plaintext.
8. **Thiết kế message lớn.** Với GCM decrypt file vài GB, đề xuất nơi tạm chứa plaintext cho đến khi tag được xác minh trước khi publish. Giải thích tại sao lưu ngay plaintext vào file đích tin cậy trong lúc `update` là nguy hiểm.
9. **Sai quy trình.** Trình bày hậu quả khi bỏ `doFinal`, gọi `init` giữa các chunk, dùng Cipher chung cho hai request, hoặc gửi thiếu tag.

**Tiêu chí hoàn thành:** bạn mô tả đúng `init` chọn mode/key/params, `update` có output tùy state, `doFinal` final padding/tag và reset sau thành công; biết dùng `getOutputSize` và số byte thực viết; đồng thời giữ đúng nonce, AAD và ranh giới xác thực khi dùng AES-GCM.

## Tham khảo chính thức (Java 17)

- [`Cipher` API — `init`, `update`, `doFinal`, `getOutputSize`, `updateAAD`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html)
- [`GCMParameterSpec` — nonce và độ dài authentication tag](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html)
- [`AEADBadTagException` — lỗi xác thực tag AEAD](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/AEADBadTagException.html)
- [`ByteBuffer` — `position`, `limit`, `remaining`, `flip`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)
- [Java Cryptography Architecture (JCA) Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-005">6.5 Phân biệt ECB, CBC, CTR và yêu cầu bảo vệ tính toàn vẹn của bản mã</a>

<details>
<summary>Xem chi tiết</summary>

# 6.5. Phân biệt ECB, CBC, CTR và yêu cầu bảo vệ tính toàn vẹn của bản mã

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Đọc đúng tên transformation trong Java như `AES/ECB/NoPadding`, `AES/CBC/PKCS5Padding` và `AES/CTR/NoPadding`; hiểu **AES** là thuật toán mã khối còn **ECB, CBC, CTR** là các *mode of operation*.
2. Giải thích cách ba mode biến plaintext thành ciphertext; phân biệt yêu cầu về **IV, nonce, counter**, padding và độ dài dữ liệu.
3. Nhận diện các vấn đề của **ECB lộ mẫu**, **CBC với IV/padding oracle**, **CTR khi tái sử dụng keystream**, và vì sao cả ba mode tự thân **không xác thực tính toàn vẹn** của ciphertext.
4. Thử nghiệm Java 17+ cho thấy việc sửa ciphertext/IV có thể làm plaintext thay đổi mà giải mã không phát hiện; triển khai đúng nguyên tắc **Encrypt-then-MAC** nếu buộc phải dùng CBC/CTR cũ, và ưu tiên **AEAD** như AES-GCM trong thiết kế mới.
5. Biết lưu/truyền IV và tag, xử lý lỗi xác thực, thiết kế uniqueness của nonce và tránh đánh đồng tính bí mật, toàn vẹn, xác thực và chống replay.

**Phạm vi:** bài tập trung vào mã hóa đối xứng với AES trong Java. Các ví dụ CBC/CTR *chưa có xác thực* chỉ dùng để quan sát vấn đề; không sử dụng chúng làm mẫu bảo mật hoàn chỉnh cho production.

## 1. AES là mã khối; ECB, CBC, CTR quyết định cách dùng mã khối

**AES (Advanced Encryption Standard)** là thuật toán mã hóa đối xứng hoạt động trên **khối 128 bit = 16 byte**, với khóa 128, 192 hoặc 256 bit. **Kích thước khối vẫn là 16 byte kể cả khi dùng khóa AES-256**. AES tự nó cho phép mã hóa hoặc giải mã *một khối*; để xử lý thông điệp dài, cần mode of operation, cách xử lý IV/counter và đôi khi padding.

Trong Java Cryptography Architecture/Extension, `javax.crypto.Cipher` có dạng tên:

```text
<algorithm>/<mode>/<padding>

AES/ECB/NoPadding
AES/CBC/PKCS5Padding
AES/CTR/NoPadding
AES/GCM/NoPadding
```

Trong bảng trên, `NoPadding` là chỉ dẫn không thêm padding, **không có nghĩa thông điệp không cần kiểm tra tính hợp lệ**; và `PKCS5Padding` trong cách đặt tên JCE cho AES hoạt động theo quy tắc padding tương thích PKCS#7 trên khối 16 byte.

Không viết `Cipher.getInstance("AES")` trong ứng dụng bảo mật: chế độ/padding mặc định phụ thuộc provider, có thể dẫn đến ECB ngoài ý muốn. Hãy chỉ định transformation **đầy đủ** và theo thiết kế giao thức.

### 1.1. Bốn tính chất thường bị nhầm lẫn

| Tính chất | Câu hỏi thực tế | Mã hóa ECB/CBC/CTR tự cung cấp? |
| --- | --- | --- |
| **Confidentiality – tính bí mật** | Người không có khóa có đọc được plaintext không? | Có thể cung cấp ở mức độ khác nhau **chỉ khi mode/IV/counter dùng đúng**; ECB thường không phù hợp dữ liệu có cấu trúc |
| **Integrity – tính toàn vẹn** | Dữ liệu có bị sửa kể từ lúc gửi/lưu không? | **Không**; plaintext có thể thay đổi khi ciphertext/IV bị sửa |
| **Authentication – xác thực nguồn gốc** | Dữ liệu có do bên sở hữu khóa xác thực tạo ra không? | **Không**; encryption không thay cho MAC/chữ ký |
| **Replay protection – chống phát lại** | Một bản tin cũ hợp lệ bị gửi lại có bị từ chối không? | **Không**, kể cả với MAC hoặc AEAD nếu không có sequence number/nonce/cơ chế state |

Đây là nguyên tắc của toàn bài: **“decrypt được” không có nghĩa “ciphertext chưa bị sửa”**. Một ứng dụng kiểm tra padding thành công vẫn có thể nhận plaintext do đối thủ tác động.

## 2. ECB – Electronic Codebook: các khối độc lập và lộ mẫu lặp

Với plaintext được chia thành các khối `P1, P2, ...` đủ 16 byte và khóa `K`, ECB áp dụng AES cho **từng khối độc lập**:

```text
P1 ── AES-Encrypt(K) ──> C1
P2 ── AES-Encrypt(K) ──> C2
P3 ── AES-Encrypt(K) ──> C3

C_i = E_K(P_i)
```

Nếu `P1 = P2`, thì **chắc chắn `C1 = C2` dưới cùng khóa**. Kẻ quan sát có thể không giải mã được nội dung nhưng thấy rõ khối nào lặp, từ đó suy luận cấu trúc, định dạng và mẫu dữ liệu. Với hình ảnh, bản ghi có trường lặp hoặc dữ liệu có tính khuôn mẫu, đây là rò rỉ đáng kể.

- **IV:** ECB không dùng IV; không có cơ chế phá mẫu khối bằng nonce.
- **Padding:** `AES/ECB/NoPadding` chỉ nhận đầu vào có độ dài bội số 16; `AES/ECB/PKCS5Padding` thêm padding cho message không đủ khối.
- **Tính toàn vẹn:** không có. Kẻ tấn công còn có thể hoán đổi hoặc thay thế các khối ciphertext mà không cần khóa; giải mã có thể ra các khối plaintext được sao chép/di chuyển.
- **Phù hợp:** hiếm khi dùng trực tiếp cho message thực tế. ECB có thể là primitive bên trong cấu trúc khác đã được chứng minh, nhưng không chọn ECB để “mã hóa file/JSON/database record”.

Ví dụ cùng một khối 16 byte ký tự `A` lặp hai lần: hai khối ciphertext ECB đầu tiên giống nhau hoàn toàn. Đây là biểu hiện của **deterministic per-block mapping**, không phụ thuộc vào việc khóa AES có dài 128 hay 256 bit.

**Không sửa ECB chỉ bằng cách đổi padding:** thêm PKCS5 padding không loại bỏ mẫu lặp ở các khối plaintext đầy đủ đã trùng nhau.

## 3. CBC – Cipher Block Chaining: liên kết khối và IV

CBC sử dụng ciphertext khối trước để tác động đến mã hóa khối hiện tại. Gọi `IV` là giá trị khởi đầu 16 byte, `P_i` là plaintext khối thứ `i`, `C_i` là ciphertext khối thứ `i`:

```text
C_0 = IV

P1 ─ XOR(IV) ─ AES-Encrypt(K) ─> C1
P2 ─ XOR(C1) ─ AES-Encrypt(K) ─> C2
P3 ─ XOR(C2) ─ AES-Encrypt(K) ─> C3

C_i = E_K(P_i XOR C_(i-1))
P_i = D_K(C_i) XOR C_(i-1)
```

Đây là mode có tính *chaining*: khối trước tác động lên khối sau. Với IV tạo ngẫu nhiên và khó dự đoán đúng cách, hai thông điệp giống nhau khi mã hóa ở hai lần khác nhau thường tạo ciphertext khác nhau, ngay từ khối đầu.

### 3.1. IV của CBC phải như thế nào?

- IV CBC/AES có **16 byte** tương ứng kích thước block AES, khác với GCM thường dùng nonce 12 byte.
- Đối với CBC encryption an toàn, IV cần **khó dự đoán khi plaintext được chọn**; trong thực tế tạo IV mới bằng `SecureRandom.nextBytes(new byte[16])` cho mỗi message là lựa chọn thông dụng.
- IV **không cần giữ bí mật**, có thể đặt trước ciphertext; nhưng phải được **ràng buộc toàn vẹn** với thông điệp trong sơ đồ có MAC/AEAD.
- **Không** dùng IV cố định là 16 byte zero, IV lấy từ timestamp/`Random` hoặc luôn dùng lại IV cho mọi message cùng khóa.
- Không nhầm “lưu IV cạnh ciphertext” với “IV được xác thực”: nếu đối thủ sửa IV mà không có MAC, plaintext block đầu có thể bị tác động trực tiếp.

### 3.2. CBC thường cần padding

`AES/CBC/PKCS5Padding` trong Java tự thêm padding; kể cả plaintext có độ dài đúng bội số 16, nó vẫn thêm **một khối padding đầy đủ**, nên ciphertext CBC có thể dài hơn plaintext. Với `NoPadding`, ứng dụng phải cung cấp dữ liệu bội số 16 và không tự chế padding mơ hồ.

Với `AES/CBC/PKCS5Padding`, phép giải mã có thể trả lỗi `BadPaddingException` nếu padding không hợp lệ. **Lỗi padding không phải là cơ chế xác thực.** Kẻ tấn công có thể thay đổi ciphertext và quan sát hành vi ứng dụng để suy luận plaintext nếu hệ thống phân biệt lỗi padding hợp lệ/không hợp lệ hoặc thời gian xử lý.

### 3.3. CBC có thể bị sửa có chủ đích

Công thức giải mã khối đầu:

```text
P1 = D_K(C1) XOR IV
```

Nếu đối thủ sửa bit ở **IV**, bit tương ứng của `P1` sẽ bị lật mà không cần biết khóa. Tương tự, sửa `C_i` có thể làm hỏng khối `P_i` và lật một số bit có chủ đích trong `P_(i+1)`. Điều này gọi là **malleability**.

Ví dụ cấu trúc dữ liệu chứa cờ quyền hạn hoặc số tiền sẽ đặc biệt nguy hiểm nếu backend giải mã CBC rồi tin vào plaintext mà không xác thực trước. Việc chỉnh bit không phải lúc nào cũng cho ra plaintext hợp lệ, nhưng **không được coi giải mã thành công là bằng chứng dữ liệu nguyên vẹn**.

### 3.4. Padding oracle: tại sao phải xác thực trước giải mã?

Giả sử API nhận `IV || ciphertext`, gọi `Cipher.doFinal` rồi trả:

- `400 Padding error` khi `BadPaddingException`;
- `401 Invalid message` khi plaintext giải mã thành công nhưng dữ liệu ứng dụng không hợp lệ.

Sự khác biệt có thể tạo **oracle**: đối thủ thử sửa ciphertext, quan sát kết quả để học thông tin về plaintext mà không biết khóa. Ngay cả khi dùng một thông báo lỗi chung, sự khác biệt timing có thể là tín hiệu.

Nếu buộc phải dùng CBC, cần một thiết kế có xác thực được kiểm chứng, ví dụ **Encrypt-then-MAC** với khóa độc lập; bên nhận **kiểm MAC trước khi giải mã CBC**. Cũng phải kiểm chính sách về IV, format, padding, key rotation và thông báo lỗi đồng nhất. Trong thiết kế mới, AEAD chuẩn như AES-GCM thường đơn giản và an toàn hơn để sử dụng đúng.

## 4. CTR – Counter: biến AES thành luồng khóa (keystream)

CTR không mã hóa trực tiếp plaintext block bằng AES. Nó mã hóa một **counter block** riêng, rồi XOR kết quả với plaintext. Các counter block phải **không trùng dưới cùng khóa**.

```text
T1 = nonce || counter1  ─ AES-Encrypt(K) ─> S1
T2 = nonce || counter2  ─ AES-Encrypt(K) ─> S2
T3 = nonce || counter3  ─ AES-Encrypt(K) ─> S3

C1 = P1 XOR S1
C2 = P2 XOR S2
C3 = P3 XOR S3

P_i = C_i XOR S_i
```

`T_i` là giá trị counter block đầu vào AES; `S_i` là keystream. Ký hiệu `nonce || counter` chỉ là **một cách bố trí counter block theo giao thức**: số bit nonce/counter, cách tăng counter và endian phải được thống nhất chính xác. Trong Java với AES, counter block khởi tạo được truyền qua `IvParameterSpec` dài 16 byte. Các provider có thể có cách quản lý counter khác nhau, vì thế hệ thống liên thông phải xác định format cụ thể.

### 4.1. Đặc tính hữu ích của CTR

- Không cần padding: `AES/CTR/NoPadding` mã hóa cả plaintext không đủ 16 byte; ciphertext có **cùng số byte** với plaintext.
- Các khối keystream có thể được tính độc lập khi biết các counter block, thuận lợi cho song song hóa và truy cập theo vị trí.
- Mã hóa/giải mã cùng nguyên lý XOR; `Cipher` phân biệt `ENCRYPT_MODE`/`DECRYPT_MODE` nhưng đều dùng keystream tương ứng.
- Không có chaining kiểu CBC, nên lỗi một byte ciphertext có thể chỉ làm sai byte tương ứng plaintext nếu counter chính xác.

**Đánh đổi quan trọng:** CTR không xác thực dữ liệu. Nếu đối thủ đổi một bit ciphertext, bit tương ứng plaintext đổi theo **một cách có thể dự đoán**. Ứng dụng có thể giải mã bình thường mà không có exception.

### 4.2. Quy tắc cực kỳ quan trọng: không tái sử dụng keystream

Nếu dùng cùng khóa `K` và cùng counter block `T_i` để mã hóa hai plaintext khác nhau, keystream `S_i` giống nhau:

```text
C1 = P1 XOR S
C2 = P2 XOR S

C1 XOR C2 = P1 XOR P2
```

Kẻ quan sát thấy quan hệ XOR của hai plaintext. Nếu biết/đoán được một plaintext, có thể suy ra plaintext còn lại ở các vị trí tương ứng. Đây là một dạng **two-time pad** rất nguy hiểm.

- Với CTR, điều kiện chính xác là **không bao giờ dùng lại cùng counter block đầu vào AES với cùng khóa**, kể cả giữa các message.
- Mỗi message phải có vùng counter/nonce riêng, không trùng chéo; phải chống counter overflow/wrap, tính cả số block của từng message.
- Không dùng IV/counter block cố định, bộ đếm bị reset sau restart/snapshot hoặc chỉ dựa vào random mà không hiểu xác suất va chạm/giới hạn.
- Không suy ra “CTR không cần padding nên chắc an toàn hơn CBC”: CTR có yêu cầu nonce/counter nghiêm ngặt và vẫn thiếu integrity.

Trong demo sẽ dùng IV 16 byte ngẫu nhiên chỉ cho **một lần mã hóa**. Với hệ thống sản xuất lớn, thiết kế counter/nonce duy nhất theo khóa và giới hạn số lượng block theo tài liệu/chuẩn triển khai quan trọng hơn một ví dụ ngắn.

## 5. Đối chiếu ECB, CBC, CTR từ góc nhìn bảo mật

| Tiêu chí | ECB | CBC | CTR |
| --- | --- | --- | --- |
| Cách vận hành | Mỗi khối plaintext mã hóa độc lập | XOR plaintext với ciphertext khối trước, rồi mã hóa | Mã hóa counter block thành keystream, XOR plaintext |
| Lộ mẫu lặp trong message | **Có**, khối plaintext bằng nhau → ciphertext bằng nhau | Không lộ cùng mẫu kiểu ECB khi IV/khóa dùng đúng; chaining làm các khối phụ thuộc nhau | Không lộ mẫu kiểu ECB khi counter block không trùng |
| IV/nonce/counter | Không có IV | IV 16 byte, cần mới và khó dự đoán cho encryption | Counter block 16 byte, phải bảo đảm không trùng dưới cùng khóa |
| Padding với AES | Cần nếu dữ liệu không đủ bội 16 | Cần nếu dữ liệu không đủ bội 16 | **Không cần** |
| Kích thước ciphertext | Bội số 16 | Bội số 16; thêm padding nếu dùng PKCS5Padding | Bằng kích thước plaintext |
| Mã hóa song song các khối | Có | Phụ thuộc ciphertext khối trước | Có thể tính keystream các block độc lập |
| Tác động khi ciphertext bị sửa | Thay/xáo trộn khối dẫn đến thay/xáo trộn plaintext | Lỗi hoặc sửa có cấu trúc ở khối kế tiếp; IV sửa có thể sửa block đầu | Bit ciphertext sửa → bit plaintext tương ứng sửa |
| Có MAC/tag? | **Không** | **Không** | **Không** |
| Có nên dùng nguyên bản cho dữ liệu nhạy cảm mới? | **Không** | Không, cần xác thực chuẩn; thường ưu tiên AEAD | Không, cần xác thực chuẩn; thường ưu tiên AEAD |

**Đừng đánh đồng “có IV” với “có kiểm tra integrity”.** IV CBC và nonce CTR hỗ trợ mục tiêu confidentiality; muốn biết dữ liệu có bị thay đổi phải có cơ chế xác thực riêng.

### 5.1. Nhìn nhanh hành vi sửa dữ liệu

```text
ECB:
  P_block → E_K → C_block
  Reorder/copy C_block ⇒ reorder/copy P_block (không phát hiện)

CBC (chưa có MAC):
  P1 = D_K(C1) XOR IV
  IV[0] ^= 1 ⇒ P1[0] ^= 1 (không cần biết K)

CTR (chưa có MAC):
  P = C XOR keystream
  C[0] ^= 1 ⇒ P[0] ^= 1 (không cần biết K)

GCM (có xác thực):
  C hoặc tag bị sửa ⇒ kiểm tag thất bại ⇒ từ chối plaintext
```

Sơ đồ trên là mô hình quan hệ, không phải cam kết rằng mọi sửa đổi CBC luôn cho plaintext hợp lệ hoặc GCM giải quyết được replay.

## 6. Java 17+ – demo chạy độc lập cho thấy khác biệt

Tạo file **`ModeComparisonDemo.java`** như sau. Đây là chương trình **giáo dục**: phần ECB/CBC/CTR cố ý không có MAC để quan sát điều có thể xảy ra khi chỉ dùng encryption. Khóa, IV/nonce đều tạo mới bằng các API mật mã; chương trình không in bí mật, nên stdout ổn định.

```java
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

public class ModeComparisonDemo {
    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();

        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128, random);
        SecretKey key = generator.generateKey();

        // Hai khối 16-byte giống hệt nhau.
        byte[] plain = new byte[32];
        Arrays.fill(plain, (byte) 'A');

        // ECB: cùng plaintext block -> cùng ciphertext block.
        Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
        ecb.init(Cipher.ENCRYPT_MODE, key);
        byte[] ecbCipher = ecb.doFinal(plain);
        boolean ecbRepeats = Arrays.equals(
                Arrays.copyOfRange(ecbCipher, 0, 16),
                Arrays.copyOfRange(ecbCipher, 16, 32));

        // CBC: IV phải là 16 byte và được bảo vệ toàn vẹn.
        byte[] cbcIv = new byte[16];
        random.nextBytes(cbcIv);
        Cipher cbc = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cbc.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(cbcIv));
        byte[] cbcCipher = cbc.doFinal(plain);

        cbc.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(cbcIv));
        boolean cbcRoundTrip = Arrays.equals(plain, cbc.doFinal(cbcCipher));

        // Nếu IV không được xác thực, sửa bit IV làm sửa bit P1.
        byte[] changedCbcIv = cbcIv.clone();
        changedCbcIv[0] ^= 1;
        cbc.init(Cipher.DECRYPT_MODE, key,
                new IvParameterSpec(changedCbcIv));
        byte[] cbcModifiedPlain = cbc.doFinal(cbcCipher);
        byte[] expectedCbcPlain = plain.clone();
        expectedCbcPlain[0] ^= 1;
        boolean cbcIvMalleable =
                Arrays.equals(expectedCbcPlain, cbcModifiedPlain);

        // CTR: không padding, độ dài ciphertext = plaintext.
        byte[] ctrIv = new byte[16];
        random.nextBytes(ctrIv);
        Cipher ctr = Cipher.getInstance("AES/CTR/NoPadding");
        ctr.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(ctrIv));
        byte[] ctrCipher = ctr.doFinal(plain);

        ctr.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(ctrIv));
        boolean ctrRoundTrip = Arrays.equals(plain, ctr.doFinal(ctrCipher));

        // Sửa một bit ciphertext CTR sẽ sửa bit plaintext tương ứng.
        byte[] changedCtrCipher = ctrCipher.clone();
        changedCtrCipher[0] ^= 1;
        ctr.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(ctrIv));
        byte[] ctrModifiedPlain = ctr.doFinal(changedCtrCipher);
        byte[] expectedCtrPlain = plain.clone();
        expectedCtrPlain[0] ^= 1;
        boolean ctrMalleable =
                Arrays.equals(expectedCtrPlain, ctrModifiedPlain);

        // GCM: cùng AES, nhưng được bảo vệ bằng authentication tag.
        byte[] gcmNonce = new byte[12];
        random.nextBytes(gcmNonce);
        GCMParameterSpec gcmParams =
                new GCMParameterSpec(128, gcmNonce);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, key, gcmParams);
        byte[] gcmCipherAndTag = gcm.doFinal(plain);

        gcm.init(Cipher.DECRYPT_MODE, key, gcmParams);
        boolean gcmRoundTrip =
                Arrays.equals(plain, gcm.doFinal(gcmCipherAndTag));

        // Đổi một bit cuối tag: decryption phải bị từ chối.
        byte[] changedGcm = gcmCipherAndTag.clone();
        changedGcm[changedGcm.length - 1] ^= 1;
        gcm.init(Cipher.DECRYPT_MODE, key, gcmParams);
        boolean gcmRejected = false;
        try {
            gcm.doFinal(changedGcm);
        } catch (AEADBadTagException expected) {
            gcmRejected = true;
        }

        System.out.println("ecbRepeatedBlocks=" + ecbRepeats);
        System.out.println("cbcRoundTrip=" + cbcRoundTrip);
        System.out.println("cbcIvMalleable=" + cbcIvMalleable);
        System.out.println("ctrSameLength=" +
                (ctrCipher.length == plain.length));
        System.out.println("ctrRoundTrip=" + ctrRoundTrip);
        System.out.println("ctrMalleable=" + ctrMalleable);
        System.out.println("gcmRoundTrip=" + gcmRoundTrip);
        System.out.println("gcmTamperRejected=" + gcmRejected);
    }
}
```

Chạy tại thư mục chứa file:

```shell
javac ModeComparisonDemo.java
java ModeComparisonDemo
```

**Stdout kỳ vọng trên JDK 17+ chuẩn hỗ trợ các transformation trên:**

```text
ecbRepeatedBlocks=true
cbcRoundTrip=true
cbcIvMalleable=true
ctrSameLength=true
ctrRoundTrip=true
ctrMalleable=true
gcmRoundTrip=true
gcmTamperRejected=true
```

**Phân tích đúng kết quả:**

- `ecbRepeatedBlocks=true` là tính chất toán học của ECB với hai khối plaintext giống nhau; nó không phụ thuộc vào giá trị ngẫu nhiên của khóa.
- `cbcRoundTrip=true`, `ctrRoundTrip=true` nói rằng giải mã đúng khi key và IV đúng. Chúng **không chứng minh toàn vẹn**.
- `cbcIvMalleable=true` cho thấy **sửa IV chưa được xác thực** làm plaintext block đầu thay đổi tương ứng mà `Cipher` không phát hiện. Các byte padding ở cuối message không bị ảnh hưởng trong demo.
- `ctrMalleable=true` cho thấy sửa bit ciphertext CTR đổi bit plaintext tương ứng, không có exception khi chỉ giải mã CTR.
- `gcmTamperRejected=true` chứng minh tag trong ví dụ phát hiện thay đổi; bên nhận phải từ chối không sử dụng plaintext chưa xác thực.
- Đoạn code **không trình bày cách lưu nonce hoặc quản lý số lần mã hóa dài hạn**. Khi triển khai thật, phải bảo đảm không tái dùng cặp khóa–nonce GCM/CTR.

### 6.1. Có thể suy ra từ ví dụ những gì?

**Có thể kết luận:** ECB lộ khối lặp; CBC và CTR chỉ cung cấp confidentiality khi dùng đúng mode/IV/counter, vẫn cho phép can thiệp ciphertext; AEAD có xác thực được kiểm tra khi giải mã.

**Không thể kết luận:** chỉ từ việc `gcmTamperRejected=true`, ứng dụng đã có toàn bộ security controls. Vẫn cần quản lý khóa, nonce, AAD, quyền truy cập, chống replay và xử lý lỗi an toàn. CBC/CTR demo cố ý chưa có MAC, **không sao chép nguyên vào production**.

## 7. Tại sao phải xác thực ciphertext trước khi tin plaintext?

Giả sử backend lưu:

```text
IV || ciphertext
```

và chỉ gọi `Cipher.doFinal(ciphertext)`. Khi bị sửa byte:

- ECB có thể chấp nhận khối ciphertext bị copy/reorder.
- CBC có thể trả dữ liệu bị sửa, hoặc ném lỗi padding; phản hồi khác biệt dễ tạo oracle.
- CTR luôn có phép XOR cho mọi byte ciphertext (nếu độ dài/counter hợp lệ), không tự phát hiện bit bị sửa.

**Mã hóa không có chứng thực tạo ra ranh giới tin cậy sai:** ứng dụng nhìn thấy bytes “đã giải mã” và tưởng chúng là dữ liệu do một bên hợp lệ tạo ra.

Khi cần chống sửa đổi, dùng một trong hai hướng đã được chuẩn hóa:

1. **AEAD** (*Authenticated Encryption with Associated Data*): thuật toán cung cấp confidentiality + integrity/authentication trong một cơ chế, ví dụ `AES/GCM/NoPadding`. Tag xác thực ciphertext, nonce và AAD theo cách đặc tả thuật toán. Đây là **lựa chọn ưu tiên cho hệ thống mới** nếu thỏa điều kiện nonce/key lifecycle.
2. **Encrypt-then-MAC (EtM)**: dùng encryption mode phù hợp cộng thêm MAC mật mã (ví dụ HMAC-SHA256 với **khóa khác** khóa AES); xác thực định dạng/IV/ciphertext và dữ liệu ngữ cảnh trước khi decrypt. Chỉ chọn khi cần tương thích legacy/giao thức đã được thiết kế, review và thử nghiệm cẩn thận.

**Không** thay MAC bằng checksum, CRC32, SHA-256 không khóa hoặc “thử decrypt xem có lỗi”. Hash công khai có thể bị đối thủ tính lại sau khi sửa dữ liệu. MAC phải dùng khóa bí mật/thuật toán chuẩn. Chữ ký số cũng có thể cung cấp xác thực theo mô hình khóa khác, nhưng không cần biến mọi message đối xứng thành bài toán chữ ký số.

### 7.1. Quy tắc Encrypt-then-MAC

Ví dụ định dạng tự mô tả rõ phiên bản:

```text
message = version || IV || ciphertext
tag     = HMAC_SHA256(macKey, message)
packet  = message || tag
```

Bên gửi:

1. Có **AES key** và **HMAC key** độc lập, được quản lý an toàn.
2. Tạo IV CBC mới bằng `SecureRandom`.
3. Encrypt plaintext bằng AES/CBC/PKCS5Padding.
4. Tính HMAC trên **version + IV + ciphertext** (cũng xác thực metadata/AAD ngoài nếu giao thức có).
5. Gửi `packet`; không cần giấu IV, nhưng phải giữ key bí mật.

Bên nhận:

1. Phân tích khung dữ liệu và kiểm độ dài với giới hạn tài nguyên phù hợp.
2. Tính lại HMAC cho đúng **toàn bộ dữ liệu cần xác thực**.
3. So sánh tag với cơ chế tránh rò timing, ví dụ `MessageDigest.isEqual`.
4. **Chỉ nếu tag hợp lệ**, kiểm phiên bản/format và giải mã CBC; xử lý ngoại lệ theo chính sách thống nhất.

Nếu decrypt CBC **trước** khi verify HMAC, cơ chế có thể vẫn để lộ padding oracle. Nếu MAC chỉ phủ ciphertext mà bỏ IV/version/metadata, đối thủ có thể sửa các phần bỏ sót. Nếu tái sử dụng cùng key làm AES và HMAC mà không có key separation rõ ràng, thiết kế thiếu ràng buộc mật mã cần thiết.

### 7.2. Vì sao xác thực không tự chống replay?

Giả sử packet hợp lệ đã được ký MAC. Đối thủ **không sửa** packet, chỉ gửi lại y nguyên. MAC vẫn hợp lệ vì nội dung thực sự không thay đổi. Hệ thống cần chống replay bằng **sequence number, transaction ID, timestamp có cửa sổ chấp nhận, token một lần, state chống trùng** hoặc cơ chế đã được giao thức chuẩn quy định. Các trường nhận dạng chống replay cũng phải được bảo vệ toàn vẹn.

## 8. Java 17+ – ví dụ Encrypt-then-MAC cho CBC legacy

Bài toán này dùng hai khóa độc lập: AES-128 để mã hóa và HMAC-SHA256 (32 byte secret) để xác thực. Format của ví dụ: `1 byte version || 16 byte IV || ciphertext || 32 byte HMAC`. Chỉ giải mã khi MAC hợp lệ.

Đây là **ví dụ giáo dục cho giao thức legacy**. Trong hệ thống mới, ưu tiên dùng AEAD chuẩn; tự định nghĩa packet format cần thêm versioning, key IDs, giới hạn kích thước, rotation, quản lý metadata và review bảo mật.

Tạo file **`AuthenticatedCbcDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AuthenticatedCbcDemo {
    private static final int IV_SIZE = 16;
    private static final int TAG_SIZE = 32;
    private static final int HEADER_SIZE = 1 + IV_SIZE;
    private static final byte VERSION = 1;

    private static byte[] hmac(SecretKey macKey, byte[] message)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(macKey);
        return mac.doFinal(message);
    }

    private static byte[] seal(byte[] plaintext, SecretKey aesKey,
            SecretKey macKey, SecureRandom random)
            throws GeneralSecurityException {
        byte[] iv = new byte[IV_SIZE];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plaintext);

        byte[] body = new byte[HEADER_SIZE + encrypted.length];
        body[0] = VERSION;
        System.arraycopy(iv, 0, body, 1, IV_SIZE);
        System.arraycopy(encrypted, 0, body, HEADER_SIZE, encrypted.length);

        byte[] tag = hmac(macKey, body);
        byte[] packet = Arrays.copyOf(body, body.length + tag.length);
        System.arraycopy(tag, 0, packet, body.length, tag.length);
        return packet;
    }

    private static byte[] open(byte[] packet, SecretKey aesKey,
            SecretKey macKey) throws GeneralSecurityException {
        // Ít nhất 1 header + 16 IV + 16 ciphertext + 32 tag.
        if (packet.length < HEADER_SIZE + 16 + TAG_SIZE) {
            throw new GeneralSecurityException("Invalid message");
        }
        int bodyEnd = packet.length - TAG_SIZE;
        byte[] body = Arrays.copyOf(packet, bodyEnd);
        byte[] suppliedTag = Arrays.copyOfRange(
                packet, bodyEnd, packet.length);
        byte[] computedTag = hmac(macKey, body);

        // Quan trọng: verify MAC trước khi decrypt CBC.
        if (!MessageDigest.isEqual(computedTag, suppliedTag)) {
            throw new GeneralSecurityException("Invalid message");
        }

        int encryptedLength = body.length - HEADER_SIZE;
        if (body[0] != VERSION || encryptedLength % 16 != 0) {
            throw new GeneralSecurityException("Invalid message");
        }

        byte[] iv = Arrays.copyOfRange(body, 1, HEADER_SIZE);
        byte[] encrypted = Arrays.copyOfRange(
                body, HEADER_SIZE, body.length);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(iv));
        return cipher.doFinal(encrypted);
    }

    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();

        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128, random);
        SecretKey aesKey = generator.generateKey();

        byte[] macBytes = new byte[32];
        random.nextBytes(macBytes);
        SecretKey macKey =
                new SecretKeySpec(macBytes, "HmacSHA256");

        byte[] plain = "CBC needs integrity"
                .getBytes(StandardCharsets.UTF_8);
        byte[] sealed = seal(plain, aesKey, macKey, random);
        boolean valid = Arrays.equals(
                plain, open(sealed, aesKey, macKey));

        byte[] changed = sealed.clone();
        changed[changed.length - 1] ^= 1;
        boolean rejected = false;
        try {
            open(changed, aesKey, macKey);
        } catch (GeneralSecurityException expected) {
            rejected = true;
        }

        System.out.println("authenticatedRoundTrip=" + valid);
        System.out.println("tamperRejected=" + rejected);
    }
}
```

Biên dịch/chạy:

```shell
javac AuthenticatedCbcDemo.java
java AuthenticatedCbcDemo
```

Stdout kỳ vọng:

```text
authenticatedRoundTrip=true
tamperRejected=true
```

**Diễn giải:** ciphertext bị sửa sau bước encrypt không còn MAC hợp lệ, vì thế `open()` từ chối **trước khi gọi CBC decrypt**. HMAC xác thực cả header version, IV và ciphertext. `MessageDigest.isEqual` giảm rò timing từ so sánh tag; điều này không tự giải quyết mọi side channel của toàn hệ thống.

**Giới hạn cố ý của demo:** không có cơ chế chống replay, key ID, giới hạn packet tối đa, quản lý khóa dài hạn, ràng buộc user/request/AAD, bảo vệ lỗi qua HTTP hoặc rotate key. Ứng dụng production phải giải quyết những việc đó; đừng thay packet format chuẩn bằng ví dụ học thuật này khi có lựa chọn giao thức/AEAD đã chuẩn hóa.

### 8.1. Nếu phải dùng CTR + MAC?

Các nguyên tắc tương tự: MAC phải bao phủ **đầy đủ nonce/counter + ciphertext + version + context** bằng khóa riêng; verify MAC **trước khi xuất plaintext**; counter không bao giờ trùng dưới cùng khóa. Vì CTR không dùng padding, không có lỗi `BadPaddingException`, nhưng **bit-flipping và keystream reuse** vẫn cực kỳ nguy hiểm nếu bỏ xác thực/nonce discipline.

## 9. AEAD và AES-GCM: lựa chọn mặc định tốt hơn cho thiết kế mới

**AEAD** kết hợp mã hóa và kiểm tính xác thực trong một sơ đồ; có thể xác thực thêm **AAD – Additional Authenticated Data** mà không mã hóa AAD. Ví dụ metadata AAD: loại message, phiên bản protocol, tenant ID hoặc transaction context. Dữ liệu AAD phải được cung cấp **giống hệt** ở cả encryption và decryption.

Java:

```java
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
GCMParameterSpec params = new GCMParameterSpec(128, nonce);
cipher.init(Cipher.ENCRYPT_MODE, aesKey, params);
cipher.updateAAD(aad);
byte[] ciphertextAndTag = cipher.doFinal(plaintext);
```

Đoạn này **không độc lập**: cần `aesKey`, `nonce` (thông thường 12 byte, **không lặp với cùng AES key**), `aad`, `plaintext` và xử lý exception. Trong JCE, `doFinal()` với GCM encryption thường trả **ciphertext nối với authentication tag**; khi decrypt dùng cùng key/nonce/AAD và truyền đúng dữ liệu có tag để xác minh.

### 9.1. GCM không tự động giải quyết mọi lỗi

- **Nonce reuse:** dùng lại cặp **key + nonce** với GCM là lỗi bảo mật nghiêm trọng, có thể phá confidentiality và authentication. Đặt chính sách sinh nonce duy nhất, giới hạn số message và quản lý trạng thái qua restart.
- **Tag:** thường dùng 128 bit, đừng tự rút ngắn theo cảm tính. Xử lý `AEADBadTagException` bằng cách từ chối toàn bộ message; không cho business logic nhận plaintext trước khi xác thực xong.
- **AAD:** AAD được xác thực nhưng **không được mã hóa**. Không đặt secret vào AAD với kỳ vọng dữ liệu được giấu. Cùng AAD ở cả hai phía là bắt buộc.
- **Replay:** người khác gửi lại đúng ciphertext+tag cũ vẫn có tag hợp lệ. Chống replay cần thiết kế state/protocol.
- **Key storage và authorization:** AEAD không tự quản lý khóa, quyền truy cập hay việc rò key ở log.
- **Streaming:** tránh phát dữ liệu chưa kiểm tag thành plaintext đã được tin cậy cho downstream; API/framework cần đảm bảo xử lý dữ liệu theo ranh giới xác thực.

**Đừng chỉ thay `AES/CBC/PKCS5Padding` bằng `AES/GCM/NoPadding` rồi giữ nguyên định dạng IV 16 byte, tập quán reuse IV hoặc bỏ quản lý tag.** Thay mode đòi hỏi cập nhật cả giao thức, định dạng, giới hạn sử dụng và cách xử lý lỗi.

### 9.2. Khi nào không “tự đổi mode” trong hệ thống đang chạy?

Một hệ thống đã lưu hàng triệu bản mã CBC không thể giải mã chúng như GCM bằng cách thay transformation. Muốn migrate cần định danh version, đọc dữ liệu cũ theo quy trình an toàn, xác thực trước khi dùng dữ liệu, mã hóa lại bằng AEAD với nonce mới và cập nhật format. Nếu giao thức CBC cũ thiếu MAC, nên xem việc giải mã dữ liệu từ nguồn không đáng tin cậy là rủi ro cần thiết kế riêng, không mở endpoint legacy rộng rãi.

## 10. Key, IV, nonce, MAC, tag và format vận hành

Thiết kế một record đã mã hóa nên trả lời được:

- **Algorithm/mode/version:** sử dụng AES-GCM hay CBC+HMAC? Cần có marker/metadata rõ ràng để giải mã đúng và migrate.
- **Key identity:** khóa nào, phiên bản nào? Khóa AES và khóa MAC phải độc lập nếu dùng EtM; quản lý trong keystore/KMS/HSM phù hợp.
- **IV/nonce:** cần bao nhiêu byte, được tạo như thế nào, có thể lặp sau crash/retry/concurrent writers không? IV/nonce có thể lưu cạnh ciphertext.
- **Ciphertext:** dài bao nhiêu, encoding/định dạng được giới hạn ra sao? Khi lưu Base64, encoding không tăng tính bí mật.
- **Authentication:** tag/MAC có bao phủ tất cả trường ảnh hưởng đến cách hiểu bản mã (version, IV, ciphertext, metadata hoặc AAD) không?
- **Associated context:** record này gắn với user/tenant/object/route nào? Có cần xác thực context để chống hoán đổi ciphertext hợp lệ giữa các đối tượng?
- **Replay/order:** có cần sequence number, request ID, token chỉ dùng một lần hoặc counter được giám sát không?
- **Failure behavior:** xác thực sai thì từ chối, không dùng plaintext và không lộ lý do chi tiết qua API, log hay timing.

Ví dụ format AEAD khái niệm:

```text
version || key-id || nonce || ciphertext || tag
                   \_____ authenticated ______/
AAD: version, key-id, tenant/context (định nghĩa giao thức quyết định)
```

Sơ đồ chỉ để học: tính chất “được xác thực” phụ thuộc **chính xác những bytes nào đưa vào AAD và MAC/tag**. Không mặc nhiên mọi header trong định dạng file đều tự được xác thực. Việc lập trình đúng schema, thứ tự, encoding, độ dài, phân tách trường là một phần của an toàn.

## 11. Xử lý lỗi trong Java và các dấu hiệu chẩn đoán

| Biểu hiện | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `InvalidKeyException` | Key AES sai loại/độ dài, provider/policy không phù hợp | Xác minh algorithm, số bit key, provider; không log secret key |
| `InvalidAlgorithmParameterException` | IV/nonce sai kích thước/kiểu, provider không hỗ trợ tham số | Kiểm transformation và `IvParameterSpec`/`GCMParameterSpec` |
| `IllegalBlockSizeException` | ECB/CBC `NoPadding` nhưng plaintext/ciphertext không phải bội số block | Chọn format/padding chuẩn đúng giao thức, không thêm byte tùy ý |
| `BadPaddingException` khi CBC decrypt | Padding hỏng, key/IV/ciphertext sai, dữ liệu bị sửa | Nếu dùng EtM, **verify MAC trước decrypt**; không expose padding oracle |
| `AEADBadTagException` khi GCM decrypt | Sai key/nonce/AAD/tag hoặc dữ liệu thay đổi | Từ chối message; kiểm metadata và lifecycle, không bỏ qua tag |
| Plaintext CTR thay đổi một vài byte nhưng không exception | CTR không xác thực, ciphertext bị sửa hoặc counter sai | Dùng AEAD/MAC và nonce đúng |
| Hai ciphertext ECB có nhiều block giống nhau | Mode lộ khối lặp | Không dùng ECB cho message thực tế; thay bằng AEAD |
| CTR cho ra thông tin về hai plaintext | Keystream/counter bị tái sử dụng cùng khóa | Sửa quy trình nonce/counter, đánh giá dữ liệu đã lộ và rotation khóa |
| Giải mã thành công nhưng API business data sai | Có thể do thiếu authentication/validity checks, record bị tráo hoặc replay | Ràng buộc integrity, AAD/context và authorization/replay controls |
| GCM báo sai tag sau migration | Sai nonce/tag layout, AAD hoặc format/version | Thiết kế format rõ ràng; test vector và kiểm liệu đúng phiên bản |

Một lỗi thiết kế thường gặp: trả HTTP 400 với text `invalid padding` còn HTTP 401 với `bad MAC`, làm đối thủ có một oracle mạnh. Trên giao diện bên ngoài, xử lý message không xác thực bằng lỗi đồng nhất và quan sát log nội bộ có kiểm soát. Đừng tin vào text exception từ provider làm thông báo public.

### 11.1. Cẩn thận với thứ tự xử lý

**Sai:**

```text
Nhận IV+ciphertext
→ Giải mã CBC
→ Parse JSON
→ Kiểm role trong JSON
→ Cuối cùng mới kiểm HMAC (hoặc không kiểm)
```

**Đúng với Encrypt-then-MAC:**

```text
Nhận packet, kiểm framing/size
→ Kiểm HMAC xác thực version+IV+ciphertext+context
→ Nếu sai, từ chối; không decrypt
→ Nếu đúng, mới giải mã CBC
→ Parse dữ liệu
→ Kiểm schema và phân quyền nghiệp vụ
```

**Đúng với GCM:**

```text
Nhận packet, giải mã metadata và giới hạn size
→ Cung cấp key+nonce+AAD đúng cho Cipher GCM
→ doFinal xác minh tag
→ Nếu tag sai, từ chối toàn bộ plaintext
→ Nếu tag đúng, parse/check schema và phân quyền nghiệp vụ
```

MAC/AEAD tạo ranh giới xác thực cho bytes; **schema validation và authorization** vẫn là các bước riêng.

## 12. Câu hỏi tự kiểm tra có đáp án

**1. Tại sao ECB bị coi là không phù hợp để mã hóa một ảnh?** Vì các block plaintext giống nhau sinh ra các block ciphertext giống nhau dưới cùng khóa, làm lộ mẫu/đường nét/cấu trúc dù pixel gốc đã mã hóa.

**2. Dùng AES-256 có giải quyết mẫu lặp ECB không?** Không. Key dài hơn không thay đổi phép ánh xạ khối deterministic của ECB.

**3. CBC có IV ngẫu nhiên 16 byte nhưng không có HMAC. Dữ liệu có toàn vẹn không?** Không. IV hoặc ciphertext có thể bị sửa; padding oracle cũng có thể xuất hiện nếu ứng dụng phản hồi theo lỗi giải mã.

**4. IV CBC có phải là secret không?** Không, thông thường có thể công khai. Nhưng phải được tạo mới/khó dự đoán khi cần và **xác thực toàn vẹn** cùng message nếu dùng EtM.

**5. Tại sao `AES/CTR/NoPadding` mã hóa dữ liệu 17 byte không cần padding?** CTR biến đầu ra AES của counter block thành keystream XOR với plaintext; block cuối dùng phần keystream cần thiết, ciphertext dài đúng 17 byte.

**6. Nếu CTR dùng cùng key và counter block cho hai message, đối thủ học gì?** Khi thấy `C1 XOR C2`, đối thủ tính được `P1 XOR P2`; plaintext có cấu trúc/biết trước có thể bị lộ đáng kể.

**7. Vì sao CTR decrypt thành công không chứng minh ciphertext nguyên vẹn?** Giải mã CTR là XOR với keystream; đổi bit ciphertext sẽ đổi bit plaintext tương ứng mà không yêu cầu kiểm tag.

**8. Vì sao CBC+HMAC phải xác minh MAC trước decrypt?** Để dữ liệu không xác thực không đi vào phép giải mã padding và tạo kênh quan sát dẫn tới padding oracle.

**9. Có thể thay HMAC bằng SHA-256(ciphertext) không?** Không. Hash công khai không có secret key; đối thủ sửa ciphertext rồi tính lại hash.

**10. GCM có chống replay không?** Không. GCM xác thực nội dung của một packet, nhưng packet cũ có tag hợp lệ vẫn có thể được phát lại; cần sequence/state hoặc cơ chế một lần.

**11. GCM nonce cần giữ bí mật không?** Không, nhưng **phải không lặp dưới cùng key**; không để cơ chế khởi động/retry/đồng bộ khiến nonce tái sử dụng.

**12. Khi nào nên chọn CBC+HMAC thay vì GCM?** Chủ yếu khi phải tương thích định dạng/giao thức legacy đã được kiểm chứng, có thiết kế MAC trước decrypt, khóa tách biệt và versioning. Hệ thống mới thường ưu tiên AEAD được cung cấp và quản lý đúng cách.

## 13. Checklist trước khi đưa mã hóa đối xứng vào production

1. **Không dùng ECB** để mã hóa thông điệp có cấu trúc hoặc dữ liệu người dùng.
2. Chọn transformation đầy đủ; không gọi `Cipher.getInstance("AES")` một cách mơ hồ.
3. **Ưu tiên AEAD** như AES-GCM; nếu dùng CBC/CTR, bổ sung sơ đồ xác thực đúng với khóa riêng và verify trước decrypt.
4. Tạo **key** bằng nguồn mật mã phù hợp, lưu trong cơ chế quản lý khóa; không hard-code vào source, log hoặc URL.
5. Xác định kích thước/format IV hoặc nonce; **CBC IV mới, khó dự đoán**; CTR/GCM **không trùng counter/nonce với cùng key**, có giới hạn usage và xử lý restart/retry.
6. Xác thực **version, IV/nonce, ciphertext và metadata/AAD ảnh hưởng đến ngữ nghĩa**; không bỏ trường quan trọng khỏi MAC/tag.
7. Không phát plaintext hay xử lý business logic nếu xác thực thất bại; lỗi bên ngoài đồng nhất, không leak padding/timing.
8. Có cơ chế chống **replay**, phân quyền và kiểm tra schema theo threat model; MAC/AEAD không tự cung cấp chúng.
9. Định dạng lưu trữ có version/key ID và quy trình migration; không thay mode mà bỏ qua dữ liệu cũ.
10. Viết test cho **round-trip**, sửa một byte ciphertext/tag/IV/AAD phải bị từ chối trong đường có xác thực, ràng buộc context và failure behavior.

## Tài liệu chính thống

- [NIST SP 800-38A – Recommendation for Block Cipher Modes of Operation: Methods and Techniques](https://csrc.nist.gov/pubs/sp/800/38/a/final): định nghĩa ECB, CBC, CTR; yêu cầu IV/counter và tính chất vận hành.
- [NIST SP 800-38D – Galois/Counter Mode (GCM) and GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final): xác thực AES-GCM và yêu cầu quản lý IV/nonce.
- [Java Cryptography Architecture (JCA) Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): engine `Cipher`, provider và API mật mã Java.
- [Java SE 17 – Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): transformation, padding, AEAD/GCM, `updateAAD` và hành vi encryption/decryption.
- [Java SE 17 – GCMParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): tag length và nonce/IV trong Java.
- [Java SE 17 – Mac](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Mac.html) và [MessageDigest.isEqual](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html): HMAC và đối chiếu tag trong một thiết kế có xác thực.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-006">6.6 AEAD, AES/GCM/NoPadding và vai trò của authentication tag</a>

<details>
<summary>Xem chi tiết</summary>

# 6.6 AEAD, AES/GCM/NoPadding và vai trò của authentication tag

Mã hóa dữ liệu nhằm giữ bí mật là **chưa đủ** khi dữ liệu có thể bị kẻ tấn công thay đổi. Một bản mã vẫn có thể bị chỉnh sửa, tráo ngữ cảnh hoặc thay bằng bản mã khác; ứng dụng cần biết có nên **chấp nhận và xử lý** kết quả giải mã hay không. **AEAD (Authenticated Encryption with Associated Data)** kết hợp tính bí mật của mã hóa và phép kiểm tính toàn vẹn/xác thực dựa trên khóa trong một cơ chế được thiết kế thống nhất. Trong Java, biến thể thực tế phổ biến là **`AES/GCM/NoPadding`**.

Bài học giải thích AEAD, AES-GCM, **authentication tag** (thẻ xác thực), nonce/IV, AAD và các quy tắc dùng `Cipher` an toàn. Ví dụ Java 17+ ở phần 8 minh họa dữ liệu thật được khôi phục và các sửa đổi bị từ chối.

## 1. AEAD giải quyết vấn đề gì?

### 1.1. Tính bí mật và tính toàn vẹn là hai yêu cầu khác nhau

Giả sử một ứng dụng gửi bản ghi chuyển khoản giữa các dịch vụ:

~~~text
Bản rõ P = "amount=100;currency=VND"
             |
             v
          Mã hóa
             |
             v
Bản mã C được truyền qua mạng
~~~

Mã hóa cung cấp **confidentiality (tính bí mật)**: người không có khóa phù hợp không đọc được bản rõ. Tuy nhiên, **không phải mọi chế độ mã hóa đều tự xác thực bản mã**. Với một số cách mã hóa không có xác thực, kẻ tấn công có thể sửa byte, cắt ghép hoặc thay thông điệp và khiến bên nhận xử lý dữ liệu sai mà không có bằng chứng mật mã đáng tin rằng bản mã đã bị sửa.

AEAD thêm cơ chế **authentication tag**. Bên nhận chỉ được chấp nhận thông điệp khi xác minh tag thành công với **đúng khóa, nonce, ciphertext và AAD**.

| Thuộc tính | Ý nghĩa trong AEAD |
| --- | --- |
| **Confidentiality** | Giấu bản rõ trước người không có khóa giải mã. |
| **Integrity** | Phát hiện sửa đổi trái phép đối với ciphertext và dữ liệu liên kết được xác thực. |
| **Key-based authenticity** | Bên nhận có bằng chứng thông điệp/tag hợp lệ được tạo bởi bên có quyền dùng khóa bí mật chung (theo giả định khóa chưa lộ). |
| **Associated-data integrity** | Dữ liệu công khai quan trọng vẫn được xác thực dù không mã hóa. |
| **Không tự chống replay** | Kẻ tấn công có thể phát lại nguyên gói hợp lệ nếu giao thức không quản lý số thứ tự, nonce của thông điệp, phiên hoặc request ID. |
| **Không tự chứng minh cá nhân cụ thể** | Nếu nhiều bên cùng biết khóa đối xứng, tag không chỉ ra bên nào đã tạo thông điệp. Không thay được chữ ký số/PKI hoặc xác thực danh tính. |

**Quan trọng:** “Authenticated” trong AEAD là **xác thực mật mã theo khóa**, không tự động có nghĩa “người dùng đã đăng nhập”, “đúng tenant”, “được phép chuyển tiền”, hay “chắc chắn do công ty X phát hành”. Ứng dụng vẫn phải xác thực danh tính, phân quyền và tin cậy nguồn khóa.

### 1.2. Vì sao không chỉ dùng AES ở bất kỳ mode nào?

Tên `AES` chỉ là thuật toán mã khối; bảo mật ứng dụng còn phụ thuộc vào **mode**, cách dùng IV/nonce và cách xác minh tính toàn vẹn:

- **AES-ECB** không cung cấp bảo mật cần thiết cho các mẫu dữ liệu lặp; không nên dùng cho dữ liệu ứng dụng.
- **AES-CBC** với padding chỉ cung cấp mã hóa nếu đứng riêng; cần một cơ chế xác thực đúng cách và xử lý lỗi cẩn thận. Tự ghép kiểm tra padding/giải mã vào giao thức dễ tạo lỗi bảo mật.
- **AES-CTR** bảo vệ bí mật khi nonce đúng, nhưng ciphertext có tính sửa đổi có kiểm soát nếu không xác thực.
- **AES-GCM** là một mode **AEAD**, kết hợp mã hóa kiểu counter với thẻ xác thực GCM; phù hợp cho nhiều nhu cầu mới khi quản lý khóa/nonce đúng.
- **ChaCha20-Poly1305** cũng là AEAD; lựa chọn thuật toán phải theo thư viện, môi trường, tiêu chuẩn và giao thức cụ thể, không tự trộn cấu phần của hai thuật toán.

Nên ưu tiên **primitive AEAD có sẵn trong thư viện đáng tin**; đừng tự thiết kế bộ “AES + hash”, hoặc giả định `SHA-256(ciphertext)` đủ để ngăn giả mạo vì digest không có khóa.

## 2. Mô hình toán học của AES-GCM

AES-GCM nhận các dữ liệu sau:

| Ký hiệu | Tên | Cần bí mật? | Vai trò |
| --- | --- | --- | --- |
| `K` | AES secret key | **Có** | Bí mật của các bên được phép mã hóa/giải mã. |
| `N` | Nonce/IV | **Không**, nhưng phải duy nhất cho mỗi lần mã hóa dưới cùng khóa | Tạo trạng thái mã hóa GCM; sai quy tắc nonce có thể phá nghiêm trọng an toàn. |
| `P` | Plaintext | Có, nếu ứng dụng cần giữ kín | Nội dung muốn mã hóa. |
| `A` | AAD (Additional Authenticated Data) | **Không** cần giữ bí mật | Metadata phải được ràng buộc và xác thực cùng bản mã. |
| `C` | Ciphertext | Không | Bản mã của `P`, dài bằng `P` trong AES-GCM. |
| `T` | Authentication tag | Không | Thẻ xác thực của ciphertext và AAD trong ngữ cảnh khóa và nonce. |

Mô hình khái quát:

~~~text
                    K (khóa bí mật)        N (nonce duy nhất theo K)
                             \              /
                              \            /
P (plaintext) ----------------> AES-GCM Encrypt <------- A (AAD)
                                      |
                                      v
                              C (ciphertext) + T (tag)

K, N, A, C, T ----------------> AES-GCM Decrypt/Verify
                                      |
                            +---------+---------+
                            |                   |
                       tag hợp lệ          tag không hợp lệ
                            |                   |
                       trả P              báo lỗi/từ chối
~~~

Dưới dạng hàm:

~~~text
(C, T) = AEAD_Encrypt(K, N, P, A)
P      = AEAD_Decrypt(K, N, C, T, A), nếu tag hợp lệ
ERROR  = AEAD_Decrypt(K, N, C, T, A), nếu tag không hợp lệ
~~~

**Không có bước “giải mã thành công rồi kiểm tag tùy chọn”.** Việc chấp nhận dữ liệu phải phụ thuộc vào phép xác thực thành công. Khi dùng API streaming hoặc provider có thể tạo output trung gian, **không được sử dụng plaintext chưa xác thực** cho nghiệp vụ, ghi DB, phản hồi API hay gọi hệ thống khác trước khi xác minh xong.

## 3. AES/GCM/NoPadding nghĩa là gì trong JCA/JCE?

Trong Java, gọi:

~~~java
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
~~~

Chuỗi transformation có ba thành phần:

| Thành phần | Ý nghĩa |
| --- | --- |
| `AES` | Thuật toán mã khối đối xứng Advanced Encryption Standard; khóa thường 128/192/256 bit theo hệ thống. |
| `GCM` | Galois/Counter Mode: mode AEAD cung cấp mã hóa và authentication tag. |
| `NoPadding` | GCM không dùng kiểu padding như PKCS#5/PKCS#7 cho plaintext; dữ liệu không cần bù thành bội số của 16 byte. |

GCM kết hợp cơ chế tạo keystream kiểu **CTR** để mã hóa với phép xác thực dựa trên phép toán trong trường hữu hạn, thường được mô tả qua **GHASH** và khối tag. Người viết Java **không cần tự tính GHASH, counter hoặc tag**; `Cipher` và provider thực hiện phần thuật toán. Tự viết lại để “tối ưu” là dễ mắc lỗi nghiêm trọng.

Trong cách dùng tiêu chuẩn với provider JCE phổ biến:

- Đặt tag 128 bit bằng `new GCMParameterSpec(128, nonce)`.
- Trên **mã hóa**, `Cipher.doFinal(plaintext)` trả **ciphertext nối với tag**; ví dụ bài này lưu chúng liền nhau dưới tên `ciphertextAndTag`.
- Trên **giải mã**, đưa toàn bộ phần **ciphertext + tag** vào `doFinal(...)`; provider kiểm tag và chỉ trả plaintext khi xác thực thành công.
- Vì GCM dùng `NoPadding`, khi plaintext dài `n` byte, phần ciphertext dài `n` byte; tổng `ciphertextAndTag` dài `n + 16` byte khi tag 128 bit.
- **Tag không phải nonce**: nonce chọn cho một lần mã hóa, còn tag là kết quả phép tính xác thực gắn với nội dung và AAD.
- **Tag không phải mã băm SHA-256**: không thể thay bằng `MessageDigest.digest(...)`, cắt vài byte hash rồi gọi là tag.

### 3.1. Authentication tag bảo vệ điều gì?

Giả sử người nhận có đủ `K, N, A, C, T`. Khi một trong các dữ liệu cần xác thực bị thay đổi mà không có khóa đúng để tạo tag hợp lệ, xác minh GCM sẽ thất bại với xác suất rất cao theo giới hạn bảo mật của thuật toán.

Các tình huống minh họa:

| Thay đổi ở phía nhận | Hành vi mong đợi |
| --- | --- |
| **Ciphertext `C` bị đổi một byte** | Tag không khớp; từ chối. |
| **Tag `T` bị đổi một byte** | Tag không khớp; từ chối. |
| **AAD `A` đổi** | Tag không khớp; từ chối; AAD mặc dù không mã hóa vẫn được xác thực. |
| **Nonce `N` đổi** | Giải mã với nonce khác, xác thực không hợp lệ; từ chối. |
| **Dùng sai khóa `K`** | Không thể xác minh tag hợp lệ; từ chối. |
| **Gửi lại nguyên gói hợp lệ, đúng AAD và khóa** | Tag **vẫn hợp lệ**. AEAD không tự biết thông điệp đã dùng trước đó. |

Với `Cipher` của Java, tag sai khi giải mã GCM thường dẫn tới **`AEADBadTagException`** (là loại `BadPaddingException`). Không catch rồi âm thầm tiếp tục dùng dữ liệu. Đừng tự so sánh tag bằng phép `==`, `Arrays.equals` hoặc tự tạo tag — **provider xác minh tag bên trong `doFinal`**.

Tag 128 bit là lựa chọn thông dụng. Tag dài hơn làm việc giả mạo khó hơn trong mô hình thích hợp; không tùy tiện rút ngắn tag hoặc cho hai bên dùng độ dài tag khác nhau. **Tag có độ dài cố định không bảo đảm tuyệt đối không thể giả mạo**, mà cho xác suất/giới hạn an toàn khi tuân thủ các giả định như nonce duy nhất và giới hạn số lần sử dụng khóa.

## 4. Nonce/IV: có thể công khai nhưng không được tái sử dụng với cùng khóa

### 4.1. Quy tắc then chốt

**Đối với AES-GCM, không bao giờ mã hóa hai thông điệp khác nhau với cùng cặp (key, nonce).** Tái sử dụng nonce dưới cùng khóa có thể làm lộ quan hệ giữa các plaintext và dẫn tới giả mạo tag; không thể “sửa” hậu quả chỉ bằng cách lưu tag dài hơn.

Thông thường Java dùng **nonce 96 bit = 12 byte** cho GCM. Nonce **không cần bí mật**, có thể đặt ngay đầu gói tin. Bên giải mã cần nonce giống hệt để xác minh thành công.

~~~text
Khóa K_A:   N_001, N_002, N_003, ...   (mỗi nonce chỉ dùng 1 lần để mã hóa)
Khóa K_B:   có không gian nonce riêng
LỖI:       Encrypt(K_A, N_001, P1) và Encrypt(K_A, N_001, P2)
~~~

**Hai chiến lược nonce phổ biến:**

1. **Nonce ngẫu nhiên 96 bit:** sinh bằng `SecureRandom` trong bài demo. Mỗi lần có xác suất trùng rất nhỏ ở số lượng thao tác hợp lý, nhưng **không bảo đảm duy nhất tuyệt đối**. Với nhiều bản ghi/tốc độ cao, phải có hạn mức sử dụng khóa, giám sát rủi ro va chạm và xoay khóa theo chính sách.
2. **Nonce đếm/định danh có cấu trúc:** thiết kế prefix định danh instance/partition cùng counter duy nhất, có cơ chế phối hợp và bền vững qua restart. Phải tránh hai process dùng chung khóa cùng cấp lại một nonce. Không tự chế khi chưa có quy tắc phân phối, lưu trạng thái, giới hạn số lượng và kiểm thử rõ ràng.

**Không dùng** nonce cố định cho mọi request, nonce từ timestamp không đảm bảo duy nhất, chuỗi chỉ số ngẫu nhiên yếu hoặc `new Random()` để sinh nonce. Nếu một hệ thống có nhiều process cùng giữ khóa AES, cần giải quyết tính duy nhất nonce **toàn hệ thống**, không chỉ trong một process.

**Không nhầm:** nonce GCM dùng cho yêu cầu **duy nhất khi mã hóa**; nonce/request ID dùng chống replay ở tầng giao thức là một bài toán khác dù có thể cùng tên. Tính duy nhất nonce AES-GCM **không tự ngăn phát lại** một ciphertext + tag hợp lệ.

### 4.2. Vì sao mất nonce sẽ không giải mã được?

Nonce là tham số đầu vào của GCM; giải mã cần cả **khóa và nonce ban đầu**. Nếu chỉ lưu ciphertext + tag mà bỏ nonce, không thể tái tạo nonce ngẫu nhiên sau này. Do đó, định dạng lưu trữ thường đưa nonce vào record bên cạnh ciphertext/tag.

Để phục vụ xoay khóa, record còn cần **key identifier** hoặc cách ánh xạ khóa an toàn. Key ID có thể công khai nhưng phải được xác thực/kiểm soát ngữ cảnh để tránh nhầm khóa hoặc downgrade. Không bao giờ xuất khóa AES cùng gói dữ liệu.

## 5. AAD: metadata không cần mã hóa nhưng phải được bảo vệ khỏi sửa đổi

**AAD (Additional Authenticated Data)** là dữ liệu byte được đưa vào phép tính tag nhưng **không nằm trong ciphertext**. Bên nhận phải tái tạo **chính xác cùng dãy byte AAD** khi giải mã.

Ví dụ bạn mã hóa giá trị một giao dịch:

~~~text
P   = "amount=100;currency=VND"  // cần giữ bí mật
AAD = "v1|tenant=blue|type=transfer"  // được phép công khai

Encrypt(K, N, P, AAD) → C + T

Decrypt(K, N, C, T, "v1|tenant=red|type=transfer") → lỗi tag
~~~

AAD giúp ràng buộc ciphertext vào **ngữ cảnh**. Nếu dữ liệu mã hóa của tenant A bị di chuyển sang tenant B nhưng bên B giải mã với AAD chứa tenant B từ **ngữ cảnh đáng tin**, tag sẽ không khớp. Tuy vậy, AAD không thể thay kiểm tra quyền tenant: nếu ứng dụng tùy tiện **tin giá trị tenant do kẻ tấn công tự gửi trong cùng gói**, việc xác minh tag không tự thực hiện phân quyền.

Các trường thường cân nhắc đưa vào AAD khi phù hợp với thiết kế:

- **Phiên bản định dạng/protocol** để ngăn nhầm định dạng hoặc thuật toán.
- **Record ID, object ID, tenant ID** nếu phải buộc bản mã thuộc một đối tượng/ngữ cảnh xác định.
- **Loại nội dung, endpoint, mục đích sử dụng khóa** nếu một khóa được dùng trong nhiều luồng cần tách ngữ cảnh.
- **Thông tin định danh khác** cần xác thực nhưng không cần che giấu.

Chú ý:

- AAD **không bí mật**: đừng để thông tin nhạy cảm vào AAD rồi tưởng đã được mã hóa.
- **Khác một byte là khác AAD**: UTF-8/UTF-16, newline, thứ tự trường JSON, khoảng trắng, cách viết số và Unicode normalization đều quan trọng. Thiết kế một biểu diễn byte **canonical** rõ ràng.
- Trong API Java, gọi `cipher.updateAAD(aad)` **sau `init` và trước khi đưa plaintext/ciphertext vào `update` hay `doFinal`**. Thứ tự gọi sai có thể gây lỗi API.
- Nếu không có metadata cần ràng buộc, AAD có thể rỗng; không ép phải tạo AAD giả. Nhưng nếu giao thức yêu cầu xác thực metadata, quên `updateAAD` là lỗi thiết kế.
- AAD dùng cho cả mã hóa và giải mã; **hai bên phải trùng byte 100%**. Không nên tùy ý chuyển cấu trúc từ string sang JSON mà không có giao ước phiên bản.

## 6. Định dạng truyền/lưu: nonce, ciphertext và tag nằm ở đâu?

Trong ví dụ Java, bản ghi nhị phân có dạng:

~~~text
+-------------------+--------------------------+----------------+
| nonce (12 byte)   | ciphertext (n byte)      | tag (16 byte)  |
+-------------------+--------------------------+----------------+
<--------------------- packet = 12 + n + 16 byte ---------------->
~~~

Một phần `nonce + ciphertext + tag` có thể được **Base64-encode** để đặt trong JSON. Base64 chỉ là **biểu diễn**, không tự bảo mật dữ liệu. AAD trong ví dụ là chuỗi byte lấy từ **ngữ cảnh ứng dụng** và được truyền/tái tạo riêng, không nằm trong packet.

Trong hệ thống có nhiều phiên bản và khóa, record thực tế thường cần thêm metadata, ví dụ:

~~~text
version | keyId | nonce | ciphertext | authenticationTag
             \                       /
              metadata định dạng được quy định cách xác thực
~~~

Nếu `version` hoặc `keyId` quyết định cách giải mã, cần thiết kế gắn metadata đó vào **AAD và/hoặc hợp đồng kiểm tra tin cậy** để không cho attacker tráo ngữ cảnh. Đặc tả phải nói rõ kiểu/độ dài từng trường, thứ tự byte, giới hạn payload, chế độ cấp nonce và cách xác định khóa. **Đừng cố đoán** tag nằm ở byte nào nếu hệ thống khác có cách lưu tách tag khỏi ciphertext; đó là khác biệt của định dạng giao thức, không phải tự do bỏ tag.

**Ví dụ kích thước:** Plaintext ASCII `"amount=100;currency=VND"` có 23 byte. Với AES-GCM tag 16 byte và nonce 12 byte, packet bài demo dài **51 byte** (`12 + 23 + 16`). Kích thước packet có thể khác nếu định dạng thêm header hoặc các trường khác.

## 7. Chuẩn bị an toàn trước khi lập trình

1. **Khóa:** Tạo bằng `KeyGenerator.getInstance("AES")` với nguồn `SecureRandom`. AES-128 hoặc AES-256 có thể sử dụng theo yêu cầu và chính sách hệ thống; mẫu dưới dùng AES-128 để dễ chạy rộng rãi, **không lưu khóa trong mã nguồn**.
2. **Nonce:** 12 byte mới cho mỗi lần mã hóa dưới cùng khóa; sinh bằng CSPRNG trong ví dụ. Đối với sản xuất nhiều tiến trình, thiết kế cấp nonce không trùng toàn hệ thống.
3. **Tag:** Dùng 128 bit qua `GCMParameterSpec(128, nonce)` ở cả hai phía.
4. **AAD:** Xác định byte ổn định bằng `StandardCharsets.UTF_8` và một quy ước định dạng thống nhất; lấy các thuộc tính nghiệp vụ quan trọng từ ngữ cảnh đã xác minh.
5. **Cipher:** Tạo một `Cipher` riêng cho mỗi thao tác; các đối tượng `Cipher` có trạng thái, không dùng chung không đồng bộ giữa nhiều request/thread.
6. **Lỗi:** Không trả plaintext nếu xác thực thất bại. Không cho phép client phân biệt quá chi tiết sai tag/sai khóa/sai AAD trong thông báo API; log chỉ metadata cần thiết, tránh log plaintext, secret, khóa hoặc các chi tiết nhạy cảm.
7. **Giới hạn dữ liệu:** Kiểm tra độ dài packet trước khi cấp phát/giải mã; đặt giới hạn kích thước theo nghiệp vụ và chính sách GCM của hệ thống. Một lần `doFinal` trên cả payload phù hợp ví dụ ngắn; payload lớn cần cấu trúc record/chunk AEAD được thiết kế riêng để xác thực từng chunk và thứ tự của chúng.

## 8. Ví dụ Java 17+ hoàn chỉnh: mã hóa, giải mã và phát hiện sửa đổi

Ví dụ chạy bằng **JDK chuẩn**, không cần dependency bên ngoài. Nó tạo key tạm, sinh nonce mới cho **một lần mã hóa**, gắn nonce trước `ciphertextAndTag`, rồi thử giải mã đúng và các bản bị sửa. Các dòng stdout dưới đây **không in khóa, nonce hay ciphertext ngẫu nhiên**, do đó có thể kiểm tra ổn định.

Lưu với tên chính xác **`AeadGcmDemo.java`**:

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class AeadGcmDemo {
    static final int NONCE_BYTES = 12;
    static final int TAG_BITS = 128;
    static final SecureRandom RANDOM = new SecureRandom();

    static byte[] encrypt(SecretKey key, byte[] plain, byte[] aad)
            throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        byte[] ciphertextAndTag = cipher.doFinal(plain);

        // Định dạng ví dụ: 12 byte nonce || ciphertext || 16 byte tag.
        return ByteBuffer.allocate(nonce.length + ciphertextAndTag.length)
                .put(nonce)
                .put(ciphertextAndTag)
                .array();
    }

    static byte[] decrypt(SecretKey key, byte[] packet, byte[] aad)
            throws GeneralSecurityException {
        if (packet.length < NONCE_BYTES + TAG_BITS / 8) {
            throw new IllegalArgumentException("Packet too short");
        }

        ByteBuffer source = ByteBuffer.wrap(packet);
        byte[] nonce = new byte[NONCE_BYTES];
        source.get(nonce);
        byte[] ciphertextAndTag = new byte[source.remaining()];
        source.get(ciphertextAndTag);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);

        // Thành công mới trả plaintext; sai tag ném AEADBadTagException.
        return cipher.doFinal(ciphertextAndTag);
    }

    static boolean rejected(SecretKey key, byte[] packet, byte[] aad)
            throws GeneralSecurityException {
        try {
            decrypt(key, packet, aad);
            return false;
        } catch (AEADBadTagException expected) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128, RANDOM);
        SecretKey key = generator.generateKey();

        byte[] plain = "amount=100;currency=VND"
                .getBytes(StandardCharsets.UTF_8);
        byte[] aad = "v1|tenant=blue|type=transfer"
                .getBytes(StandardCharsets.UTF_8);
        byte[] packet = encrypt(key, plain, aad);

        System.out.println("Plain bytes     = " + plain.length);
        System.out.println("Packet bytes    = " + packet.length);
        System.out.println("Tag bytes       = " + TAG_BITS / 8);
        System.out.println("Restored        = " + new String(
                decrypt(key, packet, aad), StandardCharsets.UTF_8));

        byte[] changedCiphertext = packet.clone();
        changedCiphertext[NONCE_BYTES] ^= 1;
        byte[] changedTag = packet.clone();
        changedTag[changedTag.length - 1] ^= 1;
        byte[] changedNonce = packet.clone();
        changedNonce[0] ^= 1;

        System.out.println("Changed data    = " +
                rejected(key, changedCiphertext, aad));
        System.out.println("Changed tag     = " +
                rejected(key, changedTag, aad));
        System.out.println("Changed nonce   = " +
                rejected(key, changedNonce, aad));
        System.out.println("Changed AAD     = " + rejected(key, packet,
                "v1|tenant=red|type=transfer"
                        .getBytes(StandardCharsets.UTF_8)));

        SecretKey wrongKey = generator.generateKey();
        System.out.println("Wrong key       = " +
                rejected(wrongKey, packet, aad));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AeadGcmDemo.java
java AeadGcmDemo
~~~

Đầu ra kỳ vọng:

~~~text
Plain bytes     = 23
Packet bytes    = 51
Tag bytes       = 16
Restored        = amount=100;currency=VND
Changed data    = true
Changed tag     = true
Changed nonce   = true
Changed AAD     = true
Wrong key       = true
~~~

**Giải thích chính xác:**

1. `encrypt` sinh nonce 12 byte bằng `SecureRandom`, tạo `Cipher` với AES-GCM và tag 128 bit, đưa AAD vào trước khi mã hóa.
2. `doFinal(plain)` tạo ciphertext nối tag 16 byte. Packet là **nonce + ciphertext + tag**; bản rõ 23 byte nên packet 51 byte.
3. `decrypt` lấy nonce từ packet, thiết lập cùng độ dài tag và AAD, sau đó đưa `ciphertextAndTag` vào `doFinal`. Tag hợp lệ thì byte plaintext trả về đúng.
4. Khi sửa byte đầu ciphertext, byte cuối tag, byte nonce, hoặc đổi tenant trong AAD, xác minh thất bại; `rejected` bắt `AEADBadTagException` nên các dòng `Changed ...` in `true` (nghĩa là **bị từ chối**, không phải xác thực thành công).
5. Tạo một khóa AES khác khiến `Wrong key = true`; chứng minh không thể giải mã bằng key khác chỉ vì đã biết nonce/ciphertext/tag.

**Đây là ví dụ học tập:** Khóa sinh tạm mất sau khi chạy, nonce ngẫu nhiên chưa có cơ chế phối hợp cho nhiều instance và packet không chứa version/key ID. Trong hệ thống thật phải thêm quy tắc lưu khóa, cấp nonce, phiên bản schema, giới hạn payload và chống replay khi cần. Đoạn code minh họa nguyên tắc xác minh tag, không phải một thư viện lưu trữ ciphertext hoàn chỉnh.

## 9. Vai trò của tag so với MAC và chữ ký số

AES-GCM là **AEAD**: bên có secret key có thể tạo ciphertext/tag; người có cùng key có thể giải mã và xác minh. Tag được tích hợp trong phép GCM, **không phải đối tượng chứng minh danh tính bên ký độc lập**.

| Cơ chế | Giấu plaintext? | Xác thực/toàn vẹn? | Ai có thể kiểm tra? | Lưu ý |
| --- | --- | --- | --- | --- |
| **AES-GCM (AEAD)** | **Có**, nếu khóa bí mật và nonce đúng | **Có**, với ciphertext và AAD | Bên giữ secret key phù hợp | Tag gắn với mã hóa; nonce phải duy nhất theo key |
| **AES-CTR không có MAC** | Có theo giả định mode/nonce đúng | **Không** | Không có bước kiểm tag | Ciphertext có thể bị chỉnh sửa có chủ đích |
| **HMAC-SHA256** | **Không** | Có, theo khóa chia sẻ | Bên giữ HMAC secret | HMAC không giải mã, không tạo ciphertext |
| **Chữ ký số (Ed25519, RSA-PSS...)** | **Không** | Có, theo khóa ký và message | Bất kỳ ai có public key đáng tin | Ký bằng private key; phục vụ xác minh công khai |
| **SHA-256 không khóa** | Không | Tạo digest; không xác thực nguồn nếu đứng riêng | Ai cũng tính được | Không thay thế tag, HMAC hoặc chữ ký |
| **Base64** | Không | Không | Ai cũng encode/decode | Chỉ biểu diễn packet trong text |

**Không nên “nâng cấp” AES-GCM bằng cách tự nối thêm `SHA-256(ciphertext)`** rồi bỏ tag. Digest không có khóa không ngăn attacker thay bản mã và tạo digest mới. Ngược lại, cũng không cần tự ghép một HMAC riêng vào GCM chỉ để “bảo vệ tag tốt hơn” nếu không có giao thức đặc tả và đánh giá độc lập; dùng AEAD chuẩn đúng cách là đủ cho mục tiêu của nó.

## 10. Bốn tình huống ứng dụng điển hình

### 10.1. Mã hóa một record trong cơ sở dữ liệu

~~~text
DB row:
  id = 42
  tenant = blue
  keyId = k2026-10
  nonce = ...
  ciphertextAndTag = ...

Giải mã:
  key = KMS.resolve(keyId)
  aad = canonical("v1", tenant từ ngữ cảnh tin cậy, row id)
  plaintext = AES-GCM-Decrypt(key, nonce, ciphertextAndTag, aad)
~~~

Nếu ai đó tráo ciphertext giữa hai row mà ứng dụng lấy `row id`/tenant từ **row/ngữ cảnh đã kiểm chứng** vào AAD, việc tráo thường bị phát hiện bằng tag không hợp lệ. Cần thiết kế cách ràng buộc `keyId` và version, chống attacker điều khiển metadata đầu vào không tin cậy. Encryption không thay phân quyền đọc DB, mã hóa ở ổ đĩa hoặc chính sách truy cập.

### 10.2. Nhận request API có thể bị phát lại

Một gói AES-GCM hợp lệ **có thể được gửi lại** nếu vẫn dùng cùng key, nonce, ciphertext/tag và AAD. Tag không chứa lịch sử đã nhận. Với API có tác dụng tạo lệnh thanh toán hoặc sửa dữ liệu, ứng dụng nên ràng buộc request ID, timestamp/expiry hoặc sequence vào giao thức/AAD, **sau đó kiểm tra hiệu lực và chống dùng lại bằng trạng thái tin cậy**. Chỉ đưa timestamp vào AAD mà không kiểm thời gian/không ghi nhận ID đã dùng vẫn không chống replay đầy đủ.

### 10.3. Mã hóa file lớn hoặc dữ liệu streaming

Không đọc file rất lớn vào RAM chỉ để dùng một `doFinal` nếu hệ thống không có bộ nhớ phù hợp. Có thể dùng API `update(...)`, `doFinal(...)` của `Cipher` theo đúng giao thức nhưng **không được tin/tiêu thụ plaintext trung gian trước khi tag hoàn tất**. Thiết kế record/chunk AEAD cho file lớn thường cần:

- Nonce duy nhất cho mỗi chunk, dưới cùng khóa;
- Ràng buộc **file ID, chunk index, tổng số chunk, vị trí/thứ tự** qua AAD;
- Xác minh **đủ các chunk và metadata toàn cục**, chống cắt bớt/đổi thứ tự/thay chunk;
- Giới hạn dung lượng và quản lý quota khóa đúng chuẩn.

Không thể đơn giản coi “mỗi chunk decrypt được” là toàn bộ file chắc chắn hoàn chỉnh nếu kẻ tấn công có thể xóa hoặc hoán vị chunk.

### 10.4. Dữ liệu mã hóa rồi Base64 trong JSON

~~~json
{
  "version": 1,
  "keyId": "k2026-10",
  "payloadBase64": "..."
}
~~~

Trong ví dụ minh họa, `payloadBase64` là Base64 của **nonce || ciphertext || tag**. API cần quy ước AAD và xác minh version/key ID theo hợp đồng. **Giải mã Base64 không phải giải mã AES-GCM**; còn cần khóa, nonce, ciphertext, tag và AAD khớp.

## 11. Những lỗi bảo mật và lỗi API thường gặp

| Lỗi | Vì sao sai/nguy hiểm | Cách làm đúng |
| --- | --- | --- |
| **Tái sử dụng cùng key + nonce khi encrypt** | Có thể phá cả tính bí mật và khả năng xác thực GCM. | Cấp nonce duy nhất theo key, giám sát quota, xoay khóa và thiết kế phối hợp nhiều process. |
| **Dùng nonce 12 byte toàn số 0 cho mọi message** | Mỗi lần với cùng key đều dùng lại nonce. | Sinh/điều phối nonce mới cho mỗi lần mã hóa; nonce không cần giữ bí mật. |
| **Tưởng nonce phải giấu** | Thiếu nonce khiến không giải mã được; giấu nonce không bù việc reuse. | Lưu/truyền nonce cùng ciphertext/tag, nhưng bảo đảm quy tắc duy nhất. |
| **Bỏ 16 byte tag khi lưu ciphertext** | Không thể xác minh bản mã đã được chấp nhận; giải mã sẽ lỗi. | Giữ ciphertext **và** tag nguyên vẹn theo định dạng. |
| **Tự so sánh tag bằng Java `==`/`equals`** | Xác thực GCM phải do primitive thực hiện với khóa và nonce; so sánh sai kiểu không có ý nghĩa. | Truyền nguyên ciphertext+tag vào `Cipher.doFinal` khi decrypt. |
| **Catch `AEADBadTagException` rồi sử dụng dữ liệu đã có** | Dữ liệu chưa xác thực có thể dẫn đến tác động nghiệp vụ. | Từ chối toàn bộ thông điệp, không commit tác dụng phụ từ plaintext chưa xác thực. |
| **Nhầm AAD thành ciphertext** | AAD không được mã hóa nên dữ liệu nhạy cảm có thể lộ. | Chỉ dùng AAD cho metadata cần xác thực và cho phép công khai; plaintext cần bí mật phải đưa vào encrypt. |
| **Đổi AAD giữa hai phía** | Tag không hợp lệ, ví dụ khác encoding, thứ tự trường hoặc tenant. | Quy định canonical byte AAD, dùng UTF-8, cùng schema/version. |
| **Gọi `updateAAD` sau khi xử lý input qua `update`** | Vi phạm trình tự API của AEAD. | `init` → `updateAAD` → `update/doFinal`. |
| **Sử dụng AES/GCM/NoPadding nhưng tin rằng “NoPadding = không có tag”** | “NoPadding” chỉ là không có padding plaintext; GCM vẫn tạo tag. | Luôn xác minh tag đúng độ dài. |
| **Dùng tag 128 bit lúc encrypt, khai báo 96 bit lúc decrypt** | Hai bên hiểu khác độ dài tag/định dạng; xác minh lỗi. | Cố định `GCMParameterSpec` theo giao thức. |
| **Dùng chung một `Cipher` instance cho các thread/request** | Trạng thái Cipher và IV/tag bị trộn hoặc race. | Tạo/init riêng cho từng thao tác, không chia sẻ mutable Cipher giữa luồng. |
| **Tưởng AES-GCM tự chống request phát lại** | Tag hợp lệ cho gói cũ vẫn hợp lệ. | Có protocol state chống replay, expiry/sequence/request ID được kiểm tra. |
| **Dùng key hard-code hoặc ghi key vào log** | Lộ khóa làm mất an toàn nhiều bản ghi. | KMS/secret manager, phân quyền, rotation, audit; không log secret. |
| **Không quản lý phiên bản và key ID** | Không giải mã được sau rotation, dễ nhập nhằng giao thức. | Record có schema version/key ID, metadata nhạy về ngữ cảnh được xác thực. |
| **Đổ toàn bộ lỗi xác thực vào response chi tiết** | Tạo kênh rò rỉ về trạng thái khóa/dữ liệu hoặc hữu ích cho attacker. | Trả lỗi tổng quát an toàn; nội bộ log có kiểm soát và không chứa secrets. |

**Về xử lý lỗi:** `IllegalArgumentException` cho packet quá ngắn trong demo là kiểm tra **định dạng đầu vào**, khác với `AEADBadTagException` khi tag sai. Hệ thống thực tế nên chuẩn hóa cả hai thành lỗi yêu cầu không hợp lệ ở ranh giới không tin cậy, đồng thời giữ telemetry nội bộ phù hợp mà không lộ dữ liệu nhạy cảm.

## 12. Câu hỏi tự kiểm tra (có đáp án)

**Câu 1.** Một người chặn mạng nhìn thấy nonce 12 byte, ciphertext và tag 16 byte. Họ có thể giải mã ngay chỉ vì nonce không bí mật không?

**Đáp án:** Không. Nonce là tham số công khai; cần khóa AES phù hợp để giải mã và xác minh tag. Bảo mật phụ thuộc khóa bí mật, nonce đúng cách và tính an toàn của thuật toán.

**Câu 2.** Dữ liệu AAD trong AES-GCM có được giữ bí mật không? Nếu AAD đổi từ `tenant=blue` thành `tenant=red` nhưng ciphertext/tag giữ nguyên thì sao?

**Đáp án:** AAD **không được mã hóa**. Thay byte AAD khiến xác minh tag thất bại khi decrypt dưới cùng key/nonce/ciphertext/tag. Ứng dụng phải lấy tenant từ ngữ cảnh đáng tin và kiểm tra quyền riêng.

**Câu 3.** Với AES-GCM tag 128 bit, plaintext 23 byte và nonce 12 byte, packet `nonce || ciphertext || tag` dài bao nhiêu?

**Đáp án:** `12 + 23 + 16 = 51` byte. GCM là NoPadding nên ciphertext dài bằng plaintext trong định dạng ví dụ.

**Câu 4.** Một dịch vụ encrypt với cùng key và cùng nonce cho hai record khác nhau, nhưng mỗi record có AAD khác. Có an toàn không?

**Đáp án:** **Không.** AAD khác nhau không thay thế yêu cầu duy nhất của nonce dưới cùng khóa. Phải tránh mọi lần mã hóa lặp cặp key/nonce.

**Câu 5.** `decrypt` ném `AEADBadTagException`. Có nên trả phần plaintext có thể đã được xử lý trước khi exception xảy ra không?

**Đáp án:** Không. Bỏ toàn bộ dữ liệu chưa xác thực; không dùng plaintext trung gian trong tính toán nghiệp vụ, không commit transaction hay ghi ra nơi khác trước khi tag hợp lệ.

**Câu 6.** Một client gửi lại nguyên vẹn một gói thanh toán GCM hợp lệ mà server đã xử lý hôm qua. Tag có thất bại vì request cũ không?

**Đáp án:** Không. Nếu key/nonce/AAD/ciphertext/tag vẫn khớp, tag vẫn hợp lệ. Server cần quản lý chống replay bằng request ID/sequence và thời hạn hợp lệ.

**Câu 7.** Có nên gửi thêm `SHA-256(ciphertext)` cùng AES-GCM tag để xác minh dữ liệu chưa bị sửa?

**Đáp án:** Không cần cho mục tiêu này. SHA-256 không khóa không chống người thay ciphertext rồi băm lại. Tag AES-GCM đã là cơ chế xác thực tích hợp; hãy ưu tiên dùng đúng primitive và protocol chuẩn.

**Câu 8.** Khi hệ thống có 20 worker cùng mã hóa với một khóa AES, mỗi worker dùng counter nonce bắt đầu từ 0. Có ổn không?

**Đáp án:** Không, vì các worker có thể tạo cùng nonce dưới một khóa. Cần cơ chế phân vùng nonce/điều phối counter/định danh instance đảm bảo không trùng kể cả restart, hoặc chiến lược sinh nonce đã được đánh giá và quota/key rotation phù hợp.

## 13. Checklist áp dụng AES-GCM trong Java

- **Đúng primitive:** `AES/GCM/NoPadding` qua `Cipher` chuẩn; 128-bit authentication tag bằng `GCMParameterSpec`, không thay tag bằng checksum/hash.
- **Đúng secret:** Khóa AES có entropy và quyền truy cập phù hợp, không hard-code; có quản lý vòng đời/rotation.
- **Đúng nonce:** 12 byte theo định dạng phổ biến; **duy nhất mỗi lần encrypt dưới cùng key**; phân phối trên toàn hệ thống.
- **Đúng AAD:** Chỉ metadata công khai cần xác thực; xác định byte UTF-8/canonical, gắn ngữ cảnh đã tin cậy, gọi `updateAAD` trước dữ liệu.
- **Đúng wire format:** Giữ nonce, ciphertext, tag và mọi thông tin version/key ID cần thiết; tránh nhầm Base64 với bảo mật.
- **Đúng cách decrypt:** Đưa nguyên ciphertext+tag cho `doFinal`, chỉ dùng plaintext sau khi xác minh thành công; từ chối mọi lỗi tag.
- **Đúng giao thức:** Thêm chống replay, giới hạn kích thước, phân quyền/định danh, xử lý lỗi và theo dõi hoạt động; AEAD không thay tất cả các tầng bảo mật khác.
- **Đúng vận hành:** Nếu dữ liệu lớn, thiết kế chunk/record AEAD với thứ tự/index và điều kiện hoàn tất được xác thực; không xử lý plaintext streaming chưa kiểm tag.

**Kết luận:** **AEAD = mã hóa có xác thực dữ liệu liên kết**. Với `AES/GCM/NoPadding`, **ciphertext** bảo vệ bí mật của plaintext, **authentication tag** giúp phát hiện sửa đổi của ciphertext và AAD, **nonce** là đầu vào công khai nhưng phải duy nhất dưới cùng khóa, còn **AAD** ràng buộc metadata quan trọng mà không che giấu metadata đó. Trong Java, kiểm tra tag nằm trong `Cipher.doFinal` khi giải mã: **tag không hợp lệ thì không có dữ liệu đáng tin để xử lý**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-007">6.7 GCMParameterSpec, độ dài tag và yêu cầu không lặp IV dưới cùng một khóa</a>

<details>
<summary>Xem chi tiết</summary>

# 6.7 GCMParameterSpec, độ dài tag và yêu cầu không lặp IV dưới cùng một khóa

Trong Java, khi dùng `Cipher.getInstance("AES/GCM/NoPadding")`, ta cần nắm vững hai tham số mà `GCMParameterSpec` biểu diễn: **IV/nonce** dùng cho lần mã hóa và **độ dài authentication tag** dùng để kiểm tra tính toàn vẹn/xác thực của kết quả. Một lời gọi API đúng cú pháp vẫn có thể tạo ra hệ thống không an toàn nếu **tái sử dụng IV với cùng một khóa AES**.

Sau bài này, bạn có thể:
- Giải thích cơ chế AES-GCM và vai trò của IV/nonce, tag, AAD.
- Phân biệt **tag length tính bằng bit** với **IV length tính bằng byte** trong `GCMParameterSpec`.
- Chọn độ dài tag phù hợp, hiểu giới hạn của chuẩn NIST và provider Java.
- Sinh, lưu và khôi phục IV cho từng bản mã; duy trì quy tắc **không lặp cặp (key, IV)** ở quy mô nhiều lần mã hóa, nhiều tiến trình và nhiều node.
- Viết Java 17+ encrypt/decrypt với `GCMParameterSpec`, xử lý `AEADBadTagException` và đối chiếu kết quả với test vector.

## 1. GCM là gì và bốn dữ liệu cần phân biệt

**GCM (Galois/Counter Mode)** là chế độ mã hóa đối xứng có xác thực (**AEAD**, Authenticated Encryption with Associated Data). Kết quả gồm phần dữ liệu đã mã hóa (**ciphertext**) và **authentication tag**. Khi giải mã, tag được kiểm tra để phát hiện việc ciphertext, AAD hoặc thông số mật mã không khớp. Không chấp nhận plaintext nếu xác thực thất bại.

| Thành phần | Vai trò | Có cần giữ bí mật? | Khi giải mã |
| --- | --- | --- | --- |
| **AES key** (`SecretKey`) | Khóa bí mật được dùng để mã hóa và tính tag. | **Có.** | Phải sử dụng đúng khóa. Quản lý trong keystore/KMS hoặc hệ thống quản lý bí mật phù hợp. |
| **IV/nonce** | Xác định một lần mã hóa với khóa cụ thể; tham gia tính trạng thái GCM. | **Không** với AES-GCM thông thường. | Phải có **đúng IV** đã dùng khi mã hóa. |
| **Plaintext / ciphertext** | Dữ liệu cần giữ kín và bản mã. | Plaintext cần bảo vệ; ciphertext có thể lưu/truyền. | Chỉ dùng plaintext sau khi xác thực hoàn tất. |
| **Authentication tag** (`T`) | Giá trị kiểm tra tính toàn vẹn/xác thực, thường gắn ở cuối ciphertext. | Không phải khóa bí mật. | Thiếu hoặc tag sai phải khiến giải mã thất bại. |
| **AAD** (Additional Authenticated Data) | Metadata được **xác thực nhưng không mã hóa**. | Không mặc định. | Phải cung cấp đúng byte AAD đã dùng khi mã hóa. |

Các thuật ngữ IV và nonce cùng trỏ đến tham số trong trường hợp AES-GCM này: **không cần tạo hai mảng riêng** chỉ vì tài liệu dùng hai tên. IV mang tính không bí mật nhưng có yêu cầu **duy nhất với từng khóa**.

Sơ đồ một bản ghi:

~~~text
Khóa K (bí mật) ─────────────────────────┐
IV N (mới với K) ───────────────────────┤
AAD (metadata xác thực) ────────────────┤ AES-GCM encrypt
Plaintext P ────────────────────────────┘
                                           |
                                           v
                     Ciphertext C + authentication tag T

Lưu/truyền: {version, keyId, N, C, T, AAD/context}
Giải mã: lấy đúng K, N, AAD, C và T -> verify tag -> plaintext
~~~

**AAD không phải ciphertext**, nên không được kỳ vọng AAD được mã hóa. Ví dụ có thể đưa `recordType` hoặc `tenantId` vào AAD khi muốn phát hiện sửa metadata. Nếu hệ thống mã hóa nội dung cho tenant A nhưng bỏ qua định danh tenant trong AAD/context, bản mã có thể bị dùng nhầm ngữ cảnh.

## 2. `GCMParameterSpec` thực sự lưu những gì?

Lớp `javax.crypto.spec.GCMParameterSpec` thực hiện `AlgorithmParameterSpec` và chứa **hai giá trị**:

- `tLen`: độ dài **authentication tag**, đơn vị **bit**.
- `IV`: dãy byte của initialization vector, được lưu như mảng byte, độ dài do `iv.length` xác định.

Các API quan trọng:

~~~java
// IV dài 12 byte; tag dài 128 bit (16 byte):
byte[] iv = new byte[12]; // CHỈ là ví dụ về độ dài; không dùng IV toàn 0 để mã hóa thực!
GCMParameterSpec spec = new GCMParameterSpec(128, iv);

// Lấy thông số:
int tagBits = spec.getTLen();   // 128
byte[] ivCopy = spec.getIV();   // mảng mới, dài 12 byte
~~~

Đây là **mảnh minh họa API**, không phải chương trình mã hóa độc lập. Chương trình chạy đầy đủ sử dụng IV an toàn nằm ở mục 6.

Hai constructor chính:

| API | Ý nghĩa |
| --- | --- |
| `new GCMParameterSpec(int tLen, byte[] src)` | Sao chép toàn bộ `src` làm IV và lưu độ dài tag theo **bit**. |
| `new GCMParameterSpec(int tLen, byte[] src, int offset, int len)` | Sao chép `len` byte bắt đầu tại `offset` từ `src` để làm IV; không tự chọn IV ngẫu nhiên. |
| `spec.getTLen()` | Trả về độ dài tag, **bit**, không phải byte. |
| `spec.getIV()` | Trả về **một mảng mới** mỗi lần gọi; thay đổi mảng trả về không làm đổi IV đã lưu trong spec. |

Constructor sao chép mảng IV để tránh việc người gọi sửa byte nguồn rồi âm thầm làm thay đổi tham số. Nhưng `GCMParameterSpec` **không tự sinh IV**, **không quản lý lịch sử IV**, cũng **không bảo đảm IV không trùng dưới cùng khóa**. Trách nhiệm cấp IV an toàn thuộc về chương trình/giao thức.

**Đừng nhầm đơn vị:**

~~~text
int IV_BYTES = 12;       // 12 byte = 96 bit (độ dài IV phổ biến)
int TAG_BITS = 128;      // 128 bit = 16 byte (độ dài tag phổ biến)

new GCMParameterSpec(TAG_BITS, iv);   // ĐÚNG nếu iv dài 12 byte
new GCMParameterSpec(16, iv);         // SAI ý nghĩa: 16 bit, không phải 16 byte
new GCMParameterSpec(IV_BYTES, iv);   // SAI ý nghĩa: truyền 12 làm tag length
~~~

Java API chỉ kiểm tra một số tính hợp lệ cơ bản của tham số khi tạo `GCMParameterSpec`. **Provider thực thi `Cipher.init` mới quyết định giá trị tag/IV cụ thể có được hỗ trợ hay không**. Do đó không coi việc constructor chạy được là bằng chứng cấu hình GCM hợp lệ và an toàn.

## 3. Độ dài authentication tag: 128 bit nghĩa là gì?

GCM tạo authentication tag `T`, thường chọn **128 bit (16 byte)**. Tag cho phép bên giải mã phát hiện sửa bản mã, metadata AAD hoặc sử dụng khóa/IV không khớp, với xác suất giả mạo thành công phụ thuộc cả độ dài tag lẫn giới hạn sử dụng GCM.

Theo NIST SP 800-38D, các độ dài tag thông thường được định nghĩa là **128, 120, 112, 104 hoặc 96 bit**. **64 hoặc 32 bit** chỉ được phép trong một số ứng dụng có ràng buộc đặc biệt về độ dài dữ liệu, lượng thông điệp và vòng đời khóa; không chọn chúng cho hệ thống thông thường.

| `tLen` (bit) | Độ dài tag (byte) | Định hướng |
| --- | --- | --- |
| **128** | **16** | Lựa chọn thực hành ưu tiên trong ví dụ và phần lớn ứng dụng mới. |
| 120 | 15 | Theo chuẩn GCM, nhưng phải kiểm tra provider/giao thức và nhu cầu tương thích. |
| 112 | 14 | Tương tự; tag ngắn hơn cần phân tích giới hạn bảo mật. |
| 104 | 13 | Tương tự; không tự ý rút ngắn để tiết kiệm vài byte. |
| 96 | 12 | Theo chuẩn, bảo đảm xác thực thấp hơn tag 128 bit; dùng khi giao thức quy định và đánh giá đúng giới hạn. |
| 64 hoặc 32 | 8 hoặc 4 | **Trường hợp đặc biệt theo NIST**, kèm ràng buộc nghiêm ngặt; tránh dùng nếu không có thiết kế được duyệt. |

Chú ý:
1. `tLen` là thông số **tag**, không phải số bit của khóa AES. Khóa có thể là AES-128/192/256; `GCMParameterSpec(128, iv)` không quyết định độ dài key.
2. Tag 128 bit **không đồng nghĩa** IV 128 bit. Lựa chọn phổ biến là **IV 96 bit (12 byte)** + **tag 128 bit (16 byte)**.
3. **Tag ngắn hơn làm tăng khả năng giả mạo khi đối thủ thử nhiều lần**, đồng thời các giới hạn an toàn/khối lượng dữ liệu có thể chặt hơn. Không rút ngắn tag để cố vừa một payload nhỏ mà không thay giao thức.
4. NIST yêu cầu một độ dài tag cố định, được hỗ trợ, gắn với một khóa trong cấu hình GCM; không thay độ dài tag tùy tiện giữa các thông điệp cùng khóa.
5. Không phải provider Java nào cũng hỗ trợ mọi `tLen` chuẩn. Khi `Cipher.init` gặp thông số không được hỗ trợ có thể ném `InvalidAlgorithmParameterException`. Ví dụ bên dưới chủ ý dùng 128 bit để tránh khác biệt không cần thiết.
6. Không dùng giá trị `256` làm tag length với ý nghĩ “bảo mật gấp đôi”: GCM chuẩn không có tag 256 bit.

### Tag xuất hiện ở đâu trong Java?

Với `Cipher.getInstance("AES/GCM/NoPadding")` và `TAG_BITS=128`, Java `doFinal(plaintext)` tạo **ciphertext kèm tag**, về mặt biểu diễn có thể xem là:

~~~text
ciphertextAndTag = ciphertext || tag
len(ciphertextAndTag) = len(plaintext) + 16 byte

Ví dụ plaintext 8 byte:
  8 byte ciphertext + 16 byte tag = 24 byte ciphertextAndTag

Nếu envelope có IV 12 byte:
  IV(12) || ciphertextAndTag(24) = 36 byte
~~~

`NoPadding` là tên transformation đúng cho GCM, **không có nghĩa GCM bỏ authentication tag**. Tag nằm trong kết quả `doFinal`, không tự động nằm ở `GCMParameterSpec`. `GCMParameterSpec` chỉ chứa **tham số kỳ vọng** gồm tLen và IV, chứ không giữ tag đã sinh.

Nếu phải tương tác với thư viện/protocol tách trường ciphertext và tag riêng, hãy xác định rõ **độ dài tag bằng byte**, tách/ghép đúng thứ tự và dùng một định dạng được version hóa. Không nhầm 16 byte tag với 12 byte IV.

## 4. Quy tắc sống còn: không lặp IV dưới cùng khóa

**Bất biến của AES-GCM: với mỗi khóa K, mỗi lần mã hóa phải sử dụng một IV/nonce N chưa từng sử dụng với K.** Nói cách khác, cặp **(K, N)** phải là duy nhất trong toàn bộ vòng đời sử dụng khóa cho encryption.

### 4.1. Điều gì xảy ra nếu tái sử dụng?

GCM dùng cách mã hóa dựa trên counter cùng cơ chế tính authentication tag. Nếu cùng `K` và `N` được dùng cho hai plaintext khác nhau, phần keystream có thể lặp. Trên cùng vị trí byte, quan hệ có dạng:

~~~text
C1 = P1 XOR stream(K, N)
C2 = P2 XOR stream(K, N)

C1 XOR C2 = P1 XOR P2   (ở những phần cùng vị trí)
~~~

Kẻ tấn công có thể suy ra quan hệ giữa plaintext và khai thác điểm yếu xác thực; **reusing IV còn dẫn đến nguy cơ giả mạo tag**, không chỉ lộ thông tin. Đây không phải lỗi có thể “chữa” bằng việc tạo tag dài 128 bit, thêm AAD hoặc thay plaintext. Nếu cùng (K, N) đã dùng cho encryption thì tính an toàn thiết kế đã bị vi phạm.

**Phân biệt các trường hợp:**

| Trường hợp | Có hợp lệ theo quy tắc IV? |
| --- | --- |
| Cùng key, **IV mới** cho từng encrypt | Có thể hợp lệ, nếu thực sự bảo đảm không trùng và các điều kiện khác của GCM được đáp ứng. |
| Cùng key, cùng IV cho **hai lần encrypt** khác nhau | **Không hợp lệ**, kể cả plaintext/AAD khác. |
| Cùng key, cùng IV dùng để **decrypt bản mã vừa tạo** | **Bắt buộc**, vì decrypt phải dùng đúng IV của ciphertext; đây không phải encrypt thêm lần nữa. |
| Đổi sang key mới, IV trùng một giá trị của key cũ | Quy tắc duy nhất áp dụng theo cặp (key, IV); cần quản lý key đúng, không giả định một cấu hình đổi key đã thành công nếu chưa xác minh. |
| Hai node cùng dùng K và cùng chọn N | **Không hợp lệ**, kể cả node và JVM khác nhau. |
| Restart làm bộ đếm nonce quay về 0 trong khi dùng lại key | **Không hợp lệ**. |
| Tạo `new GCMParameterSpec(128, oldIV)` cho lần encrypt mới | Vẫn **không hợp lệ** nếu oldIV đã được dùng với cùng key; đối tượng spec mới không khiến IV mới. |

### 4.2. Tại sao IV 96 bit (12 byte) thường được chọn?

GCM xử lý IV **96 bit** theo đường đi đơn giản và hiệu quả: giá trị đầu của counter được tạo từ IV nối với hằng số cuối theo đặc tả. Khi IV dài khác 96 bit, GCM phải xử lý IV qua phép GHASH trước khi xây dựng trạng thái counter; một số provider có thể hỗ trợ nhưng không nên xem độ dài tùy ý là cấu hình mặc định.

96 bit ở đây là **độ dài IV**, không phải độ dài tag. Ví dụ dùng **nonce 12 byte và tag 16 byte** không có mâu thuẫn.

IV không cần bí mật. Có thể lưu công khai ngay trước ciphertext; nhưng phải dùng đúng IV khi giải mã, và **không được tái sử dụng cho một phép mã hóa mới dưới cùng khóa**.

### 4.3. Sinh IV ngẫu nhiên không phải bảo đảm tuyệt đối

Đối với mỗi lần encrypt có thể tạo 12 byte bằng `SecureRandom.nextBytes`:

~~~java
byte[] iv = new byte[12];
new java.security.SecureRandom().nextBytes(iv);
GCMParameterSpec spec = new GCMParameterSpec(128, iv);
~~~

Đây là **mảnh minh họa**; thực tế nên quản lý đối tượng CSPRNG và vòng đời khóa theo thiết kế. CSPRNG tạo byte khó dự đoán; với IV ngẫu nhiên, **xác suất trùng là nhỏ nhưng không bằng 0**. Với không gian 96 bit, xác suất đụng sau nhiều lần sinh tăng theo hiệu ứng birthday (xấp xỉ tăng theo bình phương số lần dùng). NIST quy định các giới hạn số lần invocation dưới cùng key và các cách xây dựng IV tương ứng.

Đối với hệ thống lưu lượng thấp với chính sách xoay khóa thích hợp, IV 96 bit ngẫu nhiên là một phương án thông dụng. Đối với hệ thống nhiều node, lưu lượng lớn hoặc cần **bảo đảm không trùng mạnh hơn xác suất**, nên dùng thiết kế cấp nonce có trạng thái/bộ đếm với cách phân vùng thích hợp.

Không tự chọn một ngưỡng “an toàn” vô hạn cho `SecureRandom`. Giới hạn thực tế cần dựa trên đặc tả GCM, mức chấp nhận rủi ro, kích thước dữ liệu, số bản mã, kiến trúc và chính sách quản lý khóa.

### 4.4. Bộ đếm nonce có thể dùng, nhưng phải quản lý toàn hệ thống

Một chiến lược khác là cấp mỗi encryption một **giá trị duy nhất** từ bộ đếm hoặc không gian nonce phân vùng:

~~~text
96-bit IV = prefix riêng của key/node || counter tăng một chiều

Node A -> prefixA || 000001, prefixA || 000002, ...
Node B -> prefixB || 000001, prefixB || 000002, ...
                   ^ prefixA khác prefixB và được cấp không trùng
~~~

Sơ đồ chỉ minh họa ý tưởng, **không quy định một cách chia bit cụ thể cho mọi hệ thống**. Muốn an toàn thực sự cần xác định:

- Ai cấp prefix/namespace và làm sao biết hai node không nhận cùng prefix.
- Làm sao lưu trạng thái counter **bền vững**, không lặp lại sau crash, restore backup, scale-out hoặc rollback deployment.
- Làm sao xử lý nhiều thread/request cùng lúc để không cấp một IV hai lần.
- Giới hạn tối đa của counter và việc thay key trước khi bộ đếm quay vòng.
- Vòng đời key, version/key ID và cơ chế rotate phối hợp cùng thuật toán sinh IV.
- Chính sách của chuẩn/giao thức: không tự phối trộn cơ chế IV random và deterministic với cùng key khi không được đặc tả/đánh giá.

**Không dùng timestamp trực tiếp làm IV vì nghĩ thời gian luôn tăng**: độ phân giải đồng hồ, điều chỉnh thời gian, nhiều node và restart có thể gây trùng.

### 4.5. `Cipher` không thay hệ thống quản lý uniqueness

Một số provider có thể phát hiện việc **cùng một đối tượng `Cipher`** được khởi tạo lại cho encryption bằng một cặp key/IV vừa sử dụng. Đây không phải cơ chế chống reuse trên toàn hệ thống. Một đối tượng `Cipher` mới, JVM mới hoặc node khác không có danh sách toàn cục của mọi IV mà khóa đã từng dùng. **Không dựa vào exception của provider như lớp bảo vệ cuối cùng.**

## 5. Chu trình đúng của một phép mã hóa/giải mã AES-GCM

### 5.1. Encryption

1. Lấy đúng khóa AES `K` (và quyền sử dụng khóa) từ hệ thống quản lý khóa.
2. Cấp IV **mới với K** theo chiến lược đã chọn; ví dụ 12 byte từ CSPRNG dưới mức tải được thiết kế.
3. Tạo `GCMParameterSpec(128, iv)`; gọi `Cipher.getInstance("AES/GCM/NoPadding")`.
4. `cipher.init(Cipher.ENCRYPT_MODE, key, spec)`.
5. Nếu có AAD, gọi `cipher.updateAAD(aad)` **trước** bất kỳ `update(...)` hoặc `doFinal(...)` nào xử lý plaintext.
6. Gọi `doFinal(plaintext)` nhận ciphertext và tag; lưu/truyền cùng IV, version, key ID, AAD/context được định nghĩa trong giao thức.

### 5.2. Decryption

1. Kiểm tra version, kích thước tối thiểu, kích thước IV và tag theo định dạng **đã thỏa thuận**; không để attacker chọn tag length tùy ý.
2. Lấy đúng khóa K bằng key ID/phiên bản tin cậy; đọc IV đã đi kèm ciphertext.
3. `cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv))`.
4. Cung cấp **chính xác cùng byte AAD** (nếu có) trước xử lý ciphertext.
5. `doFinal(ciphertextAndTag)`: nếu xác thực thất bại, xử lý `AEADBadTagException` và **từ chối dữ liệu**. Không dùng plaintext chưa xác thực.

**Không phải sinh IV mới lúc decrypt.** IV mới chỉ cần cho **lần encrypt mới** với cùng key; decrypt phải dùng IV đã lưu của bản mã ban đầu.

### 5.3. AAD và thứ tự gọi API

`updateAAD` dành cho metadata cần xác thực nhưng không giấu. Ví dụ encrypt dữ liệu thuộc record `"record:v1"`: phải truyền đúng byte `"record:v1"` khi decrypt, nếu truyền `"record:v2"` thì tag không hợp lệ. Trong Java JCE, mọi AAD của GCM phải được cung cấp **trước khi bắt đầu xử lý dữ liệu** bằng `update`/`doFinal`.

GCM xác thực dữ liệu **sau khi nhận đủ input và tag**. Nếu dùng chế độ streaming hoặc provider trả ra dữ liệu trước khi xác minh, ứng dụng không được thực thi, hiển thị hoặc phát tán plaintext chưa được xác thực; chỉ chấp nhận nó sau khi `doFinal` thành công.

## 6. Java 17+: chương trình encrypt/decrypt hoàn chỉnh

Ví dụ tạo khóa AES-128 chỉ trong bộ nhớ, sử dụng IV 12 byte mới và tag 128 bit, lưu envelope dạng `IV || ciphertext || tag`. Chương trình dùng `"xin chao"` (8 byte UTF-8) để kết quả về độ dài là cố định dù IV ngẫu nhiên mỗi lần.

**Tệp: `GcmRecordDemo.java`**

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

public class GcmRecordDemo {
    static final int IV_BYTES = 12;
    static final int TAG_BITS = 128;
    static final int TAG_BYTES = TAG_BITS / 8;
    static final SecureRandom RNG = new SecureRandom();

    static byte[] seal(SecretKey key, byte[] plaintext, byte[] aad)
            throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        RNG.nextBytes(iv);

        Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
        enc.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        enc.updateAAD(aad);
        byte[] ciphertextAndTag = enc.doFinal(plaintext);

        return ByteBuffer.allocate(iv.length + ciphertextAndTag.length)
                .put(iv).put(ciphertextAndTag).array();
    }

    static byte[] open(SecretKey key, byte[] envelope, byte[] aad)
            throws GeneralSecurityException {
        if (envelope.length < IV_BYTES + TAG_BYTES) {
            throw new IllegalArgumentException("Envelope too short");
        }

        byte[] iv = Arrays.copyOfRange(envelope, 0, IV_BYTES);
        byte[] ciphertextAndTag = Arrays.copyOfRange(
                envelope, IV_BYTES, envelope.length);

        Cipher dec = Cipher.getInstance("AES/GCM/NoPadding");
        dec.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        dec.updateAAD(aad);
        return dec.doFinal(ciphertextAndTag);
    }

    public static void main(String[] args) throws GeneralSecurityException {
        KeyGenerator keys = KeyGenerator.getInstance("AES");
        keys.init(128);
        SecretKey key = keys.generateKey();

        byte[] message = "xin chao".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "record:v1".getBytes(StandardCharsets.UTF_8);
        byte[] envelope = seal(key, message, aad);

        System.out.println("Tag bits: " + TAG_BITS);
        System.out.println("IV bytes: " + IV_BYTES);
        System.out.println("Envelope bytes: " + envelope.length);
        System.out.println("Recovered: " + new String(
                open(key, envelope, aad), StandardCharsets.UTF_8));

        byte[] tampered = envelope.clone();
        tampered[tampered.length - 1] ^= 1;
        try {
            open(key, tampered, aad);
            throw new AssertionError("Tampered record was accepted");
        } catch (AEADBadTagException expected) {
            System.out.println("Tampered record rejected: true");
        }

        try {
            open(key, envelope, "record:v2".getBytes(StandardCharsets.UTF_8));
            throw new AssertionError("Wrong AAD was accepted");
        } catch (AEADBadTagException expected) {
            System.out.println("Wrong AAD rejected: true");
        }
    }
}
~~~

Chạy bằng Java 17+ ở thư mục chứa tệp:

~~~bash
javac GcmRecordDemo.java
java GcmRecordDemo
~~~

Stdout kiểm chứng (không in IV ngẫu nhiên):

~~~text
Tag bits: 128
IV bytes: 12
Envelope bytes: 36
Recovered: xin chao
Tampered record rejected: true
Wrong AAD rejected: true
~~~

**Giải thích từng phần:**

- `keys.init(128)` là độ dài khóa AES **128 bit**; `TAG_BITS=128` là độ dài **tag 128 bit**; `IV_BYTES=12` là **IV 96 bit**. Ba tham số mang ý nghĩa khác nhau.
- `seal` luôn sinh IV mới trước một lần encrypt. Không dùng một biến IV toàn cục cố định, không dùng `new byte[12]` làm IV thực.
- `enc.updateAAD(aad)` xác thực metadata không mã hóa. `enc.doFinal(message)` trả **8 byte ciphertext + 16 byte tag = 24 byte**. Envelope thêm **12 byte IV**, tổng **36 byte**.
- `open` lấy IV từ envelope; không sinh IV mới. Đồng thời nó kiểm tra độ dài tối thiểu 12 + 16 byte trước khi cắt mảng.
- Đảo một bit ở cuối envelope (trong tag) dẫn đến `AEADBadTagException`; AAD khác cũng bị từ chối. Đó là hành vi mong muốn; không xử lý ngoại lệ bằng cách trả về dữ liệu chưa xác thực.
- Đây là **ví dụ API trên một process**, chưa có giới hạn lượng bản mã, rotation, key ID hay phối hợp nonce xuyên node. Không triển khai nguyên xi như dịch vụ mã hóa nhiều node ở quy mô lớn.

Mỗi lời gọi `seal` tạo một `Cipher` mới. `Cipher` là đối tượng có trạng thái, không nên chia sẻ giữa các luồng để encrypt/decrypt cùng lúc. `SecureRandom` có thể được quản lý như một nguồn CSPRNG dùng chung theo hợp đồng API; sự phù hợp về an toàn nonce ở quy mô hệ thống vẫn phải do ứng dụng bảo đảm.

## 7. Java 17+: kiểm tra tag, IV copy và test vector cố định

Ví dụ thứ hai **chỉ phục vụ kiểm thử** dùng khóa AES-128 toàn byte 0, IV 96 bit toàn byte 0 và plaintext 16 byte toàn byte 0. Đây là **known-answer test vector** được dùng trong tài liệu/kiểm thử AES-GCM; **không dùng key/IV cố định này để encrypt dữ liệu thật**.

Bài test chứng minh:
- `getTLen()` trả **bit**, còn IV lấy ra là **mảng byte**.
- Constructor và `getIV()` sao chép IV để tránh thay đổi ngầm.
- Với plaintext 16 byte, `doFinal` trả 16 byte ciphertext + 16 byte tag = 32 byte.
- Ciphertext và tag đúng giá trị chuẩn dự kiến.

**Tệp: `GcmKnownAnswerDemo.java`**

~~~java
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class GcmKnownAnswerDemo {
    public static void main(String[] args) throws GeneralSecurityException {
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        byte[] plaintext = new byte[16];

        GCMParameterSpec params = new GCMParameterSpec(128, iv);
        iv[0] = 1; // Mảng nguồn đổi, IV trong params không đổi.
        byte[] ivCopy = params.getIV();
        ivCopy[1] = 1; // Mảng trả về đổi, params vẫn giữ IV ban đầu.

        System.out.println("IV is copied: "
                + Arrays.equals(params.getIV(), new byte[12]));
        System.out.println("Tag bits: " + params.getTLen());
        System.out.println("Tag bytes: " + params.getTLen() / 8);

        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
        encrypt.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), params);
        byte[] ciphertextAndTag = encrypt.doFinal(plaintext);

        String expected = "0388dace60b6a392f328c2b971b2fe78"
                        + "ab6e47d42cec13bdf53a67b21257bddf";
        System.out.println("Ciphertext+tag bytes: " + ciphertextAndTag.length);
        System.out.println("Known vector matches: " + expected.equals(
                HexFormat.of().formatHex(ciphertextAndTag)));

        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
        decrypt.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"), params);
        System.out.println("Recovered zero block: "
                + Arrays.equals(plaintext, decrypt.doFinal(ciphertextAndTag)));
    }
}
~~~

~~~bash
javac GcmKnownAnswerDemo.java
java GcmKnownAnswerDemo
~~~

Stdout:

~~~text
IV is copied: true
Tag bits: 128
Tag bytes: 16
Ciphertext+tag bytes: 32
Known vector matches: true
Recovered zero block: true
~~~

Chú ý không sao chép ý tưởng `new byte[12]` làm IV cố định từ **test vector** sang production. Chương trình trên **chỉ mã hóa một lần với một key/IV kiểm thử**, sau đó decrypt cùng ciphertext bằng đúng thông số.

## 8. Thiết kế định dạng bản mã và quản lý khóa/IV

Một hệ thống thực thường cần **envelope có version** để xác định các tham số được hỗ trợ. Ví dụ định dạng logic:

~~~text
Record {
  version: 1,
  algorithm: "AES/GCM/NoPadding",    // cố định theo version
  keyId: "key-2026-10",             // tra đúng khóa trong KMS
  tagBits: 128,                    // cố định/được kiểm tra theo version
  iv: 12-byte nonce,
  aadContext: metadata đã ràng buộc với record,
  ciphertextAndTag: ciphertext || 16-byte authentication tag
}
~~~

Đây là mô hình thiết kế, **không phải JSON chứa key**; trường `keyId` chỉ là định danh. Nếu encode byte bằng Base64URL/hex, phải decode/validate về đúng số byte. Không tin cậy giá trị do người gửi tự khai như `tagBits=32` để lách chính sách; thông số hợp lệ phải xuất phát từ định dạng/giao thức đã chọn. Giá trị `keyId` và metadata cần được ràng buộc/xác thực phù hợp, ví dụ nằm trong AAD của record.

Nếu dùng `Cipher.init(Cipher.ENCRYPT_MODE, key)` mà **để provider tự tạo IV**, vẫn phải lấy IV sau init (ví dụ `cipher.getIV()` hoặc `cipher.getParameters().getParameterSpec(GCMParameterSpec.class)` nếu được hỗ trợ), xác minh độ dài/tag theo giao thức và lưu lại cùng kết quả. Không giả định decrypt tự suy ra IV từ ciphertext hoặc Java tự lưu nonce trên disk.

Các hệ thống thực tế nên có:

1. **Key ID và rotation:** biết ciphertext được mã hóa bằng khóa nào; quản lý khóa trước và sau rotation.
2. **Quy tắc cấp IV/nonce:** cấp phát mới, không lặp trong phạm vi key, bảo vệ trước restart/rollback/multi-node.
3. **Giới hạn sử dụng mỗi khóa:** lượng encryption, kích thước dữ liệu, độ dài tag, thuật toán và giới hạn của NIST/protocol.
4. **Metadata và AAD:** các trường liên quan đến ngữ cảnh (tenant, loại bản ghi, version, quyền truy cập) được xác thực nhất quán.
5. **Kiểm tra đầu vào:** giới hạn kích thước trước khi cấp bộ nhớ, validate IV/tag và phiên bản; tránh dữ liệu hỏng làm cạn bộ nhớ.
6. **Xử lý lỗi:** log thông tin an toàn về bản ghi/loại lỗi; không log khóa, plaintext nhạy cảm hay ghi nhận ciphertext giả mạo là đã giải mã.
7. **Chống rollback/replay theo nghiệp vụ:** AEAD xác thực bản mã nhưng **không tự ngăn việc phát lại một bản mã cũ vẫn hợp lệ**. Nếu cần freshness/thứ tự phiên bản, phải có state/version/sequence kiểm chứng ở tầng ứng dụng.

## 9. Những sai lầm thường gặp

| Sai lầm | Hậu quả / nguyên nhân | Cách đúng |
| --- | --- | --- |
| `new GCMParameterSpec(16, iv)` vì muốn tag 16 byte | `tLen` tính bằng **bit**; truyền 16 có thể bị provider từ chối và sai bảo mật. | `new GCMParameterSpec(128, iv)`. |
| Cho rằng IV 12 byte tức tag 12 byte | Hai tham số độc lập. | IV 12 byte, tag 16 byte là cấu hình thông dụng. |
| `new GCMParameterSpec(128, constantIV)` cho mọi encrypt | Tái sử dụng (K, IV) khi dùng lại key. | Cấp IV mới thực sự cho **mỗi encryption**. |
| Dùng cùng key/IV nhưng AAD hoặc plaintext khác | Vẫn vi phạm uniqueness; tag mới không cứu thiết kế. | Cặp (key, IV) không bao giờ lặp cho encryption. |
| Sinh random 96 bit rồi cho rằng “không thể trùng” | Random có xác suất va chạm, tăng theo lượng bản mã. | Giới hạn invocation/rotation hoặc cấu trúc cấp IV bảo đảm uniqueness. |
| Bộ đếm reset về 0 khi service restart | Có thể phát sinh cặp (key, IV) cũ. | Counter bền vững, dự phòng rollback, prefix/node được cấp không trùng. |
| Tự cho rằng provider phát hiện mọi nonce reuse | Provider không có registry toàn hệ thống. | Bảo đảm ở tầng giao thức/ứng dụng. |
| Không lưu IV rồi tạo IV mới khi decrypt | Không xác thực/giải mã được ciphertext. | Lưu IV công khai cùng ciphertext; dùng đúng IV cũ khi decrypt. |
| `updateAAD` sau `update`/`doFinal` | Trái thứ tự GCM; có thể ném `IllegalStateException`. | AAD **trước** dữ liệu ciphertext/plaintext. |
| Bỏ tag hoặc tách nhầm tag khỏi ciphertext | Xác thực thất bại hoặc sai định dạng. | Cấu hình tLen cố định; truyền đúng ciphertext + tag. |
| Bắt `AEADBadTagException` rồi trả plaintext “thử được” | Chấp nhận dữ liệu chưa được xác thực. | Từ chối bản mã và báo lỗi có kiểm soát. |
| Nhìn thấy `GCMParameterSpec` được tạo là nghĩ cấu hình được hỗ trợ | Constructor không kiểm tra đầy đủ mọi hạn chế provider. | Kiểm tra tại `Cipher.init` và chạy thử trên provider môi trường đích. |
| Giảm tag xuống 32/64 bit để tiết kiệm băng thông | Giảm an toàn, cần ràng buộc đặc biệt theo NIST. | Ưu tiên tag 128 bit khi không có yêu cầu đã đánh giá riêng. |
| Dùng `Cipher.getInstance("AES")` không chỉ định mode | Mode/padding phụ thuộc provider, không nhất thiết là GCM. | Dùng `"AES/GCM/NoPadding"` tường minh. |
| Coi IV bí mật hơn khóa hoặc log plaintext/key | Sai ưu tiên bảo vệ. | Giữ kín khóa và plaintext; IV thường công khai nhưng phải đúng và unique. |
| Dùng cùng `Cipher` từ nhiều thread | `Cipher` có trạng thái và không được xem là thread-safe. | Tạo instance riêng cho mỗi thao tác. |
| Chỉ có GCM rồi tưởng đã chống replay | Ciphertext hợp lệ cũ có thể được phát lại nguyên vẹn. | Thêm sequence/version, freshness và policy cấp ứng dụng khi cần. |

## 10. Tình huống chẩn đoán

**Tình huống A: `InvalidAlgorithmParameterException` khi `init`.** Trước tiên kiểm tra `spec.getTLen()` là 128 **bit**, `spec.getIV().length` là 12 **byte** và provider có hỗ trợ thông số không. Phân biệt lỗi này với `AEADBadTagException` khi xác thực decrypt.

**Tình huống B: Encrypt chạy tốt, decrypt luôn `AEADBadTagException`.** Kiểm tra cùng khóa đúng version, IV đúng 12 byte gốc, đúng cách ghép ciphertext||tag, AAD byte-for-byte giống nhau, cùng tag length. Không thử “bỏ kiểm tra tag” để làm cho decrypt hoạt động.

**Tình huống C: Mỗi service đều tạo `SecureRandom` và mỗi request sinh 12 byte IV.** Cách này có xác suất va chạm rất thấp ở tải phù hợp, nhưng không cho bằng chứng tuyệt đối về uniqueness xuyên service; phải đánh giá số lần encrypt trên cùng key và chính sách xoay khóa.

**Tình huống D: Hai node dùng cùng key và cùng counter tăng từ 0.** Đây là **lỗi chắc chắn**: khi cả hai cùng cấp nonce 0, 1, 2... sẽ tái sử dụng (K, IV). Cần phân vùng nonce và cấp prefix/namespace không trùng hoặc sử dụng dịch vụ điều phối counter.

**Tình huống E: Tag 128 bit nhưng người vận hành đổi tLen xuống 96 để giảm kích thước record.** Giảm 4 byte trên mỗi bản mã không phải thay đổi định dạng vô hại; phải có protocol/version mới, thẩm định giới hạn và kiểm chứng provider. Với cùng khóa, không thay tLen tùy ý.

**Tình huống F: Đổi IV của record lưu trữ và thử decrypt.** GCM không nhận sai IV; xác thực thất bại với xác suất áp đảo. Tình huống này khác với **encrypt lại cùng key + old IV** vốn đã vi phạm giới hạn sử dụng ngay khi thực hiện.

## 11. Câu hỏi tự kiểm tra (kèm đáp án)

**Câu 1.** `new GCMParameterSpec(128, new byte[12])` có nghĩa IV dài 128 bit hay tag dài 128 bit?

**Đáp án:** Tag dài 128 bit (**16 byte**); IV dài 12 byte (**96 bit**). Mảng IV toàn 0 ở ví dụ câu hỏi không được tái sử dụng trong encryption thực.

**Câu 2.** Dùng lại cùng (key, IV) để decrypt chính bản mã đã lưu có vi phạm không?

**Đáp án:** Không. **Decrypt phải dùng đúng key và IV đã encrypt**. Quy tắc không lặp áp dụng cho **các lần encryption mới** dưới cùng key.

**Câu 3.** Có thể sinh nonce bằng bộ đếm 64-bit không?

**Đáp án:** Có thể trong một thiết kế tạo IV 96 bit theo giao thức phù hợp, nếu kết hợp prefix/namespace và quản lý bộ đếm để toàn bộ IV không trùng với cùng khóa, kể cả multi-node/restart. Không lấy con số 64 bit rồi truyền vào mọi API như IV hợp lệ mặc định.

**Câu 4.** Tag 128 bit có ngăn tác hại tái sử dụng IV cùng key không?

**Đáp án:** Không. Reuse khiến GCM có nguy cơ mất cả bí mật và xác thực; tag dài không sửa được lỗi nonce reuse.

**Câu 5.** Sau khi mã hóa GCM, byte `cipher.doFinal` trả ra đã bao gồm tag chưa?

**Đáp án:** Có. `Cipher` AEAD của Java gắn tag vào ciphertext khi encrypt, và xác minh tag khi decrypt. Nếu envelope còn có IV thì phải tự ghép/lưu IV như giao thức quy định.

**Câu 6.** Nếu AAD không cần mã hóa, có thể bỏ qua nó khi decrypt không?

**Đáp án:** Không nếu AAD đã được dùng khi encrypt. Decrypt phải cung cấp đúng AAD byte-for-byte để tag khớp.

**Câu 7.** Constructor `GCMParameterSpec(32, iv)` chạy được có nghĩa tag 32 bit dùng an toàn không?

**Đáp án:** Không. Constructor không đủ để đánh giá chuẩn, provider và chính sách bảo mật. Tag 32 bit chỉ có phạm vi ứng dụng đặc biệt với ràng buộc nghiêm ngặt của NIST và không phù hợp làm cấu hình chung.

**Câu 8.** Nếu một record dùng key K1, IV N; record khác dùng key K2, IV N thì có chắc chắn lặp nonce dưới cùng key không?

**Đáp án:** Không, nếu K1 và K2 thực sự là hai khóa AES khác nhau. Ràng buộc uniqueness là trên cặp (K, IV). Tuy nhiên việc xác định khóa và rotation phải đáng tin cậy.

## 12. Tài liệu tham khảo

- [Java SE 17 — GCMParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): `tLen` theo bit, các constructor, sao chép IV và `getIV()`.
- [Java SE 17 — Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): AEAD, thứ tự `updateAAD`, tag kèm ciphertext, yêu cầu không lặp IV.
- [Java SE 17 — SecureRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html): tạo byte ngẫu nhiên thích hợp cho bảo mật.
- [NIST SP 800-38D — GCM and GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final): độ dài tag hợp lệ, xây dựng IV 96 bit và các giới hạn sử dụng GCM.
- [RFC 5116 — An Interface and Algorithms for Authenticated Encryption](https://www.rfc-editor.org/rfc/rfc5116): AEAD interface và cách nhìn tổng thể về nonce, plaintext, ciphertext và AAD.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-008">6.8 Truyền AAD trước dữ liệu; phân biệt dữ liệu được xác thực với dữ liệu được mã hóa</a>

<details>
<summary>Xem chi tiết</summary>

# 6.8. Truyền AAD trước dữ liệu; phân biệt dữ liệu được xác thực với dữ liệu được mã hóa

## 1. Mục tiêu: hiểu đúng hai lớp bảo vệ trong AEAD

Khi mã hóa đối xứng, “người ngoài không đọc được nội dung” và “người ngoài không sửa được nội dung mà không bị phát hiện” là **hai yêu cầu khác nhau**:

- **Confidentiality (bí mật):** kẻ không có khóa khó khôi phục plaintext từ ciphertext.
- **Integrity/authenticity (toàn vẹn và xác thực):** bên nhận có thể phát hiện dữ liệu hoặc ngữ cảnh đã bị sửa mà không có quyền tạo authentication tag hợp lệ.

AEAD (*Authenticated Encryption with Associated Data*) phối hợp hai yêu cầu: nó **mã hóa và xác thực plaintext**, đồng thời cho phép xác thực thêm một vùng dữ liệu **không mã hóa** gọi là *Additional Authenticated Data* (AAD, đôi khi viết AD). Trong Java, một cách phổ biến là dùng `Cipher` với transformation `AES/GCM/NoPadding`.

Ví dụ thực tế: hệ thống gửi một hóa đơn với:

- Header: `version=1;tenant=shop-7` cần đọc được để định tuyến/hiểu định dạng, nhưng **không được lén đổi**.
- Payload: `hoa-don:120000 VND` cần được giữ **bí mật** và không được thay đổi.
- Nonce/IV: giá trị không bí mật, bên giải mã cần dùng đúng giá trị mã hóa đã sử dụng.
- Authentication tag: bằng chứng mật mã ràng buộc ciphertext và AAD với khóa/nonce/thuật toán.

Đưa **header** vào AAD và **payload** vào `Cipher.doFinal` cho AEAD sẽ tạo hai mức xử lý khác nhau: header còn nguyên dạng để truyền, nhưng sửa header khiến xác thực thất bại; payload được mã hóa, đồng thời được xác thực.

**Mục tiêu của bài:** biết byte nào được mã hóa, byte nào chỉ được xác thực; gọi `Cipher.updateAAD` đúng thứ tự; kiểm tra lỗi tag; thiết kế định dạng truyền dữ liệu đủ thông tin và an toàn.

## 2. AAD khác plaintext, ciphertext, IV và tag thế nào?

| Thành phần | Có bị mã hóa bởi AES-GCM? | Có được ràng buộc bởi xác thực AEAD? | Vai trò |
| --- | --- | --- | --- |
| **Plaintext** đi qua `update`/`doFinal` ở chế độ mã hóa | **Có**: trở thành ciphertext | **Có**: bên nhận chỉ chấp nhận plaintext sau kiểm tra tag | Dữ liệu cần bí mật và toàn vẹn |
| **AAD** đi qua `updateAAD` | **Không**: vẫn giữ nguyên byte công khai | **Có**: thay AAD sẽ làm tag không khớp | Header, loại bản ghi, tenant, version, định danh ngữ cảnh |
| **Ciphertext** | Đã là dữ liệu mã hóa | **Có** | Kết quả mã hóa payload; sửa byte sẽ làm xác thực thất bại |
| **IV/nonce** của GCM | **Không**; thường được lưu/truyền công khai | Phải khớp với phép mã hóa và ảnh hưởng đến quá trình tính tag | Bảo đảm uniqueness mỗi lần mã hóa dưới cùng khóa; cần đúng nonce để giải mã |
| **Authentication tag** | Không phải plaintext bí mật; thường gắn sau ciphertext trong kết quả `doFinal` | Là giá trị được kiểm tra để quyết định xác thực | Phát hiện sửa đổi dữ liệu/tag/ngữ cảnh |
| Dữ liệu **không** đưa vào AAD, cũng **không** mã hóa | Không | **Không**, trừ khi có cơ chế xác thực riêng | Không được tin là đã được AEAD bảo vệ |

Một điểm thường bị hiểu nhầm: **AAD là authenticated, nhưng không confidential**. Nếu header chứa tên khách hàng, email hay thông tin nhạy cảm, đưa header đó vào AAD **không che giấu** các thông tin này. Muốn bí mật, đặt dữ liệu trong plaintext để mã hóa (và xác thực), hoặc bảo vệ riêng bằng cơ chế thích hợp.

Tương tự, “dữ liệu đã mã hóa” chưa tự chứng minh an toàn nếu chỉ dùng một mode không xác thực như AES-CBC đơn lẻ. Ở đây AES-GCM vừa mã hóa vừa tạo tag; bên giải mã phải **kiểm tra tag thành công** trước khi sử dụng kết quả.

### Minh họa về phạm vi byte

```text
Bản ghi truyền/lưu trữ (ví dụ)
+---------------------------+----------------------+---------------------+
| header (AAD, rõ)          | nonce/IV (rõ)        | ciphertext || tag   |
| version=1;tenant=shop-7   | 12 byte              | byte đã mã hóa + tag |
+---------------------------+----------------------+---------------------+
           |                          |                       |
           +----------- đưa vào ------|------> Cipher GCM <----+
                            updateAAD(header)
                                      init(key, nonce)
                                      doFinal(payload)

Khi giải mã, cùng key + nonce + AAD + ciphertext/tag:
    tag hợp lệ --> nhận plaintext
    tag sai    --> từ chối toàn bộ bản ghi
```

Sơ đồ chỉ mô tả **ý nghĩa** các trường; trong ứng dụng thật phải có cấu trúc nhị phân hoặc JSON có schema/version/độ dài byte rõ ràng. Không trông chờ `Cipher` tự biết header nào là AAD: ứng dụng phải **chủ động** gọi `updateAAD` với đúng byte header.

## 3. Thứ tự gọi API bắt buộc trong Java

Với GCM/CCM, tài liệu `javax.crypto.Cipher` quy định **toàn bộ AAD phải được cung cấp trước khi bắt đầu xử lý dữ liệu** bằng `update` hoặc `doFinal`. Điều này áp dụng cho **cả mã hóa lẫn giải mã**.

Thứ tự đúng:

1. `Cipher.getInstance("AES/GCM/NoPadding")` để lấy engine.
2. `cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv))` để chọn khóa và tham số.
3. `cipher.updateAAD(headerBytes)` để nạp dữ liệu chỉ cần xác thực. Có thể gọi `updateAAD` nhiều lần, nhưng mọi lần đều phải diễn ra **trước** thao tác trên plaintext/ciphertext.
4. `cipher.update(payloadChunk)` không bắt buộc nếu xử lý theo từng khối; hoặc đi thẳng tới `cipher.doFinal(plaintext)` nếu dữ liệu đủ nhỏ.
5. `cipher.doFinal(...)` kết thúc phép mã hóa và trả ciphertext kèm tag xác thực.

Đối với giải mã, thay `ENCRYPT_MODE` bằng `DECRYPT_MODE` và cung cấp **chính xác cùng AAD** trước khi xử lý ciphertext. `doFinal(ciphertextAndTag)` sẽ kiểm tra tag; nếu tag không khớp, API có thể ném `AEADBadTagException`. Không sử dụng hoặc công bố plaintext khi chưa xác minh thành công.

```java
// Trích đoạn minh họa, giả sử key, iv, aad, plaintext đã tồn tại.
Cipher encryptor = Cipher.getInstance("AES/GCM/NoPadding");
encryptor.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
encryptor.updateAAD(aad);                   // 1. Xác thực header
byte[] encrypted = encryptor.doFinal(plaintext); // 2. Mã hóa payload + tag
```

**Sai thứ tự** là gọi `update(plaintextChunk)` trước rồi mới gọi `updateAAD(aad)`. Với GCM/CCM, `Cipher.updateAAD` sau khi dữ liệu đã được xử lý là sai trạng thái và có thể ném `IllegalStateException`; không coi lỗi này là “provider tùy ý”, vì contract Java đã yêu cầu AAD đi trước. Cũng không được xử lý dữ liệu bằng `doFinal` rồi mới gửi AAD: lúc đó phép toán đã kết thúc.

Lý do của thứ tự này: các mode AEAD xử lý tính xác thực AAD trước khi bắt đầu phần tính xác thực ciphertext. Provider cần nhận đủ AAD để triển khai đúng và tránh phải giữ lại toàn bộ ciphertext trong bộ đệm chỉ vì AAD đến muộn.

### Có thể gọi updateAAD nhiều lần không?

Có, khi các byte thuộc cùng một thông điệp GCM được truyền theo từng mảnh **trước payload**. Ví dụ:

```java
cipher.updateAAD(versionBytes);
cipher.updateAAD(tenantBytes);
cipher.updateAAD(headerBytes);
byte[] output = cipher.doFinal(plaintext);
```

Đây là **đoạn mã minh họa** trong ngữ cảnh đã khởi tạo `cipher` và xác định rõ các mảng. Hãy nhớ GCM xác thực **chuỗi byte AAD theo đúng thứ tự**. Nếu bên gửi ghép `versionBytes||tenantBytes||headerBytes` mà bên nhận dùng một thứ tự khác, quá trình xác thực sẽ thất bại.

Đặc biệt, với các trường có độ dài biến đổi cần **định nghĩa serialization không mơ hồ**: chẳng hạn mỗi trường có tag loại và độ dài cố định/tiền tố độ dài. Chuỗi `"ab" + "c"` và `"a" + "bc"` tạo cùng byte nếu chỉ nối trần; nếu ý nghĩa các trường khác nhau, ứng dụng phải mã hóa ranh giới trường vào AAD.

## 4. Ví dụ Java 17+ chạy độc lập: header rõ nhưng phải xác thực

Chương trình tạo một khóa AES dùng riêng cho demo, sinh nonce ngẫu nhiên mới dài 12 byte, đưa header vào AAD, mã hóa payload bằng AES-GCM và giải mã đúng. Sau đó chương trình lần lượt sửa **AAD**, **ciphertext** và **IV** để xác nhận bản ghi bị từ chối.

Tạo file `AadGcmDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class AadGcmDemo {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;

    static byte[] encrypt(SecretKey key, byte[] iv, byte[] aad,
            byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(aad); // Must precede update/doFinal on payload.
        return cipher.doFinal(plaintext);
    }

    static byte[] decrypt(SecretKey key, byte[] iv, byte[] aad,
            byte[] ciphertextAndTag) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(aad); // Supply exactly the same AAD bytes.
        return cipher.doFinal(ciphertextAndTag); // Checks authentication tag.
    }

    static boolean accepted(SecretKey key, byte[] iv, byte[] aad,
            byte[] ciphertextAndTag) throws Exception {
        try {
            decrypt(key, iv, aad, ciphertextAndTag);
            return true;
        } catch (AEADBadTagException rejected) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey key = generator.generateKey();

        byte[] iv = new byte[12]; // Fresh IV for this encryption.
        new SecureRandom().nextBytes(iv);

        byte[] aad = "version=1;tenant=shop-7"
                .getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "hoa-don:120000 VND"
                .getBytes(StandardCharsets.UTF_8);

        byte[] ciphertextAndTag = encrypt(key, iv, aad, plaintext);
        byte[] recovered = decrypt(key, iv, aad, ciphertextAndTag);

        System.out.println("AAD visible: " +
                new String(aad, StandardCharsets.UTF_8));
        System.out.println("Decrypted data: " +
                new String(recovered, StandardCharsets.UTF_8));
        System.out.println("Tag overhead: " +
                (ciphertextAndTag.length - plaintext.length) + " bytes");

        byte[] changedAad = aad.clone();
        changedAad[0] ^= 1;
        System.out.println("Changed AAD accepted: " +
                accepted(key, iv, changedAad, ciphertextAndTag));

        byte[] changedCiphertext = ciphertextAndTag.clone();
        changedCiphertext[0] ^= 1;
        System.out.println("Changed ciphertext accepted: " +
                accepted(key, iv, aad, changedCiphertext));

        byte[] changedIv = iv.clone();
        changedIv[0] ^= 1;
        System.out.println("Changed IV accepted: " +
                accepted(key, changedIv, aad, ciphertextAndTag));
    }
}
```

Biên dịch và chạy (JDK 17 trở lên có hỗ trợ AES/GCM):

```bash
javac AadGcmDemo.java
java AadGcmDemo
```

Stdout chính xác cho mọi lần chạy thành công, dù khóa/IV/ciphertext được sinh ngẫu nhiên:

```text
AAD visible: version=1;tenant=shop-7
Decrypted data: hoa-don:120000 VND
Tag overhead: 16 bytes
Changed AAD accepted: false
Changed ciphertext accepted: false
Changed IV accepted: false
```

### Giải thích vì sao ba lần sửa đều thất bại

- **Đổi AAD:** ciphertext/tag/khóa/IV vẫn y như cũ, nhưng header thay một byte. Vì AAD được dùng để tính tag GCM, `doFinal` bên giải mã ném `AEADBadTagException`. Điều này minh họa **AAD được xác thực dù không bị mã hóa**.
- **Đổi ciphertext:** header AAD giữ nguyên, nhưng một byte ciphertext đã đổi. Tag không còn hợp lệ, nên không chấp nhận payload đã bị sửa.
- **Đổi IV:** IV là tham số quan trọng của một lần mã hóa. Giải mã với IV khác sẽ không khôi phục đúng dữ liệu/tag đã tạo; ví dụ cho thấy xác thực bị từ chối.
- **Tag overhead:** với tag dài 128 **bit** thì ciphertext trả về từ `doFinal` ở ví dụ có thêm 16 **byte** tag so với plaintext. Đây là tag xác thực, không phải `PKCS5Padding`; transformation dùng `NoPadding`.
- **Giá trị AAD công khai:** chương trình cố ý in nguyên header để cho thấy AAD không hề được mã hóa. Không suy ra AAD phải đưa lên màn hình/log trong ứng dụng thật; điều đó phụ thuộc yêu cầu riêng tư của header.

**Phạm vi ví dụ:** khóa chỉ tồn tại trong process; đây không phải quy trình lưu key/ciphertext lâu dài. Khi giải mã trên máy khác hoặc sau khi restart, cần truy xuất đúng khóa qua keystore/KMS và truyền/lưu đúng IV, header AAD cùng ciphertext/tag.

## 5. Phân biệt “đã được xác thực” và “đã được mã hóa”

### 5.1. Dữ liệu được mã hóa và xác thực

Trong AES-GCM, **payload** đi qua `Cipher.update`/`doFinal` được biến đổi thành ciphertext; người không có khóa phù hợp không đọc được plaintext theo giả định an toàn của thuật toán. Đồng thời, tag GCM ràng buộc payload, AAD, khóa và nonce trong quá trình xác thực. Bên giải mã chỉ **chấp nhận kết quả sau khi tag hợp lệ**.

Từ “được mã hóa” không đồng nghĩa “bất cứ ciphertext nào cũng an toàn”: cần đúng mode AEAD, nonce/IV, độ dài tag, quản lý khóa và quy trình kiểm tra kết quả.

### 5.2. Dữ liệu được xác thực nhưng không mã hóa

**AAD** không đi qua phép mã hóa plaintext, nên người quan sát vẫn đọc được nội dung AAD nếu bản ghi chứa nó ở dạng rõ. Tuy nhiên, bên nhận gọi `updateAAD` trước `doFinal` để kiểm tra tính toàn vẹn của chính những byte đó cùng payload.

Trường hợp sử dụng: version của định dạng, loại bản ghi, tên tenant không nhạy cảm, định danh tài liệu, sequence number hoặc content type mà bên nhận cần xem *sớm* để định tuyến. Chỉ dùng các trường này để ra quyết định bảo mật **sau khi xác thực thành công**; nếu cần dùng sơ bộ để định tuyến/lấy khóa, phải coi chúng là dữ liệu chưa tin cậy và kiểm tra lại sau xác thực.

### 5.3. Dữ liệu hoàn toàn nằm ngoài phạm vi AEAD

Nếu một trường đi cùng bản ghi mà bạn **không** nạp vào AAD, cũng **không** đưa vào plaintext được mã hóa, thì GCM không tự bảo vệ trường đó. Ví dụ `tenantId` trong JSON nằm ngoài ciphertext mà bị bỏ qua ở `updateAAD` có thể bị sửa mà phép kiểm tra tag vẫn hợp lệ cho ciphertext cũ. Đây là lỗi thiết kế ngữ cảnh rất dễ bị bỏ sót.

Cách sửa: xác định từ đầu danh sách metadata ảnh hưởng đến diễn giải, quyền và bản sắc bản ghi; biểu diễn thành chuỗi byte AAD có version và truyền đúng những byte đó vào AEAD.

### 5.4. AAD không thay thế mã hóa hay chữ ký số

- AAD không bảo vệ **tính bí mật** của chính header.
- AEAD không tự chứng minh danh tính pháp lý của người gửi: nếu nhiều bên biết cùng khóa đối xứng, bên nào có khóa cũng có thể tạo tag hợp lệ.
- Không giải quyết **replay**: bản ghi ciphertext/tag/AAD/IV hợp lệ có thể bị phát lại nguyên vẹn. Chống replay cần sequence number/nonce nghiệp vụ/timestamp được xác thực **và** kiểm soát dùng lại/hết hạn ở ứng dụng.
- Không thay thế kiểm tra **authorization**: tag hợp lệ chưa có nghĩa người yêu cầu được quyền xem hoặc sửa bản ghi của tenant đó.

## 6. Thiết kế record với AAD: những byte nào phải khớp?

Ví dụ một record có thể gồm:

| Trường | Cách bảo vệ | Lưu ý |
| --- | --- | --- |
| `formatVersion` | AAD | Không cho phép kẻ gửi lặng lẽ đổi định dạng; kiểm tra version được hỗ trợ trước khi parse sâu. |
| `tenantId` | AAD nếu công khai; plaintext nếu cần bí mật | Định danh tenant cần ràng buộc với ciphertext và quyền truy cập thực tế. |
| `contentType` | AAD | Ngăn thay đổi cách phân tích payload sau giải mã. |
| `keyId` | AAD hoặc ràng buộc qua giao thức an toàn | Có thể cần dùng để tra khóa **trước xác thực**, lúc này keyId vẫn chưa đáng tin; chỉ dùng whitelist/lookup có giới hạn và xác minh lại. |
| `iv` | Trường công khai riêng | Phải bảo đảm duy nhất theo khóa khi mã hóa; dùng đúng IV ở phía giải mã. |
| `ciphertextAndTag` | Ciphertext bí mật và tag xác thực | Lưu cả tag; không tách/nhầm số byte. |
| `payload` | Plaintext đưa vào `doFinal` | Mã hóa + xác thực; ví dụ số tiền, mô tả nội dung, dữ liệu riêng tư. |

Định nghĩa **byte serialization nhất quán**: một record JSON dùng để hiển thị vẫn có thể khác thứ tự field, whitespace, Unicode normalization hoặc kiểu số giữa hai runtime. Nếu bên gửi tính AAD bằng UTF-8 của `"version=1;tenant=shop-7"` còn bên nhận dùng UTF-8 của `"tenant=shop-7;version=1"`, hai byte array khác nhau và xác thực thất bại.

Hai cách xử lý hợp lý:

1. **Lưu/truyền nguyên byte header đã được xác thực.** Bên nhận lấy chính byte này, chuyển vào `updateAAD`, chỉ sau khi tag hợp lệ mới tin các trường đã parse.
2. **Dùng serialization có chuẩn canonical rõ ràng.** Định nghĩa encoding UTF-8, thứ tự trường, độ dài và loại trường, canonicalization; cả hai phía tái tạo **giống chính xác từng byte**.

Không chỉ nối các giá trị biến độ dài mà không có ranh giới: `"ab" + "c"` và `"a" + "bc"` có cùng chuỗi byte dù hai cặp field khác nhau. Dùng length prefix hoặc cấu trúc field có tag/length và version để tránh nhập nhằng.

## 7. Xử lý dữ liệu theo stream mà không vi phạm thứ tự

Java có các overload:

- `cipher.updateAAD(byte[])`;
- `cipher.updateAAD(byte[], offset, len)`;
- `cipher.updateAAD(ByteBuffer)`.

Và các phương thức xử lý dữ liệu như `update(...)`, `doFinal(...)`. Thứ tự hợp lệ nếu mã hóa/decrypt dữ liệu theo mảnh:

```text
Cipher.init(...)
  |
  +-- updateAAD(headerPart1)
  +-- updateAAD(headerPart2)
  +-- updateAAD(headerPart3)   <-- KẾT THÚC AAD
  |
  +-- update(payloadPart1)
  +-- update(payloadPart2)
  +-- doFinal(lastPayloadPart)
       |
       +-- encrypt: ciphertext cuối + authentication tag
       +-- decrypt: kiểm tra tag; thành công mới chấp nhận plaintext
```

Với `ByteBuffer`, `updateAAD(ByteBuffer)` xử lý các byte trong phần `remaining()` của buffer và cập nhật `position` đến `limit`; nó không đồng nghĩa toàn bộ dung lượng vật lý của buffer nếu position/limit đã thay đổi.

### Không sử dụng plaintext giải mã trước khi kiểm tra tag

Trong GCM, việc kiểm tra tag cuối cùng xảy ra ở `doFinal`. Một số implementation có thể buffer đầu ra giải mã, số khác có thể cung cấp plaintext từ `update` trước khi tag cuối được xác nhận. Vì vậy **không gửi dữ liệu tạm giải mã ra network, ghi vào DB như dữ liệu đáng tin hoặc thực thi nó** trước khi toàn bộ phép xác thực thành công. Với dữ liệu lớn, dùng vùng đệm/quy trình staging và chỉ commit/publish khi `doFinal` thành công; nếu xác thực thất bại, loại bỏ dữ liệu tạm.

Nếu `doFinal` ném `AEADBadTagException`, không thử “giải mã tiếp” hoặc bỏ qua tag để cứu plaintext. Từ chối bản ghi, dọn buffer nhạy cảm thích hợp và xử lý lỗi ở ranh giới ứng dụng.

### Có phải luôn phải gọi updateAAD?

Không. AAD là **tùy chọn** trong AES-GCM: nếu không có metadata cần xác thực bổ sung, ứng dụng có thể không gọi `updateAAD` và vẫn mã hóa/xác thực payload bằng GCM. Nhưng một khi **đã thiết kế** header phải được bảo vệ như AAD, thì cả bên mã hóa và giải mã đều phải gửi đúng byte header qua `updateAAD` trước xử lý payload. Quên bước này ở **cả hai phía** có thể khiến mã hóa/giải mã vẫn chạy nhưng header không được xác thực như mong muốn.

## 8. Rủi ro và nguyên tắc an toàn đi kèm

1. **Không dùng lại (key, IV) khi mã hóa AES-GCM.** IV ngẫu nhiên 96 bit từ `SecureRandom` là một cách sinh nonce phổ biến; hệ thống tải lớn cần kiểm soát xác suất đụng độ, số lần mã hóa mỗi khóa và rotation. Không dùng IV hằng số, không tái sử dụng bản ghi cũ để mã hóa thông điệp mới.
2. **Tách IV và AAD.** IV điều khiển quá trình GCM; AAD là byte metadata tùy chọn nhưng được xác thực. Chúng không thay thế nhau. IV công khai không có nghĩa có thể dùng sai IV mà vẫn mong giải mã đúng.
3. **Dùng tag đủ mạnh theo giao thức.** Ví dụ tag 128 bit và IV 12 byte; `GCMParameterSpec(128, iv)` nhận **bit**, không phải byte. Không để client tự yêu cầu tag ngắn không theo chính sách.
4. **Bảo vệ khóa.** AAD, IV và ciphertext/tag có thể lưu/truyền công khai theo thiết kế, nhưng khóa AES phải được quản lý an toàn bằng keystore/KMS, kiểm soát truy cập và vòng đời riêng. Không hardcode khóa thật.
5. **Không dùng header chưa xác thực để cấp quyền.** Có thể parse sơ bộ version/keyId cho định tuyến nhưng phải giới hạn thuật toán/khóa được phép và **kiểm tra lại** tất cả ngữ cảnh khi tag hợp lệ.
6. **Không dùng AAD chứa dữ liệu mà bạn kỳ vọng được giấu.** Tên người dùng, địa chỉ, tài khoản hoặc nội dung riêng tư trong AAD có thể lộ cho người quan sát. Đưa phần cần bí mật vào plaintext.
7. **Không coi mã hóa AES-CBC đơn lẻ tương đương AEAD.** CBC không có tính năng xác thực AAD/tag của GCM. Nếu cần xác thực toàn bộ message, dùng AEAD hoặc giao thức encrypt-then-MAC được thiết kế đúng.
8. **Giới hạn kích thước và đầu vào.** Kiểm tra IV length, version, tag length, ciphertext size, header length, charset và trường bắt buộc trước khi cấp phát bộ nhớ hoặc giải mã. Từ chối các transformation không nằm trong allowlist.
9. **Không tiết lộ lý do xác thực thất bại quá chi tiết.** Dữ liệu sai AAD, sai key, sai IV, bị sửa ciphertext/tag có thể đều dẫn tới lỗi tag. Ứng dụng cần phản hồi từ chối phù hợp, tránh log plaintext hoặc key.
10. **Không tái dùng Cipher để mã hóa thông điệp mới với cùng key/IV.** Mỗi lần mã hóa phải khởi tạo với IV mới độc nhất. `Cipher` không phải đối tượng chia sẻ tùy tiện giữa các thread.

## 9. Triệu chứng lỗi và cách gỡ

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `IllegalStateException` khi `updateAAD` | Gọi sau `update`, sau thao tác hoàn tất, trước `init`, hoặc mode/provider không ở trạng thái chấp nhận AAD | Khởi tạo cipher và truyền toàn bộ AAD trước khi gửi bất kỳ payload/ciphertext nào |
| `IllegalArgumentException` | Truyền byte[] AAD là `null` hoặc tham số offset/length sai | Xác minh dữ liệu và giới hạn mảng trước khi gọi |
| `AEADBadTagException` từ `doFinal` | AAD/key/IV/ciphertext/tag khác với lúc mã hóa, hoặc dữ liệu bị cắt/sửa | Từ chối toàn bộ bản ghi; kiểm tra contract byte và metadata; không bỏ qua xác thực |
| Dữ liệu payload giải mã đúng nhưng header đổi mà không báo lỗi | Header không được đưa vào AAD ở cả hai phía, hoặc ứng dụng đọc trường khác với trường đã authenticated | Rà soát chính xác byte đã nạp qua `updateAAD` và vùng metadata thực sự được bảo vệ |
| Đổi thứ tự header fields thì decrypt thất bại | Serialization/canonicalization AAD giữa hai bên khác nhau | Định nghĩa byte representation tường minh và kiểm thử round-trip |
| Tag dài hơn dự kiến 16 byte | Nhầm `GCMParameterSpec` tag length **bit** với byte hoặc tách tag sai | Xác định tag 128 bit = 16 byte và cách `doFinal` trả ciphertext+tag |
| Bản ghi bị phát lại vẫn decrypt thành công | AEAD bảo vệ tính toàn vẹn, **không tự chống replay** | Dùng sequence/messageId/timestamp được xác thực và kiểm soát trùng ở application |

Đừng “chữa lỗi” `AEADBadTagException` bằng cách bỏ `updateAAD` trên cả hai phía để dữ liệu giải mã được; cách này vô hiệu hóa mục tiêu bảo vệ header. Cũng không được thay GCM bằng mode không xác thực chỉ để tránh lỗi tag.

## 10. Checklist tự kiểm tra và bài tập

Trước khi triển khai AEAD có AAD, hãy tự trả lời:

- Thành phần nào **chỉ xác thực** (AAD), thành phần nào **vừa mã hóa vừa xác thực** (payload), trường nào nằm ngoài phạm vi AEAD?
- AAD được chuyển thành byte theo encoding/schema nào? Cả bên gửi và nhận có dùng **đúng cùng chuỗi byte** và thứ tự không?
- Có gọi `updateAAD` **trước** `update`/`doFinal` trên **cả hai phía**?
- IV có được tạo mới, unique theo khóa mỗi lần mã hóa? Key được lưu và truy xuất theo cơ chế nào?
- Chỉ công bố/sử dụng plaintext **sau** khi `doFinal` giải mã thành công?
- Header dùng để phân quyền, định tuyến hay chọn khóa có được kiểm tra lại sau khi tag hợp lệ?
- Có kiểm tra sai AAD, sai IV, đổi byte ciphertext/tag, cắt ngắn payload, sai version, replay không?

**Bài tập 1:** thêm một trường `contentType=application/json` vào AAD có độ dài được mã hóa rõ ràng; cho bên giải mã dùng cùng byte, rồi thử đổi `contentType` nhưng giữ nguyên ciphertext/tag để kiểm tra thất bại.

**Bài tập 2:** chuyển payload từ `doFinal(plaintext)` sang nhiều lần `update`, kết thúc bằng `doFinal`. Giữ `updateAAD` trước tất cả các lần `update` và so sánh kết quả round-trip; thử gọi `updateAAD` sau `update` để quan sát lỗi trạng thái trên provider.

**Bài tập 3:** tạo bản ghi có version, keyId, IV, AAD và ciphertext/tag. Xây dựng quy tắc serialization/canonicalization ổn định; test sau khi lưu/đọc qua file hoặc database. Không đưa khóa AES thật vào bài tập hay kho mã nguồn.

## 11. Tài liệu chính thức

- [Cipher — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): `updateAAD`, `update`, `doFinal`, lưu ý GCM/CCM phải nạp toàn bộ AAD trước dữ liệu và kiểm tra tag.
- [GCMParameterSpec — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): IV và độ dài tag theo bit.
- [AEADBadTagException — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/AEADBadTagException.html): lỗi tag xác thực không khớp khi giải mã AEAD.
- [Java Security Developer's Guide — Oracle](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html): kiến trúc provider và cách dùng các engine mật mã, bao gồm chế độ AEAD.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-009">6.9 ChaCha20, ChaCha20-Poly1305 và quản lý nonce</a>

<details>
<summary>Xem chi tiết</summary>

# 6.9 ChaCha20, ChaCha20-Poly1305 và quản lý nonce

**ChaCha20** là stream cipher hiện đại dùng khóa bí mật 256 bit để tạo luồng byte giả ngẫu nhiên (*keystream*) rồi XOR với dữ liệu cần mã hóa. **ChaCha20-Poly1305** kết hợp ChaCha20 với cơ chế xác thực Poly1305 để tạo **AEAD** (*Authenticated Encryption with Associated Data*): vừa bảo vệ bí mật bản rõ, vừa phát hiện nội dung và metadata được xác thực bị thay đổi. Cả hai đòi hỏi quản lý **nonce** cẩn thận. Lặp nonce với cùng khóa có thể làm lộ thông tin bản rõ; với ChaCha20-Poly1305 còn phá vỡ các giả định bảo mật của thẻ xác thực.

Bài học thuộc phần **mã hóa đối xứng** của Java Cryptography Architecture/Extension (JCA/JCE). Mục tiêu là hiểu thuật toán, dùng đúng API Java 17+, và thiết kế vòng đời nonce an toàn khi ứng dụng có nhiều bản tin, luồng, tiến trình hoặc phải phục hồi sau sự cố.

## 1. Phân biệt rõ ba khái niệm

| Thành phần | Chức năng | Kích thước theo biến thể IETF/RFC 8439 | Điều không được nhầm |
| --- | --- | --- | --- |
| **ChaCha20** | Mã hóa/giải mã bằng XOR với keystream | Khóa **256 bit**; nonce **96 bit**; bộ đếm block **32 bit**; block keystream **64 byte** | Chỉ bảo mật nội dung; **không tự phát hiện sửa ciphertext**. |
| **Poly1305** | Bộ xác thực thông điệp với **khóa dùng một lần** | Tag **128 bit (16 byte)** | Không dùng Poly1305 độc lập với một khóa dùng đi dùng lại cho nhiều thông điệp. |
| **ChaCha20-Poly1305** | AEAD: mã hóa và kiểm tra tính toàn vẹn/xác thực | Khóa **256 bit**; nonce **96 bit (12 byte)**; tag **128 bit (16 byte)** | Tag được kiểm tra khi giải mã; **nonce vẫn phải duy nhất với mỗi khóa mã hóa**. |

Trong cả hai chế độ, nonce là giá trị phân biệt một lần sử dụng khóa; nó **không bắt buộc bí mật**. Có thể gửi nonce cùng ciphertext để bên nhận giải mã. Tuy nhiên, từ “không cần bí mật” **không** đồng nghĩa “được phép lặp lại”.

Đối với AEAD, dữ liệu phụ trợ **AAD** (ví dụ phiên bản định dạng, mã bản ghi, tenant, thứ tự bản tin) được **xác thực nhưng không mã hóa**. Chúng vẫn nhìn thấy được nếu gửi rõ. Cùng một AAD phải được sử dụng dưới dạng **các byte giống hệt nhau** lúc mã hóa và giải mã.

**Chọn đúng lớp bảo vệ:** khi chỉ dùng `ChaCha20`, người nhận không có bằng chứng ciphertext được giữ nguyên. Với nội dung có thể bị tấn công, ưu tiên `ChaCha20-Poly1305` hoặc một AEAD được phê duyệt, thay vì tự ghép stream cipher với một checksum như SHA-256.

## 2. ChaCha20 hoạt động như thế nào?

ChaCha20 là một ARX cipher (*addition, rotation, XOR*). Lõi của thuật toán xử lý một trạng thái 16 từ 32 bit, thực hiện **20 vòng** biến đổi bằng các phép cộng modulo `2^32`, xoay bit và XOR. Mỗi lần xử lý block sinh **64 byte keystream** từ:

- Một **key 32 byte**;
- Một **nonce 12 byte**;
- Một **block counter 32 bit**.

Đối với một block, phép mã hóa có thể hình dung:

```text
                         key 256 bit
                             |
nonce 96 bit ----------> ChaCha20 block function <---------- block counter 32 bit
                             |
                       keystream 64 byte
                             |
plaintext block -------------XOR---------------------------> ciphertext block
```

Giải mã cũng là XOR cùng keystream:

```text
C = P XOR KS(key, nonce, counter)
P = C XOR KS(key, nonce, counter)
```

Bởi `x XOR y XOR y = x`, cùng khóa, nonce và counter phải được dùng để khôi phục bản rõ. **Bộ đếm block** tăng khi xử lý các block liên tiếp của *cùng một thông điệp*. Điều đó khác với **bộ đếm thông điệp** mà hệ thống dùng để tạo một nonce mới cho thông điệp kế tiếp.

### 2.1 Hai bộ đếm khác nhau

| Bộ đếm | Nằm ở đâu? | Nhiệm vụ |
| --- | --- | --- |
| **ChaCha20 block counter (32 bit)** | Bên trong thao tác mã hóa một thông điệp | Chọn block keystream tiếp theo của cùng cặp key/nonce; phải tránh wrap và chồng vùng keystream. |
| **Message sequence / nonce counter** | Do ứng dụng hoặc giao thức quản lý | Cấp **nonce 96 bit mới** cho mỗi lần mã hóa dùng cùng key; phải được cấp phát duy nhất, kể cả khi chạy đồng thời và khởi động lại. |

Trong biến thể IETF, mỗi cặp `(key, nonce)` có không gian block counter hữu hạn, xấp xỉ tối đa 256 GiB keystream nếu tính `2^32 × 64` byte, và AEAD còn dành block counter 0 cho khóa Poly1305. Không thiết kế một bản tin lớn vô hạn dựa vào việc counter tự wrap. Thực tế nên chia thành **bản ghi có kích thước giới hạn**, mỗi bản ghi dùng nonce mới, và áp dụng giới hạn nhỏ hơn theo giao thức/provider.

### 2.2 Vì sao tái sử dụng nonce nguy hiểm?

Nếu hai thông điệp được mã hóa bằng **cùng key, cùng nonce và vùng counter trùng nhau**, keystream tương ứng bị lặp:

```text
C1 = P1 XOR KS
C2 = P2 XOR KS

C1 XOR C2 = P1 XOR P2
```

Kẻ quan sát có thể tính XOR của hai bản rõ từ hai bản mã. Nếu biết một bản rõ, họ khôi phục được phần tương ứng của bản rõ còn lại. Nếu thông điệp có cấu trúc dự đoán được, mức rò rỉ có thể nghiêm trọng. Đây **không** phải chuyện nonce “ít ngẫu nhiên” theo cảm giác; lỗi cốt lõi là **dùng lại vùng keystream**.

Thay đổi `counter` không được coi là một cách tùy tiện để “cứu” nonce lặp. Nếu ứng dụng tự chia vùng counter thì phải bảo đảm các vùng keystream không chồng, theo dõi giới hạn block và nhất quán giao thức; trong hệ thống thông thường, quy tắc đơn giản và an toàn hơn là **mỗi thông điệp có nonce riêng**.

## 3. ChaCha20-Poly1305 bổ sung điều gì?

ChaCha20-Poly1305 theo RFC 8439 là một dạng **AEAD**. Một lần mã hóa dùng:

```text
Input:
  key       = 32 byte
  nonce     = 12 byte, không lặp với key này
  plaintext = byte[] cần giữ bí mật
  AAD       = byte[] cần xác thực nhưng không cần giấu

Output:
  ciphertext
  authentication tag = 16 byte
```

Quy trình khái quát:

1. Dùng ChaCha20 với `counter = 0` để **dẫn xuất khóa Poly1305 dùng một lần** từ cặp key/nonce. Khóa này không phải một khóa lâu dài độc lập.
2. Dùng ChaCha20 từ `counter = 1` để mã hóa plaintext thành ciphertext.
3. Tính tag Poly1305 trên **AAD, ciphertext, phần đệm và độ dài được mã hóa theo RFC 8439**. Tag 16 byte được gửi cùng ciphertext.
4. Khi giải mã, kiểm tra tag theo đúng AAD và ciphertext. Nếu sai, **từ chối bản tin**; không sử dụng plaintext chưa được xác thực.

```text
                      (key, nonce)
                            |
                +-----------+-----------+
                |                       |
        ChaCha20 counter=0      ChaCha20 counter=1,2,...
                |                       |
        one-time Poly1305 key       ciphertext
                |                       |
                +-----------+-----------+
                            |
             Poly1305 xác thực AAD + ciphertext
                            |
                       tag 16 byte
```

Từ góc nhìn Java, ta **không cần tự triển khai** phép lấy khóa Poly1305, sắp xếp phần đệm hay kiểm tra tag. `Cipher.getInstance("ChaCha20-Poly1305")` làm các bước này. Tự ghép `ChaCha20` với `Poly1305` rất dễ sai ranh giới dữ liệu, counter, độ dài và xử lý lỗi.

### 3.1 AAD mang ý nghĩa gì?

Giả sử một bản ghi có:

- `version`: phiên bản định dạng;
- `recordId`: định danh bản ghi;
- `tenantId`: phạm vi sở hữu;
- `ciphertext`: dữ liệu thực tế.

Ứng dụng có thể đưa `version`, `recordId`, `tenantId` đã được mã hóa thành byte theo chuẩn nhất quán vào **AAD**. Nếu ai đó sửa `tenantId` hoặc `recordId` trong metadata được xác thực, giải mã với AAD khác sẽ thất bại. Tuy nhiên, nếu phần metadata đó không được đưa vào AAD, AEAD **không tự biết** ứng dụng muốn bảo vệ trường nào.

AAD không bí mật; không đặt mật khẩu/khóa vào đó. Tránh ghép chuỗi bằng dấu phân cách mơ hồ (`a|bc` so với `ab|c`) hoặc phụ thuộc thứ tự trường không cố định. Chọn encoding cố định (ví dụ dạng nhị phân gồm field ID + độ dài + bytes, hoặc cấu trúc canonical đã quy định) và giữ cả hai phía thống nhất.

### 3.2 AEAD không tự chống replay

Nếu một ciphertext, nonce, AAD, tag **hợp lệ** được gửi lại nguyên vẹn, kiểm tra tag vẫn có thể thành công. AEAD bảo vệ tính xác thực của dữ liệu, không tự ghi nhớ rằng thông điệp đã từng được xử lý. Với giao thức có trạng thái, cần thêm `messageId`, sequence hoặc timestamp **được xác thực** và một cơ chế chống replay/rollback ở đầu nhận.

## 4. API Java 17+: gọi đúng thuật toán và ParameterSpec

JCA/JCE cung cấp hai tên `Cipher` có liên quan nhưng contract khác nhau:

| Tác vụ | JCA/JCE | Key | Tham số khởi tạo |
| --- | --- | --- | --- |
| Stream cipher thuần | `Cipher.getInstance("ChaCha20")` | `SecretKeySpec(rawKey32, "ChaCha20")` | `new ChaCha20ParameterSpec(nonce12, initialBlockCounter)` |
| AEAD | `Cipher.getInstance("ChaCha20-Poly1305")` | `SecretKeySpec(rawKey32, "ChaCha20")` | `new IvParameterSpec(nonce12)` |
| AAD cho AEAD | `cipher.updateAAD(aad)` | Dùng key đã `init` | **Phải gọi trước** `update`/`doFinal` của dữ liệu |
| Hoàn tất | `cipher.doFinal(input)` | Dùng key đã `init` | AEAD khi mã hóa trả `ciphertext || tag`; khi giải mã kiểm tra tag |

**Chi tiết quan trọng:** `ChaCha20ParameterSpec` là tham số của **ChaCha20 thuần**, chứa nonce 12 byte và counter 32 bit. Với **ChaCha20-Poly1305** trên provider JDK thông thường, dùng `IvParameterSpec` chứa nonce 12 byte; counter được AEAD quản lý bên trong. Đừng lấy `GCMParameterSpec` của AES-GCM để khởi tạo ChaCha20-Poly1305, cũng đừng ghép `/GCM/NoPadding` vào tên ChaCha20.

Java 17+ có các lớp `javax.crypto.Cipher`, `javax.crypto.AEADBadTagException`, `javax.crypto.spec.ChaCha20ParameterSpec`, `javax.crypto.spec.IvParameterSpec` và `javax.crypto.spec.SecretKeySpec`. Các provider phải được kiểm tra trong **JDK mà ứng dụng thực tế triển khai**; provider ngoài JDK/HSM có thể khác về cách hỗ trợ thuật toán, khóa và tham số.

Khi `Cipher.init(..., key, params)` được gọi, phía giải mã **phải biết nonce và các byte AAD chính xác**. Nonce thường được truyền bên ngoài ciphertext; bảo vệ và xác thực các metadata quan trọng bằng AAD hoặc định dạng giao thức.

## 5. Chương trình chạy được: ChaCha20 thuần không xác thực bản mã

Lưu nguyên đoạn sau thành **`ChaCha20StreamDemo.java`**. Chương trình sinh khóa/nonce cho một lượt demo, mã hóa một thông điệp và giải mã lại. Sau đó nó sửa một bit ciphertext, giải mã lần nữa và cho thấy phép giải mã **vẫn trả bytes** dù dữ liệu đã đổi.

```java
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class ChaCha20StreamDemo {
    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] rawKey = new byte[32];
        byte[] nonce = new byte[12];
        random.nextBytes(rawKey);
        random.nextBytes(nonce);

        SecretKeySpec key = new SecretKeySpec(rawKey, "ChaCha20");
        ChaCha20ParameterSpec params = new ChaCha20ParameterSpec(nonce, 1);
        byte[] plaintext = "Hello ChaCha20".getBytes(StandardCharsets.UTF_8);

        Cipher encryptor = Cipher.getInstance("ChaCha20");
        encryptor.init(Cipher.ENCRYPT_MODE, key, params);
        byte[] ciphertext = encryptor.doFinal(plaintext);

        Cipher decryptor = Cipher.getInstance("ChaCha20");
        decryptor.init(Cipher.DECRYPT_MODE, key, params);
        byte[] recovered = decryptor.doFinal(ciphertext);
        System.out.println("Giai ma: "
                + new String(recovered, StandardCharsets.UTF_8));

        // Chỉ sửa bản mã để minh họa thiếu tính toàn vẹn; không mã hóa
        // một thông điệp thứ hai bằng cùng cặp key/nonce.
        byte[] tampered = ciphertext.clone();
        tampered[0] ^= 1;

        Cipher decryptor2 = Cipher.getInstance("ChaCha20");
        decryptor2.init(Cipher.DECRYPT_MODE, key, params);
        byte[] altered = decryptor2.doFinal(tampered);
        System.out.println("Giai ma ban ma bi sua: thanh cong");
        System.out.println("Noi dung con giong: "
                + Arrays.equals(plaintext, altered));

        Arrays.fill(rawKey, (byte) 0);
    }
}
```

```bash
javac -encoding UTF-8 ChaCha20StreamDemo.java
java ChaCha20StreamDemo
```

Stdout trên JDK có provider hỗ trợ ChaCha20:

```text
Giai ma: Hello ChaCha20
Giai ma ban ma bi sua: thanh cong
Noi dung con giong: false
```

Hai thao tác giải mã cùng cặp key/nonce là hành vi **đọc lại bản mã**, không phải hai lần sinh keystream cho hai plaintext mới. Đầu ra khác sau khi sửa ciphertext chứng minh ChaCha20 đơn thuần không phát hiện sửa đổi: thư viện không ném ngoại lệ xác thực, vì vốn không có tag.

**Không dùng ví dụ này như một định dạng lưu trữ dữ liệu chống giả mạo.** Nếu yêu cầu có toàn vẹn/xác thực, chuyển sang AEAD. Việc `Arrays.fill(rawKey,...)` xóa buffer do chương trình nắm giữ, không bảo đảm mọi bản sao khóa trong `SecretKeySpec`, `Cipher` hoặc bộ nhớ JVM đã bị xóa.

## 6. Chương trình chạy được: ChaCha20-Poly1305, AAD và tag thất bại

Lưu nguyên đoạn sau thành **`ChaCha20Poly1305Demo.java`**. Đây là một ví dụ tự chứa, dùng một khóa/nonce mới trong đúng một phép mã hóa. Để tập trung vào API, nonce được sinh ngẫu nhiên **chỉ cho một thông điệp demo**. Mục 7–9 sẽ đưa ra cách cấp phát nonce bảo đảm không lặp cho dịch vụ sản xuất.

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class ChaCha20Poly1305Demo {
    private static byte[] encrypt(
            SecretKeySpec key, byte[] nonce, byte[] aad, byte[] plaintext)
            throws GeneralSecurityException {
        if (nonce.length != 12) {
            throw new IllegalArgumentException("Nonce phai 12 byte");
        }
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(nonce));
        cipher.updateAAD(aad); // Luon truoc thao tac tren plaintext.
        return cipher.doFinal(plaintext); // ciphertext || 16-byte tag
    }

    private static byte[] decrypt(
            SecretKeySpec key, byte[] nonce, byte[] aad,
            byte[] ciphertextAndTag) throws GeneralSecurityException {
        if (nonce.length != 12 || ciphertextAndTag.length < 16) {
            throw new IllegalArgumentException("Khung du lieu khong hop le");
        }
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertextAndTag); // Kiem tra tag.
    }

    public static void main(String[] args) throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        byte[] rawKey = new byte[32];
        byte[] nonce = new byte[12];
        random.nextBytes(rawKey);
        random.nextBytes(nonce);

        SecretKeySpec key = new SecretKeySpec(rawKey, "ChaCha20");
        byte[] plaintext = "Hello AEAD".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "record=v1".getBytes(StandardCharsets.UTF_8);
        try {
            byte[] ciphertextAndTag = encrypt(key, nonce, aad, plaintext);
            byte[] recovered = decrypt(key, nonce, aad, ciphertextAndTag);
            System.out.println("Giai ma: "
                    + new String(recovered, StandardCharsets.UTF_8));
            System.out.println("Tag bytes: "
                    + (ciphertextAndTag.length - plaintext.length));

            byte[] tampered = ciphertextAndTag.clone();
            tampered[tampered.length - 1] ^= 1;
            try {
                decrypt(key, nonce, aad, tampered);
                throw new AssertionError("Da chap nhan tag sai");
            } catch (AEADBadTagException expected) {
                System.out.println("Tag bi sua: tu choi");
            }

            byte[] wrongAad = "record=v2".getBytes(StandardCharsets.UTF_8);
            try {
                decrypt(key, nonce, wrongAad, ciphertextAndTag);
                throw new AssertionError("Da chap nhan AAD sai");
            } catch (AEADBadTagException expected) {
                System.out.println("AAD bi sua: tu choi");
            }
        } finally {
            Arrays.fill(rawKey, (byte) 0);
        }
    }
}
```

```bash
javac -encoding UTF-8 ChaCha20Poly1305Demo.java
java ChaCha20Poly1305Demo
```

Stdout trên provider JDK phổ biến:

```text
Giai ma: Hello AEAD
Tag bytes: 16
Tag bi sua: tu choi
AAD bi sua: tu choi
```

**Giải thích từng kiểm tra:**

1. `Cipher.getInstance("ChaCha20-Poly1305")` chọn AEAD; `IvParameterSpec` truyền đúng nonce 12 byte. Bộ đếm block và cách dẫn xuất khóa Poly1305 được provider quản lý.
2. `updateAAD(aad)` chạy trước `doFinal`. Khi giải mã, AAD phải giống hệt byte-for-byte.
3. `encrypt` trả `ciphertext || tag` nên dài hơn plaintext **16 byte**. Ví dụ không mã hóa nonce vào mảng output; hệ thống thật phải lưu/truyền nonce cùng bản ghi.
4. Sửa một bit tag dẫn tới `AEADBadTagException`; thay AAD mà giữ ciphertext/tag cũ cũng bị từ chối.
5. `decrypt` không trả plaintext khi tag thất bại. Không dùng `catch (AEADBadTagException)` để bỏ tag rồi giải mã bằng ChaCha20 thường.

**Giới hạn demo:** sinh nonce ngẫu nhiên cho một thông điệp minh họa không phải cơ chế cấp phát nonces dài hạn; nếu khóa được tái sử dụng cho nhiều bản tin, toàn hệ thống phải thực thi chính sách nonce **không trùng**. Không lưu raw key trong source code, log, cơ sở dữ liệu plaintext hoặc response của dịch vụ.

## 7. Quy tắc quan trọng nhất: nonce phải duy nhất theo khóa

Với IETF ChaCha20-Poly1305, mỗi lần **mã hóa** bằng một khóa `K` phải dùng nonce `N` chưa từng được dùng để mã hóa trước đó cùng `K`. Đó là điều kiện về **tính duy nhất**, không phải yêu cầu nonce phải bí mật hoặc không thể đoán.

```text
(key K, nonce N0) -> message 0    HOP LE
(key K, nonce N1) -> message 1    HOP LE  (N1 != N0)
(key K, nonce N0) -> message 2    SAI!   (N0 da dung voi K)
(key K2, nonce N0) -> message 3   Co the hop le, neu K2 thuc su la key khac
```

Dùng lại `(K,N)` làm ChaCha20 lặp keystream và làm Poly1305 lặp khóa xác thực dùng một lần. Hậu quả không chỉ là để lộ quan hệ XOR giữa plaintext; các bảo đảm về xác thực cũng bị tổn hại. **Tuyệt đối không “khắc phục” lỗi trùng nonce bằng cách tăng counter nội bộ của Cipher hoặc bỏ tag.** Nếu hệ thống phát hiện nghi ngờ trùng nonce, cần dừng ghi với key đó, điều tra phạm vi và xử lý rủi ro/đổi khóa theo chính sách.

### 7.1 Nonce 96 bit: có thể random hay nên dùng counter?

Có hai họ chiến lược thường được nhắc tới:

| Cách cấp nonce | Ưu điểm | Giới hạn/rủi ro |
| --- | --- | --- |
| **12 byte từ `SecureRandom`** | Dễ dùng trong demo/lưu lượng nhỏ, ít trạng thái. | Ngẫu nhiên **không bảo đảm duy nhất tuyệt đối**; xác suất va chạm tăng khi số message cùng key tăng; khó chứng minh không trùng trong nhiều writer. |
| **Nonce xác định và duy nhất**: prefix phân miền + bộ đếm liên tục | Có thể bảo đảm không lặp nếu cấp phát phối hợp, trạng thái bền vững và không wrap. | Cần quản lý phân miền, restart, concurrency, cấp phát số thứ tự và key rotation chính xác. |

Với `n` nonce được chọn đều ngẫu nhiên trong không gian `2^96`, xác suất có va chạm gần đúng khi `n` nhỏ so với căn bậc hai của không gian là:

```text
P(collision) ~ n(n - 1) / (2 * 2^96)
```

Công thức chỉ dùng để **đánh giá rủi ro**, không biến random nonce thành một cơ chế chứng minh duy nhất. RFC 8439 nhấn mạnh việc mỗi lần gọi với cùng key phải có nonce duy nhất; đối với thiết kế dài hạn/multiwriter, ưu tiên **counter và phân hoạch không gian nonce** theo chính sách giao thức. Ví dụ random nonce ở mục 6 chỉ tập trung minh họa Java API.

### 7.2 Mẫu phân hoạch nonce theo writer + sequence

Một cách mô tả cấu trúc 96 bit là:

```text
        12 bytes / 96 bits
+----------------+--------------------------------+
| writerId/prefix| monotonically increasing seq   |
| 4 bytes        | 8 bytes                        |
+----------------+--------------------------------+
         hai phần phải được phân bổ nhất quán theo scope một key
```

- `writerId`/prefix **phải duy nhất giữa các writer đang dùng cùng key**, được cấp một cách có kiểm soát; không giả định prefix 4 byte chọn random chắc chắn không trùng.
- `seq` phải tăng **không lặp lại** trong cùng prefix và key. Hai thread không được cùng nhận một số. Không wrap về 0 khi đạt giới hạn.
- Sau crash/restart, writer phải phục hồi từ trạng thái **bền vững** chưa từng cấp lại; chỉ dùng `AtomicLong` trong RAM là **không đủ**.
- Một nonce đã **cấp phát và dùng** phải được coi là tiêu thụ, kể cả thao tác ghi dữ liệu thất bại hoặc response gửi lại bị mất. Retry một phép mã hóa mới phải lấy nonce **mới**, hoặc gửi lại đúng ciphertext đã lưu mà không mã hóa lại.
- Nếu có nhiều tiến trình, container, vùng địa lý hoặc shard dùng cùng key, phải có cơ chế cấp miền/sequence duy nhất toàn hệ thống. Một process tự đếm từ 0 là không đủ.

Đây là **một ví dụ bố trí bit**, không phải định dạng bắt buộc của mọi giao thức. Những hệ thống như TLS có cách tạo nonce riêng gắn với số thứ tự và bí mật phiên; **không** tự thay thế quy tắc của giao thức bằng mẫu tự chế này.

## 8. Chương trình chạy được: minh họa bộ cấp nonce dạng counter

Ví dụ này chỉ minh họa cách ghép prefix 4 byte với sequence 8 byte và cấp số atomically trong **một JVM**. Nó không có lưu trữ bền vững hay cơ chế phân quyền prefix, vì vậy **không phải allocator production**. Prefix `01 02 03 04` được cố định để stdout dễ kiểm chứng; tuyệt đối không sao chép prefix và khởi tạo counter bằng 0 vào nhiều worker cùng key trong thực tế.

Lưu thành **`NonceCounterDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

public final class NonceCounterDemo {
    private final byte[] prefix;
    private final AtomicLong nextSequence;

    NonceCounterDemo(byte[] prefix, long initialSequence) {
        if (prefix == null || prefix.length != 4 || initialSequence < 0) {
            throw new IllegalArgumentException(
                    "Prefix hoac sequence khong hop le");
        }
        this.prefix = prefix.clone();
        this.nextSequence = new AtomicLong(initialSequence);
    }

    byte[] nextNonce() {
        long current;
        do {
            current = nextSequence.get();
            if (current == Long.MAX_VALUE) {
                throw new IllegalStateException("Het vung nonce");
            }
        } while (!nextSequence.compareAndSet(current, current + 1));

        return ByteBuffer.allocate(12)
                .put(prefix)
                .putLong(current) // Quy uoc big-endian cho sequence trong demo.
                .array();
    }

    public static void main(String[] args) {
        NonceCounterDemo allocator =
                new NonceCounterDemo(new byte[]{1, 2, 3, 4}, 0);
        System.out.println(HexFormat.of().formatHex(allocator.nextNonce()));
        System.out.println(HexFormat.of().formatHex(allocator.nextNonce()));
    }
}
```

```bash
javac -encoding UTF-8 NonceCounterDemo.java
java NonceCounterDemo
```

Stdout:

```text
010203040000000000000000
010203040000000000000001
```

Điều cần hiểu trong ví dụ:

- `compareAndSet` giúp các thread chia sẻ **một** `AtomicLong` không nhận cùng số do race trong JVM.
- Dùng `ByteBuffer.putLong` theo byte order mặc định big-endian cho **định dạng message sequence**, không phải đang mô tả endian của từ bên trong ChaCha20. ChaCha20 nhận nonce dưới dạng **12 byte**; tính duy nhất là vấn đề chính.
- Để đơn giản, ví dụ chỉ sử dụng miền sequence không âm `0..Long.MAX_VALUE-1` và từ chối khi tới giới hạn, tránh lỗi wrap của `long`. Không bắt buộc dùng hết không gian 64 bit.
- Mỗi lần chạy lại `new NonceCounterDemo(..., 0)` sẽ trả **nonce cũ**. Nếu dùng lại key, đây là lỗi nghiêm trọng. Production phải có trạng thái persistent/atomic hoặc cơ chế phân biệt khóa/miền đã được thiết kế.

**Trường hợp nhiều writer:** phân bổ prefix không trùng cho từng writer cùng key; hoặc cấp dải sequence tập trung; hoặc tách khóa theo sender qua KDF thích hợp rồi quản lý nonce trong từng miền key. Mỗi phương án phải có cách chứng minh không trùng khi scale, failover, rollback và redeploy.

## 9. Kiến trúc quản lý nonce trong ứng dụng thực tế

Đây là một thiết kế tham chiếu có các thành phần bắt buộc; **không** phải đoạn mã triển khai đầy đủ cho cơ sở dữ liệu/phân tán.

```text
          Kho khóa/KMS                          Durable allocator
              |                                (per keyId + writer)
      keyId -> secret key                            |
              |                            cấp nonce không trùng
              +---------------+---------------------+
                              |
                        Encrypt service
                              |
          header(version, keyId, nonce, flags) + AAD
                              |
                  ChaCha20-Poly1305 encrypt
                              |
              ciphertext || authentication tag
                              |
          Lưu/truyền một record theo format đã version hóa
```

### 9.1 Ràng buộc khóa và nonce

**Khóa:** sinh ngẫu nhiên 32 byte bằng bộ sinh số ngẫu nhiên mật mã phù hợp, hoặc lấy từ KMS/keystore qua cơ chế tạo khóa đã phê duyệt. Không dùng `new Random()`, timestamp, username hay `password.getBytes()` như một khóa ChaCha20 trực tiếp. Nếu nguồn là password/shared secret cần dùng KDF đúng mục tiêu và quản lý salt/tham số theo thiết kế; không tự lắp hash để tạo khóa. Tránh dùng chung một khóa cho những mục đích khác nhau nếu giao thức không chứng minh tương thích.

**Key ID:** bản ghi cần chỉ ra khóa nào dùng để giải mã; khi xoay khóa, bản ghi cũ vẫn cần khả năng tìm khóa cũ cho đến khi được chuyển đổi hoặc hết hạn. Key ID có thể công khai, nhưng phải nằm trong miền tin cậy; nên xác thực metadata nhạy cảm bằng AAD.

**Nonce uniqueness domain:** khóa và nonce phải được quản lý như một cặp. Bộ đếm có thể bắt đầu lại **với khóa mới thực sự độc lập**, nhưng không được reset rồi dùng lại key cũ. KMS/rotation và allocator cần thống nhất lifecycle `keyId` để tránh cấp phát lại vùng nonce.

### 9.2 Bản ghi có phiên bản

Một format có thể là:

```text
version (1 byte)
keyIdLength + keyId
nonce (12 bytes)
ciphertextLength + ciphertext
tag (16 bytes)

AAD = encoding chinh xac cua version + keyId + recordId/tenant/context
```

Các trường độ dài và encoding phải được định nghĩa **không mơ hồ**, có giới hạn chặn payload bất thường; đoạn minh họa trên chỉ là phác thảo schema. Java `Cipher.doFinal` với ChaCha20-Poly1305 thường trả `ciphertext || tag` trong **một byte array**, do đó khi serialize cần biết nonce 12 byte ở đâu và tag 16 byte ở đâu. Không coi tag hay nonce là secret, và không được để phía nhận tự đoán key/nonce/version.

Nếu `recordId` hay `tenant` là thuộc tính kiểm soát quyền truy cập, hãy đưa chúng vào AAD và đồng thời **kiểm tra authorization** ở lớp ứng dụng; AEAD không thay thế kiểm soát quyền. Không dùng AAD để giấu dữ liệu nhạy cảm vì AAD vẫn nhìn thấy được.

### 9.3 Cấp phát bền vững trước khi mã hóa

Một hệ thống với khóa sống lâu có thể áp dụng chiến lược:

1. Xác định duy nhất `(keyId, writerId)` và miền sequence dành riêng cho nó.
2. **Reserve** sequence hoặc một khoảng sequence trong một kho giao dịch/allocator đáng tin, theo kiểu atomic, **commit dấu mốc đã cấp trước khi sử dụng**.
3. Tạo nonce từ prefix đã phân bổ + số sequence vừa cấp, chưa bao giờ lặp lại.
4. Mã hóa một record bằng AEAD rồi lưu `(keyId, nonce, AAD schema, ciphertext, tag)` theo định dạng ổn định.
5. Nếu process chết sau cấp nonce nhưng trước khi ghi record, **bỏ qua** nonce đã cấp; không lấy lại chỉ vì không thấy ciphertext trong DB.
6. Nếu response bị mất sau khi record đã lưu, **trả lại record ciphertext cũ** theo idempotency key. Nếu cần mã hóa lại dữ liệu, reserve **nonce mới**.
7. Không cho sequence wrap; cảnh báo khi tới ngưỡng định trước và xoay key theo chính sách.

Không thể chứng minh không trùng chỉ bằng `synchronized`, `AtomicLong`, khóa mutex trong một pod hay Redis key không được bảo toàn qua failover. Hệ thống cần xác minh tính bền vững và các trường hợp network partition, rollback DB, restore backup, autoscaling và di chuyển workload.

### 9.4 Các lỗi nonce phổ biến

| Lỗi | Vì sao sai | Cách phòng tránh |
| --- | --- | --- |
| Mọi lần encrypt dùng `new byte[12]` (toàn số 0) với khóa sống lâu | Lặp `(key,nonce)` từ lần thứ hai. | Chỉ nonce duy nhất cho mỗi lần encrypt; zero nonce chỉ có thể phù hợp nếu key thật sự mới mỗi bản tin theo giao thức đã thiết kế. |
| Dùng `System.currentTimeMillis()` hoặc `Random` | Dễ trùng/đoán được và không có bảo đảm duy nhất; `Random` không phải CSPRNG. | Counter phối hợp bền vững, hoặc chính sách random có chứng minh rủi ro được phê duyệt. |
| Mỗi pod tự đếm từ 0 với cùng key/prefix | Các pod tạo cùng nonce. | Cấp prefix/miền độc quyền hoặc bộ cấp sequence tập trung. |
| Restart từ counter 0 | Tạo lại nonce đã dùng trước crash. | Lưu high-water mark trước khi dùng, không cấp lại sau rollback. |
| Retry mã hóa bản ghi với nonce cũ nhưng payload/AAD thay đổi | Cặp `(key,nonce)` bị dùng lại cho một phép mã hóa mới. | Retry ciphertext đã lưu hoặc xin nonce mới cho lần encrypt mới. |
| Coi nonce là secret; không truyền nonce | Bên nhận không biết tham số giải mã, dễ phải suy đoán. | Lưu/truyền nonce rõ ràng cùng bản ghi, đồng thời kiểm soát key. |
| Cho phép counter wrap hoặc xử lý một message vô hạn | Lặp block keystream/đụng giới hạn thuật toán. | Giới hạn kích thước record, kiểm tra overflow, xoay key/nonce. |
| Cố “sửa” AEAD tag sai bằng cách giải mã stream thường | Bỏ qua xác thực, có nguy cơ đưa dữ liệu giả vào xử lý nghiệp vụ. | Từ chối toàn bộ record; điều tra key/nonce/AAD/tag. |

## 10. Xử lý lỗi Java và những điều cần tránh

| Lỗi hoặc tình huống | Thường xảy ra khi | Cách xử lý |
| --- | --- | --- |
| `NoSuchAlgorithmException` | `Cipher.getInstance("ChaCha20")` hoặc `getInstance("ChaCha20-Poly1305")` không có implementation | Kiểm tra JDK/provider, cấu hình và thuật toán; phát hiện sớm lúc startup. |
| `InvalidKeyException` | `Cipher.init` nhận khóa không đúng thuật toán/kích thước hoặc bị provider từ chối | Kiểm tra key 256 bit và nguồn cấp khóa; không thử khóa yếu/tự chuyển thuật toán. |
| `InvalidAlgorithmParameterException` | Nonce sai kích thước hoặc tham số không đúng với engine/provider | Đối chiếu `ChaCha20ParameterSpec` với stream cipher, `IvParameterSpec` với AEAD; nonce 12 byte. |
| `IllegalArgumentException` | Constructor `ChaCha20ParameterSpec` nhận nonce dài khác 12 byte hoặc validation của ứng dụng | Sửa đầu vào, không coi là xác thực bản mã thất bại. |
| `IllegalStateException` | `updateAAD` sai thứ tự/trạng thái; tái sử dụng cipher hoặc khởi tạo lại với nonce không an toàn theo kiểm tra provider | Tạo phép toán mới, cấp nonce mới; không dựa vào việc provider sẽ phát hiện mọi lần reuse ở các tiến trình. |
| `AEADBadTagException` | `doFinal` khi giải mã thấy ciphertext/tag/AAD/key/nonce không khớp | **Từ chối thông điệp**, không trả plaintext. Xử lý response tránh tạo oracle chi tiết. |

`AEADBadTagException` không có nghĩa chắc chắn “sai mật khẩu” hay “nonce bị trùng”: cùng ngoại lệ có thể xuất hiện khi ciphertext bị sửa, key sai, nonce sai, tag sai hoặc AAD không khớp. Đầu nhận nên trả thông báo xác thực thất bại thống nhất và ghi chẩn đoán giới hạn trong log bảo vệ; không log key, plaintext hay secret từ KMS.

**Không tái sử dụng cùng `Cipher` để mã hóa lần nữa bằng key/nonce cũ.** Một số provider từ chối tái sử dụng trên cùng instance, nhưng bộ nhớ chống trùng của một `Cipher` không bảo vệ các instance/process khác. Cần bộ cấp nonce ở lớp thiết kế.

### 10.1 Kiểm tra cấu hình tại startup

Đoạn dưới đây chỉ minh họa khả năng lookup (không phải một chương trình độc lập):

```java
// Đoạn mã ở trong phương thức đã khai báo throws GeneralSecurityException.
Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
System.out.println(cipher.getProvider().getName());
```

Nếu provider cụ thể được yêu cầu (ví dụ policy/FIPS), dùng overload thích hợp và không tự động fallback khi provider không sẵn có. Việc một JDK có ChaCha20-Poly1305 **không đồng nghĩa** provider hay mô-đun đó được chứng nhận theo chính sách của hệ thống; kiểm tra yêu cầu tuân thủ riêng.

## 11. ChaCha20-Poly1305, AES-GCM và XChaCha20-Poly1305

| Tiêu chí | ChaCha20-Poly1305 (IETF) | AES-GCM | XChaCha20-Poly1305 |
| --- | --- | --- | --- |
| Loại | AEAD | AEAD | AEAD dùng biến thể mở rộng nonce của ChaCha |
| Key | 256 bit | 128/192/256 bit tùy triển khai và policy | Thường 256 bit |
| Nonce | **96 bit** trong RFC 8439 | **96 bit** phổ biến/được khuyến nghị | **192 bit** |
| Tag | 128 bit | Thường 128 bit (có cấu hình khác được phép theo policy) | 128 bit |
| Java JCA/JCE | `ChaCha20-Poly1305` trên JDK hỗ trợ | `AES/GCM/NoPadding` | **Không** giả định là tên thuật toán JCA chuẩn có sẵn; cần thư viện/provider tương ứng |
| Cảnh báo chính | Không trùng nonce với key | Không trùng IV/nonce với key | Khác định dạng/nonce; không tự thay một tên bằng tên khác |

Không kết luận thuật toán nào **luôn** nhanh hơn; hiệu năng tùy CPU, tăng tốc phần cứng, provider và workload. AES-GCM và ChaCha20-Poly1305 đều là lựa chọn AEAD mạnh nếu sử dụng đúng chính sách, khóa, nonce, AAD và quản lý lỗi. Chọn dựa trên giao thức tương tác, yêu cầu compliance, thư viện sẵn có và benchmark của môi trường thật.

**XChaCha20-Poly1305 không tương thích trực tiếp với IETF ChaCha20-Poly1305**: nonce 24 byte không thể truyền vào `IvParameterSpec` 12 byte rồi gọi cùng tên để tự có XChaCha. Nếu dùng XChaCha, cần thư viện/định dạng tương ứng và quy tắc quản lý nonce của nó, không tự chế bước dẫn xuất.

## 12. Tình huống tự kiểm tra

**Tình huống A — Hai thread cùng key, mỗi thread `AtomicLong(0)`:** Có an toàn không?  
**Trả lời:** Không. Mỗi thread có counter riêng nên cùng prefix có thể tạo cùng nonce. Cần một allocator chia sẻ có cấp phát nguyên tử hoặc các prefix/miền được phân bổ duy nhất.

**Tình huống B — Tăng block counter lên 2 cho message thứ hai nhưng giữ key/nonce:** Có giải quyết được nonce reuse không?  
**Trả lời:** Không phải chính sách an toàn cho message mới. Nếu vùng keystream trùng, hai plaintext rò rỉ XOR; cần một thiết kế quản lý range và giới hạn rất chặt chẽ. Mặc định bắt buộc **nonce mới** cho mỗi lần mã hóa độc lập.

**Tình huống C — Thay AAD `tenant=alpha` thành `tenant=beta`, giữ nonce/ciphertext/tag:** Kết quả?  
**Trả lời:** AEAD từ chối do tag không khớp (nếu chuỗi byte AAD thay đổi). Điều đó không thay thế kiểm soát phân quyền tenant ở lớp ứng dụng.

**Tình huống D — Đồng bộ DB từ backup cũ khiến allocator cấp lại sequence đã dùng:** Có thể tiếp tục dùng cùng key?  
**Trả lời:** Không nên. Phải bảo đảm khôi phục trạng thái cấp nonce không bị lùi, hoặc thay key/miền trước khi mã hóa tiếp; xác định phạm vi các nonce đã có thể trùng và xử lý sự cố.

**Tình huống E — Tag hợp lệ nhưng message cũ bị gửi lại:** AEAD có tự chặn?  
**Trả lời:** Không. Cần deduplication, sequence/timestamp được xác thực và trạng thái chống replay theo giao thức.

**Tình huống F — Một file lớn được mã hóa bằng ChaCha20 với counter 32 bit và xử lý không giới hạn:** Vấn đề?  
**Trả lời:** Block counter hữu hạn; khi wrap có nguy cơ lặp keystream. Chia record hữu hạn, dùng nonce mới cho record mới, giới hạn kích thước theo giao thức/provider.

**Tình huống G — Người lập trình gọi `cipher.update(plaintext)` rồi `cipher.updateAAD(header)`:** Có đúng?  
**Trả lời:** Không. Với AEAD, AAD phải được đưa vào trước phần plaintext/ciphertext; provider có thể ném `IllegalStateException`. Hãy xác thực toàn bộ header trước khi xử lý payload.

## 13. Checklist khi thiết kế và review

- **Mục tiêu bảo mật:** cần tính bí mật **và** xác thực? Dùng AEAD thay vì ChaCha20 thuần.
- **Đúng API:** `ChaCha20ParameterSpec(nonce12, counter)` cho `ChaCha20`; `IvParameterSpec(nonce12)` cho `ChaCha20-Poly1305`.
- **Khóa:** 32 byte đủ ngẫu nhiên, quản lý trong KMS/keystore hoặc cơ chế an toàn tương ứng, không hardcode.
- **Nonce:** không lặp cho cùng key; định rõ scope, cấp phát duy nhất trong multiwriter, trạng thái bền vững qua restart/crash, không wrap.
- **AAD:** cố định encoding, có version/định danh/context liên quan nếu cần; cung cấp trước phần dữ liệu và kiểm tra lại đúng byte.
- **Record:** lưu/truyền version, keyId, nonce, ciphertext và tag theo định dạng không mơ hồ; có giới hạn độ dài.
- **Giải mã:** từ chối nếu tag sai; không trả plaintext chưa được xác thực, không log secret hoặc fallback sang stream cipher thuần.
- **Replay:** nếu nghiệp vụ cần, kiểm tra thứ tự/ID/timestamp trên dữ liệu được xác thực và lưu trạng thái ở đầu nhận.
- **Lifecycle:** phối hợp key rotation với allocator, backup/restore và xử lý sự cố trùng nonce; kiểm tra tải trọng/trần counter.
- **Kiểm thử:** test round-trip, sửa ciphertext, sửa tag, sửa AAD, sai key/nonce, record thiếu trường, restart allocator và hai writer cấp đồng thời.

## Tài liệu chính thức

1. [RFC 8439 — ChaCha20 and Poly1305 for IETF Protocols](https://www.rfc-editor.org/rfc/rfc8439.html): cấu trúc ChaCha20, nonce 96 bit, block counter, dẫn xuất khóa Poly1305, AEAD, cảnh báo reuse.
2. [Java SE 21 — `ChaCha20ParameterSpec`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/ChaCha20ParameterSpec.html): nonce 12 byte và initial counter 32 bit.
3. [Java SE 21 — `Cipher`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html): `getInstance`, `init`, `updateAAD`, `doFinal`, ngoại lệ và AEAD.
4. [Java SE 21 — `AEADBadTagException`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/AEADBadTagException.html): lỗi xác thực tag khi giải mã.
5. [Java SE 21 — Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html): tên chuẩn `ChaCha20` và `ChaCha20-Poly1305` trong JCA/JCE.

**Kết luận kỹ thuật:** ChaCha20 biến khóa và nonce thành keystream để mã hóa; ChaCha20-Poly1305 thêm tag xác thực và AAD. Sự an toàn của cả hai phụ thuộc vào **không tái sử dụng nonce với cùng khóa**. Trong Java, dùng đúng `Cipher`/`ParameterSpec`, còn ở cấp hệ thống phải quản lý nonce bền vững, duy nhất giữa các writer và sống sót qua crash/restart.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-010">6.10 Xử lý AEADBadTagException, BadPaddingException và bản mã bị cắt</a>

<details>
<summary>Xem chi tiết</summary>

# 6.10 Xử lý AEADBadTagException, BadPaddingException và bản mã bị cắt

## Mục tiêu

Sau bài học, bạn có thể xác định chính xác ý nghĩa của `AEADBadTagException` và `BadPaddingException` trong Java Cryptography Architecture (JCA/JCE), phân biệt **bản mã bị cắt ngắn** với **xác thực AEAD thất bại**, thiết kế cấu trúc bản mã (envelope) có đủ thông tin để kiểm tra độ dài, xây dựng quy trình giải mã AES-GCM không phát tán plaintext khi tag chưa được xác minh, và xử lý lỗi đủ an toàn để không tạo thêm thông tin cho đối thủ.

Bài thuộc chủ đề **6. Mã hóa đối xứng**. Trọng tâm là `AES/GCM/NoPadding`, nhưng cần biết `BadPaddingException` cũng xuất hiện ở các chế độ cũ như `AES/CBC/PKCS5Padding`. Mục tiêu là **chẩn đoán đúng, từ chối an toàn và giữ giao thức nhất quán**; ngoại lệ không phải bằng chứng duy nhất để xác định liệu dữ liệu bị sửa, truyền thiếu, khóa sai hay tham số giải mã không khớp.

## 1. Điều gì được xác thực trong AES-GCM?

AES-GCM là một chế độ **AEAD (Authenticated Encryption with Associated Data)**: nó bảo mật nội dung (confidentiality) đồng thời kiểm tra tính toàn vẹn và tính xác thực của bản mã cùng dữ liệu liên kết (integrity/authenticity), với điều kiện khóa, nonce và cách sử dụng đúng.

Một lần mã hóa AEAD sử dụng:

- **Khóa bí mật** `K`: cùng khóa phù hợp ở bên mã hóa và giải mã.
- **Nonce/IV** `N`: không cần giữ bí mật, nhưng **phải không lặp lại với cùng khóa AES-GCM**. Thực tế thường chọn nonce ngẫu nhiên 12 byte (96 bit) bằng nguồn ngẫu nhiên mật mã.
- **Plaintext** `P`: dữ liệu cần bảo mật.
- **AAD**: dữ liệu không mã hóa nhưng vẫn được đưa vào kiểm tra tính xác thực, ví dụ phiên bản envelope hoặc ngữ cảnh request.
- **Ciphertext** `C` và **authentication tag** `T`: đầu ra mà bên nhận phải kiểm tra đầy đủ trước khi tin tưởng plaintext.

Trong Java, thao tác phổ biến là:

```java
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
cipher.updateAAD(aad);
byte[] plaintext = cipher.doFinal(ciphertextAndTag);
```

Đây là **đoạn minh họa bên trong phương thức**, không phải chương trình độc lập; cần import `javax.crypto.Cipher`, `javax.crypto.spec.GCMParameterSpec`, cùng biến `key`, `nonce`, `aad`, `ciphertextAndTag` đúng định dạng. Với `GCMParameterSpec(128, nonce)`, tag dài **128 bit = 16 byte**. JCE thường nhận ciphertext **và tag nối tiếp nhau** trong đầu vào giải mã: `C || T`. Một số giao thức lưu tag ở trường riêng; khi dùng `Cipher.doFinal` cần ghép lại đúng thứ tự mà provider yêu cầu.

**“NoPadding” không có nghĩa là không có authentication tag.** GCM không dùng PKCS#5/#7 padding của CBC, nhưng vẫn bắt buộc xử lý tag xác thực. Thiếu hoặc sai tag khiến bên nhận không được chấp nhận plaintext.

### Envelope và overhead tối thiểu

Để truyền nhận an toàn, hai phía phải thống nhất format. Ví dụ minh họa trong bài:

```text
+------------+----------------+----------------------+-------------------+
| version    | nonce          | ciphertext           | GCM tag           |
| 1 byte     | 12 byte        | 0..n byte            | 16 byte           |
+------------+----------------+----------------------+-------------------+
```

Overhead tối thiểu là **1 + 12 + 16 = 29 byte**; kể cả plaintext rỗng, tag 16 byte vẫn phải có. Các byte đầu của envelope không tự được mã hóa, nhưng **version phải được đưa vào AAD** để bảo vệ khỏi sửa đổi mà vẫn giữ tính linh hoạt của format. Ví dụ cũng đưa một `context` do hai phía thống nhất vào AAD, chẳng hạn “POST /orders”.

Giao thức thực có thể cần thêm key ID, độ dài, thuật toán, tham số phiên bản, loại nội dung. Chúng phải có encoding đơn nghĩa, giới hạn kích thước, được liên kết mật mã phù hợp và được kiểm tra trước khi phân tích dữ liệu đầu vào.

## 2. Quan hệ giữa các ngoại lệ Java

Sơ đồ kế thừa quan trọng:

```text
Exception
  └─ GeneralSecurityException
       ├─ BadPaddingException
       │    └─ AEADBadTagException
       ├─ IllegalBlockSizeException
       ├─ InvalidKeyException
       ├─ InvalidAlgorithmParameterException
       └─ NoSuchAlgorithmException
```

`javax.crypto.AEADBadTagException` **kế thừa** `javax.crypto.BadPaddingException`. Do đó:

- `catch (BadPaddingException e)` cũng bắt được `AEADBadTagException`.
- Nếu muốn xử lý chi tiết ở tầng bên trong, phải bắt **`AEADBadTagException` trước `BadPaddingException`**; đảo ngược thứ tự sẽ làm nhánh cụ thể không thể truy cập và Java báo lỗi biên dịch.
- `IllegalBlockSizeException` là một nhánh **khác** của `GeneralSecurityException`, không kế thừa `BadPaddingException`.
- Các lỗi kiểm tra format do ứng dụng tự ném, ví dụ `IllegalArgumentException`, không thuộc các loại checked exception trên; nên đặt mã phân loại lỗi rõ ràng tại ranh giới giao thức.

| Ngoại lệ | Thường xuất hiện ở đâu | Điều có thể kết luận |
| --- | --- | --- |
| `AEADBadTagException` | `Cipher.doFinal` khi giải mã AEAD và tag không xác minh thành công | **Không xác thực được** tổ hợp key, nonce, AAD, ciphertext và tag đã cung cấp |
| `BadPaddingException` (loại thường) | `doFinal` ở chế độ có padding như CBC/PKCS5Padding; một số provider có thể dùng lỗi tổng quát khi xử lý AEAD | Padding cuối không hợp lệ hoặc thao tác giải mã thất bại theo provider; không đủ để chỉ ra nguyên nhân |
| `IllegalBlockSizeException` | `doFinal` với dữ liệu không phù hợp độ dài block/chế độ | Input không phù hợp cách xử lý block; có thể do truyền thiếu, sai mode hoặc framing |
| `InvalidKeyException` | `Cipher.init` | Key sai loại, không phù hợp hoặc bị provider/policy từ chối |
| `InvalidAlgorithmParameterException` | `Cipher.init` | IV/nonce/parameters không hợp lệ hoặc không được provider hỗ trợ |
| `NoSuchAlgorithmException` / `NoSuchPaddingException` | `Cipher.getInstance` | Transformation không được cung cấp bởi provider đã chọn |

**Không kết luận từ tên ngoại lệ rằng có một “cuộc tấn công”.** Với GCM, `AEADBadTagException` có thể do một byte bị hỏng vô ý, sai khóa, nonce không đúng, AAD khác, tag thiếu, cắt gói hoặc cố ý giả mạo. Các trường hợp đó có cùng biểu hiện mật mã: xác thực thất bại.

Cụ thể, `AEADBadTagException` là ngoại lệ chính xác hơn cho thất bại xác thực AEAD trên các provider tuân theo API; nhưng một số provider hoặc cách bao bọc API có thể trả lỗi ít đặc hiệu hơn. Ứng dụng cần xử lý `GeneralSecurityException`/lỗi định dạng phù hợp ở ranh giới, thay vì phụ thuộc vào chuỗi message hoặc tên một provider.

## 3. Bản mã bị cắt (truncation) có những dạng nào?

“Bản mã bị cắt” có thể xảy ra ở **tầng vận chuyển**, **tầng format envelope** hoặc ngay **phần ciphertext/tag**:

1. **Thiếu một phần header:** không đủ byte để đọc version hoặc nonce; phải chặn trước khi khởi tạo `Cipher`. Ví dụ envelope chỉ dài 10 byte, không thể có đủ nonce 12 byte và tag 16 byte.
2. **Có đủ header nhưng không đủ tag:** với layout 1 + 12 + C + 16 byte, đầu vào ít hơn 29 byte chắc chắn không hợp lệ. Từ chối bằng kiểm tra độ dài/phạm vi trước khi gọi `doFinal`.
3. **Mất một vài byte phần cuối nhưng vẫn dài ít nhất 29 byte:** ứng dụng có thể không nhận ra ngay từ tổng độ dài nếu không biết độ dài message mong đợi. GCM sẽ đọc 16 byte cuối hiện tại làm tag; tag không còn đúng nên **xác thực thất bại**, thường ném `AEADBadTagException`.
4. **Mất byte giữa ciphertext hoặc thay đổi thứ tự:** phép xác thực tag không khớp, thường cũng báo lỗi AEAD.
5. **Đọc sai ranh giới nhiều bản tin:** nối hai record hoặc cắt giữa record do framing sai, thường cho lỗi xác thực tương tự. Không coi việc tạo `Cipher` thành công là bằng chứng record đã đầy đủ.

Vì lỗi có thể mang cùng biểu hiện, **không thể phân biệt chắc chắn “bị cắt”, “bị sửa”, “AAD sai” và “khóa sai” chỉ dựa trên `AEADBadTagException`**. Muốn biết record thiếu byte ở mức giao thức, cần khung dữ liệu/độ dài rõ ràng (ví dụ length prefix đã giới hạn, Content-Length được kiểm soát, record length trong protocol), đọc đủ record, và so với số byte thực nhận.

### Khi biết độ dài trước

Nếu giao thức có trường `recordLength` từ một nguồn đáng tin/được kiểm soát, hãy kiểm tra:

```text
receivedBytes == expectedRecordLength
expectedRecordLength >= minimumEnvelopeLength
expectedRecordLength <= maximumAllowedRecordLength
```

Trường độ dài là dữ liệu **chưa được xác thực** nếu nằm ngoài tag; phải ràng buộc giới hạn và liên kết nó vào metadata xác thực nếu cần. Không cấp phát mảng theo độ dài do client cung cấp mà chưa giới hạn. Nếu nhận ít hơn số byte cần thiết, đó là **truncated record** ở tầng vận chuyển; dù vậy, thông điệp trả cho client không nên tiết lộ thêm chi tiết có ích cho việc khai thác.

### Khi không biết độ dài trước

Đọc hết record theo quy ước EOF/frame của protocol trước khi giải mã. `doFinal` xác nhận tag chỉ trên **chính những byte đã cung cấp**. Nếu đầu vào ngắn hơn minimum envelope, có thể phân loại malformed/truncated ngay; nếu dài hơn minimum mà thiếu một phần, lỗi xác thực là kết luận đúng, còn nguyên nhân vật lý cần chẩn đoán qua log/transport metrics.

## 4. Mẫu Java 17 chạy được: kiểm tra bản mã GCM bị sửa hoặc cắt

Chương trình dưới đây cố ý tạo một khóa AES ngẫu nhiên và một nonce ngẫu nhiên **cho từng lần mã hóa**, ghi bản mã dưới format `[version:1][nonce:12][ciphertext][tag:16]`. `version` cùng `context` là AAD. Phần xử lý lỗi trả các mã kết quả **chỉ để học cách phân loại bên trong**; ở API public, hãy gom các lỗi không hợp lệ về một phản hồi xác thực thất bại thống nhất.

Tạo đúng tệp **`AeadFailuresDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class AeadFailuresDemo {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;
    private static final int HEADER_BYTES = 1 + NONCE_BYTES;
    private static final byte VERSION = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] encrypt(SecretKey key, byte[] plaintext, byte[] context)
            throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(new byte[] { VERSION });
        cipher.updateAAD(context);
        byte[] ciphertextAndTag = cipher.doFinal(plaintext);

        byte[] envelope = new byte[HEADER_BYTES + ciphertextAndTag.length];
        envelope[0] = VERSION;
        System.arraycopy(nonce, 0, envelope, 1, NONCE_BYTES);
        System.arraycopy(ciphertextAndTag, 0, envelope, HEADER_BYTES,
                ciphertextAndTag.length);
        return envelope;
    }

    private static byte[] decrypt(SecretKey key, byte[] envelope, byte[] context)
            throws GeneralSecurityException {
        if (envelope == null || envelope.length < HEADER_BYTES + TAG_BYTES) {
            throw new IllegalArgumentException("Invalid envelope length");
        }
        if (envelope[0] != VERSION) {
            throw new IllegalArgumentException("Unsupported version");
        }

        byte[] nonce = Arrays.copyOfRange(envelope, 1, HEADER_BYTES);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(new byte[] { envelope[0] });
        cipher.updateAAD(context);
        return cipher.doFinal(envelope, HEADER_BYTES,
                envelope.length - HEADER_BYTES);
    }

    private static String status(SecretKey key, byte[] envelope, byte[] context) {
        try {
            decrypt(key, envelope, context);
            return "ACCEPT";
        } catch (AEADBadTagException e) {
            return "REJECT_AUTH";
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            return "REJECT_CRYPTO";
        } catch (IllegalArgumentException e) {
            return "REJECT_FORMAT";
        } catch (GeneralSecurityException e) {
            return "REJECT_CRYPTO_SETUP";
        }
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey key = generator.generateKey();

        byte[] context = "POST /orders".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "pay=100".getBytes(StandardCharsets.UTF_8);
        byte[] envelope = encrypt(key, plaintext, context);

        byte[] changedTag = envelope.clone();
        changedTag[changedTag.length - 1] ^= 1;

        byte[] changedNonce = envelope.clone();
        changedNonce[1] ^= 1;

        byte[] truncated = Arrays.copyOf(envelope, envelope.length - 1);
        byte[] tooShort = Arrays.copyOf(envelope,
                HEADER_BYTES + TAG_BYTES - 1);

        byte[] changedVersion = envelope.clone();
        changedVersion[0] = 2;

        System.out.println("valid: " + status(key, envelope, context));
        System.out.println("tag changed: " + status(key, changedTag, context));
        System.out.println("nonce changed: " + status(key, changedNonce, context));
        System.out.println("tail truncated: " + status(key, truncated, context));
        System.out.println("too short: " + status(key, tooShort, context));
        System.out.println("AAD changed: " + status(key, envelope,
                "POST /other".getBytes(StandardCharsets.UTF_8)));
        System.out.println("version changed: "
                + status(key, changedVersion, context));
        System.out.println("round-trip: "
                + Arrays.equals(plaintext, decrypt(key, envelope, context)));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 AeadFailuresDemo.java
java AeadFailuresDemo
```

**Stdout trên JDK thử nghiệm với provider AES-GCM hỗ trợ tag 128 bit** (đã biên dịch với chế độ Java 17):

```text
valid: ACCEPT
tag changed: REJECT_AUTH
nonce changed: REJECT_AUTH
tail truncated: REJECT_AUTH
too short: REJECT_FORMAT
AAD changed: REJECT_AUTH
version changed: REJECT_FORMAT
round-trip: true
```

**Diễn giải cụ thể:**

- `valid: ACCEPT` và `round-trip: true` cho thấy cùng key, nonce, AAD và ciphertext/tag cho plaintext ban đầu.
- Thay một bit **tag**, một bit **nonce**, hoặc đổi **AAD** làm xác thực GCM không thành công; trên provider thử nghiệm, Java ném `AEADBadTagException` và hàm trả `REJECT_AUTH`.
- Cắt một byte cuối của ciphertext+tag nhưng vẫn đủ 29 byte cho envelope khiến tag diễn giải ở cuối không hợp lệ; `REJECT_AUTH` là **thất bại xác thực**, chưa thể chỉ ra bên nào làm mất byte.
- `too short` chỉ còn 28 byte: thiếu tối thiểu một byte để có đủ version/nonce/tag, nên mã ứng dụng từ chối **trước khi gọi provider**.
- Đổi version thành `2` được chặn bởi kiểm tra schema. Khi version hợp lệ nhưng bị thay theo cách không bị chặn ở parser, ràng buộc version vào AAD giúp đảm bảo thay đổi sẽ làm tag không khớp.
- Dù nonce được sinh ngẫu nhiên, ciphertext thực tế khác nhau giữa các lần chạy, các **nhóm kết quả** trên ổn định trong cấu hình đã kiểm thử. Một provider khác có thể dùng lớp ngoại lệ tổng quát hơn cho một số lỗi; chức năng bắt lỗi không phụ thuộc message cụ thể.

**Chú ý về thiết kế demo:** khóa do `KeyGenerator` tạo là khóa thử nghiệm trong bộ nhớ; format và `context` chỉ là ví dụ. Ứng dụng thật phải xác định encoding của context và giới hạn message, lưu khóa trong cơ chế phù hợp, dùng nonce **duy nhất cho từng lần mã hóa dưới cùng khóa**, và phân phối nonce/tag cùng ciphertext. Không dùng ciphertext đã chỉnh sửa để “phục hồi” dữ liệu hoặc tự đoán lại nonce.

## 5. Viết `catch` đúng thứ tự và ánh xạ lỗi ở boundary

Khi muốn ghi chẩn đoán nội bộ cho engine `Cipher`, mẫu chung có thể là:

```java
try {
    byte[] plaintext = cipher.doFinal(ciphertextAndTag);
    // Chỉ xử lý plaintext sau khi doFinal thành công.
} catch (AEADBadTagException e) {
    // GCM/AEAD: tag không được xác thực.
} catch (BadPaddingException e) {
    // Lỗi padding hoặc lỗi giải mã tổng quát theo provider.
} catch (IllegalBlockSizeException e) {
    // Input không phù hợp kích thước block/mode.
}
```

Đây chỉ là **khối minh họa**: `cipher` đã được khởi tạo trước với khóa, IV/AAD đúng; `ciphertextAndTag` là `byte[]`; các loại ngoại lệ cần import từ `javax.crypto`. Nếu sửa thứ tự bằng cách bắt `BadPaddingException` trước `AEADBadTagException`, nhánh cụ thể sẽ không thể bắt được và mã không biên dịch.

### Phản hồi bên ngoài nên thống nhất

Ví dụ một API nhận bản mã do người dùng gửi, hệ thống bên trong có thể phân loại:

| Mã nội bộ | Trường hợp | Phản hồi bên ngoài |
| --- | --- | --- |
| `MALFORMED_ENVELOPE` | Thiếu header/tag, sai version, trường kích thước bất thường | `Invalid encrypted payload` |
| `AUTHENTICATION_FAILED` | GCM xác thực tag không thành công | `Invalid encrypted payload` |
| `DECRYPTION_FAILED` | Provider báo lỗi padding/block khi giải mã | `Invalid encrypted payload` |
| `KEY_OR_PROVIDER_CONFIG_ERROR` | Key/provider cấu hình sai hoặc không khả dụng | Lỗi dịch vụ chung, không tiết lộ khóa/thuật toán nội bộ |

Mức HTTP status cụ thể phụ thuộc hợp đồng API; quan trọng là **không xây một oracle phân biệt chi tiết** như “tag sai ở byte thứ 7”, “khóa đúng nhưng padding sai”, “nonce đúng” hoặc trả dữ liệu plaintext chưa xác thực. Ứng dụng có thể log mã lỗi, số byte nhận, format version và correlation ID trong kênh vận hành được bảo vệ, nhưng **không log khóa bí mật, plaintext, token, toàn bộ ciphertext/tag nhạy cảm**.

Với lỗi do triển khai hoặc provider bị thiếu, nên giám sát và xử lý đúng cấu hình. **Không fallback tự động** từ AES-GCM sang AES-CBC, đổi tag length, bỏ AAD hoặc thử hàng loạt key/nonce tùy tiện nhằm “giải được bằng mọi giá”.

## 6. `BadPaddingException` với AES-CBC/PKCS5Padding khác gì GCM?

Một hệ thống cũ có thể dùng:

```java
Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
```

Đoạn trên chỉ tạo engine, **chưa mã hóa/giải mã và chưa chỉ định khóa/IV**. Trong CBC với padding:

- Plaintext được đệm để có độ dài theo block AES (16 byte).
- Bên nhận phải kiểm tra và bỏ padding sau khi giải mã block cuối.
- Nếu byte cuối sau giải mã **không tạo padding hợp lệ**, provider thường ném `BadPaddingException`.
- Nếu byte input không phù hợp số block, provider có thể ném `IllegalBlockSizeException`; tên ngoại lệ chính xác phụ thuộc provider và thao tác.
- Ciphertext bị sửa, sai key hoặc record không đầy đủ **có thể** gây ra các lỗi trên; nhưng cũng có trường hợp CBC trả plaintext sai mà **không ném ngoại lệ** vì padding tình cờ hợp lệ.

Vì vậy, **CBC + padding không cung cấp tính xác thực chỉ bằng việc kiểm tra padding**. Việc trả phản hồi khác nhau giữa “padding đúng/sai” có thể tạo *padding oracle* nguy hiểm trong các hệ thống mã hóa không có xác thực. Hệ thống mới ưu tiên AEAD như AES-GCM; hệ thống legacy cần xem lại giao thức xác thực và có kế hoạch di chuyển chặt chẽ. Không “sửa” lỗi padding bằng cách bỏ `PKCS5Padding`, tự đệm zero, cắt block, loại bỏ byte cuối hoặc trả dữ liệu đã giải mã dở.

### Vì sao GCM vẫn có `BadPaddingException`?

Do mô hình kế thừa API Java, `AEADBadTagException extends BadPaddingException`. Điều này là **lý do về phân loại ngoại lệ**, không có nghĩa GCM sử dụng PKCS#5 padding. Nếu một middleware chỉ bắt `BadPaddingException`, nó có thể đang gộp cả lỗi tag xác thực GCM lẫn lỗi padding của CBC. Khi phân tích log, phải biết **transformation** và **provider** đã sử dụng.

## 7. Plaintext trước khi xác thực: lỗi nghiêm trọng khi streaming

Với AEAD, plaintext chỉ đáng tin khi **authentication tag đã được xác minh thành công**. Một số chế độ/provider có thể phát sinh các byte plaintext tạm thời từ `Cipher.update(...)` trước `doFinal(...)`, trong khi tag chưa được kiểm tra. Đó là **dữ liệu chưa xác thực**.

Quy tắc an toàn:

1. **Không** gửi plaintext tạm thời đến client, parser nghiệp vụ, queue, database hay hệ thống thực thi trước khi xác thực hoàn tất.
2. Với bản tin nhỏ, dùng `Cipher.doFinal(...)` trên toàn bộ ciphertext/tag và chỉ trả dữ liệu khi lời gọi thành công.
3. Với bản tin lớn, nếu API/provider trả byte trước khi xác thực, giữ chúng trong vùng cách ly (bộ nhớ hoặc file tạm có kiểm soát), chỉ publish/commit **sau khi `doFinal` thành công**; hủy sạch dữ liệu chưa tin cậy khi xác thực thất bại.
4. Không dùng kết quả một phần để cập nhật trạng thái, phát hành thanh toán, giải nén dữ liệu theo cách gây tác dụng phụ hoặc suy luận “đã giải mã được phần đầu nên key đúng”.

**Lưu ý `CipherInputStream`:** tài liệu Java mô tả rằng lớp này có thể bắt các ngoại lệ kiểm tra toàn vẹn như `BadPaddingException` mà không ném lại đầy đủ cho client; do đó nó **có thể không phù hợp cho giải mã có xác thực** như GCM. Khi việc nhận biết tag thất bại là bắt buộc, sử dụng `Cipher` trực tiếp và quản lý output chưa xác thực theo chính sách rõ ràng.

Việc giải mã một file lớn thành file đích có thể gây mất an toàn nếu ghi trực tiếp rồi người khác đọc ngay. Ưu tiên file tạm không được truy cập bởi ứng dụng khác, giải mã/kiểm tra đầy đủ, sau đó mới **đổi tên/commit atomically nếu filesystem hỗ trợ**; khi lỗi, xóa/hủy file tạm theo chính sách.

## 8. Thiết kế framing để xử lý bản mã bị cắt đáng tin cậy

### 8.1 Xác định một record trước khi giải mã

Cần định nghĩa **một record** là bao nhiêu byte, giới hạn đầu vào và số lượng record. Chẳng hạn:

```text
recordLength (4 bytes, unsigned, network byte order)
version (1 byte)
nonce (12 bytes)
ciphertext (variable)
tag (16 bytes)
```

Độ dài này chỉ là ví dụ framing, **không phải** định dạng từ chương trình `AeadFailuresDemo` (demo nhận sẵn đúng một envelope). Ứng dụng đọc đúng `recordLength` byte trước khi giải mã, từ chối nếu EOF đến sớm hoặc vượt giới hạn kích thước. Nếu một trường độ dài được dùng để cấp phát buffer, phải xác nhận giá trị nằm trong giới hạn và tránh tràn số khi cộng header/tag.

Khi metadata quan trọng nằm ngoài ciphertext, cần ràng buộc vào AAD và định nghĩa rõ thứ tự, độ dài từng trường, phiên bản schema. Nếu protocol gửi record length, header version, key ID và thuật toán, tất cả phải được kiểm tra theo danh sách cho phép; những trường ảnh hưởng ý nghĩa nội dung cũng cần được xác thực đúng cách.

### 8.2 Không “sửa” record bị cắt bằng cách đoán tag

Một lỗi phổ biến là thấy `AEADBadTagException` rồi thử thêm byte zero, tự cắt 16 byte cuối, thay nonce, thay AAD, hoặc giải mã một phần. Các cách đó thay đổi dữ liệu/giao thức ban đầu, không khôi phục bản mã xác thực. Khi truyền nhận không đầy đủ, giải pháp đúng là **đọc lại từ nguồn đáng tin** theo cơ chế retry ở tầng giao thức, sử dụng record mới đầy đủ và xác minh lại từ đầu.

Nếu retry một **lần giải mã** trên cùng record với cùng khóa/AAD là cần thiết để chẩn đoán thì kết quả bảo mật sẽ không khác; không dùng vòng lặp thử khóa/tag để âm thầm vượt qua yêu cầu xác thực. Với **mã hóa**, không bao giờ retry bằng cách mã hóa plaintext khác với cùng key+nonce của AES-GCM.

### 8.3 Giữ format và kích thước đã cam kết

Nếu bạn dùng tag GCM 128 bit khi mã hóa, bên nhận phải biết đúng tag length 128 bit. Không lấy `length - 16` để tách tag khi chưa xác định header, trailer và phép framing đã đúng; không chấp nhận một tag bị cắt rồi so sánh vài byte đầu do attacker cung cấp. Nếu giao thức hỗ trợ tag ngắn hơn, đây phải là **quyết định thiết kế tường minh** được đánh giá bảo mật, thống nhất bên gửi/nhận, và truyền đạt không mơ hồ; không phải cách xử lý lỗi.

## 9. Kiểm tra lỗi theo quy trình

Khi điều tra một `AEADBadTagException`/`BadPaddingException` trên hệ thống thật, thực hiện theo thứ tự:

1. **Đọc transformation thực tế:** `AES/GCM/NoPadding` hay `AES/CBC/PKCS5Padding`? Nếu là GCM, kiểm tra tag length và AAD; nếu CBC, đánh giá nguy cơ padding oracle và thiếu authentication.
2. **Kiểm tra byte framing:** tổng số byte, header/version, số byte nonce/tag, record length và liệu stream có bị EOF sớm. Từ chối record ngắn trước bước crypto khi có thể.
3. **Kiểm tra chọn đúng key:** key ID, phiên bản rotation, provider/keystore; tránh log key. Khi key sai, tag GCM không khớp giống hệt trường hợp ciphertext bị sửa.
4. **Kiểm tra nonce/IV:** hai phía phải dùng byte nonce giống hệt; nonce 12 byte và tag 16 byte là quyết định format của ví dụ, không được tự suy ra từ ciphertext nếu giao thức khác.
5. **Kiểm tra AAD:** đúng byte và thứ tự, encoding, version và ngữ cảnh request. Sai một byte AAD cũng làm tag không xác minh.
6. **Kiểm tra ciphertext/tag và transport:** có bị hex/Base64 decode sai, newline, whitespace, reverse byte, chuyển charset, cắt record hoặc lỗi buffer size hay không?
7. **Kiểm tra provider bằng test độc lập:** round-trip với key/nonce/AAD trong môi trường thử nghiệm và dữ liệu test đã biết; không thử sửa dữ liệu production để bỏ qua lỗi.
8. **Kiểm tra chính sách xuất dữ liệu:** chỉ publish plaintext khi `doFinal` thành công; mọi exception cần rollback/xóa output tạm.

### Bảng chẩn đoán nhanh

| Dấu hiệu | Có thể do | Hành động |
| --- | --- | --- |
| GCM ném `AEADBadTagException` | Tag, key, nonce, AAD hoặc ciphertext không khớp; record bị sửa/cắt | Từ chối plaintext, kiểm tra format/nguồn byte bằng dữ liệu test, giữ phản hồi public thống nhất |
| GCM thiếu ít nhất 16 byte tag theo format | Input không đủ tối thiểu | Chặn trước `Cipher.init/doFinal`, báo malformed/truncated nội bộ |
| Ciphertext bị cắt một byte nhưng vẫn đủ minimum | Tag vẫn được diễn giải từ các byte cuối hiện có và không xác thực | Từ chối record; kiểm tra expected length/EOF |
| CBC ném `BadPaddingException` | Padding không hợp lệ, key sai, ciphertext thay đổi, lỗi format | Không xem đây là thông tin có thể công bố; đánh giá thiết kế CBC và thay bằng AEAD khi phù hợp |
| CBC ném `IllegalBlockSizeException` | Đầu vào không phù hợp số block hoặc mode | Kiểm tra framing, block size, loại dữ liệu |
| CBC không ném exception nhưng plaintext sai | Padding tình cờ hợp lệ; không có kiểm tra toàn vẹn | Không coi giải mã thành công là xác thực; cần AEAD/MAC |
| `InvalidAlgorithmParameterException` | Nonce/IV/tag length không hợp lệ hoặc provider từ chối | Kiểm tra transformation, `GCMParameterSpec` và provider |
| Kết quả khác giữa hai môi trường | Provider/JDK/cấu hình hoặc byte đầu vào khác | Ghi version, provider, transformation, kích thước record, test vector; không ghi khóa bí mật |
| GCM decrypt stream trả dữ liệu dù tag sai | Kết quả tạm hoặc wrapper nuốt lỗi xác thực | Không publish plaintext trước `doFinal`; tránh dùng wrapper che lỗi |

## 10. Checklist tích hợp vào dịch vụ

- [ ] Giao thức nêu rõ algorithm/mode, kích thước nonce/tag, thứ tự byte, version và AAD.
- [ ] AES-GCM dùng nonce **không trùng dưới cùng key**; key được quản lý đúng và không nằm trong log.
- [ ] Bên nhận kiểm tra version, tổng độ dài tối thiểu, giới hạn kích thước và framing trước giải mã.
- [ ] Không tự giả định mọi lỗi tag đều do giả mạo; phân biệt nguyên nhân vận hành bằng log/transport metrics có kiểm soát.
- [ ] Bắt `AEADBadTagException` trước `BadPaddingException` ở mã cần phân loại cụ thể.
- [ ] Không tiết lộ thông tin padding/tag/key chi tiết trên API public.
- [ ] Plaintext chỉ được sử dụng hoặc publish **sau khi kiểm tra tag thành công**.
- [ ] Có test round-trip, đổi một bit ciphertext/tag/nonce, sai AAD, bản mã cắt ngắn và thiếu header.
- [ ] Nếu lưu nhiều record, mỗi record có ranh giới và authentication đúng; không trộn các record.
- [ ] Không fallback sang mode không xác thực, không bỏ tag hoặc tự sửa data lỗi.

## 11. Bài tập thực hành

1. Chạy `AeadFailuresDemo.java`. Giải thích vì sao thay đổi tag, nonce và AAD đều dẫn đến `REJECT_AUTH` dù nguyên nhân bên trong khác nhau.
2. Đổi `Arrays.copyOf(envelope, envelope.length - 1)` thành cắt còn **28 byte**. Vì sao trạng thái chuyển từ `REJECT_AUTH` sang `REJECT_FORMAT` trong ví dụ?
3. Thêm một trường `keyId` (kiểu byte) vào header envelope. Thiết kế quy tắc chọn khóa và cách đưa trường đó vào AAD; làm sao tránh cho attacker điều khiển bất kỳ key nào được dùng?
4. Nếu nhận 32 MiB ciphertext qua stream, mô tả cách giữ output trong file tạm và chỉ publish sau khi tag xác thực thành công.
5. Phân biệt hậu quả của `BadPaddingException` trong CBC với `AEADBadTagException` trong GCM. Vì sao giải mã CBC thành công mà không báo exception vẫn chưa đủ để tin plaintext?
6. Thiết kế tập test với phiên bản header không hợp lệ, record vượt quá kích thước, ciphertext bị thiếu byte, base64 bị hỏng và các provider khác nhau; chỉ ra lỗi nào nên chặn ở parser và lỗi nào cần để `Cipher.doFinal` xác minh.

## Nguồn tham khảo chính thức

- [Java SE 17 — `AEADBadTagException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/AEADBadTagException.html): xác thực tag AEAD thất bại và quan hệ kế thừa `BadPaddingException`.
- [Java SE 17 — `BadPaddingException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/BadPaddingException.html): lỗi padding khi giải mã và các ngoại lệ liên quan.
- [Java SE 17 — `IllegalBlockSizeException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/IllegalBlockSizeException.html): đầu vào không phù hợp thao tác block.
- [Java SE 17 — `Cipher`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): transformation, `updateAAD`, `doFinal`, xử lý GCM và ngoại lệ.
- [Java SE 17 — `GCMParameterSpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): độ dài tag (bit) và IV/nonce.
- [Java SE 17 — `CipherInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/CipherInputStream.html): cảnh báo khi dùng với chế độ giải mã có xác thực.
- [NIST SP 800-38D — GCM/GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final): đặc tả GCM, tag và yêu cầu với IV/nonce.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-011">6.11 Giới hạn của CipherInputStream khi cần phát hiện lỗi xác thực AEAD</a>

<details>
<summary>Xem chi tiết</summary>

# 6.11 Giới hạn của CipherInputStream khi cần phát hiện lỗi xác thực AEAD

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích AEAD xác thực điều gì và vì sao phải kiểm tra authentication tag trước khi sử dụng plaintext.
2. Nêu chính xác cảnh báo trong Java SE 17 đối với **javax.crypto.CipherInputStream** khi giải mã bằng AEAD như AES-GCM: một số lỗi xác thực có thể bị bắt và **không chuyển tiếp** cho code gọi.
3. Phân biệt **đọc hết InputStream mà không thấy IOException** với **đã xác thực thành công ciphertext**.
4. Dùng **javax.crypto.Cipher.doFinal()** trực tiếp để nhận lỗi **AEADBadTagException**, kiểm soát trường hợp ciphertext bị sửa, sai AAD, sai khóa hoặc bị cắt cụt.
5. Thiết kế quy trình giải mã giữ plaintext ở trạng thái **chưa được tin cậy** đến khi xác thực thành công, kể cả với dữ liệu lớn; tránh sử dụng đầu ra một phần, xử lý lỗi sai hoặc chấp nhận dữ liệu chưa đủ tag.

**Ý chính:** Khi ứng dụng bắt buộc phát hiện lỗi xác thực, không nên lấy việc đọc thành công từ **CipherInputStream** làm bằng chứng. Tài liệu Java 17 cảnh báo class này có thể nuốt lỗi integrity check trong quá trình giải mã GCM. **Cipher API trực tiếp**, đặc biệt **doFinal()**, cho phép quan sát kết quả xác thực rõ ràng hơn.

## 1. AEAD bảo vệ dữ liệu như thế nào?

**AEAD (Authenticated Encryption with Associated Data)** là mã hóa có xác thực. Với AES-GCM, ngoài bí mật của plaintext, hệ thống có thể kiểm tra tính toàn vẹn và xác thực theo khóa của ciphertext cùng **AAD**.

Các thành phần cần hiểu:

- **Key:** khóa AES bí mật, phải được bảo vệ và cấp quyền đúng cách.
- **Nonce/IV:** giá trị của lần mã hóa, thường 12 byte với GCM; **không được lặp lại khi dùng cùng khóa**. Nonce có thể lưu cùng ciphertext nhưng phải đúng byte ban đầu.
- **Plaintext:** dữ liệu trước khi mã hóa.
- **Ciphertext:** dữ liệu sau khi mã hóa.
- **Authentication tag:** mã xác thực do GCM tạo; thường 16 byte (128 bit), tính trên khóa, nonce, dữ liệu và AAD.
- **AAD (Additional Authenticated Data):** ngữ cảnh không cần giữ bí mật nhưng phải kiểm tra toàn vẹn, ví dụ tenant ID, loại bản ghi, version hoặc định danh tài nguyên.

Sơ đồ:

~~~text
MÃ HÓA:
plaintext + key + nonce + AAD
               |
        AES-GCM encrypt
               |
         ciphertext || tag
               |
       lưu/truyền cùng nonce

GIẢI MÃ:
ciphertext || tag + key + nonce + AAD
                   |
            AES-GCM decrypt
                   |
               Verify tag
                 /     \
               Đúng    Sai
                |       |
          plaintext   Từ chối
~~~

Trong Java JCA, **Cipher.getInstance("AES/GCM/NoPadding")** trả một Cipher có thể dùng cho mã hóa hoặc giải mã. Khi mã hóa, **Cipher.doFinal(plaintext)** tạo ciphertext kèm tag theo định dạng đầu ra của JCA/provider. Khi giải mã, **Cipher.doFinal(ciphertextWithTag)** xác minh tag. Nếu không khớp, phương thức có thể ném **AEADBadTagException**.

Một bản ghi có tag sai có thể là do ciphertext bị chỉnh sửa, sai khóa, sai nonce, sai AAD, bị cắt mất byte hoặc bị hỏng trong lưu/truyền. **Tag sai nghĩa là không xác thực được; không tự chứng minh nguyên nhân hoặc người gây ra lỗi.**

### 1.1 Tại sao phải kiểm tra tag trước khi dùng plaintext?

Giả sử một dịch vụ đọc thông tin tài khoản đã mã hóa AES-GCM từ storage. Kẻ tấn công sửa ciphertext trên disk mà không biết khóa. Hành vi đúng của dịch vụ là:

1. Giải mã và xác thực bằng đúng key/nonce/AAD.
2. Nếu tag sai, không trả hồ sơ hoặc bản ghi rỗng được đánh dấu hợp lệ.
3. Không ghi plaintext chưa xác thực vào cache, DB, response hoặc hệ thống downstream.
4. Phát hiện lỗi, xử lý theo quy trình và ghi log phù hợp, không rò key/plaintext.

Dùng dữ liệu trước khi xác thực hoàn tất làm mất mục tiêu bảo vệ toàn vẹn của AEAD.

## 2. CipherInputStream hoạt động thế nào?

**javax.crypto.CipherInputStream** bọc một **InputStream** nền và một **Cipher** đã khởi tạo. Mỗi khi bên gọi read(), lớp bọc đọc byte nguồn và đưa qua Cipher trước khi trả dữ liệu cho phía gọi.

~~~text
encrypted.bin
     |
  InputStream
     |
CipherInputStream
  [Cipher]
     |
read()/readAllBytes()
     |
byte[] đầu ra
~~~

API này thuận tiện khi muốn xử lý luồng byte theo giao diện InputStream. Nhưng vì hoạt động trên hai lớp trừu tượng khác nhau, **hợp đồng về exception không giống trực tiếp Cipher API**.

### 2.1 Cảnh báo chính thức trong Java SE 17

JavaDoc Java 17 của **CipherInputStream** ghi rõ lớp này tuân thủ semantics, đặc biệt **failure semantics**, của **FilterInputStream** và **InputStream**. Nó bắt các exception không thuộc tập ngoại lệ mà giao diện lớp cha khai báo. Cụ thể:

- Nó **có thể bắt BadPaddingException và các exception khác** xuất hiện khi kiểm tra tính toàn vẹn thất bại trong lúc giải mã.
- Những exception đó **không được ném lại**, nên code gọi **có thể không biết** kiểm tra integrity đã thất bại.
- Vì vậy, CipherInputStream **có thể không phù hợp để giải mã chế độ authenticated encryption như GCM**.
- Ứng dụng cần authenticated encryption có thể sử dụng **Cipher API trực tiếp** thay thế.

Nguồn gốc kỹ thuật:

- [Oracle Java SE 17 — CipherInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/CipherInputStream.html)
- [Oracle Java SE 17 — Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html)
- [Oracle Java SE 17 — AEADBadTagException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/AEADBadTagException.html)

**Độ chính xác trong diễn giải:** tài liệu nói **có thể** bỏ qua lỗi xác thực, không có nghĩa mọi JVM/provider luôn có cùng kết quả. Một provider hay một lần thử phát hiện lỗi đúng **không tạo ra bảo đảm portable cho mọi môi trường**.

### 2.2 Tại sao exception mật mã có thể biến mất?

Trong mô hình Cipher:

~~~text
Cipher.doFinal(ciphertextAndTag)
          |
     Verify tag
       /     \
     OK       FAILED
      |          |
  plaintext   AEADBadTagException
~~~

Trong mô hình CipherInputStream:

~~~text
Stream.read()
    |
Cipher (có thể gọi doFinal khi kết thúc input)
    |
Tag verification thất bại
    |
BadPaddingException / AEADBadTagException
    |
CipherInputStream có thể bắt và không chuyển tiếp
    |
Bên đọc stream có thể không nhận được tín hiệu tag sai
~~~

**AEADBadTagException** là subclass của **BadPaddingException**, mà BadPaddingException thuộc nhóm **GeneralSecurityException**. **InputStream.read()** chủ yếu tuân theo hợp đồng **IOException**. Việc bọc Cipher vào InputStream không đương nhiên biến mọi lỗi mật mã thành IOException truyền ra ngoài.

Không nên suy ra “CipherInputStream bảo vệ được dữ liệu vì bên trong dùng Cipher đúng”. Thuật toán có thể phát hiện sai tag nhưng **bên ngoài lại không quan sát được lỗi**.

## 3. Ví dụ anti-pattern: đọc hết không đồng nghĩa xác thực thành công

Đoạn Java sau chỉ là minh họa một cách dùng dễ gây hiểu nhầm; các biến Cipher/InputStream đã được khởi tạo bên ngoài, nên đây **không phải chương trình độc lập**.

~~~java
// decryptCipher là Cipher AES-GCM đã init ở DECRYPT_MODE.
// encryptedInput chứa nonce đã được xử lý ở bước init,
// còn luồng này chứa ciphertext || authentication tag.
try (CipherInputStream stream =
         new CipherInputStream(encryptedInput, decryptCipher)) {

    byte[] received = stream.readAllBytes();

    // Không được suy ra tag hợp lệ chỉ vì readAllBytes() không throw.
    // Không sử dụng received như plaintext đã xác thực.
}
~~~

Vấn đề nằm ở giả định ngầm của lập trình viên:

~~~text
"readAllBytes không ném IOException"
                  |
                  v
"Tag chắc chắn đã được kiểm tra thành công"   <- SUY LUẬN SAI
                  |
                  v
"Được phép dùng dữ liệu làm đầu vào nghiệp vụ"
~~~

Khi tag không hợp lệ, tùy provider, bạn có thể thấy lỗi, đầu ra thiếu/rỗng hoặc hành vi khác; không nên tự dựa vào một kết quả thực nghiệm cụ thể rồi coi nó là hợp đồng của API.

### 3.1 Cả readAllBytes(), close() và catch(IOException) đều không sửa được vấn đề

| Cách xử lý | Điều thực sự làm được | Điều không được bảo đảm |
| --- | --- | --- |
| **readAllBytes()** | Cố đọc toàn bộ byte cho tới EOF, trừ khi có lỗi đọc | Không buộc wrapper phải ném lại lỗi tag mà nó đã bắt |
| **try-with-resources/close()** | Giải phóng resource đúng vòng đời | Không chứng minh toàn bộ message đã được xác thực |
| **catch(IOException)** | Bắt các IOException được truyền ra | Không bắt một AEADBadTagException đã bị wrapper giữ lại |
| **available()** | Báo ước lượng byte có thể đọc không chặn theo giao diện InputStream | Không phải trạng thái integrity check hay tag |
| **skip()** | Bỏ qua byte theo semantics stream | Không xác thực thay cho quy trình kết thúc Cipher |
| **Kiểm tra plaintext có dữ liệu** | Có thể kiểm tra độ dài hoặc cú pháp cơ bản | Không xác minh tính toàn vẹn mật mã |

Đóng stream thành công **không tương đương** với **doFinal đã trả plaintext sau khi xác minh tag thành công**.

### 3.2 Lỗi đọc và lỗi xác thực là hai loại sự cố

Một **IOException** có thể đến từ filesystem, mạng hoặc InputStream nền. Còn **AEADBadTagException** cho thấy authentication tag không phù hợp với key, nonce, AAD và ciphertext được đưa vào Cipher.

Ứng dụng nên phân biệt:

- **Lỗi format** trước khi giải mã (version, cấu trúc, thiếu nonce, payload quá ngắn).
- **Lỗi đọc nguồn** (disk/network).
- **Lỗi xác thực** khi gọi Cipher.doFinal.
- **Lỗi nghiệp vụ** sau khi có plaintext đã xác thực.

Không biến tất cả thành “tệp rỗng” hoặc “chưa có dữ liệu”. Tuy nhiên khi trả lỗi cho client không tin cậy, hạn chế thông tin nhạy cảm và tránh tạo oracle tiết lộ chi tiết xử lý mật mã.

## 4. Thiết kế an toàn cho payload có giới hạn kích thước

Với một bản ghi hoặc file có kích thước phù hợp bộ nhớ:

1. **Giới hạn kích thước đầu vào** trước và trong lúc đọc.
2. Phân tích **version, key ID nếu có, nonce và ciphertext/tag**, kiểm tra cấu trúc tối thiểu.
3. Lấy khóa từ key manager/KMS phù hợp, giới hạn quyền sử dụng.
4. **Cipher.init(DECRYPT_MODE, key, new GCMParameterSpec(tagBits, nonce))**.
5. Gọi **Cipher.updateAAD(aad)** trước khi đưa ciphertext vào Cipher.
6. Gọi trực tiếp **Cipher.doFinal(ciphertextWithTag)**. Phương thức trả plaintext khi thành công hoặc ném lỗi mật mã khi thất bại.
7. **Chỉ sau khi doFinal thành công** mới công bố hoặc xử lý plaintext.
8. Khi thất bại, từ chối bản ghi; không dùng byte trung gian, không thử chế độ bảo mật yếu hơn.

~~~text
Input ciphertext có giới hạn
            |
Kiểm tra header/nonce/version
            |
Cipher.init(DECRYPT_MODE)
Cipher.updateAAD(...)
            |
Cipher.doFinal(ciphertext || tag)
        /                 \
   thành công         AEADBadTagException
        |                 |
Dùng plaintext        Từ chối, không publish
~~~

Tại sao đây là khác biệt quan trọng? Bên gọi **trực tiếp nhận kết quả cuối của Cipher**, và exception xác thực không phải đi qua một lớp InputStream có semantics cho phép bỏ qua nó.

## 5. Ví dụ Java 17+ hoàn chỉnh: phát hiện sai tag bằng doFinal

Chương trình tự sinh khóa AES cho **một lần chạy minh họa**, mã hóa bằng AES-GCM và thử giải mã trong bốn tình huống: gói đúng, tag bị sửa, AAD sai và thiếu byte cuối.

Định dạng binary minh họa:

~~~text
+------------+----------------+---------------------------+
| version 1B | nonce 12 bytes | ciphertext || tag 16 B   |
+------------+----------------+---------------------------+
~~~

Ứng dụng thực tế phải bổ sung key ID, chính sách nonce, giao thức lưu trữ/truyền, giới hạn dung lượng và xử lý lỗi/rotation. Ví dụ này dùng dữ liệu công khai và **không đưa khóa vào output**.

Tạo tệp **AeadDoFinalDemo.java** và lưu UTF-8:

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

public class AeadDoFinalDemo {
    private static final byte VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] encrypt(byte[] plaintext,
                                  SecretKey key,
                                  byte[] aad)
            throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        byte[] ciphertextWithTag = cipher.doFinal(plaintext);

        return ByteBuffer.allocate(
                    1 + NONCE_BYTES + ciphertextWithTag.length)
                .put(VERSION)
                .put(nonce)
                .put(ciphertextWithTag)
                .array();
    }

    private static byte[] decrypt(byte[] packet,
                                  SecretKey key,
                                  byte[] aad)
            throws GeneralSecurityException {
        if (packet.length < 1 + NONCE_BYTES + TAG_BYTES) {
            throw new IllegalArgumentException("Payload qua ngan");
        }

        ByteBuffer input = ByteBuffer.wrap(packet);
        if (input.get() != VERSION) {
            throw new IllegalArgumentException("Version khong hop le");
        }

        byte[] nonce = new byte[NONCE_BYTES];
        input.get(nonce);

        byte[] ciphertextWithTag = new byte[input.remaining()];
        input.get(ciphertextWithTag);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);

        // Trả plaintext sau khi tag được xác thực,
        // hoặc ném AEADBadTagException nếu tag không hợp lệ.
        return cipher.doFinal(ciphertextWithTag);
    }

    private static boolean tagRejected(byte[] packet,
                                       SecretKey key,
                                       byte[] aad)
            throws GeneralSecurityException {
        try {
            decrypt(packet, key, aad);
            return false;
        } catch (AEADBadTagException expected) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey key = generator.generateKey();

        byte[] plaintext = "Ban ghi thu nghiem"
                .getBytes(StandardCharsets.UTF_8);
        byte[] aad = "record:42".getBytes(StandardCharsets.UTF_8);

        byte[] packet = encrypt(plaintext, key, aad);
        byte[] recovered = decrypt(packet, key, aad);

        System.out.println("Ban goc hop le: "
                + Arrays.equals(plaintext, recovered));

        byte[] tampered = packet.clone();
        tampered[tampered.length - 1] ^= 1;
        System.out.println("Sua tag bi tu choi: "
                + tagRejected(tampered, key, aad));

        byte[] wrongAad = "record:43"
                .getBytes(StandardCharsets.UTF_8);
        System.out.println("Sai AAD bi tu choi: "
                + tagRejected(packet, key, wrongAad));

        byte[] truncated = Arrays.copyOf(
                packet, packet.length - 1);
        System.out.println("Thieu byte cuoi bi tu choi: "
                + tagRejected(truncated, key, aad));

        Arrays.fill(plaintext, (byte) 0);
        Arrays.fill(recovered, (byte) 0);
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac -encoding UTF-8 AeadDoFinalDemo.java
java AeadDoFinalDemo
~~~

Stdout dự kiến trên triển khai AES-GCM thông dụng của JDK 17:

~~~text
Ban goc hop le: true
Sua tag bi tu choi: true
Sai AAD bi tu choi: true
Thieu byte cuoi bi tu choi: true
~~~

Diễn giải:

- **Ban goc hop le: true:** cùng key, nonce và AAD; tag hợp lệ; doFinal trả lại plaintext đúng.
- **Sua tag bi tu choi: true:** lật một bit trong byte cuối của tag; doFinal không trả plaintext mà ném AEADBadTagException.
- **Sai AAD bi tu choi: true:** giữ nguyên ciphertext/tag nhưng đổi context từ record:42 thành record:43. Tag không phù hợp với AAD mới.
- **Thieu byte cuoi bi tu choi: true:** input vẫn vượt kiểm tra độ dài cấu trúc tối thiểu, nhưng tag bị thiếu một byte, khiến xác thực thất bại.

Nonce và key là ngẫu nhiên nên ciphertext thay đổi mỗi lần chạy; chương trình không in ciphertext/tag và chỉ in những điều kiện đúng/sai có thể kiểm chứng. Phần phát hiện lỗi cụ thể phụ thuộc provider JCA được sử dụng, nhưng dùng Cipher.doFinal có hợp đồng ném lỗi AEAD trong Java SE 17.

**Lưu ý:** ví dụ không quản lý key bền vững và không phải giao thức production. Arrays.fill() chỉ xóa các byte[] mà code kiểm soát, không đảm bảo xóa mọi bản sao trong JVM.

### 5.1 Phân biệt AEADBadTagException với lỗi dữ liệu khác

**AEADBadTagException** kế thừa **BadPaddingException**, có thể được bắt riêng để phân biệt với lỗi cấu trúc đã phát hiện trước khi gọi Cipher. Nhưng lỗi xác thực không nói chắc nguyên nhân là tấn công, hỏng tệp hay sai metadata.

Không nên dùng đường fallback như:

~~~text
GCM tag sai
     |
Thử giải mã lại mà không xác thực tag
     |
Chấp nhận nội dung "đọc được"
~~~

Đó là việc bỏ qua thuộc tính integrity cốt lõi của AEAD. **Từ chối toàn bộ bản ghi** là kết quả an toàn khi tag sai.

## 6. Cảnh báo khác: plaintext từ Cipher.update cũng chưa được xác thực

Giả sử bạn không dùng CipherInputStream mà tự viết vòng lặp đọc ciphertext theo chunk. Code gọi **Cipher.update(...)** có thể trả về byte[] trung gian; lượng byte trả về phụ thuộc provider và chế độ.

~~~text
cipher.update(chunk1) -> candidatePlaintext1 (có thể rỗng)
cipher.update(chunk2) -> candidatePlaintext2 (có thể rỗng)
...
cipher.doFinal(lastChunkAndTag) -> phần cuối HOẶC lỗi xác thực
~~~

**Không được gọi candidatePlaintext là “đã được xác thực” trước khi doFinal thành công.** Một số provider có thể giữ mọi plaintext tới cuối; provider khác có thể cung cấp byte sớm hơn. Ứng dụng phải xử lý theo yêu cầu bảo mật chứ không dựa vào cách provider đệm byte cụ thể.

Nếu code đã:

- Ghi candidatePlaintext vào database thật;
- Đưa byte sang bộ parse hoặc giải nén nhạy cảm;
- Phát dữ liệu ra HTTP response;
- Gửi event, mở khóa tài nguyên hoặc cập nhật trạng thái nghiệp vụ;

thì một lỗi tag xảy ra ở cuối **không thể tự động hoàn tác** các side effect đã xuất hiện.

Quy tắc: **Giữ dữ liệu chưa xác thực cách ly cho đến khi bước xác minh cuối thành công.**

## 7. Giải mã file lớn an toàn

### 7.1 Cách đơn giản cho file có kích thước được giới hạn

Nếu payload phù hợp RAM:

1. Giới hạn kích thước file/request một cách thực tế.
2. Đọc toàn bộ byte encrypted payload kèm tag (đây là việc đọc byte thường, không phải CipherInputStream để xác thực).
3. Gọi Cipher.doFinal trực tiếp.
4. Chỉ chuyển kết quả plaintext cho nghiệp vụ nếu không có exception.

**Files.readAllBytes/InputStream.readAllBytes** có thể cấp phát rất nhiều bộ nhớ; không phù hợp nguồn không đáng tin hoặc file vượt giới hạn. Cần kiểm tra kích thước trước và trong lúc đọc hoặc dùng cơ chế bounded read.

### 7.2 Cách staging cho file lớn

Nếu phải giải mã theo chunk để tiết kiệm RAM:

1. Parse metadata, kiểm tra chính sách key/version/nonce/AAD và giới hạn dữ liệu.
2. Dùng **Cipher.update()** trực tiếp để xử lý những chunk ciphertext, giữ mọi output mà provider trả về trong **staging riêng biệt**, không cho client hoặc nghiệp vụ dùng.
3. Sau khi thực sự đọc đến cuối ciphertext **bao gồm tag**, gọi **Cipher.doFinal()**; đưa output còn lại (nếu có) vào staging.
4. Nếu **doFinal thất bại**, loại bỏ staging và hủy mọi thao tác; không publish bản ghi.
5. Nếu **doFinal thành công**, đóng staging, thực hiện commit/move sang vị trí chính thức qua quy trình bảo đảm tính nhất quán, chẳng hạn atomic rename khi filesystem hỗ trợ hoặc transaction thích hợp.
6. Bảo vệ staging bằng quyền truy cập nghiêm ngặt, thời hạn lưu, quy tắc dọn lỗi; lưu ý staging có thể chứa **plaintext nhạy cảm chưa xác thực**.

Hạn chế quan trọng: **GCM tag xác thực toàn bộ bản ghi**. Nếu app cần truyền từng chunk plaintext cho client **trước khi nhận tag ở cuối**, một ciphertext GCM duy nhất không cho bảo đảm mỗi chunk độc lập đã được xác thực.

Trong trường hợp cần streaming online thật sự, phải **thiết kế protocol phân mảnh có xác thực**: mỗi chunk là một AEAD record riêng với nonce duy nhất, AAD gắn record number/stream ID, và cơ chế phát hiện mất, lặp, đảo thứ tự hoặc cắt cụt record cuối. Việc cắt một ciphertext GCM thành các mảnh rồi cho rằng mỗi mảnh có tag riêng là **không đúng**.

### 7.3 Vì sao không thể chỉ đóng stream ở cuối?

Close() giải phóng resource theo hợp đồng InputStream/FilterInputStream. Nó không sửa được việc lỗi xác thực từ Cipher có thể đã bị CipherInputStream giữ lại. Vì vậy:

~~~text
try (CipherInputStream stream = ...) {
    byte[] bytes = stream.readAllBytes();
}
// Thoát try bình thường.
~~~

**Không đồng nghĩa** với:

~~~text
Cipher.doFinal() đã xác thực tag thành công
và trả plaintext hợp lệ cho đúng thông điệp.
~~~

Mọi quy trình dựa vào việc “đọc hết + đóng stream không lỗi” để khẳng định GCM integrity đều cần được xem xét lại.

## 8. Checklist đúng cho AES-GCM

Đổi API chỉ giải quyết phần **phát hiện lỗi xác thực**, không sửa được thiết kế mật mã sai.

| Yêu cầu | Vì sao cần |
| --- | --- |
| Nonce duy nhất khi dùng cùng key | Lặp nonce AES-GCM có thể phá hỏng bí mật và toàn vẹn |
| Khóa được quản lý riêng, đúng quyền | Kẻ biết khóa có thể tạo ciphertext/tag hợp lệ |
| AAD đúng thứ tự và cùng byte hai phía | AAD sai phải làm tag verification thất bại |
| Phiên bản định dạng và thuật toán cố định/allowlist | Không để attacker ép dùng chế độ yếu hoặc diễn giải khác |
| Có đủ ciphertext và tag trước khi doFinal | Thiếu tag không thể xác thực |
| Giới hạn kích thước input và output | Tránh cạn RAM, disk, CPU khi gặp input không tin cậy |
| Không dùng output trước final success | Đảm bảo không tiêu thụ plaintext chưa xác thực |
| Hủy staging khi lỗi xác thực | Tránh artifact giả được phục vụ như dữ liệu hợp lệ |
| Không log key/plaintext hay debug payload nhạy cảm | Ngăn rò dữ liệu qua quan sát vận hành |
| Chống replay nếu nghiệp vụ cần | AEAD đúng tag không ngăn phát lại gói cũ |
| Có test sai tag, sai AAD, cắt cụt | Chứng minh đường xử lý lỗi đang từ chối dữ liệu |

### 8.1 Lưu ý nonce và key rotation

AES-GCM yêu cầu không lặp nonce với cùng khóa. Nhiều ví dụ sinh nonce ngẫu nhiên 12 byte, nhưng hệ thống mã hóa số lượng lớn phải thiết kế chính sách giới hạn lưu lượng, quản lý nonce và key rotation để giảm nguy cơ va chạm. Nonce không bí mật, có thể lưu cạnh ciphertext. Key phải được bảo vệ bằng KMS/secret manager hoặc cơ chế phù hợp.

Không hardcode khóa vào Java source, không ghi key vào header, log hoặc URL. Khi đổi khóa, bản ghi cũ có thể cần key ID để chọn đúng khóa giải mã. Đừng vô hiệu hóa xác thực chỉ vì một bản ghi cũ lỗi sau rotation.

### 8.2 AAD và ngữ cảnh bản ghi

AAD có thể gồm tenant ID, record ID, version hoặc context nghiệp vụ cần ràng buộc với ciphertext. Nó không được mã hóa bí mật nhưng được đưa vào phép xác thực. Bên giải mã phải cung cấp đúng byte AAD trước khi đưa ciphertext vào Cipher.

Nếu ứng dụng tự chuyển object thành JSON rồi parse và serialize lại, thứ tự field, khoảng trắng hoặc Unicode normalization có thể thay đổi byte. Cần quy định dạng canonical và metadata được bảo vệ rõ ràng, tránh “dữ liệu logic giống nhau” nhưng tag khác vì byte khác.

### 8.3 Từ chối dữ liệu có tag sai không có nghĩa xác định được thủ phạm

GCM không phân biệt:

- attacker chủ động thay ciphertext;
- bản sao backup bị cắt cụt;
- lỗi storage truyền thiếu byte;
- lấy sai key từ keystore;
- đưa sai nonce;
- AAD không cùng ngữ cảnh.

Mọi trường hợp đều phải từ chối sử dụng dữ liệu chưa xác thực. Để phân tích nguyên nhân, cần log metadata không nhạy cảm, kiểm tra hạ tầng và quy trình khôi phục có kiểm soát; không xuất key hoặc plaintext để “debug nhanh”.

## 9. Bảng quyết định chọn API

| Nhu cầu | Cách tiếp cận |
| --- | --- |
| Giải mã một bản ghi GCM nhỏ và phải biết chắc tag đúng | Cipher.doFinal trực tiếp, bắt AEADBadTagException |
| Đọc một file lớn mã hóa thành một bản ghi GCM | Cipher.update/doFinal trực tiếp, staging không công bố trước final success |
| Phục vụ plaintext của từng chunk trước khi đến cuối file | Thiết kế giao thức AEAD theo record, xác thực từng record và tính đầy đủ của luồng |
| Đang dùng CipherInputStream rồi xem “không IOException” là thành công | Sửa thiết kế: đây không phải kết quả xác thực AEAD đáng tin |
| Dùng CipherInputStream trong tình huống không cần nhận lỗi integrity AEAD | Đánh giá theo mode và API contract cụ thể; không áp dụng kết luận rộng ngoài phạm vi |
| Input hợp lệ về Base64 nhưng tag không hợp lệ | Reject tại Cipher.doFinal; Base64 chỉ là mã hóa biểu diễn |
| Tag hợp lệ nhưng bản ghi đã được gửi từ hôm trước | Thêm freshness/anti-replay theo giao thức; tag không tự kiểm tra tính mới |

### 9.1 Những hiểu nhầm phổ biến

1. **“CipherInputStream luôn ném AEADBadTagException nếu tag sai.”** Không có bảo đảm đó theo JavaDoc.
2. **“Cứ readAllBytes rồi catch IOException là an toàn.”** Lỗi xác thực có thể bị lớp wrapper che mất.
3. **“close() sẽ báo lỗi tag còn thiếu.”** Không được coi đó là hợp đồng xác thực.
4. **“Cipher.update luôn trả plaintext ngay.”** Hành vi đệm phụ thuộc provider.
5. **“Provider của tôi hiện đệm tới cuối nên chắc mọi provider cũng vậy.”** Không nên dùng quan sát đó thay hợp đồng bảo mật.
6. **“Có doFinal ở cuối rồi, trước đó stream plaintext ra HTTP cũng được.”** Không: dữ liệu chưa xác thực đã được công bố.
7. **“Tag sai nhưng phần đầu plaintext trông hợp lệ, vẫn xử lý được.”** Không: toàn bộ bản ghi chưa vượt xác thực.
8. **“available() bằng 0 nghĩa là đã xác thực xong.”** Không: available không báo trạng thái tag/EOF.
9. **“Sai tag chỉ có thể do attacker sửa ciphertext.”** Không: key, nonce, AAD, lỗi lưu/truyền hoặc thiếu byte cũng là nguyên nhân.
10. **“Tôi đã dùng GCM nên không cần nonce policy.”** Không: nonce lặp với cùng khóa là lỗi nghiêm trọng.
11. **“Có thể tự cắt file GCM thành chunk và coi mỗi chunk đã được xác thực.”** Không: cần framing AEAD có tag riêng cho từng record và kiểm tra sự đầy đủ của luồng.
12. **“Base64 decode thành công nghĩa là ciphertext đáng tin.”** Không: phải xác thực GCM tag.

## 10. Tình huống thực tế

**Tình huống A — Hồ sơ khách hàng bị sửa trên disk.** Dịch vụ đọc qua CipherInputStream và nhận mảng byte rỗng hoặc thiếu, nhưng không thấy IOException; ứng dụng ghi đè hồ sơ bằng dữ liệu rỗng. Đây là lỗi **diễn giải tín hiệu thành công của stream**. Cần đổi sang Cipher.doFinal trực tiếp, từ chối bản ghi khi tag không hợp lệ và không commit bất kỳ dữ liệu chưa xác thực nào.

**Tình huống B — Giải nén trước kiểm tra tag.** Một pipeline giải mã GCM → giải nén → parse → nhập DB có thể xử lý byte chưa xác thực trước khi doFinal. Nếu tag sai ở cuối, tác động từ parser/DB có thể đã xảy ra. Giải pháp: stage plaintext chưa xác thực, verify toàn bộ trước khi cho logic nhạy cảm truy cập; đặt giới hạn dung lượng để tránh tấn công tài nguyên.

**Tình huống C — File hàng GB không thể readAllBytes.** Không cần nhét toàn bộ plaintext vào RAM. Dùng Cipher.update/doFinal trực tiếp và staging an toàn; chỉ đổi trạng thái file sang “đã xác thực” sau doFinal, rồi mới mở cho người dùng/ứng dụng. Nếu phải phát từng phần trước khi có tag cuối, thiết kế bản ghi AEAD riêng có liên kết thứ tự và dấu kết thúc.

**Tình huống D — Sai AAD sau khi chuyển tenant.** Ciphertext và tag không đổi, nhưng ứng dụng dùng AAD với tenant ID mới. doFinal ném AEADBadTagException. Đây là cơ chế bảo vệ đúng: ciphertext không được dùng sai ngữ cảnh. Không bỏ AAD để làm cho “giải mã thành công”.

**Tình huống E — InputStream bị ngắt giữa chừng.** Không thể lấy phần byte đã giải mã rồi coi là kết quả. Ứng dụng phải từ chối bản ghi không đầy đủ, xử lý I/O failure riêng, và không công bố artifact cho tới khi toàn bộ dữ liệu cùng tag được xác thực.

### 10.1 Bảng test thiết yếu

| Test | Kết quả đúng |
| --- | --- |
| Key + nonce + AAD + ciphertext/tag đều đúng | doFinal trả plaintext chính xác |
| Lật một bit của ciphertext | Từ chối, không publish plaintext |
| Lật một bit của tag | AEADBadTagException hoặc lỗi xác thực phù hợp |
| Đổi AAD | Từ chối, không dùng output |
| Đổi key hoặc nonce | Từ chối do xác thực không hợp lệ |
| Cắt bỏ một phần tag | Từ chối, không coi đó là file hợp lệ |
| Input thiếu version/nonce | Reject tại kiểm tra định dạng |
| Giải mã staging rồi tag fail | Xóa staging, không tạo output chính thức |
| Đọc source bị IOException | Fail closed, không công bố byte một phần |
| Thử nhiều provider/JDK được hỗ trợ | Luồng lỗi qua Cipher API vẫn được xử lý đúng |

Test quan trọng nhất không chỉ là “có exception”, mà còn là **không có side effect từ dữ liệu chưa xác thực**.

## 11. Tự kiểm tra kiến thức

**Câu 1.** Nếu CipherInputStream.readAllBytes() trả về bình thường, đã chắc tag AES-GCM hợp lệ chưa?

**Trả lời:** Chưa. JavaDoc cảnh báo CipherInputStream có thể bắt và không truyền lỗi integrity check ra ngoài.

**Câu 2.** Muốn bắt lỗi xác thực GCM rõ ràng trong Java 17 thì nên dùng gì?

**Trả lời:** Dùng Cipher trực tiếp, gọi doFinal với đúng key, nonce, AAD và ciphertext/tag; bắt AEADBadTagException và từ chối dữ liệu nếu lỗi.

**Câu 3.** Có thể dùng các byte Cipher.update trả về ngay cho nghiệp vụ không?

**Trả lời:** Không được xem đó là plaintext đã xác thực khi chưa có doFinal thành công. Cần giữ trong staging hoặc bộ đệm chưa công bố.

**Câu 4.** Tại sao close() không đủ?

**Trả lời:** Close chỉ hoàn tất vòng đời resource; nó không bảo đảm lỗi xác thực đã bị lớp wrapper giữ lại sẽ được truyền tới caller.

**Câu 5.** Tag sai có chứng minh chắc chắn có tấn công không?

**Trả lời:** Không. Dữ liệu hỏng, thiếu byte, sai key, nonce hoặc AAD đều có thể khiến doFinal thất bại.

**Câu 6.** Nếu file quá lớn để giữ toàn bộ trong RAM thì có phải quay lại CipherInputStream không?

**Trả lời:** Không. Có thể dùng Cipher.update/doFinal trực tiếp với staging an toàn; chỉ publish sau khi tag được xác thực.

**Câu 7.** Có thể cắt một ciphertext GCM lớn thành các chunk rồi xác thực từng chunk bằng tag cuối không?

**Trả lời:** Không. Muốn xác thực từng chunk độc lập cần thiết kế record AEAD với nonce/tag riêng, thứ tự và tính đầy đủ của luồng được bảo vệ.

**Câu 8.** Vì sao mã hóa AES-GCM vẫn cần quản lý nonce?

**Trả lời:** Tái sử dụng nonce với cùng key gây nguy cơ nghiêm trọng cho bí mật và toàn vẹn, dù API giải mã có báo lỗi tag đúng cách.

## Kết luận

**CipherInputStream là API đọc byte tiện lợi nhưng không phải tín hiệu xác thực AEAD đáng tin trong mọi trường hợp.** Khi bảo mật phụ thuộc vào việc phát hiện tag sai, dùng **Cipher.doFinal() trực tiếp**, kiểm soát key/nonce/AAD và từ chối dữ liệu khi lỗi. Với xử lý stream lớn, giữ plaintext trong vùng tạm chưa được tin cậy và chỉ công bố sau khi bước xác thực cuối cùng thành công.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="symmetric-encryption-012">6.12 Đóng gói IV, tham số, ciphertext, tag và chỉ sử dụng bản rõ sau khi xác thực thành công</a>

<details>
<summary>Xem chi tiết</summary>

# 6.12 Đóng gói IV, tham số, ciphertext, tag và chỉ sử dụng bản rõ sau khi xác thực thành công

Một bản mã an toàn phải được **lưu và truyền cùng những thông tin cần thiết để giải mã và xác thực đúng cách**. Với mã hóa xác thực như **AES-GCM**, các thành phần thường gồm: phiên bản định dạng, thuật toán/bộ tham số đã được chính sách cho phép, mã nhận diện khóa (*key ID*), IV/nonce, ciphertext, authentication tag và ngữ cảnh dữ liệu xác thực bổ sung (*AAD*, nếu có). Ở phía nhận, chỉ được **đưa bản rõ vào xử lý nghiệp vụ sau khi kiểm tra tag thành công**.

Điểm dễ bỏ sót: IV không phải khóa bí mật; tag không phải ciphertext; AAD không bị mã hóa; tiêu đề (*header*) của gói cũng phải được ràng buộc toàn vẹn nếu nó quyết định cách hiểu dữ liệu. Việc `Cipher.update(...)` có thể trả về byte trong một số chế độ/provider **không có nghĩa những byte đó đã được xác thực**. Trong AES-GCM, tính hợp lệ của toàn bộ ciphertext và AAD chỉ được xác lập khi phép kiểm tra tag ở `doFinal(...)` hoàn tất thành công.

Bài này dùng **Java 17+ và JCA/JCE** để giải thích hợp đồng byte, đề xuất một envelope có version, minh họa mã hóa/giải mã AES-GCM, thử sửa từng phần, và chỉ rõ cách tránh sử dụng bản rõ chưa xác thực trong API, file và pipeline streaming.

## 1. Mục tiêu học tập

Sau khi học, bạn có thể:

1. Phân biệt chính xác **IV/nonce**, các tham số thuật toán, **ciphertext**, **tag**, **AAD**, **key ID** và **khóa bí mật**.
2. Thiết kế định dạng binary envelope không mơ hồ, có version và giới hạn kích thước.
3. Hiểu Java `Cipher.getInstance("AES/GCM/NoPadding")`, `GCMParameterSpec`, `updateAAD` và `doFinal`, gồm byte đầu ra `ciphertext || tag` của ví dụ JCA.
4. Giải mã bằng cách parse có kiểm soát, lấy đúng key, xác thực AAD/header và **chỉ công bố bản rõ sau `doFinal` thành công**.
5. Nhận diện lỗi nonce trùng dưới cùng khóa, header có thể bị sửa, nhầm tag length đơn vị bit/byte, xử lý lỗi lộ thông tin và xuất bản rõ quá sớm.
6. Lập test cho đúng gói, sai tag, sai IV, sai AAD, sai key, cắt ngắn, dữ liệu dư, đổi version và concurrency.
7. Xác định giới hạn của cách làm với file lớn, stream, cipher provider, lưu trữ và vòng đời khóa.

## 2. Một encrypted envelope thực chất chứa những gì?

Giả sử dịch vụ Java cần lưu một tài liệu đã được mã hóa. Một gói có thể có dạng:

```text
+----------+---------+--------+-------------+----------------+------------+---------+
| Magic    | Version | Key ID | GCM params  | IV / nonce     | Ciphertext | Tag     |
+----------+---------+--------+-------------+----------------+------------+---------+
       \_________________ authenticated header _______________/
                     \_____________ AAD _____________/
                          (context nghiệp vụ khác cũng có thể làm AAD)
                                     \____ ciphertext+tag ____/
```

Đây là **sơ đồ logic**, không có nghĩa AAD tự xuất hiện dưới dạng đoạn byte riêng trong output của `Cipher.doFinal`. Bên mã hóa cung cấp AAD cho `Cipher.updateAAD(...)`; phía giải mã phải cung cấp **đúng cùng byte AAD theo cùng thứ tự** để xác minh tag. Nếu một metadata cần phục hồi sau nhiều năm, nó có thể được lưu trong header và đưa chính byte header đó làm AAD.

| Thành phần | Chức năng | Có phải bí mật không? | Điều cần kiểm tra |
| --- | --- | --- | --- |
| **Magic/version** | Nhận dạng định dạng, quy tắc giải mã | Không | Nhận đúng định dạng/version; các byte có ảnh hưởng an ninh phải được xác thực |
| **Algorithm/profile** | Chọn bộ thuật toán và tham số được phép | Không | Chỉ dùng allowlist đáng tin, không tự hạ cấp theo input |
| **Key ID** | Tìm khóa để giải mã trong kho khóa | Không nhất thiết | Giới hạn ID, quyền dùng khóa và vòng đời; xác thực ràng buộc ID với bản mã |
| **IV/nonce** | Giá trị độc nhất theo yêu cầu chế độ mã hóa | Thường không | Đúng độ dài, đúng ràng buộc **không tái sử dụng với cùng khóa** |
| **Ciphertext** | Nội dung đã mã hóa | Không được xem như bản rõ; vẫn cần bảo vệ truy cập | Kiểm tra cấu trúc, giới hạn độ dài, không sử dụng khi chưa xác thực |
| **Authentication tag** | Xác thực ciphertext và AAD | Không | Đúng độ dài, được kiểm trong `doFinal`; không tự bỏ qua |
| **AAD** | Ràng buộc metadata/ngữ cảnh không cần mã hóa | Không được AES-GCM mã hóa | Byte giống nhau giữa mã hóa/giải mã; bao gồm các trường tác động tới cách hiểu hoặc quyền |
| **Khóa bí mật** | Năng lực giải mã và tạo bản mã/tag hợp lệ | **Có** | **Không đóng gói chung với envelope**; lấy qua key store/KMS/HSM được kiểm soát |

Một encryption envelope *không tự động giải quyết phân quyền*. Kể cả khi tag hợp lệ, dịch vụ vẫn phải kiểm người dùng có quyền đọc tài liệu, đúng tenant, đúng hành động và đúng trạng thái nghiệp vụ.

### Vì sao cần version?

Không nên buộc ứng dụng phải đoán xem các byte đầu tiên là IV dài 12 hay 16 byte, tag dài bao nhiêu hay dùng thuật toán nào. Sau khi định dạng được triển khai, các bản mã cũ có thể tồn tại lâu hơn vòng đời của mã nguồn hiện tại. **Version** chỉ tới một đặc tả đủ chặt: thứ tự trường, encoding, độ dài, thuật toán, tag length, xử lý lỗi và phương pháp xác thực metadata.

Khi thay đổi profile, tạo version mới và có chính sách đọc dữ liệu cũ/luân chuyển khóa. Không tự “thử từng thuật toán cho tới khi giải mã ra thứ có vẻ là tiếng Việt”.

## 3. AES-GCM: ciphertext, tag và AAD được tạo ra ở đâu?

AES-GCM là chế độ **AEAD** (*Authenticated Encryption with Associated Data*): bảo mật bản rõ và tạo tag kiểm tra tính toàn vẹn của ciphertext cùng AAD. Hợp đồng API Java điển hình:

```java
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
cipher.updateAAD(aadBytes);
byte[] ciphertextAndTag = cipher.doFinal(plaintextBytes);
```

Đoạn trên **chỉ minh họa**, giả sử `key` là `SecretKey` AES, `iv` là byte[] phù hợp và `aadBytes`/`plaintextBytes` đã có. Khi dùng JCA AES-GCM theo cách này, output của `doFinal` chứa **ciphertext tiếp theo là authentication tag**; tag thường được nối ở cuối dữ liệu mã hóa. Trong profile minh họa: tag = **128 bit = 16 byte**.

Giải mã tương ứng:

```java
Cipher decipher = Cipher.getInstance("AES/GCM/NoPadding");
decipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
decipher.updateAAD(aadBytes);
byte[] verifiedPlaintext = decipher.doFinal(ciphertextAndTag);
```

Với ciphertext/tag/AAD/key/IV không hợp lệ, `doFinal` phải thất bại; nhiều provider Java báo `AEADBadTagException` (thuộc họ `BadPaddingException`), nhưng ứng dụng nên coi toàn bộ lỗi xác thực là **thất bại, không trả bất kỳ bản rõ nào**. Mỗi lần chạy cần đúng thứ tự `init -> updateAAD -> update/doFinal`; nhiều provider yêu cầu tất cả AAD phải được cung cấp **trước khi xử lý plaintext/ciphertext**.

### Phân biệt length và đơn vị

- `new GCMParameterSpec(128, iv)`: **128 là số bit tag**, không phải 128 byte.
- `iv.length == 12`: IV/nonce **12 byte = 96 bit**, kích thước được dùng phổ biến cho GCM.
- Với profile dùng tag 128 bit, ciphertext của bản rõ dài N byte cũng dài N byte, sau đó thêm tag 16 byte. Đây là quan hệ kích thước của AES-GCM không padding, **không** bao gồm header.
- Base64 hoặc hex chỉ là cách chuyển envelope nhị phân sang dạng văn bản; nó không tăng độ an toàn mật mã.
- `cipher.getIV()` có thể trả IV của cipher đã init; `cipher.getParameters()` có thể biểu diễn các tham số. Để lưu lâu dài và tương thích nhiều runtime, **nên quy định cách serialize rõ ràng** thay vì phụ thuộc vào một opaque encoding của `AlgorithmParameters`.

### Tag khác HMAC độc lập

Với AES-GCM, tag được tạo và kiểm tra bởi thuật toán AEAD gắn liền với ciphertext và AAD. **Không** bỏ tag rồi hy vọng giải mã trả văn bản trông hợp lý là đủ. Một MAC/HMAC độc lập là cơ chế khác với khóa/thiết kế/giao thức riêng; không thể nối tùy ý HMAC vào output AES-GCM rồi coi là cùng profile.

## 4. Một format binary đơn giản có giới hạn và version

Để dễ kiểm thử, bài sử dụng format **demo v1** sau, byte nối nhau đúng thứ tự:

```text
offset  size    field
0       4       magic = ASCII "GCM1"
4       1       version = 0x01
5       4       keyId = 7 (signed 32-bit big-endian, giá trị demo)
9       1       nonceLength = 12
10      12      nonce (ngẫu nhiên cho mỗi lần encrypt)
22      N       ciphertext (cùng độ dài với plaintext)
22+N    16      GCM authentication tag (128 bit)
```

Trong code Java, header cố định dài **22 byte**, nonce 12 byte và tag **16 byte**; envelope của plaintext 13 byte sẽ dài **22 + 13 + 16 = 51 byte**. `ByteBuffer` mặc định là big-endian; profile nên ghi rõ quy ước này dù chỉ có một trường `keyId` dài 4 byte.

**Đây là định dạng ví dụ**, không phải một chuẩn mạng được công nhận. Không dùng trực tiếp nó để xây hệ lưu trữ sản xuất mà bỏ qua thiết kế vòng đời key, nonce uniqueness ở quy mô triển khai, version migration, giới hạn kích thước, lỗi provider và phân quyền.

Những quyết định cần ghi trong spec thật:

1. **Magic và version:** byte giá trị cụ thể, version nào được chấp nhận, policy xử lý version cũ.
2. **Thuật toán:** với v1, `AES/GCM/NoPadding` và tag 128 bit được cấu hình **cố định**, không cho envelope tự yêu cầu thuật toán yếu hơn.
3. **Key ID:** biểu diễn và cách tra cứu đúng key trong key store/KMS, ai có quyền giải mã.
4. **IV:** độ dài 12 byte, nguồn tạo, cơ chế bảo đảm không trùng với cùng key, yêu cầu giới hạn số lần mã hóa.
5. **AAD:** byte của header và chuỗi context nghiệp vụ có cấu trúc; thứ tự và cách frame các trường phải xác định.
6. **Payload:** định dạng plaintext/ciphertext, giới hạn kích thước, byte order khi có trường số và cách xử lý file lớn.
7. **Tag:** 16 byte nằm cuối `ciphertextAndTag`; cần cả hai để giải mã.
8. **Lỗi:** bất kỳ kiểm tra format hay tag nào thất bại đều không tạo dữ liệu được tin cậy.

**Chú ý về header:** magic, version, key ID và nonce đến từ input **chưa xác thực** khi parser mới đọc. Chúng chỉ giúp định tuyến xử lý trong phạm vi đã cho phép; khi header được đưa vào AAD, tag thành công mới chứng minh các byte đó phù hợp với ciphertext và key. Không tạo đối tượng nghiệp vụ hay cấp quyền dựa trên header chưa xác thực.

## 5. Xác thực toàn bộ trước khi sử dụng bản rõ

Mối nguy quan trọng nhất của bài này là **dùng bản rõ trước khi kiểm tra tag hoàn tất**. Luồng an toàn với một envelope kích thước có giới hạn:

```text
receive envelope (không tin cậy)
         |
         v
kiểm format, version, độ dài, quota và khả năng cấp phát
         |
         v
parse header -> chọn key được phép, lấy IV, định nghĩa AAD
         |
         v
Cipher.init(DECRYPT_MODE, key, GCMParameterSpec)
         |
         v
updateAAD(đúng header và context đã thỏa thuận)
         |
         v
doFinal(toàn bộ ciphertext || tag)
       /   \
   lỗi      thành công
    |           |
không trả     plaintext đã xác thực
bản rõ          |
                 v
        kiểm schema/quyền nghiệp vụ
                 |
                 v
       mới lưu/trả/hiển thị/xử lý
```

`Cipher.doFinal(...)` trả về thành công là **điều kiện cần** để coi các byte được giải mã đã qua kiểm tra tag. Trong một số implementation, decryption theo từng khối với `update` có thể tạo output **trước khi tag cuối được kiểm**. Vì vậy, không đưa những byte đó ra HTTP response, file công khai, logger, parser có side effect hay hệ thống thanh toán.

### Những điều bị xem là “đã sử dụng bản rõ”

Không chỉ trả plaintext về client mới nguy hiểm. Các hành động sau cũng sử dụng bản rõ chưa xác thực nếu xảy ra trước `doFinal`:

- Ghi dữ liệu ra file mà thành phần khác có thể đọc.
- Ghi một bản ghi DB, phát Kafka/event hoặc thực hiện thanh toán dựa trên dữ liệu vừa giải mã.
- Parse và thực thi lệnh/biểu thức, gọi service phụ trợ hoặc truy vấn dựa trên nội dung chưa xác thực.
- Log bản rõ hoặc ghi vào trace/metrics có thể truy cập.
- Dùng plaintext để phân quyền hoặc chọn tài nguyên trước khi xác thực tag.

Quy tắc thực hành là **không publish**, **không commit side effect**, **không diễn giải như nguồn tin cậy** cho tới khi nhận được kết quả giải mã/xác thực hoàn chỉnh. Sau đó vẫn phải validate schema và kiểm quyền nghiệp vụ; một message hợp lệ về mật mã không tự biến hành động của người gửi thành được cấp phép.

### Vì sao “try/catch rồi vẫn trả byte” là lỗi?

```java
// MÃ SAI — minh họa anti-pattern, không phải chương trình độc lập
try {
    byte[] tentative = cipher.update(ciphertextChunk);
    response.write(tentative);  // Có thể phát bản rõ trước khi tag được kiểm tra
    cipher.doFinal(tagBytes);
} catch (GeneralSecurityException ex) {
    log.warn("Decryption failed");
}
```

Lỗi có thể được phát hiện **sau khi** dữ liệu nhạy cảm đã thoát khỏi ranh giới bảo vệ. Catch exception không thể thu hồi response, sự kiện hay DB write đã phát đi. Ví dụ này cũng đơn giản hóa `Cipher`/streaming nhằm làm rõ lỗi thiết kế: **không phụ thuộc vào provider có hay không có output sớm** để chứng minh hệ thống an toàn.

## 6. Java 17+ chạy được: tạo và xác minh AES-GCM envelope

Ví dụ sau dùng AES-256-GCM, nonce ngẫu nhiên 12 byte, tag 128 bit, header 22 byte, key ID demo cố định và context AAD. Nó **không trả plaintext cho caller trước khi `doFinal` xác thực thành công**. Mẫu giới hạn envelope tối đa 1.000.000 byte để từ chối dữ liệu quá lớn; đây chỉ là giới hạn demonstration, production phải chọn theo threat model và nhu cầu.

Lưu thành **`GcmEnvelopeDemo.java`**:

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

public class GcmEnvelopeDemo {
    private static final int MAGIC = 0x47434D31; // "GCM1"
    private static final byte VERSION = 1;
    private static final int KEY_ID = 7;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;
    private static final int HEADER_BYTES = 4 + 1 + 4 + 1 + NONCE_BYTES;
    private static final int MAX_ENVELOPE_BYTES = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    static byte[] frameContext(byte[] context) {
        return ByteBuffer.allocate(4 + context.length)
                .putInt(context.length).put(context).array();
    }

    static byte[] encrypt(SecretKey key, byte[] plaintext, byte[] context)
            throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] header = ByteBuffer.allocate(HEADER_BYTES)
                .putInt(MAGIC).put(VERSION).putInt(KEY_ID)
                .put((byte) NONCE_BYTES).put(nonce).array();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(header);
        cipher.updateAAD(frameContext(context));
        byte[] ciphertextAndTag = cipher.doFinal(plaintext);
        return ByteBuffer.allocate(header.length + ciphertextAndTag.length)
                .put(header).put(ciphertextAndTag).array();
    }

    static byte[] decrypt(SecretKey key, byte[] envelope, byte[] context)
            throws GeneralSecurityException {
        if (envelope == null || envelope.length < HEADER_BYTES + TAG_BYTES
                || envelope.length > MAX_ENVELOPE_BYTES) {
            throw new GeneralSecurityException("Invalid envelope size");
        }
        ByteBuffer input = ByteBuffer.wrap(envelope);
        int magic = input.getInt();
        byte version = input.get();
        int keyId = input.getInt();
        int nonceLength = Byte.toUnsignedInt(input.get());
        if (magic != MAGIC || version != VERSION || keyId != KEY_ID
                || nonceLength != NONCE_BYTES) {
            throw new GeneralSecurityException("Unsupported envelope");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        input.get(nonce);

        byte[] header = Arrays.copyOfRange(envelope, 0, HEADER_BYTES);
        byte[] ciphertextAndTag =
                Arrays.copyOfRange(envelope, HEADER_BYTES, envelope.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(header);
        cipher.updateAAD(frameContext(context));

        // Only this successful doFinal yields plaintext to the caller.
        return cipher.doFinal(ciphertextAndTag);
    }

    @FunctionalInterface
    interface Attempt {
        void run() throws GeneralSecurityException;
    }

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
        SecretKey key = generator.generateKey();
        byte[] message = "Hello AES-GCM".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "tenant-A/document-7".getBytes(StandardCharsets.UTF_8);

        byte[] packed = encrypt(key, message, aad);
        String verified = new String(decrypt(key, packed, aad), StandardCharsets.UTF_8);

        byte[] badTag = packed.clone();
        badTag[badTag.length - 1] ^= 1;
        byte[] badNonce = packed.clone();
        badNonce[HEADER_BYTES - 1] ^= 1;
        KeyGenerator anotherGenerator = KeyGenerator.getInstance("AES");
        anotherGenerator.init(256);
        SecretKey wrongKey = anotherGenerator.generateKey();
        byte[] truncated = Arrays.copyOf(packed, HEADER_BYTES + TAG_BYTES - 1);

        System.out.println("Header bytes: " + HEADER_BYTES);
        System.out.println("Nonce bytes: " + NONCE_BYTES);
        System.out.println("Tag bytes: " + TAG_BYTES);
        System.out.println("Ciphertext bytes: " + (packed.length - HEADER_BYTES - TAG_BYTES));
        System.out.println("Envelope bytes: " + packed.length);
        System.out.println("Verified plaintext: " + verified);
        System.out.println("Modified tag rejected: " +
                rejected(() -> decrypt(key, badTag, aad)));
        System.out.println("Modified nonce rejected: " +
                rejected(() -> decrypt(key, badNonce, aad)));
        System.out.println("Wrong AAD rejected: " +
                rejected(() -> decrypt(key, packed,
                        "tenant-B/document-7".getBytes(StandardCharsets.UTF_8))));
        System.out.println("Wrong key rejected: " +
                rejected(() -> decrypt(wrongKey, packed, aad)));
        System.out.println("Truncated envelope rejected: " +
                rejected(() -> decrypt(key, truncated, aad)));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 GcmEnvelopeDemo.java
java GcmEnvelopeDemo
```

**Stdout chính xác** (không in nonce/ciphertext ngẫu nhiên):

```text
Header bytes: 22
Nonce bytes: 12
Tag bytes: 16
Ciphertext bytes: 13
Envelope bytes: 51
Verified plaintext: Hello AES-GCM
Modified tag rejected: true
Modified nonce rejected: true
Wrong AAD rejected: true
Wrong key rejected: true
Truncated envelope rejected: true
```

### 6.1 Giải thích từng phần

**`encrypt`:**

1. Tạo nonce 12 byte mới bằng `SecureRandom` cho **mỗi phép mã hóa**. Với cùng một khóa AES-GCM, tuyệt đối không chủ động tái sử dụng nonce; xem mục 8 về yêu cầu uniqueness thực tế.
2. Tạo header chứa magic, version, key ID và nonce. Key ID ví dụ = 7; ở sản phẩm thật, ID dùng tìm secret key từ vùng quản lý khóa, không phải secret được copy vào header.
3. Khởi tạo `Cipher` với `AES/GCM/NoPadding`, key AES và `GCMParameterSpec(TAG_BITS, nonce)`.
4. `updateAAD(header)` bảo vệ những byte mô tả format; `updateAAD(frameContext(context))` bảo vệ ngữ cảnh (ví dụ tenant/document) mà không mã hóa chúng.
5. `doFinal(plaintext)` tạo `ciphertext || tag`. Ghép header và output này tạo envelope.

**`decrypt`:**

1. Từ chối envelope rỗng, quá ngắn để chứa header+tag, hoặc vượt kích thước cho phép **trước khi cấp phát hay giải mã lớn**.
2. Parse đúng byte header và kiểm allowlist profile (magic/version, ID minh họa, nonce length). Những kiểm tra ban đầu dùng input chưa xác thực; không dựa vào chúng để ra quyết định quyền nghiệp vụ.
3. Lấy đúng nonce, đúng byte header và toàn bộ phần `ciphertextAndTag` còn lại. Đưa cùng byte AAD theo cùng thứ tự.
4. Gọi **một** `cipher.doFinal(ciphertextAndTag)`, để provider kiểm tag. Method `decrypt` **chỉ trả byte[] sau khi doFinal thành công**; exception thoát ra nghĩa là không có bản rõ được công bố qua return.
5. Caller chuyển byte hợp lệ sang UTF-8 để hiển thị **sau khi xác thực**, đồng thời phải tiếp tục validate schema/phân quyền nếu dữ liệu đến từ người khác.

**Các test phản ví dụ:** đổi bit của tag, nonce trong header, context AAD, key hoặc cắt ngắn envelope đều bị từ chối. Cùng một message có thể cho ciphertext/tag khác nhau giữa các lượt vì nonce ngẫu nhiên; điều cần kiểm là decrypt đúng message và mọi thay đổi trái phép bị phát hiện.

### 6.2 Giới hạn của ví dụ

- `MAX_ENVELOPE_BYTES` là mức nhỏ cố ý để minh họa bounded input; không dùng `readAllBytes` cho dữ liệu không giới hạn.
- Ví dụ sinh key trong process và không lưu key. Trong hệ thống thật phải có key store/KMS, quyền gọi decrypt, version và rotation. Nếu chạy lại chương trình, key mới **không giải mã được envelope cũ**.
- Key ID = 7 bị cố định và chỉ phục vụ demo. Production cần resolver kiểm tra cấu hình/key ID, hạn chế quyền, logging an toàn và chịu được nhiều key version. Không xem ID do input cung cấp là đã được xác thực trước `doFinal`.
- Ví dụ chỉ xử lý buffer trong bộ nhớ; với lượng dữ liệu lớn, cần chiến lược cách ly bản rõ chưa xác thực và cơ chế chống lộ dữ liệu khi stream.
- Một tag hợp lệ cho context demo không chứng minh caller có quyền đọc đối tượng. Phải xác thực danh tính và phân quyền bằng metadata tin cậy, riêng với kiểm tra tag.
- Ví dụ không cố gắng bảo đảm việc mọi bản sao key/plaintext được xóa khỏi bộ nhớ JVM. Đặc tính này phụ thuộc runtime, provider, secret manager và thiết kế lưu trữ.

## 7. Đóng gói tag nối cuối so với tách thành trường riêng

Các thư viện và giao thức có thể biểu diễn tag theo hai cách:

**Dạng nối (ví dụ JCA AES-GCM):**

```text
header || ciphertext || tag
           \___________/
             doFinal output
```

Trong ví dụ, `Cipher.doFinal(plaintext)` trả byte chứa ciphertext và tag cuối; khi decrypt, gửi toàn bộ đoạn `ciphertext || tag` vào `doFinal`.

**Dạng trường riêng (một số SDK/API/giao thức):**

```text
envelope = { version, keyId, iv, ciphertext, tag }
```

Nếu API yêu cầu `ciphertext` và `tag` riêng, parser có thể tách 16 byte tag cuối sau khi đã kiểm độ dài; khi đưa lại cho JCA, ghép đúng thứ tự với ciphertext theo contract provider đang dùng. Không giả định mọi API AES-GCM đều trả cùng một kiểu đối tượng hoặc cách đặt tag.

### Vì sao không bỏ metadata “có thể tính lại”?

Sau khi ciphertext được lưu, key ID, IV, tag length và AAD context có thể quyết định khả năng giải mã. IV **không thể suy ra** từ ciphertext một cách tổng quát. Tag phải được giữ đầy đủ. Nếu mất thông tin ràng buộc AAD như tenant ID/schema version, tag có thể không xác minh được ở lần đọc sau dù key và ciphertext còn nguyên.

Với định dạng dài hạn, cần ghi rõ **version** và điều kiện tái tạo AAD; các trường metadata không tự động nằm trong tag nếu không được đưa đúng vào `updateAAD` hoặc plaintext được xác thực.

## 8. Nonce/IV: nguyên tắc khó bỏ qua nhất của GCM

**GCM yêu cầu IV/nonce độc nhất đối với cùng một khóa**. IV không cần bí mật, nhưng việc **tái sử dụng cùng nonce với cùng khóa** trong GCM có thể phá hủy bảo mật nghiêm trọng, ảnh hưởng cả tính bí mật và khả năng giả mạo. Đây là yêu cầu về cách triển khai, không phải điều kiện mà `GCMParameterSpec` tự bảo đảm.

- **Không** hard-code `new byte[12]` làm IV cho mọi lần encrypt.
- **Không** dùng timestamp đơn độc hoặc ID dễ trùng khi nhiều node/process cùng mã hóa với một khóa.
- **Không** tưởng `Cipher.init` của một instance khác có thể phát hiện mọi lần tái sử dụng IV đã xảy ra trên toàn hệ thống.
- **Không** dùng IV ngẫu nhiên mà bỏ qua giới hạn số message và xác suất va chạm khi một key được dùng ở quy mô lớn.
- **Có** chính sách cấp IV phù hợp với mô hình tải: 96-bit ngẫu nhiên chất lượng cao với giới hạn số lần mã hóa, hoặc counter/nonce allocation duy nhất được phối hợp đúng và chống rollback, tùy yêu cầu giao thức và khuyến nghị chuẩn GCM.
- **Có** key rotation, telemetry về giới hạn sử dụng, phân phối khóa giữa node và kế hoạch tránh reuse sau crash/restore snapshot.

Trong demo, `SecureRandom` tạo 12 byte mỗi lần. Điều này chỉ minh họa cách sinh ngẫu nhiên; với ứng dụng có tải cao, nhiều vùng/instance hoặc yêu cầu chứng minh không trùng, cần đánh giá chiến lược nonce trên **toàn bộ vòng đời của một key**.

**Đổi key nhưng dùng lại IV?** Yêu cầu uniqueness xét theo **cặp key + IV**. Tuy vậy, không vì thế mà làm key management hoặc IV allocator tùy tiện: phải biết chính xác key nào được dùng và tránh dùng cùng key khi counter bị reset.

## 9. AAD phải ràng buộc đúng metadata/ngữ cảnh nghiệp vụ

AAD là dữ liệu được xác thực nhưng không mã hóa. Với hệ đa tenant, một ciphertext hợp lệ cho tenant A không nên bị gắn sang tenant B mà vẫn được chấp nhận trong ngữ cảnh B. Ví dụ sử dụng:

- Header byte gồm version, key ID, nonce và profile.
- `tenantId`, `documentId`, mục đích sử dụng (ví dụ `document-storage:v1`) và các trường phân biệt nghiệp vụ.
- Thông tin phiên bản schema nếu cần để parse plaintext thống nhất.

Các trường này có thể được ghép bằng cấu trúc có **độ dài rõ ràng** (*length-prefix*) hoặc serialization chuẩn. Không chỉ nối chuỗi thiếu ranh giới, vì `("ab", "c")` và `("a", "bc")` đều cho `"abc"`. Mẫu `frameContext` thêm độ dài int32 cho một context; trong sản phẩm thật nếu context gồm nhiều field, cần định nghĩa đầy đủ việc frame từng field.

Điểm đặc biệt: **thứ tự AAD phải giống nhau**. Phía encrypt gọi `updateAAD(header)` rồi `updateAAD(frameContext(context))`; decrypt phải đưa cùng bytes theo cùng thứ tự. Tag có thể sai khi Unicode normalization, charset, thứ tự JSON field, số phiên bản hoặc khoảng trắng bị đổi. AAD nên dùng một canonical byte representation ổn định và có golden vectors.

Đừng truyền chuỗi tenant từ query param do người dùng tự sửa rồi dùng nó làm ngữ cảnh đáng tin. Tenant/document context phải được **liên kết với tài nguyên đã được xác thực và phân quyền** bằng metadata/identity thực tế; AAD chỉ giúp phát hiện tráo đổi so với chính context được cung cấp.

### Khi metadata thay đổi

Nếu `documentId`/tenant/metadata nằm trong AAD và bạn muốn đổi chúng, không thể tự sửa header hay context rồi mong tag vẫn hợp lệ. Cần quy trình **giải mã đã xác thực bằng context cũ -> xét quyền/quy tắc di chuyển -> mã hóa lại dưới context mới với IV mới**. Giữ hồ sơ migration và key ID/version phù hợp.

## 10. Kiểm tra format và lỗi xác thực là hai tầng khác nhau

Trước khi gọi mật mã, parser phải chịu trách nhiệm kiểm input do đối tác/người dùng kiểm soát:

1. **Giới hạn bytes tổng:** request/file tối đa, timeout và memory quota; không cấp phát dựa trên length tùy ý từ header.
2. **Đúng magic/version/algorithm:** nếu profile không hỗ trợ, từ chối. Không lấy transformation string trực tiếp từ dữ liệu không tin cậy.
3. **Đúng kích thước:** nonce 12, tag 16, envelope tối thiểu 22+16 byte trong demo; tổng các length không overflow khi cộng.
4. **Key ID hợp lệ:** đúng allowlist/namespace và quyền; lookup an toàn, không cho ID trỏ ra ngoài tenant/kho bí mật đã cấp.
5. **AAD có thể dựng chính xác:** không thả trôi context từ nguồn không tin cậy; có version và serialization.
6. **Không thêm byte tùy tiện:** xác định rõ toàn bộ envelope thuộc một thông điệp. Nếu format cho phép nhiều bản tin, phải có framing để xác định ranh giới.
7. **Tag được kiểm và không bị bỏ:** không trả content từ một chế độ giải mã “unauthenticated fallback” khi GCM thất bại.

Đây là kiểm tra **cú pháp và tài nguyên**, cần làm sớm để ngăn DoS. Nhưng magic/version/key ID/IV đã parse vẫn chưa được chứng minh là metadata thật trước khi xác thực tag. Sau `doFinal` thành công mới có bằng chứng toàn vẹn cho dữ liệu được đưa vào AAD và ciphertext.

### Xử lý exception

Tùy lỗi, Java có thể ném:

- `IllegalArgumentException` / lỗi parse nội bộ nếu đưa dữ liệu không hợp lệ vào API; cần chặn ở ranh giới và chuyển thành lỗi yêu cầu hợp lệ.
- `InvalidKeyException`, `InvalidAlgorithmParameterException` hoặc `NoSuchAlgorithmException` khi key/algorithm/parameter không phù hợp.
- `AEADBadTagException` (dẫn xuất `BadPaddingException`) khi kiểm tra tag thất bại.
- `GeneralSecurityException` là họ checked exception hữu ích cho API demo; ở production phân biệt logging nội bộ với phản hồi ngoài.

Không trả về **bản rõ một phần** trong bất kỳ nhánh lỗi nào. Không tự sửa tag, thử cắt tag, chuyển sang `AES/CBC/PKCS5Padding` hay chạy decrypt không xác thực chỉ vì muốn “cứu dữ liệu”. Trả lỗi ngoài theo chính sách nhất quán, tránh tạo oracle tiết lộ khác biệt qua mã lỗi, thời gian hoặc nội dung. Log request ID, key version và mã lỗi đã được làm sạch; không log raw secret, plaintext hay toàn bộ envelope nhạy cảm.

## 11. Lưu trữ và HTTP: không đẩy bản rõ chưa xác thực ra ngoài

### 11.1 File hoặc object nhỏ, có giới hạn rõ

Một chiến lược đơn giản:

1. Xác thực caller và quyền đọc object trước khi làm việc với key hoặc cấp phát lớn.
2. Đọc envelope vào bộ nhớ **chỉ khi kích thước nằm trong giới hạn**.
3. Parse header, gọi `decrypt` như mẫu và giữ `byte[]` kết quả trong phạm vi xử lý hạn chế.
4. Khi `doFinal` trả về thành công, xác thực thêm định dạng nội dung/authorization liên quan.
5. Sau đó mới gửi response hoặc ghi dữ liệu ra nơi đã được chỉ định.

Không dùng `ByteArrayOutputStream` chung rồi ghi từng output chưa xác thực thành response đang mở. Không truyền byte chưa xác thực cho parser tự động ghi DB/event.

### 11.2 Dữ liệu lớn và streaming AEAD

Với file hàng GB, giữ tất cả ciphertext và plaintext trong heap có thể không khả thi; nhưng giải mã streaming AEAD có một vấn đề: **tag thường chỉ biết hợp lệ ở cuối**. Không thể vừa phát bản rõ ra một hệ thống không thể thu hồi, vừa bảo đảm “chỉ sau khi xác thực” bằng cách đơn giản gọi `update` rồi `doFinal`.

Các phương án thiết kế cần đánh giá:

- **Encrypted file nhỏ/vừa:** áp giới hạn kích thước và xác thực toàn bộ trước khi trả. Đơn giản nhất để kiểm tra.
- **Vùng staging riêng, không thể truy cập:** nếu implementation cho plaintext sớm, chỉ ghi vào vùng tạm được cô lập, bảo vệ quyền truy cập, không kích hoạt side effect; **chỉ sau khi doFinal thành công** mới công bố hoặc thực hiện thao tác commit nguyên tử. Khi lỗi, xóa staging và không phát sự kiện. Cần tính đến crash, quyền truy cập temp file, backup, dọn dẹp và rò rỉ trước commit.
- **Chia dữ liệu thành record/chunk AEAD độc lập có cấu trúc:** mỗi chunk có nonce/sequence và tag riêng, xác thực từng record trước khi dùng, bảo vệ thứ tự, số lượng, EOF/truncation, metadata và một final commitment/manifest được xác thực. Không chỉ giải mã thành công từng chunk mà mặc định toàn bộ file đã đầy đủ; thiết kế protocol và nonce allocation rất quan trọng.
- **Cơ chế hoặc thư viện streaming AEAD được thiết kế và review riêng:** dùng abstraction có hợp đồng xác thực rõ ràng, thay vì tự sáng tạo format nối tag.

Đừng dựa trên `CipherInputStream` như bảo đảm mọi lỗi integrity sẽ được truyền nguyên vẹn tới caller. Tài liệu JDK cảnh báo class này có thể bắt một số exception kiểm tra integrity (ví dụ `BadPaddingException`) và không tái ném; vì vậy nó có thể **không phù hợp cho authenticated decryption nếu bắt buộc báo lỗi integrity đầy đủ**. Hãy chọn API được kiểm chứng và kiểm soát chính xác nơi tag được xác nhận.

### 11.3 Những loại side effect phải trì hoãn

- HTTP response body, download link hoặc object storage công khai.
- Event bus, queue, email/webhook hoặc lời gọi side-effect đến service khác.
- DB commit, update trạng thái đơn hàng, cấp quyền hoặc cập nhật số dư.
- Parser có thể thực hiện lệnh hoặc kích hoạt logic dựa trên plaintext.
- Log/trace, crash report và cache chứa dữ liệu nhạy cảm.

Nếu một ứng dụng cần hiển thị tiến trình tải xuống, có thể thông báo số byte ciphertext đã nhận hoặc trạng thái verification, nhưng không đánh dấu nội dung là xác thực khi chưa đọc và kiểm tra xong toàn bộ tag/commitment của giao thức.

## 12. So sánh AES-GCM với các định dạng mã hóa cũ

Một số hệ thống vẫn lưu:

```text
AES-CBC: IV || ciphertext
AES-CBC + HMAC: header || IV || ciphertext || macTag
```

**AES-CBC không có tag xác thực built-in.** Một `IV || ciphertext` không có MAC/AEAD không đáp ứng yêu cầu phát hiện thay đổi. Nếu hệ thống cũ dùng **Encrypt-then-MAC** đúng chuẩn, phải tính MAC trên cấu trúc bao gồm các trường liên quan (thường gồm version/IV/ciphertext và ngữ cảnh), **kiểm MAC trước khi decrypt CBC**, rồi mới giải mã và xử lý padding. Không trả chi tiết lỗi padding cho attacker để tránh padding-oracle. Phần triển khai này đòi hỏi key separation, thuật toán và protocol được thẩm định; không ghép HMAC tùy tiện vào CBC rồi gọi là AEAD an toàn.

Với thiết kế mới, ưu tiên AEAD chuẩn như AES-GCM khi phù hợp yêu cầu và quản lý nonce/key được bảo đảm. Không “fallback sang CBC” nếu GCM tag sai, cũng không chấp nhận một version cũ ngoài chính sách chỉ vì nó giải mã được.

## 13. Vận hành: key ID, rotation và migration

Một envelope dài hạn thường cần biết đã dùng **key nào**, nhưng **không chứa key**. Khi mã hóa, service lấy key theo chính sách đã được cấp, ghi key ID/version vào header và đưa header vào AAD. Khi giải mã, service parse ID để **định tuyến tới key được phép**, rồi tag thành công xác nhận header đã được ràng buộc đúng với bản mã.

Một số yêu cầu:

- **Giới hạn kho key và identity:** client không được chỉ định đường dẫn file key, URI tùy ý hoặc key thuộc tenant khác bằng cách thay key ID.
- **Key rotation:** key mới để encrypt; các key cũ còn được phép decrypt trong thời gian migration có điều kiện, sau đó thu hồi theo chính sách.
- **Re-encrypt:** decrypt+authenticate bằng key/IV/AAD cũ, rồi encrypt bằng key mới và **nonce mới**; không sao chép nguyên ciphertext/tag và chỉ thay key ID.
- **Metadata gắn kết:** key ID, version, tenant, document ID phải ổn định hoặc được di chuyển có chủ đích; thay AAD làm tag cũ không hợp lệ.
- **Không phụ thuộc raw key bytes nằm trong ứng dụng** nếu KMS/HSM có thể hỗ trợ cơ chế theo yêu cầu; khả năng dùng AES-GCM trực tiếp hay dạng envelope encryption phải phù hợp API của nhà cung cấp.
- **Audit:** ghi nhận key version, hành động, tài nguyên, kết quả thành công/thất bại có làm sạch; không log key, plaintext, full AAD nhạy cảm hoặc dữ liệu có thể giúp suy ra bí mật.

Thực tế AES-GCM không giải quyết quyền truy cập storage. IAM, quyền service, tenant isolation, bảo vệ backup và phát hiện truy cập bất thường vẫn cần thiết.

## 14. Lỗi phổ biến và cách khắc phục

| Sai lầm | Hậu quả | Thực hành đúng |
| --- | --- | --- |
| Chỉ lưu ciphertext, bỏ IV/tag | Không thể giải mã/xác thực đáng tin cậy | Lưu envelope đầy đủ, có version và các tham số cần thiết |
| Đóng gói key bí mật cùng ciphertext | Ai có gói sẽ giải mã được | Key ở key manager/KMS/HSM, envelope chỉ chứa key ID |
| Nhầm tag 128 **bit** thành 128 **byte** | Parse sai hoặc lỗi provider | Quy định tag bits khi `GCMParameterSpec`, byte khi lưu |
| Tái sử dụng IV với cùng key | Phá vỡ bảo mật GCM nghiêm trọng | Chính sách uniqueness trên toàn vòng đời key |
| Dùng nonce làm password hoặc secret | Nhầm mục đích, key không được bảo vệ | Nonce có thể công khai, key phải bí mật |
| Không đưa header/ngữ cảnh vào AAD | Có thể tráo metadata ngoài phạm vi xác thực | Bao phủ các trường tác động tới ý nghĩa và quyền |
| Coi field parse trước decrypt là đáng tin | Attacker tự thay version/key ID/tenant | Allowlist và giới hạn trước, chỉ tin sau xác thực và kiểm quyền |
| Gọi `update` rồi stream plaintext ra HTTP | Rò plaintext trước khi tag được kiểm | Buffer/staging cô lập hoặc record AEAD với contract rõ |
| Bắt `AEADBadTagException` rồi vẫn trả kết quả partial | Chấp nhận dữ liệu bị sửa | Fail closed, không publish bất kỳ plaintext nào |
| Dùng `CipherInputStream` mà không xét xử lý lỗi integrity | Có thể che giấu lỗi tag/padding tùy hợp đồng | Dùng phương pháp xác thực hoàn chỉnh có thể kiểm chứng |
| Serialize lại JSON/AAD trước decrypt | Thay byte AAD, tag thất bại | Canonical byte format/version cố định |
| Fallback thuật toán không xác thực sau lỗi | Mở đường downgrade/oracle | Version allowlist, từ chối khi tag sai |
| Key ID không giới hạn | Có thể định tuyến decrypt ngoài phạm vi | Giới hạn key namespace, quyền IAM/KMS và tenant |
| Không giới hạn chiều dài envelope | Có thể gây cấp phát quá lớn/DoS | Check length, quota, timeout trước cấp phát |
| Cho tag hợp lệ đồng nghĩa caller được phép | Lỗ hổng phân quyền | Kiểm danh tính/ACL/tenant/action riêng |
| Tag hợp lệ được dùng lại nhiều lần không kiểm | Replay hành động | Protocol có nonce/sequence/time + idempotency nếu nghiệp vụ cần |
| Thay key ID hoặc AAD mà giữ tag cũ | Tag không còn xác thực | Giải mã xác thực trước, mã hóa lại đúng context/key |

## 15. Bộ kiểm thử cần có

Thiết kế ít nhất các trường hợp sau, dùng key và dữ liệu **kiểm thử**, không dùng secret sản xuất:

| Ca | Thao tác | Kết quả mong đợi |
| --- | --- | --- |
| Round trip | Encrypt rồi decrypt với cùng key/IV trong envelope và đúng AAD | Trả nguyên byte plaintext; chỉ sau verify |
| Tag bị sửa | Đổi một bit tag cuối | Giải mã thất bại, không trả plaintext |
| Ciphertext bị sửa | Đổi một bit trong phần ciphertext | Tag thất bại, không có side effect |
| Nonce bị sửa | Đổi byte nonce trong header | Bị từ chối; tag không khớp |
| AAD thay đổi | Đổi tenant/document context | Bị từ chối; không diễn giải plaintext |
| Key khác | Dùng key khác cùng thuật toán | Bị từ chối |
| Header khác | Thay version/key ID/magic | Reject theo profile hoặc tag; không fallback |
| Cắt ngắn | Thiếu header, IV, ciphertext hoặc tag | Reject cú pháp hoặc xác thực, không đọc vượt buffer |
| Dữ liệu dư | Gắn thêm byte ở cuối envelope | Không được lặng lẽ bỏ qua; test phải đảm bảo bị từ chối khi tag sai |
| Plaintext rỗng | Encrypt 0 byte | Envelope vẫn có tag, decrypt đúng mảng rỗng |
| Plaintext Unicode | Encode UTF-8 có quy tắc rõ ràng | Round trip nguyên byte; không đổi charset ngầm |
| Header/AAD canonical | Hai runtime với cùng context được frame giống nhau | Byte AAD và xác minh khớp |
| Nhiều lần encrypt | Cùng key và plaintext với nonce mới | Không dùng lại nonce; ciphertext/tag có thể khác |
| Key rotation | Version cũ còn được cho phép hoặc đã thu hồi | Hành vi theo chính sách, không thử key bừa bãi |
| Envelope quá lớn | Input vượt quota | Reject trước khi cấp phát dữ liệu lớn |
| Streaming lỗi I/O | Nhận 90% ciphertext rồi lỗi | Không commit/publish bản rõ |
| Concurrency | Nhiều request mã hóa/giải mã đồng thời | Mỗi request dùng `Cipher` riêng, không trộn trạng thái |

**Một kiểm thử bảo mật quan trọng hơn việc so tag:** đặt một sink giả (HTTP response, DB commit, event publisher) và cho decrypt nhận ciphertext/tag sai. Xác nhận **sink không hề nhận bản rõ nào**, kể cả trước exception. Đây mới kiểm tra được yêu cầu “chỉ sử dụng bản rõ sau khi xác thực thành công”.

Để kiểm tính tương thích đa ngôn ngữ, dùng **golden vectors độc lập** của algorithm/profile: biết key, IV, AAD, plaintext và expected ciphertext/tag trong môi trường test. Đừng chỉ chứng minh encrypt/decrypt của cùng một method tương thích với chính nó.

## 16. Bài thực hành

### Bài 1 — Kiểm ranh giới byte của envelope

Dùng `GcmEnvelopeDemo`, ghi lại các byte offset của magic/version/key ID/nonce/ciphertext/tag. Với plaintext `Hello AES-GCM` (13 byte UTF-8), giải thích vì sao header = 22, tag = 16, envelope = 51 byte. Thử sửa một byte ở vị trí 22 trong ciphertext và xác nhận không trả bản rõ. Thử sửa byte ở vị trí cuối và chỉ ra đó là tag.

### Bài 2 — Bảo vệ tenant và key ID

Giả sử storage có metadata `tenantId`, `documentId`, `keyVersion`. Thiết kế AAD frame có length-prefix và test tráo metadata. Chứng minh context lấy từ metadata/identity đã được tin cậy; nếu user thay tenant trong request, service không được chuyển sang key của tenant khác chỉ vì header.

### Bài 3 — Từ byte[] sang lưu trữ thực tế

Thiết kế một phương thức `readAndVerify` cho object có giới hạn 1 MiB:

1. Kiểm caller có quyền đọc object bằng metadata đáng tin.
2. Đọc envelope có quota.
3. Parse version/ID/nonce và lấy đúng key.
4. Xác thực tag toàn bộ.
5. Chỉ sau thành công mới parse JSON/binary, ghi audit và trả response.
6. Khi sai tag, sai key, lỗi I/O hoặc metadata bị tráo, không trả bản rõ và không phát event side effect.

### Bài 4 — Thiết kế cho file lớn

Giả sử cần giải mã file 4 GiB và phục vụ download. Giải thích vì sao không nên dùng `Cipher.update` rồi ghi thẳng byte vào HTTP response trước `doFinal`. Đề xuất vùng staging cô lập có atomic publish sau verify, hoặc giao thức record AEAD có sequence/tag/EOF commitment; mô tả cách xử lý crash, retry, nonce uniqueness và file bị cắt cụt.

**Tiêu chí hoàn thành:** bạn có thể khôi phục chính xác IV, version, key ID, ciphertext, tag và AAD từ envelope; biết byte nào thực sự được xác thực; có bằng chứng test rằng tag sai khiến toàn bộ bản rõ bị từ chối; đồng thời phân biệt được **mã hóa đã xác thực** với **quyền truy cập**.

## 17. Tài liệu tham khảo

- [Java SE 17 — Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): `init`, `updateAAD`, `update`, `doFinal` và AEAD.
- [Java SE 17 — GCMParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): định nghĩa tag length theo bit và IV.
- [Java SE 17 — AEADBadTagException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/AEADBadTagException.html): lỗi xác thực tag.
- [Java SE 17 — CipherInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/CipherInputStream.html): lưu ý về exception khi xác thực integrity.
- [NIST SP 800-38D — Galois/Counter Mode (GCM)](https://csrc.nist.gov/pubs/sp/800/38/d/final): mô hình AEAD và yêu cầu IV/nonce.
- [OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): chọn chế độ, key management, nonce.


</details>

- [Quay lại đầu trang](#back-to-top)
