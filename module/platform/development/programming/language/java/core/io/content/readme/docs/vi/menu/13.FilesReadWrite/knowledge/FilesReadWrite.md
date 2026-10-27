<a id="back-to-top"></a>

# Đọc ghi bằng Files

## Menu
- [readAllBytes và giới hạn bộ nhớ](#files-read-write-001)
- [readString, writeString và charset UTF-8 mặc định của API](#files-read-write-002)
- [readAllLines và dữ liệu được đưa toàn bộ vào bộ nhớ](#files-read-write-003)
- [Files.lines đọc lười và bắt buộc quản lý vòng đời Stream](#files-read-write-004)
- [newBufferedReader và newBufferedWriter](#files-read-write-005)
- [newInputStream, newOutputStream và các lựa chọn mở tệp](#files-read-write-006)
- [READ, WRITE, CREATE, CREATE_NEW và TRUNCATE_EXISTING](#files-read-write-007)
- [APPEND và giới hạn kết hợp OpenOption](#files-read-write-008)
- [SYNC, DSYNC, DELETE_ON_CLOSE và tính hỗ trợ của provider](#files-read-write-009)
- [Files.copy kết hợp InputStream hoặc OutputStream](#files-read-write-010)
- [Files.mismatch và so sánh nội dung hai tệp](#files-read-write-011)

## <a id="files-read-write-001">readAllBytes và giới hạn bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Files.readAllBytes và giới hạn bộ nhớ trong Java

`Files.readAllBytes(Path)` là API tiện lợi của `java.nio.file.Files` để **đọc toàn bộ byte của một tệp vào một mảng `byte[]` duy nhất**. Khi tệp nhỏ và thật sự cần tất cả dữ liệu trong bộ nhớ, đây là cách viết ngắn gọn. Khi tệp lớn, đến từ nguồn không tin cậy hoặc có kích thước không dự đoán được, thao tác này có thể tiêu thụ quá nhiều RAM, gây `OutOfMemoryError`, tăng áp lực garbage collection hoặc làm ứng dụng mất ổn định.

**Quy tắc cốt lõi:** nếu nhu cầu là **xử lý tuần tự**, hãy **đọc theo từng khối (streaming)** thay vì tải toàn bộ. Nếu nghiệp vụ bắt buộc cần một `byte[]`, phải thiết kế **giới hạn kích thước và ngân sách bộ nhớ** rõ ràng.

## 1. `Files.readAllBytes(Path)` đọc dữ liệu ra sao?

API:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

Path path = Path.of("data", "photo.png");
byte[] bytes = Files.readAllBytes(path); // Có thể ném IOException.
```

Đây là đoạn mã minh họa cần đặt trong class/phương thức. Phương thức làm các việc có ý nghĩa sau:

1. Mở tệp qua filesystem provider của `Path`.
2. Đọc dữ liệu từ tệp, cố gắng lấy tất cả byte tới EOF.
3. Trả về một **mảng `byte[]`** chứa nội dung đọc được theo thứ tự.
4. Đóng tài nguyên nội bộ của thao tác khi hoàn tất hoặc khi thất bại.

`Files.readAllBytes` đọc **byte thô**, không tự giải mã UTF-8, không phân biệt ảnh, PDF, GZIP hay văn bản và không tự hiểu “mỗi byte là một ký tự”. Với văn bản, cần biết charset gốc rồi mới giải mã:

```java
import java.nio.charset.StandardCharsets;

byte[] data = Files.readAllBytes(path);
String text = new String(data, StandardCharsets.UTF_8);
```

Đoạn này minh họa khi `path` thực sự trỏ tới tệp văn bản UTF-8. Với ảnh, archive hoặc file nhị phân, **giữ dữ liệu là byte**, không ép thành `String`.

### Điều gì KHÔNG xảy ra?

- `Files.readAllBytes` **không trả về một dòng dữ liệu đang được stream**; nó chỉ trả kết quả sau khi đã gom nội dung vào mảng.
- Nó **không có tham số limit** để yêu cầu “chỉ đọc tối đa 10 MB”.
- Nó **không tự động nén hoặc làm nhỏ** nội dung.
- Nó **không tạo snapshot nguyên tử** của một tệp đang bị tiến trình khác sửa; dữ liệu đọc được có thể chịu ảnh hưởng của thay đổi đồng thời.
- Nó **không phù hợp mặc định cho mọi kích thước tệp** chỉ vì có tên ngắn gọn.

Nếu tệp không tồn tại, không đọc được hoặc phát sinh lỗi I/O, thao tác có thể ném `IOException` (ví dụ `NoSuchFileException`). Khi yêu cầu cấp phát bộ nhớ không thể đáp ứng, có thể xuất hiện `OutOfMemoryError` — đây là một `Error` nghiêm trọng, **không** nên xem là đường xử lý thông thường để “thử đọc rồi bắt lỗi nếu quá lớn”.

## 2. Vì sao toàn bộ file trong một `byte[]` dễ tốn RAM?

`byte[]` là một **đối tượng mảng liên tục về mặt logic của Java**, kích thước được biểu diễn bằng `int`. Vì vậy:

- `byte[]` không thể có số phần tử vượt quá giới hạn độ dài mảng Java (xấp xỉ `Integer.MAX_VALUE` về mặt lý thuyết, giới hạn thực tế còn phụ thuộc JVM).
- Một tệp lớn khoảng **2 GiB hoặc hơn** không thể được biểu diễn trọn vẹn trong một `byte[]` đơn thông thường. API không phải công cụ phù hợp cho file ở quy mô đó.
- **File nhỏ hơn 2 GiB vẫn có thể thất bại** nếu heap không đủ, bị phân mảnh, hoặc JVM không thể cấp phát một mảng đủ lớn.
- Mảng cần bộ nhớ cho **byte nội dung và overhead đối tượng**; nếu ứng dụng tạo thêm `String`, bản sao, mảng trung gian hoặc dữ liệu giải nén, tổng bộ nhớ có thể cao hơn đáng kể so với kích thước file.

Ví dụ một file 500 MiB được đọc bằng `Files.readAllBytes` cần một mảng khoảng **500 MiB cho nội dung**, chưa kể overhead, heap đã dùng, bước cấp phát hoặc các bản sao sau đó. Một JVM có giới hạn heap `-Xmx256m` không thể dựa vào `readAllBytes` để giữ tệp 500 MiB trong một mảng.

Lưu ý `-Xmx` giới hạn heap Java, không phải tổng bộ nhớ tiến trình; ứng dụng còn có native memory, thread stacks và các bộ đệm của thư viện. Một file 500 MiB cũng không hàm ý duy nhất “tốn đúng 500 MiB RAM” ở cấp tiến trình.

### Một số phép ước lượng hữu ích

| Dữ liệu | Kích thước byte gần đúng | Hệ quả khi `readAllBytes` |
| --- | --- | --- |
| File cấu hình 4 KiB | 4.096 byte | Thường rất phù hợp nếu thực sự cần toàn bộ |
| File 10 MiB | 10 × 1.048.576 byte | Cần giữ mảng khoảng 10 MiB và thêm overhead |
| File 500 MiB | 500 × 1.048.576 byte | Có thể chiếm tỷ lệ lớn của heap |
| File 3 GiB | Vượt giới hạn mảng `byte[]` đơn thông thường | Không dùng `readAllBytes` |
| File nén 10 MiB | 10 MiB *ở dạng nén* | Nếu giải nén trong RAM, nội dung có thể lớn hơn rất nhiều |

MiB là `1.048.576` byte, còn MB theo hệ thập phân là `1.000.000` byte. Với bài toán giới hạn bộ nhớ, cần thống nhất đơn vị để tránh sai lệch.

## 3. So sánh đọc toàn bộ với xử lý theo từng khối

```text
Cách 1: Files.readAllBytes(path)

File: [-------------------------------- dữ liệu ---------------------------]
                           |
                           v
                  byte[] chứa toàn bộ file
                           |
                   xử lý sau khi đọc xong

Bộ nhớ nội dung: O(kích thước file)


Cách 2: Files.newInputStream(path)

File: [-------------------------------- dữ liệu ---------------------------]
           |             |             |            |
           v             v             v            v
      buffer 8 KiB -> xử lý -> tái sử dụng buffer -> ...
      
Bộ nhớ đọc: O(kích thước buffer)
         (nếu logic xử lý không tích lũy toàn bộ dữ liệu)
```

Ở cách thứ hai, nếu ứng dụng chỉ tính tổng số byte, checksum, ghi sang tệp khác hoặc xử lý record tuần tự, **không cần giữ tất cả byte trong RAM**. Bộ đệm cố định 8 KiB có thể tái sử dụng qua hàng nghìn lần `read`.

Tuy nhiên, nếu sau khi đọc theo từng khối mà lại `append` mọi thứ vào `ByteArrayOutputStream` hoặc lưu toàn bộ vào một `List<byte[]>`, **tổng bộ nhớ vẫn tăng theo kích thước file**. “Dùng InputStream” không tự động đồng nghĩa bộ nhớ luôn nhỏ: quan trọng là ứng dụng làm gì với từng khối.

## 4. Chương trình Java 17+ hoàn chỉnh: so sánh hai cách

Ví dụ tạo tệp tạm nhỏ bằng UTF-8, sau đó đọc toàn bộ bằng `Files.readAllBytes` và đọc theo từng khối `4 byte` bằng `InputStream`. Chúng ta dùng `CRC32` để minh họa việc tính checksum bằng phương pháp streaming mà không cần tích lũy tất cả byte trong mảng. Sau cùng, chương trình giải mã byte UTF-8 đúng charset.

Lưu file **`ReadAllBytesDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

public class ReadAllBytesDemo {

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("java-readall-", ".txt");

        try {
            String original = "Xin chào\n";
            Files.writeString(file, original, StandardCharsets.UTF_8);

            byte[] all = Files.readAllBytes(file);

            CRC32 wholeFileChecksum = new CRC32();
            wholeFileChecksum.update(all);

            CRC32 streamedChecksum = new CRC32();
            long streamedBytes = 0;

            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[4];
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    streamedChecksum.update(buffer, 0, bytesRead);
                    streamedBytes += bytesRead;
                }
            }

            System.out.println("File bytes: " + Files.size(file));
            System.out.println("readAllBytes length: " + all.length);
            System.out.println("Streamed bytes: " + streamedBytes);
            System.out.println("CRC32 matches: " +
                    (wholeFileChecksum.getValue()
                            == streamedChecksum.getValue()));
            System.out.println("UTF-8 text matches: " +
                    original.equals(
                            new String(all, StandardCharsets.UTF_8)));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 ReadAllBytesDemo.java
java ReadAllBytesDemo
```

**Stdout chính xác trong điều kiện I/O bình thường:**

```text
File bytes: 10
readAllBytes length: 10
Streamed bytes: 10
CRC32 matches: true
UTF-8 text matches: true
```

Giải thích:

1. `"Xin chào\n"` gồm **10 byte UTF-8**: `Xin` (3), dấu cách (1), `ch` (2), `à` (2), `o` (1), LF (1).
2. `Files.readAllBytes` trả mảng đủ 10 byte; khi đưa vào `CRC32.update(all)`, toàn bộ mảng được xử lý một lần.
3. Vòng lặp streaming dùng **cùng bộ đệm 4 byte** ở mọi lần đọc; giá trị `bytesRead` mới là số byte hợp lệ trong bộ đệm.
4. `streamedChecksum.update(buffer, 0, bytesRead)` xử lý đúng phần vừa đọc, không tính lại các byte cũ ở cuối buffer.
5. `CRC32` của hai luồng bằng nhau chứng minh chúng có cùng checksum trong ví dụ; checksum **không phải** bằng chứng duy nhất tuyệt đối về tính đồng nhất trong mọi ngữ cảnh bảo mật.
6. `new String(all, StandardCharsets.UTF_8)` chỉ được dùng vì chúng ta biết tệp chứa **văn bản UTF-8**. File ảnh hay tệp GZIP không nên giải mã tùy tiện như vậy.

Chương trình xóa tệp tạm trong `finally`, và `try-with-resources` đóng `InputStream`. Nó không tạo dữ liệu lớn hoặc cố gây `OutOfMemoryError` để thử máy.

## 5. Streaming đúng cách với `Files.newInputStream`

Phần cốt lõi của ví dụ có thể tách thành một phương thức xử lý dữ liệu theo từng khối:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

static long countBytes(Path path) throws IOException {
    long total = 0;

    try (InputStream input = Files.newInputStream(path)) {
        byte[] buffer = new byte[8192];
        int n;

        while ((n = input.read(buffer)) != -1) {
            total += n;
            // Tại đây có thể xử lý CHỈ các byte buffer[0..n-1].
        }
    }

    return total;
}
```

Đây là **phương thức minh họa** cần đặt trong một class. Nó sử dụng bộ đệm cố định 8 KiB; `total` là `long` để tránh giới hạn `int` của độ dài một mảng. `InputStream.read(buffer)` có thể trả **ít hơn `buffer.length`** dù chưa EOF — không được coi “đọc ngắn” là hết file.

Nếu muốn xử lý bằng một consumer hoặc ghi ra đích, thực hiện ngay trong vòng lặp:

```java
try (InputStream input = Files.newInputStream(source);
     OutputStream output = Files.newOutputStream(target)) {

    byte[] buffer = new byte[8192];
    int n;
    while ((n = input.read(buffer)) != -1) {
        output.write(buffer, 0, n);
    }
}
```

Đây là đoạn minh họa cần biến `source`/`target` dạng `Path` và các import `InputStream`, `OutputStream`, `Files`. **Chỉ ghi `n` byte đã đọc**, không ghi toàn bộ `buffer`. Nếu source và target thực tế là cùng file hoặc cùng đối tượng tệp qua alias/link, cách mở output có thể cắt ngắn dữ liệu nguồn — cần đảm bảo hai đầu khác nhau khi sao chép.

Java còn có `Files.copy(source, target, options...)` để sao chép trực tiếp qua API tệp khi phù hợp, và `InputStream.transferTo(output)` từ Java 9 để chuyển dữ liệu sang `OutputStream`. Những cách này giúp tránh việc tự tạo mảng chứa **toàn bộ file**, miễn là đích cũng xử lý theo luồng chứ không gom toàn bộ vào bộ nhớ.

## 6. Có nên dùng `Files.size(path)` để bảo vệ trước khi đọc?

Có thể dùng `Files.size(path)` để **sàng lọc sơ bộ**:

```java
long maxBytes = 10L * 1024 * 1024; // 10 MiB
long size = Files.size(path);

if (size > maxBytes) {
    throw new IOException("File vượt giới hạn");
}

byte[] data = Files.readAllBytes(path);
```

Đoạn này là mã minh họa cần đặt trong phương thức có `throws IOException` và khai báo `path`/`Files` thích hợp. Nó giúp từ chối một tệp đã được biết là quá lớn **tại thời điểm kiểm tra**. Nhưng nó **chưa tạo giới hạn cứng** cho lượng dữ liệu được cấp phát:

- Tệp có thể **tăng kích thước giữa `Files.size` và `Files.readAllBytes`**.
- Một số loại file/provider hoặc metadata đặc thù không cung cấp kích thước hữu ích để dự đoán chính xác lượng byte đọc được.
- Dù `size <= maxBytes`, heap vẫn có thể không đủ cho mảng hoặc các đối tượng bổ sung.
- Kiểm tra `exists()`, `isRegularFile()`, `size()` riêng biệt rồi mới đọc cũng chịu rủi ro trạng thái thay đổi (*time-of-check to time-of-use*, TOCTOU).

Do đó, nếu dữ liệu **không tin cậy** hoặc bị giới hạn theo quota, giới hạn nên được **thực thi trong quá trình đọc**, không chỉ dựa trên metadata trước khi mở.

## 7. Khi bắt buộc cần `byte[]`: đọc có giới hạn trong lúc stream

Nếu API phía sau thực sự yêu cầu toàn bộ `byte[]`, có thể dùng `InputStream` và `ByteArrayOutputStream` cùng một ngưỡng byte tối đa. Mỗi lần đọc kiểm tra giới hạn **trước khi thêm vào mảng tích lũy**:

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

static byte[] readAtMost(Path path, int maxBytes)
        throws IOException {

    if (maxBytes < 0) {
        throw new IllegalArgumentException(
                "maxBytes must be non-negative");
    }

    try (InputStream input = Files.newInputStream(path);
         ByteArrayOutputStream output =
                 new ByteArrayOutputStream()) {

        byte[] buffer = new byte[8192];
        int count;
        int total = 0;

        while ((count = input.read(buffer)) != -1) {
            if (count > maxBytes - total) {
                throw new IOException("File exceeds byte limit");
            }

            output.write(buffer, 0, count);
            total += count;
        }

        return output.toByteArray();
    }
}
```

Đây là **phương thức minh họa** cần đặt trong một class. Giá trị `total` không vượt `maxBytes` vì điều kiện kiểm tra chạy **trước `output.write`**. Khi file vượt giới hạn, phương thức ném `IOException` và ngừng tích lũy dữ liệu. Nếu tệp bị sửa đồng thời, đoạn kiểm tra này vẫn áp dụng giới hạn trên **số byte thực sự được đọc và giữ lại**.

**Giới hạn quan trọng:** đây vẫn là cách **gom byte vào RAM**. `ByteArrayOutputStream` có vùng đệm nội bộ, có thể mở rộng dung lượng cấp phát vượt số byte đang chứa; `toByteArray()` còn tạo một mảng kết quả mới. Vì vậy **đỉnh bộ nhớ thực tế có thể cao hơn đáng kể `maxBytes`**, và `OutOfMemoryError` vẫn có thể xảy ra nếu giới hạn đặt quá lớn hoặc heap còn ít.

Một lựa chọn giới hạn vài MiB thường hợp lý cho upload cấu hình/small payload; nhưng phải chọn ngưỡng theo tình huống, heap, số request xử lý đồng thời và các cấu trúc phát sinh tiếp theo. Không lấy `Integer.MAX_VALUE` làm giới hạn mặc định chỉ vì `byte[]` dùng `int`.

Nếu cần **giới hạn thực sự sát ngân sách bộ nhớ**, không trả `byte[]` mà nên **xử lý streaming** với buffer cố định, hoặc dùng cơ chế lưu tạm vào file theo quota. Giới hạn byte chỉ là một phần của bảo vệ tài nguyên; còn cần hạn chế thời gian, số request và các thao tác giải nén/phân tích dữ liệu.

## 8. Tình huống thực tế: chọn `readAllBytes` hay API khác?

| Nhu cầu | Cách thường phù hợp | Vì sao |
| --- | --- | --- |
| Đọc một icon nhỏ vài KiB để kiểm tra nội dung | `Files.readAllBytes` | Gọn, đủ nhỏ để giữ trong RAM |
| Tải toàn bộ cấu hình binary nhỏ, có hạn mức rõ | `readAtMost` hoặc `Files.readAllBytes` sau đánh giá giới hạn | Có thể cần `byte[]` thực sự |
| Tính checksum cho file vài GB | `Files.newInputStream` + cập nhật digest từng khối | Không cần gom file vào một mảng |
| Sao chép file lớn | `Files.copy` hoặc input/output stream | Xử lý byte theo luồng |
| Đọc file log lớn để lọc dòng | `Files.newBufferedReader` hoặc `Files.lines` | Duyệt theo dòng |
| Tạo `String` từ toàn bộ file văn bản nhỏ | `Files.readString(path, charset)` | Rõ về charset, nhưng vẫn tải toàn bộ văn bản |
| Đọc hết các dòng vào `List<String>` | `Files.readAllLines` | Tiện cho file nhỏ; **không** dành cho file lớn |
| Phục vụ HTTP download kích thước lớn | Streaming từ file sang response stream | Tránh lưu file trong RAM trên mỗi request |
| Xử lý dữ liệu GZIP/ZIP | Giải nén streaming và áp hạn mức **sau giải nén** | Chỉ kiểm kích thước file nén không bảo vệ khỏi expansion |
| Phân tích file ảnh/video rất lớn | Decoder hoặc API chuyên dụng hỗ trợ streaming/tiling | `byte[]` toàn bộ không tối ưu; decoder còn tiêu thụ bộ nhớ riêng |

### Đọc văn bản lớn theo dòng

`Files.lines(path, StandardCharsets.UTF_8)` trả về một `Stream<String>` xử lý dòng khi cần, phù hợp với những tác vụ không cần chứa toàn bộ văn bản:

```java
try (Stream<String> lines =
        Files.lines(path, StandardCharsets.UTF_8)) {

    long warnings = lines
            .filter(line -> line.contains("WARN"))
            .count();

    System.out.println(warnings);
}
```

Đây là đoạn minh họa cần `import java.util.stream.Stream`, `Files`, `StandardCharsets` và biến `path`. **Bắt buộc đóng** stream bằng `try-with-resources` để giải phóng file đang mở. Lưu ý không thu tất cả dòng vào `toList()` nếu mục tiêu là tiết kiệm bộ nhớ; làm như vậy vẫn có chi phí tỷ lệ với nội dung.

Với văn bản UTF-8, `Files.readString` / `Files.readAllLines` cũng có chi phí bộ nhớ tỷ lệ với kích thước nội dung; chúng không phải giải pháp tự động cho file lớn chỉ vì không trả `byte[]`.

## 9. File bị thay đổi trong lúc đọc

Giả sử một tiến trình đang ghi bổ sung log, còn tiến trình khác gọi `Files.readAllBytes`. Kích thước và nội dung tệp có thể thay đổi trong lúc thao tác đang diễn ra.

Không nên suy luận rằng:

- Kích thước nhận được từ `Files.size` chắc chắn bằng `bytes.length` sau `readAllBytes`.
- Nội dung trong mảng luôn là snapshot nhất quán của một thời điểm nếu có writer khác ghi đồng thời.
- Việc đọc thành công có nghĩa dữ liệu đã được xác thực hoặc đáng tin cậy.

Nếu ứng dụng cần snapshot nhất quán, phải thiết kế cơ chế đồng bộ, copy/rename theo giao thức, khóa phù hợp hoặc định dạng có version/checksum; lựa chọn phụ thuộc filesystem và cách ứng dụng ghi file. `readAllBytes` không tự giải quyết cạnh tranh truy cập.

Với dữ liệu do người dùng cung cấp, ngoài giới hạn bộ nhớ còn cần kiểm tra định dạng và kích thước đầu vào theo nghiệp vụ. Một ảnh nén nhỏ có thể mở rộng thành bitmap rất lớn; một file ZIP nhỏ có thể chứa dữ liệu giải nén lớn bất thường. Đây là vấn đề **bộ nhớ sau xử lý**, không được giải quyết chỉ bằng cách giới hạn số byte file gốc.

## 10. Ngoại lệ và quản lý tài nguyên

Chữ ký quan trọng:

```java
byte[] Files.readAllBytes(Path path) throws IOException
```

Đây là **cách mô tả chữ ký**, không phải một lời gọi có thể biên dịch độc lập. Với `Files.readAllBytes`, không cần tự mở/đóng `InputStream` bên ngoài, vì phương thức thực hiện đọc và quản lý tài nguyên nội bộ.

Tuy nhiên, khi chuyển sang `Files.newInputStream` thì phải tự đóng stream:

```java
try (InputStream input = Files.newInputStream(path)) {
    // Dùng input.read(buffer) trong vòng lặp.
}
```

Cần phân biệt:

- **`IOException`:** tệp không tồn tại, quyền không đủ, lỗi đọc hoặc lỗi provider; xử lý theo nghiệp vụ, có thể báo người dùng hoặc retry hợp lý.
- **`OutOfMemoryError`:** JVM không thể cấp phát bộ nhớ cần thiết; đây không phải lỗi “file không tồn tại” và không nên dựa vào cơ chế catch để kiểm soát input quá lớn.
- **`SecurityException`:** có thể xảy ra trong môi trường có chính sách bảo mật/kiểm tra quyền tương ứng.
- **`InvalidPathException`:** có thể phát sinh lúc tạo `Path` từ chuỗi đường dẫn không hợp lệ, không phải vì `readAllBytes` trả byte sai.

Không nuốt mọi ngoại lệ rồi trả mảng rỗng: như vậy chương trình khó phân biệt file rỗng thực sự với thao tác đọc thất bại. Nếu giao diện yêu cầu giới hạn upload, hãy dùng ngoại lệ/cờ lỗi có thông điệp riêng cho “quá giới hạn”.

## 11. Những sai lầm thường gặp

| Sai lầm | Vì sao nguy hiểm/sai? | Hướng xử lý |
| --- | --- | --- |
| `Files.readAllBytes` với mọi file không cần xem kích thước | File lớn có thể vượt giới hạn mảng hoặc heap | Streaming hoặc áp quota có kiểm soát |
| `Files.size(path)` nhỏ nên chắc chắn đọc an toàn | File có thể tăng, heap có thể thiếu | Thực thi giới hạn trong lúc đọc và ngân sách heap |
| `new String(Files.readAllBytes(path))` cho dữ liệu bất kỳ | Chuyển nhị phân thành text không có nghĩa, charset mặc định không rõ | Giữ `byte[]` hoặc dùng charset tường minh khi biết là văn bản |
| Dùng `Files.readAllLines` để “tối ưu RAM” | Nó gom toàn bộ dòng vào `List` | `Files.lines` hoặc `BufferedReader` và xử lý tuần tự |
| Dùng `newInputStream` rồi dồn hết vào `ByteArrayOutputStream` không giới hạn | Vẫn tăng RAM theo dung lượng file | Cấp giới hạn hoặc xử lý streaming |
| Trong loop, xử lý toàn bộ `buffer` sau `read` | Cuối buffer có thể chứa byte từ lượt trước | Chỉ xử lý `buffer[0..n-1]` |
| Một lần `read(buffer)` không đủ buffer nghĩa là EOF | Luồng có thể trả ít byte hơn yêu cầu | Lặp cho tới khi nhận `-1` |
| Cho rằng `byte[]` dài bằng số ký tự | UTF-8 có ký tự nhiều byte | Phân biệt kích thước byte và ký tự |
| Cố bắt `OutOfMemoryError` để “báo file lớn” | Heap có thể đã cạn, ứng dụng không ổn định | Ngăn trước bằng thiết kế stream/limit |
| Quên đóng `InputStream` hoặc `Files.lines` | Có thể rò rỉ tài nguyên/file descriptor | `try-with-resources` |
| Chỉ kiểm kích thước file ZIP/GZIP trước xử lý | Dữ liệu sau giải nén có thể phình lớn | Giới hạn số byte sau giải nén |
| Kích thước file vừa giới hạn, tạo thêm nhiều bản sao | Đỉnh bộ nhớ và concurrency vẫn có thể gây OOM | Đo memory, tránh copy thừa, đặt quota hợp lý |

## 12. Câu hỏi tự kiểm tra

1. **`Files.readAllBytes(path)` trả gì?** Một **`byte[]` chứa toàn bộ byte đã đọc**, không phải `String` và không phải `InputStream`.
2. **Vì sao không nên gọi `readAllBytes` cho file vài GiB?** Vì giới hạn mảng `byte[]`, heap và chi phí cấp phát; streaming phù hợp hơn.
3. **`Files.size(path) <= limit` đã là giới hạn cứng chưa?** Chưa; file có thể thay đổi trước/trong khi đọc và heap có thể thiếu.
4. **Muốn tính CRC32 của file 10 GB có phải đọc toàn bộ không?** Không; dùng `InputStream`, cập nhật checksum từng buffer.
5. **`buffer.length == 8192`, `read(buffer)` trả 700: cần xử lý bao nhiêu byte?** Chỉ **700 byte**.
6. **Thay `readAllBytes` bằng `Files.readAllLines` có tiết kiệm RAM cho file log lớn không?** Không hẳn; nó vẫn gom mọi dòng vào RAM. Nên duyệt `BufferedReader` hoặc `Files.lines`.
7. **Dùng `ByteArrayOutputStream` để nhận mọi byte từ InputStream không giới hạn có phải streaming ít RAM không?** Không; nó vẫn tích lũy nội dung.
8. **Đã giới hạn file GZIP nén ở 10 MiB thì dữ liệu giải nén chắc chắn nhỏ hơn 10 MiB?** Không; phải có hạn mức cho **output đã giải nén**.
9. **`readAllBytes` đọc ảnh có phải giải mã ảnh không?** Không; nó chỉ trả byte thô của file.
10. **Khi nào `readAllBytes` là lựa chọn hợp lý?** Khi tệp **nhỏ, có giới hạn đáng tin**, và nghiệp vụ thực sự cần giữ toàn bộ byte để xử lý.

**Kết luận:** `Files.readAllBytes` là công cụ **đọc trọn một file nhỏ vào một mảng**, không phải API streaming. Khi file lớn, không dự đoán được hoặc đến từ bên ngoài, ưu tiên `Files.newInputStream`, xử lý theo buffer cố định và giới hạn tài nguyên. Khi thật sự cần `byte[]`, áp giới hạn **trong quá trình đọc** và nhớ rằng các bản sao/mảng trung gian vẫn có chi phí bộ nhớ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="files-read-write-002">readString, writeString và charset UTF-8 mặc định của API</a>

<details>
<summary>Xem chi tiết</summary>

# Files.readString(), Files.writeString() và UTF-8 mặc định của API

## 1. Mục tiêu bài học

Trong Java 11 trở lên (bao gồm Java 17+), **java.nio.file.Files** cung cấp hai phương thức thuận tiện để **đọc hoặc ghi toàn bộ nội dung văn bản**:

- **Files.readString(Path)**: đọc toàn bộ file và trả về một **String**, **giải mã UTF-8 theo mặc định của chính API**.
- **Files.writeString(Path, CharSequence, OpenOption...)**: ghi nội dung ký tự ra file bằng **UTF-8 theo mặc định của chính API**.
- Cả hai có overload nhận **Charset** để xử lý file có mã hóa khác UTF-8.

Trọng tâm không chỉ là cú pháp ngắn. Điều cần hiểu là **byte trên đĩa khác với ký tự trong String**, lựa chọn charset ảnh hưởng tính đúng đắn, và phương thức một lần đọc/ghi cả file có giới hạn về bộ nhớ, lỗi I/O và chính sách ghi đè.

> Ghi nhớ: **UTF-8 mặc định của Files.readString/writeString là quy định của hai API này**, không phải suy đoán từ charset mặc định của hệ điều hành, terminal hay JVM.

## 2. Từ byte của file đến String: charset có vai trò gì?

File lưu **byte**. String của Java chứa nội dung ký tự Unicode (biểu diễn bằng các đơn vị mã UTF-16 bên trong ngôn ngữ). Một **charset** định nghĩa cách chuyển giữa hai miền đó:

~~~text
Files.readString(path)
    file bytes ──decode UTF-8──> Java String

Files.writeString(path, text)
    Java CharSequence ──encode UTF-8──> file bytes
~~~

Ví dụ:

| Ký tự trong text | Mã Unicode | Byte UTF-8 (hex) |
| --- | --- | --- |
| **A** | U+0041 | **41** |
| **é** | U+00E9 | **C3 A9** |
| **€** | U+20AC | **E2 82 AC** |
| Xuống dòng LF | U+000A | **0A** |

Một ký tự hiển thị không nhất thiết chiếm một byte. Vì thế **Files.size(path)** trả số byte của file có thể khác **text.length()** (đếm số đơn vị mã UTF-16), và khác cả số ký tự Unicode đầy đủ khi có surrogate pair.

**UTF-8** tương thích ASCII với dải U+0000–U+007F, nhưng ký tự ngoài dải ASCII có thể dùng 2–4 byte. Ví dụ chuỗi **"Café\n€"** gồm **6 đơn vị char Java**, trong khi khi ghi bằng UTF-8 sẽ tạo **9 byte**:

~~~text
43 61 66 C3 A9 0A E2 82 AC
C  a  f  é     LF € 
~~~

Bởi vậy, **không dùng String hoặc readString() để sao chép ảnh, ZIP, PDF hay dữ liệu nhị phân**. Hãy dùng **Files.readAllBytes**, **Files.copy** hoặc các InputStream/OutputStream phù hợp.

## 3. Files.readString(Path): đọc cả file thành văn bản UTF-8

Chữ ký tiêu biểu:

~~~java
public static String readString(Path path) throws IOException
public static String readString(Path path, Charset cs) throws IOException
~~~

Ví dụ:

~~~java
Path path = Path.of("notes.txt");
String text = Files.readString(path);
~~~

Đây là **mảnh mã minh họa**; cần import **java.nio.file.Files**, **java.nio.file.Path**, và xử lý **IOException**. **Path.of(...)** chỉ tạo biểu diễn đường dẫn, không tự tạo file.

**Files.readString(path)** đọc tất cả byte của file, giải mã bằng **StandardCharsets.UTF_8** theo đặc tả của API rồi tạo **String**. Nó không dùng **Charset.defaultCharset()** để tự chọn bộ mã khi không truyền tham số. Tham số **Path** chỉ định file cần đọc; stream tài nguyên bên trong được API xử lý, người gọi không phải tự gọi close cho phương thức này.

**Không có thao tác “tự dò encoding”**: nếu file thực tế là Windows-1252, ISO-8859-1 hoặc UTF-16, việc gọi readString không tham số charset có thể thất bại hoặc cho kết quả không mong đợi. Phải biết hoặc xác định encoding của nguồn theo hợp đồng dữ liệu, sau đó truyền charset tương ứng.

### 3.1. Khi file không phải UTF-8 hợp lệ

Nếu dữ liệu byte không hợp lệ với charset, **Files.readString** có thể ném một lỗi **IOException**, điển hình là **MalformedInputException** hoặc lỗi chuyển mã liên quan. Theo API, dữ liệu sai mã hóa không được coi là văn bản hợp lệ và không đơn giản được thay thế âm thầm bằng ký tự **�**.

Ví dụ file chỉ có byte **E9** (hợp lệ cho ký tự **é** trong ISO-8859-1) nhưng không là một chuỗi UTF-8 hợp lệ đầy đủ:

~~~java
Path legacy = Path.of("legacy.txt");
String text = Files.readString(legacy, StandardCharsets.ISO_8859_1);
~~~

Đây là **mảnh mã minh họa** giả định file trên thực tế được mã hóa ISO-8859-1 và cần import **java.nio.charset.StandardCharsets**. Nếu **không biết** charset thực sự của file, không nên đoán bừa: nhiều bảng mã có thể diễn giải cùng byte thành ký tự khác nhau.

### 3.2. Không đọc dữ liệu lớn mà không cân nhắc bộ nhớ

**Files.readString** đọc **toàn bộ** văn bản vào một String. Khi file lớn, bộ nhớ cần cho byte trung gian, giải mã và String có thể đáng kể; lời gọi có thể ném lỗi do thiếu RAM hoặc giới hạn kích thước được API/JVM áp đặt. API còn lưu ý nó **không dành cho file cực lớn**.

Với log hàng chục GB hoặc nguồn dữ liệu lớn, nên dùng **Files.newBufferedReader(path, charset)**, **Files.lines(path, charset)** hoặc một parser có luồng đọc, xử lý từng dòng/khối và đóng tài nguyên đúng cách. Đừng dùng readString rồi split hàng triệu dòng nếu chỉ cần xử lý tuần tự.

## 4. Files.writeString(Path, CharSequence): ghi UTF-8 mặc định

Các overload quan trọng:

~~~java
public static Path writeString(Path path, CharSequence csq,
                               OpenOption... options) throws IOException

public static Path writeString(Path path, CharSequence csq,
                               Charset cs, OpenOption... options)
                               throws IOException
~~~

Ví dụ:

~~~java
Path path = Path.of("notes.txt");
Files.writeString(path, "Xin chào!\nDòng thứ hai");
~~~

Đây là **mảnh minh họa**; cần import **Files/Path** và xử lý **IOException**. **writeString** nhận **CharSequence**, vì vậy String hoặc loại triển khai CharSequence phù hợp đều dùng được. Nó mã hóa nội dung bằng **UTF-8 khi không truyền Charset** rồi ghi các byte ra file. Phương thức trả về **chính Path được truyền vào**, hữu ích nếu muốn kết hợp thao tác, nhưng không trả số byte được ghi.

### 4.1. Hành vi mặc định: tạo mới hoặc ghi đè nội dung

Khi **không chỉ định OpenOption**, hành vi của **Files.writeString(path, text)** tương đương việc mở file để ghi với các lựa chọn **CREATE**, **TRUNCATE_EXISTING** và **WRITE**:

- Nếu file chưa tồn tại nhưng đường dẫn cha tồn tại và có quyền, phương thức **tạo file mới**.
- Nếu file đã tồn tại, nội dung cũ **bị cắt bỏ (truncate)** rồi nội dung mới được ghi.
- Nếu thư mục cha chưa tồn tại, **writeString không tự tạo thư mục cha**.
- Phương thức không tự chèn dấu xuống dòng trước/sau văn bản; chỉ ghi ký tự có trong **CharSequence**.
- Nếu có lỗi ghi giữa chừng, file đích có thể bị thay đổi một phần; phương thức **không bảo đảm việc thay thế nội dung là giao dịch nguyên tử**.

Do vậy, đừng dùng **writeString** với file quan trọng theo thói quen mà quên chính sách ghi đè. Với file cấu hình cần cập nhật an toàn hơn, cân nhắc ghi vào file tạm, xác nhận dữ liệu, rồi di chuyển vào vị trí cuối cùng theo khả năng hệ thống file.

### 4.2. Chọn rõ chính sách với StandardOpenOption

| Mục đích | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Tạo mới nếu chưa có, ghi đè nếu có | **Files.writeString(path, text)** | CREATE + TRUNCATE_EXISTING + WRITE mặc định |
| Ghi tiếp cuối file | **Files.writeString(path, text, CREATE, APPEND)** | Không xóa nội dung trước; tạo file nếu chưa có |
| Chỉ tạo mới, từ chối nếu file tồn tại | **Files.writeString(path, text, CREATE_NEW, WRITE)** | Thích hợp khi không được ghi đè |
| Dùng charset không phải UTF-8 | **Files.writeString(path, text, charset)** | Mã hóa theo charset đã chỉ định |

Trong mã thực tế, các hằng được viết dưới dạng **StandardOpenOption.CREATE**, **StandardOpenOption.APPEND**, **StandardOpenOption.CREATE_NEW**, **StandardOpenOption.WRITE** hoặc dùng static import có chủ ý.

Ví dụ ghi thêm:

~~~java
Files.writeString(
        path, "\nDòng bổ sung",
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
~~~

Đây là **mảnh code**, giả định **path** kiểu Path đã có và đã import **java.nio.file.StandardOpenOption**. Trong ví dụ này, dấu xuống dòng **\n** do **lập trình viên chủ động đưa vào**. Nếu bỏ ký tự \n, văn bản mới sẽ nối ngay vào cuối nội dung cũ mà không tự sinh thêm dòng.

**Không kết hợp APPEND với TRUNCATE_EXISTING** để mong “vừa append vừa xóa trước”; hai hành vi mâu thuẫn và có thể bị hệ thống từ chối. Trong ứng dụng ghi từ nhiều tiến trình, một lần append không nhất thiết tương đương một bản ghi nguyên tử cấp ứng dụng: khả năng atomic, thứ tự các lời gọi và đồng bộ cần được thiết kế riêng nếu cần.

## 5. UTF-8 mặc định của API khác charset mặc định của JVM

Một nhầm lẫn phổ biến:

~~~java
Charset systemDefault = Charset.defaultCharset();
String content = Files.readString(path);
~~~

Mảnh code này cần import **java.nio.charset.Charset** và các loại Files/Path. Hai biểu thức **không có quan hệ “readString tự dùng systemDefault”**. **Files.readString(path)** luôn dùng UTF-8 theo hợp đồng phương thức kể từ khi API được giới thiệu.

Điều này có ý nghĩa đặc biệt khi ứng dụng chạy giữa các môi trường hoặc các phiên bản JDK:

- Những API khác, chẳng hạn cách tạo một **FileReader** hoặc **InputStreamReader** mà không chỉ định charset, có thể dựa vào charset mặc định tùy phiên bản JDK và cấu hình.
- Kể từ Java 18 (JEP 400), charset mặc định của Java SE được chuẩn hóa là UTF-8 trong điều kiện tiêu chuẩn, với một số ngoại lệ/cấu hình liên quan. Nhưng **Files.readString/writeString đã xác định UTF-8 theo API từ Java 11**, không phải chỉ từ Java 18.
- **System.out** hoặc terminal hiển thị ký tự với encoding nào là vấn đề khác: file byte có thể đúng UTF-8 dù console hiển thị không đúng.

Để code diễn đạt yêu cầu rõ ràng, có thể dùng overload charset tường minh:

~~~java
String text = Files.readString(path, StandardCharsets.UTF_8);
Files.writeString(path, text, StandardCharsets.UTF_8);
~~~

Kết quả mã hóa tương đương dạng không khai báo charset. Tuy nhiên, truyền charset rõ ràng có lợi khi nhóm cần đọc hợp đồng ngay tại chỗ, hoặc cần đổi sang ISO-8859-1, UTF-16, v.v. Không nên kỳ vọng hai overload sẽ tự dò encoding.

## 6. Ví dụ Java 17+ hoàn chỉnh: đọc UTF-8, kiểm tra byte, append, overwrite và file sai mã hóa

Chương trình dưới đây tạo file trong **thư mục tạm mới**, không động đến file người học. Nó dùng **Unicode escape** trong source code để kết quả không phụ thuộc vào encoding của tệp mã nguồn và không cần in ký tự tiếng Việt ra console.

Lưu vào file **TextFilesDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HexFormat;

public class TextFilesDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("text-files-demo-");
        Path note = directory.resolve("note.txt");
        Path legacy = directory.resolve("legacy.txt");

        try {
            // Café + LF + dấu euro, biểu diễn bằng Unicode escapes.
            String content = "Caf\u00E9\n\u20AC";

            // Không truyền charset: Java Files sử dụng UTF-8.
            Files.writeString(note, content);
            String readBack = Files.readString(note);
            byte[] raw = Files.readAllBytes(note);

            System.out.println("chars=" + readBack.length());
            System.out.println("bytes=" + raw.length);
            System.out.println("hex=" + HexFormat.of().formatHex(raw));
            System.out.println("equal=" + content.equals(
                    Files.readString(note, StandardCharsets.UTF_8)));

            // APPEND: ghi tiếp, không tự thêm ký tự xuống dòng.
            Files.writeString(note, "!",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            System.out.println("append=" + HexFormat.of().formatHex(
                    Files.readAllBytes(note)));

            // Không chỉ định option: cắt bỏ nội dung cũ rồi ghi "OK".
            Files.writeString(note, "OK");
            System.out.println("overwrite=" + Files.readString(note));

            // Byte E9 đứng đơn độc: hợp lệ với Latin-1, sai UTF-8.
            Files.write(legacy, new byte[] {(byte) 0xE9});
            try {
                Files.readString(legacy);
                System.out.println("unexpected-success");
            } catch (MalformedInputException ex) {
                System.out.println("invalid-utf8=true");
            }
            System.out.println("latin1=" + Files.readString(
                    legacy, StandardCharsets.ISO_8859_1)
                    .equals("\u00E9"));
        } finally {
            // Chỉ dọn đúng những đường dẫn tạm do chương trình tạo.
            Files.deleteIfExists(legacy);
            Files.deleteIfExists(note);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac TextFilesDemo.java
java TextFilesDemo
~~~

Kết quả trong môi trường filesystem bình thường:

~~~text
chars=6
bytes=9
hex=436166c3a90ae282ac
equal=true
append=436166c3a90ae282ac21
overwrite=OK
invalid-utf8=true
latin1=true
~~~

**Giải thích kết quả:**

1. **Café\n€** có 6 đơn vị char Java, nhưng khi ghi UTF-8 có 9 byte. Byte hex là **43 61 66 C3 A9 0A E2 82 AC**.
2. **readString(note)** và **readString(note, UTF_8)** trả cùng nội dung có thể đối chiếu bằng equals.
3. Khi append **!**, có thêm byte **21**, nên chuỗi hex tăng đúng một byte ở cuối.
4. **writeString(note, "OK")** không truyền option nên **cắt bỏ nội dung cũ**. Đọc lại được **OK**.
5. Byte **E9** không hợp lệ nếu đọc như một văn bản UTF-8 độc lập; vì vậy ví dụ bắt **MalformedInputException**. Chỉ định **ISO_8859_1** mới diễn giải byte này thành **é**.

Các giá trị số, hex và boolean đã được chọn để độc lập với ký tự xuất ra console. Ví dụ **không khẳng định** một file bất kỳ luôn có cùng số byte hoặc cùng encoding. Mọi thao tác file thực tế có thể ném IOException khi thiếu quyền, ổ đĩa lỗi hoặc bị tiến trình khác can thiệp; chương trình chủ động dọn đường dẫn tạm trong **finally**.

## 7. readString/writeString có giữ nguyên dấu xuống dòng không?

Hai API này **không tự chuẩn hóa** CRLF thành LF hoặc ngược lại chỉ vì chúng được gọi. Nếu String chứa **\r\n**, khi ghi với UTF-8 byte kết quả có **0D 0A**; nếu chứa **\n**, byte kết quả là **0A**. Khi readString giải mã nội dung hợp lệ, các ký tự kết thúc dòng được trả về như trong dữ liệu đã giải mã.

~~~java
String text = "A\r\nB\nC\rD";
Files.writeString(path, text, StandardCharsets.UTF_8);
String roundTrip = Files.readString(path, StandardCharsets.UTF_8);
System.out.println(text.equals(roundTrip)); // true
~~~

Đây là **mảnh code minh họa** giả định path đã được tạo, phải xử lý IOException. Lưu ý: khi ứng dụng **thao tác dòng** bằng **BufferedReader.readLine()**, phương thức readLine **bỏ dấu kết thúc dòng** trong String trả về; việc dùng **BufferedWriter.newLine()** có thể tạo quy tắc xuống dòng mới. Sự thay đổi đó thuộc **logic xử lý theo dòng**, không phải tác dụng mặc định của Files.readString/writeString.

**Files.writeString không tự thêm BOM (Byte Order Mark) cho UTF-8.** Nếu nội dung String tự chứa ký tự BOM U+FEFF hoặc file ban đầu chứa BOM, ứng dụng cần quyết định xử lý rõ ràng. Không coi BOM là ký tự “luôn bị tự động phát hiện và loại bỏ”. UTF-8 không bắt buộc BOM để giải mã.

## 8. Đọc mã hóa khác UTF-8: phải chỉ định Charset có cơ sở

Không phải tất cả file text đều là UTF-8. Một số phần mềm cũ có thể dùng ISO-8859-1, Windows-1252 hoặc UTF-16. Khi biết chắc encoding từ metadata, chuẩn giao thức hoặc thỏa thuận với bên tạo file, dùng overload Charset:

~~~java
String latin1Text = Files.readString(
        Path.of("legacy.txt"), StandardCharsets.ISO_8859_1);

Files.writeString(
        Path.of("export.txt"),
        latin1Text,
        StandardCharsets.UTF_8);
~~~

Đây là **mảnh minh họa**, cần import **Files**, **Path**, **StandardCharsets** và xử lý IOException. Nếu bước đọc đúng charset, bước ghi có thể **chuyển văn bản sang UTF-8** mà vẫn giữ ý nghĩa các ký tự đại diện được. Tuy nhiên byte file đích không nhất thiết giống byte file nguồn, vì đây là **chuyển mã văn bản**, không phải sao chép byte.

**Không phải mọi charset đều biểu diễn được mọi ký tự.** Nếu cố ghi ký tự ngoài miền hỗ trợ của charset đích, API có thể báo lỗi chuyển mã thay vì âm thầm mang lại văn bản hoàn chỉnh. Không đơn giản “thử UTF-8, nếu lỗi thì Latin-1” như một thuật toán nhận diện tin cậy: Latin-1 có ánh xạ cho mọi byte, nên một file theo encoding khác có thể bị đọc thành ký tự sai mà không ném lỗi.

Với file chưa rõ encoding, nên xác định nguồn gốc, yêu cầu người cung cấp khai báo charset, hoặc dùng công cụ phát hiện encoding với hiểu biết rõ về giới hạn độ tin cậy của nó.

## 9. Những lưu ý quan trọng về file, lỗi và tài nguyên

### 9.1. Đường dẫn và thư mục cha

**Path.of("data", "note.txt")** là đường dẫn tương đối đến thư mục làm việc của tiến trình. Nếu cha **data** chưa tồn tại, writeString **không tự tạo cha**. Cần tạo trước:

~~~java
Path directory = Path.of("data", "notes");
Files.createDirectories(directory);
Files.writeString(directory.resolve("today.txt"), "Hello UTF-8");
~~~

Đây là mảnh minh họa; với dữ liệu thật cần quyết định chính sách tên file, quyền ghi và trường hợp file đích đã tồn tại. **Files.createDirectories** làm việc ở cấp thư mục, trong khi **Files.writeString** làm việc ở cấp file.

### 9.2. Quyền truy cập và lỗi I/O

Các phương thức **readString** và **writeString** ném **IOException** hoặc các lớp con khi thất bại về I/O, ví dụ file không có, lỗi quyền, file là thư mục, lỗi ổ đĩa, hoặc sai encoding. Trường hợp nào xuất hiện phụ thuộc hệ điều hành và filesystem.

Không nên chỉ bắt **Exception** rồi bỏ qua: điều đó làm ứng dụng tưởng đã lưu thành công dù file có thể không tồn tại hoặc bị cắt dở. Với file cấu hình quan trọng, cần báo lỗi cụ thể và cân nhắc sao lưu/ghi thay thế an toàn.

### 9.3. File không được đọc/ghi nguyên tử toàn bộ

Một lần **Files.readString** không “khóa ảnh chụp” vĩnh viễn của file trước mọi thay đổi đồng thời; nếu tiến trình khác đang sửa file, kết quả phụ thuộc hệ thống và thời điểm đọc. Tương tự **writeString** không phải một phép commit nguyên tử đa byte; nếu lỗi xảy ra sau khi file đã bị truncate hoặc đã ghi một phần, nội dung có thể không hoàn chỉnh.

Nếu cần bảo toàn bản cũ khi cập nhật file, có thể ghi sang file tạm nằm trong cùng filesystem, đóng/sync theo mức độ bền cần thiết rồi thử **Files.move** với chính sách replace/atomic phù hợp. **ATOMIC_MOVE** có thể không được hỗ trợ; cần xử lý **AtomicMoveNotSupportedException**.

### 9.4. Vấn đề kích thước và luồng dữ liệu

Nếu muốn đọc từng dòng thay vì gom vào String:

~~~java
try (var reader = Files.newBufferedReader(
        path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý line theo từng dòng.
    }
}
~~~

Đây là **mảnh mã minh họa**, giả định path đã khai báo và nằm trong phương thức xử lý IOException. Chọn **Files.lines(path, charset)** cũng có thể phù hợp, nhưng phải **đóng Stream<String>** khi dùng xong, thường bằng try-with-resources. Cả hai lựa chọn giúp xử lý tuần tự mà không buộc giữ toàn bộ nội dung text trong một String lớn.

Nếu muốn giữ nguyên file nhị phân hoặc chỉ sao chép dữ liệu: **Files.copy(...)** hoặc stream byte là lựa chọn đúng. **readString** không có ý nghĩa với file có byte không hợp lệ theo charset đã chọn.

### 9.5. Vấn đề đồng thời và ghi nối tiếp

**APPEND** chỉ là chính sách ghi của file, không phải cơ chế khóa phân tán hay bảo đảm một bản ghi văn bản có nhiều byte sẽ không xen lẫn với ghi của tiến trình khác. Nếu nhiều thread/process cùng viết, cần chiến lược serialize, file locking hoặc một dịch vụ ghi có tính nhất quán theo nhu cầu.

## 10. Các lỗi thường gặp

1. **Nghĩ Files.readString(path) dùng Charset.defaultCharset().** Sai: overload không truyền charset xác định **UTF-8** theo hợp đồng API.
2. **Nghĩ UTF-8 chỉ có từ Java 18.** Sai: các overload không truyền charset này có mặc định UTF-8 kể từ Java 11; thay đổi JEP 400 là câu chuyện về charset mặc định chung.
3. **Đọc ISO-8859-1 bằng readString không truyền charset.** Có thể ném MalformedInputException hoặc trả text sai; chọn charset theo hợp đồng dữ liệu.
4. **Dùng String/readString để sao chép nhị phân.** Byte không hợp lệ theo charset có thể khiến thao tác lỗi hoặc biến đổi dữ liệu; dùng API byte.
5. **Gọi writeString rồi tưởng file cũ vẫn giữ nguyên.** Mặc định là tạo mới hoặc **truncate** file có sẵn.
6. **Dùng APPEND mà quên tạo dấu xuống dòng.** Files.writeString không tự thêm LF/CRLF; muốn xuống dòng phải đưa ký tự vào text.
7. **Cho rằng writeString tự tạo thư mục cha.** Cần **Files.createDirectories(parent)** trước khi ghi vào thư mục chưa tồn tại.
8. **Tưởng readString tiết kiệm RAM như BufferedReader.readLine.** Nó đọc cả nội dung và trả một String; không phù hợp dữ liệu cực lớn.
9. **Bỏ qua byte BOM và quy tắc newline.** Không mặc định loại/ghi BOM hay chuẩn hóa newline; kiểm tra yêu cầu của hệ thống nhận.
10. **Đọc file bằng charset A, ghi bằng B rồi so byte trực tiếp.** Chuyển mã có thể đổi byte dù ký tự tương đương; cần kiểm tra nội dung theo mục tiêu.
11. **Cho rằng writeString là ghi đè nguyên tử.** Lỗi I/O giữa chừng có thể để lại file dở; cần chiến lược update an toàn nếu dữ liệu quan trọng.
12. **Không quản lý file lớn hoặc dữ liệu không tin cậy.** Cân nhắc giới hạn kích thước, bộ nhớ, quyền truy cập và chính sách khi đọc.
13. **So số byte với String.length() rồi coi khác là lỗi.** UTF-8 là mã hóa biến độ dài, String.length() đo UTF-16 code units.
14. **Dựa vào encoding hiển thị của console để phán xét encoding của file.** Hãy kiểm tra byte (Files.readAllBytes/HexFormat) hoặc dùng một trình xem file có charset được chỉ định.
15. **Quên charset tường minh trong các API khác.** Mặc định UTF-8 ở readString/writeString không chứng minh mọi Reader/Writer hoặc thư viện bên ngoài đều dùng cùng charset.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Java 17 chạy trên hệ thống có default charset không phải UTF-8. **Files.readString(path)** mặc định dùng charset nào?  
**Đáp:** Vẫn là **UTF-8**, do API tự quy định.

**Câu 2.** File UTF-8 chứa **"Café\n€"** có bao nhiêu Java char và byte UTF-8?  
**Đáp:** **6 char** theo String.length() và **9 byte** khi mã hóa UTF-8.

**Câu 3.** Gọi **Files.writeString(path, "ABC")** khi file cũ chứa "LONG TEXT" sẽ ra sao?  
**Đáp:** Theo hành vi mặc định, file bị **truncate** rồi ghi **ABC**, không giữ phần chữ cũ.

**Câu 4.** Muốn tạo file chỉ khi chưa tồn tại, không được ghi đè thì dùng option nào?  
**Đáp:** **StandardOpenOption.CREATE_NEW** cùng chế độ ghi thích hợp, ví dụ **WRITE**.

**Câu 5.** Muốn nối thêm "hello" và tự xuống dòng, có thể chỉ viết **writeString(path, "hello", APPEND)** không?  
**Đáp:** Không tự xuống dòng. Nếu muốn LF, phải ghi thêm ký tự **\n** vào text, và cấu hình CREATE khi file chưa tồn tại nếu cần.

**Câu 6.** Nếu **Files.readString(path)** báo MalformedInputException thì chắc chắn file đã bị hỏng?  
**Đáp:** Không nhất thiết. Có thể file hợp lệ theo **charset khác UTF-8**, chẳng hạn ISO-8859-1, nhưng đang được giải mã sai.

**Câu 7.** Khi file là video hàng GB, nên dùng readString để tiện thao tác không?  
**Đáp:** Không. Video là dữ liệu nhị phân và kích thước lớn; dùng Files.copy hoặc luồng byte theo khối.

**Câu 8.** Nếu muốn đọc log lớn từng dòng, API nào phù hợp hơn?  
**Đáp:** **Files.newBufferedReader(path, charset)** hoặc **Files.lines(path, charset)** có quản lý tài nguyên.

## 12. Tổng kết

**Files.readString(Path)** và **Files.writeString(Path, CharSequence)** là các API tiện lợi để đọc/ghi **toàn bộ văn bản**. Mặc định của chính chúng là **UTF-8**, **không phụ thuộc** vào việc JVM đang báo Charset.defaultCharset() gì. Nếu dữ liệu nguồn/đích dùng charset khác, hãy truyền Charset tường minh và chấp nhận rằng chuyển mã có thể thay biểu diễn byte.

Khi viết file, phải hiểu **writeString mặc định tạo hoặc cắt bỏ nội dung cũ**, còn APPEND/CREATE_NEW là lựa chọn có chủ đích. Khi đọc, cần xử lý **IOException và dữ liệu sai charset**, tránh dồn file cực lớn vào RAM. Các phương thức này dành cho text; việc sao chép byte tuyệt đối hoặc xử lý dữ liệu nhị phân cần API luồng byte/Files.copy tương ứng.

### Tài liệu tham khảo chính thức

- [Oracle Java SE 17 — Files.readString / Files.writeString](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): overload UTF-8 mặc định, Charset, OpenOption, IOException.
- [Oracle Java SE 17 — StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html): UTF_8, ISO_8859_1, UTF_16.
- [Oracle Java SE 17 — StandardOpenOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html): CREATE, TRUNCATE_EXISTING, APPEND, CREATE_NEW.
- [Oracle Java SE 17 — MalformedInputException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/MalformedInputException.html): byte không tạo thành input hợp lệ theo charset.
- [OpenJDK JEP 400 — UTF-8 by Default](https://openjdk.org/jeps/400): thay đổi charset mặc định của Java SE từ JDK 18, khác với UTF-8 cố định của readString/writeString.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="files-read-write-003">readAllLines và dữ liệu được đưa toàn bộ vào bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# `Files.readAllLines()` và việc đưa toàn bộ dữ liệu vào bộ nhớ trong Java

## Mục tiêu bài học

Trong Java I/O, `Files.readAllLines(path)` rất tiện khi cần đọc một tệp văn bản thành từng dòng. Chỉ với một lời gọi, ta nhận được `List<String>` để duyệt, truy cập theo chỉ số, đếm hoặc so sánh dòng. Tuy nhiên, điểm quan trọng nhất cần nhớ là **phương thức đọc hết nội dung tệp và giữ tất cả các dòng trong bộ nhớ của tiến trình Java**.

Sau bài học, bạn sẽ:

- Hiểu chính xác kết quả và hành vi của `Files.readAllLines(Path)` và `Files.readAllLines(Path, Charset)`.
- Giải thích sự khác biệt giữa **đọc háo hức (eager)** toàn bộ dòng và **đọc theo luồng (streaming/lazy)**.
- Biết tại sao kích thước tệp trên ổ đĩa **không bằng** dung lượng heap dùng cho `List<String>`.
- Dùng `Files.lines()` hoặc `Files.newBufferedReader()` khi dữ liệu lớn hoặc không biết trước kích thước.
- Tránh nhầm các ký tự xuống dòng, charset, tài nguyên cần đóng và giới hạn của cách kiểm tra `Files.size()`.

Các ví dụ sử dụng **Java 17+**, có lệnh biên dịch/chạy và đầu ra dễ kiểm chứng.

## 1. `readAllLines` thực sự làm gì?

Các overload cần nhớ trong `java.nio.file.Files`:

```java
public static List<String> readAllLines(Path path) throws IOException;

public static List<String> readAllLines(Path path, Charset cs)
        throws IOException;
```

Đây là **chữ ký API minh họa**, không phải một chương trình độc lập.

- `path` xác định vị trí tệp trên filesystem. Nếu path tương đối, vị trí của nó phụ thuộc thư mục làm việc hoặc base được giải quyết bởi filesystem.
- `Files.readAllLines(path)` sử dụng **UTF-8** theo hợp đồng API hiện đại của Java.
- `Files.readAllLines(path, charset)` giải mã byte bằng charset do bạn chọn, ví dụ `StandardCharsets.UTF_8` hoặc `StandardCharsets.UTF_16LE`.
- Kết quả là **một danh sách chứa tất cả các dòng đã đọc**; mỗi phần tử là một `String` không chứa ký tự kết thúc dòng.
- Phương thức đọc cho đến **EOF** hoặc lỗi I/O. Nó không trả trước các dòng đã có rồi để bạn đọc phần còn lại theo nhu cầu.
- Sau khi lời gọi hoàn tất, nội dung các dòng nằm trong `List<String>` ở bộ nhớ Java và bạn không cần giữ một stream file đang mở để duyệt danh sách.
- Nếu tệp không tồn tại, không thể đọc, bị hỏng theo charset đang dùng hoặc có lỗi truy cập, lời gọi có thể ném `IOException` hoặc một lớp con thích hợp.

Ví dụ tư duy:

```text
Tệp UTF-8:
  alpha\n
  \n
  beta\r\n
  gamma\n

Files.readAllLines(...)
          |
          v
List<String> trong heap
  index 0: "alpha"
  index 1: ""
  index 2: "beta"
  index 3: "gamma"
```

Trong sơ đồ, `\n` và `\r\n` biểu thị ký hiệu kết thúc dòng, **không phải** các dấu gạch chéo được lưu trong danh sách. Một dòng rỗng **ở giữa tệp** tạo ra phần tử `""`; dấu kết thúc dòng đơn thuần ở **cuối tệp** không tạo thêm một dòng rỗng sau cùng.

Các dấu kết thúc dòng được Java nhận diện hiện nay gồm LF (`\n`), CR (`\r`) và CRLF (`\r\n`). API có thể mở rộng tập dấu kết thúc dòng được nhận diện trong các phiên bản tương lai, nên không xây định dạng lưu trữ nhị phân dựa vào giả định chỉ có đúng ba loại này mãi mãi.

## 2. Ví dụ Java 17+: đọc toàn bộ tệp thành `List<String>`

Chương trình dưới đây tự tạo tệp tạm để không phụ thuộc đường dẫn trên máy người học. Nó chứa một dòng rỗng ở giữa, các kiểu newline khác nhau và một dấu newline cuối tệp.

Lưu thành **`ReadAllLinesDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ReadAllLinesDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("lines-demo-", ".txt");

        try {
            Files.writeString(
                    file,
                    "Java\n\nPython\r\nGo\n",
                    StandardCharsets.UTF_8
            );

            List<String> lines =
                    Files.readAllLines(file, StandardCharsets.UTF_8);

            System.out.println("number of lines = " + lines.size());
            System.out.println("first = " + lines.get(0));
            System.out.println("second is empty = " + lines.get(1).isEmpty());
            System.out.println("last = " + lines.get(lines.size() - 1));
            System.out.println("joined = " + String.join("|", lines));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ReadAllLinesDemo.java
java ReadAllLinesDemo
```

Kết quả:

```text
number of lines = 4
first = Java
second is empty = true
last = Go
joined = Java||Python|Go
```

**Giải thích:**

1. `Files.writeString` ghi đúng chuỗi được chỉ định với mã hóa UTF-8.
2. `readAllLines` đọc đến hết tệp và tạo một `List<String>` có bốn phần tử: `"Java"`, `""`, `"Python"`, `"Go"`.
3. `lines.get(1).isEmpty()` cho thấy dòng trống ở giữa vẫn được giữ lại.
4. Dấu `\n` ở cuối không làm thêm dòng thứ năm.
5. `String.join("|", lines)` chỉ thay đổi cách ta hiển thị danh sách; nó không khôi phục chính xác các dấu xuống dòng ban đầu, vì `readAllLines` đã loại bỏ chúng.
6. `finally` dọn đúng tệp tạm vừa tạo. `readAllLines` tự quản lý việc mở/đóng nguồn đọc nội bộ; không cần gọi `close()` trên `List<String>`.

**Trường hợp đặc biệt:** Tệp hoàn toàn rỗng trả về danh sách rỗng (`lines.size() == 0`). Khi xử lý input có thể rỗng, kiểm tra `isEmpty()` trước khi dùng `get(0)`.

## 3. Vì sao “đưa toàn bộ vào bộ nhớ” là vấn đề lớn?

Đây là điểm phân biệt quan trọng nhất trong đề bài.

```text
                         Files.readAllLines(path)
                                   |
               đọc toàn bộ, giải mã, tách dòng
                                   |
                                   v
Heap Java:    List<String>
             +-----------------------------------+
             | reference -> String dòng 1        |
             | reference -> String dòng 2        |
             | reference -> String dòng 3        |
             | ...                               |
             | reference -> String dòng N        |
             +-----------------------------------+
                                   |
                         toàn bộ dữ liệu còn sống
                         khi danh sách còn được giữ
```

Trong khi danh sách còn được tham chiếu, các đối tượng `String` và cấu trúc chứa chúng cũng tiếp tục chiếm bộ nhớ. Tổng bộ nhớ sử dụng có thể **khác đáng kể so với dung lượng tệp trên đĩa**, vì:

- Byte trong tệp phải được **giải mã** thành dữ liệu chuỗi Java. Số byte UTF-8 không bằng số Java `char` hay dung lượng biểu diễn chuỗi trong bộ nhớ.
- Có **overhead của từng `String`**, các tham chiếu tới phần tử và cấu trúc danh sách. Với hàng triệu dòng rất ngắn, overhead theo **số lượng dòng** có thể đáng kể.
- Có thể xuất hiện các buffer và đối tượng trung gian trong quá trình đọc/giải mã; mức sử dụng bộ nhớ cao nhất trong lúc đọc không nhất thiết bằng dung lượng chỉ của danh sách kết quả.
- Cách JVM biểu diễn chuỗi (chẳng hạn compact strings), kiến trúc runtime và phiên bản Java ảnh hưởng kích thước thực tế. **Không có công thức cố định** kiểu “file 100 MB thì danh sách luôn tốn đúng 200 MB”.
- Nếu tiếp tục gọi `stream()`, `map()`, `toArray()`, `copyOf()`, `collect()` hoặc tạo bản sao khác, chương trình có thể giữ thêm nhiều đối tượng và làm tăng peak memory.

**Độ phức tạp khái quát:** lưu toàn bộ `N` dòng cần bộ nhớ tăng theo **tổng độ dài nội dung được giữ cộng overhead mỗi dòng**, thay vì chỉ theo kích thước của buffer đọc. `readAllLines` phù hợp với dữ liệu **nhỏ, có giới hạn rõ**; với log nhiều GB, file CSV rất lớn hoặc dữ liệu do người dùng tải lên không có giới hạn, đây thường không phải lựa chọn tốt.

Nếu hết heap, chương trình có thể gặp `OutOfMemoryError`. Đây là một `Error` nghiêm trọng, không nên coi việc bắt nó rồi cố xử lý tiếp như chiến lược vận hành bình thường. **Phòng ngừa bằng chọn thuật toán và giới hạn đầu vào phù hợp** mới là hướng chính.

### Ví dụ so sánh định lượng mang tính minh họa

Một tệp 10 triệu dòng, mỗi dòng chỉ vài ký tự, vẫn đòi hỏi tạo/giữ một lượng lớn phần tử `String` và tham chiếu danh sách. Ngay cả khi số byte văn bản trên đĩa không quá lớn, heap cần thiết có thể vượt giới hạn `-Xmx` của JVM. Đây là **nhận định về xu hướng và overhead**, không phải cam kết một mức sử dụng RAM cụ thể; muốn đo thực tế phải profiling trên JDK và dữ liệu cụ thể.

`Files.size(path)` trả về kích thước tệp theo metadata (đơn vị byte) tại một thời điểm; nó **không dự đoán chính xác dung lượng heap của `readAllLines`**, không tính overhead chuỗi, và cũng không bảo đảm tệp không thay đổi sau khi kiểm tra.

## 4. So sánh ba cách đọc dòng

| Tiêu chí | `Files.readAllLines(path, cs)` | `Files.lines(path, cs)` | `Files.newBufferedReader(path, cs)` |
| --- | --- | --- | --- |
| Kết quả | `List<String>` | `Stream<String>` | `BufferedReader` |
| Mô hình | Đọc hết trước khi trả kết quả | Stream đọc/duyệt theo nhu cầu | Vòng lặp đọc từng dòng |
| Lưu tất cả dòng? | **Có** | Không bắt buộc, tùy pipeline | Không bắt buộc, tùy code |
| Có thể chỉ lấy vài dòng đầu? | Đã đọc hết rồi | Có thể dùng `limit(n)`, nhưng có thể đọc trước một phần | Có thể dừng vòng lặp |
| Tài nguyên cần đóng | Hàm tự hoàn thành việc đọc/đóng trước khi trả | **Phải đóng Stream** bằng `try-with-resources` | **Phải đóng Reader** bằng `try-with-resources` |
| Truy cập bằng chỉ số bất kỳ sau đọc | Có: `list.get(i)` | Không trực tiếp | Không trực tiếp |
| Tình huống phù hợp | File nhỏ/có giới hạn, cần giữ các dòng | Lọc/đếm/biến đổi từng dòng | Parser theo vòng lặp, logic điều khiển chi tiết |
| Cạm bẫy | Heap tăng theo toàn tệp | `toList()`/`collect(...)` có thể lại đưa hết vào RAM | Tự tích lũy toàn bộ dòng cũng có vấn đề |

`Files.lines` và `BufferedReader` có thể đọc trước vào buffer, nhưng thường **không cần giữ toàn bộ các dòng đã xử lý**. Tuy nhiên, chúng không bảo đảm tiêu thụ một mức bộ nhớ cố định trong mọi tình huống: **một dòng quá dài** cũng có thể khiến `readLine()` tạo chuỗi rất lớn; các toán tử `sorted()`, `distinct()`, `toList()` hoặc lưu kết quả vào `List` cũng có thể phải giữ rất nhiều dữ liệu.

**Quy tắc chọn API:** Nếu cần random access hoặc duyệt nhiều lần trên một file nhỏ, `readAllLines` tiện. Nếu chỉ cần thống kê, lọc, xử lý từng dòng và bỏ qua nội dung đã dùng, `Files.lines` hoặc `BufferedReader` thường phù hợp hơn.

## 5. Dùng `Files.lines()` để xử lý theo luồng

`Files.lines(path, charset)` trả về `Stream<String>` để xử lý qua `filter`, `map`, `limit`, `count`, `forEach`. Stream có tính chất lazy: thao tác trung gian không tự chạy cho đến khi có thao tác kết thúc (*terminal operation*). Việc tiêu thụ một nguồn file thường diễn ra trong lúc thực hiện pipeline, không cần đợi đọc toàn bộ file rồi mới trả dữ liệu.

### Chương trình Java 17+: đếm dòng cảnh báo

Lưu **`StreamLinesDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class StreamLinesDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("stream-lines-", ".txt");

        try {
            Files.writeString(file,
                    "INFO started\nWARN low\nWARN high\nINFO done\n",
                    StandardCharsets.UTF_8);

            try (Stream<String> lines =
                         Files.lines(file, StandardCharsets.UTF_8)) {
                long warnings = lines
                        .filter(line -> line.startsWith("WARN "))
                        .count();

                System.out.println("warnings = " + warnings);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac StreamLinesDemo.java
java StreamLinesDemo
```

Kết quả:

```text
warnings = 2
```

**Phân tích:**

- `lines.filter(...)` tạo mô tả pipeline, còn `count()` kích hoạt việc duyệt và đếm.
- Không có `List<String>` chứa cả bốn dòng. Chương trình chỉ cần giữ trạng thái đếm và phần dữ liệu đang được xử lý.
- `Stream<String>` mở từ file có tài nguyên I/O bên dưới: **phải đóng** bằng `try-with-resources`, kể cả khi dùng `limit(1)` hoặc có lỗi trong xử lý.
- Một Stream chỉ được tiêu thụ qua pipeline một lần. Nếu cần quét lại tệp, hãy mở `Files.lines(...)` mới hoặc chọn chiến lược lưu dữ liệu có giới hạn.
- Lỗi I/O phát sinh trong lúc duyệt stream có thể được bọc trong `UncheckedIOException` thay vì chỉ xuất hiện lúc gọi `Files.lines`. Đừng quên xử lý lỗi theo thiết kế ứng dụng.

**Cẩn thận:** Thay `count()` bằng `toList()` hay `collect(Collectors.toList())` sẽ tạo một collection chứa toàn bộ kết quả được thu thập. Dù đầu vào đến từ API lazy, **toàn bộ kết quả vẫn có thể nằm trên heap**.

Ví dụ anti-pattern:

```java
// Chỉ minh họa, không phải chương trình độc lập.
try (Stream<String> stream = Files.lines(path, StandardCharsets.UTF_8)) {
    List<String> all = stream.toList(); // Vẫn gom toàn bộ dòng vào RAM.
}
```

`Files.lines` không phải một “lá chắn chống OOM” nếu pipeline phía sau chủ động giữ hết dữ liệu.

## 6. Dùng `Files.newBufferedReader()` cho vòng lặp có kiểm soát

Nếu cần parser với trạng thái phức tạp, điều kiện dừng, đếm từng dòng hoặc xử lý lỗi theo vị trí, `BufferedReader` là lựa chọn dễ đọc:

```java
// Đoạn minh họa: path đã có, xử lý IOException tại nơi gọi.
try (BufferedReader reader =
             Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        process(line); // Phương thức của ứng dụng, không có trong snippet này.
    }
}
```

`readLine()` trả về `null` khi EOF. Một dòng trống hợp lệ trả về `""`, **không phải `null`**. Điều kiện vòng lặp phải kiểm tra `!= null`, không kiểm tra `!line.isEmpty()` để tránh dừng nhầm khi gặp dòng rỗng.

### Chương trình Java 17+: đọc 10.000 dòng nhưng chỉ giữ bộ đếm

Lưu thành **`BufferedCounterDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BufferedCounterDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("counter-lines-", ".txt");

        try {
            try (BufferedWriter writer =
                         Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                for (int i = 0; i < 10_000; i++) {
                    writer.write(i % 10 == 0 ? "ERROR " : "INFO ");
                    writer.write(Integer.toString(i));
                    writer.newLine();
                }
            }

            int total = 0;
            int errors = 0;

            try (BufferedReader reader =
                         Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    total++;
                    if (line.startsWith("ERROR ")) {
                        errors++;
                    }
                }
            }

            System.out.println("lines = " + total);
            System.out.println("errors = " + errors);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac BufferedCounterDemo.java
java BufferedCounterDemo
```

Kết quả:

```text
lines = 10000
errors = 1000
```

Dù tệp có 10.000 dòng, chương trình không xây dựng `List<String>` chứa chúng. Vòng lặp chỉ giữ dòng đang xét và hai bộ đếm. `BufferedWriter` được đóng trước khi chương trình bắt đầu đọc, để dữ liệu đã được flush/đóng về phía tệp.

**Giới hạn:** Nếu một dòng duy nhất rất dài, `BufferedReader.readLine()` vẫn phải tạo một `String` chứa nguyên dòng đó. Đối với đầu vào không tin cậy có thể chứa dòng nhiều GB hoặc file không có dấu xuống dòng, muốn khống chế chặt bộ nhớ phải thiết kế bộ phân tích theo khối với giới hạn byte/ký tự rõ ràng, không chỉ thay `readAllLines` bằng `readLine()`.

## 7. Có thể kiểm tra `Files.size()` trước khi dùng `readAllLines` không?

Có thể kiểm tra kích thước tệp trước khi dùng phương thức đọc hết nhằm giảm rủi ro vô tình nạp file quá lớn:

```java
// Snippet minh họa; cần import java.nio.file.Files/Path,
// java.nio.charset.StandardCharsets, java.io.IOException và java.util.List.
static List<String> readSmallText(Path path) throws IOException {
    long maxBytes = 1_000_000L;

    if (Files.size(path) > maxBytes) {
        throw new IOException("file exceeds configured size limit");
    }
    return Files.readAllLines(path, StandardCharsets.UTF_8);
}
```

Mẫu này phù hợp với **file thông thường, ổn định, có giới hạn kích thước theo nghiệp vụ**, nhưng đừng xem nó là bảo vệ tài nguyên tuyệt đối:

1. `Files.size(path)` là kích thước theo filesystem tại một thời điểm; file có thể **thay đổi giữa bước kiểm tra và đọc** (TOCTOU).
2. Kích thước theo **byte** không bằng bộ nhớ cần để tạo `List<String>`. Một file nhiều dòng ngắn vẫn tạo nhiều đối tượng.
3. Một số nguồn/filesystem đặc biệt có thể báo kích thước không hữu ích hoặc thay đổi khi đọc.
4. Một filename/path không tin cậy có thể chịu ảnh hưởng bởi symbolic link hay quyền truy cập; phải xác thực đường dẫn và thao tác file theo yêu cầu bảo mật riêng.
5. Chương trình đồng thời xử lý nhiều tệp dù mỗi tệp có kích thước nhỏ vẫn có thể tiêu thụ nhiều heap.

Với nguồn lớn hoặc không tin cậy, nên dùng **xử lý tăng dần**, giới hạn tổng byte/line, tổng số dòng, chiều dài từng dòng, thời gian và các quota phù hợp. Nếu yêu cầu bắt buộc tuyệt đối, cần đặt các giới hạn trong **quá trình đọc**, không chỉ kiểm tra metadata trước.

## 8. Charset, dòng trống và ký tự xuống dòng

### Charset phải khớp dữ liệu thật

`Files.readAllLines(path)` mặc định UTF-8. Nếu tệp được lưu bằng charset khác (ví dụ Windows-1258 hay UTF-16LE), bạn cần chỉ định charset đúng:

```java
// Đoạn minh họa, giả định path đã được khai báo.
List<String> lines =
        Files.readAllLines(path, StandardCharsets.UTF_16LE);
```

Dùng sai charset có thể khiến đọc thất bại do dữ liệu byte không hợp lệ hoặc tạo ký tự sai dù vẫn đọc được. Với dữ liệu UTF-16 có BOM, quy tắc xử lý BOM/endian còn phụ thuộc charset được chọn; không tự suy đoán toàn bộ từ tên file.

### Giữ hay bỏ dấu kết thúc dòng?

`readAllLines` trả các chuỗi **không chứa** line separator. Ví dụ đầu vào có `\r\n` và `\n` khác nhau nhưng sau khi trả về `List<String>`, bạn không còn thông tin chính xác mỗi dòng dùng kiểu xuống dòng nào. Dùng `String.join(System.lineSeparator(), lines)` tạo **một biểu diễn mới**, không phải khôi phục nguyên byte đã có.

Nếu cần so sánh nguyên bản, tính checksum, đọc giao thức nhị phân hoặc giữ y nguyên cách xuống dòng, hãy làm việc với byte và charset theo định dạng rõ ràng; đừng dùng `readAllLines` rồi giả định kết quả vẫn chứa separator gốc.

### Dòng trống và cuối file

- File rỗng: danh sách không có phần tử.
- Hai dấu newline liên tiếp sau một dòng: có thể tạo dòng rỗng ở giữa.
- Một dấu newline kết thúc dòng cuối: không tự tạo thêm một dòng rỗng sau EOF.
- `BufferedReader.readLine()` trả `null` ở EOF; dòng rỗng trả `""`.
- `Files.lines` cũng cung cấp nội dung dòng không kèm terminator, tương thích với mô hình tách dòng của `readAllLines`.

## 9. Khi nào dùng `readAllLines` vẫn là đúng?

**Dùng `readAllLines` khi:**

- Tệp text **nhỏ và có giới hạn tin cậy**, chẳng hạn danh sách mã cấu hình vài KB, dữ liệu fixture trong bài kiểm thử hoặc văn bản ngắn cần tra cứu.
- Cần truy cập dòng theo chỉ số nhiều lần hoặc so sánh các dòng bất kỳ.
- Cần kiểm tra hay thao tác trên **toàn bộ danh sách** mà việc đọc hết ngay từ đầu là hành vi có chủ ý và chi phí có thể dự đoán.
- Muốn mã đơn giản và không cần giữ tài nguyên đọc trong khi xử lý kết quả.

**Ưu tiên `Files.lines` hoặc `newBufferedReader` khi:**

- Tệp log lớn, CSV nhiều dòng, dữ liệu không biết trước kích thước hoặc file có thể tăng liên tục.
- Mục tiêu chỉ là đếm, lọc, thống kê hoặc ghi chuyển tiếp từng dòng.
- Chỉ cần một số dòng đầu, có thể dừng sớm hoặc xử lý theo điều kiện.
- Cần chủ động xử lý từng dòng rồi bỏ đi, tránh tích lũy kết quả trong RAM.

**Chọn API khác khi:**

- Cần **toàn bộ văn bản trong một `String`**: `Files.readString(path, charset)` cũng là phương thức đọc hết, có rủi ro bộ nhớ tương tự; chỉ khác kiểu kết quả.
- Cần **mọi byte nguyên trạng**: `Files.readAllBytes(path)` đưa toàn bộ byte vào bộ nhớ, cũng không phù hợp cho file quá lớn.
- Cần đọc nhị phân, tệp cực lớn hoặc cần giới hạn bộ nhớ nghiêm ngặt: xử lý bằng `InputStream` theo từng khối, parser có giới hạn hoặc API I/O phù hợp với định dạng.
- Cần tail log hoặc theo dõi file đang được bổ sung: không sử dụng `readAllLines` như một cơ chế quan sát thay đổi liên tục; hãy thiết kế một quy trình đọc cập nhật/giám sát riêng.

## 10. Các lỗi thường gặp

1. **Đọc log vài GB bằng `readAllLines` rồi mới lọc.** Chương trình có thể hết heap trước khi lọc. Dùng `Files.lines` để lọc khi duyệt.
2. **Nghĩ `Files.lines(path).toList()` tiết kiệm heap.** Nó lại thu toàn bộ dòng thành list; lazy ở nguồn không bảo đảm ít bộ nhớ khi đích gom hết.
3. **Quên đóng `Files.lines`.** Stream nắm tài nguyên tệp cho đến khi đóng; dùng `try-with-resources`, không trông chờ GC.
4. **Nghĩ `BufferedReader.readLine()` xử lý mọi dữ liệu với memory cố định.** Một dòng quá dài vẫn có thể chiếm nhiều bộ nhớ.
5. **Dùng `readAllLines` để giữ newline gốc.** Phương thức bỏ các ký tự kết thúc dòng; không phù hợp cho roundtrip nguyên byte.
6. **Lấy `String.length()` làm kích thước file.** Độ dài chuỗi UTF-16 của Java khác kích thước byte UTF-8 và overhead trên heap.
7. **Nhầm dòng rỗng với EOF.** Dòng rỗng là `""`, EOF của `BufferedReader.readLine()` là `null`.
8. **Dựa vào `Files.size()` như một phép bảo đảm tuyệt đối.** File có thể thay đổi và heap overhead khác byte-size; áp giới hạn lúc xử lý.
9. **Giải mã bằng charset không đúng.** Khai báo `UTF-8`/charset đúng theo metadata hoặc hợp đồng dữ liệu; đừng suy từ phần mở rộng tên file.
10. **Cho rằng kiểm tra `-Xmx` lớn là thay thế thiết kế streaming.** Tăng heap chỉ nâng ngưỡng thất bại và làm tài nguyên tốn kém hơn; cần chọn thuật toán đúng.
11. **Giữ kết quả các phép `map`, `groupingBy`, `sorted` trên Stream không giới hạn.** Những thao tác đó có thể tích lũy nhiều dữ liệu; xem chi phí của toàn pipeline, không chỉ nguồn stream.
12. **Bắt `OutOfMemoryError` để coi như đọc xong.** Lỗi này không cho phép coi danh sách đã hoàn chỉnh và có thể khiến tiến trình ở trạng thái tài nguyên bất ổn.
13. **Tưởng `readAllLines` phù hợp cho file liên tục tăng.** Nó đọc đến thời điểm EOF theo cách nguồn cung cấp, không phải kênh theo dõi tail; cần chiến lược riêng khi log đang được append.

## 11. Bảng quyết định nhanh

| Tình huống | Khuyến nghị |
| --- | --- |
| File cấu hình nhỏ, cần nhiều lần `get(i)` | `Files.readAllLines(path, cs)` |
| Đếm số dòng thỏa điều kiện trong file lớn | `try (Stream<String> s = Files.lines(path, cs)) { ... }` |
| Parser cần vị trí dòng và trạng thái cụ thể | `try (BufferedReader r = Files.newBufferedReader(path, cs)) { ... }` |
| Muốn lấy 10 dòng đầu từ file lớn | `Files.lines(...).limit(10)` và đóng stream |
| File nhiều GB, chỉ cần tính tổng | Streaming, chỉ giữ biến tổng và state tối thiểu |
| Dữ liệu có thể chứa một dòng rất lớn | Parser theo khối và ràng buộc chiều dài dòng |
| Cần giữ nguyên byte và newline | `InputStream` hoặc API byte theo định dạng |
| Cần một `String` chứa hết file nhỏ | `Files.readString(path, cs)`, vẫn toàn bộ trong RAM |
| Dữ liệu từ người dùng không đáng tin | Giới hạn byte/dòng/tổng xử lý và xác thực đầu vào |

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `Files.readAllLines(path)` có trả từng dòng dần dần giống một iterator từ file đang mở không?

**Trả lời:** Không. Nó đọc toàn bộ nội dung và trả về một `List<String>` chứa tất cả các dòng đã đọc.

**Câu 2.** Một tệp 80 MB trên đĩa có chắc chắn chỉ tiêu tốn 80 MB heap khi dùng `readAllLines` không?

**Trả lời:** Không. Bộ nhớ còn gồm chuỗi đã giải mã, các đối tượng `String`, tham chiếu và danh sách, cộng bộ đệm trung gian; mức tiêu thụ phụ thuộc dữ liệu và JVM.

**Câu 3.** Tại sao `Files.lines(path).toList()` chưa giải quyết vấn đề bộ nhớ?

**Trả lời:** Vì dù việc đọc từ file là lazy, `toList()` thu toàn bộ dòng vào bộ nhớ để trả danh sách.

**Câu 4.** Với `BufferedReader`, khi nào `readLine()` trả `null` và khi nào trả `""`?

**Trả lời:** `null` nghĩa là EOF; `""` nghĩa là đọc được một dòng rỗng hợp lệ.

**Câu 5.** Có cần đóng `List<String>` do `readAllLines` trả về không?

**Trả lời:** Không. Nó chỉ là danh sách trong bộ nhớ; phương thức đã hoàn thành việc đọc. Ngược lại, `Stream<String>` từ `Files.lines` và `BufferedReader` cần được đóng.

**Câu 6.** Nếu tệp có newline cuối cùng, `readAllLines` có luôn thêm một phần tử `""` vào cuối list không?

**Trả lời:** Không. Dấu xuống dòng kết thúc dòng cuối không tự tạo một dòng rỗng mới sau EOF.

**Câu 7.** Khi xử lý dữ liệu ngoài hệ thống và yêu cầu giới hạn tài nguyên nghiêm ngặt, chỉ kiểm tra `Files.size(path)` trước `readAllLines` đã đủ chưa?

**Trả lời:** Chưa. Tệp có thể thay đổi, overhead bộ nhớ không bằng byte-size, và một số nguồn có metadata không phản ánh đầy đủ dữ liệu. Cần giới hạn trong lúc đọc và xử lý.

## 13. Tài liệu tham khảo

- [Java 17 — `Files.readAllLines(Path, Charset)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#readAllLines(java.nio.file.Path,java.nio.charset.Charset))
- [Java 17 — `Files.lines(Path, Charset)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#lines(java.nio.file.Path,java.nio.charset.Charset))
- [Java 17 — `Files.newBufferedReader(Path, Charset)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#newBufferedReader(java.nio.file.Path,java.nio.charset.Charset))
- [Java 17 — `BufferedReader.readLine()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html#readLine())

**Ghi nhớ:** `Files.readAllLines()` là API **eager** và giữ toàn bộ dòng trong `List<String>` ở heap. Nó thuận tiện cho tệp nhỏ, giới hạn rõ. Khi chỉ cần duyệt/lọc/đếm các dòng của tệp lớn, chọn `Files.lines()` hoặc `Files.newBufferedReader()`, đóng tài nguyên đúng lúc và tránh gom kết quả trở lại thành một danh sách khổng lồ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="files-read-write-004">Files.lines đọc lười và bắt buộc quản lý vòng đời Stream</a>

<details>
<summary>Xem chi tiết</summary>

# Files.lines đọc lười và bắt buộc quản lý vòng đời Stream trong Java

## 1. Files.lines() giải quyết việc gì?

Trong Java NIO.2, **`java.nio.file.Files.lines(Path)`** trả về một **`Stream<String>`** gồm các dòng văn bản từ file. Thay vì đọc toàn bộ file thành `List<String>` trước khi xử lý, ứng dụng có thể xây dựng một chuỗi thao tác `filter`, `map`, `limit`, `findFirst`, `count`... và xử lý nội dung theo nhu cầu.

Ví dụ:

~~~java
// Mảnh code: path là Path hợp lệ; phương thức xử lý IOException.
try (java.util.stream.Stream<String> lines =
        java.nio.file.Files.lines(
                path, java.nio.charset.StandardCharsets.UTF_8)) {
    long count = lines
            .filter(line -> line.startsWith("ERROR"))
            .count();
    System.out.println(count);
}
~~~

Ở đây **`Files.lines()` tạo Stream gắn với file**, còn `filter()` chưa duyệt các dòng ngay. Khi `count()` là terminal operation được thực thi, Stream bắt đầu cần các phần tử để tính kết quả. Kết thúc khối **try-with-resources** tự gọi `close()` trên Stream, nhờ đó giải phóng tài nguyên I/O.

**Thông điệp quan trọng nhất:**

> **Đọc lười (lazy) không có nghĩa là không mở tài nguyên.** `Files.lines()` mở file để tạo Stream, nhưng nội dung được đọc theo tiến trình tiêu thụ Stream. Vì vậy `Stream<String>` nhận từ `Files.lines()` phải được đóng sau khi sử dụng.

Đây là sự khác biệt quan trọng giữa Stream **tính toán trong bộ nhớ** như `List.stream()` và Stream **gắn với tài nguyên ngoài** như `Files.lines()`. Dùng Stream API nói chung không buộc phải `close()`, nhưng tài liệu `Files.lines()` yêu cầu đóng Stream để giải phóng file đang mở.

## 2. Ba giai đoạn: mở file, xây pipeline và tiêu thụ

Sơ đồ:

~~~text
Path tới file
    |
    | Files.lines(path, UTF_8)
    v
Stream<String> đã gắn với tài nguyên I/O
    |
    | .filter(...)
    | .map(...)
    | .limit(...)
    v
Pipeline đã khai báo — chưa duyệt toàn bộ dữ liệu
    |
    | .count() / .findFirst() / .forEach() / .toList()
    v
Terminal operation khiến dữ liệu được tiêu thụ
    |
    | Kết thúc try-with-resources
    v
Stream.close() -> đóng tài nguyên file liên quan
~~~

Cần phân biệt:

1. **`Files.lines(...)` có thể ném `IOException` ngay tại thời điểm gọi**, chẳng hạn file không mở được vì quyền truy cập hoặc đường dẫn sai. Không nên nói rằng “hoàn toàn không có I/O cho đến `count()`”.
2. Các **intermediate operation** như `filter()`, `map()`, `skip()`, `limit()` thường chỉ cấu hình pipeline. Chúng không buộc toàn bộ file được đọc ngay.
3. Khi có **terminal operation**, pipeline bắt đầu được duyệt; một số thao tác như `findFirst()`, `anyMatch()`, `limit(n)` có thể **short-circuit**, nghĩa là không cần xử lý hết file.
4. **Terminal operation kết thúc không đồng nghĩa Stream tự được đóng**. Phải đóng trực tiếp hoặc ưu tiên try-with-resources.
5. Stream chỉ dùng cho **một lần tiêu thụ**. Sau terminal operation, không được chạy thêm terminal operation khác trên cùng pipeline; muốn duyệt lại file, mở Stream mới.

Việc triển khai có thể dùng buffering hoặc tối ưu cách chia dữ liệu; không dựa vào giả định “mỗi lần `map()` sẽ đọc đúng một dòng byte từ ổ đĩa”. *Lazy* mô tả cơ chế thực thi pipeline, không cam kết chi tiết số lần gọi I/O.

## 3. Chương trình Java 17+ đọc lười và đóng tài nguyên

Tạo file **LazyFileLinesDemo.java** (mã nguồn UTF-8):

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class LazyFileLinesDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("lazy-lines-", ".log");

        try {
            Files.writeString(file,
                    "INFO start\n"
                    + "WARN slow\n"
                    + "ERROR disk\n"
                    + "INFO continue\n"
                    + "ERROR network\n",
                    StandardCharsets.UTF_8);

            try (Stream<String> lines =
                    Files.lines(file, StandardCharsets.UTF_8)) {
                System.out.println("streamOpened=true");

                String firstError = lines
                        .filter(line -> line.startsWith("ERROR "))
                        .findFirst()
                        .orElse("not found");

                System.out.println("firstError=" + firstError);
            }

            try (Stream<String> lines =
                    Files.lines(file, StandardCharsets.UTF_8)) {
                long totalErrors = lines
                        .filter(line -> line.startsWith("ERROR "))
                        .count();

                System.out.println("totalErrors=" + totalErrors);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 LazyFileLinesDemo.java
java LazyFileLinesDemo
~~~

Kết quả stdout:

~~~text
streamOpened=true
firstError=ERROR disk
totalErrors=2
~~~

Giải thích:

- Chương trình tạo một file tạm, ghi năm dòng UTF-8, rồi dùng hai lượt `Files.lines()` **độc lập**.
- Lượt đầu dùng `filter(...).findFirst()` để lấy dòng `ERROR disk`. Vì `findFirst()` có thể dừng khi tìm được kết quả, **không nhất thiết xử lý mọi dòng còn lại**.
- Dù pipeline chỉ cần dòng lỗi đầu tiên, Stream vẫn phải **đóng**. TWR đảm nhiệm việc này khi thoát khỏi khối.
- Lượt thứ hai mở một Stream mới và dùng `count()` để đếm tất cả dòng có tiền tố `ERROR `, cho kết quả 2.
- Cuối cùng, `Files.deleteIfExists(file)` xóa file tạm do chương trình tạo. Xóa file là một thao tác riêng, **không phải thay thế cho đóng Stream**.

Các dòng demo chỉ chứa ASCII, nên stdout ổn định khi chạy từ terminal thông thường. Trong chương trình thực tế, nếu dùng tiếng Việt hoặc ký tự Unicode, hai phía đọc/ghi cần dùng cùng charset.

## 4. Vì sao cần try-with-resources?

`Files.lines()` trả một Stream giữ tài nguyên I/O của file, nên mẫu chuẩn là:

~~~java
try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
    // Dùng lines ở đây.
}
~~~

`Stream` kế thừa cơ chế `BaseStream.close()` (BaseStream triển khai `AutoCloseable`), vì vậy nó có thể được khai báo trong try-with-resources.

Khi kết thúc khối try:

- Nếu body thành công, Stream vẫn được đóng.
- Nếu `filter`, `map`, `forEach` hay mã nghiệp vụ ném exception, TWR vẫn thử đóng Stream.
- Nếu body `return`, TWR vẫn chạy close trước khi trả về.
- Nếu đóng có lỗi đồng thời với lỗi body, quy tắc TWR về **primary/suppressed exception** được áp dụng; nguyên nhân từ body không tự bị thay bằng lỗi cleanup.

Không chỉ đóng khi bạn “đã đọc đến EOF”. Một terminal operation đọc hết file, dừng sớm, hoặc thất bại đều cần quản lý vòng đời.

**Ví dụ dễ mắc lỗi:**

~~~java
// SAI: tài nguyên file có thể không được đóng đúng hạn.
long n = Files.lines(path).filter(String::isBlank).count();
~~~

Mặc dù `count()` đã chạy xong, đoạn code không thể hiện việc đóng Stream. Không dựa vào garbage collector để đóng file descriptor. Hãy viết:

~~~java
// ĐÚNG: body có thể đọc hết hoặc ném exception, vẫn close.
try (Stream<String> lines = Files.lines(path)) {
    long n = lines.filter(String::isBlank).count();
}
~~~

**Không viết một hàm trả về Stream đã nằm trong TWR rồi kỳ vọng bên gọi dùng tiếp:**

~~~java
// SAI: Stream bị đóng khi hàm return.
static Stream<String> broken(Path path) throws IOException {
    try (Stream<String> lines = Files.lines(path)) {
        return lines.filter(line -> !line.isBlank());
    }
}
~~~

TWR chạy `close()` trước khi kết quả return được bàn giao cho caller. Caller nhận một pipeline gắn với nguồn đã đóng; tiêu thụ sau đó là không hợp lệ và có thể ném `IllegalStateException` hoặc lỗi liên quan tài nguyên đã đóng.

## 5. Quyền sở hữu Stream khi trả về từ phương thức

Có hai mẫu thiết kế hợp lệ, tùy nhu cầu API.

### Mẫu A — Xử lý trong phương thức, đóng tại chỗ

~~~java
// Mảnh code: thêm imports Path, Files, IOException, StandardCharsets,
// Stream khi đưa vào file nguồn của ứng dụng.
static long countNonBlank(Path path) throws IOException {
    try (Stream<String> lines =
            Files.lines(path, StandardCharsets.UTF_8)) {
        return lines.filter(line -> !line.isBlank()).count();
    }
}
~~~

Đây là mẫu **dễ hiểu và ít rò rỉ tài nguyên** nhất. Phương thức tự sở hữu file Stream, đóng hoàn chỉnh trước khi trả `long`. Caller không cần biết resource bên trong.

### Mẫu B — Trả Stream và chuyển trách nhiệm đóng cho caller

~~~java
static Stream<String> openNonBlankLines(Path path)
        throws IOException {
    return Files.lines(path, StandardCharsets.UTF_8)
            .filter(line -> !line.isBlank());
}
~~~

Caller **phải** dùng TWR:

~~~java
try (Stream<String> lines = openNonBlankLines(path)) {
    lines.forEach(System.out::println);
}
~~~

Phương thức `openNonBlankLines` trả một Stream vẫn sống và **chuyển ownership** cho bên gọi. Vì điều này dễ bị sử dụng sai, API nên có tên/hướng dẫn rõ ràng rằng Stream phải được đóng. Không viết wrapper trả Stream mà đóng trước ở trong factory.

**Lưu ý:** Nếu muốn trả một kết quả dễ quản lý hơn, hãy cân nhắc trả danh sách, số lượng hoặc kết quả tính toán thay vì buộc bên gọi nhớ đóng Stream. Nhưng nếu dùng `toList()` cho file rất lớn, phải cân nhắc bộ nhớ.

## 6. filter/map/limit không thay thế close()

Những ví dụ sau **vẫn cần TWR**:

~~~java
// Đọc một vài dòng:
try (Stream<String> lines = Files.lines(path)) {
    lines.limit(5).forEach(System.out::println);
}
~~~

~~~java
// Tìm dòng đầu phù hợp:
try (Stream<String> lines = Files.lines(path)) {
    boolean found = lines.anyMatch(s -> s.contains("ERROR"));
    System.out.println(found);
}
~~~

~~~java
// Bỏ qua dòng đầu rồi xử lý phần còn lại:
try (Stream<String> lines = Files.lines(path)) {
    lines.skip(1).forEach(System.out::println);
}
~~~

- `limit(5)` có thể dừng đọc sau năm phần tử đầu.
- `anyMatch` có thể dừng ngay khi gặp phần tử thỏa mãn.
- `skip(1)` bỏ qua dòng đầu về mặt pipeline nhưng vẫn phải đi qua nguồn để xác định phần tử.
- **Tất cả đều phải close**, dù terminal operation có đọc hết hay không.

Đừng nghĩ rằng **`findFirst()` tự đóng file vì nó tìm thấy kết quả**. Việc pipeline hoàn tất và việc giải phóng I/O là hai mối quan tâm khác nhau.

### Derived Stream cũng dùng chung tài nguyên gốc

~~~java
try (Stream<String> errors = Files.lines(path)
        .filter(line -> line.startsWith("ERROR"))) {
    errors.forEach(System.out::println);
}
~~~

Đóng Stream thu được từ pipeline sẽ kích hoạt cơ chế đóng liên quan đến Stream gốc từ Files.lines(). Không cần và không nên mở/đóng lại file chỉ vì đã dùng một intermediate operation.

Tuy nhiên, Stream vẫn là **một lần dùng**: không thể `count()` rồi `findFirst()` trên cùng biến `errors`. Phải xây một pipeline tổng hợp hoặc mở Stream mới.

## 7. So với Files.readAllLines và Files.readString

**`Files.lines`**, **`Files.readAllLines`** và **`Files.readString`** đều phục vụ việc đọc text, nhưng cách dùng khác nhau:

| API | Kết quả | Thời điểm lấy dữ liệu | Caller có phải đóng Stream? | Phù hợp |
| --- | --- | --- | --- | --- |
| `Files.lines(path)` | `Stream<String>` | Đọc theo tiến trình tiêu thụ pipeline | **Có** | Lọc/đếm/xử lý file lớn hoặc dừng sớm |
| `Files.readAllLines(path)` | `List<String>` | Đọc toàn bộ dòng trước khi trả về | Không có Stream file cần caller đóng | File vừa/nhỏ cần danh sách |
| `Files.readString(path)` | `String` | Đọc toàn bộ file text | Không có Stream file cần caller đóng | File text đủ nhỏ, giữ nguyên nội dung gồm xuống dòng |
| `Files.newBufferedReader(path)` | `BufferedReader` | Từng thao tác đọc | **Có**, đóng Reader | Cần kiểm soát đọc dòng hoặc parse thủ công |

Một số hệ quả:

- **`readAllLines`** chứa tất cả dòng trong bộ nhớ khi trả về; với file lớn có thể tốn rất nhiều RAM. API tự quản lý tài nguyên bên trong lời gọi, caller không nhận Stream cần đóng.
- **`readString`** đọc toàn bộ text thành một String và giữ cả dấu ngắt dòng; dùng nó nếu cần nguyên nội dung thay vì từng dòng.
- **`Files.lines`** có thể tiết kiệm bộ nhớ nếu phép xử lý chỉ giữ ít trạng thái (đếm, tìm dòng đầu, ghi kết quả tới một sink). Nhưng `Files.lines(...).toList()` vẫn **tạo danh sách chứa tất cả phần tử được thu thập**: laziness của nguồn không khiến danh sách kết quả vô hạn trở nên rẻ.
- **`BufferedReader`** hỗ trợ vòng lặp `readLine()` và kiểm soát thủ công; bản thân Reader là tài nguyên phải đóng bằng TWR.

Cả ba API Files đọc văn bản nêu trên đều có overload nhận charset; overload phổ biến **không chỉ định charset sử dụng UTF-8** trong Java SE 17. Khi làm việc với định dạng ngoài hoặc file cũ, nên chỉ định rõ `StandardCharsets.UTF_8` hoặc charset được quy ước thay vì suy đoán.

## 8. Chương trình Java 17+: gộp kết quả mà không giữ toàn bộ dòng

Để thống kê số lượng dòng theo mức log, Stream có thể duyệt file và chỉ giữ các **bộ đếm** cho các nhóm, thay vì lưu cả danh sách dòng. Tạo file **LineSummaryDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LineSummaryDemo {
    static Map<String, Long> countLevels(Path file) throws IOException {
        try (Stream<String> lines =
                Files.lines(file, StandardCharsets.UTF_8)) {
            return lines
                    .filter(s -> !s.isBlank())
                    .map(s -> s.split(":", 2)[0])
                    .collect(Collectors.groupingBy(
                            Function.identity(),
                            TreeMap::new,
                            Collectors.counting()));
        }
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("summary-", ".log");
        try {
            Files.writeString(file,
                    "INFO:started\n"
                    + "WARN:slow\n"
                    + "INFO:done\n",
                    StandardCharsets.UTF_8);

            System.out.println(countLevels(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 LineSummaryDemo.java
java LineSummaryDemo
~~~

Stdout:

~~~text
{INFO=2, WARN=1}
~~~

Trong ví dụ này:

1. `Files.lines` tạo Stream đọc file.
2. `filter` bỏ dòng trắng; `map` lấy phần trước dấu hai chấm (quy ước log demo).
3. `Collectors.groupingBy` duy trì bộ đếm theo level; `TreeMap` giúp thứ tự khóa ổn định khi in ra.
4. `collect` tiêu thụ toàn bộ dòng cần xử lý. Ngay khi khối try kết thúc, Stream được đóng.
5. Hàm trả về **Map đã tính xong**, không còn phụ thuộc một Stream file đang mở.

Với số loại log ít, dữ liệu giữ lại không tỷ lệ trực tiếp với tổng số dòng. Tuy nhiên, nếu khóa nhóm không giới hạn (ví dụ nhóm theo từng dòng khác nhau), bản thân Map vẫn có thể rất lớn. **Laziness không tự bảo đảm sử dụng bộ nhớ hằng số**; điều đó phụ thuộc terminal operation và dữ liệu được lưu.

Chương trình dùng `String.split(":", 2)` như parser tối giản theo format demo. Với log sản xuất có escape, timestamp và cấu trúc phức tạp, dùng parser đúng đặc tả.

## 9. IOException khi mở Stream và UncheckedIOException khi duyệt

Có **hai giai đoạn lỗi I/O** khác nhau:

### Giai đoạn mở nguồn

~~~java
Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8);
~~~

Nếu file không mở được, lời gọi này có thể ném **`IOException`** (checked exception). Ví dụ đường dẫn không tồn tại hoặc không có quyền đọc. TWR chưa nhận một Stream được tạo thành công nên không có Stream ấy để close; API mở file có trách nhiệm tự dọn phần nội bộ nếu khởi tạo thất bại.

### Giai đoạn đang tiêu thụ

Sau khi `Files.lines()` đã trả về Stream, lỗi I/O hoặc lỗi giải mã phát sinh trong quá trình duyệt có thể được **bọc thành `UncheckedIOException`** (runtime exception). Nguyên nhân gốc có thể lấy qua `getCause()`.

Ví dụ xử lý rõ hai nhóm lỗi:

~~~java
// Mảnh code: path là Path, UTF_8 được import
try (Stream<String> lines = Files.lines(
        path, StandardCharsets.UTF_8)) {
    long count = lines.filter(s -> !s.isBlank()).count();
    System.out.println(count);
} catch (java.io.UncheckedIOException duringRead) {
    System.err.println("Read failed: " + duringRead.getCause());
} catch (IOException duringOpen) {
    System.err.println("Open failed: " + duringOpen);
}
~~~

Đây là mẫu minh họa chẩn đoán. Khi phát sinh nhiều lỗi cùng lúc, hãy kiểm tra thêm **suppressed exceptions**, bởi close() trong TWR có thể xảy ra trong lúc truyền một exception khác. Không nên nuốt lỗi đọc rồi báo tác vụ thành công; nếu file chưa duyệt xong vì I/O lỗi thì kết quả thống kê có thể không đầy đủ.

**Charset sai** cũng có thể khiến văn bản bị lỗi hoặc bị từ chối trong lúc đọc. `Files.lines(path, charset)` là giải mã byte theo charset được chỉ định; không tự phát hiện đúng charset gốc của mọi file. Nếu nội dung là nhị phân, dùng InputStream hoặc Files API cho byte thay vì cố giải mã thành dòng text.

## 10. Line terminator và các tình huống biên

Files.lines() tạo Stream của **các dòng văn bản**, không phải những chuỗi còn nguyên dấu kết thúc dòng. Các dấu tách dòng được hỗ trợ theo hợp đồng gồm **LF** (`\n`), **CR** (`\r`) và **CRLF** (`\r\n`). Dấu kết thúc dòng bị bỏ khỏi String đại diện cho dòng.

Ví dụ:

~~~text
Byte nội dung ASCII: A 
 B 
 
 C
Các dòng: ["A", "B", "", "C"]
~~~

Vì vậy:

- Một dòng **trống ở giữa file** là String rỗng hợp lệ, không phải EOF.
- Một file rỗng cho Stream không có dòng nào.
- Một file kết thúc bằng đúng một line terminator sau nội dung cuối **không nhất thiết tạo thêm một dòng rỗng cuối**; đừng suy count từ số ký tự xuống dòng một cách máy móc.
- Các dòng rất dài vẫn có thể khiến chương trình phải giữ một String dòng lớn trong bộ nhớ; “đọc lười” không loại bỏ giới hạn bộ nhớ cho từng dòng.
- Nếu file đang bị ghi/thay đổi đồng thời, **không giả định Files.lines cung cấp snapshot nhất quán**. Với tác vụ yêu cầu tính nhất quán, cần cơ chế snapshot/khóa hoặc thiết kế nguồn dữ liệu phù hợp.

**Phân biệt `String.lines()` với `Files.lines()`:** `String.lines()` tạo Stream từ một String đã ở trong bộ nhớ, không tự giữ file descriptor cần đóng. `Files.lines()` tạo Stream gắn với file bên ngoài; phải đóng để giải phóng tài nguyên. Hai API cùng trả Stream<String> nhưng **quyền sở hữu tài nguyên** khác nhau.

## 11. BufferedReader.lines() và vòng đời Reader

Một lựa chọn khác là mở `BufferedReader` rồi lấy Stream từ **`reader.lines()`**:

~~~java
// Mảnh code: path là Path, UTF_8 đã import.
try (java.io.BufferedReader reader =
        Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    reader.lines()
            .filter(s -> s.startsWith("ERROR"))
            .forEach(System.out::println);
}
~~~

Ở mẫu này **đối tượng cần đóng chính là BufferedReader** vì phương thức đã mở Reader và giữ ownership của nó. Đừng suy rằng chỉ gọi một terminal operation trên `reader.lines()` sẽ tự đóng Reader. Đặt Reader trong TWR bảo đảm close của Reader chạy khi rời scope.

Hai cách đều hợp lệ nhưng phải chọn đối tượng sở hữu rõ ràng:

- `Files.lines(path)`: tài nguyên do Stream từ Files.lines() quản lý; **đóng Stream**.
- `Files.newBufferedReader(path)` rồi `reader.lines()`: ứng dụng đã mở Reader; **đóng Reader**.
- Nếu bọc các lớp khác hoặc trả Stream ra ngoài, định nghĩa rõ tài nguyên gốc sẽ được đóng khi nào và bởi ai.

Không để Stream dựa trên Reader thoát khỏi scope nơi Reader đã bị đóng, vì lúc caller duyệt thì nguồn dữ liệu không còn sống.

## 12. Dùng parallel() có làm Files.lines luôn nhanh hơn không?

Stream hỗ trợ **`parallel()`**, nhưng thao tác I/O từ file không tự nhiên tăng tốc chỉ nhờ nhiều thread:

~~~java
// Mảnh code: có Path path và xử lý IOException.
try (Stream<String> lines = Files.lines(path)) {
    long n = lines.parallel()
            .filter(s -> s.contains("ERROR"))
            .count();
}
~~~

Cần cân nhắc:

- Chi phí đọc file và giải mã ký tự có thể là nút cổ chai; parallel không bảo đảm giảm thời gian.
- Khả năng chia công việc và cân bằng tải phụ thuộc charset và triển khai spliterator. Java có tối ưu cho một số charset có khả năng chia theo ranh giới dòng, nhưng không được suy rằng **mọi charset hay nguồn file đều split hiệu quả**.
- Các thao tác có side effect như ghi chung một file, tăng biến đếm mutable không đồng bộ hoặc in stdout từ nhiều thread có thể có vấn đề thứ tự và race condition.
- Với `findAny()` trong parallel, phần tử được tìm không nhất thiết là phần tử đầu tiên theo thứ tự file; nếu yêu cầu dòng đầu, hãy dùng `findFirst()` và hiểu chi phí duy trì thứ tự.

Hãy đo hiệu năng với dữ liệu thực trước khi chọn parallel. **Việc cần đóng Stream bằng try-with-resources không thay đổi khi pipeline chạy parallel.**

## 13. Quản lý tài nguyên trong ứng dụng dài hạn

Một chương trình CLI chạy xong ngay có thể khiến leak khó nhận ra. Nhưng trong server, worker, service hoặc tác vụ quét nhiều file:

~~~text
for each request:
    Files.lines(file)      -> mở tài nguyên
    findFirst()            -> đọc một phần
    không close()          -> tài nguyên có thể tích lũy
~~~

Hệ quả có thể là quá nhiều file descriptor mở, hạn chế mở file mới, tiêu tốn tài nguyên hệ điều hành, hoặc khó thay đổi/xóa file trên một số nền tảng. Vì vậy cần coi **vòng đời của Stream** là thành phần thiết kế, không phải bước tối ưu tùy chọn.

Một quy trình đúng:

1. Xác định file và charset của dữ liệu văn bản.
2. Mở Stream trong **try-with-resources** tại scope nhỏ nhất có thể.
3. Xây pipeline không có side effect khó kiểm soát; chọn terminal operation theo yêu cầu.
4. Không sử dụng lại Stream sau terminal operation và không trả Stream đã đóng.
5. Để TWR xử lý close ở mọi đường thoát; log/propagate lỗi mở, lỗi duyệt và lỗi cleanup theo chính sách.
6. Nếu chỉ cần thống kê, **trả kết quả đã tính** thay vì Stream sống.
7. Với số lượng file lớn, mở và đóng từng file theo scope, không giữ đồng thời hàng trăm Stream không cần thiết.

**Điểm nhấn:** không có “bảo đảm tự đóng” chỉ vì code đã gọi `count()`, `forEach()`, `findFirst()` hoặc `toList()`.

## 14. Lỗi phổ biến và cách sửa

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| `Files.lines(path).count()` không có TWR | Tài nguyên I/O có thể không được đóng kịp | TWR quanh Files.lines |
| Nghĩ Files.lines chỉ mở file khi terminal chạy | Bỏ qua lỗi mở và ownership của Stream | Hiểu file được mở khi Files.lines trả Stream |
| `findFirst()` rồi không close | Đọc dừng sớm nhưng file vẫn được giữ | Close Stream dù short-circuit |
| `limit(10)` nghĩa là tự release tài nguyên | Nhầm giới hạn phần tử với close | TWR độc lập với limit |
| Trả Stream ra khỏi TWR đã kết thúc | Caller nhận pipeline trên nguồn đã đóng | Tính kết quả tại chỗ hoặc chuyển ownership rõ |
| Dùng lại Stream sau `count()` | Có thể ném IllegalStateException | Mở Stream mới cho lượt duyệt mới |
| Dùng `toList()` cho file rất lớn và nghĩ không tốn RAM | List vẫn giữ nhiều dòng | Streaming aggregate/sink phù hợp |
| Coi `IOException` chỉ xảy ra lúc Files.lines() | Lỗi duyệt có thể thành UncheckedIOException | Bắt và truyền lỗi đúng giai đoạn |
| Lấy reader.lines() rồi quên đóng reader | Rò rỉ Reader gốc | TWR trên BufferedReader |
| Đọc file theo charset không đúng | Lỗi decoding/garbled text | Chỉ định charset phù hợp |
| Dùng Stream<String> để giữ byte binary | Có thể thay đổi byte do giải mã | Dùng byte stream |
| Giả định parallel luôn nhanh | Tăng overhead, race/khác thứ tự | Đo benchmark và giữ xử lý thread-safe |
| Thay đổi file trong khi Stream đang duyệt | Kết quả có thể không nhất quán | Dùng snapshot/đồng bộ khi cần |

## 15. Câu hỏi tự kiểm tra

**Câu 1.** `Files.lines(path)` có chờ `count()` mới mở file không?  
**Đáp án:** Không. Files.lines tạo Stream gắn với file khi gọi. Pipeline đọc các dòng theo nhu cầu khi được tiêu thụ, nhưng việc mở tài nguyên không phải chờ đến terminal operation.

**Câu 2.** Nếu dùng `findFirst()` đọc một dòng đầu, có cần close không?  
**Đáp án:** Có. Short-circuit chỉ dừng việc tiêu thụ phần tử, không tự giải phóng file.

**Câu 3.** `Files.lines(path).toList()` có luôn tiết kiệm RAM hơn `Files.readAllLines(path)` không?  
**Đáp án:** Không. Cả hai đều có thể tạo danh sách giữ toàn bộ dòng; nguồn lazy không đồng nghĩa kết quả đã collect là lazy.

**Câu 4.** Nếu một phương thức trả `Stream<String>` tạo từ Files.lines, ai phải đóng?  
**Đáp án:** Nếu muốn Stream còn dùng được sau khi trả về, caller phải nhận ownership và đóng bằng TWR hoặc close đúng lúc. API phải nói rõ contract đó.

**Câu 5.** Lỗi I/O khi đang duyệt sau khi Files.lines trả Stream có thể xuất hiện dưới dạng gì?  
**Đáp án:** `UncheckedIOException` bao bọc `IOException` gốc; lỗi mở file có thể là `IOException` ngay lúc Files.lines().

**Câu 6.** Hai lần gọi `count()` rồi `findFirst()` trên cùng một Stream có đúng không?  
**Đáp án:** Không. Một Stream chỉ được dùng cho một pipeline/terminal operation; phải mở Stream khác hoặc kết hợp yêu cầu trong một traversal.

**Câu 7.** Dùng `BufferedReader.lines()` trong một phương thức tự mở reader thì cần đóng đối tượng nào?  
**Đáp án:** `BufferedReader` mà phương thức mở, nên đặt trong TWR.

**Câu 8.** Vì sao File Stream lazy không bảo đảm bộ nhớ không tăng?  
**Đáp án:** Một dòng có thể rất dài và terminal operation như `toList()`, `groupingBy()` có thể giữ rất nhiều dữ liệu trong RAM.

## 16. Tài liệu Java SE 17

- [Files.lines(Path)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#lines(java.nio.file.Path)): Stream dòng văn bản, lazy population, vòng đời file và close.
- [Files.lines(Path, Charset)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#lines(java.nio.file.Path,java.nio.charset.Charset)): lựa chọn charset và ngoại lệ.
- [BaseStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/BaseStream.html): close, onClose và đặc tính dùng một lần.
- [BufferedReader.lines()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html#lines()): Stream dòng từ một Reader.
- [Files.readAllLines](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#readAllLines(java.nio.file.Path)): đọc tất cả dòng vào List.

**Ghi nhớ:** `Files.lines()` cho phép xây pipeline Stream đọc các dòng theo nhu cầu, nhưng **mở tài nguyên file khi tạo Stream**. Mọi đường đi — đọc hết, dừng sớm, return hay exception — đều cần đóng Stream đúng lúc. Mẫu mặc định an toàn là **`try (Stream<String> lines = Files.lines(path, charset)) { ... }`**, và khi muốn chuyển Stream ra ngoài, phải chuyển luôn trách nhiệm đóng cho caller.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="files-read-write-005">newBufferedReader và newBufferedWriter</a>

<details>
<summary>Xem chi tiết</summary>

# Files.newBufferedReader và Files.newBufferedWriter trong Java

## 1. Tổng quan: đọc và ghi văn bản qua Files API

Khi xử lý tệp văn bản trong Java 17+, hai phương thức rất hữu ích của lớp `java.nio.file.Files` là:

- **`Files.newBufferedReader(Path, Charset)`**: mở tệp để **đọc văn bản**, tạo `BufferedReader` với bộ đệm ký tự.
- **`Files.newBufferedWriter(Path, Charset, OpenOption...)`**: mở/tạo tệp để **ghi văn bản**, tạo `BufferedWriter` với bộ đệm ký tự.

Chúng nối thế giới tệp (byte) với thế giới chuỗi và ký tự Java, đồng thời sử dụng `Path` của NIO.2. Khi văn bản được đọc từ ổ đĩa, dữ liệu byte phải được **giải mã** (*decode*) theo một `Charset`; khi ghi, ký tự Java được **mã hóa** (*encode*) thành byte.

~~~text
ĐỌC
File UTF-8 (byte) ─► Files.newBufferedReader(path, UTF_8)
                                 │ giải mã + bộ đệm
                                 ▼
                          BufferedReader
                         ├─ readLine() → String
                         └─ read(char[]) → khối char

GHI
String / char[] ─► BufferedWriter
                     │ write(), newLine(), flush(), close()
                     ▼ mã hóa + bộ đệm
                 File UTF-8 (byte)
                 Files.newBufferedWriter(path, UTF_8, ...)
~~~

Đây là **I/O văn bản**, không phải lựa chọn mặc định cho ảnh, file ZIP hay payload nhị phân. Nếu cần bảo toàn chính xác các byte không có ngữ nghĩa văn bản, sử dụng `Files.newInputStream`, `Files.newOutputStream` hoặc NIO byte API.

### Mục tiêu học tập

Sau bài này, bạn có thể:

1. Tạo `BufferedReader` / `BufferedWriter` từ `Path`, chỉ định charset rõ ràng và đóng tài nguyên đúng vòng đời.
2. Đọc từng dòng bằng `readLine()` và phân biệt EOF với dòng rỗng.
3. Đọc/ghi từng khối `char[]` bằng số ký tự thực tế, tránh lỗi ghi thừa phần buffer.
4. Hiểu `newLine()`, `flush()`, `close()` và ranh giới giữa “đã ghi vào bộ đệm” với “đã lưu bền vững”.
5. Chọn `StandardOpenOption` để tạo mới, ghi đè, nối tiếp hoặc ngăn ghi đè tệp đã tồn tại.
6. Nhận diện lỗi tệp, charset, đọc/ghi và xử lý ngoại lệ theo đúng giai đoạn.
7. Xác định khi nào nên dùng `Files.readString` / `Files.writeString`, `Files.lines` hoặc `newBufferedReader/newBufferedWriter`.

## 2. Các kiểu dữ liệu và thành phần liên quan

| API/kiểu | Vai trò | Ghi nhớ |
| --- | --- | --- |
| `Path` | Biểu diễn đường dẫn trên một `FileSystem` | `Path.of(...)` không tự tạo hoặc mở tệp |
| `Files` | Các thao tác NIO.2 với tệp/thư mục | Nhiều thao tác ném `IOException` |
| `Charset` | Quy tắc giải mã/mã hóa giữa byte và ký tự | Chọn theo định dạng tệp, thường là UTF-8 |
| `StandardCharsets.UTF_8` | Charset UTF-8 dựng sẵn | Không phụ thuộc charset mặc định môi trường |
| `BufferedReader` | Reader có bộ đệm ký tự | `readLine()`, `read(char[])`, `lines()` |
| `BufferedWriter` | Writer có bộ đệm ký tự | `write(...)`, `newLine()`, `flush()` |
| `StandardOpenOption` | Quyết định cách mở tệp để ghi | `CREATE`, `CREATE_NEW`, `APPEND`, `TRUNCATE_EXISTING`... |
| `IOException` | Báo lỗi nhập/xuất (checked exception) | Xử lý hoặc khai báo `throws` |
| `try-with-resources` | Đóng tài nguyên tự động | Đóng cả khi thân khối ném ngoại lệ |

Ví dụ khung chương trình:

~~~java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
~~~

Các import này đủ cho những thao tác đọc/ghi văn bản đơn giản. Khi dùng `StandardOpenOption`, cần import từ `java.nio.file` hoặc viết tên đầy đủ.

**Lưu ý:** `Path` là đối tượng mô tả đường dẫn, không cần gọi `close()`. Tài nguyên cần đóng là `BufferedReader` / `BufferedWriter` được mở từ tệp.

## 3. Files.newBufferedReader: mở tệp văn bản để đọc

### 3.1. Cú pháp và ý nghĩa

~~~java
BufferedReader reader =
        Files.newBufferedReader(path, StandardCharsets.UTF_8);
~~~

Đây là **đoạn mã minh họa**; biến `path` có kiểu `Path`, phương thức bao ngoài phải xử lý `IOException`. `Files.newBufferedReader` mở tệp để đọc và trả `BufferedReader`. Byte trong tệp được giải mã bằng UTF-8 để chương trình thao tác trên ký tự Java.

Ở Java 17 còn có dạng:

~~~java
BufferedReader reader = Files.newBufferedReader(path);
~~~

Dạng không truyền charset sử dụng **UTF-8** theo hợp đồng `Files.newBufferedReader(Path)`, nhưng việc truyền `StandardCharsets.UTF_8` tường minh làm ý định dễ nhìn, đặc biệt khi định dạng đầu vào có charset được quy định.

Không nhầm với `new FileReader(file)` của API truyền thống: lựa chọn charset và cách biểu đạt đường dẫn có thể khác tùy constructor, phiên bản Java và ngữ cảnh. Khi thiết kế mới, sử dụng `Path` và charset rõ ràng giúp giảm phụ thuộc môi trường.

### 3.2. Reader đọc ký tự, không đọc byte thô

Nếu tệp chứa byte UTF-8 của chữ `"Hà Nội"`, `BufferedReader` cung cấp các đơn vị `char` đã giải mã. Số byte trên đĩa có thể lớn hơn `String.length()` do chữ có dấu hoặc emoji.

`Reader.read()` trả về `int`: đơn vị `char` không âm hoặc `-1` ở EOF. `Reader.read(char[])` trả về số **đơn vị ký tự thực đọc** (hoặc `-1` ở cuối), không phải số byte trên đĩa. Vì vậy không dùng kết quả `read(char[])` để suy trực tiếp kích thước UTF-8 của tệp.

### 3.3. try-with-resources: cách sử dụng thường gặp

~~~java
try (BufferedReader reader =
        Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý line.
    }
}
~~~

Đoạn trên là khối minh họa với `Path path` có sẵn. `BufferedReader` triển khai `Closeable`, nên `try-with-resources` gọi `close()` ở cuối khối kể cả khi xảy ra ngoại lệ. Không dùng lại `reader` sau khi đã đóng.

Đừng dựa vào garbage collector để đóng tài nguyên tệp. `Reader` có thể giữ descriptor/handle của hệ điều hành; sử dụng `try-with-resources` giúp vòng đời rõ ràng và tránh rò rỉ.

## 4. BufferedReader.readLine(): đọc theo dòng

`readLine()` trả một `String` chứa **nội dung dòng** nhưng **không bao gồm dấu kết thúc dòng**, hoặc trả `null` khi đã hết dữ liệu. Java nhận diện một dòng kết thúc bởi `\n`, `\r` hoặc `\r\n` theo hợp đồng `BufferedReader.readLine()`.

Điều này có các hệ quả:

- **Dòng trống hợp lệ** được biểu diễn bằng `""` (chuỗi rỗng).
- **Kết thúc tệp** được biểu diễn bằng `null`.
- `readLine()` **loại bỏ dấu xuống dòng**: không thể dựa riêng vào kết quả để tái tạo chính xác kiểu kết thúc dòng nguyên bản.
- Một tệp kết thúc bằng `\n` sau dòng có nội dung **không nhất thiết sinh ra một dòng rỗng mới** sau dấu kết thúc cuối cùng.

### 4.1. Ví dụ độc lập với CRLF, dòng trắng và LF

Lưu chương trình **ReadLinesDemo.java**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReadLinesDemo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("reader-lines-", ".txt");

        try {
            Files.writeString(path, "first\r\n\r\nlast\n",
                    StandardCharsets.UTF_8);

            try (BufferedReader reader =
                    Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    System.out.println(lineNumber + ":[" + line + "]");
                }
                System.out.println("Lines: " + lineNumber);
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac -encoding UTF-8 ReadLinesDemo.java
java ReadLinesDemo
~~~

Kết quả:

~~~text
1:[first]
2:[]
3:[last]
Lines: 3
~~~

Giải thích chính xác: chuỗi nguồn có `first` + CRLF, một dòng rỗng + CRLF, và `last` + LF. `readLine()` lần lượt trả `"first"`, `""`, `"last"`, rồi `null`. Nếu viết `while ((line = reader.readLine()) != null)`, dòng rỗng vẫn được xử lý đúng. Ngược lại, `while (!line.isEmpty())` sẽ sai cả về ngữ nghĩa lẫn điều kiện EOF.

Vì tên tệp tạm được hệ thống chọn, ví dụ chỉ in nội dung đã đọc, không in đường dẫn ngẫu nhiên, nên output ổn định.

### 4.2. Không thể bảo toàn nguyên trạng xuống dòng bằng readLine/newLine

Giả sử tệp gốc chứa nhiều kiểu dấu kết thúc:

~~~text
alpha\r\nbeta\ngamma\r
~~~

Ký hiệu trên biểu diễn: `alpha` kết thúc bằng CRLF, `beta` bằng LF và `gamma` bằng CR. Nếu đọc bằng `readLine()` rồi ghi lại qua `writer.newLine()`, chương trình sẽ **thay tất cả dấu xuống dòng bằng quy tắc của môi trường đang chạy**, thay vì giữ từng kiểu cũ.

Điều này hợp lý nếu mục đích là **chuẩn hóa xuống dòng**; nhưng sẽ không đúng nếu mục tiêu là sao chép chính xác nội dung ký tự. Khi muốn bảo toàn từng `\r` và `\n`, hãy dùng `read(char[])` và `write(char[], off, count)` như phần tiếp theo.

### 4.3. Khi nào readLine không phù hợp?

`readLine()` thường thích hợp cho file log, danh sách, cấu hình văn bản dòng đơn, hoặc dữ liệu mà dấu xuống dòng chỉ phân chia bản ghi. Nó kém phù hợp khi:

- Cần giữ nguyên chính xác kiểu kết thúc dòng.
- Một bản ghi có thể trải qua nhiều dòng theo cú pháp phức tạp, ví dụ CSV có trường được trích dẫn chứa newline.
- Dòng có thể cực dài đến mức cần giới hạn bộ nhớ.
- Dữ liệu không thực sự là văn bản.

Lưu ý rằng `readLine()` có thể phải tích lũy một dòng rất dài trước khi trả về `String`. Với đầu vào không tin cậy, cần cân nhắc giới hạn độ dài dòng hoặc thiết kế parser xử lý có giới hạn bộ nhớ.

## 5. Đọc theo khối char[]: bảo toàn mọi ký tự và kiểm soát RAM

Ngoài `readLine()`, `BufferedReader` còn kế thừa `Reader.read(char[])` và `read(char[], off, len)`. Đây là phương án khi muốn chuyển văn bản theo từng khối, không phụ thuộc ranh giới dòng.

~~~java
char[] buffer = new char[4096];
int count;

while ((count = reader.read(buffer)) != -1) {
    writer.write(buffer, 0, count);
}
~~~

Đây là đoạn minh họa có `BufferedReader reader` và `BufferedWriter writer` đã mở. `read` có thể trả số dương **nhỏ hơn** kích thước buffer; phải ghi đúng `count` đơn vị ký tự hợp lệ. Đặc biệt trong lần đọc cuối, các phần tử còn lại của mảng có thể chứa dữ liệu từ lượt trước.

Không dùng:

~~~java
while ((count = reader.read(buffer)) != -1) {
    writer.write(buffer); // Sai: có thể ghi ký tự cũ ở cuối mảng.
}
~~~

Vì `write(buffer)` ghi toàn bộ mảng, không chỉ đoạn mới đọc. Cú pháp đúng là `writer.write(buffer, 0, count)`. Hai tham số số nguyên cuối cùng có nghĩa **offset** và **length**, không phải vị trí đầu và vị trí kết thúc.

### 5.1. Đọc theo char không phải xử lý theo code point

Java `char` là một đơn vị mã UTF-16. Ký tự ngoài BMP như nhiều emoji được biểu diễn bởi một cặp surrogate (hai `char`). Một khối `char[]` có thể dừng giữa cặp đó.

Nếu chỉ sao chép tuần tự các đơn vị `char` mà không biến đổi, Reader và Writer cùng một chuỗi liên tục vẫn có thể giữ nội dung đúng. Nhưng không nên cắt riêng từng chunk, đảo chuỗi hoặc tự phân tích “mỗi char là một ký tự hiển thị” mà bỏ qua cặp surrogate. Với xử lý Unicode nâng cao, cân nhắc các API code point và giữ trạng thái giữa các khối.

### 5.2. Reader/Writer không dành cho bảo toàn byte nhị phân

Đọc UTF-8 bằng `newBufferedReader` rồi ghi UTF-8 bằng `newBufferedWriter` thực hiện giải mã/mã hóa. Với văn bản UTF-8 hợp lệ, có thể giữ nguyên nội dung Unicode. Nhưng đây **không phải cách sao chép byte bất kỳ**: byte đầu vào có thể không hợp lệ với charset, hoặc kết quả có thể phụ thuộc cách chuyển mã nếu hai charset khác nhau.

Nếu phải sao chép ảnh, archive, file đã nén hay bất kỳ payload nhị phân, dùng `Files.copy` hoặc API byte. Không tạo `String` từ dữ liệu nhị phân không có encoding văn bản.

## 6. Files.newBufferedWriter: mở tệp để ghi văn bản

### 6.1. Cú pháp cơ bản

~~~java
BufferedWriter writer =
        Files.newBufferedWriter(path, StandardCharsets.UTF_8);
~~~

Đây là đoạn minh họa: `Path path` có sẵn và phương thức phải xử lý `IOException`. `newBufferedWriter` tạo writer mã hóa văn bản thành byte theo charset được chọn và ghi ra tệp với bộ đệm.

Một chương trình thường dùng:

~~~java
try (BufferedWriter writer =
        Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
    writer.write("Xin chào Java!");
    writer.newLine();
    writer.write("Dòng thứ hai");
}
~~~

Đây là khối minh họa. `newLine()` ghi dấu kết thúc dòng theo nền tảng, còn `write` không tự thêm newline. `try-with-resources` bảo đảm writer được đóng khi kết thúc khối, và `close()` đưa dữ liệu còn đệm xuống đích bên dưới theo hợp đồng của lớp.

### 6.2. Mặc định khi không truyền OpenOption

Với overload nhận `OpenOption...`, nếu **không cung cấp option**, hành vi ghi mặc định tương đương:

- `StandardOpenOption.CREATE`: tạo tệp nếu nó chưa tồn tại.
- `StandardOpenOption.TRUNCATE_EXISTING`: nếu tệp đã tồn tại, **cắt ngắn nội dung cũ** trước khi ghi.
- `StandardOpenOption.WRITE`: mở để ghi.

Đây là một trong những điều quan trọng nhất cần ghi nhớ: **`Files.newBufferedWriter(path, UTF_8)` không có nghĩa “append vào cuối tệp đang có”**. Nó có thể xóa toàn bộ nội dung cũ vì mở trong chế độ truncate. Trước khi mở, hãy xác định chính sách: tạo mới duy nhất, ghi đè, hay nối thêm.

### 6.3. Chỉ định OpenOption rõ ràng

Một số lựa chọn tiêu biểu từ `StandardOpenOption`:

| Option | Ý nghĩa | Tình huống |
| --- | --- | --- |
| `CREATE` | Tạo tệp nếu chưa có | Dự kiến cho phép tạo mới hoặc dùng tệp cũ |
| `CREATE_NEW` | Chỉ tạo tệp mới, thất bại nếu đã tồn tại | Tránh vô ý ghi đè |
| `TRUNCATE_EXISTING` | Khi mở để ghi, rút kích thước tệp đã có về 0 | Thay toàn bộ nội dung |
| `APPEND` | Ghi thêm ở cuối tệp | Log/bản ghi nối tiếp |
| `WRITE` | Mở để ghi | Thường đi cùng mode được chỉ định |
| `SYNC` hoặc `DSYNC` | Yêu cầu đồng bộ theo hợp đồng I/O/đích | Khi yêu cầu durability cao hơn, vẫn cần xem giới hạn filesystem |

Khi cung cấp options, **không tự giả định bộ ba option mặc định vẫn được thêm vào**. Hãy viết đầy đủ các điều kiện cần thiết theo mục tiêu. Một số tổ hợp không hợp lệ, chẳng hạn `APPEND` cùng `TRUNCATE_EXISTING`: hai yêu cầu trái nhau.

Ví dụ ghi nối tiếp:

~~~java
try (BufferedWriter writer = Files.newBufferedWriter(
        path,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND)) {
    writer.write("bổ sung");
}
~~~

Đoạn minh họa yêu cầu import `java.nio.file.StandardOpenOption`. `CREATE` cho phép tạo tệp nếu chưa có; `APPEND` yêu cầu nối cuối thay vì xóa nội dung cũ. Nếu cần thêm một dòng, nhớ tự ghi `newLine()` theo vị trí mong muốn — `APPEND` **không tự chèn newline**.

Ví dụ tạo mới mà không ghi đè:

~~~java
try (BufferedWriter writer = Files.newBufferedWriter(
        path,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE)) {
    writer.write("Dữ liệu mới");
}
~~~

Nếu tệp đã tồn tại, có thể nhận `FileAlreadyExistsException`. Không dùng mô hình `if (!Files.exists(path))` rồi mới mở để ngăn ghi đè vì có điều kiện đua (*TOCTOU*); `CREATE_NEW` là cách đặt yêu cầu tạo mới trong thao tác mở.

## 7. Ví dụ Java 17: mặc định ghi đè và chế độ APPEND

Lưu **WriteModesDemo.java**:

~~~java
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class WriteModesDemo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("writer-modes-", ".txt");

        try {
            try (BufferedWriter writer =
                    Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("old");
            }

            // Không truyền option: tệp cũ bị cắt ngắn.
            try (BufferedWriter writer =
                    Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("new");
            }

            // Thêm dữ liệu mà không xóa nội dung hiện tại.
            try (BufferedWriter writer = Files.newBufferedWriter(
                    path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                writer.write("+append");
            }

            System.out.println("Content: "
                    + Files.readString(path, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 WriteModesDemo.java
java WriteModesDemo
~~~

Kết quả:

~~~text
Content: new+append
~~~

Giải thích:

1. `Files.createTempFile` tạo tệp tạm thật.
2. Writer đầu ghi `old`; writer thứ hai dùng mode mặc định và **thay thế nội dung bằng `new`**.
3. Writer thứ ba dùng `CREATE, APPEND`, ghi thêm `+append` vào cuối.
4. Mỗi writer được đóng trước khi mở writer tiếp theo; chương trình không phụ thuộc việc dữ liệu còn nằm trong bộ đệm.
5. `finally` dọn tệp. Trong môi trường tệp không cho tạo/ghi/xóa, thao tác có thể ném `IOException` thay vì in output; đây là hành vi báo lỗi đúng, không phải lỗi ngôn ngữ Java.

Mã nguồn dùng ASCII trong dữ liệu để kết quả độc lập với cách hiển thị ký tự trên console. Ví dụ có thể mở rộng bằng tiếng Việt để thực hành charset.

## 8. readLine/newLine và lựa chọn chuẩn hóa dấu xuống dòng

Một cách chép tệp **theo dòng**:

~~~java
String line;
while ((line = reader.readLine()) != null) {
    writer.write(line);
    writer.newLine();
}
~~~

Đây là đoạn minh họa với Reader/Writer đã mở. Cách viết này **thay đổi** định dạng xuống dòng nếu:

- Tệp nguồn sử dụng CRLF nhưng hệ thống đích dùng LF, hoặc ngược lại.
- Dòng cuối nguồn **không có** dấu kết thúc dòng: đoạn trên lại thêm một dấu mới.
- Nguồn có hỗn hợp CR, LF, CRLF.

Nếu mục tiêu là chuyển mọi dòng sang định dạng kết thúc dòng theo hệ điều hành hiện tại, đây có thể là quyết định hợp lệ. Còn nếu mục tiêu là sao chép nội dung ký tự **không đổi**, dùng `read(char[])` và `write(buffer, 0, count)`.

Khi định dạng đòi hỏi một kiểu newline cố định (ví dụ LF), ghi rõ:

~~~java
writer.write("\n");
~~~

Đây là đoạn minh họa; nó luôn ghi ký tự LF thay vì gọi `newLine()` theo nền tảng. Nhưng hãy xác định điều đó thuộc **quy ước định dạng của ứng dụng**, không phải hành vi mặc định “đúng cho mọi tệp”.

### 8.1. Dòng rỗng không phải kết thúc tệp

Đây là lỗi phổ biến:

~~~java
while (!(line = reader.readLine()).isEmpty()) {
    // Sai: dừng ở dòng trống, có thể NPE khi readLine() trả null.
}
~~~

Đoạn trên là ví dụ **sai**: `""` là một dòng rỗng hợp lệ, còn `null` mới là EOF. Cách đúng là:

~~~java
while ((line = reader.readLine()) != null) {
    // Dòng rỗng vẫn được xử lý.
}
~~~

Với văn bản cấu hình có dòng trống phân cách nhóm, cần phân biệt rõ hai trạng thái này.

## 9. Ví dụ Java 17: sao chép văn bản giữ nguyên ký tự và xuống dòng

Lưu **CopyTextWithBuffersDemo.java**:

~~~java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class CopyTextWithBuffersDemo {
    public static void main(String[] args) throws IOException {
        Path folder = Files.createTempDirectory("copy-text-buffers-");
        Path source = folder.resolve("source.txt");
        Path target = folder.resolve("target.txt");

        String original = "Chào\r\nJava\n😀";

        try {
            Files.writeString(source, original, StandardCharsets.UTF_8);

            try (BufferedReader reader =
                         Files.newBufferedReader(source, StandardCharsets.UTF_8);
                 BufferedWriter writer =
                         Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                char[] buffer = new char[3];
                int count;

                while ((count = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, count);
                }
            }

            String restored = Files.readString(
                    target, StandardCharsets.UTF_8);
            boolean sameBytes = Arrays.equals(
                    Files.readAllBytes(source), Files.readAllBytes(target));

            System.out.println("Same text: " + original.equals(restored));
            System.out.println("Same UTF-8 bytes: " + sameBytes);
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
            Files.deleteIfExists(folder);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CopyTextWithBuffersDemo.java
java CopyTextWithBuffersDemo
~~~

Kết quả khi các thao tác tệp thành công:

~~~text
Same text: true
Same UTF-8 bytes: true
~~~

Lý do ví dụ chính xác:

- Writer/Reader dùng **cùng charset UTF-8** cho văn bản hợp lệ.
- `read(char[])` nhận từng khối đơn vị UTF-16; `write(buffer, 0, count)` chuyển **đúng phần hợp lệ** cho writer.
- Chương trình không xóa, thay thế hoặc tự thêm `\r`/`\n`, nên sự pha trộn CRLF và LF trong chuỗi được giữ lại.
- Emoji ngoài BMP gồm hai `char`. Buffer ba đơn vị có thể tạo ranh giới nằm gần cặp surrogate; dù vậy, vì không biến đổi dữ liệu và writer nhìn thấy toàn bộ chuỗi ký tự tuần tự, nội dung được bảo toàn.
- `Arrays.equals` kiểm tra byte thực tế trên tệp trong demo, chứ không chỉ kiểm tra hai chuỗi sau giải mã.

**Giới hạn:** Đây là ví dụ sao chép một tệp đã biết là UTF-8 hợp lệ. Không dùng chiến lược ký tự để khẳng định bảo toàn mọi loại tệp nhị phân. Nếu cần sao chép byte tổng quát, `Files.copy(source, target)` hoặc byte stream phù hợp hơn.

## 10. Charset, mã hóa và xử lý dữ liệu lỗi

### 10.1. Charset phải thống nhất với định dạng dữ liệu

`StandardCharsets.UTF_8` là lựa chọn phổ biến, hỗ trợ Unicode. Nhưng một tệp được tạo theo charset khác (ví dụ UTF-16LE hoặc Windows-1258) cần được đọc đúng charset của nó.

~~~java
try (BufferedReader reader =
        Files.newBufferedReader(path, StandardCharsets.UTF_16LE)) {
    // Đọc văn bản UTF-16LE.
}
~~~

Đây là đoạn minh họa với `Path path`. Không nên thử nhiều charset tùy tiện rồi chọn đầu ra “trông có vẻ đúng” trong hệ thống đòi tính chính xác. Hãy lấy encoding từ hợp đồng định dạng, metadata đáng tin cậy hoặc quy trình phát hiện được thiết kế rõ.

Với dữ liệu không phải UTF-8 nhưng bị đọc là UTF-8, quá trình giải mã có thể thất bại hoặc đưa ra văn bản sai. Việc thêm buffer lớn hơn không sửa được sai charset.

### 10.2. Byte không hợp lệ có thể dẫn đến MalformedInputException

Theo hợp đồng `Files.newBufferedReader`, Reader sẽ báo `IOException` khi gặp dữ liệu malformed/unmappable theo cách giải mã mà API sử dụng. Với một chuỗi byte UTF-8 không hợp lệ, có thể nhận `MalformedInputException` (một kiểu con của `CharacterCodingException`, tiếp tục kế thừa `IOException`).

Ví dụ: byte đầu `C3` trong UTF-8 cần byte tiếp theo thuộc khoảng continuation hợp lệ. Nếu sau đó lại có byte `28` (ký tự `(` trong ASCII), chuỗi byte này không hợp lệ làm mã hóa UTF-8.

### 10.3. Demo Java 17: phát hiện tệp UTF-8 không hợp lệ

Lưu **InvalidUtf8Demo.java**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class InvalidUtf8Demo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("invalid-utf8-", ".txt");

        try {
            Files.write(path, new byte[]{(byte) 0xC3, (byte) 0x28});

            try (BufferedReader reader =
                    Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                while (reader.read() != -1) {
                    // Đọc để buộc quá trình giải mã.
                }
                System.out.println("Decoded successfully");
            } catch (MalformedInputException e) {
                System.out.println("Invalid UTF-8: true");
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 InvalidUtf8Demo.java
java InvalidUtf8Demo
~~~

Kết quả cho dữ liệu được tạo trong chương trình:

~~~text
Invalid UTF-8: true
~~~

Điểm quan trọng: **mở được Reader không đồng nghĩa toàn bộ nội dung tệp đã được giải mã thành công**. Lỗi ký tự có thể phát sinh trong quá trình gọi `read`. Vì vậy, báo “đọc thành công” chỉ sau khi đã xử lý toàn bộ nội dung và hoàn tất việc đóng tài nguyên theo yêu cầu.

Nếu ứng dụng cần chính sách thay thế ký tự lỗi bằng ký tự `�` thay vì báo lỗi, có thể tự tạo `CharsetDecoder` với `CodingErrorAction.REPLACE` rồi bọc qua `InputStreamReader`; không giả định `Files.newBufferedReader` có tham số bật/tắt chính sách này.

### 10.4. Lỗi khi ghi: ký tự Unicode không thể mã hóa

Với charset đầu ra không hỗ trợ một ký tự, quá trình encode có thể thất bại nếu encoder sử dụng chính sách báo lỗi. Ví dụ, nếu chọn US-ASCII nhưng dữ liệu chứa nhiều ký tự tiếng Việt, không được mặc định rằng byte đầu ra vẫn giữ nguyên nội dung. Hãy chọn charset đủ biểu diễn dữ liệu (UTF-8 thường phù hợp) hoặc định nghĩa rõ chính sách thay thế/báo lỗi.

Một vấn đề khác là surrogate chưa hoàn chỉnh: nếu dữ liệu chỉ chứa một nửa cặp surrogate (không phải chuỗi UTF-16 hợp lệ), encoder có thể báo malformed. Điều này quan trọng khi xử lý `char[]` theo khối có biến đổi, hoặc tự xây dựng chuỗi không hợp lệ.

## 11. newLine(), flush() và close(): ba hành động khác nhau

`BufferedWriter` có thể giữ ký tự trong bộ đệm RAM trước khi gửi cho lớp ghi dưới. Ba hành động cần phân biệt:

- **`newLine()`**: *ghi ký tự xuống dòng* theo quy tắc line separator của hệ thống. Nó không bảo đảm dữ liệu đã được chuyển xuống tệp.
- **`flush()`**: yêu cầu đẩy nội dung đang đệm qua writer bên dưới. `flush()` **không bảo đảm** dữ liệu đã được đồng bộ bền vững lên ổ đĩa vật lý.
- **`close()`**: kết thúc việc dùng writer; với `BufferedWriter`, nó thực hiện việc chuyển dữ liệu còn đệm và đóng tài nguyên bên dưới. Nó có thể ném `IOException`.

Ví dụ:

~~~java
try (BufferedWriter writer =
        Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
    writer.write("A");
    writer.newLine();
    writer.write("B");
    writer.flush();
}
~~~

Đây là đoạn minh họa. `flush()` hữu ích khi một thành phần khác cần đọc nội dung đã được đẩy xuống writer bên dưới **trước khi** writer đóng, nhưng nó không thay thế `close()`. Trong các chương trình chỉ ghi rồi kết thúc, `try-with-resources` đủ để gọi `close()`, thường không cần tự flush ở mọi lần write.

### 11.1. Dữ liệu bền vững không chỉ là flush

Đừng kết luận rằng:

~~~java
writer.flush();
System.out.println("Permanently saved!");
~~~

là một chứng minh tệp đã bền vững trước mất điện. Cache hệ điều hành, filesystem và phần cứng có thể ảnh hưởng. Khi yêu cầu durability cao hơn, xem xét thiết kế đồng bộ với `FileChannel.force(...)` hoặc open options và cơ chế giao dịch/thay tệp phù hợp. Ngay cả khi đó, cần hiểu giới hạn bảo đảm của môi trường, không tự khẳng định an toàn tuyệt đối.

### 11.2. Không gọi close rồi tiếp tục write

`BufferedWriter` đóng xong không nên được sử dụng tiếp. Gọi `write` hoặc `flush` trên writer đã đóng thường ném `IOException`. Khác với một số lớp stream bộ nhớ có `close()` không tác động, writer gắn với tệp phải được coi là tài nguyên có vòng đời thật.

## 12. Lỗi mở, đọc, ghi và đóng

Hai phương thức của `Files` có thể báo lỗi tại nhiều giai đoạn:

| Giai đoạn | Ví dụ nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Mở đọc | Thiếu tệp hoặc thiếu quyền đọc | `NoSuchFileException` / `AccessDeniedException` hoặc `IOException` phù hợp |
| Mở ghi | Thư mục cha không tồn tại, thiếu quyền, chế độ `CREATE_NEW` gặp tệp đã có | Kiểm tra mode, quyền, thư mục; bắt ngoại lệ đúng API |
| Đọc | Lỗi thiết bị, dữ liệu không hợp lệ theo charset | Không biến lỗi thành EOF hợp lệ; báo thất bại |
| Ghi | Hết dung lượng, thiết bị lỗi, dữ liệu không mã hóa được | Không mặc định có thể thử ghi lại toàn bộ mà không ghi trùng |
| Flush | Lỗi khi chuyển dữ liệu đã đệm xuống tầng dưới | Xử lý tương đương lỗi ghi, không bỏ qua |
| Close | Lỗi khi chuyển nốt buffer/đóng handle | Giữ lỗi và tránh coi kết quả là thành công chắc chắn |

Một API mới không bảo đảm môi trường hoàn hảo. `Path`, `Files`, `BufferedReader` và `BufferedWriter` vẫn dựa trên tài nguyên hệ thống tệp khi xử lý file thực.

### 12.1. Catch đúng chỗ

Có thể để một phương thức đọc/ghi khai báo `throws IOException` và để tầng cao hơn quyết định thông báo người dùng, retry hay từ chối yêu cầu. Ví dụ:

~~~java
static void appendLine(Path path, String line) throws IOException {
    try (BufferedWriter writer = Files.newBufferedWriter(
            path, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
        writer.write(line);
        writer.newLine();
    }
}
~~~

Đây là **đoạn phương thức độc lập về cú pháp nhưng cần đặt trong lớp** và thêm import `BufferedWriter`, `IOException`, `Path`, `Files`, `StandardCharsets`, `StandardOpenOption`. Nó chủ động đóng writer do chính mình tạo.

Nếu append thất bại giữa chừng, không được tự động retry toàn bộ bản ghi mà không biết đã ghi được bao nhiêu byte/ký tự. Một số hệ thống cần giao thức có transaction, khóa hoặc idempotency để tránh bản ghi trùng/dở dang.

### 12.2. try-with-resources giữ suppressed exception

Nếu lỗi xảy ra trong thân `try` và tiếp tục có lỗi khi `close()`, `try-with-resources` giữ lỗi trong thân là ngoại lệ chính, còn lỗi đóng được gắn bằng `Throwable.getSuppressed()`. Điều này giúp không mất thông tin chẩn đoán.

Không viết `catch(IOException e) { return ""; }` cho một thao tác đọc bắt buộc: chuỗi rỗng có thể là nội dung tệp hợp lệ, còn lỗi I/O là một trạng thái khác. Nên ném tiếp, trả kiểu kết quả có trạng thái lỗi, hoặc xử lý cụ thể theo hợp đồng nghiệp vụ.

## 13. Sự khác biệt giữa Files.readString, readAllLines, lines và newBufferedReader

Khi chỉ cần đọc văn bản, không phải lúc nào cũng cần tự mở `BufferedReader`. Chọn API theo lượng dữ liệu và cách xử lý.

| API | Kết quả | Đặc tính |
| --- | --- | --- |
| `Files.readString(path, charset)` | Một `String` chứa toàn bộ tệp | Thuận tiện cho tệp nhỏ; phải giữ cả nội dung trong RAM |
| `Files.readAllLines(path, charset)` | `List<String>` của các dòng | Tiện cho tệp nhỏ/vừa; giữ toàn bộ dòng trong RAM |
| `Files.lines(path, charset)` | `Stream<String>` đọc dòng một cách lazy | Cần **đóng Stream** vì nó giữ tài nguyên I/O |
| `Files.newBufferedReader(path, charset)` | `BufferedReader` | Linh hoạt: readLine, read(char[]), mark/reset nếu phù hợp |

`Files.readAllLines` trả danh sách dòng đã đọc và cũng loại bỏ dấu kết thúc dòng tương tự quy tắc đọc dòng. `Files.lines` cho phép xử lý dòng dưới dạng Stream, nhưng vẫn là tài nguyên cần đóng:

~~~java
try (java.util.stream.Stream<String> lines =
        Files.lines(path, StandardCharsets.UTF_8)) {
    lines.filter(s -> !s.isBlank()).forEach(System.out::println);
}
~~~

Đây là đoạn minh họa với `Path path`; Stream đóng sẽ đóng tài nguyên do `Files.lines` mở.

Ngược lại, `BufferedReader.lines()` trả một Stream lấy các dòng từ **BufferedReader đang có**. Đóng Stream đó không thay thế trách nhiệm đóng Reader bên ngoài. Cách quản lý rõ ràng:

~~~java
try (BufferedReader reader =
        Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    reader.lines().forEach(System.out::println);
}
~~~

Đây là đoạn minh họa: Reader vẫn được đóng bằng `try-with-resources`. Không nên vừa đọc thủ công `reader.readLine()` vừa vận hành Stream `reader.lines()` song song trên cùng Reader; đó là một nguồn tiêu thụ tuần tự có trạng thái.

## 14. So sánh Files.writeString và newBufferedWriter

`Files.writeString(path, text, charset, options...)` là phương án ngắn khi đã có toàn bộ `String`. Nó mở, ghi và đóng tài nguyên theo hợp đồng của lời gọi. Ví dụ:

~~~java
Files.writeString(path, "Xin chào", StandardCharsets.UTF_8);
~~~

Đây là đoạn minh họa với `Path path` có sẵn. `newBufferedWriter` hữu ích hơn nếu phải ghi nhiều lần, xử lý dòng từng bước, stream đầu vào dài hoặc tạo nội dung dần mà không giữ toàn bộ thành một String.

Không có quy tắc “luôn dùng BufferedWriter nhanh hơn”: hiệu năng tùy số lần write, kích thước dữ liệu, lớp đệm và filesystem. Đối với tệp rất nhỏ và đã có sẵn String, `Files.writeString` có thể rõ ràng hơn. Đối với quá trình ghi lặp nhiều mảnh, `BufferedWriter` tránh mở lại tệp cho mỗi mảnh và cung cấp API quản lý writer xuyên suốt tác vụ.

### 14.1. Không mở writer mới cho từng dòng với mode mặc định

Cách sai:

~~~java
for (String line : lines) {
    try (BufferedWriter writer =
            Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
        writer.write(line);
    }
}
~~~

Đây là đoạn minh họa sai về logic; `lines` và `path` có sẵn. Vì mỗi lần mở mặc định **cắt ngắn tệp**, kết quả thường chỉ còn dòng cuối. Ngoài ra, việc mở/đóng cho mỗi dòng làm tăng overhead.

Cách phù hợp nếu muốn ghi nhiều dòng liên tiếp:

~~~java
try (BufferedWriter writer =
        Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
    for (String line : lines) {
        writer.write(line);
        writer.newLine();
    }
}
~~~

Đây là đoạn minh họa với `Iterable<String> lines` được xác định. Writer được mở một lần rồi đóng sau cùng; tệp bị thay toàn bộ có chủ đích. Nếu muốn append vào tệp cũ, thêm các open options thích hợp.

## 15. Mở nhiều tài nguyên và thứ tự đóng

Một thao tác chuyển dữ liệu từ tệp nguồn sang tệp đích có thể mở cả Reader và Writer:

~~~java
try (BufferedReader reader =
             Files.newBufferedReader(source, StandardCharsets.UTF_8);
     BufferedWriter writer =
             Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
    char[] buffer = new char[8192];
    int count;
    while ((count = reader.read(buffer)) != -1) {
        writer.write(buffer, 0, count);
    }
}
~~~

Đây là đoạn minh họa với `Path source` và `Path target`. Tài nguyên được đóng theo **thứ tự ngược với khai báo**: writer trước, reader sau.

Không nên đặt source và target thành **cùng đường dẫn** trong ví dụ ghi đè: khi writer mở ở chế độ mặc định, tệp đích (cũng là nguồn) có thể bị cắt ngắn, làm mất dữ liệu đang cần đọc. Nếu muốn biến đổi tệp tại chỗ, cân nhắc ghi ra **tệp tạm khác**, đóng đầy đủ, kiểm tra kết quả rồi chuyển tệp tạm sang đích theo chính sách phù hợp.

### 15.1. Nếu một bên mở thất bại

`try-with-resources` cũng có lợi khi mở nhiều tài nguyên: nếu Reader mở thành công nhưng mở Writer ném ngoại lệ, Reader đã mở sẽ được đóng trong quá trình thoát. Cách này hạn chế rò rỉ handle so với việc tự gán biến và quản lý đóng thủ công.

### 15.2. Không tự đóng tài nguyên của bên khác

Nếu một phương thức **nhận** `BufferedReader` hoặc `BufferedWriter` từ caller, nó thường không nên tự đóng nếu hợp đồng không trao quyền sở hữu. Ngược lại, khi phương thức tự gọi `Files.newBufferedReader` / `newBufferedWriter` để mở tệp phục vụ chính tác vụ đó, phương thức có trách nhiệm đóng tài nguyên khi xong.

## 16. Chọn dung lượng buffer và chú ý hiệu năng

`BufferedReader` / `BufferedWriter` có bộ đệm ký tự nội bộ. Khi sử dụng `Files.newBufferedReader` / `newBufferedWriter`, kích thước bộ đệm được chọn theo cách triển khai của API; không nên phụ thuộc một con số cụ thể để dự đoán hành vi.

Nếu ứng dụng cần buffer `char[]` của riêng mình để sao chép theo khối, có thể dùng `new char[4096]` hoặc `new char[8192]` làm kích thước thử nghiệm. Không có con số tối ưu cho mọi workload. Kích thước nhỏ hơn vẫn cho kết quả chính xác nếu sử dụng `count`; kích thước lớn hơn có thể giảm số lần gọi nhưng tăng một lượng bộ nhớ nhất định.

**Những vấn đề quan trọng hơn vài kilobyte buffer:**

- Không mở/đóng tệp lại cho từng token/dòng khi có thể xử lý liên tục.
- Không tích lũy tệp rất lớn thành một `String` khi chỉ cần quét tuần tự.
- Không dùng `readLine` với dòng có thể dài vô hạn mà không có chính sách giới hạn dữ liệu.
- Không flush ở mỗi ký tự nếu không có yêu cầu hiển thị/đồng bộ tương ứng.
- Phân biệt chất lượng xử lý lỗi, charset và dữ liệu với tối ưu tốc độ.

## 17. Các lỗi phổ biến với newBufferedReader/newBufferedWriter

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Không chọn charset phù hợp | Văn bản có dấu sai hoặc lỗi giải mã | Chỉ định charset theo định dạng |
| Tưởng Writer mặc định append | Nội dung cũ bị xóa | Dùng `CREATE, APPEND` khi muốn nối |
| Mở Writer mới cho từng dòng mà không đặt APPEND | Chỉ giữ dòng cuối | Mở một Writer cho toàn bộ tác vụ |
| Dùng `readLine() == ""` để xét EOF | Bỏ dòng trống hoặc NPE khi null | Chỉ `null` mới là EOF |
| Dùng `readLine()+newLine()` để copy nguyên trạng | Chuẩn hóa/mất kiểu dấu xuống dòng gốc | Dùng `read(char[])` + `write(...,count)` |
| Ghi toàn bộ buffer sau lần đọc ngắn | Chèn ký tự cũ ở cuối mảng | Dùng `writer.write(buffer,0,count)` |
| Dùng Reader/Writer cho dữ liệu nhị phân | Byte có thể bị chuyển mã và hỏng | Sử dụng byte stream/NIO |
| Không đóng Writer | Dữ liệu còn đệm có thể chưa tới đích; rò rỉ handle | `try-with-resources` |
| Tưởng `flush()` đồng nghĩa fsync | Đánh giá sai tính bền vững | Phân biệt flush/close/force |
| Đọc tệp rất lớn bằng readString | Có thể thiếu heap | Xử lý theo dòng hoặc khối |
| Đọc và ghi cùng đường dẫn bằng writer mặc định | Cắt ngắn nguồn đang đọc | Tệp tạm hoặc thiết kế in-place an toàn |
| Bỏ qua IOException từ close | Có thể mất tín hiệu ghi lỗi cuối | Để lỗi truyền lên hoặc xử lý đúng |
| Tưởng đường dẫn luôn tồn tại khi gọi `Path.of` | Mở đọc thất bại | Bắt ngoại lệ khi mở thực |
| Kết luận readLine đã giữ nguyên CRLF | Mất thông tin dấu dòng | Xử lý trực tiếp ký tự nếu cần |
| Dùng charset mặc định không nhất quán ở nhiều tầng | Tệp đọc/ghi khác nội dung | Chọn charset tường minh xuyên suốt |

## 18. Câu hỏi tự kiểm tra

**Câu 1.** `Files.newBufferedWriter(path, UTF_8)` mở một tệp đã có nội dung. Nội dung cũ có được giữ lại không?

**Đáp án:** mặc định không. Khi không truyền open option, writer dùng chế độ tương đương `CREATE`, `TRUNCATE_EXISTING`, `WRITE`: tệp hiện hữu bị cắt ngắn. Muốn nối thêm, chỉ định `CREATE` và `APPEND` phù hợp.

**Câu 2.** `readLine()` trả `""` thì đã EOF chưa?

**Đáp án:** chưa. Đó là một dòng rỗng. `null` mới là dấu hiệu hết dữ liệu.

**Câu 3.** Đọc bằng `readLine` rồi ghi bằng `newLine` có bảo toàn CRLF của tệp nguồn trên mọi hệ điều hành không?

**Đáp án:** không. `readLine` loại bỏ dấu kết thúc dòng, `newLine` tạo dấu kết thúc phù hợp môi trường đích. Muốn giữ nguyên chuỗi ký tự xuống dòng, dùng `read(char[])`/`write(char[],0,count)`.

**Câu 4.** Khi `reader.read(buffer)` trả 5 và mảng dài 1024, nên ghi bao nhiêu ký tự?

**Đáp án:** 5, bằng `writer.write(buffer, 0, 5)`; không ghi cả mảng.

**Câu 5.** `flush()` có bảo đảm byte đã được lưu bền vững trên thiết bị vật lý không?

**Đáp án:** không. Flush đẩy dữ liệu qua tầng bộ đệm mà writer quản lý; yêu cầu durability khác với việc xả buffer.

**Câu 6.** Vì sao không mở `newBufferedWriter(sourcePath, UTF_8)` cùng lúc để “sửa tại chỗ” một tệp đang được Reader đọc?

**Đáp án:** mặc định Writer có thể cắt ngắn chính tệp nguồn; dữ liệu gốc bị mất trước khi đọc xong. Nên dùng tệp tạm và quy trình thay thế an toàn.

**Câu 7.** `Files.lines` và `BufferedReader.lines` có cùng trách nhiệm đóng tài nguyên không?

**Đáp án:** với `Files.lines`, đóng Stream để đóng tài nguyên do phương thức mở. Với `BufferedReader.lines`, vẫn phải quản lý/đóng BufferedReader gốc; không cho rằng đóng Stream tự đóng Reader.

**Câu 8.** Vì sao Writer đọc/ghi UTF-8 có thể ném IOException sau khi mở thành công?

**Đáp án:** lỗi có thể xuất hiện ở ghi xuống hệ thống tệp, flush/close, hoặc chuyển mã ký tự không hợp lệ theo charset; mở thành công không bảo đảm các thao tác tiếp theo thành công.

### Bài tập thực hành

1. **Đọc dòng rỗng:** sửa `ReadLinesDemo` với nguồn `"A\n\nB"`; dự đoán số dòng và giá trị từng dòng. Kiểm tra rằng không đánh đồng dòng rỗng với EOF.
2. **Append:** sửa `WriteModesDemo` để ghi thêm `"+1"` và `"+2"` bằng một `BufferedWriter` duy nhất. So sánh kết quả với việc mở writer mới mỗi lần.
3. **Ghi đè an toàn:** thử `CREATE_NEW` khi tệp đã tồn tại và bắt `FileAlreadyExistsException`. Không xóa tệp chỉ để làm bài kiểm thử này thành công.
4. **Charset:** ghi một văn bản tiếng Việt bằng UTF-8 rồi đọc lại. Thử dùng charset khác trong môi trường thử nghiệm để quan sát lý do phải biết encoding nguồn; không đưa kết quả chuyển mã sai vào sản phẩm.
5. **Giữ xuống dòng:** dùng văn bản chứa CRLF, LF và CR, sao chép bằng hai chiến lược (theo dòng và theo khối ký tự), sau đó so sánh bằng `Arrays.equals(Files.readAllBytes(a), Files.readAllBytes(b))` để thấy sự khác biệt.
6. **Tệp lớn:** thiết kế hàm nhận `Path source` và `Path target`, copy văn bản bằng buffer `char[]`, không giữ toàn bộ tệp trong RAM, và bảo đảm đóng cả Reader lẫn Writer khi có ngoại lệ.

## 19. Tổng kết

- **`Files.newBufferedReader(path, charset)`** mở tệp cho việc đọc văn bản đã giải mã; có thể dùng `readLine()` hoặc đọc từng khối ký tự.
- **`Files.newBufferedWriter(path, charset, options...)`** tạo Writer có bộ đệm để ghi văn bản; **mode mặc định có thể xóa nội dung tệp hiện có**.
- **`readLine()` loại bỏ dấu kết thúc dòng**, trả `null` ở EOF và trả `""` cho dòng trống. `newLine()` ghi dấu xuống dòng theo nền tảng.
- **`read(char[])` và `write(buffer,0,count)`** phù hợp khi cần giữ nguyên chuỗi ký tự và xử lý tệp lớn theo từng phần.
- **UTF-8 là charset nên nêu rõ** khi định dạng tệp dùng UTF-8; charset sai hoặc dữ liệu malformed có thể gây lỗi đọc/ghi.
- **`try-with-resources` quản lý close**, giúp tránh rò rỉ tệp và giữ lỗi đóng; `flush` không phải lời hứa lưu dữ liệu bền vững trên thiết bị.
- **Chọn API theo mục tiêu**: `readString/writeString` cho dữ liệu nhỏ, `Files.lines` cho stream dòng, và `newBufferedReader/newBufferedWriter` khi cần điều khiển quá trình đọc/ghi.

### Tài liệu Java SE 17

- [Files.newBufferedReader(Path, Charset)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#newBufferedReader(java.nio.file.Path,java.nio.charset.Charset))
- [Files.newBufferedWriter(Path, Charset, OpenOption...)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#newBufferedWriter(java.nio.file.Path,java.nio.charset.Charset,java.nio.file.OpenOption...))
- [BufferedReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html)
- [BufferedWriter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedWriter.html)
- [StandardOpenOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html)
- [StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html)
- [Files.readString, writeString và lines](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="files-read-write-006">newInputStream, newOutputStream và các lựa chọn mở tệp</a>

<details>
<summary>Xem chi tiết</summary>

# `Files.newInputStream`, `Files.newOutputStream` và các lựa chọn mở tệp trong Java

Khi ứng dụng Java phải **đọc hoặc ghi byte của file** theo dòng dữ liệu (stream), hai phương thức `java.nio.file.Files.newInputStream(Path, OpenOption...)` và `Files.newOutputStream(Path, OpenOption...)` cung cấp cách mở file bằng đối tượng `Path` và trả về `InputStream`/`OutputStream` quen thuộc của Java I/O.

Một vấn đề quan trọng hơn cú pháp là **lựa chọn cách mở file**: đọc một file đã tồn tại, tạo mới, ghi đè nội dung cũ, ghi thêm cuối file, hoặc từ chối khi đường dẫn đã tồn tại. Sai một `OpenOption` có thể dẫn đến **mất dữ liệu**, ghi đè file người dùng, tưởng đã ghi thêm nhưng thực tế xóa nội dung trước đó, hoặc ghi nửa file mà không phát hiện lỗi.

Bài học thuộc nhóm **Đọc ghi bằng Files** của Java Core I/O. Mục tiêu là hiểu hành vi mặc định và tùy chọn của hai API, phối hợp chúng với buffering và chuyển mã văn bản, xử lý lỗi/tài nguyên, đồng thời lựa chọn giải pháp phù hợp cho các tình huống thực tế.

## 1. Ba thành phần: `Path`, `Files` và byte stream

**`Path`** là mô hình đường dẫn của Java NIO.2, ví dụ `Path.of("data", "input.bin")`. Tạo `Path` không tạo hay mở file. Nó chỉ mô tả vị trí file theo quy tắc filesystem/provider.

**`Files`** là lớp tiện ích có các phương thức tĩnh thao tác trên đường dẫn. Hai phương thức chính của bài:

```java
InputStream in = Files.newInputStream(path);
OutputStream out = Files.newOutputStream(path);
```

Đây là **mã minh họa**, cần import `java.io.InputStream`, `java.io.OutputStream`, `java.nio.file.Files`, `java.nio.file.Path`; biến `path` phải được khai báo và thao tác mở có thể ném `IOException`.

**`InputStream` và `OutputStream`** thao tác trên **byte**, không tự hiểu text, charset, newline hay cấu trúc định dạng file. `InputStream.read()` trả một byte dưới dạng số `0..255` hoặc `-1` ở EOF. `OutputStream.write(int)` ghi một byte (8 bit thấp).

Sơ đồ:

```text
Đọc:
Path -- Files.newInputStream(...) --> InputStream -- read() --> byte
                                                                   |
                                                 giải mã nếu cần text
                                                                   v
                                                        String / dữ liệu ứng dụng

Ghi:
String / dữ liệu ứng dụng -- mã hóa nếu là text --> byte
                                                    |
Path -- Files.newOutputStream(...) --> OutputStream -- write() --> file
```

`Files.newInputStream` **không giống** `Files.readAllBytes`: nó không yêu cầu đưa toàn bộ file vào RAM rồi mới xử lý. Tương tự, `Files.newOutputStream` thuận tiện khi ghi tuần tự theo từng byte hoặc từng khối, không cần tạo sẵn mảng byte chứa toàn bộ output.

## 2. Cú pháp và phiên bản Java

Các khai báo khái quát:

```java
public static InputStream newInputStream(Path path, OpenOption... options)
        throws IOException;

public static OutputStream newOutputStream(Path path, OpenOption... options)
        throws IOException;
```

Trong phần trên, `OpenOption` là interface đánh dấu tùy chọn mở file, còn `StandardOpenOption` là enum gồm các lựa chọn thường dùng như `READ`, `WRITE`, `APPEND`, `CREATE` và `CREATE_NEW`. Cả hai API có từ **Java 7** trong NIO.2 và có thể dùng trực tiếp với Java 17.

Ví dụ code thông dụng:

```java
try (InputStream input = Files.newInputStream(Path.of("input.bin"))) {
    // Đọc byte.
}

try (OutputStream output = Files.newOutputStream(Path.of("output.bin"))) {
    // Ghi byte.
}
```

Đây là **mã minh họa**, cần đặt trong phương thức khai báo/giải quyết `IOException`. `try-with-resources` tự đóng stream khi ra khỏi khối, cả khi có exception.

Điểm quan trọng: `OpenOption...` là tham số **varargs**, nên có thể bỏ qua hoặc truyền nhiều lựa chọn. **Không truyền gì** không đồng nghĩa với “không có hành vi mặc định”; đặc biệt **mặc định của input khác output**.

## 3. Mặc định của `Files.newInputStream`

Cách gọi:

```java
InputStream input = Files.newInputStream(path);
```

mặc định tương đương **mở để đọc** với `StandardOpenOption.READ`. File mục tiêu phải có thể mở để đọc; API **không tự tạo file nếu chưa tồn tại**.

Ví dụ:

```java
try (InputStream input = Files.newInputStream(Path.of("existing.dat"))) {
    byte[] buffer = new byte[4096];
    int n;
    while ((n = input.read(buffer)) != -1) {
        // Xử lý buffer[0..n), không phải toàn bộ 4096 phần tử.
    }
}
```

Đây là **đoạn minh họa** có thể ném `IOException`, dùng file `existing.dat` có sẵn. Khi một lần `read(buffer)` trả số dương nhỏ hơn chiều dài buffer, đó có thể là một **short read hợp lệ**, không nhất thiết đã tới EOF. Chỉ `-1` thể hiện EOF theo hợp đồng.

### Tùy chọn cho input

`StandardOpenOption.READ` là lựa chọn chuẩn, rõ nghĩa nhất để truyền tường minh:

```java
try (InputStream input = Files.newInputStream(
        path, StandardOpenOption.READ)) {
    // Xử lý byte.
}
```

Đây là **mã minh họa**; cần import `StandardOpenOption`, `Files`, `InputStream` và chuẩn bị biến `path`. Với `newInputStream`, hợp đồng chuẩn chỉ yêu cầu hỗ trợ `READ`, ngoài ra implementation/provider có thể hỗ trợ những tùy chọn riêng. **Không** dùng `APPEND`, `TRUNCATE_EXISTING` hoặc `CREATE_NEW` như thể đây là tùy chọn đọc chuẩn được hỗ trợ; chúng thuộc ngữ cảnh mở để ghi.

Nếu truyền lựa chọn không hợp lệ hoặc không hỗ trợ, API có thể ném `UnsupportedOperationException`, `IllegalArgumentException` hay ngoại lệ khác theo contract/provider. Nếu file không tồn tại, trên filesystem thông thường sẽ có `NoSuchFileException`; nếu quyền truy cập bị từ chối, có thể nhận `AccessDeniedException` hoặc một `IOException` khác.

**`InputStream` không cung cấp random access.** Đọc tuần tự từ đầu theo semantics của stream; nếu cần `position()`/`seek`, nên xem `FileChannel` hoặc `SeekableByteChannel`, không cố tự giả lập random access bằng `skip()` trong mọi trường hợp.

## 4. Mặc định của `Files.newOutputStream` — điểm dễ mất dữ liệu

Cách gọi:

```java
OutputStream output = Files.newOutputStream(path);
```

khi **không truyền option** tương đương:

```java
Files.newOutputStream(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE);
```

Ba ý nghĩa phối hợp:

1. **`CREATE`:** nếu file chưa tồn tại, cho phép tạo file mới.
2. **`TRUNCATE_EXISTING`:** nếu file đã tồn tại và được mở để ghi, **cắt nội dung cũ về độ dài 0** khi mở.
3. **`WRITE`:** mở file để ghi byte.

Vì thế, `Files.newOutputStream(path)` **không phải append**. Nếu file có dữ liệu trước đó, nội dung có thể bị xóa **ngay khi mở stream thành công**, thậm chí chương trình chưa gọi `write()` lần nào.

Ví dụ:

```java
try (OutputStream output = Files.newOutputStream(path)) {
    output.write("Mới".getBytes(StandardCharsets.UTF_8));
}
```

Đây là **mã minh họa**: biến `path` phải có, cần import `Files`, `OutputStream` và `StandardCharsets`. Nếu file cũ chứa `"Dữ liệu quan trọng"`, lời gọi mặc định có thể thay toàn bộ nội dung bằng byte UTF-8 của `"Mới"`.

**Nếu không muốn ghi đè**, chọn `CREATE_NEW` thay vì mặc định. **Nếu muốn ghi thêm**, dùng `APPEND` và quyết định có `CREATE` hay không. **Nếu muốn thay nội dung một file quan trọng theo cách hạn chế file dở dang**, cân nhắc ghi file tạm rồi di chuyển có kiểm soát, không nhầm `newOutputStream` với transaction.

## 5. Các `StandardOpenOption` chính dùng với output

| Option | Hành vi | Nên dùng khi |
|---|---|---|
| `WRITE` | Mở để ghi, không tự cắt ngắn hay tạo file nếu chỉ truyền một mình | File đã tồn tại; muốn ghi từ đầu theo cách không tự truncate |
| `APPEND` | Ghi byte ở cuối file; hành vi ghi đồng thời phụ thuộc provider | Nhật ký append hoặc nối dữ liệu cuối file |
| `TRUNCATE_EXISTING` | Khi mở file đã tồn tại để ghi, cắt ngắn về 0; nếu không có tác dụng do cách mở, xem contract | Chủ động thay toàn bộ nội dung cũ |
| `CREATE` | Tạo file nếu chưa tồn tại, còn file đã có thì mở theo những option ghi khác | Tạo hoặc mở để ghi |
| `CREATE_NEW` | Chỉ tạo mới; nếu đường dẫn đã tồn tại, thao tác thất bại; thao tác kiểm tra-tạo là nguyên tử theo contract | Không được ghi đè file sẵn có |
| `SYNC` | Yêu cầu cập nhật nội dung **và metadata** liên quan đồng bộ theo semantics provider | Một số bài toán yêu cầu đồng bộ lưu trữ |
| `DSYNC` | Yêu cầu cập nhật nội dung file đồng bộ theo semantics provider | Khi có yêu cầu độ bền dữ liệu ở mức option này |

**`APPEND` và `TRUNCATE_EXISTING` không phải cặp option hợp lý để dùng chung.** Một cái yêu cầu ghi ở cuối nội dung cũ, một cái xóa nội dung cũ trước khi ghi. Tổ hợp này không hợp lệ theo hợp đồng `newOutputStream` và có thể gây `IllegalArgumentException`.

### Lưu ý đặc biệt về `WRITE` đơn lẻ

Một trong những lỗi nguy hiểm nhất là nghĩ:

```java
Files.newOutputStream(path, StandardOpenOption.WRITE)
```

sẽ **thay thế trọn nội dung file**. Thực tế, với file thường trên provider mặc định, stream bắt đầu ở **đầu file**; nếu ghi một số byte ít hơn nội dung cũ mà không truyền `TRUNCATE_EXISTING`, **phần đuôi cũ có thể vẫn còn**.

Ví dụ file ban đầu có byte ASCII `ABCDEF`, mở với `WRITE` và ghi `XY` có thể cho nội dung **`XYCDEF`**, không phải `XY`. Điều này tạo file sai định dạng rất dễ bị bỏ qua.

### `CREATE` khác `CREATE_NEW`

- `CREATE` cho phép tạo nếu chưa tồn tại **hoặc** mở file đang tồn tại. Độ dài/nội dung file cũ sau đó phụ thuộc `TRUNCATE_EXISTING`, `APPEND` và các option khác.
- `CREATE_NEW` yêu cầu tạo file mới và **thất bại khi đường dẫn đã tồn tại**. Theo contract, sự tồn tại của symbolic link tại đường dẫn cũng có thể khiến nó thất bại, dù link trỏ tới mục tiêu không tồn tại.
- `CREATE_NEW` hữu ích khi cần “không ghi đè”. Khi thêm logic `if (!Files.exists(path)) ...` trước `CREATE`, vẫn có **TOCTOU race** vì đường dẫn có thể được tạo đồng thời.
- `CREATE_NEW` không bảo đảm toàn bộ **quá trình ghi nội dung** là nguyên tử; nó chỉ giải quyết điều kiện tạo mới. Lỗi giữa chừng vẫn có thể để lại file chưa ghi đủ.

### `SYNC`/`DSYNC` không thay cho thiết kế lưu trữ

Các option đồng bộ có thể ảnh hưởng mạnh đến hiệu năng và phụ thuộc provider/filesystem. `DSYNC` liên quan dữ liệu, `SYNC` còn bao gồm metadata theo contract; không nên bật chúng chỉ để “an toàn hơn” mà chưa biết yêu cầu. Không coi `flush()` là cùng một điều với bảo đảm dữ liệu bền vững khi mất điện.

## 6. Bảng chọn cách mở output nhanh

| Mong muốn | Lời gọi gợi ý | Điều cần nhớ |
|---|---|---|
| Tạo file nếu thiếu và **thay toàn bộ** nội dung cũ | `Files.newOutputStream(path)` | Mặc định `CREATE + TRUNCATE_EXISTING + WRITE` |
| Chỉ tạo file mới, không ghi đè | `newOutputStream(path, CREATE_NEW, WRITE)` | Thất bại nếu đường dẫn đã tồn tại |
| Ghi thêm vào file đang tồn tại | `newOutputStream(path, APPEND)` | Có thể thất bại nếu file chưa tồn tại |
| Ghi thêm, tạo file nếu chưa có | `newOutputStream(path, CREATE, APPEND)` | Không tự thêm newline |
| Ghi từ đầu mà giữ phần đuôi chưa bị ghi đè | `newOutputStream(path, WRITE)` | Thường cần rất thận trọng |
| Mở file đang tồn tại để **thay trọn** nội dung | `newOutputStream(path, WRITE, TRUNCATE_EXISTING)` | Không tự tạo file mới nếu thiếu `CREATE` |
| Đọc file có sẵn | `Files.newInputStream(path)` | Mặc định đọc, không tạo file |

Đây là các dạng dùng trên filesystem thông thường. Mọi thao tác có thể ném `IOException` vì quyền truy cập, hệ thống file, file đang được sử dụng, thiết bị lỗi hoặc lý do khác. Nếu ứng dụng đòi hỏi ràng buộc chính xác về concurrent writes, atomicity hoặc symlink, phải đọc contract của provider và thiết kế cao hơn việc chọn một option đơn lẻ.

## 7. Ví dụ 1 — `WRITE`, `APPEND`, mặc định, `CREATE_NEW` và lỗi file thiếu

Tạo tệp `OpenOptionsDemo.java` (Java 17+):

```java
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class OpenOptionsDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("options-");
        Path file = dir.resolve("demo.txt");
        try {
            try (OutputStream out = Files.newOutputStream(file)) {
                out.write("ABCDEF".getBytes(StandardCharsets.UTF_8));
            }
            try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.WRITE)) {
                out.write("XY".getBytes(StandardCharsets.UTF_8));
            }
            System.out.println("WRITE only=" + Files.readString(file, StandardCharsets.UTF_8));

            try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.APPEND)) {
                out.write("Z".getBytes(StandardCharsets.UTF_8));
            }
            System.out.println("APPEND=" + Files.readString(file, StandardCharsets.UTF_8));

            try (OutputStream out = Files.newOutputStream(file)) {
                out.write("Q".getBytes(StandardCharsets.UTF_8));
            }
            System.out.println("default output=" + Files.readString(file, StandardCharsets.UTF_8));

            try (OutputStream ignored =
                    Files.newOutputStream(file, StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE)) {
                System.out.println("unexpected new file");
            } catch (FileAlreadyExistsException expected) {
                System.out.println("CREATE_NEW existing=FileAlreadyExistsException");
            }

            try (var ignored = Files.newInputStream(dir.resolve("missing.txt"))) {
                System.out.println("unexpected missing input");
            } catch (NoSuchFileException expected) {
                System.out.println("missing input=NoSuchFileException");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 --release 17 OpenOptionsDemo.java
java OpenOptionsDemo
```

Kết quả đã kiểm thử với Java 17 trên filesystem mặc định:

```text
WRITE only=XYCDEF
APPEND=XYCDEFZ
default output=Q
CREATE_NEW existing=FileAlreadyExistsException
missing input=NoSuchFileException
```

Giải thích theo từng bước:

1. Tạo thư mục tạm riêng và file `demo.txt` chứa `ABCDEF`.
2. Mở lại chỉ với `WRITE`; ghi `XY` ở đầu, **không truncate**, nên phần đuôi `CDEF` còn lại.
3. Mở với `APPEND`; ghi `Z` vào cuối, tạo `XYCDEFZ`.
4. Mở `newOutputStream(file)` không option và ghi `Q`. Mặc định file được **truncate khi mở**, nên giờ chỉ còn `Q`.
5. `CREATE_NEW` từ chối mở đường dẫn đã tồn tại và ném `FileAlreadyExistsException`, bảo vệ chống ghi đè ngoài ý muốn.
6. `newInputStream` trên file thiếu ném `NoSuchFileException` theo provider mặc định, vì input stream **không tự tạo nguồn đọc**.
7. Khối `finally` dọn file và thư mục tạm do chương trình tạo, không động tới dữ liệu người dùng.

**Lưu ý về ngoại lệ:** ví dụ trình bày các subtype trên một filesystem thông dụng; hệ điều hành/provider khác có thể từ chối thao tác với `IOException` theo ngữ cảnh riêng. Không viết nghiệp vụ giả định mọi lỗi đều là `NoSuchFileException` hoặc `FileAlreadyExistsException`.

## 8. Ví dụ 2 — sao chép byte streaming, không tải toàn file vào RAM

Dùng `newInputStream` và `newOutputStream` để truyền byte theo các khối có kích thước giới hạn. Đây là tình huống thích hợp cho file lớn, ví dụ sao chép tệp nhị phân hoặc xử lý nội dung trước khi lưu.

Tạo tệp `StreamingCopyDemo.java`:

```java
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class StreamingCopyDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("copy-stream-");
        Path source = dir.resolve("source.txt");
        Path dest = dir.resolve("dest.txt");
        String text = "Line 1\nTiếng Việt 😊\n";
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        try {
            try (OutputStream output = Files.newOutputStream(
                    source, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                output.write(encoded);
            }

            long copied = 0;
            try (InputStream input = Files.newInputStream(source);
                 OutputStream output = Files.newOutputStream(
                         dest, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[4];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    copied += count;
                }
            }

            System.out.println("copied all bytes=" + (copied == encoded.length));
            System.out.println("files equal=" + (Files.mismatch(source, dest) == -1));
            System.out.println("text preserved=" + text.equals(
                    Files.readString(dest, StandardCharsets.UTF_8)));
            System.out.println("bytes exceed UTF-16 units=" + (encoded.length > text.length()));
        } finally {
            Files.deleteIfExists(dest);
            Files.deleteIfExists(source);
            Files.deleteIfExists(dir);
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 StreamingCopyDemo.java
java StreamingCopyDemo
```

```text
copied all bytes=true
files equal=true
text preserved=true
bytes exceed UTF-16 units=true
```

Mặc dù ví dụ khởi tạo sẵn một chuỗi ngắn làm dữ liệu thử, **vòng lặp copy** chỉ cần một buffer 4 byte ở thời điểm xử lý; với nguồn là file rất lớn, thuật toán không phải đọc hết file vào một mảng duy nhất.

Các nguyên tắc quan trọng:

- `input.read(buffer)` có thể trả ít hơn `buffer.length`. **Luôn ghi `output.write(buffer, 0, count)`**, không dùng `output.write(buffer)` nếu phần sau buffer còn dữ liệu cũ.
- EOF là `-1`; không dùng `available() == 0` để kết luận nguồn đã hết.
- Dữ liệu được sao chép ở **tầng byte**, giữ nguyên thứ tự các byte UTF-8, kể cả khi một ký tự nhiều byte bị chia giữa hai lượt đọc. Không giải mã từng khối rồi mã hóa tùy tiện nếu yêu cầu copy nhị phân chính xác.
- `Files.mismatch(source, dest)` (Java 12+) trả `-1` nếu không tìm thấy vị trí byte khác nhau giữa hai file tại thời điểm so sánh. Với file có thể bị sửa đồng thời, phép so sánh không tự bảo đảm snapshot nhất quán.
- `Files.size(path)` trả số **byte**; `text.length()` trả số UTF-16 code unit. Nội dung tiếng Việt và emoji minh họa hai kích thước này khác nhau.
- Stream phải được đóng. Nếu quá trình copy thất bại giữa chừng, file `dest` có thể **đã tồn tại nhưng chưa đủ nội dung**; nên có phương án file tạm + commit khi yêu cầu tính toàn vẹn.

Bộ đệm 4 byte chỉ để dễ quan sát và kiểm thử; trong ứng dụng hiệu năng, buffer vài KB hoặc lớn hơn thường phù hợp hơn, tùy nguồn và workload.

### Khi chỉ cần copy nguyên file: `Files.copy`

`Files.copy(source, target)` có thể đơn giản hơn việc tự mở hai stream, đặc biệt khi không cần biến đổi nội dung. `Files.copy` vẫn cần quyết định overwrite, metadata, symlink và các option khi phù hợp.

Nếu cần **lọc, mã hóa, nén, tính checksum từng khối** hoặc ghép dữ liệu theo giao thức, stream hữu ích vì caller có quyền kiểm soát từng đoạn byte. Chỉ nên tự viết loop khi việc kiểm soát đó có giá trị, không phải vì bắt buộc phải làm lại chức năng copy đã có.

## 9. Input/output stream là byte; đọc ghi text cần charset

`Files.newInputStream` trả về `InputStream`; nó **không có `readLine()`** và không giải mã Unicode. `Files.newOutputStream` trả về `OutputStream`; phương thức `write` chỉ ghi byte.

Để đọc text bằng các stream này, bọc qua:

- `InputStreamReader(input, charset)`: **giải mã byte thành UTF-16 `char`**.
- `OutputStreamWriter(output, charset)`: **mã hóa UTF-16 `char` thành byte**.
- `BufferedReader`/`BufferedWriter`: tối ưu thao tác ký tự nhỏ và hỗ trợ đọc dòng/`newLine()`.
- Nếu không cần quyền kiểm soát thấp ở tầng byte, `Files.newBufferedReader(path, charset)` và `Files.newBufferedWriter(path, charset, options...)` thường gọn hơn.

**Không viết `writer.write("Tiếng Việt".getBytes())` theo charset mặc định mơ hồ.** Hãy xác định charset theo format/giao thức, thường là `StandardCharsets.UTF_8`. Charset mặc định có thể thay đổi theo phiên bản Java, cấu hình runtime hoặc API cụ thể; sự rõ ràng tốt hơn việc hy vọng môi trường luôn giống máy phát triển.

### Ví dụ 3 — bọc byte stream bằng reader/writer UTF-8

Tạo tệp `StreamTextBridgeDemo.java`:

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class StreamTextBridgeDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("charset-bridge-");
        Path file = dir.resolve("lines.txt");
        String first = "Xin chào";
        String second = "Java 😊";
        try {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(Files.newOutputStream(
                            file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                            StandardCharsets.UTF_8))) {
                writer.write(first);
                writer.write('\n');
                writer.write(second);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(file),
                            StandardCharsets.UTF_8))) {
                System.out.println("first matches=" + first.equals(reader.readLine()));
                System.out.println("second matches=" + second.equals(reader.readLine()));
                System.out.println("EOF=" + (reader.readLine() == null));
            }
            System.out.println("byte length larger than char count="
                    + (Files.size(file) > (first + "\n" + second).length()));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 StreamTextBridgeDemo.java
java StreamTextBridgeDemo
```

```text
first matches=true
second matches=true
EOF=true
byte length larger than char count=true
```

**Diễn giải:**

1. `newOutputStream` mở file mới, `OutputStreamWriter` mã hóa ký tự theo UTF-8, `BufferedWriter` đệm việc ghi text.
2. Chương trình dùng literal `'\n'`, do đó dấu kết thúc dòng là **LF** bất kể hệ điều hành. Nếu muốn dấu xuống dòng theo nền tảng, có thể chọn `BufferedWriter.newLine()` thay vì `'\n'`.
3. Khi thoát `try-with-resources`, việc đóng `BufferedWriter` hoàn tất ghi dữ liệu đệm và đóng các lớp writer/stream bên dưới. Sau đó mở nguồn để đọc.
4. `InputStreamReader` giải mã đúng UTF-8; `BufferedReader.readLine()` đọc từng dòng và **bỏ dấu kết thúc dòng**. Lần thứ ba trả `null` vì EOF.
5. `Files.size(file)` đo byte; với tiếng Việt và emoji, byte UTF-8 nhiều hơn số UTF-16 code unit của chuỗi Java tương ứng.

**Điều cần phân biệt:** API byte stream không tự đặt newline, không tự chuyển encoding, không xác thực định dạng nghiệp vụ. Việc bọc writer/reader là quyết định của chương trình.

## 10. `try-with-resources`, `close()`, `flush()` và vòng đời file

`InputStream` và `OutputStream` là các kiểu `AutoCloseable`/`Closeable`, nên dùng `try-with-resources` để bảo đảm đóng:

```java
try (InputStream in = Files.newInputStream(source);
     OutputStream out = Files.newOutputStream(
             target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
    in.transferTo(out);
}
```

Đây là **đoạn minh họa**, cần có `Path source` và `Path target`, các import, cùng xử lý `IOException`. `InputStream.transferTo(OutputStream)` (Java 9+) truyền các byte còn lại đến EOF, trả tổng số byte đã chuyển; phương thức **không tự đóng** input hay output, vì vậy khối `try-with-resources` vẫn cần thiết.

### Khi nào `flush()` cần thiết?

`OutputStream.flush()` được dùng để đẩy dữ liệu đang đệm (nếu stream/wrapper có buffering) xuống tầng dưới. Một số stream có implementation `flush` gần như không làm gì; wrapper như `BufferedOutputStream` có dữ liệu đệm cần được đẩy ra.

**`flush()` không đồng nghĩa dữ liệu đã ghi bền vững xuống ổ đĩa**. Nếu ứng dụng yêu cầu durability khi mất điện, phải xem xét filesystem, `FileChannel.force(...)` hoặc `SYNC`/`DSYNC` theo hợp đồng và khả năng provider.

`close()` đóng stream. Khi stream là wrapper sở hữu stream bên dưới, đóng wrapper thường cũng đóng nguồn bên dưới; ví dụ `BufferedOutputStream` hoặc `OutputStreamWriter` bọc `newOutputStream`.

### Nếu ghi lỗi giữa chừng thì sao?

Nếu `OutputStream.write` ném `IOException` sau khi đã ghi một phần, không nên tự báo “file đã ghi thành công”. File đích có thể chỉ có **phần đầu nội dung**. Với file quan trọng, nên ghi vào file tạm, validate hoàn tất, rồi publish theo chiến lược move có kiểm soát.

**Không dựa vào thành công của `close()` như một phép xác thực nội dung.** Thành công ở tầng I/O không thay cho số byte kỳ vọng, checksum, cú pháp file hoặc kiểm tra bản ghi nghiệp vụ.

## 11. Buffering: khi nào nên bọc `BufferedInputStream`/`BufferedOutputStream`?

`Files.newInputStream` và `newOutputStream` cung cấp stream đọc/ghi byte. Khi chương trình đọc/ghi nhiều lần các đoạn nhỏ, buffering có thể giảm số lần gọi xuống hệ thống file:

```java
try (InputStream in = new BufferedInputStream(Files.newInputStream(source));
     OutputStream out = new BufferedOutputStream(Files.newOutputStream(
             target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
    byte[] block = new byte[8192];
    int n;
    while ((n = in.read(block)) != -1) {
        out.write(block, 0, n);
    }
}
```

Đây là **mã minh họa** cần import `BufferedInputStream`, `BufferedOutputStream`, `InputStream`, `OutputStream`, `Files`, `StandardOpenOption` và khai báo `source`/`target`.

- Buffer làm thay đổi **cách tổ chức thao tác I/O**, không thay đổi ý nghĩa byte.
- Ghi bằng `write(buffer, 0, n)` để tránh dữ liệu dư ở cuối mảng.
- Không coi buffer lớn luôn tốt hơn. Với nhiều stream đồng thời, bộ nhớ dành cho buffer cũng là tài nguyên.
- Khi đã dùng `Files.newBufferedReader`/`newBufferedWriter` cho text, API đã cung cấp buffering ký tự; không cần tự thêm nhiều tầng buffer mà không có lý do đo đạc.
- `BufferedInputStream` có khả năng `mark/reset` trong giới hạn phù hợp, khác với việc một `InputStream` bất kỳ đều hỗ trợ tua ngược.

Đối với sao chép nguyên file, có thể thử `Files.copy`, `InputStream.transferTo` hoặc các phương thức channel phù hợp, thay vì mặc định tự viết bộ đệm phức tạp.

## 12. Chọn `newInputStream/newOutputStream` hay API khác trong `Files`?

| Nhu cầu | API thường chọn | Vì sao |
|---|---|---|
| Đọc một file nhỏ thành mảng byte | `Files.readAllBytes(path)` | Gọn, nhưng giữ toàn bộ dữ liệu trong RAM |
| Ghi sẵn một mảng byte | `Files.write(path, bytes, options...)` | Trực tiếp, không tự viết loop |
| Đọc file lớn theo các khối byte | `Files.newInputStream(path)` | Kiểm soát bộ nhớ và từng khối |
| Ghi dữ liệu byte sinh dần | `Files.newOutputStream(path, options...)` | Không cần giữ toàn bộ output trước |
| Sao chép file không biến đổi | `Files.copy(source, target, options...)` | API copy trực tiếp, xử lý tùy chọn rõ |
| Đọc text nhỏ | `Files.readString(path, UTF_8)` | Gọn, charset tường minh |
| Ghi text nhỏ | `Files.writeString(path, text, UTF_8, options...)` | Gọn, có open options |
| Đọc text theo dòng | `Files.newBufferedReader(path, UTF_8)` hoặc `Files.lines` | Text/line API, không phải byte API |
| Ghi text từng dòng | `Files.newBufferedWriter(path, UTF_8, options...)` | Buffered character writer |
| Di chuyển vị trí đọc/ghi | `Files.newByteChannel`/`FileChannel` | Có khả năng position/seek |
| Khóa file hoặc đồng bộ qua channel | `FileChannel` và API tương ứng | Đặc tính vượt quá `InputStream`/`OutputStream` |
| Thao tác file tạm | `Files.createTempFile`, `createTempDirectory` | Giảm tự đặt tên đoán trước |

### Không dùng `newInputStream` cho nhu cầu “đọc bản ghi text”

Một byte trong UTF-8 không phải luôn là một ký tự. Nếu file chứa tiếng Việt và muốn xử lý theo **dòng**, dùng `BufferedReader` hoặc `Files.newBufferedReader`. Nếu đọc byte vì protocol, hãy để parser xác định framing và charset.

### Khi cần đọc-ghi đồng thời, vị trí ngẫu nhiên hoặc khóa file

`OutputStream` chỉ định hướng ghi tuần tự; `InputStream` chỉ định hướng đọc tuần tự. Nếu nghiệp vụ cần mở cùng file để đọc và ghi ở vị trí xác định hoặc cần `seek`, hãy xem `SeekableByteChannel`/`FileChannel` cùng tập `StandardOpenOption` tương ứng. **Không giả định các option mở channel đều có cùng semantics khi dùng với `newInputStream`/`newOutputStream`**.

## 13. File không tồn tại, quyền truy cập và exception

Nhiều tình huống lỗi cần được tách rõ:

- `NoSuchFileException`: một thao tác cần file có sẵn không tìm được mục tiêu.
- `FileAlreadyExistsException`: `CREATE_NEW` gặp đường dẫn đã tồn tại.
- `AccessDeniedException`: provider báo từ chối quyền truy cập.
- `IOException`: ngoại lệ tổng quát cho lỗi file, thiết bị, filesystem.
- `UnsupportedOperationException` / `IllegalArgumentException`: có thể xảy ra với option không được hỗ trợ hoặc kết hợp option không hợp lệ.
- `SecurityException`: có thể phát sinh từ kiểm tra security theo môi trường/provider, dù Java hiện đại đã thay đổi vai trò của Security Manager.

Không nên bắt `IOException` chung rồi trả chuỗi rỗng hoặc `false`, vì caller sẽ mất thông tin “đầu vào trống thật” khác “không thể đọc file”.

Ví dụ xử lý phần lỗi một cách có định hướng (mã minh họa, cần biến `target`):

```java
try (OutputStream out = Files.newOutputStream(
        target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
    out.write(data);
} catch (FileAlreadyExistsException e) {
    // Báo người dùng: không ghi đè file đang có.
} catch (IOException e) {
    // Báo hoặc ghi log nguyên nhân I/O và xử lý thất bại.
}
```

Đoạn này cần `byte[] data` và các import. **Chú ý:** `CREATE_NEW` đảm bảo không thay thế file cũ nhưng nếu lỗi ở giữa quá trình ghi thì file mới có thể đã được tạo và chỉ có một phần dữ liệu; chiến lược dọn file lỗi tùy yêu cầu.

Một số filesystem hoặc provider không thể báo đúng subtype giống nhau, nên vẫn cần fallback xử lý `IOException`.

## 14. Parent directory, loại file và quyền filesystem

Mở output có `CREATE` hoặc `CREATE_NEW` **không tự tạo tất cả thư mục cha**. Nếu `Path.of("reports", "daily", "out.bin")` trỏ tới thư mục `reports/daily` chưa có, `newOutputStream` có thể thất bại dù bạn đã chọn `CREATE`.

Khi được phép tạo cấu trúc thư mục, chủ động:

```java
Path parent = target.toAbsolutePath().getParent();
Files.createDirectories(parent);
try (OutputStream out = Files.newOutputStream(
        target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
    out.write(data);
}
```

Đây là **mã minh họa**; cần khai báo `target`, `data` và xử lý `IOException`. Không phải mọi đường dẫn do người dùng nhập đều được phép tạo thư mục; cần xác thực root và quyền sở hữu trước.

`Files.isRegularFile(path)`, `Files.isDirectory(path)` và `Files.isReadable(path)` có thể giúp giao diện báo trước trạng thái, nhưng **không bảo đảm** file vẫn giữ trạng thái đó đến lúc mở. Hãy xử lý kết quả thật của `newInputStream`/`newOutputStream`.

Trong môi trường có symbolic link, đường dẫn có thể được thay đổi giữa bước kiểm tra và thao tác. Đặc biệt, `CREATE_NEW` bảo vệ điều kiện tên file đích chưa tồn tại tại thời điểm tạo, nhưng không giải quyết toàn bộ vấn đề directory traversal, symlink ở thư mục cha hoặc quyền filesystem.

## 15. `CREATE_NEW`, tránh TOCTOU và bảo vệ dữ liệu hiện có

Một anti-pattern:

```java
if (!Files.exists(target)) {
    try (OutputStream out = Files.newOutputStream(target)) {
        out.write(data);
    }
}
```

Đây là **mã minh họa SAI nếu mục tiêu là không được ghi đè**: sau `exists`, tiến trình khác có thể tạo file. `newOutputStream(target)` mặc định `CREATE + TRUNCATE_EXISTING + WRITE` sẽ cắt ngắn dữ liệu mới tạo đó.

Cách phù hợp hơn khi chỉ muốn tạo mới:

```java
try (OutputStream out = Files.newOutputStream(
        target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
    out.write(data);
}
```

Đây là **mã minh họa đúng về điều kiện tạo mới**, cần có `target`, `data` và xử lý ngoại lệ. `CREATE_NEW` yêu cầu kiểm tra tồn tại và tạo mục mới là một thao tác nguyên tử theo contract. Nó không phải “khóa file toàn thời gian”, không bảo vệ khỏi thay đổi ở thư mục cha và không đảm bảo quá trình ghi thành công toàn bộ.

Khi ứng dụng ghi file quan trọng được công bố cho người khác, nên còn kiểm tra hoàn chỉnh payload, checksum hoặc yêu cầu nghiệp vụ, rồi mới đưa file vào vị trí đích. Đừng xem “đã tạo file thành công” như “đã có dữ liệu hoàn chỉnh”.

## 16. Ghi file tạm rồi công bố: khác biệt giữa truncate và atomic move

`Files.newOutputStream(path)` trực tiếp truncate file cũ. Nếu ứng dụng crash khi mới ghi được một nửa, file đích có thể mất nội dung ban đầu và chỉ còn bản không hoàn chỉnh.

Một chiến lược thực tế cho file cấu hình hoặc artifact quan trọng:

1. Tạo một file tạm trong thư mục phù hợp (`Files.createTempFile`), lý tưởng cùng filesystem với đích.
2. Mở file tạm để ghi đầy đủ nội dung; đóng stream, xử lý lỗi ghi.
3. Kiểm tra kích thước, checksum hoặc parse thử nếu định dạng yêu cầu.
4. Dùng `Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)` khi cần và được filesystem hỗ trợ.
5. Nếu gặp `AtomicMoveNotSupportedException`, quyết định rõ: **từ chối** nếu atomicity bắt buộc, hoặc thực hiện fallback không nguyên tử khi contract cho phép.
6. Dọn file tạm khi thất bại, đồng thời không vô tình xóa bản đã được công bố.

**Cảnh báo:** `ATOMIC_MOVE` không đồng nghĩa `fsync`/durability. Khi phải chống mất dữ liệu do mất điện, cần xem yêu cầu ghi đồng bộ file/thư mục và cơ chế filesystem. Với `ATOMIC_MOVE`, hành vi thay thế một đích đã tồn tại còn có khác biệt provider, nên phải kiểm tra contract khi bảo toàn dữ liệu quan trọng.

Có thể dùng `Files.writeString` vào temp với encoding rõ rồi move, hoặc dùng `newOutputStream` cho payload streaming. Đừng mặc định `REPLACE_EXISTING` hay fallback phá bảo đảm không ghi đè vốn đã được yêu cầu.

## 17. `APPEND` và vấn đề ghi đồng thời

`APPEND` định vị các lượt ghi tới **cuối file** theo semantics provider. Đây là lựa chọn cho nhật ký hoặc dồn nội dung mới vào file đã có. Nhưng có các điểm phải xác định:

- `APPEND` có **tự tạo file không**? Nếu muốn tạo khi file thiếu, truyền `CREATE, APPEND`. Nếu chỉ `APPEND`, có thể thất bại với file chưa tồn tại.
- `APPEND` **không tự chèn newline**. Hai lần ghi `"hello"` và `"world"` có thể thành `"helloworld"` nếu caller không tự quy định dấu phân cách.
- Việc định vị-cuối-file và ghi nội dung khi **nhiều tiến trình/thread** cùng append có phải nguyên tử hay không phụ thuộc filesystem/provider. Không thể tự khẳng định một dòng log được ghi trong nhiều `write` sẽ không bị chen dữ liệu của writer khác.
- Nếu muốn giữ thứ tự bản ghi, có thể cần một writer tập trung, khóa hoặc cơ chế logging/queue chuyên dụng.
- Nếu phải chống mất dữ liệu khi crash, cần thiết kế flush/sync; `APPEND` đơn lẻ không có nghĩa đã lưu bền vững.

Trên một file đã tồn tại, chỉ truyền `APPEND` giúp ghi cuối file; truyền `CREATE, APPEND` giúp tạo nếu thiếu. Không kết hợp `APPEND` với `TRUNCATE_EXISTING`.

## 18. EOF, partial read và incomplete output

`InputStream.read(buffer)` có thể nhận ít hơn kích thước buffer mà không báo lỗi. Vòng lặp đúng chỉ dừng tại `-1` (với yêu cầu length dương); output phải dùng đúng `n` byte đã đọc.

Nếu một **giao thức** yêu cầu header dài cố định hay payload dài N byte, việc đọc đến EOF có thể chưa đủ: ứng dụng phải đếm byte và xác nhận `actual == expected`. `InputStream.readNBytes(n)` có thể trả ít hơn n khi EOF; `DataInputStream.readFully(...)` ném `EOFException` khi không đủ. Đây là điều kiện ở tầng **định dạng**, không phải do chọn `newInputStream`.

Tương tự, `OutputStream.write(...)` có thể ném lỗi sau khi đã ghi một phần. Sau thất bại, không công bố output như một file hợp lệ nếu yêu cầu ghi đủ. Có thể kiểm tra kích thước và integrity sau khi đóng, tùy tình huống.

**`InputStream.available()` không phải phép kiểm tra EOF hay “số byte còn lại trong toàn file”.** Nó chủ yếu cho biết số byte có thể đọc mà không chặn theo đặc tính stream; không sử dụng nó làm điều kiện dừng hoặc kết luận file đã đọc đủ.

## 19. File đang bị sửa trong lúc đọc/ghi

Một stream mở từ file không tự tạo **snapshot bất biến** của nội dung. Nếu tiến trình khác sửa, cắt ngắn hoặc di chuyển file trong lúc thao tác, những gì caller thấy phụ thuộc filesystem, cách mở và thời điểm. Có thể xảy ra đọc thiếu, nội dung thay đổi hoặc exception; không được giả định một lần `Files.newInputStream` đảm bảo toàn bộ bản đọc nhất quán với file ở thời điểm mở.

Nếu cần snapshot hoặc version chính xác, cân nhắc:

- Cơ chế snapshot/immutable version ở tầng ứng dụng.
- Kiểm tra length và hash với giá trị kỳ vọng đáng tin.
- Quy ước không chỉnh sửa file đang được đọc hoặc dùng file lock trong môi trường thích hợp.
- File tạm và publish atomically khi ghi bản mới, với các giới hạn về atomicity/durability như phần trước.

`Files.size(path)` đọc độ dài ở một thời điểm; file có thể thay đổi ngay sau đó. Đừng dùng riêng kết quả `Files.size` để đảm bảo việc đọc sẽ luôn đúng số byte kỳ vọng.

## 20. Các lỗi thường gặp và cách tránh

1. **Quên rằng output mặc định truncate.** `Files.newOutputStream(path)` có thể xóa nội dung file đang có ngay khi mở. Dùng `CREATE_NEW` nếu tuyệt đối không ghi đè.
2. **Nghĩ `WRITE` một mình sẽ thay toàn bộ file.** Nó thường ghi từ đầu nhưng giữ phần đuôi cũ nếu không chọn truncate.
3. **Dùng `APPEND` nhưng mong tự tạo file mới.** Thêm `CREATE` khi hợp đồng yêu cầu tạo nếu thiếu.
4. **Dùng `APPEND` với `TRUNCATE_EXISTING`.** Hai mục đích trái nhau, kết hợp không hợp lệ.
5. **Dùng `CREATE` thay `CREATE_NEW` để tránh ghi đè.** `CREATE` không từ chối file đang tồn tại.
6. **Quên xác thực parent directory.** `CREATE` không tự tạo mọi thư mục cha; `Files.createDirectories` chỉ dùng sau khi xác minh được phép tạo.
7. **Quên đóng stream.** File descriptor/handle có thể rò rỉ; dùng `try-with-resources`.
8. **Quên `flush`/`close` của buffered writer.** Dữ liệu còn đệm có thể chưa được chuyển xuống dưới. Dùng đúng vòng đời sở hữu wrapper.
9. **Nghĩ `flush` là ghi bền vững xuống đĩa.** Durability cần cơ chế riêng; `SYNC`/`DSYNC` không đồng nghĩa mọi hệ thống đều đảm bảo giống nhau.
10. **Gọi `out.write(buffer)` sau short read.** Phần tử cũ chưa cập nhật trong buffer có thể bị ghi nhầm; dùng `write(buffer, 0, n)`.
11. **Dùng `newInputStream` đọc text rồi cast mỗi byte thành `char`.** UTF-8 có ký tự nhiều byte; giải mã bằng `InputStreamReader` hoặc `Files.newBufferedReader`.
12. **Dùng charset mặc định vô thức.** Chọn `StandardCharsets.UTF_8` hoặc charset do format quy định.
13. **Đọc cả file lớn bằng `Files.readAllBytes` mà không đo memory.** Streaming theo khối thường hợp lý hơn.
14. **Dùng `available() == 0` làm EOF.** Không an toàn; đọc đến `-1` hoặc dùng framing/length của format.
15. **Coi stream I/O là random access.** Cần `SeekableByteChannel`/`FileChannel` nếu phải di chuyển vị trí.
16. **Đặt `try-with-resources` nhưng không xử lý exception.** Đóng tự động không biến lỗi I/O thành thành công; vẫn phải xử lý `IOException`.
17. **Tưởng output stream tự ghi atomically.** File có thể mới chỉ được ghi một phần khi lỗi xảy ra.
18. **Tưởng `CREATE_NEW` giải quyết mọi symlink race.** Nó chỉ cam kết điều kiện tạo mới ở tên mục tiêu; đường dẫn cha và môi trường vẫn cần bảo vệ.
19. **Không kiểm thử trên filesystem thực tế.** Permissions, đặc tính provider, symbolic link và nguyên tử của append/move có thể khác nền tảng.
20. **Dùng một bộ OpenOption cho mọi API.** `newInputStream`, `newOutputStream` và `newByteChannel` có tập option/contract khác nhau.

## 21. Checklist trước khi triển khai

| Câu hỏi | Nếu câu trả lời là... | Chọn hoặc kiểm tra |
|---|---|---|
| Dữ liệu cần là byte hay text? | Text | Reader/writer với charset rõ hoặc `Files.readString/writeString` |
| File input phải có sẵn? | Có | `newInputStream`, xử lý thiếu/quyền truy cập |
| Output được phép ghi đè? | Không | `CREATE_NEW`, xử lý `FileAlreadyExistsException` |
| Output phải thay toàn bộ nội dung cũ? | Có | `TRUNCATE_EXISTING + WRITE`; cân nhắc file tạm nếu quan trọng |
| Output phải ghi thêm? | Có | `APPEND`; thêm `CREATE` nếu cho phép tạo mới |
| File lớn hoặc dữ liệu phát sinh từng phần? | Có | `newInputStream`/`newOutputStream` + block/transfer |
| Muốn đọc/ghi tại vị trí bất kỳ? | Có | `SeekableByteChannel`/`FileChannel` |
| Nhiều writer cùng ghi? | Có | Xác định chiến lược concurrency và bản ghi nguyên tử |
| Yêu cầu durability khi mất điện? | Có | Xem `SYNC`/`DSYNC`, force, filesystem và chiến lược recovery |
| Yêu cầu công bố bản hoàn chỉnh? | Có | File tạm, validate, move/publish có kiểm soát |
| Có đường dẫn do người dùng cung cấp? | Có | Validate root, symlink, quyền, race và giới hạn ghi |
| Kết quả cần đúng N byte? | Có | Đếm byte, kiểm tra EOF/integrity; không chỉ trông chờ stream đóng |

## 22. Tự kiểm tra kiến thức

**Câu 1.** Gọi `Files.newOutputStream(path)` khi file đã có dữ liệu, nhưng chưa gọi `write`. Nội dung file cũ có thể đã bị ảnh hưởng chưa?  
**Đáp án:** có. Mặc định output dùng `TRUNCATE_EXISTING` và có thể xóa nội dung khi mở thành công.

**Câu 2.** File ban đầu có `ABCDEF`, mở với `StandardOpenOption.WRITE` rồi ghi hai byte `XY`. Kết quả điển hình là gì?  
**Đáp án:** `XYCDEF` (mở ở đầu file, không tự truncate); khác với mở mặc định.

**Câu 3.** Muốn tạo file mới mà **không ghi đè** mục đã tồn tại, chọn `CREATE` hay `CREATE_NEW`?  
**Đáp án:** `CREATE_NEW`, vì nó từ chối khi đường dẫn đã tồn tại và điều kiện kiểm tra-tạo là nguyên tử theo contract.

**Câu 4.** `Files.newInputStream(path)` có tạo file nếu file thiếu không?  
**Đáp án:** không. Nó là API mở để đọc; caller phải xử lý lỗi thiếu nguồn.

**Câu 5.** Với output, có thể kết hợp `APPEND` và `TRUNCATE_EXISTING` để “xóa rồi nối” không?  
**Đáp án:** không. Hai option mâu thuẫn, không phải cách mở hợp lệ. Nếu muốn thay nội dung, dùng truncate; nếu muốn nối, dùng append.

**Câu 6.** Đọc file tiếng Việt bằng `Files.newInputStream` có tự trả `String` không?  
**Đáp án:** không. API trả byte stream; cần biết charset và giải mã khi muốn text.

**Câu 7.** Vòng lặp `read(buffer)` trả 7 trong khi buffer dài 4096. Có nên ghi cả mảng sang output không?  
**Đáp án:** không. Chỉ ghi `write(buffer, 0, 7)`.

**Câu 8.** Đóng stream có đảm bảo file output có đủ payload theo protocol và bền vững sau mất điện không?  
**Đáp án:** không. Cần kiểm tra số byte/format/integrity và cơ chế đồng bộ phù hợp với yêu cầu durability.

**Câu 9.** Với file lớn, khi nào `Files.readAllBytes` kém phù hợp hơn `newInputStream`?  
**Đáp án:** khi không muốn giữ toàn file trong RAM, cần xử lý từng khối hoặc truyền dữ liệu qua pipeline streaming.

**Câu 10.** Cần mở file để đọc/ghi theo vị trí ngẫu nhiên, có nên chọn `InputStream`/`OutputStream` làm API chính không?  
**Đáp án:** thường không. Dùng `SeekableByteChannel` hoặc `FileChannel` cho thao tác position/seek.

## Kết luận

**`Files.newInputStream(path)`** mặc định mở file **để đọc**, không tạo file khi thiếu. **`Files.newOutputStream(path)`** mặc định **`CREATE + TRUNCATE_EXISTING + WRITE`**, nên có thể tạo file mới **hoặc xóa nội dung cũ khi mở**. Khi cần hành vi khác, hãy truyền `StandardOpenOption` tường minh: `CREATE_NEW` để không ghi đè, `APPEND` để nối vào cuối, `CREATE` nếu cho phép tạo, `WRITE` và `TRUNCATE_EXISTING` khi thay nội dung có chủ đích.

Chọn byte stream cho dữ liệu nhị phân hoặc streaming dung lượng lớn, kết hợp reader/writer và charset rõ khi xử lý text. Luôn dùng `try-with-resources`, kiểm tra read/write lỗi, phân biệt flush với durability, và không đánh đồng mở stream thành công với tính nguyên vẹn của file cuối cùng.

### Tài liệu Java SE 17

- [`Files` — `newInputStream` và `newOutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [`StandardOpenOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html)
- [`OpenOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/OpenOption.html)
- [`InputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html)
- [`OutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html)
- [`BufferedInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedInputStream.html) và [`BufferedOutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedOutputStream.html)
- [`InputStreamReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html) và [`OutputStreamWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStreamWriter.html)
- [`FileChannel`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html), [`SeekableByteChannel`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SeekableByteChannel.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="files-read-write-007">READ, WRITE, CREATE, CREATE_NEW và TRUNCATE_EXISTING</a>

<details>
<summary>Xem chi tiết</summary>

# READ, WRITE, CREATE, CREATE_NEW và TRUNCATE_EXISTING trong Java Files I/O

> **Bối cảnh:** Java Core → I/O → **Đọc ghi bằng Files**. Bài này giải thích các `StandardOpenOption` quan trọng khi mở tệp, sự khác biệt giữa đọc và ghi, tạo mới có hoặc không ghi đè, cắt nội dung tệp đã tồn tại và các giá trị **mặc định** của `Files`. Ví dụ độc lập dùng Java 17, tạo và dọn tệp tạm để không can thiệp vào dữ liệu thật.

## 1. Vì sao chế độ mở tệp quan trọng?

Một câu lệnh “ghi tệp” có thể mang các ý nghĩa hoàn toàn khác nhau:

- Chỉ ghi vào **tệp đã có**, nhưng không xóa phần đuôi còn lại.
- Tạo tệp nếu chưa có và ghi; nếu đã có thì ghi đè từ đầu.
- Tạo tệp nếu chưa có, nhưng **tuyệt đối từ chối** khi đường dẫn đã tồn tại.
- Mở tệp đã có rồi **xóa trắng trước khi ghi**.
- Chỉ đọc dữ liệu, **không** có quyền ghi và không thay đổi nội dung.

Trong Java NIO.2, các lựa chọn này được thể hiện bằng `java.nio.file.StandardOpenOption`, một enum triển khai giao diện `OpenOption`. Chúng thường là các đối số varargs truyền cho `Files.newInputStream`, `Files.newOutputStream`, `Files.write`, `Files.writeString` và những API mở channel khác. Mỗi API có **hợp đồng riêng**: không phải option nào cũng hợp lệ, và **mặc định khi bỏ qua options có thể khác nhau**.

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

Path path = Path.of("data.txt");
// Đây chỉ là fragment; đoạn sau phải ở trong method có xử lý IOException.
// Files.writeString(path, "hello",
//         StandardOpenOption.CREATE, StandardOpenOption.WRITE);
~~~

`Path.of(...)` chỉ tạo một đối tượng định danh đường dẫn; `Files.writeString(...)` mới thực hiện I/O. Hầu hết thao tác ghi có thể sửa dữ liệu vĩnh viễn, nên phải quyết định chế độ mở **trước** khi thao tác.

### 1.1. Năm option trọng tâm

| Option | Ý nghĩa khi dùng đúng ngữ cảnh | Điều **không** có nghĩa |
| --- | --- | --- |
| `READ` | Mở quyền **đọc** các byte trong tệp | Không tự tạo tệp khi thiếu, không tự cho phép ghi. |
| `WRITE` | Mở quyền **ghi** các byte vào tệp | Không tự tạo tệp hoặc xóa trắng nội dung nếu không có option tương ứng. |
| `CREATE` | **Nếu thiếu** thì tạo tệp; **nếu đã có** thì mở tệp đó | Không tự yêu cầu từ chối ghi đè và không đồng nghĩa truncate. |
| `CREATE_NEW` | Chỉ tạo tệp **mới**; nếu mục tiêu đã tồn tại, thao tác mở thất bại | Không phải “ghi đè tệp với nội dung mới”; không cho phép thay thế tên đang tồn tại. |
| `TRUNCATE_EXISTING` | Khi mở tệp đã tồn tại **để ghi**, đặt độ dài về **0** ngay ở bước mở | Không phải xóa file khỏi thư mục, không dùng để append và bị bỏ qua khi chỉ mở READ. |

**Mẹo ghi nhớ:** `WRITE` trả lời “được sửa byte không?”, `CREATE` trả lời “thiếu thì có được tạo không?”, `CREATE_NEW` trả lời “đã tồn tại thì phải thất bại không?”, `TRUNCATE_EXISTING` trả lời “đã tồn tại thì có xóa trắng ngay khi mở không?”. `READ` quyết định khả năng đọc và có thể đi cùng `WRITE` trên các API channel hỗ trợ cả hai chiều.

## 2. `READ` — chỉ đọc dữ liệu

Mở luồng đọc chuẩn:

~~~java
// Fragment nằm trong method throws IOException:
try (InputStream in = Files.newInputStream(path)) {
    int firstByte = in.read();
}
~~~

Cần import `java.io.InputStream`, `java.nio.file.Files` và `java.nio.file.Path`. Không truyền option nào vào `Files.newInputStream(path)` nghĩa là **mở để đọc**. Bạn cũng có thể viết `Files.newInputStream(path, StandardOpenOption.READ)`. Nếu tệp không có, thường nhận `NoSuchFileException`; nếu không có quyền đọc hoặc xảy ra lỗi khác, nhận một dạng `IOException` thích hợp.

`READ` **không tạo tệp**. Đừng mong `Files.newInputStream(path, CREATE)` trở thành “mở để đọc, nếu chưa có thì tạo rỗng”; input-stream factory không có nghĩa đó. Những option không được API hỗ trợ có thể gây `UnsupportedOperationException` hoặc kết hợp sai gây `IllegalArgumentException`.

Khi đọc text có charset rõ ràng:

~~~java
// Fragment Java 11+: method throws IOException.
String content = Files.readString(path, StandardCharsets.UTF_8);
~~~

`Files.readString` tự mở/đọc/đóng file nội bộ, không nhận `StandardOpenOption...` tùy ý. Muốn đọc theo nhiều chế độ, phải chọn API mở stream/channel có options thích hợp.

### 2.1. READ không làm thay đổi file

Ở tầng `SeekableByteChannel` hoặc `FileChannel`, một channel mở **chỉ READ** không cho phép write; gọi `write` có thể ném `NonWritableChannelException`. `TRUNCATE_EXISTING` bị **bỏ qua khi mở chỉ READ** theo contract của channel. Điều này không có nghĩa dùng READ+TRUNCATE để xóa file; không hề xóa nội dung nếu đó là channel chỉ đọc.

## 3. `WRITE` — cho phép ghi, nhưng không mặc nhiên tạo hoặc truncate

Ví dụ chỉ mở để ghi tệp **đã tồn tại**:

~~~java
// Fragment; method throws IOException.
try (OutputStream out = Files.newOutputStream(
        path, StandardOpenOption.WRITE)) {
    out.write("XY".getBytes(StandardCharsets.UTF_8));
}
~~~

Cần import `java.io.OutputStream` và `java.nio.charset.StandardCharsets`. Với tệp ban đầu chứa ASCII `ABCDE`, nếu ghi `XY` từ vị trí đầu mà **không** truncate, nội dung có thể thành `XYCDE`. Cùng nguyên tắc áp dụng khi dùng `FileChannel`: khi mở thông thường, vị trí ghi bắt đầu ở đầu file, chứ **không tự đến cuối**.

Điều cần nhớ:

- `WRITE` **không tương đương** `CREATE`: nếu file thiếu và không yêu cầu tạo, mở để ghi có thể thất bại với `NoSuchFileException`.
- `WRITE` **không tương đương** `TRUNCATE_EXISTING`: nếu bạn ghi chuỗi ngắn hơn byte cũ, phần dư cũ **có thể vẫn còn**.
- `WRITE` **không đồng nghĩa append**: muốn viết tiếp cuối file dùng `APPEND` ở các API hỗ trợ hoặc đặt vị trí bằng channel. `APPEND` có quy tắc kết hợp riêng.
- Độ dài một lần gọi `write` **không nói** chắc đã ghi hết trong API channel: `SeekableByteChannel.write(ByteBuffer)` có thể ghi số byte nhỏ hơn số được yêu cầu; với channel cần lặp đến khi `ByteBuffer.hasRemaining() == false`. `OutputStream.write(byte[])` thông thường ghi tất cả byte của mảng hoặc ném IOException.
- `WRITE` không quyết định charset của văn bản. Với `byte[]`, caller encode; với `Files.writeString`, bạn chỉ định charset hoặc theo mặc định API đó.

## 4. `CREATE` — tạo nếu thiếu, dùng tệp hiện có nếu đã tồn tại

`CREATE` thường đi cùng `WRITE`:

~~~java
// Fragment: tạo tệp thiếu, hoặc ghi từ đầu tệp đã tồn tại.
try (OutputStream out = Files.newOutputStream(
        path, StandardOpenOption.CREATE,
        StandardOpenOption.WRITE)) {
    out.write("hi".getBytes(StandardCharsets.UTF_8));
}
~~~

Nếu tệp chưa tồn tại và có quyền, nó được tạo. Nếu tệp đã tồn tại, nó được mở cho ghi. **Chỉ `CREATE` và `WRITE` không buộc truncate**; khi ghi ngắn hơn nội dung cũ, phần đuôi có thể còn như ví dụ `XYCDE`.

`CREATE` không làm cha tự xuất hiện: nếu `path` là `logs/2026/app.log` mà thư mục cha `logs/2026` không tồn tại, việc tạo file vẫn có thể thất bại. Khi ứng dụng có trách nhiệm tạo thư mục, dùng `Files.createDirectories(path.getParent())` sau khi xác định parent khác `null` và kiểm tra chính sách đường dẫn.

**Khi nào chọn?** Một ứng dụng ghi định kỳ vào một tệp cố định **được phép đã tồn tại** có thể chọn `CREATE + WRITE + TRUNCATE_EXISTING` nếu muốn thay toàn bộ nội dung. Nếu muốn giữ phần cũ và thêm bản ghi, dùng `CREATE + APPEND` theo contract API. Nếu **không** cho phép tên trùng, dùng `CREATE_NEW` thay vì `CREATE`.

## 5. `CREATE_NEW` — tạo mới độc quyền và kiểm tra tồn tại nguyên tử

`CREATE_NEW` được dùng khi **không muốn ghi đè bất kỳ entry nào có sẵn** tại tên đích:

~~~java
// Fragment: method throws IOException.
Files.writeString(
        path, "new-data", StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
~~~

Nếu tên đích đã tồn tại, bao gồm trường hợp chính entry đó là **symbolic link**, việc mở với `CREATE_NEW` phải thất bại. Thường nhận `FileAlreadyExistsException`, tuy Javadoc cho phép IOException phù hợp tùy provider; không nên bắt tất cả IOException rồi tự cho rằng đó là “tệp đã tồn tại”.

**Tại sao `CREATE_NEW` tốt hơn đoạn `if (!Files.exists(path)) ...`?** Bởi kiểm tra tồn tại rồi tạo thành **hai thao tác**, có cửa sổ race giữa chúng. `CREATE_NEW` đưa yêu cầu “chỉ tạo nếu chưa tồn tại” vào một thao tác mở/tạo có kiểm tra và tạo **atomic đối với các thao tác filesystem khác** theo contract.

~~~java
// PHẢN VÍ DỤ DỄ RACE; không dùng làm bảo đảm không ghi đè.
if (!Files.exists(path)) {
    Files.writeString(path, content);
    // Tên đích có thể được tạo bởi thread/process khác giữa hai dòng.
}
~~~

Thay bằng:

~~~java
// Fragment: content là String do caller chuẩn bị.
try {
    Files.writeString(
        path, content, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
} catch (FileAlreadyExistsException ex) {
    // Quy tắc nghiệp vụ: báo tên trùng, đổi tên, hoặc từ chối yêu cầu.
}
~~~

Các đoạn trên cần import `java.nio.file.FileAlreadyExistsException`, `java.nio.file.Files` và các kiểu liên quan. Nếu một provider không dùng chính subclass `FileAlreadyExistsException` cho tình huống này, vẫn phải xử lý IOException tại ranh giới I/O.

### 5.1. Atomic creation không phải atomic content replacement

`CREATE_NEW` chỉ bảo đảm **bước kiểm tra tên và tạo entry mới** là atomic theo contract. Nó **không** bảo đảm toàn bộ dữ liệu ghi sau đó xuất hiện đồng thời, không bảo đảm crash-safe, không fsync và không là transaction. Một exception sau khi tạo tệp có thể để lại tệp **đã tồn tại nhưng chỉ chứa một phần dữ liệu**. Với dữ liệu quan trọng, thiết kế cleanup cho file mới tạo thất bại và giao thức publish nội dung hoàn chỉnh.

### 5.2. Kết hợp `CREATE` và `CREATE_NEW`?

Khi cả hai cùng hiện diện, `CREATE_NEW` **ưu tiên**, `CREATE` bị bỏ qua theo API. Không phải cách “vừa tạo mới vừa ghi đè nếu có”: đã tồn tại vẫn thất bại. Chỉ viết `CREATE_NEW` là rõ ý định hơn.

## 6. `TRUNCATE_EXISTING` — xóa trắng nội dung cũ trước khi ghi

`TRUNCATE_EXISTING` dùng với quyền ghi để đặt chiều dài tệp hiện có về **0 byte ngay khi mở**:

~~~java
// Fragment: muốn thay nội dung file đã tồn tại, không cho tạo file thiếu.
try (OutputStream out = Files.newOutputStream(
        path, StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING)) {
    out.write("OK".getBytes(StandardCharsets.UTF_8));
}
~~~

Nếu file trước đó là `ABCDE`, sau thao tác thành công nội dung là `OK`, không phải `OKCDE`. Nếu file chưa tồn tại, việc không có `CREATE` hoặc `CREATE_NEW` có thể dẫn tới `NoSuchFileException`.

Kết hợp phổ biến để **tạo nếu chưa có, thay toàn bộ nếu có**:

~~~java
Files.writeString(
    path, "full replacement", StandardCharsets.UTF_8,
    StandardOpenOption.CREATE,
    StandardOpenOption.WRITE,
    StandardOpenOption.TRUNCATE_EXISTING);
~~~

**Rủi ro lớn:** truncate diễn ra **khi mở**, trước khi bạn viết hết dữ liệu mới. Nếu đọc nguồn lỗi, process bị dừng, hết dung lượng ổ đĩa hoặc writer thất bại, dữ liệu cũ **đã có thể bị mất**. Vì vậy `CREATE+TRUNCATE_EXISTING` không phải cơ chế cập nhật an toàn tuyệt đối cho file quan trọng.

**TRUNCATE_EXISTING không phải DELETE:** tên file và entry còn tồn tại, chỉ size/contents bị thay đổi. Khi mở chỉ đọc bằng channel, tùy chọn này **bị bỏ qua**; đừng mong `READ + TRUNCATE_EXISTING` cắt tệp.

## 7. Ma trận tình huống theo ý định nghiệp vụ

Giả định đối tượng đích là file thường trên provider hỗ trợ các option chuẩn, không có xung đột quyền hoặc I/O error khác.

| Mong muốn | Ví dụ API và options | Nếu tệp chưa có | Nếu tệp đã có |
| --- | --- | --- | --- |
| Chỉ đọc | `Files.newInputStream(path, READ)` | Thất bại | Đọc, không ghi |
| Ghi đè từ đầu **không xóa phần đuôi** | `newOutputStream(path, WRITE)` | Thất bại | Ghi bắt đầu từ đầu; phần dư có thể còn |
| Tạo nếu thiếu, ghi từ đầu không mặc định truncate | `newOutputStream(path, CREATE, WRITE)` | Tạo mới | Ghi đè từ đầu, có thể còn đuôi |
| Chỉ tạo tên mới, chống trùng | `newOutputStream(path, CREATE_NEW, WRITE)` | Tạo mới | Thất bại |
| Chỉ ghi đè hoàn toàn file đang tồn tại | `newOutputStream(path, WRITE, TRUNCATE_EXISTING)` | Thất bại | Xóa size về 0 trước khi ghi |
| Tạo nếu thiếu hoặc thay nội dung nếu đã có | `newOutputStream(path, CREATE, WRITE, TRUNCATE_EXISTING)` | Tạo mới | Truncate rồi ghi |
| Đọc và ghi qua một channel | `newByteChannel(path, EnumSet.of(READ,WRITE))` | Thất bại | Có thể đọc/ghi, không tự truncate |
| Mặc định ghi ngắn gọn | `Files.writeString(path, text, UTF_8)` | Tạo mới | Truncate rồi ghi |
| Thêm nội dung vào cuối | `Files.writeString(path, text, UTF_8, WRITE, APPEND)` | Thất bại nếu thiếu CREATE | Giữ dữ liệu cũ rồi append |

**Chú ý:** hàng cuối dùng `APPEND` như một đối chiếu, không phải một trong năm option chủ đề. Khi muốn “thêm nếu thiếu cũng tạo”, truyền thêm `CREATE`. Một lời gọi APPEND giữa nhiều process/thread **không bảo đảm** toàn bộ thông điệp của mỗi bên là một đơn vị atomic; xem hợp đồng provider và phối hợp tương ứng.

### 7.1. Những tổ hợp đáng nhớ

- `CREATE_NEW + WRITE`: chỉ tạo mới; đã có thì lỗi. Không cần thêm `TRUNCATE_EXISTING` vì file mới vốn chưa có nội dung.
- `CREATE + WRITE + TRUNCATE_EXISTING`: thao tác “create-or-replace” phổ biến nhưng **không an toàn khi write nửa chừng**.
- `WRITE` **một mình**: chỉ mở tệp tồn tại để ghi, không tự truncate.
- `READ + WRITE`: thường dùng với `FileChannel` hoặc `SeekableByteChannel`, **không** phải cho `Files.newInputStream` hoặc `newOutputStream`.
- `READ + TRUNCATE_EXISTING` (channel chỉ đọc): truncate bị bỏ qua.
- `APPEND + TRUNCATE_EXISTING`: kết hợp không hợp lệ ở channel chuẩn.
- `APPEND + READ`: kết hợp không hợp lệ ở channel chuẩn.
- `CREATE + CREATE_NEW`: `CREATE_NEW` thắng, `CREATE` bị bỏ qua.
- `CREATE`/`CREATE_NEW` trong context **chỉ đọc** của `newByteChannel`: bị bỏ qua theo contract, không tự tạo tệp chỉ để đọc.

## 8. Các mặc định quan trọng trong `Files`

Một điểm rất dễ gây mất dữ liệu là mặc định **khác nhau theo method**. Không đọc tên `Files` rồi suy luận rằng tất cả đều giống nhau.

| Method | Khi không truyền options | Ai đóng tài nguyên? |
| --- | --- | --- |
| `Files.newInputStream(path)` | Mở để **READ** | Caller đóng `InputStream`, thường trong try-with-resources |
| `Files.newOutputStream(path)` | **CREATE + TRUNCATE_EXISTING + WRITE** | Caller đóng `OutputStream` |
| `Files.write(path, bytes)` | **CREATE + TRUNCATE_EXISTING + WRITE** | API tự đóng stream nội bộ |
| `Files.writeString(path, text)` (Java 11+) | **CREATE + TRUNCATE_EXISTING + WRITE** | API tự đóng stream/writer nội bộ |
| `Files.write(path, lines, charset)` | Mặc định **CREATE + TRUNCATE_EXISTING + WRITE** | API tự đóng |
| `Files.newByteChannel(path)` | **READ** nếu không có READ/WRITE/APPEND | Caller đóng channel |
| `FileChannel.open(path)` | Theo quy tắc `FileChannel.open`: mặc định **READ** | Caller đóng `FileChannel` |

**Lưu ý then chốt:** Khi bạn **truyền danh sách options rõ ràng**, các lựa chọn **mặc định cho lời gọi không options không tự được cộng thêm**. Ví dụ: nếu code cũ dùng `Files.writeString(path, text)` rồi bạn sửa thành `Files.writeString(path, text, UTF_8, WRITE)`, bạn đã chỉ định **WRITE**, không còn yêu cầu tạo mới hoặc truncate như mặc định. Nếu file đang tồn tại chứa nội dung dài, kết quả có thể khác hoàn toàn.

Các API xử lý văn bản dùng overload khác nhau: ví dụ `Files.writeString(path, text, charset, options...)` là hợp lệ từ Java 11; `Files.write(path, byte[], options...)` nhận mảng byte; các overload ghi dòng có thêm charset và iterable. Khi cần đọc byte một chiều, đừng vô tình mở output stream chỉ để “lấy access vào file”.

## 9. Ví dụ hoàn chỉnh 1: quan sát READ, WRITE, CREATE_NEW, TRUNCATE_EXISTING

Lưu thành **`FileOpenModesDemo.java`**:

~~~java
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileOpenModesDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("open-options-");
        Path file = dir.resolve("sample.txt");
        try {
            // READ không tạo file thiếu.
            try (InputStream in = Files.newInputStream(
                    file, StandardOpenOption.READ)) {
                in.read();
            } catch (NoSuchFileException expected) {
                System.out.println("readMissing=true");
            }

            // CREATE_NEW: chỉ tạo khi chưa có, và không overwrite.
            Files.writeString(
                    file, "ABCDE", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

            try {
                Files.writeString(
                        file, "FAIL", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException expected) {
                System.out.println("exclusiveCreate=true");
            }

            // WRITE không bao gồm truncate khi option được chỉ định rõ.
            try (OutputStream out = Files.newOutputStream(
                    file, StandardOpenOption.WRITE)) {
                out.write("XY".getBytes(StandardCharsets.UTF_8));
            }
            System.out.println("afterWriteOnly="
                    + Files.readString(file, StandardCharsets.UTF_8));

            // TRUNCATE_EXISTING cắt file trước khi ghi.
            Files.writeString(
                    file, "OK", StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("afterTruncate="
                    + Files.readString(file, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

~~~bash
javac --release 17 FileOpenModesDemo.java
java FileOpenModesDemo
~~~

**Stdout đã kiểm tra:**

~~~text
readMissing=true
exclusiveCreate=true
afterWriteOnly=XYCDE
afterTruncate=OK
~~~

**Phân tích:**

1. Không tạo trước file, nên `newInputStream(READ)` báo `NoSuchFileException`.
2. `CREATE_NEW+WRITE` tạo file `ABCDE` thành công lần đầu; lần hai thất bại mà không ghi `FAIL` lên file.
3. `newOutputStream(WRITE)` bắt đầu từ vị trí đầu và ghi hai byte `XY`. File ban đầu có 5 byte; không truncate nên còn `CDE` ở cuối.
4. `WRITE+TRUNCATE_EXISTING` làm chiều dài file về 0 khi mở rồi ghi `OK`, vì vậy kết quả chỉ là `OK`.
5. Tệp và directory tạm được dọn trong `finally`. Đây là demo sử dụng file tạm mới, không đụng vào file công việc.

Đây là ví dụ ASCII để so số byte trực quan. Nếu thay bằng Unicode UTF-8, **độ dài sau ghi** phụ thuộc số byte sau mã hóa, không bằng `String.length()`. Khi ghi byte giữa một chuỗi UTF-8 nhiều byte, bạn có thể tạo văn bản không hợp lệ; cần tránh sửa các offset tùy ý trong file text.

## 10. Ví dụ hoàn chỉnh 2: mặc định của Files.writeString và newOutputStream

Lưu thành **`DefaultOpenOptionsDemo.java`**:

~~~java
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DefaultOpenOptionsDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("default-write-", ".txt");
        try {
            Files.writeString(file, "long-data", StandardCharsets.UTF_8);

            // Không options: CREATE + TRUNCATE_EXISTING + WRITE.
            Files.writeString(file, "hi", StandardCharsets.UTF_8);
            System.out.println("defaultWrite="
                    + Files.readString(file, StandardCharsets.UTF_8));

            // OutputStream không options cũng truncate file tồn tại.
            try (OutputStream out = Files.newOutputStream(file)) {
                out.write("OK".getBytes(StandardCharsets.UTF_8));
            }
            System.out.println("defaultOutput="
                    + Files.readString(file, StandardCharsets.UTF_8));

            // APPEND được chỉ định; không dùng TRUNCATE_EXISTING.
            Files.writeString(
                    file, "!", StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
            System.out.println("appended="
                    + Files.readString(file, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

~~~bash
javac --release 17 DefaultOpenOptionsDemo.java
java DefaultOpenOptionsDemo
~~~

**Stdout đã kiểm tra:**

~~~text
defaultWrite=hi
defaultOutput=OK
appended=OK!
~~~

Cần quan sát `Files.writeString(file, "hi", UTF_8)` không giữ phần đuôi `ng-data` của nội dung cũ: **mặc định là truncate**. `Files.newOutputStream(file)` cũng tương tự. Cuối cùng, `WRITE+APPEND` thêm `!` vào tệp đang tồn tại; nếu muốn hoạt động cả khi chưa có file, hãy xét thêm `CREATE`.

**Đừng nhầm** giá trị mặc định của `Files.writeString` với `Files.newByteChannel`: không options ở channel thường là **READ**, chứ không phải CREATE+TRUNCATE+WRITE. Bảng ở mục 8 dùng để tra cứu khi chuyển qua API khác.

## 11. `READ + WRITE` trên channel: một handle hai chiều

`Files.newByteChannel` nhận tập `OpenOption` và trả `SeekableByteChannel`, có thể đọc/ghi và di chuyển vị trí nếu mở đủ quyền:

~~~java
try (SeekableByteChannel channel = Files.newByteChannel(
        path, EnumSet.of(StandardOpenOption.READ,
                         StandardOpenOption.WRITE))) {
    // Đọc bytes bằng channel.read(ByteBuffer).
    // Ghi bytes bằng channel.write(ByteBuffer).
    // channel.position(...) để điều chỉnh offset.
}
~~~

Đây là **fragment**; cần import `java.nio.channels.SeekableByteChannel`, `java.util.EnumSet` và `IOException`. `FileChannel.open(path, READ, WRITE)` là lựa chọn khác nếu cần các API riêng của FileChannel. Không nhầm quyền đọc+ghi với “đọc rồi ghi tiếp ở cuối”: vị trí channel được duy trì, thay đổi theo read/write, có thể đặt bằng `position(long)`.

### 11.1. Ví dụ hoàn chỉnh 3: một channel đọc và ghi không truncate

Lưu thành **`FileChannelOptionsDemo.java`**:

~~~java
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

public class FileChannelOptionsDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("open-channel-", ".txt");
        try {
            Files.writeString(file, "ABCDE", StandardCharsets.UTF_8);

            try (SeekableByteChannel channel = Files.newByteChannel(
                    file, EnumSet.of(
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE))) {

                ByteBuffer buffer = ByteBuffer.allocate(2);
                channel.read(buffer);
                buffer.flip();
                System.out.println("prefix="
                        + StandardCharsets.UTF_8.decode(buffer));

                channel.position(2);
                channel.write(ByteBuffer.wrap(
                        "zz".getBytes(StandardCharsets.UTF_8)));
            }

            System.out.println("readWrite="
                    + Files.readString(file, StandardCharsets.UTF_8));

            // TRUNCATE_EXISTING bị bỏ qua với channel chỉ READ.
            try (SeekableByteChannel channel = Files.newByteChannel(
                    file, EnumSet.of(
                            StandardOpenOption.READ,
                            StandardOpenOption.TRUNCATE_EXISTING))) {
                System.out.println("readOnlySize=" + channel.size());
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

~~~bash
javac --release 17 FileChannelOptionsDemo.java
java FileChannelOptionsDemo
~~~

**Stdout đã kiểm tra:**

~~~text
prefix=AB
readWrite=ABzzE
readOnlySize=5
~~~

Hãy tách nghĩa của từng thao tác: channel đọc 2 byte `AB`; `position(2)` đưa offset tới byte thứ ba (zero-based); ghi `zz` đè `CD` mà không truncate, giữ `E`. Khi chỉ mở `READ+TRUNCATE_EXISTING`, truncate bị bỏ qua theo hợp đồng channel, size vẫn bằng 5.

Đối với một buffer có thể cần nhiều lần ghi, production code phải lặp:

~~~java
// Fragment: channel đã mở WRITE và buffer có dữ liệu.
while (buffer.hasRemaining()) {
    channel.write(buffer);
}
~~~

Nếu channel là non-blocking trong hệ thống khác, cần xem thêm hợp đồng về tiến triển khi trả 0; đối với file channel thông thường, đây là khuôn mẫu xử lý partial writes. Demo ở trên có dữ liệu 2 byte và đã kiểm tra hoạt động trong môi trường thử nghiệm; đừng mở rộng kết luận về “một write luôn ghi hết” cho mọi channel.

## 12. READ, WRITE, APPEND và các kết hợp sai

Không phải cứ kết hợp nhiều option là mạnh hơn. Mỗi factory kiểm tra tùy chọn hợp lệ.

| Kết hợp | Kết luận |
| --- | --- |
| `READ + WRITE` với `newByteChannel` / `FileChannel.open` | Hợp lệ để dùng cả hai chiều nếu provider hỗ trợ. |
| `READ` với `Files.newInputStream` | Hợp lệ; đây là quyền đọc đúng API. |
| `READ` với `Files.newOutputStream` | **Không hợp lệ** theo hợp đồng của output-stream factory. |
| `WRITE` với `Files.newInputStream` | Không phải chế độ input chuẩn được API này hỗ trợ. |
| `CREATE + WRITE` | Cho tạo file thiếu, còn file đang tồn tại không tự bị truncate. |
| `CREATE_NEW + WRITE` | Tạo nếu chưa có; từ chối tên trùng. |
| `CREATE + CREATE_NEW + WRITE` | `CREATE_NEW` có hiệu lực, `CREATE` bị bỏ qua. |
| `APPEND + TRUNCATE_EXISTING` với channel | **Không hợp lệ** vì vừa giữ nội dung để thêm cuối vừa yêu cầu xóa trắng. |
| `READ + APPEND` với channel | **Không hợp lệ** theo contract của `newByteChannel`/`FileChannel`. |
| `TRUNCATE_EXISTING + READ` (không WRITE) với channel | Truncate bị **bỏ qua**. |
| `CREATE/CREATE_NEW + READ` (không WRITE/APPEND) với channel | Tùy chọn tạo mới bị **bỏ qua** vì mở chỉ đọc. |

**Ngoại lệ và giới hạn:** Tổ hợp không hợp lệ có thể ném `IllegalArgumentException`; option mà provider/API không hỗ trợ có thể ném `UnsupportedOperationException`; vấn đề file/quyền/I/O thường ném `IOException`. Cụ thể từng API được nêu trong Javadoc; không dựa vào một exception duy nhất cho mọi provider.

### 12.1. Files.newOutputStream không nhất thiết có buffer

Javadoc `Files.newOutputStream` cho biết stream trả về **không được buffer** bởi method này. Với nhiều write nhỏ, có thể bọc:

~~~java
// Fragment: method throws IOException; dùng đúng một owner cho stream.
try (OutputStream out = new BufferedOutputStream(
        Files.newOutputStream(
                path, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING))) {
    // Ghi nhiều byte vào out...
}
~~~

Cần import `java.io.BufferedOutputStream` và `java.io.OutputStream`. Đóng outer buffered stream sẽ đóng stream bên trong. Nhưng buffering **không** thay đổi semantics của việc mở tệp: `TRUNCATE_EXISTING` vẫn tác động **khi mở**, không phải sau khi buffer đã viết xong.

## 13. Nguyên tắc chống mất dữ liệu khi cần thay toàn bộ file

Dùng `TRUNCATE_EXISTING` trực tiếp cho file thật phù hợp khi nghiệp vụ **chấp nhận** kết quả có thể rỗng hoặc thiếu sau khi ghi lỗi (ví dụ file cache dễ tái tạo). Với config, hồ sơ hoặc dữ liệu quan trọng, thường không nên ghi đè tại chỗ rồi tin rằng ghi xong là giao dịch nguyên tử.

Một chiến lược phổ biến:

1. Tạo **file tạm mới** trong cùng thư mục/volume với đích khi có thể.
2. Ghi toàn bộ dữ liệu mới vào file tạm, dùng `CREATE_NEW` hoặc `Files.createTempFile` để tránh đụng tên có trước.
3. Đóng output (và áp dụng cơ chế đảm bảo độ bền dữ liệu khi yêu cầu).
4. Thử di chuyển file tạm sang tên đích bằng `Files.move` với `ATOMIC_MOVE` nếu provider/filesystem hỗ trợ.
5. Nếu filesystem không hỗ trợ atomic move, phải quyết định fallback có thể mất atomicity hay từ chối thao tác; không âm thầm khẳng định đó là “atomic”.
6. Dọn file tạm nếu có lỗi; kiểm soát quyền truy cập, race, symbolic links và điều kiện ghi đè mục tiêu theo yêu cầu nghiệp vụ.

**Cẩn trọng:** `Files.move(..., ATOMIC_MOVE)` có những quy tắc về các option khác và việc target đã tồn tại phụ thuộc provider/implementation; không được xem `ATOMIC_MOVE + REPLACE_EXISTING` là cam kết portable rằng thay một target có sẵn luôn thành công nguyên tử. Ngoài ra, **atomic rename khác durable write**: dữ liệu đã đổi tên có thể vẫn cần sync và các biện pháp ở mức filesystem/OS nếu yêu cầu chống mất dữ liệu khi mất điện.

### 13.1. CREATE_NEW cũng cần xử lý an ninh đường dẫn

`CREATE_NEW` chặn ghi đè **final path entry** đang tồn tại, kể cả symlink ở tên cuối. Nhưng nó không tự vô hiệu hóa symbolic link ở các thư mục cha, không khóa quyền truy cập vào root và không ngăn tác nhân khác sửa cây thư mục. Với tên file nhận từ người dùng, cần chính sách kiểm soát relative path, traversal `..`, symlink, quyền truy cập và race; không dùng `CREATE_NEW` như một sandbox đầy đủ.

## 14. Ghi văn bản so với ghi byte

Các option mô tả **cách mở tệp**, độc lập với cách mã hóa nội dung.

~~~java
// Hai cách ghi tương đương về các byte UTF-8 của chuỗi này.
byte[] data = "Xin chào".getBytes(StandardCharsets.UTF_8);

Files.write(path, data,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);

Files.writeString(path, "Xin chào", StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
~~~

Đây là **fragment**, hai lời gọi chạy lần lượt; lời gọi sau ghi lại cùng nội dung. Điều quan trọng:

- `StandardCharsets.UTF_8` đảm bảo chọn charset rõ ràng; `String.length()` đếm UTF-16 code units, không phải kích thước file UTF-8.
- Các flag `WRITE/CREATE/TRUNCATE_EXISTING` không chọn charset, không chuyển newline và không bảo đảm nội dung UTF-8 hợp lệ nếu bạn tự sửa byte trong file.
- Với I/O tệp lớn, đừng mặc định `Files.readAllBytes`/`readString` là lựa chọn tốt nếu cần xử lý theo dòng/khối. Dùng buffered reader/stream và quản lý đóng resource khi dữ liệu lớn.
- Dùng `Files.writeString(..., Charset, OpenOption...)` từ Java 11; ở Java 8 có thể dùng `Files.write(path, byte[], OpenOption...)` hoặc `Files.newBufferedWriter(path, charset, options...)`.

### 14.1. Không mặc định File System hỗ trợ toàn bộ tùy chọn

Trong môi trường ZIPFS, mạng, cloud mounted filesystem hoặc provider riêng, một số option có thể không được hỗ trợ, và behavior hoặc exception cụ thể có thể khác. Các ví dụ tập trung vào default provider hỗ trợ StandardOpenOption phổ biến. Một API portable phải dự trù `UnsupportedOperationException`, `IOException` và các constraint như filesystem chỉ đọc; kiểm tra những option bắt buộc trong môi trường triển khai.

## 15. Xử lý exception và cleanup đúng

Khi mở/ghi tệp, các lỗi cần được phân biệt để trả thông báo đúng:

| Tình huống | Kiểu lỗi thường gặp | Phản ứng hợp lý |
| --- | --- | --- |
| Tệp thiếu khi chỉ READ/WRITE | `NoSuchFileException` | Báo đường dẫn không có hoặc chuyển sang flow có quyền tạo, nếu hợp lệ |
| CREATE_NEW gặp tên đã tồn tại | `FileAlreadyExistsException` (thường) | Không tự động ghi đè; xử lý trùng tên theo yêu cầu nghiệp vụ |
| Không đủ quyền hoặc hệ thống từ chối truy cập | `AccessDeniedException`/`IOException` | Báo lỗi truy cập; không tự mở rộng quyền |
| File cha chưa tồn tại | `NoSuchFileException`/`IOException` | Kiểm tra parent, có thể tạo thư mục theo chính sách |
| Tổ hợp options sai | `IllegalArgumentException` | Sửa lỗi lập trình, không retry vô hạn |
| Provider không hỗ trợ option | `UnsupportedOperationException` | Chọn chiến lược thay thế phù hợp provider |
| Hết dung lượng, lỗi ổ đĩa, ngắt ghi | `IOException` | Xem file có thể đã tạo/truncate/ghi dở; cleanup và báo lỗi |
| Lỗi khi `close()` | `IOException` hoặc loại khác theo resource | Bảo toàn thông tin lỗi, xử lý suppressed exceptions |

Ví dụ khung mở output và đóng trong mọi đường thoát:

~~~java
// Fragment: method throws IOException.
try (OutputStream out = Files.newOutputStream(
        path, StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING)) {
    out.write(data); // data do caller chuẩn bị.
}
~~~

Nếu lỗi xảy ra **sau khi tệp đã được mở**, `try-with-resources` vẫn gọi close, nhưng không hoàn tác dữ liệu đã ghi hoặc thao tác truncate. Các exception phát sinh khi close có thể xuất hiện dưới dạng suppressed nếu body đã ném exception. **Không** suy ra “có close nghĩa là write đã hoàn tất và an toàn”.

## 16. Mô hình ra quyết định nhanh cho một API ghi file

~~~text
Cần CHỈ đọc?
  └── Có: Files.newInputStream(path) hoặc Files.readString(path, charset)
       Không tự CREATE.

Cần ghi file?
  ├── Không được đụng tên đã tồn tại
  │     └── CREATE_NEW + WRITE
  ├── Chỉ tệp đã tồn tại, muốn giữ phần dữ liệu chưa bị overwrite
  │     └── WRITE
  ├── Chỉ tệp đã tồn tại, muốn xóa trắng trước khi ghi
  │     └── WRITE + TRUNCATE_EXISTING
  ├── Thiếu thì tạo, đã có thì xóa trắng và ghi lại
  │     └── CREATE + WRITE + TRUNCATE_EXISTING
  └── Thiếu thì tạo, đã có thì thêm cuối
        └── CREATE + APPEND (+ WRITE theo API/để rõ ý định)

Cần READ và WRITE cùng một handle?
  └── Files.newByteChannel(path, EnumSet.of(READ, WRITE))
        hoặc FileChannel.open(path, READ, WRITE).
~~~

**Nhắc lại:** nếu file quan trọng và cần thay thế nguyên khối, flow ghi file tạm → đóng → di chuyển phù hợp có thể thích hợp hơn trực tiếp `TRUNCATE_EXISTING`. Không dựa vào `Files.exists` trước `Files.write` để giả lập điều kiện CREATE_NEW.

## 17. Các lỗi dễ gặp khi học hoặc review code

1. **“`WRITE` tự tạo file.”** Sai: không yêu cầu tạo thì tệp thiếu có thể lỗi.
2. **“`WRITE` tự xóa phần đuôi cũ.”** Sai: muốn xoá size cũ cần `TRUNCATE_EXISTING` khi mở để ghi.
3. **“`CREATE` chỉ tạo file mới.”** Sai: nếu file đã tồn tại thì mở được; không bảo vệ khỏi ghi đè.
4. **“`CREATE_NEW` ghi đè file với nội dung mới.”** Sai: tên đã tồn tại thì thất bại.
5. **“`CREATE_NEW` là bảo đảm ghi file atomic.”** Chỉ atomic ở bước kiểm tra/tạo tên, không bảo đảm dữ liệu được ghi hết.
6. **“`TRUNCATE_EXISTING` chỉ có tác dụng khi write thành công.”** Sai: truncate diễn ra khi mở trước khi viết.
7. **“`TRUNCATE_EXISTING` xóa tệp khỏi directory.”** Sai: nó làm length về 0, file entry còn.
8. **“`READ+WRITE` dùng được trên InputStream/OutputStream đơn chiều.”** Sai: chọn channel hỗ trợ hai chiều.
9. **“Không options ở mọi API là READ.”** Sai: `newOutputStream`/`Files.write*` mặc định có CREATE+TRUNCATE+WRITE, còn `newByteChannel` mặc định READ.
10. **“Thêm `WRITE` vào Files.writeString không ảnh hưởng.”** Có thể ảnh hưởng: custom options không tự kế thừa default CREATE/TRUNCATE.
11. **“`Files.exists` rồi `Files.writeString` chống race.”** Sai: hai thao tác không atomic.
12. **“Kết hợp `APPEND+TRUNCATE_EXISTING` để ghi rồi giữ đuôi.”** Sai: không hợp lệ cho channel chuẩn.
13. **“`CREATE+CREATE_NEW` sẽ ưu tiên CREATE.”** Sai: CREATE_NEW chi phối.
14. **“Đọc thuần READ mà truyền TRUNCATE_EXISTING sẽ xóa file.”** Sai: bị bỏ qua trong channel chỉ đọc.
15. **“FileOutputStream mở không options luôn giữ nguyên file cũ.”** Không phải mặc định của `Files.newOutputStream`: có truncate.
16. **“Dùng WRITE và ghi chuỗi ngắn hơn thì file tự thu nhỏ.”** Sai, có thể còn đuôi cũ.
17. **“Tất cả Files API trả stream đều tự đóng.”** Sai: method trả InputStream/OutputStream/Channel để caller dùng thì caller đóng; `Files.writeString` tự quản lý.
18. **“Đã có try-with-resources thì không thể mất dữ liệu.”** Sai: try đảm bảo gọi close, không rollback truncate/partial write.
19. **“File ZIP hoặc provider khác luôn hỗ trợ mọi StandardOpenOption.”** Không bảo đảm; phải theo provider.
20. **“Ghi byte theo offset bất kỳ vào UTF-8 luôn giữ text hợp lệ.”** Sai: có thể phá một sequence nhiều byte.
21. **“Đặt TRUNCATE_EXISTING làm giải pháp cập nhật crash-safe.”** Sai: cần thiết kế temp/atomic move/durability phù hợp.
22. **“APPEND là cùng nghĩa với WRITE mặc định.”** Sai: APPEND nhắm cuối file, còn WRITE đơn thuần thường từ đầu.

## 18. Bài tập tự kiểm tra

1. File `A.txt` chứa `12345678`. `Files.newOutputStream(path, WRITE)` ghi hai byte `XY`. Nội dung dự kiến là gì?
2. Tương tự, thêm `TRUNCATE_EXISTING` rồi ghi `XY`. Khác nhau thế nào?
3. Muốn **chỉ tạo mới** file upload và từ chối tên trùng, chọn option nào? Vì sao `if (!exists)` chưa đủ?
4. Muốn chạy trên file đã tồn tại nhưng **không được tạo mới**, phải bỏ option gì?
5. `Files.writeString(file, "A")` không options làm gì nếu file chứa `ABCDEFG`?
6. Khi thêm `StandardOpenOption.WRITE` tường minh vào overload `Files.writeString(file, "A", UTF_8, WRITE)`, có còn được hiểu mặc định CREATE và TRUNCATE không?
7. Nếu mở `Files.newByteChannel(file, EnumSet.of(READ, WRITE))`, có thể đọc/ghi với cùng handle không?
8. Có thể yêu cầu `READ + APPEND` trên channel chuẩn không?
9. `READ + TRUNCATE_EXISTING` trên channel chỉ đọc sẽ làm gì với size file?
10. `CREATE_NEW` có bảo đảm file được ghi hoàn chỉnh nếu máy mất điện lúc ghi không?
11. Đường dẫn có symbolic link ở **thư mục cha** có được CREATE_NEW tự xử lý thành sandbox không?
12. Vì sao phải đóng `Files.newOutputStream` bằng try-with-resources còn `Files.writeString` không trả output stream để caller đóng?
13. Nếu thiếu thư mục cha, `CREATE` có tự tạo mọi thư mục không?
14. Muốn giữ phần cũ và ghi thêm cuối thì option nào phù hợp, và tại sao không dùng `TRUNCATE_EXISTING`?
15. File cấu hình quan trọng cần cập nhật với khả năng giảm thiểu ghi nửa chừng: có chiến lược nào tốt hơn trực tiếp truncate?
16. Vì sao gọi `write(ByteBuffer)` trên một channel không được luôn giả định đã tiêu thụ hết buffer?

**Đáp án định hướng:**

1. `XY345678` vì WRITE không truncate.
2. Chỉ còn `XY` sau thao tác thành công vì length được cắt về 0.
3. `CREATE_NEW+WRITE`; kiểm tra và tạo file cùng một thao tác atomic theo hợp đồng.
4. Bỏ `CREATE` và `CREATE_NEW`; dùng `WRITE` hoặc `WRITE+TRUNCATE_EXISTING` tùy muốn xóa.
5. Ghi theo default CREATE+TRUNCATE+WRITE nên file thành `A`.
6. Không; khi chỉ định options phải nêu rõ CREATE/TRUNCATE nếu muốn những hành vi đó.
7. Có, với channel được mở bằng cả hai quyền; vị trí file cần quản lý.
8. Không, combination không hợp lệ theo contract của channel.
9. Không thay đổi size vì truncate bị bỏ qua khi chỉ đọc.
10. Không, atomic creation khác atomic/complete write và durability.
11. Không, cần kiểm soát traversal, symlink và quyền truy cập theo threat model.
12. `newOutputStream` trả resource mở do caller quản lý; `writeString` tự làm việc và dọn nội bộ.
13. Không; tạo parent riêng khi được phép.
14. `APPEND`, thường đi cùng `CREATE` nếu muốn file thiếu cũng tạo; không kết hợp TRUNCATE.
15. Ghi file tạm cùng vị trí phù hợp, đóng và thay thế bằng move atomic nếu hỗ trợ; xem thêm durability, error handling.
16. Channel có thể ghi một phần; kiểm tra `ByteBuffer.hasRemaining()` và lặp.

## 19. Tài liệu tham khảo chuẩn Java SE

- [Oracle Java SE 21 — StandardOpenOption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardOpenOption.html): ý nghĩa `READ`, `WRITE`, `CREATE`, `CREATE_NEW`, `TRUNCATE_EXISTING`, `APPEND`.
- [Oracle Java SE 21 — Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html): mặc định của `newInputStream`, `newOutputStream`, `newByteChannel`, `write`, `writeString`, ngoại lệ và cách đóng handle.
- [Oracle Java SE 21 — FileChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html): các option mở channel hai chiều, position, read/write và giới hạn tổ hợp.
- [Oracle Java SE 21 — SeekableByteChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SeekableByteChannel.html): đọc/ghi/di chuyển vị trí và size/truncate.
- [Oracle Java SE 21 — FileAlreadyExistsException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileAlreadyExistsException.html): lỗi thường gặp khi `CREATE_NEW` gặp tên đã có.
- [Oracle Java SE 21 — Files.move](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...)): `ATOMIC_MOVE`, `REPLACE_EXISTING` và giới hạn theo provider.
- [Oracle Java SE 21 — StandardCopyOption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardCopyOption.html): các lựa chọn khi move/copy file.

**Ghi nhớ cuối bài:** `READ` là quyền đọc; `WRITE` là quyền ghi; `CREATE` tạo khi thiếu nhưng dùng lại file đã có; `CREATE_NEW` chỉ tạo tên mới và từ chối trùng một cách atomic ở bước tạo; `TRUNCATE_EXISTING` xóa trắng file đã có ngay lúc mở để ghi. **Đừng suy đoán default giữa các Files API**: ghi không options thường CREATE+TRUNCATE+WRITE, còn channel không options thường READ. Chọn options dựa vào yêu cầu nghiệp vụ và khả năng chấp nhận mất dữ liệu, không theo cảm giác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="files-read-write-008">APPEND và giới hạn kết hợp OpenOption</a>

<details>
<summary>Xem chi tiết</summary>

# APPEND và giới hạn kết hợp OpenOption khi đọc/ghi bằng `Files`

## 1. Mục tiêu bài học

Trong Java NIO.2, các phương thức của `java.nio.file.Files` có thể nhận những tùy chọn mở tệp (`OpenOption`) để quyết định tệp được **đọc, ghi mới, ghi đè, cắt ngắn hay ghi nối thêm**. Một lựa chọn quan trọng là `StandardOpenOption.APPEND`: dữ liệu mới được ghi vào **cuối tệp hiện có**, thay vì ghi từ đầu tệp.

Tuy nhiên, `APPEND` không thể kết hợp tùy ý với tất cả các `OpenOption`. Nếu cấu hình sai, chương trình có thể ném ngoại lệ, mất nội dung cũ, ghi trùng dữ liệu hoặc gặp vấn đề khi nhiều luồng/tiến trình cùng ghi.

Sau bài học, bạn cần:

- Hiểu `APPEND` có tác dụng gì, khác gì với `WRITE`, `CREATE`, `CREATE_NEW` và `TRUNCATE_EXISTING`.
- Biết **mặc định khi không truyền options** vào `Files.writeString`, `Files.write`, `Files.newBufferedWriter` và `Files.newOutputStream`.
- Phân biệt các tổ hợp hợp lệ, tổ hợp không hợp lệ và các trường hợp phụ thuộc filesystem/provider.
- Sử dụng đúng `Files.writeString`, `Files.newBufferedWriter`, `Files.newOutputStream` và `Files.newByteChannel` ở Java 17+.
- Nhận diện giới hạn về tạo file, charset, xuống dòng, đồng thời, atomicity, đồng bộ dữ liệu xuống đĩa và vòng đời tài nguyên.
- Chọn cách an toàn giữa **thêm bản ghi vào log** và **thay thế toàn bộ nội dung file**.

**Thông điệp trung tâm:** `APPEND` mô tả **vị trí ghi**, không tự có nghĩa “tạo file nếu thiếu”, “được đọc cùng một channel”, “ghi nguyên tử toàn bản ghi” hoặc “dữ liệu đã được lưu bền vững”.

## 2. `StandardOpenOption` và cách diễn giải

`OpenOption` là giao diện đánh dấu các tùy chọn khi mở file. Trong API NIO.2, enum `java.nio.file.StandardOpenOption` cung cấp các lựa chọn phổ biến:

| Tùy chọn | Mục đích chính | Lưu ý |
| --- | --- | --- |
| `READ` | Mở để đọc | Không dùng cùng `APPEND` trên `Files.newByteChannel` |
| `WRITE` | Mở để ghi | Không tự có nghĩa là append; có thể ghi từ vị trí hiện hành |
| `APPEND` | Ghi mỗi lần ở cuối tệp | Kéo theo ý nghĩa ghi; không kết hợp `READ` hoặc `TRUNCATE_EXISTING` khi mở byte channel |
| `CREATE` | Tạo tệp nếu chưa tồn tại | Nếu đã tồn tại, không tự cắt ngắn nội dung |
| `CREATE_NEW` | Chỉ tạo file mới | Thất bại nếu đường dẫn đã tồn tại; thích hợp tránh ghi vào tệp có sẵn |
| `TRUNCATE_EXISTING` | Cắt tệp đang tồn tại xuống 0 khi mở để ghi | **Không phải append**; thường làm mất nội dung cũ |
| `SYNC` | Yêu cầu đồng bộ nội dung và metadata theo hợp đồng mở file | Không bảo đảm mọi hệ thống có hỗ trợ/hiệu năng như nhau |
| `DSYNC` | Yêu cầu đồng bộ cập nhật nội dung theo hợp đồng | Không đồng nghĩa tính nguyên tử của từng bản ghi |
| `DELETE_ON_CLOSE` | Đề nghị xóa file khi đóng theo semantics provider | Không phải lựa chọn để giữ log lâu dài |
| `SPARSE` | Gợi ý tạo file thưa khi phù hợp | Tùy provider, không liên quan trực tiếp đến append |

**Chú ý:** Các API khác nhau có tập `OpenOption` hợp lệ và giá trị mặc định khác nhau. Không thể nói “mọi phương thức Files mở file với READ” hay “mọi tùy chọn có thể truyền cho mọi API”.

## 3. `APPEND` hoạt động thế nào?

Giả sử tệp `events.log` đang chứa:

```text
start
```

Nếu mở với `APPEND` rồi ghi `"stop\n"`, nội dung thành:

```text
start
stop
```

Không có hành động tự chèn dấu xuống dòng. Nếu tệp ban đầu là `"start"` (không có `\n` cuối), ghi `"stop\n"` ở chế độ append sẽ tạo `"startstop\n"`. Người lập trình phải quyết định rõ **format bản ghi và ranh giới dòng**.

### 3.1. APPEND khác WRITE

`WRITE` cho phép ghi vào file nhưng **không yêu cầu mọi lần ghi luôn ở cuối file**. Nếu mở channel chỉ với `WRITE`, vị trí ghi mặc định thường là đầu file (position 0); ghi dữ liệu mới có thể đè lên những byte đầu mà không xóa phần đuôi cũ.

Ví dụ khái niệm:

```text
Tệp cũ:              ABCDEF
WRITE từ position 0: 12CDEF    (ghi hai byte; phần đuôi cũ còn)
TRUNCATE + WRITE:    12        (xóa nội dung cũ trước khi ghi)
APPEND:              ABCDEF12  (ghi thêm vào cuối)
```

`APPEND` thường được dùng với các phương thức ghi nối tiếp, chẳng hạn `Files.writeString(path, data, UTF_8, APPEND)`, hoặc stream/channel đã mở với `APPEND`. Không cố mô phỏng append đồng thời bằng `channel.position(channel.size())`: giữa bước lấy size và ghi, một writer khác có thể thay đổi file.

### 3.2. APPEND có tự tạo file nếu thiếu không?

**Không nên giả định.** Khi truyền tường minh `APPEND` mà **không** `CREATE` hoặc `CREATE_NEW`, tệp phải tồn tại theo hợp đồng mở tương ứng, nếu không có thể ném `NoSuchFileException`.

Ví dụ nên dùng để tạo log nếu thiếu và nối thêm nếu đã tồn tại:

```java
Files.writeString(
        logPath,
        "event\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
```

Đây là **đoạn mã minh họa**, chưa khai báo `logPath` và import. Nếu file chưa tồn tại, `CREATE` cho phép tạo mới; nếu đã có, `APPEND` yêu cầu thêm dữ liệu vào cuối. `APPEND` thể hiện ý nghĩa ghi (đối với các API hỗ trợ) nên có thể ghi rõ cả `WRITE` nếu muốn diễn đạt chủ đích.

## 4. Mặc định nguy hiểm: không truyền `OpenOption`

Với những overload ghi file phổ biến sau:

```java
Files.writeString(path, text);
Files.writeString(path, text, StandardCharsets.UTF_8);
Files.write(path, bytes);
Files.newBufferedWriter(path, StandardCharsets.UTF_8);
Files.newOutputStream(path);
```

**Nếu không chỉ định tùy chọn mở tệp**, các API ghi này mặc định có tác dụng tương đương **`CREATE` + `TRUNCATE_EXISTING` + `WRITE`**: file chưa có thì tạo, file đã tồn tại thì cắt về rỗng trước khi ghi, và file được mở để ghi. Đây là lựa chọn phù hợp cho **ghi lại toàn bộ nội dung**, nhưng **không phù hợp để cập nhật log theo kiểu append**.

Điểm quan trọng: khi **đã truyền options tường minh**, bạn đang chọn chế độ mở; đừng mặc định `CREATE` và `TRUNCATE_EXISTING` vẫn tự được thêm vào. Chẳng hạn:

```java
Files.writeString(path, "line\n",
        StandardCharsets.UTF_8, StandardOpenOption.APPEND);
```

về ý định là **ghi nối vào file đã tồn tại**, không đảm bảo tạo file. Nếu cần tạo khi thiếu, hãy truyền cả `CREATE`.

### 4.1. Bảng so sánh các thao tác thường gặp

| Ý định | Gợi ý `OpenOption` | Kết quả với file đã có | Kết quả với file chưa có |
| --- | --- | --- | --- |
| Ghi thay toàn bộ nội dung | Không truyền options (mặc định) | Cắt ngắn rồi ghi | Tạo và ghi |
| Ghi thay toàn bộ, ghi rõ chế độ | `CREATE, WRITE, TRUNCATE_EXISTING` | Cắt ngắn rồi ghi | Tạo và ghi |
| Ghi thêm, tạo nếu thiếu | `CREATE, APPEND` | Nối cuối | Tạo và ghi |
| Ghi thêm nhưng bắt buộc file đã có | `APPEND` | Nối cuối | Thất bại |
| Chỉ tạo file mới, từ chối file cũ | `CREATE_NEW, WRITE` | Thất bại | Tạo và ghi |
| Ghi vào file cũ theo vị trí | `WRITE` | Ghi từ vị trí hiện hành, không tự truncate | Thường thất bại nếu không có CREATE |
| Mở để đọc | `READ` trong API byte channel tương ứng | Đọc file | Thường thất bại nếu không có file |

Đối với `Files.newByteChannel`, **không truyền options mặc định là mở để `READ`**, khác các phương thức ghi file nói trên. Không áp dụng cùng một “mặc định” cho mọi API trong `Files`.

## 5. Các kết hợp không hợp lệ hoặc gây hiểu lầm

### 5.1. `APPEND` + `TRUNCATE_EXISTING`

Hai ý định mâu thuẫn:

- `APPEND` muốn giữ nội dung đã có và ghi thêm ở cuối.
- `TRUNCATE_EXISTING` muốn cắt bỏ nội dung đã có khi mở để ghi.

Với `Files.newByteChannel`/`SeekableByteChannel`, hợp đồng API quy định **`APPEND` và `TRUNCATE_EXISTING` không được dùng đồng thời**; tổ hợp không hợp lệ sẽ ném `IllegalArgumentException`. Đừng hy vọng Java “truncates trước rồi append” như một tính năng tiêu chuẩn. Chọn một ý định rõ ràng.

### 5.2. `APPEND` + `READ`

Với `Files.newByteChannel`, `APPEND` không được kết hợp `READ`. Cách định nghĩa APPEND trong API mở byte channel không cho phép vừa dùng READ vừa APPEND như một chế độ chung; tổ hợp không hợp lệ có thể phát sinh `IllegalArgumentException` hoặc dạng con phù hợp.

Nếu cần vừa quan sát nội dung file vừa thêm bản ghi:

- Mở **một luồng đọc riêng** và **một luồng append riêng**, quản lý đồng thời theo thiết kế ứng dụng.
- Hoặc chọn cơ chế truy cập theo vị trí và khóa phù hợp khi cần thao tác phức tạp; không mặc định thay thế APPEND bằng seek-to-end là an toàn dưới concurrency.
- Nếu đang xây hệ thống log/queue, cân nhắc một writer chuyên trách và cơ chế đồng bộ thay vì nhiều nơi cùng ghi tùy tiện.

Không dùng `READ, APPEND` như một tổ hợp mặc định rồi bắt lỗi để đoán provider “có thể hỗ trợ”.

### 5.3. `APPEND` + `CREATE`

Đây là tổ hợp **thường hợp lệ và hữu ích** khi muốn thêm dữ liệu vào file nếu có hoặc tạo mới nếu chưa có:

```java
Files.writeString(logPath, message,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
```

`CREATE` không đòi file phải mới; nếu file đã tồn tại nó không tự xóa. Kết hợp với `APPEND` là tình huống log cơ bản.

### 5.4. `CREATE_NEW` + `APPEND`

`CREATE_NEW` có mục tiêu khác `CREATE`: nó chỉ cho phép mở file mới, từ chối nếu tên file đã có. Nếu file đang tồn tại, ngoại lệ như `FileAlreadyExistsException` là kết quả mong đợi dù có yêu cầu append.

Vì thế, đừng chọn `CREATE_NEW` khi yêu cầu nghiệp vụ là **tích lũy nhiều bản ghi trong một file log đã tồn tại**. `CREATE_NEW` phù hợp khi bạn cần tạo đầu ra độc nhất, không chạm vào dữ liệu cũ.

### 5.5. `SYNC`/`DSYNC` không bảo đảm atomic append

Các tùy chọn `SYNC` và `DSYNC` liên quan yêu cầu đồng bộ thay đổi xuống thiết bị lưu trữ theo semantics provider. Chúng không biến nhiều lời gọi ghi hoặc nhiều dòng log thành một transaction, không bảo đảm không có xen kẽ giữa writer khác, và không thay chính sách lock/ghi tuần tự.

Bộ đệm của `BufferedWriter` cũng có ảnh hưởng: gọi `flush()` chuyển dữ liệu đã gom xuống writer/stream bên dưới, nhưng không đồng nghĩa toàn bộ dữ liệu đã durable sau sự cố mất điện. Nếu yêu cầu durability nghiêm ngặt, tìm hiểu `FileChannel.force(...)`/cơ chế fsync và hành vi filesystem/hardware thực tế.

### 5.6. Tùy chọn không hỗ trợ và ngoại lệ

Tập tùy chọn hợp lệ tùy **phương thức, filesystem provider và nền tảng**. Khi dùng tùy chọn không được API/provider hỗ trợ, có thể gặp `UnsupportedOperationException`; khi tổ hợp không hợp lệ, có thể gặp `IllegalArgumentException`; khi không được cấp quyền hoặc lỗi mở/ghi, có thể gặp `IOException` hoặc ngoại lệ truy cập tương ứng.

Đừng gộp tất cả lỗi thành “file không tồn tại”. Đặc biệt, **`IOException` khi ghi** có thể xuất hiện sau khi một phần dữ liệu đã được thêm vào file.

## 6. Ví dụ Java 17+: `Files.writeString` tạo, append và truncate

Ví dụ này chủ động tạo thư mục tạm để không sửa file người dùng, sử dụng UTF-8 rõ ràng và kiểm chứng nội dung sau mỗi thao tác. Chương trình không giả định một đường dẫn cố định trên Windows/Linux/macOS.

Lưu thành **`AppendOptionsDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class AppendOptionsDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("append-options-");
        Path log = directory.resolve("events.log");
        Path created = directory.resolve("new.log");

        try {
            Files.writeString(log, "first\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

            Files.writeString(log, "second\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);

            Files.writeString(log, "third\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

            System.out.println("Appended: "
                    + Files.readAllLines(log, StandardCharsets.UTF_8));

            Files.writeString(created, "created\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

            System.out.println("Created on append: "
                    + Files.readString(created, StandardCharsets.UTF_8)
                           .equals("created\n"));

            Files.writeString(log, "reset\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("After truncate: "
                    + Files.readAllLines(log, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(created);
            Files.deleteIfExists(log);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 AppendOptionsDemo.java
java AppendOptionsDemo
```

**Stdout khi mọi thao tác filesystem thành công:**

```text
Appended: [first, second, third]
Created on append: true
After truncate: [reset]
```

**Giải thích:**

1. `CREATE_NEW, WRITE` tạo `events.log` và ghi dòng đầu tiên. Nếu file đã tồn tại, thao tác này phải thất bại thay vì ghi đè.
2. `APPEND` thêm `second\n` vào file đã có; không cần tự seek về cuối.
3. `CREATE, APPEND` thêm `third\n` nhưng cũng xử lý được trường hợp file chưa tồn tại (đã được chứng minh với `new.log`).
4. Sau append, `readAllLines` với file demo nhỏ tạo danh sách ba dòng đúng thứ tự. Với log lớn, không nên gọi `readAllLines` chỉ để xác minh.
5. `WRITE, TRUNCATE_EXISTING` xóa nội dung cũ trong `events.log` rồi ghi `reset\n`. Lúc này các dòng trước đó không còn.
6. Khối `finally` dọn file và thư mục đã tạo. Với code production, lỗi ghi và lỗi cleanup cần được quản lý theo chính sách ứng dụng.

Ví dụ dùng ký tự `\n` để đầu ra nhất quán. Trong ứng dụng phải quy định encoding và newline theo format; `APPEND` không tự thêm dấu phân cách.

## 7. Ví dụ Java 17+: append nhị phân với `newByteChannel`

Khi làm việc với dữ liệu byte, có thể dùng `Files.newByteChannel` để mở một `SeekableByteChannel`, truyền các `OpenOption` bằng `Set`. Đây là nơi cần đặc biệt tránh kết hợp `READ` hoặc `TRUNCATE_EXISTING` với `APPEND`.

Lưu thành **`AppendByteChannelDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

public class AppendByteChannelDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("append-channel-");
        Path file = directory.resolve("data.bin");

        try {
            Files.write(file, new byte[] {'A'},
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

            try (SeekableByteChannel channel = Files.newByteChannel(
                    file,
                    EnumSet.of(
                            StandardOpenOption.WRITE,
                            StandardOpenOption.APPEND))) {
                ByteBuffer buffer = ByteBuffer.wrap(
                        "BC".getBytes(StandardCharsets.US_ASCII));

                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }

            Files.write(file, new byte[] {'D'},
                    StandardOpenOption.APPEND);

            System.out.println("Content: "
                    + Files.readString(file, StandardCharsets.US_ASCII));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 AppendByteChannelDemo.java
java AppendByteChannelDemo
```

**Stdout:**

```text
Content: ABCD
```

Chương trình:

- Tạo file mới với một byte `A`.
- Mở channel ở chế độ `WRITE, APPEND` và thêm hai byte `B`, `C`.
- Lặp khi `ByteBuffer.hasRemaining()` bởi giao diện channel có thể ghi ít byte hơn số byte đang còn trong buffer ở một lần gọi; không được coi một `write` luôn ghi toàn bộ `ByteBuffer`.
- Tiếp tục append byte `D` bằng `Files.write`.
- Đọc file demo nhỏ để xác nhận thứ tự `ABCD`.

**Ghi chú:** `SeekableByteChannel` cho phép thao tác vị trí, nhưng với `APPEND` thì cách ghi được điều khiển theo semantics append; đừng dựa vào `position(0)` để vượt qua ý nghĩa append. Khi cần sửa byte ở một vị trí tùy ý, mở với `READ`/`WRITE` theo một thiết kế khác, không kèm `APPEND`.

## 8. Dùng `Files.newBufferedWriter` khi ghi log văn bản

`newBufferedWriter` cung cấp writer có buffer để hạn chế nhiều thao tác ghi nhỏ. Khi cần append văn bản:

```java
// Đoạn minh họa: logPath là một Path hợp lệ.
try (java.io.BufferedWriter writer =
        java.nio.file.Files.newBufferedWriter(
                logPath,
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND)) {
    writer.write("event=started");
    writer.newLine();
}
```

Lưu ý thực hành:

- `try-with-resources` đóng writer và flush phần dữ liệu còn trong buffer khi đóng thành công; không bỏ quên đóng.
- `newLine()` tạo dấu xuống dòng theo nền tảng; nếu định dạng file yêu cầu LF nhất quán, ghi `"\n"` có chủ đích.
- Nếu nhiều luồng/tiến trình cùng append, buffer có thể gom hoặc chia việc ghi thành nhiều thao tác tới tầng dưới. Không coi một lần `writer.write("record")` là **một bản ghi nguyên tử** trên mọi filesystem.
- `newBufferedWriter` mở writer với charset cụ thể; thêm dữ liệu vào file mang encoding khác có thể tạo file lẫn encoding và khó đọc.
- Nếu chỉ định **`APPEND` mà không `CREATE`**, ứng dụng phải bảo đảm tệp hiện hữu hoặc xử lý lỗi không tìm thấy.
- Không dùng `TRUNCATE_EXISTING` trong cùng chế độ append để “dọn file cũ”: muốn xoay vòng log, phải có quy trình rotation riêng.

### 8.1. Chọn API theo kiểu dữ liệu

| Dữ liệu / yêu cầu | API gợi ý | Điểm cần xem |
| --- | --- | --- |
| Thêm một dòng văn bản ngắn | `Files.writeString` với `UTF_8, CREATE, APPEND` | Separator, encoding, writer khác |
| Thêm nhiều dòng liên tiếp | `Files.newBufferedWriter` với `CREATE, APPEND` | `close`/`flush`, concurrency |
| Thêm các block byte | `Files.newOutputStream` với `CREATE, APPEND` | Định dạng nhị phân, vòng đời stream |
| Cần `ByteBuffer`/channel | `Files.newByteChannel` với `WRITE, APPEND` | Không ghép `READ`/`TRUNCATE_EXISTING` |
| Ghi lại hoàn toàn file cấu hình | `Files.writeString` mặc định hoặc explicit `CREATE, WRITE, TRUNCATE_EXISTING` | Có thể mất bản cũ nếu lỗi giữa chừng |
| Chỉ tạo file mới, tránh file cũ | `CREATE_NEW, WRITE` | Xử lý file đã tồn tại |
| Ghi an toàn kiểu “replace” | Tệp tạm → verify → `Files.move` theo chính sách | `ATOMIC_MOVE` chỉ khi provider hỗ trợ |

`Files.writeString` thuận tiện cho lượng chữ nhỏ/vừa, nhưng không tự hiểu log rotation, locking hay độ dài tối đa của file.

## 9. APPEND khi nhiều thread hoặc tiến trình cùng ghi

### 9.1. “Ghi ở cuối file” không luôn có nghĩa “nguyên tử toàn bản ghi”

`APPEND` giúp vị trí ghi được xác lập ở cuối file theo hợp đồng provider. Tuy nhiên, việc dời vị trí đến EOF và ghi dữ liệu **có diễn ra như một thao tác nguyên tử không** là điều phụ thuộc hệ thống tệp/provider; không được khẳng định áp dụng chung cho mọi thiết bị hoặc mọi kích thước ghi.

Một record log như:

```text
2026-10-27 INFO started
```

có thể được writer chuyển thành một hoặc nhiều lần ghi dưới lớp buffer. Khi nhiều writer chạy đồng thời, phần byte của các record có thể xen kẽ hoặc thứ tự không trùng với thời điểm logic trong chương trình. `APPEND` cũng không ngăn tiến trình khác mở file ở chế độ truncate.

**Các chiến lược thực tế:**

1. Dùng **một writer chuyên trách** cho cùng một tệp log trong một tiến trình, chuyển bản ghi qua queue nếu cần.
2. Nếu có nhiều tiến trình, thống nhất protocol ghi/locking hoặc dùng hệ thống logging có hỗ trợ multi-process; không chỉ dựa vào `APPEND`.
3. Cân nhắc `FileChannel.lock()` trong môi trường hỗ trợ và tổ chức mọi writer cùng tuân thủ lock. File lock có thể phụ thuộc hệ điều hành/provider và không nhất thiết ngăn code bên ngoài không dùng lock.
4. Nếu cần tính nguyên tử giao dịch, hãy chọn kho dữ liệu/transaction phù hợp thay vì coi append file là database transaction.
5. Đo số byte và áp chính sách **file rotation**, retention, quota lưu trữ, thay vì để append làm đầy đĩa.

### 9.2. `APPEND` không bảo đảm thứ tự thông điệp nghiệp vụ

Ngay cả khi filesystem nối byte hợp lệ, nhiều thread có thể chuẩn bị record theo thứ tự khác thứ tự được ghi. Nếu trình tự là quan trọng, serialize ở tầng ứng dụng bằng sequence number hoặc một điểm ghi tuần tự. Đừng suy ra “event A xảy ra trước B” chỉ vì byte trong log xuất hiện trước.

### 9.3. Appending và durability

Với buffer, dữ liệu có thể còn ở heap trước khi flush; sau khi flush, dữ liệu có thể ở cache hệ điều hành; sau khi close, yêu cầu durability vẫn phụ thuộc storage stack. `SYNC`/`DSYNC` hoặc `FileChannel.force(...)` có thể được cân nhắc khi đặc tả yêu cầu ghi bền vững, nhưng **không** thay thế việc xử lý crash recovery, record framing, checksum hoặc writer đồng thời.

## 10. Hạn chế từ filesystem/provider và ngoại lệ

Các tùy chọn `StandardOpenOption` là hợp đồng API phổ biến, nhưng provider quyết định hỗ trợ nhiều chi tiết. Đặc biệt:

- **Tổ hợp không hợp lệ:** `APPEND` với `READ` hoặc `TRUNCATE_EXISTING` trong `newByteChannel` bị từ chối.
- **File thiếu:** chỉ có `APPEND` mà thiếu `CREATE`/`CREATE_NEW` không tự tạo.
- **Không có quyền:** có thể ném `AccessDeniedException` hoặc `IOException` tùy thao tác.
- **File đã tồn tại:** `CREATE_NEW` có thể ném `FileAlreadyExistsException`.
- **Tùy chọn không được hỗ trợ:** `UnsupportedOperationException` hoặc lỗi theo API/provider.
- **Đường dẫn là thư mục:** không phải tệp thường để ghi; việc mở sẽ thất bại.
- **Filesystem read-only, đầy đĩa, quota, mạng chập chờn:** lỗi I/O có thể xảy ra **sau khi một số byte đã ghi**.
- **Filesystem phân tán/network:** tính nguyên tử và khả năng quan sát append có thể khác filesystem cục bộ.
- **Symlink/đường dẫn bị thay thế:** kiểm tra “file này tồn tại” rồi mới mở có nguy cơ time-of-check-to-time-of-use; kiểm soát path theo threat model.

Nếu đang cập nhật tệp quan trọng, không chỉ quan tâm “API có ném lỗi không” mà còn cần biết **trạng thái file sau lỗi**. Append không rollback dữ liệu đã ghi; thay thế file trực tiếp bằng truncate cũng có thể làm hỏng bản cũ nếu thất bại giữa chừng.

## 11. Sai lầm thường gặp

1. **Quên `APPEND` khi ghi log:** `Files.writeString(path, line)` theo mặc định thường thay nội dung cũ bằng nội dung mới.
2. **Cho rằng `APPEND` tự tạo file:** cần `CREATE` nếu muốn file chưa tồn tại vẫn ghi thành công.
3. **Dùng `APPEND` và `TRUNCATE_EXISTING` đồng thời:** mục tiêu mâu thuẫn, tổ hợp không hợp lệ trong byte-channel API.
4. **Dùng `READ` cùng `APPEND` trong một channel:** không hợp lệ theo `Files.newByteChannel`.
5. **Nhầm `WRITE` là append:** `WRITE` đơn thuần không có semantics luôn ghi vào EOF.
6. **Gọi `position(size())` rồi ghi khi nhiều writer:** có nguy cơ race; không đồng nghĩa `APPEND`.
7. **Quên newline:** ghi `"a"` rồi `"b"` bằng append tạo `"ab"`, không tự chia dòng.
8. **Trộn charset:** nối UTF-8 vào file được mã hóa bằng charset khác làm nội dung không nhất quán.
9. **Không đóng writer/stream:** dữ liệu còn nằm trong buffer, tài nguyên có thể bị rò.
10. **Flush mỗi ký tự:** tăng số lời gọi xuống tầng dưới và thường làm giảm throughput.
11. **Coi append là transaction:** không có rollback, không bảo đảm toàn record được ghi nguyên tử trong mọi provider.
12. **Tin rằng `CREATE_NEW` có nghĩa “append nếu file có”:** ngược lại, nó từ chối file đã tồn tại.
13. **Quên xử lý ngoại lệ:** file có thể đã bị ghi một phần khi lỗi I/O xuất hiện.
14. **Không xét tăng trưởng không giới hạn:** appending dài ngày làm log lớn hoặc đầy ổ đĩa.
15. **Dùng `readAllLines` để xử lý toàn bộ file log rất lớn:** có thể tốn nhiều heap; đọc streaming theo dòng hoặc dùng công cụ log phù hợp.
16. **Nhầm cách ghi của API:** `newByteChannel` không có options mặc định khác với `writeString` và `newBufferedWriter`.

## 12. Kiểm thử cần có

Với ứng dụng dùng append, tối thiểu nên kiểm tra:

- **File chưa tồn tại:** `CREATE, APPEND` tạo file thành công; `APPEND` đơn lẻ thất bại theo hợp đồng.
- **File đã tồn tại:** sau hai lần append, nội dung cũ vẫn còn và dữ liệu mới đứng ở cuối.
- **Không truyền options:** `Files.writeString` ghi đè/truncate theo mặc định; tránh vô tình dùng cho log.
- **`CREATE_NEW` trên file đã có:** không được ghi đè.
- **Byte sequence:** append nhị phân giữ thứ tự và kích thước byte đúng.
- **Encoding:** UTF-8 với dấu tiếng Việt/emoji; tệp đích không trộn charset.
- **Newline:** phân biệt file có LF cuối và file không có LF cuối.
- **Nhiều writer:** thử tải đồng thời, kiểm tra số bản ghi, thứ tự khi cần và tình huống file rotate.
- **Lỗi I/O:** hết đĩa/quota, quyền truy cập, đóng stream; đánh giá bản ghi dở và retry để không tạo record trùng.
- **Provider khác nhau:** kiểm chứng nơi chạy thật (ổ đĩa cục bộ, mạng, filesystem ảo) nếu yêu cầu tính nguyên tử hoặc độ bền ghi đặc biệt.

## 13. Bài tập tự luyện

1. Giải thích vì sao `Files.writeString(path, "A")` rồi `Files.writeString(path, "B")` tạo kết quả khác `Files.writeString(path, "A", APPEND)` rồi `Files.writeString(path, "B", APPEND)` khi file đã tồn tại.
2. Viết chương trình tạo `journal.txt` nếu thiếu, mỗi lượt gọi ghi thêm một dòng UTF-8. Xác nhận nếu gọi ba lần, ba dòng cũ/mới vẫn tồn tại.
3. Thử dùng `APPEND` đơn lẻ với file chưa có; bắt và phân loại lỗi. Sửa code bằng `CREATE, APPEND`.
4. Thiết kế test phân biệt `WRITE`, `WRITE + TRUNCATE_EXISTING` và `APPEND` trên cùng nội dung gốc `ABCDEF`.
5. Với `Files.newByteChannel`, giải thích tại sao `APPEND + READ` và `APPEND + TRUNCATE_EXISTING` không hợp lệ. Chọn cách mở riêng cho nhu cầu đọc/ghi.
6. Ghi các byte `{1, 2}` rồi append `{3, 4}`; xác nhận tệp chứa `[1, 2, 3, 4]`, không có byte delimiter tự động.
7. Thử hai thread ghi 1.000 record mỗi thread. Tại sao không được coi một lần test thành công là bằng chứng append nguyên tử trên mọi filesystem?
8. Xây dựng chính sách rotation khi log vượt 10 MiB. Vì sao không chỉ kiểm tra `Files.size` rồi coi đó là bảo đảm chống ghi vượt ngưỡng dưới concurrency?
9. Giải thích khác biệt giữa `flush`, `close`, `SYNC`/`DSYNC` và `FileChannel.force` theo góc độ dữ liệu đi xuống tầng lưu trữ; lựa chọn cần dựa vào yêu cầu độ bền ghi và provider.
10. Nếu một lần ghi append báo lỗi sau khi một nửa record đã vào file, cơ chế retry mù quáng có thể gây lỗi gì? Đề xuất framing, checksum, sequence number hoặc hệ thống ghi chuyên dụng khi cần tính nhất quán.

## 14. Tổng kết

- `APPEND` là lựa chọn **ghi thêm ở cuối file**; nó không xóa nội dung cũ và cũng không tự thêm dòng, tự tạo file hay xử lý concurrent writers.
- `CREATE, APPEND` thường là lựa chọn phù hợp để viết file log và tạo mới nếu thiếu; `CREATE_NEW` lại chủ động **từ chối** file đã tồn tại.
- Các phương thức ghi `Files.writeString`, `Files.write`, `newBufferedWriter`, `newOutputStream` mặc định **tạo và truncate** nếu không đưa options; đừng quên quy tắc này.
- `Files.newByteChannel` có mặc định khác (đọc) và nghiêm cấm những kết hợp `APPEND + READ`, `APPEND + TRUNCATE_EXISTING`.
- `WRITE` riêng lẻ không đồng nghĩa append; `TRUNCATE_EXISTING` là hành động cắt nội dung cũ và không đi kèm mục tiêu append.
- Với hệ thống cần nhiều writer, durability, quota hay transaction, phải thiết kế **đồng bộ và xử lý lỗi ở tầng ứng dụng**, không đặt mọi kỳ vọng vào `APPEND`.

**Cách chọn an toàn:** Hỏi rõ chương trình định *tạo mới*, *ghi đè* hay *nối thêm*. Sau đó chọn tổ hợp `OpenOption` nhất quán, xác định hành vi khi file tồn tại/chưa tồn tại, đóng tài nguyên đúng cách và kiểm thử trên chính filesystem sẽ triển khai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="files-read-write-009">SYNC, DSYNC, DELETE_ON_CLOSE và tính hỗ trợ của provider</a>

<details>
<summary>Xem chi tiết</summary>

# SYNC, DSYNC, DELETE_ON_CLOSE và tính hỗ trợ của filesystem provider

## 1. Vì sao các tùy chọn mở file này quan trọng?

Khi làm việc với `java.nio.file.Files`, `FileChannel` và các API file khác, ứng dụng không chỉ quyết định **đọc/ghi gì** mà còn có thể chỉ định **cách tệp được mở và duy trì vòng đời** thông qua `OpenOption`. Ba tùy chọn dễ bị hiểu sai là:

- **`StandardOpenOption.SYNC`**: yêu cầu mỗi lần cập nhật **nội dung hoặc metadata** của file được đồng bộ xuống thiết bị lưu trữ bên dưới.
- **`StandardOpenOption.DSYNC`**: yêu cầu mỗi lần cập nhật **nội dung file** được đồng bộ xuống thiết bị lưu trữ bên dưới; khác `SYNC` ở phạm vi metadata.
- **`StandardOpenOption.DELETE_ON_CLOSE`**: yêu cầu provider **cố gắng xóa file khi resource được đóng** (*best effort*), phù hợp chủ yếu cho work file ngắn hạn.

Đây là ba `StandardOpenOption` trong `java.nio.file`, thuộc nhóm **tùy chọn khi mở tệp**. Chúng không biến Java thành hệ thống giao dịch dữ liệu, không tự đảm bảo mọi provider đều hỗ trợ, và không loại bỏ nhu cầu xử lý lỗi I/O.

```text
Chọn OpenOption
       │
       ▼
Files.newByteChannel / Files.newOutputStream / FileChannel.open
       │
       ├─ SYNC: cập nhật content + metadata đồng bộ
       │
       ├─ DSYNC: cập nhật content đồng bộ
       │
       └─ DELETE_ON_CLOSE: yêu cầu xóa khi close (best effort)
       │
       ▼
Filesystem provider + OS + filesystem + thiết bị lưu trữ
       │
       ▼
Kết quả phụ thuộc khả năng và hợp đồng của tầng dưới
```

Hai nhóm vấn đề cần phân biệt:

1. **Durability (độ bền ghi):** sau một lệnh ghi thành công, hệ thống đã yêu cầu dữ liệu được đồng bộ ở mức nào? `SYNC`, `DSYNC` và `FileChannel.force` liên quan tới câu hỏi này.
2. **Lifecycle (vòng đời tệp tạm):** một tệp được tự động yêu cầu xóa vào thời điểm nào, và việc xóa có được bảo đảm tuyệt đối không? `DELETE_ON_CLOSE` liên quan tới câu hỏi này.

Các ví dụ sử dụng **Java 17+**. Hãy nhớ rằng API cho phép *yêu cầu* hành vi; việc mở thành công và mức hỗ trợ thực tế còn liên quan đến provider, filesystem và hệ điều hành.

## 2. StandardOpenOption hoạt động ở đâu?

`StandardOpenOption` là enum triển khai `OpenOption`. Ta có thể chỉ định khi mở tệp bằng một số API, chẳng hạn:

```java
Path file = Path.of("result.bin");

try (OutputStream output = Files.newOutputStream(
        file,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.SYNC)) {
    output.write(new byte[]{1, 2, 3});
}
```

Đây là **đoạn minh họa**, cần import `Path`, `Files`, `OutputStream`, `StandardOpenOption` và xử lý `IOException`. `CREATE` tạo tệp khi thiếu, `WRITE` cho phép ghi, và `SYNC` yêu cầu cập nhật đồng bộ. Nếu file đã tồn tại, ví dụ không dùng `TRUNCATE_EXISTING`; ghi bắt đầu tại vị trí đầu thay vì mặc định xóa sạch nội dung cũ. Trong ví dụ thực tế, phải chọn thêm policy ghi đè/truncate/append có chủ đích.

Các tùy chọn mở file thông dụng:

| OpenOption | Ý nghĩa |
| --- | --- |
| `READ` | Mở để đọc |
| `WRITE` | Mở để ghi |
| `APPEND` | Ghi ở cuối file khi được mở để ghi |
| `TRUNCATE_EXISTING` | Xóa nội dung file hiện có bằng cách đặt kích thước về 0 nếu mở để ghi |
| `CREATE` | Tạo file nếu thiếu |
| `CREATE_NEW` | Tạo file mới, thất bại nếu tên đã tồn tại; kiểm tra tồn tại và tạo là thao tác nguyên tử theo hợp đồng |
| `SYNC` | Đồng bộ cập nhật content hoặc metadata |
| `DSYNC` | Đồng bộ cập nhật content |
| `DELETE_ON_CLOSE` | Cố gắng xóa file khi close |
| `SPARSE` | Gợi ý tạo file sparse; provider có thể bỏ qua khi không hỗ trợ |

**Các OpenOption không hoàn toàn độc lập với nhau.** Một tổ hợp không hợp lệ có thể gây `IllegalArgumentException`; một tùy chọn mà provider không hỗ trợ có thể gây `UnsupportedOperationException`. Ví dụ `FileChannel.open` không cho kết hợp `APPEND` với `READ` hay `TRUNCATE_EXISTING` theo hợp đồng của nó.

## 3. SYNC: đồng bộ nội dung và metadata

`StandardOpenOption.SYNC` quy định yêu cầu **mỗi cập nhật vào nội dung hoặc metadata của file** được ghi đồng bộ tới thiết bị lưu trữ bên dưới.

```java
try (FileChannel channel = FileChannel.open(
        path,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
        StandardOpenOption.SYNC)) {
    // Ghi dữ liệu vào channel...
}
```

Đây là **đoạn minh họa**, có `Path path` và các import tương ứng. `CREATE_NEW` thất bại nếu file đã tồn tại, giúp tránh vô tình ghi đè; `WRITE` là hướng truy cập; `SYNC` chỉ định yêu cầu đồng bộ.

“Metadata” có thể bao gồm thông tin file như kích thước, thời gian sửa đổi và các thuộc tính liên quan, nhưng phạm vi cập nhật metadata thực tế phụ thuộc filesystem và cách thao tác. `SYNC` có phạm vi rộng hơn `DSYNC`, vì nó không chỉ nói về byte nội dung file.

### SYNC không bảo đảm những gì?

- Không biến nhiều lời gọi `write` thành **một transaction nguyên tử**.
- Không bảo đảm hai thread/process ghi đồng thời sẽ tạo bản ghi không xen kẽ.
- Không đảm bảo dữ liệu ứng dụng hợp lệ nếu định dạng đang ghi dở thì tiến trình bị dừng.
- Không tự sao lưu dữ liệu sang máy khác, không thay cho replication hay backup.
- Không tự khóa file hoặc ngăn sửa đổi bởi tiến trình khác.
- Không loại bỏ khả năng lỗi thiết bị, hỏng phần cứng, mất điện vượt ngoài cam kết của tầng lưu trữ.
- Không chứng minh provider hoặc filesystem thực sự đáp ứng được một yêu cầu mà API không hỗ trợ.

**Đồng bộ (synchronous update) khác với tính nguyên tử (atomicity).** Một lần ghi có thể yêu cầu đồng bộ xuống lưu trữ nhưng ứng dụng vẫn chỉ mới ghi được nửa bản ghi logic nếu lỗi xảy ra trước khi hoàn tất toàn bộ chuỗi ghi.

## 4. DSYNC: đồng bộ nội dung file

`StandardOpenOption.DSYNC` yêu cầu cập nhật **nội dung** file được ghi đồng bộ xuống thiết bị lưu trữ. Khác biệt so với `SYNC` là `DSYNC` không yêu cầu cùng phạm vi metadata theo mô tả của API.

```java
try (FileChannel channel = FileChannel.open(
        path,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
        StandardOpenOption.DSYNC)) {
    // Ghi byte...
}
```

Đây là ví dụ minh họa, cần import `FileChannel`, `Path`, `StandardOpenOption` và xử lý `IOException`.

Một cách hiểu gần đúng:

- **SYNC:** yêu cầu đồng bộ nội dung **và** metadata liên quan đến cập nhật.
- **DSYNC:** yêu cầu đồng bộ nội dung; không đòi mọi metadata theo cùng mức của SYNC.

Tuy nhiên, trong các filesystem thực tế, một số metadata cần thiết để có thể truy cập lại nội dung đã ghi (ví dụ kích thước file khi append) có thể phải được cập nhật cùng dữ liệu để đáp ứng khả năng phục hồi. Không nên dùng một mô tả đơn giản về metadata để suy ra số lời gọi ghi đĩa hay kết quả crash recovery tuyệt đối trong mọi hệ.

### DSYNC có nhanh hơn SYNC không?

**Có thể**, nếu filesystem/OS có thể tránh đồng bộ một số metadata ở những lần ghi; nhưng đây không phải định lý rằng `DSYNC` luôn nhanh hơn. Kết quả đo phụ thuộc loại thiết bị, filesystem, kích thước/số lần write, ghi append hay overwrite, cache, hệ điều hành và provider.

Nếu hiệu năng quan trọng, benchmark đúng workload và yêu cầu durability của ứng dụng. Đừng chọn DSYNC chỉ vì tên nghe giống tối ưu, khi nghiệp vụ cần metadata được đồng bộ ở mức cao hơn.

## 5. So sánh SYNC và DSYNC bằng bảng quyết định

| Tiêu chí | `SYNC` | `DSYNC` |
| --- | --- | --- |
| Yêu cầu đồng bộ cập nhật nội dung | Có | Có |
| Yêu cầu đồng bộ mọi cập nhật metadata theo mô tả API | Có | Không ở cùng mức SYNC |
| Có phải một lời gọi `flush()` đơn lẻ không? | Không; là option khi mở file | Không; là option khi mở file |
| Bảo đảm giao dịch nguyên tử nhiều bước | Không | Không |
| Thích hợp khi | Muốn phạm vi đồng bộ content + metadata rộng hơn | Muốn tập trung đồng bộ content |
| Luôn chạy trên mọi provider | Không được giả định | Không được giả định |
| Chi phí | Có thể tăng so với ghi không đồng bộ | Cũng có thể tăng; đôi khi nhẹ hơn SYNC |

Trong một hệ thống log hoặc nhật ký giao dịch, lựa chọn giữa SYNC/DSYNC nên dựa trên **yêu cầu mất dữ liệu tối đa có thể chấp nhận**, chính sách crash recovery và hành vi filesystem. Không nên quyết định chỉ dựa trên API mà bỏ qua định dạng record, cách commit hay phục hồi.

## 6. Ví dụ chạy được: ghi qua FileChannel với SYNC

Ví dụ tạo một tệp tạm được kiểm soát, mở nó bằng `SYNC`, ghi UTF-8 và đọc lại để kiểm tra nội dung. Chương trình chỉ chạy qua nhánh thành công khi provider hỗ trợ lựa chọn này.

**Tệp `SyncWriteDemo.java`:**

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SyncWriteDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("sync-write-", ".txt");
        try {
            byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

            try (FileChannel channel = FileChannel.open(
                    file,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC)) {

                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }

            System.out.println("Content: "
                    + Files.readString(file, StandardCharsets.UTF_8));
            System.out.println("Bytes: " + Files.size(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac SyncWriteDemo.java
java SyncWriteDemo
```

**Stdout chính xác nếu thao tác file và SYNC được hỗ trợ, hoàn tất thành công:**

```text
Content: hello
Bytes: 5
```

Giải thích:

1. `Files.createTempFile` tạo một file riêng, tránh đè file của người dùng.
2. `FileChannel.open(file, WRITE, TRUNCATE_EXISTING, SYNC)` mở để ghi và xóa nội dung cũ của file tạm.
3. `ByteBuffer` có vị trí (position) và giới hạn (limit). Vòng `while (buffer.hasRemaining())` đảm bảo xử lý phần byte chưa được channel ghi trong mỗi lần `write`.
4. `try-with-resources` đóng channel ngay khi ghi xong, rồi đọc lại bằng `Files.readString`.
5. File tạm được xóa trong `finally`.

Ví dụ **không chứng minh bằng phép đo** rằng dữ liệu đã được đồng bộ ở phần cứng: nó chỉ dùng đúng API để **yêu cầu** hành vi SYNC. Việc đọc lại thành công không phải phép thử crash recovery; hệ điều hành có thể trả dữ liệu từ cache. Đây là sự khác biệt quan trọng giữa test chức năng và kiểm chứng durability.

## 7. FileChannel.force(boolean) khác gì với SYNC/DSYNC?

`FileChannel.force(boolean metaData)` cho phép ứng dụng **chủ động yêu cầu đồng bộ tại một thời điểm** thay vì yêu cầu mọi cập nhật trên một channel luôn đồng bộ.

```java
try (FileChannel channel = FileChannel.open(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE)) {
    // Ghi nhiều block, có thể có buffer ở tầng ứng dụng.
    channel.force(false); // Yêu cầu đồng bộ nội dung.
    // ...
    channel.force(true);  // Yêu cầu đồng bộ cả nội dung và metadata.
}
```

Đây là đoạn minh họa, không phải chương trình độc lập; `path` đã có và cần các import tương ứng. Nếu dùng wrapper có đệm bên ngoài channel, cần **đưa dữ liệu từ buffer ứng dụng tới channel** trước khi gọi `force`, nếu không những byte còn kẹt trong buffer ứng dụng chưa trở thành cập nhật trên channel.

Các khác biệt chính:

| Nhu cầu | Lựa chọn |
| --- | --- |
| Mỗi cập nhật content/metadata phải được yêu cầu đồng bộ | Mở file với `SYNC` |
| Mỗi cập nhật content phải được yêu cầu đồng bộ | Mở file với `DSYNC` |
| Ghi nhiều phần rồi đồng bộ tại checkpoint/commit | `FileChannel.force(false/true)` |

`force(false)` tập trung vào content; `force(true)` yêu cầu cả metadata. Theo tài liệu `FileChannel`, cam kết đồng bộ khi trả về được nêu cho tệp nằm trên **thiết bị lưu trữ cục bộ**; với tệp trên thiết bị không cục bộ, tài liệu **không đưa cùng một bảo đảm**. Hành vi thực tế của tham số metadata còn phụ thuộc hệ điều hành.

`force()` được đảm bảo trong phạm vi các cập nhật thực hiện qua những phương thức của channel; với cập nhật qua `MappedByteBuffer`, có thể cần `MappedByteBuffer.force()` theo hợp đồng riêng. Đừng coi `channel.force()` là lời gọi có thể ép đồng bộ mọi cache và mọi writer trong toàn JVM.

### force() có thay cho close() không?

Không. `force` yêu cầu cập nhật dữ liệu, còn `close` giải phóng resource/channel. Chương trình vẫn phải đóng channel bằng `try-with-resources`. Ngược lại, **đóng channel không tự đồng nghĩa** với một cam kết durability tương đương `SYNC` hoặc `force(true)` trong mọi môi trường.

## 8. DELETE_ON_CLOSE: yêu cầu xóa khi đóng, không phải bảo đảm tuyệt đối

`StandardOpenOption.DELETE_ON_CLOSE` yêu cầu implementation **cố gắng xóa file khi channel/stream phù hợp được đóng**. Đây là cơ chế rất hữu ích cho một số **work file** chỉ dùng trong một JVM.

Theo hợp đồng của Java:

- Khi option được chỉ định, implementation thực hiện **best effort** để xóa tệp trong quá trình `close()`.
- Nếu `close()` không được gọi, JVM có thể thực hiện một nỗ lực best effort khi kết thúc, trong điều kiện có thể.
- Thời điểm xóa, cách xóa và nhiều chi tiết khác là **implementation-specific**.
- Không nên dùng tùy tiện cho file đang được các thực thể khác mở đồng thời.
- Một số hệ điều hành không cho phép xóa file đang được process khác sử dụng theo cách nhất định.
- Với một số provider, option có thể hàm ý không theo symbolic link vì lý do bảo mật, và việc mở một link có thể thất bại.

Từ khóa quan trọng nhất là **best effort**. `DELETE_ON_CLOSE` **không phải bằng chứng chắc chắn file đã biến mất**, không đảm bảo secure erase và không thay thế chính sách quản lý tệp tạm.

### Tại sao DELETE_ON_CLOSE cần close đúng?

Nếu lập trình viên quên đóng stream, thời điểm file được yêu cầu xóa trở nên khó đoán. Ví dụ sau đáng lo ngại:

```java
OutputStream out = Files.newOutputStream(
        tempPath,
        StandardOpenOption.WRITE,
        StandardOpenOption.DELETE_ON_CLOSE);
// ...
// Quên close: file có thể không được dọn ngay.
```

Đây là đoạn minh họa, không phải cách viết khuyến nghị. Ưu tiên:

```java
try (OutputStream out = Files.newOutputStream(
        tempPath,
        StandardOpenOption.WRITE,
        StandardOpenOption.DELETE_ON_CLOSE)) {
    // Ghi dữ liệu tạm.
}
```

Đây là đoạn minh họa với file đã tồn tại hợp lệ. Resource được đóng theo scope, kích hoạt hành vi xóa best effort.

### Có nên áp dụng cho mọi file tạm?

Không nhất thiết. Nếu file cần mở lại sau khi đóng một channel, hoặc cần được một tiến trình khác sử dụng, `DELETE_ON_CLOSE` có thể xóa nó sớm hơn mong muốn. Đối với file tạm dùng chung giữa nhiều process, cần giao kèo về lifecycle rõ ràng.

## 9. Ví dụ chạy được: DELETE_ON_CLOSE với file tạm riêng

**Tệp `DeleteOnCloseDemo.java`:**

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

public class DeleteOnCloseDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("delete-on-close-");
        Path file = directory.resolve("scratch.bin");

        try {
            try (SeekableByteChannel channel = Files.newByteChannel(
                    file,
                    EnumSet.of(
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.DELETE_ON_CLOSE))) {
                channel.write(ByteBuffer.wrap(new byte[]{1, 2, 3}));
                System.out.println("Open in scope: " + channel.isOpen());
            }

            System.out.println("Exists after close: " + Files.exists(file));
        } finally {
            // Dự phòng khi provider không thể xóa ngay.
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac DeleteOnCloseDemo.java
java DeleteOnCloseDemo
```

**Stdout thường thấy trên provider hỗ trợ và xóa được khi close:**

```text
Open in scope: true
Exists after close: false
```

Ở đây dùng `CREATE_NEW` để chỉ tạo một file mới trong thư mục tạm, `WRITE` để ghi byte, và `DELETE_ON_CLOSE` để yêu cầu dọn. **Không kiểm tra `Files.exists(file)` khi channel còn mở**, vì provider có thể có thời điểm xóa khác nhau. Sau khi đóng, `Files.exists(file)` thường là `false` khi xóa thành công, nhưng không nên viết test portable đòi luôn nhận kết quả này trên mọi provider.

Khối `finally` vẫn có `Files.deleteIfExists` như phương án dọn dẹp rõ ràng. Nếu provider không hỗ trợ option, việc mở có thể ném `UnsupportedOperationException` hoặc ngoại lệ thích hợp; chương trình không âm thầm bỏ option rồi tuyên bố đã xóa.

**Lưu ý:** `Files.exists(file)` trả `false` cả trong một số trường hợp không xác định được sự tồn tại do lỗi truy cập; không coi một phép kiểm tra này là bằng chứng pháp lý về việc mọi bản sao dữ liệu đã bị xóa hoàn toàn khỏi thiết bị.

## 10. Best effort không đồng nghĩa secure delete

Một sai lầm nguy hiểm là xem `DELETE_ON_CLOSE` như thao tác **xóa dữ liệu nhạy cảm không thể phục hồi**. API chỉ nói về cố gắng xóa file entry, không đưa cam kết xóa vật lý toàn bộ block trên ổ đĩa, bản sao cache, snapshot, backup, SSD wear leveling hoặc nội dung trên filesystem từ xa.

Với dữ liệu nhạy cảm, cần thiết kế theo chính sách bảo mật của hệ thống: hạn chế quyền truy cập, tránh ghi ra file không cần thiết, chọn lưu trữ mã hóa và quản lý khóa phù hợp. `DELETE_ON_CLOSE` có thể là một thành phần dọn dẹp, không phải toàn bộ giải pháp.

Cũng không thể giả định JVM luôn thực hiện được hành vi khi kết thúc bất thường (mất nguồn điện, lỗi kernel hoặc process bị kill không cho chạy cleanup). Vì vậy file tạm quan trọng có thể cần cơ chế quét dọn theo tuổi hoặc cơ chế quản lý khác ngoài quy trình đóng thông thường.

## 11. Files.createTempFile khác DELETE_ON_CLOSE

`Files.createTempFile` tạo một tệp với tên mới, thường trong thư mục tạm. Bản thân phương thức này **không tự đặt chính sách xóa khi đóng các stream đọc/ghi sau đó**.

```java
Path temp = Files.createTempFile("job-", ".bin");
try {
    // Mở, đọc/ghi file temp.
} finally {
    Files.deleteIfExists(temp);
}
```

Đây là đoạn minh họa, xử lý `IOException`. Nếu cần mở file với `DELETE_ON_CLOSE` sau khi tạo temp file, có thể dùng option ấy khi mở channel/stream, nhưng nhớ rằng file có thể không còn truy cập qua tên khi một provider xử lý xóa sớm.

Điều kiện chọn:

| Nhu cầu | Hướng tiếp cận |
| --- | --- |
| File tạm được mở một lần, chỉ cần tồn tại trong scope | Có thể dùng `DELETE_ON_CLOSE` nếu provider hỗ trợ |
| File phải đóng rồi mở lại nhiều lần | Quản lý `Files.createTempFile` và xóa chủ động |
| File phải chia sẻ cho process khác | Tránh `DELETE_ON_CLOSE` nếu lifecycle không tương thích |
| File phải phục hồi sau crash hoặc được audit | Cần chính sách lưu trữ/dọn dẹp riêng |
| Dữ liệu nhạy cảm phải xóa an toàn | `DELETE_ON_CLOSE` không đủ để cam kết secure erase |

## 12. Provider là gì và tại sao tính hỗ trợ không đồng nhất?

Java NIO.2 cho phép nhiều **filesystem provider** thông qua `java.nio.file.spi.FileSystemProvider`. Một `Path` gắn với một `FileSystem` cụ thể; những thao tác như `Files.newByteChannel(path, options)` được chuyển tới provider của Path.

Các ví dụ bối cảnh provider:

- Filesystem mặc định của hệ điều hành.
- ZIP/JAR filesystem (ví dụ qua `FileSystems.newFileSystem`).
- Một số provider do thư viện/ứng dụng bổ sung để truy cập lưu trữ khác.

Tính năng mà filesystem mặc định hỗ trợ không có nghĩa provider ZIP hoặc provider tùy chỉnh hỗ trợ giống hệt. Provider có thể hỗ trợ `CREATE_NEW` nhưng không hỗ trợ `SYNC`, hoặc không cung cấp `FileChannel` cho mọi loại Path.

**Khả năng hỗ trợ là một phần của hợp đồng runtime.** Nếu người dùng yêu cầu tính bền ghi cao, ứng dụng cần chọn chiến lược có thể xác minh trên provider đang dùng, hoặc **báo lỗi rõ ràng** khi không thể đáp ứng. Không âm thầm bỏ `SYNC` để ghi theo cách thường rồi nói rằng dữ liệu đã được đồng bộ.

### Cách nhận diện provider trong code

```java
FileSystemProvider provider = path.getFileSystem().provider();
System.out.println(provider.getClass().getName());
```

Đây là đoạn minh họa, cần import `Path`, `FileSystemProvider`. Tên class chỉ giúp biết provider đang được dùng; **không chứng minh mọi tùy chọn mở file đều được hỗ trợ**. Provider có thể phụ thuộc filesystem mount, hệ điều hành và các ràng buộc cụ thể của file.

## 13. Lỗi cần dự liệu khi mở với tùy chọn đặc biệt

Khi gọi `FileChannel.open(path, options)` hoặc `Files.newByteChannel`, các nhóm lỗi đáng chú ý:

| Tình huống | Ngoại lệ/cách xử lý |
| --- | --- |
| Tùy chọn không được provider hỗ trợ | `UnsupportedOperationException` |
| Tổ hợp tùy chọn không hợp lệ | `IllegalArgumentException` |
| Tệp đã tồn tại và mở `CREATE_NEW` | Có thể có `FileAlreadyExistsException` |
| Không thể mở/ghi/xóa file vì I/O | `IOException` hoặc subclass |
| Không đủ quyền truy cập | Có thể `AccessDeniedException`, `SecurityException` tùy API/môi trường |
| Đóng resource lỗi | `IOException` cần xử lý; try-with-resources giữ suppressed exception khi có lỗi chính |
| File bị process khác giữ hoặc provider không thể xóa | Tùy cơ chế filesystem; không hứa `DELETE_ON_CLOSE` luôn thành công |

**`UnsupportedOperationException` và `IllegalArgumentException` là unchecked**, nên một khối `catch(IOException)` **không bắt tất cả lỗi cấu hình option**. Cần nhận diện chúng nếu ứng dụng định làm fallback hoặc báo thông điệp rõ.

Không nên catch chung mọi exception rồi tiếp tục ghi như không có vấn đề. Với ứng dụng cần durability, nếu provider từ chối `SYNC`, việc bỏ nó đi là **thay đổi yêu cầu dữ liệu**, phải được quyết định có chủ đích.

## 14. Fallback khi provider không hỗ trợ: lựa chọn nghiệp vụ

Giả sử ứng dụng muốn mở channel với `SYNC`. Có một số chính sách:

1. **Fail closed:** nếu provider không hỗ trợ, từ chối thao tác. Phù hợp khi đồng bộ là yêu cầu bắt buộc.
2. **Chuyển sang DSYNC:** chỉ hợp lệ khi nghiệp vụ đã chấp nhận mức metadata khác và DSYNC được hỗ trợ; không coi hai option hoàn toàn tương đương.
3. **Mở channel bình thường rồi gọi `force(true)` ở checkpoint:** phù hợp với một số thiết kế commit nhưng thay đổi đặc tính “mỗi cập nhật đồng bộ” thành “đồng bộ theo lô”; cần tài liệu hóa và kiểm thử.
4. **Giảm yêu cầu để ghi best-effort:** chỉ dành cho dữ liệu mà mất mát có thể chấp nhận; phải thông báo chính sách, không giả vờ có durability như trước.

Một đoạn khung lựa chọn:

```java
try (FileChannel channel = FileChannel.open(
        path,
        StandardOpenOption.WRITE,
        StandardOpenOption.SYNC)) {
    // Ghi theo yêu cầu SYNC.
} catch (UnsupportedOperationException unsupported) {
    throw new IllegalStateException(
            "Configured filesystem cannot satisfy required SYNC", unsupported);
}
```

Đây là đoạn minh họa trong ứng dụng đã xác định `Path path` hợp lệ và xử lý `IOException`. Thông điệp báo rõ rằng lựa chọn đồng bộ bắt buộc không thể đáp ứng. Không tự mở lại với `WRITE` thường nếu nghiệp vụ cần cam kết SYNC.

**Kiểm thử hỗ trợ:** một “probe” mở file tạm với option có thể cho biết provider/filesystem đang chấp nhận cấu hình trong điều kiện hiện tại. Nhưng nó không chứng minh mọi file và mọi mount đều hỗ trợ giống nhau, hoặc đảm bảo các tình huống phần cứng/OS bất thường. Luôn xử lý lỗi tại **thao tác thật**, không chỉ ở lúc khởi động.

## 15. Tùy chọn nào có ở Files API và FileChannel?

Không nên mặc định mọi API đều cho phép mọi OpenOption trong mọi cấu hình. Ví dụ:

- `Files.newOutputStream(path, options...)` nhận `OpenOption`, có các default khi không đưa option.
- `Files.newByteChannel(path, options, attrs...)` cho thao tác read/write qua `SeekableByteChannel`.
- `FileChannel.open(path, options...)` có tập option được mô tả rõ, bao gồm `SYNC`, `DSYNC`, `DELETE_ON_CLOSE`.
- `Files.writeString(path, text, charset, options...)` cho phép định nghĩa cách mở file nhưng các option được hỗ trợ cần kiểm tra tài liệu API cụ thể; không suy ra mọi tùy chọn đặc biệt từ `FileChannel` sẽ hợp lệ trên tất cả provider.
- `Files.copy` và `Files.move` có các `CopyOption` riêng; không nhầm với `StandardOpenOption`.

**Đặc biệt:** khi gọi `Files.newOutputStream(path)` mà **không có option**, phương thức dùng ngữ nghĩa mở ghi mặc định, thường tương đương `CREATE`, `TRUNCATE_EXISTING`, `WRITE`. Nhưng một khi truyền option một cách tường minh, phải hiểu tập option đang chỉ định và tác động tới việc tạo/truncate file.

## 16. Một ví dụ kết hợp DSYNC và FileChannel.force

Nếu cần minh họa hai cách yêu cầu đồng bộ, có thể dùng chương trình nhỏ sau để ghi một file mới với DSYNC, rồi dùng một file khác với `force(true)` theo đợt.

**Tệp `DsyncVsForceDemo.java`:**

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DsyncVsForceDemo {
    static void writeAll(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("durability-demo-");
        Path dsyncFile = directory.resolve("dsync.txt");
        Path forcedFile = directory.resolve("forced.txt");

        try {
            try (FileChannel channel = FileChannel.open(
                    dsyncFile,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.DSYNC)) {
                writeAll(channel, "one".getBytes(StandardCharsets.UTF_8));
            }

            try (FileChannel channel = FileChannel.open(
                    forcedFile,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                writeAll(channel, "two".getBytes(StandardCharsets.UTF_8));
                channel.force(true);
            }

            System.out.println("DSYNC content: "
                    + Files.readString(dsyncFile, StandardCharsets.UTF_8));
            System.out.println("force content: "
                    + Files.readString(forcedFile, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(dsyncFile);
            Files.deleteIfExists(forcedFile);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac DsyncVsForceDemo.java
java DsyncVsForceDemo
```

**Stdout nếu provider hỗ trợ các thao tác và chương trình thành công:**

```text
DSYNC content: one
force content: two
```

Ví dụ không đo độ trễ và không mô phỏng crash. Mục đích là thấy **cách đặt yêu cầu**:

- File thứ nhất mở với DSYNC từ đầu, nên từng cập nhật content trên channel chịu yêu cầu tương ứng.
- File thứ hai mở ghi thông thường, rồi `force(true)` được gọi tại checkpoint cuối để yêu cầu đồng bộ nội dung và metadata.

Nếu mục tiêu là độ bền ghi của nhiều tệp cùng lúc hoặc các thao tác metadata ở thư mục, các lời gọi này đơn lẻ không chứng minh toàn bộ transaction nhiều bước sẽ khôi phục nguyên tử sau crash.

## 17. SYNC/DSYNC và atomicity: đừng nhầm với ATOMIC_MOVE

Trong Java NIO, `StandardCopyOption.ATOMIC_MOVE` dành cho di chuyển/đổi tên file theo cơ chế atomic move khi provider hỗ trợ. Nó giải quyết **khía cạnh đổi tên nguyên tử**, trong khi `SYNC`/`DSYNC` là yêu cầu **đồng bộ cập nhật file**.

```text
Ghi file tạm       ───► đồng bộ content (theo policy)
       │
       ▼
Đóng file tạm
       │
       ▼
Đổi tên / move tới file đích ───► có thể yêu cầu atomic move
       │
       ▼
Cân nhắc durability của metadata thư mục / filesystem
```

Ngay cả khi một file đã được ghi đồng bộ, bước **rename** có thể có yêu cầu riêng về độ bền của entry thư mục. Một giao dịch crash-safe cần hiểu filesystem, hành vi directory metadata và cơ chế phục hồi, không chỉ thêm `SYNC` hoặc `ATOMIC_MOVE` một cách cơ học.

`ATOMIC_MOVE` có thể gây `AtomicMoveNotSupportedException` nếu không được hỗ trợ. Không nên tự fallback sang move thường rồi khẳng định thao tác vẫn atomic. Việc file được tạo bằng `DELETE_ON_CLOSE` thường còn làm phức tạp quy trình đổi tên vì mục đích option đó là xóa work file khi close.

## 18. Logging, hiệu năng và tính nhất quán

Yêu cầu đồng bộ sau mỗi cập nhật có thể tốn chi phí lớn. Ví dụ một service ghi 10.000 record nhỏ mà mỗi record có một syscall/write đồng bộ có thể khác hẳn mô hình gom nhiều record rồi force tại những điểm commit.

**Nhưng batching có đánh đổi:** nếu chờ một khoảng thời gian rồi mới `force`, dữ liệu ở giữa các checkpoint có thể bị mất khi hệ thống bị lỗi. Chọn phương án theo yêu cầu của nghiệp vụ:

| Đặc tính mong muốn | Hướng triển khai thường gặp |
| --- | --- |
| Ưu tiên ghi tuần tự nhanh, có thể chấp nhận mất một ít dữ liệu | Buffered writes + checkpoints theo lô |
| Mỗi cập nhật cần mức đồng bộ cao hơn | `SYNC` hoặc `DSYNC` theo policy |
| Muốn kiểm soát thời điểm đồng bộ tại transaction boundary | `FileChannel.force(true/false)` |
| Cần thay thế file cấu hình nguyên tử | Tệp tạm + move atomic khi hỗ trợ + kế hoạch durability riêng |
| Cần file tạm dùng một lần tự dọn | `DELETE_ON_CLOSE` nếu phù hợp và được hỗ trợ |

Đừng đo tốc độ `Files.writeString` rồi suy ra trực tiếp về mọi chế độ mở file. Benchmark nên dùng đúng loại tệp, kích thước record, filesystem, máy thật và chế độ durability.

## 19. Quan hệ với try-with-resources và wrapper streams

Các option như `DELETE_ON_CLOSE` có tác dụng ở **resource được mở**. Nếu output stream được bọc bằng `BufferedOutputStream`, việc đóng wrapper ngoài thường đóng delegate bên dưới, từ đó kích hoạt hành vi xóa best effort.

```java
try (OutputStream output = new BufferedOutputStream(
        Files.newOutputStream(temp,
                StandardOpenOption.WRITE,
                StandardOpenOption.DELETE_ON_CLOSE))) {
    output.write(payload);
}
```

Đây là đoạn minh họa với file tạm `temp` đã tồn tại và `byte[] payload`, cần import liên quan. `BufferedOutputStream` có thể giữ dữ liệu trong RAM đến khi flush/close, trong khi `SYNC`/ `DSYNC` ở resource tầng dưới chỉ liên quan đến **cập nhật đã thực sự đến tầng file**. Nếu cần cam kết về thời điểm đồng bộ, hãy kiểm soát thứ tự flush của wrapper trước khi đồng bộ channel thích hợp.

Nếu resource là **tài nguyên do caller sở hữu**, không được tự ý đóng chỉ để kích hoạt `DELETE_ON_CLOSE`; phải làm rõ quyền sở hữu. Đặc biệt không bọc `System.out` hoặc các stream chia sẻ rồi gọi `close()` tùy tiện.

## 20. Các lỗi phổ biến

| Nhầm lẫn | Vì sao sai | Cách hiểu đúng |
| --- | --- | --- |
| `SYNC` chỉ giống `flush()` | Flush xử lý buffer ứng dụng; SYNC là open option về đồng bộ cập nhật file | Phân biệt tầng buffer và thiết bị |
| `DSYNC` và `SYNC` giống nhau hoàn toàn | Metadata nằm trong phạm vi khác nhau | Chọn theo policy content/metadata |
| DSYNC luôn nhanh hơn SYNC | Hiệu năng phụ thuộc workload/provider/OS | Đo trên môi trường thật |
| SYNC bảo đảm toàn bộ transaction nhiều file là atomic | Không có cơ chế transaction tổng quát | Thiết kế commit/rename/recovery riêng |
| Gọi `FileChannel.force(false)` đồng bộ cả mọi metadata | `false` chỉ yêu cầu content | Dùng `true` khi cần metadata theo hợp đồng |
| `DELETE_ON_CLOSE` luôn xóa tệp tức thì | Chỉ best effort, thời điểm implementation-specific | Quản lý cleanup và xác minh phù hợp |
| DELETE_ON_CLOSE là secure erase | Không bảo đảm xóa vật lý/backup | Có chính sách bảo mật dữ liệu riêng |
| `Files.createTempFile` tự xóa khi close bất kỳ stream nào | Không có policy xóa mặc định như vậy | Xóa chủ động hoặc mở với option phù hợp |
| Mọi provider hỗ trợ SYNC/DSYNC/DELETE_ON_CLOSE | Không bảo đảm | Xử lý `UnsupportedOperationException` |
| Chỉ catch `IOException` là đủ cho mọi open-option error | Tùy chọn không hợp lệ có thể gây unchecked | Kiểm tra `IllegalArgumentException`, `UnsupportedOperationException` |
| Thử một lần ở khởi động là chứng minh vĩnh viễn | Filesystem/mount có thể khác hoặc thay đổi | Xử lý lỗi ở thao tác thực |
| Fallback bỏ SYNC nhưng vẫn báo “durable” | Thay đổi hợp đồng durability | Fail hoặc ghi rõ giảm yêu cầu |
| Chọn APPEND/TRUNCATE tùy tiện khi kết hợp option | Có tổ hợp không hợp lệ hoặc semantics khác | Đọc hợp đồng API và test |
| Chỉ đọc file lại để kiểm tra durability | OS cache có thể cho kết quả đọc đúng | Phân biệt chức năng và crash recovery |

## 21. Bài tập thực hành

1. Chạy `SyncWriteDemo` trên hệ điều hành/provider của bạn. Quan sát kết quả rồi giải thích vì sao đọc lại nội dung thành công không chứng minh crash durability.
2. Thay `SYNC` bằng `DSYNC` trong `SyncWriteDemo`. Kiểm tra output và nêu sự khác biệt trong phạm vi cam kết metadata.
3. Chạy `DsyncVsForceDemo`, thử `channel.force(false)` thay `true`; giải thích ý nghĩa của tham số.
4. Đo thời gian ghi nhiều record nhỏ với ghi thường, DSYNC và SYNC. Ghi rõ máy, filesystem, số lần ghi và cảnh báo cache/benchmark.
5. Chạy `DeleteOnCloseDemo`, in trạng thái tồn tại trước/sau `close()` trên provider hiện tại và đối chiếu với ý nghĩa best effort.
6. Bỏ `try-with-resources` trong `DeleteOnCloseDemo` và giải thích vì sao vòng đời file trở nên không đáng tin cậy; sau đó khôi phục cách đúng.
7. Tạo file tạm bằng `Files.createTempFile` mà không dùng DELETE_ON_CLOSE. Xác nhận việc đóng `OutputStream` bình thường không đồng nghĩa xóa file.
8. Viết test với `CREATE_NEW`: mở một file đã tồn tại và xác nhận lỗi; giải thích vì sao tùy chọn này hữu ích tránh ghi đè.
9. Thử một provider khác (ví dụ ZIP filesystem nếu môi trường có hỗ trợ). Ghi nhận nếu `SYNC` hoặc `DELETE_ON_CLOSE` bị từ chối; không bỏ qua lỗi một cách im lặng.
10. Thiết kế một chính sách fallback: nếu SYNC bị từ chối, service nên từ chối thao tác hay có thể giảm yêu cầu? Giải thích tác động nghiệp vụ.
11. Giải thích tại sao một `BufferedOutputStream` chưa flush không thể được đồng bộ xuống đĩa chỉ bằng việc yêu cầu một `FileChannel` khác force dữ liệu của nó.
12. Phân biệt `DELETE_ON_CLOSE` với file deletion an toàn đối với dữ liệu nhạy cảm.
13. Thiết kế quy trình thay thế file cấu hình: tệp tạm, ghi, đồng bộ, đóng, move và cleanup. Nêu rõ giới hạn `ATOMIC_MOVE` cũng như durability của metadata thư mục.
14. Giải thích vì sao việc provider chấp nhận mở file với `SYNC` không có nghĩa ứng dụng tự động có transaction và recovery logic.

## 22. Ghi nhớ

`SYNC`, `DSYNC` và `DELETE_ON_CLOSE` là những **yêu cầu về cách mở và quản lý tệp**, được thực hiện bởi provider/OS/filesystem với những giới hạn cụ thể.

1. **SYNC:** yêu cầu cập nhật content hoặc metadata đồng bộ xuống thiết bị lưu trữ.
2. **DSYNC:** yêu cầu cập nhật content đồng bộ; không đồng nhất phạm vi metadata với SYNC.
3. **DELETE_ON_CLOSE:** yêu cầu xóa file khi đóng trên cơ sở **best effort**, không phải bảo đảm xóa chắc chắn hoặc secure erase.
4. **FileChannel.force():** cho phép yêu cầu đồng bộ tại thời điểm được chọn; cần hiểu giới hạn thiết bị local/non-local và metadata.
5. **Provider support:** không coi các tùy chọn là phổ quát. Xử lý `UnsupportedOperationException`, `IllegalArgumentException`, `IOException` theo hợp đồng, không âm thầm thay đổi yêu cầu durability.
6. **Đúng lifecycle:** luôn đóng resource do mình sở hữu bằng `try-with-resources`; đừng dựa vào finalizer hoặc thời điểm JVM thoát để xóa file tạm.
7. **Đúng kiến trúc:** synchronous updates khác atomicity; một file hoặc nhiều file cần recovery protocol tùy bài toán.

### Tài liệu tham khảo chính thức

- [Java SE 17: `StandardOpenOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html), phần SYNC, DSYNC và DELETE_ON_CLOSE.
- [Java SE 17: `FileChannel`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html), các overload `open` và `force(boolean)`.
- [Java SE 17: `Files`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html), các hàm mở/ghi/xóa file.
- [Java SE 17: `FileSystemProvider`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/spi/FileSystemProvider.html), điểm mở rộng hệ thống tệp.
- [Java SE 17: `StandardCopyOption.ATOMIC_MOVE`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardCopyOption.html), phân biệt atomic move với chính sách ghi đồng bộ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="files-read-write-010">Files.copy kết hợp InputStream hoặc OutputStream</a>

<details>
<summary>Xem chi tiết</summary>

# `Files.copy` kết hợp `InputStream` hoặc `OutputStream` trong Java

Lớp `java.nio.file.Files` không chỉ sao chép **tệp sang tệp**. Nó còn cung cấp hai overload để chuyển dữ liệu giữa **đường dẫn tệp (`Path`)** và **luồng byte (`InputStream` hoặc `OutputStream`)**:

~~~java
long bytesToFile = Files.copy(inputStream, destinationPath);
long bytesFromFile = Files.copy(sourcePath, outputStream);
~~~

Nhờ đó, chương trình có thể lưu nội dung từ một luồng nhập vào tệp, hoặc gửi nội dung tệp tới một luồng xuất đã có sẵn. Ví dụ: tải byte về từ một nguồn, ghi báo cáo vào nơi nhận do caller quản lý, gửi tệp qua socket, hoặc xuất tệp vào một luồng bộ nhớ để kiểm thử.

Để sử dụng đúng, cần phân biệt **chiều dữ liệu**, **giá trị trả về**, **chính sách tệp đích tồn tại**, **quyền sở hữu stream** và **lỗi có thể xảy ra khi mới ghi được một phần**.

## 1. Ba nhóm overload `Files.copy` dễ nhầm

`Files` có ba hướng sao chép thường dùng:

| Chữ ký rút gọn | Dữ liệu đi từ đâu đến đâu? | Giá trị trả về |
|---|---|---|
| `Files.copy(InputStream in, Path target, CopyOption... options)` | **Luồng nhập → tệp đích** | `long` — số byte đã sao chép |
| `Files.copy(Path source, OutputStream out)` | **Tệp nguồn → luồng xuất** | `long` — số byte đã sao chép |
| `Files.copy(Path source, Path target, CopyOption... options)` | **Tệp nguồn → tệp đích** | `Path` — đường dẫn tệp đích |

Điểm quan trọng là overload được chọn dựa trên **kiểu tham số**, không dựa vào tên biến.

~~~text
1) InputStream ---- Files.copy ----> Path (tệp mới/tệp được thay thế)
2) Path (tệp) ---- Files.copy ----> OutputStream
3) Path (tệp) ---- Files.copy ----> Path (tệp)
~~~

Ví dụ:

~~~java
// in là InputStream; file là Path; out là OutputStream.
long first = Files.copy(in, file);
long second = Files.copy(file, out);
~~~

Đây là **đoạn minh họa**, không chạy độc lập nếu chưa có biến `in`, `file`, `out` và chưa xử lý `IOException`. Ở overload đầu, `file` đóng vai **đích**; ở overload thứ hai, chính `file` đóng vai **nguồn**. Cả hai kết quả kiểu `long` đều đo số **byte**, không phải số ký tự hay số dòng.

## 2. `Files.copy(InputStream, Path)`: lưu byte của stream vào tệp

Cú pháp:

~~~java
long count = Files.copy(sourceStream, targetPath);
~~~

Cách hoạt động theo hợp đồng API:

1. `sourceStream` là nguồn byte đang ở **vị trí đọc hiện tại**.
2. `targetPath` xác định tệp đích mà `Files` sẽ tạo/ghi.
3. Phương thức sao chép các byte từ nguồn đến **cuối luồng** vào tệp đích.
4. Trả về số byte đã chuyển khi hoàn tất.
5. **Không tự đóng `sourceStream`**. Caller vẫn chịu trách nhiệm vòng đời nguồn byte đó.

Nếu `InputStream` đã được đọc trước, chỉ **phần byte còn lại** được sao chép. Nếu nguồn đã ở EOF, số byte sao chép có thể bằng `0`, nhưng lưu ý tệp đích vẫn chịu chính sách tạo/thay thế tương ứng.

Ví dụ:

~~~java
try (InputStream source = new ByteArrayInputStream(
        "Hello".getBytes(StandardCharsets.UTF_8))) {
    long bytes = Files.copy(source, Path.of("hello.txt"));
    System.out.println(bytes); // 5
}
~~~

Đây là một **đoạn code cần đặt trong phương thức** có thể ném `IOException`, với import `java.io.*`, `java.nio.file.*` và `java.nio.charset.StandardCharsets`. `ByteArrayInputStream` không dùng tài nguyên tệp của hệ điều hành; `try-with-resources` ở đây minh họa rằng **caller**, không phải `Files.copy`, quyết định thời điểm đóng input.

### Tệp đích đã tồn tại thì sao?

Với overload `InputStream → Path`, theo mặc định, nếu tệp đích **đã tồn tại**, thao tác sao chép sẽ **thất bại**, thường bằng `FileAlreadyExistsException`. Có thể chủ động cho phép thay thế bằng:

~~~java
long count = Files.copy(
        sourceStream,
        targetPath,
        StandardCopyOption.REPLACE_EXISTING);
~~~

Đây là **đoạn minh họa** và `sourceStream` do caller cấp. Tùy chọn `REPLACE_EXISTING` cho phép thay thế tệp đích theo hợp đồng `Files.copy`. Điều này có thể **làm mất nội dung cũ** nếu bạn chưa có bản sao dự phòng. Không coi nó là thao tác bổ sung vào cuối file.

Với **riêng overload `Files.copy(InputStream, Path, ...)`**, `REPLACE_EXISTING` là tùy chọn sao chép được hỗ trợ theo hợp đồng Java SE 17; đừng áp dụng máy móc các option của `Path → Path` như `COPY_ATTRIBUTES` hoặc `NOFOLLOW_LINKS` vào overload này. Những lựa chọn không được hỗ trợ có thể gây `UnsupportedOperationException`.

Nếu tệp đích tồn tại nhưng không thể ghi, hoặc parent directory không tồn tại/không có quyền, thao tác cũng có thể thất bại vì lý do I/O. `REPLACE_EXISTING` không phải lệnh “bỏ qua mọi vấn đề về quyền và đường dẫn”.

### `Files` có đóng output tệp do nó tự mở không?

Với overload `InputStream → Path`, `Files` tự thực hiện phần mở/ghi tệp đích trong phạm vi thao tác của nó. **Caller không nhận một `OutputStream` tệp đích để phải tự đóng.** Ngược lại, `sourceStream` là stream bên ngoài truyền vào, nên `Files.copy` **không đóng** nó. Nếu nguồn là tài nguyên do mình mở như `Files.newInputStream(...)` hoặc stream mạng, caller đóng đúng lúc theo ownership.

## 3. `Files.copy(Path, OutputStream)`: chuyển byte từ tệp ra stream

Cú pháp:

~~~java
long count = Files.copy(sourcePath, destinationStream);
~~~

Ý nghĩa:

- `sourcePath` là đường dẫn tới tệp **nguồn**.
- `destinationStream` là nơi nhận **byte** đã do caller tạo hoặc được một tầng khác cấp.
- `Files` đọc nội dung tệp nguồn và ghi vào output stream.
- Kết quả kiểu `long` là số byte đã ghi.
- **`destinationStream` không bị đóng bởi `Files.copy`**.

Ví dụ:

~~~java
ByteArrayOutputStream captured = new ByteArrayOutputStream();
long bytes = Files.copy(Path.of("hello.txt"), captured);
System.out.println("Copied: " + bytes);
System.out.println(captured.toString(StandardCharsets.UTF_8));
~~~

Đây là **đoạn minh họa** với tệp `hello.txt` phải có sẵn, thêm import và xử lý `IOException`. `ByteArrayOutputStream` lưu byte trong RAM để kiểm thử hoặc truyền cho xử lý tiếp theo.

Với output là `System.out` hoặc một output của socket, nguyên tắc ownership càng quan trọng:

~~~java
Files.copy(Path.of("report.bin"), System.out);
System.out.flush(); // Chỉ flush khi hợp đồng của nơi nhận yêu cầu.
// Không System.out.close() trong helper chỉ dùng tạm.
~~~

Đoạn này chỉ minh họa: nó có thể **in byte nhị phân không đọc được** ra terminal nếu file không phải văn bản. `Files.copy` không giải mã hay đổi charset.

### Luồng xuất có thể đã có dữ liệu

`Files.copy(Path, OutputStream)` ghi byte vào **vị trí và trạng thái hiện tại** của `destinationStream`; nó không mang ý nghĩa “xóa sạch output và bắt đầu một tệp mới”. Với `ByteArrayOutputStream`, nếu đã ghi `prefix` trước đó thì byte sao chép được thêm vào sau `prefix`. Với file output, việc ghi đè hay nối tiếp phụ thuộc cách caller đã **mở output** (`CREATE`, `TRUNCATE_EXISTING`, `APPEND`, ...), không phải tham số của overload `Path → OutputStream`.

Cũng không nên giả định `Files.copy` sẽ tự `flush()` stream do caller truyền vào. Nếu output có buffer, việc flush/close theo giao thức và ownership là trách nhiệm của bên sở hữu.

## 4. Ví dụ Java 17 hoàn chỉnh: hai chiều copy và sở hữu stream

Chương trình sau chứng minh bằng code rằng:

- `Files.copy(InputStream, Path)` ghi byte từ input vào tệp, nhưng **không đóng input**.
- `Files.copy(Path, OutputStream)` gửi byte tệp sang output, nhưng **không đóng output**.
- Caller vẫn ghi thêm được vào output sau copy.
- Ghi vào một tệp đích đã tồn tại **không có `REPLACE_EXISTING`** gây `FileAlreadyExistsException`.
- Có `REPLACE_EXISTING` thì nội dung tệp được thay thế.
- Tất cả file/directory tạm do chương trình tạo được dọn sau khi sử dụng.

Lưu thành **`FilesCopyStreamsDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FilesCopyStreamsDemo {
    static final class TrackedInput extends FilterInputStream {
        boolean closed;

        TrackedInput(InputStream source) {
            super(source);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    static final class TrackedOutput extends FilterOutputStream {
        boolean closed;

        TrackedOutput(OutputStream destination) {
            super(destination);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("copy-streams-");
        Path targetFile = directory.resolve("target.txt");

        try {
            TrackedInput input = new TrackedInput(
                    new ByteArrayInputStream(
                            "Java I/O".getBytes(StandardCharsets.UTF_8)));
            try (input) {
                long bytes = Files.copy(input, targetFile);
                System.out.println("To file: " + bytes);
                System.out.println("Input closed by copy: " + input.closed);
            }
            System.out.println("Input closed by owner: " + input.closed);

            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            TrackedOutput output = new TrackedOutput(captured);
            try (output) {
                long bytes = Files.copy(targetFile, output);
                System.out.println("From file: " + bytes);
                System.out.println("Output closed by copy: " + output.closed);

                output.write('!');
                output.flush();
            }
            System.out.println("Output closed by owner: " + output.closed);
            System.out.println("Captured: "
                    + captured.toString(StandardCharsets.UTF_8));

            try (InputStream second = new ByteArrayInputStream(
                    "Updated".getBytes(StandardCharsets.UTF_8))) {
                try {
                    Files.copy(second, targetFile);
                } catch (FileAlreadyExistsException ex) {
                    System.out.println(
                            "Existing target: FileAlreadyExistsException");
                }
            }

            try (InputStream replacement = new ByteArrayInputStream(
                    "Updated".getBytes(StandardCharsets.UTF_8))) {
                long bytes = Files.copy(replacement, targetFile,
                        StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Replaced bytes: " + bytes);
            }
            System.out.println("File after replace: "
                    + Files.readString(targetFile, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(targetFile);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 FilesCopyStreamsDemo.java
java FilesCopyStreamsDemo
~~~

**Output dự kiến** trong môi trường có quyền tạo/xóa thư mục tạm:

~~~text
To file: 8
Input closed by copy: false
Input closed by owner: true
From file: 8
Output closed by copy: false
Output closed by owner: true
Captured: Java I/O!
Existing target: FileAlreadyExistsException
Replaced bytes: 7
File after replace: Updated
~~~

Giải thích:

1. Chuỗi `Java I/O` có 8 byte ASCII/UTF-8. `Files.copy(input, targetFile)` sao chép 8 byte vào tệp mới và trả `8L`.
2. `TrackedInput.closed` bằng `false` ngay sau lần copy vì `Files.copy` **không đóng nguồn input** được truyền vào. Khi rời `try (input)`, caller đóng input và cờ thành `true`.
3. `Files.copy(targetFile, output)` sao chép 8 byte tệp sang output. Trước khi caller đóng output, cờ vẫn `false` và `output.write('!')` tiếp tục hoạt động.
4. Đóng `TrackedOutput` bằng `try (output)` làm cờ thành `true`. `ByteArrayOutputStream` cho phép lấy nội dung đã ghi ngay cả sau khi bị đóng; không suy rộng hành vi này sang file/socket.
5. Sao chép lần hai tới **cùng tệp đang tồn tại** mà không có option thay thế sẽ ném `FileAlreadyExistsException`. Ví dụ bắt riêng ngoại lệ dự kiến để tiếp tục.
6. `REPLACE_EXISTING` cho phép ghi `Updated` (7 byte) thay cho nội dung cũ. `Files.readString(targetFile, UTF_8)` đọc lại để xác minh.
7. `finally` xóa file và thư mục tạm nếu thao tác môi trường cho phép. Nếu filesystem gặp lỗi, IOException vẫn phải được xem xét; ví dụ không giả định mọi hệ thống có quyền ghi vào thư mục tạm.

Đây là ví dụ có `main` chạy độc lập trên Java 17+, dùng đường dẫn tạm nên không yêu cầu file đầu vào có sẵn. Nó kiểm tra **lời gọi đóng trên wrapper** bằng biến `closed`, không phải công cụ đo trạng thái file descriptor của hệ điều hành.

## 5. So sánh với `InputStream.transferTo(OutputStream)`

Một phương án sao chép khác có từ Java 9 là:

~~~java
long count = input.transferTo(output);
~~~

`transferTo` sao chép **byte còn lại** từ `InputStream` sang `OutputStream`, không tự đóng cả hai stream. Nó khác các overload `Files.copy` ở loại đích/nguồn:

| Tác vụ | Cách ngắn gọn | Ai quản lý stream ngoài? |
|---|---|---|
| InputStream đã có → tệp đích | `Files.copy(input, path)` | Caller quản lý **input** |
| Tệp nguồn → OutputStream đã có | `Files.copy(path, output)` | Caller quản lý **output** |
| InputStream đã có → OutputStream đã có | `input.transferTo(output)` | Caller quản lý **cả hai** |
| Tệp nguồn → tệp đích | `Files.copy(from, to)` | `Files` quản lý thao tác qua hai `Path` |

Nếu bạn tự mở hai đầu luồng của một thao tác sao chép, có thể chọn:

~~~java
try (InputStream input = Files.newInputStream(from);
     OutputStream output = Files.newOutputStream(to)) {
    input.transferTo(output);
}
~~~

Đây là **đoạn minh họa**: `from` và `to` là `Path`, `Files.newOutputStream(to)` có chính sách tạo/cắt ngắn tệp mặc định **khác** chính sách mặc định của `Files.copy(InputStream, to)` khi tệp đích đã tồn tại. Vì vậy hai cách không phải tương đương tuyệt đối về **xử lý tệp đích có sẵn**, dù cùng truyền các byte.

Dùng `Files.copy` theo đúng overload nếu bạn muốn **Files tự quản lý đầu tệp** và caller chỉ cần quản lý đầu stream mà mình có.

## 6. Quyền sở hữu tài nguyên và đóng luồng

Khi làm việc với stream, câu hỏi quan trọng là: **ai mở, ai sở hữu và ai được phép đóng?**

### InputStream truyền vào `Files.copy(input, path)`

~~~java
static long save(Path target, InputStream source) throws IOException {
    return Files.copy(source, target);
}
~~~

Nếu hàm này chỉ **mượn** `source` từ caller, nó **không đóng** nguồn. Caller có thể tự mở file, socket, hoặc cấp luồng dữ liệu theo vòng đời khác. Khi viết JavaDoc cho hàm, nên nêu rõ: “Phương thức sao chép byte còn lại và **không đóng source**”.

Nếu ứng dụng **chủ động chuyển quyền sở hữu** source cho hàm, hàm có thể được thiết kế để đóng source khi kết thúc, nhưng cần hợp đồng rõ; không tự ý thêm `try (source)` vào một helper đang được dùng theo quy tắc mượn.

### OutputStream truyền vào `Files.copy(path, output)`

~~~java
static long send(Path source, OutputStream destination)
        throws IOException {
    return Files.copy(source, destination);
}
~~~

Hàm này cũng **không đóng** output khi chỉ mượn. Ví dụ `send(file, System.out)` không nên đóng `System.out`, bởi những thành phần khác trong chương trình có thể còn in log hoặc kết quả sau đó.

Nếu output đã bọc bằng `BufferedOutputStream`, caller sở hữu lớp bọc và quyết định thời điểm `flush`/`close`. `Files.copy` không có quyền quyết định thời điểm kết thúc toàn bộ nơi nhận.

### Vì sao `try-with-resources` phải đặt đúng chỗ?

**Đúng — caller tự mở, caller tự đóng:**

~~~java
try (InputStream in = Files.newInputStream(sourceFile)) {
    long count = Files.copy(in, newFile);
}
~~~

**Sai nếu helper đang mượn stream của người khác:**

~~~java
static long saveBorrowed(InputStream fromCaller, Path target)
        throws IOException {
    try (InputStream borrowed = fromCaller) {
        return Files.copy(borrowed, target);
    } // Đóng luôn stream của caller!
}
~~~

`try-with-resources` làm đúng việc mà nó được thiết kế: đóng các tài nguyên đã khai báo. Nhưng việc **khai báo một tài nguyên được mượn trong try** có thể vi phạm ownership. Đổi tên `borrowed` không tạo một stream mới hay chuyển quyền sở hữu.

## 7. Copy là byte I/O, không phải tự chuyển mã văn bản

`Files.copy` với `InputStream` hoặc `OutputStream` sao chép **byte nguyên trạng**. Nó không phân biệt file là ảnh, ZIP, PDF, CSV hay văn bản; không tự giải mã ký tự.

Ví dụ một chuỗi tiếng Việt:

~~~java
byte[] bytes = "Cà phê".getBytes(StandardCharsets.UTF_8);
try (InputStream input = new ByteArrayInputStream(bytes)) {
    Files.copy(input, Path.of("coffee.txt"));
}
~~~

Tệp đích nhận **byte UTF-8 do chính đoạn code tạo**, chứ không phải do `Files.copy` chọn charset. Nếu đầu vào vốn được mã hóa bằng một charset khác, `Files.copy` vẫn sao chép đúng byte của charset đó; muốn đọc thành `String` phải biết charset nguồn tương ứng.

Khi cần **chuyển mã** từ một charset sang charset khác, phải giải mã byte thành ký tự rồi mã hóa lại theo charset đích, ví dụ sử dụng `InputStreamReader`/`OutputStreamWriter` với charset tường minh. Tuyệt đối không dùng chuyển đổi ký tự nếu mục tiêu là sao chép dữ liệu nhị phân nguyên trạng.

## 8. Sao chép tệp lớn, stream chậm và xử lý lỗi

Một lời gọi `Files.copy` có thể xử lý lượng byte lớn mà không cần tự tạo `byte[]` chứa toàn bộ dữ liệu. Tuy nhiên, nó không phải phép toán nguyên tử:

- **Byte có thể được ghi một phần rồi mới gặp lỗi.** Với `InputStream → Path`, tệp đích có thể đã tồn tại ở trạng thái chưa hoàn chỉnh khi `IOException` xảy ra. Với `Path → OutputStream`, output caller cũng có thể đã nhận một phần byte.
- **Tác vụ có thể chặn.** Đọc từ socket hoặc pipe khi bên gửi chưa kết thúc dữ liệu có thể khiến `Files.copy(input, path)` chờ tiếp. Nếu input không bao giờ báo EOF, sao chép có thể kéo dài không xác định.
- **Thiếu dung lượng hoặc lỗi quyền:** có thể khiến ghi file đích thất bại. Hãy có chiến lược cleanup, kiểm tra dữ liệu sau ghi và phản hồi người dùng.
- **Không dùng return `long` như checksum:** hai tệp cùng số byte không chứng minh nội dung bằng nhau. Khi cần xác thực toàn vẹn, cân nhắc checksum/hash theo thiết kế giao thức.
- **Không dùng để giới hạn kích thước nguồn tự động:** các overload nêu trên không có tham số “chỉ copy tối đa N byte”. Với input không tin cậy, thiết kế một luồng giới hạn hoặc vòng copy có kiểm soát kích thước trước/sau, tùy yêu cầu.

### Tránh phá file nguồn và file đích trùng nhau

Một sai lầm nguy hiểm là tự mở `OutputStream` trên **chính tệp nguồn**, chẳng hạn:

~~~java
Path file = Path.of("data.bin");
try (OutputStream output = Files.newOutputStream(file)) {
    Files.copy(file, output); // NGUY HIỂM!
}
~~~

Đây là **đoạn minh họa cần tránh**. `Files.newOutputStream(file)` theo mặc định có thể **cắt ngắn tệp ngay khi mở**, trước khi `Files.copy` kịp đọc. Khi cần copy file sang file, nên dùng `Files.copy(Path, Path, ...)` với **đường dẫn nguồn và đích riêng biệt**, cùng chính sách khi đích tồn tại phù hợp.

### Sao chép an toàn khi không muốn file cũ bị hỏng

Nếu cần xuất dữ liệu vào một file đang được người dùng sử dụng, thường nên:

1. Chọn file tạm trong thư mục phù hợp.
2. Sao chép toàn bộ dữ liệu vào file tạm; xử lý lỗi và dọn file tạm nếu không hoàn thành.
3. Kiểm tra/validate dữ liệu theo hợp đồng ứng dụng.
4. Thay thế file đích theo một cơ chế phù hợp filesystem và yêu cầu về atomicity/durability.

`Files.move` với `ATOMIC_MOVE` có thể dùng khi provider hỗ trợ, nhưng **không được mặc định mọi filesystem hỗ trợ atomic move**. Không nhầm `REPLACE_EXISTING` của `Files.copy` với thao tác cập nhật nguyên tử, và cũng không coi `flush` là bảo đảm fsync.

## 9. Dùng `Files.copy(Path, Path)` khi cả hai đầu đều là tệp

Nếu đã có hai đường dẫn tệp và không cần can thiệp stream ở giữa:

~~~java
Path result = Files.copy(
        from,
        to,
        StandardCopyOption.REPLACE_EXISTING);
~~~

Đây là **đoạn minh họa** với hai biến `Path` và xử lý `IOException`. Giá trị trả về là `Path`, **không phải số byte**. Overload `Path → Path` có thêm các lựa chọn đặc thù, như `COPY_ATTRIBUTES` hoặc `LinkOption.NOFOLLOW_LINKS` tùy định dạng và hệ thống tệp. Không nhầm các option này với các overload nhận `InputStream`/`OutputStream`.

Lưu ý: `Files.copy(Path, Path)` có hợp đồng riêng khi source là directory hoặc symbolic link; việc copy một directory **không tự động sao chép đệ quy toàn bộ cây**. Khi làm việc với thư mục hay liên kết mềm, hãy đọc đúng Javadoc của overload và tùy chọn tương ứng.

## 10. Các lỗi thường gặp và cách sửa

| Sai lầm | Vấn đề | Cách sửa |
|---|---|---|
| Gọi `Files.copy(input, path)` và cho rằng input bị đóng | API không đóng stream input đã cấp | Caller quản lý input bằng `try-with-resources` khi sở hữu |
| Gọi `Files.copy(path, out)` rồi cho rằng output bị đóng | Output vẫn thuộc caller | Caller flush/close theo vòng đời của mình |
| Dùng `Files.copy(InputStream, Path)` khi tệp đích đã tồn tại nhưng không có `REPLACE_EXISTING` | Có thể nhận `FileAlreadyExistsException` | Xác định rõ chính sách tránh ghi đè hay thay thế |
| Đưa `COPY_ATTRIBUTES` vào overload InputStream → Path | Không phải tùy chọn được overload này hỗ trợ | Dùng `REPLACE_EXISTING` khi cần hoặc chọn Path → Path |
| Nhầm giá trị trả về của cả ba overload | Path → Path trả `Path`; hai overload stream trả `long` | Kiểm tra chữ ký và kiểu |
| Cố giải mã byte trong quá trình sao chép file ảnh/ZIP | Phá nội dung nhị phân | Copy byte trực tiếp |
| Coi byte count bằng nhau là toàn vẹn dữ liệu | Cùng dung lượng có thể khác nội dung | Kiểm tra hash/chữ ký hoặc đối chiếu theo giao thức |
| Để helper đóng stream được caller truyền vào | Tầng khác có thể mất quyền đọc/ghi | Định nghĩa ownership rõ, không close stream mượn |
| Mở output trên cùng file trước khi copy từ file đó | Có thể cắt ngắn nguồn | Phân biệt source/destination, đừng mở đích phá nguồn |
| Dùng `Files.copy` cho luồng dữ liệu vô hạn mà không kiểm soát | Có thể chặn hoặc tiêu thụ hết dung lượng | Có giới hạn byte, timeout/cancel theo tầng thích hợp |
| Giả định copy lỗi thì file đích luôn nguyên vẹn | Có thể để lại byte đã ghi một phần | Dùng file tạm/cleanup và chiến lược cập nhật an toàn |

## 11. Tự kiểm tra

**Câu 1.** Có một `InputStream` do HTTP client cung cấp và muốn lưu vào `download.bin` chưa tồn tại. Nên gọi overload nào? Ai đóng input?

> Dùng `Files.copy(input, path)` để truyền byte vào file đích. **Bên sở hữu stream HTTP** theo hợp đồng của client phải đóng input; `Files.copy` không tự đóng nó.

**Câu 2.** Có file `archive.zip` và muốn gửi nguyên byte vào `OutputStream` do caller cung cấp. Nên dùng gì?

> `Files.copy(Path.of("archive.zip"), output)` trả số byte đã truyền. Vì `output` do caller quản lý, không được tự đóng output trong helper chỉ mượn.

**Câu 3.** `Files.copy(InputStream, Path)` đang ghi vào tệp đã tồn tại; vì sao phải cân nhắc `REPLACE_EXISTING`?

> Theo mặc định thao tác thất bại khi đích tồn tại. `REPLACE_EXISTING` cho phép thay thế, nhưng có thể làm mất dữ liệu cũ và không tự biến copy thành cập nhật nguyên tử.

**Câu 4.** Gọi `Files.copy(pathA, pathB)` và `Files.copy(pathA, output)` thì hai kết quả cùng kiểu gì?

> **Không.** `Path → Path` trả `Path` của đích; `Path → OutputStream` trả `long` số byte.

**Câu 5.** `Files.copy(sourceFile, System.out)` có tự biến PDF thành văn bản dễ đọc không? Có nên đóng `System.out` ngay sau đó?

> **Không.** Copy giữ nguyên byte nhị phân; PDF không được chuyển thành text. Và `System.out` là stream chung, helper không được tự ý đóng.

**Câu 6.** Gọi `Files.copy(input, path)` được `1024L`. Điều này chứng minh điều gì và chưa chứng minh điều gì?

> Nó báo đã sao chép 1024 **byte** thành công theo giá trị trả về. Nó **không chứng minh** nội dung ứng với một checksum dự kiến, không bảo đảm bền vững trên thiết bị hoặc đầu vào ban đầu phải có đúng 1024 byte nếu stream đã đọc trước một phần.

## Ghi nhớ

- **`Files.copy(InputStream, Path, ...)`:** sao chép byte từ **luồng vào → tệp**, trả `long`, **không đóng input**, mặc định không ghi đè đích tồn tại.
- **`Files.copy(Path, OutputStream)`:** sao chép byte từ **tệp → luồng ra**, trả `long`, **không đóng output**.
- **`Files.copy(Path, Path, ...)`:** sao chép **tệp → tệp**, trả `Path`; các tùy chọn có hợp đồng riêng.
- **`InputStream.transferTo(OutputStream)`:** dành cho trường hợp cả hai đầu đã là stream; **không đóng hai đầu**.
- **Sao chép byte không chọn charset**; stream do bên nào sở hữu thì bên đó chịu trách nhiệm vòng đời.
- **I/O có thể thất bại sau khi ghi một phần**: thiết kế ghi đè, cleanup và xác thực dữ liệu theo yêu cầu thực tế.

**Tham khảo:** [Java SE 17 — `Files.copy`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html), [`InputStream.transferTo`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html#transferTo(java.io.OutputStream)), [`StandardCopyOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardCopyOption.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="files-read-write-011">Files.mismatch và so sánh nội dung hai tệp</a>

<details>
<summary>Xem chi tiết</summary>

# `Files.mismatch` và so sánh nội dung hai tệp trong Java I/O

## 1. Khi nào hai tệp được xem là giống nhau?

Khi kiểm tra sao lưu, đồng bộ dữ liệu, phát hiện file bị thay đổi hoặc xác minh kết quả xuất dữ liệu, câu hỏi quan trọng thường là: **hai tệp có chứa đúng cùng một dãy byte theo cùng thứ tự không?**

Hai tệp có thể:

- Có **tên khác nhau** nhưng byte giống hệt nhau.
- Có **kích thước bằng nhau** nhưng một hoặc nhiều byte khác nhau.
- Có **thời gian sửa đổi** khác nhau nhưng nội dung byte không đổi.
- Chứa văn bản nhìn giống nhau nhưng khác byte vì charset, BOM, dạng chuẩn hóa Unicode hoặc cách xuống dòng.
- Chứa cùng giá trị logic nhưng cách biểu diễn nhị phân khác nhau, ví dụ metadata nằm ngay trong cấu trúc file.

Từ Java 12, lớp `java.nio.file.Files` cung cấp phương thức **`Files.mismatch(Path path, Path path2)`** để tìm **vị trí byte đầu tiên khác nhau** giữa hai tệp. Đây là API thích hợp khi mục tiêu là so sánh nội dung byte chứ không phải tên, kích thước hay ngày sửa.

**Tư duy nền tảng:** `Files.mismatch` kiểm tra *binary equality*, không tự kiểm tra hai tệp có “cùng ý nghĩa” về mặt văn bản, JSON, ảnh hoặc tài liệu.

## 2. Chữ ký phương thức và ý nghĩa giá trị trả về

Phương thức trong `java.nio.file.Files`:

```java
public static long mismatch(Path path, Path path2) throws IOException
```

Trong đó:

- `path` và `path2` là hai `Path` trỏ tới các tệp cần so sánh.
- Giá trị trả về kiểu **`long`** là **offset tính từ 0 của byte đầu tiên khác nhau**.
- Trả **`-1L`** khi không tìm thấy byte khác nhau và hai tệp có độ dài bằng nhau.
- Nếu một tệp là tiền tố byte chính xác của tệp kia nhưng **ngắn hơn**, kết quả là **kích thước byte của tệp ngắn hơn**, vì ở offset đó một bên đã hết byte.
- Lỗi truy cập/đọc tệp có thể phát sinh **`IOException`**; phương thức không trả một mã số đặc biệt để biểu diễn lỗi I/O.

Ví dụ với dãy byte:

```text
Tệp A: 10 20 30 40
Tệp B: 10 20 30 40     -> -1  (bằng nhau)
Tệp C: 10 20 99 40     ->  2  (khác byte thứ ba)
Tệp D: 10 20           ->  2  (D kết thúc trước A)
Tệp E: 99 20 30 40     ->  0  (khác ngay byte đầu)
Tệp F: [rỗng]          ->  0  (F là tiền tố rỗng của A)
```

Offset **bắt đầu từ 0**, không phải 1: byte thứ nhất có offset `0`, byte thứ ba có offset `2`. Kết quả `0` **không có nghĩa là hai tệp giống nhau**; chỉ duy nhất `-1L` biểu diễn không có mismatch.

Bảng đối chiếu:

| Kết quả | Cách hiểu |
| ---: | --- |
| `-1L` | Hai tệp có cùng độ dài và cùng byte ở mọi vị trí |
| `0L` | Khác ở byte đầu tiên, hoặc một tệp rỗng còn tệp kia không |
| `k >= 0` | Byte đầu tiên không tương ứng ở offset `k` |
| Ngoại lệ `IOException` | Không hoàn thành được việc so sánh vì lỗi I/O |

Phương thức không sửa nội dung tệp theo hợp đồng sử dụng thông thường; nhiệm vụ của nó là đọc và đối chiếu.

## 3. Ví dụ Java 17+ hoàn chỉnh: so sánh tệp giống, khác và tệp tiền tố

Ví dụ này dùng thư mục tạm và các byte đã biết trước để đầu ra có thể kiểm chứng. Tạo file **`FilesMismatchDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FilesMismatchDemo {
    public static void main(String[] args) throws IOException {
        Path folder = Files.createTempDirectory("files-mismatch-");
        Path base = folder.resolve("base.bin");
        Path same = folder.resolve("same.bin");
        Path different = folder.resolve("different.bin");
        Path shorter = folder.resolve("shorter.bin");
        Path first = folder.resolve("first.bin");
        Path empty = folder.resolve("empty.bin");

        try {
            Files.write(base, new byte[]{10, 20, 30, 40});
            Files.write(same, new byte[]{10, 20, 30, 40});
            Files.write(different, new byte[]{10, 20, 99, 40});
            Files.write(shorter, new byte[]{10, 20});
            Files.write(first, new byte[]{99, 20, 30, 40});
            Files.write(empty, new byte[0]);

            System.out.println("same=" + Files.mismatch(base, same));
            System.out.println("different="
                    + Files.mismatch(base, different));
            System.out.println("shorter="
                    + Files.mismatch(base, shorter));
            System.out.println("first=" + Files.mismatch(base, first));
            System.out.println("empty=" + Files.mismatch(base, empty));
            System.out.println("self=" + Files.mismatch(base, base));
        } finally {
            Files.deleteIfExists(base);
            Files.deleteIfExists(same);
            Files.deleteIfExists(different);
            Files.deleteIfExists(shorter);
            Files.deleteIfExists(first);
            Files.deleteIfExists(empty);
            Files.deleteIfExists(folder);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 FilesMismatchDemo.java
java FilesMismatchDemo
```

Kết quả khi các thao tác tệp tạm đều thành công:

```text
same=-1
different=2
shorter=2
first=0
empty=0
self=-1
```

Giải thích:

1. `same` và `base` có byte bằng nhau dù là **hai đường dẫn** và hai tệp được tạo riêng biệt.
2. `different` có cùng kích thước nhưng byte tại index `2` là `99` thay cho `30`.
3. `shorter` khớp hai byte đầu rồi kết thúc, nên mismatch nằm ở index `2`.
4. `first` khác ngay index `0`.
5. `empty` không có byte để khớp với `base`, do đó index `0` là vị trí bắt đầu khác nhau.
6. So sánh tệp với chính nó cho kết quả `-1`.

Chương trình cố gắng xóa các tệp tạm trong `finally`; nếu hệ thống không cho tạo/đọc/xóa tệp, thao tác có thể báo `IOException`. Đối với sản phẩm thực tế, cần xử lý lỗi cleanup cẩn thận để không che nguyên nhân chính, và tuyệt đối không tự động xóa tệp người dùng.

## 4. Thuật toán về mặt logic: so byte cho tới khi phát hiện khác biệt

Có thể hình dung `Files.mismatch(a, b)` theo thuật toán:

```text
offset = 0
Lặp:
    lấy byte tiếp theo từ A và B
    nếu cả hai EOF: trả -1
    nếu chỉ một bên EOF: trả offset
    nếu hai byte khác nhau: trả offset
    offset = offset + 1
```

Đây là **mô hình ngữ nghĩa**, không phải mã nguồn triển khai chính thức của JDK. Java có thể tối ưu cách đọc, bộ đệm và phương pháp đối chiếu, nhưng kết quả API phải phản ánh vị trí mismatch đầu tiên.

Nếu hai tệp khác nhau ngay byte đầu, không cần xét ý nghĩa các byte sau để xác định kết quả. Nếu hai tệp giống nhau hoàn toàn, hệ thống phải có đủ thông tin để xác nhận nội dung toàn bộ phạm vi cần so sánh. Không dựa vào một giả định cố định rằng API sẽ dùng bao nhiêu RAM, chia block bao nhiêu byte hoặc gọi hệ điều hành bao nhiêu lần: đó là chi tiết triển khai.

Vì offset là `long`, API có thể biểu diễn vị trí lớn hơn giới hạn `int`, phù hợp khi làm việc với các tệp rất lớn.

## 5. So sánh nội dung với `Files.size`, tên và thời gian sửa đổi

Các API trả lời **các câu hỏi khác nhau**:

| API/thuộc tính | Cho biết điều gì? | Có chứng minh nội dung giống hệt không? |
| --- | --- | --- |
| `Files.mismatch(a, b) == -1L` | Byte trong hai tệp bằng nhau tại thời điểm được so sánh | **Có**, với giả định nguồn ổn định trong lúc kiểm tra |
| `Files.size(a) == Files.size(b)` | Hai kích thước theo byte bằng nhau | **Không** |
| `Files.getLastModifiedTime(path)` | Timestamp do hệ thống tệp cung cấp | **Không** |
| `a.equals(b)` | Hai đối tượng `Path` bằng nhau về cách biểu diễn theo provider | **Không** nếu là hai tệp khác đường dẫn; nó không so byte |
| `Files.isSameFile(a, b)` | Hai đường dẫn được xác định trỏ tới **cùng một tệp** | Không so sánh nội dung của hai tệp khác nhau |
| So sánh tên file hoặc extension | Metadata hoặc quy ước đặt tên | **Không** |

Ví dụ:

```text
A.bin = 01 02 03 04   -> size = 4
B.bin = 01 02 99 04   -> size = 4

Files.size(A) == Files.size(B)       -> true
Files.mismatch(A, B)                 -> 2
```

**Tối ưu đơn giản:** Nếu chỉ cần biết **có bằng nhau hay không**, có thể kiểm tra kích thước trước. Nếu hai kích thước khác nhau thì hai tệp chắc chắn không giống hệt byte, không cần gọi `mismatch`. Nếu kích thước bằng nhau vẫn phải so nội dung. Tuy nhiên, nếu mục tiêu là **tìm vị trí khác nhau đầu tiên**, không được chỉ lấy kích thước làm offset: hai tệp có thể vừa khác kích thước **vừa** có byte khác sớm hơn.

Ví dụ A có byte `01 02 03`, B có `01 99 03 04`. Mismatch là `1`, **không phải** `3` (kích thước tệp ngắn).

## 6. `mismatch` đếm byte, không đếm ký tự, dòng hoặc code point

Java `String` và các API đọc văn bản vận hành theo ký tự Unicode, nhưng `Files.mismatch` so **byte của tệp gốc**. Không có tham số `Charset` và không có bước chuẩn hóa văn bản ngầm.

Với UTF-8, ký tự có dấu có thể chiếm nhiều byte. Ví dụ:

```text
"café" mã hóa UTF-8:
63 61 66 C3 A9
          ^^
index: 0  1  2  3  4

"cafe\u0301" mã hóa UTF-8:
63 61 66 65 CC 81
          ^^
index: 0  1  2  3  4  5
```

Hai chuỗi có thể **trông giống nhau** vì một chuỗi dùng ký tự `é` dựng sẵn (`U+00E9`) và chuỗi còn lại dùng `e` cộng dấu sắc kết hợp (`U+0065` và `U+0301`). Tuy nhiên, byte đầu tiên khác nhau ở **offset 3**.

Tương tự, hai tệp có nội dung nhìn như cùng các dòng nhưng một bên dùng `LF` (byte `0A`) còn bên kia dùng `CRLF` (byte `0D 0A`) sẽ khác nhau ngay dấu xuống dòng đầu tiên.

**Các khác biệt thường bị bỏ sót khi nhìn bằng trình soạn thảo:** BOM UTF-8 ở đầu file, dấu cách cuối dòng, tab so với khoảng trắng, Unicode NFC/NFD, dấu xuống dòng, charset, kết thúc file có hoặc không có newline.

Nếu ứng dụng muốn hỏi “văn bản sau khi chuẩn hóa có giống nhau không?”, cần một bài toán **so sánh ngữ nghĩa văn bản** khác, không phải nhiệm vụ của `Files.mismatch`.

## 7. Ví dụ chạy được: văn bản nhìn giống nhưng byte khác

Tạo **`TextBytesMismatchDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;

public class TextBytesMismatchDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("text-mismatch-");
        Path composed = dir.resolve("composed.txt");
        Path decomposed = dir.resolve("decomposed.txt");
        Path lf = dir.resolve("lf.txt");
        Path crlf = dir.resolve("crlf.txt");

        try {
            Files.writeString(composed, "caf\u00E9", StandardCharsets.UTF_8);
            Files.writeString(decomposed, "cafe\u0301",
                    StandardCharsets.UTF_8);
            Files.writeString(lf, "A\nB", StandardCharsets.UTF_8);
            Files.writeString(crlf, "A\r\nB", StandardCharsets.UTF_8);

            System.out.println("unicode-offset="
                    + Files.mismatch(composed, decomposed));
            System.out.println("newlines-offset="
                    + Files.mismatch(lf, crlf));

            String left = Normalizer.normalize(
                    Files.readString(composed, StandardCharsets.UTF_8),
                    Normalizer.Form.NFC);
            String right = Normalizer.normalize(
                    Files.readString(decomposed, StandardCharsets.UTF_8),
                    Normalizer.Form.NFC);
            System.out.println("normalized-text-equal="
                    + left.equals(right));
        } finally {
            Files.deleteIfExists(composed);
            Files.deleteIfExists(decomposed);
            Files.deleteIfExists(lf);
            Files.deleteIfExists(crlf);
            Files.deleteIfExists(dir);
        }
    }
}
```

Chạy trên Java 17:

```bash
javac -encoding UTF-8 TextBytesMismatchDemo.java
java TextBytesMismatchDemo
```

Đầu ra:

```text
unicode-offset=3
newlines-offset=1
normalized-text-equal=true
```

Đây không phải mâu thuẫn. `Files.mismatch` trả lời **“byte có giống hệt nhau không?”**, còn phép chuẩn hóa `Normalizer.Form.NFC` rồi `String.equals` trả lời **“chuỗi đã chuẩn hóa có cùng đơn vị mã UTF-16 không?”**.

Khi so sánh văn bản, phải xác định trước hợp đồng: có bỏ qua chữ hoa/chữ thường, dấu cách, dòng trống, thứ tự dòng hoặc các chi tiết định dạng khác hay không? Mỗi lần bỏ qua một khác biệt, bạn thay đổi **định nghĩa về sự bằng nhau**. Không chuẩn hóa tùy tiện nếu mục tiêu là sao lưu hoặc xác minh file nguyên bản.

## 8. Cách viết hàm tiện ích trả lời “hai tệp có giống nhau không?”

Nếu chỉ cần boolean và muốn ném lỗi lên lớp gọi để xử lý, dùng:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileContentEquality {
    private FileContentEquality() {
    }

    public static boolean sameBytes(Path first, Path second)
            throws IOException {
        return Files.mismatch(first, second) == -1L;
    }
}
```

Tạo tệp **`FileContentEquality.java`**, biên dịch:

```bash
javac FileContentEquality.java
```

Đây là class tiện ích **biên dịch được nhưng không có `main`**, vì vậy không chạy trực tiếp bằng `java FileContentEquality`. Nó dùng được khi tích hợp vào chương trình khác:

```java
if (FileContentEquality.sameBytes(source, copy)) {
    System.out.println("Nội dung byte giống nhau");
} else {
    System.out.println("Nội dung byte khác nhau");
}
```

Đây là đoạn minh họa: `source` và `copy` phải được khai báo với kiểu `Path`; phương thức gọi xử lý `IOException`.

**Không bắt `IOException` rồi trả `false` vô điều kiện** nếu ý nghĩa `false` là “đã kiểm tra và chắc chắn khác nhau”. Có ít nhất ba trạng thái cần phân biệt:

- Đã kiểm tra, byte **bằng nhau**.
- Đã kiểm tra, byte **khác nhau**.
- **Không thể kiểm tra** vì lỗi I/O.

Có thể dùng `boolean` + `throws IOException` như trên, hoặc một kiểu kết quả giàu thông tin hơn nếu ứng dụng cần phân biệt lỗi, thời gian kiểm tra và vị trí mismatch.

## 9. Ví dụ công cụ dòng lệnh kiểm tra hai tệp

Tệp **`CompareFilesCli.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CompareFilesCli {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println(
                    "Usage: java CompareFilesCli <first> <second>");
            return;
        }

        Path first = Path.of(args[0]);
        Path second = Path.of(args[1]);

        try {
            long offset = Files.mismatch(first, second);

            if (offset == -1L) {
                System.out.println("Hai tệp giống hệt nhau theo byte.");
            } else {
                System.out.println("Byte khác đầu tiên tại offset " + offset);
                System.out.println("Kích thước A = " + Files.size(first));
                System.out.println("Kích thước B = " + Files.size(second));
            }
        } catch (IOException ex) {
            System.err.println("Không thể so sánh: " + ex.getMessage());
        }
    }
}
```

Biên dịch và gọi với **hai đường dẫn thực tế**:

```bash
javac -encoding UTF-8 CompareFilesCli.java
java CompareFilesCli first.bin second.bin
```

Không có đầu ra cố định: kết quả phụ thuộc hai tệp được truyền vào và trạng thái filesystem. Nếu một tệp không tồn tại hoặc không đọc được, `Files.mismatch` có thể ném `IOException` (ví dụ `NoSuchFileException`), và chương trình báo không thể so sánh thay vì khẳng định hai tệp khác nhau.

Với công cụ CLI dùng trong automation, cần quyết định **mã thoát (exit code)** cho ba trường hợp bằng nhau, khác nhau và lỗi; ví dụ trên mới in thông báo để minh họa API, không định nghĩa mã thoát đặc biệt.

## 10. Khi nào chọn `Files.mismatch`, `Arrays.equals`, so sánh chuỗi hoặc hash?

### So sánh trực tiếp hai file theo byte

`Files.mismatch(a, b)` là lựa chọn rõ ràng khi muốn vị trí byte đầu tiên khác nhau. Nó không đòi hỏi ứng dụng tự đọc cả hai tệp vào hai mảng byte.

### `Files.readAllBytes` + `Arrays.equals`

```java
boolean equal = Arrays.equals(
        Files.readAllBytes(first),
        Files.readAllBytes(second));
```

Đây là **đoạn minh họa**, với `Path first` và `second` đã khai báo, cần import `java.util.Arrays`, `java.nio.file.Files` và xử lý `IOException`. Nó dễ hiểu và hữu ích cho tệp **nhỏ**, nhưng tạo mảng byte chứa toàn bộ nội dung cả hai tệp trong RAM; với tệp lớn có nguy cơ tiêu tốn bộ nhớ rất cao. `Arrays.equals` chỉ cho kết quả bằng/khác, **không trả offset đầu tiên**.

Nếu hai mảng byte đã tồn tại trong RAM từ bước trước, `Arrays.equals` có thể là cách trực tiếp hơn là phải tạo tệp tạm để dùng `mismatch`.

### `Files.readString` + `String.equals`

```java
boolean sameText = Files.readString(first, StandardCharsets.UTF_8)
        .equals(Files.readString(second, StandardCharsets.UTF_8));
```

Đây là **đoạn minh họa**, cần các import và xử lý `IOException`. Cách này so văn bản sau khi **giải mã UTF-8**, không tự chuẩn hóa Unicode, newline hoặc bỏ khoảng trắng. Nó cũng cần bộ nhớ để giữ cả hai chuỗi.

Vì được giải mã sang ký tự, bài toán này khác so sánh byte: có thể có những mã byte khác nhau nhưng cùng biểu diễn văn bản sau các bước xử lý nhất định. Nếu tệp có byte không hợp lệ với charset, việc đọc văn bản có thể không thành công.

### Hash/checksum

Với việc xác minh nhiều file, hệ thống phân tán hay kiểm tra dữ liệu lớn lặp lại, có thể sử dụng hash như **SHA-256** để tạo dấu vân tay nội dung. Tuy nhiên:

- Cùng hash tạo ra từ thuật toán xác định trên cùng byte; hash khác nhau chứng tỏ byte khác nhau.
- Hai hash bằng nhau mang ý nghĩa **xác suất va chạm cực nhỏ** với thuật toán mạnh, nhưng không phải một chứng minh toán học tuyệt đối rằng mọi byte giống nhau.
- Hash không trực tiếp cho biết **offset mismatch**.
- Hash đã lưu từ trước chỉ đáng tin khi nguồn hash và quy trình tính toán được tin cậy; nếu cần chứng minh nguồn gốc/chống giả mạo, còn phải xét chữ ký số hoặc mã xác thực thông điệp thích hợp.

Khi chỉ cần đối chiếu chính xác **hai tệp hiện có tại chỗ**, `Files.mismatch` thường tránh được việc tự thiết kế vòng lặp hash hoặc tải hết nội dung vào RAM.

## 11. Hiệu năng: khác ở đầu và giống hệt là hai tình huống khác nhau

Việc phát hiện một mismatch ở đầu tệp có thể kết thúc sớm hơn so với phải xác minh hàng gigabyte nội dung giống nhau. Tuy vậy, tốc độ phụ thuộc nhiều vào:

- Kích thước và vị trí byte khác đầu tiên.
- Loại lưu trữ: SSD, HDD, network filesystem, cloud mount...
- Bộ đệm hệ điều hành/JVM và việc dữ liệu đã được cache hay chưa.
- Chi phí mở file, phân quyền, thao tác metadata.
- Các thay đổi đồng thời lên file trong lúc đọc.

**Không suy đoán** thời gian chạy từ số ký tự trong tên file hoặc kích thước metadata. Nếu hiệu năng quan trọng, benchmark với kích thước và môi trường thật.

Một cách tối ưu hợp lý khi chỉ hỏi boolean:

```java
static boolean equalWithSizeHint(Path a, Path b)
        throws IOException {
    if (Files.size(a) != Files.size(b)) {
        return false;
    }
    return Files.mismatch(a, b) == -1L;
}
```

Đây là **phương thức minh họa**, cần import `Path`, `Files` và `IOException`, đặt trong class. Việc kiểm tra size chỉ là **bộ lọc sớm**, không thay thế kiểm tra nội dung khi size bằng nhau. Với tệp bị sửa đồng thời giữa hai lời gọi, kết quả size và mismatch có thể phản ánh hai thời điểm khác nhau; vì vậy đây không phải cơ chế chứng nhận snapshot bất biến.

Nếu cần báo **offset đầu tiên**, cứ gọi `Files.mismatch` và dùng trực tiếp kết quả thay vì chỉ lấy size.

## 12. Ngoại lệ và tài nguyên: phân biệt “khác nhau” với “không đọc được”

`Files.mismatch` khai báo `throws IOException`; lỗi có thể xảy ra vì:

- Một đường dẫn không tồn tại hoặc bị đổi tên trong lúc thao tác.
- Không đủ quyền đọc một nguồn.
- Thiết bị hoặc filesystem mạng lỗi.
- Dữ liệu nguồn bị thay đổi trong quá trình so sánh, khiến kết quả không còn đại diện cho một snapshot nhất quán.
- Nguồn không có hành vi như một tệp thông thường theo quy định của provider.

**Không dùng `-1L` hay `0L` làm giá trị “lỗi” trong phần catch.** Hai giá trị này đã mang ý nghĩa thành công hợp lệ của thuật toán so sánh.

Mẫu xử lý:

```java
try {
    long mismatch = Files.mismatch(left, right);
    if (mismatch == -1L) {
        // Thành công: cùng byte
    } else {
        // Thành công: khác byte tại mismatch
    }
} catch (IOException ex) {
    // Không thể hoàn tất phép so sánh; giữ nguyên lỗi để xử lý
}
```

Đây là **đoạn minh họa**, `left`/`right` phải là `Path`, cần import `Files` và `IOException`.

`Files.mismatch` tự tổ chức việc đọc theo API; bạn **không cần** và cũng **không nên** mở một `InputStream` cho từng tệp chỉ để truyền vào `mismatch`, vì phương thức nhận `Path`. Nếu cần đọc byte xung quanh offset khác đầu tiên, đó là tác vụ riêng có thể dùng `SeekableByteChannel` hoặc `FileChannel`, cần nhớ đóng tài nguyên do bạn tự mở bằng `try-with-resources`.

## 13. Các giới hạn quan trọng của phép so sánh

### Thay đổi đồng thời và tính nhất quán

Hai tệp có thể bị tiến trình khác sửa trong lúc `mismatch` đang chạy. Phương thức này **không phải transaction** và không bảo đảm hai tệp được so từ hai snapshot bất biến tại cùng một thời điểm.

Nếu cần xác minh backup hoặc artifact đáng tin cậy:

1. Đảm bảo mỗi tệp đã được ghi hoàn chỉnh và writer đã đóng.
2. Dùng snapshot hoặc cơ chế khóa/phối hợp ghi khi hệ thống hỗ trợ và yêu cầu tính nhất quán.
3. So sánh trên nguồn ổn định; nếu đầu vào còn đang ghi, dời bước verify sang sau điểm commit.
4. Với dữ liệu cần chứng minh lâu dài, lưu checksum/hash và metadata theo quy trình phù hợp.

### Symbolic link và danh tính của tệp

`Path` biểu diễn đường dẫn; hai đường dẫn có thể cùng trỏ đến một tệp thông qua hard link, symbolic link hoặc quy tắc hệ thống tệp. Nếu cần hỏi **“có phải cùng thực thể file không?”**, xem `Files.isSameFile`. Nếu cần hỏi **“byte có giống nhau không?”**, xem `Files.mismatch`.

Đừng nhầm bằng nhau về nội dung với bằng nhau về danh tính. Hai bản sao ở hai thư mục khác nhau có thể byte hoàn toàn giống nhau nhưng vẫn là hai tệp độc lập.

### Không kiểm tra tính hợp lệ của định dạng

Hai file ZIP, PDF, JSON hoặc ảnh có thể có cùng byte nhưng cùng bị hỏng. `Files.mismatch` không xác nhận tệp mở được bằng ứng dụng tương ứng. Ngược lại, hai ảnh hiển thị giống nhau nhưng encode khác vẫn bị đánh giá là khác byte.

**So sánh byte**, **so sánh cấu trúc** và **so sánh ý nghĩa** là ba yêu cầu khác nhau; cần chọn công cụ theo mức bảo đảm mong muốn.

## 14. Tình huống thực tế: xác minh bản sao sau khi ghi

Giả sử ứng dụng vừa sao chép một tệp và muốn xác nhận nội dung tại thời điểm kiểm tra:

```java
Path source = Path.of("source.dat");
Path backup = Path.of("backup.dat");

Files.copy(source, backup,
        StandardCopyOption.REPLACE_EXISTING);

long mismatch = Files.mismatch(source, backup);
if (mismatch != -1L) {
    throw new IOException("Bản sao khác từ offset " + mismatch);
}
```

Đây là **đoạn minh họa**, cần import `Path`, `Files`, `StandardCopyOption` và `IOException`, và chỉ nên chạy với đường dẫn/việc ghi đè đã được người dùng cho phép. `Files.copy` có thể thay thế tệp đích và bản thân thao tác sao chép không được mặc định là atomic; nếu có lỗi giữa quá trình, file đích có thể chưa hoàn chỉnh. Sau khi copy thành công, `mismatch` kiểm tra lại byte của hai tệp.

**Quan trọng:** Kết quả `-1` không tự bảo đảm bản backup đã được **fsync** xuống ổ lưu trữ, sẽ tồn tại sau mất điện hay không thể bị thay đổi ngay sau lần kiểm tra. Độ bền dữ liệu và chống sửa đổi cần biện pháp bổ sung ở tầng filesystem và quy trình vận hành.

Với quy trình backup nghiêm ngặt, có thể tạo tệp tạm, ghi xong, xác thực nội dung rồi mới công bố tên backup chính thức.

## 15. Lỗi phổ biến

| Sai lầm | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| Nghĩ `mismatch == 0` nghĩa là bằng nhau | Offset 0 là byte khác đầu tiên | Chỉ `mismatch == -1L` nghĩa là bằng nhau |
| Coi offset là dòng hoặc ký tự | API trả offset **byte** | Chỉ tính dòng/ký tự sau khi giải mã và ánh xạ đúng |
| Hai file cùng size nên chắc giống nhau | Cùng size vẫn có thể khác byte | Dùng `Files.mismatch` hoặc so nội dung |
| Lấy size ngắn làm mismatch trong mọi trường hợp khác size | Hai file có thể khác byte trước lúc file ngắn kết thúc | Dùng kết quả thực tế từ `mismatch` |
| So `Path.equals` để xác minh bản sao | So cách biểu diễn đường dẫn, không so byte | Dùng `Files.mismatch` |
| Dùng `Files.isSameFile` thay phép so nội dung | Kiểm tra danh tính tệp | So byte nếu cần chứng minh nội dung |
| Bắt `IOException` rồi trả `false` | Không đọc được bị báo thành chắc chắn khác nhau | Để lỗi lan truyền hoặc trả trạng thái lỗi rõ ràng |
| Đọc hết file khổng lồ bằng `readAllBytes` | Tốn RAM không cần thiết | `Files.mismatch` khi so tệp với tệp |
| Mong `mismatch` bỏ qua BOM/newline/khoảng trắng | Không có chuẩn hóa văn bản | Chọn giải pháp so sánh văn bản riêng |
| Dùng hash làm offset mismatch | Hash chỉ là dấu vân tay nội dung | `Files.mismatch` để lấy offset |
| So file còn đang bị ghi rồi coi kết quả vĩnh viễn đúng | Nguồn thay đổi giữa và sau phép đọc | Kiểm tra trên snapshot hoặc nguồn ổn định |
| Coi `mismatch == -1` là file đã được lưu an toàn vĩnh viễn | Byte equality không đồng nghĩa durability | Quản lý flush, fsync/force, lưu trữ và commit |

## 16. Checklist và bài tự luyện

**Trước khi so sánh:**

- [ ] Chọn rõ: cần cùng **byte**, cùng **văn bản chuẩn hóa**, cùng **nội dung logic** hay cùng **thực thể tệp**?
- [ ] Xác minh hai đường dẫn đúng đối tượng và có quyền đọc.
- [ ] Đảm bảo nguồn đã ghi hoàn tất và ổn định nếu cần kết luận đáng tin.
- [ ] Nếu chỉ cần boolean, nhớ giá trị duy nhất biểu diễn bằng nhau là `-1L`.
- [ ] Nếu muốn biết vị trí đầu tiên khác nhau, dùng offset `long` trả về, bắt đầu từ 0.
- [ ] Xử lý `IOException` riêng, không biến lỗi thành kết quả khác byte.
- [ ] Với tệp lớn, tránh đọc tất cả vào RAM nếu không cần.
- [ ] Với văn bản, ghi rõ charset và quy tắc chuẩn hóa nếu bài toán không phải so byte thuần.

**Bài 1 — offset:** Tệp A chứa `00 11 22 33 44`, tệp B chứa `00 11 FF 33 44`. Dự đoán kết quả `Files.mismatch` rồi tự tạo hai tệp bằng `Files.write` để kiểm chứng. Đáp án: offset **`2`**.

**Bài 2 — tiền tố:** Tệp A chứa `41 42` (hai byte ASCII “AB”), tệp B chứa `41 42 43` (“ABC”). Tại sao mismatch là `2` chứ không phải `-1`? Vì tại offset 2, tệp A đã kết thúc nhưng B vẫn còn một byte.

**Bài 3 — cùng độ dài:** Tệp A chứa `01 02 03`, tệp B chứa `01 FF 03`. Cả hai đều dài 3 byte; hãy chứng minh kích thước không đủ để nhận định bình đẳng nội dung. `mismatch` phải trả **`1`**.

**Bài 4 — Unicode:** Tạo hai tệp UTF-8 chứa cùng từ nhìn như “café” theo dạng NFC và NFD. So sánh bằng `Files.mismatch` rồi đọc bằng `Files.readString`, chuẩn hóa với `Normalizer.Form.NFC` và dùng `equals`. Giải thích vì sao hai phép so sánh có thể đưa ra câu trả lời khác nhau.

**Ghi nhớ:** **`Files.mismatch(a, b)`** (Java 12+) trả **`-1L` khi hai tệp giống hệt theo byte**, hoặc **offset `long` bắt đầu từ 0** của byte khác đầu tiên; nếu một tệp là tiền tố của tệp kia thì offset là kích thước tệp ngắn. Phương thức không so metadata hay nội dung ngữ nghĩa, không tự bảo đảm snapshot/độ bền và có thể ném `IOException`.

</details>

- [Quay lại đầu trang](#back-to-top)
