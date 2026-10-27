<a id="back-to-top"></a>

# Truy cập ngẫu nhiên

## Menu
- [RandomAccessFile và các chế độ r, rw, rws, rwd](#random-access-001)
- [File pointer, seek và getFilePointer](#random-access-002)
- [Đọc ghi dữ liệu nguyên thủy qua DataInput và DataOutput](#random-access-003)
- [length, setLength và vùng dữ liệu chưa được ghi](#random-access-004)
- [Thiết kế bản ghi có vị trí và kích thước xác định](#random-access-005)
- [getChannel và trạng thái vị trí được chia sẻ](#random-access-006)
- [Không chia sẻ file pointer giữa nhiều thao tác mà thiếu phối hợp](#random-access-007)
- [Khi nào chọn RandomAccessFile thay FileChannel](#random-access-008)

## <a id="random-access-001">RandomAccessFile và các chế độ r, rw, rws, rwd</a>

<details>
<summary>Xem chi tiết</summary>

# RandomAccessFile và các chế độ `r`, `rw`, `rws`, `rwd`

## 1. Bài toán: đọc và sửa một vị trí bất kỳ trong tệp

Khi xử lý một tệp văn bản theo dòng, ta thường đọc tuần tự từ đầu tới cuối. Nhưng đôi khi ứng dụng cần **đọc bản ghi thứ 500**, **sửa đúng 4 byte ở giữa tệp**, hoặc **ghi nối tiếp sau byte cuối** mà không đọc lại toàn bộ nội dung. Đó là **truy cập ngẫu nhiên (random access)**.

Java cung cấp lớp **`java.io.RandomAccessFile`** để đọc/ghi theo **vị trí byte** trên một tệp:

- Có một **file pointer** (con trỏ vị trí), khởi đầu ở offset `0` khi mở tệp.
- `seek(offset)` đặt con trỏ đến offset mong muốn.
- Thao tác `read...` lấy byte bắt đầu tại con trỏ; `write...` ghi byte tại con trỏ. Sau thao tác, con trỏ tiến theo số byte đã đọc/ghi.
- `length()` trả về kích thước tệp **tính bằng byte**, khác với số ký tự hoặc số bản ghi.
- Chế độ mở (`"r"`, `"rw"`, `"rws"`, `"rwd"`) xác định quyền ghi và yêu cầu đồng bộ xuống thiết bị lưu trữ.

`RandomAccessFile` triển khai `DataInput`, `DataOutput` và `Closeable`; nó **không** là lớp con của `InputStream` hay `OutputStream`. Do có thể di chuyển con trỏ cả về trước lẫn về sau, nó phù hợp với tệp nhị phân, định dạng bản ghi có kích thước cố định, cập nhật header hoặc chỉ mục byte.

Sơ đồ tệp gồm ba số nguyên `int` (Java ghi mỗi `int` thành **4 byte**, byte có trọng số lớn trước):

~~~text
Offset byte       0             4             8            12 = length
                  |  int = 10   |  int = 20   |  int = 30  |
                  ^ bắt đầu       ^ seek(4)     ^ seek(8)   ^ EOF
~~~

Gọi `seek(4)` rồi `writeInt(99)` sẽ thay **chỉ bốn byte thứ 4 đến 7**. Tệp vẫn dài 12 byte; hai số khác giữ nguyên.

## 2. Bốn chế độ mở tệp

Cú pháp:

~~~java
// Path có thể được chuyển thành File khi dùng default file system:
try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
    // Đọc/ghi tại các byte offset mong muốn
}
~~~

Đây là đoạn minh họa đặt trong ngữ cảnh đã khai báo `Path path` và các import cần thiết. Một chương trình chạy đầy đủ nằm ở mục 5.

| Mode | Đọc | Ghi | Nếu tệp chưa tồn tại | Đảm bảo đồng bộ khi cập nhật |
|---|---|---|---|---|
| `"r"` | Có | Không | Mở thất bại (`FileNotFoundException`) | Không thực hiện ghi |
| `"rw"` | Có | Có | Cố tạo tệp mới | Không yêu cầu mỗi lần cập nhật phải đồng bộ xuống thiết bị |
| `"rws"` | Có | Có | Cố tạo tệp mới | Đồng bộ **nội dung và metadata** cho mỗi cập nhật |
| `"rwd"` | Có | Có | Cố tạo tệp mới | Đồng bộ **nội dung** cho mỗi cập nhật; metadata không có cùng yêu cầu |

Những điểm phải nhớ:

- `"r"` là **read-only**. Nếu gọi `writeInt`, `writeBytes`, `write` hoặc phương thức ghi khác trên đối tượng này, Java ném **`IOException`**.
- `"rw"` là **read/write**, **không có nghĩa** ghi nối cuối tệp (append). Trừ khi tự gọi `seek(length())`, chương trình ghi tại **con trỏ hiện tại**; lúc mới mở là offset 0 nên có thể **ghi đè phần đầu tệp**.
- `"rws"` và `"rwd"` đều là read/write giống `"rw"`, có thêm yêu cầu lưu bền vững ở mỗi cập nhật.
- Chỉ bốn chuỗi trên hợp lệ. Chẳng hạn `"w"`, `"a"`, `"rb"`, `"rw+"` hoặc `"R"` sẽ dẫn tới **`IllegalArgumentException`**.
- Việc mở tệp vẫn có thể thất bại vì không đủ quyền, không thể tạo tệp, thiếu thư mục cha, tệp đang bị hệ điều hành hạn chế truy cập hoặc lỗi I/O. `"rw"` không tự tạo toàn bộ thư mục cha.
- `RandomAccessFile` **không tự động truncate** tệp đang tồn tại khi mở bằng `"rw"`; muốn xóa nội dung cũ phải chủ động `setLength(0)`.

### 2.1. `rws`: đồng bộ dữ liệu **và metadata**

`"rws"` yêu cầu mọi cập nhật nội dung hoặc metadata được ghi **đồng bộ** xuống thiết bị lưu trữ cơ sở. Metadata liên quan đến tệp có thể bao gồm độ dài tệp và thời điểm sửa đổi; chi tiết phụ thuộc hệ thống tệp.

~~~java
try (RandomAccessFile raf = new RandomAccessFile(file, "rws")) {
    raf.seek(0);
    raf.writeInt(42); // Yêu cầu đồng bộ nội dung và metadata liên quan.
}
~~~

Đây là đoạn minh họa với `File file` đã hợp lệ. Mode này có ý nghĩa khi cần độ bền của cả thông tin quản lý tệp lẫn dữ liệu, ví dụ cập nhật khiến tệp dài thêm. Trên một số hệ thống tệp, đồng bộ metadata có thêm chi phí I/O.

### 2.2. `rwd`: đồng bộ dữ liệu, có thể rẻ hơn

`"rwd"` yêu cầu mỗi cập nhật **nội dung** được ghi đồng bộ; Java không yêu cầu đồng bộ cả metadata như `"rws"`.

~~~java
try (RandomAccessFile raf = new RandomAccessFile(file, "rwd")) {
    raf.seek(0);
    raf.writeInt(42); // Yêu cầu đồng bộ dữ liệu tệp.
}
~~~

Nếu thường xuyên ghi đè một vùng byte **đã tồn tại**, `"rwd"` có thể hợp lý vì không cần cùng mức đồng bộ metadata. Tuy nhiên không thể khẳng định `"rwd"` sẽ nhanh hơn `"rws"` trên mọi thiết bị; kết quả phụ thuộc OS, bộ nhớ đệm, file system và kiểu cập nhật.

**Rất quan trọng:** `rwd` chỉ đảm bảo đồng bộ dữ liệu theo hợp đồng API. Với một thao tác **mở rộng tệp**, metadata như kích thước cũng có thể quan trọng để sau sự cố còn truy cập được dữ liệu mới. Đừng tự suy ra “dữ liệu đã đồng bộ” đồng nghĩa toàn bộ cấu trúc tệp đã được lưu bền vững như `rws`.

### 2.3. Độ bền không đồng nghĩa tính nguyên tử hay giao dịch

Theo đặc tả Java 17, với **thiết bị lưu trữ cục bộ**, khi một phương thức trên `RandomAccessFile` dùng `"rws"` hoặc `"rwd"` trả về thành công, các thay đổi thuộc phạm vi yêu cầu đồng bộ của phương thức đó đã được ghi xuống thiết bị. Đối với **thiết bị không phải local** (ví dụ một số hệ thống tệp mạng), API **không đưa ra bảo đảm tương tự**.

Dù dùng mode nào:

- Một chuỗi nhiều lời gọi `seek`, `writeInt`, `writeLong` **không biến thành một transaction nguyên tử**. Sự cố giữa chừng vẫn có thể để lại bản ghi chỉ cập nhật một phần.
- Đồng bộ thành công không thay thế cơ chế **lock**, journal, checksum, bản sao dự phòng hoặc protocol khôi phục nếu dữ liệu quan trọng.
- Đồng bộ có thể làm chậm ứng dụng vì tăng I/O ghi. Cân bằng độ bền và hiệu năng theo yêu cầu thực tế, đo trên môi trường mục tiêu.
- `"rw"` thiếu yêu cầu đồng bộ **trên từng lần ghi**; điều đó không có nghĩa dữ liệu chắc chắn chỉ nằm trong RAM hay chắc chắn mất sau sự cố.

### 2.4. So sánh với `FileChannel.force`

Với `"rw"`, một ứng dụng có thể **ghi nhiều lần rồi yêu cầu flush/force tại một thời điểm**:

~~~java
try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
    raf.seek(0);
    raf.writeInt(10);
    raf.writeInt(20);
    raf.getChannel().force(true); // Yêu cầu đẩy content + metadata xuống thiết bị.
}
~~~

- `raf.getChannel().force(true)` gần với yêu cầu đồng bộ **content và metadata**.
- `raf.getChannel().force(false)` gần với yêu cầu đồng bộ **content**.
- `"rws"` và `"rwd"` áp dụng yêu cầu ấy **cho từng I/O/cập nhật**, thay vì chờ lời gọi `force` có chủ đích. Đặc tả lưu ý các mode đồng bộ có thể hoạt động hiệu quả hơn so với việc tự `force` sau mỗi thao tác; điều này không xác định cách nào nhanh nhất khi gom nhiều thao tác.
- `force` cũng chịu giới hạn bảo đảm khi tệp không nằm trên thiết bị cục bộ. Mở tệp chỉ đọc không biến `force` thành thao tác ghi.

Xem đặc tả chính thức của `RandomAccessFile` và `FileChannel.force(boolean)` ở cuối bài.

## 3. Con trỏ byte: `seek`, `getFilePointer`, `length`, `setLength`

### 3.1. `seek(offset)` chỉ thay đổi vị trí; không tự ghi

~~~java
raf.seek(12L);                           // Đặt byte offset của lần I/O kế tiếp.
long position = raf.getFilePointer();   // 12
long size = raf.length();                // Kích thước thực của tệp.
~~~

Nếu gọi `seek(1000)` khi file chỉ dài 20 byte, **chưa có byte nào được thêm vào**. Chỉ khi thực sự **ghi** sau vị trí đó thì tệp mới có thể mở rộng. Phần byte nằm giữa dữ liệu cũ và vùng mới không nên được ứng dụng dựa vào nếu chưa tự định nghĩa/khởi tạo định dạng dữ liệu.

- Offset tính bằng **byte**, bắt đầu từ `0`; không phải thứ tự ký tự, dòng hoặc bản ghi.
- `seek(-1)` không hợp lệ, ném `IOException`.
- `read()` ở cuối tệp trả về **`-1`**; `readInt()`, `readLong()`, `readFully(...)` khi không còn đủ byte cần thiết ném **`EOFException`**.
- `read(byte[])` có thể đọc **ít byte hơn số lượng mong muốn**. Nếu cần đúng N byte hoặc báo lỗi EOF, dùng `readFully(buffer)`.

### 3.2. Đọc/ghi kiểu nguyên thủy và thứ tự byte

`RandomAccessFile` hỗ trợ `readInt`, `readLong`, `readBoolean`, `readDouble`... và `writeInt`, `writeLong`, `writeBoolean`, `writeDouble`...

~~~java
raf.seek(0);
raf.writeInt(0x01020304);  // Ghi đúng bốn byte: 01 02 03 04.
raf.seek(0);
int number = raf.readInt(); // 0x01020304.
~~~

Các phương thức `DataInput`/`DataOutput` đa byte sử dụng **big-endian**. Kích thước điển hình: `byte` = 1, `short` = 2, `int` = 4, `long` = 8 byte. Không coi `writeInt(1000)` là ghi chuỗi văn bản `"1000"` vào tệp.

**Chuỗi ký tự:** `writeUTF` ghi độ dài (2 byte) kèm nội dung theo **modified UTF-8** của giao thức `DataOutput`, nên phải đọc lại bằng `readUTF` theo đúng định dạng; đây không phải raw UTF-8 để tiện mở bằng text editor. Dữ liệu mã hóa trong một lần `writeUTF` bị giới hạn bởi độ dài được biểu diễn trong **2 byte không dấu** (tối đa 65.535 byte modified UTF-8). `writeChars` ghi từng UTF-16 code unit thành 2 byte, còn `writeBytes` bỏ phần cao của mỗi `char`; không nên dùng hai cách này để tùy tiện ghi tiếng Việt/Unicode vào tệp văn bản. Với định dạng văn bản UTF-8 chuẩn, hãy chủ động chuyển chuỗi bằng `StandardCharsets.UTF_8`, lưu độ dài hoặc dùng giao thức framing nhất quán khi đọc.

### 3.3. `setLength(newLength)`: cắt ngắn/mở rộng tệp

`setLength(0)` dùng để làm tệp rỗng; `setLength(8)` giữ đúng 8 byte đầu khi tệp đang dài hơn 8. Nếu tăng độ dài, **nội dung các byte mới chưa được đặc tả**, nên không coi đó là vùng giá trị đã được khởi tạo theo ý ứng dụng.

Khi `setLength(newLength)` trả về, con trỏ được **giới hạn không vượt quá `newLength`**; nếu con trỏ cũ lớn hơn độ dài mới thì con trỏ về đúng vị trí cuối mới. `setLength` yêu cầu mở tệp ở chế độ cho phép ghi và có thể ném `IOException` khi hệ thống tệp không thực hiện được.

### 3.4. Đọc, cập nhật, nối thêm: đừng nhầm với append mode

~~~java
raf.seek(0);            // Ghi đè từ đầu.
raf.writeInt(10);

raf.seek(4);            // Cập nhật một vùng ở offset 4.
raf.writeInt(20);

raf.seek(raf.length()); // Tự đưa con trỏ tới EOF để append.
raf.writeInt(30);
~~~

Đoạn này là minh họa trong một đối tượng `raf` đang mở cho ghi. **Không tồn tại một mode `"a"` hợp lệ** cho `RandomAccessFile`. Việc `seek(length())` rồi `write...` cũng **không tự là append nguyên tử** khi nhiều luồng hoặc tiến trình đồng thời sử dụng cùng tệp.

## 4. Quy tắc chọn chế độ theo nhu cầu

| Tình huống | Lựa chọn thường phù hợp | Vì sao |
|---|---|---|
| Kiểm tra header, đọc bản ghi tại offset đã biết | `"r"` | Chặn thao tác ghi nhầm qua instance |
| Tạo/cập nhật tệp thực hành, nội dung có thể tạo lại | `"rw"` | Đọc/ghi linh hoạt, chi phí đồng bộ từng lần thấp |
| Ghi đè vùng dữ liệu đã tồn tại; cần yêu cầu đồng bộ dữ liệu từng lần | `"rwd"` | Bảo đảm đồng bộ content khi có thiết bị local |
| Tạo bản ghi làm tăng kích thước/đổi metadata; cần yêu cầu đồng bộ chặt hơn | `"rws"` | Đồng bộ cả content và metadata |
| Nhiều thay đổi cần ghi theo từng đợt | `"rw"` + `getChannel().force(...)` | Tự chọn thời điểm yêu cầu đồng bộ, và cần thiết kế quy trình lỗi/crash riêng |

**Không có chế độ tốt nhất cho mọi bài toán.** Chọn theo yêu cầu độ bền, mô hình lỗi, đặc tính thiết bị và khả năng khôi phục. Nếu chỉ muốn đọc/ghi tuần tự văn bản, `Files.newBufferedReader`, `Files.newBufferedWriter` hoặc API stream có thể trực quan hơn.

## 5. Chương trình Java 17+ chạy được: ghi đè, append, truncate và mở `r`

Lưu chính xác tệp **`RandomAccessFileModesDemo.java`**:

~~~java
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class RandomAccessFileModesDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("raf-modes-demo-", ".bin");

        try {
            try (RandomAccessFile raf =
                         new RandomAccessFile(file.toFile(), "rw")) {
                raf.writeInt(10); // Offset 0..3
                raf.writeInt(20); // Offset 4..7
                System.out.println("initial: length=" + raf.length()
                        + ", pointer=" + raf.getFilePointer());

                raf.seek(4);
                raf.writeInt(99); // Thay giá trị int thứ hai, không đổi độ dài.
                System.out.println("update: length=" + raf.length()
                        + ", pointer=" + raf.getFilePointer());

                raf.seek(0);
                System.out.println("values: " + raf.readInt()
                        + ", " + raf.readInt());

                raf.seek(raf.length()); // Chuyển con trỏ đến EOF.
                raf.writeByte(7);       // Thêm 1 byte.
                System.out.println("append: length=" + raf.length());

                raf.setLength(8);       // Cắt bỏ byte vừa append.
                System.out.println("truncate: length=" + raf.length()
                        + ", pointer=" + raf.getFilePointer());
            }

            try (RandomAccessFile reader =
                         new RandomAccessFile(file.toFile(), "r")) {
                System.out.println("read-only: " + reader.readInt()
                        + ", " + reader.readInt());
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp nguồn:

~~~bash
javac RandomAccessFileModesDemo.java
java RandomAccessFileModesDemo
~~~

**Stdout dự kiến khi tất cả I/O thành công:**

~~~text
initial: length=8, pointer=8
update: length=8, pointer=8
values: 10, 99
append: length=9
truncate: length=8, pointer=8
read-only: 10, 99
~~~

Diễn giải:

1. Hai lần `writeInt` ghi 8 byte; con trỏ kết thúc ở offset 8.
2. `seek(4)` đưa con trỏ về đầu số nguyên thứ hai; `writeInt(99)` ghi đè 4 byte, vẫn dài 8.
3. `seek(0)` và hai lần `readInt` khôi phục các số `10`, `99`.
4. `seek(length())` chọn offset 8; `writeByte(7)` thêm đúng một byte, tổng độ dài 9.
5. `setLength(8)` cắt một byte; con trỏ vừa ở offset 9 nay bị giới hạn về 8.
6. Đóng instance `"rw"` rồi mở `"r"` vẫn đọc được hai số; `"r"` không cho phép ghi.

`try-with-resources` bảo đảm `close()` được gọi khi thoát khối, kể cả khi có exception. Việc dùng tệp tạm riêng tránh ghi đè dữ liệu của người dùng. Nếu xảy ra lỗi I/O, chương trình có thể không in đủ những dòng trên và sẽ báo ngoại lệ; đây là hành vi cần được xử lý trong ứng dụng thật.

## 6. Dùng offset cho bản ghi kích thước cố định

Giả sử tệp lưu mỗi bản ghi:

~~~text
Record 0: [id: int (4 byte)] [balanceCents: long (8 byte)] = 12 byte
Record 1: [id: int (4 byte)] [balanceCents: long (8 byte)] = 12 byte
Record 2: [id: int (4 byte)] [balanceCents: long (8 byte)] = 12 byte
...
~~~

Byte offset của bản ghi `i` (**đếm từ 0**) là `i * 12`. Muốn đọc và sửa số dư bản ghi thứ ba (`i=2`), bắt đầu tại offset 24; trường `balanceCents` nằm ở offset 28.

Đoạn minh họa có thể đưa vào một class tiện ích; giả định file dùng đúng cấu trúc nhị phân nói trên:

~~~java
private static final int RECORD_SIZE =
        Integer.BYTES + Long.BYTES; // 12

static long readBalance(RandomAccessFile raf, long index)
        throws IOException {
    if (index < 0) {
        throw new IllegalArgumentException("index must be >= 0");
    }
    long offset = Math.multiplyExact(index, RECORD_SIZE);
    if (offset > raf.length() - RECORD_SIZE) {
        throw new java.io.EOFException("record does not exist");
    }
    raf.seek(offset + Integer.BYTES);
    return raf.readLong();
}

static void updateBalance(RandomAccessFile raf, long index, long cents)
        throws IOException {
    if (index < 0) {
        throw new IllegalArgumentException("index must be >= 0");
    }
    long offset = Math.multiplyExact(index, RECORD_SIZE);
    if (offset > raf.length() - RECORD_SIZE) {
        throw new java.io.EOFException("record does not exist");
    }
    raf.seek(offset + Integer.BYTES);
    raf.writeLong(cents);
}
~~~

**Lưu ý để dùng đúng:**

- Kiểm tra chỉ số âm; dùng `Math.multiplyExact` để tránh **tràn số** khi tính byte offset.
- Kiểm tra phạm vi trước khi update để không vô tình mở rộng tệp ở offset sai. Bản ghi được đánh số từ 0.
- `updateBalance` yêu cầu file mở ở `"rw"`, `"rwd"` hoặc `"rws"`; đối tượng chỉ đọc `"r"` sẽ không ghi được.
- Cần định nghĩa thống nhất layout (thứ tự trường, số byte, endianness, version), tránh dùng `String.length()` để tính offset chuỗi UTF-8 có độ dài biến thiên.
- Việc ghi `long` 8 byte **không phải một giao dịch** với các trường còn lại; cân nhắc checksum, log hoặc chiến lược cập nhật khi dữ liệu quan trọng.
- Đoạn trên không cung cấp khóa đồng thời; nếu nhiều tác nhân cập nhật cùng bản ghi, cần bổ sung đồng bộ phù hợp.

Nếu bản ghi có kích thước không cố định (ví dụ chuỗi UTF-8 dài ngắn khác nhau), `i * RECORD_SIZE` không dùng được; cần **index offset** hoặc định dạng có trường độ dài rõ ràng.

## 7. Đồng thời, `FileChannel`, khóa và rủi ro vận hành

### 7.1. Con trỏ là trạng thái chung trong cùng đối tượng

Trong một instance `RandomAccessFile`, thao tác `seek` rồi `read`/`write` là **một chuỗi hành động**, không phải thao tác nguyên tử ở cấp ứng dụng. Ví dụ:

~~~text
Thread A: seek(100)
Thread B: seek(200)
Thread A: writeInt(42)  // Có thể ghi từ offset 200 nếu không phối hợp.
~~~

Tránh dùng cùng một instance ở nhiều luồng mà không có kiểm soát truy cập. Với nhiều tiến trình, chỉ `synchronized` của một JVM không thể bảo vệ tiến trình khác. Có thể xem xét `FileChannel.lock()`/`tryLock()`, nhưng phải hiểu phạm vi lock, hỗ trợ và quy tắc OS; file lock **không phải** một giao dịch và một số hệ thống chỉ áp dụng theo cơ chế advisory.

### 7.2. `RandomAccessFile.getChannel()` chia sẻ vị trí

`getChannel()` trả về một `FileChannel` gắn với cùng tệp. **`channel.position()` và `raf.getFilePointer()` là cùng vị trí logic**: di chuyển một bên làm thay đổi vị trí bên kia. Thay đổi kích thước tệp qua bên nào cũng ảnh hưởng đến bên còn lại.

Vì vậy, nếu trộn `raf.seek(...)` và `channel.position(...)` trong một đoạn xử lý, phải có quy tắc kiểm soát con trỏ rõ ràng. Đóng `RandomAccessFile` cũng đóng channel liên kết; ngược lại, đóng channel của nó cũng khiến stream tương ứng bị đóng.

### 7.3. Quy tắc lỗi và bảo vệ dữ liệu

- Dùng `try-with-resources` để đóng handle; không giữ tệp mở vô hạn.
- Bắt `EOFException` riêng khi đọc record không đủ byte; phân biệt với `IOException` do quyền, thiết bị hoặc tệp hỏng.
- Dùng đường dẫn tin cậy và kiểm tra quyền trước khi cho người dùng chọn file để ghi. Mở `"rw"` trên tệp đang có dữ liệu có thể ghi đè ngay từ offset 0.
- Đừng dựa vào `"rws"`/`"rwd"` để tự xử lý **race condition**, ghi trùng, bản ghi dang dở, file format corruption hoặc ghi nhiều bước cần rollback.
- Nếu cần append đồng thời giữa nhiều luồng/tiến trình, thiết kế lock/protocol rõ ràng hoặc chọn API có bảo đảm append phù hợp thay vì tự coi `seek(length())` + `write` là nguyên tử.

## 8. Các nhầm lẫn phổ biến và cách sửa

| Nhầm lẫn | Cách hiểu đúng |
|---|---|
| “`RandomAccessFile` chỉ dùng để đọc ngẫu nhiên” | Nó có thể **đọc và ghi** nếu mở `rw/rws/rwd`. |
| “`rw` nghĩa là read/write và luôn append” | `rw` là quyền truy cập; vị trí ghi do **file pointer** quyết định. |
| “Mở `rw` sẽ xóa tệp cũ” | Không tự truncate; chỉ `setLength(0)` hoặc thao tác ghi theo offset mới thay đổi dữ liệu. |
| “`seek(1000)` làm tệp dài 1000 byte” | `seek` chỉ di chuyển pointer; tệp mở rộng khi ghi (hoặc gọi `setLength`). |
| “`rwd` đồng bộ cả file metadata” | `rwd` yêu cầu đồng bộ **content**; `rws` yêu cầu **content + metadata**. |
| “`rws` khiến nhiều lần ghi thành transaction” | Không có bảo đảm transaction/atomicity cho chuỗi lời gọi. |
| “`readInt` đọc số nguyên ở dạng chữ số ASCII” | `readInt` đọc **4 byte binary big-endian**. |
| “`read(byte[])` luôn trả về đủ buffer” | Có thể đọc thiếu; dùng `readFully` khi yêu cầu đúng kích thước. |
| “`writeUTF` ghi raw UTF-8 theo mọi chuẩn” | Đó là **modified UTF-8 có prefix 2 byte**, cần đọc bằng `readUTF` tương ứng. |
| “Lấy channel có con trỏ độc lập” | Channel và `RandomAccessFile` **chia sẻ vị trí**. |
| “`rwd/rws` đồng nghĩa dữ liệu trên network FS luôn an toàn sau crash” | Bảo đảm ghi xuống thiết bị được đặc tả cho **local storage**; remote FS không có bảo đảm tương tự. |

## 9. Tự kiểm tra

1. Mở tệp 100 byte bằng `"rw"`, gọi `seek(20)` rồi `writeInt(123)`. Kích thước mới? **100 byte** (ghi đè các byte 20–23); con trỏ ở **24**.
2. Mở tệp mới bằng `"rw"` rồi `writeLong(5L)`. Kích thước? **8 byte**, con trỏ ở **8**.
3. File đang dài 8 byte. Gọi `seek(100)` mà chưa ghi. `length()`? **8**, còn `getFilePointer()` là **100**.
4. Mở file chỉ với `"r"` rồi gọi `writeByte(1)`. Điều gì xảy ra? **`IOException`**.
5. Ở chế độ nào Java yêu cầu đồng bộ cả nội dung và metadata khi cập nhật? **`"rws"`**.
6. `"rwd"` khác `"rw"` chủ yếu ở đâu? **`"rwd"` yêu cầu đồng bộ nội dung trên từng cập nhật**, còn `"rw"` không yêu cầu như vậy.
7. Nếu sau một loạt lời gọi `write...` trong `"rw"` muốn yêu cầu đồng bộ content và metadata trước khi tiếp tục, dùng gì? **`raf.getChannel().force(true)`**.
8. Hai luồng dùng cùng một `RandomAccessFile`, mỗi luồng `seek` rồi `write`. Có tự an toàn không? **Không**; phải phối hợp để giữ tính đúng đắn của con trỏ và vùng ghi.

## 10. Tài liệu chính thức

- [Java SE 17 — RandomAccessFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/RandomAccessFile.html): bốn mode, `seek`, `setLength`, `getFilePointer`, `read`/`write`.
- [Java SE 17 — FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): `force(boolean)`, `position`, file lock và các giới hạn đồng bộ.
- [Java SE 17 — DataInput](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInput.html) và [DataOutput](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataOutput.html): bố cục binary, big-endian, `readFully`, modified UTF-8.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-access-002">File pointer, seek và getFilePointer</a>

<details>
<summary>Xem chi tiết</summary>

# File pointer, `seek` và `getFilePointer` trong Java

## 1. Vị trí con trỏ tệp là gì?

Trong **truy cập ngẫu nhiên** (*random access*), chương trình có thể đọc hoặc ghi tại một vị trí byte bất kỳ của tệp mà không cần đọc tuần tự tất cả các byte phía trước. Với `java.io.RandomAccessFile`, vị trí đang được dùng để đọc/ghi được gọi là **file pointer** (con trỏ tệp).

File pointer là **offset tính theo byte từ đầu tệp**, bắt đầu tại **0**. Nó không phải địa chỉ bộ nhớ, không phải số dòng, cũng không mặc nhiên là vị trí ký tự trong chuỗi Unicode.

Ví dụ tệp có năm byte:

```text
Offset:       0     1     2     3     4
Nội dung:    10    20    30    40    50
                              ^
                       file pointer = 3
```

Nếu gọi `read()` ở pointer 3, phương thức đọc byte giá trị 40 (thập phân) rồi dịch pointer sang 4. Nếu gọi `seek(1)`, pointer chuyển về 1; lần đọc tiếp theo lấy byte tại offset 1.

Khái niệm cốt lõi:

- **`seek(position)`** chủ động đặt vị trí đọc/ghi tuyệt đối.
- **`getFilePointer()`** trả về vị trí hiện tại.
- **Lệnh đọc/ghi thành công** dịch chuyển pointer theo số byte thực tế đã đọc/ghi.
- **`length()`** cho biết độ dài tệp tính bằng byte, độc lập với việc pointer đang ở đâu.

API này phù hợp với các tệp nhị phân có cấu trúc rõ ràng như bản ghi độ dài cố định, vùng header, chỉ mục và dữ liệu cần cập nhật tại offset xác định.

## 2. `RandomAccessFile` và các phương thức liên quan

Khởi tạo bằng một đường dẫn và chế độ truy cập:

```java
try (RandomAccessFile raf = new RandomAccessFile("data.bin", "rw")) {
    // Đọc/ghi tại các offset bằng byte.
}
```

Đây là **đoạn mã minh họa**; trong chương trình đầy đủ cần import `java.io.RandomAccessFile` và xử lý `IOException`.

Các chế độ thường gặp:

| Mode | Ý nghĩa |
| --- | --- |
| `"r"` | Chỉ đọc; tệp phải tồn tại, thao tác ghi bị từ chối |
| `"rw"` | Đọc và ghi; có thể tạo tệp nếu chưa có |
| `"rws"` | Đọc/ghi với yêu cầu đồng bộ nội dung và metadata xuống thiết bị lưu trữ |
| `"rwd"` | Đọc/ghi với yêu cầu đồng bộ các thay đổi nội dung xuống thiết bị lưu trữ |

`"rws"` và `"rwd"` có chi phí I/O cao hơn tùy hệ thống; chúng không thay thế một thiết kế giao dịch an toàn khi mất điện hoặc xử lý nhiều thao tác liên quan.

| Phương thức | Hành vi quan trọng |
| --- | --- |
| `long getFilePointer()` | Lấy offset byte hiện tại |
| `void seek(long pos)` | Đặt pointer tới offset tuyệt đối `pos` |
| `long length()` | Lấy kích thước tệp hiện tại theo byte |
| `int read()` | Đọc một byte không dấu (0–255) hoặc trả về `-1` tại EOF |
| `void readFully(byte[] b)` | Đọc đủ số byte được yêu cầu hoặc ném `EOFException` |
| `void write(int b)` | Ghi tám bit thấp của số nguyên và dịch pointer một byte |
| `void writeInt(int value)` | Ghi một số nguyên 32-bit thành **4 byte**, theo thứ tự big-endian |
| `int readInt()` | Đọc **4 byte** thành số nguyên; thiếu byte thì ném `EOFException` |
| `void setLength(long newLength)` | Cắt ngắn hoặc mở rộng độ dài logic của tệp |

`seek` yêu cầu vị trí không âm. `seek(-1)` gây `IOException`. Tất cả `seek`, `getFilePointer`, `length`, đọc và ghi đều có thể ném `IOException` do vấn đề tệp/hệ thống. Luôn đóng tài nguyên bằng `try-with-resources`.

## 3. File pointer di chuyển như thế nào?

Hình dung tệp ban đầu rỗng, pointer tại offset 0.

| Thao tác | Kết quả | Pointer sau thao tác | `length()` sau thao tác |
| --- | --- | ---: | ---: |
| Mở tệp rỗng | Chưa có byte | 0 | 0 |
| `writeInt(2026)` | Ghi 4 byte tại offset 0–3 | 4 | 4 |
| `writeByte(65)` | Ghi 1 byte tại offset 4 | 5 | 5 |
| `seek(0)` | Quay về đầu tệp, không đọc/ghi | 0 | 5 |
| `readInt()` | Đọc 4 byte | 4 | 5 |
| `readUnsignedByte()` | Đọc một byte trị số 65 | 5 | 5 |
| `read()` tại EOF | Trả về `-1` | 5 | 5 |
| `seek(4)` | Quay về vị trí byte thứ năm | 4 | 5 |
| `writeByte(66)` | **Ghi đè** byte tại offset 4 | 5 | 5 |

**`seek` tự nó không đọc hay ghi byte nào**, vì thế cũng không tự tăng độ dài tệp. Chỉ việc dịch pointer về trước rồi đọc/ghi ở vị trí đó mới ảnh hưởng đến dữ liệu.

Một số hiểu nhầm cần tránh:

- **Offset khác chỉ số “ký tự”**: văn bản UTF-8 có ký tự dài nhiều byte.
- **Byte thứ năm có offset 4**, không phải 5.
- Sau khi đọc 4 byte từ offset 0, pointer ở **4**, tức vị trí sẽ dùng cho thao tác tiếp theo.
- **Ghi tại offset hiện có thường ghi đè**, không chèn và đẩy dữ liệu phía sau sang phải.

## 4. Ví dụ Java 17+ chạy độc lập: quan sát từng lần thay đổi pointer

Chương trình tạo một tệp tạm, ghi hai giá trị, di chuyển và đọc pointer, sau đó ghi đè byte cuối. Tệp tạm được đóng và dọn dẹp.

**Tệp `FilePointerDemo.java`**

```java
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class FilePointerDemo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("file-pointer-", ".bin");

        try {
            try (RandomAccessFile raf =
                     new RandomAccessFile(path.toFile(), "rw")) {

                raf.setLength(0);
                raf.writeInt(2026);
                System.out.println("Sau writeInt: " + raf.getFilePointer());

                raf.writeByte(65);
                System.out.println("Sau writeByte: " + raf.getFilePointer());

                raf.seek(0);
                System.out.println("Sau seek(0): " + raf.getFilePointer());

                int year = raf.readInt();
                System.out.println("Gia tri int: " + year);
                System.out.println("Sau readInt: " + raf.getFilePointer());

                int lastByte = raf.readUnsignedByte();
                System.out.println("Byte thu nam: " + lastByte);
                System.out.println("Read EOF: " + raf.read());
                System.out.println("Pointer tai EOF: " + raf.getFilePointer());

                raf.seek(4);
                System.out.println("Sau seek(4): " + raf.getFilePointer());

                raf.writeByte(66);
                System.out.println("Sau ghi de: " + raf.getFilePointer());

                raf.seek(0);
                System.out.println(
                    "Du lieu cuoi: " + raf.readInt()
                    + ", " + raf.readUnsignedByte());
                System.out.println("Do dai file: " + raf.length());
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac FilePointerDemo.java
java FilePointerDemo
```

**Stdout chính xác trong điều kiện thao tác tệp thành công:**

```text
Sau writeInt: 4
Sau writeByte: 5
Sau seek(0): 0
Gia tri int: 2026
Sau readInt: 4
Byte thu nam: 65
Read EOF: -1
Pointer tai EOF: 5
Sau seek(4): 4
Sau ghi de: 5
Du lieu cuoi: 2026, 66
Do dai file: 5
```

Giải thích:

1. `writeInt(2026)` luôn ghi **4 byte** theo hợp đồng `DataOutput`, vì vậy pointer chuyển từ 0 sang 4.
2. `writeByte(65)` ghi một byte và tăng pointer lên 5.
3. `seek(0)` quay về đầu, sau đó `readInt()` tiêu thụ bốn byte và tái tạo đúng số 2026.
4. `readUnsignedByte()` đọc byte 65 tại offset 4 rồi đưa pointer tới EOF = 5.
5. `read()` tại EOF trả về `-1` và không tiêu thụ byte nào; pointer vẫn là 5.
6. `seek(4)` và `writeByte(66)` thay thế giá trị 65 thành 66 tại cùng offset. Không có byte được chèn thêm và `length()` vẫn bằng 5.

Chương trình này minh họa bản chất **đọc/ghi theo vị trí byte**, khác với đọc luồng chỉ tiến về phía trước.

## 5. `seek` vượt EOF: hợp lệ nhưng cần thận trọng

**EOF** (*end of file*) nằm tại offset `length()`. Với tệp dài 5 byte, vùng byte đang tồn tại có offset từ 0 đến 4; pointer tại 5 nghĩa là vị trí ngay sau byte cuối.

`RandomAccessFile.seek(pos)` **có thể đặt pointer vượt quá EOF** nếu `pos` không âm. Ví dụ:

```java
// Giả sử raf trỏ tới tệp dài đúng 5 byte.
raf.seek(10);
System.out.println(raf.getFilePointer()); // 10
System.out.println(raf.length());         // 5
```

Đây chỉ là đoạn mã minh họa, cần một `raf` đang mở. `seek(10)` **không** làm tệp dài 10 byte. Đọc tại vị trí sau EOF bằng `read()` trả về `-1`. Nếu sau `seek(10)` gọi `writeByte(7)`, tệp được kéo dài để chứa byte mới tại offset 10, và độ dài cuối thành **11**.

**Không được giả định nội dung vùng trống giữa EOF cũ và byte mới luôn bằng zero.** Hợp đồng Java không bảo đảm giá trị các byte trung gian khi mở rộng tệp; hành vi cụ thể phụ thuộc nền tảng và hệ thống tệp. Nếu định dạng dữ liệu yêu cầu những byte có giá trị xác định, hãy ghi các byte ấy một cách tường minh.

`setLength(newLength)` có quy tắc riêng:

- Nếu `newLength` nhỏ hơn độ dài cũ, tệp bị cắt ngắn. Nếu pointer cũ lớn hơn độ dài mới, pointer được đưa về bằng độ dài mới.
- Nếu `newLength` lớn hơn độ dài cũ, tệp được mở rộng nhưng **giá trị các byte mới không được bảo đảm**.
- Nếu chỉ thay đổi độ dài theo `setLength` và pointer vốn không vượt qua độ dài mới, pointer giữ nguyên.

Lỗi hay gặp là tưởng `seek(1000)` “cấp phát” 1.000 byte hoặc nghĩ `setLength(1000)` luôn khởi tạo phần mở rộng bằng số 0.

## 6. Ví dụ thực tế: tìm và cập nhật bản ghi độ dài cố định

Truy cập ngẫu nhiên có hiệu quả khi mỗi bản ghi có **kích thước byte cố định**. Giả sử mỗi bản ghi gồm:

| Trường | Kiểu ghi | Kích thước |
| --- | --- | ---: |
| `id` | `writeInt` | 4 byte |
| `quantity` | `writeInt` | 4 byte |
| **Tổng một bản ghi** |  | **8 byte** |

Với chỉ số bản ghi `index` bắt đầu từ 0, vị trí đầu bản ghi là:

\[
\text{offset} = \text{index} \times 8
\]

Ví dụ bản ghi thứ ba (index = 2) bắt đầu tại offset **16**. Công thức hoạt động vì mọi bản ghi có cùng kích thước 8 byte, bất kể giá trị số nguyên. Nếu dùng chuỗi UTF-8 độ dài thay đổi, không thể tiếp tục nhân chỉ số với 8 như thế mà không có quy định đóng gói khác.

**Tệp `FixedRecordDemo.java`**

```java
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class FixedRecordDemo {
    private static final int RECORD_SIZE = 8;

    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("records-", ".bin");

        try {
            try (RandomAccessFile raf =
                     new RandomAccessFile(path.toFile(), "rw")) {
                raf.setLength(0);

                raf.writeInt(101);
                raf.writeInt(5);

                raf.writeInt(102);
                raf.writeInt(7);

                raf.writeInt(103);
                raf.writeInt(9);

                long thirdOffset = 2L * RECORD_SIZE;
                raf.seek(thirdOffset);
                int thirdId = raf.readInt();
                int thirdQuantity = raf.readInt();
                System.out.println("Ban ghi thu ba: "
                        + thirdId + ", " + thirdQuantity);
                System.out.println("Pointer sau doc: "
                        + raf.getFilePointer());

                long secondQuantityOffset = 1L * RECORD_SIZE + 4;
                raf.seek(secondQuantityOffset);
                raf.writeInt(70);

                raf.seek(1L * RECORD_SIZE);
                int secondId = raf.readInt();
                int secondQuantity = raf.readInt();
                System.out.println("Ban ghi thu hai: "
                        + secondId + ", " + secondQuantity);
                System.out.println("Kich thuoc tep: " + raf.length());
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Chạy:

```bash
javac FixedRecordDemo.java
java FixedRecordDemo
```

**Stdout chính xác khi thao tác thành công:**

```text
Ban ghi thu ba: 103, 9
Pointer sau doc: 24
Ban ghi thu hai: 102, 70
Kich thuoc tep: 24
```

**Vì sao như vậy?** Có ba bản ghi, mỗi bản ghi 8 byte, tổng tệp 24 byte. `seek(16)` di chuyển tới bản ghi thứ ba; hai lần `readInt()` đọc 8 byte nên pointer ở 24. Trường `quantity` của bản ghi thứ hai bắt đầu tại offset `1 * 8 + 4 = 12`; `seek(12)` rồi `writeInt(70)` chỉ ghi đè byte 12–15, không thay đổi các bản ghi khác và không đổi chiều dài tệp.

Khi áp dụng thực tế, cần tính offset bằng `long` ngay từ đầu (ví dụ `(long) index * RECORD_SIZE`) để tránh tràn số nguyên 32-bit; kiểm tra `index >= 0`, `offset` không tràn, và `offset + RECORD_SIZE <= raf.length()` trước khi đọc một bản ghi hiện có. Nếu tệp có thể bị bên khác sửa song song, kết quả kiểm tra chiều dài cũng có thể hết hạn trước lúc đọc.

## 7. EOF, đọc thiếu byte và ngoại lệ

`read()` có thể trả về `-1` khi không còn byte. Nhưng **không phải phương thức đọc nào cũng dùng `-1`**:

- `readInt()` cần đủ bốn byte; thiếu bất cứ byte nào sẽ ném `EOFException`.
- `readUnsignedByte()` cần đúng một byte; gọi tại EOF cũng ném `EOFException`.
- `readFully(byte[])` có nghĩa là phải lấy đủ số byte yêu cầu; nếu tệp kết thúc sớm sẽ ném `EOFException`.
- `read(byte[])` có thể trả về một số byte ít hơn độ dài mảng; không được mặc định lần đọc luôn điền đầy mảng.

Ví dụ nếu tệp chỉ có ba byte, thực hiện `raf.seek(0); raf.readInt();` sẽ gây `EOFException` do `readInt` cần bốn byte. Khi một lệnh đọc nhiều byte thất bại, không được mặc nhiên suy ra pointer quay lại vị trí trước khi đọc: một phần byte có thể đã được tiêu thụ. Nếu cần thao tác đọc “tất cả hoặc không gì cả”, chương trình phải thiết kế xử lý và phục hồi vị trí phù hợp.

Một cách phòng ngừa khi đọc bản ghi cố định là kiểm tra độ dài tệp cùng các offset đã tính, sau đó **vẫn bắt và xử lý lỗi I/O khi đọc thật**.

## 8. Pointer khi đọc/ghi kiểu dữ liệu và chuỗi

`RandomAccessFile` triển khai các phương thức của `DataInput` và `DataOutput`. Vì vậy độ dịch pointer phụ thuộc **số byte được mã hóa**, không phải số lượng lệnh hay ký tự.

| Lệnh | Số byte ghi/đọc bình thường |
| --- | ---: |
| `writeByte(int)` / `readUnsignedByte()` | 1 |
| `writeShort(int)` / `readShort()` | 2 |
| `writeChar(int)` / `readChar()` | 2 |
| `writeInt(int)` / `readInt()` | 4 |
| `writeLong(long)` / `readLong()` | 8 |
| `writeFloat(float)` / `readFloat()` | 4 |
| `writeDouble(double)` / `readDouble()` | 8 |
| `writeUTF(String)` / `readUTF()` | Độ dài **thay đổi** |

Các kiểu nguyên thủy nhiều byte được mã hóa theo **big-endian** trong `RandomAccessFile`. Ví dụ số nguyên `0x01020304` được ghi thành bốn byte `01 02 03 04`; con trỏ tăng 4.

`writeUTF` sử dụng **modified UTF-8** và phần tiền tố dài **2 byte** chứa độ dài vùng dữ liệu được mã hóa. Số byte ghi **không đơn giản bằng** `string.length()` hay `string.getBytes(StandardCharsets.UTF_8).length`. Nếu cần truy cập trực tiếp phần tử thứ N trong một tệp chuỗi dài thay đổi, phải có chỉ mục offset, bảng độ dài hoặc một định dạng bản ghi cố định được định nghĩa trước.

Khi phối hợp nhiều thao tác đọc/ghi trên cùng tệp, phải ghi nhớ định dạng nhị phân và offset của từng trường. Đọc `writeInt` bằng `readShort` không “hiểu” tự động cấu trúc; nó chỉ đọc hai byte và để pointer ở vị trí tương ứng.

## 9. `RandomAccessFile`, `FileChannel` và tính đồng thời

`RandomAccessFile` là API dễ học khi cần đọc/ghi các kiểu dữ liệu ngay tại một vị trí. Với NIO, `FileChannel` cho phép làm việc với `ByteBuffer`, thao tác vị trí `position(long)`, hoặc đọc/ghi theo vị trí tường minh như `read(buffer, position)` và `write(buffer, position)`.

Nếu lấy channel bằng `raf.getChannel()` thì **vị trí của channel và file pointer của `RandomAccessFile` có liên hệ**. Thay đổi vị trí trên một bên hoặc đọc/ghi làm thay đổi vị trí cũng có thể ảnh hưởng đến vị trí nhìn thấy từ bên kia. Không nên trộn các thao tác trên cùng handle rồi giả định có hai con trỏ độc lập.

Ngoài ra, **file pointer là trạng thái thay đổi được**. Nếu nhiều luồng cùng chia sẻ một `RandomAccessFile` và thực hiện cặp lệnh `seek(offset)` → `writeInt(value)`, luồng khác có thể làm thay đổi pointer xen giữa, khiến lệnh ghi diễn ra ở offset sai. Để bảo toàn thao tác logic, cần cơ chế đồng bộ phù hợp, hoặc dùng API đọc/ghi theo vị trí tường minh khi thích hợp. Việc lựa chọn còn phụ thuộc yêu cầu nguyên tử, tính nhất quán và khóa tệp; không mặc định một file pointer dùng chung là an toàn trong ứng dụng đồng thời.

Thực hành tốt là sở hữu rõ tài nguyên qua `try-with-resources`, tránh dùng chung handle khi không có thiết kế đồng bộ và ghi rõ định dạng dữ liệu đang lưu trong tệp.

## 10. Những lỗi thường gặp và cách sửa

| Lỗi | Hệ quả | Cách đúng |
| --- | --- | --- |
| Nghĩ `seek(5)` nghĩa là tiến thêm 5 byte | Đọc/ghi tại vị trí sai | `seek` đặt **offset tuyệt đối**; muốn tiến từ vị trí hiện tại thì tính `getFilePointer() + delta` và kiểm tra tràn số |
| Dùng chỉ số bản ghi thay cho offset byte | Lệch dữ liệu | `seek(index * recordSize)` với phép tính `long` |
| Dùng `writeInt` nhưng nghĩ tăng một byte | Tính sai offset các trường | Ghi nhớ `int` chiếm 4 byte |
| Nghĩ `seek` tự kéo dài tệp | Độ dài không như dự đoán | Chỉ ghi hoặc `setLength` mới có thể làm dài tệp |
| Mong chờ `writeByte` “chèn” byte | Ghi đè dữ liệu hiện có | Thiết kế quy trình dịch dữ liệu khi cần chèn |
| Luôn gọi `readInt` tại EOF | `EOFException` | Kiểm tra giới hạn, xử lý ngoại lệ |
| Coi ký tự là byte | Offset sai với văn bản Unicode | Theo dõi byte thực và charset/định dạng |
| Tin vùng mới mở rộng luôn bằng 0 | Dữ liệu không xác định | Khởi tạo giá trị cần thiết bằng thao tác ghi |
| Dùng chung một pointer từ nhiều luồng không đồng bộ | Đọc/ghi sai vị trí | Đồng bộ hoặc chuyển sang thiết kế có vị trí tường minh |
| Quên đóng `RandomAccessFile` | Rò rỉ descriptor/khóa tài nguyên | `try-with-resources` |

## 11. Tự kiểm tra hiểu bài

Giả sử một `RandomAccessFile` mới mở ở mode `"rw"` đang trỏ vào tệp rỗng:

```java
raf.writeLong(10L);       // A
raf.seek(2);              // B
raf.writeByte(99);        // C
raf.seek(8);              // D
int value = raf.read();   // E
```

Đây là đoạn mã để suy luận, không phải chương trình Java hoàn chỉnh. Nếu tất cả lệnh I/O thành công:

- Sau **A**, pointer là **8**, độ dài tệp là **8**.
- Sau **B**, pointer là **2**, độ dài vẫn **8**.
- Sau **C**, pointer là **3**, độ dài vẫn **8** vì chỉ ghi đè một byte tại offset 2.
- Sau **D**, pointer là **8**.
- Tại **E**, `value == -1` vì đang ở EOF; pointer vẫn là **8**.

Bài tập này cho thấy file pointer là **tọa độ byte của lần I/O tiếp theo**, còn `length()` là độ dài dữ liệu; hai giá trị thường trùng nhau khi ở EOF nhưng không đồng nhất về ý nghĩa.

## 12. Ghi nhớ khi sử dụng

1. **`getFilePointer()` trả về offset byte hiện tại**, kiểu `long`, không phải số dòng/ký tự.
2. **`seek(pos)` đặt offset tuyệt đối không âm**; có thể vượt EOF nhưng không tự thay đổi `length()`.
3. Đọc và ghi có thể dịch pointer theo **số byte đã thao tác**; ghi ở vị trí cũ là ghi đè.
4. Cẩn thận **EOF, `EOFException`, offset tràn số, dữ liệu ở vùng mở rộng, định dạng byte và I/O lỗi**.
5. Với bản ghi độ dài cố định, tính `offset = index * recordSize` bằng `long` và xác minh giới hạn.
6. Không chia sẻ file pointer giữa các luồng nếu thiếu phương án đồng bộ; nên đóng file bằng `try-with-resources`.

**Tài liệu chuẩn:** Java SE 17 API của `java.io.RandomAccessFile`, `java.io.DataInput`, `java.io.DataOutput` và `java.nio.channels.FileChannel`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-access-003">Đọc ghi dữ liệu nguyên thủy qua DataInput và DataOutput</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc ghi dữ liệu nguyên thủy qua DataInput và DataOutput trong Java

`DataInput` và `DataOutput` là hai giao diện trong `java.io` định nghĩa **cách đọc và ghi dữ liệu nhị phân có cấu trúc**: số nguyên, số thực, ký tự, `boolean` và chuỗi theo quy ước. Chúng đặc biệt hữu ích khi lưu bản ghi vào file hoặc truyền dữ liệu giữa các thành phần Java. Trong chủ đề **truy cập ngẫu nhiên**, `RandomAccessFile` là ví dụ quan trọng: lớp này thực thi cả hai giao diện, đồng thời có `seek(long)` để đọc hoặc sửa dữ liệu ở một vị trí byte cụ thể.

Bài học giải thích hợp đồng nhị phân, cách kết hợp stream và `RandomAccessFile`, cách thiết kế bản ghi có độ dài cố định, kiểm soát sai lệch định dạng, và những giới hạn của việc cập nhật file tại chỗ.

## 1. Vì sao cần DataInput và DataOutput?

Một file nhị phân không tự mô tả kiểu dữ liệu. Ví dụ dãy byte `00 00 00 2A` có thể biểu diễn số nguyên 42 theo thứ tự byte big-endian, nhưng chương trình phải **biết trước** đây là một `int` để đọc đúng. Nếu chỉ ghi các byte rồi đoán kiểu khi đọc, kết quả rất dễ sai hoặc phát sinh lỗi thiếu dữ liệu.

Hai giao diện đóng vai trò một **hợp đồng về cấu trúc và thứ tự trường**:

- `DataOutput` cung cấp `writeInt`, `writeLong`, `writeDouble`, `writeBoolean`, `writeChar`, `writeUTF` và các phương thức khác.
- `DataInput` cung cấp các phương thức đối ứng `readInt`, `readLong`, `readDouble`, `readBoolean`, `readChar`, `readUTF`, cùng `readFully` và `skipBytes`.
- Chương trình **tự quyết định schema**: trường nào xuất hiện trước, ý nghĩa và độ dài từng trường, phiên bản format và số bản ghi.
- Cả hai là **interface**, không phải stream có thể tạo bằng `new DataInput()` hay `new DataOutput()`.

Các hiện thực phổ biến:

| Lớp | DataInput | DataOutput | Kiểu truy cập |
| --- | --- | --- | --- |
| `DataInputStream` | Có | Không | Đọc tuần tự từ `InputStream`. |
| `DataOutputStream` | Không | Có | Ghi tuần tự vào `OutputStream`. |
| `RandomAccessFile` | Có | Có | Đọc/ghi và di chuyển vị trí trong file bằng `seek`. |

Cần phân biệt `DataInputStream`/`DataOutputStream` với `ObjectInputStream`/`ObjectOutputStream`: bài này ghi **giá trị nguyên thủy theo schema tự định nghĩa**, không dùng Java object serialization.

## 2. Quy ước byte: ghi gì thì phải đọc đúng thứ tự ấy

Các phương thức kiểu số của DataInput/DataOutput sử dụng **big-endian** (byte có trọng số cao đứng trước). Độ dài phổ biến:

| Cặp API | Số byte | Quy tắc và lưu ý |
| --- | ---: | --- |
| `writeBoolean` / `readBoolean` | 1 | Ghi 1 cho `true`, 0 cho `false`; khi đọc, byte khác 0 cũng được xem là `true`. |
| `writeByte` / `readByte` | 1 | Đọc dưới dạng `byte` có dấu (-128..127); `readUnsignedByte` cho 0..255. |
| `writeShort` / `readShort` | 2 | `short` có dấu; `readUnsignedShort` cho 0..65535. |
| `writeChar` / `readChar` | 2 | Một **UTF-16 code unit**, không nhất thiết là cả ký tự Unicode nếu là surrogate pair. |
| `writeInt` / `readInt` | 4 | `int` có dấu, big-endian. |
| `writeLong` / `readLong` | 8 | `long` có dấu, big-endian. |
| `writeFloat` / `readFloat` | 4 | Biểu diễn bit IEEE 754, có thể có `NaN`/vô cực. |
| `writeDouble` / `readDouble` | 8 | Biểu diễn bit IEEE 754, chú ý sai số khi dùng cho tiền. |
| `writeUTF` / `readUTF` | Biến đổi | Chuỗi dạng **modified UTF-8**, có tiền tố độ dài 2 byte không dấu. |

Ví dụ `writeInt(0x01020304)` tạo bốn byte theo đúng thứ tự `01 02 03 04`. Nếu đọc lại bằng `readInt()`, ta nhận `0x01020304`. Nhưng nếu đọc bằng `readShort()` rồi `readInt()`, con trỏ đọc lệch so với schema; những trường tiếp theo sẽ bị giải nghĩa sai hoặc ném `EOFException`.

Các quy tắc có tác dụng ở **mọi** hiện thực DataInput/DataOutput đúng chuẩn, kể cả `RandomAccessFile`. Không được tự suy rằng thứ tự byte phụ thuộc CPU đang chạy. Nếu tương tác với một format little-endian, phải sử dụng bộ đọc/ghi được cấu hình tương ứng (chẳng hạn `ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)`) hoặc chuyển byte có chủ đích; các phương thức `readInt`/`writeInt` của hợp đồng DataInput/DataOutput không có tùy chọn little-endian.

## 3. Ví dụ hoàn chỉnh: file bản ghi cố định và cập nhật bằng seek

Giả sử lưu điểm của thành viên. Mỗi bản ghi gồm:

- `id`: `int` = 4 byte.
- `points`: `long` = 8 byte.
- `active`: `boolean` = 1 byte.

Tổng **13 byte/bản ghi**, không có padding. File bắt đầu bằng header gồm `magic`, `version` và `count`, mỗi trường `int` (tổng 12 byte).

```text
Offset (byte)   Nội dung                         Độ dài
0..3            magic = 0x44494F31 ("DIO1")       4
4..7            version = 1                      4
8..11           count = 3                        4
12..24          record[0]                        13
25..37          record[1]                        13
38..50          record[2]                        13

Vị trí record[i] = 12 + i * 13
Trong record[i]: id ở +0, points ở +4, active ở +12
```

Mục tiêu: tạo file tuần tự bằng `DataOutputStream`, nhảy thẳng đến bản ghi thứ hai để cộng 500 điểm bằng `RandomAccessFile`, rồi đọc toàn bộ bằng `DataInputStream`. Ví dụ sử dụng Java 17+ và tạo file tạm, cuối cùng tự dọn dẹp.

Lưu thành **`DataIoRandomAccessDemo.java`**:

```java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class DataIoRandomAccessDemo {
    private static final int MAGIC = 0x44494F31; // ASCII "DIO1"
    private static final int VERSION = 1;
    private static final int COUNT = 3;
    private static final int HEADER_BYTES = 3 * Integer.BYTES;
    private static final int RECORD_BYTES =
            Integer.BYTES + Long.BYTES + 1;

    private record Member(int id, long points, boolean active) {}

    private static void writeMember(DataOutput out, Member m)
            throws IOException {
        out.writeInt(m.id());
        out.writeLong(m.points());
        out.writeBoolean(m.active());
    }

    private static Member readMember(DataInput in) throws IOException {
        int id = in.readInt();
        long points = in.readLong();
        boolean active = in.readBoolean();
        return new Member(id, points, active);
    }

    private static long offsetOf(int index) {
        if (index < 0 || index >= COUNT) {
            throw new IndexOutOfBoundsException("index=" + index);
        }
        return HEADER_BYTES + (long) index * RECORD_BYTES;
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("data-io-", ".bin");
        try {
            // Lần 1: ghi tuần tự file mới qua DataOutputStream.
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(file)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(COUNT);
                writeMember(out, new Member(101, 1200, true));
                writeMember(out, new Member(102, 2250, false));
                writeMember(out, new Member(103, 900, true));
            }

            // Lần 2: truy cập trực tiếp record[1] nhờ RandomAccessFile.
            try (RandomAccessFile raf =
                    new RandomAccessFile(file.toFile(), "rw")) {
                long second = offsetOf(1); // byte 25
                raf.seek(second);
                Member before = readMember(raf);
                if (before.id() != 102) {
                    throw new IOException("Sai bản ghi tại byte " + second);
                }

                // Chỉ ghi đè 8 byte của trường points.
                raf.seek(second + Integer.BYTES);
                raf.writeLong(before.points() + 500);
            }

            // Lần 3: đọc tuần tự bằng DataInputStream.
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(file)))) {
                int magic = in.readInt();
                int version = in.readInt();
                int count = in.readInt();

                if (magic != MAGIC || version != VERSION ||
                        count != COUNT) {
                    throw new IOException("Header không hợp lệ");
                }

                System.out.printf(
                        "magic=%08X version=%d count=%d%n",
                        magic, version, count);

                for (int i = 0; i < count; i++) {
                    Member member = readMember(in);
                    System.out.printf(
                            "id=%d points=%d active=%b%n",
                            member.id(), member.points(), member.active());
                }

                System.out.println("EOF after records: " +
                        (in.read() == -1));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac DataIoRandomAccessDemo.java
java DataIoRandomAccessDemo
```

Stdout mong đợi (không phụ thuộc đường dẫn của tệp tạm):

```text
magic=44494F31 version=1 count=3
id=101 points=1200 active=true
id=102 points=2750 active=false
id=103 points=900 active=true
EOF after records: true
```

**Vì sao mã được chia làm ba phiên?**

1. Khi ghi, `DataOutputStream` cung cấp các phương thức `writeInt`, `writeLong`, `writeBoolean` nhưng chỉ tiến về phía trước trong `OutputStream`. Đóng stream trước khi mở file để sửa bảo đảm buffer được flush và giải phóng.
2. `RandomAccessFile(file.toFile(), "rw")` mở file có quyền đọc và ghi, đồng thời thực thi cả `DataInput` và `DataOutput`. `seek(25)` đặt con trỏ ngay đầu bản ghi thứ hai. Sau `readMember`, con trỏ đi qua 13 byte; `seek(25 + 4)` quay về đúng byte đầu của `points`. `writeLong` ghi đè đúng tám byte, giữ nguyên `id` và `active`.
3. `DataInputStream` đọc header rồi đúng `count` bản ghi theo **cùng thứ tự và kiểu** đã ghi. `in.read() == -1` xác nhận stream đã hết sau các bản ghi dự kiến, không còn byte dư.
4. Hai helper nhận `DataOutput` và `DataInput` thay vì loại stream cụ thể, nên hoạt động được với cả stream tuần tự và `RandomAccessFile`.

Mã mẫu dùng `long` cho `points`. Nếu biểu diễn giá trị tiền, hãy lưu số đơn vị nhỏ nhất bằng `long` kèm quy ước về đơn vị/scale, hoặc sử dụng một định dạng `BigDecimal` được mô tả rõ; không nên dùng `double` để kỳ vọng tính toán thập phân chính xác.

## 4. RandomAccessFile và vị trí con trỏ

`RandomAccessFile` có các chế độ mở phổ biến:

- `"r"`: chỉ đọc, file phải tồn tại; gọi phương thức ghi sẽ thất bại.
- `"rw"`: đọc và ghi; có thể tạo file nếu chưa có. Việc tạo file không tự bảo đảm schema hợp lệ.
- `"rws"` và `"rwd"`: yêu cầu đồng bộ hóa cập nhật tương ứng, chi phí có thể cao hơn; semantics và bảo đảm thực tế còn phụ thuộc hệ điều hành/filesystem.

Các phương thức liên quan:

| Phương thức | Ý nghĩa |
| --- | --- |
| `seek(long pos)` | Đặt vị trí byte tiếp theo sẽ đọc/ghi, bắt đầu từ 0; vị trí âm không hợp lệ. |
| `getFilePointer()` | Lấy vị trí byte hiện tại. |
| `length()` | Lấy kích thước file tính bằng byte. |
| `setLength(long newLength)` | Cắt bớt hoặc mở rộng file; mở rộng không mặc định tạo ra dữ liệu bản ghi hợp lệ. |
| `readInt()`/`writeInt(int)` | Đọc/ghi 4 byte tại con trỏ, rồi dịch vị trí 4 byte. |
| `readFully(byte[])` | Đọc đúng số byte của mảng hoặc ném lỗi khi kết thúc sớm. |

Cần nhớ:

- `seek` đến vị trí phía sau EOF **không tự thêm dữ liệu**; ghi tại đó có thể làm file dài hơn và để khoảng trống chưa có nội dung bản ghi hợp lệ.
- `RandomAccessFile` dùng file thông thường và nhận đối số `File` hoặc đường dẫn hệ điều hành; khác với API `Files.newByteChannel(Path, ...)` có thể làm việc với provider NIO khác.
- Không đồng thời vừa sửa bằng `RandomAccessFile` vừa đọc qua `BufferedInputStream` cũ: buffer có thể chứa dữ liệu từ trước khi cập nhật. Đóng/đồng bộ phiên thao tác rồi mở reader mới khi cần kết quả nhất quán.
- Bản ghi kích thước cố định cho phép tính offset trực tiếp. Nếu chứa trường dài biến đổi, ví dụ `writeUTF`, vị trí của những bản ghi sau **không còn là** `headerSize + index * recordSize`. Khi đó cần bảng chỉ mục offset, cấu trúc có độ dài hoặc giải pháp lưu trữ khác.

Khi tính offset từ chỉ mục và kích thước không tin cậy, kiểm tra biên và dùng `Math.multiplyExact`/`Math.addExact` (với kiểu `long`) để phát hiện tràn số. Xác minh `offset + recordSize <= file.length()` trước khi đọc nếu muốn trả thông báo lỗi rõ ràng.

## 5. Đọc tuần tự: các phương thức bổ trợ của DataInput

Ngoài `readInt` và `readLong`, `DataInput` có các phương thức xử lý byte hữu ích:

- **`readFully(byte[] dst)`** đọc đủ `dst.length` byte hoặc ném `EOFException`. `readFully(dst, off, len)` dùng một đoạn mảng. Hữu ích với payload có độ dài biết trước.
- **`readUnsignedByte()`**, **`readUnsignedShort()`** trả về `int` không dấu theo số byte được ghi. Cùng dữ liệu `FF` được `readByte()` diễn giải là -1 nhưng `readUnsignedByte()` là 255.
- **`skipBytes(int n)`** có thể bỏ qua **ít hơn n byte** (đặc biệt khi gần EOF); kiểm tra giá trị trả về, không coi đó là một phép seek chắc chắn.
- **`readUTF()`** đọc chuỗi mã hóa theo quy ước `writeUTF()`; không dùng để đọc mọi chuỗi UTF-8 tùy ý.
- **`readLine()`** của `DataInput` đã bị deprecated; với văn bản hãy dùng `BufferedReader` có charset cụ thể.

Phân biệt API `InputStream.read(byte[])` (có thể trả về **ít hơn** số byte yêu cầu ngay cả khi chưa EOF) với `DataInput.readFully` (cố lấy đủ hoặc thất bại). Ví dụ khung nhị phân có 4 byte length + payload:

```java
int length = in.readInt();
if (length < 0 || length > 1_048_576) {
    throw new IOException("Kích thước payload không hợp lệ: " + length);
}
byte[] payload = new byte[length];
in.readFully(payload);
```

Đây là **snippet**, giả định `in` là một `DataInput` đã mở và `IOException` được xử lý/truyền ra ngoài. Giới hạn 1 MiB chỉ là ví dụ; trong ứng dụng thực tế chọn hạn mức theo hợp đồng và tài nguyên. Nếu payload lớn, không tạo mảng theo mọi giá trị độ dài mà nguồn bên ngoài khai báo: đọc theo chunk có quota và kiểm tra bytes thực tế.

## 6. Chuỗi: writeUTF không đồng nghĩa UTF-8 thông thường

`DataOutput.writeUTF(String)` ghi tiền tố **2 byte không dấu** chứa *số byte của phần dữ liệu đã mã hóa*, sau đó ghi chuỗi theo **modified UTF-8**. `DataInput.readUTF()` đọc đúng định dạng này. Khác biệt đáng chú ý:

- Byte độ dài là số byte của bản mã, **không phải số ký tự Java**.
- `writeUTF` giới hạn phần đã mã hóa tối đa 65.535 byte và có thể ném `UTFDataFormatException` khi vượt quá.
- Modified UTF-8 xử lý mã null (`\u0000`) và surrogate UTF-16 khác UTF-8 chuẩn; chuỗi đọc/ghi bằng `writeUTF/readUTF` không mặc định tương thích với một parser UTF-8 của ngôn ngữ khác.
- `writeChars(String)` ghi mỗi UTF-16 code unit thành 2 byte và **không** tự ghi độ dài; `writeBytes(String)` chỉ ghi 8 bit thấp của từng char, có thể làm mất thông tin Unicode. Tránh dùng hai phương thức này cho văn bản trao đổi chung.

Một ví dụ đối ứng hợp lệ với biến `out` kiểu `DataOutput` và `in` kiểu `DataInput` đang trỏ đúng vị trí trong cùng format:

```java
out.writeUTF("Tiếng Việt");   // Ghi modified UTF-8 kèm length.
String text = in.readUTF();  // Đọc đúng format đó.
```

Hai dòng là **minh họa riêng**, không phải đoạn mã chạy tuần tự trên cùng một object. Không gọi `readUTF` trên dữ liệu được ghi bằng `Files.writeString(..., UTF_8)` vì luồng đó không có length prefix và dùng UTF-8 chuẩn.

Nếu cần format để nhiều ngôn ngữ đọc được, có thể định nghĩa **UTF-8 chuẩn + độ dài 4 byte big-endian**:

```java
// Phía ghi (out: DataOutput, text: String)
byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
out.writeInt(bytes.length);
out.write(bytes);

// Phía đọc (in: DataInput)
int len = in.readInt();
if (len < 0 || len > 1_048_576) {
    throw new IOException("Độ dài chuỗi không hợp lệ");
}
byte[] encoded = new byte[len];
in.readFully(encoded);
String decoded = new String(
        encoded, java.nio.charset.StandardCharsets.UTF_8);
```

Đây là hai đoạn độc lập trong cùng schema, dùng biến được khai báo bên ngoài. Bên đọc phải tuân thủ cùng thứ tự byte, charset và giới hạn. Hàm khởi tạo `new String(bytes, UTF_8)` có chính sách thay thế chuỗi byte không hợp lệ; nếu yêu cầu **từ chối** UTF-8 lỗi, dùng `CharsetDecoder` với `CodingErrorAction.REPORT`. Khi dữ liệu quan trọng, xác định thêm chuẩn hóa Unicode, trường rỗng và cách biểu diễn `null` trong schema.

## 7. Thiết kế một file nhị phân an toàn và có thể nâng cấp

Bản ghi là giao ước giữa chương trình ghi và đọc. Một file hợp lý thường có:

1. **Magic number**: xác nhận đúng loại file trước khi đọc các trường khác; ví dụ bốn byte của `0x44494F31`.
2. **Version**: phân biệt các phiên bản layout. Nếu version chưa hỗ trợ, từ chối hoặc chuyển đổi theo quy tắc rõ ràng; không đoán schema mới.
3. **Count/length**: cho biết số bản ghi hoặc kích thước payload; phải kiểm tra giá trị hợp lý trước khi lặp hay cấp phát.
4. **Record layout**: xác định kiểu, thứ tự, số byte, đơn vị, encoding, giá trị đặc biệt và có padding hay không.
5. **Integrity policy**: khi cần, dùng checksum/hash, ghi file tạm và thay thế có kiểm soát; header và count một mình không chứng minh dữ liệu không hỏng.

Ví dụ ở phần 3 có thể xác minh với file chuẩn:

```text
expectedLength = 12 + 3 * 13 = 51 byte
```

Nếu kích thước không khớp, có thể có truncation, byte thừa hoặc một phiên bản layout khác. Với file có payload biến độ dài, cần kiểm tra theo từng trường thay vì công thức cố định. Không chỉ dựa vào `count` khi đầu vào không tin cậy: số lượng khổng lồ có thể gây vòng lặp kéo dài, lỗi I/O hoặc cạn tài nguyên.

**Truy cập ngẫu nhiên có thể cập nhật tại chỗ khi kích thước trường không thay đổi.** Ghi `long` mới vào 8 byte cũ là phù hợp; chèn một chuỗi dài hơn vào giữa file sẽ đẩy các byte phía sau, nên `RandomAccessFile` không tự quản lý việc dịch dữ liệu hay tái xây chỉ mục. Nếu cần bản ghi dài biến đổi hoặc tra cứu phức tạp, cân nhắc cấu trúc indexed/append-only hoặc database tùy yêu cầu.

## 8. Lỗi phổ biến và nguyên nhân

| Hiện tượng | Nguyên nhân | Cách phòng tránh |
| --- | --- | --- |
| `EOFException` trong `readInt/readLong/readFully` | File dừng trước khi đủ số byte của trường. | Xác minh header, count/length và biên file; xử lý dữ liệu hỏng. |
| Dữ liệu đọc thành giá trị vô lý | Sai thứ tự trường, sai signed/unsigned, nhầm byte order. | Ghi rõ schema; đọc đúng cùng thứ tự; test bằng byte fixture. |
| `UTFDataFormatException` | Chuỗi modified UTF-8 hỏng hoặc quá dài lúc `writeUTF`. | Dùng đúng cặp API; giới hạn encoded bytes; chọn format khác khi cần. |
| Sai offset khi seek | Tính index theo số phần tử mà bỏ qua header hoặc nhầm kích thước primitive. | Dùng hằng số kích thước và công thức với `long`; kiểm tra biên. |
| File bị truncate ngoài ý muốn | Mở `Files.newOutputStream(path)` mặc định để ghi lại file hiện có. | Phân biệt tạo mới, append và update; giữ chính sách ghi đè rõ ràng. |
| Đọc thấy dữ liệu cũ | Stream có buffer chưa flush hoặc reader cũ còn buffer. | Hoàn thành/đóng writer trước khi đọc lại; không chia sẻ hỗn hợp buffer. |
| File hỏng sau ghi giữa chừng | Cập nhật nhiều trường bị gián đoạn, thao tác không transaction. | Ghi bản mới ra file tạm, kiểm tra, thay thế có chính sách và backup. |
| Bộ nhớ tăng đột biến | Tin trường length/count từ input rồi cấp phát mảng lớn. | Đặt quota và phát hiện overflow; đọc streaming nếu dữ liệu lớn. |

Các phương thức đọc nhiều byte được mô tả bởi `DataInput` ném `EOFException` nếu chưa đủ byte. Ngược lại `InputStream.read()` trả `-1` khi EOF thông thường. **Đừng dùng bắt `EOFException` thay cho schema có count/length** nếu file đã định nghĩa số bản ghi; EOF bất ngờ thường là dấu hiệu dữ liệu thiếu hoặc hỏng.

`DataInputStream` và `DataOutputStream` được đóng qua `try-with-resources`. Khi bọc `BufferedInputStream/BufferedOutputStream`, đóng lớp ngoài cũng đóng stream phía dưới. `flush()` có thể đẩy dữ liệu trong buffer, nhưng **không đồng nghĩa** dữ liệu đã được lưu bền vững trên thiết bị. Tương tự, `RandomAccessFile` đóng tài nguyên nhưng không biến chuỗi cập nhật thành transaction.

## 9. Khi nào nên chọn giải pháp khác?

- **Stream tuần tự + DataInput/DataOutput:** tốt cho protocol nhị phân nhỏ, file có schema rõ, quá trình ghi một chiều và đọc một chiều.
- **RandomAccessFile:** phù hợp khi file nằm trên OS, có bản ghi kích thước cố định, cần đọc/ghi tại offset cụ thể và có thể kiểm soát đồng bộ truy cập.
- **`FileChannel` hoặc `SeekableByteChannel`:** thích hợp khi cần thao tác ở vị trí byte, buffer, khả năng làm việc theo NIO hoặc I/O dung lượng lớn; `ByteBuffer` cho phép chọn thứ tự byte, khác hợp đồng DataInput/DataOutput.
- **`ObjectOutputStream/ObjectInputStream`:** phục vụ Java object serialization, là cơ chế khác và có vấn đề versioning, interoperability, rủi ro deserialization riêng; không dùng thay thế tùy tiện cho schema nhị phân cố định.
- **Database hoặc format có schema:** phù hợp hơn khi có bản ghi biến độ dài, nhiều writer, truy vấn/chỉ mục, transaction hoặc nhu cầu nâng cấp lâu dài.

**Không dùng Java serialization làm mặc định** chỉ vì cần ghi một vài `int` và `long`. Khi tự thiết kế format, cũng cần tự quản lý compatibility, lỗi và integrity; lựa chọn phù hợp phụ thuộc loại dữ liệu và thời gian bảo trì.

## 10. Bài tập luyện tập

**Bài tập 1 — Tính offset và quan sát byte.** Với layout ví dụ, hãy xác định byte đầu của `record[2]` và byte đầu trường `active` của nó. Đáp án: `12 + 2 * 13 = 38` và `38 + 4 + 8 = 50`. Thử `raf.seek(50); raf.readBoolean()` trên file trước khi xóa để kiểm tra giá trị `true`.

**Bài tập 2 — Cập nhật an toàn.** Bổ sung hàm `updatePoints(Path file, int index, long increment)`. Mở file dạng `"rw"`, kiểm tra magic/version/count, offset và chiều dài file, sau đó dùng `Math.addExact` cho điểm mới rồi chỉ ghi 8 byte tương ứng. Giải thích vì sao nhiều writer vẫn phải được đồng bộ.

**Bài tập 3 — Phát hiện file hỏng.** Sau khi tạo file, dùng `setLength(30)` trên `RandomAccessFile` để cố tình cắt bớt các bản ghi. Chạy chương trình đọc và kiểm tra khi nào phát sinh `EOFException`. Sau đó bổ sung kiểm tra `file.length() == HEADER_BYTES + count * RECORD_BYTES` để phát hiện sai kích thước sớm hơn.

**Bài tập 4 — Chuỗi biến độ dài.** Viết thêm `writeUTF` ở cuối một record và quan sát vì sao `RECORD_BYTES` không còn cố định. Đề xuất bảng index offset hoặc dùng length-prefix để truy cập đúng bản ghi có kích thước biến thiên.

**Tự kiểm tra:** Vì sao `DataInput` không tự lưu kiểu dữ liệu? Khi nào `readUnsignedByte` phù hợp hơn `readByte`? Vì sao `readFully` hữu ích hơn `read` cho khung nhị phân? Vì sao `writeUTF` không tương đương UTF-8 chuẩn? Và vì sao `seek` chỉ an toàn khi đã xác định rõ vị trí, kích thước và schema?

Các nguyên tắc cần mang sang dự án thật là: **thiết kế format trước, giữ thứ tự đọc/ghi đối xứng, xác minh header/length, dùng byte offset chính xác và quản lý tài nguyên/vòng đời file**. `DataInput/DataOutput` đảm bảo cách chuyển primitive sang byte; tính toàn vẹn, đồng bộ và compatibility vẫn là trách nhiệm của chương trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-access-004">length, setLength và vùng dữ liệu chưa được ghi</a>

<details>
<summary>Xem chi tiết</summary>

# `RandomAccessFile.length()`, `setLength()` và vùng dữ liệu chưa được ghi

## 1. Mục tiêu và phạm vi

Khi truy cập ngẫu nhiên bằng `java.io.RandomAccessFile`, chương trình có thể di chuyển con trỏ đến bất kỳ vị trí byte hợp lệ nào, đọc hoặc ghi tại vị trí đó, xem độ dài tệp và chủ động thay đổi độ dài. Ba thao tác dễ bị nhầm với nhau là:

- `length()`: hỏi **độ dài logic hiện tại của tệp**, tính bằng byte.
- `seek(position)`: thay đổi **vị trí con trỏ tệp** dùng cho lần đọc/ghi tiếp theo.
- `setLength(newLength)`: thay đổi **độ dài logic** của tệp, có thể cắt bớt hoặc kéo dài tệp.

Bài tập trung vào hành vi của `RandomAccessFile` (Java 17+), đặc biệt là những byte nằm trong vùng được kéo dài nhưng chưa được ứng dụng ghi giá trị xác định. Bạn sẽ hiểu khi nào việc đọc trả EOF, vì sao không được giả định phần mở rộng chứa toàn byte `0`, và cách chủ động khởi tạo dữ liệu nếu định dạng tệp cần kết quả chắc chắn.

## 2. Hai đại lượng độc lập: độ dài và con trỏ

Với một `RandomAccessFile raf`:

| API | Đơn vị và ý nghĩa | Có thay đổi độ dài tệp? |
| --- | --- | --- |
| `raf.length()` | `long`: tổng số byte của tệp tại thời điểm gọi | Không |
| `raf.getFilePointer()` | `long`: vị trí byte của thao tác đọc/ghi tiếp theo | Không |
| `raf.seek(pos)` | Đặt con trỏ đến offset `pos` (từ đầu tệp, bắt đầu từ 0) | Không, chỉ di chuyển con trỏ |
| `raf.read()` | Đọc byte hiện tại, trả giá trị 0–255 hoặc `-1` nếu EOF | Không |
| `raf.write(value)` | Ghi tại con trỏ và đẩy con trỏ đi một byte | **Có thể**, nếu ghi tới/vượt cuối tệp |
| `raf.setLength(n)` | Đặt độ dài logic mới thành `n` byte | Có, nếu `n` khác độ dài cũ |

Ví dụ tệp chứa ba byte đã được ghi là `ABC`:

```text
offset             0    1    2    3
byte đã ghi        A    B    C    | EOF
length() = 3                     ^
getFilePointer() có thể là 0, 2, 3, 10... tùy seek/đọc/ghi
```

Offset là **byte offset**, không phải vị trí ký tự Unicode. Một ký tự tiếng Việt mã hóa UTF-8 có thể chiếm nhiều byte; `length()` không cho biết số lượng ký tự. Một lần gọi `seek(10)` trên tệp dài 3 byte là hợp lệ (miễn vị trí không âm); con trỏ sẽ ở offset 10 nhưng `length()` vẫn là 3. Cho đến khi có thao tác ghi hoặc đổi độ dài, vùng giữa offset 3 và 10 chưa vì `seek` mà trở thành một phần của tệp.

Phân biệt hai khái niệm sau:

- **EOF (end of file)** là ranh giới cuối độ dài logic. Nếu con trỏ tại hoặc vượt EOF, `read()` trả `-1`.
- **Vùng dữ liệu chưa được ứng dụng ghi rõ ràng** có thể **nằm bên trong độ dài logic** sau khi tệp bị kéo dài. Không được xem vùng này là EOF chỉ vì ứng dụng chưa ghi nội dung vào đó.

Cả `length()` và `getFilePointer()` đều trả `long` vì tệp có thể lớn hơn giới hạn của `int`. Kết quả đo tệp có thể thay đổi khi tiến trình khác sửa cùng tệp; các API không tạo ra một snapshot nhất quán cho mọi thao tác tiếp theo.

## 3. `setLength(newLength)` khi thu nhỏ tệp

Nếu độ dài hiện tại là `L` và gọi `setLength(n)` với `0 <= n < L`, các byte từ offset `n` trở đi bị loại khỏi nội dung logic của tệp. Đây là **truncate**, thao tác có thể làm mất dữ liệu.

Ví dụ trước khi cắt:

```text
offset       0   1   2   3   4   5   6
byte         A   B   C   D   E   F   | EOF
length = 6
pointer = 5
```

Sau `setLength(3)`:

```text
offset       0   1   2   3
byte         A   B   C   | EOF
length = 3
pointer = 3
```

**Quy tắc con trỏ:** nếu vị trí hiện tại lớn hơn độ dài mới khi thu nhỏ, `RandomAccessFile` đưa con trỏ về đúng `newLength`. Nếu vị trí hiện tại nhỏ hơn hoặc bằng độ dài mới, con trỏ giữ nguyên. Ví dụ: pointer đang ở 1 khi cắt xuống 3 thì vẫn ở 1; pointer đang ở 5 khi cắt xuống 3 thì trở thành 3.

Trường hợp `setLength(0)` cắt toàn bộ nội dung tệp, không xóa chính tệp và không tự tạo bản sao lưu. Lệnh này cần được sử dụng cẩn thận đối với tệp dữ liệu đang có giá trị. Việc tệp bị truncate không bảo đảm có thể khôi phục phần đã bị cắt bằng cách kéo dài lại.

## 4. `setLength(newLength)` khi kéo dài tệp

Nếu độ dài hiện tại `L` và `newLength > L`, tệp được kéo dài đến đúng độ dài logic `newLength`. Theo hợp đồng API của `RandomAccessFile`, **nội dung phần được kéo dài không được xác định** (`the contents of the extended portion of the file are not defined`).

```text
Ban đầu: length = 3
offset             0   1   2   3
nội dung           A   B   C   | EOF

Sau setLength(8):
offset             0   1   2   3   4   5   6   7   8
nội dung           A   B   C   ?   ?   ?   ?   ?   | EOF
                               └──── vùng chưa có giá trị do ứng dụng xác định
length = 8
```

Dấu `?` **không** biểu thị EOF; đó là những vị trí byte hợp lệ bên trong file, nhưng bạn không được dựa vào giá trị của chúng theo hợp đồng Java. Trên nhiều hệ điều hành và filesystem, các “lỗ” có thể được đọc như byte 0, song đó không phải cơ sở portable để thiết kế định dạng dữ liệu. Việc được hệ thống tệp cấp phát vật lý ra sao cũng không đồng nghĩa với việc ứng dụng đã ghi và xác nhận dữ liệu.

Khi **mở rộng** tệp, `setLength` không dịch chuyển con trỏ đang ở vị trí hợp lệ trước đó. Con trỏ có thể đang nằm tại offset cũ hoặc thậm chí vượt EOF mới vì `seek` trước đó. Chỉ quy tắc cắt bớt khi pointer vượt độ dài mới mới tự động điều chỉnh pointer.

Lưu ý:

- `setLength` nhận giá trị `long` không âm; giá trị âm gây `IllegalArgumentException`.
- Nếu tệp không được mở để ghi (ví dụ mode `"r"`), yêu cầu đổi độ dài không thể thực hiện thành công và có thể ném `IOException`.
- Kéo dài, cắt bớt hoặc lấy metadata cũng có thể thất bại vì quyền, lỗi I/O, giới hạn dung lượng, quota hoặc hệ thống tệp. Không nên che giấu `IOException`.
- `setLength` không bảo đảm dữ liệu được “đặt trước” thành các block vật lý thực tế. Filesystem hỗ trợ sparse file có thể chỉ cấp phát block khi chúng được ghi.

## 5. `seek` vượt EOF rồi mới ghi: vùng trống có ý nghĩa gì?

Giả sử tệp chứa `ABC` (dài 3 byte). Sau `seek(10)`:

```text
0  1  2  3  4  5  6  7  8  9 10
A  B  C  | EOF                  ^ pointer
length = 3; pointer = 10
```

Nếu gọi `read()` lúc này, kết quả là `-1` vì pointer nằm ngoài độ dài logic. Nếu thay bằng `writeByte('Z')` tại offset 10, thao tác ghi sẽ làm tệp có độ dài ít nhất 11 byte. Byte ở offset 10 được ứng dụng ghi rõ là `Z`; **các byte offset 3–9 không nên bị diễn giải như dữ liệu đã khởi tạo hợp lệ**.

```text
0  1  2  3  4  5  6  7  8  9 10 11
A  B  C  ?  ?  ?  ?  ?  ?  ?  Z  | EOF
length = 11; pointer = 11
```

Đừng gộp “vùng chưa từng ghi”, “vùng được tạo do kéo dài” và “vùng EOF” thành một trạng thái. Nếu định dạng tệp có các record hợp lệ, cần có cách đánh dấu record đã tạo, bitmap, header, sentinel do bạn thực sự ghi, hoặc khởi tạo đầy đủ từng byte mà chương trình cần đọc.

## 6. Ví dụ Java 17+ chạy được: kiểm chứng length, pointer và EOF

Lưu tệp **`RandomAccessLengthDemo.java`**:

```java
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RandomAccessLengthDemo {
    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile("raf-length-", ".bin");

        try {
            try (RandomAccessFile raf =
                         new RandomAccessFile(path.toFile(), "rw")) {

                // Ba byte ASCII đã xác định: A B C
                raf.write(new byte[] {65, 66, 67});
                System.out.println("after write: length=" + raf.length()
                        + ", pointer=" + raf.getFilePointer());

                // Chỉ dịch con trỏ; tuyệt đối chưa làm dài file.
                raf.seek(10);
                System.out.println("after seek(10): length=" + raf.length()
                        + ", pointer=" + raf.getFilePointer());
                System.out.println("read beyond EOF=" + raf.read());

                // Mở rộng file nhưng không dựa vào các byte chưa ghi.
                raf.seek(3);
                raf.setLength(12);
                System.out.println("after setLength(12): length=" + raf.length()
                        + ", pointer=" + raf.getFilePointer());

                // Ghi và đọc đúng một byte mà ta biết chắc giá trị.
                raf.seek(10);
                raf.writeByte('Z');
                raf.seek(10);
                System.out.println("known byte at 10="
                        + (char) raf.readUnsignedByte());

                raf.seek(12);
                System.out.println("read at 12=" + raf.read());

                // Pointer đang ở 12; cắt file về 2 => pointer về 2.
                raf.setLength(2);
                System.out.println("after setLength(2): length=" + raf.length()
                        + ", pointer=" + raf.getFilePointer());

                raf.seek(0);
                byte[] prefix = new byte[2];
                raf.readFully(prefix);
                System.out.println("remaining prefix="
                        + new String(prefix, StandardCharsets.US_ASCII));
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac RandomAccessLengthDemo.java
java RandomAccessLengthDemo
```

Stdout dự kiến khi các thao tác I/O thành công:

```text
after write: length=3, pointer=3
after seek(10): length=3, pointer=10
read beyond EOF=-1
after setLength(12): length=12, pointer=3
known byte at 10=Z
read at 12=-1
after setLength(2): length=2, pointer=2
remaining prefix=AB
```

Vì sao ví dụ có thể có output xác định? Chương trình **chỉ đọc những byte đã được ghi rõ** (`ABC`, `Z`) hoặc đọc tại vị trí EOF (trả `-1`). Không hề in hay kiểm tra giá trị của vùng kéo dài ở offset 3–9 và 11. `Files.createTempFile` tạo tệp mới riêng; `try-with-resources` đóng `RandomAccessFile` trước khi `finally` xóa tệp.

Nếu máy không cho phép tạo/ghi/xóa tệp trong thư mục tạm, thao tác sẽ ném lỗi và ví dụ sẽ không có toàn bộ stdout ở trên; đây là lỗi môi trường I/O chứ không thay đổi quy tắc byte offset.

## 7. Khởi tạo vùng mở rộng để dữ liệu có giá trị xác định

Nếu định dạng tệp yêu cầu byte mới phải là 0 (chẳng hạn mảng bản ghi cố định), hãy **ghi 0 vào toàn bộ vùng cần khởi tạo** thay vì tin rằng `setLength` tự làm điều đó theo hợp đồng API.

Ví dụ hoàn chỉnh **`InitializeExtendedRegion.java`**:

```java
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class InitializeExtendedRegion {
    private static void fillWithZeros(
            RandomAccessFile raf, long from, long endExclusive)
            throws IOException {
        if (from < 0 || endExclusive < from) {
            throw new IllegalArgumentException("Invalid byte interval");
        }

        byte[] zeros = new byte[8192];
        raf.seek(from);
        long remaining = endExclusive - from;

        while (remaining > 0) {
            int count = (int) Math.min((long) zeros.length, remaining);
            raf.write(zeros, 0, count);
            remaining -= count;
        }
    }

    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile("raf-initialize-", ".bin");
        try {
            try (RandomAccessFile raf =
                         new RandomAccessFile(path.toFile(), "rw")) {
                raf.write(new byte[] {10, 20});

                long originalLength = raf.length();
                long targetLength = originalLength + 5;

                raf.setLength(targetLength);
                fillWithZeros(raf, originalLength, targetLength);

                raf.seek(0);
                byte[] bytes = new byte[(int) raf.length()];
                raf.readFully(bytes);
                System.out.println(Arrays.toString(bytes));
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

```bash
javac InitializeExtendedRegion.java
java InitializeExtendedRegion
```

Stdout khi thành công:

```text
[10, 20, 0, 0, 0, 0, 0]
```

Cơ chế: chương trình ghi hai byte đầu (`10`, `20`), mở rộng độ dài từ 2 lên 7, sau đó ghi **chính xác năm byte 0** lên offset 2–6. Vì vùng đó đã được ứng dụng ghi, lần đọc sau có thể phụ thuộc vào giá trị 0 (với điều kiện không có tác nhân khác sửa tệp).

Đây là một ví dụ nhỏ để in toàn bộ tệp. Với tệp lớn, **không** cấp phát một mảng byte có kích thước bằng `raf.length()`; hãy đọc/ghi theo chunk hữu hạn, có giới hạn và kiểm tra overflow khi tính offset. Phương thức `fillWithZeros` đã ghi theo block 8192 byte để tránh tạo mảng khổng lồ cho vùng cần khởi tạo.

**Đánh đổi quan trọng:** ghi hàng gigabyte byte 0 thật sự tốn thời gian và I/O, đồng thời có thể làm mất lợi ích sparse storage. Nếu hệ thống chỉ cần phân biệt bản ghi tồn tại hay chưa, metadata quản lý trạng thái bản ghi thường hợp lý hơn so với khởi tạo toàn bộ vùng dung lượng lớn.

## 8. `read()`, `readFully()` và nội dung chưa ghi

Phương thức đọc cũng dễ gây nhầm:

| Thao tác | Khi con trỏ đúng EOF hoặc vượt EOF | Khi con trỏ nằm trong vùng mở rộng chưa khởi tạo |
| --- | --- | --- |
| `read()` | Trả `-1` | Có thể đọc một byte; **không dựa vào giá trị trả về** |
| `read(byte[])` | Trả `-1` khi không thể đọc byte nào do EOF | Có thể trả số byte đã đọc; dữ liệu vùng chưa khởi tạo không có giá trị ứng dụng bảo đảm |
| `readFully(buffer)` | Ném `EOFException` nếu không đủ byte theo yêu cầu | Có thể hoàn thành nếu đủ byte theo độ dài logic, nhưng không chứng minh các byte là record hợp lệ |
| `length()` | Trả tổng chiều dài hiện tại | Bao gồm cả phần tệp đã được mở rộng dù nội dung phần đó chưa xác định |

`readFully` là hữu ích khi định dạng yêu cầu đọc chính xác `N` byte và muốn biết khi nào bị thiếu dữ liệu do EOF. Nhưng nó **không thể tự chứng minh** rằng toàn bộ `N` byte đã được ứng dụng khởi tạo có ý nghĩa. Đó là nhiệm vụ của định dạng tệp (header, record count, trạng thái bản ghi, checksum hoặc metadata tương ứng).

Ví dụ với file 3 byte `ABC`: `seek(2)` rồi `readFully(new byte[2])` sẽ ném `EOFException` vì chỉ còn 1 byte. Ngược lại, nếu đã `setLength(10)`, lệnh đọc 2 byte từ offset 2 có thể trả đủ hai byte về mặt độ dài, nhưng byte offset 3 thuộc vùng chưa được ứng dụng viết giá trị xác định.

Đối với `read(byte[])`, không giả định nó luôn đọc đủ độ dài của mảng trong một lần gọi; cần kiểm tra giá trị trả về hoặc dùng `readFully` khi yêu cầu đủ byte.

## 9. Độ dài logic, dung lượng vật lý và sparse file

Khi gọi `setLength(1_000_000_000L)`, `length()` có thể trả một tỷ byte dù quá trình chưa ghi một tỷ byte dữ liệu. Nhiều filesystem hỗ trợ **sparse file**, nơi các vùng chưa có block thực tế được ánh xạ hoặc cấp phát theo chính sách riêng.

Có ba khái niệm phải phân biệt:

1. **Logical size:** độ dài tệp mà Java báo qua `length()` hoặc `Files.size(path)`.
2. **Allocated storage:** block/bộ nhớ lưu trữ thực được cấp phát theo filesystem; có thể khác đáng kể logical size.
3. **Initialized application data:** những byte mà ứng dụng đã ghi, hoặc có đủ bằng chứng theo định dạng để diễn giải thành dữ liệu hợp lệ.

`RandomAccessFile.setLength` không phải API bảo đảm cấp phát block vật lý hay bảo đảm có sẵn dung lượng cho mọi lần ghi sau. Sparse file, giới hạn quota, compression và filesystem mạng khiến cách lưu trữ thực tế khác nhau. Nếu cần đặt trước dung lượng vật lý theo cam kết cụ thể, hãy nghiên cứu API của hệ điều hành/filesystem thay vì coi `setLength` là cam kết portable.

Đừng diễn giải tệp có logical size lớn là đã sở hữu tương ứng từng ấy disk space hoặc chắc chắn có thể ghi kín tệp mà không gặp `IOException`. Cũng đừng dùng thống kê dung lượng từ FileStore để xác nhận từng byte đã khởi tạo.

## 10. Tình huống áp dụng: định dạng bản ghi truy cập ngẫu nhiên

Giả sử một tệp lưu các record dài cố định 32 byte, có header 16 byte:

```text
header        record 0       record 1       record 2
[0 .. 15]     [16 .. 47]     [48 .. 79]     [80 .. 111]
```

Offset đầu của record thứ `i` là `16 + i * 32` (theo **byte**, với `i >= 0`). `raf.seek(offset)` cho phép đọc hoặc ghi record mà không đi qua toàn bộ các record trước. Một câu hỏi quan trọng: nếu bạn mới ghi record 0 và record 2, record 1 có hợp lệ không?

**Không thể chỉ dựa vào `length()` để trả lời.** Khi ghi record 2, logical size có thể kéo dài qua record 1. Một đọc 32 byte tại offset 48 có thể hoàn thành theo logical size, nhưng những byte đó chưa được ứng dụng định nghĩa thành record 1. Các cách thiết kế phù hợp tùy yêu cầu là:

- Duy trì record count/bitmap/trạng thái slot và **ghi trạng thái hợp lệ sau khi ghi đủ payload** (xét thêm tính crash-safe nếu triển khai thật).
- Khởi tạo tất cả slot cần đọc với giá trị mặc định do ứng dụng thực sự ghi, rồi quy ước ý nghĩa giá trị đó.
- Ghi header chứa phiên bản định dạng, kích thước record và thông tin nhận biết record hợp lệ; kiểm tra offset/length trước khi diễn giải bytes.

Khi tính `offset = headerSize + index * recordSize`, phải kiểm tra chỉ số đầu vào, khả năng tràn số `long` (`Math.multiplyExact` / `Math.addExact` có thể giúp), kích thước record và quyền truy cập. Một `seek` hợp lệ về mặt số học không có nghĩa dữ liệu tại đó tồn tại hoặc hợp lệ.

## 11. So sánh các API liên quan

| API | Điều đáng nhớ |
| --- | --- |
| `RandomAccessFile.length()` | Độ dài logic hiện tại (byte) |
| `RandomAccessFile.setLength(n)` | Có thể thu nhỏ **hoặc kéo dài** đến `n`; vùng kéo dài có nội dung không xác định |
| `RandomAccessFile.seek(n)` | Chỉ chuyển con trỏ; chưa ghi thì không thay đổi length |
| `Files.size(path)` | Lấy logical size qua đường dẫn; cũng có thể thất bại nếu không đọc metadata được |
| `FileChannel.size()` | Lấy kích thước file qua channel |
| `FileChannel.truncate(n)` | Nếu `n` nhỏ hơn size hiện tại, cắt xuống `n`; nếu `n` >= size hiện tại thì **không kéo dài file** |
| `FileChannel.position(n)` | Đổi vị trí channel; bản thân đổi vị trí không tự kéo dài file |

Điểm cần tránh: gọi `channel.truncate(1000)` trên tệp dài 10 byte **không tương đương** `raf.setLength(1000)`. `truncate` của `FileChannel` chỉ có tác dụng thu nhỏ khi kích thước được chỉ định nhỏ hơn kích thước hiện tại. Dù xử lý cùng một tệp, API có hợp đồng khác nhau.

Nếu sử dụng đồng thời `RandomAccessFile` và `getChannel()` của chính nó, vị trí của file descriptor/channel có quan hệ chung; cần hiểu cách các thao tác thay đổi position và tránh giả định chúng độc lập. Trong ví dụ học tập, thao tác trên một đối tượng `RandomAccessFile` giúp hành vi đơn giản, dễ kiểm tra.

## 12. Lỗi thường gặp, kiểm thử và xử lý lỗi

1. **Giả định `seek(largeOffset)` kéo dài file:** chỉ ghi sau seek hoặc gọi `setLength` mới có thể thay đổi độ dài.
2. **Giả định phần kéo dài luôn bằng `0`:** Java không xác định nội dung vùng đó; phải tự ghi dữ liệu cần thiết.
3. **Dùng `length()` làm số record đã ghi:** các “lỗ” có thể nằm trong vùng logical length; nên quản lý record hợp lệ bằng metadata.
4. **Tưởng `setLength(0)` là dời pointer về đầu mà không mất dữ liệu:** nó cắt toàn bộ nội dung. Muốn về đầu chỉ gọi `seek(0)`.
5. **Bỏ qua quy tắc pointer khi truncate:** nếu pointer đang vượt newLength, API đưa pointer về newLength. Kiểm tra `getFilePointer()` khi code phụ thuộc vị trí.
6. **Giả định extend là preallocate vật lý:** logical size, allocated size và khả năng ghi thành công là các thứ khác nhau.
7. **Không xử lý ngoại lệ:** `seek(-1)` và `setLength(-1)` không hợp lệ; các thao tác I/O có thể thất bại vì quyền, tệp bị thay đổi hoặc storage cạn.
8. **Nhầm byte với ký tự:** xử lý binary offset theo byte; nếu dùng UTF-8, cần encoding và cấu trúc record rõ ràng.
9. **Ghi/cắt tệp đang được tiến trình khác sử dụng mà không phối hợp:** có thể tạo dữ liệu bị mất, ghi chồng hoặc đọc không nhất quán. Phân quyền, khóa và giao thức cập nhật phải được thiết kế phù hợp.
10. **Kiểm thử các giá trị không được API bảo đảm:** test portable nên khẳng định `length`, quy tắc pointer, EOF và các byte đã chủ động ghi; không khẳng định giá trị vùng chưa ghi.

Một bộ kiểm tra nên có các tình huống:

- Tệp mới dài 0; `seek(100)` rồi `length()` vẫn 0.
- Ghi 3 byte; `length()` bằng 3; đọc tại offset 3 trả `-1`.
- `setLength(8)`; `length()` bằng 8, con trỏ giữ nguyên nếu không vượt điều kiện truncate.
- Sau khi ghi rõ vùng mở rộng, đọc lại chính những byte đó phải đúng.
- `setLength(2)` từ tệp dài hơn; phần sau offset 1 không còn thuộc logical file, pointer đang vượt 2 phải về 2.
- `setLength(0)` rồi đọc từ đầu phải báo EOF.
- Thử giá trị âm, mode chỉ đọc và lỗi I/O phù hợp, tránh biến kết quả lỗi môi trường thành một kết luận sai về nội dung chưa ghi.

## 13. Câu hỏi tự kiểm tra

1. Tệp dài 5 byte. Sau `seek(100)`, `length()` và `getFilePointer()` lần lượt là bao nhiêu? Điều gì xảy ra nếu gọi `read()`?
2. Tệp dài 5 byte, pointer ở 4. Sau `setLength(20)`, độ dài và pointer là bao nhiêu? Có được khẳng định byte ở offset 19 bằng 0 không?
3. Tệp dài 20 byte, pointer ở 18. Sau `setLength(7)`, pointer còn bao nhiêu? Nội dung ở offset 7–19 có còn được phép đọc như dữ liệu hiện tại không?
4. Vì sao sau `setLength(20)`, lệnh `readFully(new byte[5])` tại offset 10 có thể đọc đủ byte nhưng vẫn chưa chứng minh đó là một record hợp lệ?
5. Nếu cần khởi tạo 2 GiB byte 0 một cách có thể kiểm chứng ở tầng ứng dụng, bạn sẽ tránh cấp phát mảng khổng lồ bằng cách nào? Đánh đổi với sparse storage là gì?
6. `FileChannel.truncate(100)` và `RandomAccessFile.setLength(100)` có cho cùng kết quả khi tệp ban đầu dài 10 byte không?

## 14. Ghi nhớ

- `length()` là **số byte logic** hiện có; `getFilePointer()` là **vị trí** đọc/ghi tiếp theo. Hai giá trị độc lập.
- `seek()` tự nó không kéo dài tệp. Ghi vượt EOF hoặc `setLength` mở rộng mới có thể làm logical length tăng.
- `setLength` thu nhỏ thì cắt nội dung và đưa pointer về `newLength` nếu pointer đang lớn hơn độ dài mới.
- `setLength` kéo dài tạo ra những byte bên trong logical file có **nội dung không được Java xác định**; không nên xem chúng là dữ liệu đã khởi tạo.
- Nếu nghiệp vụ cần dữ liệu mặc định rõ ràng, hãy **ghi rõ các byte cần khởi tạo** hoặc quản lý trạng thái hợp lệ bằng cấu trúc dữ liệu riêng. Kiểm tra cùng lúc logical length, pointer, EOF, dữ liệu đã ghi và lỗi I/O.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-access-005">Thiết kế bản ghi có vị trí và kích thước xác định</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế bản ghi có vị trí và kích thước xác định trong Java: truy cập ngẫu nhiên

## 1. Mục tiêu và bài toán

Khi ứng dụng cần **đọc hoặc cập nhật trực tiếp bản ghi thứ N** trong một tệp dữ liệu, đọc toàn bộ tệp từ đầu đến N thường không cần thiết. Một thiết kế *fixed-size record* đặt mỗi bản ghi vào một vùng byte có kích thước không đổi; biết chỉ số bản ghi là tính được ngay vị trí byte của nó.

Ví dụ, một tệp lưu tài khoản gồm phần đầu tệp (*header*) 16 byte và nhiều bản ghi 64 byte. Để đọc bản ghi có chỉ số 2 (đánh số từ 0), ứng dụng truy cập trực tiếp byte:

~~~text
offset(index) = HEADER_SIZE + index * RECORD_SIZE
offset(2)     = 16 + 2 * 64 = 144
record 2      = các byte [144, 207] (64 byte)
~~~

Đây là **offset tính bằng byte** trong tệp; nó không phải chỉ số ký tự, số dòng văn bản hoặc khóa nghiệp vụ (`id`). Với bố trí cố định, chi phí định vị theo chỉ số có thể là O(1); hiệu suất I/O thực tế còn phụ thuộc hệ điều hành, thiết bị lưu trữ, cache và mức phân mảnh.

Sau bài học, bạn có thể thiết kế một *schema nhị phân ổn định*, tính vị trí không tràn số, đọc/ghi đúng số byte bằng `FileChannel`, xử lý dữ liệu chuỗi có độ dài thay đổi nhưng vùng lưu trữ cố định, đánh dấu xóa và nhận diện giới hạn về tính nhất quán/durability.

## 2. Quy ước trước khi ghi dữ liệu

Một định dạng bản ghi nhị phân cần một **hợp đồng** để bên ghi và bên đọc hiểu đúng cùng các byte. Hợp đồng này phải quy định rõ:

1. Kích thước header và kích thước một record theo **byte**, không theo số ký tự.
2. Kiểu và vị trí của từng trường; signed/unsigned, thứ tự byte (*endianness*), kiểu số tiền và giá trị biểu thị trạng thái.
3. Bảng mã chuỗi; cách lưu độ dài chuỗi và đệm đến kích thước cố định.
4. Cách tính offset, đánh số chỉ số, cách xác định bản ghi đã xóa hoặc chưa bao giờ ghi.
5. Version/schema khi mở lại tệp, chiến lược cập nhật, phát hiện lỗi và xử lý crash.

Không thể chỉ lưu bằng `DataOutputStream.writeUTF`, sau đó suy ra rằng mỗi bản ghi có độ dài cố định: độ dài chuỗi phụ thuộc nội dung và `writeUTF` dùng định dạng modified UTF-8 có tiền tố độ dài riêng. Cũng không thể dùng `String.length()` để ước lượng số byte UTF-8: một chữ tiếng Việt có thể chiếm nhiều byte dù chỉ là một ký tự hiển thị.

### Schema mẫu: 16 byte header + các record 64 byte

Phần đầu tệp:

| Offset tuyệt đối | Số byte | Trường | Quy ước |
| --- | ---: | --- | --- |
| 0–3 | 4 | `magic` | 0x46524543 tương ứng `FREC` |
| 4–7 | 4 | `version` | Số nguyên 32 bit, giá trị 1 |
| 8–11 | 4 | `recordSize` | Số nguyên 32 bit, giá trị 64 |
| 12–15 | 4 | `reserved` | 0 ở version 1 |

Mỗi bản ghi tính **tương đối** từ offset bắt đầu record:

| Offset trong record | Số byte | Trường | Quy ước |
| --- | ---: | --- | --- |
| 0 | 1 | `status` | 1 = đang dùng; 2 = xóa logic |
| 1–8 | 8 | `id` | `long` có dấu, khóa nghiệp vụ |
| 9–16 | 8 | `balanceCents` | `long`: đơn vị tiền nhỏ nhất, không dùng `double` |
| 17–18 | 2 | `nameLength` | Độ dài byte UTF-8, 0–40 |
| 19–58 | 40 | `nameArea` | Byte UTF-8 + vùng đệm bằng 0 |
| 59–63 | 5 | `reserved` | 0 ở version 1 |
| **Tổng** | **64** | | |

Toàn bộ `int`/`long`/`short` được mã hóa theo `ByteOrder.BIG_ENDIAN`: byte có ý nghĩa cao nhất đứng trước. Bên đọc phải dùng cùng byte order; nếu đổi sang little-endian mà không đổi schema, dữ liệu số sẽ bị hiểu sai.

Trạng thái `status` cho phép phân biệt bản ghi đang hoạt động và bản ghi bị xóa logic. Giá trị 0 không được dùng cho record hợp lệ; nó thường xuất hiện trong vùng chưa được khởi tạo hoặc vùng trống của tệp thưa (*sparse file*). Không được tự hiểu một vùng toàn số 0 là bản ghi hợp lệ.

Một tệp chứa ba record liên tục có độ dài chính xác `16 + 3 * 64 = 208` byte. Record chỉ số 1 bắt đầu tại byte 80, kết thúc ở byte 143; record chỉ số 2 bắt đầu tại byte 144.

## 3. Tính offset đúng và bảo vệ ranh giới

Công thức cơ bản:

~~~java
long offset = Math.addExact(
        (long) headerSize,
        Math.multiplyExact(index, (long) recordSize));
~~~

Đây là **mảnh mã minh họa**: `headerSize`, `index` và `recordSize` cần được khai báo phù hợp. Các phép `Math.multiplyExact` và `Math.addExact` ném `ArithmeticException` nếu tràn `long`, tránh lỗi nhân số nguyên `int` rồi mới ép lên `long`. Cần từ chối `index < 0` và chỉ cho phép ghi ở slot hợp lệ theo chính sách của ứng dụng.

Để đọc **trọn vẹn** record, kiểm tra:

~~~text
offset >= HEADER_SIZE
offset + RECORD_SIZE <= fileSize
(fileSize - HEADER_SIZE) % RECORD_SIZE == 0
~~~

Đây là các ràng buộc đối với tệp liên tục, không có trailer hoặc vùng metadata chen giữa. Phải tính `offset + RECORD_SIZE` bằng phép kiểm tra tràn (ví dụ `Math.addExact`) và kiểm tra bản ghi trong phạm vi đã cấp phát. Nếu file bị cắt còn một phần record, không được im lặng giải mã phần còn lại từ những byte chưa đọc.

**Phân biệt chỉ số lưu trữ và ID nghiệp vụ.** Record có `id=102` không nhất thiết ở vị trí 102. Ví dụ lưu ở index 1. Nếu ID không liên tục, cần bảng ánh xạ `id → slotIndex` (hoặc B-tree/hash index được lưu riêng), không lấy ID làm offset một cách máy móc vì sẽ tạo lỗ hổng lớn trong tệp.

## 4. Ví dụ Java 17+ hoàn chỉnh: đọc, cập nhật, xóa logic theo vị trí

Ví dụ dùng `java.nio.channels.FileChannel` và `ByteBuffer` để đặt record vào một vùng byte xác định. Nó tạo **tệp tạm riêng**, ghi ba bản ghi, đóng/mở lại tệp, đọc trực tiếp record 1, cập nhật record 1 ngay tại vị trí cũ, đánh dấu xóa record 2 và dọn tệp tạm khi hoàn tất.

Lưu vào file `FixedRecordDemo.java`:

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FixedRecordDemo {
    private static final int HEADER_SIZE = 16;
    private static final int RECORD_SIZE = 64;
    private static final int NAME_BYTES = 40;
    private static final int MAGIC = 0x46524543; // "FREC"
    private static final int VERSION = 1;
    private static final byte ACTIVE = 1;
    private static final byte DELETED = 2;

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("fixed-record-", ".bin");
        try {
            try (FileChannel channel = FileChannel.open(file,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                writeHeader(channel);
                writeRecord(channel, 0, 101, 125_000, "Lan");
                writeRecord(channel, 1, 102, 230_500, "Minh");
                writeRecord(channel, 2, 103, 75_000, "An");
                channel.force(true);
            }

            try (FileChannel channel = FileChannel.open(file,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                validateHeader(channel);
                System.out.println("Offset record 1: " + offsetOf(1));
                System.out.println("Record 1: " + readRecord(channel, 1));
                writeRecord(channel, 1, 102, 240_000, "Minh");
                System.out.println("Updated record 1: " + readRecord(channel, 1));
                markDeleted(channel, 2);
                System.out.println("Record 2: " + readRecord(channel, 2));
                System.out.println("Count slots: " +
                        (channel.size() - HEADER_SIZE) / RECORD_SIZE);
                channel.force(true);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static long offsetOf(long index) {
        if (index < 0) throw new IllegalArgumentException("index must be >= 0");
        return Math.addExact((long) HEADER_SIZE,
                Math.multiplyExact(index, (long) RECORD_SIZE));
    }

    private static void writeHeader(FileChannel ch) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        b.putInt(MAGIC).putInt(VERSION).putInt(RECORD_SIZE).putInt(0);
        b.flip();
        writeFully(ch, b, 0);
    }

    private static void validateHeader(FileChannel ch) throws IOException {
        if (ch.size() < HEADER_SIZE) throw new EOFException("Missing header");
        ByteBuffer b = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        readFully(ch, b, 0);
        b.flip();
        if (b.getInt() != MAGIC || b.getInt() != VERSION
                || b.getInt() != RECORD_SIZE || b.getInt() != 0) {
            throw new IOException("Unsupported header");
        }
        if ((ch.size() - HEADER_SIZE) % RECORD_SIZE != 0) {
            throw new IOException("Truncated record region");
        }
    }

    private static void writeRecord(FileChannel ch, long index,
                                    long id, long cents, String name) throws IOException {
        byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > NAME_BYTES) {
            throw new IllegalArgumentException("name exceeds 40 UTF-8 bytes");
        }
        ByteBuffer b = ByteBuffer.allocate(RECORD_SIZE).order(ByteOrder.BIG_ENDIAN);
        b.put(ACTIVE).putLong(id).putLong(cents)
                .putShort((short) utf8.length).put(utf8);
        // allocate() initialized all remaining bytes to 0; write the full slot.
        b.position(RECORD_SIZE);
        b.flip();
        writeFully(ch, b, offsetOf(index));
    }

    private static String readRecord(FileChannel ch, long index) throws IOException {
        long at = offsetOf(index);
        if (ch.size() < Math.addExact(at, (long) RECORD_SIZE)) {
            throw new EOFException("Record out of range");
        }
        ByteBuffer b = ByteBuffer.allocate(RECORD_SIZE).order(ByteOrder.BIG_ENDIAN);
        readFully(ch, b, at);
        b.flip();
        byte state = b.get();
        if (state == DELETED) return "<deleted>";
        if (state != ACTIVE) throw new IOException("Invalid record state");
        long id = b.getLong();
        long cents = b.getLong();
        int length = Short.toUnsignedInt(b.getShort());
        if (length > NAME_BYTES) throw new IOException("Invalid name length");
        byte[] nameArea = new byte[NAME_BYTES];
        b.get(nameArea);
        String name = new String(nameArea, 0, length, StandardCharsets.UTF_8);
        return "id=" + id + ", cents=" + cents + ", name=" + name;
    }

    private static void markDeleted(FileChannel ch, long index) throws IOException {
        long at = offsetOf(index);
        if (ch.size() < Math.addExact(at, (long) RECORD_SIZE)) {
            throw new EOFException("Record out of range");
        }
        writeFully(ch, ByteBuffer.wrap(new byte[] { DELETED }), at);
    }

    private static void writeFully(FileChannel ch, ByteBuffer b, long at) throws IOException {
        while (b.hasRemaining()) {
            int count = ch.write(b, at);
            if (count <= 0) throw new IOException("Write made no progress");
            at += count;
        }
    }

    private static void readFully(FileChannel ch, ByteBuffer b, long at) throws IOException {
        while (b.hasRemaining()) {
            int count = ch.read(b, at);
            if (count < 0) throw new EOFException("Unexpected end of file");
            if (count == 0) throw new IOException("Read made no progress");
            at += count;
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac FixedRecordDemo.java
java FixedRecordDemo
~~~

Stdout chính xác (đã biên dịch và chạy với JDK 21, tương thích Java 17):

~~~text
Offset record 1: 80
Record 1: id=102, cents=230500, name=Minh
Updated record 1: id=102, cents=240000, name=Minh
Record 2: <deleted>
Count slots: 3
~~~

**Diễn giải những điểm quan trọng trong code:**

- `writeHeader` ghi magic, version, kích thước record và reserved trước khi ghi phần thân. `validateHeader` chặn định dạng không tương thích, tệp thiếu header và phần thân không chia hết cho 64.
- `FileChannel.write(buffer, position)` và `FileChannel.read(buffer, position)` thao tác ở **vị trí tuyệt đối**; chúng không dùng/không thay đổi vị trí hiện tại của channel. Điều này giúp truy cập trực tiếp nhưng không làm các thao tác đồng thời tự động trở nên an toàn.
- Một lần `read`/`write` **không được bảo đảm xử lý đủ** mọi byte yêu cầu. Hai hàm `readFully`/`writeFully` cập nhật vị trí tuyệt đối sau từng lần đọc/ghi; `EOFException` nghĩa là không đủ byte. Trong ví dụ dùng channel dạng blocking trên tệp thông thường; trả 0 được báo thành lỗi tiến độ.
- `ByteBuffer.allocate(RECORD_SIZE)` khởi tạo mảng byte bằng 0. Sau khi ghi các trường có độ dài thực tế, `position(RECORD_SIZE)` rồi `flip()` khiến lần ghi chứa đủ 64 byte, bao gồm byte đệm. Nếu gọi `flip()` ngay sau `put(utf8)`, bộ đệm chỉ chứa phần đã ghi và file sẽ **không** nhận đủ một record 64 byte.
- `writeRecord` luôn viết đúng slot 64 byte: cập nhật thông tin ngắn hơn không để lộ byte tên cũ ở vùng padding. Ví dụ ghi ba slot liên tục từ 0 đến 2; helper tối giản chưa kiểm tra chính sách cho phép index bị bỏ trống, vì thế mã sản phẩm nên giới hạn index hợp lệ trước khi gọi.
- `readRecord` giải mã `status`, `long`, độ dài byte UTF-8 và `nameArea` theo đúng schema. Hàm từ chối trạng thái không hợp lệ và `nameLength > 40`; bản ghi xóa logic trả chuỗi `<deleted>` trong ví dụ.
- `markDeleted` chỉ đổi byte trạng thái tại đầu slot. Kích thước file và chỉ số record khác không thay đổi. `Count slots` đếm **slot vật lý**, bao gồm cả slot đã xóa; nó không phải số bản ghi đang hoạt động.
- Hai khối `try-with-resources` đóng channel đúng thời điểm; `Files.deleteIfExists` xóa tệp tạm sau khi các channel đã đóng. `force(true)` yêu cầu đẩy các cập nhật file/metadata ra thiết bị lưu trữ theo hợp đồng của API, nhưng không biến nhiều lần ghi thành một giao dịch nguyên tử.

## 5. Vì sao độ dài byte của chuỗi phải được lưu riêng?

Trong một schema cố định, vùng tên có thể dài 40 byte nhưng tên thật chỉ dùng 3, 10 hoặc 38 byte. Khi đọc, không thể phân biệt chính xác dữ liệu và padding bằng cách bỏ những byte 0 ở cuối nếu giá trị hợp lệ có thể chứa ký tự NUL. Vì thế ví dụ lưu riêng `nameLength` ở hai byte và đọc đúng phần đó.

Ví dụ:

~~~text
name = "Lan"
UTF-8 = 4C 61 6E                      (3 byte)
nameLength = 3                        (2 byte trong schema)
nameArea = 4C 61 6E 00 00 ... 00     (tổng 40 byte)
~~~

Nếu tên là `"Hà"` thì `name.length()` bằng 2 đơn vị ký tự UTF-16, nhưng chuỗi UTF-8 chiếm 3 byte (`H`: 1, `à`: 2). Giới hạn của schema là **40 byte mã hóa**, không phải 40 ký tự Unicode; không được cắt tùy ý sau byte thứ 40 vì có thể làm hỏng một chuỗi UTF-8 nhiều byte.

Trong hệ thống thực tế, khi bộ mã hóa gặp surrogate Unicode không hợp lệ hoặc bộ giải mã gặp dãy UTF-8 lỗi, hãy sử dụng `CharsetEncoder`/`CharsetDecoder` cấu hình `CodingErrorAction.REPORT` để từ chối dữ liệu thay vì để dấu thay thế lặng lẽ xuất hiện. `new String(bytes, UTF_8)` trong ví dụ minh họa giải mã dữ liệu đã được ứng dụng tự ghi; riêng nó không phải bộ xác thực UTF-8 nghiêm ngặt cho tệp tùy ý từ nguồn không tin cậy.

Giá trị `nameLength` có kiểu Java `short` khi viết. Ví dụ giải mã bằng `Short.toUnsignedInt` để xem đúng 0–65535; tiếp tục kiểm tra giới hạn 40 của schema thay vì tin vào dữ liệu trên đĩa.

## 6. Cập nhật, chèn, xóa và tái sử dụng slot

**Cập nhật tại chỗ** chỉ phù hợp khi mọi phiên bản bản ghi đều chiếm đúng `RECORD_SIZE`. Trong ví dụ, đổi `balanceCents` của index 1 không khiến index 2 đổi vị trí. Tên có thể dài/ngắn hơn miễn vẫn mã hóa vào vùng 40 byte; code ghi lại toàn bộ slot và zero padding.

**Chèn liên tục (append)**: slot tiếp theo thường có index `(fileSize - HEADER_SIZE) / RECORD_SIZE`, sau khi đã xác minh kích thước file hợp lệ. Khi nhiều thread/process cùng append, phải phối hợp cấp phát index để không chọn trùng slot.

**Xóa logic (tombstone)**: đặt status thành `DELETED`, giữ nguyên vị trí vật lý của record khác. Muốn tái sử dụng slot đã xóa, quản lý danh sách slot trống hoặc một cấu trúc chỉ mục với cơ chế đồng bộ. Chỉ đặt `status=DELETED` chưa khiến tệp nhỏ đi hay đảm bảo dữ liệu nhạy cảm cũ bị xóa khỏi thiết bị.

**Xóa vật lý bằng dồn record** sẽ làm thay đổi offset của các record phía sau, phá vỡ ánh xạ index → vị trí. Nếu yêu cầu offset ổn định, dùng tombstone hoặc tổ chức lại dữ liệu (*compaction*) có quản lý chỉ mục/tham chiếu và thay đổi version/generation phù hợp.

**Bản ghi mới có ID nghiệp vụ**: nếu cần tra cứu theo ID bất kỳ, xây chỉ mục từ ID tới index; kiểm tra trùng ID khi ghi. Không suy luận “ID 101 → slot 101” nếu đó không phải quy ước đã được chứng minh bởi schema.

## 7. Lựa chọn API và tính đúng đắn của truy cập ngẫu nhiên

| API | Đặc tính | Khi nào chọn |
| --- | --- | --- |
| `RandomAccessFile` | `seek(long)` thay đổi con trỏ file; có `readFully`, `readInt`, `writeLong` | Ví dụ đơn luồng, làm việc trực tiếp qua file pointer |
| `FileChannel` + `ByteBuffer` | `read/write(buffer, long position)` xử lý offset tuyệt đối, có `force` và `lock` | Định dạng nhị phân, đọc/ghi block, kiểm soát byte order |
| `SeekableByteChannel` | Có `position(long)`, đọc/ghi ở vị trí channel hiện tại | Cần abstraction chung cho những channel có thể seek |
| `MappedByteBuffer` qua `FileChannel.map` | Ánh xạ vùng tệp vào bộ nhớ | Bài toán thích hợp memory mapping, cần xử lý phạm vi map, đồng bộ và tài nguyên cẩn thận |

`RandomAccessFile.seek(offset)` đặt file pointer, nên code chia sẻ một instance và liên tục seek/read/write mà thiếu đồng bộ rất dễ đọc nhầm chỗ. Phương thức positional I/O của `FileChannel` tránh phụ thuộc vào file pointer chung, nhưng vẫn phải kiểm soát cạnh tranh khi hai writer sửa cùng byte/record, cũng như lúc file bị truncate hay close đồng thời.

**ByteBuffer có con trỏ riêng** `position`, `limit` và `remaining`. Không chia sẻ một `ByteBuffer` mutable giữa các luồng mà không có đồng bộ thích hợp. Cần xác định rõ byte order bằng `order(ByteOrder.BIG_ENDIAN)` cho từng buffer được dùng để ghi/đọc trường nhiều byte.

## 8. Những giới hạn cần thiết trong ứng dụng thực tế

### Ghi đúng record không đồng nghĩa với ghi nguyên tử

Một lần cập nhật 64 byte có thể tương ứng nhiều lần ghi/khối lưu trữ. Mất điện hoặc crash giữa chừng có thể khiến record chứa dữ liệu pha trộn giữa phiên bản cũ và mới, thậm chí header/tệp bị cắt. `FileChannel.force(true)` hỗ trợ yêu cầu đồng bộ dữ liệu nhưng **không bảo đảm transaction atomicity** cho một lần cập nhật gồm nhiều byte hay nhiều record.

Nếu cần khôi phục sau crash và tính nhất quán mạnh hơn, cân nhắc các kỹ thuật như: bản ghi có `checksum` và số thế hệ (*generation*); copy-on-write sang slot mới rồi cập nhật con trỏ/manifest an toàn; nhật ký ghi trước (*write-ahead log*); hoặc dùng database có transaction thay vì tự xây storage engine.

### Đồng bộ giữa thread/process

- Quy định ai được ghi cùng slot; dùng khóa ở tầng ứng dụng hoặc cơ chế đồng bộ phù hợp.
- `FileChannel.lock()`/`tryLock()` có thể phối hợp khóa tệp/vùng tệp, nhưng đặc tính khóa phụ thuộc nền tảng và provider. Không tự coi khóa hệ điều hành là giải pháp cho mọi trường hợp hoặc là bằng chứng đã có transaction.
- Khi đọc đồng thời writer, một record có thể bị nhìn thấy ở trạng thái trung gian. Nếu cần snapshot nhất quán, phải có giao thức đọc/ghi (ví dụ lock, version/counter và retry, hoặc copy-on-write).
- Đừng cho hai luồng vừa tính cùng `fileSize` rồi cùng append mà không cấp phát index nguyên tử.

### Kiểm tra định dạng từ tệp không tin cậy

Trước khi parse, xác minh ít nhất `magic`, `version`, `recordSize`, tổng độ dài file, index hợp lệ, status, độ dài UTF-8 và giá trị nghiệp vụ. Ví dụ chỉ kiểm tra một số trường quan trọng để tập trung vào thiết kế fixed record; ứng dụng thực tế nên xác thực `id`, `balanceCents`, bytes reserved và tính toàn vẹn.

Với nhu cầu bền vững lâu dài, đừng “đoán” schema từ kích thước file nếu header cho biết một version chưa được hỗ trợ. Hãy từ chối mở hoặc chạy quy trình migration có kiểm thử. Thay đổi kích thước record từ 64 sang 80 byte mà không thay version/migration làm toàn bộ offset cũ không còn chính xác.

### Dung lượng và phân mảnh

- Fixed-size record đơn giản cho truy cập theo slot, đổi lại có **internal fragmentation**: `nameArea` 40 byte vẫn được dành ra khi tên chỉ có 3 byte.
- Dùng record quá nhỏ sẽ hạn chế trường dữ liệu; record quá lớn gây lãng phí cho cập nhật nhỏ và cache.
- Với trường có thể dài bất kỳ (mô tả, ảnh, JSON), cân nhắc record có độ dài biến thiên cộng offset index, hoặc fixed-size metadata record trỏ đến vùng dữ liệu riêng; khi ấy cần cơ chế quản lý free space và tính nhất quán khác.
- Nếu tạo slot ở index rất lớn, một số file system có thể tạo *sparse file* với lỗ hổng đọc ra byte 0. File size lúc đó không chứng minh mọi slot trước index lớn đều từng được ghi.

## 9. Kiểm tra thiết kế trước khi triển khai

Một thiết kế có vị trí và kích thước xác định nên trả lời được các câu hỏi sau:

1. **Offset có xác định tuyệt đối?** Tên header size, record size, index base, kiểu số nguyên và phép kiểm tra tràn.
2. **Mỗi trường có ranh giới byte rõ ràng?** Tổng số byte từng trường chính xác bằng record size; byte order và signedness được tài liệu hóa.
3. **Chuỗi có quy định charset và giới hạn byte?** Encode trước rồi kiểm tra số byte, có length prefix hoặc quy tắc padding không mơ hồ.
4. **Đọc/ghi có xử lý partial I/O và EOF?** Không giả định một lời gọi channel xử lý hết buffer.
5. **Đọc lại sau khi đóng/mở tệp có đúng?** Có magic, version, kiểm tra file length và trạng thái record.
6. **Cập nhật/xóa có giữ offset các record còn lại?** Có tombstone, quy tắc tái sử dụng slot và ánh xạ ID → index.
7. **Crash/concurrency có được xem xét?** Nếu cần atomicity và durability mạnh, phải có giao thức vượt ngoài positional write cơ bản.

Các lỗi hay gặp là tính offset theo ký tự, quên header trong phép tính, dùng `int` cho phép nhân lớn, không `flip()` đúng cách, đặt `ByteBuffer.limit` chưa đủ record, không kiểm tra partial read/write, đọc chuỗi theo số ký tự thay vì UTF-8 bytes và nhầm chỉ số slot với ID nghiệp vụ.

## 10. Câu hỏi tự kiểm tra

1. Header 24 byte, mỗi record 96 byte, index bắt đầu từ 0. Record index 5 ở offset nào? **504** (=24 + 5 × 96); phạm vi byte là [504, 599].
2. Vì sao thay tên `"Lan"` bằng `"Nguyễn"` không được làm các record sau dịch chỗ? Vì cả hai tên nằm trong vùng `nameArea` cố định; phải tính độ dài UTF-8 và ghi padding đầy đủ.
3. Có thể dùng `fileSize / RECORD_SIZE` để đếm slot khi có header 16 byte không? **Không**; cần `(fileSize - HEADER_SIZE) / RECORD_SIZE` sau kiểm tra file size hợp lệ.
4. Xóa logic một slot có giảm tổng số slot không? **Không**; tombstone giữ nguyên slot vật lý, chỉ thay trạng thái.
5. `FileChannel.write(buffer, position)` có ghi đủ 64 byte chỉ với một lần gọi không? **Không được bảo đảm**; phải lặp cho đến khi buffer hết dữ liệu.
6. Đã gọi `force(true)` có đảm bảo mọi cập nhật 64 byte là nguyên tử khi mất điện không? **Không**; cần cơ chế kiểm tra/khôi phục và transaction nếu bài toán yêu cầu.

## Tài liệu API tham khảo

- [Java SE 17 — FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): positional read/write, `force`, `lock` và memory mapping.
- [Java SE 17 — ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): `position`, `limit`, `flip` và đọc/ghi dữ liệu nhị phân.
- [Java SE 17 — RandomAccessFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/RandomAccessFile.html): `seek`, `readFully` và quản lý con trỏ file.
- [Java SE 17 — Math](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `multiplyExact`, `addExact` giúp phát hiện overflow trong tính offset.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-access-006">getChannel và trạng thái vị trí được chia sẻ</a>

<details>
<summary>Xem chi tiết</summary>

# getChannel và trạng thái vị trí được chia sẻ trong truy cập ngẫu nhiên Java

Khi đọc hoặc ghi tệp bằng `RandomAccessFile`, ta có thể di chuyển con trỏ bằng `seek(long)` rồi gọi `read()` hoặc `write()`. Java NIO cung cấp `FileChannel` để thao tác với `ByteBuffer` và đọc/ghi tại một vị trí xác định. Điểm dễ nhầm: **`RandomAccessFile.getChannel()` trả về kênh gắn với cùng tệp đang mở và hai API dùng chung một trạng thái vị trí**.

Nếu một đoạn code gọi `raf.seek(100)` rồi một đoạn khác gọi `channel.read(buffer)`, con trỏ tệp của `raf` cũng tiến lên theo số byte thực đọc. Nếu tiếp đó gọi `raf.read()`, lần đọc này tiếp tục từ vị trí *sau* thao tác của channel. Hiểu đúng mối liên hệ đó là điều cần thiết khi kết hợp Java I/O truyền thống với NIO hoặc khi nhiều phương thức sử dụng chung đối tượng mở tệp.

## 1. Ba khái niệm vị trí cần phân biệt

### 1.1. File pointer của `RandomAccessFile`

`RandomAccessFile` là một đối tượng vừa đọc vừa ghi tùy chế độ mở, hỗ trợ truy cập ngẫu nhiên trong tệp. Đơn vị của `seek()` và `getFilePointer()` là **byte**, không phải số ký tự hay số phần tử trong mảng.

- `raf.getFilePointer()` trả về độ lệch byte hiện tại tính từ đầu tệp; byte đầu tiên ở vị trí 0.
- `raf.seek(offset)` đặt vị trí cho lần đọc/ghi tiếp theo. `offset` âm không hợp lệ.
- Các lần `read` hoặc `write` tuần tự sẽ di chuyển vị trí theo số byte đã tiêu thụ/ghi.
- Di chuyển `seek()` tới vị trí lớn hơn kích thước tệp **không tự làm tệp dài ra**. Ghi sau đó có thể làm tệp dài ra; nội dung khoảng trống phụ thuộc hệ thống tệp và API, không nên giả định an toàn là toàn byte 0.
- Đọc khi con trỏ ở hoặc sau EOF trả về `-1` với `read()`, trong khi `readFully(...)` ném `EOFException` nếu không đủ dữ liệu.

Một tệp có nội dung ASCII `ABCDEFGH` dài 8 byte có các vị trí 0–7 chứa dữ liệu; vị trí 8 là ngay sau byte cuối cùng (EOF).

### 1.2. File position của `FileChannel`

`FileChannel` có vị trí riêng ở cấp API, truy cập qua `channel.position()` và di chuyển qua `channel.position(newPosition)`. Lời gọi `channel.read(dst)` hoặc `channel.write(src)` **dùng vị trí hiện tại** rồi cập nhật nó theo số byte đọc/ghi thành công.

Riêng kênh lấy từ **`raf.getChannel()`**, Java quy định:

```text
RandomAccessFile.getFilePointer() == FileChannel.position()
                   (cùng một tệp mở)
```

Hai giá trị được đồng bộ về mặt ngữ nghĩa: thay đổi file pointer bằng `raf.seek()` hoặc I/O của `raf` làm thay đổi vị trí channel; thay đổi vị trí bằng `channel.position(...)` hoặc I/O tuần tự của channel làm thay đổi file pointer của `raf`. `getChannel()` trả về **FileChannel duy nhất gắn với đối tượng RandomAccessFile đó**.

Điều này **không có nghĩa** mọi `FileChannel` mở cùng tên tệp sẽ dùng chung vị trí. Một `FileChannel.open(path, ...)` độc lập, hoặc một `RandomAccessFile` khác tự mở đường dẫn đó, có trạng thái vị trí cho lần mở riêng. Chúng cùng thấy dữ liệu trên tệp khi thay đổi được hệ thống tệp phản ánh, nhưng không dùng chung con trỏ chỉ vì đường dẫn giống nhau.

### 1.3. Vị trí của `ByteBuffer`

`buffer.position()` là chỉ số trong **bộ đệm bộ nhớ**, không phải độ lệch trong tệp. Ví dụ, trước khi đọc `ByteBuffer.allocate(4)` có `position=0`; nếu channel đọc được 3 byte, vị trí buffer tăng lên 3. Vị trí tệp cũng có thể tăng 3 khi dùng `channel.read(buffer)`, nhưng đó là **hai trạng thái khác nhau**.

`buffer.flip()` chuẩn bị đọc những byte đã được nạp vào buffer: nó đổi `limit` và `position` của buffer, **không tua lại con trỏ của tệp**. `buffer.clear()` cũng chỉ thay đổi trạng thái của buffer, không xóa nội dung tệp và không đưa `FileChannel.position()` về 0.

## 2. Lời gọi nào làm di chuyển vị trí dùng chung?

Giả sử `raf` mở ở chế độ `"rw"`, `channel = raf.getChannel()` và ban đầu con trỏ đang ở byte 2.

| Thao tác | Tác động lên vị trí dùng chung |
| --- | --- |
| `raf.seek(10)` | Đặt con trỏ của cả `raf` và `channel` thành 10 |
| `raf.read()` | Nếu đọc được một byte, vị trí tăng 1; tại EOF trả `-1`, không tiêu thụ byte |
| `raf.write(byteValue)` | Ghi một byte tại vị trí hiện tại rồi tăng vị trí |
| `channel.position(5)` | Đặt vị trí của cả hai thành 5 |
| `channel.read(dst)` | Đọc từ vị trí hiện tại; vị trí tăng bằng số byte thực đọc (không nhất thiết bằng `dst.remaining()` ban đầu) |
| `channel.write(src)` | Ghi từ vị trí hiện tại; vị trí tăng bằng số byte thực ghi |
| `channel.read(dst, absoluteOffset)` | Đọc ở vị trí tệp cho trước; **không đổi** vị trí dùng chung |
| `channel.write(src, absoluteOffset)` | Ghi ở vị trí tệp cho trước; **không đổi** vị trí dùng chung |
| `buffer.flip()`, `buffer.rewind()`, `buffer.clear()` | Chỉ đổi trạng thái của `ByteBuffer`, không đổi vị trí tệp |

Hai dạng có tham số `long position` ở cuối, `FileChannel.read(ByteBuffer,long)` và `FileChannel.write(ByteBuffer,long)`, được gọi là **positional I/O**. Chúng **vẫn thay đổi `ByteBuffer.position()`** theo số byte đã xử lý, nhưng giữ nguyên `FileChannel.position()` / `raf.getFilePointer()`.

Lưu ý: tham số `position` của positional I/O là vị trí bắt đầu *trong tệp*, khác `buffer.position()`. Một lần đọc/ghi channel có thể xử lý ít byte hơn mức yêu cầu, vì vậy phải dùng **giá trị trả về** để tính tiếp, không giả định chỉ cần gọi một lần là đầy buffer.

## 3. Ví dụ Java 17+ chạy được: lần theo con trỏ từng bước

Tạo tệp **`SharedPositionDemo.java`** với nội dung sau. Chương trình sử dụng tệp tạm riêng, ghi chuỗi ASCII có độ dài xác định, thực hiện các thao tác theo thứ tự rồi xóa tệp tạm khi kết thúc.

```java
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SharedPositionDemo {
    private SharedPositionDemo() {}

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("shared-position-", ".bin");
        try {
            try (RandomAccessFile raf =
                    new RandomAccessFile(file.toFile(), "rw")) {
                FileChannel channel = raf.getChannel();

                raf.write("ABCDEFGH".getBytes(StandardCharsets.US_ASCII));
                show("write 8 bytes", raf, channel);

                raf.seek(2);
                show("raf.seek(2)", raf, channel);

                ByteBuffer relative = ByteBuffer.allocate(2);
                readFully(channel, relative);
                relative.flip();
                System.out.println("channel.read -> " +
                        StandardCharsets.US_ASCII.decode(relative));
                show("after channel.read", raf, channel);

                System.out.println("raf.read -> " + (char) raf.read());
                show("after raf.read", raf, channel);

                channel.position(1);
                show("channel.position(1)", raf, channel);
                System.out.println("raf.read -> " + (char) raf.read());
                show("after raf.read again", raf, channel);

                ByteBuffer absolute = ByteBuffer.allocate(2);
                readFullyAt(channel, absolute, 6);
                absolute.flip();
                System.out.println("read at 6 -> " +
                        StandardCharsets.US_ASCII.decode(absolute));
                show("after positional read", raf, channel);

                ByteBuffer replacement =
                        ByteBuffer.wrap(new byte[] { (byte) 'Z' });
                writeFullyAt(channel, replacement, 0);
                show("after positional write", raf, channel);
            }

            System.out.println("file content -> " +
                    Files.readString(file, StandardCharsets.US_ASCII));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void show(String label,
                             RandomAccessFile raf,
                             FileChannel channel) throws IOException {
        System.out.println(label + ": raf=" + raf.getFilePointer() +
                ", channel=" + channel.position());
    }

    private static void readFully(FileChannel channel,
                                  ByteBuffer dst) throws IOException {
        while (dst.hasRemaining()) {
            int n = channel.read(dst);
            if (n < 0) {
                throw new IOException("Unexpected EOF");
            }
            if (n == 0) {
                throw new IOException("Read made no progress");
            }
        }
    }

    private static void readFullyAt(FileChannel channel, ByteBuffer dst,
                                    long offset) throws IOException {
        while (dst.hasRemaining()) {
            int n = channel.read(dst, offset + dst.position());
            if (n < 0) {
                throw new IOException("Unexpected EOF");
            }
            if (n == 0) {
                throw new IOException("Read made no progress");
            }
        }
    }

    private static void writeFullyAt(FileChannel channel, ByteBuffer src,
                                     long offset) throws IOException {
        while (src.hasRemaining()) {
            int n = channel.write(src, offset + src.position());
            if (n == 0) {
                throw new IOException("Write made no progress");
            }
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac SharedPositionDemo.java
java SharedPositionDemo
```

Kết quả chuẩn của chương trình:

```text
write 8 bytes: raf=8, channel=8
raf.seek(2): raf=2, channel=2
channel.read -> CD
after channel.read: raf=4, channel=4
raf.read -> E
after raf.read: raf=5, channel=5
channel.position(1): raf=1, channel=1
raf.read -> B
after raf.read again: raf=2, channel=2
read at 6 -> GH
after positional read: raf=2, channel=2
after positional write: raf=2, channel=2
file content -> ZBCDEFGH
```

**Giải thích từng chuyển động:**

1. `raf.write("ABCDEFGH"...)` ghi 8 byte vào tệp tạm ban đầu rỗng nên con trỏ chuyển tới 8; channel nhìn thấy cùng vị trí.
2. `raf.seek(2)` đưa cả hai về byte 2, trước chữ `C`.
3. `channel.read(relative)` đọc `C`, `D` từ vị trí dùng chung. Lúc này con trỏ ở 4 và `raf.read()` đọc chữ `E`.
4. `channel.position(1)` đưa `raf` về byte 1; `raf.read()` đọc chữ `B`, khiến con trỏ tới 2.
5. `channel.read(absolute, 6)` lấy `G`, `H` ở vị trí tuyệt đối 6; cả hai con trỏ tệp vẫn ở 2.
6. `channel.write(replacement, 0)` thay byte đầu tiên thành `Z` nhưng vẫn giữ vị trí tệp là 2. Sau khi đóng `raf`, nội dung là `ZBCDEFGH`.

Các hàm `readFully` và `readFullyAt` lặp theo lượng dữ liệu thực đọc vì API `FileChannel.read` không đảm bảo một lần gọi sẽ lấp đầy buffer. `writeFullyAt` cũng tăng offset theo `src.position()` để hỗ trợ một lần ghi chưa hoàn tất. Ví dụ dùng tệp cục bộ thông thường; nó báo lỗi nếu một thao tác với dung lượng còn lại không tiến triển, thay vì lặp vô hạn. Nội dung ASCII giúp số byte và vị trí dễ dự đoán.

## 4. `read(dst)` và `read(dst, position)` khác nhau trong thực tế

Xét một phương thức cần đọc tiêu đề 4 byte ở đầu tệp, trong khi phương thức gọi đang duyệt bản ghi ở vị trí 500. Nếu viết:

```java
// Minh họa: channel đã khai báo và đang ở vị trí 500.
channel.position(0);
ByteBuffer header = ByteBuffer.allocate(4);
channel.read(header);
```

Phương thức này vừa **đặt lại vị trí dùng chung về 0**, vừa **đẩy nó lên theo số byte đã đọc**. Khi trở về, `RandomAccessFile` của bên gọi không còn ở 500. Nếu cố khôi phục bằng cách lưu vị trí rồi đặt lại, mọi thao tác xen giữa (đặc biệt trong chương trình nhiều luồng) vẫn có thể bị ảnh hưởng.

Dùng positional I/O để đọc một vùng độc lập:

```java
// Minh họa: channel đã khai báo, readable và còn mở.
ByteBuffer header = ByteBuffer.allocate(4);
long start = 0;
while (header.hasRemaining()) {
    int n = channel.read(header, start + header.position());
    if (n < 0) {
        throw new IOException("Header too short");
    }
    if (n == 0) {
        throw new IOException("No read progress");
    }
}
header.flip();
```

Đoạn này cần nằm trong phương thức xử lý `IOException` và có import `FileChannel`, `ByteBuffer`. Với `read(dst, position)`, trạng thái `position` của channel **và file pointer của `raf` không đổi**, dù `header.position()` tăng. Nếu muốn đọc cấu trúc có kích thước biến thiên, cũng nên kiểm tra giới hạn, kích thước tệp và trường hợp EOF trước khi cấp phát bộ đệm lớn.

Tương tự, nếu cần vá một trường trong tệp mà không làm gián đoạn vòng lặp đọc tuần tự, `channel.write(src, absolutePosition)` giúp ghi tại offset chỉ định mà không di chuyển vị trí dùng chung. Tuy nhiên thay đổi **nội dung** tệp vẫn có thể ảnh hưởng dữ liệu mà thao tác đọc khác sẽ gặp; positional I/O giải quyết vấn đề con trỏ, không tự tạo snapshot hay transaction.

## 5. Kích thước tệp, EOF và ảnh hưởng tới vị trí

**Vị trí (position)** và **kích thước (size/length)** là hai thông số khác nhau:

- `raf.length()` và `channel.size()` cùng phản ánh độ dài của tệp được mở, tính bằng byte.
- `raf.seek(offset)` hoặc `channel.position(offset)` có thể đặt offset lớn hơn kích thước tệp mà không tăng kích thước ngay.
- Đọc từ vị trí ở hoặc vượt EOF không tạo ra byte mới. Đọc từ channel có thể trả `-1` khi không còn dữ liệu.
- Ghi ở vị trí sau EOF có thể mở rộng tệp. Đừng nhầm byte do chương trình ghi với nội dung khoảng trống được tạo ra giữa EOF cũ và vùng ghi.
- `raf.setLength(newLength)` có thể **rút ngắn hoặc kéo dài** tệp. Khi kéo dài, giá trị của byte mới không được bảo đảm theo hợp đồng API. Nếu con trỏ đang vượt `newLength` sau lần gọi, nó được đặt lại bằng `newLength`.
- `channel.truncate(newSize)` chỉ rút ngắn khi `newSize` nhỏ hơn kích thước hiện có; không kéo dài tệp nếu `newSize` lớn hơn. Nếu vị trí hiện tại lớn hơn kích thước mới, nó cũng bị đưa về kích thước mới. Vì vậy `raf.getFilePointer()` và `channel.position()` vẫn cùng phản ánh thay đổi này.

Ví dụ ngắn, giả sử `raf` đang mở tệp 8 byte, `channel = raf.getChannel()`:

```java
// Minh họa, không phải chương trình độc lập.
raf.seek(100);      // Position = 100, length vẫn 8.
channel.truncate(4);
// Position của cả raf và channel = 4; length = 4.
```

Nếu một API thay đổi độ dài tệp, API còn lại sẽ thấy độ dài mới. **Đừng cố lưu trữ một bản sao kích thước tệp lâu dài** trong khi các đoạn code khác vẫn có thể ghi hoặc truncate.

### EOF không phải một vị trí "bị khóa"

Không có quy tắc nào buộc position luôn nhỏ hơn hoặc bằng size. Vị trí có thể vượt EOF. Điều quan trọng là đọc và ghi xử lý khác nhau: đọc không có dữ liệu trả dấu hiệu EOF, còn ghi có thể mở rộng tệp. Nếu chương trình cần một bản ghi ở offset nhất định, cần xác thực vùng byte đó thực sự tồn tại, thay vì dựa riêng vào phép `seek()` đã thành công.

### Mode mở tệp ảnh hưởng thao tác được phép

`new RandomAccessFile(file, "r")` mở để đọc; channel tương ứng không cho ghi. `"rw"` cho đọc/ghi; `"rws"` và `"rwd"` bổ sung yêu cầu đồng bộ cập nhật với thiết bị lưu trữ theo hợp đồng của `RandomAccessFile`. Mode đọc/ghi không quyết định có chia sẻ vị trí hay không: **kênh từ `getChannel()` vẫn gắn với cùng file pointer**.

## 6. Khả năng xảy ra lỗi khi nhiều thành phần dùng cùng con trỏ

Giả sử phương thức A thực hiện `raf.seek(100)`, kỳ vọng lần `raf.readFully(record)` kế tiếp đọc bản ghi ở byte 100. Nhưng trước lần đọc, phương thức B sử dụng `raf.getChannel()` và gọi `channel.position(0)` hoặc `channel.read(someBuffer)`. Lần đọc của A có thể đến từ vị trí ngoài ý muốn.

Đây là **lỗi phối hợp trạng thái**: việc mỗi lời gọi riêng lẻ đều hợp lệ không bảo đảm chuỗi `seek + read` là một giao dịch nguyên tử. Kịch bản nguy hiểm hơn khi hai luồng cùng sử dụng một `RandomAccessFile` hoặc channel được lấy từ nó.

Ba cách thiết kế rõ ràng:

1. **Một chủ sở hữu điều khiển vị trí tuần tự:** Toàn bộ thao tác `seek/read/write` được điều phối trong một thành phần, các thành phần khác không tự di chuyển vị trí.
2. **Positional I/O cho những thao tác độc lập:** Các lời gọi `channel.read(dst, offset)` và `channel.write(src, offset)` không phụ thuộc position dùng chung. Thích hợp với xử lý bản ghi có offset biết trước.
3. **Khóa nhất quán toàn bộ chuỗi thao tác liên quan đến vị trí:** Khi bắt buộc dùng `seek + read`, dùng **cùng một khóa ở tất cả code path có khả năng di chuyển vị trí**, bao gồm cả lời gọi đi qua `FileChannel`. Chỉ `synchronized(raf)` ở một nơi không giúp gì nếu nơi khác gọi channel mà không tham gia khóa đó.

Cần phân biệt **an toàn của bản thân FileChannel** với **tính nguyên tử của nghiệp vụ**. Hợp đồng của `FileChannel` cho phép sử dụng từ nhiều luồng; các hoạt động liên quan vị trí hoặc thay đổi kích thước tệp được điều phối theo quy tắc của channel. Tuy nhiên, điều đó không tự gộp hai lời gọi `seek()` và `read()` của ứng dụng thành một thao tác không thể bị chen giữa. Positional I/O giúp tránh đụng con trỏ, nhưng nếu hai luồng cùng ghi chồng lên vùng byte hoặc một luồng truncate trong lúc luồng khác đọc thì vẫn cần một chính sách đồng bộ dữ liệu.

### Ví dụ về phép đọc bản ghi tại offset cố định

Đây là **đoạn minh họa**, cần đặt trong phương thức xử lý `IOException` với `FileChannel channel` đã mở:

```java
long recordOffset = 128;
ByteBuffer record = ByteBuffer.allocate(16);

while (record.hasRemaining()) {
    int n = channel.read(record,
            recordOffset + record.position());
    if (n < 0) {
        throw new IOException("Incomplete record");
    }
    if (n == 0) {
        throw new IOException("Read made no progress");
    }
}
record.flip(); // Chỉ reset buffer để giải mã bản ghi.
```

Đoạn này đọc đủ 16 byte bắt đầu tại offset 128 mà không cần `channel.position(128)`, đồng thời không làm hỏng vòng lặp tuần tự dùng chung `raf`. Nếu bản ghi có độ dài/offset do dữ liệu bên ngoài cung cấp, hãy kiểm tra tràn số khi cộng offset và length cũng như kích thước tệp trước khi cấp phát bộ đệm; kiểm tra kích thước vẫn không chống được việc tệp bị sửa đồng thời.

## 7. Đóng tài nguyên và sự liên hệ giữa hai API

Kênh lấy bằng `raf.getChannel()` thuộc tài nguyên mở của `raf`, không phải một kênh tách biệt sở hữu file descriptor mới. Theo hợp đồng `RandomAccessFile`, đóng `raf` cũng đóng channel gắn với nó. Vì vậy không lưu channel vượt quá vòng đời của `raf` rồi kỳ vọng nó vẫn hoạt động.

Trong chương trình chính, chỉ dùng:

```java
try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
    FileChannel channel = raf.getChannel();
    // Làm việc với raf và channel trong cùng phạm vi.
}
```

Đoạn này là mẫu minh họa cần có `file` kiểu `Path` và các import thích hợp. Đóng `RandomAccessFile` cuối khối `try` cũng giải phóng channel. Tránh trộn lẫn quyền sở hữu vòng đời, chẳng hạn đóng channel giữa chừng rồi tiếp tục sử dụng `raf` như thể hai đối tượng hoàn toàn độc lập. Nếu muốn một channel hoàn toàn độc lập, hãy mở nó trực tiếp theo chính sách tài nguyên rõ ràng.

## 8. Lỗi thường gặp và cách khắc phục

| Ngộ nhận hoặc lỗi | Hệ quả | Cách xử lý |
| --- | --- | --- |
| `getChannel()` tạo con trỏ độc lập với `RandomAccessFile` | `raf.read()` đọc sai vị trí sau khi channel thao tác | Ghi nhớ file pointer và channel position của cùng lần mở được chia sẻ |
| Dùng `channel.position(offset); channel.read(dst)` như phép đọc "không tác dụng phụ" | Thay đổi vị trí bên gọi | Dùng `channel.read(dst, offset)` nếu muốn giữ vị trí |
| Cho rằng `read(dst)` luôn đọc đủ `dst.remaining()` | Cắt thiếu bản ghi hoặc tính sai offset | Dùng vòng lặp dựa trên số byte thực đọc; xử lý EOF |
| Cho rằng `ByteBuffer.flip()` sẽ tua lại tệp | Vị trí file pointer không thay đổi | Chỉ dùng `raf.seek()` hoặc `channel.position()` khi thực sự cần đổi vị trí tệp |
| Dùng `seek(offset)` như bằng chứng rằng dữ liệu ở offset đó tồn tại | Đọc EOF hoặc dữ liệu không hoàn chỉnh | Kiểm tra độ dài và xử lý kết quả đọc |
| Dùng nhiều luồng gọi xen kẽ `seek()` và `read()` | Bản ghi lấy từ vùng khác kỳ vọng | Một chủ sở hữu vị trí, khóa chung hoặc positional I/O |
| Giả định mọi lần mở cùng path chia sẻ con trỏ | Suy luận sai khi dùng hai channel độc lập | Phân biệt trạng thái mỗi lần mở với nội dung của cùng tệp |
| Đóng `raf` trong khi thành phần khác vẫn giữ `getChannel()` | Thao tác về sau trên channel đã đóng thất bại | Quản lý cùng vòng đời tài nguyên |
| Dùng positional I/O nhưng nghĩ dữ liệu được bảo vệ khỏi ghi đè | Con trỏ ổn định nhưng dữ liệu vẫn có thể bị sửa đồng thời | Có chiến lược đồng bộ nội dung tệp nếu cần |

Để tự kiểm tra, hãy thay đổi lần lượt `raf.seek(2)` thành `channel.position(2)`, và thay `channel.read(relative)` thành dạng `channel.read(relative, 2)`. Quan sát rằng cách đặt vị trí dùng chung vẫn cho cùng trạng thái, còn cách đọc với offset tường minh giữ nguyên con trỏ hiện tại. Thử thêm `raf.length()` và `channel.size()` sau các thao tác chỉ dịch chuyển con trỏ: kích thước vẫn là 8 byte cho tới khi có ghi mở rộng hoặc truncate.

**Ghi nhớ:** `RandomAccessFile.getChannel()` cho phép truy cập cùng tệp qua hai lớp API nhưng cũng ràng buộc chúng vào **một vị trí tuần tự dùng chung**. Các thao tác `seek()`, `position()` và I/O tương đối làm thay đổi vị trí đó; các thao tác `read/write` với **offset tệp tường minh** cho phép truy cập ngẫu nhiên mà không xáo trộn con trỏ, dù vẫn cần quản lý kích thước, vòng đời và truy cập đồng thời.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-access-007">Không chia sẻ file pointer giữa nhiều thao tác mà thiếu phối hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Không chia sẻ file pointer giữa nhiều thao tác mà thiếu phối hợp

Trong **truy cập ngẫu nhiên (random access)**, một file có thể được đọc hoặc ghi tại vị trí bất kỳ thay vì chỉ đi tuần tự từ đầu đến cuối. Java cung cấp `RandomAccessFile`, `FileChannel` và `SeekableByteChannel` để làm việc này. Điểm dễ sai nhất khi nhiều thao tác hoặc nhiều thread cùng dùng một đối tượng I/O là **file pointer (vị trí hiện tại) thuộc về đối tượng truy cập đang được chia sẻ**, nên một thao tác có thể làm thay đổi vị trí mà thao tác khác dự định sử dụng.

Quy tắc thực hành:

> Nếu các thao tác cần những offset riêng, hãy dùng API đọc/ghi theo **vị trí tuyệt đối** khi có thể. Nếu buộc phải dùng chuỗi `seek/position → read/write` dựa trên con trỏ chung, phải phối hợp **toàn bộ chuỗi thao tác** qua một cơ chế đồng bộ chung. Và ngay cả khi không còn tranh chấp con trỏ, các thao tác chồng lấn cùng byte hoặc cùng metadata vẫn cần quy tắc phối hợp dữ liệu.

Bài này tập trung vào **tính đúng đắn khi sử dụng file pointer**, các giới hạn thread-safety của API và cách thiết kế thao tác đọc/ghi có thể kiểm chứng. Không cần giả định rằng dữ liệu “đang nằm ở vị trí đúng” chỉ vì lệnh `seek` trước đó đã trả về thành công.

## 1. File pointer là gì và khác với offset nào?

**File pointer / current file position** là vị trí byte mà thao tác I/O *tương đối* tiếp theo sẽ sử dụng. Thông thường, sau khi đọc/ghi thành công `n` byte, vị trí tăng thêm `n`. Offset được tính từ đầu file, bắt đầu tại `0`, và đo bằng **byte**, không phải chỉ số ký tự Unicode.

Ví dụ file ASCII chứa `ABCDEFGH` có 8 byte:

- Vị trí `0`: byte `A`; vị trí `3`: byte `D`; vị trí `8`: ngay sau cuối file (EOF).
- `seek(3)` hoặc `position(3)` chỉ đặt vị trí đọc/ghi tiếp theo, không tự đọc hay ghi dữ liệu.
- `read` sau đó trả về một hay nhiều byte và thường dịch vị trí hiện tại theo số byte đã thực sự đọc.
- Với UTF-8, một ký tự tiếng Việt có thể chiếm nhiều byte. `position(3)` không có nghĩa “ký tự thứ tư”; đặt sai ranh giới mã hóa có thể cắt ngang ký tự.

Đừng nhầm các loại vị trí sau:

| Loại vị trí | Nằm ở đâu? | Ý nghĩa |
| --- | --- | --- |
| `RandomAccessFile.getFilePointer()` | Trạng thái của đối tượng `RandomAccessFile` / handle liên quan | Offset cho thao tác đọc/ghi kế tiếp qua `RandomAccessFile`. |
| `FileChannel.position()` | Trạng thái của channel | Offset cho các lệnh `read(ByteBuffer)` hoặc `write(ByteBuffer)` không truyền offset riêng. |
| `FileChannel.read(dst, long position)` / `write(src, long position)` | Tham số của **một lệnh** | Offset tuyệt đối của lệnh đó; không thay đổi `channel.position()`. |
| `ByteBuffer.position()` | Trạng thái trong bộ đệm của caller | Chỉ số trong `ByteBuffer` nơi dữ liệu được nhận/lấy tiếp theo; **không phải** offset trong file. |

`ByteBuffer` cũng là trạng thái mutable: một lần `read` có thể thay đổi `buffer.position()` dù thao tác với file là positional. Mỗi tác vụ nên dùng buffer riêng hoặc bảo đảm quy tắc sở hữu buffer, không chỉ phối hợp file pointer.

## 2. Các API thay đổi hoặc chia sẻ vị trí

### `RandomAccessFile`

`RandomAccessFile` cung cấp:

- `seek(long pos)`: đặt file pointer trước một lần đọc/ghi; `pos` phải không âm.
- `getFilePointer()`: trả vị trí byte hiện tại.
- `read(...)`, `readFully(...)`, `write(...)` và các phương thức đọc/ghi kiểu nguyên thủy: sử dụng rồi cập nhật pointer.
- `length()` / `setLength(...)`: xem/thay kích thước file; thao tác thay kích thước có thể ảnh hưởng vị trí hoặc dữ liệu các luồng khác đang dùng.

**Điểm đặc biệt:** `raf.getChannel()` trả về channel gắn với cùng `RandomAccessFile`. Javadoc quy định vị trí của channel này luôn bằng file pointer của `raf`; gọi `raf.seek` hoặc đọc/ghi qua `raf` ảnh hưởng `channel.position()`, và thao tác tương đối qua channel ảnh hưởng `raf.getFilePointer()`. Đừng xem chúng như hai con trỏ độc lập.

### `FileChannel`

Có hai nhóm API cần phân biệt:

```java
channel.position(100);     // đặt shared current position của channel
channel.read(buffer);      // đọc từ current position; tăng position
channel.write(buffer);     // ghi tại current position; tăng position

channel.read(buffer, 100); // đọc tại offset 100; KHÔNG đổi channel.position()
channel.write(buffer, 200);// ghi tại offset 200; KHÔNG đổi channel.position()
```

Đoạn trên là **minh họa API**, giả định `channel` và `buffer` đã được khai báo, có quyền và mode phù hợp. Phương thức đọc/ghi có thể truyền ít byte hơn `buffer.remaining()`; khi cần đọc/ghi đủ, phải lặp và cập nhật offset theo số byte *thực sự* xử lý.

Theo Javadoc `FileChannel` của Java 21, channel **có thể được dùng từ nhiều thread**. Hệ thống chỉ cho phép một thao tác có liên quan đến vị trí của channel hoặc có thể thay đổi kích thước file đang thực hiện tại một thời điểm; các thao tác cùng loại đến sau sẽ chờ thao tác trước hoàn tất. Các thao tác có offset tường minh có thể chạy đồng thời, nhưng việc chúng có thật sự song song hay không phụ thuộc implementation.

Điều trên **không** có nghĩa chuỗi `position(x); write(data)` được bảo vệ như một transaction, cũng **không** có nghĩa hai tác vụ cập nhật cùng đoạn file sẽ giữ được logic của ứng dụng. “Thread-safe API” nói về hợp đồng trạng thái nội tại của một lần gọi, còn **tính đúng đắn của thao tác nhiều bước** là trách nhiệm của chương trình.

### `SeekableByteChannel` và các nguồn khác

`SeekableByteChannel` định nghĩa `position()`, `position(long)`, `read`, `write`, `size`, `truncate`, nhưng không cung cấp overload đọc/ghi offset tuyệt đối trong chính interface. Khi chỉ có `SeekableByteChannel`, không mặc nhiên áp hợp đồng thread-safety chuyên biệt của `FileChannel` cho mọi provider. Nếu cần thay đổi pointer trước I/O, bảo vệ nguyên cụm thao tác bằng một cơ chế chung hoặc cấp channel độc lập cho từng tác vụ khi phù hợp.

## 3. Cách race condition xảy ra dù mỗi lời gọi riêng lẻ hợp lệ

Giả sử một `FileChannel` mở file 10 byte ban đầu: `abcdefghij`. Hai thao tác nghiệp vụ cần ghi:

- **A** muốn ghi `XX` tại byte `0..1`.
- **B** muốn ghi `YY` tại byte `4..5`.

Nếu cùng dùng lệnh tương đối, lịch xen kẽ hợp lệ có thể là:

| Thứ tự | Thao tác | Current position sau thao tác |
| --- | --- | --- |
| 1 | A: `channel.position(0)` | `0` |
| 2 | B: `channel.position(4)` | `4` |
| 3 | A: `channel.write(XX)` | `6` nếu ghi đủ 2 byte |
| 4 | B: `channel.write(YY)` | `8` nếu ghi đủ 2 byte |

A vô tình ghi tại `4..5` và B ghi tại `6..7`. Kết quả theo lịch trên là `abcdXXYYij`, khác với `XXcdYYghij` mà hai thao tác dự định. Hai thao tác đặt vị trí và hai thao tác ghi đều có thể hoàn tất thành công, không cần ném exception.

Lịch này là **mô hình minh họa một interleaving**, không phải khẳng định thread nào chắc chắn được scheduler chạy trước. Vấn đề cốt lõi là việc đặt vị trí và thực hiện I/O là **hai lời gọi riêng**. Đồng bộ từng lời gọi độc lập không tạo ra tính nguyên tử cho chuỗi.

Các biến thể phổ biến:

1. Một thread gọi `seek(start)`, thread khác gọi `seek(otherStart)`, rồi thread đầu đọc nhầm khối.
2. Một luồng đọc header bằng `readInt()`, luồng khác ghi dữ liệu và dịch pointer, khiến lần đọc payload tiếp theo lấy nhầm vị trí.
3. Một method tiện ích dùng `RandomAccessFile` còn method khác dùng `raf.getChannel()`, tưởng là hai cursor riêng nhưng thực tế cùng dùng một.
4. Hai thread cùng chia sẻ một `ByteBuffer` nên `flip`, `clear` hoặc `position` của buffer xen vào nhau, dù vị trí file của mỗi lệnh đã là tuyệt đối.
5. Chương trình tính `long end = channel.size()` rồi cho nhiều thread ghi tại `end`, dẫn đến việc các thread tính cùng offset và ghi đè nhau.

Việc thêm `Thread.sleep(...)`, tăng/giảm số thread hoặc thấy “chạy ổn” nhiều lần không chứng minh code không có race. Thread scheduling, OS caching, file system và tải máy đều có thể làm lỗi lúc xuất hiện lúc biến mất.

## 4. Ví dụ chạy được: `RandomAccessFile` và `getChannel()` dùng chung pointer

Tạo file `PointerCoupling.java`:

```java
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class PointerCoupling {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("raf-position-", ".dat");
        try {
            Files.writeString(file, "ABCDE", StandardCharsets.US_ASCII);
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
                FileChannel channel = raf.getChannel();
                raf.seek(1);
                System.out.println("channel-after-seek=" + channel.position());

                ByteBuffer next = ByteBuffer.allocate(1);
                channel.read(next);
                next.flip();
                System.out.println("relative-byte=" + (char) next.get());
                System.out.println("raf-after-relative=" + raf.getFilePointer());

                ByteBuffer atFour = ByteBuffer.allocate(1);
                channel.read(atFour, 4);
                atFour.flip();
                System.out.println("absolute-byte=" + (char) atFour.get());
                System.out.println("raf-after-absolute=" + raf.getFilePointer());
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch/chạy với Java 17+:

```bash
javac --release 17 PointerCoupling.java
java PointerCoupling
```

Output trên hệ thống file thông thường:

```text
channel-after-seek=1
relative-byte=B
raf-after-relative=2
absolute-byte=E
raf-after-absolute=2
```

Giải thích từng mốc:

- `raf.seek(1)` làm `channel.position()` thành `1`.
- `channel.read(next)` đọc `B` ở vị trí `1` rồi đưa pointer chung đến `2`.
- `channel.read(atFour, 4)` đọc `E` theo offset tuyệt đối `4` nên pointer chung **vẫn là `2`**.
- `next.flip()` và `atFour.flip()` điều chỉnh vị trí của từng buffer để đọc byte vừa nạp; chúng không điều chỉnh file pointer.

Để chương trình ngắn gọn, ví dụ thao tác trên tệp tạm 5 byte không có writer khác và đọc 1 byte. Trong ứng dụng xử lý file có thể đổi kích thước hoặc nhiều source bất định, hãy kiểm tra kết quả `read` và EOF (`-1`), xử lý việc đọc ít byte hơn yêu cầu.

**Lưu ý vòng đời:** `RandomAccessFile` và channel lấy từ `getChannel()` gắn với nhau; đóng `raf` cũng đóng channel liên quan và ngược lại. Không phát channel này cho một tác vụ cần dùng lâu hơn vòng đời chủ sở hữu. Nếu một thread đóng channel khi thread khác đang I/O, xử lý ngoại lệ đóng/hủy thao tác theo hợp đồng thay vì quy lỗi cho pointer.

## 5. Cách ưu tiên: offset riêng với `FileChannel.read/write(ByteBuffer, long)`

Khi tác vụ đã biết offset cần xử lý, overload positional là cách đơn giản để loại bỏ tranh chấp **current position**:

```java
int count = channel.read(targetBuffer, offset);  // không đổi channel.position()
int sent  = channel.write(sourceBuffer, offset); // không đổi channel.position()
```

Ở đây:

- `offset` do nghiệp vụ xác định cho từng vùng dữ liệu, thường truyền qua tham số task.
- Buffer thuộc quyền sử dụng của một tác vụ trong suốt thời gian I/O; `ByteBuffer` không tự an toàn khi nhiều thread cùng thay đổi state.
- Sau mỗi lần `read`/`write`, kiểm tra số byte thực tế, cập nhật offset và tiếp tục nếu còn dữ liệu.
- Offset không âm; cộng offset với số byte đã xử lý phải tránh tràn `long`.
- Không mở channel với `StandardOpenOption.APPEND` nếu logic dựa vào offset ghi tuyệt đối: Javadoc quy định hành vi `write` tại vị trí cho trước khi channel đang mở ở chế độ APPEND là **không xác định**.

Phương pháp này tránh việc A thay đổi pointer của B, **nhưng không tự giải quyết** hai tác vụ cùng ghi byte `100..199`, đọc một record trong lúc record khác bị sửa, append chồng lấn, hoặc cùng gọi `truncate`. Với yêu cầu giữ tính nhất quán, vẫn phải cấp phát vùng không chồng lấn hoặc khóa ở mức dữ liệu/record.

### Ví dụ chạy được: hai thread ghi hai vùng độc lập

Tạo file `IndependentOffsets.java`:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

public class IndependentOffsets {
    static int writeAllAt(FileChannel channel, ByteBuffer source, long offset)
            throws IOException {
        int written = 0;
        while (source.hasRemaining()) {
            int n = channel.write(source, Math.addExact(offset, written));
            if (n == 0) throw new IOException("Write made no progress");
            written += n;
        }
        return written;
    }

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("nio-slots-", ".dat");
        try {
            Files.writeString(file, "........", StandardCharsets.US_ASCII);
            try (FileChannel channel = FileChannel.open(file,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                ExecutorService pool = Executors.newFixedThreadPool(2);
                try {
                    Future<Integer> a = pool.submit(() -> writeAllAt(channel,
                        ByteBuffer.wrap("ABCD".getBytes(StandardCharsets.US_ASCII)), 0));
                    Future<Integer> b = pool.submit(() -> writeAllAt(channel,
                        ByteBuffer.wrap("WXYZ".getBytes(StandardCharsets.US_ASCII)), 4));
                    System.out.println("bytes=" + a.get() + "," + b.get());
                    System.out.println("file=" + Files.readString(file, StandardCharsets.US_ASCII));
                    System.out.println("channel-position=" + channel.position());
                } finally {
                    pool.shutdown();
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac --release 17 IndependentOffsets.java
java IndependentOffsets
```

Output đã kiểm chứng với Java 17 bytecode trên filesystem thông thường:

```text
bytes=4,4
file=ABCDWXYZ
channel-position=0
```

Vì hai task có **offset tuyệt đối không chồng lấn** (`0..3` và `4..7`), kết quả file nhất quán dù task nào chạy trước. Mỗi task sở hữu một `ByteBuffer` riêng và được `Future.get()` xác nhận đã hoàn tất trước khi đọc kết quả. Channel không bị dịch current position; nó vẫn ở `0`. Hàm `writeAllAt` lặp khi ghi ngắn, tự tăng offset bằng số byte đã ghi, và cố ý báo lỗi nếu không tiến triển để không lặp vô hạn. Việc phải phối hợp khi có ghi chồng lấn hoặc thay kích thước file vẫn áp dụng.

Ví dụ cố ý tạo tệp có đúng 8 byte trước khi hai task bắt đầu. Với tệp có thể mở rộng, thao tác positional ghi vượt cuối tệp vẫn có thể thay đổi kích thước; hợp đồng `FileChannel` có thêm quy tắc tuần tự hóa thao tác có thể thay đổi kích thước. **Điều đó không thay cho cấp phát offset chính xác**.

## 6. Khi phải dùng shared cursor: khóa toàn bộ thao tác nhiều bước

Một số thư viện hoặc interface chỉ cung cấp `seek` / `position` rồi `read` / `write`. Khi đó, hãy định nghĩa *một* lock chung cho mọi nơi truy cập cùng pointer:

```java
final Object cursorLock = new Object(); // field dùng chung của owner

byte[] readBlock(RandomAccessFile raf, long offset, int length)
        throws java.io.IOException {
    byte[] bytes = new byte[length];
    synchronized (cursorLock) {
        raf.seek(offset);
        raf.readFully(bytes);
    }
    return bytes;
}
```

Đây là **đoạn minh họa nằm trong class quản lý `cursorLock`**, không phải Java source độc lập. `readFully` có thể ném `EOFException` nếu không đủ bytes. Phải dùng **cùng một lock** cho toàn bộ code gọi `seek`, `read`, `write`, `getChannel().position` hay các thao tác tương đối trên chính handle đó. Khóa chỉ riêng `seek` hoặc chỉ `readFully` là chưa đủ:

```java
// SAI: hai khối synchronized rời nhau, thread khác có thể xen giữa.
synchronized (cursorLock) { raf.seek(offset); }
synchronized (cursorLock) { raf.readFully(bytes); }
```

Khối đồng bộ một lần phía trên bảo đảm các thread cùng tuân thủ lock không xen kẽ **giữa seek và readFully**. Tránh giữ lock trong các thao tác lâu không liên quan đến cursor; thực hiện xử lý dữ liệu phức tạp sau khi đọc xong nếu khả thi. Khóa này chỉ có hiệu lực trong JVM; một tiến trình khác dùng file riêng vẫn có thể sửa nội dung.

Có thể dùng `ReentrantLock` thay `synchronized` để hỗ trợ timeout hoặc interruptible acquisition; nguyên tắc không đổi: lock phải bao trùm toàn bộ đơn vị nghiệp vụ đang dựa vào pointer và mọi tác vụ dùng pointer phải tham gia.

**Không dựa vào `volatile long lastOffset` để bảo vệ chuỗi `seek + read`.** `volatile` chỉ tạo quy tắc visibility/order cho biến Java đó; nó không khóa trạng thái file pointer bên trong I/O handle. `AtomicLong.getAndAdd(length)` có thể dùng để **cấp phát offset không trùng nhau** cho các tác vụ nếu vùng file được quản lý đúng, nhưng bản thân nó cũng không biến các lần ghi nhiều bước thành một transaction.

## 7. Chọn kiến trúc phối hợp theo tình huống

| Tình huống | Thiết kế phù hợp | Giới hạn cần kiểm tra |
| --- | --- | --- |
| Nhiều task đọc các đoạn khác nhau của một file ít thay đổi | Dùng `FileChannel.read(buffer, offset)`, buffer riêng từng task. | Mỗi task xử lý short read / EOF; file vẫn có thể bị writer ngoài chương trình thay đổi. |
| Nhiều task ghi các ô/record có offset đã được cấp phát và không chồng lấn | `FileChannel.write(buffer, offset)`, loop khi ghi ngắn; cấp phát offset tập trung. | Chống tràn offset, tránh `truncate` đồng thời, tránh APPEND, đảm bảo layout record. |
| Cùng một `RandomAccessFile` và cần chuỗi `seek → read/write` | Một khóa chung cho toàn bộ chuỗi; có thể gom thao tác trong một owner/service. | Mọi caller phải tuân thủ; các tác vụ có thể chờ lâu vì I/O. |
| Mỗi worker có thể mở một channel riêng tới cùng file | Một channel riêng cho mỗi worker, mỗi channel có position riêng. | Nội dung file vẫn chung; các writer overlap phải phối hợp; không suy ra atomicity của record. |
| Nhiều worker append record có độ dài biến đổi | Một writer duy nhất nhận record qua queue, hoặc bộ cấp phát offset/độ dài chính xác rồi positional writes. | Cần định nghĩa record framing, thứ tự, lỗi ghi dở và recovery; APPEND không bảo đảm tính nguyên tử trên mọi hệ. |
| Nhiều tiến trình cùng thao tác file | Thiết kế giao thức phối hợp liên tiến trình; cân nhắc file lock và kiểm tra semantics trên OS/filesystem. | File lock phụ thuộc OS; không giải quyết logic giữa thread trong cùng JVM. |
| Yêu cầu đọc snapshot nhất quán trong khi có writer | Khóa dữ liệu/record, snapshot riêng, copy-on-write hoặc versioning phù hợp. | Positional API không tự tạo snapshot hay bảo đảm nhóm nhiều lệnh là transaction. |

Nếu nhiều tác vụ dùng queue tới một owner duy nhất, owner có thể thực hiện các thao tác tương đối theo thứ tự xác định. Mẫu này thường dễ lý giải với log có record thay đổi độ dài hoặc binary format nhiều trường. Tuy nhiên, quản lý một worker ghi duy nhất vẫn phải xử lý partial write, lỗi I/O và quá trình shutdown hợp lý.

Một **channel riêng cho mỗi thread** giúp tách cursor nhưng không có nghĩa file riêng. Nếu hai channel cùng mở cùng path, cả hai vẫn có thể ghi lên cùng byte; thiếu phối hợp dữ liệu có thể gây lost update. Nếu một channel lấy từ `raf.getChannel()`, đó **không phải** channel độc lập với chính `raf`.

## 8. Lock nào bảo vệ cái gì?

Phân biệt ba tầng đồng bộ:

1. **Cursor lock trong JVM:** `synchronized` hoặc `ReentrantLock` bảo vệ state `position`/`seek` và các thao tác nhiều bước thông qua đúng owner I/O. Đây là lựa chọn khi cần phối hợp các thread chung pointer.
2. **Khóa dữ liệu/record trong ứng dụng:** đảm bảo hai tác vụ không sửa cùng bản ghi một cách mâu thuẫn. Có thể là lock theo record/range, phân vùng offset hoặc một writer. Cần bảo vệ cả chuỗi “đọc → tính toán → ghi” nếu logic là read-modify-write.
3. **File lock cấp OS qua `FileChannel.lock` / `tryLock`:** dùng để phối hợp với các tiến trình khác khi môi trường hỗ trợ đúng semantics. Javadoc nêu rõ file lock được giữ thay mặt **cả JVM**, không phù hợp để điều phối nhiều thread trong cùng JVM; hành vi advisory/mandatory và khả năng shared lock tùy OS.

Ví dụ, `channel.lock(0, 100, false)` có thể xin khóa riêng trên phạm vi file, nhưng **không** nên thay thế `synchronized(cursorLock)` bằng lệnh này để bảo vệ hai thread trong một tiến trình. Trong JVM, yêu cầu lock chồng lấn còn có thể ném `OverlappingFileLockException`. Tùy hệ thống, tiến trình khác có thể bỏ qua khóa advisory, và filesystem mạng có thể có ràng buộc riêng. Giải pháp cần xác định cụ thể “không được xen kẽ cursor”, “không được ghi đè record” hay “phải phối hợp với process khác” trước khi chọn loại lock.

### Atomicity khác thread-safety và durability

- **Thread-safe**: API có thể được gọi đồng thời trong phạm vi bảo đảm của nó mà không làm hỏng trạng thái nội bộ.
- **Atomic theo nghiệp vụ**: một nhóm thao tác (ví dụ đọc số dư, cộng 1, ghi lại) xuất hiện như một đơn vị không bị chen ngang theo quy tắc ứng dụng. `FileChannel` không tự cung cấp transaction cho nhóm thao tác này.
- **Durable**: dữ liệu tồn tại sau lỗi máy/mất điện theo cơ chế lưu trữ; `FileChannel.force(...)` liên quan việc đẩy cập nhật xuống storage nhưng không tạo ra lock hay bảo đảm transaction logic giữa nhiều writer.

Không dùng `force(true)` để sửa race, và cũng không xem một mutex trong JVM là đủ để bảo đảm durability sau crash.

## 9. Các lỗi thường gặp và cách phát hiện

**Sai 1 — Đặt vị trí rồi thực hiện việc khác trước khi đọc/ghi.**

```java
channel.position(offset);
doSomethingThatMayUseTheSameChannel();
channel.read(buffer); // offset không còn chắc chắn như dự định
```

Đây là đoạn minh họa. Cách sửa tốt nhất khi có `FileChannel`: `channel.read(buffer, offset)`. Nếu không thể dùng positional API, đặt cả chuỗi dưới cùng lock và không gọi callback tùy ý khi đang giữ lock.

**Sai 2 — Coi thread-safe của `FileChannel` là transaction.**

```java
long old = channel.position();
channel.position(recordOffset);
channel.write(record);
channel.position(old);
```

Đây là đoạn minh họa. Giữa từng lời gọi, task khác vẫn có thể thay đổi position; việc khôi phục `old` còn có thể ghi đè vị trí mà task khác vừa thiết lập. Dùng write positional hoặc một lock bao trùm toàn bộ chuỗi khi thật sự cần lưu/khôi phục position.

**Sai 3 — Giả định một lần `read`/`write` xử lý đủ buffer.**

```java
// Kết quả có thể nhỏ hơn buffer.remaining() trước lời gọi.
int n = channel.write(buffer, offset);
```

Cần cập nhật `offset += n` và lặp nếu còn dữ liệu; với đọc, xử lý `-1` khi EOF, và chính sách xử lý `0` nếu không có tiến triển. Mỗi task phải sở hữu buffer cho đến khi lệnh I/O hoàn tất. Nếu file chứa record nhị phân, ghi dở có thể tạo record không hợp lệ; cần chiến lược lỗi và recovery.

**Sai 4 — Nhiều thread tự tính vị trí cuối file.**

```java
long offset = channel.size();
channel.write(buffer, offset);
```

Đây là đoạn minh họa. Hai task có thể cùng lấy một giá trị `size` trước khi bắt đầu ghi. Giải pháp là writer tập trung hoặc một cơ chế cấp phát vùng có tính nguyên tử ở tầng ứng dụng, đồng thời bảo vệ các thao tác resize/truncate liên quan. `StandardOpenOption.APPEND` cũng không nên được hiểu là mọi nền tảng đều bảo đảm một record nguyên tử.

**Sai 5 — Hiểu nhầm thread-safe của `ByteBuffer`.**

Dùng một buffer chung giữa hai task có thể khiến byte ghi sai hoặc `BufferUnderflowException` / `BufferOverflowException` do `position/limit` thay đổi bất ngờ. `FileChannel.write(src, absoluteOffset)` không khóa buffer cho caller. Tạo buffer riêng, hoặc truyền quyền sở hữu độc quyền cho worker tới khi hoàn tất.

**Sai 6 — Cố sửa race bằng file lock trong cùng JVM.**

`FileLock` là cơ chế phối hợp cấp file/OS với semantics hệ thống; không bảo vệ từng luồng khỏi thay đổi pointer qua cùng `RandomAccessFile`. Dùng mutex/owner trong JVM cho cursor, và thêm file lock nếu có yêu cầu phối hợp liên tiến trình được kiểm chứng.

**Sai 7 — Không xét việc đóng hoặc truncate từ thread khác.**

Khi channel bị đóng giữa I/O, có thể gặp `ClosedChannelException`/`AsynchronousCloseException` hoặc exception liên quan. Khi một worker gọi `truncate` hoặc thay độ dài, offset do worker khác dự định có thể không còn phù hợp. Quản lý vòng đời và phân quyền thao tác metadata, tránh cho worker đọc/ghi độc lập tùy ý đóng shared channel.

## 10. Checklist thiết kế và kiểm thử

Trước khi triển khai:

1. **Xác định sở hữu pointer:** object nào đang giữ pointer? Có `RandomAccessFile` và `getChannel()` dùng chung không? Có callback/helper nào cũng sử dụng nó?
2. **Xác định offset nghiệp vụ:** byte offset đến từ đâu, ai cấp phát, có bị trùng hoặc tràn số không? Có lẫn byte offset và character index không?
3. **Ưu tiên positional I/O:** khi có offset xác định, dùng `read/write(ByteBuffer, long)` với buffer riêng; bảo đảm lặp đủ bytes.
4. **Nếu bắt buộc dùng cursor:** lock nguyên khối `seek/position → read/write`; mọi caller dùng cùng lock; đừng dựa vào việc scheduler có vẻ tuần tự.
5. **Xác định vùng dữ liệu giao nhau:** hai task có thể ghi cùng byte, cùng record, cùng EOF hoặc gọi `truncate` không? Cần một giao thức cho phép/không cho phép overlap.
6. **Quy định ownership/vòng đời:** chỉ chủ sở hữu quyết định đóng channel; công việc bất đồng bộ hoàn tất trước khi close.
7. **Thử interleaving có kiểm soát:** dùng `CountDownLatch`/barrier hoặc trình tự giả lập để tái hiện A đặt pointer → B đổi pointer → A thao tác; không dùng `sleep` như chứng cứ correctness.
8. **Kiểm chứng file thật:** so sánh chính xác bytes ở từng offset, kiểm tra record framing, short I/O, EOF, chạy nhiều worker, và lỗi khi một thao tác thất bại giữa chừng.
9. **Đo độ đúng trước hiệu năng:** dùng positional API hay mutex theo yêu cầu nhất quán; tối ưu số channel/IOPS sau khi xác định correctness trên filesystem triển khai.

### Câu hỏi tự kiểm tra

**Câu 1.** Hai thread cùng dùng một `FileChannel`, mỗi thread gọi `position(...)` rồi `write(...)`. API thread-safe có bảo đảm cả hai ghi đúng offset không?

**Không.** Từng lời gọi hợp lệ, nhưng thread khác có thể xen vào giữa `position` và `write`. Dùng positional write hoặc khóa cả chuỗi.

**Câu 2.** Nếu dùng `channel.write(buffer, offset)` thì đã an toàn hoàn toàn với nhiều thread chưa?

**Chưa.** Nó loại bỏ tranh chấp con trỏ của channel, nhưng hai thread ghi chồng vùng byte, chia sẻ một `ByteBuffer`, thay kích thước file hay cùng cập nhật record vẫn cần phối hợp.

**Câu 3.** `raf.seek(10)` có làm đổi `raf.getChannel().position()` không?

**Có.** `RandomAccessFile` và channel của chính nó dùng chung vị trí. Ngược lại, `channel.read(dst, 20)` là đọc theo vị trí tuyệt đối nên không làm đổi pointer chung.

**Câu 4.** `FileChannel.lock()` có thay được `synchronized` để bảo vệ file pointer giữa hai thread trong một JVM không?

**Không.** `FileLock` không được thiết kế cho đồng bộ thread trong cùng JVM; dùng lock ứng dụng trên toàn bộ thao tác nhiều bước.

**Câu 5.** Vì sao chương trình ghi hai vùng độc lập ở ví dụ `IndependentOffsets` trả `ABCDWXYZ` bất kể task nào xong trước?

Vì mỗi task dùng một offset tuyệt đối khác nhau, mỗi buffer độc lập, hai phạm vi byte không chồng lấn và chương trình chờ cả hai `Future` hoàn tất trước khi đọc file. Không tác vụ nào cần sửa `channel.position()`.

## Tài liệu chính thức

- [Java SE 21 — `FileChannel`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html): hợp đồng đa luồng, current position, positional read/write, giới hạn `APPEND`, file lock.
- [Java SE 21 — `RandomAccessFile`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/RandomAccessFile.html): `seek`, `getFilePointer` và quan hệ vị trí với `getChannel()`.
- [Java SE 21 — `FileLock`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileLock.html): file locking theo OS, không phù hợp để đồng bộ các thread cùng JVM.
- [Java SE 21 — `ByteBuffer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html): vị trí, giới hạn và cách dùng buffer cho I/O.

Điểm cần nhớ xuyên suốt: **quản lý pointer là quản lý trạng thái chia sẻ**. Chọn offset tuyệt đối nếu tác vụ độc lập; nếu workflow bắt buộc dựa vào pointer chung, hãy đồng bộ toàn bộ hành động có ý nghĩa nghiệp vụ, đồng thời bảo vệ dữ liệu khi nhiều thao tác có thể chồng vùng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="random-access-008">Khi nào chọn RandomAccessFile thay FileChannel</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào chọn RandomAccessFile thay FileChannel trong Java?

## 1. Câu trả lời thực dụng

Khi cần **truy cập ngẫu nhiên** tới một tệp (đọc/ghi tại một vị trí byte xác định), Java cung cấp cả `java.io.RandomAccessFile` và `java.nio.channels.FileChannel`. Cả hai đều có thể đọc/ghi ngoài thứ tự tuần tự. Khác biệt quan trọng nhất là **mức trừu tượng, cách thao tác dữ liệu, cách quản lý vị trí và các tính năng mở rộng**.

**Chọn `RandomAccessFile`** khi tác vụ tương đối đơn giản, thiên về đọc/ghi các giá trị nguyên thủy theo một con trỏ chung: `seek(offset)` rồi `readInt()`, `writeLong()`, `readFully(byte[])`. API gọn, dễ đọc, thường phù hợp cho tệp nhị phân nhỏ/vừa, cấu trúc bản ghi cố định, bài tập và công cụ bảo trì.

**Chọn `FileChannel`** khi cần `ByteBuffer`, đọc/ghi theo vị trí tuyệt đối mà **không đổi con trỏ kênh**, xử lý khối dữ liệu lớn, `transferTo/transferFrom`, `map` (memory mapping), `lock`, hoặc chia việc đọc/ghi cho nhiều thành phần sử dụng offset rõ ràng.

Không nên hiểu rằng “`RandomAccessFile` là API truy cập ngẫu nhiên, còn `FileChannel` chỉ đọc tuần tự”. **Cả hai đều hỗ trợ truy cập ngẫu nhiên.** `FileChannel` cũng có `position(long)` để di chuyển vị trí hiện tại.

| Nhu cầu | Thường chọn | Vì sao |
|---|---|---|
| Đọc/ghi một vài `int`, `long`, `double` ở offset cụ thể | `RandomAccessFile` | Có sẵn phương thức kiểu nguyên thủy, ít mã chuyển đổi |
| Thay trường dữ liệu trong bản ghi cố định | `RandomAccessFile` nếu thao tác đơn giản | `seek` + `writeInt` rất trực quan |
| Đọc/ghi nhiều vùng không liền nhau và muốn giữ nguyên vị trí hiện tại | `FileChannel` | `read(buffer, position)` / `write(buffer, position)` |
| Làm việc với buffer, batch I/O, gathering/scattering | `FileChannel` | API NIO làm việc trực tiếp với `ByteBuffer` |
| Sao chép khối byte giữa channel/file | `FileChannel` | `transferTo` / `transferFrom` |
| Memory-mapped file, khóa vùng tệp | `FileChannel` | `map`, `lock`, `tryLock` |
| Muốn dùng thư viện `DataInput` / `DataOutput` cũ | `RandomAccessFile` | Triển khai trực tiếp cả hai interface |
| Cần xử lý file rất lớn | Tùy thao tác, thường cân nhắc `FileChannel` | Cả hai dùng offset `long`; channel có thêm chiến lược I/O |
| Nhiều luồng đọc các vùng độc lập theo offset | Thường `FileChannel` với positional I/O, có phối hợp | Tránh việc nhiều tác vụ tự đổi một con trỏ chung |

“Thường chọn” là gợi ý thiết kế, **không phải bảo đảm hiệu năng**. Tốc độ còn tùy filesystem, độ lớn buffer, thiết bị lưu trữ, cách gọi API và mẫu truy cập; khi hiệu năng quan trọng, cần benchmark với dữ liệu thực tế.

## 2. Random access là gì?

Tệp có thể xem như dãy byte đánh chỉ số từ `0` đến `length - 1`. Truy cập ngẫu nhiên cho phép chuyển tới offset cụ thể để đọc hoặc sửa tại chỗ, không cần xử lý từ byte đầu tiên.

Giả sử tệp chứa ba số nguyên `int` liên tiếp, mỗi số dài 4 byte:

```text
Offset      0         4         8        12
            |---------|---------|---------|
Giá trị     |   10    |   20    |   30    |
            |---------|---------|---------|
                    ^
          Bản ghi số 2 bắt đầu ở byte 4
```

Muốn cập nhật số thứ hai thành `99`, ta cần ghi 4 byte từ offset `4`. Cả `RandomAccessFile` và `FileChannel` đều làm được. Nếu mỗi bản ghi có độ dài cố định `recordSize`, offset bản ghi thứ `index` (đếm từ 0) là:

```text
offset = headerSize + index * recordSize
```

Trong mã thực tế nên dùng `long` cho offset và tính toán cẩn thận, ví dụ `Math.multiplyExact((long) index, recordSize)` để tránh tràn số. Đảm bảo mỗi offset có nghĩa theo **định dạng tệp**, không được giả định một ký tự UTF-8 luôn có độ dài một byte.

## 3. Cách RandomAccessFile làm việc

`RandomAccessFile` nằm trong `java.io`, implements `DataInput`, `DataOutput` và `Closeable`. Nó có **con trỏ tệp (file pointer)** dùng chung cho các thao tác đọc/ghi có vị trí hiện tại:

- `getFilePointer()`: trả offset của thao tác kế tiếp.
- `seek(long pos)`: đặt file pointer về offset từ đầu tệp; offset không được âm.
- `readInt()`, `readLong()`, `readDouble()`, `readBoolean()`, `readFully(...)`: đọc dữ liệu và tiến file pointer theo số byte đã dùng.
- `writeInt(int)`, `writeLong(long)`, `write(byte[])`: ghi dữ liệu rồi tiến con trỏ.
- `length()`: trả kích thước tệp theo byte; `setLength(long)`: thay đổi độ dài tệp.
- `getChannel()`: lấy `FileChannel` liên kết với chính đối tượng này.

Ví dụ minh họa (đây là đoạn mã, giả định `file.bin` có định dạng ba số nguyên):

```java
try (RandomAccessFile raf = new RandomAccessFile("file.bin", "rw")) {
    raf.seek(4L);
    int second = raf.readInt();   // Đọc 4 byte ở offset 4; pointer thành 8

    raf.seek(4L);
    raf.writeInt(second + 1);     // Ghi lại 4 byte; pointer thành 8
}
```

Ưu điểm nằm ở chỗ `readInt/writeInt` lo sẵn việc kết hợp bốn byte thành `int` theo **big-endian**. Các phương thức của `DataInput/DataOutput` dùng biểu diễn nhị phân xác định, giúp mã cho bản ghi chứa giá trị nguyên thủy dễ đọc.

### Chế độ mở tệp

| Mode | Ý nghĩa |
|---|---|
| `"r"` | Chỉ đọc; tệp phải tồn tại |
| `"rw"` | Đọc và ghi; có thể tạo tệp nếu chưa tồn tại |
| `"rws"` | Như `rw`, yêu cầu cập nhật nội dung **và** metadata của tệp được đồng bộ xuống thiết bị lưu trữ theo ngữ nghĩa API |
| `"rwd"` | Như `rw`, yêu cầu đồng bộ **nội dung** tệp; không yêu cầu đồng bộ metadata theo cùng mức |

`"rws"` và `"rwd"` có thể gây chi phí I/O lớn nếu ghi rất thường xuyên. Chúng cũng không tạo ra bảo đảm giao dịch/atomic toàn cục, không thay thế cơ chế kiểm soát lỗi hay chiến lược lưu dữ liệu. Trên một số hệ thống, khả năng đảm bảo đồng bộ cuối cùng phụ thuộc thiết bị và filesystem.

### Các lưu ý riêng của RandomAccessFile

1. `readInt()` cần đủ **4 byte**, nếu tới EOF sớm sẽ ném `EOFException`. `read(byte[])` thì có thể đọc ít hơn yêu cầu; khi muốn lấy đúng N byte, dùng `readFully(byte[])` hoặc xử lý lặp.
2. `readUTF/writeUTF` dùng **modified UTF-8** với phần độ dài mã hóa giới hạn, **không phải** giao thức UTF-8 tùy ý dùng `StandardCharsets.UTF_8`. Hai đầu đọc/ghi phải thống nhất định dạng.
3. `readLine()` là API cũ không phù hợp để đọc văn bản UTF-8 tổng quát. Với văn bản nên cân nhắc `BufferedReader`/`Files.newBufferedReader` và charset rõ ràng.
4. `seek` đi tới vị trí ngoài EOF được phép, nhưng việc đọc tại đó sẽ gặp EOF; nếu ghi ở vị trí vượt EOF, tệp mở rộng và giá trị các byte khoảng trống trung gian không nên được giả định theo một mẫu cụ thể.
5. `setLength` khi thu nhỏ sẽ cắt bỏ phần cuối; nếu file pointer lớn hơn độ dài mới, file pointer được điều chỉnh về độ dài mới. Khi mở rộng, nội dung vùng mở rộng không được bảo đảm có giá trị tùy ý mà ứng dụng mong đợi.
6. `RandomAccessFile` có một **file pointer dùng chung**; nếu nhiều luồng `seek` rồi `read/write` trên cùng đối tượng mà không phối hợp, vị trí có thể thay đổi xen giữa hai lệnh, dẫn tới truy cập nhầm bản ghi.

## 4. Cách FileChannel làm việc

`FileChannel` nằm trong `java.nio.channels`. Thông thường, dùng `FileChannel.open(Path, OpenOption...)` để làm việc với tệp hoặc lấy channel từ `RandomAccessFile.getChannel()`.

`FileChannel` xử lý dữ liệu qua `ByteBuffer`. Chương trình tự chọn kích thước buffer, cách giải mã các giá trị và `ByteOrder`. Điều này nhiều mã hơn với một vài kiểu nguyên thủy, nhưng thuận tiện khi đọc/ghi theo khối hoặc tích hợp hệ sinh thái NIO.

```java
try (FileChannel channel = FileChannel.open(
        Path.of("file.bin"),
        StandardOpenOption.READ,
        StandardOpenOption.WRITE)) {

    ByteBuffer value = ByteBuffer.allocate(Integer.BYTES)
            .order(ByteOrder.BIG_ENDIAN);

    value.putInt(99);
    value.flip();                     // Chuyển từ ghi vào buffer sang đọc buffer

    while (value.hasRemaining()) {
        channel.write(value);         // Ghi từ vị trí hiện tại của channel
    }
}
```

Đoạn này là **minh họa**, cần import `Path`, `StandardOpenOption`, `ByteBuffer`, `ByteOrder`, `FileChannel`; đồng thời giả định tệp đã tồn tại. Trước khi muốn sửa **đúng offset 4**, phải gọi `channel.position(4L)` hoặc dùng lệnh ghi theo vị trí tuyệt đối. Không có `position(4L)` trong đoạn minh họa nên nó ghi tại **vị trí hiện tại khi mở**, thường là đầu tệp.

### Hai kiểu I/O của FileChannel

**I/O theo con trỏ hiện tại (sequential-position I/O):**

```java
channel.position(4L);
channel.read(buffer);
channel.write(buffer);
```

Đây là đoạn mô tả phương thức; các lệnh đọc/ghi tăng vị trí channel theo lượng byte thực tế được xử lý. Trước mỗi `write`, cần đặt `ByteBuffer.position/limit` đúng trạng thái.

**I/O theo vị trí tuyệt đối (positional I/O):**

```java
channel.read(buffer, 4L);
channel.write(buffer, 4L);
```

Hai thao tác trên sử dụng offset cung cấp và **không thay đổi channel.position()**. Đây là lợi thế khi nhiều logic thao tác các vùng tệp khác nhau. Tuy nhiên, một lời gọi `read/write` có thể xử lý **ít byte hơn** `buffer.remaining()`, nên đối với dữ liệu có độ dài cố định cần lặp, đồng thời tự tăng offset sau mỗi lần xử lý.

Các tính năng khác của `FileChannel`:

- `size()`, `truncate(size)`, `position(pos)`: kích thước, cắt bớt, thay con trỏ. **`truncate` không tự mở rộng** tệp nếu kích thước truyền vào lớn hơn hiện tại; điểm này khác `RandomAccessFile.setLength`.
- `force(boolean metaData)`: yêu cầu đồng bộ nội dung (và metadata nếu `true`) ra lưu trữ theo ngữ nghĩa API. Mức đảm bảo cuối cùng còn tùy loại tệp, filesystem và hệ thống.
- `transferTo` / `transferFrom`: truyền byte giữa channel, đôi khi có tối ưu từ OS, nhưng **không bảo đảm zero-copy** hoặc truyền hết trong một lời gọi.
- `map(FileChannel.MapMode, position, size)`: ánh xạ một vùng tệp thành `MappedByteBuffer` để truy cập bộ nhớ ánh xạ. Hữu ích cho kiểu truy cập nhất định nhưng cần cân nhắc page fault, kích thước ánh xạ, vòng đời mapping và tài nguyên.
- `lock` / `tryLock`: khóa file/vùng file. Việc khóa được tôn trọng tới mức nào phụ thuộc OS/filesystem; không xem đây là giải pháp duy nhất cho mọi tình huống đồng thời.
- `read(ByteBuffer[])` / `write(ByteBuffer[])`: thao tác scattering/gathering với nhiều buffer.

**Lưu ý quan trọng:** `FileChannel` có thể là API rất tốt mà không cần dùng `map` hay zero-copy; positional I/O thông thường đã là lý do hợp lệ để chọn nó.

## 5. Chương trình Java 17+ hoàn chỉnh: cùng thao tác, hai API

Ví dụ ghi ba số nguyên 4 byte theo thứ tự `10, 20, 30`, đọc số thứ hai, thay thành `99`, sau đó đọc lại. Hai cách có cùng kết quả dữ liệu; `FileChannel` cho thấy việc đọc/ghi theo offset tuyệt đối không làm thay đổi channel position.

Tạo tệp tên **`RandomAccessChoiceDemo.java`**:

```java
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class RandomAccessChoiceDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("random-access-demo-");
        Path rafPath = directory.resolve("raf.bin");
        Path channelPath = directory.resolve("channel.bin");

        try {
            usingRandomAccessFile(rafPath);
            usingFileChannel(channelPath);
        } finally {
            Files.deleteIfExists(rafPath);
            Files.deleteIfExists(channelPath);
            Files.deleteIfExists(directory);
        }
    }

    private static void usingRandomAccessFile(Path path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            raf.setLength(0);
            raf.writeInt(10);
            raf.writeInt(20);
            raf.writeInt(30);

            raf.seek(Integer.BYTES);
            System.out.println("RAF before: " + raf.readInt());

            raf.seek(Integer.BYTES);
            raf.writeInt(99);

            raf.seek(Integer.BYTES);
            System.out.println("RAF after: " + raf.readInt());
        }
    }

    private static void usingFileChannel(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer all = ByteBuffer.allocate(3 * Integer.BYTES)
                    .order(ByteOrder.BIG_ENDIAN);
            all.putInt(10).putInt(20).putInt(30);
            all.flip();

            while (all.hasRemaining()) {
                channel.write(all);
            }
            // Sau 12 byte ghi tuần tự, position() là 12.

            long secondOffset = Integer.BYTES;
            System.out.println("Channel before: "
                    + readIntAt(channel, secondOffset));

            ByteBuffer replacement = ByteBuffer.allocate(Integer.BYTES)
                    .order(ByteOrder.BIG_ENDIAN);
            replacement.putInt(99);
            replacement.flip();
            writeFullyAt(channel, replacement, secondOffset);

            System.out.println("Channel after: "
                    + readIntAt(channel, secondOffset));
            System.out.println("Channel position: " + channel.position());
        }
    }

    private static int readIntAt(FileChannel channel, long offset)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN);
        long position = offset;

        while (buffer.hasRemaining()) {
            int count = channel.read(buffer, position);
            if (count < 0) {
                throw new EOFException("Not enough bytes for int");
            }
            if (count == 0) {
                throw new IOException("No progress while reading");
            }
            position += count;
        }

        buffer.flip();
        return buffer.getInt();
    }

    private static void writeFullyAt(
            FileChannel channel, ByteBuffer buffer, long offset)
            throws IOException {
        long position = offset;

        while (buffer.hasRemaining()) {
            int count = channel.write(buffer, position);
            if (count == 0) {
                throw new IOException("No progress while writing");
            }
            position += count;
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa mã nguồn:

```bash
javac RandomAccessChoiceDemo.java
java RandomAccessChoiceDemo
```

Trên filesystem hỗ trợ các thao tác tạo/ghi/xóa tệp thông thường, stdout chính xác là:

```text
RAF before: 20
RAF after: 99
Channel before: 20
Channel after: 99
Channel position: 12
```

Giải thích từng điểm:

1. `Integer.BYTES` bằng `4`. Số thứ hai bắt đầu ở offset `4`.
2. `RandomAccessFile.readInt()` đọc đúng 4 byte từ con trỏ đã `seek`; để đọc lại cùng vị trí sau khi sửa phải `seek` lần nữa.
3. `ByteBuffer.putInt` và `getInt` xử lý cùng dữ liệu big-endian. `flip` đặt lại vị trí/giới hạn để buffer có thể được tiêu thụ đúng cách.
4. `FileChannel` đã ghi tuần tự 12 byte, nên vị trí hiện tại bằng `12`. Cả `readIntAt` và `writeFullyAt` gọi positional I/O với `offset=4`, vì vậy vị trí channel **vẫn bằng 12**.
5. Hai hàm helper dùng vòng lặp vì lời gọi `FileChannel.read/write` không bảo đảm xử lý toàn bộ buffer trong một lần. Kiểm tra EOF và tình trạng không tiến thêm byte giúp ví dụ tránh vòng lặp vô hạn.

Ví dụ chủ động dùng **hai tệp riêng**, vì mục tiêu là minh họa cùng định dạng bằng hai API, tránh việc hai lần chạy ảnh hưởng dữ liệu của nhau. Đường dẫn thư mục tạm là ngẫu nhiên nhưng không được in ra, nên stdout không phụ thuộc tên thư mục.

## 6. RandomAccessFile và FileChannel có thể dùng chung một tệp

`RandomAccessFile.getChannel()` trả về một `FileChannel` liên kết với cùng đối tượng tệp. **File pointer của RandomAccessFile và position của channel liên kết sẽ cùng phản ánh một vị trí**, khi một bên dùng API làm thay đổi vị trí hiện tại.

Ví dụ minh họa (giả định tệp tồn tại; chưa phải chương trình độc lập):

```java
try (RandomAccessFile raf = new RandomAccessFile("shared.bin", "rw")) {
    FileChannel channel = raf.getChannel();

    raf.seek(8L);
    System.out.println(channel.position());  // 8

    channel.position(12L);
    System.out.println(raf.getFilePointer()); // 12
}
```

Đừng dùng `raf.seek(...)` ở một nơi và `channel.read(...)` ở nơi khác trên cùng đối tượng mà tưởng rằng hai con trỏ độc lập. Một thao tác positional `channel.read(buffer, offset)` không thay position, nhưng các thao tác phụ thuộc position và `seek` có thể ảnh hưởng nhau.

Đặc biệt, tránh đóng riêng channel được `raf.getChannel()` rồi tiếp tục giả định có thể dùng RAF như cũ. Hãy thiết kế rõ **quyền sở hữu tài nguyên** và đóng đối tượng bao ngoài theo try-with-resources.

## 7. Các tình huống chọn API chi tiết

### Tình huống A — Tệp cấu hình nhị phân vài trăm bản ghi cố định

Ví dụ mỗi bản ghi chứa `long id`, `int status`, `double score` (tổng 20 byte nếu không có padding). Ứng dụng cần đọc bản ghi thứ `i` rồi cập nhật `status`.

`RandomAccessFile` phù hợp khi chỉ có một luồng hoặc đã bảo vệ phần `seek + read/write`, định dạng không phức tạp và không cần chức năng channel đặc biệt. Có thể viết `raf.seek(offset)`, `raf.readLong()`, `raf.readInt()`, `raf.readDouble()` một cách trực quan.

Cần ghi rõ endianness, byte offset của từng trường, version của định dạng, giới hạn kích thước file và hành vi khi dữ liệu bị cắt. Dùng `Math.addExact` và `Math.multiplyExact` nếu offset tính từ input không tin cậy.

### Tình huống B — Dịch vụ đọc nhiều vùng tệp theo offset độc lập

Giả sử dịch vụ nhận yêu cầu đọc 4 KiB ở các offset khác nhau. `FileChannel.read(buffer, offset)` thường phù hợp hơn: mỗi yêu cầu mang offset riêng, không cần tự gọi `position(offset)` rồi đọc từ con trỏ chung.

Tuy vậy, “offset riêng” **không** tự bảo đảm an toàn nếu nhiều tác vụ ghi đè cùng vùng, cắt/đổi kích thước tệp, hoặc quản lý vòng đời channel sai. Cần đồng bộ/thiết kế bất biến dữ liệu, và kiểm soát việc đóng channel khi yêu cầu đang chạy.

### Tình huống C — Sao chép vùng lớn, xử lý buffer hoặc ánh xạ bộ nhớ

Khi cần truyền dữ liệu sang một `WritableByteChannel`, đọc nhiều buffer, hoặc truy cập lặp nhiều vùng bằng mapping, `FileChannel` cung cấp các API chuyên biệt. `transferTo` có thể tận dụng tối ưu của hệ điều hành trên một số trường hợp, nhưng phải kiểm tra số byte thực sự truyền và lặp nếu cần.

Memory mapping không phải lựa chọn luôn nhanh hơn. Với file lớn và pattern ngẫu nhiên, nên xét page fault, bộ nhớ ảo, khả năng thay đổi kích thước tệp, thời điểm dữ liệu được flush và vòng đời mapping. Benchmark từng phương án trong điều kiện vận hành.

### Tình huống D — Chỉ cần sửa một trường nguyên thủy rồi đóng

Nếu thao tác một lần, định dạng là big-endian, điều kiện filesystem đơn giản, `RandomAccessFile` tiết kiệm mã và ít điểm sai hơn việc chuẩn bị `ByteBuffer`. Nhiều dòng mã hơn không làm `FileChannel` sai; nó chỉ có thể không cần thiết cho bài toán rất nhỏ.

### Tình huống E — Có sẵn hệ thống xử lý ByteBuffer/NIO

Nếu tầng I/O, serializer hoặc parser hiện có sử dụng `ByteBuffer`, chọn `FileChannel` thường giúp giảm chuyển đổi giữa `byte[]` và buffer, và thuận tiện để tích hợp với cơ chế NIO đang dùng.

### Tình huống F — Yêu cầu đọc/ghi bất đồng bộ

Không nên chọn API chỉ vì tên chứa “Channel”. **`FileChannel` và `RandomAccessFile` đều là API I/O đồng bộ** trong cách dùng thông thường. Nếu cần mô hình bất đồng bộ thực sự, hãy xem `AsynchronousFileChannel` và thiết kế xử lý completion/future phù hợp; hiệu quả và cách triển khai vẫn phụ thuộc nền tảng.

## 8. Hiệu năng, bộ nhớ và đồng thời

**Không có “nhanh hơn tuyệt đối”.** `RandomAccessFile` có thể hiệu quả cho các thao tác nguyên thủy đơn giản, trong khi `FileChannel` có lợi thế API cho khối lớn, direct buffers, mapping, transfer và positional I/O. Không thể khẳng định rằng chỉ cần đổi `RandomAccessFile` sang `FileChannel` sẽ tăng tốc.

Khi đánh giá hiệu năng:

- Đo với kích thước tệp, phân bố offset, tỉ lệ đọc/ghi, filesystem và phần cứng thực tế.
- Tránh so sánh một bên đọc từng byte với một bên đọc theo buffer lớn: khác biệt đó phần lớn đến từ kích thước I/O.
- Phân biệt **heap buffer** (`ByteBuffer.allocate`) và **direct buffer** (`ByteBuffer.allocateDirect`); direct buffer có chi phí cấp phát/quản lý riêng và không phải mặc định tốt hơn.
- Phân biệt latency đơn lẻ và throughput; các phương thức đồng bộ dữ liệu bền vững như `"rws"/"rwd"` hoặc `force` có thể thay đổi đáng kể hiệu năng.
- Không coi file lock là thay thế mọi đồng bộ trong cùng tiến trình. Cách khóa vùng và mức độ bắt buộc/tư vấn phụ thuộc OS/filesystem.

`RandomAccessFile` có `seek` + thao tác theo con trỏ; mỗi cặp lệnh đó **không tự biến thành một thao tác nguyên tử** đối với nhiều luồng. Với `FileChannel`, positional read/write giảm phụ thuộc con trỏ chung, nhưng ứng dụng vẫn phải quản lý truy cập trùng vùng, truncate, lock, xử lý partial I/O và close đúng lúc.

## 9. Sai lầm thường gặp

1. **Nghĩ FileChannel không random access:** thực tế có cả `position(long)` và `read/write(ByteBuffer, long)`.
2. **Quên `ByteBuffer.flip()`:** sau khi `putInt`, buffer đang ở chế độ ghi; nếu không `flip`/điều chỉnh giới hạn, lệnh ghi kênh có thể không ghi dữ liệu mong đợi.
3. **Nghĩ `read/write(ByteBuffer)` luôn xử lý hết:** phải kiểm tra return value, remaining, EOF và lặp khi định dạng yêu cầu đúng N byte.
4. **Nhầm thứ tự byte:** `RandomAccessFile.readInt/writeInt` dùng big-endian; `ByteBuffer` mặc định big-endian nhưng có thể bị đổi bằng `order(...)`. Hai bên phải thống nhất.
5. **Nhầm ký tự với byte:** offset random access là **byte offset**. Với UTF-8, một ký tự có thể chiếm nhiều byte, nên không thể tính offset ký tự bằng phép nhân cố định tùy tiện.
6. **Dùng `seek` khi nhiều luồng chia sẻ file pointer:** thao tác xen kẽ có thể đọc/ghi sai vị trí. Chọn cơ chế đồng bộ hoặc positional I/O thích hợp.
7. **Nhầm `truncate` với `setLength`:** `FileChannel.truncate` chỉ thu nhỏ nếu size nhỏ hơn hiện tại; `RandomAccessFile.setLength` có thể mở rộng.
8. **Mở `RandomAccessFile("rw")` rồi tưởng sẽ tự xóa nội dung cũ:** mode `"rw"` không tự truncate; muốn thay mới toàn bộ tệp phải chủ động `setLength(0)` hoặc dùng chính sách phù hợp.
9. **Chọn API dựa trên tuyên bố “zero-copy luôn nhanh”:** `transferTo/From` không bảo đảm luôn zero-copy, và có thể truyền ít hơn số byte yêu cầu.
10. **Quên giới hạn định dạng:** `writeUTF/readUTF` là modified UTF-8 với giới hạn riêng; không dùng thay thế vô điều kiện cho văn bản UTF-8 chuẩn.
11. **Dùng `force` hoặc `"rws"` như bảo đảm giao dịch:** đồng bộ dữ liệu không đồng nghĩa atomic nhiều bản ghi hay phục hồi hoàn chỉnh khi mất điện.
12. **Bỏ qua ngoại lệ/cleanup:** dùng try-with-resources, phân biệt EOF và lỗi I/O, và xác nhận dữ liệu khi đọc bản ghi bị thiếu/corrupt.

## 10. Checklist ra quyết định

Trả lời bốn câu hỏi này:

1. **Dữ liệu được đọc/ghi thế nào?** Một vài primitive tại offset cố định → nghiêng về `RandomAccessFile`; khối byte/`ByteBuffer` → nghiêng về `FileChannel`.
2. **Có cần vị trí đọc/ghi tuyệt đối độc lập con trỏ hiện tại?** Nếu có, `FileChannel.read/write(buffer, position)` thuận tiện hơn.
3. **Có cần transfer, mapping, locking, gathering/scattering hoặc hệ sinh thái NIO?** Nếu có, `FileChannel` là lựa chọn tự nhiên.
4. **Có yêu cầu đồng thời, crash consistency và hiệu năng đặc biệt không?** Thiết kế phối hợp luồng, đồng bộ lưu trữ và benchmark trước khi quyết định; tên API không giải quyết thay kiến trúc.

**Quy tắc dễ nhớ:** `RandomAccessFile` tối ưu cho **sự đơn giản khi thao tác primitive + seek**; `FileChannel` tối ưu cho **sự linh hoạt khi thao tác buffer và offset**. Ưu tiên API giải quyết đúng nhu cầu với ít độ phức tạp nhất.

## 11. Câu hỏi tự kiểm tra

**Câu 1:** Một tệp chứa 1.000 bản ghi, mỗi bản ghi đúng 24 byte, ứng dụng một luồng chỉ cập nhật một `int` ở bản ghi thứ `i`. Chọn gì?  
**Gợi ý:** `RandomAccessFile` thường đủ đơn giản; tính `offset = header + i * 24 + fieldOffset` bằng `long`, `seek` rồi `writeInt`; kiểm tra kích thước, chỉ số và định dạng.

**Câu 2:** Đọc luân phiên 100 vùng khác nhau, mỗi vùng 8 KiB, không muốn thay một con trỏ chung. Chọn gì?  
**Gợi ý:** `FileChannel` với `read(ByteBuffer, long position)` và vòng lặp đảm bảo đủ byte; quản lý buffer/offset cho từng yêu cầu.

**Câu 3:** Sau `channel.write(buffer, 128)`, `channel.position()` có tự đổi sang 128 + số byte đã ghi không?  
**Không.** Lời gọi ghi theo vị trí tuyệt đối không thay position của channel.

**Câu 4:** Dùng `RandomAccessFile("rw")` và gọi `seek(0)` để ghi 4 byte vào tệp dài 1 MiB. Tệp có tự giảm còn 4 byte không?  
**Không.** Ghi đè 4 byte đầu không tự cắt bỏ dữ liệu còn lại; có thể dùng `setLength(4)` nếu định dạng và mục tiêu yêu cầu.

**Câu 5:** Muốn mở rộng một tệp lên 4 KiB bằng API riêng cho từng loại, `raf.setLength(4096)` và `channel.truncate(4096)` có tương đương không?  
**Không.** `setLength` có thể mở rộng; `truncate` không mở rộng nếu size lớn hơn kích thước hiện tại.

## 12. Tóm tắt

- **`RandomAccessFile`:** file pointer + `seek` + API `DataInput/DataOutput` cho primitive; dễ hiểu với định dạng bản ghi nhị phân đơn giản.
- **`FileChannel`:** `ByteBuffer`, vị trí tuyệt đối, truyền dữ liệu, memory mapping, locking và các kỹ thuật NIO; phù hợp khi cần kiểm soát I/O chi tiết.
- **Cả hai đều random access, dùng offset `long`, có thể phát sinh lỗi I/O và cần đóng tài nguyên.** Chọn theo cấu trúc dữ liệu, đặc tính truy cập và yêu cầu triển khai, không theo giả định hiệu năng chung.


</details>

- [Quay lại đầu trang](#back-to-top)
