<a id="back-to-top"></a>

# Thiết kế an toàn

## Menu
- [Giới hạn kích thước đầu vào và lượng bộ nhớ sử dụng](#safe-io-design-001)
- [Path traversal và ranh giới thư mục được phép](#safe-io-design-002)
- [Normalize không thay thế việc xử lý symbolic link](#safe-io-design-003)
- [TOCTOU: khoảng trống giữa kiểm tra và thao tác](#safe-io-design-004)
- [SecureDirectoryStream và khả năng hỗ trợ của provider](#safe-io-design-005)
- [Tạo tệp tạm có quyền phù hợp và dọn dẹp có kiểm soát](#safe-io-design-006)
- [Không ghi đè dữ liệu ngoài ý muốn khi mở hoặc copy](#safe-io-design-007)
- [Kiểm tra tính toàn vẹn bằng hash khi bài toán yêu cầu](#safe-io-design-008)
- [Tránh ghi dữ liệu nhạy cảm và đường dẫn nội bộ vào thông báo công khai](#safe-io-design-009)
- [Phân biệt quyền hệ điều hành với các cơ chế bảo mật Java lịch sử](#safe-io-design-010)

## <a id="safe-io-design-001">Giới hạn kích thước đầu vào và lượng bộ nhớ sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn kích thước đầu vào và lượng bộ nhớ sử dụng trong Java I/O

## 1. Vì sao giới hạn dữ liệu là một phần của thiết kế an toàn?

Ứng dụng Java thường đọc dữ liệu từ HTTP request, socket, file tải lên, file ZIP, log, message queue hoặc nguồn từ hệ thống khác. Nhiều ví dụ nhập môn chỉ đọc toàn bộ đầu vào:

```java
byte[] data = input.readAllBytes();
```

Cách này thuận tiện cho dữ liệu **nhỏ, đã được tin cậy và có giới hạn rõ ràng**, nhưng nguy hiểm nếu kích thước đầu vào do nguồn bên ngoài quyết định. Một client có thể gửi hàng gigabyte dữ liệu, một luồng có thể không bao giờ kết thúc, hoặc một file nén có thể rất nhỏ trên đĩa nhưng giải nén thành dữ liệu cực lớn. Khi đó chương trình có nguy cơ:

- Vượt giới hạn heap, phát sinh `OutOfMemoryError`.
- Tạo áp lực GC, tăng độ trễ cho các request khác.
- Chiếm CPU, I/O, ổ đĩa hoặc dung lượng mạng quá lâu.
- Làm một luồng bị kẹt vì đầu vào không kết thúc.
- Cho phép từ chối dịch vụ do tài nguyên bị tiêu thụ bất cân xứng (*resource exhaustion/DoS*).
- Để lại file tạm, output một phần hoặc trạng thái nghiệp vụ không nhất quán sau khi vượt giới hạn.

**Mục tiêu:** giới hạn **khối lượng dữ liệu thực tế được chấp nhận**, sử dụng **bộ đệm có kích thước kiểm soát được**, giới hạn số request đồng thời và hoàn tất hoặc từ chối đầu vào theo chính sách đã định.

Bài này tập trung vào các kỹ thuật trong **Java Core I/O và NIO**, dùng Java 17+ và không phụ thuộc framework web. Đây là kiến thức nền để triển khai chính sách upload trong Spring, servlet hoặc hệ thống streaming khác.

## 2. Phân biệt các giới hạn khác nhau

Một hệ thống an toàn thường cần nhiều loại giới hạn, bởi **một con số “tối đa 10 MB” không thể bảo vệ mọi tài nguyên**.

| Loại giới hạn | Tài nguyên kiểm soát | Ví dụ |
| --- | --- | --- |
| Kích thước đầu vào thô | Số byte nhận từ nguồn trước giải mã/giải nén | Tối đa byte upload |
| Kích thước sau giải nén | Số byte dữ liệu thật sau GZIP/ZIP | Ngăn file nén phình quá mức |
| Bộ nhớ cho một request | Array, buffer, parser object và state | Hạn mức bộ nhớ làm việc/request |
| Số phần tử | Số file ZIP, JSON nodes, records, headers | Tránh hàng triệu đối tượng nhỏ |
| Độ sâu cấu trúc | Nested JSON/XML/archive/object graph | Tránh recursion sâu và stack/resource exhaustion |
| Thời gian xử lý | Timeout cho đọc, parse và toàn request | Ngăn đầu vào chậm/không kết thúc |
| Số request đồng thời | Số tác vụ đang giữ tài nguyên | Tránh nhân nhiều giới hạn nhỏ thành tổng lớn |
| Dung lượng đĩa tạm | File spool và nội dung đang chờ xử lý | Tránh đầy filesystem |
| Kích thước output | Byte trả ra và dữ liệu tạm sinh ra | Tránh output không giới hạn |

Ví dụ: giới hạn mỗi request ở 8 MiB **không có nghĩa** 100 request đồng thời chỉ dùng 8 MiB. Nếu mỗi request giữ một `byte[]` 8 MiB, riêng những mảng ấy đã có thể chiếm khoảng 800 MiB, chưa tính buffer phụ, metadata, chuỗi, parser, heap khác và chi phí runtime. Nếu giải mã ra `String`, parse JSON và giữ bản sao trong cache, bộ nhớ đỉnh có thể lớn hơn nhiều.

Hạn mức phải gắn với **cách đọc, xử lý, giải nén, cấp phát và mức song song thực tế**. Một giới hạn kích thước chỉ có giá trị khi được kiểm tra **trước hoặc trong lúc tài nguyên tương ứng được tiêu thụ**.

## 3. Những API dễ đọc toàn bộ dữ liệu vào RAM

Các API sau hữu ích nhưng phải được dùng đúng trường hợp:

| API | Hành vi cần lưu ý |
| --- | --- |
| `InputStream.readAllBytes()` | Đọc mọi byte còn lại cho tới EOF và trả về `byte[]` |
| `Files.readAllBytes(path)` | Đọc cả nội dung file thành `byte[]` |
| `Files.readString(path)` | Đọc cả file thành `String` |
| `InputStream.transferTo(output)` | Chuyển tới EOF; có thể ghi lượng output không giới hạn nếu không có wrapper kiểm soát |
| `ByteArrayOutputStream` | Tích lũy byte trên heap; dung lượng tăng theo số byte đã ghi |
| `StringBuilder` | Giữ toàn bộ chuỗi đang xây, có thể tăng dần theo input |
| `BufferedReader.lines().toList()` | Có thể giữ tất cả dòng trong bộ nhớ |
| `ZipInputStream.readAllBytes()` trên entry | Có thể giải nén và giữ cả entry không giới hạn |
| `GZIPInputStream.readAllBytes()` | Có thể giải nén thành số byte lớn hơn file nguồn rất nhiều |

Javadoc Java 17 nói rõ `InputStream.readAllBytes()` dành cho các trường hợp đơn giản khi lượng dữ liệu phù hợp để lưu trữ; **không phải lựa chọn mặc định cho đầu vào lớn**. Nó có thể ném `OutOfMemoryError` nếu không thể cấp phát mảng cần thiết. `InputStream.transferTo()` không trả `byte[]` lớn, nhưng vẫn **đọc và ghi đến EOF**; nếu đích là `ByteArrayOutputStream` hoặc ổ đĩa không kiểm soát, tài nguyên vẫn có thể tăng không giới hạn.

**Đừng nhầm “streaming API” với “chính sách giới hạn đã được áp dụng”.** Một pipeline chỉ có vòng lặp streaming nhưng không giới hạn tổng byte, số record hoặc thời gian vẫn có thể tiêu thụ vô hạn tài nguyên ở tầng khác.

### `readNBytes(int)` có phải tự động an toàn không?

`input.readNBytes(n)` (Java 11+) đọc tối đa `n` byte và trả về mảng chứa số byte đọc thực tế. Đây có thể là công cụ phù hợp **nếu `n` đã được xác thực và bị giới hạn bởi chính sách**. Nhưng:

- Nếu truyền giá trị `n` khổng lồ do client cung cấp, vẫn có thể gây áp lực bộ nhớ.
- Nếu cần từ chối input **dài hơn `n`**, chỉ đọc `n` byte **không đủ** để kết luận đầu vào đã kết thúc; cần đọc thêm một byte để phát hiện phần dư, hoặc dùng giao thức quy định chính xác độ dài.
- Nếu không cần giữ toàn bộ nội dung trong RAM, mảng kết quả vẫn là chi phí bộ nhớ không cần thiết; dùng buffer cố định sẽ tốt hơn.

## 4. Không lấy `InputStream.available()` làm kích thước file

`available()` trả về **ước lượng số byte có thể đọc mà không chặn**, không phải tổng kích thước input và cũng không bảo đảm số byte còn lại cho tới EOF.

Cách sai:

```java
byte[] data = new byte[input.available()];
input.read(data); // Có thể đọc thiếu; kích thước mảng có thể sai.
```

Đoạn minh họa này có thể cho kết quả không đầy đủ và bỏ qua số byte thực tế trả về. Với network stream, `available()` có thể bằng 0 dù peer sẽ gửi dữ liệu sau đó. Với `ByteArrayInputStream`, nó có thể trông đúng vì implementation biết lượng byte còn lại, nhưng **không được suy rộng hợp đồng đó sang mọi InputStream**.

Muốn đọc tới EOF, cần vòng lặp dựa vào **giá trị trả về thực** của `read`. Muốn giới hạn tối đa, cần **đếm từng byte đã đọc và từ chối khi tổng vượt budget**.

## 5. Kiểm tra kích thước khai báo: cần nhưng không đủ

Một số nguồn biết trước kích thước hoặc công bố kích thước:

- `Files.size(path)` trả kích thước file tại thời điểm kiểm tra.
- HTTP `Content-Length` là số byte khai báo trong request header, nếu có.
- Frame nhị phân có trường độ dài 32-bit/64-bit.
- `ZipEntry.getSize()` trả kích thước dữ liệu chưa nén theo metadata nếu biết; có thể trả `-1` khi chưa biết.

Các giá trị này là tín hiệu để **từ chối sớm** hoặc chọn chiến lược xử lý, nhưng không thay thế việc giới hạn khi đọc:

1. **Metadata có thể vắng mặt hoặc không đáng tin**; client có thể gửi dữ liệu dài khác khai báo.
2. **File có thể thay đổi** giữa lúc kiểm tra `Files.size` và lúc đọc (*TOCTOU*).
3. **Giá trị âm, quá lớn, hoặc overflow** khi cộng/nhân có thể làm phép cấp phát sai.
4. **Kích thước nén không nói lên kích thước giải nén**; phải đếm byte sau giải nén.
5. **Nhiều thành phần** có thể cùng nằm dưới một container: phải giới hạn **cả từng phần và toàn bộ**.

Mẫu kiểm tra trước khi cấp phát:

```java
int length = input.readInt(); // Ví dụ frame có độ dài 4-byte.
if (length < 0 || length > MAX_FRAME_BYTES) {
    throw new IOException("Invalid frame length");
}
byte[] payload = input.readNBytes(length);
if (payload.length != length) {
    throw new EOFException("Truncated frame");
}
```

Đây là đoạn minh họa, giả sử `input` là `DataInputStream` và `MAX_FRAME_BYTES` là cấu hình **đã bị giới hạn một cách đáng tin**. Giá trị `length` phải được kiểm tra **trước** khi tạo mảng. Đừng làm `new byte[length]` rồi mới kiểm tra. Nếu payload lớn nhưng vẫn nằm trong policy, cân nhắc xử lý theo luồng thay vì giữ cả khối vào RAM.

## 6. Giới hạn đầu vào theo byte thực sự đọc

Một giải pháp nền tảng là **bounded streaming copy**: đọc bằng bộ đệm cố định, cộng dồn byte thực sự đã đọc, và không cho phép vượt hạn mức.

Giả sử chính sách cho phép **tối đa 10 byte**. Có ba tình huống:

| Input | Kết luận |
| --- | --- |
| Đúng 10 byte và sau đó EOF | Chấp nhận |
| 9 byte rồi EOF | Chấp nhận nếu giao thức cho phép ít hơn giới hạn |
| 11 byte | Từ chối vì vượt giới hạn |

Một triển khai đúng phải tránh lỗi **off-by-one**: nếu đọc được đúng 10 byte nhưng không kiểm tra EOF, nó chưa thể biết còn byte thứ 11 hay không. Đồng thời, muốn phát hiện vượt giới hạn mà không giữ nhiều dữ liệu, có thể **đọc thăm dò thêm đúng một byte** sau khi đã tiêu thụ 10 byte.

Cần xác định hợp đồng:

- `maxBytes` là giới hạn **bao gồm**: 10 byte vẫn được chấp nhận.
- `copied` là tổng số byte đã được chuyển đến output thành công.
- Chỉ đọc tối đa lượng byte còn trong budget.
- Khi đạt budget, đọc thêm **một byte** để phát hiện input dài hơn; không ghi byte thăm dò vào output.
- Nếu vượt budget, ném ngoại lệ và **coi output đã ghi trước đó là bản không hoàn chỉnh**, phải rollback/xóa hoặc không publish.
- Không dùng cách ép `(int) maxBytes` khi giá trị có thể lớn hơn `Integer.MAX_VALUE`; đếm tổng bằng `long` và giới hạn kích thước mỗi lượt đọc bằng buffer `int`.

## 7. Ví dụ Java 17 chạy được: `boundedCopy` không giữ toàn bộ input trong RAM

Chương trình dưới đây dùng mảng 8 KiB và không phân bổ bộ nhớ tỷ lệ với toàn bộ kích thước input. Nó chạy trên `ByteArrayInputStream` để kết quả có thể kiểm tra chính xác, nhưng thuật toán cũng có thể dùng với input là network/file stream. Khi input vượt hạn mức, output có thể đã nhận **phần tiền tố hợp lệ**: ví dụ thể hiện điều này để người học không nhầm ngoại lệ là rollback tự động.

**Tệp `BoundedCopyDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class BoundedCopyDemo {
    static long copyBounded(InputStream input, OutputStream output,
                            long maxBytes) throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes < 0");
        }

        byte[] buffer = new byte[8192];
        long copied = 0;

        while (copied < maxBytes) {
            int wanted = (int) Math.min(
                    (long) buffer.length, maxBytes - copied);
            int n = input.read(buffer, 0, wanted);

            if (n == -1) {
                return copied;
            }
            if (n == 0) {
                throw new IOException("Input made no progress");
            }

            output.write(buffer, 0, n);
            copied += n;
        }

        // Đã chạm budget: kiểm tra đúng một byte tiếp theo.
        if (input.read() != -1) {
            throw new IOException(
                    "Input exceeds " + maxBytes + " bytes");
        }
        return copied;
    }

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream accepted = new ByteArrayOutputStream();
        byte[] exactlyTen =
                "ABCDEFGHIJ".getBytes(StandardCharsets.US_ASCII);

        long size = copyBounded(
                new ByteArrayInputStream(exactlyTen),
                accepted, 10);

        System.out.println("Accepted bytes: " + size);
        System.out.println("Accepted data: "
                + accepted.toString(StandardCharsets.US_ASCII));

        ByteArrayOutputStream partial = new ByteArrayOutputStream();
        byte[] eleven =
                "ABCDEFGHIJK".getBytes(StandardCharsets.US_ASCII);

        try {
            copyBounded(new ByteArrayInputStream(eleven),
                    partial, 10);
            System.out.println("Unexpected acceptance");
        } catch (IOException ex) {
            System.out.println("Rejected: " + ex.getMessage());
            System.out.println(
                    "Partial output bytes: " + partial.size());
        }

        long empty = copyBounded(
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(), 0);
        System.out.println("Zero limit, empty input: " + empty);
    }
}
```

Lệnh biên dịch/chạy:

```bash
javac BoundedCopyDemo.java
java BoundedCopyDemo
```

**Stdout chính xác khi chương trình thành công:**

```text
Accepted bytes: 10
Accepted data: ABCDEFGHIJ
Rejected: Input exceeds 10 bytes
Partial output bytes: 10
Zero limit, empty input: 0
```

Phân tích:

1. Input đúng 10 byte: vòng lặp đọc 10 byte; lần `input.read()` thăm dò sau đó trả `-1` nên chấp nhận.
2. Input 11 byte: vòng lặp chỉ ghi 10 byte; lần thăm dò trả về byte thứ 11 nên ném `IOException`. Output đã giữ **10 byte đầu** — phải quản lý rollback.
3. Khi `maxBytes=0` và input rỗng, vòng lặp không chạy, lần thăm dò trả EOF nên trả `0`.
4. Dù input lớn bao nhiêu, **mảng làm việc vẫn chỉ 8 KiB**; biến đếm dùng `long`.
5. Đoạn kiểm tra `n==0` tránh vòng lặp không tiến triển do implementation vi phạm/ngoài dự kiến; theo hợp đồng chuẩn `InputStream.read(byte[], off, len)` với `len>0`, kết quả thông thường là số byte dương hoặc `-1`, không phải 0.

**Giới hạn của ví dụ:** bộ đệm đọc cố định không có nghĩa toàn bộ ứng dụng chỉ dùng 8 KiB: output ở đây là `ByteArrayOutputStream` nên vẫn giữ dữ liệu đã chấp nhận lên đến 10 byte. Khi input từ mạng và output là file tạm, phần tăng bộ nhớ có thể nhỏ hơn nhiều; muốn thực sự giới hạn RAM phải kiểm soát **cả nguồn, bộ đệm trung gian và đích**.

## 8. Output một phần: phải có bước commit/rollback

Nếu phương thức sao chép ghi thẳng vào tên file chính thức, khi input vượt budget nó có thể để lại file chứa phần đầu dữ liệu. Người dùng hoặc tiến trình khác có thể tưởng file ấy đã hoàn chỉnh.

Một chiến lược an toàn hơn:

```text
Nguồn không tin cậy
       |
       v
bounded copy (kiểm tra số byte thực)
       |
       v
tệp tạm thuộc quyền ứng dụng
       |
       +-- Thất bại/vượt limit --> đóng + xóa tệp tạm
       |
       +-- Thành công ---------> xác thực format/checksum
                                  |
                                  v
                             publish/rename
```

Quy trình đề xuất:

1. Tạo file tạm trong vị trí có hạn mức và quyền truy cập thích hợp.
2. Ghi vào file tạm trong lúc đọc có giới hạn.
3. Nếu kiểm tra giới hạn, nội dung và cấu trúc thành công thì mới công bố file cho người sử dụng.
4. Nếu có `IOException`, timeout, validation error hoặc request bị hủy, đóng các stream và xóa file tạm trong `finally`.
5. Nếu có cạnh tranh writer, cần chính sách tên, overwrite và đồng bộ hóa phù hợp.
6. `Files.move(..., ATOMIC_MOVE)` có thể được hỗ trợ tùy filesystem/provider; không giả định move atomically luôn khả dụng.

**Quy tắc:** “đã đọc không quá X byte” không đồng nghĩa “đã xử lý thành công”. Dữ liệu có thể thiếu định dạng, thiếu checksum, giải nén lỗi, hoặc bị ngắt ở giữa. Chỉ ghi trạng thái **COMPLETED** sau khi các điều kiện tương ứng thực sự thành công.

## 9. Ví dụ Java 17: frame có độ dài khai báo, kiểm tra trước khi cấp phát

Nhiều giao thức nhị phân dùng cấu trúc:

```text
[ length: 4-byte signed int ][ payload: length bytes ]
```

Nếu đọc độ dài do client gửi rồi lập tức tạo `new byte[length]`, client có thể khiến server xin mảng rất lớn hoặc dùng số âm. Phải xác thực **trước khi cấp phát**. Phương thức `readNBytes(length)` phù hợp khi `length` đã bị chặn bởi một hạn mức tương đối nhỏ, và phải kiểm tra dữ liệu có đủ đúng số byte hay không.

**Tệp `FrameLimitDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FrameLimitDemo {
    private static final int MAX_FRAME_BYTES = 8;

    static byte[] readFrame(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw new IOException("Rejected length: " + length);
        }

        byte[] payload = input.readNBytes(length);
        if (payload.length != length) {
            throw new EOFException("Truncated frame");
        }
        return payload;
    }

    static byte[] frame(int declaredLength, byte[] payload)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(declaredLength);
            output.write(payload);
        }
        return bytes.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        byte[] cat = "CAT".getBytes(StandardCharsets.US_ASCII);

        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(frame(cat.length, cat)))) {
            System.out.println("Accepted: " + new String(
                    readFrame(input), StandardCharsets.US_ASCII));
        }

        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(frame(1_000_000, new byte[0])))) {
            readFrame(input);
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }

        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(frame(5, cat)))) {
            readFrame(input);
        } catch (EOFException ex) {
            System.out.println("Rejected: " + ex.getMessage());
        }
    }
}
```

Lệnh chạy:

```bash
javac FrameLimitDemo.java
java FrameLimitDemo
```

**Stdout chính xác:**

```text
Accepted: CAT
Rejected length: 1000000
Rejected: Truncated frame
```

Các tình huống:

- Payload `CAT` dài 3 byte, khai báo 3: được chấp nhận.
- Khai báo 1.000.000 byte trong khi policy chỉ cho 8: bị từ chối **trước khi cấp phát mảng payload**.
- Khai báo 5 byte nhưng thực tế chỉ có 3: `readNBytes(5)` trả mảng 3 byte; ứng dụng ném `EOFException` vì frame bị thiếu.

**Đừng dùng `read(byte[])` một lần rồi mặc định đã đủ frame.** `InputStream.read` có thể trả ít byte hơn số được yêu cầu. `readNBytes(len)` hoặc một vòng lặp đọc đúng số lượng giúp kiểm soát thiếu byte theo hợp đồng.

Nếu giao thức dùng **unsigned 32-bit length**, cần chuyển sang `long` và kiểm tra khoảng hợp lệ mà **không làm tràn `int`** trước khi cấp phát. Hạn mức cấu hình phải là giá trị tin cậy từ server, không phải do client tự quyết.

## 10. Kiểu số và chống tràn khi tính kích thước

Một giới hạn đúng vẫn có thể bị vô hiệu hóa bởi số học sai.

### 10.1. Đừng cộng hai `int` lớn trước khi so sánh

Không nên:

```java
int total = currentBytes + nextBytes;
if (total > maxBytes) {
    throw new IOException("Too large");
}
```

Vì cộng hai `int` có thể **overflow** trước khi so sánh, biến `total` có thể âm hoặc nhỏ hơn dự kiến. Mã là minh họa với các biến tồn tại.

Nếu chỉ xử lý giá trị không âm và `currentBytes <= maxBytes`, có thể dùng phép trừ:

```java
if (nextBytes < 0 || nextBytes > maxBytes - currentBytes) {
    throw new IOException("Limit exceeded");
}
currentBytes += nextBytes;
```

Hoặc sử dụng `Math.addExact(...)` và bắt `ArithmeticException` khi cần cộng chính xác. Dùng `long` cho tổng byte, số record và offset lớn, nhưng vẫn phải ngăn tràn `long` nếu input/metadata không đáng tin.

### 10.2. Đừng ép xuống `int` không kiểm tra

```java
long declaredLength = input.readLong();
byte[] buffer = new byte[(int) declaredLength]; // Sai nếu long quá lớn.
```

Một `long` lớn khi ép sang `int` có thể bị cắt bớt bit hoặc thành số âm. Chỉ ép sau khi xác thực `0 <= length <= configuredMaximum` và `length <= Integer.MAX_VALUE`. Với file lớn, đọc theo từng khối hoặc lưu ra đĩa thay vì cấp mảng chứa toàn bộ.

### 10.3. Chỉ số phần tử và tổng byte phải cùng được kiểm tra

Ví dụ 10.000 phần tử, mỗi phần 10 KiB, có thể chiếm gần 100 MiB trước overhead dù một phần nhỏ. Nếu định dạng chứa nhiều header/record, cần giới hạn **cả số lượng và kích thước mỗi phần**.

## 11. File size và khả năng thay đổi trong lúc đọc

Với file nguồn:

```java
long announcedSize = Files.size(path);
if (announcedSize > MAX_BYTES) {
    throw new IOException("File too large");
}
```

Đây là bước *early rejection* tốt khi metadata có sẵn. Nhưng từ lúc kiểm tra đến lúc `Files.newInputStream(path)` hoặc `Files.readAllBytes(path)`, file có thể bị tiến trình khác thay đổi. Cũng có các file đặc biệt hoặc filesystem mà hành vi size không tương ứng một luồng dữ liệu ổn định.

**Cách chắc chắn hơn:** kiểm tra size để từ chối sớm, **vẫn** áp dụng bộ đếm giới hạn trong vòng đọc để cưỡng chế chính sách trên số byte thực nhận. Nếu yêu cầu nhất quán cao, phối hợp locking/snapshot/versioning, hoặc đọc từ handle có đặc tính phù hợp; không coi `Files.size` là giao dịch chặn writer khác.

Một file vừa vượt hạn mức có thể làm chương trình phát hiện ở lần đọc cuối; do đó chính sách tệp tạm, cleanup và rollback vẫn cần thiết kể cả sau precheck.

## 12. GZIP/ZIP: kiểm tra **dữ liệu sau giải nén**

Đối với dữ liệu nén, size của phần nén **không phải** ngân sách RAM hoặc output sau giải nén.

Ví dụ một luồng chứa nhiều byte lặp lại có thể nén thành file rất nhỏ nhưng giải nén thành lượng byte lớn. Nếu server chỉ kiểm tra tổng byte HTTP body trước khi tạo `GZIPInputStream` hoặc `ZipInputStream`, nó vẫn có nguy cơ bị **decompression bomb/zip bomb**.

Phải áp dụng giới hạn ở **phía sau bước giải nén**:

```text
Đầu vào nén
    |
    | giới hạn compressed bytes / thời gian / nguồn
    v
GZIPInputStream hoặc ZipInputStream
    |
    | giới hạn decompressed bytes thực đọc
    v
bounded parser / tệp tạm / output
    |
    v
validate rồi publish
```

Ngoài kích thước giải nén, nên giới hạn số entry ZIP, kích thước mỗi entry, tổng kích thước nhiều entry, độ sâu nested archive, số file giải nén và dung lượng đĩa. Ratio nén/giải nén có thể là một **tín hiệu bổ sung**, không thay cho tổng byte thực tế.

`ZipEntry.getSize()` có thể là `-1` hoặc không đáng tin ở thời điểm đọc, nên chỉ sử dụng để từ chối sớm khi phù hợp; **đếm byte `ZipInputStream.read(...)` thực tế** mới là lớp kiểm soát chính.

## 13. Ví dụ Java 17: giới hạn GZIP theo byte đã giải nén

Chương trình sau tạo một GZIP nhỏ trong bộ nhớ, sau đó giải nén bằng `GZIPInputStream`. Mọi byte đi ra từ decoder đều được tính vào giới hạn qua vòng đọc. Ví dụ dùng limit 10 byte: text `Java` hợp lệ, nhưng chuỗi 12 ký tự `X` vượt hạn mức và bị từ chối.

**Tệp `GzipLimitDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipLimitDemo {
    static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (GZIPOutputStream output = new GZIPOutputStream(sink)) {
            output.write(data);
        }
        return sink.toByteArray();
    }

    static String decodeBounded(byte[] compressed, int maxBytes)
            throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        try (GZIPInputStream input = new GZIPInputStream(
                new ByteArrayInputStream(compressed))) {

            byte[] buffer = new byte[4];
            int total = 0;
            int n;

            while ((n = input.read(buffer)) != -1) {
                if (n == 0) {
                    throw new IOException("No read progress");
                }
                if (n > maxBytes - total) {
                    throw new IOException("Decompressed limit exceeded");
                }
                total += n;
                result.write(buffer, 0, n);
            }
        }
        return result.toString(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        byte[] small = gzip(
                "Java".getBytes(StandardCharsets.UTF_8));
        System.out.println("Accepted: "
                + decodeBounded(small, 10));

        byte[] large = gzip(
                "XXXXXXXXXXXX".getBytes(StandardCharsets.US_ASCII));

        try {
            System.out.println(decodeBounded(large, 10));
        } catch (IOException ex) {
            System.out.println("Rejected: " + ex.getMessage());
        }
    }
}
```

Chạy:

```bash
javac GzipLimitDemo.java
java GzipLimitDemo
```

**Stdout khi thành công:**

```text
Accepted: Java
Rejected: Decompressed limit exceeded
```

**Nhận xét:**

- Chương trình đếm **byte giải nén**, không phải `compressed.length`.
- Trong `decodeBounded`, mảng làm việc chỉ 4 byte nhằm minh họa các lần đọc nhiều chunk.
- `ByteArrayOutputStream` tích lũy output nhưng bị giới hạn gián tiếp bởi chính sách 10 byte; với hạn mức lớn hơn, nên chuyển trực tiếp sang parser hoặc file tạm.
- `maxBytes` trong production phải được kiểm tra là số không âm và thuộc khoảng cấu hình được phép trước khi gọi; ở đây là hằng số đáng tin 10.
- Chỉ có giới hạn decompressed bytes thì **chưa đủ** nếu luồng nén không kết thúc hoặc decoder tiêu tốn nhiều CPU; phải bổ sung timeout và tổng kích thước input nén.

Không dùng `GZIPInputStream.readAllBytes()` trên nội dung nén từ nguồn không tin cậy rồi **mới** kiểm tra kích thước mảng đầu ra. Khi đến được bước kiểm tra, tài nguyên đã có thể bị tiêu thụ quá mức.

## 14. Kiểm soát ZIP theo số entry và tổng byte giải nén

ZIP có **nhiều entry**. Nếu chỉ giới hạn 1 MiB mỗi entry nhưng archive chứa hàng chục nghìn entry, tổng dữ liệu vẫn rất lớn. Vì vậy dùng các bộ đếm:

- `maxEntries`: số entry tối đa.
- `maxBytesPerEntry`: số byte giải nén tối đa của từng entry.
- `maxTotalExpandedBytes`: tổng byte giải nén tối đa của toàn archive.
- `maxDepth`: nếu cho phép ZIP lồng trong ZIP hay các dạng container lồng nhau.
- `maxOutputFiles` và tổng dung lượng disk khi giải nén ra file.

Mỗi lần đọc một chunk dài `n`:

```java
if (n > maxEntryBytes - entryBytes
        || n > maxTotalBytes - totalBytes) {
    throw new IOException("Expanded ZIP limit exceeded");
}

entryBytes += n;
totalBytes += n;
```

Đây là đoạn minh họa, giả định counters/budgets đã kiểm tra là không âm và counters chưa vượt budget. Dùng so sánh dựa trên **phép trừ** để tránh overflow. Không cần giữ toàn bộ entry trong RAM chỉ để biết có vượt limit hay không.

### Ví dụ Java 17: từ chối ZIP khi tổng byte vượt giới hạn

**Tệp `ZipExpansionLimitDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipExpansionLimitDemo {
    static byte[] createZip(String body) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(sink)) {
            output.putNextEntry(new ZipEntry("item.txt"));
            output.write(body.getBytes(StandardCharsets.US_ASCII));
            output.closeEntry();
        }
        return sink.toByteArray();
    }

    static int inspect(byte[] bytes, int maxEntries,
                       int maxPerEntry, int maxTotal)
            throws IOException {
        if (maxEntries < 0 || maxPerEntry < 0 || maxTotal < 0) {
            throw new IllegalArgumentException("Negative budget");
        }

        int entries = 0;
        int total = 0;

        try (ZipInputStream input = new ZipInputStream(
                new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[4];

            while ((entry = input.getNextEntry()) != null) {
                entries++;
                if (entries > maxEntries) {
                    throw new IOException("Entry count exceeded");
                }

                int entryBytes = 0;
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (n == 0) {
                        throw new IOException("No read progress");
                    }
                    if (n > maxPerEntry - entryBytes
                            || n > maxTotal - total) {
                        throw new IOException(
                                "Expanded ZIP limit exceeded");
                    }

                    entryBytes += n;
                    total += n;
                }
                input.closeEntry();
            }
        }

        return total;
    }

    public static void main(String[] args) throws IOException {
        byte[] small = createZip("CAT");
        System.out.println("Accepted bytes: "
                + inspect(small, 2, 10, 10));

        byte[] large = createZip("ABCDEFGHIJKL");
        try {
            inspect(large, 2, 10, 10);
        } catch (IOException ex) {
            System.out.println("Rejected: " + ex.getMessage());
        }
    }
}
```

Lệnh:

```bash
javac ZipExpansionLimitDemo.java
java ZipExpansionLimitDemo
```

**Stdout khi chương trình thành công:**

```text
Accepted bytes: 3
Rejected: Expanded ZIP limit exceeded
```

Điểm chính: chương trình đếm bytes **sau giải nén** và không dựa vào `ZipEntry.getSize()`. Nó cũng giới hạn entry count và từng entry riêng biệt. Ví dụ tạo ZIP trong RAM để dễ tái hiện; trong ứng dụng thật có thể đọc từ `Files.newInputStream` hoặc nguồn mạng đã bị giới hạn, và tránh giải nén tất cả entry vào một `ByteArrayOutputStream`.

**Chưa phải extractor production**: chương trình trên chỉ *kiểm tra và đếm*, không ghi file. Nếu phát triển thành extractor, phải chống **Zip Slip**, kiểm soát symlink, path traversal, quyền file, số lượng file, dung lượng đĩa, timeouts, nested archives và cleanup khi thất bại.

## 15. Byte limit, character limit và Unicode không giống nhau

Một văn bản có ít ký tự không nhất thiết có ít byte. Ví dụ UTF-8 mã hóa ASCII bằng 1 byte, nhưng nhiều ký tự có dấu dùng hơn 1 byte; emoji có thể dùng 4 byte. Java `String.length()` trả số **UTF-16 code units**, không phải số UTF-8 bytes và cũng không hẳn bằng số ký tự mà người dùng nhìn thấy (*grapheme clusters*).

Ví dụ minh họa:

```java
String text = "A😀B";

System.out.println(text.length()); // 4 UTF-16 code units
System.out.println(text.codePointCount(0, text.length())); // 3
System.out.println(text.getBytes(StandardCharsets.UTF_8).length); // 6
```

Với Java source mã hóa UTF-8, `A` và `B` là hai byte ASCII, emoji dùng bốn byte UTF-8. Tổng là **6 byte**, **3 Unicode code points**, nhưng `String.length()==4`.

**Nên đặt giới hạn ở đúng lớp:**

1. **Raw input byte limit**: trước khi decode, bảo vệ network/file/memory.
2. **Decoded text length limit**: sau khi decode, bảo vệ parser và logic text.
3. **Số dòng, số token, nesting depth**: bảo vệ cấu trúc dữ liệu.
4. **Output byte limit**: nếu serialize/encode response có thể phình thêm.

Đừng coi `String.length()` là giới hạn byte của HTTP request. Đừng tự động cắt `byte[]` theo limit nếu có thể chia đôi một UTF-8 sequence hoặc dữ liệu nhị phân cần tính toàn vẹn. Khi vượt giới hạn, **từ chối toàn bộ payload** hoặc dùng giao thức stream hỗ trợ phân đoạn rõ ràng.

### Charset decoder nghiêm ngặt

Nếu dữ liệu bắt buộc là UTF-8 hợp lệ, dùng decoder có `CodingErrorAction.REPORT` khi cần từ chối chuỗi byte không hợp lệ, thay vì âm thầm thay thế bằng ký tự khác. Có thể sử dụng `CharsetDecoder` hoặc API parser tương ứng, và kiểm soát byte budget trước khi decode. Với text streaming, giữ lại phần byte dở dang của ký tự giữa các chunk — không tự decode từng chunk độc lập bằng `new String(buffer, 0, n, UTF_8)` rồi ghép nếu chunk có thể kết thúc giữa một chuỗi UTF-8.

## 16. Bộ nhớ đỉnh có thể lớn hơn kích thước input

Đọc input 5 MiB vào `byte[]`, giải mã thành `String`, parse thành cây object và giữ lại bản sao trong cache có thể giữ **nhiều biểu diễn cùng lúc**. Hơn nữa:

- `byte[]` có object header và alignment.
- `ByteArrayOutputStream` có thể giữ internal array và `toByteArray()` tạo bản sao.
- `String` và decoder dùng biểu diễn/mảng riêng.
- JSON/XML parser có thể tạo nhiều object nhỏ, map, list, string, node metadata.
- Compression/decompression có buffer native hoặc nội bộ.
- Thư viện có thể cache hoặc giữ tham chiếu khiến object sống lâu hơn request.
- Direct `ByteBuffer` và memory mapping dùng tài nguyên ngoài heap, không hiện rõ chỉ bằng metric heap.

Do đó **giới hạn byte input không phải giới hạn heap tiêu thụ theo tỉ lệ 1:1**. Cần đo **peak retained + temporary allocations** với workload thật, tính margin cho GC/runtime và số request đồng thời.

### Ví dụ ngân sách ở mức thiết kế

Giả sử mỗi tác vụ giữ:
- tối đa 4 MiB input đã được chấp nhận trong một mảng;
- tối đa 4 MiB output trung gian;
- thêm overhead parser và runtime.

Nếu 50 tác vụ như vậy đồng thời tồn tại, chỉ hai mảng 4 MiB mỗi tác vụ đã có thể chiếm khoảng **400 MiB**, chưa tính overhead. Thay vì tối đa 50 request cùng parse trong heap, hệ thống có thể dùng semaphore giới hạn số tác vụ nặng hoặc stream dữ liệu sang file tạm.

Đây là con số minh họa cho kỹ thuật tính ngân sách, **không phải cấu hình khuyến nghị chung**. Chọn hạn mức theo heap, memory container, workload thực và SLO.

## 17. Giới hạn concurrency và backpressure

Một request 1 MiB có thể nhỏ, nhưng nếu cho phép 50.000 request giữ mỗi request một bản sao 1 MiB trong RAM, tổng bộ nhớ sẽ vượt xa giới hạn của hầu hết tiến trình ứng dụng.

Cần **hai lớp admission control**:

- **Per-request limit:** byte/record/depth/time của một request.
- **Global limit:** tổng request đang hoạt động, byte in-flight, worker tasks, kết nối đang chờ hoặc dung lượng hàng đợi.

Kỹ thuật phổ biến:

1. Dùng `Semaphore` để giới hạn số request cùng giữ bộ nhớ lớn hoặc cùng giải nén.
2. Dùng hàng đợi có độ dài cố định và từ chối sớm khi đầy.
3. Tạm ngừng đọc từ upstream khi downstream không xử lý kịp (*backpressure*).
4. Tách bộ nhớ làm việc nhỏ với file spool có quota đĩa và TTL cleanup.
5. Giải phóng tài nguyên bằng `try-with-resources`, hủy tác vụ khi timeout/cancellation và cập nhật metrics.
6. Dùng công cụ đo và cảnh báo khi tiến gần hạn mức; **không chờ `OutOfMemoryError` mới xử lý**.

Với Java NIO non-blocking, `SocketChannel.read(ByteBuffer)` có thể trả 0 khi chưa có dữ liệu; việc chặn số byte đã nhận vẫn cần đặt ở **state của từng kết nối**, không chỉ dựa vào kích thước buffer. `ByteBuffer.clear()` tái sử dụng buffer nhưng **không xóa tổng byte đã nhận**; ứng dụng phải duy trì counter riêng xuyên nhiều lần read.

## 18. Timeout và input không bao giờ kết thúc

Một giới hạn byte **không chặn được client gửi rất chậm** (ví dụ mỗi phút chỉ gửi vài byte) hoặc một nguồn treo trước EOF. Vì vậy cần chính sách thời gian:

- **Connect timeout**: tối đa chờ thiết lập kết nối.
- **Read idle timeout**: tối đa thời gian không nhận tiến độ.
- **Overall deadline**: tổng thời gian cho toàn yêu cầu, kể cả chậm nhưng vẫn có tiến độ.
- **Parsing/decompression budget**: giới hạn CPU/time sau khi nhận dữ liệu.
- **Cancellation**: khi người dùng hủy hoặc deadline hết, dừng luồng xử lý và cleanup.

Không dùng vòng lặp `while (read(...) == 0)` không điều kiện với channel non-blocking; đó là **busy spin**. Cần readiness/`Selector`, backoff phù hợp hoặc lịch xử lý theo sự kiện. Với `InputStream` blocking, việc cắt deadline có thể đòi hỏi timeout trên socket/transport hoặc việc đóng/hủy từ thread khác theo API cụ thể; `Thread.interrupt()` không có tác dụng giống nhau với mọi nguồn I/O.

**Giới hạn tổng số byte và timeout là hai trục khác nhau**, cần phối hợp.

## 19. EOF, read một phần và trách nhiệm parser

`InputStream.read(byte[])` trả về **số byte thực nhận**, có thể nhỏ hơn kích thước buffer; `-1` biểu thị EOF. Không được giả định một lần đọc đã lấy hết dữ liệu cần thiết.

Với khung thông điệp có kích thước xác định, EOF trước khi đủ byte là **truncated input**. Với upload không có kích thước xác định, EOF có thể là ranh giới kết thúc, nhưng ứng dụng vẫn phải kiểm tra tổng byte <= limit và kiểm tra định dạng hợp lệ.

`DataInputStream.readFully(byte[])` có thể hữu ích nếu đã cấp mảng có kích thước **đã được kiểm tra trước**; nếu EOF sớm, nó ném `EOFException`. Nhưng nó **không tự giới hạn input** nếu bạn cấp mảng theo độ dài không đáng tin.

Một parser tốt phải xác định rõ:
- “Đọc đủ” là bao nhiêu byte?
- Nếu thiếu, hành động từ chối là gì?
- Nếu thừa, có phải lỗi protocol hay phần mở đầu của frame kế tiếp?
- Nếu dữ liệu hợp lệ về kích thước nhưng sai checksum/schema, xử lý ra sao?
- Nếu dữ liệu đến thành nhiều chunk, state nào được giữ và khi nào cleanup?

Đối với streaming nhiều frame, **đừng đọc một byte thăm dò rồi đánh mất nó** nếu byte đó thuộc frame tiếp theo. Kỹ thuật thăm dò một byte của `BoundedCopyDemo` phù hợp cho **một input được kỳ vọng kết thúc tại EOF**. Với giao thức nhiều frame, dựa trên ranh giới đã khai báo và giữ trạng thái reader cho frame kế tiếp.

## 20. Giới hạn toàn pipeline, không chỉ một thành phần

Một luồng xử lý thực có thể đi qua:

```text
HTTP body / socket
      |
      v
transport request limit
      |
      v
decompression / decoding
      |
      v
expanded-byte and character limit
      |
      v
parser depth / item count limit
      |
      v
business validation
      |
      v
temporary file / output quota
      |
      v
commit / publish
```

Ví dụ HTTP body giới hạn 10 MiB nhưng cho phép một payload ZIP trong đó:

- 10 MiB chỉ kiểm soát **input nén**.
- Một ZIP 10 MiB có thể có **số entry rất lớn** hoặc giải nén thành hàng gigabyte.
- Parser sau giải nén có thể tạo cây object nhiều lần lớn hơn bytes gốc.
- Nhiều request cùng lúc có thể cộng dồn chi phí.

Một tầng kiểm soát thất bại không nên khiến mọi tầng sau hoàn toàn tin dữ liệu. **Defense in depth** là giới hạn bằng những bộ đếm độc lập, ở đúng điểm chuyển đổi dữ liệu.

## 21. Những lỗi thiết kế thường gặp

| Sai lầm | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Dùng `readAllBytes()` trên input không giới hạn | Có thể tiêu thụ heap lớn/OOME | Bounded stream và limit bytes |
| Dùng `InputStream.available()` như tổng kích thước | Phán đoán sai, đọc thiếu | Đếm byte trả về từ `read` |
| Tin tuyệt đối `Content-Length` | Client có thể khai báo sai/không khai báo | Check sớm và cưỡng chế khi đọc |
| `new byte[untrustedLength]` trước khi validate | Cấp phát lớn/âm/overflow | Kiểm tra độ dài trước |
| Dùng `readNBytes(max)` rồi kết luận input không quá max | Có thể còn byte phía sau | Probe EOF hoặc frame protocol |
| Dùng `transferTo(ByteArrayOutputStream)` không limit | Giữ toàn bộ data trên heap | Bounded copy hoặc bounded sink |
| Kiểm tra limit sau khi tích lũy hết bytes | Đã tiêu thụ tài nguyên quá mức | Kiểm tra ở mỗi chunk |
| Dùng `int` cho tổng byte lớn rồi cộng trực tiếp | Overflow làm bypass limit | Dùng `long` và phép so sánh dựa vào trừ |
| Chỉ giới hạn kích thước file ZIP/GZIP nén | Có thể giải nén thành nhiều GB | Giới hạn bytes sau giải nén |
| Chỉ giới hạn per-entry ZIP | Nhiều entry cộng dồn quá lớn | Giới hạn tổng và số entry |
| Chỉ giới hạn byte thô | Parser có thể tạo object graph rất lớn | Thêm item/depth/char limits |
| Một request tối đa nhỏ nhưng concurrency không giới hạn | Tổng heap vẫn tăng không kiểm soát | Admission control/semaphore/queue |
| Vượt limit nhưng giữ file output một phần | Có thể publish dữ liệu hỏng | Tệp tạm + commit/rollback |
| Chỉ có byte cap, không có deadline | Slow client chiếm tài nguyên lâu | Idle timeout và overall deadline |
| Cắt UTF-8 ở ranh giới byte và giải mã chunk riêng | Ký tự bị lỗi/trả replacement char | Stateful decoder và validation |
| Nghĩ `ByteBuffer.clear()` đặt lại byte budget | Có thể bypass limit qua nhiều lần read | Counter riêng cho request/connection |
| Bắt `OutOfMemoryError` rồi tiếp tục bình thường | Tiến trình có thể đang ở trạng thái suy kiệt | Cưỡng chế limit từ đầu, giám sát tài nguyên |
| Không đóng stream khi có lỗi | Rò rỉ file descriptor/temp files | `try-with-resources` và cleanup trong `finally` |

## 22. Bài tập tự kiểm tra

**Câu 1.** API nhận tối đa 1024 byte. `input.readNBytes(1024)` trả một mảng đúng 1024 byte. Có thể khẳng định input hợp lệ về kích thước không?

**Đáp án:** Chưa. Input có thể còn byte thứ 1025. Với payload phải kết thúc ở EOF, đọc thăm dò thêm một byte và từ chối nếu có. Với giao thức nhiều frame, không tiêu thụ byte của frame tiếp theo một cách tùy tiện; phải dùng framing đúng.

**Câu 2.** Một file ZIP có kích thước 1 MiB và chứa 100 entry, mỗi entry giải nén thành 2 MiB. Chỉ kiểm tra file ZIP <= 5 MiB có an toàn không?

**Đáp án:** Không. Tổng dữ liệu giải nén có thể khoảng 200 MiB. Cần giới hạn kích thước **nén, từng entry, tổng byte giải nén, số entry và tài nguyên xử lý**.

**Câu 3.** Có 100 request đồng thời, mỗi request giữ mảng input 4 MiB và output 4 MiB. Riêng hai mảng đó chiếm bao nhiêu?

**Đáp án:** Khoảng **800 MiB**, chưa tính overhead và các biểu diễn trung gian. Per-request cap không thay cho concurrency limit.

**Câu 4.** Tại sao `available()==0` không có nghĩa EOF?

**Đáp án:** `available` chỉ ước tính byte có thể đọc không chặn; một socket chưa nhận dữ liệu có thể trả 0 nhưng vẫn sẽ nhận thêm byte sau đó. EOF cần được xác định theo kết quả `read` và giao thức.

**Câu 5.** Nếu pipeline streaming dùng buffer 8 KiB rồi ghi vào file tạm không có quota, pipeline đã hoàn toàn an toàn về resource exhaustion chưa?

**Đáp án:** Chưa. Heap làm việc có thể nhỏ, nhưng file tạm có thể lớn không giới hạn, thời gian/CPU có thể không giới hạn, và nhiều request có thể chiếm hết đĩa. Cần byte limit, disk quota, timeout, concurrency cap và cleanup.

**Câu 6.** Vì sao phải kiểm tra `n > max - current` thay vì chỉ `current + n > max`?

**Đáp án:** Kiểm tra bằng trừ tránh overflow trong phép cộng, miễn `current` và `max` đã được xác thực và `current<=max`.

**Câu 7.** Một chuỗi Java có `text.length()==4` nhưng UTF-8 có 6 byte. Có mâu thuẫn không?

**Đáp án:** Không. `String.length()` đếm UTF-16 code units; UTF-8 là biểu diễn byte. Byte cap phải đo ở tầng byte, character cap phải đo ở tầng text theo quy ước mong muốn.

## 23. Checklist triển khai thực tế

Trước khi nhận input:

1. Định nghĩa đơn vị của từng giới hạn: **byte thô**, **byte đã giải nén**, **ký tự**, **số record**, **thời gian**, **bộ nhớ**, **đĩa**.
2. Đặt giá trị server-side đáng tin, không cho client tự nới limit.
3. Đặt giới hạn đồng thời của tác vụ cần cấp phát nhiều bộ nhớ.
4. Xác định nguồn nào có metadata đáng tin và cách từ chối sớm.

Trong khi đọc/giải mã:

1. Đọc theo **chunk có kích thước cố định**, không giữ cả input nếu không cần.
2. Cộng dồn **số byte thực nhận**, giới hạn trước khi ghi/chấp nhận thêm.
3. Kiểm tra độ dài khai báo trước `new byte[n]` hoặc `readNBytes(n)`.
4. Giới hạn thêm dữ liệu **sau giải nén** và số entry/record/depth.
5. Xử lý partial read, EOF sớm, idle timeout và tổng deadline.
6. Theo dõi request-level state qua mọi lần read; không đặt lại chỉ vì tái sử dụng buffer.

Khi hoàn tất hoặc thất bại:

1. Kiểm tra dữ liệu đã đủ và hợp lệ theo schema/giao thức.
2. Chỉ publish artifact sau khi mọi kiểm tra thành công.
3. Đóng stream/channel, xóa file tạm, giải phóng semaphore/quota.
4. Ghi metrics: bytes accepted/rejected, bộ nhớ đỉnh, temp disk, timeout, overflow, số request đồng thời.
5. Test ranh giới: **0**, **limit−1**, **limit**, **limit+1**, metadata âm/cực lớn, truncated input, compressed bomb, nhiều request đồng thời và nguồn đọc chậm.

## 24. Tổng kết

Giới hạn kích thước đầu vào là **hợp đồng tài nguyên**, không chỉ là kiểm tra một trường header. Trong Java I/O:

- **Đừng gọi `readAllBytes` tùy tiện trên nguồn không tin cậy**. Ưu tiên đọc theo chunk và đếm byte thực tế.
- **Đừng tin `available()` hoặc metadata** để suy ra tổng lượng dữ liệu; chúng chỉ có vai trò hỗ trợ.
- **Đừng cấp phát trước khi kiểm tra length**; xử lý overflow, độ dài âm và dữ liệu thiếu.
- **Kiểm tra đúng byte budget**, gồm trường hợp bằng limit và vượt limit đúng một byte.
- **Giới hạn sau giải nén** ZIP/GZIP, cùng số entry và tổng byte toàn archive.
- **Tính ngân sách đồng thời và peak memory**, không chỉ giới hạn từng request.
- **Xử lý lỗi theo transactional workflow**: output tạm → validate → publish; lỗi → cleanup.
- **Bổ sung deadline, disk quota và parser limits**, bởi mỗi tài nguyên có một con đường bị khai thác khác nhau.

Một thiết kế an toàn bảo đảm rằng **đầu vào vượt chính sách bị từ chối trước khi tài nguyên tương ứng tăng không kiểm soát**, và ngay cả khi có lỗi, ứng dụng vẫn biết cách giải phóng các tài nguyên đã sử dụng.

**Tài liệu Java SE 17:**

- [InputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html) — `read`, `readAllBytes`, `readNBytes`, `available`, `transferTo`.
- [Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html) — `size`, `newInputStream`, `readAllBytes`, `createTempFile`, `move`.
- [DataInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html) — `readInt` và `readFully`.
- [GZIPInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPInputStream.html) và [ZipInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipInputStream.html).
- [ByteArrayOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ByteArrayOutputStream.html), [ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html), [CharsetDecoder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-io-design-002">Path traversal và ranh giới thư mục được phép</a>

<details>
<summary>Xem chi tiết</summary>

# Path traversal và ranh giới thư mục được phép trong Java IO/NIO.2

Một ứng dụng thường cho người dùng tải ảnh, xem tài liệu, giải nén file hoặc đọc nhật ký thông qua tham số như `file=reports/2026.txt`. Nếu ứng dụng lấy chuỗi do người dùng gửi rồi ghép trực tiếp thành đường dẫn hệ thống, người dùng có thể khiến chương trình truy cập dữ liệu nằm **ngoài thư mục mà họ được phép sử dụng**. Lỗi này gọi là **path traversal** (directory traversal).

Mục tiêu của thiết kế an toàn là biến “chỉ đọc/ghi bên trong thư mục được cấp quyền” thành một **bất biến được kiểm tra trước thao tác IO**. Trong Java, điều đó đòi hỏi hiểu sự khác nhau giữa biểu diễn chuỗi, chuẩn hóa cú pháp, đường dẫn thực trên hệ thống file, symbolic link và quyền truy cập ở thời điểm mở file.

## 1. Path traversal là gì?

Giả sử dịch vụ cho phép tải tài liệu nằm dưới `/srv/app/uploads/alice/` và nhận tên tương đối:

```text
GET /download?name=invoices/2026.txt
```

Cách viết dễ lỗi:

```java
Path file = Path.of("/srv/app/uploads/alice").resolve(userInput);
byte[] content = Files.readAllBytes(file);
```

Đây là **đoạn mã minh họa chưa an toàn**, không phải chương trình độc lập. Nếu `userInput` chứa các thành phần `..`, hoặc là đường dẫn tuyệt đối, vị trí truy cập có thể nằm ngoài nơi được cấp quyền. Không thể tin chuỗi path chỉ vì giao diện thông thường chỉ hiển thị tên file.

Những nguồn đầu vào cần xem là **không tin cậy** gồm tham số HTTP, tên file upload (`Content-Disposition`), đường dẫn trong ZIP/TAR, chuỗi lấy từ hàng đợi, cấu hình do tenant tự sửa và dữ liệu đọc lại từ DB từng được lấy từ người dùng.

Khi lỗi xảy ra, hậu quả có thể gồm đọc file cấu hình/bí mật, ghi đè file của người khác, ghi tệp vào nơi ứng dụng thực thi, hoặc truy cập dữ liệu của tenant khác. Mức độ ảnh hưởng phụ thuộc **quyền tài khoản hệ điều hành đang chạy JVM** và thao tác thực hiện: đọc, tạo, sửa, xóa, liệt kê hay giải nén.

## 2. Ranh giới thư mục: kiểm tra theo thành phần Path

Ta cần phân biệt ba khái niệm:

- **Allowed root**: thư mục gốc đã được máy chủ quyết định, ví dụ thư mục riêng của một tenant. Không để client tự gửi allowed root. Quyền đọc/ghi nên được tách rõ theo nghiệp vụ.
- **User path**: tên/đường dẫn tương đối do client yêu cầu bên dưới root; phải được phân tích như dữ liệu và kiểm tra.
- **Candidate/real path**: đường dẫn sau khi kết hợp và kiểm tra vị trí thực tế. Chỉ sử dụng khi nó thuộc ranh giới cho phép.

Các API quan trọng trong `java.nio.file`:

| API | Có tác dụng | Không bảo đảm |
| --- | --- | --- |
| `Path.of(text)` | Phân tích chuỗi theo filesystem provider/OS | Không tự chặn traversal hay xác minh tồn tại |
| `path.isAbsolute()` | Nhận biết absolute path theo cú pháp nền tảng | Không xử lý hết các dạng path đáng ngờ như drive-relative trên Windows |
| `base.resolve(child)` | Ghép đường dẫn; nếu `child` là absolute, kết quả có thể bỏ qua một phần/toàn bộ `base` | Không kiểm tra quyền |
| `path.normalize()` | Loại bỏ `.` và rút gọn `name/..` trên chuỗi path | **Không** truy cập filesystem, không giải quyết symbolic link |
| `path.toAbsolutePath()` | Tạo biểu diễn absolute path | Không chứng minh nơi trỏ đến là hợp lệ |
| `path.toRealPath()` | Với path tồn tại: trả đường dẫn thực, theo liên kết biểu tượng theo mặc định | File chưa tồn tại sẽ không có real path; kết quả có thể lỗi thời nếu filesystem bị sửa sau khi kiểm tra |
| `candidate.startsWith(root)` | So khớp **các thành phần** của `Path` theo filesystem provider | Không tự giải quyết liên kết biểu tượng hoặc bảo vệ khỏi thay đổi đồng thời |
| `Files.isRegularFile(path)` | Kiểm tra file thường (theo symlink mặc định) | Không thay thế authorization hoặc thao tác mở file an toàn |

**Không dùng `String.startsWith` cho path.** Ví dụ chuỗi `/srv/app/files-backup/secret` bắt đầu bằng `/srv/app/files`, nhưng hai thư mục này là anh em, không phải quan hệ cha–con. `Path.startsWith` phân biệt các thành phần path; tuy nhiên chỉ nên so sánh những Path đã được chuẩn hóa cùng provider và ở dạng tương ứng (thường là absolute/real path).

Tương tự, việc thay `"../"` bằng chuỗi rỗng hoặc chỉ dùng regex chặn một mẫu là thiếu an toàn. Dấu phân cách `/`/`\`, đường dẫn tuyệt đối, dấu chấm, mã hóa URL, chữ hoa/thường và cú pháp đường dẫn phụ thuộc nền tảng. Hãy dùng `Path` để kiểm tra cấu trúc; áp dụng chính sách tên file đơn giản hơn khi có thể.

## 3. Quy trình kiểm tra khi đọc file đã tồn tại

Với một thư mục root được cấu hình và file **đã tồn tại**, quy trình cơ bản là:

1. Xác định allowed root từ phía server; dùng `toRealPath()` để neo root vào vị trí thực đang tồn tại.
2. Yêu cầu tên tương đối, từ chối tên rỗng và đường dẫn absolute. Với ứng dụng chỉ phục vụ file thông thường, nên từ chối luôn mọi thành phần `..` để làm rõ hợp đồng API.
3. Kết hợp `root.resolve(relative).normalize()` và kiểm tra candidate là **con** của root qua `startsWith`; có thể từ chối candidate bằng chính root nếu API chỉ chấp nhận file.
4. Gọi `candidate.toRealPath()` để theo symbolic links tới vị trí vật lý; kiểm tra **lần nữa** `real.startsWith(root)` nhằm ngăn liên kết dẫn ra ngoài.
5. Kiểm tra kiểu đối tượng (ví dụ file thường) và quyền nghiệp vụ; chỉ sau đó mới đọc file. Bắt lỗi IO khi file không tồn tại, quyền OS từ chối hoặc nội dung thay đổi.

Kiểm tra **cả lexical path lẫn real path** là có chủ đích: lexical check loại truy cập thoát root qua `..` hoặc đường dẫn tuyệt đối; real-path check nhận ra link vốn có tên nằm trong root nhưng dẫn đến file ở nơi khác.

## 4. Ví dụ Java 17+ chạy được: giới hạn quyền đọc file

Tạo file `SafeFileRead.java`:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SafeFileRead {
    public static String readInside(Path configuredRoot, String userInput)
            throws IOException {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("Empty file name");
        }

        // Root do server quản lý, phải tồn tại.
        Path root = configuredRoot.toRealPath();
        Path relative = Path.of(userInput);

        // Chỉ cho phép path tương đối và không có thành phần "..".
        if (relative.isAbsolute()) {
            throw new SecurityException("Absolute path is forbidden");
        }
        for (Path part : relative) {
            if (part.toString().equals("..")) {
                throw new SecurityException("Parent traversal is forbidden");
            }
        }

        // Kiểm tra cấu trúc đường dẫn, không dùng String.startsWith.
        Path candidate = root.resolve(relative).normalize();
        if (candidate.equals(root) || !candidate.startsWith(root)) {
            throw new SecurityException("Outside allowed directory");
        }

        // Theo symbolic links để kiểm tra nơi file thực sự trỏ tới.
        Path real = candidate.toRealPath();
        if (!real.startsWith(root) || !Files.isRegularFile(real)) {
            throw new SecurityException("Not an allowed regular file");
        }

        // Đọc nội dung UTF-8; với binary nên dùng Files.newInputStream.
        return Files.readString(real, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        Path sandbox = Files.createTempDirectory("path-demo-");
        Path root = Files.createDirectories(sandbox.resolve("allowed"));
        Path docs = Files.createDirectories(root.resolve("docs"));

        Files.writeString(
                docs.resolve("hello.txt"), "Xin chao",
                StandardCharsets.UTF_8);

        System.out.println("OK: " + readInside(root, "docs/hello.txt"));

        try {
            readInside(root, "../private.txt");
        } catch (SecurityException expected) {
            System.out.println("BLOCKED: ..");
        }

        try {
            readInside(root, docs.resolve("hello.txt").toString());
        } catch (SecurityException expected) {
            System.out.println("BLOCKED: absolute");
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa file:

```bash
javac -encoding UTF-8 SafeFileRead.java
java SafeFileRead
```

Stdout (thư mục tạm có tên ngẫu nhiên nhưng không được in):

```text
OK: Xin chao
BLOCKED: ..
BLOCKED: absolute
```

Ý nghĩa từng lớp kiểm tra:

- `"docs/hello.txt"` có đường dẫn tương đối nằm trong root, file tồn tại và là file thường.
- `"../private.txt"` bị chặn **trước khi mở file**, bất kể file đích có tồn tại hay không.
- `docs.resolve("hello.txt").toString()` là absolute path nên bị từ chối ngay cả khi trỏ **đến file hợp lệ**. Quy tắc của API là client chỉ được gửi tên tương đối.
- `toRealPath()` còn ngăn trường hợp `allowed/link.txt` là symbolic link trỏ tới file ngoài root (nếu kiểm tra và sử dụng cùng trạng thái filesystem).

**Giới hạn quan trọng của ví dụ:** Đây là phương pháp hợp lý khi cây thư mục dưới root **không thể bị đối thủ thay symbolic link hoặc parent directory đồng thời**. Vẫn có khoảng thời gian giữa lúc kiểm tra `toRealPath()` và lúc mở qua `Files.readString()` (*time-of-check to time-of-use*, TOCTOU). Nếu kẻ tấn công có quyền sửa cấu trúc thư mục vào thời điểm đó, họ có thể thay đường dẫn sau khi kiểm tra. Phần 6 trình bày cách nâng mức bảo vệ. Đừng xem ví dụ như lời bảo đảm chống mọi cuộc đua filesystem.

**Bài kiểm thử nên bổ sung:** tạo symbolic link **bên trong** allowed root trỏ ra file **bên ngoài**, rồi xác nhận ứng dụng từ chối. Quyền tạo link trên Windows phụ thuộc hệ thống/cấu hình nên không đưa vào stdout bắt buộc của ví dụ.

## 5. Đường dẫn chưa tồn tại: upload, tạo file và ghi dữ liệu

Với file mới, `candidate.toRealPath()` thường ném `NoSuchFileException` vì file chưa tồn tại. **Không nên bỏ qua mọi kiểm tra rồi ghi file mới**. Cần kiểm tra thư mục cha thực đã tồn tại, hạn chế tên, và tạo file không ghi đè.

Một thiết kế đơn giản cho upload: ánh xạ `uploadId` sang tên file do server sinh (ví dụ UUID), lưu tên gốc chỉ làm metadata hiển thị. Đoạn minh họa dưới đây là **một phần của phương thức xử lý upload**, giả định `configuredRoot` đã được cấp quyền riêng và `bytes` đã được kiểm tra kích thước:

```java
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

// Bên trong một phương thức throws IOException:
Path root = configuredRoot.toRealPath();
String storedName = UUID.randomUUID() + ".bin";
Path output = root.resolve(storedName).normalize();

if (!output.getParent().equals(root)) {
    throw new SecurityException("Invalid output parent");
}
try (OutputStream out = Files.newOutputStream(
        output, StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE)) {
    out.write(bytes);
}
```

`CREATE_NEW` tạo file mới một cách nguyên tử về điều kiện **chưa tồn tại tên file đích** và từ chối ghi đè mục đã tồn tại (kể cả symlink có sẵn ở tên cuối). Nó không thay thế kiểm tra quyền, hạn mức dung lượng, bảo vệ parent directories hay sandbox của hệ điều hành.

Nếu bắt buộc cho phép client chọn đường dẫn lồng nhau, hãy kiểm tra từng thành phần theo chính sách, từ chối absolute/parent traversal, ràng buộc với root bằng `Path.startsWith`, rồi xác minh parent directory thực bằng `toRealPath()` nằm trong root. Đảm bảo thư mục cha không thể bị kẻ tấn công đổi thành symbolic link sau khi kiểm tra. Khi tạo thư mục trung gian, `Files.createDirectories` không tự bảo đảm đường dẫn đã nằm trong boundary; phải kiểm soát cả chuỗi thao tác.

Với thao tác **ghi đè, xóa, đổi tên, move**, rủi ro còn lớn hơn: cần xác thực quyền đối với tài nguyên đích lẫn nguồn, xử lý symlink và thực hiện thao tác nguyên tử khi nền tảng hỗ trợ. Tránh những endpoint nhận trực tiếp absolute filesystem paths.

## 6. Symbolic link và TOCTOU: vì sao chỉ normalize chưa đủ?

Xét cấu trúc:

```text
/srv/app/allowed/
    reports/
    current -> /srv/app/private/
```

Ở đây `current` là symbolic link. Chuỗi `/srv/app/allowed/current/token.txt` trông có vẻ nằm dưới root về **mặt từ vựng** (lexical), nhưng sau khi hệ thống file giải liên kết, nó nằm trong `/srv/app/private/`. `normalize()` không hề kiểm tra symlink. `toRealPath()` mặc định theo link để tìm đích thực.

Một trường hợp khó hơn:

```text
T1: ứng dụng kiểm tra allowed/reports/file.txt là an toàn
T2: kẻ tấn công đổi reports thành symlink đi ra ngoài
T3: ứng dụng mở allowed/reports/file.txt
```

Đây là **TOCTOU**: đường dẫn đã được xác thực ở thời điểm T1 có thể không còn trỏ tới cùng tài nguyên ở T3. Lặp lại `toRealPath()` ngay trước `Files.readString()` chỉ thu hẹp cửa sổ race, **không loại bỏ hoàn toàn**.

Các mức bảo vệ thực tế:

1. **Phân quyền hệ điều hành:** không để đối tượng gửi request có quyền thay thư mục cha, symlink, junction hoặc mount bên dưới vùng mà tiến trình tin cậy đọc. Đây thường là biện pháp thiết yếu.
2. **Tách storage theo user/tenant và quyền tác vụ:** phân phối allowed root phía server; user A không thể dùng tên file để đọc tenant B dù cả hai cùng dưới một root rộng.
3. **Tránh theo symlink nếu không cần:** có thể dùng `LinkOption.NOFOLLOW_LINKS` trong các API hỗ trợ (ví dụ `Files.isRegularFile(path, NOFOLLOW_LINKS)`, `Files.newByteChannel`); kiểm soát **từng thành phần thư mục**, không chỉ tên cuối. Lưu ý dùng `toRealPath(NOFOLLOW_LINKS)` để thay cho việc kiểm tra đích thực là sai nếu sau đó vẫn mở đường dẫn theo link.
4. **Mở tương đối với directory handle:** `SecureDirectoryStream` của Java NIO.2 hỗ trợ thao tác tương đối với directory stream đã mở, giảm race khi filesystem provider hỗ trợ. `Files.newDirectoryStream` **không bảo đảm** sẽ trả `SecureDirectoryStream` trên mọi hệ điều hành/filesystem; cần kiểm tra `instanceof`, chính sách fallback và thao tác từng phân đoạn có `NOFOLLOW_LINKS` khi cần.
5. **Cô lập ở tầng OS** cho yêu cầu nghiêm ngặt: container/sandbox, quyền account ít nhất, mount namespace/chroot phù hợp hoặc API hệ điều hành hỗ trợ mở theo file descriptor. Java `Path` tự nó không tạo một security boundary toàn diện trước một cây filesystem mà attacker có thể thay đổi liên tục.

Không dựa vào Java `SecurityManager` để xử lý bài toán này trong ứng dụng hiện đại. Điều cần bảo vệ là quyền truy cập tài nguyên và độ ổn định của các thành phần path khi thao tác IO.

### Hard link, mount và junction

`toRealPath()` giúp với symbolic link; nó không chứng minh nội dung bên trong file là “vô hại”. Hard link có thể tạo thêm tên tới cùng một inode; mount point hoặc Windows junction/reparse point còn tùy provider. Vì vậy chính sách bảo vệ phải gồm quyền tạo link/mount, ownership và phân quyền OS. Một path hợp lệ nằm dưới root vẫn có thể bị từ chối theo quyền nghiệp vụ.

## 7. URL encoding, Windows path và tên file từ upload

Lỗi traversal không chỉ nằm ở chuỗi `../` nhìn thấy trực tiếp.

- **URL decoding:** Những chuỗi có mã hóa percent phải được giải mã nhất quán ở tầng HTTP/router. Tránh việc một lớp kiểm tra chuỗi **chưa decode** nhưng lớp khác decode tiếp trước khi mở file. Không tùy tiện decode hai lần; đặt quy tắc xử lý ở đúng một ranh giới.
- **Windows:** Ngoài dấu `/` còn có `\`, drive letter, UNC path (ví dụ `\\server\share`) và dạng drive-relative (ví dụ `C:folder`). `Path.isAbsolute()` không đủ để biến mọi chuỗi khác thành tên file an toàn. Nếu API chỉ cần file identifier, tốt hơn hết chỉ chấp nhận một tập ký tự và cú pháp đơn giản, độc lập OS.
- **Linux/macOS:** Backslash thường là ký tự tên file, không nhất thiết là separator. Bộ lọc chỉ hiểu separator của một OS có thể hoạt động khác khi triển khai lên OS khác.
- **Tên file upload:** Không dùng nguyên `getOriginalFilename()` hoặc tham số `filename` làm đường dẫn lưu. Có thể chứa path từ phía client. Tên hiển thị phải tách khỏi key vật lý do server sinh; áp dụng giới hạn độ dài/ký tự, tránh tên đặc biệt và collision.
- **Case và Unicode:** Khả năng phân biệt chữ hoa/thường và chuẩn hóa Unicode tùy filesystem. Đừng dùng so sánh chuỗi tùy tiện để xác nhận root hoặc quyền user.
- **Ký tự điều khiển và path lạ:** `Path.of` có thể ném `InvalidPathException` tùy platform. Bắt và trả lỗi yêu cầu không hợp lệ; không để lộ đường dẫn hệ thống trong HTTP error.

Với endpoint nhận một **ID tài liệu**, thiết kế tốt hơn là `GET /documents/{id}` → tra cứu ownership trong DB → lấy storage key do server quản lý. API không cần cung cấp thao tác filesystem path trực tiếp cho client.

## 8. Zip Slip: traversal trong tên entry của file nén

Đường dẫn trong ZIP cũng do nguồn không tin cậy cung cấp. Với `ZipEntry.getName()`, tên như `../outside.txt` hoặc absolute path có thể thoát thư mục extract nếu code ghép thẳng vào destination. Mẫu kiểm tra **trước khi tạo thư mục hoặc ghi entry**:

```java
// Đoạn nằm trong vòng lặp đọc ZipEntry, không phải chương trình độc lập.
Path root = destinationDirectory.toRealPath();
Path entryPath = Path.of(entry.getName());

if (entryPath.isAbsolute()) {
    throw new SecurityException("Absolute ZIP entry");
}
for (Path part : entryPath) {
    if (part.toString().equals("..")) {
        throw new SecurityException("Parent traversal in ZIP");
    }
}
Path target = root.resolve(entryPath).normalize();
if (!target.startsWith(root)) {
    throw new SecurityException("ZIP entry outside destination");
}
```

Đây mới là **phần kiểm tra tên**. Bộ giải nén hoàn chỉnh còn phải từ chối entry rỗng/đáng ngờ, kiểm soát thư mục cha và symlink, tránh ghi đè bằng `CREATE_NEW` nếu thích hợp, giới hạn số lượng entry/tổng dung lượng đã giải nén (zip bomb) và xử lý dữ liệu stream an toàn. Nếu tự giải nén qua filesystem có thư mục mà attacker đồng thời sửa được, cần biện pháp race-resistant ở phần 6.

## 9. Kiểm thử bảo vệ ranh giới

Đừng chỉ kiểm thử `"file.txt"` hợp lệ. Tạo bộ case có kết quả mong đợi:

| Đầu vào hoặc tình huống | Kỳ vọng |
| --- | --- |
| `docs/report.txt` tồn tại trong allowed root | Đọc được nếu user có quyền nghiệp vụ |
| `../secret.txt` | Từ chối traversal |
| `docs/../../secret.txt` | Từ chối traversal |
| Absolute path trỏ vào allowed root | Từ chối nếu API chỉ nhận relative path |
| Absolute path trỏ ra ngoài | Từ chối |
| `files-backup/x.txt` là thư mục anh em với root `files` | Không chấp nhận bằng so sánh prefix chuỗi |
| Symlink dưới root trỏ ra ngoài | Từ chối dựa trên đích thực hoặc chính sách NOFOLLOW |
| File trong root nhưng thuộc tenant khác | Từ chối theo authorization, dù containment hợp lệ |
| File không tồn tại hoặc thiếu quyền đọc | Xử lý lỗi rõ ràng; không trả nội dung ngoài root |
| Đổi symlink/thư mục cha đồng thời trong lúc mở | Không cho phép attacker tác động cấu trúc trusted directory; hoặc dùng cơ chế mở race-resistant |
| ZIP entry chứa parent traversal/absolute path | Từ chối trước khi tạo file |

Nên chạy các test tương thích trên nền tảng thực tế dùng triển khai (Linux/Windows/container), vì cú pháp path và hỗ trợ symbolic link khác nhau. Với test có symlink, đừng âm thầm biến việc thiếu quyền tạo symlink thành “test bảo mật PASS”; hãy báo rõ test bị bỏ qua và kiểm chứng trong môi trường có hỗ trợ.

Các lỗi thường gặp đáng kiểm tra khi review code: `new File(root, userInput)` không có kiểm tra containment; chỉ `normalize()`; `String.startsWith(root)`; chỉ check basename ở UI; cho client chọn root; dùng `toRealPath(NOFOLLOW_LINKS)` nhưng sau đó mở file theo link; và kiểm tra trước khi tạo file nhưng bỏ qua parent symlink/race.

## 10. Quy tắc thiết kế khi đưa vào production

1. **Mô hình quyền trước:** Xác định dữ liệu user nào được xem, thư mục nào app có quyền, tác vụ nào (read/write/delete) được cấp. Chọn allowed root từ server, theo đúng tenant và quyền tài nguyên.
2. **Giảm quyền biểu đạt của đầu vào:** Ưu tiên opaque file ID hoặc một tên file đơn; dùng path lồng nhau chỉ khi bài toán thực sự cần. Từ chối absolute path, parent traversal và các dạng path mơ hồ.
3. **Kiểm tra cấu trúc và vị trí thực:** Dùng `Path.resolve/normalize/startsWith` và `toRealPath` với file đã tồn tại; đối với file mới, kiểm tra parent và tạo bằng `CREATE_NEW` nếu không ghi đè.
4. **Bảo vệ TOCTOU:** Cấm attacker đổi cấu trúc trusted directory, hoặc dùng mở tương đối qua directory handle/`SecureDirectoryStream` khi phù hợp. Kết hợp quyền OS tối thiểu.
5. **Kiểm tra dữ liệu và khả năng vận hành:** Giới hạn kích thước file, thời gian và tổng dung lượng, xử lý exceptions, không tiết lộ absolute server paths trong response/log công khai.
6. **Dùng fixture/tình huống tấn công khi kiểm thử:** Kết hợp path traversal, symlink, tenant boundary, file không tồn tại, upload, xóa/ghi đè và entry trong archive.

Điều kiện cần nhớ là **`normalize()` giải quyết cú pháp đường dẫn; `toRealPath()` giải quyết đích thực tại một thời điểm; authorization và các biện pháp chống race mới giúp duy trì ranh giới quyền thực tế**. Không một hàm xử lý chuỗi đơn lẻ nào bảo đảm an toàn cho mọi thao tác filesystem.

**Tài liệu tham khảo chính thức:** Java SE 17 API `java.nio.file.Path` (`resolve`, `normalize`, `startsWith`, `toRealPath`), `java.nio.file.Files`, `java.nio.file.SecureDirectoryStream`, `java.nio.file.LinkOption` và `java.nio.file.StandardOpenOption`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-io-design-003">Normalize không thay thế việc xử lý symbolic link</a>

<details>
<summary>Xem chi tiết</summary>

# Normalize không thay thế việc xử lý symbolic link trong Java NIO

## 1. Vấn đề cốt lõi: đường dẫn nhìn an toàn chưa chắc trỏ tới tài nguyên an toàn

Khi ứng dụng Java nhận một tên tệp từ người dùng, API hoặc nội dung giải nén, ta thường muốn giới hạn mọi thao tác trong một thư mục tin cậy, ví dụ `/srv/app/uploads`. Một bước quen thuộc là dùng `Path.normalize()` để loại `.` và `..` dư thừa.

Nhưng **`Path.normalize()` chỉ là thao tác trên cấu trúc tên đường dẫn**. Nó **không mở filesystem, không xác minh tệp tồn tại, không kiểm tra symbolic link và không biết thực tế mỗi thành phần tên trỏ đến đâu**.

Một symbolic link (symlink) là một entry chứa tham chiếu tới đường dẫn khác. Nó có thể khiến đường dẫn **nằm bên trong thư mục cho phép về mặt cú pháp**, nhưng thao tác I/O thực tế lại truy cập dữ liệu **bên ngoài**.

~~~text
Filesystem:

/srv/app/uploads/
    public.txt
    latest -> /var/private
              (symbolic link)

/var/private/
    secret.txt

Đầu vào: latest/secret.txt
Kết quả normalize:
/srv/app/uploads/latest/secret.txt
            │
            └── VẪN có prefix /srv/app/uploads theo cú pháp

Nhưng khi hệ điều hành đi theo symlink:
latest → /var/private
           │
           └── /var/private/secret.txt   (ngoài uploads)
~~~

**Kết luận quan trọng:** `normalize()` giúp xử lý những đoạn đường dẫn như `a/../b`, nhưng **không thay thế chính sách xử lý symbolic link**, không đủ để chống mọi kiểu path traversal và không tạo ra một cơ chế sandbox filesystem.

Bài học này phân biệt lexical path và đường dẫn thực, giải thích `toRealPath()`, `NOFOLLOW_LINKS`, kiểm tra containment, race condition và hướng thiết kế an toàn trong Java 17+.

## 2. Ba thao tác Path rất dễ bị nhầm

### 2.1. `normalize()` — chuẩn hóa thuần cú pháp

~~~java
Path p = Path.of("docs", ".", "drafts", "..", "report.txt");
System.out.println(p.normalize());
~~~

Đầu ra thường là:

~~~text
docs/report.txt
~~~

`normalize()` loại các đoạn `.` và một số cặp `tên/..` theo quy tắc đường dẫn của provider. Nó **không** truy cập filesystem nên vẫn hoạt động với đường dẫn chưa tồn tại.

Lưu ý đặc biệt từ hợp đồng Java: nếu thành phần ngay trước `..` là symbolic link, việc bỏ cặp tên/`..` có thể **thay đổi tài nguyên được trỏ tới**, vì cách phân giải symlink của filesystem không tương đương thao tác xóa chuỗi.

Ví dụ mang tính khái niệm:

~~~text
dir/link -> /different/location

Đường dẫn gốc: dir/link/../file.txt
Sau normalize: dir/file.txt

Hai đường dẫn không nhất thiết trỏ cùng một file thực.
~~~

Do đó, không được mô tả `normalize()` là "resolve symlink" hoặc "canonicalize filesystem path".

### 2.2. `toAbsolutePath()` — lấy đường dẫn tuyệt đối

~~~java
Path absolute = Path.of("reports", "a.txt").toAbsolutePath();
~~~

Phương thức này chuyển đường dẫn tương đối thành đường dẫn tuyệt đối theo filesystem/provider, thường dựa trên working directory. Nó **không xác minh đích tồn tại và không mặc nhiên xử lý symbolic link**.

`toAbsolutePath().normalize()` vẫn là một đường dẫn được biến đổi chủ yếu theo cú pháp, **không phải bằng chứng đích I/O thực nằm bên trong thư mục cho phép**.

### 2.3. `toRealPath()` — tìm đường dẫn thực của mục đang tồn tại

~~~java
Path real = path.toRealPath();
~~~

Với provider filesystem mặc định và tùy chọn mặc định, `toRealPath()` trả đường dẫn tuyệt đối thực tới mục **đã tồn tại**, loại thành phần dư thừa và **đi theo symbolic link** để tìm đích. Nó có thể ném `IOException` khi mục không tồn tại, đường dẫn lỗi, quyền không đủ hoặc bị lỗi I/O.

Khác với `normalize()`, `toRealPath()` làm việc với trạng thái filesystem thực tế tại thời điểm gọi.

| API | Cần mục tồn tại? | Đi theo symlink? | Ý nghĩa |
| --- | --- | --- | --- |
| `normalize()` | Không | Không truy cập filesystem | Rút gọn cấu trúc tên đường dẫn |
| `toAbsolutePath()` | Không | Không tự xác minh symlink | Lấy đường dẫn tuyệt đối |
| `toAbsolutePath().normalize()` | Không | Không xác minh đích I/O | Absolute + lexical normalization |
| `toRealPath()` | Thường có | Có, theo mặc định | Xác định đường dẫn thực tại thời điểm kiểm tra |
| `toRealPath(NOFOLLOW_LINKS)` | Thường có | Không theo symlink theo chính sách option | Không thích hợp để chứng minh đích sau khi follow nằm trong root |

Từ "thường" để ghi nhận provider-specific semantics; khi thiết kế an toàn phải hiểu hợp đồng của filesystem đang sử dụng. Trên filesystem có symlink, khác biệt này đặc biệt quan trọng.

## 3. Một ví dụ lỗi: kiểm tra prefix sau normalize rồi tin là an toàn

Giả sử `trustedRoot` là thư mục ứng dụng cho phép đọc:

~~~java
Path trustedRoot = Path.of("/srv/app/uploads")
        .toAbsolutePath().normalize();

Path supplied = Path.of("latest/secret.txt");
Path candidate = trustedRoot.resolve(supplied).normalize();

if (!candidate.startsWith(trustedRoot)) {
    throw new SecurityException("Outside root");
}

// Không an toàn chỉ nhờ phép kiểm tra trên!
byte[] data = Files.readAllBytes(candidate);
~~~

Đoạn trên là **mảnh minh họa lỗi**, không phải chương trình chạy độc lập, và dấu `/` là ví dụ trên hệ thống dùng đường dẫn kiểu Unix.

Nếu `uploads/latest` là symlink tới `/var/private`, `candidate.startsWith(trustedRoot)` **vẫn đúng**, vì phép so sánh xét **các thành phần tên của Path**, không bắt buộc mở symlink và xác minh đích.

Phép kiểm tra trên bảo vệ một phần khỏi traversal **thuần chữ** như `../../etc/passwd` nhưng không bảo vệ khỏi **symlink traversal**.

### Vì sao phải dùng `Path.startsWith` chứ không dùng `String.startsWith`?

Đừng thay bằng:

~~~java
candidate.toString().startsWith(trustedRoot.toString());
~~~

Nếu root là `/srv/app/data` thì tên `/srv/app/database-secret` cũng bắt đầu bằng chuỗi `/srv/app/data`. `Path.startsWith(Path)` là so sánh **theo thành phần đường dẫn và quy tắc provider**, tránh lỗi prefix text đơn giản đó.

Nhưng ngay cả `Path.startsWith(Path)` cũng chỉ có ý nghĩa trên **những Path đang được so sánh**; nếu cả hai vẫn là lexical path thì nó không xác minh symlink.

## 4. Kiểm tra đích thực của file đã tồn tại

Nếu chính sách cho phép **theo symlink nhưng chỉ khi đích vẫn ở trong trusted root**, một bước kiểm tra hữu ích là:

~~~java
Path rootReal = trustedRoot.toRealPath();

Path userInput = Path.of(userProvidedName);
if (userInput.isAbsolute()) {
    throw new SecurityException("Absolute paths not allowed");
}

Path candidate = trustedRoot.resolve(userInput).normalize();
Path candidateReal = candidate.toRealPath();

if (!candidateReal.startsWith(rootReal)) {
    throw new SecurityException("Resolved path escapes root");
}

// candidateReal nằm trong rootReal tại thời điểm kiểm tra.
// Tuy nhiên vẫn phải xét race condition trước lúc mở file.
~~~

Đây là **mảnh mã minh họa**, với `trustedRoot` và `userProvidedName` đã được khai báo; cần xử lý `IOException` do đường dẫn hoặc filesystem.

Điểm chính:

1. `rootReal` có thể là đường dẫn tuyệt đối thực của thư mục gốc tin cậy.
2. `userInput.isAbsolute()` giúp loại tên tuyệt đối do người gọi cung cấp; tránh để logic `resolve` lệ thuộc ngữ nghĩa provider với path tuyệt đối.
3. `candidateReal` được xác định sau khi đi theo symlink trong đường dẫn ở thời điểm kiểm tra.
4. `candidateReal.startsWith(rootReal)` kiểm tra containment của **đường dẫn đã resolve**; symlink trỏ ra ngoài root sẽ bị phát hiện.
5. `toRealPath()` không dùng trực tiếp cho đích **chưa tồn tại**; phần tạo file mới cần chiến lược khác.
6. Kiểm tra này **chưa loại trừ Time-of-Check to Time-of-Use (TOCTOU)** nếu đường dẫn hoặc symlink thay đổi sau khi kiểm tra.

Đây là một **lớp phòng vệ**, không phải sự bảo đảm tuyệt đối rằng lời gọi `Files.newInputStream(candidateReal)` hoặc `Files.readAllBytes(candidate)` về sau sẽ luôn truy cập cùng tài nguyên.

### Chính sách phải rõ: cho phép hay từ chối symlink?

Hai chính sách hợp lệ nhưng khác nhau:

- **Cho phép symlink nội bộ:** chấp nhận `docs/current.txt -> docs/v2.txt` nếu đích thực vẫn nằm trong root; từ chối link trỏ ra ngoài.
- **Không cho phép symlink:** từ chối mọi symlink trên các thành phần mà ứng dụng dùng để truy cập tài nguyên, kể cả khi đích của chúng đang nằm trong root.

Chính sách thứ hai cần kiểm tra hoặc mở đường dẫn bằng những API có khả năng không follow symlink và chống thay đổi giữa các bước; chỉ gọi `normalize()` hoặc `toRealPath()` một lần là chưa đủ.

## 5. Ví dụ Java 17+ chạy được: lexical inside nhưng real outside

Chương trình tạo một thư mục tạm gồm một vùng tin cậy và một vùng khác cùng cấp. Trong vùng tin cậy có `public.txt`. Nó thử tạo symlink `redirect` từ vùng tin cậy tới vùng bên ngoài; nếu hệ thống hỗ trợ, chương trình chứng minh:

- Đường dẫn `trusted/redirect/secret.txt` **lexically startsWith(trusted)**.
- Đích thực của đường dẫn đó **không startsWith(trustedReal)**.
- Một đường dẫn `../outside/secret.txt` sau `normalize` bị loại bằng phép kiểm tra lexical.
- File `public.txt` thực sự nằm trong vùng tin cậy.

Một số môi trường Windows yêu cầu quyền đặc biệt để tạo symbolic link; mã xử lý trường hợp không có quyền/thư viện không hỗ trợ mà không giả định mọi hệ điều hành giống nhau.

Lưu thành `NormalizeVsSymlinkDemo.java`:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class NormalizeVsSymlinkDemo {

    public static void main(String[] args) throws IOException {
        Path workspace = Files.createTempDirectory("path-security-");
        Path trusted = workspace.resolve("trusted");
        Path outside = workspace.resolve("outside");
        Path publicFile = trusted.resolve("public.txt");
        Path secretFile = outside.resolve("secret.txt");
        Path link = trusted.resolve("redirect");

        try {
            Files.createDirectory(trusted);
            Files.createDirectory(outside);
            Files.writeString(publicFile, "public",
                    StandardCharsets.UTF_8);
            Files.writeString(secretFile, "private",
                    StandardCharsets.UTF_8);

            Path trustedReal = trusted.toRealPath();

            Path normal = trusted.resolve("public.txt");
            System.out.println("normal lexical inside="
                    + normal.toAbsolutePath().normalize()
                            .startsWith(trusted.toAbsolutePath().normalize()));
            System.out.println("normal real inside="
                    + normal.toRealPath().startsWith(trustedReal));

            Path traversal = trusted.resolve("../outside/secret.txt")
                    .normalize();
            System.out.println("dotdot lexical inside="
                    + traversal.startsWith(trusted.toAbsolutePath().normalize()));

            boolean linkCreated = false;
            try {
                Files.createSymbolicLink(link, outside.toAbsolutePath());
                linkCreated = true;
            } catch (UnsupportedOperationException
                    | IOException | SecurityException ex) {
                // Symlink không có sẵn hoặc không được phép.
            }

            if (!linkCreated) {
                System.out.println("symlink demo=unavailable");
            } else {
                System.out.println("symlink demo=available");
                Path redirected = trusted.resolve("redirect/secret.txt");
                System.out.println("symlink lexical inside="
                        + redirected.toAbsolutePath().normalize()
                                .startsWith(trusted.toAbsolutePath().normalize()));
                System.out.println("symlink real inside="
                        + redirected.toRealPath().startsWith(trustedReal));
            }
        } finally {
            // Xóa symlink như một entry, không xóa đích mà nó trỏ tới.
            Files.deleteIfExists(link);
            Files.deleteIfExists(publicFile);
            Files.deleteIfExists(secretFile);
            Files.deleteIfExists(trusted);
            Files.deleteIfExists(outside);
            Files.deleteIfExists(workspace);
        }
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac NormalizeVsSymlinkDemo.java
java NormalizeVsSymlinkDemo
~~~

Khi filesystem mặc định hỗ trợ tạo symbolic link trong thư mục tạm, stdout là:

~~~text
normal lexical inside=true
normal real inside=true
dotdot lexical inside=false
symlink demo=available
symlink lexical inside=true
symlink real inside=false
~~~

Nếu không thể tạo symlink, stdout thay thế hợp lệ:

~~~text
normal lexical inside=true
normal real inside=true
dotdot lexical inside=false
symlink demo=unavailable
~~~

Phân tích:

1. `trusted` và `outside` là hai thư mục anh em. `trustedReal` dùng làm gốc containment sau khi resolve thực tế.
2. `normal` là file bình thường trong root: cả kiểm tra theo cú pháp và theo đích thực đều trả `true`.
3. `traversal` sau `normalize()` trỏ sang thư mục `outside`, nên phép kiểm tra lexical `startsWith(trusted)` trả `false`.
4. `redirect` nếu được tạo thành công là symlink nằm **bên trong** thư mục `trusted` nhưng trỏ tới `outside`.
5. `redirected` có tên lexical bắt đầu bằng `trusted` nên dòng `symlink lexical inside` là `true`.
6. `redirected.toRealPath()` đi theo symlink và dẫn tới `outside/secret.txt`, ngoài trusted root, nên dòng `symlink real inside` là `false`.
7. Khối `finally` xóa symbolic link như một entry; xóa link không xóa file đích. Các file/thư mục thử nghiệm được dọn theo thứ tự từ con tới cha.

**Điểm cần nhớ:** ví dụ giúp quan sát khác biệt về mặt **phân giải đường dẫn**. Nó không tuyên bố phép `toRealPath()` cộng `startsWith()` tự tạo ra một sandbox chống mọi race condition hoặc attacker có quyền sửa filesystem.

## 6. Symbolic link có thể xuất hiện ở nhiều vị trí trong đường dẫn

Có ít nhất ba kiểu cần phân biệt:

### 6.1. Bản thân file đích là symlink

~~~text
trusted/report.txt -> /outside/report.txt
~~~

`Files.isSymbolicLink(trusted.resolve("report.txt"))` có thể phát hiện entry cuối cùng là symbolic link tại thời điểm gọi. Nhưng nếu một tiến trình khác có quyền thay entry đó, kết quả kiểm tra có thể lỗi thời trước lúc mở.

### 6.2. Thư mục cha ở giữa là symlink

~~~text
trusted/archives -> /outside
trusted/archives/2026.txt
~~~

`Files.isSymbolicLink(trusted.resolve("archives/2026.txt"))` chỉ kiểm tra entry cuối `2026.txt`, không tự nói rằng `archives` là symlink. Dù `2026.txt` là file thường, đường dẫn vẫn đi qua một symlink ở thành phần cha.

Chính sách cấm symlink phải xét **toàn bộ quá trình duyệt**, không chỉ tên cuối.

### 6.3. Symlink kết hợp với `..`

~~~text
trusted/jump -> /outside/folder
trusted/jump/../target.txt
~~~

Xóa `jump/..` theo cú pháp có thể làm tên đường dẫn chuyển thành `trusted/target.txt`, không nhất thiết tương đương cách hệ điều hành phân giải `jump` rồi đi lên thư mục cha của đích thực. Vì vậy, ngay cả phát biểu "normalize luôn giữ nguyên đích vật lý" cũng **sai** khi symbolic link tham gia.

### Broken symlink và symlink cycle

Một symbolic link có thể trỏ tới đường dẫn không tồn tại (broken) hoặc tạo vòng lặp khi đi theo nhiều link. `toRealPath()` có thể ném `IOException`/`FileSystemException` trong các tình huống đó. Đừng biến mọi ngoại lệ thành "đường dẫn an toàn" hoặc "file không tồn tại nên cho phép tạo"; cần xử lý lỗi một cách bảo thủ theo chính sách.

## 7. NOFOLLOW_LINKS dùng để làm gì và không làm được gì?

`java.nio.file.LinkOption.NOFOLLOW_LINKS` là lựa chọn để một số API thao tác với **entry symbolic link** thay vì đi theo link tới đích.

Ví dụ:

~~~java
boolean link = Files.isSymbolicLink(path);

boolean regularWithoutFollowing =
        Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);

Path lexicalLikeReal =
        path.toRealPath(LinkOption.NOFOLLOW_LINKS);
~~~

Đây là các mảnh minh họa có biến `Path path` đang tồn tại và import tương ứng.

Ý nghĩa:

- `Files.isSymbolicLink(path)` kiểm tra path entry cuối ở thời điểm gọi. Nó không là phép duyệt an toàn toàn bộ cây.
- `Files.isRegularFile(path, NOFOLLOW_LINKS)` thường không coi bản thân symbolic link cuối là regular file, kể cả khi đích của link là file thường.
- `toRealPath(NOFOLLOW_LINKS)` yêu cầu không đi theo symbolic link khi xác định đường dẫn theo ngữ nghĩa của provider; **không dùng kết quả này để chứng minh rằng việc mở một path theo mặc định sẽ không đi ra ngoài root**.
- Những API mở file có tùy chọn `NOFOLLOW_LINKS` phù hợp có thể từ chối symbolic link ở **entry cuối**. Nó không tự bảo đảm các thành phần thư mục cha không phải symbolic link hoặc không bị thay đổi.

Một ví dụ yêu cầu mở entry cuối mà không follow symlink:

~~~java
try (SeekableByteChannel channel = Files.newByteChannel(
        candidate,
        Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
    // Đọc các byte qua channel đã mở.
}
~~~

Mảnh code cần import `java.nio.channels.SeekableByteChannel`, `java.nio.file.*` và `java.util.Set`; biến `candidate` là một `Path`.

Đối với file cuối là symbolic link, lời gọi có thể bị từ chối theo chính sách/provider. **Nhưng parent path vẫn có thể chứa symbolic link**, nên không được khẳng định mảnh code này là đủ để cưỡng chế containment dưới một root có thể bị attacker chỉnh sửa.

Tóm tắt:

| Cách làm | Chặn traversal lexical kiểu `../`? | Nhận biết symlink? | Chống race khi path bị thay? |
| --- | --- | --- | --- |
| `normalize()` + so `startsWith` | Một phần, trên tên đường dẫn | Không | Không |
| `toRealPath()` rồi kiểm tra rootReal | Phát hiện đích thực ở thời điểm kiểm tra | Theo symlink mặc định | Không tự bảo đảm |
| `isSymbolicLink()` rồi `open()` | Không đầy đủ | Kiểm tra tại một thời điểm | Không |
| `NOFOLLOW_LINKS` khi mở entry cuối | Có thể từ chối link cuối | Có tác dụng ở vị trí API hỗ trợ | Không nhất thiết bảo vệ các ancestor |
| Directory-relative secure operations | Có thể giảm mạnh rủi ro thay đổi path giữa các bước | Có thể kiểm soát từng thành phần | Tốt hơn khi provider hỗ trợ và dùng đúng |

## 8. TOCTOU: kiểm tra đúng vẫn có thể mở sai file

TOCTOU là viết tắt của **Time of Check to Time of Use**: trạng thái filesystem có thể thay đổi giữa lúc kiểm tra và lúc sử dụng.

Ví dụ:

~~~text
Thread A / ứng dụng                     Process B / attacker

candidate.toRealPath()
→ /trusted/public.txt
                                        thay public.txt thành symlink
                                        → /outside/secret.txt

Files.readAllBytes(candidate)
→ hệ điều hành đi theo symlink mới
→ đọc file ngoài root
~~~

Ngay cả khi phép kiểm tra `toRealPath()`, `startsWith(rootReal)` vừa trả kết quả tốt, nó chỉ mô tả filesystem **tại một thời điểm**. Nó không khóa tên file hay entry khỏi việc bị đổi.

Mẫu dễ có race:

~~~java
if (!Files.isSymbolicLink(candidate)
        && candidate.toRealPath().startsWith(rootReal)) {
    return Files.readAllBytes(candidate);
}
~~~

Nếu một tiến trình khác có quyền thay directory entry, thời điểm mở thực tế có thể không còn giống thời điểm kiểm tra.

Không nên viết trong tài liệu bảo mật rằng "đã canonicalize nên chắc chắn không bị symlink attack". Đúng hơn: canonicalization là một phép kiểm tra hữu ích, **nhưng cần thêm cơ chế mở/duyệt không dựa vào một tên đường dẫn dễ bị thay thế**.

## 9. SecureDirectoryStream: thao tác tương đối với directory đã mở

Java NIO có giao diện `java.nio.file.SecureDirectoryStream`, dành cho provider có khả năng thao tác **tương đối với một directory stream đang mở**, giúp giảm rủi ro khi các entry trên filesystem bị thay thế đồng thời.

Một chiến lược:

1. Xác định và mở một thư mục gốc mà ứng dụng tin cậy và kiểm soát quyền sửa.
2. Nếu `DirectoryStream` trả về có hỗ trợ `SecureDirectoryStream`, thực hiện các thao tác tương đối với directory đã mở, thay vì liên tục resolve một chuỗi absolute path mới.
3. Với từng thư mục con cần vào, mở tiếp bằng `secure.newDirectoryStream(child, LinkOption.NOFOLLOW_LINKS)` khi chính sách cấm symlink.
4. Với file cuối, dùng `secure.newByteChannel(relativeName, options)` và yêu cầu `NOFOLLOW_LINKS` nếu thích hợp.
5. Truy cập dữ liệu qua **channel đã mở**, không lặp lại bước check rồi mở theo tên toàn cục.

Mảnh Java minh họa đọc một file con trực tiếp:

~~~java
Path baseReal = trustedRoot.toRealPath();

try (DirectoryStream<Path> opened = Files.newDirectoryStream(baseReal)) {
    if (!(opened instanceof SecureDirectoryStream<?>)) {
        throw new UnsupportedOperationException(
                "Provider does not support SecureDirectoryStream");
    }

    @SuppressWarnings("unchecked")
    SecureDirectoryStream<Path> secure =
            (SecureDirectoryStream<Path>) opened;

    Path childName = baseReal.getFileSystem().getPath("public.txt");

    try (SeekableByteChannel file = secure.newByteChannel(
            childName,
            Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
        // Thao tác đọc qua file channel đã mở.
    }
}
~~~

Đây là **mảnh minh họa**, không phải chương trình độc lập. Cần import `java.nio.file.*`, `java.nio.channels.SeekableByteChannel` và `java.util.Set`, cùng một `Path trustedRoot` đã được tin cậy.

**Không giả định `SecureDirectoryStream` luôn có sẵn** trên mọi provider/OS/filesystem. Nếu ứng dụng có yêu cầu bảo mật bắt buộc, khi provider không hỗ trợ nên **fail closed** hoặc sử dụng cơ chế cô lập mạnh hơn, thay vì âm thầm chuyển về mô hình check-then-open dễ bị race.

Ví dụ chỉ mở **một tên con trực tiếp**. Nếu đầu vào gồm nhiều component, phải duyệt từng thành phần directory bằng một `SecureDirectoryStream` mới, kiểm soát `.`/`..`, link và quyền, tuyệt đối không truyền đường dẫn không kiểm soát chứa `../` rồi kỳ vọng một handle cấp root tự tạo sandbox.

`SecureDirectoryStream` có lợi ích mạnh hơn check path thuần túy nhưng không thay thế thiết kế quyền, owner, tính đáng tin của root ban đầu và chính sách truy cập file. Nó cũng không tự giải quyết dữ liệu nhạy cảm trong file đã được mở nếu ứng dụng cho phép người dùng chọn sai file từ đầu.

## 10. Tạo file mới: toRealPath() không thể kiểm tra một đích chưa tồn tại

Một trường hợp phổ biến là upload file mới:

~~~text
trusted/
    users/
       new-image.png   (chưa tồn tại)
~~~

`target.toRealPath()` sẽ lỗi nếu `new-image.png` chưa tồn tại. Không nên thấy lỗi rồi tự cho phép tạo mà bỏ kiểm tra ancestor.

Với chính sách cho phép tạo **file mới** trong một thư mục có thể tin cậy, cần:

- Xác minh root và đường dẫn thư mục cha **đã tồn tại**.
- Xác định đích nằm trong cấu trúc được phép theo policy.
- Nếu root/subdirectory có thể bị attacker đổi, dùng thao tác directory-relative an toàn nếu provider hỗ trợ.
- Tạo bằng `CREATE_NEW` khi muốn thất bại nếu entry cuối đã tồn tại; tránh ghi đè file người khác.
- Dùng `NOFOLLOW_LINKS` hoặc cơ chế không follow entry cuối nếu API/provider hỗ trợ và chính sách yêu cầu.
- Nếu tên file đến từ user, từ chối path tuyệt đối, `..` và các thành phần đặc biệt theo policy; dùng tên basename được kiểm tra hoặc một ID server-side nếu có thể.
- Thiết lập quyền truy cập/ownership và vùng staging thích hợp. `Files.createDirectories()` không phải một cơ chế chống symlink race khi thư mục cha không đáng tin.

Ví dụ lưu upload bằng tên do server sinh thay vì dùng nguyên đường dẫn từ HTTP request thường đơn giản và an toàn hơn. Không cho client chọn thư mục con sâu nếu nghiệp vụ không cần.

### CREATE_NEW có tác dụng gì?

`StandardOpenOption.CREATE_NEW` yêu cầu tạo mới và thất bại nếu entry đích đã tồn tại; thao tác kiểm tra-tạo ở entry cuối được định nghĩa là atomic so với các thao tác tạo khác theo hợp đồng API. Điều này hữu ích để tránh vô tình overwrite một entry có sẵn, nhưng **không thay thế việc bảo vệ toàn bộ parent directory** nếu các ancestor có thể bị đổi.

## 11. Phòng vệ nhiều lớp và ranh giới tin cậy

Không có một câu lệnh `normalize()` nào thay thế được thiết kế bảo mật filesystem. Một hệ thống xử lý đường dẫn từ nguồn không tin cậy có thể áp dụng các lớp sau:

| Lớp phòng vệ | Vai trò |
| --- | --- |
| API chỉ nhận ID/tên file server sinh | Tránh cho user cung cấp path tùy ý |
| Kiểm tra path tuyệt đối và component `..` | Loại các dạng traversal trực tiếp |
| `Path.normalize()` và `Path.startsWith()` | Kiểm tra cấu trúc lexical cho đường dẫn được xây dựng |
| `toRealPath()` với root thật | Phát hiện symlink escape ở thời điểm kiểm tra đối với mục tồn tại |
| `NOFOLLOW_LINKS` và/hoặc `SecureDirectoryStream` | Hạn chế việc theo symlink và giảm check/use race |
| Quyền OS và thư mục do ứng dụng kiểm soát | Ngăn attacker thay link/entry trong vùng tin cậy |
| Tài khoản tiến trình tối thiểu quyền | Hạn chế thiệt hại nếu có lỗi logic |
| Sandboxing / container / mount policy | Tạo ranh giới hệ điều hành bổ sung |
| Logging và test symlink race | Kiểm chứng các tình huống bị lợi dụng |

Phải phân biệt **bảo đảm về đường dẫn** với **ủy quyền người dùng**. Ngay cả file nằm bên trong root đúng policy, không có nghĩa bất kỳ user nào cũng được đọc nó. Ứng dụng vẫn cần kiểm tra quyền truy cập đối với đối tượng/danh tính người dùng.

### Không dựa vào chuỗi và separator

`String.startsWith` không hiểu ranh giới component. Ghép chuỗi thủ công kiểu `root + "/" + filename` còn phụ thuộc separator, absolute path, Windows drive letter và provider. Dùng `Path`/`FileSystem` nhất quán; sau đó áp dụng chính sách containment theo path thực khi cần.

Nếu có nhiều filesystem/provider (`zipfs`, filesystem mạng...), so sánh path của provider khác nhau có thể không có ý nghĩa như path của cùng filesystem. Chỉ so sánh khi hiểu rõ `Path.getFileSystem()` và ngữ nghĩa của provider.

## 12. Các tình huống thường gây nhầm lẫn

### Tình huống A: kiểm tra lexical pass, nhưng symlink trỏ ra ngoài

~~~text
trusted/aliases -> /private
trusted/aliases/account.db
~~~

`normalize().startsWith(trusted)` có thể đúng, nhưng `toRealPath()` (mặc định follow) trỏ ra `/private/account.db`. Đây là ca bài học muốn nhấn mạnh.

### Tình huống B: symlink trỏ tới file khác nhưng vẫn bên trong root

~~~text
trusted/latest.txt -> trusted/v2.txt
~~~

Nếu chính sách **cho phép internal symlink**, có thể chấp nhận sau khi xác minh real containment. Nếu chính sách **cấm mọi symlink**, vẫn phải từ chối. Bảo mật không phải chỉ là “link trỏ ra ngoài hay không”; đó là policy cụ thể.

### Tình huống C: symlink ở ancestor

~~~text
trusted/data -> /private
trusted/data/info.txt
~~~

`Files.isSymbolicLink(trusted/data/info.txt)` không tự kiểm tra `data`. Một API `NOFOLLOW_LINKS` ở file cuối có thể vẫn đi qua link ở thư mục cha nếu không bảo vệ các bước duyệt.

### Tình huống D: đường dẫn đã xác minh bị tráo sau đó

~~~text
Check: trusted/config.txt là file thật.
Race: attacker thay nó bằng symlink.
Use: ứng dụng mở theo tên cũ.
~~~

`toRealPath()` và `isSymbolicLink()` kiểm tra tại thời điểm gọi, không cấp khóa bất biến cho tên file. Dùng handle-based access và quyền filesystem để giảm nguy cơ.

### Tình huống E: file đích chưa có

`toRealPath()` không thể bảo đảm containment của một file chưa tồn tại bằng cách gọi trực tiếp lên target. Hãy bảo vệ các thư mục cha, tạo entry mới với chính sách nhất quán và ưu tiên tên do server sinh.

### Tình huống F: theo dõi directory bằng Files.walk

`Files.walk(...)` theo mặc định không đi theo symbolic link như một directory khi không bật `FOLLOW_LINKS`. Nhưng việc quét cây không biến mọi thao tác sau đó thành an toàn; cây vẫn có thể thay đổi trong lúc hoặc sau khi đi qua, nhất là với thư mục ghi được bởi người khác.

## 13. Checklist thiết kế truy cập file an toàn

Khi viết API đọc hoặc upload từ tên người dùng:

1. **Xác định trusted root**: thư mục có chủ sở hữu và quyền sửa do ứng dụng kiểm soát.
2. **Định nghĩa symlink policy**: cấm hoàn toàn hay cho phép link có đích thực nội bộ?
3. **Giảm quyền chọn path**: ưu tiên ID/basename server sinh hơn relative path tùy ý.
4. **Kiểm tra path syntax**: path tuyệt đối, `..`, ký tự/đường dẫn đặc biệt theo provider.
5. **Dùng lexical normalize như một bước phụ**: hữu ích nhưng không coi là sandbox.
6. **Nếu mục đã tồn tại**, dùng `toRealPath()` để xác minh real containment theo policy khi phù hợp.
7. **Xử lý race**: không mặc định check-then-open là atomic; dùng `SecureDirectoryStream`/handle access hoặc isolation phù hợp.
8. **Với tạo file mới**, bảo vệ parent, dùng `CREATE_NEW` nếu không muốn overwrite, không tự follow link tùy ý.
9. **Không cho process quá nhiều quyền OS**: ngay cả khi lỗi traversal xảy ra, phạm vi truy cập vẫn nên bị giới hạn.
10. **Test cả negative cases**: `../`, absolute path, root prefix giống nhau, symlink ở file cuối, symlink ở ancestor, broken link, replace-during-open, permission denied.
11. **Quản lý fallback**: nếu filesystem thiếu primitive bảo mật cần thiết, trả lỗi thay vì hạ cấp im lặng.
12. **Đánh giá quyền ứng dụng**: file trong root chưa đủ để chứng minh caller có quyền xem nội dung.

## 14. Lỗi phổ biến và cách sửa

**Lỗi 1 — Đồng nhất normalize với canonicalize.** `normalize()` chỉ biến đổi cú pháp, không đi theo symbolic link. Dùng `toRealPath()` để biết đích thực **tại thời điểm kiểm tra**, nhưng không bỏ qua race.

**Lỗi 2 — Kiểm tra `startsWith(root)` trên lexical path rồi mở tùy ý.** Symlink nằm trong root có thể trỏ ra ngoài. Cần policy và cơ chế resolve/open an toàn.

**Lỗi 3 — Dùng `String.startsWith(rootString)`.** Prefix text không có ranh giới component, dễ chấp nhận `data-secret` khi root là `data`. Dùng `Path.startsWith(Path)` phù hợp.

**Lỗi 4 — Chỉ gọi isSymbolicLink trên file cuối.** Symlink có thể ở bất kỳ ancestor nào. Với policy cấm link, phải quản lý từng bước duyệt và hạn chế TOCTOU.

**Lỗi 5 — Nghĩ NOFOLLOW_LINKS ở final file cấm mọi symlink.** Nó không mặc nhiên ngăn ancestor symlink. Cần kiểm soát directory traversal tương đối an toàn.

**Lỗi 6 — Kiểm tra realPath rồi mở lại theo tên không được bảo vệ.** Attacker có thể tráo symlink giữa check và use. Ưu tiên thao tác dựa vào directory/file handle và quyền filesystem.

**Lỗi 7 — Coi mọi lỗi toRealPath là bằng chứng đích “chưa tồn tại nên an toàn”.** Lỗi có thể do broken symlink, permission hoặc vòng link. Không fail open.

**Lỗi 8 — Cho phép path tuyệt đối do user đưa vào.** `root.resolve(userInput)` với absolute path có thể dẫn đến vị trí không mong muốn. Từ chối input tuyệt đối và kiểm tra policy trước khi dùng.

**Lỗi 9 — Tin rằng file trong trusted root tự động được phép đọc.** Còn cần authorization cho từng người dùng và loại dữ liệu.

**Lỗi 10 — Phụ thuộc SecureDirectoryStream mà không kiểm tra support.** Không phải provider nào cũng hỗ trợ; ứng dụng có yêu cầu mạnh nên fail closed nếu thiếu primitive cần thiết.

## 15. Tự kiểm tra

1. `Path.normalize()` có tự đọc symbolic link không? **Không**. Nó xử lý thành phần tên theo cú pháp.
2. `toAbsolutePath().normalize()` có xác minh file tồn tại không? **Không**.
3. `toRealPath()` mặc định xử lý symbolic link ra sao? **Đi theo symlink** để tìm real path của mục đang tồn tại, theo ngữ nghĩa provider.
4. `candidate.normalize().startsWith(root.normalize())` đúng có bảo đảm open không thoát root không? **Không**.
5. Vì sao nên dùng `Path.startsWith` thay vì `String.startsWith`? Vì nó so **component đường dẫn**, không chỉ prefix text.
6. `Files.isSymbolicLink(candidate)` trả false có nghĩa mọi parent không phải symlink không? **Không**.
7. `NOFOLLOW_LINKS` với file cuối có tự chặn link ở parent không? **Không bảo đảm**.
8. `toRealPath()` trước `Files.readAllBytes()` có loại được race khi directory entry bị thay không? **Không hoàn toàn**; đó là TOCTOU.
9. Một file chưa tồn tại có thể gọi `toRealPath()` thành công để kiểm tra không? **Thông thường không**; cần chiến lược tạo file an toàn dựa trên parent.
10. `SecureDirectoryStream` có phải luôn được mọi filesystem cung cấp không? **Không**.
11. Symlink `trusted/latest -> trusted/v2` nên được phép hay không? **Tùy policy**: cho phép internal link hoặc cấm mọi symlink.
12. File nằm trong root đúng policy có tự chứng minh user có quyền xem không? **Không**; phải kiểm tra authorization.
13. Nếu phải bảo vệ dữ liệu nhạy cảm ngay cả khi logic path có lỗi, lớp bảo vệ ngoài Java nào hữu ích? **Quyền OS tối thiểu, root ownership, sandbox/container/mount policy** và cô lập dữ liệu.
14. Mô hình nào tốt hơn khi attacker có thể thay đổi thư mục giữa check và open? **Thao tác qua handle/directories an toàn với no-follow theo từng component**, kết hợp policy rõ ràng.

**Ghi nhớ:** `Path.normalize()` loại thành phần tên thừa nhưng **không biết symbolic link trỏ đi đâu**. Muốn kiểm tra đích đang tồn tại, hãy hiểu `toRealPath()` và containment trên các real path. Muốn chống việc path bị thay giữa kiểm tra và mở, cần cơ chế như `SecureDirectoryStream` nếu provider hỗ trợ, cùng với quyền filesystem và quản lý ownership đáng tin. **Không lấy một phép chuẩn hóa cú pháp làm ranh giới bảo mật của ứng dụng.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-io-design-004">TOCTOU: khoảng trống giữa kiểm tra và thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# TOCTOU: khoảng trống giữa kiểm tra và thao tác trong Java I/O

## 1. TOCTOU là gì và vì sao nguy hiểm?

**TOCTOU** viết tắt của **Time-of-Check to Time-of-Use**: ứng dụng kiểm tra một điều kiện ở thời điểm **T1**, nhưng chỉ thực hiện hành động dựa trên điều kiện đó tại **T2**. Trong khoảng T1 → T2, trạng thái được kiểm tra có thể đã thay đổi. Nếu ứng dụng mặc nhiên tin rằng kết quả kiểm tra vẫn còn đúng, nó có thể làm sai nghiệp vụ hoặc tạo lỗ hổng bảo mật.

Trong Java I/O, đối tượng hay gặp nhất là file và đường dẫn: ứng dụng kiểm tra một `Path` rồi mở, ghi, đổi tên hoặc xóa file tương ứng. Một tiến trình khác có thể thay file bằng symbolic link (symlink), đổi tên thư mục, tạo trước file hoặc điều chỉnh quyền giữa hai thao tác.

~~~text
Thời điểm        Luồng ứng dụng                 Tác nhân khác
T1               Kiểm tra: file chưa tồn tại
                 |                             Tạo file cùng tên
                 |                             hoặc thay symlink
T2               Ghi file theo tên đã kiểm tra
                 |
Kết quả          Thao tác có thể tác động lên đối tượng
                 khác với đối tượng ứng dụng dự định.
~~~

Có thể có race dù **không có attacker**: hai request đồng thời, hai worker, nhiều JVM, tác vụ dọn rác hoặc tiến trình ngoài đều có thể thay đổi trạng thái. Khi có đối thủ chủ động điều khiển thư mục/đường dẫn, đây còn là một dạng tấn công **race condition**.

**Phân biệt ba yếu tố:**

- **Check:** một lần hỏi hoặc kiểm tra trạng thái, chẳng hạn `Files.exists(path)`, `Files.isReadable(path)`, `Files.isRegularFile(path)`, `Files.notExists(path)`, `Files.readAttributes(path, ...)` hoặc bước kiểm tra tên file.
- **Gap:** khoảng thời gian giữa check và thao tác tiếp theo; dù rất ngắn vẫn tồn tại và thread scheduling có thể đổi thứ tự thực thi.
- **Use:** thao tác tạo, mở, đọc, ghi, di chuyển, thay thế, xóa hoặc chỉnh metadata của file.

Không phải mọi chuỗi có hai thao tác I/O đều là lỗ hổng. Vấn đề xuất hiện khi tính đúng đắn hoặc bảo mật **phụ thuộc vào giả định trạng thái ở T1 vẫn đúng ở T2**.

## 2. Cách TOCTOU xuất hiện trên filesystem

Giả sử chương trình nhận tên file từ client và chỉ muốn tạo file mới khi chưa có file đó:

~~~java
Path target = uploadDir.resolve(requestedName);
if (Files.notExists(target)) {
    Files.writeString(target, content);
}
~~~

Đây là **đoạn minh họa lỗi**, không phải chương trình độc lập. Phần nguy hiểm nằm ở:

1. `Files.notExists(target)` chỉ phản ánh trạng thái được quan sát **ngay lúc gọi**. Nó không đặt khóa và không “đặt chỗ” tên file.
2. Tác nhân khác có thể tạo `target` sau khi phương thức trả `true`.
3. Lệnh `Files.writeString(target, content)` mặc định mở file với hành vi tương đương `CREATE`, `TRUNCATE_EXISTING` và `WRITE`. Vì vậy file vừa được tạo có thể bị **ghi đè/truncate**, thay vì được bảo vệ bằng điều kiện “chỉ tạo nếu chưa có”.
4. Nếu một phần của đường dẫn là symlink mà bên khác có thể đổi, thao tác có thể bị hướng đến một file khác dự kiến.

`Files.exists` / `notExists` còn có chi tiết quan trọng: khi không xác định được trạng thái, **cả hai có thể trả `false`**. Kết quả không tồn tại không tự mang ý nghĩa chứng minh chắc chắn rằng có thể tạo file; không được dùng hai hàm này làm cơ chế đồng bộ hay kiểm soát quyền.

### 2.1. Các tình huống điển hình

| Kiểm tra trước | Thao tác sau | Rủi ro khi trạng thái đổi giữa hai bước |
| --- | --- | --- |
| `if (!Files.exists(p))` | `Files.writeString(p, ...)` | File xuất hiện, dữ liệu của bên khác bị ghi đè |
| `Files.isRegularFile(p)` | `Files.newInputStream(p)` | File bị thay hoặc symlink đổi mục tiêu, ứng dụng đọc file khác |
| `Files.isWritable(p)` | `Files.newOutputStream(p)` | Quyền/trạng thái thay đổi; mở file thất bại hoặc trúng đối tượng khác |
| Kiểm tra owner/permission/path | `Files.delete(p)` | Xóa một entry khác với entry đã kiểm tra |
| `Files.size(p) <= limit` | `Files.readAllBytes(p)` | File tăng kích thước, tiêu thụ nhiều bộ nhớ hơn dự kiến |
| Kiểm tra thư mục upload có tồn tại | Tạo file con trong thư mục | Thư mục có thể bị thay bằng symlink hoặc đổi sang nơi khác |

Ví dụ kiểm tra `Files.size(p)` rồi dùng `readAllBytes(p)` **không** tạo hạn mức an toàn. Nếu kích thước tối đa là bất biến bảo mật, hãy giới hạn số byte **trong lúc đọc** và từ chối khi vượt hạn mức.

### 2.2. Mối đe dọa thực tế phụ thuộc quyền của tác nhân khác

Một attacker chỉ có thể tận dụng race khi họ có cách tác động trạng thái có liên quan: chẳng hạn quyền thay entry trong thư mục chứa file upload, quyền chỉnh symlink, hoặc khả năng gửi các request cạnh tranh. Trong thư mục chỉ chương trình đáng tin cậy có quyền sửa, rủi ro bị lợi dụng thường thấp hơn, nhưng vẫn có thể gặp lỗi do tiến trình nội bộ.

Vì thế trước khi sửa lỗi phải xác định **ai sở hữu thư mục, ai có quyền đổi các entry, ai quyết định tên file và thao tác nào phải an toàn**. Không nên chỉ đổ lỗi cho JVM hay độ trễ của hệ điều hành.

## 3. Ví dụ Java 17+: tái hiện sai sót check → write

Ví dụ sau **mô phỏng chính xác thứ tự interleaving**, nên kết quả ổn định mà không phải hy vọng hai thread đua đúng thời điểm. Nó tạo toàn bộ file trong một thư mục tạm riêng.

Tạo file `ToctouCreateDemo.java`:

~~~java
import java.nio.file.*;

import static java.nio.file.StandardOpenOption.*;

public class ToctouCreateDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("toctou-demo-");
        Path unsafe = dir.resolve("unsafe.txt");
        Path safe = dir.resolve("safe.txt");

        try {
            // T1: Ứng dụng quan sát file chưa tồn tại.
            boolean absent = Files.notExists(unsafe);

            if (absent) {
                // Mô phỏng một tiến trình khác tạo file giữa T1 và T2.
                Files.writeString(unsafe, "other", CREATE_NEW, WRITE);

                // T2: Ứng dụng làm theo kết quả kiểm tra cũ.
                Files.writeString(unsafe, "app",
                        CREATE, TRUNCATE_EXISTING, WRITE);
            }

            System.out.println("unsafe=" + Files.readString(unsafe));

            // File đã có trước: CREATE_NEW từ chối ghi đè.
            Files.writeString(safe, "other", CREATE_NEW, WRITE);
            try {
                Files.writeString(safe, "app", CREATE_NEW, WRITE);
            } catch (FileAlreadyExistsException expected) {
                System.out.println("safe=rejected");
            }

            System.out.println("safe-content=" + Files.readString(safe));
        } finally {
            Files.deleteIfExists(unsafe);
            Files.deleteIfExists(safe);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Chạy:

~~~bash
javac ToctouCreateDemo.java
java ToctouCreateDemo
~~~

Stdout:

~~~text
unsafe=app
safe=rejected
safe-content=other
~~~

**Diễn giải:** Ở nhánh đầu, kết quả `notExists` từng đúng, nhưng đến lúc `writeString` chạy, file đã tồn tại. Các option `CREATE` và `TRUNCATE_EXISTING` khiến ứng dụng ghi đè chuỗi `other` thành `app`.

Ở nhánh sau, `CREATE_NEW` làm điều kiện “file chưa tồn tại” và việc tạo file thành **một thao tác atomic đối với việc tạo entry cùng tên** theo hợp đồng `Files`. Khi entry đã có, nó báo `FileAlreadyExistsException` (trên provider minh họa), giữ nguyên nội dung. Không cần gọi `Files.notExists` trước.

**Giới hạn của `CREATE_NEW`:** tính atomic áp dụng cho thao tác tạo entry tại đường dẫn mà filesystem đã phân giải. Nếu **thư mục cha hay các thành phần tổ tiên** do attacker thay đổi được, bản thân `CREATE_NEW` không phải giải pháp đầy đủ chống chuyển hướng qua symlink ở các thành phần đó. Đồng thời “không bị ghi đè lúc tạo” không có nghĩa file sẽ không bị thay sau khi tạo.

## 4. Giảm khoảng trống bằng thao tác atomic, không phải bằng kiểm tra nhanh hơn

Một nguyên tắc thực tế: nếu điều kiện phải đúng **ngay tại lúc thao tác**, hãy tìm API thực hiện kiểm tra điều kiện và hành động **trong cùng thao tác**.

### 4.1. Tạo file nếu chưa có

- `Files.createFile(path)` kiểm tra tên chưa có và tạo file rỗng như **một thao tác atomic**; nếu tồn tại thì thất bại.
- `Files.newByteChannel(path, Set.of(CREATE_NEW, WRITE))` mở channel và yêu cầu tạo mới độc quyền, tránh chuỗi check rồi mở.
- `Files.writeString(path, text, CREATE_NEW, WRITE)` làm tương tự cho nội dung chuỗi trong ví dụ trên.

Đừng diễn giải “atomic create” thành “ghi toàn bộ nội dung atomic”. `CREATE_NEW` bảo vệ bước tạo tên file; một process khác có thể quan sát file đang được ghi dở. Muốn người đọc chỉ thấy **phiên bản nội dung hoàn chỉnh**, cần chiến lược **ghi vào file staging rồi publish**.

### 4.2. Ghi staging rồi publish bằng atomic move

Mô hình phổ biến đối với một file kết quả:

1. Tạo file tạm mới bằng `Files.createTempFile(parent, prefix, suffix)` **trong thư mục đích hoặc cùng FileStore**, với quyền thích hợp.
2. Ghi toàn bộ nội dung vào file tạm, hoàn tất xử lý và đóng stream/channel.
3. Gọi `Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE)` để yêu cầu filesystem đổi entry trong một thao tác atomic.
4. Nếu provider ném `AtomicMoveNotSupportedException`, xử lý theo hợp đồng của ứng dụng: từ chối publish, chọn cơ chế có kiểm soát hoặc rollback. Không âm thầm fallback sang `move` thường rồi tuyên bố thao tác vẫn atomic.

Ví dụ logic publish (mã nằm **bên trong method có `throws IOException`**, với `parent` và `destination` đã được xác định an toàn):

~~~java
Path staging = Files.createTempFile(parent, ".publish-", ".part");
try {
    Files.writeString(staging, payload,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);

    // Có thể ném AtomicMoveNotSupportedException.
    Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
} finally {
    Files.deleteIfExists(staging);
}
~~~

Có hai điều phải ghi nhớ:

- Với `ATOMIC_MOVE`, tài liệu Java quy định **các option khác bị bỏ qua**; khi `destination` đã tồn tại, hành vi thay thế hay từ chối **phụ thuộc implementation**. Vì vậy **`ATOMIC_MOVE` không cung cấp cam kết “create only, tuyệt đối không overwrite”** cho đích đã tồn tại. Nếu cần cả atomic publish lẫn exclusive creation, phải chọn giao thức/storage hỗ trợ đúng tính chất đó và kiểm thử trên filesystem mục tiêu.
- Atomic rename/move chủ yếu bảo vệ **tính nhất quán của việc nhìn thấy entry**, không tự bảo đảm dữ liệu đã được flush bền vững xuống storage sau mất điện. Nếu cần durability, còn phải xét `FileChannel.force(...)`, flush thư mục và bảo đảm của hệ điều hành/filesystem.

### 4.3. File lock và mutex có đủ không?

- `synchronized`, `ReentrantLock` chỉ phối hợp các luồng **cùng chia sẻ đúng lock** trong JVM. Process khác không phải tham gia.
- `FileChannel.lock()` / `tryLock()` là một cơ chế phối hợp khóa file; cách cưỡng chế khóa và phạm vi của chúng phụ thuộc platform. Nhiều filesystem dùng khóa advisory hoặc có hành vi khác trên mạng; process không tuân giao thức lock vẫn có thể gây vấn đề.
- Distributed lock cũng chỉ có hiệu lực khi **mọi writer tuân giao thức** và điều kiện hết hạn/fencing được xử lý đúng.

Nếu cần tránh ghi đè, `CREATE_NEW` thường mạnh và đơn giản hơn “check bằng `exists` rồi lock/ghi”. Nếu bài toán là nhiều writer cùng cập nhật một giá trị, cần thiết kế concurrency control phù hợp, không chỉ thêm khóa quanh một `Path`.

## 5. Symlink race: kiểm tra một đường dẫn, mở một đối tượng khác

### 5.1. Cơ chế

Giả sử một service chạy với quyền cao nhận tên `report.txt` bên trong thư mục mà user có thể thay đổi entry:

~~~text
T1: /uploads/report.txt -> file bình thường, kiểm tra hợp lệ
       |
       | attacker đổi report.txt thành symlink
       v
T2: /uploads/report.txt -> /some/other/location
    service mở theo path, hành động bị chuyển hướng
~~~

Symlink không phải nguyên nhân duy nhất. Đối tượng file còn có thể bị **rename/replace**, và một thành phần đường dẫn cha như `/uploads/current` có thể bị đổi thành symlink; chỉ kiểm tra symlink của **tên file cuối** chưa bảo vệ được mọi thành phần.

Các thao tác như `Path.normalize()` và `Path.toAbsolutePath()` là xử lý tên đường dẫn, không khóa filesystem và không bảo đảm đối tượng đang được mở chính là đối tượng đã kiểm tra trước đó. `toRealPath()` giải quyết symlink để lấy real path tại **thời điểm gọi**, nhưng file vẫn có thể đổi trước lần sử dụng sau. Vì vậy “gọi `toRealPath` rồi mở file” vẫn có TOCTOU.

### 5.2. `NOFOLLOW_LINKS` giúp gì?

`LinkOption.NOFOLLOW_LINKS` yêu cầu API có hỗ trợ **không đi theo symbolic link** ở phép truy cập tương ứng. Ví dụ:

~~~java
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.Set;

try (SeekableByteChannel channel = Files.newByteChannel(
        path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
    // Đọc từ channel đã mở.
}
~~~

Đây là **đoạn minh họa**, giả định biến `path` đã được khai báo và code ở trong method có `throws IOException`. `NOFOLLOW_LINKS` trong lời gọi mở channel này giúp tránh follow **symlink cuối cùng** theo quy tắc provider. Nó không biến một lần `Files.isRegularFile(path, NOFOLLOW_LINKS)` trước đó thành thao tác atomic, không tự bảo vệ đường dẫn cha có thể bị thay, và không thay thế kiểm soát quyền của thư mục.

Chỉ truyền `NOFOLLOW_LINKS` cho API thực sự chấp nhận nó; đừng suy luận mọi API nhận `Path` sẽ tuân cùng semantics.

### 5.3. `SecureDirectoryStream`: thao tác tương đối với thư mục đang mở

Java NIO.2 có interface `java.nio.file.SecureDirectoryStream<Path>` dành cho chương trình cần thực hiện file operation tương đối với một **handle của thư mục đã mở**. Tài liệu Java mô tả mục tiêu là tránh race khi di chuyển, xóa và truy cập file trong cây thư mục mà path có thể bị thay trong lúc chạy.

Điểm khác với cách `Files.isRegularFile(fullPath)` rồi `Files.newInputStream(fullPath)`:

- `SecureDirectoryStream` định vị tên **tương đối với thư mục đã mở**, thay vì mỗi lần đi từ pathname đầy đủ.
- Có thể mở entry con bằng `newByteChannel(relativeName, options)`, dùng `getFileAttributeView` cho file con, `deleteFile(relativeName)`, `move(...)` hoặc mở thư mục con bằng `newDirectoryStream(relativeDir, NOFOLLOW_LINKS)`.
- Những thao tác mở thư mục con có thể giữ xử lý tương đối với handle qua từng mức cây, tránh phụ thuộc vào tên thư mục ngoài vốn có thể bị rename/replace.
- **Không phải provider nào cũng hỗ trợ**. `Files.newDirectoryStream(dir)` chỉ trả về một đối tượng triển khai `SecureDirectoryStream` nếu hệ điều hành và provider cung cấp cơ chế tương ứng. Không được cast bừa và giả định tính năng luôn có.

Ví dụ file `SecureDirectoryStreamDemo.java`, chỉ đọc một file nằm trong thư mục tạm do chương trình tạo:

~~~java
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;

public class SecureDirectoryStreamDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("secure-dir-");
        Path file = dir.resolve("data.txt");

        try {
            Files.writeString(file, "hello",
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            try (DirectoryStream<Path> raw = Files.newDirectoryStream(dir)) {
                if (!(raw instanceof SecureDirectoryStream<?>)) {
                    System.out.println("secure-stream=unavailable");
                } else {
                    @SuppressWarnings("unchecked")
                    SecureDirectoryStream<Path> secure =
                            (SecureDirectoryStream<Path>) raw;

                    try (SeekableByteChannel channel = secure.newByteChannel(
                            Path.of("data.txt"),
                            Set.of(StandardOpenOption.READ,
                                   LinkOption.NOFOLLOW_LINKS))) {
                        ByteBuffer buffer = ByteBuffer.allocate(64);
                        channel.read(buffer);
                        buffer.flip();
                        System.out.println(
                                StandardCharsets.UTF_8.decode(buffer));
                    }
                }
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch:

~~~bash
javac SecureDirectoryStreamDemo.java
java SecureDirectoryStreamDemo
~~~

Có **hai kết quả hợp lệ tùy provider**:

~~~text
hello
~~~

hoặc:

~~~text
secure-stream=unavailable
~~~

Trong nhánh hỗ trợ, `newByteChannel` nhận tên tương đối `data.txt` và làm việc với directory stream đang mở. Đây là ví dụ API cho entry đơn giản; trong dịch vụ thực tế phải kiểm tra tên đầu vào, tránh tên tuyệt đối hoặc `..`, bảo đảm handle ban đầu mở từ thư mục tin cậy và triển khai chính sách **fail closed** nếu môi trường bắt buộc race resistance mà provider không hỗ trợ.

**Giới hạn quan trọng:** giữ handle thư mục chưa tự xác nhận **quyền truy cập của ứng dụng đối với nội dung** hoặc dữ liệu đang đọc. Cần xác minh input, đối tượng và permissions tại thời điểm thích hợp. Khi cần kiểm soát chính xác các thao tác cấp hệ điều hành, có thể cần API native tương đương `openat` hoặc cơ chế hệ thống riêng — Java NIO.2 không đảm bảo tất cả các thuộc tính bảo mật của mọi filesystem.

## 6. Kiểm tra đường dẫn: cần làm nhưng chưa đủ

Giả sử chỉ cho phép tên file trực tiếp bên trong thư mục `root`:

~~~java
Path root = approvedRoot.toAbsolutePath().normalize();
Path candidate = root.resolve(requestedName).normalize();

if (!candidate.startsWith(root)) {
    throw new SecurityException("Path escapes root");
}
~~~

Đây là **mã minh họa nằm trong method** với hai biến đầu vào `approvedRoot` và `requestedName` đã khai báo. Nó giúp loại bỏ trường hợp path traversal **theo tên** như `../../outside.txt` sau khi normalize. Tuy nhiên:

- `startsWith` là kiểm tra theo các thành phần path, không kiểm tra real target trên filesystem.
- Symlink có thể trỏ ra ngoài dù pathname đã chuẩn hóa vẫn nằm bên dưới `root`.
- Nếu một tiến trình khác có quyền thay directory entry, kiểm tra ở T1 không bảo vệ cho lần mở ở T2.
- Nếu chỉ cho phép **tên file một cấp**, nên kiểm tra `requestedName` thực sự là filename đơn, không có separator hoặc thành phần `..`, trước khi resolve.
- Nên giới hạn quyền sửa của thư mục, đặt tên file do server kiểm soát khi phù hợp và dùng API thao tác tương đối với handle hoặc các lựa chọn mở atomic.

Kiểm tra path đầu vào vẫn cần thiết vì giúp xác định phạm vi. Hãy phân biệt rõ **path validation** với **filesystem object identity**; hai vấn đề phải được giải quyết bằng các cơ chế phù hợp.

## 7. Đọc, ghi, sửa và xóa: chọn biện pháp theo hành động

| Mục tiêu | Cách không đủ | Hướng thiết kế tốt hơn |
| --- | --- | --- |
| Chỉ tạo mới, không ghi đè | `if (!exists) write` | `CREATE_NEW` hoặc `Files.createFile` |
| Không cho đọc qua symlink cuối | `isSymbolicLink` rồi `newInputStream` | Mở bằng API hỗ trợ `NOFOLLOW_LINKS`; xem cả đường dẫn cha |
| Làm việc trong cây thư mục có thể bị thay | Kiểm tra real path rồi nhiều lần mở bằng full path | `SecureDirectoryStream` khi provider hỗ trợ; policy nếu không hỗ trợ |
| Publish file hoàn chỉnh | Ghi trực tiếp vào file kết quả | Ghi staging rồi `ATOMIC_MOVE`, kiểm tra semantics thay thế đích |
| Chỉ xóa entry đã được chọn | `isRegularFile(p)` rồi `delete(p)` | Định vị entry tương đối với handle thư mục tin cậy; khi phù hợp, `SecureDirectoryStream.deleteFile` |
| Giới hạn số byte đọc | `Files.size(p)` rồi `readAllBytes(p)` | Đọc stream/channel với bộ đếm byte và ngừng ngay khi vượt giới hạn |
| Cập nhật một record theo phiên bản | `read -> validate -> write` không khóa | Compare-and-set, optimistic locking với version, transaction hoặc unique constraint phù hợp |

Không phải atomic ở một bước nghĩa là cả nghiệp vụ đa bước atomic. Ví dụ tạo file thành công và sau đó insert DB row thất bại vẫn để lại trạng thái dở dang; khi cần nhất quán xuyên tài nguyên phải có transaction/compensation hoặc giao thức tương ứng.

## 8. Ví dụ TOCTOU ngoài file: giới hạn của check-before-update

TOCTOU cũng xuất hiện trong API/DB. Giả sử hai request cùng xử lý một mã coupon:

~~~text
A: SELECT remaining -> 1
B: SELECT remaining -> 1
A: UPDATE remaining -> 0; cho phép dùng
B: UPDATE remaining -> 0; cũng cho phép dùng
~~~

Nếu ứng dụng chỉ dựa vào lần `SELECT` trước đó, hai request có thể cùng được chấp nhận. Hướng đúng là biến điều kiện thành một **thao tác có kiểm soát cạnh tranh** ở storage, chẳng hạn:

~~~sql
UPDATE coupon
SET remaining = remaining - 1
WHERE id = ? AND remaining > 0;
~~~

Đoạn SQL này chỉ minh họa một thao tác decrement có điều kiện. Ứng dụng phải kiểm tra **số hàng bị cập nhật**, bảo đảm khóa/định danh và transaction/isolation phù hợp. Nếu `UPDATE` ảnh hưởng 0 hàng thì từ chối sử dụng; không xác nhận thành công bằng kết quả `SELECT` cũ.

Bài toán file và DB chung bản chất: một “lần kiểm tra đúng” không phải cam kết rằng điều kiện vẫn đúng khi thao tác khác bắt đầu.

## 9. Những giải pháp tưởng đúng nhưng còn race

### 9.1. “Kiểm tra lại ngay trước lúc mở”

~~~java
if (Files.exists(path) && Files.isReadable(path)) {
    // Race vẫn có thể xảy ra ngay tại đây.
    try (var in = Files.newInputStream(path)) {
        // ...
    }
}
~~~

Khoảng thời gian có thể ngắn hơn, nhưng chưa biến check+open thành một thao tác. `exists` và `isReadable` còn có semantics không bảo đảm lần mở sau sẽ thành công. Cần thiết kế trên **open/operation có ràng buộc thích hợp** rồi xử lý kết quả tại chính thao tác đó.

### 9.2. “Dùng `synchronized` là xong”

`synchronized` chỉ có ý nghĩa giữa những code path dùng **cùng monitor** trong một JVM; worker khác, JVM khác, process ngoài và attacker không bắt buộc tuân nó. Dùng khi cần đồng bộ nội bộ, không lấy nó làm bảo vệ file upload dùng chung.

### 9.3. “Dùng `NOFOLLOW_LINKS` ở lần check”

`Files.isRegularFile(path, NOFOLLOW_LINKS)` quan sát link tại một thời điểm. Nếu sau đó mở bằng lệnh không dùng `NOFOLLOW_LINKS`, vẫn có cửa sổ hoán đổi. Ngay cả `NOFOLLOW_LINKS` ở lần mở cũng phải được xem cùng rủi ro **ancestor path**.

### 9.4. “So `fileKey` trước và sau”

`BasicFileAttributes.fileKey()` đôi khi giúp nhận diện file trên provider hỗ trợ, nhưng không phải tất cả provider trả khóa đáng tin cậy hoặc ổn định. So sánh hai lần vẫn là **phát hiện sau sự kiện**, không thay thế phép mở và xử lý gắn với cùng object. Khi thao tác ghi/xóa đã diễn ra trên sai file, phát hiện sau đó có thể quá muộn.

### 9.5. “Chỉ xử lý `IOException` rồi retry”

Retry cùng đường dẫn mà không xem lại điều kiện an toàn có thể lặp lại race hoặc tác động lên entry mới. Phân biệt `FileAlreadyExistsException`, `NoSuchFileException`, `AccessDeniedException` và `AtomicMoveNotSupportedException`; phản ứng phụ thuộc hợp đồng của thao tác.

### 9.6. “Di chuyển file là atomic trên mọi ổ đĩa”

`Files.move` bình thường không có bảo đảm atomic chung cho mọi provider. `ATOMIC_MOVE` cũng có thể không được hỗ trợ, đặc biệt nếu khác filesystem. Không tự giả định việc kiểm tra trước `exists` giải quyết chuyện replace target trong `ATOMIC_MOVE`, vì đặc tả cho phép semantics khác nhau nếu target đã tồn tại.

## 10. Quy trình thiết kế chống TOCTOU

1. **Xác định bất biến cần giữ**: không overwrite; không đọc ra ngoài thư mục; không xóa sai file; chỉ một request được claim job; không xử lý quá dung lượng cho phép.
2. **Xác định tác nhân và phạm vi cạnh tranh**: thread cùng JVM, process khác, người dùng có quyền sửa directory entry, symlink, network filesystem hay transaction DB.
3. **Tìm một primitive atomic đúng hợp đồng**: `CREATE_NEW`, directory-handle operations, conditional update, transaction, compare-and-set hoặc commit protocol.
4. **Giữ handle/reference ổn định khi cần**: sau khi mở file/channel phù hợp, ưu tiên xử lý chính handle đó. Tránh kiểm tra trên path rồi mở lại cùng tên nếu sự thay đổi identity gây nguy hiểm.
5. **Bảo vệ directory entry và kiểm soát quyền**: tên server sinh, directory chỉ service được sửa, option chống follow link thích hợp và provider hỗ trợ `SecureDirectoryStream` nếu cần.
6. **Định nghĩa hành vi khi race thất bại**: thông thường coi “file đã tồn tại” là conflict hợp lệ, không phải lý do fallback sang overwrite. Ghi log bối cảnh tối thiểu rồi trả lỗi phù hợp.
7. **Kiểm thử thứ tự đan xen có kiểm soát**: dùng latch/barrier, hook giả lập hoặc fixture để cưỡng bức T1–tác nhân đổi trạng thái–T2. Không dựa vào `Thread.sleep(...)` để hy vọng race tự xuất hiện.
8. **Xác minh trên filesystem triển khai thực tế**: provider khác nhau ở hỗ trợ symlink, `SecureDirectoryStream`, atomic move, ACL và khả năng khóa.

Một phép thử âm tính hữu ích là tạo entry từ actor thứ hai **ngay sau check**, rồi xác minh ứng dụng không ghi đè. Với symlink race, cần kiểm thử trên hệ điều hành/provider thực sự có quyền tạo symlink và giới hạn rõ điều gì được bảo vệ; không suy diễn từ kết quả trên máy dev.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** `Files.notExists(path)` trả `true`; có bảo đảm lệnh `Files.writeString(path, ...)` tiếp theo được phép tạo file mới mà không ghi đè không?

**Đáp án:** Không. Entry có thể xuất hiện sau lần check. Nếu yêu cầu là “chỉ tạo mới”, truyền `CREATE_NEW` tại chính lần mở/ghi.

**Câu 2.** `Files.createFile(path)` atomic có nghĩa là toàn bộ nội dung file do nhiều lần `write` sau đó cũng atomic?

**Đáp án:** Không. Atomic ở đây là hành vi check-and-create đối với entry. Publication nội dung hoàn chỉnh là bài toán khác, thường cần staging + atomic move và hiểu rõ guarantee của filesystem.

**Câu 3.** Sau `Path.toRealPath()`, service chắc chắn không thể bị symlink race nếu tiếp tục mở bằng đường dẫn đó?

**Đáp án:** Không. `toRealPath` phản ánh thực trạng lúc gọi, không khóa các entry. Cần xem ai có thể thay parent và tận dụng open tương đối với directory handle hoặc API có option an toàn.

**Câu 4.** Vì sao `NOFOLLOW_LINKS` ở một lần `isRegularFile` chưa đủ?

**Đáp án:** Đó vẫn là phép kiểm tra tách khỏi lần mở, nên entry có thể bị thay sau check. Option tại thao tác mở có hiệu lực thích hợp hơn đối với final symlink, nhưng chưa tự bảo vệ ancestor path.

**Câu 5.** Tại sao chỉ dùng `synchronized` không chống được attacker sửa file qua process khác?

**Đáp án:** Monitor Java chỉ đồng bộ những thread chung JVM dùng chính monitor đó. Process khác không chịu ràng buộc.

**Câu 6.** Nếu provider không hỗ trợ `SecureDirectoryStream` mà hệ thống yêu cầu chống đổi directory entry khi đọc/xóa, nên làm gì?

**Đáp án:** Không âm thầm cast hoặc dùng fallback dựa vào các bước check/open nếu điều đó phá bất biến bảo mật. Từ chối tính năng, đổi provider/môi trường, siết quyền directory hoặc xây dựng cơ chế native phù hợp và kiểm thử.

## 12. Tài liệu tham khảo

- [Java SE 17 `Files` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): `exists`, `notExists`, `createFile`, `newByteChannel`, `move` và option `ATOMIC_MOVE`.
- [Java SE 17 `SecureDirectoryStream` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html): truy cập tương đối với directory handle, các thao tác mở/xóa/move tránh race theo mục tiêu của interface.
- [Java SE 17 `LinkOption` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html): semantics của `NOFOLLOW_LINKS`.
- [Java SE 17 `StandardOpenOption` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html): `CREATE_NEW`, `CREATE`, `TRUNCATE_EXISTING`, `WRITE`.
- [Java SE 17 `FileChannel` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): `lock`, `tryLock` và `force`.

**Điểm cốt lõi:** TOCTOU là lỗi của **giao thức nhiều bước dựa trên trạng thái có thể thay đổi**. Giải pháp không phải giảm vài microsecond giữa check và use, mà là thiết kế để điều kiện được xác nhận **ngay trong thao tác có ràng buộc phù hợp**, hoặc giữ reference ổn định tới đúng đối tượng cần xử lý.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-io-design-005">SecureDirectoryStream và khả năng hỗ trợ của provider</a>

<details>
<summary>Xem chi tiết</summary>

# SecureDirectoryStream và khả năng hỗ trợ của provider trong Java NIO.2

## 1. Tại sao cần SecureDirectoryStream?

Một ứng dụng Java có thể xử lý tệp trong thư mục do người dùng hoặc tiến trình khác có khả năng sửa đổi. Quy trình “kiểm tra đường dẫn rồi thao tác” quen thuộc có một khoảng thời gian dễ bị lợi dụng:

~~~java
Path file = root.resolve(userName);
if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
    Files.delete(file);
}
~~~

**Đây là đoạn mã minh họa rủi ro, không phải mẫu xóa an toàn.** Sau khi `isRegularFile` kiểm tra và trước `delete`, một tác nhân khác có thể thay thế một thành phần đường dẫn. Khi đó hai lời gọi có thể nói về **hai đối tượng tệp khác nhau**. Lớp lỗi này gọi là *time-of-check to time-of-use*, viết tắt **TOCTOU**.

`SecureDirectoryStream<T>` trong `java.nio.file` giải quyết một phần quan trọng của vấn đề: cung cấp những thao tác đối với **đường dẫn tương đối theo một thư mục đang mở** thay vì liên tục giải lại từ một đường dẫn tên tuyệt đối có thể bị tráo. Đây là interface được thiết kế cho những ứng dụng cần duyệt cây thư mục hoặc làm việc trên tệp theo cách chống *race condition* khi provider/hệ điều hành hỗ trợ.

~~~text
Không có handle neo thư mục:
check /safe/inbox/item.txt
       |
  attacker đổi inbox -> symlink tới /other
       |
delete /safe/inbox/item.txt  => có thể đụng đối tượng khác

Có SecureDirectoryStream:
open /safe/inbox  --> handle H tới thư mục đang mở
       |
       +-- deleteFile(Path.of("item.txt"))
                thao tác tương đối với H
                kể cả khi tên "inbox" bị đổi
~~~

Đây là sơ đồ **mô hình đe dọa**, không có nghĩa mọi thao tác trên mọi hệ thống đều có cùng kết quả. `SecureDirectoryStream` cần hỗ trợ từ provider và hệ điều hành, và không thay thế kiểm soát quyền, xác thực, bảo vệ link cứng hay chính sách sandbox.

**Ý chính:** `SecureDirectoryStream` tăng khả năng chống tấn công tráo đường dẫn/hoán đổi symbolic link ở các thao tác tương đối với thư mục đang mở. Nó **không mặc nhiên có trên mọi filesystem và provider**; ứng dụng bảo mật phải kiểm tra hỗ trợ ngay tại runtime.

## 2. Quan hệ giữa DirectoryStream và SecureDirectoryStream

`DirectoryStream<T>` hỗ trợ lặp qua các entry của một thư mục, thường được lấy từ:

~~~java
try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
    for (Path entry : entries) {
        System.out.println(entry.getFileName());
    }
}
~~~

Đây là đoạn minh họa với `root` là `Path` có sẵn, cần xử lý `IOException`. Với một `DirectoryStream<Path>` thông thường, iterator chỉ mang về các `Path` để ứng dụng tiếp tục gọi `Files.*` trên tên đó. Việc có tên đường dẫn trong iterator **không khóa** entry hoặc bảo đảm entry vẫn là đối tượng cũ khi thao tác.

`SecureDirectoryStream<T>` **extends `DirectoryStream<T>`** và thêm thao tác theo open-directory context:

| Phương thức | Công dụng |
| --- | --- |
| `newDirectoryStream(T path, LinkOption... options)` | Mở thư mục con tương đối với thư mục đang mở, trả về một `SecureDirectoryStream<T>` khác. |
| `newByteChannel(T path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)` | Mở/tạo tệp qua một đường dẫn tương đối và trả về `SeekableByteChannel`. |
| `deleteFile(T path)` | Xóa một file entry (cũng dùng để xóa symbolic link) tương đối với thư mục hiện tại. |
| `deleteDirectory(T path)` | Xóa directory entry tương đối, với các điều kiện xóa thư mục tương ứng. |
| `move(T src, SecureDirectoryStream<T> targetDir, T target)` | Di chuyển entry từ thư mục đang mở sang một thư mục đích đang mở. |
| `getFileAttributeView(Class<V> type)` | Mở attribute view của chính thư mục đang mở. |
| `getFileAttributeView(T path, Class<V> type, LinkOption... options)` | Mở attribute view của một entry tương đối. |
| `close()` | Giải phóng directory stream/handle đang mở. |

Tham số `T` trong Java NIO thường là `Path`. Khi mở từ `Files.newDirectoryStream(root)`, kiểu khai báo thường là `DirectoryStream<Path>`; **không nên ép kiểu ngay** nếu chưa kiểm tra instance.

## 3. Provider support là đặc tính tùy chọn

API `SecureDirectoryStream` tồn tại từ Java 7, và Java 17 có đầy đủ những phương thức trên. Nhưng **sự tồn tại của interface không bảo đảm provider có triển khai**.

Javadoc Java 17 quy định: nếu implementation hỗ trợ tính năng này, `DirectoryStream` được trả bởi `newDirectoryStream` có thể là một `SecureDirectoryStream`. Ứng dụng phải kiểm tra đối tượng thực tế:

~~~java
try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
    if (stream instanceof SecureDirectoryStream<?>) {
        System.out.println("Provider hỗ trợ tại vị trí này");
    } else {
        System.out.println("Không có SecureDirectoryStream");
    }
}
~~~

Đây là đoạn mã minh họa, cần import `java.nio.file.*`, biến `root` và xử lý `IOException`.

Phụ thuộc thực tế:

- **FileSystemProvider** được chọn bởi filesystem của `Path` đang sử dụng.
- Hệ điều hành có hoặc không có cơ chế mở/thao tác tương đối qua handle.
- Tùy phiên bản JDK, provider, loại filesystem và môi trường thực thi (filesystem gắn mạng, filesystem ảo, container...).

**Không viết điều kiện “Linux luôn có”, “Windows luôn không có”, “ZIP filesystem chắc chắn có”**. Khả năng hỗ trợ thực sự phải được kiểm tra ở runtime; các nhà cung cấp có thể khác nhau.

### 3.1. Vì sao không nên gọi trực tiếp trên DirectoryStream?

~~~java
DirectoryStream<Path> stream = Files.newDirectoryStream(root);

// Không hợp lệ về kiểu: DirectoryStream không định nghĩa deleteFile.
// stream.deleteFile(Path.of("data.txt"));
~~~

Phải kiểm tra và sử dụng đối tượng như `SecureDirectoryStream<Path>`:

~~~java
if (stream instanceof SecureDirectoryStream<?>) {
    @SuppressWarnings("unchecked")
    SecureDirectoryStream<Path> secure =
            (SecureDirectoryStream<Path>) stream;
    secure.deleteFile(Path.of("data.txt"));
}
~~~

Đây là những **mảnh minh họa**, chưa có phần mở/đóng resource hoàn chỉnh. Cast phù hợp ở ví dụ vì nguồn `stream` được khai báo `DirectoryStream<Path>`; không suy diễn cast đó cho các custom `DirectoryStream<T>` với type parameter khác.

### 3.2. Nếu provider không hỗ trợ thì làm gì?

Tùy yêu cầu:

1. **Công việc có yêu cầu chống path-race như một điều kiện bảo mật:** *fail closed*, ngừng thao tác và báo provider/môi trường không đáp ứng yêu cầu.
2. **Thư mục thuộc quyền kiểm soát độc quyền, không ai khác có thể đổi entry trong thời gian xử lý:** có thể cân nhắc API `Files.*` thông thường theo mô hình tin cậy đã chứng minh.
3. **Cần hỗ trợ đa nền tảng nhưng vẫn yêu cầu tính an toàn tương đương:** thay kiến trúc (vùng staging riêng với quyền truy cập chặt, phiên bản tệp bất biến, service đặc quyền chuyên trách hoặc cơ chế hệ điều hành tương đương được kiểm chứng).

**Không tạo “fallback” bằng cách chỉ gọi `toRealPath()`, `normalize()`, `Files.isSymbolicLink()` rồi `Files.delete()` và tuyên bố tương đương:** check-then-use vẫn có cửa sổ race khi một actor khác có thể thay đổi đường dẫn.

## 4. Relative path và neo vào directory handle

Điều quan trọng nhất là các phương thức bảo mật thường nhận **đường dẫn tương đối với thư mục đang mở**:

~~~java
Path name = Path.of("invoice.txt");
SeekableByteChannel channel =
        secure.newByteChannel(name, Set.of(StandardOpenOption.READ));
~~~

Mảnh mã này giả định `secure` là `SecureDirectoryStream<Path>` hợp lệ, đã được mở.

Khi `name` là relative path, thao tác dựa vào directory handle hiện tại; đổi tên thư mục trong hệ thống tên đường dẫn không làm handle tự động trỏ sang directory khác.

**Đường dẫn tuyệt đối là một ngoại lệ cần tránh.** Theo Javadoc, nếu truyền absolute path vào các phương thức này, việc định vị có thể tuân theo ngữ nghĩa đường dẫn tuyệt đối và làm mất mục tiêu neo vào thư mục đang mở. Hãy từ chối absolute path cho những thao tác có ý định giới hạn trong root.

### 4.1. Đừng chấp nhận path traversal

Ngay cả khi dùng secure stream, vẫn phải loại đường dẫn không thuộc miền tên được phép:

~~~text
Hợp lệ với API chỉ cho phép tên file đơn:
  report.txt
  data_001.bin

Phải từ chối theo quy tắc một thành phần:
  ../secret.txt
  subdir/../../secret.txt
  /etc/passwd
  ./
  ..
~~~

Kiểm tra `normalize()` và `startsWith(root)` trên đường dẫn tuyệt đối **không bảo vệ đầy đủ** trước các race do liên kết tượng trưng hoặc việc thay đổi thư mục từ bên ngoài. Cách dễ kiểm toán hơn là giới hạn user input thành **tên entry đơn**, từ chối `.`/`..`, phân cách đường dẫn, absolute path, tên đặc biệt và các trường hợp không hợp lệ theo filesystem/ứng dụng.

Ví dụ một validator minh họa:

~~~java
static Path safeSingleName(FileSystem fs, String raw) {
    if (raw == null || raw.isBlank()) {
        throw new IllegalArgumentException("Tên rỗng");
    }
    Path name = fs.getPath(raw);
    if (name.isAbsolute()
            || name.getNameCount() != 1
            || name.toString().equals(".")
            || name.toString().equals("..")) {
        throw new IllegalArgumentException("Không phải tên entry đơn");
    }
    return name;
}
~~~

Đây là **mảnh mã**; cần `java.nio.file.FileSystem` và `Path`. Với đầu vào từ Internet, còn phải định nghĩa chính sách tên, Unicode normalization, ký tự cấm, reserved names và case-folding phù hợp hệ thống. Validator không thay thế các kiểm tra authorization/ownership; nó chỉ thu hẹp phạm vi tên mà người dùng được chỉ định.

### 4.2. Nhiều cấp thư mục: đi từng cấp có kiểm soát

Nếu ứng dụng thực sự cho phép thao tác trong cấu trúc con, nên mở thư mục con theo directory handle và kiểm soát symbolic link ở **mỗi bước**:

~~~java
try (SecureDirectoryStream<Path> sub =
        secure.newDirectoryStream(
                Path.of("uploads"), LinkOption.NOFOLLOW_LINKS)) {

    try (SeekableByteChannel file = sub.newByteChannel(
            Path.of("report.txt"),
            Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
        // Đọc dữ liệu từ channel đã mở.
    }
}
~~~

Đây là mảnh minh họa (cần biến `secure` hợp lệ, các import và exception handling). `NOFOLLOW_LINKS` ở `newDirectoryStream` ngăn việc mở thư mục con nếu entry cuối chính là symbolic link. Tương tự, `NOFOLLOW_LINKS` trong set open options của `newByteChannel` yêu cầu thao tác thất bại nếu entry đích là symbolic link.

**Không trộn parent traversal tùy ý vào một chuỗi path do user nhập**, rồi mặc định nó an toàn vì đã có `SecureDirectoryStream`. Path được phép nên được giới hạn và đi từng thành phần qua các handle đã tin cậy.

## 5. Symbolic link và NOFOLLOW_LINKS

Một nguyên nhân phổ biến của path race là **symbolic link**: attacker thay entry trong directory thành link trỏ tới vị trí ngoài phạm vi.

`LinkOption.NOFOLLOW_LINKS` chỉ rõ mục tiêu không được đi theo symlink ở thao tác tương ứng:

| Thao tác | Ví dụ dùng no-follow | Ý nghĩa |
| --- | --- | --- |
| Mở thư mục con | `secure.newDirectoryStream(child, LinkOption.NOFOLLOW_LINKS)` | Thất bại nếu entry đó là symlink. |
| Mở file byte channel | `secure.newByteChannel(file, Set.of(READ, LinkOption.NOFOLLOW_LINKS))` | Thất bại nếu file entry đích là symlink. |
| Đọc metadata của entry | `secure.getFileAttributeView(file, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)` | Xem attribute của entry/link thay vì đi theo symlink. |

Chú ý **không phải mọi method đều nhận LinkOption**: `deleteFile`, `deleteDirectory` và `move` có hợp đồng riêng. Đừng cố truyền `NOFOLLOW_LINKS` vào `deleteFile` vì overload như vậy không tồn tại.

`NOFOLLOW_LINKS` không thay thế việc giới hạn đường dẫn tuyệt đối hoặc đường dẫn chứa `..`. Nó cũng không ngăn mọi thay đổi do hard link, mount, quyền filesystem hay tiến trình khác đã mở file từ trước.

## 6. Một ví dụ Java 17+ hoàn chỉnh, chạy độc lập và an toàn

Ví dụ này:

1. Tạo **thư mục tạm riêng** và file `message.txt` chứa `HELLO`.
2. Gọi `Files.newDirectoryStream(root)`.
3. Nếu provider hỗ trợ `SecureDirectoryStream`, mở file **theo tên tương đối** và `NOFOLLOW_LINKS`, đọc nội dung bằng `SeekableByteChannel`, rồi dùng `deleteFile` để xóa entry.
4. Nếu không hỗ trợ, **không giả vờ dùng API kém an toàn để thay thế**; chương trình thông báo từ chối thao tác bảo mật.
5. Đóng stream và channel bằng `try-with-resources`, sau đó dọn file/thư mục tạm.

Lưu thành `SecureDirectoryStreamDemo.java`:

~~~java
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.util.Set;

public class SecureDirectoryStreamDemo {

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("sds-demo-");
        Path file = root.resolve("message.txt");

        try {
            Files.writeString(
                    file, "HELLO", StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE_NEW);

            try (DirectoryStream<Path> stream =
                    Files.newDirectoryStream(root)) {

                if (stream instanceof SecureDirectoryStream<?>) {
                    @SuppressWarnings("unchecked")
                    SecureDirectoryStream<Path> secure =
                            (SecureDirectoryStream<Path>) stream;

                    Path name = Path.of("message.txt");

                    try (SeekableByteChannel channel =
                            secure.newByteChannel(
                                    name,
                                    Set.of(
                                            StandardOpenOption.READ,
                                            LinkOption.NOFOLLOW_LINKS))) {

                        byte[] bytes = Channels.newInputStream(channel)
                                .readAllBytes();
                        System.out.println("mode=SECURE");
                        System.out.println("text=" +
                                new String(bytes, StandardCharsets.US_ASCII));
                    }

                    secure.deleteFile(name);
                    System.out.println("deleted=" +
                            !Files.exists(file, LinkOption.NOFOLLOW_LINKS));
                } else {
                    System.out.println("mode=UNAVAILABLE");
                    System.out.println("action=REFUSED");
                }
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(root);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac SecureDirectoryStreamDemo.java
java SecureDirectoryStreamDemo
~~~

Nếu filesystem/provider hỗ trợ `SecureDirectoryStream` và file tạm hoạt động bình thường, stdout:

~~~text
mode=SECURE
text=HELLO
deleted=true
~~~

Nếu provider **không** hỗ trợ, stdout:

~~~text
mode=UNAVAILABLE
action=REFUSED
~~~

Hai đầu ra trên là **hai nhánh có điều kiện theo môi trường**, không phải kết quả cố định duy nhất của mọi hệ điều hành/JDK. Nếu các thao tác filesystem không được cấp quyền hoặc có lỗi I/O, chương trình có thể ném `IOException` thay vì in trọn nhánh; đây là hành vi đúng khi một thao tác bảo mật thất bại.

### 6.1. Vì sao kiểm tra instance thay vì chỉ kiểm tra hệ điều hành?

`Files.newDirectoryStream(root)` trả kiểu khai báo `DirectoryStream<Path>`. Nếu đối tượng thực tế triển khai `SecureDirectoryStream`, ta có thể dùng API bổ sung. Sự hỗ trợ tùy hệ điều hành và provider, nên kiểm tra kiểu thực tế **chính xác hơn** việc dựa vào tên OS hoặc filesystem được ghi trong cấu hình.

### 6.2. Vì sao đọc qua SeekableByteChannel?

`secure.newByteChannel(...)` gắn thao tác mở file với directory handle, không buộc chương trình gọi lại `Files.newInputStream(root.resolve(name))` (có thể tái giải đường dẫn). `Channels.newInputStream(channel)` chỉ bọc **channel đã mở**, nên không thay đổi đối tượng file được gắn với handle đó.

`newByteChannel` trả một channel độc lập với lifetime của directory stream đã mở nó; tuy nhiên bài ví dụ vẫn giữ stream mở cho đến khi hoàn tất các thao tác `deleteFile` để đảm bảo có directory handle dùng cho deletion.

### 6.3. Vì sao dọn bằng Files.deleteIfExists ở cuối?

Phần `finally` xử lý **thư mục tạm do chương trình tự tạo và sở hữu**, sau khi các secure operations đã kết thúc. Nó là logic dọn thử nghiệm, **không phải fallback bảo mật tương đương** nếu ta đang xử lý một directory do attacker kiểm soát. Nếu root tạm bị một actor không tin cậy kiểm soát thì phải đánh giá lại giả định an toàn của ví dụ.

## 7. Mở file bằng newByteChannel

Chữ ký Java 17:

~~~java
SeekableByteChannel newByteChannel(
        T path,
        Set<? extends OpenOption> options,
        FileAttribute<?>... attrs
) throws IOException;
~~~

Đây là **chữ ký phương thức**, không phải đoạn mã có thể đặt nguyên khối trong `main`.

Những option thường dùng:

- `StandardOpenOption.READ`: đọc.
- `StandardOpenOption.WRITE`: ghi.
- `StandardOpenOption.CREATE_NEW`: chỉ tạo khi file chưa tồn tại; hữu ích để tránh ghi đè entry đã có.
- `StandardOpenOption.CREATE`: tạo nếu chưa tồn tại, nhưng không từ chối entry đã có; cân nhắc với file untrusted.
- `StandardOpenOption.TRUNCATE_EXISTING`: cắt ngắn file hiện có khi mở ghi, nguy hiểm nếu target không được xác thực đúng.
- `LinkOption.NOFOLLOW_LINKS`: từ chối nếu final path entry là symbolic link.

Để tạo tệp mới theo directory handle:

~~~java
try (SeekableByteChannel created = secure.newByteChannel(
        Path.of("new-item.dat"),
        Set.of(
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW,
                LinkOption.NOFOLLOW_LINKS))) {

    ByteBuffer data = StandardCharsets.UTF_8.encode("safe payload");
    while (data.hasRemaining()) {
        created.write(data);
    }
}
~~~

Đây là mảnh minh họa cần import `ByteBuffer`, `StandardCharsets` và các API liên quan. `CREATE_NEW` tránh ghi đè một entry đã tồn tại; nhưng không đồng nghĩa user input đã được phép tạo tên đó theo quy tắc nghiệp vụ. Hãy kiểm tra tên/permission và giới hạn byte.

Tham số `FileAttribute<?>... attrs` có thể được sử dụng để đặt thuộc tính ngay lúc tạo file **khi provider hỗ trợ**; các attribute không được hỗ trợ có thể gây `UnsupportedOperationException`. Không giả định mọi provider đều chấp nhận thuộc tính POSIX.

## 8. Xóa file, symlink và thư mục

`secure.deleteFile(relativeName)` xóa một **entry** file tương đối với directory stream; nếu entry là symbolic link, hành vi định nghĩa theo link entry, không phải xóa theo đích link. `secure.deleteDirectory(relativeName)` xóa directory entry theo hợp đồng của filesystem, không phải “xóa cây đệ quy”.

~~~java
secure.deleteFile(Path.of("obsolete.tmp"));
secure.deleteDirectory(Path.of("empty-folder"));
~~~

Đây là mảnh minh họa: cả hai tên phải hợp lệ trong directory handle và provider có quyền xóa; thư mục cần đáp ứng quy tắc xóa directory của filesystem (thường phải rỗng). Các lỗi như `NoSuchFileException`, `DirectoryNotEmptyException`, `AccessDeniedException` hoặc `IOException` cần được xử lý theo nghiệp vụ.

**Rủi ro ngoài phạm vi của secure stream:** nếu attacker có quyền sửa những nội dung bên trong file đã mở hoặc tạo hard link liên quan đến file, directory-relative API không tự đảm bảo nội dung là tin cậy. SecureDirectoryStream chủ yếu phục vụ **định vị file trong cây thư mục khi tên có thể bị tráo**, không thay thế hệ thống quyền.

### 8.1. Xóa cây thư mục có thể phức tạp hơn

Muốn xóa cả cây do attacker có thể đồng thời thay đổi, không nên chỉ liệt kê `Files.walk(root)` rồi gọi `Files.delete(path)` trên absolute paths. Cần chiến lược thao tác tương đối bằng directory handles cho từng cấp, không đi theo symlink, kiểm tra quyền và giới hạn độ sâu/số entry. Trong môi trường không hỗ trợ SecureDirectoryStream, nên **không tuyên bố** một bản đệ quy tương tự có cùng tính kháng TOCTOU.

## 9. move(): thao tác giữa hai thư mục đang mở

API:

~~~java
void move(
        T srcpath,
        SecureDirectoryStream<T> targetdir,
        T targetpath
) throws IOException;
~~~

Ví dụ:

~~~java
try (SecureDirectoryStream<Path> destination =
        secure.newDirectoryStream(
                Path.of("archive"), LinkOption.NOFOLLOW_LINKS)) {

    secure.move(
            Path.of("input.txt"),
            destination,
            Path.of("input-archived.txt"));
}
~~~

Đây là mảnh minh họa với `secure` trỏ vào directory nguồn và có thư mục con `archive` hợp lệ.

Theo Javadoc Java 17, `move` thực hiện tương tự `Files.move` với `ATOMIC_MOVE`: việc di chuyển được thực hiện như một thao tác filesystem nguyên tử. Tuy nhiên:

- Provider có thể ném `AtomicMoveNotSupportedException` khi không thể thực hiện move nguyên tử.
- Nếu target đã tồn tại, việc thay thế hay thất bại là **implementation-specific**.
- Không có tham số `REPLACE_EXISTING` cho phương thức `SecureDirectoryStream.move` này; đừng tự giả định một chính sách ghi đè.
- Tên `srcpath` và `targetpath` nên được kiểm soát là **relative paths** trong các thư mục đã tin cậy. Nếu dùng absolute paths thì ngữ nghĩa có thể bỏ qua directory handle mong muốn.
- Atomic rename không thay thế xác thực dữ liệu, permission check, durability của thay đổi hay quy trình publish phiên bản có thể khôi phục sau mất điện.

## 10. Lấy metadata an toàn theo directory handle

`getFileAttributeView` giúp lấy/sửa thông tin tệp mà không phải dựng lại absolute path theo name:

~~~java
BasicFileAttributeView view = secure.getFileAttributeView(
        Path.of("report.txt"),
        BasicFileAttributeView.class,
        LinkOption.NOFOLLOW_LINKS);

if (view != null) {
    BasicFileAttributes attrs = view.readAttributes();
    System.out.println("size=" + attrs.size());
}
~~~

Đây là mảnh minh họa, cần `java.nio.file.attribute.BasicFileAttributeView` và `BasicFileAttributes`. Tùy provider, attribute view có thể không được hỗ trợ và trả `null`. Ngoài `BasicFileAttributeView`, một số provider có thể hỗ trợ attribute views chuyên biệt, nhưng không nên giả định như nhau ở các filesystem.

`NOFOLLOW_LINKS` giúp nhắm vào metadata của entry cuối thay vì link target. Mặc dù secure directory handle làm giảm rủi ro *định vị entry*, metadata vẫn là **ảnh chụp tại một thời điểm**: file có thể bị writer khác sửa nội dung hoặc metadata sau khi đọc. Muốn tính nhất quán của payload/ACL/version, cần chiến lược bổ sung như file bất biến, exclusive ownership hoặc protocol đồng bộ.

### 10.1. Attribute view của chính thư mục

~~~java
BasicFileAttributeView dirView =
        secure.getFileAttributeView(BasicFileAttributeView.class);
BasicFileAttributes dirAttrs = dirView.readAttributes();
~~~

Đây là mảnh minh họa; nếu provider không hỗ trợ view được hỏi có thể trả `null`, vì vậy production code cần kiểm tra trước khi dereference.

Lợi ích của view từ thư mục đã mở là hạn chế việc tra lại một tên đường dẫn có thể bị thay thế. Tuy vậy, nó không biến mọi lần đọc thuộc tính thành giao dịch nguyên tử với thao tác khác trong chương trình.

## 11. SecureDirectoryStream không phải sandbox hoàn chỉnh

Những gì interface hướng tới:

- Xử lý các path tương đối theo **open directory** thay vì một chuỗi tên dễ bị thay thế.
- Đi vào thư mục con bằng một **secure directory handle** mới, có thể dùng `NOFOLLOW_LINKS`.
- Mở file byte channel, xóa và move entry theo ngữ nghĩa hỗ trợ giảm path-race.
- Lấy attribute view gắn với directory handle để giảm phụ thuộc check-then-use trên tên đường dẫn toàn cục.

Những gì nó **không tự bảo đảm**:

1. **Không cung cấp authorization theo người dùng:** ứng dụng vẫn phải xác minh user nào được đọc/xóa file nào.
2. **Không chặn mọi hard link:** một entry không phải symlink vẫn có thể trỏ tới inode mà ứng dụng không nên thao tác; quyền và chính sách filesystem vẫn cần thiết.
3. **Không khóa nội dung file khỏi writer khác:** open channel đúng file không có nghĩa byte không thể bị sửa đồng thời.
4. **Không tránh tuyệt đối việc thay đổi mount/namespace:** security model phụ thuộc hệ điều hành và quyền của attacker; cần đánh giá threat model cụ thể.
5. **Không loại bỏ mọi TOCTOU ngoài filesystem operation:** xác minh metadata, parse format, kiểm tra checksum và quyết định nghiệp vụ là những bước riêng.
6. **Không tự giải quyết symbolic link trong path đã cho nếu ta không kiểm soát cấu trúc:** phải đi từng cấp và dùng `NOFOLLOW_LINKS` đúng nơi.
7. **Không bảo đảm mọi provider hỗ trợ:** phải kiểm tra đối tượng thực tế và xử lý nhánh không hỗ trợ.
8. **Không thay thế TLS, chữ ký số hoặc kiểm tra dữ liệu:** đường dẫn “được định vị đúng” không đồng nghĩa dữ liệu là đáng tin.

Một hệ thống an toàn phải xác định rõ quyền hệ điều hành, ai có thể sửa directory và các entry, dữ liệu nào do user nhập, provider nào thực sự hoạt động và hậu quả khi thao tác thất bại.

## 12. Race condition mẫu và cách thay đổi thiết kế

### 12.1. Sai lầm: Files.isDirectory rồi duyệt bên trong

~~~java
if (Files.isDirectory(root.resolve("incoming"))) {
    try (DirectoryStream<Path> entries =
            Files.newDirectoryStream(root.resolve("incoming"))) {
        // Có cửa sổ race giữa kiểm tra và mở.
    }
}
~~~

Thư mục `incoming` có thể bị thay đổi thành symlink trong khoảng giữa hai lời gọi; đây là một **mảnh mã minh họa kém an toàn trong môi trường untrusted**.

Với secure stream, nếu đã có handle tin cậy tới root:

~~~java
try (SecureDirectoryStream<Path> incoming =
        rootSecure.newDirectoryStream(
                Path.of("incoming"), LinkOption.NOFOLLOW_LINKS)) {
    // Làm việc tương đối với chính thư mục đã mở.
}
~~~

Lưu ý việc **tạo `rootSecure` ban đầu** cũng phải xuất phát từ nguồn root đáng tin và cơ chế bảo vệ thích hợp. Nếu attacker có thể thay root giữa lúc ta chọn root và lúc `Files.newDirectoryStream(root)` mở nó, secure stream sau đó neo vào thư mục **thực sự đã mở**, chưa chắc là thư mục ứng dụng dự định theo business policy.

### 12.2. Sai lầm: kiểm tra symlink rồi mở thường

~~~java
if (!Files.isSymbolicLink(file)) {
    Files.newInputStream(file); // Một tên khác có thể xuất hiện sau check.
}
~~~

**Mã minh họa rủi ro.** Khi dùng `SecureDirectoryStream.newByteChannel(relativeName, Set.of(READ, NOFOLLOW_LINKS))`, việc kiểm soát follow-link được chỉ định ngay trong thao tác mở file theo directory handle, tránh khoảng check-then-open đó cho entry cuối.

### 12.3. Sai lầm: bỏ qua trường hợp unsupported

~~~java
DirectoryStream<Path> ds = Files.newDirectoryStream(root);
SecureDirectoryStream<Path> s =
        (SecureDirectoryStream<Path>) ds; // Có thể ClassCastException.
~~~

Chương trình chạy được trên máy A chưa chắc an toàn hoặc hoạt động trên máy B. Kiểm tra `instanceof` và có nhánh fail-closed rõ ràng. Không “bắt ClassCastException rồi chuyển sang Files.delete” nếu rủi ro ban đầu là symlink/path-race.

## 13. Chi tiết về vòng đời và xử lý ngoại lệ

### 13.1. Đóng directory stream đúng lúc

`DirectoryStream` và `SecureDirectoryStream` đều implement `Closeable`, nên:

~~~java
try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
    // Kiểm tra instanceof và thao tác ở đây.
}
~~~

Đóng stream giải phóng tài nguyên liên quan; những phương thức secure-relative gọi trên stream đã đóng có thể ném `ClosedDirectoryStreamException`. Một `SecureDirectoryStream` con được mở từ stream cha **có vòng đời riêng** theo hợp đồng: đóng cha không tự động đóng con. Tương tự, channel mở từ `newByteChannel` cũng phải đóng độc lập.

Vì vậy có thể tách lifetime của directory handle và file handle, nhưng mã thực tế nên làm ownership minh bạch để tránh rò rỉ descriptor.

### 13.2. Các lỗi có thể cần xử lý

| Lỗi / ngoại lệ | Tình huống |
| --- | --- |
| `NoSuchFileException` | Entry không còn tồn tại khi truy cập. |
| `AccessDeniedException` | Quyền không cho phép thao tác. |
| `NotDirectoryException` | Tên được yêu cầu mở như directory nhưng không phải directory (tùy provider). |
| `DirectoryNotEmptyException` | Xóa thư mục vẫn có entry. |
| `ClosedDirectoryStreamException` | Gọi secure operation sau khi directory stream đã đóng. |
| `AtomicMoveNotSupportedException` | Không thể chuyển `move` theo thao tác nguyên tử. |
| `UnsupportedOperationException` | Provider không hỗ trợ option/attribute được yêu cầu. |
| `IOException` | Lỗi I/O rộng hơn (kể cả race làm target biến mất). |

Không giả định mọi hệ điều hành trả cùng **lớp exception cụ thể** trong mọi tình huống; nhiều lớp ngoại lệ chi tiết là *optional specific exception*. Khi thao tác bảo mật không thể hoàn tất theo yêu cầu, cần dừng/rollback nghiệp vụ phù hợp thay vì âm thầm bỏ qua.

### 13.3. Thread-safety và iteration

Một directory stream dùng để iterate tên entry; tập entry có thể thay đổi trong lúc duyệt. Không dựa vào thứ tự duyệt, không giả định snapshot, và không dùng stream/channels từ nhiều thread theo cách có data race. Nếu cần xử lý bất đồng bộ, hãy xác định thread nào sở hữu directory handle và quản lý thời điểm close.

## 14. Một quy trình an toàn hơn khi xử lý file upload

Ví dụ dịch vụ nhận tên file từ request và lưu trong một thư mục upload:

1. **Xác thực và phân quyền** user trước, giới hạn số lượng và kích thước upload.
2. Chọn root theo cấu hình tin cậy, không lấy một absolute path từ user.
3. Mở root và **kiểm tra `instanceof SecureDirectoryStream<?>`**; nếu provider không hỗ trợ mà yêu cầu chống path-race là bắt buộc, từ chối thao tác.
4. Kiểm soát tên file là **một thành phần relative**; cấm absolute, `.`, `..` và các biểu diễn qua separator.
5. Sử dụng `newByteChannel(name, Set.of(WRITE, CREATE_NEW, NOFOLLOW_LINKS))` để tạo entry, tránh ghi đè và theo symlink.
6. Ghi dữ liệu theo byte count, kiểm tra quota và size giới hạn; xử lý partial write khi cần.
7. Nếu upload cần commit có kiểm soát, lưu ở một vùng staging tin cậy, xác minh nội dung và dùng quy trình publish đã thiết kế. `move` secure-relative có ngữ nghĩa atomic nhưng không tự bổ sung mọi yêu cầu durability/authorization.
8. Đóng channel và directory streams đúng vòng đời, xóa bản tạm khi lỗi, nhưng phải xét race/ownership ngay cả khi cleanup.
9. Log những lỗi không hỗ trợ provider, permission, size/quota và thao tác từ chối mà không lộ đường dẫn nhạy cảm cho client.

Đừng xem `CREATE_NEW` là bảo đảm “file này do mình tạo nên chắc chắn an toàn”: dữ liệu có thể đến từ nguồn không tin cậy, tên có thể có xung đột ở mức nghiệp vụ và còn rất nhiều yêu cầu an ninh khác.

## 15. Khi nào vẫn dùng Files.* bình thường?

`Files.readString`, `Files.copy`, `Files.delete`, `Files.move` và `Files.walkFileTree` có API rất tiện. Chúng không tự trở thành sai chỉ vì tồn tại `SecureDirectoryStream`.

Chọn `Files.*` khi:

- Tệp/thư mục được ứng dụng sở hữu với **quyền hệ điều hành bảo vệ**, không có actor không tin cậy có thể tráo path giữa các thao tác.
- Đọc file cấu hình bất biến, dữ liệu build nội bộ, test fixtures hoặc công việc batch không có threat model path-race đáng kể.
- Provider không hỗ trợ secure stream nhưng workflow **được chứng minh là không yêu cầu** bảo vệ race giữa check/use.

Chọn `SecureDirectoryStream` khi:

- Xóa/di chuyển/đọc/ghi trong thư mục dùng chung nơi actor không tin cậy có thể thay entry đồng thời.
- Cần bảo vệ khỏi symlink swap của các thành phần directory do đối thủ kiểm soát.
- Duyệt cây tệp hoặc publish entry theo directory handle với yêu cầu hạn chế TOCTOU.
- Có thể **yêu cầu môi trường hỗ trợ API này** hoặc có chính sách fail-closed.

Nếu yêu cầu bảo vệ path-race bắt buộc mà platform không có khả năng tương đương, giải pháp đúng có thể là **không triển khai chức năng trên platform đó**, chứ không phải âm thầm giảm bảo đảm.

## 16. Bài tập tự kiểm tra

1. **`SecureDirectoryStream` là class hay interface?** Là interface mở rộng `DirectoryStream<T>`.
2. **Mọi `Files.newDirectoryStream(dir)` đều trả một `SecureDirectoryStream` không?** Không; phụ thuộc provider và hỗ trợ hệ điều hành.
3. **Kiểm tra hỗ trợ bằng cách nào?** Xem đối tượng thực tế có `instanceof SecureDirectoryStream<?>` hay không; chỉ cast khi hợp lệ.
4. **Vì sao `Files.isSymbolicLink(path)` rồi `Files.newInputStream(path)` có thể nguy hiểm?** Có khoảng TOCTOU giữa kiểm tra và sử dụng tên.
5. **Lợi ích của relative path theo directory handle?** Giảm việc giải lại tên directory trên toàn cục khi entry bị đổi tên hoặc tráo.
6. **`NOFOLLOW_LINKS` có vai trò gì?** Yêu cầu không theo symbolic link ở thao tác tương ứng, ví dụ mở thư mục con/file.
7. **`deleteFile` có tham số `NOFOLLOW_LINKS` không?** Không; nó có hợp đồng xóa entry riêng.
8. **`move` của secure stream có bảo đảm overwrite target không?** Không; khi target tồn tại, hành vi replace hay fail tùy implementation.
9. **`move` có thể ném gì nếu atomic move không khả dụng?** `AtomicMoveNotSupportedException`.
10. **Đóng secure directory stream cha có tự đóng stream con không?** Không. Stream con có lifetime riêng.
11. **Đóng directory stream có tự đóng channel đã mở từ nó không?** Không. Channel có lifetime riêng.
12. **Nếu provider không hỗ trợ, có được tuyên bố fallback check-then-delete đạt cùng bảo đảm không?** Không; nếu chống race là yêu cầu bắt buộc cần fail-closed hoặc thay kiến trúc.
13. **Đưa `../../etc/passwd` làm relative path có an toàn vì đã dùng secure stream không?** Không; cần validation và kiểm soát các thành phần path.
14. **SecureDirectoryStream có ngăn mọi hard link attack không?** Không; cần xét ownership, quyền và threat model filesystem.
15. **Nếu file đã mở an toàn nhưng một writer khác có thể thay nội dung cùng file, data có tự bất biến không?** Không; phải có kiểm soát version/ownership và đọc/ghi nhất quán.
16. **Nếu chương trình chạy trên Linux nhưng FileSystemProvider tự viết không hỗ trợ SDS, có dùng được không?** Không được giả định; kiểm tra runtime và xử lý unsupported.
17. **Có nên dùng absolute path khi gọi secure.newByteChannel(...) để giới hạn trong root?** Không; absolute path có thể bỏ qua ngữ nghĩa neo vào thư mục đang mở.
18. **Nếu ứng dụng cần chống path-race nhưng không kiểm soát root ban đầu, mở SDS có đủ chứng minh thư mục đúng không?** Chưa; cần đảm bảo ban đầu root handle trỏ đến thư mục tin cậy.
19. **Có phải mọi DirectoryStream iteration phản ánh snapshot bất biến?** Không; entry có thể thay đổi trong lúc lặp.
20. **SecureDirectoryStream có thay thế kiểm tra nội dung upload và quota không?** Không; nó tập trung thao tác đường dẫn tương đối, không xác thực dữ liệu.

## 17. Tài liệu Java 17 chính thức

- [SecureDirectoryStream — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html): ngữ nghĩa open-directory-relative, các phương thức hỗ trợ, fallback và ngoại lệ.
- [DirectoryStream — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/DirectoryStream.html): iteration và vòng đời directory stream.
- [Files — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): `newDirectoryStream`, `newByteChannel` và các thao tác tệp thông thường.
- [LinkOption — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html): `NOFOLLOW_LINKS` và ý nghĩa tùy thao tác.
- [FileSystemProvider — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/spi/FileSystemProvider.html): vai trò của provider trong filesystem implementation.

**Ghi nhớ:** `SecureDirectoryStream` hoạt động theo **handle của thư mục đang mở**, giúp chống nhiều race do tráo entry/link khi thao tác trên **relative paths**. Hỗ trợ phụ thuộc provider và hệ điều hành, nên phải **kiểm tra `instanceof` tại runtime**. Nếu an toàn chống race là yêu cầu bắt buộc, provider không hỗ trợ thì **fail closed**, không thay bằng check-then-use và tuyên bố tương đương. Luôn kiểm soát tên path, symlink, quyền truy cập, lifetime và tính toàn vẹn dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-io-design-006">Tạo tệp tạm có quyền phù hợp và dọn dẹp có kiểm soát</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo tệp tạm có quyền phù hợp và dọn dẹp có kiểm soát trong Java NIO

## 1. Vì sao tệp tạm cũng là một vấn đề bảo mật?

Ứng dụng Java thường tạo tệp tạm để xử lý ảnh tải lên, xuất báo cáo, giải nén, sắp xếp dữ liệu lớn, lưu kết quả trung gian hoặc chuyển dữ liệu giữa các bước xử lý. Tệp chỉ tồn tại trong thời gian ngắn **không có nghĩa là an toàn**.

Các rủi ro thực tế gồm:

- **Lộ dữ liệu:** tệp chứa token, dữ liệu khách hàng, nội dung upload hoặc thông tin bí mật nhưng người dùng khác trên máy có thể đọc.
- **Dự đoán tên và chiếm chỗ:** dùng tên cố định như `/tmp/report.txt` rồi ghi đè có thể tạo race hoặc va chạm giữa các tiến trình.
- **Symbolic-link attack:** kẻ tấn công làm đường dẫn tạm trỏ đến file đích khác trước khi ứng dụng mở lại.
- **TOCTOU:** ứng dụng kiểm tra `exists()`, `isSymbolicLink()` hoặc quyền truy cập ở thời điểm A, nhưng file bị thay thế trước lúc sử dụng ở thời điểm B.
- **Rò rỉ tài nguyên:** file/channel còn mở, tệp và thư mục tạm còn lại sau exception, cancel hoặc timeout.
- **Đầy ổ đĩa:** upload không giới hạn tạo file tạm lớn, hàng nghìn request để lại file làm dịch vụ ngừng hoạt động.
- **Xóa không triệt để:** gọi `delete()` không đồng nghĩa dữ liệu cũ đã bị xóa vật lý khỏi SSD, swap, snapshot, backup hoặc journal.

Mục tiêu của bài này: **tạo tệp tạm bằng API phù hợp; áp dụng quyền ngay khi tạo; cô lập thư mục làm việc; dùng tài nguyên có quyền sở hữu rõ ràng; dọn dẹp khi thành công hoặc lỗi; hiểu rõ những giới hạn còn lại.**

## 2. Chọn API nào để tạo file hoặc thư mục tạm?

Java NIO.2 (package `java.nio.file`) có các phương thức:

~~~java
Path file = Files.createTempFile("prefix-", ".tmp");
Path dir  = Files.createTempDirectory("job-");

// Chỉ định thư mục cha đáng tin cậy:
Path fileInDir = Files.createTempFile(dir, "data-", ".bin");
Path nestedDir = Files.createTempDirectory(dir, "stage-");
~~~

Các phương thức `Files.createTempFile(...)` và `Files.createTempDirectory(...)` **tạo đối tượng trong hệ thống tệp ngay tại lời gọi**, thay vì chỉ sinh một chuỗi tên để dùng sau này.

Khi provider hỗ trợ, việc tạo entry mới kiểm tra sự tồn tại và tạo file một cách **nguyên tử đối với hoạt động filesystem liên quan**, tránh lỗi phổ biến của mẫu “chọn tên rồi kiểm tra chưa tồn tại”. Chúng không tạo hợp đồng khóa hoặc giao dịch cho các thao tác **sử dụng sau khi tạo**.

| API | Kết quả | Dùng khi nào? |
|---|---|---|
| `Files.createTempFile(prefix,suffix,attrs...)` | Tệp tạm trong vị trí do provider xác định | Thao tác độc lập không cần cây riêng |
| `Files.createTempFile(dir,prefix,suffix,attrs...)` | Tệp tạm trong thư mục đã chọn | Muốn quản lý quyền/trọn vòng đời một job |
| `Files.createTempDirectory(prefix,attrs...)` | Thư mục tạm trong vị trí do provider xác định | Cần nơi chứa riêng nhiều file trung gian |
| `Files.createTempDirectory(parent,prefix,attrs...)` | Thư mục con tạm trong parent | Dịch vụ có khu vực scratch do quản trị viên kiểm soát |
| `Files.createFile(path,attrs...)` | File có tên xác định, phải chưa tồn tại | Khi application quản lý rõ pathname và yêu cầu create-new |
| `File.createTempFile(...)` | API `java.io.File` cũ | Hệ thống legacy; code mới ưu tiên NIO `Files` |

**Không viết:**

~~~java
// Mẫu sai: tên cố định, kiểm tra rồi mới tạo.
Path p = Path.of("/tmp/my-app-export.csv");
if (!Files.exists(p)) {
    Files.writeString(p, confidentialData);
}
~~~

Có race giữa `exists()` và `writeString()`. Ngoài ra, `Files.writeString` với bộ tùy chọn mặc định có thể tạo file mới hoặc truncate file đang tồn tại, không phù hợp với yêu cầu không ghi đè dữ liệu lạ.

**Nên dùng** `Files.createTempFile(...)` hoặc một lệnh create-new có semantics được xác định rõ. Tránh tự sinh tên tạm bằng timestamp, thread ID hoặc random yếu rồi ghép vào thư mục dùng chung.

### Prefix/suffix có phải là thông tin bí mật?

Không. Prefix/suffix để giúp nhận dạng loại file và vận hành; tên do API sinh ra giúp tránh đụng độ nhưng **không thay thế quyền truy cập**. Đừng coi tên khó đoán là lớp bảo vệ duy nhất. Không cho token, password, email khách hàng hoặc dữ liệu nhạy cảm vào tên file vì tên có thể xuất hiện trong log, danh sách thư mục hay crash report.

## 3. Chọn thư mục cha: không phải mọi nơi “temp” đều đáng tin

`Files.createTempFile("x-", ".tmp")` và `createTempDirectory("x-")` không truyền tham số thư mục sẽ sử dụng vị trí mặc định của provider. Với provider mặc định, vị trí này thường liên quan thuộc tính hệ thống `java.io.tmpdir`.

Tuy nhiên:

- Vị trí temp thực tế có thể khác nhau theo OS/container, runtime configuration hoặc provider.
- Thư mục tạm chung có thể cho phép nhiều người dùng tạo entry trong đó.
- Temp trên volume mạng có đặc tính quyền, độ trễ, visibility và cleanup khác local disk.
- Một ứng dụng chạy trong container có thể dùng filesystem ephemeral, quota riêng hoặc mount read-only.
- Thư mục cha có thể không tồn tại, hết dung lượng hoặc không đủ quyền.

**Quy tắc tốt:** với dữ liệu nhạy cảm hoặc nhiều bước xử lý, tạo **một thư mục tạm riêng cho từng job**, hạn chế truy cập trên thư mục đó, rồi tạo các tệp con bên trong.

~~~text
trusted-temp-parent/
  secure-job-<generated>/
    source-<generated>.bin
    output-<generated>.json
    intermediate-<generated>.dat

Job owns entire subtree:
  create -> use -> close handles -> delete children -> delete directory
~~~

Khi chỉ một job được quyền ghi vào thư mục riêng, nhiều nguy cơ do người dùng khác thay thế pathname giảm đi đáng kể. Nhưng một tác nhân chạy **cùng tài khoản OS và có quyền tương đương**, hoặc người dùng đặc quyền, vẫn có thể can thiệp; thư mục 0700 không tạo sandbox chống chính principal sở hữu.

### Quyền của thư mục quan trọng ngang quyền file

Ngay cả file có mode `0600`, một thư mục cha có quyền viết quá rộng có thể làm pathname bị đổi/xóa, gây race lúc mở lại. Trên hệ POSIX, một thư mục scratch private thường dùng mode `0700` (owner read/write/execute, không cấp group/others).

**Đừng chỉ kiểm tra quyền file mà quên quyền parent chain.** Thư mục dùng để chứa dữ liệu mật nên do principal được tin cậy sở hữu, không bị ghi bởi đối tượng trái phép, và mọi symlink/mount trong đường dẫn phải thuộc chính sách kiểm soát.

## 4. POSIX: cấp quyền **ngay lúc tạo**, tránh “tạo rồi chmod”

Trên POSIX-compatible filesystem, các quyền cơ bản thường biểu diễn bằng chuỗi:

- `rw-------`, tức mode 0600: chỉ chủ sở hữu đọc/ghi file.
- `rwx------`, tức mode 0700: chỉ chủ sở hữu được đọc/ghi/đi qua thư mục.
- `r--r--r--`, tức mode 0444: chỉ quyền đọc, nhưng **không phù hợp** nếu cần sửa tệp trong quá trình xử lý.
- Quyền group/others có thể làm tăng người có thể đọc/thay đổi dữ liệu.

Java NIO cung cấp `PosixFilePermissions.asFileAttribute(...)` để truyền quyền khi tạo:

~~~java
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

Set<PosixFilePermission> dirMode =
        PosixFilePermissions.fromString("rwx------");
Set<PosixFilePermission> fileMode =
        PosixFilePermissions.fromString("rw-------");

FileAttribute<Set<PosixFilePermission>> privateDir =
        PosixFilePermissions.asFileAttribute(dirMode);
FileAttribute<Set<PosixFilePermission>> privateFile =
        PosixFilePermissions.asFileAttribute(fileMode);

// Tạo với thuộc tính quyền ngay trong thao tác tạo:
Path jobDir = Files.createTempDirectory("secure-job-", privateDir);
Path artifact = Files.createTempFile(jobDir, "data-", ".bin", privateFile);
~~~

Đây là **đoạn minh họa**, cần import `java.nio.file.Files` và `Path`. Chỉ chạy đúng trên provider hỗ trợ thuộc tính POSIX cho vị trí đó.

Tính chất quan trọng:

1. File attributes được chuyển vào **thao tác tạo**, nên tránh khoảng hở giữa việc tạo file với quyền quá rộng và thao tác sửa quyền sau đó.
2. Provider có thể từ chối attribute không hỗ trợ bằng `UnsupportedOperationException`; không được lờ đi và giả vờ quyền đã được bảo vệ.
3. Quyền hiệu lực có thể bị hạn chế thêm bởi umask/ACL/policy nền tảng; **kiểm tra lại quyền thực tế** nếu quyền là một security invariant.
4. `setPosixFilePermissions(path, ...)` trên một file **đã tồn tại** là thao tác thay đổi quyền riêng, không “hồi tố” việc file đã lộ dữ liệu ở thời điểm trước đó.

### Vì sao “tạo rồi sửa quyền” là nguy hiểm?

~~~java
// Không nên dùng cho dữ liệu nhạy cảm ở một thư mục chia sẻ.
Path tmp = Files.createTempFile("secret-", ".bin");
Files.setPosixFilePermissions(
        tmp, PosixFilePermissions.fromString("rw-------"));
~~~

Với provider mặc định, `createTempFile` có thể tạo quyền mặc định chặt hơn một lệnh tạo file thông thường, nhưng **không nên coi mọi provider đều bảo đảm chính sách của ứng dụng**. Nếu quyền cụ thể là bắt buộc, cung cấp thuộc tính ngay khi tạo và xác minh chúng trên nền tảng mục tiêu.

**Lưu ý về quyền owner:** mode 0600 không chống được malware chạy cùng account, root/administrator hay cơ chế backup có quyền cao; cần sandbox/OS account isolation khi threat model đòi hỏi.

## 5. Windows và filesystem không hỗ trợ POSIX

Windows thường dùng **ACL (Access Control List)**, không dựa hoàn toàn vào các bit quyền POSIX. Cú pháp `PosixFilePermissions` **không phải chính sách di động giữa mọi provider**.

Kiểm tra hỗ trợ trước khi dùng:

~~~java
boolean supportsPosix = FileSystems.getDefault()
        .supportedFileAttributeViews()
        .contains("posix");
~~~

Đây chỉ là kiểm tra capability của **default filesystem**; đường dẫn thực còn có thể ở provider/file store khác. Khi code xử lý nhiều filesystem, cần xử lý lỗi và xác minh trên chính đường dẫn mục tiêu.

Với Windows hoặc filesystem ACL-based:

- Dùng file owner và `AclFileAttributeView` / `AclEntry` khi chính sách yêu cầu thiết lập quyền cụ thể.
- Kiểm soát **inherited ACL** từ thư mục cha; không giả định tài khoản khác không có quyền vì chương trình chỉ biết tên user hiện tại.
- Nếu file share/volume có khả năng khác biệt, việc tạo file với thuộc tính ACL ngay khi tạo phụ thuộc provider; kiểm tra tài liệu platform và test security thực tế.
- Nếu không thể xác minh chính sách tối thiểu, **fail closed** đối với dữ liệu nhạy cảm thay vì âm thầm tạo file dưới quyền không rõ.
- Không dịch máy móc `chmod 0600` thành “an toàn tương đương trên mọi Windows filesystem”.

API liên quan:

~~~java
// Minh họa truy cập ACL view, không phải một policy hoàn chỉnh.
AclFileAttributeView view = Files.getFileAttributeView(
        path, AclFileAttributeView.class);
if (view == null) {
    throw new UnsupportedOperationException("ACL view unavailable");
}
// ACL thực tế phải được thiết kế theo owner, service account,
// các identity được phép và quyền inherited của parent.
~~~

Các import của đoạn minh họa thuộc `java.nio.file` và `java.nio.file.attribute`. Không nên sao chép một ACL từ môi trường khác rồi đặt bừa lên thư mục/temp file vì có thể khóa chính ứng dụng hoặc mở quyền ngoài ý muốn.

## 6. Mở lại tệp tạm: tránh symbolic link và thao tác thừa

Sau khi tạo, ứng dụng thường ghi file rồi đọc lại, chuyển sang bước tiếp hoặc xóa. Nếu một bên khác có thể thay pathname trong khoảng thời gian này, thao tác mở lại có thể trỏ đến **đối tượng khác**.

Các điểm phòng vệ:

1. Tạo tệp dưới một thư mục parent chỉ tài khoản tin cậy được phép ghi.
2. Tránh giữ tên cố định trong thư mục chia sẻ công cộng.
3. Khi mở lại, cân nhắc `LinkOption.NOFOLLOW_LINKS` trong các API nhận `OpenOption` để từ chối symlink ở thành phần cuối, **nếu provider hỗ trợ**.
4. Không coi `Files.isSymbolicLink(path)` rồi `open(path)` là kiểm tra không race: pathname có thể thay đổi giữa hai lời gọi.
5. Không mặc định `NOFOLLOW_LINKS` kiểm tra đầy đủ mọi thành phần cha hoặc ngăn tất cả hardlink/mount/directory swap attacks.
6. Với trường hợp đối thủ có thể sửa directory tree, cân nhắc `SecureDirectoryStream` hoặc API cấp OS có khả năng thao tác tương đối với một directory handle an toàn khi được hỗ trợ.

Ví dụ reopen file đã tạo:

~~~java
import java.nio.channels.SeekableByteChannel;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Set;

try (SeekableByteChannel out = Files.newByteChannel(
        artifact,
        Set.<OpenOption>of(
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS))) {
    // Ghi dữ liệu vào channel. Không mở với CREATE nếu file đã tồn tại.
}
~~~

Đoạn trên sử dụng `artifact` được tạo trước; chưa độc lập chạy được. Vì `Files.createTempFile(...)` **đã tạo file**, thao tác mở tiếp để ghi **không cần** `CREATE` hoặc `CREATE_NEW`. Đặc biệt `CREATE_NEW` trên chính đường dẫn vừa tạo sẽ **thất bại do file đã tồn tại**.

### Tại sao `CREATE_NEW` là một công cụ khác?

`Files.newByteChannel(path, Set.of(CREATE_NEW, WRITE), attrs...)` dùng khi muốn **tạo tệp mới tại pathname đã chọn** và tuyệt đối không ghi đè entry hiện hữu. Nó không thay cho `Files.createTempFile` trong trường hợp bạn đã có file tạm. Nếu cần tạo/tạo và mở bằng cùng một primitive atomic, có thể dùng `CREATE_NEW` với path được chọn dưới một parent tin cậy, nhưng phải tự xử lý lựa chọn tên, va chạm và quyền.

## 7. Quản lý vòng đời: tài nguyên nào phải đóng trước khi xóa?

Tệp tạm thường được mở bởi:

- `InputStream` / `OutputStream`.
- `BufferedReader` / `BufferedWriter`.
- `FileChannel` / `SeekableByteChannel`.
- Thư viện xử lý ảnh, ZIP, PDF, CSV, XML hoặc database engine.
- Một process con đang dùng đường dẫn.
- Một thread đang chạy operation bất đồng bộ.

**Nguyên tắc ownership:** thành phần tạo tài nguyên phải xác định ai chịu trách nhiệm đóng nó và ai được quyền xóa file. Nếu có thao tác I/O còn pending hoặc một process khác chưa kết thúc, xóa ngay có thể thất bại hoặc tạo hành vi phụ thuộc OS.

Mẫu thông thường:

~~~java
Path file = Files.createTempFile("work-", ".tmp");
try {
    try (OutputStream out = Files.newOutputStream(file)) {
        out.write(bytes);
    }

    // Xử lý bằng thư viện đọc file sau khi writer đã đóng.
    consumeFile(file);
} finally {
    Files.deleteIfExists(file);
}
~~~

Đây là mã minh họa, cần imports và các biến `bytes`/`consumeFile` do ứng dụng định nghĩa. `finally` được chạy cả khi xử lý thành công lẫn khi ném exception thông thường, nhưng **không có bảo đảm** nó chạy khi process bị kill cưỡng bức, JVM crash hoặc mất điện.

### Vì sao `try-with-resources` hữu ích hơn?

`try-with-resources` tự đóng `AutoCloseable` ở lối thoát, kể cả khi một bước xử lý ném exception. Nếu cả phần thân lẫn `close()` đều ném lỗi, Java giữ lỗi chính và thêm lỗi đóng làm **suppressed exception** — tốt hơn việc một lỗi cleanup trong `finally` che khuất nguyên nhân thất bại ban đầu.

Một cách tổ chức là tạo đối tượng quản lý workspace tạm triển khai `AutoCloseable`:

~~~java
try (TempWorkspace workspace = TempWorkspace.create()) {
    // Tạo và xử lý file bên trong workspace.
} // close() xóa nội dung và directory theo quy tắc.
~~~

Ví dụ chương trình hoàn chỉnh ở mục sau triển khai cấu trúc này.

## 8. Chương trình Java 17+ hoàn chỉnh: thư mục riêng 0700, file 0600 và cleanup

Chương trình dưới đây dành cho **filesystem POSIX** và cố tình **fail closed** nếu default filesystem không có POSIX attribute view. Điều này có nghĩa:

- Trên Linux/macOS hoặc filesystem POSIX phù hợp: tạo thư mục với mode owner-only, tạo file owner-only và xác minh không có quyền group/others.
- Trên Windows ACL-only: không cố tạo file nhạy cảm với quyền không thể chứng minh bằng POSIX; in thông báo và kết thúc bình thường.
- Mở file để ghi với `NOFOLLOW_LINKS`; sử dụng `try-with-resources` để đóng kênh trước khi xóa.
- Xóa file và các thư mục con bằng `Files.walkFileTree(...)`, không theo symbolic link trong traversal mặc định.
- In kết quả ổn định không tiết lộ đường dẫn file tạm ngẫu nhiên.

Lưu thành **`SecureTempLifecycleDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class SecureTempLifecycleDemo {

    // Scope sở hữu toàn bộ cây thư mục tạm.
    private static final class TempWorkspace implements AutoCloseable {
        private final Path root;

        TempWorkspace(Path root) {
            this.root = root;
        }

        @Override
        public void close() throws IOException {
            // walkFileTree mặc định KHÔNG theo symbolic links.
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(
                        Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(
                        Path dir, IOException failure)
                        throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static boolean ownerOnly(
            Set<PosixFilePermission> permissions) {
        for (PosixFilePermission permission : permissions) {
            if (permission.name().startsWith("GROUP_")
                    || permission.name().startsWith("OTHERS_")) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        if (!FileSystems.getDefault()
                .supportedFileAttributeViews()
                .contains("posix")) {
            System.out.println("POSIX unavailable: refuse sensitive temp");
            return;
        }

        Set<PosixFilePermission> directoryMode =
                PosixFilePermissions.fromString("rwx------");
        Set<PosixFilePermission> fileMode =
                PosixFilePermissions.fromString("rw-------");

        Path root = Files.createTempDirectory(
                "secure-job-",
                PosixFilePermissions.asFileAttribute(directoryMode));

        boolean directoryPrivate;
        boolean filePrivate;
        String content;

        try (TempWorkspace scope = new TempWorkspace(root)) {
            directoryPrivate = ownerOnly(
                    Files.getPosixFilePermissions(
                            root, LinkOption.NOFOLLOW_LINKS));

            if (!directoryPrivate) {
                throw new SecurityException("Directory too permissive");
            }

            Path file = Files.createTempFile(
                    root,
                    "record-",
                    ".txt",
                    PosixFilePermissions.asFileAttribute(fileMode));

            filePrivate = ownerOnly(
                    Files.getPosixFilePermissions(
                            file, LinkOption.NOFOLLOW_LINKS));

            if (!filePrivate) {
                throw new SecurityException("File too permissive");
            }

            ByteBuffer payload = ByteBuffer.wrap(
                    "hello".getBytes(StandardCharsets.UTF_8));

            try (SeekableByteChannel channel = Files.newByteChannel(
                    file,
                    Set.<OpenOption>of(
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            LinkOption.NOFOLLOW_LINKS))) {
                while (payload.hasRemaining()) {
                    int n = channel.write(payload);
                    if (n <= 0) {
                        throw new IOException("No write progress");
                    }
                }
            }

            // Directory thuộc owner nên các tiến trình không có
            // quyền ghi trong đó không thể thay pathname.
            content = Files.readString(file, StandardCharsets.UTF_8);
        } // Đã đóng channel; TempWorkspace.close() xóa cây tạm.

        System.out.println("private-directory=" + directoryPrivate);
        System.out.println("private-file=" + filePrivate);
        System.out.println("content=" + content);
        System.out.println("cleanup="
                + Files.notExists(root, LinkOption.NOFOLLOW_LINKS));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SecureTempLifecycleDemo.java
java SecureTempLifecycleDemo
~~~

**Stdout trên một filesystem POSIX hoạt động bình thường, có quyền tạo/xóa tệp:**

~~~text
private-directory=true
private-file=true
content=hello
cleanup=true
~~~

**Stdout khi default filesystem không hỗ trợ POSIX view:**

~~~text
POSIX unavailable: refuse sensitive temp
~~~

Giải thích:

1. Chương trình xác nhận khả năng dùng POSIX và không âm thầm bỏ qua security requirement.
2. `Files.createTempDirectory(..., asFileAttribute(0700))` tạo workspace riêng.
3. `Files.createTempFile(root,..., asFileAttribute(0600))` tạo file trong workspace với quyền được yêu cầu ngay khi tạo.
4. `getPosixFilePermissions(...)` xác minh không cấp quyền cho group/others. Cách kiểm tra này **không tự kiểm hết ACL bổ sung hoặc các cơ chế đặc quyền ngoài POSIX**.
5. `NOFOLLOW_LINKS` từ chối symlink ở file cuối khi reopen nếu provider triển khai đúng option. Parent riêng giảm rủi ro thay thế entry bởi user khác, nhưng không bảo đảm an toàn khi có kẻ tấn công cùng UID.
6. Các thao tác ghi theo `SeekableByteChannel` có vòng lặp vì không nên suy ra một lệnh `write` sẽ chuyển toàn bộ ByteBuffer trong mọi trường hợp.
7. `TempWorkspace.close()` dùng `walkFileTree`, gọi `Files.delete` trên từng file trước rồi mới delete directory. Mặc định traversal không theo symlink; nếu có symlink xuất hiện trong thư mục, nó được xử lý như entry để xóa link, không đi theo target.
8. `try-with-resources` đảm bảo cleanup được yêu cầu khi thoát khối bình thường hoặc do exception.
9. In `cleanup=true` chỉ sau khi việc xóa kết thúc và `Files.notExists(root, NOFOLLOW_LINKS)` xác nhận không còn entry ở đường dẫn đó.

**Giới hạn:** đây là ví dụ POSIX, không triển khai chính sách Windows ACL. Nếu không có POSIX support, nó từ chối tạo workspace nhạy cảm thay vì rơi về quyền mặc định không được xác minh. Với nhu cầu cross-platform, cần xây policy tương ứng trên ACL provider và kiểm thử quyền thực tế.

## 9. Dọn dẹp một file và dọn dẹp cả thư mục cây

### Một file tạm độc lập

Nếu chỉ có một file:

~~~java
Path temp = Files.createTempFile("job-", ".dat");
try {
    process(temp);
} finally {
    Files.deleteIfExists(temp);
}
~~~

`process` là hàm ứng dụng. Cần đóng mọi stream/channel do `process` mở trước khi đi tới xóa. Dùng `deleteIfExists` giúp không coi “file đã được một bước khác xóa” là lỗi, nhưng **không nuốt mọi lỗi xóa**: file còn mở, permission denied hoặc I/O failure vẫn có thể ném `IOException`.

### Nhiều file và thư mục con

Không thể xóa directory **còn chứa file** chỉ bằng một lần `Files.delete(directory)`. Cần xóa children trước và parent sau. `Files.walkFileTree` là API phù hợp khi ứng dụng **sở hữu toàn cây** và threat model không cho phép bên lạ sửa cây trong lúc cleanup.

Không dùng:

~~~java
// Không đủ nếu thư mục còn các entry:
Files.delete(tempDirectory);
~~~

Cũng không nên viết một hàm recursive delete nhận đường dẫn tùy ý từ input người dùng rồi xóa không kiểm soát. Nếu pathname bị nhập sai, bạn có thể xóa nhầm một thư mục production.

**Quy tắc an toàn cho cleanup tree:**

- Phải có bằng chứng ứng dụng đã tạo và sở hữu root workspace.
- Không nhận trực tiếp directory-to-delete từ request body hoặc query parameter.
- Không đi theo symbolic link (mặc định `walkFileTree` không bật `FOLLOW_LINKS`).
- Nếu cần bảo vệ chống cùng-account attacker sửa pathname, dùng phương án mạnh hơn như `SecureDirectoryStream` khi filesystem/provider hỗ trợ, thay vì tin rằng walk-and-delete luôn race-free.
- Log lỗi cleanup theo cách không để lộ thông tin nhạy cảm trong file name/path.
- Nếu xóa thất bại, báo cho hệ giám sát và có chính sách xử lý sau, không im lặng bỏ qua.

## 10. Vì sao `deleteOnExit()` không nên là phương án cleanup chính?

Java có:

~~~java
temp.toFile().deleteOnExit();
~~~

Nó yêu cầu JVM cố xóa file khi JVM **kết thúc bình thường** theo cơ chế shutdown hook; đây không phải bảo đảm đủ cho môi trường server hoặc dữ liệu nhạy cảm.

Nhược điểm:

- Không có bảo đảm chạy khi JVM bị kill cưỡng bức, mất điện hoặc OS crash.
- Ứng dụng server có thể sống nhiều ngày/tuần; file vẫn tồn tại suốt thời gian đó.
- Danh sách file đăng ký `deleteOnExit` tồn tại trong bộ nhớ JVM trong vòng đời process; dùng cho hàng triệu request sẽ tăng tiêu thụ bộ nhớ.
- File có thể còn được sử dụng bởi process khác hoặc không xóa được do quyền.
- Nếu có directory và nhiều child, thứ tự đăng ký xóa cần được cân nhắc; không nên dựa vào hook để thay mô hình sở hữu workspace rõ ràng.
- Trong hệ thống container/kubernetes, lifecycle có thể bị cắt ngắn hoặc mount volume persistent vẫn còn file rác.

**Khuyến nghị:** dùng `try-with-resources`/`finally` để xóa ngay khi job kết thúc. Có thể cân nhắc `deleteOnExit()` làm lớp phòng vệ phụ cho tool chạy ngắn, nhưng không xem nó là cleanup SLA hoặc cơ chế bảo mật.

## 11. `DELETE_ON_CLOSE`: hữu ích nhưng có giới hạn

`StandardOpenOption.DELETE_ON_CLOSE` yêu cầu xóa file khi channel được đóng, tùy provider hỗ trợ. Có thể dùng cho một số trường hợp file tạm chỉ cần tồn tại trong lúc channel đang mở:

~~~java
// Mã minh họa, temporaryFile đã được tạo bởi ứng dụng.
try (SeekableByteChannel channel = Files.newByteChannel(
        temporaryFile,
        Set.<OpenOption>of(
                StandardOpenOption.WRITE,
                StandardOpenOption.DELETE_ON_CLOSE,
                LinkOption.NOFOLLOW_LINKS))) {
    // Xử lý dữ liệu trong khi channel còn mở.
}
~~~

Những điều không nên giả định:

- Không phải mọi filesystem/provider đều hỗ trợ mọi option.
- Không nên phụ thuộc vào file vẫn còn nhìn thấy dưới pathname trong lúc channel đang mở. Một số hệ thống có thể xóa entry sớm hoặc có semantics khác.
- Nếu thư viện khác cần **mở lại file bằng tên**, `DELETE_ON_CLOSE` có thể làm workflow không hoạt động.
- Không đảm bảo xóa vật lý an toàn mọi bản sao của dữ liệu.
- Không thay việc cleanup các **file khác và thư mục chứa**.

Với pipeline nhiều bước, private directory + explicit cleanup thường dễ kiểm soát hơn.

## 12. Exception, cancel và lỗi cleanup: tránh làm mất nguyên nhân chính

Giả sử xử lý upload ném exception, rồi trong `finally` xóa file cũng thất bại:

~~~java
Path file = Files.createTempFile("upload-", ".tmp");
try {
    importData(file);
} finally {
    Files.deleteIfExists(file);
}
~~~

Nếu `importData` đã ném lỗi và `deleteIfExists` lại ném lỗi khác, lỗi cleanup có thể **che lấp lỗi nghiệp vụ ban đầu** trong cách viết `finally` này. Khi điều đó quan trọng, dùng đối tượng `AutoCloseable` để dọn resource bằng try-with-resources như mục 8: Java quản lý **suppressed exceptions** để giữ nguyên nhân chính.

Có thể xem:

~~~java
try {
    runJob();
} catch (Exception e) {
    System.err.println("Cause: " + e);
    for (Throwable suppressed : e.getSuppressed()) {
        System.err.println("Cleanup also failed: " + suppressed);
    }
}
~~~

Mã minh họa; `runJob` phải tự đóng tài nguyên/cleanup bằng try-with-resources theo policy. Trong production, log lỗi không nên chứa nội dung input nhạy cảm.

### I/O bất đồng bộ và process con

Nếu file tạm đang dùng bởi `AsynchronousFileChannel`, process khác hoặc background task, việc đóng wrapper ở thread chính **không chứng minh** mọi thao tác pending đã hoàn tất và kết quả đã được tiêu thụ. Cần phối hợp lifecycle:

~~~text
CREATE TEMP -> SUBMIT WORK -> WAIT / CANCEL / DRAIN
                              |
                              v
                     CLOSE ALL FILE HANDLES
                              |
                              v
                      DELETE TEMP WORKSPACE
~~~

Thời điểm xóa chỉ nên đến khi không còn bên hợp lệ cần sử dụng file. Nếu job bị hủy, hủy tác vụ trước, đợi hoặc cô lập đúng cách, rồi cleanup với timeout/giám sát phù hợp.

## 13. Lỗi cleanup trong môi trường thực tế

| Vấn đề | Nguyên nhân có thể | Phản ứng phù hợp |
|---|---|---|
| `DirectoryNotEmptyException` | Child chưa xóa, process khác vừa tạo file | Kiểm tra ownership, duyệt đúng thứ tự, hạn chế writer |
| `AccessDeniedException` | Quyền/ACL không cho xóa hoặc file đang được giữ | Đóng handle, kiểm policy, ghi nhận lỗi |
| `NoSuchFileException` | Một tác nhân khác đã xóa | `deleteIfExists` khi điều đó hợp lệ |
| `FileSystemException` | Volume lỗi, file busy, provider constraint | Báo lỗi vận hành và retry có giới hạn nếu an toàn |
| Disk full | Ghi file/metadata không đủ chỗ | Giới hạn quota, fail request, cleanup các file đã tạo |
| Process crash/KILL | `finally`/shutdown hook không chạy | Startup sweeper theo ownership và TTL, ngoài luồng nghiệp vụ |
| Symlink xuất hiện trong temp tree | Pathname có thể đã bị thay | Tránh follow link, xem xét đối thủ cùng UID, dùng directory handles khi cần |
| Thư viện giữ file handle | Windows có thể không cho xóa khi đang mở | Đóng toàn bộ readers/writers/process trước cleanup |

**Không khuyến khích retry xóa vô hạn.** Retry chỉ có ích nếu lỗi có thể hồi phục (file còn tạm thời đang được dùng) và có deadline rõ. Lỗi permission do chính sách sai cần sửa ownership/ACL, không phải tăng số lượt thử.

## 14. Xử lý dữ liệu nhạy cảm khi ghi vào file tạm

Một file tạm chứa token hoặc thông tin định danh nên có chính sách rõ:

1. **Data minimization:** có cần lưu ra đĩa không? Nếu dữ liệu nhỏ và thời gian xử lý ngắn, có thể xử lý trong memory.
2. **Quyền/identity:** chạy với service account riêng, private temp root, quyền file owner-only hoặc ACL tương đương đã kiểm chứng.
3. **Không lộ tên:** không để thông tin nhạy cảm vào prefix/suffix, log path hay exception message cho người dùng cuối.
4. **Không ghi lại quá nhiều:** không tạo bản sao trung gian không cần thiết.
5. **Giới hạn dung lượng:** đặt max bytes per request, per job, per worker và quota toàn scratch volume.
6. **Quy định thời gian lưu:** giữ file ngắn nhất có thể, dọn ngay sau completion/abort, xử lý orphaned files theo TTL.
7. **Mã hóa nếu cần:** cân nhắc mã hóa đĩa hoặc từng file khi threat model bao gồm mất/đánh cắp thiết bị, snapshot hoặc backup.
8. **Hạn chế thư viện bên thứ ba:** khi dùng công cụ chuyển đổi ảnh/PDF/ZIP, xác định thư viện có tự tạo tệp tạm ở đâu, có tôn trọng TMPDIR và cleanup không.

### Delete không phải secure erase

`Files.delete(...)` xóa một entry filesystem. Trên SSD, copy-on-write filesystem, journal, snapshot, backup hoặc storage ảo hóa, dữ liệu có thể vẫn tồn tại trong các block hoặc bản sao. Đừng hứa “gọi delete là không thể phục hồi”. Với dữ liệu mật, kết hợp encryption-at-rest, quản lý key, chính sách retention và hủy key khi cần.

## 15. Upload và giải nén: chống lạm dụng ổ đĩa

Một hệ thống nhận upload cần làm hơn việc tạo tên file khó đoán:

- Chặn kích thước HTTP request trước khi ghi xuống scratch.
- Áp dụng quota số file và tổng byte trên mỗi request/user/job.
- Với ZIP, chống zip bomb: giới hạn tổng dữ liệu sau giải nén, tỷ lệ nén, số entry, độ sâu directory và thời gian xử lý.
- Chống path traversal: entry name như `../../secret.txt` không được phép thoát ra khỏi private temp root.
- Không dựa duy nhất vào `Path.normalize().startsWith(root)` khi đối thủ có thể kiểm soát symlink và thư mục con; cần kiểm tra đường dẫn thực/handle cùng chính sách chống TOCTOU.
- Không sao chép tên file upload nguyên văn thành tên file trên đĩa. Dùng tên server-generated và lưu tên hiển thị như metadata đã sanitize.
- Sau lỗi import hoặc format parsing, dọn toàn bộ workspace mà job sở hữu, không để lại file chứa dữ liệu vừa upload.

Đoạn kiểm tra **cơ bản** chống traversal theo chuỗi đường dẫn:

~~~java
// Minh họa: root là thư mục private đã chuẩn hóa,
// entryName lấy từ archive, root không cho bên lạ sửa.
Path target = root.resolve(entryName).normalize();
if (!target.startsWith(root)) {
    throw new SecurityException("Archive path escapes workspace");
}
~~~

Đây **chưa đủ chống symlink race** nếu untrusted code có thể đặt symlink/đổi directory trong subtree. Cần lớp kiểm soát thêm, không dùng đoạn này làm bằng chứng security hoàn chỉnh cho extractor nhận nội dung thù địch.

## 16. Dọn file tạm tồn dư sau crash: startup sweeper

Không phải mọi ứng dụng đều có thể đảm bảo `finally` chạy. Có thể xây một tiến trình dọn **orphaned workspace** theo thời hạn:

~~~text
scratch-root/  (root được dịch vụ quản lý)
  job-<generated>/        createdAt=...
  job-<generated>/        createdAt=...
  job-<generated>/        createdAt=...

Sweeper (ngoài đường xử lý request):
  -> liệt kê workspace thuộc ứng dụng
  -> bỏ qua job đang có lease/heartbeat hợp lệ
  -> chọn thư mục hết TTL an toàn
  -> kiểm tra ownership/root boundaries
  -> xóa cây với quy tắc no-follow và giới hạn
  -> log failures để cảnh báo
~~~

**Không xóa mọi thư mục có prefix giống nhau** trong một folder shared; file do job khác hoặc người dùng khác có thể trùng prefix. Sweeper cần quyền sở hữu rõ ràng (thư mục parent ứng dụng kiểm soát, metadata/lease đáng tin, account riêng) và chính sách chống xóa nhầm.

Nếu ứng dụng có nhiều instance cùng chia sẻ scratch volume, mỗi instance/job nên có namespace riêng và cơ chế fencing/lease nếu bắt buộc; TTL một mình có thể xóa file của job đang chạy rất lâu.

## 17. Những nhầm lẫn thường gặp

| Nhầm lẫn | Thực tế |
|---|---|
| “Tên khó đoán đủ bảo mật tệp tạm” | Cần quyền và parent directory đáng tin. |
| “Files.createTempFile chỉ tạo tên” | Phương thức tạo file thực sự. |
| “Đã createTempFile rồi thì dùng CREATE_NEW khi mở lại” | CREATE_NEW sẽ báo file đã tồn tại. |
| “0600 bảo vệ file ngay cả khi parent cho attacker thay entry” | Parent ghi được có thể gây pathname race. |
| “Tạo file rồi chmod tương đương set quyền lúc tạo” | Có thể có cửa sổ lộ quyền; dùng FileAttribute khi tạo. |
| “POSIX permission string áp dụng mọi Windows filesystem” | Windows thường dùng ACL/provider khác. |
| “NOFOLLOW_LINKS chặn mọi đường tấn công filesystem” | Không chặn hết các thành phần cha, hardlink hoặc đổi directory. |
| “Files.isSymbolicLink rồi open là race-free” | Có TOCTOU giữa check và use. |
| “deleteOnExit chắc chắn xóa khi crash” | Không chạy khi kill/OS crash và file tồn tại suốt runtime. |
| “deleteIfExists nuốt mọi lỗi” | Vẫn có thể ném IOException/AccessDenied. |
| “Đóng channel là xóa mọi file” | Không; close và delete là các thao tác riêng. |
| “DELETE_ON_CLOSE luôn dùng được trên mọi provider” | Có thể unsupported, ảnh hưởng khả năng mở lại file. |
| “walkFileTree luôn an toàn trước concurrent attacker” | Pathname có thể bị thay; dùng directory handle nếu cần. |
| “Xóa file xong nghĩa là secure erase” | Dữ liệu có thể còn trong block/snapshot/backup. |
| “Một request được xóa toàn bộ thư mục tmp” | Chỉ xóa subtree chính request tạo và sở hữu. |
| “Mỗi request có thể dùng vô hạn scratch space” | Phải có quota, limit và cơ chế cleanup. |

## 18. Checklist triển khai

1. Xác định dữ liệu có nhạy cảm hay không và yêu cầu thời gian lưu.
2. Chọn parent scratch root do dịch vụ quản lý, giới hạn quyền ghi/traverse.
3. Tạo private temp directory riêng cho job nếu xử lý nhiều bước.
4. Dùng `Files.createTempFile/createTempDirectory` thay tên cố định hoặc check-then-create.
5. Thiết lập `FileAttribute` lúc tạo nếu filesystem hỗ trợ, xác minh quyền thực tế.
6. Với Windows, thiết kế/kiểm tra ACL; nếu không đáp ứng chính sách bảo mật, fail closed.
7. Không lưu thông tin bí mật trong tên file hay log đường dẫn.
8. Tránh symlink TOCTOU; `NOFOLLOW_LINKS` chỉ là một lớp phòng vệ.
9. Dùng try-with-resources đóng streams/channels và cleanup cả khi exception.
10. Với async/process con, chỉ xóa sau khi ownership được trả về hoặc operation được cô lập đúng cách.
11. Dọn thư mục con trước parent, không follow symlink, không nhận path-to-delete từ request.
12. Ghi nhận và xử lý lỗi cleanup; không bỏ qua lỗi hoặc retry vô hạn.
13. Đặt giới hạn số file/byte/thời gian; chống ZIP bomb và path traversal.
14. Dùng cleanup ngay sau job; không dựa vào deleteOnExit làm phương án chính.
15. Có sweeper cho orphaned workspaces sau crash và tránh xóa job còn hoạt động.
16. Kiểm thử Windows/Linux/container và filesystem sản xuất, không suy luận mọi provider có cùng khả năng.

## 19. Câu hỏi tự kiểm tra

1. `Files.createTempFile(...)` chỉ trả tên hay tạo file? **Nó tạo file thực sự.**
2. Nếu parent shared writable, tên file ngẫu nhiên có đủ chống đọc/tráo không? **Không; cần quyền/ownership và kiểm soát directory.**
3. Mode POSIX thường phù hợp cho file chứa dữ liệu riêng tư? **`rw-------` (0600)**.
4. Mode POSIX thường phù hợp cho thư mục scratch chỉ owner truy cập? **`rwx------` (0700)**.
5. Vì sao truyền `asFileAttribute(...)` ngay khi tạo? **Tránh khoảng hở trước khi set quyền sau tạo.**
6. Có thể áp dụng POSIX mode trực tiếp cho mọi Windows ACL filesystem không? **Không.**
7. Đã `createTempFile` rồi mở bằng `CREATE_NEW` có đúng không? **Không, file đã tồn tại.**
8. `NOFOLLOW_LINKS` có chặn mọi kiểu TOCTOU không? **Không, chỉ một lớp kiểm soát.**
9. `Files.deleteIfExists(...)` có luôn thành công nếu file còn mở không? **Không.**
10. `deleteOnExit()` có chạy khi JVM bị kill cưỡng bức không? **Không được bảo đảm.**
11. `walkFileTree` mặc định có tự đi theo symbolic links không? **Không.**
12. Vì sao xóa children trước khi xóa thư mục? **Directory còn entry thường không được xóa.**
13. Tại sao `try-with-resources` tốt cho cleanup hơn `finally` đơn giản? **Nó quản lý close và suppressed exceptions.**
14. `Files.delete` có chứng minh secure erase trên SSD/snapshot không? **Không.**
15. Nếu job bị hủy giữa async write, có nên xóa file ngay khi background task vẫn dùng? **Không; cần phối hợp completion/cancel/close và ownership.**
16. Cần gì để dọn file sau crash khi `finally` không chạy? **Sweeper/TTL có ràng buộc ownership và tránh xóa nhầm.**

## 20. Tham khảo chính thức

- [Java SE 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): `createTempFile`, `createTempDirectory`, `newByteChannel`, `delete`, `deleteIfExists`, `walkFileTree`.
- [Java SE 17 — PosixFilePermissions](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFilePermissions.html): `fromString` và `asFileAttribute`.
- [Java SE 17 — PosixFileAttributeView](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFileAttributeView.html): quyền, owner và thuộc tính POSIX.
- [Java SE 17 — AclFileAttributeView](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/AclFileAttributeView.html): chính sách ACL trên provider tương ứng.
- [Java SE 17 — SecureDirectoryStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html): thao tác relative-to-directory an toàn hơn trước pathname races khi provider hỗ trợ.
- [Java SE 17 — StandardOpenOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html): `CREATE_NEW`, `TRUNCATE_EXISTING`, `DELETE_ON_CLOSE`.
- [Java SE 17 — LinkOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html): `NOFOLLOW_LINKS`.
- [Java SE 17 — File.deleteOnExit](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/File.html#deleteOnExit()): giới hạn của cleanup khi JVM kết thúc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-io-design-007">Không ghi đè dữ liệu ngoài ý muốn khi mở hoặc copy</a>

<details>
<summary>Xem chi tiết</summary>

# Không ghi đè dữ liệu ngoài ý muốn khi mở hoặc copy tệp trong Java

Mất dữ liệu khi xử lý file thường không bắt đầu từ một lệnh xóa tường minh. Chỉ cần mở một đường dẫn bằng API có mặc định **cắt ngắn nội dung**, dùng `REPLACE_EXISTING` sai chỗ hoặc ghi vào một file đã tồn tại ở vị trí byte 0, ứng dụng có thể làm hỏng dữ liệu người dùng dù không hề gọi `Files.delete()`. Trong Java I/O và NIO.2, **chọn đúng chế độ tạo/mở tệp, kiểm tra quyền sở hữu và xử lý lỗi giữa chừng** là những bước quan trọng của thiết kế an toàn.

Bài này giải thích khi nào Java tự tạo, cắt ngắn, ghi đè hoặc từ chối đích đã tồn tại; phân biệt `CREATE` với `CREATE_NEW`, `TRUNCATE_EXISTING` với `APPEND`, hành vi của `Files.copy()`, giới hạn của bước `Files.exists()` trước khi ghi, và cách giảm rủi ro khi cần cập nhật một file quan trọng.

Mục tiêu là chọn chiến lược **“không thay nội dung hiện có trừ khi đã có chủ đích”**, chứ không phải giả định mọi thao tác file đều an toàn chỉ vì không khai báo tùy chọn thay thế.

## 1. Định nghĩa đúng các dạng “ghi đè”

Có ít nhất bốn tình huống khác nhau:

1. **Truncate khi mở:** File có nội dung bị đưa về độ dài 0 ngay lúc mở, trước khi ứng dụng kịp ghi dữ liệu mới.
2. **Overwrite tại offset:** File vẫn giữ kích thước ban đầu hoặc được kéo dài, nhưng một số byte đang có bị thay bởi byte mới, thường khi `position = 0`.
3. **Replace file object/đường dẫn đích:** Thao tác copy/move thay tệp đang nằm ở đường dẫn đích bằng tệp khác, có thể làm mất nội dung hoặc metadata cũ.
4. **Ghi ra ngoài vùng dự kiến:** Đường dẫn bị chuẩn hóa sai hoặc theo symbolic link làm dữ liệu được ghi sang một file khác với mục tiêu nghiệp vụ.

Để tránh mất dữ liệu phải phân biệt cả bốn. Chẳng hạn `StandardOpenOption.WRITE` **không tự xóa sạch file khi mở**, nhưng một lần `write()` tại offset 0 vẫn có thể **ghi đè các byte đầu**. Ngược lại `TRUNCATE_EXISTING` làm rỗng file ngay khi mở thành công, kể cả khi phần thân `try` chưa ghi byte nào.

## 2. Bảng hành vi mở và ghi: những mặc định nguy hiểm

| API / cấu hình | Nếu file đích đã tồn tại | Nếu file chưa tồn tại | Rủi ro |
| --- | --- | --- | --- |
| `Files.newOutputStream(path)` | **Cắt ngắn file** khi mở | Tạo file mới | Dễ mất nội dung vì mặc định tương đương `CREATE + TRUNCATE_EXISTING + WRITE` |
| `Files.newBufferedWriter(path, charset)` | **Cắt ngắn file** khi mở | Tạo file mới | Cũng là mặc định tạo mới hoặc truncate |
| `Files.write(path, bytes)` / `Files.writeString(path, text)` | **Thay nội dung file** (mặc định truncate) | Tạo file mới | Không dùng trực tiếp với tên có thể tồn tại nếu cần bảo toàn |
| `new FileOutputStream(file)` | **Cắt ngắn file** | Tạo file | Mặc định không phải append |
| `new FileOutputStream(file, true)` | Mở để ghi nối | Tạo file nếu thiếu | Có thể thêm dữ liệu vào file không mong muốn |
| `FileChannel.open(path)` | Mở chỉ đọc | Thất bại nếu thiếu | Không tự cho phép ghi |
| `FileChannel.open(path, WRITE)` | Cho phép ghi vào file cũ, vị trí mới mở là 0 | Thường thất bại nếu file thiếu | Ghi đè từ đầu khi gọi `write()` |
| `FileChannel.open(path, WRITE, CREATE)` | Giữ nội dung khi mở; `write()` vẫn có thể ghi đè | Tạo file | Không phải bảo đảm “chỉ tạo nếu thiếu và không bao giờ sửa file cũ” |
| `FileChannel.open(path, WRITE, CREATE, TRUNCATE_EXISTING)` | **Cắt về 0 byte khi mở** | Tạo file | Chỉ phù hợp khi cố ý thay toàn bộ dữ liệu |
| `FileChannel.open(path, WRITE, CREATE_NEW)` | **Thất bại, không mở để ghi** | Tạo file mới | Thích hợp cho mục tiêu “không đụng file đã có” |
| `FileChannel.open(path, WRITE, APPEND)` | Ghi nối cuối | Thất bại nếu thiếu, trừ khi thêm `CREATE` | Không có nghĩa khóa độc quyền giữa nhiều writer |

Các mô tả mặc định dùng các file provider Java thông thường; thao tác cụ thể vẫn có thể thất bại vì quyền, loại đường dẫn, hệ thống tệp hoặc lỗi I/O. Khi mục tiêu là **không bao giờ sửa file đã có**, hãy đặt policy bằng thao tác tạo độc quyền như `CREATE_NEW`, thay vì trông cậy vào việc mở với `CREATE`.

### Vì sao Files.writeString có thể nguy hiểm?

Đoạn mã:

```java
Files.writeString(Path.of("config.txt"), "new");
```

nếu `config.txt` đã tồn tại và ghi được, API mặc định **tạo nếu thiếu và truncate nếu đã có**. Một file cấu hình dài có thể bị biến thành chỉ ba byte `new`. Nếu mục tiêu là chỉ tạo file mới, hãy ghi rõ:

```java
Files.writeString(
        Path.of("config.txt"),
        "new",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
```

Đây là **đoạn minh họa** cần import `Files`, `Path`, `StandardCharsets`, `StandardOpenOption` và xử lý `IOException`. Nếu file đã tồn tại, thao tác thất bại, thường với `FileAlreadyExistsException`, thay vì ghi đè.

## 3. CREATE và CREATE_NEW khác nhau ở đâu?

`CREATE` nghĩa là **tạo file khi chưa có**, nhưng nếu file đã tồn tại và channel được phép ghi, chương trình vẫn có thể mở file đó để sửa. Ngược lại, `CREATE_NEW` yêu cầu **tạo một file mới**; nếu đường dẫn đã tồn tại thì thất bại. Kiểm tra tồn tại và tạo mới được thực hiện **nguyên tử đối với nhau** theo hợp đồng API.

```java
// Minh họa: path là Path được kiểm soát, xử lý IOException ở caller.
try (FileChannel channel = FileChannel.open(
        path,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE)) {
    // Chỉ tới đây nếu tạo file mới thành công.
}
```

Đối với `Files.newOutputStream()`:

```java
// Minh họa: không cấp quyền ghi vào file đã có.
try (OutputStream out = Files.newOutputStream(
        path,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE)) {
    out.write(payload);
}
```

`payload` phải là `byte[]` do caller chuẩn bị; đây là đoạn code minh họa, chưa phải chương trình độc lập.

Một số đặc điểm cần ghi nhớ:

- `CREATE_NEW` từ chối **đường dẫn đã có**, kể cả đường dẫn đang là **symbolic link** dù đích link không còn tồn tại. Đặc điểm này giúp tránh thao tác “theo symlink cũ rồi ghi vào chỗ khác” trong ca tạo mới.
- `CREATE_NEW` không tự tạo **thư mục cha**. Nếu `parent` thiếu, lệnh vẫn thất bại; chỉ tạo thư mục cha khi đó là đường dẫn được phép.
- `CREATE_NEW` không xác thực rằng đường dẫn cha không bị thay đổi đồng thời bởi tác nhân khác. Trong thư mục công cộng hoặc có người khác sửa, cần biện pháp ownership và bảo vệ đường dẫn ở cấp hệ thống tệp.
- Nếu file cần tạo chứa nội dung nhạy cảm, còn phải chọn **quyền truy cập của file mới**, chính sách `FileAttribute`/umask và nơi lưu trữ thích hợp.
- `CREATE_NEW` giải quyết race **tồn tại so với tạo** tại đường dẫn đích, nhưng không biến một chuỗi nhiều lệnh ghi thành giao dịch nguyên tử hoàn chỉnh.

### Files.createFile cũng từ chối tên có sẵn

```java
Path file = Files.createFile(target);
```

`Files.createFile()` là thao tác tạo file mới nguyên tử nếu chưa có, có thể nhận `FileAttribute<?>...` khi hệ thống hỗ trợ. Tuy nhiên, tạo trước một file rỗng rồi gọi `Files.writeString(file, text)` **không tự bảo đảm không có writer khác sửa file giữa hai bước**, và `Files.writeString` mặc định vẫn truncate file đang mở. Nếu cần vừa khẳng định quyền tạo mới vừa ghi dữ liệu, thường đơn giản hơn khi **mở trực tiếp với `CREATE_NEW + WRITE` rồi ghi qua chính stream/channel đó**.

## 4. Kiểm tra Files.exists() trước khi ghi không đủ an toàn

Một anti-pattern:

```java
if (Files.notExists(target)) {
    Files.writeString(target, content);
}
```

Giữa lệnh `notExists()` và `writeString()`, luồng hoặc tiến trình khác có thể tạo file cùng tên. Lệnh ghi phía sau dùng mặc định truncate và có thể **ghi đè file vừa xuất hiện**. Đây là lỗi **TOCTOU** (*time-of-check to time-of-use*).

```text
Thời điểm | Process A                          | Process B
--------- | ---------------------------------- | -------------------
T1        | notExists(target) -> true         |
T2        |                                    | tạo target, ghi "B"
T3        | writeString(target, "A")           |
T4        | target chứa "A" (mất dữ liệu "B")   |
```

Cách đúng khi nghiệp vụ yêu cầu “không tạo đè” là dùng một thao tác có **điều kiện tồn tại gắn với việc tạo**:

```java
try {
    Files.writeString(target, content, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE);
} catch (FileAlreadyExistsException exists) {
    // Không ghi đè: báo trùng tên hoặc chọn tên mới theo quy tắc nghiệp vụ.
}
```

Đây là đoạn minh họa cần khai báo biến `target` và `content`, import các kiểu và xử lý `IOException` khác. **Không chuyển ngay sang `TRUNCATE_EXISTING` trong nhánh catch chỉ để “cho chạy thành công”**, vì như vậy phá bỏ chính sách không ghi đè.

`Files.exists()` vẫn hữu ích để hiển thị thông tin cho người dùng hoặc chuẩn bị UI, nhưng **không dùng kết quả kiểm tra cũ làm bảo đảm độc quyền ghi**. `notExists()` trả `false` cũng có thể có nghĩa là không xác định được trạng thái, không nhất thiết file đang tồn tại.

## 5. Files.copy(): mặc định từ chối target đã tồn tại

Khi sao chép bằng Java NIO:

```java
Files.copy(source, target);
```

Với `Path source` và `Path target`, **nếu file đích đã tồn tại thì thao tác copy thông thường thất bại** (thường `FileAlreadyExistsException`), trừ các trường hợp đặc biệt do API quy định như hai đường dẫn thực sự tham chiếu đến cùng file. Không cần kiểm tra `Files.exists(target)` trước khi gọi để tránh ghi đè.

Nếu bạn cố ý cho phép thay thế:

```java
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
```

thì đó là một quyết định **phá hủy dữ liệu cũ có chủ đích**, cần phê duyệt và xử lý lỗi. `Files.copy()` **không được bảo đảm atomic**, kể cả khi không dùng `REPLACE_EXISTING`. Nếu xảy ra `IOException` giữa lúc copy, file đích mới hoặc file đích đang bị thay có thể nằm ở trạng thái dở dang. Vì vậy **“không thay file cũ”** và **“file mới luôn hoàn chỉnh”** là hai yêu cầu khác nhau.

### COPY_ATTRIBUTES và NOFOLLOW_LINKS không phải “chế độ an toàn tuyệt đối”

Một lời gọi:

```java
Files.copy(source, target,
        StandardCopyOption.COPY_ATTRIBUTES,
        LinkOption.NOFOLLOW_LINKS);
```

là **đoạn minh họa**. `COPY_ATTRIBUTES` yêu cầu copy một số metadata phù hợp provider và có thể thất bại. `NOFOLLOW_LINKS` điều khiển việc **không đi theo symbolic link ở source** trong thao tác copy; khi source là symbolic link, API có thể sao chép link thay vì nội dung đích liên kết nếu hỗ trợ. Cả hai **không thay thế** chính sách tạo đích an toàn, kiểm soát quyền và bảo vệ khỏi path traversal.

Nếu cần tránh ghi đè, hãy **không thêm `REPLACE_EXISTING`** và kiểm tra/giữ nguyên nội dung đích khi nhận `FileAlreadyExistsException`.

### Files.copy(InputStream, Path) cũng cần thận trọng

```java
Files.copy(networkInput, target);
```

Đây là mẫu minh họa với `networkInput` kiểu `InputStream`. Overload này không dùng `Path source`, nhưng cũng có tùy chọn `REPLACE_EXISTING` và có thể để lại dữ liệu đã copy một phần nếu lỗi trong quá trình đọc/ghi. Khi input là dữ liệu không tin cậy, cần giới hạn dung lượng/tốc độ và không sử dụng tên file do đầu vào cung cấp trực tiếp để ghi ra ngoài vùng cho phép.

## 6. Chương trình Java 17+ hoàn chỉnh: không ghi đè khi copy hoặc tạo

Ví dụ tự tạo thư mục tạm nên không chạm vào file thật của người học. Chương trình:

- Ghi một file nguồn có nội dung `NEW DATA` và một đích đã tồn tại có nội dung `KEEP`.
- Thử `Files.copy(source, existing)` mà không có `REPLACE_EXISTING`; xác nhận từ chối và đích giữ nguyên.
- Copy đến một tên mới chưa tồn tại.
- Tạo một file khác bằng `FileChannel.open(..., CREATE_NEW, WRITE)`; thử tạo lại lần hai và xác nhận từ chối mà nội dung không đổi.
- Đóng mọi channel trước khi xóa file/thư mục tạm.

Tạo file **`SafeOpenCopyDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class SafeOpenCopyDemo {
    private SafeOpenCopyDemo() {}

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("safe-copy-demo-");
        Path source = directory.resolve("source.txt");
        Path existing = directory.resolve("existing.txt");
        Path copied = directory.resolve("copied.txt");
        Path created = directory.resolve("created.txt");

        try {
            Files.writeString(source, "NEW DATA\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            Files.writeString(existing, "KEEP\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

            try {
                Files.copy(source, existing);
                throw new AssertionError("Copy should not replace existing");
            } catch (FileAlreadyExistsException expected) {
                System.out.println("copy-existing=refused");
            }
            System.out.println("existing-still="
                    + Files.readString(existing, StandardCharsets.UTF_8)
                            .strip());

            Files.copy(source, copied);
            System.out.println("copied="
                    + Files.readString(copied, StandardCharsets.UTF_8)
                            .strip());

            try (FileChannel channel = FileChannel.open(created,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(
                        "SAFE\n".getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) {
                    if (channel.write(bytes) == 0) {
                        throw new IOException("No write progress");
                    }
                }
            }
            System.out.println("created="
                    + Files.readString(created, StandardCharsets.UTF_8)
                            .strip());

            try (FileChannel ignored = FileChannel.open(created,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                throw new AssertionError("Create should refuse existing");
            } catch (FileAlreadyExistsException expected) {
                System.out.println("create-new-existing=refused");
            }
            System.out.println("created-still="
                    + Files.readString(created, StandardCharsets.UTF_8)
                            .strip());
        } finally {
            Files.deleteIfExists(created);
            Files.deleteIfExists(copied);
            Files.deleteIfExists(existing);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac SafeOpenCopyDemo.java
java SafeOpenCopyDemo
```

Kết quả khi các thao tác trên thư mục tạm thành công trên filesystem thông thường:

```text
copy-existing=refused
existing-still=KEEP
copied=NEW DATA
created=SAFE
create-new-existing=refused
created-still=SAFE
```

Phân tích:

1. `Files.writeString(..., CREATE_NEW, WRITE)` tạo hai file đầu tiên mà không có khả năng vô tình truncate một file trùng tên.
2. `Files.copy(source, existing)` mặc định không thay file đích `existing.txt`; exception được bắt rõ ràng, không nuốt các `IOException` khác.
3. `Files.copy(source, copied)` thành công vì `copied.txt` chưa tồn tại. Nó vẫn không phải thao tác copy nguyên tử đối với lỗi I/O giữa chừng.
4. `FileChannel.open(created, CREATE_NEW, WRITE)` thực hiện mở file mới một cách loại trừ, sau đó vòng lặp `ByteBuffer.hasRemaining()` bảo đảm hoàn tất bytes được chấp nhận ghi.
5. Cố mở `created.txt` lần nữa với `CREATE_NEW` báo `FileAlreadyExistsException`, để nguyên file cũ `SAFE`.
6. `try-with-resources` đóng channel trước khi cleanup. `finally` cố xóa các file tạm thuộc sở hữu ví dụ.

Ví dụ cho thấy **bảo vệ file đã tồn tại**; nó không chứng minh rằng toàn bộ tiến trình copy/ghi sẽ tự rollback khi mất điện hoặc lỗi I/O. Trong ứng dụng thật, nếu thao tác thất bại sau khi đã tạo file mới, phải có chính sách phát hiện và dọn **chính file mới mà ứng dụng sở hữu**, không xóa bừa đường dẫn có thể đã bị một tác nhân khác thay thế.

## 7. Nếu vẫn cần thay nội dung file cũ thì làm thế nào?

Có những nhu cầu hợp lệ như cập nhật cấu hình, thay bản export hoặc ghi phiên bản mới của một file báo cáo. Khi đó **không ghi đè ngoài ý muốn** nghĩa là việc thay thế phải **có chủ đích, được kiểm soát và có phương án phục hồi**, không nhất thiết cấm thay thế trong mọi trường hợp.

### Tránh truncate file đích ngay khi bắt đầu

Cách rủi ro:

```java
Files.writeString(target, newlyGeneratedContent);
```

Nếu file cũ quan trọng, `Files.writeString()` mặc định truncate target và tiến trình có thể chết trước khi ghi xong. Thay vì ghi thẳng vào target, cân nhắc chiến lược:

1. Tạo **file tạm mới** trong cùng filesystem và thư mục thích hợp; tên file tạm phải được cấp phát an toàn, ví dụ `Files.createTempFile(parent, prefix, suffix)`.
2. Ghi dữ liệu mới hết vào file tạm, kiểm tra tính hợp lệ, đóng stream/channel, tùy yêu cầu có thể `FileChannel.force(true)` để yêu cầu đồng bộ nội dung/metadata.
3. Chỉ khi nội dung đã sẵn sàng, thực hiện một thao tác `Files.move()` từ file tạm sang file đích **theo chính sách thay thế đã được cho phép**.
4. Nếu cần tính nguyên tử, thử `StandardCopyOption.ATOMIC_MOVE` trên filesystem hỗ trợ, xử lý `AtomicMoveNotSupportedException` rõ ràng.
5. Ghi log kết quả, kiểm thử crash/recovery và cleanup file tạm còn sót lại.

**Quan trọng:** `ATOMIC_MOVE` không có nghĩa là “tuyệt đối không ghi đè”. Khi `ATOMIC_MOVE` được chọn, **hành vi nếu target đã tồn tại là implementation-specific** và các option khác có thể bị bỏ qua theo hợp đồng `Files.move()`. Do đó không dùng `Files.move(temp, target, ATOMIC_MOVE)` làm bằng chứng chung rằng target cũ chắc chắn không thể bị thay. Nếu yêu cầu **tuyệt đối không chạm file đích đã có**, lựa chọn tạo độc quyền `CREATE_NEW` hoặc cơ chế publish có hợp đồng no-replace được xác minh trên nền tảng sẽ phù hợp hơn.

### Ví dụ thay thế có chủ đích và phải kiểm soát

```java
// Phác thảo: ứng dụng đã kiểm tra quyền thay thế target,
// temporary chứa nội dung hoàn chỉnh, cùng FileStore phù hợp.
try {
    Files.move(temporary, target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
} catch (AtomicMoveNotSupportedException unsupported) {
    // Chọn chính sách riêng: thất bại an toàn hoặc fallback đã chấp thuận.
}
```

Đây là phác thảo **không độc lập**; đòi hỏi biến `temporary`/`target`, xử lý `IOException` và chính sách fallback. Với `ATOMIC_MOVE`, việc thay thế target có tồn tại hay không còn tùy implementation; **không suy ra chỉ việc thêm `REPLACE_EXISTING` vào một atomic move sẽ cho ngữ nghĩa portable đầy đủ**. Trên hệ thống không hỗ trợ atomic move, fallback không nguyên tử có thể làm tăng nguy cơ ứng dụng khác quan sát trạng thái trung gian. Nếu không chấp nhận điều đó, phải **dừng và báo lỗi**, không tự fallback một cách âm thầm.

`FileChannel.force(true)` yêu cầu đồng bộ nội dung/file metadata theo hợp đồng API; nó không bảo đảm toàn bộ thao tác rename hoặc cập nhật thư mục chứa tên file đã bền vững qua mọi sự cố mất điện trên mọi OS/filesystem. Yêu cầu durability nghiêm ngặt cần chiến lược cấp hệ thống và kiểm thử recovery.

## 8. Bảo vệ khỏi path traversal, symbolic link và nhầm thư mục

`CREATE_NEW` bảo vệ trường hợp có file/symlink ngay tại **đường dẫn đích**, nhưng không tự bảo vệ mọi thành phần của đường dẫn hay mọi loại race. Ví dụ ứng dụng định tạo:

```text
/app/export/report.txt
```

Nếu `/app/export` có thể bị thay bằng symbolic link trỏ tới thư mục khác, thao tác tạo tệp vẫn có thể ghi ngoài vùng dự kiến. Cần lựa chọn thư mục gốc mà ứng dụng sở hữu, xác thực và giới hạn đầu vào tên file, cũng như bảo vệ các directory entries khỏi tác nhân không tin cậy.

Một bước chuẩn hóa đường dẫn cơ bản:

```java
// Minh họa: root là thư mục tin cậy, userName lấy từ đầu vào.
Path base = root.toAbsolutePath().normalize();
Path candidate = base.resolve(userName).normalize();
if (!candidate.startsWith(base) || candidate.equals(base)) {
    throw new IllegalArgumentException("Unsafe target path");
}
```

Điều này chặn một số trường hợp lexical `../` trỏ ra ngoài nhưng **không chặn hoàn toàn symlink hoặc race trên các thành phần thư mục**. Đối với môi trường có đối thủ chủ động chỉnh sửa đường dẫn, cần cách truy cập dựa trên thư mục được mở an toàn, quyền filesystem, hoặc `SecureDirectoryStream` nếu provider hỗ trợ. Không tự tuyên bố `normalize() + startsWith()` là giải pháp chống path traversal toàn diện.

Đối với `Files.copy(source, target)`, cũng cần cân nhắc `Files.isSameFile(source, target)` khi thiết kế quy trình: hai đường dẫn có thể tham chiếu cùng một file qua hard link/symlink. Với cùng đối tượng file, một thao tác copy có thể không làm gì theo hợp đồng API; điều này không chứng minh dữ liệu đã được nhân bản thành file độc lập.

### Quyền sở hữu và tập tin tạm

- Chỉ cleanup file mà tiến trình **thực sự tạo và vẫn sở hữu**; không gọi `Files.deleteIfExists(target)` bừa bãi trong `catch` sau thất bại `CREATE_NEW`.
- Đừng tạo file tạm theo tên dễ đoán rồi mở bằng cách có thể truncate một file có sẵn.
- Tránh thư mục tạm chia sẻ không tin cậy cho các thao tác có đường dẫn do người dùng định nghĩa; `Files.createTempFile()` tránh va chạm tên nhưng không tự biến toàn bộ workflow thành không có race.
- Với file nhạy cảm, cần đặt permission cho file và thư mục, nhất là khi dữ liệu được ghi trước khi đổi tên vào nơi công khai.
- Khi dùng `Files.copy(..., REPLACE_EXISTING)`, nhớ việc thay file có thể làm thay metadata hoặc quyền file đích. Kiểm tra nhu cầu bảo toàn attributes/ownership theo provider.

## 9. Khi nào cần APPEND thay vì CREATE_NEW?

Có lúc mục tiêu không phải tạo file mới hay thay toàn bộ file cũ mà là **bổ sung một bản ghi vào log hiện có**:

```java
try (FileChannel channel = FileChannel.open(
        logPath,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND)) {
    ByteBuffer data = StandardCharsets.UTF_8.encode("record\n");
    while (data.hasRemaining()) {
        channel.write(data);
    }
}
```

Đây là đoạn minh họa với `logPath` kiểu `Path`, import `FileChannel`, `ByteBuffer`, `StandardOpenOption`, `StandardCharsets` và xử lý `IOException`. `APPEND` giúp mỗi lần ghi theo ngữ nghĩa cuối file, tránh tình huống mở `WRITE + CREATE` rồi ghi từ byte 0. Nhưng `APPEND` không phải khóa phân tán, không bảo đảm atomicity khi nhiều tiến trình append trên mọi filesystem, và không khôi phục dữ liệu khi lỗi.

Nếu log phải có từng record bất khả phân, hãy quy định framing, đồng bộ giữa writer, kiểm tra kết quả và chiến lược durability phù hợp. Không dùng `APPEND` cùng `TRUNCATE_EXISTING` trong `FileChannel.open()`; tổ hợp này không hợp lệ.

## 10. Xử lý exception đúng để không làm hỏng dữ liệu

| Tình huống | Ngoại lệ thường gặp | Xử lý an toàn |
| --- | --- | --- |
| `CREATE_NEW` gặp tên đã có | `FileAlreadyExistsException` | Giữ nguyên file, trả lỗi trùng tên hoặc sinh tên mới theo chính sách |
| `Files.copy()` gặp đích đã có | `FileAlreadyExistsException` | Không tự retry bằng `REPLACE_EXISTING` |
| Không tồn tại thư mục cha | `NoSuchFileException` hoặc `IOException` | Kiểm tra root được phép, chỉ tạo parent hợp lệ |
| Không có quyền | `AccessDeniedException` hoặc `IOException` | Báo lỗi quyền, không làm fallback sang đường dẫn ngẫu nhiên |
| Hết dung lượng hoặc lỗi giữa khi ghi/copy | `IOException` | Đánh dấu output chưa hoàn chỉnh, dọn tài nguyên owned, không tuyên bố thành công |
| Move nguyên tử không được hỗ trợ | `AtomicMoveNotSupportedException` | Fail-safe hoặc fallback được cho phép rõ ràng |
| Source/target bị người khác thay đổi giữa các bước | Thay đổi kết quả hoặc `IOException` tùy provider | Thiết kế ownership/locking và không dùng pre-check như cam kết |
| Phần đích là thư mục/link bất thường | Ngoại lệ phụ thuộc OS/provider | Xác thực loại file/đường dẫn và quyền, không ghi đè bất chấp |

Phân biệt lỗi **“file đã tồn tại”** với lỗi I/O khác. Một đoạn `catch (Exception ignored) { ... }` quá rộng có thể che mất lỗi copy dở và khiến ứng dụng ghi metadata “thành công” trong khi dữ liệu thực không đầy đủ.

Nếu đã tạo một file mới nhưng ghi bị lỗi, ứng dụng có thể cần loại bỏ **file mới dở dang**. Nhưng cleanup này không được phép xóa file đã tồn tại từ trước hoặc file thuộc người khác chỉ vì trùng tên; mô hình ownership, thư mục riêng hoặc token theo dõi tài nguyên là điều quan trọng.

## 11. Checklist thiết kế thao tác không ghi đè

Trước khi ghi hoặc copy, kiểm tra:

1. **Chính sách:** Tác vụ này chỉ tạo file mới, nối vào file cũ, sửa một phần, hay thay toàn bộ? Không dùng một API cho mọi trường hợp.
2. **Tên đích:** Path được tạo từ root tin cậy? Có chặn đường dẫn ra ngoài hoặc symlink đáng ngờ? Thư mục cha có do ứng dụng sở hữu?
3. **Mở file:** Đã chọn `CREATE_NEW` khi cần tuyệt đối từ chối tên tồn tại? Có vô tình dùng API mặc định truncate?
4. **Copy:** Có chủ động thêm `REPLACE_EXISTING` không? Nếu có, ai đã cho phép mất nội dung cũ?
5. **TOCTOU:** Có dùng `Files.exists()` rồi dựa vào kết quả cũ để mở bằng API nguy hiểm không?
6. **I/O lỗi giữa chừng:** Có ghi vào file tạm, kiểm soát file dở, cleanup đúng đối tượng và thông báo lỗi rõ ràng không?
7. **Atomicity & durability:** Ứng dụng cần tệp đích luôn hoàn chỉnh, cần cấm overwrite tuyệt đối, hay cần giữ dữ liệu qua mất điện? Các yêu cầu này khác nhau.
8. **Đồng thời:** Có nhiều writer hay các tiến trình khác cùng truy cập? Có cần sở hữu độc quyền, locking hoặc version check không?
9. **Quyền và metadata:** File mới có permission phù hợp, file bị thay có cần giữ attributes/owner?
10. **Kiểm thử:** Đã thử file đích có sẵn, symlink, thư mục cha thiếu, quyền bị từ chối, ENOSPC, crash giữa copy, thao tác hai tiến trình đồng thời và hành vi nền tảng triển khai chưa?

## 12. Những lỗi phổ biến cần tránh

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Dùng `Files.writeString(path, text)` với tên file do người dùng cung cấp | Có thể truncate file cũ | `CREATE_NEW + WRITE` nếu chỉ tạo mới |
| Dùng `new FileOutputStream(file)` và nghĩ nó nối cuối | Làm rỗng nội dung cũ | Dùng append rõ ràng nếu đúng nghiệp vụ |
| Dùng `WRITE + CREATE` và nghĩ không bao giờ sửa file đã có | Ghi đè từ offset 0 | Dùng `CREATE_NEW` hoặc ghi offset có chủ đích |
| Kiểm tra `Files.notExists()` trước rồi mới ghi mặc định | Race TOCTOU khiến đè file do tiến trình khác tạo | Dùng một thao tác tạo độc quyền |
| Thêm `REPLACE_EXISTING` khi muốn "copy cho chắc" | Xóa/đổi file đích đã có | Không thêm trừ khi thay thế là nghiệp vụ đã được duyệt |
| Nghĩ `Files.copy()` là atomic | File mới hoặc file thay thế có thể dở khi lỗi | Chiến lược staging + kiểm tra + publish |
| Tin `ATOMIC_MOVE` bảo đảm no-overwrite portable | Target cũ có thể bị thay tùy implementation | Đừng đồng nhất atomic rename với create-if-absent |
| Truyền path từ ZIP/user mà không kiểm tra | Viết ngoài vùng đích dự kiến | Root tin cậy, kiểm tra đường dẫn và symlink/race |
| Xóa `target` ngay trong catch khi `CREATE_NEW` thất bại | Xóa file có sẵn của người khác | Chỉ cleanup những file đã tạo và sở hữu |
| Coi `FileChannel.write()` một lần là đã ghi hết | File mới có thể thiếu byte | Kiểm tra `ByteBuffer.hasRemaining()` |
| Ghi vào cùng file qua hai tên hard link khác nhau | Thay đổi dữ liệu ngoài dự định dù tên khác nhau | Xác minh file identity và quyền |
| Không xử lý `IOException` sau khi tạo file mới | Để lại file hỏng và báo thành công | Cleanup owned partial output, phát lỗi rõ ràng |

**Ghi nhớ:** “Không ghi đè” phải được xác định bằng **hợp đồng của chính thao tác mở/copy**, không bằng một lần kiểm tra tồn tại trước đó. Dùng `CREATE_NEW` để tạo độc quyền và từ chối file đã có; để `Files.copy(source, target)` không có `REPLACE_EXISTING` khi cần giữ nguyên target; tránh mặc định truncate của `Files.writeString()`, `newOutputStream()` và `FileOutputStream`. Khi buộc phải thay file cũ, làm việc với tệp tạm, phương án publish và recovery rõ ràng; đừng nhầm “không ghi đè”, “thao tác nguyên tử” và “ghi bền” là một tính chất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-io-design-008">Kiểm tra tính toàn vẹn bằng hash khi bài toán yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra tính toàn vẹn bằng hash khi bài toán yêu cầu (Java I/O)

Khi ứng dụng đọc, sao chép, tải xuống, giải nén hoặc lưu trữ tệp, câu hỏi quan trọng đôi khi không chỉ là **I/O có kết thúc không**, mà còn là: **“Những byte nhận được có đúng là những byte mình mong đợi không?”**. Nếu bài toán yêu cầu phát hiện nội dung bị thay đổi, mất byte, trộn byte hoặc sai phiên bản, ta có thể dùng **hàm băm mật mã** như SHA-256 để tính **digest** của dữ liệu và so sánh với một digest tham chiếu.

Tuy nhiên, **hash không phải lời giải cho mọi vấn đề an toàn**. Một giá trị SHA-256 chỉ giúp kiểm tra tính khớp khi có **digest tham chiếu đủ đáng tin**; nếu kẻ tấn công có thể thay cả file **lẫn hash đính kèm**, việc so sánh vẫn cho kết quả đúng với dữ liệu đã bị thay. Khi cần xác thực ai tạo dữ liệu, sử dụng HMAC/chữ ký số với quản lý khóa và xác thực nguồn thích hợp.

Bài này trình bày: chọn đúng lúc cần hash, khác biệt giữa hash/CRC/HMAC/chữ ký, cách tính SHA-256 theo luồng bằng `MessageDigest`, `DigestInputStream`, `FileChannel` và `ByteBuffer`, cách kiểm tra sau sao chép, đối phó dữ liệu thay đổi đồng thời, chi phí I/O, bảo mật và bài tập Java 17+.

## 1. Hash là gì và kiểm tra điều gì?

Hàm băm mật mã `H` nhận một chuỗi byte độ dài bất kỳ và tạo ra digest độ dài cố định:

```text
Bytes nguồn                    SHA-256                      Digest 32 byte
"abc" (ASCII)          ──────────────────────>    ba7816bf8f01cfea...
"abd" (ASCII)          ──────────────────────>    a52d159f262b2c6d...

Khi kiểm tra:
actual = SHA-256(bytes đọc thực tế)
expected = digest tham chiếu đã được xác thực/đáng tin
PASS chỉ khi actual và expected khớp nhau.
```

SHA-256 có đầu ra **256 bit = 32 byte**, thường hiển thị thành **64 ký tự hexadecimal**. Đây không phải thuật toán nén có thể đảo ngược: không thể “giải mã hash” để lấy lại file. Một thay đổi nhỏ ở đầu vào thông thường làm digest thay đổi rõ rệt.

Các thuộc tính của một hàm băm mật mã tốt gồm khả năng chống tìm preimage, second preimage và collision trong phạm vi độ an toàn phù hợp; không nên viết phần mềm dựa vào giả định **không bao giờ** có collision về mặt toán học. Với SHA-256, xác suất collision ngẫu nhiên là cực nhỏ trong các quy mô thực tế thông thường, nhưng đánh giá bảo mật còn tùy mô hình đe dọa.

Điều kiện kiểm tra đúng phải rõ:

- **Đối tượng được hash**: raw bytes file, dữ liệu sau giải nén, payload đã giải mã hay serialized representation?
- **Thời điểm**: trước sao chép, sau sao chép, sau tải xuống, trước tiêu thụ hay khi khởi động?
- **Nguồn của expected digest**: manifest đã ký, cơ sở dữ liệu tin cậy, hash độc lập qua kênh an toàn hay chỉ một file `.sha256` tải cùng chỗ không được bảo vệ?
- **Kết quả khi mismatch**: từ chối dùng file, xóa bản tạm, retry từ nguồn tin cậy, cảnh báo hay tiếp tục theo chính sách?

## 2. Khi nào thực sự cần hash?

Hash rất hữu ích khi sự **khớp byte-for-byte** có giá trị đối với quyết định nghiệp vụ:

| Tình huống | Hash giúp gì? | Cần bổ sung gì? |
| --- | --- | --- |
| Tải artifact/plugin từ nguồn phát hành | Kiểm tra file tải có đúng digest phát hành. | Digest phải qua kênh tin cậy hoặc có chữ ký số; không tin file hash tùy ý. |
| Sao chép file quan trọng | So source/copy để phát hiện sai byte hoặc thiếu byte. | Quản lý file thay đổi đồng thời, lỗi ghi, snapshot và việc sử dụng sau kiểm tra. |
| Đồng bộ, lưu trữ, backup | Phát hiện content drift, dedup theo nội dung. | Manifest, versioning, identity, recovery; hash không thay thế backup. |
| Pipeline ingest dữ liệu | Xác minh payload trước khi commit/xử lý. | Giới hạn kích thước, schema validation, auth, transaction. |
| Chuyển dữ liệu qua nhiều bước | Đối chiếu hash đầu vào và đầu ra cùng representation. | Định nghĩa rõ có bước chuyển đổi hợp lệ (nén, mã hóa, newline) hay không. |
| Xác minh cache | Đảm bảo giá trị cache tương ứng nội dung mong đợi. | Key/version, chống race và chiến lược invalidation. |

Không nên tính SHA-256 cho mọi lần đọc file nếu bài toán không cần sự đảm bảo ấy và chi phí CPU/I/O không đáng. Chẳng hạn một bản ghi log tạm không quan trọng có thể chỉ cần kiểm tra kết quả write và retry; một artifact được dùng chạy code hoặc cập nhật phần mềm cần xác thực mạnh hơn một checksum.

**Hash không thay thế** việc kiểm tra số byte thực sự đọc/ghi, `IOException`, close/flush, permission, file format, schema hay input validation. Một file bị lỗi định dạng vẫn có thể có SHA-256 khớp với hash của chính nó.

## 3. Hash, checksum, CRC, HMAC và chữ ký số khác nhau thế nào?

| Công cụ | Mục đích chính | Có chống sửa đổi có chủ đích khi đối thủ thay được dữ liệu và checksum không? |
| --- | --- | --- |
| Tổng byte/kích thước file | Phát hiện thiếu dữ liệu thô sơ. | Không. Hai file khác nhau vẫn cùng kích thước. |
| CRC-32 (`java.util.zip.CRC32`) | Phát hiện lỗi truyền/lưu trữ tình cờ với chi phí thấp. | Không. CRC không phải cryptographic MAC. |
| SHA-256/SHA-512 (`MessageDigest`) | Digest mật mã cho so khớp nội dung. | Chỉ có ý nghĩa xác thực nếu expected digest được bảo vệ đáng tin. |
| HMAC-SHA-256 (`javax.crypto.Mac`) | Xác thực toàn vẹn với **khóa bí mật chung**. | Có, với điều kiện khóa an toàn, protocol đúng và thuật toán còn phù hợp. |
| Chữ ký số (`Signature`) | Xác thực dữ liệu bằng private key, kiểm tra bằng public key được tin cậy. | Có, nếu khóa công khai/chuỗi tin cậy được xác minh đúng. |
| AEAD (ví dụ AES-GCM) | Mã hóa và kiểm tra tag xác thực kèm context dữ liệu. | Có, nếu khóa/nonce/AAD được dùng theo thiết kế đúng. |

Ví dụ **không an toàn** để chứng minh tính xác thực: tải `program.jar` cùng `program.jar.sha256` từ cùng một vị trí mà kẻ tấn công có thể sửa cả hai, sau đó nói file “đã được xác thực”. Kẻ tấn công có thể tạo digest mới cho file giả.

Ví dụ có ý nghĩa hơn: ứng dụng đã có **expected SHA-256 được phân phối qua kênh tin cậy độc lập**, hoặc manifest được ký bởi publisher với khóa công khai đã xác thực trước. Khi so sánh digest, ứng dụng phát hiện bytes nhận không khớp bản đã phát hành.

**CRC-32** phù hợp kiểm tra lỗi ngẫu nhiên của ZIP và nhiều protocol; không dùng CRC thay SHA-256/HMAC cho quyết định bảo mật.

**MD5 và SHA-1** có những vấn đề collision đã biết, không nên lựa chọn cho hệ thống mới có đối thủ chủ động. Đối với kiểm tra toàn vẹn file hiện đại, SHA-256 là mặc định thực dụng; chính sách thuật toán dài hạn có thể cần cập nhật theo yêu cầu tổ chức.

## 4. Java API cơ bản: MessageDigest và SHA-256

`java.security.MessageDigest` là API tiêu chuẩn:

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
digest.update(new byte[] {0x61, 0x62, 0x63});
byte[] result = digest.digest();
String hex = HexFormat.of().formatHex(result);
```

Đây là **snippet** cần import `java.security.MessageDigest`, `java.util.HexFormat` và xử lý `NoSuchAlgorithmException`. Bytes `0x61, 0x62, 0x63` là chuỗi ASCII `abc`. Với Java 17+, `HexFormat` là tiện ích chuẩn để in/đọc hexadecimal.

Hai cách sử dụng:

- **Một lần**: `digest.digest(allBytes)`, chỉ phù hợp khi dữ liệu đã nằm sẵn trong bộ nhớ với kích thước an toàn.
- **Nhiều chunk**: gọi `digest.update(chunk...)` nhiều lần theo **đúng thứ tự byte**, sau đó gọi `digest.digest()` một lần để lấy kết quả.

Ví dụ về tính nối tiếp:

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
digest.update("a".getBytes(StandardCharsets.US_ASCII));
digest.update("bc".getBytes(StandardCharsets.US_ASCII));
byte[] result = digest.digest(); // Cùng SHA-256 với bytes "abc".
```

Phần này là snippet, cần imports và handling checked exception. Không cần nối mọi chunk thành một mảng lớn: `MessageDigest` xử lý trạng thái băm tăng dần.

**Chú ý lifecycle:** `digest()` kết thúc phép tính và reset trạng thái digest theo hợp đồng API. Nếu tiếp tục gọi `update` sau khi đã `digest()` thì đang băm một thông điệp mới, không phải nối tiếp thông điệp cũ. `MessageDigest` cũng không phải một đối tượng tự động thread-safe: dùng instance riêng cho từng luồng/tác vụ trừ khi có đồng bộ hóa rõ ràng.

## 5. Hash của byte, không phải “ký tự nhìn giống nhau”

SHA-256 làm việc trên **byte sequence**. Với text, cùng một câu hiển thị có thể có bytes khác nhau do:

- Charset: UTF-8, UTF-16, legacy code page.
- Unicode normalization: NFC và NFD có thể biểu diễn ký tự có dấu khác nhau.
- Dấu xuống dòng: `\n`, `\r\n`, hoặc file có BOM.
- Khoảng trắng cuối dòng, tabs, encoding khác nhau, byte thừa/thiếu.
- JSON có thay đổi thứ tự key, spacing hoặc number serialization; XML có canonicalization riêng.

Ví dụ:

```java
byte[] a = "abc".getBytes(StandardCharsets.UTF_8);
byte[] b = "abc\n".getBytes(StandardCharsets.UTF_8);
// Hai mảng khác độ dài nên SHA-256 phải được tính độc lập.
```

Đây là snippet. Nếu yêu cầu **tính toàn vẹn file**, hash **raw bytes** như được lưu thực tế; đừng tự đọc text rồi ghi lại để chuẩn hóa mà không có yêu cầu rõ. Nếu bài toán yêu cầu tương đương **về nội dung logic**, hãy định nghĩa quy tắc canonicalization rõ ràng và áp dụng giống nhau ở bên phát hành/nhận trước khi hash.

Với định dạng nén/mã hóa, phải xác định hash là của:

1. **Bytes ZIP/đã nén** (kiểm tra chính xác archive nhận), hay
2. **Bytes entry sau giải nén** (kiểm tra chính xác nội dung gốc), hay
3. **Object đã parse/canonicalize** (kiểm tra tương đương logic).

Ba digest đó không thể thay thế nhau. Một ZIP được tạo lại với timestamp khác có thể có raw archive hash khác dù payload file giống nhau.

## 6. Hash file lớn theo luồng với DigestInputStream

Không dùng `Files.readAllBytes(file)` cho file rất lớn hoặc không tin cậy chỉ để tính SHA-256. Hãy đọc theo chunk và cập nhật digest:

```java
// Snippet: file là Path có quyền đọc và đã được kiểm soát.
MessageDigest digest = MessageDigest.getInstance("SHA-256");
try (InputStream in = new DigestInputStream(
        Files.newInputStream(file), digest)) {
    byte[] buffer = new byte[8192];
    while (in.read(buffer) != -1) {
        // DigestInputStream đã cập nhật digest theo byte thực được đọc.
    }
}
byte[] actual = digest.digest();
```

Đoạn trên cần các imports `java.io.InputStream`, `java.security.DigestInputStream`, `java.security.MessageDigest`, `java.nio.file.Files` và xử lý exceptions. `DigestInputStream` được dùng để cập nhật digest **trong lúc read**, không cần gọi `digest.update(buffer)` thêm lần nữa; gọi thêm sẽ băm bytes **hai lần** và kết quả sai.

Nếu tự dùng InputStream thuần:

```java
try (InputStream in = Files.newInputStream(file)) {
    byte[] buffer = new byte[8192];
    int n;
    while ((n = in.read(buffer)) != -1) {
        if (n > 0) {
            digest.update(buffer, 0, n);
        }
    }
}
```

Đây là snippet dùng `digest` mới, chưa finalize; việc đọc phải có logic phù hợp loại stream. Cần luôn dùng **n**, không update toàn bộ mảng `buffer`: lần đọc cuối thường có ít byte hơn `buffer.length`; phần đuôi chứa byte cũ từ lần trước không thuộc dữ liệu mới.

Kích thước chunk như 8 KiB hoặc 64 KiB ảnh hưởng hiệu năng, nhưng không ảnh hưởng kết quả nếu thứ tự và tập bytes giống hệt nhau.

## 7. FileChannel và ByteBuffer: băm cùng bytes được ghi ra

Với NIO, `FileChannel.read(ByteBuffer)` cập nhật `position` theo số byte đọc thực tế; sau `flip()`, vùng `[position, limit)` là dữ liệu vừa nhận. Muốn **vừa copy vừa hash**, có thể tạo view của chunk để đọc byte cho digest mà không thay đổi `position` của buffer gốc dùng cho `FileChannel.write()`:

```java
// Snippet: in/out là FileChannel; chunk là ByteBuffer ở pha ghi.
int n = in.read(chunk);
if (n > 0) {
    chunk.flip();
    digest.update(chunk.asReadOnlyBuffer()); // View có position riêng.
    while (chunk.hasRemaining()) {
        int written = out.write(chunk);
        if (written == 0) {
            throw new IOException("Không có tiến triển khi ghi");
        }
    }
    chunk.clear();
}
```

Đây là snippet; với file blocking thông thường chính sách fail-fast khi write trả 0 giúp tránh busy loop. Không sao chép nguyên chính sách cho `SocketChannel` non-blocking: `write() == 0` có thể là trạng thái hợp lệ và cần chờ `OP_WRITE`.

Điểm cốt lõi:

- `asReadOnlyBuffer()` tạo **view dùng chung bytes nhưng position/limit độc lập** với buffer gốc; `digest.update(ByteBuffer)` tiêu thụ view từ position đến limit.
- `FileChannel.write(chunk)` vẫn thấy position gốc ở 0 và gửi **đúng các byte vừa được hash**.
- Vì `write` có thể partial, lặp `while (chunk.hasRemaining())` trước khi `clear()`.
- Nếu gặp `n == -1`, đó là EOF; stop. Nếu `n == 0` trên đường đọc file blocking mà buffer vẫn có chỗ, ứng dụng cần chính sách không tiến triển.
- Đừng hash toàn bộ buffer capacity thay cho dữ liệu thực vừa đọc.

**Giới hạn an toàn:** digest tính lúc copy xác nhận tập byte mà ứng dụng đã **định ghi**, không tự bảo đảm các bytes thực sự tồn tại ở file đích sau lỗi hệ thống hoặc bị process khác sửa. Nếu cần quyết định sử dụng file đích, phải **đọc lại file đích để hash và đối chiếu với digest tham chiếu** trước khi commit/tiêu thụ theo chính sách.

## 8. Ví dụ Java 17+ chạy độc lập: copy, đối chiếu, phát hiện thay đổi

Bài thử dùng known-answer vector `SHA-256("abc")` với giá trị hex chuẩn:

```text
ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
```

Chương trình tạo hai file tạm, ghi `abc` vào file nguồn, dùng `FileChannel` và buffer **2 byte** để buộc dữ liệu được xử lý thành nhiều lượt, tính SHA-256 khi copy. Sau đó nó kiểm tra digest nguồn và digest file đích. Cuối cùng đổi dữ liệu đích thành `abd` để chứng minh digest không còn khớp. Trong ví dụ, giá trị SHA-256 tham chiếu được **hardcode để thực hành**; trong ứng dụng thật phải lấy giá trị mong đợi từ một nguồn được xác thực.

Lưu thành **`FileHashIntegrityDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class FileHashIntegrityDemo {
    private static final String TRUSTED_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossibleOnStandardJdk) {
            throw new IllegalStateException(impossibleOnStandardJdk);
        }
    }

    private static byte[] sha256(Path file) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream stream = new DigestInputStream(
                Files.newInputStream(file), digest)) {
            byte[] chunk = new byte[8192];
            while (stream.read(chunk) != -1) {
                // DigestInputStream updates the digest as bytes are read.
            }
        }
        return digest.digest();
    }

    private static byte[] copyAndHash(Path source, Path target)
            throws IOException {
        MessageDigest digest = newSha256();
        ByteBuffer chunk = ByteBuffer.allocate(2);
        try (FileChannel in = FileChannel.open(source,
                     StandardOpenOption.READ);
             FileChannel out = FileChannel.open(target,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {
            while (true) {
                int n = in.read(chunk);
                if (n == -1) break;
                if (n == 0) throw new IOException("Read without progress");
                chunk.flip();

                // Read-only duplicate has independent position.
                digest.update(chunk.asReadOnlyBuffer());
                while (chunk.hasRemaining()) {
                    if (out.write(chunk) == 0) {
                        throw new IOException("Write without progress");
                    }
                }
                chunk.clear();
            }
        }
        return digest.digest();
    }

    public static void main(String[] args) throws IOException {
        Path source = Files.createTempFile("hash-src-", ".txt");
        Path target = Files.createTempFile("hash-dst-", ".txt");
        try {
            Files.writeString(source, "abc", StandardCharsets.US_ASCII);
            byte[] expected = HexFormat.of().parseHex(TRUSTED_SHA256);
            byte[] copiedDigest = copyAndHash(source, target);
            System.out.println("sha256=" +
                    HexFormat.of().formatHex(copiedDigest));
            System.out.println("source-valid=" +
                    MessageDigest.isEqual(expected, sha256(source)));
            System.out.println("copied-valid=" +
                    MessageDigest.isEqual(expected, copiedDigest));
            System.out.println("destination-valid=" +
                    MessageDigest.isEqual(expected, sha256(target)));

            Files.writeString(target, "abd", StandardCharsets.US_ASCII,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("after-tamper-valid=" +
                    MessageDigest.isEqual(expected, sha256(target)));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 FileHashIntegrityDemo.java
java FileHashIntegrityDemo
```

Stdout đã được đối chiếu bằng biên dịch và chạy trên JDK 17+:

```text
sha256=ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
source-valid=true
copied-valid=true
destination-valid=true
after-tamper-valid=false
```

**Giải thích:**

1. `Files.writeString(source, "abc", US_ASCII)` tạo đúng ba byte `61 62 63`. Digest chuẩn được dùng như expected từ một test vector đã biết.
2. `copyAndHash` mở hai `FileChannel` trong `try-with-resources`, đọc tối đa hai byte một lượt và cập nhật `MessageDigest` bằng read-only view.
3. `while (chunk.hasRemaining())` đảm bảo mỗi chunk được chuyển trọn sang file đích theo vị trí `ByteBuffer` trước khi đọc chunk tiếp.
4. `MessageDigest.isEqual` so sánh hai mảng digest thực. `source-valid=true` và `copied-valid=true` cho biết data nguồn và các byte đã đưa qua quy trình copy khớp expected.
5. `destination-valid=true` vì chương trình **đọc lại file đích** rồi băm độc lập; điều này bổ sung một phép kiểm soát so với chỉ tin số byte đã write.
6. Khi thay `abc` bằng `abd`, digest của file đích thay đổi; so sánh với expected cũ cho `after-tamper-valid=false`.
7. Hai file tạm được xóa ở `finally`, channel/input stream được đóng an toàn trước cleanup.

**Giới hạn demo:** chỉ một chương trình thao tác trên các file temp, không có writer ngoài đồng thời. Nó minh họa đúng kỹ thuật băm và so khớp, **không** tự đảm bảo chống TOCTOU, file đã fsync, hoặc dữ liệu không thể bị sửa sau thời điểm kiểm tra. Trong production, cần xem phần quản lý vòng đời và nguồn tin cậy bên dưới.

## 9. So sánh digest đúng cách và đọc expected hash

Một digest SHA-256 thực có **32 byte**. Với nguồn tham chiếu ở dạng hex, xác minh độ dài/encoding trước khi dùng:

```java
// Snippet: expectedHex lấy từ manifest đã xác thực.
String expectedHex = expectedHexFromTrustedManifest;
if (!expectedHex.matches("[0-9a-fA-F]{64}")) {
    throw new IOException("Expected SHA-256 không hợp lệ");
}
byte[] expected = HexFormat.of().parseHex(expectedHex);
byte[] actual = sha256(downloadedFile);
if (!MessageDigest.isEqual(expected, actual)) {
    throw new IOException("Integrity check failed");
}
```

Đây là snippet với `downloadedFile`, `expectedHexFromTrustedManifest` và `sha256(...)` đã được khai báo bên ngoài; `HexFormat` có trong Java 17. Nếu manifest chứa thuật toán `SHA-512`, độ dài digest khác; không đoán độ dài từ một chuỗi hash tùy ý.

`MessageDigest.isEqual(byte[], byte[])` là tiện ích so sánh digest thích hợp hơn việc tự ghép chuỗi và so theo code nhiều nhánh; tuyệt đối không khẳng định từ API này rằng cả quy trình đã có chống timing side-channel hoàn hảo. Nếu xác thực dùng secret và giao thức nhạy timing, tuân thủ thiết kế MAC/chữ ký đã được kiểm tra chuyên sâu.

Tính **hash bằng một `MessageDigest` mới cho mỗi đối tượng/tác vụ**; tránh dùng một instance toàn cục rồi nhiều thread `update` đồng thời. Ghi hash ra log nên kèm thuật toán, phiên bản schema, identity file/version và bối cảnh; đừng vô tình dùng digest như password hashing hoặc kho lưu token bí mật.

## 10. Hash được lưu ở đâu và độ tin cậy đến từ đâu?

Bảng quyết định:

| Expected hash lấy từ | Có thể tin để chống sửa file không? | Lý do |
| --- | --- | --- |
| SHA-256 cùng tải từ URL do attacker kiểm soát | **Không**. | Đối thủ có thể tạo đồng thời file và hash giả. |
| Hash trong DB tin cậy có phân quyền/kiểm toán | Có thể dùng, tùy mức bảo vệ DB và quy trình tạo hash. | Integrity của reference là yếu tố then chốt. |
| Hash hardcode trong bản ứng dụng được phân phối tin cậy | Có thể dùng cho artifact/version cụ thể. | Hash không tự bảo vệ binary ứng dụng khỏi bị sửa. |
| Manifest ký số có public key được ghim/xác minh tin cậy | Có thể cho xác thực publisher nếu protocol và khóa đúng. | Chữ ký bảo vệ manifest hash và metadata gắn kết. |
| HMAC của payload với secret key không lộ | Có thể cho xác thực giữa các bên biết khóa. | Không phù hợp nếu mọi bên được phát hành public đều phải kiểm chứng mà không biết secret. |

Cần gắn digest với đúng **đối tượng**. Nếu chỉ có danh sách SHA-256 mà không biết digest tương ứng với tên artifact, version, kích thước hoặc ngữ cảnh nào, người kiểm tra có thể xác nhận một file hợp lệ nhưng **sai phiên bản**. Manifest nên xác thực cả đường dẫn logic, version, thuật toán, digest và những metadata định danh quan trọng theo nhu cầu.

**Hash đơn lẻ không chứng minh người gửi là ai.** Nó chỉ là một phép tính xác định trên bytes; phần **nguồn tin cậy** đến từ protected manifest, MAC, signature, TLS/authentication và quyền truy cập được thiết kế riêng.

## 11. HMAC và chữ ký số khi cần chống giả mạo

Khi hai hệ thống giữ **khóa bí mật chung**, HMAC giúp kiểm tra dữ liệu có do bên biết khóa tạo ra và không bị đổi trên đường đi:

```java
// Snippet minh họa: secret là byte[] lấy từ kho khóa an toàn,
// không hardcode khóa trong code hoặc log.
Mac mac = Mac.getInstance("HmacSHA256");
SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
mac.init(key);
byte[] tag = mac.doFinal(payload);
```

Snippet cần imports `javax.crypto.Mac`, `javax.crypto.spec.SecretKeySpec` và xử lý exceptions; `payload` là bytes. Không dùng cùng một khóa bí mật cho nhiều mục đích không liên quan; định nghĩa protocol gắn cả identity/version/nonce hoặc context tùy threat model. Một HMAC hợp lệ không tự cung cấp chống replay nếu ứng dụng chấp nhận lặp lại cùng một thông điệp mà không kiểm tra sequence/expiry/idempotency.

Khi cần phát hành cho nhiều người có thể kiểm chứng mà **không** tiết lộ private key, dùng **chữ ký số**. Người phát hành ký manifest hoặc digest có cấu trúc; người nhận kiểm tra chữ ký bằng public key/chuỗi tin cậy đúng. Đừng lấy public key từ cùng nguồn không được xác thực rồi coi như đã giải quyết bài toán giả mạo.

**Đừng phát minh chữ ký bằng cách viết `SHA-256(key || message)`.** Dùng HMAC hoặc signature algorithms được định nghĩa và thư viện crypto chuẩn. Với lưu password, không dùng SHA-256 trực tiếp: password hashing cần salt và thuật toán chuyên dụng có chi phí phù hợp (ví dụ Argon2id, scrypt, PBKDF2 theo chính sách).

## 12. Hash trong quy trình tải xuống/sao chép an toàn

Một quy trình mẫu để xuất bản file đích:

```text
expected hash đã xác thực + identity/version
             |
             v
Tạo file đích TẠM trong thư mục/quyền được kiểm soát
             |
             v
Tải hoặc copy bytes theo chunk, kiểm tra n và quota
             |
             v
Đóng/flush các resource tương ứng, kiểm tra I/O error
             |
             v
Băm lại file tạm, so với expected SHA-256
             |
     +-------+--------+
     |                |
  MISMATCH           MATCH
     |                |
Xóa/quarantine    Tùy yêu cầu: force/sync và atomic publish
Không commit      file đã xác thực, quản lý quyền truy cập
                      |
                      v
                Người tiêu thụ sử dụng đúng version đó
```

Nguyên tắc:

1. Viết vào file **tạm chưa được client khác coi là bản hợp lệ**; không ghi đè trực tiếp bản sản xuất trước khi verify.
2. Dùng expected digest đáng tin; lấy qua một cơ chế riêng hoặc manifest được ký.
3. Chỉ xử lý file sau khi kiểm tra actual digest khớp; thất bại phải **fail closed** đối với dữ liệu quan trọng.
4. Chọn cơ chế publish có atomicity theo nền tảng, ví dụ `Files.move(..., ATOMIC_MOVE)` nếu provider hỗ trợ và hợp đồng replace phù hợp. Phải xử lý `AtomicMoveNotSupportedException`, lỗi permission và khả năng thay file đích khác nhau.
5. Nếu cần độ bền sau mất điện, xem xét `FileChannel.force(true)` cho file và cơ chế sync thư mục mà hệ điều hành/provider cho phép. Atomic rename không tự đồng nghĩa dữ liệu đã được fsync.
6. Không để producer khác sửa file tạm sau kiểm tra; điều phối ownership, quyền truy cập, lock/rename và thời điểm sử dụng.

**TOCTOU:** kiểm tra hash tại T1 rồi đọc lại cùng đường dẫn tại T2 **không bảo đảm** bytes vẫn như cũ nếu file bị thay thế/ghi đè giữa hai mốc. Ưu tiên lưu file verified trong vùng bất biến theo chính sách, dùng định danh version/content-addressed storage, quản lý handle và quyền ghi, xác minh gần thời điểm dùng khi cần.

## 13. Dữ liệu nguồn thay đổi trong lúc hash/copy

Bài toán khó xuất hiện khi file lớn và có writer khác đang cập nhật:

```text
T0: SHA-256 bắt đầu đọc file X
T1: Writer khác thay đổi đoạn giữa của X
T2: SHA-256 đọc phần còn lại
T3: Digest thu được từ một chuỗi byte có thể không
    tương ứng với snapshot nhất quán của file ở bất kỳ thời điểm nào.
```

Một số lưu ý quan trọng:

- Đọc file theo chunk không cung cấp **snapshot isolation**.
- Kiểm tra `Files.size()` và `Files.getLastModifiedTime()` trước/sau có thể hỗ trợ phát hiện thay đổi, nhưng không phải chứng minh an toàn: timestamps có độ phân giải, có thể bị chỉnh và file có thể đổi rồi đổi lại.
- `FileChannel.lock()` có tác dụng theo OS/file system và mức hợp tác của writer; không đảm bảo mọi chương trình đều tôn trọng.
- Nếu cần khẳng định hash của một **phiên bản bất biến**, dùng snapshot, copy-on-write, quy trình producer hoàn tất rồi rename/publish, content-addressed object hoặc giao thức đồng bộ được kiểm soát.
- File được mở bằng một handle cũng không mặc nhiên chặn mọi thay đổi của process khác tới nội dung; phải hiểu đúng locking và sharing semantics trên OS.

Ví dụ với backup: không nên dùng SHA-256 của file đang bị update liên tục rồi suy rằng bạn đã sao lưu phiên bản hợp lệ của database. Cần backup snapshot hoặc protocol riêng của database.

## 14. Chi phí hiệu năng và chiến lược tối ưu

SHA-256 được tính tuyến tính theo số byte đầu vào: **O(n)** về thời gian và có thể dùng **O(1)** bộ nhớ phụ với buffer kích thước cố định. Tuy nhiên, công cụ thực tế còn chịu overhead đọc đĩa, I/O mạng và đối chiếu file đích.

| Cách làm | Số lần đi qua dữ liệu | Điểm mạnh | Điểm yếu |
| --- | --- | --- | --- |
| Copy rồi hash file nguồn và đích | Copy + ít nhất hai lượt đọc để băm | Phát hiện sai lệch ở cả hai phía, dễ giải thích. | Tốn I/O đọc thêm. |
| Hash khi copy qua `MessageDigest.update` | Hash phần bytes đang copy | Ít lượt xử lý hơn, thuận tiện streaming. | Chưa xác minh bytes đã persist tại đích sau write. |
| Hash lại file đích sau copy | Thêm một lượt đọc file đích | Kiểm tra độc lập nội dung đã đọc lại. | Có thêm I/O; vẫn có race với writer khác. |
| Hash theo block/chunk có manifest | Mỗi block có digest riêng | Hỗ trợ resume/dedup/kiểm tra cục bộ. | Phải xác thực manifest và thứ tự/index chunk. |
| Hash toàn file trước mỗi lần dùng | Đọc hết bytes mỗi lần | Kiểm tra nội dung gần thời điểm sử dụng. | Tốn chi phí cho dữ liệu lớn, không loại trừ TOCTOU hoàn toàn. |

Không nên tối ưu bằng cách bỏ kiểm tra nếu rủi ro nghiệp vụ yêu cầu nó. Mặt khác, với cache nội bộ có semantics rõ, có thể dùng metadata/version/content ID để tránh băm lại khi đã có bằng chứng bất biến đáng tin; đo workload trước khi chọn.

Một `MessageDigest` dùng chung không có lý do phải cập nhật theo từng byte; đọc buffer đủ lớn để giảm syscall overhead. Trong Java 17, `MessageDigest.update(ByteBuffer)` hỗ trợ trực tiếp buffer heap/direct; không nhất thiết phải copy sang `byte[]`.

## 15. Những giới hạn hash không giải quyết

**Hash không kiểm chứng định dạng.** Một file có SHA-256 khớp expected vẫn có thể chứa lệnh nguy hiểm hoặc dữ liệu không tuân thủ schema. Vì vậy validate JSON/ZIP/protocol sau integrity check.

**Hash không mã hóa dữ liệu.** SHA-256 không giấu bytes, không tạo confidentiality. Nếu cần bảo mật nội dung, dùng encryption/authenticated encryption phù hợp.

**Hash không chứng minh timestamp hay filename.** Hai file khác tên nhưng cùng bytes sẽ có cùng SHA-256. Nếu identity/version/name quan trọng, bảo vệ và kiểm tra metadata đó bằng manifest được xác thực.

**Hash không đồng nghĩa durability.** Digest khớp khi đọc lại không xác nhận ổ đĩa sẽ giữ nguyên sau mất điện; `force` và storage protocol là một trách nhiệm riêng.

**Hash không chống replay.** Bên gửi có thể gửi lại bản hợp lệ cũ kèm digest hợp lệ; chống downgrade/replay cần version monotonic, expiry, nonce, signature context và quy tắc ứng dụng.

**Hash không chống đường dẫn độc hại.** Dù archive ZIP có SHA-256 khớp, việc giải nén vẫn phải bảo vệ khỏi Zip Slip, symlink, zip bomb và resource exhaustion.

**Hash không sửa lỗi tự động.** Nếu mismatch, không được “bỏ qua cảnh báo để tiếp tục”. Hệ thống cần retry/quarantine/khôi phục từ nguồn an toàn; bản hash không chứa thông tin để dựng lại dữ liệu hỏng.

## 16. Lỗi thường gặp và cách khắc phục

| Hiện tượng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Hash thay đổi dù text nhìn giống nhau | Charset, BOM, newline, Unicode normalization, whitespace khác. | Xác định hash raw bytes hay canonicalized content; thống nhất quy tắc. |
| Hash sai ở chunk cuối | Gọi `digest.update(buffer)` với cả mảng thay vì đúng `n` byte mới. | Dùng `update(buffer, 0, n)` hoặc `DigestInputStream`. |
| Hash khác khi dùng NIO | Hash cả capacity/thừa byte, hoặc buffer position đã bị `write` tiêu thụ. | `flip()` đúng; dùng duplicate/read-only view trước write. |
| Kết quả hash chỉ đúng lần đầu | Gọi `digest()` rồi tiếp tục update trên state đã reset, hoặc reuse chung giữa threads. | Instance mới cho mỗi phép hash; finalize một lần. |
| File copy bị thiếu byte | Chỉ gọi `FileChannel.write()` một lần, không xử lý partial write. | `while (buffer.hasRemaining())` trước `clear()`. |
| Không phát hiện file bị đổi | Expected digest bị thay cùng file. | Bảo vệ expected bằng chữ ký/HMAC/kênh tin cậy. |
| Reader coi SHA-256 khớp là bằng chứng người gửi | Hash không có khóa/xác thực. | HMAC hoặc chữ ký số, xác thực khóa đúng. |
| Hash file nguồn khớp nhưng đích sai | File đích bị đổi sau copy hoặc write lỗi. | Băm lại đích, giữ file tạm riêng và publish sau verify. |
| Digest khớp nhưng khi dùng nội dung khác | TOCTOU giữa hash và open/consume, đường dẫn bị thay. | Quản lý immutable artifact/handle/ownership, verify gần lúc dùng. |
| File đang ghi cho kết quả không nhất quán | Không có snapshot và writer sửa trong lúc đọc. | Dùng snapshot/producer publish protocol. |
| Dùng MD5/CRC cho xác thực tải code | Không chống giả mạo có chủ đích. | SHA-256 tham chiếu tin cậy + chữ ký/HMAC theo mô hình đe dọa. |
| Dùng SHA-256 của mật khẩu để lưu trữ | Hash quá nhanh, thiếu thiết kế chống brute-force. | Password hashing chuyên dụng với salt/cost theo policy. |
| Chuỗi hex không parse được | Dính newline, dấu cách, prefix hoặc độ dài sai. | Chuẩn hóa đầu vào theo format có kiểm soát, validate đúng 64 hex cho SHA-256. |
| Hash sau decompression khác hash file ZIP | Băm hai representation khác nhau. | Xác định lớp dữ liệu và thứ tự xử lý thống nhất. |

## 17. Kiểm thử hệ thống có hash

Một bộ kiểm thử tốt cần bao gồm:

1. **Known answer:** `SHA-256("abc")` đúng giá trị hex trong bài. Dùng fixture chuẩn, không lấy expected bằng chính hàm vừa viết rồi tự gọi đó là test độc lập.
2. **Roundtrip:** copy dữ liệu, hash source, hash target, so với expected đã xác thực. Kiểm tra size, bytes và hash.
3. **Tài liệu trống:** hash của file rỗng cũng là một digest SHA-256 hợp lệ, không phải chuỗi rỗng.
4. **File không cùng size:** ứng dụng nhận ra mismatch không chỉ qua digest mà còn lỗi I/O/size khi phù hợp.
5. **Sửa một byte:** thay một byte ở đầu, giữa và cuối; digest so expected không khớp.
6. **Sửa tên file nhưng giữ bytes:** digest raw bytes không đổi; manifest phải định nghĩa policy cho tên/đường dẫn.
7. **Chunking:** tính với buffer 1, 2, 8192 byte và direct buffer; digest phải giống nhau.
8. **Quyền truy cập và disk full:** bắt `IOException`, không publish file tạm chưa verify.
9. **Concurrent writer:** mô phỏng file bị sửa trong lúc copy để kiểm tra chính sách snapshot hoặc từ chối commit.
10. **Expected bị thay:** chứng minh hash thuần của file + hash giả **không** tạo xác thực; kiểm thử signature/HMAC path thật sự.
11. **TOCTOU:** sau verify, thay file/đường dẫn trước khi tiêu thụ; thiết kế kiểm soát việc sử dụng artifact đã kiểm tra.
12. **Giới hạn nguồn lực:** file lớn, đọc chậm, network timeout, deadline, queue backlog và quota.
13. **Replay/version:** file bản cũ vẫn có hash hợp lệ nhưng không được chấp nhận nếu policy yêu cầu bản mới hơn.

Nếu chương trình xuất ra `hash-valid=true`, cần định nghĩa rõ “valid so với expected nào và expected đến từ đâu”. Test chỉ hash cùng một input hai lần rồi so chúng với nhau **không chứng minh** integrity so với nguồn độc lập.

## 18. Bài tập thực hành

**Bài tập 1 — Hash các bytes file thay vì text.** Tạo hai file: một file chứa `"Xin chào\n"` UTF-8, file kia chứa `"Xin chào\r\n"` UTF-8. Tính SHA-256 bằng `DigestInputStream`; giải thích vì sao hash khác dù khi hiển thị có thể trông tương tự.

**Bài tập 2 — Streaming chunk sizes.** Chỉnh `ByteBuffer.allocate(2)` thành `allocate(1)`, `allocate(16)` và `allocate(8192)`. Kiểm tra digest của `abc` và việc copy file luôn giống nhau; nêu điều kiện `flip/clear/hasRemaining` bảo vệ khỏi mất/trùng byte.

**Bài tập 3 — Sửa một byte.** Trong chương trình mẫu, thử `"abc"` → `"aBc"`, `"abd"` và `"abc\n"`. So SHA-256 trước/sau. Vì sao chỉ dùng `Files.size()` sẽ bỏ qua thay đổi `abc → abd`?

**Bài tập 4 — Xác thực manifest.** Định nghĩa một manifest có `artifactId`, `version`, `length`, `algorithm`, `digest`. Nêu cách lấy manifest có chữ ký và xác minh public key đáng tin; không coi file manifest tải cùng nguồn không bảo vệ là bằng chứng.

**Bài tập 5 — Download an toàn.** Viết hàm nhận dữ liệu qua stream vào file tạm, có quota tổng 50 MiB, deadline và đếm bytes. Sau tải xuống, băm lại file tạm, so expected SHA-256 và chỉ publish bằng move có kiểm tra atomicity; khi mismatch phải xóa/quarantine.

**Bài tập 6 — Phân biệt CRC32 và SHA-256.** Tính cả hai trên cùng payload. Giải thích vì sao CRC32 phù hợp kiểm tra lỗi ZIP thông thường nhưng không làm người sửa archive có chủ đích thất bại nếu họ cũng tính lại CRC.

**Bài tập 7 — Race và immutable source.** Cho một thread/tiến trình sửa file trong lúc thread khác hash; quan sát sự khác biệt. Thiết kế producer tạo tệp tạm → flush/đóng → publish tên cố định → gửi manifest tin cậy, để consumer không hash file còn đang bị ghi.

**Bài tập 8 — HMAC.** Dùng `javax.crypto.Mac` và key lấy từ nơi quản lý key thử nghiệm để tạo HMAC-SHA-256 cho payload. Thay đổi một byte rồi kiểm tra tag không còn đúng; giải thích tại sao HMAC cần khóa bí mật còn SHA-256 thuần không.

**Bài tập 9 — Bảo vệ chống replay.** Tạo hai artifact v1/v2 đều có chữ ký/hash hợp lệ. Thiết kế policy không chấp nhận v1 sau khi đã kích hoạt v2. Vì sao digest khớp không đủ để quyết định phiên bản được phép sử dụng?

**Bài tập 10 — Hash ZIP.** Tạo hai ZIP chứa cùng file text nhưng timestamp của entry khác nhau. So SHA-256 **toàn ZIP** và SHA-256 **bytes entry giải nén**; giải thích sự khác biệt và chọn representation phù hợp yêu cầu.

### Câu hỏi tự kiểm tra

- `MessageDigest.update` nhận bytes theo luồng khác gì đọc toàn bộ file vào mảng?
- Khi read chỉ trả `n=7` vào mảng 8192 byte, phải cập nhật digest bao nhiêu byte?
- `DigestInputStream` có cần gọi `digest.update(buffer)` thêm sau mỗi lần đọc không?
- Vì sao hash file nguồn trong lúc copy không tự chứng minh file đích không bị thay?
- Nếu expected digest do attacker cung cấp cùng file, so sánh có tạo authentication không?
- Khi muốn xác nhận chính publisher đã phát hành artifact, dùng HMAC hay chữ ký số trong bối cảnh nào?
- CRC32 có chức năng gì so với SHA-256?
- Vì sao file đang được writer sửa có thể không có snapshot ổn định khi hash?
- Hash khớp có đồng nghĩa file format hợp lệ, bản không bị replay và dữ liệu đã fsync không?
- Tại sao raw ZIP bytes và bytes giải nén từ entry thường có SHA-256 khác nhau?

## 19. Tài liệu tham khảo chính thức

- [Java SE 21 — MessageDigest](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html): tạo SHA-256, `update`, `digest`, `isEqual`.
- [Java SE 21 — DigestInputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/DigestInputStream.html): băm tự động dữ liệu đọc từ stream.
- [Java SE 21 — FileChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html): read/write, buffer position, force và lifecycle.
- [Java SE 21 — ByteBuffer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html): `flip`, `remaining`, `asReadOnlyBuffer`.
- [Java SE 21 — HexFormat](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HexFormat.html): chuyển digest sang và từ chuỗi hexadecimal.
- [Java SE 21 — Mac](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html): HMAC với khóa bí mật.
- [Java SE 21 — Signature](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Signature.html): ký và xác minh chữ ký số.
- [NIST FIPS 180-4 — Secure Hash Standard](https://csrc.nist.gov/pubs/fips/180-4/upd1/final): đặc tả SHA-256 và họ Secure Hash Algorithm.

**Ghi nhớ:** Khi bài toán yêu cầu byte-for-byte integrity, tính SHA-256 của **đúng bytes** theo luồng và so với **digest tham chiếu đáng tin**. Hash có thể giúp phát hiện khác biệt, nhưng không tự xác thực nguồn, ngăn sửa đổi sau kiểm tra, tránh replay hoặc đảm bảo độ bền vật lý. Hãy gắn nó vào quy trình quản lý file tạm, quyền truy cập, phiên bản, chữ ký/HMAC và chính sách fail-closed phù hợp rủi ro.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-io-design-009">Tránh ghi dữ liệu nhạy cảm và đường dẫn nội bộ vào thông báo công khai</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh ghi dữ liệu nhạy cảm và đường dẫn nội bộ vào thông báo công khai trong Java I/O

## 1. Mục tiêu và tình huống

Ứng dụng Java thường đọc/ghi file bằng `Files`, `FileChannel`, `InputStream`, `OutputStream`, xử lý upload/download, tệp tạm, archive ZIP/JAR và tài nguyên của người dùng. Khi thao tác thất bại, exception có thể chứa **đường dẫn tuyệt đối trên server**, username hệ điều hành, tên tài liệu, storage key, endpoint hạ tầng, thậm chí một phần dữ liệu input. Nếu lấy nguyên `exception.getMessage()` hoặc stack trace làm nội dung HTTP response, UI, email, webhook hay message gửi khách hàng, thông tin không cần thiết có thể bị rò rỉ.

Ví dụ phản hồi **không nên công khai**:

```text
Could not read /srv/apps/prod/private/customers/42/tax-record.pdf
java.nio.file.AccessDeniedException:
    /srv/apps/prod/private/customers/42/tax-record.pdf
```

Phản hồi an toàn hơn:

```text
status=404
code=RESOURCE_UNAVAILABLE
message=Không thể truy cập tài nguyên được yêu cầu.
requestId=REQ-DEMO-001
```

Bài này giúp bạn:

1. Nhận diện thông tin nhạy cảm trong đường dẫn, exception, request và log.
2. Tạo lỗi công khai từ **allowlist** gồm status, mã lỗi, thông điệp ổn định và mã tương quan.
3. Xử lý `NoSuchFileException`, `AccessDeniedException`, `IOException` mà không phơi lộ chi tiết filesystem.
4. Tách public response với chẩn đoán/log nội bộ cũng phải được bảo vệ.
5. Tránh phản chiếu tên file upload, URL riêng, token, thông tin cá nhân hoặc raw payload.
6. Kiểm tra các đường rò rỉ gián tiếp qua framework error page, serializer, log/trace exporter.
7. Viết chương trình và bài test Java 17+ có stdout xác định.

Nguyên tắc quan trọng: **mục tiêu không phải che mọi lỗi**, mà là cho người dùng thông tin hữu ích trong phạm vi họ được phép biết, trong khi người vận hành vẫn có thể tra cứu sự cố qua một kênh được kiểm soát.

## 2. Thế nào là thông báo công khai?

Bất cứ nội dung nào người không có quyền vận hành nội bộ có thể đọc đều cần coi là bề mặt công khai hoặc bán công khai:

- HTTP API response body/header, GraphQL error, WebSocket message.
- Thông báo lỗi trên web, điện thoại, CLI được phân phối cho khách hàng.
- Email, SMS, notification, webhook gửi cho bên thứ ba.
- File export báo lỗi và trang hỗ trợ kỹ thuật.
- Browser console, client analytics SDK, trace/event exporter có quyền truy cập rộng.
- Trích dẫn log hoặc stack trace được dán vào issue/ticket chia sẻ.
- Trang lỗi mặc định của reverse proxy hoặc web framework.

**Log nội bộ không đương nhiên an toàn**. Log có thể tập trung về dịch vụ SaaS, được sao lưu nhiều năm, xuất sang đối tác hoặc hiện trong dashboard công khai do cấu hình sai. Vì vậy cần kiểm soát quyền truy cập, thời hạn lưu, mã hóa khi phù hợp và tối thiểu hóa dữ liệu ngay từ lúc tạo log.

## 3. Những dữ liệu dễ bị lộ qua I/O

| Loại | Ví dụ | Rủi ro |
| --- | --- | --- |
| Đường dẫn nội bộ | `/srv/prod/private/payroll/2026.csv` | Tiết lộ cấu trúc máy chủ, dữ liệu nghiệp vụ |
| Đường dẫn Windows | `C:\Users\svc\AppData\Local\...` | Tiết lộ tài khoản hệ thống, môi trường |
| UNC/network path | `\\fileserver\finance\salary.csv` | Tiết lộ tên host/share riêng |
| Storage URI | Bucket/key riêng, object-store URL chứa token | Lộ hạ tầng hoặc quyền truy cập |
| Secret | `Authorization`, API key, refresh token, mật khẩu | Có thể bị sử dụng truy cập trái phép |
| PII | Email, điện thoại, ID, họ tên gắn với filename | Lộ dữ liệu cá nhân |
| Nội dung tệp | Dòng CSV/JSON/XML gây lỗi parse | Có thể chứa cả dữ liệu nhạy cảm |
| Stack trace | Package, class, source line, cause | Tiết lộ chi tiết triển khai và vị trí lỗi |
| Query string/filename | Tên file upload, tham số client | Có thể chứa secret và dữ liệu do đối thủ kiểm soát |
| Diagnostics từ provider | Tên disk/mount, endpoint, reason | Lộ cấu hình vận hành |

Tên file chỉ là **basename** không có nghĩa vô hại: `hospital-result_Alice.pdf` vẫn có thể chứa dữ liệu cá nhân. Không hiển thị `Path.getFileName()` trừ khi thuộc tính display name đã được phê duyệt cho đúng đối tượng và đúng bối cảnh.

## 4. Exception của Java I/O không phải dữ liệu an toàn để phản chiếu

Ví dụ các loại cần hiểu:

- `NoSuchFileException` khi không thấy một `Path`.
- `AccessDeniedException` khi không đủ quyền truy cập.
- `FileAlreadyExistsException` nếu entry đích đã tồn tại.
- `FileSystemException` có `getFile()`, `getOtherFile()`, `getReason()`.
- `FileNotFoundException` với message thường chứa file path/hệ thống.
- `InvalidPathException` có thể chứa chuỗi input gây lỗi; đây là unchecked exception.
- `IOException` chung từ file, socket, ZIP, filesystem provider, cloud storage adapter.
- Lỗi parser khi đọc file có thể chứa đoạn nội dung sai định dạng.

Không có hợp đồng nào cho rằng `Throwable.getMessage()` đã xóa secret hoặc path. Cùng một thao tác có thể tạo thông điệp khác nhau giữa các OS/provider/JDK.

Anti-pattern (đoạn minh họa không độc lập):

```java
try {
    return Files.readString(path);
} catch (IOException ex) {
    // SAI nếu string này được gửi cho người dùng không có quyền:
    return "Không đọc được file " + path + ": " + ex.getMessage();
}
```

Các cách gián tiếp khác cũng có thể rò rỉ:

```java
exception.toString()
exception.getLocalizedMessage()
Arrays.toString(exception.getStackTrace())
path.toAbsolutePath().toString()
exception.printStackTrace(new PrintWriter(responseWriter))
```

Đây là biểu thức minh họa cần **tránh tại biên xuất thông tin**, không phải code độc lập. Stack trace có thể chứa nested causes và suppressed exceptions; dữ liệu lộ ra nhiều hơn message đầu tiên.

## 5. Tách public response khỏi thông tin chẩn đoán

Mẫu thiết kế:

```text
Files / Channel / FileStore / Parser
                |
             Exception
                |
       +--------+---------+
       |                  |
       v                  v
 Public Error Mapper   Internal Diagnostics
       |                  |
       v                  v
 PublicError DTO       Structured event
 status, code, msg      code, requestId,
 requestId              category, duration
       |                  |
       v                  v
 HTTP/UI/webhook         Log có quyền kiểm soát
 Không có Throwable      Không log raw secrets
```

Public DTO nên chứa **chỉ những trường được cho phép**. Không thêm các field như `Throwable cause`, `Path internalPath`, raw request hay `StackTraceElement[]` để serializer tự xử lý.

Một hợp đồng thường có:

```text
status:     500
code:       STORAGE_ERROR
message:    Không thể hoàn tất thao tác. Vui lòng thử lại.
requestId:  e.g. một UUID riêng của request
```

Message public giúp định hướng và không xác nhận thông tin ngoài quyền của người dùng. Mã lỗi phục vụ UI và support; requestId phục vụ đối chiếu với log có quyền truy cập. **Request ID không phải access token**, và người biết ID không được tự động xem log liên quan.

## 6. Chọn status/mã lỗi có chủ đích

| Điều kiện | Ví dụ status HTTP | Public code | Nội dung hiển thị |
| --- | --- | --- | --- |
| Resource không có/không thể truy cập theo chính sách | 404 | `RESOURCE_UNAVAILABLE` | Không thể truy cập tài nguyên. |
| Bị cấm và chính sách cho phép tiết lộ việc cấm | 403 | `ACCESS_DENIED` | Bạn không có quyền thực hiện thao tác. |
| Request sai schema/giá trị đầu vào | 400 | `INVALID_REQUEST` | Dữ liệu yêu cầu không hợp lệ. |
| Upload vượt giới hạn | 413 | `UPLOAD_TOO_LARGE` | Tệp vượt giới hạn cho phép. |
| Định dạng file không được hỗ trợ | 415/400 | `UNSUPPORTED_FILE_TYPE` | Định dạng tệp không được hỗ trợ. |
| Lỗi I/O nội bộ bất thường | 500 | `STORAGE_ERROR` | Không thể hoàn tất thao tác. |
| Dịch vụ lưu trữ tạm thời không hoạt động | 503 nếu phù hợp | `STORAGE_UNAVAILABLE` | Dịch vụ tạm thời không khả dụng. |

Đây là **bảng minh họa**, không phải quy tắc status áp dụng cho mọi API.

Trong hệ thống tài nguyên riêng, đôi khi cả “không tồn tại” và “không có quyền xem” đều cần trả **404 với cùng schema/message** để không tạo kênh dò sự tồn tại. Ngược lại, một API phục vụ người dùng đã được xác thực có thể chủ động dùng 403. Lựa chọn phải theo chính sách authorization và được áp dụng nhất quán, không chỉ làm đẹp nội dung.

Cần chú ý **side channel ngoài text**: sự khác biệt quá lớn về status, kích thước body hoặc độ trễ có thể làm lộ việc resource có tồn tại hay không. Không nên hứa đã che giấu hoàn toàn chỉ nhờ thay message.

## 7. Ví dụ chạy được: ánh xạ exception sang `PublicError` (Java 17+)

Lưu thành **`SafePublicErrorDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;

public class SafePublicErrorDemo {
    record PublicError(
            int status, String code, String message, String requestId) {
    }

    static PublicError map(Throwable error, String requestId) {
        if (error instanceof NoSuchFileException
                || error instanceof AccessDeniedException) {
            // Demo: tài nguyên riêng, không xác nhận sự tồn tại.
            return new PublicError(
                    404,
                    "RESOURCE_UNAVAILABLE",
                    "Không thể truy cập tài nguyên được yêu cầu.",
                    requestId);
        }

        if (error instanceof IllegalArgumentException) {
            return new PublicError(
                    400,
                    "INVALID_REQUEST",
                    "Dữ liệu yêu cầu không hợp lệ.",
                    requestId);
        }

        return new PublicError(
                500,
                "STORAGE_ERROR",
                "Không thể hoàn tất thao tác.",
                requestId);
    }

    private static void printSafe(Throwable error, String requestId) {
        PublicError result = map(error, requestId);
        System.out.println(result.status() + " | "
                + result.code() + " | "
                + result.message() + " | "
                + result.requestId());
    }

    public static void main(String[] args) {
        String id = "REQ-DEMO-001"; // cố định CHỈ cho ví dụ

        printSafe(new NoSuchFileException(
                "/srv/private/clients/42/contract.pdf"), id);

        printSafe(new AccessDeniedException(
                "/mnt/secure/reports/finance.csv"), id);

        printSafe(new IOException(
                "storage failed; token=DEMO_SECRET_DO_NOT_DISCLOSE"), id);

        printSafe(new IllegalArgumentException(
                "raw input=/internal/tmp/name.txt"), id);
    }
}
```

Chạy với JDK 17+ và terminal hỗ trợ UTF-8:

```bash
javac -encoding UTF-8 SafePublicErrorDemo.java
java SafePublicErrorDemo
```

Stdout khi thành công:

```text
404 | RESOURCE_UNAVAILABLE | Không thể truy cập tài nguyên được yêu cầu. | REQ-DEMO-001
404 | RESOURCE_UNAVAILABLE | Không thể truy cập tài nguyên được yêu cầu. | REQ-DEMO-001
500 | STORAGE_ERROR | Không thể hoàn tất thao tác. | REQ-DEMO-001
400 | INVALID_REQUEST | Dữ liệu yêu cầu không hợp lệ. | REQ-DEMO-001
```

**Giải thích:**

1. Exception chứa path/token **chỉ ở phía bên trong** object. Mapper không gọi `getMessage()` hoặc `toString()`.
2. Hai lỗi file riêng `NoSuchFileException` và `AccessDeniedException` được ánh xạ giống nhau, giảm nguy cơ tiết lộ trạng thái tồn tại.
3. `IOException` bất thường nhận mã `STORAGE_ERROR`, không phát tán nguyên nhân raw.
4. `record PublicError` có field cố định, không giữ `Throwable`.
5. `IllegalArgumentException` chỉ được dùng minh họa đầu vào sai. Trong ứng dụng thật, không nên ánh xạ **mọi** `IllegalArgumentException` thành lỗi client; lỗi lập trình nội bộ cũng có thể phát sinh loại này. Nên phân loại tại tầng nghiệp vụ bằng exception riêng.
6. ID cố định trong demo tạo stdout xác định; trong hệ thống thật hãy dùng ID server-generated theo từng request.

Ví dụ là **error mapping** thuần Java, chưa phải HTTP server. Nó không thay thế kiểm tra phân quyền trước thao tác đọc file.

## 8. Ví dụ chạy được: xử lý I/O thực và tách stdout/stderr

Lưu **`SafeFileReadDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class SafeFileReadDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("safe-file-");
        String requestId = "REQ-DEMO-002";

        try {
            Path internalPath = directory.resolve("private-record.txt");

            try {
                Files.readString(internalPath);
                System.out.println("status=200");
            } catch (NoSuchFileException ex) {
                // Diagnostic: chỉ các trường cho phép,
                // không in ex, ex.getMessage(), ex.getFile().
                System.err.println(
                        "event=FILE_READ_FAILED"
                        + " type=NoSuchFileException"
                        + " requestId=" + requestId);

                // Public response giả lập.
                System.out.println("status=404");
                System.out.println("code=RESOURCE_UNAVAILABLE");
                System.out.println(
                        "message=Không thể truy cập tài nguyên được yêu cầu.");
                System.out.println("requestId=" + requestId);
            }
        } finally {
            Files.deleteIfExists(directory);
        }
    }
}
```

```bash
javac -encoding UTF-8 SafeFileReadDemo.java
java SafeFileReadDemo
```

**Stdout** khi đọc file không tồn tại:

```text
status=404
code=RESOURCE_UNAVAILABLE
message=Không thể truy cập tài nguyên được yêu cầu.
requestId=REQ-DEMO-002
```

**Stderr** khi cùng thao tác thất bại:

```text
event=FILE_READ_FAILED type=NoSuchFileException requestId=REQ-DEMO-002
```

Hai dòng output nằm trên hai luồng khác nhau; IDE/terminal có thể trộn chúng với thứ tự khác nhau nếu hiển thị chung. Nếu stderr được chuyển cho bên ngoài, nó **cũng là kênh công khai**; dùng một hệ thống log có chính sách bảo vệ, không chỉ dựa vào tên stdout/stderr.

Ở đây chương trình tự tạo thư mục mới và cố ý đọc một file chưa tạo, nên `NoSuchFileException` là kết quả dự kiến trong môi trường bình thường. Path tuyệt đối và tên tệp không xuất hiện vì code không in object `internalPath` hoặc exception. Nếu I/O gặp lỗi khác không được bắt, `main` vẫn có thể in stack trace: **server thật bắt buộc có error boundary** chuyển lỗi chưa biết thành public response an toàn, thay vì để runtime tự gửi stack trace ra HTTP.

## 9. Diagnostic logs cũng cần thiết kế allowlist

Log nội bộ có thể hỗ trợ hỗ trợ kỹ thuật, nhưng điều đó không cho phép ghi mọi dữ liệu tùy ý.

**Cách dễ tạo rò rỉ:**

```java
logger.log(Level.SEVERE,
        "Read failed: " + path + " / " + exception.getMessage(),
        exception);
```

Đây là snippet **anti-pattern trong hệ thống log truy cập rộng**. Nó có thể gửi path, message, cause và stack trace sang mọi sink do logger cấu hình. Không có nghĩa tất cả việc ghi stack trace đều bị cấm trong mọi môi trường; cần phân tầng và đánh giá chính sách thông tin.

**Cách kiểm soát trường log hơn:**

```java
logger.log(Level.WARNING,
        "event={0} requestId={1} errorCategory={2}",
        new Object[] {
                "FILE_READ_FAILED",
                requestId,
                "NOT_FOUND_OR_DENIED"
        });
```

Snippet này giả định các biến/logger được khởi tạo. Các field đến từ danh mục cố định, không từ `exception.getMessage()`. Nếu cần stack trace trong một kênh riêng được bảo vệ, phải có quyền truy cập hạn chế, thời hạn lưu và chính sách kiểm tra secret/PII. Một logger gọi là “internal” không thay đổi bản chất dữ liệu nếu nó xuất ra dashboard công khai.

| Field có thể cho phép | Ví dụ | Cảnh báo |
| --- | --- | --- |
| `eventCode` | `FILE_READ_FAILED` | Dùng danh mục cố định |
| `requestId` | UUID kiểm tra hợp lệ | Không mã hóa ID khách hàng/path |
| `operation` | `READ_RESOURCE` | Không ghi raw query/path |
| `errorCategory` | `NOT_FOUND_OR_DENIED` | Không lộ chi tiết quyền riêng |
| `serviceCode` | `DOCUMENT_SERVICE` | Chỉ dùng tên được phép |
| `durationBucket` | `under_100ms` | Tránh dữ liệu không cần thiết |
| `publicErrorCode` | `RESOURCE_UNAVAILABLE` | Dùng cùng mã response |

Không đưa file path, email, token hoặc raw filename thành label của metrics. Điều này gây rủi ro lộ dữ liệu, tăng số giá trị label (*cardinality*) và làm hệ thống giám sát khó vận hành.

## 10. `requestId`: truy vết mà không công khai bí mật

Mã request giúp nhân viên hỗ trợ tra log đúng sự cố mà không yêu cầu người dùng chụp màn hình stack trace.

Nguyên tắc:

1. Tạo ID ở server, ví dụ `UUID.randomUUID().toString()`, hoặc dùng trace ID từ cơ chế đã được kiểm soát.
2. Không ghép `userId + path + token` thành ID và không xuất giá trị hash từ một secret thấp entropy như thể chắc chắn vô hại.
3. Không coi request ID là bằng chứng người dùng có quyền đọc toàn bộ log.
4. Nếu nhận `X-Request-ID` từ client, kiểm tra chiều dài, charset, ký tự điều khiển và chính sách trust; nếu không tin cậy, tự tạo ID.
5. Bảo vệ hệ thống tra cứu log bằng xác thực/authorization; biết request ID **không đủ** để được xem chẩn đoán nội bộ.
6. Không dùng ID cố định của ví dụ trong production.

Snippet:

```java
String requestId = java.util.UUID.randomUUID().toString();
```

Vì ID thực tế ngẫu nhiên, không thể viết stdout cố định cho một chương trình sinh ID mới ở mỗi lần chạy. Hai ví dụ trước dùng `REQ-DEMO-...` chỉ để thuận tiện đối chiếu output.

## 11. Tên file upload không phải dữ liệu tin cậy

Tên file do client gửi có thể chứa path cục bộ hoặc PII:

```text
C:\Users\Alice\Documents\medical-result.pdf
customer-123456789-bank-statement.pdf
../private/secret.txt
```

Không dùng chuỗi này trực tiếp làm đường dẫn lưu trên server hoặc in vào error log public. Các vấn đề bao gồm:

- Path traversal, separator khác nhau giữa Windows/Unix.
- Trùng tên file, cố ý ghi đè.
- Chuỗi rất dài hoặc ký tự điều khiển dùng cho log/header injection.
- Thông tin cá nhân được người dùng đặt trong tên file.
- Nội dung file giả mạo extension hoặc MIME type.

Một chính sách thực dụng:

- Server tạo **storage key** độc lập tên gốc, có đủ tính duy nhất.
- Kiểm tra giới hạn byte upload, loại file thực tế, quyền sở hữu resource.
- Lưu **display name** riêng khi nghiệp vụ thực sự cần; chỉ hiển thị nó cho người được phép.
- Không đưa path lưu file vào HTTP response hoặc URL kiểu file nội bộ.
- Xử lý download header `Content-Disposition` bằng thư viện hỗ trợ encoding/validation phù hợp, không nối raw string chứa CR/LF.

Snippet minh họa, chưa phải upload handler hoàn chỉnh:

```java
String storageKey = java.util.UUID.randomUUID().toString();
Path destination = controlledUploadRoot.resolve(storageKey);
```

`controlledUploadRoot` phải do server sở hữu, có quyền filesystem/sandbox phù hợp. Cần thêm chiến lược ngăn symlink race, kiểm soát dung lượng/quota, scan theo chính sách và xử lý file lỗi/partial write.

## 12. Không tin rằng `normalize()` đã giải quyết bảo mật path

Trong Java NIO, `Path.normalize()` loại bỏ các thành phần đường dẫn cú pháp như `.` và `..`; `toAbsolutePath()` tạo dạng tuyệt đối dựa trên môi trường. Hai lệnh này **không tự xác minh authorization**, không ngăn mọi trường hợp symbolic link/race và không chứng minh file nằm trong root thực được phép.

Ví dụ khi phục vụ download từ resource ID:

```text
request resourceId
        |
        v
authenticate user
        |
        v
authorize resourceId (domain policy)
        |
        v
map ID to server-owned storage key/path
        |
        v
open/read with controlled filesystem permissions
        |
        v
return bytes or safe public error DTO
```

Không dùng raw path do client cung cấp làm chứng cứ quyền truy cập. Nếu ứng dụng xử lý path trong thư mục đối thủ có thể sửa, cân nhắc cơ chế directory handle/phân quyền thích hợp; `SecureDirectoryStream` chỉ dùng khi provider hỗ trợ và còn cần thiết kế chống race tương ứng.

Ẩn path khỏi lỗi là một phần **giảm lộ thông tin**, không thay thế bảo vệ truy cập file thật.

## 13. Các nguồn rò rỉ gián tiếp

Ngay cả khi service đã không trả `ex.getMessage()`, một lớp khác có thể làm lộ:

- Default error page bật debug với `trace` hoặc `exception`.
- JSON serializer gặp DTO có field `Throwable`.
- HTTP filter thêm `X-Error-Detail` nguyên nhân lỗi.
- Middleware phản chiếu query string hoặc upload filename.
- Tracing SDK gửi raw request/exception đến bên thứ ba.
- UI hiển thị nguyên error body từ HTTP client.
- Email hỗ trợ đính kèm toàn bộ server log.
- Structured log exporter đưa raw `path` vào dashboard truy cập rộng.

Cần có **error boundary tập trung** cho mọi exception chưa được phân loại. Dùng response schema allowlist, kiểm tra các HTTP header, tắt trang debug ngoài development, kiểm tra error handler ở framework và hạn chế dữ liệu logging/tracing. Một status 500 không tự bảo vệ nội dung của body.

## 14. Redaction hỗ trợ, nhưng không phải tấm chắn duy nhất

**Redaction** loại bỏ hoặc thay thế một số field như `Authorization`, token hay PII. Khi buộc phải nhận một object raw, có thể redact các trường theo schema trước khi log.

Tuy nhiên regex trên một message bất kỳ không phải chính sách bảo mật hoàn chỉnh:

```java
String sanitized = message.replaceAll(
        "token=[^ ]+", "token=[REDACTED]");
```

Snippet trên **không đủ an toàn** khi token có xuống dòng, encoding, dấu phân cách khác hoặc nằm trong `cause`; path Windows/Unix, JSON/XML và thông tin cá nhân không có một mẫu tổng quát đáng tin.

Thứ tự ưu tiên:

1. Không tạo field public từ raw exception ngay từ đầu.
2. Chọn status/code/message từ bảng cố định.
3. Tối thiểu hóa field log bằng allowlist.
4. Redact những trường bắt buộc phải xử lý ở tầng riêng, kèm kiểm thử.
5. Kiểm soát truy cập log, retention, encryption và nơi xuất.
6. Kiểm tra lại sau thay đổi thư viện/framework vì một error serializer có thể thêm trường mới.

Nếu một hàm tên `sanitize()` được dùng khắp nơi, nó vẫn phải được đánh giá theo dữ liệu đầu vào và đường xuất; **tên hàm không phải bằng chứng chống rò rỉ**.

## 15. Log injection từ nội dung client

Một filename hoặc request ID chưa kiểm soát có thể chứa ký tự xuống dòng:

```text
report.pdf
ERROR admin access granted
```

Nếu hệ thống nối chuỗi trực tiếp vào log, kẻ tấn công có thể tạo bản ghi giả. Đối sách:

- Không log raw input không cần thiết.
- Ưu tiên structured logging có escaping/encoding đúng format.
- Giới hạn độ dài và loại bỏ/kiểm tra các ký tự điều khiển theo policy.
- Không chấp nhận request ID tùy ý từ client nếu không có quy tắc kiểm tra.
- Phân biệt server-generated fields với user-provided fields.
- Giới hạn số lượng event/log và có backpressure tránh log flooding.

`requestId` cố định/được kiểm soát và `eventCode` từ enum thường dễ bảo vệ hơn concatenation của mọi chi tiết request.

## 16. Kiểm thử public error bằng dữ liệu mồi nhạy cảm

Một test tốt chủ động tạo exception có chứa path/token giả, sau đó assert những chuỗi đó **không xuất hiện trong public output**. Không kiểm thử chỉ đường đi thành công.

Tạo **`PublicErrorContractTest.java`**:

```java
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.List;

public class PublicErrorContractTest {
    record PublicError(int status, String code, String message) {
        @Override
        public String toString() {
            return status + "|" + code + "|" + message;
        }
    }

    static PublicError map(Throwable error) {
        if (error instanceof NoSuchFileException
                || error instanceof AccessDeniedException) {
            return new PublicError(404, "RESOURCE_UNAVAILABLE",
                    "Tài nguyên không khả dụng.");
        }
        return new PublicError(500, "STORAGE_ERROR",
                "Không thể hoàn tất thao tác.");
    }

    public static void main(String[] args) {
        String privatePath = "/var/private/billing/customer-42.csv";
        String secretToken = "API_TOKEN_TEST_ONLY_123";

        List<Throwable> errors = List.of(
                new NoSuchFileException(privatePath),
                new AccessDeniedException(privatePath),
                new IOException("token=" + secretToken),
                new IllegalStateException(
                        "source path=" + privatePath));

        int passed = 0;
        for (Throwable error : errors) {
            String text = map(error).toString();
            if (text.contains(privatePath)
                    || text.contains(secretToken)
                    || text.contains("/var/private")
                    || text.contains(error.getClass().getName())) {
                throw new AssertionError("Sensitive detail leaked");
            }
            passed++;
        }

        System.out.println("passed=" + passed);
    }
}
```

```bash
javac -encoding UTF-8 PublicErrorContractTest.java
java PublicErrorContractTest
```

Stdout khi thành công:

```text
passed=4
```

Bài test này là **kiểm tra hợp đồng tối thiểu**, không chứng minh toàn bộ server an toàn. Trong thực tế cần test response JSON/headers/body mà framework thực sự xuất, telemetry/log sink, nested causes, Windows/Unix paths, URI, token, Unicode, control characters, user content trong archive và lỗi bất ngờ từ middleware.

### Checklist kiểm thử theo loại lỗi

| Tình huống | Dữ liệu mồi | Điều kiện kiểm tra |
| --- | --- | --- |
| File không tồn tại | Path riêng | Không có path trong response |
| File bị từ chối | `AccessDeniedException` có path | Không lộ tên file/quyền nội bộ |
| I/O bất thường | `IOException` chứa token mồi | Token không xuất ở response/log công khai |
| Input sai | Filename có `../` | Không phản chiếu raw name |
| Upload quá lớn | Tên file chứa email | Chỉ hiển thị code/giới hạn phù hợp |
| Parser lỗi | Dòng CSV chứa PII giả | Không hiện nội dung file |
| Nested cause | Exception chain có path | Serializer không lộ cause/stack |
| Header injection | ID chứa CR/LF | ID bị từ chối hoặc thay thế |
| Logging | Token trong request body | Không ghi vào sink truy cập rộng |

## 17. Phân tầng trách nhiệm

Một kiến trúc dễ kiểm soát gồm:

**File adapter / repository:** biết `Path`, `Files`, `FileChannel` và nguyên nhân I/O thật. Không tự quyết định public message.

**Service nghiệp vụ:** phân loại lỗi theo quyền người dùng, resource và ngữ nghĩa. Quyết định trường hợp nào nên trả 403, trường hợp nào 404 để hạn chế enumeration.

**API boundary:** bắt lỗi đã phân loại và lỗi chưa biết, tạo `PublicError` chỉ có status/code/message/requestId allowlisted. Không đưa `Throwable` trực tiếp vào serializer.

**Diagnostic logging:** ghi event và ID có kiểm soát; audit log riêng nếu cần, với phân quyền và retention. Thông tin chi tiết chỉ được phép theo chính sách bảo vệ rõ ràng.

**Security tests và monitoring:** kiểm tra đường lỗi thật, hệ thống log/exporter và cấu hình framework sau deploy. Không chỉ test một hàm xử lý exception đơn lẻ.

## 18. Những lỗi thiết kế phổ biến

1. Trả `IOException.getMessage()` trực tiếp cho người dùng.
2. Nối `Path.toAbsolutePath()` vào thông báo 404.
3. Serialize `Throwable` hoặc `StackTraceElement[]` trong DTO.
4. Hiển thị trang debug lỗi 500 ở production.
5. Ghi toàn bộ request body/header `Authorization` vào log khi lỗi parser.
6. Để raw filename upload thành server path hoặc log public.
7. Tin rằng `getFileName()` loại hết PII.
8. Phân biệt 403/404 không nhất quán trên resource bí mật, gây enumeration.
9. Dùng error code chứa internal path/storage key.
10. Tạo request ID từ user identifier, filename hoặc secret.
11. Cho người biết request ID xem log mà không authorization.
12. Dùng một regex redaction rồi coi tất cả an toàn.
13. Chưa xử lý CR/LF và log injection từ input.
14. Chỉ kiểm tra controller, bỏ qua middleware, header và serializer.
15. Coi stderr, tracing dashboard hoặc ticket support là kênh không bao giờ công khai.
16. Ghi email/PII thành label metrics, gây rò rỉ và high-cardinality.
17. Dựa vào `Path.normalize()` để thay authorization/symlink safety.
18. Không kiểm tra lỗi chưa dự kiến và stack trace tự động của framework.

## 19. Bài tập thực hành

**Bài A — sửa anti-pattern.** Thay mã sau bằng một public DTO không chứa path/message:

```java
catch (IOException ex) {
    return "Failed: " + path + " / " + ex.getMessage();
}
```

**Bài B — resource bí mật.** Hãy thiết kế phản hồi cho hai trường hợp cùng một ID: không tồn tại và không có quyền xem. Khi nào bạn chọn cùng một 404? Làm sao đảm bảo phép kiểm tra quyền vẫn được thực thi?

**Bài C — filename chứa PII.** Khách hàng tải lên tệp tên chứa họ tên và thông tin y tế. Tại sao tên này không nên dùng trong log công khai? Tạo một storage key phía server và một display name có chính sách riêng.

**Bài D — error serializer.** Một framework trả JSON có `message` và `trace` từ exception. Tạo DTO chỉ có `status`, `code`, `message`, `requestId`; test trên response thực.

**Bài E — chống log injection.** Client gửi request ID chứa dấu xuống dòng và chuỗi giả mạo log. Hãy mô tả cách giới hạn độ dài/charset hoặc thay bằng ID server-generated.

**Bài F — kiểm thử đa nền tảng.** Thêm dữ liệu mồi path Windows, path Linux, UNC path, URL riêng, token và tên file Unicode vào test; đảm bảo không xuất chuỗi đó ở response.

## 20. Tài liệu tham khảo

- [Java SE 17 — FileSystemException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystemException.html): `getFile()`, `getOtherFile()`, `getReason()`.
- [Java SE 17 — NoSuchFileException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/NoSuchFileException.html).
- [Java SE 17 — AccessDeniedException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/AccessDeniedException.html).
- [Java SE 17 — IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html).
- [Java SE 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html).
- [OWASP Error Handling Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Error_Handling_Cheat_Sheet.html).
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html).

## 21. Ghi nhớ

Thông báo công khai cần trả lời **người dùng có thể làm gì tiếp**, không phản chiếu hệ thống lưu file ở đâu, tài nguyên riêng có tồn tại hay không, hoặc token/request payload nào gây ra exception. `NoSuchFileException`, `AccessDeniedException`, `FileSystemException`, `IOException` có thể chứa path, message OS và nested causes; không đưa raw exception vào UI/HTTP/email/webhook.

Mô hình đúng là **lỗi I/O → phân loại và kiểm tra quyền → ánh xạ status/code/message từ allowlist → thêm requestId an toàn → ghi diagnostic có kiểm soát → kiểm thử các nhánh thất bại**. Log, telemetry và thông báo hỗ trợ cũng cần được bảo vệ. Việc ẩn path trong một catch block chỉ là một bước; an toàn thực tế cần phân quyền, kiểm soát dữ liệu xuất ra, chống injection, chính sách log và test hệ thống.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-io-design-010">Phân biệt quyền hệ điều hành với các cơ chế bảo mật Java lịch sử</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt quyền hệ điều hành với các cơ chế bảo mật Java lịch sử

## 1. Vì sao phải phân biệt hai lớp bảo vệ?

Trong Java Core I/O, một ứng dụng đọc file, ghi log, tạo thư mục hay mở socket không chỉ chịu quy tắc của ngôn ngữ Java. Nó chạy **bên trong một tiến trình do hệ điều hành quản lý**, với tài khoản, quyền truy cập, mount, sandbox hoặc chính sách hệ thống cụ thể.

Ngày trước, Java còn có cơ chế **`SecurityManager` + `Policy` + `Permission` + `AccessController`** để hạn chế **code chạy trong cùng JVM** theo các quyền do Java xét. Cơ chế đó khác với quyền filesystem do hệ điều hành thực thi, và hiện không còn là hướng xây dựng sandbox mới:

- Trong **JDK 17**, `SecurityManager` và API liên quan được **deprecated for removal** theo **JEP 411**. Ở các JDK còn hỗ trợ, nếu không có Security Manager đang được bật, cơ chế policy kiểm tra quyền kiểu lịch sử không cung cấp lớp sandbox như nhiều ví dụ Java cũ mô tả.
- Từ **JDK 24**, `SecurityManager` bị **vô hiệu hóa vĩnh viễn** theo **JEP 486**: không thể bật bằng tùy chọn khởi động và không thể cài Security Manager mới lúc runtime; API cũ còn tồn tại trong một số phiên bản cho tương thích nhưng hành vi đã thay đổi.
- **Quyền của hệ điều hành vẫn hoạt động bình thường** trong cả hai trường hợp: nếu tiến trình không có quyền mở file, hệ điều hành/filesystem có thể từ chối dù JVM không có Security Manager.

Bài tập trung vào **thiết kế I/O an toàn**: hiểu ai thực sự cấp/từ chối quyền, xử lý lỗi đúng, quản lý danh tính tiến trình, giới hạn path và cách thay thế những kỳ vọng sai về Java sandbox lịch sử.

> **Quy tắc nhớ ngay:** “Không có `SecurityManager`” **không** có nghĩa Java có thể đọc mọi file. “Có `FilePermission`” **không** có nghĩa bạn đã cấp quyền ở hệ điều hành.

## 2. Hai lớp quyền: hình dung đường đi của một lệnh đọc

Một chương trình gọi:

```java
String text = Files.readString(
        Path.of("/srv/app/config.txt"),
        StandardCharsets.UTF_8);
```

Đây là đoạn minh họa, dùng import `java.nio.file.Files`, `Path` và `StandardCharsets`, cần xử lý `IOException`. Trên một JVM hiện đại, đường đi khái quát:

```text
Java application
   |
   | Files.readString(path)
   v
Java NIO provider / native filesystem API
   |
   v
Hệ điều hành + filesystem + mount
   |
   +-- Danh tính tiến trình: UID/GID, token/privileges...
   +-- Quyền file và thư mục: mode/ACL/ownership
   +-- Mount read-only, sandbox, container, security policy...
   |
   +-- Cho phép -> đọc được bytes
   |
   +-- Từ chối  -> lỗi I/O hoặc lỗi phù hợp provider
```

Trong một JVM **cũ có Security Manager đang hoạt động**, còn có thể có lớp kiểm tra `Permission` của Java trước khi tới hệ điều hành. Nhưng ngay cả khi Java cho phép, **OS vẫn có quyền từ chối**. Java permission là một lớp kiểm soát bổ sung, không phải “vé thông hành” để bỏ qua ACL của OS.

### Bảng đối chiếu

| Câu hỏi | Quyền hệ điều hành | Java Security Manager lịch sử |
|---|---|---|
| Ai áp đặt? | Kernel/filesystem/OS security subsystem | Cơ chế kiểm tra permission bên trong Java runtime khi được bật |
| Danh tính chính | User/group/token/process và quyền liên quan | ProtectionDomain, CodeSource, policy và ngữ cảnh stack |
| Đối tượng thường bảo vệ | File, directory, socket, process, device, syscall, mount | Thao tác Java được instrument để kiểm tra `Permission` |
| Ví dụ | POSIX mode, ACL, Windows access token + DACL | `FilePermission`, `SocketPermission`, `RuntimePermission` |
| Khi Java gọi Files.readString | OS kiểm tra khả năng mở/đọc | JDK cũ **có thể** xét Java permission nếu Security Manager đang bật |
| Có thể vượt OS ACL? | Không: OS là nguồn quyết định cuối cùng về tài nguyên | Không |
| Tình trạng trong JDK 24+ | Vẫn có hiệu lực | Security Manager không thể được bật |
| Phạm vi bảo vệ | Tiến trình, user, namespace, OS-level enforcement | Chủ yếu intended sandbox/kiểm soát code trong JVM theo mô hình lịch sử |

Một ứng dụng có thể có **thêm** xác thực, phân quyền nghiệp vụ, giới hạn tên file, kiểm soát tenant hoặc cơ chế trong thư viện. Những lớp đó **khác** cả quyền OS lẫn Security Manager cũ.

## 3. Quyền filesystem ở Linux/Unix: owner, group, others

Với filesystem hỗ trợ mô hình POSIX, file thường có owner, group và các bit quyền:

```text
-rw-r-----  1 appuser appgroup ... config.txt
 ||| ||| |||
 ||| ||| +++-- others: ---
 ||| +++------ group: r--
 +++---------- owner: rw-
```

Ba quyền cơ bản:

- `r` (read): được đọc nội dung file nếu việc truy cập đường dẫn đến được file và không bị lớp kiểm soát khác cấm.
- `w` (write): được sửa nội dung file khi cách mở và các điều kiện filesystem cho phép.
- `x` (execute): với file thực thi có ý nghĩa chạy file; với **thư mục**, có ý nghĩa **traverse/search** mục bên trong.

Quyền của **thư mục** đặc biệt quan trọng:

| Quyền thư mục | Ý nghĩa thường gặp trên POSIX |
|---|---|
| `r` | Liệt kê tên các mục khi điều kiện khác phù hợp |
| `w` | Thay đổi directory entries: tạo/xóa/đổi tên khi kết hợp các quyền cần thiết |
| `x` | Truy cập/traverse tên mục, cần để đi qua thư mục khi resolve path |

Vì thế một file có `r--` chưa chắc đọc được nếu tiến trình không có quyền đi qua thư mục cha. Tương tự, **xóa/rename một file** chủ yếu liên quan quyền trên thư mục chứa nó, không phải cứ file có bit `w` mới xóa được. Quy tắc cụ thể còn liên quan sticky bit, ACL, mount options, ownership và hệ thống bảo vệ khác.

### Các công cụ hệ điều hành (minh họa)

```bash
id
ls -ld /srv/app /srv/app/data
ls -l /srv/app/data/config.txt
stat /srv/app/data/config.txt
getfacl /srv/app/data/config.txt
```

Các lệnh là **ví dụ trên Linux/Unix**, có thể không được cài đầy đủ hoặc có output khác tùy hệ thống. `getfacl` có thể không có sẵn và filesystem có thể không hỗ trợ cùng kiểu ACL. Không chạy `chmod -R 777` hay cấp quyền rộng chỉ để “chữa lỗi Java”.

## 4. Quyền Windows: access token và ACL

Trên Windows, access check thường dựa vào **access token** của tiến trình/luồng và **security descriptor/DACL** của đối tượng file/folder; danh tính có thể bao gồm user, groups, integrity level và privilege đặc thù.

Một số khái niệm cần phân biệt:

- **Owner**: danh tính sở hữu đối tượng, có tác động tới quản trị quyền nhưng không đồng nghĩa được phép mọi thao tác trong mọi điều kiện.
- **DACL/ACE**: các entry allow/deny quyền với các principal; inheritance từ thư mục cha có thể ảnh hưởng.
- **Access token**: danh tính và privileges mà tiến trình thực sự đang dùng; “đang đăng nhập bằng tài khoản admin” không đồng nghĩa tiến trình luôn chạy với đầy đủ quyền elevated.
- **UAC, integrity levels, policy**: có thể bổ sung hạn chế; không quy tất cả lỗi `AccessDeniedException` về một ACE đơn giản.
- **File sharing/locking**: cách mở và handle của tiến trình khác có thể khiến rename/delete/open thất bại ngay cả khi ACL có vẻ cho phép.

`DosFileAttributeView` của Java NIO (read-only, hidden, system, archive) **không phải** bộ ACL đầy đủ của Windows. Đánh dấu read-only không thay thế cấu hình DACL. Muốn làm việc với ACL khi provider hỗ trợ, xem `AclFileAttributeView`, `AclEntry` và `UserPrincipalLookupService`.

Cách kiểm tra quyền Windows thường dùng công cụ quản trị hệ điều hành như `icacls`, PowerShell và giao diện Security của file/folder, với tài khoản chạy ứng dụng thực tế. Không dùng kết quả trên máy developer để suy luận quyền trên production server.

## 5. Java NIO kiểm tra/đọc metadata quyền như thế nào?

Java `java.nio.file.Files` cho phép tương tác với thuộc tính filesystem **khi provider hỗ trợ**:

| API | Mục đích |
|---|---|
| `Files.getOwner(path)` | Đọc owner của file |
| `Files.setOwner(path, principal)` | Yêu cầu đổi owner, cần OS cho phép |
| `Files.getPosixFilePermissions(path)` | Đọc bộ POSIX permissions nếu có view hỗ trợ |
| `Files.setPosixFilePermissions(path, perms)` | Yêu cầu sửa POSIX mode khi có quyền |
| `Files.getFileAttributeView(path, PosixFileAttributeView.class)` | Lấy view quản lý thuộc tính POSIX nếu provider hỗ trợ |
| `Files.getFileAttributeView(path, AclFileAttributeView.class)` | Lấy view ACL nếu được hỗ trợ |
| `Files.isReadable(path)` | Kiểm tra sơ bộ xem JVM có thể đọc file hay không |
| `Files.isWritable(path)` | Kiểm tra sơ bộ khả năng ghi |
| `Files.readAttributes(path, ...)` | Đọc các thuộc tính chỉ định qua provider |
| `Files.createTempFile(..., attrs)` | Có thể tạo file với attributes khởi tạo thích hợp và được hỗ trợ |

**Sử dụng API để xem/sửa quyền không làm JVM có quyền quản trị file vô hạn.** Lời gọi `setOwner` hay `setPosixFilePermissions` cũng bị OS kiểm tra. Khi không hỗ trợ, API có thể ném `UnsupportedOperationException` hoặc ngoại lệ khác theo hợp đồng.

### POSIX permissions trong Java (mã minh họa)

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.nio.file.attribute.PosixFilePermission;

Path path = Path.of("/srv/app/secrets.txt");

// Chỉ chạy trên filesystem/provider hỗ trợ POSIX attributes
// và khi tiến trình có quyền thay đổi permissions.
Set<PosixFilePermission> ownerOnly =
        PosixFilePermissions.fromString("rw-------");

Files.setPosixFilePermissions(path, ownerOnly);
```

Đây là **đoạn mã minh họa**, yêu cầu đặt trong method xử lý `IOException` và phải áp dụng với đối tượng được phép quản trị. Không áp dụng `setPosixFilePermissions` bừa lên file hệ thống hoặc nhầm `"rw-------"` với một “role” độc lập của ứng dụng.

### Kiểm tra provider

```java
var view = Files.getFileAttributeView(
        path,
        PosixFileAttributeView.class);

if (view != null) {
    Set<PosixFilePermission> permissions = view.readAttributes().permissions();
    System.out.println(PosixFilePermissions.toString(permissions));
}
```

Đây là snippet cần import `PosixFileAttributeView`, `PosixFilePermission`, `PosixFilePermissions` và xử lý lỗi. `view != null` chỉ nói provider có API tương ứng cho path; không hứa việc thay đổi permission sau đó sẽ luôn thành công.

## 6. isReadable/isWritable không phải cơ chế cấp quyền

`Files.isReadable(path)` và `Files.isWritable(path)` thường tiện cho UX hoặc quan sát, nhưng **không phải quyết định an ninh bất biến**.

Phản ví dụ:

```java
if (Files.isWritable(target)) {
    Files.writeString(target, content);
}
```

Trong khoảng giữa `isWritable` và `writeString`:

- ACL có thể thay đổi.
- File có thể bị xóa/đổi tên.
- Symlink có thể trỏ sang mục khác.
- Mount có thể chuyển sang read-only.
- Handle khác hoặc policy có thể thay đổi điều kiện thao tác.

Đây là **TOCTOU** (time-of-check to time-of-use). Check trả true không đảm bảo operation sau thành công. Check trả false cũng không nói chính xác vì sao: không tồn tại, không thể xác định, điều kiện truy cập không đủ, hoặc provider không trả lời đáng tin cậy.

Mẫu tốt hơn trong nhiều trường hợp là **thực hiện thao tác được phép trong ranh giới kiểm soát và xử lý ngoại lệ thực tế**:

```java
try {
    String text = Files.readString(target, StandardCharsets.UTF_8);
    // Xử lý text theo schema nghiệp vụ.
} catch (NoSuchFileException ex) {
    // File không còn tồn tại ở thời điểm mở/đọc.
} catch (AccessDeniedException ex) {
    // Filesystem/provider báo từ chối truy cập.
} catch (IOException ex) {
    // Các lỗi I/O khác.
}
```

Đây là đoạn code minh họa, cần imports/biến `target`. Không phải mọi provider trả đúng subclass `AccessDeniedException` khi bị từ chối; `IOException` tổng quát cũng cần được xử lý. Ở JDK 17 với Security Manager lịch sử được bật, một số API còn có thể ném `SecurityException` cho Java permission; không nên nhầm nó với OS ACL.

## 7. Java SecurityManager trong lịch sử hoạt động thế nào?

Trong các Java đời cũ, `SecurityManager` được thiết kế để kiểm soát một số hành động nhạy cảm của code Java theo **permission check**.

Ví dụ các khái niệm:

- `java.io.FilePermission`: biểu diễn quyền như `read`, `write`, `execute`, `delete` đối với file/path theo policy Java.
- `java.net.SocketPermission`: đại diện một số quyền network như connect/accept/listen/resolve trong cơ chế cũ.
- `java.lang.RuntimePermission`: quyền với một số khả năng runtime.
- `java.security.Policy`: nguồn chính sách map nguồn code/protection domain tới permissions.
- `java.security.AccessController` và `AccessControlContext`: liên quan tới stack-based access checks trong mô hình lịch sử.
- `ProtectionDomain`/`CodeSource`: mô tả code và miền quyền trong policy model.

Với Security Manager hoạt động, lời gọi Java I/O có thể đi qua `checkRead`/`checkWrite` hoặc `checkPermission` trước khi tới OS. Nếu policy Java từ chối, code có thể nhận `SecurityException` hoặc subclass. Nếu policy Java cho phép nhưng OS từ chối, I/O vẫn thất bại.

**Đừng lấy mã Java 8 với `System.setSecurityManager(new SecurityManager())` làm công thức cấu hình bảo mật ứng dụng mới.** Ở JDK 17 các API này đã bị deprecated for removal; từ JDK 24 không thể bật cơ chế ấy.

## 8. FilePermission của Java không phải chmod hay ACL

Đây là một lỗi diễn giải phổ biến. Với `java.io.FilePermission` (API lịch sử):

```java
import java.io.FilePermission;

FilePermission permission =
        new FilePermission("/srv/app/data/report.csv", "read");
```

Đoạn trên **chỉ tạo một đối tượng `Permission` trong JVM** để dùng trong mô hình Java security cũ. Nó **không**:

- Gọi `chmod`.
- Đặt POSIX mode `r--------` hay `rw-------`.
- Thêm một ACE vào Windows ACL.
- Cấp quyền truy cập cho process account.
- Làm OS bỏ qua mount read-only hoặc policy.
- Tự kích hoạt `SecurityManager`.
- Đảm bảo tệp tồn tại hoặc đọc được.

Ngược lại, `Files.setPosixFilePermissions(path, perms)` yêu cầu thay đổi **metadata quyền của filesystem**, nếu provider hỗ trợ và hệ điều hành cho phép.

Bảng minh họa:

| Code/khái niệm | Kết quả liên quan quyền |
|---|---|
| `new FilePermission(path, "read")` | Tạo Java permission object, không sửa OS |
| `Policy` cấp FilePermission (JDK cũ có Security Manager) | Có thể cho phép qua lớp Java policy nếu check liên quan được thực thi |
| `chmod 600 secret.txt` trên POSIX | Thay mode của file khi OS cho phép |
| `Files.setPosixFilePermissions(...)` | Thay POSIX permission qua NIO nếu provider hỗ trợ/quyền đủ |
| Windows `icacls` | Xem/sửa ACL theo khả năng và quyền hệ thống |
| Chạy service bằng user ít quyền | OS giới hạn tài nguyên mà process có thể sử dụng |
| Docker/container mount file read-only | Áp ràng buộc từ môi trường chạy theo cơ chế container/host |

Tên `Permission` trong Java rất dễ gây nhầm vì nó giống “permission” của OS. Cần luôn hỏi: **quyền thuộc lớp policy JVM lịch sử hay metadata/enforcement của OS?**

## 9. JDK 17, JDK 24 và hướng phát triển

Sự thay đổi của Security Manager có ý nghĩa trực tiếp khi đọc code, README, khóa học hoặc tutorial Java cũ.

| Dòng thời gian | Trạng thái cần biết |
|---|---|
| Các Java đời cũ | Có cơ chế Security Manager và policy được sử dụng trong một số môi trường sandbox/plugin |
| JDK 17 — JEP 411 | Security Manager cùng API liên quan **deprecated for removal**; không nên dùng cho thiết kế mới |
| JDK 18–23 | Tiếp tục quá trình chuyển dịch với các thay đổi/cảnh báo theo từng release; tham khảo docs phiên bản chính xác |
| JDK 24 — JEP 486 | **Permanently disable Security Manager**; không thể bật lúc startup hoặc runtime |
| JDK sau 24 | Theo hướng dẫn migration, Java native Security Manager không phải giải pháp sandbox cho ứng dụng hiện đại; API legacy có thể còn để chuyển tiếp nhưng không có cơ chế quản lý policy như trước |

Khi kiểm tra JVM thực tế:

```bash
java -version
```

Đừng viết một phần mềm mới yêu cầu `-Djava.security.manager=allow` để bảo vệ plugin rồi chạy nó trên JDK 24+: cơ chế này **không thể được bật**. Trên JDK 17, những cách bật/cấu hình còn tồn tại chủ yếu phục vụ tương thích/migration, không phải lời khuyên kiến trúc dài hạn.

### JEP 411 vs JEP 486

- **JEP 411 — Deprecate the Security Manager for Removal**: từ JDK 17, cảnh báo và định hướng loại bỏ.
- **JEP 486 — Permanently Disable the Security Manager**: JDK 24 chặn việc kích hoạt; các API liên quan có hành vi degraded để hỗ trợ quá trình chuyển tiếp.

**Permanently disabled không nhất thiết nghĩa mọi class API đã bị xóa khỏi bytecode ngay trong JDK 24.** Phải phân biệt “vô hiệu hóa chức năng” với “xóa class khỏi JDK”. Khi migration, kiểm tra release notes theo phiên bản đang sử dụng.

## 10. AccessController.doPrivileged từng dùng để làm gì?

Trong kiến trúc Security Manager lịch sử, stack-based checks có thể xem xét các protection domains trong call stack. `AccessController.doPrivileged` dùng để thực hiện đoạn code với ngữ nghĩa truy cập đặc biệt trong bối cảnh Java policy.

Ví dụ kiểu tài liệu cũ:

```java
String value = AccessController.doPrivileged(
        (PrivilegedAction<String>) () -> System.getProperty("user.home"));
```

**Đây là snippet lịch sử, không khuyến nghị cho code mới.** Cần import `AccessController` và `PrivilegedAction`, compiler JDK 17 có thể cảnh báo deprecated.

Điểm cần phân biệt:

- `doPrivileged` không cấp quyền đọc tệp theo OS cho process.
- Nó không giúp thoát khỏi UID/GID, Windows DACL, read-only mount hoặc sandbox của container.
- Từ JDK 24, khi Security Manager không còn hoạt động, các overload phổ biến của `doPrivileged` chỉ chạy action ngay theo hành vi không có Security Manager, không còn là ranh giới đặc quyền JVM như thiết kế ban đầu.
- Đừng dùng `doPrivileged` để “bọc” thao tác file bị `AccessDeniedException`; đó không phải cách sửa quyền hệ thống.

Khi thấy code legacy lạm dụng `doPrivileged`, hãy xem xét **loại bỏ phụ thuộc vào Security Manager**, rồi thiết kế quyền tiến trình/kiểm soát nghiệp vụ tương ứng thay vì chỉ xóa cảnh báo compiler.

## 11. Không có SecurityManager không đồng nghĩa JVM an toàn hoặc không an toàn tuyệt đối

Java runtime có những cơ chế an toàn khác với Security Manager:

- **Type safety, bytecode verification, access modifiers, module boundaries**: bảo vệ tính đúng đắn/giới hạn một số tương tác ở mức ngôn ngữ và runtime.
- **Quyền OS, process account, filesystem permissions, networking controls**: kiểm soát thực tế việc truy cập tài nguyên hệ thống.
- **Xác thực và phân quyền ứng dụng**: quyết định user/tenant nào được thực hiện thao tác nghiệp vụ.
- **Crypto, TLS, key management**: bảo vệ tính bí mật/toàn vẹn và xác thực theo giao thức.
- **Process isolation/container/VM**: tạo ranh giới độc lập hơn với các chương trình ít tin cậy.

Các cơ chế này giải quyết **những loại rủi ro khác nhau**, không thay thế tất cả lẫn nhau.

Một class `private` không ngăn cùng tiến trình có quyền OS đọc file mật khẩu nếu code có khả năng I/O và được gọi không đúng chỗ. Ngược lại, bảo vệ file bằng mode `600` cũng không ngăn chính ứng dụng có quyền đọc file ấy vô tình log secret ra ngoài hoặc gửi tới user sai tenant.

Đây là hai lớp kiểm tra cần độc lập: **authorization nghiệp vụ** và **authority của process**.

## 12. Chọn danh tính tiến trình theo nguyên tắc least privilege

Thay vì trông chờ Java policy để cấm từng thư viện, ứng dụng nên được chạy với **ít quyền hệ điều hành nhất đủ thực hiện công việc**.

Ví dụ service chỉ cần đọc file cấu hình và ghi log:

```text
Process identity: app-service

Allowed:
  /etc/my-app/config.yaml       read
  /var/lib/my-app/data/         read/write theo nghiệp vụ
  /var/log/my-app/              append/write theo chính sách
  outbound network             chỉ các đích được cho phép nếu có egress control

Not necessary:
  /root/, user home khác, /etc/shadow
  /var/run/docker.sock
  host device nodes
  quyền thay owner/chmod rộng
```

Một triển khai production có thể dùng:

- Dedicated service user, không chạy root/Administrator trừ khi có lý do bắt buộc.
- POSIX mode + ACL hoặc Windows DACL theo thư mục/file thực tế.
- Container run-as non-root; tránh mount thư mục host nhạy cảm và container privileged.
- Read-only root filesystem khi ứng dụng chỉ cần một vài writable directories.
- Giới hạn network egress/ingress, capabilities và namespace theo nền tảng.
- Secret injection và quyền đọc file secret hẹp; không đóng gói secret trực tiếp trong artifact.
- Audit logs và monitoring các lỗi `AccessDeniedException` mà không in nội dung bí mật.

**Process user trong container không tự động có cùng ý nghĩa với user trên host.** UID mapping, volume mounts, Kubernetes securityContext, SELinux/AppArmor và cấu hình platform cần được kiểm tra theo môi trường.

### Nguy hiểm của “chạy admin để hết AccessDenied”

Việc tăng quyền cho toàn bộ JVM có thể làm lỗi tạm biến mất nhưng **mở rộng blast radius** khi ứng dụng có path traversal, deserialization vulnerability hoặc dependency bị tấn công. Sửa quyền chính xác vào thư mục cần dùng, hoặc thay đổi vị trí lưu file theo cấu trúc phù hợp thay vì nâng đặc quyền toàn tiến trình.

## 13. Ví dụ Java 17+ hoàn chỉnh: thao tác file và giải thích ranh giới quyền

Chương trình sau chạy **không dùng SecurityManager**. Nó tạo file tạm dưới thư mục mà OS cho phép, viết và đọc bằng NIO, rồi minh họa một phép kiểm tra path **chỉ ở mức từ vựng (lexical)**. Ví dụ **không** cố tạo lỗi permission bằng cách thay quyền OS, vì kết quả phụ thuộc hệ điều hành/user/ACL.

Lưu tên **`OsVsJavaPermissionsDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class OsVsJavaPermissionsDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("os-permissions-");
        Path file = directory.resolve("record.txt");

        try {
            Files.writeString(
                    file,
                    "approved-data\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

            String loaded = Files.readString(
                    file, StandardCharsets.UTF_8);

            Path relative = Path.of("reports", "..", "record.txt");
            Path candidate = directory.resolve(relative).normalize();

            System.out.println("Read: " + loaded.strip());
            System.out.println("Lexically within directory: "
                    + candidate.startsWith(directory));
            System.out.println("Same normalized target: "
                    + candidate.equals(file));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac OsVsJavaPermissionsDemo.java
java OsVsJavaPermissionsDemo
```

**Stdout chính xác nếu OS cho phép tạo/đọc/ghi/xóa trong temp directory:**

```text
Read: approved-data
Lexically within directory: true
Same normalized target: true
```

Giải thích:

1. `Files.createTempDirectory` yêu cầu OS cấp phép tạo một thư mục tạm mới. Nếu temp dir không writable, chương trình có thể ném `IOException` trước khi in.
2. `CREATE_NEW` tạo file chưa tồn tại, tránh vô tình truncate một file có sẵn tại tên `record.txt`. Nó không tự cung cấp một sandbox chống các tấn công bằng đường dẫn ngoài thư mục tin cậy.
3. `Files.readString` đọc qua provider theo quyền thực của process. Không có Java `FilePermission` nào được sử dụng để “cấp” quyền OS.
4. `normalize()` bỏ phần `reports/..` ở mức cấu trúc chuỗi path; `startsWith(directory)` trả true cho ví dụ đơn giản này.
5. **Kiểm tra lexical trên không phải chứng minh chống symlink/TOCTOU**. Nó chỉ cho thấy một kỹ thuật lọc sơ bộ, tuyệt đối không dùng riêng cặp `normalize()+startsWith()` như ranh giới bảo mật hoàn chỉnh với path do attacker kiểm soát.
6. Mọi temp resources được xóa ở `finally` khi chương trình chạy bình thường. Khi kill đột ngột/crash, `finally` có thể không chạy; ứng dụng production cần chiến lược cleanup khác.

Mã này độc lập, dùng Java 17+ và không có thao tác admin; không đưa `SecurityManager` vào vì nó không phải nền tảng xây ứng dụng mới.

## 14. POSIX FileAttributeView: tạo file riêng tư từ đầu

Nếu file lưu secret trên provider hỗ trợ POSIX, có thể yêu cầu attributes khi **tạo file** thay vì tạo file với quyền rộng rồi sửa lại:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;

Path directory = Path.of("/srv/app/secrets");

FileAttribute<?> ownerOnly =
        PosixFilePermissions.asFileAttribute("rw-------");

Path secret = Files.createTempFile(
        directory,
        ".secret-",
        ".tmp",
        ownerOnly);
```

Đây là **đoạn minh họa**, không chạy độc lập; thư mục phải tồn tại, có quyền tạo, và provider hỗ trợ POSIX attributes. Trên Windows filesystem/view không hỗ trợ, yêu cầu attributes có thể ném `UnsupportedOperationException`. Cần chọn biện pháp Windows ACL phù hợp chứ không bắt IOException rồi tự động tạo một file secret với quyền mặc định rộng.

**Lợi ích:** đặt permission trong thao tác tạo file (nếu provider hỗ trợ) giúp tránh khoảng thời gian file nhạy cảm tồn tại với permission không phù hợp trước lúc gọi `setPosixFilePermissions`.

Tuy nhiên, chế độ đọc/ghi thực tế vẫn có thể bị hạn chế bởi ACL/mount/SELinux, và root/elevated principal có thể có những quyền khác. `rw-------` không phải bảo đảm “không user nào khác trên thế giới có thể truy cập dưới mọi cơ chế”.

### Khi filesystem không hỗ trợ POSIX view

Đừng gắn chặt ứng dụng đa nền tảng vào `PosixFilePermissions` mà không kiểm tra môi trường. Có thể:

1. Xác định yêu cầu bảo mật của file.
2. Kiểm tra view/provider hỗ trợ.
3. Nếu yêu cầu “owner only” là bắt buộc nhưng không thể cấu hình, **fail closed** thay vì tạo file nhạy cảm với quyền không kiểm soát.
4. Dùng nền tảng lưu secret/ACL đúng OS hoặc chuyển sang môi trường có isolation phù hợp.

## 15. AccessDeniedException, SecurityException và các lỗi khác

Các lỗi Java I/O dễ bị hiểu nhầm:

| Loại lỗi | Thường thể hiện | Có luôn do Java SecurityManager? |
|---|---|---|
| `AccessDeniedException` | Filesystem/provider báo không được phép truy cập path | Không |
| `FileSystemException` | Vấn đề filesystem, có thể chứa reason/otherFile | Không |
| `NoSuchFileException` | Mục không tồn tại khi thao tác | Không |
| `FileAlreadyExistsException` | Tạo mới với tên đã tồn tại | Không |
| `SecurityException` | Lỗi quyền/kiểm soát đặc thù API/runtime, trong các JDK cũ có thể từ security manager | Không được mặc định suy ra OS ACL |
| `UnsupportedOperationException` | Provider/API không hỗ trợ thao tác/attribute view | Không |
| `IOException` | Nhóm lỗi I/O rộng hơn | Không |

Những phương thức `Files.*` thường nêu `SecurityException` trong tài liệu lịch sử khi có Security Manager đang bật. Ở JDK 24+ bạn không được dùng sự tồn tại của khai báo ấy để kết luận một Java policy đang bảo vệ file. Ngược lại, khi OS từ chối, mã thường nhận exception loại I/O; xử lý theo provider và message thực tế.

**Không viết logic kiểu: “Bắt SecurityException rồi cấp FilePermission là hết lỗi”.** Việc cấp quyền thực phải giải quyết qua OS/admin hoặc policy ứng dụng, không được thực hiện ngầm bởi mã không đủ đặc quyền.

### Logging an toàn

Log thông tin có ích: operation, mã lỗi, path đã redacted khi cần, principal của service theo quy trình quan sát phù hợp và correlation ID. Không log toàn bộ nội dung file bí mật, password hay access token để chẩn đoán permission failure.

## 16. Path traversal là lỗi ứng dụng, không phải lỗi OS permissions

Giả sử web endpoint nhận tên file từ người dùng:

```text
GET /download?file=invoice.pdf
```

Nếu dùng trực tiếp:

```java
Path target = root.resolve(userInput);
byte[] bytes = Files.readAllBytes(target);
```

attacker có thể thử `../secret.txt` hoặc dạng tương đương trên OS đích.

Nếu tiến trình **có quyền OS** đọc file secret đó, OS sẽ không từ chối chỉ vì người gọi HTTP không được phép xem nó. Kernel nhìn thấy **một request từ process account**, không biết user HTTP nào đang yêu cầu.

Ứng dụng cần kiểm soát:

1. Xác thực danh tính user/request.
2. Phân quyền theo tenant/document ID và trạng thái nghiệp vụ.
3. Không sử dụng path tùy ý như đối tượng authority; tốt hơn map ID đã được xác thực tới file do ứng dụng quản lý.
4. Giới hạn file root cố định, phân biệt file và thư mục, tránh symlink escape.
5. Xử lý race giữa check và open, đặc biệt ở thư mục attacker có thể ghi.
6. Vẫn chạy process dưới account ít quyền, để OS chặn phần lớn đường dẫn không thuộc phạm vi service.

### Kiểm tra lexical: cần nhưng chưa đủ

```java
Path base = root.toAbsolutePath().normalize();
Path candidate = base.resolve(userProvidedName).normalize();

if (!candidate.startsWith(base)) {
    throw new IllegalArgumentException("Path outside allowed root");
}
```

Đây là snippet minh họa. Nó chặn một số trường hợp `..` sau khi normalize nhưng **không chứng minh đường dẫn thực nằm trong root** nếu attacker có thể tạo/thay đổi symlink hoặc file trong thư mục. Ngoài ra, phải xử lý absolute input policy, case/volume semantics và encoding/canonicalization nhất quán.

Nếu provider hỗ trợ `SecureDirectoryStream`, có thể thực hiện một số thao tác tương đối so với directory handle để giảm race do thay tên/symlink. Không phải mọi provider hỗ trợ; thiết kế chặt hơn thường gồm quyền thư mục hạn chế, không để attacker sửa namespace, kiểm tra theo file descriptor/handle và isolation OS. Không dùng `toRealPath()` một lần rồi tin path bất biến cho mọi I/O về sau.

## 17. Thư mục tạm, file nhạy cảm và symlink

`Files.createTempFile` là API tốt hơn cách tự ghép tên như `secret-<timestamp>.tmp` vì API tạo một file mới với tên duy nhất theo semantics provider, giảm khả năng collision.

Nhưng thiết kế an toàn còn cần:

- Chọn **thư mục tin cậy**, có quyền phù hợp với tài khoản chạy service.
- Nếu tạo file secret, bảo đảm permission/ACL thích hợp **ngay khi tạo** nếu provider hỗ trợ.
- Không dùng tên cố định trong thư mục world-writable mà không có chiến lược chống race.
- Không lấy `/tmp` làm chỗ để lưu secret lâu dài mặc định; hiểu sticky bit, policy cleanup và quyền hệ điều hành.
- Không truyền path tạm cho tiến trình khác rồi giả định nó không thể thay nội dung trước khi file được đọc lại.
- Kiểm soát symlink/hardlink và file thay thế ở những đường dẫn attacker có thể tác động.
- Đóng file handle, bảo đảm dữ liệu không bị công bố trước khi validate.
- Nếu cần thay thế file quan trọng, cân nhắc write-temp-then-rename, atomic move khi hỗ trợ, và yêu cầu durability riêng.

Lưu ý: **atomic rename, file permission và quyền truy cập ứng dụng là ba vấn đề độc lập**. Một file được đổi tên nguyên tử vẫn có thể mang ACL sai; một file có ACL đúng vẫn có thể chứa dữ liệu nghiệp vụ sai; một app xác thực user đúng vẫn cần chống race trên filesystem.

## 18. Không dựa vào “sandbox Java” để chạy plugin không tin cậy

Một yêu cầu thường gặp là “chạy đoạn Java do người dùng gửi nhưng không cho đọc file”. Trong Java đời cũ từng có mô hình sandbox cùng JVM dựa trên Security Manager. Với Java hiện đại:

- Không dùng `SecurityManager` làm ranh giới bảo mật mới; từ JDK 24 không thể bật.
- Cùng một JVM thường không phải security boundary phù hợp để thực thi mã tùy ý có thể dùng các API mạnh.
- ClassLoader, module access, Java visibility, bytecode verification không cung cấp cách thay thế tương đương để ngăn code không tin cậy truy cập OS resources.
- Cần **cô lập ở mức tiến trình/OS** với account ít quyền, filesystem namespaces/mounts, sandbox/container/VM, network policy, CPU/memory/time quotas và giám sát.
- Với code thật sự không tin cậy, container bản thân nó không tự động tạo isolation mạnh nếu chạy privileged, mount socket host, cho quyền cao hoặc tắt security restrictions.
- Không để secret production nằm trong cùng filesystem namespace hoặc môi trường của tiến trình chạy code không tin cậy khi không có lý do bắt buộc.
- Quản lý cleanup của child process, timeout, stdout/stderr, quota và tránh cho phép child truy cập runtime/host control sockets.

Thành phần lập trình có thể hạn chế “API nào được phép gọi” vẫn hữu ích để thực thi **business rules**, nhưng không nên quảng cáo nó là sandbox vững chắc trước attacker có thể chạy bytecode tùy ý cùng quyền OS của tiến trình.

## 19. Xác thực/authorization của người dùng ứng dụng là lớp thứ ba

Một server chạy bằng user `app-service` có thể đọc toàn bộ thư mục `/var/lib/app/orders`. Nhưng khi Alice gọi `GET /orders/123`, việc Alice có quyền xem order 123 phải do ứng dụng xác định:

```text
HTTP request từ Alice
   |
   v
Authentication: Alice là ai?
   |
   v
Authorization: Alice có quyền xem order 123?
   |
   v
Lấy path từ ID đã được kiểm tra theo domain model
   |
   v
Filesystem open/read dưới quyền OS của app-service
   |
   v
Response chỉ chứa dữ liệu đã được phép tiết lộ
```

OS không tự biết “order 123 thuộc Alice”. Nếu ứng dụng bỏ qua bước authorization, lệnh đọc file vẫn có thể thành công vì process service được phép. Đây là lỗi **IDOR/broken object-level authorization**, không thể giải quyết chỉ bằng chmod cho một user service chung.

Với hệ thống multi-tenant:

- Map tenant + resource ID ở server side.
- Kiểm tra quyền trên từng thao tác read/write/delete.
- Tránh dùng raw filename hoặc đường dẫn làm object ID có thể chỉnh tùy ý.
- Giới hạn worker/service account tới thư mục chứa dữ liệu cần thiết, hoặc cô lập theo tenant khi yêu cầu cao.
- Audit truy cập dữ liệu với actor ứng dụng **và** process/service identity, không trộn hai danh tính.

## 20. ACL trong Java NIO: khác gì với POSIX mode?

`AclFileAttributeView` cung cấp cách làm việc với danh sách `AclEntry` khi filesystem hỗ trợ:

```java
AclFileAttributeView view = Files.getFileAttributeView(
        path,
        AclFileAttributeView.class);

if (view != null) {
    List<AclEntry> entries = view.getAcl();
    for (AclEntry entry : entries) {
        System.out.println(entry.principal() + ": " + entry.permissions());
    }
}
```

Đây là đoạn minh họa, cần import `AclFileAttributeView`, `AclEntry`, `List`, `Files`, `Path` và xử lý `IOException`; biến `path` giả định được khai báo.

Không kết luận:

- `getAcl()` trả một list nghĩa là Windows ACL được mô phỏng y hệt POSIX `rwx`.
- Thứ tự/loại ACE không ảnh hưởng việc đánh giá quyền; ACL có các quy tắc phức tạp.
- Mọi provider đều cho phép `setAcl`.
- Có thể sử dụng `AclEntryPermission.READ_DATA` để “trao quyền” bằng cách tự tạo một đối tượng enum mà không sửa metadata.
- Chuyển nguyên xi ACL từ hệ điều hành này sang hệ khác luôn giữ semantics.

Để sửa ACL, cần có hiểu biết về principals, ACE allow/deny, inheritance và quyền thay đổi security descriptor trên nền tảng thực. Không tự động thêm entry `ALLOW ALL` để giải quyết lỗi ghi file trong production.

## 21. Quyền thư mục và atomic file updates

Giả sử ứng dụng ghi file cấu hình một cách an toàn:

1. Tạo file tạm trong thư mục đích.
2. Ghi dữ liệu mới và validate.
3. Đồng bộ temp file nếu yêu cầu durability.
4. Di chuyển temp để thay tên đích theo atomic rename khi được hỗ trợ.
5. Kiểm tra/giữ quyền file mới phù hợp; dọn temp khi lỗi.

OS có thể kiểm tra nhiều khả năng khác nhau:

- Tạo temp yêu cầu quyền thích hợp trên **thư mục**.
- Ghi nội dung yêu cầu được phép mở/ghi **file**.
- Rename/replace yêu cầu quyền trên directory entries và có thể phụ thuộc ACL/handle.
- Đồng bộ dữ liệu có thể bị hạn chế bởi provider/filesystem.
- Đổi owner hoặc ACL của file mới không mặc định được phép cho process.

Một chi tiết đáng chú ý: khi tạo **file tạm mới rồi thay thế file đích**, metadata owner/ACL của temp có thể trở thành metadata của file đang ở tên đích. Không nên tưởng atomic replace tự giữ nguyên ACL cũ. Cần đặt metadata phù hợp **trước** khi công bố nếu bài toán yêu cầu.

**Không nên để Java tự xóa file đích rồi rename temp** để “vượt lỗi permissions”. Cách đó vừa có thể làm mất bản cũ vừa tạo khoảng trống reader nhìn thấy, và nếu OS không cho phép thì thao tác vẫn thất bại.

## 22. Sự khác nhau giữa Java module/security và OS access control

Java Platform Module System (`module-info.java`) cho phép khai báo `requires`, `exports`, `opens`... để kiểm soát khả năng truy cập code/package qua module boundaries. Đây **không phải** hệ thống cấp phép đọc file.

Ví dụ:

```java
module com.example.viewer {
    requires java.base;
}
```

Đây là ví dụ `module-info.java`, không phải chương trình runnable độc lập. Module `java.base` vốn được đọc mặc định; khai báo chỉ nhằm minh họa cú pháp.

Không suy ra rằng vì module không `requires java.io`, code không thể đọc filesystem: `java.io` là một **package** trong `java.base`, không phải một module riêng. Cũng không suy ra `exports` hay `opens` là quyền OS. Java module boundaries hướng tới encapsulation, readability và reflective access constraints; ranh giới quyền I/O cần OS/process/application policy.

Tương tự, `private`, `protected`, classpath isolation hay package visibility không tạo một filesystem sandbox trước code có khả năng chạy trong cùng JVM và cùng OS principal.

## 23. So sánh lỗi thực tế bằng bảng chẩn đoán

| Triệu chứng | Khả năng cần kiểm tra | Không nên làm |
|---|---|---|
| `AccessDeniedException` khi đọc tệp | Process user, ACL/owner, parent dir traverse, mount, sandbox, lock/provider | Tạo `new FilePermission` rồi tưởng OS cấp quyền |
| Ghi file bị từ chối trên Linux | Mode/ACL file, dir permissions, read-only mount, SELinux/AppArmor | Chạy toàn app bằng root ngay |
| File có `rw-------` nhưng process khác vẫn đọc được | Owner thực, elevated privileges, namespace, ACL, shared hardlinks, data đã bị sao chép | Xem mode bits là biện pháp mã hóa |
| `SecurityException` trong code Java 8/17 với manager | Java policy/permission check, cấu hình Security Manager, classpath | Nhầm ngay thành thiếu chmod |
| `System.setSecurityManager` không hoạt động JDK 24+ | JEP 486 đã vô hiệu hóa tính năng | Cố tìm flag “bật lại” |
| `Files.isReadable` true nhưng đọc vẫn lỗi | TOCTOU, mount/provider, race, share/lock, path đã đổi | Coi probe là authorization quyết định |
| Một user HTTP xem được file user khác | Application authorization/tenant isolation/path traversal | Chỉ đổi Java SecurityManager policy |
| Plugin tùy ý đọc được secrets | Cùng process account/namespace, thiếu OS isolation | Chỉ dùng ClassLoader để sandbox |
| POSIX permission API unsupported | Filesystem/provider không có view | Bỏ qua yêu cầu file private rồi vẫn công bố |
| Container vẫn thấy host secret | Volume mounts, root/capabilities, securityContext | Tin “đã dùng Docker” là đủ |

## 24. Thực hành: thiết kế các phép thử kiểm soát

Các bài kiểm tra nên tách riêng hành vi hệ điều hành và cơ chế Java:

**Bài 1 — Khác nhau giữa user process.** Tạo file dữ liệu trong môi trường thực hành, đặt mode/ACL chỉ cho service A đọc. Chạy **hai tiến trình thật sự** bằng hai tài khoản/nhóm phù hợp và thử `Files.readString`. Đừng thay đổi file hệ thống hoặc secrets thực.

**Bài 2 — Directory traverse.** Cấp quyền đọc cho file nhưng bỏ quyền traverse từ một thư mục cha trong môi trường test POSIX, quan sát khả năng mở file. Khôi phục permission sau test để không khóa đường truy cập của chính mình.

**Bài 3 — Probe vs operation.** Kiểm tra `Files.isReadable` và sau đó thử đọc; thảo luận vì sao hai thời điểm có thể khác nhau. Không tạo điều kiện race bằng cách thay symlink ngoài thư mục test.

**Bài 4 — Giới hạn Java policy lịch sử.** Đọc tài liệu JDK 17/JEP 411 và JDK 24/JEP 486; ghi rõ `FilePermission` từng có vai trò nào và vì sao không còn dùng nó làm sandbox trong Java mới. Không cài Security Manager vào hệ thống đang hoạt động chỉ để thử.

**Bài 5 — Path traversal.** Viết hàm resolve tên file trong thư mục staging do ứng dụng kiểm soát. Thử các input `file.txt`, `../file.txt`, absolute path và symlink. Nhận diện điểm lexical check giúp lọc nhưng không thay thế chống symlink race.

**Bài 6 — Container.** Chạy service ở môi trường test với root filesystem read-only và một writable volume được cấp đúng quyền. Liệt kê các đường dẫn cần ghi: temp, logs, cache, uploads. Xử lý lỗi bằng cách sửa mount/ACL có chủ đích, không bật privileged container.

**Bài 7 — Kiểm tra file secret mới.** Trên POSIX view phù hợp, tạo file ngay với attributes owner-only; trên Windows dùng ACL policy tương ứng. Xác nhận không có khoảng thời gian file nằm trên shared folder với quyền mặc định quá rộng.

## 25. Câu hỏi tự kiểm tra

**1.** Không có Security Manager, `Files.readString` có luôn đọc được bất kỳ file nào không?  
**Không.** OS/filesystem/mount tiếp tục thực thi quyền của process.

**2.** `new FilePermission(path,"read")` có tương đương `chmod u+r path` không?  
**Không.** Nó chỉ là Java Permission object trong mô hình cũ; không sửa POSIX mode.

**3.** Từ JDK nào Security Manager permanently disabled?  
**JDK 24**, theo JEP 486. JDK 17 đã deprecated for removal theo JEP 411.

**4.** `AccessController.doPrivileged` có giúp một process không đủ quyền OS đọc `/etc/shadow` không?  
**Không.** Nó không thay OS identity/ACL.

**5.** Trên POSIX, vì sao một file có bit read vẫn không mở được?  
**Có thể** thiếu quyền traverse trên thư mục cha, ACL/mount/OS policy khác, hoặc identity của process không tương ứng với quyền đó.

**6.** `Files.isReadable(path) == true` có bảo đảm lệnh mở file sau một giây thành công?  
**Không.** Có race/TOCTOU và điều kiện hệ thống có thể đổi.

**7.** Một process service được OS cho đọc mọi file tenant. Nếu API không kiểm tra tenant, OS có tự từ chối Alice đọc file của Bob không?  
**Không theo logic tenant ứng dụng.** OS thấy process service; ứng dụng phải tự enforce authorization.

**8.** Có thể dùng ClassLoader hoặc JPMS thay Security Manager để sandbox bytecode không tin cậy không?  
**Không như một ranh giới OS security đầy đủ.** Cần isolation ở mức process/OS/container/VM phù hợp.

**9.** File quyền `rw-------` có bảo đảm không ai có thể lấy dữ liệu nếu chạy app bằng root và log toàn bộ nội dung?  
**Không.** OS mode không bảo vệ khỏi ứng dụng/privileged process đã có authority và việc rò rỉ dữ liệu ở tầng ứng dụng.

**10.** Khi dùng temp file rồi atomic rename thay đích, file mới có chắc giữ ACL cũ không?  
**Không.** Đối tượng được công bố là file mới và có thể mang metadata của temp; cần policy bảo toàn quyền.

## 26. Checklist thiết kế Java I/O an toàn

Trước khi triển khai hoặc review hệ thống đọc/ghi:

- [ ] Xác định **process account** đang chạy JVM thực tế.
- [ ] Xác định tài nguyên nào cần đọc, ghi, tạo, xóa, rename; cấp quyền tối thiểu.
- [ ] Kiểm tra quyền **thư mục cha**, không chỉ file.
- [ ] Hiểu filesystem/provider và OS: POSIX mode/ACL, Windows DACL, mount và sandbox.
- [ ] Không dựa vào `FilePermission` hoặc `SecurityManager` lịch sử để cấp/quản lý OS access.
- [ ] Hiểu JDK 17 deprecated và JDK 24 permanently disabled đối với Security Manager.
- [ ] Xác thực và phân quyền **người dùng nghiệp vụ** trước khi map ID sang path.
- [ ] Hạn chế path traversal và symlink; không dựa riêng vào `normalize()+startsWith()` chống attacker.
- [ ] Không dùng `isReadable/isWritable` như bảo đảm thành công hay security check cuối cùng.
- [ ] Thực hiện I/O thật trong try/catch và xử lý `IOException` theo ngữ cảnh.
- [ ] Tạo file nhạy cảm với permission/ACL thích hợp từ đầu khi có hỗ trợ.
- [ ] Tách namespace/process nếu phải thực thi plugin hoặc code không tin cậy.
- [ ] Không chạy JVM với quyền root/Administrator chỉ để tránh kiểm soát ACL.
- [ ] Khi dùng container, kiểm tra mount, user, capabilities và secret exposure.
- [ ] Khi xuất bản file tạm, xét atomicity, metadata ACL và durability **độc lập**.
- [ ] Có test thật trên OS/filesystem triển khai, không suy ra từ máy developer.
- [ ] Không log secret trong lỗi quyền hoặc ngoại lệ I/O.
- [ ] Có security review khi thay đổi quyền file, token, sandbox hoặc service principal.

## 27. Tổng kết

- **Quyền hệ điều hành** do kernel/filesystem/access token/ACL/mount kiểm soát, quyết định tiến trình có thể sử dụng tài nguyên hệ thống hay không.
- **Java Security Manager lịch sử** là lớp kiểm tra permission bên trong JVM, **không bao giờ thay quyền OS**; `FilePermission` chỉ là biểu diễn policy Java chứ không phải thao tác chmod/ACL.
- **JDK 17**: Security Manager deprecated for removal (JEP 411). **JDK 24+**: Security Manager permanently disabled (JEP 486); đừng xây sandbox mới dựa vào nó.
- **Ứng dụng phải tự authorization theo user/tenant**; OS process account không tự biết quyền nghiệp vụ của từng request.
- **`Files.isReadable/isWritable` là probe, không phải authority**; filesystem race, symlink và path traversal cần thiết kế xử lý riêng.
- **Least privilege + OS/process isolation + kiểm tra dữ liệu/đường dẫn + quản lý lifecycle I/O** là nền tảng thực tế cho Java I/O an toàn.

### Tài liệu tham khảo chính thức

- [JEP 411 — Deprecate the Security Manager for Removal](https://openjdk.org/jeps/411)
- [JEP 486 — Permanently Disable the Security Manager](https://openjdk.org/jeps/486)
- [Oracle: The Security Manager Is Permanently Disabled, JDK 25](https://docs.oracle.com/en/java/javase/25/security/security-manager-is-permanently-disabled.html)
- [Oracle: SecurityManager, Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/SecurityManager.html)
- [Java FilePermission (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FilePermission.html)
- [Java Files API (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [PosixFileAttributeView](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/PosixFileAttributeView.html)
- [AclFileAttributeView](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/AclFileAttributeView.html)
- [SecureDirectoryStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html)


</details>

- [Quay lại đầu trang](#back-to-top)
