# Quản lý tài nguyên an toàn với try-with-resources

File, stream, socket, đối tượng JDBC và nhiều tài nguyên bên ngoài heap cần được **đóng đúng thời điểm**. Bộ thu gom rác (Garbage Collector) quản lý bộ nhớ của đối tượng Java, nhưng không phải là cơ chế đảm bảo giải phóng file descriptor, socket hay database handle đúng lúc.

`try-with-resources` (TWR) biến quyền sở hữu tài nguyên và việc dọn dẹp thành một phần rõ ràng của cú pháp.

## <a id="autocloseable">AutoCloseable</a>

Một tài nguyên dùng trong TWR phải có kiểu triển khai `AutoCloseable`:

```java
public interface AutoCloseable {
    void close() throws Exception;
}
```

Ví dụ:

```java
try (InputStream in = Files.newInputStream(path)) {
    return in.read();
}
```

Luồng cơ bản:

```text
tạo tài nguyên
        ↓
chạy phần thân
        ↓
rời phần thân do chạy xong / return / throw
        ↓
Java gọi close()
        ↓
sau đó luồng điều khiển mới tiếp tục ra ngoài
```

### VÌ SAO KHÔNG CHỜ GARBAGE COLLECTOR?

Một đối tượng stream có thể không còn được tham chiếu tới (unreachable) nhưng tài nguyên hệ điều hành phía dưới vẫn cần được dọn dẹp đúng thời điểm.

Mô hình cần nhớ:

```text
vòng đời bộ nhớ
≠
vòng đời tài nguyên bên ngoài
```

TWR giải quyết **vòng đời/quyền sở hữu tài nguyên**, không phải tối ưu GC.

### Catch/finally nằm ở đâu?

```java
try (InputStream in = Files.newInputStream(path)) {
    use(in);
} catch (IOException ex) {
    handle(ex);
} finally {
    afterOperation();
}
```

Tài nguyên được đóng khi rời phần `try` quản lý tài nguyên **trước khi** các khối `catch`/`finally` gắn với câu lệnh try-with-resources bắt đầu xử lý kết quả thoát ra.

Vì vậy khi `catch` bắt được exception, quá trình đóng các tài nguyên do try-with-resources sở hữu đã được thực hiện trước đó.

### Nếu tài nguyên là null?

Một tài nguyên trong try-with-resources có thể có giá trị `null`. Trường hợp này hợp lệ:

```java
void useNullableResource() throws Exception {
    try (AutoCloseable resource = null) {
        // ...
    }
}
```

Khi rời `try`, Java chỉ gọi `close()` nếu tài nguyên khác `null`. Đây là quy tắc của TWR, không phải lý do để cố tình thiết kế tài nguyên bằng `null`; trong mã nguồn thực tế vẫn nên ưu tiên quyền sở hữu và trạng thái tài nguyên rõ ràng.

## <a id="resource-close-order">Thứ tự đóng tài nguyên</a>

Tài nguyên được khởi tạo từ trái sang phải:

```java
try (A a = openA();
     B b = openB();
     C c = openC()) {
    use(a, b, c);
}
```

Thứ tự khởi tạo:

```text
A → B → C
```

Đóng theo thứ tự ngược:

```text
C → B → A
```

### VÌ SAO ĐÓNG NGƯỢC?

Tài nguyên tạo sau thường phụ thuộc tài nguyên tạo trước.

Ví dụ một tài nguyên bọc tài nguyên khác:

```java
try (InputStream raw = Files.newInputStream(path);
     BufferedInputStream buffered = new BufferedInputStream(raw)) {
    ...
}
```

`buffered` bọc `raw`, nên đối tượng bọc được đóng trước. Tuy nhiên, nhiều đối tượng bọc cũng tự đóng tài nguyên bên trong khi `close()`; việc có khai báo cả hai tài nguyên trong cùng try-with-resources hay không phải dựa trên **quyền sở hữu thực tế** để tránh đóng lặp một tài nguyên không thuộc trách nhiệm của mình.

### Nếu khởi tạo tài nguyên ở giữa bị thất bại?

Đây là trường hợp biên quan trọng.

```java
try (A a = openA();
     B b = openB();   // throw tại đây
     C c = openC()) {
    ...
}
```

Kết quả:

```text
A đã tạo thành công
B khởi tạo thất bại
C chưa bao giờ được tạo
        ↓
A vẫn được đóng
```

TWR chịu trách nhiệm dọn dẹp những tài nguyên đã khởi tạo thành công trước điểm thất bại. Nếu việc khởi tạo B đã ném exception và `A.close()` cũng ném exception, lỗi khởi tạo B vẫn là lỗi chính còn lỗi từ `A.close()` được giữ dưới dạng suppressed exception.

Một chi tiết quan trọng: nếu `openB()` đã tự cấp phát một tài nguyên nội bộ rồi thất bại **trước khi trả về đối tượng B**, TWR chưa bao giờ nhận quyền sở hữu B nên không thể gọi `B.close()`. Phần mã đang khởi tạo B phải tự dọn những tài nguyên nó đã cấp phát trước khi ném lỗi.

Điều này khó viết đúng nếu tự quản lý nhiều `try/finally` lồng nhau.

## <a id="twr-vs-finally">Try-with-resources và finally</a>

Dọn dẹp thủ công:

```java
InputStream in = Files.newInputStream(path);
try {
    return in.read();
} finally {
    in.close();
}
```

trông đơn giản nhưng có vấn đề nếu:

```text
phần thân ném A
và
close() ném B
```

Với `finally` thủ công, B có thể che A nếu mã nguồn không tự xử lý cẩn thận.

TWR có quy tắc chuẩn để giữ lỗi chính và gắn lỗi dọn dẹp dưới dạng **ngoại lệ được giữ lại (suppressed exception)**.

### So sánh

```text
finally thủ công
→ tự quản lý null, thứ tự đóng, nhiều tài nguyên, nhiều lỗi

try-with-resources
→ quyền sở hữu rõ trong cú pháp
→ thứ tự đóng được định nghĩa
→ dọn dẹp khi khởi tạo một phần bị thất bại
→ lỗi chính/suppressed exception được bảo toàn theo hợp đồng
```

TWR không thay thế mọi `finally`; `finally` vẫn hữu ích cho việc dọn dẹp không biểu diễn bằng `AutoCloseable`. Nhưng với tài nguyên do mã nguồn hiện tại sở hữu, TWR thường là lựa chọn mặc định tốt hơn.

### GHI NHỚ

```text
khai báo tài nguyên
→ quyền sở hữu/vòng đời rõ ràng

khởi tạo
→ trái sang phải

đóng
→ phải sang trái

khởi tạo giữa chừng thất bại
→ tài nguyên đã mở trước đó vẫn được đóng

phần thân + đóng tài nguyên cùng thất bại
→ cần suppressed exception
```

Phần tiếp theo đi sâu vào trường hợp thao tác chính đã thất bại mà việc dọn dẹp cũng thất bại: Java giữ cả hai lỗi như thế nào?

Việc dọn dẹp cũng có thể thất bại. Đây là bài toán khó của quản lý tài nguyên:

```text
thao tác chính đã thất bại
        +
dọn dẹp cũng thất bại
        ↓
không được làm mất lỗi nào
```

`try-with-resources` giải quyết việc này bằng khái niệm **lỗi chính (primary exception)** và **suppressed exception**.

## <a id="primary-vs-suppressed">Lỗi chính và lỗi được giữ lại (Suppressed Exception)</a>

Giả sử phần thân ném A:

```java
try (DemoResource resource = new DemoResource()) {
    throw new IllegalArgumentException("body failed");
}
```

và `resource.close()` cũng ném B.

TWR giữ:

```text
A
→ lỗi chính

B
→ suppressed exception được gắn vào A
```

### VÌ SAO A ĐƯỢC ƯU TIÊN?

Thao tác chính đã thất bại trước vì A. Nếu lỗi dọn dẹp B thay thế A, hệ thống sẽ báo:

```text
"close failed"
```

nhưng lại mất lỗi giải thích **vì sao thao tác chính đã thất bại**.

Cơ chế suppression cho phép giữ đầy đủ bức tranh nguyên nhân:

```text
lỗi chính
→ lỗi quyết định kết quả chính

suppressed
→ lỗi phụ xảy ra trong lúc dọn dẹp khi kết quả chính đã được xác định
```

### Ví dụ quan sát được

```java
final class DemoResource implements AutoCloseable {
    @Override
    public void close() {
        throw new IllegalStateException("close failed");
    }
}

try (DemoResource resource = new DemoResource()) {
    throw new IllegalArgumentException("body failed");
}
```

Nếu catch bên ngoài:

```java
catch (Exception ex) {
    System.out.println(ex.getMessage());

    for (Throwable suppressed : ex.getSuppressed()) {
        System.out.println("suppressed: " + suppressed.getMessage());
    }
}
```

kết quả logic là:

```text
primary   = IllegalArgumentException("body failed")
suppressed[0]
          = IllegalStateException("close failed")
```

Đây là bằng chứng trực tiếp cho lý do TWR an toàn hơn việc dọn dẹp thủ công.

## <a id="get-suppressed">Quan sát Suppressed Exception</a>

API:

```java
Throwable[] suppressed = ex.getSuppressed();
```

Một exception có thể có **nhiều** lỗi bị giữ lại dưới dạng suppressed.

Ví dụ ba tài nguyên:

```text
A mở trước
B mở sau
C mở cuối
```

Khi phần thân đã thất bại, quá trình đóng diễn ra:

```text
C → B → A
```

Nếu `C.close()` và `A.close()` cùng thất bại:

```text
lỗi từ phần thân
→ lỗi chính

lỗi khi đóng C
→ suppressed

lỗi khi đóng A
→ suppressed
```

Thứ tự trong dữ liệu chẩn đoán suppressed phản ánh các lỗi dọn dẹp được ghi nhận trong quá trình đóng.

### Suppressed exception khác Cause

Hai khái niệm trả lời hai câu hỏi khác nhau:

```text
cause
→ lỗi nào dẫn tới exception hiện tại theo chuỗi nguyên nhân/chuyển đổi?

suppressed
→ lỗi phụ nào xảy ra trong lúc một lỗi khác đã là kết quả chính?
```

Ví dụ:

```text
RuntimeException
cause
└── IOException

IOException
suppressed
└── lỗi khi đóng tài nguyên
```

Một throwable có thể vừa có `cause` vừa có suppressed exceptions.

> Chi tiết nâng cao: một lớp con của `Throwable` có thể tắt cơ chế suppression qua constructor được bảo vệ của `Throwable`. Khi đó suppressed exception sẽ không được ghi lại. Đây là trường hợp biên dành cho người viết thư viện; mô hình mặc định của các exception thông thường vẫn là suppression được bật.

### Logic nghiệp vụ có nên phụ thuộc vào suppressed?

Thông thường không.

Suppressed exception chủ yếu phục vụ **ngữ cảnh chẩn đoán**. Logic nghiệp vụ nên dựa vào hợp đồng, kiểu exception hoặc dữ liệu có cấu trúc rõ ràng hơn, thay vì quy tắc kiểu “nếu có đúng 2 suppressed exception thì làm X”.

## <a id="close-failure">Lỗi khi đóng tài nguyên</a>

Có ba trường hợp cần phân biệt.

### Trường hợp 1 — Phần thân thành công, đóng tài nguyên thất bại

```text
phần thân thành công
→ close() ném B
→ B trở thành lỗi chính
```

Thao tác cuối cùng vẫn thất bại vì tài nguyên không được đóng thành công theo hợp đồng.

### Trường hợp 2 — Phần thân thất bại, đóng tài nguyên cũng thất bại

```text
phần thân ném A
→ close() ném B
→ A là lỗi chính
→ B được suppressed trên A
```

### Trường hợp 3 — Nhiều lần đóng cùng thất bại, phần thân thành công

Với:

```text
A mở trước
B mở sau
```

thứ tự đóng:

```text
B → A
```

Nếu cả hai `close()` đều ném exception:

```text
lỗi khi đóng B
→ lỗi đóng tài nguyên chính

lỗi khi đóng A
→ được suppressed trên lỗi của B
```

Lỗi đầu tiên xuất hiện trong quá trình đóng trở thành kết quả chính; các lỗi đóng tiếp theo được giữ dưới dạng suppressed.

### Dọn dẹp thủ công dễ sai ở đâu?

```java
try {
    use();
} finally {
    close(); // nếu throw, có thể che exception từ use()
}
```

Tự tái tạo đúng quy tắc của TWR với nhiều tài nguyên, khởi tạo một phần và nhiều lỗi khi đóng là không đơn giản.

Đó là lý do suppression không phải một chi tiết “lạ” của API; nó tồn tại để giải quyết bài toán bảo toàn thông tin lỗi rất cụ thể.

### GHI NHỚ

```text
cause
→ chuỗi nguyên nhân / chuyển đổi exception

suppressed
→ lỗi phụ được giữ lại để không che lỗi chính

try-with-resources
→ tự quản lý suppression cho các lỗi dọn dẹp
```

Trước khi rời chủ đề try-with-resources, phần cuối xem một tiện ích cú pháp từ Java 9: dùng trực tiếp một biến `final` hoặc effectively final làm tài nguyên.

## <a id="effective-final-resource">Tài nguyên dùng biến effectively final</a>

Từ Java 9, biến cục bộ đã tồn tại có thể được dùng trực tiếp trong phần khai báo tài nguyên nếu nó là `final` hoặc effectively final:

```java
InputStream in = Files.newInputStream(path);

try (in) {
    consume(in);
}
```

Effectively final nghĩa là sau khi được gán giá trị, biến không bị gán lại.

Không hợp lệ về mặt effective-final:

```java
InputStream in = Files.newInputStream(path);
in = anotherStream;

try (in) {
    ...
}
```

### Quyền sở hữu tài nguyên vẫn cần rõ ràng

Cú pháp cho phép dùng biến có sẵn không có nghĩa mọi phương thức nhận `AutoCloseable` đều nên đóng nó.

Hãy hỏi:

```text
Ai tạo tài nguyên?
Ai sở hữu vòng đời của tài nguyên?
Ai có trách nhiệm đóng?
```

Nếu phương thức chỉ “mượn” tài nguyên do bên gọi sở hữu, tự đóng nó có thể vi phạm hợp đồng của bên gọi.

Chương tiếp theo chuyển từ cơ chế quản lý lỗi/tài nguyên sang thiết kế API và miền nghiệp vụ: khi nào một kiểu ngoại lệ tùy chỉnh thực sự bổ sung ý nghĩa?
