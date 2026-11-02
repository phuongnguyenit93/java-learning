<a id="back-to-top"></a>

# 23. Thực hành tổng hợp

## Menu
- [23.1 Xây công cụ tính digest file và xác minh giá trị mong đợi](#security-practice-001)
- [23.2 Xây bộ xác thực thông điệp HMAC có ngữ cảnh và cơ chế chống phát lại](#security-practice-002)
- [23.3 Xây định dạng mã hóa file AEAD có phiên bản và xử lý lỗi xác thực](#security-practice-003)
- [23.4 Xây bộ kiểm tra mật khẩu PBKDF2 hỗ trợ nâng cấp tham số](#security-practice-004)
- [23.5 Tạo và xác minh chữ ký tài liệu bằng khóa lấy từ PKCS12 KeyStore](#security-practice-005)
- [23.6 Thực nghiệm KeyAgreement hoặc DHKEM và phân tích phần xác thực còn thiếu](#security-practice-006)
- [23.7 Tạo chuỗi chứng thư thử nghiệm rồi xây và kiểm tra đường dẫn PKIX](#security-practice-007)
- [23.8 Thiết lập kết nối Java mTLS và kiểm thử các tình huống handshake thất bại](#security-practice-008)
- [23.9 Phân tích một cấu hình hoặc đoạn mã mật mã sai và nêu căn cứ sửa](#security-practice-009)
- [23.10 Tự đánh giá bằng lựa chọn cơ chế cho dữ liệu, danh tính và ranh giới tin cậy cụ thể](#security-practice-010)

## <a id="security-practice-001">23.1 Xây công cụ tính digest file và xác minh giá trị mong đợi</a>

<details>
<summary>Xem chi tiết</summary>

# 23.1. Xây công cụ tính digest file và xác minh giá trị mong đợi

## 1. Bài toán thực hành và kết quả cần đạt

Giả sử nhận một tệp `release.zip`, `library.jar` hoặc bản sao lưu từ nguồn khác. Người phát hành công bố một giá trị SHA-256 của tệp gốc. Ta cần một **công cụ dòng lệnh (CLI) bằng Java** để (1) tính digest của **đúng các byte trong tệp**, (2) hiển thị kết quả bằng hexadecimal và (3) so sánh với giá trị mong đợi rồi trả mã thoát rõ ràng cho người dùng hoặc script CI.

Trong bài này, **digest** là kết quả của hàm băm mật mã (cryptographic hash) áp dụng cho nội dung byte của tệp. SHA-256 cho kết quả 256 bit (32 byte = **64 ký tự hex**), SHA-512 cho 512 bit (64 byte = **128 ký tự hex**). Một thay đổi rất nhỏ trong dữ liệu thông thường sẽ làm digest khác đi. Ta sẽ dùng API chuẩn `java.security.MessageDigest`, đọc tệp dạng stream, tránh tải toàn bộ tệp vào heap.

Mục tiêu kiểm chứng sau bài:

- `digest`: tính SHA-256 hoặc SHA-512 của đường dẫn tệp; kết quả ở dạng hex chữ thường, có tên thuật toán.
- `verify`: nhận thuật toán, đường dẫn và **giá trị digest mong đợi**; trả `MATCH` nếu trùng hoặc `MISMATCH` nếu khác.
- Từ chối thuật toán không nằm trong allowlist, checksum sai độ dài hoặc chứa ký tự không phải hex.
- Xử lý rõ lỗi mở/đọc tệp; script phân biệt được thành công, khác digest, nhập sai và I/O lỗi qua **exit code**.
- Dùng bộ nhớ gần như cố định khi xử lý tệp lớn: chỉ dùng buffer 64 KiB và trạng thái digest của thuật toán.

**Giới hạn bảo mật cần nhớ ngay:** checksum chứng minh rằng byte của tệp trùng với checksum **được đưa vào**. Nếu kẻ tấn công thay được **cả tệp lẫn checksum mong đợi**, kết quả `MATCH` không chứng minh nguồn phát hành đáng tin. Muốn xác thực người phát hành, cần kiểm tra chữ ký số hoặc MAC với khóa tin cậy, hoặc nhận digest qua kênh độc lập đáng tin.

## 2. Digest được tính từ byte, không từ tên file hay văn bản

Một công thức ý niệm:

~~~text
File (chuỗi byte)
    -> đọc theo các block 64 KiB
    -> MessageDigest.update(block, offset, length) nhiều lần
    -> MessageDigest.digest()
    -> byte[32] với SHA-256 hoặc byte[64] với SHA-512
    -> HexFormat.of().formatHex(bytes)
    -> chuỗi hex để hiển thị hoặc đưa vào lệnh verify
~~~

Ví dụ chuẩn SHA-256 của **đúng ba byte ASCII** tương ứng `a`, `b`, `c` (không có newline, BOM hoặc khoảng trắng) là:

~~~text
ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
~~~

Một tệp chứa `abc\n`, `abc\r\n`, thêm BOM UTF-8 hoặc nội dung đã qua sửa đổi encoding sẽ có digest khác. Tên tệp, phần mở rộng, thời điểm sửa, owner và permission **không nằm trong digest nội dung**. Hai tệp khác tên nhưng có cùng byte sẽ cho cùng digest với cùng thuật toán. Muốn bảo vệ cả metadata hoặc nhiều tệp, cần một manifest được định nghĩa rõ cách serialize và bảo vệ riêng.

Đừng nhầm giữa:

| Cơ chế | Công dụng | Điều không tự bảo đảm |
| --- | --- | --- |
| `SHA-256(file)` | Fingerprint/toàn vẹn của byte nội dung so với một checksum tham chiếu | Danh tính người tạo tệp, tính hợp pháp của nội dung, nguồn gốc checksum |
| `HMAC-SHA-256(secret, data)` | Xác thực dữ liệu giữa các bên cùng giữ khóa bí mật | Xác thực công khai bên thứ ba, nếu không chia sẻ khóa |
| Chữ ký số trên digest/manifest | Có thể chứng minh bên nắm khóa ký tạo chữ ký và dữ liệu không bị đổi, khi khóa công khai được xác thực | Độ tin cậy nếu chấp nhận tùy ý khóa công khai do chính thông điệp đưa vào |

Với checksum tải về, SHA-256/SHA-512 là lựa chọn phù hợp. Không chọn MD5 hoặc SHA-1 cho cơ chế xác minh nội dung cần chống đối thủ cố tình tạo collision. Không nên tự tạo thuật toán băm bằng `String.hashCode()`, `CRC32` hoặc phép XOR: chúng không phải hàm băm mật mã phù hợp cho bài toán này.

## 3. Thiết kế CLI và hợp đồng đầu vào/đầu ra

Công cụ dùng hai lệnh, có tên tệp truyền dưới dạng tham số (đặt trong dấu nháy nếu chứa khoảng trắng):

~~~bash
java FileDigestTool digest SHA-256 "path/to/file.bin"
java FileDigestTool digest SHA-512 "path/to/file.bin"

java FileDigestTool verify SHA-256 "path/to/file.bin" "<64-hex-characters>"
java FileDigestTool verify SHA-512 "path/to/file.bin" "<128-hex-characters>"
~~~

Tên thuật toán trong ví dụ là `SHA-256` hoặc `SHA-512`; chương trình cho phép chữ thường trong tên thuật toán vì chuẩn hóa bằng `Locale.ROOT`. Lệnh `digest` cần đúng **3 đối số** (tính cả tên lệnh); `verify` cần đúng **4 đối số**. Chuỗi hex phải có đúng số ký tự và chỉ gồm `0-9`, `a-f`, `A-F`; nhập hex hoa hay thường đều được, và chương trình hiển thị chuẩn hex chữ thường. **Giá trị mong đợi được truyền vào hoàn chỉnh**, không tự download từ URL hay mở manifest lạ.

| Exit code | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `0` | Tính digest xong hoặc xác minh `MATCH` | File đọc được, digest giống expected |
| `1` | `MISMATCH`: đọc file thành công nhưng bytes không giống digest mong đợi | Tệp bị sửa hoặc expected thuộc bản khác |
| `2` | Lệnh/thuật toán/độ dài/chuỗi hex/đường dẫn không hợp lệ | `MD5` không được hỗ trợ; expected hex chỉ 3 ký tự |
| `3` | Không thể đọc tệp do I/O hoặc quyền truy cập | File không tồn tại, thiếu quyền, lỗi đọc |

Kết quả thường in `stdout`; thông báo usage, lỗi đầu vào, lỗi I/O in `stderr`. Với kiểm tra tự động, nên **kiểm tra exit code** thay vì tìm từ `MATCH` trong log. Đây là công cụ cấp phát quyền đọc theo tiến trình CLI của người dùng; không có chức năng sửa tệp, không kết nối mạng và không cố gắng tự xác minh nguồn gốc checksum.

## 4. Mã nguồn hoàn chỉnh — Java 17 trở lên

Tạo file **`FileDigestTool.java`** và lưu nguyên mã dưới đây. Chương trình chỉ dùng Java SE (không yêu cầu Maven/Gradle hay thư viện ngoài):

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class FileDigestTool {
    private static final int BUFFER_SIZE = 64 * 1024;

    private FileDigestTool() {}

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        if (args == null || args.length < 3 || args.length > 4) {
            return usage();
        }

        boolean verify;
        if ("digest".equals(args[0]) && args.length == 3) {
            verify = false;
        } else if ("verify".equals(args[0]) && args.length == 4) {
            verify = true;
        } else {
            return usage();
        }

        String algorithm;
        try {
            algorithm = selectAlgorithm(args[1]);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            return 2;
        }

        byte[] expected = null;
        if (verify) {
            int requiredHexLength = algorithm.equals("SHA-256") ? 64 : 128;
            if (args[3].length() != requiredHexLength) {
                System.err.println("Expected exactly " + requiredHexLength
                        + " hexadecimal characters for " + algorithm);
                return 2;
            }
            try {
                expected = HexFormat.of().parseHex(args[3]);
            } catch (IllegalArgumentException ex) {
                System.err.println("Expected digest is not valid hexadecimal");
                return 2;
            }
        }

        Path file;
        try {
            file = Path.of(args[2]);
        } catch (InvalidPathException ex) {
            System.err.println("Invalid file path");
            return 2;
        }

        try {
            byte[] actual = digestFile(file, algorithm);
            if (!verify) {
                System.out.println(algorithm + " "
                        + HexFormat.of().formatHex(actual) + " " + file);
                return 0;
            }

            if (MessageDigest.isEqual(actual, expected)) {
                System.out.println("MATCH: " + algorithm + " " + file);
                return 0;
            }
            System.out.println("MISMATCH: " + algorithm + " " + file);
            System.out.println("  expected: " +
                    HexFormat.of().formatHex(expected));
            System.out.println("  actual:   " +
                    HexFormat.of().formatHex(actual));
            return 1;
        } catch (IOException | SecurityException ex) {
            System.err.println("Cannot read file: " + ex.getMessage());
            return 3;
        }
    }

    private static byte[] digestFile(Path file, String algorithm)
            throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Required digest unavailable", ex);
        }

        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = input.read(buffer)) != -1) {
                if (n == 0) {
                    throw new IOException("Stream made no progress");
                }
                md.update(buffer, 0, n);
            }
        }
        return md.digest();
    }

    private static String selectAlgorithm(String text) {
        return switch (text.toUpperCase(Locale.ROOT)) {
            case "SHA-256" -> "SHA-256";
            case "SHA-512" -> "SHA-512";
            default -> throw new IllegalArgumentException(
                    "Only SHA-256 and SHA-512 are supported");
        };
    }

    private static int usage() {
        System.err.println("Usage:");
        System.err.println("  java FileDigestTool digest SHA-256 <file>");
        System.err.println("  java FileDigestTool digest SHA-512 <file>");
        System.err.println(
                "  java FileDigestTool verify SHA-256 <file> <expectedHex>");
        System.err.println(
                "  java FileDigestTool verify SHA-512 <file> <expectedHex>");
        return 2;
    }
}
~~~

### Giải thích các phần quan trọng

**1. Chọn thuật toán theo allowlist.** `selectAlgorithm()` chỉ cho `SHA-256` và `SHA-512`. Người dùng không được truyền tùy ý thuật toán mà provider tình cờ hỗ trợ. `NoSuchAlgorithmException` của hai thuật toán chuẩn là lỗi môi trường JDK nếu xảy ra, không phải trường hợp “hash không khớp”.

**2. Đọc stream thay vì `Files.readAllBytes()`.** `Files.newInputStream(file)` mở tệp, `read(buffer)` điền tối đa 64 KiB mỗi lần, `md.update(buffer, 0, n)` băm **chỉ n byte thực đọc**. `MessageDigest` duy trì trạng thái qua các lần `update()`; `digest()` chốt kết quả khi EOF. Nếu file rỗng, vòng lặp không chạy nhưng `digest()` vẫn cho digest hợp lệ của chuỗi byte rỗng. Thời gian xử lý tỷ lệ thuận với số byte; footprint buffer gần như cố định.

**3. Không băm buffer thừa.** Ở block cuối, `n` có thể nhỏ hơn `buffer.length`. Dùng `update(buffer)` thay cho `update(buffer,0,n)` trong vòng lặp sẽ vô tình đưa các byte cũ/không thuộc file vào digest. Đây là lỗi rất phổ biến khi viết công cụ băm theo block.

**4. Parse expected hex thành byte.** `HexFormat.of().parseHex(args[3])` giải mã chuỗi hex; trước đó kiểm tra chính xác 64 hoặc 128 ký tự để không chấp nhận expected sai kích thước. Cặp byte được so sánh bằng `MessageDigest.isEqual(actual, expected)` thay vì so sánh hai chuỗi biểu diễn khác kiểu hoa/thường. API này được thiết kế tránh so sánh nội dung kiểu dừng ở byte khác đầu tiên; tuy nhiên digest ở đây là giá trị công khai và phép so sánh không thể biến nguồn expected thiếu tin cậy thành an toàn.

**5. Xử lý lỗi rõ ràng.** `InvalidPathException` là lỗi cú pháp đường dẫn; `IOException` bao gồm lỗi mở/đọc tệp. Tệp không đọc được **không** được báo `MISMATCH`: đó là tình huống khác với “đã băm thành công nhưng digest sai”. `try-with-resources` đóng stream ngay cả khi đọc bị lỗi.

**6. Mã thoát dành cho script.** Hàm `run()` trả mã để dễ thử, `main()` dùng `System.exit(exitCode)` khi mã khác 0. Một script CI có thể fail ngay khi `verify` trả 1/2/3. Trong phương thức `digestFile`, không dùng `available()` làm kích thước file và không bao giờ đưa toàn bộ nội dung vào `String`.

## 5. Biên dịch, tạo dữ liệu mẫu và kiểm chứng từng bước

Yêu cầu máy có **JDK 17+**, không chỉ JRE. Tại thư mục chứa `FileDigestTool.java`:

~~~bash
javac FileDigestTool.java
java FileDigestTool
~~~

Lệnh `java FileDigestTool` không có đối số sẽ in cách dùng vào `stderr` và trả exit code `2`. Để tạo file `sample.bin` có **chính xác ba byte** `abc`, hãy chọn cách tương ứng hệ điều hành.

Trên Linux/macOS hoặc shell hỗ trợ `printf`:

~~~bash
printf 'abc' > sample.bin
~~~

Trên Windows PowerShell (kể cả Windows PowerShell 5.1), ghi byte trực tiếp để tránh encoding/BOM và newline không mong muốn:

~~~powershell
[System.IO.File]::WriteAllBytes(
    "sample.bin",
    [System.Text.Encoding]::ASCII.GetBytes("abc")
)
~~~

### 5.1. Tính digest SHA-256

~~~bash
java FileDigestTool digest SHA-256 sample.bin
~~~

Output chính xác khi nội dung file là ba byte ASCII `abc` và đối số đường dẫn là `sample.bin`:

~~~text
SHA-256 ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad sample.bin
~~~

Giá trị này là test vector chuẩn của SHA-256 với input `abc`, không phụ thuộc hệ điều hành/JDK nếu byte nội dung giống nhau. Nếu digest khác, kiểm tra cách tạo file, newline, BOM, encoding, tên file và việc file có bị thay đổi không.

### 5.2. Kiểm tra checksum đúng

~~~bash
java FileDigestTool verify SHA-256 sample.bin ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
~~~

Output:

~~~text
MATCH: SHA-256 sample.bin
~~~

Exit code: `0`. Trong Bash/Zsh kiểm tra mã bằng `echo $?` ngay sau lệnh; trong PowerShell là `$LASTEXITCODE`.

### 5.3. Kiểm tra checksum khác

~~~bash
java FileDigestTool verify SHA-256 sample.bin aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
~~~

Output:

~~~text
MISMATCH: SHA-256 sample.bin
  expected: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  actual:   ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
~~~

Exit code: `1`. Đây **không phải** lỗi không đọc được file: chương trình đã băm thành công và kết quả không trùng expected.

### 5.4. Tính và xác minh SHA-512

~~~bash
java FileDigestTool digest SHA-512 sample.bin
~~~

Output:

~~~text
SHA-512 ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f sample.bin
~~~

Dùng **chuỗi 128 ký tự** ở trên làm expected trong lệnh `verify SHA-512`:

~~~bash
java FileDigestTool verify SHA-512 sample.bin ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f
~~~

Output:

~~~text
MATCH: SHA-512 sample.bin
~~~

Nếu truyền checksum SHA-256 vào `verify SHA-512`, chương trình từ chối **sai độ dài** (exit `2`), không nhầm với trường hợp file bị sửa.

### 5.5. Tự thay đổi file sau khi có expected

Trên Linux/macOS:

~~~bash
printf 'abd' > sample.bin
java FileDigestTool verify SHA-256 sample.bin ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
~~~

Trên PowerShell, dùng:

~~~powershell
[System.IO.File]::WriteAllBytes(
    "sample.bin",
    [System.Text.Encoding]::ASCII.GetBytes("abd")
)
java FileDigestTool verify SHA-256 sample.bin ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
~~~

Kết quả sẽ là `MISMATCH` với exit `1` vì file đã đổi byte. Kiểm thử này xác nhận chương trình **băm lại nội dung hiện tại**, không chỉ so checksum đầu vào với chuỗi có sẵn.

## 6. Checklist kiểm thử chương trình

Cách kiểm thử tối thiểu trước khi dùng công cụ trong quy trình phát hành:

| Tình huống | Kỳ vọng |
| --- | --- |
| File `abc`, SHA-256 chuẩn | Hex đúng 64 ký tự bắt đầu `ba7816bf...`, exit `0` |
| `verify` với checksum đúng, viết hoa toàn bộ | `MATCH`, exit `0` (hex không phân biệt hoa/thường) |
| `verify` với checksum cùng độ dài nhưng khác byte | `MISMATCH`, exit `1` |
| Chuỗi expected chỉ dài 3 ký tự | Báo độ dài expected sai, exit `2` |
| Expected đúng độ dài nhưng chứa `g` hoặc dấu cách | Từ chối chuỗi hex không hợp lệ, exit `2` |
| Tên thuật toán `MD5` hoặc `SHA-1` | Không nằm trong allowlist, exit `2` |
| Thiếu một đối số hoặc chọn lệnh không tồn tại | In usage, exit `2` |
| File không tồn tại/không có quyền đọc | Báo lỗi đọc, exit `3` |
| File rỗng | Tính được digest của chuỗi byte rỗng, exit `0` |
| File lớn hơn heap có sẵn (nếu hệ thống đủ dung lượng I/O) | Không cần tải toàn bộ file vào heap; vẫn băm được theo stream |
| File chứa các byte 0x00, 0xFF hoặc Unicode được mã hóa | Băm byte trực tiếp, không chuyển qua `String` |
| Đổi tên file nhưng giữ nguyên các byte | Digest không thay đổi |
| Thêm đúng một newline vào cuối file | Digest thay đổi so với `abc` |

Để kiểm tra chéo độc lập, có thể dùng `sha256sum sample.bin` trên nhiều hệ Unix, hoặc `Get-FileHash -Algorithm SHA256 sample.bin` trong PowerShell. Cách trình bày output của các công cụ khác nhau nhưng **chuỗi digest 64 ký tự** của cùng bytes phải trùng. Trên Windows còn có `certutil -hashfile sample.bin SHA256` khi công cụ đó hiện diện. Hãy so sánh chuỗi hex, không so sánh nguyên cả dòng vì tiền tố/đường dẫn và kiểu chữ có thể khác nhau.

Dùng ví dụ SHA-256 của file rỗng để kiểm tra hành vi của `digest()` với không có lần `update()`:

~~~text
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
~~~

Một file không chứa byte nào khác với file chỉ có newline. Không coi “mở được file” là bằng chứng đọc hết dữ liệu; nếu I/O ném lỗi giữa chừng, công cụ trả exit `3` và không đưa ra một digest thành công.

## 7. Đưa công cụ vào workflow tải xuống hoặc CI

Một luồng xác minh release có thể gồm:

~~~text
Nguồn phát hành đáng tin -> giá trị SHA-256 đã công bố
                                         |
Tải release.zip -> FileDigestTool verify --+--> MATCH (0) -> cho phép bước tiếp
                                         +--> MISMATCH (1) -> chặn release
                                         +--> INPUT ERROR (2) -> sửa cấu hình
                                         +--> READ ERROR (3) -> khắc phục đường dẫn/quyền
~~~

Dòng `verify --+` ở sơ đồ là mô tả ý tưởng, **không phải** tham số CLI thực tế. Lệnh thật dùng `verify SHA-256 <file> <expectedHex>`.

Ví dụ Bash script chỉ triển khai tiếp khi verify thành công:

~~~bash
set -e
javac FileDigestTool.java
java FileDigestTool verify SHA-256 release.zip "$EXPECTED_SHA256"
# Chỉ chạy bước tiếp theo khi verify có exit 0.
~~~

Trong CI, `EXPECTED_SHA256` phải lấy từ nguồn được bảo vệ phù hợp (ví dụ manifest đã được xác thực, metadata release được tin cậy hoặc cấu hình commit đã kiểm soát). Truyền một biến môi trường chứa hex là tiện cho automation, nhưng **đặt biến** không tự chứng minh nguồn của nó an toàn. Nếu cần chữ ký artifact, hãy dùng cơ chế ký và tin cậy riêng rồi kiểm tra chữ ký trước khi chấp nhận checksum.

Không nên tự động “khắc phục” `MISMATCH` bằng cách tính digest mới rồi thay expected cho khớp; thao tác đó làm mất ý nghĩa xác minh. Khi lệch, kiểm tra đúng phiên bản artifact, nguồn expected, cách tải file, đường truyền, hậu xử lý và khả năng file đã bị thay đổi.

## 8. Ranh giới tin cậy và những điều checksum không giải quyết

### 8.1. Checksum không phải xác thực nguồn

Hãy xét hai tình huống:

- **Tệp thay đổi nhưng expected không đổi:** `verify` trả `MISMATCH`; công cụ đã phát hiện khác biệt byte.
- **Kẻ tấn công thay được cả tệp và expected:** hắn tính lại digest của file giả; `verify` trả `MATCH`. Đây là kết quả đúng của phép so sánh, nhưng **không** có bằng chứng file do nhà phát hành hợp pháp cung cấp.

Vì thế cần gắn checksum với một **nguồn tin cậy**: chữ ký số trên manifest, chứng thực của registry/repository được cấu hình, kênh phân phối có xác thực và chính sách kiểm soát sửa đổi. TLS bảo vệ đường truyền tới endpoint được xác thực nhưng không tự bảo đảm artifact có nguồn gốc/chuỗi cung ứng đáng tin nếu endpoint hoặc tài khoản phát hành bị xâm nhập.

Một chữ ký số trên bản phát hành vẫn cần xác minh public key/identity và chính sách ký. Nếu chỉ dùng hash trong trường hợp quan trọng, kết quả nên được mô tả đúng là *đã trùng checksum tham chiếu*.

### 8.2. Nội dung có thể bị đổi giữa kiểm tra và sử dụng (TOCTOU)

Chương trình mở tệp và băm bytes nó đọc tại thời điểm đó. Nếu tiến trình khác có thể sửa file **trong khi** băm hoặc sau khi `verify` trước khi hệ thống thực thi/cài đặt, tính toàn vẹn tại thời điểm sử dụng không được đảm bảo. Tính digest cũng không tạo snapshot nguyên tử của file đang bị thay đổi.

Khi đưa vào quy trình nhạy cảm, dùng staging directory với quyền ghi hạn chế, tệp bất biến theo nội dung (content-addressed storage) hoặc một artifact/handle đã xác minh được tiêu thụ trực tiếp. Đừng xác minh đường dẫn trong thư mục công cộng rồi mở lại đường dẫn đó sau một khoảng thời gian mà một tài khoản khác có thể thay file/symlink.

### 8.3. Đường dẫn do người khác cung cấp và link

CLI này giả định người gọi có quyền chọn file để đọc trên máy họ. Nếu chuyển chương trình thành HTTP API và lấy `file` từ request không tin cậy, phải xác định thư mục được phép, ngăn path traversal/symlink escape, không đọc file hệ thống và áp quota/kích thước/thời gian. `Path.of()` chỉ parse đường dẫn; nó **không** xác thực authorization hoặc sandbox. Mã hiện tại cố ý không thêm endpoint server hoặc filesystem policy vào CLI học tập.

### 8.4. Kích thước và lỗi vận hành

Đọc tuần tự theo block nhỏ giữ heap ổn định nhưng không giới hạn **thời gian đọc**, thời gian CPU, disk I/O hoặc việc đọc từ file/device có thể bị thay đổi. Khi mở rộng thành dịch vụ nhận dữ liệu bên ngoài, cần input byte cap, deadline/cancellation, concurrency cap và error handling riêng. Nếu file là tài nguyên đặc biệt (pipe/thiết bị) thay vì regular file, phải xác định chính sách trước khi đưa vào workflow unattended.

Checksum **không chứa metadata** như tên, path, owner, quyền truy cập hay timestamp. Không suy ra tệp chưa từng bị đổi hoặc cùng nguồn chỉ vì digest trùng với giá trị tính được trước đó trên một nội dung giống nhau.

## 9. Các lỗi phổ biến khi tự viết công cụ digest

1. **Băm tên file thay vì nội dung:** `MessageDigest.update(fileName.getBytes())` chỉ băm chuỗi tên; phải đọc byte từ `Files.newInputStream(path)`.
2. **Băm text sau khi decode và encode lại:** chuyển `byte[]` thành `String` có thể làm đổi bytes khi encoding/normalization khác; ở công cụ digest file, băm **nguyên byte**.
3. **Dùng `Files.readAllBytes(path)` cho mọi file:** có thể hết RAM với file lớn; xử lý qua stream.
4. **Băm toàn buffer mỗi vòng:** `md.update(buffer)` có thể đưa byte thừa vào digest ở vòng cuối; dùng `md.update(buffer,0,n)`.
5. **Bỏ qua lỗi đọc rồi băm dữ liệu đã có:** không được trả `MATCH` hoặc digest “thành công” cho file chỉ đọc được một phần.
6. **Nhầm checksum sai với I/O lỗi:** `MISMATCH` cần mã khác với `file not found` để script biết xử lý.
7. **Nhập expected không kiểm tra:** dấu cách, prefix `0x`, chữ hex sai, độ dài sai hoặc checksum của thuật toán khác phải được xử lý rõ. CLI mẫu không âm thầm trim/ghép/đổi dạng expected để tránh xác minh khác ý người dùng.
8. **Trộn thuật toán:** SHA-512 có đầu ra dài gấp đôi SHA-256; không thể so sánh hai giá trị khác thuật toán. CLI mẫu ghi rõ algorithm ở mọi thao tác.
9. **Dùng MD5/SHA-1 cho artifact cần an toàn trước đối thủ:** collision resistance không còn đủ cho mục đích này; dùng SHA-256/SHA-512 hoặc thuật toán hiện đại phù hợp policy.
10. **Tin checksum tải cùng nơi mà không xác thực nguồn:** có thể kiểm tra lỗi truyền tải vô tình, nhưng chưa đủ chống kẻ thay được cả file và checksum.
11. **Giả định file được bảo vệ sau `MATCH`:** nếu đường dẫn thay đổi giữa thời điểm verify và lúc sử dụng, cần chặn race.
12. **Dùng `equalsIgnoreCase` trên hex rồi cho rằng nó có chức năng bảo mật:** cách so sánh chuỗi không tự mang lại tính tin cậy; chuẩn hóa hex, parse thành bytes và dùng API phù hợp sẽ giảm lỗi triển khai.

## 10. Bài tập mở rộng theo cùng nền tảng

Sau khi hoàn thành CLI tối thiểu, có thể thực hành các yêu cầu sau trong phiên bản riêng:

- **Nhiều tệp:** tính digest của từng file trong danh sách, chuẩn hóa format manifest, phát hiện trùng path và xác định rõ xử lý lỗi từng file. Với manifest, cần quy định encoding, tên file có dấu cách/newline, thứ tự và phạm vi được ký; không tự ghép chuỗi tùy tiện để coi là format chuẩn.
- **Tiến độ:** đếm tổng byte đã đọc và in progress sang `stderr` khi phù hợp; tránh làm thay đổi format stdout mà script đang parse.
- **Kiểm soát dung lượng/thời gian:** giới hạn số byte, đặt deadline và hủy công việc nếu mở rộng thành dịch vụ; công cụ stream hiện tại không phải máy quét dữ liệu vô hạn.
- **Quy trình phát hành:** nhận SHA-256 từ một manifest **đã xác thực chữ ký**, rồi áp kiểm tra trước khi cài đặt. Tách bước “chứng thực nguồn manifest” và “so digest nội dung” để kiểm thử rõ.
- **Tính tương thích:** kiểm tra cùng một tệp bằng `sha256sum`/`Get-FileHash` hoặc một thư viện độc lập, so test vector `abc`, file rỗng và file nhị phân lớn.

### Tóm tắt kiến thức kỹ thuật cần nhớ

Quy trình của một công cụ digest đáng tin ở mức **toàn vẹn byte** là: xác định thuật toán cho phép → mở và đọc file theo stream → `MessageDigest.update(buffer,0,n)` → `digest()` → xuất hex nhất quán → parse expected nghiêm ngặt → so bytes và trả exit code rõ ràng. Mọi kết luận về **người phát hành** hoặc **an toàn khi sử dụng file sau verify** đều cần các giả định bảo mật riêng ngoài phép băm.

## Tài liệu tham khảo

- [Java SE 17 — MessageDigest](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html): `getInstance`, `update`, `digest`, `isEqual`.
- [Java SE 17 — HexFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HexFormat.html): `formatHex` và `parseHex`.
- [Java SE 17 — Files.newInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#newInputStream(java.nio.file.Path,java.nio.file.OpenOption...)): đọc file dạng stream và lỗi I/O.
- [NIST FIPS 180-4 — Secure Hash Standard](https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.180-4.pdf): các thuật toán băm SHA-2 và test vector tiêu chuẩn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-practice-002">23.2 Xây bộ xác thực thông điệp HMAC có ngữ cảnh và cơ chế chống phát lại</a>

<details>
<summary>Xem chi tiết</summary>

# 23.2 Xây bộ xác thực thông điệp HMAC có ngữ cảnh và cơ chế chống phát lại

## 1. Bài thực hành giải quyết vấn đề gì?

Giả sử một dịch vụ thanh toán nhận yêu cầu chuyển tiền qua HTTP:

```http
POST /v1/transfer?fast=false HTTP/1.1
Content-Type: application/json

{"amount":100}
```

Mã độc trên đường truyền, một thành phần trung gian không đáng tin hoặc một bên có được bản sao yêu cầu có thể cố gắng:

- Thay body thành `{"amount":900}` mà vẫn dùng thông tin xác thực cũ.
- Giữ body nhưng đổi path hoặc query để thực thi thao tác khác.
- Chuyển thông điệp đã được ký cho **dịch vụ khác** dùng chung khóa.
- Gửi lại **nguyên yêu cầu hợp lệ** nhiều lần trong khoảng thời gian ngắn (replay).
- Gửi đồng thời nhiều bản sao cùng nonce để vượt qua bước kiểm tra replay không nguyên tử.

Bài này xây dựng một giao thức minh họa dùng **HMAC-SHA256 + ngữ cảnh đã chuẩn hóa + timestamp + nonce + bộ lưu dấu replay nguyên tử** trên Java 17+. Người học triển khai cả bên tạo bằng chứng và bên xác minh, chạy thử sửa dữ liệu, hết hạn, gửi lặp và gửi đồng thời.

**HMAC chỉ xác thực rằng người tạo tag biết cùng secret key và dữ liệu được MAC không đổi.** HMAC không mã hóa nội dung, không tự phân biệt được hai bên cùng giữ shared secret, và **không tự chống replay**. Timestamp và nonce, khi được đưa vào dữ liệu có MAC và được kiểm tra bằng một kho replay dùng chung, mới tạo thành cơ chế chống phát lại.

## 2. Mục tiêu và tiêu chí hoàn thành

Sau bài thực hành, bạn có thể:

1. Thiết kế **định dạng byte đầu vào HMAC xác định, không mơ hồ**, có tên giao thức/phiên bản và ràng buộc đúng HTTP method, target (path + query), audience, media type, key ID, timestamp, nonce và body bytes.
2. Dùng `javax.crypto.Mac` với `HmacSHA256`, khóa HMAC đủ mạnh, `SecureRandom`, Base64url, `Clock` và so sánh tag bằng `MessageDigest.isEqual`.
3. Kiểm tra cửa sổ thời gian có độ lệch đồng hồ; chống xử lý hai lần cùng nonce bằng **claim nguyên tử** chỉ sau khi MAC hợp lệ.
4. Diễn giải sự khác biệt giữa **chống replay**, **idempotency**, **authorization** và **bảo mật kênh truyền**.
5. Xác định giới hạn của ví dụ chạy một JVM và cách thay replay cache bằng kho phân tán có TTL, phục vụ nhiều instance.
6. Viết ca kiểm thử chứng minh: sửa body/đường dẫn/audience → từ chối; request hợp lệ lần đầu → chấp nhận; gửi lại → từ chối; quá hạn → từ chối; 32 lần đồng thời → đúng một lần chấp nhận.

**Phạm vi:** một giao thức minh họa do hai phía cùng thỏa thuận. Không có server Spring Boot hoặc Redis bắt buộc để chạy ví dụ, nhưng bài chỉ rõ cách gắn vào HTTP và các điều kiện cần có khi đưa vào production. **Không tự nhận** đây là định dạng chuẩn hóa toàn ngành; nếu cần liên thông với hệ khác, cân nhắc chuẩn như HTTP Message Signatures (RFC 9421) và chính sách replay tương ứng.

## 3. Threat model, các biên tin cậy và nguyên tắc thiết kế

| Thành phần | Được tin cậy ở điểm nào? | Không được mặc định tin gì? |
| --- | --- | --- |
| Client có shared HMAC secret | Có thể tạo tag cho các thông điệp được phép phát hành. | Không đồng nghĩa client được phép mọi thao tác nghiệp vụ. |
| Request nhận qua HTTP | Có thể được xác minh theo bản byte và metadata thực nhận. | Body, path, header, timestamp, nonce và key ID đều là đầu vào chưa tin cậy trước khi MAC hợp lệ. |
| Key registry phía server | Ánh xạ `keyId` đến secret và danh tính/đối tác hợp lệ theo cấu hình server. | Không lấy key từ chính request hoặc tự động chấp nhận mọi `keyId`. |
| Clock phía server | Cung cấp `now` trong đơn vị UTC epoch seconds. | Đồng hồ mọi máy luôn trùng nhau tuyệt đối. |
| Replay store | Cấp quyền “nonce này được dùng lần đầu” một cách nguyên tử trên cùng phạm vi. | Bản đồ local ở từng pod đủ để chống replay trên toàn cụm. |
| Business handler | Chỉ chạy sau xác minh, replay claim, authorization và kiểm tra dữ liệu nghiệp vụ. | Một MAC đúng tự động tương đương giao dịch hợp lệ. |

Các thuộc tính cần đạt:

- **Integrity:** đổi bất cứ byte/metadata đã đưa vào HMAC thì tag cũ không hợp lệ, trừ trường hợp phá được giả định mật mã.
- **Context binding/domain separation:** tag của request ở `payments-api`, `POST`, `/v1/transfer?fast=false` không được dùng thành request khác có thông điệp ràng buộc khác.
- **Freshness:** timestamp có chữ ký chỉ được chấp nhận trong khoảng thời gian xác định.
- **Uniqueness:** trong khoảng hợp lệ, mỗi cặp `(keyId, nonce)` chỉ được “claim” một lần trên cùng phạm vi tin cậy.
- **Fail closed:** nếu không thể xác thực tag hoặc không thể xác nhận nonce chưa dùng, không thực thi hành động đặc quyền.

Hệ thống vẫn cần **TLS**, quản lý secret, authorization, giới hạn tần suất và kiểm tra nghiệp vụ. Ngay cả request HMAC hợp lệ cũng có thể là yêu cầu có giá trị sai về mặt nghiệp vụ hoặc đến từ một client bị lộ secret.

## 4. Hợp đồng thông điệp và định dạng MAC

Để hai bên có cùng tag, giao thức phải nói rõ **chính xác những byte được ký**, không chỉ nói “ký JSON”. Trong bài này, payload được ràng buộc với:

| Trường | Định dạng ký | Nguồn cần dùng ở verifier |
| --- | --- | --- |
| Domain/version | `security-cryptography/request-hmac/v1` | Hằng số của giao thức, do server định nghĩa. |
| `keyId` | UTF-8 | Giá trị nhận được, sau đó lookup trong registry tin cậy. |
| `timestamp` | Chuỗi số thập phân UTC epoch seconds qua `Long.toString` | Đọc từ bằng chứng request; phải kiểm tra cửa sổ thời gian. |
| `nonce` | Chuỗi Base64url không padding của 16 byte ngẫu nhiên | Đọc từ bằng chứng request; kiểm tra mã hóa hợp lệ và claim chống replay. |
| `audience` | UTF-8, ví dụ `payments-api` | Audience đúng của dịch vụ thực tế, không tin một header tự khai báo. |
| `method` | UTF-8, ví dụ `POST` | Method thực tế do HTTP server xử lý. |
| `target` | UTF-8, ví dụ `/v1/transfer?fast=false` | Path + query thực tế theo quy ước canonicalization đã thống nhất. |
| `mediaType` | UTF-8, ví dụ `application/json` | Content type thực tế theo biểu diễn được cả hai bên quy định. |
| `body` | **Raw bytes**, không phải chuỗi parse rồi serialize lại | Chính bytes mà giao thức xác định là request body được bảo vệ. |

**Cách đóng khung từng trường:** chuyển thành byte UTF-8 nếu là text; viết **4 byte độ dài big-endian**, tiếp đó là đúng số byte dữ liệu. Với body, viết độ dài và byte body. Các trường xuất hiện **theo thứ tự cố định** trong bảng.

```text
canonicalRequest =
  len(domain/version) || domain/version
  || len(keyId) || keyId
  || len(decimal(timestamp)) || decimal(timestamp)
  || len(nonce) || nonce
  || len(audience) || audience
  || len(method) || method
  || len(target) || target
  || len(mediaType) || mediaType
  || len(bodyBytes) || bodyBytes

tagBytes = HMAC-SHA256(secretForKeyId, canonicalRequest)
tagText  = Base64urlWithoutPadding(tagBytes)
```

Ký hiệu `len(x)` ở đây là 4-byte length prefix, `||` là phép nối byte. Thay đổi ranh giới trường không được cho ra cùng dữ liệu canonical: `["ab", "c"]` khác `["a", "bc"]`. Đây là lý do không dùng `method + path + body` hoặc nối chuỗi với ký tự phân tách tùy tiện, vì các trường có thể chứa chính dấu phân tách đó.

**Đóng khung không có nghĩa được tự do sửa biểu diễn:** `/a%2Fb` có thể được router xử lý khác `/a/b`, query có thể bị reorder, media type có thể khác hoa/thường hoặc có tham số `charset`. Hai phía phải thống nhất một representation trước khi tính HMAC. Ví dụ dùng các chuỗi cố định được đưa vào đúng thứ tự; với HTTP thật, dùng method/target **do runtime quan sát và ứng dụng quy định cách chuẩn hóa**, không lấy giá trị method/path giả từ header do client tự đưa.

Chúng ta ký **body bytes**, không ký Object Java hay một chuỗi JSON được tạo lại sau khi parse. Cùng ý nghĩa JSON nhưng khác dấu cách, thứ tự trường hay encoding có thể có tag khác; đây là kết quả dự kiến khi hợp đồng chọn “exact bytes”. Nếu yêu cầu ký JSON theo ngữ nghĩa, phải định nghĩa một cơ chế JSON canonicalization riêng và dùng đồng nhất ở cả hai phía.

## 5. Quy trình ký và xác minh

<escape>Luồng bên gửi và bên nhận:</escape>

```text
SENDER
  -> Lấy keyId và shared HMAC secret đúng audience
  -> Xác định method, target, audience, mediaType, body bytes
  -> timestamp = giờ hiện tại (epoch seconds)
  -> nonce = 16 byte từ SecureRandom; Base64url không padding
  -> Canonical encode có prefix, length framing, thứ tự cố định
  -> HMAC-SHA256(secret, canonical bytes)
  -> Gửi metadata + body + tag qua HTTPS

RECEIVER
  -> Giới hạn kích thước request, parse metadata theo định dạng
  -> Xác định audience/method/target/body thật từ server
  -> Tra keyId trong registry đáng tin và kiểm tra policy client
  -> Kiểm tra timestamp ở khoảng cho phép
  -> Tái tạo canonical bytes, tính HMAC, so sánh tag
  -> Claim (keyId, nonce) NGUYÊN TỬ trong kho replay
  -> Kiểm tra authorization/idempotency/validation nghiệp vụ
  -> Thực thi hoặc trả lỗi; không thực thi hai lần với cùng nonce
```

Thứ tự thực tế có thể gồm validate cú pháp và giới hạn kích thước trước khi tính MAC để chống tiêu hao tài nguyên. **Không ghi nonce vào replay store trước khi HMAC đúng**: nếu làm vậy, kẻ tấn công không có secret vẫn có thể gửi một yêu cầu giả cùng nonce để chiếm chỗ và gây từ chối dịch vụ cho request hợp lệ.

### Cửa sổ thời gian

Bài dùng `WINDOW_SECONDS = 300` (5 phút) và chấp nhận:

```text
-300 <= serverNowEpochSeconds - signedTimestamp <= 300
```

Kiểm tra **cả quá cũ lẫn quá xa tương lai**; timestamp được đưa vào MAC nên không thể được kẻ nghe lén tự nâng lên thời gian mới mà vẫn giữ tag hợp lệ.

Cửa sổ 5 phút **không đủ để chống replay**: attacker vẫn có 5 phút để gửi lại một bản hợp lệ. Sau khi tag hợp lệ, một replay store phải từ chối mọi lần claim `(keyId, nonce)` thứ hai trong thời gian còn chấp nhận timestamp đó.

Để tránh cache quên nonce quá sớm, ví dụ lưu đến:

```text
expiresAt = signedTimestamp + WINDOW_SECONDS + 1
```

Cộng thêm 1 giây vì điều kiện freshness chấp nhận biên `timestamp + 300` (inclusive). Nếu timestamp là 300 giây **tương lai**, thời gian giữ có thể xấp xỉ 601 giây tính từ thời điểm nhận; vì thế đặt TTL cố định đúng 300 giây từ `now` sẽ không đủ cho mọi request hợp lệ. Với kho TTL thực tế, TTL phải tính từ `expiresAt - now` và có chính sách dự phòng độ chính xác đồng hồ/lưu trữ thích hợp.

## 6. Mã Java 17+ hoàn chỉnh

Tạo tệp **`HmacReplayDemo.java`** dưới đây. Đây là một chương trình Java độc lập, không dùng thư viện ngoài JDK. Nó cố tình có `Clock.fixed` để phép thử hết hạn tái lập được; trong ứng dụng thật thay bằng `Clock.systemUTC()` và cấp secret từ key store/secret manager.

```java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class HmacReplayDemo {
    private static final long WINDOW_SECONDS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();

    record Message(String method, String target, String audience,
                   String mediaType, byte[] body) {
        Message {
            Objects.requireNonNull(method);
            Objects.requireNonNull(target);
            Objects.requireNonNull(audience);
            Objects.requireNonNull(mediaType);
            body = Objects.requireNonNull(body).clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    record Proof(String keyId, long timestamp, String nonce, String tag) {}
    record ReplayKey(String keyId, String nonce) {}

    static final class ReplayCache {
        private final ConcurrentHashMap<ReplayKey, Long> used =
                new ConcurrentHashMap<>();

        boolean claim(ReplayKey key, long expiry, long now) {
            AtomicBoolean accepted = new AtomicBoolean(false);
            used.compute(key, (k, previousExpiry) -> {
                if (previousExpiry == null || previousExpiry <= now) {
                    accepted.set(true);
                    return expiry;
                }
                return previousExpiry;
            });
            return accepted.get();
        }
    }

    static final class Verifier {
        private final Map<String, SecretKey> keys;
        private final String expectedAudience;
        private final Clock clock;
        private final ReplayCache replayCache;

        Verifier(Map<String, byte[]> keyBytes, String audience,
                 Clock clock, ReplayCache replayCache) {
            Map<String, SecretKey> snapshot = new HashMap<>();
            for (var entry : keyBytes.entrySet()) {
                byte[] key = Objects.requireNonNull(entry.getValue()).clone();
                if (key.length < 32) {
                    throw new IllegalArgumentException("Short HMAC key");
                }
                snapshot.put(
                        entry.getKey(), new SecretKeySpec(key, "HmacSHA256"));
            }
            this.keys = Map.copyOf(snapshot);
            this.expectedAudience = Objects.requireNonNull(audience);
            this.clock = Objects.requireNonNull(clock);
            this.replayCache = Objects.requireNonNull(replayCache);
        }

        boolean verify(Message message, Proof proof) {
            if (message == null || proof == null
                    || !expectedAudience.equals(message.audience())
                    || proof.keyId() == null || proof.nonce() == null
                    || proof.tag() == null || proof.keyId().length() > 64
                    || proof.nonce().length() > 64
                    || proof.tag().length() > 128) {
                return false;
            }

            SecretKey key = keys.get(proof.keyId());
            if (key == null) return false;

            long now = clock.instant().getEpochSecond();
            long drift;
            try {
                drift = Math.subtractExact(now, proof.timestamp());
            } catch (ArithmeticException e) {
                return false;
            }
            if (drift < -WINDOW_SECONDS || drift > WINDOW_SECONDS) {
                return false;
            }

            byte[] nonceBytes;
            byte[] suppliedTag;
            try {
                nonceBytes = Base64.getUrlDecoder().decode(proof.nonce());
                suppliedTag = Base64.getUrlDecoder().decode(proof.tag());
            } catch (IllegalArgumentException e) {
                return false;
            }
            String canonicalNonce = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(nonceBytes);
            if (nonceBytes.length != 16
                    || !canonicalNonce.equals(proof.nonce())
                    || suppliedTag.length != 32) {
                return false;
            }

            byte[] expectedTag = hmac(key, canonical(message, proof));
            if (!MessageDigest.isEqual(expectedTag, suppliedTag)) {
                return false;
            }

            long expiresAt;
            try {
                expiresAt = Math.addExact(
                        proof.timestamp(), WINDOW_SECONDS + 1);
            } catch (ArithmeticException e) {
                return false;
            }

            return replayCache.claim(
                    new ReplayKey(proof.keyId(), proof.nonce()),
                    expiresAt, now);
        }
    }

    private static void writePart(
            DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length);
        out.write(value);
    }

    private static void writeText(
            DataOutputStream out, String text) throws IOException {
        writePart(out, text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] canonical(Message message, Proof proof) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            writeText(out, "security-cryptography/request-hmac/v1");
            writeText(out, proof.keyId());
            writeText(out, Long.toString(proof.timestamp()));
            writeText(out, proof.nonce());
            writeText(out, message.audience());
            writeText(out, message.method());
            writeText(out, message.target());
            writeText(out, message.mediaType());
            writePart(out, message.body());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "In-memory encoding failed", e);
        }
        return buffer.toByteArray();
    }

    private static byte[] hmac(SecretKey key, byte[] input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "HmacSHA256 unavailable", e);
        }
    }

    static Proof sign(
            Message message, String keyId, byte[] secret, long timestamp) {
        byte[] randomNonce = new byte[16];
        RANDOM.nextBytes(randomNonce);
        String nonce = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(randomNonce);

        Proof unsigned = new Proof(keyId, timestamp, nonce, "");
        String tag = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(
                        new SecretKeySpec(secret, "HmacSHA256"),
                        canonical(message, unsigned)));
        return new Proof(keyId, timestamp, nonce, tag);
    }

    public static void main(String[] args) {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);

        Clock clock = Clock.fixed(
                Instant.parse("2026-01-01T00:00:00Z"),
                ZoneOffset.UTC);
        long now = clock.instant().getEpochSecond();

        Message original = new Message(
                "POST", "/v1/transfer?fast=false",
                "payments-api", "application/json",
                "{\"amount\":100}".getBytes(StandardCharsets.UTF_8));
        Proof proof = sign(original, "kid-1", key, now);
        Verifier verifier = new Verifier(
                Map.of("kid-1", key), "payments-api",
                clock, new ReplayCache());

        Message alteredBody = new Message(
                original.method(), original.target(),
                original.audience(), original.mediaType(),
                "{\"amount\":900}".getBytes(StandardCharsets.UTF_8));
        Message alteredTarget = new Message(
                "POST", "/v1/transfer?fast=true",
                original.audience(), original.mediaType(), original.body());
        Message otherAudience = new Message(
                original.method(), original.target(),
                "reports-api", original.mediaType(), original.body());

        System.out.println(
                "body changed: " + verifier.verify(alteredBody, proof));
        System.out.println(
                "target changed: " + verifier.verify(alteredTarget, proof));
        System.out.println(
                "wrong audience: " + verifier.verify(otherAudience, proof));
        System.out.println(
                "first request: " + verifier.verify(original, proof));
        System.out.println(
                "replay: " + verifier.verify(original, proof));

        Proof old = sign(original, "kid-1", key, now - 301);
        System.out.println(
                "expired: " + verifier.verify(original, old));

        Proof concurrent = sign(original, "kid-1", key, now);
        long accepted = IntStream.range(0, 32).parallel()
                .filter(i -> verifier.verify(original, concurrent))
                .count();
        System.out.println("parallel accepted: " + accepted);
    }
}
```

### Biên dịch và chạy

Chạy tại thư mục chứa `HmacReplayDemo.java`:

```bash
javac HmacReplayDemo.java
java HmacReplayDemo
```

Kết quả chuẩn theo chương trình:

```text
body changed: false
target changed: false
wrong audience: false
first request: true
replay: false
expired: false
parallel accepted: 1
```

Chương trình sinh ngẫu nhiên secret 32 byte và nonce 16 byte, nhưng chỉ in trạng thái boolean và bộ đếm nên stdout ở trên ổn định. Ca thử đã được chạy với triển khai JDK hiện có; cú pháp sử dụng Java 17+. Không có secret hoặc tag thực nào được in ra log.

### Giải thích theo từng lớp

**`Message`:** giữ method, target, audience, mediaType và body. Constructor record clone `byte[]` đầu vào, accessor `body()` trả bản clone, tránh người gọi sửa mảng nội bộ sau khi yêu cầu đã được xác định. `record` tự động chưa đủ để bảo vệ thành phần mảng mutable; clone ở cả hai phía bảo đảm snapshot của ví dụ.

**`Proof`:** lưu `keyId`, timestamp, nonce và tag. Tag **không được đưa vào nội dung được MAC**, vì sẽ tạo phụ thuộc vòng; các trường `keyId`, timestamp và nonce thì phải có trong canonical encoding.

**`canonical(...)`:** viết toàn bộ trường theo thứ tự cố định bằng `DataOutputStream.writeInt` (4-byte big-endian) rồi ghi bytes. Dùng `StandardCharsets.UTF_8` cho text; `Long.toString` cho timestamp; body giữ nguyên bytes. Prefix `.../v1` ràng buộc miền ứng dụng và phiên bản giao thức.

**`hmac(...)`:** tạo `Mac.getInstance("HmacSHA256")` mới cho mỗi lần tính. `Mac` không nên dùng chung một instance mutable cho các thread mà không đồng bộ; ví dụ tạo riêng cho từng phép tính. `SecretKeySpec` được xây từ key bytes; server tạo bản sao key khi đưa vào key registry của `Verifier`.

**`sign(...)`:** dùng `SecureRandom` tạo 16 byte nonce (128-bit không gian ngẫu nhiên), chuyển sang Base64url không padding và ký canonical bytes. Nonce không cần giữ bí mật nhưng phải có xác suất trùng đủ nhỏ; **không dùng `Random`, timestamp đơn thuần hoặc counter cục bộ dễ tái sử dụng khi nhiều máy phát yêu cầu**.

**`Verifier.verify(...)`:** lấy key qua registry, kiểm tra thời gian, decode nonce/tag và độ dài chuẩn, tính lại HMAC, so sánh tag bằng `MessageDigest.isEqual`, **sau đó mới claim nonce**. Thuật toán do server cố định, không cho client tự chọn `MD5` hay một algorithm yếu qua header.

**`ReplayCache.claim(...)`:** khóa replay là record `(keyId, nonce)`, tránh ghép chuỗi dễ mơ hồ. `ConcurrentHashMap.compute` cập nhật **nguyên tử theo cùng key**: nếu chưa có hoặc entry đã hết hạn thì chỉ một request có thể nhận `true`; những request đồng thời khác nhận `false`. `AtomicBoolean` chỉ thu thập kết quả cho lần gọi đó; không thay thế sự nguyên tử của `compute`.

**`Clock.fixed(...)`:** dùng thời điểm giả để kiểm thử biên, thay vì phụ thuộc thời gian máy tính và `Thread.sleep`. Khi chạy thật, cấp clock từ server, đồng bộ thời gian giữa các node theo chính sách hạ tầng.

**Giới hạn quan trọng:** `ReplayCache` này chỉ dùng trong **một JVM** và **không dọn toàn bộ nonce cũ nếu chúng không được truy cập lại**; do đó **không phù hợp cho production** cả về phân tán và quản lý bộ nhớ. Mục tiếp theo đưa ra thay thế có TTL. Đừng đưa nguyên cache minh họa này vào dịch vụ chạy lâu hoặc nhiều instance.

## 7. Thử nghiệm: vì sao từng yêu cầu bị chặn?

| Ca chạy | Giá trị kỳ vọng | Giải thích |
| --- | --- | --- |
| Body `{"amount":100}` → `{"amount":900}` | `false` | Body bytes đổi, HMAC dự kiến khác tag của `Proof`. |
| Target `fast=false` → `fast=true` | `false` | Query/path là thành phần context có MAC; tag cũ không còn phù hợp. |
| Audience `payments-api` → `reports-api` | `false` | Verifier kỳ vọng audience local khác; đồng thời audience là trường có MAC. |
| Request hợp lệ lần đầu | `true` | Key, timestamp, body, context và nonce đều được xác minh; claim thành công. |
| Gửi lại chính xác yêu cầu đó | `false` | MAC vẫn đúng nhưng nonce đã có trong replay store. |
| Timestamp `now - 301` | `false` | Dù tag được ký đúng bằng secret, request nằm ngoài cửa sổ 300 giây. |
| 32 bản sao cùng nonce gửi song song | `1` lượt nhận | `compute` cho đúng một request giành được claim đối với một replay key. |

Có một điểm dễ bị bỏ sót: **MAC lỗi không làm tiêu thụ nonce**. Trong `main`, yêu cầu bị thay body/target/audience được kiểm tra **trước** request gốc. Nếu server tự đưa nonce vào cache trước khi xác minh tag, các thử nghiệm giả mạo có thể khiến request hợp lệ đến sau bị từ chối. Ví dụ vẫn nhận `first request: true`, chứng minh nhánh MAC lỗi không chiếm nonce.

Phần mềm thực tế không nên trả cho client các thông báo chi tiết kiểu "MAC sai ở trường body" hoặc log secret/tag thô. Có thể dùng mã lỗi chung như xác thực không hợp lệ, còn log nội bộ giữ mã lý do có kiểm soát, correlation ID và thông tin không nhạy cảm.

## 8. Thay cache local bằng replay store phân tán

Với hai instance `api-1` và `api-2` sau load balancer, lưu nonce trong `ConcurrentHashMap` riêng từng instance sẽ tạo lỗi:

```text
Client -> request hợp lệ ------> api-1: nonce chưa có, nhận
       -> gửi lại cùng request -> api-2: nonce chưa có, nhận lần 2
```

Tất cả instance xác minh **cùng phạm vi** phải thống nhất quyết định claim. Một lựa chọn là Redis có phép **SET-if-absent kèm TTL trong cùng một operation**:

```text
SET replay:payments-api:kid-1:<nonce> 1 NX EX <ttlSeconds>
```

Trong đó các giá trị kèm dấu ngoặc nhọn chỉ là **ký hiệu tham số giải thích giao thức**, không phải lệnh literal để copy chạy ngay. Ý nghĩa:

- `NX`: chỉ ghi khi key chưa tồn tại; kết quả thành công = claim lần đầu.
- `EX`: hết hạn sau `ttlSeconds` giây; chỉ đặt sau khi MAC hợp lệ và timestamp trong cửa sổ.
- `ttlSeconds`: tính từ `expiresAt - now`, không dùng cố định 300 giây cho mọi timestamp.
- Key nên bao gồm **namespace môi trường + audience + danh tính/key scope đáng tin + nonce**; tránh lẫn sandbox với production hoặc các tenant dùng key ID trùng nhau.
- Đặt TTL **cùng lệnh NX**; đừng thực hiện `GET` rồi `SET` ở hai round trip vì có race, và đừng đặt `SETNX` rồi `EXPIRE` riêng nếu lỗi giữa hai lệnh có thể để key không hạn.

Ví dụ TTL: client gửi timestamp bằng `now + 300` (tương lai trong giới hạn). Đến giây hiện tại, request vẫn hợp lệ thêm khoảng 600 giây; chỉ nhớ nonce trong 300 giây sẽ mở cửa cho replay ở nửa sau khoảng đó. `expiresAt = timestamp + 301` bảo toàn dấu nonce tới khi request chắc chắn hết thời gian được chấp nhận.

**Trong trường hợp Redis ngắt kết nối hoặc không thể xác nhận kết quả SET NX**, verifier không nên coi nonce là mới rồi chạy nghiệp vụ. Áp dụng chính sách fail closed hoặc một chiến lược nhất quán, được thiết kế rõ với nghiệp vụ. Sticky session, cache cục bộ có TTL riêng và fallback “cho qua nếu Redis lỗi” đều không chứng minh chống replay trên toàn hệ thống.

Một lựa chọn khác là bảng CSDL có unique constraint trên replay key, INSERT tranh chấp và cột expiry có cleanup. Khi chọn CSDL, phải cân nhắc transaction isolation, thời gian giữ, phạm vi key, chi phí ghi và trạng thái nghiệp vụ. **Không phân biệt “đã được xác thực” với “đã thực thi giao dịch”**: chống replay khóa đường vào lần hai nhưng không bảo đảm giao dịch hoàn tất một lần trong mọi sự cố.

### Tính đúng thời gian và dọn dữ liệu

- Lưu expiry dựa trên **timestamp đã MAC**, không cho client chọn TTL tùy ý.
- Xác minh timestamp theo clock server đáng tin; đồng bộ đồng hồ các node.
- Khi timestamp vừa chạm biên cho phép, claim vẫn phải giữ được nonce cho tới khi request hết cửa sổ.
- TTL và cleanup phải **không xóa sớm** entry của request còn hợp lệ.
- Có giới hạn số lượng request, kích thước nonce và tốc độ gọi để replay store không thành mục tiêu gây cạn tài nguyên.

## 9. Gắn verifier vào xử lý HTTP thực tế

Để đưa ý tưởng vào Servlet, Spring MVC, WebFlux hay gateway, cần lớp adapter lấy dữ liệu **thực từ request** và chuyển thành `Message`/`Proof`:

1. Đọc body **một lần theo chiến lược đã giới hạn kích thước**, giữ đúng bytes dự kiến được ký; nếu cần đọc lại trong controller thì dùng wrapper/buffering phù hợp hoặc chia sẻ payload đã lưu. Đừng mặc định đọc lại stream đã bị filter tiêu thụ.
2. Lấy method thật và URI target theo một quy ước ổn định, bao gồm query nếu hợp đồng yêu cầu. Quy định rõ xử lý percent-encoding, dấu `/`, path normalization, dấu `+` trong query, nhiều giá trị cùng tên và thay đổi do reverse proxy.
3. Lấy content type đã chuẩn hóa theo hợp đồng đã công bố; nếu dùng raw header, bảo toàn đúng representation ở phía signer/verifier. Không tin một header "method", "path" hay "audience" tự khai báo thay cho quan sát từ server.
4. Lấy `keyId`, `timestamp`, `nonce`, `tag` qua các header đã quy định; hạn chế độ dài, số lượng, cách parse và từ chối header trùng gây mơ hồ. Phải định nghĩa timestamp đơn vị **giây** chứ không trộn với milliseconds.
5. Xác định `audience` từ **cấu hình dịch vụ/route hiện hành** và key registry; đừng để caller chỉ cần sửa header để làm request được kiểm trong miền khác.
6. Xây `Message` và `Proof` bất biến; kiểm tra cú pháp và freshness rồi MAC; claim replay nguyên tử; sau đó đánh giá authorization, validation và xử lý nghiệp vụ.

Không nhất thiết gửi `audience` như một header: có thể là giá trị cấu hình của mỗi endpoint và cả hai bên biết trước. Nếu truyền header vì mục đích debug hoặc routing, verifier **vẫn** phải đối chiếu với audience local.

**Reverse proxy là nguồn sai khác điển hình:** một bên MAC `/api/v1/a%2Fb` trong khi bên kia đọc path đã decode thành `/api/v1/a/b`. Phải định nghĩa điểm chuẩn hóa thống nhất, kiểm tra ứng dụng trên đúng representation. Nếu gateway viết lại path hoặc giải nén body, xác định rõ việc ký diễn ra trước/sau phép biến đổi đó. Không tự "sửa" lỗi MAC bằng cách bỏ field target hay bỏ ký body.

Khi body là JSON, chính sách parse JSON và schema validation vẫn chạy riêng; HMAC chỉ kiểm **bytes**. Có thể áp dụng thêm canonical JSON theo tiêu chuẩn nếu nghiệp vụ đòi cùng nội dung ngữ nghĩa có cùng tag, nhưng không âm thầm thay hợp đồng raw bytes.

## 10. Quản lý khóa và các ranh giới bảo mật còn lại

### 10.1 Secret và key ID

- Dùng key HMAC sinh từ CSPRNG, độ dài phù hợp chính sách; ví dụ `new byte[32]` + `SecureRandom.nextBytes` tạo 256 bit ngẫu nhiên cho HMAC-SHA256.
- Không tạo secret bằng password yếu, mã hóa chuỗi dễ đoán hay copy một secret cứng vào source repository.
- Mỗi bên chia sẻ secret phải có kênh cấp phát/quản lý an toàn; ưu tiên secret manager/HSM khi phù hợp, giới hạn quyền đọc và audit.
- `keyId` là **định danh tra cứu**, không phải bằng chứng tin cậy. Registry server phải biết key thuộc đối tác nào và được dùng với audience/operation nào; khóa bị thu hồi phải bị từ chối dù tag cũ tính đúng.
- Key ID được đưa vào MAC, giảm nhầm lẫn metadata; khi rotate, server có thể kiểm tra nhiều key ID trong thời gian chuyển đổi được phê duyệt và phải đặt thời điểm vô hiệu hóa rõ ràng.
- Đừng dùng chung secret cho các giao thức khác nhau nếu có thể cấp khóa riêng; domain prefix giúp phân tách nhưng không thay thế quản lý key độc lập.

### 10.2 So sánh tag và lỗi định dạng

`MessageDigest.isEqual(expectedTag, suppliedTag)` giúp tránh phép so sánh byte-by-byte ngây thơ có thể dừng ở byte khác đầu tiên. Ví dụ từ chối tag không phải đúng 32 byte HMAC-SHA256 trước khi so sánh và không so Base64 text qua `String.equals` để làm quyết định mật mã.

Cần hiểu đúng giới hạn: đây **không bảo đảm toàn bộ HTTP endpoint chạy thời gian cố định**; các nhánh cú pháp, tra key, kiểm timestamp, dữ liệu khác nhau vẫn có thể khác thời gian. Mục tiêu cụ thể là giảm phụ thuộc thời gian của bước so sánh tag. Mọi bí mật phải được bảo vệ ở cả log, lỗi trả client và nơi lưu trữ.

### 10.3 HMAC, TLS và authorization khác nhau

- **HMAC:** chứng minh tính toàn vẹn và sự biết shared secret tương ứng với `keyId`. Cả hai bên sở hữu secret có thể tạo tag; không có bằng chứng chống chối bỏ kiểu private/public key.
- **TLS:** bảo vệ kênh truyền và danh tính endpoint theo thiết lập TLS; HMAC không thay thế HTTPS.
- **Authorization:** client có quyền gọi route, chuyển số tiền nào, thao tác trên tài khoản nào? Đây là kiểm tra nghiệp vụ dựa trên danh tính/role và policy đã thẩm định.
- **Replay protection:** cùng bằng chứng không được thực thi nhiều lần trong cửa sổ; không chặn một client hợp lệ chủ động phát sinh **nhiều yêu cầu mới** với nonce khác nhau.
- **Rate limiting / abuse monitoring:** cần cho brute force, tài nguyên và yêu cầu hợp lệ nhưng lạm dụng. Các lớp bảo vệ này bổ sung cho nhau.

Một MAC hợp lệ trên `{"amount":100}` vẫn có thể vi phạm hạn mức giao dịch; phải kiểm tra quyền, số dư, anti-fraud và chính sách nghiệp vụ riêng.

## 11. Replay protection không đồng nghĩa idempotency

Giả sử client gửi yêu cầu thanh toán đã ký. Server xác thực tag, claim nonce thành công, rồi:

- Server đã ghi giao dịch nhưng response bị mất.
- Client retry lại **cùng request/tag/nonce** vì không biết kết quả.
- Replay store từ chối lần hai như thiết kế.

Cơ chế này ngăn chạy lại cùng request, nhưng client chưa biết lần đầu thành công hay thất bại. Để xây API an toàn cho nghiệp vụ quan trọng, còn cần **idempotency key** và trạng thái giao dịch có thể tra cứu/reconcile, hoặc quy trình thao tác có idempotency được quản lý độc lập.

Phân biệt:

| Cơ chế | Khóa kiểm soát | Mục đích |
| --- | --- | --- |
| HMAC | Shared secret + canonical bytes | Chống sửa đổi dữ liệu đã MAC và xác nhận bên biết secret. |
| Timestamp | Cửa sổ thời gian | Giới hạn thời gian một bằng chứng có thể được chấp nhận. |
| Nonce replay store | `(trust scope, nonce)` | Từ chối dùng lại cùng bằng chứng trong cửa sổ. |
| Idempotency | `(client identity, operation key)` và trạng thái nghiệp vụ | Cho phép retry an toàn và trả kết quả nhất quán, tránh thực hiện nghiệp vụ lặp. |
| Database transaction | Ràng buộc nhất quán dữ liệu | Bảo vệ cập nhật dữ liệu khỏi lỗi một phần và xung đột đồng thời. |

**Claim nonce và commit giao dịch không tự trở thành một giao dịch nguyên tử xuyên Redis/CSDL.** Nếu service crash sau claim nhưng trước commit, retry cùng nonce bị chặn dù nghiệp vụ chưa chạy; có thể cần quy trình phát nonce/tag mới theo cùng idempotency key, lưu trạng thái xử lý hoặc orchestrate phù hợp. Không tuyên bố “exactly once” chỉ vì đã dùng `SET NX`.

Đừng cho phép bỏ qua replay store để giải quyết lỗi retry. Hãy định nghĩa một hợp đồng idempotency rõ ràng cho client hợp lệ, đồng thời vẫn bảo vệ giao dịch khỏi request giả và replay độc hại.

## 12. Kiểm thử có hệ thống

### 12.1 Các ca tối thiểu nên có

| Nhóm | Đầu vào kiểm thử | Điều mong đợi |
| --- | --- | --- |
| Happy path | Tag HMAC đúng; timestamp trong cửa sổ; nonce chưa có | Chấp nhận đúng một lần. |
| Tamper | Sửa 1 byte body, đổi method, target/query, audience hoặc media type nhưng giữ tag cũ | Từ chối. |
| Proof tamper | Đổi timestamp, nonce hoặc key ID nhưng không ký lại | Từ chối. |
| Forged tag | Tag sai 1 byte; Base64url không hợp lệ; độ dài tag không phải 32 byte | Từ chối, không claim nonce. |
| Freshness | Timestamp quá cũ, quá xa tương lai, bằng đúng biên ±300 giây | Theo chính sách: ngoài biên từ chối, trong/đúng biên chấp nhận nếu MAC và nonce hợp lệ. |
| Replay | Gửi y hệt request đã nhận | Từ chối lần 2. |
| Concurrency | 32–100 bản cùng `(keyId, nonce)` trên nhiều thread | Đúng một claim thành công. |
| Key lifecycle | Key ID không tồn tại, key thu hồi, rotate sang key mới | Từ chối key không hợp lệ; key được cho phép hoạt động theo policy. |
| Cross-audience | Dùng proof ký cho service A tại service B | Từ chối. |
| Multi-instance | Bản gốc đến node A, replay đến node B | Từ chối nhờ store dùng chung. |
| Cache failure | Không nhận được phản hồi claim nonce | Fail closed, không chạy nghiệp vụ. |
| Canonicalization | Path/query encoding khác, JSON whitespace khác, mediaType khác | Tag không khớp nếu bytes/field đã ký đổi theo hợp đồng. |

### 12.2 Tách mock Clock và replay store

`Clock` được inject vào `Verifier` để kiểm thử không chờ thời gian thật. Mỗi test nên dùng `ReplayCache` mới (nếu không chủ đích thử replay) để trạng thái nonce của test trước không làm sai kết quả test sau. Với integration test, dùng Redis/database thật hoặc môi trường tương đương để kiểm tra atomic claim, TTL và xử lý lỗi mạng; test mỗi pod dùng cache local không chứng minh được tính đúng toàn cụm.

Kiểm thử đồng thời phải quan sát **số claim thành công** và **số lần side-effect nghiệp vụ**; hai số này không đồng nghĩa nếu handler có các bước ngoài transaction.

### 12.3 Bài tập mở rộng

1. **Kiểm tra thay method:** xây `Message("GET",...)` từ dữ liệu gốc và dùng `Proof` của `POST`. Giải thích vì sao tag phải sai.
2. **Tamper proof:** giữ request gốc, đổi `nonce` rồi kiểm. Nếu server claim nonce trước MAC thì có thể phát sinh tác hại gì?
3. **Biên thời gian:** tạo proof cho `now + 300`, `now - 300`, `now + 301` và `now - 301`; định nghĩa expected outcome và chứng minh TTL từ `timestamp + 301` an toàn cho các proof được nhận.
4. **Dịch vụ thứ hai:** tạo verifier audience `reports-api` với một registry riêng; thử sử dụng proof của `payments-api` và mô tả khóa và miền tin cậy.
5. **Redis triển khai thật:** thay `ReplayCache` bằng adapter cho `SET NX EX`, đặt key theo namespace/audience/principal/nonce, viết integration test hai instance và test kho replay không phản hồi.
6. **Retry an toàn:** thêm idempotency key và bản ghi trạng thái giao dịch; mô tả response khi cùng operation được client retry bằng một **nonce mới** sau lỗi mạng.

## 13. Những lỗi dễ mắc khi triển khai

| Sai lầm | Tại sao nguy hiểm | Cách sửa |
| --- | --- | --- |
| Chỉ tính HMAC trên body | Tag hợp lệ có thể được dùng ở method/path/audience khác nếu cùng secret và ngữ cảnh không bị ràng buộc. | MAC cả context, protocol version, timestamp và nonce. |
| Nối chuỗi không độ dài/escaping | Trường hợp trường chứa ký tự phân tách làm canonical bytes mơ hồ. | Length-prefixed binary encoding hoặc canonicalization được đặc tả chặt. |
| Tin `X-Method`/`X-Path` do client tự khai báo | Có thể ký một giá trị nhưng server xử lý giá trị khác. | Dùng context thật từ HTTP runtime/route theo hợp đồng. |
| Parse JSON rồi stringify khác trước khi MAC | Canonical bytes hai bên lệch. | Ký raw bytes hoặc canonical JSON chuẩn và thống nhất. |
| Dùng SHA-256 thuần `digest(secret || body)` | Cơ chế tự chế thiếu bảo đảm HMAC chuẩn. | `Mac.getInstance("HmacSHA256")` với key chất lượng. |
| Nonce từ timestamp hoặc counter dễ đoán/trùng | Nhiều request hay nhiều máy có thể tái sử dụng nonce. | Nonce 128-bit từ `SecureRandom` và một scope replay được định nghĩa. |
| Chỉ kiểm timestamp | Cùng request vẫn replay được trong cửa sổ hợp lệ. | Claim nonce trong shared replay store sau MAC. |
| `GET` rồi `SET` để kiểm nonce | Hai request đồng thời cùng thấy chưa có nonce rồi cùng thành công. | Atomic NX hoặc unique insert. |
| Nonce cache tại từng node | Replay sang node khác vượt kiểm tra. | Shared atomic store trên toàn bộ verifier cùng scope. |
| Claim nonce trước MAC | Request giả có thể chiếm nonce và làm rớt request hợp lệ. | Chỉ claim sau xác minh MAC. |
| TTL đúng 300 giây kể từ lúc nhận | Request có signed timestamp tương lai còn được nhận sau khi nonce đã hết hạn. | TTL đến cuối cửa sổ `signedTimestamp + 301` (hoặc policy chặt hơn). |
| Cho qua khi replay store lỗi | Một phần lỗi hạ tầng làm mất cơ chế chống replay. | Fail closed và có chiến lược sẵn sàng/phục hồi. |
| Key ID/algorithm hoàn toàn do header quyết định | Thay khóa hoặc thuật toán để né chính sách. | Trusted registry; allow-list key/algorithm/version phía server. |
| So tag qua `String.equals` và log secret/tag | Có thể lộ thông tin hoặc tạo đường dò từng byte. | Decode, kiểm độ dài, so bytes bằng `MessageDigest.isEqual`; log tối thiểu. |
| Coi HMAC đúng là được phép thanh toán | Không kiểm quyền, số tiền, tài khoản, số dư. | Authorization và validation sau bước xác thực, trước side-effect. |
| Coi reject replay là đủ để hỗ trợ retry | Response bị mất có thể khiến client không biết kết quả. | Idempotency key/trạng thái giao dịch và cơ chế tra cứu. |

## 14. Checklist triển khai trước khi đưa vào service thật

- [ ] Đã chốt protocol version, thứ tự trường, byte encoding, percent-encoding và cách xác định target/byte body qua proxy.
- [ ] Key ID chỉ tra trong registry tin cậy; secret được cấp, rotate và thu hồi an toàn; không log secret.
- [ ] HMAC-SHA256 với secret có entropy phù hợp; tag fixed-length và so sánh byte thích hợp.
- [ ] Audience, method, target, mediaType, timestamp, nonce và body đều được ràng buộc trong MAC đúng hợp đồng.
- [ ] Timestamp đơn vị UTC epoch seconds, có kiểm cả biên quá cũ/quá tương lai; clock đồng bộ.
- [ ] Nonce 128-bit random, Base64url hợp lệ; replay key không mơ hồ và có phạm vi tin cậy rõ.
- [ ] Claim nonce chỉ sau MAC hợp lệ, có tính nguyên tử trên **mọi instance**; TTL bảo toàn tới cuối cửa sổ; lỗi store thì không thực thi nghiệp vụ.
- [ ] HTTP input có giới hạn kích thước/thời gian, quy tắc parse header trùng và metadata không rõ ràng; body không bị tiêu thụ/biến đổi ngoài hợp đồng.
- [ ] Authorization, validation, rate limit, idempotency và transaction nghiệp vụ được đánh giá riêng.
- [ ] Đã chạy test tamper, replay, hết hạn, race, multi-node và lỗi replay store; đã định nghĩa monitoring/alerting cho tỷ lệ từ chối bất thường.

Khi giải thích được **những byte nào nằm trong HMAC**, **vì sao timestamp đã MAC vẫn chưa đủ chống replay**, **vì sao nonce phải claim nguyên tử sau xác thực**, và **vì sao shared replay store + idempotency/authorization là trách nhiệm riêng**, bạn đã hoàn thành mục tiêu của bài thực hành.

## 15. Tài liệu chính thức và đối chiếu

- Oracle Java 17 Javadoc: [`Mac`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Mac.html), [`SecretKeySpec`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/SecretKeySpec.html).
- Oracle Java 17 Javadoc: [`SecureRandom`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html), [`MessageDigest.isEqual`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/MessageDigest.html#isEqual(byte%5B%5D,byte%5B%5D)), [`Base64`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Base64.html).
- Oracle Java 17 Javadoc: [`Clock`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Clock.html), [`ConcurrentHashMap.compute`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#compute(K,java.util.function.BiFunction)).
- IETF: [RFC 2104 – HMAC: Keyed-Hashing for Message Authentication](https://www.rfc-editor.org/rfc/rfc2104), [RFC 9421 – HTTP Message Signatures](https://www.rfc-editor.org/rfc/rfc9421).
- Redis: [`SET` command (NX và EX)](https://redis.io/docs/latest/commands/set/).

Các tài liệu nêu hành vi API và giao thức tham khảo. Hợp đồng demo ở bài này vẫn cần được hai phía cùng phê duyệt và thử nghiệm khi triển khai thật, đặc biệt ở phần representation HTTP, đồng bộ clock, key identity, replay store phân tán và idempotency nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-practice-003">23.3 Xây định dạng mã hóa file AEAD có phiên bản và xử lý lỗi xác thực</a>

<details>
<summary>Xem chi tiết</summary>

# 23.3 Xây định dạng mã hóa file AEAD có phiên bản và xử lý lỗi xác thực

## 1. Bài toán và kết quả cần đạt

Hãy thiết kế một định dạng file nhị phân có thể **mã hóa và kiểm tra tính xác thực** của nội dung, có **phiên bản rõ ràng**, đủ thông tin để trình giải mã chọn đúng thuật toán và **không công bố plaintext khi xác thực thất bại**. Đây là bài thực hành tích hợp Java Cryptography Architecture/JCE, `Cipher`, `SecureRandom`, xử lý file, đặc tả định dạng và xử lý lỗi.

Một định dạng đáng tin cần trả lời đồng thời các câu hỏi:

1. File có đúng kiểu của ứng dụng không? Phiên bản nào? Bộ thuật toán (*algorithm suite*) nào?
2. IV/nonce được tạo, lưu và kiểm tra thế nào? Làm sao ngăn tái sử dụng nonce dưới cùng khóa?
3. Những byte metadata nào được bảo vệ bằng **AAD** (Additional Authenticated Data)?
4. Ciphertext và authentication tag nằm ở đâu? Làm sao phát hiện byte bị sửa, file bị cắt cụt hoặc khóa sai?
5. Dùng khóa nào để giải mã sau khi tiến trình tạo file đã kết thúc, và ai được cấp quyền dùng khóa?
6. Phần mềm phải làm gì khi tag không hợp lệ, phiên bản không hỗ trợ, file quá lớn hoặc thao tác ghi file thất bại?
7. Làm thế nào để định dạng có thể thêm phiên bản sau mà không phá tương thích hoặc tự động hạ cấp bảo mật?

**AEAD (Authenticated Encryption with Associated Data)** bảo vệ **tính bí mật và tính toàn vẹn/xác thực mật mã** cho plaintext, đồng thời cho phép xác thực metadata được gửi công khai dưới dạng AAD. Trong bài, sử dụng `AES/GCM/NoPadding` với khóa AES-256, nonce 12 byte và tag 16 byte (128 bit). **Tag không chứng minh người dùng được cấp quyền, không chống replay/rollback và không làm cho khóa yếu trở nên mạnh.** Những thuộc tính đó cần cơ chế riêng.

### 1.1. Vì sao không chỉ gọi AES rồi lưu ciphertext?

Mã hóa không xác thực có thể khiến byte bị thay đổi không bị phát hiện đáng tin cậy, và việc ghép riêng các giải thuật rất dễ sai. GCM kết hợp mã hóa và kiểm tra tính toàn vẹn trong một cơ chế AEAD, nhưng **chỉ an toàn khi tuân thủ chính sách nonce/key và xác thực tag trước khi dùng plaintext**.

Các thuật ngữ:

- **Plaintext:** nội dung file gốc.
- **Ciphertext:** dữ liệu đã mã hóa (với GCM, có độ dài bằng plaintext trong trường hợp không padding).
- **Nonce/IV:** giá trị đầu vào đặc biệt cho từng lần mã hóa. Với GCM, **không được tái sử dụng cùng cặp key + nonce**.
- **Authentication tag:** 16 byte cuối trong định dạng v1; kết quả xác thực nội dung mã hóa và AAD.
- **AAD:** bytes công khai được bảo vệ tính toàn vẹn nhưng **không mã hóa**; ở đây là toàn bộ header.
- **Key:** bí mật 256 bit được quản lý **ngoài file**. File lưu nonce, không lưu raw key.

## 2. Đặc tả định dạng file v1

Đặt tên minh họa cho định dạng: **JAED**, magic ASCII gồm bốn byte `J A E D`. Đây là định dạng học tập do bài này tự thiết kế, **không phải tiêu chuẩn/định dạng mã hóa phổ biến** và không được đưa nguyên dạng vào sản phẩm mà chưa có threat model, review, giới hạn sử dụng và chiến lược quản lý khóa.

### 2.1. Bố cục nhị phân chính xác

```text
+------------------- Header = 18 bytes ---------------------+
| magic "JAED" | version 01 | suite 01 | nonce 12 bytes       |
|   4 bytes    |   1 byte   |  1 byte  |    12 bytes          |
+------------------------------------------------------------+
| Ciphertext (N bytes, N = plaintext length, 0 <= N <= 1 MiB)|
+------------------------------------------------------------+
| GCM authentication tag (16 bytes)                         |
+------------------------------------------------------------+
```

| Offset (tính từ 0) | Độ dài | Field | Quy tắc |
| --- | ---: | --- | --- |
| 0–3 | 4 byte | `magic` | ASCII `JAED`; sai magic → định dạng không hợp lệ. |
| 4 | 1 byte | `version` | Giá trị `0x01`; phiên bản khác → không tự giải mã theo v1. |
| 5 | 1 byte | `suiteId` | Giá trị `0x01` = AES-256-GCM, nonce 96 bit, tag 128 bit. |
| 6–17 | 12 byte | `nonce` | Sinh ngẫu nhiên bằng `SecureRandom` cho **mỗi file mã hóa**. |
| 18 đến 18+N−1 | N byte | `ciphertext` | `Cipher.doFinal(plaintext)` trả ciphertext nối tag; N có thể là 0. |
| 18+N đến 33+N | 16 byte | `tag` | Xác thực ciphertext và AAD header. |

Vậy:

- `HEADER_SIZE = 4 + 1 + 1 + 12 = 18` byte.
- `TAG_SIZE = 16` byte.
- `fileSize = 18 + plaintextLength + 16`; file rỗng hợp lệ dài **34 byte**.
- Bài demo giới hạn plaintext **1 MiB**; file JAED hợp lệ lớn nhất là `1 MiB + 34` byte.
- Toàn bộ 18 byte header được đưa vào `Cipher.updateAAD(header)`. Header công khai nhưng nằm trong kiểm tra tag. Nonce cũng là tham số GCM; ghi nonce ngay trong header giúp trình giải mã tái tạo đúng tham số.

**Không** Base64 plaintext/ciphertext khi ghi file nhị phân; ghi **đúng bytes**. Một định dạng có text metadata/JSON có thể dùng AAD, nhưng phải quy định **canonical byte encoding**; hai bên AAD khác một khoảng trắng cũng làm xác thực thất bại. Header v1 chọn kích thước cố định để tránh sự nhập nhằng đó.

### 2.2. Những gì v1 cố ý không chứa

File v1 **không chứa khóa bí mật, mật khẩu, salt, key ID, độ dài file gốc tường minh, đường dẫn hoặc tên file**. Vì vậy, hàm giải mã cần nhận `SecretKey` đã được cung cấp một cách tin cậy từ ngoài.

Ưu điểm: header ngắn, các offset cố định, ít lỗi phân tích cú pháp. Hạn chế: không tự biết tìm key nào khi có nhiều khóa; không hỗ trợ file lớn theo kiểu streaming; không có key rotation hoặc chống replay. Những vấn đề này được giải quyết ở tầng ứng dụng hoặc định nghĩa thành phiên bản mới có kiểm chứng.

## 3. Các bất biến bảo mật bắt buộc

### 3.1. Key và nonce

- Dùng `KeyGenerator.getInstance("AES")` và `init(256)` để sinh khóa AES-256 cho demo. Java 17+ với nhà cung cấp phù hợp hỗ trợ AES/GCM; nếu môi trường không hỗ trợ khóa/cấu hình đó thì **báo lỗi**, không âm thầm chuyển sang thuật toán yếu hơn.
- Mỗi lần mã hóa tạo nonce mới dài **12 byte** bằng `SecureRandom.nextBytes`. **Không** tái sử dụng key + nonce, kể cả mã hóa lại cùng file. Không lấy nonce từ đồng hồ, filename, hash plaintext hoặc counter thiếu cơ chế đồng bộ/bền vững.
- Nonce **không cần bí mật** và được lưu cạnh ciphertext. Một nonce 96 bit ngẫu nhiên có xác suất va chạm thấp ở quy mô phù hợp, nhưng đây là xác suất, không phải bảo đảm tuyệt đối. Nếu mã hóa số lượng file rất lớn dưới cùng key, phải đánh giá birthday bound, giới hạn số lần dùng, xoay key hoặc dùng hệ thống cấp nonce duy nhất.
- Không sinh khóa mới trong quá trình giải mã. Khóa phải được lấy đúng từ key store/HSM/KMS hoặc nguồn được xác thực. **Nếu mất key thì file không thể khôi phục chỉ từ nonce và tag**.
- Không ghi key/nonce cùng cặp cố định vào mã nguồn hay test fixtures được đưa vào production. Khi tái sử dụng cùng key, nguy cơ chính là **nonce collision**, không phải việc nonce công khai.

### 3.2. AAD và thứ tự gọi Cipher

Khi mã hóa:

```java
// Snippet API: key và nonce được cung cấp đúng kiểu, từ nguồn an toàn.
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
cipher.updateAAD(header);              // trước bất kỳ update/doFinal nào
byte[] ciphertextAndTag = cipher.doFinal(plaintext);
```

Đây chỉ là **snippet**; chương trình hoàn chỉnh ở mục 5. Quy tắc đối xứng khi giải mã: nhận và kiểm tra header, khởi tạo `Cipher.DECRYPT_MODE` với **nonce đọc từ file**, gọi `updateAAD(header)` đúng **các bytes nhận được**, rồi `doFinal(ciphertextAndTag)`.

Nếu thay một byte header mà vẫn đi qua bước parse v1, tag sẽ không hợp lệ. Nếu byte magic/version/suite bị thay thành giá trị không hỗ trợ, parser có thể từ chối **trước khi** kiểm tra tag. Cả hai đều là **thất bại không sinh plaintext**; chỉ cần phân biệt chúng trong diagnostic nội bộ, không phơi lỗi chi tiết cho đối tượng không đáng tin.

Không gọi `cipher.updateAAD(...)` **sau** khi đã bắt đầu xử lý ciphertext bằng `update`/`doFinal`. Không tự lấy tag khỏi `Cipher.doFinal` rồi quên ghi nó; với GCM trong JCE, kết quả mã hóa của `doFinal` chứa **ciphertext nối tag**.

### 3.3. Không sử dụng plaintext trước khi xác thực xong

Điều kiện xuất file đã giải mã: `Cipher.doFinal` **hoàn tất thành công và xác thực tag hợp lệ**. Khi phát sinh `AEADBadTagException`, coi dữ liệu là không hợp lệ, dừng xử lý, không xuất plaintext, không mở hoặc truyền nội dung cho nghiệp vụ.

Đặc biệt, với xử lý nhiều phần `Cipher.update` trong các triển khai AEAD, không nên **đưa những bytes có thể được trả về trước khi tag được kiểm chứng** ra file đích hoặc gọi tác vụ nghiệp vụ dựa trên chúng. Demo sử dụng **giải mã một lần bằng `doFinal`**, giữ plaintext trong bộ nhớ cho đến khi xác thực thành công; sau đó ghi file tạm và xuất bản theo thao tác move.

Không phân biệt rõ ra ngoài “khóa sai”, “ciphertext đã sửa” và “tag hỏng”: các tình huống này thường chỉ được phát hiện chung là **authentication failed**, điều phù hợp với bảo mật và tránh thông báo chi tiết cho bên gửi không tin cậy.

## 4. Quy trình mã hóa, giải mã và báo lỗi

### 4.1. Mã hóa v1

1. Kiểm tra đầu vào, quyền đọc và kích cỡ plaintext (giới hạn 1 MiB trong demo).
2. Nhận khóa AES-256 từ cơ chế quản lý khóa bên ngoài.
3. Sinh 12 byte nonce mới từ `SecureRandom`.
4. Tạo header cố định: `JAED | 0x01 | 0x01 | nonce`.
5. Tạo `Cipher AES/GCM/NoPadding`, init `ENCRYPT_MODE` với khóa và nonce, rồi `updateAAD(header)`.
6. Gọi `doFinal(plaintext)` để nhận ciphertext + 16-byte tag.
7. Ghép `header || ciphertext || tag`; ghi file tạm **trong cùng thư mục** với file đích.
8. Sau khi ghi hoàn chỉnh, move file tạm sang tên đích. Demo dùng `ATOMIC_MOVE`; nếu filesystem không hỗ trợ thì báo lỗi thay vì giả định đã bảo đảm tính nguyên tử.

### 4.2. Giải mã v1

1. Đọc file với giới hạn byte độc lập trước khi cấp phát bộ nhớ lớn; từ chối file quá ngưỡng.
2. Kiểm tra có tối thiểu 34 byte và nhận biết magic/version/suite. Không tự đoán layout khi gặp phiên bản lạ.
3. Lấy **chính xác** 18 byte header từ file; tách nonce từ offset 6–17.
4. Nhận **đúng key** từ quản lý khóa. Nếu thiếu key, từ chối; không phát sinh key “thay thế”.
5. Init `Cipher.DECRYPT_MODE`, gọi `updateAAD(header)`, rồi `doFinal` trên phần `ciphertext || tag`.
6. Nếu `doFinal` phát sinh `AEADBadTagException`, **không ghi plaintext đích**, không fallback sang CBC/CTR và không thử bỏ qua tag.
7. Chỉ khi xác thực thành công mới ghi plaintext ra file tạm và chuyển sang đích. Xử lý IOException khi ghi/đổi tên, dọn file tạm.

**File bị cắt bớt hoặc nối thêm byte** thường sẽ thất bại ở kiểm tra kích thước/định dạng hoặc xác thực tag. Không tiếp nhận một tiền tố plaintext khi file chưa được kiểm tra hết. File được thay thế bằng một file cũ nhưng có tag hợp lệ **vẫn giải mã được**: GCM không tự cung cấp **chống replay/rollback**.

### 4.3. Sơ đồ luồng có điểm quyết định

```text
ENCRYPT
  source bytes -> size check -> fresh nonce
    -> v1 header -> GCM encrypt(AAD=header)
    -> header || ciphertext || tag
    -> temporary file -> atomic publication

DECRYPT
  JAED bytes -> size + magic/version/suite check -> key lookup
    -> GCM decrypt(AAD=raw header)
            |
       +----+----------------------+
       |                           |
    doFinal OK                  AEADBadTagException
       |                           |
  plaintext verified           reject & discard
       |                           |
  staged output                NO plaintext output
       |
  publish only on success
```

## 5. Chương trình Java 17+ có thể biên dịch và chạy

Bản demo độc lập chạy hoàn toàn với thư viện JDK, sử dụng **khóa ngẫu nhiên tạm thời trong cùng tiến trình**, file trong thư mục tạm mới, ciphertext và tag được xác thực trước khi xuất plaintext. Nó còn cố tình sửa ciphertext và thử giải mã bằng **khóa sai** để kiểm tra hành vi fail-closed.

**Ràng buộc rõ ràng:** khóa demo bị mất khi chương trình kết thúc; đây là kiểm thử round-trip trong một JVM, **chưa phải công cụ lưu trữ khóa lâu dài**. Demo dùng tối đa 1 MiB để không cần xây thiết kế streaming ở bước này. Nó không dùng password hoặc hardcode key.

Lưu thành **`VersionedAeadFileDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class VersionedAeadFileDemo {
    private static final byte[] MAGIC = {'J', 'A', 'E', 'D'};
    private static final int VERSION = 1;
    private static final int AES256_GCM = 1;
    private static final int NONCE_SIZE = 12;
    private static final int TAG_SIZE = 16;
    private static final int HEADER_SIZE = 4 + 1 + 1 + NONCE_SIZE;
    private static final int MAX_PLAIN = 1024 * 1024;
    private static final int MAX_ENVELOPE = HEADER_SIZE + MAX_PLAIN + TAG_SIZE;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] readLimited(Path path, int max) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(max + 1);
            if (bytes.length > max) {
                throw new IOException("File exceeds size limit");
            }
            return bytes;
        }
    }

    private static byte[] makeHeader(byte[] nonce) {
        byte[] header = new byte[HEADER_SIZE];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        header[4] = (byte) VERSION;
        header[5] = (byte) AES256_GCM;
        System.arraycopy(nonce, 0, header, 6, NONCE_SIZE);
        return header;
    }

    private static void checkHeader(byte[] file) throws IOException {
        if (file.length < HEADER_SIZE + TAG_SIZE) {
            throw new IOException("Truncated envelope");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (file[i] != MAGIC[i]) {
                throw new IOException("Unknown file format");
            }
        }
        if (Byte.toUnsignedInt(file[4]) != VERSION) {
            throw new IOException("Unsupported version");
        }
        if (Byte.toUnsignedInt(file[5]) != AES256_GCM) {
            throw new IOException("Unsupported algorithm suite");
        }
    }

    private static void writeNewAtomically(Path output, byte[] contents)
            throws IOException {
        Path target = output.toAbsolutePath();
        if (Files.exists(target)) {
            throw new IOException("Destination already exists");
        }
        Path temporary = Files.createTempFile(target.getParent(), ".jaed-", ".part");
        try {
            Files.write(temporary, contents);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void encrypt(Path input, Path output, SecretKey key) throws Exception {
        byte[] plain = readLimited(input, MAX_PLAIN);
        try {
            byte[] nonce = new byte[NONCE_SIZE];
            RANDOM.nextBytes(nonce);
            byte[] header = makeHeader(nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key,
                    new GCMParameterSpec(TAG_SIZE * 8, nonce));
            cipher.updateAAD(header);
            byte[] ciphertextAndTag = cipher.doFinal(plain);
            byte[] envelope = new byte[header.length + ciphertextAndTag.length];
            System.arraycopy(header, 0, envelope, 0, header.length);
            System.arraycopy(ciphertextAndTag, 0, envelope,
                    header.length, ciphertextAndTag.length);
            writeNewAtomically(output, envelope);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    static void decrypt(Path input, Path output, SecretKey key) throws Exception {
        byte[] envelope = readLimited(input, MAX_ENVELOPE);
        checkHeader(envelope);
        byte[] header = Arrays.copyOfRange(envelope, 0, HEADER_SIZE);
        byte[] nonce = Arrays.copyOfRange(header, 6, HEADER_SIZE);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_SIZE * 8, nonce));
        cipher.updateAAD(header);
        byte[] verifiedPlaintext = cipher.doFinal(
                envelope, HEADER_SIZE, envelope.length - HEADER_SIZE);
        try {
            writeNewAtomically(output, verifiedPlaintext);
        } finally {
            Arrays.fill(verifiedPlaintext, (byte) 0);
        }
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();

        Path dir = Files.createTempDirectory("jaed-demo-");
        Path input = dir.resolve("sample.txt");
        Path encrypted = dir.resolve("sample.jaed");
        Path recovered = dir.resolve("recovered.txt");
        Path damaged = dir.resolve("tampered.jaed");
        Path rejectedOutput = dir.resolve("rejected.txt");
        Path wrongKeyOutput = dir.resolve("wrong-key.txt");
        try {
            Files.writeString(input, "Xin chao AEAD!\n", StandardCharsets.UTF_8);
            encrypt(input, encrypted, key);
            decrypt(encrypted, recovered, key);
            System.out.println("Round-trip: " +
                    Arrays.equals(Files.readAllBytes(input),
                                  Files.readAllBytes(recovered)));

            byte[] altered = Files.readAllBytes(encrypted);
            altered[HEADER_SIZE] ^= 1;
            Files.write(damaged, altered);
            try {
                decrypt(damaged, rejectedOutput, key);
                System.out.println("Tampered ciphertext: accepted");
            } catch (AEADBadTagException expected) {
                System.out.println("Tampered ciphertext: rejected");
            }
            System.out.println("Plaintext published on failure: " +
                    Files.exists(rejectedOutput));

            SecretKey otherKey = generator.generateKey();
            try {
                decrypt(encrypted, wrongKeyOutput, otherKey);
                System.out.println("Wrong key: accepted");
            } catch (AEADBadTagException expected) {
                System.out.println("Wrong key: rejected");
            }
        } finally {
            for (Path p : new Path[] {input, encrypted, recovered, damaged,
                                      rejectedOutput, wrongKeyOutput}) {
                Files.deleteIfExists(p);
            }
            Files.deleteIfExists(dir);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 VersionedAeadFileDemo.java
java VersionedAeadFileDemo
```

Stdout đã kiểm chứng với mã trên (biên dịch `--release 17`, chạy bằng JDK 24):

```text
Round-trip: true
Tampered ciphertext: rejected
Plaintext published on failure: false
Wrong key: rejected
```

**Kết quả không phụ thuộc nội dung ciphertext ngẫu nhiên**: mỗi lượt chạy tạo key và nonce khác nhau; bài chỉ so sánh plaintext và quyết định xác thực, không ghi cố định giá trị nonce/tag. `Round-trip: true` chứng minh đọc lại đúng bytes; `Tampered ciphertext: rejected` cho thấy việc đổi một byte ciphertext làm `doFinal` ném `AEADBadTagException`; `Plaintext published on failure: false` kiểm tra output không tồn tại sau thất bại. Sai key cũng bị từ chối cùng loại lỗi.

### 5.1. Vì sao code giới hạn kích thước trước khi đọc?

`readLimited(input, max)` chỉ đọc nhiều nhất `max + 1` byte. Nếu có byte vượt ngưỡng, báo lỗi sớm thay vì `Files.readAllBytes` vào bộ nhớ không có trần. Demo giữ file nguyên trong RAM để có thể gọi GCM `doFinal` một lần. Lớp thực tế còn cần giới hạn request/body của giao thức, lượng dữ liệu nén, memory budget và concurrency.

Đối với file mã hóa, parser từ chối file ít hơn 34 byte; ciphertext phần còn lại có thể dài 0 cho file plaintext rỗng. Byte header được **copy nguyên trạng** và đưa vào AAD, nên không có sai lệch giữa header mà file mang theo và AAD dùng xác thực.

### 5.2. Vì sao dùng file tạm và `ATOMIC_MOVE`?

File đã giải mã chỉ được ghi **sau** khi xác thực tag thành công. Trong demo, file tạm được tạo ở cùng thư mục đích và `Files.move(..., ATOMIC_MOVE)` xuất bản kết quả. Nếu filesystem không hỗ trợ move nguyên tử thì thao tác báo lỗi; không được tự coi bản sao/move thường là nguyên tử.

Phần kiểm tra `Files.exists(target)` giúp demo tránh ghi đè **trong trường hợp thông thường**; nó **không phải biện pháp chống race có tính bảo đảm** khi tác nhân khác đồng thời tạo target. Đặc tả Java cũng cho phép hành vi phụ thuộc implementation khi `ATOMIC_MOVE` gặp tên đích đã tồn tại. Ứng dụng thật cần chính sách no-clobber/overwrite riêng, đường dẫn/thư mục tin cậy, kiểm soát symlink, quyền file và cả cơ chế durability (ví dụ fsync file/parent directory tùy filesystem) nếu cần chống sự cố mất điện.

Demo xóa file tạm ở `finally`, không đưa plaintext lỗi ra tên file đích. Với hệ thống cần không lưu plaintext ra ổ đĩa, hãy giới hạn toàn bộ quy trình trong vùng lưu trữ đã được bảo vệ, cân nhắc disk encryption và chính sách xóa/retention phù hợp.

### 5.3. Địa chỉ thật của key ở đâu?

Trong demo `key` nằm trong JVM và được sử dụng ngay cho cả `encrypt`/`decrypt`. Sau khi chương trình kết thúc, **key không được lưu** nên những file demo không thể giải mã lại nếu giữ file .jaed. Đây là lựa chọn có chủ đích để tránh lồng thêm ví dụ lưu key nguy hiểm như ghi raw AES key cạnh ciphertext.

Đối với ứng dụng thực, `SecretKey` thường đến từ HSM, KMS, keystore được bảo vệ hoặc key wrapping bằng một KEK (key encryption key). Không nên in Base64 raw key ra log, đặt key trong file nguồn, truyền key qua command-line arguments hoặc dùng cùng một key duy nhất cho mọi tenant/mọi môi trường. Thiết kế key ID và key rotation ở mục 8.

## 6. Kiểm thử tampering, truncation và malformed format

### 6.1. Ma trận kiểm thử bắt buộc

| Đầu vào hoặc thay đổi | Kết quả mong đợi | Vì sao |
| --- | --- | --- |
| File plaintext hợp lệ | Giải mã đúng từng byte | Key, nonce, AAD và tag khớp. |
| Plaintext rỗng | File JAED dài 34 byte và giải mã ra 0 byte | Vẫn cần GCM tag. |
| Đổi một byte ciphertext | `AEADBadTagException`, không xuất plaintext | Ciphertext mất xác thực. |
| Đổi một byte tag | `AEADBadTagException` | Tag không khớp tính toán. |
| Dùng sai key | `AEADBadTagException` (đối với dữ liệu đúng cấu trúc) | Không xác thực được bằng key khác. |
| Đổi byte nonce mà vẫn giữ header hợp lệ | `AEADBadTagException` | Nonce thay khiến việc kiểm tra GCM thất bại. |
| Đổi magic `JAED` | Lỗi định dạng trước khi giải mã | Không nhận diện được container. |
| Đổi `version=1` thành 2 | Lỗi phiên bản không hỗ trợ | Không đoán layout v2. |
| Đổi `suiteId=1` sang giá trị lạ | Lỗi thuật toán không hỗ trợ | Không fallback sang thuật toán khác. |
| Cắt bớt ciphertext/tag | Từ chối do thiếu kích thước hoặc tag sai | Không có tiền tố plaintext được chấp nhận. |
| Nối thêm byte vào cuối file | Tag không còn đúng trên toàn bộ dữ liệu hoặc vượt ngưỡng | Không âm thầm bỏ qua trailing bytes. |
| File lớn hơn limit | `IOException` do giới hạn byte | Bảo vệ nguồn lực. |
| File đích đã tồn tại | Demo báo lỗi thay vì thay thế | Ngăn ghi đè ngoài ý muốn trong điều kiện thông thường. |
| Ổ đĩa/rename lỗi | Không xác nhận ghi thành công; dọn file tạm | Ghi file là một bước độc lập với xác thực mật mã. |

Nên thêm test tạo nhiều file từ **cùng key** và kiểm tra nonce khác nhau; test file ở ngưỡng 1 MiB và 1 MiB + 1 byte; test một byte sửa trong **mỗi field**, bao gồm header và tag; test công bố file sau lỗi IO. Khi có key rotation, test giải mã file cũ với key cũ qua key ID.

### 6.2. Tách lỗi nội bộ và phản hồi ra ngoài

Khi triển khai API/service, nên phân loại nội bộ:

| Loại lỗi | Tín hiệu thường thấy | Xử lý |
| --- | --- | --- |
| Malformed/unknown format | Magic sai, file quá ngắn, header bị rút ngắn | Từ chối ngay; không đoán định dạng. |
| Unsupported format | Version/suite chưa biết | Từ chối, có thể hướng dẫn nâng cấp phần mềm qua kênh tin cậy. |
| Authentication failed | `AEADBadTagException` khi `doFinal` | Xóa trạng thái tạm; không xuất plaintext. |
| Key unavailable | KMS/keystore không trả đúng key | Không tạo key khác để thử; báo lỗi cấu hình/quyền. |
| Resource limit | File quá lớn, ngưỡng memory/concurrency | Từ chối trước khi cấp phát quá lớn. |
| IO/storage error | Không đọc/ghi/rename được | Không coi là mã hóa/giải mã thành công. |

Đối với phía client không tin cậy, có thể trả thông báo chung **“Không thể mở file mã hóa”** thay vì nói chi tiết “key đúng nhưng tag sai” hoặc gợi ý trạng thái key. Log nội bộ giữ mã lỗi và correlation ID để vận hành, **không in key, plaintext, ciphertext đầy đủ hay dữ liệu nhạy cảm**. Không nuốt `AEADBadTagException` rồi tiếp tục với plaintext chưa được xác minh.

### 6.3. Tampering khác corruption và khác replay

AEAD phát hiện **bất kỳ thay đổi chưa được xác thực** đối với những byte bảo vệ, trong giới hạn an toàn của thuật toán và key/nonce. Nhưng nó không cho biết file bị thay do lỗi lưu trữ hay tác nhân tấn công, và không cung cấp dấu thời gian tin cậy. Nếu ai đó thay file hiện tại bằng **một file JAED cũ, nguyên vẹn, dùng key hợp lệ**, tag vẫn có thể đúng. Muốn chống rollback/replay cần thêm phiên bản tài liệu, số thứ tự tăng đơn điệu, hash/version được lưu trong kho tin cậy, hoặc policy giao dịch bên ngoài; đồng thời ràng buộc định danh file/tenant vào AAD nếu use case cần.

## 7. Quản lý metadata và phạm vi xác thực

Header v1 được bảo vệ bằng AAD, nhưng không chứa đầy đủ mọi ngữ cảnh có thể quan trọng với hệ thống. Ví dụ: file được dùng cho tenant A và file cho tenant B có thể dùng key khác nhau; nếu cùng key và API cho phép hoán đổi file có tag hợp lệ, GCM **không biết** file “đáng lẽ thuộc tenant A” nếu tenant ID không nằm trong AAD hoặc metadata được xác thực nơi khác.

Một hệ thống ứng dụng có thể bổ sung **AAD theo ngữ cảnh** như `applicationId`, `tenantId`, `fileId`, `purpose` và `schemaVersion`, nhưng phải quy định serialization nhất quán và lấy giá trị từ **nguồn tin cậy** lúc giải mã. Khi mở rộng định dạng, có thể xác định:

```text
AAD = exactHeaderBytes || canonicalContextBytes
```

Bản demo cố ý chỉ dùng `AAD = exactHeaderBytes`. Hai bên tự thêm “filename hiện tại” vào AAD mà không thống nhất định dạng sẽ không giải mã được khi đổi tên/di chuyển file. Việc mã hóa tên file/metadata nhạy cảm phải được thiết kế riêng: AAD **công khai** và chỉ có tính toàn vẹn, không cung cấp bí mật cho tên file.

## 8. Versioning, key rotation và mở rộng v2

### 8.1. Version không phải một chú thích tùy ý

Định nghĩa **version 1** cố định về cấu trúc, offset, thuật toán và độ dài nonce/tag; sau khi có file v1 được phát hành, **không đổi âm thầm ý nghĩa của `suiteId=1`**. Nếu cần thay đổi layout (chẳng hạn thêm key ID, salt, chunk records), tạo v2 và parser riêng.

```text
magic 4 bytes
    |
    +-- version 1 -> parse JAED v1 -> suite 1 AES-256-GCM
    |
    +-- version 2 -> parse JAED v2 -> suite v2 đã định nghĩa rõ
    |
    +-- unknown   -> REJECT (không đoán, không downgrade)
```

Header đã được xác thực AAD nhưng parser cần đọc version để biết nên áp dụng thuật toán nào. Vì vậy **không tin version để hạ chuẩn bảo mật**, chỉ dùng nó chọn từ các phiên bản đã cho phép trong chính sách cục bộ, rồi xác thực tag đầy đủ trước khi trả plaintext. File có version không biết phải bị từ chối, không “thử AES-CBC để xem có đọc được không”.

### 8.2. Bổ sung `keyId` mà không lộ key

Khi hệ thống có nhiều khóa, v2 có thể thêm **key ID** trong header, ví dụ một UUID hoặc định danh opaque có độ dài giới hạn. Key ID chỉ giúp service tra cứu khóa, **không phải bí mật** và không có nghĩa khóa đã được cấp quyền. Cần:

- Giới hạn độ dài và encoding key ID, tránh parser cấp phát không giới hạn.
- Đưa key ID vào AAD cùng header để chống sửa đổi tùy tiện.
- Lookup khóa qua cơ chế quản lý khóa tin cậy, kiểm tra tenant/purpose/permission trước khi sử dụng.
- Khi rotate, file mới dùng key mới, file cũ có thể giải mã bằng key cũ trong thời gian retention chính sách cho phép; nếu key cũ bị thu hồi, giải mã phải fail closed.
- Không gửi raw AES key trong header. Nếu dùng *wrapped data encryption key*, định nghĩa thuật toán wrap, key ID/KEK và cách xác thực envelope một cách chặt chẽ.

**Không yêu cầu key ID bắt buộc cho demo v1**, vì sample chỉ sử dụng một key tạm và nhấn mạnh nguyên lý AEAD trước.

### 8.3. Nếu người dùng chỉ có password

Password không phải key AES-256 ngẫu nhiên. Cần dùng **KDF có salt ngẫu nhiên, cost được hiệu chỉnh và tham số được version hóa**, ví dụ Argon2id qua thư viện được kiểm tra hoặc PBKDF2-HMAC-SHA256 trong JDK khi phù hợp chính sách. Salt được lưu công khai; key được suy ra ở cả hai phía. Không dùng `password.getBytes()`, `String.hashCode()` hoặc `SHA-256(password)` trực tiếp làm key.

Lưu ý: file mã hóa bằng password thường có thể bị **thử đoán password offline** nếu người tấn công có bản sao file và tag; KDF chỉ tăng chi phí đoán, không biến password yếu thành bí mật entropy cao. Cần tránh truyền password qua command-line arguments/log và xử lý vòng đời bí mật trong bộ nhớ.

## 9. File lớn: khi nào phải thiết kế AEAD theo từng chunk?

Demo v1 giải mã toàn bộ file trong bộ nhớ, đặt trần 1 MiB. Đây là thiết kế thích hợp cho ví dụ nhỏ vì **không cần giải phóng plaintext trước khi tag cuối được kiểm chứng**. Với file vài GiB, không nên xóa giới hạn rồi gọi `Files.readAllBytes`. Cần một định dạng **mới**, có record/chunk, nonce riêng và xác thực thứ tự/toàn vẹn toàn file.

### 9.1. Phác thảo định dạng v2 dạng record

Đây là **thiết kế khái niệm, không phải code runnable**:

```text
v2 authenticated header:
    magic | version=2 | suite | keyId | fileNoncePrefix | limits

record 0:
    index=0 | flags | plaintextLength | ciphertext_0 | tag_0
record 1:
    index=1 | flags | plaintextLength | ciphertext_1 | tag_1
...
final record:
    index=N | FINAL flag | length=0 | tag_final
```

Mỗi record phải có IV **duy nhất** dưới cùng key, ví dụ thiết kế rõ 96-bit nonce từ *file-specific prefix* và 32-bit record counter. Phải bảo đảm **prefix không lặp giữa file dùng cùng key** hoặc dùng subkey riêng cho file; counter không wrap và tổng số record nằm trong giới hạn cho phép. Không chỉ gọi `new SecureRandom` rồi cho cùng một nonce vào mọi chunk.

AAD của từng record cần ràng buộc ít nhất **header v2, file identity, record index, flags, record length và các metadata liên quan**. Record cuối phải có marker/tag được xác thực và parser phải yêu cầu **đúng một final record tại vị trí hợp lệ, không có byte thừa**. Nếu không, kẻ tấn công có thể cắt mất những record cuối, thay thứ tự, lặp, ghép các record khác file hoặc chấp nhận một tiền tố file như file hoàn chỉnh.

### 9.2. Chỉ xuất bản file khi tất cả record hợp lệ

Dù mỗi chunk có tag riêng, chỉ xác thực chunk 0 **không chứng minh cả file hoàn chỉnh**. Có thể giải mã các record vào một file staging có quyền hạn chặt chẽ, nhưng **không được công bố/tiêu thụ file đó trước khi xác thực record cuối và toàn bộ cấu trúc**. Nếu phát hiện lỗi ở chunk N, xóa staging file; nếu cần bảo đảm mạnh hơn, dùng storage có giao dịch/quyền truy cập giới hạn để tránh phần mềm khác nhìn thấy dữ liệu đang dở.

Đặc tả streaming phải giải quyết riêng: độ dài record, giới hạn tổng byte, cách xác định final record, sequence/counter, duy nhất nonce, quá trình retry/resume, concurrent writer và cơ chế chống rollback. Đừng tự “stream GCM v1” bằng cách giải mã rồi xuất ngay `Cipher.update` trước khi tag cuối hoàn tất.

## 10. Những lỗi triển khai dễ gặp nhất

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Dùng lại nonce với cùng AES-GCM key | Phá vỡ giả định bảo mật của GCM, có thể dẫn tới lộ thông tin/giả mạo | Sinh nonce mới duy nhất theo chính sách, hạn mức và rotation. |
| Không gọi `updateAAD(header)` ở cả hai phía | Metadata không được ràng buộc bởi tag theo thiết kế | Xác thực **đúng bytes header** trước `doFinal`. |
| Lưu key bên cạnh ciphertext | Mất ý nghĩa bảo mật nếu cả file và key cùng bị lấy | KMS/HSM/keystore, key wrapping và phân quyền tách biệt. |
| Bỏ qua `AEADBadTagException` | Chấp nhận file giả hoặc hỏng | Từ chối, hủy trạng thái và không xuất plaintext. |
| Dùng plaintext từ `update` trước khi xác thực tag | Có thể làm lộ hoặc tiêu thụ dữ liệu chưa xác thực | Buffer/staging, xác thực toàn file rồi publish. |
| Đọc tất cả bytes vào RAM mà không giới hạn | Memory exhaustion/DoS | Bounded input, định dạng chunk có cấu trúc và hạn mức. |
| Tự đoán chiều dài IV/tag/metadata | Parser sai offset, có thể downgrade hoặc sai AAD | Fixed layout per version, reject unknown. |
| Dùng `Cipher.getInstance("AES")` | Phụ thuộc default mode/padding của provider, không có AEAD được đảm bảo | Nêu cụ thể `AES/GCM/NoPadding`. |
| Nhầm encryption với authentication/authorization hệ thống | Một file có tag hợp lệ có thể thuộc người dùng khác hoặc là bản cũ | Xác thực nguồn/tenant, AAD context, chống replay nếu cần. |
| Ghi đè file đích ngay khi decrypt bắt đầu | Có thể để lại plaintext lỗi hoặc làm mất file cũ | Hoàn tất tag, staging file, publish có chính sách. |
| Coi `ATOMIC_MOVE` luôn hỗ trợ và luôn no-clobber | Hành vi phụ thuộc filesystem; có thể lỗi hoặc xử lý target tồn tại khác nhau | Kiểm tra lỗi `AtomicMoveNotSupportedException`, thiết kế chính sách overwrite/race riêng. |
| “Nâng v2” bằng cách diễn giải lại dữ liệu v1 | Các phiên bản không thể dự đoán/tương thích, có nguy cơ downgrade | Version map rõ ràng, AAD header, reject unknown. |

## 11. Checklist kiểm thử trước khi dùng ngoài bài lab

- [ ] Có đặc tả byte offset, encoding, version/suite và kích thước tối đa; parser từ chối mọi cấu trúc không xác định.
- [ ] Bộ thuật toán nằm trong allowlist nội bộ; không tự downgrade sang chế độ không xác thực.
- [ ] Quản lý key qua nguồn tin cậy, có quyền truy cập, key ID/rotation/backup nếu use case cần.
- [ ] Nonce 96 bit mới cho mỗi lần mã hóa dưới một key; hạn mức và chiến lược tránh trùng rõ ràng.
- [ ] AAD bao gồm mọi metadata cần bảo vệ; decrypt sử dụng đúng bytes tương ứng.
- [ ] Tag GCM 128 bit và ciphertext còn nguyên; mọi thay đổi không được xác thực đều dẫn tới từ chối.
- [ ] Không ghi plaintext đích trước khi tag hợp lệ; thất bại làm sạch file tạm và trạng thái nhạy cảm khả thi.
- [ ] Giới hạn kích thước, memory, CPU, số kết nối và thời gian cả ở tầng nhận dữ liệu lẫn parser.
- [ ] Bắt lỗi version, magic, truncated, sai tag/khóa và lỗi IO; thông báo ngoài không rò thông tin nhạy cảm.
- [ ] Đã thử round-trip, empty file, tampering từng trường, truncation, append bytes, wrong key, quá ngưỡng và concurrent write.
- [ ] Đã xác định rõ có cần tính chống rollback/replay, ràng buộc tenant/file, hay chính sách xóa/khôi phục không.
- [ ] Có threat model, code review, kiểm thử trên JDK/provider và filesystem triển khai thực tế.

## 12. Câu hỏi tự kiểm tra

1. **Tại sao nonce phải lưu trong header nhưng không cần bí mật?** Vì bên giải mã phải dùng lại đúng nonce; bảo mật GCM dựa vào key bí mật và không lặp cặp key/nonce, không dựa vào che nonce.
2. **Tại sao version/suite/header được đưa vào AAD?** Để byte metadata được ràng buộc với ciphertext/tag, tránh sửa metadata mà vẫn được chấp nhận.
3. **Vì sao chỉ thay một byte ciphertext lại thất bại ở `doFinal`?** Vì tag AEAD kiểm tra toàn bộ ciphertext và AAD đã cung cấp.
4. **Vì sao không giải mã thử bằng key mới nếu key cũ không có?** Vì file được mã hóa bằng key xác định; key khác không có khả năng giải mã hợp lệ và fallback che lỗi quản lý khóa.
5. **Vì sao demo giới hạn plaintext 1 MiB?** Vì nó giữ file trong RAM và chỉ xuất plaintext sau khi xác thực; giới hạn ngăn tiêu thụ bộ nhớ không kiểm soát.
6. **Thêm `keyId` có đồng nghĩa bảo vệ key không?** Không; nó chỉ là định danh công khai giúp tìm đúng key ở hệ thống bảo mật ngoài file.
7. **Một file cũ nhưng nguyên vẹn có được GCM phát hiện là replay không?** Không; cần trusted version/sequence hoặc cơ chế chống rollback riêng.
8. **Nếu version 2 dùng chunk riêng, tại sao cần final record có tag?** Để phát hiện file bị cắt bớt, tránh chấp nhận tiền tố hợp lệ như file đầy đủ.
9. **Nếu `Cipher.update` trả plaintext trước khi xác thực tag thì nên xử lý thế nào?** Không đưa ra bên ngoài; chờ tag/chuỗi record được xác minh, dùng staging và publish sau.
10. **Khi nào chọn AAD ngoài header?** Khi phải ràng buộc file với tenant, purpose, ID hoặc ngữ cảnh tin cậy; định nghĩa canonical encoding và lấy dữ liệu từ nguồn đúng quyền.

## 13. Tài liệu tham khảo

- [Oracle Java SE 21 — Cipher API và AEAD/GCM](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
- [Oracle Java SE 21 — GCMParameterSpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html)
- [Oracle Java SE 21 — AEADBadTagException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/AEADBadTagException.html)
- [Oracle Java SE 21 — SecureRandom](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html)
- [Oracle Java SE 21 — Files và ATOMIC_MOVE](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html)
- [NIST SP 800-38D — Recommendation for Galois/Counter Mode](https://csrc.nist.gov/pubs/sp/800/38/d/final)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-practice-004">23.4 Xây bộ kiểm tra mật khẩu PBKDF2 hỗ trợ nâng cấp tham số</a>

<details>
<summary>Xem chi tiết</summary>

# 23.4. Xây bộ kiểm tra mật khẩu PBKDF2 hỗ trợ nâng cấp tham số

## 1. Bài toán và kết quả mong đợi

Ứng dụng đã lưu mật khẩu với PBKDF2 vài năm trước, khi số vòng lặp (*iterations*) thấp hơn chính sách hiện tại. Nâng cấp toàn bộ bản ghi bằng cách chạy PBKDF2 lần nữa trên hash đã lưu là sai: kết quả mới sẽ không còn là phép kiểm tra trực tiếp mật khẩu gốc và có thể tạo lỗ hổng migration. Yêu cầu đúng là: **xác minh bằng chính tham số đã lưu**, và **chỉ sau khi người dùng chứng minh biết mật khẩu** mới tạo verifier mới bằng tham số hiện hành, với **salt ngẫu nhiên mới**, sau đó cập nhật bản ghi an toàn.

Bài học thực hành này xây một bộ kiểm tra mật khẩu trong **Java 17+** với khả năng nâng cấp tham số trên lần đăng nhập hợp lệ. Sau bài, bạn có thể:

1. Giải thích PBKDF2-HMAC-SHA256, salt, iteration count, độ dài derived key và giới hạn bảo vệ của việc “hash chậm”.
2. Thiết kế một bản ghi verifier có **algorithm identifier + format version + tham số + salt + derived key**, đủ dữ liệu để kiểm tra bản ghi cũ và nâng cấp theo thời gian.
3. Viết luồng **register → verify → decide rehash → propose replacement**, không nâng cấp khi sai mật khẩu và không hạ cấp tham số đã mạnh hơn.
4. Kiểm tra byte đã dẫn xuất bằng phép so sánh phù hợp với dữ liệu bí mật; giải phóng bản sao mật khẩu trong `PBEKeySpec`.
5. Dùng cập nhật **compare-and-swap** ở cơ sở dữ liệu để tránh ghi đè thay đổi mật khẩu đồng thời.
6. Thiết kế kiểm thử đúng/sai mật khẩu, verifier bị hỏng, dữ liệu legacy, thời gian xử lý, giới hạn tài nguyên và lựa chọn chính sách production.

**Phạm vi:** lưu trữ/verifying mật khẩu cho đăng nhập, **không** dùng PBKDF2 như mã hóa hai chiều hoặc thay thế các cơ chế xác thực, chống brute force và bảo vệ phiên đăng nhập. Bài trình bày một triển khai học tập có thể biên dịch/chạy; khi sản xuất, phải ghép với authentication framework, storage và chính sách vận hành của hệ thống.

## 2. PBKDF2 thực sự giải quyết vấn đề gì?

PBKDF2 (*Password-Based Key Derivation Function 2*) biến mật khẩu người dùng cùng salt và chi phí tính toán thành một chuỗi byte dẫn xuất. Trong JDK, ta có thể gọi:

```text
derived = PBKDF2-HMAC-SHA256(password, salt, iterations, outputLength)

stored = algorithm + version + iterations + salt + derived
verify(input):
    candidate = PBKDF2-HMAC-SHA256(input, stored.salt,
                                  stored.iterations, stored.outputLength)
    compare(candidate, stored.derived)
```

| Thành phần | Ý nghĩa | Lưu ở đâu? |
| --- | --- | --- |
| Mật khẩu gốc | Bí mật do người dùng biết | Không lưu trong database dưới dạng plaintext, mã hóa có thể giải mã, log hoặc source. |
| `salt` | Byte ngẫu nhiên độc lập cho từng lần tạo verifier, làm các mật khẩu giống nhau thường có hash khác nhau và cản việc tiền tính bảng dò. | Lưu **cùng verifier**; salt **không cần giữ bí mật**. |
| `iterations` | Số vòng lặp làm mỗi lần đoán mật khẩu tốn thêm CPU. | Lưu **cùng verifier** vì bản ghi cũ có thể có cost khác hiện tại. |
| HMAC/PRF | Hàm giả ngẫu nhiên nội bộ của PBKDF2; bài này dùng HMAC-SHA256. | Ghi rõ bằng ID `pbkdf2-sha256`, không chỉ ghi chung chung “PBKDF2”. |
| `derived key length` | Số bit đầu ra; ví dụ 32 byte tương đương 256 bit, **không** có nghĩa mật khẩu được bảo đảm entropy 256 bit. | Có thể suy ra từ độ dài verifier đã lưu; dùng đúng khi kiểm tra bản ghi cũ. |
| Format/version | Quy ước encoding, interpretation, normalization và schema dùng để parse. | Lưu trong bản ghi để tránh nhầm giữa các phiên bản. |
| Pepper (tùy chính sách) | Bí mật cấp ứng dụng dùng ở một lớp bảo vệ bổ sung, tách khỏi DB. | Nếu có, lưu trong KMS/HSM/secret store được quản lý, **không** ghép vào cột hash. |

PBKDF2 giúp tăng **chi phí đoán offline** khi database bị lộ. Nó không làm mật khẩu phổ biến trở thành mạnh, không chống được login online nếu ứng dụng không giới hạn request, không mã hóa password để có thể “giải mã lại”. **SHA-256 một lần** hoặc `MessageDigest.digest(password)` không thay thế password hashing có cost như PBKDF2/Argon2id.

Với hệ thống không có yêu cầu đặc biệt về FIPS, **Argon2id** thường được OWASP ưu tiên vì có tính chất *memory-hard*. PBKDF2-HMAC-SHA256 vẫn là lựa chọn hữu ích khi cần triển khai bằng JCA/JCE, tương thích hệ thống cũ hoặc dùng cryptographic module đáp ứng chính sách. **Sử dụng thuật toán PBKDF2 tự nó không chứng minh hệ thống đạt FIPS**: còn cần kiểm tra provider/module, chế độ vận hành, khóa, quy trình và phạm vi chứng nhận.

## 3. Chính sách tham số phải có khả năng thay đổi

Không nên cố định một giá trị iteration trong hàm `verify` rồi áp cho mọi tài khoản. Chính sách có thể thay đổi theo năm, máy chủ và yêu cầu tuân thủ; dữ liệu được tạo ở thời điểm cũ phải vẫn đọc được trong giai đoạn di trú.

| Thông số | Ví dụ **lab** để chạy nhanh | Ví dụ **production baseline tham khảo** |
| --- | --- | --- |
| Algorithm | `PBKDF2WithHmacSHA256` | `PBKDF2WithHmacSHA256` nếu policy chọn PBKDF2 |
| Iterations | Cũ `12_000` → mới `24_000` | **Ít nhất 600.000** theo OWASP cho PBKDF2-HMAC-SHA256 tại thời điểm biên soạn; benchmark và điều chỉnh theo chính sách mới nhất |
| Salt | Cũ 12 byte → mới 16 byte | 16 byte ngẫu nhiên/record là lựa chọn thông dụng (128 bit), tránh trùng lặp |
| Derived verifier | Cũ 24 byte → mới 32 byte | 32 byte/256 bit trong bài |
| Randomness | `SecureRandom` | CSPRNG đã được chính sách triển khai chấp thuận |
| Format | `pbkdf2-sha256$v1$...` | Có định danh algorithm và version tường minh |
| Migration | Rehash khi login thành công | Rehash + DB CAS, xử lý các account lâu không login và các tình huống reset riêng |

**12.000/24.000 iterations trong ví dụ Java là cố ý thấp để demo nhanh; tuyệt đối không dùng như cấu hình production.** OWASP Password Storage Cheat Sheet hiện hướng dẫn **600.000 iterations cho PBKDF2-HMAC-SHA256** trong các trường hợp cần FIPS-compatible password hashing; đây là mốc tham khảo để bắt đầu, không phải benchmark hoàn chỉnh trên mọi hạ tầng. NIST SP 800-63B-4 (2025) yêu cầu salt và scheme chống đoán offline, khuyến nghị lưu scheme/cost để nâng cấp và tăng cost theo năng lực tính toán qua thời gian. Hãy đo thời gian p95/p99 trên máy production dự kiến, số request đồng thời, lưu lượng đăng nhập và ngân sách CPU trước khi phê duyệt.

### 3.1. Tại sao không tăng vòng lặp ngay trong lúc verify?

Giả sử bản ghi cũ dùng `12_000`, hiện policy là `600_000`. Muốn kiểm tra mật khẩu nhập vào, **bắt buộc** dùng `12_000` và salt cũ để tính candidate tương thích. Dùng `600_000` trên password và salt cũ rồi so sánh với digest cũ luôn thất bại. Quy trình đúng:

```text
Password người dùng nhập
    |
    +--> Parse bản ghi cũ: algorithm, version, old cost, salt, outputLength
    |
    +--> PBKDF2(input, salt cũ, cost cũ, length cũ)
    |       |
    |       +-- KHÔNG KHỚP --> từ chối; không nâng cấp
    |       |
    |       +-- KHỚP --> kiểm tra chính sách hiện tại
    |                        |
    |                        +-- đã đạt --> đăng nhập; giữ nguyên bản ghi
    |                        |
    |                        +-- chưa đạt --> salt mới ngẫu nhiên
    |                                         + PBKDF2(input, tham số mới)
    |                                         + cập nhật CAS nguyên tử
    |                                         + giữ kết quả đăng nhập hợp lệ
```

Việc tạo verifier mới đòi hỏi **mật khẩu gốc trong lần login hợp lệ** (hoặc thiết lập lại mật khẩu với người dùng); không thể tính verifier tương đương với tham số mới chỉ từ giá trị digest cũ mà không có bí mật đầu vào.

### 3.2. Format của bản ghi

Bài này dùng một chuỗi tự mô tả:

```text
pbkdf2-sha256$v1$<iterations>$<salt-base64url>$<verifier-base64url>
```

Đây là **cú pháp minh họa**, các placeholder không được lưu nguyên vào DB.

Ví dụ lược đồ logic:

| Trường | Ví dụ | Lý do có mặt |
| --- | --- | --- |
| `pbkdf2-sha256` | ID | Tránh nhầm với PBKDF2-HMAC-SHA1, bcrypt hoặc Argon2id. |
| `v1` | Phiên bản encoding | Dành chỗ cho thay đổi tương thích sau này. |
| `24000` | Iteration của record | Mỗi user có thể có cost khác nhau. |
| Base64url(salt) | Salt riêng mỗi record | Tái tạo đúng phép dẫn xuất của record. |
| Base64url(verifier) | Digest đã dẫn xuất | Đối chiếu khi nhập mật khẩu. |

**Không đưa secret vào record.** Record chứa salt và verifier là dữ liệu nhạy cảm cần quản lý quyền truy cập, backup, logging và retention, nhưng không chứa mật khẩu gốc. Dùng Base64url **không** mã hóa hoặc che giấu mật khẩu; nó chỉ biểu diễn byte dưới dạng text. Trong ví dụ, độ dài verifier đã giải mã chính là tham số `hashBytes`.

**Tính tương thích:** format `v1` nên được gắn với một quy ước encoding password xác định. Không tự động `trim()`, lowercase, đổi normalization Unicode hoặc thay provider có cách diễn giải `char[]` khác giữa lưu và kiểm tra; nếu phải thay, version và quy trình migration cần mô tả rõ. Đặc biệt, việc “làm sạch” dấu cách ở đầu/cuối hoặc biến đổi chữ hoa có thể vô tình thay đổi secret người dùng đã chọn.

## 4. Thiết kế API của bộ verifier

Một bộ verifier tốt trả kết quả có thể phân biệt:

- **`valid=false`:** password sai. Không có verifier mới, không cập nhật DB.
- **`valid=true, upgradedRecord=null`:** password đúng, record đã đạt policy (hoặc mạnh hơn policy hiện tại); không cần ghi DB.
- **`valid=true, upgradedRecord!=null`:** password đúng, record cũ cần nâng cấp; trả **đề xuất record mới** để tầng lưu trữ áp dụng atomically.

Tách **thuật toán/kiểm tra** khỏi **lưu DB/transaction** sẽ giúp dễ test và tránh kiểm tra mật khẩu tự ý ghi đè dữ liệu mới hơn. Hàm kiểm tra chỉ cung cấp replacement; caller quyết định có cập nhật và khi nào ghi audit.

Chính sách nâng cấp trong bài xét ba chiều:

```text
needsUpgrade =
    stored.iterations < target.iterations
    OR stored.saltBytes < target.saltBytes
    OR stored.hashBytes < target.hashBytes
```

Khi nâng, tính tham số mới theo `max(stored, target)` trên từng chiều để **không làm giảm** số vòng, salt length hoặc độ dài đầu ra nếu record đã mạnh hơn một phần policy mới. Quy tắc này **không** thay cho migration đổi thuật toán: nếu phải chuyển từ `pbkdf2-sha256` sang Argon2id hoặc một schema mới, xây bộ adapter riêng theo format/phiên bản và phân nhánh sau khi kiểm tra đúng password.

## 5. Java 17+ — ví dụ hoàn chỉnh, có thể biên dịch và chạy

Tạo file chính xác tên **`Pbkdf2UpgradeDemo.java`** trong thư mục thực hành. Chương trình minh họa register, login sai, login đúng, phát sinh replacement bằng salt mới, kiểm tra lại verifier sau nâng cấp, và tránh rehash vô hạn. Các phương thức `create`, `verify`, `inspect` dùng cho học tập; caller cần kết nối storage atomically như mục 7 trước khi triển khai thật.

```java
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Pbkdf2UpgradeDemo {
    private static final SecureRandom RNG = new SecureRandom();
    private static final String ID = "pbkdf2-sha256";
    private static final int MAX_RECORD_CHARS = 512;
    private static final int MAX_ITERATIONS = 3_000_000;

    record Params(int iterations, int saltBytes, int hashBytes) {
        Params {
            if (iterations < 10_000 || iterations > MAX_ITERATIONS ||
                    saltBytes < 8 || saltBytes > 64 ||
                    hashBytes < 16 || hashBytes > 64) {
                throw new IllegalArgumentException("Unsupported PBKDF2 parameters");
            }
        }
    }

    private record Stored(int iterations, byte[] salt, byte[] verifier) {}
    record Check(boolean valid, String upgradedRecord) {}

    static String create(char[] password, Params params)
            throws GeneralSecurityException {
        if (password == null || params == null) {
            throw new IllegalArgumentException("Password and params required");
        }
        byte[] salt = new byte[params.saltBytes()];
        RNG.nextBytes(salt);
        byte[] digest = derive(password, salt, params.iterations(), params.hashBytes());
        try {
            return ID + "$v1$" + params.iterations() + "$"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + "$"
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } finally {
            Arrays.fill(digest, (byte) 0);
        }
    }

    static Check verify(char[] password, String record, Params target)
            throws GeneralSecurityException {
        if (password == null || target == null) {
            throw new IllegalArgumentException("Password and policy required");
        }
        Stored old = parse(record);
        byte[] actual = derive(password, old.salt(), old.iterations(),
                old.verifier().length);
        boolean matches;
        try {
            matches = MessageDigest.isEqual(actual, old.verifier());
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
        if (!matches) {
            return new Check(false, null);
        }
        boolean needsUpgrade = old.iterations() < target.iterations()
                || old.salt().length < target.saltBytes()
                || old.verifier().length < target.hashBytes();
        if (!needsUpgrade) {
            return new Check(true, null);
        }
        Params next = new Params(
                Math.max(old.iterations(), target.iterations()),
                Math.max(old.salt().length, target.saltBytes()),
                Math.max(old.verifier().length, target.hashBytes()));
        return new Check(true, create(password, next));
    }

    static Params inspect(String record) {
        Stored s = parse(record);
        return new Params(s.iterations(), s.salt().length, s.verifier().length);
    }

    private static Stored parse(String encoded) {
        if (encoded == null || encoded.length() > MAX_RECORD_CHARS) {
            throw new IllegalArgumentException("Invalid stored verifier");
        }
        String[] fields = encoded.split("\\$", -1);
        if (fields.length != 5 || !ID.equals(fields[0])
                || !"v1".equals(fields[1])) {
            throw new IllegalArgumentException("Unsupported verifier format");
        }
        try {
            int iterations = Integer.parseInt(fields[2]);
            byte[] salt = Base64.getUrlDecoder().decode(fields[3]);
            byte[] verifier = Base64.getUrlDecoder().decode(fields[4]);
            new Params(iterations, salt.length, verifier.length);
            return new Stored(iterations, salt, verifier);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Malformed stored verifier", ex);
        }
    }

    private static byte[] derive(char[] password, byte[] salt,
            int iterations, int hashBytes) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, hashBytes * 8);
        try {
            byte[] result = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            if (result == null || result.length != hashBytes) {
                throw new GeneralSecurityException("Unexpected derived key length");
            }
            return result;
        } finally {
            spec.clearPassword();
        }
    }

    public static void main(String[] args) throws Exception {
        // Chỉ để chạy lab nhanh: KHÔNG dùng 12k/24k iterations khi triển khai thật.
        Params oldParams = new Params(12_000, 12, 24);
        Params newParams = new Params(24_000, 16, 32);
        char[] initialPassword = "demo-password-2026".toCharArray();
        String stored;
        try {
            stored = create(initialPassword, oldParams);
        } finally {
            Arrays.fill(initialPassword, '\0');
        }

        char[] wrong = "wrong-password".toCharArray();
        Check bad;
        try {
            bad = verify(wrong, stored, newParams);
        } finally {
            Arrays.fill(wrong, '\0');
        }
        System.out.println("Wrong accepted: " + bad.valid());
        System.out.println("Wrong upgrade: " + (bad.upgradedRecord() != null));

        char[] correct = "demo-password-2026".toCharArray();
        Check good;
        try {
            good = verify(correct, stored, newParams);
        } finally {
            Arrays.fill(correct, '\0');
        }
        System.out.println("Correct accepted: " + good.valid());
        System.out.println("Upgrade offered: " + (good.upgradedRecord() != null));
        Params upgraded = inspect(good.upgradedRecord());
        System.out.println("New iterations: " + upgraded.iterations());
        System.out.println("New salt bytes: " + upgraded.saltBytes());
        System.out.println("New verifier bytes: " + upgraded.hashBytes());

        char[] again = "demo-password-2026".toCharArray();
        Check rechecked;
        try {
            rechecked = verify(again, good.upgradedRecord(), newParams);
        } finally {
            Arrays.fill(again, '\0');
        }
        System.out.println("New verifier valid: " + rechecked.valid());
        System.out.println("Further upgrade needed: " +
                (rechecked.upgradedRecord() != null));
    }
}
```

Lệnh chạy:

```shell
javac Pbkdf2UpgradeDemo.java
java Pbkdf2UpgradeDemo
```

`stdout` dự kiến (salt và digest **không được in**, nên output này ổn định):

```text
Wrong accepted: false
Wrong upgrade: false
Correct accepted: true
Upgrade offered: true
New iterations: 24000
New salt bytes: 16
New verifier bytes: 32
New verifier valid: true
Further upgrade needed: false
```

Đối với một JVM có JCA provider hỗ trợ `PBKDF2WithHmacSHA256`, output trên phải chính xác. Nếu không có thuật toán/provider được cung cấp, `SecretKeyFactory.getInstance` sẽ ném `NoSuchAlgorithmException`; **không** tự hạ xuống `PBKDF2WithHmacSHA1` để làm kết quả chạy được. Cấu hình Java 17+ tiêu chuẩn với SunJCE thường có thuật toán này.

### 5.1. Đọc lớp `Params` và giới hạn đầu vào

`Params` đóng gói `iterations`, `saltBytes` và `hashBytes`, đồng thời kiểm tra biên trước khi tính toán. Giới hạn `iterations <= 3_000_000` và `encoded.length() <= 512` trong demo nhằm ngăn một bản ghi DB bị hỏng/bị sửa làm tiêu tốn CPU hoặc bộ nhớ bất thường. **Những giới hạn này là cấu hình của bộ demo**, phải thiết kế lại khi policy production cao hơn; không nên nhận trực tiếp iteration từ client rồi dùng không kiểm soát.

`Params` của demo cho phép số vòng nhỏ để mô phỏng record cũ. **Mức tối thiểu được phép đọc record cũ không phải mức tối thiểu được phép tạo record mới ở production.** Hệ thống thật nên có policy mới riêng, đảm bảo `create` cho đăng ký/reset luôn dùng thông số production đã duyệt. Nếu record cực cũ thấp hơn biên parser, cần migration adapter có kiểm soát hoặc hướng dẫn reset; không được “sửa” số vòng trong chuỗi trước khi verify.

### 5.2. Đọc `create` và `derive`

- `RNG.nextBytes(salt)` tạo salt mới mỗi lần tạo record. Không chia sẻ một salt cố định cho toàn bộ user, không lấy salt từ username/timestamp, không dùng `java.util.Random`.
- `PBEKeySpec(password, salt, iterations, hashBytes * 8)` yêu cầu độ dài bằng **bit**, trong khi phần còn lại của lớp dùng **byte**. Đây là lỗi unit chuyển đổi rất dễ gặp.
- `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` quyết định HMAC nội bộ; **không** thay bằng một vòng `SHA-256` thông thường.
- `Base64.getUrlEncoder().withoutPadding()` biểu diễn salt/digest thành text phù hợp lưu DB; cả hai phần vẫn phải được coi là thông tin verifier cần bảo vệ.
- `PBEKeySpec.clearPassword()` xóa bản sao ký tự mà spec sở hữu. Caller trong `main` còn chủ động `Arrays.fill(charArray, '\0')` sau khi dùng. Không thể bảo đảm xóa mọi bản sao qua JVM/GC, nhưng giới hạn vòng đời bí mật vẫn có giá trị. Chuỗi mật khẩu literal trong `main` **chỉ để test**, không được hardcode mật khẩu thật.
- Mảng `digest`/`actual` được ghi đè byte 0 sau khi hoàn thành sử dụng; log không in salt, digest, password hoặc private secrets.

### 5.3. Đọc `parse` và `verify`

- `parse` chỉ chấp nhận **đúng scheme, version và số trường**, giải mã Base64url và kiểm tra giới hạn. Nó xử lý record từ DB như **dữ liệu không đáng tin**, nhưng việc ngăn format bất thường trong demo không thay thế kiểm tra toàn vẹn dữ liệu và bảo vệ quyền sửa DB.
- `verify` dùng **iterations, salt, verifier length của record cũ** khi tính candidate. Nó không dùng `target.iterations` để kiểm tra record cũ.
- `MessageDigest.isEqual(actual, old.verifier())` tránh so sánh từng byte theo kiểu dừng ở vị trí khác đầu tiên. Với output cùng chiều dài, đây là công cụ phù hợp để giảm lộ thông tin theo giá trị byte. Nó **không** bảo đảm cả API login có timing giống nhau khi username không tồn tại, format khác, thuật toán khác hay chi phí mỗi record khác nhau.
- Nếu mật khẩu sai, `Check(false, null)` được trả ngay: **không tái băm, không cập nhật**.
- Nếu đúng và record chưa đạt policy, `verify` gọi `create(password, strongerParams)` để tạo salt mới và verifier mới rồi trả `Check(true, replacement)`. Nó **không tự ghi database**.
- Nếu record hiện tại đã bằng hoặc mạnh hơn policy trên mọi chiều, `Check(true, null)`; mỗi login tiếp theo không tạo lại salt/digest vô ích.

## 6. Sự khác nhau giữa “rehash” và “đăng nhập”

Trong thực tế, kết quả xác minh đúng mật khẩu và việc nâng cấp hash có liên quan nhưng không phải một transaction đơn giản:

```text
AccountStore.read(userId)
   -> snapshot {accountId, passwordVerifier, credentialVersion, disabled, ...}
   -> kiểm tra trạng thái account và password được cung cấp
   -> PBKDF2.verify(password, snapshot.passwordVerifier, targetPolicy)
       |
       +-- INVALID -> không cấp phiên, không update verifier
       |
       +-- VALID, no replacement -> xử lý session theo policy hiện hành
       |
       +-- VALID, replacement ->
             AccountStore.compareAndSwap(snapshot.version, replacement)
             |
             +-- 1 row -> nâng cấp thành công
             +-- 0 row -> có thay đổi đồng thời; không ghi đè,
                          đọc lại account / đánh giá session theo chính sách
```

**Điều kiện bắt buộc:** account còn hợp lệ, không khóa/disable, không bị reset password hay thu hồi credential trong lúc xử lý. Nếu CAS thất bại do người dùng vừa đổi mật khẩu, **không được cố ghi lại verifier từ snapshot cũ**, kể cả password cũ vừa verify thành công. Chính sách session ở thời điểm đó phải xét trạng thái/phiên bản credential mới, không đơn giản cấp token vô điều kiện.

### 6.1. Vì sao phải dùng salt mới khi nâng tham số?

Nếu tăng số vòng lặp cho cùng password nhưng giữ salt cũ, vẫn có thể tạo verifier dùng cost mới. Tuy nhiên quy trình rehash chuẩn nên **sinh salt mới** để giảm liên hệ giữa hai record, duy trì nguyên tắc mỗi lần tạo verifier có salt ngẫu nhiên. Trong code, `create` luôn phát sinh salt mới. Không được thao tác kiểu:

```text
newVerifier = PBKDF2(oldVerifier, oldSalt, newIterations)
```

Lệnh trên dùng **digest** làm mật khẩu mới, không tương đương với `PBKDF2(originalPassword, newSalt, newIterations)`. Nó cũng để lại mô hình chuyển đổi mà kẻ tấn công có bản ghi DB có thể khai thác mà không cần biết mật khẩu. Không dùng cách này như “nâng cấp không cần đăng nhập”.

### 6.2. Không áp dụng giảm tham số bằng rehash

Giả sử policy mới đặt 600.000 vòng nhưng một số record đã có 850.000 vòng, salt 32 byte. Rehash về 600.000 vòng/salt 16 byte sẽ là **hạ cấp một phần**. Bộ demo giữ `Math.max(old,target)` cho mọi chiều nếu một chiều khác vẫn phải nâng. Nếu phải giảm cost vì hiệu năng hoặc thay đổi tiêu chuẩn, đó phải là một **quyết định migration chủ động**, được phê duyệt, phân tích threat model và kiểm thử riêng; đừng để logic “needsUpgrade” tự động thực hiện.

## 7. Tích hợp database an toàn: compare-and-swap

Bảng minh họa:

```sql
CREATE TABLE account_credentials (
    account_id BIGINT PRIMARY KEY,
    password_verifier VARCHAR(512) NOT NULL,
    credential_version BIGINT NOT NULL DEFAULT 0
);
```

Ở bước đăng nhập, đọc `password_verifier` và `credential_version` cùng trạng thái tài khoản phù hợp. Sau khi `verify` trả `valid=true` và có `upgradedRecord`, ghi atomically bằng điều kiện version:

```sql
UPDATE account_credentials
SET password_verifier = :new_verifier,
    credential_version = credential_version + 1
WHERE account_id = :account_id
  AND credential_version = :old_credential_version;
```

Đây là **SQL tham số hóa minh họa**, các tên `:param` không phải SQL thuần có thể chạy trực tiếp không thông qua driver/framework. Giá trị tham số phải được **bind**, không nối chuỗi. Đếm số row affected:

- **1 row:** upgrade được lưu thành công; cần bảo đảm không bỏ qua quy tắc audit/credential session.
- **0 row:** snapshot không còn phù hợp (record có thể đã bị reset, đổi mật khẩu, vô hiệu hóa hoặc được upgrade bởi login khác). Đọc lại và thực hiện logic bảo mật thích hợp; **không thử UPDATE vô điều kiện**.
- **Lỗi database:** quy định rõ xử lý retry và session; khi thay đổi trạng thái credential, kiểm tra account theo transaction/isolation thích hợp.

Nếu bảng account/credential liên quan nhiều trạng thái, có thể dùng version riêng cho password credential hoặc ràng buộc với trạng thái account trong cùng transaction. Đối với hệ thống có nhiều node, CAS trong DB quan trọng hơn `synchronized` ở một JVM. Tuyệt đối không dùng `UPDATE ... WHERE account_id=:id` đơn thuần trên bản ghi vừa được verify từ snapshot cũ.

**Tình huống race điển hình:** Request A đọc hash cũ và verify thành công. Request B reset mật khẩu, ghi verifier mới. Request A chạy rehash từ password cũ. Nếu A không CAS, nó ghi đè reset của B và **khôi phục khả năng đăng nhập bằng mật khẩu cũ**. Version check ngăn thao tác ghi này; logic session cũng phải kiểm tra credential version tương ứng.

## 8. Thiết kế kiểm thử: không chỉ happy path

| Trường hợp | Dữ liệu/điều kiện | Kết quả mong đợi |
| --- | --- | --- |
| Đăng ký mới | Password hợp lệ, policy hiện tại | Record có scheme, version, cost hiện hành, salt ngẫu nhiên, derived key đúng độ dài. |
| Đúng mật khẩu, record cũ | Cost/salt/hash nhỏ hơn policy | `valid=true`, `upgradedRecord!=null`; record mới verify lại thành công. |
| Sai mật khẩu, record cũ | Password khác secret đã lưu | `valid=false`, không tạo replacement, không UPDATE. |
| Đúng mật khẩu, record đã đạt | Cùng hoặc mạnh hơn policy | `valid=true`, replacement `null`, không tính PBKDF2 lần 2 để nâng. |
| Hai người dùng cùng password | Hai lần tạo record riêng | Salt và verifier **thường khác nhau**; không kiểm tra bằng cách trông chờ hai string giống nhau. |
| Sai một byte verifier | Tamper ở DB hoặc copy lỗi | Đăng nhập không được chấp nhận; nếu format không hợp lệ phải xử lý như lỗi record. |
| Sai version/algorithm | `v2` hoặc `bcrypt` ở parser v1 | Từ chối/route sang adapter đã định nghĩa, không âm thầm thử sai thuật toán. |
| Iterations cực lớn | Trường cost do dữ liệu bị hỏng | Parser từ chối trước phép tính nặng (bound của policy). |
| Base64 không hợp lệ | Salt/digest chứa ký tự sai hoặc thiếu phần | Format error được kiểm soát, không làm crash toàn bộ service. |
| Đổi mật khẩu đồng thời | CAS update affected 0 row | Không ghi đè bản ghi mới; không cấp phiên dựa trên credential stale khi policy cấm. |
| Policy tăng giữa hai phiên bản deploy | Node A/B khác cấu hình | Không tự hạ cấp; đồng bộ chính sách và migration rollout. |
| KDF/provider không khả dụng | Không có `PBKDF2WithHmacSHA256` | Báo lỗi cấu hình/service; không fallback sang hash nhanh hoặc SHA-1. |
| Input rất dài/traffic cao | Nhiều lần thử sai/đầu vào lớn | Có rate limiting, giới hạn request theo chính sách, tài nguyên và capacity test. |

### 8.1. Bộ test cần khẳng định tính bất biến

Một test migration tốt không so sánh cả chuỗi hash mới với một literal: salt ngẫu nhiên khiến output khác nhau mỗi lần. Thay vào đó, test các thuộc tính:

1. Bản ghi mới có `iterations/saltBytes/hashBytes` đúng mục tiêu và format đúng.
2. `verify(correct,newRecord,currentPolicy)` thành công.
3. `verify(wrong,newRecord,currentPolicy)` thất bại.
4. `verify(correct,newRecord,currentPolicy).upgradedRecord()==null`.
5. Khi đưa record cũ vào, replacement chỉ xuất hiện sau khi password **đúng**.
6. DB CAS bảo vệ được password reset và concurrent login.
7. Không có secret/salt/verifier nguyên văn được đưa vào application log; exception không biến thành thông báo cụ thể cho người dùng.

Trong integration test, phải dùng **cấu hình đủ mạnh và đúng provider** ít nhất ở một bộ test đại diện, đồng thời test nhanh có thể dùng cost thấp. Không dùng benchmark giả lập vài nghìn vòng làm căn cứ phát hành.

## 9. Chính sách production và triển khai dần

### 9.1. Chọn iteration cost dựa trên yêu cầu và benchmark

Theo OWASP Password Storage Cheat Sheet được đối chiếu tại thời điểm soạn bài, **PBKDF2-HMAC-SHA256: 600.000 iterations** là khuyến nghị; OWASP cũng ưu tiên Argon2id khi không có ràng buộc sử dụng PBKDF2. Giá trị 600.000 phải được nhìn như **mốc chuẩn tham khảo**, cần theo dõi thay đổi hướng dẫn và benchmark theo hạ tầng, yêu cầu tuân thủ, timeout, số luồng và giới hạn tài nguyên.

Hệ thống nên đo:

- Thời gian `verify` trung vị, p95/p99 ở tải đăng nhập dự kiến; đo cả lúc CPU căng hoặc có nhiều login.
- Peak concurrent work của PBKDF2, thread pool, rate limiter, hàng đợi và khả năng lạm dụng login để gây DoS.
- Thời gian khi **upgrade**: một lần xác minh bằng cost cũ cộng một lần tạo hash bằng cost mới, có thể lớn hơn xác minh thông thường.
- Nhu cầu người dùng/password reset và tốc độ hoàn tất migration; số lượng record theo nhóm cost và version.
- Kế hoạch tăng cost định kỳ và giới hạn tối đa chấp nhận của parser, đồng bộ giữa các instance.

Không khuyến nghị tăng vô hạn iteration: máy khách truy cập thật phải nhận kết quả trong budget phù hợp, còn kẻ tấn công online cần bị chặn bằng rate limiting và chống tự động hóa. Nếu đạt điều kiện dùng Argon2id, đánh giá memory cost song song với CPU cost.

### 9.2. Salt và nguồn ngẫu nhiên

- Sinh ít nhất một salt ngẫu nhiên riêng cho mỗi verifier; 16 byte (128 bit) là lựa chọn thực tế, dù NIST 800-63B-4 nêu mức tối thiểu salt **32 bit**. Không coi 32 bit là mục tiêu tối ưu cho hệ thống mới.
- `SecureRandom` của Java lấy ngẫu nhiên từ provider; production có yêu cầu validated module phải xác minh nguồn/provider và chế độ hoạt động theo policy.
- **Không dùng salt là bí mật chung**, không dùng username/email/timestamp làm salt duy nhất, không tái sử dụng salt từ record cũ khi rehash nếu có thể tránh.
- Salt/derived hash lưu trong cùng record cho phép phân biệt credential của các account và nâng cấp nhiều phiên bản.

### 9.3. Password representation và Unicode

PBKDF2 nhận password dưới dạng chuỗi ký tự nhưng thuật toán cơ sở xử lý byte; các thư viện/provider có thể khác nhau ở quy ước chuyển `char[]` sang byte, Unicode normalization và biểu diễn ký tự. Để migration có thể kiểm chứng:

1. Chọn quy ước encoding và normalization ngay khi thiết kế; lưu bằng scheme/version hoặc tài liệu contract.
2. Không tự `trim()`, lowercase, cắt ký tự hoặc chuẩn hóa khác nhau giữa đăng ký và đăng nhập.
3. Giữ khả năng đọc record được tạo từ phiên bản cũ/provider cũ, hoặc viết adapter kiểm chứng được bằng *test vectors*.
4. Kiểm tra password Unicode, ký tự tổ hợp, emoji, khoảng trắng, boundary lengths và đổi runtime/provider.
5. Cấp đủ kích thước password input phù hợp chính sách truy cập; hạn chế request bất thường để chống DoS, nhưng không ép password ngắn hay truncate bí mật ngoài yêu cầu chuẩn.

`PBEKeySpec` giữ bản sao `char[]` trong nội bộ; `clearPassword()` giúp xóa bản sao đó sau khi xử lý. Tầng web có thể đã nhận password dưới dạng `String` bất biến, khó xóa khỏi heap; điều này đòi hỏi tránh lưu/log/truyền secret không cần thiết và giảm vòng đời object, chứ không thể hứa bảo đảm mọi byte đã bị xóa.

### 9.4. Pepper và quản lý bí mật bổ sung

Một **pepper** là khóa bí mật chung hoặc khóa có quản lý dùng để bổ sung lớp bảo vệ nếu DB bị lộ. Ví dụ kiến trúc có thể HMAC verifier với key do HSM/KMS giữ, tách khỏi cột database. Tuy nhiên:

- Pepper **không thay thế** salt riêng từng user và PBKDF2 cost.
- Pepper phải được cấp phát, lưu riêng, phân quyền và xoay vòng; khi mất pepper, không thể đơn giản xác minh các bản ghi phụ thuộc nó.
- Format nên có `pepperKeyId`/version nếu có rotation; migration phải biết verifier nào dùng khóa nào và trình tự verify/rehash đúng.
- Không hardcode pepper vào source, không đặt cạnh DB dump, và không tự viết sơ đồ kết hợp không có thiết kế/chính sách rõ ràng.

NIST SP 800-63B-4 khuyến nghị thêm một keyed hashing/encryption operation với secret key chỉ verifier biết, tách khỏi các password hashes và ưu tiên bảo vệ bằng phần cứng khi thích hợp. Bài Java này cố ý chỉ triển khai lõi PBKDF2; mở rộng với pepper cần thay đổi version/architecture theo chính sách.

## 10. Phạm vi bảo vệ và các biện pháp bắt buộc xung quanh PBKDF2

Ngay cả khi PBKDF2 đúng, đăng nhập còn cần:

- **Rate limiting** theo tài khoản/IP/threat signals, theo dõi credential stuffing và tránh biến password KDF thành CPU exhaustion endpoint.
- **TLS** khi truyền password; không ghi mật khẩu vào URL, debug logs, tracing, exception, analytics hoặc message queue.
- **Thông báo lỗi nhất quán** tránh tiết lộ username/account có tồn tại. Đừng cho người dùng biết record dùng 12.000 hay 600.000 vòng; dữ liệu này có thể dùng nội bộ để audit.
- **Chính sách mật khẩu** phù hợp yêu cầu hiện hành: tránh mật khẩu phổ biến/đã lộ, hỗ trợ mật khẩu dài và password manager; không tự ý rút gọn input.
- **MFA, quản lý session**: revoke/rotate session khi reset credential, tăng mức độ xác minh cho thao tác nhạy cảm.
- **Chính sách lỗi fail-safe**: unknown scheme, corrupted verifier, unavailable crypto provider phải được xử lý như lỗi authentication/configuration có kiểm soát; không nhận password chỉ vì parser lỗi.
- **Kiểm soát DB**: RBAC tối thiểu, encryption-at-rest/backup access, audit, không cho phép client ghi `password_verifier` hoặc `credential_version`.
- **Vòng đời mật khẩu**: password reset, đổi password, khóa tài khoản, migration khi password không được dùng lâu, và kế hoạch nghỉ hưu thuật toán cũ.

**Phân biệt online và offline:** PBKDF2 cố tăng chi phí cho kẻ tấn công *offline* khi đã có DB dump. Rate limiting/MFA/captcha theo risk (nếu phù hợp), account monitoring và session protection giải quyết phần lớn bối cảnh *online*. Một policy thật cần cả hai.

## 11. Nâng cấp từ hệ thống nhiều thuật toán cũ

Không phải mọi bản ghi legacy đều là PBKDF2-HMAC-SHA256. Một hệ thống cũ có thể chứa bcrypt, PBKDF2-HMAC-SHA1, SHA-1/MD5 một lần hoặc code tự chế. Không nên ép parser `v1` ở mục 5 nhận mọi định dạng:

```text
readCredential(userId)
    -> dispatch theo scheme/version đã biết
       -> legacyAdapter.verify(input, oldRecord)
       -> nếu đúng, create verifier MỚI theo policy hiện hành
       -> CAS ghi verifier mới, đánh dấu migration
       -> nếu sai, từ chối
    -> unknown/không còn kiểm chứng được
       -> quy trình password reset/khôi phục chính thức
```

Các nguyên tắc migration:

1. **Không đoán** thuật toán bằng độ dài hex string; lưu mapping/version/schema đáng tin từ lịch sử hệ thống.
2. Chỉ dùng legacy verifier cần thiết để kiểm tra mật khẩu cũ; không dùng SHA-1/MD5 mới cho đăng ký/reset.
3. Cần người dùng nhập đúng mật khẩu hoặc hoàn tất reset để có verifier mới. Không lấy **old hash làm new password**.
4. Tạo salt ngẫu nhiên mới, dùng format/scheme hiện hành, lưu bằng CAS và giữ audit.
5. Theo dõi tỷ lệ hoàn tất migration; record không login lâu có thể cần password reset tại mốc kết thúc hỗ trợ legacy.
6. Khi có nhiều instance phiên bản cũ/mới, bảo đảm **reader/writer compatibility** và không downgrade. Bật ghi format mới trước khi đảm bảo mọi consumer hiểu format đó có thể làm gián đoạn đăng nhập.
7. Nếu policy thay cả normalization/encoding, phải có adapter hiểu representation cũ và test vectors để tránh khóa tài khoản của người dùng Unicode.

Không nên trả `valid=true` chỉ vì không đọc được old format, và không chọn fallback “mã hóa SHA-256 rồi so” nếu schema không chứng minh đó là phương pháp cũ.

## 12. Các lỗi phổ biến và cách sửa

| Lỗi | Vì sao nguy hiểm | Cách sửa |
| --- | --- | --- |
| Chỉ lưu hash, không lưu iterations/salt/scheme | Mất thông tin để kiểm tra hoặc nâng cấp nhiều thế hệ | Lưu self-describing verifier hoặc các cột metadata tương đương. |
| Dùng `new SecureRandom(seed)` với seed đoán được hoặc salt hằng | Tạo quan hệ giữa các account và tạo điều kiện tiền tính | Dùng CSPRNG, salt ngẫu nhiên mới từng record. |
| Tăng iteration trong `verify` rồi so với digest cũ | Sai kết quả, khóa tài khoản | Verify theo tham số **đã lưu**, nâng cấp sau khi đúng password. |
| “Rehash” bằng cách PBKDF2(oldDigest) | Không tạo verifier tương đương cho password gốc; không thay đổi chi phí đoán mật khẩu như kỳ vọng | Chỉ rehash từ plaintext password đã verify hoặc reset chính thức. |
| Dùng `Arrays.equals` và viết loop dừng ngay ở byte khác | Có thể lộ khác biệt theo thời gian xử lý | Dùng `MessageDigest.isEqual` trên mảng cùng chiều dài; đánh giá toàn bộ timing của login. |
| Chấp nhận iterations từ record/client không kiểm tra | Record bị hỏng hoặc request độc hại gây CPU DoS | Bound parser, schema validation, rate limiting và quản lý policy. |
| Nâng cấp cả password sai | Cho phép ghi credential mới sau đăng nhập thất bại | Chỉ propose update khi `valid=true`. |
| Rehash lại ở mọi login | Tăng CPU, ghi DB và thay salt không cần thiết | Chỉ nâng khi một tham số thấp hơn policy hoặc version cần migration. |
| Dùng luôn min cost của lab cho production | Dễ dò offline hơn chuẩn hiện hành | Tách cấu hình lab/production; chọn giá trị theo OWASP/NIST/policy và benchmark. |
| Cập nhật không có version/CAS | Race có thể ghi đè password vừa reset | `UPDATE WHERE credential_version=:old`, xử lý affected 0 row. |
| Log password, salt/verifier, timing hoặc exception detail | Rò rỉ thông tin xác thực và nội bộ | Log event ID, outcome tổng quát, metric không chứa secret; bảo vệ audit. |
| Bỏ qua format/version và bắt mọi lỗi trả success | Mở đường bypass authentication | Unknown/corrupt record phải thất bại an toàn, có quy trình reset. |
| Tự động chuyển PBKDF2-HMAC-SHA256 sang SHA-1 khi provider thiếu | Có thể vi phạm policy hoặc tạo verifier yếu mới | Báo lỗi cấu hình và xử lý hạ tầng/crypto provider. |
| Cho rằng iteration lớn tự đạt FIPS | Thuật toán khả dụng khác với chứng nhận module, approved mode và tuân thủ môi trường | Đánh giá module/procedure và các yêu cầu triển khai riêng. |

## 13. Câu hỏi tự kiểm tra

1. **Tại sao record cần lưu `iterations`?** Vì bản ghi cũ có cost khác policy mới, và verifier phải được tính với cost gốc trước khi quyết định nâng cấp.
2. **Sai password có bao giờ sinh replacement không?** Không. Chỉ khi candidate khớp verifier đã lưu mới xét policy nâng cấp.
3. **Vì sao salt không cần bí mật nhưng lại phải ngẫu nhiên?** Salt giúp khác biệt hóa phép dẫn xuất giữa record, chống tiền tính; giữ bí mật salt không phải mô hình bảo vệ chính.
4. **PBKDF2-HMAC-SHA256 với output 32 byte có làm mật khẩu 8 ký tự đạt 256 bit entropy?** Không. Output length không tăng entropy của password người dùng.
5. **Có thể làm tăng iterations chỉ bằng old hash không?** Không thể tạo verifier tương đương với password gốc theo cách đó; cần password đúng tại login hoặc quy trình reset.
6. **`MessageDigest.isEqual` đã giải quyết toàn bộ timing side channel?** Không. Format, account tồn tại/không, iterations, CPU scheduling và response path vẫn có thể khác; cần thiết kế và kiểm thử toàn luồng.
7. **Vì sao nâng cấp cần CAS?** Tránh một login cũ ghi đè password reset hoặc credential update xảy ra đồng thời.
8. **Nếu record đã có 800.000 vòng và policy 600.000 vòng?** Không rehash chỉ vì số vòng khác; nếu có chiều khác cần nâng, giữ `max(old,target)` để tránh hạ cấp.
9. **Giới hạn iterations lớn có thể tạo rắc rối gì?** Nếu hard-code không cập nhật, verifier hợp lệ có cost tăng theo policy mới có thể bị từ chối; max phải là một phần của cấu hình/version migration.
10. **Khi nào nên cân nhắc Argon2id?** Khi không bị ràng buộc phải dùng PBKDF2/FIPS-compatible implementation và có khả năng quản lý bộ nhớ/CPU theo policy; đánh giá bằng chuẩn và hạ tầng thực tế.

## 14. Checklist triển khai

- [ ] Verifier có scheme/version/cost/salt/output đủ để parse chính xác.
- [ ] Lưu salt random riêng, không lưu plaintext password; password được bảo vệ khi truyền và khi xử lý.
- [ ] Verify với tham số cũ trước; sai mật khẩu không thay đổi DB.
- [ ] Rehash dùng password đúng, salt mới và policy mới; không giảm các chiều đã mạnh hơn.
- [ ] Dùng `PBEKeySpec.clearPassword` và làm sạch các mảng bí mật trong phạm vi có thể.
- [ ] Giới hạn record length, cost, kích thước input và rate limiting; có negative tests.
- [ ] Ghi replacement bằng DB CAS/version, xử lý password reset cạnh tranh.
- [ ] New registration/reset dùng **production policy**, không dùng lab cost.
- [ ] Đo thời gian/CPU cost thật và lập lịch tăng tham số, đồng thời cập nhật parser limits.
- [ ] Có adapter migration cho scheme cũ và quy trình reset đối với record không còn kiểm chứng an toàn.
- [ ] Có authentication, authorization, TLS, MFA/session, log/audit và DB access controls phù hợp.
- [ ] Không dùng từ “FIPS compliant” chỉ dựa trên tên `PBKDF2WithHmacSHA256`.

## 15. Tài liệu đối chiếu

- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): lựa chọn Argon2id/scrypt/PBKDF2, iteration/work factor, salt/pepper, nâng cấp legacy hashes. Tham khảo PBKDF2-HMAC-SHA256 **600.000 iterations** và luôn kiểm tra lần cập nhật mới nhất.
- [NIST SP 800-63B-4 — Authentication and Authenticator Management (2025)](https://pages.nist.gov/800-63-4/sp800-63b.html): verifier chống offline guessing, salt, cost, ghi nhận scheme/cost cho migration, keyed hashing bổ sung và lifecycle.
- [Java SE 21 — PBEKeySpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html): `PBEKeySpec(char[],salt,iterations,keyLengthBits)`, bản sao password, `clearPassword()`.
- [Java SE 21 — SecretKeyFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/SecretKeyFactory.html): cách khởi tạo algorithm và generate secret theo provider.
- [Java SE 21 — MessageDigest.isEqual](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html#isEqual(byte%5B%5D,byte%5B%5D)): so sánh byte arrays phục vụ verifier.

Khi mang code sang ứng dụng thực, kiểm tra **JDK/provider**, tham số/policy và cấu trúc database thực tế; các giá trị cost trong `main` chỉ nhằm minh họa luồng upgrade.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-practice-005">23.5 Tạo và xác minh chữ ký tài liệu bằng khóa lấy từ PKCS12 KeyStore</a>

<details>
<summary>Xem chi tiết</summary>

# 23.5. Tạo và xác minh chữ ký tài liệu bằng khóa lấy từ PKCS12 KeyStore

> **Mục tiêu thực hành:** Tạo một `PKCS12 KeyStore` chứa cặp khóa RSA và chứng thư X.509, lấy `PrivateKey` từ alias để ký **đúng các byte của tài liệu** bằng JCA `Signature`, xuất chứng thư công khai, rồi dùng chứng thư đó xác minh chữ ký. Thực hành phát hiện tài liệu bị thay đổi và phân biệt rõ **chữ ký hợp lệ về mặt mật mã** với **danh tính người ký được tin cậy**. Ví dụ dùng Java 17+, JDK `keytool` và tệp chữ ký detached.

## 1. Bài toán và kết quả cần đạt

Giả sử một hệ thống cần gửi tệp `invoice.txt` cho bên nhận. Bên gửi muốn bên nhận phát hiện việc thay đổi nội dung sau khi ký và, nếu có cơ sở tin cậy riêng, xác định đúng khóa ký được phê duyệt. Ta chọn mô hình **chữ ký rời (detached signature)**:

```text
                 BÊN KÝ                          BÊN XÁC MINH
        ┌────────────────────┐             ┌──────────────────────┐
        │ invoice.txt        │             │ invoice.txt          │
        │  byte[] tài liệu   │             │ invoice.sig          │
        └─────────┬──────────┘             │ signer-cert.pem      │
                  │                        └───────────┬──────────┘
                  ▼                                    │
        ┌────────────────────┐                         ▼
        │ Signature.update() │             ┌──────────────────────┐
        │ SHA256withRSA      │             │ CertificateFactory   │
        │ + PrivateKey       │             │ X.509 -> PublicKey   │
        └─────────┬──────────┘             └───────────┬──────────┘
                  │                                    │
                  ▼                                    ▼
              invoice.sig                   Signature.initVerify()
                  │                                    │
                  └─────────── truyền cùng tài liệu ──┤
                                                       ▼
                                           Signature.verify(sig)
                                                true / false
```

Ba tệp chính của bài:

| Tệp | Chứa gì | Được công bố cho bên nhận? |
| --- | --- | --- |
| `signer.p12` | Private key và chuỗi chứng thư của signer, bảo vệ bằng cơ chế của PKCS#12/password | **Không**. Chỉ bên ký giữ, giới hạn quyền file và quản lý khóa. |
| `signer-cert.pem` | Chứng thư X.509 chứa **public key**; không có private key | Có thể công bố, nhưng bên nhận phải xác minh chứng thư này được gắn với đúng danh tính/khóa kỳ vọng. |
| `invoice.txt` | Đúng bytes cần bảo vệ | Có. Tệp có thể hiển thị được theo ứng dụng. |
| `invoice.sig` | Bytes chữ ký được tạo từ private key; không phải tài liệu đã mã hóa | Có. Đi kèm đúng bản tài liệu và thuật toán. |

Các bước hoàn thành:

1. Tạo RSA-3072 `PrivateKeyEntry` trong KeyStore loại `PKCS12`, đặt alias `docsign`.
2. Xuất leaf certificate thành PEM (không xuất private key).
3. Ký từng byte của `invoice.txt` bằng thuật toán `SHA256withRSA` và lưu bytes chữ ký vào `invoice.sig`.
4. Xác minh bằng public key đọc từ `signer-cert.pem`, không cần password hay file `signer.p12` ở phía người nhận.
5. Sửa một byte tài liệu và thấy kết quả `INVALID`; biết giải thích trường hợp sai chứng thư, sai thuật toán, sai alias, sai password.
6. Xác định phần **chưa được bài demo xử lý**: PKIX trust, kiểm tra danh tính, thu hồi, timestamp, tính hợp lệ của tài liệu theo định dạng nghiệp vụ.

## 2. Nắm đúng PKCS#12, khóa, chứng thư và chữ ký

### 2.1. PKCS#12 là container khóa, không phải thuật toán ký

`PKCS12` (thường đuôi `.p12` hoặc `.pfx`) là một định dạng keystore để lưu private key, chứng thư và chuỗi chứng thư; một số provider còn hỗ trợ các loại entry khác. Nó không tự quyết định dùng SHA-256, RSA, ECDSA, Ed25519 hay RSASSA-PSS. Thuật toán ký phải chọn riêng bằng `Signature.getInstance(...)` và tương thích với loại khóa.

Trong Java:

- `KeyStore.getInstance("PKCS12")` chọn kiểu keystore.
- `KeyStore.load(inputStream, password)` mở nội dung keystore bằng password.
- `KeyStore.isKeyEntry(alias)` giúp kiểm tra alias chứa key entry; không đủ thay kiểm tra kiểu private key.
- `KeyStore.getKey(alias, password)` trả về `Key`; để ký ta cần một `PrivateKey`.
- `KeyStore.getCertificate(alias)` trả về chứng thư gắn với alias (thường là chứng thư của signer).
- `KeyStore.getCertificateChain(alias)` có thể trả về chuỗi chứng thư nếu entry lưu chuỗi đó.
- `CertificateFactory.getInstance("X.509")` đọc chứng thư công khai DER hoặc PEM thành `X509Certificate`.
- `X509Certificate.getPublicKey()` lấy public key cần cho xác minh.

Alias trong keystore chỉ là tên định danh một entry **trong container**, không phải danh tính bảo mật đã được xác thực. Một alias `docsign` không chứng minh công ty, cá nhân hoặc máy chủ nào thực sự sở hữu khóa.

### 2.2. Public key và private key có nhiệm vụ khác nhau

| Thành phần | Dùng khi ký | Dùng khi xác minh | Bí mật? |
| --- | --- | --- | --- |
| `PrivateKey` | `Signature.initSign(privateKey)` | Không cần | **Có** |
| `PublicKey` trong certificate | Không cần để tạo signature | `Signature.initVerify(publicKey)` | Không |
| `Signature` bytes | `Signature.sign()` tạo ra | `Signature.verify(signatureBytes)` kiểm tra | Không |
| Password của PKCS12 | Giúp mở keystore và giải mã key entry theo provider | Không cần nếu dùng chứng thư PEM độc lập | **Có** |

Không nhầm chữ ký số với mã hóa tài liệu. Sau khi ký, nội dung `invoice.txt` vẫn đọc được. Nếu cần giữ bí mật nội dung, phải dùng **encryption** và cơ chế quản lý khóa riêng.

### 2.3. `SHA256withRSA` cụ thể ký cái gì?

Với JCA algorithm name `SHA256withRSA`, provider thực hiện một sơ đồ chữ ký RSA dùng SHA-256 (đối với provider JDK thông dụng là RSASSA-PKCS1-v1_5). Ứng dụng chuyển **bytes của tài liệu** qua `Signature.update(...)`, sau đó `sign()` sinh chữ ký. Khi xác minh, bên nhận dùng **đúng bytes tài liệu** và chữ ký tương ứng.

Không cần tự tính digest SHA-256 trước rồi đưa digest vào `SHA256withRSA`: nếu làm vậy, provider sẽ băm **digest đầu vào lần nữa**, tạo chữ ký cho dữ liệu khác với tài liệu gốc. `MessageDigest` chỉ dùng riêng khi cần hash để kiểm tra, hiển thị hoặc pin artifact.

Một chữ ký detached dạng raw bytes **không tự lưu** tên thuật toán, thông tin signer, timestamp hay tên tệp. Trong bài, thuật toán được cố định ở cả hai phía qua contract. Hệ thống thực tế cần envelope/manifest có version, algorithm, signer ID/certificate và context được bảo vệ bởi chính sách tin cậy.

## 3. Chuẩn bị môi trường

Yêu cầu:

- JDK **17 trở lên** có `java`, `javac` và `keytool` trên `PATH`.
- Terminal tương tác để nhập password mà không đặt password trên command line.
- Quyền ghi vào một thư mục lab không chứa dữ liệu nhạy cảm.
- Các lệnh shell dưới đây dùng **Bash** trên Linux/macOS hoặc một shell Bash tương thích; trên PowerShell cần đổi cú pháp nối dòng và lệnh tạo file. Java source giữ nguyên trên các OS.

Kiểm tra công cụ:

```bash
java -version
javac -version
keytool -help
```

Phiên bản patch và thông báo help phụ thuộc JDK; không cần chúng trùng một chuỗi output cố định. Các lệnh thực hành không cần cài thư viện bên ngoài `java.base`.

Tạo thư mục lab rồi vào đó bằng shell. Tạo đúng tài liệu văn bản với nội dung ASCII và xuống dòng LF:

```bash
mkdir -p document-signing-lab
cd document-signing-lab
printf 'Invoice ID: INV-42\nAmount: 100 USD\n' > invoice.txt
```

Việc dùng `printf` thay vì editor giúp mô tả chính xác bytes được ký. Nếu editor đổi LF sang CRLF, đổi encoding hoặc thêm BOM, bytes sẽ khác. Trong một ứng dụng ký file PDF/Word/XML, cũng phải xác định **đúng biểu diễn byte** được ký.

## 4. Tạo PKCS12 KeyStore và xem entry

Từ thư mục lab, chạy:

```bash
keytool -genkeypair \
  -alias docsign \
  -keyalg RSA \
  -keysize 3072 \
  -sigalg SHA256withRSA \
  -validity 30 \
  -storetype PKCS12 \
  -keystore signer.p12 \
  -dname "CN=Document Signing Lab, O=Training, C=VN" \
  -ext "KU=digitalSignature"
```

`keytool` sẽ yêu cầu nhập và xác nhận password tương tác. Ví dụ cố ý **không** dùng `-storepass`, `-keypass` hay biến môi trường để ghi password vào script, process list hoặc lịch sử lệnh. Với KeyStore PKCS12 do `keytool` tạo, khóa và store thường được bảo vệ bằng cùng password; một nguồn PKCS12/provider khác có thể cần xử lý entry password riêng.

Ý nghĩa cấu hình:

- `-genkeypair`: tạo cặp public/private key và chứng thư self-signed phục vụ thử nghiệm.
- `-alias docsign`: định danh entry. Khi lấy private key phải dùng đúng alias.
- `-keyalg RSA -keysize 3072`: chọn RSA 3072 bit cho lab; không chọn số bit theo mặc định không kiểm soát.
- `-sigalg SHA256withRSA`: thuật toán để **ký chứng thư self-signed khi sinh keypair**; về sau chương trình cũng chọn `SHA256withRSA` để ký **tài liệu**, nhưng đây là hai chữ ký khác nhau.
- `-validity 30`: thời hạn chứng thư 30 ngày. Không tự động tạo timestamp hoặc chứng minh thời điểm ký tài liệu.
- `-storetype PKCS12`: chỉ rõ kiểu keystore, tránh dựa vào cấu hình mặc định JDK.
- `-dname`: Distinguished Name ghi trong chứng thư thử nghiệm; người ký tự khai không làm danh tính đáng tin.
- `-ext KU=digitalSignature`: khai báo mục đích sử dụng key trong chứng thư; để triển khai thực tế phải kiểm tra certificate profile/policy áp dụng, không tự dùng self-signed lab certificate làm identity đã xác thực.

**Không dùng khóa thử nghiệm để ký phát hành thật.** Trong production, private key được tạo/lưu theo chính sách chuyên biệt (hardware security module, managed key service hoặc keystore được quản trị), chứng thư được cấp/xác thực phù hợp, có kiểm toán và quy trình thu hồi/xoay vòng.

### 4.1. Xác nhận alias và entry

```bash
keytool -list -v \
  -keystore signer.p12 \
  -storetype PKCS12 \
  -alias docsign
```

Tìm các thông tin quan trọng trong output thay vì đối chiếu nguyên văn toàn bộ:

- Entry type thường là **`PrivateKeyEntry`**, không phải chỉ `trustedCertEntry`.
- Owner/Subject, Issuer và thời gian hiệu lực của chứng thư. Trong lab, Subject và Issuer cùng trỏ tới self-signed identity.
- Public Key Algorithm/size và Signature algorithm của certificate.
- SHA-256 fingerprint của certificate, KeyUsage, certificate chain length.

Lệnh trên chỉ **liệt kê metadata**. Nó không tạo chữ ký tài liệu và cũng không chứng minh signer được CA đáng tin cấp chứng thư.

### 4.2. Xuất chứng thư công khai thành PEM

```bash
keytool -exportcert \
  -alias docsign \
  -keystore signer.p12 \
  -storetype PKCS12 \
  -rfc \
  -file signer-cert.pem
```

`-rfc` xuất Base64 PEM dạng:

```text
-----BEGIN CERTIFICATE-----
... dữ liệu Base64 ...
-----END CERTIFICATE-----
```

PEM này chứa **public key và thông tin chứng thư**, không phải private key. Không đưa `signer.p12` hay mật khẩu cho bên nhận chỉ để họ xác minh một chữ ký.

Có thể đọc chứng thư vừa xuất mà không mở private key:

```bash
keytool -printcert -v -file signer-cert.pem
```

**Điểm tin cậy quan trọng:** file `signer-cert.pem` do chính bên ký xuất chỉ là một public certificate. Người xác minh phải nhận nó từ nguồn được tin cậy, hoặc đối chiếu SHA-256 fingerprint/public key với thông tin đã biết qua kênh độc lập; không thể chấp nhận bất kỳ certificate nào do người gửi không đáng tin tự đính kèm.

## 5. Chương trình Java 17+ hoàn chỉnh: ký và xác minh chữ ký rời

Tạo file **`DocumentSignatureTool.java`** trong thư mục lab. Chương trình dùng hai lệnh:

```text
sign   <signer.p12> <alias> <document> <signature-file>
verify <signer-cert.pem> <document> <signature-file>
```

**`DocumentSignatureTool.java`**

```java
import java.io.BufferedInputStream;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

public final class DocumentSignatureTool {
    private static final String ALGORITHM = "SHA256withRSA";
    private static final long MAX_SIGNATURE_SIZE = 16 * 1024;

    private DocumentSignatureTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 5 && args[0].equals("sign")) {
            sign(
                Path.of(args[1]),
                args[2],
                Path.of(args[3]),
                Path.of(args[4])
            );
            return;
        }

        if (args.length == 4 && args[0].equals("verify")) {
            boolean valid = verify(
                Path.of(args[1]),
                Path.of(args[2]),
                Path.of(args[3])
            );

            System.out.println("verification=" + (valid ? "VALID" : "INVALID"));
            if (!valid) {
                System.exit(1);
            }
            return;
        }

        System.err.println(
            "Usage:\n" +
            "  sign <pkcs12> <alias> <document> <signature>\n" +
            "  verify <certificate.pem> <document> <signature>"
        );
        System.exit(2);
    }

    private static void sign(
        Path storeFile, String alias, Path document, Path signatureFile
    ) throws Exception {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException(
                "Use an interactive terminal to enter the PKCS12 password"
            );
        }

        char[] password = console.readPassword("PKCS12 password: ");
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("PKCS12 password is required");
        }

        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(storeFile)) {
                store.load(in, password);
            }

            if (!store.isKeyEntry(alias)) {
                throw new IllegalArgumentException("No key entry: " + alias);
            }

            Key key = store.getKey(alias, password);
            if (!(key instanceof PrivateKey privateKey)) {
                throw new IllegalArgumentException(
                    "Entry is not a private key: " + alias
                );
            }
            if (!"RSA".equalsIgnoreCase(privateKey.getAlgorithm())) {
                throw new IllegalArgumentException(
                    "This demo requires an RSA private key"
                );
            }
            if (!(store.getCertificate(alias) instanceof X509Certificate)) {
                throw new IllegalArgumentException(
                    "X.509 certificate missing for alias: " + alias
                );
            }

            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            updateFromFile(signer, document);

            byte[] rawSignature = signer.sign();
            Files.write(
                signatureFile,
                rawSignature,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
            System.out.println("signature=CREATED");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static boolean verify(
        Path certificatePem, Path document, Path signatureFile
    ) throws Exception {
        X509Certificate certificate;
        try (InputStream in = Files.newInputStream(certificatePem)) {
            certificate = (X509Certificate) CertificateFactory
                .getInstance("X.509")
                .generateCertificate(in);
        }

        if (!"RSA".equalsIgnoreCase(
                certificate.getPublicKey().getAlgorithm())) {
            throw new IllegalArgumentException(
                "This demo requires an RSA public key"
            );
        }

        long size = Files.size(signatureFile);
        if (size <= 0 || size > MAX_SIGNATURE_SIZE) {
            throw new IllegalArgumentException(
                "Unexpected signature file size"
            );
        }

        Signature verifier = Signature.getInstance(ALGORITHM);
        verifier.initVerify(certificate.getPublicKey());
        updateFromFile(verifier, document);

        byte[] rawSignature = Files.readAllBytes(signatureFile);
        return verifier.verify(rawSignature);
    }

    private static void updateFromFile(
        Signature engine, Path document
    ) throws IOException, SignatureException {
        try (InputStream in = new BufferedInputStream(
                Files.newInputStream(document))) {
            byte[] buffer = new byte[8192];
            int count;

            while ((count = in.read(buffer)) != -1) {
                if (count > 0) {
                    engine.update(buffer, 0, count);
                }
            }
        }
    }
}
```

### 5.1. Vì sao các thao tác Java ở đúng vị trí?

1. **`KeyStore.load` trước `getKey`:** Keystore là nguồn của private key; alias phải được xác nhận là key entry. Nếu người dùng chỉ có `trustedCertEntry`, sẽ không lấy được private key để ký.
2. **`getKey(alias, password)` trả về kiểu `Key`:** Kiểm tra `instanceof PrivateKey` và thuật toán RSA thay vì cast mù. Một keystore có thể chứa nhiều alias và loại khóa khác nhau.
3. **`Signature.initSign(privateKey)`:** xác lập trạng thái ký bằng private key. Chỉ bên ký thực hiện.
4. **`updateFromFile` đọc stream:** dữ liệu file được đưa vào `Signature.update` theo thứ tự, không cần đưa toàn bộ tài liệu lớn vào heap cùng lúc.
5. **`Signature.sign()`:** trả bytes chữ ký. Tệp `invoice.sig` là **binary**, không được sửa qua text editor.
6. **`Files.write(..., CREATE_NEW)`:** chủ động từ chối ghi đè chữ ký cũ. Nếu muốn ký lại trong bài lab, dùng tên tệp kết quả mới hoặc xóa bản lab cũ một cách có chủ đích.
7. **`CertificateFactory.generateCertificate`:** nhận chứng thư PEM độc lập, không cần keystore chứa private key.
8. **`initVerify(publicKey)` + `update` + `verify(bytes)`:** dùng đúng public key, đúng document bytes và chữ ký tương ứng.
9. **`MAX_SIGNATURE_SIZE`:** đây là giới hạn input đơn giản cho file chữ ký thường rất nhỏ, tránh việc `readAllBytes` đọc tệp tùy ý lớn. Production cần kiểm soát kích thước mọi input, không chỉ signature.
10. **`Arrays.fill(password, '\0')`:** giảm thời gian password tồn tại trong chính mảng ứng dụng. Nó không cam kết xóa mọi bản sao nội bộ của provider hoặc JVM khỏi bộ nhớ.
11. **`Console.readPassword`:** tránh truyền password qua `argv` và tránh echo ký tự nhập. Nếu chạy trong IDE hoặc terminal không có console, chương trình cố ý từ chối; trong dịch vụ thật nên tích hợp secret manager hoặc hardware-backed key provider.

**Đây là một công cụ demo mật mã, chưa phải dịch vụ xác thực chứng thư đầy đủ.** Mục đích của lệnh `verify` là trả lời: “Những bytes này có khớp chữ ký tương ứng với public key trong certificate đã đưa vào không?”. Nó **không** mặc định xây dựng đường dẫn chứng thư PKIX, đối chiếu trusted publisher, kiểm tra thu hồi hay timestamp.

### 5.2. Vì sao không cho verifier dùng `signer.p12`?

Verifier không có lý do đọc private key. Nếu bên ký gửi cả `signer.p12` và password cho bên nhận, private key có thể bị lộ và bất kỳ ai có khóa đó có thể tạo chữ ký mới. Tách public certificate ra khỏi signer keystore còn giúp cô lập trách nhiệm: hệ thống ký giữ vật liệu bí mật, hệ thống xác minh chỉ giữ khóa công khai **đã được chấp thuận**.

## 6. Biên dịch, ký và xác minh bản đúng

Sau khi tạo `DocumentSignatureTool.java`, chạy:

```bash
javac DocumentSignatureTool.java
java DocumentSignatureTool sign signer.p12 docsign invoice.txt invoice.sig
```

Chương trình yêu cầu password của `signer.p12` trong terminal. Sau khi nhập đúng password và ký thành công, chương trình in **dòng stdout sau prompt**:

```text
signature=CREATED
```

Tệp `invoice.sig` đã được tạo. Có thể dùng `ls -l` hoặc công cụ kiểm tra kích thước để xác nhận nó tồn tại; chữ ký RSA 3072 bit có độ dài **384 byte** (3072 / 8), không phụ thuộc kích thước tài liệu trong ví dụ.

Xác minh:

```bash
java DocumentSignatureTool verify signer-cert.pem invoice.txt invoice.sig
```

**Stdout chính xác:**

```text
verification=VALID
```

Lệnh thành công với exit code `0`. Bên nhận **không** cần nhập password và không cần file `signer.p12`. Tuy nhiên, ở bước này certificate là self-signed do chính lab tạo. Kết quả `VALID` cho thấy sự nhất quán giữa dữ liệu, chữ ký và public key **được cung cấp**, chứ chưa xác nhận danh tính tác giả trong thế giới thực.

### 6.1. Thử sửa nội dung: cùng certificate và signature, file khác

Tạo bản thay đổi **số tiền** nhưng giữ cùng cấu trúc:

```bash
printf 'Invoice ID: INV-42\nAmount: 900 USD\n' > invoice-modified.txt
java DocumentSignatureTool verify \
  signer-cert.pem invoice-modified.txt invoice.sig
```

**Stdout chính xác:**

```text
verification=INVALID
```

Lệnh trả exit code `1` theo logic trong `main`. Điều này chứng minh tính nhạy với byte được ký: bên nhận không cần biết “số tiền hợp lệ là bao nhiêu” để phát hiện tài liệu đã khác với bản mà khóa ký tương ứng đã ký.

Nếu chỉ sửa khoảng trắng, thay LF bằng CRLF, chỉnh Unicode encoding hoặc thêm một ký tự vào bản gốc, phép xác minh cũng thất bại nếu bytes khác nhau. Có những định dạng tài liệu cần **canonicalization** (ví dụ XML Signature) hoặc chuẩn ký chuyên biệt (ví dụ PAdES/PDF) để người nhận hiểu chính xác phần nào được ký. Tool lab này **không** tạo XMLDSig/PAdES và không đưa signature vào nội bộ tài liệu.

### 6.2. Thử sai public key

Để thử nghiệm, dùng một keystore thứ hai có cặp RSA khác. Chỉ dùng trong lab:

```bash
keytool -genkeypair \
  -alias other \
  -keyalg RSA \
  -keysize 3072 \
  -sigalg SHA256withRSA \
  -validity 30 \
  -storetype PKCS12 \
  -keystore other.p12 \
  -dname "CN=Other Lab Signer, O=Training, C=VN" \
  -ext "KU=digitalSignature"

keytool -exportcert \
  -alias other \
  -keystore other.p12 \
  -storetype PKCS12 \
  -rfc \
  -file other-cert.pem

java DocumentSignatureTool verify \
  other-cert.pem invoice.txt invoice.sig
```

**Stdout nếu hai cặp khóa độc lập như kỳ vọng:**

```text
verification=INVALID
```

Chữ ký không xác minh được bằng public key khác. Đây cũng là lý do bên nhận phải xác định **public key nào mới được phép ký** trước khi quyết định nhận tài liệu.

Không dùng cách “thử mọi certificate được gửi kèm cho đến khi có cái trả true” rồi tuyên bố sender đáng tin. Kẻ tấn công hoàn toàn có thể tạo một cặp khóa mới, ký tài liệu tự sửa và gửi kèm chứng thư tương ứng; bước mật mã sẽ `VALID` với cặp khóa **của kẻ đó**.

### 6.3. Thử lỗi dữ liệu chữ ký hoặc password

- Xóa `invoice.sig` rồi verify: `Files.size` gây lỗi `NoSuchFileException`, khác với `verification=INVALID`. Đây là lỗi input thiếu tệp.
- Đưa file có kích thước `0` hoặc hơn 16 KiB vào chỗ `invoice.sig`: chương trình ném `IllegalArgumentException` vì không đúng điều kiện bảo vệ input.
- Nhập sai password `signer.p12`: việc tải keystore hoặc khôi phục key có thể thất bại. Đó là **lỗi vận hành credential**, không phải tài liệu “chữ ký sai”.
- Dùng alias không có private key: chương trình báo `No key entry`, không cố ký bằng chứng thư công khai.
- Ký lại với output filename đã tồn tại: `CREATE_NEW` từ chối ghi đè; tránh vô tình thay chữ ký đã phát hành.

Trong API production, nên phân loại rõ `VALID`, `INVALID_SIGNATURE`, `INVALID_INPUT`, `UNTRUSTED_SIGNER`, `EXPIRED_CERTIFICATE` và `OPERATIONAL_ERROR`. Đừng gộp một exception I/O thành “chữ ký hợp lệ/không hợp lệ”.

## 7. Nhận diện rõ điều mà demo **chưa** kiểm tra

Một signature có thể `VALID` về mặt toán học mà hệ thống vẫn **phải từ chối tài liệu**.

| Câu hỏi bảo mật | Lệnh `verify` của demo kiểm tra? | Bước cần thêm |
| --- | --- | --- |
| Đúng byte tài liệu, chữ ký và public key khớp nhau? | **Có** | Kiểm tra mọi byte được ký và thuật toán cố định. |
| Certificate có thuộc đúng đối tác được phép ký không? | **Không** | Pin public key/certificate fingerprint qua kênh độc lập hoặc trust policy được quản lý. |
| Chuỗi chứng thư tới trust anchor được chấp nhận? | **Không** | PKIX `CertPathValidator` hoặc trust service phù hợp. |
| Certificate còn hạn tại thời điểm kiểm tra hay thời điểm ký được chứng minh? | **Không** | Xác minh `notBefore`/`notAfter`, timestamp và chính sách thời gian. |
| Chứng thư/key đã bị thu hồi hoặc compromise chưa? | **Không** | Chính sách CRL/OCSP và xử lý không truy cập được nguồn thu hồi. |
| KeyUsage/ExtendedKeyUsage và certificate policy phù hợp với việc ký tài liệu? | **Không** | Kiểm tra profile chứng thư, ứng dụng và chính sách của tổ chức. |
| Tài liệu được đúng người dùng phê duyệt, đúng hợp đồng? | **Không** | Authorization workflow, audit, ràng buộc ID/version/context, quy trình phê duyệt. |
| Tài liệu không tiết lộ bí mật hoặc chứa nội dung độc hại? | **Không** | Kiểm tra nội dung, định dạng, xử lý file không tin cậy. |
| File tên `invoice.txt` tương ứng đúng hóa đơn/đơn hàng? | **Không** | Ký cả metadata ngữ cảnh trong một cấu trúc được định nghĩa rõ. |
| File đã được ký vào một thời điểm cụ thể? | **Không** | Trusted timestamp/token và kiểm tra TSA, không dựa vào mtime của file. |

### 7.1. Hai mô hình tin cậy của verifier

**Mô hình A – Pin khóa/chứng thư:** với một hệ thống có nhà phát hành cố định, xác minh trước fingerprint/public key từ một danh sách được quản lý (có kênh thay đổi và thu hồi riêng). Sau đó mới thực hiện signature verification. Nếu certificate được gửi cùng file nhưng fingerprint không có trong danh sách, **từ chối dù `Signature.verify(...)` trả true**.

**Mô hình B – PKIX/CA:** với tổ chức có hệ thống PKI, kiểm tra chuỗi từ leaf certificate qua các intermediate CA đến trust anchor được bên nhận tin cậy, sau đó xác định subject/identities, key usage, certificate policy, thời hạn, thu hồi và quy tắc ký tài liệu. Tên CN trong chứng thư **không tự đủ** để kết luận bên ký là ai; phải ràng buộc identity với business principal mà ứng dụng cho phép.

Không tự bổ sung `cert.checkValidity()` rồi cho rằng đã giải quyết tất cả: API đó chỉ kiểm tra khoảng thời gian hiệu lực của chứng thư so với một thời điểm, **không** xây trust chain hoặc kiểm tra thu hồi. Ngược lại, chữ ký lịch sử có timestamp đáng tin có thể cần đánh giá chứng thư **tại thời điểm đã ký**, không chỉ giờ hiện tại.

### 7.2. Sai lầm khi người ký kiểm soát cả certificate và chữ ký

Hình dung hai tình huống:

- Người ký đúng ký `invoice.txt`, bên nhận đã pin đúng certificate của người ký. Khi tệp thay đổi, signature thất bại.
- Kẻ giả mạo tự tạo `attacker.p12`, ký một bản `invoice.txt` giả, xuất `attacker-cert.pem` và gửi cả ba tệp. Nếu bên nhận lấy certificate từ chính gói không tin cậy rồi chỉ gọi `verify`, kết quả có thể **VALID**. Không có phép mật mã nào trong ví dụ tự chứng minh đây là signer được phê duyệt.

Chính sách xác định “ai có quyền ký tài liệu nào” phải được đặt **ngoài** hàm kiểm tra chữ ký thuần túy. Đây là điểm khác biệt cốt lõi giữa “crypto verified” và “business approved”.

## 8. Chi tiết bytes: ký văn bản, PDF, XML và dữ liệu có cấu trúc

Chương trình dùng `Files.newInputStream(document)`, chuyển bytes lần lượt sang `Signature.update`. Nó không parse nội dung, không chuẩn hóa dòng, không bóc header, không ký tên tệp. Điều này có ưu điểm: quy tắc đơn giản và không phụ thuộc độ lớn tài liệu. Nhưng có những giới hạn:

- **Đổi một byte thì chữ ký không khớp:** UTF-8 với BOM/không BOM, LF/CRLF, thứ tự thuộc tính JSON hay khoảng trắng được tính như các bytes khác nhau.
- **Không ký ý nghĩa của tài liệu:** hai biểu diễn khác nhau nhưng hiển thị cùng thông tin vẫn là hai payload khác nhau. Hai file bytes giống nhau nhưng được dùng cho hai giao dịch khác nhau có thể cần thêm context vào chữ ký.
- **Không bao trùm metadata bên ngoài file:** file path, tên người dùng, nội dung HTTP headers, tenant ID, document ID, revision, MIME type hoặc thời điểm phát hành không được ký trừ khi đưa vào bytes được bảo vệ.
- **Không phải digital signature format tương thích mọi phần mềm:** `invoice.sig` là một chuỗi bytes raw theo contract `SHA256withRSA`; trình đọc PDF/Office thông thường không tự nhận diện nó.
- **Không mã hóa:** thông tin trong tệp vẫn công khai. Quyền đọc và confidentiality là bài toán riêng.

### 8.1. Nếu cần ký cả ngữ cảnh nghiệp vụ

Giả sử cùng một tệp `invoice.txt` được gửi cho hai tenant. Nếu chỉ ký nội dung file mà không ràng buộc tenant/document ID, hệ thống có nguy cơ dùng lại chữ ký hợp lệ ở ngữ cảnh khác (**replay hoặc substitution**).

Một thiết kế production có thể tạo **manifest đã canonicalize** chứa:

```json
{
  "format": "document-signature-v1",
  "algorithm": "SHA256withRSA",
  "documentId": "INV-42",
  "tenantId": "tenant-01",
  "documentSha256": "lowercase-hex-of-document-bytes",
  "signerKeyId": "approved-key-2026"
}
```

Đây là **minh họa schema**, chưa phải payload để đưa trực tiếp vào `Signature.update`: phải định nghĩa canonicalization/serialization **chính xác** và ký bytes canonical của toàn manifest, kèm theo kiểm tra hash của tài liệu. Bên nhận phải kiểm tra manifest đã được ký, khớp tenant/document/revision expected, không chỉ giải mã JSON rồi tin trường `algorithm` hay `signerKeyId` do người gửi cung cấp.

Hoặc dùng tiêu chuẩn chữ ký có profile chính thức phù hợp môi trường: CMS/PKCS#7, JWS/COSE, XMLDSig/XAdES, PDF signature/PAdES. Mỗi tiêu chuẩn có yêu cầu encoding, canonicalization, signature coverage, trust và timestamp riêng. Đừng tự tuyên bố raw `.sig` của bài này là một trong các chuẩn đó.

### 8.2. Stream dữ liệu và tính bất biến khi ký

Vì JCA `Signature` có thể nhận từng chunk qua `update`, bộ nhớ tiêu thụ của tool lab không tăng tuyến tính theo kích thước tài liệu. Tuy nhiên:

- Ký trên một file đang được tiến trình khác sửa có thể tạo **race condition**: bytes được đọc không còn là bytes được gửi sau đó. Trong production, lấy immutable snapshot/content-addressed artifact trước khi ký.
- Nếu tài liệu được stream từ network, phải kiểm soát kích thước và nguồn dữ liệu, tránh byte stream vô hạn.
- Với số lượng lớn file, ký một tệp ZIP không đồng nghĩa mỗi file con được đối tác hiểu/kiểm tra như một artifact độc lập; chỉ rõ phạm vi bytes.
- Khi phân phối, lưu hash toàn file và audit metadata ở hệ thống đáng tin để phát hiện thay nhầm phiên bản hoặc cung cấp bản không đúng.

## 9. Thuật toán, định dạng chữ ký và khả năng tương thích

Trong lab, signer và verifier đều dùng `SHA256withRSA` với RSA-3072. Không đổi tên thuật toán ở một bên rồi mong bên kia chấp nhận. Các lựa chọn khác cũng được JCA hỗ trợ trong phạm vi provider:

| Cặp khóa | Tên `Signature` thường dùng | Lưu ý |
| --- | --- | --- |
| RSA | `SHA256withRSA` | Phù hợp demo; scheme phổ biến RSASSA-PKCS1-v1_5 trên JDK thông dụng. |
| RSA | `RSASSA-PSS` | Cần cấu hình/ghi lại đồng nhất digest, MGF1, salt length và trailer; không thể suy từ tên tệp `.sig`. |
| EC | `SHA256withECDSA` | KeyStore phải chứa khóa EC tương thích; cách encode chữ ký ECDSA cần thống nhất khi liên thông hệ thống. |
| Ed25519 | `Ed25519` | Thuật toán có cách xử lý thông điệp riêng, không dùng `SHA256withEd25519` tùy ý. |

**Không thay khóa mà quên thay thuật toán.** Nếu tạo EC KeyStore rồi chạy code cố định `SHA256withRSA`, chương trình từ chối nhờ kiểm tra `privateKey.getAlgorithm()`. Với API production, lựa chọn thuật toán nên được quản lý qua chính sách server-side theo key ID, không nhận trực tiếp từ payload không đáng tin để tránh downgrade/confusion.

RSA 3072-bit không có nghĩa mật mã “tương đương 3072 bit an toàn”; độ mạnh an toàn của RSA phụ thuộc bài toán toán học, scheme, provider, quy tắc padding và các yếu tố khác. Không dùng SHA-1, MD5 hoặc những thuật toán yếu/đã bị chính sách nền tảng vô hiệu hóa chỉ để tương thích với dữ liệu cũ.

Chữ ký RSA trong demo dài 384 bytes dù tài liệu là 36 bytes hay 10 MB. Chữ ký không chứa bản sao tài liệu; cần cung cấp **cả tài liệu** khi xác minh.

## 10. Quản lý private key trong PKCS12 KeyStore

### 10.1. Không tiết lộ mật khẩu hoặc private key

- Không commit `signer.p12`, password, private key PEM, file backup chứa private key vào Git, image hoặc artifact phát hành.
- Đặt quyền file theo người sở hữu, giới hạn bản sao và sao lưu được mã hóa. Với Linux có thể dùng OS ACL/permissions thích hợp; với Windows dùng ACL và secret-management facilities.
- Không truyền password bằng `-storepass "..."`, `-keypass "..."`, command-line arguments hoặc ghi trực tiếp trong source. Chúng có thể lọt vào shell history, process listing, log và telemetry.
- Không log giá trị `Key`, passwords hoặc toàn bộ keystore. Không dùng email/chat để gửi password cùng file keystore.
- Nếu chạy trong server không có interactive console, dùng secret manager, provider hỗ trợ protected authentication path, HSM/PKCS#11 hoặc cơ chế cấp credential có kiểm soát. Đừng thêm fallback hard-coded password chỉ để chạy demo qua IDE.
- Khi nghi ngờ khóa lộ, **không thể thu hồi các chữ ký đã được tạo một cách tùy ý**; phải xử lý sự cố theo chính sách, thu hồi chứng thư nếu có PKI, thay khóa, cập nhật trust policy và đánh giá chữ ký cũ theo thời điểm chứng minh được.

Dùng `char[]` cho password giảm một số rủi ro so với `String` immutable tồn tại lâu; việc xóa mảng trong `finally` là **best effort**, không bảo đảm JVM/provider đã xóa toàn bộ bản sao. Với ứng dụng nhạy cảm cao, bảo vệ khóa qua HSM/KMS hoặc signing service có audit sẽ phù hợp hơn so với giải mã private key vào heap của tiến trình ứng dụng.

### 10.2. Password không thay thế xác thực danh tính

Người có `signer.p12` và password có thể dùng private key tạo chữ ký. Nhưng **bên nhận không thể suy ra ai đã nhập password** chỉ từ chữ ký. Cần quy trình kiểm soát ai có quyền gọi signing service, approval workflow, audit, dual control nếu cần, hardware-backed key access, và cách công bố signer identity đáng tin.

Password bảo vệ keystore **khi lưu trữ**, không biến key thành không thể bị lấy khỏi bộ nhớ sau khi được khôi phục. Nếu OS process đã bị xâm nhập và có quyền gọi signer, attacker có thể yêu cầu ký hoặc đọc nội dung tùy khả năng của ứng dụng. Phân quyền và isolation vẫn cần thiết.

### 10.3. Key rollover, hiệu lực và lưu trữ bằng chứng

- Mỗi key/certificate nên có ID, thời hạn, trạng thái và chính sách sử dụng rõ ràng.
- Khi xoay vòng key, cập nhật verifier để biết **key nào được phép ký phiên bản/tài liệu nào**; đừng xoá trust của mọi chữ ký cũ nếu cần lưu hồ sơ.
- Chữ ký demo **không có trusted timestamp**. Nếu muốn chứng minh chữ ký tồn tại khi certificate còn hiệu lực, phải tích hợp TSA hoặc chuẩn chữ ký hỗ trợ timestamp và xác minh token; đặt ngày trong file không thay thế TSA.
- Chứng thư có thể hết hạn, bị thu hồi hoặc sai KeyUsage/EKU. Việc `Signature.verify` trả true với public key vẫn có thể xảy ra; trust policy phải đánh giá riêng.
- Đừng dùng một cặp private key chung cho tất cả tenant hoặc mọi loại nghiệp vụ nếu yêu cầu audit/phân quyền khác nhau.

## 11. Triển khai thực tế: tách thao tác ký, kiểm tra và phê duyệt

Một quy trình sản xuất an toàn hơn thường có các thành phần:

```text
   Trusted business workflow
      │ xác thực người yêu cầu và quyền ký document ID
      ▼
   Create immutable document snapshot + metadata
      │ hash / version
      ▼
   Authorized signing service
      │ key ID + HSM/PKCS#11/KMS/PKCS12 theo policy
      │ audit request / approval / algorithm
      ▼
   Signed envelope (document + signature + signer metadata)
      │
      ▼
   Recipient verify service
      ├── Decode/validate envelope schema and size
      ├── Check expected document/tenant/context
      ├── Resolve allowed public key / validate PKIX chain
      ├── Check certificate validity / revocation / usage / TSA policy
      └── Cryptographic Signature.verify(bytes) -> accept/reject
```

Lưu ý **thứ tự quyết định**: hệ thống phải xác định signer/key có nằm trong trust policy không; không dùng `Signature.verify(...)` trả true làm lối tắt cho authorization. Nếu signer không được phê duyệt thì chứng thư/chữ ký “khớp” vẫn không cho phép chấp nhận tài liệu.

Với một dịch vụ nhiều tenant, không dùng một certificate bất kỳ từ request làm khóa được phép ký cho mọi tenant. Resolve allowed signer theo tenant, loại tài liệu, mục đích ký, phiên bản và thời điểm thích hợp; ghi audit nhưng không đưa private key vào log.

### 11.1. Gợi ý cấu trúc kết quả API

Một dịch vụ verify có thể trả những trạng thái có ý nghĩa rõ:

| Trạng thái | Ý nghĩa |
| --- | --- |
| `VALID_TRUSTED` | Chữ ký đúng và toàn bộ trust/policy liên quan đều đạt. |
| `INVALID_SIGNATURE` | Chữ ký không khớp bytes và public key dự kiến. |
| `UNTRUSTED_SIGNER` | Chữ ký có thể đúng toán học nhưng signer/key không nằm trong danh sách cho phép. |
| `INVALID_CERTIFICATE` | Certificate chain, thời hạn, mục đích sử dụng hoặc thu hồi không đạt. |
| `UNSUPPORTED_ALGORITHM` | Thuật toán/parameters nằm ngoài policy hoặc provider không hỗ trợ. |
| `MALFORMED_INPUT` | Thiếu/tệp sai format/kích thước/manifest không hợp lệ. |
| `VERIFICATION_ERROR` | Lỗi hệ thống, I/O, provider, kết nối OCSP/TSA tùy chính sách xử lý. |

**Không tự động coi lỗi OCSP/network là chứng thư “không bị thu hồi”.** Chính sách fail-closed hay chiến lược xử lý gián đoạn phải được thiết kế theo yêu cầu nghiệp vụ và mức rủi ro, có phân biệt giữa “không biết” và “đã kiểm tra hợp lệ”.

## 12. Lỗi thường gặp và cách tìm nguyên nhân

| Hiện tượng | Nguyên nhân khả dĩ | Hướng kiểm tra |
| --- | --- | --- |
| `FileNotFoundException` / `NoSuchFileException` khi load keystore hoặc document | Đường dẫn sai hoặc file thiếu | Kiểm tra working directory và đường dẫn tuyệt đối/tương đối; không tự tìm keystore khác. |
| `IOException` hoặc `UnrecoverableKeyException` khi load/getKey | Sai store/entry password hoặc PKCS12 không tương thích/provider khác | Xác nhận keystore type, password, alias và nguồn file; không log password. |
| `No key entry: docsign` | Alias sai hoặc chỉ chứa certificate | `keytool -list -v`, xác minh `PrivateKeyEntry`. |
| `This demo requires an RSA private key` | KeyStore chứa EC/Ed25519/khóa không phải RSA | Chọn thuật toán tương thích hoặc dùng đúng RSA test key. |
| `signature=CREATED` nhưng verify báo `INVALID` | File/cert/signature không cùng cặp hoặc một file đã thay byte | Đối chiếu hash, phiên bản, certificate fingerprint và signature output. |
| `SignatureException` khi verify | Signature bytes sai định dạng/độ dài hoặc provider từ chối dữ liệu | Kiểm tra encoding và truyền binary đúng; phân biệt lỗi format với mismatch. |
| `Unexpected signature file size` | Signature thiếu/rỗng hoặc input không phù hợp policy của ví dụ | Chặn input quá lớn, kiểm tra file nguồn. |
| `No Console` khi chạy trong IDE/CI | `System.console()` trả `null` | Chạy terminal tương tác cho lab; production dùng secret manager/secure key provider. |
| Bản PDF ký bằng raw `.sig` không hiện biểu tượng “signed” trong PDF reader | Raw detached signature không phải PAdES/PDF signature object | Dùng đúng standard/profile chữ ký của loại tài liệu. |
| Mã verify trả true dù cert tự ký | Pure signature verification không đồng nghĩa PKIX trust | Validate certificate/identity theo trust policy độc lập. |
| Signature cũ vẫn verify toán học sau khi cert hết hạn | Phép toán dùng public key, không tự xem chứng thư | Xác định thời gian và chính sách PKIX/timestamp/thu hồi. |

### 12.1. Không nhầm chữ ký, chữ ký JAR và chữ ký chứng thư

Trong bài có thể có ba chữ ký khác nhau:

1. **Chữ ký tự thân của X.509 certificate:** được tạo khi `keytool -genkeypair` sinh chứng thư self-signed; dùng để xác minh tính nhất quán của chứng thư, không chứng minh publisher được bên nhận tin.
2. **Chữ ký tài liệu `invoice.sig`:** được Java `Signature.sign()` tạo trên bytes của `invoice.txt`, chính là thứ `DocumentSignatureTool.verify` kiểm tra.
3. **JAR signing (`jarsigner`):** là cơ chế ký nội dung entry của JAR và metadata `META-INF`. Không được xem `invoice.sig` tự động như một JAR signature hoặc ngược lại.

Nếu muốn ký file XML/PDF theo chuẩn tài liệu của đối tác, cần workflow riêng cho định dạng chuẩn, canonicalization, các thuộc tính được ký và chứng thư/timestamp phù hợp.

## 13. Checklist hoàn thành lab

Trước khi kết thúc, tự kiểm chứng:

- [ ] `keytool -list -v` cho thấy alias `docsign` là `PrivateKeyEntry` RSA, KeyStore loại `PKCS12`.
- [ ] `signer.p12` không nằm trong Git hoặc thư mục public; password không được ghi vào script, command-line hay log.
- [ ] `signer-cert.pem` được xuất từ **đúng alias**, là certificate công khai, không có private key.
- [ ] `DocumentSignatureTool.java` biên dịch với JDK 17+.
- [ ] `java DocumentSignatureTool sign ...` tạo `invoice.sig` binary và in `signature=CREATED`.
- [ ] Verify trên tài liệu gốc dùng PEM certificate in `verification=VALID`, exit code `0`.
- [ ] Verify trên `invoice-modified.txt` in `verification=INVALID`, exit code `1`.
- [ ] Verify với chứng thư khóa khác cho `INVALID`.
- [ ] Giải thích được vì sao một file signature khớp với cert **không tự** xác thực signer.
- [ ] Biết cần gắn document ID/tenant/context, trust anchor/fingerprint và certificate policy nếu dùng cho nghiệp vụ thật.
- [ ] Biết sự khác nhau giữa PKCS12, X.509, `PrivateKey`, `PublicKey`, `Signature` và hash độc lập.
- [ ] Phân loại được lỗi I/O/password/alias/algorithm với một chữ ký không khớp.

Bài thực hành đã sử dụng **chữ ký rời** nên hai đầu phải thống nhất contract: byte encoding, thuật toán, định dạng tệp chữ ký, public key được chấp nhận và chính sách ngữ cảnh. Chỉ một thay đổi không được đồng bộ giữa hai đầu là đủ khiến kết quả xác minh sai.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Tại sao không lấy `KeyStore.getCertificate(alias).getPublicKey()` rồi gọi `initSign`?

**Trả lời:** `initSign` yêu cầu `PrivateKey`; public key chỉ dùng trong `initVerify`. Chữ ký chỉ tạo được bởi bên giữ private key tương ứng.

**Câu 2.** Nếu `Signature.verify` trả true, hệ thống đã xác thực người ký chưa?

**Trả lời:** Chưa. True chỉ nghĩa là chữ ký khớp bytes thông điệp và public key được cung cấp. Cần xác minh certificate/key nằm trong trust policy, PKIX và/hoặc pinning, identity và quyền nghiệp vụ.

**Câu 3.** Nếu đổi `invoice.txt` từ LF sang CRLF mà nội dung nhìn giống nhau, có còn xác minh được không?

**Trả lời:** Không, vì bytes đã thay đổi; ví dụ ký nguyên bytes mà không canonicalize. Với loại tài liệu cần chuẩn hóa, hai bên phải dùng một chuẩn ký và canonicalization được định nghĩa rõ.

**Câu 4.** File `invoice.sig` có thể gửi công khai không? Có giải mã lại được nội dung invoice từ nó không?

**Trả lời:** Có thể gửi chữ ký công khai; bản thân chữ ký không chứa tài liệu để giải mã. Chữ ký không bảo mật nội dung file; nếu cần bí mật, dùng mã hóa và access control riêng.

**Câu 5.** Tại sao cần PEM certificate ở phía người nhận mà không cần signer.p12?

**Trả lời:** Xác minh dùng public key từ chứng thư. Private key trong PKCS12 chỉ cần ở bên ký. Người nhận phải nhận/duyệt certificate qua trust policy đáng tin.

**Câu 6.** PKCS12 chứa certificate đã hết hạn, nhưng Java API của demo vẫn có thể báo `VALID`. Có mâu thuẫn không?

**Trả lời:** Không. Chức năng demo chưa kiểm tra thời hạn/chuỗi tin cậy. Kiểm tra toán học và quyết định tin cậy là hai bước khác nhau; ứng dụng thực tế cần xử lý certificate validity và timestamp theo chính sách.

**Câu 7.** Nếu người ký tự gửi certificate bất kỳ cùng với chữ ký, bên nhận có nên tin certificate đó không?

**Trả lời:** Không. Người tấn công có thể tự tạo private key, ký file giả và đưa public certificate của chính họ. Cần pin được khóa hoặc kiểm tra chuỗi PKIX và binding với principal được cho phép.

**Câu 8.** Vì sao code dùng `Signature.update(InputStream)` theo từng chunk thay vì `Files.readAllBytes(document)`?

**Trả lời:** Để xử lý file lớn mà không đưa toàn bộ file vào heap một lúc. Vẫn cần giới hạn kích thước/trạng thái input và chống file bị thay đổi giữa quá trình ký và phân phối.

## 15. Tài liệu chuẩn đối chiếu

- Oracle Java SE 17, **KeyStore API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html
- Oracle Java SE 17, **Signature API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/Signature.html
- Oracle Java SE 17, **CertificateFactory API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertificateFactory.html
- Oracle Java SE 17, **X509Certificate API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html
- Oracle Java SE 17, **keytool command**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html
- Oracle Java SE 17, **Standard Algorithm Names Specification**: https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
- IETF **RFC 5280 – X.509 Public Key Infrastructure Certificate and CRL Profile**: https://www.rfc-editor.org/rfc/rfc5280
- IETF **RFC 7292 – PKCS #12: Personal Information Exchange Syntax v1.1**: https://www.rfc-editor.org/rfc/rfc7292

**Kết luận thực hành:** PKCS12 bảo vệ và tổ chức private key; `Signature.initSign/update/sign` tạo chữ ký cho đúng bytes tài liệu; chứng thư công khai cung cấp `PublicKey` cho `initVerify/update/verify`. Một chữ ký đúng chỉ là bước kiểm tra **integrity và sự nhất quán với khóa**, còn quyết định tin người ký và chấp nhận tài liệu cần PKI/trust policy, thời gian, quyền nghiệp vụ và kiểm soát khóa riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-practice-006">23.6 Thực nghiệm KeyAgreement hoặc DHKEM và phân tích phần xác thực còn thiếu</a>

<details>
<summary>Xem chi tiết</summary>

# 23.6 Thực nghiệm KeyAgreement hoặc DHKEM và phân tích phần xác thực còn thiếu

## 1. Bài toán và kết luận phải tự kiểm chứng

Hai chương trình Java kết nối qua một mạng không tin cậy muốn thống nhất khóa phiên để bảo vệ dữ liệu. Chúng có thể dùng **Diffie–Hellman key agreement**, ví dụ X25519 qua `javax.crypto.KeyAgreement`, hoặc một **DH-based Key Encapsulation Mechanism (DHKEM)** như thành phần trong HPKE (RFC 9180).

**Điều dễ nhầm:** cả hai phía tạo được cùng một khóa bí mật **không chứng minh** rằng họ đã giao tiếp với đúng người. Nếu kẻ tấn công chủ động thay khóa công khai trong quá trình trao đổi, họ có thể tạo **hai phiên bí mật riêng**, một với mỗi phía, rồi đọc/sửa/chuyển tiếp thông điệp. Thuật toán DH vẫn chạy chính xác về mặt mật mã, nhưng **xác thực danh tính đối tác đang thiếu**.

Bài thực hành dùng Java 17+ để:

1. Sinh cặp khóa X25519 ngắn hạn cho Alice và Bob; dùng `KeyAgreement` tạo shared secret và dẫn xuất khóa phiên bằng HKDF-HMAC-SHA-256.
2. Mô phỏng attacker thay public key giữa đường (man-in-the-middle, MITM), chứng minh attacker thống nhất được khóa với cả hai phía dù Alice/Bob tưởng đang nói chuyện trực tiếp.
3. Bổ sung khóa danh tính Ed25519 **đã được tin cậy từ trước** và xác minh chữ ký lên bản ghi trao đổi để phát hiện thay thế khóa phía server.
4. Phân biệt **key agreement**, **key derivation**, **key confirmation**, **authentication**, **authorization**, **replay protection** và **forward secrecy**.
5. Liên hệ cơ chế DHKEM/HPKE: ai được xác thực trong Base, PSK, Auth và AuthPSK; điều gì vẫn phải do ứng dụng/protocol giải quyết.

Không cần hệ thống mạng, máy chủ hoặc thư viện bên ngoài: bản thực nghiệm chạy trong một JVM, tạo các vai trò giả lập bằng các cặp khóa riêng. Đây là **thí nghiệm để nhìn thấy ranh giới bảo mật**, không phải một protocol có thể đưa vào production.

## 2. Mô hình đe dọa: kẻ nghe lén và kẻ chủ động khác nhau

Hãy phân biệt hai loại attacker:

- **Passive eavesdropper:** chỉ quan sát byte truyền trên kênh. Với X25519 triển khai đúng, việc biết public key của hai phía không cho phép họ tính shared secret trong điều kiện giả định bảo mật của thuật toán.
- **Active attacker/MITM:** có thể chặn, bỏ, sửa, thay, chèn, chuyển tiếp hoặc phát lại thông điệp trước khi chúng đến peer. Nếu public key được trao đổi qua kênh chưa xác thực, kẻ này có thể thay public key bằng của mình; Diffie–Hellman thuần túy không biết điều đó.

Mô hình hai bên ban đầu:

~~~text
Alice                                         Bob
a = private X25519                           b = private X25519
A = public X25519  -------------------------> nhận A
nhận B             <------------------------- B = public X25519

K_A = X25519(a, B)                           K_B = X25519(b, A)

                         K_A == K_B
~~~

Đây là **thỏa thuận khóa**, chưa chứng minh rằng `B` thực sự thuộc Bob hay `A` thực sự thuộc Alice. Ngay cả khi chuyển khóa thành AES key và dùng AES-GCM, nếu attacker là người cùng chia sẻ khóa với mỗi phía thì AES-GCM bảo vệ các đường truyền đó **với chính attacker**, không xác thực danh tính người ở đầu kia.

Mục tiêu an toàn phải được viết rõ: *Alice xác thực Bob theo danh tính nào? Bob có cần xác thực Alice không? Ai tin cậy khóa danh tính đó? Giao thức chống replay/downgrade ra sao?* Một thí nghiệm chỉ in `true` cho hai khóa bằng nhau chưa trả lời các câu này.

## 3. `KeyAgreement` làm gì trong Java và không làm gì?

Trong Java SE 17, API chính là:

~~~java
// Trích đoạn API, không phải chương trình độc lập.
KeyPairGenerator gen = KeyPairGenerator.getInstance("X25519");
KeyPair myPair = gen.generateKeyPair();

KeyAgreement ka = KeyAgreement.getInstance("X25519");
ka.init(myPair.getPrivate());
ka.doPhase(peerPublicKey, true);
byte[] rawSharedSecret = ka.generateSecret();
~~~

Các lớp thuộc `java.security.KeyPairGenerator` và `javax.crypto.KeyAgreement`, algorithm name `X25519` theo RFC 7748. `doPhase(peerPublicKey, true)` nhận **public key của đối tác** và thực hiện pha cuối trong giao thức hai bên. `generateSecret()` trả byte shared secret (với X25519 là 32 byte). API này:

- **Có:** tạo cặp khóa, tính DH trên private key của mình và public key phía bên kia.
- **Không:** gửi public key qua socket; phân tích/đóng gói frame; xác minh certificate hoặc người dùng; chống thay public key; tạo một giao thức phiên hoàn chỉnh; kiểm tra quyền nghiệp vụ.
- **Không tự đảm bảo:** raw shared secret phù hợp để dùng trực tiếp làm AES key hoặc HMAC key cho một ứng dụng tùy ý. Thông thường phải qua KDF được chuẩn hóa và ràng buộc ngữ cảnh.

Với X25519, các provider tuân chuẩn có thể từ chối peer public key bất hợp lệ/đầu vào cho kết quả all-zero. RFC 7748 nói về kiểm tra all-zero; demo sẽ kiểm tra và từ chối một giá trị shared secret toàn zero theo cách duyệt hết byte. **Đừng tự viết phép nhân đường cong** hoặc tự bỏ qua lỗi của provider.

`PublicKey.getEncoded()` trong JCA thường cho biểu diễn X.509 SubjectPublicKeyInfo của public key; đó không phải mặc định là 32 byte public key thô được dùng trong wire format của một chuẩn khác. Khi truyền qua mạng, giao thức cần định dạng key và parser có bounds rõ ràng. Một public key dù parse đúng hoàn toàn vẫn có thể **do attacker tạo**.

## 4. Tại sao cần HKDF sau Diffie–Hellman?

Gọi `Z = X25519(privateLocal, publicPeer)`. `Z` là secret chung, nhưng còn phải đưa nó vào **key schedule** của protocol để tạo khóa đúng mục đích. Ví dụ ta cần một AES-128 key, nên dẫn xuất đúng 16 byte từ `Z` bằng HKDF:

~~~text
PRK = HKDF-Extract(salt, Z)
K   = HKDF-Expand(PRK, info, 16)
~~~

Theo RFC 5869, HKDF với SHA-256 dùng HMAC-SHA-256 trong cả hai bước. `salt` không nhất thiết bí mật; có thể được thỏa thuận/định nghĩa trong protocol. `info` dùng để **phân miền theo vai trò, phiên, phiên bản, thuật toán và bản ghi trao đổi** (transcript). Khi sản xuất, nên dùng implementation KDF được duy trì bởi thư viện/framework thay vì chế tác một giao thức mật mã mới.

Trong demo, `salt="training-only-HKDF-salt"` là hằng số công khai phục vụ học tập. `info` là một bản ghi có nhãn phiên bản và hai public key với thứ tự/độ dài xác định: client public key trước, server public key sau. HMAC đầu trích xuất thành PRK, HMAC thứ hai xử lý `info || 0x01` và lấy 16 byte đầu cho khóa minh họa. Với một khối đầu ra 16 byte, đây là **HKDF-Expand một block đúng cấu trúc RFC 5869**. Không in `Z` hoặc `K` ra log.

Ràng buộc transcript là **cần thiết nhưng chưa đủ**: attacker có thể tạo một transcript nhất quán riêng cho từng đoạn kết nối. Việc đưa public key vào `info` không tự biến public key đó thành **đã được xác thực**.

## 5. Thành công ở đường truyền trung thực

Alice sinh cặp khóa tạm thời `(a, A)`, Bob sinh `(b, B)`. Alice dùng `a` cùng `B`; Bob dùng `b` cùng `A`.

~~~text
Alice:
  Z_A = X25519(a, B)
  T   = encode("example/v1", A, B)
  K_A = HKDF(Z_A, salt, T)

Bob:
  Z_B = X25519(b, A)
  T   = encode("example/v1", A, B)
  K_B = HKDF(Z_B, salt, T)

Z_A == Z_B  =>  K_A == K_B
~~~

Đây là tính chất cốt lõi của Diffie–Hellman. Vì `A` và `B` có thể gửi công khai, người nghe lén chỉ thấy cặp public key và transcript. Tuy nhiên, trước khi nói rằng “Alice đã bắt tay an toàn với Bob”, ta **chưa có bất kỳ bằng chứng mật mã nào về danh tính Bob**. Danh tính có thể được gắn bằng khóa ký tin cậy, certificate chain, PSK, hoặc một protocol đã xác thực như TLS 1.3.

Điểm thực hành cần ghi nhận: khóa bằng nhau là **functional correctness** của trao đổi DH. Nó không chứng minh xác thực hoặc toàn vẹn kênh công khai.

## 6. Thực nghiệm MITM: attacker tạo hai khóa phiên khác nhau

Mallory chặn thông điệp public key. Thay vì đưa khóa `A` của Alice tới Bob, Mallory đưa public key của chính mình `M_B`; thay vì đưa khóa `B` của Bob tới Alice, Mallory đưa `M_A`. Mallory sở hữu private key tương ứng `m_A` và `m_B`.

~~~text
Alice                  Mallory                         Bob
  |--- A ---------------->|                               |
  |                       |--- M_B ----------------------->|
  |                       |<---------------------------- B |
  |<----------------- M_A-|                               |
  |                       |                               |
  | Z_A = X25519(a, M_A)  | Z_MA = X25519(m_A, A)          |
  |                       | Z_MB = X25519(m_B, B)          | Z_B = X25519(b, M_B)
  |                       |                               |
  | K_A == K_MA           | K_MB == K_B                   |
  |                       |                               |
  |--- encrypted data --->|--- decrypt / change / encrypt ->|
~~~

Alice có chung một khóa với Mallory, Bob có chung một khóa **khác** với Mallory. Mỗi phía có thể sử dụng AEAD, thậm chí làm **key confirmation** bằng MAC thành công trong phiên riêng của mình. Mallory vẫn là đối tác mật mã của từng phiên, và có thể giải mã/chỉnh sửa/tái mã hóa dữ liệu khi đóng vai MITM. Vấn đề không nằm ở việc X25519 “bị phá” mà ở chỗ **không ai chứng minh public key nhận được thuộc peer mong đợi**.

Về nguyên lý, nếu yêu cầu **server-authenticated key exchange**, Alice cần có một khóa xác thực danh tính lâu dài của Bob đã tin cậy qua kênh độc lập, sau đó yêu cầu Bob ký bản ghi trao đổi bao gồm các ephemeral public key. Nếu attacker thay `B` bằng `M_A`, chữ ký thật của Bob không khớp transcript của Alice; attacker không có private key danh tính của Bob để tạo chữ ký thay thế.

## 7. Demo Java 17+ độc lập: X25519 + HKDF + MITM + Ed25519

Đoạn sau chỉ dùng Java SE chuẩn. Mọi vai trò chạy trong bộ nhớ; không tạo socket, không gửi dữ liệu nhạy cảm. Nó tạo:

- `alice` và `bob`: các cặp X25519 tạm thời cho phiên trung thực.
- `malloryToAlice` và `malloryToBob`: các cặp X25519 do attacker giữ để thay public key.
- `trustedBobIdentity`: cặp Ed25519 danh tính của server, **giả định Alice đã pin khóa công khai này bằng kênh tin cậy từ trước**.
- `attackerIdentity`: cặp Ed25519 khác do attacker tự tạo. Dù chữ ký của attacker đúng với khóa attacker, nó **không được chấp nhận** khi Alice xác minh theo khóa công khai Bob đã pin.

~~~java
// File: X25519IdentityLab.java
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class X25519IdentityLab {
    static byte[] shared(PrivateKey mine, PublicKey peer) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(mine);
        agreement.doPhase(peer, true);
        byte[] secret = agreement.generateSecret();
        int nonZero = 0;
        for (byte b : secret) nonZero |= b;
        if (nonZero == 0) throw new IllegalStateException("Invalid shared secret");
        return secret;
    }

    static byte[] transcript(PublicKey clientEphemeral,
                             PublicKey serverEphemeral) {
        byte[] label = "java-learning/23.6/example/v1".getBytes(StandardCharsets.UTF_8);
        byte[] c = clientEphemeral.getEncoded();
        byte[] s = serverEphemeral.getEncoded();
        ByteBuffer buffer = ByteBuffer.allocate(12 + label.length + c.length + s.length);
        buffer.putInt(label.length).put(label);
        buffer.putInt(c.length).put(c);
        buffer.putInt(s.length).put(s);
        return buffer.array();
    }

    static byte[] derivedKey(byte[] sharedSecret, byte[] binding)
            throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] publicSalt = "training-only-HKDF-salt".getBytes(StandardCharsets.UTF_8);
        mac.init(new SecretKeySpec(publicSalt, "HmacSHA256"));
        byte[] prk = mac.doFinal(sharedSecret); // HKDF-Extract
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(binding);                    // HKDF-Expand info
        mac.update((byte) 1);                   // T(1), 1 block
        byte[] output = Arrays.copyOf(mac.doFinal(), 16);
        Arrays.fill(prk, (byte) 0);
        return output;
    }

    static byte[] signed(PrivateKey identityPrivate, byte[] binding)
            throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(identityPrivate);
        signer.update(binding);
        return signer.sign();
    }

    static boolean verified(PublicKey pinnedIdentity, byte[] binding,
                            byte[] signature) throws Exception {
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pinnedIdentity);
        verifier.update(binding);
        return verifier.verify(signature);
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator ephemeralGen = KeyPairGenerator.getInstance("X25519");
        KeyPair alice = ephemeralGen.generateKeyPair();
        KeyPair bob = ephemeralGen.generateKeyPair();
        KeyPair malloryToAlice = ephemeralGen.generateKeyPair();
        KeyPair malloryToBob = ephemeralGen.generateKeyPair();

        byte[] honestTranscript = transcript(alice.getPublic(), bob.getPublic());
        byte[] aliceHonest = derivedKey(shared(alice.getPrivate(), bob.getPublic()),
                honestTranscript);
        byte[] bobHonest = derivedKey(shared(bob.getPrivate(), alice.getPublic()),
                honestTranscript);
        System.out.println("honestSameKey="
                + MessageDigest.isEqual(aliceHonest, bobHonest));

        byte[] aliceLegTranscript =
                transcript(alice.getPublic(), malloryToAlice.getPublic());
        byte[] serverLegTranscript =
                transcript(malloryToBob.getPublic(), bob.getPublic());

        byte[] aliceLeg = derivedKey(shared(alice.getPrivate(),
                malloryToAlice.getPublic()), aliceLegTranscript);
        byte[] malloryAliceLeg = derivedKey(shared(malloryToAlice.getPrivate(),
                alice.getPublic()), aliceLegTranscript);

        byte[] bobLeg = derivedKey(shared(bob.getPrivate(),
                malloryToBob.getPublic()), serverLegTranscript);
        byte[] malloryBobLeg = derivedKey(shared(malloryToBob.getPrivate(),
                bob.getPublic()), serverLegTranscript);

        System.out.println("attackerKnowsClientLeg="
                + MessageDigest.isEqual(aliceLeg, malloryAliceLeg));
        System.out.println("attackerKnowsServerLeg="
                + MessageDigest.isEqual(bobLeg, malloryBobLeg));
        System.out.println("twoLegKeysDistinct="
                + !MessageDigest.isEqual(aliceLeg, bobLeg));

        KeyPairGenerator identityGen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair trustedBobIdentity = identityGen.generateKeyPair();
        KeyPair attackerIdentity = identityGen.generateKeyPair();

        byte[] bobProof = signed(trustedBobIdentity.getPrivate(), honestTranscript);
        byte[] forgedProof = signed(attackerIdentity.getPrivate(),
                aliceLegTranscript);

        System.out.println("trustedServerSignatureValid="
                + verified(trustedBobIdentity.getPublic(),
                        honestTranscript, bobProof));
        System.out.println("attackerImpersonationAccepted="
                + verified(trustedBobIdentity.getPublic(),
                        aliceLegTranscript, forgedProof));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac X25519IdentityLab.java
java X25519IdentityLab
~~~

Đầu ra kỳ vọng trên JDK có X25519 và Ed25519 provider tương thích (thí nghiệm đã chạy trên OpenJDK):

~~~text
honestSameKey=true
attackerKnowsClientLeg=true
attackerKnowsServerLeg=true
twoLegKeysDistinct=true
trustedServerSignatureValid=true
attackerImpersonationAccepted=false
~~~

**Giải thích từng dòng:**

1. `honestSameKey=true`: hai bên chân thực đã thống nhất cùng một khóa 16 byte sau HKDF.
2. `attackerKnowsClientLeg=true`: Mallory có cùng khóa trên leg với Alice.
3. `attackerKnowsServerLeg=true`: Mallory có cùng khóa trên leg với Bob.
4. `twoLegKeysDistinct=true`: hai session key khác nhau với xác suất áp đảo; do khóa được sinh ngẫu nhiên, xác suất trùng ngẫu nhiên của hai giá trị 128 bit về mặt lý thuyết không bằng 0.
5. `trustedServerSignatureValid=true`: bản ghi trao đổi chân thực được ký bằng Ed25519 private key của danh tính Bob và verify bằng public key tin cậy của Bob.
6. `attackerImpersonationAccepted=false`: chữ ký của một danh tính tự tạo khác **không được chấp nhận** nếu Alice dùng đúng public key danh tính Bob đã pin.

Code không xuất raw shared secret, private key hoặc session key ra stdout. Hàm `transcript` có nhãn mục đích và length-prefix để tránh nhập nhằng cặp public key; cả hai bên phải sử dụng đúng **thứ tự client/server** giống nhau. Hàm `derivedKey` chỉ minh họa cấu trúc HKDF cho một khóa 16 byte; không thay thế key schedule của một protocol chuẩn. Các mảng byte chứa khóa chưa được quản lý vòng đời bộ nhớ như phần mềm bảo mật production.

**Giới hạn quan trọng:** demo không kết nối mạng, không truyền/verify certificate, không nhận public key từ nguồn ngoài và không thực hiện toàn bộ handshake được xác thực. Khóa `trustedBobIdentity` được tạo **bên trong cùng JVM** để mô phỏng một trust anchor đã được phân phối an toàn từ trước; hệ thống thật phải giải quyết việc cấp phát, pin/validate, rollover và thu hồi key. Bản ghi minh họa chỉ chứa nhãn phiên bản và hai ephemeral public key; protocol thực tế còn phải ràng buộc **peer identity, role, offered/selected algorithms, session nonce, protocol context và toàn bộ dữ liệu handshake ảnh hưởng đến khóa/quyền**.

## 8. Tại sao transcript binding và HKDF vẫn chưa chặn MITM?

Trong demo, cả `aliceLegTranscript` và `serverLegTranscript` đều có nhãn protocol và public key của hai bên. Mallory xây dựng mỗi transcript hợp lệ theo góc nhìn của từng leg:

~~~text
Alice nhìn thấy:  (AlicePublic, MalloryPublicToAlice)
Bob nhìn thấy:    (MalloryPublicToBob, BobPublic)
~~~

Vì Mallory nắm private key phù hợp cho cả hai transcript, Mallory có thể tạo đúng HKDF key cho từng leg. Khóa vẫn được ràng buộc với transcript, nhưng **bản ghi đó chưa được một chủ thể đáng tin chứng thực**. Muốn vượt qua khoảng trống này, ít nhất một phía phải xác minh binding giữa public key tạm thời và danh tính định danh đã có trust anchor.

Khi triển khai authenticated key exchange, một transcript đầy đủ thường phải chứa các đầu vào như:

~~~text
protocol_id / version / algorithm suite
client_identity? / server_identity
client_hello, server_hello
ephemeral public keys A, B
nonces, connection/session identifiers
selected parameters / key shares
role labels, transcript digest
~~~

Dấu hỏi sau `client_identity?` nhắc rằng authentication một chiều có thể không yêu cầu client identity; nó vẫn phải chống nhầm vai trò. Danh sách trên là **conceptual checklist**, không phải wire format chuẩn. Các giao thức như TLS 1.3 định nghĩa transcript hashing và CertificateVerify/Finished cụ thể; tự gán nhãn rồi ký vài byte tùy ý không tạo một protocol đã được phân tích bảo mật.

## 9. Chữ ký Ed25519 thêm được phần xác thực nào?

Cơ chế demo:

~~~text
1) Alice đã biết và tin cậy pk_identity_Bob từ ngoài phiên.
2) Alice gửi A, Bob gửi B.
3) Bob ký Sign(sk_identity_Bob, transcript(protocol, A, B)).
4) Alice kiểm tra Verify(pk_identity_Bob, transcript, signature).
5) Nếu chữ ký sai → dừng. Nếu đúng → tiếp tục các bước khóa và policy còn lại.
~~~

Attacker có thể tự tạo public key Ed25519 và ký transcript đã sửa. Nhưng nếu Alice **không** thay `pk_identity_Bob` bằng key do attacker đưa, chữ ký đó không verify được. Việc Alice giữ vững **nguồn public key danh tính tin cậy** là điểm tạo ra thuộc tính authentication. Nếu Alice đọc public key danh tính ngay từ một trường không đáng tin trong thông điệp và chấp nhận vô điều kiện, attacker có thể gửi cả khóa của mình và chữ ký hợp lệ; ta quay lại lỗi ban đầu.

Chữ ký server lên ephemeral transcript có thể tạo **server authentication** khi liên kết đúng khóa danh tính/tên server. Nó **không tự tạo client authentication**. Bob chỉ nhìn thấy một ephemeral public key `A`, chưa biết nó thuộc user nào nếu không có thêm client certificate, client signature, PSK hoặc một phương án identity proof khác.

Nếu một ứng dụng dùng X.509 certificate: còn phải kiểm tra certificate path/trust anchor, validity, expected hostname/service name, key usage/extended key usage nếu liên quan, chính sách thu hồi và quyền signer. **`Signature.verify(...) = true` với public key bất kỳ chưa chứng minh public key đó thuộc người mà ứng dụng muốn giao tiếp**.

Một cặp khóa Ed25519 danh tính dùng để ký bản ghi; một cặp X25519 ephemeral dùng để tính DH. Chúng có **vai trò mật mã khác nhau**: không dùng private key Ed25519 như private key X25519 bằng cách ép kiểu hoặc cố tái sử dụng raw key bytes. Trong dự án thật, chọn và quản lý khóa theo hợp đồng của protocol/library.

## 10. Key confirmation khác authentication và authorization

Cùng một từ “xác nhận” dễ gây lẫn lộn:

| Tính chất | Chứng minh điều gì? | Thí nghiệm này |
| --- | --- | --- |
| **Key agreement** | Hai phía tính được secret dựa trên private/public key tương ứng | Alice/Bob hoặc mỗi leg của Mallory |
| **Key derivation** | Từ secret và transcript tạo key theo mục đích | HKDF-HMAC-SHA-256 |
| **Key confirmation** | Peer có thể tạo/kiểm tra một MAC hoặc AEAD tag chứng tỏ biết key đang dùng | Chưa được demo triển khai như một message handshake |
| **Entity/peer authentication** | Khóa phiên được gắn với **đúng danh tính** định tin cậy | Ed25519 ký transcript + public key danh tính Bob được pin mới bắt đầu giải quyết |
| **Authorization** | Danh tính đã xác thực có quyền thực hiện thao tác nghiệp vụ không? | Không thuộc phạm vi `KeyAgreement` |
| **Replay protection** | Thông điệp/phiên cũ bị phát lại có bị từ chối? | Demo chưa xử lý |
| **Confidentiality/integrity of application data** | Nội dung sau handshake được AEAD bảo vệ phù hợp không? | Demo chưa gửi ciphertext |

Một attacker có shared secret với Alice **cũng có thể tạo key-confirmation MAC hợp lệ trên leg Alice–Mallory**. Vì thế kết quả MAC đúng không có nghĩa đầu kia là Bob. Tương tự, AES-GCM phát hiện sửa đổi bởi người **không biết khóa**, nhưng Mallory là người nắm khóa của hai leg, nên có thể hợp lệ giải mã và mã hóa lại.

**Authorization** vẫn là bước riêng: xác thực server Bob không đảm bảo client Alice được phép đặt lệnh thanh toán trị giá 1 tỷ. Bên nhận phải kiểm tra identity, tenant, role, scope và policy nghiệp vụ sau handshake phù hợp.

## 11. Các lựa chọn xác thực cho giao thức thỏa thuận khóa

| Phương án | Lợi ích | Ràng buộc cần có |
| --- | --- | --- |
| **Server certificate + chữ ký transcript** | Client xác thực server theo khóa gắn với hostname/service name; có thể dùng ephemeral DH để tạo forward secrecy | Đúng trust anchors, tên server, chứng chỉ, thuật toán, trạng thái thu hồi và transcript |
| **Pin server public key** | Đơn giản cho số peer nhỏ hoặc hệ thống nhúng/định danh cố định | Phân phối key ban đầu đáng tin, hỗ trợ rotation, kiểm tra đúng service/tenant |
| **Mutual certificates/signatures** | Xác thực cả hai phía khi cần danh tính peer tương hỗ | Mỗi peer phải validate danh tính/mục đích khóa bên kia; kiểm soát quyền độc lập |
| **PSK-based authentication** | Chứng minh possession của secret thiết lập từ trước; có thể dùng HMAC transcript | PSK entropy đủ mạnh, phân phối/rotation/identity mapping chặt, chống nhầm PSK và replay |
| **TOFU (trust on first use)** | Có thể phát hiện thay khóa sau lần đầu | Lần đầu dễ bị MITM; chính sách riêng để xử lý thay khóa, cảnh báo và người dùng chấp thuận |
| **Protocol đã được chuẩn hóa (TLS 1.3, Noise profile, HPKE chuẩn theo use case)** | Giảm rủi ro tự thiết kế wire format và key schedule | Vẫn cần cấu hình trust, danh tính, hostname, ciphersuites, phiên bản, replay/authorization phù hợp |

Không có “một chữ ký chung” đủ cho mọi protocol. Ký **đúng transcript** là cần thiết; nếu bỏ qua phần thuật toán đã thương lượng hoặc danh tính trong dữ liệu được ký, attacker có thể tạo downgrade hoặc identity-misbinding trong những thiết kế thiếu ràng buộc. Hệ thống production nên dùng protocol/library được thẩm định thay vì xây mới từ các đoạn mã minh họa.

## 12. DHKEM là gì, và khác `KeyAgreement` thuần như thế nào?

**DHKEM (Diffie–Hellman Key Encapsulation Mechanism)** trong RFC 9180 là một **KEM** xây từ Diffie–Hellman và HKDF với domain separation/format chuẩn. Cần phân biệt:

- `KeyAgreement` là **JCA primitive** tính secret từ local private key và peer public key; không định nghĩa cú pháp `enc`, KEM context, KDF label hoặc authenticated HPKE modes.
- **DHKEM** định nghĩa các thao tác `Encap(pkR) → (shared_secret, enc)` và `Decap(enc, skR) → shared_secret` trong base KEM, với khóa tạm thời của sender và public key dài hạn/được công bố của recipient; `enc` là thông điệp encapsulation thường chứa ephemeral public key theo định dạng của KEM.
- **Authenticated DHKEM** hỗ trợ `AuthEncap/ AuthDecap`, kết hợp thêm private key tĩnh của sender và public key tĩnh tương ứng ở recipient theo cấu trúc định nghĩa bởi RFC.
- **HPKE** kết hợp KEM, KDF và AEAD với một key schedule, mode, `info`, `aad`, nonce management và định dạng/thuật toán chính xác. Không thể coi `X25519 + một hàm HMAC tùy ý` là implementation tương thích với HPKE.

Ví dụ các KEM ID chuẩn của RFC 9180:

| KEM | Cặp primitive | Vai trò |
| --- | --- | --- |
| `DHKEM(X25519, HKDF-SHA256)` (KEM ID `0x0020`) | X25519 + HKDF-SHA256 | Encapsulate/decapsulate secret, có giao diện authenticated KEM |
| `DHKEM(P-256, HKDF-SHA256)` (KEM ID `0x0010`) | ECDH P-256 + HKDF-SHA256 | Cơ chế tương tự trên curve NIST P-256 |
| `DHKEM(X448, HKDF-SHA512)` (KEM ID `0x0021`) | X448 + HKDF-SHA512 | Cấu hình X448 |

`DHKEM` trong HPKE không chỉ là phép DH trong ví dụ Java; RFC 9180 còn quy định **KEM context, labeled Extract/Expand, serialization, kích thước/encoding và xử lý lỗi**. Nếu cần interoperability với HPKE, phải dùng thư viện hỗ trợ RFC 9180 và kiểm tra test vectors. Với **Java 17**, `KeyAgreement("X25519")` là primitive phù hợp để học; **không suy ra** Java 17 mặc định đã có `Cipher("HPKE")` hay API KEM đủ để triển khai RFC 9180 tự động.

## 13. HPKE Base, PSK, Auth và AuthPSK: ai được chứng thực?

Theo RFC 9180, HPKE có bốn mode:

| Mode | Cơ sở khóa | Thuộc tính xác thực |
| --- | --- | --- |
| `mode_base` (`0x00`) | DHKEM tới public key recipient, KDF, AEAD | **Không cung cấp sender authentication.** Recipient public key vẫn cần được phân phối/xác minh đúng để sender biết mình mã hóa cho ai. |
| `mode_psk` (`0x01`) | Base KEM + pre-shared key | Xác thực possession của PSK ở mức bảo mật của mode; danh tính phụ thuộc cách app quản lý, gắn `psk_id` và PSK. |
| `mode_auth` (`0x02`) | AuthEncap/AuthDecap với sender static KEM key | Recipient nhận bảo đảm sender sở hữu private key tương ứng `pkS`, **chỉ khi `pkS` đã được xác thực/ánh xạ đúng**; có giới hạn KCI trong RFC. |
| `mode_auth_psk` (`0x03`) | Auth KEM + PSK | Kết hợp possession của sender static key và PSK, vẫn cần identity binding và policy đúng. |

Cần tránh hai hiểu nhầm:

**“Dùng DHKEM Base là tự động xác thực cả Alice và Bob.”** Không. Base không chứng minh danh tính sender. Nếu attacker thay **recipient public key** được ứng dụng dùng, ciphertext có thể được gửi đến chính attacker. Bảo đảm phía recipient dựa vào việc sender **đã có đúng, đáng tin `pkR` từ trước**.

**“HPKE Auth nghĩa là địa chỉ email/username của sender đã được xác thực.”** Không. RFC mô tả proof possession của sender private key tương ứng static `pkS`; việc ràng buộc khóa đó với tên người/đối tác/tenant và kiểm tra quyền là trách nhiệm ngoài KEM. RFC cũng thảo luận **key-compromise impersonation** đối với DHKEM Auth. Đừng tự suy ra mutual authentication hoặc tính chống replay của toàn bộ ứng dụng từ tên mode.

HPKE là **công cụ mã hóa hybrid**, không tự tạo một protocol hội thoại hai chiều có chứng thực người dùng, cấp quyền và chống phát lại. Khi ứng dụng cần phiên trực tuyến lâu dài, có thể phù hợp hơn với một protocol authenticated key exchange đã chuẩn hóa, ví dụ TLS 1.3. Nếu cần mã hóa không tương tác tới một recipient đã công bố khóa, HPKE có thể phù hợp hơn; lựa chọn dựa vào threat model.

## 14. Làm thế nào thiết kế một handshake được xác thực?

Một thiết kế ở mức nguyên tắc (không phải RFC/wire format để triển khai trực tiếp) có thể gồm:

~~~text
Client                                          Server
  |---- ClientHello: nonceC, algorithms, A ---->|
  |<--- ServerHello: nonceS, selected, B -------|
  |<--- Certificate / trusted identity proof ---|
  |<--- Signature_B(full transcript) -----------|
  |-- Verify trusted identity, name, transcript-|
  |---- Key confirmation / Finished ------------>|
  |<--- Key confirmation / Finished -------------|
  |==== Encrypted application data =============|
~~~

Phía client cần xác nhận **identity key hoặc chain** gắn với server dự kiến, signature đúng trên toàn bộ transcript có ngữ cảnh/role, chính sách thuật toán không bị hạ cấp, và các giá trị challenge/nonce còn mới. Nếu cần mutual authentication, server cũng phải xác minh chứng thực của client trên transcript và áp dụng authorization tương ứng.

Một số câu hỏi thiết kế bắt buộc phải trả lời:

1. **Trust anchor** có nguồn nào? PKI, key pin hay PSK? Ai cho phép thay hoặc thu hồi khóa?
2. **Identity** được ánh xạ thế nào? Hostname/SAN, service ID, tenant, user, role, key ID; cơ chế chống nhầm danh tính?
3. **Transcript** chứa những gì? Algorithm suite, offered/selected cipher, cả hai ephemeral keys, roles, protocol version, nonce, app context, hướng truyền? Dữ liệu ký có length-prefix/canonicalization rõ ràng?
4. **Finished/key confirmation** có key riêng theo hướng nào? Có MAC riêng cho client và server để tránh replay/reflection không?
5. **Application data** dùng AEAD key/nonce schedule ra sao? Có record sequence number, AAD chứa header/protocol context, chống nonce reuse và xử lý tag failure không?
6. **Thay đổi trạng thái**: khi verify fail, timeout, alert hoặc peer gửi key lạ, kết nối có fail-closed không?
7. **Forward secrecy** có yêu cầu? Có dùng ephemeral DH cho mỗi phiên, xóa ephemeral private key khi không cần nữa, hạn chế retention transcript/secret?
8. **Session reuse/resumption** có xác thực và chống replay riêng không? Có quản lý vòng đời PSK/ticket và đổi khóa hợp lý không?

Đối với production, những câu hỏi này thường là lý do nên **dùng TLS 1.3/HPKE profile được đánh giá** thay vì sáng tác handshake mới. Đoạn mã X25519/Ed25519 ở mục 7 chỉ làm rõ ranh giới, chưa đủ để triển khai cả tám yêu cầu.

## 15. Forward secrecy, key compromise và những giới hạn khác

Dùng **ephemeral X25519** mỗi phiên có thể hỗ trợ forward secrecy khi protocol xác thực đúng và xóa ephemeral secrets sau phiên. Khi private key danh tính dài hạn bị lộ về sau, attacker đã thu traffic cũ **không nhất thiết** tính được khóa các phiên trước vì các ephemeral DH private key đã bị xóa. Nhưng điều đó phụ thuộc thiết kế key schedule, lifecycle, việc không tái sử dụng ephemeral key và threat model; một ví dụ chỉ tạo key mới trong JVM chưa bảo đảm forward secrecy thực tế.

Phân biệt ba kiểu lộ khóa:

- **Ephemeral private key của phiên bị lộ** → kẻ có transcript có thể tính secret phiên đó; không có cơ chế nào “bù” nếu private key phiên và peer public key đều lộ.
- **Identity signing private key dài hạn bị lộ** → attacker có thể mạo danh trong các phiên tương lai hoặc giả mạo signature chưa được ràng buộc. Quá khứ có thể vẫn hưởng forward secrecy nếu ephemeral secrets thật sự bị xóa và protocol vững.
- **Recipient static KEM private key bị lộ** → với các mô hình mã hóa một chiều như HPKE Base sử dụng cùng recipient static key, ciphertext cũ đã ghi lại có thể bị ảnh hưởng theo thuộc tính forward secrecy của mode; không mặc định có forward secrecy kiểu ephemeral–ephemeral như TLS 1.3.

**KCI (key-compromise impersonation)** là một phân tích khác: nếu private key tĩnh của một bên bị lộ, attacker có thể giả một peer khác **đến chính bên bị lộ khóa** trong một số scheme. RFC 9180 cảnh báo giới hạn này với Auth DHKEM. Không suy rằng mode tên “Auth” có mọi thuộc tính của một giao thức mutually authenticated AKE.

Việc xóa mảng Java bằng `Arrays.fill` không bảo đảm mọi bản sao trong provider/heap đã biến mất. Với code production, quản lý secret bằng API được duy trì, giới hạn lifetime và để cơ chế bảo mật của protocol/library xử lý vòng đời tốt nhất có thể.

## 16. Sai lầm phổ biến cần phát hiện bằng test

| Sai lầm | Vì sao sai | Kiểm thử/khắc phục |
| --- | --- | --- |
| “`honestSameKey=true` nên đã xác thực Bob” | Shared key equality không chứng minh danh tính | Chèn attacker thay public key, xem leg nào sở hữu khóa |
| Lấy identity public key từ payload chưa tin cậy | Attacker có thể thay cả key và signature | Pin key hoặc validate cert/trust anchor và expected name |
| Chỉ Hash/ HKDF shared secret rồi nói đã chống MITM | KDF không gán identity | Bắt buộc authenticated transcript |
| Chỉ dùng AES-GCM sau DH | Attacker biết khóa từng leg vẫn có thể decrypt/re-encrypt | Authenticated key exchange trước khi trao dữ liệu nghiệp vụ |
| Chỉ gửi HMAC key confirmation | Mallory cũng biết key từng leg | Dùng entity authentication độc lập |
| Không bind protocol version, algorithm, role, nonces | Có thể downgrade, reflection hoặc identity misbinding | Canonical transcript đầy đủ và chính sách version/suite |
| Tái sử dụng public/private X25519 ephemeral | Rủi ro đối với forward secrecy và key lifecycle | Fresh key pairs theo giao thức, kiểm soát lifecycle |
| Raw DH secret dùng trực tiếp làm khóa mọi mục đích | Thiếu KDF/domain separation, khó tách khóa/hướng | HKDF/key schedule chuẩn, các context label tách biệt |
| Chấp nhận key encoding/public point tùy tiện | Provider có thể báo lỗi hoặc hành vi không như tưởng | Parser giới hạn, algorithm check, reject invalid/all-zero |
| Dùng cùng key/nonce cho nhiều AEAD messages | Nonce reuse với GCM có thể phá bảo mật | Unique nonce per key, sequence-number schedule |
| Không kiểm tra identity-to-authorization mapping | Người có key hợp lệ vẫn có thể không có quyền | Authn → authz → resource permission |
| Tự implement “HPKE” bằng `X25519 + HMAC + AES` | Không tương thích RFC 9180 KEM context, label, mode, encoding | Thư viện HPKE chuẩn, kiểm thử vectors/interoperability |
| Không chống replay | Giao dịch đã ký/mã hóa có thể bị gửi lại | Nonce, session ID, timestamp, replay cache theo nghiệp vụ |
| Bắt lỗi chữ ký rồi vẫn tiếp tục phiên | Mở đường bỏ qua authentication | Fail closed, hủy secret/connection |

## 17. Bài tập thực hành có hướng dẫn kết quả

### Bài tập A — Quan sát MITM nhưng không in bí mật

Chạy `X25519IdentityLab`. Giải thích vì sao `attackerKnowsClientLeg=true` và `attackerKnowsServerLeg=true` cùng đúng. Xác định public key mà Alice *thực sự* dùng ở leg của mình, và public key Bob *thực sự* nhận.

**Đáp án:** Alice dùng `malloryToAlice.getPublic()`; Bob nhận `malloryToBob.getPublic()`. Mallory sở hữu cả hai private key tương ứng và có khả năng tính đúng mỗi shared secret. Alice/Bob không đồng thuận trên **cùng transcript** khi mạng có attacker, nhưng mỗi leg lại có transcript nội bộ nhất quán với attacker.

### Bài tập B — Xác thực danh tính từ nguồn tin cậy

Đổi bước kiểm tra chữ ký sao cho client verify bằng `attackerIdentity.getPublic()`. Giá trị `attackerImpersonationAccepted` thay đổi thế nào? Việc đổi như vậy có an toàn không?

**Đáp án:** Chữ ký do attacker tạo sẽ verify được với đúng public key attacker, vì vậy biểu thức verify thành `true`. Đây không phải chứng cứ rằng attacker là Bob. Nếu ứng dụng đổi trust anchor dựa trên public key do attacker cung cấp trên kết nối, toàn bộ authentication mất tác dụng. Không làm cách này trong ứng dụng thật.

### Bài tập C — Signature phải ký đúng transcript

Giả sử Bob ký `honestTranscript`, nhưng Alice thấy `aliceLegTranscript`. Hãy dự đoán kết quả của:

~~~java
// Trích đoạn ý tưởng; các biến có trong ví dụ đầy đủ.
boolean accepted = verified(
        trustedBobIdentity.getPublic(), aliceLegTranscript, bobProof);
~~~

**Đáp án:** `false` trong tình huống này vì chữ ký của Bob dành cho transcript `(AlicePublic, BobPublic)`, còn Alice đang thử verify với `(AlicePublic, MalloryPublic)`. Thay đổi bất kỳ byte nào trong transcript cần làm chứng thực hết hiệu lực nếu signature được kiểm tra đúng.

### Bài tập D — Key confirmation có “vá” được authentication không?

Mallory gửi MAC được tạo bằng `malloryAliceLeg` cho Alice, Alice kiểm tra MAC bằng `aliceLeg` và nhận thành công. Có thể kết luận Bob là peer không?

**Đáp án:** Không. MAC cho thấy người gửi có khóa leg với Alice. Vì attacker biết khóa đó, MAC không chứng minh danh tính Bob. Cần trust anchor và xác minh transcript/identity.

### Bài tập E — Chọn HPKE mode phù hợp

Ứng dụng nhận ciphertext từ nhiều sender khác nhau và cần biết sender nào đã gửi. Nếu dùng HPKE Base và có recipient private key đáng tin, có đủ để nhận diện sender chưa?

**Đáp án:** Chưa. Base mode không xác thực sender. Cần sender authentication riêng, hoặc dùng một HPKE authenticated mode với static sender key đã được recipient xác minh và gắn với identity theo policy, đồng thời xử lý replay/authorization theo ứng dụng.

### Bài tập F — Thêm test fail-closed

Trong demo, ép `verified(trustedBobIdentity.getPublic(), aliceLegTranscript, forgedProof)` trả `false`. Ở ứng dụng production, nên làm gì với ciphertext/application request nhận sau đó?

**Đáp án:** Dừng handshake/connection hoặc từ chối request trước khi tạo trạng thái đã xác thực; không gửi khóa ứng dụng, không chạy giao dịch dựa trên danh tính Bob, không retry bằng cách bỏ qua signature verification.

## 18. Kiểm thử tối thiểu trước khi chuyển sang protocol thật

Trước khi cho phép một luồng xử lý sử dụng khóa có được từ DH/KEM, kiểm thử ít nhất:

1. **Honest path:** hai bên thống nhất secret/key, encrypt/decrypt một bản tin AEAD hợp lệ qua implementation chuẩn.
2. **MITM/key substitution:** thay một hoặc cả hai public key chưa ký; quy trình authentication phải chặn trước khi chuyển sang authenticated application state.
3. **Wrong signer:** chữ ký đúng nhưng với public key khác, certificate sai hostname/tenant hoặc khóa hết hạn/không được phép; từ chối.
4. **Transcript mismatch:** đổi nonce, role, algorithm suite, public key, giao thức version hoặc `info`; signature/Finished/key derivation không được silently accept.
5. **Invalid input:** malformed public key/curve, payload quá lớn, all-zero/low-order DH output; từ chối nhất quán.
6. **Replay:** gửi lại handshake/application message cũ và kiểm tra policy về nonce/session/token.
7. **Key schedule:** client/server cùng transcript cho cùng key; đổi role/context/version tạo key khác; không dùng cùng key cho các hướng/purpose khác nhau.
8. **Record protection:** sử dụng nonce/sequence đúng, AEAD tag failure phải được xử lý an toàn, không xuất plaintext chưa xác minh.
9. **Long-term key events:** key rotation, revoked key, certificate mismatch, trust-anchor change và compromise response.
10. **Protocol interoperability:** nếu chọn RFC 9180, so đối chiếu official HPKE test vectors của đúng KEM/KDF/AEAD/mode và các triển khai độc lập.

Các nhóm test này nhằm xác định **đúng thuộc tính an toàn muốn cung cấp**, không chỉ xác định thuật toán trả giá trị trông hợp lệ.

## 19. Tài liệu tiêu chuẩn để đọc sâu

- **Java SE 17 — `KeyAgreement`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/KeyAgreement.html
- **Java SE 17 — `KeyPairGenerator`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyPairGenerator.html
- **RFC 7748 — Elliptic Curves for Security (X25519/X448):** https://www.rfc-editor.org/rfc/rfc7748
- **RFC 5869 — HMAC-based Extract-and-Expand Key Derivation Function (HKDF):** https://www.rfc-editor.org/rfc/rfc5869
- **RFC 9180 — Hybrid Public Key Encryption (DHKEM/HPKE modes):** https://www.rfc-editor.org/rfc/rfc9180
- **RFC 8446 — TLS 1.3 (authenticated handshake, transcript and Finished):** https://www.rfc-editor.org/rfc/rfc8446

**Thông điệp cần nhớ:** `KeyAgreement`/DHKEM giải bài toán tạo bí mật chung từ khóa DH; HKDF chuyển bí mật đó thành khóa dùng đúng mục đích. **Danh tính của đối tác không tự xuất hiện từ phép DH, KDF, AES-GCM hay key confirmation.** Muốn chống active MITM, cần trust anchor và authenticated transcript/protocol. Sau khi xác thực vẫn còn phải xử lý authorization, replay, key lifecycle và bảo vệ bản tin theo một key schedule đã chuẩn hóa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-practice-007">23.7 Tạo chuỗi chứng thư thử nghiệm rồi xây và kiểm tra đường dẫn PKIX</a>

<details>
<summary>Xem chi tiết</summary>

# 23.7. Tạo chuỗi chứng thư thử nghiệm rồi xây và kiểm tra đường dẫn PKIX

## Mục tiêu của bài thực hành

Trong bài này, bạn sẽ **tự tạo ba tầng chứng thư X.509** bằng `keytool`, sau đó viết chương trình Java 17+ dùng `CertPathBuilder` để tìm đường dẫn tin cậy và `CertPathValidator` để kiểm tra đường dẫn ấy theo PKIX. Bài gồm cả trường hợp thành công và các phép thử thất bại có chủ đích.

Sau khi hoàn thành, bạn có thể:

1. Phân biệt **root CA**, **intermediate CA**, **end-entity/leaf certificate**, **trust anchor** và **candidate certificate**.
2. Tạo cặp khóa/CSR/chứng thư bằng `keytool`, cấp chứng thư cho một intermediate và một máy chủ thử nghiệm.
3. Đọc chứng thư PEM bằng `CertificateFactory` và tạo tập ứng viên bằng `CertStore`.
4. Cấu hình `PKIXBuilderParameters`, `X509CertSelector`, `TrustAnchor` và xây `CertPath`.
5. Đọc kết quả `PKIXCertPathBuilderResult`, rồi chủ động gọi `CertPathValidator` với `PKIXParameters`.
6. Thử **root không được tin cậy**, **chứng thư quá hạn**, và hiểu vì sao thiếu intermediate, sai CA constraints hoặc bị chặn thuật toán cũng gây lỗi.
7. Biết những việc **PKIX không làm thay ứng dụng**: xác minh hostname, quyết định quyền truy cập, quản lý khóa bí mật và cấu hình kiểm tra thu hồi phù hợp production.

**Môi trường:** JDK 17+ có `java`, `javac` và `keytool` trong `PATH`; một thư mục thử nghiệm trống, quyền tạo tệp, không cần Internet hay CA công cộng. Toàn bộ tên DNS trong bài là tên giả `lab.example.test`. Các mật khẩu hiển thị trong lệnh chỉ dùng trong phòng thực hành cô lập.

> **Giới hạn:** không dùng root hoặc private key tạo ở đây để ký chứng thư thật. Không đưa file `.p12`/private key và mật khẩu demo vào Git, artifact CI hoặc máy production. Những khóa và chứng thư trong bài chỉ là dữ liệu thực hành có thể xóa sau khi học.

## 1. PKIX kiểm tra điều gì?

**PKIX (Public Key Infrastructure X.509)** là tập quy tắc về cấu trúc, xây dựng và xác minh chuỗi chứng thư. Trong Java, bạn dùng các API chuẩn của `java.security.cert`.

Chuỗi thí nghiệm sẽ có dạng:

```text
        [Lab Root CA]                     Trust anchor (được tin từ trước)
        Subject: CN=Lab Root CA
        self-signed; BasicConstraints CA=true; pathLen=1
                    │ ký
                    ▼
        [Lab Intermediate CA]            CA trung gian
        Subject: CN=Lab Intermediate CA
        Issuer:  CN=Lab Root CA
        CA=true; pathLen=0; keyCertSign
                    │ ký
                    ▼
        [lab.example.test]               Leaf / end-entity
        Subject: CN=lab.example.test
        Issuer:  CN=Lab Intermediate CA
        SAN: DNS:lab.example.test
        CA=false; EKU=serverAuth; digitalSignature
```

**Trust anchor** là điểm neo do người kiểm tra chủ động tin cậy. Trong thí nghiệm, chúng ta chỉ định chứng thư root làm trust anchor: `new TrustAnchor(root, null)`. Việc root tự ký **không tự làm nó đáng tin**. Thông thường, root đã được cấp qua truststore/OS hoặc quy trình quản trị CA, không phải lấy tùy tiện từ kết nối mạng.

**CertPath** mà Java xây thường gồm các chứng thư **từ leaf tới intermediate**, và **không gồm trust anchor**. Vì vậy trong lab có ba chứng thư nhưng `getCertPath().getCertificates().size()` là **2**. Đó là kết quả đúng.

Bốn thành phần lõi:

| API | Vai trò |
| --- | --- |
| `CertificateFactory.getInstance("X.509")` | Chuyển dữ liệu DER/PEM thành `X509Certificate`; có thể tạo `CertPath` từ danh sách có thứ tự |
| `X509CertSelector` | Mô tả certificate mục tiêu của builder; ví dụ chỉ đúng leaf |
| `CertStore` | Tập nguồn certificate/CRL **ứng viên**, có thể lấy từ memory, LDAP, HTTP theo từng implementation; không phải nơi khai báo trust anchor |
| `CertPathBuilder.getInstance("PKIX")` | Tìm một đường dẫn thỏa các ràng buộc PKIX tới trust anchor được tin |
| `CertPathValidator.getInstance("PKIX")` | Kiểm tra một `CertPath` đã có theo `PKIXParameters` |
| `PKIXBuilderParameters` / `PKIXParameters` | Chứa trust anchors, date, revocation policy, checker và các tham số tương ứng |

**Nhớ phân biệt:** “Tôi tìm thấy certificate intermediate trong `CertStore`” khác hoàn toàn với “Tôi tin CA intermediate”. Nguồn ứng viên không tự được nâng thành trust anchor.

## 2. Điều kiện quan trọng của chứng thư trong chuỗi

Một chuỗi đúng tên issuer/subject chưa đủ. Bộ kiểm tra PKIX còn xét nhiều thuộc tính liên quan.

### 2.1. Chữ ký số và quan hệ cấp phát

- Root có `Subject` và `Issuer` của chính nó (self-signed trong mô hình lab).
- Intermediate có `Issuer` chỉ root; chữ ký của intermediate phải kiểm tra được bằng **public key của root**.
- Leaf có `Issuer` chỉ intermediate; chữ ký của leaf phải kiểm tra được bằng **public key của intermediate**.

So khớp `Issuer` và `Subject` chỉ là bước tìm ứng viên; phải xác minh chữ ký và các constraint. Gọi riêng `leaf.verify(intermediate.getPublicKey())` chỉ kiểm tra chữ ký leaf, **chưa** thực hiện đầy đủ PKIX, cũng chưa xác minh trust anchor.

### 2.2. BasicConstraints và pathLenConstraint

Intermediate phải được phép làm CA (X.509 `BasicConstraints: CA:true`). Nếu intermediate có `CA:false` thì nó không được ký leaf như một CA hợp lệ. `pathLen:0` ở intermediate nghĩa là **không được có thêm một CA trung gian không tự phát hành bên dưới intermediate** trong đường dẫn hợp lệ; intermediate vẫn được ký leaf.

Root trong bài có `CA:true, pathLen:1`. Đường dẫn root → một intermediate → leaf phù hợp. `pathLenConstraint` được áp dụng theo quy tắc RFC 5280, không phải phép đếm thô tất cả certificate trong file PEM.

### 2.3. KeyUsage, ExtendedKeyUsage và SAN

- Certificate CA dùng để ký certificate cần `keyCertSign` phù hợp khi `KeyUsage` có mặt; CA dùng ký CRL có thể có `cRLSign`.
- Leaf cấp cho server thử nghiệm dùng `digitalSignature`, và `ExtendedKeyUsage: serverAuth` trong ví dụ.
- `SubjectAlternativeName (SAN)` chứa `DNS:lab.example.test` để phục vụ xác minh danh tính endpoint khi dùng TLS.

**Điểm thực hành:** PKIX validation thành công chỉ chứng minh đường chứng thư hợp lệ theo tập quy tắc/parameter được áp dụng. Nó **không tự so khớp** `lab.example.test` với hostname mà ứng dụng muốn kết nối. Với HTTPS/TLS, phải bật và kiểm tra cơ chế endpoint identification/hostname verification phù hợp, ngoài việc kiểm tra PKIX.

### 2.4. Thời gian, chính sách thuật toán, thu hồi

Certificate có `NotBefore` và `NotAfter`. Java kiểm tra hiệu lực theo ngày xác minh từ `PKIXParameters.getDate()` (mặc định gần thời điểm hiện tại). Các ràng buộc thuật toán (ví dụ `jdk.certpath.disabledAlgorithms`), key size, signature, name constraints, policy và revocation có thể làm thất bại đường dẫn. Trong lab, **tạm tắt revocation** vì chúng ta không phát hành CRL hay vận hành OCSP responder; phải cấu hình policy thu hồi riêng khi triển khai thật.

## 3. Chuẩn bị thư mục riêng và kiểm tra công cụ

Tạo một thư mục mới chỉ dành cho lab, ví dụ `pkix-lab`, rồi chạy mọi lệnh `keytool` trong thư mục đó. Trên Windows, Linux và macOS, các lệnh `keytool` một dòng dưới đây có thể chạy trong terminal khi `keytool` của JDK ở `PATH`.

```bash
java -version
javac -version
keytool -help
```

`java -version` phụ thuộc máy, nên không có output chung cố định. `keytool -help` chỉ để kiểm tra công cụ khả dụng. Trong các ví dụ tiếp theo, một số file `.p12` được sinh ra, chứa **private key**: không mở chúng bằng text editor, không gửi kèm bài tập và không commit.

Các tệp sẽ tạo:

| Tệp | Vai trò | Có chứa private key không? |
| --- | --- | --- |
| `root.p12` | Keystore root CA dùng ký intermediate | **Có** |
| `root.pem` | Certificate công khai của root CA | Không |
| `intermediate.p12` | Keystore private key intermediate dùng ký leaf | **Có** |
| `intermediate.csr` | Certificate signing request của intermediate | Không chứa private key |
| `intermediate.pem` | Certificate intermediate được root ký | Không |
| `server.p12` | Keystore private key của server thử nghiệm | **Có** |
| `server.csr` | Certificate signing request của leaf | Không chứa private key |
| `server.pem` | Certificate leaf được intermediate ký | Không |
| `unrelated.p12`, `unrelated.pem` | Root khác để kiểm tra trường hợp trust anchor sai | File `.p12` có private key; PEM không |

Các câu lệnh trong bài dùng mật khẩu `changeit`, **chỉ để lab tái lập**. Mật khẩu CLI có thể bị lưu trong terminal history, process list hoặc log. Với production, dùng cơ chế quản lý bí mật/credential phù hợp, key access control và CA workflow của tổ chức.

## 4. Tạo root CA tự ký bằng `keytool`

Sinh cặp RSA và chứng thư root. Đặt `CA=true`, `pathLen=1` và `keyCertSign` để mô tả rõ vai trò phát hành CA:

```bash
keytool -genkeypair -alias root -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=Lab Root CA,O=PKIX Lab,C=VN" -validity 3650 -ext "BC:critical=ca:true,pathlen:1" -ext "KU:critical=keyCertSign,cRLSign" -storetype PKCS12 -keystore root.p12 -storepass changeit -keypass changeit -noprompt
```

Giải thích:

- `-genkeypair` tạo cặp public/private key và một certificate self-signed ban đầu cho alias `root`.
- `-keyalg RSA -keysize 2048` dùng RSA 2048 bit, đủ cho ví dụ tương thích rộng; yêu cầu production có thể đòi mức cao hơn hoặc EC.
- `-sigalg SHA256withRSA` dùng thuật toán chữ ký số SHA-256 với RSA.
- `-validity 3650` là số **ngày**, chỉ dành cho lab. CA thật dùng vòng đời/phê duyệt khác.
- `-ext BC:critical=ca:true,pathlen:1` đánh dấu CA và giới hạn số CA con dưới root.
- `-ext KU:critical=keyCertSign,cRLSign` cho phép ký certificate/CRL theo key usage.
- `root.p12` lưu private key của CA; mất quyền kiểm soát tệp này có thể làm mất ý nghĩa trust anchor của cả lab.

Xuất root certificate **công khai** ra PEM để builder Java đọc:

```bash
keytool -exportcert -alias root -keystore root.p12 -storepass changeit -file root.pem -rfc
```

`-rfc` xuất dạng PEM `-----BEGIN CERTIFICATE-----`. **PEM này không chứa private key**. Chỉ import một certificate làm trust anchor sau khi bạn đã xác nhận được fingerprint/nguồn phát hành ngoài băng một cách tin cậy.

Kiểm tra thuộc tính:

```bash
keytool -printcert -file root.pem
```

Xem `Owner/Subject`, `Issuer`, `SHA256withRSA`, `BasicConstraints` và `KeyUsage`. Dòng hiển thị khác nhau theo phiên bản JDK; không phụ thuộc vào số thứ tự extension trong output.

## 5. Tạo intermediate CA và để root ký certificate

Trước tiên tạo khóa riêng cho intermediate:

```bash
keytool -genkeypair -alias intermediate -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=Lab Intermediate CA,O=PKIX Lab,C=VN" -validity 3650 -storetype PKCS12 -keystore intermediate.p12 -storepass changeit -keypass changeit -noprompt
```

Lệnh `-genkeypair` ban đầu tạo **placeholder self-signed certificate** gắn với khóa intermediate. Certificate mà ứng dụng PKIX sẽ dùng là **`intermediate.pem` được root CA ký ở bước kế tiếp**, không phải placeholder ban đầu.

Tạo **CSR** chứa public key và tên intermediate, được ký bằng private key của intermediate để chứng minh quyền sở hữu khóa:

```bash
keytool -certreq -alias intermediate -keystore intermediate.p12 -storepass changeit -file intermediate.csr
```

Root dùng CSR để phát hành certificate CA:

```bash
keytool -gencert -alias root -keystore root.p12 -storepass changeit -infile intermediate.csr -outfile intermediate.pem -rfc -validity 365 -sigalg SHA256withRSA -ext "BC:critical=ca:true,pathlen:0" -ext "KU:critical=keyCertSign,cRLSign"
```

Lưu ý:

- `-alias root` là private key **đang ký** certificate mới, không phải alias trong `intermediate.p12`.
- `-infile intermediate.csr` chứa thông tin về chủ thể **được cấp** certificate.
- Certificate trung gian có `Issuer=Lab Root CA` và `Subject=Lab Intermediate CA`.
- `pathlen:0` ngăn intermediate cấp một CA trung gian tiếp theo trong path này, nhưng vẫn cho phép ký leaf.
- Ví dụ này cố ý giữ tách file certificate public và keystore private key. Chương trình Java sẽ đọc PEM bên ngoài, không cần import intermediate reply trở lại keystore chỉ để kiểm tra PKIX.

Kiểm tra:

```bash
keytool -printcert -file intermediate.pem
```

Phải thấy `Issuer` là root, `BasicConstraints CA:true` và `KeyUsage keyCertSign`. Nếu thiếu các extension CA quan trọng, `CertPathBuilder`/`CertPathValidator` có thể từ chối intermediate trong vai trò CA, dù chữ ký thô kiểm tra đúng.

### Câu hỏi nhỏ

Tại sao ta không lấy chứng thư self-signed placeholder ở `intermediate.p12` làm intermediate của path? Vì certificate placeholder không mang **chữ ký phát hành bởi root CA**; nó không tạo đường tin cậy từ leaf qua intermediate tới root.

## 6. Tạo leaf/server certificate do intermediate ký

Tạo private key server và certificate placeholder:

```bash
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=lab.example.test,O=PKIX Lab,C=VN" -validity 365 -storetype PKCS12 -keystore server.p12 -storepass changeit -keypass changeit -noprompt
```

Tạo CSR (đề xuất SAN):

```bash
keytool -certreq -alias server -keystore server.p12 -storepass changeit -file server.csr -ext "SAN=dns:lab.example.test"
```

Intermediate ký certificate server và **phát hành extension được kiểm soát ở phía CA**:

```bash
keytool -gencert -alias intermediate -keystore intermediate.p12 -storepass changeit -infile server.csr -outfile server.pem -rfc -validity 90 -sigalg SHA256withRSA -ext "BC:critical=ca:false" -ext "KU:critical=digitalSignature,keyEncipherment" -ext "EKU=serverAuth" -ext "SAN=dns:lab.example.test"
```

Các extension có ý nghĩa:

- `BC:critical=ca:false`: leaf không được làm CA phát hành certificate con.
- `KU:critical=digitalSignature,keyEncipherment`: ví dụ tương thích RSA/TLS rộng; các giao thức/phiên bản TLS khác nhau có yêu cầu key usage khác. Không coi `keyEncipherment` luôn bắt buộc cho TLS 1.3.
- `EKU=serverAuth`: nhận diện mục đích server authentication.
- `SAN=dns:lab.example.test`: danh tính DNS để ứng dụng TLS kiểm tra, **không** phải một trust anchor.
- `-validity 90`: certificate có tuổi thọ ngắn để thử tình huống quá hạn một cách dễ xác định.

Lệnh trên được dùng trong lab để tạo certificate riêng; nếu đưa leaf vào một TLS server thực, bạn thường phải **import certificate chain reply vào `server.p12`** theo đúng thứ tự và cấu hình key manager phù hợp. Nhưng bài này chỉ dùng PEM công khai để build/validate PKIX, nên chưa cần chạy TLS server.

Kiểm tra:

```bash
keytool -printcert -file server.pem
```

Bây giờ sơ đồ quan hệ phải là:

```text
root.pem                root private key trong root.p12
  |
  +-- chữ ký trên intermediate.pem
                         intermediate private key trong intermediate.p12
                            |
                            +-- chữ ký trên server.pem
                                               server private key trong server.p12
```

**Không bao giờ** lấy private key của root hay intermediate đưa vào kho khóa của server chỉ để làm cho validator thành công. Validator chỉ cần public certificate và nguồn trust anchor đáng tin.

## 7. Tạo một root không liên quan để làm negative test

Bài lab cần kiểm tra trường hợp chứng thư ký đúng nhưng **chain không dẫn tới root được tin cậy**. Tạo thêm một root CA độc lập:

```bash
keytool -genkeypair -alias other -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=Unrelated Root,O=PKIX Lab,C=VN" -validity 3650 -ext "BC:critical=ca:true,pathlen:1" -ext "KU:critical=keyCertSign,cRLSign" -storetype PKCS12 -keystore unrelated.p12 -storepass changeit -keypass changeit -noprompt
```

```bash
keytool -exportcert -alias other -keystore unrelated.p12 -storepass changeit -file unrelated.pem -rfc
```

Certificate `unrelated.pem` có cấu trúc CA hợp lệ, nhưng không phải issuer của `intermediate.pem`. Đây là lý do không được nhầm “self-signed CA có hình thức hợp lệ” với “được ứng dụng tin cậy”.

## 8. Viết chương trình Java 17+ xây và kiểm tra CertPath

Chương trình hoàn chỉnh dưới đây đọc **bốn chứng thư PEM công khai**, thiết lập trust anchor từ `root.pem` và dùng intermediate/leaf làm candidate. Sau khi build thành công, nó chủ động gọi validator rồi làm hai phép thử âm tính:

- **Wrong root:** kiểm tra cùng một CertPath với trust anchor `unrelated.pem` và chờ `CertPathValidatorException`.
- **Expired leaf:** đặt ngày xác minh bằng **một ngày sau `NotAfter` của leaf** và chờ `CertPathValidatorException`.

Việc sử dụng ngày tương lai trong negative test giúp bài test có thể tái lập mà không phải chờ chứng thư thực sự hết hạn.

**Tệp `PkixLab.java` (Java 17+, độc lập):**

```java
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class PkixLab {
    static X509Certificate read(String path) throws Exception {
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: java PkixLab root.pem intermediate.pem server.pem unrelated.pem");
        }

        X509Certificate root = read(args[0]);
        X509Certificate intermediate = read(args[1]);
        X509Certificate leaf = read(args[2]);
        X509Certificate unrelatedRoot = read(args[3]);

        // 1. Chọn CHÍNH XÁC leaf làm đích cần tìm đường tin cậy.
        X509CertSelector target = new X509CertSelector();
        target.setCertificate(leaf);

        // 2. TrustAnchor là root đã tin cậy từ trước.
        PKIXBuilderParameters buildParams = new PKIXBuilderParameters(
                Set.of(new TrustAnchor(root, null)), target);

        // Lab offline không phát hành CRL hay chạy OCSP. KHÔNG sao chép
        // thiết lập này thành mặc định cho hệ thống production.
        buildParams.setRevocationEnabled(false);

        // 3. CertStore là NGUỒN ỨNG VIÊN, không phải truststore.
        buildParams.addCertStore(CertStore.getInstance(
                "Collection",
                new CollectionCertStoreParameters(
                        List.of(leaf, intermediate))));

        // 4. Build path. Builder thực hiện validation trong quá trình tìm.
        PKIXCertPathBuilderResult path = (PKIXCertPathBuilderResult)
                CertPathBuilder.getInstance("PKIX").build(buildParams);

        System.out.println("Built path length: "
                + path.getCertPath().getCertificates().size());

        // 5. Chủ động validate CertPath đã có để thấy sự khác biệt API.
        PKIXParameters validation = new PKIXParameters(
                Set.of(new TrustAnchor(root, null)));
        validation.setRevocationEnabled(false);

        PKIXCertPathValidatorResult verified = (PKIXCertPathValidatorResult)
                CertPathValidator.getInstance("PKIX")
                        .validate(path.getCertPath(), validation);

        System.out.println("PKIX validated: "
                + verified.getTrustAnchor().getTrustedCert().equals(root));

        // Negative test A: cùng CertPath, nhưng chỉ tin một root KHÁC.
        PKIXParameters wrongAnchor = new PKIXParameters(
                Set.of(new TrustAnchor(unrelatedRoot, null)));
        wrongAnchor.setRevocationEnabled(false);

        boolean wrongAnchorRejected = false;
        try {
            CertPathValidator.getInstance("PKIX")
                    .validate(path.getCertPath(), wrongAnchor);
        } catch (CertPathValidatorException expected) {
            wrongAnchorRejected = true;
        }
        System.out.println("Wrong root rejected: " + wrongAnchorRejected);

        // Negative test B: đặt ngày xác minh sau khi leaf hết hạn.
        PKIXParameters future = new PKIXParameters(
                Set.of(new TrustAnchor(root, null)));
        future.setRevocationEnabled(false);
        future.setDate(new Date(leaf.getNotAfter().getTime() + 86400000L));

        boolean expiredRejected = false;
        try {
            CertPathValidator.getInstance("PKIX")
                    .validate(path.getCertPath(), future);
        } catch (CertPathValidatorException expected) {
            expiredRejected = true;
        }
        System.out.println("Expired leaf rejected: " + expiredRejected);
    }
}
```

Lệnh biên dịch/chạy trong cùng thư mục với bốn file PEM:

```bash
javac PkixLab.java
java PkixLab root.pem intermediate.pem server.pem unrelated.pem
```

**Stdout đã kiểm chứng với bộ certificate tạo bằng các lệnh `keytool` nêu trên:**

```text
Built path length: 2
PKIX validated: true
Wrong root rejected: true
Expired leaf rejected: true
```

Với bộ chứng thư khác, ngày đã thay đổi, thiếu PEM hoặc chính sách JDK nghiêm ngặt hơn ngăn thuật toán/kích thước khóa, chương trình có thể ném exception ngay từ bước build thay vì in bốn dòng trên. Đây là tín hiệu điều kiện môi trường/lab chưa đúng, không phải điều kiện để vô hiệu hóa các bảo vệ của PKIX. Ví dụ đã dùng RSA 2048 và SHA-256 để tương thích với JDK tiêu chuẩn hỗ trợ những thuật toán đó; JDK của doanh nghiệp có thể áp dụng ngưỡng cao hơn.

## 9. Phân tích chương trình từng bước

### 9.1. CertificateFactory đọc X.509

```java
CertificateFactory factory = CertificateFactory.getInstance("X.509");
try (InputStream in = Files.newInputStream(Path.of("server.pem"))) {
    X509Certificate leaf = (X509Certificate)
            factory.generateCertificate(in);
}
```

Đây là fragment độc lập về ý tưởng, yêu cầu các import tương ứng và file `server.pem` từ lab. `CertificateFactory` chỉ parse certificate; parse thành công không chứng minh chữ ký hợp lệ, CA đáng tin, ngày hợp lệ hay hostname khớp.

### 9.2. X509CertSelector xác định leaf

```java
X509CertSelector selector = new X509CertSelector();
selector.setCertificate(leaf);
```

Đây là selector **khớp chính xác certificate leaf** của lab. Trên hệ thống lớn, selector có thể dựa vào subject, serial number, certificate policy, key usage... nhưng phải kiểm soát sự không duy nhất và nguồn certificate. Đừng dùng mỗi chuỗi CN để quyết định đang kiểm tra certificate nào.

### 9.3. TrustAnchor quyết định nơi kết thúc đường tin cậy

```java
TrustAnchor anchor = new TrustAnchor(root, null);
```

`null` là optional name constraints cho đối tượng `TrustAnchor` này, **không phải tuyên bố tất cả name constraints trên certificate chain sẽ bị bỏ qua**. Vì trust anchor là điểm được tin do cấu hình chọn, các trường như thời hạn, chữ ký self-signed và nhiều extension của chính certificate root không được xác minh theo cùng cách với certificate nằm **trong** `CertPath`. Điều đó làm việc đưa đúng root vào truststore trở nên đặc biệt quan trọng.

Trong production, root phải được đưa vào qua quy trình quản trị truststore/root CA (hoặc trust anchors của hệ điều hành phù hợp). Không bao giờ tạo `new TrustAnchor(peerSuppliedCertificate, null)` trực tiếp từ chứng thư đối tác vừa gửi mà không có cơ chế tin cậy ngoài băng: cách đó sẽ biến đầu vào chưa tin thành nguồn tin.

### 9.4. CertStore cung cấp các intermediate ứng viên

```java
CertStore candidates = CertStore.getInstance(
        "Collection",
        new CollectionCertStoreParameters(
                List.of(leaf, intermediate)));
buildParams.addCertStore(candidates);
```

Ứng dụng thực có thể có nhiều intermediate từ chứng thư máy chủ gửi, kho CA nội bộ hoặc LDAP/AIA fetcher. Việc truy vấn AIA tải certificate bổ sung là tính năng phụ thuộc implementation/cấu hình; **không giả định Java luôn tự tải intermediate thiếu từ Internet**.

Khi thí nghiệm thấy `CertPathBuilderException: unable to find valid certification path`, trước hết kiểm tra certificate ứng viên có đúng intermediate issuer không, certificate CA có đúng `BasicConstraints`/`KeyUsage` không và trust anchor có đúng root không. Đừng sửa truststore bằng cách thêm thẳng certificate leaf để ép thành công, vì như vậy đã đổi bài toán tin cậy.

### 9.5. Builder và Validator khác nhau thế nào?

- `CertPathBuilder` nhận **mục tiêu** + **trust anchor** + **candidate stores** + constraints; tìm ra một path hợp lệ trong số nhiều đường có thể có. Builder của PKIX có thực hiện kiểm tra tính hợp lệ trong quá trình tìm.
- `CertPathValidator` nhận một **CertPath đã có** + `PKIXParameters` và kiểm tra path đó. Nếu ứng dụng đã có đúng danh sách leaf → intermediate, chỉ cần validator có thể là đủ.
- `PKIXCertPathBuilderResult` cung cấp `getCertPath()`, `getTrustAnchor()`, `getPolicyTree()`, `getPublicKey()`. `PKIXCertPathValidatorResult` cũng cung cấp các trường như trust anchor và policy tree.
- Builder tìm đường thành công không thay thế bước kiểm tra danh tính endpoint của ứng dụng, cũng không tự kích hoạt chính sách thu hồi khi bạn đã `setRevocationEnabled(false)`.

### 9.6. Vì sao có đúng hai certificate trong path?

Java `CertPath` (dạng `PkiPath`/`X.509` trong bài) thường liệt kê theo thứ tự **leaf → intermediate**, **không bao gồm trust anchor**:

```text
getCertPath().getCertificates()  -> [server.pem, intermediate.pem]
getTrustAnchor().getTrustedCert() -> root.pem
```

Dấu `[ ]` trong sơ đồ là mô tả thứ tự, không phải output raw certificate. Vì thế phải hiểu `2` là số certificate **trong đường được kiểm tra**, khác tổng số certificate của mô hình PKI.

## 10. Có thể validate một đường dẫn đã biết mà không chạy Builder

Một bài thực hành bổ sung: nếu bạn đã có chính xác leaf và intermediate, hãy tạo `CertPath` trực tiếp. Lưu ý thứ tự phải từ **leaf đi lên CA**; root là trust anchor nên không thêm vào danh sách.

**Fragment đặt trong `main` và dùng các biến `leaf`, `intermediate`, `root` của chương trình ở mục 8:**

```java
CertificateFactory factory = CertificateFactory.getInstance("X.509");
java.security.cert.CertPath manualPath =
        factory.generateCertPath(List.of(leaf, intermediate));

PKIXParameters params = new PKIXParameters(
        Set.of(new TrustAnchor(root, null)));
params.setRevocationEnabled(false); // Chỉ dành cho lab offline.

PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult)
        CertPathValidator.getInstance("PKIX")
                .validate(manualPath, params);
System.out.println(result.getTrustAnchor().getTrustedCert()
        .equals(root)); // true
```

Đây là **fragment tích hợp**, không phải file Java độc lập: dùng import và biến đã có trong `PkixLab.java`. Nếu đảo ngược thành `List.of(intermediate, leaf)`, validator có thể từ chối vì certification path không có thứ tự leaf-to-anchor đúng.

Điểm cần nhớ: `CertPathBuilder` có thể **lựa chọn giữa nhiều intermediate**; `CertPathValidator` không tự làm lại nhiệm vụ tìm đường khi path bạn đưa vào sai.

## 11. Cách kiểm tra cấu trúc và chữ ký mà không nhầm với PKIX

Các lệnh inspect (không hiện private key):

```bash
keytool -printcert -file root.pem
keytool -printcert -file intermediate.pem
keytool -printcert -file server.pem
```

Đọc các trường sau:

| Certificate | Subject | Issuer | BasicConstraints | KeyUsage / EKU / SAN |
| --- | --- | --- | --- | --- |
| Root | Lab Root CA | Lab Root CA | CA true, pathLen 1 | keyCertSign, cRLSign |
| Intermediate | Lab Intermediate CA | Lab Root CA | CA true, pathLen 0 | keyCertSign, cRLSign |
| Leaf | lab.example.test | Lab Intermediate CA | CA false | digitalSignature, keyEncipherment; EKU serverAuth; SAN DNS:lab.example.test |

`root.getSubjectX500Principal().equals(root.getIssuerX500Principal())` có thể trả `true` cho root tự ký, nhưng điều đó **không xác thực** nó là trust anchor thật hay chữ ký root hợp lệ. `leaf.verify(intermediate.getPublicKey())` kiểm tra chữ ký leaf, nhưng không kiểm tra toàn bộ đường, hạn sử dụng, key usage, constraints hoặc chính sách thuật toán. Công cụ đúng cho yêu cầu đầy đủ là `CertPathValidator("PKIX")` với chính sách được đặt rõ.

Có thể kiểm tra hình thức chuỗi bằng `keytool` mà không tiết lộ bí mật, nhưng đừng gửi output có hostname, thông tin tổ chức hoặc đường dẫn nội bộ nếu dữ liệu đó cần bảo vệ.

## 12. Negative tests: hiểu được vì sao PKIX từ chối

### 12.1. Sai root / trust anchor

`unrelated.pem` là CA khác, có chữ ký tự ký đúng và `CA:true`. Nhưng nó **không ký** intermediate. Khi gọi validator với tập anchor chỉ có `unrelated.pem`, path `server → intermediate` không thể nối tới anchor; chương trình phải in:

```text
Wrong root rejected: true
```

**Bài học:** không được tin một CA chỉ vì certificate có nhãn “CA” hoặc self-signed. Trust là quyết định của hệ thống kiểm tra, dựa trên nguồn root được cấp có kiểm soát.

### 12.2. Leaf đã hết hạn

Kết quả của:

```java
future.setDate(new Date(leaf.getNotAfter().getTime() + 86400000L));
```

là yêu cầu PKIX đánh giá tại thời điểm sau `NotAfter` của leaf **một ngày**. Negative test được viết để kỳ vọng validator ném `CertPathValidatorException`:

```text
Expired leaf rejected: true
```

Đây không phải cách sửa thời gian hệ thống và không làm thay đổi certificate. Trong thực tế, phải gia hạn/cấp lại leaf trước khi hết hạn, rồi kiểm tra sự tương thích của chain và secret/key rotation.

### 12.3. Thiếu intermediate trong ứng viên

Hãy thử sửa code **trong một bản sao bài lab**: chỉ cung cấp `List.of(leaf)` cho `CollectionCertStoreParameters`, không cung cấp intermediate. Nếu builder không có nguồn intermediate khác và root chưa ký trực tiếp leaf, `CertPathBuilder.build` sẽ thất bại.

Bài kiểm tra này phụ thuộc môi trường: JVM có thể có cơ chế lấy certificate bổ sung theo cấu hình, vì vậy hãy chạy trong môi trường cô lập và kiểm soát các `CertStore`/tùy chọn liên quan để tránh false positive. Không kết luận “Java tự tải được intermediate” chỉ vì máy test có cache/nguồn riêng.

### 12.4. Intermediate không được phép làm CA

Thử cấp intermediate certificate có `BC:critical=ca:false` hoặc thiếu `keyCertSign` khi policy yêu cầu. Khi intermediate đứng trong vai trò CA của path, PKIX phải từ chối nếu các constraint bắt buộc bị vi phạm.

Chỉ thay certificate PEM intermediate là không đủ để đảm bảo một thử nghiệm đúng: nếu bạn phát hành một certificate mới, hãy chắc rằng chữ ký leaf và issuer/key khớp với intermediate mới, và thời hạn/constraints của các certificate khác không gây lỗi trước làm che mất nguyên nhân định kiểm tra.

### 12.5. Thuật toán yếu bị security policy chặn

JDK có security property `jdk.certpath.disabledAlgorithms` có thể từ chối certificate/khóa không đạt yêu cầu như MD5 hoặc RSA key size yếu. Giá trị cụ thể phụ thuộc JDK/version/vendor. Xem effective policy bằng:

```java
System.out.println(java.security.Security.getProperty(
        "jdk.certpath.disabledAlgorithms"));
```

Đây là fragment dùng trong `main` hoặc JShell. Không hardcode danh sách mặc định của một JDK cụ thể và không nới policy chỉ để cho một chứng thư lab yếu đi qua. Với TLS, còn có các hạn chế riêng như `jdk.tls.disabledAlgorithms`.

### 12.6. Revocation thực tế

Ở chương trình lab, `setRevocationEnabled(false)` được dùng vì **không có CRL distribution point/OCSP responder thử nghiệm**. Điều này làm bài học có thể chạy offline và tập trung vào chain/PKIX path. Nó **không chứng minh** certificate chưa bị thu hồi. Một triển khai thực tế phải quyết định rõ nguồn CRL/OCSP, timeout, soft-fail hay hard-fail theo chính sách an ninh và rủi ro nghiệp vụ; kết quả xây path chỉ có ý nghĩa trong phạm vi policy đã thực sự kích hoạt.

## 13. Những tham số PKIX đáng biết trong triển khai thực tế

| Tùy chọn/API | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `PKIXParameters(Set<TrustAnchor>)` | Danh sách điểm neo tin cậy | Không lấy trực tiếp root từ peer chưa được xác minh |
| `PKIXBuilderParameters(Set<TrustAnchor>, CertSelector)` | Thêm mục tiêu để builder chọn đường | Target selector phải đúng leaf/certificate mục tiêu |
| `setDate(Date)` | Thời điểm kiểm tra hiệu lực certificate | Mặc định dùng thời gian hiện tại; kiểm thử quá hạn đặt ngày rõ ràng |
| `setRevocationEnabled(boolean)` | Bật/tắt phần revocation của PKIX | Lab tắt có chủ đích; production có yêu cầu riêng |
| `setMaxPathLength(int)` | Giới hạn số CA trung gian được builder chấp nhận | Khác với `BasicConstraints.pathLenConstraint` được ghi trong certificate |
| `setInitialPolicies(Set<String>)` | Ràng buộc certificate policy OID | Phải hiểu PKIX policy tree, không đặt bừa |
| `setExplicitPolicyRequired(boolean)` | Yêu cầu explicit policy khi cần | Không bật chỉ để “mạnh hơn” nếu chưa thiết kế policy |
| `addCertStore(CertStore)` | Nguồn chứng thư/CRL ứng viên | Không trao quyền tin cậy cho certificate mới |
| `addCertPathChecker(...)` | Kiểm tra ràng buộc mở rộng cho path | Cần hiểu cách xử lý critical extensions và trạng thái checker |
| `getTrustAnchor()` trên result | Anchor đã được dùng trong đường được xây/kiểm tra | Có thể có nhiều anchor; phải kiểm tra anchor mong muốn |

**Giới hạn path length:** thuộc tính `PKIXBuilderParameters.setMaxPathLength(0)` ngăn builder dùng intermediate CA ở giữa target và trust anchor, trong khi intermediate certificate có `pathLen=0` vẫn có thể ký leaf. Hai loại con số này dễ bị nhầm. Thí nghiệm của chúng ta cần **một intermediate**, nên không áp một builder max path length bằng 0.

### Load trust anchors từ KeyStore trong ứng dụng thật

Java hỗ trợ constructor `PKIXBuilderParameters(KeyStore keystore, CertSelector targetConstraints)`. Khi dùng, những trusted certificate entries trong keystore có thể trở thành trust anchors cho builder. Để hình dung cách chuẩn bị truststore trong lab, có thể import root certificate công khai:

```bash
keytool -importcert -alias lab-root -file root.pem -keystore lab-truststore.p12 -storetype PKCS12 -storepass changeit -noprompt
```

**Phải kiểm tra fingerprint của root trước khi nhập** trong hệ thống thật. `-noprompt` chỉ chấp nhận được ở đây vì certificate root do chính lab tạo, không phải file tải từ bên ngoài.

Fragment Java tích hợp:

```java
java.security.KeyStore trustStore =
        java.security.KeyStore.getInstance("PKCS12");
try (java.io.InputStream input =
        java.nio.file.Files.newInputStream(
                java.nio.file.Path.of("lab-truststore.p12"))) {
    trustStore.load(input, "changeit".toCharArray()); // DEMO ONLY
}

X509CertSelector selector = new X509CertSelector();
selector.setCertificate(leaf);
PKIXBuilderParameters params =
        new PKIXBuilderParameters(trustStore, selector);
params.setRevocationEnabled(false); // Lab offline only.
```

Fragment trên giả định `leaf` và các import tương ứng đã có. Nó không tự có intermediate candidate: khi chuyển từ `Set<TrustAnchor>` sang `KeyStore`, vẫn phải cung cấp `CertStore` với intermediate hoặc một nguồn ứng viên hợp lệ. Root là anchor; certificate intermediate không nên nhập thành anchor chỉ để làm đường build ngắn đi.

## 14. Tích hợp PKIX với HTTPS/mTLS

Bài này thực hành **offline Java CertPath**. Trong một kết nối HTTPS/mTLS thực tế, JSSE có thêm các yếu tố:

1. TLS handshake chọn protocol/cipher/signature scheme và nhận certificate chain từ peer.
2. `X509TrustManager`/PKIX validator kiểm tra certificate path theo truststore/chính sách phù hợp.
3. Client kiểm tra đúng server hostname/SAN. Nếu dùng `SSLEngine` hoặc `SSLSocket` thuần, application phải cấu hình endpoint identification phù hợp; `HttpsURLConnection` có quy tắc hostname verification riêng.
4. Nếu là mTLS, server phải xác thực **client certificate**, trust anchors và authorization identity của client; certificate hợp lệ không tự cấp quyền truy cập API.
5. Kiểm tra revocation khi chính sách yêu cầu; lưu ý hạn chế thuật toán từ `jdk.certpath.disabledAlgorithms` và `jdk.tls.disabledAlgorithms` trong JSSE.
6. Đóng kết nối, quản lý key/keystore/password/truststore và monitor certificate lifecycle.

Có thể test riêng offline PKIX mà chưa chạy TLS; kết quả đó có ích để bóc tách vấn đề certificate/CA. Nhưng “`PkixLab` báo validated=true” không chứng minh bạn đã cấu hình đúng HTTPS, hostname verification hoặc server có private key phù hợp để hoàn tất handshake.

**Sai lầm nguy hiểm:** xây `TrustManager` trả về mà không kiểm tra gì, hoặc thêm mọi certificate do server gửi vào trust anchors. Làm vậy biến một kết nối TLS có vấn đề thành “thành công” bằng cách bỏ qua nền tảng của sự tin cậy.

## 15. Gỡ lỗi khi path build/validation thất bại

| Triệu chứng | Nguyên nhân thường gặp | Cách xác minh |
| --- | --- | --- |
| `CertPathBuilderException: unable to find valid certification path` | Thiếu intermediate, trust anchor không khớp, cấu trúc/constraints không hợp lệ | Kiểm tra Subject/Issuer, file PEM, `CertStore`, anchor, BC/KU và root |
| `CertPathValidatorException: Path does not chain with any of the trust anchors` | Trust anchors đang dùng không phải issuer cuối của path | In fingerprint root có trong truststore và root ký intermediate |
| `CertPathValidatorException: validity check failed` | Chứng thư chưa tới `NotBefore` hoặc đã quá `NotAfter` | Xem ngày xác minh, certificate validity, đồng hồ của process |
| `CertPathValidatorException: basic constraints check failed` | CA:false hoặc pathLenConstraint bị vi phạm | Xem BasicConstraints của intermediate và số CA con |
| `CertPathValidatorException: key usage check failed` | CA certificate thiếu quyền `keyCertSign` | Kiểm tra KeyUsage của CA |
| `CertPathValidatorException: algorithm constraints check failed` | Thuật toán/chìa khóa bị JDK policy cấm | Xem effective `jdk.certpath.disabledAlgorithms`, kích thước khóa, thuật toán ký |
| `CertificateException` khi đọc PEM | Sai đường dẫn, file rỗng, không phải X.509 certificate | `keytool -printcert -file ...`; không lấy file CSR làm certificate |
| `InvalidAlgorithmParameterException` | Không có trust anchor hợp lệ hoặc parameters sai | Kiểm tra `Set<TrustAnchor>`/keystore và selector |
| Root khác được dùng ngoài dự kiến | Có nhiều trust anchors, builder chọn path khác | Kiểm tra `result.getTrustAnchor()` và path certificate list |

Không nên bắt mọi `GeneralSecurityException` rồi in `"validated: true"`. Trong ví dụ, **chỉ hai negative test** bắt `CertPathValidatorException` như một kết quả mong đợi; lỗi ở bước build hoặc validate chính phải làm chương trình thất bại.

### Debug có kiểm soát

Bật trace của cơ chế CertPath trong một môi trường test:

```bash
java -Djava.security.debug=certpath PkixLab root.pem intermediate.pem server.pem unrelated.pem
```

Output debug tùy JDK; nó có thể rất dài, chứa tên issuer/subject, certificate policy, alias hoặc metadata cấu hình. Không bật vô hạn ở production. Đọc **nguyên nhân gốc**, không chỉ vài dòng exception cuối cùng. Trường hợp fail ở bước tìm certificate khác trường hợp tìm được path nhưng constraints/algorithm không qua.

Một chuỗi đã build thành công với revocation **off** không thể dùng để kết luận hệ thống đã đáp ứng yêu cầu an ninh production về CRL/OCSP.

## 16. Bài tập mở rộng có thể thực hiện an toàn

### Bài tập 1 — Trực tiếp so sánh builder và validator

Thay phần build trong `PkixLab` bằng danh sách cố định `List.of(leaf, intermediate)` qua `CertificateFactory.generateCertPath`. Dùng cùng anchor và validation. Kết quả nên thành công. Đảo thứ tự danh sách rồi thử lại; giải thích lỗi theo hướng path từ **target lên issuer**.

### Bài tập 2 — Certificate chain thiếu CA trung gian

Tạo bản sao của chương trình, bỏ intermediate khỏi `CertStore`. Dự đoán khả năng tìm đường và chứng minh bằng exception trong môi trường không có certificate source khác. Giải thích vì sao **root certificate đúng không đủ** khi nó không trực tiếp ký leaf.

### Bài tập 3 — Path length

Thử `buildParams.setMaxPathLength(0)` rồi build lại. Trong mô hình của lab cần một intermediate CA, nên yêu cầu này quá hạn chế. So sánh với `pathLen=0` **nằm trong certificate intermediate** và giải thích vì sao intermediate vẫn có thể ký leaf.

### Bài tập 4 — Sai ngày kiểm tra

Đổi `setDate` sang một ngày **trước `NotBefore` của leaf**, rồi chạy negative validation. Giải thích khác biệt `NOT_YET_VALID` và `EXPIRED` (các lý do exception có thể khác nhau theo implementation).

### Bài tập 5 — Thay root tin cậy

Giữ nguyên leaf và intermediate, thay `Set.of(new TrustAnchor(root, null))` bằng root `unrelatedRoot`. Không sửa private key/certificate, chỉ thay điểm neo tin cậy. Giải thích lý do thất bại dù các certificate vẫn có chữ ký hợp lệ từ khóa ban đầu.

### Bài tập 6 — Tìm hiểu hostname

Dùng `leaf.getSubjectAlternativeNames()` để xem DNS SAN của certificate server. Giải thích vì sao không thể chỉ dựa vào `CertPathValidator.validate` để nói server có đúng tên `lab.example.test` hay không. Đề xuất vị trí kiểm tra hostname ở lớp TLS/application.

### Bài tập 7 — Thiết kế policy revocation

Không cần dựng một OCSP responder thật trong lab cơ bản. Viết bảng quyết định cho production: nguồn CRL/OCSP là gì, cache và timeout bao lâu, khi mất kết nối tới nguồn thu hồi thì chặn hay cho qua, cách test một serial đã bị revoke. Khi muốn thực hành revocation, cần tạo CRL hợp lệ hoặc vận hành responder test rồi bật `PKIXRevocationChecker`/tham số tương ứng một cách có kiểm soát.

## 17. Checklist hoàn tất lab

- [ ] Có đủ `root.pem`, `intermediate.pem`, `server.pem`, `unrelated.pem` và file `PkixLab.java`.
- [ ] Root là self-signed CA, intermediate do root ký, leaf do intermediate ký.
- [ ] Intermediate có `CA:true`, `keyCertSign` và `pathLen=0`; leaf có `CA:false`, SAN cho `lab.example.test`.
- [ ] `javac PkixLab.java` biên dịch thành công bằng JDK 17+.
- [ ] Chạy Java cho kết quả `Built path length: 2` và `PKIX validated: true`.
- [ ] Các phép thử root sai và ngày hết hạn đều in `true` cho trạng thái bị từ chối.
- [ ] Hiểu rằng trust anchor không nằm trong `CertPath`; `CertStore` không cấp trust.
- [ ] Hiểu giới hạn của lab: revocation off, chưa xác minh hostname và chưa chạy TLS thực tế.
- [ ] Không commit private key `.p12`, CSR bí mật hoặc mật khẩu lab vào kho mã nguồn.
- [ ] Xóa thư mục lab/khóa thử nghiệm khi không cần nữa, theo chính sách giữ artifact nội bộ.

## Tài liệu tham khảo chính thức

- [Java 17 — `CertPathBuilder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathBuilder.html), [`CertPathValidator`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertPathValidator.html), [`CertificateFactory`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertificateFactory.html).
- [Java 17 — `PKIXBuilderParameters`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXBuilderParameters.html), [`PKIXParameters`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html), [`TrustAnchor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/TrustAnchor.html).
- [Java 17 — `X509Certificate`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509Certificate.html), [`X509CertSelector`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/X509CertSelector.html), [`CertStore`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/CertStore.html).
- [Java 17 — `keytool`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html) — `-genkeypair`, `-certreq`, `-gencert`, `-exportcert` và các certificate extensions.
- [RFC 5280 — Internet X.509 Public Key Infrastructure Certificate and CRL Profile](https://www.rfc-editor.org/rfc/rfc5280).

**Kết nối kiến thức:** phần tạo certificate là quy trình phát hành khóa và chứng thư; phần `CertPathBuilder` là tìm đường từ certificate mục tiêu tới trust anchor; phần `CertPathValidator` là kiểm tra đường đó theo PKIX và policy bạn thực sự bật. Nếu không phân biệt rõ **nguồn ứng viên**, **nguồn tin cậy**, **bộ ràng buộc** và **danh tính endpoint**, một demo chạy thành công vẫn có thể dẫn đến triển khai TLS không an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-practice-008">23.8 Thiết lập kết nối Java mTLS và kiểm thử các tình huống handshake thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# 23.8. Thiết lập kết nối Java mTLS và kiểm thử các tình huống handshake thất bại

> Bài thực hành tổng hợp Java 17+: tạo certificate/keystore/truststore cho hai phía, cấu hình JSSE `SSLContext` với `KeyManagerFactory` và `TrustManagerFactory`, bắt buộc client authentication, kiểm tra hostname của server, chạy một phiên mTLS thành công và xác nhận các trường hợp handshake hoặc trao đổi ứng dụng bị từ chối. Bài lab chạy trên loopback; không cần dịch vụ internet.

## 1. Mục tiêu và tiêu chí hoàn thành

Bạn sẽ thực hành:

1. Giải thích TLS một chiều so với **mutual TLS (mTLS)** và hai hướng kiểm tra chứng chỉ trong TLS handshake.
2. Phân biệt `keystore` (private key + certificate chain của chính bên mình) với `truststore` (trust anchors/certificates dùng để xác minh peer).
3. Tạo hai danh tính riêng `server` và `client` bằng `keytool`, phân phối đúng **chứng chỉ công khai**, giữ private key trong keystore.
4. Cấu hình `SSLServerSocket.setNeedClientAuth(true)`, `SSLSocket` và `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")`.
5. Chạy chương trình Java 17+ **độc lập** để chứng minh 5 kết quả: mTLS hợp lệ thành công; không có client certificate, server không được tin cậy, hostname sai và hai phía không có phiên bản TLS chung đều thất bại.
6. Phân tích khác biệt giữa **TLS handshake thất bại**, **TLS thành công nhưng không có xác thực danh tính ứng dụng**, và **TLS thành công nhưng ứng dụng từ chối quyền**.
7. Biết cách chẩn đoán bằng exception, JSSE debug, metadata của certificate, protocol/cipher đã thương lượng mà không tắt trust validation hoặc kiểm tra hostname.

### Điều kiện đạt bài

- Client và server cùng xác minh peer khi phiên mTLS hợp lệ; sau handshake, server có thể lấy client principal bằng `SSLSession.getPeerPrincipal()`.
- Mỗi negative test bị từ chối vì điều kiện bảo mật cụ thể. **Không** coi `SocketException` chung hay một kết nối ngẫu nhiên thất bại là bằng chứng rằng certificate validation hoạt động.
- Không dùng `trust-all TrustManager`, không bỏ hostname verification, không cố bật lại TLS yếu để ép bài test thành công.
- Đầu ra chương trình khớp mẫu ở mục 7 trên môi trường JSSE phù hợp.

## 2. Nhìn đúng về mTLS

TLS thông thường trong HTTPS cho phép **client xác thực server**: server đưa certificate chain và chứng minh sở hữu private key, còn client kiểm tra chain, tính hợp lệ, tên endpoint và các ràng buộc mật mã. mTLS bổ sung bước **server yêu cầu và xác thực certificate của client** ngay trong TLS handshake.

```text
Client                                    Server
  | ---- ClientHello (TLS versions,...) -> |
  | <- ServerHello + server certificate -- |
  | <- CertificateRequest (khi mTLS) ---- |
  | ---- client certificate + proof ----> |
  | <------ Finished / Finished --------> |
  |            HANDSHAKE COMPLETE           |
  | <--- application data được bảo vệ ---> |
```

Đây là sơ đồ logic: thứ tự message và cách trao đổi khóa thay đổi giữa TLS 1.2 và TLS 1.3; không dùng sơ đồ này để tự viết TLS wire protocol. JSSE đảm nhiệm handshake và dữ liệu đã mã hóa.

**Hai hướng trust không hoán đổi cho nhau:**

| Bên kiểm tra | Chứng chỉ cần tin | Cách xác minh | Ý nghĩa |
| --- | --- | --- | --- |
| Client | Server certificate chain | `TrustManager` + hostname/SAN validation + TLS policies | Client kết nối đúng service đích và không bị kẻ trung gian mạo danh. |
| Server | Client certificate chain | `TrustManager` phía server + `needClientAuth=true` | Kết nối có certificate client được server tin theo chính sách trust. |
| Ứng dụng trên server | Identity của certificate client đã xác thực | Ánh xạ principal/fingerprint/SAN và authorization policy | Client được phép gọi API/thao tác tài nguyên nào. |

Một certificate được CA tin cậy **không** tự cho phép client gọi mọi API. TLS xác nhận danh tính và bảo vệ kênh; authorization là một kiểm tra tiếp theo của ứng dụng.

### Keystore, truststore, key manager và trust manager

- **Identity keystore:** lưu private key và certificate của chính endpoint. `KeyManagerFactory` chọn certificate/private key để chứng minh danh tính trong handshake. Phải hạn chế quyền đọc file và quản lý password qua secret manager.
- **Truststore:** lưu trust anchors/certificates mà endpoint dùng để xác minh peer. `TrustManagerFactory` triển khai path validation và trust decision; không có private key của peer.
- **`SSLContext`:** giữ key managers, trust managers, nguồn random và TLS session state; tạo `SSLSocketFactory`/`SSLServerSocketFactory`.
- **`SSLParameters`:** các tham số phiên như protocol, cipher suites, endpoint identification; cấu hình **trước khi bắt đầu handshake**.
- **`needClientAuth` vs `wantClientAuth`:** `need` bắt buộc client cung cấp authentication certificate hợp lệ; `want` chỉ đề nghị, nên client thiếu certificate vẫn có thể kết nối. Với endpoint cần mTLS bắt buộc dùng `need`.

> Tại phòng lab này, truststore chứa **certificate tự ký của chính peer** để bài dễ tự chạy và thấy ranh giới trust. Đây là hình thức *tin tưởng trực tiếp vào leaf certificate*. Trong production, sử dụng PKI/CA đáng tin, certificate chain và chính sách xác minh/thu hồi hợp lệ; không bê các private key hoặc password của lab vào hệ thống thật.

## 3. Kiến trúc bài thực hành và cấu trúc file

Cần JDK **17+** có `javac`, `java`, `keytool`; dùng terminal Windows PowerShell, Linux shell hoặc macOS Terminal. Kiểm tra rằng ba lệnh đến cùng bản JDK mong muốn:

```bash
java -version
javac -version
keytool -help
```

Tạo một thư mục làm việc riêng, ví dụ:

```bash
mkdir mtls-lab
cd mtls-lab
```

Các file sau sẽ được tạo:

```text
mtls-lab/
  server.p12          # PrivateKeyEntry server + certificate tự ký
  client.p12          # PrivateKeyEntry client + certificate tự ký
  server.crt          # Chứng chỉ server được export, KHÔNG có private key
  client.crt          # Chứng chỉ client được export, KHÔNG có private key
  server-trust.p12    # Bên server tin client.crt
  client-trust.p12    # Bên client tin server.crt
  MutualTlsLab.java   # Chương trình chạy 5 test
```

Mối quan hệ:

```text
server.p12  (server private key + cert)
      |                    client-trust.p12 (server.crt)
      +-----------------------------> client kiểm tra server

client.p12  (client private key + cert)
      |                    server-trust.p12 (client.crt)
      +-----------------------------> server kiểm tra client
```

Tên file và hai chiều trust cần đúng; nếu đảo truststore, đôi khi handshake fail với lỗi trông giống certificate không hợp lệ. Chúng ta không cấu hình `cacerts` toàn JDK để tránh tác động tới ứng dụng khác.

## 4. Tạo danh tính TLS demo bằng `keytool`

**Chỉ dành cho bài lab cục bộ:** password ví dụ là `changeit`, keypair RSA 3072 bit, cert tự ký có thời hạn 7 ngày. Trong production không truyền password ở command line vì có thể lộ trong process list, lịch sử shell và log; dùng secrets/PKI và cơ chế provisioning phù hợp. Không đưa file `*.p12` chứa private key vào Git.

### 4.1. Tạo keystore của server

Từ thư mục `mtls-lab`, chạy toàn bộ lệnh trên một dòng:

```bash
keytool -genkeypair -alias server -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=localhost" -ext "SAN=dns:localhost" -ext "EKU=serverAuth" -validity 7 -keystore server.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt
```

- `CN=localhost` là subject minh họa, nhưng **hostname validation phải dựa vào SAN**; vì thế có `SAN=dns:localhost`.
- `EKU=serverAuth` mô tả mục đích TLS server. Chỉ sử dụng cert này trên server; không dùng làm client cert.
- `PKCS12` được chỉ định minh bạch, tránh phụ thuộc vào `KeyStore.getDefaultType()` của JDK.

### 4.2. Tạo keystore của client

```bash
keytool -genkeypair -alias client -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=client" -ext "EKU=clientAuth" -validity 7 -keystore client.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt
```

Client certificate có `EKU=clientAuth`. Đối với service production, danh tính client nên có tên có quản trị như SAN URI/DNS, subject/issuer hoặc định danh workload theo PKI; không mặc nhiên tin rằng string `CN=client` là một role.

### 4.3. Xuất certificate công khai của hai phía

```bash
keytool -exportcert -rfc -alias server -keystore server.p12 -storepass changeit -file server.crt
keytool -exportcert -rfc -alias client -keystore client.p12 -storepass changeit -file client.crt
```

`-rfc` tạo PEM certificate. **Không** xuất private key vào các file `.crt`. File chứng chỉ có thể được phân phối theo quy trình trust an toàn; người nhận vẫn phải kiểm tra fingerprint/nguồn của certificate trước khi coi là trust anchor.

### 4.4. Mỗi bên import certificate mà nó cần tin

```bash
keytool -importcert -noprompt -alias server -file server.crt -keystore client-trust.p12 -storetype PKCS12 -storepass changeit
keytool -importcert -noprompt -alias client -file client.crt -keystore server-trust.p12 -storetype PKCS12 -storepass changeit
```

Kiểm tra store và certificate:

```bash
keytool -list -v -keystore server.p12 -storepass changeit
keytool -list -v -keystore client.p12 -storepass changeit
keytool -list -v -keystore server-trust.p12 -storepass changeit
keytool -list -v -keystore client-trust.p12 -storepass changeit
keytool -printcert -v -file server.crt
keytool -printcert -v -file client.crt
```

Kỳ vọng:

| File | Loại entry | Điều kiện quan trọng |
| --- | --- | --- |
| `server.p12` | `PrivateKeyEntry` | Alias `server`, certificate `CN=localhost`, SAN DNS `localhost`, EKU `serverAuth`. |
| `client.p12` | `PrivateKeyEntry` | Alias `client`, certificate `CN=client`, EKU `clientAuth`. |
| `server-trust.p12` | `trustedCertEntry` | Alias `client`, chỉ chứa certificate công khai của client. |
| `client-trust.p12` | `trustedCertEntry` | Alias `server`, chỉ chứa certificate công khai của server. |

Với demo trust trực tiếp, không có CA chain trung gian. Trên hệ thống dùng CA cấp certificate thật, truststore chứa CA/trust anchor tương ứng, keystore có private key và đầy đủ certificate chain cần gửi; test phải kiểm tra path building, EKU/KU, name constraints và chính sách revocation theo yêu cầu.

## 5. Java 17+ độc lập: một chương trình chạy cả 5 tình huống

Tạo **chính xác** file `MutualTlsLab.java` trong cùng thư mục với các file `.p12`. Code dựng một TLS server tạm trên `127.0.0.1` với **port do hệ điều hành cấp**; client kết nối qua TCP loopback và dùng layered `SSLSocket` để truyền hostname dự kiến vào bước xác minh. Các test tuần tự nên không cần mở cổng ra mạng ngoài.

```java
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

public final class MutualTlsLab {
    // Chỉ cho fixture cục bộ; production phải dùng secret manager.
    static final char[] PASSWORD = "changeit".toCharArray();

    static KeyStore store(String filename) throws Exception {
        KeyStore result = KeyStore.getInstance("PKCS12");
        if ("empty".equals(filename)) {
            // Negative test: truststore mới không chứa trust anchor nào.
            result.load(null, PASSWORD);
        } else {
            try (InputStream input = new FileInputStream(filename)) {
                result.load(input, PASSWORD);
            }
        }
        return result;
    }

    static SSLContext context(String identity, String trusted)
            throws Exception {
        KeyManager[] keys = null;
        if (identity != null) {
            KeyManagerFactory km = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            km.init(store(identity), PASSWORD);
            keys = km.getKeyManagers();
        }

        TrustManagerFactory tm = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tm.init(store(trusted));

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(keys, tm.getTrustManagers(), null);
        return ctx;
    }

    static void attempt(String label, SSLContext serverContext,
                        SSLContext clientContext, String expectedHost,
                        String[] serverProtocols, String[] clientProtocols,
                        boolean shouldSucceed) throws Exception {
        AtomicReference<Throwable> serverError = new AtomicReference<>();
        AtomicBoolean serverCompleted = new AtomicBoolean();
        Throwable clientError = null;
        boolean clientCompleted = false;

        try (SSLServerSocket listener = (SSLServerSocket) serverContext
                .getServerSocketFactory().createServerSocket(
                        0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(5000);
            listener.setNeedClientAuth(true);
            if (serverProtocols != null) {
                listener.setEnabledProtocols(serverProtocols);
            }

            Thread worker = new Thread(() -> {
                try (SSLSocket connection = (SSLSocket) listener.accept()) {
                    connection.setSoTimeout(5000);
                    connection.startHandshake();

                    // Principal chỉ được tin sau khi peer authentication xong.
                    if (connection.getSession().getPeerPrincipal() == null) {
                        throw new SSLException("Missing verified client principal");
                    }
                    if (connection.getInputStream().read() != 42) {
                        throw new IOException("Missing application request");
                    }
                    connection.getOutputStream().write(43);
                    connection.getOutputStream().flush();
                    serverCompleted.set(true);
                } catch (Throwable failure) {
                    serverError.set(failure);
                }
            }, "mtls-test-server");
            worker.setDaemon(true);
            worker.start();

            try (Socket tcp = new Socket(
                        "127.0.0.1", listener.getLocalPort());
                 SSLSocket client = (SSLSocket) clientContext
                         .getSocketFactory().createSocket(
                                 tcp, expectedHost,
                                 listener.getLocalPort(), true)) {
                client.setSoTimeout(5000);

                // Raw SSLSocket cần yêu cầu hostname verification rõ ràng.
                SSLParameters parameters = client.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                client.setSSLParameters(parameters);

                if (clientProtocols != null) {
                    client.setEnabledProtocols(clientProtocols);
                }
                client.startHandshake();
                client.getOutputStream().write(42);
                client.getOutputStream().flush();

                if (client.getInputStream().read() != 43) {
                    throw new IOException("No authenticated response");
                }
                clientCompleted = true;
            } catch (Throwable failure) {
                clientError = failure;
            }

            worker.join(6500);
            if (worker.isAlive()) {
                throw new IllegalStateException(
                        "Server test timed out: " + label);
            }
        }

        // TLS 1.3 có thể đưa alert về phía client bất đồng bộ:
        // cần kiểm tra cả server lẫn một round trip ứng dụng.
        boolean exchange = clientCompleted && serverCompleted.get();
        boolean sslRejected = clientError instanceof SSLException
                || serverError.get() instanceof SSLException;

        boolean passed = shouldSucceed
                ? exchange : !exchange && sslRejected;
        if (!passed) {
            throw new AssertionError(label + ": unexpected result"
                    + " client=" + (clientError == null ? "ok" : clientError)
                    + " server=" + (serverError.get() == null
                            ? "ok" : serverError.get()));
        }
        System.out.println(label + ": "
                + (shouldSucceed ? "PASS" : "REJECTED AS EXPECTED"));
    }

    public static void main(String[] args) throws Exception {
        SSLContext server = context("server.p12", "server-trust.p12");
        SSLContext client = context("client.p12", "client-trust.p12");
        SSLContext noClientCertificate =
                context(null, "client-trust.p12");
        SSLContext untrustedServer =
                context("client.p12", "empty");

        attempt("valid-mtls", server, client,
                "localhost", null, null, true);
        attempt("missing-client-cert", server, noClientCertificate,
                "localhost", null, null, false);
        attempt("untrusted-server", server, untrustedServer,
                "localhost", null, null, false);
        attempt("hostname-mismatch", server, client,
                "wrong.example.test", null, null, false);
        attempt("protocol-mismatch", server, client, "localhost",
                new String[] {"TLSv1.2"},
                new String[] {"TLSv1.3"}, false);
    }
}
```

### 5.1. Biên dịch và chạy

```bash
javac --release 17 MutualTlsLab.java
java MutualTlsLab
```

Với JDK 17+ có provider JSSE hỗ trợ TLS 1.2 và 1.3, keystore đã tạo đúng, ví dụ đã kiểm thử cho stdout:

```text
valid-mtls: PASS
missing-client-cert: REJECTED AS EXPECTED
untrusted-server: REJECTED AS EXPECTED
hostname-mismatch: REJECTED AS EXPECTED
protocol-mismatch: REJECTED AS EXPECTED
```

Đây là stdout chính xác của chương trình khi **tất cả assertion đạt**. Nếu bất kỳ tình huống nào xử lý sai, chương trình ném `AssertionError`/exception và không được coi là pass. Nội dung chi tiết của các `SSLHandshakeException` và TLS alert có thể khác giữa JDK/provider/TLS version; vì thế không so sánh message tiếng Anh như một golden string.

### 5.2. Từng dòng cấu hình quan trọng có tác dụng gì?

| Dòng/API | Vai trò | Bỏ đi thì hậu quả gì? |
| --- | --- | --- |
| `KeyManagerFactory.init(store(identity), PASSWORD)` | Khai báo private key/cert để chứng minh danh tính của bên mình | Client không có chứng chỉ để trả lời `CertificateRequest`; server không có chứng chỉ để xác thực chính nó. |
| `TrustManagerFactory.init(store(trusted))` | Chỉ chấp nhận trust anchors được cấp phát | Nếu dùng default truststore ngoài ý muốn, bạn có thể tin sai CA hoặc thiếu CA riêng. |
| `SSLContext.init(keys, tm.getTrustManagers(), null)` | Gắn vật liệu khóa/trust vào TLS context | Context không mang chính sách xác thực bạn kỳ vọng. |
| `listener.setNeedClientAuth(true)` | Bắt buộc TLS client authentication phía server | Server có thể nhận kết nối chỉ xác thực server (TLS một chiều). |
| `setEndpointIdentificationAlgorithm("HTTPS")` | Kiểm tra hostname dự kiến với SAN/certificate server trên raw socket | Chain được tin nhưng certificate cấp cho host khác vẫn có thể bị chấp nhận. |
| `client.startHandshake()` và `connection.startHandshake()` | Kích hoạt handshake rõ ràng | Dù handshake cũng có thể khởi phát ngầm khi I/O, khó phân tách lỗi trong test. |
| `getSession().getPeerPrincipal()` | Lấy identity certificate client đã xác thực | Không được dùng tên client do request HTTP tự khai báo để thay peer identity. |
| Đọc byte `42` / trả byte `43` | Chứng minh kênh bảo vệ đã trao đổi dữ liệu hai chiều | Một phía có thể chưa thấy TLS alert thất bại ngay tại `startHandshake()`. |

Giá trị `"empty"` trong `store` **chỉ tạo truststore rỗng cho negative test**, tuyệt đối không phải “trust-all”. Khi không có trust anchor, client phải từ chối xác minh server; việc không truyền file truststore vào client mà dùng `null` cho trust manager lại có ý nghĩa khác (JSSE có thể sử dụng các trust anchors mặc định của JDK).

## 6. Diễn giải từng test và nguyên nhân mong đợi

| Case | Thay đổi có chủ ý | Phía nào phải phát hiện? | Điều kiện đạt |
| --- | --- | --- | --- |
| `valid-mtls` | Server có `server.p12`, tin `client.crt`; client có `client.p12`, tin `server.crt`; hostname `localhost` đúng SAN | Cả hai xác thực thành công | Handshake thành công **và** byte yêu cầu/phản hồi được trao đổi. |
| `missing-client-cert` | `SSLContext` client không có `KeyManager`/private key | Server với `needClientAuth=true` | Có lỗi TLS authentication; không hoàn tất round trip ứng dụng. |
| `untrusted-server` | Truststore client rỗng | Client không xây dựng được đường tin cậy đến certificate server | Có lỗi certificate validation/TLS; không hoàn tất ứng dụng. |
| `hostname-mismatch` | Client kỳ vọng `wrong.example.test` trong khi cert server có `SAN=dns:localhost` | Client dùng `HTTPS` endpoint identification | Chain có thể tin cậy nhưng tên sai nên handshake bị từ chối. |
| `protocol-mismatch` | Server chỉ cho `TLSv1.2`, client chỉ cho `TLSv1.3` | TLS negotiation ở cả hai phía | Không tìm được protocol chung; handshake bị từ chối. |

**Tại sao phải kiểm cả hai phía?** Với TLS 1.3, server có thể phát cảnh báo sau khi client đã nhận đủ message khiến lời gọi `client.startHandshake()` cục bộ vừa trả về. Nếu chỉ đo “client.startHandshake không ném exception”, negative test thiếu client certificate có thể bị hiểu nhầm là pass. Demo buộc trao đổi dữ liệu và kiểm tra trạng thái server, cùng với việc ít nhất một bên phát sinh `SSLException` khi test âm.

Điều kiện `sslRejected` của demo là bước xác nhận *có lỗi từ JSSE*, vẫn cần xem log/fixture khi đưa vào CI thật: một test âm chỉ đạt có ý nghĩa nếu nó từ chối **vì policy được chủ đích kiểm thử**, không phải vì file fixture thiếu, port bị chiếm, timeout hay lỗi nguồn khác. File/keystore không tồn tại khiến chương trình lỗi trước khi chạy test, không phải negative TLS test hợp lệ.

### Ý nghĩa của “handshake thất bại” và “request bị từ chối”

- **Handshake thất bại:** một bên không chứng minh được danh tính, path/hostname/crypto constraints không đạt, TLS version/cipher không tương thích... Không thiết lập được phiên TLS tin cậy.
- **Handshake thành công, authorization thất bại:** certificate client hợp lệ nhưng chưa có quyền đọc resource, chưa được ánh xạ tenant, hoặc bị policy từ chối. Lúc này máy chủ có thể trả HTTP `403 Forbidden` trên kênh TLS đã xác thực (tùy giao thức và mô hình lỗi). Đây **không phải** lỗi TLS handshake.
- **Ứng dụng khác giao thức:** nếu service phía trên TLS là HTTP, sau handshake phải gửi HTTP request và xử lý HTTP response; demo chỉ trao đổi hai byte để kiểm chứng mTLS, không tự nhận là một HTTPS endpoint hoàn chỉnh.

## 7. Mở rộng negative tests: kiểm tra certificate có vấn đề

Bộ 5 test là nền tảng; với CI môi trường thật cần thêm các trường hợp sau. Tạo fixture riêng cho từng lỗi, giữ các yếu tố còn lại hợp lệ để tránh “test fail vì nguyên nhân khác”.

| Ca mở rộng | Cách tạo tiền đề kiểm thử | Lỗi hoặc kết quả mong đợi |
| --- | --- | --- |
| Client certificate không được server tin | Tạo client identity khác và **không** đặt CA/leaf cert ấy vào truststore server | Server từ chối xác thực; có thể do client không gửi cert phù hợp danh sách CA được yêu cầu hoặc do cert không xây dựng được trust path, tùy provider. |
| Server cert hết hạn/chưa có hiệu lực | Cấp fixture với `NotBefore`/`NotAfter` ngoài thời điểm test và trust chain phù hợp | Client từ chối theo certificate validity policy. |
| Client cert hết hạn/chưa có hiệu lực | Cấp cert client hết hạn bằng CA thử nghiệm; đảm bảo không có lỗi phụ làm che nguyên nhân | Server từ chối client certificate. |
| EKU không phù hợp | Cấp cert cho mục đích `serverAuth` nhưng dùng làm client (hoặc ngược lại) với chain valid | Provider/policy phù hợp phải từ chối usage không hợp lệ. |
| SAN hostname/IP không khớp | Dùng server cert chỉ có `DNS:localhost` nhưng client đòi tên khác | Client hostname verification từ chối. |
| CA bị thu hồi/không tin cậy | Tháo trust anchor hoặc dùng PKI có CRL/OCSP test | Với CA không tin: từ chối; với revoked: chỉ kỳ vọng từ chối khi **revocation checking đã cấu hình đúng**. |
| Chứng chỉ sai key size/algorithm | Dùng fixture cố ý vi phạm `jdk.tls.disabledAlgorithms` / `jdk.certpath.disabledAlgorithms` | Handshake hoặc path validation thất bại theo constraints của JDK. |
| Không có cipher suite chung | Giới hạn hai phía vào các cipher suites **được provider hỗ trợ** nhưng không giao nhau | Handshake fail khi không thương lượng được cipher phù hợp. |
| Không có client cert nhưng dùng `wantClientAuth` | Thử cùng client thiếu cert trên server cấu hình `want` | TLS có thể thành công vì xác thực client là tùy chọn; đó là bằng chứng `want` không thay được `need`. |
| Truststore sai file/password | Cho chương trình load store sai loại/khóa | Lỗi khởi tạo (`IOException`, `UnrecoverableKeyException`...) **trước** handshake; phân loại đúng, không gọi đây là chứng chỉ bị TLS từ chối. |

### Ví dụ tự tạo “client lạ” để thử server trust

Trong thư mục lab, có thể tạo một `other-client.p12` với certificate khác:

```bash
keytool -genkeypair -alias other -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -dname "CN=other-client" -ext "EKU=clientAuth" -validity 7 -keystore other-client.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt
```

Trong code test mở rộng, thay `client.p12` bằng `other-client.p12` nhưng giữ `server-trust.p12` vẫn chỉ tin certificate của `client`. Sau đó gọi:

```java
// Đoạn bổ sung vào main(...) của MutualTlsLab, không độc lập:
SSLContext otherClient =
        context("other-client.p12", "client-trust.p12");
attempt("untrusted-client", server, otherClient,
        "localhost", null, null, false);
```

Một số TLS implementations lọc certificate ứng viên dựa trên danh sách CA issuer mà server yêu cầu; “client lạ” có thể **không được gửi** trong handshake, thay vì được gửi rồi bị kiểm tra trust thất bại. Kết luận bảo mật vẫn là phiên bị từ chối, nhưng để chứng minh riêng `PKIX` rejection cần bộ test CA-signed chains được kiểm soát và quan sát TLS handshake detail.

### Không suy đoán trạng thái thu hồi từ test chain/truststore

Một cert có chain hợp lệ và `TrustManagerFactory` khởi tạo từ PKCS#12 **không tự chứng minh** hệ thống đang kiểm tra CRL/OCSP đúng yêu cầu. Revocation checking của PKIX/JSSE phụ thuộc provider, system/security properties, `PKIXBuilderParameters`, `CertPathTrustManagerParameters` và cấu hình vận hành. Nếu mô hình rủi ro yêu cầu kiểm tra revoked certificate, phải có test fixture OCSP/CRL đáng tin và negative test cho certificate bị thu hồi, đồng thời định nghĩa fail-open/fail-closed theo policy.

## 8. Chẩn đoán handshake failure đúng lớp

Khi bài lab hoặc ứng dụng thực xảy ra lỗi, đọc **first useful cause** ở hai phía; tên exception và TLS alert thường chỉ là dấu hiệu ban đầu.

| Dấu hiệu | Nhóm nguyên nhân | Kiểm tra trước |
| --- | --- | --- |
| `SSLHandshakeException: PKIX path building failed` | Trust anchor/chain thiếu hoặc không tin | File truststore thực đã load, alias và fingerprint CA/cert, peer chain, issuer và intermediate. |
| `No subject alternative DNS name matching...` hoặc `No name matching...` | Hostname validation thất bại | Hostname client dùng, SAN của server certificate, SNI/virtual host, DNS/IP vs DNS SAN. |
| `Received fatal alert: certificate_required` hoặc `bad_certificate` | Server yêu cầu client cert nhưng client không gửi/không được chấp nhận | `needClientAuth`, client key managers, cert chain/EKU và danh sách acceptable issuers. |
| `No available authentication scheme` / `handshake_failure` | Server không có phù hợp certificate/key/cipher hoặc protocol | Server KeyManager, private key, provider, TLS versions/cipher suites, cert key usage. |
| `protocol_version`, `no cipher suites in common` | Không giao nhau protocol/cipher | Hai bên cùng JDK/provider/policy, `setEnabledProtocols`, `jdk.tls.disabledAlgorithms`. |
| `UnrecoverableKeyException` / `keystore password was incorrect` | Lỗi nạp private key/keystore | PKCS#12 format, password, alias, key entry và file permission. |
| `SocketTimeoutException`, `Connection refused`, `EOFException` | Có thể lỗi mạng, đóng socket hoặc cảnh báo TLS xảy ra trước đó | Đừng lập tức kết luận về certificate; kiểm tra server log, ports, timeouts và TLS debug. |

**Cẩn thận:** các message trên là ví dụ thường gặp, không phải giao diện ổn định. Không viết test phụ thuộc chuỗi message cụ thể khi JDK/provider thay đổi.

### Debug JSSE có phạm vi

Chỉ dùng debug trong môi trường thử nghiệm được phép, với file log được kiểm soát:

```bash
java -Djavax.net.debug=ssl:handshake MutualTlsLab
```

Để tập trung vào path validation theo nhu cầu:

```bash
java -Djava.security.debug=certpath MutualTlsLab
```

Khi cần xác định **đã thương lượng** protocol/cipher thật trong ứng dụng, sau handshake thành công có thể lấy:

```java
// Đoạn minh họa đặt sau startHandshake(), không phải chương trình độc lập:
SSLSession session = client.getSession();
System.out.println(session.getProtocol());
System.out.println(session.getCipherSuite());
```

Chỉ đọc `SSLSession.getPeerPrincipal()` **sau** một handshake thành công và chứng minh peer được xác thực; không biến subject DN thành role toàn cục chỉ bằng so sánh string. Những log kể cả không chứa private key vẫn có thể làm lộ certificate identities, hostname, endpoint, session metadata; phải giới hạn phạm vi/retention và tránh lưu secret.

## 9. Checklist mTLS cho ứng dụng thực

### 9.1. Quản lý certificate và PKI

- Dùng certificate cho workload với SAN/identity rõ ràng, EKU phù hợp (`serverAuth`/`clientAuth`), thuật toán/cỡ khóa theo security baseline.
- Đảm bảo chain gửi đủ intermediate; truststore chứa trust anchor được phê duyệt. Kiểm tra fingerprint/thông tin CA qua kênh đáng tin; không import file cert từ nguồn không xác minh.
- Bảo vệ private keys bằng quyền file/HSM/KMS/secret manager; dùng password khác `changeit` và không ghi literal trong source.
- Quản lý lifecycle: issuance, auto-renewal, rotation, overlapping trust anchors, revocation khi cần, giám sát `NotAfter`, dọn cert cũ.
- Quy định cách chọn client identity nếu keystore chứa nhiều certificate: alias, issuer, SNI và provider; kiểm tra certificate **thực được gửi**, không chỉ file có tồn tại.
- Định nghĩa fail-closed cho client trust/hostname validation; tránh `TrustManager` trả success vô điều kiện hoặc `HostnameVerifier` luôn `true`.

### 9.2. Gắn mTLS với authorization

- Sau handshake, xác định principal đã được JSSE xác minh (`getPeerPrincipal`, certificate chain), chuẩn hóa theo CA/tenant/identity mapping đáng tin.
- Áp dụng authorization của ứng dụng (ví dụ `service-A` chỉ được gọi `GET /reports`), audit identity và quyết định quyền mà không lưu private key/certificate nhạy cảm ngoài mục đích vận hành.
- Với proxy/load balancer chấm dứt TLS, backend **không thể tự tin** một header như `X-Client-Cert` từ mạng không tin cậy. Phải xác thực proxy, giới hạn kênh nội bộ và bảo vệ cơ chế chuyển tiếp identity trước khi authorize.
- Phân biệt TLS authentication failure với `401`/`403` ứng dụng; chỉ lúc đã có TLS channel thì mới có thể trả HTTP status theo giao thức.

### 9.3. Chính sách TLS và xác thực server

- Giữ hostname verification theo DNS SAN của service (hoặc IP SAN nếu kết nối bằng IP); raw `SSLSocket` cần bật `HTTPS` endpoint identification hoặc chính sách tương đương.
- Chỉ bật phiên bản TLS/cipher suite còn được tổ chức phê duyệt; đừng sửa `jdk.tls.disabledAlgorithms` để “cứu” handshake thất bại.
- Phân biệt cipher/protocol *supported*, *enabled* và *negotiated*. Kiểm tra phiên runtime đích sau các bản cập nhật JDK vì mặc định có thể đổi.
- Không tự tắt mTLS trong staging nhằm tránh các negative test: giữ chính sách gần production và dùng fixture hợp lệ.
- Cấu hình timeout và giới hạn tài nguyên phù hợp; bài demo tạo một luồng ngắn cho mỗi kết nối và không phải mẫu server chịu tải thực.

### 9.4. Tích hợp với HTTP thay vì raw socket

`MutualTlsLab` dùng raw JSSE sockets để đo handshake một cách rõ ràng. Với HTTP API, dùng thư viện HTTP của Java/framework và cấu hình `SSLContext` trên client/server. Ví dụ phần **client** của Java 17 có thể tạo `java.net.http.HttpClient` với `SSLContext` đã khởi tạo:

```java
// Đoạn minh họa, giả sử biến SSLContext clientContext đã có:
java.net.http.HttpClient httpClient = java.net.http.HttpClient
        .newBuilder()
        .sslContext(clientContext)
        .build();
// Gửi request HTTPS đến URI thực; không tắt hostname verification.
```

Framework phía server (Spring Boot, Jakarta EE, servlet container hoặc reverse proxy) cần cấu hình client auth = `NEED` / `required` và truststore riêng theo tài liệu của đúng phiên bản triển khai. Không sao chép nguyên cấu hình của raw `SSLServerSocket` sang một HTTP server mà chưa hiểu cơ chế TLS termination.

## 10. Ma trận kiểm thử CI/CD đề xuất

Mỗi scenario cần mô tả **setup**, **kết quả phải xảy ra**, **bằng chứng** và **điều kiện fail pipeline**.

| ID | Setup | Kỳ vọng | Bằng chứng |
| --- | --- | --- | --- |
| MTLS-01 | Cert server/client hợp lệ, trust hai chiều, hostname đúng | Handshake + exchange thành công | Client nhận response đúng; server ghi nhận verified client principal; negotiated TLS/cipher đạt policy. |
| MTLS-02 | Client không có `PrivateKeyEntry` | Không có phiên mTLS usable | Server log báo client auth failure; không có application response hợp lệ. |
| MTLS-03 | Client không tin trust anchor server | Client dừng | SSL certificate validation failure; server không cấp phiên ứng dụng. |
| MTLS-04 | SAN server khác hostname yêu cầu | Client dừng | Hostname verification failure (dù certificate chain được tin). |
| MTLS-05 | TLS protocols không giao nhau | Handshake thất bại | TLS protocol negotiation failure; không có application response. |
| MTLS-06 | Cert client do CA không được tin | Server từ chối | Đúng logic client authentication failure, phân biệt “không gửi cert”/“invalid chain” bằng log. |
| MTLS-07 | Client cert hợp lệ nhưng identity không có quyền | Handshake vẫn thành công; request bị từ chối | Application trả authorization failure (ví dụ HTTP 403) và ghi audit. |
| MTLS-08 | Cert hết hạn, sai EKU hoặc bị thu hồi | Từ chối theo policy đã cấu hình | Test kiểm chứng certpath/EKU/revocation cụ thể, không chỉ check non-200. |
| MTLS-09 | Đổi JDK build/provider | Không có downgrade bất ngờ | Snapshot defaults, handshake matrix, release notes và các assertion bảo mật. |

**Không cố định toàn bộ exception text**. Test nên assert loại sự kiện bảo mật, identity/certificate fixture và trạng thái ứng dụng; protocol/cipher được negotiated có thể phụ thuộc provider, phiên bản JDK và chính sách. Các test negative có thể yêu cầu xem kết quả trên cả client/server để phân biệt lỗi phát sinh trên phía nào.

## 11. Những lỗi thực hành cần tránh

1. **Nhập `server.crt` vào `server-trust.p12` rồi tưởng server tin client.** Server phải tin **client** (hoặc CA cấp client), client phải tin **server** (hoặc CA cấp server). Xem lại sơ đồ hai hướng.
2. **Đưa certificate public vào keystore nhưng quên private key.** TLS endpoint không thể chứng minh danh tính chỉ với `trustedCertEntry`; cần `PrivateKeyEntry` chứa private key và certificate chain.
3. **Dùng `setWantClientAuth(true)` cho service bắt buộc mTLS.** `want` không bắt buộc; dùng `setNeedClientAuth(true)` và kiểm thử trường hợp client trống credentials.
4. **Chỉ kiểm tra chain tin cậy, không kiểm tra hostname.** Trust một CA không có nghĩa mọi cert của CA đó đều hợp lệ cho `localhost`; bật endpoint identification.
5. **Coi `client.startHandshake()` không ném lỗi là toàn bộ phiên đã hợp lệ.** Phải xác nhận server đã chấp nhận client certificate và request/response thực sự đi được; đặc biệt chú ý alert của TLS 1.3.
6. **Lấy `CN` của client làm role admin tự động.** Phải có policy mapping/authorization ở tầng ứng dụng.
7. **Dùng truststore rỗng nhưng nhầm là cho phép mọi chứng chỉ.** Truststore rỗng từ chối trust; trust-all là cơ chế tùy biến thiếu an toàn, tuyệt đối tránh.
8. **Sửa global JDK `cacerts` hoặc tắt certificate validation để test pass.** Dùng truststore riêng theo ứng dụng và sửa fixture/PKI; test phải chứng minh thất bại khi không tin peer.
9. **Kỳ vọng mọi phiên bản JDK hiển thị cùng SSL alert.** So sánh cause, certificate, policy và application exchange; đừng gắn cứng vào một chuỗi exception.
10. **Đưa `changeit` hoặc PKCS#12 lab lên production.** Lab dùng mật khẩu công khai và certificate tự ký 7 ngày để minh họa. Production cần quản trị secrets/PKI, quyền file, rotation, audit và threat model phù hợp.

## 12. Bài tập mở rộng có đáp án định hướng

**Bài 1 — Đổi SAN server.** Tạo certificate có `SAN=dns:api.example.test`, thay expected hostname thành `localhost`. Kết quả phải là hostname mismatch, dù truststore đã chứa cert đúng. Muốn pass, dùng hostname thực được cấp và định tuyến tới test server phù hợp; không tắt verifier.

**Bài 2 — Thử `wantClientAuth`.** Thay `listener.setNeedClientAuth(true)` bằng `setWantClientAuth(true)` và chạy client thiếu certificate. Quan sát sự khác biệt với negative test ban đầu. Tại sao service có dữ liệu nhạy cảm không được dùng `want` khi policy là bắt buộc client identity? Vì TLS cho phép phiên không có client certificate và kiểm soát truy cập tầng sau có thể bị bỏ sót.

**Bài 3 — Thêm client lạ.** Dùng `other-client.p12` theo mục 7 và giữ server chỉ tin `client.crt`. Chứng minh phiên bị từ chối, rồi phân biệt “client không gửi cert do issuer filtering” với “server nhận cert nhưng không trust” bằng JSSE debug. Để test chain rejection nhất quán, tạo một CA lab và cấp hai certificate client từ hai CA khác nhau.

**Bài 4 — Ánh xạ identity thành quyền ứng dụng.** Sau `connection.getSession().getPeerPrincipal()`, gắn client identity được quản lý vào quyền `read-report`. Hãy tạo trường hợp cert client đúng nhưng không có quyền `delete-report`: TLS vẫn pass, thao tác `delete-report` bị chặn ở authorization layer. Tránh dùng một chuỗi header do client tự gửi thay cho peer principal.

**Bài 5 — Kiểm thử certificate rotation.** Có CA/cert cũ và mới; trong giai đoạn overlap, đảm bảo truststore cập nhật trước khi thay certificate của endpoint. Sau khi rotation hoàn tất và đã qua thời gian an toàn, xóa trust anchor cũ; dùng test âm để chứng minh client với cert cũ không còn được phép.

**Bài 6 — Dùng PKCS#12 do CA cấp thay self-signed.** Tạo CSR, ký với CA lab hoặc CA nội bộ được phép, import CA chain và certificate response đúng alias trong identity keystore. Chạy lại MTLS-01 đến MTLS-05. Xem xét CA key không được phân phối cho ứng dụng; truststore chỉ nên chứa CA certificate công khai.

## 13. Tài liệu chuẩn

- Oracle Java SE 17 — [JSSE Reference Guide](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): `SSLContext`, TLS handshake, key/trust managers, TLS 1.2/1.3 và JSSE debug.
- Oracle Java SE 17 — [SSLSocket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLSocket.html): `startHandshake`, `getSession`, `setSSLParameters`.
- Oracle Java SE 17 — [SSLServerSocket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLServerSocket.html): `setNeedClientAuth`, `setWantClientAuth`, socket factory.
- Oracle Java SE 17 — [SSLParameters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLParameters.html): `setEndpointIdentificationAlgorithm` và protocol constraints.
- Oracle Java SE 17 — [keytool Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html): `-genkeypair`, `-ext SAN`, `-ext EKU`, `-exportcert`, `-importcert`.
- Oracle Java SE 17 — [SSLContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/SSLContext.html) và [KeyManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/KeyManagerFactory.html), [TrustManagerFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/net/ssl/TrustManagerFactory.html).

Khi áp dụng trong môi trường thật, dùng tài liệu của đúng JDK vendor/build, TLS termination component và framework đang chạy. Lab minh họa nguyên lý mTLS/JSSE với certificate tự ký trên loopback; tiêu chí an toàn production phải dựa vào PKI, chính sách authorization và threat model riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-practice-009">23.9 Phân tích một cấu hình hoặc đoạn mã mật mã sai và nêu căn cứ sửa</a>

<details>
<summary>Xem chi tiết</summary>

# 23.9. Phân tích cấu hình hoặc đoạn mã mật mã sai và nêu căn cứ sửa

## 1. Bài toán review: mã hóa dữ liệu nhạy cảm trong ứng dụng Java

Một nhóm triển khai dịch vụ lưu ghi chú giao dịch, nội dung chứng từ hoặc thông tin cá nhân trong cơ sở dữ liệu. Vì muốn “mã hóa dữ liệu trước khi lưu”, họ tạo một hàm dùng AES với khóa cố định, IV toàn số không, rồi gắn thêm SHA-256 của ciphertext để “kiểm tra toàn vẹn”. Chương trình chạy được, giải mã được và đã qua một bài kiểm thử round-trip. Liệu có đủ an toàn?

Bài thực hành này yêu cầu **đọc mã và xác định chính xác cơ chế sai, tác động bảo mật, căn cứ kỹ thuật và cách sửa có thể kiểm chứng**. Trọng tâm không chỉ là đổi một chuỗi thuật toán. Cần biết dữ liệu nào được bảo vệ, ai có thể truy cập hoặc sửa dữ liệu, hệ thống đang tin tưởng vào cái gì, và bằng chứng nào cho phép kết luận bản sửa an toàn hơn.

Giả định cho case study: ứng dụng cần **giữ bí mật** dữ liệu khi lưu trữ, đồng thời phải **phát hiện sửa đổi trái phép** của ciphertext và không được giải mã một bản ghi của tenant này trong ngữ cảnh tenant khác. Kẻ tấn công có thể xem/sửa trường mã hóa trong database hoặc sao chép bản ghi, nhưng **chưa nắm khóa bí mật được bảo quản đúng cách**. Nếu kẻ tấn công đã chiếm toàn bộ quyền thực thi ứng dụng và khóa, AES-GCM không thể tự cứu được hệ thống; khi đó cần thêm kiểm soát vận hành, quyền truy cập và phát hiện xâm nhập.

Một review có chất lượng phải trả lời được:

1. **Đang bảo vệ tài sản nào?** Ví dụ nội dung chứng từ, thông tin khách hàng; có cần bí mật, toàn vẹn, chống tráo đổi giữa bản ghi và xác thực nguồn không?
2. **Đối thủ điều khiển được gì?** Ciphertext, metadata, file cấu hình, database, mã nguồn, mạng, khóa hay chỉ một phần?
3. **Mã nguồn hiện tại làm gì thật?** Không suy ra an toàn từ tên hàm `encrypt()` hoặc từ việc ciphertext có dạng Base64.
4. **Lỗi do đâu?** Trích đúng API, mode, IV/nonce, khóa, thuật toán hash, bước xác minh và đường đi dữ liệu.
5. **Sửa bằng cơ chế nào và vì sao?** Chọn thuật toán và tham số thích hợp, quản lý khóa, xác thực nội dung và dữ liệu liên kết (AAD), định dạng có version.
6. **Kiểm chứng thế nào?** Round-trip, trường hợp sai ciphertext/khóa/ngữ cảnh/phiên bản, fail-closed, tương thích dữ liệu cũ, giới hạn tài nguyên và kiểm thử triển khai.

## 2. Đoạn mã cần review: chạy được nhưng thiếu nhiều bảo đảm

Đây là một **snippet sai có chủ ý** nằm trong một class đã import các lớp được dùng; **không** phải chương trình độc lập để sao chép vào production. Hàm trả một chuỗi ciphertext Base64 và chuỗi SHA-256 dạng hex được nối bằng dấu hai chấm:

~~~java
// CỐ Ý KHÔNG AN TOÀN: chỉ dùng cho phân tích lỗi.
static String encryptInvoiceBad(String plainText) throws Exception {
    byte[] keyBytes = "0123456789abcdef"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] fixedIv = new byte[16];

    javax.crypto.Cipher cipher =
            javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
            new javax.crypto.spec.SecretKeySpec(keyBytes, "AES"),
            new javax.crypto.spec.IvParameterSpec(fixedIv));

    byte[] encrypted = cipher.doFinal(
            plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    byte[] digest = java.security.MessageDigest
            .getInstance("SHA-256").digest(encrypted);

    return java.util.Base64.getEncoder().encodeToString(encrypted)
            + ":" + java.util.HexFormat.of().formatHex(digest);
}
~~~

Một người mới có thể nhận xét “AES là thuật toán mạnh, SHA-256 cũng mạnh, vậy có đủ hai lớp”. Kết luận đó sai vì **tính an toàn của cơ chế phụ thuộc vào mode, key, IV, tính xác thực của digest, cách lưu dữ liệu và cách người nhận xác minh**. Bản thân việc ghép hai thuật toán tốt không mặc nhiên tạo ra giao thức an toàn.

### 2.1. Lỗi 1 — Khóa AES đặt cứng trong source code

~~~java
byte[] keyBytes = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
~~~

Đây là 16 byte, đủ **độ dài** của khóa AES-128, nhưng không đủ **bí mật**: ai đọc source, binary, log, image build hoặc bản copy cấu hình có thể biết khóa. Chuỗi dễ đoán hoặc được tái sử dụng trên mọi môi trường còn khiến nhiều dữ liệu cùng chịu rủi ro. Khóa lộ cho phép giải mã bất kỳ bản ghi nào dùng khóa đó; chỉ thay IV không bù được việc mất khóa.

**Căn cứ sửa:** tạo khóa có entropy thích hợp bằng CSPRNG hoặc KMS/HSM, giữ khóa ngoài mã nguồn và kho Git, cấp quyền tối thiểu, có quy trình cung cấp/luân chuyển/thu hồi. Java có `KeyGenerator.getInstance("AES")` để tạo khóa phục vụ demo. Ở production, dịch vụ cần nhận khóa từ kho quản lý khóa được bảo vệ hoặc thao tác mã hóa trong dịch vụ khóa. Không chỉ đổi từ literal trong Java sang một literal trong file `application.yml` rồi gọi đó là an toàn.

### 2.2. Lỗi 2 — IV của CBC cố định toàn số không

~~~java
byte[] fixedIv = new byte[16];
cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(fixedIv));
~~~

CBC yêu cầu IV có thuộc tính ngẫu nhiên/không đoán trước phù hợp cho lần mã hóa. Khi **cùng khóa + cùng IV** được dùng nhiều lần, hai thông điệp có cùng block plaintext đầu tiên cho ciphertext block đầu tiên giống nhau, và toàn bộ hai plaintext giống nhau sẽ tạo ciphertext giống nhau. Điều này rò rỉ thông tin về tính bằng nhau và cấu trúc của dữ liệu. IV toàn số không không phải “giá trị mặc định tốt”; nó là **tái sử dụng IV một cách chắc chắn**.

**Căn cứ sửa:** với dữ liệu mới, ưu tiên authenticated encryption như `AES/GCM/NoPadding`. GCM cần **nonce/IV duy nhất cho mọi lần mã hóa bằng cùng khóa**; thường dùng IV 96 bit (12 byte) với chiến lược đảm bảo uniqueness. Demo tạo 12 byte mới từ `SecureRandom` mỗi lần. Với hệ thống lưu lượng lớn, nhiều instance hoặc key dùng lâu, phải áp dụng chính sách nonce duy nhất, giới hạn số lần mã hóa theo key và rotation trước khi xác suất va chạm hoặc giới hạn mode trở nên đáng kể. Không dùng fixed nonce, không reset counter khi khởi động, không tự ghép timestamp làm nonce nếu có thể trùng.

**Lưu ý:** IV/nonce **không bắt buộc bí mật**, có thể lưu cùng ciphertext. Điểm quyết định là **đúng yêu cầu của mode**; CBC IV và GCM nonce không phải cùng một hợp đồng bảo mật.

### 2.3. Lỗi 3 — CBC + SHA-256 của ciphertext không tạo xác thực

~~~java
byte[] encrypted = cipher.doFinal(plaintext);
byte[] digest = MessageDigest.getInstance("SHA-256").digest(encrypted);
return base64(encrypted) + ":" + hex(digest);
~~~

`SHA-256(ciphertext)` là digest **không có khóa**. Nếu bên nhận tin chuỗi digest được gửi **ngay cạnh ciphertext** và kẻ tấn công sửa được cả hai, họ có thể sửa ciphertext rồi **tính lại SHA-256**. Việc kiểm tra hai trường mới trùng nhau không xác thực được người tạo ciphertext. CBC tự nó không cung cấp kiểm tra tính toàn vẹn/xác thực; nếu lỗi giải mã/padding bị lộ khác biệt, thiết kế có thể dẫn tới rủi ro padding oracle, ngoài khả năng chỉnh sửa ciphertext.

Đây không có nghĩa “SHA-256 vô dụng”: khi một expected digest được cung cấp độc lập qua nguồn đã xác thực, SHA-256 có thể kiểm tra sự trùng byte với nguồn đó. Nhưng **digest tự gắn cạnh dữ liệu mà đối thủ cũng sửa được** không phải MAC. Đừng thay SHA-256 bằng SHA-512 trong mẫu này rồi kết luận đã sửa lỗi: vẫn thiếu khóa xác thực.

**Căn cứ sửa:** ưu tiên mode AEAD (ví dụ AES-GCM) để giải mã chỉ thành công khi tag hợp lệ; nếu buộc tương thích với CBC legacy, phải thiết kế một cơ chế Encrypt-then-MAC được chuyên gia rà soát với **khóa MAC độc lập**, xác minh MAC đầy đủ trước khi decrypt và không rò rỉ lỗi padding. Bài này chọn AES-GCM cho dữ liệu **mã hóa mới**; không tự xây protocol CBC-HMAC khi không cần thiết.

### 2.4. Lỗi 4 — Không ràng buộc ciphertext với bản ghi/tenant và không version hóa

Hàm không lưu IV (vì cứ dùng lại giá trị cố định), không mô tả format ciphertext và không gắn nội dung mã hóa với định danh bản ghi. Người có quyền sửa database có thể **đổi chỗ ciphertext** của hai bản ghi cùng khóa: ciphertext vẫn có thể giải mã hợp lệ nhưng dữ liệu thuộc bản ghi/tenant sai. Ngay cả khi mode mã hóa có tag, nếu ứng dụng không gắn tag với **ngữ cảnh nghiệp vụ đúng**, một ciphertext nguyên vẹn vẫn có thể bị phát lại hoặc sao chép vào nơi khác.

**Căn cứ sửa:** chuẩn hóa **Additional Authenticated Data (AAD)**, chẳng hạn version giao thức, tenant ID và record ID đáng tin từ server. AAD không bị mã hóa nhưng được đưa vào tính authentication tag; thay một byte AAD sẽ khiến xác minh tag thất bại. Dùng định dạng lưu trữ versioned, trong đó mỗi bản ghi biết version/nonce/ciphertext+tag và có thể tra đúng key ID khi rotation. AAD **không thay thế** kiểm tra authorization: dữ liệu ngữ cảnh phải được lấy từ nguồn đáng tin, không chấp nhận một tenant ID tùy ý do người dùng cung cấp rồi coi đó là quyền truy cập.

### 2.5. Lỗi 5 — Round-trip test không kiểm tra giả định an toàn

~~~text
plaintext -> encrypt -> decrypt -> plaintext
~~~

Tính đúng đắn round-trip chỉ chứng minh phía mã hóa và giải mã **đồng ý với nhau về thuật toán và tham số**. Một hàm mã hóa với fixed key + zero IV vẫn có thể vượt qua kiểm thử đó. Cần thêm các ca thay ciphertext, sửa nonce, đổi AAD, dùng khóa khác, lỗi tag, dữ liệu ngắn, version không hỗ trợ, kiểm tra uniqueness nonce ở mức hệ thống và thử các trường hợp deploy/rotate.

## 3. Bảng kết luận review có căn cứ

| Dấu hiệu trong mã | Tác động đúng phạm vi | Sửa cần thiết | Bằng chứng xác nhận |
| --- | --- | --- | --- |
| Literal `"0123456789abcdef"` làm AES key | Khóa dễ lộ, khó thu hồi, dùng lại giữa môi trường | Tạo/quản lý khóa bởi CSPRNG/KMS/KeyStore; key ID và rotation | Audit không có secret hardcoded; deployment đọc key từ kho quyền hạn |
| `AES/CBC/PKCS5Padding` không MAC | Không bảo vệ ciphertext khỏi chỉnh sửa có chủ ý | AEAD `AES/GCM/NoPadding` hoặc Encrypt-then-MAC đúng chuẩn khi bắt buộc legacy | Sửa 1 byte ciphertext → decrypt thất bại, không trả plaintext |
| `new byte[16]` dùng làm IV cho mọi lần | Các lần mã hóa cùng key + IV có tính xác định, rò rỉ mẫu | Nonce mới, đúng kích cỡ và unique theo key | Theo dõi/review chính sách phát nonce; không dùng constant |
| `SHA-256(ciphertext)` đính kèm ciphertext | Kẻ sửa cả hai có thể tính lại checksum | Tag AEAD hoặc MAC có khóa; bảo vệ format/version/AAD | Sửa ciphertext hoặc AAD → không pass authentication |
| Không lưu version/key ID/ngữ cảnh | Khó migrate và dễ tráo ciphertext giữa bản ghi | Header version, nonce, key selection, AAD từ ngữ cảnh tin cậy | Đổi tenant/record context → xác minh thất bại |
| Chỉ test encrypt/decrypt | Che khuất tamper/nonce/rotation failure | Test âm tính, test liên thông, review vận hành | Test vector và negative cases được tự động kiểm chứng |

**Mức độ ưu tiên:** hardcoded key và CBC không xác thực là các vấn đề cần sửa trước khi tiếp tục lưu dữ liệu nhạy cảm mới. Đối với dữ liệu cũ, phải đánh giá rủi ro lộ khóa và dữ liệu đã có, kế hoạch thu hồi/rotation và khả năng xác thực nguồn trước khi migration.

## 4. Thiết kế bản sửa: một format rõ ràng và có xác thực

Ta chọn AES-GCM theo một giao thức minh họa dành cho **mẩu dữ liệu nhỏ trong bộ nhớ**:

~~~text
Mỗi lần encrypt:
    key AES giữ bí mật
    nonce = SecureRandom 12 byte mới
    algorithm = AES/GCM/NoPadding
    tag = 128 bit
    AAD = VERSION (1 byte) || UTF8(context do server chọn)
    ciphertextWithTag = Cipher.doFinal(plaintext)

Packet trả về:
    +---------+---------------+-------------------------------+
    | version | nonce         | ciphertext || tag             |
    | 1 byte  | 12 byte       | ciphertext bytes + tag 16 byte|
    +---------+---------------+-------------------------------+

Khi decrypt:
    kiểm tra độ dài tối thiểu và version hỗ trợ
    lấy nonce và ciphertextWithTag từ packet
    dùng cùng key + AAD phiên bản + AAD nghiệp vụ
    Cipher.doFinal(ciphertextWithTag)
      -> thành công: có plaintext đã xác thực
      -> tag sai: từ chối toàn bộ, không sử dụng plaintext
~~~

Ở đây Java JCE cung cấp tag GCM nằm **cuối** byte array do `Cipher.doFinal()` trả về trên đường mã hóa. Định dạng lưu bản này là quy ước của ví dụ, không phải format do JDK tự tạo. Packet chứa version và nonce dạng plaintext; ciphertext+tag chứa dữ liệu được mã hóa cùng tag bảo vệ. Khi cần lưu text/JSON, có thể mã hóa toàn bộ packet bằng Base64; Base64 là **encoding**, không tạo mật mã hay tính xác thực bổ sung.

Một AAD hợp lệ có thể là chuỗi UTF-8 xác định: `tenant=7;record=42`. Bên giải mã phải dựng lại đúng byte AAD từ tenant/record được xác nhận trên server. Nếu context đầu vào khác, tag không hợp lệ. Với hệ thống thật nên định nghĩa serialization không nhập nhằng (ví dụ length-prefix hoặc canonical encoding cho từng trường), phạm vi tenant/record, encoding, thuật toán, version và key ID; đừng tạo một AAD bằng phép nối không rõ ranh giới.

**Tại sao có version?** Khi thay thuật toán/định dạng hoặc xoay khóa, hệ thống biết cách giải mã artifact cũ theo policy cho phép. Bản demo chỉ hỗ trợ version 1 và **reject** mọi version khác. Version được đưa vào AAD để sau bước giải mã thành công nó cũng được tag xác thực. Khi cần hỗ trợ nhiều version/khóa, hãy dùng danh sách cho phép, lưu key ID xác định và bảo vệ metadata liên quan bằng AAD. Không để một trường version do client điều khiển tự động bật lại thuật toán yếu.

**Nonce và key:** Demo tạo khóa AES-128 để Java SE 17 hỗ trợ rộng rãi; AES-256 cũng phù hợp khi cấu hình/provider hỗ trợ. Khóa demo được sinh mới mỗi lần chạy nên packet từ tiến trình này **không thể giải mã sau khi restart bằng khóa mới**. Production phải cấp lại đúng khóa đã được quản lý an toàn; không tái tạo key mỗi request, không hardcode key, không lưu khóa chung với ciphertext trong cùng nơi không bảo vệ.

**Tại sao không thêm `SHA-256(ciphertext)`?** GCM đã kiểm tra tag xác thực đối với ciphertext và AAD với khóa bí mật. Một digest công khai tự gắn cạnh ciphertext không thêm đảm bảo xác thực. Nếu hệ thống vẫn cần SHA-256 vì mục đích định danh artifact/caching/kiểm chứng từ nguồn tin cậy, đó là bài toán khác, cần làm rõ nguồn của expected digest.

## 5. Java 17+ hoàn chỉnh — sửa mã và kiểm chứng

Lưu toàn bộ mã dưới tên **`CryptoReviewDemo.java`**. Chương trình chỉ dùng Java SE, không cần thư viện ngoài, không sử dụng key cố định, không dùng IV/nonce cố định, không ghi bí mật vào file và không cần mạng.

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

public class CryptoReviewDemo {
    private static final byte VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    static SecretKey generateKey() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        return generator.generateKey();
    }

    static byte[] encrypt(SecretKey key, byte[] plaintext, byte[] context)
            throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(new byte[] { VERSION });
        cipher.updateAAD(context);
        byte[] ciphertextWithTag = cipher.doFinal(plaintext);

        return ByteBuffer.allocate(1 + NONCE_BYTES + ciphertextWithTag.length)
                .put(VERSION).put(nonce).put(ciphertextWithTag).array();
    }

    static byte[] decrypt(SecretKey key, byte[] packet, byte[] context)
            throws GeneralSecurityException {
        if (packet == null || packet.length < 1 + NONCE_BYTES + TAG_BYTES) {
            throw new GeneralSecurityException("Invalid packet length");
        }

        ByteBuffer buffer = ByteBuffer.wrap(packet);
        byte version = buffer.get();
        if (version != VERSION) {
            throw new GeneralSecurityException("Unsupported packet version");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        buffer.get(nonce);
        byte[] ciphertextWithTag = new byte[buffer.remaining()];
        buffer.get(ciphertextWithTag);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(new byte[] { version });
        cipher.updateAAD(context);
        return cipher.doFinal(ciphertextWithTag);
    }

    public static void main(String[] args) throws Exception {
        SecretKey key = generateKey();
        byte[] context = "tenant=7;record=42".getBytes(StandardCharsets.UTF_8);
        byte[] message = "amount=100".getBytes(StandardCharsets.UTF_8);
        byte[] packet = encrypt(key, message, context);

        System.out.println("round trip: " +
                Arrays.equals(message, decrypt(key, packet, context)));
        System.out.println("packet version: " + packet[0]);

        byte[] tampered = packet.clone();
        tampered[tampered.length - 1] ^= 1;
        try {
            decrypt(key, tampered, context);
            System.out.println("tampered packet accepted");
        } catch (AEADBadTagException ex) {
            System.out.println("tampered packet: rejected");
        }

        byte[] wrongContext =
                "tenant=8;record=42".getBytes(StandardCharsets.UTF_8);
        try {
            decrypt(key, packet, wrongContext);
            System.out.println("wrong context accepted");
        } catch (AEADBadTagException ex) {
            System.out.println("wrong context: rejected");
        }

        try {
            decrypt(generateKey(), packet, context);
            System.out.println("wrong key accepted");
        } catch (AEADBadTagException ex) {
            System.out.println("wrong key: rejected");
        }

        byte[] invalidVersion = packet.clone();
        invalidVersion[0] = 2;
        try {
            decrypt(key, invalidVersion, context);
            System.out.println("invalid version accepted");
        } catch (GeneralSecurityException ex) {
            System.out.println("invalid version: rejected");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CryptoReviewDemo.java
java CryptoReviewDemo
~~~

Stdout dự kiến trên một JDK hỗ trợ AES/GCM như Java SE 17 (đã chạy thử với JDK trên môi trường kiểm tra):

~~~text
round trip: true
packet version: 1
tampered packet: rejected
wrong context: rejected
wrong key: rejected
invalid version: rejected
~~~

### 5.1. Theo dõi đường đi từ encrypt sang decrypt

**Key creation.** `KeyGenerator.getInstance("AES")` và `generator.init(128)` tạo một AES key phù hợp cho demo. Java không cần tự chuyển password thành `SecretKeySpec`. Mã không in hoặc serialize khóa; tính bảo vệ lâu dài và lưu khóa thuộc kiến trúc KMS/KeyStore chứ không nằm trong demo.

**Nonce.** `RANDOM.nextBytes(nonce)` tạo nonce mới 12 byte cho **mỗi lời gọi encrypt()**. GCM yêu cầu **không lặp cặp key+nonce**. Việc dùng random 96 bit có xác suất va chạm rất nhỏ trong mức sử dụng được kiểm soát, nhưng **không phải bằng chứng tuyệt đối duy nhất**: thiết kế production phải giới hạn lưu lượng theo key, theo dõi vòng đời key và dùng chiến lược nonce đảm bảo uniqueness ở quy mô cần thiết. Không lấy từ `java.util.Random`, timestamp ngắn, hash plaintext hoặc biến toàn cục reset khi restart.

**Mode và tag.** `Cipher.getInstance("AES/GCM/NoPadding")` chỉ rõ cả algorithm/mode/padding; `GCMParameterSpec(128, nonce)` đặt tag 128 bit. GCM không cần PKCS#5 padding. `cipher.doFinal(plaintext)` tạo ciphertext cùng tag; bên nhận chỉ nhận plaintext sau khi `doFinal(ciphertextWithTag)` xác minh xong. Khi tag sai, provider phát `AEADBadTagException`; **không được bắt exception rồi trả plaintext rỗng và coi là dữ liệu hợp lệ**.

**AAD.** `updateAAD(new byte[]{VERSION})` và `updateAAD(context)` được gọi **sau `init` nhưng trước `doFinal`** ở cả hai chiều. Không cần giữ AAD trong packet nếu ứng dụng có thể khôi phục **đúng bytes** AAD từ ngữ cảnh đáng tin. Thay tenant từ 7 sang 8 khiến tag không hợp lệ dù ciphertext không đổi. Nếu context do client tự khai, application authorization vẫn phải kiểm tra.

**Packet.** `ByteBuffer.allocate(1 + NONCE_BYTES + ciphertextWithTag.length)` ghi version, nonce, ciphertext+tag theo đúng thứ tự. Bên nhận kiểm tra độ dài tối thiểu `1 + 12 + 16` trước khi đọc và từ chối version không hỗ trợ. Kích thước plaintext tối thiểu có thể bằng 0, nên ciphertext+tag tối thiểu 16 byte với tag 128 bit. Giao thức nhiều version phải có bộ parser riêng, không tự đoán độ dài nonce từ dữ liệu giả.

**Sai dữ liệu.** `tampered[tampered.length - 1] ^= 1` đảo một bit cuối của tag; giải mã thất bại. Đổi AAD hoặc khóa cũng thất bại. Bản test version sửa byte header thành 2, bị từ chối ngay do danh sách version hỗ trợ chỉ có 1. Test kiểm tra bất biến bảo mật, chứ không suy rằng round-trip đủ.

### 5.2. Cách chương trình khác với mã sai ban đầu

| Chủ đề | Mã sai | Mã mới |
| --- | --- | --- |
| Key | Chuỗi literal trong source | `KeyGenerator` tạo key thử nghiệm; production cần key từ hệ thống quản lý khóa |
| Encryption mode | CBC không có xác thực | AES-GCM (AEAD) có tag |
| IV/nonce | 16 byte zero dùng lại | 12 byte mới từ `SecureRandom` trên mỗi encrypt |
| Integrity | SHA-256 không khóa gắn cạnh ciphertext | Tag AEAD dưới khóa bí mật |
| Ngữ cảnh | Không bind tenant/record | `updateAAD` ràng buộc context |
| Storage format | Base64 ciphertext + unkeyed digest, không version | Version + nonce + ciphertext + tag |
| Lỗi verify | Không thấy kiểm soát tamper | Tag sai → exception, không trả plaintext |
| Tests | Round-trip | Round-trip và các ca tamper/context/key/version |

### 5.3. Phạm vi của ví dụ và điều kiện để triển khai thật

Đoạn mã trên minh họa **nguyên lý đúng của AEAD** với payload nhỏ. Nó **chưa phải** thư viện mã hóa production hoàn chỉnh: chưa có persistence khóa, key ID/rotation, tính toán giới hạn sử dụng key/nonce trên nhiều node, admission/size limits, thống nhất version parser qua nhiều dịch vụ, kiểm tra quyền truy cập nghiệp vụ, xử lý replay, audit và quy trình khôi phục.

Không mở rộng ví dụ sang file rất lớn bằng cách giữ toàn bộ dữ liệu trong một `byte[]`: cả `Cipher.doFinal` và `ByteBuffer.allocate` ở đây đều cấp phát theo payload. Với file lớn, cần format mã hóa từng chunk có **nonce duy nhất và tag riêng cho mỗi chunk**, ràng buộc thứ tự/chunk index/final marker để phát hiện thiếu, chèn, hoán đổi hoặc truncate; hoặc dùng thư viện format streaming AEAD đã được kiểm toán. **Không phát plaintext chưa xác thực ra ngoài** chỉ vì pipeline decrypt dạng streaming đã tạo vài block trước khi xác minh tag cuối.

## 6. Kiểm thử bảo mật: bằng chứng khác với “chạy được”

Một checklist review cần có kiểm thử **phủ định**, không chỉ đường thành công:

| Ca kiểm tra | Cách thử | Kết quả cần đạt |
| --- | --- | --- |
| Round-trip | Mã hóa rồi giải mã cùng key, đúng AAD | Trả bytes gốc chính xác |
| Payload trống | `encrypt(key, new byte[0], aad)` rồi decrypt | Trả mảng rỗng hợp lệ, không lỗi tag |
| Thay một bit ciphertext/tag | Clone packet, đổi một byte trong vùng ciphertext+tag | Decrypt từ chối; không xuất plaintext |
| Thay nonce trong packet | Clone packet, đổi một byte nonce | Authentication thất bại |
| Thay AAD | Cùng packet + key nhưng tenant/record AAD khác | Authentication thất bại |
| Thay key | Key AES khác cùng chiều dài | Authentication thất bại |
| Packet thiếu version/nonce/tag | Cắt ngắn một hoặc nhiều byte | Từ chối do format hoặc authentication, không crash |
| Version không biết | Đổi byte đầu sang version không hỗ trợ | Từ chối rõ ràng, không fallback thuật toán yếu |
| Hai lần encrypt cùng plaintext/key | Quan sát nonce/ciphertext của hai packet | Nonce phải khác; chính sách uniqueness phải bảo đảm ở mức hệ thống |
| Sai quyền truy cập tenant | Người dùng truy xuất record của tenant khác | Authorization từ chối kể cả khi có thể lấy packet |
| Key rotation | Đổi active key rồi giải mã artifact cũ bằng key ID hợp lệ | Đọc được chỉ theo policy; khóa lộ bị revoke đúng hạn |
| Tải lớn, payload giả, parser fuzz | Input vượt limit/kiểu dữ liệu hỏng | Giới hạn kích thước/thời gian và lỗi có kiểm soát |
| Khởi động lại, nhiều instance | Cùng key được dùng sau restart/giữa node | Không tái sử dụng nonce; kho khóa/ID nhất quán |

**Lưu ý khi test nonce:** sinh hai nonce ngẫu nhiên không thể đảm bảo khác nhau tuyệt đối chỉ bằng một assertion cho hai lần thử. Test hai lần encrypt có thể phát hiện **lỗi IV cố định rõ ràng**, nhưng không chứng minh xác suất va chạm an toàn ở quy mô tỷ hoặc hàng nghìn tỷ lần mã hóa. Thiết kế uniqueness phải dựa trên policy/giới hạn vận hành; nếu dùng counter, cần quản lý không trùng qua restart và các instance chia sẻ key.

**Không kiểm thử an toàn bằng cách tắt tag check** hoặc catch mọi `GeneralSecurityException` rồi tiếp tục. Một API giải mã an toàn cần có đường thất bại thống nhất, không log plaintext/key hay ciphertext nhạy cảm, và không phân biệt quá chi tiết lỗi có thể làm oracle trong môi trường attacker-controlled.

## 7. Khi cần sửa cấu hình crypto thay vì mã nguồn

Trong một ứng dụng Spring/Java hoặc tệp cấu hình tương tự, review phải kiểm tra **giá trị đang thực sự được runtime sử dụng**, không chỉ file mẫu. Ví dụ cấu hình sau là **anti-pattern minh họa**, không phải cấu hình Spring được khẳng định đã tồn tại:

~~~yaml
crypto:
  transformation: AES/ECB/PKCS5Padding
  key: 0123456789abcdef
  iv: 000000000000000000000000
  integrity: SHA-256
  accept-invalid-tag: true
~~~

Các vấn đề cần phát hiện:

- `AES/ECB/PKCS5Padding`: ECB với plaintext block trùng nhau sẽ làm ciphertext block trùng nhau, lộ pattern; không dùng cho dữ liệu nhạy cảm có cấu trúc. Nếu mã chỉ gọi `Cipher.getInstance("AES")`, mode/padding mặc định là **phụ thuộc provider**; trên provider thường dùng có thể dẫn đến ECB, nên cần chỉ rõ transformation, không giả định cụ thể ở mọi JDK/provider.
- `key` chứa literal có thể nằm trong image, log config, Git hoặc quyền đọc rộng; secret manager/KMS và rotation là cách sửa, không phải chỉ đổi tên khóa cấu hình.
- `iv` cố định không đáp ứng yêu cầu nonce GCM hay IV CBC. **Không** chuyển giá trị này sang GCM rồi nghĩ đã sửa.
- `integrity: SHA-256` chỉ mô tả hash không khóa; phải xác định nó đang so với nguồn nào. Nếu nó là self-check bên cạnh dữ liệu, không thay tag/MAC.
- `accept-invalid-tag: true` nếu được code thực thi thành bỏ qua lỗi xác thực là vi phạm fail-closed; bản sửa phải từ chối, không tự giải mã/tiếp tục.
- Thiếu ràng buộc ngữ cảnh, version, key ID, chính sách thuật toán, giới hạn payload và phân quyền lấy key.

Một cấu hình gợi ý về **policy**, không phải khóa production:

~~~yaml
crypto:
  policy:
    transformation: AES/GCM/NoPadding
    nonce-bytes: 12
    tag-bits: 128
    allowed-envelope-versions: [1]
    aad-context: tenant-and-record
    key-source: managed-key-service
    reject-authentication-failure: true
~~~

Các trường YAML này chỉ là **ý tưởng thiết kế để review**; Java/Spring không tự động hiểu hay áp dụng chúng. Production cần code nạp policy, validation giá trị, secrets access, key provider thực và kiểm thử cấu hình đang active. Không đưa raw AES key hoặc credential vào file ví dụ.

## 8. Dùng đúng cơ chế mật mã cho đúng mục tiêu

Khi review code, một lựa chọn “mã hóa” sai đôi khi bắt nguồn từ việc hiểu sai mục tiêu:

| Yêu cầu nghiệp vụ | Cơ chế phù hợp | Nhầm lẫn nên tránh |
| --- | --- | --- |
| Giữ bí mật + phát hiện sửa đổi dữ liệu | AEAD, ví dụ AES-GCM với key/nonce/AAD đúng | AES-ECB, CBC không MAC, checksum không khóa tự gửi kèm |
| Lưu password đăng nhập để xác minh sau | Password hashing **chậm**, salt độc lập (ví dụ Argon2id, scrypt, bcrypt hoặc PBKDF2 theo policy) | Encrypt password có thể giải ngược, hoặc `SHA-256(password)` nhanh |
| Dẫn xuất AES key từ passphrase | Password-based KDF có salt và work factor được cấu hình, ví dụ PBKDF2-HMAC-SHA256 khi phù hợp | `new SecretKeySpec(password.getBytes(), "AES")` hoặc cắt/đệm password cho đủ 16 byte |
| Chứng minh một artifact do bên ký tin cậy phát hành | Chữ ký số (và kiểm tra identity/public key/certificate trust) | SHA-256 của artifact tự công bố cùng nơi mà đối thủ có thể thay |
| Kiểm tra toàn vẹn giữa hai bên dùng bí mật chung | HMAC hoặc AEAD phù hợp với toàn bộ protocol | `MessageDigest.digest(message)` và gọi kết quả là MAC |
| Mã hóa thông tin qua HTTP | TLS với hostname/chain validation đáng tin, policy phiên bản an toàn | Trust-all `X509TrustManager`, hostname verifier luôn true, tự “mã hóa payload” nhưng để TLS sai |

Password hashing và mã hóa dữ liệu có thể cùng hiện diện trong một hệ thống nhưng **không** nên dùng chung khóa/thuật toán một cách tùy tiện. Password hash của tài khoản phải kèm salt ngẫu nhiên theo tài khoản, work factor được benchmark và migration policy; Argon2id là lựa chọn được khuyến nghị cho phần lớn ứng dụng mới khi thư viện/bối cảnh hỗ trợ. `SHA-256(password)` hoặc `MD5(password)` không phù hợp lưu password vì tốc độ quá nhanh làm offline guessing dễ hơn.

Đối với **CBC legacy** không thể loại bỏ ngay, việc chuyển IV từ zero sang random **mới chỉ sửa một lỗi**; vẫn phải thêm xác thực đúng chuẩn và tương thích format, không coi CBC random IV là AEAD. Với **AES-GCM**, đổi mode mà giữ nonce cố định có thể còn gây nguy cơ nghiêm trọng; cặp key+nonce trùng là vi phạm trực tiếp điều kiện an toàn GCM. Cả hai bản “sửa một nửa” đều không đạt.

## 9. Lập kế hoạch sửa dữ liệu cũ và quản lý khóa

Đổi mã hóa không chỉ là sửa một dòng `getInstance`. Ciphertext cũ được tạo từ CBC/IV zero không thể bỗng nhiên giải mã đúng bằng AES-GCM; nếu ghi đè format mà không migration, ứng dụng sẽ mất khả năng đọc dữ liệu. Một kế hoạch thực tế cần:

1. **Kiểm kê:** xác định nơi thực sự gọi hàm cũ, các khóa/môi trường đang dùng, loại dữ liệu, số bản ghi, quyền truy cập và cả đường backup/export.
2. **Đánh giá khóa cũ:** nếu đã commit trong Git, image hoặc cung cấp rộng, phải coi nguy cơ lộ khóa là thật; rotation chỉ bảo vệ tốt hơn dữ liệu mã hóa **mới**, không đảo ngược việc lộ plaintext của các bản sao cũ.
3. **Tạo format mới có version/key ID:** mỗi record có khả năng xác định thuật toán và key đúng theo policy. Không đưa raw key vào packet; key ID chỉ là tham chiếu tới kho khóa.
4. **Chọn đường migration an toàn:** chỉ giải mã bằng legacy key/decoder trong luồng migration đã kiểm soát. Vì legacy CBC thiếu authentication, không tự tin rằng plaintext cũ chưa từng bị sửa; ưu tiên so đối chiếu với nguồn dữ liệu đáng tin, ràng buộc nghiệp vụ và kiểm tra định dạng trước khi re-encrypt.
5. **Re-encrypt dưới AEAD:** lưu version mới, nonce mới, ciphertext+tag, AAD đúng bản ghi; test đọc lại và rollback theo quy trình kiểm soát. Sao lưu có quyền truy cập hạn chế trong thời gian chuyển.
6. **Giảm/tháo hỗ trợ cũ:** sau migration, tắt write CBC, hạn chế read CBC theo thời hạn, loại bỏ khóa legacy theo retention và chính sách khôi phục, cập nhật tài liệu/on-call.
7. **Theo dõi và audit:** lượng ciphertext v1/v2, lỗi tag, lỗi key ID, số lượng migrate, thời hạn key, rủi ro nonce collision, truy cập trái phép và lỗi xử lý.

Không log key, passphrase, plaintext hoặc dữ liệu có thể giải mã từ log. Không hiển thị cho client lỗi “padding sai tại byte N” hay stack trace tiết lộ nội bộ. Với endpoint, trả lỗi hợp đồng nhất quán; ghi metric ẩn danh theo loại failure và correlation ID có kiểm soát.

### 9.1. Key ID và AAD phải được thiết kế cùng nhau

Nếu mỗi version có nhiều khóa được rotate, packet thực tế thường cần `keyId` để chỉ đúng khóa. `keyId` **không bí mật** nhưng là metadata có ảnh hưởng bảo mật: cần giới hạn ID hợp lệ, phạm vi tenant và mục tiêu khóa; khi bảo vệ bằng AEAD, có thể đưa version/key ID vào AAD được xác thực. Không bao giờ cho một key ID do attacker tùy chọn khiến server truy xuất bí mật thuộc tenant khác.

Nếu AAD chứa chuỗi `tenant=7;record=42`, ứng dụng không được lấy `tenant=7` từ chính ciphertext hoặc query parameter không được ủy quyền rồi coi đó là xác nhận. Đường đi đúng là nhận request → xác thực người gọi → xác định record và tenant được phép từ backend → dựng AAD → chọn khóa thuộc scope → decrypt → chỉ sử dụng plaintext khi tag hợp lệ.

### 9.2. Không dùng nonce ngẫu nhiên để “chữa” mọi bài toán

Nonce/IV không thay thế key management, authentication, authorization hoặc replay prevention. AES-GCM xác thực ciphertext và AAD, nhưng **không tự ngăn phát lại một packet hoàn toàn hợp lệ**. Nếu việc giải mã còn kích hoạt giao dịch hoặc ghi trạng thái, phải ràng buộc message ID, sequence number, expiry hoặc cơ chế chống replay ở tầng nghiệp vụ, và lưu trạng thái đã xử lý.

## 10. Mẫu báo cáo code review có thể kiểm chứng

Khi gửi PR review, nên mô tả dựa trên bằng chứng cụ thể thay vì ghi chung “thuật toán yếu”:

> **Phát hiện:** hàm `encryptInvoiceBad` dùng khóa AES hardcoded, CBC IV cố định và SHA-256 không khóa bên cạnh ciphertext. Điều này làm lộ khóa nếu source bị đọc, tái sử dụng IV khiến bản rõ cùng tiền tố có ciphertext block đầu giống nhau, và không xác thực được chỉnh sửa vì attacker có thể tính lại SHA-256. **Sửa:** chuyển dữ liệu ghi mới sang AES-GCM với fresh 96-bit nonce cho từng lần sử dụng key, tag 128 bit, AAD gắn tenant/record/version, key lấy từ kho quản lý; dùng format có version và kế hoạch migrate legacy. **Kiểm chứng:** round-trip hợp lệ; sửa ciphertext/tag, đổi tenant/record AAD hoặc khóa thì decrypt bị từ chối; cấu hình không có key literal/fixed nonce; test restart/rotation và key access policy.

Mẫu review tốt tách rõ ba tầng bằng chứng:

- **Bằng chứng từ mã:** dòng API, tham số, nguồn key/nonce, cách lưu ciphertext và nhánh xử lý exception.
- **Căn cứ chuẩn/API:** tài liệu Oracle `Cipher` về GCM, AAD và uniqueness của IV; OWASP về authenticated modes, cấm hardcode key; chính sách bảo mật dự án và threat model đã xác định.
- **Bằng chứng thực nghiệm:** test dương tính và âm tính, migration/restart, fuzz input và kiểm tra hệ thống quản lý khóa.

Tránh phát biểu “AES-256 là bảo mật tuyệt đối”, “dùng HTTPS thì crypto at rest không cần quản lý khóa”, hoặc “đã hash ciphertext nên bảo đảm không bị sửa”. Các câu này bỏ qua bối cảnh và trách nhiệm xác thực của từng thành phần.

## 11. Tự luyện tập với một cấu hình hoặc PR khác

1. Nếu thấy `Cipher.getInstance("AES")`, hãy xác định provider và transformation thực tế; vì sao phải chọn rõ mode/padding? Khi nào ECB làm lộ hình dạng/mẫu dữ liệu?
2. Nếu ai đó chuyển `AES/CBC/PKCS5Padding` thành `AES/GCM/NoPadding` nhưng giữ `new byte[12]` làm nonce, họ đã sửa đủ chưa? Vì sao **tái sử dụng key+nonce** nguy hiểm?
3. Nếu checksum được lưu cạnh ciphertext, ai có thể sửa checksum? Khi nào nó là expected digest từ nguồn tin cậy, khi nào chỉ là giá trị tự công bố?
4. Nếu dữ liệu v1 được decrypt thành công dưới khóa đúng nhưng người dùng tráo bản ghi 7 sang bản ghi 8, bạn cần ràng buộc gì trong AAD và kiểm tra quyền truy cập ở đâu?
5. Nếu một hệ thống dùng `SHA-256(password)` để xác minh đăng nhập, việc thêm salt không làm chậm đáng kể quá trình thử password. Nên dùng loại KDF/password hashing nào, cách chọn work factor ra sao?
6. Nếu muốn hỗ trợ các record cũ CBC, tại sao cần migration thay vì chỉ thay thuật toán và key? Làm sao tránh nhận ciphertext legacy không xác thực rồi dùng ngay như dữ liệu tin cậy?
7. Nếu Java catch `AEADBadTagException` rồi trả `new byte[0]`, hệ thống có phân biệt được “thông điệp trống hợp lệ” và “tag sai” không? Điều đó phá vỡ invariant nào?

Hoàn thành bài khi có thể chỉ ra ít nhất **một dấu hiệu mã/config sai → cơ chế gây hại → bản sửa → test kiểm chứng**, đồng thời phân biệt được confidentiality, integrity/authenticity, key management, nonce uniqueness và application authorization.

## Tài liệu tham khảo

- [Oracle Java SE 17 — Cipher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html): hợp đồng `AES/GCM/NoPadding`, `updateAAD`, `doFinal`, yêu cầu không lặp IV với cùng khóa.
- [Oracle Java SE 17 — GCMParameterSpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html): cấu hình độ dài tag và IV.
- [Oracle Java SE 17 — AEADBadTagException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/AEADBadTagException.html): lỗi khi không xác minh được authentication tag.
- [OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): dùng AEAD và key management, tránh ECB/khóa hardcoded.
- [OWASP Key Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html): quản lý vòng đời, lưu trữ, phân quyền và rotation.
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): password hashing và salt/work factor.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="security-practice-010">23.10 Tự đánh giá bằng lựa chọn cơ chế cho dữ liệu, danh tính và ranh giới tin cậy cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

# 23.10 Tự đánh giá bằng lựa chọn cơ chế cho dữ liệu, danh tính và ranh giới tin cậy cụ thể

## Mục tiêu và cách dùng bài thực hành

Đây là bài **tự đánh giá tích hợp** cho Java Advance — Security & Cryptography. Nhiệm vụ không phải kể tên càng nhiều thuật toán càng tốt; bạn phải chọn **cơ chế bảo vệ phù hợp với một dữ liệu cụ thể, một danh tính cụ thể và một ranh giới tin cậy cụ thể**, sau đó giải thích vì sao lựa chọn đó đủ hoặc chưa đủ trước kịch bản tấn công.

Sau bài này, bạn có thể:

1. Viết một threat model ngắn nêu **tài sản**, **chủ thể**, **đối thủ**, **đường đi dữ liệu** và **ranh giới tin cậy**.
2. Phân biệt các mục tiêu: **bí mật (confidentiality)**, **toàn vẹn (integrity)**, **xác thực (authentication)**, **phân quyền (authorization)**, **chống phát lại (replay resistance)** và **tính sẵn sàng (availability)**.
3. Chọn các cơ chế thích hợp: TLS/mTLS, AEAD, password hashing, HMAC, chữ ký số, xác minh token, quản lý khóa qua KMS/HSM, chứng chỉ/PKI và kiểm soát truy cập.
4. Nhận ra cơ chế không đáp ứng mục tiêu: SHA-256 không mã hóa dữ liệu, chữ ký không giữ bí mật, mTLS không tự cấp quyền nghiệp vụ, mã hóa ổ đĩa không bảo vệ dữ liệu đã giải mã trong tiến trình, JWT có chữ ký không tự ẩn claims.
5. Đưa ra **bằng chứng kiểm thử**, tình huống thất bại và kế hoạch vòng đời khóa/định danh cho đề xuất của mình.

**Cách học:** Đọc bối cảnh và câu hỏi ở phần I–II, tự ghi đáp án vào mẫu đánh giá, sau đó đối chiếu phần III. Phần IV có chương trình Java 17+ cho hai phép kiểm chứng mật mã độc lập; phần V giúp chấm điểm và sửa các khoảng trống.

## I. Phương pháp chọn cơ chế: xuất phát từ tài sản và ranh giới

### 1. Sáu câu hỏi không được bỏ qua

Trước khi viết `Cipher.getInstance(...)` hoặc cấu hình `SSLContext`, hãy trả lời đủ:

| Câu hỏi | Ví dụ cần ghi cụ thể | Vì sao cần? |
|---|---|---|
| **Cái gì?** Dữ liệu và độ nhạy | Bản ghi hóa đơn có tên và số tài khoản, private signing key, bearer token | Không phải mọi tài sản đều cần cùng kiểu bảo vệ |
| **Ai?** Danh tính và quyền | Người dùng A thuộc tenant X; workload `invoice-api`; đối tác ngoài doanh nghiệp | Biết ai được phép đọc, ký, giải mã, thay đổi hoặc gọi API |
| **Đi đâu?** Biên giới tin cậy | Browser → gateway; gateway → service; service → database; service → đối tác | Lựa chọn TLS, xác thực peer và kiểm soát truy cập theo từng đường |
| **Chống ai?** Kịch bản tấn công | Nghe trộm mạng, sửa ciphertext, copy bản ghi giữa tenant, đánh cắp DB backup, giả webhook | Mỗi cơ chế chỉ giải quyết một tập nguy cơ nhất định |
| **Bằng gì?** Cơ chế/khóa/thuật toán | TLS 1.3; AES-GCM; Ed25519; Argon2id; HMAC-SHA-256; key trong KMS | Phải có API và chính sách khóa thực hiện được |
| **Kiểm chứng thế nào?** Test/quan sát/khắc phục | Sai AAD phải fail; chữ ký bị đổi byte phải fail; revoke quyền phải fail; mất KMS phải báo lỗi an toàn | Nếu không định nghĩa hành vi thất bại thì chưa chứng minh thiết kế an toàn |

Điểm quan trọng: **một ứng dụng có thể dùng nhiều cơ chế cho một đường dữ liệu**. Ví dụ TLS bảo vệ dữ liệu trên đường truyền, AES-GCM bảo vệ bản ghi khi lưu, RBAC/ABAC quyết định ai được đọc bản ghi, còn audit ghi lại việc truy cập. Những lớp này không hoán đổi cho nhau.

### 2. Đừng nhầm sáu mục tiêu bảo mật

| Mục tiêu | Câu hỏi chính | Cơ chế thường phù hợp | Cơ chế dễ bị dùng nhầm |
|---|---|---|---|
| Bí mật | Ai có thể **đọc nội dung**? | TLS trên đường truyền; AES-GCM/AEAD khi lưu; quản lý khóa | `MessageDigest(SHA-256)`, chữ ký số, chỉ Base64 |
| Toàn vẹn | Nội dung có bị **sửa** không? | AEAD tag, HMAC, chữ ký số | Mã hóa không xác thực, checksum không có khóa chống attacker chủ động |
| Xác thực danh tính | Ta đang nói chuyện với **ai**, dựa trên bằng chứng nào? | OIDC/session/passkey cho người dùng; certificate/mTLS hoặc workload identity; chữ ký với public key được tin cậy | Tên service trong HTTP header chưa được xác thực |
| Phân quyền | Chủ thể **được làm gì trên bản ghi nào**? | Authorization policy theo role/tenant/object/operation, enforce tại service | Chỉ có JWT hợp lệ, chỉ có TLS, chỉ có certificate |
| Chống phát lại | Request/message cũ có thể được gửi lại? | Nonce/timestamp, event ID, dedupe, idempotency và cửa sổ thời gian | HMAC/chữ ký hợp lệ nhưng thiếu freshness |
| Sẵn sàng/khả năng phục hồi | Khi KMS, HSM, CA hoặc identity provider lỗi thì sao? | Fail-closed đúng chỗ, retry có giới hạn, backup/rotation, HA, khôi phục khóa | “Bật encryption” rồi bỏ qua vận hành |

**Tính toàn vẹn mật mã không đồng nghĩa tính đúng đắn nghiệp vụ.** Một hóa đơn có chữ ký hợp lệ có thể vẫn có số tiền sai nếu người ký được cấp quyền sai; service phải validate rules, nguồn dữ liệu và quyền trước khi ký.

### 3. Bảng chọn nhanh cơ chế Java 17+

| Bài toán | Cơ chế phù hợp | API/JDK hoặc tích hợp | Điều kiện không được quên |
|---|---|---|---|
| Dữ liệu nhạy cảm được lưu cần đọc lại | AEAD: `AES/GCM/NoPadding` và key từ KMS/keystore/HSM nếu phù hợp | `javax.crypto.Cipher`, `GCMParameterSpec`, `KeyGenerator`, `SecureRandom` | **Không dùng lại nonce với cùng khóa**; xác thực tag trước khi dùng plaintext; quản lý khóa riêng |
| Giao tiếp browser/API | HTTPS/TLS 1.3 (TLS 1.2 nếu cần và được cấu hình an toàn) | JSSE/HTTP client/server | Xác minh hostname/chain, trust anchors; redirect/headers/session policy |
| Danh tính service qua mạng | mTLS, workload identity, token audience-bound theo kiến trúc | `SSLContext`/`KeyManager`/`TrustManager` và policy | Xác thực service **khác** kiểm tra quyền đối với dữ liệu |
| Mật khẩu người dùng lưu trong DB | Password hashing chuyên dụng: Argon2id; scrypt/PBKDF2 khi yêu cầu phù hợp | Thư viện mật khẩu được bảo trì; JDK `SecretKeyFactory` cho PBKDF2 | Salt ngẫu nhiên riêng mỗi mật khẩu; work factor; rate limit/MFA; không dùng SHA-256 nhanh |
| Webhook giữa hai bên cùng giữ secret | HMAC-SHA-256 trên bytes đã quy định | `javax.crypto.Mac` | Secret đủ entropy, lưu an toàn, key ID, thời gian/nonce/event ID chống replay, so sánh constant-time |
| Báo cáo cần xác minh ngoại tuyến bởi bên thứ ba | Chữ ký số: Ed25519, ECDSA hoặc RSA-PSS theo compatibility | `java.security.Signature`, `KeyStore` | Xác minh public key/trust, bytes chuẩn hóa, signer authority, thời gian và revocation |
| Chỉ phát hiện thay đổi do lỗi vô ý | Checksum/digest | `MessageDigest` | Không chống kẻ tấn công có thể thay cả file lẫn digest |
| Phân quyền người dùng đối với record | Quy tắc server-side theo tenant/object/action | Middleware và domain authorization | Phải kiểm tra mỗi request; không tin tenantId do client tự khai |
| Bearer token cho API | OIDC/OAuth2 theo profile; token ký và validation toàn bộ claims | Thư viện JWT/OIDC tin cậy | Verify `iss`, `aud`, `exp`, thuật toán allowlist, key ID có kiểm soát; token ký không mặc định mã hóa |
| Private key không thể xuất khỏi HSM | Provider dùng opaque key handle | `KeyStore("PKCS11")`/`Signature`/`Cipher` theo vendor | Kiểm tra key usage, mechanism, quyền gọi key và audit; không gọi `getEncoded()` |

Hai trường hợp dễ nhầm:

- **SHA-256 của file** giúp phát hiện lỗi nếu digest nằm trong một kênh đáng tin, nhưng nếu attacker đổi được cả file và checksum, bạn cần HMAC/chữ ký mới kiểm chứng được nguồn/toàn vẹn chống attacker.
- **Hash mật khẩu** cần cố ý *tốn tài nguyên* và có salt. `MessageDigest.getInstance("SHA-256")` đủ nhanh để làm digest nhưng chính vì thế **không thích hợp để lưu mật khẩu**. Token ngẫu nhiên có entropy cao là bài toán khác; có thể lưu hash/HMAC token theo threat model mà không áp dụng trực tiếp quy tắc mật khẩu con người.

## II. Bài toán tích hợp: một hệ thống nhiều ranh giới

### 4. Bối cảnh giả định — hệ thống Sổ Cái

Một nền tảng SaaS tên **Sổ Cái** phục vụ hai doanh nghiệp `tenant-alpha` và `tenant-beta`. Khách hàng đăng nhập trên trình duyệt để xem và phê duyệt hóa đơn. Kiến trúc có các thành phần:

```text
[Browser: người dùng]
       |
       | R1: Internet, không tin trình duyệt
       v
[API Gateway / TLS termination] --- [Identity Provider]
       |
       | R2: mạng dịch vụ nội bộ vẫn có thể bị xâm nhập
       v
[Invoice API] ---- R3 ----> [Database + Object Storage + Backup]
       |
       +---- R4 ----> [Event Queue] ---> [Ledger Consumer]
       |
       +---- R5 ----> [Partner Webhook/Callback ngoài tổ chức]
       |
       +---- R6 ----> [Xuất báo cáo được ký] ---> [Bên kiểm toán offline]

[Invoice API / Signing Service] ---- R7 ----> [KMS hoặc HSM]
```

**Tài sản và dữ liệu:**

- `D1`: mật khẩu người dùng; người đăng nhập có thể dùng MFA hoặc passkey.
- `D2`: hóa đơn chứa tên người mua, thông tin tài khoản, số tiền, tenantId, recordId; phải đọc được cho nghiệp vụ hợp lệ.
- `D3`: access token/session cookie để gọi API.
- `D4`: sự kiện `InvoiceApproved` trên queue gồm `tenantId`, `invoiceId`, `amount`, `eventId`.
- `D5`: webhook partner báo giao dịch thành công, có `messageId`, `timestamp`, `payload`.
- `D6`: báo cáo PDF/JSON đã chốt cần bên kiểm toán xác minh ngoại tuyến.
- `D7`: data encryption key (DEK), signing private key, CA/trust anchors, secret webhook.

**Danh tính và đối thủ:**

- `I1`: người dùng là con người thuộc đúng tenant; `I2`: process/workload `invoice-api`; `I3`: service `ledger-consumer`; `I4`: đối tác bên ngoài; `I5`: bên kiểm toán chỉ có public verification key.
- `T1`: attacker nghe/chèn thông điệp trên mạng; `T2`: attacker đọc bản backup DB; `T3`: kẻ có quyền ghi một hàng DB nhưng không có quyền KMS; `T4`: kẻ gửi lại webhook/event hợp lệ; `T5`: client tenant-alpha cố đọc hóa đơn của tenant-beta; `T6`: service đã bị xâm nhập có thể gọi API ký.
- Giả định có `KMS/HSM` được vận hành tách quyền, nhưng **không** giả định nó sẽ tự chặn mọi thao tác sai nghiệp vụ. Tùy tình huống, có thể cần cả khóa tenant riêng, IAM và audit.

Bài này kiểm tra **thiết kế biên giới tin cậy**, không chỉ biên dịch một chương trình Java. Bạn phải chỉ ra bên nào chứng thực bên nào và dữ liệu có đổi trạng thái bảo vệ khi đi qua từng đường không.

### 5. Mẫu trả lời bắt buộc cho mỗi quyết định

Trước khi xem đáp án mẫu, thử hoàn thành một bảng như sau cho từng câu hỏi:

| Trường | Cần ghi |
|---|---|
| Asset / classification | Ví dụ `D2`: PII + tài chính, cần bí mật, toàn vẹn và audit |
| Chủ thể / trust boundary | Ví dụ `I1` tại browser (không tin) → `invoice-api`, R1/R2 |
| Attacker/khả năng | Đọc bản backup; đổi tenantId; phát lại request |
| Mục tiêu cụ thể | Chỉ người có quyền trong tenant đọc, không sửa được record mà không phát hiện |
| Cơ chế và API | TLS + server-side object authorization + AES-GCM; `Cipher`, `GCMParameterSpec` |
| Quản lý key/identity | KMS, key version, identity của workload được cấp quyền decrypt |
| Thất bại cần thấy | Sai tenant/bản ghi/AAD gây `AEADBadTagException`; thiếu quyền gây 403 |
| Vận hành | Rotation, auditing, backup, latency, lỗi KMS, giới hạn log |

Một đáp án chỉ có “dùng AES-256” là **chưa đạt**, vì chưa trả lời ai giữ khóa, ai được gọi decrypt, mã hóa những trường nào, dữ liệu qua ranh giới nào và kiểm thử ra sao.

### 6. Mười câu tự đánh giá — làm trước khi đọc phần III

**Câu 1 — R1: định danh người dùng và mật khẩu.** Người dùng `tenant-alpha` đăng nhập bằng mật khẩu vào trang web qua Internet. Chọn cơ chế lưu mật khẩu, bảo vệ phiên, xử lý MFA/session, và cách xác định tenant của người dùng. Vì sao `SHA-256(password)` hoặc “mã hóa AES để lấy lại mật khẩu” không hợp lý?

**Câu 2 — R1/R2: quyền đọc bản ghi.** Người dùng có access token được ký hợp lệ, nhưng đổi URL từ `/tenants/alpha/invoices/7` sang `/tenants/beta/invoices/7`. Cơ chế nào quyết định 403/404? Vai trò của TLS, JWT signature và authorization tại Invoice API khác nhau thế nào?

**Câu 3 — R3: dữ liệu nhiều tenant khi lưu.** Cần lưu `D2` sao cho người có bản backup hoặc quyền đọc DB (nhưng **không** có quyền KMS) không đọc được plaintext. Chọn encryption mode, AAD, nonce/IV, key hierarchy, và test chống việc copy ciphertext của alpha sang beta.

**Câu 4 — R2: hai microservice nội bộ.** Gateway đã kết thúc TLS của browser, sau đó gọi Invoice API qua mạng nội bộ. Có nên bỏ TLS vì “đã ở trong VPC” không? Chọn cách xác thực workload và xác định ai được gọi endpoint `approveInvoice`.

**Câu 5 — R4: event queue.** Attacker có thể đọc/sửa/phát lại message trên queue, trong khi consumer cần biết message xuất phát từ producer được phép. Chọn transport protection, message integrity/origin, khóa và replay defense; khi nào HMAC đủ và khi nào chữ ký số hợp lý hơn?

**Câu 6 — R5: webhook từ đối tác ngoài.** Đối tác gửi `POST /callback` qua HTTPS với timestamp, messageId và JSON body; attacker có thể lưu một request hợp lệ rồi gửi lại. Chọn cơ chế kiểm tra nguồn request và replay, và định nghĩa chính xác bytes được MAC/sign. Vì sao chỉ kiểm tra `X-Partner-Id` hoặc IP không đủ?

**Câu 7 — R6: báo cáo kiểm toán offline.** Bên kiểm toán chỉ được giữ public key, cần phát hiện tài liệu bị sửa và xác minh người/pháp nhân ký. Chọn HMAC hay `Signature`? Có cần mã hóa nội dung không? Trình bày cách ánh xạ public key tới signer có thẩm quyền và quá trình rotation/revocation.

**Câu 8 — R7: private key trong HSM.** Signing Service phải ký tài liệu nhưng không được lấy bytes private key. Thiết kế đường gọi Java phù hợp, giới hạn quyền của workload và policy thiết bị. Nếu HSM có khóa “non-exportable” nhưng service bị chiếm và ký được 1.000 tài liệu giả, yếu tố nào đang thiếu?

**Câu 9 — toàn hệ thống: thay khóa và mất khóa.** Một DEK/KEK nghi lộ hoặc signing key bị thu hồi. Cần chọn key version, envelope encryption, rewrap/re-encrypt, khả năng giải mã dữ liệu cũ, xác minh chữ ký lịch sử và thời điểm loại bỏ key. Tại sao không chỉ xóa key cũ ngay lập tức?

**Câu 10 — kiểm thử ranh giới.** Thiết kế ít nhất **sáu phép kiểm âm** có thể tự động chạy: sai tenant, sửa ciphertext/tag, thay bytes tài liệu, hết hạn token, phát lại webhook, danh tính service sai. Ghi rõ kiểm tra ở API hay mật mã và kết quả an toàn kỳ vọng.

Có thể chấm mỗi câu tối đa 4 điểm theo rubric ở phần V. Hãy ghi rõ những giả định chưa biết (ví dụ identity provider, khả năng HSM, dữ liệu nào bắt buộc giữ bí mật, yêu cầu pháp lý); không tự bịa chúng thành sự thật của hệ thống giả định.

## III. Đáp án tham khảo và lập luận

### 7. Câu 1 — mật khẩu, phiên và tenant identity

**Lựa chọn phù hợp:**

- Lưu password verifier bằng **Argon2id** cùng salt ngẫu nhiên độc lập cho từng mật khẩu và tham số công sức tính toán; chọn thư viện còn được duy trì. Trong hệ thống có yêu cầu compatibility đặc thù, cân nhắc scrypt hoặc PBKDF2-HMAC-SHA-256 với cost đã benchmark theo chính sách.
- Xác thực qua HTTPS; bổ sung rate limiting, chính sách chống credential stuffing, MFA/passkey phù hợp nguy cơ; không ghi password/OTP vào log.
- Sau login, dùng session do server quản lý hoặc OIDC/OAuth2 flow phù hợp; browser cookie nên `Secure`, `HttpOnly`, `SameSite` theo mô hình ứng dụng, đồng thời có chống CSRF khi dùng cookie xác thực.
- Thiết lập `tenantId` từ **nguồn định danh tin cậy** và mối liên kết tenant membership đã được kiểm chứng. Không coi query parameter hay JSON body `tenantId` là bằng chứng phân quyền.

**Vì sao:** mật khẩu của con người thường có entropy thấp; SHA-256 quá nhanh để chống dò offline. AES là reversible encryption và bắt buộc có decryption key: lộ khóa có thể khiến toàn bộ password bị khôi phục. Phiên còn cần hết hạn/revoke và bảo vệ chống đánh cắp, không được coi mật khẩu đúng một lần là quyền vĩnh viễn.

**Kiểm thử:** cùng password hai người phải có hash lưu khác nhau do salt; nhập sai phải fail; session hết hạn không được dùng lại; một user được thêm/loại khỏi tenant phải có hiệu lực theo chính sách session/authorization.

### 8. Câu 2 — chữ ký token không cấp quyền dữ liệu

Một JWT được verify thành công xác nhận **token không bị sửa và do khóa phù hợp tạo ra**; còn cần kiểm tra issuer, audience, hạn, trạng thái phiên và chính sách token cụ thể. `iss`, `aud`, `exp` phải theo profile token và validation của ứng dụng; token không đáng tin chỉ vì decode được hoặc vì header tự khai thuật toán/`kid`.

- API phải xác thực user/service principal, lấy membership/tenant từ dữ liệu tin cậy, rồi áp dụng **object-level authorization** cho invoice `7` trên tenant được yêu cầu.
- Nếu user alpha gọi đường beta thì trả lời từ chối theo policy (403 hoặc 404 che giấu tồn tại tài nguyên), tránh lộ chi tiết khác tenant.
- TLS bảo vệ channel; chữ ký token bảo vệ tính toàn vẹn claims; policy nghiệp vụ quyết định **được quyền đọc/duyệt** record nào. Ba lớp không thay thế nhau.

**Kiểm thử:** token alpha hợp lệ vẫn không đọc/sửa beta. Chỉ thay tenantId trong URL, header hoặc body không đủ nâng quyền. Thử token thiếu `aud` hoặc audience sai, `exp` đã qua và thuật toán không nằm trong allowlist.

### 9. Câu 3 — encryption at rest + ràng buộc tenant qua AAD

**Chọn AES-GCM/AEAD**, ví dụ `AES/GCM/NoPadding` với key 256-bit được sinh an toàn, IV/nonce 96-bit duy nhất cho mỗi lần encrypt dưới cùng key và tag 128-bit. Kèm AAD chứa **ngữ cảnh phải gắn với dữ liệu** như `version`, `tenantId`, `recordId`, `schemaVersion`. Mã hóa các trường bí mật; không tự động mã hóa toàn bộ metadata nếu hệ thống cần chỉ mục/tìm kiếm có thiết kế riêng.

- Một envelope thông thường chứa `keyVersion`, `nonce`, `ciphertext + authenticationTag` và metadata không bí mật. **Nonce không cần bí mật**, nhưng phải độc nhất theo key; tag phải được verify trước khi sử dụng plaintext.
- DEK dùng mã hóa record/object; KEK trong KMS bảo vệ DEK đã wrap. Workload phải có IAM cho decrypt theo mục đích/tenant, và toàn bộ hoạt động được audit. Nếu không có tách quyền, attacker điều khiển Invoice API có thể vẫn gọi KMS: encryption at rest không tự bảo vệ khỏi runtime compromise.
- Khi decrypt, **AAD phải được tái tạo từ ngữ cảnh tenant/record được service xác thực và quyền đã kiểm tra**, không đơn thuần tin tenantId do caller gửi. Sao chép ciphertext + nonce + tag sang tenant khác phải thất bại khi AAD beta khác alpha.
- Nếu dùng random nonce 96-bit cho AES-GCM, phải đánh giá xác suất va chạm theo **số bản mã trên mỗi key**; hệ thống khối lượng lớn cần chiến lược uniqueness có kiểm soát và key rotation. Không reset counter vô điều kiện khi restart.

**Kiểm thử:** đổi một byte ciphertext hoặc tag → `AEADBadTagException`; AAD tenant khác → từ chối; sai key/version → từ chối; không rơi lại `AES/ECB` hoặc plaintext khi KMS mất kết nối.

**Giới hạn:** AAD **được xác thực nhưng không mã hóa**. Không đặt PII ở AAD nếu yêu cầu bảo mật nội dung đó; nếu bắt buộc, chuyển thành định danh không nhạy cảm hoặc mã hóa cùng plaintext. AEAD chống sửa bản mã, không ngăn service hợp lệ sửa dữ liệu sau khi giải mã theo đường nghiệp vụ.

### 10. Câu 4 — TLS sau gateway và danh tính workload

Việc TLS browser kết thúc ở gateway tạo **ranh giới mới**. Gateway → Invoice API cần bảo vệ riêng theo threat model; đừng giả định VPC/private network làm mọi peer đáng tin.

- Chọn TLS khi đi trên mạng nội bộ; mTLS hoặc cơ chế workload identity được quản lý để xác thực peer khi thích hợp. Xác minh certificate chain/identity của service và mapping danh tính workload từ SAN/SPIFFE ID hoặc một cơ chế tương đương đã được kiểm soát.
- API kiểm tra riêng quyền `approveInvoice`: service identity `gateway` có được gọi không, còn principal người dùng có được duyệt invoice đó không, trạng thái invoice có cho phép thao tác không.
- Nếu gateway chuyển identity qua headers, Invoice API chỉ tin headers được truyền bởi đường **đã xác thực và kiểm soát**; không để client Internet tự điền `X-User-Id`/`X-Tenant-Id` rồi qua gateway.
- Thiết kế connection pooling, certificate rotation, trust anchors, clock/expiry và fail-closed khi identity peer không hợp lệ.

**Kiểm thử:** dùng certificate của workload khác phải bị từ chối; gateway bị cấu hình sai identity phải fail; người dùng được auth nhưng không có quyền invoice phải fail; certificate hết hạn phải fail theo chính sách.

### 11. Câu 5 — queue: integrity, origin và replay

Trong hệ thống producer/consumer cùng một trust domain, lựa chọn ban đầu là **ACL/IAM trên queue + TLS và identity cho client**. Nếu attacker có khả năng sửa event khi đã ở queue hoặc có trung gian không tin cậy, cần bảo vệ **message-level integrity/origin**:

- **HMAC-SHA-256** đủ nếu producer và consumer tin cậy nhau, có thể chia sẻ secret, và không cần bên thứ ba chứng minh ai đã ký. Với HMAC, mọi bên có secret đều có thể tạo MAC — không có thuộc tính xác minh độc lập như chữ ký số.
- **Chữ ký số** phù hợp nếu các bên cần phân tách quyền ký/xác minh, số consumer lớn hoặc verifier không được phép tạo message. Phải phân phối public key đáng tin, kiểm tra key version và identity của signer.
- Cả HMAC và signature đều xác thực **bytes**; cần định nghĩa encoding/canonical representation (schema, field ordering, Unicode, version). Tránh ký JSON rồi verify JSON serialize lại theo thứ tự khác nhau.
- Chống replay bằng `eventId`, timestamp/TTL phù hợp, dedupe/idempotency lưu bền vững; giả định queue có thể giao ít nhất một lần và có retry. Dedupe phải áp dụng **trước tác động nghiệp vụ không lặp**, với giao dịch/unique constraint tương thích.

**Kiểm thử:** sửa amount hoặc tenant → MAC/signature fail; message nguyên vẹn gửi lại → chỉ một lần hạch toán; message với keyId không tồn tại hoặc bị thu hồi → từ chối theo policy.

### 12. Câu 6 — webhook ngoài tổ chức

HTTPS bảo vệ kênh và xác minh server khi caller là client; **HTTPS thông thường không chứng minh webhook được gửi bởi đối tác cụ thể**. Header `X-Partner-Id` hoặc IP là metadata có thể giả/mất ổn định qua proxy.

Một lựa chọn hợp lý là:

1. Cấp **secret HMAC riêng cho từng đối tác/endpoint** qua kênh quản trị an toàn, có `keyId` và quy trình rotation.
2. Định nghĩa chuỗi bytes cần xác thực, ví dụ: `version || timestamp || messageId || rawBody` với **framing không nhập nhằng** (độ dài trường, định dạng chính xác hoặc canonical encoding). Tính `HMAC-SHA-256` trên cùng bytes ở hai phía; không tính trên JSON đã parse/serialize nếu protocol không quy định chuẩn hóa.
3. Verify MAC bằng phép so sánh **constant-time** của kết quả đã decode; chỉ chấp nhận thuật toán đã allowlist; không chọn secret hay quyền theo header chưa xác thực.
4. Kiểm tra timestamp trong cửa sổ cho phép, đồng hồ/clock skew, uniqueness của `messageId` và lưu dedupe đủ lâu. Thực hiện tác động nghiệp vụ idempotent.
5. Kiểm tra partner identity, tenant binding, quyền gửi loại event, schema, amount và trạng thái giao dịch **sau** khi xác thực thông điệp.

**Kiểm thử:** body sửa 1 byte → fail; timestamp hết hạn → fail; messageId lặp → không xử lý hai lần; key đối tác A không ký được message cho B. Không log raw secret, MAC bí mật, token hoặc body nhạy cảm.

Nếu đối tác không thể chia sẻ secret giữa các trust domain hoặc cần xác minh độc lập, có thể dùng **chữ ký số** với public key được pin/định danh và rotation được quản lý. Chọn theo hợp đồng giao tiếp thực tế.

### 13. Câu 7 — báo cáo cần chứng minh nguồn và toàn vẹn

Dùng **chữ ký số** bằng `java.security.Signature`, ví dụ Ed25519/ECDSA/RSA-PSS nếu hệ sinh thái người ký và bên kiểm toán hỗ trợ. HMAC không phù hợp khi bên kiểm toán chỉ được **verify mà không được tạo** chữ ký; họ không nên giữ cùng secret với signer.

- Xác định **đúng bytes** được ký: PDF ở trạng thái cuối với chuẩn signature dành cho PDF hoặc JSON được chuẩn hóa bằng quy tắc đã thống nhất; bao gồm version, report ID, tenant, thời điểm và mục đích.
- Private key có thể nằm trong HSM; verifier nhận public key/certificate được gắn với signer có thẩm quyền và policy riêng. Chữ ký hợp lệ với public key bất kỳ chưa chứng minh người ký hợp pháp.
- Nếu có certificate/PKI, kiểm tra chain, trust anchor, mục đích sử dụng, thời hạn và trạng thái thu hồi theo chính sách. Với chứng từ cần xác minh nhiều năm, cân nhắc trusted timestamp, evidence archival và chính sách lưu public key để xử lý sau key rotation.
- Chữ ký **không làm report bí mật**; nếu report chứa dữ liệu nhạy cảm và được gửi qua mạng hoặc lưu ở kho ngoài, cần bổ sung TLS/mã hóa at rest/kiểm soát quyền. Không cố dùng `Cipher.ENCRYPT_MODE` với private key để thay chữ ký số.

**Kiểm thử:** thay byte trong report → verify false; verify bằng public key không thuộc signer cho phép → từ chối; key ký hết quyền hoặc bị thu hồi → áp dụng policy với bằng chứng thời điểm, không tùy tiện kết luận toàn bộ chữ ký lịch sử đều vô giá trị.

### 14. Câu 8 — HSM không xuất khóa vẫn cần quyền gọi key

Một workflow Java có thể sử dụng `KeyStore.getInstance("PKCS11", provider)` để lấy `PrivateKey` dưới dạng opaque handle, rồi gọi `Signature.getInstance("Ed25519", provider)` hoặc thuật toán **thực sự được thiết bị cung cấp** và `initSign(privateKey)`. Tùy HSM, provider Java có thể hỗ trợ RSA/ECDSA nhưng chưa chắc hỗ trợ Ed25519; không giả định tất cả mechanism đều có.

**Quyền cần kiểm tra riêng:**

- KMS/HSM phân quyền theo key alias/role/operation (chỉ `SIGN`, không `DECRYPT` nếu không cần), xác thực workload, rate limit/quota, mTLS/token tới thiết bị.
- Trước mỗi yêu cầu ký, service phải xác minh tenant, principal, quyền ký báo cáo, nguồn dữ liệu, trạng thái phê duyệt, nội dung/hàm băm chính xác và idempotency.
- Đối với giao dịch giá trị cao, có thể cần approval nhiều người, dual control hoặc signing policy độc lập với process ứng dụng.
- Không gọi `privateKey.getEncoded()`, không xuất PKCS#8 ra file, không đặt PIN trong source/command line/log.

Nếu service bị xâm nhập vẫn ký được 1.000 tài liệu sai, **non-exportability chỉ ngăn lấy bytes khóa**, chưa ngăn **lạm dụng quyền ký**. Lớp thiếu có thể là workload authorization, policy engine độc lập, giới hạn/phê duyệt theo nội dung, quota, audit hoặc phát hiện bất thường. HSM là ranh giới bảo vệ vật liệu khóa, không phải bộ xác thực toàn bộ nghiệp vụ.

### 15. Câu 9 — vòng đời khóa, rewrap và chữ ký lịch sử

Một kế hoạch đúng phải có:

- **Key inventory:** `keyId`, version, thuật toán, purpose, owner/tenant, trạng thái active/decrypt-only/retired/revoked, nơi lưu, policy và người được sử dụng.
- **Encryption key rotation:** khi đổi KEK có thể **rewrap DEK** mà không mã hóa lại toàn bộ dữ liệu, nếu threat model và key hierarchy cho phép. Khi DEK lộ hoặc thuật toán/nonce policy cần thay, thường phải **decrypt rồi re-encrypt** dữ liệu với DEK/nonce mới qua quy trình an toàn và có checkpoint.
- **Historical reads:** dữ liệu cũ mang `keyVersion` phải vẫn được giải mã trong thời gian lưu trữ bắt buộc nếu không có kế hoạch migrate. Xóa old key trước khi re-encrypt/backup hoàn tất có thể gây mất dữ liệu vĩnh viễn.
- **Signing key rotation:** key cũ có thể ngừng **ký mới** nhưng public verification key/cert và evidence cần giữ theo chính sách để kiểm chữ ký lịch sử. Nếu key bị compromise, cần thu hồi, đánh giá phạm vi chữ ký và thời gian nghi vấn, không chỉ xoay key mà bỏ qua incident.
- **Incident & operations:** vô hiệu hóa quyền, lưu audit, backup/restore có kiểm tra, chứng minh không xuất private key nếu cần; phân tách quyền quản lý key với service runtime.

**Kiểm thử:** sau rotate, bản mới dùng key version mới; bản cũ vẫn đọc được theo policy; khi access KMS bị revoke service không giải mã được; backup/restore đúng dữ liệu; signing key cũ không ký mới nhưng verifier kiểm tra bằng chứng lịch sử đúng quy tắc.

### 16. Câu 10 — kiểm thử âm cần thể hiện biên giới

| Kịch bản âm | Tầng phải chặn | Kết quả mong đợi |
|---|---|---|
| User alpha đổi tenant sang beta | Authorization ở Invoice API; AAD có thể tăng phòng thủ cho ciphertext | Từ chối truy cập bản ghi, không lộ chi tiết khác tenant |
| Thay ciphertext/tag/AAD khi lưu | `Cipher.doFinal` AES-GCM | `AEADBadTagException`, không sử dụng plaintext |
| Sửa bytes report sau khi ký | `Signature.verify` + signer trust policy | Chữ ký không hợp lệ |
| Access token hết hạn hoặc sai `aud` | Authentication/token validation | 401/403 theo policy, không chấp nhận claims |
| Webhook có HMAC đúng nhưng `messageId` lặp | Anti-replay/idempotency trong service | Không xử lý giao dịch lần thứ hai |
| Workload không có cert/identity đúng | mTLS/workload-auth layer | Handshake hoặc authz từ chối |
| Event trên queue sửa `amount`/`tenantId` | HMAC/chữ ký trên representation đã chuẩn hóa | Consumer từ chối event |
| HSM key chỉ `SIGN` nhưng yêu cầu decrypt | HSM provider + policy | Từ chối thao tác |
| KMS không sẵn | Crypto/KMS integration | Không trả plaintext bằng cách bỏ encryption, báo lỗi có kiểm soát |
| Token bị đánh cắp nhưng chưa hết hạn | Session/revocation, sender binding, authorization/risk controls | Từ chối sau revoke hoặc bắt xác thực lại theo policy |

**Điểm tinh tế:** nhiều phép kiểm âm là **authorization hoặc vận hành**, không phải phép kiểm mật mã. Không cố biến mọi lỗi thành `InvalidKeyException` hoặc `AEADBadTagException`; kiểm thử phải đặt đúng lớp.

## IV. Phòng thí nghiệm Java 17+: chứng minh hai mục tiêu độc lập

### 17. AEAD ràng buộc ngữ cảnh; chữ ký phát hiện sửa thông điệp

Chương trình này **chỉ minh họa primitive mật mã trong một JVM** bằng khóa thử sinh tạm. Nó không xác thực người dùng thật, không tạo danh tính bên ngoài, không chứng minh quản lý khóa HSM, và không thay thế authorization hay replay defense của hệ thống Sổ Cái.

Tạo file **`BoundaryMechanismsDemo.java`**:

```java
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class BoundaryMechanismsDemo {
    private static final int GCM_TAG_BITS = 128;

    private static byte[] decrypt(SecretKey key, byte[] nonce,
                                  byte[] aad, byte[] ciphertext)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(GCM_TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext);
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();

        byte[] nonce = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(nonce);
        byte[] aad = "v1|tenant=alpha|record=inv-7"
                .getBytes(StandardCharsets.UTF_8);
        byte[] payload = "amount=1250&currency=VND"
                .getBytes(StandardCharsets.UTF_8);

        Cipher encryption = Cipher.getInstance("AES/GCM/NoPadding");
        encryption.init(Cipher.ENCRYPT_MODE, key,
                new GCMParameterSpec(GCM_TAG_BITS, nonce));
        encryption.updateAAD(aad);
        byte[] ciphertext = encryption.doFinal(payload);

        byte[] recovered = decrypt(key, nonce, aad, ciphertext);
        System.out.println("Encrypted data recoverable: " +
                Arrays.equals(payload, recovered));

        byte[] wrongTenant = "v1|tenant=beta|record=inv-7"
                .getBytes(StandardCharsets.UTF_8);
        boolean tenantSwapRejected = false;
        try {
            decrypt(key, nonce, wrongTenant, ciphertext);
        } catch (AEADBadTagException expected) {
            tenantSwapRejected = true;
        }
        System.out.println("Tenant binding rejects swap: " +
                tenantSwapRejected);

        KeyPairGenerator keys = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = keys.generateKeyPair();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(payload);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pair.getPublic());
        verifier.update(payload);
        System.out.println("Signed message verifies: " +
                verifier.verify(signature));

        verifier.initVerify(pair.getPublic());
        verifier.update("amount=9999&currency=VND"
                .getBytes(StandardCharsets.UTF_8));
        System.out.println("Tampered message verifies: " +
                verifier.verify(signature));
    }
}
```

Biên dịch và chạy với **Java 17+**:

```bash
javac --release 17 BoundaryMechanismsDemo.java
java BoundaryMechanismsDemo
```

**Stdout đã kiểm chứng thực tế** bằng `javac --release 17` và `java`:

```text
Encrypted data recoverable: true
Tenant binding rejects swap: true
Signed message verifies: true
Tampered message verifies: false
```

Đọc từng kết quả theo mục tiêu bảo mật:

1. `Encrypted data recoverable: true`: AES-GCM dùng đúng key, nonce và AAD phục hồi plaintext trong JVM; ví dụ không tiết lộ key trong output.
2. `Tenant binding rejects swap: true`: đổi AAD từ alpha sang beta khiến kiểm tra authentication tag fail. **Giả định** hệ thống xác định AAD đúng từ tenant identity **đã được xác thực và cấp quyền**; nếu lấy AAD trực tiếp từ giá trị do attacker tùy ý gửi, biện pháp này không đủ kiểm soát phân quyền.
3. `Signed message verifies: true`: chữ ký Ed25519 có thể được kiểm bằng public key tương ứng. Trong chương trình, hai key sinh tại chỗ; việc một verifier thực tế **tin** public key nào phải do hệ thống danh tính/quản trị khóa quyết định.
4. `Tampered message verifies: false`: đổi bytes được verify khiến chữ ký thất bại. Chữ ký trên thông điệp cũ **vẫn có thể phát lại** nguyên vẹn; cần `messageId`, thời gian và dedupe hoặc một protocol đảm bảo freshness riêng.

**Giới hạn thiết kế để tự đánh giá thêm:**

- Mã tạo một nonce ngẫu nhiên **một lần** cho một lần encrypt. Trong ứng dụng thực, phải thiết kế giữ nonce duy nhất cho **mọi lần mã hóa dưới cùng key**, kể cả khi restart, chạy nhiều instance và có concurrency.
- AAD ở ví dụ là chuỗi ASCII cố định. Với dữ liệu bên ngoài, phải dùng encoding **không nhập nhằng**, version và quy tắc chuẩn hóa/length-prefix. Không tùy tiện ghép input người dùng bằng delimiter `|` nếu giá trị có thể chứa delimiter.
- Demo không lưu `keyVersion`, nonce hay ciphertext vào database và không dùng KMS; đó là phần thiết kế riêng trong câu 3/câu 9.
- Ed25519 được JDK 17 hỗ trợ trên nền tảng thông thường; HSM/provider bên ngoài có thể không hỗ trợ, vì vậy production phải kiểm tra capability của token và yêu cầu interoperability.
- Phép kiểm crypto không bảo vệ nguyên vẹn một request **trong toàn bộ vòng đời** nếu ứng dụng deserialize sai, dùng nhầm tenant hoặc cho phép signer sai. Cần bind metadata, kiểm tra quyền và validate nghiệp vụ ở các boundary.

### 18. Tự thay đổi ví dụ và dự đoán kết quả

Thử những thay đổi sau **mỗi lần một thay đổi** và giải thích bằng mục tiêu bảo mật, trước khi chạy:

| Thay đổi | Dự đoán | Nguyên nhân |
|---|---|---|
| Đổi một byte `ciphertext` trước decrypt | Ném `AEADBadTagException` (hoặc lỗi authentication tag tương đương tùy provider) | AES-GCM xác thực bản mã |
| Đổi một byte `aad` trong decrypt | Ném `AEADBadTagException` | AAD cũng thuộc dữ liệu được xác thực |
| Dùng khóa AES khác | Giải mã không thành công với xác suất áp đảo | Tag không khớp key |
| Đổi `payload` khi verify chữ ký | `verify(...) == false` | Chữ ký ràng buộc chính xác bytes |
| Giữ nguyên payload và chữ ký, gửi lần hai | `verify(...)` vẫn có thể `true` | Signature không tự chống replay |
| Copy public key mà không có bằng chứng identity | Verify mật mã có thể `true` nhưng chưa chứng minh chủ thể | Trust binding là kiểm tra bên ngoài primitive |
| Bỏ object authorization nhưng giữ đúng AAD | Tenant có thể vẫn bị truy cập theo đường ứng dụng hợp lệ | AEAD không quyết định ai được request decrypt |

Nếu mở rộng bài thực hành thành kiểm thử tự động, hãy dùng một **fixture có dữ liệu giả** và assertion cho cả thành công lẫn lỗi. Tuyệt đối không biến `AES/GCM/NoPadding` thành `AES/ECB/PKCS5Padding` để tránh exception: đó là thay đổi security property cốt lõi.

## V. Rubric tự chấm và bản thiết kế có thể review

### 19. Chấm 10 câu, tối đa 40 điểm

Mỗi câu 0–4 điểm:

| Điểm mỗi câu | Tiêu chí |
|---|---|
| **0** | Không có giải pháp hoặc chọn cơ chế sai mục tiêu nghiêm trọng |
| **1** | Nêu được tài sản/mục tiêu nhưng chưa xác định danh tính/ranh giới |
| **2** | Có cơ chế tương đối phù hợp nhưng thiếu key ownership, binding hoặc authentication/authorization |
| **3** | Có cơ chế, danh tính/ranh giới, key/certificate/secret management và lý giải lựa chọn |
| **4** | Đạt mức 3, thêm **test âm cụ thể**, replay/rotation/incident/operational policy cần thiết và cách xác minh kết quả |

Mức đọc điểm tham khảo: **34–40** có thể đem vào review thiết kế; **26–33** cần bổ sung các lớp đang thiếu; **dưới 26** nên làm lại từ threat model. Điểm cao không tự chứng minh hệ thống production an toàn; đây là thước đo **khả năng lập luận**.

**Những lỗi nghiêm trọng phải sửa bất kể tổng điểm:**

- Lưu mật khẩu dưới dạng plaintext, reversible AES hoặc SHA-256 nhanh đơn thuần.
- Tắt kiểm tra certificate/hostname để sửa lỗi TLS.
- Reuse AES-GCM nonce dưới cùng key hoặc bỏ qua tag khi decrypt.
- Chấp nhận JWT chỉ vì decode được, không verify signature/`iss`/`aud`/`exp`.
- Cho phép đọc/duyệt record chỉ vì user có session hợp lệ, không kiểm tra object-level authorization.
- Chọn HMAC/chữ ký nhưng bỏ chống replay trong workflow có tác động lặp.
- Cho rằng private key không xuất được nghĩa là mọi request ký đều được phép.
- Lưu private key, token, PIN, plaintext nhạy cảm hoặc JDK raw debug log trong log có quyền rộng.

### 20. Mẫu quyết định cuối cùng để mang tới security review

Với một tình huống bất kỳ, hãy viết bản quyết định theo cấu trúc tối thiểu sau (bảng **để người học tự điền**, không phải một hệ thống có thật):

| Mục | Nội dung cần giải trình |
|---|---|
| Asset và classification | Dữ liệu được bảo vệ; nơi xuất hiện: browser/RAM/network/DB/backup/log |
| Trust boundary | Nguồn → đích; bên nào do mình kiểm soát; bên nào không tin |
| Identity và authorization | User/workload/partner; bằng chứng danh tính; quyền trên object/action |
| Threats và security goals | Ai có thể đọc/sửa/giả/phát lại; mức thiệt hại |
| Mechanism và Java API | TLS/mTLS, AES-GCM/`Cipher`, HMAC/`Mac`, `Signature`, Argon2id library, token validation |
| Key lifecycle | KMS/HSM/secret manager, nonce, owner, version, rotation, revoke, backup và xóa |
| Data binding/freshness | AAD, signed bytes, canonicalization, timestamp, ID/dedupe |
| Happy-path test | Điều kiện và output kỳ vọng, không dùng dữ liệu thật |
| Negative test | Sai key/tenant/claim/signature/replay/certificate phải bị từ chối |
| Observability | TraceId, event/outcome, audit không lộ dữ liệu nhạy cảm |
| Recovery | KMS/CA/IdP/HSM lỗi; khóa bị lộ; migrate dữ liệu cũ và hành vi fail-closed |

Một lựa chọn hoàn chỉnh phải trả lời được câu hỏi phản biện: **“Nếu attacker kiểm soát đúng một thành phần ở phía bên kia ranh giới, còn ta vẫn làm đúng những gì đã mô tả, cơ chế này ngăn được điều gì và còn bỏ ngỏ điều gì?”** Nếu không trả lời được, cần quay lại mô hình đe dọa trước khi chọn thuật toán.

### 21. Câu hỏi tổng kết nhanh

1. **Mã hóa AES-GCM ở DB có thay authorization cho người dùng không?** Không. Nó bảo vệ bản mã trước đối thủ không có key, không thay kiểm soát quyền gọi decrypt hoặc đọc record.
2. **HMAC webhook đã đủ chống duplicate charge chưa?** Không. Phải có freshness, dedupe và nghiệp vụ idempotent.
3. **JWT có chữ ký bảo đảm claims bí mật không?** Không. Token ký thông thường có payload đọc được; cần tránh đặt secret/PII nhạy cảm vào claims hoặc thiết kế mã hóa phù hợp.
4. **mTLS service A → B có chứng minh người dùng U có quyền duyệt hóa đơn không?** Không. Nó xác thực workload/peer; B cần xác minh identity và quyền nghiệp vụ riêng.
5. **Đổi key version có cần re-encrypt ngay mọi file không?** Tùy loại key và threat model. Đổi KEK có thể rewrap DEK; đổi DEK bị lộ có thể buộc re-encrypt. Phải giữ khả năng đọc dữ liệu lịch sử theo policy.
6. **Chữ ký số xác minh được qua public key bất kỳ có chứng minh signer đáng tin không?** Không. Phải có trust binding tới identity, key ownership và policy.
7. **Tại sao một key “non-exportable” vẫn cần rate limit và kiểm tra người gọi?** Vì attacker có thể lạm dụng key bằng cách gọi API ký/giải mã mà không cần xuất bytes.
8. **Hash thông thường được dùng khi nào?** Khi cần digest/checksum hoặc làm thành phần của protocol có xác thực; không thay password hashing chậm, HMAC hay chữ ký số.
9. **Nonce AES-GCM cần bí mật không?** Không; cần **duy nhất cho mỗi khóa** và đi kèm ciphertext để decrypt.
10. **Điểm bắt đầu cho thiết kế bảo mật là gì?** Asset, identity, attacker và trust boundary; thuật toán được chọn **sau khi** đã xác định mục tiêu cần bảo vệ.

## Tài liệu chính thức và hướng dẫn thực hành

- Oracle Java 21, *Java Cryptography Architecture Reference Guide*: https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html — `Cipher`, `Signature`, `KeyStore`, `Mac`, AEAD và JCA providers.
- Oracle Java 21, *Java Secure Socket Extension Reference Guide*: https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html — TLS, certificates, trust/key managers và kết nối an toàn.
- OWASP, *Cryptographic Storage Cheat Sheet*: https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html — threat model, authenticated encryption và key management.
- OWASP, *Password Storage Cheat Sheet*: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html — Argon2id/scrypt/PBKDF2, salt và work factor.
- OWASP, *Transport Layer Security Cheat Sheet*: https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html — TLS/mTLS và cấu hình peer identity.
- OWASP, *REST Security Cheat Sheet*: https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html — JWT integrity/issuer/audience/expiry, API security và replay.
- NIST, *SP 800-57 Part 1 Rev. 5 — Recommendation for Key Management*: https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final — key purpose, protection, lifecycle, cryptoperiod và recovery.


</details>

- [Quay lại đầu trang](#back-to-top)
