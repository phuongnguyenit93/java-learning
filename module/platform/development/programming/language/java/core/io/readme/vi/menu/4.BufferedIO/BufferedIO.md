# Lớp bọc, bộ đệm và hành vi đẩy dữ liệu (flush)

Các chương trước đã có các luồng đọc/ghi được dữ liệu. Bây giờ có hai câu hỏi thực tế: Java bổ sung hành vi cho một mô hình I/O đã có bằng cách nào, và vì sao nhiều thao tác I/O rất nhỏ thường cần bộ đệm? Câu trả lời chung là **ghép các lớp bọc (wrapper composition)**; đệm dữ liệu là một ứng dụng quan trọng của mô hình đó.

## <a id="wrapper-composition">Ghép lớp bọc I/O để bổ sung hành vi</a>

Một **lớp bọc (wrapper)** nhận một stream, reader hoặc writer khác làm lớp bên dưới, bổ sung một khả năng rồi chuyển tiếp việc đọc/ghi xuống lớp đó. Nhờ vậy Java không cần tạo một lớp riêng cho mọi tổ hợp như “đọc tệp + giải mã UTF-8 + đọc theo dòng + có bộ đệm”.

```text
nguồn byte
    ↓ FileInputStream
    ↓ InputStreamReader   — chuyển byte thành ký tự
    ↓ BufferedReader      — thêm bộ đệm + readLine()
ứng dụng
```

Không phải lớp bọc nào cũng dùng để đệm dữ liệu. `DataInputStream` bổ sung cách đọc các giá trị nguyên thủy có cấu trúc; `InputStreamReader` bổ sung bước giải mã charset; `BufferedInputStream` hoặc `BufferedReader` mới bổ sung bộ đệm. Khi đọc một chuỗi lớp bọc, hãy hỏi **mỗi lớp thêm hành vi gì**, **`close()` được lan truyền xuống chuỗi như thế nào**, và tách riêng câu hỏi **phạm vi ứng dụng nào thật sự sở hữu trách nhiệm đóng tài nguyên bên dưới**.

## <a id="buffering-purpose">Vì sao cần bộ đệm?</a>

Bộ đệm là một vùng nhớ tạm nằm giữa mã ứng dụng và tài nguyên/tầng I/O bên dưới. Lớp bọc có bộ đệm gom nhiều thao tác nhỏ trước khi chuyển dữ liệu xuống đối tượng được bọc.

```text
nhiều lần đọc nhỏ                  ít lần đọc lớn hơn
ứng dụng <── bộ đệm <───────────────────────── nguồn

nhiều lần ghi nhỏ                  ít lần ghi lớn hơn
ứng dụng ──> bộ đệm ─────────────────────────> đích
```

Khi đọc, lớp bọc có thể lấy một khối dữ liệu từ luồng bên dưới rồi phục vụ nhiều lời gọi nhỏ từ bộ nhớ; việc lấy khối tiếp theo vào bộ đệm thường được gọi là **nạp lại (refill)**. Khi ghi, lớp bọc có thể giữ nhiều phần dữ liệu nhỏ trong bộ nhớ rồi đẩy xuống dưới theo khối.

Lợi ích chính thường là giảm số lần đi qua **ranh giới** phải gọi xuống tầng I/O hoặc hệ điều hành có chi phí cao hơn. Đệm dữ liệu không làm thay đổi nội dung và cũng không tự biến mọi thuật toán thành nhanh hơn; hiệu quả phụ thuộc nguồn/đích, kích thước thao tác và việc tầng bên dưới đã có bộ đệm hiệu quả hay chưa.

Ví dụ, ghi từng byte trực tiếp:

```java
for (byte value : data) {
    out.write(value);
}
```

nếu `out` là `BufferedOutputStream`, các lần `write` nhỏ được gom trước khi chuyển xuống luồng đầu ra bên dưới.

## <a id="character-buffering">Dùng bộ đệm cho dữ liệu ký tự</a>

Đọc từng `char` một qua tầng bên dưới có thể tạo rất nhiều thao tác nhỏ. I/O văn bản thường ghép luồng ký tự với:

- `BufferedReader` để đọc qua bộ đệm và có `readLine()`;
- `BufferedWriter` để gom nhiều lần ghi ký tự nhỏ trước khi chuyển tiếp xuống dưới.

Ví dụ:

```java
java.nio.file.Path temp = java.nio.file.Files.createTempFile("lines-", ".txt");

try (java.io.BufferedWriter writer =
         new java.io.BufferedWriter(
             new java.io.OutputStreamWriter(
                 new java.io.FileOutputStream(temp.toFile()),
                 java.nio.charset.StandardCharsets.UTF_8))) {
    writer.write("dòng 1");
    writer.newLine();
    writer.write("dòng 2");
}

try (java.io.BufferedReader reader =
         new java.io.BufferedReader(
             new java.io.InputStreamReader(
                 new java.io.FileInputStream(temp.toFile()),
                 java.nio.charset.StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}

java.nio.file.Files.deleteIfExists(temp);
```

`readLine()` bỏ ký tự kết thúc dòng khỏi văn bản trả về và trả `null` khi EOF. `BufferedWriter.newLine()` ghi ký tự kết thúc dòng phù hợp với nền tảng khi đó là định dạng mà ứng dụng mong muốn.

Đệm dữ liệu không thay đổi cách mã hóa; cầu nối charset vẫn chịu trách nhiệm mã hóa và giải mã. Phần tiếp theo tập trung vào thời điểm dữ liệu đang nằm trong bộ đệm đầu ra cần được đẩy xuống tầng bên dưới.

## <a id="flush-semantics">flush() có ý nghĩa gì?</a>

Với đầu ra có bộ đệm, dữ liệu ứng dụng vừa ghi có thể vẫn chỉ nằm trong bộ nhớ của lớp bọc. `flush()` yêu cầu stream hoặc writer đẩy dữ liệu đang chờ xuống **tầng bên dưới**.

```text
ứng dụng
    ↓ write
bộ đệm
    ↓ flush
stream/writer bên dưới
    ↓
hệ điều hành / thiết bị / đầu bên kia
```

`flush()` quan trọng khi bên nhận cần thấy dữ liệu **trước khi luồng bị đóng**, ví dụ:

- giao thức yêu cầu/phản hồi đang chờ thông điệp hiện tại;
- chương trình tương tác cần hiển thị lời nhắc ngay;
- một `Writer` sống lâu nhưng một nhóm dữ liệu có ý nghĩa đã hoàn thành.

Ví dụ:

```java
try (java.io.BufferedWriter writer =
         new java.io.BufferedWriter(
             new java.io.OutputStreamWriter(
                 socketOutputStream,
                 java.nio.charset.StandardCharsets.UTF_8))) {
    writer.write("PING");
    writer.newLine();
    writer.flush(); // đẩy phần đang chờ xuống tầng dưới ngay lúc này
}
```

`socketOutputStream` trong ví dụ là biến minh họa đại diện cho một luồng đầu ra đã được lấy từ socket; nó không phải tên API JDK đặc biệt.

`flush()` **không đồng nghĩa dữ liệu đã bền vững trên đĩa vật lý**. Nó chỉ đẩy dữ liệu qua các bộ đệm do lớp hiện tại kiểm soát xuống tầng tiếp theo. **Độ bền dữ liệu (durability)** là mức bảo đảm dữ liệu vẫn tồn tại sau các sự cố mà hệ thống quan tâm; với tệp, yêu cầu này liên quan tới hệ thống tệp và các cơ chế sâu hơn như `FileDescriptor.sync()` hoặc `FileChannel.force(...)`.

Các lớp bọc đầu ra thông thường thực hiện phần đẩy dữ liệu cần thiết khi `close()`. Với tài nguyên sống ngắn trong `try-with-resources`, gọi `flush()` ngay trước `close()` chỉ theo thói quen thường là dư thừa. Chủ động gọi `flush()` có ý nghĩa khi mã cần tạo một ranh giới để bên nhận quan sát dữ liệu hoặc hoàn tất một bước giao thức trong khi tài nguyên vẫn mở.

## <a id="buffer-size-tradeoff">Đánh đổi kích thước bộ đệm</a>

Bộ đệm nhỏ có thể phải nạp lại hoặc đẩy dữ liệu thường xuyên hơn. Bộ đệm lớn có thể giảm số lần gọi xuống dưới, nhưng đổi lại dùng nhiều bộ nhớ hơn cho mỗi luồng đang hoạt động và lợi ích sẽ giảm dần sau một mức nào đó.

Vì vậy không có một kích thước “tốt nhất” cho mọi ứng dụng:

```text
bộ đệm quá nhỏ
→ nhiều lần đi xuống tầng dưới

bộ đệm hợp lý
→ gom thao tác tốt, bộ nhớ vừa phải

bộ đệm rất lớn
→ thêm bộ nhớ, có thể không tăng thông lượng tương ứng
```

**Thông lượng (throughput)** là lượng dữ liệu xử lý được trong một đơn vị thời gian. **Độ trễ (latency)** là thời gian một thao tác cụ thể mất từ lúc bắt đầu tới khi hoàn tất. **Điểm nghẽn (bottleneck)** là tầng đang giới hạn hiệu năng tổng thể. Tăng kích thước bộ đệm chỉ đáng giá nếu phép đo cho thấy nó thực sự cải thiện thông lượng/độ trễ hoặc giảm đúng điểm nghẽn.

Với hàng nghìn kết nối hoặc tệp mở đồng thời, vài chục KB bổ sung cho mỗi lớp bọc có thể trở thành lượng bộ nhớ đáng kể. Với một tác vụ sao chép tệp lớn, bộ đệm lớn hơn một chút có thể hữu ích nếu đo đạc cho thấy điểm nghẽn nằm ở số lần I/O.

Java cung cấp kích thước mặc định cho các lớp bọc có bộ đệm, đủ hợp lý để bắt đầu trong nhiều tình huống. Chỉ nên đổi kích thước khi tải công việc, mục tiêu độ trễ/thông lượng hoặc phép đo hiệu năng cho lý do cụ thể.

## <a id="buffered-wrappers">Các lớp bọc có bộ đệm</a>

Java dùng các đối tượng lớp bọc để giữ quy tắc đọc/ghi quen thuộc nhưng bổ sung hành vi như đệm dữ liệu:

```text
byte:
FileInputStream  → BufferedInputStream
FileOutputStream → BufferedOutputStream

văn bản:
InputStreamReader  → BufferedReader
OutputStreamWriter → BufferedWriter
```

Ví dụ ghi rồi đọc lại UTF-8:

Các lệnh `Path`/`Files` trong ví dụ chỉ dùng để tạo và dọn tệp tạm. Người học chưa cần hiểu API hệ thống tệp ở đoạn này; mục tiêu là quan sát lớp bọc có bộ đệm và chuỗi `Writer`/`Reader`.

```java
java.nio.file.Path temp = java.nio.file.Files.createTempFile("buffered-", ".txt");
String expected = "Xin chào Java ☕";

try (java.io.BufferedWriter writer =
         new java.io.BufferedWriter(
             new java.io.OutputStreamWriter(
                 new java.io.FileOutputStream(temp.toFile()),
                 java.nio.charset.StandardCharsets.UTF_8))) {
    writer.write(expected);
}

String actual;
try (java.io.BufferedReader reader =
         new java.io.BufferedReader(
             new java.io.InputStreamReader(
                 new java.io.FileInputStream(temp.toFile()),
                 java.nio.charset.StandardCharsets.UTF_8))) {
    actual = reader.readLine();
}

System.out.println(expected.equals(actual)); // true
java.nio.file.Files.deleteIfExists(temp);
```

Khi lớp bọc ngoài cùng được đóng, nó đóng chuỗi bên dưới theo quy tắc của các lớp này. Nếu phạm vi hiện tại sở hữu toàn bộ chuỗi, mã thường chỉ đóng lớp bọc ngoài cùng thay vì đóng từng tầng rời rạc; việc bọc một tài nguyên do bên gọi sở hữu không tự động chuyển quyền sở hữu ở mức ứng dụng.

Không nên thêm nhiều lớp bộ đệm chỉ vì “nhiều bộ đệm sẽ nhanh hơn”. Nếu một tầng đã đệm dữ liệu hiệu quả, thêm một bộ đệm khác có thể chỉ tăng bộ nhớ và số lần sao chép. Hãy chọn lớp bọc theo khả năng thực sự cần: đệm byte, đọc văn bản theo dòng, chuyển đổi charset hoặc một hành vi khác.

Sau khi biết cách dữ liệu di chuyển, chương tiếp theo chuyển sang một khái niệm khác: `java.io.File` không phải luồng dữ liệu mà là mô hình cũ để biểu diễn đường dẫn và thao tác với siêu dữ liệu/hệ thống tệp.
