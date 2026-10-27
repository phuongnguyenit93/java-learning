<a id="back-to-top"></a>

# I/O trong bộ nhớ

## Menu
- [ByteArrayInputStream và ByteArrayOutputStream](#in-memory-io-001)
- [toByteArray tạo bản sao và writeTo chuyển dữ liệu](#in-memory-io-002)
- [StringReader, StringWriter và các luồng mảng char](#in-memory-io-003)
- [Chuyển byte&#91;&#93; sang String bằng charset xác định](#in-memory-io-004)
- [SequenceInputStream nối nhiều nguồn byte](#in-memory-io-005)
- [PushbackInputStream và PushbackReader](#in-memory-io-006)
- [PipedInputStream, PipedOutputStream và giao tiếp giữa luồng](#in-memory-io-007)
- [PipedReader, PipedWriter và nguy cơ bế tắc khi dùng không đúng](#in-memory-io-008)
- [Dùng stream bộ nhớ trong kiểm thử và các phép biến đổi](#in-memory-io-009)
- [Giới hạn dung lượng khi tích lũy toàn bộ nội dung](#in-memory-io-010)

## <a id="in-memory-io-001">ByteArrayInputStream và ByteArrayOutputStream</a>

<details>
<summary>Xem chi tiết</summary>

# ByteArrayInputStream và ByteArrayOutputStream: I/O byte trong bộ nhớ Java

## 1. Vì sao cần luồng byte trong bộ nhớ?

Trong Java, `InputStream` mô tả một nguồn byte mà chương trình có thể đọc, còn `OutputStream` mô tả một đích nhận byte. Nguồn/đích không nhất thiết là tệp hay kết nối mạng: đôi khi tất cả dữ liệu đều nằm trong RAM. Hai lớp thuộc `java.io` giải quyết trường hợp đó:

- **`ByteArrayInputStream`** cho phép **đọc** tuần tự từ một mảng `byte[]` đã có.
- **`ByteArrayOutputStream`** cho phép **ghi** từng byte hoặc từng khối byte vào một vùng nhớ tự tăng, rồi lấy kết quả thành `byte[]`.

Nhờ triển khai cùng các API `InputStream` và `OutputStream`, chúng có thể kết nối với những phương thức xử lý luồng thông thường mà không cần tệp thật. Đây là công cụ thuận tiện để kiểm thử, biến đổi nội dung nhỏ trong bộ nhớ, lắp ghép payload và dùng các API chấp nhận một luồng.

~~~text
byte[] có sẵn                        byte[] kết quả
      │                                    ▲
      ▼                                    │ toByteArray()
ByteArrayInputStream ─ read() ─► Xử lý ─ write() ─► ByteArrayOutputStream
      ▲                                    │
      │ đọc từ vùng nhớ                   │ ghi vào vùng nhớ tự mở rộng
      └───────────── không bắt buộc có ổ đĩa hay socket ────────────┘
~~~

Điểm phân biệt quan trọng: tên `Stream` ở đây chỉ **giao diện luồng byte**, không có nghĩa JVM nhất thiết mở file descriptor hoặc socket. Hai lớp này hoạt động trên bộ nhớ Java. Tuy vậy, `byte[]` có giới hạn kích thước và tiêu thụ heap; việc lưu cả tệp lớn vào RAM có thể gây áp lực bộ nhớ.

Bài này giải thích cấu tạo, hợp đồng phương thức, khác biệt về sao chép/chia sẻ mảng, vòng đời, chuyển đổi văn bản theo charset, lỗi thường gặp và các chương trình Java 17+ có thể biên dịch độc lập.

## 2. Hai lớp có vai trò đối xứng nhưng cách lưu dữ liệu khác nhau

| Đặc điểm | `ByteArrayInputStream` | `ByteArrayOutputStream` |
| --- | --- | --- |
| Hướng dữ liệu | Đọc từ mảng byte đầu vào | Ghi vào bộ đệm byte đang tăng |
| Dữ liệu lúc tạo | Cần một `byte[]` nguồn | Có thể tạo rỗng, tùy chọn dung lượng ban đầu |
| Kích thước hữu ích | Phần đọc được xác định khi khởi tạo | Tăng theo lượng byte đã ghi |
| Con trỏ | Có vị trí đọc hiện tại | Có số byte đã ghi (`size()`) |
| API chính | `read()`, `read(byte[],off,len)`, `skip()`, `available()`, `mark()`, `reset()` | `write(int)`, `write(byte[],off,len)`, `writeBytes()`, `writeTo()`, `toByteArray()`, `size()`, `reset()` |
| Có mở tệp/socket? | Không | Không |
| Đóng có làm mất khả năng dùng? | `close()` là thao tác không có hiệu lực theo hợp đồng lớp | `close()` là thao tác không có hiệu lực theo hợp đồng lớp |
| Sao chép dữ liệu lúc tạo/lấy ra | Constructor lưu tham chiếu tới mảng nguồn, không tự sao chép nó | `toByteArray()` tạo **bản sao** chỉ gồm những byte đã ghi |

**Đừng đồng nhất hai hướng:**

- Với đầu vào, ta đã có mảng và chỉ cần đi qua nó lần lượt. Khi đọc, vị trí tăng; kích thước nguồn không tự tăng.
- Với đầu ra, ta chưa cần biết chính xác dung lượng cuối. Mỗi `write` có thể khiến bộ đệm nội bộ mở rộng. `size()` là số byte hợp lệ, không nhất thiết là dung lượng mảng nội bộ.

Trong các ví dụ dưới, tên lớp được viết đầy đủ hoặc import từ `java.io`. Tất cả đều là API nền tảng, không cần thư viện ngoài.

## 3. ByteArrayInputStream: đọc từ byte[] như một InputStream

### 3.1. Hai constructor quan trọng

~~~java
byte[] data = {10, 20, 30, 40};

// Đọc toàn bộ mảng:
ByteArrayInputStream all = new ByteArrayInputStream(data);

// Chỉ đọc một vùng liên tiếp, bắt đầu ở offset 1, tối đa length 2:
ByteArrayInputStream slice = new ByteArrayInputStream(data, 1, 2);
~~~

Đoạn trên minh họa API; khi dùng trong một lớp riêng cần `import java.io.ByteArrayInputStream`. Với `slice`, dữ liệu đọc được là byte `20` và `30`. Tham số thứ ba là **độ dài** (`length`), không phải chỉ số kết thúc. Hãy cấp `offset` và `length` hợp lệ, không dựa vào hành vi với chỉ số âm hoặc vùng ngoài mảng.

Về mặt tư duy, một `ByteArrayInputStream` giữ:

- **`buf`:** tham chiếu đến mảng nguồn.
- **`pos`:** vị trí byte sẽ được đọc tiếp.
- **`count`:** giới hạn kết thúc phần có thể đọc.
- **`mark`:** vị trí ghi dấu để có thể quay lại.

Các trường này được lớp quản lý; ứng dụng thông thường tương tác qua API công khai.

Đặc biệt, constructor **không sao chép byte[]**. Nếu bên ngoài sửa mảng trong khi Scanner hoặc phương thức đọc khác sử dụng `ByteArrayInputStream`, các byte đọc được sau đó có thể bị ảnh hưởng. Muốn đầu vào bất biến theo thời gian, hãy tự tạo bản sao, chẳng hạn `source.clone()`, trước khi truyền vào constructor.

### 3.2. read() và dấu hiệu kết thúc

`int value = in.read()` có hai dạng kết quả:

- `0` đến `255`: giá trị **unsigned** của byte vừa đọc, biểu diễn dưới dạng `int`.
- `-1`: đã đi hết phần dữ liệu của luồng.

Điểm này khác với việc lấy trực tiếp một phần tử `byte` trong Java: `byte` là kiểu có dấu, phạm vi `-128..127`. Byte thô `0xFF` khi đặt trong `byte` có giá trị `-1`, nhưng `InputStream.read()` trả **`255`** cho byte này. Cần dùng `int` để không nhầm dữ liệu `0xFF` với EOF.

Ví dụ độc lập, lưu thành **ByteArrayReadDemo.java**:

~~~java
import java.io.ByteArrayInputStream;

public class ByteArrayReadDemo {
    public static void main(String[] args) throws Exception {
        byte[] bytes = {0, 127, (byte) 0x80, (byte) 0xFF};

        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            int value;
            while ((value = input.read()) != -1) {
                System.out.println(value);
            }
            System.out.println("EOF: " + input.read());
        }
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac -encoding UTF-8 ByteArrayReadDemo.java
java ByteArrayReadDemo
~~~

Kết quả chính xác:

~~~text
0
127
128
255
EOF: -1
~~~

Nếu cần chuyển `byte` có dấu sang dạng unsigned `int` trong các thuật toán riêng, dùng `Byte.toUnsignedInt(value)` hoặc `value & 0xFF`. Không cần chuyển kiểu này với kết quả từ `InputStream.read()` vì API đã trả về giá trị trong phạm vi `0..255`.

### 3.3. read(byte[] dst, int off, int len)

Một lần đọc có thể đưa byte vào đoạn của mảng đích:

~~~java
byte[] dst = new byte[8];
int count = input.read(dst, 2, 3);
~~~

Đây là đoạn minh họa: `input` đã được tạo. Nó yêu cầu tối đa ba byte từ nguồn, đặt vào `dst[2]`, `dst[3]`, `dst[4]`. **`count` là số byte thực nhận** (hoặc `-1` nếu EOF khi yêu cầu đọc dữ liệu). Những ô ngoài đoạn được đọc không mang dữ liệu mới của lượt đó.

Với một `ByteArrayInputStream` có dữ liệu sẵn, mỗi lượt đọc thường lấy ngay tối đa lượng byte hiện có. Nhưng khi viết hàm tổng quát nhận `InputStream`, đừng giả định `read(buffer)` luôn lấp đầy mảng: với tệp và mạng, có thể đọc được ít hơn yêu cầu.

Một vòng lặp sao chép đúng:

~~~java
byte[] buffer = new byte[4096];
int count;
while ((count = input.read(buffer)) != -1) {
    output.write(buffer, 0, count);
}
~~~

Đây là đoạn minh họa với `InputStream input`, `OutputStream output` có sẵn, và phương thức bao ngoài xử lý `IOException`. Dùng `count` thay vì `buffer.length` để tránh ghi lại dữ liệu cũ từ phần cuối mảng khi lượt đọc cuối không đầy.

### 3.4. available() và skip()

Đối với `ByteArrayInputStream`, `available()` trả về **số byte còn lại trong phần mảng mà luồng này có thể đọc** mà không bị chặn. Đây là một đặc điểm dễ dự đoán vì toàn bộ dữ liệu vốn đã ở RAM.

Ví dụ một mảng 5 byte, đã đọc hai byte thì `available()` bằng 3. Sau `skip(2)`, còn lại 1. `skip(long n)` chỉ tiến qua nhiều nhất số byte còn lại; không tạo dữ liệu, không đọc ngược khi `n` không dương.

Tuy nhiên, **không khái quát cách hiểu `available()` thành kích thước toàn bộ nội dung của mọi `InputStream`**. Với mạng, file stream hoặc wrapper khác, hợp đồng `available()` thường chỉ nói đến số byte ước tính có thể đọc ngay mà không chặn; giá trị 0 không đồng nghĩa với EOF.

### 3.5. mark(), reset() và khả năng đọc lại

`ByteArrayInputStream` hỗ trợ đánh dấu vị trí và quay lại:

- `markSupported()` trả `true`.
- `mark(int readlimit)` ghi nhớ vị trí đọc hiện tại. Với lớp này, `readlimit` không hạn chế khả năng đánh dấu.
- `reset()` đưa vị trí đọc về mốc đã đánh dấu.
- Nếu không tự đánh dấu, mốc ban đầu tương ứng vị trí lúc tạo luồng (0 đối với constructor toàn mảng, `offset` đối với constructor đọc vùng).

Ví dụ độc lập, **ByteArrayMarkDemo.java**:

~~~java
import java.io.ByteArrayInputStream;

public class ByteArrayMarkDemo {
    public static void main(String[] args) throws Exception {
        byte[] source = {10, 20, 30, 40};

        try (ByteArrayInputStream input = new ByteArrayInputStream(source)) {
            System.out.println("First: " + input.read());
            input.mark(100);
            System.out.println("Second: " + input.read());
            System.out.println("Skipped: " + input.skip(1));
            System.out.println("Fourth: " + input.read());

            input.reset();
            System.out.println("Again: " + input.read());
            System.out.println("Available: " + input.available());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ByteArrayMarkDemo.java
java ByteArrayMarkDemo
~~~

Kết quả:

~~~text
First: 10
Second: 20
Skipped: 1
Fourth: 40
Again: 20
Available: 2
~~~

Dòng `Again: 20` chứng minh `reset()` quay về `mark` sau byte 10; nó không tự quay về đầu mảng khi đã có dấu mới. Với một `InputStream` khác, `markSupported()` có thể trả false hoặc readlimit có ý nghĩa thực sự; đừng áp dụng bảo đảm của `ByteArrayInputStream` lên mọi loại nguồn.

## 4. ByteArrayOutputStream: xây dựng byte[] bằng nhiều lần ghi

### 4.1. Khởi tạo, dung lượng ban đầu và tự mở rộng

`new ByteArrayOutputStream()` bắt đầu với một bộ đệm nội bộ có dung lượng ban đầu mặc định. `new ByteArrayOutputStream(initialSize)` cho phép cung cấp dung lượng ban đầu không âm, hữu ích khi có ước lượng kích thước kết quả.

~~~java
ByteArrayOutputStream output = new ByteArrayOutputStream();
ByteArrayOutputStream estimated = new ByteArrayOutputStream(1024);
~~~

Đây là đoạn minh họa yêu cầu import `java.io.ByteArrayOutputStream`. **Dung lượng ban đầu không phải kích thước nội dung**: cả hai đối tượng mới tạo đều có `size() == 0`. Khi ghi vượt dung lượng, lớp tự mở rộng mảng nội bộ.

Không nên cấp dung lượng quá lớn “cho chắc” vì nó chiếm heap từ đầu. Ngược lại, nếu biết kết quả khoảng vài kilobyte, dung lượng ban đầu hợp lý có thể giảm số lần cấp phát và sao chép nội bộ.

### 4.2. write(int b): chỉ byte thấp được ghi

`output.write(int b)` ghi **8 bit thấp nhất** của `b`. Nó không ghi toàn bộ số nguyên 32-bit theo định dạng nhị phân. Ví dụ `output.write(0x1234)` ghi byte `0x34`, tức giá trị thập phân 52, là ký tự ASCII `'4'`.

Nếu muốn ghi một `int` 32-bit theo thứ tự byte nhất định, phải tự xác định định dạng hoặc dùng lớp như `DataOutputStream` bọc `ByteArrayOutputStream`. Với dữ liệu mạng/file có cấu trúc, định dạng cần xác định **endianness**, độ rộng trường và quy tắc diễn giải.

### 4.3. write(byte[], off, len) và writeBytes(byte[])

- `write(byte[] bytes, int off, int len)` ghi đúng `len` byte bắt đầu ở vị trí `off` của mảng nguồn.
- `writeBytes(byte[] bytes)` (có từ Java 11) ghi toàn bộ byte trong mảng.
- `write(int b)` ghi một byte thấp nhất của số `int`.

Nếu một phần mảng được dùng làm buffer đọc tệp, chỉ ghi `count` byte vừa đọc. Nếu mảng đã đại diện chính xác cho một payload hoàn chỉnh, `writeBytes(payload)` giúp ngắn gọn.

### 4.4. size(), toByteArray(), reset(), writeTo()

`size()` trả số byte đã ghi và còn hữu ích trong bộ đệm. Nó **không** cho biết dung lượng mảng nội bộ đã cấp phát.

`toByteArray()` tạo một **mảng mới** chứa bản sao của đúng `size()` byte hiện tại. Nếu sau đó tiếp tục ghi hoặc `reset()`, bản sao đã trả về không đổi. Việc tạo bản sao cũng có chi phí cấp phát và sao chép, đặc biệt đáng chú ý với payload lớn.

`reset()` đặt số byte hợp lệ về 0 để có thể dùng lại `ByteArrayOutputStream`. Nó **không bắt buộc giải phóng hoặc xóa mảng nội bộ**; bộ đệm có thể được tái sử dụng để giảm cấp phát. Điều này khác với việc tạo đối tượng mới và không nên dùng `reset()` như biện pháp xóa dữ liệu nhạy cảm khỏi RAM.

`writeTo(OutputStream target)` ghi nội dung hợp lệ của bộ đệm hiện tại sang một output stream khác, giúp tránh tạo bản sao `byte[]` bằng `toByteArray()` trước khi ghi. Nó không tự đóng output đích. Trong trường hợp phải chuyển một payload có sẵn vào luồng tệp/socket, cách này thường hữu ích.

### 4.5. Ví dụ độc lập về snapshot và reset

Lưu **ByteArrayWriteDemo.java**:

~~~java
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class ByteArrayWriteDemo {
    public static void main(String[] args) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(2)) {
            output.write(0x41); // 'A'
            output.write(new byte[]{0x42, 0x43, 0x44}, 1, 2); // 'C', 'D'

            byte[] snapshot = output.toByteArray();
            System.out.println("Size: " + output.size());
            System.out.println("Snapshot: " +
                    new String(snapshot, StandardCharsets.US_ASCII));

            output.reset();
            output.write("Z".getBytes(StandardCharsets.US_ASCII));

            System.out.println("After reset: " +
                    output.toString(StandardCharsets.US_ASCII));
            System.out.println("Old snapshot: " +
                    new String(snapshot, StandardCharsets.US_ASCII));
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ByteArrayWriteDemo.java
java ByteArrayWriteDemo
~~~

Kết quả chính xác:

~~~text
Size: 3
Snapshot: ACD
After reset: Z
Old snapshot: ACD
~~~

Ví dụ minh họa ba cơ chế: bộ đệm tự tăng dù khởi tạo kích thước 2, `toByteArray()` trả snapshot độc lập, và `reset()` bắt đầu một nội dung mới mà không thay đổi snapshot cũ.

## 5. Bài thực hành tổng hợp: sao chép văn bản UTF-8 qua hai luồng trong RAM

Một tình huống quen thuộc: chương trình có văn bản Java, mã hóa thành UTF-8, sau đó muốn sử dụng một hàm nhận `InputStream` và ghi sang `OutputStream`. Hai luồng mảng là cách ghép nhanh mà không phải tạo tệp tạm.

Lưu **MemoryStreamsDemo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MemoryStreamsDemo {
    public static void main(String[] args) throws Exception {
        String original = "Xin chào!";
        byte[] encoded = original.getBytes(StandardCharsets.UTF_8);

        byte[] copied;
        try (ByteArrayInputStream input = new ByteArrayInputStream(encoded);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            byte[] chunk = new byte[3];
            int count;
            while ((count = input.read(chunk)) != -1) {
                output.write(chunk, 0, count);
            }

            copied = output.toByteArray();
        }

        System.out.println("Bytes: " + copied.length);
        System.out.println("Same bytes: " + Arrays.equals(encoded, copied));
        System.out.println("Decoded: " +
                new String(copied, StandardCharsets.UTF_8));
    }
}
~~~

~~~bash
javac -encoding UTF-8 MemoryStreamsDemo.java
java MemoryStreamsDemo
~~~

Kết quả:

~~~text
Bytes: 10
Same bytes: true
Decoded: Xin chào!
~~~

Tại sao `10` byte? `"Xin chào!"` có 9 đơn vị `char`, nhưng ký tự `à` chiếm 2 byte trong UTF-8, nên tổng byte là 10. Vòng lặp sao chép **byte thô**; nó không cố giải mã một khối 3 byte thành String riêng lẻ. Đây là điều đúng vì chuỗi byte UTF-8 của một ký tự có thể bị chia giữa hai lần đọc. Chỉ sau khi có payload đầy đủ, ví dụ mới tạo String với `StandardCharsets.UTF_8`.

Nếu ngay từ đầu chỉ cần chuyển String thành byte[] thì `original.getBytes(UTF_8)` đã đủ; không nhất thiết phải tạo luồng. Điểm dạy học ở đây là cách các API cùng hợp đồng `InputStream` và `OutputStream` có thể dùng bộ nhớ làm nguồn/đích trong một pipeline mà mã xử lý không cần biết đó là tệp, socket hay mảng.

## 6. Những khác biệt về sao chép và chia sẻ mảng

### 6.1. ByteArrayInputStream dùng chung mảng nguồn

~~~java
byte[] source = {65, 66};
ByteArrayInputStream input = new ByteArrayInputStream(source);
source[0] = 90;
int first = input.read(); // 90, tương ứng 'Z'
~~~

Đây là đoạn minh họa; cần `import java.io.ByteArrayInputStream`. Vì constructor không sao chép mảng, cập nhật từ bên ngoài có thể nhìn thấy khi đọc. Nếu muốn stream có dữ liệu riêng:

~~~java
ByteArrayInputStream isolated =
        new ByteArrayInputStream(source.clone());
~~~

Với dữ liệu không thay đổi và được quản lý bởi một thành phần, việc dùng chung mảng tránh sao chép không cần thiết. Với mã có nhiều thành phần hoặc cần bảo đảm dữ liệu đầu vào không thể bị sửa ngoài ý muốn, clone đầu vào là một lựa chọn rõ ràng.

### 6.2. ByteArrayOutputStream tạo bản sao đầu ra

`output.toByteArray()` trả một mảng mới. Sửa mảng nhận được **không** sửa nội dung đang nằm trong `ByteArrayOutputStream`:

~~~java
ByteArrayOutputStream output = new ByteArrayOutputStream();
output.writeBytes(new byte[]{1, 2, 3});
byte[] copy = output.toByteArray();
copy[0] = 99;
// output vẫn giữ 1, 2, 3.
~~~

Đây là đoạn minh họa; cần import `java.io.ByteArrayOutputStream`. Mảng bản sao cũng không “theo dõi” các lần ghi sau này. Nếu muốn chia sẻ kết quả sang nơi khác, hãy xác định rõ nơi nhận được phép sửa mảng hay cần clone thêm.

### 6.3. Sao chép kép và sử dụng bộ nhớ

Mẫu:

~~~java
byte[] data = output.toByteArray();
InputStream input = new ByteArrayInputStream(data);
~~~

có nghĩa `toByteArray()` đã cấp phát bản sao; `ByteArrayInputStream` chỉ giữ tham chiếu tới bản sao đó. Tại thời điểm này có thể cùng tồn tại **bộ đệm nội bộ của ByteArrayOutputStream và mảng data**. Với dữ liệu lớn, chi phí bộ nhớ có thể đáng kể.

Nếu đích là một `OutputStream` khác, `output.writeTo(destination)` có thể tránh mảng trung gian. Nếu hàm nhận bắt buộc là `InputStream`, có thể chấp nhận snapshot đó với dữ liệu nhỏ/vừa, hoặc thay thiết kế để xử lý theo luồng từ nguồn gốc nhằm tránh giữ cả payload trong RAM.

## 7. Read/write theo từng khối: chú ý count và phần đuôi mảng

Dù `ByteArrayInputStream` đọc từ RAM và thường có thể lấp đầy buffer cho đến khi gần EOF, **mã tổng quát phải dựa trên số byte thực sự đọc**.

Ví dụ nguồn 5 byte, buffer 2 byte:

~~~text
Nguồn:                 A B C D E
Lượt đọc 1: count=2 → A B
Lượt đọc 2: count=2 → C D
Lượt đọc 3: count=1 → E   (phần còn lại của mảng vẫn có thể là D)
Lượt đọc 4: count=-1 → EOF
~~~

Nếu viết `output.write(buffer)` ở lượt 3, ta có thể chèn thêm byte cũ không thuộc dữ liệu mới. Với `ByteArrayOutputStream`, lỗi sẽ hiện rất rõ: `size()` tăng thêm byte và `toByteArray()` chứa dữ liệu dư. Dùng `output.write(buffer, 0, count)`.

Một demo độc lập, lưu **ByteChunkDemo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class ByteChunkDemo {
    public static void main(String[] args) throws Exception {
        byte[] data = "ABCDE".getBytes(StandardCharsets.US_ASCII);

        try (ByteArrayInputStream input = new ByteArrayInputStream(data);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2];
            int count;
            while ((count = input.read(buffer)) != -1) {
                System.out.println("Read: " + count);
                output.write(buffer, 0, count);
            }
            System.out.println("Copied: " +
                    output.toString(StandardCharsets.US_ASCII));
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ByteChunkDemo.java
java ByteChunkDemo
~~~

Kết quả:

~~~text
Read: 2
Read: 2
Read: 1
Copied: ABCDE
~~~

Nhờ nguồn trong RAM, kích thước và số lần đọc ở ví dụ này có thể dự đoán; với nguồn tổng quát như network stream, lượt đọc có thể ngắn hơn buffer vì lý do khác ngoài EOF.

## 8. Chuyển đổi giữa byte[] và String đúng charset

`ByteArrayInputStream` và `ByteArrayOutputStream` **không tự hiểu dữ liệu byte là văn bản**. Nếu byte biểu diễn chuỗi, việc mã hóa và giải mã phải dùng charset khớp với định dạng.

Với Java 17:

~~~java
String text = "Hà Nội";
byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);

ByteArrayInputStream input = new ByteArrayInputStream(utf8);

ByteArrayOutputStream output = new ByteArrayOutputStream();
output.writeBytes(utf8);
String decoded = output.toString(StandardCharsets.UTF_8);
~~~

Đây là đoạn minh họa cần import `java.nio.charset.StandardCharsets` và hai lớp stream. `decoded` bằng `"Hà Nội"`. Lớp `ByteArrayOutputStream` còn có `toString()` không truyền charset, nhưng phương thức đó dùng charset mặc định theo hợp đồng, có thể không phải charset của dữ liệu khi chạy trên môi trường khác; nên ưu tiên `toString(StandardCharsets.UTF_8)` hoặc `new String(output.toByteArray(), UTF_8)` khi cần tính nhất quán.

**Không giải mã từng mảnh UTF-8 độc lập** nếu một ký tự có thể bị chia giữa các mảng; ví dụ dấu tiếng Việt và emoji cần nhiều byte. Hãy gom đủ payload rồi giải mã, hoặc dùng một `InputStreamReader`/`CharsetDecoder` có trạng thái xuyên suốt các khối.

Một số byte như ảnh JPEG, ZIP, khóa nhị phân hoặc thông điệp định dạng riêng **không phải văn bản UTF-8**. Tạo String từ chúng có thể thay đổi dữ liệu vì chuỗi byte không hợp lệ bị thay thế. Giữ chúng dưới dạng `byte[]`, dùng `InputStream`/`OutputStream` và tránh ép qua String.

## 9. Cách tương tác với các API InputStream và OutputStream khác

Giá trị của hai lớp không chỉ nằm ở những phương thức riêng; chúng triển khai các hợp đồng phổ biến của Java I/O.

### 9.1. Dùng một hàm xử lý bất kỳ InputStream nào

Ví dụ một hàm tổng quát:

~~~java
static long copy(InputStream input, OutputStream output)
        throws IOException {
    byte[] buffer = new byte[8192];
    long total = 0;
    int count;
    while ((count = input.read(buffer)) != -1) {
        output.write(buffer, 0, count);
        total += count;
    }
    return total;
}
~~~

Đây là **đoạn mã phương thức**, không phải một chương trình độc lập. Cần import `java.io.InputStream`, `OutputStream` và `IOException` và đặt trong lớp. Hàm **không tự đóng** hai tài nguyên vì chúng được người gọi truyền vào; quyền sở hữu thuộc giao ước bên ngoài.

Trong kiểm thử, có thể dùng `new ByteArrayInputStream(fixture)` làm đầu vào và `new ByteArrayOutputStream()` làm đầu ra để kiểm tra chính xác byte mà không phải tạo tệp. Khi chạy thật, cùng hàm có thể nhận `Files.newInputStream(path)` và `Files.newOutputStream(path)`.

### 9.2. transferTo thay vòng lặp thủ công

Từ Java 9, `InputStream.transferTo(OutputStream)` đọc hết đầu vào còn lại và ghi sang đầu ra, trả về số byte đã chuyển. Với Java 17:

~~~java
long copied = input.transferTo(output);
~~~

Đây là đoạn minh họa với hai stream đã được cung cấp. Phương thức **không tự đóng** các stream. Nếu quyền đóng do người gọi quản lý, cần giữ `try-with-resources` hoặc quy tắc vòng đời tương đương tại nơi sở hữu.

Việc dùng `transferTo` có thể gọn hơn một vòng lặp nếu chỉ cần chuyển toàn bộ byte. Nó không thay thế kiểm tra giới hạn kích thước, xử lý lỗi hay quyết định có cần gom mọi byte vào RAM hay không.

### 9.3. DataOutputStream và DataInputStream

`ByteArrayOutputStream` có thể làm đích của `DataOutputStream` khi cần ghi số theo định dạng nhị phân xác định, còn `ByteArrayInputStream` có thể làm nguồn của `DataInputStream` khi đọc lại. Ví dụ Java 17 độc lập, **BinaryPayloadDemo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class BinaryPayloadDemo {
    public static void main(String[] args) throws Exception {
        byte[] payload;

        try (ByteArrayOutputStream raw = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(258);
            out.writeBoolean(true);
            out.flush();
            payload = raw.toByteArray();
        }

        try (DataInputStream in =
                     new DataInputStream(new ByteArrayInputStream(payload))) {
            System.out.println("Length: " + payload.length);
            System.out.println("Number: " + in.readInt());
            System.out.println("Flag: " + in.readBoolean());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 BinaryPayloadDemo.java
java BinaryPayloadDemo
~~~

Kết quả:

~~~text
Length: 5
Number: 258
Flag: true
~~~

`DataOutputStream.writeInt` ghi bốn byte theo thứ tự **byte cao trước (big-endian)**, còn `writeBoolean(true)` ghi một byte 1. `DataInputStream` đọc đúng định dạng đó. Đây khác với `ByteArrayOutputStream.write(258)`, vốn chỉ ghi **một byte** có giá trị 2.

Dùng cặp DataInput/DataOutput chỉ khi hai đầu thống nhất định dạng Java này hoặc có hợp đồng tương thích; không tự suy ra đó là định dạng của mọi dịch vụ mạng.

## 10. Vòng đời tài nguyên và tác động của close()

Cả `ByteArrayInputStream` và `ByteArrayOutputStream` đều triển khai các giao diện I/O tương ứng và có thể được đóng bằng `try-with-resources`. Với **chính hai lớp này**, `close()` **không có tác động** đến khả năng sử dụng: sau khi gọi close, `ByteArrayInputStream` vẫn có thể đọc, còn `ByteArrayOutputStream` vẫn có thể ghi và gọi `toByteArray()`.

Điều này phù hợp vì chúng không giữ file descriptor/socket cần giải phóng. Tuy nhiên, **không suy rộng** cho `FileInputStream`, `FileOutputStream`, `BufferedInputStream`, socket stream hoặc các wrapper I/O khác: với nhiều loại stream, đóng là bắt buộc và dùng lại sau khi đóng sẽ lỗi.

Vẫn có ích khi dùng `try-with-resources` cho các ví dụ tổng quát vì:

- Cách viết nhất quán khi sau này thay mảng bằng nguồn tệp hoặc socket.
- Phạm vi sống của đối tượng được thể hiện rõ.
- Các wrapper có thể giữ tài nguyên khác, dù nguồn gốc ban đầu là bộ nhớ.

**Chú ý khi bọc:** ví dụ `DataOutputStream` bọc `ByteArrayOutputStream`. Đóng `DataOutputStream` cũng đóng stream bên dưới; tuy ByteArrayOutputStream vẫn truy cập được sau khi đóng, không nên áp dụng hành vi đó cho các OutputStream tổng quát khác.

### 10.1. Buffering có thật sự cần cho mảng byte?

`BufferedInputStream` và `BufferedOutputStream` thường giúp giảm số lần thao tác I/O tốn chi phí với tệp/socket. Với byte array stream, dữ liệu đã nằm trong RAM nên việc thêm wrapper buffering thường không mang lợi ích đáng kể cho việc giảm gọi hệ điều hành, thậm chí có thể thêm sao chép/bộ đệm không cần thiết.

Nếu API đang yêu cầu wrapper vì định dạng hoặc cơ chế tiện ích, vẫn có thể dùng; nhưng không nên mặc định rằng bọc thêm `BufferedOutputStream` luôn làm nhanh hơn `ByteArrayOutputStream`.

## 11. Sự khác biệt giữa reset() của InputStream và OutputStream

Cả hai lớp đều có `reset()` nhưng ý nghĩa **khác nhau**:

- `ByteArrayInputStream.reset()`: đưa **vị trí đọc** về `mark`, cho phép đọc lại những byte của nguồn. Nó không xóa hay thay dữ liệu nguồn.
- `ByteArrayOutputStream.reset()`: đặt **số byte đã ghi hợp lệ** về 0 để bắt đầu kết quả mới. Nó không làm thay đổi những bản sao đã trả qua `toByteArray()`.

Dễ nhầm nếu cùng một biến tên `stream`. Nên đặt tên mang ý nghĩa `input`/`output` và diễn giải chính xác hành động “đọc lại” với “xóa nội dung logic để ghi lại”.

Đối với `ByteArrayOutputStream`, `reset()` không bảo đảm xóa các byte cũ khỏi vùng nhớ nội bộ; nếu xử lý dữ liệu nhạy cảm, không coi reset là thao tác bảo mật xóa bộ nhớ. Đối với `ByteArrayInputStream`, `reset()` không tự đưa về đầu tuyệt đối nếu đã gọi `mark()` ở vị trí khác.

## 12. Khi nào dùng byte[] trực tiếp và khi nào cần stream?

Không phải thao tác nào với RAM cũng cần hai lớp này:

| Mục đích | Lựa chọn thường hợp lý |
| --- | --- |
| Đã có byte[] và muốn gọi API nhận InputStream | `new ByteArrayInputStream(data)` |
| Muốn ghi dần nhiều khối byte mà chưa biết kích thước cuối | `ByteArrayOutputStream` |
| Chỉ muốn lấy byte của một String | `text.getBytes(UTF_8)` |
| Chỉ muốn đổi byte[] hoàn chỉnh thành String | `new String(bytes, UTF_8)` |
| Muốn ghép vài mảng cố định nhỏ | Có thể tính tổng chiều dài và dùng `System.arraycopy` hoặc `ByteBuffer` |
| Cần giữ payload nhỏ để kiểm thử hàm streaming | Cặp `ByteArrayInputStream` / `ByteArrayOutputStream` |
| Cần truyền dữ liệu liên tục từ tệp lớn sang đích khác | `InputStream`/`OutputStream` theo khối, tránh giữ toàn bộ trong RAM |
| Cần tuần tự hóa object Java theo định dạng riêng | Xem API tuần tự hóa thích hợp; không tự suy ra BAOS đã định dạng object |

`ByteArrayOutputStream` rất thuận tiện khi kích thước đầu ra khó đoán nhưng vẫn **có giới hạn thực tế trong RAM**. Với tệp hàng gigabyte, việc gom mọi byte vào BAOS thường không phải giải pháp tốt: bộ đệm mở rộng chiếm heap, còn `toByteArray()` có thể cần thêm một mảng lớn thứ hai.

Khi ứng dụng đã có nguồn/đích streaming, hãy cân nhắc xử lý dần thay vì lắp ghép toàn bộ nội dung rồi mới ghi.

## 13. Quản lý bộ nhớ, dung lượng và các rủi ro

### 13.1. Bộ đệm tăng có thể gây OutOfMemoryError

`ByteArrayOutputStream` dùng một mảng nội bộ để chứa dữ liệu và có thể phải cấp phát mảng lớn hơn khi hết chỗ. Khi dữ liệu không giới hạn hoặc bị điều khiển bởi đầu vào bên ngoài, bộ đệm có thể làm cạn heap hoặc chạm giới hạn kích thước mảng của JVM.

Một số biện pháp:

1. Xác định **kích thước payload tối đa** theo nghiệp vụ trước khi bắt đầu gom.
2. Nếu có header khai báo độ dài, kiểm tra giá trị đó nhưng vẫn theo dõi lượng byte thực tế; không tin mù quáng vào kích thước do bên ngoài cung cấp.
3. Nếu nội dung lớn, xử lý theo luồng đến nơi tiêu thụ hoặc dùng file tạm khi thiết kế cho phép.
4. Không gọi `toByteArray()` liên tục bên trong vòng lặp ghi vì mỗi lần đều có thể tạo một bản sao lớn.
5. Không tạo `new ByteArrayOutputStream(Integer.MAX_VALUE)` trừ khi thực sự có môi trường và mục đích hợp lý; cấp phát dung lượng ban đầu lớn có thể thất bại ngay.

Một thiết kế dễ kiểm soát có thể theo dõi tổng byte với `long total` trước mỗi lần ghi và từ chối khi vượt ngưỡng. Nhưng bản thân `ByteArrayOutputStream` vẫn dùng mảng Java có giới hạn chỉ số kiểu `int`, nên ứng dụng không thể chứa lượng byte tùy ý chỉ bằng một instance.

### 13.2. Không nhầm size() với capacity

`size()` báo **số byte đã ghi**. Trong nội bộ, BAOS có thể giữ vùng chứa lớn hơn số đó để chừa chỗ ghi tiếp. Không nên suy đoán mức heap chiếm dụng chỉ từ `size()`, và cũng không cần truy cập trường nội bộ để đọc một “capacity” không nằm trong API công khai.

Sau `reset()`, `size()` trở về 0 nhưng buffer có thể vẫn chiếm dung lượng cũ. Nếu kết thúc công việc với mảng lớn, hãy bỏ tham chiếu để đối tượng có thể được GC thu gom khi thích hợp; reset đơn thuần không phải yêu cầu trả bộ nhớ lập tức cho JVM.

### 13.3. Chia sẻ và an toàn khi sửa dữ liệu

Vì `ByteArrayInputStream` dùng chung mảng nguồn, không nên đồng thời sửa mảng từ một thread khác trong lúc đọc mà không có cơ chế đồng bộ phù hợp. Với `ByteArrayOutputStream`, dù một số phương thức được đồng bộ trong triển khai, thiết kế giao thức nhiều bước giữa các thread vẫn cần đảm bảo quyền sở hữu/đồng bộ rõ ràng. Đừng dựa vào việc “dữ liệu ở RAM” để suy ra mọi kiểu truy cập đồng thời đều an toàn.

## 14. Những lỗi phổ biến và cách khắc phục

| Lỗi | Vì sao sai | Cách đúng |
| --- | --- | --- |
| Kiểm tra `byteValue == -1` như dấu hiệu EOF | Byte dữ liệu `0xFF` có giá trị signed -1 | Đọc qua `int value = input.read()` và kiểm tra `value == -1` |
| `output.write(buffer)` sau mỗi lượt đọc | Lượt cuối có thể chưa đầy buffer, ghi thêm byte cũ | `output.write(buffer, 0, count)` |
| `new String(bytes)` không charset | Kết quả có thể khác trên môi trường khác | Chọn `StandardCharsets.UTF_8` hoặc charset theo định dạng |
| Tạo String từ dữ liệu nhị phân bất kỳ | Giải mã làm biến đổi/mất byte | Giữ dữ liệu nhị phân ở dạng byte[] |
| Tưởng BAIS đã sao chép dữ liệu nguồn | Bên ngoài sửa mảng khiến nội dung đọc thay đổi | Dùng `source.clone()` nếu cần bản sao độc lập |
| Tưởng `toByteArray()` trả mảng nội bộ | Sửa kết quả không làm đổi BAOS và có thêm chi phí bộ nhớ | Xem đó là bản sao, dùng `writeTo` khi có thể |
| Gọi `reset()` để xóa dữ liệu nhạy cảm | BAOS chỉ đặt lại `count`, không bảo đảm xóa bộ nhớ | Thiết kế chính sách lưu giữ/xóa phù hợp |
| Dùng `available()` để tính kích thước mọi InputStream | Với nguồn sống, 0 không phải EOF | Chỉ áp dụng giá trị xác định với BAIS; tổng quát thì đọc đến -1 |
| Nghĩ `write(0x1234)` ghi 4 byte của int | Nó chỉ ghi 8 bit thấp nhất | Dùng `DataOutputStream.writeInt` theo định dạng cần thiết |
| Tích lũy tệp rất lớn trong BAOS | Bộ đệm và snapshot dễ làm cạn heap | Sao chép theo luồng, giới hạn payload |
| Giả định đóng stream mảng giống đóng file | `close()` của hai lớp là no-op | Hiểu hợp đồng lớp cụ thể; luôn đóng tài nguyên thật |
| Dùng `ByteArrayInputStream.reset()` để xóa nội dung | Nó chỉ quay vị trí đọc về mốc | Tạo dữ liệu khác hoặc quản lý mảng nguồn riêng |
| Đọc UTF-8 theo chunk rồi tạo `String` độc lập mỗi lượt | Có thể cắt giữa byte của ký tự nhiều byte | Giữ byte và decode một lần hoặc dùng decoder có trạng thái |

## 15. Thực hành mở rộng và câu hỏi tự kiểm tra

**Câu 1.** Một `ByteArrayInputStream` đọc mảng `{(byte)0xFF}`; lời gọi `read()` đầu tiên và thứ hai trả gì?

**Đáp án:** `255` rồi `-1`. API `read()` trả int unsigned cho byte hợp lệ, và chỉ dùng -1 cho EOF.

**Câu 2.** `ByteArrayInputStream` có sao chép mảng truyền vào constructor không?

**Đáp án:** không. Nó lưu tham chiếu; cần `clone()` hoặc `Arrays.copyOf` nếu muốn tách dữ liệu.

**Câu 3.** Sau khi gọi `byte[] snapshot = output.toByteArray()` rồi `output.reset()` và ghi byte mới, `snapshot` có đổi không?

**Đáp án:** không. Snapshot là bản sao của dữ liệu tại thời điểm gọi.

**Câu 4.** Hai lớp này có nên dùng `try-with-resources` không, dù close không có hiệu lực?

**Đáp án:** có thể, đặc biệt để giữ cấu trúc sở hữu rõ ràng và nhất quán với API stream. Nhưng đừng coi việc đóng BAIS/BAOS có tác dụng giải phóng file/socket như lớp tương ứng của tệp.

**Câu 5.** Muốn gửi nội dung BAOS sang một `OutputStream` đã tồn tại mà không tạo mảng trung gian qua `toByteArray()`, dùng gì?

**Đáp án:** `output.writeTo(destination)`. Đích vẫn do người sở hữu đóng; phương thức không tự đóng đích.

**Câu 6.** `ByteArrayOutputStream.write(0x1234)` làm size tăng bao nhiêu, và byte được ghi là gì?

**Đáp án:** tăng 1; byte `0x34`. Muốn ghi int 32-bit, chọn định dạng rõ ràng và API thích hợp như `DataOutputStream.writeInt`.

**Câu 7.** Trong vòng lặp đọc có buffer dài 8, lần cuối `read` trả 3. Ghi mấy byte?

**Đáp án:** đúng 3 byte với `write(buffer, 0, 3)`.

**Câu 8.** Dùng `new String(baos.toByteArray(), UTF_8)` có phù hợp với nội dung là ảnh PNG không?

**Đáp án:** không. Đây là dữ liệu nhị phân; chuyển thành String có thể làm hỏng byte nếu sau đó mã hóa lại. Giữ dưới dạng byte[].

### Bài tập thực hành

1. Đổi `ByteChunkDemo` thành bộ đệm dài 3 và nguồn `"ABCDEFGH"`; dự đoán các giá trị `count` và xác nhận số byte trong output.
2. Tạo một `ByteArrayInputStream` từ vùng `offset=2, length=3` của mảng sáu byte. Đọc đến EOF, gọi `reset()` và kiểm tra nội dung có thể đọc lại từ offset ban đầu.
3. Mở rộng `ByteArrayWriteDemo` bằng `writeTo` tới một BAOS thứ hai. So sánh kết quả với `toByteArray()` và thay đổi snapshot để quan sát tính độc lập.
4. Tạo chuỗi có tiếng Việt và emoji, mã hóa UTF-8, sao chép theo buffer 1 byte, rồi **chỉ giải mã khi toàn bộ payload đã được ghép lại**. Xác nhận `String.equals`, `Arrays.equals` và phân biệt số byte với độ dài UTF-16.
5. Thêm kiểm tra giới hạn dung lượng trước khi ghi vào BAOS; thử với dữ liệu vượt giới hạn để bảo đảm ứng dụng từ chối có kiểm soát.

## 16. Những điểm cần nhớ

- **`ByteArrayInputStream` đọc từ mảng byte có sẵn**; đọc tuần tự, hỗ trợ `mark/reset`, `skip` và `available`. Mảng nguồn được **dùng chung**, không mặc định copy.
- **`ByteArrayOutputStream` tích lũy dữ liệu byte trong mảng tự tăng**, cung cấp `size`, `reset`, `writeTo` và `toByteArray`. `toByteArray` trả **bản sao**.
- Luôn phân biệt **giá trị byte có dấu** với **kết quả `int` của `InputStream.read()`**, trong đó `-1` là EOF.
- Khi sao chép từng khối, **ghi đúng `count` byte mới đọc**. Khi xử lý văn bản, dùng charset rõ ràng và không giải mã từng mảnh UTF-8 tách rời.
- Hai lớp là **I/O trên heap**, không giữ descriptor/socket; `close()` không cấm sử dụng tiếp với chính hai lớp này, nhưng không thể suy ra điều đó cho mọi stream.
- Dữ liệu trong RAM vẫn có chi phí. **Giới hạn kích thước đầu ra**, tránh sao chép không cần thiết và chọn streaming khi payload lớn.

### Tài liệu tham khảo

- [ByteArrayInputStream — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ByteArrayInputStream.html)
- [ByteArrayOutputStream — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ByteArrayOutputStream.html)
- [InputStream — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html)
- [OutputStream — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html)
- [DataOutputStream — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutputStream.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="in-memory-io-002">toByteArray tạo bản sao và writeTo chuyển dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# ByteArrayOutputStream.toByteArray() tạo bản sao và writeTo() chuyển dữ liệu

Trong Java I/O, **ByteArrayOutputStream** là một **OutputStream ghi vào vùng nhớ**: thay vì gửi ngay các byte đến ổ đĩa hoặc socket, nó tích lũy dữ liệu trong một buffer nội bộ có thể mở rộng. Hai phương thức thường dùng để lấy dữ liệu ra là:

- **byte[] toByteArray()**: trả về **một mảng byte mới** chứa bản sao của toàn bộ dữ liệu đã ghi tính đến thời điểm gọi.
- **void writeTo(OutputStream out)**: **chuyển các byte đang tích lũy sang một OutputStream khác**, không yêu cầu người gọi tạo mảng trung gian bằng toByteArray().

Hiểu sự khác biệt này giúp chọn đúng cách xuất dữ liệu, tránh sửa nhầm buffer, kiểm soát bộ nhớ và làm rõ ai sở hữu luồng đích.

## 1. ByteArrayOutputStream nằm ở đâu trong Java I/O?

**java.io.ByteArrayOutputStream** kế thừa **OutputStream**, nhưng ghi byte vào RAM. Có thể coi đối tượng có trạng thái logic như sau:

~~~text
ByteArrayOutputStream
┌──────────────────────────────────────┐
│ buffer nội bộ: [41][00][FF][42][..] │
│ số byte hợp lệ (count): 4           │
│ phần dung lượng còn trống: không phải dữ liệu
└──────────────────────────────────────┘
                 │
           ┌─────┴────────────┐
           ▼                  ▼
     toByteArray()       writeTo(out)
           │                  │
   mảng byte mới         out.write(...)
   [41 00 FF 42]         trên 4 byte hợp lệ
~~~

Ứng dụng ghi dữ liệu vào đối tượng bằng **write(int)** hoặc **write(byte[], off, len)**. Buffer nội bộ sẽ lớn lên khi cần; **size()** cho biết số byte đang hợp lệ, không phải dung lượng vật lý đã cấp phát. Việc này phù hợp khi muốn xây dựng một payload nhỏ trong bộ nhớ rồi lấy bytes để gửi đi, ví dụ tạo một frame nhị phân hoặc thông điệp trước khi xuất.

Ví dụ khởi tạo:

~~~java
ByteArrayOutputStream data = new ByteArrayOutputStream();
data.write(0x41);
data.write(new byte[] {0x00, (byte) 0xFF, 0x42});
System.out.println(data.size()); // 4
~~~

Đây là **mảnh code minh họa** (cần import **java.io.ByteArrayOutputStream** và đặt trong phương thức có quyền xử lý IOException nếu khai báo qua kiểu OutputStream). Kết quả **size() = 4** dù buffer có thể được cấp phát nhiều hơn 4 byte. Byte **0x00** và **0xFF** đều hợp lệ vì đây là I/O nhị phân, không có bước decode charset.

**ByteArrayOutputStream** không tự mở file, socket hay thiết bị nào. Muốn gửi dữ liệu ra ngoài, ứng dụng chủ động lấy byte bằng **toByteArray()** hoặc dùng **writeTo(out)**.

## 2. toByteArray(): tạo một bản sao độc lập

Hợp đồng của **toByteArray()** là trả về một **byte[] mới** chứa các byte đã ghi, theo đúng thứ tự, với độ dài bằng **size()** tại thời điểm lấy snapshot.

~~~java
byte[] snapshot = buffer.toByteArray();
~~~

Ở đây **buffer** là một ByteArrayOutputStream đã tạo. Mảng **snapshot** là **bản sao**, không phải một view của buffer nội bộ. Bởi vậy:

1. **Sửa phần tử của snapshot không làm thay đổi dữ liệu trong ByteArrayOutputStream.**
2. **Ghi thêm dữ liệu vào ByteArrayOutputStream không tự thay đổi nội dung hoặc độ dài của snapshot cũ.**
3. **reset()** ByteArrayOutputStream không làm rỗng các snapshot đã lấy.
4. **Gọi toByteArray() nhiều lần tạo các mảng mới**, dù dữ liệu có thể giống nhau; người gọi chịu trách nhiệm về chi phí cấp phát và vòng đời của mảng.

Ví dụ:

~~~text
Trước khi gọi:   ByteArrayOutputStream = [41 00 FF]
toByteArray():   snapshot          = [41 00 FF]

snapshot[0] = 5A:
                 snapshot          = [5A 00 FF]
                 ByteArrayOutputStream vẫn [41 00 FF]

Ghi thêm 42 vào stream:
                 ByteArrayOutputStream = [41 00 FF 42]
                 snapshot vẫn          = [5A 00 FF]
~~~

**Tại sao Java cố tình tạo bản sao?** Vì nếu lộ trực tiếp mảng nội bộ, code bên ngoài có thể thay dữ liệu trong luồng mà không qua **write(...)**, vi phạm đóng gói và dễ gây lỗi. Bản sao còn cho phép lưu lại trạng thái byte tại một thời điểm để xử lý sau khi ByteArrayOutputStream được ghi tiếp hoặc reset.

### 2.1. ByteArrayOutputStream và byte[] là hai trạng thái độc lập

Một lưu ý về **tính độc lập**: giá trị **snapshot** không phải “live view” cập nhật tự động. Nếu người dùng cần byte mới sau khi ghi tiếp, phải gọi **toByteArray()** lần khác.

~~~java
byte[] first = buffer.toByteArray();
buffer.write(0x7F);
byte[] second = buffer.toByteArray();
~~~

Đây là đoạn minh họa. **first** giữ nguyên mảng cũ, còn **second** có thêm byte mới. Hai mảng do hai lần gọi được tạo độc lập; chúng không chia sẻ phần tử theo kiểu cùng một mảng.

### 2.2. Chi phí RAM của toByteArray()

Nếu buffer đang chứa **N byte**, gọi **toByteArray()** phải cấp phát thêm một **byte[N]** và sao chép N byte từ vùng đệm. Trong lúc cả buffer nội bộ và snapshot còn được giữ, chương trình có thể phải giữ **cả hai vùng nhớ**; nếu gọi nhiều lần và giữ nhiều snapshot, RAM tăng thêm tương ứng.

Ví dụ lưu 50 MiB trong ByteArrayOutputStream, rồi gọi **toByteArray()**: ngoài bộ nhớ đã giữ trong stream, bản sao thường cần thêm khoảng **50 MiB** (chưa tính overhead mảng, dung lượng dư nội bộ, allocator và các đối tượng khác). Không có nghĩa tổng đúng 100 MiB trong mọi JVM, nhưng cho thấy tại sao cần cẩn thận với dữ liệu lớn.

Chi phí này **đáng chấp nhận khi thực sự cần một byte[] độc lập**, chẳng hạn API nhận byte[], tính hash trên snapshot, gửi qua phương thức chỉ hỗ trợ mảng, hoặc giữ phiên bản không đổi của payload khi tiếp tục xây dựng stream.

## 3. writeTo(OutputStream out): ghi các byte hiện có sang luồng đích

**writeTo(OutputStream out)** chuyển toàn bộ **size() byte đang tích lũy** sang một đối tượng **OutputStream** khác. Theo cách triển khai tiêu chuẩn của ByteArrayOutputStream, nó viết đoạn byte hợp lệ từ buffer nội bộ đến luồng đích; không bắt buộc tạo bản sao byte[] qua **toByteArray()** trước đó.

~~~java
ByteArrayOutputStream buffer = new ByteArrayOutputStream();
buffer.write(new byte[] {1, 2, 3});

OutputStream destination = /* một OutputStream đã mở */;
buffer.writeTo(destination);
~~~

Đây là **mảnh minh họa**, chưa chạy độc lập vì cần tạo một destination thực tế. Phương thức **writeTo** gọi cơ chế ghi của OutputStream đích. OutputStream đích có thể là FileOutputStream, BufferedOutputStream, ByteArrayOutputStream khác, luồng mạng hoặc stream có xử lý bổ sung như nén/mã hóa.

### 3.1. writeTo() không xóa dữ liệu nguồn

**writeTo() không tiêu thụ hoặc reset nội dung của ByteArrayOutputStream.** Sau khi ghi sang destination, **size()** và các byte nguồn vẫn như cũ, nên có thể tiếp tục ghi thêm và gọi **writeTo()** lần khác.

~~~text
Nguồn đã tích lũy:    [41 42 43]  ("ABC")
Đích ban đầu:         [7E]

source.writeTo(dest)

Nguồn sau writeTo:    [41 42 43]  (giữ nguyên)
Đích sau writeTo:     [7E 41 42 43]
~~~

Nếu gọi **source.writeTo(dest)** thêm lần nữa mà không reset nguồn/đích, đích có thể nhận **ABC lần thứ hai**. Đây là cách **ghi thêm**, không phải sao chép kiểu “thay thế toàn bộ nội dung đích”. Chính OutputStream đích và trạng thái của nó quyết định có ghi nối tiếp, ghi đè hay một hình thức xử lý khác.

### 3.2. writeTo() không đồng nghĩa với flush() hoặc close()

**writeTo()** chuyển byte bằng API ghi của đích, **không tự đóng đích** và cũng **không mặc định gọi flush()** trên đích.

Ví dụ nếu **destination** là **BufferedOutputStream**, dữ liệu được chuyển vào buffer của destination nhưng có thể **chưa đến luồng nền** khi writeTo trả về. Nếu cần dữ liệu rời lớp đệm ngay, ứng dụng chủ động gọi **destination.flush()** ở ranh giới thích hợp. Nếu sở hữu destination, đóng bằng **try-with-resources**.

**flush() không đồng nghĩa với dữ liệu đã được ghi bền vững xuống đĩa hoặc đã được bên nhận xử lý.** Đó là các ranh giới xác nhận khác, phụ thuộc loại OutputStream, hệ thống file hoặc giao thức.

### 3.3. Chú ý IOException và tình trạng đích ghi dở

Chữ ký của **writeTo(OutputStream out)** cho phép ném **IOException**, phát sinh từ quá trình ghi xuống đích. Chẳng hạn ổ đĩa đầy, socket ngắt hoặc stream đích lỗi giữa chừng. Khi đó, đích **có thể đã nhận một phần dữ liệu** trước lúc lỗi. Đừng coi writeTo là giao dịch nguyên tử với cơ chế tự rollback.

Nếu **out** bằng null, việc gọi writeTo gây lỗi **NullPointerException** khi cố sử dụng đích. Cần bảo đảm đối tượng đích hợp lệ và xử lý lỗi I/O phù hợp ứng dụng.

## 4. So sánh hai API theo mục đích

| Tiêu chí | **toByteArray()** | **writeTo(OutputStream)** |
| --- | --- | --- |
| Kiểu trả về | **byte[]** | **void** |
| Tác dụng | Tạo snapshot byte độc lập | Đưa byte tích lũy sang OutputStream khác |
| Cần mảng mới kích thước dữ liệu? | **Có** theo hợp đồng | **Không bắt buộc** tạo mảng snapshot ở phía ByteArrayOutputStream |
| Dữ liệu nguồn sau thao tác | Giữ nguyên | Giữ nguyên |
| Đầu ra có phụ thuộc trạng thái đích? | Không có đích | Có: phụ thuộc OutputStream được truyền |
| Có tự flush/close luồng đích? | Không áp dụng | **Không** |
| Lỗi thường cần xét | Cấp phát bộ nhớ, OutOfMemoryError | IOException, ghi dở, ownership đích |
| Nên chọn khi | Cần byte[] để lưu snapshot, API yêu cầu mảng | Muốn chuyển trực tiếp dữ liệu hiện có vào stream đích |

Cả hai đều **không biến byte thành ký tự**. Nếu đang xử lý PNG, ZIP, PDF hoặc payload nhị phân, dữ liệu vẫn là byte. Khi muốn text, nên giải mã bằng charset tường minh ở tầng ứng dụng, tránh dùng **toString()** không chỉ định charset rồi tưởng đây là bản sao byte.

## 5. Ví dụ Java 17+ độc lập: snapshot, writeTo, ghi thêm và reset

Lưu chương trình sau thành **MemoryTransferDemo.java**:

~~~java
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public class MemoryTransferDemo {
    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream source = new ByteArrayOutputStream(2);
        source.write(new byte[] {0x41, 0x00, (byte) 0xFF});

        byte[] snapshot1 = source.toByteArray();
        snapshot1[0] = 0x5A; // Đổi A thành Z trong bản sao.
        System.out.println("snapshot1=" + hex(snapshot1));
        System.out.println("source=" + hex(source.toByteArray()));

        source.write(0x42); // Thêm B vào source.
        byte[] snapshot2 = source.toByteArray();
        System.out.println("snapshot2=" + hex(snapshot2));

        ByteArrayOutputStream target = new ByteArrayOutputStream();
        target.write(0x7E); // Byte có sẵn tại đích.
        source.writeTo(target);
        System.out.println("target=" + hex(target.toByteArray()));

        // Đích đã nhận dữ liệu; việc thêm vào source không cập nhật đích.
        source.write(0x43);
        System.out.println("target after source append=" +
                hex(target.toByteArray()));

        // Reset nguồn không thay đổi snapshot2 đã tạo trước đó.
        source.reset();
        source.write("12".getBytes(StandardCharsets.US_ASCII));
        System.out.println("source after reset=" +
                hex(source.toByteArray()));
        System.out.println("snapshot2 after reset=" + hex(snapshot2));

        // writeTo không tự flush luồng đích có bộ đệm.
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (BufferedOutputStream buffered =
                     new BufferedOutputStream(sink, 8)) {
            source.writeTo(buffered);
            System.out.println("sink before flush=" + sink.size());
            buffered.flush();
            System.out.println("sink after flush=" + sink.size());
        }

        System.out.println("sink data=" + hex(sink.toByteArray()));
        System.out.println("snapshot2 matches original prefix=" +
                Arrays.equals(snapshot2,
                        new byte[] {0x41, 0x00, (byte) 0xFF, 0x42}));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac MemoryTransferDemo.java
java MemoryTransferDemo
~~~

Kết quả chính xác:

~~~text
snapshot1=5a00ff
source=4100ff
snapshot2=4100ff42
target=7e4100ff42
target after source append=7e4100ff42
source after reset=3132
snapshot2 after reset=4100ff42
sink before flush=0
sink after flush=2
sink data=3132
snapshot2 matches original prefix=true
~~~

Giải thích:

1. **snapshot1** ban đầu chứa **41 00 FF**. Sửa phần tử đầu của snapshot thành **5A** không làm nguồn thay đổi: nguồn vẫn **41 00 FF**.
2. Ghi thêm **42** vào nguồn rồi gọi **toByteArray()** tạo **snapshot2 = 41 00 FF 42**.
3. **target** đã có byte **7E**. **source.writeTo(target)** ghi tiếp **41 00 FF 42**, cho kết quả **7E 41 00 FF 42**; nội dung nguồn không bị xóa.
4. Sau đó ghi thêm **43** vào nguồn, target vẫn giữ byte cũ; writeTo không tạo kết nối tự đồng bộ giữa hai stream.
5. **reset()** khiến số byte hợp lệ của nguồn về 0 rồi nhận **31 32** (ASCII “12”). Snapshot2 vẫn giữ **41 00 FF 42**.
6. Với **BufferedOutputStream** dung lượng 8, writeTo của nguồn 2 byte chưa làm sink nhận dữ liệu vì 2 byte đang nằm trong buffer. **flush()** làm sink nhận đủ 2 byte.

Ví dụ sử dụng **ByteArrayOutputStream** làm đích để quan sát trực tiếp byte sau khi ghi. Với file hoặc socket thật, flush và thời điểm dữ liệu được xác nhận còn tùy tầng bên dưới.

## 6. Xuất ByteArrayOutputStream ra file: hai cách thường gặp

Giả sử đã tích lũy payload trong biến **content** kiểu **ByteArrayOutputStream** và có đường dẫn file đích **Path destination**.

### Cách 1: toByteArray() + Files.write

~~~java
byte[] payload = content.toByteArray();
Files.write(destination, payload);
~~~

Đây là **mảnh code**, giả định đã import **java.nio.file.Files**, **java.nio.file.Path** và có các biến nói trên. Phương thức hữu ích khi ứng dụng **cần giữ một mảng độc lập** để tính hash, truyền vào API khác hoặc lưu snapshot. Đổi lại, nó tạo mảng mới có kích thước số byte đang tích lũy.

### Cách 2: writeTo() + Files.newOutputStream

~~~java
try (OutputStream file = Files.newOutputStream(destination)) {
    content.writeTo(file);
}
~~~

Mảnh code này cần import **java.io.OutputStream** và **java.nio.file.Files**, đồng thời sử dụng các biến **content**, **destination**. Khi nghiệp vụ chỉ muốn đưa nội dung ByteArrayOutputStream ra stream khác, **writeTo(file)** trực tiếp thường tránh được bước tạo bản sao **byte[]** như cách 1.

**Lưu ý quan trọng:** **Files.write(path, bytes)** và **Files.newOutputStream(path)** với tùy chọn mặc định đều có thể **tạo file mới hoặc cắt file đích đang tồn tại**. Nếu không muốn ghi đè dữ liệu có sẵn, phải chủ động chọn chính sách mở/tạo file khác, kiểm tra đường dẫn và xử lý lỗi. Việc writeTo không tự quyết định chính sách ghi đè; đó là trách nhiệm của cách tạo OutputStream đích.

Với dữ liệu lớn, vấn đề lớn hơn bản sao toByteArray chính là **ByteArrayOutputStream đã giữ tất cả dữ liệu trong RAM từ đầu**. Nếu có thể đọc trực tiếp từ nguồn và ghi vào đích theo khối, hãy cân nhắc streaming:

~~~java
try (InputStream in = Files.newInputStream(source);
     OutputStream out = Files.newOutputStream(destination)) {
    in.transferTo(out);
}
~~~

Đây là **mảnh code minh họa**, cần import **java.io.InputStream**, **java.io.OutputStream**, **java.nio.file.Files** và biến **Path source/destination** hợp lệ. **InputStream.transferTo(OutputStream)** tránh việc phải dồn toàn bộ nội dung vào một ByteArrayOutputStream; đây là lựa chọn khác khi không cần tích lũy dữ liệu trong bộ nhớ.

## 7. reset(), close() và sự khác nhau giữa kích thước nội dung với dung lượng buffer

**reset()** đặt số byte hợp lệ về **0** để tái sử dụng vùng nhớ đã cấp phát:

~~~java
ByteArrayOutputStream content = new ByteArrayOutputStream();
content.write(new byte[] {10, 20, 30});
byte[] saved = content.toByteArray();

content.reset();
System.out.println(content.size()); // 0
System.out.println(saved.length);    // 3
~~~

Mảnh code này cho thấy **saved** vẫn độc lập với content. Gọi reset **không xóa nội dung trong saved**, đồng thời **không bảo đảm xóa sạch các byte cũ khỏi mảng nội bộ**; nó thường chỉ thay đổi biến đếm hợp lệ, giữ dung lượng buffer để tái sử dụng. Do đó, **reset không phải thao tác xóa dữ liệu bí mật an toàn khỏi RAM**.

**close()** của **ByteArrayOutputStream** theo đặc tả **không có tác dụng**: các phương thức vẫn có thể được gọi sau khi đóng đối tượng này. Đây là ngoại lệ hữu ích của stream thuần bộ nhớ, **không áp dụng tùy tiện cho OutputStream file/socket**. Nếu dùng writeTo để gửi đến file/socket, **phải quản lý vòng đời luồng đích riêng**. Với buffer lớn đã sử dụng xong, bỏ tham chiếu để GC có cơ hội thu hồi bộ nhớ; reset đơn thuần có thể tiếp tục giữ mảng nội bộ lớn.

**size()** là số byte đã tích lũy, không phải capacity. Nếu một ByteArrayOutputStream đã từng giữ 10 MiB rồi reset, **size() == 0** nhưng bộ nhớ backing buffer có thể vẫn được giữ ở mức tương ứng. Nếu mong giải phóng RAM sau đợt dữ liệu lớn, không nên giữ mãi đối tượng cũ.

## 8. Có nên dùng toString() thay toByteArray()?

**ByteArrayOutputStream.toString(Charset)** hoặc tạo **new String(byte[], charset)** biến các byte thành **văn bản theo charset**, đó là thao tác khác. Nếu payload là UTF-8 hợp lệ và mục đích là đọc text, giải mã có charset tường minh như **StandardCharsets.UTF_8** là hợp lý.

Nếu byte đại diện PNG, ZIP, PDF, dữ liệu nén hoặc các frame nhị phân, không chuyển qua String để sao chép. Những byte không hợp lệ theo charset có thể bị thay thế và không khôi phục được bản gốc khi mã hóa lại.

Ví dụ:

~~~java
ByteArrayOutputStream content = new ByteArrayOutputStream();
content.write(new byte[] {0x41, (byte) 0xFF});

byte[] exact = content.toByteArray(); // [41 FF], nguyên byte.
String text = content.toString(StandardCharsets.UTF_8);
// "A�" trong xử lý decode UTF-8 thay thế byte FF không hợp lệ.
~~~

Đây là **mảnh code minh họa**, cần import **java.io.ByteArrayOutputStream** và **java.nio.charset.StandardCharsets**. Đối với luồng nhị phân, **byte[]** và OutputStream là mức trừu tượng phù hợp.

Đừng nhầm **toByteArray()** với **toString()**: một phương thức sao chép byte, phương thức còn lại chuyển nội dung thành biểu diễn ký tự, có nguy cơ mất thông tin khi byte không tạo thành văn bản hợp lệ.

## 9. Bảng quyết định theo tình huống

| Tình huống | Chọn API | Lý do |
| --- | --- | --- |
| Cần nhận payload dưới dạng **byte[]** để gọi thư viện | **toByteArray()** | Có snapshot độc lập theo hợp đồng |
| Muốn sửa thử byte đã nhận mà không sửa dữ liệu đang tích lũy | **toByteArray()** | Thay đổi trên mảng bản sao không ảnh hưởng nguồn |
| Đã có ByteArrayOutputStream và muốn ghi ngay vào **file/socket OutputStream** | **writeTo(out)** | Truyền các byte đã tích lũy đến đích mà không cần mảng snapshot |
| Muốn xuất cùng payload đến hai đích khác nhau | Gọi **writeTo(out1)** và **writeTo(out2)** | Nguồn không bị tiêu thụ sau mỗi lần |
| Muốn bắt đầu tích lũy một payload mới với cùng đối tượng | **reset()**, rồi **write(...)** | Reset số byte hợp lệ, tái sử dụng buffer |
| Muốn đích buffered gửi dữ liệu đang giữ | Gọi **flush()** trên đích | writeTo không tự flush |
| Cần xử lý file hàng GB, không cần toàn bộ trong RAM | **InputStream/OutputStream streaming** | Không cần tích lũy toàn bộ dữ liệu rồi mới xuất |
| Cần sao chép file đơn giản | **Files.copy(...)** | API chuyên dụng cho file, có tùy chọn copy phù hợp |

## 10. Các lỗi phổ biến và cách tránh

1. **Cho rằng toByteArray() trả về mảng nội bộ.** Thực tế là **bản sao mới**; chỉnh sửa mảng không ảnh hưởng source.
2. **Cho rằng writeTo() làm rỗng nguồn.** Nó **không reset**, nên lần writeTo sau có thể ghi lại dữ liệu cũ.
3. **Cho rằng writeTo() cũng tạo snapshot trả về.** Nó trả **void** và ghi trực tiếp bằng hợp đồng của OutputStream đích.
4. **Quên flush đích có buffer.** writeTo có thể trả về trong lúc BufferedOutputStream vẫn giữ byte; flush khi cần gửi ngay.
5. **Quên đóng OutputStream đích.** Close ByteArrayOutputStream không thay thế close file/socket.
6. **Giữ nhiều snapshot lớn từ toByteArray().** Mỗi bản sao cần RAM riêng, tăng sức ép GC.
7. **Gọi reset và tưởng mảng đã cấp phát được giải phóng hoặc bị xóa an toàn.** Reset chỉ làm rỗng trạng thái logic của dữ liệu hợp lệ.
8. **Dùng ByteArrayOutputStream cho dữ liệu cực lớn mà không cần gom vào RAM.** Chọn streaming theo khối để giới hạn bộ nhớ.
9. **Chuyển nhị phân thành String rồi encode lại.** Có thể làm biến đổi byte; giữ dạng byte[] hoặc OutputStream.
10. **Coi writeTo() là ghi giao dịch nguyên tử.** Nếu đích lỗi giữa chừng, đích có thể nhận một phần dữ liệu; cần chiến lược retry hoặc file tạm phù hợp.
11. **Không phân biệt ghi nối tiếp và ghi đè đích.** writeTo ghi theo hành vi hiện tại của OutputStream; cách mở file quyết định truncate/append.
12. **Tính độ dài bằng dung lượng đã cấp phát.** Dùng **size()** hoặc độ dài của mảng toByteArray, không giả định kích thước backing buffer bằng số byte hợp lệ.

## 11. Tự kiểm tra kiến thức

**Câu 1.** Ghi byte [1, 2], lấy **a = toByteArray()**, rồi sửa **a[0] = 9**. Gọi **toByteArray()** lần nữa sẽ trả dữ liệu gì?  
**Đáp:** **[1, 2]**, vì mảng **a** độc lập với buffer trong ByteArrayOutputStream.

**Câu 2.** Gọi **source.writeTo(target)** rồi **source.reset()**. Target có mất dữ liệu đã nhận không?  
**Đáp:** Không. reset chỉ làm rỗng số byte hợp lệ ở **source**; target đã nhận một bản ghi byte theo hợp đồng của nó.

**Câu 3.** Nếu gọi **writeTo(target)** hai lần với source không đổi thì sao?  
**Đáp:** Target có thể nhận nội dung nguồn **hai lần liên tiếp**, trừ khi hành vi riêng của target hoặc cách ứng dụng điều khiển khác đi.

**Câu 4.** writeTo trên **BufferedOutputStream** có tự flush sau mỗi lần không?  
**Đáp:** Không; gọi **flush()** khi cần đẩy các byte đang đệm xuống luồng nền.

**Câu 5.** Vì sao dùng **writeTo(file)** đôi khi tiết kiệm RAM hơn **Files.write(path, source.toByteArray())**?  
**Đáp:** writeTo không yêu cầu tạo mảng bản sao bằng toByteArray. Nhưng bản thân source vẫn đã giữ dữ liệu trong RAM; lợi ích chỉ nằm ở việc tránh một bản sao bổ sung.

**Câu 6.** Nếu **reset()** làm **size() == 0**, có nghĩa bộ nhớ đệm đã bị hủy hoặc mọi byte bí mật đã bị xóa chưa?  
**Đáp:** Không. Dung lượng buffer có thể còn được giữ, và reset không phải thao tác xóa byte bảo mật.

**Câu 7.** Nếu file đích có dữ liệu cũ, **Files.newOutputStream(destination)** không option có giữ dữ liệu cũ không?  
**Đáp:** Thông thường **không**: nó sẽ mở file theo tùy chọn mặc định **CREATE** và **TRUNCATE_EXISTING** cho việc ghi. Muốn hành vi khác, phải dùng option phù hợp có kiểm soát.

## 12. Kết luận

**toByteArray()** cung cấp **snapshot byte[] độc lập**; dùng khi cần mảng để lưu, truyền API hoặc xử lý riêng. **writeTo(OutputStream)** gửi **các byte đang tích lũy** đến một stream đích, thuận tiện khi cần xuất dữ liệu mà không tạo một mảng snapshot phụ. Cả hai đều **giữ nguyên nội dung logic của nguồn**, nhưng **writeTo** có thể thất bại do lỗi đích và không tự flush/close đích.

Với dữ liệu lớn, điều cần xem xét trước tiên là **có thật sự cần giữ toàn bộ nội dung trong ByteArrayOutputStream hay không**. Nếu không, chọn luồng đọc/ghi theo khối. Với dữ liệu vừa và nhỏ cần tích lũy trong bộ nhớ, dùng **toByteArray / writeTo / reset / size** đúng hợp đồng sẽ giúp giữ chính xác byte và tránh nhiều lỗi khó tìm.

### Tham khảo

- [Oracle Java SE 17 — ByteArrayOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ByteArrayOutputStream.html): hợp đồng toByteArray, writeTo, reset, size, close.
- [Oracle Java SE 17 — OutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html): write, flush và close.
- [Oracle Java SE 17 — BufferedOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedOutputStream.html): việc đệm byte và flush.
- [Oracle Java SE 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): Files.write, Files.newOutputStream và streaming qua file.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="in-memory-io-003">StringReader, StringWriter và các luồng mảng char</a>

<details>
<summary>Xem chi tiết</summary>

# `StringReader`, `StringWriter` và các luồng mảng `char` trong Java I/O

## 1. Vì sao cần luồng ký tự trong bộ nhớ?

Không phải tác vụ I/O nào cũng cần mở tệp hoặc kết nối mạng. Đôi khi chương trình đã có dữ liệu dạng `String` hay `char[]` nhưng muốn truyền nó vào một API làm việc với `Reader`; hoặc muốn thu kết quả từ một API nhận `Writer` mà không ghi ra đĩa. Java có bốn lớp tiện dụng trong `java.io`:

| Lớp | Hướng thao tác | Nguồn/đích | Trường hợp phù hợp |
| --- | --- | --- | --- |
| `StringReader` | Đọc | Một `String` đã có | Cho parser/API cần `Reader` đọc từ chuỗi |
| `StringWriter` | Ghi | Bộ đệm ký tự tăng kích thước được | Thu văn bản mà API xuất qua `Writer` |
| `CharArrayReader` | Đọc | Một mảng `char[]`, có thể giới hạn đoạn | Cho API cần `Reader` đọc dữ liệu trong mảng |
| `CharArrayWriter` | Ghi | Mảng `char[]` nội bộ tăng kích thước được | Ghi dữ liệu ký tự và lấy mảng hoặc ghi tiếp sang `Writer` khác |

Tất cả hoạt động ở tầng **ký tự UTF-16 của Java**. Không có bước đọc byte từ đĩa, giải mã charset hay encode sang UTF-8 khi bạn chỉ chuyển giữa `String`, `char[]` và những lớp này.

Ví dụ quan hệ kiểu:

```text
String ──> StringReader ──> Reader ──> parser / BufferedReader
char[] ──> CharArrayReader ──> Reader ──> parser

formatter / parser ──> Writer ──> StringWriter ──> String
formatter / parser ──> Writer ──> CharArrayWriter ──> char[]
```

Dấu mũi tên trong hai dòng ghi thể hiện **dữ liệu đi vào writer rồi được lấy lại**; cấu trúc `Writer` là API để ghi, còn `toString()`/`toCharArray()` lấy kết quả.

## 2. Hiểu đúng `Reader` và `Writer` trước khi ghép lớp

`Reader` và `Writer` là các abstraction cho dữ liệu ký tự:

- `Reader.read()` trả về một `int` chứa **một đơn vị mã UTF-16** từ `0` đến `65535`, hoặc `-1` khi hết dữ liệu. Điều này khác `InputStream.read()`, chỉ trả giá trị byte `0..255` hoặc `-1`.
- `Reader.read(char[])` hay `read(char[], offset, length)` trả về **số lượng đơn vị mã `char`** được đọc, hoặc `-1` nếu EOF với yêu cầu đọc có độ dài dương. Giá trị `0` có thể xuất hiện khi yêu cầu độ dài bằng 0.
- `Writer.write(int)` ghi phần 16 bit thấp của đối số như một `char`; `write(char[])`, `write(String)` và `append(...)` ghi một hay nhiều ký tự.
- `flush()` yêu cầu đẩy dữ liệu đang chờ qua các lớp bọc; `close()` kết thúc vòng đời của một reader/writer theo hợp đồng từng lớp.

**`char` không đồng nghĩa một Unicode code point.** Một ký tự Unicode nằm ngoài vùng BMP (như nhiều emoji) được biểu diễn bằng **hai `char`** (surrogate pair). Vì vậy số lần `Reader.read()` có thể lớn hơn số code point mà người dùng coi là ký tự.

Chọn API theo nguồn dữ liệu: có sẵn `String` thì `StringReader`; có sẵn `char[]` thì `CharArrayReader`. Nếu một thư viện đã nhận trực tiếp `String` và không yêu cầu luồng, tạo `Reader` chỉ để bọc chuỗi thường không mang lại lợi ích.

## 3. `StringReader`: cho một `String` trở thành `Reader`

Tạo bằng `new StringReader(text)`, trong đó `text` là một `String`. `StringReader` đọc tuần tự các đơn vị mã UTF-16, có các thao tác `read()`, `read(char[])`, `skip(...)`, `mark(...)`, `reset()` và `close()`.

Ví dụ độc lập **`StringReaderDemo.java`**:

```java
import java.io.IOException;
import java.io.StringReader;

public class StringReaderDemo {
    public static void main(String[] args) throws IOException {
        try (StringReader reader = new StringReader("AβC")) {
            System.out.println((char) reader.read());

            reader.mark(10);
            System.out.println((char) reader.read());

            reader.reset();
            System.out.println((char) reader.read());
            System.out.println((char) reader.read());
            System.out.println(reader.read());
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 StringReaderDemo.java
java StringReaderDemo
```

Đầu ra:

```text
A
β
β
C
-1
```

Giải thích:

1. Lần đầu đọc `A`, reader chuyển con trỏ đến trước `β`.
2. `mark(10)` đánh dấu vị trí hiện tại (trước `β`). Tham số là `readAheadLimit` theo API, không làm mảng tự tăng kích thước.
3. `read()` lấy `β`; `reset()` quay lại vị trí đánh dấu, nên lần đọc tiếp theo vẫn nhận `β`.
4. Tiếp theo là `C`, rồi `-1` báo hết chuỗi.
5. `StringReader` hỗ trợ `markSupported() == true`. Sau `close()`, các thao tác đọc thường ném `IOException`; không tiếp tục dùng một reader đã đóng.

Nếu cần đọc theo **dòng**, bọc thêm `BufferedReader`:

```java
BufferedReader lines = new BufferedReader(new StringReader(text));
String firstLine = lines.readLine();
```

Đoạn này **chỉ minh họa**, cần có biến `text`, import `java.io.BufferedReader`, xử lý `IOException` và đóng reader khi xong. `readLine()` loại bỏ dấu kết thúc dòng ở kết quả; nó khác `read()` lấy từng đơn vị mã.

## 4. `StringWriter`: thu nội dung được ghi thành `String`

`StringWriter` cung cấp một `Writer` với bộ đệm `StringBuffer` ở bên trong. Những thao tác quan trọng:

| Phương thức | Chức năng |
| --- | --- |
| `write(String)`, `write(char[])`, `write(int)` | Ghi văn bản/đơn vị mã vào bộ đệm |
| `append(CharSequence)` | Nối nội dung theo giao diện `Appendable`; trả về writer để tiếp tục nối |
| `toString()` | Lấy `String` biểu diễn nội dung hiện tại |
| `getBuffer()` | Trả về `StringBuffer` **nội bộ**, có thể sửa trực tiếp |
| `flush()`, `close()` | Không có tác dụng với chính `StringWriter`; nội dung vẫn lấy được sau khi đóng |

Ví dụ độc lập **`StringWriterDemo.java`**:

```java
import java.io.StringWriter;

public class StringWriterDemo {
    public static void main(String[] args) {
        StringWriter writer = new StringWriter();
        writer.write("Xin ");
        writer.append("chào");
        writer.write('!');

        System.out.println(writer.toString());
        System.out.println("length=" + writer.getBuffer().length());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 StringWriterDemo.java
java StringWriterDemo
```

Đầu ra:

```text
Xin chào!
length=9
```

`getBuffer()` cho quyền truy cập trực tiếp bộ đệm có thể thay đổi; chỉ dùng khi thật sự cần thao tác với `StringBuffer`. Đa số trường hợp nên lấy kết quả qua `toString()`. `StringWriter(int initialSize)` cho phép đặt dung lượng ban đầu; đây là **capacity**, không phải giới hạn cứng về số ký tự sẽ ghi.

`StringWriter` phù hợp khi thư viện chỉ nhận `Writer`, ví dụ renderer mẫu, formatter, serializer văn bản hoặc công cụ kiểm thử muốn thu output để so sánh. Nếu chỉ nối vài đoạn chuỗi trong mã ứng dụng và không cần API `Writer`, `StringBuilder` thường trực tiếp và đơn giản hơn.

## 5. `CharArrayReader`: đọc một mảng hoặc một đoạn mảng

`CharArrayReader` nhận `char[]` có sẵn:

- `new CharArrayReader(buffer)`: đọc toàn bộ mảng.
- `new CharArrayReader(buffer, offset, length)`: đọc **một đoạn** bắt đầu tại `offset` với tối đa `length` phần tử, không cần tạo chuỗi trung gian.
- Hỗ trợ `read()`, `read(char[], offset, length)`, `skip(...)`, `mark(...)` và `reset()`.

Ví dụ độc lập **`CharArrayReaderDemo.java`**:

```java
import java.io.CharArrayReader;
import java.io.IOException;

public class CharArrayReaderDemo {
    public static void main(String[] args) throws IOException {
        char[] source = "xABCDEy".toCharArray();

        try (CharArrayReader reader = new CharArrayReader(source, 1, 5)) {
            char[] block = new char[3];

            int first = reader.read(block, 0, block.length);
            System.out.println("chunk=" + new String(block, 0, first));

            reader.mark(10);
            System.out.println("next=" + (char) reader.read());

            reader.reset();
            System.out.println("again=" + (char) reader.read());
            System.out.println("last=" + (char) reader.read());
            System.out.println("EOF=" + reader.read());
        }
    }
}
```

Chạy:

```bash
javac CharArrayReaderDemo.java
java CharArrayReaderDemo
```

Đầu ra:

```text
chunk=ABC
next=D
again=D
last=E
EOF=-1
```

Reader này chỉ thấy đoạn `ABCDE`: ký tự `x` và `y` nằm ngoài phạm vi đã chọn. `read(char[], ...)` trả **số `char` thực đọc**, vì vậy dùng `new String(block, 0, first)`; không mặc định toàn bộ mảng đích đều vừa được điền dữ liệu mới.

**Điểm cần lưu ý về mảng:** `CharArrayReader` sử dụng mảng được truyền vào; không cần tạo bản sao độc lập theo hợp đồng constructor. Nếu mã khác sửa các phần tử mảng nguồn khi reader đang hoạt động, dữ liệu đọc có thể thay đổi theo. Không chia sẻ mảng có thể bị sửa đồng thời khi không có cơ chế phối hợp.

## 6. `CharArrayWriter`: ghi vào mảng `char` có thể tăng dung lượng

`CharArrayWriter` bắt đầu bằng bộ đệm ký tự nội bộ và tự tăng khi cần. Khác với `CharArrayReader` (nhận mảng có sẵn để đọc), `CharArrayWriter` sở hữu vùng nhớ để **nhận dữ liệu ghi vào**.

API chính:

| Phương thức | Kết quả |
| --- | --- |
| `write(...)` hoặc `append(...)` | Thêm ký tự vào bộ đệm |
| `toCharArray()` | Lấy **bản sao** `char[]` của nội dung hiện tại |
| `toString()` | Lấy bản `String` của nội dung |
| `size()` | Số đơn vị mã `char` hiện có |
| `writeTo(Writer other)` | Ghi nội dung hiện tại sang một writer khác |
| `reset()` | Đặt độ dài logic về 0 để tái sử dụng bộ đệm |
| `close()` | Không có tác dụng; lớp này vẫn có thể sử dụng sau khi đóng |

Chương trình độc lập **`CharArrayWriterDemo.java`**:

```java
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.StringWriter;

public class CharArrayWriterDemo {
    public static void main(String[] args) throws IOException {
        StringWriter destination = new StringWriter();

        try (CharArrayWriter writer = new CharArrayWriter(2)) {
            writer.write("Java");

            char[] snapshot = writer.toCharArray();
            writer.writeTo(destination);

            writer.reset();
            writer.write("IO");

            System.out.println("snapshot=" + new String(snapshot));
            System.out.println("destination=" + destination);
            System.out.println("reused=" + writer);
            System.out.println("size=" + writer.size());
        }
    }
}
```

Chạy:

```bash
javac CharArrayWriterDemo.java
java CharArrayWriterDemo
```

Đầu ra:

```text
snapshot=Java
destination=Java
reused=IO
size=2
```

`new CharArrayWriter(2)` chỉ cấp **capacity ban đầu bằng 2**, nên vẫn ghi được `Java` (4 ký tự). `toCharArray()` trả bản sao: sau `reset()` và ghi `IO`, biến `snapshot` vẫn chứa `Java`. `writeTo(destination)` chuyển nội dung tại thời điểm gọi, vì thế `destination` giữ `Java`.

`reset()` giúp tái sử dụng vùng nhớ trong các lượt xử lý lặp lại, nhưng bộ nhớ đã được cấp vẫn có thể được giữ lại. Khi làm việc với văn bản cực lớn, đừng giữ một writer có capacity lớn mãi nếu ứng dụng không cần tái sử dụng nó.

## 7. Ví dụ tích hợp: `StringReader` → `BufferedReader` → `BufferedWriter` → `StringWriter`

Đây là tình huống thường gặp khi một API phải xử lý từng dòng văn bản nhưng ta muốn tất cả hoạt động trong RAM. Ví dụ chuẩn hóa một văn bản gồm hai dòng, đánh số và chuyển thành chữ hoa.

Tạo tệp **`InMemoryTextPipeline.java`**:

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;

public class InMemoryTextPipeline {
    public static void main(String[] args) throws IOException {
        String source = "  táo  \n  chuối \n";
        StringWriter destination = new StringWriter();

        try (BufferedReader input =
                     new BufferedReader(new StringReader(source));
             BufferedWriter output =
                     new BufferedWriter(destination)) {

            String line;
            int lineNumber = 1;

            while ((line = input.readLine()) != null) {
                String normalized = line.strip().toUpperCase(Locale.ROOT);
                output.write(lineNumber + ":" + normalized);
                output.write('\n');
                lineNumber++;
            }
        }

        System.out.print(destination.toString());
    }
}
```

Chạy với Java 17+:

```bash
javac -encoding UTF-8 InMemoryTextPipeline.java
java InMemoryTextPipeline
```

Đầu ra:

```text
1:TÁO
2:CHUỐI
```

Dòng dữ liệu đi qua các tầng như sau:

1. `StringReader(source)` chuyển chuỗi có sẵn thành `Reader`; không tạo file và không diễn ra encode/decode UTF-8.
2. `BufferedReader` cho phép đọc theo dòng bằng `readLine()`; dấu xuống dòng không nằm trong kết quả `line`.
3. `strip()` bỏ khoảng trắng ở đầu/cuối mỗi dòng; `toUpperCase(Locale.ROOT)` biến `táo`, `chuối` thành `TÁO`, `CHUỐI` theo quy tắc Unicode, không dựa vào locale mặc định.
4. `BufferedWriter` nhận chuỗi đã xử lý, ghép `\n` **tường minh** để kết quả không phụ thuộc ký tự xuống dòng của hệ điều hành.
5. Khi rời `try-with-resources`, writer ngoài cùng được đóng và flush, nên `StringWriter destination` chứa toàn bộ kết quả. Đóng chính `StringWriter` không làm mất kết quả được lưu trong RAM.

Ví dụ này cũng phù hợp với **unit test**: một hàm xử lý `Reader` → `Writer` có thể kiểm thử chỉ với `StringReader` và `StringWriter`, không cần tạo tệp tạm hay kết nối mạng.

## 8. Cẩn thận với Unicode: `char` khác code point

Vì tất cả những reader/writer này dùng đơn vị mã UTF-16, một emoji thường cần hai `char`. Ví dụ độc lập **`CharUnitsDemo.java`**:

```java
import java.io.IOException;
import java.io.StringReader;

public class CharUnitsDemo {
    public static void main(String[] args) throws IOException {
        String text = "A😀B";

        System.out.println("length=" + text.length());
        System.out.println("codePoints="
                + text.codePointCount(0, text.length()));

        int units = 0;
        try (StringReader reader = new StringReader(text)) {
            while (reader.read() != -1) {
                units++;
            }
        }
        System.out.println("reader units=" + units);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 CharUnitsDemo.java
java CharUnitsDemo
```

Đầu ra:

```text
length=4
codePoints=3
reader units=4
```

`A` và `B` mỗi chữ dùng một đơn vị mã. Emoji `😀` được biểu diễn bởi một **cặp surrogate**, nên tổng `String.length()` và số lần đọc `StringReader.read()` là 4, còn số code point là 3.

**Hệ quả khi xử lý:** Nếu cần thao tác theo Unicode code point, hãy kết hợp đúng surrogate pair (ví dụ qua `String.codePoints()` hoặc `Character.toCodePoint(...)`), thay vì cho rằng mỗi lần `read()` là một ký tự độc lập mà người dùng nhìn thấy. Cắt `char[]` giữa cặp surrogate có thể tạo văn bản không hợp lệ khi chuyển sang byte theo charset.

## 9. Có nên thêm `BufferedReader` hoặc `BufferedWriter` không?

Đối với nguồn và đích đã ở trong bộ nhớ:

- `StringReader` đọc từ một chuỗi hiện hữu, `CharArrayReader` đọc trực tiếp từ mảng. Chúng không chịu chi phí gọi hệ điều hành để đọc đĩa/mạng.
- `StringWriter` và `CharArrayWriter` ghi vào bộ đệm RAM. Không bắt buộc phải bọc `BufferedWriter` chỉ vì muốn “tăng tốc”.
- `BufferedReader` vẫn hữu ích khi cần `readLine()`, vì phương thức này không tồn tại trên `StringReader` hay `CharArrayReader`.
- `BufferedWriter` hữu ích khi muốn API `newLine()` hoặc khi tái sử dụng cùng thuật toán cho writer khác, nhưng lớp đệm bổ sung có thể chưa đẩy nội dung xuống writer bên dưới cho đến `flush()` hoặc `close()`.

Chọn wrapper theo **giao diện thuật toán cần dùng**, tránh đệm lặp lại vì thói quen. Nếu nguồn bên dưới là file/socket, buffer đôi khi quan trọng để giảm lượt I/O vật lý; điều đó không tự động áp dụng cho mọi tác vụ trong RAM.

Một thiết kế linh hoạt là hàm nhận `Reader` và `Writer`:

```java
static void copyCharacters(Reader source, Writer destination)
        throws IOException {
    char[] buffer = new char[1024];
    int count;

    while ((count = source.read(buffer)) != -1) {
        destination.write(buffer, 0, count);
    }
}
```

Đây là **đoạn mã minh họa**, cần import `java.io.Reader`, `java.io.Writer` và `java.io.IOException`, đưa vào trong một class thích hợp. Nó hoạt động với `StringReader`, `CharArrayReader`, `FileReader` hoặc bất kỳ `Reader` tuân hợp đồng; tương tự đối với đích `Writer`. Bản thân hàm không đóng source/destination vì caller thường là bên quyết định vòng đời.

## 10. Vòng đời tài nguyên, sao chép và dung lượng bộ nhớ

Mặc dù các lớp hoạt động trong RAM, chúng vẫn có hành vi `close()` riêng:

| Lớp | Sau `close()` |
| --- | --- |
| `StringReader` | Không tiếp tục đọc; thao tác có thể gây `IOException` |
| `CharArrayReader` | Không tiếp tục đọc; thao tác có thể gây `IOException` |
| `StringWriter` | `close()` không có tác dụng; nội dung vẫn có thể truy xuất và writer có thể tiếp tục dùng |
| `CharArrayWriter` | `close()` không có tác dụng; bộ đệm còn đó, vẫn dùng được |

Chính vì vậy việc đọc tiếp từ reader sau khi đóng không phải cách truy xuất lại nguồn. Nếu cần lượt đọc khác, có thể tạo `StringReader` mới từ `String` gốc, hoặc một `CharArrayReader` mới từ mảng còn hợp lệ.

So sánh các cách lưu dữ liệu:

- `String` bất biến, phù hợp để giữ kết quả không cần sửa; `StringReader` dùng nó như nguồn.
- `char[]` thay đổi được, phù hợp khi muốn quản lý vùng nhớ và đoạn dữ liệu; `CharArrayReader` làm việc trên mảng nguồn nên tránh sửa mảng trong lúc đang đọc.
- `StringWriter` dùng `StringBuffer` nội bộ, có thể chuyển kết quả thành `String`. `getBuffer()` cho phép tác động vào bộ đệm nên cần kiểm soát nơi truy cập.
- `CharArrayWriter.toCharArray()` trả **bản sao**, còn `reset()` xóa độ dài logic mà thường giữ capacity.
- Kết quả `toString()` hoặc `toCharArray()` có thể yêu cầu cấp phát bộ nhớ bổ sung. Với nội dung rất lớn, quá nhiều bản sao cùng tồn tại làm tăng áp lực heap.

Nếu dữ liệu đầu vào thực sự là **byte**, chọn `ByteArrayInputStream` hoặc giải mã bằng `InputStreamReader(..., charset)` theo đúng nhu cầu. Không ép từng byte thành `char` để “đổi sang luồng ký tự”: việc đó không phải chuyển mã Unicode. Tương tự, khi cần tạo byte, dùng `OutputStreamWriter(..., charset)`/`String.getBytes(charset)` thay vì xem `char[]` như một mảng byte.

## 11. Lỗi phổ biến và checklist chọn lớp

| Nhầm lẫn | Vì sao sai | Cách sửa |
| --- | --- | --- |
| `StringReader` là `InputStream` | Nó là `Reader` và làm việc trên `char` | Chỉ dùng API yêu cầu `Reader` |
| `StringWriter` ghi vào tệp | Nó ghi vào bộ đệm RAM | Dùng `Files.newBufferedWriter` hoặc writer dựa trên file để ghi tệp |
| `CharArrayReader` tự copy mảng nguồn | Reader làm việc với mảng được truyền vào | Không sửa/chia sẻ mảng đầu vào không kiểm soát |
| `read()` đọc một Unicode code point | `Reader.read()` đọc một đơn vị mã UTF-16 | Xử lý surrogate pair khi cần code point |
| `read(char[])` luôn điền đầy mảng | Có thể đọc ít hơn độ dài yêu cầu | Dùng đúng `count` được trả về |
| `CharArrayWriter.toCharArray()` trả mảng nội bộ | API trả bản sao nội dung | Dùng `reset()` để tái sử dụng, đừng mong sửa snapshot sẽ đổi writer |
| Tưởng `reset()` của mọi reader đều về đầu | `reset()` về vị trí `mark` nếu đã đánh dấu | Xác định rõ vị trí mark và kiểm tra `markSupported()` |
| Đọc `StringWriter` bọc bởi `BufferedWriter` quá sớm | Dữ liệu có thể còn trong buffer ngoài | `flush()` hoặc đóng BufferedWriter trước khi lấy kết quả |
| Thêm charset vào `StringReader` | Reader đã làm việc trên ký tự | Chỉ định charset ở ranh giới byte ↔ ký tự khi thực sự có byte |
| Dùng `StringWriter` cho dữ liệu khổng lồ không giới hạn | Bộ đệm có thể chiếm nhiều heap | Xem xét streaming, giới hạn đầu vào và tránh bản sao lớn |

**Tự luyện:** Viết hàm `normalize(Reader input, Writer output)` đọc từng dòng rồi thêm số thứ tự. Thử hai cách truyền dữ liệu: `StringReader`/`StringWriter` và `CharArrayReader`/`CharArrayWriter`. Khi chạy cùng nội dung, so sánh hai chuỗi đầu ra bằng `equals`. Sau đó thử chuỗi chứa emoji để giải thích vì sao `String.length()` và số code point có thể khác nhau.

**Ghi nhớ:** `StringReader`/`CharArrayReader` biến văn bản sẵn có trong RAM thành **nguồn `Reader`**; `StringWriter`/`CharArrayWriter` biến API **`Writer`** thành **đích trong RAM**. Chúng làm việc với đơn vị mã `char`, không tự chuyển byte theo charset. Chọn lớp theo dạng dữ liệu sẵn có, quản lý đúng vòng đời và kiểm soát các bản sao khi tài liệu lớn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="in-memory-io-004">Chuyển byte&#91;&#93; sang String bằng charset xác định</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển byte[] sang String bằng charset xác định trong Java

Khi đọc dữ liệu từ `ByteArrayOutputStream`, tệp nhỏ, socket, bộ nhớ đệm hoặc một API trả về `byte[]`, chương trình thường cần khôi phục **văn bản** dưới dạng `String`. Bước chuyển đổi tưởng như đơn giản nhưng chỉ đúng khi ta biết **những byte đó được mã hóa bằng charset nào** và xử lý rõ dữ liệu byte không hợp lệ.

Trong Java 17+, cách thông dụng là:

```java
String text = new String(bytes, StandardCharsets.UTF_8);
```

Trong đó `bytes` là mảng `byte[]` đã chứa **dữ liệu văn bản mã hóa bằng UTF-8**, và `StandardCharsets.UTF_8` là charset đã được xác định theo hợp đồng dữ liệu. Mảnh mã cần `import java.nio.charset.StandardCharsets;`.

Bài này thuộc nhóm **I/O trong bộ nhớ**: chuyển mảng byte sang chuỗi, xác định đúng charset, hiểu lỗi khi giải mã sai, chọn hành vi khi byte không hợp lệ và biết khi nào cần xử lý theo luồng thay vì nạp toàn bộ dữ liệu.

## 1. Đừng nhầm byte với ký tự

Java `byte[]` là tập hợp các đơn vị 8 bit. Nó **không mang thông tin charset**; ví dụ các byte `C3 A9` tự nó không nói rằng chúng phải trở thành chữ `é`.

```text
String --mã hóa (encode bằng UTF-8)--> byte[]
   "é"                         C3 A9
                                    |
                              giải mã (decode bằng UTF-8)
                                    |
                                    v
                                  "é"
```

Nếu cùng byte `C3 A9` được giải mã theo ISO-8859-1, ta nhận hai ký tự tương ứng `Ã` và `©`, thường hiển thị thành `"Ã©"`. Vì vậy cùng một `byte[]` có thể cho các `String` khác nhau tùy charset.

Phân biệt ba khái niệm:

- **Byte:** đơn vị dữ liệu thô; `byte[]` chứa byte có thể là văn bản, ảnh PNG, file ZIP, mã hóa nhị phân hay dữ liệu nén.
- **Ký tự Java:** `String` sử dụng các đơn vị UTF-16; `char` là đơn vị 16 bit, còn một code point ngoài BMP có thể cần hai `char`.
- **Charset:** quy tắc biến một chuỗi ký tự thành byte và biến byte trở lại ký tự, ví dụ UTF-8, UTF-16, US-ASCII, ISO-8859-1.

Không có phép “ép kiểu” `(String) bytes` hợp lệ cho `byte[]`. Cũng không nên dùng `Arrays.toString(bytes)` để giải mã: phương thức này tạo **biểu diễn văn bản của các số byte**, như `[65, 66]`, chứ không tạo `"AB"`.

## 2. Constructor nên ưu tiên: new String(byte[], Charset)

Cú pháp:

```java
byte[] bytes = { 72, 101, 108, 108, 111 };
String text = new String(bytes, StandardCharsets.UTF_8);
System.out.println(text); // Hello
```

Đây là đoạn mã minh họa cần import `StandardCharsets`. Dữ liệu chỉ gồm ASCII nên khi giải mã UTF-8 mỗi byte tương ứng một ký tự như mong đợi.

API chính:

```java
new String(byte[] bytes, Charset charset)
new String(byte[] bytes, int offset, int length, Charset charset)
```

- `bytes`: mảng byte đầu vào cần giải mã.
- `charset`: bộ mã được chỉ định tường minh; thường dùng một hằng trong `StandardCharsets`.
- `offset`: **vị trí byte bắt đầu** trong mảng.
- `length`: **số byte phải giải mã**, không phải vị trí byte kết thúc, cũng không phải số ký tự sẽ tạo ra.
- Kết quả là một `String` mới chứa các đơn vị ký tự đã giải mã. Thay đổi mảng `bytes` sau đó không thay đổi nội dung của `String` đã tạo.
- `null` cho đối số bắt buộc và offset/length không hợp lệ sẽ làm thao tác thất bại; hãy kiểm tra đầu vào trước khi giải mã nếu chúng đến từ nguồn ngoài.

Cách viết này thể hiện **hợp đồng định dạng dữ liệu** ngay ở điểm chuyển đổi. Không trông chờ vào charset mặc định của máy chạy, cấu hình locale hoặc cách console đang hiển thị.

### Những charset thường gặp

| Charset | Đặc điểm | Khi nào dùng |
| --- | --- | --- |
| `StandardCharsets.UTF_8` | Unicode, độ dài mỗi code point có thể từ 1 đến 4 byte | Mặc định hợp lý cho định dạng văn bản hiện đại, nếu protocol/file khai báo UTF-8 |
| `StandardCharsets.US_ASCII` | 7-bit ASCII | Giao thức giới hạn rõ trong ASCII |
| `StandardCharsets.ISO_8859_1` | Mỗi byte 0–255 ánh xạ sang một ký tự Unicode tương ứng | Dữ liệu legacy có hợp đồng Latin-1 |
| `StandardCharsets.UTF_16BE` | UTF-16 theo thứ tự byte big-endian, không tự phát BOM | Dữ liệu có quy định rõ UTF-16BE |
| `StandardCharsets.UTF_16LE` | UTF-16 theo thứ tự byte little-endian, không tự phát BOM | Dữ liệu có quy định rõ UTF-16LE |
| `StandardCharsets.UTF_16` | UTF-16 có quy tắc BOM và thứ tự byte theo đặc tả charset | Dữ liệu thực sự được khai báo UTF-16 |

**Không chọn ISO-8859-1 chỉ vì nó có thể giải mã mọi byte:** kết quả có thể là chuỗi ký tự sai nghĩa. Cũng không chọn UTF-8 một cách suy đoán nếu định dạng nguồn nói rõ một charset khác. Với charset không có trong `StandardCharsets`, dùng `Charset.forName("...")` khi tên đã được xác minh và nhà cung cấp runtime hỗ trợ; một tên sai hoặc không được hỗ trợ có thể gây lỗi tại bước lấy charset.

## 3. Vì sao phải ghi rõ charset?

Hai constructor sau không tương đương về mức độ xác định đầu ra:

```java
String implicit = new String(bytes);
String explicit = new String(bytes, StandardCharsets.UTF_8);
```

`new String(bytes)` dùng **charset mặc định** của môi trường Java. Trong Java 17, mặc định có thể phụ thuộc hệ điều hành/cấu hình. Từ Java 18, UTF-8 là mặc định theo JEP 400 trong cấu hình thông thường, nhưng mã cần trao đổi dữ liệu với hệ thống khác **vẫn nên khai báo charset rõ ràng**.

Tác hại của việc ngầm định:

1. Một file được ghi theo UTF-8 có thể đọc đúng trên máy A nhưng lỗi dấu trên máy B dùng charset mặc định khác.
2. Một chương trình chạy thành công trong IDE nhưng cho kết quả khác khi chạy job, container hoặc runtime cấu hình khác.
3. Phía nhận không thể xác định rõ vì sao byte trở thành ký tự; việc sửa lỗi khó khăn hơn.

Quy tắc bền vững: **nơi tạo byte và nơi đọc byte phải thống nhất charset bằng đặc tả dữ liệu**. Ví dụ, file cấu hình yêu cầu UTF-8 thì cả hai đầu dùng UTF-8; HTTP phải tôn trọng protocol và `Content-Type`/quy tắc tương ứng; một file legacy đã được quy định Windows-1252 thì phải sử dụng charset đó nếu runtime hỗ trợ.

## 4. Ví dụ Java 17+ hoàn chỉnh: từ String → byte[] → String

Tạo tệp `ByteArrayToStringDemo.java`:

```java
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Collectors;

public class ByteArrayToStringDemo {
    public static void main(String[] args) throws Exception {
        String original = "Ch\u00E0o Java \u2615";
        byte[] utf8 = original.getBytes(StandardCharsets.UTF_8);

        System.out.println("UTF-8 bytes: " + utf8.length);
        System.out.println("Hex: "
                + HexFormat.ofDelimiter(" ").withUpperCase()
                        .formatHex(utf8));

        String restored = new String(utf8, StandardCharsets.UTF_8);
        System.out.println("Round trip: " + original.equals(restored));

        // Chỉ giải mã 3 byte từ vị trí 1: 41 C3 A9 = "Aé".
        byte[] frame = {
                0x21, 0x41, (byte) 0xC3, (byte) 0xA9, 0x3F
        };
        String slice = new String(frame, 1, 3, StandardCharsets.UTF_8);
        System.out.println("Slice A\u00E9: " + "A\u00E9".equals(slice));

        // Cùng 2 byte, nhưng một charset không đúng với nguồn.
        byte[] eAcute = { (byte) 0xC3, (byte) 0xA9 };
        String wronglyDecoded =
                new String(eAcute, StandardCharsets.ISO_8859_1);
        System.out.println("Wrong charset equal: "
                + "\u00E9".equals(wronglyDecoded));

        // C3 28 là UTF-8 lỗi; constructor String có chính sách thay thế.
        byte[] bad = { (byte) 0xC3, (byte) 0x28 };
        String forgiving = new String(bad, StandardCharsets.UTF_8);
        String points = forgiving.codePoints()
                .mapToObj(cp -> "U+" + Integer.toHexString(cp)
                        .toUpperCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
        System.out.println("Decoded error code points: " + points);

        // Cùng dữ liệu lỗi, nhưng decoder nghiêm ngặt từ chối.
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bad));
        } catch (CharacterCodingException e) {
            System.out.println("Strict decoder: "
                    + e.getClass().getSimpleName());
        }

        // Lấy byte[] từ OutputStream ghi trong bộ nhớ, rồi giải mã.
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (OutputStreamWriter writer =
                     new OutputStreamWriter(sink, StandardCharsets.UTF_8)) {
            writer.write("Hi \u2615");
        }
        System.out.println("BAOS toString Charset: "
                + "Hi \u2615".equals(sink.toString(StandardCharsets.UTF_8)));
    }
}
```

Biên dịch/chạy:

```bash
javac ByteArrayToStringDemo.java
java ByteArrayToStringDemo
```

Stdout đã đối chiếu với JDK:

```text
UTF-8 bytes: 14
Hex: 43 68 C3 A0 6F 20 4A 61 76 61 20 E2 98 95
Round trip: true
Slice Aé: true
Wrong charset equal: false
Decoded error code points: U+FFFD U+28
Strict decoder: MalformedInputException
BAOS toString Charset: true
```

Giải thích:

- `"Chào Java ☕"` có 14 byte UTF-8: `à` là `C3 A0`, còn ký hiệu `☕` là `E2 98 95`. `utf8.length` đếm byte nên khác số ký tự.
- Giải mã bằng cùng UTF-8 tạo chuỗi tương đương với chuỗi gốc; `original.equals(restored)` trả `true`.
- `frame` có hai byte đánh dấu ở hai đầu (`!` và `?`). Lệnh `new String(frame, 1, 3, UTF_8)` chỉ dùng ba byte `41 C3 A9`, tạo `"Aé"`; không kéo theo các byte ngoài vùng được chọn.
- Giải mã `C3 A9` bằng ISO-8859-1 không tạo đúng một ký tự `é`, nên so sánh trả `false`.
- Byte `C3` đứng trước `28` không tạo thành cặp UTF-8 hợp lệ. Constructor `String` với charset thay chuỗi lỗi bằng `U+FFFD` và vẫn giữ ký tự `(` là `U+28`.
- `CharsetDecoder` được cấu hình `REPORT` không chấp nhận dữ liệu đó, nên phát sinh `MalformedInputException`.
- `ByteArrayOutputStream` giúp kết nối trực tiếp bài học với **I/O trong bộ nhớ**: `OutputStreamWriter` mã hóa ký tự thành byte, còn `toString(StandardCharsets.UTF_8)` giải mã byte đã ghi.

Ví dụ cố ý in phần lớn kết quả dưới dạng số, hex hoặc boolean để không phụ thuộc khả năng hiển thị dấu tiếng Việt/emoji của terminal.

## 5. Giải mã một phần byte[]: offset và length là byte

Constructor:

```java
String text = new String(bytes, offset, length, charset);
```

Nó có ý nghĩa: bắt đầu tại chỉ số `offset` trong mảng, lấy **đúng `length` byte** và giải mã phần đó. Độ dài không phải `endIndex`, và không cho phép vượt biên mảng.

Ví dụ `byte[]` chứa:

```text
index: 0   1   2   3   4
byte:  21  41  C3  A9  3F
       !   A    \--é--/ ?
```

`new String(bytes, 1, 3, UTF_8)` nhận `41 C3 A9` và tạo `"Aé"`. Hai lệnh thường bị nhầm:

```java
new String(bytes, 1, 3, StandardCharsets.UTF_8); // bytes 1, 2, 3
new String(bytes, 1, 4, StandardCharsets.UTF_8); // bytes 1, 2, 3, 4
```

Dòng hai lấy bốn byte, bao gồm byte `3F` (`?`). **`offset + length` chỉ được đến `bytes.length`**, không được vượt. Nếu thông tin offset/length đến từ network hoặc cấu trúc dữ liệu chưa đáng tin, kiểm tra cẩn thận để tránh chỉ số âm, vượt biên hoặc overflow số nguyên. Cách kiểm tra khi các biến đều là `int`:

```java
if (offset < 0 || length < 0
        || offset > bytes.length
        || length > bytes.length - offset) {
    throw new IllegalArgumentException("Invalid byte slice");
}
String part = new String(bytes, offset, length, StandardCharsets.UTF_8);
```

Đây là **mảnh mã minh họa**, `bytes`, `offset`, `length` phải có sẵn.

### Không được cắt ở giữa một ký tự UTF-8

Ví dụ byte UTF-8 của `é` là `C3 A9`. Nếu cắt đúng một byte `C3` rồi gọi:

```java
new String(new byte[] { (byte) 0xC3 }, StandardCharsets.UTF_8);
```

Constructor không nhận đủ dữ liệu để tái tạo `é`; với quy tắc thay thế lỗi, nó sinh ký tự thay thế `U+FFFD`. Việc cắt phần mảng đúng về chỉ số **không đảm bảo đúng ranh giới ký tự**.

Cần phân biệt:

- Nếu định dạng chỉ ra **offset và length theo byte**, phải bảo đảm đó là những ranh giới hợp lệ của dữ liệu theo charset; hoặc dùng decoder tăng dần giữ phần byte dở cho chunk tiếp theo.
- Nếu muốn cắt **theo ký tự Java**, hãy giải mã đúng dữ liệu trước rồi thao tác trên `String`. Nếu cần cắt theo Unicode code point, dùng API `codePoint*` vì `String.substring` vẫn dùng vị trí UTF-16 và có thể cắt đôi cặp surrogate.
- Nếu dữ liệu gồm nhiều bản tin, lấy byte slice theo framing/độ dài bản tin thay vì cố dựa vào `String.length()`.

## 6. Byte[] trong bộ nhớ: những API cần biết

### 6.1. ByteArrayOutputStream → byte[] → String

`ByteArrayOutputStream` là một `OutputStream` ghi byte vào bộ đệm bộ nhớ. Khi đã ghi xong:

```java
ByteArrayOutputStream out = new ByteArrayOutputStream();
out.write(new byte[] { 65, 66, 67 });
byte[] bytes = out.toByteArray();
String text = new String(bytes, StandardCharsets.US_ASCII); // "ABC"
```

Đây là mảnh mã; cần `java.io.ByteArrayOutputStream` và `java.nio.charset.StandardCharsets`. `toByteArray()` **tạo bản sao** của các byte đang có trong buffer; kết quả không bao gồm phần dung lượng chưa sử dụng.

Java cũng có cách viết gọn:

```java
String text = out.toString(StandardCharsets.UTF_8);
```

`ByteArrayOutputStream.toString(Charset)` giải mã **toàn bộ byte hiện có trong bộ đệm** theo charset đã chọn, thuận tiện khi mục đích cuối cùng là `String`. Phương thức này có từ Java 10 và dùng được trong Java 17+. `out.toString()` không truyền charset lại phụ thuộc default charset, vì vậy không nên dùng cho nội dung trao đổi giữa môi trường khác nhau.

Một `ByteArrayOutputStream` có thể được đọc sau `close()` vì việc đóng loại stream này không làm nội dung bộ nhớ mất hiệu lực. **Không suy diễn rằng mọi OutputStream đều tiếp tục đọc/ghi được sau close.**

### 6.2. ByteArrayInputStream chứa byte, không tự biến thành String

`ByteArrayInputStream` đọc một mảng byte như nguồn stream:

```java
byte[] bytes = "Xin chào".getBytes(StandardCharsets.UTF_8);
try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
    byte[] allBytes = in.readAllBytes();
    String text = new String(allBytes, StandardCharsets.UTF_8);
}
```

Mảnh mã cần import `java.io.ByteArrayInputStream` và `java.nio.charset.StandardCharsets`. `InputStream.readAllBytes()` có từ Java 9. Cách này giúp quan sát luồng byte nhưng nếu **đã có mảng byte ban đầu**, có thể gọi trực tiếp `new String(bytes, UTF_8)`; tạo thêm `InputStream` rồi đọc lại chỉ để chuyển đổi là không cần thiết.

Khi cần xử lý luồng lớn hoặc dữ liệu từng phần, một `InputStreamReader(in, UTF_8)` có thể phù hợp hơn, vì nó chuyển byte sang ký tự trong quá trình đọc thay vì gom mọi byte thành một mảng lớn.

### 6.3. ByteBuffer khác byte[]

Khi dùng Java NIO, byte đọc về có thể nằm trong `ByteBuffer` với `position` và `limit`. Không giả định `buffer.array()` luôn tồn tại (direct/read-only buffer có thể không có mảng truy cập được), hoặc `array()` đúng bằng phần byte có hiệu lực. Chỉ các byte trong khoảng `position` đến `limit` mới là **remaining bytes** cần giải mã.

Ví dụ:

```java
ByteBuffer buffer = ByteBuffer.wrap(
        "OK".getBytes(StandardCharsets.UTF_8));
String text = StandardCharsets.UTF_8.decode(buffer).toString();
```

Đây là mảnh mã, cần import `java.nio.ByteBuffer` và `java.nio.charset.StandardCharsets`. `Charset.decode(ByteBuffer)` là hàm tiện ích có chính sách thay thế dữ liệu lỗi; nếu bắt buộc từ chối byte không hợp lệ, dùng một `CharsetDecoder` với `CodingErrorAction.REPORT`.

## 7. Charset sai tạo lỗi dấu: mojibake

Giả sử byte UTF-8:

```text
é → C3 A9 (UTF-8)
```

Giải mã bằng charset ISO-8859-1 thì:

```java
byte[] utf8 = { (byte) 0xC3, (byte) 0xA9 };
String right = new String(utf8, StandardCharsets.UTF_8);        // "é"
String wrong = new String(utf8, StandardCharsets.ISO_8859_1);  // "Ã©"
```

Đây là ví dụ độc lập về ánh xạ byte; charset ISO-8859-1 **vẫn giải mã thành các ký tự hợp lệ**, chỉ sai nghĩa đối với nguồn đã được mã hóa theo UTF-8. Vì vậy **không có exception** chỉ vì nội dung hiển thị sai dấu.

Khi nghi ngờ sai charset:

1. Xác định **nguồn tạo byte**, tài liệu định dạng, header hoặc metadata cung cấp charset gì.
2. Kiểm tra các byte thực tế bằng hex nếu cần; ví dụ `C3 A9` tương ứng UTF-8 `é`.
3. Đổi **charset tại bước giải mã byte** theo hợp đồng đúng; tránh sửa lỗi bằng chuỗi `replace(...)` dựa trên các ký tự đã hiển thị sai.
4. Nếu dữ liệu đã được đọc sai, còn **byte gốc** thì giải mã lại từ byte gốc. Nếu không còn byte gốc và nội dung đã qua các phép biến đổi mất thông tin, không phải lúc nào cũng khôi phục hoàn toàn được.
5. Không “dò charset” từ một byte[] ngắn rồi coi kết quả là chắc chắn. Nhiều charset cho các kết quả trông hợp lệ, nhất là dữ liệu chỉ có ASCII.

Mọi byte từ `0x00` đến `0x7F` của nội dung ASCII cơ bản đều được UTF-8 mã hóa giống US-ASCII; vì vậy test chỉ có `"ABC"` không chứng minh cách giải mã tiếng Việt là đúng. Hãy đưa vào dữ liệu có dấu, emoji hoặc ký tự nằm ngoài ASCII khi kiểm thử giao thức văn bản Unicode.

## 8. Dữ liệu UTF-8 không hợp lệ: constructor String có thể âm thầm thay thế

Đoạn byte:

```text
C3 28
```

là UTF-8 không hợp lệ, vì `C3` mở đầu một chuỗi hai byte nhưng `28` không phải continuation byte. Khi dùng:

```java
byte[] bad = { (byte) 0xC3, (byte) 0x28 };
String text = new String(bad, StandardCharsets.UTF_8);
```

Constructor `String(byte[], Charset)` **thay thế** chuỗi byte malformed/unmappable bằng ký tự thay thế được dùng trong giải mã, không ném `CharacterCodingException` cho lỗi đó. Trong UTF-8 của Java, chuỗi ở trên trở thành `U+FFFD` rồi `U+0028` (`(`).

Điều này hữu ích cho UI xem trước dữ liệu hỏng, nhưng là vấn đề khi:

- Định dạng yêu cầu **UTF-8 hợp lệ tuyệt đối**.
- Hệ thống ký/xác minh dữ liệu và cần giữ nguyên thông tin gốc.
- Tên file, identifier, trường giao thức hoặc dữ liệu quan trọng không được phép bị thay/loại bỏ ký tự.
- Phần mềm muốn báo chính xác bản tin/tệp nào có byte bị cắt cụt.

### Dùng CharsetDecoder để từ chối byte lỗi

```java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class StrictUtf8Example {
    public static void main(String[] args) {
        byte[] bytes = { (byte) 0xC3, (byte) 0x28 };

        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            System.out.println("Valid UTF-8: " + text);
        } catch (CharacterCodingException e) {
            System.out.println("Rejected: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Lưu `StrictUtf8Example.java` rồi chạy:

```bash
javac StrictUtf8Example.java
java StrictUtf8Example
```

Stdout:

```text
Rejected: MalformedInputException
```

`CharsetDecoder` mới tạo mặc định đã dùng `REPORT`, nhưng chỉ định cả hai nhóm lỗi làm rõ mục đích và giúp người đọc thấy điểm khác với constructor `String`. `MalformedInputException` và `UnmappableCharacterException` đều là `CharacterCodingException`. Khi dữ liệu lỗi, hãy trả về lỗi phù hợp; không lấy chuỗi đã bị thay ký tự rồi coi là đã xác thực thành công.

Đừng nhầm `new String(bytes, charset)` với `charset.newDecoder().decode(ByteBuffer.wrap(bytes))`: chúng khác nhau về **chính sách xử lý lỗi đầu vào**. `Charset.decode(ByteBuffer)` tiện ích trên `Charset` cũng dùng chế độ thay thế nên không thay cho strict decoder.

## 9. Một số byte không phải nội dung văn bản

Mảng byte trả về từ file hoặc API **có thể là dữ liệu nhị phân**, ví dụ ảnh JPEG/PNG, dữ liệu ZIP/GZIP, định dạng protobuf hoặc bản tin mã hóa. Đừng trực tiếp gọi `new String(binaryBytes, UTF_8)` rồi cố gắng dùng kết quả như một bản sao dữ liệu ban đầu:

- Không phải mọi chuỗi byte là UTF-8 hợp lệ.
- Khi giải mã lỗi bằng chế độ thay thế, nhiều chuỗi byte khác nhau có thể bị biến thành cùng ký tự `U+FFFD`.
- Mã hóa ngược `text.getBytes(UTF_8)` lúc đó **không thể bảo đảm thu lại byte ban đầu**.
- Dữ liệu nhị phân có byte `0`, byte điều khiển và các cấu trúc không tương ứng văn bản.

Nếu yêu cầu là **biểu diễn nhị phân an toàn bằng các ký tự ASCII để truyền/lưu dưới dạng text**, hãy dùng Base64 hoặc hex thay vì coi byte ngẫu nhiên là văn bản.

Ví dụ:

```java
byte[] binary = { 0x00, (byte) 0xFF, 0x10 };
String encoded = Base64.getEncoder().encodeToString(binary);
byte[] recovered = Base64.getDecoder().decode(encoded);
boolean exact = Arrays.equals(binary, recovered); // true
```

Đoạn minh họa cần import `java.util.Base64` và `java.util.Arrays`. Đây là **mã hóa biểu diễn nhị phân (binary-to-text)**, không phải charset UTF-8. Base64 không biến dữ liệu nhị phân thành văn bản Unicode có ý nghĩa và cũng không mã hóa bảo mật/encryption.

## 10. UTF-8 BOM và dữ liệu có tiền tố

Một số nguồn UTF-8 đặt **BOM** `EF BB BF` ở đầu nội dung. Giải mã các byte này bằng UTF-8 có thể tạo ký tự `U+FEFF` ở đầu chuỗi. Vì vậy nếu dự kiến chuỗi bắt đầu bằng `"ABC"` nhưng thực tế là `"\uFEFFABC"`, phép `startsWith("ABC")` thất bại.

Không nên tự xóa ba byte đầu mọi mảng dữ liệu:

- Không phải mọi tài liệu UTF-8 đều có BOM.
- Ba byte đầu có thể thuộc nội dung thật trong một protocol khác.
- Những charset khác như UTF-16 có quy tắc BOM khác.
- BOM được xử lý ra sao có thể phụ thuộc codec/API và định dạng file; phải đọc yêu cầu cụ thể của nguồn.

Chỉ khi đặc tả cho phép BOM và bộ đọc chưa xử lý BOM, có thể xét loại bỏ **ký tự `U+FEFF` đầu chuỗi** sau khi giải mã, với điều kiện ứng dụng không cần giữ nó như nội dung. Việc thay mọi `U+FEFF` trong toàn chuỗi là quá mức.

## 11. Đọc toàn bộ file vào bộ nhớ khi kích thước phù hợp

Nếu tệp văn bản đã có kích thước nhỏ, mã rõ ràng:

```java
Path path = Path.of("message.txt");
byte[] rawBytes = Files.readAllBytes(path);
String text = new String(rawBytes, StandardCharsets.UTF_8);
```

Đây là mảnh mã; cần import `java.nio.file.Files`, `java.nio.file.Path` và `java.nio.charset.StandardCharsets`, đồng thời xử lý `IOException`. `Files.readAllBytes` trả toàn bộ nội dung, sau đó `new String` giải mã. Nó đơn giản nhưng cần giữ **cả mảng byte và String** trong bộ nhớ, và sẽ dùng chính sách thay thế cho byte không hợp lệ.

Nếu file rất lớn, đường truyền liên tục, hay cần xử lý từng dòng, ưu tiên:

```java
try (BufferedReader reader =
        Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý một dòng rồi chuyển tiếp.
    }
}
```

Đoạn minh họa cần import `java.io.BufferedReader` và các lớp NIO nói trên, xử lý `IOException`. API reader giải mã trong khi đọc, tránh buộc ứng dụng tải toàn bộ file thành `byte[]`. Nếu cần kiểm soát nghiêm ngặt lỗi byte, hãy dùng `InputStreamReader` với `CharsetDecoder` cấu hình `REPORT`.

### ByteArrayOutputStream và String cho payload nhỏ

Khi ghi text ra `ByteArrayOutputStream`, có hai chiều:

```text
String --OutputStreamWriter(UTF-8)--> ByteArrayOutputStream
                                          |
                                      toByteArray()
                                          |
                                          v
                                      byte[] UTF-8
                                          |
                                      new String(bytes, UTF-8)
                                          |
                                          v
                                        String
```

Đây là cấu trúc hữu ích trong unit test, ví dụ khi cần xác minh kết quả text mà một API tạo ra từ `OutputStream`. Nếu chỉ cần so sánh byte chính xác (giao thức, chữ ký), hãy so sánh `byte[]` trước khi giải mã; nếu muốn so sánh nội dung text, giải mã đúng charset rồi so sánh `String`.

## 12. Hiệu năng, sao chép và tính bất biến

- Constructor `new String(bytes, charset)` tạo ra đối tượng `String` từ dữ liệu đã giải mã. Không nên giả định rằng nó là một “view” trực tiếp lên `byte[]`: thay mảng byte sau đó không làm `String` thay đổi.
- Việc giải mã có chi phí CPU và cấp phát bộ nhớ; không nên decode đi decode lại cùng một byte[] trong vòng lặp nóng chỉ để kiểm tra một điều kiện có thể xử lý tốt hơn ở tầng byte.
- `String.length()` trả số **đơn vị UTF-16**, không phải byte input. `"é"` có một đơn vị `char` nhưng hai byte UTF-8; emoji ngoài BMP có hai `char` và bốn byte UTF-8.
- `bytes.length` là độ dài byte sau khi mã hóa hoặc lấy từ nguồn; nếu cần độ dài chính xác của một bản tin trong UTF-8, hãy tính trên **dữ liệu đã mã hóa**, không dùng `text.length()`.
- Không dùng `new String(bytes, charset)` như một phép xác minh round-trip nếu nguồn có thể chứa chuỗi byte không hợp lệ và việc mất byte là không chấp nhận được: dùng strict decoder trước.
- Để xử lý các đoạn byte đọc theo chunk, đừng decode độc lập mỗi chunk bằng constructor `String` nếu có khả năng cắt đôi ký tự đa byte. Dùng decoder tăng dần hoặc `InputStreamReader` để giữ trạng thái qua ranh giới chunk.

Ví dụ lỗi chia khối:

```text
Byte UTF-8 của "é": C3 A9
Chunk #1: C3
Chunk #2: A9

new String(chunk1, UTF_8) + new String(chunk2, UTF_8)
→ không bằng "é" (hai chunk đều thiếu dữ liệu phù hợp).

Giải mã cả C3 A9 một lần hoặc giữ trạng thái decoder giữa chunk
→ "é".
```

## 13. Những lỗi phổ biến và cách sửa

| Sai lầm | Vấn đề | Cách đúng |
| --- | --- | --- |
| `new String(bytes)` cho dữ liệu trao đổi | Phụ thuộc charset mặc định | `new String(bytes, StandardCharsets.UTF_8)` hoặc charset thực theo hợp đồng |
| Giải mã UTF-8 bytes bằng ISO-8859-1 | Mojibake, sai dấu mà có thể không ném exception | Xác nhận và dùng charset ban đầu |
| Coi `byte[]` bất kỳ là text | Dữ liệu nhị phân bị mất khi đi qua String | Giữ byte nguyên vẹn; Base64/hex nếu cần biểu diễn text |
| Dùng `Arrays.toString(bytes)` để lấy văn bản | Ra biểu diễn số byte | `new String(bytes, charset)` |
| Dùng `String.length()` làm kích thước payload | Sai số byte, có thể cắt giữa ký tự | Tính độ dài byte sau mã hóa |
| Truyền `length` như end-index | Giải mã sai phạm vi hoặc vượt mảng | `length` là số byte tính từ `offset` |
| Cắt giữa các byte UTF-8 rồi giải mã độc lập | Sinh `U+FFFD` hoặc lỗi | Giữ ranh giới ký tự/decoder tăng dần |
| Dùng constructor String để kiểm tra UTF-8 hợp lệ | Malformed byte có thể bị thay | `CharsetDecoder` với `REPORT` |
| Giả định lỗi dấu sẽ luôn có exception | Nhiều charset tạo chuỗi hợp lệ nhưng sai nghĩa | Dựa trên metadata/protocol và kiểm tra hex |
| Gọi `toString()` của `byte[]` | Kết quả tên kiểu và địa chỉ/hash biểu diễn, không phải nội dung | `new String(bytes, charset)` |
| Quên đóng stream/reader tệp | Rò tài nguyên | `try-with-resources` |
| Đọc file lớn bằng `readAllBytes` rồi decode | Tăng áp lực bộ nhớ | `BufferedReader`/`InputStreamReader` đọc dần |
| Xóa BOM bất kể nguồn | Làm hỏng dữ liệu hoặc mất nội dung | Áp dụng chính sách BOM theo định dạng thực |

## 14. Câu hỏi kiểm tra

1. **Mảng `byte[]` có charset riêng không?** Không. Nó chỉ chứa byte; charset do định dạng hoặc thỏa thuận bên ngoài xác định.
2. **Cách chuyển byte UTF-8 sang String ổn định?** `new String(bytes, StandardCharsets.UTF_8)`.
3. **Vì sao `C3 A9` có thể thành `"é"` hoặc `"Ã©"`?** Vì UTF-8 và ISO-8859-1 diễn giải cùng byte theo quy tắc khác nhau.
4. **`new String(bytes, 2, 5, UTF_8)` lấy các chỉ số nào?** Năm byte ở chỉ số 2, 3, 4, 5, 6; số 5 là chiều dài.
5. **Điều gì xảy ra nếu mảng chỉ có byte đầu `C3` của chữ `é`?** Giải mã UTF-8 bằng constructor String sẽ thay đoạn byte lỗi/bị cắt, không tạo chữ `é`.
6. **Làm sao từ chối byte UTF-8 sai?** Dùng `UTF_8.newDecoder()` và `CodingErrorAction.REPORT`, bắt `CharacterCodingException`.
7. **`ByteArrayOutputStream.toByteArray()` trả gì?** Bản sao các byte đã ghi trong buffer.
8. **`ByteArrayOutputStream.toString(Charset)` có ý nghĩa gì?** Giải mã nội dung byte của bộ đệm theo charset chỉ định.
9. **Có thể gọi `new String(binary, UTF_8)` để giữ byte ZIP/ảnh nguyên vẹn không?** Không; byte không đại diện văn bản có thể mất thông tin. Giữ byte hoặc dùng Base64 khi cần văn bản biểu diễn.
10. **Vì sao không decode từng chunk mạng bằng `new String(chunk, UTF_8)`?** Chunk có thể chia đôi dãy byte của một ký tự Unicode.
11. **`String.length()` có bằng `bytes.length` không?** Không nhất thiết; mã hóa có thể dùng nhiều byte cho một code point.
12. **Giải mã sai charset có luôn ném exception?** Không. Kết quả có thể chỉ sai nghĩa nhưng vẫn là chuỗi Unicode hợp lệ.

## Tham khảo API Java 17

- [`String` constructors — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html).
- [`StandardCharsets` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html).
- [`Charset` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/Charset.html).
- [`CharsetDecoder` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html).
- [`ByteArrayOutputStream` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ByteArrayOutputStream.html).
- [`Files` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html).
- [`InputStreamReader` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html).

**Ghi nhớ:** `byte[]` chỉ là byte thô; ý nghĩa văn bản xuất hiện khi chọn đúng charset. Dùng `new String(bytes, charset)` cho dữ liệu văn bản hợp lệ, `new String(bytes, offset, length, charset)` cho đúng phần byte và `CharsetDecoder` với `REPORT` khi dữ liệu sai cần bị từ chối thay vì âm thầm thay ký tự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="in-memory-io-005">SequenceInputStream nối nhiều nguồn byte</a>

<details>
<summary>Xem chi tiết</summary>

# SequenceInputStream: nối nhiều nguồn byte trong Java I/O

## 1. Mục tiêu và phạm vi

`java.io.SequenceInputStream` biểu diễn **một luồng byte đầu vào được tạo bằng cách nối tuần tự nhiều `InputStream`**. Khi đọc đến hết nguồn thứ nhất, nó chuyển sang nguồn kế tiếp; chương trình tiêu thụ bên ngoài chỉ cần làm việc với **một** `InputStream`.

Chủ đề này thuộc nhóm **I/O trong bộ nhớ** của Java Core. Các ví dụ chính sử dụng `ByteArrayInputStream` để thấy rõ cơ chế nối dữ liệu; cùng nguyên lý có thể dùng với file streams và các nguồn byte khác khi quản lý tài nguyên cẩn thận.

Sau bài học, bạn có thể:

- Phân biệt **ghép tuần tự (concatenation)** với xen kẽ, trộn hoặc chạy nhiều luồng song song.
- Dùng hai constructor: `SequenceInputStream(InputStream, InputStream)` và `SequenceInputStream(Enumeration<? extends InputStream>)`.
- Giải thích khi nào `read()` trả EOF, khi nào chuyển nguồn, tác động của nguồn rỗng và cách `close()` xử lý các stream thành phần.
- Nối dữ liệu nhị phân và byte UTF-8 mà không tự chèn dấu phân cách hoặc làm hỏng chuỗi nhiều byte.
- Chọn cách đọc/ghi có bộ nhớ giới hạn cho dữ liệu lớn, không lạm dụng `readAllBytes()` hoặc `ByteArrayOutputStream` khi kích thước nguồn không biết trước.
- Nhận diện các trường hợp không nên dùng `SequenceInputStream` và cách xử lý lỗi I/O, vòng đời tài nguyên, nguồn đồng thời.

**Ý chính:** `SequenceInputStream` là một **InputStream chỉ đọc** có thứ tự, đưa ra cùng dãy byte như thể đặt byte của từng nguồn sát nhau. Nó không nhận biết định dạng file, ký tự, bản ghi, delimiter hoặc giao thức.

## 2. Ghép tuần tự diễn ra thế nào?

Giả sử ba nguồn:

```text
InputStream A: [10, 20]
InputStream B: []          (nguồn rỗng)
InputStream C: [30, 40]

SequenceInputStream:
   A đến EOF  -> đóng A, chuyển sang B
   B đến EOF  -> đóng B, chuyển sang C
   C đến EOF  -> đóng C, hết tất cả nguồn

Kết quả đọc: [10, 20, 30, 40]
```

Việc chuyển nguồn diễn ra khi đọc đến **EOF của nguồn hiện tại**, không phải khi một lần gọi `read(byte[])` đọc được ít byte hơn số đã yêu cầu. Trong Java, `InputStream.read()` trả một giá trị từ 0 đến 255 khi đọc được byte; chỉ `-1` biểu thị đã hết dữ liệu. Với `read(byte[], off, len)`, số trả về là số byte thực nhận (hoặc `-1` khi EOF và độ dài yêu cầu khác 0). Không tự cho rằng một lần `read(byte[])` luôn đầy mảng.

### 2.1. Các đặc tính cần ghi nhớ

| Đặc tính | `SequenceInputStream` |
| --- | --- |
| Loại dữ liệu | **Byte**; mỗi byte giữ nguyên giá trị |
| Thứ tự | Đọc hết nguồn 1 rồi tới nguồn 2, 3, ... |
| Xen kẽ dữ liệu | Không; không có round-robin hay merge theo thời gian |
| Tự chèn separator | Không; không thêm khoảng trắng, newline, header hay metadata |
| Tự mã hóa/giải mã | Không; muốn văn bản thì gắn reader/decoder ở đúng chỗ |
| Tự tìm độ dài tổng | Không; luồng chung chỉ biết EOF khi đã đi hết các nguồn |
| Trạng thái đọc | Tiến về trước; không hỗ trợ quay lại một cách tổng quát |
| Đồng thời | Không đọc các nguồn song song để tăng tốc |
| Đóng nguồn | Quản lý các input stream thành phần khi đã đọc hết hoặc khi đóng stream ghép |
| Tình huống nguồn block | Có thể chờ nguồn hiện tại; không tự timeout |
| Loại API | Kế thừa `InputStream`, dùng được với các API nhận `InputStream` |

Điểm cần hiểu về **dữ liệu rỗng**: nếu một nguồn không có byte nào, nó được bỏ qua khi phát hiện EOF; nguồn kế tiếp vẫn được đọc bình thường. Nếu không còn nguồn nào, `SequenceInputStream.read()` trả `-1`.

### 2.2. Dữ liệu kết hợp có phải một file hợp lệ không?

**Không nhất thiết.** Kết quả của phép nối byte là một dãy byte đúng theo thứ tự, nhưng định dạng có thể không còn hợp lệ nếu từng mảnh là các file độc lập:

- Nối hai file văn bản UTF-8 có thể tạo văn bản đọc được nếu cấu trúc hợp lý, nhưng **không tự tạo newline** giữa hai file.
- Nối các byte *phân mảnh từ cùng một file nhị phân* có thể tái tạo file gốc chính xác.
- Nối hai file ZIP, PNG, PDF hoặc dữ liệu nén độc lập không tự tạo một “file hợp nhất” đúng định dạng; điều này phụ thuộc cấu trúc định dạng và yêu cầu của chương trình đọc.
- Với giao thức có header, checksum, độ dài, frame hoặc boundary, chỉ nối byte có thể làm mất ranh giới; cần quy tắc đóng gói/giải mã riêng.

## 3. Hai constructor và quyền sở hữu nguồn

### 3.1. Nối đúng hai `InputStream`

```java
InputStream joined = new SequenceInputStream(first, second);
```

Ở đây `first` và `second` là hai `InputStream` đã được tạo. Khi đọc, thứ tự luôn là **first rồi second**. Hai stream thành phần có thể là `ByteArrayInputStream`, `FileInputStream`, stream từ `Files.newInputStream(...)` hoặc nguồn byte khác.

Một cách dùng quen thuộc:

```java
try (InputStream joined =
        new SequenceInputStream(first, second)) {
    // Đọc từ joined cho đến EOF.
}
```

**Đây là đoạn minh họa:** `first` và `second` cần được tạo trước đó, và trách nhiệm xử lý lỗi khi tạo các nguồn phải được xác định tại nơi mở nguồn.

`SequenceInputStream` sở hữu các nguồn mà nó nhận theo hợp đồng đóng stream: khi một nguồn cạn, nó đóng nguồn vừa đọc xong rồi chuyển sang nguồn tiếp theo; gọi `close()` trên stream ghép sẽ đóng nguồn hiện tại và những nguồn còn lại trong chuỗi. Vì vậy, khi đã chuyển quyền quản lý vào stream ghép, nên coi stream ghép là tài nguyên phải được đóng. Tránh dùng lại nguồn cũ sau khi đã giao cho nó.

### 3.2. Nối nhiều nguồn bằng `Enumeration`

Constructor thứ hai:

```java
SequenceInputStream joined =
        new SequenceInputStream(enumerationOfInputStreams);
```

`enumerationOfInputStreams` có kiểu `Enumeration<? extends InputStream>`. Mỗi nguồn được đưa vào theo thứ tự `Enumeration.nextElement()` trả về. Nếu có 3, 10 hay 100 nguồn, chương trình tiêu thụ bên ngoài vẫn dùng một InputStream.

Java có thể chuyển `List` sang `Enumeration` bằng `Collections.enumeration(list)`:

```java
// Minh họa; first, second, third là các InputStream đã được tạo.
java.util.List<java.io.InputStream> sources =
        java.util.List.of(first, second, third);
java.util.Enumeration<java.io.InputStream> inputs =
        java.util.Collections.enumeration(sources);

try (java.io.SequenceInputStream joined =
        new java.io.SequenceInputStream(inputs)) {
    // Đọc toàn bộ các nguồn theo đúng thứ tự danh sách.
}
```

Các mảnh được nối **theo thứ tự cung cấp**; nếu thứ tự có ý nghĩa, phải xác lập thứ tự ở đầu vào. Đừng tùy tiện duyệt một collection không cam kết thứ tự rồi mặc nhiên cho rằng thứ tự là chính xác.

**Quan trọng với nhiều tệp:** nếu tự mở hàng nghìn stream trước khi tạo `Enumeration`, bạn có thể giữ hàng nghìn file descriptor cùng lúc và phải xử lý lỗi xảy ra ở giữa giai đoạn khởi tạo. `SequenceInputStream` chỉ giúp đọc tuần tự; nó không tự giải quyết việc mở hàng loạt tệp hiệu quả. Xem cách thay thế ở mục 8.

## 4. Ví dụ Java 17+: nối hai mảnh byte chứa UTF-8 nhiều byte

Đây là tình huống quan trọng: chuỗi Unicode `A😀B` được mã hóa thành UTF-8 có **6 byte** (1 byte cho `A`, 4 byte cho emoji và 1 byte cho `B`). Ta cố tình cắt byte ở **giữa** emoji rồi nối lại bằng `SequenceInputStream`. Cách đúng là nối byte trước, sau đó mới giải mã văn bản.

Lưu chương trình sau vào **`SequenceInputStreamDemo.java`** với mã nguồn UTF-8:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SequenceInputStreamDemo {
    public static void main(String[] args) throws IOException {
        byte[] original = "A😀B".getBytes(StandardCharsets.UTF_8);

        // Vị trí 3 nằm giữa dãy byte mã hóa emoji.
        byte[] part1 = Arrays.copyOfRange(original, 0, 3);
        byte[] part2 = Arrays.copyOfRange(
                original, 3, original.length);

        try (InputStream joined = new SequenceInputStream(
                new ByteArrayInputStream(part1),
                new ByteArrayInputStream(part2))) {

            byte[] result = joined.readAllBytes();
            System.out.println("Bytes: " + result.length);
            System.out.println("Match: " + Arrays.equals(original, result));
            System.out.println("Text: "
                    + new String(result, StandardCharsets.UTF_8));
        }
    }
}
```

Lệnh biên dịch/chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 SequenceInputStreamDemo.java
java SequenceInputStreamDemo
```

**Stdout chính xác:**

```text
Bytes: 6
Match: true
Text: A😀B
```

**Giải thích:**

1. `getBytes(StandardCharsets.UTF_8)` sinh 6 byte. `part1` nhận ba byte đầu, `part2` nhận ba byte cuối.
2. `ByteArrayInputStream` đọc trực tiếp từ byte array có sẵn trong bộ nhớ; hai phần này là nguồn byte riêng.
3. `SequenceInputStream` trả byte của `part1` rồi nối tiếp byte của `part2`, không bỏ byte nào và không chèn ký tự giữa chúng.
4. `Arrays.equals` trả `true` vì thứ tự byte khôi phục đúng nguyên bản.
5. Chỉ khi dữ liệu đã được nối, `new String(result, UTF_8)` mới giải mã dãy UTF-8 hoàn chỉnh thành `A😀B`.

Nếu thay vào đó giải mã từng mảnh UTF-8 riêng lẻ rồi nối hai `String`, việc cắt giữa một chuỗi byte của code point có thể gây replacement character hoặc lỗi giải mã tùy decoder. Với dữ liệu văn bản bị phân mảnh theo byte, **hãy decode trên luồng byte ghép** hoặc sau khi đã khôi phục dãy byte đúng.

**Giới hạn ví dụ:** `readAllBytes()` giữ toàn bộ kết quả trong RAM, phù hợp với ví dụ rất nhỏ này. Đối với các nguồn lớn, dùng đọc theo khối hoặc `transferTo` đến đích không tích lũy toàn bộ nội dung.

## 5. Ví dụ Java 17+: nối nhiều nguồn và truyền byte không cần vòng lặp

Ví dụ này dùng `Enumeration` của ba nguồn byte; nguồn giữa rỗng để kiểm tra chuyển nguồn và tránh tự chèn delimiter. Đích là `ByteArrayOutputStream` nhằm quan sát kết quả ngay tại bộ nhớ.

Lưu vào **`MultiSourceSequenceDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class MultiSourceSequenceDemo {
    public static void main(String[] args) throws IOException {
        List<InputStream> parts = List.of(
                new ByteArrayInputStream(new byte[] {1, 2}),
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayInputStream(new byte[] {3, 4, 5})
        );
        Enumeration<InputStream> sources =
                Collections.enumeration(parts);

        try (InputStream joined = new SequenceInputStream(sources);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            long transferred = joined.transferTo(output);

            System.out.println("Transferred: " + transferred);
            System.out.println("Result: "
                    + Arrays.toString(output.toByteArray()));
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 MultiSourceSequenceDemo.java
java MultiSourceSequenceDemo
```

**Stdout:**

```text
Transferred: 5
Result: [1, 2, 3, 4, 5]
```

Ở đây `transferTo` (có từ Java 9 trên `InputStream`) chuyển byte đến `OutputStream` và trả về **tổng số byte đã truyền**. Nguồn rỗng không tạo giá trị và không làm ngừng việc đọc các nguồn sau. Kết quả được in bằng `Arrays.toString` nên bạn có thể kiểm tra cả thứ tự và số phần tử.

**Điều cần phân biệt:** `ByteArrayOutputStream` tích lũy toàn bộ byte trong RAM, có thể dùng trong bài học và dữ liệu nhỏ đã giới hạn. Nếu nguồn lớn hoặc đến từ mạng, thay đích bằng file stream, socket stream hoặc đầu ra có khả năng xử lý tuần tự và áp dụng quota khi cần. `transferTo` không tự giới hạn số byte của nguồn.

## 6. Quản lý EOF, `close()` và exception

### 6.1. Khi nào một nguồn được đóng?

`SequenceInputStream` đọc hết một nguồn thì chuyển sang nguồn khác, đồng thời đóng nguồn đã cạn. Khi đóng stream ghép, các stream được quản lý còn lại cũng được đóng theo hợp đồng lớp. Điều này khác với việc tự tạo và giữ một danh sách `InputStream` độc lập rồi quên đóng từng stream.

Cần lưu ý hai nhóm tình huống:

- **Đọc đến hết:** qua từng EOF, các nguồn đã dùng được đóng và nguồn tiếp theo được chọn. Khi không còn nguồn, stream ghép trả EOF.
- **Dừng sớm/ngoại lệ:** việc bỏ dở giữa chừng mà **không đóng** stream ghép có thể để lại file handle/network stream. Bọc stream ghép trong `try-with-resources`, kể cả khi định đọc một phần dữ liệu.

Các `ByteArrayInputStream` trong ví dụ giữ byte trong bộ nhớ và thao tác `close()` của chúng không có tác dụng giải phóng file handle. Với `FileInputStream` hoặc stream mạng, việc đóng đúng tài nguyên đặc biệt quan trọng.

### 6.2. Trách nhiệm khi tạo nguồn bị lỗi giữa chừng

Giả sử muốn nối hai file bằng:

```java
// Minh họa ý tưởng, có nguy cơ rò nguồn nếu mở file thứ hai thất bại.
InputStream first = Files.newInputStream(path1);
InputStream second = Files.newInputStream(path2);
InputStream joined = new SequenceInputStream(first, second);
```

Đây **không phải một mẫu hoàn chỉnh an toàn**: nếu việc mở `path2` ném `IOException`, `first` đã mở nhưng chưa được giao cho stream ghép; mã cần cơ chế đóng `first` khi bước thứ hai thất bại. Với tệp thật, nên dùng `try-with-resources` theo từng nguồn ngay lúc mở, hoặc hàm khởi tạo có cleanup khi lỗi; xác định rõ trách nhiệm đóng để tránh bỏ quên nguồn.

Một cách **đơn giản, chắc chắn** khi chỉ cần ghép nhiều file (không bắt buộc phải có một InputStream chung) là xử lý từng file trong một vòng lặp và đóng từng nguồn ngay sau khi chuyển xong, xem mục 8. Với số lượng nguồn lớn, cách đó còn tránh mở quá nhiều file đồng thời.

### 6.3. Hành vi khi một nguồn đọc bị lỗi

Nếu `read()` của một stream thành phần ném `IOException`, phép đọc từ `SequenceInputStream` có thể thất bại. Không mặc định nó sẽ “bỏ qua nguồn lỗi” như nguồn rỗng rồi tự tiếp tục ở nguồn kế tiếp. Với dữ liệu yêu cầu tính toàn vẹn, phải xử lý lỗi như một kết quả thất bại: tránh công bố file ghép thiếu phần hoặc tự nhận là thành công.

Nếu writer đích ném lỗi khi dùng `transferTo`, đích cũng có thể đã có **một phần byte**. `SequenceInputStream` không cung cấp transaction, rollback, checksum hay atomic replacement. Khi cần bảo vệ file đích, dùng tệp tạm, xác minh số byte/checksum/định dạng, rồi áp dụng chính sách công bố thích hợp.

### 6.4. Không dựa vào `available()` để biết tổng số byte

`InputStream.available()` nói về ước lượng số byte có thể đọc không phải chờ từ stream tại thời điểm đó; nó **không phải phép đo tổng dung lượng của tất cả nguồn** và không phải tín hiệu EOF. Giá trị 0 không chứng minh đã hết input. Với `SequenceInputStream`, chỉ nên dừng vòng đọc khi `read()`/`read(byte[])` báo EOF theo hợp đồng.

## 7. Đọc streaming và giới hạn bộ nhớ

`SequenceInputStream` chỉ cung cấp một góc nhìn nối các stream. Nó không bắt buộc ứng dụng phải giữ tất cả byte. Tuy nhiên, bộ nhớ dùng bao nhiêu còn phụ thuộc **nguồn** và **đích**:

| Pipeline | Đặc điểm bộ nhớ |
| --- | --- |
| Hai `ByteArrayInputStream` trên `byte[]` đã tồn tại | Byte đã nằm trong RAM trước khi ghép; `SequenceInputStream` không tự ghép thành một mảng lớn |
| `SequenceInputStream` → `readAllBytes()` | Tạo kết quả trong RAM theo toàn bộ dữ liệu đọc; không phù hợp với input không giới hạn |
| `SequenceInputStream` → `ByteArrayOutputStream` | Đầu ra tích lũy toàn bộ dữ liệu trong RAM |
| `SequenceInputStream` → `Files.newOutputStream(...)` qua `transferTo` | Có thể truyền từng khối, không cần giữ mọi byte trong RAM |
| Nhiều file mở từ trước trong một `List<InputStream>` | Có thể giữ nhiều file descriptor; độ lớn của từng file không quyết định số descriptor |
| Nhiều stream từ mạng | Có thể block khi đọc, cần quota/timeouts/cancellation của giao thức bên ngoài |

### 7.1. Khi nào dùng `transferTo`?

Với Java 17+, nếu chỉ cần **chuyển hết byte** đến một `OutputStream`, có thể dùng:

```java
// Minh họa, input đã là một SequenceInputStream đang mở;
// output là OutputStream đang mở.
long copied = input.transferTo(output);
```

Phương thức này chuyển byte theo từng khối và trả tổng byte đã gửi, song vẫn đọc **đến EOF**, không tự áp dụng quota, thời gian chờ hay chính sách đích. Nếu bài toán có giới hạn kích thước, phải tự đo trước/trong khi chuyển theo cách không vượt quá ngưỡng; kiểm tra `copied` sau khi chuyển không ngăn việc đích đã nhận quá nhiều byte.

### 7.2. Đọc theo khối khi phải xử lý dữ liệu

Nếu muốn tính checksum hoặc xử lý chunk có kiểm soát, đọc từng khối là lựa chọn rõ ràng:

```java
// Minh họa: input và output phải được quản lý bằng try-with-resources.
byte[] buffer = new byte[16 * 1024];
int count;
while ((count = input.read(buffer)) != -1) {
    if (count > 0) {
        output.write(buffer, 0, count);
    }
}
```

Giá trị `count` có thể nhỏ hơn kích thước mảng, đặc biệt khi gần cuối một nguồn hoặc cuối chuỗi. Phải truyền **đúng `count` byte** sang đích, không luôn ghi `buffer.length` (vì phần còn lại có thể chứa byte cũ từ lượt trước).

**Đừng coi một lần đọc ngắn là hết một stream thành phần.** `SequenceInputStream` chịu trách nhiệm tìm EOF thật và chuyển nguồn. Ở vòng xử lý bên ngoài, điều kiện kết thúc là `-1`.

### 7.3. Giới hạn nguồn lớn hoặc không đáng tin

Nếu các nguồn đến từ file do người dùng tải lên, mạng hoặc kho lưu trữ không kiểm soát, hãy đặt:

- **Quota tổng số byte** sau khi nối và, nếu cần, quota từng nguồn.
- **Timeout/deadline/cancellation** cho nguồn có thể chờ hoặc không phát EOF.
- **Giới hạn số nguồn** trong Enumeration/List để tránh quá nhiều handle.
- **Kiểm tra thứ tự và danh tính phần** (chẳng hạn chunk index, version, size, checksum); `SequenceInputStream` chỉ tôn trọng thứ tự được giao, không xác minh các mảnh đúng.
- **Xử lý khi một phần thiếu hoặc bị lỗi:** không công bố kết quả nối như file hoàn chỉnh.
- **Quản lý đầu ra tạm:** nếu ghi file quan trọng, lưu tạm và xác minh toàn vẹn trước khi thay thế kết quả cũ.

## 8. Những lựa chọn thay thế

`SequenceInputStream` phù hợp khi **một API cần đúng một `InputStream`** đại diện cho nhiều nguồn tuần tự. Không phải bài toán nối dữ liệu nào cũng cần đến lớp này.

### 8.1. Nối nhiều file với số lượng lớn

Nếu mục tiêu là ghép nhiều tệp lần lượt vào một tệp đích, vòng lặp mở **một file tại một thời điểm** thường dễ kiểm soát file handle hơn. Đoạn minh họa dưới đây có biến `paths` là một `List<Path>` hợp lệ và `outputPath` là `Path` đích; mã gọi phải kiểm tra không đưa file đích vào danh sách nguồn:

```java
// Đoạn minh họa (paths và outputPath cần được cung cấp).
try (java.io.OutputStream target =
        java.nio.file.Files.newOutputStream(outputPath)) {
    for (java.nio.file.Path path : paths) {
        try (java.io.InputStream source =
                java.nio.file.Files.newInputStream(path)) {
            source.transferTo(target);
        }
    }
}
```

Ưu điểm: chỉ mở một stream nguồn mỗi lượt, đóng ngay sau khi xong; xử lý lỗi được gắn với tên từng file dễ dàng. Nhược điểm: nếu nơi tiêu thụ yêu cầu **một InputStream** thay vì OutputStream đích do ta kiểm soát, `SequenceInputStream` thuận tiện hơn.

Chú ý: đây vẫn là phép **nối byte**, không tự tạo một ZIP/PDF/PNG hợp lệ từ các file độc lập. Nếu đích đã tồn tại, `Files.newOutputStream(outputPath)` thông thường ghi đè nội dung theo các tùy chọn mở mặc định; cân nhắc tệp tạm nếu cần bảo toàn bản cũ khi phát sinh lỗi.

### 8.2. Ghép dữ liệu đã nằm trong bộ nhớ

Nếu tổng dữ liệu **nhỏ và có giới hạn**, có thể dùng `ByteArrayOutputStream` cùng `write(byte[])` để tạo mảng kết quả:

```java
// Minh họa với các mảng byte nhỏ có sẵn.
java.io.ByteArrayOutputStream output =
        new java.io.ByteArrayOutputStream();
output.writeBytes(firstBytes);
output.writeBytes(secondBytes);
byte[] joined = output.toByteArray();
```

`writeBytes` có từ Java 11. Với cách này, kết quả được **materialize** thành một `byte[]` mới; dễ lưu trữ/sử dụng lại nhưng tốn bộ nhớ theo tổng số byte. `SequenceInputStream` cung cấp **góc nhìn stream**, thích hợp khi muốn tiêu thụ từng phần mà không xây một mảng ghép trung gian.

### 8.3. Đọc dữ liệu văn bản

Nếu các nguồn là **văn bản đã được mã hóa đúng cùng charset** nhưng bị chia tùy ý theo byte, có thể nối byte bằng `SequenceInputStream` rồi tạo **một** `InputStreamReader` phía ngoài:

```java
// Minh họa; first và second là InputStream nguồn,
// truyền quyền đóng nguồn vào SequenceInputStream.
try (java.io.Reader reader = new java.io.InputStreamReader(
        new java.io.SequenceInputStream(first, second),
        java.nio.charset.StandardCharsets.UTF_8)) {
    char[] characters = new char[1024];
    while (reader.read(characters) != -1) {
        // Xử lý từng khối ký tự (sử dụng số char thực đọc
        // nếu cần chuyển tiếp hoặc lưu trữ dữ liệu).
    }
}
```

Điểm mấu chốt là **decoder duy nhất nằm ngoài luồng byte ghép**, vì một code point UTF-8 có thể có byte ở hai nguồn khác nhau. Để mã đầy đủ, phải tạo `first`/`second` và xử lý lỗi khởi tạo như mục 6. Không chọn cách trên nếu các nguồn thực chất chứa dữ liệu với các charset khác nhau; phải decode từng nguồn theo charset riêng và xác định quy tắc ghép ở mức ký tự.

### 8.4. Khi cần xen kẽ hoặc ghép có cấu trúc

Nếu muốn nhận lần lượt một byte từ nguồn A rồi một byte từ B (round-robin), `SequenceInputStream` không làm điều đó. Nếu muốn merge theo timestamp hoặc khóa sắp xếp, phải dùng thuật toán merge theo dữ liệu. Nếu muốn gắn header, length-prefix, delimiter, checksum hay nén nhiều file thành archive, hãy dùng writer/parser theo định dạng được yêu cầu.

## 9. Các bẫy kỹ thuật quan trọng

1. **Nhầm nối tuần tự với đọc song song:** `SequenceInputStream` xử lý hết nguồn trước rồi mới đến nguồn sau; không tự cải thiện băng thông bằng nhiều thread.
2. **Dùng `readAllBytes()` với nguồn lớn:** tạo mảng kết quả tăng theo dữ liệu; có thể hết heap.
3. **Tin rằng nối hai file độc lập sẽ tạo một file hợp lệ:** định dạng có header/footer, frame hoặc index cần xử lý riêng.
4. **Quên dấu phân cách:** `"hello"` + `"world"` trở thành `"helloworld"`; phải chủ động thêm newline nếu đó là yêu cầu.
5. **Giải mã UTF-8 từng chunk trước khi nối:** có thể cắt giữa byte mã hóa của một code point và làm hỏng văn bản.
6. **Không đóng stream ghép:** các nguồn chưa đọc hết vẫn có thể giữ tài nguyên.
7. **Mở trước quá nhiều file:** dù đọc tuần tự, các nguồn được tạo sớm có thể cùng chiếm file descriptor.
8. **Không xử lý lỗi khi khởi tạo nguồn:** file thứ hai không mở được có thể làm rò file thứ nhất đã mở.
9. **Ghi thiếu/thừa byte do không dùng kết quả `read(byte[])`:** luôn dùng số byte thực nhận.
10. **Dùng `available() == 0` để dừng:** đây không phải EOF, đặc biệt với stream mạng.
11. **Không ràng buộc thứ tự phần:** ghép chunk sai thứ tự tạo nội dung sai dù không có lỗi I/O.
12. **Không xử lý kết quả ghi dở:** nếu đầu ra lỗi, dữ liệu đích có thể đã chứa một phần.
13. **Coi `SequenceInputStream` là stream có thể seek/reset:** đây là luồng tuần tự, không hỗ trợ truy cập ngẫu nhiên như file channel.
14. **Cho rằng ByteArrayInputStream phải giải phóng tài nguyên ngoài heap:** nguồn mảng byte không giữ file descriptor; việc close có hiệu ứng khác với file/socket.
15. **Gọi API chuyển toàn bộ trên nguồn không bao giờ EOF:** có thể block lâu; cần timeout và giới hạn thuộc tầng nguồn/giao thức.

## 10. Bài tập thực hành và câu hỏi tự kiểm tra

### Bài tập 1 — Đúng thứ tự và nguồn rỗng

Nối ba `ByteArrayInputStream` chứa `{5, 6}`, `{}`, `{7}`. Dùng `read()` đến `-1`, in từng byte; kết quả phải theo thứ tự `5`, `6`, `7`. Giải thích vì sao nguồn rỗng không làm toàn bộ chuỗi dừng.

### Bài tập 2 — Nhiều mảnh từ một dữ liệu gốc

Lấy `byte[]` từ chuỗi `"Tiếng Việt 😀"` mã hóa UTF-8, chia thành ba mảnh tại **bất kỳ vị trí byte nào**, dùng `SequenceInputStream` ghép lại. So sánh mảng byte với mảng gốc, rồi giải mã một lần sau khi nối. Nếu chia ngay giữa emoji, vì sao vẫn khôi phục văn bản đúng?

### Bài tập 3 — So sánh bộ nhớ

Dùng tổng nguồn có kích thước đã giới hạn để so sánh:
- `SequenceInputStream` → `readAllBytes()`;
- `SequenceInputStream` → `ByteArrayOutputStream`;
- `SequenceInputStream` → `Files.newOutputStream(...)` thông qua `transferTo`.

Nêu điểm nào giữ kết quả trong heap; điểm nào có thể chuyển từng khối. Không thử với nguồn không giới hạn hoặc file rất lớn nếu chưa có quota tài nguyên.

### Bài tập 4 — Phát hiện lỗi ghép

Mô phỏng ba nguồn, trong đó nguồn thứ hai ném `IOException` giữa chừng. Kiểm tra ngoại lệ được truyền ra, stream ghép được đóng qua `try-with-resources` và kết quả tạm được loại bỏ. Tại sao không được báo thành công chỉ vì đã sao chép được byte của nguồn thứ nhất?

### Bài tập 5 — Lựa chọn API

Giải thích công cụ phù hợp cho từng trường hợp:

- API chỉ nhận một `InputStream` nhưng dữ liệu được chia thành nhiều nguồn byte theo thứ tự.
- Có 10.000 tệp cần ghép vào một tệp đích; không nên mở đồng thời 10.000 file descriptor.
- Có 2 file ZIP độc lập muốn tạo một archive đúng định dạng.
- Cần xen kẽ các frame từ hai nguồn theo timestamp.
- Muốn ghép một vài mảng byte nhỏ thành `byte[]` để xử lý lại.

**Đáp án định hướng:** `SequenceInputStream`; vòng lặp mở/đóng từng nguồn; thư viện/archive writer chuẩn; thuật toán merge theo timestamp; `ByteArrayOutputStream` hoặc sao chép mảng.

## 11. Tóm tắt kiến thức cốt lõi

- `SequenceInputStream` cung cấp **một luồng InputStream** đại diện cho nhiều nguồn byte **nối theo thứ tự**.
- Hai constructor nhận **hai stream** hoặc **Enumeration các stream**; EOF từng nguồn kích hoạt việc chuyển sang nguồn kế, còn EOF cuối cùng kết thúc toàn chuỗi.
- Nó không chèn delimiter, không tạo định dạng ghép, không chạy song song và không xử lý byte theo Unicode/charset.
- Khi ghép byte văn bản bị cắt giữa ký tự đa byte, **nối byte trước, decode sau** bằng đúng charset.
- `try-with-resources` bảo đảm đóng stream ghép; với tài nguyên thực, cần quản lý cả lỗi khởi tạo trước khi trao quyền sở hữu.
- Với nguồn lớn, truyền theo khối đến đích không tích lũy dữ liệu; tránh `readAllBytes()` và `ByteArrayOutputStream` nếu toàn bộ kết quả có thể vượt bộ nhớ.
- Nếu chỉ cần ghi nhiều file theo thứ tự vào một file, vòng lặp mở và đóng **từng file** có thể đơn giản và tiết kiệm file descriptor hơn.

Một lựa chọn tốt là lựa chọn giải quyết đúng nhu cầu **nối byte tuần tự**, **vòng đời tài nguyên**, **bộ nhớ** và **tính đúng của định dạng**, thay vì dùng `SequenceInputStream` cho mọi hình thức “ghép dữ liệu”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="in-memory-io-006">PushbackInputStream và PushbackReader</a>

<details>
<summary>Xem chi tiết</summary>

# `PushbackInputStream` và `PushbackReader`

## Mục tiêu bài học

Khi đọc dữ liệu tuần tự, đôi lúc chương trình phải **xem trước một vài byte hoặc ký tự** để quyết định nhánh xử lý. Nếu đó là byte/ký tự thuộc về dữ liệu của bước tiếp theo, chương trình cần “trả lại” cho lần đọc sau. `java.io.PushbackInputStream` và `java.io.PushbackReader` cung cấp một **bộ đệm đẩy lại nhỏ** (*pushback buffer*) cho nhu cầu này.

Sau bài này, bạn có thể:

- Phân biệt pushback ở mức **byte** và mức **ký tự**.
- Sử dụng `read()`/`unread()` để nhìn trước dữ liệu (*lookahead*) mà vẫn giữ đúng thứ tự đọc.
- Hiểu **dung lượng cố định**, cơ chế đọc lại theo kiểu stack (LIFO đối với từng lần `unread`), hành vi tại EOF và ngoại lệ khi pushback buffer đầy.
- Áp dụng `PushbackReader` cho việc phân tích token/dấu phân cách và `PushbackInputStream` cho việc kiểm tra byte mở đầu, chẳng hạn UTF-8 BOM, trên một luồng trong bộ nhớ.
- So sánh pushback với `mark()`/`reset()`, `BufferedInputStream`, `BufferedReader`, `ByteArrayInputStream` và tự giữ một ký tự lookahead trong parser.
- Xác định điểm giới hạn: đây là **đọc tuần tự có khả năng trả lại một lượng nhỏ dữ liệu**, không phải cơ chế tua lại tệp tùy ý.

## 1. Vị trí trong Java I/O

Hai lớp nằm trong `java.io` và là **lớp bao (decorator)** cho nguồn đọc khác:

```text
InputStream nguồn byte
    │
    ▼
PushbackInputStream ──► read byte ──► xử lý
    ▲                       │
    └──────── unread(byte) ──┘

Reader nguồn ký tự
    │
    ▼
PushbackReader ─────► read char ──► phân tích text
    ▲                       │
    └──────── unread(char) ─┘
```

Ví dụ nguồn bên dưới có thể là `ByteArrayInputStream`, `FileInputStream`, `BufferedInputStream`, `StringReader` hoặc `InputStreamReader`.

Các lớp pushback **không ghi ngược dữ liệu về nguồn gốc**. `unread` chỉ đặt byte/ký tự vào buffer nội bộ của wrapper. Lần `read` kế tiếp lấy dữ liệu đã trả lại trước rồi mới đọc tiếp từ underlying stream/reader. Nếu nguồn là file, vị trí thực tế của file descriptor không bị tua lại bởi `unread`; nếu nguồn là mạng, byte cũng không bị gửi ngược qua socket.

### Pushback khác “rewind”

Giả sử nguồn chứa `A B C D`, trình đọc lấy `A`, `B`, rồi gọi `unread(B)`, `unread(A)`:

```text
Nguồn ban đầu:        A  B  C  D
Đã đọc từ nguồn:      A  B          (nguồn kế tiếp ở C)
Pushback buffer:     [A, B]        (A là phần tử sẵn sàng đọc)
Lần đọc tiếp:         A → B → C → D
```

Về quan sát ở wrapper, hai byte A/B xuất hiện lại; nhưng byte nguồn gốc **không được tua lại**. Đối với từng lần `unread(int)` riêng lẻ, phần tử vừa đẩy lại sẽ được đọc trước (LIFO). Nếu muốn trả lại một đoạn gồm nhiều byte/ký tự theo **thứ tự gốc**, hãy dùng `unread(byte[], off, len)` hoặc `unread(char[], off, len)` với đủ dung lượng, hoặc đẩy từng phần tử ngược thứ tự.

## 2. `PushbackInputStream`: trả lại byte

`PushbackInputStream` kế thừa `FilterInputStream`, nhận một `InputStream` bên dưới:

| Constructor | Ý nghĩa |
|---|---|
| `new PushbackInputStream(in)` | Bộ đệm pushback mặc định **1 byte** |
| `new PushbackInputStream(in, size)` | Bộ đệm pushback **`size` byte**, cố định; `size <= 0` không hợp lệ |

Các phương thức quan trọng:

| Phương thức | Hành vi |
|---|---|
| `read()` | Đọc byte pushback trước; nếu trống, đọc nguồn bên dưới; `0..255` hoặc `-1` (EOF) |
| `read(byte[], off, len)` | Đọc byte pushback còn có, sau đó nhận thêm dữ liệu từ nguồn theo hợp đồng `InputStream` |
| `unread(int b)` | Đẩy lại một byte; lần `read` kế tiếp nhận byte đó |
| `unread(byte[] b)` | Đẩy lại cả mảng byte, giữ thứ tự các phần tử khi đọc lại |
| `unread(byte[] b, int off, int len)` | Đẩy lại đoạn `b[off..off+len)` |
| `available()` | Ước tính byte có thể đọc mà không chặn, bao gồm byte pushback; **không** phải tổng byte còn lại |
| `skip(long n)` | Bỏ qua số byte theo hợp đồng `InputStream`, bao gồm phần đang pushback |
| `close()` | Đóng wrapper và underlying stream |

`read()` trả kiểu **`int`**, dù dữ liệu là byte: giá trị `0..255` là byte hợp lệ, còn `-1` nghĩa là hết nguồn. Khi dùng `unread`, **chỉ unread một byte đã đọc thành công**, không unread `-1` của EOF.

### Ví dụ 1 — đọc trước hai byte rồi trả lại đúng thứ tự

Lưu chương trình Java 17 vào **`PushbackByteDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

public class PushbackByteDemo {
    public static void main(String[] args) throws IOException {
        byte[] source = "ABCDE".getBytes(StandardCharsets.UTF_8);

        try (PushbackInputStream input = new PushbackInputStream(
                new ByteArrayInputStream(source), 2)) {
            int first = input.read();
            int second = input.read();

            System.out.println("peeked: " +
                (char) first + (char) second);

            // unread từng byte: đẩy phần tử cuối trước.
            input.unread(second);
            input.unread(first);

            byte[] replayed = input.readNBytes(2);
            System.out.println("replayed: " +
                new String(replayed, StandardCharsets.UTF_8));
            System.out.println("remaining: " +
                new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
```

Chạy:

```bash
javac PushbackByteDemo.java
java PushbackByteDemo
```

Kết quả chính xác:

```text
peeked: AB
replayed: AB
remaining: CDE
```

**Giải thích:** `first` là mã byte `A`, `second` là mã byte `B`. Để đọc lại theo thứ tự A rồi B, chương trình `unread(B)` **trước** và `unread(A)` **sau**. Buffer có dung lượng 2 nên chứa vừa hai byte. `readNBytes(2)` (Java 9+) lấy đúng hai byte pushback trong trường hợp cụ thể này; lần đọc tiếp tục sau đó nhận CDE từ nguồn. Vì ví dụ chỉ dùng ASCII, cast byte thành `char` ở phần hiển thị là hợp lý; **không cast từng byte UTF-8 thành ký tự** khi dữ liệu đa byte.

Dùng `try-with-resources` để đóng `PushbackInputStream`; đóng wrapper cũng đóng `ByteArrayInputStream` bên dưới (với stream trong bộ nhớ không có tài nguyên hệ điều hành phải giải phóng).

### Đẩy lại một mảng thay cho nhiều lời gọi

Khi nhìn trước một đoạn:

```java
byte[] prefix = new byte[4];
int n = input.read(prefix, 0, prefix.length);
if (n > 0) {
    input.unread(prefix, 0, n);
}
```

Đây là **đoạn minh họa**, giả sử `input` là `PushbackInputStream` có dung lượng pushback ít nhất 4 và đã khai báo/được xử lý `IOException`. Phần `prefix[0..n)` được trả lại để lần đọc kế tiếp tiếp tục theo **đúng thứ tự gốc**. `read(...)` có thể đọc **ít hơn 4 byte** ngay cả khi nguồn chưa EOF; mã thật cần lặp nếu giao thức phải chờ đúng 4 byte.

Không làm `input.unread(prefix, 0, 4)` nếu `n` chỉ bằng 2: như vậy sẽ đưa thêm những byte chưa từng đọc từ nguồn vào luồng dữ liệu.

## 3. `PushbackReader`: trả lại ký tự UTF-16 `char`

`PushbackReader` kế thừa `FilterReader`, nhận một `Reader`:

| Constructor | Ý nghĩa |
|---|---|
| `new PushbackReader(reader)` | Buffer pushback mặc định **1 `char`** |
| `new PushbackReader(reader, size)` | Buffer có thể chứa **`size` đơn vị `char`**, cố định |

Các phương thức tương ứng:

- `read()` đọc một đơn vị `char` và trả dưới dạng `int` (`0..65535` hoặc `-1` cho EOF).
- `read(char[], off, len)` đọc một đoạn ký tự.
- `unread(int c)`, `unread(char[] c)`, `unread(char[] c, int off, int len)` trả lại ký tự đã đọc.
- `ready()` cho biết khả năng đọc theo trạng thái buffer/reader; không nên biến kết quả thành cam kết rằng toàn bộ dòng dữ liệu đã sẵn có.
- `close()` đóng wrapper và reader bên dưới.

Quan trọng: `Reader` xử lý các **đơn vị UTF-16 `char`**, không tự động tách grapheme cluster hay Unicode code point. Chẳng hạn emoji 😀 dùng **hai `char`** trong một `String`. Nếu parser đọc theo `char`, nó phải xử lý surrogate pair nếu chính sách ngôn ngữ phân tích theo code point.

`PushbackReader` thích hợp khi parser text cần xem một ký tự để quyết định: “ký tự này thuộc token tiếp theo; tôi phải đẩy lại”. Nó **không** quay ngược quá trình giải mã byte ở `InputStreamReader`.

### Ví dụ 2 — nhìn trước dấu phân cách khi tách token

Lưu **`PushbackCharDemo.java`**:

```java
import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;

public class PushbackCharDemo {
    private static String readToken(PushbackReader reader)
            throws IOException {
        StringBuilder token = new StringBuilder();
        int ch;

        while ((ch = reader.read()) != -1) {
            if (ch == ',') {
                reader.unread(ch); // Token sau sẽ xử lý dấu phân cách.
                break;
            }
            token.append((char) ch);
        }
        return token.toString();
    }

    public static void main(String[] args) throws IOException {
        try (PushbackReader reader = new PushbackReader(
                new StringReader("alpha,beta"), 1)) {
            String first = readToken(reader);
            int delimiter = reader.read();
            String second = readToken(reader);

            System.out.println("first: " + first);
            System.out.println("delimiter: " + (char) delimiter);
            System.out.println("second: " + second);
        }
    }
}
```

Chạy:

```bash
javac PushbackCharDemo.java
java PushbackCharDemo
```

Kết quả:

```text
first: alpha
delimiter: ,
second: beta
```

Ở lần `readToken` đầu, parser đọc đến dấu phẩy và biết nó không thuộc nội dung token. `unread(',')` cho phép tầng gọi đọc chính dấu phẩy đó. Sau khi tầng gọi tiêu thụ delimiter, parser tiếp tục đọc `beta`. Vì chỉ cần xem trước một dấu phân cách, buffer **1 ký tự** đủ dùng.

Đây là ví dụ parser ASCII đơn giản. Với CSV thực tế, dấu phẩy có thể xuất hiện trong trường đặt trong dấu ngoặc kép, dấu ngoặc kép có escaping, có CRLF và nhiều quy tắc khác. Không dùng `readToken` trên làm CSV parser hoàn chỉnh.

## 4. Bộ đệm pushback có giới hạn cố định

Khi tạo:

```java
PushbackInputStream bytes = new PushbackInputStream(source, 2);
PushbackReader chars = new PushbackReader(reader, 2);
```

Đây là **đoạn minh họa**, với `source` là `InputStream` và `reader` là `Reader`. Nếu một wrapper vừa được tạo với capacity 2, nó có tối đa **2 đơn vị trống để unread**, không tăng kích thước tự động.

Ví dụ (chỉ minh họa):

```java
PushbackInputStream pb = new PushbackInputStream(source, 1);
int a = pb.read();
int b = pb.read();
pb.unread(b); // Một chỗ pushback đã được chiếm.
pb.unread(a); // IOException: pushback buffer đã đầy.
```

Không nên dựa vào chuỗi thông báo lỗi `push back buffer is full` trong logic nghiệp vụ; hãy xử lý loại ngoại lệ `IOException` và thiết kế dung lượng phù hợp. Nếu muốn peek tối đa N byte, cần cấp capacity ít nhất N **nếu có lúc phải trả lại toàn bộ N byte**. Dung lượng tối thiểu hợp lệ của constructor `(source, size)` là 1.

Việc `read()` lấy đi một byte đang pushback sẽ giải phóng một chỗ để `unread()` thêm. Buffer chỉ cần chứa **phần chưa đọc lại**; không phải lúc nào nó cũng chứa toàn bộ byte chương trình từng đọc.

### Thứ tự LIFO và unread mảng

Giả sử đã đọc A rồi B:

| Thao tác | Hai ký tự sẽ đọc lại |
|---|---|
| `unread(B)` rồi `unread(A)` | A, B |
| `unread(A)` rồi `unread(B)` | B, A |
| `unread(new byte[]{'A', 'B'})` | A, B |

Với `PushbackReader`, nguyên lý tương tự cho `char[]`. Lý do `unread(byte[])` giữ đúng thứ tự là API đã hỗ trợ đẩy lại **cả đoạn** theo trình tự của nó; bạn không phải tự đảo mảng trước khi gọi.

Đừng nhầm **pushback buffer** với **buffer đọc để tăng hiệu năng**. Buffer pushback phục vụ tính đúng đắn khi nhìn trước; buffer của `BufferedInputStream`/`BufferedReader` giảm nhiều thao tác đọc nhỏ từ underlying source. Một ứng dụng có thể dùng cả hai loại buffer, nhưng phải hiểu thứ tự lớp bao.

## 5. Ứng dụng byte lookahead: UTF-8 BOM

Một tình huống thực tế là kiểm tra ba byte đầu `EF BB BF` (UTF-8 BOM). Nếu định dạng cho phép BOM, chương trình có thể bỏ qua đúng ba byte này; nếu không có BOM, phải **trả lại những byte đã đọc thử** để nội dung văn bản không mất các ký tự đầu.

```text
                 Đọc tối đa 3 byte đầu
                       │
            ┌──────────┴───────────┐
            │                      │
      EF BB BF đúng            Không phải BOM
            │                      │
       Bỏ 3 byte            unread chính xác số
            │               byte đã đọc (0..3)
            └──────────┬───────────┘
                       ▼
              Decode phần còn lại
                     bằng UTF-8
```

Đây là **BOM ở mức byte**, vì vậy `PushbackInputStream` thích hợp. `PushbackReader` hoạt động **sau bước decode**, không dùng để quay ngược những byte decoder đã tiêu thụ. Không áp dụng quy tắc ba byte của UTF-8 BOM cho UTF-16 hoặc UTF-32: các encoding đó có BOM/endianness và độ dài khác.

### Ví dụ 3 — đọc BOM tùy chọn và không làm mất prefix

Lưu **`Utf8BomPushbackDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

public class Utf8BomPushbackDemo {
    private static String decodeOptionalBom(byte[] data)
            throws IOException {
        try (PushbackInputStream input =
                 new PushbackInputStream(
                     new ByteArrayInputStream(data), 3)) {
            byte[] prefix = new byte[3];
            int count = 0;

            // Dùng read() từng byte để xử lý đúng cả nguồn rất ngắn.
            while (count < prefix.length) {
                int next = input.read();
                if (next == -1) {
                    break;
                }
                prefix[count++] = (byte) next;
            }

            boolean isBom = count == 3
                && (prefix[0] & 0xff) == 0xEF
                && (prefix[1] & 0xff) == 0xBB
                && (prefix[2] & 0xff) == 0xBF;

            if (!isBom) {
                // Trả lại chính các byte đã đọc, đúng thứ tự.
                input.unread(prefix, 0, count);
            }

            return new String(
                input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static void main(String[] args) throws IOException {
        byte[] plain =
            "Xin chào".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[3 + plain.length];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(
            plain, 0, withBom, 3, plain.length);

        System.out.println("plain: " + decodeOptionalBom(plain));
        System.out.println("with BOM: " + decodeOptionalBom(withBom));
        System.out.println(
            "empty: [" + decodeOptionalBom(new byte[0]) + "]");
    }
}
```

Chạy Java 17+:

```bash
javac Utf8BomPushbackDemo.java
java Utf8BomPushbackDemo
```

Kết quả:

```text
plain: Xin chào
with BOM: Xin chào
empty: []
```

Chương trình đọc tối đa ba byte. Nếu **không có BOM**, `unread(prefix, 0, count)` giữ lại toàn bộ số byte đã nhìn trước, kể cả với file có 1 hoặc 2 byte. Nếu đúng BOM, chương trình không unread ba byte BOM mà tiếp tục decode phần còn lại. Nếu input rỗng, `count == 0`, không có byte nào cần đẩy lại.

**Giới hạn ví dụ:** `readAllBytes()` nạp toàn bộ dữ liệu vào bộ nhớ; phù hợp minh họa với `ByteArrayInputStream`, không phù hợp mặc định cho file/network rất lớn. Nếu cần stream liên tục, sau bước BOM detection dùng `InputStreamReader(input, StandardCharsets.UTF_8)` và `BufferedReader` để đọc dần. Một decoder nghiêm ngặt có thể sử dụng `StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)`; constructor `new String(bytes, UTF_8)` thường thay byte lỗi bằng ký tự thay thế theo hợp đồng của nó. Chỉ loại BOM khi định dạng cho phép; có những giao thức yêu cầu UTF-8 **không BOM**.

### Ranh giới đọc không phải ranh giới ký tự UTF-8

Với dữ liệu UTF-8 có dấu tiếng Việt hay emoji, một ký tự có thể dùng 2–4 byte. `PushbackInputStream` chỉ nhìn thấy byte, không tự hiểu Unicode. Nếu bạn đọc đúng một byte đầu của emoji rồi coi nó là ký tự hoàn chỉnh, bạn sẽ giải mã sai. Với nội dung text, thường nên dùng một `InputStreamReader` xử lý charset, rồi `PushbackReader` để nhìn trước ký tự sau khi decode.

Không tự tách “message” theo số byte mỗi `read()` của socket: một message có thể phân mảnh ở nhiều lần đọc, và nhiều message có thể cùng một lần đọc. Pushback chỉ giúp phần **lookahead cục bộ**, không thay thế parser giao thức có framing đúng.

## 6. `mark()/reset()` khác pushback thế nào?

Nhiều ứng dụng chỉ cần nhìn trước một đoạn; ngoài pushback còn có cơ chế **đánh dấu và đọc lại**:

| Cơ chế | Cách hoạt động | Hợp khi nào |
|---|---|---|
| `PushbackInputStream` / `PushbackReader` | Sau khi đọc, gọi `unread` để đưa **dữ liệu đã chọn** vào buffer đầu luồng; dung lượng cố định | Peek một vài byte/ký tự, nhận diện prefix/delimiter |
| `BufferedInputStream.mark(readlimit)` / `reset()` | Ghi nhớ vị trí logic rồi trở lại vị trí đã đánh dấu nếu mark còn hợp lệ | Muốn đọc thử một đoạn và quay lại điểm mark |
| `BufferedReader.mark(readAheadLimit)` / `reset()` | Tương tự với các ký tự | Parser cần quay lại vị trí ký tự đã đánh dấu |
| Lưu giá trị vừa đọc vào biến `lookahead` | Parser giữ chính ký tự tiếp theo mà không unread | Parser có state machine, tokenizing phức tạp |
| `ByteArrayInputStream` / `StringReader` | Nguồn bộ nhớ có cơ chế mark/reset riêng | Input nhỏ đã có sẵn toàn bộ trong bộ nhớ |
| `SeekableByteChannel` / `RandomAccessFile` | Đọc lại tại vị trí theo offset khi nguồn hỗ trợ seek | File cần truy cập ngẫu nhiên |

**`PushbackInputStream` và `PushbackReader` không hỗ trợ `mark/reset`**: `markSupported()` trả `false`; `reset()` ném `IOException`; `mark(...)` không mang hiệu lực quay lại như mong đợi. Đừng “phòng xa” bằng cách gọi `mark()` trên wrapper pushback rồi trông chờ `reset()` cứu parser. Hãy chọn **một cơ chế lookahead** theo nhu cầu.

Khác biệt quan trọng: `mark/reset` thường quay lại **một vị trí đã đánh dấu**, trong khi `unread` cho phép đặt lại **những byte/ký tự cụ thể**. Chương trình có thể `unread` một giá trị không trùng với giá trị từng đọc; API cho phép đưa giá trị vào buffer, nhưng nếu làm vậy thì đang tạo dữ liệu tổng hợp, không chỉ tua lại. Nên tách logic **biến đổi luồng** khỏi logic **peek rồi unread đúng dữ liệu gốc**.

### Về nguồn dữ liệu trong bộ nhớ

`ByteArrayInputStream` và `StringReader` đã cung cấp khả năng mark/reset. Nếu toàn bộ dữ liệu đã ở trong bộ nhớ và bạn chỉ cần đọc lại một vị trí, thường không cần thêm wrapper pushback. Tuy nhiên pushback vẫn hữu ích để giữ một interface parser thống nhất giữa nguồn memory và file/network stream **không seek được**.

Với parser phức tạp cần lookahead dài, lexer/token stream có cursor riêng, trạng thái và giới hạn rõ ràng sẽ dễ kiểm soát hơn việc liên tục mở rộng pushback buffer.

## 7. Ghép `PushbackInputStream`, `PushbackReader` với bộ đệm khác

Thứ tự các lớp bao có thể ảnh hưởng đến kết quả:

```text
Raw byte stream
    ↓
BufferedInputStream         (tăng hiệu quả đọc byte)
    ↓
PushbackInputStream         (byte lookahead, ví dụ kiểm tra header)
    ↓
InputStreamReader(UTF-8)    (decode byte → char)
    ↓
PushbackReader              (char lookahead, ví dụ phân tích dấu phân cách)
    ↓
Parser
```

Đây là **một sơ đồ khái niệm**, không phải cấu hình bắt buộc. Không phải parser nào cũng cần cả hai lớp pushback. Nếu chỉ parse text, thường `InputStreamReader` + `PushbackReader` hoặc `BufferedReader` + state machine là đủ. Nếu cần kiểm tra BOM ở mức byte, có thể peek qua `PushbackInputStream` **trước** khi tạo decoder.

Với việc đẩy lại dữ liệu ở cấp byte, lớp tiếp nhận `unread` phải chính là wrapper qua đó lần đọc tiếp theo sẽ đi qua. Nếu một `BufferedInputStream` bên ngoài đã prefetch byte vào buffer riêng, gọi `unread` trên lớp pushback bên dưới có thể không làm byte xuất hiện ngay ở lần đọc kế tiếp từ lớp buffer bên ngoài. Thiết kế ownership của wrapper rõ ràng và không trộn đọc trực tiếp trên underlying stream sau khi đã đưa wrapper vào sử dụng.

### Reader và stream đọc byte không nên thay phiên tùy tiện

Khi `InputStreamReader` đã đọc byte từ underlying stream, decoder có thể giữ buffer và trạng thái nội bộ. Nếu một phần code đọc ký tự qua reader, phần khác trực tiếp đọc byte hoặc pushback tại stream dưới reader, thứ tự byte/char rất dễ sai.

Nên lựa chọn rõ một điểm làm chủ:

- **Xử lý protocol/header byte trước**, hoàn thành bước peek/unread, sau đó chuyển sang reader decode.
- **Xử lý nội dung ký tự**, dùng một reader và nếu cần peek thì bọc `PushbackReader` trên reader đó.
- Nếu cần cả mức byte và mức ký tự, mô tả ranh giới chuyển đổi và **không xen kẽ** hai cách đọc trên cùng nguồn.

Đặc biệt với Unicode, mỗi `read()` của `PushbackReader` trả về **một UTF-16 code unit**, không nhất thiết là một Unicode code point đầy đủ. Code point nằm ngoài BMP cần hai `char`; parser cần xử lý chúng như một cặp nếu grammar hoạt động trên code point.

## 8. EOF, lỗi I/O và vòng đời tài nguyên

### EOF không phải một byte/ký tự để unread

Một lời gọi `read()` trả `-1` khi không còn dữ liệu. `unread(-1)` **không phải lệnh “đặt EOF trở lại”**; nó có thể đưa giá trị byte/char lấy từ phần bit thấp của số nguyên vào buffer, làm biến dạng luồng. Luôn kiểm tra:

```java
int next = input.read();
if (next != -1) {
    input.unread(next);
}
```

Đoạn minh họa này dùng `PushbackInputStream input`, buffer còn chỗ và được xử lý `IOException`. Với `PushbackReader` cũng cần kiểm tra `-1`.

Nếu underlying source đã tới EOF, bạn **vẫn có thể đọc được byte/ký tự mới được unread hợp lệ** trước khi `read()` trở lại `-1`. Do đó “đã thấy EOF một lần” không có nghĩa mọi lần đọc wrapper về sau đều nhất thiết trả `-1`, nếu ứng dụng chủ động unread thêm dữ liệu.

### `read(byte[])` có thể trả ít hơn yêu cầu

Với file, network hoặc stream có nhiều tầng buffer, `read(buffer, off, len)` không bảo đảm lấp đầy `len` byte mỗi lần. Nếu parser cần đúng N byte header thì phải lặp đọc cho đến khi đủ, EOF hoặc lỗi, đồng thời giới hạn độ dài để tránh chờ vô hạn một input không hoàn chỉnh. `InputStream.readNBytes(n)` giúp đọc tối đa n byte nhưng vẫn có thể trả ít hơn khi EOF. Với text, `Reader.read(char[], off, len)` cũng phải xử lý đọc từng phần và EOF.

### `IOException`, buffer overflow và đóng tài nguyên

`unread` có thể ném `IOException` khi buffer không đủ chỗ hoặc stream/reader đã đóng. Các thao tác đọc có thể ném lỗi từ underlying source. Trong mã sản phẩm:

1. Xác định **số byte/ký tự tối đa phải nhìn trước** để cấp đúng dung lượng.
2. Gọi unread **đúng những dữ liệu đã read thành công**, theo đúng thứ tự.
3. Kiểm tra EOF và partial read.
4. Dùng try-with-resources cho wrapper sở hữu tài nguyên.
5. Không chia sẻ cùng wrapper giữa nhiều luồng đọc/ghi mà chưa thiết kế synchronization và quyền sở hữu rõ ràng.

Khi đóng `PushbackInputStream`/`PushbackReader`, wrapper đóng cả underlying source. Nếu source là `System.in` hoặc một reader dùng chung, không đặt wrapper trong scope đóng tùy tiện mà không hiểu ownership, vì nó có thể khiến phần khác của chương trình không còn đọc được.

## 9. Các lỗi phổ biến

### Lỗi 1: Tạo buffer mặc định 1 byte nhưng unread nhiều hơn

```java
PushbackInputStream pb = new PushbackInputStream(source);
pb.unread(new byte[] {0x01, 0x02}); // IOException do chỉ có 1 chỗ.
```

Đây là **phản ví dụ**, với `source` là `InputStream`, có xử lý `IOException`. Nếu parser cần nhìn trước hai byte, dùng `new PushbackInputStream(source, 2)`. Không phụ thuộc rằng Java sẽ tự mở rộng buffer.

### Lỗi 2: Unread nhiều lần theo thứ tự đọc

Đã đọc A, B mà gọi `unread(A)` rồi `unread(B)`, dữ liệu đọc lại sẽ là **B, A**. Cách đúng để trả lại A trước B là `unread(B)` rồi `unread(A)`, hoặc `unread(new byte[]{'A', 'B'})` khi có đủ chỗ.

### Lỗi 3: Tin `mark/reset` trên pushback

Cả hai wrapper pushback đều báo `markSupported() == false`. `reset()` không thay thế `unread`. Nếu cần quay lại một vị trí đánh dấu, chọn wrapper có hỗ trợ mark hoặc giữ cursor riêng.

### Lỗi 4: Đọc byte Unicode rồi ép thành `char`

UTF-8 có nhiều code point dùng nhiều byte, ví dụ chữ “é” là `C3 A9`. Hai byte không phải hai ký tự độc lập. Chỉ decode **sau khi** gom đúng chuỗi byte hoặc dùng streaming decoder/Reader. `PushbackReader` làm việc sau decode và thích hợp cho cú pháp theo ký tự.

### Lỗi 5: Nhận diện BOM mà làm mất byte đầu

Đọc thử 3 byte rồi bỏ luôn khi không phải `EF BB BF` sẽ làm mất ba byte đầu nội dung thật. Cần `unread` chính xác số byte đã đọc, kể cả nguồn chỉ có 1–2 byte. Không lấy `available()` làm tổng độ dài file hoặc phép kiểm tra đủ header.

### Lỗi 6: Ghép wrapper sai thứ tự và trộn underlying source

Nếu parser đọc qua `BufferedInputStream` bên trên nhưng code lại `unread` trên `PushbackInputStream` bên dưới, byte trả lại có thể chưa xuất hiện ngay do buffer bên trên còn dữ liệu. Không vừa đọc trực tiếp `source` vừa đọc qua wrapper của source; tránh bỏ qua dữ liệu đã buffer.

### Lỗi 7: Coi pushback là tính năng “seek”

Pushback không đặt lại file position, không phục hồi trạng thái giao thức trên mạng và không tự giúp tua lại vô hạn. Nếu cần truy cập offset tùy ý trong file, chọn `SeekableByteChannel`/`FileChannel` hoặc `RandomAccessFile`.

### Lỗi 8: Parser thiếu xử lý input lỗi hoặc malformed

Header có thể thiếu byte, dấu phân cách có thể không xuất hiện, text có thể chứa surrogate không hợp lệ hoặc byte UTF-8 sai. Pushback không kiểm tra tất cả tính hợp lệ đó. Cần validate định dạng, giới hạn bộ nhớ/thời gian đọc và xử lý `IOException`/EOF.

### Lỗi 9: Thêm pushback vào mọi stream vì tưởng tăng tốc

Pushback là công cụ **điều khiển lookahead**, không phải mặc định tối ưu I/O. Đối với đọc tuần tự thông thường, `BufferedInputStream`, `BufferedReader` hoặc đọc theo block có thể thích hợp hơn. Chỉ thêm pushback khi có logic phải trả lại dữ liệu.

## 10. Bảng quyết định lựa chọn

| Nhu cầu | API nên cân nhắc | Lý do |
|---|---|---|
| Peek 1–4 byte đầu của binary protocol | `PushbackInputStream` với capacity đủ | Trả lại prefix nếu không đúng magic/header |
| Kiểm tra UTF-8 BOM trước khi decode | `PushbackInputStream`, sau đó `InputStreamReader` | BOM được nhận diện ở mức byte |
| Peek dấu phân cách khi tokenize văn bản | `PushbackReader` | Làm việc với ký tự sau khi decode |
| Tokenizer có grammar phức tạp và lookahead dài | Parser giữ token/cursor riêng | Dễ biểu diễn trạng thái và backtracking |
| Muốn đọc thử rồi quay lại vị trí đã đánh dấu | `BufferedInputStream.mark/reset`, `BufferedReader.mark/reset` | Phù hợp cơ chế dấu mốc |
| Dữ liệu nhỏ đã nằm trong mảng byte | `ByteArrayInputStream` và cursor/mark | Ít lớp bọc hơn; vẫn dùng pushback nếu API parser yêu cầu |
| Muốn tua đến offset trong file | `FileChannel.position`, `SeekableByteChannel` | Truy cập vị trí thật, không phải buffer trả lại |
| Đọc text thông thường theo dòng | `BufferedReader.readLine` | Không cần lookahead tự quản nếu đã phân tách dòng |
| Đọc ký tự UTF-8 qua nhiều chunk | `InputStreamReader`/decoder streaming | Giữ trạng thái charset thay vì decode byte rời |

## 11. Bài tập tự kiểm tra

**Câu 1.** Nguồn chứa `XYZ`. Bạn đọc X và Y, sau đó gọi `unread(X)`, `unread(Y)` trên buffer đủ lớn. Hai ký tự đọc lại đầu tiên là gì?

**Đáp án:** Y rồi X. Pushback theo từng lần unread hoạt động kiểu LIFO. Để có X rồi Y, gọi `unread(Y)` trước.

**Câu 2.** Constructor `new PushbackReader(reader)` mặc định cho trả lại bao nhiêu `char` cùng lúc?

**Đáp án:** 1 `char`. Khi cần nhiều hơn, chỉ rõ `size` trong constructor.

**Câu 3.** Tại sao `unread(-1)` sau EOF là sai?

**Đáp án:** `-1` là sentinel báo hết dữ liệu, không phải byte/ký tự đã đọc. `unread` có thể đẩy giá trị rút từ `-1` vào buffer, làm giả dữ liệu.

**Câu 4.** Vì sao không dùng `PushbackReader` để kiểm tra ba byte `EF BB BF`?

**Đáp án:** `PushbackReader` hoạt động trên ký tự **sau khi decode**; ba giá trị đó là byte BOM UTF-8. Cần nhận diện mức byte bằng `PushbackInputStream` trước khi tạo reader.

**Câu 5.** Nếu `PushbackInputStream` có capacity 3, `read` được 2 byte, có thể `unread(prefix, 0, 2)` rồi đọc lại không?

**Đáp án:** Có, nếu buffer đang trống và chưa đóng. Hai byte đọc lại giữ đúng thứ tự trong prefix.

**Câu 6.** `PushbackInputStream.markSupported()` trả gì? `reset()` có đưa stream về đầu đoạn đọc thử không?

**Đáp án:** `false`. `reset()` ném `IOException`; phải dùng `unread` hoặc chọn wrapper có hỗ trợ mark/reset.

**Câu 7.** Vì sao không nên dùng `PushbackInputStream` như buffer đọc lớn?

**Đáp án:** Mục đích chính là trả lại **một lượng nhỏ dữ liệu lookahead**, buffer có kích thước cố định và không tự thay cho chiến lược buffering để tăng hiệu năng hay streaming parser có framing.

**Ghi nhớ:** `PushbackInputStream` trả lại **byte**, `PushbackReader` trả lại **`char`**. Cả hai đều đặt dữ liệu vào buffer nhỏ ở **đầu luồng wrapper**, không viết ngược vào nguồn và không hỗ trợ `mark/reset`. Chọn dung lượng theo nhu cầu peek, xử lý đúng thứ tự `unread`, EOF và charset; dùng chúng khi parser cần nhìn trước nhưng vẫn muốn đọc dữ liệu tuần tự qua cùng một interface.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="in-memory-io-007">PipedInputStream, PipedOutputStream và giao tiếp giữa luồng</a>

<details>
<summary>Xem chi tiết</summary>

# `PipedInputStream`, `PipedOutputStream` và giao tiếp giữa luồng trong Java

`PipedInputStream` và `PipedOutputStream` trong `java.io` tạo thành một **ống dẫn byte một chiều (byte pipe)** giữa hai phần công việc trong **cùng JVM**. Một luồng (thread) có thể ghi byte vào đầu ra, còn luồng khác đọc những byte ấy ở đầu vào. Khác với `ByteArrayInputStream`/`ByteArrayOutputStream` lưu dữ liệu đã có trong bộ nhớ, pipe có **bộ đệm hữu hạn và khả năng chờ (blocking)**: nếu chưa có dữ liệu, phía đọc có thể đợi; nếu bộ đệm đầy, phía ghi có thể đợi phía đọc tiêu thụ.

Điểm quan trọng nhất là **kết nối đúng hai đầu, chạy bên ghi và bên đọc đồng thời, đóng bên ghi khi hoàn tất và xử lý lỗi ở cả hai phía**. Pipe chuyển **dòng byte** theo thứ tự, không tự tạo ranh giới “thông điệp” và không biến `read()` thành thao tác bất đồng bộ.

## 1. Hai lớp đảm nhiệm việc gì?

- `PipedOutputStream`: đầu **ghi**. Producer gọi `write(int)` hoặc `write(byte[], off, len)` để đẩy byte vào pipe.
- `PipedInputStream`: đầu **đọc**. Consumer gọi `read()` hoặc `read(byte[])` để nhận các byte theo thứ tự đã ghi.
- **Kết nối**: một đầu input và một đầu output được nối với nhau bằng constructor hoặc `connect(...)`. Không được nối lại một đầu đã kết nối.
- **Vòng đời**: khi producer đóng đầu output sau khi ghi xong, consumer có thể đọc hết các byte còn trong buffer rồi nhận `-1` (EOF). Ngược lại, đóng đầu input quá sớm có thể khiến việc ghi của producer thất bại.
- **Bộ đệm**: phần đệm thuộc cơ chế pipe trong bộ nhớ, không phải file hay kết nối mạng. `PipedInputStream(int pipeSize)` cho phép chọn kích thước bộ đệm bằng số byte dương.

Sơ đồ:

```text
Thread producer                         Thread consumer
      |                                       ^
      | write(bytes)                          | read(bytes)
      v                                       |
PipedOutputStream ---- kết nối ----> PipedInputStream
                                   [bộ đệm byte hữu hạn]
```

Đây là cơ chế giao tiếp **một chiều**: dữ liệu đi từ output sang input. Nếu ứng dụng cần trao đổi hai chiều, cần hai pipe có chiều ngược nhau hoặc một cơ chế giao tiếp phù hợp hơn.

### Các cách khởi tạo/kết nối hợp lệ

```java
// Cách 1: Tạo input trước, đưa vào constructor của output.
PipedInputStream input = new PipedInputStream();
PipedOutputStream output = new PipedOutputStream(input);

// Cách 2: Tạo output trước, đưa vào constructor của input.
PipedOutputStream output2 = new PipedOutputStream();
PipedInputStream input2 = new PipedInputStream(output2);

// Cách 3: Tạo hai đầu rời rồi kết nối một lần.
PipedInputStream input3 = new PipedInputStream(4096);
PipedOutputStream output3 = new PipedOutputStream();
output3.connect(input3);
```

Các đoạn trên là **minh họa**, cần `import java.io.PipedInputStream; import java.io.PipedOutputStream;` và đặt bên trong phương thức có xử lý `IOException`. Chỉ chọn **một** cách cho mỗi cặp input/output, không phải ba cách cho cùng một pipe. `connect()` có thể ném `IOException` nếu đầu tương ứng đã nối, bị đóng hoặc kết nối không hợp lệ; thao tác đọc/ghi trước khi nối cũng có thể lỗi.

`PipedInputStream` mặc định có bộ đệm hữu hạn; không nên dựa vào một dung lượng mặc định cụ thể để thiết kế logic. Khi cần giới hạn sức chứa, hãy chỉ định rõ `pipeSize` dương và xem đó là giới hạn của **bộ đệm byte**, không phải độ dài lớn nhất của cả dòng dữ liệu.

## 2. Ví dụ hoàn chỉnh: một thread ghi, thread khác đọc

Đoạn dưới đây sử dụng một thread từ `ExecutorService` làm producer, còn thread `main` làm consumer. Bộ đệm của pipe chỉ có **4 byte** để minh họa rằng hai phía phải tiến triển đồng thời. Producer ghi toàn bộ dữ liệu UTF-8 rồi đóng output; consumer đọc cho đến EOF và cuối cùng decode lại các byte.

```java
// PipeHelloDemo.java
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PipeHelloDemo {
    public static void main(String[] args) throws Exception {
        PipedInputStream input = new PipedInputStream(4);
        PipedOutputStream output = new PipedOutputStream(input);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> producer = executor.submit(() -> {
                try (PipedOutputStream writer = output) {
                    writer.write("Xin chào, pipe!"
                            .getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });

            try (PipedInputStream reader = input) {
                ByteArrayOutputStream received = new ByteArrayOutputStream();
                byte[] buffer = new byte[3];
                int count;

                while ((count = reader.read(buffer)) != -1) {
                    received.write(buffer, 0, count);
                }

                producer.get(); // Chuyển lỗi của producer về main.
                System.out.println("text="
                        + received.toString(StandardCharsets.UTF_8));
                System.out.println("bytes=" + received.size());
            }
        } finally {
            executor.shutdown();
        }
    }
}
```

Biên dịch/chạy với **Java 17+**:

```bash
javac -encoding UTF-8 PipeHelloDemo.java
java PipeHelloDemo
```

Đầu ra:

```text
text=Xin chào, pipe!
bytes=16
```

Giải thích:

1. `PipedOutputStream(input)` kết nối output vào input.
2. `executor.submit(...)` chạy công việc ghi trên thread khác. `main` không cần chờ producer hoàn tất trước khi bắt đầu đọc.
3. Vì bộ đệm chỉ có 4 byte mà payload UTF-8 dài 16 byte, producer phải chờ consumer giải phóng chỗ trống khi cần.
4. `reader.read(buffer)` trả về **số byte thực sự nhận**; vòng lặp chỉ ghi `count` byte hợp lệ sang `ByteArrayOutputStream`.
5. Producer đóng `writer`, báo đã hết dữ liệu. Consumer nhận `-1` sau khi đọc hết phần còn trong pipe.
6. `producer.get()` đợi tác vụ hoàn tất và phát hiện exception từ producer (dưới dạng lỗi của `Future`), thay vì để lỗi trong worker bị bỏ qua.
7. UTF-8 có ký tự `à` nhiều byte; ghép byte rồi giải mã toàn bộ giúp không vô tình tách một ký tự đa byte ở ranh giới các lần `read()`.

**Không gọi `producer.get()` trước vòng lặp đọc** trong ví dụ này: nếu producer đang ghi quá dung lượng pipe, nó sẽ đợi consumer đọc; nếu `main` lại đợi producer hoàn tất trước khi đọc, chương trình có thể bị **deadlock**.

Lưu ý trong chương trình thực tế, nếu consumer lỗi sớm, cần đóng input để giải phóng tài nguyên và xử lý/cancel producer; đồng thời giới hạn thời gian chờ khi thích hợp. Ví dụ tập trung vào đường đi thành công và truyền lỗi producer.

## 3. Blocking, backpressure và thứ tự byte

Hai đầu pipe giao tiếp nhờ vùng đệm có dung lượng hữu hạn:

| Tình huống | Bên đọc | Bên ghi |
| --- | --- | --- |
| Chưa có byte và đầu ghi còn hoạt động | `read()` có thể **chờ** dữ liệu | Có thể ghi byte vào bộ đệm nếu còn chỗ |
| Bộ đệm đang còn byte | `read()` trả một hoặc nhiều byte | Ghi tiếp nếu còn chỗ |
| Bộ đệm đã đầy | Consumer đọc sẽ giải phóng chỗ | `write()` có thể **chờ** đến khi có chỗ |
| Đầu ghi đã đóng sau khi ghi xong | Đọc byte còn đệm, sau đó `read()` trả `-1` | Không ghi tiếp được qua đầu đã đóng |
| Đầu đọc bị đóng khi producer vẫn ghi | Không đọc tiếp được | Lệnh ghi có thể báo `IOException` |

Việc bên ghi tự nhiên phải đợi khi bộ đệm đầy được gọi là **backpressure**: tốc độ producer bị giới hạn bởi khả năng consumer nhận dữ liệu. Đây là tác dụng hữu ích với một luồng byte, nhưng cũng là lý do không được lạm dụng pipe trong những tác vụ thực hiện tuần tự trên cùng thread.

### Ví dụ hoàn chỉnh: quan sát ghi bị chặn khi bộ đệm đầy

Để tránh đo bằng `sleep()` hoặc thời gian may rủi, chương trình tạo pipe dung lượng **2 byte** và cho producer ghi **3 byte**. Trước khi main đọc byte nào, việc ghi đủ ba byte không thể hoàn thành thành công. Khi main đọc một byte, producer có chỗ để ghi byte thứ ba và kết thúc.

```java
// PipeBackpressureDemo.java
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PipeBackpressureDemo {
    public static void main(String[] args) throws Exception {
        PipedInputStream input = new PipedInputStream(2);
        PipedOutputStream output = new PipedOutputStream(input);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> writeTask = executor.submit(() -> {
                try (PipedOutputStream writer = output) {
                    writer.write(new byte[] {10, 20, 30});
                }
                return null;
            });

            try (PipedInputStream reader = input) {
                System.out.println("doneBeforeRead=" + writeTask.isDone());
                System.out.println("first=" + reader.read());

                writeTask.get();
                System.out.println("doneAfterRead=" + writeTask.isDone());
            }
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac PipeBackpressureDemo.java
java PipeBackpressureDemo
```

Đầu ra dự kiến khi producer và consumer hoạt động đúng:

```text
doneBeforeRead=false
first=10
doneAfterRead=true
```

Với dung lượng 2, producer không thể ghi thành công cả ba byte **trước khi có lần đọc giải phóng ít nhất một vị trí**, nên `writeTask.isDone()` ban đầu là `false` (hoặc tác vụ chưa được lập lịch). Sau `reader.read()` trả byte đầu `10`, việc ghi có thể hoàn tất; `writeTask.get()` đợi chính xác thời điểm producer xong. Đây là minh họa **điều kiện bộ đệm**, không phải benchmark đo thời gian.

Nếu viết kiểu sau trên cùng thread:

```java
// MÃ SAI VỀ THIẾT KẾ: chỉ minh họa, không chạy riêng.
PipedInputStream input = new PipedInputStream(2);
PipedOutputStream output = new PipedOutputStream(input);
output.write(new byte[] {10, 20, 30}); // Có thể bị chặn.
int first = input.read();               // Có thể không bao giờ tới đây.
```

Thread đang cố ghi nhiều hơn dung lượng đệm sẽ chờ consumer, nhưng consumer nằm ở dòng lệnh **sau** trong chính thread đó. Đó là bẫy deadlock phổ biến.

### `read()` không bảo đảm trả đủ một “message”

Pipe là **byte stream**, nên một lần `read(byte[])` có thể trả ít byte hơn yêu cầu. Kích thước buffer của consumer, cách producer chia các lần `write` và thời điểm lập lịch **không phải ranh giới bản ghi**. Dù producer gọi `write(message)` một lần, consumer vẫn có thể nhận thông điệp qua nhiều lần `read`.

Do đó, không được gắn ý nghĩa “mỗi `read` = một message”. Muốn trao đổi message, ứng dụng phải có giao thức đóng khung (framing), ví dụ prefix chứa chiều dài, dấu phân cách có escape, hoặc codec ở tầng trên.

## 4. Đóng đầu ghi, EOF và vòng đời hai phía

Một quan hệ quan trọng:

```text
Producer ghi dữ liệu ---> Producer đóng output
       |                        |
       v                        v
Consumer đọc phần còn đệm ---> read() == -1 (EOF)
```

Khi bên ghi **đóng hợp lệ**, byte đã ghi trước đó vẫn được consumer lấy ra trước khi đến EOF. `read()` trên đầu đọc không tự trở thành `-1` chỉ vì tạm thời không có dữ liệu; khi đầu ghi còn mở và chưa có dữ liệu, nó có thể chờ.

Đóng **đầu đọc** không giống đóng **đầu ghi**:

- Đóng `PipedOutputStream` là cách producer thông báo “không còn byte mới” theo luồng dữ liệu và cho phép consumer kết thúc bằng EOF sau khi đọc hết dữ liệu đã đệm.
- Đóng `PipedInputStream` khi producer chưa xong có thể làm producer nhận `IOException` lúc ghi. Không dùng nó làm “tín hiệu bình thường” cho producer đã xong.
- Một worker bị chết hoặc kết thúc **mà không đóng đúng đầu pipe** có thể gây các lỗi “broken pipe”/“write end dead”/“read end dead” tùy trạng thái và implementation. Không dựa vào nội dung thông báo lỗi cụ thể để thay giao thức hoàn tất.
- `flush()` không thay thế `close()` làm dấu hiệu kết thúc luồng. Đặc biệt, `flush()` không phát ra marker EOF.

### Quyền sở hữu tài nguyên khi dùng wrapper

Bạn có thể bọc pipe bằng các lớp khác:

```java
// Minh họa một cặp wrapper đọc/ghi dữ liệu nhị phân.
DataInputStream reader = new DataInputStream(pipedInput);
DataOutputStream writer = new DataOutputStream(pipedOutput);
```

Ví dụ trên giả định `pipedInput` và `pipedOutput` đã nối và các import sẵn có. Khi mỗi thread **sở hữu** một đầu của pipe, dùng `try-with-resources` với **wrapper ngoài cùng** của đầu đó. `DataOutputStream.close()` đóng đầu output bên dưới; `DataInputStream.close()` đóng đầu input bên dưới. Đừng đóng raw pipe đang được thread kia sử dụng khi nó chưa xong.

Nếu dùng `BufferedOutputStream`/`BufferedWriter` bọc output, dữ liệu có thể còn nằm trong bộ đệm **ngoài** pipe; cần `flush()` ở thời điểm thông điệp phải được consumer nhìn thấy ngay, hoặc `close()` đúng lúc kết thúc. Tránh tình huống reader chờ data trong pipe nhưng writer lại chờ reader trong khi dữ liệu vẫn nằm ở buffer của writer.

## 5. Pipe chỉ truyền byte: cần framing nếu muốn giao tiếp theo thông điệp

Giả sử producer muốn gửi hai chuỗi `"A"` và `"Xin chào"`. Nếu cứ nối byte UTF-8 liên tiếp, consumer không biết đâu là ranh giới chuỗi đầu tiên. Một cách rõ ràng là gửi:

1. Số nguyên `int` 4 byte (`writeInt`) biểu diễn độ dài **byte** của payload.
2. Đúng số byte payload.
3. Lặp lại cho message kế tiếp.
4. Gửi sentinel chiều dài `-1` để kết thúc giao thức ví dụ này, rồi đóng output.

Consumer dùng `readInt()` đọc kích thước, kiểm tra phạm vi, rồi `readFully()` để nhận **đúng** số byte. `readFully()` tự gọi đọc nhiều lần nếu cần và ném `EOFException` khi stream đóng quá sớm; không nhầm short read thành message hoàn chỉnh.

```java
// PipeMessagesDemo.java
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PipeMessagesDemo {
    private static void send(DataOutputStream writer, String text)
            throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        writer.writeInt(payload.length);
        writer.write(payload);
    }

    public static void main(String[] args) throws Exception {
        PipedInputStream input = new PipedInputStream(8);
        PipedOutputStream output = new PipedOutputStream(input);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> producer = executor.submit(() -> {
                try (DataOutputStream writer = new DataOutputStream(output)) {
                    send(writer, "A");
                    send(writer, "Xin chào");
                    writer.writeInt(-1);
                }
                return null;
            });

            try (DataInputStream reader = new DataInputStream(input)) {
                int messages = 0;

                while (true) {
                    int length = reader.readInt();

                    if (length == -1) {
                        break;
                    }
                    if (length < 0 || length > 1024) {
                        throw new IOException("Invalid message length");
                    }

                    byte[] payload = new byte[length];
                    reader.readFully(payload);
                    System.out.println("message="
                            + new String(payload, StandardCharsets.UTF_8));
                    messages++;
                }

                producer.get();
                System.out.println("count=" + messages);
            }
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac -encoding UTF-8 PipeMessagesDemo.java
java PipeMessagesDemo
```

Đầu ra:

```text
message=A
message=Xin chào
count=2
```

Đây là một **giao thức ví dụ do ứng dụng định nghĩa**, không phải định dạng mặc định của `PipedInputStream`/`PipedOutputStream`. Sentinel `-1` chỉ có ý nghĩa vì hai đầu đã thỏa thuận trước; `readInt()` **không** coi `-1` là EOF của pipe. Nếu producer đóng trước khi gửi đủ header hoặc payload, consumer nhận `EOFException`; đó là lỗi thông điệp chưa hoàn chỉnh, khác với sentinel bình thường.

Kiểm tra `length` trước khi cấp phát mảng để tránh một nguồn lỗi/cố ý gửi kích thước rất lớn gây cạn bộ nhớ. Trong thực tế, cần thêm quy tắc về version, checksum, timeout, lỗi nội dung và giới hạn ứng dụng.

Có thể thay framing bằng newline khi **quy ước nội dung không chứa newline không escape**, hoặc dùng `InputStreamReader`/`BufferedReader` cho text protocol. Khi nội dung có dấu phân cách/escape phức tạp, parser phải hiểu grammar; bản thân pipe vẫn chỉ là phương tiện vận chuyển byte.

## 6. Byte, ký tự và UTF-8: tránh giải mã từng khối tùy ý

`PipedInputStream`/`PipedOutputStream` **không tự hiểu Unicode**. UTF-8 chuyển ký tự thành 1–4 byte, vì vậy một lần `read(buffer)` có thể dừng **giữa** các byte của một ký tự.

Không nên làm:

```java
// MÃ MINH HỌA SAI: có thể giải mã từng chunk UTF-8 bị cắt giữa ký tự.
byte[] buffer = new byte[2];
int n;
while ((n = input.read(buffer)) != -1) {
    System.out.print(new String(buffer, 0, n, StandardCharsets.UTF_8));
}
```

Đoạn trên giả định `input` là stream đã mở. `new String(...)` trên mỗi chunk dùng **decoder mới**; nếu một ký tự đa byte bị cắt, kết quả có thể là ký tự thay thế `�` thay vì văn bản gốc.

Hai lựa chọn đúng theo nhu cầu:

- **Tầng byte:** giữ nguyên các byte của payload đến khi đủ message (như `readFully` trong ví dụ trên), rồi giải mã *toàn bộ payload* bằng UTF-8.
- **Tầng ký tự:** bọc `PipedInputStream` bằng `InputStreamReader(input, StandardCharsets.UTF_8)` một lần, có thể bọc thêm `BufferedReader`. Decoder khi ấy giữ trạng thái qua nhiều lần đọc. Nếu muốn truyền ký tự phía ghi, bọc `PipedOutputStream` bằng `OutputStreamWriter(output, StandardCharsets.UTF_8)`.

Dùng `Reader`/`Writer` cũng không tự tạo ranh giới message. Cần delimiter hoặc framing ở tầng ứng dụng và `flush()` thích hợp nếu writer có bộ đệm.

## 7. Lỗi thường gặp, ngắt luồng và chẩn đoán deadlock

### 7.1. Viết trước, đọc sau trên cùng một thread

Nếu producer ghi vượt sức chứa mà chưa có consumer chạy, `write()` có thể chặn mãi. **Khắc phục:** chạy producer và consumer đồng thời; không gọi `join()` hoặc `Future.get()` trên producer **trước khi** consumer bắt đầu đọc nếu producer có thể bị kẹt vì buffer đầy.

### 7.2. Không đóng output

Nếu producer đã gửi byte cuối nhưng vẫn để đầu output mở, consumer tiếp tục chờ ở lần `read()` tiếp theo. Không dùng `available() == 0` để kết luận đã hết: `available()` chỉ phản ánh số byte có thể đọc mà không chặn tại thời điểm kiểm tra, **không chứng minh EOF**. Khắc phục: thiết kế kết thúc hợp lệ, đóng writer trong `finally`/`try-with-resources`, hoặc có giao thức message kết thúc rõ ràng.

### 7.3. Đóng input quá sớm

Consumer gặp lỗi hoặc tự thoát khi writer đang gửi sẽ khiến writer có thể nhận `IOException`. Khắc phục: xác định rõ chính sách hủy, đóng các đầu thích hợp để đánh thức tác vụ đang chờ khi có lỗi, truyền lỗi của hai phía về nơi điều phối. Không bỏ qua `Future.get()` trong code sản xuất nếu lỗi producer quan trọng.

### 7.4. Đầu pipe chưa nối hoặc đã nối hai lần

Các thao tác đọc/ghi trên đầu chưa nối có thể gây `IOException` (thông điệp tùy môi trường). Nối lại hai đầu đã nối cũng không hợp lệ. Khắc phục: cấu hình cặp input/output **một lần** trước khi khởi động thread, bàn giao cho hai phía sử dụng.

### 7.5. Thread chết bất thường

Pipe trong Java có kiểm tra trạng thái thread đọc/ghi trong một số tình huống; khi thread đầu kia kết thúc không đúng cách, có thể phát sinh `IOException` dạng broken/dead pipe. **Không** xem việc phát hiện thread chết là tín hiệu kết thúc nghiệp vụ tin cậy. Cần đóng bình thường, truyền exception qua `Future`/cơ chế điều phối và định nghĩa protocol rõ ràng.

### 7.6. Interrupt và hủy tác vụ

Các thao tác pipe blocking có thể phản ứng với interrupt bằng `InterruptedIOException` hoặc lỗi liên quan theo API/implementation. Trong chương trình cần hủy được, hãy thiết kế rõ cách ngắt công việc, đóng pipe để giải phóng những thread đang chờ, và không nuốt lỗi interrupt. `Future.cancel(true)` chỉ gửi yêu cầu ngắt nếu worker đang chạy; không nên giả định nó tự giải phóng mọi resource hoặc đảm bảo thoát tức thì.

### 7.7. Nhiều producer/consumer dùng chung một pipe

Pipe nhắm đến mô hình giao tiếp giữa các thread, thường là **một writer và một reader** cho mỗi cặp. Dùng nhiều writer không biến các lần `write()` thành những thông điệp nguyên tử; dữ liệu có thể xen kẽ ở mức ứng dụng khi các lần ghi xen nhau. Dùng nhiều consumer có thể chia nhau byte, không phải mỗi consumer đều nhận một bản sao. Nếu cần nhiều nhà sản xuất/tiêu thụ với ranh giới phần tử và điều phối rõ ràng, cân nhắc `BlockingQueue<T>` hoặc cơ chế concurrent phù hợp.

### 7.8. Dùng pipe để thay hàng đợi thông điệp

`PipedInputStream` xử lý **byte**. Nó không có `put(Message)`, cơ chế broadcast, retry, ack, persist hay schema. Nếu mục tiêu là chuyển các object hoặc task giữa worker, `BlockingQueue<T>` thường trực tiếp và dễ kiểm soát hơn. Nếu mục tiêu là kết nối một API sản xuất `OutputStream` vào API tiêu thụ `InputStream` mà không tạo file trung gian, pipe lại rất hữu ích.

## 8. So sánh với các giải pháp I/O trong bộ nhớ khác

| Giải pháp | Dạng dữ liệu | Có blocking vì chờ đầu kia? | Mục đích tiêu biểu |
| --- | --- | --- | --- |
| `ByteArrayOutputStream` / `ByteArrayInputStream` | Mảng byte | Không theo cơ chế pipe | Tạo dữ liệu rồi đọc lại trong RAM, không cần producer/consumer chạy cùng lúc. |
| `PipedOutputStream` / `PipedInputStream` | Byte stream nối hai đầu | **Có**: đọc đợi dữ liệu, ghi có thể đợi chỗ trống | Streaming dữ liệu giữa producer và consumer trong JVM. |
| `StringWriter` / `StringReader` | Ký tự trong bộ nhớ | Không theo cơ chế pipe | Xây dựng và xử lý chuỗi ký tự. |
| `BlockingQueue<T>` | Phần tử kiểu `T` | **Có**, tùy thao tác và dung lượng queue | Bàn giao message/task giữa thread, giữ ranh giới phần tử. |
| `java.nio.channels.Pipe` | Byte qua `SourceChannel`/`SinkChannel` | Có thể cấu hình chế độ blocking của channel theo API | Kết hợp với mô hình NIO và channel. |

`java.nio.channels.Pipe` cũng là cơ chế truyền byte một chiều, nhưng là API channel với các đặc tính và phương thức khác. Không đồng nhất nó với cặp `java.io.PipedInputStream`/`PipedOutputStream`; đặc biệt cách chờ, tương tác selector và các lựa chọn thiết kế cần xem theo từng channel/provider.

### Khi nào nên chọn `PipedInputStream`/`PipedOutputStream`?

Phù hợp khi:

- Cần nối một API **ghi `OutputStream`** với một API **đọc `InputStream`** trong cùng ứng dụng.
- Muốn producer phát dữ liệu dần và consumer xử lý đồng thời, không cần tạo file tạm hay giữ toàn bộ payload trong RAM.
- Chấp nhận mô hình byte stream, backpressure và việc quản lý vòng đời thread.

Không phù hợp khi:

- Chỉ cần đọc lại byte đã tạo xong: `ByteArrayOutputStream` và `ByteArrayInputStream` đơn giản hơn.
- Cần hàng đợi các object có ranh giới message, nhiều producer/consumer: dùng queue.
- Cần giao tiếp giữa **hai tiến trình** hoặc máy khác: dùng cơ chế IPC/network tương ứng; các lớp `Piped*Stream` này không phải named pipe của hệ điều hành.
- Cần tốc độ/độ phức tạp concurrency cao, timeout, cancellation và quản lý lỗi chuyên biệt: có thể cần channel, queue hoặc pipeline concurrency được thiết kế rõ.

## 9. Checklist thiết kế giao tiếp bằng pipe

Trước khi triển khai, xác định:

1. **Ai tạo/kết nối pipe?** Kết nối hai đầu đúng một lần, trước khi các worker sử dụng.
2. **Ai là producer/consumer?** Chạy hai phía đồng thời trên các thread phù hợp; không tự tạo tình huống writer chờ reader trong cùng thread.
3. **Kích thước bộ đệm bao nhiêu?** Cân đối RAM, lưu lượng, tần suất blocking. Không dùng dung lượng buffer làm ranh giới message.
4. **Định dạng dữ liệu là gì?** Byte thô, charset được xác định, hay protocol có length prefix/delimiter? Nếu có message, chỉ rõ framing.
5. **Điều kiện kết thúc?** Producer đóng output; consumer đọc tới EOF hoặc giải mã sentinel protocol được quy định.
6. **Ai đóng resource?** Mỗi phía sở hữu đầu của mình và wrapper ngoài cùng; đóng đúng thứ tự.
7. **Lỗi ở một phía được truyền thế nào?** Dùng `Future.get()`, shared error state hoặc tầng điều phối; xử lý hủy để không bỏ lại thread bị chặn.
8. **Có giới hạn input không?** Nếu đọc length prefix, kiểm tra độ dài trước khi cấp phát; nếu streaming dữ liệu ngoài, giới hạn tổng bytes khi cần.
9. **Có dùng buffered/character wrapper không?** Hiểu `flush()` và charset; tránh deadlock do dữ liệu còn trong buffer writer.
10. **Có thực sự cần byte stream không?** Nếu giao tiếp ở cấp độ object/task, cân nhắc `BlockingQueue`.

## 10. Câu hỏi tự kiểm tra

**1.** Pipe có thể dùng để thread A ghi byte và thread B đọc byte ngay khi dữ liệu được phát sinh không?

**Đáp án:** Có. Kết nối `PipedOutputStream` với `PipedInputStream`; chạy producer và consumer đồng thời. Đây là một byte stream một chiều.

**2.** Pipe có 4 byte đệm; một thread đơn lẻ gọi `write` 100 byte rồi mới gọi `read`. Có an toàn không?

**Đáp án:** Không. Lệnh ghi có thể bị chặn khi buffer đầy mà không có consumer đang chạy để giải phóng chỗ.

**3.** Bên đọc không còn byte sẵn trong buffer, nhưng bên ghi vẫn mở. `read()` bắt buộc trả `-1`?

**Đáp án:** Không. Nó có thể chờ dữ liệu. EOF `-1` thường xuất hiện sau khi bên ghi đã đóng và dữ liệu đệm đã được tiêu thụ.

**4.** Producer gọi `write(byte[])` một lần. Consumer có chắc nhận toàn bộ array trong một lần `read(byte[])`?

**Đáp án:** Không. Pipe không bảo toàn ranh giới các lần `write` thành message. Consumer phải dùng giao thức framing và lặp đọc để lấy đủ dữ liệu.

**5.** Nếu gửi văn bản UTF-8, có nên chuyển từng chunk tùy ý thành `String` riêng?

**Đáp án:** Không. Ký tự đa byte có thể bị tách ở biên chunk. Dùng decoder giữ trạng thái (`InputStreamReader`) hoặc ghép đủ payload byte rồi mới decode.

**6.** `flush()` trên writer có báo “đã hết stream” cho consumer không?

**Đáp án:** Không. `flush()` đẩy dữ liệu đang đệm; producer phải đóng đầu ghi hoặc gửi tín hiệu kết thúc theo giao thức ứng dụng.

**7.** Khi gửi các object/tác vụ nhiều producer tới nhiều consumer, pipe có phải lựa chọn đầu tiên không?

**Đáp án:** Thường không. `BlockingQueue<T>` quản lý phần tử và điều phối producer/consumer thuận tiện hơn. Pipe phù hợp hơn với byte stream.

**8.** Vì sao trong ví dụ `PipeHelloDemo` gọi `producer.get()` **sau** vòng đọc?

**Đáp án:** Nếu gọi trước, thread main có thể chờ writer hoàn tất trong khi writer đang chờ main đọc để giải phóng bộ đệm, gây deadlock. Đọc đồng thời rồi mới đợi Future kết thúc giúp hai phía tiến triển.

**Ghi nhớ:** `PipedOutputStream` + `PipedInputStream` là **ống dẫn byte một chiều, có blocking và bộ đệm hữu hạn, hoạt động giữa các thread của cùng JVM**. Kết nối trước khi chạy, đọc/ghi đồng thời, đóng phía ghi để consumer nhận EOF, và tự định nghĩa framing nếu cần thông điệp. Khi vấn đề ở cấp độ object/task chứ không phải byte stream, hãy cân nhắc hàng đợi concurrent.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="in-memory-io-008">PipedReader, PipedWriter và nguy cơ bế tắc khi dùng không đúng</a>

<details>
<summary>Xem chi tiết</summary>

# PipedReader, PipedWriter và nguy cơ bế tắc khi dùng không đúng

> **Ngữ cảnh:** Java Core → I/O trong bộ nhớ. Bài học giải thích một cặp luồng ký tự nối hai tác vụ trong cùng JVM, vì sao đọc/ghi có thể chặn, trường hợp tạo bế tắc khi vận hành không đúng và cách tổ chức producer–consumer để có điểm kết thúc, dọn tài nguyên và báo lỗi rõ ràng. Các lớp này thuộc `java.io` từ Java 1.1; ví dụ độc lập bên dưới dùng Java 17.

## 1. Bản chất của pipe ký tự

`PipedWriter` là **đầu ghi** ký tự; `PipedReader` là **đầu đọc**. Sau khi kết nối hai đầu thành một pipe, các `char` được ghi ở đầu writer sẽ được lấy ra lần lượt ở đầu reader. Pipe chứa một **bộ đệm hữu hạn** nên hai phía có thể hoạt động với tốc độ khác nhau trong phạm vi dung lượng bộ đệm. Nó thường dùng cho giao tiếp producer–consumer **giữa các thread trong cùng JVM**. Không phải socket, không phải file trên ổ đĩa và không phải giải pháp gửi dữ liệu qua các process.

~~~text
Thread PRODUCER                       Thread CONSUMER
       |                                     ^
       | PipedWriter.write(...)              | PipedReader.read(...)
       v                                     |
       +-------------------------------------+
       |          bộ đệm char hữu hạn        |
       +-------------------------------------+
          rỗng → read phải chờ dữ liệu
          đầy  → write phải chờ có chỗ trống
          writer.close() → reader có thể tới EOF
~~~

Đối tượng truyền trong pipe là **UTF-16 code unit** theo `Reader`/`Writer`, không phải byte đã encode. Đối với tiếng Việt thông thường nhiều code point BMP, một `char` thường biểu diễn một code point; với emoji ngoài BMP, một code point cần **hai `char`** tạo surrogate pair. Nếu cần luân chuyển **byte nhị phân**, dùng `PipedInputStream` và `PipedOutputStream` hoặc giải pháp phù hợp khác.

### So với StringReader/StringWriter và queue

| Cách truyền | Phù hợp khi | Giới hạn |
| --- | --- | --- |
| `StringReader` / `CharArrayReader` | Một chuỗi/array đã có sẵn ở bộ nhớ, đọc lại cục bộ | Không tạo liên kết streaming tới thread ghi đang sản xuất. |
| `StringWriter` / `CharArrayWriter` | Thu thập output ký tự thành bộ đệm tăng dần rồi đọc sau | Có thể giữ toàn bộ nội dung trong RAM; không tự cung cấp blocking producer–consumer. |
| `PipedReader` + `PipedWriter` | Một producer tạo chuỗi ký tự tăng dần và consumer đọc như `Reader` | Bounded pipe; có thể chặn và bế tắc nếu không có thread đối ứng. |
| `BlockingQueue<String>` | Producer–consumer truyền **message** có ranh giới rõ | Phải tự chọn capacity, giao thức kết thúc và xử lý lỗi; không phải Reader/Writer. |
| `PipedInputStream` + `PipedOutputStream` | Kết nối bên gửi/nhận **byte** | Không tự giải mã charset; thường cũng cần hai thread. |

**Chọn đúng trừu tượng:** Nếu parser chỉ nhận `Reader` và dữ liệu được tạo dần từ một tác vụ khác, pipe ký tự có thể dùng được. Nếu giao tiếp là hàng đợi sự kiện hoặc từng message độc lập, một queue có giới hạn dung lượng cùng cơ chế đóng/kết thúc rõ ràng thường đơn giản hơn. Nếu chỉ cần trả về một `String` đã tạo sẵn, pipe có thể phức tạp không cần thiết.

## 2. Tạo cặp pipe và kết nối chính xác một lần

Có hai mẫu hợp lệ.

**Kết nối ngay bằng constructor:**

~~~java
// Fragment đặt trong phương thức có thể ném IOException.
PipedReader reader = new PipedReader(1024);
PipedWriter writer = new PipedWriter(reader);
~~~

**Hoặc khởi tạo rời rồi connect:**

~~~java
// Fragment đặt trong phương thức có thể ném IOException.
PipedReader reader = new PipedReader();
PipedWriter writer = new PipedWriter();
writer.connect(reader);       // Tương đương reader.connect(writer).
~~~

Đây là các **fragment**; để biên dịch cần import `java.io.PipedReader`, `java.io.PipedWriter` và khai báo/bắt `IOException` ở chỗ kết nối. Hai cách trên có ý nghĩa tương tự: writer và reader tạo thành một cặp giao tiếp một chiều.

- Một reader/writer **chưa kết nối** không dùng được cho thao tác đọc/ghi bình thường; có thể gặp `IOException` khi dùng.
- Gọi `connect` lần thứ hai sau khi cặp đã được kết nối (hoặc cố nối đầu đó với đối tượng khác) gây `IOException`. **Không** tạo lại cặp kết nối như một “reset”.
- `new PipedReader(int pipeSize)` cho biết kích thước buffer theo **số `char`**, yêu cầu `pipeSize > 0`; giá trị bằng 0 hoặc âm gây `IllegalArgumentException`. Overload chỉ định buffer có từ Java 6.
- `new PipedReader(writer, pipeSize)` tạo reader với buffer kích thước được chọn và kết nối nó với writer chưa kết nối. `new PipedWriter(reader)` là cách trực quan để kết nối vào một reader đã có.
- Buffer mặc định trong OpenJDK thông thường là 1.024 `char`, nhưng **không cần dựa vào con số mặc định để chứng minh chương trình không deadlock**. Mọi buffer đều hữu hạn, và tốc độ producer có thể vượt tốc độ consumer.

### 2.1. Trạng thái quan trọng

~~~text
UNCONNECTED
   └── connect một lần ──> CONNECTED
                            |       |
                            |       └── reader.close() → phía đọc không còn dùng được
                            └── writer.close() → phía ghi kết thúc,
                                                  reader đọc nốt buffer rồi EOF
~~~

Đây là mô hình khái niệm, **không phải** sơ đồ trạng thái chính thức đầy đủ của implementation. Writer đóng để báo không còn dữ liệu; reader đóng để ngừng tiêu thụ. Không coi một đầu đã đóng là có thể kết nối lại hoặc dùng lại để mở phiên mới.

## 3. Blocking và backpressure: nguyên nhân đầu tiên dẫn tới kẹt

Pipe có một vùng đệm hữu hạn. Hai quy tắc cơ bản:

1. `PipedReader.read()` có thể **chặn** cho đến khi có ít nhất một ký tự, EOF được phát hiện hoặc có lỗi. Khi writer đã đóng bình thường và buffer được đọc hết, `read()` trả `-1`.
2. `PipedWriter.write(...)` có thể **chặn** nếu bộ đệm đã đầy và phải đợi consumer đọc bớt. Với `write(char[], off, len)`, thao tác phải viết đủ số ký tự yêu cầu hoặc ném lỗi; một lần ghi dài có thể phải chờ nhiều vòng.

Đó là **backpressure**: producer nhanh bị giới hạn bởi khả năng nhận của consumer. Nó có thể hữu ích vì không phải lưu toàn bộ dữ liệu vào bộ nhớ, nhưng đòi hỏi producer và consumer có khả năng chạy tiến triển **độc lập**.

Giả sử buffer cho phép giữ 4 `char`:

~~~text
Producer ghi 'A','B','C','D' → [ A B C D ] (đầy)
Producer muốn ghi 'E'       → WAIT đến khi consumer lấy bớt
Consumer đọc 'A','B'        → [ C D _ _ ]
Producer có chỗ để ghi tiếp → tiến triển
~~~

Nếu consumer **chưa được chạy** hoặc đang chờ một việc chỉ producer mới hoàn thành được, thời gian chờ có thể thành vô hạn. Nếu producer **không bao giờ ghi hoặc không bao giờ đóng** mà consumer cứ đọc, consumer có thể chờ vô hạn. Dùng `Thread.sleep(...)` không sửa quan hệ phụ thuộc này.

## 4. Ba kiểu bế tắc/tắc nghẽn thường gặp

### 4.1. Cùng một thread gọi read trước write

~~~java
// PHẢN VÍ DỤ: KHÔNG CHẠY như một chương trình.
PipedReader reader = new PipedReader(4);
PipedWriter writer = new PipedWriter(reader);
int first = reader.read();       // Có thể CHỜ vì pipe trống.
writer.write('A');               // Không bao giờ được thực hiện.
~~~

Thread bị kẹt tại `reader.read()` khi đang chờ một ký tự mà **chính nó** định ghi ở dòng sau. JavaDocs khuyến nghị hai đầu pipe được vận hành bởi hai thread khác nhau; dùng hai đầu trong cùng một thread có thể deadlock.

### 4.2. Cùng thread ghi quá sức chứa trước khi đọc

~~~java
// PHẢN VÍ DỤ: KHÔNG CHẠY như một chương trình.
PipedReader reader = new PipedReader(4);
PipedWriter writer = new PipedWriter(reader);
writer.write("ABCDEFGHIJ");     // Dài hơn dung lượng 4 char; có thể kẹt.
char[] block = new char[10];
int n = reader.read(block);      // Không tới được đây để giải phóng buffer.
~~~

Một `write(String)` có thể phải truyền nhiều phần xuống pipe. Khi đầy, writer chờ reader lấy bớt, nhưng reader lại nằm sau lệnh ghi trên **cùng thread**. Nếu ví dụ ghi ít hơn dung lượng rồi đọc, chương trình **có thể** chạy được trong một số điều kiện; điều đó không biến thiết kế cùng-thread thành an toàn cho input có kích thước không giới hạn.

### 4.3. Main thread chờ producer kết thúc trước khi đọc

~~~java
// PHẢN VÍ DỤ: KHÔNG CHẠY nếu producer sẽ ghi quá buffer.
producer.start();
producer.join();                 // Main chờ producer hoàn thành.
while (reader.read() != -1) { }  // Nhưng consumer chỉ bắt đầu sau join.
~~~

Producer đang chờ có chỗ trống trong buffer, còn main chờ producer thoát; main không đọc được vì bị giữ ở `join()`. Đây là **vòng phụ thuộc chờ**. Muốn chạy đúng, **bắt đầu producer, tiêu thụ đồng thời, sau đó mới join** khi đã đạt EOF hoặc hoàn tất việc dọn tài nguyên. Dù đã dùng **hai thread**, đặt `join` sai vị trí vẫn có thể làm chương trình kẹt.

### Deadlock so với blocking hợp lệ

Một lần `read` đợi vài mili giây để producer sinh ra dữ liệu là **blocking hợp lệ**, chưa phải deadlock. Deadlock xảy ra khi các tác vụ tạo vòng phụ thuộc mà không tác vụ nào có thể tiếp tục. Với pipe, cũng có thể xảy ra **indefinite wait** khi một phía còn sống nhưng không gửi/đóng, hoặc thread xử lý bị ngừng mà phía khác chưa được thông báo. Đừng gộp mọi thời gian chờ thành deadlock; phải xem **ai đang chờ điều kiện gì và ai có khả năng tạo điều kiện đó**.

## 5. Ví dụ độc lập đúng: producer và consumer chạy đồng thời

Tạo tệp **`PipeCharsDemo.java`**:

~~~java
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.util.concurrent.atomic.AtomicReference;

public class PipeCharsDemo {
    public static void main(String[] args) throws Exception {
        // Buffer chỉ có 4 char; producer sẽ gửi nhiều hơn 4 char.
        PipedReader reader = new PipedReader(4);
        PipedWriter writer = new PipedWriter(reader);
        AtomicReference<IOException> producerError = new AtomicReference<>();

        Thread producer = new Thread(() -> {
            try (writer) {
                writer.write("HELLO\nJAVA");
                // Không cần dùng sleep() để phối hợp.
            } catch (IOException error) {
                producerError.set(error);
            }
            // try-with-resources đóng writer ngay khi producer xong.
        }, "pipe-producer");

        producer.start(); // Producer có cơ hội ghi/đợi trên thread riêng.

        StringBuilder received = new StringBuilder();
        try (reader) {
            char[] buffer = new char[3];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                received.append(buffer, 0, count);
            }
        }

        producer.join(); // Chỉ chờ sau khi đã tiêu thụ tới EOF.
        if (producerError.get() != null) {
            throw producerError.get(); // Không nuốt lỗi producer.
        }

        System.out.println("received="
                + received.toString().replace('\n', '|'));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 PipeCharsDemo.java
java PipeCharsDemo
~~~

**Stdout của lần chạy thành công:**

~~~text
received=HELLO|JAVA
~~~

Đây là ví dụ có đầu vào cố định; thời điểm producer và consumer được scheduler chạy **không xác định**, nhưng output giữ thứ tự ký tự. Đã kiểm tra ví dụ này trên JDK bằng cơ chế chạy Java tương đương.

Từng phần có ý nghĩa riêng:

1. `PipedReader(4)` chủ động đặt buffer nhỏ để chuỗi 10 ký tự không thể nằm toàn bộ trong buffer một lúc.
2. `new PipedWriter(reader)` tạo **đúng một kết nối**.
3. `producer.start()` cho phép thread ghi chạy song song với main đang đọc. Việc producer bị chặn khi buffer đầy **không chặn main** tiêu thụ.
4. `read(buffer)` trả số `char` thật đọc; phải append `count` ký tự thay vì toàn bộ mảng, vì lần đọc cuối có thể không đầy buffer.
5. Kết thúc khối `try (writer)` gọi `close()` ở phía ghi, giúp reader nhận **EOF** khi đọc hết dữ liệu đang đệm.
6. `producer.join()` thực hiện **sau vòng đọc**, nên main không chờ producer khi chính producer còn cần main tiêu thụ. `AtomicReference` giúp lỗi `IOException` từ producer được báo về main, thay vì biến mất trong thread khác.

**Ranh giới của demo:** Đây là đường chạy thành công minh họa quan hệ producer–consumer. Nếu consumer ném exception trước khi đọc hết hoặc bị hủy, chương trình production cần chiến lược **đóng đầu đọc, truyền tín hiệu hủy, dừng producer và join có giới hạn**, không để thread ghi mắc kẹt chờ buffer. Phần dưới trình bày nguyên tắc đó. Không áp dụng `Thread.stop()` để xử lý.

## 6. Vì sao phải đóng writer để reader nhận EOF?

Hãy phân biệt ba trạng thái:

- **Buffer rỗng, writer còn mở:** reader có thể đợi vì producer vẫn có thể gửi thêm.
- **Buffer còn ký tự, writer đã đóng bình thường:** reader vẫn đọc những ký tự đang có; sau khi hết buffer, `read()` trả `-1`.
- **Reader đã đóng:** đầu nhận không còn tiêu thụ; writer đang hoặc sẽ ghi có thể nhận `IOException`. Đóng reader **không phải** thủ tục báo hoàn thành bình thường cho một luồng dữ liệu đã được gửi trọn.

Sai lầm thường gặp:

~~~java
// Producer đã ghi xong nhưng không close().
writer.write("done");
// Reader khác vẫn đang while ((n = reader.read(...)) != -1)
// sẽ không nhất thiết thấy EOF; nó còn chờ "dữ liệu tiếp theo".
~~~

**Quy tắc thực hành:** trong mô hình một producer sở hữu writer, đóng writer bằng `try-with-resources` hoặc `finally` sau khi thực sự hoàn tất. Đừng chỉ `flush()` rồi chờ reader tự hiểu đã kết thúc.

`PipedWriter.flush()` thông báo cho các reader đang đợi biết dữ liệu trong pipe; khi đặt `BufferedWriter` phía ngoài, `bufferedWriter.flush()` còn đẩy dữ liệu từ buffer của wrapper xuống writer bên dưới. Nhưng **flush không phải tín hiệu EOF** và không tự đóng writer. Với một stream ký tự kéo dài, hãy có giao thức xác định thời điểm hoàn thành hoặc message kết thúc nếu cần, thay vì “không thấy dữ liệu nữa nên tự thoát”.

## 7. Có thật sự cần `flush()` không?

Dưới góc nhìn API:

- `PipedWriter` có phương thức `flush()` và tài liệu nêu nó đánh thức các reader đang đợi khi ký tự đang chờ trong pipe.
- `BufferedWriter` bọc bên ngoài có buffer **riêng**; một lần `write` có thể mới nằm ở đó. Khi consumer cần nhận ngay mà writer còn mở, `flush()` trên **BufferedWriter** chuyển ký tự xuống pipe.
- `close()` trên writer/wrapper cũng hoàn tất vòng đời ghi, thường flush dữ liệu ở tầng wrapper rồi đóng đích theo hợp đồng của lớp đó.
- `flush()` không bảo đảm producer đọc hết nguồn, không giải phóng capacity nếu consumer không đọc, và không khắc phục tình huống producer đang kẹt vì buffer đầy trong cùng thread.

Ví dụ fragment có buffering bên ngoài:

~~~java
// Fragment: pipe là PipedWriter đã kết nối.
try (java.io.BufferedWriter out =
        new java.io.BufferedWriter(pipe)) {
    out.write("part-1");
    out.flush(); // Đẩy dữ liệu từ BufferedWriter xuống pipe.
    out.write("part-2");
} // Đóng wrapper, và thông thường đóng writer bên trong.
~~~

Fragment này vẫn cần consumer đồng thời. Nếu consumer không chạy, dữ liệu có thể khiến `flush` hoặc `close` phải chờ khi buffer đầy; thêm `flush()` không phải một cách né deadlock.

## 8. Thread chết và khái niệm broken pipe

`PipedReader.read()` có thể ném `IOException` khi pipe **broken**, chưa connect, đã đóng hoặc I/O gặp lỗi. `PipedWriter.write(...)` cũng có thể báo pipe broken, chưa connect hoặc đóng. Một số implementation phát hiện rằng **thread từng thực hiện read/write không còn sống** và có thể báo lỗi “Read end dead” hoặc “Write end dead”.

Tuy nhiên, **không nên dùng việc phát hiện thread chết làm giao thức kết thúc chương trình**:

- Producer có thể kết thúc **mà không close writer**, khiến consumer đợi hoặc gặp lỗi tùy trạng thái; không nên xem đó là EOF hợp lệ.
- Consumer ngừng nhận dữ liệu mà producer vẫn còn ghi có thể khiến producer chờ buffer hoặc nhận lỗi; caller phải quyết định hủy/dọn dẹp.
- Kiểu lỗi và thời điểm phát hiện phụ thuộc trạng thái, scheduling và chi tiết implementation; đừng assert chính xác chuỗi exception message trong test.
- Một thread được coi còn sống nhưng không có kế hoạch ghi thêm cũng có thể khiến consumer đợi vô thời hạn; kiểm tra `isAlive()` đơn thuần không đủ chứng minh tiến triển.

Vì vậy, **close writer ở đường thành công**, còn ở đường thất bại cần **báo exception riêng**, đóng endpoints và quản lý task lifecycle có chủ đích. Việc consumer nhận EOF không tự bảo đảm producer đã hoàn tất thành công — xem thêm `Future.get()`, `join()` + kênh báo lỗi, hoặc cơ chế điều phối của ứng dụng.

## 9. Xử lý lỗi của producer không được im lặng

Mã chạy ở thread khác không tự truyền exception lên thread gọi `start()`. Nếu producer ném lỗi mà consumer cứ đợi thêm input, luồng đọc có thể bị treo hoặc báo broken pipe, làm mất **nguyên nhân gốc**. Trong ví dụ `PipeCharsDemo`, `AtomicReference<IOException>` giữ lỗi của producer và được kiểm tra sau khi `join()`. Thiết kế mở rộng có thể dùng:

- `ExecutorService.submit(...)` trả `Future<?>`. `future.get()` xác nhận task hoàn tất và nhận `ExecutionException` có cause, nhưng không được gọi `get()` **trước khi** consumer tiêu thụ đủ nếu producer còn có thể chặn vì pipe đầy.
- Một cấu trúc truyền kết quả/lỗi rõ ràng trong pipeline; reader nhận EOF rồi caller còn phải kiểm tra producer có thành công không.
- Cơ chế hủy toàn bộ tác vụ khi reader hoặc writer gặp lỗi, đóng đúng endpoints, sau đó chờ task dừng trong thời hạn phù hợp.
- Đường đi của exception cả hai phía, không chỉ producer: consumer `IOException` cũng có thể yêu cầu báo cho producer dừng.

**Không** hứa rằng `Thread.interrupt()` luôn giải phóng ngay mọi blocked I/O trong mọi JDK/provider. Nếu buộc phải hủy, kiểm tra hành vi interrupt của API cụ thể, có chiến lược close/resource cleanup, quản lý timeout ở **cấp task** và tránh để thread chờ không giới hạn.

## 10. Có thể vừa đọc vừa ghi trong một thread không?

Đối với nguồn nhỏ đã biết và không lấp đầy buffer, bạn **có thể quan sát** một chương trình dùng một thread mà vẫn chạy. Nhưng API Java cảnh báo dùng reader và writer của cặp pipe trong cùng thread **không được khuyến nghị** do nguy cơ deadlock; việc chạy được với chuỗi ngắn không đủ để an toàn với payload dài hơn, tốc độ thay đổi hoặc xử lý newline.

Nếu có một thread duy nhất và dữ liệu đã có sẵn:

- Chọn `StringReader` để trình parser một `Reader` sau khi tạo hoàn chỉnh chuỗi.
- Chọn `CharArrayReader`/`CharArrayWriter` khi muốn dữ liệu ở dạng `char[]`.
- Dùng `StringWriter` để gom dữ liệu rồi `new StringReader(writer.toString())` nếu thực sự cần giao diện Reader.
- Đối với file có thể đọc lại, sử dụng `Files.newBufferedReader` hoặc nguồn file phù hợp.

Dùng pipe để “chuyển String sang Reader” trên cùng thread chỉ tăng nguy cơ mắc kẹt trong khi `new StringReader(text)` đã đáp ứng bài toán.

## 11. Bộ đệm hữu hạn và chọn pipeSize

`pipeSize` là số **UTF-16 code unit** trong bộ đệm reader. Khi producer có thể gửi `String` dài không xác định, không có kích thước buffer cố định nào đủ để đảm bảo writer **không bao giờ phải chờ**. Mục tiêu của bounded buffer chính là điều tiết tốc độ hai phía.

Quy tắc chọn:

1. Bắt đầu với mức đủ cho một số khối ký tự phù hợp workload. Không đẩy lên hàng triệu ký tự chỉ để che một deadlock do tổ chức thread sai.
2. Đo throughput, thời gian chờ producer và memory pressure với dữ liệu thực tế. Buffer lớn có thể giảm số lần chờ trong tải burst nhưng chiếm RAM và tăng lượng dữ liệu đang nằm chờ.
3. Reader có thể `read(char[],off,len)` ít hơn `len`; mỗi lần chỉ xử lý **số được trả**. Đừng giả định một lần read trả đúng một “message” hoặc một dòng.
4. Nếu dữ liệu có ranh giới message, định nghĩa framing riêng, ví dụ length prefix, newline với escape hợp lệ hoặc queue chứa object; pipe là luồng ký tự tuần tự, không tự bảo toàn ranh giới của từng lời gọi `write`.
5. Khi kích thước bộ đệm quá nhỏ và các thao tác đòi đối phương làm việc ở thứ tự ngược, vấn đề cốt lõi thường là **vòng phụ thuộc**, không phải con số capacity.

**Đối với Unicode:** một emoji ngoài BMP chứa hai UTF-16 code unit; pipe có thể tách hai nửa giữa các lần `read(buffer)`. Nếu muốn hiển thị, phân tích hoặc mã hóa thành UTF-8, phải duy trì trạng thái ký tự qua ranh giới buffer thay vì coi mỗi khối đọc là chuỗi hoàn chỉnh độc lập.

## 12. Không nhầm ready(), read() và EOF

`PipedReader.ready()` báo liệu đầu đọc **đã có ký tự trong circular buffer** để một lời gọi đọc tiếp không bị chặn chờ input ở thời điểm kiểm tra. Nếu trả `false`, nó **không cam kết** rằng lần `read` sau chắc chắn chặn: producer có thể vừa ghi ký tự trong lúc đó.

~~~java
// Fragment: reader đã connect và còn mở.
if (reader.ready()) {
    int ch = reader.read(); // Có thể lấy ký tự đang sẵn.
}
// Nếu ready() là false, KHÔNG được suy ra reader đã EOF.
~~~

Không làm như sau:

~~~java
// PHẢN VÍ DỤ LOGIC:
while (reader.ready()) {
    System.out.print((char) reader.read());
}
// Thoát sớm khi buffer tạm rỗng dù producer sắp ghi tiếp.
~~~

Cách đọc hết luồng đúng là:

~~~java
// Fragment: trong method có thể ném IOException.
char[] block = new char[1024];
int n;
while ((n = reader.read(block)) != -1) {
    process(block, n); // process là hàm do ứng dụng định nghĩa.
}
~~~

Đây là **fragment**, chưa phải chương trình độc lập vì `reader` và `process` do caller cung cấp. Vòng lặp tới `-1` cần writer đóng hợp lệ để dừng. `read(char[], off, 0)` có thể trả 0, nhưng với yêu cầu số lượng dương, method sẽ cố đọc ít nhất một `char` hoặc trả `-1` theo hợp đồng tài liệu.

## 13. Hai đầu pipe và try-with-resources: ai sở hữu tài nguyên?

Cặp pipe cần **hai vòng đời**: producer sở hữu đầu ghi, consumer sở hữu đầu đọc. Cách phân chia này đơn giản hóa trách nhiệm.

| Tình huống | Producer / writer | Consumer / reader |
| --- | --- | --- |
| Chạy bình thường | Ghi từng phần, đóng writer sau khi hoàn tất | Đọc tới `-1`, đóng reader |
| Producer gặp lỗi | Lưu/gửi lỗi, vẫn đóng writer trong `finally` / try-with-resources | Có thể nhận EOF sau phần dữ liệu đã ghi; kiểm tra báo lỗi producer ở cấp điều phối |
| Consumer muốn dừng sớm | Nhận tín hiệu dừng qua controller hoặc phát hiện reader đóng | Đóng reader và thông báo hủy theo chính sách |
| Chương trình bị hủy | Dừng tạo dữ liệu, đóng đầu ghi và giải quyết blocked operation | Đóng đầu đọc, chờ task kết thúc có giới hạn |
| Writer đóng trước khi ghi gì | Đóng writer bình thường | Reader có thể tới EOF ngay |
| Writer không đóng sau khi hoàn tất | Không còn dữ liệu nhưng vẫn giữ pipe mở | Reader có thể chờ vô thời hạn |

Một lưu ý về Java try-with-resources: nếu bạn tạo cả hai endpoints trong **cùng một try block** và writer/reader cần chạy trên thread khác, vòng đời tài nguyên phải bao phủ khoảng thời gian hai thread thực sự sử dụng chúng. Không đóng đầu đọc trong main khi producer vẫn gửi; cũng không để producer giữ writer mở sau khi đã dừng.

### 13.1. Mô hình điều phối có timeout và cancellation

Ứng dụng thực tế nên có người điều phối biết:

1. Khi nào khởi động producer và consumer, kết nối bao nhiêu pipe.
2. Task nào đang chạy, cách nhận lỗi qua `Future`/result channel.
3. Khi nào đóng writer để đánh dấu hoàn tất; khi nào cần đóng reader vì không dùng nữa.
4. Khi nào dừng chờ (`Future.get(timeout, unit)` hoặc cơ chế task timeout tương đương).
5. Nếu hết thời hạn, cách hủy các task, đóng đầu liên quan, ghi lỗi và chờ chúng dừng; không giả định interrupt là giải pháp duy nhất.

**Cảnh báo về thứ tự:** `future.get()`/`thread.join()` có thể chính là mắt xích của deadlock nếu caller giữ nhiệm vụ tiêu thụ dữ liệu nhưng lại chờ producer trước. Thiết kế phải cho consumer được chạy đồng thời; nếu cả producer lẫn consumer là task riêng, controller có thể giám sát cả hai mà không tự làm consumer.

## 14. Vì sao pipe đôi khi trả IOException thay vì chờ mãi?

Javadoc mô tả `PipedReader.read()` có thể ném `IOException` khi broken/unconnected/closed hoặc gặp lỗi. Writer cũng có thể ném `IOException` trong tình huống tương tự. Một implementation có thể phát hiện thread phía đối ứng đã chết trong khi buffer không còn dữ liệu hoặc reader không còn nhận.

Điều này **không phải cơ chế đảm bảo phát hiện mọi lỗi concurrency** và cũng không phải thay thế `join`/`Future`/close. Chương trình nào phụ thuộc vào exception text “Pipe broken”, “Write end dead” hay “Read end dead” để truyền kết quả ứng dụng sẽ khó duy trì giữa phiên bản và môi trường.

Hãy luôn ghi nhận **exception gốc của task** thay vì chỉ thấy đầu kia báo lỗi pipe. Khi consumer bị lỗi parse, đừng tiếp tục writer bằng mọi giá rồi chỉ log IOException thứ cấp. Khi producer bị lỗi, đóng writer trong finally và báo nguyên nhân cho bộ điều phối để nó phân biệt “đã gửi đủ dữ liệu” với “bị EOF do tác vụ hỏng”.

## 15. Những sai lầm phổ biến

1. **Gọi `read()` trước `write()` trên cùng thread**: đọc chờ dữ liệu mà chính thread này chưa ghi.
2. **Ghi payload dài hơn pipeSize trước khi bắt đầu read trên cùng thread**: producer đầy buffer và không thể tự giải phóng chỗ.
3. **`join()` producer trước khi tiêu thụ pipe**: hai thread vẫn có vòng phụ thuộc chờ.
4. **Quên `writer.close()`**: consumer đang đọc tới EOF có thể đợi vô hạn.
5. **Gọi `flush()` rồi tưởng đã gửi EOF**: flush có thể báo dữ liệu đang sẵn nhưng writer vẫn mở.
6. **Dùng `ready() == false` để suy ra hết dữ liệu**: đó chỉ là trạng thái sẵn sàng tức thời.
7. **Ghi mà không connect hoặc connect hai lần**: vi phạm hợp đồng cặp pipe, có thể `IOException`.
8. **Đặt buffer rất lớn để “sửa deadlock”**: chỉ che vấn đề ở payload hiện tại; đòi hỏi thiết kế concurrency đúng.
9. **Đọc một lần vào mảng rồi giả định đã đủ n ký tự**: xử lý số `char` thực đọc và EOF.
10. **Dùng pipe ký tự để copy ảnh/ZIP**: chúng phải đi qua byte stream, không qua Reader/Writer.
11. **Để producer exception biến mất trong thread nền**: consumer chỉ thấy EOF/broken/wait mà không biết nguyên nhân.
12. **Coi thread bị interrupt là chắc chắn đã thoát mọi I/O**: phải có chiến lược cancel và close tài nguyên.
13. **Dùng chung nhiều producer/consumer tùy tiện trên một pipe**: pipe được mô tả cho kết nối một phía gửi và một phía đọc; muốn nhiều publisher, cần cơ chế phối hợp/truyền message ở cấp cao hơn.
14. **Đóng reader trước khi producer gửi xong mà không báo hủy**: producer có thể ném lỗi hoặc kẹt trong thao tác còn dở.
15. **Xem mỗi lần write là một message không thể chia**: Reader chỉ cho dãy ký tự; các lần read có thể chia/gộp tùy tình trạng bộ đệm.

## 16. Bài tập tự kiểm tra

1. Vì sao `PipedReader` và `PipedWriter` thường phải được vận hành trên hai thread khác nhau?
2. `new PipedReader(4)` có thể xử lý tổng cộng 10.000 ký tự không? Điều kiện để hoạt động thành công là gì?
3. Mô tả vòng phụ thuộc khi main gọi `producer.join()` trước vòng `reader.read()`.
4. `writer.flush()` và `writer.close()` khác nhau thế nào đối với việc consumer đạt EOF?
5. `reader.ready() == false` có phải là dấu hiệu producer đã kết thúc không?
6. Nếu producer ghi chuỗi UTF-16 chứa emoji ngoài BMP, pipe truyền bao nhiêu đơn vị `char` cho một emoji, và consumer cần tránh giả định gì?
7. Vì sao ứng dụng cần kênh báo lỗi producer riêng ngay cả khi consumer đọc tới `-1`?
8. Nếu chỉ muốn biến một `String` đã có thành `Reader` để parser dùng trên cùng thread, chọn `PipedReader` hay `StringReader`?
9. Một thread producer còn sống nhưng đứng im và writer chưa đóng; reader có thể chờ mãi không? Có phải mọi dạng chờ đó đều là deadlock?
10. Khi cần truyền message có ranh giới và nhiều producer, nên cân nhắc trừu tượng nào khác?

**Đáp án định hướng:**

1. Vì blocking khi pipe rỗng/đầy cần phía còn lại chạy để tạo dữ liệu hoặc giải phóng capacity.
2. **Có**, buffer chỉ chứa tối đa một phần tại một thời điểm; consumer phải đọc đồng thời và writer cần đóng khi xong.
3. Producer chờ consumer đọc để có chỗ, main chờ producer hoàn tất nên consumer không chạy.
4. Flush hỗ trợ chuyển/thông báo dữ liệu đang có; **close** hoàn tất đầu ghi và cho reader nhận EOF sau khi đọc hết buffer.
5. **Không**, đó là snapshot của buffer ở thời điểm kiểm tra.
6. Thường **hai `char`** cho một supplementary code point; không được coi một lần read hay một char là một ký tự Unicode hoàn chỉnh.
7. EOF báo kết thúc luồng dữ liệu; nó không khẳng định producer đã sinh đủ dữ liệu hoặc không bị lỗi.
8. `StringReader` trực tiếp và đơn giản.
9. **Có** thể chờ vô hạn; không phải mọi chờ lâu đều hình thành vòng phụ thuộc deadlock.
10. `BlockingQueue` có capacity và giao thức dừng, hoặc API messaging/concurrency phù hợp.

## 17. Tài liệu chuẩn

- [Oracle Java SE 21 – PipedReader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/PipedReader.html): kết nối, `pipeSize`, blocking read, `ready()`, EOF và lỗi.
- [Oracle Java SE 21 – PipedWriter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/PipedWriter.html): kết nối, blocking write, `flush()` và `close()`.
- [Oracle Java SE 21 – Reader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Reader.html): contract của character-stream I/O và `read(char[],off,len)`.
- [Oracle Java SE 21 – PipedInputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/PipedInputStream.html) và [PipedOutputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/PipedOutputStream.html): phiên bản tương ứng để trao đổi byte.
- [Oracle Java SE 21 – BlockingQueue](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/BlockingQueue.html): lựa chọn cho producer–consumer có đơn vị là message và chính sách capacity.

**Ghi nhớ:** pipe ký tự là một buffer hữu hạn nối writer và reader. Đọc khi rỗng và ghi khi đầy đều có thể chặn; do đó cần producer và consumer có khả năng chạy đồng thời, **đóng writer để phát EOF**, kiểm tra lỗi của thread và bố trí `join()/get()` sau khi đã cho bên nhận tiến triển.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="in-memory-io-009">Dùng stream bộ nhớ trong kiểm thử và các phép biến đổi</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng stream bộ nhớ trong kiểm thử và các phép biến đổi Java

## 1. Ý tưởng: thay nguồn/đích thật bằng dữ liệu trong RAM

Một chương trình Java thường đọc hoặc ghi qua các trừu tượng `InputStream`, `OutputStream`, `Reader`, `Writer`. **Stream bộ nhớ** cung cấp các implementation làm việc với mảng byte hoặc chuỗi ký tự ngay trong RAM. Nhờ đó, ta có thể kiểm thử logic đọc/ghi, chuẩn hóa nội dung, nén, giải nén hay biến đổi dữ liệu mà không cần tạo tệp tạm hoặc khởi chạy một máy chủ.

```text
Dữ liệu kiểm thử trong RAM
            |
            v
   ByteArrayInputStream / StringReader
            |
            v
     Logic xử lý I/O chung
            |
            v
  ByteArrayOutputStream / StringWriter
            |
            v
  So sánh kết quả / kiểm tra bất biến
```

Cùng một hàm nhận `InputStream` có thể chạy với `Files.newInputStream(path)` ở ứng dụng thật và `ByteArrayInputStream(bytes)` trong unit test. Tương tự, hàm nhận `OutputStream` có thể ghi ra tệp trong sản xuất và ghi vào `ByteArrayOutputStream` trong test. Điểm quan trọng là **thiết kế hàm nhận luồng qua tham số**, chứ không gắn chặt logic vào tệp cụ thể.

Tình huống phù hợp:

- Kiểm thử một parser, bộ chuyển mã, serializer hoặc thuật toán sao chép mà không phụ thuộc filesystem.
- So sánh byte đầu ra chính xác, kể cả byte `0x00`, `0xFF`, newline, chữ có dấu hoặc định dạng nhị phân.
- Xây dựng payload nhỏ hoặc vừa trong RAM trước khi gửi đến API, lưu trữ hoặc truyền qua luồng khác.
- Thử một chuỗi phép biến đổi: văn bản → byte UTF-8 → nén → giải nén → văn bản.
- Kiểm tra rằng code giữ nguyên thứ tự byte, số byte đọc được, charset và quy tắc đóng/flush.

**Phạm vi:** stream bộ nhớ vẫn tuân thủ hợp đồng I/O của Java nhưng thường nhanh, có sẵn dữ liệu và ít lỗi môi trường. Chúng không tự mô phỏng độ trễ mạng, read ngắn bất thường, lỗi đĩa hoặc sự cố đóng luồng. Muốn kiểm thử những nhánh đó, ta cần thêm implementation giả lập có chủ đích.

## 2. Bốn stream bộ nhớ nền tảng

| Mục đích | Loại byte | Loại ký tự |
| --- | --- | --- |
| Đọc từ dữ liệu đã có trong RAM | `ByteArrayInputStream` | `StringReader`, `CharArrayReader` |
| Gom dữ liệu đầu ra vào RAM | `ByteArrayOutputStream` | `StringWriter`, `CharArrayWriter` |
| Trừu tượng mà logic xử lý nên nhận | `InputStream` | `Reader` |
| Trừu tượng mà logic xuất kết quả nên nhận | `OutputStream` | `Writer` |

Các lớp trên thuộc `java.io`.

- `ByteArrayInputStream`: một `InputStream` đọc byte từ **mảng byte đã có**.
- `ByteArrayOutputStream`: một `OutputStream` có buffer tự tăng, gom toàn bộ byte đã được ghi.
- `StringReader`: một `Reader` đọc các đơn vị `char` từ một `String`.
- `StringWriter`: một `Writer` gom các đơn vị `char` thành nội dung chuỗi có thể lấy qua `toString()`.

Hai nhóm byte và ký tự có ý nghĩa khác nhau. **Stream byte không tự hiểu encoding**, còn `StringReader`/`StringWriter` làm việc với các đơn vị UTF-16 của Java và không phải là nơi tự động sinh byte UTF-8. Khi cần kiểm tra byte của văn bản, phải **mã hóa/giải mã với charset đã chọn**, thường là `StandardCharsets.UTF_8`.

## 3. ByteArrayInputStream: cung cấp byte cho code cần InputStream

Khởi tạo thông dụng:

```java
byte[] fixture = {0, 65, (byte) 0xFF};
InputStream source = new ByteArrayInputStream(fixture);
```

Đây là **đoạn minh họa**, cần import `java.io.InputStream` và `java.io.ByteArrayInputStream`.

`ByteArrayInputStream` coi mảng đã truyền là nguồn byte. Nó hỗ trợ:

| Phương thức | Hành vi |
| --- | --- |
| `read()` | Trả một byte dưới dạng `int` 0–255; `-1` khi EOF |
| `read(byte[], off, len)` | Đọc vào vùng chỉ định, trả số byte thực đọc |
| `available()` | Số byte còn lại có thể đọc của **implementation bộ nhớ này** |
| `skip(n)` | Bỏ qua tối đa `n` byte còn lại |
| `mark(...)` và `reset()` | Đánh dấu vị trí rồi quay về đó; lớp này hỗ trợ `mark/reset` |
| `transferTo(OutputStream)` | Chuyển các byte còn lại sang một output stream (Java 9+) |
| `close()` | **Không có tác dụng** đối với lớp này; có thể tiếp tục gọi các phương thức sau khi close |

Khác với file stream, `ByteArrayInputStream` không sở hữu file descriptor hoặc socket. Vì vậy nó hữu ích làm fixture cho test và ví dụ có thể chạy độc lập.

### Hai lưu ý về tính bất biến

**Thứ nhất, constructor không tạo bản sao phòng vệ của mảng byte nguồn.** Nếu bên khác thay đổi nội dung `fixture` sau khi tạo luồng nhưng trước khi đọc, kết quả đọc có thể thay đổi. Với một fixture cần ổn định, đừng sửa mảng gốc, hoặc tự tạo bản sao:

```java
byte[] safeFixture = fixture.clone();
InputStream source = new ByteArrayInputStream(safeFixture);
```

**Thứ hai, `mark/reset` của `ByteArrayInputStream` không có nghĩa mọi `InputStream` đều quay ngược được.** Trước khi dùng `reset()` trên luồng bất kỳ, kiểm tra `markSupported()` và hiểu hợp đồng của implementation. Với luồng file, network hoặc các wrapper khác, hành vi có thể khác.

## 4. ByteArrayOutputStream: gom byte rồi lấy kết quả

Một `ByteArrayOutputStream` chứa buffer byte tăng kích thước khi cần:

```java
ByteArrayOutputStream sink = new ByteArrayOutputStream();
sink.write(65);
sink.write(new byte[]{66, 67});
byte[] result = sink.toByteArray(); // Các byte 65, 66, 67.
```

Đây là **đoạn minh họa**, cần import `java.io.ByteArrayOutputStream`.

Các thao tác chủ yếu:

| Phương thức | Ý nghĩa |
| --- | --- |
| `write(int)`, `write(byte[], off, len)` | Ghi byte vào buffer trong RAM |
| `size()` | Số byte đã ghi hiện tại |
| `toByteArray()` | Tạo **một bản sao mới** của các byte đã ghi |
| `toString(Charset)` | Giải mã các byte đã ghi thành `String` với charset xác định |
| `writeTo(OutputStream)` | Chuyển toàn bộ byte đã gom đến một đích khác |
| `reset()` | Đặt số byte đã ghi về 0 để dùng lại buffer |
| `close()` | Không có tác dụng; phương thức của lớp vẫn dùng được sau close |

`ByteArrayOutputStream` khác với `ByteArrayInputStream` ở hướng dữ liệu: một bên **gom byte**, một bên **phát byte đã có**.

`toByteArray()` tạo bản sao, vì vậy việc gọi `reset()` hoặc ghi thêm vào `ByteArrayOutputStream` sau đó không làm thay đổi mảng kết quả đã lấy trước đó. Ngược lại, lấy một bản sao lớn có chi phí bộ nhớ và sao chép dữ liệu; đừng gọi `toByteArray()` liên tục trong mỗi lượt ghi nếu dữ liệu lớn.

**Không chuyển byte nhị phân thành `String` chỉ để xem kết quả.** Với nội dung UTF-8 đã biết, dùng `toString(StandardCharsets.UTF_8)`. Với ảnh, ZIP hoặc dữ liệu nhị phân, hãy so sánh byte/hex/hash phù hợp.

## 5. Ví dụ chạy được: sao chép byte trong RAM và xác minh

**Tệp `MemoryBytesDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HexFormat;

public class MemoryBytesDemo {
    public static void main(String[] args) throws IOException {
        byte[] original = {0, 65, (byte) 0xFF};

        try (InputStream in = new ByteArrayInputStream(original);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            long transferred = in.transferTo(out);
            byte[] snapshot = out.toByteArray();

            System.out.println("Transferred: " + transferred);
            System.out.println("Hex: " + HexFormat.of().formatHex(snapshot));
            System.out.println("Exact match: "
                    + Arrays.equals(original, snapshot));

            out.reset();
            System.out.println("Size after reset: " + out.size());
            System.out.println("Snapshot length: " + snapshot.length);
        }
    }
}
```

Lệnh:

```bash
javac MemoryBytesDemo.java
java MemoryBytesDemo
```

**Stdout chính xác trên Java 17+:**

```text
Transferred: 3
Hex: 0041ff
Exact match: true
Size after reset: 0
Snapshot length: 3
```

Giải thích:

1. Byte `0xFF` hợp lệ và được `read()` biểu diễn bằng số nguyên 255, **khác** EOF `-1`.
2. `transferTo(out)` chuyển các byte còn lại từ luồng nguồn sang luồng đích, trả số byte đã chuyển.
3. `Arrays.equals(original, snapshot)` kiểm tra từng byte theo đúng thứ tự; phù hợp để xác minh **kết quả nhị phân chính xác**.
4. `HexFormat` (Java 17+) giúp biểu diễn byte dưới dạng hex dễ đọc.
5. `out.reset()` bỏ nội dung logic trong buffer đầu ra; mảng `snapshot` do `toByteArray()` tạo trước đó vẫn chứa ba byte.

Không có tệp nào được tạo trong ví dụ này. Bản thân `ByteArrayInputStream` không mô phỏng tình huống đọc một phần do mạng; nó chỉ cung cấp nguồn byte được xác định trước.

## 6. StringReader và StringWriter: kiểm thử logic ký tự

Nếu đầu vào/đầu ra ở cấp **văn bản**, dùng `Reader` và `Writer` thay vì ép text thành byte rồi ngược lại khi không cần. Cách này giúp test tập trung vào thuật toán xử lý chuỗi.

`StringReader` cho phép `read()`, `read(char[])`, `skip()`, `mark()`, `reset()` trên nội dung `String`. Sau khi `StringReader.close()`, các lần đọc tiếp thường ném `IOException`; nên không dựa vào hành vi đóng giống `ByteArrayInputStream`.

`StringWriter` tích lũy ký tự; `toString()` trả kết quả, `getBuffer()` trả buffer kiểu `StringBuffer` có thể thay đổi nên tránh cho code không tin cậy sửa trực tiếp. `StringWriter.close()` không có tác dụng, nhưng vẫn nên theo hợp đồng quản lý tài nguyên khi làm việc với các `Writer` tổng quát.

Để code xử lý text tái sử dụng được, hãy nhận `Reader source`, `Writer target` và để bên gọi quyết định chúng là memory hay file streams.

### Ví dụ chạy được: chuẩn hóa từng dòng bằng Reader/Writer

Bài toán: bỏ dòng trắng, `strip()` khoảng trắng ở đầu/cuối và chuyển các dòng còn lại thành chữ hoa. Kết quả sử dụng LF `'\n'` tường minh để dễ kiểm thử trên mọi hệ điều hành.

**Tệp `MemoryTextTransform.java`:**

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

public class MemoryTextTransform {
    // Chỉ xử lý nội dung; caller sở hữu source và target.
    static int normalize(Reader source, Writer target) throws IOException {
        BufferedReader lines = new BufferedReader(source);
        int kept = 0;
        String line;

        while ((line = lines.readLine()) != null) {
            String value = line.strip();
            if (value.isEmpty()) {
                continue;
            }

            target.write(value.toUpperCase(Locale.ROOT));
            target.write('\n');
            kept++;
        }
        target.flush();
        return kept;
    }

    public static void main(String[] args) throws IOException {
        String input = "  Hà Nội  \n   \n Cần Thơ \n";
        StringWriter output = new StringWriter();

        try (StringReader inputReader = new StringReader(input)) {
            int kept = normalize(inputReader, output);
            String expected = "HÀ NỘI\nCẦN THƠ\n";

            System.out.println("Kept lines: " + kept);
            System.out.println("Exact text: "
                    + expected.equals(output.toString()));
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 MemoryTextTransform.java
java MemoryTextTransform
```

**Stdout chính xác:**

```text
Kept lines: 2
Exact text: true
```

Hàm `normalize` không hề biết dữ liệu đến từ `StringReader`. Khi cần đọc từ tệp, bên gọi có thể tạo một `BufferedReader` hoặc `InputStreamReader` với charset phù hợp rồi truyền theo kiểu `Reader`. Khi cần ghi file, dùng `Writer` mở từ đường dẫn tệp. **Logic xử lý không đổi**.

Lưu ý `BufferedReader.readLine()` bỏ dấu kết thúc dòng gốc, và hàm cố ý ghi lại LF. Do đó phép biến đổi này chuẩn hóa newline chứ không bảo toàn byte gốc. `String.toUpperCase(Locale.ROOT)` cũng có thể làm độ dài chuỗi thay đổi đối với một số ký tự Unicode; đây là biến đổi văn bản có chủ đích, không phải sao chép nhị phân.

### CharArrayReader và CharArrayWriter

Nếu dữ liệu được biểu diễn trực tiếp dưới dạng `char[]`, dùng `CharArrayReader` và `CharArrayWriter`. `CharArrayWriter.toCharArray()` trả bản sao ký tự đã ghi; `reset()` cho phép dùng lại buffer. Chúng phục vụ các thao tác ký tự, **không thực hiện chuyển đổi charset**. Với Unicode ngoài BMP, một code point có thể gồm hai `char`, vì thế không cắt tùy ý giữa một cặp surrogate khi muốn thu được các đoạn text độc lập hợp lệ.

## 7. Thiết kế unit test bằng cách truyền InputStream/OutputStream

Giả sử code nghiệp vụ có nhiệm vụ sao chép đúng byte từ nguồn sang đích. Thiết kế kém cho việc test là hàm mở cứng `input.bin` và `output.bin` ở bên trong; test phải chuẩn bị tệp và dọn dẹp. Một thiết kế linh hoạt hơn:

```java
static long copy(InputStream source, OutputStream target)
        throws IOException {
    byte[] buffer = new byte[4096];
    long total = 0;
    int count;

    while ((count = source.read(buffer)) != -1) {
        target.write(buffer, 0, count);
        total += count;
    }
    return total;
}
```

Đây là **phương thức minh họa trong ngữ cảnh**, yêu cầu `java.io.InputStream`, `java.io.OutputStream`, `java.io.IOException`. Test có thể truyền `ByteArrayInputStream` và `ByteArrayOutputStream`. Code sản xuất có thể truyền luồng mở từ file, socket hoặc API khác.

Ba điều cần kiểm thử riêng biệt:

1. **Dữ liệu:** đầu ra phải bằng đúng byte đầu vào; giữ nguyên byte `0x00`, `0xFF`, newline và Unicode đã mã hóa.
2. **Ranh giới:** `read(buffer)` có thể trả ít hơn chiều dài buffer. Chỉ được ghi `count` byte.
3. **Lỗi:** nguồn/đích thật có thể phát sinh `IOException`. Stream bộ nhớ mặc định ít khi tạo được lỗi kiểu này nên cần luồng giả lập khi muốn test nhánh thất bại.

### Ví dụ hoàn chỉnh: kiểm thử kết quả và lỗi I/O mô phỏng

**Tệp `CopyServiceTestDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CopyServiceTestDemo {
    static long copy(InputStream source, OutputStream target)
            throws IOException {
        byte[] buffer = new byte[4];
        int count;
        long total = 0;

        while ((count = source.read(buffer)) != -1) {
            target.write(buffer, 0, count);
            total += count;
        }
        return total;
    }

    // OutputStream giả lập lỗi ngay khi đã nhận đủ limit byte.
    static final class FailAfter extends OutputStream {
        private int remaining;

        FailAfter(int limit) {
            remaining = limit;
        }

        @Override
        public void write(int value) throws IOException {
            if (remaining == 0) {
                throw new IOException("synthetic write failure");
            }
            remaining--;
        }
    }

    public static void main(String[] args) throws IOException {
        byte[] payload = "abcde".getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long copied = copy(new ByteArrayInputStream(payload), output);

        System.out.println("Copied: " + copied);
        System.out.println("Exact bytes: "
                + Arrays.equals(payload, output.toByteArray()));

        boolean failedAsExpected = false;
        try {
            copy(new ByteArrayInputStream(payload), new FailAfter(2));
        } catch (IOException exception) {
            failedAsExpected =
                    "synthetic write failure".equals(exception.getMessage());
        }
        System.out.println("Failure detected: " + failedAsExpected);
    }
}
```

Biên dịch/chạy:

```bash
javac CopyServiceTestDemo.java
java CopyServiceTestDemo
```

**Stdout chính xác:**

```text
Copied: 5
Exact bytes: true
Failure detected: true
```

`FailAfter` là `OutputStream` tự định nghĩa cho test. Hàm `OutputStream.write(byte[], off, len)` mặc định của lớp cha ghi lần lượt qua `write(int)`, nên khi tới byte thứ ba, `FailAfter.write(int)` ném `IOException`. Đây là bằng chứng có thể kiểm tra nhánh xử lý lỗi mà không cần làm hỏng ổ đĩa thật.

Trong dự án dùng JUnit 5, có thể chuyển các dòng `System.out.println` của ví dụ thành `assertEquals(5, copied)`, `assertArrayEquals(payload, output.toByteArray())`, và `assertThrows(IOException.class, ...)`. Cốt lõi là **kết quả test có thể xác định** và không cần dữ liệu nằm ngoài tiến trình.

## 8. Kiểm thử từ byte UTF-8 đến logic xử lý text

Nếu ứng dụng thực tế nhận dữ liệu qua `InputStream` nhưng nội dung là text, một test chỉ dùng `StringReader` có thể bỏ sót lỗi **charset**. Để kiểm tra toàn tuyến:

```text
String fixture
    │ getBytes(UTF_8)
    ▼
ByteArrayInputStream
    │
    ▼ InputStreamReader(UTF_8)
Logic xử lý Reader / Writer
    │
    ▼ OutputStreamWriter(UTF_8)
ByteArrayOutputStream
    │ toByteArray()
    ▼
So sánh byte UTF-8 hoặc giải mã với UTF-8
```

Ví dụ đoạn mã đặt trong một phương thức đã có hàm `normalize(Reader, Writer)` ở mục trước:

```java
byte[] incoming = "  Hà Nội\n".getBytes(StandardCharsets.UTF_8);
ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();

try (Reader reader = new InputStreamReader(
            new ByteArrayInputStream(incoming), StandardCharsets.UTF_8)) {
    Writer writer = new OutputStreamWriter(
            outputBytes, StandardCharsets.UTF_8);
    normalize(reader, writer);
    writer.flush(); // Cần để byte kết quả được chuyển xuống outputBytes.
}
String result = outputBytes.toString(StandardCharsets.UTF_8);
```

Đây là **đoạn minh họa**, cần các import tương ứng. `result` khi thành công là `"HÀ NỘI\n"`. Không cần đóng writer bọc `ByteArrayOutputStream` trong ví dụ này vì `flush()` đủ cho UTF-8 trong trường hợp này, nhưng với writer khác hoặc encoder stateful, **`close()` có thể cần thiết để hoàn tất mã hóa**. Nếu đang quản lý một writer do code tự mở, nên dùng `try-with-resources` theo đúng quyền sở hữu.

Nếu test sử dụng byte fixtures nhưng code gọi `new String(bytes)` hoặc `text.getBytes()` mà **không chỉ định charset**, kết quả có thể phụ thuộc runtime hoặc môi trường. Test hướng tới tính xác định nên sử dụng `StandardCharsets.UTF_8` rõ ràng cả khi tạo đầu vào và khi đọc đầu ra.

Đặc biệt tránh `(char) byteValue` để tự giải mã tiếng Việt: UTF-8 mã hóa một ký tự thành nhiều byte. `ByteArrayInputStream` chỉ trả byte chứ không giữ ranh giới code point.

## 9. Phép biến đổi nhị phân trong RAM: nén và giải nén GZIP

Một pipeline có thể bọc nhiều stream:

```text
Mảng byte gốc
    │
    ▼ GZIPOutputStream
ByteArrayOutputStream (gom byte đã nén)
    │ toByteArray()
    ▼ ByteArrayInputStream (đọc byte nén)
GZIPInputStream
    │ transferTo(...)
    ▼ ByteArrayOutputStream (gom byte giải nén)
So sánh với byte gốc
```

`GZIPOutputStream` và `GZIPInputStream` thuộc `java.util.zip`. Đây là **phép biến đổi byte**, không phải chuyển đổi charset. Khi nguồn là văn bản, ta chủ động mã hóa UTF-8 một lần ở rìa pipeline rồi giải mã một lần sau cùng.

### Ví dụ hoàn chỉnh: kiểm tra nén/giải nén trong RAM

**Tệp `MemoryGzipDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MemoryGzipDemo {
    public static void main(String[] args) throws IOException {
        byte[] original =
                "Java memory stream. ".repeat(20)
                        .getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream compressedSink = new ByteArrayOutputStream();

        try (GZIPOutputStream gzip =
                     new GZIPOutputStream(compressedSink)) {
            gzip.write(original);
        } // close() hoàn tất phần cuối của định dạng GZIP.

        byte[] compressed = compressedSink.toByteArray();
        boolean hasGzipHeader = compressed.length >= 2
                && (compressed[0] & 0xFF) == 0x1F
                && (compressed[1] & 0xFF) == 0x8B;

        ByteArrayOutputStream restoredSink = new ByteArrayOutputStream();
        try (GZIPInputStream gunzip = new GZIPInputStream(
                new ByteArrayInputStream(compressed))) {
            gunzip.transferTo(restoredSink);
        }

        System.out.println("GZIP header: " + hasGzipHeader);
        System.out.println("Restored exact bytes: "
                + Arrays.equals(original, restoredSink.toByteArray()));
    }
}
```

Biên dịch/chạy:

```bash
javac MemoryGzipDemo.java
java MemoryGzipDemo
```

**Stdout chính xác:**

```text
GZIP header: true
Restored exact bytes: true
```

Tại sao phải **đóng `GZIPOutputStream` trước khi đọc mảng byte nén**? Vì GZIP có trailer và trạng thái nén cần hoàn tất; chỉ gọi `flush()` không thay thế chắc chắn cho `finish()` hoặc `close()`. Trong ví dụ, `try-with-resources` kết thúc luồng GZIP trước `toByteArray()`, nên byte đã nén có cấu trúc đầy đủ. Đóng `GZIPOutputStream` cũng đóng `ByteArrayOutputStream` bên dưới, nhưng với lớp bộ nhớ này `close()` không có tác dụng, nên vẫn có thể lấy kết quả.

Đừng so sánh byte nén với byte gốc hoặc đòi hỏi kích thước nén luôn nhỏ hơn: dữ liệu ngắn hoặc khó nén có thể tạo đầu ra **lớn hơn** do header và metadata. Phép kiểm thử thích hợp là **giải nén lại và so sánh byte nguyên bản**, đồng thời kiểm tra lỗi cho dữ liệu nén hỏng ở test khác.

## 10. Phép biến đổi có cấu trúc: DataOutputStream và DataInputStream

Khi truyền/ghi dữ liệu nhị phân có cấu trúc, có thể xếp `DataOutputStream` trên `ByteArrayOutputStream`, rồi dùng `DataInputStream` trên `ByteArrayInputStream` để kiểm tra đọc ngược.

**Tệp `MemoryDataRecord.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class MemoryDataRecord {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream memory = new ByteArrayOutputStream();

        try (DataOutputStream writer = new DataOutputStream(memory)) {
            writer.writeInt(42);
            writer.writeBoolean(true);
            writer.writeUTF("Java");
        }

        byte[] serialized = memory.toByteArray();
        try (DataInputStream reader = new DataInputStream(
                new ByteArrayInputStream(serialized))) {
            int id = reader.readInt();
            boolean active = reader.readBoolean();
            String name = reader.readUTF();

            System.out.println("Record: " + id + "/" + active + "/" + name);
            System.out.println("EOF: " + (reader.read() == -1));
        }
    }
}
```

Lệnh:

```bash
javac MemoryDataRecord.java
java MemoryDataRecord
```

**Stdout chính xác:**

```text
Record: 42/true/Java
EOF: true
```

`DataOutputStream.writeInt` ghi bốn byte theo thứ tự high-order byte trước; `writeBoolean` ghi một byte; `writeUTF` ghi độ dài rồi dữ liệu theo **modified UTF-8**, khác UTF-8 thông thường ở một số trường hợp. Chương trình đọc phải dùng đúng **thứ tự và phương thức tương ứng**: `readInt`, `readBoolean`, `readUTF`.

**Lưu ý thực tế:** `DataOutputStream.writeUTF`/ `DataInputStream.readUTF` không phải giải pháp tổng quát để phát ra JSON, text UTF-8 thuần hoặc giao thức bên ngoài. Nếu định dạng tương tác yêu cầu UTF-8 chuẩn với chiều dài kiểu khác, hãy đặc tả rõ framing, endian và charset, rồi viết bộ mã hóa phù hợp. Stream bộ nhớ giúp tạo fixture nhị phân để đối chiếu các byte kỳ vọng.

## 11. Chọn byte hay char cho phép biến đổi?

| Dữ liệu và phép biến đổi | Nên xử lý bằng | Vì sao |
| --- | --- | --- |
| Sao chép ảnh, ZIP, PDF, protobuf, checksum | `InputStream`/`OutputStream` | Giữ nguyên byte, không giải mã |
| Nén GZIP, mã hóa nhị phân, parse định dạng binary | Byte streams và wrapper tương ứng | Phép biến đổi được định nghĩa trên byte |
| Chuẩn hóa dòng, loại khoảng trắng, đổi chữ hoa | `Reader`/`Writer` | Phép biến đổi được định nghĩa trên ký tự |
| Đọc UTF-8 từ file/network để parse text | `InputStreamReader` + charset | Chuyển từ byte sang ký tự có kiểm soát |
| Xuất text UTF-8 ra network/file | `OutputStreamWriter` + charset | Chuyển từ ký tự sang byte rõ quy ước |
| Test kết quả serialize chính xác | `ByteArrayOutputStream.toByteArray()` | Xác minh byte, độ dài, thứ tự |
| Test hàm xử lý text | `StringReader`/`StringWriter` | Dễ tạo input, so sánh string |
| Test toàn tuyến encoding/decoding | ByteArray streams + Reader/Writer wrappers | Phát hiện lỗi charset và BOM khi có |

Việc **ghép nhiều wrapper** là bình thường. Ví dụ `BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), UTF_8))` tạo luồng đọc theo dòng từ byte fixture. Nhưng lớp càng nhiều, quyền sở hữu và thứ tự flush/close càng phải rõ; đóng wrapper ngoài thường đóng luôn lớp bên trong.

## 12. Quan trọng: không đồng nhất memory stream với I/O thực tế

Stream bộ nhớ tạo ra môi trường test có dữ liệu sẵn, ít biến động. Điều đó hữu ích cho unit test nhưng cũng có giới hạn.

### 12.1 Lần read thường đầy dữ liệu hơn thực tế

`ByteArrayInputStream.read(byte[], off, len)` có thể trả phần lớn số byte yêu cầu ngay lập tức (đến giới hạn dữ liệu còn lại). Trong mạng hoặc hệ thống file, `InputStream.read(...)` được phép trả **ít hơn** số byte yêu cầu. Không viết thuật toán đòi mỗi lần đọc phải bằng `buffer.length`.

Khi cần test trường hợp đọc từng phần, có thể bọc luồng bằng `FilterInputStream` và giới hạn số byte của mỗi `read(byte[], off, len)`:

```java
// Lớp minh họa trong ngữ cảnh; cần import java.io.*.
final class SmallChunkInputStream extends FilterInputStream {
    SmallChunkInputStream(InputStream delegate) {
        super(delegate);
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        return in.read(bytes, offset, Math.min(length, 2));
    }
}
```

Một hàm copy đúng phải xử lý được khi chỉ có 1 hoặc 2 byte mỗi lần. Khi test văn bản UTF-8, byte chunk thậm chí có thể kết thúc giữa ký tự đa byte; dùng **decoder streaming** như `InputStreamReader` thay vì gọi `new String(chunk, UTF_8)` trên từng mảnh.

### 12.2 Luồng bộ nhớ thông thường không báo lỗi môi trường

`ByteArrayOutputStream.write` và `StringWriter.write` làm việc trong RAM, thường không ném `IOException` do lỗi đĩa. Một phần mềm xử lý tệp/network phải được test bổ sung với fake streams ném lỗi sau N byte, lỗi khi close, EOF sớm, hay `Reader` lỗi giữa chừng. Đó là lý do ví dụ `FailAfter` ở trên có ích.

### 12.3 Bộ nhớ có giới hạn

Gom toàn bộ nội dung với `ByteArrayOutputStream` hoặc `StringWriter` tiêu thụ RAM theo lượng dữ liệu. Khi buffer tăng và khi gọi `toByteArray()`, có thể có **nhiều bản sao dữ liệu cùng tồn tại**. Với vài MB đã biết, điều này thường chấp nhận được; với hàng GB, stream thực sự theo chunk sẽ tốt hơn.

Nếu input không đáng tin hoặc có thể không giới hạn (ví dụ request tải lên hay dữ liệu giải nén), phải đặt **giới hạn kích thước** trước khi gom tất cả vào RAM. GZIP có thể giải nén thành dữ liệu lớn hơn rất nhiều so với byte nén ban đầu, vì vậy test thành công với payload nhỏ không bảo đảm chống hết bộ nhớ trong sản xuất.

### 12.4 Không đại diện cho tính đồng thời hay atomicity

Một stream bộ nhớ đơn giản không mô phỏng khóa file, write atomicity, nhiều tiến trình ghi đồng thời, mạng chậm hoặc socket bị ngắt. Unit test tập trung vào logic chuyển đổi; integration test cần cơ chế thật hoặc môi trường kiểm thử gần thực tế khi các yếu tố này quan trọng.

## 13. Quản lý vòng đời: ai tạo stream, ai đóng stream?

Trong Java, một số memory streams có `close()` không tác dụng: `ByteArrayInputStream`, `ByteArrayOutputStream`, `StringWriter`. Nhưng **điều đó không có nghĩa mọi stream được thay thế trong sản xuất cũng đóng tùy tiện được**.

Một API xử lý I/O có thể có hợp đồng:

```java
static void process(InputStream source, OutputStream target)
        throws IOException {
    // Đọc từ source và ghi sang target.
    // Caller tạo và sở hữu hai stream nên phương thức không đóng chúng.
}
```

Đây là mẫu **phân quyền sở hữu**, không phải chương trình chạy độc lập. Khi caller tạo stream, caller sẽ quyết định dùng `try-with-resources` để đóng. Code xử lý có thể gọi `flush()` nếu API yêu cầu kết quả đến ngay, nhưng cần tránh đóng stream ngoài ý muốn, đặc biệt nếu một luồng được dùng cho nhiều bước hoặc là `System.in/out`.

Nếu chính API tự mở file hay `GZIPOutputStream`, hãy dùng `try-with-resources` tại nơi sở hữu tài nguyên. Với wrapper nén hoặc bộ mã hóa có trạng thái, **close/finish có thể là bước kết thúc dữ liệu** cần thiết. Các test chỉ dùng memory stream có thể che giấu lỗi quên đóng wrapper.

### Ví dụ điển hình về thứ tự close

```java
ByteArrayOutputStream bytes = new ByteArrayOutputStream();
try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
    gzip.write(payload);
}
byte[] completeGzip = bytes.toByteArray();
```

Đây là đoạn minh họa với `byte[] payload` đã có, cần import `ByteArrayOutputStream`, `GZIPOutputStream` và xử lý `IOException`. Dữ liệu sau `close()` của wrapper đã hoàn tất cấu trúc GZIP. Việc `bytes.close()` không có tác dụng không thay đổi yêu cầu phải kết thúc đúng wrapper GZIP.

## 14. Những lỗi thường gặp

| Lỗi | Vì sao sai hoặc nguy hiểm | Cách khắc phục |
| --- | --- | --- |
| Dùng `new String(output.toByteArray())` khi test text | Phụ thuộc default charset | Chỉ rõ `StandardCharsets.UTF_8` hoặc charset của dữ liệu |
| Dùng `ByteArrayOutputStream.toString()` không charset rõ | Có thể phụ thuộc encoding mặc định | `toString(StandardCharsets.UTF_8)` |
| So sánh String để kiểm thử ảnh/ZIP | Nhị phân không phải văn bản | `assertArrayEquals`/`Arrays.equals` trên byte |
| Cắt UTF-8 thành chunk rồi giải mã mỗi chunk độc lập | Có thể tách byte của một ký tự | Dùng `InputStreamReader`/decoder streaming |
| Coi `char` luôn là một Unicode code point | UTF-16 có surrogate pair | Dùng API code point khi xử lý ở cấp ký tự Unicode |
| Luôn ghi `out.write(buffer)` sau `read(buffer)` | Ghi cả phần dữ liệu cũ của buffer ở lượt cuối | `out.write(buffer, 0, count)` |
| Cho rằng `ByteArrayInputStream` mô phỏng mọi I/O | Không có độ trễ, lỗi mạng và nhiều read ngắn như thực tế | Thêm fake streams và integration test |
| Quên gọi flush/close wrapper mã hóa/nén | Byte cuối/trailer có thể chưa được hoàn tất | Đóng wrapper đúng vòng đời |
| Gọi `toByteArray()` sau từng lần write lớn | Tạo nhiều bản sao và tốn RAM | Chỉ lấy snapshot khi cần kiểm tra/kết quả |
| Tạo `ByteArrayOutputStream` cho đầu vào không giới hạn | Có thể hết heap | Streaming + giới hạn kích thước |
| Cho rằng reset thay đổi snapshot đã lấy | `toByteArray()` tạo bản sao độc lập | Phân biệt buffer nội bộ với bản sao đầu ra |
| Sửa mảng byte đã truyền vào `ByteArrayInputStream` | Fixture có thể thay đổi ngoài dự kiến | Giữ mảng bất biến theo quy ước hoặc clone |
| Đóng luồng do caller sở hữu trong hàm xử lý | Có thể khiến các bước sau mất stream | Công bố ownership, để caller đóng |
| Dùng `System.setOut` cho mọi test | Thay đổi trạng thái global của cả JVM | Truyền `OutputStream`/`PrintStream` qua tham số |
| So sánh dữ liệu nén byte-for-byte giữa công cụ | Header, metadata và implementation có thể khác | Xác minh giải nén phục hồi byte gốc và cấu trúc hợp lệ |

## 15. Mẫu thiết kế cho mã sản xuất và test

Một cách chia trách nhiệm rõ ràng:

```text
Application boundary
  ├─ Mở FileInputStream / socket / API source
  ├─ Mở FileOutputStream / socket / API destination
  ├─ Gọi logic xử lý với InputStream, OutputStream
  └─ Đóng tài nguyên mà application sở hữu

Unit test
  ├─ ByteArrayInputStream / StringReader từ fixture
  ├─ ByteArrayOutputStream / StringWriter thu kết quả
  ├─ Gọi cùng logic xử lý
  └─ So sánh chính xác hoặc kiểm tra bất biến

Failure test
  ├─ FakeInputStream / FakeOutputStream trả chunk ngắn hoặc ném lỗi
  └─ Kiểm tra phản ứng, không làm hỏng file thật
```

Tách **nguồn/đích** khỏi **phép biến đổi** là chìa khóa. Khi cần, API nên nhận chính `InputStream`, `OutputStream`, `Reader` hay `Writer` qua constructor hoặc tham số. Điều đó cũng giúp đo hiệu năng/streaming: thuật toán không cần đọc toàn bộ input vào RAM chỉ vì test đang dùng stream bộ nhớ.

Có thể dùng `InputStream.nullInputStream()`, `OutputStream.nullOutputStream()`, `Reader.nullReader()` hay `Writer.nullWriter()` trong Java hiện đại để mô phỏng EOF hoặc đích bỏ qua dữ liệu, nhưng chúng không thay thế được memory stream **khi cần kiểm tra byte hoặc ký tự đầu ra**. Chẳng hạn một output sink bỏ qua nội dung có thể giúp thử hàm có chạy xong không, nhưng không chứng minh thuật toán ghi đúng dữ liệu.

## 16. Bài tập thực hành

1. Sửa `MemoryBytesDemo` để byte nguồn là `{0, 1, (byte) 0x80, (byte) 0xFF}`. Dự đoán `HexFormat` và kiểm tra `Arrays.equals`.
2. Trong `MemoryTextTransform`, thêm dòng tiếng Việt có emoji và chữ kết hợp dấu. Quan sát kết quả `strip()`/`toUpperCase(Locale.ROOT)` và so sánh dựa trên Unicode thay vì số byte.
3. Thay `StringWriter` bằng `OutputStreamWriter` trên `ByteArrayOutputStream` với UTF-8, rồi xác minh byte nhận được sau `flush()`.
4. Thay buffer 4 byte trong `CopyServiceTestDemo` thành 2 byte. Kết quả byte và số byte copy phải giữ nguyên.
5. Dùng `SmallChunkInputStream` bọc một `ByteArrayInputStream`. Chạy lại hàm `copy` và thử dữ liệu dài có byte `0xFF`; kiểm tra thuật toán vẫn đúng.
6. Thay `FailAfter(2)` bằng `FailAfter(0)` hoặc `FailAfter(5)`; giải thích lúc nào exception sẽ xuất hiện.
7. Thay payload nén GZIP bằng 1000 byte ngẫu nhiên rồi kiểm tra byte giải nén. Giải thích vì sao compressed size có thể lớn hơn original size.
8. Trong `MemoryDataRecord`, đổi thứ tự `readBoolean()` và `readInt()` và quan sát giá trị sai hoặc lỗi; khôi phục đúng thứ tự theo định dạng.
9. Tạo một hàm tính checksum cho `InputStream`, chạy bằng `ByteArrayInputStream` với fixture biết trước rồi dùng file stream với cùng byte. Kết quả phải giống nhau.
10. Viết JUnit test cho một hàm nhận `Reader` và `Writer`, dùng `StringReader`/`StringWriter` và `assertEquals(expected, actual)`. Sau đó bổ sung một test giả lập reader ném `IOException` giữa chừng.

## 17. Tóm lược kiến thức cần nhớ

**`ByteArrayInputStream`** và **`StringReader`** thay nguồn I/O bằng dữ liệu xác định trong RAM; **`ByteArrayOutputStream`** và **`StringWriter`** gom kết quả để đối chiếu. Khi thiết kế thuật toán nhận **các interface/class trừu tượng của luồng**, cùng logic có thể chạy trên memory fixtures, file, pipe hoặc dữ liệu khác mà không sửa code.

Ba nguyên tắc đặc biệt quan trọng:

1. **Xác định tầng dữ liệu:** byte cho nhị phân, reader/writer cho văn bản, charset rõ ràng ở ranh giới giữa byte và ký tự.
2. **Kiểm thử cả thành công và thất bại:** so sánh dữ liệu chính xác; dùng fake streams cho read ngắn, EOF sớm, ngoại lệ hoặc nhánh cần mô phỏng.
3. **Không quên giới hạn môi trường:** stream trong RAM tiêu thụ heap và không phản ánh đầy đủ file/network; wrapper biến đổi như GZIP vẫn cần kết thúc đúng vòng đời.

### Tài liệu tra cứu

- Java SE 17: [`ByteArrayInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ByteArrayInputStream.html) và [`ByteArrayOutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ByteArrayOutputStream.html).
- Java SE 17: [`StringReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/StringReader.html), [`StringWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/StringWriter.html), [`CharArrayReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/CharArrayReader.html), [`CharArrayWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/CharArrayWriter.html).
- Java SE 17: [`InputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html), [`OutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html), [`DataInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html), [`DataOutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutputStream.html).
- Java SE 17: [`GZIPInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPInputStream.html) và [`GZIPOutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPOutputStream.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="in-memory-io-010">Giới hạn dung lượng khi tích lũy toàn bộ nội dung</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn dung lượng khi tích lũy toàn bộ nội dung trong I/O bộ nhớ

Đọc toàn bộ dữ liệu vào RAM rất tiện khi làm việc với một tệp cấu hình nhỏ, phản hồi ngắn hoặc dữ liệu cần kiểm tra lại nhiều lần. Trong Java, các lớp như `ByteArrayOutputStream`, `CharArrayWriter`, `StringWriter` và `StringBuilder` cho phép **tích lũy dữ liệu ngày càng nhiều trong bộ nhớ**. Các phương thức `InputStream.readAllBytes()` hoặc `Files.readAllBytes(path)` cũng tạo kết quả chứa toàn bộ dữ liệu.

Nhưng kiểu xử lý này có **giới hạn thực tế**: dung lượng heap, giới hạn độ dài mảng, kích thước phần dữ liệu được giữ lại và bản sao tạm khi chuyển sang `byte[]`, `char[]` hoặc `String`. Khi nguồn lớn hoặc không đáng tin cậy, “đọc hết rồi mới xử lý” có thể gây `OutOfMemoryError` hoặc làm bộ gom rác hoạt động quá tải.

Bài này tập trung vào cơ chế tăng dung lượng, cách áp một **giới hạn có kiểm tra**, và khi nào chuyển sang xử lý từng khối thay vì gom toàn bộ nội dung.

## 1. “I/O trong bộ nhớ” gồm những gì?

Trong Java, luồng I/O thường có nguồn hoặc đích là tệp, socket hay tài nguyên ngoài. Với I/O trong bộ nhớ, nguồn/đích là đối tượng giữ dữ liệu trong RAM.

| API | Vai trò | Điểm cần biết về bộ nhớ |
|---|---|---|
| `ByteArrayOutputStream` | Gom các byte được ghi vào một vùng nhớ | Vùng đệm tự mở rộng; `toByteArray()` tạo bản sao dữ liệu |
| `ByteArrayInputStream` | Đọc từ mảng `byte[]` đã có | Thường dùng mảng có sẵn, không tự tải thêm dữ liệu |
| `CharArrayWriter` | Gom ký tự `char` vào bộ nhớ | Mảng ký tự tự mở rộng; `toCharArray()` tạo bản sao |
| `CharArrayReader` | Đọc từ `char[]` đã có | Phù hợp dữ liệu ký tự đã nằm trong RAM |
| `StringWriter` | Gom văn bản trong buffer ký tự | Buffer tăng dần theo lượng văn bản ghi |
| `StringReader` | Đọc từ `String` đã có | Không giải quyết vấn đề nếu `String` gốc quá lớn |
| `StringBuilder` | Ghép chuỗi ký tự trong RAM | `append(...)` có thể làm buffer tăng rất lớn |
| `InputStream.readAllBytes()` | Đọc toàn bộ byte còn lại của luồng thành mảng | Có thể tiêu thụ nhiều RAM; không phù hợp nguồn lớn |
| `Files.readString(path)` | Đọc nội dung văn bản của tệp thành `String` | Cần giữ toàn bộ kết quả dưới dạng chuỗi |
| `Files.readAllLines(path)` | Gom mọi dòng thành `List<String>` | Ngoài ký tự còn có đối tượng `List` và `String` của từng dòng |

**Lưu ý:** I/O “trong bộ nhớ” không có nghĩa là dung lượng **không giới hạn**. Khả năng mở rộng buffer chỉ có nghĩa API có thể cấp phát thêm cho đến khi chạm giới hạn của mảng hoặc môi trường thực thi.

## 2. Kích thước dữ liệu, dung lượng vùng đệm và heap là ba khái niệm khác nhau

Với bộ đệm có thể mở rộng, cần tách rõ:

- **Size / số dữ liệu hợp lệ**: số byte hoặc phần tử ký tự thực sự đã ghi, chẳng hạn `ByteArrayOutputStream.size()`.
- **Capacity / dung lượng cấp phát**: số phần tử buffer nội bộ có thể chứa trước khi cần cấp phát lớn hơn. Capacity có thể lớn hơn size vì thường được tăng theo từng đợt.
- **Heap / ngân sách RAM JVM**: nơi nhiều đối tượng Java sống. Heap cũng phải chứa mã ứng dụng, object khác, các bản sao trung gian và nhiều cấu trúc phụ trợ.

Giả sử một `ByteArrayOutputStream` có size là 1 MiB, nó có thể đã được cấp một buffer có capacity lớn hơn 1 MiB. Khi gọi `toByteArray()`, Java tạo **một mảng mới** chứa chính xác các byte hợp lệ. Trong thời gian đó, buffer nội bộ và mảng kết quả **cùng có thể còn sống**, nên không được tính rằng chỉ cần đúng 1 MiB RAM để tạo kết quả.

`new ByteArrayOutputStream(1024)` chỉ đặt **dung lượng ban đầu** là 1024 byte; nó **không đặt giới hạn tối đa**. Tương tự, `StringBuilder.ensureCapacity(...)` chỉ dự phòng dung lượng, không cấm append thêm sau đó. Ngược lại, `ByteArrayOutputStream.size()` báo tổng dữ liệu đã ghi, **không phải capacity đang cấp phát**.

### Giới hạn của mảng không đồng nhất với heap

Mảng Java dùng chỉ số kiểu `int`, vì vậy độ dài của một mảng không thể vượt quá phạm vi biểu diễn `int`, và giới hạn triển khai thực tế có thể thấp hơn `Integer.MAX_VALUE`. Trong khi đó, heap có thể cạn **từ trước** khi đến giới hạn một mảng.

Ví dụ, yêu cầu một mảng `byte[]` lớn có thể thất bại ngay cả trên máy có tổng RAM rất cao vì:
- JVM có giới hạn heap riêng, chẳng hạn được cấu hình bằng `-Xmx`.
- Heap đã được sử dụng bởi các đối tượng khác.
- Có thêm buffer tạm hoặc bản sao khiến **đỉnh bộ nhớ đồng thời** cao hơn dung lượng dữ liệu gốc.
- JVM không cấp phát được một mảng đủ lớn theo giới hạn/đặc điểm triển khai.

Không dùng `Integer.MAX_VALUE` làm giới hạn an toàn cho một phản hồi web, tệp nhập hoặc thông điệp socket. Hãy xác định giới hạn nghiệp vụ nhỏ hơn đáng kể, phù hợp với quy mô dữ liệu và tổng số yêu cầu đồng thời.

## 3. Vì sao buffer tự tăng có thể gây quá tải?

`ByteArrayOutputStream` giữ một mảng byte bên trong; nếu thao tác ghi tiếp vượt chỗ trống hiện tại, nó phải mở rộng vùng lưu trữ. `CharArrayWriter` và các bộ xây dựng văn bản cũng cần tăng vùng đệm khi dữ liệu lớn dần.

Trong quá trình tăng, triển khai có thể phải cấp mảng mới và sao chép dữ liệu cũ. Ngoài ra, đoạn code sử dụng kết quả có thể phát sinh **bản sao thứ hai**. Cơ chế này thường hợp lý cho dữ liệu nhỏ, nhưng có thể tốn CPU và RAM khi kích thước khó dự đoán.

Ví dụ tưởng như tiện:

~~~java
// Mảnh mã minh họa: input là một InputStream đã mở.
ByteArrayOutputStream memory = new ByteArrayOutputStream();
input.transferTo(memory);
byte[] all = memory.toByteArray();
~~~

Cả `memory` và `all` có thể cùng giữ byte của toàn bộ nguồn. `transferTo(...)` chỉ chuyển từ một luồng sang một luồng khác; khi **đích vẫn là `ByteArrayOutputStream`**, chương trình vẫn tích lũy toàn bộ dữ liệu vào RAM. Đừng coi `transferTo` là giải pháp streaming tiết kiệm bộ nhớ nếu nơi nhận vẫn là một buffer trong bộ nhớ.

Tương tự:

~~~java
// Mảnh mã minh họa: reader là Reader đã mở.
StringWriter writer = new StringWriter();
reader.transferTo(writer);
String allText = writer.toString();
~~~

Đầu ra vẫn được gom toàn bộ. Cách dùng API trông giống “stream” nhưng **đích quyết định mô hình lưu trữ**.

## 4. readAllBytes và readString: tiện nhưng không mặc nhiên an toàn

`InputStream.readAllBytes()` đọc tất cả các byte còn lại đến EOF rồi trả về `byte[]`. Tài liệu JDK 17 nhấn mạnh API này phục vụ **trường hợp đơn giản khi dữ liệu vừa bộ nhớ**, không dành cho luồng có dữ liệu lớn. Nó có thể phát sinh `OutOfMemoryError` nếu không thể cấp phát mảng cần thiết.

~~~java
// Mảnh mã minh họa: source là InputStream đã mở.
byte[] data = source.readAllBytes();
~~~

Các biến thể phổ biến có điểm khác nhau:

| Cách làm | Đặc điểm |
|---|---|
| `source.readAllBytes()` | Đọc đến EOF, kích thước không được giới hạn bằng tham số |
| `source.readNBytes(limit)` | Chỉ đọc **tối đa** `limit` byte và trả mảng tối đa kích thước đó |
| `source.readNBytes(buffer, off, len)` | Cố gắng điền mảng có sẵn, chỉ đọc tối đa `len` byte; trả số byte thực tế |
| `Files.readAllBytes(path)` | Đọc toàn tệp vào một mảng; phù hợp tệp nhỏ |
| `Files.readString(path, charset)` | Đọc thành một `String`; cần ngân sách bộ nhớ cho cả dữ liệu đọc và chuỗi |
| `Files.readAllLines(path, charset)` | Giữ tất cả dòng trong `List<String>` |
| `BufferedReader.readLine()` trong vòng lặp | Có thể xử lý từng dòng, không cần gom toàn bộ tệp; riêng dòng rất dài vẫn tạo `String` lớn |

**Phân biệt việc “đọc tối đa N” và “từ chối nếu nguồn có hơn N”:**

`readNBytes(N)` giúp giới hạn lượng dữ liệu trả về trong một lần gọi, nhưng **không tự xác nhận rằng nguồn chỉ dài N byte**. Nếu nguồn có N+1 byte, lời gọi đầu tiên vẫn trả đúng N byte mà không thông báo “quá giới hạn”. Muốn từ chối nguồn vượt N, phải kiểm tra thêm có byte tiếp theo hoặc dùng vòng lặp có bộ đếm và quy tắc vượt ngưỡng.

`available()` cũng **không** được dùng để suy ra tổng kích thước nguồn: hợp đồng của `InputStream.available()` chỉ mô tả ước lượng số byte có thể đọc mà không bị chặn, không phải độ dài toàn luồng.

## 5. Cách đặt giới hạn tích lũy theo số byte

Khi thật sự cần tạo kết quả `byte[]`, đặt giới hạn rõ ràng **trước khi đọc**, chẳng hạn 1 MiB hoặc 10 MiB tùy nghiệp vụ, và kiểm tra trong từng lần đọc. Không chờ đến sau khi `toByteArray()` mới so sánh độ dài: khi đó tài nguyên đã được sử dụng.

Giả sử ngân sách được đặt là `maxBytes`. Sau khi đọc được `n > 0` byte:

~~~java
// Mảnh mã minh họa: total, maxBytes và n là số không âm hợp lệ.
if (n > maxBytes - total) {
    throw new IOException("Nguồn vượt giới hạn");
}
total += n;
~~~

So sánh `n > maxBytes - total` tránh tính `total + n` trước khi kiểm tra; cách này hữu ích để giảm nguy cơ tràn số khi chương trình mở rộng sang kích thước lớn. Bất biến cần duy trì là `0 <= total <= maxBytes`. Khi theo dõi số byte rất lớn trong một pipeline truyền dữ liệu, có thể sử dụng `long` cho tổng, nhưng **một mảng `byte[]` đơn lẻ vẫn bị giới hạn độ dài kiểu `int`**.

### Ví dụ chạy được: trả về mảng chỉ khi nguồn không vượt giới hạn

Tạo file **`BoundedMemoryReadDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class BoundedMemoryReadDemo {
    public static void main(String[] args) throws IOException {
        byte[] source = "abcdefghi".getBytes(StandardCharsets.US_ASCII);

        try (InputStream in = new ByteArrayInputStream(source)) {
            byte[] result = readAtMost(in, 9);
            System.out.println("length=" + result.length);
            System.out.println("text=" +
                    new String(result, StandardCharsets.US_ASCII));
        }

        try (InputStream in = new ByteArrayInputStream(source)) {
            readAtMost(in, 8);
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static byte[] readAtMost(InputStream input, int maxBytes)
            throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes < 0");
        }

        ByteArrayOutputStream output =
                new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[4];
        int total = 0;
        int count;

        while ((count = input.read(buffer)) != -1) {
            if (count > maxBytes - total) {
                throw new IOException(
                        "Vượt giới hạn " + maxBytes + " byte");
            }
            output.write(buffer, 0, count);
            total += count;
        }
        return output.toByteArray();
    }
}
~~~

Biên dịch/chạy với JDK 17+:

~~~bash
javac -encoding UTF-8 BoundedMemoryReadDemo.java
java BoundedMemoryReadDemo
~~~

Stdout:

~~~text
length=9
text=abcdefghi
Vượt giới hạn 8 byte
~~~

Giải thích:

1. Mẫu dữ liệu có 9 byte ASCII. Với `maxBytes=9`, cả ba lượt đọc qua buffer dài 4 byte (4 + 4 + 1) đều hợp lệ và phương thức trả mảng chính xác.
2. Với `maxBytes=8`, sau khi tích lũy 8 byte, vòng lặp nhận thêm 1 byte. Điều kiện `count > maxBytes - total` đúng và ném `IOException` trước khi ghi phần vượt giới hạn vào kết quả.
3. Bộ đệm khởi đầu của `ByteArrayOutputStream` được chọn nhỏ và hợp lý; tham số constructor không đặt hard limit. **Chính điều kiện trong vòng lặp mới thực thi giới hạn nghiệp vụ**.
4. Vì kết quả phải là `byte[]`, `toByteArray()` tạo bản sao. Giới hạn 9 byte bảo vệ khối dữ liệu trong ví dụ, nhưng khi áp dụng cho kích thước lớn còn phải tính thêm bộ nhớ đỉnh do bản sao.
5. Để tái sử dụng hàm với nguồn không đáng tin cậy, cân nhắc deadline, hủy thao tác hoặc kiểm soát đầu vào có thể chặn vô thời hạn. Giới hạn dung lượng **khác** giới hạn thời gian.

`readAtMost` ở trên giả định `maxBytes` phù hợp với ngân sách RAM và kiểu `int`. Với hệ thống thực tế, chỉ số giới hạn phải đến từ cấu hình/chính sách ứng dụng được kiểm soát; không lấy trực tiếp một giá trị cực lớn do bên gửi đề xuất.

## 6. Các cách giới hạn khác: chỉ đọc phần đầu, kiểm tra dư một byte, báo lỗi

Nếu chỉ cần **xem trước** tối đa N byte (preview), `input.readNBytes(N)` thích hợp, vì ứng dụng chủ ý bỏ qua phần còn lại. Nhưng phải gọi kết quả đó là **preview**, không phải “toàn bộ tài liệu”.

Nếu cần **đọc toàn bộ khi kích thước không quá N và báo lỗi nếu nhiều hơn**, có thể dùng:

1. Đọc tối đa N byte vào một mảng hoặc buffer.
2. Nếu đã đọc đủ N byte, thử đọc **thêm một byte** để phân biệt vừa đủ N với nguồn dài hơn N.
3. Nếu lần thử trả `-1`, nguồn kết thúc đúng ngưỡng; nếu trả byte từ `0` đến `255`, nguồn vượt giới hạn.

Lưu ý ở trường hợp N bằng 0: phải kiểm tra một byte trước khi kết luận nguồn rỗng. Cách này có thể tiêu thụ thêm một byte từ input khi nguồn vượt ngưỡng. Nếu cần giữ lại byte đó, ứng dụng phải dùng nguồn có khả năng đẩy lại/đánh dấu, hoặc chọn giao thức phù hợp. Cách lặp `readAtMost` ở trên chấp nhận việc đọc một phần buffer khiến số byte đã tiêu thụ từ nguồn có thể lớn hơn giới hạn, dù nó không tích lũy những byte vượt hạn mức.

Một số bài toán có yêu cầu bảo toàn stream khi không hợp lệ; khi đó thiết kế cơ chế giới hạn ở ranh giới đọc hoặc quản lý frame dữ liệu, thay vì chỉ nhận kết quả quá giới hạn sau khi đọc.

## 7. Xử lý từng khối: giải pháp thường tốt nhất khi không cần giữ toàn bộ

Nếu mục tiêu là **sao chép**, **tính checksum**, **nén**, **mã hóa theo dòng/khối** hoặc truyền dữ liệu tới nơi khác, có thể xử lý liên tục bằng một bộ đệm kích thước cố định mà không giữ tất cả nguồn trong RAM.

~~~java
// Mảnh mã minh họa: input/output là các stream đã mở.
byte[] buffer = new byte[8192];
int n;
while ((n = input.read(buffer)) != -1) {
    output.write(buffer, 0, n);
}
~~~

Ý nghĩa:

- `buffer` chỉ cấp phát cố định 8192 byte để dùng lại.
- `read(...)` trả **số byte thực tế**, có thể ít hơn buffer; chỉ ghi `n` byte vừa nhận.
- Nguồn có kích thước lớn không khiến bộ đệm này tự phình lên. Tổng byte đi qua có thể rất lớn, nhưng **bộ đệm của vòng lặp** vẫn giữ nguyên kích thước.
- Đích là yếu tố quyết định: ghi ra `FileOutputStream` hoặc một đích hỗ trợ streaming sẽ tránh tích lũy toàn bộ; nếu `output` chính là `ByteArrayOutputStream` thì dữ liệu vẫn gom hết trong RAM.
- Với nguồn tệp, đóng stream bằng `try-with-resources`; nếu dữ liệu nhạy cảm, còn phải cân nhắc thời gian sống của các mảng trung gian.

`InputStream.transferTo(OutputStream)` (Java 9+) cũng phục vụ việc truyền byte từ input sang output đến EOF, nhưng **không áp sẵn dung lượng tối đa**. Nếu cần giới hạn tổng byte sao chép, vẫn phải tự đếm hoặc bao một lớp giới hạn trước khi truyền.

### Ví dụ hoàn chỉnh: sao chép file mà không load toàn bộ

Tạo file **`StreamingFileCopyDemo.java`**:

~~~java
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class StreamingFileCopyDemo {
    public static void main(String[] args) throws IOException {
        Path source = Files.createTempFile("copy-source-", ".bin");
        Path target = Files.createTempFile("copy-target-", ".bin");

        try {
            Files.write(source,
                    "one\ntwo\n".getBytes(StandardCharsets.UTF_8));

            long total = 0;
            try (InputStream in = Files.newInputStream(source);
                 OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[3];
                int n;

                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    total += n;
                }
            }

            System.out.println("bytes=" + total);
            System.out.println("same="
                    + (Files.mismatch(source, target) == -1));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 StreamingFileCopyDemo.java
java StreamingFileCopyDemo
~~~

Stdout:

~~~text
bytes=8
same=true
~~~

Dữ liệu `"one\ntwo\n"` có 8 byte UTF-8. Mảng `buffer` trong ví dụ chỉ dài 3 byte, khiến chương trình bắt buộc lặp nhiều lần. `Files.mismatch(...)` (có từ Java 12) xác nhận hai tệp giống byte theo byte mà không cần tự gọi `readAllBytes()`.

Trong ứng dụng thật, file tạm chỉ dùng để minh họa cách sao chép và kiểm tra. Khi sao chép từ nguồn do người dùng cung cấp, phải kiểm tra nguồn/đích, quyền ghi, ngân sách thời gian và bảo đảm không vô tình ghi đè dữ liệu quan trọng.

## 8. Giới hạn khi tích lũy văn bản: byte khác ký tự

Khi dữ liệu là văn bản, cần phân biệt hai loại giới hạn:

- **Giới hạn theo byte** hữu ích cho kích thước file, lưu trữ, băng thông và bảo vệ dữ liệu đầu vào dạng byte.
- **Giới hạn theo ký tự** hữu ích cho chiều dài nội dung sau khi giải mã. Trong Java, `String.length()`, `StringBuilder.length()` và `CharArrayWriter.size()` đếm **đơn vị mã UTF-16** (thường tương ứng với `char`), không nhất thiết bằng số Unicode code point hay số “ký tự người dùng nhìn thấy”.

Ví dụ chuỗi `"A🙂B"` có ba Unicode code point nhưng `String.length()` là 4 vì emoji được biểu diễn bằng hai đơn vị UTF-16. Trong UTF-8, dữ liệu này có 6 byte: `A` (1) + emoji (4) + `B` (1).

Do đó, một giới hạn “10.000 ký tự” phải định nghĩa rõ đang tính theo code point, đơn vị UTF-16 hay độ dài hiển thị; nếu mục tiêu là giới hạn RAM khi tích lũy, còn phải tính encoding và cấu trúc chứa.

### Ví dụ giới hạn StringBuilder theo đơn vị UTF-16

~~~java
// Mảnh mã minh họa: chunk là phần văn bản nhận được từ reader.
int maxUnits = 1000;
StringBuilder builder = new StringBuilder();
if (chunk.length() > maxUnits - builder.length()) {
    throw new IllegalArgumentException("Vượt số đơn vị UTF-16");
}
builder.append(chunk);
~~~

Cách kiểm tra trên chỉ giới hạn lượng **nội dung** tích lũy. Nó không cho một mức cam kết chính xác về byte heap, bởi triển khai `String`/`StringBuilder` và overhead bộ nhớ có thể khác nhau. Nếu cần giới hạn ký tự hiển thị hoặc Unicode code point, thuật toán phải xét thêm quy tắc Unicode phù hợp.

Đối với dữ liệu streaming UTF-8, **không nên** chuyển từng mảng byte thành `String` độc lập khi một ký tự nhiều byte có thể nằm ở ranh giới hai lần đọc; dùng `InputStreamReader` hoặc `CharsetDecoder` để giải mã có trạng thái qua các khối.

## 9. reset và close: có giải phóng bộ nhớ đang giữ không?

`ByteArrayOutputStream.reset()` và `CharArrayWriter.reset()` đặt **số lượng dữ liệu hợp lệ về 0**, để có thể ghi một nội dung khác bằng buffer đã cấp phát. Chúng **không bắt buộc giảm capacity**; thực tế các lớp này giữ lại vùng đệm để tái sử dụng.

Với hai lớp bộ nhớ này, `close()` không giải phóng buffer nội bộ theo nghĩa “trả RAM ngay”; các phương thức vẫn có thể sử dụng sau khi đóng theo hợp đồng riêng của lớp. Khi object không còn được tham chiếu, bộ gom rác mới có cơ hội thu hồi vùng nhớ tương ứng.

Đừng dùng `reset()` như cơ chế chống tràn bộ nhớ khi một object buffer đã từng phình quá lớn và được giữ sống lâu. Với hệ thống phục vụ nhiều request, giữ lại một buffer từng nhận payload lớn có thể khiến heap bị chiếm không cần thiết; sau khi xử lý request nên bỏ tham chiếu đến buffer lớn nếu không muốn tái sử dụng với cùng capacity.

### Ví dụ chạy được: size, toByteArray và reset

Tạo file **`MemoryBufferReuseDemo.java`**:

~~~java
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class MemoryBufferReuseDemo {
    public static void main(String[] args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(2);

        buffer.writeBytes("abc".getBytes(StandardCharsets.UTF_8));
        byte[] snapshot = buffer.toByteArray();
        snapshot[0] = (byte) 'Z';

        System.out.println("size=" + buffer.size());
        System.out.println("stored="
                + buffer.toString(StandardCharsets.UTF_8));

        buffer.reset();
        System.out.println("afterReset=" + buffer.size());

        buffer.writeBytes("XY".getBytes(StandardCharsets.UTF_8));
        System.out.println("reused="
                + buffer.toString(StandardCharsets.UTF_8));
    }
}
~~~

~~~bash
javac -encoding UTF-8 MemoryBufferReuseDemo.java
java MemoryBufferReuseDemo
~~~

Stdout:

~~~text
size=3
stored=abc
afterReset=0
reused=XY
~~~

Dù constructor chỉ yêu cầu capacity ban đầu bằng 2, buffer vẫn chứa được ba byte `abc` vì nó **tự mở rộng**, chứng minh tham số đó không phải hard limit. Việc sửa `snapshot[0]` thành `Z` không đổi nội dung buffer vì `toByteArray()` đã trả **bản sao**. Sau `reset()`, số byte hợp lệ bằng 0; ghi `XY` tạo nội dung mới. Chương trình không kết luận capacity đã giảm, vì API không cung cấp getter công khai để truy xuất chính xác capacity của `ByteArrayOutputStream`.

## 10. Kiểm tra kích thước trước và trong khi đọc

Đối với một file có `Path`, `Files.size(path)` trả kích thước hiện tại theo byte, hữu ích để **từ chối sớm** file quá lớn:

~~~java
// Mảnh mã minh họa: path là Path hợp lệ, maxBytes là giới hạn đã xác định.
if (Files.size(path) > maxBytes) {
    throw new IOException("File quá lớn");
}
~~~

Nhưng kiểm tra ban đầu **không thay thế** kiểm tra lúc đọc. Tệp có thể thay đổi sau thời điểm đo, và một luồng mạng, luồng nén hoặc luồng sinh động không có kích thước đáng tin trước khi đọc.

Nếu dữ liệu đã **giải nén**, giới hạn kích thước tệp nén trên đĩa không đảm bảo nội dung giải nén nhỏ; phải theo dõi kích thước **sau giải nén** khi tích lũy nội dung. Tương tự, một HTTP header khai báo kích thước có thể không có, sai hoặc không đáng tin; phải giới hạn byte thực nhận.

Ngoài giới hạn cho **mỗi** nguồn, hệ thống cần xem xét tổng lượng bộ nhớ cho **nhiều** tác vụ đồng thời. Chẳng hạn 100 request cùng gom tối đa 10 MiB tạo ít nhất một khoảng cỡ 1.000 MiB dữ liệu logic và còn phát sinh các bản sao/overhead khác; việc từng request tuân thủ 10 MiB **không có nghĩa** cả hệ thống nằm trong ngân sách heap.

## 11. Cách chọn chiến lược theo mục tiêu

| Mục tiêu | Chiến lược phù hợp | Điều cần tránh |
|---|---|---|
| Giải mã một payload nhỏ, có giới hạn rõ | Đọc có kiểm tra max byte, rồi giải mã bằng charset tường minh | Gom vô hạn từ nguồn không đáng tin |
| Tải/đọc cả file cấu hình nhỏ | `Files.readString` hoặc `Files.readAllBytes` sau khi đảm bảo phù hợp bộ nhớ | Dùng API tiện lợi cho file hàng GB |
| Sao chép dữ liệu giữa hai file | `InputStream` → buffer cố định → `OutputStream` hoặc `Files.copy` | Sao chép qua `ByteArrayOutputStream` nếu không cần mảng kết quả |
| Xử lý log theo dòng | `BufferedReader.readLine()` trong vòng lặp | `Files.readAllLines` cho log có thể rất lớn |
| Tạo một chuỗi nhỏ từ nhiều phần | `StringBuilder`/`StringWriter` với giới hạn độ dài | Ghép vô hạn từ input streaming |
| Xử lý dữ liệu có thể dài không xác định | Streaming, chunking, pagination hoặc giới hạn đầu vào | Coi `readAllBytes()` là thao tác vô hại |
| Ghi ra network/file theo từng gói | Xử lý và ghi từng khối, theo dõi tổng và lỗi | Buffer toàn bộ dữ liệu trong RAM trước khi bắt đầu ghi |

Một vài quy tắc áp dụng:

1. **Biết giới hạn nghiệp vụ trước khi tích lũy**. Một giá trị mặc định rõ ràng như 1 MiB là ví dụ, không phải giới hạn phổ quát.
2. **Kiểm tra khi đọc/append**, không chỉ sau khi hoàn thành.
3. **Tính thêm bản sao** khi gọi `toByteArray()`, `toCharArray()`, `toString()`, giải mã text, gom List hoặc tạo những đối tượng trung gian.
4. **Đừng trộn byte với ký tự**. Dung lượng UTF-8 không thể suy ra đơn giản từ `String.length()`; số code point lại là khái niệm khác.
5. **Dùng streaming khi đầu ra không cần dữ liệu toàn phần**. Streaming chỉ thật sự tiết kiệm khi đích và pipeline không gom lại toàn bộ.
6. **Quản lý tài nguyên đúng loại**. `ByteArrayOutputStream.close()` không phải thao tác giải phóng heap; ngược lại, các luồng tệp/socket phải được đóng bằng `try-with-resources`.
7. **Tách lỗi quá giới hạn khỏi lỗi I/O** để báo đúng nguyên nhân, và từ chối dữ liệu lớn bằng thông báo rõ ràng thay vì trông chờ bắt `OutOfMemoryError`. `OutOfMemoryError` có thể khiến JVM ở trạng thái thiếu tài nguyên nghiêm trọng, không phải nhánh xử lý thông thường.

## 12. Tóm tắt

- Các buffer bộ nhớ như `ByteArrayOutputStream`, `CharArrayWriter` và `StringBuilder` **tự tăng dung lượng**, không tự đặt giới hạn số byte/ký tự phù hợp ứng dụng.
- `size()` là lượng dữ liệu hợp lệ; **capacity** có thể lớn hơn và **heap** còn phải chứa các đối tượng khác. `toByteArray()`/`toCharArray()` có thể làm tăng đỉnh bộ nhớ vì tạo bản sao.
- `readAllBytes()`, `readString()` và `readAllLines()` phù hợp với dữ liệu nhỏ hoặc đã được kiểm soát; không áp dụng tùy tiện cho nguồn không biết kích thước.
- Muốn giữ toàn bộ kết quả thì kiểm tra **giới hạn trong từng lần đọc** và cân nhắc bộ nhớ của bản sao; muốn chỉ preview thì dùng API có giới hạn và không giả định đó là toàn bộ nguồn.
- Nếu không cần tích lũy, xử lý **theo từng khối** để dung lượng buffer cố định, đồng thời kiểm soát đích ghi.
- `reset()` tái sử dụng buffer nhưng không nhất thiết giải phóng capacity; chỉ bỏ các tham chiếu không còn cần mới giúp bộ gom rác có thể thu hồi.
- Đặt giới hạn cả theo **byte**, **ký tự**, **số công việc đồng thời** và **thời gian** tương ứng mục tiêu hệ thống.

### Tài liệu chính thức

- [Java 17 — ByteArrayOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ByteArrayOutputStream.html)
- [Java 17 — CharArrayWriter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/CharArrayWriter.html)
- [Java 17 — InputStream.readAllBytes/readNBytes/transferTo](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html)
- [Java 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)


</details>

- [Quay lại đầu trang](#back-to-top)
