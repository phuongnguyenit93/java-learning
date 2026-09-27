# Buffered I/O

Các chapter trước đã có stream đọc/ghi được dữ liệu. Bây giờ xuất hiện một vấn đề thực tế: nếu ứng dụng tạo rất nhiều thao tác nhỏ xuống nguồn/đích bên dưới, chi phí gọi qua nhiều lớp hoặc chạm tới hệ điều hành có thể lớn hơn chính lượng dữ liệu đang xử lý. Buffering gom các thao tác nhỏ thành những khối lớn hơn.

## <a id="buffering-purpose">Vì sao cần buffering?</a>

Buffer là một vùng nhớ tạm nằm giữa code ứng dụng và tài nguyên/tầng I/O bên dưới. **Wrapper** là object bọc một stream/writer khác để bổ sung hành vi nhưng vẫn chuyển dữ liệu xuống object được bọc.

```text
nhiều read nhỏ                    ít read lớn hơn
application <── buffer <───────────────────────── source

nhiều write nhỏ                   ít write lớn hơn
application ──> buffer ─────────────────────────> sink
```

Khi đọc, wrapper có thể lấy một khối dữ liệu từ stream bên dưới rồi phục vụ nhiều lời gọi nhỏ từ bộ nhớ; lần lấy thêm dữ liệu vào buffer thường được gọi là **refill**. Khi ghi, wrapper có thể giữ nhiều phần dữ liệu nhỏ trong bộ nhớ rồi đẩy xuống dưới theo khối.

Lợi ích chính thường là giảm số lần đi qua **boundary**, tức ranh giới phải gọi xuống tầng I/O/hệ điều hành đắt hơn. Buffering không làm thay đổi nội dung dữ liệu và cũng không tự biến thuật toán thành nhanh hơn trong mọi trường hợp; hiệu quả phụ thuộc nguồn/đích, kích thước thao tác và lớp đã có sẵn buffering bên dưới.

Ví dụ, ghi từng byte trực tiếp:

```java
for (byte value : data) {
    out.write(value);
}
```

nếu `out` là `BufferedOutputStream`, các lần `write` nhỏ được gom trước khi chuyển xuống output stream bên dưới.

## <a id="flush-semantics">flush() có ý nghĩa gì?</a>

Với output có buffer, dữ liệu ứng dụng vừa ghi có thể vẫn đang nằm trong bộ nhớ của wrapper. `flush()` yêu cầu stream/writer đẩy dữ liệu đang giữ xuống **tầng bên dưới**.

```text
application
    ↓ write
buffer
    ↓ flush
underlying stream/writer
    ↓
OS / device / peer
```

`flush()` rất quan trọng khi bên nhận cần thấy dữ liệu **trước khi stream bị đóng**, ví dụ:

- giao thức request/response đang chờ message hiện tại;
- chương trình tương tác cần hiển thị prompt ngay;
- writer sống lâu nhưng một nhóm dữ liệu đã hoàn thành logic.

Ví dụ:

```java
try (java.io.BufferedWriter writer =
         new java.io.BufferedWriter(
             new java.io.OutputStreamWriter(
                 socketOutputStream,
                 java.nio.charset.StandardCharsets.UTF_8))) {
    writer.write("PING");
    writer.newLine();
    writer.flush(); // gửi phần đang buffer xuống tầng dưới ngay lúc này
}
```

`socketOutputStream` trong ví dụ là biến minh họa đại diện cho một output stream đã được lấy từ socket; nó không phải tên API JDK đặc biệt.

`flush()` **không đồng nghĩa dữ liệu đã bền vững trên đĩa vật lý**. Nó chỉ chuyển dữ liệu qua các buffer do lớp hiện tại kiểm soát xuống tầng tiếp theo. **Durability (độ bền dữ liệu)** là mức bảo đảm dữ liệu vẫn tồn tại sau các sự cố mà hệ thống quan tâm; với file, yêu cầu này liên quan tới filesystem và các cơ chế sâu hơn như `FileDescriptor.sync()` hoặc `FileChannel.force(...)`, sẽ được nhắc lại ở phần `FileChannel`.

`close()` của các output wrapper thông thường sẽ hoàn tất việc flush cần thiết trước khi đóng tầng bên dưới, nên với resource ngắn hạn trong `try-with-resources`, không cần gọi `flush()` ngay trước `close()` chỉ theo thói quen. Flush chủ động có ý nghĩa khi cần tạo một visibility/protocol boundary trong khi resource vẫn mở.

## <a id="buffer-size-tradeoff">Đánh đổi kích thước buffer</a>

Buffer nhỏ có thể phải refill/flush thường xuyên hơn. Buffer lớn có thể giảm số lần gọi xuống dưới, nhưng đổi lại dùng nhiều bộ nhớ hơn cho mỗi stream đang hoạt động và lợi ích sẽ giảm dần sau một mức nào đó.

Vì vậy không có một kích thước “tốt nhất” cho mọi ứng dụng:

```text
buffer quá nhỏ
→ nhiều lần đi xuống tầng dưới

buffer hợp lý
→ gom thao tác tốt, bộ nhớ vừa phải

buffer rất lớn
→ thêm bộ nhớ, có thể không tăng throughput tương ứng
```

**Throughput** là lượng dữ liệu xử lý được trong một đơn vị thời gian. **Latency** là thời gian một thao tác cụ thể mất từ lúc bắt đầu tới khi hoàn tất. **Bottleneck** là tầng đang giới hạn hiệu năng tổng thể. Tăng buffer chỉ đáng giá nếu phép đo cho thấy nó thực sự cải thiện throughput/latency hoặc giảm bottleneck phù hợp.

Với hàng nghìn kết nối hoặc file mở đồng thời, vài chục KB bổ sung cho mỗi wrapper có thể trở thành lượng memory đáng kể. Với một tác vụ copy file lớn, buffer lớn hơn một chút có thể hữu ích nếu đo đạc cho thấy bottleneck nằm ở số lần I/O.

Java cung cấp default buffer size cho các buffered wrapper, đủ hợp lý để bắt đầu trong nhiều tình huống. Chỉ nên đổi kích thước khi workload, latency/throughput target hoặc benchmark cho lý do cụ thể.

## <a id="buffered-wrappers">Các buffered wrapper</a>

Java dùng kiểu **decorator/wrapper**: bọc object cũ bằng object mới để giữ contract đọc/ghi quen thuộc nhưng bổ sung hành vi như buffering.

```text
byte:
FileInputStream  → BufferedInputStream
FileOutputStream → BufferedOutputStream

text:
InputStreamReader  → BufferedReader
OutputStreamWriter → BufferedWriter
```

Ví dụ round-trip UTF-8:

Các lệnh `Path`/`Files` trong ví dụ chỉ dùng để tạo và dọn file tạm. Người học chưa cần hiểu filesystem API ở đoạn này; mục tiêu là quan sát wrapper buffering và chuỗi `Writer`/`Reader`.

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

Khi wrapper ngoài cùng được đóng, nó đóng chain bên dưới theo contract của các class này. Vì vậy code thường sở hữu và đóng wrapper ngoài cùng thay vì đóng từng tầng rời rạc.

Không nên thêm nhiều lớp buffer chỉ vì “nhiều buffer sẽ nhanh hơn”. Nếu một tầng đã buffering hiệu quả, thêm một buffer khác có thể chỉ tăng memory/copy. Hãy chọn wrapper theo capability thực sự cần: byte buffering, line-oriented text, charset conversion, hoặc một hành vi khác.

Sau khi biết cách dữ liệu di chuyển, chapter tiếp theo chuyển sang một khái niệm khác: `java.io.File` không phải stream dữ liệu mà là abstraction cũ để biểu diễn pathname và thao tác metadata/filesystem.
