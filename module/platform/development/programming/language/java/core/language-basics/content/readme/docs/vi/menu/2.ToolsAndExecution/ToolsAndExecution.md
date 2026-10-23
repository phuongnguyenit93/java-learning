<a id="back-to-top"></a>

# Công cụ và thực thi

## Menu
- [Kiểm tra phiên bản java và javac](#tools-and-execution-001)
- [Tạo tệp nguồn và biên dịch bằng javac](#tools-and-execution-002)
- [Chạy lớp bằng java và phân biệt tên lớp với tên tệp](#tools-and-execution-003)
- [Thư mục làm việc, thư mục đầu ra và classpath](#tools-and-execution-004)
- [Chạy chương trình một tệp nguồn](#tools-and-execution-005)
- [Thử biểu thức, biến và phương thức với JShell](#tools-and-execution-006)
- [Phân biệt tham số JVM với đối số chương trình](#tools-and-execution-007)
- [Nhận diện lỗi không tìm thấy lớp và không tương thích phiên bản](#tools-and-execution-008)

## <a id="tools-and-execution-001">Kiểm tra phiên bản java và javac</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra phiên bản `java` và `javac`

> **Module:** Java core — language-basics  
> **Mục:** Công cụ và thực thi  
> **Mục tiêu:** Dùng terminal xác định đúng phiên bản **Java runtime đang chạy** và **trình biên dịch Java đang được gọi**, tìm nguyên nhân khi chúng khác nhau, kiểm tra khả năng chạy của file `.class` và biết khi nào dùng `javac --release`.

## 1. Hai lệnh giống tên nhưng khác chức năng

Khi học Java, bạn thường cần hai chương trình dòng lệnh:

- **`java`:** Java launcher, khởi động môi trường thực thi để chạy chương trình Java. Phiên bản được hiển thị bởi `java -version` cho biết launcher/runtime mà terminal hiện đang gọi.
- **`javac`:** Trình biên dịch trong **JDK**, chuyển mã nguồn Java (`.java`) thành bytecode (`.class`). Phiên bản trong `javac -version` cho biết compiler đang được gọi.

Chữ **JDK** (*Java Development Kit*) chỉ bộ công cụ phát triển, thông thường chứa **cả** `java` và `javac`. Máy chỉ có runtime để chạy ứng dụng có thể có lệnh `java` nhưng không có `javac`.

| Câu hỏi | Kiểm tra bằng | Ý nghĩa |
| --- | --- | --- |
| Terminal đang khởi động phiên bản Java nào? | `java -version` | Phiên bản launcher/runtime được tìm thấy qua môi trường hiện hành |
| Terminal đang chọn compiler Java nào? | `javac -version` | Phiên bản `javac` đang được tìm thấy |
| Có thể biên dịch `Hello.java` không? | `javac Hello.java` | Kiểm tra thực tế có compiler và mã nguồn hợp lệ |
| Có thể chạy `Hello.class` không? | `java Hello` | Kiểm tra JVM/runtime cùng classpath và entry point phù hợp |

**Lưu ý:** `java -version` **không** cho biết file `.class` được biên dịch bằng compiler nào. Ngược lại, `javac -version` **không** chứng minh máy sẽ dùng cùng JDK đó khi bạn gõ `java`. Hai lệnh có thể đến từ hai thư mục cài đặt khác nhau.

## 2. Chạy kiểm tra phiên bản ngay trên terminal

Mở Terminal (macOS/Linux), Command Prompt hoặc PowerShell (Windows), lần lượt chạy:

~~~bash
java -version
javac -version
~~~

Ví dụ minh họa **một môi trường JDK 17**, không phải kết quả cố định của mọi máy:

~~~text
openjdk version "17.0.x" ...
OpenJDK Runtime Environment ...
OpenJDK 64-Bit Server VM ...
javac 17.0.x
~~~

Trong kết quả thực tế, các dấu `...` và `x` được thay bằng thông tin phiên bản/build/vendor cụ thể. Bạn có thể thấy tên bản phân phối như Oracle, Eclipse Temurin, Microsoft, Amazon Corretto hoặc một bản dựng OpenJDK khác.

Cách đọc:

1. Tìm **phiên bản tính năng chính** (*feature version*): ví dụ `17` trong `17.0.x` hoặc `21` trong `21.0.x`.
2. Các phần còn lại thường cho biết cập nhật bảo trì và build. Hai máy cùng “Java 17” có thể có các patch/build khác nhau.
3. Ghi nhận **cả hai dòng kiểm tra**. Nếu chúng khác nhau, xác định nguyên nhân thay vì giả định Java đã hỏng.
4. Không suy ra bản mới nhất hoặc tình trạng bảo mật chỉ từ số phiên bản lớn: chính sách hỗ trợ/cập nhật tùy nhà cung cấp và thời điểm.

### `-version` hay `--version`?

Với Java hiện đại, có thể sử dụng:

~~~bash
java --version
javac --version
~~~

`--version` là cú pháp thông dụng từ **Java 9 trở lên**. Để kiểm tra trên cả nhiều cài đặt cũ, đặc biệt Java 8, `-version` là lựa chọn quen thuộc và tương thích hơn. Đừng vội kết luận “không có Java” nếu một bản cũ không hiểu `--version`: thử `-version`.

Với các bản Java 8, tên phiên bản đôi khi được in theo dạng `1.8.0_...`. **Java 8 ≈ 1.8**, không phải Java 1 theo cách gọi phiên bản hiện đại.

## 3. Đọc kết quả đúng: ví dụ ba tình huống

| `java -version` | `javac -version` | Kết luận bước đầu |
| --- | --- | --- |
| `17.0.x` | `17.0.x` | Runtime và compiler cùng dòng phiên bản 17; vẫn nên kiểm tra đường dẫn nếu có nhiều JDK |
| `11.0.x` | `17.0.x` | Compiler có thể tạo bytecode mới hơn JVM 11 hiểu được, trừ khi cấu hình target phù hợp |
| Có phiên bản, chạy được | “command not found”/“not recognized” | Có runtime được tìm thấy, nhưng `javac` không tìm được qua terminal hiện tại |

**Không bắt buộc** mọi hệ thống có cả hai số phiên bản giống hệt đến từng bản vá. Một compiler mới có thể được cấu hình tạo chương trình cho runtime cũ; tuy nhiên phải kiểm soát **phiên bản bytecode, API chuẩn và thư viện phụ thuộc**.

Ngược lại, `java` và `javac` cùng báo “17” **chưa đủ** để chứng minh chúng nằm trong một thư mục JDK. Ví dụ một lệnh có thể là alias, wrapper hoặc shim trỏ sang cài đặt khác. Đó là lúc cần xem **đường dẫn thực tế**.

## 4. Xác định terminal đang tìm `java` và `javac` ở đâu

### Windows — PowerShell

~~~powershell
where.exe java
where.exe javac
Get-Command java -All
Get-Command javac -All
$env:JAVA_HOME
~~~

- `where.exe` cho biết những executable trùng tên được tìm thấy trên `PATH` theo cơ chế tìm kiếm của Windows.
- `Get-Command ... -All` hữu ích trong PowerShell vì còn có thể phát hiện alias/function/command được PowerShell ưu tiên.
- `$env:JAVA_HOME` in giá trị biến môi trường trong **phiên PowerShell hiện tại** nếu có.

**Phải dùng `where.exe`**, không chỉ `where` trong PowerShell: `where` có thể là alias của `Where-Object`, không phải chương trình dò đường dẫn.

### macOS / Linux — shell

~~~bash
command -v java
command -v javac
type -a java
type -a javac
echo "$JAVA_HOME"
~~~

`command -v` cho thấy command nào được shell phân giải; `type -a` (với Bash và nhiều shell phổ biến) có thể liệt kê nhiều kết quả/alias/function.

Tham khảo thêm tùy hệ điều hành:

~~~bash
# Linux có công cụ readlink hỗ trợ -f:
readlink -f "$(command -v java)"
~~~

~~~bash
# macOS: liệt kê một số cài đặt JDK mà java_home biết:
 /usr/libexec/java_home -V
~~~

Lệnh macOS trên có **một khoảng trắng đầu dòng không cần thiết**; khi gõ hãy dùng `/usr/libexec/java_home -V`. `readlink -f` không phải tùy chọn portable trên mọi phiên bản macOS, vì vậy không áp dụng máy móc lệnh Linux.

### Vì sao các đường dẫn có thể không giống bạn nghĩ?

Giả sử máy cài JDK 11 và JDK 17. Khi gõ `java`, hệ điều hành/shell tìm lệnh theo môi trường hiện tại (thường là thứ tự trong `PATH`), không tự chọn bản JDK phù hợp với mã nguồn bạn định chạy.

Ngoài ra:

- IDE có thể được cấu hình sử dụng JDK khác với terminal.
- Maven/Gradle có thể được cấu hình JDK/toolchain riêng; các lệnh terminal trên chỉ mô tả môi trường bạn vừa kiểm tra, **không tự chứng minh mọi build tool dùng cùng JDK**.
- Trình khởi chạy qua wrapper, alias hay executable manager có thể chọn Java theo cấu hình riêng.
- Chạy chương trình trong container, CI hoặc SSH là **một môi trường khác** với terminal trên máy cá nhân.

## 5. `JAVA_HOME`, `PATH` và phiên bản Java: ba điều khác nhau

| Thành phần | Vai trò | Điều không nên giả định |
| --- | --- | --- |
| `JAVA_HOME` | Biến môi trường thường trỏ tới thư mục JDK cho build tool và script | Không bảo đảm lệnh `java` trong terminal đang dùng đúng thư mục ấy |
| `PATH` | Danh sách vị trí tìm executable khi gõ tên lệnh | Không bảo đảm chỉ có một bản Java hoặc không có wrapper |
| `java.home` (thuộc tính Java runtime) | Thư mục runtime của **tiến trình Java thực sự đang chạy** | Không nhất thiết bằng giá trị `JAVA_HOME` hoặc vị trí JDK dùng để biên dịch |

Một cách điều chỉnh **tạm thời trong phiên terminal hiện tại**:

**Windows PowerShell (đường dẫn chỉ là ví dụ, hãy thay bằng JDK thật):**

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
javac -version
~~~

**macOS/Linux, Bash hoặc shell tương tự:**

~~~bash
export JAVA_HOME="/duong-dan/toi/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version
~~~

Các lệnh này **không cài Java**, chỉ thay đổi cách phiên shell hiện tại tìm công cụ. Đừng sao chép đường dẫn ví dụ nếu thư mục ấy không tồn tại. Trên một số shell có cache đường dẫn command, có thể cần mở terminal mới hoặc chạy `hash -r` (Bash) sau khi đổi `PATH`.

**Quy tắc chẩn đoán:** kiểm tra phiên bản → kiểm tra đường dẫn → kiểm tra `JAVA_HOME` và cấu hình build tool. Không nên chỉnh sửa biến môi trường toàn hệ thống trước khi biết lỗi nằm ở đâu.

## 6. Ví dụ Java chạy được: xác nhận phiên bản từ bên trong chương trình

Lệnh `java -version` phản ánh Java mà terminal gọi. Ta cũng có thể hỏi **chính chương trình đang chạy** về runtime của nó.

Tạo tệp **VersionEnvironmentDemo.java**:

~~~java
public class VersionEnvironmentDemo {
    public static void main(String[] args) {
        System.out.println("Java version : "
                + System.getProperty("java.version"));
        System.out.println("Runtime name : "
                + System.getProperty("java.runtime.name"));
        System.out.println("Java vendor  : "
                + System.getProperty("java.vendor"));
        System.out.println("VM name      : "
                + System.getProperty("java.vm.name"));
        System.out.println("Runtime home : "
                + System.getProperty("java.home"));
        System.out.println("Feature      : "
                + Runtime.version().feature());
    }
}
~~~

Đây là mã **Java 9+** vì có `Runtime.version()`; để học trên môi trường phổ biến, hãy dùng **JDK 11 hoặc 17 trở lên**.

Biên dịch và chạy trong thư mục chứa file:

~~~bash
javac VersionEnvironmentDemo.java
java VersionEnvironmentDemo
~~~

Đầu ra minh họa:

~~~text
Java version : 17.0.x
Runtime name : OpenJDK Runtime Environment
Java vendor  : <vendor của runtime>
VM name      : OpenJDK 64-Bit Server VM
Runtime home : <đường dẫn runtime hiện tại>
Feature      : 17
~~~

Giá trị thực tế tùy môi trường; không phải mọi JVM đều in đúng tên ví dụ trên. Nếu chạy trong IDE, hãy so sánh đầu ra của ví dụ **ở terminal** và **trong IDE** để kiểm tra chúng có chọn runtime khác nhau không.

Điều quan trọng:

- `System.getProperty("java.version")` và `Runtime.version().feature()` đo runtime **đang thực thi mã**.
- `java.home` đo vị trí runtime đang dùng **bên trong tiến trình**, không phải vị trí JDK chắc chắn đã chạy `javac`.
- Chương trình không tự biết chính xác compiler `javac` nào đã tạo file class; muốn biết bạn đã gọi compiler nào cần xem lệnh build và cấu hình của nó.

### Điều gì xảy ra nếu chỉ có `java` mà không có `javac`?

Nếu `java -version` thành công nhưng `javac -version` báo không tìm thấy lệnh, có thể máy chỉ có runtime, hoặc compiler không nằm trên `PATH`. Khi cần học hoặc build từ `.java`, hãy cài/chọn một **JDK** tương thích, rồi xác minh lại cả hai lệnh.

Không sử dụng thông báo thành công của `java -version` để khẳng định chắc chắn đã có toàn bộ công cụ phát triển.

## 7. Phiên bản compiler khác với phiên bản class file

Đây là nguyên nhân quan trọng nhất của lỗi “Java trên máy chạy quá cũ”.

Khi biên dịch, `javac` tạo bytecode `.class` theo một **class-file version**. Runtime Java cũ hơn có thể không đọc được định dạng mới.

| Mức Java mục tiêu | Class-file major version thường gặp |
| --- | ---: |
| Java 8 | 52 |
| Java 11 | 55 |
| Java 17 | 61 |
| Java 21 | 65 |

Ví dụ, chương trình đã build cho **Java 17 (major 61)** không thể được nạp bởi runtime chỉ hỗ trợ tối đa **Java 11 (major 55)**. Khi đó có thể nhận:

~~~text
java.lang.UnsupportedClassVersionError:
  ... has been compiled by a more recent version
  of the Java Runtime (class file version 61.0);
  this runtime only recognizes class file versions up to 55.0
~~~

Đây là **thông báo minh họa**, không nhất thiết giống hệt thông báo của mọi vendor.

### Kiểm tra file class bằng `javap`

Sau khi biên dịch `VersionEnvironmentDemo.java` bằng JDK:

~~~bash
javap -verbose VersionEnvironmentDemo
~~~

Tìm dòng như:

~~~text
major version: 61
~~~

`javap` là công cụ xem thông tin class/bytecode của JDK. **Dòng major version của file class** cho biết mức định dạng của class ấy, không trực tiếp chứng minh file đã được tạo bằng phiên bản compiler cụ thể nào.

### Biên dịch cho phiên bản runtime mục tiêu

Nếu dùng một compiler mới hơn, có thể yêu cầu nó build cho một mức Java cũ hơn mà compiler còn hỗ trợ. Ví dụ dùng **JDK 17** để nhắm **Java 11**:

~~~bash
javac --release 11 VersionEnvironmentDemo.java
javap -verbose VersionEnvironmentDemo
java VersionEnvironmentDemo
~~~

Với ví dụ này, major version dự kiến là **55**, vì mục tiêu được chọn là Java 11. Để chứng minh khả năng chạy trên Java 11, bạn vẫn phải **thực sự chạy chương trình bằng runtime Java 11**, không chỉ bằng runtime 17 trên máy build.

`--release` kiểm soát đồng thời mức ngôn ngữ, class-file target và các **API Java chuẩn** khả dụng ở release mục tiêu (trong giới hạn compiler hỗ trợ). Nó giúp tránh lỗi thường gặp khi dùng `-source`/`-target` nhưng vô tình gọi một API Java chuẩn chỉ có trong runtime mới.

**Giới hạn quan trọng:** `--release` không làm thư viện bên thứ ba tự tương thích với Java cũ, không xử lý native library hay môi trường runtime khác, và không thể yêu cầu compiler hỗ trợ release mà nó không biết. Ví dụ JDK 8 không hiểu `--release 11`.

### Kiểm tra đường build và runtime thực tế

Một kết quả đúng cần tối thiểu ba thông tin:

1. Phiên bản **compiler** dùng để build (`javac -version` hoặc thông tin build tool).
2. Mức target của file `.class` (`javap -verbose` hoặc cấu hình `--release`).
3. Phiên bản **runtime thực sự dùng khi chạy** (`java -version` tại máy đích).

Ba thông tin này có liên quan nhưng **không được đồng nhất**.

## 8. Các lỗi phiên bản và cách xử lý

| Triệu chứng | Nguyên nhân khả dĩ | Kiểm tra / hướng xử lý |
| --- | --- | --- |
| `java: command not found` hoặc “not recognized” | Không cài runtime/launcher, `PATH` sai, môi trường shell khác | Tìm executable Java; kiểm tra cài đặt và `PATH` |
| `javac: command not found` nhưng `java` chạy được | Chỉ có runtime hoặc `PATH` chưa trỏ tới JDK `bin` | Xác minh vị trí `javac`, chọn JDK phù hợp |
| `java` báo 11, `javac` báo 17 | Hai executable có thể được chọn từ các bản cài khác nhau | Kiểm tra đường dẫn từng lệnh và mục tiêu build/runtime |
| `UnsupportedClassVersionError` | Bytecode mới hơn runtime đang chạy | Dùng runtime phù hợp hoặc rebuild với `--release` đúng |
| `invalid flag: --release` | Compiler quá cũ hoặc không hỗ trợ flag | Xem `javac -version`, chọn compiler phù hợp |
| `error: release version 21 not supported` | Compiler hiện tại không hỗ trợ mục tiêu 21 | Không thể biên dịch Java 21 bằng compiler quá cũ; dùng JDK hỗ trợ |
| `NoSuchMethodError` sau khi đổi runtime/thư viện | Có thể chạy với API/thư viện không khớp phiên bản lúc build | Xem dependency/classpath, API runtime và bản thư viện thực sự được nạp |
| IDE chạy khác terminal | IDE chọn Project SDK/Run Configuration riêng | Kiểm tra cấu hình JDK trong IDE và terminal |
| Dùng Docker/CI nhưng máy cá nhân chạy tốt | Container/runner có runtime/JDK khác | Kiểm tra phiên bản **bên trong** container/runner, không chỉ máy chủ |

**Đừng chữa nhầm bệnh:** `Could not find or load main class` thường liên quan **classpath hoặc tên main class**, không nhất thiết là lỗi phiên bản. Mã nguồn sai cú pháp là lỗi biên dịch; `UnsupportedClassVersionError` là lỗi tương thích bytecode tại runtime.

## 9. Quy trình kiểm tra nhanh trước khi bắt đầu dự án

Lần đầu mở một dự án Java hoặc nhận một yêu cầu chạy code, hãy làm theo thứ tự:

~~~mermaid
flowchart TD
    A["Mở terminal tại môi trường cần kiểm tra"] --> B["java -version và javac -version"]
    B --> C{"Cả hai lệnh có mặt?"}
    C -->|Không| D["Kiểm tra JDK, PATH, alias/wrapper"]
    C -->|Có| E["Ghi nhận các phiên bản chính"]
    E --> F["Kiểm tra đường dẫn java/javac"]
    F --> G["Xác định target Java của dự án"]
    G --> H["Biên dịch với cấu hình phù hợp"]
    H --> I["Chạy bằng runtime mục tiêu"]
    I --> J{"Kết quả ổn?"}
    J -->|Không| K["Đọc lỗi: classpath, bytecode hay API"]
    K --> F
    J -->|Có| L["Môi trường đã được xác nhận"]
~~~

Checklist ngắn:

- [ ] `java -version` có kết quả và bạn biết đang dùng phiên bản nào.
- [ ] `javac -version` có kết quả nếu cần biên dịch mã Java.
- [ ] Đã kiểm tra vị trí executable, nhất là khi cài nhiều JDK.
- [ ] Biết dự án yêu cầu runtime Java nào; không chọn version ngẫu nhiên.
- [ ] Nếu có `--release`, biết mục tiêu biên dịch và giới hạn của nó.
- [ ] Chạy thử bằng **runtime đích**, không chỉ runtime trên máy phát triển.

## 10. Bài tập tự luyện

**Bài 1 — Đọc phiên bản:** Giả sử `java -version` cho `11.0.x` còn `javac -version` cho `17.0.x`. Có phải compiler Java 17 không thể dùng được không?

*Gợi ý:* Chưa thể kết luận như vậy. Compiler 17 có thể tạo class target 11 với `--release 11` nếu mã và API tương thích. Hãy xác minh lệnh build và thử chạy artifact trên runtime 11 thực.

**Bài 2 — Xác định đường dẫn:** Trên máy có hai JDK, hãy liệt kê vị trí `java` và `javac` bằng lệnh phù hợp hệ điều hành. So sánh với `JAVA_HOME` và giải thích vì sao khác nhau.

**Bài 3 — Kiểm tra file class:** Biên dịch `VersionEnvironmentDemo.java`, dùng `javap -verbose` xác định major version. Sau đó (nếu compiler hỗ trợ) build với `--release 11` và kiểm tra sự khác biệt.

**Bài 4 — Nhận diện lỗi:** Bạn đưa file `.class` target Java 17 lên máy Java 11, nhận `UnsupportedClassVersionError`. Hãy đề xuất **hai** cách giải quyết hợp lệ và mô tả rủi ro phụ thuộc thư viện nếu chọn build về Java 11.

**Bài 5 — IDE so với terminal:** Một ứng dụng chạy trong IDE nhưng thất bại khi chạy ở terminal. Hãy kiểm tra runtime được in từ `VersionEnvironmentDemo` ở cả hai nơi, đường dẫn `java` trong terminal và cấu hình Run/SDK của IDE.

## 11. Tự đánh giá

Bạn hiểu bài khi có thể giải thích:

1. Vì sao phải chạy **cả `java -version` lẫn `javac -version`** thay vì chỉ một lệnh.
2. Vì sao `JAVA_HOME` không tự động chứng minh lệnh `java` đang dùng đúng JDK đó.
3. Sự khác nhau giữa **phiên bản compiler**, **class-file target** và **phiên bản runtime**.
4. Khi gặp `UnsupportedClassVersionError`, vì sao cần xem `--release` và runtime đích thay vì chỉ đổi tên file.
5. Cách xác nhận trên Windows/macOS/Linux bằng lệnh đơn giản mà không làm thay đổi dữ liệu ứng dụng.

**Kết luận:** `java -version` kiểm tra môi trường **chạy**, `javac -version` kiểm tra công cụ **biên dịch**. Muốn chương trình Java chạy đúng, cần kết hợp cả hai với đường dẫn executable thực, cấu hình target và kiểm thử trên runtime mà ứng dụng sẽ sử dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tools-and-execution-002">Tạo tệp nguồn và biên dịch bằng javac</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo tệp nguồn và biên dịch bằng javac

Để máy tính thực thi chương trình Java, trước tiên bạn cần viết **mã nguồn** vào tệp có đuôi `.java`, rồi sử dụng **trình biên dịch `javac`** của JDK để tạo **bytecode** trong tệp `.class`. Sau đó, lệnh `java` khởi động JVM để chạy lớp có phương thức `main`.

Bài này hướng dẫn thao tác từ đầu bằng **JDK 17 trở lên**, sử dụng terminal Windows PowerShell hoặc Linux. Không cần IDE, Maven, Gradle hay thư viện ngoài.

## 1. Phân biệt ba thứ rất dễ nhầm

| Thành phần | Dùng để làm gì? | Ví dụ |
| --- | --- | --- |
| Tệp nguồn `.java` | Chứa mã mà người lập trình viết | `HelloCompiler.java` |
| Lệnh `javac` | Phân tích mã, kiểm tra nhiều lỗi và tạo bytecode | `javac HelloCompiler.java` |
| Tệp `.class` | Kết quả biên dịch cho JVM | `HelloCompiler.class` |
| Lệnh `java` | Chạy lớp qua JVM; với ví dụ này gọi `main` | `java HelloCompiler` |
| JDK | Bộ công cụ phát triển, gồm `javac` và các công cụ chạy Java | JDK 17+ |

**`javac` không chạy chương trình.** Một lần biên dịch thành công cũng không chứng minh thuật toán đúng với mọi dữ liệu. Nó chủ yếu xác nhận mã thỏa những quy tắc cú pháp, kiểu và ngữ nghĩa mà compiler có thể kiểm tra.

Quy trình tổng quát:

~~~text
Mã nguồn HelloCompiler.java
         |
         | javac -encoding UTF-8 -d out src/HelloCompiler.java
         v
Bytecode out/HelloCompiler.class
         |
         | java -cp out HelloCompiler
         v
JVM thực thi -> Kết quả trong terminal
~~~

## 2. Kiểm tra JDK trước khi viết mã

Mở **PowerShell** trên Windows hoặc **terminal** trên Linux, chạy:

~~~bash
java -version
javac -version
~~~

Ví dụ kết quả cho JDK 17 có thể có dạng:

~~~text
openjdk version "17.0.x" ...
javac 17.0.x
~~~

Phần `x` đại diện số bản vá; kết quả thật thay đổi theo bản phân phối JDK. Điều cần xác nhận là cả **`java` và `javac` cùng có thể được gọi** và phiên bản đáp ứng yêu cầu.

Nếu thấy thông báo `javac` không được nhận diện:

- Kiểm tra đã cài **JDK**, thay vì chỉ có runtime Java.
- Kiểm tra biến môi trường `PATH` có thư mục `bin` của JDK phù hợp.
- Mở terminal mới sau khi thay đổi cấu hình môi trường.
- Kiểm tra xem `java` và `javac` có đang trỏ tới hai JDK khác nhau hay không.

Trên Windows PowerShell, có thể xem đường dẫn lệnh bằng `Get-Command java, javac`; trên Linux, dùng `command -v java` và `command -v javac`. Đây là các lệnh kiểm tra môi trường, không phải lệnh biên dịch.

## 3. Tạo thư mục dự án đơn giản

Chọn một thư mục làm nơi thực hành, ví dụ `java-compile-demo`, rồi mở terminal **ngay trong thư mục này**. Cấu trúc mong muốn:

~~~text
java-compile-demo/
├── src/
│   └── HelloCompiler.java
└── out/                 (chứa .class sau khi biên dịch)
~~~

Tạo thư mục:

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force -Path src, out | Out-Null
~~~

**Linux (Bash):**

~~~bash
mkdir -p src out
~~~

Dùng trình soạn thảo văn bản để tạo tệp **`src/HelloCompiler.java`**. Lưu dưới dạng **UTF-8**, chọn đúng phần mở rộng `.java`; tránh vô tình lưu thành `HelloCompiler.java.txt` trên Windows.

## 4. Viết chương trình Java đầu tiên

Nội dung đầy đủ của `src/HelloCompiler.java`:

~~~java
public class HelloCompiler {
    public static void main(String[] args) {
        String visitor = args.length > 0 ? args[0] : "ban";
        System.out.println("Xin chao, " + visitor + "!");
    }
}
~~~

Những thành phần cần hiểu:

- `public class HelloCompiler` khai báo lớp công khai. Tệp chứa lớp công khai này phải mang tên `HelloCompiler.java` (đúng chữ hoa/chữ thường).
- Dấu `{ }` đánh dấu khối lớp và khối phương thức.
- `public static void main(String[] args)` là điểm bắt đầu khi chạy chương trình Java theo cách trong bài này.
- `args` là mảng các đối số mà bạn truyền sau tên lớp khi chạy.
- Toán tử ba ngôi `?:` chọn đối số đầu tiên nếu có; nếu không có thì dùng chuỗi mặc định `"ban"`.
- `System.out.println(...)` in một dòng ra terminal.
- Dấu `;` kết thúc các câu lệnh Java tương ứng.

Không cần viết `package` cho ví dụ đầu này: lớp nằm trong **unnamed package** để lệnh biên dịch và chạy đơn giản.

## 5. Biên dịch từ mã nguồn thành bytecode

Từ thư mục `java-compile-demo`, chạy **cùng một lệnh** trên PowerShell hoặc Bash:

~~~bash
javac -encoding UTF-8 -d out src/HelloCompiler.java
~~~

Ý nghĩa các tham số:

| Thành phần | Ý nghĩa |
| --- | --- |
| `javac` | Gọi trình biên dịch Java |
| `-encoding UTF-8` | Chỉ định cách đọc ký tự mã nguồn |
| `-d out` | Đặt thư mục gốc chứa các tệp `.class` được tạo |
| `src/HelloCompiler.java` | Đường dẫn của tệp nguồn cần biên dịch |

Nếu thành công, `javac` thường **không in thông báo**. Hãy kiểm tra thư mục `out`: bạn sẽ thấy `HelloCompiler.class`.

Lưu ý: vì lệnh đã chỉ định `-d out`, thư mục `out` phải tồn tại trước khi chạy. Đối với mã **không có package**, file đầu ra nằm ngay trong `out`. Không cần di chuyển file `.class` bằng tay.

### Chạy kết quả biên dịch

~~~bash
java -cp out HelloCompiler
~~~

Kết quả:

~~~text
Xin chao, ban!
~~~

Thử truyền tên:

~~~bash
java -cp out HelloCompiler Minh
~~~

Kết quả:

~~~text
Xin chao, Minh!
~~~

Giải thích:

- `-cp out` (viết đầy đủ là `-classpath out`) chỉ nơi JVM cần tìm lớp của chương trình.
- `HelloCompiler` là **tên lớp**, không phải tên file `HelloCompiler.java` hoặc `HelloCompiler.class`.
- `Minh` là đối số dòng lệnh, xuất hiện ở `args[0]`.

Dùng dấu `/` trong các đường dẫn ở ví dụ trên phù hợp cho những lệnh Java này trên cả Windows và Linux. Nếu đường dẫn thực tế chứa khoảng trắng, đặt **toàn bộ đường dẫn** trong dấu nháy kép.

## 6. Sửa mã, biên dịch lại rồi chạy lại

Thử thay dòng:

~~~java
System.out.println("Xin chao, " + visitor + "!");
~~~

thành:

~~~java
System.out.println("Chuc ban hoc Java tot, " + visitor + "!");
~~~

Sau khi **lưu tệp nguồn**, chạy lại:

~~~bash
javac -encoding UTF-8 -d out src/HelloCompiler.java
java -cp out HelloCompiler Minh
~~~

Kết quả mới:

~~~text
Chuc ban hoc Java tot, Minh!
~~~

Chỉ sửa `.java` rồi chạy lại `java` mà **không biên dịch lại** có thể khiến bạn nhìn thấy **bytecode cũ** trong `.class`. Đặc biệt nếu lần biên dịch mới thất bại nhưng file `.class` cũ vẫn còn, JVM vẫn có thể chạy lớp cũ. Vì vậy, khi thử sửa mã, hãy đọc kết quả biên dịch trước khi tin rằng bản mới đã được chạy.

## 7. Khi chương trình có `package`

Từ khóa `package` xác định namespace của lớp. Ví dụ khác này độc lập với `HelloCompiler`.

Tạo thư mục `src/tutorial`:

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force -Path src/tutorial | Out-Null
~~~

**Linux:**

~~~bash
mkdir -p src/tutorial
~~~

Lưu mã sau vào **`src/tutorial/PackageHello.java`**:

~~~java
package tutorial;

public class PackageHello {
    public static void main(String[] args) {
        System.out.println("Package works");
    }
}
~~~

Biên dịch:

~~~bash
javac -encoding UTF-8 -d out src/tutorial/PackageHello.java
~~~

Vì lớp khai báo `package tutorial;`, compiler tạo file:

~~~text
out/
└── tutorial/
    └── PackageHello.class
~~~

Chạy với **tên lớp đầy đủ kèm package**:

~~~bash
java -cp out tutorial.PackageHello
~~~

Kết quả:

~~~text
Package works
~~~

Không chạy bằng `java -cp out PackageHello` vì lớp này không thuộc unnamed package. Khi dùng `-d out`, `javac` sắp xếp output theo package; `java` cần tìm lớp bằng tên đủ điều kiện.

**Phân biệt:** folder `src/tutorial` là vị trí tổ chức tệp nguồn theo thói quen tốt; khai báo `package tutorial;` trong nội dung mã mới xác định package Java. Trong một dự án nhỏ, hãy để cấu trúc thư mục phản ánh package cho dễ hiểu và dễ xây dựng.

## 8. Một số lỗi biên dịch thường gặp

Bạn có thể thử từng lỗi sau, **mỗi lần chỉ sửa một chỗ** rồi biên dịch lại.

| Tình huống | Triệu chứng thường gặp | Cách xử lý |
| --- | --- | --- |
| Lưu file thành `HelloCompiler.java.txt` | Không tìm thấy tệp `.java` | Hiển thị phần mở rộng và đổi đúng tên |
| Viết `public class HelloCompilor` trong `HelloCompiler.java` | Lỗi tên lớp công khai không khớp file | Đổi tên lớp hoặc file cho đúng |
| Thiếu `;` hoặc `}` | `';' expected` hoặc lỗi cú pháp | Kiểm tra câu lệnh và dấu ngoặc |
| Gán `int count = "3";` | `incompatible types` | Sửa kiểu hoặc chuyển chuỗi đúng cách |
| Gọi `javac` ở sai thư mục | `file not found` | Kiểm tra thư mục làm việc và đường dẫn nguồn |
| Chạy `java HelloCompiler.class` | Không tìm thấy lớp phù hợp | Dùng tên lớp `HelloCompiler`, bỏ đuôi file |
| Quên `-cp out` khi đã dùng `-d out` | JVM không tìm thấy lớp | Thêm `-cp out` |
| Có `package tutorial;` nhưng chạy tên ngắn | Không tìm thấy main class | Chạy `tutorial.PackageHello` |
| `java` cũ hơn phiên bản dùng để biên dịch | `UnsupportedClassVersionError` khi chạy | Dùng runtime tương thích hoặc biên dịch cho phiên bản đích |

Các thông báo có thể khác theo bản JDK và hệ điều hành. Điều quan trọng là **phân loại nguyên nhân**: tên lớp, thư mục, cú pháp, kiểu dữ liệu, package hay phiên bản.

### Tự thử một lỗi compiler có chủ đích

Trong file `HelloCompiler.java`, thêm dòng sau **bên trong `main`**:

~~~java
int hours = "two"; // Sai: String không thể gán cho int.
~~~

Chạy `javac -encoding UTF-8 -d out src/HelloCompiler.java`, bạn sẽ nhận lỗi kiểu dữ liệu. Sửa thành:

~~~java
int hours = 2;
~~~

Sau đó biên dịch lại. Đây là minh họa rằng `javac` có thể từ chối mã **trước khi chương trình chạy**.

## 9. Các tùy chọn `javac` hữu ích sau khi đã chạy được cơ bản

| Lệnh hoặc tùy chọn | Khi nào hữu ích? |
| --- | --- |
| `javac -version` | Xác minh phiên bản compiler |
| `javac -encoding UTF-8 ...` | Ấn định encoding đầu vào nguồn |
| `javac -d out ...` | Tách tệp `.class` khỏi thư mục nguồn |
| `javac -Xlint:all ...` | Hiển thị thêm cảnh báo compiler (tùy mã) |
| `javac --release 17 ...` | Khi dùng JDK mới hơn để hướng tới API/ngôn ngữ Java 17 |
| `javac -cp libs/something.jar ...` | Khi thực sự cần biên dịch với thư viện ngoài; đường dẫn chỉ là ví dụ |

Ví dụ biên dịch nhắm Java 17 khi đang sử dụng JDK hỗ trợ tùy chọn này:

~~~bash
javac --release 17 -encoding UTF-8 -d out src/HelloCompiler.java
~~~

`--release 17` ràng buộc mức ngôn ngữ và API nền tảng Java tương ứng với release được hỗ trợ. Tuy vậy, nó không tự bảo đảm rằng **mọi thư viện bên thứ ba** hoặc cấu hình runtime của bạn đều tương thích; phải kiểm tra riêng các phụ thuộc.

Không cần thêm các tham số phức tạp khi bài toán chỉ có một tệp nguồn.

## 10. Bài tập tự thực hành

**Bài 1 — Biên dịch không có IDE.** Tạo một thư mục mới, lưu `src/HelloCompiler.java` đúng tên, rồi chạy `javac` và `java`. Ghi lại tên file `.class` được tạo. Giải thích vì sao bước chạy không dùng `.java` làm đối số lớp.

**Bài 2 — Truyền tham số.** Chạy `java -cp out HelloCompiler Lan`. Dự đoán chính xác một dòng kết quả trước khi chạy. Thử bỏ tham số và giải thích nhánh `args.length > 0`.

**Bài 3 — Tìm lỗi.** Đổi tên lớp thành `HelloCompilor` nhưng giữ nguyên tên tệp. Dự đoán `javac` báo lỗi ở bước nào và sửa tối thiểu ra sao. Sau bài tập, khôi phục tên đúng.

**Bài 4 — Package.** Biên dịch `src/tutorial/PackageHello.java` và giải thích vì sao output nằm trong `out/tutorial/`. So sánh `java -cp out PackageHello` với `java -cp out tutorial.PackageHello`.

**Bài 5 — Chạy sau khi sửa nguồn.** Đổi câu in ra terminal, chạy chương trình nhưng cố ý bỏ bước `javac`. Mô tả vì sao kết quả có thể vẫn là câu cũ. Sau đó biên dịch lại và đối chiếu.

### Checklist trước khi chuyển sang bài Java tiếp theo

- [ ] Cài JDK và gọi được cả `java` lẫn `javac`.
- [ ] Tạo được file nguồn `.java` bằng UTF-8.
- [ ] Hiểu quan hệ giữa tên file và tên lớp `public`.
- [ ] Biên dịch với `javac -encoding UTF-8 -d out ...`.
- [ ] Chạy bằng `java -cp out TenLop`.
- [ ] Giải thích đúng `package` và tên lớp đầy đủ khi có package.
- [ ] Phân biệt lỗi biên dịch, lỗi lúc chạy và chương trình chạy bản `.class` cũ.
- [ ] Tự sửa ít nhất một lỗi `javac` rồi biên dịch và chạy lại thành công.

## Ghi nhớ

**`.java` là mã nguồn, `javac` biên dịch mã nguồn thành `.class`, còn `java` khởi động JVM để chạy lớp đã biên dịch.** Khi quen quy trình **tạo tệp → biên dịch → sửa lỗi → chạy → đối chiếu kết quả**, bạn có thể học cú pháp Java mà không phụ thuộc vào nút Run của IDE.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tools-and-execution-003">Chạy lớp bằng java và phân biệt tên lớp với tên tệp</a>

<details>
<summary>Xem chi tiết</summary>

# Chạy lớp bằng java và phân biệt tên lớp với tên tệp

## 1. Điều cần hiểu trước khi gõ lệnh

Trong quy trình Java truyền thống, bạn **viết mã nguồn trong tệp `.java`**, dùng **`javac`** biên dịch mã nguồn thành một hoặc nhiều **tệp `.class`**, rồi dùng lệnh **`java`** để khởi động một lớp có điểm vào chương trình.

~~~mermaid
flowchart LR
    A["Welcome.java<br/>Tên tệp nguồn"] -->|"javac"| B["Welcome.class<br/>Bytecode"]
    B -->|"java Welcome<br/>Tên lớp"| C["JVM nạp lớp<br/>và gọi main"]
    C --> D["Chương trình chạy"]
~~~

**Quy tắc cốt lõi:** trong chế độ chạy **class đã biên dịch**, đối số chính của `java` là **tên lớp (class name)**, thường kèm tên package nếu có. Bạn **không** truyền đường dẫn tệp `.class` như thể đó là chương trình thực thi.

Ví dụ ba lệnh dưới đây không mang cùng ý nghĩa:

| Lệnh | Ý nghĩa |
|---|---|
| `javac Welcome.java` | Biên dịch tệp nguồn `Welcome.java` |
| `java Welcome` | Chạy lớp có tên `Welcome` mà JVM tìm được trên classpath |
| `java Welcome.java` | Chạy **tệp nguồn** bằng source-file mode (có từ Java 11), không phải chạy lớp `Welcome` đã biên dịch theo cách ở dòng trên |

Các ví dụ sử dụng **JDK 17 trở lên**. Hãy mở terminal tại thư mục đúng như ví dụ hoặc điều chỉnh đường dẫn phù hợp với máy của bạn.

## 2. Chạy một lớp đơn giản, không có package

Tạo tệp **`Welcome.java`** với nội dung sau:

~~~java
public class Welcome {
    public static void main(String[] args) {
        String person = args.length > 0 ? args[0] : "Java";
        System.out.println("Xin chao, " + person + "!");
    }
}
~~~

Mở terminal trong thư mục chứa `Welcome.java`. Xác nhận có đủ công cụ của JDK:

~~~bash
java -version
javac -version
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 Welcome.java
java -cp . Welcome
java -cp . Welcome Lan
~~~

Đầu ra của hai lệnh `java`, theo thứ tự:

~~~text
Xin chao, Java!
Xin chao, Lan!
~~~

Giải thích từng thành phần:

- `javac` đọc **`Welcome.java`** và, nếu biên dịch thành công, tạo **`Welcome.class`** trong thư mục hiện hành.
- `--release 17` hướng tới cú pháp/API/class-file tương thích Java 17; trình biên dịch đang dùng phải hỗ trợ mức release này.
- `-encoding UTF-8` chỉ định cách đọc mã nguồn; không bảo đảm terminal hiển thị mọi ký tự Unicode giống nhau.
- `java` là chương trình launcher của runtime; nó khởi động JVM để tìm và thực thi class.
- `-cp .` đặt classpath là thư mục hiện tại; dấu `.` là đường dẫn thư mục, **không phải phần mở rộng tệp**.
- `Welcome` là **tên class**, không phải `Welcome.java` hay `Welcome.class`.
- `Lan` là đối số ứng dụng; với chương trình này nó trở thành `args[0]`.

Nhờ điều kiện `args.length > 0`, chạy không có đối số không gây `ArrayIndexOutOfBoundsException`.

### Lệnh tương ứng trong Bash và PowerShell

Cú pháp `javac` và `java` ở trên **dùng được trong cả Bash và PowerShell**. Ví dụ muốn liệt kê file đầu vào và bytecode đã tạo:

**Bash (Linux/macOS/Git Bash):**

~~~bash
ls Welcome.java
javac --release 17 -encoding UTF-8 Welcome.java
ls Welcome.class
java -cp . Welcome
~~~

**PowerShell (Windows):**

~~~powershell
Get-Item .\Welcome.java
javac --release 17 -encoding UTF-8 .\Welcome.java
Get-Item .\Welcome.class
java -cp . Welcome
~~~

Đường dẫn `.\Welcome.java` dùng khi gọi `javac` là **đường dẫn đến tệp nguồn**. Khi gọi `java` để chạy class, vẫn viết **`Welcome`** vì đó là tên lớp JVM cần tìm.

## 3. Ba tên thường bị nhầm với nhau

| Khái niệm | Ví dụ | Dùng ở đâu? |
|---|---|---|
| **Tên tệp nguồn** | `Welcome.java` | Đối số cho `javac`; editor mở/chỉnh sửa |
| **Tên class đã khai báo** | `Welcome` trong `class Welcome` | Là tên định danh của lớp |
| **Tên tệp bytecode** | `Welcome.class` | Tệp `javac` tạo ra để JVM tải |
| **Tên đầy đủ của class** | `demo.Welcome` | Tên khi lớp thuộc package `demo` |
| **Đường dẫn nguồn** | `src/demo/Welcome.java` | Vị trí tệp trên đĩa |
| **Classpath root** | `out` | Thư mục hoặc tập entry để runtime tìm class |

Không nên suy luận tên class chỉ từ tên tệp. Hãy **đọc khai báo `class` và `package`**, rồi xác định classpath root đang chứa bytecode.

Trong đoạn `public class Welcome` ở ví dụ chính, Java bắt buộc tên tệp nguồn công khai là `Welcome.java`. Trên hệ thống phân biệt chữ hoa/thường, `welcome.java` và `Welcome.java` là những tên khác nhau.

## 4. Khi tên tệp nguồn không phải tên class chạy

Một tệp `.java` có thể chứa nhiều class cấp cao nhất (*top-level class*) miễn tuân thủ quy tắc về class `public`. Tạo tệp **`Examples.java`**:

~~~java
class Helper {
    static String message() {
        return "Hello from Helper";
    }
}

class Runner {
    public static void main(String[] args) {
        System.out.println(Helper.message());
    }
}
~~~

Chạy:

~~~bash
javac --release 17 Examples.java
java -cp . Runner
~~~

Kết quả:

~~~text
Hello from Helper
~~~

`javac` có thể tạo **`Helper.class`** và **`Runner.class`**, mặc dù tệp nguồn tên là **`Examples.java`**. Lý do: không có top-level class nào được khai báo `public`, nên tên tệp nguồn không phải bằng tên `Runner`.

**Lệnh `java -cp . Examples` sẽ không chạy được ví dụ này**, vì không có class tên `Examples`. Thử `java -cp . Runner` mới đúng.

Ngược lại, nếu khai báo `public class Runner`, tệp chứa nó phải được đặt là **`Runner.java`**. Đây là quy tắc biên dịch, không phải quy tắc buộc mọi tên nguồn lúc nào cũng giống tên class chạy.

## 5. Package làm thay đổi tên dùng với lệnh java

Xét cấu trúc dự án:

~~~text
project/
└── src/
    └── demo/
        └── Welcome.java
~~~

Tệp `src/demo/Welcome.java`:

~~~java
package demo;

public class Welcome {
    public static void main(String[] args) {
        String who = args.length > 0 ? args[0] : "Java";
        System.out.println("Xin chao tu demo, " + who + "!");
    }
}
~~~

Phần `package demo;` có nghĩa class có **tên đầy đủ (fully qualified class name)** là `demo.Welcome`, không còn chỉ là `Welcome` ở default package.

Từ thư mục `project`, tạo thư mục kết quả trước khi biên dịch.

**Bash:**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 -d out src/demo/Welcome.java
java -cp out demo.Welcome Lan
~~~

**PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -d out src/demo/Welcome.java
java -cp out demo.Welcome Lan
~~~

Kết quả:

~~~text
Xin chao tu demo, Lan!
~~~

Sau biên dịch, cấu trúc quan trọng sẽ là:

~~~text
project/
├── src/
│   └── demo/
│       └── Welcome.java
└── out/
    └── demo/
        └── Welcome.class
~~~

Phân tích lệnh chạy **`java -cp out demo.Welcome Lan`**:

- `-cp out`: nơi JVM bắt đầu tìm các class.
- `demo.Welcome`: class cần chạy, gồm **package + tên lớp**.
- `Lan`: đối số chuyển cho chương trình, không thuộc tên class.

**Classpath phải trỏ tới `out`, không phải `out/demo`**, bởi JVM dùng package `demo` để tìm đường dẫn tương ứng bên dưới classpath root.

~~~mermaid
flowchart TD
    CP["Classpath root: out/"] --> PK["Package: demo/"]
    PK --> BC["Welcome.class"]
    CMD["java -cp out demo.Welcome"] --> CP
~~~

Khi đang ở thư mục `project`, các cách gọi sau là **khác nhau**:

| Lệnh | Đánh giá |
|---|---|
| `java -cp out demo.Welcome` | Đúng |
| `java -cp out Welcome` | Sai tên lớp đầy đủ |
| `java -cp out/demo demo.Welcome` | Sai classpath root |
| `java -cp out/demo Welcome` | Có thể gây lỗi tên class bên trong không khớp đường dẫn/package; không phải cách chạy đúng |
| `java -cp out demo/Welcome.class` | Sai: truyền đường dẫn tệp thay vì tên lớp |

## 6. Điểm vào chương trình: lớp phải có main phù hợp

Trong Java 17, để dùng ví dụ chạy class thông thường như trên, lớp mục tiêu cần phương thức:

~~~java
public static void main(String[] args) {
    // Lệnh bắt đầu từ đây
}
~~~

Một biến thể tương đương có thể dùng `String... args`. Nhưng các phương thức dưới đây **không phải entry point thông thường của Java 17**:

~~~java
// Sai kiểu trả về:
public static int main(String[] args) { return 0; }

// Sai vì thiếu static:
public void main(String[] args) { }

// Sai kiểu tham số:
public static void main(int[] args) { }
~~~

Các class vẫn có thể **biên dịch thành công** nếu những phương thức đó hợp lệ như method thông thường; lỗi xuất hiện khi bạn cố launch chúng làm main class mà không có điểm vào được hỗ trợ.

**Đừng trộn quy tắc Java 17 với các dạng main mới hơn trong những phiên bản Java khác.** Bài này dùng JDK 17 để cú pháp và lệnh có thể đối chiếu nhất quán.

### Main class khác helper class

Một chương trình có thể dùng hàng chục class, nhưng lệnh `java` cần biết **class nào là điểm vào**. Ví dụ `Runner` gọi `Helper.message()`; việc `Helper` không có `main` không ngăn `Runner` chạy. Nhưng chạy `java Helper` như main class sẽ không có entry point phù hợp.

## 7. Phân biệt chế độ chạy class với chạy source trực tiếp

Từ Java 11, launcher còn có **source-file mode**, cho phép chạy trực tiếp một tệp nguồn đơn giản:

~~~bash
java Welcome.java
~~~

Đối chiếu hai cách:

| Cách | Lệnh | Ý nghĩa |
|---|---|---|
| **Class mode** | `javac Welcome.java` rồi `java Welcome` | Bạn biên dịch trước; launcher tìm class đã có trên classpath |
| **Source-file mode** | `java Welcome.java` | Launcher xử lý mã nguồn để thực thi trong lần gọi; không yêu cầu bạn tự tạo `.class` lưu trên đĩa |
| **JAR mode** | `java -jar program.jar` | Launcher dùng thông tin điểm vào của JAR thực thi và quy tắc classpath tương ứng |

Hai lệnh `java Welcome` và `java Welcome.java` khác nhau do **chế độ launcher**, không phải chỉ khác cách viết phần mở rộng. Trong class mode, không ghi `.java` hoặc `.class` theo sau tên lớp.

Source-file mode có những quy tắc biên dịch và tổ chức riêng; nó hữu ích cho thử nhanh ví dụ nhỏ. Khi cần quan sát bytecode, kiểm soát classpath hay mô phỏng quy trình build, nên thực hành `javac` rồi `java` trước.

**Không nhầm `java -jar app.jar` với `java -cp app.jar demo.Welcome`:** chế độ `-jar` dựa vào cấu hình entry point như `Main-Class` trong manifest của JAR; cách dùng `-cp` yêu cầu bạn nêu class để chạy. Không phải file JAR nào cũng tự chạy được bằng `-jar`.

## 8. Classpath không phải đường dẫn đến một file .class riêng lẻ

Classpath là tập **vị trí tìm class và thư viện**. Một entry có thể là thư mục chứa cây package hoặc một file JAR.

Ví dụ có class ứng dụng ở `out/` và thư viện ở `lib/example.jar`:

**Bash (Linux/macOS):**

~~~bash
java -cp "out:lib/example.jar" demo.Welcome
~~~

**PowerShell (Windows):**

~~~powershell
java -cp "out;lib/example.jar" demo.Welcome
~~~

Dấu phân cách các entry của classpath là **`:` trên Linux/macOS** và **`;` trên Windows**; việc dùng Bash hay PowerShell không tự quyết định dấu phân cách, **hệ điều hành chạy JVM** mới là yếu tố. Với Git Bash chạy Java Windows, thông thường vẫn dùng `;`.

Lưu ý: ví dụ `demo.Welcome` ở trên không cần thư viện `example.jar`; đoạn lệnh chỉ minh họa cú pháp nhiều classpath entry, nên chỉ sử dụng khi JAR đó thực sự tồn tại. Lệnh tối thiểu để chạy demo là `java -cp out demo.Welcome`.

Khi chỉ có một entry như `out` hoặc `.`, không cần dấu phân cách nào.

## 9. Các lỗi khi gõ java và cách chẩn đoán

| Thông báo / triệu chứng | Nguyên nhân khả dĩ | Cách kiểm tra |
|---|---|---|
| `Could not find or load main class Welcome` | Sai tên class, sai classpath hoặc chưa biên dịch | Xem `.class` ở đâu, kiểm tra tên `class` và `-cp` |
| `ClassNotFoundException: demo.Welcome` | Class loader không tìm thấy tên class cần nạp | Xem có `out/demo/Welcome.class` và `-cp out` hay không |
| `NoClassDefFoundError` kèm `wrong name` | Tên package trong bytecode không khớp vị trí/kiểu gọi | Kiểm tra khai báo `package`, classpath root và tên đầy đủ |
| `Main method not found` | Class có tồn tại nhưng không có entry point phù hợp | Kiểm tra chữ ký `public static void main(String[] args)` |
| `UnsupportedClassVersionError` | JVM quá cũ để đọc class file đã biên dịch | So sánh `java -version`, mức `--release` và JDK biên dịch |
| Chạy ra kết quả cũ dù đã sửa `.java` | Đang chạy `.class` cũ | Lưu source, chạy lại `javac`, kiểm tra thư mục output |
| `javac` báo tên public class không khớp tên tệp | Tên tệp nguồn sai | Đổi đúng `Welcome.java` cho `public class Welcome` |
| `java` không được nhận diện | JDK/runtime hoặc `PATH` chưa đúng | Kiểm tra cài JDK, cấu hình `PATH`, mở terminal mới |

**Quy trình kiểm tra nhanh khi chạy class mode thất bại:**

1. Đang đứng ở thư mục nào? Bash: `pwd`; PowerShell: `Get-Location`.
2. `javac` đã chạy thành công chưa? Nếu lỗi biên dịch, có thể vẫn còn file `.class` cũ.
3. Class thực tế khai báo `package` nào và `class` nào?
4. Tệp `.class` nằm dưới thư mục classpath nào?
5. Bạn đã gọi **`java -cp <root> package.ClassName`** chưa?
6. Nếu class có nhưng không launch được, kiểm tra chữ ký `main` và `java -version`.

Tránh sửa ngẫu nhiên tên file, package và classpath cùng lúc; hãy xác minh từng điều kiện để biết nguyên nhân thật sự.

## 10. Dùng javap để xác nhận class đã được biên dịch

JDK có `javap` để xem thông tin class file mà không chạy chương trình. Với ví dụ package `demo`:

~~~bash
javap -classpath out demo.Welcome
~~~

Bạn sẽ thấy thông tin của `demo.Welcome`, bao gồm phương thức `public static void main(java.lang.String[])`. Để xem bytecode:

~~~bash
javap -classpath out -c demo.Welcome
~~~

`javap` giúp trả lời “JVM có thể tìm thấy class tên nào, method nào được biên dịch?” nhưng **không thay cho việc thực thi hoặc kiểm thử**. Đầu ra cụ thể thay đổi theo compiler/JDK.

Bạn cũng có thể so sánh sau khi sửa mã nguồn: nếu chưa chạy lại `javac`, `javap` tiếp tục mô tả `.class` cũ ở classpath ấy.

## 11. Các trường hợp biên cần nhớ

**Tên file và tên public class khác nhau.** `public class SalesApp` trong `Program.java` gây lỗi compile. Còn tệp `Program.java` chỉ có `class SalesApp` không public có thể biên dịch thành `SalesApp.class`; lúc chạy dùng `java SalesApp`.

**Package khác thư mục hiện tại.** Với `demo.Welcome`, tên class phải viết đầy đủ khi launch, ngay cả khi bạn đang ở gần tệp `Welcome.class`. Đổi thư mục hiện tại không biến tên đầy đủ của class thành `Welcome`.

**Nhiều class trong cùng một source.** Không có quan hệ “một source luôn tạo một class file”. Nested class còn có cách đặt tên bytecode riêng; hãy để compiler và launcher xử lý thay vì đổi tên `.class` thủ công.

**Tham số ứng dụng đứng sau class name.** `java -cp out demo.Welcome Lan` chuyển `Lan` vào `args`; còn tùy chọn như `-cp out` thuộc về launcher. Đặt sai vị trí có thể khiến chuỗi bị hiểu thành đối số hoặc tên class.

**JVM khác JDK biên dịch.** Nếu máy có nhiều phiên bản Java, `javac -version` và `java -version` có thể khác. Đúng tên class mà sai phiên bản runtime vẫn có thể bị `UnsupportedClassVersionError`.

**Đường dẫn chứa dấu cách.** Bao đường dẫn file/thư mục trong dấu nháy phù hợp của shell:

~~~bash
javac -d "build output" "src/demo/Welcome.java"
java -cp "build output" demo.Welcome
~~~

Trước khi dùng `-d`, hãy tạo thư mục output phù hợp theo môi trường của bạn. Dấu nháy bảo vệ đường dẫn, **không biến tên class thành đường dẫn**.

**Chỉ đổi nội dung source.** Trong class mode, JVM không tự biên dịch lại source bị sửa. Nếu muốn chạy source mới, hãy `javac` lại trước; source-file mode là chế độ khác.

## 12. Bài tập tự kiểm tra có đáp án

### Bài A — chọn đúng lệnh chạy

Có tệp `Hello.java`:

~~~java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
~~~

Bạn đã chạy `javac Hello.java` thành công trong thư mục hiện hành. Lệnh nào chạy **class đã biên dịch**?

A. `java Hello.java`  
B. `java Hello`  
C. `java Hello.class`

**Đáp án:** B. A yêu cầu source-file mode trên runtime hỗ trợ; C dùng đuôi file sai trong class mode.

### Bài B — dự đoán sản phẩm của javac

Tệp nguồn tên `Tools.java`:

~~~java
class Util {
    static int twice(int x) { return x * 2; }
}
class Program {
    public static void main(String[] args) {
        System.out.println(Util.twice(4));
    }
}
~~~

**Câu hỏi:** tạo những class file nào và chạy bằng gì?

**Đáp án:** khi biên dịch thành công, tạo `Util.class` và `Program.class`; chạy `java -cp . Program`, kết quả `8`. Tệp `Tools.java` không tạo class `Tools` vì không có khai báo `class Tools`.

### Bài C — phát hiện classpath root sai

Đã biên dịch `package school;` và `public class Main` thành `out/school/Main.class`. Lệnh nào đúng?

~~~bash
java -cp out school.Main
~~~

**Đáp án:** chính lệnh trên. Không dùng `-cp out/school` hoặc `java -cp out Main`.

### Bài D — truyền tham số đúng vị trí

Từ chương trình `Welcome`, muốn `args[0]` là `Binh`. Hãy viết lệnh.

**Đáp án:** `java -cp . Welcome Binh` với class ở default package và `Welcome.class` trong thư mục hiện tại.

### Bài E — source đã sửa nhưng output chưa thay đổi

Bạn sửa `System.out.println("A");` thành `System.out.println("B");` trong `Welcome.java`, rồi chạy lại `java Welcome` nhưng vẫn thấy `A`.

**Đáp án:** khả năng cao bạn đang chạy `Welcome.class` cũ hoặc một class cùng tên ở classpath khác. Hãy lưu source, chạy lại `javac Welcome.java`, dùng classpath tường minh và xác minh file output.

### Bài F — chẩn đoán “Main method not found”

Tệp `MathTool.java` có:

~~~java
public class MathTool {
    public static int square(int n) {
        return n * n;
    }
}
~~~

**Hỏi:** vì sao `javac MathTool.java` được mà `java MathTool` thất bại?

**Đáp án:** khai báo class và method `square` hợp lệ, nhưng class không có điểm vào `main` phù hợp với chế độ launch Java 17. Thêm `public static void main(String[] args)` để gọi `square` hoặc viết một class `Runner` có main rồi gọi `MathTool.square(3)`.

## 13. Checklist nghiệm thu

- [ ] Biết `javac` nhận **tệp mã nguồn**, còn `java` trong class mode nhận **tên lớp**.
- [ ] Chạy được `javac Welcome.java` và `java -cp . Welcome`.
- [ ] Hiểu quy tắc tên tệp khi class được khai báo `public`.
- [ ] Giải thích được một source có thể biên dịch ra nhiều tệp `.class`.
- [ ] Với `package demo;`, chạy đúng bằng `java -cp out demo.Welcome`.
- [ ] Biết đặt classpath ở **thư mục gốc chứa package**, không ở thư mục con package.
- [ ] Phân biệt `java Welcome` với `java Welcome.java` và `java -jar app.jar`.
- [ ] Xác định được class nào có entry point `main` hợp lệ trong Java 17.
- [ ] Đọc được lỗi class not found, main not found và class file version mismatch.
- [ ] Biết cách xem class đã biên dịch bằng `javap` và kiểm tra hai phiên bản `javac`/`java`.

**Kết luận:** Hãy xác định **class và package trong mã**, **nơi bytecode được biên dịch ra**, rồi mới viết lệnh `java`. Khi chạy class đã biên dịch, dùng `java -cp <classpath-root> package.ClassName` (hoặc chỉ `ClassName` nếu không có package). **Tên tệp `.java` là đầu vào của trình biên dịch; tên class là đối tượng mà launcher cần tìm để chạy.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tools-and-execution-004">Thư mục làm việc, thư mục đầu ra và classpath</a>

<details>
<summary>Xem chi tiết</summary>

# Thư mục làm việc, thư mục đầu ra và classpath trong Java

> **Module:** Java core — language-basics  
> **Chủ đề:** Công cụ và thực thi  
> **Mục tiêu:** Phân biệt thư mục làm việc của tiến trình, thư mục chứa kết quả biên dịch và classpath. Tự biên dịch/chạy một project Java có `package`, nhiều tệp `.java`, tệp dữ liệu và classpath resource; giải thích vì sao JVM tìm được class nhưng chương trình không tìm được file, hoặc ngược lại.

## 1. Ba loại đường dẫn cần phân biệt

Một chương trình Java có thể vừa nạp bytecode `.class`, vừa đọc file trên ổ đĩa. Đó là **hai cơ chế tìm kiếm khác nhau**, thêm một cơ chế thứ ba khi compiler tạo kết quả:

| Khái niệm | Tác dụng | Ví dụ |
| --- | --- | --- |
| **Working directory** (thư mục làm việc, CWD) | Nơi các đường dẫn file **tương đối** được giải quyết khi tiến trình chạy | `Path.of("data", "customer.txt")` đọc từ thư mục `data` dưới CWD |
| **Output directory** (thư mục đầu ra biên dịch) | Nơi `javac -d` ghi các file `.class` theo cây package | `javac -d out ...` ghi `out/demo/app/Main.class` |
| **Classpath** (đường tìm lớp/tài nguyên) | Danh sách các thư mục hoặc JAR dùng để tìm class và resource trên classpath | `java -cp 'out:resources' demo.app.Main` trên Linux/macOS |

Dùng `javac -d out` **không** làm chương trình tự chạy từ `out`, cũng **không** tự thêm `out` vào classpath của một lệnh `java` khác. Dùng `java -cp ...` **không** chuyển working directory sang bất kỳ thư mục nào được liệt kê.

~~~mermaid
flowchart TD
    A["src/main/java: mã nguồn"] --> B["javac -d out"]
    B --> C["out: bytecode .class"]
    C --> D["Classpath: out + resources"]
    R["resources: file bên trong classpath"] --> D
    D --> J["JVM: nạp class, resource"]
    W["Working directory: path-lab"] --> F["data/customer.txt: file tương đối"]
    J --> F
    J --> O["Kết quả chạy"]
~~~

**Mẹo nhớ:** `-d` trả lời “compiler **ghi** ở đâu?”, `-cp` trả lời “JVM/compiler **tìm class** ở đâu?”, còn CWD trả lời “đường dẫn file **tương đối** bắt đầu ở đâu?”.

## 2. Project mẫu và cây thư mục

Ta tạo thư mục `path-lab` chứa mã Java 11+ không dùng dependency ngoài:

~~~text
path-lab/
├── src/
│   └── main/
│       └── java/
│           └── demo/
│               ├── app/
│               │   └── Main.java
│               └── lib/
│                   └── GreetingFormatter.java
├── data/
│   └── customer.txt
└── resources/
    └── messages/
        └── greeting.txt
~~~

Khi biên dịch, chương trình sẽ tạo thêm thư mục `out`. Cây `src/main/java` là một **quy ước tổ chức**; từ khóa `package` trong mỗi tệp mới quyết định tên đầy đủ của class và cách Java sắp xếp output theo `-d`.

Hai tệp văn bản cần tạo trước:

**`data/customer.txt`**

~~~text
Lan
~~~

**`resources/messages/greeting.txt`**

~~~text
Xin chào
~~~

Lưu với UTF-8. Chúng cùng là file văn bản nhưng **cách chương trình tìm chúng khác nhau**: `customer.txt` dựa trên working directory, còn `greeting.txt` sẽ được tìm qua classpath.

## 3. Viết mã Java cho project

### 3.1. GreetingFormatter.java

Tệp `src/main/java/demo/lib/GreetingFormatter.java`:

~~~java
package demo.lib;

public final class GreetingFormatter {
    private GreetingFormatter() { }

    public static String format(String greeting, String customer) {
        if (greeting == null || greeting.isBlank()
                || customer == null || customer.isBlank()) {
            throw new IllegalArgumentException("Missing text");
        }
        return greeting + ", " + customer + "!";
    }
}
~~~

Lớp có tên đầy đủ `demo.lib.GreetingFormatter`. `package demo.lib;` không phải một phần của đường dẫn Java command; JVM sẽ dùng tên package để tìm file class bên dưới mỗi **classpath root**.

### 3.2. Main.java

Tệp `src/main/java/demo/app/Main.java`:

~~~java
package demo.app;

import demo.lib.GreetingFormatter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private static String readGreeting() throws IOException {
        try (InputStream in =
                Main.class.getResourceAsStream("/messages/greeting.txt")) {
            if (in == null) {
                throw new IllegalStateException(
                        "Missing classpath resource: /messages/greeting.txt");
            }
            return new String(
                    in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }

    public static void main(String[] args) throws IOException {
        Path workingDir = Path.of("").toAbsolutePath().normalize();
        System.out.println("Working directory: " + workingDir);

        // File ngoài: tương đối theo working directory.
        Path customerFile = Path.of("data", "customer.txt");
        String customer = Files.readString(
                customerFile, StandardCharsets.UTF_8).strip();

        // Resource: tìm theo classpath, không theo CWD.
        String greeting = readGreeting();
        System.out.println("Customer file: "
                + customerFile.toAbsolutePath().normalize());
        System.out.println(GreetingFormatter.format(greeting, customer));
    }
}
~~~

Đọc kỹ sự khác nhau:

- `Files.readString(Path.of("data", "customer.txt"), UTF_8)` mở file tương đối với working directory. Nếu chưa có file, Java có thể ném `NoSuchFileException`.
- `Main.class.getResourceAsStream("/messages/greeting.txt")` tìm resource từ **gốc đường tìm resource** cho class này. Ở ví dụ **classpath truyền thống**, đó là entry `resources` hoặc resource đã đóng gói trong JAR. Khi không tìm được, API trả `null` và chương trình chủ động ném `IllegalStateException`.
- `import demo.lib.GreetingFormatter;` có nghĩa chương trình sử dụng một class khác; class này phải sẵn có khi biên dịch và khi runtime nạp nó.

Tệp `Main.java` dùng `Path.of`, `Files.readString`, `String.strip` và `InputStream.readAllBytes` nên yêu cầu **Java 11 trở lên**. Đây là một project **console** giản lược để quan sát đường dẫn, không phải cấu hình Maven/Gradle hoàn chỉnh.

## 4. Biên dịch và chạy bằng Bash trên macOS/Linux

Từ **thư mục cha** của `path-lab`:

~~~bash
cd path-lab
mkdir -p out

javac -encoding UTF-8 -d out \
  src/main/java/demo/lib/GreetingFormatter.java \
  src/main/java/demo/app/Main.java

java -cp 'out:resources' demo.app.Main
~~~

`mkdir -p out` tạo thư mục gốc cho output. Khi dùng `javac -d out`, hãy tạo sẵn `out`; compiler sẽ tạo thư mục package con trong đó. Sau khi biên dịch thành công, bạn sẽ có:

~~~text
out/
└── demo/
    ├── app/
    │   └── Main.class
    └── lib/
        └── GreetingFormatter.class
~~~

`-cp 'out:resources'` cung cấp **hai** classpath entries: `out` để tìm hai class và `resources` để tìm `messages/greeting.txt`. **Dấu hai chấm** là dấu phân tách classpath trên macOS/Linux.

Kết quả ví dụ:

~~~text
Working directory: <đường dẫn tuyệt đối tới path-lab>
Customer file: <đường dẫn tuyệt đối tới path-lab/data/customer.txt>
Xin chào, Lan!
~~~

Hai đường dẫn phụ thuộc vị trí thật trên máy; dòng cuối phải là `Xin chào, Lan!`.

**Không chạy `java Main`** khi class có `package demo.app;`. Tên lớp dùng để chạy là `demo.app.Main`, không có đuôi `.java` hoặc `.class`.

## 5. Biên dịch và chạy trên Windows PowerShell

Từ thư mục cha của `path-lab`:

~~~powershell
Set-Location path-lab
New-Item -ItemType Directory -Force -Path out | Out-Null
javac -encoding UTF-8 -d out src/main/java/demo/lib/GreetingFormatter.java src/main/java/demo/app/Main.java
java -cp 'out;resources' demo.app.Main
~~~

Điểm khác biệt: **Windows dùng dấu chấm phẩy** để phân tách các entry classpath, vì vậy `out;resources`, không phải `out:resources`. Trong PowerShell, đặt toàn bộ chuỗi classpath trong dấu nháy đơn để shell truyền nó nguyên vẹn thành **một đối số**.

Bạn có thể dùng dấu gạch chéo xuôi trong đường dẫn truyền cho các công cụ Java trên Windows như ví dụ, hoặc dùng dạng đường dẫn Windows tương ứng. Tránh nhầm dấu **phân tách path trong hệ điều hành** với dấu **phân tách nhiều phần tử của classpath**.

Nếu thư mục có khoảng trắng như `C:\Java Projects\path-lab`, cần quote đường dẫn nguồn/đích chứa khoảng trắng. Ví dụ `java -cp 'C:\Java Projects\path-lab\out;C:\Java Projects\path-lab\resources' demo.app.Main` là classpath Windows gồm hai entry; khi dùng đường dẫn tuyệt đối, working directory **vẫn không tự thay đổi**.

## 6. Thử nghiệm: lỗi nằm ở classpath hay working directory?

### Trường hợp A: class có đủ, nhưng thiếu resource

Từ đúng thư mục `path-lab`, chạy:

~~~bash
java -cp out demo.app.Main
~~~

- Java nạp `Main.class` và `GreetingFormatter.class` trong `out` thành công.
- File `data/customer.txt` vẫn đọc được vì working directory đúng.
- Nhưng `messages/greeting.txt` không có trong `out`, nên `getResourceAsStream` trả `null` và chương trình báo thiếu classpath resource.

**Cách sửa:** thêm `resources` vào `-cp`, hoặc đóng gói resource vào JAR đúng đường dẫn. Không nên chỉ đổi CWD với hy vọng JVM tự tìm classpath resource.

### Trường hợp B: có resource nhưng thiếu class

~~~bash
java -cp resources demo.app.Main
~~~

JVM chỉ tìm trong `resources`, không thấy `demo/app/Main.class`. Chương trình thất bại ở bước **nạp main class**, chưa chạy logic đọc file. Thêm `out` vào classpath để JVM tìm được class.

### Trường hợp C: classpath đúng nhưng CWD sai

Giả sử bạn đứng ở thư mục **cha** của `path-lab`. Nếu đưa classpath hợp lệ từ đó, ví dụ macOS/Linux:

~~~bash
java -cp 'path-lab/out:path-lab/resources' demo.app.Main
~~~

JVM có thể tìm được class và resource. Nhưng `Path.of("data", "customer.txt")` lúc này trỏ tới `data/customer.txt` **bên dưới thư mục cha**, không phải `path-lab/data/customer.txt`. Chương trình sẽ báo thiếu file nếu file ấy không tồn tại ở vị trí đang tìm.

Có hai hướng giải quyết hợp lý: **chạy từ working directory đúng** hoặc **truyền đường dẫn file cấu hình rõ ràng** (tuyệt đối hay tương đối, được xử lý theo hợp đồng). Không nên viết cứng đường dẫn tuyệt đối tới máy của một lập trình viên vào mã nguồn dùng chung.

## 7. Classpath root và cấu trúc package

Lớp trong ví dụ khai báo:

~~~java
package demo.app;

public final class Main {
    public static void main(String[] args) { }
}
~~~

Khi output đặt tại `out`, bytecode nằm ở `out/demo/app/Main.class`. Vì vậy:

| Lệnh | Đánh giá | Lý do |
| --- | --- | --- |
| `java -cp out demo.app.Main` | Tìm được main class | Classpath root là `out`, Java tự ánh xạ `demo.app.Main` thành `demo/app/Main.class` |
| `java -cp out/demo/app Main` | Sai cho lớp ví dụ | `Main` không nằm ở default package; trong bytecode tên lớp vẫn là `demo.app.Main` |
| `java -cp resources demo.app.Main` | Thiếu classpath cho code | `resources` không chứa bytecode của main class |

Tên **package** do khai báo `package` quyết định, không do thư mục hiện tại của terminal tự sinh ra. Nên đặt file nguồn theo cây package vì nó giúp code và build tool dễ hiểu; `javac -d` đặt output theo đúng tên package.

## 8. `javac -cp` khác `java -cp` thế nào?

Cùng là tùy chọn thiết lập đường tìm class, nhưng **thời điểm** khác:

- `javac -cp ...` cho **compiler** biết nơi tìm những class đã biên dịch hoặc JAR cần để giải quyết import/type.
- `java -cp ...` cho **runtime** biết nơi tìm main class, các class phụ thuộc và các resource trên classpath.

Trong ví dụ ban đầu, ta đưa **cả hai tệp nguồn** cho `javac` cùng một lần nên compiler có thể biên dịch chúng cùng nhau. Nếu biên dịch từng file riêng:

~~~bash
# Thực hiện từ path-lab, sau khi out đã tồn tại.
javac -encoding UTF-8 -d out src/main/java/demo/lib/GreetingFormatter.java
javac -encoding UTF-8 -cp out -d out src/main/java/demo/app/Main.java
~~~

Lượt biên dịch thứ hai cần thấy `GreetingFormatter.class` trong `out`. Nhưng sau khi build, lệnh chạy vẫn cần **`out` và `resources`** để chương trình hoạt động đúng.

Nếu có thư viện ngoài `lib/helper.jar`, trong project thực tế, compiler và runtime có thể cần thêm JAR đó trên classpath:

~~~bash
# Minh họa cú pháp trên macOS/Linux; thay tên nguồn và lớp cho dự án thật.
javac -cp 'lib/helper.jar' -d out YourFile.java
java -cp 'out:resources:lib/helper.jar' your.package.Main
~~~

Trên Windows, dùng `;` thay cho `:` khi phân tách nhiều phần tử classpath. Dòng này **không phải lệnh chạy project mẫu** vì project mẫu không có `lib/helper.jar` hay `YourFile.java`.

Khi chỉ định `-cp` tường minh, đừng mặc định Java sẽ tự thêm `out`, `resources`, mọi JAR đang có hay thư mục hiện tại. `CLASSPATH` là biến môi trường tùy chọn, nhưng cấu hình bằng `-cp` thường rõ hơn cho từng lệnh.

## 9. Đóng gói class và resource vào JAR

Sau khi biên dịch, hãy tạo JAR có entry point và resource:

**Bash (macOS/Linux):**

~~~bash
jar --create --file demo.jar \
  --main-class demo.app.Main \
  -C out . -C resources .
java -jar demo.jar
~~~

**PowerShell (Windows):**

~~~powershell
jar --create --file demo.jar --main-class demo.app.Main -C out . -C resources .
java -jar demo.jar
~~~

Tùy chọn `-C out .` đưa các file class nằm trong `out` vào gốc JAR, giữ cây package. Tùy chọn `-C resources .` đưa `messages/greeting.txt` vào đúng đường dẫn ở bên trong JAR. `--main-class demo.app.Main` ghi thông tin main class vào manifest của JAR.

Khi chạy `java -jar`, JVM dùng main class từ manifest. **Đừng giả định** `-cp` đặt cạnh `-jar` sẽ tự thêm mọi thư viện ngoài vào đường tìm class; với ứng dụng nhiều dependency cần đóng gói hoặc khai báo chúng theo cách phù hợp.

**Điểm mấu chốt:** `data/customer.txt` **vẫn là file bên ngoài**. Lệnh `java -jar demo.jar` từ `path-lab` vẫn đọc được file đó; chạy JAR từ một working directory khác có thể không đọc được, dù class và greeting resource đã nằm trong JAR.

## 10. Bảng chẩn đoán lỗi thường gặp

| Hiện tượng | Nguyên nhân có thể | Hướng khắc phục |
| --- | --- | --- |
| `javac -d out` không thể ghi class | `out` chưa tồn tại hoặc không có quyền ghi | Tạo `out` trước, kiểm tra quyền |
| `Could not find or load main class` | `-cp` thiếu output root hoặc tên package sai | Kiểm tra `out/demo/app/Main.class` và chạy `demo.app.Main` |
| `NoClassDefFoundError` | Thiếu class phụ thuộc lúc chạy, hoặc lỗi nạp/khởi tạo khác | Xem classpath, dependency và phần `Caused by` |
| `package demo.lib does not exist` khi compile | Compiler không thấy mã hay class của thư viện nội bộ | Biên dịch cả hai nguồn hoặc thêm `-cp out` ở lượt sau |
| `NoSuchFileException: data/customer.txt` | Sai working directory hoặc chưa có file | Xem CWD và đường dẫn thực tế, không chỉ classpath |
| `Missing classpath resource` | Thiếu `resources` trên classpath/JAR, sai tên resource | Kiểm tra root classpath và `messages/greeting.txt` |
| Chạy được trong IDE nhưng lỗi trên terminal | IDE đặt working directory, output hoặc classpath khác | Kiểm tra Run Configuration rồi so sánh ba giá trị |
| `UnsupportedClassVersionError` | Bytecode build cho Java mới hơn runtime hỗ trợ | Kiểm tra JDK build, runtime và target phù hợp |

**Phân biệt lỗi ở ba tầng:** `javac` không tìm được type là lỗi **biên dịch**; `java` không tìm được main class là lỗi **nạp chương trình**; `Files.readString` không mở được file là lỗi **I/O lúc chạy**. Ba lỗi này cần ba hướng kiểm tra khác nhau.

### Trường hợp biên đáng lưu ý

- **Có khoảng trắng trong đường dẫn:** quote classpath và đối số đường dẫn. Trên Bash, dấu `;` có ý nghĩa đặc biệt đối với shell.
- **Phân biệt hoa/thường:** các hệ điều hành có thể có quy tắc phân biệt chữ khác nhau; tên `Main.class`, package, resource phải chính xác.
- **Đường dẫn tương đối trong IDE:** IDE có thể mặc định working directory là project root, module root hoặc thư mục cấu hình khác. Không mặc định giống terminal.
- **Thư mục runtime khác với thư mục JDK:** `JAVA_HOME`, `java.home`, CWD và classpath là các khái niệm độc lập.
- **Classpath khác module path:** bài này dùng classpath và project **không có `module-info.java`**. Ứng dụng theo Java Platform Module System có quy tắc nạp/module path riêng.

## 11. Quy trình kiểm tra nhanh khi project không chạy

1. **Xem CWD:** Bash dùng `pwd`, PowerShell dùng `Get-Location`. Khi chạy Java, chương trình có thể in `Path.of("").toAbsolutePath()` để hỗ trợ chẩn đoán.
2. **Xem nơi xuất bytecode:** file `.class` có dưới thư mục đã truyền cho `-d` không, và cây package có đúng không?
3. **Xem runtime classpath:** entry đầu tiên chứa gốc package của main class không? Đã thêm resource/JAR cần thiết chưa?
4. **Kiểm tra file dữ liệu:** `data/customer.txt` có tồn tại **tương đối với CWD** không?
5. **Đọc nguyên thông báo lỗi:** lỗi biên dịch, lỗi load class hay lỗi mở file là ba tầng khác nhau.
6. **Thay một yếu tố mỗi lượt:** không đổi cả classpath, CWD và package đồng thời rồi đoán nguyên nhân.

## 12. Bài tập có đáp án định hướng

**Bài 1 — Class có đủ nhưng resource thiếu.** Dự đoán điều gì xảy ra khi chạy `java -cp out demo.app.Main` từ `path-lab`.

*Gợi ý:* Nạp được các file `.class` và đọc được `data/customer.txt`, nhưng thiếu `resources` để tìm `/messages/greeting.txt`.

**Bài 2 — Classpath đúng nhưng file không thấy.** Chạy từ thư mục cha với `-cp 'path-lab/out:path-lab/resources'` trên macOS/Linux. Tại sao `Files.readString` có thể thất bại?

*Gợi ý:* CWD là thư mục cha; đường dẫn tương đối sẽ trỏ tới `data` dưới thư mục cha, không phải dưới `path-lab`.

**Bài 3 — Thử compile hai lượt.** Biên dịch `GreetingFormatter.java` trước, sau đó `Main.java`. Lượt hai cần thêm tùy chọn nào để compiler tìm class đã tạo?

*Gợi ý:* Dùng `javac -cp out -d out ...`.

**Bài 4 — Đóng gói JAR.** Tạo `demo.jar` chứa hai class và `messages/greeting.txt`. Chạy từ project root và từ một thư mục khác. Phần nào đã nằm trong JAR, phần nào vẫn phụ thuộc CWD?

**Bài 5 — Cải thiện thiết kế.** Cho chương trình nhận đường dẫn `customer.txt` qua `args[0]` thay vì cố định `data/customer.txt`. Kiểm tra trường hợp thiếu đối số, file không tồn tại và đường dẫn tương đối. Thảo luận khi nào nên dùng đường dẫn tuyệt đối.

### Tự đánh giá

Bạn hiểu bài khi tự trả lời được: `javac -d` tạo `.class` ở đâu; `java -cp` tìm `demo.app.Main` và resource từ đâu; `Path.of("data","customer.txt")` phụ thuộc thư mục nào; tại sao Java package cần tên FQCN khi chạy; và vì sao đóng gói JAR **không tự biến file bên ngoài thành resource bên trong JAR**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tools-and-execution-005">Chạy chương trình một tệp nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# Chạy chương trình một tệp nguồn trong Java

Bạn vừa viết một chương trình Java nhỏ trong tệp `.java` và muốn xem ngay kết quả, nhưng chưa cần tạo thư mục `.class` hoặc cấu hình dự án. Từ **JDK 11**, Java hỗ trợ **source-file mode**: dùng lệnh `java` trỏ trực tiếp tới tệp nguồn. Trên **JDK 17 trở lên**, có thể chạy:

~~~bash
java QuickTotal.java 3 125000
~~~

Java launcher sẽ biên dịch mã nguồn trong bộ nhớ và chạy chương trình. Với cách chạy này, bạn **không phải gọi `javac` trước** và không có tệp `QuickTotal.class` được tạo trên đĩa chỉ bởi lệnh trên.

Bài học này hướng dẫn cách dùng chế độ đó, những tình huống phù hợp và điểm khác với quy trình biên dịch truyền thống.

## 1. Hai cách chạy một chương trình Java

Giả sử bạn có một tệp `QuickTotal.java` khai báo lớp `QuickTotal` với phương thức `main`.

| Cách | Lệnh | Tệp `.class` trên đĩa? | Thích hợp khi nào? |
| --- | --- | --- | --- |
| **Chạy trực tiếp nguồn** | `java QuickTotal.java` | Không tạo bởi lần chạy source-file mode | Thử nhanh một chương trình nguồn nhỏ |
| **Biên dịch rồi chạy** | `javac QuickTotal.java` rồi `java QuickTotal` | Có, sau khi biên dịch | Muốn kiểm soát sản phẩm build, triển khai hoặc dùng trong dự án lớn hơn |

Điểm quan trọng là **lệnh `java` có hai cách hiểu khác nhau**:

- `java QuickTotal.java`: đối số đầu là **đường dẫn tệp nguồn**; launcher dùng source-file mode.
- `java QuickTotal`: đối số đầu là **tên lớp**; JVM phải tìm bytecode của lớp qua classpath/module path thích hợp.

Không được thay `QuickTotal.java` bằng `QuickTotal.class` trong lệnh chạy lớp. Tên file nguồn và tên lớp là hai khái niệm khác nhau.

### Bên trong source-file mode diễn ra gì?

~~~mermaid
flowchart TD
    A["QuickTotal.java"] --> B["Lệnh java QuickTotal.java"]
    B --> C["Biên dịch mã nguồn trong bộ nhớ"]
    C --> D{"Mã có lỗi biên dịch?"}
    D -->|Có| E["In chẩn đoán và không chạy main"]
    D -->|Không| F["Khởi động main trong JVM"]
    F --> G["In kết quả hoặc báo lỗi lúc chạy"]
~~~

Vì việc biên dịch diễn ra khi chạy, lỗi cú pháp hay lỗi kiểu dữ liệu vẫn được phát hiện. Không tạo `.class` trên đĩa **không có nghĩa Java bỏ qua bước biên dịch**.

## 2. Kiểm tra JDK trước khi bắt đầu

Mở **PowerShell (Windows)** hoặc **Bash (Linux)**:

~~~bash
java -version
javac -version
~~~

Đầu ra cần cho biết bạn đang sử dụng JDK 17 trở lên (hoặc một phiên bản hỗ trợ tương ứng). Cách chạy nguồn trực tiếp bắt đầu được hỗ trợ từ JDK 11; ở đây ta dùng JDK 17+ để thống nhất môi trường học.

Nếu terminal báo không tìm thấy `java` hoặc `javac`:

1. Xác minh đã cài JDK và mở lại terminal sau khi cài hoặc đổi `PATH`.
2. **Windows PowerShell:** dùng `Get-Command java, javac` để xem chương trình được tìm thấy ở đâu.
3. **Linux:** dùng `command -v java` và `command -v javac`.
4. Khi nhiều JDK cùng tồn tại, cần bảo đảm lệnh `java` đang sử dụng phiên bản mong muốn.

Source-file mode chạy từ lệnh `java`, nhưng có JDK đầy đủ sẽ hữu ích khi bạn muốn so sánh với cách dùng `javac` ở phần sau.

## 3. Tạo tệp Java cho ví dụ

Mở một thư mục thực hành tùy chọn trong terminal. Có thể tạo thư mục bằng:

**PowerShell (Windows):**

~~~powershell
New-Item -ItemType Directory -Force -Path quick-java | Out-Null
Set-Location quick-java
~~~

**Bash (Linux):**

~~~bash
mkdir -p quick-java
cd quick-java
~~~

Dùng trình soạn thảo văn bản để tạo tệp chính xác tên **`QuickTotal.java`**, lưu dưới dạng văn bản UTF-8. Trên Windows, kiểm tra tệp không vô tình trở thành `QuickTotal.java.txt`.

Bài toán: nhận **số lượng hàng** và **đơn giá tính bằng VND nguyên** qua đối số dòng lệnh; in thành tiền. Số lượng phải lớn hơn 0, đơn giá không âm; khi phép nhân vượt miền `long`, phải báo lỗi thay vì âm thầm dùng kết quả tràn.

Nội dung hoàn chỉnh:

~~~java
public class QuickTotal {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println(
                    "Cach dung: java QuickTotal.java <soLuong> <donGiaVnd>");
            return;
        }

        try {
            int quantity = Integer.parseInt(args[0]);
            long unitPriceVnd = Long.parseLong(args[1]);

            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "So luong phai lon hon 0");
            }
            if (unitPriceVnd < 0) {
                throw new IllegalArgumentException(
                        "Don gia khong duoc am");
            }

            long totalVnd = Math.multiplyExact(
                    (long) quantity, unitPriceVnd);

            System.out.println("So luong: " + quantity);
            System.out.println("Don gia: " + unitPriceVnd + " VND");
            System.out.println("Thanh tien: " + totalVnd + " VND");
        } catch (NumberFormatException ex) {
            System.out.println("Loi: Dau vao phai la so nguyen hop le");
        } catch (IllegalArgumentException ex) {
            System.out.println("Loi: " + ex.getMessage());
        } catch (ArithmeticException ex) {
            System.out.println("Loi: Ket qua vuot mien long");
        }
    }
}
~~~

### Vì sao đoạn mã này hoạt động?

- `public class QuickTotal` định nghĩa lớp sẽ chạy. Ví dụ dùng tệp `QuickTotal.java` trùng tên lớp công khai để có thể chạy bằng **cả source-file mode lẫn `javac QuickTotal.java` truyền thống**. Riêng source-file mode không buộc tên tệp trùng tên lớp `public`: launcher chạy lớp top-level đầu tiên trong tệp, nên lớp đó phải có `main` phù hợp. Với biên dịch truyền thống bằng `javac`, lớp top-level `public` vẫn phải nằm trong tệp cùng tên.
- `main(String[] args)` là điểm bắt đầu; chuỗi sau tên tệp nguồn trở thành các phần tử của mảng `args`.
- `Integer.parseInt` đọc số lượng; `Long.parseLong` đọc đơn giá. Cả hai đều có thể ném `NumberFormatException` khi chuỗi sai định dạng hoặc ngoài miền số.
- `quantity <= 0` và `unitPriceVnd < 0` thực thi các quy tắc đầu vào đã xác định.
- `Math.multiplyExact` trả về tích dạng `long` và ném `ArithmeticException` nếu tràn `long`, giúp tránh kết quả sai âm thầm.
- `NumberFormatException` là lớp con của `IllegalArgumentException` nên phải được bắt trước nhánh `IllegalArgumentException` tổng quát.
- Phần mã nguồn sử dụng các chuỗi thông báo ASCII để kết quả nhất quán giữa terminal có cấu hình encoding khác nhau.

Đây là một tệp nguồn độc lập: không cần thư viện ngoài, file cấu hình build hoặc class thứ hai.

## 4. Chạy trực tiếp với `java TenTep.java`

Trong thư mục chứa `QuickTotal.java`, chạy:

~~~bash
java QuickTotal.java 3 125000
~~~

Kết quả dự kiến:

~~~text
So luong: 3
Don gia: 125000 VND
Thanh tien: 375000 VND
~~~

Diễn giải: `3 × 125000 = 375000`. Khi dùng source-file mode, bạn không phải tạo tệp `.class` trên đĩa và không cần chạy `javac` riêng.

Thử chạy lại với đầu vào khác:

~~~bash
java QuickTotal.java 2 45000
~~~

~~~text
So luong: 2
Don gia: 45000 VND
Thanh tien: 90000 VND
~~~

Nếu sửa nội dung `QuickTotal.java` rồi lưu tệp, lần gọi `java QuickTotal.java` tiếp theo sẽ dùng **mã nguồn mới**. Đây là một điểm thuận tiện khi thực hành chương trình nhỏ.

### Khi bạn đang ở thư mục cha

Nếu lưu tại `quick-java/QuickTotal.java` nhưng đứng ở thư mục cha của `quick-java`, dùng:

~~~bash
java quick-java/QuickTotal.java 3 125000
~~~

Windows và Linux đều chấp nhận cách truyền đường dẫn này qua Java launcher. Nếu đường dẫn thực tế có dấu cách, đặt đường dẫn trong dấu nháy kép:

~~~bash
java "quick-java/QuickTotal.java" 3 125000
~~~

Dấu nháy bao quanh **đường dẫn**, còn `3` và `125000` là hai đối số riêng của chương trình.

## 5. Phân biệt các dạng lệnh dễ nhầm

| Lệnh | Điều xảy ra |
| --- | --- |
| `java QuickTotal.java 3 125000` | Chạy trực tiếp tệp nguồn |
| `java --source 17 QuickTotal.java 3 125000` | Chạy nguồn và yêu cầu mức ngôn ngữ Java 17 |
| `javac QuickTotal.java` | Biên dịch thành `QuickTotal.class` trong thư mục hiện tại |
| `java QuickTotal 3 125000` | Chạy bytecode của lớp `QuickTotal` qua classpath hiện tại |
| `java QuickTotal.class` | Không phải cách gọi lớp đúng; launcher không lấy tên tệp `.class` làm tên lớp |
| `javac -d out QuickTotal.java` | Đặt `QuickTotal.class` vào thư mục `out` (cần có thư mục đích phù hợp) |
| `java -cp out QuickTotal 3 125000` | Chạy lớp đã biên dịch từ thư mục `out` |

Tùy chọn `--source 17` hữu ích khi bạn muốn yêu cầu chế độ ngôn ngữ cụ thể. Nếu JDK đang dùng hỗ trợ Java 17, ví dụ sau cũng hợp lệ:

~~~bash
java --source 17 QuickTotal.java 3 125000
~~~

Đừng nhầm `--source 17` với `javac --release 17`. Lệnh `javac --release` dành cho biên dịch tạo bytecode nhắm tới phiên bản Java được hỗ trợ và các API nền tảng của release đó; source-file mode trong bài này dùng launcher `java` để thực thi trực tiếp.

## 6. Kiểm tra đầu vào sai và trường hợp biên

Hãy thử từng lệnh; khi dữ liệu sai, chương trình in thông báo lỗi đã viết trong mã:

| Lệnh | Kết quả/điểm cần kiểm tra |
| --- | --- |
| `java QuickTotal.java 1 0` | Thành tiền 0 VND |
| `java QuickTotal.java 2 100` | Thành tiền 200 VND |
| `java QuickTotal.java 0 100` | Báo số lượng phải lớn hơn 0 |
| `java QuickTotal.java -2 100` | Báo số lượng không hợp lệ |
| `java QuickTotal.java 2 -1` | Báo đơn giá không được âm |
| `java QuickTotal.java abc 100` | Báo đầu vào không phải số nguyên hợp lệ |
| `java QuickTotal.java 2 10.5` | Báo lỗi vì phiên bản này yêu cầu VND nguyên |
| `java QuickTotal.java 3` | In hướng dẫn sử dụng vì thiếu đối số |
| `java QuickTotal.java 2 9223372036854775807` | Báo kết quả vượt miền `long` |
| `java QuickTotal.java 2147483648 1` | Báo lỗi số lượng vượt miền `int` |

**Hai điểm biên quan trọng:**

1. `quantity = 1` được chấp nhận nhưng `quantity = 0` bị từ chối — vì bài toán yêu cầu lớn hơn 0.
2. Kết quả đúng của phép nhân có thể vượt miền `long` dù mỗi đầu vào được parse thành công. Kiểm tra kiểu không loại bỏ nhu cầu phát hiện tràn khi chạy.

`javac` hoặc bước biên dịch trong bộ nhớ sẽ kiểm tra cú pháp và kiểu, nhưng **không tự quyết định** số lượng bằng 0 có hợp lệ hay không; đó là quy tắc bạn viết trong chương trình.

## 7. Lỗi hay gặp với chế độ chạy trực tiếp

### Lỗi 1: Không tìm thấy hoặc không đọc được tệp nguồn

Nếu chạy `java QuickTotal.java ...` nhưng đường dẫn không trỏ tới tệp nguồn tồn tại và có thể đọc được, launcher thất bại **trước khi thực thi `main`**. Thông báo phụ thuộc JDK và cách launcher phân tích đối số; không nên chép một mẫu lỗi về `main` để kết luận rằng tệp đã bị thiếu.

Hãy kiểm tra theo thứ tự:

- Thư mục làm việc hiện tại là đâu? Tệp `QuickTotal.java` có thực sự ở đó không?
- Nếu tệp nằm trong thư mục con, đã truyền đúng đường dẫn (ví dụ `java quick-java/QuickTotal.java 3 125000`) chưa?
- Có vô tình lưu thành `QuickTotal.java.txt`, sai chữ hoa/thường của đường dẫn hoặc không có quyền đọc không?
- Nếu nguồn được tìm thấy nhưng launcher báo **không có `main`**, hãy kiểm tra chữ ký `main` của **lớp top-level đầu tiên** trong tệp. Đó là lỗi điểm vào chương trình, **không phải bằng chứng tệp nguồn không tồn tại**.

**PowerShell:** `Get-Location` và `Get-ChildItem`. **Linux:** `pwd` và `ls`.

### Lỗi 2: Chạy tên lớp thay vì tệp nguồn

~~~bash
java QuickTotal
~~~

Lệnh trên yêu cầu tìm **lớp đã biên dịch**. Nếu bạn chỉ có `QuickTotal.java`, JVM có thể báo không tìm thấy lớp. Muốn chạy nguồn trực tiếp, dùng:

~~~bash
java QuickTotal.java
~~~

Chạy không đủ hai đối số vẫn biên dịch và vào `main`, nhưng ví dụ sẽ in dòng hướng dẫn sử dụng. Điều này khác với lỗi compiler.

### Lỗi 3: Lỗi cú pháp hoặc kiểu ở nguồn

Nếu vô tình sửa:

~~~java
int quantity = "ba"; // Không hợp lệ: String không gán trực tiếp cho int.
~~~

Source-file mode vẫn thực hiện bước biên dịch và báo lỗi. **`main` chưa được chạy**, vì mã chưa hợp lệ. Sau khi sửa `int quantity = 3;`, bạn có thể gọi lại lệnh `java QuickTotal.java ...` ngay.

### Lỗi 4: JVM cũ hoặc công cụ khác phiên bản

JDK 8 không hỗ trợ kiểu chạy `java File.java`. Khi đó cần nâng lên phiên bản được hỗ trợ (bài này chọn JDK 17+), hoặc dùng `javac` rồi chạy `java TenLop` theo quy trình truyền thống.

Kiểm tra `java -version` nếu gặp lỗi chế độ chạy tệp nguồn.

## 8. Khi nào nên dùng source-file mode?

**Nên dùng** khi:

- Thực hành ngôn ngữ Java bằng một tệp nhỏ.
- Viết ví dụ thuật toán, kiểm tra thư viện chuẩn hoặc thử nhanh một giả thuyết.
- Muốn sửa rồi chạy lại mà không cần quản lý tệp build đầu ra.

**Cân nhắc chuyển sang biên dịch/build truyền thống** khi:

- Chương trình có nhiều tệp nguồn và phụ thuộc cần quản lý rõ.
- Bạn muốn kiểm soát quá trình đóng gói, kiểm thử tự động hoặc phiên bản bytecode để triển khai.
- Cần lặp lại build trong CI, tích hợp JAR hoặc công cụ như Maven/Gradle.
- Muốn tái sử dụng các tệp `.class` đã biên dịch thay vì xử lý tệp nguồn mỗi lần chạy.

Cơ chế source-file mode có thể làm việc với các lớp/phụ thuộc đã có trong classpath và có những quy tắc chi tiết khi mã nguồn chứa nhiều lớp. Tuy nhiên, bài thực hành này chỉ sử dụng **một tệp nguồn tự đủ** để tránh phải cấu hình thêm.

## 9. Bài tập ngắn

**Bài 1.** Chạy `java QuickTotal.java 4 75000`. Dự đoán kết quả thành tiền trước khi chạy.

*Đáp án:* `300000 VND`.

**Bài 2.** Sửa dòng in `"Thanh tien:"` thành `"Tong tien:"`, lưu tệp rồi chạy nguồn trực tiếp. Có cần chạy `javac` riêng không?

*Đáp án:* Không. Khi thực thi `java QuickTotal.java`, launcher sẽ dùng mã nguồn đã lưu.

**Bài 3.** Chạy thử lần lượt `java QuickTotal.java 0 1`, `java QuickTotal.java 1 -1` và `java QuickTotal.java one 1`. Xác định ca nào thất bại khi **chuyển chuỗi thành số**, ca nào bị từ chối do **quy tắc bài toán**.

*Đáp án:* `one` không parse được; 0 và −1 parse được nhưng bị từ chối theo quy tắc nghiệp vụ.

**Bài 4.** Biên dịch truyền thống bằng `javac QuickTotal.java`, sau đó chạy `java QuickTotal 3 125000`. Giải thích vì sao câu lệnh `java` lần này **không có `.java`**.

*Đáp án:* JVM đang tìm tên lớp từ bytecode đã được `javac` tạo trên đĩa, không chạy trực tiếp tệp nguồn.

### Checklist hoàn thành

- [ ] `java -version` cho thấy phiên bản hỗ trợ source-file mode (bài dùng JDK 17+).
- [ ] Có tệp `QuickTotal.java` đúng tên và thư mục.
- [ ] Chạy được `java QuickTotal.java 3 125000` và nhận đúng `375000 VND`.
- [ ] Tự sửa và chạy lại mã nguồn mà không cần lệnh `javac` riêng.
- [ ] Phân biệt được lỗi biên dịch và lỗi dữ liệu lúc chạy.
- [ ] Biết khi nào chuyển sang `javac` / classpath / công cụ build.

## Ghi nhớ

**`java TenTep.java` chạy mã nguồn trực tiếp bằng cách biên dịch trong bộ nhớ rồi thực thi**, thích hợp để học và thử nhanh một chương trình Java nhỏ. **`javac TenTep.java` rồi `java TenLop`** là quy trình tạo và chạy bytecode trên đĩa. Học đúng hai cách sẽ giúp bạn hiểu mình đang chạy **tệp nguồn** hay **lớp đã biên dịch**, đồng thời xử lý lỗi chính xác hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tools-and-execution-006">Thử biểu thức, biến và phương thức với JShell</a>

<details>
<summary>Xem chi tiết</summary>

# Thử biểu thức, biến và phương thức với JShell

## 1. JShell dùng để làm gì?

**JShell** là công cụ REPL (*Read–Evaluate–Print Loop*) đi kèm **JDK từ Java 9**. Bạn nhập một đoạn Java nhỏ (*snippet*), JShell biên dịch/đánh giá đoạn đó, hiển thị kết quả hoặc thông báo lỗi, rồi cho phép nhập tiếp. Đây là cách nhanh để tự kiểm tra một phép tính, thử API, tạo biến và khai báo phương thức **mà chưa cần tạo một class có `main`**.

~~~text
Bạn nhập biểu thức / khai báo / lệnh JShell
                 |
                 v
       JShell xử lý snippet Java
                 |
        +--------+---------+
        |                  |
     Hợp lệ              Có lỗi
        |                  |
  Cập nhật trạng thái     In chẩn đoán
  và hiện kết quả         không nhận snippet lỗi
        |
     Nhập tiếp
~~~

Khác với terminal hệ điều hành, dấu nhắc **`jshell>`** là nơi nhập *mã Java và lệnh nội bộ JShell*. Các lệnh `javac`, `java`, `cd` hay `ls` là lệnh shell hệ điều hành, không phải biểu thức Java để gõ trực tiếp tại `jshell>`.

JShell rất phù hợp để học ngôn ngữ, khám phá API và dựng ví dụ nhỏ. Nó không thay thế việc viết chương trình có cấu trúc, bộ test tự động hoặc hệ thống build cho dự án thực tế.

## 2. Chuẩn bị JDK và mở JShell

Bài này dùng **JDK 17 trở lên**. Mở Bash, PowerShell hoặc terminal của IDE và gõ:

**Bash (Linux/macOS/Git Bash):**

~~~bash
java -version
javac -version
jshell --version
jshell
~~~

**PowerShell (Windows):**

~~~powershell
java -version
javac -version
jshell --version
jshell
~~~

Khi công cụ sẵn sàng, bạn thường thấy phần chào mừng và dấu nhắc gần giống:

~~~text
|  Welcome to JShell -- Version 17...
|  For an introduction type: /help intro

jshell>
~~~

Phiên bản và lời chào có thể khác theo bản JDK, tùy chọn và môi trường. Bạn **chỉ nhập phần sau `jshell>`**, không gõ lại dấu nhắc. Nếu hệ thống báo `jshell` không được nhận diện, kiểm tra bạn đã cài **JDK** và thư mục `bin` của JDK có trên `PATH`; việc chỉ có một runtime Java hoặc cấu hình `PATH` sai có thể làm thiếu công cụ này.

JShell nhận snippet ngay khi đoạn mã hoàn chỉnh. Với khai báo nhiều dòng hoặc block chưa đóng, nó sẽ tiếp tục chờ bạn hoàn thiện, thường bằng dấu nhắc nối dòng.

## 3. Thử biểu thức và quan sát kết quả

Nhập lần lượt vào JShell:

~~~java
2 + 3 * 4
(2 + 3) * 4
7 / 2
7.0 / 2
"Java".toUpperCase()
Math.max(10, 25)
~~~

Kết quả giá trị theo thứ tự là:

| Biểu thức | Giá trị | Vì sao? |
|---|---|---|
| `2 + 3 * 4` | `14` | Nhân có ưu tiên cao hơn cộng |
| `(2 + 3) * 4` | `20` | Ngoặc đổi cách nhóm |
| `7 / 2` | `3` | Hai toán hạng nguyên nên chia nguyên |
| `7.0 / 2` | `3.5` | Có toán hạng `double` |
| `"Java".toUpperCase()` | `"JAVA"` | Gọi phương thức `String` |
| `Math.max(10, 25)` | `25` | Trả số lớn hơn |

Ví dụ JShell có thể hiển thị kết quả của một biểu thức như:

~~~text
jshell> 2 + 3 * 4
$1 ==> 14
~~~

`$1` là **biến tạm tự sinh** cho kết quả snippet; ở phiên của bạn có thể là `$2`, `$3`... nếu đã nhập thêm lệnh khác. Vì vậy, khi hướng dẫn một phép tính cụ thể, hãy dựa vào **giá trị bên phải `==>`**, không lệ thuộc số thứ tự `$n`.

JShell cũng có thể nhận dấu `;` ở cuối các snippet thích hợp. Với biểu thức tương tác đơn giản, bỏ dấu chấm phẩy thường tiện hơn; khi viết chương trình Java trong tệp, hãy tuân thủ cú pháp đầy đủ của câu lệnh.

## 4. Tạo, đọc và cập nhật biến

Thử một giao dịch mua hàng rất nhỏ:

~~~java
int unitPrice = 120_000;
int quantity = 3;
int subtotal = unitPrice * quantity;
subtotal
quantity = 4;
subtotal
subtotal = unitPrice * quantity;
subtotal
~~~

Các giá trị đáng kiểm tra:

| Bước | Kết quả |
|---|---|
| Tạo `unitPrice` và `quantity` | 120.000 và 3 |
| Tính `subtotal` lần đầu | 360.000 |
| Gán lại `quantity = 4` | `quantity` là 4 |
| Đọc `subtotal` ngay sau đó | **Vẫn là 360.000** |
| Gán lại `subtotal = unitPrice * quantity` | **480.000** |

**Lưu ý:** biến `subtotal` giữ **giá trị đã tính tại thời điểm gán**, không phải một công thức bảng tính tự động cập nhật khi `quantity` thay đổi.

Bạn có thể hỏi JShell trạng thái hiện tại:

~~~text
/vars
~~~

Nó liệt kê các biến đã tạo trong phiên cùng kiểu/giá trị. Nếu nhập:

~~~java
var label = "Don hang";
label.length()
~~~

`var` suy ra kiểu `String` từ giá trị khởi tạo; Java vẫn kiểm tra kiểu tĩnh. `var` không có nghĩa biến được phép thay đổi sang một kiểu tùy ý về sau.

### Khi khai báo lại một tên

Trong JShell, bạn có thể thử:

~~~java
int level = 1;
level = 2;
level
~~~

Đây là **gán lại**, không phải khai báo mới. JShell còn hỗ trợ thay thế một snippet đã khai báo bằng snippet mới trong môi trường tương tác. Tuy nhiên, đừng nhầm quy tắc tiện dụng đó với việc có thể khai báo trùng hai biến cục bộ cùng tên trong một block Java bình thường.

## 5. Khai báo phương thức rồi gọi ngay

Một phương thức giúp đặt tên cho phép tính và thử cùng logic với nhiều đối số.

Nhập vào JShell:

~~~java
int square(int n) { return n * n; }
square(5)
square(-3)
~~~

Kết quả gọi lần lượt là `25` và `9`.

Tiếp theo:

~~~java
long lineTotal(long unitPrice, int quantity) {
    if (unitPrice < 0 || quantity < 0) {
        throw new IllegalArgumentException("Gia hoac so luong am");
    }
    return Math.multiplyExact(unitPrice, (long) quantity);
}
lineTotal(120_000L, 3)
lineTotal(120_000L, 4)
~~~

Giá trị lần lượt là `360000` và `480000`. JShell cho phép khai báo method tương tự thành viên của class, nhưng không yêu cầu bạn gõ cả `public class Demo { ... }` cho mỗi phép thử.

Kiểm tra danh sách method:

~~~text
/methods
~~~

### Biểu thức và lời gọi method khác nhau thế nào?

- `square(5)` là **biểu thức gọi method** có giá trị trả về `25`.
- `System.out.println(square(5));` là **câu lệnh gọi method** tạo hiệu ứng in ra `25`; `println` trả về `void`.
- `square` được định nghĩa trong trạng thái phiên JShell; bạn có thể gọi lại mà không khai báo lại ở mỗi dòng.

Một method cần phù hợp kiểu trả về: `int square(int n) { return "abc"; }` bị từ chối do `String` không thể trở thành `int` trong phép `return` này.

## 6. Thử nhanh điều kiện, chuỗi và null

Nhập lần lượt:

~~~java
int amount = 300_000;
boolean activeMember = true;
activeMember && amount >= 200_000
String customer = null;
customer != null && !customer.isBlank()
"Java".equals(customer)
~~~

Giá trị các biểu thức boolean lần lượt là **`true`**, **`false`**, **`false`**.

Phân tích:

- `&&` là toán tử ngắn mạch: khi `customer != null` là `false`, vế sau **không được đánh giá**, tránh gọi `isBlank()` trên `null`.
- `"Java".equals(customer)` an toàn với `customer == null`, vì đối tượng gọi method là chuỗi literal khác null.
- Với kiểu tham chiếu, `==` kiểm tra hai tham chiếu có cùng object hay không; nó không phải phép so sánh nội dung chuỗi chung cho mọi trường hợp.

Thử điều kiện ba ngôi:

~~~java
String category = amount >= 300_000 ? "LON" : "NHO";
category
~~~

Kết quả là `"LON"`. Khi cần nhiều nhánh hoặc xử lý phức tạp, viết method và dùng `if` sẽ dễ đọc hơn ghép nhiều toán tử ba ngôi lồng nhau.

## 7. Nhận diện lỗi từ phản hồi JShell

JShell thử biên dịch snippet và báo lỗi khi cú pháp, tên hoặc kiểu không hợp lệ:

| Thử trong JShell | Kết quả cần nhận ra |
|---|---|
| `int age = "hai muoi";` | Lỗi kiểu: không thể gán `String` vào `int` |
| `missing + 1` | Lỗi tên: biến `missing` chưa được định nghĩa |
| `square("5")` | Lỗi truyền đối số không phù hợp với `square(int)` |
| `7 / 0` | Phép chia nguyên cho 0 gây `ArithmeticException` **khi đánh giá** |
| `String s = null; s.length()` | `NullPointerException` khi gọi method qua `null` |
| `int x = Integer.MAX_VALUE; x + 1` | Tràn số `int`, kết quả là `-2147483648` |

Câu chữ cụ thể của thông báo lỗi có thể khác giữa các phiên bản JDK, nên hãy đọc **loại lỗi** và snippet gây lỗi thay vì học thuộc toàn bộ chuỗi thông báo.

Nếu một snippet lỗi bị từ chối, các snippet hợp lệ trước đó thông thường vẫn có trong trạng thái phiên; bạn có thể sửa và nhập lại.

Một biểu thức có thể **đúng cú pháp mà sai ý định**. Chẳng hạn `7 / 2` trả `3` chính xác theo quy tắc chia nguyên của Java, nhưng nếu bài toán yêu cầu `3.5` thì bạn cần đổi biểu thức sang `7.0 / 2`. JShell giúp quan sát hành vi, không thể tự đoán yêu cầu nghiệp vụ của bạn.

## 8. Lệnh JShell để xem và quản lý phiên

Những lệnh mở đầu bằng dấu `/` là **lệnh riêng của JShell**, không phải phép chia hay cú pháp Java.

| Lệnh | Tác dụng |
|---|---|
| `/help` | Xem trợ giúp và các lệnh hỗ trợ |
| `/help intro` | Xem hướng dẫn nhập môn |
| `/vars` | Liệt kê biến trong phiên |
| `/methods` | Liệt kê các method đã khai báo |
| `/types` | Liệt kê các kiểu đã định nghĩa |
| `/imports` | Xem các import đang dùng |
| `/list` | Hiển thị các snippet trong phiên kèm ID |
| `/drop <id-or-name>` | Bỏ snippet/khai báo đã chọn |
| `/reset` | Đặt lại trạng thái snippets, hữu ích khi muốn làm từ đầu |
| `/save <file>` | Lưu các snippet dưới dạng văn bản nguồn |
| `/open <file>` | Nạp và thực hiện snippets từ tệp |
| `/exit` | Thoát JShell |

Các ID cụ thể phụ thuộc lịch sử phiên. Hãy xem `/list` để xác định ID trước khi dùng `/drop`. Không chọn ID theo ảnh chụp của người khác.

Ví dụ:

~~~text
jshell> /vars
jshell> /methods
jshell> /list
jshell> /reset
jshell> /exit
~~~

Sau `/reset`, thử gọi lại `square(5)`: nếu bạn chưa nhập lại định nghĩa `square`, JShell không còn tìm thấy method ấy. Đây là cách chứng minh trạng thái JShell **thuộc phiên tương tác**, không tự trở thành một class trong dự án.

## 9. Lưu một bài thử JShell để dùng lại

Bạn có thể tạo tệp tên **`practice.jsh`** bằng editor, chứa các snippet Java sau (không cần `package`, không cần `main`):

~~~java
int price = 50_000;
int count = 2;
int subtotal = price * count;
int discounted(long amount, int percent) {
    if (amount < 0 || percent < 0 || percent > 100) {
        throw new IllegalArgumentException("Du lieu khong hop le");
    }
    return (int) (amount * (100 - percent) / 100);
}
subtotal
discounted(subtotal, 10)
~~~

Từ JShell, trong thư mục chứa tệp, nhập:

~~~text
/open practice.jsh
/vars
/methods
~~~

**Kết quả giá trị cần xác nhận:** `subtotal = 100000`, `discounted(subtotal, 10) = 90000`. Dạng hiển thị còn phụ thuộc cấu hình feedback của JShell.

Tệp `.jsh` dùng để lưu các snippet phiên, không phải một class Java biên dịch được bằng `javac practice.jsh`. Nếu muốn lưu trực tiếp những snippet bạn đã nhập trong phiên, dùng `/save practice.jsh`, rồi có thể `/reset` và `/open practice.jsh` để thử lại.

**Giới hạn số học của demo:** method `discounted` trong file chỉ dùng giá nhỏ cho bài thử nhanh; việc `amount * (100 - percent)` có thể tràn `long`, và ép `long` sang `int` có thể mất thông tin. Vì vậy không dùng đoạn này làm mô-đun tính tiền sản xuất. Ở bài tập cuối, bạn sẽ sửa cả công thức lẫn kiểu trả về.

### Chạy file snippet từ terminal

JShell có thể được khởi động với file snippets, ví dụ từ thư mục có `practice.jsh`:

**Bash:**

~~~bash
jshell practice.jsh
~~~

**PowerShell:**

~~~powershell
jshell .\practice.jsh
~~~

Dù chạy theo cách nào, các snippets được đánh giá trong JShell; bạn không có một file class ứng dụng chứa `public static void main` chỉ nhờ lưu `.jsh`.

## 10. Từ JShell sang một chương trình Java hoàn chỉnh

Khi một ý tưởng đã được thử, hãy chuyển thành class có `main` để đóng gói và chạy lặp lại có chủ đích. Tạo **`Experiment.java`**:

~~~java
public final class Experiment {
    private Experiment() {}

    static int square(int n) {
        return Math.multiplyExact(n, n);
    }

    public static void main(String[] args) {
        int value = args.length == 0 ? 5 : Integer.parseInt(args[0]);
        System.out.println("Binh phuong: " + square(value));
    }
}
~~~

Chạy bằng **JDK 17+**:

**Bash:**

~~~bash
javac --release 17 -encoding UTF-8 Experiment.java
java Experiment
java Experiment 7
~~~

**PowerShell:**

~~~powershell
javac --release 17 -encoding UTF-8 .\Experiment.java
java Experiment
java Experiment 7
~~~

Đầu ra của hai lần chạy tương ứng:

~~~text
Binh phuong: 25
Binh phuong: 49
~~~

Bài này cố tình phân biệt: nhập `square(5)` trong JShell dùng **trạng thái snippet của phiên**; chạy `java Experiment` dùng **class đã biên dịch có `main`**. Hai cách bổ sung nhau, không thay thế nhau trong mọi tình huống.

Với đối số không phải số nguyên, `Integer.parseInt` có thể ném `NumberFormatException`. Với số đủ lớn, `Math.multiplyExact` phát hiện tràn `int` bằng `ArithmeticException`. Chạy được với số 5 không có nghĩa chạy được với mọi đầu vào.

## 11. Một phiên thực hành có cấu trúc

Thực hiện các bước sau trong một phiên JShell mới, ghi lại **giá trị thực**, không chỉ dòng bạn nhập:

1. Mở `jshell`; dùng `/help intro` nếu chưa quen.
2. Tính `9 / 4` và `9.0 / 4`; giải thích vì sao là `2` và `2.25`.
3. Tạo `int price = 75_000;` và `int count = 2;`. Thử `price * count`.
4. Đổi `count = 3;`; kiểm tra lại biểu thức. Nếu đã gán kết quả vào biến riêng, quan sát giá trị cũ có tự đổi hay không.
5. Khai báo `long total(long unitPrice, int quantity)` với `Math.multiplyExact` và từ chối số lượng âm; gọi method với ít nhất ba đầu vào.
6. Nhập `/vars`, `/methods`, `/list` và chỉ ra những snippet đã tạo.
7. Thử một lỗi kiểu, một lỗi tên và một exception runtime; ghi lại khác biệt.
8. Lưu phiên bằng `/save`, mở lại sau `/reset` và xác minh có thể tái tạo các phép thử.

### Bảng kết quả gợi ý

| Thử nghiệm | Giá trị/kết quả cần xác nhận |
|---|---|
| `9 / 4` | `2` |
| `9.0 / 4` | `2.25` |
| `75_000 * 2` | `150000` |
| `75_000 * 3` | `225000` |
| `Math.multiplyExact(75_000L, 3L)` | `225000` |
| `"hello".substring(1, 4)` | `"ell"` |
| `(String) null` rồi gọi `.length()` | `NullPointerException` khi method được gọi |

## 12. Bài tập kèm hướng dẫn đáp án

### Bài A — viết phương thức kiểm tra điểm

Tạo `boolean passed(int score)` trả `true` khi `score >= 50`; từ chối điểm dưới 0 hoặc trên 100 bằng `IllegalArgumentException`.

Một đáp án:

~~~java
boolean passed(int score) {
    if (score < 0 || score > 100) {
        throw new IllegalArgumentException("Score 0..100");
    }
    return score >= 50;
}
~~~

Thử `passed(49)`, `passed(50)`, `passed(100)`, `passed(101)`. Kỳ vọng: `false`, `true`, `true`, rồi exception. Ngưỡng đúng `50` kiểm tra được sự khác biệt giữa `>` và `>=`.

### Bài B — sửa kiểu trả về và tràn số

Trong `practice.jsh`, thay đoạn tính tiền bằng phương thức trả `long`, giới hạn đầu vào và dùng các phép toán an toàn hơn:

~~~java
long discounted(long amount, int percent) {
    if (amount < 0 || percent < 0 || percent > 100) {
        throw new IllegalArgumentException("Invalid input");
    }
    long kept = 100L - percent;
    long numerator = Math.multiplyExact(amount, kept);
    return numerator / 100L;
}
~~~

`discounted(100_000L, 10)` cho `90000`. Khi `amount` rất lớn, `multiplyExact` có thể ném exception thay vì âm thầm tạo số sai. Đây là **cách phát hiện tràn**, chưa phải giải pháp tính mọi số `long` lớn; muốn tính chính xác trên toàn miền có thể dùng `BigInteger` hoặc thay đổi thuật toán theo yêu cầu nghiệp vụ.

### Bài C — giải thích trạng thái phiên

Sau `/reset`, gọi `passed(50)` trước khi định nghĩa lại method. Vì sao không hoạt động?

**Đáp án:** snippet định nghĩa `passed` thuộc trạng thái phiên đã được xóa; JShell không lưu method đó như một class trên classpath để các phiên sau tự nạp. Hãy nhập lại, dùng `/open` với file snippet phù hợp hoặc viết mã nguồn `.java` có cấu trúc.

## 13. Checklist hoàn thành

- [ ] Mở được JShell từ JDK 17+ trên Bash hoặc PowerShell.
- [ ] Phân biệt lệnh terminal (`jshell`, `javac`, `java`) với lệnh bên trong JShell (`/vars`, `/list`, `/exit`).
- [ ] Đánh giá được các biểu thức số học, chuỗi, boolean và giải thích được kiểu kết quả.
- [ ] Khai báo, đọc và cập nhật biến; hiểu biến lưu giá trị, không phải công thức tự cập nhật.
- [ ] Khai báo và gọi phương thức có tham số/kiểu trả về.
- [ ] Phân biệt lỗi biên dịch snippet và exception khi đánh giá.
- [ ] Dùng `/vars`, `/methods`, `/list`, `/reset` để quan sát trạng thái.
- [ ] Lưu/mở `.jsh`, hiểu nó không tương đương class Java có `main`.
- [ ] Biên dịch và chạy `Experiment.java` với đầu ra dự kiến.
- [ ] Nhận ra hạn chế của JShell: phù hợp để thử nhanh, không thay thế tests/build của dự án.

**Kết luận:** JShell là phòng thực hành ngắn gọn để học cách Java đánh giá biểu thức, lưu biến và gọi phương thức. Hãy quan sát kết quả và lỗi thực tế, hiểu trạng thái của phiên, rồi chuyển những đoạn mã cần kiểm thử và tái sử dụng lâu dài sang tệp `.java` với `main` hoặc bộ test thích hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tools-and-execution-007">Phân biệt tham số JVM với đối số chương trình</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt tham số JVM với đối số chương trình trong Java

Khi chạy Java bằng terminal, bạn có thể thấy một lệnh như sau:

~~~bash
java -Xmx128m -Dstudy.mode=lab -cp out ArgsBoundaryDemo 45 "Java core" --check
~~~

Ở đây, `-Xmx128m` và `-Dstudy.mode=lab` là các tùy chọn cấu hình môi trường chạy Java. `-cp out` cho **Java launcher** biết nơi tìm lớp. Trong khi đó, `45`, `Java core` và `--check` là **đối số của chương trình**; chúng được truyền đến `main(String[] args)`.

Bài học này giúp bạn nhận ra **ranh giới giữa phần cấu hình chạy Java và dữ liệu do ứng dụng nhận**. Ví dụ sử dụng **JDK 17 trở lên**, chỉ cần terminal Windows PowerShell hoặc Linux và một tệp nguồn Java.

## 1. Nhìn cấu trúc lệnh từ trái sang phải

Ở dạng chạy một lớp đã biên dịch, lệnh thường có cấu trúc:

~~~text
java [tùy chọn cho launcher/JVM] TênLớp [đối số chương trình]
        ^                           ^         ^
        cấu hình                điểm bắt đầu  args[]
~~~

Trong ví dụ:

~~~text
java -Xmx128m -Dstudy.mode=lab -cp out ArgsBoundaryDemo 45 "Java core" --check
     └──────── tùy chọn ────────┘    └lớp───┘ └──── args[] ────────┘
~~~

Một số tài liệu gọi chung các phần trước tên lớp là **“JVM arguments”**. Cách gọi này tiện trong hội thoại, nhưng nên phân biệt chính xác:

| Thành phần | Ai xử lý? | Tác dụng | Có xuất hiện trong `args[]`? |
| --- | --- | --- | --- |
| `-Xmx128m` | JVM/Java launcher | Cấu hình giới hạn heap tối đa | Không |
| `-Xms64m` | JVM/Java launcher | Cấu hình heap ban đầu | Không |
| `-Dstudy.mode=lab` | JVM/Java launcher | Đặt Java system property | Không |
| `-ea` | JVM/Java launcher | Bật kiểm tra `assert` | Không |
| `-cp out` | Java launcher | Chọn classpath tìm lớp/thư viện | Không |
| `ArgsBoundaryDemo` | Java launcher | Chọn lớp có điểm bắt đầu `main` | Không |
| `45` | Ứng dụng | Chuỗi đối số thứ nhất | Có: `args[0]` |
| `"Java core"` | Shell rồi ứng dụng | Một chuỗi chứa khoảng trắng | Có: `args[1]` |
| `--check` | Ứng dụng | Cờ do ứng dụng tự quy định | Có: `args[2]` |

`-cp` là **tùy chọn của launcher** chứ không phải phép đặt giới hạn bộ nhớ cho JVM. Còn `-D` tạo **system property**, không phải biến môi trường của hệ điều hành.

**Quy tắc thực hành:** các tùy chọn Java như `-Xmx`, `-D`, `-cp` phải đặt ở phần trước tên lớp đối với dạng chạy này; nội dung sau tên lớp được coi là dữ liệu ứng dụng.

## 2. Tạo chương trình minh họa để nhìn thấy `args[]`

Tạo tệp **`ArgsBoundaryDemo.java`** chứa mã Java sau:

~~~java
public class ArgsBoundaryDemo {
    public static void main(String[] args) {
        String mode = System.getProperty("study.mode", "default");

        System.out.println("study.mode=" + mode);
        System.out.println("args.length=" + args.length);

        for (int i = 0; i < args.length; i++) {
            System.out.println("args[" + i + "]=" + args[i]);
        }

        if (args.length > 0) {
            try {
                int minutes = Integer.parseInt(args[0]);
                if (minutes < 0) {
                    System.out.println("minutes=INVALID_NEGATIVE");
                } else {
                    System.out.println("minutes=" + minutes);
                }
            } catch (NumberFormatException ex) {
                System.out.println("minutes=INVALID_NUMBER");
            }
        }
    }
}
~~~

Ý nghĩa:

- `main(String[] args)` nhận **mảng chuỗi**. Dù bạn gõ `45`, trong `args[0]` ban đầu vẫn là `String` `"45"`, không phải số `int`.
- `System.getProperty("study.mode", "default")` đọc system property đã được cấu hình; nếu property chưa có thì dùng chuỗi mặc định `default`.
- Vòng `for` in **đúng từng chuỗi** nhận được và chỉ số của nó.
- `Integer.parseInt` minh họa việc ứng dụng tự phân tích đối số. Java launcher không tự chuyển mọi đối số ứng dụng thành số.
- Giá trị âm và chuỗi không phải số được xử lý tường minh; đó là **quy tắc ứng dụng**, không phải quy tắc của `-Xmx` hay `-D`.

Đây là chương trình Java độc lập, không cần thư viện bên ngoài, không cần framework hay tệp cấu hình đặc biệt.

## 3. Biên dịch và chạy trên Windows PowerShell hoặc Linux

Trước tiên kiểm tra JDK:

~~~bash
java -version
javac -version
~~~

Lưu `ArgsBoundaryDemo.java` trong một thư mục làm việc, mở terminal ở thư mục đó.

**Tạo thư mục đầu ra trên Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force -Path out | Out-Null
~~~

**Tạo thư mục đầu ra trên Linux/Bash:**

~~~bash
mkdir -p out
~~~

Biên dịch — lệnh giống nhau ở cả hai hệ điều hành:

~~~bash
javac -encoding UTF-8 -d out ArgsBoundaryDemo.java
~~~

Sau đó chạy:

~~~bash
java -Xmx128m -Dstudy.mode=lab -cp out ArgsBoundaryDemo 45 "Java core" --check
~~~

**Kết quả dự kiến:**

~~~text
study.mode=lab
args.length=3
args[0]=45
args[1]=Java core
args[2]=--check
minutes=45
~~~

**Tại sao?**

1. Java launcher nhận `-Xmx128m` để yêu cầu cấu hình heap tối đa; tùy chọn này **không đi vào `args[]`**.
2. `-Dstudy.mode=lab` tạo property `study.mode` mang giá trị `lab`. Mã đọc nó qua `System.getProperty`.
3. `-cp out` chỉ thư mục tìm lớp `ArgsBoundaryDemo` đã được biên dịch.
4. `ArgsBoundaryDemo` xác định lớp được chạy.
5. Ba chuỗi còn lại đi vào `args[0]`, `args[1]` và `args[2]`. Dấu nháy quanh `"Java core"` được **shell** dùng để giữ hai từ thành **một đối số**, không phải ký tự thuộc nội dung chuỗi nhận được.

Không nên dựa vào con số bộ nhớ heap thực tế để kiểm chứng ví dụ: `-Xmx128m` quy định cấu hình giới hạn, không cam kết JVM cấp phát hoặc sử dụng đúng 128 MiB ngay khi chạy.

## 4. Đặt `-D` sai vị trí: kết quả thay đổi rõ rệt

Hãy so sánh hai lệnh có cùng văn bản `-Dstudy.mode=lab`:

**Đặt trước tên lớp — đây là tùy chọn Java:**

~~~bash
java -Dstudy.mode=lab -cp out ArgsBoundaryDemo 45
~~~

~~~text
study.mode=lab
args.length=1
args[0]=45
minutes=45
~~~

**Đặt sau tên lớp — đây là đối số chương trình:**

~~~bash
java -cp out ArgsBoundaryDemo 45 -Dstudy.mode=lab
~~~

~~~text
study.mode=default
args.length=2
args[0]=45
args[1]=-Dstudy.mode=lab
minutes=45
~~~

Lệnh thứ hai **không đặt system property** `study.mode`. Chuỗi bắt đầu bằng `-D` chỉ là một phần tử trong `args[]`, và vì ứng dụng ví dụ không tự phân tích chuỗi đó nên nó không thay đổi cách chạy.

Tương tự, chạy:

~~~bash
java -cp out ArgsBoundaryDemo 45 -Xmx128m
~~~

sẽ đưa chuỗi `-Xmx128m` vào đối số ứng dụng, **không đặt giới hạn heap qua chuỗi đó**. Tác dụng của ký tự `-` phụ thuộc **bộ phân tích lệnh nào đang đọc nó**.

Đây là nguyên nhân phổ biến của những lỗi kiểu “đã thêm `-D` mà ứng dụng vẫn đọc property mặc định”.

## 5. Đối số ứng dụng không tự có ý nghĩa

Java chỉ đưa các chuỗi sau lớp tới `main`. Chương trình phải **tự quyết định** chúng đại diện cho gì.

Giả sử một ứng dụng khác quy định:

~~~text
java App 15 --verbose --output=result.txt
~~~

`15` có thể là số lượng, số phút hay một mã tùy ý; `--verbose` có thể bật log; `--output=result.txt` có thể chọn tên tệp. **Đó là quy ước riêng của `App`**, không phải bộ tùy chọn chuẩn do JVM xử lý.

Trong chương trình `ArgsBoundaryDemo`:

- `45` được diễn giải thành số phút chỉ vì mã chủ động gọi `Integer.parseInt(args[0])`.
- `--check` được in ra nhưng chưa làm gì khác vì mã không có điều kiện xử lý nó.
- Khi không có đối số, `args.length = 0`; vòng lặp không in phần tử nào, và khối parse được bỏ qua.
- Khi `args[0]` là `abc` hoặc số vượt miền `int`, `parseInt` thất bại; chương trình in `minutes=INVALID_NUMBER`.
- Khi `args[0] = -5`, phép parse thành công nhưng ứng dụng báo `minutes=INVALID_NEGATIVE`.

Lỗi parse và dữ liệu âm là hai tình huống khác nhau: một tình huống **sai định dạng số**, tình huống còn lại **định dạng đúng nhưng vi phạm yêu cầu**.

## 6. `-D`, biến môi trường và classpath khác nhau thế nào?

Một hệ thống thường có cả **Java system properties**, **environment variables** và **program arguments**. Chúng không thay thế cho nhau.

| Cơ chế | Cách thiết lập / truyền | Cách Java đọc | Đặc điểm |
| --- | --- | --- | --- |
| System property | `java -Dstudy.mode=lab ...` | `System.getProperty("study.mode")` | Thuộc hệ thống properties của tiến trình JVM |
| Biến môi trường | Do shell/hệ điều hành đặt trước khi chạy | `System.getenv("STUDY_MODE")` | Được tiến trình thừa hưởng từ môi trường |
| Đối số chương trình | Sau tên lớp | `args[0]`, `args[1]`... | Mảng chuỗi ứng dụng nhận |
| Classpath | `java -cp out ...` | Được launcher/loader sử dụng | Xác định nơi tìm lớp và các thư viện |

Ví dụ thiết lập **biến môi trường** chỉ trong phiên terminal hiện tại:

**PowerShell:**

~~~powershell
$env:STUDY_MODE = "lab"
~~~

**Bash/Linux:**

~~~bash
export STUDY_MODE=lab
~~~

Nếu muốn thử trực tiếp trong mã, bổ sung lời gọi này vào `main`:

~~~java
System.out.println(
        "env.STUDY_MODE=" + System.getenv("STUDY_MODE"));
~~~

Đây là **đoạn bổ sung**, không có sẵn trong đầu ra chuẩn ở mục 3. Lệnh đặt biến môi trường `STUDY_MODE` không tự tạo Java system property `study.mode`; nếu muốn hỗ trợ cả hai, ứng dụng phải định nghĩa rõ quy tắc ưu tiên.

Lưu ý: classpath nhiều mục dùng ký tự phân tách **`;` trên Windows** và **`:` trên Linux/macOS**. Ví dụ ở bài này chỉ có một thư mục `out` nên cùng một lệnh `-cp out` hoạt động trên cả hai nền tảng.

## 7. Các cách khởi chạy khác vẫn có ranh giới tương tự

### Chạy JAR thực thi

Nếu đã có tệp JAR hợp lệ chứa cấu hình `Main-Class`, cú pháp điển hình là:

~~~bash
java -Xmx256m -Dstudy.mode=prod -jar app.jar 45 --check
~~~

- `-Xmx256m` và `-Dstudy.mode=prod`: tùy chọn JVM.
- `-jar app.jar`: Java launcher chọn JAR và main class được cấu hình trong manifest.
- `45`, `--check`: đối số ứng dụng được truyền đến `main`.

Đây chỉ là **ví dụ cú pháp**: bài này không tạo `app.jar`, nên không chạy lệnh trên nếu chưa có JAR tương ứng. Tên JAR không được truyền thành `args[0]`.

### Chạy thẳng tệp nguồn trên JDK 17+

Java cũng hỗ trợ chế độ chạy tệp nguồn:

~~~bash
java -Xmx128m -Dstudy.mode=lab ArgsBoundaryDemo.java 45 "Java core" --check
~~~

Trong cách này, `java` biên dịch mã nguồn trong bộ nhớ rồi chạy nó; không cần lệnh `javac` riêng. Tùy chọn JVM vẫn đi **trước tệp nguồn**; dữ liệu ứng dụng vẫn đi **sau tệp nguồn**.

Lệnh trên giả định bạn đang ở thư mục có `ArgsBoundaryDemo.java`. Nếu đã chỉ định đường dẫn khác, thay tên tệp bằng đường dẫn thực tế.

## 8. Xử lý lỗi thường gặp

| Hiện tượng | Nguyên nhân có thể | Cách kiểm tra/sửa |
| --- | --- | --- |
| `System.getProperty` trả `default` dù đã gõ `-D` | Đặt `-D` sau tên lớp, sai key hoặc chưa cấu hình | Đưa `-Dkey=value` lên trước lớp, kiểm tra chính tả |
| `args.length` khác số mong đợi | Đếm cả tên lớp hoặc chia nhầm chuỗi có khoảng trắng | Chỉ đếm chuỗi sau tên lớp; đặt chuỗi có khoảng trắng trong nháy kép |
| `-Xmx...` xuất hiện trong `args[]` | Đã đặt tùy chọn ở vị trí đối số ứng dụng | Đưa lên trước tên lớp hoặc tên tệp/JAR |
| JVM báo không nhận diện tùy chọn | Gõ sai flag hoặc phiên bản JDK không hỗ trợ | Kiểm tra `java -version` và `java --help` |
| Không tìm được main class | Thiếu `-cp out`, classpath sai hoặc chưa biên dịch | Xem tệp `.class` và kiểm tra tên lớp |
| `NumberFormatException` | Đối số được parse không phải số `int` hợp lệ | Kiểm tra chuỗi, miền số và thông báo |
| Heap hết bộ nhớ | Dữ liệu hoặc hành vi cấp phát vượt khả năng JVM | Đo/điều tra sử dụng bộ nhớ, không chỉ tăng `-Xmx` tùy tiện |

Muốn xem thêm các tùy chọn launcher:

~~~bash
java --help
java -X
~~~

Một số tùy chọn JVM chuyên sâu có dạng `-XX:...`; cú pháp và sự hỗ trợ phụ thuộc phiên bản/triển khai JVM. Không nên chép tùy chọn chuyên sâu từ môi trường khác mà chưa hiểu tác dụng.

### Đừng nhầm lỗi Java với lỗi của shell

Trước khi Java launcher đọc đối số, **shell** đã xử lý dấu nháy, khoảng trắng, biến môi trường và một số ký tự đặc biệt. PowerShell và Bash không có quy tắc hoàn toàn giống nhau. Ví dụ cách trích dẫn một đối số chứa khoảng trắng bằng `"Java core"` hoạt động trong cả hai, nhưng quy tắc escape các dấu nháy lồng nhau khác nhau.

Khi cần chẩn đoán, hãy bắt đầu bằng ví dụ đơn giản và in `args.length` cùng từng `args[i]` như chương trình trên. Đừng vội kết luận JVM đã làm mất một chuỗi nếu shell là bên đã phân tách nó khác dự kiến.

## 9. Bài tập tự kiểm tra

**Bài 1 — Dự đoán đầu ra.** Với:

~~~bash
java -Dstudy.mode=study -cp out ArgsBoundaryDemo 20 "bai mot"
~~~

Hãy dự đoán property, số đối số và giá trị từng đối số.

*Đáp án:* property `study`, `args.length=2`, `args[0]=20`, `args[1]=bai mot`, `minutes=20`.

**Bài 2 — Phát hiện đặt sai vị trí.**

~~~bash
java -cp out ArgsBoundaryDemo -Xmx256m 20
~~~

Ở đây giới hạn heap có được đặt bởi chuỗi `-Xmx256m` không? `args[]` chứa gì?

*Đáp án:* Không. `args[0]="-Xmx256m"`, `args[1]="20"`, ứng dụng in `minutes=INVALID_NUMBER` do cố parse phần tử đầu.

**Bài 3 — Phân biệt ba cơ chế.** Muốn ứng dụng đọc cấu hình `study.mode` qua `System.getProperty`, bạn nên dùng `-D`, biến môi trường hay `args[0]`?

*Đáp án:* Dùng `-Dstudy.mode=...` ở trước tên lớp. Hai cách kia chỉ có tác dụng nếu ứng dụng chủ động đọc và ánh xạ chúng.

**Bài 4 — Sửa câu lệnh chạy.** Chương trình cần nhận `45` và `--check` trong `args[]`, giới hạn heap được yêu cầu là `128m`, property `study.mode=qa`. Hãy viết một lệnh chạy đúng.

*Một đáp án:*

~~~bash
java -Xmx128m -Dstudy.mode=qa -cp out ArgsBoundaryDemo 45 --check
~~~

**Bài 5 — Thử dữ liệu lỗi.** Lần lượt đặt `args[0]` thành `-1`, `abc` và `2147483648`. Chúng khác nhau thế nào?

*Đáp án:* `-1` parse thành công nhưng vi phạm miền dữ liệu; `abc` không phải số; `2147483648` là số viết đúng hình thức thập phân nhưng vượt miền `int`, nên hai giá trị sau đều bị `NumberFormatException`.

## Checklist ghi nhớ

- [ ] Biết phân biệt **tùy chọn trước tên lớp/JAR/tệp nguồn** và **đối số sau điểm bắt đầu**.
- [ ] Biết `-Xmx` cấu hình heap, `-D` cấu hình system property, còn `-cp` là tùy chọn tìm lớp của launcher.
- [ ] Biết `String[] args` chỉ chứa những chuỗi mà ứng dụng được truyền sau entry point.
- [ ] Biết vì sao `"hai tu"` được truyền thành một đối số trên PowerShell/Bash.
- [ ] Biết `System.getProperty` và `System.getenv` đọc hai nguồn cấu hình khác nhau.
- [ ] Biết cách chạy `javac` rồi `java -cp out ...` trên cả Windows/Linux và so sánh với `java TenTep.java`.
- [ ] Có thể tự đọc `args.length`, phân loại lỗi parse và sửa vị trí `-D` khi cấu hình không có hiệu lực.

**Kết luận:** Hãy nhìn lệnh Java như hai phần: **cấu hình cách chạy Java** và **dữ liệu đưa cho ứng dụng**. Đặt đúng phía của ranh giới tên lớp/JAR/tệp nguồn giúp tránh nhầm lẫn giữa JVM, Java launcher và `main(String[] args)`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tools-and-execution-008">Nhận diện lỗi không tìm thấy lớp và không tương thích phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện lỗi không tìm thấy lớp và không tương thích phiên bản

## 1. Mục tiêu: đọc đúng lỗi trước khi sửa

Một chương trình Java có thể **biên dịch thành công nhưng không khởi động**, hoặc chạy được trên máy phát triển mà thất bại trên máy khác. Hai nhóm nguyên nhân rất phổ biến là:

- **Không tìm thấy lớp:** Java launcher hoặc class loader không tìm được class theo tên và nơi tìm kiếm được cấu hình; hoặc có class chính nhưng thiếu một class phụ thuộc.
- **Không tương thích phiên bản:** bytecode được biên dịch cho mức Java mới hơn JVM đang chạy có thể đọc, gây `UnsupportedClassVersionError`.

Đừng vội sửa mã nghiệp vụ. Trước tiên hãy xác định: lỗi xảy ra lúc **`javac`**, lúc **`java` tìm main class**, hay lúc **JVM nạp một class phụ thuộc**.

~~~mermaid
flowchart TD
    START["Lệnh Java thất bại"] --> PHASE{"Lệnh nào báo lỗi?"}
    PHASE -->|"javac"| BUILD["Kiểm tra mã nguồn,<br/>dependency khi biên dịch"]
    PHASE -->|"java"| MSG{"Thông báo chính?"}
    MSG -->|"Could not find or load main class"| NAME["Tên class, package,<br/>classpath root"]
    MSG -->|"NoClassDefFoundError"| DEP["Thiếu dependency runtime<br/>hoặc lỗi định nghĩa/khởi tạo class"]
    MSG -->|"UnsupportedClassVersionError"| VER["JVM chạy quá cũ<br/>so với class file"]
    MSG -->|"Main method not found"| MAIN["Kiểm tra chữ ký main"]
    NAME --> AGAIN["Sửa đúng nguyên nhân<br/>rồi chạy lại"]
    DEP --> AGAIN
    VER --> AGAIN
    MAIN --> AGAIN
~~~

Các thông báo đầy đủ có thể khác theo JVM và môi trường; sơ đồ là hướng chẩn đoán ban đầu, không phải kết luận dựa vào duy nhất một dòng stack trace.

## 2. Ba nơi dễ bị nhầm: source, bytecode và runtime

| Thành phần | Ví dụ | Công dụng |
|---|---|---|
| Source file | `src/demo/Hello.java` | Đầu vào cho `javac` |
| Class file | `out/demo/Hello.class` | Bytecode JVM có thể nạp |
| Tên class đầy đủ | `demo.Hello` | Tên được chỉ định khi chạy class mode |
| Classpath root | `out` | Nơi JVM bắt đầu tìm đường dẫn `demo/Hello.class` |
| Runtime Java | `java` và JVM đi kèm | Nạp, kiểm tra và thực thi bytecode |
| Compiler | `javac` | Chọn mức ngôn ngữ/API/class file lúc build |

**Quy tắc:** với `package demo;` và `public class Hello`, sau khi biên dịch ra `out/demo/Hello.class`, cách chạy đúng là **`java -cp out demo.Hello`**, không phải `java -cp out Hello` hay `java -cp out/demo demo.Hello`.

Classpath chỉ định **gốc tìm kiếm**, còn package là một phần của **tên định danh class**. Tên tệp `.java` và `.class` không được truyền thay thế tên class trong class mode.

## 3. Dựng một ví dụ chạy đúng trên Java 17+

Tạo cấu trúc sau:

~~~text
classpath-demo/
└── src/
    └── demo/
        └── Hello.java
~~~

Nội dung `src/demo/Hello.java`:

~~~java
package demo;

public final class Hello {
    private Hello() {}

    public static void main(String[] args) {
        String name = args.length == 0 ? "Java" : args[0];
        System.out.println("Hello, " + name);
    }
}
~~~

Từ thư mục `classpath-demo`, tạo thư mục output, biên dịch, rồi chạy.

**Bash (Linux/macOS):**

~~~bash
mkdir -p out
javac --release 17 -encoding UTF-8 -d out src/demo/Hello.java
java -cp out demo.Hello
java -cp out demo.Hello Lan
~~~

**PowerShell (Windows):**

~~~powershell
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -encoding UTF-8 -d out .\src\demo\Hello.java
java -cp out demo.Hello
java -cp out demo.Hello Lan
~~~

Kết quả dự kiến từ **hai lệnh chạy**:

~~~text
Hello, Java
Hello, Lan
~~~

Tệp được tạo:

~~~text
out/
└── demo/
    └── Hello.class
~~~

`javac -d out` giữ cấu trúc package trong output. `java -cp out demo.Hello` nạp bytecode tương ứng, rồi gọi `main(String[] args)`.

**Thử lỗi có chủ đích:** chạy `java -cp out Hello`. Launcher tìm `Hello` ở default package, không phải `demo.Hello`, nên thường báo:

~~~text
Error: Could not find or load main class Hello
Caused by: java.lang.ClassNotFoundException: Hello
~~~

Cách sửa là **đổi lệnh chạy** thành `java -cp out demo.Hello`. Không cần đổi tên source, xóa class file hay thêm package lần nữa.

## 4. Lỗi không tìm thấy main class: điều gì cần kiểm tra?

Một lỗi điển hình:

~~~text
Error: Could not find or load main class demo.Hello
Caused by: java.lang.ClassNotFoundException: demo.Hello
~~~

Hãy kiểm tra tuần tự:

1. **Biên dịch có thành công không?** Nếu `javac` báo lỗi, `.class` mới có thể chưa được tạo; tệp bytecode cũ vẫn còn và có thể gây nhầm lẫn.
2. **Class có nằm đúng chỗ?** Với tên `demo.Hello` và classpath `out`, cần có `out/demo/Hello.class`.
3. **Đang đứng trong thư mục nào?** Đường dẫn `out` là tương đối theo thư mục làm việc hiện tại.
4. **Tên class có đầy đủ package không?** `Hello` khác `demo.Hello`; chữ hoa/thường trong tên class cũng quan trọng.
5. **`-cp` có trỏ tới gốc đúng không?** `out/demo` không phải root của class `demo.Hello`.
6. **Có vô tình chạy source mode?** `java Hello.java` có ý nghĩa khác `java Hello` trên các JDK hỗ trợ chạy trực tiếp source.

Lệnh xem file và thư mục hiện hành:

| Bash | PowerShell |
|---|---|
| `pwd` | `Get-Location` |
| `ls out/demo/Hello.class` | `Get-Item .\out\demo\Hello.class` |
| `ls out` | `Get-ChildItem .\out` |

Nếu trình biên dịch và runtime đều tìm thấy class nhưng launcher lại báo thiếu **`main`**, đó là lỗi khác. Với cách chạy class thông thường trên Java 17, điểm vào thường là:

~~~java
public static void main(String[] args) {
    // ...
}
~~~

Class thiếu `main` vẫn có thể biên dịch thành công; nó chỉ không tự trở thành chương trình khởi chạy theo cách này.

## 5. Phân biệt ClassNotFoundException và NoClassDefFoundError

Hai tên trông giống nhau nhưng thường chỉ **những tình huống khác nhau**:

| Lỗi | Ý nghĩa phổ biến | Khi nào gặp? |
|---|---|---|
| `ClassNotFoundException` | Một API tải class **bằng tên**, như `Class.forName` hoặc `ClassLoader.loadClass`, không tìm được class | Nạp động, reflection, hoặc nguyên nhân bên dưới khi launcher không tìm thấy main class |
| `NoClassDefFoundError` | JVM cần định nghĩa/hoàn tất việc sử dụng một class nhưng không có định nghĩa cần thiết, hoặc class trước đó khởi tạo lỗi | Thiếu JAR/class ở runtime, khác biệt classpath hoặc lỗi khởi tạo class |

Ví dụ thiếu class chỉ **khi chạy**:

- Biên dịch `demo.App` có tham chiếu `lib.Helper` khi thư viện `lib.jar` tồn tại trong build classpath.
- Khi chạy, quên đưa `lib.jar` vào runtime classpath.
- JVM cố dùng `lib.Helper` và có thể ném `NoClassDefFoundError: lib/Helper` (thường có `ClassNotFoundException` trong chuỗi nguyên nhân).

**Không giải quyết mọi `NoClassDefFoundError` bằng cách thêm một JAR.** Nếu phần stack trace đề cập `Could not initialize class` hoặc `ExceptionInInitializerError`, class có thể đã được tìm thấy nhưng **static initialization trước đó thất bại**. Khi ấy cần xem lỗi gốc từ static initializer, không chỉ tìm file.

`NoClassDefFoundError` thuộc nhóm `Error`, không phải ngoại lệ nghiệp vụ nên dùng `try/catch` để tiếp tục bình thường. Hướng sửa thông thường là cấu hình đúng và loại bỏ nguyên nhân lỗi khi khởi động.

## 6. Lớp có tồn tại nhưng sai tên package: “wrong name”

Nếu JVM đang tìm `Hello` ở default package nhưng bytecode thực sự chứa tên nhị phân `demo.Hello`, có môi trường hiện lỗi liên quan:

~~~text
java.lang.NoClassDefFoundError: Hello (wrong name: demo/Hello)
~~~

Điều này khác với “không có file”. **File có thể tồn tại** nhưng vị trí và tên class được JVM yêu cầu **không khớp tên định danh bên trong class file**.

Không dùng thủ thuật đổi tên `Hello.class` hay chuyển bừa nó sang thư mục gốc. Hãy xác nhận nội dung `package demo;`, chạy bằng `demo.Hello` và đặt `-cp` vào đúng thư mục root.

Bạn có thể dùng JDK tool:

~~~bash
javap -classpath out demo.Hello
~~~

Nếu lệnh này hiển thị được class `demo.Hello`, nó là bằng chứng rằng `javap` tìm thấy class dưới root `out` theo tên đó (không chứng minh `main` hay toàn bộ dependency runtime đều đúng).

## 7. UnsupportedClassVersionError: bytecode mới, JVM cũ

Ví dụ tình huống:

- Một project được **biên dịch cho Java 17**, tạo class-file major version **61**.
- Bạn cố chạy class ấy bằng **Java 11**, runtime chỉ hỗ trợ class-file major version tối đa **55**.
- JVM có thể báo `UnsupportedClassVersionError`.

Thông báo thường có ý như:

~~~text
java.lang.UnsupportedClassVersionError: demo/Hello
has been compiled by a more recent version of the Java Runtime
(class file version 61.0);
this version of the Java Runtime only recognizes class file versions up to 55.0
~~~

Thông báo có thể khác giữa các JVM; hai con số là ví dụ chuẩn cho Java 17 và Java 11.

Bảng đối chiếu thường gặp:

| Java release | Class-file major version |
|---|---:|
| Java 8 | 52 |
| Java 11 | 55 |
| Java 17 | 61 |
| Java 21 | 65 |

**Không nhầm `javac -version` với `java -version`.** Hai lệnh có thể trỏ tới các JDK/runtime khác nhau nếu máy cài nhiều phiên bản hoặc cấu hình PATH không nhất quán.

Bạn cũng không thể chạy class-file version mới bằng cách **đổi tên file**, sửa package hay thêm `-cp`. Đây là vấn đề tương thích định dạng bytecode/runtime, không phải thiếu đường dẫn.

## 8. Chẩn đoán phiên bản bằng lệnh thật

**Trên Bash:**

~~~bash
java -version
javac -version
type -a java
type -a javac
~~~

**Trên PowerShell:**

~~~powershell
java -version
javac -version
Get-Command java
Get-Command javac
where.exe java
where.exe javac
~~~

Các lệnh này giúp xác minh **lệnh nào được tìm thấy trong PATH**. Trên Windows, `where.exe` khác alias `where` của PowerShell.

Để xem version của `.class`, với demo đã biên dịch:

~~~bash
javap -verbose -classpath out demo.Hello
~~~

Tìm các dòng gần phần đầu:

~~~text
Classfile .../demo/Hello.class
  ...
  major version: 61
~~~

`major version: 61` đúng với class file dành cho Java 17. Đường dẫn và chi tiết khác có thể thay đổi theo nền tảng/JDK.

Nếu đang dùng JDK hỗ trợ Java 17 và cần chạy trên runtime Java 17, biên dịch với:

~~~bash
javac --release 17 -encoding UTF-8 -d out src/demo/Hello.java
~~~

`--release 17` đồng thời chọn mức ngôn ngữ, bytecode target và **các API chuẩn được phép dùng** tương ứng Java 17. Nếu trình biên dịch quá cũ để hỗ trợ release đã yêu cầu, nó sẽ báo lỗi; bạn cần cài JDK phù hợp.

## 9. Ba hướng sửa lỗi phiên bản — chọn theo hoàn cảnh

### Hướng A: Nâng runtime

Nếu môi trường được phép nâng JDK/runtime, chọn runtime **có hỗ trợ class file mới**, đồng thời kiểm thử ứng dụng, thư viện và cấu hình. Đây thường là hướng phù hợp khi project thực sự cần API/tính năng Java mới.

**Không được giả định** nâng runtime là miễn phí rủi ro: framework, flag JVM, bảo mật và dependency native có thể đòi cập nhật.

### Hướng B: Biên dịch cho release cũ hơn

Nếu môi trường bắt buộc chạy Java 17 nhưng bạn đang build bằng JDK 21 (và mã không phụ thuộc tính năng Java 21), thử:

~~~bash
javac --release 17 -encoding UTF-8 -d out src/demo/Hello.java
~~~

Mức release yêu cầu phải được **chính phiên bản `javac` đang dùng hỗ trợ**. Với ví dụ này, class file đầu ra sẽ mang major 61. Dùng `javap -verbose` để tự kiểm tra.

Không thể chỉ thay `--release` rồi giữ cú pháp hoặc API chỉ có ở Java mới: compiler sẽ từ chối, và bạn cần refactor mã hoặc chọn runtime mới.

### Hướng C: Đồng bộ môi trường build và runtime

Ghi rõ yêu cầu phiên bản Java vào cấu hình project, CI và hướng dẫn chạy. Nếu build bằng Maven hoặc Gradle, cấu hình Java toolchain và release mục tiêu cho nhất quán. Nếu triển khai container, kiểm tra **base image runtime** thay vì chỉ JDK trên máy phát triển.

Một ứng dụng chạy được trên laptop chưa đủ bằng chứng image/server sẽ chạy được: cần chạy smoke test trên **runtime đích thực tế**.

## 10. Classpath lúc biên dịch khác classpath lúc chạy

Xét ứng dụng phụ thuộc một thư viện JAR. Bạn có thể biên dịch thành công nhờ tham số `-cp` của `javac`, nhưng khi chạy lại quên thư viện ấy trong `java -cp`.

Ví dụ cú pháp, giả sử **`lib/helper.jar` thực sự tồn tại** và chứa các class mà ứng dụng cần:

**Bash trên Linux/macOS:**

~~~bash
javac -cp "lib/helper.jar" -d out src/demo/App.java
java -cp "out:lib/helper.jar" demo.App
~~~

**PowerShell trên Windows:**

~~~powershell
javac -cp "lib/helper.jar" -d out .\src\demo\App.java
java -cp "out;lib/helper.jar" demo.App
~~~

Dấu phân cách nhiều classpath entries là **`:` trên Linux/macOS**, **`;` trên Windows** (phụ thuộc hệ điều hành của JVM, không đơn thuần loại terminal). Đây là ví dụ về **cú pháp**, không phải dự án hoàn chỉnh: class `App` và `helper.jar` phải có sẵn thì lệnh mới chạy được.

Một lỗi khác là thiếu dependency **bắc cầu**: ứng dụng có `library-a.jar` nhưng library A cần thêm library B lúc chạy. Đọc **stack trace tới nguyên nhân sâu nhất**, kiểm tra dependency tree hoặc nội dung JAR, thay vì đoán tên thư viện từ một dòng đầu.

**Lưu ý `java -jar`:** chế độ JAR thực thi có các quy tắc entry point và classpath riêng; `-cp` do bạn chỉ định thường bị bỏ qua khi dùng `-jar`. Khi thiếu dependency, hãy xem manifest, cơ chế đóng gói và tài liệu build của ứng dụng, không áp dụng máy móc cú pháp `java -cp ... Main` vào mọi file JAR.

## 11. Những trường hợp dễ chẩn đoán nhầm

| Triệu chứng | Không nên kết luận ngay | Điều nên xác minh |
|---|---|---|
| `Could not find or load main class` | “Code Java viết sai” | Full class name, classpath root, vị trí `.class` |
| `ClassNotFoundException` | “Luôn thiếu dependency JAR” | Tên lớp được yêu cầu, class loader đang dùng |
| `NoClassDefFoundError` | “Chắc chắn thiếu file” | Có `Caused by`, `wrong name`, `Could not initialize class` không? |
| `UnsupportedClassVersionError` | “Sửa lại source cho đúng cú pháp” | Class-file major vs runtime `java -version` |
| `cannot find symbol` từ `javac` | “Runtime classpath thiếu” | Source/build dependencies ở thời điểm compile |
| `Main method not found` | “Class không tồn tại” | Main class có được tìm thấy nhưng thiếu entry point không? |
| Lỗi xuất hiện chỉ trên server | “Server Java bị hỏng” | JDK/JVM version, container image, PATH, thư mục làm việc, packaging |
| Sau build vẫn in kết quả cũ | “JVM cache nhầm mã” | Có đang chạy class/JAR cũ hoặc output directory khác? |

**Phân biệt thêm:** `ClassNotFoundException` là checked exception khi API tải class theo tên không tìm thấy. `NoClassDefFoundError` là lỗi liên kết/định nghĩa class ở runtime; một lỗi khởi tạo class có thể khiến những lần dùng tiếp theo báo dạng này. Đừng xem hai loại là từ đồng nghĩa.

## 12. Quy trình xử lý sự cố 6 bước

1. **Sao chép đầy đủ lỗi và command** gây lỗi (bao gồm `-cp`, `-jar`, thư mục hiện hành).
2. **Xác định giai đoạn**: `javac`, launcher tìm main class, nạp class phụ thuộc, hay initialization.
3. **Kiểm tra version**: `java -version`, `javac -version`, và `javap -verbose` với class liên quan nếu cần.
4. **Kiểm tra đường đi**: full name, package, classpath root, file `.class`/JAR và đường dẫn tương đối.
5. **Chọn một thay đổi nhỏ nhất**: sửa `-cp`, bổ sung dependency runtime, đồng bộ JDK hoặc đổi `--release`. Không thay đồng thời nhiều yếu tố rồi mất dấu nguyên nhân.
6. **Chạy lại trên môi trường đích**, lưu lệnh hoạt động và thêm kiểm tra smoke test vào hướng dẫn triển khai/CI.

### Ví dụ checklist nhanh cho demo

~~~bash
java -version
javac -version
javap -verbose -classpath out demo.Hello
java -cp out demo.Hello
~~~

Trên PowerShell, bốn lệnh này có cú pháp tương tự. Nếu `javap` không tìm được `demo.Hello`, ưu tiên kiểm tra **output root và tên lớp**. Nếu `javap` hiển thị major 61 nhưng runtime là Java 11, ưu tiên **nâng runtime hoặc build lại với release mà runtime hỗ trợ**.

## 13. Bài tập có đáp án

### Bài A — tìm lỗi classpath

Bạn có `out/demo/Hello.class`, package bên trong là `demo`. Chạy:

~~~bash
java -cp out/demo Hello
~~~

**Đáp án:** classpath root sai cho lớp `demo.Hello`; sửa thành `java -cp out demo.Hello`.

### Bài B — nhận diện lỗi version

Class có `major version: 65`, máy đích dùng JVM Java 17. Lý do có thể thất bại?

**Đáp án:** major 65 là Java 21, Java 17 thông thường chỉ hỗ trợ tới major 61. Có thể xuất hiện `UnsupportedClassVersionError`. Hướng sửa: chạy bằng runtime hỗ trợ Java 21, hoặc biên dịch lại cho release 17 sau khi bảo đảm code/API tương thích.

### Bài C — phân loại lỗi thiếu phụ thuộc

Build có `helper.jar`, lúc chạy quên đưa JAR vào classpath, ứng dụng chỉ lỗi khi gọi một tính năng cụ thể.

**Đáp án:** đây là nghi vấn **thiếu class dependency ở runtime**, có thể báo `NoClassDefFoundError`. Kiểm tra classpath runtime và stack trace; đừng chỉ sửa source hoặc tên main class.

### Bài D — phân biệt hai loại thông báo

1. `java -cp out demo.Hello` báo `Could not find or load main class demo.Hello`.
2. `java -cp out demo.Hello` tìm được class nhưng báo `Main method not found`.

**Đáp án:** (1) xem classpath root/tên đầy đủ/file; (2) xem chữ ký `main` của class đã tìm thấy. Hai trường hợp cần hai cách sửa khác nhau.

### Bài E — build bằng JDK mới, chạy JVM cũ

Bạn build bằng JDK 21, deploy lên Java 17. Nêu ba câu hỏi trước khi sửa.

**Đáp án gợi ý:** (i) class file major version hiện là bao nhiêu? (ii) source và API có tương thích Java 17 không? (iii) được nâng runtime đích hay phải dùng `--release 17`? Sau thay đổi, xác minh bằng `javap` và chạy smoke test trên JVM 17 thực tế.

## 14. Checklist nghiệm thu

- [ ] Phân biệt được **không tìm thấy main class**, **thiếu class phụ thuộc**, **sai tên package**, **không có main** và **không tương thích class-file version**.
- [ ] Biên dịch và chạy ví dụ `demo.Hello` bằng đúng classpath root.
- [ ] Biết `javac` và `java` là hai lệnh có vai trò khác nhau; kiểm tra được cả hai phiên bản.
- [ ] Dùng `javap -verbose` để đọc major version và hiểu 52/55/61/65.
- [ ] Biết Java 17 class (major 61) không chạy được trên JVM Java 11.
- [ ] Phân biệt build classpath và runtime classpath.
- [ ] Biết `java -jar` có cách xử lý classpath khác với class mode.
- [ ] Biết đọc dòng `Caused by` và lỗi initialization trước khi kết luận thiếu JAR.
- [ ] Có thể sửa từng nguyên nhân một và kiểm chứng lại trên runtime đích.

**Kết luận:** khi gặp lỗi Java lúc khởi động, hãy tách ba câu hỏi: **JVM đang tìm class tên nào? Tìm từ classpath root nào? Bytecode có tương thích với JVM đang chạy không?** Trả lời đúng ba câu đó sẽ loại bỏ phần lớn cách sửa mò như đổi tên file, chép JAR ngẫu nhiên hoặc thay phiên bản Java không có căn cứ.

</details>

- [Quay lại đầu trang](#back-to-top)
