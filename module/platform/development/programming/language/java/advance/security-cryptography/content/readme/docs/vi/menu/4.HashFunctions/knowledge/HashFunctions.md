<a id="back-to-top"></a>

# 4. Hàm băm

## Menu
- [4.1 Tính một chiều, tiền ảnh và va chạm của hàm băm mật mã](#hash-functions-001)
- [4.2 MessageDigest với SHA-2, SHA-3 và việc kiểm tra khả năng hỗ trợ](#hash-functions-002)
- [4.3 Chu trình update, digest, reset và xử lý dữ liệu nhiều phần](#hash-functions-003)
- [4.4 Băm file lớn bằng luồng hoặc ByteBuffer](#hash-functions-004)
- [4.5 Phân biệt checksum, Object.hashCode và digest mật mã](#hash-functions-005)
- [4.6 So sánh digest bằng MessageDigest.isEqual và phạm vi bảo đảm của API](#hash-functions-006)
- [4.7 Giới hạn của digest công khai trước dữ liệu bị thay thế và mật khẩu yếu](#hash-functions-007)

## <a id="hash-functions-001">4.1 Tính một chiều, tiền ảnh và va chạm của hàm băm mật mã</a>

<details>
<summary>Xem chi tiết</summary>

# 4.1. Tính một chiều, tiền ảnh và va chạm của hàm băm mật mã

Một hàm băm mật mã (cryptographic hash function) biến dữ liệu có độ dài bất kỳ thành một **giá trị băm (digest) có độ dài cố định**. Ta thường dùng SHA-256 để tính dấu vân tay cho một tệp, kiểm tra dữ liệu đã đổi hay chưa, hoặc làm thành phần bên trong chữ ký số và HMAC. Nhưng để hiểu vì sao một giá trị băm có thể dùng trong các cơ chế bảo mật, phải phân biệt ba thuộc tính: **kháng tiền ảnh**, **kháng tiền ảnh thứ hai** và **kháng va chạm**.

Ví dụ, SHA-256 của chuỗi UTF-8 `abc` là:

```text
Input bytes (UTF-8): 61 62 63
SHA-256 (hex):
ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
```

Kết quả gồm 32 byte, thường được trình bày thành **64 chữ số hex**. Một chuỗi khác thường tạo digest khác, nhưng không thể khẳng định rằng *mọi* hai chuỗi khác nhau đều có hash khác nhau.

Sau bài học, bạn cần:
- Giải thích hàm băm “một chiều” có nghĩa là khó tìm đầu vào từ digest, không phải không thể về mặt toán học.
- Phân biệt rõ bài toán tìm **preimage**, **second preimage** và **collision**, đặc biệt là những thông tin kẻ tấn công được cho trước.
- Ước lượng chi phí tấn công lý tưởng với đầu ra `n` bit: cỡ `2^n` cho tiền ảnh/tiền ảnh thứ hai và `2^(n/2)` cho va chạm.
- Hiểu nghịch lý sinh nhật (birthday bound), tác động của rút ngắn digest, và vì sao dữ liệu ít entropy vẫn có thể bị đoán dễ dàng.
- Áp dụng SHA-256 bằng Java 17+, đọc kết quả dưới dạng byte/hex, và không nhầm hash thường với cơ chế xác thực hay lưu mật khẩu.

## 1. Hàm băm mật mã là gì?

Ký hiệu tổng quát:

```text
             H
Message M --------> Digest h = H(M)
(any length)        (fixed n bits)
```

Mô hình lý tưởng hóa:

\[
H:\{0,1\}^{*}\rightarrow\{0,1\}^{n}
\]

Trong đó:
- `M` là một dãy **byte/bit**, không mặc nhiên là ký tự; cách mã hóa UTF-8, chuẩn hóa hay serialize JSON ảnh hưởng trực tiếp đến digest.
- `n` là độ dài digest tính bằng bit. Với SHA-256, `n = 256`; với SHA-512, `n = 512`.
- `H` là thuật toán công khai, xác định: cùng đúng một dãy byte thì cho cùng digest. Thuật toán SHA-256 không cần một bí mật riêng để hoạt động.

Một hàm băm mật mã phù hợp thường được thiết kế để có các đặc điểm:
1. **Tính xác định (deterministic):** `H(M)` luôn cho cùng kết quả nếu thuật toán và byte đầu vào không đổi.
2. **Hiệu quả khi tính xuôi:** với thông điệp và thuật toán, tính digest có chi phí chấp nhận được.
3. **Kháng tiền ảnh:** khi chỉ biết một digest mục tiêu, khó tìm một thông điệp băm ra giá trị đó.
4. **Kháng tiền ảnh thứ hai:** khi biết một thông điệp cụ thể, khó tìm một thông điệp *khác* có cùng digest.
5. **Kháng va chạm:** khó tự tìm *bất kỳ cặp* thông điệp khác nhau nào có cùng digest.

Các mục 3–5 là **mục tiêu an toàn trước đối thủ có tài nguyên tính toán hữu hạn**, không phải bảo đảm tồn tại duy nhất một thông điệp cho mỗi digest. Hiện tượng avalanche — đổi một ít bit đầu vào thường làm nhiều bit đầu ra đổi — là đặc tính mong muốn, nhưng chỉ nhìn thấy avalanche **không đủ chứng minh** ba mức kháng tấn công trên.

### 1.1. Vì sao va chạm chắc chắn tồn tại?

Đầu vào có thể dài tùy ý, trong khi đầu ra chỉ có `2^n` giá trị khác nhau. Theo nguyên lý Dirichlet (pigeonhole principle), có nhiều thông điệp khác nhau chia sẻ cùng digest. Nói cách khác:

\[
\exists M_1 \neq M_2 : H(M_1) = H(M_2)
\]

**Va chạm tồn tại về mặt toán học**; câu hỏi bảo mật thực tế là đối thủ có thể **tìm được** một va chạm hữu ích với chi phí khả thi hay không.

Đừng hiểu câu “xác suất va chạm thấp” thành “không tồn tại va chạm”, và cũng đừng hiểu “có va chạm” thành “hàm băm bị phá”. Một hàm băm 256 bit tốt vẫn có vô số cặp va chạm, nhưng việc chủ động tìm cặp bằng tấn công tổng quát dự kiến rất tốn kém.

## 2. Ba bài toán: preimage, second preimage và collision

Hãy đặt tên cho digest mục tiêu là `h`, thông điệp gốc là `M` và thông điệp mới là `M'`.

| Thuộc tính | Đối thủ được cho trước | Đối thủ muốn tìm | Điều kiện thành công |
| --- | --- | --- | --- |
| **Kháng tiền ảnh (preimage resistance)** | Một digest `h` | Bất kỳ `M` | `H(M) = h` |
| **Kháng tiền ảnh thứ hai (second-preimage resistance)** | Một thông điệp `M` cụ thể | Một `M'` khác `M` | `H(M') = H(M)` |
| **Kháng va chạm (collision resistance)** | Hàm `H`, **không bị cố định trước M** | Hai thông điệp `M_1, M_2` khác nhau | `H(M_1) = H(M_2)` |

Khác biệt quyết định nằm ở **mức tự do được chọn đầu vào**:

- **Preimage:** digest đã được chọn; đối thủ cần tìm *một lời giải* cho giá trị đó.
- **Second preimage:** thông điệp gốc đã cố định; đối thủ không được tùy ý chọn cả hai thông điệp.
- **Collision:** đối thủ được tự chọn **cả hai** thông điệp, nên có thể thử rất nhiều ứng viên và tìm bất kỳ cặp trùng nhau nào.

### 2.1. Hình dung từng thách thức

**Thử thách A — tiền ảnh:** Người kiểm tra đưa digest `h = SHA-256(secretMessage)`, không tiết lộ secretMessage. Nhiệm vụ là tìm **bất cứ** dữ liệu `X` sao cho `SHA-256(X) = h`. `X` không nhất thiết đúng bằng secretMessage ban đầu.

**Thử thách B — tiền ảnh thứ hai:** Người kiểm tra công khai một file hợp đồng `M` đã được chấp thuận. Đối thủ phải tạo một file `M' != M` sao cho `SHA-256(M') = SHA-256(M)`. Giữ nguyên digest của một thông điệp **cố định** là điều kiện quan trọng.

**Thử thách C — va chạm:** Đối thủ tự tạo hai tệp `A` và `B` bất kỳ, miễn `A != B` nhưng có cùng digest. Nếu đối thủ còn điều khiển được ý nghĩa của hai tệp (ví dụ một tệp trông vô hại và một tệp độc hại), đây có thể là tiền đề cho một cuộc tấn công thay thế tài liệu trong giao thức ký hoặc phê duyệt thiết kế sai.

**Không nhầm:** có một collision `H(A)=H(B)` **không tự động** cho phép tìm second preimage của một tài liệu khác `M` đã được chọn từ trước. Kẻ tấn công sở hữu cặp `A,B` không được giả định rằng `A=M`.

### 2.2. Ba tính chất liên quan nhưng không đồng nhất

Kháng va chạm là yêu cầu mạnh trong nhiều thiết kế ký/định danh nội dung vì đối thủ có thể tìm cặp dữ liệu trước khi một bên đáng tin xử lý một trong hai. Kháng tiền ảnh và tiền ảnh thứ hai bảo vệ các kịch bản **mục tiêu đã cố định**.

Không suy ra “hàm băm một chiều ⇒ chống va chạm” hoặc “dễ tìm va chạm ⇒ tìm được tiền ảnh bất kỳ”. Đây là các trò chơi an ninh khác nhau, có phương pháp tấn công và mô hình lựa chọn đầu vào khác nhau.

## 3. Độ khó lý tưởng theo số bit

Với hàm băm `n` bit được mô hình như ánh xạ ngẫu nhiên đủ tốt, đầu vào/đầu ra được chọn trong điều kiện phù hợp và chưa có điểm yếu cấu trúc nào, độ khó tổng quát được ước lượng:

| Bài toán | Độ khó tổng quát điển hình | Nếu digest 256 bit |
| --- | --- | --- |
| Tìm preimage của mục tiêu ngẫu nhiên | Xấp xỉ `2^n` lần thử | `2^256` |
| Tìm second preimage của thông điệp đã cố định | Xấp xỉ `2^n` lần thử | `2^256` |
| Tìm collision của hai thông điệp tùy chọn | Xấp xỉ `2^(n/2)` lần thử | `2^128` |

Đây là **ước lượng trong mô hình lý tưởng**, không phải bằng chứng mọi hàm băm thực tế đạt đúng các mốc đó; nội dung dài đặc biệt, cấu trúc thuật toán, nhiều mục tiêu và các kiểu tấn công cụ thể có thể làm thay đổi công việc cần thiết. Ngoài ra, **mật khẩu hoặc mã PIN có ít khả năng** sẽ bị đoán theo không gian đầu vào nhỏ, chứ không phải đợi tới `2^256` thử.

### 3.1. Vì sao va chạm tìm nhanh hơn: birthday bound

Giả sử thử `q` đầu vào khác nhau và quan sát `q` digest `n` bit có phân phối đều. Có khoảng:

\[
\frac{q(q-1)}{2}
\]

cặp digest có thể so sánh. Mỗi cặp có xác suất khoảng `1/2^n` để trùng nhau trong mô hình ngẫu nhiên. Khi `q` còn nhỏ so với không gian đầu ra, xác suất có ít nhất một collision xấp xỉ:

\[
P(\text{collision}) \approx 1 -
\exp\left(-\frac{q(q-1)}{2^{n+1}}\right).
\]

Khi `q` đạt mức cùng bậc với `2^{n/2}`, xác suất va chạm trở nên đáng kể. Để đạt xấp xỉ **50%**, cần khoảng `1,177 \times 2^{n/2}` mẫu ngẫu nhiên; hệ số 1,177 không đổi bậc độ phức tạp.

Ví dụ với digest chỉ **16 bit**: có `2^16 = 65.536` kết quả. Chỉ cần khoảng **301 mẫu** là xác suất thấy ít nhất một cặp trùng nhau đạt khoảng 50% trong mô hình đều. Đây là lý do phép thử Java ở phần sau dễ tìm collision ở **2 byte đầu** của SHA-256.

Độ mạnh chống va chạm của hash `n` bit theo cách tính tổng quát thường chỉ ở mức khoảng **`n/2` bit an ninh**, trong khi độ mạnh preimage lý tưởng có thể khoảng **`n` bit**. Khi thiết kế ID chống đối thủ chủ động hoặc rút ngắn digest để lưu trữ, đừng nhầm “128-bit hash” với “128-bit chống collision”; digest 128 bit chỉ có birthday bound ở cỡ `2^64`.

### 3.2. Tìm collision không đồng nghĩa với đoán được nội dung

Nếu biết `H(password)`, đối thủ thường thử các mật khẩu khả dĩ theo từ điển rồi băm và so sánh. Đây là một kiểu **đoán preimage trong tập đầu vào entropy thấp**, khác với thuật toán tìm collision tự do.

Một PIN 4 chữ số có tối đa 10.000 ứng viên. Dù đem qua SHA-256, entropy của PIN **không tăng** lên 256 bit. Đối thủ có digest và khả năng kiểm tra ngoại tuyến có thể thử toàn bộ không gian nhỏ này rất nhanh nếu chỉ dùng SHA-256 trực tiếp.

## 4. “Một chiều” là tính chất tính toán, không phải mã hóa

Khác biệt giữa hash và encryption:

| Hàm băm | Mã hóa |
| --- | --- |
| Đưa dữ liệu tới digest có kích thước cố định | Biến plaintext thành ciphertext theo scheme và tham số |
| Thông thường không cần khóa bí mật | Cần khóa để mã hóa/giải mã, tùy loại scheme |
| Không có thuật toán “decrypt hash” đúng nghĩa để khôi phục bản gốc | Bên có quyền và khóa thích hợp có thể giải mã, kiểm tra tag nếu là AEAD |
| Có thể nhiều đầu vào cùng digest | Scheme giải mã hợp lệ phải xác định được plaintext từ bản mã phù hợp |
| Phù hợp làm thành phần cho dấu vân tay, chữ ký, MAC, KDF… | Phù hợp bảo vệ tính bí mật, AEAD còn có xác thực/toàn vẹn |

Khi chỉ có một digest, việc “đảo ngược” thường phải dựa vào thử ứng viên, khai thác đầu vào đoán được hoặc tìm điểm yếu thuật toán. Website “reverse SHA-256” thường tra bảng digest của các giá trị phổ biến: đó **không phải** giải mã toán học SHA-256.

Cần phân biệt:
- **Một chiều theo định nghĩa bảo mật:** chưa biết phương pháp hiệu quả để tìm preimage trong mô hình đe dọa tương ứng.
- **Không thể đảo ngược duy nhất:** từ một digest cố định không đủ thông tin để xác định một đầu vào ban đầu duy nhất vì có rất nhiều đầu vào cho cùng digest.
- **Đầu vào khó đoán:** là điều kiện độc lập về entropy. Một hàm băm rất mạnh vẫn không thể làm một mật khẩu phổ biến thành bí mật khó đoán khi bị tấn công offline.

## 5. Khi nào các thuộc tính được sử dụng?

### 5.1. Kiểm tra toàn vẹn tệp

Giả sử người tải file biết trước digest SHA-256 kỳ vọng thông qua **nguồn đáng tin**, rồi tính SHA-256 của file đã tải và so sánh. Nếu digest khác, file chắc chắn không trùng đúng byte với kỳ vọng (bỏ qua khả năng lỗi xử lý không thuộc mô hình); nếu digest giống, xác suất đối thủ cố ý đưa file khác có cùng digest được đánh giá theo an ninh của hash và mô hình tấn công.

Nhưng nếu kẻ tấn công thay được **cả file lẫn dòng SHA-256 hiển thị trên cùng trang không đáng tin**, việc so sánh không xác thực tác giả hoặc nguồn tải. Muốn xác minh nguồn, cần nguồn digest đã được xác thực, chữ ký số hợp lệ với public key đáng tin, hoặc cơ chế xác thực tương đương.

### 5.2. Chữ ký số và tài liệu đã ký

Nhiều sơ đồ chữ ký xử lý digest hay chuỗi byte đã qua encoding/hash theo thuật toán ký. Nếu đối thủ có thể chế tạo hai tài liệu mang cùng digest trong bối cảnh ký thiếu ràng buộc, họ có thể cố gắng làm cho chữ ký trên tài liệu lành tính được chấp nhận cho tài liệu khác.

Vì vậy, **kháng collision** đặc biệt quan trọng trong các giao thức mà bên tấn công có quyền chọn tài liệu được đưa ra để ký. Tuy nhiên, chỉ nêu `H(M)` chưa mô tả toàn bộ an ninh chữ ký: còn phải có thuật toán ký chuẩn, định dạng encode/verify phù hợp, kiểm soát domain/loại thông điệp, khóa ký được tin cậy và quyền ký.

### 5.3. Định danh nội dung, cache và Merkle tree

Digest có thể dùng để tham chiếu một blob nội dung (content addressing) hoặc tổ chức Merkle tree. Với nguồn dữ liệu và quy tắc encoding đã định, cùng byte sinh cùng digest, giúp kiểm tra tái sử dụng/cập nhật.

Nếu đầu vào do đối thủ kiểm soát, rủi ro collision và cách dùng hash làm **khóa định danh duy nhất** phải được xem xét. Ở cấu trúc cây, nên phân biệt rõ hash của leaf và hash của internal node bằng tag/domain separation để tránh nhập nhằng cấu trúc. Hash không tự chứng minh ai là người xuất bản nội dung.

### 5.4. Lưu mật khẩu: yêu cầu khác hẳn

**Không lưu `SHA-256(password)` trực tiếp** như password verifier. SHA-256 được thiết kế tính nhanh, trong khi mật khẩu do con người chọn thường dễ đoán. Hãy sử dụng password-hashing/KDF chuyên dụng có salt riêng và tham số chi phí được thiết lập hợp lý: Argon2id thường là lựa chọn ưu tiên trong hệ thống hỗ trợ; scrypt, bcrypt hoặc PBKDF2 có thể phù hợp theo ràng buộc triển khai, với thông số được thẩm định.

**Salt không phải khóa bí mật**: có thể lưu kèm verifier; mục đích là làm các bản ghi có cùng password dùng đầu vào khác nhau, ngăn tái sử dụng bảng băm tính trước trên nhiều bản ghi. Salt không làm mật khẩu yếu tự tăng entropy; lựa chọn hàm cố ý tốn tài nguyên tính toán/bộ nhớ mới giúp tăng chi phí đoán offline.

## 6. Bit, byte, hex và việc rút ngắn digest

Với SHA-256:

```text
256 bits = 32 bytes = 64 hexadecimal digits

"abc" (UTF-8): 61 62 63
SHA-256("abc"):
ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
```

Độ dài hex không phải độ mạnh an ninh độc lập: 64 chữ số hex mã hóa 256 bit đầu ra, trong khi chuỗi 64 ký tự *văn bản* nói chung không nhất thiết có 256 bit entropy.

### 6.1. Rút ngắn digest có thể làm giảm an ninh nhanh

Nếu chỉ giữ `t` bit đầu trong digest rồi xem như định danh:

\[
H_t(M) = \operatorname{prefix}_t(H(M))
\]

thì trong mô hình đều, chống preimage mục tiêu ở mức cỡ `2^t`, còn chống collision chỉ ở mức cỡ `2^{t/2}`. Ví dụ giữ **16 bit đầu** của SHA-256 tạo ra một *hàm băm 16 bit mới* dễ có va chạm; phát hiện collision ở đó **không phải** phát hiện collision của toàn bộ SHA-256.

Không tự rút ngắn digest trong giao thức ký/xác thực hoặc thiết kế chống va chạm nếu chưa xác định security level, số lượng dữ liệu và các yêu cầu của chuẩn liên quan. Với mục đích kiểm tra dữ liệu chống sửa đổi có chủ đích, nên giữ đầu ra đầy đủ của thuật toán chuẩn khi có thể.

### 6.2. Khác biệt byte dẫn tới khác biệt digest

So sánh:

```text
"abc"      -> bytes 61 62 63
"abc\n"   -> bytes 61 62 63 0a
"ABC"      -> bytes 41 42 43
```

SHA-256 tính trên **byte thực tế**, không hiểu rằng hai chuỗi “trông tương đương”. Một tệp có CRLF khác LF có thể có digest khác; JSON có thứ tự thuộc tính hoặc khoảng trắng khác có thể có digest khác; văn bản Unicode có thể có nhiều cách mã hóa tổ hợp khác nhau.

Khi một giao thức yêu cầu hai bên tính digest/chữ ký của “cùng thông điệp”, phải chỉ rõ:
- Charset (ví dụ UTF-8), chuẩn hóa Unicode nếu có, cách mã hóa dòng/ký tự.
- Canonical serialization của JSON/XML/đối tượng, độ dài và phân cách các trường, thứ tự trường.
- Phiên bản format, domain/application context và những byte chính xác được bảo vệ.

**Không tùy tiện trim, lowercase hay normalize sau khi đã ký**: thao tác như vậy có thể làm thay đổi nghĩa nghiệp vụ hoặc gây nhập nhằng. Cần thống nhất format trước khi tính hash.

## 7. Java 17+: quan sát tính xác định và collision do cắt còn 16 bit

Ví dụ dưới đây dùng `MessageDigest` trong Java Cryptography Architecture (JCA). Chương trình tính SHA-256 của `abc`, so sánh digest, sau đó **cố ý chỉ lấy 16 bit đầu** để tìm một collision dễ quan sát.

Tạo tệp `HashFundamentalsDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public class HashFundamentalsDemo {
    private static byte[] sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(input.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        byte[] digest = sha256("abc");
        System.out.println("SHA-256(abc): "
                + HexFormat.of().formatHex(digest));

        System.out.println("Same input => same digest: "
                + MessageDigest.isEqual(sha256("abc"), sha256("abc")));
        System.out.println("abc vs abd same digest: "
                + MessageDigest.isEqual(sha256("abc"), sha256("abd")));

        // Demo: collision on a deliberately truncated 16-bit digest.
        // It does NOT claim a collision in full 256-bit SHA-256.
        Map<Integer, String> seen = new HashMap<>();
        for (int i = 0; i < 100_000; i++) {
            String input = "message-" + i;
            byte[] fullDigest = sha256(input);
            int prefix16 = ((fullDigest[0] & 0xff) << 8)
                    | (fullDigest[1] & 0xff);

            String previous = seen.putIfAbsent(prefix16, input);
            if (previous != null) {
                System.out.println("Collision on 16-bit prefix: "
                        + previous + " / " + input);
                System.out.printf("Prefix (hex): %04x%n", prefix16);
                System.out.println("Full SHA-256 equal: "
                        + MessageDigest.isEqual(sha256(previous), fullDigest));
                break;
            }
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac HashFundamentalsDemo.java
java HashFundamentalsDemo
```

Kết quả (đã kiểm tra với JDK; đầu vào và thứ tự duyệt cố định):

```text
SHA-256(abc): ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
Same input => same digest: true
abc vs abd same digest: false
Collision on 16-bit prefix: message-126 / message-133
Prefix (hex): 6620
Full SHA-256 equal: false
```

Giải thích chi tiết:

1. `MessageDigest.getInstance("SHA-256")` tạo đối tượng tính SHA-256. `digest(byte[])` cho mảng **32 byte**, không phải chuỗi hex. `HexFormat.of().formatHex(...)` chỉ là cách hiển thị.
2. Hai lần băm chính xác cùng byte `abc` trả digest giống nhau. Đây là **tính xác định**, không phải một bằng chứng về kháng collision.
3. Băm `abc` và `abd` trả hai digest khác nhau; đây là **một quan sát** cho cặp cụ thể, không thể suy ra mọi cặp khác nhau đều có digest khác.
4. `prefix16` lấy 2 byte đầu, tức chỉ có `65.536` giá trị có thể. Chương trình ghi nhớ các tiền tố đã thấy và dừng khi gặp cặp trùng.
5. `message-126` và `message-133` cùng tiền tố `6620`, nhưng **toàn bộ SHA-256 khác nhau**. Đây là collision của hàm `H_16` do ta định nghĩa bằng cách cắt ngắn, không phải lỗ hổng SHA-256.
6. `MessageDigest.isEqual(...)` so sánh byte digest theo API dành cho so sánh giá trị băm. Nó **không biến** SHA-256 không khóa thành MAC; không dùng kết quả so khớp digest tự công bố để xác thực bên gửi.

Từ ví dụ, có thể tự thay `prefix16` thành 8 bit hoặc 24 bit để quan sát thay đổi trong số lượng mẫu cần thử, nhưng không dùng một thí nghiệm nhỏ để khẳng định xác suất “chắc chắn” cho mọi tập dữ liệu. Birthday bound mô tả **xác suất** dưới giả định phân phối phù hợp.

### 7.1. Vì sao ví dụ không “phá SHA-256”?

Chương trình tìm hai chuỗi `A,B` sao cho:

\[
\operatorname{prefix}_{16}(SHA256(A))
=
\operatorname{prefix}_{16}(SHA256(B)).
\]

Nhưng nó đồng thời kiểm tra:

\[
SHA256(A) \neq SHA256(B).
\]

Muốn chứng minh collision của **SHA-256 gốc**, phải chỉ ra hai thông điệp khác nhau có **toàn bộ 256 bit** digest trùng khớp. Việc đoán trúng một vài byte đầu chỉ là collision trên hàm bị rút ngắn do ta tạo ra.

### 7.2. Lưu ý dùng MessageDigest ngoài demo

- Chỉ định rõ `StandardCharsets.UTF_8` cho dữ liệu văn bản. Không lệ thuộc platform default charset.
- Đối tượng `MessageDigest` là mutable; không mặc định chia sẻ một instance giữa nhiều thread. Tạo instance theo lần dùng hoặc quản lý concurrency đúng cách.
- `digest(...)` xử lý mảng byte đầu vào; với file lớn nên đọc thành các khối và gọi `update(...)` nhiều lần, sau đó `digest()` để hoàn tất, thay vì buộc toàn bộ file vào RAM.
- Đừng dùng `new String(digestBytes, UTF_8)` để biểu diễn digest: digest là bytes ngẫu nhiên về mặt biểu diễn và có thể không tạo UTF-8 hợp lệ. Dùng hex/base64 nếu cần văn bản.
- Chỉ dựa vào digest so sánh với **giá trị kỳ vọng đáng tin** cho mục đích toàn vẹn; nếu phải xác thực đối tác, chọn HMAC/chữ ký hoặc giao thức xác thực thích hợp.

## 8. Các lỗi phổ biến và giới hạn của hàm băm

| Ngộ nhận/thiết kế | Vấn đề | Cách hiểu hoặc xử lý đúng |
| --- | --- | --- |
| “SHA-256 không thể có collision” | Đầu vào vô hạn, digest hữu hạn | Va chạm chắc chắn tồn tại; bảo mật dựa vào độ khó tìm |
| “Preimage, second preimage và collision giống nhau” | Quên ràng buộc thông điệp/digest đã cố định | Xác định chính xác điều đối thủ được chọn |
| “Hash 256 bit chống collision ở mức 256 bit” | Bỏ qua birthday bound | Độ khó collision tổng quát cỡ `2^128` |
| “Có cặp collision bất kỳ là thay được file M đã ký” | Không phân biệt collision với second preimage | Kiểm tra liệu kẻ tấn công có kiểm soát tài liệu trước khi ký không |
| “Lưu SHA-256(password) đã an toàn” | SHA-256 quá nhanh, mật khẩu entropy thấp | Dùng Argon2id/scrypt/bcrypt/PBKDF2 theo chính sách phù hợp cùng salt |
| “Hash là mã hóa, có thể decrypt hash” | Digest không có phép giải mã giữ nguyên dữ liệu | Dùng encryption khi cần giữ bí mật và giải mã |
| “Gửi SHA-256 cùng file chứng minh file từ server đúng” | Đối thủ có thể sửa cả hai | Digest/signature phải được xác thực qua trust source |
| “Chỉ cần hash(req) là xác thực API” | Hash không có bí mật và không chứng minh tác giả | Dùng HMAC hoặc chữ ký với quản lý khóa và identity binding |
| “Dùng `SHA-256(secret || message)` làm MAC” | Thiết kế tự chế có thể chịu length-extension/ambiguity | Dùng chuẩn HMAC-SHA256, không tự sáng tạo MAC |
| “Thay đổi 1 bit làm digest đổi mạnh ⇒ hash đã an toàn” | Avalanche không chứng minh kháng tấn công | Cần phân tích thuật toán và bằng chứng mật mã |
| “MD5/SHA-1 dùng cho chữ ký hiện đại vẫn đủ tốt” | Va chạm thực tế đã làm suy yếu các hàm này | Tránh MD5/SHA-1 cho an ninh collision; dùng thuật toán hiện đại được chấp nhận |
| “Chỉ so hai chuỗi hex nên không cần thống nhất encoding” | UTF-8/JSON/newline/Unicode khác → bytes khác | Chuẩn hóa format rõ ràng trước khi băm |
| “`String.hashCode()` và CRC32 cũng là hash nên có thể ký” | Không được thiết kế chống đối thủ tìm va chạm/giả mạo | Dùng hàm băm mật mã chuyên dụng |

### 8.1. Cẩn thận với thuật toán cũ và phép xây dựng tự chế

- **MD5** và **SHA-1** có các vấn đề va chạm đã được chứng minh thực tế, nên không phù hợp cho cơ chế an ninh đòi hỏi collision resistance hiện đại. Đừng đánh đồng việc một thuật toán vẫn tồn tại trong API tương thích với việc nó nên được chọn cho thiết kế mới.
- **SHA-256**, **SHA-384/512** và họ **SHA-3** là những họ hàm băm tiêu chuẩn dùng theo yêu cầu an ninh và chính sách tương thích. Chọn hàm phải xét security strength, yêu cầu hiệu năng, trường hợp ứng dụng, thuật toán ký/MAC liên quan và quy định hiện hành.
- Với SHA-256 kiểu Merkle–Damgård, biết `SHA-256(secret || message)` có thể tạo cơ hội cho **length-extension attack** khi một ứng dụng tự xây MAC bằng cách ghép secret vào trước message. Kháng preimage **không có nghĩa** ghép khóa vào SHA-256 đã trở thành MAC an toàn. Dùng HMAC theo chuẩn và phân định thông điệp/ngữ cảnh phù hợp.

Không nên tự thiết kế thuật toán hash, giao thức ký hoặc MAC chỉ bằng cách ghép string và thêm digest; các lỗi encoding, domain separation và xử lý trust thường không lộ ra trong unit test “hash đúng”.

## 9. Đọc tình huống theo đúng thuộc tính

**Tình huống 1 — Tệp đã công bố có digest đáng tin.** Bên tấn công muốn thay bản tải về bằng *một tệp khác* mà vẫn khớp chính digest đã công bố. Tệp/digest mục tiêu đã cố định. Đây gần với bài toán **second preimage** của tệp mục tiêu, dưới giả định digest được công bố xác thực.

**Tình huống 2 — Hai hợp đồng có cùng digest trước khi ký.** Bên tấn công tự làm hai hợp đồng, cho người dùng ký hợp đồng lành tính rồi chuyển chữ ký sang hợp đồng còn lại trong một giao thức không phân biệt thông điệp. Trọng tâm là khả năng tìm **collision**, đặc biệt là collision có tính chất mà tấn công yêu cầu.

**Tình huống 3 — Digest của một API token ngẫu nhiên.** Bên tấn công chỉ có digest và muốn tìm một giá trị bất kỳ có cùng digest. Đây là **preimage**; nếu token có entropy đủ cao thì khó về tổng quát. Nếu token chỉ là mã PIN 4 chữ số thì thử vét cạn theo tập giá trị khả dĩ có thể dễ.

**Tình huống 4 — Hai tệp lớn trong kho lưu trữ có 2 byte đầu SHA-256 giống nhau.** Đây là collision của **digest 16 bit bị rút ngắn**, và chưa cho biết có collision của SHA-256 đầy đủ hay không.

**Tình huống 5 — Attacker đổi file tải và đổi SHA-256 trên cùng website đã bị chiếm.** Ở đây không cần tấn công hash; lỗ hổng là **nguồn digest không đáng tin**. Phải xác thực nguồn hoặc chữ ký, không chỉ chọn hash dài hơn.

## 10. Bài tập tự kiểm tra

**Câu 1.** Với hàm băm 160 bit lý tưởng, độ khó tổng quát để tìm một cặp collision là cỡ bao nhiêu?

**Đáp án:** Cỡ `2^80` phép thử, khác với bài toán preimage mục tiêu cố định vốn cỡ `2^160` trong mô hình phù hợp.

**Câu 2.** Người kiểm tra giao digest `h` mà không giao thông điệp gốc. Bạn phải tìm `M` bất kỳ sao cho `H(M)=h`. Đây là thuộc tính gì?

**Đáp án:** Kháng tiền ảnh (preimage resistance).

**Câu 3.** Người kiểm tra đưa sẵn `M`; bạn cần `M' != M` nhưng `H(M')=H(M)`. Có được tự tạo hai thông điệp khác bất kỳ để tìm collision không?

**Đáp án:** Không. Đây là second-preimage challenge; thông điệp gốc đã cố định.

**Câu 4.** Một hàm băm `n=32` bit có `2^32` giá trị đầu ra. Khi thử vài chục nghìn đầu vào khác nhau, vì sao vẫn có thể xuất hiện collision sớm?

**Đáp án:** Vì số cặp giữa các mẫu tăng xấp xỉ theo bình phương số mẫu; birthday bound cho mức cỡ `2^16` mẫu thì collision đã có xác suất đáng kể, không cần thử hết `2^32` giá trị.

**Câu 5.** Vì sao `SHA-256("1234")` không làm mật khẩu “1234” có sức chống đoán tương đương khóa ngẫu nhiên 256 bit?

**Đáp án:** Không gian mật khẩu vẫn nhỏ và dễ đoán. Đối thủ có thể tính SHA-256 của các ứng viên và so sánh; cần mật khẩu đủ mạnh cùng password-hashing chuyên dụng và chính sách kiểm soát xác thực.

**Câu 6.** Bạn có thể xem hai SHA-256 digest của file được tính ở hai máy là “khác file” nếu một máy đã tự động chuyển CRLF thành LF không?

**Đáp án:** Có thể khác digest vì byte file khác, dù phần hiển thị trông gần giống. Cần thống nhất byte và quy tắc canonicalization khi bài toán yêu cầu cùng nội dung logic.

**Câu 7.** Tại sao hai thông điệp khác nhau cùng *4 ký tự hex đầu* của SHA-256 chưa chứng minh collision của SHA-256?

**Đáp án:** 4 hex chỉ biểu diễn 16 bit đầu; 240 bit còn lại có thể khác. Va chạm đầy đủ đòi hỏi cả 256 bit trùng.

## 11. Sơ đồ ghi nhớ

```text
               DỮ LIỆU BYTE M
                      |
              Cryptographic hash H
                      |
                Digest n bit
                      |
      +---------------+------------------+
      |               |                  |
  Preimage        Second preimage     Collision
  Cho trước h     Cho trước M         Tự chọn M1, M2
  Tìm M           Tìm M' != M         M1 != M2
  H(M) = h        H(M') = H(M)        H(M1) = H(M2)
  ~2^n            ~2^n               ~2^(n/2)
                      |
       Mốc độ khó trên là mô hình lý tưởng,
       không phải bảo đảm tuyệt đối mọi trường hợp
```

**Điểm cần nhớ:** *Một chiều* nói về sự khó khăn của việc tìm đầu vào cho digest cố định; *second preimage* yêu cầu thay thế một thông điệp đã biết mà giữ nguyên digest; *collision* cho phép chọn tự do cả hai thông điệp, vì thế chịu birthday bound. Tính chất mật mã của hash chỉ phát huy trong giao thức dùng đúng byte, đúng thuật toán, mức độ dài phù hợp và nguồn tin cậy được xác thực.

### Tài liệu tham khảo

- NIST FIPS 180-4, *Secure Hash Standard (SHS)* — định nghĩa SHA-256 và các thuật toán SHA-2: https://csrc.nist.gov/pubs/fips/180-4/upd1/final
- NIST FIPS 202, *SHA-3 Standard* — họ SHA-3: https://csrc.nist.gov/pubs/fips/202/final
- NIST SP 800-107 Rev. 1, *Recommendation for Applications Using Approved Hash Algorithms* — định nghĩa các mức an ninh của hash: https://csrc.nist.gov/pubs/sp/800/107/r1/final
- Java SE 17 API, `MessageDigest`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html
- Java SE 17 API, `HexFormat`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HexFormat.html
- OWASP, *Password Storage Cheat Sheet* — hướng dẫn lưu mật khẩu chuyên dụng: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-functions-002">4.2 MessageDigest với SHA-2, SHA-3 và việc kiểm tra khả năng hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# 4.2 MessageDigest với SHA-2, SHA-3 và việc kiểm tra khả năng hỗ trợ

## Mục tiêu học tập

Sau bài này, bạn có thể: sử dụng `java.security.MessageDigest` để băm một mảng byte hoặc luồng dữ liệu; phân biệt SHA-2 với SHA-3 và chọn đúng **tên thuật toán chuẩn trong Java**; kiểm tra thuật toán có thực sự hoạt động trên JVM/provider mục tiêu; đọc và xử lý `NoSuchAlgorithmException`; và tránh các lỗi bảo mật như coi digest là bằng chứng xác thực hoặc băm mật khẩu trực tiếp.

Đây là bài về **hàm băm mật mã (cryptographic hash)** trong kiến trúc JCA, không phải mã hóa/giải mã. Với cùng một chuỗi byte đầu vào và cùng thuật toán, đầu ra băm là xác định; dữ liệu đầu vào dài tùy ý tạo ra digest có độ dài cố định. Thay đổi dữ liệu đầu vào thường làm digest thay đổi mạnh, nhưng **digest không thể dùng để khôi phục plaintext** và cũng không thể tự chứng minh ai là người gửi dữ liệu.

## 1. SHA-2 và SHA-3: cùng mục tiêu, khác thuật toán

Cả SHA-2 (chuẩn FIPS 180-4) và SHA-3 (chuẩn FIPS 202) đều là các họ hàm băm mật mã. SHA-2 dùng các cấu trúc dựa trên các hàm nén; SHA-3 dựa trên cấu trúc *sponge* của Keccak. Cùng số bit đầu ra không có nghĩa hai thuật toán cho cùng digest hoặc có thể thay thế tùy tiện trong giao thức đã triển khai.

| Họ | Tên chuẩn truyền cho `MessageDigest.getInstance(...)` | Digest (bit) | Byte | Hex ký tự |
| --- | --- | ---: | ---: | ---: |
| SHA-2 | `SHA-224` | 224 | 28 | 56 |
| SHA-2 | `SHA-256` | 256 | 32 | 64 |
| SHA-2 | `SHA-384` | 384 | 48 | 96 |
| SHA-2 | `SHA-512` | 512 | 64 | 128 |
| SHA-2 | `SHA-512/224` | 224 | 28 | 56 |
| SHA-2 | `SHA-512/256` | 256 | 32 | 64 |
| SHA-3 | `SHA3-224` | 224 | 28 | 56 |
| SHA-3 | `SHA3-256` | 256 | 32 | 64 |
| SHA-3 | `SHA3-384` | 384 | 48 | 96 |
| SHA-3 | `SHA3-512` | 512 | 64 | 128 |

**Điểm dễ nhầm:**

- `SHA-256` thuộc **SHA-2**, còn `SHA3-256` thuộc **SHA-3**. Hai giá trị băm đều 256 bit, nhưng **khác nhau đối với cùng dữ liệu**. Một hệ thống đã dùng SHA-256 không thể tự đổi thành SHA3-256 khi verify dữ liệu cũ.
- `SHA-512/256` là **một biến thể SHA-2 được xác định riêng** (có giá trị khởi tạo riêng), không tương đương với lấy 32 byte đầu của kết quả `SHA-512`.
- `SHA3-256` là tên SHA-3 được chuẩn hóa theo FIPS 202; không coi các đầu ra có nhãn `Keccak-256` ở thư viện khác là tương thích mặc định vì quy ước padding/domain separation có thể khác.
- `SHA-1` **không** thuộc họ SHA-2 và đã không phù hợp cho các ứng dụng mới cần chống va chạm. `MD5` cũng không nên dùng để bảo vệ tính toàn vẹn trước đối thủ chủ động.

Tên trên là các **standard algorithm names** của Java SE 17; chúng mô tả cách đặt tên, không bảo đảm **mọi provider** đều cài mọi biến thể. Thực tế khả dụng phụ thuộc bản JDK, provider đã đăng ký và cấu hình runtime. Đặc biệt, đừng giả định SHA3 luôn có ở mọi bản phân phối hoặc mọi provider.

### Đầu vào/đầu ra thực chất là byte

`MessageDigest` xử lý `byte[]`/`ByteBuffer` hoặc các phần byte được truyền qua `update`; nó không biết JSON, Unicode, tệp hay nội dung giao thức. Bạn phải quy định cách chuyển **chính xác** dữ liệu thành byte: ví dụ `"abc".getBytes(StandardCharsets.UTF_8)`. UTF-8 của các ký tự, dấu xuống dòng `LF`/`CRLF`, khoảng trắng, thứ tự trường JSON hoặc Unicode normalization khác nhau có thể tạo digest khác nhau dù người đọc thấy văn bản gần giống nhau.

Giá trị trả về là `byte[]`, không phải chuỗi hex. Hex chỉ là cách **biểu diễn**: mỗi byte được viết thành hai ký tự hex. Java 17 có `HexFormat.of().formatHex(digestBytes)` để in ổn định.

## 2. Vòng đời của `MessageDigest`

Bốn thao tác cốt lõi:

1. **Chọn engine/thuật toán:** `MessageDigest.getInstance("SHA-256")` yêu cầu một provider triển khai dịch vụ JCA có loại `MessageDigest` và tên `SHA-256`.
2. **Nạp dữ liệu:** `update(byte[])`, `update(byte[], offset, length)` hoặc `update(ByteBuffer)` có thể gọi nhiều lần, theo đúng thứ tự các byte. Có thể thay bằng `digest(byte[])` nếu toàn bộ nội dung đã ở trong mảng.
3. **Kết thúc phép băm:** `digest()` xử lý toàn bộ các byte đã `update` rồi trả về `byte[]`; `digest(byte[])` đồng thời xử lý mảng byte đó. Hai cách cho cùng kết quả nếu chuỗi byte giống nhau.
4. **Trạng thái sau khi kết thúc:** sau một lần `digest()`, đối tượng `MessageDigest` được reset để có thể tính digest mới. Gọi `digest()` tiếp mà không nạp lại dữ liệu sẽ băm **chuỗi rỗng**, không lặp lại kết quả cũ.

Mã minh họa **đặt bên trong phương thức có xử lý `NoSuchAlgorithmException`**:

```java
byte[] input = "abc".getBytes(StandardCharsets.UTF_8);

// Một lần: truyền toàn bộ byte.
byte[] whole = MessageDigest.getInstance("SHA-256").digest(input);

// Nhiều phần: thứ tự và các byte ghép lại phải giống nhau.
MessageDigest md = MessageDigest.getInstance("SHA-256");
md.update((byte) 'a');
md.update("bc".getBytes(StandardCharsets.UTF_8));
byte[] parts = md.digest();

boolean equal = MessageDigest.isEqual(whole, parts); // true
```

Đoạn trên chỉ là một phần chương trình; import cần `java.security.MessageDigest` và `java.nio.charset.StandardCharsets`. Đối tượng `MessageDigest` có trạng thái thay đổi qua `update` nên **không chia sẻ một instance cho nhiều luồng đồng thời** nếu không có cơ chế đồng bộ phù hợp. Cách đơn giản là tạo instance riêng cho từng tác vụ. `getDigestLength()` thường cho số byte đầu ra, nhưng theo API có thể trả `0` nếu provider không xác định được; `digest().length` là độ dài thực tế của kết quả đã tính.

## 3. Chương trình Java 17 chạy được: SHA-2, SHA-3 và hỗ trợ theo provider

Tạo đúng tệp **`HashFamiliesDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.HexFormat;
import java.util.List;

public final class HashFamiliesDemo {
    public static void main(String[] args) throws Exception {
        byte[] abc = "abc".getBytes(StandardCharsets.UTF_8);

        for (String algorithm : List.of(
                "SHA-256", "SHA-512/256", "SHA3-256", "SHA3-512")) {
            try {
                MessageDigest digest = MessageDigest.getInstance(algorithm);
                byte[] bytes = digest.digest(abc);
                System.out.printf("%-11s bits=%3d provider=%s%n",
                        algorithm, bytes.length * 8, digest.getProvider().getName());
                if (algorithm.equals("SHA-256") || algorithm.equals("SHA3-256")) {
                    System.out.println("  abc=" + HexFormat.of().formatHex(bytes));
                }
            } catch (NoSuchAlgorithmException e) {
                System.out.println(algorithm + " unsupported");
            }
        }

        MessageDigest incremental = MessageDigest.getInstance("SHA-256");
        incremental.update((byte) 'a');
        incremental.update("bc".getBytes(StandardCharsets.UTF_8));
        byte[] chunked = incremental.digest();
        byte[] oneShot = MessageDigest.getInstance("SHA-256").digest(abc);
        System.out.println("SHA-256 one-shot == incremental: "
                + MessageDigest.isEqual(oneShot, chunked));

        System.out.println("MessageDigest names advertised: "
                + Security.getAlgorithms("MessageDigest").size());
        System.out.println("Providers registering SHA3-256:");
        boolean found = false;
        for (Provider provider : Security.getProviders()) {
            Provider.Service service = provider.getService("MessageDigest", "SHA3-256");
            if (service == null) {
                continue;
            }
            found = true;
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA3-256", provider);
                System.out.printf("  %s -> %s (%d bits)%n",
                        provider.getName(), service.getAlgorithm(),
                        digest.digest(abc).length * 8);
            } catch (NoSuchAlgorithmException e) {
                System.out.println("  " + provider.getName()
                        + ": registered but unavailable");
            }
        }
        if (!found) {
            System.out.println("  none");
        }

        if (args.length > 0) {
            byte[] fileHash = hashFile(Path.of(args[0]), "SHA-256");
            System.out.println("SHA-256(file)=" + HexFormat.of().formatHex(fileHash));
        }
    }

    private static byte[] hashFile(Path file, String algorithm)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
        }
        return digest.digest();
    }
}
```

Biên dịch/run bằng JDK 17 trở lên (ví dụ mã đã kiểm bằng `javac --release 17`):

```bash
javac --release 17 HashFamiliesDemo.java
java HashFamiliesDemo
```

Một lần chạy trên JVM có provider `SUN` cho các thuật toán được thử cho kết quả:

```text
SHA-256     bits=256 provider=SUN
  abc=ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
SHA-512/256 bits=256 provider=SUN
SHA3-256    bits=256 provider=SUN
  abc=3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532
SHA3-512    bits=512 provider=SUN
SHA-256 one-shot == incremental: true
MessageDigest names advertised: 13
Providers registering SHA3-256:
  SUN -> SHA3-256 (256 bits)
```

Đây là **stdout thực tế của một cấu hình thử nghiệm**, không phải kết quả bắt buộc của mọi máy. Tên provider, tổng số tên đăng ký, số provider và những thuật toán báo `unsupported` phụ thuộc môi trường. Hai chuỗi hex nêu trên là **test vector chuẩn của dữ liệu UTF-8 `abc`**; nếu có implementation SHA-256/SHA3-256 tuân thủ chuẩn, hai digest này phải trùng.

Khi chạy với đối số là đường dẫn tệp, ví dụ:

```bash
java HashFamiliesDemo document.bin
```

chương trình còn in `SHA-256(file)=...` cho **toàn bộ các byte** của tệp `document.bin`. Tệp phải tồn tại; đây là thao tác tùy chọn khi người học cung cấp tệp thật. Không có dòng kết quả cố định cho tệp bất kỳ vì nó phụ thuộc vào nội dung tệp.

### Vì sao cách băm tệp an toàn về bộ nhớ?

Hàm `hashFile` đọc tối đa một buffer 8 KiB cho mỗi vòng lặp rồi gọi `update(buffer, 0, count)`. Nó không cần giữ toàn bộ tệp trong RAM. Dùng **đúng `count` byte đã đọc** là quan trọng: `update(buffer)` sẽ đưa cả phần byte cũ còn sót trong buffer vào digest khi lần đọc cuối có ít hơn 8192 byte. Độ dài buffer có thể thay đổi mà không đổi giá trị digest miễn chuỗi byte đầu vào giữ nguyên.

Chú ý: hàm băm đọc nội dung tệp tại thời điểm mở/đọc. Nếu có tiến trình khác sửa tệp trong lúc băm, digest thu được không nhất thiết đại diện cho một snapshot nhất quán; tùy yêu cầu, cần đóng băng nguồn/kiểm tra metadata hoặc dùng cơ chế lưu trữ có snapshot.

## 4. Kiểm tra khả năng hỗ trợ: ba lớp bằng chứng

### 4.1 Tên chuẩn của Java

Tài liệu **Java Security Standard Algorithm Names** liệt kê những tên như `SHA-256`, `SHA-512/256`, `SHA3-256`. Dùng đúng tên chuẩn để giảm phụ thuộc alias riêng của provider. Đừng coi tên chuẩn tồn tại trong tài liệu là bằng chứng rằng JVM đang chạy đã cài provider cung cấp nó.

### 4.2 Danh sách thuật toán được đăng ký

`Security.getAlgorithms("MessageDigest")` trả về tập các tên thuật toán được những provider đăng ký cho engine `MessageDigest` tại thời điểm gọi. Ví dụ đoạn sau **không phải chương trình độc lập**:

```java
Set<String> names = Security.getAlgorithms("MessageDigest");
names.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(System.out::println);
```

Cần import `java.security.Security` và `java.util.Set`. Đây là công cụ **khám phá** runtime; không dùng thứ tự/tên và số lượng trả về làm hợp đồng portable giữa nhiều JVM. Việc xuất hiện tên trong danh sách đăng ký chưa chứng minh một instance khởi tạo và tính digest thành công.

### 4.3 Kiểm tra provider cụ thể rồi thực hiện digest

`Security.getProviders()` liệt kê các provider đã đăng ký. Dùng:

- `provider.getService("MessageDigest", "SHA3-256")` để xem provider đó **công bố** dịch vụ thích hợp (có thể nhận tên chính hoặc alias đã đăng ký). `null` là không tìm thấy service với tên được yêu cầu ở provider đó.
- `MessageDigest.getInstance("SHA3-256", provider)` để yêu cầu **chính provider đó** tạo implementation. Nếu thành công, kiểm tra thêm kết quả `digest` bằng vector chuẩn.
- `digest.getProvider().getName()` để xác định provider của instance đã tạo khi dùng overload **không** chỉ định provider.
- `Security.getProviders("MessageDigest.SHA3-256")` là một filter khác cho các provider phù hợp; API có thể trả `null` nếu không tìm được, nên phải kiểm tra null trước khi duyệt.

Khi gọi `MessageDigest.getInstance("SHA3-256")` **không truyền provider**, JCA chọn trong tập provider đã đăng ký theo cơ chế lựa chọn của runtime. JDK Reference Implementation còn có cấu hình ưu tiên theo thuật toán; đừng suy ra chắc chắn lựa chọn cuối chỉ từ vị trí trong `Security.getProviders()`.

| Mức kiểm tra | API | Có thể kết luận |
| --- | --- | --- |
| Tên chuẩn | Standard Algorithm Names | Tên hợp lệ theo tiêu chuẩn đặt tên |
| Đăng ký chung | `Security.getAlgorithms("MessageDigest")` | Runtime quảng bá tên cho engine hiện tại |
| Đăng ký từng provider | `provider.getService(type, name)` | Provider quảng bá một dịch vụ phù hợp |
| Tạo implementation | `MessageDigest.getInstance(name, provider)` | Provider tạo được instance khi thử |
| Hoạt động chính xác | `digest(input)` và so test vector | Implementation đã tạo tính đúng với dữ liệu thử nghiệm |

Nếu provider cung cấp alias như `SHA256` cho `SHA-256`, alias đó **không bắt buộc** phải tồn tại trong provider khác. Dùng tên chuẩn `SHA-256` cho API công khai/trường dữ liệu hệ thống, đồng thời ghi rõ tên thuật toán cùng digest khi trao đổi giữa các dịch vụ: cả hai bên phải dùng đúng cùng một họ và biến thể.

## 5. Chính sách ứng dụng khi thuật toán không có sẵn

`MessageDigest.getInstance("SHA3-256")` có thể ném `NoSuchAlgorithmException` nếu không provider nào cung cấp dịch vụ. Khi thuật toán là một phần của giao thức, chữ ký dữ liệu, kiểm tra checksum hay định dạng file, **hãy fail fast và báo thiếu capability** hoặc triển khai provider đã được phê duyệt. **Không tự fallback** `SHA3-256` → `SHA-256` chỉ vì hai digest đều dài 256 bit, hoặc SHA-256 → MD5/SHA-1 để “chạy cho được”: cả hai thay đổi hợp đồng dữ liệu, và fallback yếu còn làm giảm bảo mật.

Một cách tổ chức kiểm tra ở startup là đặt tên yêu cầu trong cấu hình rõ ràng:

```java
static MessageDigest requireDigest(String standardName) {
    try {
        return MessageDigest.getInstance(standardName);
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(
                "Required MessageDigest unavailable: " + standardName, e);
    }
}
```

Đoạn trên là phương thức minh họa để chèn vào một class Java có import `MessageDigest` và `NoSuchAlgorithmException`. Chọn/chấp nhận provider theo chính sách deployment. Khi yêu cầu tuân thủ FIPS hoặc provider phần cứng, kiểm tra và chỉ định đúng provider được phép; một instance “tồn tại” trong JVM không tự chứng minh môi trường đạt yêu cầu tuân thủ.

Nếu sản phẩm **thực sự hỗ trợ nhiều lựa chọn** theo một giao thức có thương lượng, cần đưa algorithm identifier vào metadata, ràng buộc danh sách cho phép ở hai phía và kiểm tra digest theo đúng thuật toán được thỏa thuận. Không dựa vào độ dài digest để đoán thuật toán.

## 6. Tính toàn vẹn, xác thực và so sánh digest

Giả sử bạn lưu SHA-256 của một bản phát hành, rồi kiểm tra bản tải về. Khi digest khớp, bạn có bằng chứng byte tải về giống **nội dung gắn với digest mà bạn đang tin cậy**, với giới hạn bảo mật của thuật toán. Nhưng nếu đối thủ có thể thay **cả tệp lẫn digest công bố**, digest thuần không chứng minh nguồn gốc và không bảo vệ khỏi giả mạo.

- Để kiểm tra dữ liệu bị thay đổi vô ý hoặc so khớp với digest đã xác thực từ kênh tin cậy: `MessageDigest` phù hợp.
- Để **xác thực thông điệp** bằng khóa bí mật: dùng engine `Mac` như `HmacSHA256` với quản lý khóa đúng; không tự tạo “MAC” bằng cách nối chuỗi bí mật và dữ liệu rồi gọi `SHA-256`.
- Để **xác thực nguồn gốc bằng khóa bất đối xứng**: dùng `Signature` với thuật toán/mode thích hợp và kiểm tra khóa công khai được tin cậy.
- Để lưu mật khẩu: **không dùng SHA-256/SHA3-256 trực tiếp** dù thêm salt; sử dụng cơ chế password-hashing chuyên dụng có salt riêng, chi phí tính toán phù hợp (ví dụ Argon2id, scrypt, bcrypt hoặc PBKDF2 theo yêu cầu hệ thống).

So hai mảng byte digest bằng `MessageDigest.isEqual(expected, actual)` thay vì kiểm tra reference với `==`. Phương thức hỗ trợ so sánh nội dung byte; nó **không biến digest không khóa thành chữ ký/MAC** và không giải quyết vấn đề tin cậy đối với digest mong đợi. Không tự chuyển digest sang `String` bằng charset text; dùng `HexFormat` hoặc Base64 khi cần lưu/trao đổi.

## 7. Lỗi thường gặp và cách xác định nguyên nhân

| Hiện tượng | Khả năng gây ra và cách sửa |
| --- | --- |
| `NoSuchAlgorithmException: SHA3-256` | Provider trong runtime hiện tại không cung cấp tên được yêu cầu; kiểm tra tên chuẩn, `Security.getProviders()` và thử `getInstance` theo từng provider |
| `SHA3_256` hoặc `SHA-3-256` không nhận | Sai tên chuẩn; trong JCA dùng `SHA3-256` (không gạch nối giữa `SHA` và `3`) |
| Hai chương trình băm “cùng text” ra hai chuỗi hex khác nhau | So lại **byte** UTF-8, newline, BOM, Unicode normalization, ký tự khoảng trắng và cùng tên thuật toán |
| `SHA-512/256` không trùng với 256 bit đầu của `SHA-512` | Đây là hai thuật toán khác nhau; `SHA-512/256` có đặc tả riêng |
| Băm một file lớn bị thiếu bộ nhớ | Chuyển từ `Files.readAllBytes` sang streaming `InputStream` + `update` theo buffer |
| Kết quả băm lần thứ hai bất ngờ | `digest()` reset trạng thái; phải nạp lại dữ liệu cho lần mới. Không chia sẻ instance có trạng thái giữa các thread |
| Nhìn thấy `SHA3-256` trong danh sách nhưng `getInstance` thất bại | Thông tin quảng bá chưa chắc chứng minh khởi tạo thành công; thử đúng overload với provider và kiểm tra ngoại lệ |
| Hash khớp nhưng vẫn không tin nguồn gửi | Digest không có khóa không cung cấp authentication; xác thực digest qua kênh đáng tin hoặc dùng MAC/chữ ký |

### Kiểm tra trước khi đưa vào production

1. Xác nhận thuật toán yêu cầu bằng **tên chuẩn**, khả năng hỗ trợ trong đúng JVM/container và chính sách provider cần dùng.
2. Chuẩn hóa cách biểu diễn **byte đầu vào** (charset, định dạng serialization, newline, thứ tự trường, canonicalization) cho cả phía tạo lẫn phía kiểm tra.
3. Dùng dữ liệu kiểm thử cố định: SHA-256(UTF-8 `abc`) = `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`; SHA3-256(UTF-8 `abc`) = `3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532`.
4. Với dữ liệu lớn, dùng `update` từng phần và đo bộ nhớ; giữ đúng thứ tự byte, đọc đúng số lượng byte thực nhận.
5. Khi thiếu thuật toán bắt buộc, lỗi rõ ràng tại startup hoặc điểm dùng; không đổi thuật toán một cách ngầm định.
6. Ghi/chuyển algorithm identifier cùng hash khi dữ liệu đi qua ranh giới hệ thống; nhớ rằng digest không chứng minh tính xác thực nếu nguồn digest chưa được tin cậy.

## Bài tự luyện

1. Chạy chương trình, ghi lại các tên SHA-2/SHA-3 được hỗ trợ, provider được chọn cho từng tên và tập provider quảng bá `SHA3-256`.
2. Sửa đoạn `update` thành ba lần truyền từng ký tự `a`, `b`, `c`, kiểm tra giá trị `SHA-256` có giữ nguyên không; sau đó đảo thứ tự hai phần để quan sát thay đổi digest.
3. Tạo hai tệp UTF-8 chỉ khác newline cuối cùng, chạy chương trình với từng đường dẫn. Giải thích vì sao hash khác nhau.
4. Thử `MessageDigest.getInstance("SHA3-256", provider)` với từng provider trong máy và phân biệt **“provider công bố service”** với **“instance chạy đúng test vector”**.
5. Thiết kế metadata cho hệ thống kiểm tra tệp gửi qua mạng: cần lưu hash, tên thuật toán, cách tạo byte đầu vào và **cơ chế tin cậy digest** ra sao?

## Tài liệu tham khảo

- [Java SE 17 — Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html): các tên SHA-2/SHA-3 chuẩn, kích thước digest và yêu cầu hỗ trợ.
- [Java SE 17 — `MessageDigest` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html): `getInstance`, `update`, `digest`, `reset` và `isEqual`.
- [Java SE 17 — `Security` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Security.html): `getAlgorithms`, `getProviders` và lọc provider.
- [Java SE 17 — `Provider` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Provider.html): `getService`, `getServices` và tên thuật toán provider hỗ trợ.
- [NIST FIPS 180-4](https://csrc.nist.gov/pubs/fips/180-4/upd1/final): các biến thể SHA-2.
- [NIST FIPS 202](https://csrc.nist.gov/pubs/fips/202/final): định nghĩa SHA-3.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-functions-003">4.3 Chu trình update, digest, reset và xử lý dữ liệu nhiều phần</a>

<details>
<summary>Xem chi tiết</summary>

# 4.3. Chu trình `update`, `digest`, `reset` và xử lý dữ liệu nhiều phần

## Mục tiêu

Bài này hướng dẫn sử dụng `java.security.MessageDigest` của Java Cryptography Architecture (JCA) để băm một thông điệp thành nhiều phần, tính digest cuối cùng, chủ động hủy state, và băm dữ liệu lớn mà không cần nạp toàn bộ file vào RAM.

Sau bài học, bạn có thể:

- Giải thích vòng đời **`update` → `digest` → reset tự động**, phân biệt với `reset()` do lập trình viên gọi.
- Nạp dữ liệu bằng `byte[]`, các đoạn có `offset/len` và `ByteBuffer` mà không tính thừa byte.
- Băm file hoặc `InputStream` theo từng chunk và chứng minh digest không phụ thuộc kích thước chunk khi byte đầu vào giống nhau.
- Hiểu rõ sự khác biệt giữa `digest()`, `digest(byte[] input)` và `digest(byte[] buf, int offset, int len)`.
- Tránh tái sử dụng state sai, nhầm ranh giới dữ liệu và các lỗi thường gặp liên quan encoding, concurrency, luồng đọc và tính bảo mật.

Ví dụ dùng **Java 17+** và **SHA-256**. `SHA-256` nhận byte input và cho output 256 bit (= 32 byte, thường hiển thị 64 ký tự hex). Các ví dụ tập trung vào API và state, không triển khai thuật toán SHA-256 từ đầu.

## 1. `MessageDigest` là engine có trạng thái

Gọi factory:

```java
MessageDigest md = MessageDigest.getInstance("SHA-256");
```

Snippet giả định đã `import java.security.MessageDigest;` và có xử lý `NoSuchAlgorithmException`. JCA chọn implementation của provider phù hợp đang đăng ký. Đối tượng mới chứa **state của thông điệp rỗng**.

Gọi `md.update(...)` để đưa thêm byte vào state, theo **thứ tự**. Gọi `md.digest()` khi đã nạp đủ để **hoàn tất băm**, nhận mảng digest, đồng thời engine **tự reset** về state ban đầu. Gọi `md.reset()` nếu muốn **hủy dữ liệu đã nạp** mà không lấy kết quả.

```text
MessageDigest mới (thông điệp rỗng)
              |
      update(part 1)
              |
      update(part 2)
              |
      update(part n)
              |
           digest()
              |
              +----> trả digest của (part1 || part2 || ... || partN)
              |
              v
      state rỗng trở lại
              |
      sẵn sàng cho thông điệp tiếp theo
```

Bất kỳ lúc nào trước `digest()`:

```text
update(part1) -> update(part2) -> reset()
                                    |
                                    v
                          bỏ phần dữ liệu đã nạp
                          state rỗng, không có digest
```

Do đó:

- `update("ab"); update("c"); digest()` băm **`abc`**.
- `update("ab"); digest("c")` cũng băm **`abc`**: `digest(byte[] input)` nạp thêm `input` rồi final.
- `update("ab"); reset(); digest("c")` băm **`c`**, vì đã bỏ `ab`.
- `digest(); digest()` trên cùng instance: lần đầu băm state hiện có, lần sau băm **thông điệp rỗng** do lần trước đã reset.
- `digest()` không trả về “state đã lưu”; nó **kết thúc thông điệp**. Nếu muốn tiếp tục cùng thông điệp, chưa được gọi `digest()`.

## 2. Các overload cần phân biệt

| Phương thức | Chức năng | Trạng thái sau lời gọi |
| --- | --- | --- |
| `update(byte b)` | Nạp đúng 1 byte. | Giữ state, có thêm byte. |
| `update(byte[] input)` | Nạp **toàn bộ mảng**. | Giữ state, có thêm mảng. |
| `update(byte[] input, int offset, int len)` | Nạp đúng `len` byte từ `input[offset]`. | Giữ state, có thêm đoạn byte. |
| `update(ByteBuffer input)` | Nạp từ `position` đến trước `limit`; position tiến đến limit. | Giữ state, buffer được tiêu thụ. |
| `digest()` | Final hóa state hiện tại. | **Tự reset**, trả digest byte[]. |
| `digest(byte[] input)` | Nạp **input** rồi final hóa. | **Tự reset**, trả digest byte[]. |
| `digest(byte[] buf, int offset, int len)` | Final hóa và ghi digest vào **buffer đích** tại `offset`, có tối đa `len` byte. | **Tự reset**, trả số byte ghi (có thể ném `DigestException`). |
| `reset()` | Bỏ toàn bộ state đang nạp, không băm. | State rỗng. |
| `getDigestLength()` | Hỏi độ dài digest theo byte. | Không nạp byte; có thể trả 0 nếu provider không xác định được. |

**Rất dễ nhầm overload:** tham số `byte[]` của `digest(byte[] input)` là **đầu vào**; còn `byte[] buf` của `digest(byte[] buf, offset, len)` là **đầu ra**. Muốn băm một đoạn mảng, dùng `update(input, offset, len)`, sau đó `digest()`.

Mỗi lời gọi `update` nối thêm bytes vào cùng một thông điệp. Ranh giới giữa các lời gọi không được mã hóa trong digest; chỉ **chuỗi byte cuối cùng** quan trọng. Vì vậy, chia `abc` thành `a + bc` hay `ab + c` vẫn cho cùng SHA-256 nếu không có `digest` hoặc `reset` xen giữa.

## 3. Chương trình hoàn chỉnh: update, digest và reset

Lưu file **`DigestLifecycleDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class DigestLifecycleDemo {
    private DigestLifecycleDemo() {}

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    public static void main(String[] args) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        md.update(utf8("a"));
        md.update(utf8("b"));
        System.out.println("chunks-abc = " + hex(md.digest(utf8("c"))));

        System.out.println("after-digest-empty = " + hex(md.digest()));

        md.update(utf8("discard"));
        md.reset();
        System.out.println("after-reset-abc = " + hex(md.digest(utf8("abc"))));

        byte[] src = utf8("abc");
        md.update(src, 0, 1);
        md.update(src, 1, 2);
        System.out.println("slices-abc = " + hex(md.digest()));

        ByteBuffer buffer = ByteBuffer.wrap(utf8("__abc__"));
        buffer.position(2);
        buffer.limit(5);
        md.update(buffer);
        System.out.println("buffer-remaining = " + buffer.remaining());
        System.out.println("bytebuffer-abc = " + hex(md.digest()));

        md.update(utf8("abc"));
        byte[] destination = new byte[40];
        int written = md.digest(destination, 4, 32);
        System.out.println("written-bytes = " + written);
        System.out.println("destination-abc = " +
                HexFormat.of().formatHex(destination, 4, 4 + written));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 DigestLifecycleDemo.java
java DigestLifecycleDemo
```

**Stdout chính xác:**

```text
chunks-abc = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
after-digest-empty = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
after-reset-abc = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
slices-abc = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
buffer-remaining = 0
bytebuffer-abc = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
written-bytes = 32
destination-abc = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
```

Cách đọc từng kết quả:

1. `md.update("a")` và `md.update("b")` tích lũy `ab`; `md.digest("c")` **nạp tiếp `c`**, trả SHA-256(`abc`) rồi reset.
2. `md.digest()` ngay sau đó cho SHA-256 của **0 byte** (thông điệp rỗng): một bằng chứng rằng state sau `digest` đã được reset.
3. `md.update("discard"); md.reset(); md.digest("abc")` bỏ phần đã nạp dở; chỉ tính trên `abc`.
4. `md.update(src,0,1)` và `md.update(src,1,2)` băm hai đoạn `a` và `bc` chính xác, cũng là `abc`.
5. `ByteBuffer.wrap("__abc__")` chứa hai byte `_` ở đầu và cuối; đặt position=2, limit=5 để lấy đúng `abc`. `md.update(buffer)` tiêu thụ `remaining()` và làm position bằng limit.
6. `md.digest(destination,4,32)` ghi **32 byte OUTPUT** kể từ index 4 của mảng 40 byte. `written = 32` và hàm `HexFormat.of().formatHex(destination,4,4+written)` chỉ in vùng đã ghi.

Lưu ý: `HexFormat` có trong **Java 17+**. Có thể dùng `String.format` hoặc tự định dạng hex ở Java cũ, nhưng ví dụ này chủ đích yêu cầu Java 17+.

## 4. Nạp dữ liệu từ `InputStream` vào một digest duy nhất

`MessageDigest` không quan tâm dữ liệu đến từ mảng byte, file hay mạng: điều kiện để kết quả đúng là mọi byte được truyền vào `update` **đúng một lần, đúng thứ tự**, rồi mới gọi `digest()`. Phần này minh họa cách dùng luồng để chứng minh quy tắc state; cách thiết kế, benchmark và kiểm thử file nhiều GiB, `FileChannel` và `ByteBuffer` được triển khai riêng trong bài **4.4 Băm file lớn bằng luồng hoặc ByteBuffer**.

Phương thức minh họa dưới đây nhận `InputStream` đã mở; cần đặt trong class có import `java.io.InputStream` và `java.security.MessageDigest`, xử lý ngoại lệ theo chữ ký. Đây là **đoạn phương thức**, còn `DigestLifecycleDemo` ở mục 3 là chương trình độc lập của bài này:

```java
static byte[] sha256(InputStream source) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] buffer = new byte[8192];
    int n;
    while ((n = source.read(buffer)) != -1) {
        if (n > 0) {
            md.update(buffer, 0, n);
        }
    }
    return md.digest();
}
```

Một lần `read` có thể trả ít hơn 8192 byte, đặc biệt ở chunk cuối; `update(buffer, 0, n)` vì thế chỉ nhận những byte thực đã đọc. Nếu thay bằng `update(buffer)`, các byte còn sót trong buffer cũng bị nạp, làm sai digest. Nếu gọi `digest()` bên trong vòng lặp, state bị reset giữa các chunk và kết quả không còn là digest của toàn luồng.

Hàm này **không đóng** nguồn mà caller sở hữu: nên gọi bằng `try-with-resources`, chỉ dùng kết quả nếu đọc đến EOF thành công. Với `ByteArrayInputStream` chứa đúng byte UTF-8 của `abc`, kết quả hex là `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`. Đổi cách chia chunk không đổi digest nếu giữ nguyên chuỗi byte. Với nguồn không có EOF (stream kéo dài vô hạn), ứng dụng phải xác định điểm kết thúc thông điệp trước khi final.

## 5. Dùng `DigestInputStream` làm lựa chọn thay thế

JDK có `java.security.DigestInputStream`: các byte **đọc qua wrapper** được tự động nạp vào `MessageDigest` gắn với nó. Snippet dưới đây dùng trong một phương thức đã khai báo biến `Path path` và xử lý checked exceptions:

```java
MessageDigest md = MessageDigest.getInstance("SHA-256");
try (DigestInputStream input =
             new DigestInputStream(Files.newInputStream(path), md)) {
    input.transferTo(OutputStream.nullOutputStream());
}
byte[] result = md.digest();
```

Import cần thiết: `java.security.MessageDigest`, `java.security.DigestInputStream`, `java.nio.file.Files`, `java.nio.file.Path` và `java.io.OutputStream`.

`transferTo(OutputStream.nullOutputStream())` đọc hết file thông qua wrapper, không giữ toàn bộ file trong RAM. `DigestInputStream` nạp bytes **khi chúng được đọc**, chứ không tự hoàn tất hash lúc constructor hay `close()`; do đó phải gọi `md.digest()` sau khi đã đọc đủ. Nếu chỉ đọc prefix rồi dừng, digest chỉ tính prefix; đọc trực tiếp từ stream gốc (bỏ wrapper) cũng làm mất những byte ấy khỏi digest wrapper. Có thể dùng wrapper để tiết kiệm code, còn vòng lặp `update` thủ công cho quyền kiểm soát rõ ràng hơn về số byte/ranh giới dữ liệu.

## 6. Các chi tiết của `ByteBuffer` và ranh giới dữ liệu

### `position`, `limit` và `remaining`

`MessageDigest.update(ByteBuffer)` băm bytes trong vùng `[position, limit)` hiện hành của buffer; sau đó position chuyển đến limit, không băm capacity chưa chứa dữ liệu hợp lệ. Với NIO, thường phải gọi `flip()` sau khi ghi vào buffer để chuyển từ mode ghi sang mode đọc. Nếu quên `flip()` hoặc đặt limit sai, có thể băm sai byte.

Khi muốn giữ position của buffer ban đầu, có thể truyền **duplicate**:

```java
ByteBuffer view = original.duplicate();
md.update(view);        // Thay đổi position của view; original giữ position.
```

Đây là snippet có sẵn `original` kiểu `ByteBuffer` và `md` kiểu `MessageDigest`. `duplicate()` tạo view độc lập về position/limit nhưng **vẫn chia sẻ bộ nhớ chứa byte**, không sao chép dữ liệu nền; đừng để thread khác sửa nội dung đồng thời. Với `ByteBuffer` trực tiếp hoặc read-only, ý nghĩa position/limit và `update` vẫn vậy.

### Ranh giới chunk không được mã hóa

Nếu hai chương trình nạp cùng byte stream `abc`, một chương trình `update("a"); update("bc")` và chương trình kia `update("ab"); update("c")`, digest giống nhau. Đừng tưởng số lần gọi `update` lưu được ranh giới trường.

Ví dụ băm một record bằng cách nối trực tiếp UTF-8: hai cặp (`"ab"`, `"c"`) và (`"a"`, `"bc"`) đều cho byte sequence `abc`. Muốn phân biệt, phải dùng **format mã hóa record có cấu trúc**, chẳng hạn nạp trước độ dài của từng trường bằng số byte cố định rồi nạp chính bytes của trường. Khi dùng độ dài, phải đếm **byte đã encode**, không đếm `String.length()` (đơn vị UTF-16).

**Khi hash nhiều thông điệp độc lập**, mỗi thông điệp cần một lần finalize riêng. `update(A1); update(A2); digest()` là hash message A; sau đó `update(B1); digest()` là hash message B. Nếu muốn tách hai nhánh có prefix chung, có thể cân nhắc `clone()` **chỉ khi provider hỗ trợ** hoặc xây hai instance và nạp lại prefix, chứ `reset()` không lưu checkpoint.

## 7. Văn bản, encoding và tính tái lập

`MessageDigest` chỉ xử lý **byte**. Các biểu diễn khác nhau của cùng văn bản nhìn thấy được có thể tạo byte khác nhau:

- Encoding khác nhau, ví dụ UTF-8 và UTF-16.
- Unicode normalization khác nhau.
- Line endings `\n` (LF) và `\r\n` (CRLF).
- Dấu cách, BOM, ký tự ẩn, thứ tự byte hay việc truyền dữ liệu qua một lớp text decoder/encoder.

Luôn chọn encoding rõ ràng khi chuyển chuỗi:

```java
byte[] data = text.getBytes(StandardCharsets.UTF_8);
md.update(data);
```

Snippet giả định `text` là `String` và `md` đã tạo. Tránh dùng `String.getBytes()` không có charset khi cần kết quả byte ổn định trên nhiều runtime. Đặc biệt, để xác minh hash một **file nhị phân**, hãy hash trực tiếp bytes của file, đừng biến nó thành `String` rồi encode lại. Để so khớp digest giữa hai hệ thống, phải thống nhất **exact byte representation** trước khi xem xét provider hay mã hash.

## 8. Tái sử dụng và lỗi trong thực tế

### Khi nào cần gọi `reset()`?

- **Không cần** giữa hai phép băm đã hoàn thành bằng `digest()`: JCA đã reset tự động.
- **Cần** nếu muốn hủy state đã nạp dở và bắt đầu một thông điệp mới trên cùng instance.
- **Không dùng** để chốt digest, tạm lưu state, hoặc cố xóa an toàn dữ liệu nhạy cảm khỏi heap; `reset()` không đảm bảo xóa sạch bộ nhớ.

### Concurrency và ownership

`MessageDigest` là đối tượng **mutable**. Không mặc định nó an toàn khi nhiều thread gọi `update`/`digest` đồng thời; state có thể bị trộn giữa các thông điệp, gây kết quả không kiểm soát. Cách đơn giản là **một instance riêng cho mỗi hoạt động băm**, hoặc duy trì ownership đồng bộ nghiêm ngặt. Với workload cần tối ưu, đo chi phí tạo instance và thiết kế vòng đời rõ ràng trước khi cân nhắc pooling. Không dùng `clone()` nếu provider chưa được kiểm chứng hỗ trợ.

### Đọc lỗi hoặc nguồn thay đổi đồng thời

Nếu `InputStream` đọc dở rồi lỗi, không thể coi kết quả băm của phần đã nhận là hash toàn bộ. Nên báo lỗi và bỏ instance; nếu tái sử dụng instance thì reset trước lần mới. Nếu file bị sửa trong lúc đọc, phép hash có thể bao phủ một trạng thái không nhất quán dù `read` không ném exception. Với bài toán cần tính nhất quán, phải kiểm soát nguồn hoặc cách chụp dữ liệu đầu vào.

### Digest không phải xác thực

`SHA-256` thuần cho dấu vân tay byte, không chứng minh dữ liệu đến từ ai: ai có thể sửa payload cũng có thể tính lại hash. Với yêu cầu toàn vẹn **có khóa bí mật**, xem xét `Mac`/HMAC; với password lưu trữ, dùng cơ chế password hashing có salt và chi phí thích hợp thay vì SHA-256 trực tiếp. Đây là khác biệt về mục tiêu bảo mật, không làm thay đổi quy tắc `update/digest/reset`.

## 9. Bảng lỗi thường gặp

| Nhầm lẫn/lỗi | Triệu chứng | Sửa đúng |
| --- | --- | --- |
| Gọi `digest()` trong mỗi lượt `read` | Cho nhiều hash của từng phần, state reset liên tục. | `update` cho mọi block, `digest` một lần sau khi đọc xong. |
| Dùng `update(block)` với mảng tái sử dụng | Block cuối có byte cũ, hash sai. | Dùng `update(block,0,n)` chính xác. |
| Gọi `reset()` giữa hai phần của một thông điệp | Phần đầu bị loại. | Chỉ reset khi chủ động hủy thao tác dở. |
| Gọi `digest()` rồi tiếp tục `update` | Phần sau trở thành một thông điệp mới. | Chỉ finalize khi có đầy đủ mọi phần. |
| Cho rằng `digest(bytes)` bỏ dữ liệu đã nạp | Hash vô tình gồm cả pending state và `bytes`. | Tạo instance mới/reset nếu cần hash chỉ `bytes`. |
| Nhầm `digest(out,off,len)` là update input | Không đưa input mong muốn vào state. | Dùng `update(input,off,len)`; overload digest đó ghi output. |
| Quên `ByteBuffer.flip()` hoặc giới hạn position/limit | Băm vùng dữ liệu không dự kiến. | Kiểm tra vùng `remaining()` trước `update`. |
| Lấy `String.length()` làm độ dài byte của trường UTF-8 | Tiền tố độ dài sai, encoding record nhập nhằng. | Mã hóa ra byte trước, dùng `byte[].length`. |
| Không cố định charset/chuẩn hóa line ending | Digest khác nhau giữa môi trường. | Thống nhất exact bytes/encoding/protocol. |
| Chia sẻ instance giữa thread | State bị race/trộn. | Instance riêng cho từng phép băm. |
| Stream thất bại vẫn xuất hash như đã hoàn tất | Hash chỉ tính prefix. | Bỏ kết quả, báo lỗi và khởi tạo lại có kiểm soát. |
| Coi hash thuần là xác thực nguồn gửi | Không ngăn được giả mạo nếu attacker sửa cả dữ liệu và hash. | Sử dụng HMAC/chữ ký theo yêu cầu bảo mật. |

## 10. Kiểm tra hiểu bài

1. Tạo hai instance SHA-256 độc lập. Instance A gọi `update("a")`, `update("bc")`, `digest()`; instance B gọi `digest("abc")`. Tại sao output giống nhau? Điều kiện nào làm chúng khác?
2. Nạp `a`, gọi `reset()`, nạp `bc` rồi `digest()`. So với SHA-256(`abc`) và SHA-256(`bc`) để xác nhận prefix đã bị bỏ.
3. Viết một `main` gọi phương thức `sha256(InputStream)` ở mục 4 trên cùng một dữ liệu với buffer dài 1, 7, 8192 rồi 65536 byte; giải thích vì sao digest phải giữ nguyên và tìm lỗi trong `read`/`update` nếu khác.
4. Dùng một ByteBuffer chứa `__abc__`, đặt position=2 và limit=5, xác nhận kết quả bằng `digest("abc")` trên instance khác. Kiểm tra position sau update.
5. Tạo mảng output 40 byte, nạp `abc` rồi gọi `digest(output,4,32)`. Giải thích giá trị trả về 32 và vùng nào chứa digest. Thử output nhỏ để xem exception.
6. Giải thích vì sao hai cặp chuỗi (`ab`, `c`) và (`a`, `bc`) có cùng hash khi nối byte trực tiếp. Đề xuất encoding có tiền tố độ dài giúp tách ranh giới.
7. Băm một file ở dạng binary stream, so sánh kết quả với công cụ SHA-256 độc lập trên máy. Nếu mismatch, kiểm tra source file đang thay đổi, byte count, encoding, BOM và line ending.

Đạt yêu cầu khi bạn giải thích được **`update` tích lũy**, **`digest` chốt và tự reset**, **`reset` bỏ dữ liệu chưa chốt**, và dùng chunk đúng **số byte thực đọc**, giữ digest không đổi khi chỉ đổi kích thước chunk.

## Tài liệu Java 17 chính thức

- [`MessageDigest`: `update`, `digest`, `reset`, `getDigestLength`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html)
- [`DigestInputStream`: cập nhật digest khi đọc qua wrapper](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/DigestInputStream.html)
- [`InputStream`: `read`, `transferTo`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html)
- [`ByteBuffer`: `position`, `limit`, `remaining`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)
- [JCA Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-functions-004">4.4 Băm file lớn bằng luồng hoặc ByteBuffer</a>

<details>
<summary>Xem chi tiết</summary>

# 4.4 Băm file lớn bằng luồng hoặc ByteBuffer

## 1. Mục tiêu và bản chất của bài toán

Hàm băm mật mã (cryptographic hash) như **SHA-256** biến một dãy byte dài bất kỳ thành một giá trị dài cố định: SHA-256 tạo ra **32 byte = 256 bit = 64 ký tự hex**. Khi băm file, đầu vào là **toàn bộ các byte của nội dung file, theo đúng thứ tự**, từ byte đầu đến byte cuối. Đường dẫn, tên file, thời gian sửa và kích thước file không tự động được đưa vào digest.

Với file vài GB hoặc lớn hơn bộ nhớ JVM, cách sau dễ gây tốn RAM, tràn giới hạn mảng hoặc `OutOfMemoryError`:

```java
// Minh họa cách KHÔNG nên dùng khi file có thể rất lớn.
byte[] allBytes = Files.readAllBytes(path);
byte[] result = MessageDigest.getInstance("SHA-256").digest(allBytes);
```

Thay vào đó, chỉ giữ một vùng đệm cố định (ví dụ **64 KiB**), lặp cho đến EOF và gọi `MessageDigest.update(...)` cho **chính xác số byte thực sự đọc được**:

```text
File (N byte)
  | read tối đa 64 KiB mỗi lượt
  v
[buffer] -> digest.update(chunk 1) -> digest.update(chunk 2) -> ...
                                                  |
                                                  v
                                           digest.digest()
                                                  |
                                                  v
                                  SHA-256: 32 byte kết quả
```

Trong ký hiệu toán học, nếu nội dung file được chia thành `c1, c2, ..., ck` thì kết quả đúng là `SHA256(c1 || c2 || ... || ck)`; dấu `||` là phép nối byte. Hash theo luồng phải bằng hash đọc toàn bộ file **khi hai bên xử lý cùng một chuỗi byte**. Các ranh giới chunk không làm thay đổi digest.

- **Thời gian:** xấp xỉ O(N) thao tác băm và đọc I/O khi file có N byte.
- **Bộ nhớ ứng dụng:** xấp xỉ O(B), với B là kích thước buffer cố định; không tăng theo dung lượng file. Provider có thể dùng thêm trạng thái/bộ nhớ nội bộ.
- **Kết quả:** `MessageDigest.digest()` hoàn tất và reset đối tượng. Với file mới, khởi tạo `MessageDigest` mới hoặc chắc chắn reset; không vô tình ghép dữ liệu của hai file.
- **Điều kiện quan trọng:** đọc cùng một file trong hai thời điểm **không đồng nghĩa** đọc cùng ảnh chụp dữ liệu nếu file đang bị sửa.

Bài này tập trung vào ba cách với Java 17+: `InputStream` + `byte[]`, `FileChannel` + `ByteBuffer`, và biến thể `DigestInputStream`. Sau đó xét file vượt 2 GiB, so sánh digest, hiệu năng, tính nhất quán và các lỗi thường gặp.

## 2. Chọn API nào?

| Cách | API chính | Điểm mạnh | Điểm cần chú ý |
| --- | --- | --- | --- |
| Đọc tuần tự bằng luồng | `Files.newInputStream(path)`, `InputStream.read(byte[])`, `MessageDigest.update(byte[], off, len)` | Đơn giản; áp dụng cả cho luồng dữ liệu từ file, network hoặc nội dung tải xuống | Chỉ hash `count` byte vừa đọc; dùng try-with-resources; không dùng `available()` để xác định EOF |
| Đọc qua NIO | `FileChannel.open(path)`, `channel.read(buffer)`, `MessageDigest.update(ByteBuffer)` | Quản lý file position, dễ phối hợp với NIO và direct buffer | Phải dùng `flip()` trước `update`, `clear()` trước lượt đọc mới; `update(ByteBuffer)` thay đổi `position` |
| Luồng tự cập nhật hash | `DigestInputStream` | Hữu ích khi đã có pipeline đọc InputStream | Không tự update digest lần nữa; phải đọc đến hết nếu cần hash toàn file |
| Memory-mapped (tùy nhu cầu) | `FileChannel.map(READ_ONLY, offset, length)` | Có thể hữu ích khi đọc theo cửa sổ lớn | `MappedByteBuffer` một vùng map Java 17 có `size <= Integer.MAX_VALUE`; mapping không phải snapshot và vòng đời khác buffer thông thường |

**Không có phương pháp nào mặc định nhanh nhất cho mọi hệ thống.** Hệ điều hành, cache trang, ổ lưu trữ, provider SHA-256, kích thước buffer và số luồng đọc ảnh hưởng kết quả. Chọn `InputStream` cho độ rõ ràng trước; cân nhắc `FileChannel` khi ứng dụng đang dùng NIO hoặc đã benchmark cho workload thực.

## 3. Vòng đời đúng của `MessageDigest` trong một lần băm file

```text
MessageDigest.getInstance("SHA-256")
              |
              v
       digest.update(...)   <--- lần 1
              |
       digest.update(...)   <--- lần 2
              |
               ...
              |
       digest.update(...)   <--- lần cuối, có thể ngắn hơn buffer
              |
              v
         digest.digest()
              |
              v
      32 byte SHA-256; digest reset
```

`update(byte[], offset, len)` tiêu thụ một phần mảng; **không cần** đợi đủ 64 KiB mới cập nhật. `update(ByteBuffer)` chỉ tiêu thụ các byte trong khoảng `position` đến `limit`. Sau `digest()`, không cần gọi `reset()` nếu định dùng lại đối tượng, nhưng việc tạo instance riêng cho mỗi phép băm giúp tránh nhầm trạng thái khi xử lý đồng thời.

Đặc biệt, **không** tính `digest()` sau mỗi chunk rồi nối các digest. Biểu thức `SHA256(c1) || SHA256(c2)` không bằng `SHA256(c1 || c2)` và không phải giá trị SHA-256 của file. Chỉ gọi `digest()` **một lần sau khi đã đọc hết file**.

## 4. Ví dụ hoàn chỉnh: hai cách băm cùng file

Chương trình dưới đây thực hiện hai phép đọc độc lập để so sánh `InputStream` với `FileChannel + ByteBuffer`. Nếu chạy **không đối số**, chương trình tạo một file tạm chứa đúng ba byte ASCII của chuỗi `"abc"`, rồi xóa file tạm. Trường hợp này cho đầu ra xác định để tự kiểm tra. Nếu truyền đường dẫn, chương trình băm file thật; có thể truyền thêm digest SHA-256 đáng tin cậy để xác minh.

Lưu mã nguồn thành **`FileSha256Demo.java`** (Java 17+):

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FileSha256Demo {
    private static final int CHUNK_SIZE = 64 * 1024;
    private static final String SHA256_ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private FileSha256Demo() {}

    public static byte[] hashStream(Path path)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] chunk = new byte[CHUNK_SIZE];

        try (InputStream in = Files.newInputStream(path)) {
            int count;
            while ((count = in.read(chunk)) != -1) {
                if (count > 0) {
                    digest.update(chunk, 0, count);
                }
            }
        }
        return digest.digest();
    }

    public static byte[] hashChannel(Path path)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteBuffer buffer = ByteBuffer.allocateDirect(CHUNK_SIZE);

        try (FileChannel channel =
                     FileChannel.open(path, StandardOpenOption.READ)) {
            while (true) {
                int count = channel.read(buffer);
                if (count == -1) {
                    break;
                }
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }
        return digest.digest();
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: java FileSha256Demo [file [trusted-sha256-hex]]");
        }

        boolean temporary = args.length == 0;
        Path path = temporary
                ? Files.createTempFile("hash-demo-", ".bin")
                : Path.of(args[0]);

        try {
            if (temporary) {
                Files.write(path, "abc".getBytes(StandardCharsets.US_ASCII));
            }

            byte[] fromStream = hashStream(path);
            byte[] fromChannel = hashChannel(path);
            System.out.println("stream=" +
                    HexFormat.of().formatHex(fromStream));
            System.out.println("channel=" +
                    HexFormat.of().formatHex(fromChannel));
            System.out.println("same=" +
                    MessageDigest.isEqual(fromStream, fromChannel));

            String trustedHex = temporary
                    ? SHA256_ABC
                    : (args.length == 2 ? args[1] : null);
            if (trustedHex != null) {
                byte[] expected = HexFormat.of().parseHex(trustedHex);
                if (expected.length != 32) {
                    throw new IllegalArgumentException(
                            "Expected a 64-character SHA-256 hex digest");
                }
                System.out.println("expectedMatch=" +
                        MessageDigest.isEqual(expected, fromStream));
            }
        } finally {
            if (temporary) {
                Files.deleteIfExists(path);
            }
        }
    }
}
```

Biên dịch và chạy với đầu vào mẫu:

```bash
javac -encoding UTF-8 FileSha256Demo.java
java FileSha256Demo
```

Kết quả:

```text
stream=ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
channel=ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
same=true
expectedMatch=true
```

Chuỗi băm này là test vector SHA-256 chuẩn của chính xác ba byte `abc`, **không phải** hash của chuỗi có ký tự xuống dòng sau `abc`.

Chạy với file thực (hãy thay đường dẫn):

```bash
java FileSha256Demo "/data/large-archive.zip"
```

Nếu đã nhận được SHA-256 hex qua một nguồn **đáng tin cậy** (ví dụ manifest đã được ký và xác minh), hãy truyền đối số thứ hai:

```bash
java FileSha256Demo "/data/large-archive.zip" "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
```

Chuỗi hex trong lệnh này chỉ minh họa định dạng đối số, **không phải checksum đúng của file ví dụ**. Khi có đối số thứ hai, `expectedMatch=true` chỉ xảy ra nếu digest của file thực khớp chính xác 32 byte mong đợi. Một file đã bị sửa mà vẫn so với checksum mới lấy từ cùng nguồn không đáng tin thì kết quả so khớp vẫn **không xác lập tính xác thực**.

### 4.1 Giải thích cách dùng InputStream

```java
byte[] chunk = new byte[64 * 1024];
try (InputStream in = Files.newInputStream(path)) {
    int count;
    while ((count = in.read(chunk)) != -1) {
        if (count > 0) {
            digest.update(chunk, 0, count);
        }
    }
}
byte[] sha256 = digest.digest();
```

Đoạn trên là **một phần trích từ chương trình đầy đủ**, giả sử `path` và `digest` đã được tạo.

- `read(chunk)` trả về **số byte thực đọc**, có thể nhỏ hơn dung lượng mảng (ví dụ chunk cuối chỉ có 7 byte). Giá trị `-1` là EOF. Do đó phải gọi `update(chunk, 0, count)`, **không** gọi `update(chunk)` ở mọi lượt, vì những byte cũ còn nằm ở đuôi buffer sẽ bị hash lại.
- Với buffer có độ dài dương, luồng file thông thường đọc đến khi có dữ liệu hoặc EOF. Phép kiểm tra `count > 0` giúp không cập nhật gì khi có trường hợp đọc 0 byte.
- `Files.newInputStream` trả luồng byte; `try-with-resources` tự đóng file kể cả khi `read` hoặc `update` ném lỗi. Không dựa vào `available()` để dừng: phương thức đó ước lượng số byte đọc được ngay mà không chặn, **không** phải kích thước file hay thông báo EOF.
- Một lần đọc không bảo đảm đầy buffer; phải lặp đến `-1`. Không đổi byte thành `String` trước khi hash, vì giải mã và mã hóa lại có thể làm đổi dữ liệu.
- Với bulk read 64 KiB, không nhất thiết thêm `BufferedInputStream`; hãy benchmark nếu pipeline gồm nhiều phép đọc rất nhỏ.

### 4.2 Giải thích FileChannel và vòng đời ByteBuffer

`FileChannel.open(path, StandardOpenOption.READ)` tạo channel đọc tuần tự; `FileChannel.read(buffer)` ghi byte vào buffer bắt đầu từ `position` hiện tại và tăng `position` theo số byte thực đọc. Chu trình cho mỗi lượt:

```text
Mới allocate / sau clear:
    position=0               limit=capacity (65536)
Sau channel.read(...), ví dụ đọc 100 byte:
    position=100             limit=65536
Sau buffer.flip():
    position=0               limit=100
Sau digest.update(buffer):
    position=100             limit=100
Sau buffer.clear():
    position=0               limit=65536
```

`buffer.flip()` chuyển buffer từ chế độ ghi sang đọc: đặt `limit` thành vị trí ghi hiện tại, rồi đưa `position` về 0. `digest.update(buffer)` tiêu thụ **đúng** các byte từ `position` đến `limit` và đưa `position` tới `limit`; nó không tự gọi `flip` và không sửa `limit`. `buffer.clear()` đưa các chỉ số về trạng thái sẵn sàng ghi lại, **không xóa nội dung cũ khỏi bộ nhớ**.

`FileChannel.read` trả `-1` ở EOF, có thể trả ít byte hoặc 0 byte theo hợp đồng API. Code vẫn gọi `flip/update/clear` an toàn nếu `count == 0`; lúc đó phạm vi dữ liệu rỗng. Với `FileChannel` đọc file ở chế độ blocking và buffer còn chỗ, việc đọc thông thường sẽ chờ có dữ liệu hoặc EOF, nên không cần triển khai vòng chờ chủ động. Đối với **kênh không blocking khác** (ví dụ socket), phải xử lý trường hợp trả 0 để tránh vòng lặp quay CPU.

`ByteBuffer.allocateDirect(64 * 1024)` cấp direct buffer, có thể giảm một số bước sao chép trong thao tác I/O, nhưng có chi phí cấp phát và sử dụng vùng nhớ ngoài heap. `ByteBuffer.allocate(...)` cấp heap buffer cũng tương thích `MessageDigest.update(ByteBuffer)`. Direct không bảo đảm luôn nhanh hơn và không nên cấp phát direct buffer lớn cho từng request mà không xét ngân sách bộ nhớ.

### 4.3 Tại sao có thể xử lý file lớn hơn 2 GiB?

`InputStream.read` và `FileChannel.read(ByteBuffer)` chỉ cần biết **số byte của một chunk** (kiểu `int`). Vòng lặp có thể chạy hàng chục nghìn lần cho file nhiều GB; kích thước file thực tế được quản lý bằng metadata hoặc `long` khi cần. Không yêu cầu mảng `byte[]` có kích thước bằng file, cũng không đưa tổng chiều dài file vào một `ByteBuffer` duy nhất.

Một buffer 64 KiB có thể tái sử dụng để đọc file 5 GiB, 50 GiB, ... miễn là file hệ thống/provider hỗ trợ và chương trình có đủ thời gian, dung lượng I/O. Trần kích thước là trần của hệ thống file/API/độ dài nguồn dữ liệu, chứ **không** phải 64 KiB. Nếu cần hiển thị tiến độ, dùng bộ đếm `long processed += count` mỗi lần đọc, không tăng theo `buffer.capacity()`.

## 5. Kiểm thử cả chunk rỗng, đúng biên và dư 1 byte

Một lỗi phổ biến chỉ xuất hiện khi file dài `65536 + 1` byte: lượt đọc cuối có 1 byte nhưng tác giả lại hash toàn bộ mảng 65536 byte. Một lỗi khác là quên `flip` khiến ByteBuffer truyền cả vùng chưa được đọc. Kiểm tra nhiều độ dài sát biên buffer giúp bắt những lỗi này.

Lưu thành **`HashBoundaryCheck.java`** trong cùng thư mục với `FileSha256Demo.java`:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public final class HashBoundaryCheck {
    public static void main(String[] args) throws Exception {
        int[] lengths = {0, 1, 65535, 65536, 65537, 131079, 1048577};
        Path file = Files.createTempFile("hash-boundaries-", ".bin");

        try {
            for (int length : lengths) {
                byte[] data = new byte[length];
                for (int i = 0; i < length; i++) {
                    data[i] = (byte) (31 * i + 7);
                }
                Files.write(file, data);

                byte[] expected = MessageDigest.getInstance("SHA-256")
                        .digest(data);
                byte[] stream = FileSha256Demo.hashStream(file);
                byte[] channel = FileSha256Demo.hashChannel(file);
                if (!MessageDigest.isEqual(expected, stream)
                        || !MessageDigest.isEqual(expected, channel)) {
                    throw new AssertionError(
                            "Mismatch for file length " + length);
                }
            }
            System.out.println("cases=" + lengths.length);
            System.out.println("allMatch=true");
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

`HashBoundaryCheck` là chương trình **chạy đầy đủ** nhưng **phụ thuộc** hai phương thức `FileSha256Demo.hashStream` và `FileSha256Demo.hashChannel` của lớp ví dụ trước. Chương trình tự tạo file tạm với dữ liệu nhị phân có thể tái lập, thử 7 kích thước, và xóa file khi xong. Bộ dữ liệu thử chỉ ~1 MiB ở trường hợp lớn nhất nên việc giữ mảng toàn bộ trong **kiểm thử** là hợp lý; đây không phải mẫu xử lý file lớn ngoài sản phẩm.

```bash
javac -encoding UTF-8 FileSha256Demo.java HashBoundaryCheck.java
java HashBoundaryCheck
```

```text
cases=7
allMatch=true
```

Những độ dài được kiểm tra gồm **0, 1, 65535, 65536, 65537, 131079 và 1048577** byte. SHA-256 trên byte[] nhỏ được dùng làm đáp án độc lập cho hai đường đọc chunk; ngoại lệ `AssertionError` cho biết có cách đọc khác với dữ liệu gốc.

Kiểm thử nên mở rộng bằng file thực có byte 0, byte giá trị âm khi nhìn dưới dạng `byte` Java, nội dung đa ngôn ngữ ở các encoding khác nhau và file lớn hơn 2 GiB khi môi trường tích hợp hỗ trợ. Với file nhỏ, chuỗi rỗng có SHA-256 chuẩn là `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`; đây là test vector tốt để chắc chắn vòng lặp xử lý EOF ngay từ đầu.

## 6. Biến thể: DigestInputStream tự cập nhật hash khi đọc

Nếu ứng dụng đã sử dụng `InputStream` để copy, parse hoặc đọc dữ liệu từ nguồn khác, có thể bọc luồng bằng `java.security.DigestInputStream` để cập nhật digest **tự động khi các byte được đọc qua wrapper**:

```java
// Hàm minh họa độc lập về mặt chữ ký; đặt trong lớp Java với import:
// java.io.IOException, java.nio.file.Files, java.nio.file.Path,
// java.security.DigestInputStream, java.security.MessageDigest,
// java.security.NoSuchAlgorithmException.
static byte[] hashWithDigestInputStream(Path path)
        throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (DigestInputStream input =
                 new DigestInputStream(Files.newInputStream(path), digest)) {
        byte[] chunk = new byte[64 * 1024];
        while (input.read(chunk) != -1) {
            // DigestInputStream da goi digest.update(...) trong read().
        }
    }
    return digest.digest();
}
```

Giải thích: `DigestInputStream.read(...)` sẽ gọi `digest.update(...)` khi chế độ cập nhật đang bật (mặc định bật). **Không** gọi thêm `digest.update(chunk)` trong vòng lặp: như vậy mỗi byte bị hash hai lần. Cần đọc luồng **đến EOF** để băm đủ nội dung; đóng trước khi đọc hết chỉ tạo hash của phần đã tiêu thụ. Phương thức `on(false)` có thể tắt cập nhật nếu giao thức yêu cầu bỏ qua một đoạn (ví dụ header), nhưng khi ấy giá trị digest **không còn là SHA-256 của toàn file**.

Cách này phù hợp khi vừa đọc dữ liệu để xử lý vừa cần hash đúng phần dữ liệu mà pipeline tiêu thụ. Nếu cần hash toàn bộ file bất kể xử lý nghiệp vụ đọc bao nhiêu byte, hãy tách việc tiêu thụ đầy đủ khỏi logic dừng sớm.

## 7. Có nên memory-map file lớn?

Một số hệ thống dùng `FileChannel.map(READ_ONLY, offset, size)` để tạo `MappedByteBuffer` và đưa thẳng vào `MessageDigest.update(ByteBuffer)`. Điều đó vẫn hoạt động với cùng nguyên tắc `position/limit`, nhưng ở Java 17, overload tạo `MappedByteBuffer` có giới hạn **mỗi lần map không vượt `Integer.MAX_VALUE` byte**. Vì vậy không thể map cả file 5 GiB bằng một lời gọi.

Nếu thật sự cần mapping, phải map **theo cửa sổ có độ dài giới hạn**, với `offset` kiểu `long`; hash từng cửa sổ theo đúng thứ tự, cuối cùng gọi `digest()` một lần. Minh họa thuật toán (đoạn khung, **không** phải chương trình độc lập):

```java
// channel: FileChannel da mo READ, digest: MessageDigest SHA-256
final long WINDOW = 1L << 30; // 1 GiB, nho hon Integer.MAX_VALUE
long size = channel.size();
for (long pos = 0; pos < size; ) {
    long length = Math.min(WINDOW, size - pos);
    ByteBuffer mapped =
            channel.map(FileChannel.MapMode.READ_ONLY, pos, length);
    digest.update(mapped);
    pos += length;
}
byte[] result = digest.digest();
```

Chú ý: vùng map có vòng đời độc lập với việc đóng channel; dữ liệu/file bị thay đổi hoặc rút ngắn sau khi map có thể gây hành vi phụ thuộc hệ điều hành, kể cả lỗi truy cập vùng map. Mapping có thể làm tăng áp lực địa chỉ bộ nhớ và khó quản lý giải phóng kịp thời. Với nhiệm vụ **chỉ băm tuần tự**, đọc `InputStream` hoặc `FileChannel.read` thường là lựa chọn dễ kiểm soát và portable hơn. Muốn kết luận mapping nhanh hơn phải đo bằng benchmark cùng môi trường và file thật.

## 8. Kiểm tra checksum: khớp hash khác với xác thực nguồn

Một SHA-256 không có khóa cho phép kiểm tra **hai chuỗi byte có cùng digest hay không** (với xác suất va chạm cực thấp đối với SHA-256 khi dùng đúng). Nhưng hash không chứng minh người gửi là ai; ai sửa được cả file lẫn giá trị hash kèm theo đều có thể cung cấp cặp mới tự nhất quán.

Luồng xác minh an toàn hơn:

1. Lấy SHA-256 mong đợi từ **nguồn đáng tin**: ví dụ metadata từ kênh đã được xác thực, hoặc manifest có **chữ ký số đã xác minh** bằng khóa tin cậy.
2. Giải mã hex thành đúng **32 byte**; đầu vào SHA-256 thông thường phải có 64 ký tự hex, không bao gồm dấu cách hoặc tiền tố `0x` nếu parser không xử lý.
3. Băm đúng byte của file với `MessageDigest`, không giải mã thành text.
4. So sánh byte bằng `MessageDigest.isEqual(expected, actual)`, sau khi kiểm tra độ dài. Hàm này tránh so sánh rồi dừng ở byte đầu tiên khác; tuy nhiên cần thiết kế thêm bảo vệ về nguồn dữ liệu và ranh giới bảo mật.
5. Nếu không khớp, **từ chối sử dụng file**; không lấy giá trị digest mới sinh để "sửa" expected nếu chưa có một nguồn tin cậy xác nhận thay đổi.

Trong chương trình chính, `same=true` chỉ nói hai phương pháp đọc đã cho cùng digest, **không** nói file đáng tin. Khi nhận expected từ manifest tin cậy, `expectedMatch=true` mới cho phép áp dụng quy tắc của quy trình xác minh. SHA-256 không mã hóa file và không che giấu nội dung. Dùng HMAC hoặc chữ ký số nếu cần bảo đảm tính xác thực; băm file đơn thuần không thay thế chữ ký.

Độ dài đầu ra là **số byte digest**, không liên quan đến kích thước file; 64 ký tự hex vì mỗi byte được biểu diễn thành hai chữ số hex. Java 17 cung cấp `HexFormat.of().formatHex(...)` và `HexFormat.of().parseHex(...)`; không cần tự chuyển các byte âm kiểu Java bằng cách nối chuỗi tùy ý.

## 9. File thay đổi khi đang hash: tính nhất quán và TOCTOU

Với file rất lớn, phép băm có thể kéo dài. Trong thời gian đó, một tiến trình khác có thể ghi, truncate, thay thế hoặc đổi nội dung tại đường dẫn. Một lần đọc tuần tự **không được JCA/FileChannel bảo đảm là bản chụp nguyên tử** của file.

- Lượt hash `InputStream` và lượt hash `FileChannel` trong chương trình chính diễn ra **nối tiếp**, nên nếu file bị sửa giữa hai lượt, `same=false` có thể phản ánh **hai phiên bản khác nhau** thay vì lỗi thuật toán.
- File bị kéo dài trong lúc đọc có thể khiến dữ liệu được đọc theo một biên EOF khác; file bị rút ngắn gây thất bại hoặc dữ liệu thiếu. Hash khi file đang bị sửa có thể không tương ứng với một trạng thái ổn định nào.
- Kiểm tra `Files.size(path)` hoặc thời gian sửa **trước và sau** chỉ giúp phát hiện một số thay đổi, **không bảo đảm** file không bị sửa rồi khôi phục. `FileChannel.lock(...)` cũng không phải khóa cưỡng chế di động tuyệt đối giữa mọi tiến trình/hệ điều hành.
- Nếu cần bằng chứng tin cậy, hash **bản snapshot bất biến**, file tạm được tạo xong rồi đóng, bản copy kiểm soát quyền ghi, hoặc đối tượng lưu trữ phiên bản cố định. Khi tải xuống, có thể hash ngay trong quá trình ghi ra file tạm, kiểm tra expected digest sau khi hoàn tất, rồi chỉ công bố/đổi tên file theo quy trình an toàn.
- Với đường dẫn do người dùng cung cấp, cần đánh giá symlink, directory traversal, quyền truy cập và việc đường dẫn bị thay thế giữa lúc kiểm tra và mở file. Quyết định truy cập file phải dựa trên chính sách ứng dụng, không chỉ dựa vào hash.

Hash chỉ phản ánh byte đã được engine tiêu thụ trong lần thực thi đó. Nếu ứng dụng muốn bảo đảm tính toàn vẹn của dữ liệu sẽ **được dùng sau này**, phải đảm bảo dữ liệu đã hash chính là dữ liệu được lưu, chuyển đi hoặc xử lý, không lấy lại file khác từ cùng đường dẫn mà không kiểm soát phiên bản.

## 10. Lỗi phổ biến và cách sửa

| Lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| `Files.readAllBytes` trên file rất lớn | O(N) RAM; có thể `OutOfMemoryError` hoặc đụng giới hạn mảng | Đọc chunk cố định bằng stream/channel |
| `digest.update(chunk)` dù `read` chỉ trả 1 byte | Hash cả dữ liệu thừa từ lần trước trong mảng | `digest.update(chunk, 0, count)` |
| `while (in.available() > 0)` | Có thể dừng sớm, bỏ dữ liệu | Đọc đến `read(...) == -1` |
| Gọi `digest()` sau mỗi chunk | Cho chuỗi digest của từng chunk, không phải hash file | Chỉ final sau khi đọc xong |
| Quên `buffer.flip()` | Hash sai vùng nhớ, kể cả byte chưa thuộc file | `read → flip → update → clear` |
| Quên `buffer.clear()` | Buffer hết chỗ, lượt đọc kế tiếp có thể trả 0 liên tục | Clear chỉ số sau khi update; không giữ stale position |
| Gọi `flip()` hai lần hoặc `rewind()` sai | Giới hạn/số byte hợp lệ sai, mất hoặc hash lặp dữ liệu | Theo đúng state `position/limit` của từng lượt |
| Dùng `String`/`Reader` để băm "file văn bản" | Encoding và newline có thể thay đổi byte gốc | Băm byte từ `InputStream` hoặc `ByteBuffer` |
| Đọc cùng `MessageDigest` từ nhiều thread | Trộn trạng thái nhiều file; digest sai | Tạo digest riêng cho mỗi tác vụ/luồng |
| Thấy `same=true` rồi kết luận an toàn | Hai lượt có thể cùng đọc file đã bị tấn công | So với digest tin cậy; xác minh chữ ký/HMAC khi cần |
| Dựa vào `size`/`lastModifiedTime` như snapshot | Bỏ sót thay đổi trong lúc đọc | Dùng ảnh chụp bất biến hoặc workflow có kiểm soát |
| Nuốt `IOException` rồi vẫn trả digest | Hash của nội dung chưa đọc đủ bị coi là hoàn tất | Để exception làm thất bại toàn tác vụ; xóa/từ chối kết quả |

**Lưu ý về lỗi:** `NoSuchAlgorithmException` xảy ra nếu provider không hỗ trợ thuật toán; Java SE phải hỗ trợ `SHA-256` theo danh sách chuẩn. `IOException` có thể do file không tồn tại, quyền bị từ chối, thiết bị hỏng hoặc đọc giữa chừng thất bại. Nếu hash file lỗi, không được trả một digest "tạm" như kết quả hợp lệ. `try-with-resources` đóng tài nguyên I/O kể cả khi có exception; không tự nuốt hoặc thay thế exception bằng chuỗi hash rỗng.

## 11. Hiệu năng và bộ nhớ: đo thay vì đoán

**Bắt đầu với buffer vừa phải** như 32–256 KiB, ví dụ 64 KiB, rồi đo trên loại file và thiết bị thực tế. Buffer quá nhỏ tăng số lời gọi I/O; buffer quá lớn tăng footprint (nhất là khi hàng trăm request đồng thời), có thể gây áp lực heap hoặc direct memory mà lợi ích I/O không tăng tương ứng.

**Direct vs heap ByteBuffer:** direct buffer có thể có lợi trong một số đường truyền I/O native, nhưng cấp phát direct buffer thường đắt hơn heap buffer, và provider băm có thể phải sao chép dữ liệu vào implementation nội bộ. Đừng giả định `allocateDirect` cho throughput cao hơn; đo cả CPU, GC, direct memory, latency và số tác vụ đồng thời.

**So sánh công bằng:** thử nhiều kích thước file, nhiều thiết bị và trạng thái cache. Đọc hai lần liên tiếp thường khiến lượt thứ hai được hưởng OS page cache; cần đảo thứ tự, warm-up và nhiều lần đo. Chỉ dùng `System.nanoTime` thô để chẩn đoán gần đúng; nếu muốn benchmark nghiêm túc thì dùng công cụ như JMH và đo cả I/O thực tế.

**Không cần song song hóa digest bằng cách ghép hash chunk.** SHA-256 của một file có cấu trúc state nối tiếp; tính hash riêng cho mỗi chunk rồi nối không cho ra SHA-256 của toàn file. Các thiết kế hash tree/Merkle tree là **giao thức khác** với đầu ra khác, chỉ dùng nếu định dạng bên tiêu thụ có quy định.

**Một tác vụ, một instance:** `MessageDigest` có state; không chia cùng instance cho nhiều file đang xử lý song song. Nếu tối ưu tái sử dụng object theo thread, phải đảm bảo `reset()` trước thông điệp mới và không chia sẻ đồng thời; tạo mới đơn giản hơn khi chi phí chấp nhận được.

## 12. Tự kiểm tra kiến thức

1. Một file 8 GiB có cần tạo mảng byte dài 8 GiB để lấy SHA-256 không? **Không**; chunk 64 KiB và `update` liên tục là đủ.
2. `InputStream.read(chunk)` trả 19 khi buffer có 65536 phần tử thì nên hash bao nhiêu byte? **19 byte**: `digest.update(chunk, 0, 19)`.
3. ByteBuffer đang có `position=19, limit=65536` sau `channel.read`; cần làm gì trước khi `digest.update`? **`flip()`**, để phạm vi đọc đúng 19 byte; sau update phải `clear()`.
4. Tại sao không gọi `digest()` sau mỗi chunk? Vì `digest()` kết thúc **một hash** và reset state; kết quả chunk riêng **khác** hash của phép nối byte.
5. File rỗng có digest không? **Có**: SHA-256 của luồng 0 byte là `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
6. Hai cách đọc cho cùng digest có chứng minh file do nhà phát hành công bố không? **Không**; phải đối chiếu với digest nguồn tin cậy hoặc xác minh chữ ký số.
7. Vì sao `lastModifiedTime` không chứng minh file ổn định khi hash? Có thể thay đổi rồi phục hồi timestamp, hoặc sửa trong lúc đọc. Cần bản snapshot hay quy trình phiên bản bất biến.
8. Có thể băm file 10 GiB bằng một `MappedByteBuffer` từ `FileChannel.map` Java 17 không? **Không** với một vùng map kiểu `MappedByteBuffer` duy nhất; cần chia cửa sổ map hoặc dùng vòng lặp `read`.

## Tài liệu API chính thức

- [MessageDigest (Java SE 21): update(ByteBuffer), digest(), reset(), isEqual()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html)
- [Files.newInputStream(Path)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html#newInputStream(java.nio.file.Path,java.nio.file.OpenOption...))
- [InputStream.read(byte[])](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStream.html)
- [FileChannel.read(ByteBuffer) và map()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html)
- [ByteBuffer: flip(), clear(), position(), limit()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html)
- [DigestInputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/DigestInputStream.html)
- [HexFormat (Java 17+)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HexFormat.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-functions-005">4.5 Phân biệt checksum, Object.hashCode và digest mật mã</a>

<details>
<summary>Xem chi tiết</summary>

# 4.5 Phân biệt checksum, Object.hashCode và digest mật mã

Ba cách gọi “hash” dễ bị đánh đồng trong Java: **checksum** để phát hiện lỗi truyền/lưu dữ liệu, **`hashCode()`** để hỗ trợ tìm kiếm và so sánh đối tượng trong các cấu trúc như `HashMap`, và **digest mật mã** để tạo dấu vân tay có tính kháng tấn công của một chuỗi byte. Chúng có thể cùng trả về các con số hoặc chuỗi hex, nhưng **mục tiêu, dữ liệu đầu vào, tính chất va chạm và mức độ an toàn hoàn toàn khác nhau**.

Bài học thuộc chủ đề **Hàm băm** và tập trung vào việc chọn đúng kỹ thuật trong Java, không nhầm “kiểm tra lỗi” với “xác thực nguồn gốc”.

## 1. Mục tiêu học tập

Sau khi hoàn thành, bạn có thể:

1. Phân biệt **checksum chống lỗi ngẫu nhiên**, **`hashCode()` phục vụ cấu trúc dữ liệu**, và **cryptographic digest** như SHA-256.
2. Nêu chính xác hợp đồng `equals()`/`hashCode()`, ý nghĩa va chạm và hậu quả khi khóa của `HashMap` bị thay đổi.
3. Biết tại sao CRC32/CRC32C/Adler32 khác SHA-256, và tại sao **SHA-256 riêng lẻ cũng không xác thực được người gửi**.
4. Viết Java 17+ tính checksum, `String.hashCode()` và SHA-256; đọc đúng từng kết quả và biết khi nào cần truyền **byte**, khi nào cần định nghĩa **đối tượng**.
5. Biết chọn checksum, digest, HMAC/chữ ký số hoặc password KDF theo yêu cầu thực tế.

## 2. Bảng đối chiếu nhanh

| Tiêu chí | Checksum | `Object.hashCode()` / phương thức `hashCode()` | Digest mật mã |
| --- | --- | --- | --- |
| Câu hỏi giải quyết | Byte có **vô tình** bị hỏng/thay đổi không? | Đặt/tìm đối tượng vào **bucket** nào và nhanh chóng lọc ứng viên so sánh? | Hai nội dung byte có cùng **dấu vân tay mật mã** không, dưới giả định thuật toán kháng va chạm? |
| Đầu vào thường gặp | Chuỗi byte theo định dạng đã xác định | Một đối tượng, qua phương thức `hashCode()` của lớp | Chuỗi byte bất kỳ; có thể đưa vào theo nhiều chunk |
| Ví dụ | CRC32, CRC32C, Adler32 | `String.hashCode()`, `record.hashCode()`, `Objects.hash(...)` | SHA-256, SHA-384/512, SHA-3 |
| Kết quả | Thường checksum ngắn (ví dụ CRC32 là 32 bit) | Java trả về **`int` có dấu 32 bit** | SHA-256 trả về **32 byte (256 bit)**; hiển thị hex thành 64 ký tự |
| Tính chất chính | Phát hiện tốt các dạng **lỗi truyền/lưu không chủ ý** theo thiết kế mã kiểm lỗi | Bằng nhau theo `equals()` thì **bắt buộc** có cùng hash; khác nhau **có thể** cùng hash | Được thiết kế chống tìm tiền ảnh, tiền ảnh thứ hai và va chạm một cách hiệu quả |
| Kẻ tấn công chủ động | Có thể sửa dữ liệu rồi tính lại checksum; CRC không phải cơ chế bảo mật | Có thể tạo nhiều đối tượng khác nhau cùng `hashCode()`; không xác thực và không bảo mật | Khó tạo va chạm có chủ đích với thuật toán mạnh, nhưng **ai cũng tính được digest** của dữ liệu họ chọn |
| Mục đích đúng | Kiểm lỗi gói tin, bản ghi, định dạng lưu trữ, hệ thống kiểm soát lỗi | `HashMap`, `HashSet`, khóa bảng băm, nhận diện ứng viên trước khi so `equals` | So khớp file với digest **đáng tin cậy**, content addressing, thành phần của HMAC/chữ ký số và giao thức mật mã |

**Ba câu cần nhớ:**

- **Checksum khác digest mật mã:** phát hiện byte hỏng ngẫu nhiên khác với chống người cố tình tạo nội dung giả trùng dấu vân tay.
- **`hashCode()` khác digest:** trả về cùng một số **không nói rằng hai đối tượng bằng nhau**, càng không nói dữ liệu chưa bị sửa.
- **Digest khác xác thực:** SHA-256 đủ mạnh về va chạm vẫn không chứng minh **ai** đã cung cấp byte hoặc ai công bố digest; muốn chống giả mạo nguồn gốc cần điểm neo tin cậy, MAC hoặc chữ ký số.

## 3. Checksum: phát hiện lỗi dữ liệu ngoài ý muốn

### 3.1. Cơ chế

Một checksum tính từ byte dữ liệu:

~~~text
Dữ liệu gốc: bytes ──checksum algorithm──> checksum A
Dữ liệu nhận: bytes ─checksum algorithm──> checksum B
A == B ?  →  nhiều khả năng dữ liệu không bị hỏng theo mô hình lỗi của thuật toán
~~~

Các giao thức lưu trữ/truyền tải thường đặt checksum bên cạnh nội dung để phát hiện lỗi do nhiễu tín hiệu, lỗi ghi đọc, lỗi đường truyền hoặc ghi nhận sai. **Không thể suy luận “A == B thì hai chuỗi byte chắc chắn giống nhau”**: đầu ra hữu hạn khiến va chạm tồn tại.

**CRC (Cyclic Redundancy Check)** diễn giải dữ liệu dưới dạng bit và tính số dư theo một đa thức quy ước. Thiết kế đa thức giúp phát hiện hiệu quả nhiều mẫu lỗi (chẳng hạn một bit bị lật hoặc nhiều lỗi burst trong phạm vi năng lực mã). CRC **không được thiết kế để ngăn người tấn công cố ý thay dữ liệu**, vì thuật toán và phép tính đều công khai, có cấu trúc đại số cho phép điều chỉnh/tính lại checksum.

- **CRC32 (`java.util.zip.CRC32`)** thường gặp trong ZIP/GZIP và nhiều định dạng cũ; với `"abc"` dưới UTF-8 có giá trị hex `352441c2`.
- **CRC32C (`java.util.zip.CRC32C`)** dùng đa thức Castagnoli, được nhiều hệ thống lưu trữ/truyền tải chọn; với đúng byte `"abc"` cho `364b3fb7`. **CRC32 và CRC32C không hoán đổi được**, dù cùng là checksum 32 bit. Bên gửi và bên nhận phải thống nhất biến thể, thứ tự byte và quy ước đóng gói.
- **Adler32 (`java.util.zip.Adler32`)** dùng phép cộng kiểm tra dạng khác, thường dùng trong zlib; `"abc"` cho `024d0127`. Nó cũng **không phải hàm băm mật mã**.
- Checksum rất đơn giản kiểu tổng byte đôi khi dùng cho nhu cầu phát hiện lỗi cơ bản, nhưng dễ bỏ sót nhiều dạng lỗi hơn CRC chuyên dụng.

Một CRC32 có **32 bit đầu ra** nên không thể ánh xạ duy nhất mọi thông điệp. Với hai giá trị phân phối gần đều, xác suất trùng ngẫu nhiên xấp xỉ `2^-32`; đây **không phải** xác suất bảo đảm chống sửa đổi có chủ đích. CRC có những quan hệ tuyến tính khiến kẻ tấn công có thể chủ động dựng dữ liệu cùng CRC theo nhiều cách.

Trong Java, `Checksum.update(byte[], offset, len)` nạp byte, `Checksum.getValue()` trả giá trị dưới dạng `long` (vì checksum 32 bit thường xem là **không dấu**), còn `reset()` cho phép tái sử dụng đối tượng. Muốn tạo checksum cho **hai thông điệp độc lập**, cần `reset()` trước thông điệp mới hoặc tạo đối tượng mới; đừng vô tình tính checksum của dữ liệu nối tiếp.

### 3.2. Checksum dùng khi nào?

Dùng checksum khi mục tiêu là **phát hiện sai hỏng không chủ ý** và giao thức đã quy định thuật toán. Ví dụ một gói dữ liệu trên đường truyền đã được **bảo vệ an ninh bằng cơ chế khác** vẫn có thể dùng CRC để kiểm lỗi hiệu quả ở tầng thấp. Nếu cần chứng minh **nhà phát hành đáng tin** hoặc chống đối thủ giả mạo, checksum **không thay** HMAC/chữ ký số.

Một checksum khớp là tín hiệu đáng tin **trong đúng mô hình lỗi và nguồn checksum đã xác định**, không phải bằng chứng không thể sửa hay bằng chứng danh tính.

## 4. `Object.hashCode()`: cơ chế hỗ trợ cấu trúc dữ liệu Java

### 4.1. `hashCode()` thực chất là gì?

`Object` khai báo phương thức:

~~~java
public int hashCode()
~~~

Lớp con có thể **ghi đè** để định nghĩa mã băm dựa trên trạng thái có ý nghĩa của đối tượng. `String.hashCode()` là ví dụ nổi bật; `record` trong Java cũng cung cấp triển khai `equals()`/`hashCode()` phù hợp với các thành phần record.

Một `HashMap<K,V>` dùng mã băm để xác định vị trí/nhóm bucket ứng viên, rồi sử dụng `equals()` để phân biệt các khóa có mã băm trùng nhau. `HashSet` áp dụng tương tự cho phần tử.

- **Hợp đồng bắt buộc:** Nếu `a.equals(b) == true` thì `a.hashCode() == b.hashCode()`.
- **Chiều ngược lại sai:** Hai đối tượng **không bằng nhau** vẫn có thể có cùng `hashCode()` (collision).
- Nếu trạng thái liên quan `equals` không đổi, gọi `hashCode()` nhiều lần trong cùng một lần chạy phải nhất quán. Không có yêu cầu chung rằng hash code của mọi lớp phải ổn định qua những lần chạy JVM riêng biệt.
- Nếu ghi đè `equals()`, cần ghi đè `hashCode()` nhất quán. Thiếu một trong hai sẽ làm các collection dựa trên hash hoạt động sai về mặt logic.

**Ví dụ va chạm có thể tái hiện:**

~~~text
"Aa".hashCode() = 2112
"BB".hashCode() = 2112
"Aa".equals("BB") = false
~~~

Điều này **không khiến `HashMap` coi `"Aa"` và `"BB"` là cùng khóa**. Nó phải kiểm tra `equals()` sau khi các ứng viên cùng nhóm hash. Bảng băm chịu được va chạm; va chạm dày đặc có thể ảnh hưởng hiệu năng, nên chất lượng phân phối hash code vẫn rất quan trọng.

### 4.2. `Object` mặc định, `String` và `Objects` không giống nhau

- `new Object().hashCode()` dùng triển khai mặc định của `Object`, thường gắn với nhận diện đối tượng trong JVM, nhưng **không được Java cam kết** là địa chỉ bộ nhớ, giá trị duy nhất hoặc định danh bền vững.
- `String.hashCode()` ghi đè theo nội dung chuỗi với thuật toán xác định trong đặc tả Java; nhiều chuỗi khác nhau vẫn có thể va chạm. Nó là một `int` và **không** phải SHA-256.
- `System.identityHashCode(obj)` lấy kiểu hash nhận diện của `Object` ngay cả khi lớp ghi đè `hashCode()`; vẫn không duy nhất, không bền vững và không có tính chất mật mã.
- `Objects.hashCode(obj)` là tiện ích xử lý `null` (trả 0 nếu null, ngược lại gọi `obj.hashCode()`); `Objects.hash(fields...)` kết hợp nhiều trường theo cách phục vụ hợp đồng Java, **không phải hàm băm mật mã**. Đối với mã hiệu năng nhạy cảm, cân nhắc chi phí tạo mảng varargs từ `Objects.hash(...)`.

**Khóa có thể thay đổi là bẫy:** Nếu bạn đưa một đối tượng vào `HashMap` rồi sửa trường tham gia `equals/hashCode`, lần tra cứu sau có thể tìm ở bucket khác và thất bại dù đối tượng vẫn tồn tại trong map. Ưu tiên **khóa bất biến**, ví dụ `String` hoặc `record` chứa các thành phần bất biến, và không thay đổi trạng thái làm nên danh tính khóa.

Một hash code 32 bit cũng **không phải ID duy nhất**, fingerprint nội dung, khóa bảo mật hoặc giá trị phù hợp để đưa ra quyết định quyền truy cập.

## 5. Digest mật mã: dấu vân tay byte có tính kháng tấn công

### 5.1. Cơ chế và tính chất

Một hàm băm mật mã như SHA-256 nhận chuỗi byte độ dài bất kỳ rồi trả digest độ dài cố định:

~~~text
UTF-8("abc") → [0x61, 0x62, 0x63]
SHA-256(bytes) → 32 byte
Hex biểu diễn digest → ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
~~~

**Hex không phải digest khác:** Nó chỉ là cách hiển thị digest nhị phân. Với SHA-256, 32 byte hiển thị thành 64 ký tự hex. Base64 cũng có thể biểu diễn chính 32 byte ấy dưới dạng text khác.

Một hash mật mã an toàn hướng tới:

1. **Preimage resistance:** biết `H(m)`, khó tìm được một `m` cho ra đúng digest ấy.
2. **Second-preimage resistance:** biết trước `m`, khó tìm `m' != m` có cùng digest.
3. **Collision resistance:** khó chủ động tìm **bất kỳ** cặp `m != m'` nào có cùng digest. Với digest lý tưởng 256 bit, chi phí tìm va chạm theo phương pháp sinh nhật cỡ `2^128` phép thử, khác với không gian tất cả `2^256` digest.

“Khó” có nghĩa về **chi phí tính toán khi dùng đúng thuật toán/độ dài**, không có nghĩa toán học không tồn tại va chạm. Bởi vậy digest thường phù hợp so khớp nội dung hoặc content-addressing, nhưng vẫn cần xác minh byte, ngữ cảnh, thuật toán và nguồn digest tùy mục đích.

- **SHA-256** là lựa chọn phổ biến và có sẵn trong JDK qua `MessageDigest.getInstance("SHA-256")`.
- **SHA-512/SHA-3** có thể phù hợp khi giao thức/yêu cầu chỉ rõ. Đừng tự thay thuật toán hai đầu nếu định dạng cố định.
- **MD5 và SHA-1** có vấn đề va chạm nghiêm trọng; không chọn cho yêu cầu chống kẻ tấn công tạo nội dung giả trùng digest.
- **SHA-256 không dùng để lưu mật khẩu trực tiếp:** mật khẩu thường có entropy thấp. Dùng password hashing/KDF chậm và có salt riêng (Argon2id, scrypt hoặc PBKDF2 theo hệ sinh thái và thông số an toàn), khác việc hash một file.

### 5.2. Digest không tự xác thực nguồn gốc

Giả sử website cho tải `app.zip` và cũng đặt `SHA256SUMS` ngay cùng website. Nếu attacker chiếm website, họ có thể **thay cả file và dòng SHA-256** cho khớp file độc hại. Kết quả `MessageDigest` của bạn sẽ khớp nhưng **không chứng minh đúng nhà phát hành**.

Chọn bổ sung theo mô hình đe dọa:

- **Digest từ kênh đáng tin độc lập**: có thể dùng làm giá trị kỳ vọng để phát hiện thay đổi trên bản tải về.
- **HMAC**: hai bên có cùng secret có thể tạo/xác minh thẻ xác thực gắn với byte dữ liệu; phù hợp xác thực thông điệp trong hệ thống cùng chia sẻ khóa.
- **Chữ ký số**: bên ký giữ private key; người nhận dùng **public key đã được xác thực** để kiểm chữ ký, phù hợp phân phối artifact cho nhiều bên kiểm tra.
- **TLS/AEAD**: khi cần bảo vệ nội dung trong quá trình truyền hoặc lưu trữ, dùng giao thức/thuật toán phù hợp; SHA-256 **không mã hóa** nội dung.

Cả checksum, `hashCode()` lẫn SHA-256 đều không cần secret để tính. **Sự khác biệt về bảo mật nằm ở tính chất toán học và cách sử dụng**, còn bằng chứng danh tính phải đến từ giao thức tin cậy.

## 6. Đối tượng Java và chuỗi byte không phải cùng dữ liệu đầu vào

Muốn so sánh chính xác, phải xác định **đang băm cái gì**.

| Đầu vào/thao tác | Ý nghĩa |
| --- | --- |
| `"abc".hashCode()` | Mã băm theo nội dung `String` dùng cho collection; giá trị `int` là `96354`. |
| `CRC32.update("abc".getBytes(UTF_8))` | Checksum của **3 byte** UTF-8: `61 62 63`. |
| `SHA-256("abc".getBytes(UTF_8))` | Digest mật mã của **cùng 3 byte** UTF-8, 32 byte kết quả. |
| `sha256(fileBytes)` | Hash chính xác byte của file, **không** mặc nhiên là hash chuỗi filename hay nội dung đã normalize. |
| `sha256(serialize(object))` | Hash bản serialize theo quy tắc đã định; thay serialization có thể thay digest dù đối tượng có cùng ý nghĩa nghiệp vụ. |

**Những khác biệt thường bị bỏ sót:**

- **Encoding:** `UTF-8` khác `UTF-16`. `String.hashCode()` dựa trên các đơn vị ký tự UTF-16 của `String`, trong khi `MessageDigest` nhận **byte**; phải chọn `StandardCharsets.UTF_8` hoặc định dạng đã thống nhất.
- **Xuống dòng:** `"abc"`, `"abc\n"`, file dùng LF và file dùng CRLF có byte khác nhau → SHA-256/CRC khác.
- **Unicode normalization:** hai chuỗi trông giống nhau có thể có chuỗi mã Unicode/byte khác nhau (ví dụ ký tự có dấu ở dạng kết hợp và dựng sẵn). Chỉ normalize **nếu giao thức quy định** rồi băm đúng bản đã được chuẩn hóa.
- **JSON và dữ liệu có cấu trúc:** khác thứ tự field, khoảng trắng hoặc biểu diễn số có thể cho byte khác dù nghiệp vụ coi tương đương. Nếu cần ký/hash dữ liệu có cấu trúc qua nhiều ngôn ngữ, thống nhất **canonical serialization** trước.
- **Định dạng/lý lịch thuật toán:** gửi cùng checksum cần kèm hoặc quy định rõ CRC32 hay CRC32C, cách đóng gói và byte order. Với digest, cần biết SHA-256 hay thuật toán khác; biểu diễn hex có thể chữ hoa/chữ thường nhưng **byte digest** mới là bản chất.

**Không sử dụng `Object.toString()`, `hashCode()` hoặc serialize mặc định tùy tiện để tạo digest bền vững của đối tượng.** Chúng không hứa hẹn canonicalization, ổn định liên phiên bản hay mức độ xác thực phù hợp.

## 7. Ví dụ Java 17+: một chương trình, ba loại giá trị

Ví dụ dùng API chuẩn trong JDK, không cần Maven hay thư viện ngoài. Chương trình cố tình chọn `"Aa"` và `"BB"` để tạo **va chạm `String.hashCode()`**, rồi chứng minh `HashMap` vẫn lưu được cả hai khóa. Đồng thời tính checksum và digest của `"abc"`.

Lưu dưới tên **`ChecksumHashDigestDemo.java`**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

public class ChecksumHashDigestDemo {
    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static String checksum(Checksum checksum, byte[] data) {
        checksum.update(data, 0, data.length);
        return "%08x".formatted(checksum.getValue());
    }

    static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = utf8("abc");
        System.out.println("CRC32(abc)     = " + checksum(new CRC32(), bytes));
        System.out.println("CRC32C(abc)    = " + checksum(new CRC32C(), bytes));
        System.out.println("Adler32(abc)   = " + checksum(new Adler32(), bytes));
        System.out.println("SHA-256(abc)   = "
                + HexFormat.of().formatHex(sha256(bytes)));

        String first = "Aa";
        String second = "BB";
        System.out.println("hashCode(Aa)   = " + first.hashCode());
        System.out.println("hashCode(BB)   = " + second.hashCode());
        System.out.println("Aa equals BB?  = " + first.equals(second));

        Map<String, String> values = new HashMap<>();
        values.put(first, "first");
        values.put(second, "second");
        System.out.println("Map size       = " + values.size());
        System.out.println("Map[BB]        = " + values.get("BB"));

        System.out.println("SHA equals?    = " + MessageDigest.isEqual(
                sha256(utf8(first)), sha256(utf8(second))));
        System.out.println("CRC32 matches? = " + checksum(new CRC32(), bytes)
                .equals(checksum(new CRC32(), utf8("abd"))));
    }
}
~~~

Chạy:

~~~bash
javac ChecksumHashDigestDemo.java
java ChecksumHashDigestDemo
~~~

Kết quả chính xác với JDK 17+:

~~~text
CRC32(abc)     = 352441c2
CRC32C(abc)    = 364b3fb7
Adler32(abc)   = 024d0127
SHA-256(abc)   = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
hashCode(Aa)   = 2112
hashCode(BB)   = 2112
Aa equals BB?  = false
Map size       = 2
Map[BB]        = second
SHA equals?    = false
CRC32 matches? = false
~~~

**Phân tích output:**

1. CRC32, CRC32C và Adler32 nhận **cùng 3 byte** nhưng tạo kết quả khác nhau do thuật toán khác nhau. Không dùng giá trị từ thuật toán này để xác minh thuật toán kia.
2. SHA-256 nhận đúng các byte ấy nhưng kết quả dài 256 bit, có các mục tiêu chống tấn công mà CRC không có. Digest hex dài 64 ký tự.
3. `"Aa"` và `"BB"` **cùng `hashCode` nhưng `equals()` trả `false`**. `HashMap` dùng cả mã băm và quan hệ bằng nhau nên có hai entry; không có chuyện cứ trùng hash là bị ghi đè khóa.
4. SHA-256 của `"Aa"` và `"BB"` không bằng nhau. Đây **không** chứng minh SHA-256 không bao giờ va chạm; chỉ là phép kiểm cụ thể cho hai thông điệp này.
5. Checksum của `"abc"` và `"abd"` khác nhau trong ví dụ. **Không** suy ra CRC có thể chống người cố ý sửa byte rồi tạo checksum trùng.

Ở ví dụ, mỗi checksum dùng đối tượng `new CRC32()`/`new CRC32C()`/`new Adler32()` riêng, tránh cộng dồn trạng thái giữa các thông điệp. `MessageDigest.getInstance("SHA-256").digest(data)` tính digest trên byte; `HexFormat` chỉ chuyển digest thành ký tự hex. `MessageDigest.isEqual` so sánh hai mảng digest mà không dừng ngay ở byte khác đầu tiên; với các giao thức xác thực, vẫn phải xác minh tất cả điều kiện đầu vào và độ dài theo thiết kế.

### 7.1. Hash file lớn bằng luồng, tránh đọc toàn bộ vào RAM

Khi file có thể lớn, đừng buộc `Files.readAllBytes(...)` nạp hết nội dung. Dùng `MessageDigest.update(...)` nhiều lần; kết quả tương đương hash một lần với **đúng chuỗi byte nối lại**, không phụ thuộc cách chia các chunk.

Đoạn **phương thức minh họa sau đây cần đặt trong một class Java**, không phải một chương trình độc lập. Nó sử dụng các import `java.io.InputStream`, `java.nio.file.Files`, `java.nio.file.Path` và `java.security.MessageDigest`:

~~~java
static byte[] sha256File(Path file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = Files.newInputStream(file)) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
        }
    }
    return digest.digest();
}
~~~

Gọi `HexFormat.of().formatHex(sha256File(Path.of("archive.zip")))` để nhận chuỗi hex cho byte file thực tế. Muốn kiểm tra bản tải, so kết quả với digest kỳ vọng từ **nguồn tin cậy**. Ghi nhận thêm thuật toán, thời điểm, tên artifact và kênh nhận digest để giảm nhập nhằng trong vận hành.

**Lưu ý API:** Các đối tượng `MessageDigest`/`Checksum` có trạng thái bên trong. Không chia sẻ cùng một instance cho các luồng đang cập nhật độc lập nếu không có đồng bộ và quy tắc reset rõ ràng. Ví dụ đầy đủ tạo instance cho mỗi phép tính.

## 8. Chọn đúng kỹ thuật trong tình huống thực tế

| Tình huống | Kỹ thuật thích hợp | Lý do |
| --- | --- | --- |
| Đọc bản ghi từ đĩa, muốn phát hiện bit hỏng ngẫu nhiên | Checksum theo chuẩn định dạng, như CRC32C | Nhanh, tập trung vào lỗi ngẫu nhiên; chưa chứng minh nguồn gốc |
| Một định dạng ZIP/GZIP quy định CRC32 | **CRC32 chính xác theo đặc tả** | Không được thay bằng CRC32C hoặc SHA-256 tùy ý |
| Triển khai `HashMap<CustomerKey, Order>` | `equals()` và `hashCode()` đúng hợp đồng; khóa bất biến | Chỉ phục vụ phân phối/tìm kiếm khóa |
| Dùng mã băm làm khóa định danh file phát hành | **SHA-256 của byte file** | Tính kháng va chạm tốt hơn checksum/hashCode cho content ID |
| Kiểm file tải xuống từ máy chủ có thể bị giả mạo | SHA-256 **từ kênh tin cậy** hoặc chữ ký số với khóa công khai đã xác thực | Digest tự công bố kèm file không chống kẻ sửa cả hai |
| Xác thực webhook giữa hai dịch vụ có shared secret | **HMAC-SHA256** cộng quy tắc chống replay | SHA-256 trần không có khóa xác thực |
| Cho nhiều khách hàng kiểm tra nhà phát hành bản cập nhật | **Chữ ký số**, ví dụ Ed25519 | Người kiểm có public key; chỉ người giữ private key ký |
| Lưu mật khẩu người dùng | **Password hashing/KDF** phù hợp, salt riêng và tham số chi phí đúng | SHA-256 đơn lẻ quá nhanh và không giải quyết tấn công đoán mật khẩu |
| Dùng CRC hoặc SHA-256 làm “mã hóa dữ liệu” | **Không đúng mục tiêu**; cần encryption nếu dữ liệu phải bí mật | Cả checksum lẫn digest đều không cung cấp cách giữ bí mật nội dung |

### 8.1. Case study: “File tải về và hash khớp mà vẫn nhiễm mã độc”

Quy trình yếu:

~~~text
Trang bị chiếm quyền
  ├── file release.zip  bị thay thành file độc hại
  └── SHA256SUMS       được tính lại tương ứng
Người dùng tải cả hai → SHA-256 khớp → vẫn có thể nhận file độc hại
~~~

Quy trình mạnh hơn: lấy khóa công khai nhà phát hành qua **kênh tin cậy**, xác minh chữ ký trên đúng artifact hoặc manifest đã định nghĩa, đồng thời kiểm dữ liệu và chính sách phiên bản. Digest hỗ trợ ràng buộc byte, chữ ký bảo vệ giá trị tham chiếu/người ký. Cơ chế phân phối khóa và thu hồi khóa vẫn quan trọng.

### 8.2. Case study: “Tôi lưu `hashCode()` của object vào database để deduplicate”

Thiết kế này sai vì:

- Chỉ có 32 bit, va chạm là bình thường khi dữ liệu tăng.
- Cách tính phụ thuộc lớp, trường, triển khai `equals/hashCode`; thay phiên bản code có thể thay giá trị.
- Một số hash code mặc định gắn với nhận diện đối tượng, không ổn định sau khi tạo đối tượng mới hay restart.
- Hai object được coi bằng nhau theo logic Java chưa chắc có cùng **byte serialize**, và ngược lại.

Muốn content-addressing, hãy tạo **serialization canonical** của những trường định nghĩa nội dung, rồi băm bằng SHA-256 (cùng namespace/schema version để tránh nhập nhằng). Vẫn phải dùng các ràng buộc dữ liệu và so sánh thích hợp nếu cần chắc chắn về danh tính nghiệp vụ, không coi digest là quyền sở hữu hay chữ ký.

### 8.3. Case study: “CRC32 mismatch nhưng SHA-256 match”

Nếu hai bên thực sự tính trên **chính xác cùng byte** và cùng thuật toán SHA-256, digest khớp nhưng CRC32 tính khác có thể do **quy trình CRC khác nhau**: một bên dùng CRC32C, quên reset `Checksum`, băm dữ liệu khác phạm vi, hoặc biểu diễn/đóng gói số dư theo endianness khác. Đừng vội suy ra một primitive “bị lỗi”. Đối chiếu byte đầu vào, thuật toán, thứ tự chunk, độ dài và metadata trước.

Nếu cả hai phép tính đều dùng chung thuật toán và byte chuẩn, về mặt toán học digest và CRC mỗi loại vẫn có khả năng va chạm riêng. Vì vậy **tránh khẳng định tuyệt đối** chỉ từ sự khớp của một giá trị băm.

## 9. Lỗi thường gặp khi code Java

| Lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Dùng `hashCode()` để phát hiện file bị sửa | Va chạm và không ổn định/không phù hợp với byte file | SHA-256 hoặc checksum phù hợp; định nghĩa nguồn tham chiếu đáng tin |
| Ghi đè `equals()` nhưng không ghi đè `hashCode()` | `HashMap`/`HashSet` có thể không tra cứu đúng khóa bằng nhau | Viết và kiểm tra cả hai theo cùng trường định danh |
| Dùng key mutable trong `HashMap` và đổi trường ảnh hưởng hash | Khóa có thể “biến mất” khi tra cứu | Dùng khóa bất biến hoặc không thay đổi trường định danh |
| Tưởng `hashCode(a)==hashCode(b)` là `a.equals(b)` | Gộp nhầm dữ liệu khi có collision | Dùng `equals` để kết luận bằng nhau |
| Thay CRC32 bằng CRC32C vì đều 32 bit | Hai đầu ra khác nhau, kiểm lỗi không tương thích | Giữ đúng biến thể, seed, xử lý byte và cách đóng gói giao thức |
| Không reset đối tượng `Checksum` giữa hai payload | Kết quả của payload thứ hai cộng dồn payload đầu | Dùng instance mới hoặc `reset()` đúng chỗ |
| Hash `String.getBytes()` mà không chỉ rõ charset | Byte thay đổi theo môi trường/cấu hình và giao thức khó kiểm | Dùng `StandardCharsets.UTF_8` hoặc charset đã quy định |
| Hash JSON ở một bên với field order khác bên còn lại | Digest/tag không khớp | Dùng cùng canonicalization/serialization |
| So SHA-256 với digest nhận từ chính nguồn bị tấn công | False sense of security | Dùng nguồn digest tin cậy, HMAC hoặc chữ ký số |
| Dùng MD5/SHA-1 chống dữ liệu giả mạo | Bị khai thác va chạm trong nhiều kịch bản | Chọn SHA-256/SHA-3 theo giao thức đã thẩm định |
| Băm mật khẩu bằng SHA-256 đơn thuần | Dễ thử đoán offline với số lượng lớn | Dùng Argon2id/scrypt/PBKDF2, salt riêng, cost phù hợp |
| Đưa `MessageDigest` hay `Checksum` chung cho nhiều thread | Race condition, kết quả bị trộn trạng thái | Tạo instance riêng hoặc kiểm soát đồng bộ |
| Dùng digest để thay mã hóa | Digest không giúp người có quyền giải mã lại dữ liệu và không che bí mật của dữ liệu có entropy thấp | Dùng encryption cho tính bí mật, digest cho fingerprint |

## 10. Bài tập tự kiểm tra (có đáp án)

**Bài 1.** Hai `String` khác nhau có cùng `hashCode()`. `HashMap` có bắt buộc xem chúng là cùng khóa?

**Đáp án:** Không. `HashMap` dùng hash để khoanh vùng và `equals()` để kiểm khóa thực sự bằng nhau. `"Aa"` và `"BB"` là ví dụ thực tế: hash 2112 như nhau nhưng `equals` false.

**Bài 2.** Sau khi `hashCode()` của một khóa mutable thay đổi, vì sao `map.get(key)` có thể không tìm lại được giá trị?

**Đáp án:** Map có thể tìm theo bucket của mã băm **mới**, còn entry đã nằm ở bucket theo mã băm **cũ**. Cần giữ ổn định các trường tham gia `equals/hashCode` trong lúc object được dùng làm key.

**Bài 3.** File A và B cùng checksum CRC32. Có kết luận chúng giống hệt nhau và do người phát hành đáng tin tạo ra không?

**Đáp án:** Không. CRC32 là 32 bit nên có va chạm và không chống sửa đổi có chủ đích; không có cơ chế xác thực danh tính.

**Bài 4.** Tại sao CRC32(`"abc"`) và CRC32C(`"abc"`) lại khác dù đều 32 bit?

**Đáp án:** Chúng sử dụng **đa thức/thuật toán CRC khác nhau**. Tên số “32” chỉ độ rộng checksum, không khẳng định công thức tương thích.

**Bài 5.** Bạn tải `installer.jar` và `installer.sha256` từ cùng server. Hash khớp; điều này đủ bảo vệ trước server bị xâm nhập chưa?

**Đáp án:** Chưa. Kẻ tấn công có thể thay cả file lẫn digest. Cần điểm neo tin cậy độc lập, ví dụ checksum từ kênh được xác thực hoặc chữ ký số với public key đáng tin.

**Bài 6.** Hai service cùng tính SHA-256 của cùng ý nghĩa JSON, nhưng digest khác nhau. Phải sửa hàm SHA-256 không?

**Đáp án:** Thường không. Kiểm tra byte nguồn, UTF-8, thứ tự field, khoảng trắng, newline, Unicode normalization và canonicalization trước. SHA-256 hoạt động trên **byte**, không dựa vào ý nghĩa JSON.

**Bài 7.** Khi nào cần HMAC-SHA256 thay vì SHA-256 trên request webhook?

**Đáp án:** Khi cần xác thực bên gửi **cùng giữ secret**, chống người không có secret tự tạo request hợp lệ. Giao thức cũng phải có cơ chế chống replay; HMAC một mình không chặn phát lại request nguyên vẹn.

## 11. Checklist chọn thuật toán

Trước khi viết code, hãy tự hỏi tuần tự:

1. **Có đối thủ chủ động sửa/giả mạo không?** Nếu chỉ kiểm lỗi ngẫu nhiên và định dạng quy định CRC/Adler, dùng checksum. Nếu có đối thủ chủ động, không dừng ở CRC.
2. **Đang băm object hay bytes?** Nếu để triển khai khóa `HashMap`, làm đúng `equals/hashCode` và giữ khóa bất biến. Nếu băm file/message, xác định chính xác byte.
3. **Có cần dấu vân tay khó va chạm trước đối thủ không?** Chọn SHA-256 hoặc thuật toán được giao thức chỉ định, nhận byte qua `MessageDigest`.
4. **Digest kỳ vọng từ đâu và ai được quyền công bố?** Nếu cần xác thực nguồn, thêm HMAC/chữ ký số hoặc nguồn tham chiếu đáng tin. Không coi SHA-256 tự động là chữ ký.
5. **Có cần giữ bí mật hay lưu mật khẩu không?** Dùng encryption/AEAD cho tính bí mật và password KDF cho mật khẩu; không gán công việc đó cho checksum, `hashCode()` hay SHA-256 đơn lẻ.

**Kết luận trọng tâm:** **checksum** đo sai hỏng dữ liệu theo mô hình lỗi; **`hashCode()`** phục vụ quan hệ bằng nhau và cấu trúc tra cứu trong Java; **digest mật mã** tạo fingerprint có tính kháng va chạm và tiền ảnh. Kết quả giống hình thức số/hex nhưng **không thể thay thế cho nhau**, và không cái nào tự cung cấp danh tính người gửi khi đứng riêng lẻ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-functions-006">4.6 So sánh digest bằng MessageDigest.isEqual và phạm vi bảo đảm của API</a>

<details>
<summary>Xem chi tiết</summary>

# 4.6. So sánh digest bằng MessageDigest.isEqual và phạm vi bảo đảm của API

## 1. Bối cảnh: so sánh giá trị băm có thể liên quan đến bảo mật

Digest (giá trị băm) là một mảng byte được tạo từ thông điệp bằng thuật toán băm, ví dụ `SHA-256` trả về **32 byte (256 bit)**. Trong Java, ta thường cần so sánh:

- Digest tính lại từ dữ liệu với digest kỳ vọng đã lưu để phát hiện dữ liệu bị thay đổi.
- Giá trị HMAC nhận được với HMAC do bên xác minh tính bằng **khóa bí mật**.
- Các giá trị mật mã mà thời gian xác minh có thể bị bên ngoài đo, dù thông tin sai khác bên trong phải được hạn chế lộ.

Nếu cách so sánh thoát sớm ngay khi gặp byte khác đầu tiên, thời gian thực thi có thể phụ thuộc vị trí byte sai đầu tiên. `java.security.MessageDigest.isEqual` là một API so sánh hai byte array có **đặc tính hạn chế kiểu rò rỉ này**, trong phạm vi được tài liệu Java công bố.

Cần phân biệt hai câu hỏi: **hai mảng byte có bằng nhau không?** và **nguồn dữ liệu có đáng tin không?** Phương thức `isEqual` chỉ trả lời câu thứ nhất; câu thứ hai cần thuật toán, nguồn giá trị kỳ vọng và chính sách an ninh thích hợp.

## 2. Chữ ký, dữ liệu đầu vào và giá trị trả về

API:

```java
public static boolean isEqual(byte[] digesta, byte[] digestb)
```

Đây là **phương thức static**: gọi `MessageDigest.isEqual(expected, actual)` mà không cần tạo đối tượng `MessageDigest`. Nó **không tự tính digest**, không gọi SHA-256/HMAC và không xác thực algorithm/provider đã dùng để tạo mảng.

Với **hai mảng byte không null**:

- Trả `true` nếu độ dài bằng nhau và từng byte cùng vị trí bằng nhau.
- Trả `false` nếu độ dài khác hoặc có ít nhất một byte khác.
- Không tự chuyển hex/Base64 thành byte, không bỏ qua khoảng trắng, không chuẩn hóa Unicode, không cho phép prefix khớp được tính là toàn bộ digest.
- Việc digest bằng nhau không phải chứng minh toán học hai **thông điệp gốc** giống nhau: một hàm băm hữu hạn đầu ra luôn có khả năng collision; độ khó tìm collision phụ thuộc thuật toán băm.

**Không dùng `==` hoặc `byte[].equals` để kiểm tra nội dung hai digest.** Đối với array, cả hai là phép so sánh tham chiếu; hai mảng khác object nhưng cùng 32 byte SHA-256 sẽ được xem là khác bằng `==`.

Ví dụ tối thiểu (đoạn mã đặt trong một chương trình đã import `MessageDigest` và `StandardCharsets`):

```java
byte[] a = MessageDigest.getInstance("SHA-256")
        .digest("hello".getBytes(StandardCharsets.UTF_8));
byte[] b = MessageDigest.getInstance("SHA-256")
        .digest("hello".getBytes(StandardCharsets.UTF_8));
boolean equal = MessageDigest.isEqual(a, b); // true
```

Ở đây `MessageDigest.digest` thực hiện phép băm, còn `MessageDigest.isEqual` chỉ thực hiện so sánh. Java không tự ghi nhớ nguồn, thuật toán hay ý nghĩa mật mã của mỗi byte array.

## 3. Timing side-channel: vì sao so sánh thông thường chưa đủ?

Xét đoạn mã tự viết sau:

```java
// Ví dụ để PHÂN TÍCH, không dùng cho so sánh dữ liệu mật mã.
static boolean earlyExit(byte[] a, byte[] b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) {
        if (a[i] != b[i]) return false;
    }
    return true;
}
```

Với hai mảng cùng độ dài, sai ở byte đầu làm vòng lặp dừng ngay; sai ở byte cuối khiến vòng lặp đi gần hết. Trong một giao thức cụ thể, đối thủ có khả năng gửi nhiều phép thử và đo thời gian có thể phân tích tương quan này. Khả năng khai thác phụ thuộc độ nhiễu mạng, quyền truy cập, thiết kế protocol, tốc độ máy và nhiều yếu tố; vài phép đo nhanh/chậm **không** đủ để chứng minh có lỗ hổng.

So sánh các cách viết phổ biến:

| Cách viết | Kiểm tra nội dung byte? | Phù hợp để so sánh MAC/digest khi cần hạn chế timing leak? |
| --- | --- | --- |
| `a == b` | Không, kiểm tra reference | Không |
| `a.equals(b)` với `byte[]` | Không, mảng kế thừa `Object.equals` | Không |
| `Arrays.equals(a, b)` | Có | Không có hợp đồng thời gian dành cho mục tiêu mật mã; có đường kết thúc sớm |
| `String.equals` với chuỗi hex/Base64 | So sánh ký tự | Không bảo đảm thời gian cho dữ liệu bí mật |
| `MessageDigest.isEqual(a, b)` | Có | Được thiết kế để giảm rò rỉ vị trí sai khác, theo phạm vi API |

`Arrays.equals` thường là lựa chọn đúng cho byte array **không nhạy cảm về timing**. Với digest/tag cần so sánh tránh rò rỉ vị trí byte sai, nên chọn API chuyên dụng `MessageDigest.isEqual`.

**Không biến byte digest thô thành `String` bằng UTF-8 rồi so sánh.** Digest là byte tùy ý, không bảo đảm UTF-8 hợp lệ. Nếu giao thức truyền digest bằng Base64/hex, hãy giải mã chặt chẽ về `byte[]` rồi so sánh.

## 4. “Constant-time” của isEqual bảo đảm tới đâu?

### 4.1. Điều Java mô tả

Tài liệu Java SE 17 cho `isEqual` ghi trong **Implementation Note** rằng các byte của `digesta` được xét khi so sánh và thời gian tính toán được thiết kế phụ thuộc vào **độ dài `digesta`**, không phụ thuộc nội dung hai mảng hay độ dài `digestb`. Tài liệu các JDK mới hơn, ví dụ Java SE 24, giải thích rõ ngoại lệ khi `digestb` là `null` hoặc có độ dài **0 byte**.

Vì vậy với đầu vào thông thường là **hai mảng không null, không rỗng**, ta nên đặt digest/tag *kỳ vọng có độ dài cố định* ở đối số đầu: `MessageDigest.isEqual(expectedTag, receivedTag)`. Với HmacSHA256 không cắt ngắn, expected tag là 32 byte.

Khi so sánh theo hướng đó, logic không cần dừng ngay ở vị trí byte đầu tiên không khớp. Đây là **một đặc tính thuật toán của phép so sánh**, không phải chứng minh mọi đường thực thi đều có cùng thời gian tường (wall-clock).

### 4.2. Những ngoại lệ và giới hạn quan trọng

1. **Đối số thứ nhất có thể thay đổi độ dài.** Implementation Note cho phép thời gian phụ thuộc `digesta.length`. Nếu độ dài này được điều khiển bởi bên không tin cậy, không suy ra thời gian độc lập mọi chiều dài đầu vào.
2. **`null` và mảng rỗng được xử lý đặc biệt.** Một số đường trong OpenJDK có fast-path khi `digesta == digestb`, khi một mảng là `null`, hoặc `digestb.length == 0`. Do đó, không khẳng định “mọi trường hợp đều đi qua đủ byte”. API không phải hợp đồng an ninh cho phép nhận `null`/rỗng như tag hợp lệ.
3. **Trường hợp cùng tham chiếu:** OpenJDK có thể trả `true` ngay khi hai tham số trỏ tới cùng một byte array. Không dùng alias/same-reference để benchmark thuộc tính timing; đối với xác minh thật, expected tag được tính lại độc lập với dữ liệu nhận.
4. **Độ dài tag không nhất thiết là bí mật.** Ứng dụng có thể từ chối nhanh tag sai độ dài vì định dạng chuẩn đã quy định 32 byte. Nếu độ dài chính nó là thông tin bí mật, cần phân tích thiết kế bao trùm hơn thay vì trông chờ `isEqual` che giấu.
5. **Các bước xung quanh vẫn có thời gian riêng:** parse request, kiểm tra quyền, decode Base64, tính SHA/HMAC, đọc DB, lỗi JSON, exception và trả HTTP đều nằm ngoài hợp đồng thời gian của `isEqual`.
6. **Máy ảo và phần cứng có nhiễu:** JIT, garbage collection, CPU cache, scheduling, tối ưu hóa compiler và network khiến thời gian quan sát thực tế biến đổi. “Constant-time” trong tài liệu không có nghĩa cùng số nano-giây trong mọi môi trường.
7. **Không bảo đảm an toàn khi thuật toán hoặc nguồn kỳ vọng sai.** So sánh SHA-256 không khóa với digest do client tự khai báo không biến nó thành xác thực; dùng API đúng không sửa được thiết kế mật mã sai.

Bảng trạng thái để tránh nhầm:

| Hai đối số | Kết quả logic cần kỳ vọng | Lưu ý về bảo mật |
| --- | --- | --- |
| Không null, cùng độ dài và cùng byte | `true` | Mặc định của so sánh nội dung |
| Không null, cùng độ dài nhưng khác ít nhất một byte | `false` | Không cần tự viết vòng lặp dừng sớm |
| Không null, khác độ dài | `false` | Không chấp nhận tag bị cắt nếu giao thức đòi đủ độ dài |
| Cùng một tham chiếu byte array | `true` | Có fast-path trong OpenJDK; không dùng để kiểm thử timing |
| `null` hoặc tag rỗng | Bên gọi **tự từ chối trước** | Đừng dựa vào hành vi triển khai của trường hợp biên |

## 5. Ví dụ Java 17+ độc lập: SHA-256 và xác minh HMAC

Chương trình sau minh họa hai mục đích khác nhau:

- **SHA-256:** so sánh digest của cùng thông điệp, thông điệp khác và digest bị cắt ngắn.
- **HmacSHA256:** tạo khóa ngẫu nhiên cho demo; tính tag bằng `Mac` và kiểm tra bằng `MessageDigest.isEqual`; sửa thông điệp, sửa tag hoặc thiếu tag đều bị từ chối.

Tạo file `DigestCompareDemo.java`:

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public class DigestCompareDemo {
    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] data) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static byte[] hmacSha256(byte[] data, SecretKey key)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        return mac.doFinal(data);
    }

    private static boolean verifyHmac(byte[] data, byte[] suppliedTag,
            SecretKey key) throws GeneralSecurityException {
        byte[] expectedTag = hmacSha256(data, key);
        if (suppliedTag == null || suppliedTag.length != expectedTag.length) {
            return false;
        }
        return MessageDigest.isEqual(expectedTag, suppliedTag);
    }

    public static void main(String[] args) throws Exception {
        byte[] original = utf8("message=v1");
        byte[] modified = utf8("message=v2");

        byte[] digestA = sha256(original);
        byte[] digestBCopy = sha256(original);
        byte[] digestDifferent = sha256(modified);

        System.out.println("SHA-256 length: " + digestA.length);
        System.out.println("Same message: " +
                MessageDigest.isEqual(digestA, digestBCopy));
        System.out.println("Different message: " +
                MessageDigest.isEqual(digestA, digestDifferent));
        System.out.println("Short digest: " +
                MessageDigest.isEqual(digestA,
                        Arrays.copyOf(digestBCopy, digestBCopy.length - 1)));

        KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256");
        generator.init(256);
        SecretKey key = generator.generateKey();
        byte[] tag = hmacSha256(original, key);
        byte[] tamperedTag = tag.clone();
        tamperedTag[0] ^= 1;

        System.out.println("HMAC valid: " + verifyHmac(original, tag, key));
        System.out.println("HMAC changed message: " +
                verifyHmac(modified, tag, key));
        System.out.println("HMAC changed tag: " +
                verifyHmac(original, tamperedTag, key));
        System.out.println("HMAC missing tag: " +
                verifyHmac(original, null, key));
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac DigestCompareDemo.java
java DigestCompareDemo
```

Stdout chính xác (dù key và tag được tạo ngẫu nhiên mỗi lần):

```text
SHA-256 length: 32
Same message: true
Different message: false
Short digest: false
HMAC valid: true
HMAC changed message: false
HMAC changed tag: false
HMAC missing tag: false
```

**Giải thích theo các bước:**

1. Hai lần băm `message=v1` tạo ra hai mảng riêng nhưng cùng byte. `MessageDigest.isEqual(digestA, digestBCopy)` trả `true`; phép so sánh reference `digestA == digestBCopy` sẽ trả `false`.
2. `message=v2` khác thông điệp ban đầu nên trong ví dụ tạo ra digest khác. Cắt bớt một byte của digest khiến độ dài chỉ còn 31 byte, kết quả cũng `false`.
3. `KeyGenerator.getInstance("HmacSHA256")` tạo **khóa bí mật dùng để minh họa**, không hardcode key trong source. Hệ thống thật phải quản lý key bằng keystore/KMS hoặc cơ chế tương đương.
4. `Mac.getInstance("HmacSHA256")` và `mac.init(key)` tạo tag từ khóa và byte thông điệp. Bên nhận tính lại `expectedTag` từ *cùng khóa* và *cùng byte thông điệp*.
5. `verifyHmac` từ chối tag thiếu/sai độ dài theo hợp đồng **đủ 32 byte**, sau đó gọi `MessageDigest.isEqual(expectedTag, suppliedTag)`. Expected tag đặt bên trái và được tính độc lập.
6. Đổi byte thông điệp hoặc tag làm xác minh thất bại; null cũng bị từ chối. Đây là hành vi của hệ thống mẫu, không phải chứng minh không tồn tại mọi kiểu tấn công.

**Phạm vi demo:** chương trình sinh key mới ở mỗi lần chạy và tạo tag/verify trong cùng process. Muốn xác minh tag do ứng dụng khác tạo, hai bên phải sử dụng cùng khóa được phân phối an toàn và thống nhất định dạng byte. Không được khởi tạo một khóa ngẫu nhiên mới mỗi lần xác minh tag đã nhận.

## 6. Digest thường và MAC: cùng dùng isEqual, khác nhau về bảo đảm

### 6.1. SHA-256 chỉ kiểm tra digest

Nếu ta có SHA-256 của một tệp từ **nguồn tin cậy**, tính lại digest rồi so sánh sẽ giúp phát hiện sai khác dữ liệu. Đây là chức năng kiểm tra tính toàn vẹn trong điều kiện **giá trị kỳ vọng được bảo vệ**.

Nếu kẻ tấn công có thể sửa cả tệp **và** digest đi kèm, họ chỉ cần tính lại SHA-256. Phép so sánh trả `true` dù dữ liệu không còn đáng tin. `isEqual` không tạo ra bí mật hay quyền hạn.

### 6.2. HMAC dùng để xác thực tính toàn vẹn và nguồn biết khóa

HMAC lấy khóa bí mật và thông điệp làm đầu vào. Bên xác minh giữ khóa hợp lệ, tự tính tag kỳ vọng, rồi so sánh với tag nhận được. Theo giả định an toàn của HMAC và quản lý khóa đúng, kẻ không có khóa không thể tùy ý thay thông điệp và tạo tag hợp lệ.

```text
BÊN GỬI                              BÊN NHẬN
message bytes                        message bytes
     |                                    |
key + Mac(HmacSHA256)          key + Mac(HmacSHA256)
     |                                    |
     v                                    v
receivedTag  ---------------->      expectedTag
                                         |
                         MessageDigest.isEqual(
                             expectedTag, receivedTag)
                                         |
                                   true / false
```

Sơ đồ này không cho thấy việc **truyền key**: key phải được chia sẻ bảo mật từ trước hoặc truy xuất qua hệ thống quản lý khóa. HMAC không mã hóa nội dung, không chặn replay nếu giao thức thiếu nonce/timestamp/messageId, không tự kiểm tra quyền truy cập. Cần đưa metadata bảo mật quan trọng vào thông điệp đã xác thực và áp dụng chính sách server phù hợp.

### 6.3. Không áp dụng trực tiếp cho mật khẩu hoặc chữ ký số

- **Mật khẩu:** Lưu `SHA-256(password)` rồi dùng `isEqual` không chống được dò mật khẩu offline khi database rò rỉ. Dùng password-hashing/KDF chuyên dụng có salt và chi phí phù hợp (Argon2id, scrypt, bcrypt hoặc PBKDF2 theo yêu cầu hệ thống), và hàm verify của thư viện được chọn.
- **Chữ ký số:** Muốn kiểm tra chữ ký của bên giữ private key thì dùng `java.security.Signature.verify` với khóa public, thuật toán và policy thích hợp. So sánh hai digest bằng `isEqual` không thay thế chữ ký số.
- **Bảo mật nội dung:** `isEqual` không mã hóa dữ liệu. Muốn bảo mật tính riêng tư và toàn vẹn, dùng cơ chế mã hóa được xác thực như AES-GCM theo thiết kế đúng.

## 7. Cách tổ chức verification trong sản phẩm

1. **Xác định chuẩn byte:** tên thuật toán, UTF-8, thứ tự trường, canonicalization, số phiên bản, định dạng Base64/hex và điều gì chính xác được đưa vào HMAC. JSON có thể có cùng ý nghĩa nhưng khác whitespace/thứ tự trường; nếu byte không giống, MAC sẽ khác.
2. **Giới hạn dữ liệu đầu vào:** kiểm tra kích thước request, phiên bản, cú pháp và độ dài tag **trước** khi xử lý dữ liệu lớn. Nếu yêu cầu full HmacSHA256, không chấp nhận tag 31 byte hay giá trị rỗng. Đừng tự thêm zero byte hoặc cắt tag để cho khớp.
3. **Lấy khóa từ nguồn tin cậy:** dùng keyId đã xác minh, phân quyền theo tenant, rotation và tách key khỏi ciphertext/tag. Không log hoặc hardcode khóa production.
4. **Tính expected tag ở server:** dùng `Mac` và cùng thông điệp được canonical hóa. Instance `Mac` có trạng thái, vì vậy tránh chia sẻ cùng instance đang update giữa nhiều thread không có cơ chế kiểm soát.
5. **So sánh bytes, không so sánh hex string:** decode tag đúng định dạng, xác nhận độ dài, rồi `MessageDigest.isEqual(expectedTag, receivedTag)`. Khi cần che tín hiệu thời gian nhạy cảm hơn, đánh giá cả đường request, including parse và error response; không coi phép so sánh đơn lẻ là tất cả.
6. **Kiểm tra nghiệp vụ và replay:** MAC hợp lệ vẫn có thể là một thông điệp cũ được gửi lại. Nếu yêu cầu, đưa timestamp/nonce/messageId được xác thực vào protocol, lưu trạng thái dùng một lần và kiểm tra hết hạn.

Về chuỗi hex/Base64: `Base64` chỉ mã hóa cách trình bày byte, không mã hóa bí mật. Với digest nhị phân không dùng `new String(digestBytes)` hoặc `digestBytes.toString()` để kiểm tra. Decoder phải rõ ràng khi gặp ký tự lạ, chuỗi sai padding hay byte vượt giới hạn; không làm ngầm nhiều kiểu decode khác nhau.

### Về truncated MAC

Một số giao thức chuẩn có thể quy định tag HMAC được cắt ngắn với chính sách độ dài cố định. Việc cắt ngắn làm thay đổi độ khó giả mạo; không cho client tự quyết định giữ bao nhiêu byte và không chấp nhận “prefix khớp” nếu protocol yêu cầu tag nguyên vẹn. Ví dụ trong chương trình dùng **full tag HmacSHA256 32 byte**, vì vậy kiểm tra `suppliedTag.length != expectedTag.length` là chủ đích.

## 8. Bảng kiểm tra phạm vi bảo đảm

| Nhận định | Đúng hay sai? |
| --- | --- |
| `MessageDigest.isEqual` tính SHA-256 cho ta | **Sai**: nó chỉ so sánh hai `byte[]`. |
| Hai mảng có cùng độ dài và từng byte giống nhau sẽ được coi bằng nhau | **Đúng** với đầu vào digest hợp lệ. |
| API có đặc tính thời gian nhằm tránh lộ vị trí byte sai khác | **Đúng**, với phạm vi và các ngoại lệ nêu trong Implementation Note. |
| Thời gian **mọi** lời gọi luôn bằng nhau, kể cả `null`/rỗng/cùng reference | **Sai**; có đường xử lý biên và đặc tính thời gian chỉ cho phép suy luận có điều kiện. |
| Toàn bộ HTTP endpoint trở thành constant-time nhờ dùng `isEqual` | **Sai**: decode, I/O, hash, authentication, JIT, network ngoài phạm vi đó. |
| So sánh SHA-256 thông thường tạo ra xác thực nguồn gửi | **Sai** nếu không bảo vệ digest kỳ vọng; kẻ tấn công có thể tính lại. |
| So sánh HMAC đúng bằng API giúp hạn chế timing leak tại bước đối chiếu tag | **Đúng**, nhưng cần HMAC với khóa bí mật an toàn và giao thức đúng. |
| Digest bằng nhau tuyệt đối chứng minh hai thông điệp gốc giống nhau | **Sai** về mặt toán học vì có thể xảy ra collision. |
| `isEqual` tự chặn replay, tăng độ khó dò mật khẩu, mã hóa nội dung hoặc xác minh chữ ký số | **Sai**; từng việc cần cơ chế riêng. |

## 9. Các lỗi thường gặp

| Lỗi | Hậu quả | Sửa |
| --- | --- | --- |
| Dùng `==` hay `byte[].equals` cho digest | Hai mảng cùng nội dung nhưng khác object bị xem là không bằng | Dùng `MessageDigest.isEqual`; `Arrays.equals` nếu không cần thuộc tính timing. |
| Dùng `Arrays.equals`/vòng lặp có `return` sớm cho HMAC | Không có bảo đảm tránh phụ thuộc vào vị trí byte sai | `MessageDigest.isEqual` với expected tag cố định. |
| Dùng `String.equals` trên hex/Base64 | So sánh chuỗi, nguy cơ xử lý encoding không nhất quán và không có hợp đồng timing | Decode nghiêm ngặt thành `byte[]`. |
| Coi “constant-time” là phản hồi HTTP luôn cùng thời gian | Bỏ qua parse, I/O, lỗi và thay đổi độ dài | Xác định threat model và đánh giá pipeline đầy đủ. |
| Tin tag `null`, rỗng hoặc sai độ dài | Mở thêm đường xử lý ngoài hợp đồng | Từ chối theo định dạng giao thức. |
| Client gửi cả message lẫn plain SHA digest, server coi là authenticated | Kẻ tấn công có thể gửi message giả cùng digest tự tính | HMAC với khóa server hoặc chữ ký số. |
| Hai phía hash JSON/Unicode khác byte | MAC hợp lệ từ phía gửi vẫn không khớp phía nhận | Thống nhất canonical byte representation. |
| Lưu SHA-256(password) thay vì password KDF | Dễ bị dò offline với mật khẩu yếu | Password hash/KDF có salt và cấu hình chi phí an toàn. |
| So sánh kết quả nhưng không kiểm tra quyền/replay | MAC có thể hợp lệ cho hành động sai ngữ cảnh hoặc request cũ | Bao phủ metadata trong MAC và kiểm soát nghiệp vụ, replay, keyId. |

## 10. Bài tập tự kiểm tra

1. Hai `byte[]` khác reference nhưng chứa cùng 32 byte: `a == b`, `Arrays.equals(a,b)` và `MessageDigest.isEqual(a,b)` trả những gì?
2. Vì sao phép so sánh `SHA-256(file)` với checksum do client gửi không tự chứng minh file đáng tin?
3. Khi so sánh HMAC, vì sao nên đặt tag kỳ vọng **được tính lại** ở đối số thứ nhất?
4. Điều gì không được bảo đảm khi tag bên nhận là `null`, rỗng hoặc có độ dài sai?
5. Tại sao thời gian xử lý trong HTTP endpoint có thể khác nhau dù `MessageDigest.isEqual` đã dùng đúng?
6. Giữa plain SHA-256, HmacSHA256, password hashing và chữ ký số, trường hợp nào cần khóa bí mật chung, trường hợp nào dùng public key?
7. Nếu tag HMAC hợp lệ nhưng request được gửi lại lần thứ hai, `isEqual` có tự phát hiện replay không?

**Thực hành:** thay byte đầu hoặc byte cuối của một digest/tag trong ví dụ và quan sát Boolean; so sánh với `Arrays.equals` về **ngữ nghĩa kết quả**, không dùng vài lần đo thời gian để tuyên bố an ninh timing. Thêm kiểm tra thay payload, tag ngắn 31 byte và request trùng messageId theo chính sách của ứng dụng.

## 11. Tài liệu chính thức

- [Java SE 17: MessageDigest](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html) — `isEqual`, `digest` và Implementation Note.
- [Java SE 24: MessageDigest](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/security/MessageDigest.html) — Implementation Note làm rõ trường hợp đối số thứ hai `null`/rỗng.
- [Java SE 17: Mac](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Mac.html) — tạo và xác minh MAC/HMAC.
- [OpenJDK: MessageDigest.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/security/MessageDigest.java) — tham khảo mã nguồn; chi tiết implementation có thể thay đổi theo phiên bản.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hash-functions-007">4.7 Giới hạn của digest công khai trước dữ liệu bị thay thế và mật khẩu yếu</a>

<details>
<summary>Xem chi tiết</summary>

# 4.7 Giới hạn của digest công khai trước dữ liệu bị thay thế và mật khẩu yếu

Một **digest công khai** như SHA-256 rất hữu ích để phát hiện dữ liệu bị thay đổi **khi người kiểm tra đã có một giá trị digest đáng tin cậy**. Nhưng hàm băm không chứa bí mật: bất kỳ ai có dữ liệu cũng có thể tính lại digest. Vì thế, nếu kẻ tấn công có thể thay **cả nội dung lẫn digest đi kèm**, phép so khớp vẫn thành công. Tương tự, băm trực tiếp một mật khẩu dễ đoán bằng SHA-256 không làm mật khẩu đó trở nên khó đoán: khi digest bị lộ, đối thủ có thể thử hàng loạt ứng viên **offline**, không cần gửi yêu cầu đăng nhập.

Hai tình huống này cùng liên quan tới hàm băm nhưng có **mô hình đe dọa khác nhau**:

- **Tính toàn vẹn/xác thực nguồn dữ liệu:** người kiểm tra cần biết digest tham chiếu đến từ nguồn nào và đã được bảo vệ như thế nào. Nếu cần xác thực nguồn gốc, dùng **HMAC** với khóa bí mật chung hoặc **chữ ký số** với khóa công khai đáng tin cậy, tùy kiến trúc.
- **Lưu trữ và xác minh mật khẩu:** máy chủ cần kiểm chứng mật khẩu mà không lưu plaintext, đồng thời khiến mỗi lần đoán mật khẩu bị lộ tốn tài nguyên. Dùng **password hashing/KDF có salt riêng và work factor**, ưu tiên Argon2id khi có thư viện thích hợp; PBKDF2-HMAC-SHA256 là ví dụ thực hiện được qua API JCA/JCE trong Java.

Bài này chỉ ra chính xác giới hạn của digest, minh họa hai cuộc tấn công ở mức kiến thức, rồi xây dựng cách kiểm tra phù hợp bằng Java 17+.

## 1. Digest công khai thực sự bảo đảm điều gì?

Với `SHA-256`:

```text
Dữ liệu gốc (bytes)  ── SHA-256 ──>  digest 256 bit (32 byte)

Người kiểm tra:
  digestTinhLai = SHA-256(duLieuNhanDuoc)
  digestTinhLai == digestThamChieu ? "khớp" : "không khớp"
```

SHA-256 là một hàm băm mật mã được thiết kế có các tính chất bảo mật như kháng tìm tiền ảnh, kháng tìm tiền ảnh thứ hai và kháng va chạm. Đầu ra có độ dài cố định; thay đổi một byte của dữ liệu thường làm digest khác đi. Tuy nhiên, **đó không phải cơ chế xác thực người phát hành**. Hàm băm là thuật toán công khai, không cần khóa, nên một bên đối địch cũng có thể tính `SHA-256(noiDungGia)`.

Phân biệt các thuật ngữ thường gặp:

| Khái niệm | Câu hỏi được trả lời | Giới hạn cần nhớ |
| --- | --- | --- |
| **Digest khớp** | Dữ liệu đang có khớp với **giá trị tham chiếu được dùng để so sánh** không? | Chưa chứng minh giá trị tham chiếu đó đáng tin. |
| **Kháng va chạm** | Có khó tìm hai thông điệp khác nhau cùng digest không? | Kẻ tấn công thay cả nội dung lẫn digest **không cần tìm va chạm**. |
| **Kháng tiền ảnh** | Có khó tìm dữ liệu từ digest cho một đầu vào đủ khó đoán không? | Mật khẩu ngắn/phổ biến có không gian thử nhỏ; có thể thử từ điển. |
| **Tính toàn vẹn có nguồn tin cậy** | Dữ liệu hiện tại có trùng nội dung mà nguồn đã xác thực công bố không? | Phụ thuộc cách bảo vệ/bắt nguồn của digest tham chiếu. |
| **Xác thực nguồn gốc** | Dữ liệu có thực sự do chủ thể nắm khóa hợp lệ tạo ra không? | Cần MAC/chữ ký số và quản lý khóa; digest thuần không đủ. |

Một checksum SHA-256 được đăng trên trang chính thức có thể hữu ích nếu chính trang/kênh lấy checksum được xác thực và đối thủ không sửa được. Nếu tải file và checksum từ **cùng một kho HTTP không đáng tin** mà đối thủ kiểm soát, việc khớp checksum gần như không mang bằng chứng chống giả mạo.

### 1.1 Tấn công thay cả dữ liệu và digest

```text
BÊN PHÁT HÀNH               KÊNH CÓ THỂ BỊ THAY                BÊN NHẬN
file.bin  ----------------> [thay bằng fake.bin] ------------> fake.bin
SHA256(file.bin) ----------> [đổi thành SHA256(fake.bin)] ----> SHA256(fake.bin)

Bên nhận: SHA256(fake.bin) == SHA256(fake.bin)  => true
Bên nhận: không hề biết file.bin ban đầu là gì!
```

Kẻ tấn công không phá SHA-256, không giải thuật toán, cũng không cần tìm hai file trùng digest. Họ chỉ thay **một cặp dữ liệu + digest tương ứng**. Nếu người nhận có digest gốc được xác minh từ một kênh độc lập, phép đối chiếu với cặp giả sẽ **không khớp**.

Do đó cần đặt đúng câu hỏi: *“Ai có quyền thay nội dung? Ai có quyền thay digest tham chiếu? Dựa vào đâu ta tin digest tham chiếu?”* Trả lời ba câu này trước khi nói một checksum “bảo vệ tính toàn vẹn”.

### 1.2 Khi nào chỉ SHA-256 là phù hợp?

- **Phát hiện lỗi truyền/lưu trữ ngoài ý muốn:** ví dụ checksum của bản sao lưu hoặc file build, khi điểm đối chiếu ban đầu đáng tin.
- **Nhận diện nội dung (content addressing/deduplication):** dùng digest để phân loại nội dung; cần bổ sung biện pháp kiểm soát nguồn/giá trị mong đợi khi có đối thủ chủ động.
- **Kiểm tra tải xuống:** file có digest trùng với digest chuẩn đã nhận qua kênh tin cậy. Digest lấy từ cùng nguồn không bảo vệ thì không chứng minh nhà phát hành.
- **Không thích hợp một mình:** xác thực webhook, bảo đảm manifest cập nhật không bị tráo, ký gói phần mềm, bảo vệ dữ liệu khỏi người có thể sửa đồng thời cả dữ liệu và checksum, hoặc bảo vệ cơ sở dữ liệu mật khẩu yếu.

## 2. Ba cách bảo vệ dữ liệu khỏi bị tráo

Khi cần chống đối thủ có thể thay đổi dữ liệu, lựa chọn cơ chế theo bên nào cần kiểm chứng và bên nào nắm khóa.

| Cơ chế | Bí mật/điểm tin cậy cần giữ | Ai có thể kiểm chứng? | Dùng cho |
| --- | --- | --- | --- |
| `SHA-256(data)` và **expected digest được xác thực** | Kênh/manifest chứa digest tham chiếu phải đáng tin | Bất cứ ai có expected digest đáng tin | Kiểm tra file download, checksum bản phát hành. |
| `HMAC-SHA256(key, data)` | **Khóa đối xứng bí mật** chỉ chia sẻ giữa các bên hợp lệ | Bên giữ cùng khóa | Xác thực webhook nội bộ hoặc thông điệp giữa hai dịch vụ chia sẻ khóa. |
| **Chữ ký số** `Sign(privateKey, data)` | Khóa riêng của bên ký; danh tính và khóa công khai của họ phải được xác thực | Bất cứ ai có **khóa công khai tin cậy** | Phân phối phần mềm, manifest phát hành, nhiều bên xác minh độc lập. |
| **AEAD** (ví dụ AES-GCM) | Khóa mã hóa hợp lệ, nonce/IV đúng quy tắc và tag | Bên giữ khóa giải mã | Khi cần **bí mật nội dung và xác thực bản mã**, không phải chỉ checksum. |

**HMAC** dùng hàm băm bên trong nhưng có **khóa bí mật**, nên một bên chỉ sửa dữ liệu mà không biết khóa sẽ không tự tạo được MAC hợp lệ. Không dùng `SHA256(key || message)` tự chế thay cho HMAC; hãy dùng `javax.crypto.Mac` và `HmacSHA256`. Khóa HMAC phải đủ ngẫu nhiên, được cấp và quản lý an toàn, không hardcode. Bên nào biết cùng khóa cũng có khả năng tạo MAC, vì vậy HMAC không đem lại tính chứng minh công khai ai đã ký như chữ ký số.

Với chữ ký số, API Java là `java.security.Signature`. Nhà phát hành ký bằng `PrivateKey`, bên nhận kiểm tra bằng `PublicKey` **được gắn với danh tính đáng tin**. Tải khóa công khai từ cùng kho đang bị tấn công mà không kiểm tra nguồn cũng có thể dẫn tới xác minh một chữ ký giả được tạo bằng khóa của đối thủ. Nếu dùng certificate, phải kiểm tra chuỗi tin cậy, mục đích sử dụng và trạng thái phù hợp chính sách.

HMAC/chữ ký số cũng **không tự ngăn phát lại** một thông điệp cũ vốn hợp lệ. Nếu hệ thống cần chống replay/rollback, đưa version, timestamp, sequence/nonce hoặc định danh sự kiện vào dữ liệu được xác thực và kiểm soát trạng thái ở phía nhận.

## 3. Ví dụ Java 17+: thay dữ liệu, thay digest, đối chiếu HMAC và đoán mật khẩu yếu

Lưu thành **`PublicDigestDemo.java`**. Ví dụ dùng `MessageDigest` tính SHA-256; `Mac` tính HMAC-SHA256. Đoạn đoán mật khẩu chỉ chạy trên mẫu `123456` được tạo ngay trong chương trình để giải thích cơ chế tấn công offline.

```java
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class PublicDigestDemo {
    static byte[] sha256(String text) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] hmac(String text, byte[] key) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws GeneralSecurityException {
        String original = "name=invoice;amount=100";
        String changed = "name=invoice;amount=900";

        byte[] trustedDigest = sha256(original);
        byte[] replacedDigest = sha256(changed);

        // Attacker chỉ thay file; expected digest gốc được bảo vệ.
        System.out.println("1. Digest cu khop noi dung bi doi: "
                + MessageDigest.isEqual(trustedDigest, sha256(changed)));

        // Attacker thay được cả file lẫn expected digest đi kèm.
        System.out.println("2. Digest gia khop noi dung bi doi: "
                + MessageDigest.isEqual(replacedDigest, sha256(changed)));

        // Khóa ví dụ được tạo ngẫu nhiên, hai đầu hợp lệ phải chia sẻ an toàn.
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        try {
            byte[] trustedMac = hmac(original, key);
            // Bên nhận tự tính lại MAC bằng khóa bí mật của mình.
            System.out.println("3. HMAC cu khop noi dung bi doi: "
                    + MessageDigest.isEqual(trustedMac, hmac(changed, key)));
        } finally {
            Arrays.fill(key, (byte) 0);
        }

        // Chỉ minh họa một mật khẩu yếu tự tạo; không dùng làm xử lý thật.
        byte[] leakedDigest = sha256("123456");
        boolean guessed = false;
        for (String candidate : new String[]{"admin", "123456", "welcome"}) {
            if (MessageDigest.isEqual(leakedDigest, sha256(candidate))) {
                guessed = true;
                break;
            }
        }
        System.out.println("4. Doan duoc mat khau yeu offline: " + guessed);
    }
}
```

Chạy bằng Java 17+ với provider JDK thông dụng hỗ trợ SHA-256 và HmacSHA256:

```bash
javac -encoding UTF-8 PublicDigestDemo.java
java PublicDigestDemo
```

Stdout (không phụ thuộc giá trị khóa ngẫu nhiên):

```text
1. Digest cu khop noi dung bi doi: false
2. Digest gia khop noi dung bi doi: true
3. HMAC cu khop noi dung bi doi: false
4. Doan duoc mat khau yeu offline: true
```

Đọc kỹ bốn kết quả:

1. `false`: digest của dữ liệu giả **khác** digest gốc đáng tin. Đây là trường hợp đối chiếu checksum hữu ích.
2. `true`: digest giả được tính trên dữ liệu giả nên khớp. Đây là **thất bại của nguồn tham chiếu không được bảo vệ**, không phải lỗi của SHA-256.
3. `false`: MAC hợp lệ của thông điệp gốc không còn khớp với thông điệp đã thay. Trong thực tế, đối thủ **không biết khóa** nên không thể đơn giản tự tính một MAC mới hợp lệ; bên nhận phải lấy MAC gốc từ nguồn chính sách cho phép và kiểm tra nó.
4. `true`: chỉ từ một digest SHA-256 của mật khẩu yếu, chương trình đoán trúng bằng danh sách ứng viên nhỏ. Không có thao tác “giải mã hash”: nó chỉ **băm từng ứng viên rồi so sánh**.

`MessageDigest.isEqual(byte[], byte[])` phù hợp để so sánh các digest/MAC ở mức byte; tránh phép so chuỗi tự chế dừng tại ký tự sai đầu tiên. Không được nhầm “so sánh cẩn thận” với “ngăn brute force”: nó không làm phép băm trở nên chậm và không tạo bí mật cho digest công khai. Ví dụ dùng `String` cố định để dễ đọc; dữ liệu thực phải được xác thực trên **chuỗi byte chính xác**, với định dạng/canonicalization nhất quán.

## 4. Vì sao SHA-256 thuần không bảo vệ mật khẩu yếu?

Giả sử cơ sở dữ liệu lưu:

```text
username = alice
password_hash = SHA256("123456")
```

Khi bản ghi bị lộ, đối thủ biết thuật toán là SHA-256 và biết digest được lưu. Họ có thể đoán hàng triệu ứng viên từ từ điển/bộ quy tắc ở máy riêng, tính `SHA256(candidate)` rồi so sánh. Không bị giới hạn bởi tốc độ endpoint đăng nhập, CAPTCHA hoặc khóa tài khoản, vì đây là **tấn công offline trên dữ liệu đã lộ**. Năng lực thực tế tùy phần cứng và thuật toán, nhưng SHA-256 được thiết kế chạy nhanh; điều đó rất tốt cho checksum, lại bất lợi khi mật khẩu có độ ngẫu nhiên thấp.

Các hiểu lầm cần sửa:

- **“SHA-256 không đảo ngược nên không ai tìm được mật khẩu.”** Không cần đảo ngược: chỉ cần thử các mật khẩu có xác suất cao. Một giá trị như `123456` bị đoán rất nhanh.
- **“Đổi SHA-256 sang SHA-512 là đủ.”** SHA-512 cũng là digest tổng quát nhanh; chỉ tăng độ dài digest không bổ sung work factor đủ lớn.
- **“Băm hai/lần vài lần sẽ an toàn.”** Lặp một vài vòng SHA-256 tự thiết kế không tương đương password hashing đã được phân tích và điều chỉnh chi phí.
- **“Thêm salt sẽ khiến mật khẩu khó đoán.”** Salt chủ yếu ngăn dùng bảng băm tính trước và giảm hiệu quả tái sử dụng kết quả trên nhiều tài khoản. Salt **không tăng entropy thực** của mật khẩu.
- **“Giữ kín tên thuật toán là đủ.”** Không nên dựa vào bí mật của thuật toán: thiết kế phải chống chịu ngay cả khi lộ thuật toán, tham số và salt.
- **“Có rate limit là đã an toàn.”** Rate limit bảo vệ truy cập **online**, không chặn thử offline trên dữ liệu hash bị lộ.

Nếu mật khẩu chỉ thuộc một tập nhỏ, đối thủ có thể thử toàn bộ tập đó với bất kỳ hash công khai nào. Mật khẩu phải đủ khó đoán, hệ thống nên sàng lọc mật khẩu phổ biến/đã lộ theo chính sách và dùng password hashing có chi phí đáng kể.

### 4.1 Salt, work factor và pepper khác nhau ra sao?

```text
password (bí mật người dùng) + salt riêng (công khai)
                |
                v
 Password KDF (ví dụ PBKDF2-HMAC-SHA256)
   có cost / iterations lớn và được hiệu chuẩn
                |
                v
    derived hash / verifier
                |
                v
Lưu: algorithm + parameters + salt + derived hash
```

| Thành phần | Vai trò | Có thể lưu cùng hash? |
| --- | --- | --- |
| **Salt** | Sinh riêng, ngẫu nhiên cho mỗi bản ghi; làm hai mật khẩu giống nhau thường có kết quả khác nhau; chống bảng tra cứu tính trước dùng chung. | **Có.** Salt không phải mật khẩu và không cần giữ bí mật. |
| **Work factor** | Làm mỗi lần đoán đắt hơn về CPU và/hoặc RAM; cập nhật theo thời gian khi phần cứng thay đổi. | **Có.** Phải lưu để xác minh bản ghi và hỗ trợ nâng cấp. |
| **Pepper** (tùy chọn) | Một bí mật bổ sung dùng trong thiết kế phù hợp, giúp chống lại tình huống chỉ lộ database. | **Không.** Lưu tách DB, ví dụ hệ quản lý secret hoặc HSM; không nhúng trong mã nguồn/cùng bảng hash. |
| **Password** | Bí mật mà người dùng cung cấp để xác minh. | **Không lưu plaintext** và không ghi vào log. |

**Salt không phải khóa HMAC.** Một `SHA256(salt || password)` đơn lẻ có salt tốt vẫn là phép băm **nhanh**; không đủ chống đoán offline. Salt cần dùng **cùng** KDF chuyên cho mật khẩu. Hai người có chung password nên có salt riêng và hash riêng, nhưng một mật khẩu phổ biến vẫn dễ bị đoán hơn một passphrase ngẫu nhiên mạnh.

**Pepper không thay thế salt/KDF**. Nó là biện pháp bổ sung có chi phí vận hành (quản lý/luân chuyển khóa, phục hồi), chỉ hữu ích khi khóa pepper không bị lộ cùng dữ liệu. Nếu kẻ tấn công có quyền sửa bảng hash trong database, họ còn có thể thay bản ghi theo cách khác: việc làm chậm đoán mật khẩu **không tự cung cấp tính toàn vẹn** hay phân quyền ghi cơ sở dữ liệu.

## 5. Chọn cơ chế lưu mật khẩu đúng

Mục tiêu của password hashing là làm tốn tài nguyên **mỗi lần thử ứng viên**, kể cả khi kẻ tấn công có bản ghi hash/salt. Các lựa chọn được dùng phổ biến:

| Cơ chế | Đặc điểm | Khi nên dùng |
| --- | --- | --- |
| **Argon2id** | KDF chuyên cho mật khẩu, có tham số RAM, thời gian và song song; chống đoán bằng phần cứng song song tốt hơn digest nhanh. | **Ưu tiên cho thiết kế mới** nếu có thư viện tin cậy và vận hành phù hợp. |
| **scrypt** | KDF phụ thuộc bộ nhớ với tham số CPU/RAM. | Khi Argon2id không có nhưng thư viện scrypt tin cậy sẵn có. |
| **bcrypt** | Password hash có cost điều chỉnh; nhiều triển khai có giới hạn độ dài đầu vào 72 byte. | Hệ thống cũ đã dùng bcrypt; kiểm tra chính xác giới hạn và migration. |
| **PBKDF2-HMAC-SHA256** | KDF dựa trên số vòng lặp HMAC; phổ biến và có thể dùng qua `SecretKeyFactory` trên JDK thích hợp. Không phải memory-hard. | Khi có yêu cầu tương thích/tuân thủ hoặc muốn học API JCA/JCE; phải hiệu chuẩn số vòng lặp. |
| **SHA-256/SHA-512/MD5 thuần** | Digest tổng quát, chạy nhanh, không có cost/password salt tích hợp. | **Không** dùng trực tiếp làm password verifier. |

Theo **OWASP Password Storage Cheat Sheet** được tham khảo cho bài này, cấu hình tối thiểu điển hình với Argon2id là `m=19456 KiB (~19 MiB), t=2, p=1`; với PBKDF2-HMAC-SHA256 là **600.000 vòng** (đặc biệt khi cần giải pháp theo FIPS). Các giá trị là **mức tham chiếu tại thời điểm triển khai**, phải rà lại tài liệu mới và đo trên môi trường sản xuất. Tăng cost quá mức có thể làm máy chủ bị quá tải khi nhiều lần đăng nhập; cần hạn chế lưu lượng và đánh giá tài nguyên. Không dùng duy nhất một mốc benchmark trên laptop cho mọi môi trường.

Java 17 cung cấp API `javax.crypto.SecretKeyFactory`, `javax.crypto.spec.PBEKeySpec` cho PBKDF2 qua provider có hỗ trợ `PBKDF2WithHmacSHA256`. JDK chuẩn không đưa ra một API tích hợp chung bảo đảm có Argon2id trên mọi môi trường: nếu chọn Argon2id, sử dụng thư viện được duy trì, kiểm tra phiên bản/dependency và triển khai theo hướng dẫn của thư viện.

### 5.1 Quy tắc lưu bản ghi verifier

Một bản ghi nên chứa đủ thông tin **để xác minh và nâng cấp**, chẳng hạn:

```text
pbkdf2-sha256$600000$base64(salt)$base64(derivedHash)
```

Đây là **định dạng riêng của ví dụ**; các trường lần lượt là tên scheme, số vòng lặp, salt và hash đã dẫn xuất. Tất cả các trường trừ password có thể công khai. Trong production, cần định nghĩa encoding và phiên bản định dạng ổn định, giới hạn độ dài và cost được chấp nhận để dữ liệu bản ghi hỏng không gây DoS, áp dụng migration cho bản ghi cũ, và bảo vệ quyền ghi/tính toàn vẹn của kho lưu trữ. Không bao giờ lưu mật khẩu ở dạng Base64: Base64 chỉ là mã hóa biểu diễn.

## 6. Ví dụ Java 17+: lưu và xác minh mật khẩu bằng PBKDF2 có salt

Ví dụ này chọn **PBKDF2WithHmacSHA256**, salt 16 byte từ `SecureRandom`, **600.000** vòng lặp và output 256 bit. Tên thuật toán/salt/cost được lưu cùng verifier; khi xác minh, dùng salt/cost đã lưu và so sánh `MessageDigest.isEqual`. Mật khẩu trong `main` là giá trị minh họa công khai, **không** dùng làm mật khẩu thật.

Lưu thành **`PasswordStorageDemo.java`**:

```java
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordStorageDemo {
    private static final String SCHEME = "pbkdf2-sha256";
    private static final String JCA_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ROUNDS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] derive(char[] password, byte[] salt, int rounds)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(JCA_ALGORITHM)
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    static String store(char[] password) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] result = derive(password, salt, ROUNDS);
        try {
            return SCHEME + "$" + ROUNDS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(result);
        } finally {
            Arrays.fill(result, (byte) 0);
        }
    }

    static boolean verify(char[] input, String record)
            throws GeneralSecurityException {
        if (record == null) return false;
        String[] fields = record.split("\\$", -1);
        if (fields.length != 4 || !SCHEME.equals(fields[0])) return false;

        int rounds;
        try {
            rounds = Integer.parseInt(fields[1]);
        } catch (NumberFormatException ex) {
            return false;
        }
        // Chặn cost bất thường trong định dạng demo do chính ứng dụng tạo.
        if (rounds < 100_000 || rounds > 2_000_000) return false;

        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(fields[2]);
            expected = Base64.getDecoder().decode(fields[3]);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (salt.length != SALT_BYTES || expected.length != KEY_BITS / 8) {
            return false;
        }

        byte[] actual = derive(input, salt, rounds);
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    public static void main(String[] args) throws GeneralSecurityException {
        char[] good = "DemoOnly#NotARealPassword".toCharArray();
        char[] wrong = "wrong-guess".toCharArray();
        try {
            String record = store(good);
            System.out.println("4. Dung mat khau: " + verify(good, record));
            System.out.println("5. Sai mat khau: " + verify(wrong, record));
            String[] fields = record.split("\\$");
            System.out.println("6. Salt 16 byte duoc luu: "
                    + (Base64.getDecoder().decode(fields[2]).length == 16));
        } finally {
            Arrays.fill(good, '\0');
            Arrays.fill(wrong, '\0');
        }
    }
}
```

```bash
javac -encoding UTF-8 PasswordStorageDemo.java
java PasswordStorageDemo
```

Stdout (salt và hash mỗi lần chạy là ngẫu nhiên, nhưng các dòng kiểm tra dưới đây cố định):

```text
4. Dung mat khau: true
5. Sai mat khau: false
6. Salt 16 byte duoc luu: true
```

Giải thích các bước:

1. `store` tạo **salt mới** cho từng bản ghi bằng `SecureRandom`, không dùng chung một salt cố định.
2. `PBEKeySpec(password, salt, rounds, KEY_BITS)` mô tả các tham số của PBKDF2; `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` tạo kết quả dẫn xuất. Không thay bằng `MessageDigest.getInstance("SHA-256")` lặp vài lần tự chế.
3. Trường `SCHEME` và `rounds` được lưu, nên sau này có thể phân biệt bản ghi cũ/mới. Salt phải giống với lúc tạo bản ghi; **không sinh salt mới khi verify**.
4. `verify` kiểm tra định dạng, giới hạn rounds, độ dài salt và hash trước khi tính lại. Giới hạn rounds trong ví dụ ngăn tham số do bản ghi bất thường khiến CPU bị chiếm quá lâu; hệ thống thực phải định nghĩa chính sách migration cho các giá trị lịch sử.
5. `MessageDigest.isEqual` so sánh byte array sau dẫn xuất; không dùng `String.equals` trên plaintext hoặc so từng ký tự đến khi khác.
6. `PBEKeySpec.clearPassword()` xóa **bản sao password bên trong spec**, và `Arrays.fill` xóa một số buffer do chương trình sở hữu. Điều đó không bảo đảm xóa mọi bản sao trong JVM hay provider. Vì `main` chứa string literal công khai để demo, không dùng cách này nhận mật khẩu production; hãy nhận đầu vào qua quy trình bảo mật phù hợp và tránh lưu/log dưới dạng `String` lâu dài.

**Giới hạn của ví dụ:** đây là mô-đun Java console giúp học JCA/JCE, chưa có database, API đăng nhập, TLS, rate limit, pepper, kiểm tra mật khẩu đã lộ, migration hoặc giám sát tải. Các bản ghi bị kẻ tấn công **sửa trực tiếp** vẫn đòi hỏi kiểm soát quyền ghi/đảm bảo toàn vẹn database. `PBKDF2` làm chậm đoán offline nhưng không bù đắp một mật khẩu cực yếu và không thay thế quy trình quản lý tài khoản.

## 7. Vận hành password verifier thực tế

Một thiết kế thực tế cần xem password hashing trong toàn bộ vòng đời:

1. **Đăng ký/đổi mật khẩu:** kiểm tra theo chính sách chấp nhận độ dài, mật khẩu phổ biến/đã lộ và Unicode; không ép các quy tắc ghép ký tự tùy tiện. NIST SP 800-63B-4 nhấn mạnh độ dài, blocklist, mật khẩu đủ mạnh và khả năng dùng trình quản lý mật khẩu.
2. **Chuẩn hóa nhất quán:** cùng một password phải đi vào KDF dưới cùng encoding/quy tắc Unicode khi tạo và xác minh. Nếu áp dụng Unicode normalization như NFC, đưa vào contract và thực hiện nhất quán. Không âm thầm `trim`, lowercase hoặc cắt ngắn password khi xác minh.
3. **Tạo verifier:** dùng KDF, salt mới và work factor theo cấu hình đã đo. Không để người dùng điều khiển thuật toán/cost qua request; không tạo salt bằng `Random` hoặc thời gian hệ thống.
4. **Lưu có cấu trúc:** ghi algorithm/version, salt, cost và derived hash. Phân quyền DB, sao lưu và log phù hợp; không lưu plaintext/recovery hint.
5. **Xác minh:** nhận password qua TLS hoặc kênh bảo vệ tương đương; dùng tham số đã lưu, so sánh an toàn, trả thông báo thất bại không lộ account internals. Rate limit và bảo vệ abuse vẫn cần thiết dù có KDF.
6. **Nâng cấp:** khi cost hoặc scheme cũ, **sau một lần xác minh hợp lệ** có thể tạo verifier mới với salt/cost mới rồi cập nhật an toàn. Không thể chuyển trực tiếp SHA-256 hash cũ thành một password KDF mạnh tương đương mà không có password gốc; lập kế hoạch rehash hoặc reset.
7. **Giám sát và xử lý sự cố:** theo dõi thời gian/công suất xác minh, phát hiện credential stuffing và tấn công account, bảo vệ pepper riêng (nếu dùng), buộc đổi mật khẩu khi có bằng chứng lộ bí mật.

### 7.1 Đo chi phí và chống quá tải

Password hashing cố ý tốn tài nguyên. Nếu mỗi lần xác minh dùng nhiều CPU/RAM, nhiều request đồng thời có thể làm dịch vụ cạn tài nguyên. Kết hợp work factor được hiệu chuẩn, giới hạn số lần thử, kiểm soát đồng thời, timeout và theo dõi latency. Khi dùng Argon2id, đặc biệt phải tính tổng RAM ở số lượng request đồng thời; không chỉ tăng `m` đến mức tối đa trong tài liệu tham khảo.

**Không giảm work factor một cách im lặng** chỉ vì hệ thống quá tải. Hãy xác định tải thực, tối ưu luồng xác minh, thiết lập giới hạn, rồi chọn cấu hình đã được phê duyệt và tái đo khi đổi máy/JDK/provider.

## 8. Phân biệt lựa chọn qua các tình huống

| Tình huống | Chọn gì? | Vì sao? |
| --- | --- | --- |
| Muốn biết file tải xuống có đúng byte so với checksum **đã xác minh** hay không | SHA-256 + expected digest tin cậy | Kiểm tra tính khớp, không cần khóa mới khi nguồn expected đã được xác thực. |
| File và checksum cùng nằm trong kho mà đối thủ có thể thay | Chữ ký số của manifest/file hoặc digest từ nguồn bảo vệ độc lập | Digest thuần của file giả vẫn khớp digest giả. |
| Hai dịch vụ dùng bí mật chung để xác thực payload webhook | HMAC-SHA256 với quản lý key, định dạng xác thực rõ ràng | Bên không giữ key không tự tạo MAC mới hợp lệ. |
| Public download được nhiều bên kiểm tra độc lập | Chữ ký số và khóa công khai gắn danh tính đáng tin | Không phải chia sẻ khóa bí mật HMAC cho mọi người dùng. |
| Cần cả tính bí mật và xác thực bản mã | AEAD phù hợp, ví dụ AES-GCM | Digest/MAC không tự mã hóa để giấu plaintext. |
| Lưu verifier đăng nhập | Argon2id/scrypt hoặc PBKDF2 phù hợp + salt riêng + cost | Làm chậm tấn công đoán offline vào mật khẩu có thể bị lộ. |
| Người dùng chọn `123456` | Blocklist/chính sách mật khẩu, mật khẩu mạnh, KDF và cơ chế xác thực khác | KDF không biến một bí mật cực dễ đoán thành bí mật mạnh. |
| Đối thủ có quyền ghi bảng password verifier | Bảo vệ quyền ghi DB, kiểm tra toàn vẹn, giám sát thay đổi | KDF chống thử password trên bản ghi bị lộ; không tự ngăn thay bản ghi. |

### 8.1 Bốn câu hỏi để tự kiểm tra

**Câu 1.** “Tôi tải file từ máy chủ A, và máy chủ A trả kèm SHA-256. Hai giá trị khớp; tôi có chắc file không bị tráo?”  
**Trả lời:** Chỉ chắc rằng file khớp với giá trị checksum được nhận. Nếu đối thủ thay được cả hai trên đường truyền/kho lưu, không có bảo đảm về nguồn gốc. Cần xác thực kênh, checksum kỳ vọng đáng tin, hoặc chữ ký số.

**Câu 2.** “Nếu tấn công thay file và checksum mà vẫn khớp, có phải SHA-256 bị collision?”  
**Trả lời:** Không. Đối thủ tính digest mới của file mới, không cần hai file khác nhau có cùng digest. Va chạm không liên quan đến ví dụ này.

**Câu 3.** “Thêm 16 byte salt ngẫu nhiên vào `SHA256(password)` đã đủ lưu password chưa?”  
**Trả lời:** Chưa. Salt làm hash của các bản ghi khác nhau và chống bảng tính trước, nhưng SHA-256 một vòng vẫn nhanh. Dùng KDF chuyên dụng với work factor, salt riêng và mật khẩu khó đoán.

**Câu 4.** “Trong chương trình PBKDF2, vì sao lúc xác minh phải đọc salt và rounds từ record?”  
**Trả lời:** KDF phải nhận lại đúng tham số đã dùng khi tạo verifier; salt mới sẽ tạo kết quả khác. Lưu version/work factor cho phép xác minh bản ghi lịch sử và nâng cấp qua thời gian.

**Câu 5.** “Có nên che giấu tên `PBKDF2WithHmacSHA256` hoặc giá trị salt để chống lộ mật khẩu?”  
**Trả lời:** Không cần. Chúng được xem là thông tin công khai. An toàn phụ thuộc mật khẩu khó đoán, KDF được chọn/hiệu chuẩn và biện pháp bảo vệ dữ liệu/secret phụ trợ.

**Câu 6.** “Tôi dùng HMAC cho webhook. Payload cũ hợp lệ được gửi lại y hệt thì HMAC có chặn không?”  
**Trả lời:** Không. MAC vẫn hợp lệ trên thông điệp cũ. Nếu replay có hại, ký/xác thực cả timestamp, message ID hoặc sequence và kiểm tra giới hạn thời gian/dedupe ở bên nhận.

## 9. Những lỗi thường gặp

- Kiểm tra `SHA256(file)` bằng digest nằm trong cùng một phản hồi không đáng tin rồi kết luận file do đúng nhà phát hành tạo ra.
- Nghĩ tấn công thay `file + digest` đòi hỏi tìm va chạm SHA-256; thực ra chỉ cần tính digest của dữ liệu giả.
- Dùng `MessageDigest` trực tiếp làm password hash hoặc băm vài vòng tự chế rồi cho là password storage KDF.
- Dùng một salt cố định cho tất cả người dùng; lấy salt từ username, timestamp hoặc nguồn dễ đoán; hoặc coi salt là bí mật phải giấu.
- Cho rằng salt/pepper/MFA/rate limit có thể thay thế lẫn nhau. Chúng giải quyết những lớp đe dọa khác nhau.
- Dùng `SHA256(key || message)` tự chế thay HMAC; không bảo vệ key, không quản lý key rotation hoặc không kiểm soát replay.
- Dùng chữ ký số nhưng tải khóa công khai chưa xác thực từ cùng nguồn không đáng tin với file cần kiểm tra.
- Lưu plaintext password, log password, dùng Base64 như biện pháp bảo mật, giữ credential test hardcode trong production.
- Tạo record PBKDF2 nhưng không lưu salt/work factor/version; cố verify với salt mới; bỏ qua xử lý dữ liệu hỏng hoặc cost quá mức.
- Đưa toàn bộ mật khẩu yếu/compromised vào hệ thống và hy vọng KDF mạnh sẽ bù mọi rủi ro.

## 10. Checklist trước khi triển khai

**Đối với file/thông điệp:** xác định ai có thể sửa nội dung và digest; chọn expected digest đáng tin, HMAC hoặc chữ ký số; xác thực trust anchor; kiểm tra chính xác bytes/encoding và định dạng; bổ sung chống replay/rollback nếu cần.

**Đối với mật khẩu:** không lưu plaintext; sàng lọc mật khẩu yếu theo chính sách; dùng KDF đã được phân tích; sinh salt riêng; hiệu chuẩn cost; lưu schema/version/salt/cost/hash; so sánh an toàn; giới hạn thử online; kiểm soát quyền ghi DB; thiết kế nâng cấp/migration và bảo vệ pepper nếu có.

## Tài liệu đối chiếu

- [Java SE 21 — MessageDigest](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html): tính digest, `digest`, `isEqual`.
- [Java SE 21 — Mac](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html): HMAC, `init` và `doFinal`.
- [Java SE 21 — Signature](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Signature.html): ký và kiểm tra chữ ký số.
- [Java SE 21 — SecretKeyFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/SecretKeyFactory.html) và [PBEKeySpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html): tạo verifier PBKDF2 với salt và số vòng lặp.
- [OWASP — Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): Argon2id, scrypt, bcrypt, PBKDF2, salt, pepper, work factor, migration.
- [NIST SP 800-63B-4 — Password verifiers](https://pages.nist.gov/800-63-4/sp800-63b.html#verifierreq): bảo vệ password verifier khỏi tấn công offline, salt, cost và điều chỉnh theo thời gian.
- [OWASP — Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): chọn cơ chế mật mã dựa trên mô hình đe dọa.

**Điểm cần nhớ:** digest công khai có ích để **so sánh với giá trị kỳ vọng đáng tin**; nó không tự xác thực tác giả khi cả dữ liệu và digest đều bị thay. Để xác thực nguồn, cần HMAC/chữ ký số với quản lý khóa phù hợp. Để bảo vệ mật khẩu yếu khỏi đoán offline, cần password hashing có salt và cost — và vẫn phải khuyến khích mật khẩu đủ mạnh.

</details>

- [Quay lại đầu trang](#back-to-top)
