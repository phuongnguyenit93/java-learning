<a id="back-to-top"></a>

# Console và định dạng

## Menu
- [System.in, System.out và System.err](#console-and-formatting-001)
- [Chuyển hướng đầu vào, đầu ra và lỗi ở mức tiến trình](#console-and-formatting-002)
- [System.console và trường hợp không có console](#console-and-formatting-003)
- [Console.reader, writer, readLine và readPassword](#console-and-formatting-004)
- [PrintStream khác PrintWriter](#console-and-formatting-005)
- [print, println, printf và Formatter](#console-and-formatting-006)
- [Locale, mẫu định dạng và dấu phân cách số](#console-and-formatting-007)
- [Auto-flush và các thao tác thực sự kích hoạt flush](#console-and-formatting-008)
- [checkError và các lỗi I/O không được ném trực tiếp](#console-and-formatting-009)
- [Không vô tình đóng các stream chuẩn dùng chung](#console-and-formatting-010)

## <a id="console-and-formatting-001">System.in, System.out và System.err</a>

<details>
<summary>Xem chi tiết</summary>

# System.in, System.out và System.err trong Java

## 1. Ba luồng I/O chuẩn dùng để làm gì?

Một chương trình dòng lệnh thường có ba kênh giao tiếp với môi trường bên ngoài:

- **`System.in`** là **standard input (stdin)**: luồng byte đầu vào. Nó thường nhận thao tác nhập từ bàn phím khi chương trình chạy trong terminal, nhưng cũng có thể nhận dữ liệu từ tệp, pipe hoặc chương trình khác.
- **`System.out`** là **standard output (stdout)**: luồng đầu ra thông thường, dành cho kết quả mà người dùng hoặc chương trình gọi cần nhận.
- **`System.err`** là **standard error (stderr)**: luồng đầu ra riêng, dành cho lỗi, cảnh báo và thông tin chẩn đoán.

Trong Java, cả ba thuộc lớp `java.lang.System` và có thể truy cập ở bất kỳ đâu bằng `System.in`, `System.out`, `System.err` mà không cần import `System`.

```text
                          ┌───────────────────────────┐
bàn phím / file / pipe ──>│ System.in  : InputStream   │
                          │                           │
                          │        Ứng dụng Java      │
                          │                           │
terminal / file / pipe <──│ System.out : PrintStream   │
terminal / file / pipe <──│ System.err : PrintStream   │
                          └───────────────────────────┘
```

Ba kênh này là **luồng của tiến trình (process)**: khi khởi động chương trình, shell/hệ điều hành thường gắn chúng với terminal; bên gọi có thể đổi hướng luồng mà **không cần sửa mã Java**. Dù stdout và stderr thường cùng hiện trên một màn hình, chúng là **hai kênh logic riêng**. Phân biệt đúng kênh giúp shell lấy kết quả chương trình, lưu lỗi ra tệp và kết nối các chương trình bằng pipe một cách đáng tin cậy.

**Quy tắc giao tiếp đơn giản:** dữ liệu hoặc kết quả chính đi qua `System.out`; lỗi/cảnh báo đi qua `System.err`; chương trình nhận yêu cầu/dữ liệu từ `System.in`. Đây là quy ước thiết kế thường dùng, không phải ràng buộc bắt buộc của Java.

## 2. Kiểu dữ liệu thực tế của ba biến

| Biến | Kiểu Java | Chiều | Thao tác thường dùng |
| --- | --- | --- | --- |
| `System.in` | `java.io.InputStream` | Vào | `read()`, `read(byte[])` |
| `System.out` | `java.io.PrintStream` | Ra | `print()`, `println()`, `printf()`, `write()`, `flush()` |
| `System.err` | `java.io.PrintStream` | Ra | `print()`, `println()`, `printf()`, `write()`, `flush()` |

`InputStream` nhận **byte**; `PrintStream` cũng có khả năng ghi byte, nhưng bổ sung các phương thức thuận tiện để **in văn bản có định dạng**. `PrintStream` triển khai trên `OutputStream`, nên `System.out.write(65)` ghi một byte giá trị 65, còn `System.out.print(65)` ghi văn bản biểu diễn số `65` (thường là hai ký tự). Đây là điểm khác biệt quan trọng khi thiết kế chương trình xuất dữ liệu.

Hai ví dụ sau là các lời gọi **minh họa**, không phải chương trình độc lập:

```java
System.out.write(65);    // Một byte có giá trị 65 (ASCII chữ A).
System.out.print(65);    // Hai ký tự chữ số: 6 và 5.
System.err.println("Không thể mở tệp.");
```

`PrintStream` có các phương thức ghi chuỗi theo encoding được cấu hình. Vì vậy không nên mặc định rằng `System.out` dùng cùng charset với `Charset.defaultCharset()` trên mọi JDK, console và cấu hình. Nếu đầu ra phải là dữ liệu trao đổi theo một định dạng byte chính xác, hãy xác định charset và cách đóng gói dữ liệu trong hợp đồng giao tiếp.

## 3. System.in: đọc stdin dưới dạng byte

Vì `System.in` là `InputStream`, các quy tắc đọc byte vẫn áp dụng:

- `int read()` trả **0 đến 255** khi đọc được một byte, hoặc **-1** khi kết thúc luồng (EOF).
- `int read(byte[] buffer)` trả số byte đọc được; không cam kết luôn điền đầy mảng. Chỉ phần tử từ `0` đến `count - 1` là dữ liệu mới.
- Một lần đọc có thể chờ khi chưa có dữ liệu (blocking). Người dùng chưa nhập phím Enter hoặc phía pipe chưa đưa dữ liệu là những lý do có thể khiến chương trình trông như đang “đứng”.
- EOF không phải ký tự xuống dòng: Enter thường hoàn tất một dòng; EOF có nghĩa nguồn không còn byte.

Nếu nhập **văn bản**, đọc từng byte rồi ép thẳng thành `char` là sai với dữ liệu đa byte như UTF-8. Chọn một charset rõ ràng và dùng `InputStreamReader` để giải mã, có thể thêm `BufferedReader` để đọc theo dòng.

### Khi nào dùng BufferedReader?

`BufferedReader` tiện khi muốn nhận một dòng văn bản. Nó trả `String` không kèm ký tự xuống dòng; trả `null` ở EOF. Nó chấp nhận LF, CR hoặc CRLF để kết thúc dòng.

```java
// Đoạn minh họa; cần import java.io.* và StandardCharsets.
BufferedReader reader = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8));

String line = reader.readLine();
if (line == null) {
    System.err.println("Không có dữ liệu đầu vào (EOF).");
} else {
    System.out.println("Đã nhập: " + line);
}
```

Đoạn trên **không đóng** `reader` vì wrapper đóng thì `System.in` cũng bị đóng, có thể ảnh hưởng phần khác của ứng dụng tiếp tục đọc stdin. Với một ứng dụng dòng lệnh ngắn và độc lập, việc đóng stdin khi toàn bộ quá trình đã kết thúc có thể chấp nhận được; tuy nhiên ở thư viện, component tái sử dụng, hoặc chương trình nhiều bước, **người sở hữu luồng mới nên quyết định đóng**.

### Ví dụ Java 17 chạy được: đọc một dòng rồi phản hồi

**Tệp `ReadStdin.java`:**

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ReadStdin {
    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));

        System.out.print("Name? ");
        System.out.flush(); // Đảm bảo prompt được yêu cầu đẩy ra.

        String name = reader.readLine();
        if (name == null) {
            System.err.println("Input is empty (EOF)");
            return;
        }

        String normalized = name.strip();
        if (normalized.isEmpty()) {
            System.err.println("Input name is blank");
            return;
        }

        System.out.println("Hello, " + normalized);
    }
}
```

Biên dịch bằng JDK 17+:

```bash
javac ReadStdin.java
java ReadStdin
```

Nếu gõ `Lan` và nhấn Enter, terminal thường hiển thị:

```text
Name? Lan
Hello, Lan
```

Dòng `Name? Lan` bao gồm lời nhắc do chương trình in và ký tự người dùng gõ do **terminal echo**. Echo phụ thuộc cách terminal cấu hình, không phải một `println` của Java.

Với stdin đã chứa chính xác một dòng `Lan`, **dữ liệu stdout mà chương trình tạo** là:

```text
Name? Hello, Lan
```

Lúc này dữ liệu được lấy từ nguồn thay vì gõ tương tác; chương trình không in lại chuỗi input đã đọc. **Stderr trống** khi hợp lệ. Nếu stdin có EOF ngay từ đầu, stdout chỉ chứa `Name? ` (không xuống dòng), còn stderr chứa `Input is empty (EOF)` và một newline.

Lệnh chạy với shell hỗ trợ input redirection (Bash hoặc Command Prompt) sau khi tạo tệp văn bản `name.txt` chứa `Lan`:

```bash
java ReadStdin < name.txt
```

Trên **Windows PowerShell 5.1**, cú pháp input redirection `<` không được hỗ trợ giống `cmd.exe`; có thể dùng `cmd /c "java ReadStdin < name.txt"` khi cần giữ cơ chế redirect byte của `cmd`. Nếu dùng pipeline PowerShell như `Get-Content name.txt | java ReadStdin`, việc chuyển dữ liệu qua pipeline có thể ảnh hưởng encoding so với đọc trực tiếp từ tệp; với dữ liệu Unicode cần chọn giải pháp và encoding phù hợp.

### Tại sao phải flush lời nhắc?

`print("Name? ")` không có newline. Một số môi trường có buffering nên prompt có thể chưa xuất hiện trước khi chương trình chờ `readLine()`. `System.out.flush()` là yêu cầu hợp lý tại ranh giới tương tác. Đừng dựa vào việc `println` thường flush tự động ở một số cấu hình như một đảm bảo chung cho mọi output wrapper.

## 4. System.out: đầu ra bình thường và định dạng

`System.out` là `PrintStream`. Các phương thức quen thuộc:

| Phương thức | Công dụng |
| --- | --- |
| `print(value)` | In biểu diễn văn bản của giá trị, **không tự thêm newline** |
| `println(value)` | In giá trị rồi thêm **dấu kết thúc dòng của môi trường** |
| `println()` | Chỉ thêm dòng mới |
| `printf(format, args)` | In theo format string |
| `printf(locale, format, args)` | In theo locale tường minh |
| `format(...)` | Tương tự `printf` |
| `append(...)` | Nối chuỗi ký tự vào `PrintStream` |
| `write(int)`/`write(byte[])` | Ghi byte thô qua tầng output |
| `flush()` | Đẩy dữ liệu đang đệm xuống tầng bên dưới theo khả năng luồng |
| `checkError()` | Kiểm tra trạng thái lỗi ghi của `PrintStream` |

Hàm `println` thêm dòng mới nhưng không bảo đảm byte xuống dòng giống nhau giữa các hệ điều hành: thông thường LF trên Unix và CRLF trên Windows. Để chuẩn hóa một file/protocol cần LF, nên ghi ký tự `"\n"` theo đặc tả thay vì phụ thuộc separator hệ thống.

`printf` sử dụng quy tắc của `java.util.Formatter`:

- `%s`: chuỗi hoặc biểu diễn `String.valueOf(...)`.
- `%d`: số nguyên.
- `%f` và `%.2f`: số thực, với độ chính xác hiển thị.
- `%n`: newline theo môi trường, tốt hơn `"\n"` khi muốn format output theo nền tảng.
- `%%`: dấu phần trăm thực.

Cần chú ý **locale**. Một số locale dùng dấu phẩy làm dấu thập phân, khác dấu chấm. Với kết quả mà máy khác cần parse, nên truyền `Locale.ROOT` hoặc một locale định dạng xác định.

### Ví dụ Java 17: phân biệt print, println và printf

**Tệp `StandardOutputFormat.java`:**

```java
import java.util.Locale;

public class StandardOutputFormat {
    public static void main(String[] args) {
        System.out.print("Items: ");
        System.out.println(3);
        System.out.printf(Locale.ROOT, "Price: %.2f%n", 12.5);
        System.out.println("Done");
    }
}
```

Chạy:

```bash
javac StandardOutputFormat.java
java StandardOutputFormat
```

**Stdout hiển thị** (mỗi dòng kết thúc theo hệ điều hành):

```text
Items: 3
Price: 12.50
Done
```

Nếu bỏ `Locale.ROOT`, kết quả định dạng số thực có thể thay đổi tùy locale mặc định. Khi xuất CSV hoặc dữ liệu máy đọc, việc chỉ định locale thôi cũng chưa đủ để xây dựng định dạng an toàn: cần quy định escaping/quoting, charset và line ending.

### PrintStream và lỗi ghi: điểm dễ bỏ sót

Khác `OutputStream.write(...)` có thể ném `IOException`, các phương thức `PrintStream.print`/`println`/`write` được thiết kế **không ném checked `IOException` trong cách gọi thường lệ**. Chúng ghi nhận lỗi I/O vào trạng thái bên trong; ứng dụng có thể kiểm tra bằng `checkError()`:

```java
// Đoạn minh họa:
System.out.println("result");
System.out.flush();
if (System.out.checkError()) {
    System.err.println("Could not write standard output");
}
```

`checkError()` tự gọi flush nếu stream chưa đóng; trạng thái lỗi thường tồn tại sau khi lỗi ghi xảy ra. **Không coi việc `println` chạy xong là xác nhận bên nhận đã nhận/dùng dữ liệu**. Trong các chương trình xuất dữ liệu quan trọng hoặc pipeline, xem xét chính sách phát hiện broken pipe và exit status phù hợp. Với API không phải `PrintStream`, như `OutputStream` hoặc `Writer` thông thường, lỗi I/O có thể được báo bằng `IOException`.

## 5. System.err: kênh cảnh báo/lỗi độc lập

`System.err` cũng là `PrintStream`, có cùng API `print`, `println`, `printf`. Khác biệt nằm ở **mục đích và kênh tiến trình**: stderr dùng cho chẩn đoán, còn stdout giữ kết quả bình thường.

Một công cụ dòng lệnh có thể xuất kết quả ở stdout để bên gọi đưa tiếp vào chương trình khác, trong khi lỗi/cảnh báo cần được nhìn thấy mà không làm hỏng dữ liệu. Ví dụ, nếu stdout là danh sách ID thuần, việc in `"Warning: ..."` vào stdout có thể khiến chương trình nhận dữ liệu hiểu nhầm đó là một ID. Đưa warning sang stderr giúp dữ liệu stdout dễ parse.

```java
System.out.println("ID-001"); // Kết quả dành cho pipeline.
System.err.println("Warning: using cached data"); // Chẩn đoán.
```

Trên terminal, cả hai dòng có thể cùng hiện ra nên người dùng khó nhận ra kênh. Khi redirect stdout và stderr vào **hai tệp khác nhau**, sự khác biệt trở nên rõ.

### Ví dụ Java 17 chạy được: hai kênh đầu ra

**Tệp `OutputChannels.java`:**

```java
public class OutputChannels {
    public static void main(String[] args) {
        System.out.println("record-1");
        System.err.println("warning: fallback");
        System.out.println("record-2");
    }
}
```

Biên dịch:

```bash
javac OutputChannels.java
```

Ví dụ chạy trên shell hỗ trợ redirection:

```bash
java OutputChannels > result.txt 2> diagnostics.txt
```

**Nội dung `result.txt`:**

```text
record-1
record-2
```

**Nội dung `diagnostics.txt`:**

```text
warning: fallback
```

Đây là ví dụ dễ kiểm chứng rằng stdout và stderr là hai kênh khác nhau. Nếu chỉ chạy `java OutputChannels` và nhìn một terminal chung, **không được dựa vào thứ tự xen kẽ** giữa hai kênh để suy luận thứ tự sự kiện, vì buffering, lịch trình và cơ chế hiển thị có thể khiến các dòng trông khác thứ tự gọi.

## 6. Redirect, pipe và ý nghĩa của 0, 1, 2

Các ký hiệu stdin, stdout và stderr tương ứng theo quy ước hệ điều hành Unix/POSIX với file descriptor **0, 1, 2**. Shell khác nhau có cú pháp redirect tương tự nhưng không hoàn toàn giống nhau. Đối với Bash và Command Prompt, ví dụ thường gặp:

| Cú pháp | Ý nghĩa |
| --- | --- |
| `java App < input.txt` | Lấy stdin từ `input.txt` |
| `java App > output.txt` | Ghi stdout vào tệp, thường **ghi đè** |
| `java App >> output.txt` | Nối thêm stdout vào tệp |
| `java App 2> error.txt` | Ghi stderr vào tệp riêng |
| `java App > output.txt 2> error.txt` | Tách hai đầu ra |
| `java App 2>&1` | Hướng stderr tới nơi stdout hiện đang trỏ |
| `java Producer \| java Consumer` | Nối stdout của Producer với stdin của Consumer |

Khi dùng pipe `Producer | Consumer`, stderr của Producer **thường không tự đi vào stdin của Consumer**; stderr vẫn đi theo tuyến riêng trừ khi có redirection khác. Đây là lý do nên giữ stdout sạch, chỉ chứa dữ liệu mà bên tiêu thụ mong đợi.

**Thứ tự redirect có thể quan trọng.** Chẳng hạn ở Bash, `java App > output.txt 2>&1` cho cả hai kênh cùng ghi tới nơi stdout đã được hướng đến. Còn `java App 2>&1 > output.txt` có ý nghĩa khác vì stderr trước đó được gắn tới đích stdout ban đầu. Khi cùng dồn về một đích, thứ tự các byte/đoạn từ hai luồng không được coi là một thứ tự tổng thể đáng tin cậy chỉ nhờ cú pháp redirect.

**PowerShell có mô hình streams và redirection riêng**, đặc biệt khi gọi native executable qua pipeline, và cách xử lý encoding cũng khác giữa Windows PowerShell 5.1 và PowerShell mới. Hãy thử lệnh trên đúng shell đang sử dụng; không coi mọi cú pháp pipe/redirect là hoàn toàn tương đương về byte, encoding và exit status.

## 7. Stdin và stdout liên quan đến byte và charset như thế nào?

`System.in` đọc byte, còn `BufferedReader(InputStreamReader(...))` đọc text sau khi giải mã. Chọn charset ở biên dữ liệu:

```java
// Mã minh họa; mỗi thư viện có thể chọn charset theo hợp đồng riêng.
BufferedReader reader = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8));
String text = reader.readLine();
```

Ví dụ giả định dữ liệu được đưa vào stdin là **UTF-8**. Khi dữ liệu thực sự được tạo từ terminal với encoding khác, UTF-8 tường minh có thể không tương thích. Cần xác định nguồn là tệp, pipe, terminal hay hệ thống khác, rồi chọn charset phù hợp. Việc cùng nhìn thấy chữ tiếng Việt trên màn hình không chứng minh các byte trao đổi là UTF-8.

### Console encoding và default charset là hai khái niệm khác

`Charset.defaultCharset()` là charset mặc định cho nhiều API Java; JDK 17 trước JEP 400 có thể phụ thuộc nền tảng, JDK 18+ thường mặc định UTF-8 (với các ngoại lệ/chế độ tương thích). Nhưng charset của **console/standard streams** có thể đi theo cơ chế riêng của JVM và môi trường. Không nên suy luận đơn giản rằng `System.out` luôn là UTF-8 chỉ vì JVM có default charset UTF-8.

Nếu công cụ cần xuất **byte UTF-8 chính xác** để tiêu thụ từ pipe hoặc file, có thể tự tạo `Writer` trên output stream với charset xác định, nhưng phải quản lý việc flush và không tùy tiện đóng standard stream:

```java
// Đoạn minh họa, không phải chương trình độc lập.
Writer utf8 = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
utf8.write("Xin chào");
utf8.flush();
```

Cần import `java.io.Writer`, `java.io.OutputStreamWriter`, `java.nio.charset.StandardCharsets`, xử lý `IOException`. Không gọi `utf8.close()` giữa vòng đời ứng dụng nếu chương trình còn dùng stdout, vì đóng wrapper sẽ đóng stream bên dưới. Tránh trộn trực tiếp `System.out.print` với việc ghi bằng wrapper có đệm vào cùng một output stream nếu không quản lý thứ tự flush rõ ràng.

Nếu dữ liệu phải là **binary**, dùng byte-oriented API lên output channel hoặc `System.out.write(byte[])` và tách hẳn logging sang `System.err` để không làm hỏng payload.

## 8. System.console() và khác biệt với System.in

`System.console()` trả về một `java.io.Console` khi JVM có console tương tác gắn với quá trình chạy; nó có thể trả **`null`**, thường gặp khi chạy trong IDE, qua một số launcher hoặc với đầu vào/đầu ra đã được redirect.

Khác biệt chính:

- `System.in` luôn là kênh stdin của tiến trình theo API JVM, có thể đọc byte từ pipe/file.
- `Console` cung cấp thao tác đọc dòng, in prompt và `readPassword()` phù hợp hơn cho một số giao diện terminal.
- Khi `System.console() == null`, không được gọi thẳng `console.readLine()`; cần có phương án dùng stdin hoặc thay đổi cách chương trình được chạy.
- `Console.readPassword()` giúp đọc mật khẩu mà không echo theo cách console hỗ trợ; kết quả là `char[]`, có thể xóa sau khi xử lý. Nó **không phải** cách nhập mật khẩu phù hợp khi chương trình đang nhận dữ liệu tự động từ pipe.

### Ví dụ Java 17: nhập qua console hoặc stdin

**Tệp `ConsoleFallback.java`:**

```java
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConsoleFallback {
    public static void main(String[] args) throws IOException {
        Console console = System.console();
        String name;

        if (console != null) {
            name = console.readLine("Name? ");
        } else {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.out.print("Name? ");
            System.out.flush();
            name = reader.readLine();
        }

        if (name == null) {
            System.err.println("No name (EOF)");
            return;
        }

        System.out.println("Hello, " + name);
    }
}
```

Biên dịch:

```bash
javac ConsoleFallback.java
java ConsoleFallback
```

Với nhập `Minh`, chương trình in lời chào `Hello, Minh` sau khi có một dòng đầu vào; việc prompt hiển thị/echo như thế nào phụ thuộc nhánh console và môi trường. Nếu stdin ở EOF, stderr in `No name (EOF)`. Ví dụ này dùng UTF-8 ở **nhánh fallback**; với môi trường có charset stdin khác, cần điều chỉnh encoding theo dữ liệu thực tế.

Không nên dùng `System.console() != null` làm phép xác minh bảo mật rằng người nhập là người thật, vì chương trình vẫn chịu ảnh hưởng cấu hình terminal và tiến trình bên ngoài.

## 9. Scanner khi nhập dữ liệu có cấu trúc

`java.util.Scanner` là tiện ích cao hơn để phân tích token, số và các kiểu dữ liệu văn bản. Có thể gắn nó với stdin:

```java
// Đoạn minh họa:
Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
String word = scanner.next();
```

Cần import `java.util.Scanner` và `java.nio.charset.StandardCharsets`. Khi sử dụng `nextInt()` rồi `nextLine()`, hãy nhớ `nextInt()` chỉ đọc token số; phần kết thúc dòng vẫn có thể còn trong đầu vào, dẫn đến `nextLine()` trả một chuỗi rỗng. Nếu giao thức dựa trên từng dòng, thường dễ kiểm soát hơn khi dùng `BufferedReader.readLine()` rồi `Integer.parseInt(line.strip())` cùng xử lý lỗi.

`Scanner` cũng có cơ chế `Locale`: parsing dấu phẩy/dấu chấm của số có thể khác nhau giữa các locale. Khi cấu trúc đầu vào có định dạng xác định, cần chọn `scanner.useLocale(Locale.ROOT)` hoặc locale phù hợp với đặc tả.

**Đóng `Scanner` cũng đóng `System.in` bên dưới**, vì thế không tự động `try-with-resources` scanner quanh stdin nếu luồng được dùng chung giữa nhiều đoạn chương trình. Nếu scanner được tạo trên một luồng file mà đoạn mã tự sở hữu, `try-with-resources` vẫn là lựa chọn phù hợp.

## 10. Quản lý vòng đời và không vô tình đóng ba luồng chuẩn

`System.in`, `System.out`, `System.err` thường do JVM/launcher khởi tạo, có vòng đời gắn với cả tiến trình. Đóng trực tiếp chúng có thể khiến những đoạn khác không còn nhập hoặc xuất được.

Ví dụ cần thận trọng:

```java
// Không thích hợp nếu chương trình còn dùng stdout sau khối này.
try (PrintWriter writer = new PrintWriter(System.out)) {
    writer.println("First phase");
}
// PrintWriter.close() đóng luồng System.out bên dưới.
```

Khi tạo wrapper chỉ để dùng trong một phương thức, có thể để luồng chuẩn được quản lý ở cấp ứng dụng và gọi `flush()` khi cần thay vì đóng wrapper giữa chương trình. Với tệp độc lập mà bạn tự mở, vẫn ưu tiên `try-with-resources`. Phân biệt **wrapper** và **quyền sở hữu tài nguyên** là thiết yếu.

`System.out.close()` có thể làm các lần `println` sau không xuất được như mong đợi; `PrintStream` thường ghi nhận lỗi thay vì ném checked `IOException`, nên chương trình có thể “im lặng” mà không thấy exception.

### Khi nào flush?

- **Prompt nhập liệu:** flush trước khi chờ stdin nếu prompt được in mà không có newline.
- **Dữ liệu streaming cần bên nhận xử lý ngay:** flush ở các ranh giới ứng dụng cần, cân bằng hiệu năng.
- **Khi kết thúc quá trình ghi:** flush thông thường là hợp lý; với output stream đã được đóng đúng cách thì close sẽ xử lý buffer tùy implementation.

`flush()` không đảm bảo phía nhận đã đọc dữ liệu, cũng không chứng minh đã ghi bền vững trên ổ đĩa.

## 11. Có thể thay đổi các luồng System bằng setIn, setOut, setErr

Java cung cấp:

```java
System.setIn(InputStream newInput);
System.setOut(PrintStream newOutput);
System.setErr(PrintStream newError);
```

Đây là các lời gọi **minh họa** với biến luồng đã được tạo. Chúng **thay đổi tham chiếu global của JVM** đến standard streams, thường dùng trong test, framework hoặc bootstrap đặc biệt. Khi thay `System.out` bằng một `PrintStream` ghi vào bộ nhớ, code gọi `System.out.println` sau đó được chuyển sang nơi mới; nó không nhất thiết thay đổi file descriptor của mọi thư viện native hoặc tiến trình con.

Vì thay đổi có phạm vi toàn JVM, phải **khôi phục luồng cũ** trong `finally`. Nếu test chạy song song, một test thay `System.out` có thể bắt nhầm output của test khác. Tốt hơn nên thiết kế phần logic quan trọng nhận một `PrintStream`/`Writer` qua tham số khi có thể.

### Ví dụ Java 17 chạy được: tạm thời thu stdout

**Tệp `CaptureStdout.java`:**

```java
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class CaptureStdout {
    public static void main(String[] args) {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (PrintStream capture =
                     new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            System.out.print("captured");
        } finally {
            System.setOut(original);
        }

        System.out.println("Captured: "
                + bytes.toString(StandardCharsets.UTF_8));
    }
}
```

Chạy:

```bash
javac CaptureStdout.java
java CaptureStdout
```

**Stdout chính xác:**

```text
Captured: captured
```

Vì `System.out` đã được đổi tạm thời, lần `print("captured")` không đi thẳng ra terminal. Sau khối `finally`, stdout gốc được phục hồi để in kết quả. `capture.close()` chỉ đóng stream tạm, không đóng stdout gốc. Trong ứng dụng đa luồng, ví dụ này cần chạy trong ngữ cảnh kiểm thử được kiểm soát; thao tác thay global stream không phải kỹ thuật an toàn mặc định để thu log từng request.

## 12. stdout/stderr và exit code: hai cơ chế khác nhau

In lỗi ra `System.err` **không tự làm chương trình thất bại**. Ngược lại, một chương trình có thể kết thúc với mã lỗi mà không in gì. Shell và các công cụ automation thường kiểm tra **exit code**, bên cạnh stdout và stderr.

Ví dụ:

```java
// Đoạn minh họa, chỉ gọi khi muốn kết thúc JVM.
System.err.println("Invalid command-line arguments");
System.exit(2);
```

`System.exit(2)` yêu cầu chấm dứt JVM với status 2 theo quy ước ứng dụng; shell thường coi status khác 0 là thất bại. Tránh dùng `System.exit` trong thư viện hoặc unit test vì sẽ kết thúc cả tiến trình chạy. Với phương thức `main` riêng, lựa chọn khác là ném exception chưa bắt hoặc quản lý luồng điều khiển rõ ràng; exit status cụ thể phụ thuộc cách launcher xử lý.

Một CLI tốt thường xác định ba quy ước cùng lúc: **stdout chứa gì**, **stderr chứa gì** và **mã thoát có ý nghĩa gì**. Đối với công cụ chỉ tạo dữ liệu đầu ra, thông báo thành công hoặc progress không nên trộn vào stdout nếu bên gọi cần parse dữ liệu sạch.

## 13. Buffering, trộn hai luồng và tính đồng thời

Dù code lần lượt gọi:

```java
System.out.println("A");
System.err.println("B");
System.out.println("C");
```

điều đó không đảm bảo terminal, IDE hoặc tệp đích hiển thị `A`, `B`, `C` đúng thứ tự nếu hai luồng đi qua **hai cơ chế đệm hoặc tiêu thụ khác nhau**. Flush có thể làm việc xuất hiện kịp thời hơn nhưng không biến hai stream thành một giao thức có thứ tự tổng thể.

Trong chương trình đa luồng, nhiều thread cùng in ra các stream có thể tạo nội dung xen kẽ; ngay cả khi từng lời gọi được đồng bộ ở một mức độ bởi implementation, **đừng dựa vào tính nguyên tử của toàn bộ một thông điệp ứng dụng** nếu ghép từ nhiều lời gọi. Cách tốt hơn là định dạng thông điệp hoàn chỉnh trước khi ghi, hoặc giao việc log theo thứ tự cho một component logging chuyên dụng.

Nếu cả stdout và stderr được redirect chung một tệp, điều này vẫn không tạo một đồng hồ hay một thứ tự sự kiện đáng tin cậy. Để chẩn đoán đa luồng, thường nên có timestamp, thread identifier và cơ chế logging phù hợp thay vì ghép `System.out.println` rải rác.

## 14. Ví dụ thực tế: một CLI có stdout sạch và stderr rõ ràng

Chương trình `NormalizeLines` đọc **từng dòng UTF-8** từ stdin, chuẩn hóa khoảng trắng ở đầu/cuối rồi xuất các dòng không rỗng sang stdout. Thống kê số dòng bị bỏ qua được đưa ra stderr, để có thể pipe stdout vào chương trình khác mà không lẫn thông báo chẩn đoán.

**Tệp `NormalizeLines.java`:**

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class NormalizeLines {
    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Writer writer = new OutputStreamWriter(
                System.out, StandardCharsets.UTF_8);

        int omitted = 0;
        String line;

        while ((line = reader.readLine()) != null) {
            String value = line.strip();
            if (value.isEmpty()) {
                omitted++;
                continue;
            }

            writer.write(value);
            writer.write('\n'); // Định dạng đầu ra quy định LF.
        }

        writer.flush(); // Không đóng System.out do ứng dụng còn sở hữu.
        System.err.println("Omitted blank lines: " + omitted);
    }
}
```

Biên dịch:

```bash
javac NormalizeLines.java
```

Giả sử `lines.txt` là một tệp **UTF-8** với nội dung dưới đây (có một dòng chỉ gồm khoảng trắng ở giữa):

```text
  Lan

 Minh
```

Chạy ở Bash/cmd:

```bash
java NormalizeLines < lines.txt > clean.txt 2> stats.txt
```

**Nội dung `clean.txt` đúng theo logic chương trình:**

```text
Lan
Minh
```

**Nội dung `stats.txt`:**

```text
Omitted blank lines: 1
```

`String.strip()` loại khoảng trắng theo định nghĩa Unicode whitespace của Java, khác một số trường hợp so với `trim()`. Chương trình không giữ newline gốc: nó xuất LF cố định, đây là **chủ ý của định dạng đầu ra**. Dòng rỗng có thể là dòng trống thực sự hoặc chỉ chứa whitespace. Trường hợp đầu vào lỗi charset cần được xử lý riêng nếu yêu cầu từ chối UTF-8 không hợp lệ; cấu hình decoder mặc định có thể thay byte lỗi tùy API.

Đây là cách ghép đúng ba kênh: stdin là nguồn dữ liệu, stdout chỉ xuất bản ghi đã xử lý, stderr là thống kê/chẩn đoán. Nếu muốn log/tracing phức tạp hơn, có thể chuyển phần chẩn đoán sang logging framework và cấu hình appender phù hợp.

## 15. Những nhầm lẫn phổ biến

| Nhầm lẫn | Vì sao nguy hiểm | Cách hiểu/sửa |
| --- | --- | --- |
| `System.in` luôn là bàn phím | Stdin có thể là file, pipe hoặc nguồn khác | Thiết kế đọc được cả tương tác và redirect |
| `read()` trả một ký tự văn bản | Nó trả một **byte**, có thể là một phần UTF-8 | Dùng `InputStreamReader` và charset phù hợp cho text |
| `-1` là byte `0xFF` | `read()` trả `int` để phân biệt EOF với 255 | Kiểm tra `int value == -1` |
| `System.out` và `System.err` luôn tương đương | Shell có thể redirect riêng | Kết quả sang stdout; cảnh báo/lỗi sang stderr |
| `print(65)` tương đương `write(65)` | Một bên ghi biểu diễn số, một bên ghi byte | Dùng đúng API text hoặc byte |
| `println` luôn tạo ký tự `'\n'` duy nhất | Separator tùy nền tảng | Chọn `%n` cho dòng hệ thống, `"\n"` cho LF cố định |
| Không có exception nghĩa là `PrintStream` ghi thành công | `PrintStream` có cơ chế lưu trạng thái lỗi | Kiểm tra `checkError()` khi cần |
| Đóng `Scanner(System.in)` chỉ giải phóng Scanner | Nó đóng cả stdin bên dưới | Chỉ đóng khi sở hữu/quyết định kết thúc stdin |
| `System.console()` luôn khác `null` | IDE, pipeline, launcher có thể không cấp Console | Kiểm tra `null`, có nhánh fallback |
| Mọi terminal đều dùng UTF-8 | Encoding có thể tùy cấu hình | Thống nhất charset thực tế hai phía |
| stderr được pipe tự động cùng stdout | Pipe thường chỉ nối stdout | Redirect stderr có chủ đích khi cần |
| Trộn stdout và stderr sẽ giữ đúng thứ tự câu lệnh | Hai stream có buffering/consumer riêng | Đừng dựa vào thứ tự hiển thị |
| `System.setOut` chỉ ảnh hưởng một phương thức | Tham chiếu thay đổi toàn JVM | Khôi phục trong `finally`, tránh test song song |
| In ra stderr tự động trả exit code lỗi | Kênh output và exit status độc lập | Đặt exit status theo giao kèo CLI |

## 16. Chọn công cụ nào cho từng nhu cầu?

- **Nhập một dòng text:** `BufferedReader(new InputStreamReader(System.in, charset))`.
- **Nhập dữ liệu có token/số:** `Scanner` nếu phù hợp, chú ý locale, token còn lại và quyền đóng stdin.
- **Nhập tương tác với prompt/password:** cân nhắc `System.console()` và luôn xử lý `null`.
- **In kết quả thân thiện với người dùng:** `System.out.println` hoặc `printf` với locale xác định nếu cần.
- **In lỗi/cảnh báo:** `System.err`, để stdout giữ dữ liệu đầu ra sạch.
- **Xuất một luồng byte hoặc văn bản theo encoding giao kèo:** dùng byte output hoặc một `Writer` cấu hình charset và định dạng rõ ràng.
- **Kiểm thử output:** ưu tiên truyền `PrintStream`/interface qua dependency injection; chỉ dùng `System.setOut` trong test cô lập và luôn phục hồi luồng gốc.
- **Ứng dụng có logging và chẩn đoán phức tạp:** dùng logging framework thay vì in tự do từ nhiều lớp.

## 17. Bài tập tự kiểm tra

1. Chạy `OutputChannels` ba lần: bình thường, với `> result.txt`, và với `> result.txt 2> diagnostics.txt`. Giải thích thông điệp nào thuộc stdout/stderr.
2. Chạy `ReadStdin` với bàn phím và với tệp input. Quan sát khác biệt giữa **terminal echo** và những byte mà Java thực sự in ra.
3. Thay `System.out.print("Name? ")` bằng `println`, giải thích thay đổi về cách prompt hiển thị và vì sao `flush` vẫn có ý nghĩa trong một số kênh có buffer.
4. Tạo một file UTF-8 gồm tiếng Việt, đọc qua `InputStreamReader(System.in, UTF_8)` và đối chiếu với chạy trong terminal có encoding khác. Ghi lại charset của từng đầu.
5. Dùng `System.err.println` để báo lỗi khi không có input, rồi cho chương trình chạy với stdin EOF. Kiểm tra stdout và stderr độc lập.
6. Mở rộng `NormalizeLines` để nếu toàn bộ input rỗng thì trả exit status 2. Giữ lỗi ở stderr và giải thích vì sao một message trên stderr không đủ để biểu thị thất bại cho script.
7. Viết test cho một phương thức nhận `PrintStream output` rồi gọi `output.println("ok")`. Dùng `ByteArrayOutputStream` để so sánh kết quả mà không thay đổi `System.out` toàn JVM.
8. Tìm và sửa lỗi `Scanner.nextInt()` ngay trước `nextLine()`; đối chiếu với phương án đọc nguyên dòng và parse rõ ràng.
9. Giải thích tại sao stdout là kênh dữ liệu, stderr là kênh chẩn đoán, còn **exit status** là tín hiệu kết quả của tiến trình; ba điều này liên quan nhưng không thay thế nhau.

## 18. Ghi nhớ

`System.in` là **đầu vào kiểu `InputStream`**, còn `System.out` và `System.err` là **hai đầu ra kiểu `PrintStream`**. Chúng là các kênh chuẩn của tiến trình Java và có thể được redirect qua file hoặc pipe. Để viết công cụ dòng lệnh rõ ràng, hãy đọc stdin theo đúng kiểu dữ liệu và charset, xuất kết quả chính ra stdout, đưa lỗi/cảnh báo qua stderr, kiểm soát flush/đóng theo quyền sở hữu, và dùng exit code làm tín hiệu thành công/thất bại cho bên gọi.

### Tài liệu tham khảo

- Java SE 17 API: [`System`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html), [`InputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html), [`PrintStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PrintStream.html).
- Java SE 17 API: [`Console`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Console.html), [`InputStreamReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html), [`Scanner`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Scanner.html).
- [JEP 400: UTF-8 by Default](https://openjdk.org/jeps/400) để phân biệt default charset JVM với yêu cầu charset cụ thể ở console/standard streams.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="console-and-formatting-002">Chuyển hướng đầu vào, đầu ra và lỗi ở mức tiến trình</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển hướng đầu vào, đầu ra và lỗi ở mức tiến trình trong Java I/O

## 1. Ba kênh chuẩn của một tiến trình

Khi một chương trình Java chạy, nó thường nhận được ba kênh giao tiếp chuẩn do môi trường khởi chạy chuẩn bị:

| Kênh | Tên | File descriptor thông dụng trên POSIX | API Java | Ý nghĩa |
| --- | --- | --- | --- | --- |
| Standard input | stdin | 0 | **System.in**, kiểu InputStream | Đọc dữ liệu vào |
| Standard output | stdout | 1 | **System.out**, kiểu PrintStream | Ghi kết quả bình thường |
| Standard error | stderr | 2 | **System.err**, kiểu PrintStream | Ghi lỗi hoặc thông tin chẩn đoán |

Khi khởi chạy trực tiếp từ terminal, stdin thường nhận dữ liệu từ bàn phím; stdout và stderr thường cùng hiện trong terminal. Nhưng **đích của hai luồng xuất vẫn độc lập về mặt logic**. Shell hoặc tiến trình cha có thể nối riêng mỗi luồng đến file, pipe, terminal hay dịch vụ thu thập log.

**Chuyển hướng I/O ở mức tiến trình (process redirection)** nghĩa là đổi nguồn stdin hoặc đích stdout/stderr của một tiến trình. Nếu môi trường chuyển hướng stdout vào file, lệnh **System.out.println(...)** trong Java vẫn được gọi như cũ; nội dung sẽ tới file thay vì màn hình.

~~~text
                 +-------------------------+
input.txt ------>| stdin   System.in       |
                 |                         |
                 |     Java process        |
                 |                         |
output.txt <-----| stdout  System.out      |
error.txt  <-----| stderr  System.err      |
                 +-------------------------+
~~~

**Tại sao phải tách stdout và stderr?** Khi chương trình tạo dữ liệu đầu ra cho công cụ khác xử lý (ví dụ JSON, CSV, tên file, mã kết quả), những dòng lỗi được in lẫn vào stdout có thể khiến bên đọc không phân tích được. Tách stderr giúp lấy kết quả thuần từ stdout và vẫn theo dõi thông báo chẩn đoán.

## 2. Chuyển hướng bằng shell

Trên **Bash/sh của Linux và macOS** và **cmd.exe của Windows**, những toán tử quen thuộc có ý nghĩa:

| Toán tử | Hành động |
| --- | --- |
| **< input.txt** | Nối stdin của chương trình từ file |
| **> output.txt** hoặc **1> output.txt** | Nối stdout vào file; thường tạo mới hoặc ghi đè file cũ |
| **>> output.txt** | Ghi nối thêm stdout vào file |
| **2> error.txt** | Nối stderr vào file; thường tạo mới hoặc ghi đè file cũ |
| **2>> error.txt** | Ghi nối thêm stderr |
| **> both.txt 2>&1** | Chuyển stdout vào file, rồi nối stderr với đích stdout hiện tại |
| **2> error.txt > output.txt** | Chuyển hai luồng tới hai file riêng |

Đối với ký hiệu **0, 1, 2**, đây là các số file descriptor được shell sử dụng: 0 là stdin, 1 là stdout, 2 là stderr. Toán tử **2>&1** có nghĩa là sao chép đích hiện tại của stdout cho stderr; nó không phải thao tác nối nội dung hai String trong Java.

### Thứ tự chuyển hướng rất quan trọng

~~~bash
java Example > combined.log 2>&1
~~~

Shell xử lý từ trái sang phải: trước tiên stdout hướng đến combined.log, sau đó stderr nối cùng đích đó. Cả kết quả và lỗi đều có thể vào một file.

~~~bash
java Example 2>&1 > only-stdout.log
~~~

Ở lệnh thứ hai, stderr được nối với **đích stdout ban đầu** trước khi stdout hướng vào only-stdout.log. Nếu đích ban đầu là terminal, lỗi vẫn có thể hiện ở terminal. Hai lệnh **không tương đương**.

Khi cùng chuyển vào một file, không nên dựa vào thứ tự xen kẽ chính xác của từng dòng stdout và stderr trong mọi tình huống: hai luồng có bộ đệm và lịch trình ghi độc lập, và công cụ thu thập log cũng có thể ảnh hưởng thứ tự quan sát.

## 3. Chương trình Java 17+ dùng cả ba luồng

Tạo file **StandardStreamsDemo.java**, lưu mã nguồn UTF-8:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class StandardStreamsDemo {
    public static void main(String[] args) throws IOException {
        BufferedReader input = new BufferedReader(
                new InputStreamReader(
                        System.in, StandardCharsets.UTF_8));

        String line = input.readLine();

        if (line == null) {
            System.out.println("OUT:<EOF>");
        } else {
            System.out.println("OUT:" + line);
        }
        System.err.println("ERR:diagnostic");
    }
}
~~~

Giải thích:

- **System.in** cung cấp byte đầu vào. InputStreamReader giải mã byte thành ký tự theo UTF-8; BufferedReader cung cấp **readLine()**.
- Nếu stdin đã kết thúc mà chưa có dòng nào, **readLine() trả null**, chương trình in OUT:<EOF>.
- Kết quả hoặc trạng thái vào stdout qua System.out; thông điệp chẩn đoán vào stderr qua System.err.
- Chương trình đọc đúng **một dòng** rồi kết thúc, phù hợp để thử chuyển hướng từ file. Ví dụ không chủ động đóng System.in để tránh đóng kênh chuẩn khi chương trình khác cần dùng tiếp.

Biên dịch bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 StandardStreamsDemo.java
~~~

Trên **Bash/sh**:

~~~bash
printf 'xin chao\n' > input.txt
java StandardStreamsDemo < input.txt > output.txt 2> error.txt
cat output.txt
cat error.txt
~~~

Trên **cmd.exe**:

~~~bat
echo xin chao>input.txt
java StandardStreamsDemo < input.txt > output.txt 2> error.txt
type output.txt
type error.txt
~~~

Cả hai cách đều tạo đầu vào là một dòng xin chao và tách hai luồng. Nội dung **output.txt**:

~~~text
OUT:xin chao
~~~

Nội dung **error.txt**:

~~~text
ERR:diagnostic
~~~

Nếu bỏ phần **2> error.txt** mà vẫn có **> output.txt**, thông báo ERR:diagnostic sẽ tiếp tục đi theo stderr ban đầu, thường là terminal. Nếu dùng **> combined.txt 2>&1**, cả hai dòng được hướng vào combined.txt.

### PowerShell có cú pháp và quy tắc riêng

Không mặc định lấy ví dụ **< input.txt** của Bash/cmd áp dụng nguyên xi cho PowerShell, đặc biệt Windows PowerShell 5.1. Trong PowerShell, có thể dùng Start-Process với ba tham số chuyển hướng riêng:

~~~powershell
$p = Start-Process -FilePath java -ArgumentList 'StandardStreamsDemo' -RedirectStandardInput 'input.txt' -RedirectStandardOutput 'output.txt' -RedirectStandardError 'error.txt' -NoNewWindow -Wait -PassThru
$p.ExitCode
~~~

Ví dụ giả định input.txt đã tồn tại, class được biên dịch trong thư mục đang làm việc và Java có trên PATH. **-Wait** đợi tiến trình hoàn tất, **-PassThru** lấy đối tượng tiến trình để kiểm tra ExitCode.

PowerShell còn có hệ thống stream và cách xử lý encoding của redirection riêng, thay đổi theo phiên bản. Nếu cần dữ liệu binary giữ nguyên từng byte, ưu tiên API byte của Java thay vì suy rằng mọi shell sẽ luôn giữ nguyên byte trong các pipeline có xử lý text.

## 4. Exit code độc lập với stdout và stderr

**Thông báo lỗi** và **mã thoát tiến trình** là hai khái niệm khác nhau:

- Một chương trình có thể ghi cảnh báo vào stderr và vẫn thoát với mã 0.
- Một chương trình có thể không ghi gì vào stderr nhưng trả mã thoát khác 0.
- Chương trình có thể ghi dữ liệu một phần ra stdout rồi thất bại.
- Chuyển stderr vào file không khiến thao tác lỗi biến thành thành công.

Trong Bash, biến **$?** ngay sau lệnh có exit status của lệnh gần nhất; cmd.exe thường dùng **%ERRORLEVEL%**; PowerShell hỗ trợ **$LASTEXITCODE** cho tiến trình native. Trong Java, **Process.waitFor()** trả exit code của tiến trình con sau khi nó kết thúc. Quy ước phổ biến là 0 biểu thị thành công, nhưng ý nghĩa chính xác phải theo hợp đồng của từng chương trình.

Để tự quy định mã thoát cho một ứng dụng Java, có thể dùng **System.exit(code)** ở nơi phù hợp. Không dùng phương thức đó để kết thúc tùy tiện một thư viện vì nó chấm dứt cả JVM hiện tại.

## 5. ProcessBuilder: cấu hình kênh chuẩn của tiến trình con

Khi Java cần chạy một chương trình bên ngoài, dùng **ProcessBuilder**. Khác với shell, ProcessBuilder nhận **danh sách lệnh và đối số**, và cung cấp API để nối stdin/stdout/stderr trước khi gọi **start()**.

Mặc định, ba kênh của tiến trình con dùng **PIPE**:

~~~text
               Java parent process
                       |
    ghi vào child stdin: process.getOutputStream()
                       |
                       v
                  Java child
                       |
     +-----------------+-----------------+
     |                                   |
 child stdout                        child stderr
     |                                   |
     v                                   v
process.getInputStream()      process.getErrorStream()
~~~

Tên phương thức theo **góc nhìn của parent**: getOutputStream() là nơi parent **ghi vào stdin của child**, còn getInputStream() là nơi parent **đọc stdout của child**. Đây là nguyên nhân dễ gây nhầm lẫn khi mới dùng Process API.

### Chương trình chạy được: redirect từ file và thu hai output riêng

Tạo file **ProcessRedirectionDemo.java**:

~~~java
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class ProcessRedirectionDemo {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "child".equals(args[0])) {
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(
                            System.in, StandardCharsets.UTF_8));

            String first = input.readLine();
            String second = input.readLine();

            System.out.println("received=" + first + "|" + second);
            System.err.println("warning=demo");
            System.exit(7);
            return;
        }

        Path dir = Files.createTempDirectory("redirect-java-");
        Path source = dir.resolve("source.txt");
        Path normal = dir.resolve("normal.txt");
        Path errors = dir.resolve("errors.txt");

        try {
            Files.writeString(source, "alpha\nbeta\n",
                    StandardCharsets.UTF_8);

            String os = System.getProperty("os.name")
                    .toLowerCase(Locale.ROOT);
            String javaName = os.contains("win") ? "java.exe" : "java";
            Path executable = Path.of(
                    System.getProperty("java.home"),
                    "bin", javaName);

            ProcessBuilder builder = new ProcessBuilder(
                    executable.toString(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    ProcessRedirectionDemo.class.getName(),
                    "child");

            builder.redirectInput(source.toFile());
            builder.redirectOutput(normal.toFile());
            builder.redirectError(errors.toFile());

            Process child = builder.start();
            int exit = child.waitFor();

            String stdout = Files.readString(
                    normal, StandardCharsets.UTF_8).strip();
            String stderr = Files.readString(
                    errors, StandardCharsets.UTF_8).strip();

            System.out.println("exit=" + exit);
            System.out.println("stdout=" + stdout);
            System.out.println("stderr=" + stderr);
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(normal);
            Files.deleteIfExists(errors);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ProcessRedirectionDemo.java
java ProcessRedirectionDemo
~~~

Stdout từ **parent**:

~~~text
exit=7
stdout=received=alpha|beta
stderr=warning=demo
~~~

Điều gì đã xảy ra?

1. Parent tạo file tạm chứa alpha và beta, mỗi giá trị ở một dòng.
2. Parent dùng đường dẫn đến Java executable của JDK đang chạy và khởi động **một JVM con**, chạy lại class với đối số child.
3. **redirectInput(source)** dùng nội dung file làm stdin của child.
4. **redirectOutput(normal)** và **redirectError(errors)** tách stdout, stderr vào hai file riêng.
5. Child đọc hai dòng, in received=alpha|beta lên stdout, in warning=demo lên stderr rồi chủ động thoát với mã 7.
6. Parent **waitFor()** trước khi đọc các file kết quả, nên có thể biết child đã kết thúc. Parent chỉ in lại nội dung file sau đó.
7. Parent xóa các file tạm trong finally.

**System.exit(7)** bên nhánh child chỉ kết thúc **JVM con**, không kết thúc JVM cha. Mã thoát 7 được chọn để minh họa rằng parent có thể nhận một exit code khác 0.

Ví dụ giả định chạy bằng classpath thông thường như lệnh đã đưa. Với ứng dụng chạy trên module path, fat JAR hoặc classloader tùy chỉnh, lệnh gọi JVM con cần điều chỉnh theo cách đóng gói. Các chuỗi trong output của ví dụ chỉ dùng ASCII nên không phát sinh khác biệt console encoding; với văn bản Unicode phải thống nhất encoding ở hai phía.

## 6. Chế độ redirect được Java cung cấp

| Cơ chế ProcessBuilder | Ý nghĩa | Khi nào dùng |
| --- | --- | --- |
| **Redirect.PIPE** | Nối qua pipe do Java parent sở hữu; mặc định | Parent xử lý output hoặc cấp input trong thời gian chạy |
| **Redirect.INHERIT** | Child dùng kênh chuẩn đã có của parent | Chương trình con tương tác trực tiếp hoặc cùng dùng log của parent |
| **Redirect.from(file)** | Child lấy stdin từ file | Dữ liệu đầu vào hữu hạn có sẵn |
| **Redirect.to(file)** | Child ghi stdout/stderr vào file, thay nội dung cũ | Tạo báo cáo/log mới |
| **Redirect.appendTo(file)** | Ghi nối stdout/stderr vào file | Giữ log trước đó |
| **Redirect.DISCARD** | Bỏ stdout/stderr | Output không cần thiết |

Một số cấu hình minh họa, đặt **trước** builder.start():

~~~java
// Mảnh code; builder là ProcessBuilder, logPath là Path.
builder.inheritIO(); // Kế thừa stdin/stdout/stderr từ parent.
~~~

Hoặc:

~~~java
builder.redirectOutput(
        ProcessBuilder.Redirect.appendTo(logPath.toFile()));
builder.redirectError(ProcessBuilder.Redirect.INHERIT);
~~~

Hai mảnh code mô tả **các cách chọn cấu hình**, không phải yêu cầu gọi liên tiếp nếu muốn giữ nguyên cả ba redirect của inheritIO.

**Lưu ý về API Process:** khi stdout của child đã được chuyển tới file hay INHERIT, **process.getInputStream()** tương ứng thường là *null input stream*, không có nội dung stdout để parent đọc. Tương tự, khi stderr đã redirect, getErrorStream() không còn kênh dữ liệu stderr riêng; khi stdin đã lấy từ file/INHERIT, getOutputStream() là *null output stream*. Đừng đồng thời kỳ vọng vừa redirect thẳng tới file vừa đọc toàn bộ nội dung qua PIPE mặc định.

**inheritIO()** kế thừa kênh chuẩn của **process parent**, và parent có thể đã được shell chuyển hướng từ trước. Chế độ inherit không nhất thiết có nghĩa child nối với màn hình vật lý.

## 7. Gộp stdout với stderr trong Java

Nếu một lệnh chỉ cần ghi log chung, có thể bật **redirectErrorStream(true)**:

~~~java
// Mảnh code: builder đã được cấu hình lệnh phù hợp.
builder.redirectErrorStream(true);
Process process = builder.start();

try (var input = process.getInputStream()) {
    input.transferTo(System.out);
}
int exitCode = process.waitFor();
~~~

Lúc này stderr của child được gộp vào stdout và parent đọc dữ liệu gộp từ **getInputStream()**. Cấu hình redirectError(...) riêng bị bỏ qua khi redirectErrorStream(true). Nếu stdout còn là PIPE thì input.transferTo(System.out) tiêu thụ pipe trong lúc child chạy; sau khi stream hết dữ liệu, waitFor() thu mã thoát.

Nếu muốn lưu chung vào file:

~~~java
builder.redirectErrorStream(true);
builder.redirectOutput(
        ProcessBuilder.Redirect.appendTo(logPath.toFile()));
~~~

**Nhược điểm khi gộp:** thông báo lỗi có thể xen trong JSON/CSV vốn được kỳ vọng chỉ nằm ở stdout, làm hỏng quá trình phân tích. Chọn gộp khi thực sự muốn log chung; giữ riêng stderr khi stdout là dữ liệu có cấu trúc.

Thứ tự tương đối của thông điệp sau khi gộp không nhất thiết tái tạo chính xác thứ tự lập trình do buffering, flush và tiến độ thực thi.

## 8. Rủi ro deadlock khi không đọc các PIPE

Lỗi thường gặp khi parent gọi:

~~~java
// KHÔNG AN TOÀN nếu stdout/stderr của child vẫn là PIPE
// và child ghi đủ nhiều dữ liệu.
Process child = builder.start();
int code = child.waitFor();
// Sau đó mới bắt đầu đọc stdout và stderr.
~~~

**Vì sao có thể treo?** Pipe có bộ đệm hữu hạn. Child ghi stdout hoặc stderr quá nhiều làm pipe đầy; child phải chờ parent đọc. Parent lại đang **waitFor()**, chờ child kết thúc. Hai bên chờ nhau:

~~~text
child write() -> pipe đầy -> chờ parent đọc
parent waitFor() -> chờ child kết thúc
=> deadlock
~~~

Các lựa chọn giải quyết:

1. Redirect stdout và stderr của child **trực tiếp tới file**, như chương trình ProcessRedirectionDemo.
2. Dùng **inheritIO()** nếu có môi trường sẵn tiêu thụ output và chấp nhận dùng kênh chuẩn của parent.
3. Nếu muốn một stream chung, **redirectErrorStream(true)** và đọc stream chung trong lúc child chạy.
4. Nếu cần tách kết quả và lỗi, **đọc stdout và stderr đồng thời**, ví dụ mỗi stream bằng một task hoặc thread riêng; sau đó đợi child hoàn tất.

Một tình huống chờ khác liên quan stdin: khi mặc định stdin của child nối với PIPE, chương trình con có thể đợi dữ liệu hoặc EOF. Sau khi parent ghi xong vào **process.getOutputStream()**, phải **đóng đầu ghi** để thông báo hết input khi giao thức yêu cầu EOF. Nếu không, child có thể chờ vô thời hạn.

**waitFor() không tạo timeout tự động.** Nếu child có nguy cơ treo, dùng biến thể waitFor(timeout, unit), xử lý trường hợp quá hạn và lựa chọn chấm dứt tiến trình theo yêu cầu ứng dụng. Timeout cũng không thay thế việc quản lý các luồng đang đọc.

## 9. Encoding và tính toàn vẹn dữ liệu

Việc chuyển hướng chỉ thay đổi **kết nối dữ liệu**, không tự chọn charset và không tự hiểu định dạng file. Java String phải được **encode** thành byte khi ghi ra kênh; byte từ stdin phải được **decode** nếu chương trình muốn xử lý dưới dạng ký tự.

Ví dụ **InputStreamReader(System.in, StandardCharsets.UTF_8)** có nghĩa là ứng dụng tin rằng stdin đang mang văn bản UTF-8. Nếu file thực tế là Windows-1252 hoặc UTF-16LE mà chương trình giải mã như UTF-8, chữ có thể lỗi hoặc ký tự bị thay thế. Ngược lại, các luồng stdout/stderr dùng PrintStream có encoding riêng theo cấu hình runtime; nếu bên nhận cần text Unicode, hãy thống nhất encoding hoặc ghi bằng OutputStreamWriter với charset tường minh.

Nếu stdout chứa **ảnh, ZIP hoặc dữ liệu binary**, đừng đọc bằng Reader rồi ghi thành String. Hãy truyền nguyên byte bằng InputStream/OutputStream; ví dụ input.transferTo(output) sao chép byte mà không decode thành ký tự. Đối với text, dùng BufferedReader/BufferedWriter với charset đúng.

Chú ý **buffering**: khi chương trình ghi vào pipe hoặc file, dữ liệu có thể nằm trong bộ đệm trước khi được gửi tới kênh dưới. Flush thích hợp có thể cần thiết với giao thức tương tác để phía nhận thấy dữ liệu kịp thời. Nhưng flush không bảo đảm file đã được đồng bộ bền vững lên đĩa hoặc phía nhận đã xử lý xong.

**Không coi stderr là dữ liệu của stdout** chỉ vì chúng cùng hiển thị trên màn hình. Nếu cần parse nội dung stdout bằng ứng dụng khác, hãy chọn dữ liệu có cấu trúc ở stdout và chẩn đoán ở stderr từ ngay thiết kế.

## 10. System.setIn/out/err không phải redirect OS của process con

Java có thể thay đối tượng kênh chuẩn trong **JVM hiện tại**:

~~~java
System.setIn(otherInputStream);
System.setOut(otherPrintStream);
System.setErr(otherPrintStream);
~~~

Đây là **mảnh code minh họa**: biến otherInputStream thuộc InputStream, còn otherPrintStream thuộc PrintStream, do chương trình cung cấp. Nó thay các đối tượng mà mã Java truy cập qua System.in/out/err. Vì tác động trên toàn JVM, nhiều thread hoặc thư viện cùng sử dụng System.out có thể bị ảnh hưởng.

Tuy nhiên, **System.setOut không tương đương chuyển hướng stdout ở cấp hệ điều hành** và không tự cấu hình stdout của mọi tiến trình con đã khởi động. Mã native hoặc thư viện giữ tham chiếu đến luồng cũ có thể không tuân theo luồng mới. Muốn điều khiển kênh chuẩn của **tiến trình con**, ưu tiên ProcessBuilder; muốn chuyển hướng khi người dùng chạy một lệnh, dùng shell.

| Phương án | Phạm vi | Mục đích |
| --- | --- | --- |
| Bash/cmd redirection | Tiến trình được shell khởi chạy | Chạy chương trình qua file hoặc pipe |
| ProcessBuilder redirects | Tiến trình con được Java tạo | Thu output, cấp input, lưu log, kế thừa terminal |
| System.setIn/out/err | Các đối tượng kênh chuẩn của JVM hiện tại | Thay I/O mà mã Java hiện tại tham chiếu |
| System.console() | API truy cập console tương tác nếu tồn tại | Hỏi/đọc console, không thay thế redirect |

**System.console() có thể trả null** khi chạy qua IDE, CI, pipeline hoặc khi môi trường không có console thích hợp. Không dùng riêng giá trị null để khẳng định cụ thể stdout đang được chuyển vào file nào.

## 11. Tình huống thực tế

**Xuất kết quả và giữ lỗi riêng:** Một công cụ sinh JSON nên in JSON ra stdout và lỗi parse/tham số ra stderr. Khi dùng redirect, bên gọi nhận file JSON sạch và vẫn có file lỗi để điều tra.

**Gọi bộ biên dịch từ Java:** Parent dùng ProcessBuilder với danh sách lệnh/đối số; redirect stdout và stderr sang file hoặc đọc đồng thời; kiểm tra exit code để biết compilation thành công. Có log không đồng nghĩa exit code bằng 0.

**Ghi log lâu dài:** Có thể dùng Redirect.appendTo(logFile) khi cần giữ log cũ, nhưng phải xem xét tăng trưởng file và cách xoay vòng log; chuyển hướng không tự quản lý dung lượng.

**Lệnh cần nhập dữ liệu:** Nếu stdin từ file, dùng redirectInput(file). Nếu parent cần gửi dữ liệu động, để stdin là PIPE, ghi vào process.getOutputStream() và đóng đầu ghi đúng lúc. Nếu child cần tương tác với terminal, cân nhắc inheritIO().

**Pipeline nhiều tiến trình:** Shell có thể nối stdout của tiến trình A vào stdin của tiến trình B. Trong Java, có ProcessBuilder.startPipeline(...) cho một số cấu hình pipeline; vẫn phải quản lý lỗi stderr, mã thoát của từng process và vòng đời toàn pipeline. Đừng mặc định mã thoát của tiến trình cuối phản ánh mọi lỗi ở các giai đoạn trước nếu giao thức không định nghĩa điều đó.

## 12. Các lỗi thường gặp và cách tránh

| Sai lầm | Hệ quả | Cách đúng |
| --- | --- | --- |
| Dùng >file rồi tưởng stderr cũng vào file | Lỗi vẫn đi tới stderr cũ | Thêm 2>file khác hoặc 2>&1 |
| Đảo thứ tự >file và 2>&1 | Đích stderr không như dự kiến | Xác định redirect trái sang phải |
| Gọi waitFor trước khi drain các PIPE lớn | Child có thể chặn, parent đợi mãi | Drain đồng thời hoặc redirect file |
| Không đóng stdin PIPE khi hoàn tất | Child có thể tiếp tục chờ EOF | Đóng getOutputStream() sau khi ghi xong |
| Coi có stderr là exit code thất bại | Đánh giá trạng thái sai | Đọc cả exit code và hợp đồng chương trình |
| Gộp stderr vào stdout JSON | Output có thể không parse được | Giữ hai luồng riêng |
| Đưa dấu > vào ProcessBuilder như đối số | Child nhận chuỗi >, không được redirect | Dùng redirectOutput |
| Thay System.out rồi tưởng child cũng thay | Child có kênh chuẩn OS riêng | Cấu hình ProcessBuilder |
| Mặc định stdout là Unicode đúng mọi nơi | Encoding có thể khác nhau | Xác định charset ở hai đầu |
| Dùng Reader/Writer cho file ảnh qua PIPE | Có thể đổi byte, hỏng file | Dùng luồng byte |
| Đọc từ stdout bằng getInputStream sau redirectOutput(file) | Stream tương ứng không còn dữ liệu để đọc | Đọc file đã redirect hoặc giữ PIPE |
| Quên đóng stream hoặc cleanup khi lỗi | Rò rỉ tài nguyên/file tạm | try-with-resources, finally |

## 13. Bài tập tự kiểm tra

**Câu 1.** Nếu chạy java App >out.txt 2>err.txt, System.out và System.err đi đâu?  
**Đáp án:** System.out đi vào out.txt; System.err đi vào err.txt. System.in giữ nguồn cũ nếu không chuyển hướng riêng.

**Câu 2.** java App >both.txt 2>&1 khác java App 2>&1 >both.txt thế nào?  
**Đáp án:** Lệnh đầu nối stderr vào đích stdout đã đổi sang file; lệnh sau sao chép đích stdout ban đầu cho stderr rồi mới đổi stdout, nên lỗi có thể ở terminal.

**Câu 3.** Vì sao Process.getOutputStream() của Java parent lại viết vào stdin của child?  
**Đáp án:** Tên phương thức lấy theo hướng nhìn của parent: đó là output stream từ parent **đến** input của child.

**Câu 4.** Process.waitFor() có bảo đảm không bị kẹt khi child in nhiều lỗi không?  
**Đáp án:** Không. Nếu stderr PIPE đầy nhưng parent không đọc, child có thể chặn. Cần drain stderr đồng thời hoặc redirect ra file/gộp.

**Câu 5.** Nếu parent muốn parse JSON từ stdout của child, có nên bật redirectErrorStream(true) không?  
**Đáp án:** Thường không. Nó trộn stderr với stdout, có thể làm JSON mất tính hợp lệ.

**Câu 6.** Chương trình ghi ERR:warning lên stderr nhưng exit code 0 thì bắt buộc thất bại?  
**Đáp án:** Không. Exit code và nội dung stderr được định nghĩa riêng trong hợp đồng của công cụ.

**Câu 7.** System.setOut(newStream) có tự đổi stdout của mọi process khác không?  
**Đáp án:** Không. Nó đổi tham chiếu System.out của JVM hiện tại; ProcessBuilder hoặc shell mới cấu hình stdio của child process.

**Câu 8.** Dữ liệu nhị phân được redirect qua file hay PIPE có tự biến thành văn bản không?  
**Đáp án:** Không. Redirect là nối byte giữa các kênh, còn ý nghĩa byte phụ thuộc định dạng và encoding; cần giữ byte nguyên vẹn nếu là binary.

## 14. API Java tham khảo

- [Java SE 17 — System](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html): System.in, System.out, System.err và setIn/setOut/setErr.
- [Java SE 17 — ProcessBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ProcessBuilder.html): redirectInput, redirectOutput, redirectError, redirectErrorStream, inheritIO.
- [Java SE 17 — ProcessBuilder.Redirect](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ProcessBuilder.Redirect.html): PIPE, INHERIT, DISCARD, from, to, appendTo.
- [Java SE 17 — Process](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Process.html): getInputStream, getOutputStream, getErrorStream, waitFor.

**Điểm cốt lõi:** Stdin, stdout và stderr là ba kênh chuẩn riêng của một tiến trình. Shell hoặc ProcessBuilder nối chúng với terminal, file hay PIPE; mã Java tiếp tục thao tác qua System.in/out/err. Muốn vận hành đúng, phải quản lý thêm charset, việc tiêu thụ PIPE, EOF đầu vào, exit code và sự khác biệt giữa log chẩn đoán và dữ liệu đầu ra.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="console-and-formatting-003">System.console và trường hợp không có console</a>

<details>
<summary>Xem chi tiết</summary>

# `System.console()` và trường hợp không có console

## Mục tiêu bài học

Một chương trình Java có thể chạy trong cửa sổ terminal, nhưng cũng có thể chạy từ IDE, qua pipe, bằng scheduler, như service, trong CI/CD hoặc container. Cách khởi chạy quyết định liệu Java có nhận được **console tương tác** hay không. Vì vậy, **`System.console()` có thể trả về `null`**, dù `System.in` và `System.out` vẫn tồn tại và hoạt động.

Sau bài này, bạn có thể:

- Phân biệt **`java.io.Console`** với ba luồng chuẩn **`System.in`**, **`System.out`**, **`System.err`**.
- Giải thích vì sao `System.console()` trả về một `Console` hoặc `null` và tránh suy luận sai chỉ từ việc chương trình có in được nội dung.
- Sử dụng `Console.readLine()`, `readPassword()`, `printf()`, `format()`, `writer()`, `reader()` và `flush()` đúng mục đích.
- Xây dựng chương trình có **fallback rõ ràng** khi chạy không có console, xử lý EOF và I/O error, đồng thời không làm lộ mật khẩu do tự động chuyển sang input có echo.
- Viết mã Java 17+ chạy được và hiểu đầu ra nào thay đổi theo môi trường.

## 1. Console khác gì `System.in`, `System.out`, `System.err`?

`java.io.Console` là API hướng tới **thiết bị console tương tác** gắn với JVM, cung cấp khả năng đọc một dòng, đọc mật khẩu với echo bị vô hiệu hóa khi có thể, ghi định dạng và tương tác text. Lấy đối tượng bằng:

```java
Console console = System.console();
```

Đây là **đoạn mã minh họa**; cần `import java.io.Console;`. Biến `console` có thể là `null`.

Ngược lại, ba luồng chuẩn là những đối tượng có sẵn:

| API | Kiểu | Chức năng |
|---|---|---|
| `System.in` | `InputStream` | Nguồn byte đầu vào chuẩn; có thể là keyboard, pipe, file hoặc EOF |
| `System.out` | `PrintStream` | Đích đầu ra chuẩn; có thể là terminal, file, pipe hoặc logger bên ngoài |
| `System.err` | `PrintStream` | Đích thông báo lỗi chuẩn; có thể chuyển hướng riêng |
| `System.console()` | `Console` hoặc `null` | Giao diện console tương tác nếu JVM/môi trường cung cấp |

**Không có quy tắc “có `System.in` thì phải có `Console`”**. Java vẫn có thể đọc từ pipe hoặc file bằng `System.in` và in qua `System.out` khi `System.console() == null`.

Ví dụ sơ đồ:

```text
                          Một tiến trình JVM
               ┌────────────────────────────────┐
terminal/pipe → │ System.in       (luồng byte)   │
terminal/file ← │ System.out      (luồng byte)   │
terminal/file ← │ System.err      (luồng byte)   │
               │                                │
console thực ↔ │ System.console() (có thể null) │
               └────────────────────────────────┘
```

`System.console()` phụ thuộc **cách JVM được khởi chạy và môi trường nền tảng**. Một JVM chạy từ dòng lệnh tương tác, không chuyển hướng các stream chuẩn, **thường** có console; đây không phải cam kết cho mọi shell, terminal giả lập, container hoặc triển khai Java.

## 2. Khi nào `System.console()` trả `null`?

Các tình huống thường gặp:

1. **IDE:** chương trình chạy qua cửa sổ *Run* hoặc *Debug* của IntelliJ IDEA, Eclipse hay công cụ khác. Giao diện “console” của IDE thường chỉ là panel mô phỏng I/O, không phải thiết bị console JVM nhận diện.
2. **Chuyển hướng / pipe:** chẳng hạn `java App < input.txt`, `java App > output.txt`, hoặc `echo data | java App`. Việc chuyển hướng có thể khiến console không khả dụng, nhưng `System.in`/`System.out` vẫn hoạt động theo chuyển hướng.
3. **CI/CD, cron, scheduled task, service/daemon:** tiến trình thường không được gắn terminal tương tác.
4. **Container không gắn TTY:** tiến trình có thể có stdin/stdout để gắn log nhưng không có terminal. Việc cấp pseudo-TTY (ví dụ `docker run -it`) có thể thay đổi điều kiện, song vẫn phụ thuộc runtime/launcher.
5. **Kiểm thử tự động:** bộ test có thể thay thế `System.in`/`System.out` bằng stream trong bộ nhớ; điều đó không tạo ra `Console` cho JVM.

Không khẳng định tuyệt đối rằng **mọi lần** chạy trong IDE hoặc redirect **đều** trả `null`: hành vi là đặc điểm môi trường; **kiểm tra tại runtime** là cách đáng tin cậy:

```java
Console console = System.console();
if (console == null) {
    // Chọn chế độ phi tương tác hoặc xử lý nguồn stdin được cấu hình.
} else {
    // Có thể dùng readLine/readPassword/printf của Console.
}
```

Đây là **đoạn minh họa**, không phải chương trình độc lập. Không gọi `console.readLine()` ngay khi chưa kiểm tra `null`: sẽ gây `NullPointerException` ở môi trường không có console.

### Điều `null` không nói lên

- Không có nghĩa ứng dụng **không thể nhận input**; `System.in` có thể là một file, pipe hoặc stream test.
- Không có nghĩa ứng dụng **không thể in ra output**; `System.out` và `System.err` vẫn có thể gửi đến logging agent/file.
- Không có nghĩa **mạng hoặc database** không hoạt động; `Console` chỉ là một phương tiện tương tác text.
- Không có nghĩa **không có người dùng**: IDE có thể vẫn cho nhập chữ vào `System.in` mà không cấp `Console`.
- Ngược lại, `Console` khác `null` cũng không có nghĩa luôn có người sẵn sàng nhập, hoặc một lời gọi đọc không thể kết thúc bằng EOF/lỗi.

## 3. API chính của `java.io.Console`

| Phương thức | Ý nghĩa |
|---|---|
| `System.console()` | Lấy console gắn với JVM hoặc `null` |
| `console.readLine()` | Đọc một dòng, không bao gồm line terminator; có thể trả `null` khi EOF |
| `console.readLine("Name: ")` | In prompt dạng format rồi đọc dòng |
| `console.readPassword()` | Đọc mật khẩu với echo bị vô hiệu hóa khi có thể, trả `char[]`, có thể `null` khi EOF |
| `console.readPassword("Password: ")` | In prompt rồi đọc `char[]` |
| `console.printf(format, args...)` | Xuất văn bản có định dạng; trả về `Console` |
| `console.format(format, args...)` | Tương tự `printf` |
| `console.writer()` | Trả `PrintWriter` liên kết console để ghi text |
| `console.reader()` | Trả `Reader` liên kết console để đọc text |
| `console.flush()` | Flush writer của console |

Cả `readLine` và `readPassword` có phiên bản không prompt hoặc prompt dùng format string. `readLine("Name: ")` là cú pháp dùng phiên bản có tham số format, và định dạng sẽ được xử lý tương tự một lời gọi format của console; nếu prompt chứa `%` muốn in nguyên văn qua định dạng, cần escape thành `%%` hoặc chuyển định dạng phù hợp.

**Giới hạn lỗi:** `Console` có thể phát sinh `java.io.IOError` nếu có lỗi I/O bên dưới, ngoài các tình huống kết thúc input bình thường (`null` ở `readLine`/`readPassword`). Không nên gộp EOF với chuỗi rỗng:

- `readLine() == null`: hết input.
- `readLine().isEmpty()`: người dùng đã nhập dòng trống.
- `readPassword() == null`: hết input.
- `readPassword().length == 0`: người dùng nhập mật khẩu trống.

### Dùng formatter đúng chỗ

```java
console.printf("Items: %d, total: %.2f%n", 3, 19.75);
```

Đây là **đoạn minh họa**, giả sử `console` hợp lệ; `%n` tạo newline theo hệ thống, `%d` cho số nguyên, `%.2f` cho số thực. Formatter dùng quy tắc locale trong quá trình format, nên dạng dấu thập phân có thể khác nhau theo locale nếu không được chỉ định rõ. Đừng viết test yêu cầu nguyên văn dấu `.` cho mọi locale khi không kiểm soát locale.

## 4. Ví dụ Java 17: nhận biết console và fallback nhập tên

Chương trình sau hỗ trợ cả chế độ **interactive** khi có console lẫn chế độ **stdin** khi không có console. Nó không coi việc thiếu console là lỗi đối với thông tin thông thường như tên người dùng.

Lưu **`ConsoleAwareDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConsoleAwareDemo {
    public static void main(String[] args) throws IOException {
        Console console = System.console();
        String name;

        if (console != null) {
            console.printf("Mode: console%n");
            name = console.readLine("Name: ");
        } else {
            System.out.println("Mode: standard input");
            System.out.print("Name: ");
            System.out.flush();

            // Không đóng reader ở đây: nó bao System.in thuộc tiến trình.
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8)
            );
            name = reader.readLine();
        }

        if (name == null) {
            System.out.println();
            System.out.println("No input (EOF).");
            return;
        }

        name = name.strip();
        if (name.isEmpty()) {
            System.out.println("Name cannot be blank.");
            return;
        }

        System.out.println("Hello, " + name + "!");
    }
}
```

Biên dịch:

```bash
javac ConsoleAwareDemo.java
```

Chạy từ terminal tương tác:

```bash
java ConsoleAwareDemo
```

Nếu có console, một phiên tương tác điển hình (các dòng người dùng gõ có thể được terminal echo):

```text
Mode: console
Name: An
Hello, An!
```

Nếu chạy trong môi trường `System.console() == null`, ví dụ nhận `An` qua stdin, stdout sau khi nhập dòng sẽ là:

```text
Mode: standard input
Name: Hello, An!
```

Ở trường hợp nhập qua pipe, terminal không nhất thiết hiển thị lại `An` như khi người dùng gõ; `Name: ` được in không kèm newline, nên `Hello, An!` xuất hiện trên cùng dòng. Điều kiện hiển thị tùy cách chạy, nhưng **cả hai nhánh đều xử lý một chuỗi tên và EOF**. Trong shell Unix có thể thử `printf 'An\n' | java ConsoleAwareDemo`; trên PowerShell có thể thử `'An' | java ConsoleAwareDemo`. Nếu môi trường thực tế vẫn cấp `Console`, chương trình sẽ chọn nhánh console; ví dụ stdout nhánh fallback chỉ áp dụng khi console là `null`.

Để kiểm tra chính xác và độc lập môi trường phần “đọc dòng”, hãy tách việc đọc từ một `Reader` thành hàm nhận dependency; khi test truyền `StringReader` thay vì cố ép `System.console()` thành một giá trị.

### Tại sao code không đóng `System.in`?

Đóng `BufferedReader` sẽ đóng luôn `InputStreamReader` và thường cả underlying `System.in`. Trong một lệnh CLI nhỏ mà chương trình sắp kết thúc, chuyện này có thể không đáng kể; nhưng trong ứng dụng/library cần tiếp tục đọc stdin, việc đóng global stream có thể phá các tác vụ khác. Vì thế **xác định ai sở hữu resource** trước khi dùng try-with-resources trên wrapper của `System.in`.

Khi đọc một file do ứng dụng tự mở bằng `Files.newBufferedReader(path, charset)`, ngược lại, caller thường sở hữu reader và **nên đóng** bằng try-with-resources.

### Chọn charset cho fallback

`System.in` là byte stream; `InputStreamReader(System.in, StandardCharsets.UTF_8)` giả định nguồn input thực sự được encode UTF-8. Nếu terminal, pipe hoặc file dùng charset khác, có thể xuất hiện ký tự lỗi. Hợp đồng CLI cần ghi rõ input encoding hoặc tuân theo charset terminal/phần mềm đích. Việc chọn UTF-8 ở ví dụ chỉ phù hợp khi nguồn được quy định là UTF-8; nó không tự chuyển byte sai charset thành đúng tiếng Việt.

## 5. Vì sao `readPassword()` đặc biệt?

`Console.readPassword()` cố gắng đọc mật khẩu khi **vô hiệu hóa echo** từ console (để ký tự người dùng gõ không hiển thị). Mật khẩu được trả về dưới dạng **`char[]`** thay vì `String` nhằm cho phép code xóa nội dung mảng khi dùng xong.

- `String` bất biến; ứng dụng không có cách đáng tin để chủ động ghi đè các ký tự bên trong một instance `String`.
- `char[]` có thể được ghi đè bằng `Arrays.fill(secret, '\0')`.
- Điều này **giảm thời gian mảng do ứng dụng nắm giữ còn chứa mật khẩu**, nhưng không phải bảo đảm mọi bản sao của mật khẩu biến mất khỏi JVM hoặc thư viện, hệ điều hành và phần cứng.
- Không log `char[]`, không in mật khẩu lên console, không lưu nó lâu hơn mức cần thiết.
- Không convert sang `String` chỉ để thao tác tiện hơn nếu thư viện nhận được `char[]` hoặc API xác thực an toàn hơn.

### Ví dụ Java 17: đọc mật khẩu khi có console, từ chối fallback echo

Lưu **`PasswordConsoleDemo.java`**:

```java
import java.io.Console;
import java.util.Arrays;

public class PasswordConsoleDemo {
    public static void main(String[] args) {
        Console console = System.console();

        if (console == null) {
            System.err.println(
                "Secure interactive console is unavailable."
            );
            return;
        }

        char[] password = console.readPassword("Password: ");
        if (password == null) {
            console.printf("Input ended.%n");
            return;
        }

        try {
            if (password.length == 0) {
                console.printf("Empty password was entered.%n");
            } else {
                // Demo: nơi này sẽ truyền char[] cho hàm xác thực tin cậy.
                console.printf("Password input received.%n");
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
```

Biên dịch và chạy:

```bash
javac PasswordConsoleDemo.java
java PasswordConsoleDemo
```

Nếu có console, chương trình in prompt, nhận mật khẩu không echo rồi in `Password input received.` hoặc thông báo chuỗi trống. Nếu không có console, chương trình chỉ in ra stderr:

```text
Secure interactive console is unavailable.
```

Phương án này **cố ý không tự chuyển sang `Scanner(System.in)` để đọc mật khẩu**. Đối với thông tin bí mật, fallback có echo có thể khiến mật khẩu lộ trong terminal, log, transcript hoặc qua công cụ theo dõi input. Đừng tự động bật fallback chỉ vì `System.console() == null`.

Trong ứng dụng thực tế, phần xác thực sẽ gọi API thích hợp, xử lý lỗi và xoá mảng trong `finally` kể cả khi xác thực thất bại. Không đem chương trình minh họa này làm hệ thống kiểm tra mật khẩu hoàn chỉnh: nó chỉ trình diễn vòng đời input và không xác thực độ mạnh/đúng sai của mật khẩu.

### Nếu automation phải cung cấp secret?

Đây là **thiết kế khác** với nhập mật khẩu tương tác. Tùy hệ thống có thể chọn secret manager, cơ chế cấp credential của môi trường chạy, file descriptor bảo vệ, hoặc API chuyên dụng. Không tự đọc qua stdin như mật khẩu ở chế độ echo rồi giả định rằng nó an toàn; cũng tránh truyền secret trên command-line argument vì có thể bị ghi vào process listing, logs hoặc lịch sử lệnh. Biến môi trường cũng có rủi ro bị lộ trong cấu hình, log và công cụ quản trị, nên cần tuân theo cơ chế secret của nền tảng.

Một CLI hợp lý có thể hỗ trợ các mode được khai báo rõ ràng:

```text
--interactive        yêu cầu Console; không có -> báo lỗi
--input-file <path>  nhận dữ liệu thường từ file, charset định rõ
--batch              không prompt; lấy cấu hình đã được cấp
```

Đây là **minh họa giao diện**, không phải các option chuẩn tự có trong JVM. Đối với secret, nguồn cấp phải được định nghĩa riêng và đánh giá an toàn theo môi trường triển khai.

## 6. EOF, dòng trống và lỗi I/O

Một lỗi phổ biến là code giả định mọi lần đọc trả `String`. Nhưng dù dùng `Console.readLine()` hay `BufferedReader.readLine()`, caller cần xử lý **EOF**.

```text
readLine() == null    → Không còn dữ liệu để đọc (EOF)
readLine().isEmpty()  → Có dòng được đọc, nhưng nội dung rỗng
readLine() == "  "    → Có dòng chỉ chứa khoảng trắng
```

Nếu muốn cấm input trắng, sau khi đã kiểm tra `null` mới gọi `isBlank()` hoặc `strip()`. Không viết:

```java
String name = console.readLine().strip();
```

Đoạn **phản ví dụ** này có hai lỗi: `console` có thể `null`, và `readLine()` có thể `null`. Thứ tự đúng là kiểm tra cả hai trước khi xử lý chuỗi.

Ví dụ vòng lặp trong trường hợp đã xác minh có console:

```java
String line;
while ((line = console.readLine("> ")) != null) {
    if (line.equalsIgnoreCase("quit")) {
        break;
    }
    console.printf("You typed: %s%n", line);
}
```

Đây là **đoạn minh họa**, `console` là đối tượng `Console` khác `null`. Khi EOF, vòng lặp dừng; khi người dùng nhập `quit`, vòng lặp cũng dừng. Nếu prompt yêu cầu nhập số, hãy đọc text rồi parse với `Integer.parseInt` và xử lý `NumberFormatException`, thay vì bỏ qua input lỗi.

`Console` có thể ném `IOError` khi lỗi I/O. `BufferedReader.readLine()` thường báo lỗi qua checked `IOException`. Phải phân biệt ngoại lệ thật với EOF, có chính sách exit code và thông báo phù hợp trong ứng dụng CLI.

### Ctrl+D, Ctrl+Z và Ctrl+C có phải luôn giống nhau?

Không. Tùy terminal/OS:

- Ctrl+D trên nhiều terminal kiểu Unix khi dòng đang trống có thể khiến `readLine()` thấy EOF.
- Ctrl+Z rồi Enter thường được dùng để báo EOF trong console Windows.
- Ctrl+C thường tạo interrupt/signal hoặc yêu cầu dừng chương trình, **không đồng nghĩa** với việc `readLine()` chắc chắn trả `null`.

Không xây dựng logic nghiệp vụ phụ thuộc mù quáng vào một phím cụ thể trên mọi hệ điều hành; hãy xử lý giá trị API trả về và chính sách dừng của tiến trình.

## 7. Các lựa chọn khi không có console

| Nhu cầu | Hướng xử lý phù hợp | Vì sao |
|---|---|---|
| CLI tương tác cần hỏi câu đơn giản | Nếu có `Console`, dùng `readLine`; nếu không, có thể `BufferedReader(System.in)` tùy hợp đồng | Chạy được trong IDE hoặc pipe |
| CLI nhận dữ liệu từ pipeline/file | Dùng `System.in`, reader charset rõ ràng, parse theo định dạng xác định | Không cần console tương tác |
| CLI cần nhập mật khẩu không echo | Yêu cầu `Console.readPassword` và báo lỗi khi không có console, hoặc thiết kế nguồn secret chuyên dụng | Tránh lộ bí mật do fallback |
| Tool chạy trong CI hoặc cron | Cấu hình bằng tham số/file/nguồn config được duyệt; không đợi prompt | Non-interactive, tránh chạy treo |
| Server/web application | Request, config, secrets service, logging thay cho prompt | Server không phụ thuộc terminal |
| Công cụ có nhiều loại input | Tách `Reader`/`InputStream` và logic xử lý để unit test | Giảm ràng buộc với môi trường |
| Thông báo lỗi cho người vận hành | Dùng `System.err` hoặc framework logging | stderr có thể được capture riêng |
| Output được pipe sang chương trình khác | In đúng cấu trúc, giữ prompt/status ở stderr khi thiết kế giao thức CLI | Tránh làm bẩn dữ liệu stdout |

Một chương trình dùng stdin theo giao thức batch **không nhất thiết phải coi thiếu console là lỗi**. Điều nguy hiểm là **tự động đổi sang đường nhập kém an toàn cho mật khẩu** hoặc để chương trình chờ vô hạn một prompt không bao giờ có người trả lời.

### Lưu ý `Scanner` và `BufferedReader`

`Scanner` tiện cho đọc dữ liệu dạng token và parse kiểu nguyên thủy, nhưng `next()` và `nextLine()` có ngữ nghĩa khác nhau; việc trộn hai phương thức thường để sót newline trong input. Trong CLI xử lý dòng, `BufferedReader.readLine()` thường rõ ràng hơn.

`Scanner.close()` trên `System.in` đóng luôn nguồn toàn cục. Vì vậy, nếu scanner chỉ là wrapper của input được quản lý ở nơi khác, cần định nghĩa ownership cẩn thận. Nếu scan file tự mở, dùng try-with-resources.

Không thay thế `Console.readPassword()` bằng `Scanner.nextLine()` rồi giả định tính năng ẩn ký tự vẫn được giữ. `Scanner` không cung cấp bảo đảm disabling terminal echo giống Console password API.

## 8. Kiểm thử khi hành vi phụ thuộc môi trường

Kiểm thử `System.console()` trực tiếp trong JUnit thường khó ổn định vì framework và runner có thể không gắn terminal. Thay vì ép mọi test chạy với TTY, hãy **tách nguồn đọc khỏi logic ứng dụng**.

### Ví dụ Java 17: kiểm thử fallback bằng stream trong bộ nhớ

Lưu **`LineInputDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class LineInputDemo {
    static String greeting(BufferedReader reader) throws IOException {
        String line = reader.readLine();

        if (line == null) {
            return "No input (EOF).";
        }
        if (line.isBlank()) {
            return "Blank name.";
        }
        return "Hello, " + line.strip() + "!";
    }

    public static void main(String[] args) throws IOException {
        try (BufferedReader normal =
                 new BufferedReader(new StringReader("An\n"));
             BufferedReader blank =
                 new BufferedReader(new StringReader(" \n"));
             BufferedReader eof =
                 new BufferedReader(new StringReader(""))) {
            System.out.println(greeting(normal));
            System.out.println(greeting(blank));
            System.out.println(greeting(eof));
        }
    }
}
```

Chạy:

```bash
javac LineInputDemo.java
java LineInputDemo
```

Stdout **không phụ thuộc terminal**:

```text
Hello, An!
Blank name.
No input (EOF).
```

`StringReader` mô phỏng input để kiểm tra xử lý dòng trống và EOF; đây không phải thử nghiệm xem runtime có `Console` hay không. Ở ứng dụng thật, đưa abstraction input/output vào logic nghiệp vụ là cách tách biệt transport với validation. Khi test chế độ console, có thể integration-test trong môi trường PTY phù hợp, song không bắt buộc mọi unit test phải được gắn TTY.

## 9. Những tình huống gây hiểu nhầm

### “IDE có tab Console, tại sao `System.console() == null`?”

Tên tab UI của IDE không quyết định đối tượng `Console` JVM. IDE có thể gửi văn bản tới stdout và nhận stdin thông qua stream riêng nhưng không cấp console hệ thống theo cách JVM nhận diện. Dùng fallback stdin cho dữ liệu thường hoặc chạy từ terminal phù hợp nếu cần `readPassword`.

### “Có terminal trên màn hình thì chắc chắn có Console?”

Không. Chuyển hướng, pseudo-terminal, cách tiến trình con được khởi chạy và cấu hình runtime đều ảnh hưởng. Kiểm tra `System.console()` tại runtime; không suy từ việc có cửa sổ terminal.

### “`System.console() == null` có nghĩa `System.in == null`?”

Không. `System.in` thường vẫn là `InputStream`; nó có thể lập tức đến EOF, có dữ liệu được pipe hoặc đã bị thay bằng test fixture. Sự tồn tại của stream không bảo đảm có dữ liệu.

### “Nếu `readPassword()` trả `char[]`, mật khẩu đã hoàn toàn an toàn?”

Không. `char[]` cho phép xóa **mảng hiện có**, nhưng input có thể đã qua các buffer bên dưới hoặc bị copy bởi thư viện. Bảo vệ secret cần xem xét toàn bộ vòng đời, log, crash dump, cách truyền và xác thực. Dùng `Arrays.fill` trong `finally`, đồng thời tránh tạo `String` không cần thiết.

### “In prompt bằng `System.out.print` rồi đọc dòng có tự hiện ngay không?”

Không nên phụ thuộc hoàn toàn vào buffering của các wrapper và sink. Nếu muốn prompt không kết thúc newline được đẩy ra ngay, gọi `System.out.flush()` hoặc writer phù hợp. Khi stdout bị chuyển hướng sang file/pipe, prompt có thể xuất hiện ở nơi không có người đọc; CLI nên có chế độ `--batch` hoặc không prompt khi nguồn input không tương tác.

### “Gọi `console.reader()` và `new Scanner(System.in)` xen kẽ có ổn không?”

Không nên nếu chúng cùng liên quan một nguồn terminal. Hai wrapper có thể buffer trước dữ liệu khiến thứ tự đọc khó dự đoán, hoặc xử lý charset/newline khác nhau. Chọn một phương pháp làm chủ input trong một luồng xử lý. `Console` còn có cơ chế đồng bộ dành cho read/write console; đừng tự giả định trộn mọi đối tượng reader đều an toàn.

### “Không có console nên lấy password từ dòng tham số là được?”

Tham số dòng lệnh có thể hiện trong process listing, diagnostic, crash report và lịch sử shell. Không dùng giải pháp này như fallback tự động. Chọn nguồn secret được nền tảng hỗ trợ và giới hạn quyền truy cập.

### “`Console.readLine()` luôn trả text theo UTF-8?”

Đừng giả định. Encoding/decoding của console liên quan cấu hình terminal và JVM; `System.in` đọc byte cũng cần reader với charset đúng. Với file/pipe có định dạng rõ ràng, dùng charset tường minh để tránh sai tiếng Việt.

## 10. Quy trình triển khai CLI ổn định

1. **Quy định chế độ chạy.** Chương trình tương tác hay batch? Có cần bí mật? Dữ liệu qua stdin là dòng UTF-8, JSON hay định dạng khác?
2. **Kiểm tra console một lần tại điểm chọn chế độ.** `Console console = System.console();` rồi chọn nhánh phù hợp; không gọi API của `console` nếu `null`.
3. **Dữ liệu thường:** nếu cho phép pipe hoặc IDE, dùng `BufferedReader` trên `System.in` với charset phù hợp. Nếu không có dữ liệu, xử lý EOF để thoát rõ ràng.
4. **Mật khẩu:** dùng `readPassword` khi có console hoặc nguồn secret non-interactive được thiết kế trước. Không tự downgrade sang nhập hiện ký tự.
5. **Tách input, validation và output.** Logic xử lý nhận kiểu `String` hoặc object đã parse; để lớp UI/CLI tương tác với Console/Reader. Dễ kiểm thử độc lập.
6. **Không chờ vô hạn trong môi trường batch.** Nếu thiếu tham số bắt buộc và không cho phép prompt, báo thiếu cấu hình, chọn exit code phù hợp.
7. **Xử lý lỗi và resource ownership.** Phân biệt EOF với lỗi I/O. File reader tự mở cần đóng, wrapper trên `System.in` có thể không nên đóng nếu tiến trình còn dùng.
8. **Tách stdout/stderr có chủ đích.** Nếu stdout được dùng như data protocol cho shell pipe, prompt/error/status nên được định tuyến rõ ràng để không phá dữ liệu.
9. **Kiểm chứng môi trường thực tế.** Chạy qua IDE, terminal tương tác, chuyển hướng stdin/stdout và CI/container nếu sản phẩm phải hỗ trợ các chế độ đó. Đừng biến kết quả một hệ điều hành thành cam kết xuyên nền tảng.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Trong IDE, chương trình in được `Hello` qua `System.out`. Điều đó có chứng minh `System.console() != null` không?

**Đáp án:** Không. stdout có thể được IDE capture độc lập với console. Phải kiểm tra `System.console()`.

**Câu 2.** Khi `System.console() == null`, có còn cách đọc dữ liệu thường do shell pipe vào không?

**Đáp án:** Có, có thể đọc `System.in` bằng `BufferedReader(new InputStreamReader(System.in, charset))`, xử lý EOF và charset theo hợp đồng.

**Câu 3.** Vì sao không viết `System.console().readLine()` trực tiếp?

**Đáp án:** `System.console()` có thể `null`, gây `NullPointerException`; bản thân `readLine()` cũng có thể trả `null` khi EOF.

**Câu 4.** `Console.readPassword()` khác `Scanner.nextLine()` ở điểm quan trọng nào?

**Đáp án:** `readPassword()` có cơ chế nhập không echo khi có thể và trả về `char[]` để chủ động xóa; `Scanner.nextLine()` không cung cấp bảo đảm đó.

**Câu 5.** Nếu nhập dòng trắng và EOF đều dẫn đến không có tên người dùng, có nên coi chúng là cùng một trạng thái?

**Đáp án:** Không. Dòng trống là **một dòng được nhập**; EOF là **kết thúc nguồn input**. Quy trình prompt/thoát/validation thường xử lý hai trạng thái khác nhau.

**Câu 6.** Muốn kiểm thử validation của CLI mà CI không có terminal, có phải tìm cách tạo `Console` trong mọi unit test không?

**Đáp án:** Không. Tách logic đọc/validation bằng `Reader` hay interface, dùng `StringReader` cho unit test và kiểm thử tích hợp với console thật khi cần.

**Ghi nhớ:** `System.console()` **có thể trả `null`** và đây là tình huống vận hành bình thường. `System.in/out/err` là các luồng chuẩn vẫn có thể hoạt động. Với text thông thường, thiết kế fallback rõ ràng qua stdin; với mật khẩu, giữ yêu cầu nhập không echo hoặc một nguồn secret an toàn đã xác định; luôn xử lý EOF, lỗi và khác biệt giữa môi trường tương tác với batch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="console-and-formatting-004">Console.reader, writer, readLine và readPassword</a>

<details>
<summary>Xem chi tiết</summary>

# Console.reader, writer, readLine và readPassword trong Java

> **Chủ đề:** Java Core I/O → **Console và định dạng**. Bài này giải thích cách lấy thiết bị console tương tác, đọc/ghi ký tự bằng `reader()` và `writer()`, nhập từng dòng bằng `readLine()`, nhập dữ liệu bí mật bằng `readPassword()`, cùng khác biệt giữa console thực và standard streams khi chạy trong IDE, CI hoặc qua pipe. Ví dụ dùng API của Java 17+; `java.io.Console` đã có từ Java 6.

## 1. Console là gì, có giống System.in và System.out không?

`java.io.Console` là API Java SE cho thiết bị **console dạng ký tự** gắn với JVM, nếu có. Nó thích hợp với ứng dụng chạy tương tác từ terminal: hiện lời nhắc, đọc tên người dùng, chọn thao tác, nhập mật khẩu mà terminal không echo lại nội dung.

Lấy Console bằng:

~~~java
import java.io.Console;

Console console = System.console();
if (console == null) {
    System.err.println("Không có console tương tác cho tiến trình này.");
    // Chọn cách làm việc khác hoặc dừng tác vụ tương tác.
}
~~~

Đây là **fragment**, nằm trong một phương thức. Không thể tự tạo Console bằng `new Console()`: phương thức phổ biến là `System.console()`. JVM có một đối tượng Console gắn với console của tiến trình nếu môi trường cung cấp.

**Phân biệt quan trọng:**

| Đối tượng/API | Công dụng | Khi chạy từ IDE/CI hoặc redirect |
| --- | --- | --- |
| `System.in` | Standard input dạng `InputStream` (byte) | Có thể đọc pipe/file hoặc nguồn do môi trường cấu hình, kể cả khi không có console. |
| `System.out` | Standard output dạng `PrintStream` | Có thể ghi tới terminal, file hoặc pipe; không đồng nghĩa luôn có Console. |
| `System.err` | Standard error, thường dùng cho thông báo lỗi | Có thể redirect độc lập; không bảo đảm Console tồn tại. |
| `System.console()` | Trả về `Console` nếu JVM có thiết bị console phù hợp, ngược lại `null` | Thường `null` trong nhiều IDE, trình chạy nền, môi trường CI hoặc khi chuyển hướng luồng; phải kiểm tra thực tế. |
| `Console.reader()` / `Console.writer()` | Reader/PrintWriter kiểu ký tự gắn với console | Chỉ gọi sau khi đã xác nhận `console != null`. |
| `Console.readLine()` / `readPassword()` | Đọc tương tác cấp dòng, hoặc cấp dòng với echo bị tắt | Không thay thế API đọc dữ liệu batch từ file/pipe nếu không có console. |

**Không suy diễn:** “chương trình có `System.in` tức có Console” là sai. Console phụ thuộc nền tảng và cách khởi động JVM. Trên terminal thông thường, `System.console()` hay có giá trị; trong test runner, chương trình launch bởi IDE hoặc lệnh redirect, nó thường `null`. Hành vi thực tế có thể khác theo JDK/launcher/terminal, nên code phải xử lý nhánh `null`.

## 2. Tổng quan bốn API được giao

| API | Kết quả | Đơn vị thao tác | Đặc điểm |
| --- | --- | --- | --- |
| `console.reader()` | `Reader` duy nhất gắn với console | `char`/UTF-16 code unit | Đọc ký tự trực tiếp, thích hợp parser/scanner tự quản lý. Không ẩn nội dung gõ. |
| `console.writer()` | `PrintWriter` duy nhất gắn với console | Văn bản đã mã hóa để xuất console | `print`, `println`, `printf`; nên flush khi cần prompt hiển thị ngay. |
| `console.readLine()` hoặc `readLine(fmt,args...)` | `String`, hoặc `null` khi EOF | Một dòng ký tự | Có thể hiển thị prompt, đọc tới dấu kết thúc dòng, loại bỏ ký tự kết thúc dòng. Nội dung nhập thường được echo. |
| `console.readPassword()` hoặc `readPassword(fmt,args...)` | `char[]`, hoặc `null` khi EOF | Một dòng ký tự nhạy cảm | Vô hiệu hóa echo khi nhập mật khẩu; trả mảng ký tự có thể xóa sau sử dụng. |

Các biến thể không tham số đọc một dòng/secret mà không cung cấp prompt. Các biến thể nhận `String fmt, Object... args` tạo prompt bằng cú pháp `Formatter`, ví dụ `console.readLine("Chào %s, tên bạn? ", role)`. Nếu prompt chứa ký tự `%` theo nghĩa literal trong **format string**, viết `%%` để tránh lỗi định dạng.

**Cùng một Console không có bốn nguồn I/O độc lập.** `reader()`, `writer()` và các hàm đọc/ghi cấp cao sử dụng thiết bị console chung. Tự đọc xen kẽ từ `reader()`, `System.in` hoặc nhiều wrapper có bộ đệm trên cùng nguồn có thể làm mất đồng bộ logic đọc.

## 3. `Console.reader()` — đọc console như Reader

`reader()` trả về đối tượng `java.io.Reader` duy nhất gắn với Console. Nó dành cho chương trình cần logic phân tích ký tự riêng, chẳng hạn parser hoặc `Scanner` xử lý token. Các phương thức của `Reader` nhận/trả dữ liệu theo **UTF-16 code unit**, chứ không phải byte thô.

~~~java
// Fragment: phải lấy được console trước khi dùng.
Console console = System.console();
if (console != null) {
    Reader reader = console.reader();
    int codeUnit = reader.read();
    if (codeUnit == -1) {
        console.writer().println("Không còn dữ liệu đầu vào.");
    } else {
        console.writer().printf("Mã char đầu tiên: U+%04X%n", codeUnit);
    }
}
~~~

Vì đây là **fragment**, cần import `java.io.Console`, `java.io.Reader`, khai báo hoặc bắt `IOException` của `Reader.read()`. Nếu người dùng gõ emoji ngoài BMP, một `read()` trả **một code unit**, không đảm bảo toàn bộ code point. `reader()` dùng để đọc dữ liệu nhập thông thường, không có cam kết tắt echo.

### 3.1. Đọc theo dòng trên Reader có khác readLine không?

Có. `Console.readLine(...)` cho tình huống prompt + một dòng thông thường và trả `String`. `reader().read(...)` có thể chặn đợi người dùng hoàn tất dòng; tài liệu `Console.reader` nêu các bulk-read method sẽ không đọc vượt biên dòng trong một invocation dù buffer còn chỗ. `Reader.read()` trả `int` với `-1` ở EOF, và có thể đọc từng code unit.

Ví dụ chuyển Reader thành bộ phân tích:

~~~java
// Fragment: chỉ dùng khi console != null.
java.util.Scanner scanner =
        new java.util.Scanner(console.reader());

String command = scanner.nextLine(); // có thể ném khi không còn dòng.
~~~

`Scanner` còn có quy tắc delimiter, locale và exception của riêng nó. **Không** dùng đồng thời `scanner.nextLine()` và `console.readLine()` trên cùng console rồi giả định các buffer sẽ phối hợp chính xác. Chọn một kiểu đọc phù hợp với vòng đời tương tác. Đừng vô tình đóng scanner giữa chừng nếu code sau vẫn cần tiếp tục dùng chung console.

### 3.2. Đóng Reader do Console trả về?

Theo API Java 21, gọi `close()` trên đối tượng do `console.reader()` hoặc `console.writer()` trả về **không đóng underlying console stream**. Tuy nhiên chúng là đối tượng gắn với Console được chia sẻ, nên tránh tự ý tạo nhiều wrapper rồi đóng/đọc xen kẽ. Sở hữu tài nguyên và hành vi của wrapper (ví dụ `Scanner`, `BufferedReader`) vẫn cần xem riêng. Với CLI đơn giản, thường dùng trực tiếp `readLine()` để không phải tự quản lý stream đọc.

## 4. `Console.writer()` — viết với PrintWriter

`writer()` trả về đối tượng `PrintWriter` gắn với console. Bạn có thể:

- `writer().print(value)` để viết không xuống dòng.
- `writer().println(value)` để viết và thêm dấu xuống dòng.
- `writer().printf("Đã nhận %d mục%n", count)` để định dạng chuỗi.
- `writer().flush()` hoặc `console.flush()` để đẩy nội dung đang đệm ra thiết bị, hữu ích trước khi chờ nhập.

~~~java
// Fragment: console khác null.
java.io.PrintWriter out = console.writer();
out.println("Công cụ nhập dữ liệu");
out.printf("Phiên làm việc: %s%n", "demo");
out.print("Gõ lệnh: ");
out.flush(); // Hiển thị lời nhắc trước khi chờ.
String command = console.readLine();
~~~

Tài liệu `Console` cũng có `console.printf(...)` và `console.format(...)` để in ra console trực tiếp, cùng `console.flush()`. Nếu muốn prompt trên một dòng không có dấu xuống dòng, `console.readLine("Tên: ")` thường rõ ràng hơn tự `print` rồi `readLine()`.

**Đặc điểm của PrintWriter:** nhiều phương thức ghi của `PrintWriter` không ném trực tiếp `IOException` kiểu checked; chúng có thể ghi nhận lỗi để kiểm tra với `checkError()`. Nếu ứng dụng đòi hỏi kiểm tra nghiêm ngặt lỗi xuất, cân nhắc kiểm tra trạng thái lỗi thay vì mặc định `println()` luôn thành công. API `Console.readLine/readPassword` lại có thể ném `java.io.IOError` khi lỗi I/O.

### 4.1. Charset của Console

Console làm việc với ký tự và cần charset phù hợp với môi trường console. Từ **Java 17**, `console.charset()` trả `Charset` sử dụng bởi Console. Charset này có thể **khác** `Charset.defaultCharset()`. Do đó không nên khẳng định mọi Console chỉ dùng UTF-8 hay mặc định của chương trình quyết định cách terminal hiển thị mọi ký tự.

~~~java
// Fragment Java 17+: console != null.
console.writer().printf("Console charset: %s%n", console.charset());
console.flush();
~~~

Khi xuất tiếng Việt bị lỗi, xem xét terminal/font/locale/charset và cấu hình luồng. Không giải quyết bằng cách mù quáng chuyển byte sang `char`; `Console.writer()` đã làm việc ở tầng ký tự, còn terminal chịu trách nhiệm render.

## 5. `Console.readLine()` — đọc một dòng nhập thông thường

Hai dạng cơ bản:

~~~java
String line = console.readLine();                  // Không in prompt.
String name = console.readLine("Tên của bạn: ");  // Có prompt.
~~~

**Hợp đồng quan trọng:**

1. Phương thức đợi người dùng nhập một dòng; dữ liệu bình thường được console **echo** khi gõ.
2. Trả một `String` không chứa ký tự kết thúc dòng `\n`, `\r` hoặc cặp `\r\n`. Nó không tự `trim()` khoảng trắng đầu/cuối.
3. Khi hết input, trả `null`. Dòng rỗng người dùng nhấn Enter là **`""`**, khác hoàn toàn `null`.
4. Nếu lỗi I/O, phương thức có thể ném `IOError`; định dạng prompt sai có thể ném `IllegalFormatException`.
5. Prompt `String fmt` dùng quy tắc định dạng, không phải câu SQL hoặc command. Đừng đưa input không tin cậy thành **format string** nếu mục đích chỉ hiển thị nguyên văn.

Ví dụ nhập tên và kiểm tra EOF:

~~~java
// Fragment: console đã được xác nhận khác null.
String name = console.readLine("Tên hiển thị: ");
if (name == null) {
    console.writer().println("Đã kết thúc đầu vào.");
} else if (name.isEmpty()) {
    console.writer().println("Bạn chưa nhập tên.");
} else {
    console.writer().printf("Xin chào, %s!%n", name);
}
console.flush();
~~~

**Dòng rỗng không phải EOF.** Phân biệt chúng khi xây menu CLI. Nếu đọc số, `readLine()` trả **String**, không tự parse số; phải dùng `Integer.parseInt` với xử lý `NumberFormatException` và xác định cách xử lý khoảng trắng.

### 5.1. Lời nhắc có tham số và ký tự %

~~~java
// Fragment: định dạng prompt theo java.util.Formatter.
String project = "Java";
String answer = console.readLine("Bạn muốn học %s? [y/n] ", project);

// Dấu % literal trong format string phải viết %%.
String ratio = console.readLine("Nhập mức hoàn thành (0..100%%): ");
~~~

Nếu biến `project` là dữ liệu đến từ nguồn khác, cách truyền nó làm **đối số** `%s` rõ ràng và an toàn hơn đặt cả biến làm format string. Đừng nhầm `String.format`/`printf` với nối chuỗi thuần túy: dấu `%` có ý nghĩa cú pháp.

## 6. `Console.readPassword()` — đọc chuỗi bí mật mà không echo

`readPassword()` đọc một dòng trong chế độ **echo disabled**, tức ký tự người dùng gõ không được in lại ra console như lúc nhập thường. Nó trả `char[]` thay vì `String`.

~~~java
// Fragment: console đã có và mã nằm trong method.
char[] password = console.readPassword("Mật khẩu: ");
if (password == null) {
    console.writer().println("Đã kết thúc đầu vào.");
} else {
    try {
        // Truyền char[] tới một API kiểm tra bí mật có hỗ trợ char[],
        // hoặc xử lý tạm thời theo yêu cầu ứng dụng.
        // Không in mật khẩu, không ghi log mật khẩu.
    } finally {
        java.util.Arrays.fill(password, '\0');
    }
}
~~~

Lý do chọn `char[]`: phần tử mảng có thể được ghi đè ngay sau khi dùng. `String` bất biến, không có API đáng tin để chủ động xóa nội dung trong bộ nhớ. **Xóa mảng không phải bảo đảm tuyệt đối xóa mọi bản sao bí mật** trong JVM, thư viện, OS hay swap, nhưng giảm thời gian bản sao bạn kiểm soát còn tồn tại.

**Không nên làm:**

~~~java
// Ví dụ SAI về xử lý dữ liệu bí mật:
String leaked = new String(password);
System.out.println("password=" + leaked);
~~~

Chuyển mảng sang `String` và ghi ra stdout/log có thể tạo bản sao giữ lâu và phơi bày bí mật. Tốt hơn dùng API xác thực hỗ trợ dữ liệu có vòng đời ngắn (char[]/byte[] hoặc cơ chế credential chuyên dụng) và dọn dẹp các buffer trung gian khi có thể.

**Echo bị tắt không đồng nghĩa mật khẩu được bảo mật hoàn toàn.** Nó chỉ che phần nhập khỏi màn hình terminal; không thay thế kết nối an toàn, xác thực đúng, phân quyền, quản lý secrets hoặc phòng chống các thành phần bên ngoài ghi lại input. Với terminal không khả dụng, đừng lặng lẽ chuyển sang `Scanner(System.in)` để nhập mật khẩu theo chế độ echo bình thường mà không cảnh báo hoặc thiết kế bảo mật phù hợp.

### 6.1. Mật khẩu rỗng và hết đầu vào

- Khi người dùng chỉ nhấn Enter, đầu vào có thể là `new char[0]`, tức **mảng rỗng** (phải quyết định có chấp nhận hay không).
- Khi EOF, `readPassword()` trả `null`; không gọi `Arrays.fill(null,...)` hoặc `password.length` khi chưa kiểm tra.
- `readPassword()` có thể ném `IOError` khi thao tác console thất bại, hoặc `IllegalFormatException` nếu format prompt sai.
- Trường hợp người dùng hủy ứng dụng bằng tín hiệu của OS không đồng nghĩa nhất thiết sẽ nhận `null`; môi trường/terminal có thể kết thúc process trước khi Java xử lý.

## 7. Chương trình mẫu tương tác hoàn chỉnh: đọc tên và mật khẩu

Tạo file **`ConsoleInputDemo.java`**:

~~~java
import java.io.Console;
import java.io.PrintWriter;
import java.util.Arrays;

public class ConsoleInputDemo {
    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Không có console tương tác; hãy chạy từ terminal.");
            return;
        }

        PrintWriter out = console.writer();
        out.println("=== Java Console Demo ===");
        out.flush();

        String name = console.readLine("Tên hiển thị: ");
        if (name == null) {
            out.println("Đầu vào đã kết thúc.");
            out.flush();
            return;
        }

        char[] password = console.readPassword("Mật khẩu (không echo): ");
        if (password == null) {
            out.println("Đầu vào đã kết thúc.");
            out.flush();
            return;
        }

        try {
            // Chỉ mô phỏng việc nhận dữ liệu; chưa xác thực mật khẩu.
            out.printf("Xin chào, %s. Đã nhận thông tin nhập.%n", name);
            out.flush();
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
~~~

Lệnh biên dịch/chạy trong terminal tương tác ở thư mục có file:

~~~bash
javac --release 17 ConsoleInputDemo.java
java ConsoleInputDemo
~~~

**Ví dụ phiên nhập minh họa** (dòng nhập tên do người dùng gõ; nội dung nhập tại mật khẩu không xuất hiện trong terminal):

~~~text
=== Java Console Demo ===
Tên hiển thị: Mai
Mật khẩu (không echo):
Xin chào, Mai. Đã nhận thông tin nhập.
~~~

Đây là **transcript phụ thuộc nhập liệu**, không phải stdout cố định của mọi môi trường. Nếu JVM không có console, chương trình chỉ ghi vào stderr:

~~~text
Không có console tương tác; hãy chạy từ terminal.
~~~

Ví dụ cố ý **không** xác thực mật khẩu với giá trị hard-code: mục tiêu là cách nhận/dọn mảng nhạy cảm trong CLI. Khi mở chương trình qua nhiều IDE hoặc lệnh redirect, `System.console()` có thể `null` và nhánh lỗi là hành vi đúng được dự kiến. Đừng sửa nhánh này bằng cách in secret ra log hoặc tạo `String`.

### 7.1. Vì sao không dùng try-with-resources cho Console?

`Console` không triển khai `AutoCloseable`; không viết `try (Console c = System.console())`. Các đối tượng `Reader` và `PrintWriter` trả bởi console dùng chung vòng đời thiết bị; với CLI đơn giản, bạn không cần tự đóng chúng sau mỗi prompt. Hàm `Console.flush()` tồn tại để đẩy output theo nhu cầu. `Arrays.fill(password, '\0')` trong `finally` là **cleanup nội dung nhạy cảm**, khác với `close()` một I/O resource.

## 8. Khi cần `reader()` và `writer()` trực tiếp

Chọn chúng khi bạn muốn parser tự điều khiển từng ký tự/nhóm ký tự hoặc có một API nhận `Reader`/`Writer`. Ví dụ **độc lập** dưới đây chỉ minh họa đọc một UTF-16 code unit, không xử lý password hoặc kết hợp các cơ chế input khác.

Tạo **`ConsoleReaderWriterDemo.java`**:

~~~java
import java.io.Console;
import java.io.IOException;
import java.io.Reader;
import java.io.PrintWriter;

public class ConsoleReaderWriterDemo {
    public static void main(String[] args) throws IOException {
        Console console = System.console();
        if (console == null) {
            System.err.println("Không có console tương tác.");
            return;
        }

        Reader in = console.reader();
        PrintWriter out = console.writer();

        out.print("Nhập một ký tự ASCII rồi Enter: ");
        out.flush();

        int first = in.read();
        if (first == -1) {
            out.println("EOF");
        } else {
            out.printf("Mã UTF-16 đầu tiên: U+%04X%n", first);
        }
        out.flush();
    }
}
~~~

~~~bash
javac --release 17 ConsoleReaderWriterDemo.java
java ConsoleReaderWriterDemo
~~~

Nếu người dùng nhập chữ `A` rồi Enter, phần chương trình tự in sau khi đọc sẽ là:

~~~text
Mã UTF-16 đầu tiên: U+0041
~~~

Ngoài output này, terminal có thể echo lời nhập và lời nhắc nên transcript toàn bộ còn phụ thuộc môi trường. Ví dụ chỉ đọc **một** `char`, vì vậy ký tự Enter còn ở buffer/nguồn đọc; không dùng đoạn này làm vòng lặp CLI nhiều câu hỏi nếu chưa xử lý phần còn lại của dòng. Với ký tự nằm ngoài BMP như emoji, cần đọc cặp surrogate hoặc giải pháp nhận nguyên dòng, không mặc định một lần `Reader.read()` đủ một “ký tự hiển thị”.

## 9. Tương tác giữa Reader, Scanner, readLine và readPassword

**Mỗi tầng đọc có thể giữ dữ liệu trong buffer**. Giả sử bạn tạo `Scanner(console.reader())`, gọi `scanner.next()`, rồi `console.readLine()`; scanner có thể đã lấy thêm các ký tự/dấu xuống dòng mà đoạn readLine tiếp theo tưởng sẽ đọc. Tương tự, tự bọc `BufferedReader` nhiều lần quanh cùng nguồn có thể gây tình trạng khó đoán.

**Nguyên tắc thiết kế:** trong một luồng tương tác, chọn **một chủ sở hữu cơ chế đọc**. Nếu chỉ cần prompt/dòng/secret, ưu tiên `Console.readLine` và `Console.readPassword`; nếu cần token/grammar phức tạp, tổ chức parser từ một `Reader` và tránh phối hợp tùy tiện với các hàm đọc khác. Đặc biệt không đọc một phần UTF-8 từ `System.in` rồi kỳ vọng `Console.reader()` sẽ tiếp nối đúng trạng thái decoder.

Lưu ý `Console` đồng bộ hóa một số thao tác để bảo đảm tính nguyên tử của các tác vụ quan trọng trên console, nhưng các thread khác có thể **chờ** khi một thread đang nhập/xuất. Điều đó không biến việc thực hiện hai lời gọi riêng `writer.print` rồi `reader.read` thành một transaction không thể chen ngang; không cho nhiều thread hiện prompt đồng thời nếu không có bộ điều phối.

## 10. So với `Scanner(System.in)`, `BufferedReader` và đối số dòng lệnh

| Cách lấy input | Hợp với nhu cầu | Giới hạn liên quan bài này |
| --- | --- | --- |
| `console.readLine("Tên: ")` | Prompt + đọc một dòng người dùng nhập ở terminal có console | Cần `System.console() != null`; trả `null` khi EOF. |
| `console.readPassword("Mật khẩu: ")` | Nhập bí mật có **echo disabled** | Trả `char[]`, cần xóa mảng và quản lý secret; không có trên môi trường không cấp console. |
| `console.reader()` + parser/Scanner | Phân tích input ký tự phức tạp từ console | Quản lý buffer, EOF, exception và tránh đọc trộn cơ chế. |
| `console.writer()` | Xuất ký tự/định dạng trên console | Có thể cần flush; `PrintWriter` có cơ chế kiểm tra lỗi riêng. |
| `new Scanner(System.in)` | Đọc stdin dạng token/dòng, kể cả pipe/file | Không tự tắt echo. Không thay thế `readPassword()` cho secret. |
| `BufferedReader(new InputStreamReader(System.in, charset))` | Đọc stdin dòng thường từ chương trình tự động/file | Chọn charset theo contract, tự quản lý exception, không phụ thuộc console tương tác. |
| `args[]` / biến môi trường / secret provider | Cấu hình không tương tác | Không nên truyền mật khẩu qua command-line args vì có nguy cơ hiện trong process list/log; chọn cách quản lý secret phù hợp môi trường. |

### 10.1. Muốn chạy batch khi không có console?

Đối với **dữ liệu thường** (không nhạy cảm), có thể thiết kế hỗ trợ cả stdin pipe/file. Ví dụ:

~~~java
// Fragment: không tự đóng System.in nếu tài nguyên thuộc vòng đời JVM.
Console console = System.console();
String line;
if (console != null) {
    line = console.readLine("Nhập tên: ");
} else {
    java.io.BufferedReader reader = new java.io.BufferedReader(
        new java.io.InputStreamReader(
            System.in, java.nio.charset.StandardCharsets.UTF_8));
    line = reader.readLine();
}
~~~

Đoạn trên nằm trong method khai báo/bắt `IOException`. Nhánh không có console **không in prompt ra stdout**, vì stdin có thể là dữ liệu tự động từ pipeline; charset UTF-8 là **giả định giao thức của ví dụ**, không phải khẳng định mọi stdin có encoding UTF-8.

Nếu yêu cầu là **mật khẩu**, không tự động đổi từ `readPassword()` sang `readLine()` hay scanner có echo rồi coi tương đương. Thiết kế một đường cấp secret phù hợp cho CI/container và cơ chế CLI cho terminal; không làm lộ secret qua stdout, shell history, log hay đối số dòng lệnh.

## 11. Chi tiết về EOF, lỗi I/O và định dạng

**EOF:** với `readLine` và `readPassword`, kết quả `null` có nghĩa không có dòng tiếp theo do đầu vào kết thúc; với `reader().read()` là `-1`. Dòng trắng chỉ Enter vẫn là dữ liệu hợp lệ về mặt đọc (`""` hoặc `char[0]`), không phải EOF. Terminal có thể phát EOF bằng tổ hợp phím tùy hệ thống, nhưng phím tắt không phải là contract Java ổn định.

**I/O exceptions:** `Reader.read()` khai báo checked `IOException`. Những phương thức `Console.readLine/readPassword` có thể ném `IOError` (một subclass của `Error`, không phải checked `IOException`) khi I/O thất bại; không thể chỉ dựa vào `catch(IOException)` cho mọi lỗi từ Console. Có thể thiết kế boundary của CLI để thoát/báo lỗi phù hợp mà không vô tình nuốt lỗi.

**Format errors:** `readLine(fmt,args...)`, `readPassword(fmt,args...)`, `printf/format` dùng cú pháp định dạng của Java. Dấu `%` không được escape, thiếu argument hoặc type không phù hợp có thể gây `IllegalFormatException`. Ví dụ prompt tỷ lệ phần trăm phải dùng `"Mức 50%%: "` chứ không dùng `"Mức 50%: "` làm format string tùy tiện.

**I/O buffering và visibility:** `PrintWriter` có các chế độ buffering/flush riêng. Trước khi chờ nhập bằng reader, hãy `out.flush()` hoặc `console.flush()` khi đã dùng `out.print(...)`. `Console.readLine(fmt,args...)` đã là API một bước prompt + read, giúp hạn chế các lỗi phối hợp giữa các lần gọi riêng.

**Threading:** Console có đồng bộ hóa các hoạt động quan trọng; một chương trình nhiều thread đọc đồng thời từ một Console dễ tạo trải nghiệm nhắc lệnh chồng chéo. Tách lớp CLI input thành một điểm xử lý duy nhất hoặc xếp hàng yêu cầu tương tác.

## 12. Cách kiểm thử hợp lý

**Không nên** coi `System.console() != null` là điều kiện bắt buộc trong mọi unit test. Test runner/JUnit/IDE thường không cung cấp console tương tác. Thay vào đó:

1. Tách logic phân tích/kiểm tra dữ liệu khỏi việc đọc `Console`. Ví dụ hàm `parseCommand(String input)` nhận chuỗi thường, test trực tiếp bằng JUnit.
2. Tạo boundary nhận input qua interface hoặc đối tượng truyền vào nếu cần kiểm thử quy trình hỏi đáp. Với bài demo nhỏ, đơn giản kiểm tra kết quả của `System.console() == null` là đủ.
3. Kiểm tra thực tế chế độ **no-echo** trong terminal thật với `readPassword()`; không mong CI tái hiện thao tác của terminal.
4. Với nhánh stdin batch, test riêng bằng file UTF-8 đã định nghĩa contract; không cố test secret qua pipeline có echo.
5. Không chụp/ghi nội dung mật khẩu vào snapshot, console log, test failure message hay report. Chỉ kiểm tra hành vi và cleanup mà không lộ dữ liệu nhạy cảm.

Các ví dụ tương tác ở trên không có stdout cố định khi người dùng thay đổi input, và phần hiển thị của terminal phụ thuộc môi trường. Nên đánh giá theo **hợp đồng API và tình huống** thay vì so khớp transcript với mật khẩu thực.

## 13. Lỗi phổ biến

1. **Gọi `System.console().readLine()` mà không kiểm tra null.** Có thể `NullPointerException` trong IDE/CI/redirect.
2. **Cho rằng `System.in` giống `Console.reader()`.** Một bên là byte standard stream, bên kia là reader của console, với đặc tính lifecycle/buffering khác.
3. **Dùng `readLine` để nhập mật khẩu.** Input bình thường có echo, tạo `String` khó chủ động xóa.
4. **Đổi `char[]` mật khẩu thành `String` chỉ để tiện xử lý.** Tăng thời gian và nơi lưu secret không kiểm soát được.
5. **Không xóa `char[]` khi xong.** Dùng `try/finally` và `Arrays.fill(...)` trên chính mảng đã nhận.
6. **Đồng nhất `null` với chuỗi/mảng rỗng.** EOF và dòng trống mang ý nghĩa khác nhau.
7. **Trộn `Scanner(console.reader())`, `Console.readLine()` và đọc trực tiếp `System.in`.** Buffering có thể làm mất/trôi input.
8. **Quên flush khi tự in prompt bằng `writer().print`.** Prompt có thể không hiển thị đúng lúc người dùng cần nhập.
9. **Bỏ qua `IllegalFormatException` khi prompt có `%`.** Format string phải hợp lệ hoặc dùng `%%`.
10. **Tin rằng `PrintWriter.println` ném checked IOException mỗi khi ghi thất bại.** Xem `checkError()` và hành vi của Writer.
11. **Đọc `reader().read()` một lần rồi gọi đó là một ký tự hiển thị.** Đơn vị là UTF-16 code unit, emoji ngoài BMP có thể cần hai `char`.
12. **Giả định Console charset luôn trùng default charset.** `Console.charset()` có thể khác `Charset.defaultCharset()`.
13. **Dùng readPassword như cơ chế bảo mật duy nhất.** No-echo giảm lộ trên màn hình nhưng không thay thế bảo vệ secret trong bộ nhớ và hệ thống.
14. **Cho rằng có Console ở mọi môi trường chạy Java 17/21.** Sự tồn tại phụ thuộc nền tảng và cách launch, không đơn giản là phiên bản JDK.

## 14. Câu hỏi tự kiểm tra

1. Vì sao `System.in` hoạt động nhưng `System.console()` vẫn có thể `null`?
2. Khi nào chọn `console.reader()` thay cho `console.readLine()`?
3. `console.writer()` trả kiểu gì, và vì sao prompt viết bằng `print()` nên được flush?
4. Hai lần người dùng nhập chuỗi rỗng và phát EOF khác nhau về giá trị trả lại thế nào?
5. Vì sao `readPassword()` trả `char[]` mà không trả `String`? Phải làm gì sau khi dùng?
6. Có nên tạo fallback `new Scanner(System.in).nextLine()` để nhập secret khi Console không tồn tại không?
7. Nếu prompt có nội dung `"Hoàn thành 50%?"`, phải chú ý gì khi truyền nó vào overload format `readLine(fmt,args...)`?
8. Java 17 có `Console.charset()`, nó có nhất thiết bằng `Charset.defaultCharset()` không?
9. Vì sao trộn Scanner trên console.reader với console.readLine có thể làm khó dự đoán input?
10. Vì sao code trong `catch (IOException e)` không xử lý được mọi lỗi I/O mà `Console.readPassword()` báo ra?

**Gợi ý đáp án:** (1) stdin vẫn có thể được pipe, console thì không; (2) khi cần parser ký tự/token riêng thay vì chỉ một dòng; (3) `PrintWriter`, cần flush để prompt hiển thị; (4) `""`/`char[0]` khác `null`; (5) mảng có thể xóa bằng `Arrays.fill` trong finally; (6) không nên làm âm thầm vì có thể echo secret; (7) escape `%` thành `%%` hoặc đặt dữ liệu làm argument; (8) không; (9) tầng đọc có thể đệm thêm; (10) Console có thể ném `IOError` là `Error`, khác checked `IOException`.

## 15. Tài liệu chuẩn

- [Oracle Java SE 21 — Console](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Console.html): hợp đồng của `reader`, `writer`, `readLine`, `readPassword`, null/EOF, lỗi I/O, charset, synchronized I/O và bảo vệ mảng mật khẩu.
- [Oracle Java SE 21 — System.console()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#console()): điều kiện console có thể không tồn tại.
- [Oracle Java SE 21 — Reader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Reader.html): đọc ký tự, EOF `-1` và checked `IOException`.
- [Oracle Java SE 21 — PrintWriter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/PrintWriter.html): `print/println/printf`, `flush` và `checkError`.
- [Oracle Java SE 21 — Arrays.fill](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Arrays.html): xóa nội dung một `char[]` khi hết nhu cầu.
- [Oracle Java SE 21 — Scanner](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Scanner.html): parser token từ Reader và những lưu ý về scanner/buffering.

**Ghi nhớ:** lấy Console bằng `System.console()` rồi **kiểm tra null**; `reader()`/`writer()` dành cho thao tác ký tự trực tiếp; `readLine()` trả `String` và thường echo; `readPassword()` trả `char[]` với echo tắt và cần xóa sau sử dụng. Khi không có console, thiết kế lối xử lý batch/secret riêng phù hợp thay vì tự nhận đầu vào mật khẩu như dòng text bình thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="console-and-formatting-005">PrintStream khác PrintWriter</a>

<details>
<summary>Xem chi tiết</summary>

# `PrintStream` khác `PrintWriter` trong Java I/O

## Mục tiêu

Java có hai lớp xuất dữ liệu quen thuộc hỗ trợ `print`, `println`, `printf` và `format`: `java.io.PrintStream` và `java.io.PrintWriter`. Cú pháp tương tự nhau nhưng chúng khác nhau về **loại đích nhận dữ liệu, byte và ký tự, charset, quy tắc auto-flush và cách báo lỗi I/O**.

Sau bài này, bạn sẽ:

- Phân biệt được `PrintStream extends FilterOutputStream` với `PrintWriter extends Writer` và lựa chọn theo kiểu đích.
- Hiểu vì sao `print("é")` khác `write(0xE9)` trên `PrintStream`.
- Chọn charset rõ ràng khi chuyển ký tự thành byte.
- Hiểu chính xác điều gì khiến mỗi lớp tự `flush()` khi bật `autoFlush`.
- Xử lý đúng những lỗi ghi mà `print` không ném `IOException` trực tiếp ra ngoài.
- Biết dùng các lớp này trong console, tệp, bộ nhớ và chương trình Java 17+.

## 1. Khác biệt cốt lõi ở hệ phân cấp

```text
Dữ liệu Java: int, double, String, Object
                     |
        print / println / printf / format
                     |
        +------------+-------------+
        |                          |
        v                          v
   PrintStream                PrintWriter
   (OutputStream)               (Writer)
        |                          |
        v                          +---> Writer đích:
  chuyển ký tự thành byte               StringWriter,
  theo charset đã chọn                  BufferedWriter,...
        |                          |
        v                          +---> hoặc OutputStream
    OutputStream                       qua tầng OutputStreamWriter
    (file/socket/memory)
```

`PrintStream` là một loại `OutputStream`: ngoài in biểu diễn văn bản, nó có phương thức ghi **byte thô**. `PrintWriter` là một loại `Writer`: các thao tác `write` của nó nhận **ký tự/chuỗi ký tự**, không có phương thức ghi `byte[]` thô. Tuy vậy, `PrintWriter` **vẫn có constructor nhận `OutputStream`**, khi đó nó đặt một tầng mã hóa ký tự thành byte ở phía dưới.

| Tiêu chí | `PrintStream` | `PrintWriter` |
| --- | --- | --- |
| Kế thừa | `FilterOutputStream` → `OutputStream` | `Writer` |
| Đích phù hợp trực tiếp | `OutputStream` | `Writer` hoặc `OutputStream` qua chuyển đổi |
| Ghi raw bytes | `write(int)`, `write(byte[], off, len)`, `writeBytes(byte[])` | Không có API `write(byte[])` |
| Ghi giá trị Java | `print`, `println`, `printf`, `format` | `print`, `println`, `printf`, `format` |
| Lỗi I/O trong thao tác in | Đánh dấu trạng thái lỗi; kiểm tra bằng `checkError()` | Cũng đánh dấu trạng thái lỗi; kiểm tra bằng `checkError()` |
| `autoFlush` | Gồm các trigger liên quan newline/ghi mảng byte và `println` | Chỉ khi `println`, `printf`, `format` |
| Ví dụ | `System.out`, `System.err` | Sinh báo cáo với `StringWriter`, `BufferedWriter` |

Cần hiểu chính xác câu “`PrintStream` làm việc với byte”: **không có nghĩa `PrintStream.print(String)` ghi thẳng từng Java `char` như byte**. Văn bản được mã hóa bằng charset rồi mới ghi tới `OutputStream`. Ngược lại, `PrintWriter` hướng đến ký tự nhưng vẫn có thể xuất ra tệp/socket thông qua một `OutputStreamWriter`.

## 2. Ví dụ Java 17+: cùng văn bản, hai loại đích

Chương trình dưới đây có kết quả xác định trên các hệ điều hành nhờ chuẩn hóa dấu xuống dòng để **hiển thị**, không thay đổi dữ liệu đã ghi.

Lưu thành **`PrintTargetsDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class PrintTargetsDemo {
    private static String display(String text) {
        return text.replace("\r\n", "\n")
                   .replace('\r', '\n')
                   .replace("\n", "|");
    }

    public static void main(String[] args) {
        ByteArrayOutputStream byteSink = new ByteArrayOutputStream();

        try (PrintStream ps =
                     new PrintStream(byteSink, true, StandardCharsets.UTF_8)) {
            ps.print("Count=");
            ps.println(7);
            ps.printf(Locale.ROOT, "Ratio=%.1f%n", 2.5);
        }

        StringWriter charSink = new StringWriter();
        try (PrintWriter pw = new PrintWriter(charSink, true)) {
            pw.print("Count=");
            pw.println(7);
            pw.printf(Locale.ROOT, "Ratio=%.1f%n", 2.5);
        }

        String textFromBytes =
                new String(byteSink.toByteArray(), StandardCharsets.UTF_8);
        System.out.println("PrintStream: " + display(textFromBytes));
        System.out.println("PrintWriter: " + display(charSink.toString()));
    }
}
```

Biên dịch và chạy:

```bash
javac PrintTargetsDemo.java
java PrintTargetsDemo
```

Kết quả:

```text
PrintStream: Count=7|Ratio=2.5|
PrintWriter: Count=7|Ratio=2.5|
```

Với `PrintStream`, kết quả được giữ trong `ByteArrayOutputStream` dưới dạng byte UTF-8; sau đó chương trình giải mã thành `String`. Với `PrintWriter`, `StringWriter` giữ trực tiếp ký tự. Cả hai in cùng nội dung; **sự khác nhau thể hiện ở giao diện, đích và quá trình mã hóa**, không phải hình thức `println`.

Lưu ý:

- `Locale.ROOT` giúp `%.1f` in `2.5` ổn định thay vì phụ thuộc locale hệ thống.
- `println` và `%n` sử dụng ký hiệu xuống dòng của nền tảng, vì thế đoạn mã hiển thị chuyển nó sang dấu `|`.
- `try-with-resources` gọi `close()` ở cuối phạm vi. Hai đích trong bộ nhớ vẫn cho phép lấy nội dung để minh họa sau đó; không suy diễn rằng có thể sử dụng mọi đích tệp/socket sau khi đóng.

## 3. Charset: một ký tự có thể tạo nhiều byte

Trong Java, `String`/`char` biểu diễn dữ liệu ký tự; `OutputStream` chứa byte. Với chữ `é`, mã hóa UTF-8 đúng là **hai byte `C3 A9`**.

- `PrintStream.print("é")` với UTF-8 ghi `C3 A9`.
- `PrintStream.write(0xE9)` ghi byte thô `E9`; byte đơn này **không phải** UTF-8 hợp lệ cho chữ `é`.
- `PrintWriter.print("é")` với tầng mã hóa UTF-8 ghi `C3 A9`.

Ví dụ độc lập **`PrintCharsetDemo.java`**:

```java
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class PrintCharsetDemo {
    public static void main(String[] args) {
        ByteArrayOutputStream a = new ByteArrayOutputStream();
        try (PrintStream out =
                     new PrintStream(a, false, StandardCharsets.UTF_8)) {
            out.print("é");
        }

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (PrintStream out =
                     new PrintStream(b, false, StandardCharsets.UTF_8)) {
            out.write(0xE9);
        }

        ByteArrayOutputStream c = new ByteArrayOutputStream();
        try (PrintWriter out =
                     new PrintWriter(c, false, StandardCharsets.UTF_8)) {
            out.print("é");
        }

        HexFormat hex = HexFormat.of();
        System.out.println("PrintStream text = " + hex.formatHex(a.toByteArray()));
        System.out.println("PrintStream raw = " + hex.formatHex(b.toByteArray()));
        System.out.println("PrintWriter text = " + hex.formatHex(c.toByteArray()));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 PrintCharsetDemo.java
java PrintCharsetDemo
```

Stdout:

```text
PrintStream text = c3a9
PrintStream raw = e9
PrintWriter text = c3a9
```

Đây là lý do **không dùng `write(int)` như cách thay thế `print(char)`**. Trong `PrintStream`, `write(int)` nhận byte chứ không phải Unicode code point. Nếu muốn xuất nội dung văn bản, hãy dùng `print` với charset tương thích với phía đọc.

### Chọn charset khi tạo đối tượng

```java
// Snippet minh họa; output là OutputStream hợp lệ của ứng dụng.
PrintStream ps =
        new PrintStream(output, true, StandardCharsets.UTF_8);
PrintWriter pw =
        new PrintWriter(output, true, StandardCharsets.UTF_8);
```

Đây là hai **lựa chọn constructor**, không phải lời khuyên sử dụng đồng thời hai writer trên cùng `output`. Các wrapper cùng chia sẻ một `OutputStream` có thể dùng bộ đệm/flush khác nhau và tranh quyền đóng tài nguyên, khiến thứ tự byte khó kiểm soát.

- Khi không truyền charset, những constructor dựa trên `OutputStream` sử dụng charset mặc định của JVM; với dữ liệu trao đổi bên ngoài, nên **khai báo charset tường minh**.
- Khi đã có `Writer`, `PrintWriter(writer)` phù hợp trực tiếp. Nếu đó là `OutputStreamWriter`, encoding do `OutputStreamWriter` quyết định.
- Cả hai lớp mặc định thay thế một số chuỗi ký tự không thể mã hóa bằng ký tự thay thế của charset. Nếu yêu cầu **phát hiện** dữ liệu không thể mã hóa, hãy dùng `CharsetEncoder` với `CodingErrorAction.REPORT`.
- Văn bản xuất theo `println` có dấu xuống dòng tùy hệ điều hành; nếu giao thức cần đúng một byte LF (`0A`) hoặc CRLF (`0D 0A`), hãy tuân thủ định dạng giao thức thay vì mặc định dùng `println`.

## 4. `autoFlush` khác nhau như thế nào?

Flush là thao tác đẩy phần dữ liệu chưa được chuyển khỏi các lớp đệm xuống tầng dưới. Flush **không đồng nghĩa** đóng tài nguyên, không khẳng định dữ liệu đã được đồng bộ vật lý lên đĩa và cũng không bảo đảm bên nhận mạng đã xử lý xong.

### Quy tắc `PrintStream`

Nếu bật `autoFlush=true`, API `PrintStream` quy định việc tự gọi flush khi:

1. Một phương thức `println(...)` được gọi.
2. Một mảng byte được ghi vào stream (ví dụ `write(byte[], off, len)`).
3. Ký tự/byte xuống dòng `'\n'` được ghi; ví dụ `print("\n")` hoặc `write('\n')`.

### Quy tắc `PrintWriter`

Nếu bật `autoFlush=true`, API `PrintWriter` tự flush **chỉ khi gọi**:

1. `println(...)`.
2. `printf(...)`.
3. `format(...)`.

**`PrintWriter.print("\n")` không tự flush chỉ vì chuỗi có newline.** Tương tự, `write("\n")` hoặc `append('\n')` không được coi là trigger tự flush. Khi muốn bảo đảm dữ liệu đã được chuyển qua lớp đệm sau thao tác này, hãy gọi `flush()`.

| Lời gọi (autoFlush=true) | `PrintStream` | `PrintWriter` |
| --- | --- | --- |
| `println("ready")` | Tự flush | Tự flush |
| `print("\n")` | Tự flush do newline | **Không tự flush** |
| `write('\n')` | Tự flush cho byte LF | **Không tự flush** khi ghi ký tự LF |
| `write(byte[], 0, len)` | Tự flush khi ghi mảng byte | Không có API ghi byte tương ứng |
| `printf("id=%d", 7)` | Không phải trigger được liệt kê riêng; nếu cần bảo đảm hãy gọi `flush()` | **Tự flush**, dù format không chứa newline |

Các chi tiết thực thi nội bộ có thể khiến `PrintStream` flush khi thao tác định dạng cuối cùng đi qua đường ghi byte; tuy nhiên, không được lấy điều đó thay thế một lần `flush()` tường minh ở **ranh giới giao thức**.

### Ví dụ Java 17+: nhìn được sự khác nhau ngay trong `PrintWriter`

Lưu tệp **`PrintWriterFlushDemo.java`**:

```java
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

public class PrintWriterFlushDemo {
    static class CountingWriter extends StringWriter {
        int flushCount;

        @Override
        public void flush() {
            flushCount++;
        }
    }

    public static void main(String[] args) {
        CountingWriter sink = new CountingWriter();

        try (PrintWriter out = new PrintWriter(sink, true)) {
            out.print("first\n");
            System.out.println("after print = " + sink.flushCount);

            out.println("second");
            System.out.println("after println = " + sink.flushCount);

            out.printf(Locale.ROOT, "count=%d", 3);
            System.out.println("after printf = " + sink.flushCount);
        }
    }
}
```

Chạy:

```bash
javac PrintWriterFlushDemo.java
java PrintWriterFlushDemo
```

Kết quả:

```text
after print = 0
after println = 1
after printf = 2
```

Các số được in **trước khi** khối `try-with-resources` đóng writer. Ta đang quan sát số lần `flush` được kích hoạt trong khối lệnh, không đo tốc độ thiết bị hay thời điểm dữ liệu được lưu bền vững.

Một tình huống thực tế: chương trình gửi request bằng `PrintWriter.print(...)` rồi chờ phản hồi. Nếu chưa flush và bên kia đang chờ đủ request, hai phía có thể cùng chờ. Khi giao thức đòi hỏi delimiter hoặc độ dài cố định, phải **hoàn thành thông điệp** và gọi `flush()` hoặc dùng trigger thích hợp trước khi đọc response.

## 5. Lỗi ghi I/O không nhất thiết thoát ra từ `print()`

Cả hai lớp được thiết kế để những phương thức in văn bản có thể **ghi nhận lỗi I/O vào một cờ nội bộ** thay vì ném `IOException` cho từng lời gọi in. Do đó đoạn sau có thể chạy qua `println` mà thực tế đích không nhận được dữ liệu:

```java
// Snippet: out là PrintStream hoặc PrintWriter đã được tạo.
out.println("result");
if (out.checkError()) {
    // Ghi dữ liệu đã gặp lỗi I/O mà print API phát hiện được.
}
```

`checkError()` kiểm tra trạng thái lỗi và gọi flush khi đối tượng còn mở, giúp phát hiện cả một số lỗi xảy ra trong đệm. Lưu ý:

- `checkError() == true`: đã có lỗi được lớp in ghi nhận; cần xử lý theo logic ứng dụng.
- `checkError() == false`: chưa phát hiện lỗi **tại thời điểm kiểm tra**; không chứng minh ghi ra đĩa đã bền vững hay người nhận đã xử lý thành công.
- Constructor mở tệp có thể vẫn ném `IOException` hoặc ngoại lệ liên quan. Các lỗi cú pháp `printf` (`IllegalFormatException`) hay `NullPointerException` cũng không bị quy tắc “không ném IOException” loại bỏ.
- Nếu cần **bắt `IOException` trực tiếp tại nơi ghi**, hãy dùng `Writer`/`OutputStream` hoặc các lớp như `BufferedWriter`, `OutputStreamWriter` thay vì chỉ dựa vào `print`.

### Ví dụ Java 17+: mô phỏng đích ghi hỏng

Tạo **`PrintErrorDemo.java`**:

```java
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class PrintErrorDemo {
    static class FailingOutputStream extends OutputStream {
        @Override
        public void write(int value) throws IOException {
            throw new IOException("write failed");
        }
    }

    static class FailingWriter extends Writer {
        @Override
        public void write(char[] chars, int offset, int length)
                throws IOException {
            throw new IOException("write failed");
        }

        @Override
        public void flush() throws IOException {
            throw new IOException("flush failed");
        }

        @Override
        public void close() {
            // No external resource.
        }
    }

    public static void main(String[] args) {
        try (PrintStream out = new PrintStream(
                new FailingOutputStream(), false, StandardCharsets.UTF_8)) {
            out.print("data");
            System.out.println("PrintStream error = " + out.checkError());
        }

        try (PrintWriter out = new PrintWriter(new FailingWriter())) {
            out.print("data");
            System.out.println("PrintWriter error = " + out.checkError());
        }
    }
}
```

Biên dịch, chạy:

```bash
javac PrintErrorDemo.java
java PrintErrorDemo
```

Stdout:

```text
PrintStream error = true
PrintWriter error = true
```

Các phương thức `print` **không ném trực tiếp `IOException`** từ hai đích giả lập thất bại; `checkError()` cho biết trạng thái lỗi. Trong dịch vụ thật, hãy kiểm tra trạng thái sau điểm ghi/flush quan trọng, ghi nhận lỗi và đưa ra chiến lược xử lý thay vì coi việc gọi `print` là đã giao dữ liệu thành công.

## 6. `System.out`, `System.err` và console

`System.out` và `System.err` là **`PrintStream`**, không phải `PrintWriter`:

```java
System.out.println("Ready");
System.err.printf("Status=%d%n", 500);
```

Đây là đoạn minh họa hợp lệ trong phương thức Java. Nó giải thích vì sao các ví dụ “Hello World” gọi `println` trực tiếp trên `System.out`.

Không nên đóng `System.out` hoặc `System.err` ở một hàm tiện ích:

```java
// Ví dụ thiết kế sai khi ứng dụng còn cần console:
try (PrintStream console = System.out) {
    console.println("Ready");
}
```

`close()` thường đóng cả nguồn lực bên dưới và có thể làm phần còn lại của chương trình không in được nữa. Hãy để thành phần quản lý vòng đời ứng dụng quyết định vòng đời các stream toàn cục.

Bạn **có thể** bọc `System.out` trong `PrintWriter`:

```java
// Đoạn minh họa: không tự ý close vì có thể đóng System.out bên dưới.
PrintWriter consoleWriter = new PrintWriter(System.out, true);
consoleWriter.println("Ready");
```

Nhưng việc bọc thêm thường không cần thiết để chỉ in console và có thể khiến quản lý flush/close rắc rối hơn. Muốn kiểm thử output, truyền `PrintStream` hoặc `Writer` từ caller; test dùng `ByteArrayOutputStream` hay `StringWriter` để so sánh nội dung.

Với ứng dụng server, phần lớn thông báo vận hành dài hạn nên đi qua API logging phù hợp thay vì sử dụng `System.out.println` ở mọi nơi.

## 7. Khi nào chọn loại nào?

### Chọn `PrintStream` khi

- Có sẵn `OutputStream` và cần xuất văn bản/giá trị định dạng.
- Đang làm việc với `System.out` hoặc `System.err`.
- API thư viện yêu cầu chính xác đối tượng `PrintStream`.
- Có lý do rõ ràng dùng thao tác byte thô lẫn in văn bản; phải quy định encoding và khung dữ liệu, không trộn tùy tiện trong giao thức.

### Chọn `PrintWriter` khi

- Có sẵn một `Writer` như `BufferedWriter`, `StringWriter` hoặc writer do framework cung cấp.
- Muốn làm việc theo **ký tự** và cần `print`, `println`, `printf` nhưng không có nhu cầu ghi byte thô.
- Muốn tạo text trong bộ nhớ rồi kiểm tra nội dung mà không cần encode/decode ở bước trung gian.

### Không nhất thiết dùng cả hai

| Yêu cầu | API cân nhắc |
| --- | --- |
| Ghi văn bản và cần bắt trực tiếp `IOException` | `BufferedWriter`, `OutputStreamWriter`, `Files.newBufferedWriter` |
| Ghi dữ liệu nhị phân | `OutputStream`, `BufferedOutputStream` |
| Ghi giá trị nguyên thủy theo dạng nhị phân | `DataOutputStream` |
| Tích lũy text trong bộ nhớ | `StringWriter`; thêm `PrintWriter` nếu cần định dạng |
| Kiểm soát lỗi mã hóa ký tự nghiêm ngặt | `CharsetEncoder` + `CodingErrorAction.REPORT` |
| Nhật ký ứng dụng production | Logging framework |

Không nên xem `PrintStream` là cách mặc định ghi protocol nhị phân chỉ vì nó cũng có `write(byte[])`. Ngược lại, không cần tạo `PrintWriter` nếu `Writer` sẵn có chỉ cần `write(String)` mà không dùng tiện ích định dạng.

## 8. Những lỗi thường gặp

1. **Nhầm `PrintStream` với stream chỉ chứa văn bản.** Nó có thể ghi byte thô; `print` là một API tiện ích chuyển giá trị thành văn bản trên byte stream.
2. **Nhầm `PrintWriter` không thể ghi file/socket.** Nó có thể nhận `OutputStream` và charset. Bản chất API vẫn là ký tự.
3. **Dùng `PrintStream.write(int)` để mã hóa Unicode.** Lệnh này ghi byte, không chuyển Unicode code point thành UTF-8.
4. **Giả định mọi lệnh chứa `\n` đều tự flush.** Với `PrintWriter` chỉ `println/printf/format` kích hoạt autoFlush; `print("\n")` không đủ.
5. **Bỏ qua `checkError()`.** `print` không bảo đảm phát hiện lỗi qua exception và có thể để lệnh tiếp theo chạy dù tầng dưới lỗi.
6. **Dùng `printf` nhưng không quản lý locale.** Với file trao đổi máy đọc, dùng locale rõ ràng và quy định dấu thập phân.
7. **Cho rằng `println` luôn ghi `\n`.** Nó dùng ký tự xuống dòng của nền tảng; định dạng mạng/tệp có quy định riêng cần được tuân thủ.
8. **Tạo nhiều wrapper cùng ghi một đích.** Khi bộ đệm, charset và thứ tự flush khác nhau, dữ liệu có thể bị xen kẽ hoặc đảo thứ tự; chọn một pipeline ghi có chủ sở hữu.
9. **Đóng writer mượn từ caller.** `close()` thường kéo theo đóng đích bên dưới; phân biệt wrapper do hàm sở hữu và stream do caller quản lý.
10. **Giả định flush nghĩa là đã lưu bền vững.** Flush chỉ chuyển dữ liệu qua tầng đệm thích hợp; độ bền dữ liệu hoặc xác nhận từ peer là vấn đề riêng.

## 9. Tự kiểm tra

**Câu 1.** Vì sao `System.out.println(...)` dùng `PrintStream`?

**Trả lời:** `System.out` là một `PrintStream`, một loại `OutputStream` có các phương thức in/định dạng tiện ích.

**Câu 2.** `PrintWriter` có thể nhận `OutputStream` không?

**Trả lời:** Có. Constructor nhận `OutputStream` tạo tầng chuyển ký tự thành byte phía dưới; nó vẫn là API `Writer`.

**Câu 3.** `PrintWriter.print("\n")` với `autoFlush=true` có bảo đảm flush ngay không?

**Trả lời:** Không. `PrintWriter` tự flush do `println`, `printf` hoặc `format`; nếu cần, gọi `flush()` rõ ràng.

**Câu 4.** `PrintStream.write(0xE9)` có ghi chữ `é` theo UTF-8 không?

**Trả lời:** Không. Nó ghi đúng byte `E9`, còn `print("é")` theo UTF-8 ghi `C3 A9`.

**Câu 5.** Lệnh `out.println("ok")` chạy qua mà không ném exception có chứng minh file đã ghi thành công không?

**Trả lời:** Không. Cả hai print API có thể cất lỗi I/O trong cờ nội bộ; phải kiểm tra `checkError()` và xử lý theo mức bảo đảm cần thiết của ứng dụng.

**Câu 6.** Muốn tạo báo cáo text vào `StringWriter`, nên dùng gì?

**Trả lời:** Dùng trực tiếp `StringWriter.write(...)` khi chỉ ghi chuỗi; bọc bằng `PrintWriter` nếu cần `println` và định dạng giá trị theo `printf`.

## 10. Tài liệu chuẩn

- [Java SE 17 — `PrintStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PrintStream.html)
- [Java SE 17 — `PrintWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PrintWriter.html)
- [Java SE 17 — `OutputStreamWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStreamWriter.html)
- [Java SE 17 — `StandardCharsets`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html)

**Ghi nhớ:** `PrintStream` là lớp in có gốc **byte stream**, `PrintWriter` là lớp in có gốc **character writer**; cả hai đều có `print/println/printf` và `checkError`, nhưng **autoFlush không giống nhau**. Chọn lớp theo đích và dữ liệu thực sự, khai báo charset khi cần byte, kiểm tra lỗi I/O và quản lý đúng quyền sở hữu stream.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="console-and-formatting-006">print, println, printf và Formatter</a>

<details>
<summary>Xem chi tiết</summary>

# print, println, printf và Formatter trong Java

Khi xuất dữ liệu ra console hoặc tạo văn bản có cấu trúc, Java cung cấp ba phương thức quen thuộc trên `PrintStream`: `print(...)`, `println(...)` và `printf(...)`. Cả ba thường được gọi qua `System.out`, nhưng **chúng không có cùng hành vi xuống dòng hay định dạng giá trị**. Phía dưới `printf` là cơ chế của `java.util.Formatter`, có khả năng căn lề, chèn số theo mẫu, chọn `Locale` và tạo chuỗi định dạng để gửi sang tệp hoặc nơi nhận khác.

Bài này giải thích cách chọn đúng phương thức, đọc cú pháp `%...`, kiểm soát định dạng giữa các máy và tránh các lỗi thường gặp trong Java 17+.

## 1. Đầu ra tiêu chuẩn và PrintStream

`System.out` là luồng đầu ra tiêu chuẩn (stdout), có kiểu `PrintStream`; `System.err` là luồng lỗi chuẩn (stderr), cũng là `PrintStream`. Với chương trình chạy trong terminal, cả hai thường hiển thị trên màn hình. Tuy nhiên khi chuyển hướng hoặc chạy trong IDE, chúng có thể được gửi tới những nơi khác nhau.

~~~java
System.out.print("Hello");
System.out.println(" World");
System.err.println("Something went wrong");
~~~

Đây là **mảnh mã minh họa**. Hai lời gọi đầu nối lại thành dòng `Hello World` trên stdout. Lời gọi cuối gửi thông báo sang stderr; thứ tự hiển thị tương đối giữa hai luồng không nhất thiết được bảo đảm bởi terminal hoặc công cụ gom log. Không tự gọi `close()` trên `System.out`/`System.err` trong chương trình thông thường, vì các phần còn lại của ứng dụng có thể vẫn cần dùng chúng.

`PrintStream` có các overload của `print`/`println` nhận kiểu nguyên thủy, `String`, `char[]` và `Object`. Đối với đối tượng, việc biểu diễn thường dựa vào `String.valueOf(...)` và `toString()`; do đó định dạng đẹp của đối tượng tùy cách `toString()` được triển khai. Riêng mảng số nguyên không tự in danh sách phần tử bằng `print(array)`; để hiển thị nội dung, dùng `Arrays.toString(array)`.

## 2. print, println và printf khác nhau thế nào?

| Phương thức | Hành vi chính | Có tự kết thúc dòng? | Có chuỗi định dạng `%...`? |
|---|---|---|---|
| `print(value)` | In biểu diễn của một giá trị | **Không** | Không |
| `println(value)` | In giá trị rồi in dấu kết thúc dòng | **Có** | Không |
| `println()` | Chỉ in dấu kết thúc dòng | **Có** | Không |
| `printf(format, args...)` | Thay các format specifier bằng giá trị đã định dạng | **Không**, trừ khi mẫu chứa ký tự xuống dòng hoặc `%n` | **Có** |
| `format(format, args...)` trên `PrintStream` | Cùng cơ chế định dạng như `printf` | Theo mẫu | Có |

`print` hữu ích để nối nhiều phần trên cùng dòng. `println` thích hợp cho thông báo theo dòng. `printf` thích hợp khi cần bản ghi có kích thước cột, số chữ số, độ chính xác hoặc biểu diễn khác nhau theo loại dữ liệu.

Ví dụ:

~~~java
System.out.print("A");
System.out.print("B");
System.out.println("C");
System.out.println("D");
~~~

Kết quả trên stdout:

~~~text
ABC
D
~~~

Hai lần `print` không tạo newline. Lần `println("C")` mới kết thúc dòng `ABC`; lần `println("D")` tạo dòng tiếp theo.

**Điểm rất dễ nhầm:** `System.out.printf("Done")` không tự xuống dòng. Nếu muốn xuống dòng, dùng `System.out.printf("Done%n")` hoặc gọi `println()` sau đó.

## 3. Ví dụ hoàn chỉnh: ba phương thức trong cùng chương trình

Tạo file **`ConsoleOutputDemo.java`**:

~~~java
import java.util.Locale;

public class ConsoleOutputDemo {
    public static void main(String[] args) {
        System.out.print("Java");
        System.out.print(" ");
        System.out.println("IO");
        System.out.println();

        System.out.printf(Locale.US, "ID=%04d; price=%.2f%n", 7, 12.5);
        System.out.printf(Locale.US, "[%-6s][%05d]%n", "OK", 42);
        System.out.printf(Locale.US,
                "%2$s/%1$s/%%%n", "right", "left");
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac -encoding UTF-8 ConsoleOutputDemo.java
java ConsoleOutputDemo
~~~

Stdout (có **một dòng trống** sau `Java IO`):

~~~text
Java IO

ID=0007; price=12.50
[OK    ][00042]
left/right/%
~~~

Giải thích:

1. Hai lần `print` và một lần `println` cùng tạo `Java IO`; sau đó `println()` tạo dòng trống.
2. `%04d` in số nguyên `7` trong độ rộng tối thiểu 4 với các số `0` điền bên trái, cho `0007`.
3. `%.2f` in số thực với hai chữ số sau dấu thập phân, cho `12.50` khi dùng `Locale.US`.
4. `%-6s` căn trái chuỗi `OK` trong vùng rộng ít nhất sáu ký tự: `OK` cộng bốn dấu cách. `%05d` cho `00042`.
5. `%2$s` lấy tham số thứ hai (`left`); `%1$s` lấy tham số thứ nhất (`right`). `%%` là ký tự `%` thực, `%n` kết thúc dòng.
6. `Locale.US` được chỉ rõ để kết quả số và ký hiệu định dạng không phụ thuộc locale mặc định của máy chạy.

Các dòng văn bản giống nhau trên các nền tảng, nhưng chuỗi byte của dấu xuống dòng cuối các dòng có thể khác tùy hệ điều hành vì `println` và `%n` dùng line separator tương ứng của nền tảng.

## 4. Cấu trúc format specifier của Formatter

Mẫu tổng quát cho một phép chuyển đổi có đối số:

~~~text
%[argument_index$][flags][width][.precision]conversion
~~~

| Phần | Vai trò | Ví dụ |
|---|---|---|
| `%` | Bắt đầu một specifier | `%d` |
| `argument_index$` | Lấy đối số ở vị trí 1, 2, 3, ... (đếm từ 1) | `%2$s` |
| `flags` | Điều chỉnh căn lề, dấu, số 0, phân nhóm chữ số... | `%-10s`, `%+d` |
| `width` | **Độ rộng tối thiểu**, không tự cắt chuỗi nếu vượt quá | `%8d` |
| `.precision` | Độ chính xác hoặc độ dài tối đa, tùy kiểu chuyển đổi | `%.2f`, `%.3s` |
| `conversion` | Loại biểu diễn mong muốn | `s`, `d`, `f` |

Phần trong dấu `[...]` là tùy chọn. Không phải mọi tổ hợp cờ, độ rộng và precision đều hợp lệ với mọi conversion; chọn sai có thể phát sinh ngoại lệ định dạng.

Một số specifier đặc biệt không tiêu thụ đối số:

- `%%` xuất ký tự phần trăm `%`.
- `%n` xuất **dấu kết thúc dòng phù hợp nền tảng**.
- Ký tự `\n` viết trong chuỗi Java sẽ đưa đúng ký tự LF; nó không mang ý nghĩa *platform line separator* như `%n`.

### Các conversion thông dụng

| Specifier | Loại đối số phù hợp | Ví dụ giá trị / kết quả |
|---|---|---|
| `%s` | Hầu hết đối tượng, dạng chuỗi | `"Java"` → `Java` |
| `%S` | Dạng chữ hoa của `%s` theo locale áp dụng | `"java"` → `JAVA` |
| `%d` | Kiểu số nguyên: `byte`, `short`, `int`, `long`, `BigInteger` | `42` → `42` |
| `%x`, `%X` | Số nguyên hệ 16, chữ thường/chữ hoa | `255` → `ff` / `FF` |
| `%o` | Số nguyên hệ 8 | `8` → `10` |
| `%f` | `float`, `double`, `BigDecimal` | `1.5` → `1.500000` khi không chỉ precision |
| `%e`, `%E` | Số thực dạng khoa học | `12.0` → biểu diễn có số mũ |
| `%b` | Giá trị boolean theo quy tắc Formatter | `true` → `true` |
| `%c` | Ký tự, hoặc một code point hợp lệ từ giá trị nguyên phù hợp | `'A'` → `A` |
| `%tF` | Đối số ngày/giờ hỗ trợ conversion: ngày `yyyy-MM-dd` | `LocalDate.of(2024, 2, 29)` → `2024-02-29` |
| `%n` | Không nhận đối số | Dấu kết thúc dòng |
| `%%` | Không nhận đối số | `%` |

Một số chi tiết cần nhớ:

- `%d` **không** dành cho số thực `double`; nếu muốn biểu diễn số thập phân, dùng `%f` hoặc chuyển kiểu có chủ đích.
- `%f` mặc định sáu chữ số sau dấu thập phân (ví dụ `1.500000`), không phải hiển thị nguyên trạng bằng `Double.toString`.
- `%b` của `Formatter` **khác cách ép chuỗi sang boolean**: nếu đối số là `null` thì kết quả `false`; nếu là `Boolean` thì theo giá trị đó; đối tượng khác `null` và khác `Boolean` sẽ cho `true`. Đừng dùng `%b` để diễn giải dữ liệu text `"false"`.
- `%s` với `null` thường cho chữ `null`; nó không tương đương chuỗi rỗng. Với đối tượng tùy biến, kết quả phụ thuộc `toString()` hoặc `Formattable` nếu có hỗ trợ.
- `%tF` dùng cú pháp ngày-giờ: ký tự `t` theo sau là `F`; các biến thể khác có `%tY` (năm), `%tm` (tháng), `%td` (ngày), `%tT` (giờ/phút/giây). Loại đối số phải hỗ trợ trường thời gian được yêu cầu.
- Số nguyên Java kiểu `char`/`Character` không được dùng trực tiếp với `%d`; nếu cần mã số ký tự, chuyển sang kiểu `int` rõ ràng.

## 5. Độ rộng, căn lề, số 0 và precision

### 5.1. Width là tối thiểu

~~~java
System.out.printf(Locale.US, "[%5s]%n", "A");
System.out.printf(Locale.US, "[%-5s]%n", "A");
~~~

Đây là mảnh mã minh họa cần import `java.util.Locale` và đặt trong một phương thức. Kết quả:

~~~text
[    A]
[A    ]
~~~

- `%5s`: tổng độ rộng ít nhất 5, mặc định thêm 4 dấu cách ở **bên trái**.
- `%-5s`: cờ `-` căn **trái**, thêm 4 dấu cách ở bên phải.
- Nếu giá trị dài hơn width, Formatter không mặc nhiên cắt dữ liệu. Width không phải kích thước tối đa.

### 5.2. Precision phụ thuộc loại chuyển đổi

~~~java
System.out.printf(Locale.US, "[%.3s]%n", "Formatter");
System.out.printf(Locale.US, "[%.2f]%n", 3.14159);
~~~

Kết quả:

~~~text
[For]
[3.14]
~~~

- `%.3s` giới hạn phần biểu diễn chuỗi tối đa ba ký tự được định dạng, cho `For`.
- `%.2f` quy định hai chữ số sau dấu phân cách thập phân, có làm tròn khi cần.
- **Không dùng `%.2d`** theo thói quen từ các ngôn ngữ khác: precision không áp dụng cho phép chuyển số nguyên `%d` trong `java.util.Formatter`; có thể gặp `IllegalFormatPrecisionException`. Để thêm số 0 bên trái, dùng ví dụ `%04d`.

### 5.3. Cờ định dạng số thường gặp

| Specifier | Giá trị | Kết quả với `Locale.US` | Ý nghĩa |
|---|---:|---|---|
| `%+d` | `42` | `+42` | Luôn hiển thị dấu |
| `%06d` | `42` | `000042` | Điền số 0 đủ độ rộng 6 |
| `%,d` | `1234567` | `1,234,567` | Phân nhóm chữ số |
| `%(d` | `-42` | `(42)` | Số âm đặt trong ngoặc |
| `%8.2f` | `3.5` | `    3.50` | Độ rộng 8, hai chữ số thập phân |
| `%-8.2f` | `3.5` | `3.50    ` | Căn trái trong 8 vị trí |

Các khoảng trắng ở hai hàng cuối là thành phần của output. Không dùng cờ `0` cho chuỗi `%s` và đừng kết hợp hai cờ trái nghĩa `-` với `0`: những tổ hợp không phù hợp có thể ném `FormatFlagsConversionMismatchException` hoặc `IllegalFormatFlagsException`.

## 6. Thứ tự đối số và tái sử dụng đối số

Mặc định, các specifier có đối số tiêu thụ chúng theo thứ tự:

~~~java
String text = String.format("%s:%d", "port", 8080);
~~~

`text` chứa `port:8080`. Đây là mảnh mã minh họa.

Muốn đổi thứ tự mà không đổi danh sách đối số, dùng chỉ số bắt đầu từ **1**, gắn `$`:

~~~java
String text = String.format("%2$s / %1$s / %2$s", "B", "A");
~~~

`text` chứa `A / B / A`. Đối số được nhắc lại nhiều lần mà không phải truyền trùng.

Cờ `<` còn cho phép dùng lại đối số của specifier trước đó:

~~~java
String result = String.format(Locale.US, "%1$.2f / %<.1f", 2.5);
~~~

`result` là `2.50 / 2.5`. Khi dùng `<` trước khi có đối số trước đó, Formatter báo lỗi thiếu đối số. Trong mã có nhiều trường định dạng, chỉ số tường minh thường dễ kiểm tra hơn vì việc trộn chỉ số tường minh và ngầm định có những quy tắc khó đoán.

## 7. Locale ảnh hưởng đến kết quả định dạng

`Locale` quyết định các quy tắc bản địa hóa trong Formatter, chẳng hạn dấu thập phân, phân nhóm hàng nghìn và tên tháng. Mặc định, các lời gọi `printf(...)` không chỉ `Locale` lấy **default locale cho nhóm FORMAT**, nên kết quả có thể khác giữa hai máy.

Chương trình **`LocaleFormattingDemo.java`**:

~~~java
import java.time.LocalDate;
import java.util.Locale;

public class LocaleFormattingDemo {
    public static void main(String[] args) {
        double amount = 12345.6;

        System.out.println("US: "
                + String.format(Locale.US, "%,.2f", amount));
        System.out.println("DE: "
                + String.format(Locale.GERMANY, "%,.2f", amount));

        LocalDate date = LocalDate.of(2024, 2, 29);
        System.out.printf(Locale.US, "Date: %1$tF%n", date);

        String message = "Item=%s; quantity=%d".formatted("Pen", 3);
        System.out.println(message);
    }
}
~~~

~~~bash
javac -encoding UTF-8 LocaleFormattingDemo.java
java LocaleFormattingDemo
~~~

Kết quả:

~~~text
US: 12,345.60
DE: 12.345,60
Date: 2024-02-29
Item=Pen; quantity=3
~~~

Mức độ làm tròn của `%.2f` là hành vi định dạng biểu diễn, **không đổi giá trị gốc của biến `double`**. Khi nghiệp vụ cần quy tắc tiền tệ chính xác, phải chọn kiểu số (ví dụ `BigDecimal`), độ chính xác và cách làm tròn phù hợp riêng; Formatter chủ yếu trình bày kết quả.

- Dùng `Locale.US` hoặc một locale cụ thể để có kết quả dễ dự đoán khi định dạng báo cáo hoặc kiểm thử.
- Dùng locale theo người dùng cho nội dung hiển thị thân thiện với vùng miền.
- Không xem dấu thập phân do Formatter in ra là định dạng trao đổi máy-máy mặc định. CSV, JSON, SQL hoặc giao thức dữ liệu có quy tắc riêng; hãy tuân theo chuẩn của từng định dạng.
- `String.formatted(args...)` (Java 15+) là cách ngắn để áp dụng cú pháp Formatter lên chuỗi mẫu; nó sử dụng locale mặc định khi cần bản địa hóa. Muốn chỉ định locale, dùng `String.format(Locale, ...)`.

## 8. java.util.Formatter: định dạng không nhất thiết in ra console

`java.util.Formatter` là bộ máy định dạng. `PrintStream.printf` và `PrintStream.format` là các cách tiện lợi để ghi kết quả định dạng vào PrintStream; `String.format` tạo **String mới**; còn một đối tượng `Formatter` có thể ghi trực tiếp vào một đích `Appendable`, ví dụ `StringBuilder` hoặc `Writer`.

Ví dụ độc lập **`FormatterBuilderDemo.java`**:

~~~java
import java.util.Formatter;
import java.util.Locale;

public class FormatterBuilderDemo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder();

        try (Formatter formatter = new Formatter(builder, Locale.US)) {
            formatter.format("%2$s | %1$04d%n", 3, "ID");
            formatter.format("ratio=%.2f%n", 2.5);
        }

        System.out.print(builder.toString());
    }
}
~~~

~~~bash
javac -encoding UTF-8 FormatterBuilderDemo.java
java FormatterBuilderDemo
~~~

Stdout:

~~~text
ID | 0003
ratio=2.50
~~~

Vòng đời ví dụ:

1. `StringBuilder` là nơi nhận ký tự, chưa có byte I/O hoặc console.
2. `new Formatter(builder, Locale.US)` tạo formatter ghi vào builder với locale chỉ định.
3. Hai lần `format` thêm dữ liệu vào cùng builder, **không xóa nội dung trước đó**.
4. `Formatter` hỗ trợ `AutoCloseable` nên dùng được trong `try-with-resources`. Ở đây `StringBuilder` không phải nguồn I/O cần đóng, và kết quả vẫn truy cập được sau khi đóng Formatter.
5. Lời gọi `System.out.print(...)` cuối chỉ làm nhiệm vụ xuất chuỗi đã tạo; có thể thay bằng ghi sang file hoặc sử dụng trong ứng dụng mà không in console.

Đối với Formatter ghi vào một đích thuộc loại `Closeable`, thao tác `Formatter.close()` sẽ đóng cả đích đó. Không nên mở `Formatter` sở hữu `System.out` rồi vô tình đóng nó giữa chương trình. Một đối tượng Formatter đã đóng không còn cho phép định dạng tiếp; dùng lại sẽ gặp `FormatterClosedException`.

### Formatter có thể ghi ra Writer

~~~java
// Mảnh mã minh họa: writer là Writer do ứng dụng sở hữu.
try (Formatter formatter = new Formatter(writer, Locale.US)) {
    formatter.format("%s,%.2f%n", "Book", 12.5);
}
~~~

Dùng cách này khi muốn tập trung chuẩn bị nội dung định dạng ở một nơi, có thể kết hợp với `BufferedWriter` hoặc `Files.newBufferedWriter(...)` với charset tường minh. Khi formatter đóng, writer phía dưới cũng được đóng nếu nó hỗ trợ `Closeable`.

## 9. printf, Formatter và phương thức tạo chuỗi

| API | Đích kết quả | Giá trị trả về | Đặc điểm |
|---|---|---|---|
| `System.out.printf(...)` | Stdout qua PrintStream | Chính `PrintStream` | Ghi trực tiếp, không tự thêm newline |
| `System.out.format(...)` | Stdout qua PrintStream | Chính `PrintStream` | Cơ chế tương đương printf |
| `String.format(...)` | Bộ nhớ | `String` mới | Phù hợp tạo thông báo rồi gửi sang nơi khác |
| `"pattern".formatted(...)` | Bộ nhớ | `String` mới | Cách viết ngắn từ Java 15 |
| `formatter.format(...)` | Đích `Appendable` của Formatter | Chính `Formatter` | Cho phép tiếp tục thêm nhiều đoạn với cùng formatter |

`printf` có overload `printf(Locale locale, String format, Object... args)`. Nếu không truyền locale, định dạng số và ngày tháng được thực hiện theo locale FORMAT mặc định. `String.format` cũng có overload tương ứng.

`print` và `println` **không phân tích chuỗi đầu vào thành format specifier**: `System.out.println("100%")` chỉ in đúng `100%`. Ngược lại, với `printf("100%")`, dấu phần trăm đứng một mình có thể gây lỗi cú pháp định dạng; phải viết `printf("100%%")` hoặc dùng `print/println` cho chuỗi không cần định dạng.

## 10. Lỗi định dạng và cách phát hiện

Khác với `print` đơn thuần, Formatter kiểm tra sự tương thích giữa mẫu và đối số **khi chương trình chạy**. Sai mã conversion, thiếu đối số hoặc cờ không hợp lệ thường gây một ngoại lệ thuộc họ `IllegalFormatException`, tức lỗi runtime.

| Lỗi | Ví dụ | Kết quả thường gặp |
|---|---|---|
| Kiểu đối số không phù hợp | `String.format("%d", "12")` | `IllegalFormatConversionException` |
| Thiếu đối số | `String.format("%s %d", "A")` | `MissingFormatArgumentException` |
| Precision không hỗ trợ | `String.format("%.2d", 4)` | `IllegalFormatPrecisionException` |
| Dùng cờ không phù hợp với conversion | `String.format("%05s", "A")` | `FormatFlagsConversionMismatchException` |
| Mẫu có conversion không được hỗ trợ | `String.format("%q", 1)` | `UnknownFormatConversionException` |

Ví dụ độc lập **`FormattingErrorsDemo.java`**:

~~~java
import java.util.IllegalFormatConversionException;
import java.util.MissingFormatArgumentException;

public class FormattingErrorsDemo {
    public static void main(String[] args) {
        try {
            String.format("%d", "42");
        } catch (IllegalFormatConversionException ex) {
            System.out.println("Wrong type");
        }

        try {
            String.format("%s %d", "A");
        } catch (MissingFormatArgumentException ex) {
            System.out.println("Missing argument");
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 FormattingErrorsDemo.java
java FormattingErrorsDemo
~~~

Stdout:

~~~text
Wrong type
Missing argument
~~~

Hãy xem lỗi định dạng là lỗi trong chương trình hoặc dữ liệu đầu vào cần được kiểm soát. Không nên dùng `catch (Exception)` rồi bỏ qua để che giấu mẫu sai. Nếu có phần văn bản do người dùng nhập, hãy truyền nó **như đối số của `%s`** (chẳng hạn `printf("%s%n", userText)`) thay vì dùng toàn bộ văn bản đó làm format string; một dấu `%` trong chuỗi người dùng sẽ không bị hiểu nhầm là lệnh định dạng.

### Lỗi I/O khác lỗi cú pháp format

`PrintStream` theo hợp đồng không ném `IOException` từ các thao tác in thông thường; sự cố I/O bên dưới thường được ghi nhận qua cờ lỗi, có thể kiểm tra bằng `checkError()`. Nhưng **ngoại lệ định dạng** như `IllegalFormatException` vẫn có thể ném khi mẫu/đối số sai. Đừng nhầm hai loại lỗi này.

Tương tự, `Formatter` ghi vào một đích `Appendable` có thể ghi nhận lỗi `IOException` từ đích và cung cấp thông tin qua `ioException()`. Khi ghi dữ liệu quan trọng ra file, ngoài việc định dạng chính xác còn phải quyết định cách phản ứng nếu việc ghi thực sự thất bại. Đặc biệt, việc không thấy ngoại lệ từ `PrintStream.print(...)` không đủ chứng minh dữ liệu đã được lưu bền vững xuống thiết bị lưu trữ.

## 11. Charset, newline và luồng đầu ra

Một chuỗi `String` trong JVM khác với byte do `System.out` gửi ra terminal/file. Charset dùng bởi `PrintStream` và môi trường terminal có thể ảnh hưởng đến việc dấu tiếng Việt hiển thị đúng; từ JDK 18, default charset UTF-8 không đồng nghĩa mọi thiết lập của `System.out`/`System.err` và terminal đều nhất thiết dùng cùng encoding.

Nếu cần tạo file văn bản có định dạng ổn định, có thể ghi một chuỗi được tạo bởi Formatter bằng charset rõ ràng:

~~~java
// Mảnh mã minh họa: path là Path hợp lệ và đã chọn cách xử lý ghi đè.
String line = String.format(Locale.US, "%s,%.2f%n", "Tea", 3.5);
Files.writeString(path, line, StandardCharsets.UTF_8);
~~~

Đoạn này cần import `Locale`, `Files` và `StandardCharsets`; `Files.writeString` dùng UTF-8 được chỉ định. Nó chỉ minh họa encoding, **không** là chương trình chạy độc lập. Khi cần tệp CSV thật, còn phải xử lý dấu phẩy trong dữ liệu, nháy kép, xuống dòng trong trường và các quy tắc CSV; chỉ nối `%s,%.2f` chưa đủ để làm bộ ghi CSV hoàn chỉnh.

Về xuống dòng:

- `println(...)` viết dấu kết thúc dòng theo nền tảng.
- `printf("...%n")` thêm line separator theo nền tảng.
- `printf("...\n")` viết ký tự LF `U+000A` trong chuỗi format.
- `print(...)` và `printf(...)` không tự thêm newline khi không được yêu cầu.

`PrintStream` có thể được cấu hình tự `flush` trong các tình huống xác định, nhưng flush phụ thuộc cách tạo PrintStream và không phải bảo đảm lưu bền vững trên đĩa. Với ứng dụng cần log có độ tin cậy cao hoặc lưu dữ liệu giao dịch, nên dùng công cụ và chiến lược ghi phù hợp thay vì chỉ phụ thuộc `System.out.printf`.

## 12. Cách chọn công cụ trong thực tế

**Thông báo đơn giản theo dòng:** dùng `println(...)`. Nó dễ đọc, tự xuống dòng, ít rủi ro hơn dùng `printf` mà không cần specifier.

**Nối phần nội dung trên cùng dòng:** dùng `print(...)`. Nhưng đối với UI terminal phức tạp, hãy cân nhắc công cụ hỗ trợ terminal thay vì phụ thuộc cách luồng ký tự được đệm và hiển thị.

**Bảng, số với dấu phân cách/độ chính xác:** dùng `printf(locale, pattern, args...)`; kiểm soát width, precision, cờ và locale.

**Tạo văn bản để dùng nhiều nơi:** dùng `String.format(...)` hoặc `String.formatted(...)`; tránh ghi trực tiếp ra console giữa tầng xử lý nghiệp vụ.

**Ghi nhiều đoạn vào cùng buffer hoặc Writer:** dùng `Formatter` với đích `Appendable`. Xác định rõ trách nhiệm đóng tài nguyên và kiểm tra lỗi I/O khi ghi ra đích ngoài.

**Số tiền hay dữ liệu cần tính toán chính xác:** Formatter là tầng trình bày; thiết kế kiểu dữ liệu, quy tắc làm tròn và cách lưu/trao đổi là các quyết định khác.

## 13. Các lỗi dễ mắc

1. **Cho rằng `printf` tự xuống dòng:** không đúng. Thêm `%n` khi cần kết thúc dòng.
2. **Nhầm `%n` với `\n`:** `%n` là line separator nền tảng, còn `\n` là LF. Kiểm thử byte đầu ra và định dạng file cần biết khác biệt này.
3. **Dùng `%d` cho `double`, `%.2d` cho số nguyên hoặc `%05s` cho chuỗi:** mỗi conversion chấp nhận kiểu và cờ khác nhau; tổ hợp sai gây runtime exception.
4. **Cho rằng width là độ dài tối đa:** nó chỉ là tối thiểu; muốn giới hạn chuỗi, xem precision của `%s` nếu phù hợp, hoặc kiểm tra dữ liệu trước khi in.
5. **Quên Locale:** `%,.2f` có thể in dấu phân nhóm và thập phân khác nhau giữa các máy. Chọn locale theo yêu cầu.
6. **Dùng `%b` để đọc giá trị boolean từ một chuỗi:** đối số `"false"` vẫn là đối tượng khác null và sẽ được Formatter biểu diễn thành `true` với `%b`.
7. **Dùng dữ liệu người dùng làm format string:** dấu `%` có thể kích hoạt specifier; nên truyền dữ liệu như đối số của mẫu cố định.
8. **In mảng bằng `print(intArray)` rồi chờ danh sách giá trị:** kiểu mảng không có `toString()` thân thiện. Dùng `Arrays.toString(...)` hoặc formatter cho từng phần tử.
9. **Dùng `printf` để tính toán/round số rồi tái sử dụng chuỗi như kết quả số học:** Formatter chỉ xây dựng biểu diễn, không thay thế quy tắc tính toán.
10. **Đóng `System.out` hoặc bọc `System.out` trong một Formatter rồi đóng ngoài ý muốn:** có thể khiến phần còn lại của chương trình không in được.
11. **Cho rằng mọi lỗi khi in đều ném `IOException`:** `PrintStream` thường ghi nhận qua `checkError()`; lỗi format runtime vẫn có thể ném bình thường.
12. **Nghĩ stdout và stderr nhất thiết hiển thị theo thứ tự gọi:** khi được trộn/chuyển hướng riêng, thứ tự quan sát được có thể khác, nên không dựa vào đó để bảo đảm thứ tự sự kiện.

## 14. Ghi nhớ

- `print(value)` **không xuống dòng**; `println(value)` **xuống dòng**; `printf(format, args...)` **định dạng theo mẫu** và chỉ xuống dòng khi yêu cầu.
- `Formatter` hỗ trợ `%s`, `%d`, `%f`, `%tF`, `%%`, `%n` cùng flags/width/precision/chỉ số đối số. Đúng loại đối số là yêu cầu bắt buộc.
- Chỉ rõ `Locale` để kết quả số và ngày/giờ ổn định; dùng `%n` khi cần line separator của nền tảng.
- `String.format`, `String.formatted` và `Formatter` cho phép tạo dữ liệu có định dạng mà không buộc phải in console.
- Cần phân biệt lỗi cú pháp/kiểu định dạng với lỗi I/O thực tế; nếu ghi ra file hoặc hệ thống khác, chọn charset và cơ chế quản lý tài nguyên phù hợp.

### Tài liệu chính thức

- [Java SE — PrintStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/PrintStream.html)
- [Java SE — Formatter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Formatter.html)
- [Java SE — String.format và formatted](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="console-and-formatting-007">Locale, mẫu định dạng và dấu phân cách số</a>

<details>
<summary>Xem chi tiết</summary>

# Locale, mẫu định dạng và dấu phân cách số trong Java

Một số có **giá trị** và có **cách trình bày**. Ví dụ, cùng một giá trị một triệu hai trăm ba mươi bốn nghìn năm trăm sáu mươi bảy phẩy năm có thể hiển thị như `1,234,567.50` hoặc `1.234.567,50` tùy quy ước vùng. Dấu phẩy không phải lúc nào cũng là dấu thập phân; dấu chấm cũng không phải lúc nào cũng là dấu nhóm hàng nghìn.

Trong Java, `Locale` mô tả quy ước ngôn ngữ/vùng để **định dạng** và **đọc** giá trị cho con người. `Formatter`, `printf`, `NumberFormat`, `DecimalFormat` và `Scanner` có những cách sử dụng `Locale` khác nhau. Bài học thuộc nhóm **Console và định dạng**, tập trung vào định dạng số khi in console, mẫu định dạng, dấu tách nhóm, dấu thập phân, parsing đầu vào và các lỗi dễ gây sai số.

## 1. Ba lớp vấn đề cần phân biệt

**Giá trị số:** Một biến `long total = 1234567;` hay `BigDecimal amount = new BigDecimal("1234567.50");` chứa giá trị số. Bản thân biến không chứa dấu nhóm hàng nghìn hay quy ước hiển thị thập phân.

**Biểu diễn số dưới dạng văn bản:** Khi ứng dụng gọi `String.format(...)` hay `NumberFormat.format(...)`, kết quả là `String`. Chuỗi có thể có dấu nhóm (`,`, `.` hoặc khoảng trắng đặc biệt), dấu thập phân, chữ số bản địa, dấu âm, ký hiệu tiền tệ, v.v.

**Diễn giải văn bản thành số:** Khi người dùng nhập `1.234,50`, chương trình cần biết đó là số theo quy ước nào. Nếu áp dụng ngữ pháp US một cách máy móc, có thể hiểu sai hoặc từ chối input. `Scanner` và `NumberFormat` hỗ trợ một phần quá trình chuyển ngược này.

Ba lớp này cần được định nghĩa rõ khi thiết kế giao diện console:

```text
Giá trị số (BigDecimal, long, double...)
          |
          | format với Locale + quy tắc hiển thị
          v
Chuỗi hiển thị (ví dụ "1.234,50")
          |
          | parse với Locale + kiểm tra hợp lệ
          v
Giá trị số (nếu chuyển đổi thành công)
```

**`Locale` là lựa chọn biểu diễn và quy tắc văn hóa, không phải cơ chế chuyển đổi kiểu số lưu trong bộ nhớ.** Thay `Locale.US` bằng `Locale.GERMANY` không làm giá trị `1234567.5` thay đổi. Nó cũng không tự đổi charset console thành UTF-8.

## 2. `Locale` và quy ước phân cách số

`java.util.Locale` định danh một ngôn ngữ và tùy chọn vùng, ví dụ:

- `Locale.US`: một cấu hình tiếng Anh/Mỹ, thường dùng dấu **phẩy** để nhóm hàng nghìn và dấu **chấm** để ngăn phần thập phân.
- `Locale.GERMANY`: tiếng Đức/Đức, thường dùng dấu **chấm** để nhóm và dấu **phẩy** cho thập phân.
- `Locale.forLanguageTag("vi-VN")`: tiếng Việt/Việt Nam, hữu ích khi ứng dụng dành cho người dùng Việt. Ký tự phân cách, cách hiển thị tiền tệ, chữ số và các chi tiết cụ thể có thể phụ thuộc dữ liệu locale của môi trường Java; nên kiểm tra bằng API thay vì hard-code mọi quy tắc.
- `Locale.FRANCE`: ví dụ về trường hợp nhóm chữ số bằng **khoảng trắng chuyên biệt** (có thể là narrow no-break space tùy dữ liệu locale), khác với dấu cách ASCII thông thường.

Bảng minh họa cho giá trị `1234567.5`, giả định luôn hiển thị hai chữ số sau dấu thập phân:

| Locale | Biểu diễn số phổ biến | Phân cách nhóm | Phân cách thập phân |
|---|---|---|---|
| `Locale.US` | `1,234,567.50` | `,` | `.` |
| `Locale.GERMANY` | `1.234.567,50` | `.` | `,` |
| `Locale.forLanguageTag("vi-VN")` | Theo dữ liệu locale của JDK đang dùng | Tra từ `DecimalFormatSymbols` | Tra từ `DecimalFormatSymbols` |

Mốc ba chữ số chỉ là một ví dụ; không phải mọi vùng trên thế giới đều nhóm số theo cụm ba chữ số giống US. Dấu tách còn có thể là **ký tự Unicode không nhìn thấy rõ**, nhất là các dạng space. Vì vậy không nên so sánh trực tiếp với `" "` khi xử lý output của mọi locale.

### Kiểm tra ký tự phân cách thực tế

Trong một phương thức Java (đoạn minh họa, cần import các kiểu):

```java
Locale locale = Locale.forLanguageTag("vi-VN");
DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
char grouping = symbols.getGroupingSeparator();
char decimal = symbols.getDecimalSeparator();
```

Với import `java.util.Locale` và `java.text.DecimalFormatSymbols`, hai ký tự trên cho biết dấu nhóm và dấu thập phân mà API cung cấp cho locale. Nếu cần debug khoảng trắng, hãy in **Unicode code point** thay vì chỉ nhìn ký tự.

Một số thành phần hiển thị (như dấu phần trăm, đơn vị tiền tệ, vị trí ký hiệu, số lượng chữ số thập phân mặc định) còn tùy API `NumberFormat` và thiết lập vùng.

## 3. `printf` và `String.format`: định dạng trực tiếp với Locale

Java `System.out.printf` (thông qua `PrintStream`) và `String.format` dùng ngôn ngữ định dạng của `java.util.Formatter`. Cả hai cho phép truyền **`Locale` tường minh**, ví dụ:

```java
String us = String.format(Locale.US, "%,.2f", 1234567.5);
System.out.printf(Locale.GERMANY, "%,.2f%n", 1234567.5);
```

Đây là **đoạn minh họa**: cần import `java.util.Locale`. Nên truyền locale khi đầu ra phải ổn định trên nhiều máy; phiên bản không nhận locale sẽ dùng locale mặc định phù hợp với API và môi trường đang chạy, vốn có thể khác.

### Cấu trúc của format specifier

Một format specifier kiểu thông dụng:

```text
% [flags] [width] [.precision] conversion
```

Ví dụ `%,12.2f`:

- `%`: mở đầu một phép định dạng.
- `,`: **flag grouping** yêu cầu sử dụng dấu nhóm theo locale với conversion được hỗ trợ.
- `12`: **minimum width** (độ rộng tối thiểu); không phải số ký tự tối đa và không cắt bớt giá trị dài.
- `.2`: precision, với `%f` là số chữ số sau dấu thập phân.
- `f`: định dạng số dạng thập phân cố định.

Các mẫu hữu ích:

| Format | Ý nghĩa | Ví dụ khi dùng US |
|---|---|---|
| `%d` | Số nguyên thập phân, không chủ động tách nhóm | `1234567` |
| `%,d` | Số nguyên có nhóm theo locale | `1,234,567` |
| `%.2f` | Số thực với hai chữ số thập phân, không grouping | `1234567.50` |
| `%,.2f` | Số thực với grouping và hai chữ số thập phân | `1,234,567.50` |
| `%12.2f` | Tối thiểu 12 ký tự, mặc định canh phải | Thêm khoảng trắng bên trái nếu cần |
| `%-12.2f` | Tối thiểu 12 ký tự, canh trái | Thêm khoảng trắng bên phải nếu cần |
| `%n` | Xuống dòng theo môi trường | Dùng trong định dạng console thay vì tự gắn `\n` nếu muốn quy ước hệ thống |
| `%%` | In ký tự `%` literal | `%` |

**Flag `,` ở đây là ký hiệu cú pháp của Formatter, không có nghĩa dấu nhóm xuất ra luôn là ký tự dấu phẩy.** Với `Locale.GERMANY`, cùng format `%,.2f` sẽ in dấu nhóm `.` và dấu thập phân `,`.

Không phải conversion nào cũng nhận mọi flag. Ví dụ dùng grouping với `%s` là không phù hợp. `%s` định dạng đối tượng dưới dạng chuỗi và không biến số thành biểu diễn grouping tự động. Sử dụng conversion phù hợp với kiểu dữ liệu.

### Ví dụ 1 — so sánh định dạng US và Đức trên console

Tạo file `PrintfLocaleDemo.java` (Java 17+):

```java
import java.util.Locale;

public class PrintfLocaleDemo {
    public static void main(String[] args) {
        double amount = 1_234_567.5;

        System.out.println("US grouping: "
                + String.format(Locale.US, "%,.2f", amount));
        System.out.println("DE grouping: "
                + String.format(Locale.GERMANY, "%,.2f", amount));
        System.out.println("US no grouping: "
                + String.format(Locale.US, "%.2f", amount));
        System.out.println("US integer: "
                + String.format(Locale.US, "%,d", 1234567));
        System.out.println("DE integer: "
                + String.format(Locale.GERMANY, "%,d", 1234567));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 --release 17 PrintfLocaleDemo.java
java PrintfLocaleDemo
```

Kết quả đã kiểm tra:

```text
US grouping: 1,234,567.50
DE grouping: 1.234.567,50
US no grouping: 1234567.50
US integer: 1,234,567
DE integer: 1.234.567
```

Hai dòng đầu dùng cùng `amount` nhưng cho chuỗi khác nhau. Dòng thứ ba không có flag `,` nên không nhóm số. Hai dòng cuối cho thấy `%,d` cũng chịu tác động của locale; dữ liệu đầu vào là cùng số nguyên `1234567`.

**Về kiểu số:** `double` thuận tiện cho ví dụ nhưng biểu diễn nhị phân của nó không chính xác tuyệt đối với nhiều phân số thập phân (ví dụ `0.1`). Với tiền, thuế hoặc các phép làm tròn thập phân có ràng buộc, ưu tiên `BigDecimal` khởi tạo từ chuỗi và quy định `RoundingMode` rõ ràng.

### Một số lỗi cú pháp Formatter

- `String.format(Locale.US, "%,.2f", "1234.5")` là **sai kiểu**: chuỗi không phải đối số số thực cho `%f`; có thể ném `IllegalFormatConversionException`.
- `String.format(Locale.US, "%s", 1234.5)` không tự thêm dấu nhóm hay thập phân theo locale; vì `%s` dùng biểu diễn chuỗi của đối tượng.
- `System.out.printf("%f", value)` không chọn cố định precision hai chữ số; cần `%.2f` hoặc quy tắc độ chính xác khác.
- `String.format(Locale.GERMANY, "%,.2f", value)` **đúng** dù cú pháp chứa ký tự comma: đây là flag group, không phải ký tự cố định trong output.
- Không dùng một chuỗi hiển thị có grouping như `"1,234.50"` rồi gọi `Double.parseDouble` và mong JVM hiểu dấu phân cách vùng; `Double.parseDouble` dùng ngữ pháp số của Java, không phải locale.

## 4. `NumberFormat`: biểu diễn số, phần trăm và tiền tệ

`java.text.NumberFormat` là API chuyên cho biểu diễn số theo locale. Các factory thường dùng:

- `NumberFormat.getNumberInstance(locale)`: số thông thường.
- `NumberFormat.getIntegerInstance(locale)`: biểu diễn số nguyên theo cài đặt locale.
- `NumberFormat.getCurrencyInstance(locale)`: hiển thị tiền tệ theo locale/đồng tiền tương ứng.
- `NumberFormat.getPercentInstance(locale)`: hiển thị phần trăm; với giá trị `0.125` thường biểu diễn `12.5%` nếu chọn một chữ số thập phân.

Ví dụ:

```java
NumberFormat number = NumberFormat.getNumberInstance(Locale.US);
number.setGroupingUsed(true);
number.setMinimumFractionDigits(2);
number.setMaximumFractionDigits(2);
String view = number.format(new BigDecimal("1234567.5"));
```

Đây là **mã minh họa**, cần import `java.text.NumberFormat`, `java.util.Locale` và `java.math.BigDecimal`. `view` theo US sẽ là `"1,234,567.50"` trong môi trường Java thông thường.

Tính năng cấu hình:

- `setGroupingUsed(boolean)`: bật/tắt nhóm chữ số.
- `setMinimumFractionDigits(int)` / `setMaximumFractionDigits(int)`: giới hạn số chữ số phần thập phân ở đầu ra.
- `setRoundingMode(RoundingMode)`: quy định cách làm tròn ở implementation hỗ trợ. Với Java `DecimalFormat`, có thể dùng `RoundingMode.HALF_UP`, `HALF_EVEN`, v.v.
- `format(...)`: biến giá trị thành `String`; **không sửa** biến số gốc.

### Tiền tệ và phần trăm không chỉ là dấu tách

Đừng cố format tiền tệ bằng mỗi `%,.2f` rồi tự nối ký hiệu `$`/`₫`. Quy ước tiền tệ còn bao gồm vị trí ký hiệu, chữ số thập phân mặc định, ký tự cách, dấu âm và lựa chọn currency.

Tương tự, định dạng phần trăm trong `NumberFormat.getPercentInstance` **nhân giá trị với 100 để hiển thị**. Chẳng hạn `0.125` ứng với `12.5%`, không phải `0.125%`. Nếu input đã là số “12.5” đại diện cho 12,5%, đừng truyền trực tiếp vào percent formatter mà chưa xác định đơn vị miền nghiệp vụ.

Để cố định đồng tiền bất kể locale, có thể thiết lập `Currency` cho formatter, nhưng ký hiệu, cách đặt và quy tắc hiển thị vẫn phải được kiểm tra cho trường hợp sử dụng cụ thể.

## 5. `DecimalFormat` và ngôn ngữ mẫu định dạng

`java.text.DecimalFormat` là implementation mạnh của `NumberFormat` cho phép quy định mẫu như:

```text
#,##0.00
```

Các ký hiệu **trong mẫu chưa bản địa hóa**:

| Ký hiệu pattern | Ý nghĩa |
|---|---|
| `0` | Vị trí chữ số **bắt buộc**; thêm số 0 nếu cần |
| `#` | Vị trí chữ số **tùy chọn** |
| `,` | Vị trí **nhóm chữ số** trong pattern; ký tự nhóm thật lấy từ symbols/locale |
| `.` | Vị trí **phân cách thập phân** trong pattern; dấu thật lấy từ symbols/locale |
| `;` | Phân tách subpattern dương và âm nếu định nghĩa hai nhánh |
| `%` | Hệ số biểu diễn phần trăm (nhân 100) và ký hiệu phần trăm theo locale |
| `'...'` | Quote ký tự literal theo cú pháp pattern khi cần |

Với pattern `#,##0.00`, formatter nhóm các chữ số và luôn hiển thị **đúng hai** chữ số phần thập phân. Locale quyết định `,` hay `.` được in ra thực tế.

**Điểm rất dễ nhầm:** `applyPattern("#,##0.00")` nhận cú pháp pattern chuẩn sử dụng dấu `,` cho grouping và `.` cho decimal **bất kể locale hiển thị**. Khi dùng `applyLocalizedPattern(...)`, cú pháp pattern lại theo `DecimalFormatSymbols` của locale. Không được tráo lẫn hai phương thức.

Ví dụ `DecimalFormat` tạo từ `NumberFormat.getNumberInstance(Locale.GERMANY)` rồi áp dụng pattern `#,##0.00` sẽ hiển thị `1.234.567,50`. Không phải viết ngược pattern thành `#.##0,00` cho `applyPattern`.

### Ví dụ 2 — định dạng, làm tròn và phân tích số với `DecimalFormat`

Tạo file `DecimalFormatLocaleDemo.java` (Java 17+):

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class DecimalFormatLocaleDemo {
    public static void main(String[] args) {
        DecimalFormat format =
                (DecimalFormat) NumberFormat.getNumberInstance(Locale.GERMANY);
        format.applyPattern("#,##0.00");
        format.setRoundingMode(RoundingMode.HALF_UP);
        format.setParseBigDecimal(true);

        System.out.println("formatted=" + format.format(new BigDecimal("1234567.5")));
        System.out.println("rounded=" + format.format(new BigDecimal("12.345")));

        String input = "1.234.567,50";
        ParsePosition position = new ParsePosition(0);
        Number parsed = format.parse(input, position);
        System.out.println("parsed=" + ((BigDecimal) parsed).toPlainString());
        System.out.println("fully parsed="
                + (parsed != null && position.getIndex() == input.length()));

        String withSuffix = input + "oops";
        ParsePosition extraPosition = new ParsePosition(0);
        Number partial = format.parse(withSuffix, extraPosition);
        System.out.println("suffix rejected by full check="
                + (partial != null && extraPosition.getIndex() != withSuffix.length()));

        NumberFormat percent = NumberFormat.getPercentInstance(Locale.US);
        percent.setMinimumFractionDigits(1);
        System.out.println("percent=" + percent.format(new BigDecimal("0.125")));
    }
}
```

```bash
javac -encoding UTF-8 --release 17 DecimalFormatLocaleDemo.java
java DecimalFormatLocaleDemo
```

```text
formatted=1.234.567,50
rounded=12,35
parsed=1234567.50
fully parsed=true
suffix rejected by full check=true
percent=12.5%
```

**Giải thích:**

1. Factory tạo number formatter theo `Locale.GERMANY`; đoạn ví dụ sử dụng implementation `DecimalFormat` của bộ locale chuẩn trong JDK. `applyPattern("#,##0.00")` chọn grouping và hai chữ số phần thập phân.
2. `setRoundingMode(HALF_UP)` khiến `12.345` (được khởi tạo **chính xác** bằng chuỗi `BigDecimal`) được hiển thị là `12,35`.
3. `setParseBigDecimal(true)` yêu cầu `DecimalFormat` tạo kết quả parse ở dạng `BigDecimal` cho các giá trị số thông thường, tránh chuyển sang `Double` và mất độ chính xác thập phân không cần thiết. Các trường hợp ký hiệu đặc biệt như NaN/Infinity vẫn cần xem contract của parser.
4. `ParsePosition` cho biết **đã đọc đến đâu** trong chuỗi. Chỉ khi `parsed != null` và `position.getIndex() == input.length()` mới coi là đã tiêu thụ hết input.
5. `format.parse("1.234.567,50oops", ...)` có thể đọc **phần đầu hợp lệ** rồi dừng ở chữ `o` mà không ném exception; đây là lý do phải kiểm tra vị trí cuối.
6. `NumberFormat.getPercentInstance(Locale.US)` nhân `0.125` với 100 để hiển thị `12.5%`.

**Giới hạn của kiểm tra `ParsePosition`:** tiêu thụ toàn bộ chuỗi không đồng nghĩa mọi quy tắc nghiệp vụ hay mọi vị trí grouping đều được xác thực nghiêm ngặt. `DecimalFormat` có hành vi parsing khá dễ dãi ở nhiều trường hợp. Với input nhạy cảm (số tiền, số hợp đồng, giao thức), nên kiểm tra thêm mẫu ký tự, phạm vi, định dạng grouping và số lượng chữ số được phép trước khi nhận dữ liệu.

## 6. `DecimalFormatSymbols` và dấu tách số thật

`DecimalFormatSymbols` nắm các ký hiệu vùng, chẳng hạn:

- `getDecimalSeparator()`: ký tự phân cách phần nguyên và phần thập phân.
- `getGroupingSeparator()`: ký tự nhóm phần nguyên.
- `getMinusSign()`: dấu âm dùng trong định dạng theo locale.
- `getPercent()`: dấu phần trăm.
- `getZeroDigit()`: chữ số 0 để biểu diễn các bộ chữ số được locale dùng.
- `getCurrencySymbol()` và `getInternationalCurrencySymbol()`: ký hiệu tiền tệ.

Trong ứng dụng đa ngôn ngữ, **lấy dữ liệu từ symbols** thường an toàn hơn tự viết logic dạng `if (locale.equals(Locale.GERMANY)) ...`. Tuy nhiên, `Locale` chỉ là một phần của yêu cầu: nếu hệ thống cần định dạng cố định (ví dụ CSV phải dùng dấu chấm thập phân), chính **đặc tả dữ liệu đích** mới quyết định.

Các ví dụ parser dễ lỗi khi có ký tự khoảng trắng đặc biệt ở grouping, dấu âm Unicode, chữ số không phải 0–9 ASCII hoặc dấu phần trăm. Nếu giao diện nhận output từ thư viện i18n, hãy kiểm tra code point thực tế khi điều tra bug thay vì chỉ nhìn dấu in trên màn hình.

### Tùy biến ký hiệu có chủ đích

Có thể tạo `DecimalFormatSymbols` cho một locale rồi sửa grouping/decimal separator và truyền vào `DecimalFormat`. Nhưng cách này là **quy tắc tùy chỉnh của ứng dụng**, không còn chắc giống chuẩn địa phương. Nếu yêu cầu đơn thuần là in số đúng vùng, hãy chọn locale và dùng formatter mặc định trước khi nghĩ tới override.

Tương tự, không nên thay `,` thành `.` bằng `String.replace` trên **toàn chuỗi số đã format**; nếu cả hai dấu cùng xuất hiện, thứ tự thay có thể làm hỏng grouping, và điều này càng sai khi có tiền tệ hoặc khoảng trắng Unicode.

## 7. Đọc đầu vào console bằng `Scanner.useLocale`

`java.util.Scanner` phân tích token từ chuỗi, stdin, file hoặc nguồn khác. Khi gọi `Scanner.nextInt()`, `nextDouble()`, `nextBigDecimal()`, ... Scanner có thể dùng locale để diễn giải **dấu grouping, dấu thập phân và một số ký hiệu số**.

**Locale dành cho đầu vào** phải tương ứng với quy ước mà người dùng được yêu cầu nhập. Thay đổi locale **không** làm các ký tự có sẵn trong input tự đổi từ `,` sang `.`.

### Ví dụ 3 — cùng giá trị nhập theo hai quy ước

Tạo file `ScannerLocaleDemo.java` (Java 17+):

```java
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Scanner;

public class ScannerLocaleDemo {
    public static void main(String[] args) {
        try (Scanner us = new Scanner("1,234.50").useLocale(Locale.US);
             Scanner de = new Scanner("1.234,50").useLocale(Locale.GERMANY)) {
            BigDecimal a = us.nextBigDecimal();
            BigDecimal b = de.nextBigDecimal();
            System.out.println("US parsed=" + a.toPlainString());
            System.out.println("DE parsed=" + b.toPlainString());
            System.out.println("same numeric value=" + (a.compareTo(b) == 0));
        }
        try (Scanner strictGerman = new Scanner("1234.50")
                .useLocale(Locale.GERMANY)) {
            System.out.println("DE accepts 1234.50="
                    + strictGerman.hasNextBigDecimal());
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 ScannerLocaleDemo.java
java ScannerLocaleDemo
```

```text
US parsed=1234.50
DE parsed=1234.50
same numeric value=true
DE accepts 1234.50=false
```

Giải thích:

- Cả hai token số có **giá trị tương đương**: `1,234.50` ở US và `1.234,50` ở Đức.
- `nextBigDecimal()` trả `BigDecimal`; so sánh giá trị bằng `compareTo`, vì `BigDecimal.equals` còn xét cả **scale** (ví dụ `1.0` và `1.00` có thể so sánh bằng giá trị nhưng `equals` khác).
- Scanner đặt locale Đức không nhận token `1234.50` theo cú pháp số thập phân Đức của ví dụ; `hasNextBigDecimal()` cho biết việc gọi `nextBigDecimal()` có phù hợp với token kế tiếp hay không.
- Đây là demo dùng **String input** để đầu ra có thể kiểm chứng; khi đọc `System.in`, cần thiết lập locale theo lựa chọn của người dùng và giải quyết rõ charset đầu vào.

Đối với dữ liệu không hợp lệ, `nextBigDecimal()` có thể ném `InputMismatchException`, hoặc `NoSuchElementException` nếu đã hết input. Trong một chương trình tương tác, hãy kiểm tra và báo người dùng nhập lại thay vì nuốt exception hoặc âm thầm suy đoán locale khác.

### Scanner: token và dòng khác nhau

`Scanner` mặc định phân tách token bằng khoảng trắng, **không** coi dấu nhóm số là token delimiter. Khi chuyển giữa `nextBigDecimal()` và `nextLine()`, cần lưu ý dấu kết thúc dòng còn lại (ví dụ sau khi nhập số rồi nhấn Enter, `nextLine()` đầu tiên có thể trả chuỗi rỗng). Đây là hành vi thao tác token-vs-line, độc lập với việc dấu thập phân là comma hay dot.

Một cách thiết kế rõ là: đọc **một dòng hoàn chỉnh** bằng `BufferedReader.readLine()` / `Scanner.nextLine()`, sau đó validate và parse dòng đó theo locale đã chọn. Cách này cho phép báo lỗi trên chính chuỗi người dùng gửi.

## 8. `BigDecimal` và dữ liệu trung gian không phụ thuộc locale

Đối với tiền và số thập phân cần chính xác, `BigDecimal` thường là lựa chọn thích hợp. Nhưng **`new BigDecimal(String)` không phải parser theo locale**:

```java
BigDecimal value = new BigDecimal("1234.50");
```

Đây là mã minh họa; cần import `java.math.BigDecimal`. Chuỗi dùng **dấu chấm** cho phần thập phân và không có grouping theo ngữ pháp `BigDecimal`, dù ứng dụng hiển thị theo `Locale.GERMANY`. `new BigDecimal("1.234,50")` là không hợp lệ đối với constructor này; để nhận input địa phương, hãy dùng parser locale và kiểm tra kết quả.

Khi truyền dữ liệu giữa các hệ thống, cần phân biệt:

- **Giá trị nội bộ:** `BigDecimal` hoặc các kiểu số theo quy tắc nghiệp vụ.
- **Đầu ra cho người dùng:** format theo locale (có grouping, ký hiệu phần trăm/tiền tệ).
- **Giao thức hoặc cấu hình máy đọc:** format **bất biến** do đặc tả quy định, thường không grouping và dùng dấu chấm decimal. Ví dụ `BigDecimal.toPlainString()` cho representation thập phân không có ký pháp số mũ ở mức chuỗi.
- **JSON number:** cú pháp JSON không chấp nhận dấu tách nhóm hàng nghìn; decimal separator theo grammar JSON là dấu chấm. Khi sinh JSON, dùng serializer thực sự, không đưa trực tiếp chuỗi đã format theo locale vào vị trí số.

**Không ghép output dành cho người đọc trở lại làm input cho parser máy đọc** nếu chưa xác nhận chính xác locale, định dạng và chính sách kiểm tra. Việc format rồi parse lại có thể mất chữ số vì quy tắc làm tròn, hoặc biến dữ liệu thành invalid khi môi trường triển khai thay đổi.

## 9. Khác nhau giữa độ chính xác, scale và làm tròn

Ví dụ giá trị thập phân `12.345`:

- `BigDecimal("12.345")` lưu ba chữ số phần thập phân một cách chính xác.
- `DecimalFormat` với pattern `"0.00"` chỉ **hiển thị** hai chữ số; nếu áp dụng `RoundingMode.HALF_UP` thì ra `12.35` theo US hoặc `12,35` theo Đức.
- `BigDecimal.setScale(2, RoundingMode.HALF_UP)` trả **giá trị BigDecimal mới** đã được làm tròn ở scale 2; đây là bước biến đổi dữ liệu, khác với chỉ format.
- `double` là dạng dấu phẩy động nhị phân; làm tròn giá trị double có thể gặp các trường hợp khó dự đoán theo trực giác thập phân.

Không nên dùng việc định dạng `%.2f` làm một quy trình tính tiền. Hãy quy định cách làm tròn nghiệp vụ, xử lý bằng kiểu số phù hợp, sau đó format chỉ để hiển thị.

**Grouping không làm tròn.** `setGroupingUsed(true)` chỉ yêu cầu phân cách các nhóm chữ số. Việc hiển thị hai chữ số sau dấu thập phân do pattern/precision/min-max fraction digits quyết định.

**Precision của `Formatter` và digit pattern của `DecimalFormat` khác cú pháp:**

- `%.2f`: cú pháp Formatter dành cho một argument dạng số thực.
- `#,##0.00`: cú pháp DecimalFormat biểu diễn một con số bằng ký hiệu pattern.
- Không truyền mẫu `#,##0.00` vào `printf` rồi mong Formatter hiểu nó như `DecimalFormat`.

## 10. Locale mặc định, khả năng tái lập và thread safety

### Không phụ thuộc vô thức vào locale mặc định

`Locale.getDefault()` lấy locale mặc định của môi trường. Giá trị có thể khác giữa máy phát triển, máy build, container, desktop người dùng và cấu hình runtime. Vì vậy hai cách gọi:

```java
String ambiguous = String.format("%,.2f", 1234.5);
String explicit = String.format(Locale.US, "%,.2f", 1234.5);
```

là **mã minh họa**; cần import `java.util.Locale`. Chuỗi `ambiguous` phụ thuộc locale mặc định, còn `explicit` buộc quy tắc US. Thay `Locale.setDefault(...)` toàn tiến trình để giải quyết một vị trí format cụ thể có thể ảnh hưởng nhiều mã thư viện khác, nên ưu tiên truyền locale tại điểm định dạng.

Với console, có hai vấn đề tách biệt:

1. **Locale**: dấu thập phân, dấu grouping, chữ số, cách hiển thị.
2. **Charset/encoding của console**: chuyển ký tự Unicode thành byte để terminal render. Đổi locale không bảo đảm terminal có font/glyph hoặc encoding đúng.

Nếu kết quả số trông sai ký tự, kiểm tra cả symbols của locale và encoding đầu ra (nhất là khi dùng dấu cách Unicode hoặc chữ số ngoài ASCII).

### Đừng chia sẻ tùy tiện `DecimalFormat`

`DecimalFormat` và `NumberFormat` (thông thường) là các formatter **có trạng thái, mutable và không bảo đảm thread-safe**. Thay pattern, rounding mode hoặc symbols trong một thread có thể làm thread khác thấy kết quả không mong đợi.

Với ứng dụng concurrent, tạo formatter theo từng lần sử dụng, per-thread hoặc bảo vệ truy cập theo một chính sách đồng bộ phù hợp. `Locale` có thể dùng như đối tượng chỉ định chính sách; đừng giữ một formatter mutable toàn cục rồi thay đổi pattern theo từng request.

`String.format(Locale, ...)` tạo formatter phục vụ lời gọi đó, thuận tiện cho các tác vụ hiển thị không yêu cầu giữ một formatter mutable.

## 11. Khi parse dữ liệu người dùng, cần kiểm tra gì?

Trước khi chấp nhận một chuỗi số thành dữ liệu nghiệp vụ:

1. **Chọn đúng locale đầu vào.** Người dùng biết mình phải nhập `1,234.50` hay `1.234,50` không? Trường nhập có hiển thị ví dụ minh họa không?
2. **Phân biệt kiểu dữ liệu.** Số nguyên, số thực, tiền, phần trăm, tỷ lệ hay số điện thoại/mã bưu chính? Không phải chuỗi toàn chữ số nào cũng nên parse thành số; mã định danh có thể cần giữ số 0 đầu.
3. **Xử lý grouping.** Có chấp nhận dấu nhóm hay yêu cầu input canonical không grouping? Nếu chấp nhận, parser có kiểm tra **đúng vị trí** nhóm hay chỉ bỏ dấu nhóm một cách khoan dung?
4. **Kiểm tra parse toàn bộ chuỗi.** Với `NumberFormat.parse` và `ParsePosition`, tránh chấp nhận `"1.234,50abc"` chỉ vì phần tiền tố parse được.
5. **Kiểm tra biên giá trị và scale.** Số có âm được không? Cho phép bao nhiêu chữ số thập phân? Giá trị có nằm trong khoảng nghiệp vụ?
6. **Quy định cách làm tròn.** Với tiền, sử dụng `BigDecimal` và `RoundingMode` theo chính sách được xác định.
7. **Không tự đổi tất cả dấu phân cách.** `replace(",", ".")` biến `"1,234.50"` thành `"1.234.50"` và phá giá trị. Thao tác thay ký tự tùy tiện không giải quyết được parser đa locale.
8. **Kiểm tra ký tự Unicode.** Grouping của một số locale dùng khoảng trắng đặc biệt. Sao chép từ trang web/bảng tính có thể đưa vào ký tự không phải ASCII space.
9. **Báo lỗi rõ ràng.** Thay vì âm thầm parse theo locale khác, cung cấp ví dụ đúng và để người dùng sửa chuỗi.
10. **Tách format UI khỏi format file/giao thức.** Log máy đọc, CSV, JSON và dữ liệu gửi API phải dùng cú pháp được định nghĩa bởi định dạng đích.

Việc `NumberFormat.parse` trả `Number` thành công **không phải** bảo đảm bảo toàn chính xác chữ số đầu vào, tính hợp lệ miền nghiệp vụ hay grouping nghiêm ngặt. `setParseBigDecimal(true)` chỉ giúp phần giá trị thập phân được biểu diễn phù hợp hơn, không thay thế bước validate.

## 12. Những lỗi phổ biến

| Sai lầm | Vì sao sai | Cách làm tốt hơn |
|---|---|---|
| Coi `,` luôn là dấu nhóm và `.` luôn là thập phân | Vai trò có thể đảo theo locale | Truyền locale và dùng formatter/parser tương ứng |
| Viết `%,.2f` nhưng nghĩ flag comma phải in dấu phẩy | Formatter dùng flag cố định, output localized | Kiểm tra bằng US/Germany với cùng format |
| Lấy `Double.toString`/`%s` để hiển thị số theo locale | Chỉ có biểu diễn chuỗi không chọn grouping theo locale | `String.format(locale, "%,.2f", value)` hoặc `NumberFormat` |
| Tự viết `value.replace(".", ",")` | Dễ phá grouping, ký hiệu và nội dung | Dùng API formatting của Java |
| Gọi `new BigDecimal("1.234,50")` | Constructor không parse theo locale | Dùng parser localized có kiểm tra |
| Nhầm `applyPattern` và `applyLocalizedPattern` | Cú pháp ký hiệu pattern khác nhau | Dùng `applyPattern("#,##0.00")` với ký hiệu pattern chuẩn |
| Dùng `DecimalFormat.parse` rồi bỏ qua `ParsePosition` | Có thể chấp nhận đầu chuỗi hợp lệ và bỏ suffix | Xác thực parse toàn bộ chuỗi; validate thêm |
| Dùng `double` cho tiền mà không định nghĩa rounding | Sai lệch biểu diễn nhị phân và quy tắc làm tròn | `BigDecimal` + `RoundingMode` |
| Dùng percent formatter cho giá trị 12.5 khi muốn 12.5% | Percent formatter nhân 100 | Dùng tỷ lệ `0.125` |
| Ghi số localized vào JSON number hoặc cột CSV máy đọc | Định dạng máy đọc có grammar riêng | Dùng serializer và quy tắc định dạng chuẩn của file/giao thức |
| Giữ `DecimalFormat` mutable dùng chung nhiều thread | Không bảo đảm thread-safe | Formatter per-call hoặc đồng bộ thích hợp |
| Trông chờ `Locale` sửa font/encoding console | Locale chỉ quyết định quy ước định dạng | Kiểm tra charset terminal và font riêng |

## 13. Tình huống chọn API

### Tình huống A: In một bảng số lên console

Dùng `System.out.printf(locale, format, args...)` nếu cần chiều rộng cố định, căn cột và số chữ số thập phân rõ ràng:

```java
System.out.printf(Locale.US, "%-12s %12s%n", "Item", "Amount");
System.out.printf(Locale.US, "%-12s %,12.2f%n", "Books", 1234.5);
```

Đây là **đoạn minh họa** (cần import `Locale`); mã không tự giải quyết vấn đề độ rộng hiển thị Unicode hoặc emoji theo terminal. Với bảng sử dụng locale có dấu tách nhóm Unicode, cần kiểm tra thiết bị hiển thị có xử lý đúng chiều rộng các glyph hay không.

### Tình huống B: Hiển thị hóa đơn theo vùng của người dùng

Dùng `NumberFormat.getCurrencyInstance(locale)` và đồng tiền được xác định. Định nghĩa cách làm tròn bằng `BigDecimal` theo chính sách nghiệp vụ **trước khi in**; locale điều khiển cách hiển thị, không quyết định người dùng được tính tiền theo đơn vị nào.

### Tình huống C: Parse số người dùng nhập trong console

Dùng `Scanner.useLocale(locale)` với `hasNextBigDecimal` / `nextBigDecimal` khi ngữ pháp token của Scanner đáp ứng yêu cầu. Nếu cần kiểm tra toàn chuỗi hoặc pattern chính xác, đọc một dòng rồi dùng `DecimalFormat` + `ParsePosition` và bước validate riêng.

### Tình huống D: Lưu tỷ lệ hoặc số lượng vào file cấu hình

Dùng biểu diễn không grouping có cú pháp do file format quy định. Khi ứng dụng khác đọc file, không bắt nó biết locale trên máy đã tạo file. Dữ liệu máy đọc cần tính ổn định; chuỗi đẹp cho người dùng và chuỗi chuẩn cho giao thức là hai kết quả khác nhau.

### Tình huống E: Số lớn có phần thập phân chính xác

Dùng `BigDecimal` và `DecimalFormat` với pattern và `RoundingMode` phù hợp. Không chuyển qua `double` chỉ để áp dụng `printf` nếu làm mất chữ số có ý nghĩa.

## 14. Tự kiểm tra

**Câu 1.** Cùng `1234567.5` được format bằng `String.format(Locale.US, "%,.2f", n)` và bằng `Locale.GERMANY` thì kết quả là gì?  
**Đáp án:** `1,234,567.50` và `1.234.567,50`.

**Câu 2.** Dấu `,` trong `%,.2f` có buộc output chứa dấu phẩy không?  
**Đáp án:** không. Đó là grouping flag trong cú pháp `Formatter`; ký tự nhóm xuất ra do locale quy định.

**Câu 3.** `DecimalFormat` theo `Locale.GERMANY` có cần `applyPattern("#.##0,00")` không?  
**Đáp án:** không. `applyPattern("#,##0.00")` dùng cú pháp pattern chuẩn; dấu hiển thị được locale thay đổi. `applyLocalizedPattern` mới nhận cú pháp localized.

**Câu 4.** Người dùng nhập `"1.234,50"` theo Đức. `new BigDecimal("1.234,50")` có parse thành 1234.5 không?  
**Đáp án:** không. Constructor `BigDecimal(String)` không dùng `Locale`. Cần parser phù hợp và kiểm tra đầy đủ input.

**Câu 5.** `NumberFormat.getPercentInstance(Locale.US)` nhận `0.125` thì hiển thị phần trăm bao nhiêu?  
**Đáp án:** `12.5%` khi formatter được cấu hình một chữ số thập phân.

**Câu 6.** Vì sao cần so sánh `ParsePosition.getIndex()` với `input.length()`?  
**Đáp án:** parser có thể chỉ parse tiền tố hợp lệ rồi dừng, để lại phần dữ liệu chưa xử lý. Tuy nhiên kiểm tra này chưa đủ để xác thực mọi ràng buộc grouping/nghiệp vụ.

**Câu 7.** Với hóa đơn giá trị `BigDecimal("12.345")` cần hai chữ số thập phân và `HALF_UP`, kết quả US/DE là gì?  
**Đáp án:** `12.35` / `12,35`, cùng một kết quả làm tròn về giá trị và khác cách biểu diễn.

**Câu 8.** Có nên dùng cùng một `DecimalFormat` rồi đổi pattern theo từng request ở nhiều thread?  
**Đáp án:** không, trừ khi bảo vệ quyền truy cập thích hợp. `DecimalFormat` mutable và không bảo đảm thread-safe.

## Kết luận

**`Locale` quyết định quy tắc biểu diễn và phân tích số theo ngôn ngữ/vùng; giá trị số vẫn là giá trị số.** Với output console ngắn gọn, chọn `printf(locale, ...)` hoặc `String.format(locale, ...)`; với mẫu phức tạp, grouping, làm tròn và tiền tệ, dùng `NumberFormat` / `DecimalFormat`. Với input, chọn `Scanner.useLocale` hoặc parser locale có kiểm tra toàn chuỗi và điều kiện nghiệp vụ. Luôn phân biệt **dấu phân cách hiển thị**, **cú pháp pattern**, **độ chính xác số**, **charset console** và **định dạng dữ liệu máy đọc**.

### Tài liệu Java SE 17

- [`Locale`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html)
- [`Formatter` và các flag định dạng](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Formatter.html)
- [`NumberFormat`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/NumberFormat.html)
- [`DecimalFormat`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/DecimalFormat.html), [`DecimalFormatSymbols`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/DecimalFormatSymbols.html)
- [`Scanner`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Scanner.html)
- [`BigDecimal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html), [`RoundingMode`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="console-and-formatting-008">Auto-flush và các thao tác thực sự kích hoạt flush</a>

<details>
<summary>Xem chi tiết</summary>

# Auto-flush và các thao tác thực sự kích hoạt flush trong Java I/O

Khi ghi ra console, file hay socket, chương trình thường không chuyển ngay mọi ký tự hoặc byte tới nơi nhận. Một số lớp Java I/O dùng **bộ đệm** (*buffer*) để gom dữ liệu; thao tác **`flush()`** yêu cầu đẩy dữ liệu đang đệm xuống các tầng ghi bên dưới. **Auto-flush** là chế độ khiến một số thao tác nhất định tự kích hoạt flush.

Điểm quan trọng: **không có một quy tắc auto-flush áp dụng cho mọi lớp Java I/O**. `PrintWriter` và `PrintStream` có điều kiện kích hoạt khác nhau; `BufferedWriter.newLine()` tự nó không flush. Vì vậy “có ký tự xuống dòng” và “đã flush” không luôn tương đương.

## 1. `write`, `flush`, `close` khác nhau như thế nào?

- **`write`/`print`:** cung cấp dữ liệu cho luồng. Nếu có bộ đệm, dữ liệu có thể chưa chuyển tới tầng nhận kế tiếp.
- **`flush()`:** yêu cầu chuyển dữ liệu đã đệm xuống các tầng bên dưới; wrapper thường gọi tiếp `flush` của luồng được bọc. Điều này **không bảo đảm** byte đã bền vững trên ổ đĩa hoặc ứng dụng ở đầu bên kia mạng đã xử lý.
- **`close()`:** đóng luồng, thường hoàn tất dữ liệu còn đệm và giải phóng tài nguyên. Với luồng dùng chung như `System.out`, không đóng tùy tiện.

**Auto-flush** không có nghĩa “mọi lần `write` đều flush”, “mọi `\n` đều flush”, hoặc “không cần nghĩ đến buffering”. Phải xác định **kiểu đối tượng**, **constructor** và **phương thức thực sự được gọi**.

```text
Ứng dụng
    |
    | print/write/println/printf/format
    v
PrintWriter hoặc PrintStream
    |
    | có thể gọi flush nếu thỏa điều kiện autoFlush
    v
BufferedWriter / OutputStreamWriter / BufferedOutputStream
    |
    | flush xuống tầng thấp hơn
    v
OS buffer -> file / socket / terminal / thiết bị
```

Thời điểm dữ liệu hiển thị trong console hoặc tới ứng dụng khác còn phụ thuộc nhiều tầng. `flush()` là yêu cầu của API ghi, không phải ACK từ hệ thống nhận.

## 2. `PrintWriter`: các phương thức thực sự kích hoạt auto-flush

`PrintWriter` ghi **văn bản** qua một `Writer` hoặc `OutputStream`. Để bật auto-flush:

```java
PrintWriter out = new PrintWriter(destination, true);
```

Đây là đoạn mã minh họa: `destination` là `Writer` hoặc `OutputStream` đã có, với `import java.io.PrintWriter`. Cờ `true` bật auto-flush, `false` tắt. Các constructor thông thường không cung cấp `autoFlush` mặc định **tắt** chế độ này.

Theo hợp đồng `PrintWriter`, khi cờ auto-flush là `true`, các nhóm phương thức **kích hoạt flush**:

1. `println(...)`, kể cả `println()` không đối số;
2. `printf(...)`, ngay cả khi chuỗi format **không có** dấu xuống dòng;
3. `format(...)`, ngay cả khi chuỗi format **không có** dấu xuống dòng.

Ngược lại, chỉ gọi `print(...)`, `write(...)` hoặc `append(...)` **không kích hoạt auto-flush theo quy tắc của `PrintWriter`**, kể cả khi dữ liệu chứa `'\n'`.

| Thao tác với `PrintWriter` autoFlush=true | Có kích hoạt auto-flush theo hợp đồng? |
| --- | --- |
| `out.print("Hello")` | Không |
| `out.print("Hello\n")` | Không |
| `out.write("Hello\n")` | Không |
| `out.write('\n')` | Không |
| `out.append('\n')` | Không |
| `out.println("Hello")` | **Có** |
| `out.println()` | **Có** |
| `out.printf("n=%d", 7)` | **Có**, dù không có `%n` |
| `out.format("n=%d", 7)` | **Có**, dù không có `%n` |
| `out.flush()` | Chủ động flush, bất kể autoFlush |
| `out.close()` | Hoàn tất và đóng luồng |

**Bẫy quan trọng:** `out.println("x")` có thể vừa ghi ký tự kết thúc dòng của nền tảng vừa tự flush; `out.print("x\n")` chỉ ghi chuỗi có LF, **không tự flush** theo hợp đồng `PrintWriter`. Hai cách có thể nhìn giống nhau trên Linux nhưng khác lúc dữ liệu được đẩy xuống tầng dưới.

Khi `autoFlush=false`, `println`/`printf`/`format` **không tự kích hoạt flush bởi cờ đó**. Dữ liệu vẫn có thể được chuyển đi nếu buffer đầy, tầng dưới có cơ chế riêng, hoặc ta gọi `flush()` hay đóng luồng. Tắt auto-flush không có nghĩa dữ liệu hoàn toàn đứng yên cho đến lúc đóng.

## 3. Ví dụ Java 17+ chạy được: đo `flush()` của luồng đích

Thay vì dự đoán từ việc chữ xuất hiện trên terminal, ta tạo một `Writer` giả lập để đếm số lần `flush()` được gọi. Chương trình dùng `PrintWriter` với `autoFlush=true` và `BufferedWriter` không có auto-flush.

Lưu file tên **`AutoFlushDemo.java`**:

```java
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

public class AutoFlushDemo {

    public static void main(String[] args) throws IOException {
        SpyWriter printTarget = new SpyWriter();

        try (PrintWriter out = new PrintWriter(printTarget, true)) {
            out.print("A");
            System.out.println("PrintWriter after print: "
                    + printTarget.flushCount);

            out.write("\n");
            System.out.println("PrintWriter after write LF: "
                    + printTarget.flushCount);

            out.println("B");
            System.out.println("PrintWriter after println: "
                    + printTarget.flushCount);

            out.printf("n=%d", 7);
            System.out.println("PrintWriter after printf: "
                    + printTarget.flushCount);

            out.format(" m=%d", 8);
            System.out.println("PrintWriter after format: "
                    + printTarget.flushCount);

            out.flush();
            System.out.println("PrintWriter after explicit flush: "
                    + printTarget.flushCount);
        }

        SpyWriter bufferedTarget = new SpyWriter();
        try (BufferedWriter out = new BufferedWriter(bufferedTarget)) {
            out.write("C");
            out.newLine();
            System.out.println("BufferedWriter after newLine: "
                    + bufferedTarget.flushCount);

            out.flush();
            System.out.println("BufferedWriter after explicit flush: "
                    + bufferedTarget.flushCount);
        }
    }

    static final class SpyWriter extends Writer {
        private final StringBuilder characters = new StringBuilder();
        int flushCount;

        @Override
        public void write(char[] chars, int offset, int length) {
            characters.append(chars, offset, length);
        }

        @Override
        public void flush() {
            flushCount++;
        }

        @Override
        public void close() {
            // Nguồn đích giả lập chỉ dùng để quan sát flush.
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac -encoding UTF-8 AutoFlushDemo.java
java AutoFlushDemo
```

**Stdout đã kiểm chứng:**

```text
PrintWriter after print: 0
PrintWriter after write LF: 0
PrintWriter after println: 1
PrintWriter after printf: 2
PrintWriter after format: 3
PrintWriter after explicit flush: 4
BufferedWriter after newLine: 0
BufferedWriter after explicit flush: 1
```

Giải thích:

- `print("A")` và `write("\n")` không auto-flush, nên bộ đếm vẫn 0.
- `println("B")` tự flush, đưa bộ đếm lên 1.
- `printf("n=%d", 7)` và `format(" m=%d", 8)` mỗi lệnh flush thêm một lần dù không có newline.
- `flush()` trực tiếp tăng bộ đếm lên 4.
- `BufferedWriter.newLine()` chỉ viết dấu kết thúc dòng; `flush()` tường minh mới khiến `SpyWriter` nhận được lệnh flush.

`SpyWriter` chỉ đo lời gọi `flush()` vào **writer đích**, không đo độ bền vật lý của file hay độ trễ nhận dữ liệu qua mạng. Ví dụ in kết quả **trước khi wrapper đóng** để không gộp hoạt động close vào phép đo.

## 4. `PrintStream`: quy tắc khác `PrintWriter`

`PrintStream` là lớp xuất byte có các phương thức in văn bản; `System.out` và `System.err` có kiểu `PrintStream`. Ta có thể tạo với auto-flush tường minh:

```java
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

// Giả sử đã có OutputStream destination:
PrintStream out = new PrintStream(
        destination, true, StandardCharsets.UTF_8
);
```

Theo tài liệu API, khi cờ auto-flush của `PrintStream` bật, các sự kiện có thể kích hoạt flush xuống luồng byte bên dưới là:

- **Ghi mảng byte**, như `write(byte[])`, `write(byte[], off, len)` hoặc `writeBytes(byte[])`;
- **Gọi `println(...)`**;
- **Ghi ký tự hoặc byte newline `'\n'`**.

Như vậy, `PrintStream.write('\n')` **kích hoạt auto-flush**, trong khi `PrintWriter.write("\n")` **không kích hoạt auto-flush theo hợp đồng riêng của `PrintWriter`**.

| Thao tác với `PrintStream` autoFlush=true | Kết luận |
| --- | --- |
| `out.write('X')` | Không có trigger newline byte |
| `out.write('\n')` | **Có** trigger newline byte |
| `out.write(new byte[] {65, 66})` | **Có** trigger ghi byte array |
| `out.writeBytes(new byte[] {65, 66})` | **Có** trigger ghi byte array |
| `out.println("X")` | **Có** trigger `println` |
| `out.print("X\n")` | Có newline character trong dữ liệu |
| `out.print("X")` | Không dùng riêng lời gọi này như cam kết flush khi tương tác |
| `out.printf("n=%d", 7)` | Không lấy tên `printf` làm trigger riêng; xét byte/newline thực sự ghi |
| `out.flush()` | Chủ động flush bất kể cấu hình |

`PrintStream` có thể mã hóa ký tự thành byte qua các bước nội bộ. Vì thế, không nên khẳng định *số lần flush chính xác* của mọi `print(...)` hoặc `printf(...)` chỉ từ bảng API; hãy quan tâm những sự kiện được đặc tả và gọi `flush()` nếu thời điểm là yêu cầu thiết kế.

Ví dụ:

```java
byte[] payload = {65, 66};
out.write(payload);   // PrintStream autoFlush=true: ghi mảng byte.
out.write('\n');      // PrintStream autoFlush=true: newline byte.
```

`out` ở đây là `PrintStream` đã mở. `write` ghi **byte thô**; không tự chuyển payload thành văn bản có charset. Nếu `out` là `BufferedOutputStream` hoặc một `OutputStream` khác, không thể áp dụng quy tắc auto-flush của `PrintStream` cho nó.

## 5. `BufferedWriter.newLine()` và `BufferedOutputStream`

`BufferedWriter` tối ưu ghi ký tự; `BufferedOutputStream` tối ưu ghi byte. Cả hai có `flush()` và `close()` nhưng **không có cờ autoFlush=true như `PrintWriter`/`PrintStream`**.

```java
writer.write("Dòng 1");
writer.newLine();
writer.write("Dòng 2");
writer.flush();
```

Đoạn minh họa giả định `writer` là `BufferedWriter` đã mở. `newLine()` chỉ ghi line separator theo nền tảng: trên nhiều hệ Unix dùng LF, còn Windows thường dùng CRLF. Nó **không tự flush**.

Với byte stream:

```java
output.write(bytes);
output.flush();
```

`output` là `BufferedOutputStream` đã mở và `bytes` là byte array. Buffer có thể chuyển dữ liệu khi đầy hoặc theo cơ chế nội bộ, nhưng việc ghi ký tự `'\n'` vào buffered stream **không mang theo quy tắc auto-flush của `PrintStream`**.

Điểm dễ nhầm: `BufferedWriter.newLine()` và `PrintWriter.println()` có thể tạo cùng dấu kết thúc dòng. Nhưng `println()` của `PrintWriter` có auto-flush nếu cờ bật; `newLine()` của `BufferedWriter` thì không.

## 6. Console: hiển thị prompt trước khi đọc input

Ví dụ một chương trình cần in lời nhắc không kết thúc dòng, rồi chờ người dùng:

```java
import java.util.Scanner;

System.out.print("Nhập tên: ");
System.out.flush();  // Đẩy prompt trước khi chờ người dùng.

Scanner scanner = new Scanner(System.in);
String name = scanner.nextLine();
System.out.println("Xin chào, " + name);
```

Đây là đoạn minh họa cần đặt trong `main` và có import `Scanner`. Lệnh `flush()` giúp mã **chủ động** xác định thời điểm đẩy prompt. Không chỉ dựa vào giả định “console tự hiển thị ngay”, vì ứng dụng có thể chạy trong IDE, pipe, terminal, test harness hoặc chuyển hướng `stdout`.

`System.out` là `PrintStream`, nhưng không nên dựa vào cấu hình auto-flush chưa được xác minh để quyết định lúc hiển thị prompt; chương trình hoặc framework còn có thể thay `System.out` qua `System.setOut(...)`. Khi cần flush rõ ràng, gọi `System.out.flush()`.

Không đóng `System.in`/`System.out` ở giữa ứng dụng chỉ để đẩy prompt: chúng là tài nguyên dùng chung. Tùy quyền sở hữu, cũng cần cân nhắc trước khi đóng `Scanner` bọc `System.in`.

## 7. `printf`, `format` và `%n`

Với `PrintWriter` autoFlush=true, **`printf`/`format` tự flush ngay cả khi format không có newline**:

```java
writer.printf("Tiến độ: %d%%", 75);
writer.format(" | Tổng: %d", 100);
```

`writer` ở đây là `PrintWriter` bật auto-flush. Trong format, `%%` là dấu phần trăm literal.

Ba cơ chế cần phân biệt:

- **`"\n"`:** ký tự LF cố định, không nhất thiết là line separator theo nền tảng.
- **`"%n"`:** format specifier tạo dấu xuống dòng theo nền tảng.
- **`println(...)`:** phương thức in một dòng và kết thúc dòng; với `PrintWriter` bật auto-flush, còn trigger flush.

Ví dụ:

```java
writer.print("OK\n");     // Không auto-flush theo PrintWriter.
writer.printf("OK");      // Auto-flush, dù không xuống dòng.
writer.printf("OK%n");    // Vừa xuống dòng, vừa auto-flush.
```

Các đoạn trên không có ý nghĩa giống hệt nhau trên mọi loại `Writer` hoặc `PrintStream`. Luôn xác định API đang thực sự gọi.

## 8. `flush()` có phải “ghi thành công” không?

`flush()` có thể chuyển byte khỏi bộ đệm ở tầng Java nhưng **không tương đương**:

- “File đã được ghi bền vững lên thiết bị lưu trữ”: hệ điều hành và thiết bị vẫn có thể đệm. Nếu cần yêu cầu đồng bộ file, phải chọn API và giới hạn phù hợp, ví dụ `FileChannel.force(...)`.
- “Đã đến tận ứng dụng bên kia mạng”: socket/network và ứng dụng nhận có trạng thái riêng. Muốn biết phía nhận đã **xử lý**, cần xác nhận ở tầng giao thức.
- “Không có lỗi I/O”: lời gọi ghi có thể lỗi, và cách báo lỗi tùy lớp.

### `close()` là kết thúc vòng đời

Thông thường dùng `try-with-resources`:

```java
try (BufferedWriter writer = Files.newBufferedWriter(
        path, StandardCharsets.UTF_8)) {
    writer.write("Nội dung");
    writer.newLine();
}
```

Đây là phương thức/khối minh họa cần biến `path` và import `BufferedWriter`, `Files`, `StandardCharsets`. `close()` thường hoàn tất phần đệm còn lại và đóng tài nguyên. Nếu cần **tiếp tục ghi**, hãy `flush()` khi cần, đừng `close()`.

### Lỗi và `checkError()`

`PrintWriter` và `PrintStream` không báo `IOException` trực tiếp từ các phương thức in thông thường; chúng ghi nhận lỗi nội bộ. Có thể kiểm tra:

```java
writer.println("Đã ghi");
if (writer.checkError()) {
    throw new IllegalStateException("Không thể ghi đầu ra");
}
```

`writer` ở đây là `PrintWriter`. **`checkError()` có thể tự gọi flush trước khi kiểm tra trạng thái lỗi**. Do đó khi đo đạc “phương thức nào đã trigger flush”, đừng chen `checkError()` giữa các phép đo mà bỏ qua tác dụng phụ này.

Khác với hai lớp Print, `BufferedWriter.write()` và `BufferedWriter.flush()` có thể ném `IOException`, cho phép ứng dụng xử lý ngoại lệ theo hợp đồng thông thường.

## 9. Auto-flush và hiệu năng

Auto-flush phù hợp với tương tác khi bên nhận cần thấy dữ liệu sớm, nhưng flush quá nhiều có thể làm mất lợi ích của buffering.

Ví dụ:

```java
for (int i = 0; i < 10_000; i++) {
    writer.println(i);
}
```

Nếu `writer` là `PrintWriter` với auto-flush bật, **mỗi lần `println` đều kích hoạt flush**. Với file hoặc mạng, điều này có thể tạo nhiều lượt xử lý I/O không cần thiết.

Khi ghi lượng lớn dữ liệu và không cần thấy ngay từng dòng, cân nhắc `BufferedWriter` hoặc `PrintWriter` tắt auto-flush, kết hợp flush theo đợt và đóng đúng. Ngược lại, với prompt hoặc giao thức hai chiều đang chờ dữ liệu, trì hoãn flush có thể khiến cả hai phía cùng chờ.

**Không có một cấu hình “luôn nhanh nhất”.** Chọn các mốc flush theo nhu cầu giao tiếp và đo đạc hiệu năng với ứng dụng thực tế.

## 10. Bảng đối chiếu nhanh

| Lớp/kiểu luồng | Khi nào flush? | Điều không nên suy luận |
| --- | --- | --- |
| `BufferedWriter` | `flush()`, đóng, hoặc cơ chế chuyển buffer | `newLine()` không tự flush |
| `BufferedOutputStream` | `flush()`, đóng, hoặc buffer được chuyển đi | Ghi newline không tạo trigger riêng |
| `PrintWriter` autoFlush=false | Flush chủ động/đóng hoặc tầng dưới xử lý | `println` không auto-flush khi cờ tắt |
| `PrintWriter` autoFlush=true | **`println`, `printf`, `format`**, flush chủ động/đóng | `print("\n")` và `write("\n")` không tự flush |
| `PrintStream` autoFlush=true | **Ghi byte array, `println`, newline**, flush chủ động/đóng | Không áp dụng y nguyên bảng của `PrintWriter` |
| `System.out` | Tùy `PrintStream` thực tế và cách dùng; có `flush()` | Không mặc định mọi lần `print` đều flush ngay |

Các lớp bên dưới có thể đẩy dữ liệu sớm khi buffer đầy; bảng nêu **điểm trigger theo hợp đồng**, không phải thời gian vật lý byte được hiển thị hoặc gửi đi.

## 11. Những lỗi thường gặp

| Sai lầm | Hậu quả/giải thích | Cách làm |
| --- | --- | --- |
| “`print("\n")` như `println()` về auto-flush” | Sai với `PrintWriter` | Gọi `println` hoặc `flush()` |
| “`BufferedWriter.newLine()` tự flush” | Chỉ viết line separator | Gọi `flush()` ở mốc cần đồng bộ |
| “`printf` phải có `%n` mới flush” | `PrintWriter` trigger trên lời gọi `printf` | Phân biệt format và auto-flush |
| “`PrintStream` và `PrintWriter` giống nhau” | Trigger của `PrintStream` gồm cả byte array/newline | Xem đúng API của lớp |
| “Flush nghĩa là file an toàn nếu mất điện” | OS/thiết bị còn trạng thái riêng | Dùng kỹ thuật đồng bộ file nếu cần |
| “Flush nghĩa là server đã xử lý” | Không có ACK của bên nhận | Thiết kế giao thức xác nhận |
| “Không thấy prompt tức là dữ liệu đã mất” | Có thể còn buffering hoặc chuyển hướng stdout | Flush chủ động và kiểm tra môi trường |
| “Dùng close để hiển thị prompt” | Close kết thúc luồng, không thể tiếp tục ghi | Dùng `flush()` |
| “`checkError` chỉ đọc trạng thái” | Có thể gọi flush | Tính tới tác dụng phụ |
| “Auto-flush luôn tăng hiệu năng” | Flush liên tục có overhead | Chọn tần suất flush hợp lý |

## 12. Tự kiểm tra

1. `new PrintWriter(target, true)` rồi `print("A\n")` có auto-flush không? **Không**.
2. Cùng writer gọi `printf("x=%d", 3)` không có newline có auto-flush không? **Có**.
3. `BufferedWriter.newLine()` có tự flush không? **Không**.
4. `PrintStream` autoFlush=true gọi `write('\n')` có trigger không? **Có**, do newline byte.
5. `PrintStream` autoFlush=true gọi `write(new byte[]{1, 2})` có trigger không? **Có**, do ghi mảng byte.
6. Vì sao nên `System.out.flush()` sau `print("Nhập: ")` trước `Scanner.nextLine()`? **Để chủ động đẩy prompt** trước khi chờ input.
7. `flush()` có chứng minh byte được ghi bền vững vào đĩa hoặc server đã xử lý không? **Không**.
8. `PrintWriter.checkError()` có thể ảnh hưởng số lần flush không? **Có**, vì phương thức có thể gọi flush khi kiểm tra.
9. Với một triệu dòng không cần cập nhật tức thời, auto-flush mỗi dòng có thể gây gì? **Tăng overhead I/O**.

**Ghi nhớ:** `PrintWriter` auto-flush khi gọi **`println`, `printf`, `format`** (nếu bật); `PrintStream` auto-flush theo **`println`, ghi mảng byte và newline** (nếu bật); `BufferedWriter` cần **`flush()` chủ động** ở mốc cần đẩy dữ liệu. Khi yêu cầu thời điểm quan trọng, gọi `flush()` rõ ràng và đừng nhầm với bảo đảm lưu bền vững hay bên nhận đã xử lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="console-and-formatting-009">checkError và các lỗi I/O không được ném trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# checkError() và các lỗi I/O không được ném trực tiếp trong Java

Trong Java I/O, phần lớn luồng ghi có thể báo thất bại bằng `IOException`. Tuy nhiên, hai lớp chuyên in và định dạng đầu ra là **`java.io.PrintStream`** và **`java.io.PrintWriter`** có cách báo lỗi khác: các thao tác ghi thông thường **không ném `IOException` trực tiếp** cho người gọi, mà ghi nhận lỗi vào trạng thái nội bộ. Phương thức **`checkError()`** cho phép kiểm tra trạng thái đó.

Điều này đặc biệt quan trọng khi ghi log, xuất báo cáo, tạo văn bản định dạng hoặc in ra console. Việc `print()`, `println()` hoặc `printf()` chạy xong mà không xuất hiện `IOException` **không đồng nghĩa dữ liệu đã được ghi thành công**.

Bài học nằm trong chủ đề **Console và định dạng** của Java Core I/O; trọng tâm là cách nhận biết, giải thích và xử lý lỗi đối với các API có cơ chế “giữ lỗi trong cờ trạng thái”.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Phân biệt cách báo lỗi của `PrintStream`, `PrintWriter` so với `OutputStream` và `Writer` thông thường.
- Nêu đúng hợp đồng `checkError()`, bao gồm việc kiểm tra có thể **flush** đầu ra.
- Hiểu vì sao `print()`, `println()`, `printf()` chạy không ném `IOException` vẫn có thể mất dữ liệu.
- Sử dụng `checkError()` tại điểm thích hợp và biết rằng boolean này **không cho biết nguyên nhân chi tiết**.
- Phân biệt `autoFlush` của `PrintStream` với `PrintWriter` và nhận diện các ngoại lệ **không phải** lỗi I/O vẫn có thể được ném.
- Viết chương trình Java 17+ tái hiện lỗi ghi một cách tất định, không phụ thuộc ổ đĩa, mạng hay terminal thực tế.
- Chọn API cấp thấp có `throws IOException` khi nghiệp vụ cần quản lý lỗi và nguyên nhân cụ thể.

## 2. Vì sao hai lớp in định dạng xử lý lỗi đặc biệt?

### `PrintStream`

`java.io.PrintStream` kế thừa `FilterOutputStream` và được dùng để in biểu diễn văn bản của nhiều kiểu giá trị:

~~~java
stream.print("name=");
stream.println("Java");
stream.printf("count=%d%n", 3);
~~~

`System.out` và `System.err` thông thường là hai `PrintStream` được JVM cung cấp. `PrintStream` cũng có các thao tác ghi byte, nhưng `print`/`println`/`printf` tập trung vào xuất ký tự đã được mã hóa xuống đầu ra byte.

Theo hợp đồng `PrintStream` trong Java 17, **các thao tác ghi không truyền `IOException` thông thường cho bên gọi**; khi luồng nền báo lỗi I/O, lớp in ghi nhớ lỗi vào một cờ để `checkError()` kiểm tra. Ngoại lệ đáng chú ý là `InterruptedIOException` từ luồng nền: `PrintStream` xử lý bằng cách đặt trạng thái ngắt của thread thay vì đơn giản ghi nhận như lỗi I/O thông thường.

### `PrintWriter`

`java.io.PrintWriter` kế thừa `Writer`, cung cấp các thao tác in văn bản tương tự `PrintStream`:

~~~java
writer.print("name=");
writer.println("Java");
writer.printf("count=%d%n", 3);
~~~

`PrintWriter` làm việc với **ký tự**; bản thân nó không có phương thức ghi byte thô giống `OutputStream`. Các phương thức của `PrintWriter` không truyền `IOException` do ghi/flush/close ra ngoài theo cách mà `Writer` thông thường có thể làm; muốn phát hiện tình trạng lỗi phải kiểm tra `checkError()`.

**Phân biệt quan trọng:** việc khởi tạo `PrintStream`/`PrintWriter` bằng constructor mở file vẫn **có thể ném `IOException`** (hoặc ngoại lệ checked cụ thể) khi không mở/tạo được file. “Không ném IOException” ở đây chủ yếu nói đến cơ chế báo lỗi **khi đã có đối tượng in và thực hiện thao tác I/O**, không phải miễn mọi lỗi ở mọi giai đoạn.

## 3. Bảng so sánh các API ghi

| Kiểu | Đơn vị làm việc | Lỗi I/O lúc ghi/flush/close | Cách người gọi nhận biết |
| --- | --- | --- | --- |
| `OutputStream`, ví dụ `FileOutputStream` | Byte | Phương thức thường khai báo `throws IOException` | Bắt/lan truyền ngoại lệ |
| `Writer`, ví dụ `BufferedWriter` | Ký tự | Phương thức thường khai báo `throws IOException` | Bắt/lan truyền ngoại lệ |
| `PrintStream` | In định dạng, ghi byte | Không truyền `IOException` I/O thông thường từ thao tác in/ghi | `checkError()`; lưu ý trạng thái ngắt riêng |
| `PrintWriter` | In định dạng, ghi ký tự | Không truyền `IOException` I/O thông thường từ thao tác in/ghi | `checkError()` |
| `Formatter` | Định dạng theo mẫu | Lỗi I/O với `Appendable` có thể được ghi nhận thay vì ném trực tiếp | `ioException()` |

Không phải mọi thứ mang tên “print” đều có cùng hợp đồng. Khi chọn cách xử lý lỗi, hãy nhìn vào **kiểu API cụ thể** và trách nhiệm của tầng gọi.

## 4. `checkError()` hoạt động thế nào?

Hai lớp đều có:

~~~java
boolean failed = output.checkError();
~~~

Với `PrintStream`, `checkError()` **flush đầu ra nếu còn mở**, sau đó kiểm tra trạng thái lỗi. Với `PrintWriter`, `checkError()` cũng **flush nếu writer chưa đóng** rồi kiểm tra trạng thái lỗi. Do đó lời gọi này **không đơn thuần là đọc một biến boolean**; nó có thể kích hoạt một thao tác I/O còn đang được hoãn.

| Kết quả | Có thể kết luận | Không được kết luận |
| --- | --- | --- |
| `true` | Lớp in đã ghi nhận một tình trạng lỗi theo cơ chế của nó | Không biết chắc lỗi gốc là gì, bao nhiêu byte/ký tự đã ra đích hoặc có thể tiếp tục an toàn |
| `false` | Tại thời điểm kiểm tra không có lỗi được ghi nhận trong trạng thái mà API quan sát được | Không chứng minh dữ liệu đã ghi bền vững xuống đĩa, tới máy nhận, hay sẽ không lỗi sau này |

**Ví dụ thường gặp:** `print("abc")` chỉ đưa dữ liệu vào buffer; `checkError()` có thể dẫn đến flush, và chính flush làm tầng dưới phát sinh `IOException`. Khi đó `checkError()` trả `true`. Nếu chưa hề kiểm tra, chương trình có thể tưởng thao tác in đã thành công.

### Cờ lỗi thường “dính” sau khi đã được đặt

Cả hai lớp đều cung cấp `protected setError()` và `protected clearError()`. Khi cờ đã thành `true`, các lần `checkError()` sau vẫn có thể trả `true` cho đến khi cờ được xóa bằng cơ chế nội bộ/phân lớp phù hợp, kể cả khi một thao tác về sau không gặp lỗi mới.

**Không có API public để lấy hoặc reset toàn bộ lỗi một cách đáng tin cậy** trên một đối tượng `PrintStream`/`PrintWriter` thông thường. `clearError()` là `protected`, không phải lời khuyên để caller tự “phớt lờ lỗi rồi tiếp tục”. Nếu đầu ra đã hỏng, thường phải điều tra, thay thế tài nguyên hoặc báo thất bại ở tầng nghiệp vụ.

Đặc biệt, `checkError()` không trả về `IOException` gốc, stack trace hay đường dẫn file lỗi. Nếu cần giữ nguyên nguyên nhân, dùng `Writer`/`OutputStream` thích hợp hoặc tạo lớp giám sát riêng khi thiết kế API.

## 5. Ví dụ Java 17+ chạy được: `PrintStream` nuốt lỗi ghi I/O

Ta có thể tạo `OutputStream` giả lập: bất cứ lần ghi nào cũng ném `IOException`. Dùng nó làm đích của `PrintStream` sẽ cho kết quả ổn định, không cần làm hỏng file thật.

Tạo **`PrintStreamErrorDemo.java`**:

~~~java
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class PrintStreamErrorDemo {
    static final class BrokenOutputStream extends OutputStream {
        @Override
        public void write(int value) throws IOException {
            throw new IOException("simulated disk failure");
        }
    }

    public static void main(String[] args) {
        try (PrintStream out = new PrintStream(
                new BrokenOutputStream(), true, StandardCharsets.UTF_8)) {

            out.println("record-1");

            System.out.println("afterPrintln=continued");
            System.out.println("checkError=" + out.checkError());
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac PrintStreamErrorDemo.java
java PrintStreamErrorDemo
~~~

**Stdout chính xác:**

~~~text
afterPrintln=continued
checkError=true
~~~

Vì sao?

1. `BrokenOutputStream.write(int)` ném `IOException` mỗi khi có thao tác ghi thực tế.
2. `PrintStream.println` thử ghi xuống stream bên dưới; khi gặp lỗi I/O thông thường, `PrintStream` ghi nhận lỗi thay vì trả ngoại lệ checked lên `main`.
3. Dòng `afterPrintln=continued` chứng minh **luồng điều khiển tiếp tục**.
4. `checkError()` trả `true` vì cờ lỗi đã được bật.
5. `try-with-resources` vẫn đóng tài nguyên in, nhưng bản thân `close()` của `PrintStream` cũng không truyền `IOException` I/O thông thường ra ngoài.

Đây là ví dụ minh họa việc **dữ liệu ghi có thể thất bại mà chương trình vẫn tiếp tục**. Nó không chứng minh ghi vào console hay file luôn thất bại; việc tạo một nguồn hỏng làm trường hợp lỗi tái hiện được.

## 6. Ví dụ Java 17+ chạy được: `PrintWriter` cũng cần `checkError()`

`PrintWriter` dùng `Writer` bên dưới. Một writer có thể ném `IOException` khi được yêu cầu ghi, nhưng `PrintWriter` không truyền lỗi đó ra như `BufferedWriter` thông thường.

Tạo **`PrintWriterErrorDemo.java`**:

~~~java
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

public class PrintWriterErrorDemo {
    static final class BrokenWriter extends Writer {
        @Override
        public void write(char[] chars, int offset, int length)
                throws IOException {
            throw new IOException("simulated writer failure");
        }

        @Override
        public void flush() {
            // Không có buffer bên dưới.
        }

        @Override
        public void close() {
            // Không có tài nguyên thực để đóng.
        }
    }

    public static void main(String[] args) {
        try (PrintWriter out = new PrintWriter(new BrokenWriter(), true)) {
            out.println("record-2");

            System.out.println("afterPrintln=continued");
            System.out.println("checkError=" + out.checkError());
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac PrintWriterErrorDemo.java
java PrintWriterErrorDemo
~~~

**Stdout chính xác:**

~~~text
afterPrintln=continued
checkError=true
~~~

`BrokenWriter` cố tình lỗi trên thao tác ghi mảng ký tự. `PrintWriter` nhận lỗi từ writer bên dưới và đánh dấu trạng thái lỗi nội bộ. `println()` trở về như một thao tác thông thường; `checkError()` mới chỉ ra đã xảy ra vấn đề.

**Điểm dễ nhầm:** nếu bạn gọi trực tiếp `BrokenWriter.write(...)`, người gọi phải xử lý `IOException`. Nhưng khi gọi qua `PrintWriter`, lỗi I/O có thể bị chuyển thành cờ thay vì được ném.

## 7. So sánh với `BufferedWriter` khi muốn biết lỗi gốc

Trong tác vụ cần phản ứng nghiêm ngặt, chẳng hạn ghi báo cáo bắt buộc đầy đủ, ghi file cấu hình hoặc xuất dữ liệu mà lỗi ghi phải làm thao tác nghiệp vụ thất bại, `BufferedWriter` hoặc `OutputStream` có thể phù hợp hơn vì không cố ý giấu nguyên nhân I/O.

~~~java
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

public final class StrictWrite {
    private StrictWrite() {}

    public static void writeRequiredRecord(
            Writer destination, String record) throws IOException {
        BufferedWriter buffered = new BufferedWriter(destination);
        buffered.write(record);
        buffered.newLine();
        buffered.flush();
    }
}
~~~

Lớp có thể biên dịch nhưng không có `main`. Nó không đóng `destination` vì ownership vẫn thuộc bên gọi; tuy nhiên cần bảo đảm hàm hoàn tất `flush()` và chính sách đóng ở caller. `BufferedWriter` sẽ ném hoặc lan truyền `IOException` nếu tầng dưới báo lỗi, giúp caller có nguyên nhân cụ thể để xử lý/log và báo thất bại.

Trong thực tế, nếu bạn là người **mở file**, có thể bọc việc mở writer bằng `try-with-resources`:

~~~java
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReportWriter {
    private ReportWriter() {}

    public static void save(Path target, String report) throws IOException {
        try (BufferedWriter writer =
                     Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write(report);
            writer.newLine();
        }
    }
}
~~~

Đây là lớp không có `main`. `Files.newBufferedWriter` có thể tạo file mới hoặc truncate file đích theo mặc định; caller phải chọn đường dẫn/chiến lược cập nhật an toàn. Nếu ghi hoặc đóng gặp lỗi I/O, thao tác có thể ném `IOException` ra ngoài thay vì chỉ đặt cờ. Với yêu cầu ghi nguyên tử, cần có thiết kế file tạm và thay thế file đích sau khi xác minh.

**Lưu ý:** việc `flush()` hoặc `close()` kết thúc thành công vẫn chưa bảo đảm dữ liệu đã được đồng bộ bền vững xuống thiết bị lưu trữ. `checkError() == false` còn là bằng chứng yếu hơn; nó chỉ phản ánh trạng thái báo lỗi được lớp in biết đến.

## 8. `autoFlush` và vì sao hai lớp không giống nhau

Cả `PrintStream` và `PrintWriter` đều có constructor nhận cờ `autoFlush`, nhưng các sự kiện kích hoạt flush khác nhau.

### `PrintStream` với `autoFlush=true`

Theo tài liệu Java 17, tự flush có thể xảy ra khi:

- Ghi một **mảng byte**.
- Gọi một phương thức `println(...)`.
- Ghi một ký tự hoặc byte newline `'\n'`.

`PrintStream` làm việc với byte dưới lớp mã hóa, nên hợp đồng tự flush này **không giống** hành vi của `PrintWriter`.

### `PrintWriter` với `autoFlush=true`

Tự flush xảy ra khi gọi:

- `println(...)`
- `printf(...)`
- `format(...)`

**Không có bảo đảm autoFlush chỉ vì bạn tự ghi ký tự `'\n'` bằng `write('\n')`** hoặc chỉ gọi `print(...)` không có newline. Ngay cả khi `autoFlush=false`, `checkError()` vẫn có thể flush nếu writer còn mở.

| Hành động | `PrintStream(autoFlush=true)` | `PrintWriter(autoFlush=true)` |
| --- | --- | --- |
| `println("x")` | Tự flush | Tự flush |
| `write('\n')` | Tự flush theo hợp đồng `PrintStream` | Không có autoFlush theo chỉ sự kiện này |
| `print("x")` không newline | Không suy ra luôn flush | Không suy ra luôn flush |
| `checkError()` khi chưa đóng | Flush để kiểm tra lỗi | Flush để kiểm tra lỗi |

### Demo độc lập: khác nhau ở `write('\n')`

Tạo file **`AutoFlushDifferenceDemo.java`**:

~~~java
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class AutoFlushDifferenceDemo {
    static final class CountingOutputStream extends OutputStream {
        int flushes;

        @Override
        public void write(int value) {
            // Cố ý bỏ dữ liệu để chỉ đếm flush.
        }

        @Override
        public void flush() {
            flushes++;
        }
    }

    static final class CountingWriter extends StringWriter {
        int flushes;

        @Override
        public void flush() {
            flushes++;
        }
    }

    public static void main(String[] args) throws IOException {
        CountingOutputStream byteTarget = new CountingOutputStream();
        try (PrintStream out = new PrintStream(
                byteTarget, true, StandardCharsets.UTF_8)) {
            out.write('\n');
            System.out.println("streamFlushed=" + (byteTarget.flushes > 0));
        }

        CountingWriter charTarget = new CountingWriter();
        try (PrintWriter out = new PrintWriter(charTarget, true)) {
            out.write('\n');
            System.out.println("writerFlushedAfterWrite="
                    + (charTarget.flushes > 0));

            out.println("done");
            System.out.println("writerFlushedAfterPrintln="
                    + (charTarget.flushes > 0));
        }
    }
}
~~~

Chạy Java 17+:

~~~bash
javac AutoFlushDifferenceDemo.java
java AutoFlushDifferenceDemo
~~~

Stdout:

~~~text
streamFlushed=true
writerFlushedAfterWrite=false
writerFlushedAfterPrintln=true
~~~

Bộ đếm chỉ được đọc **trước khi đóng các stream**, nên kết quả không bị ảnh hưởng bởi `close()`. Không khẳng định số lần flush chính xác của `PrintStream`, vì một phương thức có thể gọi flush nhiều hơn một lần tùy cách triển khai; ví dụ chỉ kiểm tra có ít nhất một lần.

**Hiệu năng:** `autoFlush=true` có thể khiến hệ thống chuyển dữ liệu xuống tầng dưới thường xuyên hơn, hữu ích cho phản hồi tương tác nhưng có chi phí khi ghi nhiều mục nhỏ. `autoFlush=false` thường phù hợp khi đợi đến cuối một đợt và chủ động flush. Dù theo cách nào, nếu tính chính xác quan trọng, vẫn cần kiểm tra kết quả ghi chứ không chỉ dựa vào việc method đã trả về.

## 9. Mẫu xử lý lỗi ở ranh giới tác vụ

Giả sử bạn buộc phải dùng `PrintWriter` vì đang sử dụng các phương thức in định dạng. Một chiến lược tối thiểu là:

~~~java
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

public final class CheckedPrintTask {
    private CheckedPrintTask() {}

    public static void writeRecord(
            Writer sink, String name, int quantity) throws IOException {
        PrintWriter printer = new PrintWriter(sink, false);

        printer.printf("item=%s, quantity=%d%n", name, quantity);

        if (printer.checkError()) {
            throw new IOException("Formatted output failed");
        }
    }
}
~~~

Lớp có thể biên dịch; caller cấp `Writer` và quản lý lúc đóng nó. `checkError()` yêu cầu flush trước khi trả trạng thái, nên lỗi ở tầng dưới vào thời điểm flush có cơ hội được phát hiện.

Nhưng cách này có các giới hạn:

1. **Không giữ `IOException` gốc:** bạn chỉ có boolean để tạo ngoại lệ mô tả chung.
2. **Không phân biệt ghi một phần:** có thể đã ghi một phần dữ liệu trước khi báo lỗi.
3. **Không thay thế kiểm tra lỗi lúc đóng đích thực sự:** nếu caller đóng `sink` sau đó mới xảy ra lỗi, cần chính sách báo lỗi của `sink` và cách sở hữu tài nguyên rõ ràng.
4. **Không chắc lỗi xảy ra chính xác ở bản ghi đang viết:** trạng thái lỗi từ thao tác trước có thể vẫn còn.
5. Nếu `sink` chính là một `PrintWriter` khác, các lớp wrapper có thể che giấu thông tin gốc sâu hơn; nên tránh lồng các lớp print không cần thiết.

Khi bạn kiểm soát cả API và vòng đời tài nguyên, thường **tốt hơn dùng `Writer`/`BufferedWriter` trực tiếp** với `throws IOException` thay vì ép một đường đi qua `PrintWriter` rồi tái tạo một ngoại lệ không có nguyên nhân.

### Đọc trạng thái sau `close()`

Nếu thao tác đóng/flush cuối cùng **có thể tạo lỗi mới**, việc kiểm tra `checkError()` **sau khi đóng** có giá trị để phát hiện lỗi mà `PrintWriter.close()` tự ghi nhận. Ví dụ:

~~~java
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

public final class CloseErrorCheck {
    private CloseErrorCheck() {}

    public static void writeAndCheck(Writer sink, String text)
            throws IOException {
        PrintWriter out = new PrintWriter(sink);
        try {
            out.print(text);
        } finally {
            out.close();
        }
        if (out.checkError()) {
            throw new IOException("Output or close failed");
        }
    }
}
~~~

Lớp này không có `main` và **cố ý chuyển quyền đóng `sink` cho helper**; chỉ dùng khi hợp đồng của phương thức cho phép đóng sink do caller truyền vào. `checkError()` của `PrintWriter` sau khi đóng không flush writer đã đóng, nhưng vẫn phản ánh lỗi đã được lớp này ghi nhận.

Nếu code có ngoại lệ runtime ngay trong khối `try`, Java tiếp tục lan truyền ngoại lệ sau `finally` và không đi tới `if`; vì thế ví dụ chủ yếu kiểm tra tình trạng thao tác I/O bình thường và đóng tài nguyên. Khi cần bảo toàn tất cả nguyên nhân và lỗi đóng, nên làm việc với `Writer` gốc và `try-with-resources` để Java ghi nhận cả suppressed exceptions.

## 10. Các ngoại lệ vẫn có thể ném ra bình thường

Không diễn giải “`PrintStream`/`PrintWriter` không ném `IOException` khi ghi” thành “mọi lệnh `print` luôn không ném ngoại lệ”. Các lỗi không thuộc cơ chế I/O nội bộ vẫn có thể phát sinh:

- `IllegalFormatException` (và các lớp con) khi chuỗi định dạng sai hoặc kiểu dữ liệu không khớp trong `printf`/`format`.
- `NullPointerException` cho tham số bắt buộc không được `null`, tùy overload/API.
- `IndexOutOfBoundsException` cho đoạn mảng/chuỗi sai chỉ số.
- `RuntimeException` do mã người dùng chạy trong `Object.toString()` hoặc `Formattable.formatTo(...)`.
- Lỗi lúc **mở file** ở constructor có thể là `IOException` hoặc ngoại lệ checked cụ thể; lỗi bảo mật/quyền truy cập có thể có cách biểu diễn khác.
- `Error` cấp JVM không được cơ chế này biến thành boolean thành công.

Ví dụ định dạng sai:

~~~java
System.out.printf("%d%n", "not a number");
~~~

Đoạn mã ném `IllegalFormatConversionException` (một loại `IllegalFormatException`); `checkError()` **không phải** cơ chế dùng để phát hiện lỗi định dạng này. Nó không phải `IOException` từ thiết bị I/O.

### Trường hợp `InterruptedIOException` của `PrintStream`

Tài liệu `PrintStream` Java 17 phân biệt `InterruptedIOException` với những `IOException` khác: khi bên dưới ném ngoại lệ dạng này, lớp có thể **đặt interrupt flag** của thread để biểu thị tín hiệu ngắt thay vì đặt cờ lỗi theo cách thông thường. Vì thế, **`checkError() == false` không phải bằng chứng rằng mọi trạng thái bất thường đều đã được loại trừ**. Khi ứng dụng có cơ chế hủy công việc/ngắt thread, cần xử lý cờ interrupt đúng ý nghĩa của thiết kế.

Không nên tự động xóa trạng thái ngắt bằng `Thread.interrupted()` chỉ để làm ví dụ “đẹp”, bởi phương thức đó **vừa đọc vừa xóa** cờ ngắt. Nếu cần kiểm tra mà không xóa, dùng `Thread.currentThread().isInterrupted()`.

## 11. `System.out` / `System.err` cần chú ý gì?

`System.out.println(...)` và `System.err.println(...)` thường được dùng với giả định “in được tức là thành công”. Trên console tương tác, nhiều lỗi không xảy ra thường xuyên, nhưng vẫn có thể gặp **đích đã đóng**, pipe bị phía nhận đóng sớm, môi trường redirect output, lỗi file log, hoặc đích I/O đặc biệt.

~~~java
System.out.println("export complete");
if (System.out.checkError()) {
    // Phát hiện trạng thái lỗi của PrintStream.
    // Quyết định xử lý ở tầng nghiệp vụ hoặc kênh báo lỗi khác.
}
~~~

Nếu `System.out` đã lỗi, việc tiếp tục báo lỗi bằng chính `System.out` có thể cũng không tới nơi. Tùy ứng dụng, có thể dùng một kênh quan sát khác, trả mã trạng thái, ném ngoại lệ ở tầng ứng dụng hoặc ghi vào hệ thống logging được thiết kế riêng.

**Không tự ý `close()` `System.out` hoặc `System.err`** trong thư viện hay chương trình còn cần in tiếp. Đây là các tài nguyên chia sẻ của tiến trình. `System.console()` có thể trả `null` khi chạy trong IDE, CI hoặc môi trường không có console tương tác; không nên coi việc có console là mặc định.

`checkError()` trên `System.out` cũng có thể flush. Nó **không xác nhận** người dùng đã nhìn thấy văn bản trên terminal, file redirect đã fsync, hay hệ thống giám sát đã tiếp nhận log.

## 12. `Formatter.ioException()`: cơ chế tương tự nhưng khác API

`java.util.Formatter` nhận `Appendable` làm đích và hỗ trợ `format(...)`. Khi gặp `IOException` lúc ghi qua đích, `Formatter` có thể ghi nhớ ngoại lệ thay vì truyền lên qua phương thức format; có thể kiểm tra với **`formatter.ioException()`**.

~~~java
import java.io.IOException;
import java.io.Writer;
import java.util.Formatter;
import java.util.Locale;

public class FormatterErrorDemo {
    static final class FailingWriter extends Writer {
        @Override
        public void write(char[] chars, int offset, int length)
                throws IOException {
            throw new IOException("formatter output failed");
        }

        @Override
        public void flush() { }

        @Override
        public void close() { }
    }

    public static void main(String[] args) {
        try (Formatter formatter =
                     new Formatter(new FailingWriter(), Locale.ROOT)) {
            formatter.format("item=%d", 42);

            IOException cause = formatter.ioException();
            System.out.println("formatterError=" + (cause != null));
            System.out.println("message="
                    + (cause == null ? "none" : cause.getMessage()));
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac FormatterErrorDemo.java
java FormatterErrorDemo
~~~

**Stdout dự kiến:**

~~~text
formatterError=true
message=formatter output failed
~~~

Điểm khác biệt với `PrintWriter.checkError()`: `Formatter.ioException()` có thể trả lại **`IOException` được ghi nhận**, thay vì chỉ một giá trị boolean. Tuy nhiên `Formatter` và `PrintWriter` không phải cùng một lớp; đừng gọi `checkError()` trên `Formatter` hay gọi `ioException()` trên `PrintStream`.

Lỗi cú pháp định dạng như `IllegalFormatException` vẫn được xử lý theo hợp đồng riêng và có thể ném trực tiếp. Khi dùng `Formatter` bọc các đối tượng khác, phải rõ ai sở hữu đích và liệu `Formatter.close()` có đóng đích nếu đích triển khai `Closeable` hay không.

## 13. Lỗi lúc flush/close: thời điểm thường bị bỏ sót

Một số hệ thống có buffer ở nhiều tầng:

~~~text
print / println / printf
          |
          v
PrintWriter / PrintStream [trạng thái lỗi]
          |
          v
BufferedWriter / encoder / OutputStream
          |
          v
File / pipe / socket / thiết bị
~~~

Lệnh in có thể chỉ đưa dữ liệu vào buffer. Lỗi chỉ xuất hiện khi:

- Dữ liệu đủ nhiều làm buffer phải đẩy xuống tầng dưới.
- `autoFlush` kích hoạt một đợt flush.
- `checkError()` gọi flush.
- `flush()` hoặc `close()` được gọi trực tiếp.
- Tầng bên dưới thực hiện chuyển dữ liệu rồi mới biết đích không còn khả dụng.

Vì vậy có thể **chưa có lỗi được ghi nhận ở thời điểm `print()` trả về**, nhưng sau một lần flush/checkError lại là `true`. Khi có nhiều tầng buffering, cần xác định chỗ nào là **ranh giới hoàn thành** của tác vụ.

Nếu một quy trình có yêu cầu xác thực dữ liệu đã được lưu bền vững hoặc nhận bởi hệ thống khác, cần cơ chế mạnh hơn (ví dụ xác nhận phía nhận, transaction, checksum/ack, `FileChannel.force` khi có yêu cầu đồng bộ file). Bản thân `checkError()` chỉ kiểm tra trạng thái lỗi mà print API quan sát được.

## 14. Hướng dẫn quyết định chọn API

### Chỉ cần in cho người dùng hoặc debug

`System.out.print/println/printf` phù hợp. Khi công cụ CLI có yêu cầu đầu ra bắt buộc chính xác, hãy cân nhắc kiểm tra `System.out.checkError()` sau khi hoàn tất và phản ánh lỗi bằng mã thoát hoặc kênh khác.

### Cần in định dạng ra một `Writer` đã có

`PrintWriter` rất tiện cho `printf/println`. Nếu lỗi ghi là vấn đề nghiệp vụ, **kiểm tra `checkError()` sau đợt ghi/flush**, đồng thời hiểu rằng mất nguyên nhân gốc khiến báo lỗi kém chi tiết.

### Cần độ chính xác và khả năng retry/audit

Ưu tiên `Writer`/`BufferedWriter` hoặc `OutputStream`/`BufferedOutputStream` với `throws IOException`. Khi lỗi xảy ra, giữ nguyên nguyên nhân và quyết định rõ chính sách ghi một phần, retry, file tạm, timeout và đóng tài nguyên.

### Cần format qua `Appendable` tùy ý

`Formatter` có thể phù hợp; kiểm tra `ioException()` cho lỗi I/O của đích và vẫn xử lý lỗi format/runtime bằng cách khác.

### Có nhiều tầng wrapper

Đừng bọc nhiều `PrintWriter`/`PrintStream` không cần thiết rồi giả định tầng ngoài phản ánh đầy đủ từng lỗi. Hợp đồng có thể chuyển lỗi thành trạng thái nội bộ ở tầng đầu tiên; dùng một API lỗi rõ ràng ở biên nghiệp vụ sẽ dễ quản lý hơn.

## 15. Những hiểu nhầm thường gặp

**1. “Không thấy exception tức là đã ghi thành công.”**  
Sai với `PrintStream`/`PrintWriter`: lỗi I/O thông thường có thể chỉ bật cờ. Hãy dùng `checkError()` nếu kết quả ghi cần được kiểm tra.

**2. “`checkError()` chỉ đọc cờ, không tác động I/O.”**  
Sai: khi còn mở, phương thức có thể gọi flush, và flush lại có thể gây lỗi mới hoặc độ trễ I/O.

**3. “`checkError() == false` nghĩa là byte đã đến đích cuối và bền vững.”**  
Sai: nó không bảo đảm fsync, nhận trên socket hay thành công của các bước lưu trữ bên ngoài. Cờ phản ánh lỗi được lớp print nhận biết, không phải biên bản giao hàng.

**4. “Nếu in thất bại sẽ biết chính xác IOException nào từ `checkError()`.”**  
Sai: kết quả chỉ là boolean. Cần API ném lỗi trực tiếp hoặc cơ chế giám sát riêng nếu cần nguyên nhân.

**5. “`PrintStream` và `PrintWriter` autoFlush giống nhau.”**  
Sai: `PrintWriter` kích hoạt autoFlush theo `println/printf/format`; `PrintStream` có thêm hành vi theo ghi mảng byte và newline.

**6. “Tất cả ngoại lệ đều bị các lớp print bắt lại.”**  
Sai: lỗi định dạng `IllegalFormatException`, lỗi chỉ số và các ngoại lệ runtime vẫn có thể phát sinh. `InterruptedIOException` ở `PrintStream` còn có đường xử lý riêng.

**7. “Gọi `close()` xong là mọi lỗi đều tự được báo bằng exception.”**  
Sai với `PrintStream`/`PrintWriter`: lỗi I/O lúc flush/close cũng có thể được ghi nhận trong trạng thái thay vì ném. Với tác vụ quan trọng, cần chính sách kiểm tra cuối đời tài nguyên và ownership rõ ràng.

**8. “Cờ lỗi true nghĩa là lần ghi gần nhất thất bại hoàn toàn.”**  
Sai: trạng thái có thể tích lũy lỗi cũ, và thao tác ghi có thể thành công một phần trước khi xảy ra lỗi. Đừng dùng cờ lỗi như một bộ đếm số byte đã ghi hoặc phân loại lỗi chi tiết.

## 16. Checklist kiểm tra mã sản xuất

- [ ] Biết biến đầu ra là `PrintStream`, `PrintWriter`, `Formatter` hay `Writer/OutputStream`?
- [ ] Nếu dùng lớp print, có `checkError()` ở mốc mà nghiệp vụ cần biết tình trạng I/O?
- [ ] Nếu dùng `Formatter`, đã kiểm tra `ioException()` khi điều kiện đầu ra yêu cầu không có lỗi?
- [ ] Có phân biệt lỗi I/O được ghi vào cờ với lỗi runtime/format có thể ném trực tiếp?
- [ ] Có hiểu `checkError()` có thể flush khi đối tượng còn mở?
- [ ] Không coi trạng thái false là bảo đảm durable commit, network ACK hoặc giao hàng thành công?
- [ ] Nếu dùng `autoFlush`, đã hiểu đúng khác biệt giữa `PrintStream` và `PrintWriter`?
- [ ] Quyền đóng resource đã rõ: ai sở hữu `Writer`/`OutputStream`, ai chịu trách nhiệm đóng và báo lỗi ở cuối?
- [ ] Khi cần nguyên nhân `IOException` và chính sách retry nghiêm ngặt, đã cân nhắc dùng API trực tiếp có `throws IOException`?
- [ ] Không đóng `System.out`/`System.err` do thư viện không sở hữu?
- [ ] Test có mô phỏng lỗi ghi bằng `OutputStream`/`Writer` giả lập thay vì kỳ vọng lỗi file/ngẫu nhiên?

### Câu hỏi tự kiểm tra

**Câu 1.** `PrintWriter.println("A")` hoàn thành, không có exception. Có chắc đã ghi thành công không?  
**Đáp án:** Không. Cần quan sát `checkError()` tại mốc hợp lý và hiểu giới hạn của tín hiệu này.

**Câu 2.** `checkError()` có thể tự làm một thao tác I/O không?  
**Đáp án:** Có. Nếu luồng còn mở, nó flush trước khi kiểm tra trạng thái lỗi.

**Câu 3.** `PrintWriter(autoFlush=true).write('\n')` có chắc tự flush không?  
**Đáp án:** Không. AutoFlush của `PrintWriter` gắn với `println/printf/format`; khác `PrintStream`.

**Câu 4.** Nếu muốn giữ nguyên nguyên nhân `IOException` khi ghi file cấu hình, nên dùng gì?  
**Đáp án:** Dùng `Writer`/`BufferedWriter` hoặc `OutputStream` với `throws IOException` và `try-with-resources`; các API này có thể truyền nguyên nhân lỗi gốc lên caller.

**Câu 5.** `System.out.checkError() == false` có chứng minh log đã được hệ thống giám sát đọc chưa?  
**Đáp án:** Không. Nó không phải xác nhận từ hệ thống nhận log.

**Câu 6.** Nếu gọi `printf("%d", "text")` và Java ném `IllegalFormatConversionException`, có phải `checkError` là cơ chế xử lý?  
**Đáp án:** Không. Đây là lỗi định dạng runtime, cần xử lý theo hợp đồng định dạng, khác lỗi I/O được giữ trong cờ.

**Ghi nhớ:** `PrintStream` và `PrintWriter` chọn sự tiện lợi khi in văn bản/định dạng nên **có thể chuyển lỗi I/O thành trạng thái**. `checkError()` giúp phát hiện lỗi mà lớp in biết tới, nhưng **không mang nguyên nhân chi tiết và không bảo đảm dữ liệu đã tới nơi cuối cùng**. Khi tính đúng đắn đòi hỏi xử lý lỗi chính xác, hãy chọn abstraction cho phép `IOException` đi tới caller và quản lý đầy đủ vòng đời tài nguyên.

## Tham khảo API chính thức (Java 17)

- [PrintStream — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PrintStream.html)
- [PrintWriter — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PrintWriter.html)
- [Formatter — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Formatter.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="console-and-formatting-010">Không vô tình đóng các stream chuẩn dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Không vô tình đóng các stream chuẩn dùng chung trong Java

Trong ứng dụng Java, `System.in`, `System.out` và `System.err` thường được sử dụng ở nhiều nơi: nhập lệnh trong console, in kết quả, ghi thông báo và báo lỗi. Một thao tác tưởng hợp lý như đặt `Scanner(System.in)` vào `try-with-resources` trong một hàm tiện ích có thể khiến **luồng nhập chuẩn bị đóng** khi hàm kết thúc. Những phần còn lại của chương trình khi đó không thể tiếp tục đọc input như trước.

Nguyên tắc chính của bài học: **chỉ đóng tài nguyên mà mình sở hữu hoặc được giao quyền đóng**. Một helper chỉ mượn stream chuẩn để đọc hay ghi **không nên kết thúc vòng đời stream đó**. Khi thêm `Scanner`, `BufferedReader`, `PrintWriter` hoặc các wrapper khác, cần nhớ rằng gọi `close()` trên wrapper có thể đóng **luôn stream nền**.

## 1. Ba stream chuẩn của Java là gì?

Lớp `java.lang.System` cung cấp ba trường tĩnh dùng chung trong tiến trình Java:

| Stream | Kiểu | Vai trò thông thường | Ví dụ |
|---|---|---|---|
| `System.in` | `InputStream` | Nhận dữ liệu đầu vào chuẩn (stdin) | Đọc nội dung người dùng nhập hoặc dữ liệu được chuyển hướng từ tệp |
| `System.out` | `PrintStream` | Xuất kết quả/thông tin thông thường (stdout) | `System.out.println("OK")` |
| `System.err` | `PrintStream` | Xuất thông báo lỗi/chẩn đoán (stderr) | `System.err.println("Lỗi nhập liệu")` |

`System.in` cung cấp **byte**, còn `System.out` và `System.err` là các đối tượng `PrintStream` có phương thức `print`/`println`/`printf` thuận tiện. Những tên gọi này mô tả **kênh I/O**, không bảo đảm rằng đầu kia luôn là một bàn phím hoặc màn hình vật lý.

Ví dụ chạy từ shell:

~~~bash
java Main < input.txt > output.txt 2> error.txt
~~~

Lệnh có ý nghĩa: stdin của Java được nối với `input.txt`, stdout được chuyển tới `output.txt` và stderr tới `error.txt`. Trong IDE, môi trường test, container hoặc dịch vụ, những stream này cũng có thể được nối với hệ thống khác. Bởi vậy, một phương thức sâu bên trong ứng dụng **không được tự xem mình là chủ sở hữu độc quyền** của chúng.

Các trường `System.in`, `System.out`, `System.err` có thể được thay thế bằng `System.setIn(...)`, `System.setOut(...)` và `System.setErr(...)` trong phạm vi ứng dụng có quyền phù hợp, chẳng hạn trong test hoặc khi nhúng ứng dụng. Điều này càng củng cố yêu cầu phân định **ai cung cấp stream, ai dùng và ai đóng**.

## 2. Vì sao đóng stream chuẩn trong helper lại nguy hiểm?

Giả sử chương trình có hai giai đoạn:

~~~text
main()
  ├── readProfile(): đọc một dòng từ System.in
  └── readCommand(): đọc một lệnh khác từ System.in
~~~

Nếu `readProfile()` tạo `Scanner` bọc `System.in` rồi đóng scanner khi trả về, nó có thể **đóng luôn `System.in`**. Khi `readCommand()` cố gắng đọc tiếp, nó có thể nhận lỗi hoặc không còn dữ liệu đọc được.

Một mẫu code dễ mắc lỗi:

~~~java
// KHÔNG NÊN nếu sau hàm này ứng dụng vẫn cần System.in.
static String readOneName() {
    try (Scanner scanner = new Scanner(System.in)) {
        return scanner.nextLine();
    } // scanner.close() cũng đóng nguồn đọc System.in.
}
~~~

Đây là **đoạn minh họa lỗi ownership**, không phải ví dụ được khuyến khích. `Scanner.close()` gọi `close()` trên input source của nó nếu source triển khai `Closeable`; `System.in` là `InputStream` và triển khai giao diện này.

Vấn đề không phải bản thân `try-with-resources`: cơ chế đó **đúng** khi tài nguyên nằm trong phạm vi sở hữu của hàm. Sai lầm là đặt một **wrapper đang mượn tài nguyên chung** vào khu vực tự động đóng.

Một lỗi tương tự ở stdout:

~~~java
// KHÔNG NÊN: đóng wrapper có thể đóng System.out.
static void printReport() {
    try (PrintWriter writer = new PrintWriter(System.out)) {
        writer.println("Báo cáo hoàn tất");
    }
}
~~~

`PrintWriter.close()` đóng writer và tầng output bên dưới. Vì `System.out` là luồng chung, những nơi in log hoặc kết quả sau đó có thể không còn hoạt động như mong đợi. `PrintStream` như `System.out` thường ghi nhận lỗi thay vì ném `IOException` từ các phương thức `print`/`println`, khiến việc vô tình đóng stdout có thể không biểu hiện bằng một ngoại lệ rõ ràng.

**Đóng wrapper** thường khác với **ngừng dùng wrapper**. Khi caller sở hữu luồng chuẩn, một helper có thể dùng wrapper tạm để đọc/ghi nhưng không được tự ý gọi `close()` trên nó.

## 3. Quyền sở hữu và vòng đời: cần hỏi ai có trách nhiệm đóng?

**Ownership (quyền sở hữu)** là thỏa thuận về việc bên nào chịu trách nhiệm kết thúc tài nguyên. Nó không được suy ra chỉ từ kiểu tham số `InputStream` hoặc `OutputStream`.

### Trường hợp A — Hàm tự mở tài nguyên: hàm chịu trách nhiệm đóng

Ví dụ code mở tệp để đọc:

~~~java
static String readFile(Path path) throws IOException {
    try (BufferedReader reader =
                 Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        return reader.readLine();
    }
}
~~~

Đây là **phương thức minh họa**, cần import `java.io.*`, `java.nio.file.*` và `java.nio.charset.StandardCharsets`. Stream tệp được tạo bên trong hàm; sau khi đọc xong, hàm không cần nó nữa. Dùng `try-with-resources` để đóng là hợp lý.

### Trường hợp B — Hàm nhận stream do caller cung cấp: hàm chỉ mượn

~~~java
static void writeGreeting(PrintStream destination, String name) {
    destination.println("Xin chào, " + name);
    // Không destination.close(): caller đang sở hữu.
}
~~~

Đây là **phương thức minh họa** nhận `PrintStream` từ caller. Nó có thể dùng với `System.out`, `System.err` hoặc một `PrintStream` khác do caller tạo. Hợp đồng của hàm là **ghi một dòng**, không phải **kết thúc nơi nhận**.

### Trường hợp C — Luồng chuẩn được môi trường quản lý: đừng đóng ở code cục bộ

`System.in`, `System.out`, `System.err` thường tồn tại trong suốt vòng đời ứng dụng và có thể được chia sẻ bởi thư viện, framework, command loop, REPL hoặc thành phần ghi log. Một hàm đọc một câu hỏi hoặc in một báo cáo chỉ là **người dùng tạm thời**.

| Ai tạo/cấp tài nguyên? | Bên sử dụng | Bên thường đóng |
|---|---|---|
| Hàm mở `FileInputStream`/`Files.newInputStream` | Chính hàm đó | Hàm, thường với `try-with-resources` |
| Caller truyền `InputStream` vào thư viện | Thư viện/helper chỉ mượn | Caller, trừ khi hợp đồng nói rõ chuyển quyền sở hữu |
| Java runtime hoặc môi trường ứng dụng cấp `System.in/out/err` | Nhiều phần của ứng dụng | Tầng quản lý vòng đời ứng dụng/môi trường; helper không tự đóng |
| Hàm tự tạo `ByteArrayInputStream` hoặc `ByteArrayOutputStream` | Hàm | Hàm; đóng nhiều stream bộ nhớ có thể không giải phóng tài nguyên hệ điều hành nhưng vẫn cần hợp đồng rõ |
| Framework cung cấp stream phản hồi/request | Code callback | Theo hợp đồng framework |

**Ghi nhớ:** quyền sở hữu là **hợp đồng API**. Khi xây dựng phương thức công khai, hãy viết rõ “phương thức không đóng luồng đầu vào/đầu ra được truyền vào” hoặc “phương thức nhận quyền sở hữu và sẽ đóng luồng”.

## 4. Các wrapper có thể đóng stream chuẩn như thế nào?

Một stream gốc có thể được bọc nhiều tầng. Khi đóng tầng ngoài, nhiều lớp tiêu chuẩn chuyển tiếp thao tác đóng xuống tầng trong:

~~~text
Scanner.close() ------------------------> System.in.close()

BufferedReader.close()
  -> InputStreamReader.close() ----------> System.in.close()

PrintWriter.close()
  -> OutputStreamWriter.close() ---------> System.out.close()

BufferedOutputStream.close() -----------> System.out.close()
~~~

Sơ đồ mô tả các trường hợp khi wrapper thực sự bọc **chính các stream chuẩn**. Việc đóng sâu hay không phải xem hợp đồng của wrapper cụ thể; những ví dụ này minh họa hành vi các wrapper JDK thông thường.

### `Scanner`

`Scanner` có thể nhận `InputStream`, `Readable`, `File`, `Path` hoặc `String` tùy constructor. Nếu source là `Closeable`, `scanner.close()` cũng đóng source. Vì thế:

~~~java
Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
String name = scanner.nextLine();
// Không scanner.close() nếu ứng dụng còn dùng System.in.
~~~

Đây là **đoạn minh họa**, cần import `java.util.Scanner` và `java.nio.charset.StandardCharsets`. Nếu helper thực sự tự sở hữu source, cách đóng có thể khác.

### `BufferedReader` và `InputStreamReader`

~~~java
BufferedReader reader = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8));
String line = reader.readLine();
// Không reader.close() trong một helper chỉ mượn System.in.
~~~

`BufferedReader` bọc `InputStreamReader`; đóng reader ngoài cùng sẽ đóng adapter ký tự, từ đó đóng stream byte bên dưới. Bổ sung điều quan trọng: **reader có bộ đệm có thể đọc trước nhiều byte/ký tự**. Nếu tạo nhiều `Scanner`/`BufferedReader` riêng cùng đọc `System.in`, chúng có thể tranh chấp dữ liệu ngay cả khi không đóng stream. Hãy dùng **một đối tượng đọc được chia sẻ ở tầng phù hợp** cho cùng phiên nhập.

### `PrintWriter`, `OutputStreamWriter` và `PrintStream`

~~~java
Writer writer = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
writer.write("Xin chào\n");
writer.flush(); // Đẩy buffer ký tự xuống output được mượn.
// Không writer.close() trong helper chỉ mượn System.out.
~~~

`OutputStreamWriter` chuyển ký tự thành byte theo charset đã chỉ định. `flush()` đẩy dữ liệu trong buffer của writer xuống output bên dưới; khác với `close()`, nó không kết thúc luồng. Nếu `PrintWriter` bao `System.out`, `PrintWriter.close()` cũng đóng nơi nhận bên dưới theo hợp đồng wrapper.

**Lưu ý về charset:** `System.out` vốn là `PrintStream` có cơ chế chuyển ký tự thành byte cho đích cấu hình của runtime. Tạo thêm `OutputStreamWriter(System.out, UTF_8)` tạo một tầng mã hóa riêng; chỉ nên làm khi **chủ đích** muốn byte UTF-8 đi qua tầng đó và nó tương thích môi trường. Với việc in console thông thường, gọi trực tiếp `System.out.print/println/printf` thường rõ ràng hơn.

## 5. Ví dụ Java 17: một helper chỉ mượn stream, owner đóng sau

Để kiểm tra hành vi **không đóng nguồn/đích do caller quản lý** mà không gây ảnh hưởng đến `System.in`/`System.out` thật, chương trình dùng hai stream thử nghiệm có cờ đánh dấu mỗi lần `close()` được gọi.

Helper `greet` nhận input và output. Nó tạo `Scanner` để đọc một dòng, `OutputStreamWriter` để ghi UTF-8, gọi `flush()` khi cần và **không gọi `close()`**. Sau khi helper trả về, caller có thể ghi tiếp vào output. Chỉ **owner** mới đặt hai nguồn/đích vào `try-with-resources`.

Lưu toàn bộ mã vào **`SharedStandardStreamsDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class SharedStandardStreamsDemo {
    static final class TrackedInput extends FilterInputStream {
        boolean closed;

        TrackedInput(InputStream delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    static final class TrackedOutput extends FilterOutputStream {
        boolean closed;

        TrackedOutput(OutputStream delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    // Chỉ mượn input/output; quyền close thuộc về caller.
    static void greet(InputStream input, OutputStream output)
            throws IOException {
        Scanner scanner = new Scanner(input, StandardCharsets.UTF_8);
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);

        writer.write("Tên: ");
        writer.flush();
        if (scanner.hasNextLine()) {
            String name = scanner.nextLine();
            writer.write("Xin chào, " + name + "\n");
            writer.flush();
        }
        // Không scanner.close() hay writer.close().
    }

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        TrackedInput input = new TrackedInput(
                new ByteArrayInputStream(
                        "Mai\n".getBytes(StandardCharsets.UTF_8)));
        TrackedOutput output = new TrackedOutput(capture);

        try (input; output) {
            greet(input, output);
            output.write("Tiếp tục\n".getBytes(StandardCharsets.UTF_8));
            output.flush();

            System.out.println("Trong try: inputClosed=" + input.closed
                    + ", outputClosed=" + output.closed);
        }

        System.out.println("Sau try: inputClosed=" + input.closed
                + ", outputClosed=" + output.closed);
        System.out.println("Nội dung đã ghi:");
        System.out.print(capture.toString(StandardCharsets.UTF_8));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 SharedStandardStreamsDemo.java
java SharedStandardStreamsDemo
~~~

Kết quả khi console hiển thị được tiếng Việt:

~~~text
Trong try: inputClosed=false, outputClosed=false
Sau try: inputClosed=true, outputClosed=true
Nội dung đã ghi:
Tên: Xin chào, Mai
Tiếp tục
~~~

**Giải thích:**

1. `TrackedInput` và `TrackedOutput` là wrapper đánh dấu lời gọi `close()`, bọc byte stream trong bộ nhớ. Chúng chỉ nhằm kiểm chứng **vòng đời được điều khiển bởi caller**.
2. `greet` dùng dữ liệu nhập `"Mai\n"`, tạo output từ `Writer`, gọi `flush()` để hoàn tất phần ghi tại thời điểm đó nhưng **không đóng wrapper**.
3. Ngay khi `greet` trả về, hai biến `closed` vẫn `false`. Caller còn dùng `output.write(...)` thành công để ghi thêm `"Tiếp tục\n"`.
4. Thoát khối `try (input; output)`, Java tự gọi `close()` cho các tài nguyên mà **caller đã khai báo sở hữu** (theo thứ tự ngược lúc khai báo: output rồi input). Sau đó cả hai cờ trở thành `true`.
5. `System.out` thật chỉ được sử dụng để báo kết quả; ví dụ **không đóng hoặc thay thế** bất kỳ stream chuẩn nào của tiến trình.
6. `ByteArrayOutputStream` có thể cung cấp nội dung đã ghi ngay cả sau khi bị đóng; đây là đặc tính của lớp này. Không suy diễn rằng file/socket cũng giữ hành vi tương tự sau `close()`.

Trong một ứng dụng thật, nếu `greet(System.in, System.out)` được gọi từ command loop, hợp đồng mượn stream vẫn có ý nghĩa: helper không được đóng luồng chuẩn vì caller có thể nhập/xuất tiếp. Tuy nhiên, không nên liên tục tạo `Scanner` mới trên `System.in` trong command loop vì scanner có thể **đọc trước** và giữ lại dữ liệu trong buffer riêng. Thay vào đó, caller có thể tạo **một reader/scanner dùng chung**, rồi truyền nó vào các helper.

## 6. Cách thiết kế API tốt hơn: caller sở hữu scanner, helper chỉ sử dụng

Nếu cả chương trình phải hỏi nhiều câu, đưa việc khởi tạo và quản lý bộ đọc ra tầng ứng dụng:

~~~java
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class SharedConsoleReader {
    static void askName(Scanner scanner) {
        System.out.print("Tên: ");
        if (scanner.hasNextLine()) {
            String name = scanner.nextLine();
            System.out.println("Xin chào, " + name);
        }
        // Helper không đóng scanner.
    }

    static void askCity(Scanner scanner) {
        System.out.print("Thành phố: ");
        if (scanner.hasNextLine()) {
            String city = scanner.nextLine();
            System.out.println("Bạn ở " + city);
        }
        // Vẫn không đóng scanner.
    }

    public static void main(String[] args) {
        Scanner sharedScanner =
                new Scanner(System.in, StandardCharsets.UTF_8);
        askName(sharedScanner);
        askCity(sharedScanner);
        // Không đóng System.in trong chương trình minh họa.
    }
}
~~~

Đây là **chương trình Java 17 độc lập**, lưu thành `SharedConsoleReader.java`. Khi chạy `java SharedConsoleReader`, người dùng sẽ lần lượt nhập tên và thành phố. Output tương tác phụ thuộc câu trả lời; ví dụ nhập `Lan` rồi `Đà Nẵng`:

~~~text
Tên: Lan
Xin chào, Lan
Thành phố: Đà Nẵng
Bạn ở Đà Nẵng
~~~

Trong thực tế, nhiều terminal sẽ hiển thị lại chữ người dùng gõ trên cùng dòng prompt. Phần ví dụ trên là **một phiên tương tác mẫu**, không phải chuỗi output cố định cho mọi trường hợp.

Lợi ích của cách thiết kế này:

- Chỉ có **một** `Scanner` đọc `System.in`, giảm nguy cơ nhiều buffer tranh chấp.
- Mỗi helper nhận sẵn reader và không quyết định vòng đời toàn ứng dụng.
- Việc test dễ hơn: caller có thể truyền `Scanner` được tạo từ `String`, còn đầu ra có thể được thiết kế bằng tham số `PrintStream` thay vì ghi trực tiếp `System.out`.
- Lớp điều phối quyết định khi nào luồng nhập thật sự kết thúc, dựa trên hợp đồng ứng dụng.

Không có yêu cầu phải đóng `System.in` cuối `main` ở mọi ứng dụng console. Khi tiến trình kết thúc, hệ điều hành thu hồi các resource của tiến trình; việc tránh đóng stream chuẩn trong helper là để không gây tác dụng phụ cho các thành phần vẫn còn chạy. Với tài nguyên **do ứng dụng tự mở**, vẫn phải đóng đúng lúc.

## 7. `flush()` không phải `close()`

Khi ghi qua tầng có buffer, dữ liệu có thể chưa tới đích ngay sau `write()`. Ba thao tác khác nhau:

| Thao tác | Mục đích | Ảnh hưởng tới quyền sử dụng tiếp |
|---|---|---|
| `write(...)` / `print(...)` | Gửi ký tự/byte vào API xuất, có thể qua buffer | Thường có thể ghi tiếp |
| `flush()` | Yêu cầu đưa nội dung đã đệm xuống tầng output bên dưới | **Không đóng**, vẫn có thể ghi tiếp |
| `close()` | Kết thúc vòng đời stream/wrapper, thường thực hiện flush theo hợp đồng rồi đóng tầng dưới | **Không nên sử dụng tiếp** |

Trong helper dùng `Writer` bọc `System.out`, nếu cần đảm bảo phần output đệm được đẩy xuống trước khi trả về thì có thể gọi `writer.flush()`; đó **không phải lý do để gọi `writer.close()`**.

`PrintWriter` có những constructor có tùy chọn **autoFlush**, nhưng không nên cho rằng “autoFlush = true” khiến mọi phương thức ghi đều flush. Với `PrintWriter`, auto-flush gắn với các thao tác như `println`, `printf` và `format` theo hợp đồng của lớp; `write` hay `print` đơn thuần có thể không kích hoạt như mong muốn. Khi cần ranh giới output rõ ràng, kiểm tra hợp đồng và gọi `flush()` phù hợp.

Tương tự, `System.out.flush()` và `System.err.flush()` thường an toàn hơn `close()` khi bạn chỉ muốn đẩy phần đã ghi. Nhưng `flush()` không bảo đảm dữ liệu đã được lưu bền vững trên đĩa hoặc đầu nhận mạng đã xử lý xong.

## 8. Những điều dễ bị bỏ sót

### Đóng `System.out` đôi khi không báo lỗi ngay

Các thao tác `print`/`println` của `PrintStream` không truyền `IOException` trực tiếp như nhiều API output khác. Vì vậy, sau khi một thư viện vô tình đóng stdout, các lệnh in sau đó **có thể không hiển thị** thay vì lập tức ném một ngoại lệ dễ thấy. `PrintStream.checkError()` cho phép kiểm tra trạng thái lỗi theo hợp đồng, nhưng **kiểm tra lỗi không thay thế** việc thiết kế ownership đúng.

Không nên tạo chương trình thử trên `System.out` thật bằng cách cố ý gọi `System.out.close()` để “xem có sao không”, nhất là trong IDE, test runner, notebook hoặc tiến trình có nhiều chức năng dùng chung.

### `System.err` cũng là tài nguyên chung

Không được cho rằng stderr có thể đóng tùy ý vì “chỉ ghi lỗi”. Công cụ logging, trình xử lý ngoại lệ và framework vẫn có thể cần `System.err` sau lời gọi của bạn. Việc terminal ghép stdout và stderr thành cùng một nơi hiển thị **không làm thay đổi** vòng đời sở hữu của hai đối tượng `PrintStream`.

### Close wrapper khi caller vẫn cần stream là lỗi hợp đồng

Ví dụ:

~~~java
// SAI nếu caller cần dùng System.out tiếp:
try (BufferedOutputStream buffered = new BufferedOutputStream(System.out)) {
    buffered.write("Hello\n".getBytes(StandardCharsets.UTF_8));
} // BufferedOutputStream.close() đóng stream nền.
~~~

Đây là **đoạn minh họa hành vi không nên dùng**. Import cần thiết gồm `java.io.BufferedOutputStream` và `java.nio.charset.StandardCharsets`; phương thức chứa mã phải xử lý/khai báo `IOException`. Nếu thật sự cần buffer, hãy quản lý tầng này ở cấp sở hữu stream hoặc xây API nhận một writer được caller quản lý, và flush theo ranh giới hợp đồng.

### Redirect stream trong test cũng cần khôi phục

Khi test cần chặn output, có thể thay `System.out` bằng `PrintStream` ghi vào `ByteArrayOutputStream`. Việc gọi `System.setOut(...)` thay đổi **trạng thái toàn cục** và có thể ảnh hưởng test khác chạy cùng lúc. Nếu bắt buộc thay, cần khôi phục stream gốc bằng `finally` và chỉ đóng stream tạm mà test đã tạo; tránh vô tình đóng stream gốc. Đối với code thư viện, một thiết kế nhận `PrintStream`/`Writer` qua tham số thường dễ test hơn và ít ảnh hưởng toàn cục.

### `try-with-resources` không sai; đặt sai quyền sở hữu mới gây lỗi

~~~java
// ĐÚNG: tự mở input từ tệp, tự sở hữu, nên tự đóng.
try (InputStream input = Files.newInputStream(path)) {
    input.transferTo(System.out);
}
// Đã đóng input; System.out vẫn được để mở.
~~~

Đây là **đoạn minh họa** với `path` kiểu `Path` đã khai báo, cần import `java.io.InputStream` và `java.nio.file.Files`, cùng xử lý `IOException`. `transferTo` bản thân nó **không đóng** input hay output. `try-with-resources` ở đây chỉ bao **input do ta sở hữu**, nên không gây đóng `System.out`.

**Quy tắc không thể thay thế bằng mẹo cú pháp:** trước khi dùng `try-with-resources`, hỏi “bên nào sở hữu tài nguyên này và có quyền kết thúc nó?”.

## 9. Sai lầm thường gặp và cách sửa

| Sai lầm | Hệ quả | Cách sửa |
|---|---|---|
| `try (Scanner sc = new Scanner(System.in))` trong hàm đọc một giá trị | Đóng scanner làm đóng input nguồn chung | Dùng scanner chung cấp từ caller; helper chỉ đọc |
| `try (PrintWriter w = new PrintWriter(System.out))` trong hàm in | Khi thoát `try` có thể đóng stdout | Dùng trực tiếp `System.out` hoặc writer được caller quản lý |
| Gọi `System.out.close()` sau một đoạn in | Các phần sau có thể không in được | Nếu chỉ cần đẩy buffer, gọi `flush()` |
| Gọi `System.err.close()` vì tưởng stderr không được dùng nữa | Logging/lỗi tiếp theo có thể mất | Giữ stderr cho tầng quản lý ứng dụng |
| Cho rằng đóng wrapper chỉ giải phóng bộ đệm riêng | Nhiều wrapper còn đóng stream gốc | Kiểm tra hợp đồng `close` của từng lớp |
| Tạo nhiều `Scanner` cùng đọc `System.in` | Buffer đọc trước làm dữ liệu bị giữ trong các scanner khác nhau | Tạo một scanner chung và truyền xuống helper |
| Không đóng `Files.newInputStream` vì sợ đóng stream chuẩn | Rò rỉ file descriptor không liên quan stdout/stdin | Đóng tài nguyên tệp do mình mở; chỉ giữ mở stream được mượn |
| Dùng `close()` thay `flush()` sau mỗi bản ghi | Kết thúc quá sớm nơi nhận dữ liệu | Flush phù hợp, để owner close lúc hoàn tất |
| Giả định `System.out` luôn là màn hình | Khi chuyển hướng, stdout có thể là tệp hoặc pipe | Xem stream theo vai trò kênh I/O |
| Test thay `System.out` rồi không khôi phục | Test khác bị ảnh hưởng bởi global state | Khôi phục tham chiếu gốc trong `finally` |

## 10. Bài tập tự kiểm tra

**Câu 1.** Hàm `askAge()` tạo `Scanner(System.in)` trong `try-with-resources`. Hàm khác tiếp tục cần đọc tên từ `System.in`. Có gì sai?

> Hàm đầu đã **đóng scanner**, kéo theo đóng input nguồn theo hợp đồng của `Scanner`. Tốt hơn tạo một scanner ở tầng điều phối và truyền vào cả hai hàm, không đóng trong các helper.

**Câu 2.** Một helper nhận `OutputStream out` từ caller và bọc `OutputStreamWriter(out, UTF_8)`. Helper có nên gọi `writer.close()` sau mỗi lần ghi?

> Không, trừ khi hợp đồng cho phép helper sở hữu và đóng output. Khi chỉ mượn, có thể gọi `writer.flush()` để đẩy dữ liệu, nhưng đóng writer sẽ đóng luồng bên dưới.

**Câu 3.** Khi nào dùng `try-with-resources` là lựa chọn đúng?

> Khi code **mở và sở hữu** tài nguyên, ví dụ `Files.newInputStream(path)` hoặc `Files.newBufferedWriter(path, UTF_8)`, và sau khi khối kết thúc không còn cần nó. Với stream mượn, quyền close thuộc caller hoặc tầng sở hữu.

**Câu 4.** Chạy `System.out.println("Done")` sau khi một thư viện đã đóng `System.out` mà không thấy lỗi. Điều này có chứng minh stdout còn hoạt động không?

> Không. `PrintStream` có thể ghi nhận lỗi nội bộ thay vì ném `IOException` từ `println`. Cần sửa việc đóng sai stream và, khi chẩn đoán, có thể xem `checkError()`.

**Câu 5.** Tại sao tránh hai `Scanner` độc lập cùng đọc `System.in` ngay cả khi không đóng cái nào?

> Vì mỗi scanner có thể **đọc trước** dữ liệu vào bộ đệm riêng; một scanner có thể đã nhận những byte mà scanner khác dự định đọc. Dùng một scanner chung cho cùng phiên nhập để quản lý thứ tự dữ liệu.

**Câu 6.** Một phương thức mở `InputStream` của file rồi gọi `input.transferTo(System.out)`. Nên đóng stream nào trong phương thức?

> Đóng **input file do phương thức mở**, thường bằng `try-with-resources`. Không tự đóng `System.out` vì đó là stream chuẩn dùng chung; bản thân `transferTo` không đóng stream nào.

## Ghi nhớ

**Stream chuẩn `System.in`, `System.out`, `System.err` là các kênh thường được chia sẻ trong ứng dụng.** Wrapper như `Scanner`, `BufferedReader`, `PrintWriter` có thể **đóng luôn nguồn/đích bên dưới** khi gọi `close()`. Vì vậy hãy:

1. **Đóng** stream do mình tạo và sở hữu (dùng `try-with-resources`).
2. **Không đóng** stream do caller hoặc môi trường cấp nếu chỉ mượn.
3. **Dùng chung một bộ đọc** cho `System.in` khi chương trình nhập nhiều lượt.
4. **Phân biệt `flush` và `close`** khi cần xuất dữ liệu có đệm.

Một API tốt luôn làm rõ **ai sở hữu, ai chỉ dùng, khi nào tài nguyên được đóng**. Đây là chìa khóa để chương trình console, công cụ CLI, thư viện và test Java không vô tình làm hỏng các luồng dùng chung.

**Tài liệu tham khảo:** [Java SE 17 — `System`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html), [`Scanner.close()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Scanner.html#close()), [`PrintStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PrintStream.html), [`PrintWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PrintWriter.html).

</details>

- [Quay lại đầu trang](#back-to-top)
