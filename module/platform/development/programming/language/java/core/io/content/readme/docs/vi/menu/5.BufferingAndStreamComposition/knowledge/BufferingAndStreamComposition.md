<a id="back-to-top"></a>

# Bộ đệm và ghép luồng

## Menu
- [Mục đích của BufferedInputStream và BufferedOutputStream](#buffering-and-stream-composition-001)
- [BufferedReader và BufferedWriter](#buffering-and-stream-composition-002)
- [Decorator: lớp bọc bổ sung hành vi cho stream](#buffering-and-stream-composition-003)
- [Thứ tự ghép stream, buffer, charset và định dạng](#buffering-and-stream-composition-004)
- [Kích thước bộ đệm và số lần gọi I/O](#buffering-and-stream-composition-005)
- [flush đẩy bộ đệm phần mềm nhưng không đồng nghĩa dữ liệu bền vững](#buffering-and-stream-composition-006)
- [close lớp ngoài cùng và việc đóng tài nguyên bên dưới](#buffering-and-stream-composition-007)
- [Mark, reset, markSupported và readlimit](#buffering-and-stream-composition-008)
- [Reader.ready khác kiểm tra EOF như thế nào](#buffering-and-stream-composition-009)
- [Tránh trộn nhiều lớp đọc có bộ đệm trên cùng một nguồn](#buffering-and-stream-composition-010)

## <a id="buffering-and-stream-composition-001">Mục đích của BufferedInputStream và BufferedOutputStream</a>

<details>
<summary>Xem chi tiết</summary>

# Mục đích của BufferedInputStream và BufferedOutputStream trong Java I/O

Khi làm việc với file, socket hoặc nguồn dữ liệu khác, thao tác đọc/ghi **mỗi lần một byte** thường khiến chương trình gọi xuống nguồn hoặc đích quá nhiều lần. **BufferedInputStream** và **BufferedOutputStream** là hai lớp bọc luồng byte thuộc gói **java.io**, sử dụng một vùng nhớ đệm để **gom nhiều thao tác nhỏ thành những lần đọc/ghi theo khối**. Mục tiêu chính là giảm overhead của lời gọi đến luồng bên dưới; cả hai vẫn xử lý **byte**, không tự chuyển đổi charset hay thay đổi nội dung nhị phân.

## 1. BufferedInputStream và BufferedOutputStream giải quyết vấn đề gì?

Giả sử muốn đọc 100.000 byte của một file, xử lý từng byte rồi ghi ra file khác. Nếu gọi **FileInputStream.read()** và **FileOutputStream.write(int)** riêng lẻ cho mỗi byte, chương trình có thể phải thực hiện rất nhiều thao tác xuống các luồng nền, tùy cách các lớp đó được triển khai. Chi phí kiểm tra trạng thái, chuyển qua các tầng Java/native và giao tiếp thiết bị hoặc hệ điều hành có thể lớn hơn công việc xử lý một byte.

Một **buffer** là mảng byte trong bộ nhớ JVM. Thay vì xuống nguồn 100.000 lần để đáp ứng 100.000 lần **read()**, một **BufferedInputStream** có thể lấy một khối lớn và phục vụ nhiều lần đọc nhỏ từ khối trong RAM. Chiều ghi tương tự: **BufferedOutputStream** tích lũy các byte được ghi rồi gửi từng khối xuống luồng bên dưới.

| Lớp | Bao bọc loại luồng | Mục đích chính | Cách hoạt động |
| --- | --- | --- | --- |
| **BufferedInputStream** | **InputStream** | Giảm số lần đọc nhỏ từ nguồn | Nạp byte theo khối vào buffer, trả dần cho các lần đọc tiếp theo |
| **BufferedOutputStream** | **OutputStream** | Giảm số lần ghi nhỏ xuống đích | Giữ byte trong buffer rồi đẩy xuống luồng nền khi đầy hoặc khi flush/close |
| **BufferedReader** | **Reader** | Đệm dữ liệu ký tự, hỗ trợ đọc dòng | Làm việc với **char**, không thay thế luồng byte cho dữ liệu nhị phân |
| **BufferedWriter** | **Writer** | Đệm các lần ghi ký tự | Làm việc với văn bản, không dùng để sao chép byte tùy ý |

**Buffering không làm cho CPU, ổ đĩa hay mạng tự nhanh hơn**. Nó giúp giảm số lời gọi nhỏ và overhead, khi cấu hình và kiểu truy cập phù hợp. Với những API đã truyền dữ liệu theo khối lớn, việc bọc thêm buffer có thể mang lại ít lợi ích.

## 2. Cơ chế của BufferedInputStream

### 2.1. Vùng đệm đọc hoạt động như thế nào?

Ví dụ ta bọc một InputStream bằng buffer có dung lượng 4 byte:

~~~text
Nguồn dữ liệu: A B C D E F G H I J
                │
                │ nạp một khối khi cần
                ▼
       [ A | B | C | D ]    buffer bên trong
         ▲
         └── read() trả 'A'; lần gọi kế tiếp đọc 'B' từ RAM

Khi hết A..D, buffer được nạp khối E..H, rồi I..J.
~~~

Đây là **mô hình khái niệm** của những lần **read()** nhỏ: buffer giúp tái sử dụng dữ liệu đã đọc. Thực tế Java còn có tối ưu hóa: khi người gọi yêu cầu **một khối rất lớn** bằng **read(byte[], off, len)**, BufferedInputStream có thể đọc trực tiếp vào mảng đích, bỏ qua việc sao chép không cần thiết qua buffer, tùy trạng thái mark và triển khai JDK.

Khi tạo luồng:

~~~java
InputStream buffered = new BufferedInputStream(originalStream);
~~~

hoặc chỉ định kích thước buffer:

~~~java
InputStream buffered = new BufferedInputStream(originalStream, 16 * 1024);
~~~

**originalStream** là một **InputStream đã được tạo trước**, ví dụ từ **Files.newInputStream(path)**. Hai đoạn trên là *mảnh code minh họa*, không phải chương trình chạy độc lập. Constructor nhận **size** dương; kích thước bằng 0 hoặc âm sẽ gây **IllegalArgumentException**. Nếu không truyền size, JDK dùng dung lượng mặc định do triển khai quyết định; đừng dựa vào con số mặc định để làm giao thức.

Khi các byte đang nằm trong buffer, lần **read()** kế tiếp thường lấy dữ liệu trực tiếp từ RAM. Khi buffer hết và luồng nguồn còn mở, lần đọc tiếp theo phải nạp thêm dữ liệu nên **vẫn có thể chặn**, hoặc ném **IOException**. Buffering **không bỏ được timeout, EOF hay lỗi đọc từ nguồn**.

### 2.2. Đọc từng byte và đọc theo khối

Với **BufferedInputStream.read()**, mỗi lời gọi trả một số nguyên từ **0 đến 255** hoặc **-1** khi EOF. Byte **0x00** là dữ liệu bình thường; đừng coi nó là EOF. Với **read(byte[])**, số byte thực nhận có thể nhỏ hơn dung lượng mảng, kể cả khi chưa EOF. Số byte hợp lệ luôn là **count** được trả về.

Ví dụ:

~~~java
byte[] chunk = new byte[8192];
int count;
while ((count = buffered.read(chunk)) != -1) {
    // Chỉ xử lý chunk[0] đến chunk[count - 1].
}
~~~

Mảnh này giả định đã có **buffered** kiểu **InputStream**. Trường hợp đọc khối lớn, bản thân **read(byte[])** đã giảm số lần gọi so với đọc từng byte; vì thế **lợi ích của lớp BufferedInputStream bổ sung có thể nhỏ hoặc không đáng kể**. Cần đo bằng workload thật thay vì luôn bọc nhiều tầng buffer.

### 2.3. mark() và reset(): tiện ích đáng chú ý

**BufferedInputStream** hỗ trợ **mark() / reset()** ngay cả khi nguồn nền không tự hỗ trợ đánh dấu. Điều này hữu ích khi parser cần xem trước vài byte để xác định định dạng, sau đó quay về vị trí đã đánh dấu.

- Gọi **mark(readlimit)** để đánh dấu vị trí hiện tại và khai báo số byte có thể đọc trước khi mark không còn hợp lệ.
- Gọi **reset()** để trở về vị trí đánh dấu nếu mark vẫn hợp lệ.
- **markSupported()** trả về **true** với BufferedInputStream.
- Nếu đọc quá giới hạn làm mark mất hiệu lực, **reset()** có thể ném **IOException**.
- Giữ mark với readlimit lớn có thể làm buffer phải mở rộng, dẫn đến tăng dùng RAM; đây không phải cơ chế tua ngược cả file tùy ý.

Đây là một lợi ích chức năng ngoài chuyện giảm số lần đọc, nhưng vẫn cần quản lý readlimit và tuổi thọ của luồng.

## 3. Cơ chế của BufferedOutputStream

### 3.1. Gom các lần ghi nhỏ thành lần ghi lớn

Ví dụ đặt buffer 4 byte, rồi gọi **write(int)** từng byte:

~~~text
write('A')  → [ A |   |   |   ]
write('B')  → [ A | B |   |   ]
write('C')  → [ A | B | C |   ]
write('D')  → [ A | B | C | D ]
write('E')  → đẩy A B C D xuống OutputStream nền,
              giữ E trong buffer cho lần tiếp theo.
~~~

Sơ đồ thể hiện nguyên tắc gom byte; thời điểm chi tiết có thể thay đổi theo cách ghi và triển khai. Với mảng lớn có độ dài **>= kích thước buffer**, BufferedOutputStream có thể đẩy phần đang đệm rồi ghi mảng đó **thẳng xuống luồng nền**, tránh một lần sao chép thừa.

Tạo wrapper:

~~~java
OutputStream buffered = new BufferedOutputStream(originalStream, 16 * 1024);
~~~

**originalStream** là OutputStream đã tạo, chẳng hạn **Files.newOutputStream(path)**. Đối với thao tác ghi, điều thiết yếu là **write(...) thành công trên wrapper không nhất thiết có nghĩa byte đã được gửi xuống đích ngay lúc đó**. Một phần hoặc toàn bộ dữ liệu vẫn có thể nằm trong buffer.

### 3.2. Khi nào dữ liệu trong buffer được đẩy ra?

Trong quá trình sử dụng bình thường, buffered output gửi byte xuống luồng bên dưới khi:

1. Buffer đã đầy và chương trình muốn ghi thêm byte.
2. Chương trình chủ động gọi **flush()**.
3. Chương trình gọi **close()** hoặc kết thúc khối **try-with-resources**: dữ liệu đang đệm được flush trong quá trình đóng (trừ khi xảy ra lỗi I/O).

Dùng **flush()** ở ranh giới dữ liệu cần được chuyển ngay, chẳng hạn sau một bản tin ứng dụng hoặc sau khi xuất một phản hồi cần truyền đi khi kết nối còn mở:

~~~java
buffered.write(message);
buffered.flush();
~~~

Đây là mảnh code minh họa, giả định **buffered** kiểu OutputStream và **message** là byte[]. **flush()** yêu cầu đẩy byte từ các bộ đệm của tầng này (và theo hợp đồng của các luồng lồng nhau, việc flush có thể tiếp tục đến tầng bên dưới). Nó **không bảo đảm byte đã được ghi bền vững xuống ổ đĩa vật lý**, không thay cho đồng bộ dữ liệu kiểu **FileChannel.force(...) / FileDescriptor.sync()**, và **không bảo đảm đầu bên kia mạng đã nhận/xử lý bản tin**.

**Không flush sau mỗi byte** nếu mục tiêu là tăng tốc ghi. Làm vậy làm mất phần lớn tác dụng gom các thao tác nhỏ. Nếu thao tác ngắn và có sở hữu wrapper, thường có thể để **close()** trong try-with-resources hoàn tất flush thay vì gọi flush thủ công vô ích ngay trước close.

## 4. Ví dụ Java 17+ có thể chạy: quan sát buffer, flush, mark/reset và bảo toàn byte

Lưu mã sau thành **BufferedStreamsDemo.java**. Ví dụ chỉ dùng bộ nhớ để kết quả lặp lại ổn định, không cần tạo file hay phụ thuộc tốc độ đĩa.

~~~java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public class BufferedStreamsDemo {
    public static void main(String[] args) throws IOException {
        // 1. Buffer đọc và chức năng mark/reset.
        byte[] letters = "ABCDE".getBytes(StandardCharsets.US_ASCII);
        try (BufferedInputStream in = new BufferedInputStream(
                new ByteArrayInputStream(letters), 3)) {
            System.out.println("first=" + (char) in.read());
            in.mark(3);
            byte[] next = in.readNBytes(2);
            System.out.println("next=" +
                    new String(next, StandardCharsets.US_ASCII));
            in.reset();
            System.out.println("after reset=" + (char) in.read());
        }

        // 2. Buffer ghi giữ byte tạm thời trước flush.
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (BufferedOutputStream out =
                     new BufferedOutputStream(sink, 8)) {
            out.write(new byte[] {65, 66, 67}); // A B C
            System.out.println("before flush=" + sink.size());
            out.flush();
            System.out.println("after flush=" + sink.size());
            out.write('D'); // Byte D còn trong buffer cho đến close.
        }
        System.out.println("after close=" + sink.size());
        System.out.println("text=" +
                new String(sink.toByteArray(), StandardCharsets.US_ASCII));

        // 3. Sao chép dữ liệu nhị phân có byte 00 và FF.
        byte[] binary = {0x00, (byte) 0xFF, 0x0A, 0x41};
        ByteArrayOutputStream copyTarget = new ByteArrayOutputStream();
        try (InputStream in = new BufferedInputStream(
                     new ByteArrayInputStream(binary), 2);
             OutputStream out =
                     new BufferedOutputStream(copyTarget, 2)) {
            byte[] chunk = new byte[3];
            int count;
            while ((count = in.read(chunk)) != -1) {
                out.write(chunk, 0, count);
            }
        }
        byte[] copied = copyTarget.toByteArray();
        System.out.println("binary equal=" +
                Arrays.equals(binary, copied));
        System.out.println("binary hex=" +
                HexFormat.of().formatHex(copied));
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac BufferedStreamsDemo.java
java BufferedStreamsDemo
~~~

Kết quả:

~~~text
first=A
next=BC
after reset=B
before flush=0
after flush=3
after close=4
text=ABCD
binary equal=true
binary hex=00ff0a41
~~~

Diễn giải:

- **mark(3)** đặt dấu trước B; đọc BC rồi **reset()** quay lại B, nên dòng **after reset=B**.
- Với output buffer dung lượng 8, ba byte **ABC** chưa đủ để kích hoạt ghi xuống **sink**, vì thế **before flush=0**.
- **flush()** làm sink có 3 byte; thêm D rồi kết thúc try-with-resources sẽ **close()**, dẫn đến flush và sink có 4 byte.
- Đối với byte nhị phân **00 FF 0A 41**, đầu ra khớp chính xác (**Arrays.equals** là true). Buffered streams **không tự giải mã UTF-8 hoặc đổi dữ liệu nhị phân**.

Những con số về kích thước sink trong ví dụ dựa vào việc ghi một mảng 3 byte với buffer dung lượng 8 và được kiểm soát rõ ràng. Trong hệ thống file/mạng thật, thời điểm thiết bị hoặc phía nhận thực sự xử lý dữ liệu còn phụ thuộc những tầng bên dưới.

## 5. Ví dụ thực tế: copy file bằng buffered streams

Muốn sao chép file tùy ý mà không biến byte thành ký tự, có thể kết hợp buffer ở cả hai chiều và vòng lặp đọc/ghi theo khối. Lưu chương trình thành **BufferedFileCopy.java**:

~~~java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class BufferedFileCopy {
    public static void main(String[] args) throws IOException {
        Path source = Path.of("input.bin");
        Path target = Path.of("output.bin");

        try (InputStream in = new BufferedInputStream(
                     Files.newInputStream(source), 16 * 1024);
             OutputStream out = new BufferedOutputStream(
                     Files.newOutputStream(target), 16 * 1024)) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = in.read(chunk)) != -1) {
                out.write(chunk, 0, count);
            }
        }

        System.out.println("identical=" +
                (Files.mismatch(source, target) == -1L));
    }
}
~~~

Lệnh biên dịch/chạy sau khi tạo file **input.bin** trong thư mục hiện tại:

~~~bash
javac BufferedFileCopy.java
java BufferedFileCopy
~~~

Nếu không có lỗi, file nguồn không thay đổi trong lúc sao chép và không có tác nhân khác đồng thời sửa đích, output là:

~~~text
identical=true
~~~

Lưu ý:

- **Files.newInputStream** mở nguồn và **Files.newOutputStream** mở đích. Theo mặc định với Files.newOutputStream(path) không có option, file đích sẽ được tạo nếu chưa có hoặc **bị cắt về rỗng nếu đã tồn tại**; phải chủ động chọn chính sách xử lý file đích để tránh mất dữ liệu.
- **out.write(chunk, 0, count)** là bắt buộc: chỉ **count** byte là vừa nhận, nhất là ở lần đọc cuối. Viết cả buffer có thể khiến byte cũ bị chèn vào file đích.
- Try-with-resources **đóng wrapper**, kéo theo đóng luồng nền. Khi OutputStream được đóng, dữ liệu đang đệm được flush; nếu quá trình này ném IOException, bản sao có thể dở dang.
- **Files.mismatch** (Java 12+) so sánh từng byte; **-1** nghĩa là không tìm thấy byte khác nhau trong hai file được đối chiếu. Chỉ so kích thước file không đủ chứng minh dữ liệu giống nhau.
- Nếu cần đơn giản hơn và không có yêu cầu xử lý từng khối, **Files.copy(source, target)** thường là cách sao chép file trực tiếp. Dùng buffered wrappers khi chương trình **thực sự cần làm việc với luồng**.

### Đọc/ghi từng khối có sẵn buffer còn cần BufferedInputStream/OutputStream?

Không phải lúc nào cũng cần. Với vòng lặp đọc mảng **8192 byte** và ghi mảng theo khối, code đã giảm số lời gọi so với **read() / write(int)** đơn lẻ. Các lớp nền còn có thể tự tối ưu, ví dụ tầng hệ điều hành có page cache hoặc thư viện có bộ đệm riêng. Bọc thêm **BufferedInputStream/BufferedOutputStream** đôi khi tăng thêm sao chép bộ nhớ mà không tăng đáng kể throughput; đôi khi lại có ích nếu các lớp giao tiếp với nguồn đọc chậm hoặc chương trình xen kẽ nhiều thao tác nhỏ. **Đo thực tế** với kích thước khối, loại thiết bị và kiểu truy cập của ứng dụng.

## 6. Ghép luồng (stream composition): thêm chức năng bằng cách bọc lớp

Cả hai lớp đều thuộc mô hình **decorator**: một luồng bọc luồng khác, giữ nguyên giao diện InputStream/OutputStream nhưng thêm buffering. Ta có thể xếp nhiều lớp để kết hợp chức năng.

Ví dụ đọc các kiểu nguyên thủy từ file theo giao thức của **DataInputStream**:

~~~java
try (DataInputStream in = new DataInputStream(
        new BufferedInputStream(Files.newInputStream(path)))) {
    int version = in.readInt();
    // ...
}
~~~

Ví dụ ghi dữ liệu có kiểu bằng **DataOutputStream**:

~~~java
try (DataOutputStream out = new DataOutputStream(
        new BufferedOutputStream(Files.newOutputStream(path)))) {
    out.writeInt(42);
    out.writeUTF("Java");
}
~~~

Hai đoạn trên là **mảnh mã minh họa**, giả định đã import **java.io.*** và **java.nio.file.***, cũng như khai báo **path** kiểu **Path**. **DataInputStream/DataOutputStream** đọc/ghi cấu trúc giá trị nguyên thủy, trong khi lớp Buffered làm nhiệm vụ gom byte; mỗi lớp giữ trách nhiệm riêng.

Một số quy tắc ghép luồng:

1. **Gọi nghiệp vụ ở wrapper ngoài cùng.** Đối với DataOutputStream ở ví dụ, gọi **writeInt** trên DataOutputStream thay vì bỏ qua nó để ghi trực tiếp vào OutputStream nền.
2. **Đóng wrapper ngoài cùng nếu bạn sở hữu toàn bộ chuỗi.** Đóng các decorator thường chuyển tiếp đến luồng nền; đừng tiếp tục sử dụng luồng đã bị đóng.
3. **Tránh đóng các luồng chia sẻ không thuộc sở hữu.** Chẳng hạn wrapper quanh **System.out** thường không nên bị đóng tùy tiện vì nó sẽ đóng standard output của ứng dụng; chỉ flush ở thời điểm phù hợp.
4. **Không xếp buffer lặp lại chỉ vì nghĩ càng nhiều càng nhanh.** BufferedInputStream bọc BufferedInputStream hoặc BufferedOutputStream bọc BufferedOutputStream thường tăng độ phức tạp và có thể tăng copy mà không rõ lợi ích.
5. **Chú ý thứ tự khi có nén/mã hóa.** Đặt buffer ở phía dữ liệu thô hay dữ liệu sau xử lý có thể thay đổi nơi gom khối. Các lớp như GZIPInputStream/GZIPOutputStream thường có cơ chế xử lý block và buffering của riêng chúng; hãy hiểu chuỗi wrapper và benchmark thay vì giả định một thứ tự tối ưu chung.

## 7. Chọn kích thước buffer

Constructor dạng **(stream, size)** cho phép tự chọn kích thước mảng byte nội bộ. Ví dụ **8 KiB (8192 byte)** hoặc **16 KiB (16384 byte)** thường là các giá trị thử nghiệm hợp lý, không phải con số tối ưu cho mọi trường hợp.

**Quá nhỏ:** buffer phải nạp/xả nhiều lần, dẫn đến nhiều lời gọi nền hơn. **Quá lớn:** mỗi luồng giữ nhiều RAM, có thể lãng phí khi đồng thời mở hàng nghìn kết nối hoặc file; lợi ích giảm dần. Ví dụ 10.000 stream, mỗi stream có một buffer 64 KiB, phần dung lượng buffer theo phép nhân lý thuyết lên tới khoảng **625 MiB** chỉ cho một chiều (64 × 10.000 KiB = 640.000 KiB ≈ 625 MiB), chưa kể overhead và các buffer khác.

Nếu dùng 2 chiều input/output cho cùng kết nối, có thể có **hai buffer riêng**. Tài nguyên RAM cần xem theo tổng số wrapper đồng thời sống. Khi benchmark, đo throughput và latency trong workload thật: số lượng lời gọi nhỏ, kích thước mỗi lần, tốc độ đĩa/mạng, nhu cầu flush, độ dài luồng và số kết nối đồng thời.

Các lựa chọn không nên dựa vào **InputStream.available()** để suy ra tổng độ lớn file/stream; nó chỉ ước tính byte đọc ngay không chặn, không phải kích thước cuối cùng hay cơ sở duy nhất để cấp phát.

## 8. Mối quan hệ giữa flush, close, timeout và độ bền dữ liệu

| Hành động | BufferedInputStream | BufferedOutputStream |
| --- | --- | --- |
| **read()** | Trả dữ liệu đang đệm hoặc nạp thêm từ nguồn | Không áp dụng |
| **write()** | Không áp dụng | Ghi vào buffer hoặc đẩy byte đến đích khi thích hợp |
| **flush()** | Không cung cấp (InputStream không có flush) | Đẩy dữ liệu đang đệm xuống OutputStream bên dưới |
| **close()** | Đóng wrapper và luồng nền, giải phóng bộ đệm | Flush dữ liệu còn đệm rồi đóng wrapper/luồng nền; lỗi I/O vẫn có thể xảy ra |
| **mark/reset** | Có hỗ trợ với giới hạn readlimit | Không áp dụng |

**flush() không đồng nghĩa với “đã lưu an toàn trên đĩa”**. Với output file, hệ điều hành có thể còn cache; với socket, việc byte đi khỏi buffer Java cũng chưa có nghĩa phía bên kia xử lý. Muốn yêu cầu độ bền đĩa, cần API đồng bộ phù hợp và hiểu hợp đồng hệ thống file; muốn xác nhận dữ liệu mạng, cần giao thức báo nhận ở tầng ứng dụng khi cần.

**Buffer không thay thế timeout**. Đọc mạng có thể vẫn chặn khi chờ byte mới. Chương trình cần thiết lập timeout tại lớp socket/network thích hợp và xử lý ngắt kết nối. Bản thân BufferedInputStream không tự đặt giới hạn thời gian cho read(), và BufferedOutputStream không làm đích bị chậm trở thành đích không chặn.

**Một write không có nghĩa toàn bộ dữ liệu được giao thành công** nếu sau đó close/flush ném lỗi. Khi dữ liệu quan trọng, kiểm tra lỗi I/O, tính toàn vẹn và trạng thái file/luồng đích.

## 9. Lỗi thường gặp

1. **Nhầm buffered streams với character streams.** BufferedInputStream/OutputStream giữ byte, trong khi BufferedReader/Writer làm việc với ký tự. Dữ liệu nhị phân nên dùng luồng byte.
2. **Quên flush khi kết nối còn mở nhưng cần gửi dữ liệu ngay.** Byte có thể vẫn nằm trong BufferedOutputStream; gọi **flush()** tại ranh giới bản tin cần phát.
3. **Flush sau từng byte.** Làm mất hiệu quả gom nhiều thao tác nhỏ. Gom theo bản tin/khối và flush khi cần.
4. **Quên đóng wrapper khi sở hữu tài nguyên.** Byte cuối có thể chưa được flush và descriptor không được giải phóng. Sử dụng **try-with-resources**.
5. **Nghĩ close() của wrapper chỉ đóng wrapper.** Thông thường nó đóng luôn luồng bên trong; tránh đóng những stream chia sẻ còn cần dùng.
6. **Ghi quá số byte đọc được.** Trong vòng lặp buffer, phải dùng **out.write(buffer, 0, count)**, không ghi cả mảng khi **count < buffer.length**.
7. **Đo hiệu năng chỉ bằng một lần chạy.** Tốc độ còn do page cache, JIT, I/O thực, dữ liệu, hệ điều hành và concurrency; benchmark đúng workload.
8. **Cho rằng buffer size càng lớn càng tốt.** RAM tăng theo số luồng, có khi không giảm được lời gọi nền thêm đáng kể.
9. **Tin rằng flush đồng nghĩa fsync hoặc ACK mạng.** Chúng khác nhau; phải chọn cơ chế xác nhận/durability đúng yêu cầu.
10. **Lạm dụng mark/reset khi cần tua ngược dài.** Mark có readlimit và có thể hết hiệu lực, không thay thế khả năng đọc lại file từ đầu.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** BufferedInputStream có phải biến dữ liệu từ byte thành văn bản không?  
**Đáp:** Không. Nó giữ byte trong mảng đệm, không giải mã ký tự.

**Câu 2.** Nếu ghi "ABC" vào BufferedOutputStream có dung lượng 8, byte đã nhất thiết tới OutputStream nền chưa?  
**Đáp:** Chưa. Nó có thể nằm trong buffer; **flush()** hoặc **close()** sẽ yêu cầu đẩy byte đi.

**Câu 3.** Tại sao ghi từng byte vào FileOutputStream có thể chậm hơn khi bọc bằng BufferedOutputStream?  
**Đáp:** Vì wrapper gom các lần gọi nhỏ thành lần ghi lớn xuống luồng nền, giảm chi phí gọi I/O.

**Câu 4.** Nếu vòng lặp đã đọc **byte[8192]** rồi ghi theo khối, thêm buffer luôn nhanh hơn đúng không?  
**Đáp:** Không. Lợi ích tùy triển khai nguồn/đích, kích thước truy cập, chi phí sao chép bộ nhớ và workload thật.

**Câu 5.** Gọi flush trên buffered output có bảo đảm ổ đĩa đã ghi bền vững hoặc đối tác mạng đã nhận không?  
**Đáp:** Không. Flush đẩy dữ liệu trong tầng buffering Java xuống luồng bên dưới, khác với đồng bộ đĩa hoặc xác nhận ở tầng giao thức.

**Câu 6.** BufferedInputStream có chức năng nào khác ngoài buffering?  
**Đáp:** Nó hỗ trợ **mark/reset**, cho phép quay lại vị trí đã đánh dấu trong giới hạn hợp lệ, kể cả khi InputStream nền không có hỗ trợ riêng.

## 11. Ghi nhớ và chọn API

**BufferedInputStream** giúp gom các lần đọc nhỏ thành ít lần đọc lớn hơn từ nguồn; **BufferedOutputStream** gom các lần ghi nhỏ và cần xả buffer đúng thời điểm qua **flush() / close()**. Cả hai là wrapper byte-stream, có thể ghép với stream nền khác theo decorator, không tự đổi charset và không tự giải quyết timeout hay độ bền dữ liệu.

Nếu đang **sao chép file đơn giản**, cân nhắc **Files.copy(...)**. Nếu xử lý byte theo luồng, cần làm việc từng byte hoặc có nhiều thao tác I/O nhỏ, hai lớp buffered là công cụ phù hợp. Chọn kích thước buffer dựa trên tần suất đọc/ghi, lượng RAM và phép đo thực tế.

### Tài liệu tham khảo

- [Oracle Java SE 17 — BufferedInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedInputStream.html): nguyên tắc buffering, constructor, mark/reset, read.
- [Oracle Java SE 17 — BufferedOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedOutputStream.html): buffering, flush và close.
- [Oracle Java SE 17 — InputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html): đọc byte, read(byte[]), EOF.
- [Oracle Java SE 17 — OutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html): write(byte[], off, len), flush, close.
- [Oracle Java SE 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): newInputStream, newOutputStream, copy và mismatch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buffering-and-stream-composition-002">BufferedReader và BufferedWriter</a>

<details>
<summary>Xem chi tiết</summary>

# BufferedReader và BufferedWriter trong Java I/O

`BufferedReader` và `BufferedWriter` là hai lớp xử lý **luồng ký tự có bộ đệm** trong gói `java.io`. Chúng bọc một `Reader` hoặc `Writer` khác để gom các thao tác nhỏ thành những đợt trao đổi dữ liệu lớn hơn, giảm chi phí gọi liên tục xuống nguồn hoặc đích bên dưới. `BufferedReader` còn có `readLine()` để đọc văn bản theo dòng; `BufferedWriter` có `newLine()` để viết dấu kết thúc dòng phù hợp với môi trường chạy.

Điều quan trọng là hiểu **bộ đệm thuộc luồng ký tự**, **cách xác định dòng**, **khi nào phải flush/close**, và những dữ liệu nào có thể bị thay đổi nếu đọc bằng `readLine()` rồi ghi bằng `newLine()`.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Mô tả cách buffered character streams giảm chi phí I/O và phân biệt buffer nội bộ với mảng `char[]` do bạn cấp.
- Tạo `BufferedReader`, `BufferedWriter` từ `Reader`/`Writer` hoặc từ `Files.newBufferedReader/newBufferedWriter` với charset rõ ràng.
- Sử dụng `readLine()`, `read(char[])`, `write(...)`, `append(...)`, `newLine()` và `flush()` đúng hợp đồng.
- Xử lý chính xác `null` (EOF) và chuỗi rỗng `""` (dòng trống), đồng thời hiểu một lần `read` có thể trả về ít hơn số ký tự được yêu cầu.
- Chọn sao chép theo dòng hay theo khối ký tự tùy việc có cần giữ nguyên ký tự xuống dòng hay không.
- Kiểm soát việc đóng tài nguyên, giới hạn bộ nhớ và các lỗi hay gặp khi làm việc với văn bản Unicode.

## 2. Vị trí của hai lớp trong hệ thống Java I/O

Hai lớp không trực tiếp đại diện cho file hoặc mạng: chúng là **lớp bọc (decorator)** nhận một luồng ký tự đã có.

~~~text
Đọc file hoặc nguồn byte:
byte từ file/socket
    -> InputStream / lớp byte bên dưới
    -> InputStreamReader (giải mã byte theo charset)
    -> BufferedReader (buffer ký tự, readLine)
    -> ứng dụng

Ghi file hoặc đích byte:
ứng dụng
    -> BufferedWriter (buffer ký tự, newLine)
    -> OutputStreamWriter (mã hóa ký tự theo charset)
    -> OutputStream / lớp byte bên dưới
    -> byte đến file/socket
~~~

Với `Files.newBufferedReader(path, charset)` và `Files.newBufferedWriter(path, charset)`, Java tạo chuỗi thành phần cần thiết ở bên dưới; bạn không phải lắp thủ công từng lớp. Đối với text trong bộ nhớ, `StringReader` và `StringWriter` có thể thay thế phần file/byte.

| Thành phần | Đơn vị dữ liệu | Chức năng chính |
| --- | --- | --- |
| `InputStream` / `OutputStream` | Byte | Đọc/ghi dữ liệu nhị phân |
| `InputStreamReader` / `OutputStreamWriter` | Chuyển byte ↔ ký tự | Giải mã/mã hóa theo charset |
| `Reader` / `Writer` | Ký tự Java (UTF-16 `char`) | Hợp đồng trừu tượng đọc/ghi ký tự |
| `BufferedReader` | Ký tự, dòng | Bộ đệm để đọc và `readLine()` |
| `BufferedWriter` | Ký tự | Bộ đệm để ghi và `newLine()` |

**Lưu ý:** bọc `InputStream` bằng `BufferedReader` trực tiếp là sai kiểu: constructor của `BufferedReader` nhận `Reader`. Tương tự, `BufferedWriter` nhận `Writer`, không nhận `OutputStream`. Nếu cần chuyển đổi byte–ký tự, thêm `InputStreamReader` hoặc `OutputStreamWriter` với charset phù hợp.

### Constructor

~~~java
BufferedReader reader = new BufferedReader(existingReader);
BufferedReader customReader = new BufferedReader(existingReader, 16 * 1024);

BufferedWriter writer = new BufferedWriter(existingWriter);
BufferedWriter customWriter = new BufferedWriter(existingWriter, 16 * 1024);
~~~

Đây là **mảnh mã**: `existingReader` và `existingWriter` phải là các biến đã khai báo. Giá trị `16 * 1024` là **số ký tự** trong buffer nội bộ, không phải byte. Constructor có tham số kích thước yêu cầu `size > 0`, nếu không sẽ ném `IllegalArgumentException`. Constructor một tham số chọn dung lượng mặc định do triển khai cung cấp; không nên xây dựng logic đúng/sai dựa vào một con số buffer mặc định cụ thể.

## 3. `BufferedReader`: đọc ký tự và đọc dòng

`BufferedReader` kế thừa `Reader`, do đó có thể dùng như một `Reader` thông thường và có thêm những tiện ích quan trọng:

- `int read()`: đọc một đơn vị `char` (giá trị số không âm), hoặc `-1` nếu EOF.
- `int read(char[] cbuf, int off, int len)`: đọc tối đa `len` ký tự vào mảng từ chỉ số `off`; trả số ký tự thực nhận, `-1` nếu hết nguồn, hoặc `0` khi `len == 0`.
- `String readLine()`: đọc một dòng; trả chuỗi **không chứa** ký tự kết thúc dòng, hoặc `null` khi đã hết dữ liệu và không còn ký tự nào tạo nên dòng tiếp theo.
- `boolean ready()`: cho biết một thao tác đọc kế tiếp có thể thực hiện mà không bị chặn trong điều kiện được báo; **không phải phép kiểm tra EOF**.
- `mark(int readAheadLimit)` và `reset()`: đánh dấu và quay về vị trí đọc trước đó trong giới hạn cho phép; không phải tua lại cả nguồn tùy ý.
- `Stream<String> lines()`: tạo stream dòng lazy; tài nguyên bên dưới vẫn cần quản lý, và lỗi I/O khi duyệt có thể xuất hiện dưới dạng `UncheckedIOException`.

### Hợp đồng của `readLine()`

`readLine()` coi **LF** (`\n`), **CR** (`\r`) hoặc **CRLF** (`\r\n`) là ranh giới dòng. Dấu kết thúc không nằm trong `String` được trả về.

| Nội dung nguồn | Các giá trị `readLine()` lần lượt trả |
| --- | --- |
| `"red\nblue"` | `"red"`, `"blue"`, sau đó `null` |
| `"red\r\nblue\n"` | `"red"`, `"blue"`, sau đó `null` |
| `"\n"` | `""` (dòng rỗng), sau đó `null` |
| `""` | `null` ngay lần đầu |
| `"red\n\nblue"` | `"red"`, `""`, `"blue"`, rồi `null` |

Đặc biệt:

- **`null`** biểu thị không còn dòng để đọc; **`""`** là một dòng thật nhưng không có ký tự.
- Dòng cuối **không cần** kết thúc bằng newline vẫn được trả về.
- Một newline ở cuối nguồn kết thúc dòng trước đó; không mặc nhiên tạo thêm một dòng rỗng khác sau nó.
- Không thể dùng `readLine()` để biết chính xác dòng trước đó kết thúc bằng LF, CR, CRLF hay không có terminator: thông tin này đã bị loại bỏ.

Vòng lặp chuẩn:

~~~java
String line;
while ((line = reader.readLine()) != null) {
    process(line);
}
~~~

Đây là mảnh mã với `reader` là một `BufferedReader` đã tạo và `process` là thao tác riêng của ứng dụng. Không viết `while (!line.isEmpty())`, vì dòng trống **không phải EOF**. Cũng không dùng `reader.ready()` làm điều kiện lặp đọc tất cả các dòng.

## 4. `BufferedWriter`: ghi ký tự, xuống dòng và đẩy bộ đệm

`BufferedWriter` kế thừa `Writer`, hỗ trợ các phương thức:

- `write(int c)`: ghi một đơn vị `char`, phần 16 bit thấp của `c`.
- `write(char[] cbuf, int off, int len)`: ghi đúng vùng ký tự được yêu cầu.
- `write(String s)` và `write(String s, int off, int len)`: ghi chuỗi hoặc đoạn chuỗi.
- `append(CharSequence)`, `append(CharSequence, start, end)` và `append(char)`: bổ sung ký tự theo hợp đồng `Appendable`.
- `newLine()`: ghi chuỗi phân cách dòng do môi trường xác định.
- `flush()`: yêu cầu đẩy các ký tự đang giữ trong buffer xuống `Writer` bên dưới, đồng thời flush đối tượng bên dưới.
- `close()`: flush dữ liệu còn lại rồi đóng writer bên dưới; gọi nhiều lần được phép theo hợp đồng `Writer`.

Ví dụ:

~~~java
writer.write("Xin chào");
writer.newLine();
writer.append("Java");
writer.flush();
~~~

Đây là mảnh mã với biến `writer` đã khai báo. Nếu muốn tạo file văn bản mà không chờ đến `close()` mới đưa dữ liệu xuống tầng tiếp theo, có thể gọi `flush()` tại các mốc cần quan sát dữ liệu. Tuy nhiên, **flush không đồng nghĩa dữ liệu đã được đồng bộ bền vững xuống phần cứng**; bộ đệm của hệ điều hành và thiết bị lưu trữ vẫn tồn tại.

### `newLine()` so với ký tự `'\n'`

`newLine()` dùng chuỗi phân cách dòng do môi trường chạy quy định, thường ứng với `System.lineSeparator()`. Nếu định dạng file hoặc giao thức quy định chính xác byte/ký tự `LF`, hãy ghi `'\n'` theo quy ước đó, thay vì phụ thuộc newline của hệ điều hành.

Ví dụ file chuẩn hóa LF:

~~~java
writer.write("header");
writer.write('\n'); // Chính xác LF trong chuỗi ký tự
writer.write("value");
writer.write('\n');
~~~

Ngược lại, với file text dành cho thao tác thông thường trên máy hiện tại, `newLine()` có thể phù hợp hơn.

## 5. Chương trình Java 17+ chạy được: đọc theo dòng, ghi có buffer

Ví dụ này dùng nguồn và đích trong bộ nhớ để bạn quan sát chính xác cách `readLine()` bỏ terminator, nhận dòng trống và cách `newLine()` tạo terminator mới.

Tạo **`BufferedTextDemo.java`**:

~~~java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class BufferedTextDemo {
    public static void main(String[] args) throws IOException {
        String input = "alpha\r\n\r\nbeta\n";
        StringWriter result = new StringWriter();
        int lines = 0;

        try (BufferedReader reader =
                     new BufferedReader(new StringReader(input), 4);
             BufferedWriter writer =
                     new BufferedWriter(result, 4)) {

            String line;
            while ((line = reader.readLine()) != null) {
                writer.write('[');
                writer.write(line);
                writer.write(']');
                writer.newLine();
                lines++;
            }
        }

        String visible = result.toString()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", "\\n");

        System.out.println("lines=" + lines);
        System.out.println("content=" + visible);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BufferedTextDemo.java
java BufferedTextDemo
~~~

**Stdout chính xác, không phụ thuộc newline của Windows/Linux/macOS vì phần in đã chuẩn hóa:**

~~~text
lines=3
content=[alpha]\n[]\n[beta]\n
~~~

Giải thích:

1. Nguồn có ba dòng: `alpha`, dòng trống và `beta`. Lần `readLine()` thứ tư trả `null`, nên vòng lặp kết thúc.
2. Buffer nội bộ dung lượng 4 **không giới hạn** chiều dài dòng. `readLine()` có thể ghép nhiều lần đọc để tạo chuỗi dài hơn buffer.
3. Mỗi dòng được ghi kèm dấu `[ ]` và gọi `newLine()`. `BufferedWriter` có thể giữ dữ liệu tạm thời trước khi đưa xuống `StringWriter`.
4. `try-with-resources` đóng `writer` và đẩy phần ký tự còn lại. Việc đọc `result.toString()` sau đó là hợp lệ với `StringWriter`: lớp này vẫn cho phép truy xuất buffer qua `toString()` sau `close()`.
5. `replace("\r\n", "\n").replace('\r', '\n')` chuẩn hóa line ending để trình bày trên console; nó chỉ dùng cho minh họa, không làm thay đổi nguồn ban đầu.

Chọn cỡ buffer 4 chỉ nhằm làm rõ buffering; không nên kết luận 4 là kích thước tối ưu. Khi xử lý thực tế, ưu tiên cỡ mặc định trừ khi đo hiệu năng và chứng minh cần thay đổi.

## 6. Chương trình sao chép file UTF-8 và giữ nguyên ký tự xuống dòng

Khi dùng cặp `readLine()` / `newLine()`, ranh giới dòng của nguồn bị viết lại theo định dạng mới. Nếu mục tiêu là **sao chép chính xác chuỗi ký tự sau giải mã**, bao gồm cả CR, LF và CRLF như chúng xuất hiện, hãy đọc `char[]` và ghi đúng số ký tự vừa đọc.

Tạo file **`BufferedFileCopy.java`**:

~~~java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BufferedFileCopy {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java BufferedFileCopy <source> <target>");
            return;
        }

        Path source = Path.of(args[0]);
        Path target = Path.of(args[1]);

        // Chỉ dùng khi source và target là hai file khác nhau.
        try (BufferedReader reader =
                     Files.newBufferedReader(source, StandardCharsets.UTF_8);
             BufferedWriter writer =
                     Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {

            char[] buffer = new char[4096];
            int count;

            while ((count = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, count);
            }
        }
    }
}
~~~

Biên dịch và chạy với file `source.txt` đang tồn tại:

~~~bash
javac BufferedFileCopy.java
java BufferedFileCopy source.txt copy.txt
~~~

Chương trình **không in stdout** khi thành công. File `copy.txt` chứa nội dung văn bản nguồn được đọc và ghi lại bằng UTF-8; thứ tự các đơn vị `char`, bao gồm CR và LF, được giữ nguyên. Vì đầu vào và đầu ra cùng định nghĩa UTF-8, đây là cách sao chép **văn bản**. Nó **không phải công cụ sao chép byte nhị phân** và không hứa kết quả luôn trùng mọi byte của nguồn (ví dụ trường hợp BOM hoặc dữ liệu bị xử lý bởi decoder/encoder theo chính sách charset).

### Chú ý khi áp dụng

- `Files.newBufferedReader(source, UTF_8)` trả về `BufferedReader`. `Files.newBufferedWriter(target, UTF_8)` trả về `BufferedWriter`.
- Khi không truyền `OpenOption`, `newBufferedWriter` mặc định tạo file nếu chưa có, hoặc ghi đè bằng cách **truncate** nội dung file đích đã tồn tại. Vì vậy, **phải bảo đảm hai đường dẫn trỏ tới hai file khác nhau**; nếu cùng file (kể cả alias/hard link), file nguồn có thể bị cắt nội dung. Đây là giới hạn an toàn của ví dụ.
- Với output cần **append** cuối file, dùng đúng open options như `StandardOpenOption.CREATE` và `StandardOpenOption.APPEND`; không tưởng rằng `BufferedWriter` tự quyết định append.
- `char[4096]` là buffer **của ứng dụng**, khác với buffer riêng nằm bên trong `BufferedReader` và `BufferedWriter`.
- Một lần `reader.read(buffer)` **không bảo đảm lấp đầy mảng**. Chỉ `buffer[0]` đến `buffer[count - 1]` thuộc lần đọc ấy; phương thức `writer.write(buffer, 0, count)` tránh ghi ký tự cũ từ lần trước.
- Với file nhị phân hoặc yêu cầu giữ nguyên mọi byte, dùng `Files.copy(...)` hoặc luồng byte. Khi cần biến đổi line ending theo chủ đích, có thể chọn `readLine()` và ghi terminator rõ ràng.
- `try-with-resources` bảo đảm đóng cả hai luồng khi khối kết thúc, kể cả khi có lỗi. Trong trường hợp lỗi ghi, file đích có thể đã chứa **một phần dữ liệu**; ghi vào file tạm và di chuyển sau khi thành công có thể phù hợp với quy trình yêu cầu tính toàn vẹn.

## 7. Luồng ký tự, byte và Unicode: nên chọn đúng lớp

`BufferedReader`/`BufferedWriter` làm việc với đơn vị Java `char`, tức **UTF-16 code unit**, không phải byte UTF-8 và không nhất thiết trùng số Unicode code point. Ví dụ ký tự emoji ngoài BMP chiếm **hai** đơn vị `char` nhưng chỉ là một code point.

- Khi đọc bằng `read(char[])`, một cặp surrogate có thể nằm ở hai lần đọc khác nhau. Nếu chỉ chuyển các đoạn ký tự đúng thứ tự sang `Writer` cùng một quá trình mã hóa, không nên tự giả định mỗi khối đã chứa code point hoàn chỉnh.
- Tránh tạo một `String` rồi xử lý Unicode theo code point từ từng khối bất kỳ nếu khối có thể cắt giữa surrogate pair.
- Việc giải mã UTF-8 thuộc `InputStreamReader` hay tầng đọc do `Files.newBufferedReader` tạo ra, **không** thuộc riêng cơ chế buffering của `BufferedReader`.
- Nếu dùng `new FileReader(path)` hoặc `new FileWriter(path)` theo overload dựa vào default charset, kết quả có thể phụ thuộc môi trường/JDK. Ưu tiên chỉ rõ `StandardCharsets.UTF_8` ở ranh giới byte–text để dữ liệu có ý nghĩa nhất quán.

Chẳng hạn với socket:

~~~java
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// Mảnh mã nằm trong phương thức có biến socket kiểu Socket:
BufferedReader reader = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
~~~

Đây là **mảnh mã**, không phải chương trình độc lập. `readLine()` chờ cho đến khi nhận được ký tự kết thúc dòng, EOF hoặc gặp lỗi; nếu peer tiếp tục giữ socket mở nhưng không gửi newline, lệnh có thể chặn. Khi thiết kế giao thức mạng cần định nghĩa framing, kích thước dòng tối đa và timeout phù hợp.

## 8. `flush()`, `close()` và sự khác nhau giữa “ghi” và “đã tới đích”

Hình dung:

~~~text
Ứng dụng gọi write("abc")
        |
        v
BufferedWriter [buffer chứa "abc"]
        |
        | đủ chỗ hoặc flush()/close()
        v
Writer bên dưới -> encoder/file/socket -> hệ điều hành/thiết bị
~~~

`BufferedWriter.write(...)` thường có thể hoàn thành sau khi chỉ ghi vào buffer nội bộ, còn `flush()` chuyển dữ liệu tồn đọng sang writer bên dưới. `close()` cũng đẩy dữ liệu cần thiết rồi đóng writer bên dưới. `flush()` không phải lời hứa về durable persistence xuống thiết bị hay xác nhận peer đã đọc các byte qua mạng.

Chương trình độc lập minh họa `flush` bằng đích trong bộ nhớ, **`FlushDemo.java`**:

~~~java
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;

public class FlushDemo {
    public static void main(String[] args) throws IOException {
        StringWriter target = new StringWriter();

        try (BufferedWriter writer = new BufferedWriter(target, 8)) {
            writer.write("abc");
            writer.flush();
            System.out.println("afterFlush=" + target);
        }

        System.out.println("afterClose=" + target);
    }
}
~~~

Chạy:

~~~bash
javac FlushDemo.java
java FlushDemo
~~~

Kết quả:

~~~text
afterFlush=abc
afterClose=abc
~~~

Kích thước buffer 8 lớn hơn chuỗi `abc`. Sau `flush()`, dữ liệu đã tới `StringWriter` nên quan sát được trước khi đóng. Trong ứng dụng ghi file, nên dùng `try-with-resources` và **tránh gọi `flush` sau mỗi ký tự/dòng** trừ khi thật sự cần đầu ra xuất hiện ngay, bởi flush liên tục làm giảm lợi ích gom dữ liệu.

Nếu truyền một `Writer` do người gọi sở hữu vào helper, thiết kế cần thống nhất **ai được đóng đích**. Không tùy ý đóng writer của người gọi chỉ vì helper tạm bọc nó bằng `BufferedWriter`. Đóng lớp bọc thông thường kéo theo đóng đích bên dưới.

## 9. `readLine()` so với đọc theo khối `char[]`

| Tiêu chí | `readLine()` | `read(char[])` |
| --- | --- | --- |
| Đơn vị ứng dụng nhận | Một dòng `String` | Số ký tự thực đọc vào mảng |
| Newline nguồn | Bị loại khỏi chuỗi | Có thể giữ nguyên CR/LF |
| Kết thúc | `null` khi EOF | `-1` khi EOF và yêu cầu đọc dương |
| Dòng trống | `""` | Đơn vị không liên quan đến dòng |
| Giới hạn bộ nhớ | Có thể phải tích lũy **cả dòng** trong RAM | Buffer do chương trình chọn có giới hạn |
| Bài toán phù hợp | File cấu hình, bản ghi văn bản có newline | Sao chép ký tự, xử lý streaming và bảo toàn separator |

Nếu file có các dòng dài bất thường do dữ liệu không tin cậy, **`readLine()` không cung cấp tham số độ dài tối đa**. Kiểm tra `line.length()` sau khi `readLine()` đã hoàn thành có thể là quá muộn để ngăn tiêu thụ bộ nhớ lớn. Với giao thức không tin cậy, cần bộ đọc theo từng ký tự/khối có **giới hạn tích lũy**, bộ phân tích cú pháp chuyên dụng hoặc cơ chế định khung khác.

Nếu cần giữ chính xác separator từng dòng, `readLine()` không đủ vì nó xóa thông tin `\r`/`\n`/`\r\n`. Đọc theo khối và xử lý separator theo trạng thái, hoặc dùng API hỗ trợ bảo toàn nếu cần.

## 10. `ready()`, `mark()`, `reset()` và `lines()`

### `ready()` không có nghĩa “còn dữ liệu”

~~~java
if (reader.ready()) {
    int ch = reader.read();
}
~~~

Đoạn mã này chỉ hỏi khả năng đọc **mà không chặn** ở thời điểm đó, không xác định còn bao nhiêu dòng/ký tự, cũng không xác định EOF. Nguồn socket chưa có dữ liệu tức thời nhưng chưa kết thúc có thể chưa ready; từ đó không được suy luận “đã xong”. Vòng lặp đọc file nên dựa vào giá trị `-1` hoặc `null` của thao tác đọc thực tế.

### `mark()/reset()`: xem trước rồi đọc lại trong giới hạn

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class MarkResetDemo {
    public static void main(String[] args) throws IOException {
        try (BufferedReader reader =
                     new BufferedReader(new StringReader("ABCDE"))) {
            reader.mark(3);
            System.out.println((char) reader.read());
            System.out.println((char) reader.read());
            reader.reset();
            System.out.println((char) reader.read());
        }
    }
}
~~~

Chạy:

~~~bash
javac MarkResetDemo.java
java MarkResetDemo
~~~

Stdout:

~~~text
A
B
A
~~~

`mark(3)` đặt giới hạn lưu ký tự cần giữ để `reset()`. Nếu đọc vượt xa `readAheadLimit`, việc reset không còn được bảo đảm; `reset()` có thể ném `IOException`. Không dùng `mark` làm cơ chế quay lại toàn bộ file có kích thước tùy ý.

### `lines()` là một stream lazy

~~~java
try (BufferedReader reader = java.nio.file.Files.newBufferedReader(
        java.nio.file.Path.of("source.txt"),
        java.nio.charset.StandardCharsets.UTF_8)) {
    reader.lines().filter(line -> !line.isBlank()).forEach(System.out::println);
}
~~~

Đây là đoạn mã chạy được **khi đặt trong phương thức có khai báo xử lý `IOException`**, ví dụ `main(String[] args) throws IOException`, và file `source.txt` tồn tại. `lines()` không háo hức đọc hết trước. Hãy duyệt stream **khi reader còn mở**; đóng stream không thay thế trách nhiệm đóng `BufferedReader`. Không xen kẽ việc gọi `readLine()` trên reader với duyệt `lines()` khi chưa kết thúc, vì cùng tiêu thụ một vị trí đọc. Các lỗi I/O xảy ra trong lúc duyệt có thể thành `UncheckedIOException`.

## 11. Các lỗi thường gặp

**Lỗi 1 — Xem dòng trống là hết dữ liệu.**

~~~java
// Sai: dừng sớm khi xuất hiện một dòng trống.
while (!(line = reader.readLine()).isEmpty()) {
    // ...
}
~~~

Đoạn trên còn có thể ném `NullPointerException` khi `readLine()` trả `null`. Cách đúng là so sánh với `null`, sau đó tùy nghiệp vụ quyết định xử lý hay bỏ qua dòng rỗng.

**Lỗi 2 — Quên `flush()` hoặc `close()`.**  
Nếu chỉ ghi vài ký tự vào buffer và chương trình ngừng dùng writer mà không đóng/flush, dữ liệu có thể vẫn nằm trong RAM và chưa đến writer bên dưới. Dùng `try-with-resources` cho tài nguyên tự sở hữu; gọi `flush()` khi thực sự cần đầu ra ngay.

**Lỗi 3 — Một lần `read(char[])` phải đầy mảng.**

~~~java
// Sai: count có thể nhỏ hơn buffer.length.
char[] buffer = new char[1024];
int count = reader.read(buffer);
if (count != -1) {
    writer.write(buffer); // Ghi cả ký tự chưa đọc/giá trị cũ.
}
~~~

Cách đúng là `writer.write(buffer, 0, count)`. Buffer là sức chứa; `count` là số ký tự hợp lệ.

**Lỗi 4 — Tưởng `readLine()/newLine()` giữ nguyên nội dung nguồn.**  
Đọc theo dòng xóa dấu kết thúc dòng, ghi `newLine()` thêm separator do môi trường chọn. Chỉ dùng khi **cho phép chuẩn hóa** line endings và chính sách thêm newline; để giữ chuỗi ký tự nguyên trạng, dùng `read(char[])`/`write(char[],off,len)`.

**Lỗi 5 — Chọn sai mức abstraction.**  
Không dùng bộ đệm ký tự để sao chép ZIP, ảnh, dữ liệu nén hoặc payload nhị phân. Dùng byte streams hoặc `Files.copy` cho byte. Cũng đừng tạo `BufferedReader` để hỏi tổng độ dài file: không có hợp đồng này.

**Lỗi 6 — Giả định buffering chữa mọi vấn đề hiệu năng.**  
Buffer giúp giảm nhiều lời gọi nhỏ xuống tầng bên dưới, nhưng không loại bỏ latency mạng, parsing tốn CPU hay chi phí mã hóa charset. Buffer quá lớn tốn bộ nhớ; buffer quá nhỏ có thể làm tăng số lần tương tác. Lựa chọn theo số liệu đo và khối lượng công việc.

**Lỗi 7 — Dùng file nguồn và đích giống nhau khi sao chép.**  
Nếu đích bị truncate khi mở, có thể phá dữ liệu trước khi đọc xong. Kiểm tra nguồn và đích khác nhau; nếu ghi đè an toàn là yêu cầu quan trọng, dùng file tạm và bước thay thế phù hợp.

**Lỗi 8 — Để giao thức mạng không có giới hạn dòng.**  
`readLine()` có thể chặn khi chưa có newline/EOF và có thể tiêu thụ rất nhiều RAM trên một dòng dài. Với dữ liệu bên ngoài, hãy quy định framing, độ dài tối đa, timeout, charset và chính sách lỗi.

## 12. Checklist thực hành

- [ ] Dữ liệu là **text** và charset đã được quy định rõ, ví dụ `UTF-8`?
- [ ] Đang dùng `BufferedReader`/`BufferedWriter` trên đúng tầng `Reader`/`Writer`?
- [ ] Vòng lặp `readLine()` kiểm tra `null`, không nhầm với `""`?
- [ ] Nếu đọc mảng `char[]`, chỉ ghi **`count`** ký tự vừa nhận?
- [ ] Có chủ đích giữ nguyên hoặc chuẩn hóa CR/LF/CRLF?
- [ ] Đã tính đến trường hợp dòng rất dài và luồng có thể chặn?
- [ ] `flush()` và `close()` phù hợp quyền sở hữu tài nguyên, có `try-with-resources` khi cần?
- [ ] Không dùng `ready()` để kết luận EOF và không áp đặt kích thước buffer như số ký tự chắc chắn đọc được?
- [ ] Khi ghi file, đã xét hành vi tạo/truncate/append và tránh tự ghi đè nguồn?

### Câu hỏi tự kiểm tra

**1.** `readLine()` trả `""`. Nguồn đã hết chưa?  
**Đáp án:** Chưa thể kết luận. Đó là một dòng rỗng hợp lệ; chỉ `null` cho biết không còn dòng.

**2.** Buffer nội bộ có 8192 ký tự nhưng một dòng dài 100.000 ký tự. `readLine()` sẽ trả gì?  
**Đáp án:** Có thể trả chuỗi toàn bộ 100.000 ký tự (nếu dòng hợp lệ/đủ dữ liệu và bộ nhớ), bởi nó ghép qua nhiều lượt đọc; buffer không đặt giới hạn dòng.

**3.** Vì sao `readLine()` rồi `newLine()` không thể bảo toàn file một cách chính xác theo ký tự?  
**Đáp án:** `readLine()` bỏ dấu CR/LF/CRLF và `newLine()` ghi dấu mới phụ thuộc môi trường, đồng thời có thể thêm terminator cho dòng cuối.

**4.** Khi nào nên gọi `flush()`?  
**Đáp án:** Khi dữ liệu cần chuyển xuống writer bên dưới trước lúc đóng, ví dụ một phản hồi phải gửi ngay. Khi dùng `try-with-resources` với file được đóng sau khi ghi xong, `close()` đã bao gồm việc đẩy buffer.

**5.** Buffer là ký tự hay byte?  
**Đáp án:** Bộ đệm của hai lớp này chứa **ký tự**; khi kết hợp với file byte, lớp mã hóa/giải mã phía dưới mới chuyển đổi giữa UTF-16 `char` của Java và byte theo charset.

**Ghi nhớ:** `BufferedReader` giúp đọc văn bản và từng dòng thuận tiện; `BufferedWriter` gom thao tác ghi và hỗ trợ xuống dòng. **Buffer chỉ cải thiện cách vận chuyển ký tự**; ứng dụng vẫn phải xử lý EOF, độ dài đọc thực, charset, ranh giới dòng, flush và quyền sở hữu tài nguyên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buffering-and-stream-composition-003">Decorator: lớp bọc bổ sung hành vi cho stream</a>

<details>
<summary>Xem chi tiết</summary>

# Decorator: lớp bọc bổ sung hành vi cho stream trong Java I/O

Một `InputStream` có thể đọc byte từ tệp, nhưng nếu muốn **đọc có bộ đệm**, **đọc kiểu dữ liệu nguyên thủy** hoặc **đếm số byte đã đọc** thì sao? Java I/O giải quyết nhiều nhu cầu như vậy bằng cách **bọc một stream bên trong stream khác**. Lớp bên ngoài giữ cùng giao diện đọc/ghi cơ bản, nhưng bổ sung hành vi trước hoặc sau khi chuyển công việc xuống stream bên trong. Đây là cách áp dụng **Decorator pattern** trong thực tế.

Ví dụ ghép luồng:

~~~text
CHƯƠNG TRÌNH
      |
      v
DataInputStream       <-- bổ sung readInt(), readLong(), readFully()...
      |
      v
BufferedInputStream   <-- thêm bộ đệm cho việc đọc byte
      |
      v
FileInputStream       <-- thực sự đọc byte từ tệp
      |
      v
TỆP NGUỒN
~~~

Ở chiều ghi, cùng tư tưởng hoạt động từ lớp ngoài vào lớp trong: `DataOutputStream` viết giá trị theo biểu diễn nhị phân, `BufferedOutputStream` gom các byte, rồi `FileOutputStream` đưa byte xuống tệp đích.

Sau bài học, bạn cần giải thích được **vai trò từng lớp**, **thứ tự ghép**, **cách lời gọi được chuyển tiếp**, **ai đóng tài nguyên**, và **vì sao bọc stream không tự thay đổi dữ liệu theo kiểu bạn mong muốn nếu chọn sai thứ tự**.

## 1. Decorator pattern trong ngữ cảnh luồng là gì?

Decorator là một mẫu thiết kế cấu trúc: tạo một đối tượng **bọc** một đối tượng có cùng giao diện, sau đó cho đối tượng bọc cung cấp hành vi bổ sung mà không phải sửa trực tiếp lớp nguồn. Trong Java I/O:

- **Component:** kiểu chung, chẳng hạn `InputStream` (đọc byte) hoặc `OutputStream` (ghi byte).
- **Concrete component:** stream nền cung cấp dữ liệu, ví dụ `FileInputStream`, `ByteArrayInputStream`, `FileOutputStream`.
- **Decorator:** stream bọc, như `BufferedInputStream`, `DataInputStream`, `BufferedOutputStream`, `DataOutputStream`.
- **Client:** code chỉ gọi đối tượng ngoài cùng, không cần tự điều phối từng tầng.

Hình dung:

~~~text
Client -> Decorator C -> Decorator B -> Decorator A -> Stream gốc
          [cùng hợp đồng InputStream hoặc OutputStream]
~~~

Decorator **giữ tham chiếu đến một stream khác** và thường ủy quyền các thao tác cần thiết cho nó, có thể thay đổi/đệm/chuyển đổi/đo lường dữ liệu. Về mặt kiến trúc, người dùng muốn thay đổi hành vi bằng **composition (ghép đối tượng)** thay vì sinh nhiều lớp con kết hợp mọi đặc tính.

Ví dụ tạo chuỗi đọc:

~~~java
InputStream input = new DataInputStream(
        new BufferedInputStream(
                new FileInputStream("payload.bin")));
~~~

Các lớp tham gia đều tương thích với kiểu `InputStream`, nên có thể đưa `input` tới các phương thức nhận `InputStream`. Tuy nhiên, phương thức đặc thù `readInt()` chỉ hiện ra nếu biến được khai báo theo kiểu `DataInputStream` hoặc có tham chiếu tới decorator đó:

~~~java
try (DataInputStream input = new DataInputStream(
        new BufferedInputStream(new FileInputStream("payload.bin")))) {
    int version = input.readInt();
}
~~~

Đây là **đoạn minh họa** (không chạy độc lập nếu chưa có `payload.bin` và import). Tệp phải thực sự chứa ít nhất bốn byte theo định dạng mà `readInt` mong đợi; `DataInputStream` không tự nhận biết định dạng file.

## 2. Những lớp decorator byte stream phổ biến

| Lớp | Bọc gì? | Hành vi bổ sung |
|---|---|---|
| `FilterInputStream` | `InputStream` | Lớp cơ sở cho các bộ lọc/luồng bọc input; mặc định phần lớn thao tác chuyển xuống stream bên trong. |
| `BufferedInputStream` | `InputStream` | Giữ một vùng byte đệm để giảm số lần phải đọc nguồn bên dưới; hỗ trợ `mark`/`reset` theo hợp đồng của lớp. |
| `DataInputStream` | `InputStream` | Đọc giá trị nhị phân có kiểu bằng `readInt()`, `readLong()`, `readDouble()`, `readFully()`, v.v. |
| `PushbackInputStream` | `InputStream` | Cho phép đẩy lại một hoặc nhiều byte qua `unread()` để lần đọc sau nhận lại chúng. |
| `FilterOutputStream` | `OutputStream` | Lớp cơ sở cho các bộ lọc/luồng bọc output. |
| `BufferedOutputStream` | `OutputStream` | Gom byte vào bộ đệm trước khi đưa xuống output bên dưới. |
| `DataOutputStream` | `OutputStream` | Ghi giá trị có kiểu như `writeInt()`, `writeLong()`, `writeDouble()` theo định dạng nhị phân của Java. |

Ngoài ra, các lớp nén như `GZIPInputStream` và `GZIPOutputStream` thuộc hệ thống Java I/O/ZIP cung cấp hành vi **giải nén/nén** khi bọc stream. Chúng hữu ích để thấy rằng decorator không chỉ làm bộ đệm: nó có thể thực hiện cả biến đổi dữ liệu.

Nhìn theo quan hệ kế thừa phổ biến:

~~~text
InputStream
  +-- FilterInputStream
        +-- BufferedInputStream
        +-- DataInputStream
        +-- PushbackInputStream

OutputStream
  +-- FilterOutputStream
        +-- BufferedOutputStream
        +-- DataOutputStream
~~~

Không phải **mọi** lớp đọc/ghi đều nhất thiết kế thừa `FilterInputStream`/`FilterOutputStream`. Điều quan trọng của decorator ở đây là **cấu trúc bao một đối tượng và bổ sung hành vi qua hợp đồng chung**, không chỉ tên lớp hoặc hệ kế thừa.

## 3. Cơ chế của lớp bọc: lời gọi chạy qua các tầng

Giả sử nguồn là file chứa byte và chương trình gọi:

~~~java
int value = input.read();
~~~

Nếu `input` là một `BufferedInputStream` bọc `FileInputStream`, lời gọi đi vào lớp buffer trước. Khi buffer còn byte, nó có thể trả ngay byte đã được giữ. Khi cần nạp thêm, nó đọc một khối byte từ `FileInputStream`, rồi trả một byte cho caller. Điều này **giảm số lần gọi xuống tầng I/O nền**, nhưng không nhất thiết làm số byte đầu ra khác đi.

Với `DataInputStream` ở lớp ngoài cùng:

~~~java
int version = dataInput.readInt();
~~~

`readInt()` lấy **bốn byte** từ tầng input bên dưới và ghép thành một giá trị `int` theo định dạng xác định của `DataInput` (big-endian cho số nguyên nhiều byte). `BufferedInputStream` bên trong có thể lấy trước thêm byte vào buffer, nhưng giá trị logic mà `readInt` trả về vẫn đến từ đúng bốn byte của trường dữ liệu này.

**Đừng nhầm số byte đã đọc từ nguồn vật lý với số byte client đã tiêu thụ.** Do nạp trước, `BufferedInputStream` có thể đọc nhiều byte từ file bên dưới dù caller mới yêu cầu một byte hoặc một giá trị. Nếu bạn đặt decorator để thống kê số byte **dưới** buffer, số đếm có thể lớn hơn lượng dữ liệu client đã sử dụng tại thời điểm đo. Nếu đặt thống kê **trên** buffer, số đếm phản ánh các byte đã đi qua lớp thống kê. Thứ tự bọc vì thế ảnh hưởng cả quan sát.

### Thứ tự bọc là một phần của thiết kế

Ví dụ hai cách đặt lớp đếm:

~~~text
A) Client -> CountingInputStream -> BufferedInputStream -> FileInputStream
    đếm byte đã được client lấy qua lớp Counting

B) Client -> BufferedInputStream -> CountingInputStream -> FileInputStream
    đếm byte Buffered đã lấy từ stream nền, kể cả byte đọc trước
~~~

Nếu mỗi tầng chỉ có hành vi chuyển tiếp, sự khác biệt khó thấy; nếu một tầng **buffer, nén, mã hóa, giải mã, kiểm đếm**, thứ tự có thể thay đổi kết quả hoặc số liệu. Thiết kế pipeline từ **định dạng byte thực trên nguồn/đích** rồi đặt tầng xử lý thích hợp.

## 4. Ví dụ Java 17 chạy được: ghép nhiều decorator

Ví dụ này cố tình không cần file bên ngoài. Ta tạo một định dạng nhị phân nhỏ:

~~~text
4 byte: version (số int)
4 byte: số byte của nội dung
N byte: văn bản mã hóa theo UTF-8
~~~

**Luồng ghi**: `DataOutputStream -> BufferedOutputStream -> ByteArrayOutputStream`.  
**Luồng đọc**: `DataInputStream -> BufferedInputStream -> CountingInputStream -> ByteArrayInputStream`.

`CountingInputStream` là decorator tự viết, kế thừa `FilterInputStream` để đếm byte đi qua nó. Tệp mã nguồn **`StreamDecoratorDemo.java`**:

~~~java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class StreamDecoratorDemo {
    static final class CountingInputStream extends FilterInputStream {
        private long count;

        CountingInputStream(InputStream wrapped) {
            super(wrapped);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) count++;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int number = super.read(bytes, offset, length);
            if (number > 0) count += number;
            return number;
        }

        long getCount() {
            return count;
        }
    }

    public static void main(String[] args) throws IOException {
        byte[] message = "Xin chào".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream storage = new ByteArrayOutputStream();

        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(storage))) {
            output.writeInt(3);
            output.writeInt(message.length);
            output.write(message);
        }

        byte[] wire = storage.toByteArray();
        CountingInputStream counted = new CountingInputStream(
                new ByteArrayInputStream(wire));
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(counted))) {
            int version = input.readInt();
            int length = input.readInt();
            byte[] body = new byte[length];
            input.readFully(body);

            System.out.println("Version: " + version);
            System.out.println("Text: " + new String(body, StandardCharsets.UTF_8));
            System.out.println("Body length: " + length);
        }

        System.out.println("Encoded bytes: " + wire.length);
        System.out.println("Read from source: " + counted.getCount());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 StreamDecoratorDemo.java
java StreamDecoratorDemo
~~~

Kết quả kỳ vọng khi terminal hỗ trợ UTF-8:

~~~text
Version: 3
Text: Xin chào
Body length: 9
Encoded bytes: 17
Read from source: 17
~~~

Nếu console của môi trường cấu hình encoding khác UTF-8, ký tự có dấu có thể hiển thị sai dù phép chuyển đổi byte và giá trị được đọc đúng. Phần còn lại của output vẫn giúp xác minh số byte và quá trình bọc.

**Phân tích từng tầng:**

1. `ByteArrayOutputStream storage` là **đích byte cuối** trong bộ nhớ. `BufferedOutputStream` làm bộ đệm, còn `DataOutputStream` cung cấp `writeInt` để ghi chính xác định dạng của hai số nguyên.
2. `message` có **9 byte UTF-8**: `Xin` có ba byte, dấu cách một byte, `chào` có năm byte vì ký tự `à` cần hai byte. `writeInt(3)` ghi bốn byte; `writeInt(9)` ghi bốn byte; `write(message)` ghi chín byte, nên kết quả là **4 + 4 + 9 = 17 byte**.
3. Đóng `DataOutputStream` ở cuối `try-with-resources` làm các byte còn trong `BufferedOutputStream` được đưa xuống tầng dưới. Sau đó `storage.toByteArray()` lấy byte đã tích lũy; việc đóng `ByteArrayOutputStream` không làm mất nội dung trong bộ nhớ.
4. `ByteArrayInputStream` cung cấp 17 byte để đọc ngược. `CountingInputStream` ghi nhận byte nguồn thực sự cấp; `BufferedInputStream` bọc nó để đệm; `DataInputStream` ở ngoài giải mã hai trường `int`.
5. `readFully(body)` đọc đủ `length` byte hoặc ném `EOFException` nếu không đủ. Nó phù hợp với định dạng có độ dài xác định; không giống một lần `read(body)` có thể trả ít byte hơn yêu cầu.
6. Trong trường hợp cụ thể này, toàn bộ 17 byte đều được tiêu thụ nên số byte đếm là 17. Nếu file có dữ liệu dư, bộ đệm có thể đọc trước nhiều hơn mức `DataInputStream` đã dùng.
7. `try-with-resources` đóng **lớp ngoài cùng**. Các lớp bọc tiêu chuẩn trong chuỗi ví dụ chuyển việc đóng xuống stream bên trong theo hợp đồng của chúng.

Ví dụ minh họa **cơ chế ghép stream và một định dạng nhị phân tự định nghĩa**; nó không phải một định dạng file tiêu chuẩn. Khi nhận dữ liệu từ nguồn không tin cậy, phải kiểm tra `length` (âm, quá lớn, vượt giới hạn cho phép) **trước khi cấp phát `new byte[length]`**. Không bao giờ cấp phát mảng tùy ý chỉ vì một số nguyên trong dữ liệu đầu vào khai báo kích thước như vậy.

### Vì sao decorator tự viết ghi đè hai overload `read`?

Một decorator thống kê cần quyết định **đo byte nào** và bao phủ các cách đọc mà code sử dụng. `read()` trả một byte hoặc `-1` khi hết luồng; `read(byte[], offset, length)` trả số byte đã đọc. Lớp trên tăng bộ đếm chỉ khi có dữ liệu thực sự. Nó kế thừa phần xử lý `read(byte[])` sao cho lời gọi đi qua overload có ba tham số.

Không nên lấy số lần **gọi phương thức** làm số byte đọc. Một lời gọi `read(byte[], off, len)` có thể nhận từ 1 đến `len` byte (hoặc giá trị đặc biệt theo hợp đồng của nó). Mọi decorator tự viết cũng cần xem xét các phương thức khác như `skip()`, `mark()` và `reset()` nếu muốn bộ đếm phản ánh một định nghĩa khác: ví dụ “tổng byte lấy từ nguồn”, “byte đến client” hay “vị trí hiện tại”. Bộ đếm đơn giản ở đây **không đếm `skip` và không đảo số đếm khi `reset`**; vì chương trình không gọi các phương thức ấy nên output ví dụ không bị ảnh hưởng.

## 5. Đóng stream bọc và quyền sở hữu tài nguyên

Với các decorator Java I/O thường gặp, khi đóng **decorator ngoài cùng**, lệnh `close()` được chuyển tới stream nằm bên trong, có thể tiếp tục xuống stream gốc:

~~~text
try (DataOutputStream out =
         new DataOutputStream(
             new BufferedOutputStream(
                 Files.newOutputStream(path)))) {
    // Ghi dữ liệu
}

Ra khỏi try:
DataOutputStream.close()
     -> BufferedOutputStream.close()
           -> OutputStream từ Files.newOutputStream(...).close()
~~~

Do đó, nếu chính phương thức tạo và sở hữu chuỗi decorator, thông thường chỉ cần khai báo **đối tượng ngoài cùng** trong `try-with-resources`. Không nhất thiết liệt kê mọi tầng như những tài nguyên riêng rồi tự đóng lặp lại. Tuy nhiên, phải tra cứu hợp đồng `close` của wrapper cụ thể; một thư viện có thể thiết kế wrapper không đóng stream bên dưới.

**Phân biệt rõ:**

- **Lớp ngoài cùng sở hữu chuỗi:** phương thức tạo stream gốc và decorator, tự chịu trách nhiệm đóng nó; `try-with-resources` ở tầng ngoài cùng là thuận tiện.
- **Lớp bọc mượn stream của caller:** nếu code đặt wrapper vào `try-with-resources`, việc đóng wrapper có thể **đóng luôn stream của caller**. Chỉ làm vậy khi hợp đồng cho phép chuyển quyền sở hữu hoặc caller không dùng stream nữa.
- **Luồng chung (`System.in`/`System.out`) hoặc framework quản lý:** không đóng bừa chỉ vì tiện viết `try-with-resources`. Cần quy định rõ ai kết thúc stream.
- **Stream output có buffer:** dữ liệu có thể chưa đi hết đến đích nếu chưa `flush()` hoặc `close()` đúng lúc. `flush` yêu cầu đẩy dữ liệu có đệm xuống tầng dưới, nhưng không tự bảo đảm dữ liệu đã bền vững trên đĩa hay ứng dụng đầu nhận đã xử lý.

Ví dụ helper nhận output từ caller và chỉ mượn:

~~~java
static void writeRecord(DataOutputStream output, int value)
        throws IOException {
    output.writeInt(value);
    // Không close: caller còn có thể ghi record tiếp.
}
~~~

Đây là **phương thức minh họa** được đặt trong một lớp Java có import `DataOutputStream` và `IOException`. Đối với luồng có dữ liệu đệm, caller chủ động quyết định khi nào flush/close dựa theo vòng đời và giao thức.

## 6. Các lớp bọc phải tương thích với loại dữ liệu

### Byte stream với byte stream

`BufferedInputStream`, `DataInputStream`, `PushbackInputStream` bọc một `InputStream` và vẫn thuộc phía **byte**; `BufferedOutputStream`, `DataOutputStream` bọc `OutputStream` và vẫn thuộc phía **byte**.

### Cầu nối byte và ký tự là adapter

`InputStreamReader` nhận `InputStream` và giải mã byte thành **ký tự**; `OutputStreamWriter` nhận `OutputStream` và mã hóa ký tự thành byte. Về vai trò thiết kế, chúng là **adapter giữa byte stream và character stream**, không phải decorator cùng kiểu `InputStream` ↔ `InputStream`.

~~~java
Reader reader = new BufferedReader(
        new InputStreamReader(input, StandardCharsets.UTF_8));
~~~

Đoạn minh họa cần các import tương ứng. `input` là `InputStream` đã có; `InputStreamReader` chuyển byte thành ký tự UTF-8, và `BufferedReader` đệm ký tự/bổ sung `readLine()`. `BufferedReader` không phải `BufferedInputStream`: một bên đệm **ký tự**, bên kia đệm **byte**. Nếu cần bảo toàn dữ liệu nhị phân tuyệt đối, tránh đi qua decoder/encoder ký tự.

### Dữ liệu có kiểu phải đọc đúng cách đã ghi

`DataOutputStream.writeInt(123)` ghi **4 byte nhị phân**; phía đọc tương ứng là `DataInputStream.readInt()`. Không nên mở file vừa ghi bằng `writeInt` như một tệp UTF-8 rồi chờ thấy các chữ số `"123"`.

Một điểm dễ nhầm: `DataOutputStream.writeUTF(String)` dùng **modified UTF-8** với tiền tố độ dài, **không phải chính xác cùng byte với `string.getBytes(StandardCharsets.UTF_8)` trong mọi trường hợp**. Muốn định dạng một chuỗi **UTF-8 chuẩn** có độ dài rõ ràng, có thể dùng `writeInt(bytes.length)` rồi `write(bytes)` như ví dụ và đọc tương ứng `readInt()` + `readFully()`. Hai phía phải đồng ý về định dạng và thứ tự trường.

## 7. Lớp nén cũng cho thấy vì sao thứ tự ghép quan trọng

Khi lưu dữ liệu nhị phân được mã hóa bởi `DataOutputStream` rồi nén GZIP, một thứ tự hợp lý là:

~~~java
// Minh họa; path là Path đã khai báo.
try (DataOutputStream out = new DataOutputStream(
        new GZIPOutputStream(
                new BufferedOutputStream(
                        Files.newOutputStream(path))))) {
    out.writeInt(123);
}
~~~

Dữ liệu đi theo thứ tự: `DataOutputStream` tạo byte biểu diễn số nguyên → `GZIPOutputStream` **nén** các byte → `BufferedOutputStream` đệm byte đã nén trước khi ghi xuống tệp. Để đọc lại, dựng luồng có trật tự đối ứng:

~~~java
try (DataInputStream in = new DataInputStream(
        new GZIPInputStream(
                new BufferedInputStream(
                        Files.newInputStream(path))))) {
    int value = in.readInt();
}
~~~

Đây là **các đoạn minh họa** cần import `java.io.*`, `java.nio.file.*`, `java.util.zip.GZIPInputStream` và `java.util.zip.GZIPOutputStream`; `path` được cung cấp bởi ứng dụng. Chúng không tạo thành một chương trình `main` riêng.

Nếu đặt tầng giải nén sai phía, chẳng hạn cố `readInt()` trực tiếp từ byte GZIP chưa giải nén, sẽ không thu được số `123` đã ghi. Nếu thay đổi thứ tự các bộ lọc **có biến đổi dữ liệu**, cần thiết kế phía đọc tương ứng. Chỉ gắn thêm wrapper mà không hiểu giao thức là không đủ.

## 8. Decorator và các lựa chọn thay thế

Decorator phù hợp khi cần **tái sử dụng cùng nguồn/đích và thêm một hành vi theo từng tầng**. Tuy nhiên, không phải thao tác I/O nào cũng bắt buộc phải tự dựng chuỗi lớp bọc:

- `Files.readString(path, UTF_8)` hoặc `Files.writeString(path, text, UTF_8)` ngắn gọn khi xử lý một tệp văn bản nhỏ và biết charset.
- `Files.copy(source, destination)` phù hợp nếu chỉ cần sao chép tệp theo đường dẫn và không cần đụng đến dữ liệu ở giữa.
- `InputStream.transferTo(output)` sao chép byte còn lại từ input sang output; có thể sử dụng ngay cả khi input/output đã bọc nhiều tầng.
- `BufferedReader` hữu ích nếu cần đọc từng dòng văn bản; đây là tầng ký tự, nên dùng với charset giải mã phù hợp.
- Đối với bài toán cần nhiều hiệu năng hoặc thao tác ngẫu nhiên phức tạp, `java.nio.channels` có các lựa chọn kênh/buffer chuyên biệt; không nên mặc định decorator giải quyết mọi nhu cầu.

Đánh đổi của ghép nhiều tầng là **thêm cấu hình, thêm trạng thái nội bộ và trách nhiệm đóng tài nguyên**. Dùng đúng những tầng đáp ứng yêu cầu và kiểm tra API có sẵn, thay vì luôn thêm `BufferedInputStream` dù chưa đo lường hoặc API đã có cơ chế đọc theo khối thích hợp.

## 9. Lỗi thường gặp

| Nhầm lẫn | Hậu quả | Cách làm đúng |
|---|---|---|
| “Gắn thêm `BufferedInputStream` là tệp đổi nội dung.” | Buffer chỉ thay chiến lược lấy byte, không đổi bản chất định dạng. | Tách bạch **buffering** và **transformation**. |
| “`DataInputStream` sẽ tự hiểu file text/JSON.” | API này đọc định dạng byte của các phương thức `DataInput`, không đoán cấu trúc. | Phải biết schema binary và thứ tự trường đã ghi. |
| “Không cần quan tâm thứ tự decorator.” | Tầng nén, mã hóa, buffer, đếm có thể làm số liệu/dữ liệu khác. | Thiết kế đường đi từ producer đến consumer và ghép thứ tự đối ứng. |
| “Đóng wrapper ngoài không ảnh hưởng stream gốc.” | Nhiều wrapper gọi `close` xuống lớp dưới, vô tình đóng stream người khác sở hữu. | Thiết kế ownership, dùng `try-with-resources` ở bên sở hữu. |
| “`flush` và `close` là một.” | `flush` đẩy buffer, còn `close` kết thúc tài nguyên; không thể coi đồng nghĩa. | Flush/close theo hợp đồng khi hoàn tất bản ghi/luồng. |
| “Đọc `read(byte[])` một lần luôn đủ.” | Một lần đọc có thể trả ít hơn số byte yêu cầu. | Dùng vòng lặp hoặc `DataInputStream.readFully` khi định dạng đã có độ dài. |
| “`CountingInputStream` trong ví dụ luôn đếm byte mà client đã xử lý.” | Ở dưới buffer, lớp đếm còn thấy byte được đọc trước. | Đặt lớp đo ở tầng tương ứng với đại lượng cần đo. |
| “Đọc text bằng byte stream không cần charset.” | Khi chuyển byte thành ký tự, charset vẫn quyết định nội dung. | Dùng `InputStreamReader` với charset đúng nếu cần text. |
| “`new byte[length]` an toàn với mọi file.” | Độ dài trong input không tin cậy có thể sai/âm/rất lớn. | Ràng buộc độ dài trước khi cấp phát, xử lý EOF và lỗi định dạng. |

## 10. Bài tập tự kiểm tra

**Bài 1.** Chương trình đọc từng dòng UTF-8 từ file. Hãy mô tả các tầng hợp lý.

> **Đáp án:** `FileInputStream` (byte nguồn) → `InputStreamReader(..., UTF_8)` (giải mã byte thành ký tự) → `BufferedReader` (đệm ký tự và cung cấp `readLine()`). Hoặc dùng `Files.newBufferedReader(path, UTF_8)` để tạo trực tiếp reader phù hợp.

**Bài 2.** Vì sao có thể giữ biến ngoài cùng kiểu `InputStream` nhưng không gọi trực tiếp `readInt()`?

> **Đáp án:** `InputStream` là hợp đồng đọc byte chung; `readInt()` là phương thức bổ sung của `DataInputStream`. Muốn gọi trực tiếp phải có tham chiếu kiểu `DataInputStream` (hoặc giao diện hỗ trợ tương ứng), dù đối tượng runtime đang được bọc.

**Bài 3.** Khi code tự mở `FileInputStream` và bọc `BufferedInputStream`, nên đóng ở đâu?

> **Đáp án:** Dùng `try-with-resources` cho đối tượng bọc ngoài cùng do phương thức sở hữu. Theo hợp đồng của các lớp tiêu chuẩn này, đóng wrapper sẽ đóng stream nền. Đừng tự động dùng cách đó nếu stream nền do caller/framework sở hữu.

**Bài 4.** Đặt một bộ đếm phía dưới `BufferedInputStream`. Caller mới gọi `read()` một lần nhưng số đếm nhảy nhiều byte. Đó có chắc là lỗi không?

> **Đáp án:** Không. `BufferedInputStream` có thể nạp sẵn một khối byte. Bộ đếm phía dưới đo byte **đã lấy từ nguồn**, không nhất thiết là số byte caller đã tiêu thụ.

**Bài 5.** Ghi `writeInt(123)` qua `GZIPOutputStream`. Đọc `readInt()` trực tiếp từ tệp GZIP có đúng không?

> **Đáp án:** Không. Phải đặt `GZIPInputStream` ở phía nguồn để **giải nén** byte trước, rồi `DataInputStream` mới giải nghĩa chúng bằng `readInt()`.

## Ghi nhớ

Decorator stream là cách **bổ sung hành vi qua các tầng bọc có cùng giao diện I/O**. Khi ghép, hãy tự hỏi: **nguồn/đích thực là gì; lớp nào thêm buffer, xử lý định dạng hoặc biến đổi byte; thứ tự tầng có đúng với giao thức không; ai sở hữu và đóng chuỗi này?** Trả lời được các câu hỏi đó giúp thiết kế pipeline Java I/O vừa dễ mở rộng vừa không làm sai dữ liệu hoặc vòng đời tài nguyên.

**Tham khảo:** [Java SE 17 — `FilterInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FilterInputStream.html), [`BufferedInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedInputStream.html), [`DataInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html), [`DataOutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutputStream.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buffering-and-stream-composition-004">Thứ tự ghép stream, buffer, charset và định dạng</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự ghép stream, buffer, charset và định dạng trong Java I/O

## 1. Tư duy theo luồng dữ liệu: byte → ký tự → cấu trúc

Trong Java I/O, nhiều lớp có thể bọc (**wrap**) cùng một nguồn/đích để mỗi lớp đảm nhận một chức năng. Với dữ liệu văn bản, bốn mối quan tâm cần phân biệt:

1. **Stream byte:** kết nối nguồn/đích vật lý hoặc logic, chẳng hạn `FileInputStream`, `FileOutputStream`, `Files.newInputStream`, `ByteArrayInputStream`.
2. **Buffer:** giảm số lần gọi tầng bên dưới bằng cách đọc/ghi theo khối, chẳng hạn `BufferedInputStream`, `BufferedOutputStream`, `BufferedReader`, `BufferedWriter`.
3. **Charset:** chuyển giữa **byte** và **ký tự**, chẳng hạn `InputStreamReader` và `OutputStreamWriter` với `StandardCharsets.UTF_8`.
4. **Định dạng (format):** quy ước biến các giá trị và bản ghi thành văn bản hoặc dữ liệu nhị phân: một dòng ứng với một bản ghi, CSV, JSON, hoặc `DataOutputStream` với một giao thức nhị phân cụ thể.

**Quy tắc chính:** Ghép lớp theo **kiểu dữ liệu mà mỗi lớp nhận và trả về**, rồi kiểm tra quy ước định dạng của tệp/giao thức. Không hoán đổi tùy ý các lớp chỉ vì chúng đều có chữ “stream” hoặc “buffer” trong tên.

Sơ đồ đọc văn bản đơn giản:

```text
Nguồn byte (file/network)
    ↓  byte
[BufferedInputStream]       (tùy chọn: buffer byte)
    ↓  byte
InputStreamReader(UTF-8)     (giải mã byte → char)
    ↓  char
BufferedReader              (tùy chọn: buffer char, readLine)
    ↓  String
Bộ phân tích định dạng       (parse từng dòng, CSV, JSON...)
    ↓
Giá trị nghiệp vụ
```

Sơ đồ ghi văn bản là chiều ngược lại về **ngữ nghĩa xử lý dữ liệu**:

```text
Giá trị nghiệp vụ
    ↓
Bộ tạo định dạng             (CSV, JSON, các dòng văn bản...)
    ↓  String / char
BufferedWriter               (tùy chọn: buffer char)
    ↓  char
OutputStreamWriter(UTF-8)     (mã hóa char → byte)
    ↓  byte
[BufferedOutputStream]       (tùy chọn: buffer byte)
    ↓  byte
Đích byte (file/network)
```

Dấu ngoặc vuông nghĩa là lớp tùy chọn. Sơ đồ diễn tả **chiều dữ liệu chảy**; mã Java thực tế thường được tạo từ **lớp bên trong (nguồn/đích) ra lớp bên ngoài (API ứng dụng dùng)**.

## 2. Ghép lớp theo kiểu Java

| Nhóm | Đầu vào/đầu ra của lớp | Vai trò |
| --- | --- | --- |
| `InputStream` / `OutputStream` | `byte`, `byte[]` | Nền tảng I/O byte |
| `BufferedInputStream` / `BufferedOutputStream` | `InputStream` / `OutputStream` | Bọc luồng byte và thêm buffer byte |
| `Reader` / `Writer` | `char`, `char[]`, `String` | Nền tảng I/O ký tự |
| `InputStreamReader` | Bọc một `InputStream` | Giải mã byte thành ký tự bằng charset |
| `OutputStreamWriter` | Bọc một `OutputStream` | Mã hóa ký tự thành byte bằng charset |
| `BufferedReader` / `BufferedWriter` | Bọc `Reader` / `Writer` | Thêm buffer ký tự, `readLine()` / `newLine()` |
| `PrintWriter` | Bọc `Writer` hoặc `OutputStream` | `print`, `println`, `printf` để tạo văn bản trình bày |
| `DataInputStream` / `DataOutputStream` | Bọc luồng byte | Đọc/ghi kiểu nguyên thủy theo **định dạng nhị phân** đã quy định |

Ví dụ đúng về mặt kiểu:

```java
// input là InputStream đã mở:
Reader decoder = new InputStreamReader(input, StandardCharsets.UTF_8);
BufferedReader lines = new BufferedReader(decoder);

// output là OutputStream đã mở:
Writer encoder = new OutputStreamWriter(output, StandardCharsets.UTF_8);
BufferedWriter text = new BufferedWriter(encoder);
```

Đoạn trên **chỉ minh họa cách ghép lớp**; `input`/`output` phải được khai báo và mở ở ngữ cảnh bao quanh. Khi dùng thực tế nên quản lý đóng luồng bằng `try-with-resources`, như ví dụ đầy đủ bên dưới.

Ví dụ sai về kiểu hoặc ý nghĩa:

- `new BufferedReader(input)` **không biên dịch**, vì `BufferedReader` yêu cầu `Reader` chứ không nhận trực tiếp `InputStream`. Cần một `InputStreamReader` làm cầu nối.
- `new BufferedInputStream(reader)` cũng không biên dịch, vì buffer byte không nhận `Reader`.
- Gọi `readLine()` trên `InputStream` không có nghĩa; đây là API xử lý ký tự ở `BufferedReader`.
- Lấy byte UTF-8 rồi tự ép từng byte thành `char` không phải giải mã UTF-8; ký tự Unicode có thể cần nhiều byte.

## 3. Ghép lớp đọc đúng thứ tự

Từ một nguồn byte:

```java
BufferedReader reader = new BufferedReader(
        new InputStreamReader(
                new BufferedInputStream(input),
                StandardCharsets.UTF_8));
```

Luồng xử lý thực tế:

1. `BufferedInputStream` nhận byte từ `input` theo khối.
2. `InputStreamReader` **giải mã UTF-8**, xử lý đúng cả ký tự được mã hóa thành nhiều byte nằm qua ranh giới giữa các lần đọc.
3. `BufferedReader` giữ ký tự trong buffer và cho phép `readLine()`.
4. Ứng dụng nhận `String` để xử lý theo định dạng đã thống nhất.

**Không phải lúc nào cũng cần cả hai buffer.** `InputStreamReader`/decoder và các lớp I/O có cơ chế quản lý dữ liệu riêng; `BufferedReader` thường đã đáp ứng nhu cầu đọc theo dòng. Bổ sung `BufferedInputStream` chỉ vì “càng nhiều buffer càng nhanh” có thể tăng bộ nhớ, độ phức tạp và không cải thiện tốc độ. Cần chọn buffer dựa trên cách truy cập, nguồn I/O, đo đạc thực tế.

Nếu tệp không chứa văn bản mà chứa dữ liệu nhị phân, không dùng `Reader` chỉ để tận dụng `readLine()`. Chọn API byte hoặc parser nhị phân tương ứng.

## 4. Ghép lớp ghi đúng thứ tự

Từ một đích byte:

```java
BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(
                new BufferedOutputStream(output),
                StandardCharsets.UTF_8));
```

Khi ứng dụng gọi `writer.write(...)`:

1. `BufferedWriter` có thể giữ các ký tự trong bộ đệm.
2. `OutputStreamWriter` chuyển ký tự thành byte theo UTF-8.
3. `BufferedOutputStream` có thể giữ các byte trong bộ đệm.
4. `output` ghi byte vào đích khi dữ liệu được đẩy xuống.

Lớp nằm ngoài cùng quyết định giao diện ứng dụng sử dụng: `write(String)`, `newLine()` ở `BufferedWriter`. Ứng dụng không cần tự tính từng byte UTF-8. Với protocol cần UTF-8, charset phải được chọn **cả phía ghi lẫn phía đọc**; buffer không có khả năng sửa lỗi dùng sai charset.

Để ghi một tài liệu nhỏ hoặc một tệp thông thường, biến thể **đơn giản hơn** cũng hợp lý:

```java
BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(output, StandardCharsets.UTF_8));
```

Hoặc khi thao tác với file bằng `Path`:

```java
BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
```

Các dòng này là **đoạn mã minh họa**, cần khai báo `path`, import `java.nio.file.Files`, `java.nio.file.Path` và xử lý `IOException`. `Files.newBufferedWriter` mặc định có thể **tạo hoặc ghi đè nội dung tệp**; không chạy tùy tiện trên tệp cần giữ nguyên. Các API `Files` ở đây giúp tránh phải ghép thủ công khi không cần kiểm soát lớp stream byte ở tầng dưới.

## 5. Ví dụ Java 17+ chạy được: buffer byte, charset và định dạng dòng

Chương trình dùng mảng byte trong bộ nhớ để tránh đụng vào dữ liệu thật. Tạo file **`LayeredTextDemo.java`**:

```java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class LayeredTextDemo {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new BufferedOutputStream(sink),
                        StandardCharsets.UTF_8))) {
            writer.write("Tên,điểm");
            writer.newLine();
            writer.write(String.format(Locale.ROOT, "%s,%.2f", "An", 7.5));
            writer.newLine();
            writer.write(String.format(Locale.ROOT, "%s,%.2f", "Bình", 8.0));
            writer.newLine();
        }

        byte[] bytes = sink.toByteArray();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new BufferedInputStream(new ByteArrayInputStream(bytes)),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Dòng: " + line);
            }
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 LayeredTextDemo.java
java LayeredTextDemo
```

Đầu ra:

```text
Dòng: Tên,điểm
Dòng: An,7.50
Dòng: Bình,8.00
```

Điểm cần quan sát:

- Giá trị `7.5`/`8.0` thành `7.50`/`8.00` ở **bước định dạng** `String.format(Locale.ROOT, "%.2f", ...)`. `Locale.ROOT` giúp dấu thập phân là dấu chấm; charset không tự chọn dấu thập phân.
- `StandardCharsets.UTF_8` quyết định **byte của chữ có dấu** như `Tên`, `điểm` và `Bình`, nhưng không quyết định tên cột hay quy ước dấu phẩy.
- `writer.newLine()` tạo dấu hết dòng theo nền tảng. `reader.readLine()` nhận diện các dạng kết thúc dòng quen thuộc và trả nội dung không kèm ký tự kết thúc dòng, vì vậy đầu ra console ổn định.
- `try-with-resources` đóng `BufferedWriter` ngoài cùng, đồng thời đẩy dữ liệu qua `OutputStreamWriter` và `BufferedOutputStream` xuống `sink` trước khi gọi `toByteArray()`. Nếu lấy bytes quá sớm, phần còn chờ trong buffer có thể chưa xuất hiện.
- `ByteArrayOutputStream` hỗ trợ lấy bản sao nội dung đã ghi ngay cả sau khi wrapper phía trên đóng nó.

Mã dùng cú pháp đơn giản giống CSV để minh họa **thứ tự lớp**. Đây chưa phải serializer/parser CSV hoàn chỉnh: nếu giá trị chứa dấu phẩy, dấu ngoặc kép hoặc xuống dòng, phải tuân theo quy tắc escaping/quoting của định dạng CSV, thường nên dùng thư viện xử lý CSV tương ứng. `line.split(",")` đơn giản không đủ cho các trường hợp đó.

## 6. “Định dạng” nằm ở đâu trong chuỗi?

Chọn **định dạng** trước khi quyết định gọi API nào để encode hoặc decode.

### Văn bản có cấu trúc

Định dạng quyết định **cú pháp bản ghi và giá trị**:

- CSV: cách phân tách trường, quoting dấu phẩy, escaping dấu ngoặc kép, dòng và header.
- JSON: đối tượng, mảng, chuỗi, escape và giá trị boolean/number theo đặc tả JSON.
- Dữ liệu ghi từng dòng: mỗi dòng là một record nếu ứng dụng xác định như vậy; không tự suy diễn mọi file văn bản đều có cấu trúc này.
- Log: timestamp, level, thông điệp, quy ước xuống dòng; cần chọn cách định dạng ngày giờ và số độc lập với charset.

Trong các trường hợp này, **serializer/parser** chạy ở tầng ký tự hoặc byte theo thư viện định dạng, còn **charset** đảm nhận phần biến đổi giữa ký tự và byte. Chẳng hạn JSON trên một giao thức có charset UTF-8: serializer tạo byte UTF-8 (hoặc ghi ký tự qua `Writer` phù hợp); không thể đổi parser JSON thành `BufferedReader` bằng cách thêm buffer.

### `PrintWriter` và `printf`

`PrintWriter` tiện cho định dạng trình bày với `print`, `println` và `printf`. Ví dụ cấu trúc kiểu thường thấy:

```java
PrintWriter out = new PrintWriter(
        new BufferedWriter(
                new OutputStreamWriter(output, StandardCharsets.UTF_8)));

out.printf(Locale.ROOT, "total=%.2f%n", 12.5);
out.flush();
```

Đây là **mã minh họa**, với `output` là một `OutputStream` đã mở và do nơi gọi chịu trách nhiệm quản lý vòng đời. `printf` xử lý **cách biểu diễn số và chuỗi**, `OutputStreamWriter` xử lý **charset**, `BufferedWriter` xử lý **buffer**. `PrintWriter` thường **không ném `IOException` từ các thao tác print/write/printf** mà ghi nhận trạng thái lỗi; khi độ tin cậy đầu ra quan trọng phải dùng `checkError()` hoặc chọn `Writer`/API có truyền lỗi trực tiếp. Nếu dùng `try-with-resources` với `PrintWriter` sở hữu luồng, đóng lớp ngoài cùng một lần.

`%n` là dấu xuống dòng theo nền tảng, khác với `\n` cố định. Với định dạng file có quy định chuẩn cụ thể về CRLF/LF, hãy ghi rõ dấu xuống dòng tương ứng và không phụ thuộc hệ điều hành.

### Dữ liệu nhị phân có định dạng riêng

Một định dạng nhị phân có thể quy định `int` 4 byte, `long` 8 byte, độ dài rồi đến byte dữ liệu, checksum, v.v. `DataOutputStream`/`DataInputStream` làm việc trực tiếp trên byte:

```java
DataOutputStream out = new DataOutputStream(
        new BufferedOutputStream(output));
out.writeInt(42);
out.writeUTF("Xin chào");
out.flush();
```

Đây là **mã minh họa** cần `output` đã mở, import `java.io.DataOutputStream`/`java.io.BufferedOutputStream` và quản lý đóng/ngoại lệ. Phía đọc tương ứng phải dùng `DataInputStream.readInt()` trước `readUTF()`, đúng **thứ tự trường dữ liệu** đã ghi. `writeUTF` sử dụng **modified UTF-8 của `DataOutputStream`**, khác UTF-8 chuẩn ở một số trường hợp (ví dụ ký tự NUL và surrogate); không thay nó bằng `new String(bytes, UTF_8)` hoặc `InputStreamReader(UTF_8)` nếu không tái hiện chính xác định dạng byte.

Ở đây **không có bước chuyển một tài liệu văn bản qua `OutputStreamWriter`**. Cố áp charset/`Reader` lên toàn bộ dãy byte của định dạng nhị phân có thể làm hỏng dữ liệu. Chọn nhánh text hoặc binary dựa trên đặc tả định dạng thực tế.

## 7. Nếu có nén, mã hóa hay biến đổi byte khác

Lớp biến đổi byte (chẳng hạn `GZIPInputStream`/`GZIPOutputStream`) xử lý **byte đã nén**, không thể nhận ký tự trực tiếp. Vì thế với văn bản UTF-8 được nén GZIP:

```text
GHI:  String → Writer tạo ký tự → UTF-8 bytes → GZIP bytes → đích
ĐỌC: nguồn GZIP bytes → giải nén → UTF-8 bytes → Reader → String
```

Ví dụ Java 17+ độc lập **`GzipTextPipelineDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipTextPipelineDemo {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new GZIPOutputStream(destination),
                        StandardCharsets.UTF_8))) {
            writer.write("Café");
            writer.newLine();
        }

        byte[] compressed = destination.toByteArray();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(
                                new ByteArrayInputStream(compressed)),
                        StandardCharsets.UTF_8))) {
            System.out.println(reader.readLine());
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 GzipTextPipelineDemo.java
java GzipTextPipelineDemo
```

Đầu ra:

```text
Café
```

**Điểm quan trọng:** `GZIPOutputStream` cần `finish()` hoặc `close()` để ghi đầy đủ trailer/checksum của định dạng GZIP. Đóng `BufferedWriter` ngoài cùng làm đóng dây chuyền xuống GZIP, rồi mới lấy `destination.toByteArray()`. Nếu đảo thứ tự và cố đưa byte GZIP thẳng vào `InputStreamReader`, decoder UTF-8 sẽ đọc byte nén như văn bản và kết quả không còn đúng.

Với nén và mã hóa bảo mật cùng tồn tại, thứ tự lớp phải tuân **đặc tả giao thức**: ví dụ một giao thức có thể yêu cầu nén trước rồi mới mã hóa ở chiều ghi, và giải mã rồi mới giải nén ở chiều đọc. Không tự hoán đổi vì các phép biến đổi không nhất thiết giao hoán. Lớp buffer cũng cần bố trí ở vị trí phù hợp để không làm hỏng ranh giới thông điệp hay cơ chế flush của protocol.

## 8. Tốc độ, kích thước buffer và hành vi đọc trước

**Buffer byte** gom các lệnh đọc/ghi byte; **buffer ký tự** gom các thao tác đọc/ghi ký tự và cung cấp API tiện lợi như `readLine()`. Hai buffer có thể cùng tồn tại vì chúng xử lý dữ liệu ở hai phía của bộ chuyển charset, nhưng **không tự động có lợi** khi thêm cả hai.

Các tình huống thường gặp:

| Nhu cầu | Chuỗi lớp thường phù hợp |
| --- | --- |
| Đọc dòng UTF-8 từ file | `Files.newBufferedReader(path, UTF_8)` |
| Ghi dòng UTF-8 vào file | `Files.newBufferedWriter(path, UTF_8)` |
| Đọc block byte từ file | `BufferedInputStream(Files.newInputStream(path))` hoặc đọc block trực tiếp từ `InputStream` |
| Xử lý bản ghi văn bản từ socket | `BufferedReader(InputStreamReader(socket.getInputStream(), UTF_8))`, kèm framing giao thức |
| Xuất dữ liệu nhị phân nhiều trường | `DataOutputStream(BufferedOutputStream(output))` |
| Đọc văn bản đã nén GZIP | `BufferedReader(InputStreamReader(GZIPInputStream(input), UTF_8))` |

Hầu hết ví dụ Java ở trên dùng UTF-8 làm bộ mã tường minh. Khi giao thức/định dạng yêu cầu charset khác, phải áp dụng charset đúng theo đặc tả cả hai chiều.

**Đọc trước (read-ahead) có hậu quả:** `BufferedReader` và `InputStreamReader` có thể lấy từ luồng byte nhiều dữ liệu hơn lượng ký tự ứng dụng vừa yêu cầu. Nếu đọc vài dòng bằng wrapper rồi quay lại đọc trực tiếp `InputStream` gốc, vị trí đọc thực tế có thể đã vượt quá điểm tưởng tượng. Không trộn tùy ý các API truy cập chung một luồng; hãy để **một lớp ngoài cùng** quản lý quá trình đọc hoặc dùng framing/tầng parser được thiết kế cho việc chuyển giữa các loại dữ liệu.

Kích thước buffer lớn hơn không luôn nhanh hơn: còn phụ thuộc tốc độ mạng/đĩa, mẫu truy cập, số luồng chạy, áp lực bộ nhớ, chính sách flush và throughput/latency cần đạt. Chỉ tăng buffer khi có số liệu đo cụ thể.

## 9. `flush()`, `close()` và chủ sở hữu luồng

`BufferedWriter` có thể chưa đẩy dữ liệu xuống ngay khi gọi `write`. Ngay cả khi các byte đã qua `OutputStreamWriter`, `BufferedOutputStream` cũng có thể đang giữ lại. Vì thế:

- **`flush()` trên lớp ngoài cùng** yêu cầu đẩy dữ liệu trong chuỗi các wrapper xuống các tầng dưới theo hợp đồng của từng lớp.
- **`close()` lớp ngoài cùng** thường flush phần còn chờ rồi đóng các lớp bọc bên dưới; `try-with-resources` là cách thông thường để bảo đảm bước này.
- `flush()` **không đồng nghĩa dữ liệu đã được ghi bền vững lên thiết bị** (fsync) hay đầu nhận đã xử lý xong. Nếu cần durability phải dùng API và giao thức thích hợp, ví dụ `FileChannel.force(...)` trong tình huống tương ứng.
- Với stream biến đổi có định dạng kết thúc, như GZIP, **flush có thể chưa đủ để hoàn chỉnh output**; cần `finish()` hoặc `close()` theo hợp đồng của codec.

Ví dụ:

```java
try (BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
    writer.write("OK");
    writer.flush(); // đẩy dữ liệu đang đệm; ví dụ có thể cần để phản hồi ngay
}
```

Đây là **mã minh họa**: `output` là luồng đã mở và được wrapper sở hữu. Không đóng một wrapper nếu bạn cần giữ luồng gốc tiếp tục mở trừ khi có thiết kế quản lý vòng đời riêng. Tránh đóng nhiều lần từng lớp mà không hiểu quan hệ sở hữu. Với `System.out`, không dùng `try-with-resources` tùy tiện vì đóng nó có thể ảnh hưởng toàn bộ ứng dụng.

Trong giao thức tương tác, writer được buffer cần `flush()` ở **ranh giới hoàn tất một thông điệp** nếu phía nhận đang chờ; ngược lại flush sau từng ký tự làm mất lợi ích bộ đệm. `BufferedReader.readLine()` cần ký tự kết thúc dòng hoặc EOF để hoàn tất dòng; nếu phía ghi chưa gửi newline/chưa flush, phía đọc có thể chờ mãi dù ứng dụng tưởng đã gọi `write`.

## 10. Những lỗi ghép lớp phổ biến

| Lỗi | Vì sao xảy ra | Cách làm đúng |
| --- | --- | --- |
| `BufferedReader(InputStream)` | Sai kiểu: reader cần `Reader` | Thêm `InputStreamReader(input, charset)` |
| Tạo `InputStreamReader` sau khi đã dùng `DataInputStream` đọc nhị phân tùy ý | Byte giao thức có thể không phải text | Dùng đúng parser/định dạng được quy định |
| Mã hóa String theo UTF-8, đọc theo charset mặc định | Charset hai phía không thống nhất | Truyền cùng charset tường minh |
| Tách byte UTF-8 bằng các lệnh ép `(char)byte` | Một ký tự có thể chứa nhiều byte | Để decoder xử lý ranh giới byte |
| Trộn đọc trực tiếp nguồn với `BufferedReader` đã bọc nguồn | Read-ahead làm vị trí nguồn khó đoán | Dùng một parser/chuỗi wrapper nhất quán |
| Đọc GZIP bytes trực tiếp bằng `InputStreamReader` | Chưa giải nén | `GZIPInputStream` phải giải nén trước khi decode |
| Gọi `toByteArray()` khi writer còn mở | Nội dung còn nằm trong buffer | Flush/close đúng thời điểm; với GZIP phải hoàn tất trailer |
| Mở cả buffer byte và buffer char không có lý do | Tăng overhead/bộ nhớ, đôi khi thêm latency | Chọn buffer theo nhu cầu và đo lường |
| `println` trong giao thức cần đúng LF | Xuống dòng phụ thuộc nền tảng | Ghi delimiter được giao thức quy định |
| Gọi `String.split(",")` để parse CSV bất kỳ | Không xử lý quoting/escape | Sử dụng parser đúng chuẩn CSV |
| Tin `PrintWriter` luôn báo `IOException` trực tiếp | PrintWriter có thể lưu cờ lỗi nội bộ | Kiểm tra `checkError()` hoặc chọn API báo lỗi |
| Gọi `flush` và tưởng đã lưu an toàn trên đĩa | Flush buffer ≠ durability của filesystem | Dùng cơ chế force/sync khi có yêu cầu bền vững |

## 11. Phương pháp thiết kế một pipeline I/O

Đối với một đường đọc hoặc ghi bất kỳ, lần lượt trả lời:

1. **Dữ liệu gốc là gì?** File, socket, byte mảng, bộ nhớ, hay luồng đã được API khác mở?
2. **Dữ liệu logic là text hay binary?** Với binary, chọn định dạng/protocol nhị phân; với text, xác định encoding thực.
3. **Có biến đổi byte không?** Nén, giải nén, mã hóa bảo mật, checksum... Sắp xếp đúng thứ tự được định dạng quy định.
4. **Ứng dụng cần thao tác ở mức nào?** Từng byte, dòng văn bản, record CSV, object JSON, số nguyên nhị phân?
5. **Buffer đặt ở đâu có giá trị?** Đọc dòng thì thường `BufferedReader`; byte block thì buffer byte hoặc đọc theo mảng. Tránh đệm lặp không cần thiết.
6. **Ai sở hữu nguồn/đích và khi nào hoàn tất?** Chọn nơi gọi `flush`, `finish` hoặc `close`; lưu ý writer có thể chưa đẩy dữ liệu.
7. **Đã kiểm thử hai chiều chưa?** Encode → decode, định dạng → parse, EOF, dữ liệu Unicode, ký tự không hợp lệ, thiếu dòng cuối, và trường hợp buffer nhỏ.

**Bài tự luyện:** Thiết kế pipeline ghi một file GZIP chứa CSV UTF-8, sau đó đọc lại từng dòng. Với phía ghi, đặt bộ tạo CSV ở lớp logic ngoài cùng, rồi writer UTF-8, sau đó `GZIPOutputStream` và file byte. Với phía đọc, đảo đường biến đổi: file byte → `GZIPInputStream` → `InputStreamReader(UTF_8)` → `BufferedReader` → parser CSV. Giải thích vì sao không đọc byte GZIP bằng `BufferedReader` ngay lập tức, và vì sao phải đóng gzip writer trước khi lấy bản nén hoàn chỉnh.

**Ghi nhớ:** Thứ tự đúng xuất phát từ **hợp đồng kiểu dữ liệu** và **đặc tả định dạng**: byte stream ở lõi I/O; buffer byte ở tầng byte khi cần; charset bridge chỉ ở ranh giới byte–char; buffer char và format/parser ở tầng văn bản; phép nén/mã hóa byte phải nằm ở đúng vị trí giao thức quy định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buffering-and-stream-composition-005">Kích thước bộ đệm và số lần gọi I/O</a>

<details>
<summary>Xem chi tiết</summary>

# Kích thước bộ đệm và số lần gọi I/O trong Java

## 1. Mục tiêu học tập

Khi đọc/ghi tệp hoặc trao đổi dữ liệu qua luồng, cách gọi `read()` và `write()` ảnh hưởng đến số lần lớp Java phải yêu cầu lớp bên dưới cung cấp hoặc nhận dữ liệu. **Bộ đệm (buffer)** gom nhiều thao tác nhỏ thành các lần trao đổi theo khối, thường giảm chi phí gọi I/O và cải thiện thông lượng, nhưng không phải cứ tăng kích thước buffer thì chương trình sẽ luôn nhanh hơn.

Sau bài học, bạn có thể:

- Giải thích **buffer là gì**, luồng có đệm khác gì luồng không đệm, và quan hệ giữa kích thước buffer với số lần yêu cầu dữ liệu từ tầng dưới.
- Ước tính số lần đọc/ghi theo khối trong mô hình đơn giản và chỉ ra vì sao số **lần gọi phương thức Java** không đồng nghĩa chính xác với số **system call** hay thao tác đọc/ghi vật lý trên thiết bị.
- Chọn kích thước đệm ban đầu hợp lý cho `BufferedInputStream`, `BufferedOutputStream`, `BufferedReader`, `BufferedWriter`; hiểu khi nào tăng buffer hữu ích hoặc không.
- Nhận diện đánh đổi giữa **throughput, latency, memory, flush, EOF, thời gian chờ và hiệu quả khi có nhiều luồng**.
- Đo hiệu quả bằng ví dụ Java 17+ có đầu ra xác định, rồi biết cách thiết kế phép đo hiệu năng thực tế có kiểm soát.

Phạm vi bài là **bộ đệm và ghép luồng** trong Java Core I/O. Nội dung tập trung vào nguyên tắc ra quyết định và phương pháp kiểm chứng, không đưa ra “kích thước tối ưu” chung cho mọi thiết bị hay workload.

## 2. Buffer là gì và được đặt ở đâu?

**Buffer** là vùng nhớ tạm giữ dữ liệu giữa nơi sinh/nhận dữ liệu và nơi tiêu thụ/ghi dữ liệu. Khi ứng dụng xử lý từng byte hoặc từng ký tự, lớp có đệm có thể gom chúng thành một khối lớn trước khi chuyển xuống luồng bên dưới.

Ví dụ, chương trình đọc từng byte:

```text
Ứng dụng:        read() read() read() ... 1000 lần
                      |
BufferedInputStream (buffer 128 byte):
                 1 lần nạp tối đa 128 byte → phục vụ nhiều lần read()
                      |
InputStream bên dưới:
                 nhiều yêu cầu nhỏ có thể thành ít yêu cầu theo khối hơn
                      |
Hệ điều hành / filesystem / thiết bị
```

Chiều ghi:

```text
Ứng dụng:        write(1 byte) write(1 byte) ...
                      |
BufferedOutputStream (buffer 128 byte):
                 gom các byte vào bộ nhớ
                      |
                 ghi xuống luồng bên dưới theo khối
                      |
Hệ điều hành / filesystem / thiết bị
```

Buffer nằm ở nhiều tầng: lớp `Buffered*` trong Java, bộ đệm của `Reader`/`Writer`/encoder, page cache của hệ điều hành, bộ nhớ của thiết bị và đôi khi buffer mạng. Chúng có vai trò khác nhau. **Thêm một `BufferedInputStream` không bảo đảm thêm một thao tác đọc vật lý cho mỗi lần nạp buffer.**

### 2.1. Các lớp I/O thường gặp

| Lớp / API | Đơn vị | Vai trò |
| --- | --- | --- |
| `InputStream`, `OutputStream` | Byte | Giao diện đọc/ghi byte; việc có đệm phụ thuộc lớp triển khai |
| `BufferedInputStream` | Byte | Dự trữ một mảng byte; các `read()` nhỏ có thể được phục vụ từ mảng |
| `BufferedOutputStream` | Byte | Gom nhiều `write()` nhỏ; ghi khối xuống OutputStream bên dưới |
| `Reader`, `Writer` | `char` UTF-16 | Giao diện đọc/ghi ký tự, có thể liên quan mã hóa ký tự |
| `BufferedReader` | `char` | Gom đọc ký tự; có `readLine()` để đọc theo dòng |
| `BufferedWriter` | `char` | Gom ký tự trước khi ghi xuống writer được bọc |
| `Files.newBufferedReader` / `newBufferedWriter` | Ký tự | Tiện ích mở luồng văn bản có đệm, nên chỉ định charset |
| `FileChannel` và `ByteBuffer` | Byte | API channel/buffer cho nhu cầu theo vị trí hoặc tối ưu cụ thể |

**Lưu ý:** “Không dùng lớp `BufferedInputStream`” không có nghĩa mọi lần gọi `read()` chắc chắn gây system call; một số lớp đầu vào vốn đã có cache/buffer. Ngược lại, thêm nhiều lớp đệm chồng nhau không tự động tạo hiệu năng tốt hơn.

## 3. Kích thước bộ đệm ảnh hưởng số lần gọi I/O thế nào?

Giả sử tổng lượng dữ liệu là **N byte**, mỗi lần lớp dưới cung cấp/nhận thành công đúng **B byte**, không có lỗi hoặc trả về ngắn. Số lần chuyển dữ liệu theo khối tối thiểu theo mô hình này là:

\[
K = \left\lceil \frac{N}{B} \right\rceil
\]

Với N = 1.000 byte:

| Kích thước B | Số khối để chuyển 1.000 byte theo giả định đơn giản |
| ---: | ---: |
| 1 byte | 1.000 |
| 10 byte | 100 |
| 100 byte | 10 |
| 128 byte | 8 (7 khối 128 + 1 khối 104) |
| 256 byte | 4 |
| 1.024 byte | 1 |

Công thức trên là **ước lượng cho số lần truyền payload khi mỗi yêu cầu có thể xử lý tối đa B byte**, không phải cam kết của Java hay filesystem. Thực tế có thể khác vì:

- `read(byte[])` có thể trả về **ít hơn** số byte đã yêu cầu, ngay cả khi chưa EOF; cần xử lý đúng giá trị trả về.
- Vòng lặp đọc đến EOF thường cần **một lần đọc cuối** trả `-1` để xác nhận đã hết dữ liệu.
- `write(byte[], off, len)` của `OutputStream` tuân thủ hợp đồng API khác với `WritableByteChannel.write(ByteBuffer)`; trên channel có thể phải lặp vì ghi từng phần.
- Buffer không đầy có thể được đẩy ra khi gọi `flush()` hoặc `close()`; nhiều lần flush nhỏ có thể làm tăng số lần ghi xuống dưới.
- Người đọc có thể gọi `read(byte[])` với khối lớn hoặc có các lớp tối ưu/đường chuyển dữ liệu riêng, khiến kích thước lớp buffer không còn quyết định trực tiếp từng yêu cầu.
- Mã hóa ký tự, nén dữ liệu, độ lớn block filesystem và cache hệ điều hành làm quan hệ giữa ký tự, byte và thao tác thiết bị thêm phức tạp.

**Điểm quan trọng:** phép đếm ở tầng `Buffered*` thường là số lần gọi **luồng được bọc**. Muốn khẳng định số system calls thật sự phải đo ở tầng hệ điều hành bằng công cụ phù hợp; không thể suy một-một chỉ từ số lời gọi Java.

### 3.1. Mô hình chi phí và lợi ích giảm dần

Một mô hình định tính hữu ích:

\[
T_{\text{total}} \approx T_{\text{data}} + K \times T_{\text{call}} + T_{\text{copy}} + T_{\text{other}}
\]

Trong đó `T_data` là chi phí vận chuyển dữ liệu, `T_call` là chi phí cố định của mỗi lời gọi sang tầng dưới, `T_copy` là chi phí sao chép qua buffer và `T_other` gồm mã hóa, lock, scheduling, caching hoặc độ trễ thiết bị.

Tăng B có thể làm giảm K mạnh khi B ban đầu rất nhỏ. Nhưng khi K đã ít, tăng B thêm có thể mang lại lợi ích nhỏ hoặc khiến phần giữ bộ nhớ/copy phức tạp hơn. Đây chỉ là **mô hình phân tích**, không phải công thức dự đoán thời gian chính xác trên mọi máy.

## 4. Ví dụ Java 17+: đếm số lần gọi tầng dưới khi đọc và ghi

Ví dụ dùng các `InputStream`/`OutputStream` đo đếm trong bộ nhớ, không truy cập tệp thật. Nhờ đó, kết quả **tất định** và chỉ rõ sự khác biệt giữa “ứng dụng gọi phương thức bao nhiêu lần” với “buffer yêu cầu luồng bên dưới bao nhiêu lần”.

Lưu thành **`BufferCallCountDemo.java`**:

```java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

public class BufferCallCountDemo {
    static final class CountingInput extends InputStream {
        private final byte[] data;
        private int position;
        int calls;

        CountingInput(byte[] data) {
            this.data = data;
        }

        @Override
        public int read() {
            calls++;
            return position < data.length ? data[position++] & 0xff : -1;
        }

        @Override
        public int read(byte[] destination, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, destination.length);
            if (length == 0) {
                return 0;
            }
            calls++;
            if (position >= data.length) {
                return -1;
            }
            int count = Math.min(length, data.length - position);
            System.arraycopy(data, position, destination, offset, count);
            position += count;
            return count;
        }
    }

    static final class CountingOutput extends OutputStream {
        int calls;
        int bytes;

        @Override
        public void write(int value) {
            calls++;
            bytes++;
        }

        @Override
        public void write(byte[] source, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, source.length);
            calls++;
            bytes += length;
        }
    }

    public static void main(String[] args) throws IOException {
        byte[] data = new byte[1000];
        Arrays.fill(data, (byte) 65);

        CountingInput rawInput = new CountingInput(data);
        int rawReadBytes = 0;
        while (rawInput.read() != -1) {
            rawReadBytes++;
        }

        CountingInput bufferedSource = new CountingInput(data);
        int bufferedReadBytes = 0;
        try (InputStream in = new BufferedInputStream(bufferedSource, 128)) {
            while (in.read() != -1) {
                bufferedReadBytes++;
            }
        }

        CountingOutput rawOutput = new CountingOutput();
        for (byte value : data) {
            rawOutput.write(value);
        }

        CountingOutput bufferedTarget = new CountingOutput();
        try (OutputStream out = new BufferedOutputStream(bufferedTarget, 128)) {
            for (byte value : data) {
                out.write(value);
            }
        }

        System.out.println("Read raw: " + rawInput.calls
                + " calls, " + rawReadBytes + " bytes");
        System.out.println("Read buffered: " + bufferedSource.calls
                + " calls, " + bufferedReadBytes + " bytes");
        System.out.println("Write raw: " + rawOutput.calls
                + " calls, " + rawOutput.bytes + " bytes");
        System.out.println("Write buffered: " + bufferedTarget.calls
                + " calls, " + bufferedTarget.bytes + " bytes");
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 BufferCallCountDemo.java
java BufferCallCountDemo
```

`stdout`:

```text
Read raw: 1001 calls, 1000 bytes
Read buffered: 9 calls, 1000 bytes
Write raw: 1000 calls, 1000 bytes
Write buffered: 8 calls, 1000 bytes
```

**Giải thích kết quả:**

1. **Đọc không đệm:** `read()` được gọi 1.000 lần để nhận từng byte và **lần thứ 1.001** trả EOF (`-1`).
2. **Đọc với buffer 128:** `BufferedInputStream` phục vụ nhiều lệnh `read()` từ mảng byte. Với nguồn đo đếm trong ví dụ, luồng dưới nhận 8 yêu cầu lấy dữ liệu (7 × 128 + 104 byte) và 1 yêu cầu báo EOF, tổng **9**.
3. **Ghi không đệm:** gọi `write(int)` 1.000 lần trên `CountingOutput`, mỗi lần ghi một byte, nên có **1.000** lời gọi tới đích.
4. **Ghi với buffer 128:** các byte được gom thành 7 lần ghi 128 byte và một lần ghi 104 byte khi đóng luồng, tổng **8** lời gọi xuống `CountingOutput`.

**Các số trên đo lời gọi tới đối tượng `CountingInput`/`CountingOutput`, không phải số system calls thực tế.** Hai lớp đo đếm không mở file descriptor và không ghi ra thiết bị. Giá trị `8`/`9` là kết quả của cách tổ chức luồng trong ví dụ, không phải cam kết rằng mọi reader/writer hoặc filesystem sẽ luôn có những con số đó.

Ở lần ghi cuối, `try-with-resources` đóng `BufferedOutputStream` và đẩy phần byte còn trong bộ đệm xuống đích. Nếu chương trình thoát bất thường trước khi flush/close, phần dữ liệu chưa đẩy xuống có thể không xuất hiện ở luồng bên dưới.

## 5. Ảnh hưởng khác nhau giữa đọc và ghi

### 5.1. Đọc từng byte, đọc thành khối

Một vòng lặp:

```java
int value;
while ((value = in.read()) != -1) {
    // xử lý một byte
}
```

Nếu `in` là `FileInputStream`, rất nhiều lần đọc từng byte có thể gây chi phí lớn; nếu `in` là `BufferedInputStream`, phần lớn `read()` được phục vụ bằng buffer đã nạp. Nhưng chương trình vẫn có chi phí xử lý **mỗi byte một vòng lặp**.

Khi bài toán chỉ cần chuyển dữ liệu, đọc thành khối giúp giảm cả số lời gọi ở tầng ứng dụng:

```java
byte[] block = new byte[16 * 1024];
int count;
while ((count = in.read(block)) != -1) {
    if (count > 0) {
        out.write(block, 0, count);
    }
}
```

Đây là **đoạn minh họa**, không phải chương trình độc lập; `in` và `out` cần là các luồng mở hợp lệ. Luôn dùng `count` thực tế: lần đọc có thể ngắn hơn chiều dài `block`. Với luồng chuẩn ở chế độ blocking và `block.length > 0`, đọc thành công thường có số byte dương; đoạn trên vẫn xử lý an toàn trường hợp đọc bằng 0. Quản lý đóng hai luồng bằng `try-with-resources` tại nơi sở hữu tài nguyên.

Nếu luồng có sẵn cơ chế chuyển tối ưu, cân nhắc `InputStream.transferTo(OutputStream)` hoặc `Files.copy`. Không giả định viết lại thủ công luôn nhanh hơn API chuẩn.

### 5.2. Ghi nhỏ liên tục, nhu cầu flush và độ trễ

`BufferedOutputStream` gom `write` nhỏ cho đến khi buffer đầy hoặc được `flush()`/`close()`. Điều này thường cải thiện thông lượng khi ghi một lượng dữ liệu lớn.

Tuy nhiên, nếu chương trình cần bên nhận thấy một thông điệp **ngay lập tức**, giữ dữ liệu trong buffer có thể tăng latency. Với giao thức yêu cầu ranh giới thông điệp, flush ở **điểm hoàn thành thông điệp**, không flush sau mỗi byte một cách máy móc.

Ví dụ đối với console, socket hoặc output phân lớp: dữ liệu chưa được flush có thể chưa được chuyển tới tầng kế tiếp; nhưng `flush()` không bảo đảm bên nhận đã xử lý thông điệp hoặc dữ liệu đã bền vững trên đĩa.

### 5.3. Character buffer có kích thước tính bằng `char`

`BufferedReader` và `BufferedWriter` nhận kích thước buffer tính theo **đơn vị `char`**; con số đó không tương đương số byte UTF-8. Với ký tự đa byte, số byte tạo thành còn do charset và cấu trúc Unicode. Ví dụ:

```java
// Đoạn minh họa, không phải chương trình độc lập.
try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(
        path, java.nio.charset.StandardCharsets.UTF_8);
     java.io.BufferedReader buffered =
             new java.io.BufferedReader(reader, 16 * 1024)) {
    int first = buffered.read();
}
```

Cách ghép này chỉ minh họa cú pháp; `Files.newBufferedReader` vốn trả một `BufferedReader` nên bọc thêm `BufferedReader` là **thừa** trong đa số ứng dụng. Khi cần cấu hình dung lượng bộ đệm ký tự rõ ràng, có thể tạo `InputStreamReader` từ `InputStream` rồi bọc `BufferedReader(reader, size)`; phải xác định rõ ai sở hữu và đóng các resource. Tương tự, `BufferedWriter(Writer, size)` đếm theo ký tự.

## 6. Chọn kích thước buffer như thế nào?

Không có một con số hiệu quả tối ưu cho mọi workload. Kích thước mặc định do các lớp Java quyết định có thể phù hợp cho rất nhiều ứng dụng; việc tăng lên 64 KiB hoặc 256 KiB cần lý do và số đo thực tế.

### 6.1. Một quy trình chọn lựa thực dụng

1. **Xác định loại dữ liệu:** byte hay ký tự, kích thước bản ghi/tệp, số lượng kết nối, yêu cầu độ trễ và cách sử dụng đầu ra.
2. **Chọn baseline:** trước tiên dùng `Files.copy`/`transferTo` hoặc lớp `Buffered*` với mặc định, đo workload đại diện.
3. **Nếu có nhiều thao tác rất nhỏ:** thử buffer rõ ràng, chẳng hạn 4 KiB, 8 KiB, 16 KiB, 64 KiB, 256 KiB; đó là các **mức để thử nghiệm**, không phải quy tắc bắt buộc.
4. **Đo nhiều chỉ số:** thời gian xử lý, throughput (MiB/s), latency (đặc biệt p95/p99), số lần gọi tầng dưới, CPU, GC, heap, bộ nhớ giữ bởi các luồng, tổng byte đọc/ghi và số lỗi.
5. **Đánh giá trong tải song song:** cùng buffer 256 KiB, 10 luồng có thể dùng 2,5 MiB cho một lớp buffer, còn 10.000 luồng có thể dùng khoảng 2,44 GiB *chỉ riêng mảng buffer*, nếu mỗi luồng thật sự giữ một buffer 256 KiB.
6. **Chọn mức nhỏ nhất đáp ứng mục tiêu:** nếu hiệu năng gần tương đương, buffer nhỏ hơn thường dễ quản lý bộ nhớ và độ trễ.

Ví dụ phép tính bộ nhớ thô:

\[
M_{\text{buffer}} \approx C \times B
\]

trong đó `C` là số luồng/phiên đồng thời có buffer riêng và `B` là số byte mỗi buffer; con số này **chưa tính object header, buffer khác, decoder/encoder, queue và bộ nhớ ứng dụng**. Với `BufferedReader`, dung lượng `char[]` thường cần nhiều hơn một byte cho một `char` nên phải tính theo đơn vị cấp phát thực tế.

### 6.2. Vì sao buffer càng lớn không phải lúc nào cũng tốt?

- **Lợi ích giảm dần:** từ 1 byte lên 8 KiB giảm mạnh số lời gọi, nhưng từ 64 KiB lên 1 MiB có thể ít khác biệt khi tầng dưới đã xử lý hiệu quả.
- **Tăng footprint:** buffer thường là riêng cho từng instance; số kết nối/tệp đồng thời lớn làm tổng bộ nhớ tăng.
- **Tác động latency:** dữ liệu chờ flush có thể làm bên nhận thấy thông điệp chậm; chính sách flush và ranh giới bản ghi quan trọng.
- **Chi phí sao chép:** lớp wrapper mới có thể thêm đường copy; với API truyền theo khối đã tối ưu, chèn thêm buffer chưa chắc giúp ích.
- **Chi phối bởi bottleneck khác:** mã hóa UTF-8, parse JSON/CSV, nén, tốc độ ổ đĩa, network, lock hoặc số thao tác metadata có thể quyết định throughput nhiều hơn buffer.
- **Tính chất môi trường:** ổ cứng/SSD, filesystem cache, máy ảo, mạng và cách lập lịch thread khác nhau có thể thay đổi kết quả.

Không tối ưu từ công thức đơn giản nếu bài toán đã chạm giới hạn băng thông/CPU hoặc latency phục vụ request là mục tiêu chính.

## 7. Ghép luồng: đệm đúng tầng

Trong Java, các lớp bọc luồng kết hợp khả năng: mở tệp byte → giải mã ký tự → đệm dòng; hoặc mở tệp byte → đệm byte → ghi.

### 7.1. Ví dụ ghép đúng chức năng

Với byte nhị phân:

```java
// Đoạn minh họa; inputPath và outputPath phải là Path hợp lệ.
try (java.io.InputStream input =
         new java.io.BufferedInputStream(
             java.nio.file.Files.newInputStream(inputPath), 16 * 1024);
     java.io.OutputStream output =
         new java.io.BufferedOutputStream(
             java.nio.file.Files.newOutputStream(outputPath), 16 * 1024)) {
    input.transferTo(output);
}
```

Các đối tượng `Buffered*` ở bên ngoài sở hữu luồng tệp bên trong; đóng chúng sẽ đóng luồng đã được bọc. Ở ví dụ này, buffer có thể hữu ích tùy cách `transferTo` thực hiện truyền và đặc tính nguồn/đích; nên đo trước khi quyết định cần cả hai.

Với văn bản:

```java
// Đoạn minh họa; source và target phải là Path hợp lệ.
try (java.io.BufferedReader reader =
         java.nio.file.Files.newBufferedReader(
             source, java.nio.charset.StandardCharsets.UTF_8);
     java.io.BufferedWriter writer =
         java.nio.file.Files.newBufferedWriter(
             target, java.nio.charset.StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        writer.write(line);
        writer.newLine();
    }
}
```

**Chú ý:** `readLine()` bỏ dấu xuống dòng gốc; `newLine()` thêm dấu xuống dòng phụ thuộc nền tảng. Đây là **biến đổi nội dung theo dòng**, không phải phép sao chép bảo toàn byte hoặc bảo toàn chính xác kiểu kết thúc dòng. Nếu yêu cầu giữ nguyên byte, không dùng quy trình trên.

### 7.2. Tránh buffer trùng lặp vô ích

`Files.newBufferedReader` đã trả một `BufferedReader`; `Files.newBufferedWriter` trả `BufferedWriter`. Bọc thêm một `BufferedReader` nữa chỉ vì nghĩ “hai lớp buffer sẽ nhanh gấp đôi” thường không có căn cứ. Tương tự, với API đã nhận/gửi buffer lớn, thêm `BufferedInputStream` có thể không giảm rõ các lời gọi thực tế.

Tuy nhiên, không có nguyên tắc “cấm ghép hai lớp”: một lớp có thể đệm ký tự, lớp khác đệm byte, lớp khác mã hóa hoặc nén; mỗi lớp có mục đích riêng. Quyết định dựa trên chức năng, cách flush và số đo thực tế.

## 8. Đo hiệu năng đúng cách

Ví dụ `BufferCallCountDemo` đo số lần gọi của các đối tượng Java và khẳng định cơ chế gom lời gọi; **nó không benchmark ổ đĩa**. Khi so sánh thật:

- Dùng cùng dữ liệu đầu vào và đầu ra hợp lệ; xác minh tổng byte và nội dung sau mỗi lần chạy.
- Tách thời gian mở/đóng tệp khỏi thời gian truyền nếu muốn đánh giá riêng throughput, đồng thời có phép đo end-to-end để phản ánh ứng dụng.
- Thử nhiều kích thước file: rất nhỏ (chi phí mở file nổi bật), trung bình, lớn hơn filesystem cache nếu môi trường cho phép.
- Chạy nhiều lượt; khởi động JVM, JIT, GC và cache ảnh hưởng lần chạy đầu. Báo phân bố hoặc median thay vì lấy một số lẻ.
- Giám sát throughput/latency, CPU, heap, GC, bộ nhớ đồng thời, thời gian chờ và số bytes thực. Nếu phân tích số system call, dùng công cụ quan sát của hệ điều hành; đừng suy từ `CountingInput`.
- Tránh để trình benchmark “thắng” nhờ tác vụ khác: ghi vào `ByteArrayOutputStream` và ghi ra SSD là hai workload khác hẳn; đọc từ page cache khác đọc thực từ thiết bị.
- Với vi mô Java, có thể dùng framework benchmark như JMH khi phù hợp; đo chức năng I/O end-to-end vẫn phải theo tải thực và cache thực tế.

**Câu hỏi cần trả lời khi đổi kích thước:** “Mục tiêu là giảm chi phí gọi phương thức, giảm system call, tăng MiB/s, hay giảm độ trễ để người dùng thấy kết quả?” Mỗi mục tiêu có phép đo và lựa chọn khác nhau.

## 9. Những lỗi thường gặp

1. **Đồng nhất lời gọi Java và system call:** `BufferedInputStream.read()` có thể chạy hoàn toàn trong Java khi đã có dữ liệu; luồng dưới/hệ điều hành còn có cơ chế cache riêng.
2. **Hiểu nhầm `read(byte[])` luôn lấp đầy buffer:** cần dùng số byte thực trả về; `-1` mới báo EOF.
3. **Bỏ qua lần đọc cuối xác nhận EOF:** số lần yêu cầu đọc của chương trình có thể nhiều hơn số khối payload một đơn vị hoặc hơn nữa.
4. **Flush sau mỗi byte hoặc từng ký tự:** loại bỏ phần lớn lợi ích gom ghi; chỉ flush theo yêu cầu quan sát dữ liệu và ranh giới giao thức.
5. **Không flush/close khi hoàn tất:** dữ liệu còn trong buffer có thể chưa tới sink; dùng `try-with-resources`.
6. **Tăng buffer đồng loạt cho mọi connection:** tổng footprint lớn, tăng áp lực heap/GC và có thể xấu đi khi tải cao.
7. **Lấy kích thước buffer ký tự làm số byte:** UTF-16 `char` và byte UTF-8 có kích thước khác.
8. **Thêm nhiều buffer giống nhau không có mục đích:** tăng phức tạp và copy mà chưa chắc giảm lời gọi hữu ích.
9. **Tin một phép đo trên file nhỏ:** lần chạy đầu, cache, xử lý file metadata hoặc scheduling có thể che hiệu ứng buffer.
10. **Không xử lý lỗi I/O:** ghi dở và reader/writer thất bại có thể làm chương trình công bố dữ liệu sai. Buffer chỉ tối ưu trao đổi dữ liệu, không cung cấp transaction hoặc rollback.
11. **Coi kích thước buffer là giới hạn kích thước input:** buffer 8 KiB vẫn có thể dùng để đọc tệp 100 GiB; ứng dụng cần quota/timeouts độc lập.
12. **Không xét độ trễ:** throughput tăng nhưng thông điệp bị giữ trong buffer quá lâu có thể không phù hợp với ứng dụng tương tác.

## 10. Bài tập tự kiểm tra

1. Với N = 10.000 byte, buffer B = 1.000 byte và giả định mỗi lần truyền đủ B byte, tính số khối payload. Vì sao vòng đọc đến EOF có thể cần thêm một lần gọi `read`?
2. Từ `BufferCallCountDemo`, thử B = 250 byte (giữ input 1.000 byte). Dự đoán số lần ghi xuống `CountingOutput` khi close, và số lần đọc xuống `CountingInput` bao gồm kiểm tra EOF.
3. Thêm `flush()` sau mỗi `out.write(value)`. Giải thích vì sao số lần gọi xuống tầng dưới sẽ tăng rất mạnh.
4. So sánh hai trường hợp: một tệp văn bản UTF-8 1 GiB được chuyển sang tệp khác bằng buffered reader/writer và cùng tệp đó được gom vào `StringWriter`. Đường nào có nguy cơ dùng heap tỷ lệ với kích thước file?
5. Thiết kế thử nghiệm nhiều kích thước buffer 4/16/64/256 KiB cho tệp lớn. Bạn sẽ kiểm tra tính đúng của output và đo thông số nào?
6. Trong ứng dụng có 20.000 kết nối đồng thời, vì sao buffer riêng 256 KiB/kết nối là quyết định tốn bộ nhớ? Tính phần buffer thô bằng phép nhân và cho biết còn thiếu những thành phần gì.
7. Giải thích khi nào nên flush ở ranh giới thông điệp và vì sao flush không chứng minh thiết bị đã lưu bền vững.
8. Phân biệt buffer `byte[]` với buffer `char[]` trong việc đo dung lượng và charset.

**Ghi nhớ:** Buffer làm thay đổi cách **gom các yêu cầu đọc/ghi** tới tầng dưới. Kích thước buffer lớn hơn thường giảm số lần chuyển khối trong mô hình đơn giản, nhưng lợi ích thực chịu ảnh hưởng của EOF, cách đọc/ghi, layering, cache, số lượng phiên đồng thời, chính sách flush và loại thiết bị. Hãy chọn dựa trên **tính đúng, mục tiêu hiệu năng và kết quả đo**, thay vì mặc định “càng lớn càng nhanh”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buffering-and-stream-composition-006">flush đẩy bộ đệm phần mềm nhưng không đồng nghĩa dữ liệu bền vững</a>

<details>
<summary>Xem chi tiết</summary>

# `flush()` đẩy bộ đệm phần mềm nhưng không đồng nghĩa dữ liệu bền vững

## Mục tiêu bài học

Trong Java I/O, gọi `flush()` đúng lúc giúp dữ liệu còn nằm trong **bộ đệm của một lớp ghi** được chuyển tiếp xuống tầng bên dưới. Nhưng điều đó **không có nghĩa** byte đã đến đĩa vật lý, sẽ sống sót sau mất điện, đã được phía mạng bên kia nhận và xử lý, hay toàn bộ thao tác ghi có tính nguyên tử.

Sau bài này, bạn có thể:

- Phân biệt **ghi vào buffer**, **flush**, **close**, **đọc lại được**, **synchronization/durability** và **atomicity**.
- Giải thích hành vi `flush()` của `BufferedOutputStream`, `BufferedWriter`, `OutputStreamWriter`, `PrintWriter` và các lớp bao.
- Biết khi nào dùng `FileChannel.force(...)` hoặc `FileDescriptor.sync()` để yêu cầu đồng bộ file với thiết bị lưu trữ.
- Thiết kế thứ tự thao tác khi phải vừa flush buffer ở Java vừa yêu cầu đồng bộ file, đồng thời hiểu giới hạn của thao tác rename, network flush và ứng dụng phân tán.
- Tránh gọi `flush()` quá thường xuyên hoặc dùng nó làm bằng chứng dữ liệu bền vững.

## 1. Một lời gọi `write()` có thể mới chỉ ghi vào bộ nhớ Java

Hãy hình dung ứng dụng muốn ghi một dòng vào file:

```text
Ứng dụng Java
    │ write("order=42\n")
    ▼
BufferedWriter / BufferedOutputStream       ← bộ đệm do Java/library quản lý
    │ flush() — chuyển dữ liệu còn đọng xuống tầng kế tiếp
    ▼
OutputStreamWriter / FileOutputStream
    │ ghi byte tới hệ điều hành
    ▼
Bộ đệm hệ điều hành / page cache             ← có thể vẫn ở RAM
    │ thao tác đồng bộ thích hợp (ví dụ force / fsync)
    ▼
Filesystem, driver, thiết bị lưu trữ         ← cơ chế cache/journal riêng
    │
    ▼
Dữ liệu được hệ thống lưu trữ xác nhận
```

Sơ đồ đơn giản hóa đường đi của một file bình thường; những filesystem, thiết bị, môi trường ảo hoặc storage từ xa có thể có các tầng khác. Điểm quan trọng là **mỗi tầng có bộ đệm và cam kết riêng**. `flush()` ở một lớp Java **không tự động đi qua tất cả các tầng** và không được hiểu như `fsync`.

Ví dụ: `BufferedOutputStream` nhận ba byte, cất chúng trong mảng byte nội bộ. Chừng nào buffer chưa đầy hoặc chưa flush, `OutputStream` bên dưới có thể chưa thấy byte nào. Khi gọi `flush()`, ba byte được gửi xuống underlying stream. Nếu underlying stream là `FileOutputStream`, hệ điều hành đã nhận lệnh ghi nhưng có thể chỉ lưu dữ liệu trong page cache. Nếu underlying stream là socket, byte có thể đang ở hàng đợi gửi của hệ điều hành.

Không phải mọi output stream đều sở hữu buffer riêng. Với `OutputStream` cơ sở, `flush()` có triển khai mặc định **không làm gì**; lớp con có thể ghi đè. Vì vậy, việc phương thức tồn tại trên API không đủ để kết luận có hoạt động đồng bộ xuống phần cứng.

## 2. Năm trạng thái phải phân biệt

| Trạng thái | Điều có thể khẳng định | Điều **chưa** thể kết luận |
|---|---|---|
| **`write(...)` vào lớp có buffer** | Lớp ghi đã chấp nhận dữ liệu theo hợp đồng API | Dữ liệu đã tới stream/file/socket bên dưới |
| **`flush()` thành công** | Lớp được gọi đã chuyển dữ liệu đang đệm và yêu cầu các tầng dưới flush theo hợp đồng lớp đó | Dữ liệu chắc chắn nằm trên đĩa bền vững hoặc bên nhận đã xử lý |
| **`close()` thành công** | Tài nguyên đã được đóng; với nhiều wrapper ghi phổ biến, dữ liệu còn đệm được flush trong lúc đóng | Đĩa đã được sync, giao dịch nguyên tử, hay phía mạng đã lưu |
| **Đọc lại file thấy dữ liệu** | Dữ liệu hiện quan sát được qua API đọc trong điều kiện hiện tại | Sau mất điện/OS crash, dữ liệu vẫn tồn tại |
| **`force()`/`sync()` thành công** | Đã yêu cầu cơ chế đồng bộ xuống thiết bị theo hợp đồng API và khả năng hỗ trợ | Một giao dịch nhiều file chắc chắn nguyên tử, thiết bị/đường truyền bất kỳ luôn đáp ứng mọi bảo đảm, hoặc thư mục/rename đã được đồng bộ đầy đủ |

Phân biệt **visibility (khả năng quan sát)** và **durability (độ bền sau sự cố)**. Một tiến trình khác có thể đọc thấy byte trong cache mà dữ liệu đó vẫn chưa được bảo đảm tồn tại sau crash.

Phân biệt thêm **atomicity (nguyên tử)**. Atomicity nói về việc người quan sát thấy toàn bộ trạng thái cũ hoặc mới, không thấy trạng thái nửa chừng; durability nói về trạng thái đã cam kết có còn sau sự cố hay không. `flush()`, `force()` và `ATOMIC_MOVE` giải quyết những vấn đề khác nhau, không thể thay thế nhau.

## 3. `flush()` của các lớp Java hoạt động ra sao?

### 3.1. `OutputStream` và `BufferedOutputStream`

`OutputStream.flush()` là phương thức cho phép lớp con đẩy byte còn đọng; triển khai mặc định không làm gì. `BufferedOutputStream` giữ byte trong vùng đệm và khi flush thì ghi các byte đó tới underlying stream, sau đó gọi `flush()` của stream bên dưới.

Cần để ý thứ tự khi ghép các lớp:

```java
OutputStream raw = Files.newOutputStream(path);
BufferedOutputStream buffered = new BufferedOutputStream(raw);
```

Đây là **đoạn minh họa**, giả sử `path` là `Path` hợp lệ và xử lý `IOException`. Nên dùng try-with-resources trong chương trình thực tế. `buffered.write(...)` có thể chỉ thay đổi mảng byte trong `buffered`. `buffered.flush()` đẩy byte xuống `raw`; **`raw.flush()` không nhất thiết đẩy được dữ liệu vẫn nằm bên trên trong `buffered`**. Gọi flush ở lớp ngoài cùng chịu trách nhiệm ghi để đi qua đúng chuỗi lớp bao.

Một stream bên dưới có thể có buffer khác. `BufferedOutputStream` không tự hứa rằng toàn bộ đường đi sau đó đến tận thiết bị lưu trữ đã ổn định.

### 3.2. `Writer`, `BufferedWriter`, `OutputStreamWriter`

`Writer` là abstraction dành cho ký tự, trong khi `OutputStream` ghi byte. `OutputStreamWriter` encode ký tự thành byte theo charset đã chọn và có thể giữ byte nội bộ trước khi gửi xuống stream. `BufferedWriter` lại có thêm buffer ký tự.

```text
BufferedWriter                (buffer char)
    ↓ flush()
OutputStreamWriter            (encoder ký tự → byte, có thể có buffer byte)
    ↓ flush()
OutputStream                  (tầng đích; có thể là BufferedOutputStream)
    ↓
file / network / memory sink
```

Vì vậy, flush **wrapper ghi bên ngoài** có ý nghĩa khi phải khiến dữ liệu đi qua toàn bộ chuỗi chuyển đổi ký tự/byte. Nếu chỉ flush `OutputStream` ở đáy mà không flush `BufferedWriter` bên trên, các ký tự chưa được đẩy xuống vẫn có thể bị giữ lại.

`BufferedWriter.newLine()` chỉ viết ký tự xuống dòng theo hệ thống; **không tự động flush**. `writer.write("\n")` cũng không tự bảo đảm flush. Muốn người tiêu thụ thấy dữ liệu sớm, gọi `writer.flush()` đúng thời điểm theo giao thức.

### 3.3. `PrintWriter` và auto-flush

`PrintWriter` có tùy chọn `autoFlush` khi tạo đối tượng, nhưng `autoFlush=true` **không có nghĩa** mọi `print(...)`, `write(...)`, hay mọi ký tự `'\n'` đều tự flush. Với `PrintWriter`, hành vi auto-flush được kích hoạt bởi các phương thức `println`, `printf`, `format`, theo hợp đồng lớp. Nếu giao thức cần chắc chắn đẩy byte sau một lệnh `print`, hãy flush tường minh.

Một khác biệt quan trọng: `PrintWriter` không truyền các `IOException` theo cách `BufferedWriter` thường làm, mà lưu trạng thái lỗi; có thể kiểm tra bằng `checkError()` (phương thức này cũng flush nếu stream còn mở). Không bỏ qua lỗi ghi khi độ chính xác dữ liệu quan trọng. `PrintStream` cũng có cơ chế xử lý lỗi riêng; không mặc định việc in thành công tức đã lưu trữ thành công.

## 4. Ví dụ Java 17: nhìn thấy hiệu quả của `flush()` trên bộ đệm phần mềm

Chương trình sau không dùng filesystem, nhờ đó đo chính xác việc byte còn trong `BufferedOutputStream` và đã đi đến đích bên dưới hay chưa.

Lưu **`BufferedFlushDemo.java`**:

```java
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class BufferedFlushDemo {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        try (BufferedOutputStream buffered =
                 new BufferedOutputStream(sink, 8)) {
            buffered.write("ABC".getBytes(StandardCharsets.UTF_8));
            System.out.println("before flush: " + sink.size());

            buffered.flush();
            System.out.println("after flush: " + sink.size());

            buffered.write('D');
            System.out.println("after another write: " + sink.size());
        }

        System.out.println("after close: " +
            sink.toString(StandardCharsets.UTF_8));
    }
}
```

Chạy:

```bash
javac BufferedFlushDemo.java
java BufferedFlushDemo
```

Kết quả:

```text
before flush: 0
after flush: 3
after another write: 3
after close: ABCD
```

Giải thích theo thứ tự:

1. `BufferedOutputStream` được cấp buffer 8 byte; ghi `ABC` (3 byte) chưa làm đầy buffer nên `sink` vẫn có độ dài 0.
2. `buffered.flush()` chuyển `ABC` vào `ByteArrayOutputStream`, làm `sink.size() == 3`.
3. Ghi thêm `D` không bắt buộc đẩy ngay byte mới, vì buffer còn chỗ; `sink` vẫn có 3 byte.
4. Khi kết thúc try-with-resources, `close()` của `BufferedOutputStream` flush byte `D` đang chờ rồi đóng wrapper. `ByteArrayOutputStream` vẫn cho phép đọc nội dung trong bộ nhớ.

**Giới hạn chứng minh:** ví dụ chỉ xác nhận `flush()` đẩy byte từ một buffer Java sang `ByteArrayOutputStream`; không có file, kernel page cache hay đĩa nào ở đây, vì vậy không thể dùng ví dụ này làm phép đo durability.

## 5. Ví dụ: buffer ký tự cũng cần `flush()`

Lưu **`WriterFlushDemo.java`**:

```java
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class WriterFlushDemo {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        try (Writer writer = new BufferedWriter(
                new OutputStreamWriter(sink, StandardCharsets.UTF_8),
                64)) {
            writer.write("café");
            System.out.println("bytes before: " + sink.size());

            writer.flush();
            System.out.println("bytes after: " + sink.size());
            System.out.println("decoded: " +
                sink.toString(StandardCharsets.UTF_8));
        }
    }
}
```

Chạy:

```bash
javac WriterFlushDemo.java
java WriterFlushDemo
```

Kết quả:

```text
bytes before: 0
bytes after: 5
decoded: café
```

`BufferedWriter` ban đầu giữ bốn ký tự UTF-16 trong buffer của nó, nên `sink` chưa thấy gì. Sau khi flush, chuỗi được encode thành **5 byte UTF-8** (ba chữ ASCII và `é` dài hai byte). Gọi flush ở `BufferedWriter` ngoài cùng giúp ký tự đi qua `OutputStreamWriter` xuống sink.

Đây cũng là minh họa rằng một `Writer` còn phải xử lý **charset**; thao tác `flush()` đẩy dữ liệu đang chờ, nhưng không thay thế việc xác định đúng charset.

## 6. File I/O: đã flush ở Java vẫn có thể chỉ nằm trong page cache

Giả sử một ứng dụng ghi file trên hệ thống tệp thông thường. Với `FileOutputStream` hoặc `Files.newOutputStream`, khi byte được chuyển tới lời gọi ghi của hệ điều hành, dữ liệu có thể được lưu vào **page cache** trước khi được đẩy đến thiết bị. `flush()` của wrapper ở Java giải quyết buffer của wrapper; nó không tự bảo đảm OS đã flush tất cả dữ liệu file đến thiết bị.

Ngay cả khi bạn đóng stream và mở lại file để đọc thấy dữ liệu đúng, kết quả đó vẫn có thể đến từ cache hiện tại. **Đọc lại thành công là kiểm tra khả năng quan sát, không phải bài kiểm tra mất điện**.

### 6.1. `FileChannel.force(boolean metaData)`

`java.nio.channels.FileChannel.force(metaData)` yêu cầu cập nhật nội dung file lên thiết bị lưu trữ theo hợp đồng của channel. Với file nằm trên **thiết bị lưu trữ cục bộ**, API Java quy định các thay đổi phù hợp được buộc ghi xuống thiết bị; với thiết bị không cục bộ, mức bảo đảm có thể phụ thuộc hệ thống/provider.

- `force(false)`: yêu cầu force các thay đổi **nội dung file**; có thể vẫn force metadata tùy triển khai.
- `force(true)`: yêu cầu force cả **nội dung** và **metadata** file liên quan.
- Thao tác có thể ném `IOException` nếu việc force thất bại.
- Việc `force` thành công không tự tạo atomic transaction giữa nhiều file, không đồng bộ mọi thư mục liên quan theo một hợp đồng phổ quát và không bảo đảm storage từ xa cư xử giống storage cục bộ.
- Nếu dữ liệu được thay đổi qua cơ chế **khác channel này**, không nên giả định `channel.force(...)` sẽ bao trùm tất cả những thay đổi đó. Dùng cùng một channel cho ví dụ ghi/force là cách biểu đạt trách nhiệm rõ ràng hơn.

Một vấn đề hay gặp là gọi `force` **trước khi** `BufferedOutputStream.flush()`. Khi đó byte vẫn có thể ở mảng Java, chưa từng đi qua `FileChannel`; force không thể buộc xuống đĩa dữ liệu mà channel chưa nhận được.

### 6.2. Ví dụ Java 17: flush wrapper trước rồi force channel

Lưu **`FlushAndForceDemo.java`**. Chương trình tạo tệp tạm, ghi qua buffer, flush đến `FileChannel`, yêu cầu force và đọc lại dữ liệu để kiểm tra byte:

```java
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FlushAndForceDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("io-flush-", ".txt");

        try {
            try (FileChannel channel =
                     FileChannel.open(file, StandardOpenOption.WRITE);
                 OutputStream buffered = new BufferedOutputStream(
                     Channels.newOutputStream(channel), 8192)) {

                buffered.write("DATA\n".getBytes(StandardCharsets.UTF_8));

                buffered.flush();  // 1. Đẩy byte còn ở buffer Java
                channel.force(true); // 2. Yêu cầu force nội dung/metadata
            }

            System.out.print(
                Files.readString(file, StandardCharsets.UTF_8)
            );
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Chạy:

```bash
javac FlushAndForceDemo.java
java FlushAndForceDemo
```

Kết quả trên môi trường I/O bình thường:

```text
DATA
```

Ở đây `Channels.newOutputStream(channel)` chuyển `FileChannel` sang API `OutputStream`, rồi `BufferedOutputStream` cung cấp lớp đệm. Gọi `buffered.flush()` **trước** `channel.force(true)` để đảm bảo byte đã được gửi tới channel. Việc `Files.readString` thành công chỉ xác minh nội dung quan sát được; ví dụ **không thể chứng minh** hành vi sau crash/power loss, không phải benchmark hay bài kiểm tra vật lý thiết bị. Chương trình xóa file tạm để dọn tài nguyên; trong ứng dụng lưu dữ liệu thật, việc xóa và đồng bộ thư mục phải tuân theo quy trình riêng.

Một thay thế trong API `java.io` là **`FileDescriptor.sync()`**, ví dụ với `FileOutputStream.getFD().sync()`, sau khi đã flush wrapper đang chứa dữ liệu. Nó yêu cầu hệ thống đồng bộ các buffer của descriptor với thiết bị lưu trữ. Thứ tự vẫn quan trọng: nếu byte chưa tới file descriptor, sync ở bên dưới không thể đồng bộ những byte còn đọng trong buffer Java.

### 6.3. Các giới hạn của `force` và `sync`

`force`/`sync` mạnh hơn flush ở wrapper, nhưng cần hiểu chính xác phạm vi:

- File nằm trên filesystem mạng có thể có ngữ nghĩa, buffering và bảo đảm khác file local.
- Filesystem, kernel, thiết bị, controller cache và phần cứng/ảo hóa phải phối hợp mới tạo ra độ bền thực tế. Không phát biểu “100% chắc chắn” cho mọi tổ hợp thiết bị.
- `force` một file không tự bảo đảm **mọi thay đổi tên file hoặc directory entry** đã được đồng bộ. Việc tạo file, đổi tên và thay thế file liên quan tới metadata thư mục; thao tác sync directory không được Java NIO.2 cung cấp theo một giao diện portable áp dụng mọi hệ điều hành.
- `MappedByteBuffer` khi sửa vùng memory-mapped có cơ chế `force()` riêng; đừng giả định flush wrapper hoặc force trên một channel bất kỳ thay thế được thao tác phù hợp cho mapping.
- Một chuỗi thao tác “ghi A, ghi B” vẫn có thể dở dang sau crash, kể cả khi mỗi bước yêu cầu force riêng. **Durability không tự bằng atomicity**.

Khi nghiệp vụ đòi hỏi cam kết giao dịch, phục hồi sau crash, ghi nhiều bản ghi nhất quán hoặc thứ tự commit chặt chẽ, hãy dùng định dạng log/transaction protocol được thiết kế đúng hoặc cơ sở dữ liệu có bảo đảm đã được kiểm chứng trên hạ tầng đích.

## 7. `close()` khác `flush()`

**`flush()`** nhằm đẩy dữ liệu đang nằm trong buffer của các lớp ghi nhưng **giữ stream/writer mở** để viết tiếp. **`close()`** kết thúc vòng đời, giải phóng tài nguyên; với `BufferedOutputStream`, `BufferedWriter` và nhiều wrapper ghi khác, đóng cũng flush dữ liệu còn chờ.

```java
try (var writer =
         Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
    writer.write("first line");
    writer.newLine();
    writer.flush(); // Chỉ cần nếu muốn đẩy trước khi thoát scope
    writer.write("second line");
}
// close được gọi tự động khi rời try-with-resources
```

Đây là **đoạn minh họa**, biến `path` là `Path`; có import `Files`, `StandardCharsets`; phương thức xử lý `IOException`. Nếu chỉ cần đóng sau một lần ghi, thường **không cần flush ngay trước close** ở các lớp buffered writer chuẩn. Ngược lại, nếu muốn dữ liệu đi tiếp trước khi kết thúc scope, flush tường minh là hợp lý.

Lưu ý:

- `close()` có thể ném `IOException` trong lúc flush/đóng; dùng try-with-resources để không quên đóng và để Java xử lý suppressed exceptions hợp lý.
- Việc wrapper đóng có đóng tài nguyên underlying hay không phải theo **hợp đồng của wrapper cụ thể**. Với các wrapper chuẩn như `BufferedOutputStream`, thường đóng cả underlying stream. Không chia sẻ bừa cùng underlying stream cho nhiều owner có quyền close.
- Không thể dùng một `Writer` đã đóng để ghi tiếp; `flush()` không thay thế `close()` để giải phóng file descriptor.
- `Files.writeString(...)` kết thúc thao tác ghi không đồng nghĩa file đã được force xuống đĩa.

## 8. Network: `flush()` không phải ACK của ứng dụng bên nhận

Sơ đồ cho socket có buffer:

```text
Ứng dụng gửi
    ↓ buffered.write(message)
Bộ đệm Java
    ↓ buffered.flush()
SocketOutputStream / kernel send buffer
    ↓ TCP/network
Kernel nhận ở máy bên kia
    ↓ ứng dụng bên kia read()
Xử lý nghiệp vụ, ghi database, commit
    ↓ ứng dụng bên kia gửi ACK nghiệp vụ
Ứng dụng gửi nhận ACK và xác minh
```

Ở phía gửi, `buffered.flush()` có thể giúp byte rời khỏi Java buffer để được chuyển cho hệ thống mạng. **Nó không chứng minh bên kia đã `read()`, decode đúng giao thức, xử lý thành công, hay commit dữ liệu bền vững.** Ngay cả TCP ACK (nếu quan sát được ở tầng giao thức) chủ yếu xác nhận byte đến TCP stack phía nhận, không phải xác nhận thao tác nghiệp vụ đã hoàn thành.

Nếu một client gửi “thanh toán thành công” hoặc “lưu lệnh giao dịch”, để biết server thực sự xử lý thành công cần **phản hồi ở tầng ứng dụng** với định danh yêu cầu, trạng thái commit, idempotency/khả năng retry. Trên TCP, ranh giới `write()` và `read()` không tương ứng một message; cần framing (độ dài, delimiter với escaping đúng, hoặc protocol có cấu trúc).

Một số wrapper có buffer, một số socket stream không giữ buffer Java riêng. Vì vậy, `flush()` không phải lệnh “gửi gấp” phổ quát tới mọi mạng; nó phụ thuộc lớp nào đang giữ dữ liệu. Với giao thức tương tác, cân bằng batch/flush, kích thước message, độ trễ, backpressure và các tùy chọn socket thay vì flush sau mỗi byte.

## 9. `flush()` không bảo đảm thay thế file nguyên tử

Hãy xét cách cập nhật file cấu hình:

```text
Mở file cũ và truncate
→ Ghi nửa nội dung mới
→ Mất điện / tiến trình bị kill
→ File có thể rỗng, dang dở hoặc thiếu nhất quán
```

Gọi `flush()` sau mỗi đoạn **không giải quyết** việc một độc giả có thể thấy file bị ghi dở hoặc crash xảy ra giữa các đoạn. Một quy trình cập nhật file có yêu cầu an toàn hơn thường cần các bước thiết kế riêng:

1. Tạo **file tạm trong cùng thư mục/filesystem đích** và ghi toàn bộ nội dung mới vào file tạm; tránh đè trực tiếp nội dung đang phục vụ người đọc.
2. Flush tất cả Java buffer liên quan rồi force file tạm theo yêu cầu durability; xử lý `IOException`.
3. Sau khi nội dung tạm sẵn sàng, dùng `Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, ...)` **khi filesystem/provider hỗ trợ**; có thể nhận `AtomicMoveNotSupportedException`. Ngữ nghĩa thay thế target đã tồn tại khi yêu cầu atomic move là provider-specific, không nên suy diễn `REPLACE_EXISTING` luôn có cùng hành vi trên mọi nền tảng.
4. Nếu nghiệp vụ yêu cầu crash durability của tên/đường dẫn, cần xác minh cơ chế đồng bộ **directory entry** và recovery trên OS/filesystem cụ thể. Java không cung cấp một quy trình portable hoàn chỉnh có cam kết chung cho tất cả filesystem.
5. Xác định chính sách fallback khi atomic rename không hỗ trợ; không âm thầm fallback sang thao tác không nguyên tử nếu yêu cầu bắt buộc atomicity.

Đây là **quy trình khái niệm**, không phải đoạn mã độc lập hay lời hứa rằng mọi filesystem/thiết bị đều cung cấp transaction. Nếu cập nhật nhiều file phải đồng bộ trạng thái cùng nhau, một lần rename file đơn lẻ vẫn không đủ; cân nhắc journal, manifest có version/commit marker hoặc database.

## 10. Chi phí và thời điểm nên gọi `flush()`

Flush quá ít có thể làm dữ liệu bị trễ trong buffer; flush quá thường xuyên có thể mất lợi ích batching, tăng lời gọi hệ thống hoặc tăng tương tác mạng. `force()`/sync thường đắt hơn vì yêu cầu đồng bộ sâu hơn.

| Bài toán | Khi nên flush | Có cần sync/force hoặc giao thức khác? |
|---|---|---|
| Ghi một file nhỏ rồi đóng | Thường close của wrapper là đủ để đẩy buffer Java | Nếu cần tồn tại sau crash, xem xét file force và quy trình commit |
| Ghi file lớn dạng streaming | Flush tại ranh giới thực sự cần quan sát, hoặc để buffer đầy tự xử lý | Tùy yêu cầu durability; không force mỗi byte |
| CLI/REPL in prompt | Flush sau prompt nếu không có newline hoặc stdout đang buffer | Không liên quan file durability |
| Protocol yêu cầu client/server trao đổi lệnh và phản hồi tức thì | Flush ở ranh giới message sau khi đã encode/framing đầy đủ | Cần application ACK để biết bên nhận xử lý |
| Ghi audit/trading/payment log | Flush Java buffer trước khi đánh dấu bản ghi đã xử lý | Thường cần cơ chế commit/durability xác định; cân nhắc database/log chuyên dụng |
| Viết `ByteArrayOutputStream` trực tiếp | Không có buffer ngoài nên flush thường không mang thêm ý nghĩa | Không có đĩa để sync |
| Dùng `BufferedOutputStream` bọc `ByteArrayOutputStream` | Flush để sink có byte còn đọng tại lớp bao | Không liên quan durability |
| Cập nhật file cấu hình không được phép dang dở | Flush file tạm và force theo yêu cầu | Cần atomic replace/recovery phù hợp filesystem |

Không nên biến “gọi `flush()` ở cuối mỗi `write()`” thành quy tắc máy móc. Hãy xác định **thời điểm người khác cần nhìn thấy dữ liệu** và **điểm nào được coi là commit bền vững**; hai nhu cầu này khác nhau.

## 11. Câu hỏi thường gặp

### `flush()` có ghi dữ liệu từ RAM xuống ổ cứng không?

Nó giúp đẩy dữ liệu đang chờ ở tầng buffer cụ thể (ví dụ `BufferedOutputStream`, `BufferedWriter`) xuống tầng tiếp theo. Tầng tiếp theo có thể là bộ nhớ, socket, file stream hoặc buffer khác. Nếu muốn yêu cầu hệ điều hành đồng bộ file lên thiết bị, dùng API như `FileChannel.force()` hay `FileDescriptor.sync()` đúng cách, trong phạm vi bảo đảm của hệ thống tệp.

### Gọi `flush()` rồi `Files.readString` đọc đúng có chứng minh bền vững không?

Không. Dữ liệu có thể đã vào page cache nên lệnh đọc thấy phiên bản mới; sau mất điện vẫn có thể mất nếu chưa đồng bộ. Readback là phép kiểm thử logic dữ liệu **ở trạng thái hiện tại**, không phải crash/power-loss test.

### Nếu dùng try-with-resources rồi thì có cần `flush()` trước `close()` không?

Với `BufferedOutputStream` và `BufferedWriter` chuẩn, thường không cần nếu mục tiêu chỉ là đẩy dữ liệu khi kết thúc scope, vì `close()` sẽ xử lý buffer. Có thể cần flush **sớm hơn**, trong khi đối tượng còn mở, nếu bên tiêu thụ cần nhận dữ liệu ngay. Không đồng nhất đóng stream với `fsync`.

### `FileChannel.force(true)` có làm file update trở thành atomic transaction không?

Không. Nó yêu cầu đồng bộ dữ liệu và metadata file theo hợp đồng; không tự bảo đảm chuyển đổi từ trạng thái cũ sang mới một cách nguyên tử, hoặc đồng bộ thư mục/tập hợp file. Atomicity cần cơ chế riêng.

### Gọi `flush()` dưới `BufferedWriter` có đủ không?

Không nhất thiết. Nếu ký tự còn nằm trong `BufferedWriter`, flush trực tiếp một `OutputStream` bên dưới không đẩy được ký tự chưa được `BufferedWriter` chuyển xuống. Flush lớp ghi ngoài cùng mà ứng dụng đã viết vào.

### `flush()` trên socket có báo server đã nhận yêu cầu thành công không?

Không. Nó có thể đẩy byte tới tầng socket/hệ điều hành ở phía gửi; cần protocol và phản hồi ứng dụng để xác nhận server đã xử lý/commit.

### `PrintWriter(autoFlush=true)` có tự flush mỗi lần `print()` không?

Không. Với `PrintWriter`, auto-flush gắn với `println`, `printf` và `format` (không phải mọi `print`/`write`). Nếu cần đẩy sau `print`, gọi `flush()` tường minh; để phát hiện lỗi, dùng `checkError()` hoặc lựa chọn Writer có cơ chế exception rõ ràng.

## 12. Tự kiểm tra

1. **Một `BufferedOutputStream` chứa 20 byte chưa flush. Ứng dụng gọi `FileChannel.force(true)` lên channel bên dưới. 20 byte có chắc đã force không?**  
   **Không.** Nếu còn nằm trong buffer Java, chúng chưa đến channel. Cần flush wrapper trước, rồi force đúng channel.
2. **Một ứng dụng vừa flush file, mở lại đọc được nội dung. Điều gì đã được xác minh?**  
   Khả năng quan sát nội dung trong trạng thái hiện tại; chưa chứng minh dữ liệu sống sót sau crash.
3. **Khi nào `close()` khác `flush()`?**  
   Flush đẩy buffer để tiếp tục dùng stream; close kết thúc vòng đời và giải phóng tài nguyên. Với nhiều buffered writer/stream chuẩn, close cũng flush buffer.
4. **Đâu là khác biệt giữa `FileChannel.force(false)` và `force(true)`?**  
   `false` yêu cầu force nội dung; `true` yêu cầu force nội dung và metadata file. Hành vi chi tiết phụ thuộc provider và hạ tầng.
5. **Sau khi `socketOutputStream.flush()`, có thể trả “thanh toán thành công” chưa?**  
   Chưa. Phải chờ phản hồi nghiệp vụ xác nhận server xử lý và commit thành công.
6. **File cần cập nhật nguyên tử có đủ bằng `flush()` và `force()` không?**  
   Chưa. Cần cơ chế xuất bản trạng thái mới nguyên tử, chẳng hạn file tạm và atomic move khi được hỗ trợ, cùng thiết kế đồng bộ metadata/recovery theo môi trường.

**Ghi nhớ:** `flush()` xử lý **bộ đệm phần mềm trong đường ghi**; `force()`/`sync()` xử lý yêu cầu **đồng bộ file xuống lưu trữ** ở mức được hệ thống hỗ trợ; atomic rename/transaction giải quyết **tính nguyên tử**, còn application ACK xác nhận **xử lý phía nhận**. Bốn nhu cầu này phải được thiết kế và kiểm chứng riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buffering-and-stream-composition-007">close lớp ngoài cùng và việc đóng tài nguyên bên dưới</a>

<details>
<summary>Xem chi tiết</summary>

# `close()` lớp ngoài cùng và việc đóng tài nguyên bên dưới trong Java I/O

Trong Java I/O, các lớp thường được **ghép thành chuỗi (wrapper/decorator)** để bổ sung chức năng. Ví dụ, một `BufferedReader` bổ sung đọc có bộ đệm cho `InputStreamReader`, còn `InputStreamReader` giải mã byte lấy từ `FileInputStream` thành ký tự:

```text
Mã ứng dụng
    |
    v
BufferedReader                 <- lớp ngoài cùng, ứng dụng gọi read()/close()
    |
    v
InputStreamReader              <- giải mã byte thành ký tự
    |
    v
FileInputStream                <- nắm giữ tài nguyên file/descriptor
    |
    v
Tệp trên hệ điều hành
```

**Quy tắc thực hành:** Với các wrapper I/O chuẩn đóng tầng bên dưới theo hợp đồng của chúng, hãy **đóng lớp ngoài cùng mà ứng dụng sở hữu**. Lệnh `close()` được chuyển xuống các tầng được bọc, giúp giải phóng tài nguyên gốc; đối với luồng đầu ra, việc đóng còn hoàn tất/đẩy dữ liệu đang nằm trong bộ đệm. Tuy nhiên, **không được mặc định mọi wrapper tự viết hoặc mọi API đều sở hữu tài nguyên được truyền vào**. Phải xét hợp đồng ownership và hành vi `close()` của lớp cụ thể.

Bài này phân biệt rõ: cơ chế đóng lan truyền, trình tự đóng, `flush()` so với `close()`, ranh giới sở hữu tài nguyên, xử lý lỗi khi đóng và cách kiểm chứng bằng Java 17+.

## 1. Vì sao các luồng được bọc nhiều tầng?

Các wrapper chia trách nhiệm, thay vì nhét mọi chức năng vào một lớp:

| Tầng | Vai trò | Có thể sở hữu / bọc |
| --- | --- | --- |
| `FileInputStream`, `FileOutputStream` | Đọc/ghi byte với tệp | Handle/descriptor của tệp |
| `InputStreamReader`, `OutputStreamWriter` | Chuyển giữa byte và ký tự theo `Charset` | `InputStream` / `OutputStream` |
| `BufferedInputStream`, `BufferedOutputStream` | Giảm các lần I/O tầng dưới nhờ bộ đệm byte | Luồng byte được bọc |
| `BufferedReader`, `BufferedWriter` | Bộ đệm ký tự; `BufferedReader` hỗ trợ `readLine()` | `Reader` / `Writer` |
| `DataInputStream`, `DataOutputStream` | Đọc/ghi các kiểu nguyên thủy và định dạng nhị phân | Luồng byte được bọc |
| `GZIPInputStream`, `GZIPOutputStream` | Giải nén/nén dữ liệu GZIP | Luồng byte được bọc |

Ví dụ mở file ở ba tầng:

```java
InputStream raw = Files.newInputStream(path);
Reader decoder = new InputStreamReader(raw, StandardCharsets.UTF_8);
BufferedReader reader = new BufferedReader(decoder);
```

Đây là **mã minh họa**: `path`, các import và cách xử lý exception chưa được trình bày thành chương trình độc lập. Một lần `reader.readLine()` có thể đọc qua cả ba tầng. Việc đóng lớp ngoài cùng `reader.close()` cũng đi theo chuỗi đóng các tầng **đối với các lớp chuẩn này**. Để tránh bỏ sót khi có exception, hãy dùng `try-with-resources` ngay khi tạo wrapper.

## 2. `close()` của lớp ngoài cùng đóng những gì?

Với nhiều wrapper chuẩn của `java.io`, phương thức `close()` **ủy quyền (delegate)** đến luồng/reader/writer được bọc. Vì vậy:

```text
reader.close()
    -> BufferedReader đóng Reader bên dưới
        -> InputStreamReader đóng InputStream bên dưới
            -> FileInputStream giải phóng file descriptor
```

Tương tự, luồng ghi có thể tạo chuỗi:

```text
BufferedWriter.close()
    -> đẩy ký tự còn trong bộ đệm
    -> OutputStreamWriter.close()
        -> hoàn tất trạng thái bộ mã hóa và đóng OutputStream
            -> FileOutputStream.close()
                -> giải phóng tài nguyên file
```

**Lưu ý về thuật ngữ:** Trong chuỗi trên, gọi `close()` ở wrapper ngoài cùng và đi xuống các tầng là hành vi đóng **lan truyền theo quan hệ bọc**, không phải cơ chế đệ quy đặc biệt của JVM. Mỗi lớp thực hiện logic `close()` của chính nó theo hợp đồng. Nếu sử dụng wrapper tự viết, bạn phải kiểm tra xem implementation có chuyển tiếp `close()` hay không.

### Ví dụ 1 — quan sát trực tiếp quá trình đóng tầng dưới

Ví dụ sau dùng `FilterInputStream` để theo dõi thời điểm nguồn bên dưới được yêu cầu đóng. Nguồn dữ liệu ở đây là `ByteArrayInputStream` để có kết quả lặp lại chính xác; lớp `TrackingInputStream` quan sát việc gọi `close()`, chứ không dựa vào việc tệp có bị khóa hay không.

```java
// CloseChainDemo.java
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class CloseChainDemo {
    static final class TrackingInputStream extends FilterInputStream {
        boolean closed;

        TrackingInputStream(InputStream source) {
            super(source);
        }

        @Override
        public void close() throws IOException {
            super.close();
            closed = true;
            System.out.println("closed: underlying input");
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] sourceBytes = "Xin chào".getBytes(StandardCharsets.UTF_8);
        TrackingInputStream source = new TrackingInputStream(
                new ByteArrayInputStream(sourceBytes));

        try (BufferedReader outer = new BufferedReader(
                new InputStreamReader(source, StandardCharsets.UTF_8))) {
            System.out.println("read: " + outer.readLine());
        }

        System.out.println("underlying.closed=" + source.closed);
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 CloseChainDemo.java
java CloseChainDemo
```

Kết quả:

```text
read: Xin chào
closed: underlying input
underlying.closed=true
```

Vì `try-with-resources` quản lý **chính `BufferedReader` ngoài cùng**, khi thoát khối `try`, Java gọi `outer.close()`. `BufferedReader` đóng `InputStreamReader`, rồi `InputStreamReader` đóng `TrackingInputStream`. Dòng `closed: underlying input` xác nhận yêu cầu đóng đã tới tầng dưới.

`ByteArrayInputStream.close()` vốn không cần giải phóng tài nguyên hệ điều hành và không cấm đọc tiếp; ở ví dụ này cờ `closed` đo **việc gọi close**, không khẳng định mọi loại stream trở nên không đọc được sau khi đóng. Với `FileInputStream`, hành vi đóng còn giải phóng handle file.

## 3. `try-with-resources`: cách đóng wrapper đúng và an toàn

Cách sử dụng nên ưu tiên cho một file do chương trình mở:

```java
Path path = Path.of("data.txt");
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
                Files.newInputStream(path), StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}
```

Đây là **đoạn mã minh họa**; cần các import tương ứng và một tệp `data.txt` tồn tại. Nguyên tắc quan trọng: một resource là `BufferedReader` ngoài cùng đã đủ để đóng chuỗi chuẩn của ví dụ. Không cần gọi thủ công `reader.close()` trong `finally`.

`try-with-resources` bảo đảm lệnh `close()` xảy ra khi rời khối, kể cả khi:

- hoàn thành bình thường;
- gặp `return` hoặc `break`;
- code bên trong ném exception.

Dùng một tầng ngoài cùng có ý nghĩa vì chính nó biết việc hoàn tất dữ liệu đang giữ và cách đóng tầng dưới. Nếu chỉ đóng `FileOutputStream` thô trước khi bộ đệm wrapper được đẩy xuống, dữ liệu đang nằm ở wrapper có thể chưa được ghi và những lệnh ghi tiếp theo sẽ lỗi.

**Không bắt buộc khai báo tất cả tầng là tài nguyên `try-with-resources`.** Ví dụ này thường không cần thiết:

```java
try (InputStream raw = Files.newInputStream(path);
     BufferedInputStream buffered = new BufferedInputStream(raw)) {
    // ...
}
```

Khi kết thúc khối, Java đóng `buffered` trước, rồi đóng `raw` thêm lần nữa vì cả hai đều có khai báo resource. Những lớp chuẩn thường chịu được việc đóng lặp, nhưng không phải mọi resource tùy biến đều có hợp đồng `close()` idempotent. Nếu đã chọn wrapper chuẩn ngoài cùng làm owner, thường chỉ khai báo wrapper này.

### Các resource *độc lập* được đóng theo thứ tự ngược

Khác với chuỗi wrapper, khi khai báo nhiều tài nguyên thật sự **độc lập** trong một `try`, Java tự đóng theo thứ tự từ phải sang trái (ngược thứ tự khai báo). Ví dụ chạy độc lập:

```java
// CloseOrderDemo.java
public class CloseOrderDemo {
    static final class NamedResource implements AutoCloseable {
        private final String name;

        NamedResource(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        try (NamedResource first = new NamedResource("A");
             NamedResource second = new NamedResource("B")) {
            System.out.println("work");
        }
    }
}
```

```bash
javac CloseOrderDemo.java
java CloseOrderDemo
```

Kết quả:

```text
open A
open B
work
close B
close A
```

Đây là **thứ tự đóng những biến resource khai báo trong `try`**; còn việc đóng lan truyền của chuỗi wrapper xảy ra bên trong phương thức `close()` của từng wrapper.

## 4. `flush()` khác `close()`: phần dữ liệu còn trong bộ đệm

Với luồng đầu ra, cần phân biệt hai thao tác:

- **`flush()`** yêu cầu đẩy phần dữ liệu đang đệm của wrapper xuống tầng bên dưới. Luồng **vẫn mở** và còn được phép ghi tiếp.
- **`close()`** hoàn tất việc sử dụng resource. Wrapper đầu ra thông dụng sẽ thực hiện công việc cần thiết để đẩy dữ liệu còn đệm rồi đóng tầng được bọc.

`flush()` không đồng nghĩa với dữ liệu đã được ghi **bền vững trên thiết bị lưu trữ**; dữ liệu có thể vẫn còn trong cache của hệ điều hành hoặc phần cứng. Nếu cần yêu cầu độ bền với tệp, tìm hiểu `FileChannel.force(...)` và những bảo đảm cụ thể của hệ thống tệp.

### Ví dụ 2 — bộ đệm chưa ghi ra nguồn bên dưới cho đến khi flush/close

```java
// BufferedCloseDemo.java
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class BufferedCloseDemo {
    static final class TrackingOutputStream extends FilterOutputStream {
        boolean closed;

        TrackingOutputStream(OutputStream destination) {
            super(destination);
        }

        @Override
        public void close() throws IOException {
            super.close();
            closed = true;
            System.out.println("closed: underlying output");
        }
    }

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        TrackingOutputStream underlying = new TrackingOutputStream(sink);

        try (BufferedOutputStream outer =
                     new BufferedOutputStream(underlying)) {
            outer.write("ABC".getBytes(StandardCharsets.UTF_8));
            System.out.println("before close bytes=" + sink.size());
        }

        System.out.println("after close bytes=" + sink.size());
        System.out.println("underlying.closed=" + underlying.closed);
        System.out.println("text=" + sink.toString(StandardCharsets.UTF_8));
    }
}
```

```bash
javac BufferedCloseDemo.java
java BufferedCloseDemo
```

Kết quả:

```text
before close bytes=0
closed: underlying output
after close bytes=3
underlying.closed=true
text=ABC
```

Ba byte `ABC` đủ nhỏ nên nằm trong bộ đệm mặc định của `BufferedOutputStream`. Việc đóng `outer` khiến dữ liệu được chuyển xuống `TrackingOutputStream`/`ByteArrayOutputStream` và yêu cầu đóng cả tầng dưới. Vì `ByteArrayOutputStream` hoạt động trong RAM nên `close()` của chính nó không làm mất dữ liệu; trạng thái `underlying.closed` do lớp theo dõi ghi nhận.

Nếu thay bên trong `try` bằng:

```java
outer.write("ABC".getBytes(StandardCharsets.UTF_8));
outer.flush();
System.out.println(sink.size()); // 3
```

đây là **đoạn minh họa thay thế**, `flush()` làm dữ liệu xuất hiện trong `sink` ngay trong khối, nhưng không đóng luồng. Khi muốn ghi tiếp sau `flush()` thì vẫn làm được; sau `close()` không nên sử dụng tiếp wrapper.

**Không đóng tầng dưới trước tầng ngoài:** Ví dụ nếu gọi `underlying.close()` trước rồi mới `outer.close()`, các byte vẫn ở `BufferedOutputStream` có thể phải đẩy qua một tầng đã đóng; đối với nguồn thực như tệp/socket có thể gây mất dữ liệu hoặc lỗi. Hãy để wrapper đầu ra ngoài cùng thực hiện đóng theo thứ tự hợp lệ.

## 5. Những wrapper có công việc hoàn tất khi `close()`

Một số wrapper không chỉ đệm mà còn **định dạng dữ liệu**:

- `GZIPOutputStream` phải hoàn tất cấu trúc nén; `close()` hoàn tất và đóng luồng bên dưới. Nếu muốn hoàn tất GZIP nhưng **giữ luồng bên dưới mở** trong thiết kế có chủ đích, xem `finish()`; `flush()` thông thường không thay cho hoàn tất định dạng.
- `ZipOutputStream` cần ghi các cấu trúc kết thúc ZIP (chẳng hạn central directory) để tệp ZIP hoàn chỉnh. `close()` đóng cả archive và luồng bên dưới; `finish()` hoàn tất archive mà chưa đóng luồng bên dưới.
- `OutputStreamWriter` chuyển ký tự sang byte bằng `CharsetEncoder`. Dữ liệu/ trạng thái bộ mã hóa có thể cần được xử lý khi `flush()` hoặc `close()`, nên không thể cho rằng chỉ ghi vào wrapper là nguồn dưới đã nhận đầy đủ.
- `BufferedWriter` và `BufferedOutputStream` giữ dữ liệu ở RAM để giảm số thao tác ở tầng dưới; đóng đúng wrapper giúp tránh bỏ sót phần còn đệm.

**Phân biệt `finish()` và `close()` khi API có hỗ trợ:** `finish()` hoàn tất định dạng của một luồng đặc thù; `close()` còn giải phóng/đóng đối tượng và thường cả luồng bên dưới. Không phải `OutputStream` nào cũng có `finish()`.

Đừng thay thế `close()` bằng `flush()` ở mọi nơi. Tài nguyên tệp/socket vẫn phải được đóng bởi owner; một định dạng nén có thể chưa hoàn chỉnh nếu chưa chạy logic kết thúc.

## 6. Ai thực sự sở hữu luồng bên dưới?

Điều kiện quan trọng nhất khi quyết định đóng tầng ngoài là **quyền sở hữu tài nguyên (resource ownership)**:

| Tình huống | Ai nên đóng? | Lý do |
| --- | --- | --- |
| Phương thức tự mở file và tạo wrapper trên file ấy | Phương thức, thông qua wrapper ngoài cùng | Đã tạo và sở hữu toàn bộ chuỗi. |
| Hàm nhận `InputStream`/`OutputStream` từ caller và chỉ đọc/ghi một phần | Thường là caller, trừ khi hợp đồng chuyển quyền sở hữu rõ ràng | Hàm không nên làm hỏng nguồn dùng chung. |
| `System.in`, `System.out`, `System.err` | Vòng đời ứng dụng/môi trường quản lý | Đóng wrapper bọc chúng có thể đóng standard stream mà nơi khác còn cần. |
| Một socket có nhiều thành phần dùng chung | Thành phần được chỉ định owner của socket | Đóng stream do socket cung cấp có thể đóng kết nối, ảnh hưởng thành phần khác. |
| Wrapper tùy biến có `close()` override đặc biệt | Theo hợp đồng của wrapper đó | Không được suy rằng `close()` luôn delegate. |

Quy tắc thiết kế API thực tiễn:

- **Tạo ở đâu, quản lý vòng đời ở đó**, trừ khi tài liệu hoặc giao diện chuyển giao ownership.
- Ghi rõ trong Javadoc/hợp đồng hàm rằng `OutputStream`/`Reader` truyền vào sẽ được đóng hay vẫn thuộc caller.
- Nếu hàm chỉ *mượn* stream, nó có thể `flush()` khi cần đẩy dữ liệu, nhưng thường **không `close()`** nó.
- Nếu hàm *nhận chuyển quyền sở hữu*, hãy đảm bảo đóng khi hoàn thành hoặc gặp lỗi, tốt nhất dùng `try-with-resources`.

Ví dụ hàm chỉ mượn `OutputStream`:

```java
// Đoạn minh họa, không tự chạy nếu thiếu import/lớp bao.
static void sendMessage(OutputStream borrowed, String text)
        throws IOException {
    borrowed.write(text.getBytes(StandardCharsets.UTF_8));
    // Không gọi borrowed.close(): caller vẫn sở hữu stream.
}
```

Đặc biệt tránh mẫu `try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out)))` trong hàm chỉ in thử ra console: đóng `writer` sẽ lan xuống `System.out` và có thể làm các lệnh in sau không còn hoạt động. Với `Scanner` bọc `System.in`, `scanner.close()` cũng có thể đóng nguồn nhập chuẩn đang được chia sẻ.

### Khi cần wrapper nhưng không được đóng nguồn của caller

Có ba cách tùy kiến trúc:

1. Caller chủ động tạo wrapper và giữ quyền kiểm soát vòng đời của **cả wrapper lẫn nguồn**; hàm chỉ nhận wrapper để sử dụng.
2. Hàm mượn nguồn nhưng tự tạo bộ đệm tạm; phải `flush()` trước khi trả lại nếu có dữ liệu đầu ra và không đóng wrapper có cơ chế close lan truyền. Cần xác định rõ lỗi và trạng thái đệm còn tồn tại.
3. Dùng một wrapper **close-shield / non-closing wrapper** khi tầng ngoài cần được đóng để hoàn tất nội dung nhưng tầng dưới thuộc caller. Cách này phải được thiết kế và kiểm thử riêng; không phải mặc định của `BufferedOutputStream`.

Ví dụ minh họa về close-shield cho đầu ra (mã nằm trong lớp Java phù hợp, có import `FilterOutputStream`, `OutputStream`, `IOException`):

```java
static final class NonClosingOutputStream extends FilterOutputStream {
    NonClosingOutputStream(OutputStream delegate) {
        super(delegate);
    }

    @Override
    public void close() throws IOException {
        flush(); // Không gọi super.close(), do đó giữ delegate mở.
    }
}
```

Nếu tạo `new BufferedOutputStream(new NonClosingOutputStream(borrowed))`, `close()` của `BufferedOutputStream` sẽ đẩy dữ liệu và đóng wrapper shield; shield chỉ flush, **không gọi `borrowed.close()`**. Đây là ví dụ về chính sách sở hữu, không thay thế việc kiểm tra những wrapper phía trên có cần `close()` để hoàn tất định dạng hoặc những luồng có trạng thái phức tạp.

## 7. Exception khi đọc/ghi và cả khi đóng tài nguyên

`close()` có thể ném `IOException`. Lỗi có thể xuất hiện ở lúc đẩy phần buffer còn lại xuống file, hoàn tất mã hóa/nén, hoặc đóng tài nguyên. Vì vậy exception liên quan đến `close()` **không phải chuyện chỉ xảy ra sau khi công việc đã thành công**.

Với `try-with-resources`:

- Nếu thân khối `try` thành công nhưng `close()` thất bại, exception từ `close()` được truyền ra.
- Nếu thân `try` đã ném exception và `close()` cũng ném, exception của thân được giữ là **primary exception**, còn exception từ `close()` được đính vào mảng **suppressed exceptions**.
- Nếu nhiều resource được khai báo, chúng được đóng theo thứ tự ngược; exception trong quá trình đóng những resource tiếp theo có thể được giữ dưới dạng suppressed.

Ví dụ Java 17+ độc lập:

```java
// SuppressedCloseDemo.java
import java.io.IOException;

public class SuppressedCloseDemo {
    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        try (FailingResource resource = new FailingResource()) {
            throw new IOException("body failed");
        } catch (IOException ex) {
            System.out.println("primary=" + ex.getMessage());
            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println("suppressed=" + suppressed.getMessage());
            }
        }
    }
}
```

```bash
javac SuppressedCloseDemo.java
java SuppressedCloseDemo
```

Kết quả:

```text
primary=body failed
suppressed=close failed
```

Khi điều tra sự cố mất dữ liệu hay file bị hỏng, đừng chỉ đọc `ex.getMessage()` của lỗi đầu tiên. Có thể phải xem `getSuppressed()` để tìm lỗi đóng/flushing tài nguyên. Cũng đừng dùng `finally { stream.close(); }` một cách ngây thơ nếu việc đóng có nguy cơ **che mất exception chính** của thân khối; `try-with-resources` có quy tắc suppression rõ ràng.

Một điểm giới hạn: dù nhiều wrapper chuẩn truyền `close()` xuống bên dưới, **không thể kết luận mọi tài nguyên chắc chắn đã đóng thành công khi `close()` ném lỗi**. Tình trạng cụ thể phụ thuộc implementation, hệ điều hành và lỗi; chính vì vậy cần giữ ngoại lệ và thiết kế khôi phục phù hợp.

## 8. Một số trường hợp khác dễ hiểu nhầm

### `close()` thường khác với EOF

Khi đọc dữ liệu, EOF cho biết nguồn không còn ký tự/byte trong luồng. `close()` là hành động giải phóng tài nguyên do ứng dụng quản lý, có thể xảy ra **trước EOF** nếu chỉ cần đọc một phần. Đọc tiếp sau `close()` không phải cách kiểm tra EOF hợp lệ; nhiều stream sẽ ném `IOException`.

### Không phải mọi đối tượng đóng rồi đều “không thể dùng” như nhau

`ByteArrayInputStream` và `ByteArrayOutputStream` hoạt động trên bộ nhớ, nên việc `close()` của chúng **không có hiệu lực** theo hợp đồng. Một `FileInputStream` đã đóng thì các thao tác đọc tiếp thường ném lỗi. Không suy từ một lớp trong bộ nhớ ra hành vi của tài nguyên hệ điều hành.

### `PrintWriter`/`PrintStream` có khác biệt về báo lỗi

`PrintWriter` thường **không ném `IOException` từ nhiều phương thức ghi**; có thể kiểm tra trạng thái lỗi với `checkError()`. Đóng `PrintWriter` vẫn có thể đóng writer/stream bên dưới, nhưng việc không thấy exception từ một lời gọi ghi không có nghĩa dữ liệu đã được ghi thành công. Với logic cần báo lỗi I/O rõ ràng, cân nhắc `BufferedWriter`/`Writer` và bắt `IOException`.

### Tầng nén hoặc định dạng có thể cần hoàn tất đúng cách

Nếu chỉ đóng `FileOutputStream` bên dưới một `GZIPOutputStream` hoặc `ZipOutputStream` đang mở, cấu trúc đầu ra có thể thiếu phần kết thúc. Luôn để owner đóng wrapper định dạng ngoài cùng hoặc gọi `finish()` đúng hợp đồng rồi đóng tầng do mình sở hữu.

### Không suy diễn cho mọi Java API

Lớp chuẩn `FilterInputStream`, `FilterOutputStream`, `BufferedReader`, `BufferedWriter`, `InputStreamReader`, `OutputStreamWriter` thường có close propagation theo thiết kế. Nhưng một `Reader` tùy biến, bộ đệm do thư viện ngoài quản lý, stream trong servlet, hay API tự động quản lý vòng đời có thể có hợp đồng khác. Khi không chắc, xem tài liệu `close()` của **chính implementation**.

## 9. Cách chọn thiết kế đóng tài nguyên

Khi tạo chuỗi I/O, trả lời bốn câu hỏi trước:

1. **Nguồn gốc tài nguyên:** Ai mở file, socket hoặc kết nối?
2. **Chủ sở hữu vòng đời:** Ai có trách nhiệm đóng?
3. **Wrapper ngoài cùng cần hoàn tất gì:** flush bộ đệm, xử lý charset, `finish()` định dạng nén?
4. **Khi có lỗi:** `try-with-resources` có giữ được exception gốc và lỗi khi đóng không?

Một sơ đồ quyết định:

```text
Tôi tự tạo nguồn và sở hữu nó?
  |
  +-- Có --> Bọc theo nhu cầu, try-with-resources trên lớp ngoài cùng.
  |           Để close() lan xuống qua các wrapper chuẩn.
  |
  +-- Không --> Xác nhận hợp đồng có chuyển ownership không.
                |
                +-- Có --> Quản lý close() theo hợp đồng đã nhận.
                |
                +-- Không --> Không close() nguồn dùng chung.
                              Nếu tạo wrapper, thiết kế flush/shield
                              và bàn giao vòng đời một cách rõ ràng.
```

Với các luồng chuẩn, mẫu ngắn gọn thường đúng nhất cho *nguồn do chính hàm tạo*:

```java
try (BufferedWriter writer = Files.newBufferedWriter(
        path, StandardCharsets.UTF_8)) {
    writer.write("Dữ liệu cần lưu");
}
```

Đoạn minh họa này giả định `path` đã được xác định. `Files.newBufferedWriter` sở hữu cơ chế mở tệp và trả wrapper có bộ đệm. Kết thúc khối sẽ đóng writer, xử lý phần buffer còn lại và giải phóng tài nguyên tệp. Không cần `writer.flush()` ngay trước `close()` chỉ vì muốn chắc chắn; nếu cần quan sát dữ liệu khi luồng **vẫn mở**, đó mới là lý do gọi `flush()`.

## 10. Câu hỏi tự kiểm tra

**1.** `BufferedReader` bọc `InputStreamReader` bọc `FileInputStream`. Nếu chính hàm đã mở file, nên đóng gì?

**Đáp án:** Đóng `BufferedReader` ngoài cùng bằng `try-with-resources`; các wrapper chuẩn sẽ chuyển tiếp việc đóng xuống tầng file.

**2.** Việc đóng `FileOutputStream` bên dưới trước khi đóng `BufferedOutputStream` có an toàn không?

**Đáp án:** Không nên. Buffer phía ngoài có thể còn byte chưa chuyển xuống nguồn. Hãy đóng wrapper ngoài cùng để flush và đóng theo thứ tự.

**3.** `flush()` có thay cho `close()` không?

**Đáp án:** Không. `flush()` chuyển dữ liệu còn đệm xuống dưới và vẫn để stream mở; `close()` kết thúc vòng đời, và wrapper định dạng có thể phải ghi thêm phần kết thúc.

**4.** Một hàm nhận `OutputStream` của caller, dùng xong có tự động đóng không?

**Đáp án:** Chỉ khi hợp đồng ghi rõ caller chuyển ownership. Mặc định thiết kế nên để caller quản lý vòng đời của nguồn đã truyền vào.

**5.** `try (A a=...; B b=...)` đóng tài nguyên theo thứ tự nào?

**Đáp án:** `b` trước, `a` sau. Đây là thứ tự giữa các **resource khai báo**, không phải thay thế close propagation của wrapper.

**6.** Nếu thân `try-with-resources` ném lỗi và `close()` ném lỗi khác, tìm lỗi đóng ở đâu?

**Đáp án:** Thường trong `primaryException.getSuppressed()`. Đừng bỏ qua các suppressed exceptions khi chẩn đoán.

**7.** Đóng wrapper bọc `System.out` có thể ảnh hưởng phần khác của ứng dụng không?

**Đáp án:** Có. Các standard stream là tài nguyên dùng chung; tránh để helper đóng chúng nếu helper không sở hữu.

**8.** `ByteArrayOutputStream.close()` không ngăn đọc lại dữ liệu; vậy `FileOutputStream.close()` cũng thế?

**Đáp án:** Không. `ByteArrayOutputStream` đặc biệt vì không giữ tài nguyên I/O hệ điều hành; không lấy nó làm bằng chứng cho file/socket stream.

**Ghi nhớ:** Khi **sở hữu cả chuỗi wrapper I/O chuẩn**, đóng **wrapper ngoài cùng** là cách để dữ liệu/định dạng hoàn tất và tài nguyên bên dưới được đóng. Khi chỉ **mượn nguồn**, quyền quyết định `close()` thuộc owner; phải ghi rõ hợp đồng và xử lý `flush()`/close-shield nếu cần.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buffering-and-stream-composition-008">Mark, reset, markSupported và readlimit</a>

<details>
<summary>Xem chi tiết</summary>

# Mark, reset, markSupported và readlimit trong Java I/O

> **Bối cảnh:** Java Core I/O → **Bộ đệm và ghép luồng**. Bài này tập trung vào cơ chế **đánh dấu vị trí đọc, đọc thử và quay lại**, cách wrapper bổ sung khả năng này, sự khác nhau giữa giới hạn đọc trước theo **byte** và theo **ký tự**, cùng những giới hạn không được bỏ qua khi viết parser. Các ví dụ độc lập biên dịch được với Java 17; các API cốt lõi được trình bày đã có từ những phiên bản Java trước đó.

## 1. Tại sao phải đánh dấu rồi đọc lại?

Giả sử parser đang nhận một luồng byte, cần nhìn vài byte đầu để đoán định dạng, sau đó cho bộ xử lý chính đọc lại **từ đúng điểm vừa đánh dấu**:

1. Đặt điểm nhớ bằng `mark(readlimit)`.
2. Đọc thử một đoạn với `read(...)`.
3. Quyết định xử lý dựa trên đoạn vừa xem.
4. Nếu cần đọc lại đoạn đó, gọi `reset()` để di chuyển **vị trí đọc của chính stream đó** về điểm đánh dấu.

Đây là kiểu **lookahead**: đọc trước mà chưa coi đoạn dữ liệu đó đã bị tiêu thụ vĩnh viễn. Một số input stream chỉ đọc tuần tự, không có seek hoặc khả năng đọc lại; vì vậy **không phải mọi stream đều hỗ trợ mark/reset**. Bộ đệm có thể giữ những byte đã đọc để phát lại mà không cần quay vị trí của tệp/socket gốc.

Hãy phân biệt ngay ba việc:

- **`mark`** chỉ ghi nhớ vị trí; không trả một đối tượng bookmark, không nhân bản stream, không đọc dữ liệu.
- **`reset`** đưa vị trí về **mark gần nhất còn hợp lệ**, không mặc định về đầu stream hoặc đầu file.
- **`markSupported`** cho biết **implementation của chính stream đang dùng** có hỗ trợ giao thức mark/reset không; kiểu biến `InputStream` hay `Reader` không đủ để kết luận.

## 2. Bốn thành phần của hợp đồng API

### 2.1. `InputStream.mark(int readlimit)`

Đặt vị trí ghi nhớ trong byte-input stream. Tham số `readlimit` là **số byte tối đa được đề nghị bảo toàn để có thể reset** kể từ mark. Ví dụ `mark(8)` có nghĩa code sau đó dự định đọc trước nhiều nhất 8 byte và vẫn cần phát lại chúng.

- Phương thức này có kiểu trả về `void` và **không khai báo `throws IOException`** trong `InputStream`.
- Implementation mặc định của lớp `InputStream` cho `mark(int)` **không làm gì**. Phải kiểm tra subclass/wrapper mới biết mark có hiệu lực.
- Có thể mark nhiều lần; **mark mới thay mark trước**. Không có stack lưu nhiều vị trí để reset ngược qua nhiều mark.
- **`readlimit` không phải số lần được phép gọi `read`** và cũng không phải giới hạn tốc độ đọc. Nó là giới hạn về **khoảng dữ liệu cần có khả năng đọc lại**.
- Khi đã đọc **vượt readlimit**, implementation **không có nghĩa vụ** giữ dữ liệu để reset. Reset có thể thất bại; một số stream vẫn reset được do đặc điểm lưu trữ riêng. Đừng viết code phụ thuộc vào việc mark chắc chắn mất hiệu lực ngay ở byte thứ `readlimit + 1`.
- Đặt readlimit rất lớn có thể khiến wrapper giữ/mở rộng buffer tương ứng, tạo áp lực bộ nhớ; không thể xem đó là cơ chế rewind vô hạn.

### 2.2. `InputStream.reset()`

Đặt vị trí đọc trở về mark gần nhất (nếu còn hợp lệ). Các byte đã đọc kể từ mark có thể được phát lại theo thứ tự cũ. `reset()` khai báo `throws IOException`.

Các trường hợp cần xử lý: stream không hỗ trợ reset, chưa mark, mark bị vô hiệu, stream đã đóng hoặc lỗi I/O khác. Tùy loại stream, gọi `reset` khi chưa mark có thể trở về trạng thái mặc định riêng (như đầu nguồn bộ nhớ), nhưng **không được giả định mọi stream như vậy**.

**Reset không tạo dữ liệu mới** và cũng không đảm bảo nguồn gốc chưa thay đổi nếu có code khác thao tác với cùng tài nguyên bên dưới. Trong một pipeline đọc bình thường, chỉ thao tác thông qua **cùng một wrapper đang mark và reset**.

### 2.3. `InputStream.markSupported()`

Trả `boolean`. Hợp đồng của `InputStream` nói hỗ trợ mark/reset là thuộc tính ổn định của từng instance. Phương thức mặc định trả `false`; những subclass/wrapper phù hợp có thể trả `true`.

~~~java
// Fragment: in là một InputStream đang mở.
if (in.markSupported()) {
    in.mark(16);
    // Chỉ đọc trước trong phạm vi đã thiết kế.
    in.reset();
} else {
    // Chọn buffering, pushback, hoặc cách xử lý khác.
}
~~~

Đây là **fragment minh họa**: `in` do code xung quanh cung cấp. Trong chương trình thật vẫn cần bắt hoặc khai báo `IOException` khi `reset()`, ngay cả khi `markSupported()` là `true`: hỗ trợ không có nghĩa mọi mark đều còn hợp lệ.

### 2.4. `readlimit` là hạn mức gì?

Trong `InputStream.mark(n)`, đơn vị `n` là **byte được đọc kể từ mark**; đối với `Reader.mark(n)`, tham số được gọi là **`readAheadLimit`**, đơn vị là **ký tự theo `Reader` (UTF-16 code unit trong Java)**.

Không được coi hai tham số là số grapheme (ký tự người dùng thấy) hoặc số Unicode code point. Một emoji ngoài BMP có thể chiếm **2 code unit UTF-16**, trong khi khi encode UTF-8 sẽ chiếm **4 byte**. Khi làm parser văn bản, hãy tính read-ahead ở tầng wrapper mà parser thao tác.

**Điểm then chốt:** readlimit là phần giới hạn về độ dài dữ liệu có thể replay, không phải hạn mức **tổng số byte của stream**, không phải giới hạn `available()`, và không tự biến đọc thành non-blocking.

## 3. Những implementation thường gặp hỗ trợ hay không hỗ trợ?

| Stream/Reader | `markSupported()` thường trả | Điều cần biết |
| --- | --- | --- |
| `InputStream` mặc định | `false` | `mark` mặc định không làm gì; `reset` mặc định báo lỗi. |
| `FileInputStream` | `false` | File descriptor có vị trí, nhưng lớp stream này không cung cấp mark/reset; file seek là API khác. |
| `ByteArrayInputStream` | `true` | Nguồn đã nằm trong bộ nhớ; có thể reset về mark ngay cả khi đã đọc vượt tham số readlimit. |
| `BufferedInputStream` | `true` | Buffer có thể lưu lại byte đã đọc; readlimit quyết định mức đọc trước dự định giữ. |
| `FilterInputStream` | Phụ thuộc stream bọc bên trong | Lớp cơ sở chuyển tiếp các thao tác; subclass có thể đổi hành vi. |
| `DataInputStream` | Phụ thuộc stream bọc bên trong | Một số method đọc nhiều byte; mark trên đúng wrapper và giữ giới hạn theo số byte. |
| `PushbackInputStream` | `false` | Có `unread` để đẩy lại byte trong buffer riêng, nhưng không hỗ trợ mark/reset. |
| `InputStreamReader` | `false` | Decoder chuyển byte → ký tự; nếu cần đọc lại ký tự, thường bọc bên ngoài bằng `BufferedReader`. |
| `BufferedReader` | `true` | `mark(int readAheadLimit)` theo **số code unit/char**, có khả năng đọc lại sau reset. |
| `StringReader` và `CharArrayReader` | `true` | Dữ liệu nguồn ký tự ở bộ nhớ, có khả năng reset theo cơ chế riêng. |
| `PushbackReader` | `false` | Dùng `unread` thay cho mark/reset. |

Bảng thể hiện hành vi API chuẩn của những lớp phổ biến, không thay thế việc đọc tài liệu subclass cụ thể hoặc gọi `markSupported()` khi đầu vào là một stream bất kỳ do bên thứ ba cung cấp.

### 3.1. Wrapper thay đổi điều gì?

~~~text
FileInputStream [markSupported = false]
          |
          v
BufferedInputStream [markSupported = true]
          |
          v
Parser nhận BufferedInputStream, không đọc trực tiếp FileInputStream
~~~

`BufferedInputStream` giữ các byte đã đọc trong buffer khi cần. Vì vậy, dù `FileInputStream` không hỗ trợ mark, wrapper có thể tự hỗ trợ mà **không đòi nguồn gốc phải có khả năng quay lại**.

Tránh giữ cả hai biến rồi đọc xen kẽ:

~~~java
// Fragment sai về thiết kế: source và buffered cùng chia một nguồn dữ liệu.
InputStream source = /* nguồn từ caller */;
BufferedInputStream buffered = new BufferedInputStream(source);
buffered.mark(8);
int peek = buffered.read();
int next = source.read(); // Không đọc trực tiếp source sau khi đã bọc!
// buffered.reset() không thể khôi phục một cách đúng đắn hai con trỏ đọc.
~~~

Bộ đệm có thể đã kéo thêm byte từ `source` trước thời điểm caller đọc trực tiếp. Quy tắc đúng: tạo wrapper một lần và chuyển wrapper đó cho toàn bộ code tiêu thụ nguồn. `close()` wrapper thường đóng cả nguồn bên trong; quản lý ownership để tránh đóng sớm hoặc đóng lặp do cấu trúc vòng đời rối.

## 4. Ví dụ chạy được: đọc thử rồi phát lại byte

Tạo tệp **`MarkByteDemo.java`**:

~~~java
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MarkByteDemo {
    public static void main(String[] args) throws IOException {
        byte[] data = "ABCDE".getBytes(StandardCharsets.US_ASCII);

        // bufferSize = 2 để thấy mark/reset hoạt động dù buffer nhỏ.
        try (InputStream in = new BufferedInputStream(
                new ByteArrayInputStream(data), 2)) {

            System.out.println("supported=" + in.markSupported());
            System.out.println("first=" + (char) in.read()); // A

            in.mark(3); // Đánh dấu trước B, dự định nhìn tối đa 3 byte.
            System.out.println("peek=" + (char) in.read()
                    + "" + (char) in.read());             // BC

            in.reset(); // Quay về trước B.
            System.out.println("replay=" + (char) in.read()
                    + "" + (char) in.read());             // BC
        }
    }
}
~~~

Chạy bằng Java 17 (mã cũng dùng API Java cũ):

~~~bash
javac MarkByteDemo.java
java MarkByteDemo
~~~

Stdout:

~~~text
supported=true
first=A
peek=BC
replay=BC
~~~

**Giải thích từng bước:** Con trỏ ban đầu trước `A`. Sau `read()` đầu, con trỏ đứng trước `B`. `mark(3)` lưu vị trí này; hai lần đọc cho `B` và `C`. `reset()` đưa con trỏ logic về `B`, nên hai lần đọc tiếp phát lại `BC`. Không có byte mới được thêm vào nguồn. Kích thước buffer khởi tạo **2** không ngăn việc mark với `readlimit=3`: wrapper có thể giữ/mở rộng phần dữ liệu thích hợp khi cần.

Biểu thức `"peek=" + (char)in.read() + "" + (char)in.read()` cố ý bắt đầu bằng **String**, vì cộng trực tiếp hai giá trị `char` có thể tạo phép cộng số nguyên thay vì ghép chuỗi. Đặc biệt nếu tệp chứa byte UTF-8 nhiều byte/ký tự, ép `(char) in.read()` từng byte **không phải decoder văn bản đúng**. Demo sử dụng ASCII để minh họa vị trí byte, không dùng thủ thuật đó làm parser Unicode.

## 5. Ví dụ phản ví dụ: `readlimit` không phải quy tắc hủy mark ngay lập tức

Tạo **`MemoryMarkDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class MemoryMarkDemo {
    public static void main(String[] args) throws IOException {
        byte[] values = {10, 20, 30, 40};

        try (ByteArrayInputStream in = new ByteArrayInputStream(values)) {
            System.out.println("supported=" + in.markSupported());

            in.mark(1); // Yêu cầu đọc trước 1 byte.
            System.out.println(in.read()); // 10
            System.out.println(in.read()); // 20
            System.out.println(in.read()); // 30

            in.reset(); // Vẫn hoạt động với ByteArrayInputStream.
            System.out.println("afterReset=" + in.read()); // 10
        }
    }
}
~~~

~~~bash
javac MemoryMarkDemo.java
java MemoryMarkDemo
~~~

Stdout:

~~~text
supported=true
10
20
30
afterReset=10
~~~

Đã đọc **3 byte sau `mark(1)`**, nhưng `ByteArrayInputStream` vẫn nhớ vị trí vì toàn bộ dữ liệu có sẵn trong bộ nhớ và implementation này không dùng `readlimit` để xóa mark. Hợp đồng chung chỉ nói stream **không bắt buộc** nhớ đủ khi vượt giới hạn. Vì vậy:

- **Không** viết unit test chung kiểu “đọc `readlimit + 1` byte thì `reset` bắt buộc ném `IOException`”.
- **Không** suy ra `readlimit` là lượng bộ nhớ dùng bởi mọi implementation.
- Nếu parser **phải** đọc lại N byte, chọn implementation có contract phù hợp, đặt readlimit bao phủ phần đọc trước và xử lý `IOException` khi cần.

Với `BufferedInputStream`, mark có thể bị vô hiệu nếu dữ liệu cần giữ vượt giới hạn và buffer phải bỏ vùng cũ. Việc thất bại ở thao tác cụ thể nào tùy trạng thái buffer và hợp đồng API; đừng xây logic nghiệp vụ dựa trên thời điểm nội bộ giải phóng bộ đệm.

## 6. Cách `BufferedInputStream` sử dụng buffer khi mark

Không có mark, `BufferedInputStream` được phép tiêu thụ dữ liệu từ nguồn thành từng khối và bỏ những byte đã phục vụ, giúp giảm số lời gọi I/O xuống nguồn. Khi có mark:

1. `mark(readlimit)` lưu **vị trí logic** hiện tại trong bộ đệm.
2. Đọc tiếp có thể lấy byte từ buffer, refill từ nguồn hoặc di chuyển phần dữ liệu đang cần giữ.
3. Khi còn cần reset, wrapper bảo toàn vùng từ mark tới vị trí đọc; một buffer nhỏ có thể được mở rộng để đáp ứng readlimit trong trường hợp cần.
4. Khi việc duy trì mark không còn nằm trong giới hạn, wrapper được phép bỏ mark/vùng cũ để hoạt động hiệu quả.
5. `reset` hợp lệ chỉ di chuyển vị trí đọc logic trong dữ liệu giữ lại; không nhất thiết đổi vị trí thật của file/socket bên dưới.

Điều này giải thích vì sao một mark với readlimit lớn trên luồng dữ liệu dài có thể làm tăng bộ nhớ. **`new BufferedInputStream(source, 8192)` không hề có nghĩa toàn bộ lookahead bị giới hạn tuyệt đối ở 8192 byte**, đồng thời `mark(1_000_000)` cũng **không đảm bảo** chính xác 1 MB sẽ được cấp phát ngay khi mark. Các cấu trúc nội bộ là chi tiết implementation; điều nên thiết kế là **N byte thực sự có thể cần replay**.

Nếu chỉ muốn xem 4 hoặc 8 byte header, truyền readlimit hợp lý thay vì `Integer.MAX_VALUE`. Nếu phải random access trên file lớn, cân nhắc `FileChannel.position(...)` hoặc `RandomAccessFile.seek(...)`, hoặc parser lưu header theo cấu trúc riêng: mark/reset không phải cơ chế tìm kiếm dữ liệu tùy ý.

## 7. `BufferedReader.mark(readAheadLimit)`: đơn vị là ký tự

`Reader` xử lý dữ liệu đã giải mã thành ký tự, nên phương thức `mark(int readAheadLimit)` tính giới hạn theo **char/UTF-16 code unit** mà `Reader` phát ra. Khác với `InputStream.mark`, khai báo của `Reader.mark` có thể **ném `IOException`**. `BufferedReader` hỗ trợ mark/reset và có thể mở rộng buffer để giữ phần read-ahead.

Tạo tệp **`MarkReaderDemo.java`**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class MarkReaderDemo {
    public static void main(String[] args) throws IOException {
        // Chuỗi có A + emoji supplementary U+1F642 + B.
        try (BufferedReader reader =
                new BufferedReader(new StringReader("A\uD83D\uDE42B"))) {
            System.out.println("supported=" + reader.markSupported());

            reader.mark(3); // 3 UTF-16 code unit, không phải 3 glyph.
            System.out.println("first=" + (char) reader.read()); // A

            char high = (char) reader.read();
            char low = (char) reader.read();
            System.out.println("high=" + Character.isHighSurrogate(high));
            System.out.println("low=" + Character.isLowSurrogate(low));

            reader.reset();
            System.out.println("replay=" + (char) reader.read());
        }
    }
}
~~~

~~~bash
javac MarkReaderDemo.java
java MarkReaderDemo
~~~

Stdout:

~~~text
supported=true
first=A
high=true
low=true
replay=A
~~~

Một code point emoji ngoài BMP dùng **hai** `char`. Khi `BufferedReader` đọc 3 lần `read()` trong ví dụ, đã tiêu thụ 3 code unit (A + high surrogate + low surrogate), dù người dùng nhìn thấy hai ký tự. `readAheadLimit=3` được chọn đúng cho số `char` cần replay.

Nếu nguồn là byte từ mạng/tệp, cấu trúc thường là:

~~~java
// Fragment: source là InputStream do caller cung cấp.
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(source, StandardCharsets.UTF_8))) {
    if (reader.markSupported()) {
        reader.mark(64); // 64 đơn vị char ở tầng reader.
        // Đọc ký tự...
        reader.reset();
    }
}
~~~

Fragment cần import `java.io.BufferedReader`, `java.io.InputStreamReader`, `java.nio.charset.StandardCharsets` và nằm trong method xử lý `IOException`. Hãy mark **trên BufferedReader**, vì `InputStreamReader` thường không hỗ trợ mark. Đừng reset stream byte bên dưới khi `InputStreamReader` đã đọc trước byte để giải mã; trạng thái decoder và buffer có thể không còn đồng bộ.

### 7.1. `BufferedReader.mark(0)` và điều kiện đầu vào

`BufferedReader.mark(readAheadLimit)` có thể ném `IllegalArgumentException` nếu `readAheadLimit` âm. `0` hợp lệ về đối số nhưng **không nên dùng làm cam kết phát lại một ký tự**: nếu cần đọc trước N ký tự, hãy đặt giới hạn đủ N và xử lý lỗi khi reset. Với `BufferedInputStream.mark(readlimit)`, hợp đồng chung không cho phép suy luận giống hệt về cách xử lý các giá trị bất thường; gọi theo dữ liệu hợp lệ đã tính rõ.

Đừng nhầm bộ đệm char trong `BufferedReader` với buffer byte trong `BufferedInputStream`: cùng là caching nhưng nằm ở **hai tầng khác nhau**. Khi ghép lớp, trách nhiệm cấp limit thuộc tầng mà parser đọc.

## 8. Mark sau khi `readLine` hoặc `skip`: còn tính không?

**Có thể có.** Mark/reset phụ thuộc lượng dữ liệu đã đọc sau mark và implementation, không chỉ số lần gọi `read()`.

- `BufferedReader.readLine()` đọc ký tự tới hết dòng và thường tiêu thụ dấu kết thúc dòng; `readAheadLimit` phải tính cả phần ký tự được **tiêu thụ**, kể cả ký tự newline vốn không có trong `String` kết quả. Kết thúc dòng Windows `\r\n` có hai code unit.
- `skip(n)` cũng di chuyển vị trí đọc; theo hợp đồng cụ thể, thao tác skip có thể tiêu thụ dữ liệu cần được giữ để reset. Không giả định “skip không tính”.
- `InputStream.read(byte[])` có thể đọc ít hơn số byte yêu cầu; lượng được **đọc thật** mới quan trọng. Với `DataInputStream.readInt()`, một số nguyên được diễn giải từ **4 byte**, không phải 1 “ký tự”; readlimit thuộc byte layer.
- `InputStream.readAllBytes()` hoặc `transferTo(...)` có thể tiêu thụ cả phần dữ liệu còn lại; đặt mark với readlimit nhỏ trước một thao tác tiêu thụ không giới hạn có thể làm reset không còn khả dụng.

Đối với parser nhận dạng header, hãy đọc **số byte cố định** có giới hạn rõ, không dựa vào `available()`: `available()` chỉ ước lượng số byte có thể đọc/skip mà không blocking, **không phải độ dài toàn bộ stream**.

## 9. Mẫu lookahead an toàn theo chiều xử lý

### 9.1. Byte header hữu hạn

~~~java
// Fragment bên trong method có thể ném IOException.
// source là InputStream chưa bị tiêu thụ bên ngoài wrapper.
try (BufferedInputStream in = new BufferedInputStream(source)) {
    if (!in.markSupported()) {
        throw new IllegalStateException("Missing mark/reset");
    }

    in.mark(4);
    int b1 = in.read();
    int b2 = in.read();
    // b1/b2 có thể bằng -1; kiểm tra EOF trước khi so header.
    boolean gzip = b1 == 0x1F && b2 == 0x8B;
    in.reset();

    if (gzip) {
        // Truyền 'in' cho tầng xử lý gzip phù hợp.
    } else {
        // Truyền 'in' cho tầng xử lý định dạng khác.
    }
}
~~~

Đây là **fragment**, không phải chương trình độc lập. Giới hạn 4 byte đủ cho hai lần đọc thử; nếu muốn nhận dạng thêm nhiều định dạng, tính lại N và số byte thực đọc. Việc parser thực thi đúng còn cần xử lý EOF, các bộ đọc hợp lệ và ownership của stream được truyền. Nếu `reset()` lỗi, **không được bỏ qua rồi tiếp tục coi con trỏ chưa bị thay đổi**.

### 9.2. Chỉ cần đọc lại một số byte: `PushbackInputStream`

`PushbackInputStream` có thể `unread(byte)` hoặc `unread(byte[],...)` để đẩy lại byte vào pushback buffer. Nó thích hợp khi bạn đã đọc một prefix nhỏ và muốn trả đúng prefix đó cho consumer, nhưng `markSupported()` vẫn là **false**.

~~~java
// Fragment: source là InputStream; try-with-resources quản lý tài nguyên.
try (PushbackInputStream in = new PushbackInputStream(source, 2)) {
    int first = in.read();
    if (first != -1) {
        in.unread(first); // Lần read kế tiếp sẽ trả cùng byte.
    }
    // Xử lý tiếp với 'in', không đổi sang 'source'.
}
~~~

Fragment cần import `java.io.PushbackInputStream`, khai báo/bắt `IOException`, và không được `unread(-1)` khi EOF. Độ lớn pushback buffer phải đủ dữ liệu trả lại; nếu quá sức chứa, `unread` ném `IOException`. Pushback không tương đương mark/reset nhiều lượt: nó đặt dữ liệu cụ thể về trước vị trí đọc, không đưa con trỏ về một bookmark được lưu.

### 9.3. Cần seek tùy ý trên file

- `RandomAccessFile.seek(long)` hoặc `FileChannel.position(long)` cho phép đặt vị trí trên **file/channel** hỗ trợ random access; đây là cơ chế khác với `InputStream.mark/reset`.
- Một file có thể được mở lại và bỏ qua phần đầu để đọc lại, nhưng không hiệu quả cho tệp lớn và không chắc phản ánh cùng snapshot nếu file bị thay đổi.
- Socket stream thường không thể seek; nếu cần đọc lại, dùng buffering, pushback hoặc tự lưu lượng header hữu hạn. Nếu phải rewind dữ liệu không giới hạn, cần thiết kế spool ra bộ nhớ/tệp tạm có giới hạn và phương án cleanup.

## 10. Các kiểu lỗi và xử lý hợp lý

| Tình huống | Kết quả có thể gặp | Cách xử lý |
| --- | --- | --- |
| `markSupported() == false` | `mark` có thể bị bỏ qua, `reset` ném `IOException` | Dùng wrapper hỗ trợ, pushback hoặc cấu trúc dữ liệu khác. |
| Gọi `reset` trước `mark` | Tùy implementation: lỗi hoặc trạng thái mặc định riêng | Mark trước khi đọc thử, không dựa vào mặc định. |
| Đọc vượt `readlimit` | Mark có thể bị vô hiệu; một số implementation vẫn cho reset | Chọn limit đủ cho lookahead; không coi giới hạn là điểm hủy tuyệt đối. |
| Mark/Reset sau `close()` | Lỗi I/O hoặc mark không có tác dụng tùy hợp đồng class | Giữ stream mở trong toàn bộ giao dịch đọc thử và đọc chính. |
| `BufferedReader.mark(-1)` | `IllegalArgumentException` | Kiểm tra số ký tự cần đọc trước, không dùng số âm. |
| Nhiều lần `mark` | Mark gần nhất thay mark trước | Chỉ thiết kế một mốc hiện hành hoặc tự lưu dữ liệu cho nested backtracking. |
| Mark trên một wrapper nhưng đọc source trực tiếp | Cursor logic và dữ liệu buffer mất đồng bộ | Chỉ để một consumer dùng wrapper đã chọn. |
| Đọc UTF-8 raw byte rồi cắt giữa sequence | Ký tự lỗi nếu decode từng đoạn | Decode bằng Reader có trạng thái; mark ở tầng ký tự khi parser làm việc với text. |
| `readLine` bỏ newline khỏi kết quả | Đếm limit thiếu ký tự `\n` hay `\r\n` đã tiêu thụ | Dành readAheadLimit theo code unit thực đọc, gồm dấu dòng. |
| `mark(Integer.MAX_VALUE)` cho mọi nguồn | Wrapper có thể giữ buffer rất lớn | Giới hạn protocol rõ ràng, chặn input không tin cậy và chọn chiến lược seek/spool phù hợp. |

**Nguyên tắc bắt lỗi:** `markSupported()` chỉ xác nhận capability của loại stream; nó **không loại bỏ** ngoại lệ khi `reset()`, `read()` hay đóng stream. Trong parser, thất bại rewind thường khiến bạn **không còn ở vị trí dự định**; chuyển sang luồng xử lý bình thường mà không sửa con trỏ là bug.

## 11. Phân biệt mark/reset với các API dễ nhầm

| Cơ chế | Điểm giống | Khác biệt cốt lõi |
| --- | --- | --- |
| `mark` + `reset` | Đọc thử và quay về mốc | Mốc logic gần nhất, replay có điều kiện theo stream và readlimit. |
| `PushbackInputStream.unread` | Cung cấp lại byte đã tiêu thụ | Caller chủ động đẩy **byte cụ thể** vào buffer, giới hạn bởi pushback capacity. |
| `FileChannel.position` / `RandomAccessFile.seek` | Có thể đọc lại từ vị trí trước | Dựa vào nguồn có random access, không bị giới hạn bởi readlimit kiểu stream. |
| `BufferedInputStream` | Caching byte làm giảm I/O tới nguồn | Buffering không đồng nghĩa vô hạn rewind; mark có điều kiện riêng. |
| `BufferedReader` | Caching dữ liệu text đã decode | Mark theo char code unit, không theo số byte của encoding. |
| `available()` | Hỏi về dữ liệu sẵn ngay | Không trả vị trí, không đặt mark, không báo chiều dài đầy đủ. |
| `skip(long)` | Di chuyển vị trí đọc tới trước | Không đặt bookmark; số thực skip có thể nhỏ hơn yêu cầu. |
| `InputStream.transferTo` | Đưa byte sang output | Tiêu thụ stream, không có cam kết reset sau đó. |

## 12. Câu hỏi tự kiểm tra

1. `FileInputStream` trả `markSupported() == false`. Bọc bằng `BufferedInputStream` có thể làm mark/reset hoạt động không? Vì sao?
2. Bạn mark với `readlimit = 5`, đọc 7 byte rồi reset thành công ở `ByteArrayInputStream`. Điều này có vi phạm contract không?
3. `reset()` di chuyển về đầu tệp hay vị trí gọi `mark` gần nhất?
4. Parser dùng `BufferedReader` và đọc emoji ngoài BMP sau mark. Một emoji thường tiêu thụ bao nhiêu đơn vị `char`?
5. Vì sao `readAheadLimit=10` không luôn đủ để `readLine()` lấy chuỗi kết quả dài 10 ký tự?
6. Nếu người khác đọc trực tiếp `FileInputStream` trong lúc bạn mark trên `BufferedInputStream` của nó, điều gì có thể sai?
7. Khi cần trả lại đúng 2 byte header nhưng source không hỗ trợ mark, còn những lựa chọn nào?
8. Có thể dựa vào việc đọc byte thứ `readlimit + 1` để khẳng định mọi `reset` sẽ ném lỗi không?

**Gợi ý đáp án:**

1. **Có**; wrapper giữ byte đã đọc để phát lại mà không đòi nguồn file tự seek.
2. **Không**; readlimit là ngưỡng mà sau đó stream không còn bắt buộc giữ mark, không phải lệnh bắt buộc xóa mark.
3. Trở về mark gần nhất còn hợp lệ, không tự động về đầu.
4. Thường **hai** UTF-16 code unit (`char`), dù là một code point.
5. `readLine()` còn có thể tiêu thụ `\n` hoặc `\r\n` mà không trả trong chuỗi kết quả.
6. Hai tầng không còn chia sẻ vị trí logic nhất quán; buffer có thể đã đọc trước dữ liệu của source.
7. `BufferedInputStream` + mark/reset, `PushbackInputStream` + `unread`, hoặc lưu prefix riêng rồi phối hợp reader.
8. **Không**; mark có thể còn sử dụng được tùy implementation và trạng thái.

## 13. Tài liệu API chuẩn

- [`InputStream` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStream.html): `mark(int)`, `reset()`, `markSupported()` và giới hạn contract của `readlimit`.
- [`BufferedInputStream` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/BufferedInputStream.html): buffering, mark vị trí logic, điều kiện làm mất mark.
- [`ByteArrayInputStream` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ByteArrayInputStream.html): stream trong bộ nhớ, mark/reset luôn được hỗ trợ.
- [`BufferedReader` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/BufferedReader.html): `mark(int readAheadLimit)` và tính chất mark theo ký tự.
- [`Reader` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Reader.html): contract chung của reader, `mark` và `reset`.
- [`PushbackInputStream` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/PushbackInputStream.html): `unread` và `markSupported() == false`.

**Ghi nhớ:** kiểm tra `markSupported()` trên **đúng đối tượng** đang được đọc; `mark(N)` trước khi lookahead, đảm bảo N tính theo đúng đơn vị và đủ cho đoạn cần phát lại; `reset()` có thể ném `IOException`; không đọc xen kẽ bên dưới wrapper và không dùng mark/reset làm random access không giới hạn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buffering-and-stream-composition-009">Reader.ready khác kiểm tra EOF như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# `Reader.ready()` khác kiểm tra EOF như thế nào trong Java I/O?

## 1. Hai câu hỏi hoàn toàn khác nhau

Khi đọc dữ liệu bằng `java.io.Reader`, có hai vấn đề dễ bị nhầm lẫn:

- **Readiness:** Nếu gọi `read()` tiếp, thao tác đó có phải **chờ dữ liệu đầu vào** hay không? Đây là mục đích của `Reader.ready()`.
- **End of stream / EOF:** Nguồn đã **hết ký tự** theo hợp đồng của reader chưa? Điều này được báo bởi **giá trị trả về của thao tác đọc**, chẳng hạn `read() == -1` hoặc `readLine() == null`.

**`ready()` không phải phương thức kiểm tra EOF.** Một reader có thể chưa sẵn sàng *ở thời điểm hiện tại* nhưng vẫn còn dữ liệu sẽ xuất hiện sau; ngược lại, có reader trả `true` khi đã EOF bởi lần `read()` tiếp theo có thể trả `-1` ngay mà không cần chờ.

Theo hợp đồng của Java SE 17, `Reader.ready()` trả `true` nếu lời gọi `read()` kế tiếp **được bảo đảm không chặn để đợi input**. Trả `false` chỉ là **không có bảo đảm đó**; điều này **không có nghĩa** lời gọi `read()` chắc chắn sẽ chặn.

~~~text
                 CÂU HỎI: điều gì xảy ra khi đọc tiếp?
                         |
             +-----------+-----------+
             |                       |
        reader.ready()           reader.read()
             |                       |
       true / false          ký tự / -1 / IOException
             |                       |
       mức sẵn sàng             nội dung / EOF / lỗi
       cho read() tiếp
~~~

Ví dụ thực tế: kết nối mạng có thể chưa nhận thêm ký tự ở thời điểm kiểm tra, nên `ready()` là `false`, nhưng bên gửi vẫn đang hoạt động và sẽ gửi tiếp; **đó chưa phải EOF**.

## 2. Bảng so sánh API

| Tiêu chí | `Reader.ready()` | `Reader.read()` / `Reader.read(char[])` | `BufferedReader.readLine()` |
| --- | --- | --- | --- |
| Mục đích | Hỏi liệu `read()` kế tiếp có phải đợi input | Thực sự đọc ký tự | Thực sự đọc một dòng |
| Giá trị thông thường | `boolean`: `true`/`false` | `int`: giá trị char, số ký tự, hoặc `-1` | `String` hoặc `null` |
| Dấu hiệu EOF | **Không có** | **`-1`** | **`null`** |
| Có thể chặn? | Không dùng để đọc dữ liệu | Có thể chặn chờ ký tự, lỗi hoặc EOF | Có thể chặn đến khi có dòng, lỗi hoặc EOF |
| Có tiêu thụ ký tự? | Không lấy ký tự về cho chương trình | Có | Có |
| Dùng làm điều kiện vòng lặp đến hết file? | **Không** | **Có**, kiểm tra `-1` | **Có**, kiểm tra `null` |

Với `Reader.read()`, kết quả là số nguyên `0` đến `65535` đại diện cho một đơn vị UTF-16 (`char`), hoặc `-1` khi hết nguồn. **Không ép kiểu sang `char` trước khi kiểm tra `-1`**: phép ép kiểu loại bỏ ý nghĩa giá trị sentinel EOF.

Với `Reader.read(char[], off, len)`, kết quả là số ký tự đọc được, hoặc `-1` nếu hết nguồn; nếu `len == 0`, phương thức được phép trả `0` mà không đọc ký tự nào. Với lần đọc có `len > 0` trên các reader tuân thủ hợp đồng chuẩn, việc không có ký tự vì EOF được biểu diễn bằng `-1`, không phải `0`.

Với `BufferedReader.readLine()`, một dòng rỗng trả `""` (String rỗng); **chỉ `null` là EOF**. Dòng cuối không có ký tự xuống dòng vẫn được trả về nếu còn ký tự.

## 3. Chương trình Java 17+ chứng minh `ready() == true` vẫn có thể ở EOF

`StringReader` đọc từ một chuỗi đã nằm sẵn trong bộ nhớ. Đối với một `StringReader` đang mở, thao tác `read()` có thể được giải quyết ngay, kể cả khi không còn ký tự và nó sẽ trả `-1`. Triển khai `StringReader.ready()` của JDK trả `true` trong trường hợp này.

Tạo file **ReadyAtEofDemo.java**:

~~~java
import java.io.IOException;
import java.io.StringReader;

public class ReadyAtEofDemo {
    public static void main(String[] args) throws IOException {
        try (StringReader reader = new StringReader("Hi")) {
            System.out.println("readyBefore=" + reader.ready());
            System.out.println("first=" + (char) reader.read());
            System.out.println("second=" + (char) reader.read());

            int next = reader.read();
            System.out.println("readAtEof=" + next);
            System.out.println("readyAtEof=" + reader.ready());
            System.out.println("readAgain=" + reader.read());
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 ReadyAtEofDemo.java
java ReadyAtEofDemo
~~~

Stdout:

~~~text
readyBefore=true
first=H
second=i
readAtEof=-1
readyAtEof=true
readAgain=-1
~~~

Diễn giải:

1. `readyBefore=true`: hai ký tự trong bộ nhớ có thể đọc ngay.
2. Hai lần `read()` lần lượt tiêu thụ `H` và `i`.
3. Lần đọc tiếp theo trả **`-1`**: đã EOF.
4. `readyAtEof=true` nhưng vẫn EOF. Lý do: lần `read()` kế tiếp trả `-1` ngay, không phải đợi input.
5. Lần đọc sau nữa tiếp tục trả `-1`.

**Kết luận từ ví dụ:** Nếu dùng `while (reader.ready())` để kiểm tra “còn dữ liệu không”, chương trình có thể tiếp tục lặp ở EOF mà không dừng; nếu bên trong đọc ký tự bằng ép kiểu `(char) reader.read()`, nó có thể xử lý sai `-1` như dữ liệu bình thường.

**Giới hạn của ví dụ:** `readyAtEof=true` ở trên là hành vi cụ thể của `StringReader`; không được suy ra rằng mọi `Reader` đều trả `true` ở EOF. Điều đúng cho mọi Reader là **không dùng `ready()` làm dấu EOF**.

## 4. `ready() == false` cũng không chứng minh đã hết dữ liệu

Ngược lại, `false` chỉ nói reader **không bảo đảm** `read()` tiếp theo sẽ không cần chờ. Có thể việc đọc sẽ chặn, cũng có thể trả ký tự ngay, báo lỗi ngay hoặc trả EOF ngay tùy reader.

Tình huống điển hình: một nguồn streaming (chẳng hạn dữ liệu đến từ mạng hoặc đường ống) chưa có ký tự để trả ở thời điểm kiểm tra. Nếu người gửi chỉ tạm dừng, không có lý do gì để kết luận nguồn đã đóng.

~~~text
Thời điểm T0: receiver có thể thấy ready() == false
Thời điểm T1: sender gửi tiếp các byte
Thời điểm T2: read() có thể trả ký tự
Thời điểm T3: sender đóng luồng theo giao thức
Thời điểm T4: read() có thể trả -1 (EOF)
~~~

Các thời điểm trên chỉ là kịch bản minh họa, không phải lời hứa rằng mọi Reader sẽ trả `false` tại T0 hay kết thúc đúng thứ tự thời gian đó.

**Sai lầm phổ biến:**

~~~java
// SAI: ready() == false không chứng minh EOF.
while (reader.ready()) {
    int ch = reader.read();
    // Xử lý ch...
}
~~~

Đoạn này có thể **bỏ sót dữ liệu** nếu `ready()` tạm thời trả `false`; với một reader cụ thể luôn ready tại EOF, cũng có nguy cơ lặp vô hạn nếu không tự kiểm tra kết quả `read()`.

**Cách đúng khi yêu cầu là đọc toàn bộ ký tự đến EOF:**

~~~java
// Đúng: cần có Reader reader còn mở và xử lý IOException.
int ch;
while ((ch = reader.read()) != -1) {
    // Dùng (char) ch sau khi đã loại trừ giá trị -1.
    System.out.print((char) ch);
}
~~~

Đây là mảnh code dành cho phương thức đã có reader hợp lệ; nó có thể **chặn khi chưa có dữ liệu**. Nếu luồng không bao giờ đóng và không có quy tắc kết thúc khác, vòng lặp cũng có thể chờ vô thời hạn. Đó là vấn đề về vòng đời của nguồn dữ liệu, không thể sửa bằng `ready()`.

## 5. `BufferedReader.ready()` và ý nghĩa của dữ liệu trong bộ đệm

`BufferedReader` là lớp bọc (`decorator`) có bộ đệm ký tự nhằm giảm số lần đọc nguồn bên dưới. Theo Java SE 17, `BufferedReader.ready()` có thể trả `true` **khi bộ đệm còn ký tự hoặc reader bên dưới sẵn sàng**.

~~~text
InputStream (byte)
       |
       v
InputStreamReader (decode byte -> char theo charset)
       |
       v
BufferedReader (giữ trước một số char trong buffer)
       |
       +--> ready(): buffer còn ký tự / nguồn sẵn sàng?
       |
       +--> read() / readLine(): thực sự tiêu thụ ký tự
~~~

Ví dụ về bộ đệm:

- Reader nền có thể **tạm không sẵn sàng** để đọc thêm byte/ký tự mới từ nguồn.
- Nhưng `BufferedReader` đã đọc trước một số ký tự vào buffer nên **`BufferedReader.ready()` vẫn có thể là `true`**.
- Khi buffer cạn, `ready()` có thể đổi thành `false`; điều này không có nghĩa nguồn đã EOF.
- Khi EOF đến, kết quả đáng tin cậy vẫn là **`read() == -1`** hoặc **`readLine() == null`**, không phải một giá trị cố định của `ready()`.

**Một khác biệt tinh tế:** Hợp đồng `ready()` nói về **lời gọi `read()` kế tiếp** không chặn để đợi input. Nếu chương trình gọi `readLine()`, chỉ có một vài ký tự trong buffer mà chưa có dấu kết thúc dòng, `readLine()` vẫn có thể cần chờ thêm. Vì thế **`ready() == true` không phải bảo đảm rằng toàn bộ dòng tiếp theo đã sẵn sàng**.

Tương tự, một lần gọi `read(char[])` yêu cầu nhiều ký tự; ngay cả khi đã đọc được một số ký tự, việc điền thêm phần còn lại phụ thuộc reader và điều kiện của nguồn. Không dùng `ready()` để cam kết độ dài thông điệp hoặc tốc độ đọc toàn bộ payload.

## 6. Đọc từng dòng đến EOF: cách kiểm tra khác với chuỗi rỗng

Tạo file **ReadLinesDemo.java**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class ReadLinesDemo {
    public static void main(String[] args) throws IOException {
        String input = "first\n\nlast";

        try (BufferedReader reader =
                new BufferedReader(new StringReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("line=[" + line + "]");
            }
            System.out.println("EOF");
        }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 ReadLinesDemo.java
java ReadLinesDemo
~~~

Stdout:

~~~text
line=[first]
line=[]
line=[last]
EOF
~~~

Ví dụ cho thấy:

- `"first"`: dòng đầu kết thúc bởi LF.
- `""`: dòng thứ hai trống, **không phải EOF**.
- `"last"`: dòng cuối không có ký tự xuống dòng vẫn được trả về.
- `null`: không còn dòng nào để đọc, lúc đó mới in `EOF`.

Không viết `while (reader.readLine() != "")` hoặc `while (reader.ready())` để thay cho phép so sánh với `null`. Biến `line` trong điều kiện phải được gán từ **một lần đọc duy nhất** để không vô tình bỏ qua dòng.

Với file text UTF-8 thực tế, có thể dùng:

~~~java
// Mảnh code trong phương thức có Path path và xử lý IOException.
try (BufferedReader reader =
        java.nio.file.Files.newBufferedReader(
                path, java.nio.charset.StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý từng dòng.
    }
}
~~~

Chọn charset tường minh giúp việc chuyển từ byte file sang ký tự đúng theo định dạng. Việc bọc buffer hỗ trợ hiệu năng nhưng không đổi điều kiện phát hiện EOF.

## 7. Đọc theo từng khối char: chú ý giá trị đếm và `-1`

Nếu cần đọc dữ liệu lớn, thay vì đọc từng ký tự, dùng buffer:

~~~java
// Mảnh code trong phương thức có Reader reader và xử lý IOException.
char[] buffer = new char[4096];
int count;
while ((count = reader.read(buffer, 0, buffer.length)) != -1) {
    // Chỉ vùng [0, count) có ký tự mới đọc trong lượt này.
    process(buffer, 0, count);
}
~~~

Ở đây `process` là hàm xử lý do chương trình cung cấp; ví dụ minh họa không phải chương trình chạy độc lập. Kích thước buffer 4096 là mức tối đa mỗi lần yêu cầu; **`read` không hứa trả đủ 4096 ký tự**. Nếu trả 120 thì chỉ 120 phần tử đầu là dữ liệu mới. Chạy vòng lặp đến khi `-1`, không dựa vào `ready()`.

Trong hợp đồng chuẩn `Reader.read(char[], off, len)`, với `len > 0`, `-1` là EOF; nếu `len == 0`, có thể nhận `0`, kể cả khi đã EOF. Vì vậy dùng mảng không rỗng và `buffer.length > 0` khi tạo vòng lặp đọc bình thường.

Nếu dữ liệu là nhị phân (ảnh, ZIP...), dùng `InputStream.read(byte[])` và `-1` tương ứng. **`InputStream.available()` cũng không phải cách phát hiện EOF**: nó cho biết ước lượng byte có thể đọc mà không chặn, không phải kích thước còn lại của luồng hay dấu kết thúc.

## 8. Vì sao không dùng `ready()` để làm polling hoặc timeout?

Một đoạn kiểu này thường có vẻ hợp lý nhưng không an toàn:

~~~java
// SAI nếu mục tiêu là đọc hết một stream:
while (true) {
    if (reader.ready()) {
        int ch = reader.read();
        if (ch == -1) break;
        handle(ch);
    } else {
        // Giả sử đã EOF rồi thoát, hoặc vòng lặp nóng chờ.
        break;
    }
}
~~~

Nếu `ready()` trả `false` chỉ vì dữ liệu chưa tới, `break` làm **mất phần dữ liệu sẽ đến sau**. Thay `break` bằng việc lặp liên tục không giới hạn có thể khiến chương trình **busy-spin**, chiếm CPU. Mặt khác, ngay cả khi `ready()` trả `true`, những bước xử lý khác vẫn có thể tốn thời gian hoặc phát sinh lỗi; đó không phải cơ chế deadline/timeouts.

Phải chọn giải pháp theo yêu cầu:

| Yêu cầu | Hướng xử lý |
| --- | --- |
| Đọc file/dữ liệu hữu hạn đến hết | `read() != -1`, `readLine() != null` hoặc `read(buffer) != -1` |
| Giao thức biết trước độ dài message | Đọc đủ độ dài đã định, xử lý thiếu byte/ký tự như lỗi giao thức |
| Message kết thúc bằng delimiter | Đọc đến delimiter đã quy định, không suy từ `ready()` |
| Socket có thể chờ lâu | Cấu hình timeout hoặc giới hạn vòng đời ở tầng socket/protocol; xử lý EOF và lỗi riêng |
| Kết nối event-driven/nonblocking | Dùng API được thiết kế cho nonblocking I/O như `java.nio.channels.Selector` với channel phù hợp, thay vì giả lập bằng `Reader.ready()` |
| Chỉ muốn biết thao tác `read()` tiếp có được bảo đảm không chờ input | Khi đó mới cân nhắc `ready()`, nhưng vẫn phải kiểm tra giá trị trả về của `read()` |

**EOF của socket:** thường chỉ xuất hiện khi phía gửi kết thúc chiều gửi của kết nối, hoặc giao thức/nguồn kết thúc và reader tiêu thụ hết dữ liệu đã đệm; “tạm thời chưa có byte” không phải EOF. Đọc socket có thể trả lỗi I/O thay vì EOF tùy tình huống.

Đối với dữ liệu có dấu ngắt dòng, `readLine()` có thể chờ lâu khi bên gửi chưa gửi newline dù có một số ký tự. Nếu cần phản hồi tức thời theo ký tự, thiết kế giao thức/reader cho yêu cầu đó thay vì dùng `ready()` để đoán “đã có một dòng”.

## 9. Các trường hợp biên cần nhớ

**Reader đang đóng:** `ready()` và `read()` có thể ném `IOException`; đóng luồng không phải một cách thay cho kiểm tra `-1`. Trong chương trình đúng, dùng try-with-resources để đảm bảo giải phóng tài nguyên.

**Buffer còn dữ liệu sau khi nguồn bên dưới đã hết:** `BufferedReader` vẫn có thể trả những ký tự đã đệm. Chỉ khi ký tự trong buffer đã được tiêu thụ hết thì thao tác đọc tiếp mới trả `-1`.

**Nguồn vô hạn hoặc chưa biết khi nào kết thúc:** một lần `read()` có thể chặn để đợi dữ liệu; việc duyệt “đến EOF” không tự có deadline. Ứng dụng cần timeout, đóng luồng, tín hiệu dừng hoặc frame protocol phù hợp.

**Một ký tự Unicode nhìn thấy không luôn là một `char`:** `Reader.read()` trả về một đơn vị UTF-16; một code point ngoài BMP có thể cần hai lần đọc. Điều kiện EOF vẫn là `-1`, bất kể cách ghép surrogate pair của dữ liệu.

**`readLine() == null` khác `readLine().isEmpty()`:** chuỗi rỗng là một dòng hợp lệ; nên gán kết quả vào biến rồi kiểm tra `null` trước khi xử lý.

**Nhiều thread cùng đọc một reader:** gọi `ready()` ở thread A rồi `read()` ở thread B hoặc có thread khác tiêu thụ dữ liệu ở giữa có thể tạo trạng thái thay đổi theo thời gian. Không dùng `ready()` như cơ chế phối hợp đồng bộ giữa các thread.

## 10. Bảng sửa lỗi nhanh

| Code / suy luận sai | Vì sao sai | Cách sửa |
| --- | --- | --- |
| `if (!reader.ready()) EOF` | False không khẳng định hết dữ liệu | Gọi `read()` và kiểm tra `-1` |
| `while (reader.ready()) { ... }` | Có thể bỏ dữ liệu, hoặc không tự dừng ở EOF | Dùng kết quả đọc làm điều kiện |
| `char c = (char) reader.read()` ngay lập tức | Có thể ép `-1` thành `char`, làm mất dấu EOF | Giữ `int`, kiểm tra `-1` trước |
| `while ((line = reader.readLine()) != "")` | So sánh sai ngữ nghĩa dòng trống và EOF | `while ((line = reader.readLine()) != null)` |
| `reader.ready() == true` nghĩa là đã có đủ nguyên một dòng | Readiness của `read()` không nói đủ delimiter | `readLine()` có thể phải chờ tiếp |
| `InputStream.available() == 0` nghĩa là EOF | Available là tín hiệu sẵn có, không phải EOF | Dùng `InputStream.read() == -1` |
| `read(char[])` luôn lấp đầy buffer | Reader được phép trả ít hơn độ dài yêu cầu | Xử lý đúng `count` mỗi lần |
| `ready()` thay thế socket timeout | Không cam kết thời hạn, không phải API timeout | Dùng deadline/timeout ở nguồn hoặc giao thức |

## 11. Câu hỏi tự kiểm tra

**Câu 1.** `reader.ready()` trả `false`. Có thể kết luận luồng đã EOF chưa?  
**Đáp án:** Không. Lần đọc tiếp theo chưa được bảo đảm là không chặn; dữ liệu vẫn có thể tới sau, hoặc lần đọc có thể trả ngay.

**Câu 2.** `reader.ready()` trả `true`. Có bảo đảm lần `read()` kế tiếp trả một ký tự không?  
**Đáp án:** Không. Nó chỉ bảo đảm không chặn vì chờ input; lần đọc vẫn có thể trả `-1` ở EOF. `StringReader` là ví dụ dễ thấy.

**Câu 3.** Khi đọc `Reader` từng ký tự, đâu là điều kiện dừng chuẩn?  
**Đáp án:** Đọc vào biến `int` và dừng khi `read() == -1`.

**Câu 4.** `BufferedReader.readLine()` trả `""`. Có phải EOF?  
**Đáp án:** Không. Đó là dòng rỗng; EOF được thể hiện bằng `null`.

**Câu 5.** Tại sao `BufferedReader.ready()` có thể `true` dù nguồn bên dưới không sẵn sàng?  
**Đáp án:** Vì buffer của `BufferedReader` đã có sẵn ký tự để trả ngay.

**Câu 6.** Nếu đang đọc socket mà thiếu byte cuối cùng, `ready()` giúp xác định tổng số byte còn lại không?  
**Đáp án:** Không. Phải dựa vào framing/giao thức hoặc EOF thực sự, và dùng timeout khi cần; `ready()` không cung cấp độ dài payload.

**Câu 7.** `ready()` trả `true` có bảo đảm `readLine()` không chặn không?  
**Đáp án:** Không. Một ký tự có thể đã sẵn sàng nhưng chưa có dấu kết thúc dòng, nên `readLine()` vẫn có thể chờ thêm.

## 12. Tài liệu chuẩn để đối chiếu hợp đồng

- [Java SE 17 — Reader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Reader.html): đặc tả `ready()`, `read()`, `read(char[], off, len)`.
- [Java SE 17 — BufferedReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html): đặc tả bộ đệm, `ready()` và `readLine()`.
- [Java SE 17 — StringReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/StringReader.html): reader từ chuỗi trong bộ nhớ, ví dụ để quan sát EOF.

**Điểm nhớ cốt lõi:** **`ready()` trả lời câu hỏi về khả năng đọc mà không phải chờ input**, còn **EOF được báo bởi kết quả thực sự của `read()` (`-1`) hoặc `readLine()` (`null`)**. Trong vòng lặp xử lý toàn bộ dữ liệu, luôn đặt điều kiện dừng theo kết quả đọc, không theo `ready()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="buffering-and-stream-composition-010">Tránh trộn nhiều lớp đọc có bộ đệm trên cùng một nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh trộn nhiều lớp đọc có bộ đệm trên cùng một nguồn trong Java I/O

## Mục tiêu bài học

Trong Java, `BufferedInputStream`, `BufferedReader`, `Scanner` và các bộ chuyển đổi như `InputStreamReader` giúp đọc dữ liệu thuận tiện, hiệu quả hơn. Nhưng nếu tạo **nhiều đối tượng đọc độc lập trên cùng một nguồn** rồi thay phiên đọc, chương trình có thể mất byte, bỏ qua dòng hoặc thấy EOF sớm dù dữ liệu vẫn đang nằm trong bộ đệm của đối tượng khác.

Sau bài học, bạn có thể:

- Giải thích **read-ahead (đọc trước)** và vị trí dữ liệu được giữ trong bộ đệm.
- Phân biệt **ghép nhiều lớp theo một chuỗi duy nhất** (an toàn) với **nhiều đầu đọc cạnh tranh trên cùng nguồn** (dễ sai).
- Quan sát lỗi bằng một chương trình Java 17+ có đầu ra xác định, rồi sửa bằng một điểm đọc duy nhất.
- Tổ chức luồng byte, luồng ký tự, parser nhị phân và tài nguyên `System.in` với quyền sở hữu rõ ràng.

## 1. Vì sao bộ đệm đọc trước nhiều hơn một lời gọi `read()`?

Xét dữ liệu byte trong nguồn:

```text
Nguồn gốc: A B C D E F G H I J K
```

Khi bạn gọi `buffered.read()` chỉ để nhận **một byte**, `BufferedInputStream` có thể đọc trước **một khối** từ nguồn và giữ phần còn lại trong bộ đệm riêng:

```text
                 +------------------------------------+
raw InputStream  | A B C D E F G H | I J K           |
                 +------------------------------------+
                         | đọc trước 8 byte
                         v
buffered buffer  [ A B C D E F G H ]
                     ^
           buffered.read() trả A

raw đang ở vị trí trước I.
B C D E F G H vẫn còn trong bộ đệm của buffered.
```

Sau khi gọi `buffered.read()`, vị trí đọc của nguồn gốc có thể đã tiến đến `I`, **không phải `B`**. Những byte `B..H` không mất: chúng vẫn ở trong `buffered`, và phải được lấy qua **chính đối tượng đó**.

Mức đọc trước thực tế phụ thuộc lớp và cách triển khai; kích thước bộ đệm không bảo đảm rằng mỗi lần đọc luôn lấy đúng bấy nhiêu byte trên mọi nguồn. Điều quan trọng là **một đối tượng có thể đã lấy từ nguồn nhiều hơn lượng dữ liệu nó vừa trả về cho người gọi**.

Bộ đệm của `BufferedReader` hoạt động tương tự ở mức **ký tự**; `InputStreamReader` còn có thể lấy trước nhiều byte để giải mã ký tự từ `InputStream`. `Scanner` cũng có cơ chế đệm/phân tích token riêng.

## 2. Phân biệt hai cách tổ chức rất dễ nhầm

**Đúng: các lớp nằm nối tiếp trong *một pipeline*, chỉ có một đầu đọc ứng dụng.**

```text
InputStream gốc
      |
      v
BufferedInputStream
      |
      v
InputStreamReader (UTF-8)
      |
      v
BufferedReader
      |
      v
Ứng dụng chỉ gọi readLine() trên BufferedReader
```

Mỗi lớp phía ngoài lấy dữ liệu từ lớp ngay dưới nó. Phần đọc trước vẫn đi qua **một đường tiêu thụ duy nhất**. Đây là cách **ghép luồng** hợp lệ, dù có thể không cần thêm mọi tầng đệm trong mọi chương trình.

**Sai: tạo hai đầu đọc cạnh tranh hoặc đọc xen kẽ qua lớp bọc và nguồn gốc.**

```text
                     +--> BufferedInputStream A --> logic 1
InputStream gốc -----+
                     +--> BufferedInputStream B --> logic 2

hoặc

InputStream gốc -----+--> BufferedInputStream --> logic 1
                     +--> đọc trực tiếp raw   --> logic 2
```

Khi A lấy trước byte vào buffer, B không biết byte nào đã nằm trong buffer của A. Cả hai cùng làm thay đổi vị trí đọc chung ở nguồn gốc. Vì vậy, kết quả tùy theo **thứ tự và mức đọc trước của từng bên**; dữ liệu mà ứng dụng mong chờ có thể nằm ở một đối tượng khác.

**Quy tắc cốt lõi:** Có thể bọc **nhiều tầng trên cùng một chuỗi**, nhưng không nên dùng **nhiều đầu đọc độc lập để cạnh tranh trên cùng nguồn** nếu không có cơ chế phân phối dữ liệu được thiết kế riêng.

## 3. Ví dụ Java 17+: đọc xen kẽ làm đổi thứ tự dữ liệu

Ví dụ cố tình tạo một trường hợp đủ nhỏ để nhìn thấy read-ahead. `ByteArrayInputStream` giữ sẵn dãy `ABCDEFGHIJK`; `BufferedInputStream` được tạo với bộ đệm 8 byte. Lưu thành **`MixedBufferedReadDemo.java`**:

```java
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MixedBufferedReadDemo {
    public static void main(String[] args) throws IOException {
        byte[] data = "ABCDEFGHIJK".getBytes(StandardCharsets.US_ASCII);
        ByteArrayInputStream raw = new ByteArrayInputStream(data);

        try (BufferedInputStream buffered = new BufferedInputStream(raw, 8)) {
            System.out.println("buffered first = " + (char) buffered.read());
            System.out.println("raw available = " + raw.available());
            System.out.println("raw next = " + (char) raw.read());
            System.out.println("buffered rest = "
                    + new String(buffered.readAllBytes(),
                                 StandardCharsets.US_ASCII));
        }
    }
}
```

Biên dịch, chạy trên JDK 17+:

```bash
javac MixedBufferedReadDemo.java
java MixedBufferedReadDemo
```

Đầu ra của ví dụ với `ByteArrayInputStream` và bộ đệm 8 byte trên JDK 17:

```text
buffered first = A
raw available = 3
raw next = I
buffered rest = BCDEFGHJK
```

Diễn giải theo từng bước:

1. `buffered.read()` trả `A` nhưng lấy trước tám byte `A..H` vào bộ đệm. Nguồn `raw` chỉ còn `IJK` nên `raw.available()` là `3`.
2. `raw.read()` **bỏ qua góc nhìn của `buffered`** và nhận `I` trực tiếp. Đây là chỗ chương trình vô tình đổi thứ tự xử lý.
3. Khi tiếp tục đọc từ `buffered`, chương trình nhận `B..H` trong bộ đệm, sau đó nhận `JK` còn ở nguồn. Kết quả ghép lại thành `A` + `I` + `BCDEFGHJK`, không còn theo thứ tự `ABCDEFGHIJK` ban đầu.

Các byte không tự biến mất khỏi chương trình demo; chúng bị **phân phối sai thứ tự giữa hai đường đọc**. Trong parser thực tế, việc đọc nhầm vị trí như vậy có thể gây lỗi tiêu đề, thiếu payload hoặc giải mã sai dữ liệu.

Đừng phụ thuộc vào chính xác giá trị `raw.available() == 3` với nguồn khác. Con số này minh họa một trường hợp cụ thể; `available()` không phải tổng độ dài dữ liệu, càng không biết hết byte đang nằm trong buffer của wrapper.

## 4. Cách sửa: mọi thao tác đọc dùng cùng một đối tượng có bộ đệm

Lưu thành **`SingleBufferedReadDemo.java`**:

```java
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SingleBufferedReadDemo {
    public static void main(String[] args) throws IOException {
        byte[] data = "ABCDEFGHIJK".getBytes(StandardCharsets.US_ASCII);

        try (BufferedInputStream in =
                     new BufferedInputStream(new ByteArrayInputStream(data), 8)) {
            System.out.println("first = " + (char) in.read());
            System.out.println("rest = "
                    + new String(in.readAllBytes(), StandardCharsets.US_ASCII));
        }
    }
}
```

Chạy:

```bash
javac SingleBufferedReadDemo.java
java SingleBufferedReadDemo
```

Kết quả:

```text
first = A
rest = BCDEFGHIJK
```

Lúc này, lần đọc đầu tiên có thể vẫn đọc trước `A..H`, nhưng phần còn lại được trả lại **đúng thứ tự** bởi cùng `in`. Bước sửa không đòi hỏi tắt buffering: chỉ cần không chuyển sang đọc trực tiếp nguồn `raw` giữa chừng.

Nếu nhiều phương thức cần cùng nguồn, hãy **truyền cùng đối tượng `BufferedInputStream`** xuống các phương thức đó, hoặc đặt quyền đọc ở một lớp parser rồi cho các thành phần khác tiêu thụ **kết quả đã phân tích**. Tránh mỗi phương thức tự tạo một wrapper mới từ nguồn gốc.

## 5. Trộn byte và ký tự còn nguy hiểm hơn

Ví dụ thiết kế dễ gặp lỗi:

```java
// MINH HỌA SAI: hai lối đọc trên cùng socketInput.
InputStreamReader characters =
        new InputStreamReader(socketInput, java.nio.charset.StandardCharsets.UTF_8);
int firstCharacter = characters.read();
int nextRawByte = socketInput.read();
```

Đây là **đoạn minh họa**, không phải chương trình độc lập; biến `socketInput` đại diện một `InputStream` do chương trình khác cấp.

`InputStreamReader` có thể đọc trước nhiều byte để giải mã UTF-8. Vì vậy, `nextRawByte` không nhất thiết là byte ngay sau ký tự vừa được trả về. Nếu byte đó thuộc ký tự UTF-8 nhiều byte, parser còn có thể tách giữa các byte của cùng một ký tự và gây lỗi.

Tương tự, tạo cả:

```java
BufferedReader lines =
        new BufferedReader(new InputStreamReader(raw, java.nio.charset.StandardCharsets.UTF_8));
Scanner tokens = new Scanner(raw, java.nio.charset.StandardCharsets.UTF_8);
```

là hai bộ đọc độc lập trên cùng `raw` nếu chương trình dùng chúng xen kẽ. Mỗi bên có thể đọc trước hoặc giữ trạng thái phân tích riêng. Lưu ý đoạn này giả định đã có biến `raw` kiểu `InputStream` và các import phù hợp; nó chỉ minh họa lỗi thiết kế.

Các tổ hợp nên tránh khi cùng tranh một nguồn:

- `BufferedReader` và `Scanner` cùng đọc `System.in`.
- `BufferedReader` đọc dòng, rồi đọc tiếp byte trực tiếp từ `InputStream` bên dưới.
- Hai `BufferedReader` khác nhau cùng bọc một `Reader` hoặc cùng bọc một đầu vào thô.
- Một `BufferedInputStream` đọc header nhưng phần body lại dùng `rawInputStream.read()`.
- Đọc một ít dữ liệu bằng wrapper A, sau đó bỏ A và dựng wrapper B trên nguồn gốc nhưng không xử lý byte A đã đọc trước.

Đừng nhầm lỗi này với **khác biệt token/dòng của `Scanner`**: `nextInt()` thường để lại dấu xuống dòng để `nextLine()` đọc; đó là quy tắc phân tách đầu vào **ngay trong một Scanner**, và có cách xử lý riêng. Trộn hai scanner/reader trên cùng nguồn tạo thêm lỗi đọc trước, khó đoán hơn.

## 6. Pipeline ký tự đúng: một `BufferedReader` đọc toàn bộ dòng

Lưu tệp **`SingleTextReaderDemo.java`**:

```java
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SingleTextReaderDemo {
    public static void main(String[] args) throws IOException {
        byte[] bytes = "alpha\nbeta\n".getBytes(StandardCharsets.UTF_8);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new BufferedInputStream(
                                new ByteArrayInputStream(bytes)),
                        StandardCharsets.UTF_8))) {
            String line;
            int index = 0;

            while ((line = reader.readLine()) != null) {
                System.out.println("line " + (++index) + ": " + line);
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac SingleTextReaderDemo.java
java SingleTextReaderDemo
```

Đầu ra:

```text
line 1: alpha
line 2: beta
```

Đây là chuỗi **`ByteArrayInputStream → BufferedInputStream → InputStreamReader(UTF-8) → BufferedReader`**. Có nhiều lớp, thậm chí hai tầng hỗ trợ buffering, nhưng chỉ có **một đường đọc** và ứng dụng chỉ sử dụng `reader.readLine()`. Do đó dữ liệu được giải mã và chia dòng nhất quán.

Trong thực tế, `BufferedReader(new InputStreamReader(raw, UTF_8))` thường đã đủ cho việc đọc theo dòng. Chỉ thêm `BufferedInputStream` khi có lý do phù hợp với kiến trúc hoặc hiệu năng. Nếu bọc thêm, hãy tiếp tục dùng **đầu ngoài cùng**.

### `readLine()` tiêu thụ những gì?

`BufferedReader.readLine()` trả dòng **không kèm** ký tự kết thúc dòng (`\n`, `\r` hoặc `\r\n`). Nếu định dạng đòi hỏi giữ nguyên byte ban đầu, đừng vừa đọc bằng `readLine()` vừa cố “lấy lại” dấu xuống dòng bằng một đầu đọc byte độc lập. Thay vào đó, chọn API xử lý byte hoặc parser có khả năng bảo toàn separator ngay từ đầu.

## 7. Parser nhị phân: đọc header và body qua *cùng một luồng*

Một lỗi thường gặp ở giao thức nhị phân là đọc header qua wrapper, sau đó đọc body trên đối tượng thô. Hãy giữ cả hai bước trên một đường đọc. `DataInputStream` cung cấp `readUnsignedShort()`, `readFully(byte[])` và các phương thức cho dữ liệu nhị phân; có thể đặt nó **bên ngoài** `BufferedInputStream`.

Ví dụ packet có hai byte độ dài (big-endian), ba byte chữ `cat`, rồi một byte đánh dấu `X`. Lưu thành **`FramedPacketDemo.java`**:

```java
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FramedPacketDemo {
    public static void main(String[] args) throws IOException {
        byte[] packet = {0, 3, 99, 97, 116, 88};

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(
                        new ByteArrayInputStream(packet), 4))) {
            int length = in.readUnsignedShort();
            byte[] body = new byte[length];
            in.readFully(body);

            System.out.println("length = " + length);
            System.out.println("body = "
                    + new String(body, StandardCharsets.US_ASCII));
            System.out.println("trailer = " + (char) in.readUnsignedByte());
            System.out.println("eof = " + in.read());
        }
    }
}
```

Chạy:

```bash
javac FramedPacketDemo.java
java FramedPacketDemo
```

Đầu ra:

```text
length = 3
body = cat
trailer = X
eof = -1
```

`DataInputStream` đọc **cả header, body lẫn trailer** từ cùng `in`; việc `BufferedInputStream` lấy trước các byte không gây vấn đề. `readFully` sẽ đọc cho đủ số byte yêu cầu hoặc ném `EOFException` nếu EOF đến sớm, khác với một lần `read(byte[])` vốn có thể trả ít hơn số byte mong muốn.

Khi xử lý dữ liệu bên ngoài không đáng tin cậy, phải **kiểm tra giới hạn của `length` trước khi cấp phát `new byte[length]`**. Trong chương trình này, packet cố định và đủ nhỏ để tập trung vào nguyên tắc dùng chung một đầu đọc.

## 8. Đóng luồng và chuyển quyền sở hữu cũng có thể phá pipeline

Nhiều wrapper I/O trong Java **đóng luôn luồng bên dưới** khi `close()`. Nếu hai thành phần cùng tạo wrapper từ một đối tượng gốc, việc một wrapper đóng có thể khiến thành phần kia không đọc tiếp được.

Ví dụ các thao tác này dễ gây vấn đề:

```java
// MINH HỌA SAI khi raw còn được nơi khác sử dụng:
BufferedReader first = new BufferedReader(
        new InputStreamReader(raw, java.nio.charset.StandardCharsets.UTF_8));
first.close(); // Thường đóng cả InputStreamReader và raw bên dưới.
int next = raw.read(); // Có thể ném IOException nếu raw đã đóng.
```

Đây là đoạn mô tả rủi ro, không phải mã chạy độc lập và không phải mọi `InputStream` đều phản ứng giống nhau sau `close()`. Chẳng hạn `ByteArrayInputStream.close()` không có tác dụng, trong khi nhiều nguồn tệp và socket không thể đọc sau khi đóng. Không dựa vào ngoại lệ của luồng trong bộ nhớ để suy ra hành vi của nguồn thật.

Quy ước thực tế:

1. Một lớp hoặc thành phần **sở hữu** nguồn và quyết định thời điểm đóng.
2. Bọc nguồn **một lần thành chuỗi chính**, truyền lớp ngoài cùng cho code đọc.
3. Nếu phương thức chỉ mượn stream, ghi rõ hợp đồng nó **không tự đóng**; thành phần sở hữu sẽ đóng khi toàn bộ quá trình hoàn tất.
4. Với `System.in` dùng chung trong ứng dụng tương tác, đừng vô tình đóng nó trong một helper hoặc `try-with-resources` của reader tạm thời, khiến những phần sau không nhận được input.

**Ngoại lệ:** Nếu cố ý tạo stream con độc lập hoặc API cấp lại một nguồn mới, mỗi stream có thể có vòng đời riêng. Nguyên tắc ở bài này áp dụng khi **thực sự dùng chung một nguồn dữ liệu có trạng thái đọc**.

## 9. Nếu thật sự cần hai bộ xử lý, phân phối dữ liệu như thế nào?

Đôi khi ứng dụng cần vừa parse vừa log, vừa tính checksum hoặc cho nhiều component quan sát cùng dữ liệu. Cách giải không phải cho các bên tự đua đọc trên một `InputStream`:

- **Một reader, nhiều consumer:** chỉ một thành phần đọc từng khối byte, sau đó chuyển **bản sao hoặc dữ liệu đã phân tích** cho các thành phần khác. Mỗi consumer không động chạm tới vị trí của nguồn.
- **Ghi đồng thời trong pipeline có kiểm soát:** một lớp trung gian có thể chuyển byte qua nhiều đích (ví dụ log và parser), nhưng vẫn giữ **một đường đọc từ nguồn**. Cần xác định cách xử lý lỗi và thứ tự.
- **Nguồn dữ liệu tái mở được:** nếu cần hai lượt đọc hoàn toàn độc lập và nguồn hỗ trợ, mở **hai stream khác nhau từ đầu** (chẳng hạn hai lần gọi `Files.newInputStream(path)`); lúc đó mỗi stream có vị trí riêng. Không áp dụng cách này cho socket/pipe tiêu thụ một lần.
- **Dữ liệu nhỏ lưu trong RAM:** đọc toàn bộ nguồn **một lần** rồi cấp các `ByteArrayInputStream` riêng trên mảng đã lưu. Với nguồn lớn, không dùng giải pháp này vô điều kiện vì tốn bộ nhớ.
- **Dữ liệu dùng chung nhiều thread:** tập trung quyền đọc tại một thành phần rồi phân phối qua queue hoặc cơ chế message. Việc nhiều thread thay nhau gọi `read()` lên các wrapper khác nhau cũng không giải quyết được đọc trước và có thể tạo thêm lỗi đồng bộ.

Nếu một phương thức cần đọc một phần dữ liệu rồi để phương thức tiếp theo đọc tiếp, hãy chuyển **chính wrapper hiện tại** giữa các phương thức, không chỉ chuyển `raw` ban đầu.

## 10. Dấu hiệu nhận biết và cách chẩn đoán lỗi

| Triệu chứng | Câu hỏi cần kiểm tra | Cách sửa |
| --- | --- | --- |
| Đọc trực tiếp nguồn gốc bị nhảy qua vài byte | Có wrapper nào vừa đọc trước trên nguồn đó không? | Đọc phần tiếp theo qua chính wrapper. |
| Một parser thấy EOF bất ngờ | Dữ liệu có nằm trong buffer của parser khác không? | Dùng một parser/reader có quyền sở hữu nguồn. |
| `BufferedReader` bỏ qua dòng “khó hiểu” khi ghép `Scanner` | Có hai đối tượng cùng lấy trước từ `System.in`/socket không? | Chọn một API đầu vào và thống nhất quy tắc parse. |
| Byte đọc được nhưng ký tự UTF-8 bị lỗi | Có đang chuyển qua lại giữa đọc byte và đọc ký tự không? | Đọc và giải mã tập trung bằng một `Reader`/decoder. |
| `raw.available()` rất nhỏ dù còn dữ liệu | Byte đã nằm trong một buffer phía ngoài chưa? | Không dùng `available()` để tính dữ liệu còn lại hoặc EOF. |
| Đọc tiếp thất bại sau khi helper kết thúc | Helper có đóng wrapper, kéo theo đóng nguồn chung không? | Quy định quyền sở hữu và thời điểm `close()`. |

### `mark()` / `reset()` có giải quyết được không?

`BufferedInputStream` có thể hỗ trợ `mark/reset` trong một phạm vi đọc được giữ trong buffer. Tuy nhiên, `reset()` chỉ điều chỉnh **trạng thái của wrapper đó** theo hợp đồng của nó; không đồng bộ vị trí và bộ đệm của các wrapper cạnh tranh khác. Đừng coi `mark/reset` là cách “điều chỉnh” hai bộ đọc độc lập trên cùng một nguồn.

### Có thể chỉ dùng `InputStreamReader` mà không có `BufferedReader` không?

Có. `InputStreamReader` chuyển byte sang ký tự; `BufferedReader` bổ sung bộ đệm ký tự và các thao tác như `readLine()`. Mối nguy không nằm ở việc có bao nhiêu lớp nối tiếp, mà ở việc có **bao nhiêu lối đọc riêng cùng tranh dữ liệu nguồn**. Một `InputStreamReader` cũng có thể đọc trước byte khi giải mã.

## 11. Tự kiểm tra

**Câu 1.** `BufferedInputStream` trả về `A`; có được gọi `raw.read()` để lấy `B` không?

**Trả lời:** Không bảo đảm. Wrapper có thể đã lấy `B` cùng nhiều byte khác vào buffer. Hãy dùng `buffered.read()` để lấy byte kế tiếp theo đúng thứ tự.

**Câu 2.** `InputStream → BufferedInputStream → InputStreamReader → BufferedReader` có bị xem là trộn nhiều đầu đọc không?

**Trả lời:** Không, nếu ứng dụng chỉ đọc ở `BufferedReader` ngoài cùng và các lớp nối tiếp nhau. Đây là **một pipeline**; những đầu đọc *song song* trên cùng nguồn mới là rủi ro.

**Câu 3.** Tại sao hai `Scanner` cùng đọc `System.in` dễ sai?

**Trả lời:** Mỗi scanner có buffer và trạng thái token riêng. Một scanner có thể lấy trước dữ liệu mà scanner kia cần. Hãy dùng một scanner hoặc một reader/parser duy nhất.

**Câu 4.** Parser đọc hai byte độ dài và payload. Header đọc qua `DataInputStream(BufferedInputStream(raw))`, body đọc từ `raw`: có an toàn không?

**Trả lời:** Không. Dữ liệu body có thể nằm sẵn trong buffer của `DataInputStream`/`BufferedInputStream`. Đọc cả header và body qua cùng đối tượng `DataInputStream`.

**Câu 5.** Muốn hai thuật toán phân tích độc lập cùng một file, có thể mở hai `InputStream` từ cùng đường dẫn tệp không?

**Trả lời:** Có, nếu file ổn định và mỗi lời gọi mở tạo một stream riêng, quản lý vòng đời riêng. Đây là **hai nguồn đọc độc lập**, khác với hai wrapper dùng chung **một đối tượng stream**. Nếu file bị thay đổi đồng thời, phải có cách xử lý nhất quán dữ liệu.

## Ghi nhớ

**Một nguồn có trạng thái đọc → một pipeline chính → một đầu đọc ngoài cùng.** Có thể ghép nhiều tầng đệm và giải mã **nối tiếp** theo nhu cầu, nhưng khi đã đọc qua wrapper có bộ đệm thì đừng chuyển sang đọc trên raw stream hoặc wrapper thứ hai cùng bọc raw. Muốn nhiều consumer, hãy phân phối dữ liệu sau một điểm đọc chung hoặc mở các nguồn độc lập khi nguồn hỗ trợ.


</details>

- [Quay lại đầu trang](#back-to-top)
