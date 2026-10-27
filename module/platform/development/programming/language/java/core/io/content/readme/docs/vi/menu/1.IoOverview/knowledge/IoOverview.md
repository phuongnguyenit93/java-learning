<a id="back-to-top"></a>

# Tổng quan I/O

## Menu
- [I/O là gì và vai trò của nguồn, đích và dữ liệu](#io-overview-001)
- [Phân biệt dữ liệu nhị phân với văn bản](#io-overview-002)
- [Luồng dữ liệu, tài nguyên hệ điều hành và vòng đời sử dụng](#io-overview-003)
- [Phân biệt stream I/O với java.util.stream.Stream](#io-overview-004)
- [Bản đồ java.io, java.nio và NIO.2](#io-overview-005)
- [Tuần tự, truy cập ngẫu nhiên, blocking và non-blocking](#io-overview-006)
- [Kiến thức tiên quyết và trình tự học đề xuất](#io-overview-007)
- [Phạm vi Java Core và khác biệt theo phiên bản JDK](#io-overview-008)

## <a id="io-overview-001">I/O là gì và vai trò của nguồn, đích và dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# I/O là gì? Vai trò của nguồn, đích và dữ liệu

Trong Java, **I/O (Input/Output — nhập/xuất)** là hoạt động chương trình **nhận dữ liệu từ bên ngoài** hoặc **đưa dữ liệu ra bên ngoài** ranh giới xử lý của nó. “Bên ngoài” không nhất thiết là Internet hay thiết bị vật lý: một tệp, bàn phím, kết nối mạng, tiến trình khác và vùng nhớ chứa sẵn byte đều có thể tham gia I/O.

Một hoạt động I/O luôn nên được mô tả bằng ba thành phần:

- **Nguồn (source):** nơi dữ liệu được đọc hoặc nhận.
- **Dữ liệu (data):** nội dung di chuyển từ nguồn sang đích; cần hiểu định dạng, kích thước và cách mã hóa của nó.
- **Đích (destination):** nơi dữ liệu được ghi hoặc gửi đến.

**Chiều “input” và “output” được xác định theo góc nhìn của chương trình đang xét**, không phải theo tên thiết bị.

## 1. Phân biệt input và output

- **Input:** dữ liệu đi **vào chương trình**. Ví dụ: chương trình đọc nội dung `config.txt`, nhận dòng người dùng nhập hoặc đọc byte từ một kết nối.
- **Output:** dữ liệu đi **ra khỏi chương trình**. Ví dụ: chương trình ghi `report.txt`, hiển thị kết quả ra standard output hoặc gửi một phản hồi qua mạng.
- Cùng một chương trình thường làm cả hai: đọc một tệp nguồn, biến đổi nội dung, rồi ghi sang tệp đích.

```text
                    INPUT                          OUTPUT
[Nguồn: tệp/bàn phím/mạng] ---> [CHƯƠNG TRÌNH] ---> [Đích: tệp/màn hình/mạng]
                  dữ liệu đi vào               dữ liệu đi ra
```

Ví dụ, một ứng dụng chuyển đổi văn bản: `raw.txt` là **nguồn**, các byte/chuỗi đọc được là **dữ liệu đầu vào**; chương trình chuyển sang chữ hoa; `upper.txt` là **đích** chứa **dữ liệu đầu ra**. Nội dung đầu ra có thể khác nội dung đầu vào vì chương trình đã xử lý nó.

I/O không đồng nghĩa với tính toán. Phép `sum = a + b` xử lý dữ liệu đang có trong bộ nhớ nhưng tự nó không đọc từ nguồn bên ngoài hoặc ghi ra đích bên ngoài. Ngược lại, `System.out.println(sum)` thực hiện output.

## 2. Nguồn (source): chương trình lấy dữ liệu ở đâu?

Nguồn có thể là:

| Nguồn | Ví dụ Java | Điều chương trình nhận |
|---|---|---|
| Bàn phím hoặc standard input | `System.in` | Byte đi vào luồng nhập chuẩn; có thể được giải mã thành ký tự |
| Tệp trên hệ thống tệp | `Files.newInputStream(path)` hoặc `Files.readString(path)` | Byte hoặc văn bản được đọc từ tệp |
| Kết nối mạng | `socket.getInputStream()` | Byte do đầu bên kia truyền tới |
| Vùng nhớ | `ByteArrayInputStream` | Byte đã được đặt sẵn trong mảng |
| Dữ liệu được tạo từ thành phần khác | Một `InputStream` do thư viện cung cấp | Byte nhận qua giao diện đọc |

Một nguồn không nhất thiết “tự đẩy” dữ liệu vào Java: chương trình thường chủ động gọi `read()` hoặc một API đọc. Lời gọi đọc có thể cần chờ dữ liệu, gặp cuối luồng hoặc phát sinh lỗi. Đừng mặc định một lần đọc là đủ cho toàn bộ nội dung.

**Nguồn là vai trò tại một bước cụ thể, không phải thuộc tính cố định của một tệp.** `fileA.txt` là nguồn khi được đọc; nếu một bước khác ghi vào chính tệp đó, nó trở thành đích của bước ghi.

## 3. Đích (destination): dữ liệu được đưa đi đâu?

Đích có thể là:

| Đích | Ví dụ Java | Tác dụng |
|---|---|---|
| Standard output | `System.out` | In thông tin ra luồng xuất chuẩn, thường thấy trong terminal |
| Standard error | `System.err` | Gửi thông báo lỗi ra luồng lỗi chuẩn |
| Tệp | `Files.newOutputStream(path)` hoặc `Files.writeString(path, text)` | Lưu dữ liệu vào tệp |
| Kết nối mạng | `socket.getOutputStream()` | Gửi byte cho đầu bên kia |
| Vùng nhớ | `ByteArrayOutputStream` | Gom byte đầu ra trong bộ nhớ |

`System.out` **không luôn đồng nghĩa với màn hình**. Khi chạy `java Main > output.txt`, shell chuyển hướng standard output sang tệp; từ góc nhìn Java, chương trình vẫn ghi vào `System.out`.

Khi ghi, chương trình cần biết **đích có cho phép ghi không**, **ghi mới hay nối tiếp**, và **khi nào dữ liệu đã được chuyển từ bộ đệm tới tầng bên dưới**. Ghi ra một luồng không tự bảo đảm dữ liệu đã được lưu bền vững trên đĩa hoặc phía nhận đã xử lý nó.

## 4. Dữ liệu (data): thứ thực sự được truyền

I/O vận chuyển **dữ liệu**, không nhất thiết vận chuyển trực tiếp đối tượng Java theo nguyên dạng trong bộ nhớ. Hai cách nhìn quan trọng:

1. **Byte (nhị phân):** phù hợp với ảnh, file nén, âm thanh, dữ liệu giao thức, hoặc sao chép file bất kỳ. `InputStream` đọc byte; `OutputStream` ghi byte.
2. **Ký tự/văn bản:** phù hợp với chữ, dòng cấu hình, dữ liệu text. `Reader` đọc ký tự; `Writer` ghi ký tự. Khi lưu/truyền văn bản thành byte, cần một **bảng mã ký tự** (charset) như UTF-8.

Ví dụ, chuỗi Java `"Xin chào"` là văn bản, còn khi ghi tệp UTF-8, chuỗi được **mã hóa thành byte**. Khi đọc tệp thành chuỗi, các byte cần được **giải mã đúng bảng mã**. Nếu ghi bằng UTF-8 nhưng đọc theo bảng mã khác, chữ tiếng Việt có thể bị sai. Đối với tệp nhị phân, không tùy tiện chuyển mọi byte thành `String`.

Ngoài nội dung, cần biết **ranh giới dữ liệu**: một tệp có điểm cuối, một thông điệp mạng có thể cần quy tắc độ dài hoặc dấu phân cách, và việc đọc vài byte không nhất thiết nhận được “một thông điệp” hoàn chỉnh.

```text
VĂN BẢN                   BYTE                       VĂN BẢN
"Xin chào" --mã hóa UTF-8--> [dãy byte] --giải mã UTF-8--> "Xin chào"
```

## 5. Dòng chảy I/O trong Java

Với thao tác sao chép tệp:

```text
source.txt  --đọc byte-->  bộ đệm trong Java  --ghi byte-->  destination.txt
    NGUỒN                     DỮ LIỆU                      ĐÍCH
```

- `InputStream.read(byte[])` yêu cầu lấy byte từ nguồn vào bộ đệm. Giá trị trả về là **số byte thực tế đã đọc**, hoặc `-1` khi hết luồng. Không được giả định mỗi lần đọc đầy bộ đệm.
- `OutputStream.write(buffer, 0, count)` chỉ ghi `count` byte hợp lệ vừa đọc. Ghi cả mảng có thể đưa thêm dữ liệu cũ/không hợp lệ.
- Bộ đệm là vùng giữ dữ liệu tạm thời giữa lần đọc và ghi. **Bộ đệm không phải nguồn/đích cuối cùng** của ví dụ này.
- `try-with-resources` đóng các luồng khi rời khối, kể cả khi xảy ra ngoại lệ. `IOException` biểu thị những lỗi I/O cần được xử lý hoặc khai báo.

Trong Java có nhiều mức API. `Files.readString(path, UTF_8)`/`Files.writeString(path, text, UTF_8)` tiện cho văn bản kích thước phù hợp với bộ nhớ; `InputStream`/`OutputStream` cho phép xử lý dữ liệu theo từng khối, thích hợp khi muốn sao chép byte không phụ thuộc định dạng. `Reader`/`Writer` thích hợp khi cần xử lý ký tự thay vì byte.

## 6. Ví dụ chạy được: nguồn tệp, đích tệp, dữ liệu là byte

Ví dụ dùng Java 17+, tạo hai tệp tạm, ghi một văn bản UTF-8 vào tệp nguồn, **sao chép các byte** từ nguồn sang đích bằng luồng I/O, rồi đọc đích để kiểm tra. File nguồn và đích được xóa khi chương trình kết thúc.

Lưu mã sau thành **`IoRolesDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class IoRolesDemo {
    public static void main(String[] args) throws IOException {
        Path source = Files.createTempFile("io-source-", ".txt");
        Path destination = Files.createTempFile("io-destination-", ".txt");

        try {
            Files.writeString(source, "Xin chào I/O\n", StandardCharsets.UTF_8);

            try (InputStream input = Files.newInputStream(source);
                 OutputStream output = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int count;

                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }

            System.out.print(Files.readString(destination, StandardCharsets.UTF_8));
            System.out.println("Cùng dữ liệu: " +
                    Arrays.equals(Files.readAllBytes(source),
                                  Files.readAllBytes(destination)));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(destination);
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp mã nguồn:

```bash
javac -encoding UTF-8 IoRolesDemo.java
java IoRolesDemo
```

Kết quả chuẩn khi chương trình chạy thành công:

```text
Xin chào I/O
Cùng dữ liệu: true
```

Đọc ví dụ theo **nguồn → dữ liệu → đích**:

1. `Files.writeString` chuẩn bị nội dung trong **tệp nguồn**. Ở bước chuẩn bị này, `source` là **đích của thao tác ghi**.
2. `Files.newInputStream(source)` mở luồng **đọc từ tệp nguồn**; ở bước sao chép, `source` có vai trò **nguồn**.
3. `input.read(buffer)` nhận tối đa `buffer.length` byte mỗi lần; biến `count` cho biết lượng byte hợp lệ. Dữ liệu lúc này chỉ tạm nằm trong `buffer`.
4. `Files.newOutputStream(destination)` mở luồng ghi tới **tệp đích**; `output.write(buffer, 0, count)` đưa các byte vừa đọc sang đó.
5. `Files.readString(destination, UTF_8)` đọc **lại tệp đích** thành văn bản để in ra standard output. Lúc này `destination` đổi vai trò thành **nguồn của bước kiểm tra**, còn `System.out` là **đích của bước in**.
6. `Arrays.equals` so sánh các byte thực sự trong hai tệp; `true` chứng minh nội dung byte bằng nhau trong lần chạy này. Việc đóng luồng và xóa tệp tạm làm rõ vòng đời tài nguyên.

Điểm cốt lõi: **một đường đi I/O có thể gồm nhiều bước; vai trò nguồn/đích được xác định riêng cho mỗi bước**. “Dữ liệu” có thể biến đổi giữa các bước, nhưng ví dụ sao chép byte trên cố ý giữ nguyên.

## 7. Ví dụ ngắn: người dùng nhập, chương trình trả kết quả

`System.in` là **nguồn**; dòng nhập được giải mã thành `String` là **dữ liệu**; `System.out` là **đích**. Đoạn dưới là **phần thân phương thức**, cần đặt vào phương thức Java có `throws IOException` hoặc xử lý ngoại lệ và có các import tương ứng.

```java
BufferedReader reader = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8));

System.out.print("Nhập tên: ");
String name = reader.readLine();
System.out.println("Chào, " + name);
```

Import cần dùng: `java.io.BufferedReader`, `java.io.InputStreamReader` và `java.nio.charset.StandardCharsets`. Người dùng gõ `Lan` rồi Enter thì dòng phản hồi là `Chào, Lan`; lời nhắc và nội dung người dùng tự gõ không nên nhầm với dữ liệu do chương trình đọc hoặc xuất. Trong ví dụ này không đóng `System.in` vì đây là luồng chuẩn thường được các phần khác của chương trình tiếp tục sử dụng.

Nếu đầu vào đã được chuyển hướng từ tệp, `reader.readLine()` vẫn đọc standard input. Tương tự, standard output có thể được chuyển hướng sang tệp mà không cần sửa câu lệnh `println`.

## 8. Các nhầm lẫn thường gặp

| Nhầm lẫn | Cách hiểu đúng |
|---|---|
| “Input chỉ là nhập từ bàn phím; output chỉ là in màn hình.” | Tệp, mạng, bộ nhớ, luồng chuẩn đều có thể đóng vai trò nguồn hoặc đích. |
| “Nguồn/đích là thuộc tính cố định của một đối tượng.” | Vai trò phụ thuộc **thao tác**: một tệp có thể là đích lúc ghi, nguồn lúc đọc lại. |
| “Một lần `read` lấy hết dữ liệu.” | `read` có thể trả về ít hơn số byte yêu cầu; phải lặp đến điểm cuối hoặc đủ độ dài mong muốn. |
| “`read` trả về `0` nghĩa là hết luồng.” | Với `InputStream.read(byte[])` và bộ đệm khác rỗng, dấu hiệu kết thúc là **`-1`**. |
| “`write(buffer)` luôn đúng sau `read(buffer)`.” | Cần ghi đúng phần hợp lệ: `write(buffer, 0, count)`. |
| “Văn bản và byte là như nhau.” | Văn bản cần bảng mã khi chuyển sang/từ byte; UTF-8 giúp kiểm soát kết quả. |
| “In ra `System.out` chắc chắn hiển thị trên màn hình.” | Luồng chuẩn có thể được chuyển hướng sang tệp hoặc nơi nhận khác. |
| “Ghi xong có thể bỏ qua việc đóng tài nguyên.” | Dùng `try-with-resources` cho luồng mở từ file/socket; tài nguyên được giải phóng đúng vòng đời. |
| “Output thành công nghĩa là đầu nhận đã sử dụng dữ liệu.” | Ghi thành công vào luồng không chứng minh đích ở tầng khác đã xử lý hoặc dữ liệu đã bền vững. |

## 9. Tự kiểm tra hiểu bài

**Tình huống A:** Chương trình đọc `orders.csv`, tính tổng tiền và ghi `total.txt`. Xác định các thành phần.

- Bước đọc: **nguồn** = `orders.csv`; **dữ liệu đầu vào** = các byte/ký tự biểu diễn đơn hàng.
- Bước tính tổng: xử lý dữ liệu trong chương trình, tự nó không phải hoạt động I/O.
- Bước ghi: **đích** = `total.txt`; **dữ liệu đầu ra** = tổng tiền sau khi được chuyển sang biểu diễn để lưu.

**Tình huống B:** Chương trình tải dữ liệu từ mạng và lưu tệp. Kết nối mạng là nguồn hay đích?

- Với bước **nhận** dữ liệu: kết nối (luồng đọc) là **nguồn**, tệp được ghi là **đích**.
- Với bước **gửi yêu cầu** trước đó: kết nối (luồng ghi) lại là **đích**. Cùng một kết nối có thể tham gia cả hai chiều.

**Tình huống C:** Tại sao phải chỉ định UTF-8 khi ghi và đọc văn bản tiếng Việt?

- Vì ký tự trong `String` cần được **mã hóa** thành byte khi ghi và **giải mã** từ byte khi đọc. Dùng cùng bảng mã ngăn lỗi do giả định khác nhau giữa các môi trường.

### Ghi nhớ

Khi gặp bất kỳ tác vụ I/O nào, hãy hỏi: **Chương trình đang đọc hay ghi? Nguồn là gì? Đích là gì? Dữ liệu là byte hay ký tự, được biểu diễn và mã hóa ra sao?** Trả lời bốn câu hỏi này giúp chọn API Java thích hợp và tìm lỗi đúng chỗ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="io-overview-002">Phân biệt dữ liệu nhị phân với văn bản</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt dữ liệu nhị phân với văn bản trong Java I/O

## 1. Cùng lưu thành byte, khác cách diễn giải

Trong bộ nhớ, trên đĩa và khi truyền qua mạng, nội dung cuối cùng đều được biểu diễn bằng **byte**. **Dữ liệu văn bản (text)** là dữ liệu mà các byte được chuyển đổi thành ký tự theo một **bảng mã (charset/character encoding)**. **Dữ liệu nhị phân (binary)** theo cách phân loại I/O thường là dữ liệu được diễn giải theo cấu trúc byte riêng của một định dạng: ảnh, file nén, số nguyên, bản ghi giao thức, trường độ dài cố định...

Do đó **file text cũng là một dãy byte**. Sự khác nhau nằm ở *hợp đồng diễn giải*, không phải việc một file có dùng bit hay không:

- Với **text**, phải biết charset để mã hóa ký tự thành byte và giải mã byte thành ký tự.
- Với **binary có cấu trúc**, phải biết quy tắc byte: thứ tự trường, kích thước, endianness, phiên bản hoặc parser phù hợp.
- Có **định dạng hỗn hợp**: thông điệp HTTP có header dạng text và body có thể chứa JSON, ZIP, ảnh hoặc byte bất kỳ; file binary cũng có thể chứa trường chuỗi UTF-8.

Ví dụ, “Aé” có hai Unicode code point nhưng chiếm ba byte trong UTF-8:

~~~text
Ký tự            A        é
Code point       U+0041   U+00E9
UTF-8 hex        41       C3 A9
Toàn bộ byte     41 C3 A9
~~~

Giải mã dãy byte 41 C3 A9 bằng UTF-8 cho ra “Aé”; nếu giải mã bằng ISO-8859-1 lại thành “AÃ©”. **Bản thân byte không tự cho biết charset**: ứng dụng phải có quy ước, đặc tả hay metadata để xác định nó.

## 2. Khái niệm nền tảng

| Khái niệm | Cách hiểu | Ví dụ |
| --- | --- | --- |
| Bit/byte | Đơn vị dữ liệu thô, mỗi byte gồm 8 bit | Byte 0x41 |
| Unicode code point | Mã Unicode biểu diễn một đơn vị ký tự | é = U+00E9 |
| Charset/encoding | Quy tắc biến ký tự thành byte và ngược lại | UTF-8, UTF-16LE, ISO-8859-1 |
| String trong Java | Chuỗi ký tự trong bộ nhớ, không kèm “charset của file” | “Aé” |
| File format | Quy tắc tổ chức nội dung | CSV, JSON, PNG, ZIP |
| Extension/MIME | Tín hiệu/khai báo loại file, không bảo đảm nội dung | .txt, .png, image/png |

**String.length() của Java đếm đơn vị mã UTF-16 (char)**, không phải byte UTF-8 hay luôn là số Unicode code point. Một code point ngoài Basic Multilingual Plane có thể dùng hai char (surrogate pair); một ký tự người dùng nhìn thấy cũng có thể gồm nhiều code point ghép dấu. Dùng codePointCount(...) để đếm code point; dùng getBytes(charset).length hoặc đọc byte thực tế để biết kích thước sau mã hóa.

UTF-8 mã hóa Unicode thành 1–4 byte cho mỗi code point; ký tự ASCII dùng một byte tương ứng. UTF-16LE/UTF-16BE dùng quy tắc và byte order khác. **BOM (byte order mark)** là dấu hiệu tùy chọn: BOM UTF-8 có các byte EF BB BF, **UTF-8 không yêu cầu BOM**. Với UTF-16, BOM có thể dùng để biểu thị byte order; không có BOM thì cần biết encoding từ nguồn khác.

Kết thúc dòng cũng là byte: LF = 0A hex, CRLF = 0D 0A hex. Hai file hiển thị cùng chữ vẫn có thể khác byte. Khi cần checksum hoặc chữ ký số phải xét byte; khi so sánh text theo ngữ nghĩa, cần thống nhất charset và quy tắc xuống dòng/chuẩn hóa.

## 3. Trong Java chọn byte stream hay character stream?

| Nhu cầu | API Java | Điều cần lưu ý |
| --- | --- | --- |
| Copy file bất kỳ mà giữ nguyên byte | InputStream, OutputStream, Files.copy | Không đi qua String |
| Đọc/ghi dữ liệu byte | Files.readAllBytes, Files.write | Không tự giải mã |
| Đọc/ghi văn bản | Reader, Writer, Files.readString, Files.writeString | Chọn charset rõ ràng |
| Kết nối byte với ký tự | InputStreamReader, OutputStreamWriter | Charset là phần bắt buộc của hợp đồng |
| Đọc cấu trúc binary | DataInputStream, ByteBuffer, thư viện parser | Biết schema, byte order, version |

**InputStream/OutputStream làm việc với byte**; **Reader/Writer làm việc với ký tự**. InputStreamReader là cầu nối *byte → ký tự*, OutputStreamWriter là cầu nối *ký tự → byte*. Những cầu nối này hiểu encoding, nhưng **không** tự giải mã định dạng PNG, ZIP hoặc protobuf.

Với text được quy định là UTF-8, dùng StandardCharsets.UTF_8 ở cả hai chiều. Khi định dạng yêu cầu charset khác thì dùng đúng charset ấy. Tránh vô tình dựa vào encoding mặc định của môi trường nếu dữ liệu cần chạy qua nhiều hệ thống.

## 4. Ví dụ Java 17+ chạy độc lập

Lưu đoạn sau thành **BinaryVsTextDemo.java**, mã nguồn UTF-8:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class BinaryVsTextDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("io-text-", ".txt");
        try {
            String original = "Aé";
            Files.writeString(file, original, StandardCharsets.UTF_8);

            byte[] raw = Files.readAllBytes(file);
            String restored = Files.readString(file, StandardCharsets.UTF_8);

            System.out.println("utf16Units=" + original.length());
            System.out.println("codePoints="
                    + original.codePointCount(0, original.length()));
            System.out.println("utf8Bytes=" + raw.length);
            System.out.println("hex=" + HexFormat.of().formatHex(raw));
            System.out.println("restored=" + restored);
            System.out.println("roundTrip=" + original.equals(restored));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 BinaryVsTextDemo.java
java BinaryVsTextDemo
~~~

Stdout:

~~~text
utf16Units=2
codePoints=2
utf8Bytes=3
hex=41c3a9
restored=Aé
roundTrip=true
~~~

**Files.writeString** đã mã hóa String thành byte UTF-8. **Files.readAllBytes** trả về chính ba byte trong file, không diễn giải thành ký tự. **Files.readString** dùng UTF-8 để giải mã byte thành String. Độ dài 2 đơn vị UTF-16 và 3 byte UTF-8 không hề mâu thuẫn. Ví dụ tạo file tạm và xóa trong finally nên không cần đường dẫn phụ thuộc máy.

readAllBytes/readString thích hợp khi dữ liệu đủ nhỏ. Với file lớn, đọc toàn bộ file một lần có thể tốn nhiều RAM; hãy xử lý tuần tự bằng InputStream/Reader và buffer.

## 5. Những tình huống thực tế

### 5.1. Sao chép ảnh, ZIP hoặc bất kỳ file nào

Khi yêu cầu là sao chép nguyên nội dung, thao tác ở tầng byte. Đây là **mảnh code minh họa** đặt trong phương thức đã có hai biến Path source và destination, có import Files và xử lý IOException:

~~~java
try (var input = Files.newInputStream(source);
     var output = Files.newOutputStream(destination)) {
    input.transferTo(output);
}
~~~

transferTo truyền byte, không mã hóa hay giải mã. File PNG mở đầu bằng signature gồm các byte **89 50 4E 47 0D 0A 1A 0A** (hex); nếu xử lý PNG bằng Reader và Writer, byte không hợp lệ theo charset có thể bị thay thế khiến file ảnh hỏng. Đổi tên .png thành .txt cũng không biến dữ liệu ảnh thành văn bản.

### 5.2. Đọc CSV, JSON và TXT

Nếu hợp đồng nói rõ dữ liệu là text UTF-8, giải mã byte thành String:

~~~java
String json = Files.readString(path, StandardCharsets.UTF_8);
~~~

Đây chỉ là **mảnh code** khi đã có Path path và import phù hợp. Nó thực hiện **decode**, chưa phải **parse**. JSON cần parser JSON; CSV cần parser xử lý dấu ngoặc kép, dấu phân cách và newline trong một trường dữ liệu. Charset và cú pháp định dạng là hai trách nhiệm khác nhau.

### 5.3. Đọc số nhị phân

Chuỗi text “1234” có byte UTF-8 **31 32 33 34**. Một trường integer 32-bit trong định dạng binary có thể cũng chiếm bốn byte nhưng mang giá trị khác và có byte order riêng. Muốn đọc cần biết độ dài trường, signed/unsigned, big-endian/little-endian và version. DataInputStream hoặc ByteBuffer có thể hỗ trợ các thao tác đó; chúng không tự đoán schema của file chưa rõ định dạng.

### 5.4. Nhúng binary vào JSON

Nếu muốn gửi ảnh trong JSON, có thể dùng **Base64** để biến một dãy byte thành chuỗi ký tự ASCII, rồi phía nhận giải mã Base64 để phục hồi byte gốc. Base64 **không biến ảnh thành text theo nghĩa dữ liệu gốc**, không phải nén và không phải mã hóa bảo mật. Không nên tạo String từ byte ảnh tùy ý bằng một charset để đưa vào JSON vì có nguy cơ đổi byte.

### 5.5. Nội dung hỗn hợp

Một định dạng binary có thể chứa tên file, ghi chú hoặc metadata là chuỗi UTF-8; một tài liệu JSON lại có thể chứa trường Base64 của ảnh. Trong mỗi trường hợp, dùng parser đúng định dạng trước, rồi áp dụng decoding tại những trường mà đặc tả quy định là text.

## 6. Byte không hợp lệ và cách phát hiện lỗi charset

Nếu byte C3 A9 của “é” được giải mã theo ISO-8859-1, chữ sẽ hiện thành “Ã©” (mojibake). Nếu một chuỗi byte không hợp lệ theo UTF-8 được đưa qua new String(bytes, StandardCharsets.UTF_8), ký tự bị lỗi có thể được thay thế bằng **U+FFFD**. Nếu lại mã hóa và ghi ra file, các byte gốc có thể không phục hồi được.

Để **từ chối** byte không hợp lệ thay vì chấp nhận thay thế, dùng CharsetDecoder cùng CodingErrorAction.REPORT.

Tạo file **StrictUtf8Demo.java**:

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class StrictUtf8Demo {
    public static void main(String[] args) {
        byte[] input = {(byte) 0xC3, (byte) 0x28};

        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(input));
            System.out.println("valid UTF-8");
        } catch (CharacterCodingException e) {
            System.out.println("invalid UTF-8");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 StrictUtf8Demo.java
java StrictUtf8Demo
~~~

Stdout:

~~~text
invalid UTF-8
~~~

Byte C3 cần một byte tiếp nối theo quy tắc UTF-8; byte 28 không phải byte tiếp nối hợp lệ. Decoder ném lỗi, chương trình in “invalid UTF-8”. Tuy nhiên, **một dãy byte hợp lệ UTF-8 chưa đủ chứng minh nó là text**: nội dung binary có thể tình cờ gồm byte UTF-8 hợp lệ.

## 7. Nhận diện loại file khi không có thông tin đầy đủ

Không tồn tại phép thử tổng quát 100% chỉ từ nội dung byte để khẳng định “đây là text” hay “đây là binary”. Các byte **41 42 43** có thể biểu diễn chữ “ABC” theo ASCII/UTF-8, nhưng cũng có thể là ba byte của một bản ghi nhị phân.

Quy trình hợp lý:

1. **Xem hợp đồng dữ liệu**: tài liệu API, schema, giao thức, Content-Type và charset khi đáng tin, hoặc metadata của nguồn file.
2. **Đối chiếu định dạng**: header, magic bytes, version, parser; extension chỉ là gợi ý và có thể bị đổi.
3. **Nếu xác định là text**, chọn encoding được yêu cầu; dùng decoder báo lỗi nếu cần bảo vệ tính toàn vẹn.
4. **Nếu chưa rõ**, giữ bản gốc dưới dạng byte, xem hex dump hoặc hỏi đặc tả; đừng mở bằng text editor rồi ghi lại với encoding phỏng đoán.

Những đặc điểm như byte 00, tỷ lệ ký tự in được, ký tự điều khiển, BOM đều **chỉ là heuristic**. Văn bản UTF-16 có thể chứa byte 00; file nhị phân có thể chứa nhiều chuỗi chữ nhìn thấy được. Kiểm tra hợp lệ UTF-8 là kiểm tra *một encoding*, không phải parser nhận dạng mọi file.

## 8. Lỗi thường gặp và cách tránh

| Sai lầm | Hệ quả | Hướng xử lý |
| --- | --- | --- |
| Quyết định chỉ dựa vào .txt/.bin | Phân loại sai nếu đuôi bị đổi | Dùng schema, metadata và signature |
| Dùng Reader/Writer để copy ảnh | Có thể đổi byte, hỏng ảnh | Copy bằng byte stream |
| Không chỉ định charset cho text | Chữ Việt sai trên môi trường khác | Chọn charset ở cả đọc/ghi |
| Dùng String.length() làm số byte | Sai với ký tự đa byte/surrogate pair | Đếm byte sau mã hóa |
| Bỏ qua newline và BOM | Byte/checksum khác dù hiển thị giống nhau | Xác định quy ước ngay từ đầu |
| Thấy UTF-8 hợp lệ liền coi là text | Nhận diện nhầm định dạng | Dùng hợp đồng và parser |
| Đọc cả file rất lớn vào byte[] | Tốn RAM | Stream/buffer theo khối |
| Coi Base64 là encryption | Hiểu sai bảo mật | Chỉ dùng Base64 làm biểu diễn byte |

## 9. Tự kiểm tra kèm giải đáp

**Câu 1.** “Aé” có 2 code point nhưng 3 byte UTF-8: Java có làm mất dữ liệu không?  
**Đáp án:** Không. A chiếm 1 byte, é chiếm 2 byte UTF-8; nếu ghi và đọc cùng charset, chuỗi được phục hồi chính xác.

**Câu 2.** Có thể copy file ZIP bằng byte stream mà không hiểu ZIP?  
**Đáp án:** Có. Copy nguyên byte không đòi hỏi parser định dạng; chỉ khi phân tích/chỉnh sửa bên trong ZIP mới cần hiểu cấu trúc.

**Câu 3.** File không có BOM có thể là UTF-8 không?  
**Đáp án:** Có. UTF-8 BOM là tùy chọn, không bắt buộc.

**Câu 4.** Giải mã UTF-8 thành công có đủ kết luận đó là file text?  
**Đáp án:** Không. Binary cũng có thể vô tình là dãy UTF-8 hợp lệ.

**Câu 5.** Vì sao không dùng new String(imageBytes, UTF_8) để đưa ảnh vào JSON?  
**Đáp án:** Byte không hợp lệ có thể bị thay thế. Dùng Base64 để biểu diễn và phục hồi đúng mọi byte.

**Câu 6.** Files.readString(path, UTF_8) có phân tích cú pháp CSV/JSON chưa?  
**Đáp án:** Chưa. Phương thức chỉ decode byte thành String; cần parser riêng để hiểu các trường của file.

**Ghi nhớ:** Luôn xác định **định dạng và charset** trước khi diễn giải byte. Java byte stream dùng để xử lý/bảo toàn byte; Java character stream dùng để xử lý ký tự theo bảng mã được chọn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="io-overview-003">Luồng dữ liệu, tài nguyên hệ điều hành và vòng đời sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Luồng dữ liệu, tài nguyên hệ điều hành và vòng đời sử dụng trong Java I/O

## 1. Bức tranh tổng thể: đọc và ghi dữ liệu là một quá trình có tài nguyên

I/O (Input/Output) là cách chương trình trao đổi dữ liệu với bên ngoài: tệp, bàn phím, màn hình, kết nối mạng, bộ nhớ hoặc một nguồn/đích khác. **Input** là dữ liệu đi vào chương trình; **output** là dữ liệu đi ra chương trình. Một thao tác I/O có thể đọc hoặc ghi từng phần, bị chặn trong lúc chờ dữ liệu, hoặc thất bại vì nguyên nhân ngoài chương trình.

Hình dung một lần sao chép tệp:

~~~text
Tệp nguồn trên đĩa
      │   hệ điều hành cho phép mở tệp
      ▼
InputStream  ── đọc từng khối byte ──► byte[] buffer
                                            │
                                            ▼
                                     OutputStream
                                            │
                                            ▼
                                     Tệp đích trên đĩa
~~~

Trong sơ đồ có ba đối tượng cần phân biệt:

1. **Nguồn/đích dữ liệu**: tệp, socket, thiết bị, mảng byte... Đây là nơi dữ liệu đến hoặc đi.
2. **Đối tượng I/O của Java**: chẳng hạn `InputStream`, `OutputStream`, `Reader`, `Writer`; chúng cung cấp phương thức đọc/ghi và quản lý một phần trạng thái.
3. **Tài nguyên ở hệ điều hành**: chẳng hạn file descriptor hoặc handle liên quan đến tệp/socket. Không phải mọi luồng Java đều sử dụng tài nguyên hệ điều hành.

Hai từ “luồng” trong bài cần hiểu đúng ngữ cảnh: **stream I/O** là dòng dữ liệu được đọc/ghi theo thứ tự; nó **không phải thread** (luồng thực thi). Đối tượng stream là một cách truy cập dữ liệu, còn thread là đơn vị thực hiện lệnh.

**Mục tiêu thực hành:** biết ai mở tài nguyên, ai chịu trách nhiệm đóng, đọc/ghi đến đâu là hoàn tất, và điều gì xảy ra nếu thao tác lỗi giữa chừng.

## 2. Stream, Reader/Writer và lớp bao (wrapper)

### 2.1. Byte và ký tự không giống nhau

- `InputStream` và `OutputStream` xử lý **byte**. Phù hợp với dữ liệu nhị phân và sao chép nội dung nguyên trạng, ví dụ ảnh, ZIP, PDF.
- `Reader` và `Writer` xử lý **ký tự** (Java `char` / văn bản đã giải mã). Phù hợp với dữ liệu văn bản nhưng cần chọn bảng mã (charset) đúng lúc chuyển giữa byte và ký tự.
- `InputStreamReader` chuyển byte thành ký tự qua một `Charset`; `OutputStreamWriter` chuyển ký tự thành byte.
- Các API tiện ích như `Files.readString`, `Files.newBufferedReader` và `Files.writeString` cũng cần được dùng với charset rõ ràng khi tính nhất quán quan trọng; UTF-8 là lựa chọn phổ biến.

Ví dụ chuỗi `"á"` có một đơn vị UTF-16 (`String.length() == 1`) nhưng khi mã hóa UTF-8 cần hai byte. Bởi vậy **số ký tự không bằng số byte**. Sao chép tệp nhị phân qua `Reader/Writer` có thể làm thay đổi dữ liệu do quá trình giải mã và mã hóa.

### 2.2. Lớp nền và lớp bao có trách nhiệm khác nhau

~~~text
Ứng dụng
   │
BufferedInputStream      ← gom nhiều lần đọc nhỏ vào bộ đệm
   │
FileInputStream          ← đọc byte từ tệp, thường giữ handle/descriptor
   │
Hệ điều hành / hệ thống tệp
~~~

`BufferedInputStream` chủ yếu bổ sung bộ đệm; `FileInputStream` thực hiện đọc từ tệp. Hai đối tượng có thể dùng chung **một** tài nguyên tệp bên dưới, vì vậy “có hai object” không có nghĩa “đã mở hai tệp”. Khi đóng lớp bao ngoài cùng do mình sở hữu, thao tác đóng thông thường được truyền xuống luồng bên dưới.

Các stream trong bộ nhớ như `ByteArrayInputStream` và `ByteArrayOutputStream` không giữ file descriptor; đóng `ByteArrayOutputStream` không làm nó mất dữ liệu đã tích lũy. Ngược lại, luồng từ `Files.newInputStream(path)` thường liên quan đến tài nguyên tệp cần giải phóng. Không đánh đồng mọi `close()` với một lần gọi hệ điều hành giống nhau.

### 2.3. Bộ đệm và từng phần dữ liệu

`read()` một byte có thể gây nhiều lần gọi I/O hơn đọc một mảng byte; trong thực tế thường dùng bộ đệm, ví dụ `byte[8192]`. Đọc không bắt buộc lấp đầy cả mảng: một lần `read(buffer)` có thể trả về số byte nhỏ hơn `buffer.length`. Khi đọc đến hết luồng, kết quả là `-1`; không được lấy `-1` làm số byte hợp lệ để ghi.

Với độ dài mảng lớn hơn 0, dạng vòng lặp thường dùng là:

~~~java
byte[] buffer = new byte[8192];
int count;
while ((count = in.read(buffer)) != -1) {
    out.write(buffer, 0, count);
}
~~~

Đây là **đoạn mã minh họa**, trong đó `in` và `out` đã được tạo và sẽ được đóng bởi phần bao ngoài. Chỉ ghi đúng `count` byte nhận được. Viết `out.write(buffer)` sẽ có nguy cơ ghi thêm các byte cũ ở phần cuối mảng khi lần đọc cuối trả về ít dữ liệu.

## 3. Hệ điều hành tham gia I/O như thế nào?

Khi một chương trình mở tệp hoặc socket thông qua Java, JVM và thư viện nền tảng thường phối hợp với hệ điều hành để tạo một định danh cho tài nguyên đang mở: **file descriptor** ở các hệ Unix hoặc **handle** ở Windows. Chi tiết ánh xạ phụ thuộc hệ điều hành và loại API; không có quy tắc “một Java object luôn bằng một file descriptor”.

Trong lúc tài nguyên còn mở, hệ điều hành có thể duy trì vị trí đọc/ghi, quyền truy cập, bộ đệm, trạng thái kết nối hoặc khóa liên quan. Đóng tài nguyên làm kết thúc quyền sử dụng của đối tượng đó và yêu cầu giải phóng tài nguyên bên dưới theo hợp đồng API. Việc dữ liệu đã được ghi đến đâu trên thiết bị lưu trữ vật lý là câu chuyện khác (xem mục 8).

Ví dụ hậu quả khi quên đóng liên tục:

- Tiến trình giữ quá nhiều descriptor/handle dẫn đến lỗi kiểu “too many open files” hoặc không mở thêm tệp/socket được.
- Tệp có thể chưa được giải phóng để đổi tên/xóa trên một số hệ điều hành hoặc khi còn khóa, ánh xạ bộ nhớ.
- Dữ liệu vẫn nằm trong bộ đệm chưa được đẩy xuống luồng bên dưới.
- Kết nối mạng tồn tại lâu hơn dự kiến, giữ bộ nhớ và tài nguyên của cả hai đầu.

**Garbage Collector (GC) quản lý bộ nhớ đối tượng Java, không phải cơ chế bảo đảm đóng tài nguyên I/O đúng thời điểm.** Một object không còn được tham chiếu có thể được thu gom sau đó, hoặc rất lâu sau đó. Dù một thư viện có biện pháp dọn dẹp dự phòng, không được dựa vào GC để thay `close()`.

Cần nhớ ba lớp khác nhau:

| Lớp | Ví dụ | Điều phải kiểm soát |
| --- | --- | --- |
| Dữ liệu | byte, ký tự, bản ghi, thông điệp | Đọc/ghi đúng số lượng, đúng charset và thứ tự |
| Đối tượng Java | `InputStream`, `BufferedReader`, `FileChannel` | Trạng thái đang mở, bộ đệm, lỗi, `close()` |
| Tài nguyên bên ngoài | descriptor/handle tệp, socket, khóa | Mở có giới hạn, giải phóng kịp thời, hạn chế tranh chấp |

## 4. Vòng đời tài nguyên: mở → dùng → kết thúc

Một đường đi điển hình:

~~~text
Chưa mở
   │ open/newInputStream/newOutputStream
   ▼
Đang mở
   ├── read/write/flush thành công ──► tiếp tục sử dụng
   ├── hết dữ liệu đầu vào (EOF) ────► vẫn có thể cần close
   └── IOException ─────────────────► vẫn phải close
   │
   ▼
close() / try-with-resources
   │
   ▼
Đã đóng: không tiếp tục I/O trên đối tượng có tài nguyên
~~~

**EOF không đồng nghĩa với đóng tệp.** `read() == -1` chỉ cho biết đã hết dữ liệu của đầu vào theo giao ước đọc; descriptor/handle vẫn cần giải phóng. Tương tự, ghi đủ các byte dự kiến không tự bảo đảm `close()` đã chạy.

Một số điểm dễ bỏ sót:

- **Mở cũng có thể thất bại**: tệp không tồn tại, thiếu quyền, đường dẫn sai, quá nhiều tệp đang mở.
- **Đọc hoặc ghi có thể thất bại giữa chừng**: thiết bị đầy, kết nối bị cắt, quyền truy cập thay đổi. Đích có thể đã nhận một phần dữ liệu.
- **`flush()` và `close()` có thể ném `IOException`**. Đường xử lý lỗi phải tính đến cả lỗi dọn dẹp.
- **Không phải một lần `write` tương đương một giao dịch**. Nếu quá trình sao chép thất bại, tệp đích có thể chứa dữ liệu chưa hoàn chỉnh; nếu cần thay thế tệp an toàn phải thiết kế quy trình ghi tạm và đổi tên phù hợp hệ thống tệp.
- **Đóng nhiều lần**: một số lớp có hành vi dung thứ, nhưng không được giả định mọi triển khai `AutoCloseable` đều bảo đảm `close()` idempotent. Hãy thiết kế mỗi tài nguyên có một chủ sở hữu chịu trách nhiệm đóng.

Trong mô hình luồng, thao tác đọc/ghi còn có thể **chặn** để chờ dữ liệu hoặc thiết bị. Chẳng hạn socket đang mở nhưng phía kia chưa gửi thêm byte; việc chờ không nói lên rằng dữ liệu đã hết. Đặc biệt với mạng, cần xem xét timeout, hủy thao tác và quy tắc đóng riêng của API đó.

## 5. Đóng đúng bằng try-with-resources

`try-with-resources` (có từ Java 7) là cách chuẩn để quản lý đối tượng triển khai `AutoCloseable`, bao gồm nhiều loại trong `java.io`, `java.nio.channels` và các stream I/O khác.

~~~java
try (InputStream in = Files.newInputStream(source);
     OutputStream out = Files.newOutputStream(target)) {
    // Đọc và ghi.
} // out.close() chạy trước, rồi in.close(), kể cả khi thân try phát sinh lỗi.
~~~

Đây là đoạn minh họa; các biến `source` và `target` phải là `Path` hợp lệ, và lớp chứa đoạn mã phải xử lý/khai báo `IOException`. Tài nguyên được đóng **theo thứ tự ngược với thứ tự khai báo**. Nếu một bước đóng xảy ra lỗi, Java vẫn cố gắng đóng các tài nguyên còn lại.

Lợi ích so với tự viết `try/finally` là quản lý chính xác ngoại lệ. Nếu thân `try` ném một ngoại lệ và `close()` cũng ném ngoại lệ, ngoại lệ từ thân `try` là ngoại lệ chính; lỗi khi đóng được gắn vào đó dưới dạng **suppressed exception** (truy xuất qua `Throwable.getSuppressed()`). Đừng thay thế nó bằng ngoại lệ dọn dẹp rồi mất nguyên nhân ban đầu.

Ví dụ độc lập minh họa thứ tự đóng:

~~~java
public class CloseOrderDemo {
    static final class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        try (Resource first = new Resource("first");
             Resource second = new Resource("second")) {
            System.out.println("work");
        }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 CloseOrderDemo.java
java CloseOrderDemo
~~~

Kết quả chính xác:

~~~text
work
close second
close first
~~~

`Closeable` mở rộng `AutoCloseable` và quy định `close()` ném tối đa `IOException`; `AutoCloseable.close()` có thể khai báo `Exception`. Hợp đồng chính xác của mỗi lớp vẫn cần được tôn trọng. Không viết `catch (Exception ignored)` chỉ để giấu lỗi đóng, vì có thể mất tín hiệu rằng dữ liệu chưa được đẩy ra ngoài thành công.

## 6. Ví dụ đầy đủ: sao chép tệp và giải phóng tài nguyên

Ví dụ Java 17+ sau tự tạo tệp đầu vào tạm, đọc từng khối byte, ghi sang tệp đích, tự đóng hai luồng và xóa tệp tạm. Dữ liệu thử là ASCII để số byte dễ kiểm tra. Tên tệp Java phải là **IoLifecycleDemo.java**.

~~~java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class IoLifecycleDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("java-io-");
        Path source = directory.resolve("source.txt");
        Path target = directory.resolve("target.txt");

        try {
            Files.writeString(source, "Java I/O\n", StandardCharsets.UTF_8);
            long total = 0;

            try (InputStream in = new BufferedInputStream(
                         Files.newInputStream(source));
                 OutputStream out = new BufferedOutputStream(
                         Files.newOutputStream(target))) {

                byte[] buffer = new byte[8192];
                int count;

                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    total += count;
                }
            }

            System.out.println("Bytes copied: " + total);
            System.out.println("Content: " +
                    Files.readString(target, StandardCharsets.UTF_8).strip());
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Biên dịch và chạy ở thư mục chứa tệp nguồn:

~~~bash
javac -encoding UTF-8 IoLifecycleDemo.java
java IoLifecycleDemo
~~~

Kết quả:

~~~text
Bytes copied: 9
Content: Java I/O
~~~

Giải thích từng quyết định:

1. `Files.createTempDirectory` tạo thư mục thử nghiệm riêng; `source` và `target` là đường dẫn tệp chứ chưa phải stream đang mở.
2. `Files.writeString` thực hiện trọn một thao tác ghi tiện ích và tự quản lý tài nguyên nội bộ. Chuỗi `"Java I/O\n"` gồm 9 byte UTF-8.
3. `Files.newInputStream` và `Files.newOutputStream` mở các tài nguyên cần được đóng. `BufferedInputStream/BufferedOutputStream` bọc chúng để hạn chế các thao tác I/O nhỏ.
4. Thân `try-with-resources` sao chép đúng số byte mỗi lượt. `total` chỉ tăng sau khi `write` trả về bình thường.
5. Rời khối `try` đóng `out` rồi `in`, kể cả khi `read` hoặc `write` có lỗi. `BufferedOutputStream.close()` đẩy phần dữ liệu đang đệm xuống luồng bên dưới trước khi đóng.
6. Chỉ sau khi khối đóng thành công mới đọc lại tệp đích. Khối `finally` cố gắng xóa các tệp và thư mục thử nghiệm.

Ví dụ ưu tiên sự rõ ràng. Trong phần mềm thực tế, nếu thao tác chính lỗi và việc xóa tệp tạm cũng lỗi, ngoại lệ từ `finally` có thể che lỗi gốc; khi cần giữ cả hai nguyên nhân, hãy thiết kế riêng bước dọn tệp tạm và gắn ngoại lệ dọn dẹp dưới dạng suppressed exception. Đồng thời phải quyết định sẽ giữ lại tệp đích chưa hoàn chỉnh để điều tra hay xóa đi.

Nếu chỉ muốn sao chép tệp thông thường, Java có API tiện ích `Files.copy(source, target, ...)`; ví dụ thủ công ở đây để nhìn thấy vòng đời của **luồng dữ liệu** và **tài nguyên**.

## 7. Văn bản: đưa charset vào đúng ranh giới byte/ký tự

Tệp và socket truyền **byte**; người dùng đọc **chữ**. Bộ giải mã chuyển byte thành ký tự theo charset. Khi charset không khớp, chữ có dấu có thể thành ký tự lỗi; nếu dùng charset mặc định tùy môi trường, kết quả giữa các máy có thể khác nhau.

Đọc văn bản có thể dùng:

~~~java
try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý một dòng; readLine() đã bỏ dấu kết thúc dòng.
    }
}
~~~

Đây là đoạn minh họa trong ngữ cảnh có `Path path` và xử lý `IOException`. `readLine()` trả về `null` tại EOF; khi gặp EOF vẫn cần đóng `reader`. Nếu phải bảo toàn byte gốc, kiểu xuống dòng hoặc mã hóa đầu vào, đừng dùng cách đọc dòng rồi ghi lại theo charset như một phương pháp “sao chép nguyên vẹn”.

`Files.readString(path, UTF_8)` tiện cho tệp văn bản nhỏ, nhưng tải toàn bộ nội dung vào bộ nhớ; với tệp lớn, hãy đọc tuần tự hoặc xử lý theo khối/dòng. Với đầu ra, `Files.newBufferedWriter(path, UTF_8)` cho phép ghi văn bản theo kiểu tuần tự.

Ngoài `Reader/Writer`, các nguồn lười như `Files.lines(path, UTF_8)` và `Files.walk(path)` trả về `Stream` có tài nguyên I/O cần đóng; đặt chúng trong `try-with-resources`. Không suy diễn rằng mọi `java.util.stream.Stream` đều giữ tài nguyên ngoài: stream từ một danh sách trong bộ nhớ thường không cần đóng.

## 8. Flush, close và độ bền dữ liệu là ba việc khác nhau

**`flush()`** yêu cầu bộ đệm của đối tượng ghi đẩy những gì đang giữ xuống đích kế tiếp theo hợp đồng API. Chẳng hạn `BufferedOutputStream.flush()` chuyển dữ liệu còn đệm xuống `OutputStream` bên dưới. Nó không tự cam kết dữ liệu đã nằm bền vững trên ổ đĩa vật lý.

**`close()`** kết thúc việc sử dụng tài nguyên; nhiều output stream sẽ flush trong quá trình đóng rồi giải phóng luồng bên dưới. Sau khi đóng, không tiếp tục ghi lên stream đó. Close thành công vẫn không phải cam kết tuyệt đối rằng dữ liệu đã sống sót qua sự cố mất điện.

**Đồng bộ dữ liệu xuống thiết bị** là yêu cầu mạnh hơn. Với tệp, `FileChannel.force(boolean metaData)` yêu cầu đẩy các cập nhật liên quan đến channel xuống thiết bị lưu trữ theo hợp đồng của API, nhưng bảo đảm thực tế còn phụ thuộc loại thiết bị, hệ điều hành, hệ thống tệp và việc tệp được mở từ đâu. Đây không phải thao tác thay thế cho `close()`, và cũng không làm toàn bộ quá trình thay tệp trở thành giao dịch nguyên tử.

| Thao tác | Mục đích chính | Không nên suy ra |
| --- | --- | --- |
| `write(...)` | Đưa dữ liệu vào đối tượng/đích ghi | Mọi byte đã ghi bền vững trên đĩa |
| `flush()` | Đẩy dữ liệu ở bộ đệm của lớp hiện tại xuống tầng kế | Tệp đã được đóng hoặc đã được fsync |
| `close()` | Kết thúc vòng đời, giải phóng tài nguyên | GC đã chạy hoặc thiết bị không thể mất dữ liệu |
| `FileChannel.force(...)` | Yêu cầu đồng bộ cập nhật tệp liên quan đến channel | Thay thế việc đóng, hoặc đảm bảo một giao dịch đa tệp |

Ví dụ dịch vụ ghi cấu hình quan trọng: có thể cần ghi tệp tạm, flush/đóng đúng cách, yêu cầu đồng bộ thích hợp rồi thay thế tệp đích bằng thao tác di chuyển hỗ trợ tính nguyên tử khi có thể. Chính sách này phụ thuộc yêu cầu độ bền và hệ thống tệp; không áp dụng máy móc cho mọi bản ghi nhỏ.

## 9. Ai sở hữu stream thì người đó quản lý vòng đời

Quy tắc thực hành: **phần nào tạo/mở tài nguyên thường chịu trách nhiệm đóng**, trừ khi hợp đồng API chỉ rõ quyền sở hữu được chuyển cho bên nhận.

- Một hàm tự gọi `Files.newInputStream(path)` thì nên đóng stream trong hàm, thường bằng `try-with-resources`.
- Một hàm nhận `InputStream` từ người gọi thường chỉ sử dụng stream; nó **không tự đóng** nếu hợp đồng không chuyển quyền sở hữu, vì người gọi có thể còn cần dùng.
- Một hàm tạo `BufferedReader` bọc stream do người gọi sở hữu phải cân nhắc: đóng wrapper thường sẽ đóng luôn stream nền. Hãy công bố rõ ai chịu trách nhiệm đóng chuỗi wrapper.
- `System.in`, `System.out` và `System.err` là luồng chuẩn chia sẻ của tiến trình. Đóng chúng trong mã thư viện hoặc bài thực hành có thể làm các phần còn lại không đọc/ghi tiếp được; thông thường không nhận quyền đóng chúng.

Đối với chương trình lớn, hãy biểu diễn rõ trách nhiệm trong tên hàm, tài liệu API và cấu trúc `try-with-resources`. Tránh tình huống hai nơi đều cho rằng bên kia sẽ đóng, hoặc cả hai đều tự đóng tài nguyên chung.

Một số API cấp cao như `Files.readAllBytes` hoặc `Files.readString` tự mở và đóng tài nguyên cần thiết trong lời gọi; người sử dụng chỉ nhận kết quả dữ liệu. API kiểu `Files.newInputStream` hoặc `Files.lines` trao lại một đối tượng sống cần đóng. Sự khác biệt nằm ở **hợp đồng của phương thức**, không nằm ở việc tên có chứa “Files”.

## 10. Lỗi thường gặp và cách phát hiện

| Dấu hiệu / cách viết | Vấn đề | Cách xử lý |
| --- | --- | --- |
| Mở tệp trong vòng lặp, không đóng | Rò rỉ handle/descriptor sau nhiều lần | Dùng `try-with-resources` trong mỗi lần mở |
| Chỉ gọi `close()` trên đường thành công | Ngoại lệ bỏ qua bước giải phóng | Đưa tài nguyên vào `try-with-resources` ngay khi mở |
| Dùng `available() == 0` để suy ra EOF | `available()` chỉ ước lượng số byte có thể đọc không chặn, không xác định hết luồng | Dựa vào giá trị trả về của `read()` |
| Giả định `read(buffer)` điền đầy mảng | Ghi thêm dữ liệu cũ hoặc mất dữ liệu | Dùng đúng `count` do `read` trả về |
| Ghi tệp nhị phân qua `Reader/Writer` | Giải mã/mã hóa làm đổi byte | Dùng byte stream hoặc `Files.copy` |
| Đóng wrapper không thuộc sở hữu | Đóng luôn stream gốc của bên gọi | Quy định rõ quyền sở hữu trong hợp đồng |
| Gọi `flush()` rồi coi như an toàn khi mất điện | Nhầm bộ đệm Java với độ bền vật lý | Chọn chiến lược đồng bộ và ghi tệp an toàn khi cần |
| Nuốt mọi `IOException` ở `close()` | Mất tín hiệu hỏng đường ghi hoặc dọn dẹp | Ghi nhận và xử lý lỗi, bảo toàn ngoại lệ chính |
| Mở `Files.lines`/`Files.walk` nhưng không đóng | Có thể giữ tài nguyên đọc tệp/thư mục | Bọc kết quả trong `try-with-resources` |

Để tìm lỗi tài nguyên trong chương trình thực, hãy theo dõi số descriptor/handle đang mở của tiến trình, thử tải lặp nhiều lần, xem stack trace `IOException`, và kiểm tra mọi nhánh kết thúc sớm, lỗi và hủy. Số lượng handle tăng ổn định theo tải mà không giảm sau khi hoàn thành công việc là một dấu hiệu cần điều tra, chưa đủ để kết luận chính xác chỗ rò rỉ.

## 11. Tự kiểm tra hiểu biết

**Tình huống A.** Một hàm gọi `read(buffer)`, nhận được `3` nhưng lại ghi `buffer.length` byte. Hỏi lỗi là gì? **Giải thích:** chỉ ba phần tử đầu mảng chứa dữ liệu mới của lượt đọc; phần còn lại có thể chứa byte từ lượt trước. Cần `write(buffer, 0, 3)`.

**Tình huống B.** Chương trình đã đọc đến `-1` và thoát vòng lặp nhưng không gọi `close()`. Hỏi tài nguyên đã được giải phóng chưa? **Giải thích:** EOF không kết thúc vòng đời tài nguyên. Dùng `try-with-resources`.

**Tình huống C.** Một thư viện nhận `InputStream` do ứng dụng cung cấp và tự đóng stream ở cuối hàm. Có luôn đúng không? **Giải thích:** không; nếu không có thỏa thuận chuyển quyền sở hữu, thư viện có thể làm hỏng các lần đọc tiếp theo của ứng dụng.

**Tình huống D.** `BufferedOutputStream.flush()` trả về bình thường. Có thể khẳng định dữ liệu sẽ sống sót sau mất điện không? **Giải thích:** không; flush ở tầng Java không tương đương đồng bộ bền vững trên thiết bị.

**Bài tập thực hành:** thử sửa ví dụ sao chép để (1) sao chép chuỗi UTF-8 có dấu và kiểm tra kích thước byte; (2) đặt kiểm tra lỗi giữa chừng và quan sát tệp đích có thể dang dở; (3) thay vòng lặp tự viết bằng `Files.copy` rồi so sánh trách nhiệm đóng tài nguyên của hai cách.

## Ghi nhớ

1. **Stream mô tả dòng byte/ký tự; tài nguyên hệ điều hành là thứ cần được quản lý bên dưới**, và không phải stream nào cũng có descriptor riêng.
2. **Mở, sử dụng, đọc tới EOF và đóng là các mốc khác nhau**. Không để GC quyết định thời điểm giải phóng I/O.
3. **Dùng try-with-resources và xác định chủ sở hữu tài nguyên**. Wrapper có thể đóng luôn luồng nền.
4. **Xử lý đọc/ghi theo từng phần và giữ thông tin lỗi**. Không nhầm số byte đã đọc, bộ đệm đã flush, tài nguyên đã đóng và dữ liệu đã bền vững.
5. **Chọn API theo dữ liệu**: byte stream cho nhị phân, Reader/Writer với charset rõ ràng cho văn bản, các tiện ích `Files` khi không cần tự quản lý vòng lặp.

### Tài liệu API tham khảo

- [InputStream (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html)
- [OutputStream (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html)
- [AutoCloseable (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html)
- [Files (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [FileChannel (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="io-overview-004">Phân biệt stream I/O với java.util.stream.Stream</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt stream I/O với `java.util.stream.Stream` trong Java

Trong Java, từ **stream** xuất hiện ở hai nhóm API khác nhau. **I/O stream** là luồng đọc/ghi dữ liệu qua thiết bị, file, socket hoặc bộ nhớ; **`java.util.stream.Stream<T>`** là pipeline xử lý các phần tử theo phong cách khai báo. Chúng có thể kết hợp, nhưng không thay thế trực tiếp cho nhau. Bài này đặt trong phần **Tổng quan I/O** và tập trung vào cách chọn, dùng và quản lý hai loại stream.

## 1. Hai ý nghĩa của “stream”

**I/O stream — dữ liệu đi qua một kênh đọc/ghi**

- `java.io.InputStream` đọc **byte**; `java.io.OutputStream` ghi **byte**.
- `java.io.Reader` đọc **ký tự**; `java.io.Writer` ghi **ký tự**. Khi đổi giữa byte và ký tự, cần xác định mã hóa như UTF-8.
- Nguồn/đích có thể là file (`FileInputStream`), bộ nhớ (`ByteArrayInputStream`), kết nối mạng hoặc một luồng I/O khác được bọc thêm (`BufferedInputStream`, `InputStreamReader`).
- Các lệnh như `read()`, `write()`, `flush()` trực tiếp thực hiện hoặc yêu cầu hoạt động I/O. Đọc tiến dần theo vị trí hiện tại; kết thúc luồng đọc thường biểu thị bằng `-1`. Nhiều thao tác có thể chặn luồng thực thi và ném `IOException`.
- Phần lớn tài nguyên I/O cần `close()` đúng lúc. `try-with-resources` giúp đóng cả khi có ngoại lệ.

**Stream API — tập hợp các phép biến đổi/tiêu thụ phần tử**

- `java.util.stream.Stream<T>` biểu diễn pipeline trên các **đối tượng kiểu `T`**, ví dụ `Stream<String>`; `IntStream`, `LongStream`, `DoubleStream` chuyên cho kiểu nguyên thủy.
- Nguồn có thể là `Collection.stream()`, `Arrays.stream()`, `Stream.of()`, bộ sinh phần tử, hoặc API đọc dữ liệu như `Files.lines()`.
- Các thao tác trung gian như `filter`, `map`, `distinct` tạo pipeline; thao tác kết thúc như `toList`, `count`, `reduce`, `forEach` kích hoạt tiêu thụ dữ liệu.
- Pipeline thông thường **lazy**: định nghĩa thao tác trung gian chưa xử lý ngay phần tử. Stream là luồng sử dụng một lần; không chạy lại cùng pipeline sau terminal operation.
- Stream API có thể xử lý tuần tự hay song song, nhưng song song **không** tự động làm I/O nhanh hơn.

> Phân biệt tên đầy đủ khi giải thích hoặc import: `java.io.InputStream` / `java.io.OutputStream` thuộc I/O; `java.util.stream.Stream` thuộc Stream API. `Reader`/`Writer` cũng là I/O dù tên không chứa “Stream”.

## 2. Bảng so sánh trực tiếp

| Tiêu chí | I/O stream (`InputStream`, `OutputStream`, `Reader`, `Writer`) | `java.util.stream.Stream<T>` |
|---|---|---|
| Câu hỏi nó giải quyết | Dữ liệu được **đọc từ đâu, ghi đi đâu, bằng cách nào**? | Phần tử được **lọc, chuyển đổi, tổng hợp thế nào**? |
| Đơn vị chính | Byte hoặc ký tự | Phần tử đối tượng (`T`) hoặc primitive stream |
| Hướng xử lý | Đọc/ghi qua nguồn hoặc đích cụ thể | Luồng phần tử chạy từ nguồn qua pipeline đến kết quả |
| API đặc trưng | `read`, `write`, `flush`, `close` | `filter`, `map`, `sorted`, `collect`, `toList`, `count` |
| Thời điểm làm việc | Thao tác đọc/ghi diễn ra khi gọi phương thức; có thể block | Intermediate operations thường lazy, terminal operation mới kích hoạt |
| Ngoại lệ | Thường dùng `IOException` (checked) | Các thao tác thông thường không khai báo `IOException`; nguồn dựa trên I/O có thể phát sinh `UncheckedIOException` khi duyệt |
| Tài nguyên | File descriptor, socket, buffer… có thể cần đóng | Stream từ collection thường không giữ tài nguyên ngoài; Stream dựa trên file có thể cần đóng |
| Dùng lại | Đọc làm tiến vị trí; có loại hỗ trợ `mark/reset` hoặc mở lại nguồn | Không tái sử dụng sau terminal; cần tạo Stream mới |
| Parallel | Tự bản thân I/O stream không cung cấp pipeline `parallel()` | Có `parallel()` nhưng phải xét tính đúng, thứ tự, chi phí và nguồn dữ liệu |
| Kết quả | Byte/ký tự đã đọc, hoặc hiệu ứng ghi ra đích | Giá trị tổng hợp, collection, phần tử tùy terminal operation |

Cả hai đều có tính **đọc dần** trong nhiều trường hợp, nhưng “lazy” ở Stream API nói về **lúc chạy các phép biến đổi**, không có nghĩa chỉ Stream API mới tiết kiệm bộ nhớ hay mới hỗ trợ đọc theo từng phần.

## 3. Ví dụ 1 — cùng chữ “stream”, hai công việc khác nhau

Tệp `StreamKindsDemo.java` (Java 17+):

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class StreamKindsDemo {
    public static void main(String[] args) throws IOException {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);

        // I/O: đọc byte từ một nguồn.
        try (InputStream input = new ByteArrayInputStream(data)) {
            System.out.println((char) input.read());
            System.out.println(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }

        // Stream API: xử lý các phần tử của một List.
        List<String> chosen = List.of("An", "Binh", "Chi").stream()
                .filter(name -> name.length() >= 3)
                .map(String::toUpperCase)
                .toList();
        System.out.println(chosen);
    }
}
```

Chạy:

```bash
javac --release 17 StreamKindsDemo.java
java StreamKindsDemo
```

Kết quả:

```text
a
bc
[BINH, CHI]
```

Ở phần đầu, `read()` lấy byte đầu tiên (`'a'`); `readAllBytes()` lấy **phần còn lại** (`"bc"`), không tự quay về đầu. `ByteArrayInputStream` là I/O stream nhưng đọc trong bộ nhớ, vì thế “I/O” không đồng nghĩa mọi lần đọc đều truy cập đĩa hoặc mạng.

Ở phần sau, `List.stream()` tạo `Stream<String>`, `filter` giữ tên dài ít nhất ba ký tự, `map` viết hoa và `toList()` tạo kết quả. Pipeline không đọc byte từ thiết bị; nó xử lý các đối tượng đã có trong `List`. Danh sách trả về bởi `Stream.toList()` là **không thể chỉnh sửa cấu trúc** (unmodifiable), từ Java 16.

## 4. Lazy pipeline và cơ chế tiêu thụ một lần

Ví dụ `LazyPipelineDemo.java` (Java 17+):

```java
import java.util.stream.Stream;

public class LazyPipelineDemo {
    public static void main(String[] args) {
        Stream<String> pipeline = Stream.of("a", "bb", "ccc")
                .map(value -> {
                    System.out.println("map:" + value);
                    return value.toUpperCase();
                })
                .filter(value -> value.length() >= 2);

        System.out.println("Built pipeline");
        System.out.println(pipeline.toList());
    }
}
```

```bash
javac --release 17 LazyPipelineDemo.java
java LazyPipelineDemo
```

```text
Built pipeline
map:a
map:bb
map:ccc
[BB, CCC]
```

`map` và `filter` chỉ dựng pipeline; gọi `toList()` mới khiến các phần tử đi qua pipeline. Thứ tự tác vụ có thể khác nếu đổi cách xây dựng pipeline, dùng phép tối ưu hoặc chạy song song. Lệnh in trong `map` ở đây chỉ để minh họa thời điểm thực thi; trong chương trình thực tế, hạn chế dựa vào side effect của intermediate operation.

**Điểm dễ nhầm:** `Stream<T>` không phải một “túi chứa” có thể duyệt đi duyệt lại:

```java
Stream<String> oneShot = Stream.of("A", "B");
long count = oneShot.count();       // Hợp lệ
// oneShot.toList();                // IllegalStateException: stream đã được dùng
```

Đoạn trên là **minh họa**, không phải chương trình độc lập. Sau `count()`, nếu cần tính tiếp, hãy tạo stream mới từ nguồn. Tương tự, `parallel()` chỉ đổi cách thực thi pipeline; nó không biến I/O stream thành Stream API và không cho phép dùng lại stream đã tiêu thụ.

## 5. Khi hai API kết hợp: đọc file và lọc các dòng

Một file thường lưu **byte**. `Files.lines(path, charset)` tạo ra `Stream<String>`: phần I/O đọc và giải mã dữ liệu, còn Stream API cung cấp `filter`/`count`. Đây là **cầu nối** giữa hai khái niệm.

Tệp `FileLinesDemo.java` (Java 17+; tự tạo và dọn file tạm):

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FileLinesDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("io-stream-demo-", ".txt");
        try {
            Files.writeString(file, "Java\nI/O\nStream\n",
                    StandardCharsets.UTF_8);

            // Stream API đọc các dòng từ nguồn I/O: phải đóng Stream.
            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                long count = lines.filter(line -> line.length() >= 4).count();
                System.out.println("Long lines: " + count);
            }

            // I/O Reader: tự đọc tuần tự từng dòng.
            try (BufferedReader reader =
                    Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                System.out.println("First: " + reader.readLine());
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac --release 17 FileLinesDemo.java
java FileLinesDemo
```

```text
Long lines: 2
First: Java
```

Giải thích:

1. `Files.writeString` ghi văn bản UTF-8; ký hiệu `\n` trong chuỗi Java tạo xuống dòng.
2. `Files.lines` tạo một `Stream<String>` đọc dòng theo nhu cầu khi terminal operation chạy. `filter` giữ `"Java"` và `"Stream"`; `count` in `2`.
3. `try-with-resources` đóng stream từ `Files.lines`, nhờ đó tài nguyên file liên quan được giải phóng kể cả khi pipeline lỗi. Terminal operation như `count()` **không có nghĩa** tự đóng stream.
4. `Files.newBufferedReader` trả về `BufferedReader`; `readLine()` tự đọc dòng đầu. Reader cũng được đóng bằng `try-with-resources`.
5. Khối `finally` xóa file tạm sau khi cả stream và reader đã đóng. Nếu thao tác dọn file lỗi, `deleteIfExists` có thể ném `IOException`.

Cú pháp `BufferedReader.lines()` cũng trả ra `Stream<String>`, nhưng bạn vẫn phải quản lý **`BufferedReader` đã tạo ra stream đó**. Đừng mặc định chỉ đóng Stream trả về bởi `reader.lines()` là đã đóng reader. Ví dụ khi reader do bạn mở:

```java
try (BufferedReader reader =
        Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    long total = reader.lines().filter(s -> !s.isBlank()).count();
    System.out.println(total);
}
```

Đoạn này là **mẫu minh họa**: `path` phải là một `Path` hợp lệ, và nơi gọi phải xử lý/khai báo `IOException`. Reader còn mở trong lúc terminal operation chạy. `Files.lines` và `BufferedReader.lines` không đảm bảo tải toàn bộ file vào bộ nhớ; dù vậy, các phép biến đổi có trạng thái như `sorted` hoặc terminal operation như `toList` vẫn có thể gom nhiều phần tử vào bộ nhớ.

## 6. Chọn API theo yêu cầu thực tế

**Cần sao chép, đọc/ghi dữ liệu thô, truyền qua socket, hoặc xử lý định dạng nhị phân:** dùng `InputStream`/`OutputStream`, hoặc `Files.copy` khi phù hợp. Làm việc với byte thì không áp dụng `filter` hay `map` trực tiếp lên `InputStream`; nếu cần pipeline, phải chuyển thành nguồn phần tử có ý nghĩa và cân nhắc chi phí.

**Cần đọc/ghi văn bản:** dùng `Reader`/`Writer` và đặt `Charset` rõ ràng khi chuyển đổi byte ↔ ký tự. Ví dụ `Files.newBufferedReader(path, UTF_8)` để đọc từng dòng, `Files.newBufferedWriter` để ghi chuỗi. Đừng biến một byte bất kỳ thành `char` rồi kỳ vọng đúng Unicode; ví dụ 1 chỉ làm vậy vì byte `'a'` thuộc ASCII.

**Cần lọc/biến đổi/tổng hợp dữ liệu đã có:** chọn `Stream<T>` từ `Collection`/mảng/nguồn phát sinh phần tử. Với thao tác đơn giản, vòng lặp truyền thống có thể dễ hiểu và tiết kiệm overhead hơn.

**Cần đọc file văn bản và xử lý từng dòng:** `Files.lines(path, UTF_8)` kết hợp I/O và Stream API; đóng stream trong `try-with-resources`. Nếu cần tự kiểm soát cách đọc, xử lý luồng ký tự, hoặc đọc luân phiên từng dòng theo quy trình trạng thái, `BufferedReader.readLine()` là lựa chọn trực tiếp.

**File nhỏ hay lớn?** `Files.readAllLines` đọc toàn bộ các dòng thành `List<String>`; thuận tiện khi dữ liệu vừa bộ nhớ. `Files.lines` hỗ trợ xử lý dần theo pipeline, thường giảm bộ nhớ với các phép lọc/tổng hợp streaming, nhưng không tự giải quyết mọi bài toán bộ nhớ (ví dụ `sorted().toList()` phải giữ dữ liệu đáng kể).

## 7. Những sai lầm thường gặp

1. **Gọi nhầm phương thức:** `InputStream` không có `map`/`filter`; `Stream<String>` không có `read()`/`write()` như I/O stream. Hai API khác mục đích và kiểu dữ liệu.
2. **Bỏ qua mã hóa:** đọc byte rồi chuyển thẳng sang chữ bằng charset mặc định có thể hỏng tiếng Việt. Với dữ liệu văn bản, nêu rõ `StandardCharsets.UTF_8` ở hai phía khi cần.
3. **Quên đóng nguồn I/O:** dùng `Files.lines` nhưng chỉ gọi `count()`/`toList()`, rồi quên `close()`. Giữ `try-with-resources` quanh stream gắn với file. Với reader do mình mở, đóng reader.
4. **Cho rằng mọi Stream phải đóng:** `Stream.of(...)` và `list.stream()` thông thường không giữ tài nguyên hệ điều hành. Mặc dù `Stream` kế thừa `AutoCloseable`, nhu cầu đóng phụ thuộc **nguồn**; `try-with-resources` vẫn hợp lệ nhưng không thường cần cho stream trong bộ nhớ.
5. **Giả định `readAllBytes()` luôn tốt:** nó lưu toàn bộ byte còn lại trong một mảng; dữ liệu lớn nên cân nhắc xử lý theo khối, `transferTo`, hay API xử lý trực tiếp.
6. **Dùng lại Stream sau terminal:** muốn chạy `count` và `toList` độc lập thì tạo hai stream mới, hoặc thu dữ liệu trước nếu giới hạn bộ nhớ cho phép.
7. **Coi Stream API luôn nhanh hơn:** xây pipeline có overhead; `parallel()` có thể làm giảm hiệu năng hoặc gây tranh chấp ở nguồn I/O. Đo hiệu năng theo dữ liệu thực tế trước khi tối ưu.
8. **Không hiểu ranh giới ngoại lệ:** các phương thức I/O như `Files.lines`/`readLine` thường khai báo `IOException`; lỗi đọc khi đang duyệt stream dựa trên file có thể xuất hiện ở runtime dưới dạng `UncheckedIOException`. Xử lý tại đúng biên I/O; đừng nuốt lỗi.
9. **Lạm dụng side effect và trật tự:** `forEach` trên parallel stream không đảm bảo thứ tự gặp phần tử; nếu thứ tự quan trọng, xem `forEachOrdered` hoặc xử lý tuần tự. Dùng `map`/`filter` chủ yếu cho hàm biến đổi và điều kiện thuần.

## 8. Checklist ghi nhớ

- Đang **truyền/đọc/ghi byte hoặc ký tự**? Nghĩ tới `java.io` và `java.nio.file`.
- Đang **xử lý chuỗi phần tử**? Nghĩ tới `java.util.stream`.
- Đang đọc từ file qua `Files.lines`? **Cả hai** cùng xuất hiện: I/O cung cấp phần tử, Stream API xử lý phần tử.
- Có file/socket/reader mở? Xác định **ai sở hữu và ai đóng** tài nguyên.
- Có terminal operation? Pipeline đã bị tiêu thụ; muốn chạy lại phải tạo Stream mới.

**Kết luận:** “stream” trong I/O nói về **dòng dữ liệu đi qua điểm đọc/ghi**; “stream” trong Stream API nói về **pipeline xử lý phần tử**. Khi kết hợp chúng, chọn API theo dạng dữ liệu, kiểm soát ngoại lệ và đóng tài nguyên tại đúng ranh giới.

### Tài liệu tra cứu (Java SE 17)

- [`java.io.InputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html), [`Reader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Reader.html)
- [`java.util.stream.Stream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html)
- [`Files.lines` và các tiện ích `Files`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [`BufferedReader.lines`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="io-overview-005">Bản đồ java.io, java.nio và NIO.2</a>

<details>
<summary>Xem chi tiết</summary>

# Bản đồ `java.io`, `java.nio` và NIO.2

## Mục tiêu bài học

Sau bài này, bạn có thể chọn đúng API cho đọc/ghi tệp, xử lý văn bản, dữ liệu nhị phân, kết nối mạng và theo dõi hệ thống tệp; hiểu vai trò của **stream**, **reader/writer**, **buffer**, **channel**, **selector**, **`Path`** và **`Files`**; phân biệt I/O chặn, không chặn và bất đồng bộ.

Ba tên gọi thường đi cùng nhau nhưng không phải ba hệ thống loại trừ nhau:

- **`java.io`**: API stream/reader/writer truyền thống, thiên về xử lý dữ liệu tuần tự.
- **`java.nio`** (cùng `java.nio.channels`, `java.nio.charset`): buffer, channel, selector và chuyển đổi charset.
- **NIO.2**, xuất hiện từ Java 7: tên gọi các mở rộng I/O, nổi bật nhất là `java.nio.file` với `Path`, `Files`, `FileSystem`, file attributes và `WatchService`, cùng các channel bất đồng bộ. Không có gói chuẩn `java.nio2`.

## 1. Bản đồ hệ sinh thái

```text
Bài toán I/O
├── Đọc/ghi byte tuần tự
│   ├── java.io.InputStream / OutputStream
│   └── java.nio.file.Files.newInputStream / newOutputStream
├── Đọc/ghi văn bản
│   ├── java.io.Reader / Writer, BufferedReader / BufferedWriter
│   ├── java.nio.charset.Charset / CharsetDecoder / CharsetEncoder
│   └── java.nio.file.Files.newBufferedReader / newBufferedWriter
├── Đường dẫn, tệp, thư mục, metadata
│   ├── java.io.File (đời trước)
│   └── java.nio.file.Path / Files / FileSystem / FileStore / attributes
├── Truy cập byte có buffer, vị trí, mapping
│   └── java.nio.ByteBuffer + java.nio.channels.FileChannel
├── Kết nối mạng
│   ├── java.net.Socket + InputStream / OutputStream
│   └── java.nio.channels.SocketChannel / ServerSocketChannel /
│       DatagramChannel + ByteBuffer
├── Quản lý nhiều socket theo sự kiện
│   └── SelectableChannel + Selector
└── Đọc/ghi tệp với kết quả bất đồng bộ
    └── java.nio.channels.AsynchronousFileChannel
```

Có ba câu hỏi độc lập:

1. **Dữ liệu là byte hay văn bản?** Stream xử lý byte; Reader/Writer xử lý ký tự và dùng charset để liên hệ với byte. `ByteBuffer` giữ byte trong bộ nhớ.
2. **Nguồn/đích là gì?** Hệ thống tệp dùng `Path`/`Files`; socket có kênh riêng; một API không thay thế mọi loại nguồn dữ liệu.
3. **Mô hình thực thi nào?** Chặn, không chặn hay completion bất đồng bộ? Tên NIO không bảo đảm mọi thao tác đều non-blocking.

## 2. `java.io`: stream, reader/writer và các lớp bao

### Byte stream

`InputStream` đọc byte, `OutputStream` ghi byte. Các API tiêu biểu:

| API | Công dụng |
|---|---|
| `FileInputStream` / `FileOutputStream` | Byte I/O từ tệp |
| `BufferedInputStream` / `BufferedOutputStream` | Giảm số thao tác I/O nhỏ |
| `DataInputStream` / `DataOutputStream` | Đọc/ghi các kiểu nguyên thủy theo định dạng nhị phân |
| `ByteArrayInputStream` / `ByteArrayOutputStream` | Dữ liệu trong mảng byte |
| `PrintStream` | Xuất dữ liệu dạng chữ, như `System.out` |
| `ObjectInputStream` / `ObjectOutputStream` | Java serialization; tránh giải tuần tự dữ liệu không tin cậy |

`InputStream.read()` trả về `0..255` hoặc `-1` khi hết dữ liệu. `read(byte[])` **có thể đọc ít hơn** số byte yêu cầu; không được coi một lời gọi read là một record/thông điệp hoàn chỉnh. `flush()` đẩy dữ liệu khỏi buffer của stream, nhưng không mặc nhiên đồng nghĩa ghi bền vững xuống thiết bị lưu trữ.

Dùng try-with-resources để đóng nguồn/đích ngay cả khi có exception:

```java
try (InputStream in = Files.newInputStream(source);
     OutputStream out = Files.newOutputStream(destination)) {
    in.transferTo(out);
}
```

Đây là **đoạn minh họa** (có hai biến `Path source`, `Path destination`; import `java.io.*` và `java.nio.file.*`). `transferTo` có từ Java 9. `Files.newInputStream` thuộc NIO.2 nhưng **trả về `java.io.InputStream`**: các họ API được thiết kế để phối hợp.

### Character stream và charset

`Reader`/`Writer` làm việc với ký tự; `InputStreamReader`/`OutputStreamWriter` chuyển qua lại giữa byte stream và character stream nhờ một charset. `BufferedReader.readLine()` đọc dòng nhưng **bỏ ký tự xuống dòng**, còn `BufferedWriter` hữu ích để ghi dần lượng văn bản lớn.

```text
file bytes → InputStream → UTF-8 decoder → Reader → char
char → Writer → UTF-8 encoder → OutputStream → file bytes
```

Ưu tiên charset tường minh, ví dụ `StandardCharsets.UTF_8`. Trên **Java 17**, charset mặc định có thể phụ thuộc hệ điều hành/thiết lập; từ Java 18, JEP 400 đưa UTF-8 thành mặc định. Với định dạng dữ liệu cần tính di động, vẫn chỉ rõ charset.

Một điểm Unicode: độ dài `String` đếm đơn vị UTF-16 `char`, không phải số byte UTF-8 hay số code point. Ký tự ngoài BMP có thể dùng hai `char` (surrogate pair).

### `java.io.File`

`File` đại diện cho đường dẫn theo API cũ, không phải chính nội dung tệp. Trong mã mới, thường ưu tiên `Path`/`Files` vì nhiều thao tác hệ thống tệp và ngoại lệ I/O cụ thể hơn. Khi kết nối API cũ:

```java
Path path = oldFile.toPath();
File oldFileAgain = path.toFile();
```

Đoạn này giả định `oldFile` là `java.io.File`; `toFile()` chỉ được bảo đảm cho `Path` thuộc filesystem provider mặc định.

## 3. `java.nio`: Buffer + Channel + Charset + Selector

### `ByteBuffer` có trạng thái

`ByteBuffer` lưu byte và quản lý ba giá trị:

| Giá trị | Ý nghĩa |
|---|---|
| `capacity` | Sức chứa cố định |
| `position` | Chỉ số byte tiếp theo sẽ ghi/đọc |
| `limit` | Ranh giới được phép truy cập trong pha hiện tại |

Bất biến: `0 <= position <= limit <= capacity`. Mô hình chuyển pha:

```text
put(...) ──> buffer ở chế độ ghi ── flip() ──> get(...)
                                     │
                               hasRemaining()
                                     │
                           ┌─────────┴─────────┐
                           │                   │
                   clear() ghi lại      compact() giữ dữ liệu
                   từ đầu buffer        chưa đọc, ghi tiếp
```

- `put(...)` tăng `position` khi ghi vào buffer.
- `flip()` đặt `limit = position`, `position = 0` để đọc phần vừa ghi.
- `get()` tăng `position` khi đọc.
- `clear()` đặt `position = 0`, `limit = capacity`, **không xóa byte khỏi bộ nhớ**.
- `compact()` đưa các byte chưa đọc về đầu và đặt vị trí để bổ sung byte.
- `rewind()` đặt `position = 0` mà không đổi `limit`.

Chương trình Java 17 độc lập, lưu vào **`ByteBufferStateDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ByteBufferStateDemo {
    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put(new byte[] {10, 20, 30});
        System.out.printf(
            "write: position=%d, limit=%d, capacity=%d%n",
            buffer.position(), buffer.limit(), buffer.capacity()
        );

        buffer.flip();
        System.out.println("read: " + buffer.get() + ", " + buffer.get());

        buffer.compact();  // Giữ 30, chuẩn bị ghi tiếp
        buffer.put((byte) 40);
        buffer.flip();

        List<Integer> remaining = new ArrayList<>();
        while (buffer.hasRemaining()) {
            remaining.add(Byte.toUnsignedInt(buffer.get()));
        }
        System.out.println("remaining: " + remaining);
    }
}
```

Chạy:

```bash
javac ByteBufferStateDemo.java
java ByteBufferStateDemo
```

Stdout:

```text
write: position=3, limit=5, capacity=5
read: 10, 20
remaining: [30, 40]
```

Kết quả chứng minh `compact()` giữ giá trị 30 chưa được đọc. Nếu gọi `clear()` thay `compact()`, vùng byte đó không còn được đánh dấu cần giữ.

`ByteBuffer.allocate` cấp phát heap buffer; `allocateDirect` tạo direct buffer có thể giảm một số bước sao chép ở native I/O nhưng chi phí cấp phát/thu hồi và giới hạn bộ nhớ khác. Không mặc định direct buffer luôn nhanh hơn. `ByteOrder` quy định thứ tự byte cho số nhiều byte, khác với charset của văn bản.

### `Channel` vận chuyển; `Buffer` chứa byte

`Channel` biểu diễn kết nối I/O. Các channel byte đọc vào `ByteBuffer` hoặc ghi từ `ByteBuffer`:

```text
Đọc: nguồn → channel.read(buffer) → buffer ghi → flip() → get()
Ghi: put(...) → buffer có dữ liệu → flip() → channel.write(buffer) → đích
```

`FileChannel` hỗ trợ đọc/ghi theo vị trí, `position`, `size`, `truncate`, `transferTo`, `transferFrom`, `map` và `force`. Một `read(buffer)` có thể trả `-1` ở EOF, `0` hoặc số byte dương nhỏ hơn sức chứa; một `write(buffer)` có thể ghi ít hơn toàn bộ số byte còn lại. Nếu cần chuyển hết dữ liệu, kiểm tra kết quả và lặp theo trạng thái `hasRemaining()`.

`FileChannel.map` trả `MappedByteBuffer` để ánh xạ vùng tệp vào bộ nhớ. Nó giúp truy cập một số kiểu dữ liệu lớn/ngẫu nhiên nhưng kéo theo quản lý mapping, native resource và nhất quán dữ liệu; không phải cách đọc file mặc định.

### Charset và ranh giới từng chunk

`java.nio.charset.Charset`, `CharsetDecoder`, `CharsetEncoder` xử lý biểu diễn ký tự. `StandardCharsets.UTF_8.decode(buffer)` thuận tiện nếu buffer chứa **toàn bộ** chuỗi byte UTF-8. Khi dữ liệu đến nhiều chunk, các byte của cùng một ký tự có thể bị chia ở hai chunk; hãy dùng decoder streaming, giữ phần byte chưa đủ, tránh chuyển từng chunk riêng thành String gây ký tự lỗi.

### Selector và mạng không chặn

`Selector` cho phép một thread chờ sự kiện sẵn sàng trên nhiều **`SelectableChannel`**:

1. Mở `Selector`, tạo `SocketChannel`, gọi `configureBlocking(false)`.
2. Đăng ký channel với các operation phù hợp như `OP_CONNECT`, `OP_READ`, `OP_WRITE`.
3. Trong event loop, gọi `select()` hoặc `select(timeout)`, xử lý các `SelectionKey` sẵn sàng, loại key đã xử lý khỏi selected-key set.
4. Giữ trạng thái đọc/ghi từng phần qua các sự kiện, xử lý lỗi, đóng tài nguyên và backpressure.

`SocketChannel`, `ServerSocketChannel`, `DatagramChannel` và một số pipe channel có thể dùng selector. **`FileChannel` không phải `SelectableChannel` và không đăng ký selector được.** Việc đăng ký `OP_WRITE` liên tục có thể gây vòng lặp bận vì socket thường sẵn sàng để ghi; chỉ đăng ký khi có dữ liệu còn chờ, tùy thiết kế event loop.

Selector không tự xử lý protocol framing, timeout, thread safety hoặc backpressure. TCP là **byte stream**: một `read` có thể nhận nửa message hoặc nhiều message; ứng dụng phải định nghĩa khung dữ liệu.

## 4. NIO.2: `Path`, `Files` và filesystem services

### `Path` biểu diễn đường dẫn; `Files` thao tác

`Path` đại diện các thành phần đường dẫn thuộc một `FileSystem`, không có nghĩa tệp đã tồn tại. `Files` là tập phương thức thao tác file/thư mục.

| Nhu cầu | API |
|---|---|
| Tạo đường dẫn | `Path.of(...)`; `base.resolve("name.txt")` |
| Chuẩn hóa cú pháp | `path.normalize()` |
| Kiểm tra loại file | `Files.isRegularFile`, `Files.isDirectory` |
| Đọc tệp văn bản nhỏ | `Files.readString(path, UTF_8)` |
| Ghi tệp văn bản | `Files.writeString(path, value, UTF_8)` |
| Xử lý văn bản dạng stream | `Files.newBufferedReader`, `newBufferedWriter` |
| Xử lý byte | `Files.newInputStream`, `newOutputStream` |
| Tạo cây thư mục | `Files.createDirectories` |
| Sao chép/di chuyển | `Files.copy`, `Files.move` cùng option |
| Liệt kê thư mục | `Files.list`, `Files.newDirectoryStream` |
| Duyệt đệ quy | `Files.walk`, `Files.walkFileTree` |
| Thuộc tính | `Files.readAttributes`, `BasicFileAttributes` |
| Theo dõi thay đổi | `WatchService`, `Path.register` |

`Path.of("data", "file.txt")` thường tương đối theo working directory của tiến trình. `toAbsolutePath()` chuyển thành đường dẫn tuyệt đối; `toRealPath()` còn truy vấn filesystem, có thể giải symbolic link. `normalize()` chỉ làm sạch thành phần cú pháp như `.`/`..`, không truy vấn đĩa và không bảo đảm an toàn.

`Files.exists(path)` và `Files.notExists(path)` đều có thể trả `false` khi không xác định. Kiểm tra trước rồi mở tệp có race condition (TOCTOU); hãy xử lý `IOException` của chính thao tác thay vì tin trạng thái ban đầu là bất biến.

### Ví dụ thực hành kết hợp cả ba họ API

Chương trình độc lập Java 17, lưu vào **`IoMapDemo.java`**. Nó tạo file bằng `Path`/`Files` (NIO.2), đọc text bằng `BufferedReader` (`java.io`) và đọc byte bằng `FileChannel`/`ByteBuffer` (NIO).

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class IoMapDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("io-map-");
        Path file = dir.resolve("message.txt");

        try {
            Files.writeString(file, "Xin chào Java\n", StandardCharsets.UTF_8);

            try (BufferedReader reader =
                     Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                System.out.println("Reader: " + reader.readLine());
            }

            try (FileChannel channel =
                     FileChannel.open(file, StandardOpenOption.READ)) {
                ByteBuffer buffer = ByteBuffer.allocate(64);
                int bytesRead = channel.read(buffer);
                buffer.flip();

                String text = StandardCharsets.UTF_8.decode(buffer)
                    .toString().stripTrailing();

                System.out.println("Channel bytes: " + bytesRead);
                System.out.println("Channel text: " + text);
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
```

Chạy:

```bash
javac IoMapDemo.java
java IoMapDemo
```

Stdout:

```text
Reader: Xin chào Java
Channel bytes: 15
Channel text: Xin chào Java
```

`Xin chào Java` kèm `\\n` có 14 `char` nhưng chiếm **15 byte UTF-8** do `à` dùng hai byte. Buffer 64 byte đủ chứa tất cả dữ liệu của ví dụ. Với file lớn, phải lặp read và quản lý ranh giới UTF-8 qua các chunk. `finally` dọn đúng thư mục/tệp vừa tạo trong ví dụ; không áp dụng xóa tương tự cho file người dùng.

### Duyệt thư mục và thuộc tính

`Files.list(dir)` trả về `Stream<Path>` gồm các mục trực tiếp; `Files.walk(dir)` có thể đi sâu vào cây. Stream do hai API này tạo ra sở hữu tài nguyên hệ thống tệp: phải đóng.

```java
try (var paths = Files.list(dir)) {
    paths.filter(Files::isRegularFile)
         .forEach(System.out::println);
}
```

Đoạn minh họa cần biến `Path dir` và import `java.nio.file.Files`; stdout phụ thuộc nội dung thực tế. `Files.walkFileTree` với `FileVisitor` thích hợp khi cần xử lý lỗi, traversal sâu và điều khiển hành vi theo từng nút.

`Files.size`, `BasicFileAttributes`, `PosixFileAttributes`, `DosFileAttributes` có mức hỗ trợ phụ thuộc filesystem provider/hệ điều hành. Không giả định attribute POSIX tồn tại trên Windows, hoặc symbolic link hoạt động như nhau mọi nơi. `Files.readSymbolicLink`, `Files.isSymbolicLink` và `LinkOption.NOFOLLOW_LINKS` giúp phân biệt một số thao tác đối với link. Nếu theo link khi duyệt cây, cần xử lý vòng lặp link/quyền truy cập.

### `WatchService`

`FileSystem.newWatchService()` tạo watcher; gọi `directory.register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)` để đăng ký thư mục. Vòng lặp nhận `WatchKey`, đọc `pollEvents()`, xử lý `OVERFLOW` và gọi `reset()`; khi `reset()` trả false, key không còn hợp lệ. Việc watch một thư mục không tự động watch toàn bộ cây con. Sự kiện có thể gộp, đến trễ, bị mất do tràn hàng đợi hoặc do provider/hệ điều hành; ứng dụng cần đối chiếu trạng thái thực tế khi yêu cầu tính chính xác.

## 5. Chặn, không chặn, bất đồng bộ là các cơ chế khác nhau

| Mô hình | Ví dụ API | Đặc điểm |
|---|---|---|
| Đồng bộ, thường chặn | `InputStream.read`, `Files.readString`, các thao tác `FileChannel` thông thường | Thread gọi đợi thao tác hoàn tất theo ngữ nghĩa API |
| Non-blocking | `SocketChannel.configureBlocking(false)` + `Selector` | Một lần đọc/ghi có thể chưa tiến triển; tự giữ trạng thái và xử lý sự kiện |
| Asynchronous completion | `AsynchronousFileChannel.read` với `Future` hoặc `CompletionHandler` | Khởi động I/O rồi nhận kết quả sau; implementation có thể dựa trên thread pool |

Ví dụ async ngắn, **không phải chương trình độc lập** (giả sử có biến `Path path`, phương thức cho phép `throws Exception`):

```java
try (AsynchronousFileChannel channel =
         AsynchronousFileChannel.open(path, StandardOpenOption.READ)) {
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    Future<Integer> pending = channel.read(buffer, 0L);
    int count = pending.get();  // Thread hiện tại đợi ở đây
    if (count > 0) {
        buffer.flip();
        // Xử lý các byte vừa đọc
    }
}
```

Cần import `java.nio.ByteBuffer`, `java.nio.channels.AsynchronousFileChannel`, `java.nio.file.StandardOpenOption`, `java.util.concurrent.Future`. `pending.get()` ngay sau `read` khiến thread gọi vẫn **chờ**; để tránh chờ trực tiếp có thể dùng `CompletionHandler`. Provider không bắt buộc dùng native kernel asynchronous I/O.

NIO.2 còn bổ sung `AsynchronousSocketChannel`, `AsynchronousServerSocketChannel` cho mạng; mô hình callback/completion của chúng không giống event loop với `Selector`.

## 6. Các cầu nối (adapter) quan trọng

```text
java.io.File ──────────── .toPath() ──> Path
Path mặc định ─────────── .toFile() ──> java.io.File
Path ───────── Files.newInputStream ──> InputStream
Path ─── Files.newBufferedReader ──> BufferedReader
Path ──────── FileChannel.open ──> FileChannel ↔ ByteBuffer
Channel ─── Channels.newInputStream ──> InputStream
InputStream ─── Channels.newChannel ──> ReadableByteChannel
```

`java.nio.channels.Channels` là nơi có adapter stream/channel. Việc bọc stream thành channel **không tự biến nó thành selectable channel**. `Files.newByteChannel` có thể cung cấp `SeekableByteChannel` để truy cập byte theo vị trí; `FileChannel` có các tính năng tệp bổ sung như transfer, mapping, force.

Khi thư viện yêu cầu `InputStream`, `Reader`, `Path` hay `ByteBuffer`, nên dùng kiểu thư viện nhận và chuyển đổi ở đúng ranh giới. Không cần tái tạo một pipeline buffer/channel phức tạp chỉ để dùng API mới hơn.

## 7. Bảng chọn API theo bài toán

| Bài toán | Lựa chọn thông thường | Khi cần thay đổi |
|---|---|---|
| Đọc text nhỏ có charset xác định | `Files.readString(path, UTF_8)` | Text lớn: `newBufferedReader` để đọc dần |
| Ghi text | `Files.writeString` hoặc `newBufferedWriter` | Ghi lâu/dần: writer và buffer |
| Sao chép file | `Files.copy` | Dữ liệu không phải file: stream/channel |
| Xử lý binary tuần tự | `InputStream`/`OutputStream` | Truy cập offset: `FileChannel` |
| Cập nhật một vùng file | `FileChannel`/`SeekableByteChannel` | Mapping lớn đã đo đạc: `MappedByteBuffer` |
| Danh sách file, metadata | `Path`, `Files`, `FileVisitor` | API legacy cần `File`: dùng adapter |
| Mạng đơn giản | Socket + stream | Nhiều socket: cân nhắc channel + selector |
| Theo dõi thay đổi thư mục | `WatchService` | Cần trạng thái đúng: kết hợp quét xác minh |
| I/O tệp với completion bất đồng bộ | `AsynchronousFileChannel` | Chỉ cần I/O đồng bộ: `FileChannel`/`Files` |

Luôn xét **loại dữ liệu, kích thước, pattern truy cập, số kết nối và nhu cầu concurrency** trước khi chọn. `Files.readAllBytes` và `Files.readString` giữ nội dung trong bộ nhớ, dễ gây áp lực heap với file đầu vào không kiểm soát kích thước.

## 8. Lỗi phổ biến và các giới hạn cần nhớ

1. **Nhầm byte/character.** Không dựa vào độ dài `String` để suy số byte UTF-8, không decode từng chunk độc lập khi một ký tự có thể trải qua nhiều chunk.
2. **Đánh đồng NIO với non-blocking.** `Files.readString` và `FileChannel` thông thường là đồng bộ; `FileChannel` không đăng ký `Selector`.
3. **Giả định một lần read/write hoàn thành tất cả.** Xử lý EOF `-1`, partial read/write, backpressure và framing giao thức.
4. **Quên `flip()`, hoặc `clear()` quá sớm.** `compact()` giữ byte chưa đọc khi cần bổ sung dữ liệu.
5. **Rò rỉ tài nguyên.** Dùng try-with-resources cho stream, reader/writer, channel, `DirectoryStream`, `Files.list`/`walk` stream, `WatchService`.
6. **Nhầm flush/close với durability và atomicity.** `Files.move(..., ATOMIC_MOVE)` có thể báo `AtomicMoveNotSupportedException`; `flush` không tự bảo đảm `fsync`. Với dữ liệu quan trọng cần quy trình tệp tạm, sync, move và fallback có kiểm chứng theo platform.
7. **Giả định đường dẫn giống nhau mọi nơi.** Khác biệt case sensitivity, quyền, separator, symlink, metadata, charset mặc định và filesystem provider đều có thể làm mã thiếu tính di động.
8. **Kiểm tra rồi tin trạng thái không đổi.** `Files.exists`, `isReadable` và việc chuẩn hóa đường dẫn không ngăn TOCTOU, thay link, hoặc lỗi quyền truy cập; phải xử lý exception của thao tác thực.
9. **Không giới hạn tài nguyên.** Hạn chế open files/channels, buffer native, độ sâu traversal và kích thước dữ liệu; file mapping có vòng đời riêng.
10. **Giải tuần tự dữ liệu không tin cậy.** `ObjectInputStream` có rủi ro bảo mật với nguồn không đáng tin; ưu tiên định dạng/giao thức có xác thực đầu vào rõ ràng.

## 9. Câu hỏi tự kiểm tra

1. Cần đọc CSV 2 GB UTF-8. Vì sao `Files.readString` thường không phù hợp? Hãy chọn `Files.newBufferedReader` và giải thích việc đóng tài nguyên, xử lý từng record.
2. `ByteBuffer` có `capacity=8`, đã ghi 5 byte. Sau `flip()` thì `position=0, limit=5`. Đọc 2 byte rồi `compact()` thì **`position=3, limit=8`**. Vì sao?
3. Server cần phục vụ nhiều kết nối TCP. `SocketChannel` + `Selector` giải quyết bài toán gì, và vẫn cần tự giải quyết protocol framing/backpressure như thế nào?
4. Có thể đăng ký `FileChannel` với `Selector` không? **Không**, vì nó không phải `SelectableChannel`.
5. Có thể xem `WatchService` là lịch sử sự kiện đầy đủ không? **Không**, vì sự kiện có thể gộp/mất và xuất hiện `OVERFLOW`; cần đối chiếu filesystem.

**Ghi nhớ:** `java.io` cung cấp stream và reader/writer; `java.nio` cung cấp buffer, channel, charset và selector; NIO.2 mở rộng thành `Path`, `Files`, dịch vụ filesystem và API bất đồng bộ. Chúng bổ sung cho nhau và được chọn theo bản chất I/O của ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="io-overview-006">Tuần tự, truy cập ngẫu nhiên, blocking và non-blocking</a>

<details>
<summary>Xem chi tiết</summary>

# Tuần tự, truy cập ngẫu nhiên, blocking và non-blocking trong Java I/O

Khi thiết kế thao tác nhập/xuất (I/O), có hai câu hỏi khác nhau:

1. **Truy cập dữ liệu theo thứ tự nào?** Đọc/ghi **tuần tự** (sequential) hay có thể **nhảy tới vị trí bất kỳ** (random access)?
2. **Lời gọi phải đợi đến lúc nào?** Thao tác **blocking** có thể giữ luồng đang gọi để chờ; thao tác **non-blocking** trả quyền điều khiển khi chưa thể xử lý ngay.

Đây là **hai trục phân loại độc lập về khái niệm**, không phải bốn loại I/O loại trừ nhau. Một chương trình có thể đọc tuần tự bằng API blocking, hoặc xử lý dữ liệu tuần tự trên kết nối mạng non-blocking. Khả năng kết hợp cụ thể phụ thuộc thiết bị và API: Java NIO không cung cấp chế độ `configureBlocking(false)` cho `FileChannel` của tệp thông thường.

## 1. Tuần tự (sequential) và truy cập ngẫu nhiên (random access)

### 1.1. Truy cập tuần tự

Với truy cập tuần tự, chương trình xử lý dữ liệu theo dòng chảy từ vị trí hiện tại tới vị trí tiếp theo. Ví dụ: đọc từng byte, từng khối byte hoặc từng dòng văn bản; ghi liên tiếp vào cuối hoặc vị trí hiện tại của luồng.

- `InputStream` / `OutputStream` làm việc với byte; `Reader` / `Writer` làm việc với ký tự.
- `BufferedInputStream`, `BufferedReader` giảm số lần truy cập tầng dưới nhờ bộ đệm. `BufferedReader.readLine()` phù hợp cho văn bản chia dòng.
- `Files.newInputStream(path)`, `Files.newBufferedReader(path, charset)` là cách mở luồng/tệp thông dụng.
- `mark()` / `reset()` trên một số `InputStream` chỉ cho quay lại dấu mốc theo điều kiện hỗ trợ; **không tương đương** với việc đặt con trỏ tùy ý vào mọi offset.
- "Tuần tự" nói về **cách truy cập**, không đảm bảo dữ liệu nằm liên tiếp vật lý trên đĩa hay việc đọc luôn nhanh.

Ví dụ Java 17+ độc lập, tạo tệp UTF-8 tạm, đọc từng dòng và dọn dẹp tệp:

```java
// SequentialDemo.java
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SequentialDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("sequential-", ".txt");
        try {
            Files.writeString(file, "dòng 1\ndòng 2\n", StandardCharsets.UTF_8);

            try (BufferedReader reader =
                     Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 SequentialDemo.java
java SequentialDemo
```

Đầu ra:

```text
dòng 1
dòng 2
```

`readLine()` tiến dần qua nội dung; `null` báo hết tệp. Nó trả về phần nội dung dòng, không kèm dấu xuống dòng. `try-with-resources` đóng reader; `finally` dọn tệp tạm kể cả khi có lỗi.

### 1.2. Truy cập ngẫu nhiên

Random access cho phép đặt một **vị trí logic (offset)** rồi đọc/ghi tại đó mà không phải đọc lần lượt toàn bộ phần trước. Đây là kỹ thuật thích hợp cho chỉ mục cố định, bản ghi kích thước cố định, cập nhật một vùng trong tệp hoặc đọc header/footer.

Trong Java có ba API tiêu biểu:

| API | Vị trí và ý nghĩa |
| --- | --- |
| `RandomAccessFile` | `seek(long)` di chuyển con trỏ tệp; `getFilePointer()`, `length()`, `setLength()`; hỗ trợ đọc/ghi kiểu nguyên thủy. |
| `SeekableByteChannel` | Trừu tượng channel có `position()` và `position(long)`; các thao tác đọc/ghi tương đối dùng vị trí hiện tại. |
| `FileChannel` | Channel cho tệp; hỗ trợ `position(long)` và `read(ByteBuffer, long)` / `write(ByteBuffer, long)` tại offset chỉ định mà **không đổi vị trí hiện tại** của channel. |

Vị trí trong các API này được tính bằng **byte**, không phải số ký tự Unicode hay số dòng. Khi dùng văn bản UTF-8 có ký tự nhiều byte, không thể giả định `seek(10)` tương ứng ký tự thứ 11. Đồng thời, random access chỉ có ý nghĩa khi nguồn dữ liệu và API cho phép seek; một TCP socket không thể nhảy ngược tới byte cũ của luồng mạng.

Ví dụ dùng `RandomAccessFile` với ba số nguyên `int` (mỗi số 4 byte):

```java
// RandomAccessDemo.java
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class RandomAccessDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("random-", ".bin");
        try {
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
                raf.setLength(0);
                raf.writeInt(10);  // byte 0..3
                raf.writeInt(20);  // byte 4..7
                raf.writeInt(30);  // byte 8..11

                raf.seek(4);
                System.out.println("second = " + raf.readInt());

                raf.seek(8);
                raf.writeInt(99);  // ghi đè số thứ ba
                raf.seek(0);
                System.out.println("values = " +
                        raf.readInt() + ", " + raf.readInt() + ", " + raf.readInt());
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac RandomAccessDemo.java
java RandomAccessDemo
```

Đầu ra:

```text
second = 20
values = 10, 20, 99
```

`seek(4)` đi thẳng tới số nguyên thứ hai; `seek(8)` đi tới số thứ ba. `writeInt` / `readInt` dùng biểu diễn nhị phân 4 byte theo thứ tự big-endian được xác định bởi `DataOutput` / `DataInput`, vì vậy đây **không phải** ví dụ xử lý số ở dạng chuỗi. Với bản ghi có độ dài biến đổi, chỉ `seek()` thôi chưa đủ: cần cấu trúc chỉ mục hoặc định dạng lưu trữ biết offset của bản ghi.

Với `FileChannel`, phân biệt hai kiểu:

```java
channel.position(100);       // đổi vị trí logic của channel
channel.read(buffer);        // đọc từ vị trí hiện tại, cập nhật vị trí
channel.read(buffer, 500);   // đọc tại byte 500, không đổi vị trí hiện tại
```

Ba dòng trên là **đoạn minh họa**, giả định `channel` đã mở và `buffer` còn chỗ trống. `read` có thể đọc ít byte hơn yêu cầu; khi cần đủ một khối, phải lặp và kiểm tra EOF. Các API seek không tự làm I/O thành non-blocking.

## 2. Blocking và non-blocking thực sự có nghĩa gì?

### 2.1. Blocking I/O

Với I/O blocking, lời gọi đang thực hiện **có thể chờ** cho đến khi dữ liệu đến, kết nối được chấp nhận, tài nguyên sẵn sàng hoặc thao tác hoàn tất / thất bại. Trong thời gian đó, luồng Java gọi API chưa tiếp tục chạy các câu lệnh phía sau.

Ví dụ:

- `InputStream.read()` trên socket có thể chờ peer gửi dữ liệu, đóng đầu ra hoặc phát sinh lỗi.
- `ServerSocket.accept()` và `ServerSocketChannel.accept()` ở chế độ blocking có thể chờ kết nối đến.
- `SocketChannel.read(ByteBuffer)` ở chế độ mặc định blocking có thể chờ dữ liệu khi buffer còn chỗ trống.
- `FileChannel.read` / `write` là thao tác đồng bộ và có thể chặn trong khi truy cập hệ thống tệp, dù thuộc gói `java.nio`.

"Blocking" **không có nghĩa mỗi lời gọi luôn đợi lâu**: dữ liệu đã sẵn sàng có thể trả kết quả ngay. Cũng không có nghĩa `read` luôn điền đầy buffer; số byte trả về phải được kiểm tra. Đường đọc có thể kết thúc bằng EOF (`-1`) hoặc lỗi. Timeout/cơ chế hủy của từng API khác nhau; không thể giả định mọi lời gọi blocking đều có cùng cách ngắt.

### 2.2. Non-blocking I/O

Ở chế độ non-blocking, thao tác trên channel được hỗ trợ có thể trả về ngay cả khi chưa đọc/ghi được dữ liệu. Mã ứng dụng tự quyết định khi nào thử lại hoặc đăng ký **sự kiện sẵn sàng** với `Selector`.

Java NIO áp dụng `configureBlocking(false)` cho các `SelectableChannel` như:

- `SocketChannel` — giao tiếp TCP;
- `ServerSocketChannel` — nhận kết nối TCP;
- `DatagramChannel` — gửi/nhận UDP.

Ví dụ `SocketChannel.read(dst)` khi `dst` còn chỗ:

| Kết quả | Cách hiểu |
| --- | --- |
| `n > 0` | Đã đọc `n` byte; chưa chắc đủ một thông điệp. |
| `n == 0` | Hiện chưa có byte nào được đọc; thường gặp ở chế độ non-blocking, không phải EOF. |
| `n == -1` | Đã tới cuối luồng đầu vào của kết nối. |
| Ném exception | Có lỗi I/O; cần xử lý/đóng tài nguyên phù hợp. |

Đối với `SocketChannel.write(src)`, một lần ghi có thể xử lý **ít hơn** `src.remaining()` byte, kể cả trong hoàn cảnh không được giả định ghi đầy. Nếu non-blocking trả `0`, giữ phần buffer chưa gửi và chờ lần sẵn sàng tiếp theo; **không bỏ dữ liệu chưa gửi**.

Lưu ý: `Selector.select()` **có thể tự blocking** để đợi sự kiện dù các channel đã đăng ký ở chế độ non-blocking. Sử dụng `selectNow()` hoặc `select(timeout)` khi cần chính sách chờ khác. Non-blocking giúp một thread quản lý nhiều kết nối bằng sự kiện; bản thân nó không bảo đảm nhanh hơn cho mọi khối lượng công việc.

## 3. Ma trận hai trục và lựa chọn API

| Kiểu truy cập | Blocking/synchronous | Non-blocking/asynchronous |
| --- | --- | --- |
| **Tuần tự** | `BufferedReader` đọc dòng từ tệp; `InputStream` trên socket; `SocketChannel` blocking. | `SocketChannel` / `DatagramChannel` non-blocking + `Selector` xử lý luồng byte hoặc datagram theo sự kiện. |
| **Ngẫu nhiên** | `RandomAccessFile`, `SeekableByteChannel`, `FileChannel` truy cập offset của tệp. | Đối với tệp thông thường, Java không cho chuyển `FileChannel` thành selectable non-blocking. `AsynchronousFileChannel` cung cấp API bất đồng bộ có vị trí, nhưng đó là **mô hình completion** riêng, không phải `SocketChannel.configureBlocking(false)`. |

Hai thuộc tính cần nhìn độc lập khi đọc tên thư viện:

- **`java.io` và `java.nio` không đồng nghĩa tuyệt đối với blocking và non-blocking.** NIO bao gồm `FileChannel` đồng bộ, `SocketChannel` có hai chế độ và `AsynchronousFileChannel`.
- **`AsynchronousFileChannel.read(buffer, position)`** trả `Future<Integer>` hoặc nhận `CompletionHandler`. Nếu gọi `future.get()` thì luồng đang gọi có thể chờ. Việc API bất đồng bộ không bảo đảm thao tác đĩa luôn chạy không cần thread nền.
- **Memory-mapped file** (`FileChannel.map`) hỗ trợ truy cập vị trí qua buffer ánh xạ nhưng lỗi trang / truy cập dữ liệu vẫn có thể chờ I/O; không nên gắn nhãn "luôn non-blocking".
- Một lần đọc `BufferedReader` tuần tự vẫn có thể dựa trên tệp hỗ trợ random access; đây là lựa chọn **cách chương trình truy cập**, không phải thuộc tính bất biến của mọi tệp.

## 4. `Selector`: sự kiện sẵn sàng trong non-blocking networking

Luồng xử lý cơ bản:

1. Mở một `SelectableChannel`, gọi `configureBlocking(false)`.
2. Đăng ký channel với `Selector` bằng các interest operations thích hợp (`OP_ACCEPT`, `OP_CONNECT`, `OP_READ`, `OP_WRITE`).
3. Gọi `select()`/`select(timeout)` để chờ; duyệt `selectedKeys()` và **loại key đã xử lý** khỏi tập selected.
4. Với kết nối nhận được, cấu hình socket mới sang non-blocking rồi đăng ký `OP_READ`.
5. Khi readable, đọc vào `ByteBuffer`; xử lý `> 0`, `0`, `-1`; lưu phần thông điệp chưa hoàn chỉnh. Khi writable, tiếp tục ghi phần còn lại của buffer.
6. Hủy key/đóng channel khi xong hoặc có lỗi; quản lý timeout và giới hạn bộ đệm khi chạy thực tế.

Ví dụ tự chạy trên loopback, **server ở chế độ non-blocking** và dùng `Selector`. Client trong ví dụ cố ý dùng chế độ blocking mặc định để gửi thông điệp ngắn; như vậy thể hiện rõ chế độ I/O do **từng channel** quyết định:

```java
// NonBlockingDemo.java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;

public class NonBlockingDemo {
    public static void main(String[] args) throws Exception {
        try (ServerSocketChannel server = ServerSocketChannel.open();
             Selector selector = Selector.open()) {

            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);

            try (SocketChannel client = SocketChannel.open()) {
                client.connect(server.getLocalAddress());

                ByteBuffer outgoing =
                        ByteBuffer.wrap("ping".getBytes(StandardCharsets.UTF_8));
                while (outgoing.hasRemaining()) {
                    client.write(outgoing);
                }
                client.shutdownOutput();

                ByteArrayOutputStream received = new ByteArrayOutputStream();
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                boolean eof = false;

                while (!eof && System.nanoTime() < deadline) {
                    selector.select(1000);
                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();

                        if (key.isAcceptable()) {
                            SocketChannel peer = server.accept();
                            if (peer != null) {
                                peer.configureBlocking(false);
                                peer.register(selector, SelectionKey.OP_READ);
                            }
                        } else if (key.isReadable()) {
                            SocketChannel peer = (SocketChannel) key.channel();
                            ByteBuffer buffer = ByteBuffer.allocate(32);
                            int n;

                            while ((n = peer.read(buffer)) > 0) {
                                buffer.flip();
                                received.write(buffer.array(), 0, buffer.remaining());
                                buffer.clear();
                            }
                            if (n == -1) {
                                peer.close();
                                eof = true;
                            }
                        }
                    }
                }

                if (!eof) {
                    throw new IOException("Timeout waiting for EOF");
                }
                System.out.println(received.toString(StandardCharsets.UTF_8));
            }
        }
    }
}
```

```bash
javac NonBlockingDemo.java
java NonBlockingDemo
```

Đầu ra trong điều kiện loopback hoạt động bình thường:

```text
ping
```

Diễn giải: hệ điều hành cấp một cổng loopback còn trống; `OP_ACCEPT` báo có kết nối cần nhận; peer được chuyển sang non-blocking và đăng ký `OP_READ`. Client gửi bốn byte và `shutdownOutput()`, vì vậy server có thể quan sát EOF `-1` sau dữ liệu. Dù `selector.select(1000)` có thể chờ, `server.accept()` và `peer.read()` trên server ở chế độ non-blocking sẽ không chờ dữ liệu/kết nối theo cơ chế blocking. Ví dụ có deadline để tránh vòng lặp vô hạn khi chạy trong môi trường mạng bất thường.

Ở chương trình thật, không thể coi một lần `read` là một thông điệp TCP. TCP là **byte stream**, không giữ ranh giới các lần `write`. Hãy xác định khung thông điệp (độ dài đầu bản tin, dấu phân cách, hoặc giao thức có header), giữ buffer qua nhiều sự kiện và chỉ giải mã ký tự UTF-8 khi đã gom đủ dãy byte. Với dữ liệu lớn, không cấp phát `ByteBuffer` mới cho mọi sự kiện như ví dụ minh họa; quản lý buffer theo connection.

### Các điểm dễ gây lỗi khi dùng NIO

- **`ByteBuffer.flip()`** chuyển từ trạng thái ghi vào buffer sang đọc dữ liệu trong buffer; **`clear()`** chuẩn bị ghi lại nhưng không xóa byte vật lý. Nếu có dữ liệu đọc dở, dùng **`compact()`** để giữ phần chưa xử lý.
- Non-blocking `read() == 0` không báo EOF; không dùng vòng `while (read(...) == 0) {}` chạy liên tục vì tốn CPU. Thường đăng ký `OP_READ` và chờ sự kiện mới.
- Chỉ bật `OP_WRITE` khi thật sự còn dữ liệu chờ gửi. Kết nối thường xuyên writable, đăng ký `OP_WRITE` vô điều kiện dễ khiến `select()` liên tục thức dậy (busy loop).
- `OP_CONNECT` đi với `SocketChannel` kết nối non-blocking: `connect()` có thể trả `false`, khi sẵn sàng kết nối phải kiểm tra `finishConnect()`.
- Sự kiện "readable" / "writable" biểu thị mức độ **sẵn sàng tại thời điểm quan sát**, không bảo đảm đọc đầy buffer hay truyền xong bản tin. Kết nối có thể đóng/lỗi giữa lần báo sự kiện và thao tác.
- Không trộn `InputStream` lấy từ socket với logic selector của cùng socket một cách tùy tiện; điều phối một mô hình đọc/ghi rõ ràng cho mỗi kết nối.

## 5. So sánh ưu/nhược và tình huống chọn lựa

| Nhu cầu | Gợi ý | Vì sao |
| --- | --- | --- |
| Đọc log hoặc CSV từ đầu đến cuối | `BufferedReader` / `Files.newBufferedReader` | Đọc tuần tự, ít trạng thái, dễ kết hợp xử lý dòng. |
| Cập nhật bản ghi nhị phân tại offset đã biết | `RandomAccessFile` hoặc `FileChannel` | Truy cập trực tiếp vị trí byte, tránh đọc toàn bộ tệp trước. |
| Sao chép tệp lớn theo từng khối | Luồng đệm hoặc `FileChannel` | Xử lý dữ liệu theo khối, không cần nạp cả tệp vào RAM. |
| Server TCP có nhiều kết nối cần xử lý bằng ít thread I/O | `SocketChannel` non-blocking + `Selector` | Một event loop có thể multiplex nhiều channel; đòi hỏi quản lý trạng thái và buffer. |
| Ứng dụng nhỏ, ít kết nối, muốn code dễ hiểu | Socket blocking hoặc thư viện trừu tượng mức cao | Luồng điều khiển tuyến tính thường dễ viết và kiểm thử. |
| Đọc nhiều vùng tệp qua API completion | `AsynchronousFileChannel` với `CompletionHandler` / `Future` | Biết offset và nhận kết quả khi hoàn tất mà không phải tự thiết kế selector cho tệp. |

**Hiệu năng không quyết định chỉ bởi nhãn.** Random access có thể tăng seek / random disk I/O và phá tính cục bộ bộ nhớ; đọc tuần tự thường tận dụng được readahead. Non-blocking giúp giảm số thread chờ khi phải quản lý nhiều socket, nhưng state machine, selector, framing và backpressure phức tạp hơn. Đo workload thực tế (số kết nối, kích thước buffer, độ trễ, throughput, mức sử dụng CPU/RAM) trước khi thay đổi thiết kế.

## 6. Các nhầm lẫn thường gặp và cách kiểm tra

1. **“Dùng `java.nio` là non-blocking.”** Sai: `FileChannel` và nhiều API NIO vẫn thực hiện I/O đồng bộ; phải kiểm tra channel có phải `SelectableChannel` và trạng thái `isBlocking()` hay không.
2. **“Random access làm thao tác không phải đợi đĩa.”** Sai: seek chỉ thay đổi vị trí logic; truy xuất dữ liệu có thể vẫn blocking.
3. **“Non-blocking read trả `0` tức là kết thúc stream.”** Sai: `-1` mới biểu thị EOF đối với `SocketChannel.read`.
4. **“`read(buffer)` luôn đọc đủ số byte yêu cầu.”** Sai: số byte đọc được có thể ít hơn dung lượng đích; phải dựa vào giá trị trả về, trạng thái buffer và giao thức.
5. **“Selector tự chạy mã khi dữ liệu đến.”** Sai: ứng dụng phải chạy event loop, duyệt selected keys, đọc/ghi và quản lý trạng thái.
6. **“`Future.get()` là cách đọc file không bao giờ chặn.”** Sai: lời gọi `get()` có thể chờ; muốn xử lý bất đồng bộ cần cấu trúc chương trình cho phép tiếp tục công việc khác hoặc dùng completion callback.
7. **“Seek theo số ký tự giống seek theo byte.”** Sai với UTF-8 và nhiều encoding khác; cần chỉ mục byte hoặc giải mã tuần tự đúng cách.

Tự kiểm tra: (a) Muốn đọc dòng 1 đến dòng cuối của một file văn bản, nên dùng kiểu truy cập nào? **Tuần tự**. (b) Muốn thay byte ở offset 4096 của file nhị phân? **Truy cập ngẫu nhiên** qua API seekable. (c) Một `SocketChannel` non-blocking trả `0` khi đọc? **Chưa đọc được byte nào tại thời điểm đó**, giữ trạng thái và đợi readiness. (d) `FileChannel.position(4096)` có khiến lần đọc tiếp theo tự non-blocking? **Không**.

Điểm cần giữ lại: **sequential/random access trả lời “đọc ở đâu”; blocking/non-blocking trả lời “lời gọi có chờ hay không”.** Chọn API dựa trên bản chất nguồn dữ liệu và yêu cầu xử lý, rồi kiểm tra kết quả từng thao tác đọc/ghi thay vì suy từ tên lớp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="io-overview-007">Kiến thức tiên quyết và trình tự học đề xuất</a>

<details>
<summary>Xem chi tiết</summary>

# Kiến thức tiên quyết và trình tự học Java I/O

> Phạm vi: Java Core I/O. Bài này xác định những kiến thức cần có, lý do chúng quan trọng và lộ trình học từ thao tác tệp đơn giản đến thiết kế luồng dữ liệu an toàn. Đây là bản đồ học tập; các API nâng cao được giới thiệu đúng vị trí, không cần thành thạo trước khi bắt đầu.

## 1. Đích đến của việc học I/O

**Input/Output (I/O)** là việc chương trình nhận dữ liệu từ hoặc gửi dữ liệu đến một nguồn/đích bên ngoài bộ nhớ đang xử lý: bàn phím, tệp, bộ đệm, kết nối mạng hoặc một luồng khác. Trong Java, cần phân biệt dữ liệu **byte** với **ký tự**, thao tác **đọc/ghi** với vòng đời **tài nguyên**, và cách xử lý dữ liệu nhỏ với dữ liệu lớn.

Sau lộ trình này, người học cần có khả năng:

- Chọn đúng API để đọc/ghi tệp văn bản UTF-8 và dữ liệu nhị phân.
- Giải thích được sự khác nhau giữa `InputStream`/`OutputStream`, `Reader`/`Writer`, `Path`/`Files`, và `Channel`/`Buffer`.
- Sử dụng `try-with-resources` để đóng tài nguyên; xử lý `IOException` có chủ đích.
- Xử lý tệp lớn theo từng phần thay vì mặc định tải toàn bộ vào RAM.
- Nhận biết rủi ro về đường dẫn, mã hóa, dữ liệu không đáng tin, ghi dở và việc nhiều luồng cùng truy cập.
- Tự xác minh kết quả đọc/ghi bằng ví dụ chạy được và bài kiểm tra cho trường hợp lỗi.

## 2. Kiến thức tiên quyết: cần biết gì trước?

| Nền tảng | Mức cần nắm | Liên hệ trực tiếp với I/O |
| --- | --- | --- |
| Cú pháp Java cơ bản | Khai báo biến, toán tử, `if`, `for`, `while`, gọi phương thức | Đọc lặp từng dòng/từng khối byte, dừng khi hết dữ liệu |
| Kiểu dữ liệu và mảng | `byte`, `char`, `int`, `String`, `byte[]`, chỉ số mảng | Bộ đệm byte; phân biệt ký tự Unicode với byte mã hóa |
| Lớp và đối tượng | `new`, constructor, interface, kế thừa, gọi phương thức | Các trừu tượng luồng, reader, writer và lớp bọc như `BufferedReader` |
| Ngoại lệ | `try`, `catch`, `finally`, `throws`, checked exception | Đọc/ghi có thể thất bại do tệp không tồn tại, mất quyền, thiết bị hoặc đường truyền |
| Tài nguyên và phạm vi biến | `AutoCloseable` ở mức sử dụng, `try-with-resources` | Đóng file handle/stream kể cả khi có lỗi |
| Collections ở mức căn bản | `List`, duyệt phần tử; biết giới hạn bộ nhớ | Lưu các dòng nhỏ khi cần, cân nhắc xử lý tuần tự cho dữ liệu lớn |
| Công cụ JDK | Chạy `java -version`, `javac`, `java`, xem đường dẫn làm việc | Biên dịch, chạy và quan sát tệp thật trên hệ điều hành |

**Nên củng cố trước nếu chưa nắm:** vòng lặp `while` với điều kiện trả về `null` hoặc `-1`; ý nghĩa của `throws IOException`; cách đối chiếu tên tệp nguồn với `public class`; và phân biệt `String` bất biến với mảng byte có thể thay đổi.

**Chưa bắt buộc ở điểm xuất phát:** lập trình đa luồng, socket, serialization, selector, memory-mapped file, hay framework web. Chúng trở nên hữu ích ở các chặng sau khi đã đọc/ghi cơ bản chắc chắn.

## 3. Những khái niệm gốc cần hiểu ngay

### 3.1 Byte, ký tự và charset

- **Byte** là đơn vị dữ liệu nhị phân (8 bit); ảnh, tệp nén và dữ liệu giao thức phải được xem là byte, không tự giải mã thành `String`.
- **Ký tự** là dữ liệu văn bản đã được giải mã. `String` chứa chuỗi ký tự Java (UTF-16 code units); một ký tự Unicode ngoài BMP có thể chiếm hai `char`.
- **Charset** quyết định cách biến văn bản thành byte và ngược lại. Nên chọn rõ `StandardCharsets.UTF_8` khi định dạng tệp hoặc giao thức yêu cầu UTF-8; không giả định máy nào cũng có cùng charset mặc định.
- Các ký tự xuống dòng có thể dùng `\n`, `\r\n` hoặc `\r`. `BufferedReader.readLine()` bỏ ký tự kết thúc dòng; nếu phải giữ nguyên byte hoặc line ending, không dùng cách đọc đó để tái tạo nguyên trạng.

Ví dụ tư duy: văn bản `"é"` và `"😀"` có độ dài `String.length()` lần lượt là 1 và 2 UTF-16 code units, nhưng khi mã hóa UTF-8 có lần lượt 2 và 4 byte. Vì vậy **số ký tự, số `char` và số byte không tương đương**.

### 3.2 Nguồn/đích dữ liệu và lớp API

| Cần giải quyết | Nhóm API nên nhận diện |
| --- | --- |
| Đọc/ghi byte | `InputStream`, `OutputStream` (`java.io`) |
| Đọc/ghi ký tự | `Reader`, `Writer`; `BufferedReader`, `BufferedWriter` |
| Thao tác tệp, thư mục, đường dẫn | `Path`, `Files` (`java.nio.file`) |
| I/O với bộ đệm, vị trí và kênh | `ByteBuffer`, `FileChannel` (`java.nio`) |
| Socket và multiplexing ở tầng nâng cao | `SocketChannel`, `Selector` khi yêu cầu bài toán phù hợp |

`java.io.InputStream` là luồng byte; **không phải** `java.util.stream.Stream<T>` dùng cho thao tác dữ liệu theo phong cách functional. `Path` mô tả đường dẫn; `Files` cung cấp thao tác lên hệ thống tệp. Một `Path` tự nó không mở hay đóng tệp.

### 3.3 Vòng đời tài nguyên và lỗi

Đường đọc thường có ba bước: **mở nguồn → đọc và xử lý → đóng tài nguyên**. Dùng `try-with-resources` cho đối tượng triển khai `AutoCloseable`: Java gọi `close()` khi ra khỏi khối, kể cả khi đọc ném ngoại lệ. Đóng thành công vẫn không đảm bảo mọi lỗi nghiệp vụ đã được xử lý; ví dụ, ghi một tệp đích quan trọng cần thêm chiến lược ghi tạm rồi thay thế theo yêu cầu hệ thống tệp.

`IOException` là checked exception phổ biến; gọi API cần bắt hoặc khai báo `throws`. Phải phân biệt lỗi đọc thất bại với tín hiệu **hết dữ liệu**: `InputStream.read()` trả `-1` khi EOF, còn `BufferedReader.readLine()` trả `null` khi EOF. Không dùng `available()` làm điều kiện đọc hết một stream.

## 4. Trình tự học đề xuất

Đi theo thứ tự dưới đây; mỗi chặng có một năng lực cần đạt trước khi chuyển sang chặng tiếp theo. Không cần học mọi lớp cùng lúc.

### Chặng 0 — Chuẩn bị môi trường và Java nền tảng

**Học:** kiểm tra JDK 17+, biên dịch/chạy một file `.java`, hiểu working directory, đường dẫn tương đối và tuyệt đối; ôn ngoại lệ, vòng lặp, `String`, `byte[]` và `try-with-resources`.

**Thực hành:** một chương trình nhận chuỗi, in ra console; cố tình truyền tên tệp không tồn tại ở bài thử kế tiếp và quan sát thông tin `IOException`.

**Đạt khi:** giải thích được vì sao đường dẫn tương đối phụ thuộc thư mục làm việc và vì sao `try-with-resources` khác việc chỉ gọi `close()` cuối luồng chạy bình thường.

### Chặng 1 — File và Path trước khi học nhiều loại stream

**Học:** `Path.of(...)`, `resolve(...)`, `normalize()`, `Files.exists(...)`, `Files.isRegularFile(...)`, `Files.createDirectories(...)`, `Files.readString(...)`, `Files.writeString(...)`, `Files.copy(...)`, `Files.move(...)`, `Files.deleteIfExists(...)`.

**Thực hành:** tạo thư mục riêng cho bài tập, ghi một tệp UTF-8, đọc lại và so sánh nội dung, sao chép sang tên khác rồi dọn tệp. Dùng `resolve` để nối đường dẫn thay cho tự chèn dấu `/` hoặc `\`.

**Đạt khi:** chọn được API đọc/ghi văn bản **nhỏ** và biết một lần kiểm tra `Files.exists` trước đó không bảo đảm thao tác kế tiếp sẽ thành công: tệp có thể đổi giữa hai thao tác.

### Chặng 2 — Byte streams và character streams

**Học:** `InputStream`/`OutputStream`, `Reader`/`Writer`, `InputStreamReader`/`OutputStreamWriter`, `BufferedInputStream`/`BufferedOutputStream`, `BufferedReader`/`BufferedWriter`.

**Thực hành:** sao chép ảnh theo từng khối byte bằng `InputStream.transferTo(OutputStream)` hoặc vòng lặp `read(byte[])`/`write(...)`; đọc văn bản từng dòng bằng `BufferedReader`; so sánh với đọc toàn bộ chuỗi qua `Files.readString`.

**Đạt khi:** biết chọn byte cho dữ liệu nhị phân, character cho văn bản, nêu rõ charset tại ranh giới chuyển byte ↔ text, và hiểu buffering giảm số lần gọi I/O trong nhiều tình huống.

### Chặng 3 — Quản lý bộ nhớ, lỗi và độ tin cậy

**Học:** xử lý dữ liệu lớn theo luồng; `try-with-resources`; flush và close; ngoại lệ khi mở/đọc/ghi; tệp không tồn tại, quyền truy cập, đường dẫn sai, dữ liệu chưa hoàn chỉnh.

**Thực hành:** xử lý tệp log lớn theo từng dòng mà không gọi `Files.readAllLines` hoặc `Files.readAllBytes`; giả lập lỗi bằng cách trỏ tới tệp không tồn tại; kiểm tra tài nguyên được đóng.

**Đạt khi:** giải thích được `flush()` đẩy dữ liệu từ lớp đệm xuống tầng dưới nhưng không tự đảm bảo dữ liệu đã được lưu bền vững trên thiết bị; `close()` giải phóng tài nguyên và có thể phát sinh lỗi.

### Chặng 4 — File tree và metadata

**Học:** `Files.list(...)`, `Files.walk(...)`, `Files.walkFileTree(...)`, `BasicFileAttributes`, symbolic link; phân biệt lọc tên tệp với đọc nội dung tệp.

**Thực hành:** liệt kê tệp theo hậu tố và tính tổng kích thước trong một thư mục bài tập; dùng `try-with-resources` với stream trả về bởi `Files.list`/`Files.walk`.

**Đạt khi:** biết giới hạn phạm vi quét, xử lý lỗi quyền truy cập và tránh vô tình đi theo symlink ngoài thư mục dự kiến.

### Chặng 5 — NIO buffers và channels

**Học:** `ByteBuffer` với `position`, `limit`, `capacity`, `flip()`, `clear()`; `FileChannel` để đọc/ghi theo vị trí; phân biệt blocking I/O thường gặp với non-blocking I/O trong các trường hợp kênh hỗ trợ.

**Thực hành:** đọc tệp nhị phân bằng `FileChannel` và `ByteBuffer`; ghi hoặc đọc một vùng xác định của tệp; so sánh với stream ở chặng 2.

**Đạt khi:** giải thích được vì sao phải `flip()` trước khi đọc dữ liệu vừa ghi vào buffer, và không mặc định mọi `Channel` đều non-blocking.

### Chặng 6 — Áp dụng theo loại dữ liệu và độ phức tạp

**Học theo nhu cầu:** `DataInputStream`/`DataOutputStream` cho primitive theo định dạng xác định; `ObjectInputStream`/`ObjectOutputStream` ở mức nhận biết rủi ro, không coi Java serialization là định dạng trao đổi mặc định; socket I/O, selector và asynchronous channel chỉ khi bài toán cần.

**Thực hành:** viết một định dạng bản ghi nhị phân nhỏ có mô tả thứ tự trường, phiên bản và charset; kiểm thử đọc lại. Với dữ liệu trao đổi hệ thống, cân nhắc định dạng có schema/ràng buộc rõ thay vì deserialization Java từ nguồn không đáng tin.

**Đạt khi:** đưa ra lý do kỹ thuật cho API đã chọn theo kiểu dữ liệu, kích thước, nhu cầu hiệu năng, độ tin cậy và khả năng bảo trì.

## 5. Ví dụ Java 17 hoàn chỉnh: ghi và đọc UTF-8 an toàn

Ví dụ dưới đây minh họa **chặng 1–3**: tạo tệp tạm, ghi UTF-8, đọc từng dòng, đóng reader tự động và xóa tệp trong `finally`. Tên tệp tạm được hệ thống tạo, nên chương trình không phụ thuộc thư mục chạy.

Lưu tệp với tên **`IoRoadmapDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class IoRoadmapDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("io-roadmap-", ".txt");

        try {
            Files.writeString(file, "Xin chào\nJava I/O\n", StandardCharsets.UTF_8);

            try (BufferedReader reader =
                    Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    System.out.println(++lineNumber + ": " + line);
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+ (lưu mã nguồn ở UTF-8):

```bash
javac -encoding UTF-8 IoRoadmapDemo.java
java IoRoadmapDemo
```

Kết quả stdout chính xác trong lần chạy thành công:

```text
1: Xin chào
2: Java I/O
```

**Vì sao chương trình đúng:** `Files.writeString` chuyển văn bản thành byte UTF-8; `Files.newBufferedReader` chuyển byte UTF-8 trở lại thành dòng ký tự; `readLine()` trả `null` sau dòng cuối; `try-with-resources` đóng reader; `finally` cố gắng xóa tệp tạm kể cả khi đọc có lỗi. `main throws IOException` cho phép lỗi I/O đi ra cho người chạy thấy thay vì nuốt mất lỗi. Trong ứng dụng thực tế, chọn chính sách log/thông báo/khôi phục phù hợp.

**Giới hạn của ví dụ:** đây là tệp nhỏ và ứng dụng một luồng; nó chưa minh họa sao chép nhị phân, xử lý dữ liệu nhiều GB, concurrent writers hay ghi tệp quan trọng theo kiểu atomic replacement.

## 6. Chọn công cụ theo tình huống

| Tình huống | Lựa chọn khởi đầu | Cảnh báo quan trọng |
| --- | --- | --- |
| Đọc/ghi tệp văn bản nhỏ | `Files.readString` / `Files.writeString` với charset | Nội dung lớn có thể tiêu tốn nhiều RAM |
| Đọc văn bản theo từng dòng | `Files.newBufferedReader` hoặc `Files.lines` | Đóng reader/stream; nhớ line ending không được giữ nguyên |
| Sao chép tệp thông thường | `Files.copy` | Chọn chính sách tệp đích có sẵn, xử lý lỗi; không giả định thao tác luôn atomic |
| Sao chép byte giữa hai nguồn/đích | `InputStream` + `OutputStream`, `transferTo` | Đóng cả hai resource; không chuyển nhị phân thành text |
| Duyệt nhiều tệp trong thư mục | `Files.walk` hoặc `walkFileTree` | Giới hạn độ sâu/quyền/symlink; đóng stream |
| Truy cập ngẫu nhiên vùng byte | `FileChannel` và `ByteBuffer` | Hiểu `position`/`limit` và kết quả `read`/`write` có thể từng phần |
| Nhiều kết nối mạng cùng lúc | NIO selector / asynchronous API khi đo đạc chứng minh cần | Nhiều trạng thái và lỗi hơn; không dùng chỉ vì tên “NIO” |

## 7. Thứ tự thực hành và tiêu chí tự kiểm tra

1. **Bài tập văn bản:** ghi tệp `notes.txt` UTF-8 gồm chữ tiếng Việt và emoji; đọc lại; tự kiểm chứng byte và ký tự có độ dài khác nhau.
2. **Bài tập nhị phân:** sao chép ảnh/tệp ZIP bằng byte stream; so kích thước và nội dung byte giữa hai tệp, không dùng `Reader`.
3. **Bài tập file lớn:** đếm số dòng bằng `BufferedReader` hoặc `Files.lines` với tài nguyên được đóng; giải thích lý do không dùng `readAllLines` cho tệp lớn.
4. **Bài tập lỗi:** thử đường dẫn sai và đường dẫn không có quyền truy cập (nếu môi trường cho phép); phân biệt thông báo cho người dùng với log chẩn đoán; bảo đảm không bỏ qua ngoại lệ.
5. **Bài tập tổ chức:** duyệt thư mục con, liệt kê tên/kích thước, xử lý các mục bị xóa trong khi đang duyệt.
6. **Bài tập NIO:** đọc file bằng `FileChannel` và buffer, ghi chú trạng thái buffer trước và sau `flip()`; đối chiếu với API stream đã biết.

Có thể dùng các câu hỏi sau làm checkpoint:

- Vì sao không mở ảnh bằng `BufferedReader` để sao chép?
- `read()` trả `-1` khác gì so với ngoại lệ?
- `readLine()` trả `null` trong trường hợp nào? Nó có giữ `\r\n` không?
- Khi nào `Files.readString` tiện lợi, và khi nào nên đọc từng dòng/từng khối?
- `try-with-resources` đóng những gì, còn `Path` có cần đóng không?
- Vì sao gọi `Files.exists(path)` rồi mới ghi vẫn có thể gặp lỗi?
- Khi sử dụng `Files.lines` hoặc `Files.walk`, trách nhiệm đóng tài nguyên thuộc về ai?
- Trong một ví dụ ByteBuffer ghi rồi đọc, `flip()` thay đổi trạng thái gì?

Nếu còn lúng túng với hai câu đầu liên quan đến **byte/text** và **EOF/ngoại lệ**, nên củng cố chặng 1–2 trước khi học channel hoặc selector.

## 8. Lỗi phổ biến cần tránh xuyên suốt lộ trình

- **Dùng charset ngầm định:** có thể cho kết quả khác trên các môi trường hoặc làm hỏng dữ liệu; ghi rõ UTF-8 tại ranh giới chuyển đổi khi định dạng yêu cầu.
- **Dùng `readAllBytes`/`readAllLines` cho mọi tệp:** dữ liệu lớn có thể gây áp lực heap; ưu tiên đọc theo khối/dòng nếu không cần giữ toàn bộ.
- **Quên đóng tài nguyên:** gây rò rỉ file descriptor hoặc giữ khóa tài nguyên; dùng `try-with-resources` theo vòng đời thật.
- **Không kiểm tra số byte thực đọc:** `read(byte[])` có thể đọc ít hơn độ dài mảng, ngay cả trước EOF; chỉ xử lý số byte phương thức thực trả về.
- **Giả định một lần ghi luôn hoàn tất mọi byte với NIO channel:** kiểm tra giá trị trả về của `write` và lặp nếu giao thức/thao tác yêu cầu ghi đủ.
- **Nuốt `IOException`:** làm mất nguyên nhân và khiến đầu ra tưởng đã thành công; báo lỗi đúng tầng, có thông tin ngữ cảnh và giữ nguyên cause khi bọc ngoại lệ.
- **Đưa đường dẫn từ người dùng vào thao tác nhạy cảm mà không kiểm soát:** chuẩn hóa, xác định thư mục gốc cho phép, xét symlink và kiểm soát quyền truy cập; `normalize()` chỉ xử lý cú pháp, không tự ngăn path traversal.
- **Coi `flush` hoặc `close` là đồng nghĩa ghi bền vững tuyệt đối:** yêu cầu độ bền cao cần xem đặc tính filesystem, đồng bộ dữ liệu và chiến lược thay thế/khôi phục.
- **Học selector/async quá sớm:** làm mờ lỗi cơ bản về charset, EOF và cleanup; chỉ tiến lên khi các bài tập nền tảng ổn định.

## 9. Khi nào sẵn sàng học chủ đề I/O kế tiếp?

Bạn có thể tiến từ phần tổng quan sang các bài chuyên sâu khi tự viết được chương trình Java 17 đọc/ghi UTF-8, sao chép tệp nhị phân mà không làm biến đổi dữ liệu, xử lý EOF chính xác và đóng tài nguyên dù có ngoại lệ. Từ nền tảng đó, học `Path`/`Files` → luồng byte/ký tự và buffering → xử lý dữ liệu lớn/lỗi → duyệt file tree → `ByteBuffer`/`Channel` → networking/serialization/async theo yêu cầu bài toán sẽ giúp mỗi API mới gắn với một vấn đề cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="io-overview-008">Phạm vi Java Core và khác biệt theo phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi Java Core I/O và khác biệt theo phiên bản JDK

> **Ngữ cảnh:** Java Core – I/O, thuộc nhóm **Tổng quan I/O**. Bài học xác định phần I/O nào thuộc Java SE, cách chọn API theo loại dữ liệu và những khác biệt cần kiểm tra khi chạy hoặc biên dịch với các phiên bản JDK. Những mốc bên dưới là thay đổi tiêu biểu, không phải danh mục đầy đủ mọi thay đổi của từng bản JDK.

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

- Nhận diện các gói Java SE cung cấp I/O, phân biệt chúng với framework hay thư viện bên ngoài.
- Chọn giữa luồng **byte**, luồng **ký tự**, thao tác **tệp/đường dẫn**, **channel/buffer** và cơ chế blocking/asynchronous.
- Nhận biết API có sẵn ở Java 8, mới xuất hiện từ Java 9/10/11/12, cùng thay đổi **charset mặc định** từ JDK 18.
- Viết mã đọc/ghi tệp có charset tường minh, đóng tài nguyên đúng và kiểm tra biên dịch theo phiên bản Java mục tiêu.
- Hiểu sự khác nhau giữa API được phép dùng lúc biên dịch, môi trường runtime và tác động của virtual threads trong JDK 21.

## 2. Phạm vi Java Core I/O là gì?

**I/O (Input/Output)** là hoạt động nhận dữ liệu hoặc xuất dữ liệu ra ngoài phần tính toán: tệp, bộ nhớ, thiết bị chuẩn, kết nối mạng và hệ thống tệp. Ở đây **Java Core** chỉ API chuẩn trong Java SE do JDK cung cấp; bạn không cần thêm dependency Maven/Gradle để dùng các lớp sau.

| API chuẩn | Công dụng chính | Ví dụ |
| --- | --- | --- |
| `java.io` | Byte stream, character stream, buffering, dữ liệu nguyên thủy, serialization | `InputStream`, `OutputStream`, `Reader`, `Writer`, `BufferedReader`, `DataInputStream` |
| `java.nio` | Buffer và những cấu trúc dữ liệu hỗ trợ I/O | `ByteBuffer` |
| `java.nio.channels` | Channel, truy cập theo vị trí, I/O không chặn cho những channel hỗ trợ | `FileChannel`, `SocketChannel`, `Selector` |
| `java.nio.file`, `java.nio.file.attribute` | Đường dẫn, đọc/ghi tệp, quản lý thư mục, file metadata | `Path`, `Files`, `FileVisitor`, `BasicFileAttributes` |
| `java.nio.charset` | Quy tắc mã hóa byte ↔ ký tự | `Charset`, `StandardCharsets`, `CharsetDecoder` |
| `java.util.zip` | Nén/giải nén trên các luồng | `ZipInputStream`, `GZIPOutputStream` |

Các luồng chuẩn `System.in`, `System.out` và `System.err` cũng thuộc Java SE; `System.out` là `PrintStream`. I/O mạng có API chuẩn `java.net`, nhưng phân tích socket/HTTP chi tiết thuộc chủ đề networking. JDBC, servlet container, Spring Web và những lớp tiện ích của framework không phải là API Java Core I/O chỉ vì chúng cũng đọc/ghi dữ liệu.

### 2.1. Byte stream, character stream và file API

- **Byte stream** (`InputStream`/`OutputStream`) làm việc với byte thô: ảnh, PDF, ZIP, protocol nhị phân. `InputStream.read()` trả giá trị 0–255 hoặc `-1` lúc hết dữ liệu; `read(byte[])` có thể trả số byte ít hơn độ dài buffer.
- **Character stream** (`Reader`/`Writer`) làm việc với ký tự. Khi kết nối với tệp hay socket, byte phải được giải mã theo charset; một ký tự Unicode có thể chiếm nhiều byte UTF-8. Ví dụ `InputStreamReader` nối một byte stream với reader.
- **`Path` và `Files`** quản lý đường dẫn và filesystem: tạo, đọc, ghi, di chuyển, kiểm tra thuộc tính tệp. `Path.of("data.txt")` chỉ tạo đường dẫn tương đối, không tạo tệp và không kiểm tra tệp tồn tại. `java.io.File` là API cũ, có thể chuyển sang `Path` bằng `file.toPath()`.
- **Channel/Buffer** hỗ trợ dữ liệu theo khối, truy cập vị trí bất kỳ, non-blocking với channel thích hợp và các lựa chọn nâng cao. Không phải lớp nào trong gói `java.nio` cũng non-blocking: `Files.readAllBytes` vẫn chặn trong khi đọc.
- **NIO.2** gồm API `Path`, `Files`, `WatchService`, `AsynchronousFileChannel` có từ **Java 7**; không cần chờ Java 11 hay 21 mới có.

### 2.2. Nguyên tắc chung không phụ thuộc phiên bản JDK

I/O có thể thất bại do tệp không tồn tại, thiếu quyền, hỏng thiết bị, thiếu chỗ lưu, đường dẫn không hợp lệ hay lỗi mạng; xử lý `IOException` theo chính sách ứng dụng. Đóng `InputStream`, `Reader`, `Writer`, `FileChannel` và stream do `Files.list`/`Files.walk`/`Files.lines` trả về khi xong việc; thường dùng **try-with-resources**. Với dữ liệu lớn, tránh nạp toàn bộ vào RAM bằng `readAllBytes` hoặc `readString`; xử lý từng dòng hoặc dùng buffer có kích thước cố định.

Phân biệt **blocking I/O** (lời gọi có thể chờ dữ liệu) với non-blocking/asynchronous I/O (có cơ chế theo dõi tiến triển mà không chặn kiểu cũ). Tên gói `nio` không đủ để quyết định cơ chế thực thi.

## 3. Bảng mốc phiên bản JDK quan trọng

| Phiên bản | API/thay đổi tiêu biểu | Ý nghĩa |
| --- | --- | --- |
| **Java 7** | NIO.2: `Path`, `Files`, `WatchService`, `AsynchronousFileChannel`; try-with-resources | Nền tảng file API hiện đại và cơ chế tự đóng tài nguyên đã có từ trước Java 8. |
| **Java 8** | `Files.lines`, `Files.list`, `Files.walk`, `Files.find` | Phối hợp file/directory API với Stream API; stream giữ tài nguyên phải đóng đúng cách. |
| **Java 9** | `InputStream.readAllBytes()`, `readNBytes(byte[],int,int)`, `transferTo(OutputStream)`; `javac --release` | Thao tác stream thuận tiện hơn, kiểm tra API mục tiêu khi biên dịch. |
| **Java 10** | `Reader.transferTo(Writer)` | Sao chép nội dung ký tự từ reader sang writer gọn hơn. |
| **Java 11 (LTS)** | `Path.of(...)`, `Files.readString`, `Files.writeString`, `InputStream.readNBytes(int)`, `InputStream.nullInputStream()` | API thao tác chuỗi/tệp/byte đơn giản hơn nhưng không có trên Java 8. |
| **Java 12** | `Files.mismatch(Path,Path)`, `InputStream.skipNBytes(long)` | Tìm vị trí byte đầu tiên khác giữa hai tệp; bỏ qua đúng lượng byte nếu có đủ. |
| **Java 17 (LTS)** | Baseline triển khai phổ biến, kế thừa những API ở trên | Không được suy ra rằng mọi API I/O hay charset mặc định đều mới đổi ở Java 17. |
| **Java 18** | **JEP 400: UTF-8 by Default** | Các Java SE API dựa vào default charset dùng UTF-8 theo cấu hình mặc định; có thể thay đổi hành vi so với JDK 8/11/17. |
| **Java 21 (LTS)** | **Virtual threads** chính thức theo JEP 444 | Có lựa chọn mở rộng số tác vụ chờ I/O đồng thời; không thay thế toàn bộ `Files`/`InputStream`. |

**Hai cách kiểm tra khác nhau:** Nếu dự án nhắm Java 8 thì một lời gọi chỉ có từ Java 11 **không hợp lệ ở bước biên dịch với API Java 8**. Ngược lại, cùng source đã biên dịch vẫn có thể đổi hành vi khi nâng **runtime**, ví dụ lúc dùng charset mặc định.

## 4. Charset và khác biệt trước/sau JDK 18

Dữ liệu trong tệp gồm **byte**, còn `String`/`Reader` biểu diễn **ký tự**. Khi chuyển đổi, chương trình phải dùng charset như UTF-8. Nếu người ghi mã hóa theo UTF-8 nhưng người đọc giải mã theo charset khác, tiếng Việt có thể lỗi hoặc thao tác đọc báo dữ liệu không hợp lệ.

- **JDK 17 trở về trước:** `Charset.defaultCharset()` thường phụ thuộc hệ điều hành/locale/cấu hình; cùng một chương trình có thể chạy khác nhau trên các máy.
- **Từ JDK 18:** JEP 400 quy định **UTF-8 là charset mặc định của Java SE API liên quan theo cấu hình mặc định**. Không có nghĩa mọi byte bên ngoài đều UTF-8, hay nâng runtime sẽ tự chuyển mã tệp cũ.
- **Ngoại lệ rất quan trọng:** Một số overload `Files` đã **quy định UTF-8 độc lập với charset mặc định** từ trước JDK 18, chẳng hạn `Files.newBufferedReader(path)`, `Files.newBufferedWriter(path)`; từ Java 11 có `Files.readString(path)`/`writeString(path, text)` cũng dùng UTF-8. Trong khi đó `new InputStreamReader(input)`, `new String(bytes)` và `text.getBytes()` (không đối số charset) phụ thuộc vào default charset.
- Muốn hành vi ổn định giữa JDK/OS, dùng overload có `StandardCharsets.UTF_8`: `new InputStreamReader(input, StandardCharsets.UTF_8)`, `new String(bytes, StandardCharsets.UTF_8)`, `text.getBytes(StandardCharsets.UTF_8)` và `Files.readString(path, StandardCharsets.UTF_8)`.
- Với kho dữ liệu lịch sử dùng charset khác, phải xác định **charset thật** trước khi di chuyển; ép UTF-8 khi đọc dữ liệu cũ không làm byte tự đổi thành UTF-8.

**Ví dụ không nên phụ thuộc default charset:** đọc byte từ tệp cũ rồi gọi `new String(bytes)`. Kết quả có thể khác giữa Java 8/17 và Java 18/21. Cách đúng là biết charset ghi của tệp và truyền charset đó khi decode.

## 5. Ví dụ Java 8: API tệp và UTF-8 ổn định

Tạo **`IoJava8.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class IoJava8 {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("io-java8-", ".txt");
        try {
            byte[] data = "Xin chào Java".getBytes(StandardCharsets.UTF_8);
            Files.write(file, data);

            byte[] restored = Files.readAllBytes(file);
            System.out.println(new String(restored, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac IoJava8.java
java IoJava8
~~~

Stdout chính xác:

~~~text
Xin chào Java
~~~

`Files.createTempFile` tạo một tệp tên ngẫu nhiên và `finally` cố xóa tệp sau khi dùng, kể cả có lỗi. `Files.write` ghi byte nên không liên quan charset mặc định; charset được chọn khi chuyển `String` sang byte và ngược lại. Ví dụ này chạy được trên **Java 8** và JDK mới hơn, vì chỉ dùng các API có sẵn từ Java 7–8. Dùng `readAllBytes` ở đây vì nội dung nhỏ; không lấy nó làm mẫu đọc tệp nhiều gigabyte.

## 6. Ví dụ Java 11+: `Files.readString` và `Files.writeString`

Tạo **`IoJava11.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class IoJava11 {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("io-java11-", ".txt");
        try {
            Files.writeString(file, "Dữ liệu UTF-8\n", StandardCharsets.UTF_8);
            String text = Files.readString(file, StandardCharsets.UTF_8);
            System.out.print(text);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

~~~bash
javac IoJava11.java
java IoJava11
~~~

Stdout:

~~~text
Dữ liệu UTF-8
~~~

`Files.writeString/readString` có từ **JDK 11**. Không dùng đoạn code này khi dự án vẫn target Java 8. `Files.readString` đọc toàn bộ file trong một lần, có thể tốn bộ nhớ nếu file lớn; nó quản lý việc mở/đóng file nội bộ. `Files.writeString` nếu không truyền `OpenOption` sẽ tạo file nếu cần và ghi đè nội dung cũ; cần chỉ rõ yêu cầu “append” hay “overwrite” trong ứng dụng thật.

Với tệp văn bản lớn, **Java 8 cũng có** `Files.newBufferedReader` để đọc từng dòng:

~~~java
// Minh họa đặt bên trong một phương thức có thể ném IOException.
// path là biến Path chỉ tới một tệp văn bản UTF-8.
try (java.io.BufferedReader reader =
         java.nio.file.Files.newBufferedReader(
             path, java.nio.charset.StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý dòng tại đây; không gom toàn bộ nội dung vào List/String.
    }
}
~~~

Đây là **fragment minh họa**, chưa phải chương trình độc lập vì `path` và phần xử lý dòng do người gọi cung cấp. `readLine` loại bỏ ký tự kết thúc dòng, cần lưu ý nếu phải giữ nguyên định dạng tệp.

## 7. Sao chép luồng byte: Java 8 và Java 9

Để sao chép nội dung nhị phân, đừng biến nó thành `String`. Với Java 8:

~~~java
// in là InputStream và out là OutputStream đã mở.
byte[] buffer = new byte[8192];
int n;
while ((n = in.read(buffer)) != -1) {
    out.write(buffer, 0, n);
}
~~~

Giá trị `n` là **số byte thực đọc**. Viết `out.write(buffer)` thay vì `out.write(buffer,0,n)` có thể ghi lại byte thừa từ lần đọc trước, đặc biệt ở lần cuối. Caller chịu trách nhiệm đóng `in` và `out` đúng cách.

Từ Java 9, có thể rút ngắn:

~~~java
// in: InputStream, out: OutputStream (API từ Java 9).
long count = in.transferTo(out);
~~~

`InputStream.transferTo` sao chép toàn bộ byte còn lại và trả số byte đã chuyển, **không tự đóng** hai stream. `InputStream.readAllBytes()` cũng từ Java 9 nhưng đọc toàn bộ vào RAM. `InputStream.readNBytes(byte[],off,len)` từ Java 9 cố đọc đến đủ số lượng yêu cầu hoặc EOF; khác với `read(byte[])` có thể trả ít hơn dù chưa EOF. Những lựa chọn này không loại bỏ nhu cầu xử lý `IOException`.

## 8. Chọn API theo công việc, không chỉ theo phiên bản

| Công việc | API nên cân nhắc | Lưu ý |
| --- | --- | --- |
| Đọc văn bản nhỏ, JDK 11+ | `Files.readString(path, UTF_8)` | Dễ hiểu, tránh phụ thuộc charset hệ thống; nạp cả tệp vào RAM. |
| Ghi văn bản nhỏ, JDK 11+ | `Files.writeString(path, text, UTF_8)` | Quy định rõ tạo tệp, overwrite, append bằng `OpenOption` khi cần. |
| Đọc văn bản lớn, kể cả Java 8 | `Files.newBufferedReader(path, UTF_8)` | Dùng try-with-resources, xử lý từng dòng. |
| Đọc/ghi ảnh, ZIP, nhị phân | `InputStream`/`OutputStream` hoặc `Files.newInputStream`/`newOutputStream` | Sao chép byte nguyên vẹn, không decode làm text. |
| Liệt kê/duyệt thư mục | `Files.list`, `Files.walk` hoặc `Files.walkFileTree` | Xử lý quyền, symlink, lỗi; đóng stream khi cần. |
| Vị trí bất kỳ trong tệp | `FileChannel` hoặc `RandomAccessFile` | Quản lý vị trí/buffer; không đồng nghĩa với async. |
| Hàng nghìn socket đang chờ | Channel/selector hoặc mô hình thread phù hợp | Là thiết kế networking/concurrency, cần benchmark workload thật. |
| Triển khai Java 8 | `Files`/`Path`/buffered streams API có trước Java 9 | Không vô tình dùng `readString` hoặc `transferTo` mới. |

`Files.lines(path)` trả về một `Stream<String>` có thể đọc theo tiến trình và phải đóng để giải phóng tài nguyên; `Files.readAllLines(path)` đọc toàn bộ dòng vào `List<String>`. Stream API **không làm I/O tự động non-blocking**. Cũng không nên coi `Files.readAllBytes` là phương pháp tối ưu chỉ vì hàm ngắn.

## 9. API compatibility và runtime compatibility

### 9.1. Biên dịch theo API mục tiêu

Nếu phân phối ứng dụng chạy trên Java 8, kiểm tra không dùng method mới hơn. Trên `javac` hỗ trợ `--release` (JDK 9+), biên dịch ví dụ Java 8 như sau:

~~~bash
javac --release 8 IoJava8.java
java IoJava8
~~~

Nếu cố `javac --release 8 IoJava11.java`, biên dịch lỗi vì `Files.readString/writeString` không tồn tại trong API Java 8. `--release` giới hạn level ngôn ngữ, bytecode và Java SE API để build phù hợp target. Nó không thay cho chạy test bằng runtime Java 8 thật, nhất là với permission, OS/file system provider và dữ liệu thực.

### 9.2. Kiểm tra runtime và môi trường

Nâng từ JDK 8/11/17 lên 21 có thể không đụng tới API trong source nhưng vẫn lộ lỗi do **charset mặc định đã đổi ở JDK 18**, đường dẫn tương đối, working directory, quyền trên tệp, symbolic link và tương tác với file system. Cần thử cả dữ liệu tiếng Việt, tên tệp Unicode, empty file, file thiếu/quá lớn và tình huống thất bại khi đọc/ghi.

Các lệnh định danh môi trường:

~~~bash
java -version
javac -version
~~~

Trong Gradle/Maven, đừng nhầm **JDK dùng để chạy build tool**, **JDK/toolchain biên dịch**, **release/API đích** và **runtime production**. Chúng có thể khác nhau. Thiết lập cụ thể phải tương ứng JDK mục tiêu, rồi chạy test trong môi trường triển khai thực tế.

## 10. Virtual threads trong Java 21 ảnh hưởng gì tới I/O?

Java 21 chính thức hóa **virtual threads** (JEP 444), giúp ứng dụng có nhiều tác vụ đồng thời đang chờ I/O dùng tài nguyên thread hiệu quả hơn trong các tình huống được runtime hỗ trợ. Nhưng điều này **không làm tệp hay mạng nhanh hơn**, không biến `Files.readString` thành API trả `Future` và không bắt buộc thay toàn bộ code cũ.

Một virtual thread có thể tạm nhường carrier thread khi blocking I/O được runtime hỗ trợ; vẫn phải xem xét thao tác trong `synchronized` hoặc native call, vì ở JDK 21 có tình huống **pinning** làm carrier bị giữ trong lúc chờ. Các lựa chọn **NIO selector**, **AsynchronousFileChannel** và **virtual threads** phục vụ mô hình đồng thời khác nhau, lựa chọn theo workload. Với bài học tổng quan này, trọng tâm vẫn là chọn loại dữ liệu, charset, xử lý lỗi và đóng tài nguyên; threading có bài chuyên sâu riêng.

## 11. Lỗi phổ biến

1. **“NIO luôn non-blocking.”** Sai: `Files.readAllBytes` có thể blocking. Xem hợp đồng method/channel cụ thể.
2. **“Một lần `read(buffer)` nhận đủ buffer.”** Sai: dùng số byte trả về; `-1` là EOF.
3. **“Dùng Reader sao chép ZIP cũng được.”** Sai: decode text có thể làm thay đổi byte gốc.
4. **“Charset hệ thống luôn là UTF-8.”** Không đúng với mọi JDK/cấu hình; chọn charset tường minh.
5. **“Nâng lên JDK 21 sẽ đọc đúng tệp mã hóa cũ.”** Không: cần biết encoding thực tế của dữ liệu lưu.
6. **“Files.lines/list/walk không cần đóng.”** Các stream này có thể giữ tài nguyên hệ thống; dùng try-with-resources.
7. **“readAllBytes thích hợp mọi kích thước tệp.”** Có nguy cơ thiếu heap; dùng buffer/dòng khi dữ liệu lớn.
8. **“Method có mặt trên máy JDK 21 tức chạy được trên Java 8.”** Kiểm tra `--release 8` và runtime mục tiêu.
9. **“Ghi string sẽ tự append.”** `Files.writeString` mặc định ghi đè nội dung; cần xác định open options.
10. **“Path.of tạo tệp ngay.”** Nó chỉ tạo đối tượng đường dẫn. Path tương đối còn phụ thuộc working directory.
11. **“I/O thất bại luôn không để lại dữ liệu.”** Có thể đã đọc/ghi một phần trước lỗi; xác định chiến lược cleanup, backup, ghi tạm + replace khi cần nguyên tử.

## 12. Tự kiểm tra

1. Nên dùng `InputStream` hay `Reader` để copy ảnh PNG? Vì sao?
2. Vì sao project target Java 8 không biên dịch được `Files.readString`?
3. Vòng lặp `read(buffer)` cần ghi bao nhiêu byte mỗi lần?
4. JDK nào thay đổi default charset sang UTF-8, và điều đó có tự giải quyết dữ liệu lưu theo charset cũ không?
5. `Files.lines` và `Files.readAllLines` khác nhau ở thời điểm nạp dữ liệu, bộ nhớ và quản lý tài nguyên thế nào?

**Gợi ý đáp án:** (1) byte stream để giữ nguyên byte; (2) method bắt đầu từ Java 11; (3) đúng số byte `n` trả về; (4) JDK 18, không tự chuyển mã tệp; (5) lazy stream cần đóng so với `List` chứa toàn bộ dòng.

## Tài liệu chuẩn để đối chiếu theo JDK

- [Oracle Java SE 11: InputStream](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/io/InputStream.html) — kiểm tra mục “Since” cho `readAllBytes`, `readNBytes`, `transferTo`.
- [Oracle Java SE 11: Files](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/nio/file/Files.html) — overload đọc/ghi chuỗi và UTF-8.
- [Oracle Java SE 21: Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html) — hợp đồng file API trên JDK hiện đại.
- [OpenJDK JEP 400 – UTF-8 by Default](https://openjdk.org/jeps/400) — thay đổi từ JDK 18.
- [OpenJDK JEP 444 – Virtual Threads](https://openjdk.org/jeps/444) — phiên bản JDK 21 và mối quan hệ với blocking I/O.

**Quy tắc thực hành:** lựa chọn API dựa vào **loại dữ liệu**, **kích thước**, **charset**, **cơ chế đồng thời** và **JDK tối thiểu**. Khi nâng JDK, kiểm tra cả API lúc biên dịch lẫn hành vi runtime; nguyên tắc xử lý lỗi và đóng tài nguyên vẫn luôn cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)
