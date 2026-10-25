<a id="back-to-top"></a>

# Vòng đời JVM

## Menu
- [Từ lệnh java đến tiến trình JVM và điểm vào chương trình](#jvm-lifecycle-001)
- [Chạy lớp, JAR, module và tệp nguồn: khác nhau ở cách khởi động](#jvm-lifecycle-002)
- [Phân biệt tham số JVM, thuộc tính hệ thống và đối số của ứng dụng](#jvm-lifecycle-003)
- [Khởi động, khởi tạo lớp đầu tiên và gọi main theo phiên bản Java](#jvm-lifecycle-004)
- [Các luồng của ứng dụng và các luồng nội bộ của JVM](#jvm-lifecycle-005)
- [Khi main kết thúc: luồng không daemon và điều kiện JVM thoát](#jvm-lifecycle-006)
- [Shutdown hook, System.exit và Runtime.halt trong quá trình kết thúc](#jvm-lifecycle-007)
- [Khác biệt giữa kết thúc bình thường, ngoại lệ không được bắt và JVM bị dừng cưỡng bức](#jvm-lifecycle-008)

## <a id="jvm-lifecycle-001">Từ lệnh java đến tiến trình JVM và điểm vào chương trình</a>

<details>
<summary>Xem chi tiết</summary>

# Từ lệnh `java` đến tiến trình JVM và điểm vào chương trình

## 1. Bức tranh tổng thể

Khi mở terminal và nhập `java demo.LifecycleApp`, ta không “gọi thẳng phương thức `main`” như gọi một phương thức bình thường từ mã nguồn. Lệnh này khởi động **một chương trình thực thi của hệ điều hành**, thiết lập một **tiến trình** chứa môi trường **JVM**, rồi JVM tìm, nạp, liên kết, khởi tạo lớp đầu vào và gọi `main`.

Vòng đời điển hình của ứng dụng Java chạy qua command line:

```text
Người dùng / script / IDE
          |
          | gõ hoặc chạy lệnh java [JVM options] MainClass [app args]
          v
Shell tìm chương trình java theo PATH (hoặc đường dẫn tuyệt đối)
          |
          v
Hệ điều hành khởi tạo tiến trình (process, có PID, không gian địa chỉ ...)
          |
          v
Java launcher phân tích tùy chọn và cấu hình runtime
          |
          v
Khởi động JVM trong tiến trình
          |
          v
Xác định lớp / JAR / module / tệp nguồn đầu vào
          |
          v
Nạp → liên kết → khởi tạo lớp chính
          |
          v
Gọi public static void main(String[] args)
          |
          v
Ứng dụng chạy: lời gọi phương thức, đối tượng, luồng, I/O ...
          |
          v
Điều kiện kết thúc JVM / yêu cầu thoát
          |
          v
Shutdown hooks (khi cơ chế shutdown thông thường được thực hiện)
          |
          v
JVM kết thúc, tiến trình trả mã thoát cho hệ điều hành
```

Đây là **luồng khái niệm**, không phải danh sách mọi hàm native nội bộ. Thứ tự, tên thành phần và cơ chế tạo JVM cụ thể có thể khác theo hệ điều hành, nhà cung cấp JVM và cách nhúng runtime. Trong trường hợp **khởi chạy một ứng dụng bằng lệnh `java` thông thường**, mô hình trên giúp lý giải phần lớn hành vi quan sát được.

Bài học tập trung vào ranh giới **lệnh shell → tiến trình hệ điều hành → Java launcher → JVM → lớp đầu vào → phương thức `main` → kết thúc tiến trình**.

## 2. Phân biệt bốn thực thể dễ bị nhầm lẫn

| Thực thể | Nó là gì? | Điều cần nhớ |
| --- | --- | --- |
| **Shell** | Chương trình nhận lệnh, chẳng hạn PowerShell, CMD, Bash | Phân tách cú pháp lệnh và yêu cầu hệ điều hành chạy một chương trình |
| **`java` launcher** | Chương trình thực thi đi cùng bản phân phối Java | Đọc tùy chọn, xác định kiểu khởi chạy và bắt đầu JVM |
| **Tiến trình (process)** | Đơn vị thực thi do hệ điều hành quản lý, có PID và tài nguyên riêng | Chứa mã native của launcher, JVM và mã ứng dụng đang chạy |
| **JVM** | Máy ảo thực thi bytecode và cung cấp cơ chế runtime | Nạp lớp, thực thi mã Java, quản lý nhiều hoạt động runtime |
| **`main`** | Phương thức đầu vào chuẩn của một ứng dụng Java kiểu class-launch | Là điểm vào **logic ứng dụng**, không phải điểm bắt đầu toàn bộ tiến trình hệ điều hành |

Ví dụ, hai cửa sổ terminal cùng chạy `java -cp out demo.LifecycleApp` sẽ **thông thường tạo hai tiến trình Java độc lập**, có PID và vùng nhớ khác nhau. Các trường `static` không tự động được chia sẻ qua hai tiến trình; “cùng tên lớp” không có nghĩa “cùng JVM”.

Một JVM có thể chạy **nhiều luồng (thread)** trong một tiến trình. Luồng chia sẻ nhiều tài nguyên của tiến trình, còn các tiến trình Java riêng biệt không chia sẻ heap theo cách thông thường. Đừng đồng nhất PID với thread ID hoặc cho rằng mỗi thread tạo ra một JVM mới.

## 3. Điều gì xảy ra ngay sau khi nhập lệnh `java`?

### 3.1. Shell tìm đúng chương trình `java`

Trong terminal, tên `java` thường được giải quyết theo cơ chế tìm chương trình của shell và hệ điều hành, thường liên quan đến biến môi trường `PATH`. Nếu chỉ định đường dẫn tuyệt đối đến `java` của một JDK cụ thể, ta tránh được nhiều nhầm lẫn do có nhiều JDK cùng cài đặt.

Các lệnh chẩn đoán:

```powershell
java -version
javac -version
where.exe java
Get-Command java
```

Đối với Bash hoặc shell tương tự:

```bash
java -version
javac -version
command -v java
```

`java -version` chỉ in thông tin runtime rồi thoát; nó **không yêu cầu** một lớp ứng dụng có `main`. `javac` là công cụ biên dịch mã nguồn. Hai lệnh có thể trỏ đến các bản cài đặt khác nhau nếu cấu hình `PATH` không nhất quán.

Khi tìm thấy launcher, shell yêu cầu hệ điều hành chạy nó. Trên các hệ thống phổ biến, hệ điều hành cấp cho tiến trình một định danh PID, không gian địa chỉ và các tài nguyên cần thiết. Cách shell đợi lệnh hoàn thành, chạy nền hoặc chuyển hướng đầu vào/đầu ra phụ thuộc vào shell; JVM không tự quyết định toàn bộ hành vi đó.

### 3.2. Launcher hiểu hai loại đối số khác nhau

Cú pháp class-launch cơ bản:

```text
java [tùy chọn của launcher/JVM] <tên lớp chính> [đối số của ứng dụng...]
```

Ví dụ:

```bash
java -Xmx256m -Dapp.mode=demo -cp out demo.LifecycleApp alpha "two words"
```

Ở đây:

- `java`: khởi chạy ứng dụng Java.
- `-Xmx256m`: tùy chọn đặt mức heap tối đa của JVM (ví dụ thuộc nhóm tùy chọn JVM, không phải tham số của `main`).
- `-Dapp.mode=demo`: thuộc tính hệ thống Java, có thể đọc qua `System.getProperty("app.mode")`.
- `-cp out`: dùng thư mục `out` làm đường dẫn tìm class thông thường.
- `demo.LifecycleApp`: tên **đầy đủ** (*fully qualified class name*) của lớp đầu vào, không phải tên tệp `.java` hoặc `.class`.
- `alpha` và `"two words"`: được shell truyền thành hai đối số của ứng dụng; launcher chuyển chúng vào mảng `String[] args` của `main`.

**Vị trí đối số rất quan trọng.**

```bash
java -Xmx256m -cp out demo.LifecycleApp alpha
```

Trong lệnh trên, `-Xmx256m` được launcher/JVM xử lý. Ngược lại:

```bash
java -cp out demo.LifecycleApp -Xmx256m
```

Ở lệnh sau, chuỗi `"-Xmx256m"` đứng **sau tên lớp chính**, vì vậy nó là **đối số ứng dụng**, không phải yêu cầu đặt heap. Tùy chọn JVM phải được đặt ở phần tùy chọn trước tên chương trình theo cú pháp tương ứng.

Cú pháp dấu nháy và việc tách chuỗi thành token xảy ra ở phía shell; Java nhận những chuỗi đã được chuyển tới launcher, không tự tách lại nội dung của dấu nháy như shell.

### 3.3. Launcher khởi tạo JVM và tìm chương trình cần chạy

Launcher chuẩn bị cấu hình runtime và khởi động JVM phù hợp. JVM sử dụng class loader và các cơ chế runtime để tìm định nghĩa lớp, liên kết lớp và thực thi mã. Đặc tả JVM mô tả ý nghĩa các bước; không nên suy diễn rằng mọi bản JVM luôn có một thứ tự chi tiết giống hệt nhau trong nội bộ.

Trong trường hợp chạy class thông thường:

```text
java -cp out demo.LifecycleApp
                    |
                    v
Classpath = out
                    |
                    v
Tìm out/demo/LifecycleApp.class
                    |
                    v
Nạp, liên kết, khởi tạo demo.LifecycleApp
                    |
                    v
Gọi demo.LifecycleApp.main(String[])
```

Đường dẫn tệp nêu trên là cách hình dung với thư mục lớp đã biên dịch. Trên Windows dấu phân cách thư mục có thể được hiển thị bằng `\`; class loader cũng có thể tìm class từ JAR hoặc nguồn lớp khác tùy cấu hình.

## 4. JVM chuẩn bị lớp chính trước khi gọi `main`

Theo mô hình của Java, khởi động một ứng dụng kiểu class-launch dẫn đến các bước quan trọng sau.

### Bước 1: Loading — nạp lớp

JVM cần định nghĩa lớp đầu vào ở dạng class file/bytecode hợp lệ. Class loader tìm và tạo biểu diễn runtime của lớp. Nếu lớp không nằm trên classpath/module path thích hợp hoặc tên đầy đủ bị sai, ứng dụng có thể dừng **trước khi vào `main`**.

Ví dụ: với `package demo;` và tên lớp `LifecycleApp`, launcher phải tìm `demo.LifecycleApp`. Gõ `java -cp out LifecycleApp` sẽ không tương đương.

### Bước 2: Linking — liên kết lớp

Liên kết gồm các hoạt động như:

1. **Verification:** xác minh tính hợp lệ của các cấu trúc class file và một số ràng buộc kiểu/bytecode.
2. **Preparation:** chuẩn bị vùng lưu trữ của các trường `static` và các giá trị mặc định theo đặc tả, trước khi mã khởi tạo tĩnh của ứng dụng được thực hiện.
3. **Resolution:** giải quyết tham chiếu ký hiệu như thông tin lớp/phương thức/trường thành thực thể runtime khi cần; một số resolution có thể được trì hoãn.

Không được kết luận rằng **mọi lớp phụ thuộc** sẽ được tải, giải quyết và khởi tạo ngay trước `main`. Có những công việc xảy ra khi chương trình bắt đầu sử dụng chúng.

### Bước 3: Initialization — khởi tạo lớp chính

Trước khi `main` được gọi, lớp khai báo phương thức đó phải được khởi tạo thành công. Điều này liên quan đến việc khởi tạo các lớp cha cần thiết, chạy bộ khởi tạo trường `static` và khối `static { ... }` theo quy tắc của ngôn ngữ.

Ví dụ:

```java
public class InitBeforeMain {
    static {
        System.out.println("static initializer");
    }

    public static void main(String[] args) {
        System.out.println("main method");
    }
}
```

Biên dịch và chạy:

```bash
javac InitBeforeMain.java
java InitBeforeMain
```

**Stdout chính xác khi chạy bình thường:**

```text
static initializer
main method
```

Điều này chứng minh: **`main` không phải câu lệnh Java đầu tiên có thể được thực thi trong chương trình**. Bộ khởi tạo tĩnh của lớp chính có thể chạy trước. Nếu khởi tạo thất bại, `main` có thể chưa được gọi.

### Bước 4: Invoke main — gọi phương thức đầu vào

Với mô hình class-launch chuẩn tương thích Java 17, phương thức đầu vào có chữ ký:

```java
public static void main(String[] args)
```

Hoặc cú pháp varargs tương đương về kiểu tham số:

```java
public static void main(String... args)
```

Ý nghĩa:

- `public`: phương thức có khả năng được gọi từ ngoài lớp theo hợp đồng điểm vào chuẩn.
- `static`: launcher không cần tự tạo một đối tượng của lớp ứng dụng để gọi phương thức.
- `void`: phương thức không trả một giá trị kết quả trực tiếp theo kiểu `int` để làm exit code.
- `String[] args`: chứa các đối số chương trình mà launcher chuyển tới sau tên lớp/JAR/module/tệp nguồn, không gồm các tùy chọn JVM đã xử lý.

Tên biến `args` **không bắt buộc**; có thể viết `String[] parameters`. Phương thức `main` cũng có thể gọi các phương thức khác hoặc khởi tạo framework, rồi ứng dụng mới thực sự bắt đầu xử lý yêu cầu.

## 5. Thí nghiệm hoàn chỉnh Java 17+: quan sát lifecycle, tham số và PID

Tạo cấu trúc thư mục sau:

```text
jvm-launch-demo/
└── src/
    └── demo/
        └── LifecycleApp.java
```

Nội dung **`src/demo/LifecycleApp.java`**:

```java
package demo;

import java.util.Arrays;

public final class LifecycleApp {
    static {
        System.out.println("1. class initialized");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("2. main entered");
        System.out.println("3. arguments: " + Arrays.toString(args));

        if (Arrays.asList(args).contains("--inspect")) {
            System.out.println("PID: " + ProcessHandle.current().pid());
            Thread.sleep(20_000);
        }

        System.out.println("4. main returning");
    }
}
```

Mở terminal ở thư mục **`jvm-launch-demo`** và chạy:

```bash
javac -d out src/demo/LifecycleApp.java
java -cp out demo.LifecycleApp alpha "two words"
```

**Stdout chính xác của lần chạy trên:**

```text
1. class initialized
2. main entered
3. arguments: [alpha, two words]
4. main returning
```

Giải thích từng điểm:

1. `javac -d out ...` biên dịch mã nguồn và tạo `out/demo/LifecycleApp.class`. `-d out` định vị thư mục gốc của cấu trúc package trong output.
2. `java -cp out demo.LifecycleApp ...` bắt đầu một **tiến trình Java mới**; `-cp out` chỉ ra nơi tìm lớp.
3. Trước khi `main` được gọi, JVM khởi tạo lớp nên dòng `1. class initialized` xuất hiện trước.
4. Launcher truyền hai tham số vào mảng `args`. Vì đã đặt dấu nháy quanh `"two words"`, đó là một tham số duy nhất gồm khoảng trắng.
5. Sau dòng `4. main returning`, phương thức `main` kết thúc bình thường. Nếu không còn luồng non-daemon nào giữ JVM hoạt động, quá trình shutdown bắt đầu.

Đổi đối số:

```bash
java -cp out demo.LifecycleApp
```

Khi đó dòng `3` là:

```text
3. arguments: []
```

**Quan sát PID và tiến trình đang sống:** chạy:

```bash
java -cp out demo.LifecycleApp --inspect
```

Lần này chương trình sẽ in thêm một dòng dạng `PID: <số thay đổi theo lần chạy>` rồi tạm ngủ khoảng 20 giây. **Không có một giá trị PID cố định** để đưa vào expected stdout, vì hệ điều hành cấp phát PID ở mỗi lần chạy.

Trong lúc tiến trình còn sống, có thể quan sát nó bằng Task Manager/Process Explorer trên Windows hoặc các công cụ quản lý tiến trình tương ứng trên Linux/macOS. Khi có JDK, thử lệnh:

```bash
jps -l
```

Công cụ `jps` có thể hiển thị tiến trình JVM đang chạy; trên một số môi trường/container hoặc với quyền truy cập hạn chế, nó không liệt kê được mọi tiến trình. Tìm tiến trình trước khi khoảng ngủ kết thúc. Có thể dùng `jcmd <PID> VM.command_line` khi `jcmd` có sẵn và đủ quyền; thay `<PID>` bằng giá trị in thực tế. Không nhập nguyên ký tự `<`/`>` trong lệnh shell.

**Quan sát quan hệ tiến trình:** chạy ứng dụng hai lần, mỗi lần với `--inspect`, từ hai terminal. Thông thường chúng in hai PID khác nhau. Mỗi lần class `LifecycleApp` được khởi tạo trong JVM riêng, nên khối `static` sẽ chạy trong mỗi tiến trình. Không tồn tại một bộ biến `static` chung tự động nối hai lần chạy.

## 6. Bốn cách chỉ định điểm vào cho Java launcher

Bốn chế độ khác nhau ở **cách tìm điểm vào và dependencies**, không thay đổi nguyên tắc thực thi bytecode, điều kiện kết thúc JVM hoặc ý nghĩa `main`.

| Chế độ | Câu lệnh minh họa | Cơ chế chọn lớp ban đầu |
| --- | --- | --- |
| **Class** | `java -cp out demo.LifecycleApp alpha` | Binary class name nằm trên classpath |
| **JAR** | `java -jar lifecycle-app.jar alpha` | `Main-Class` trong JAR manifest |
| **Named module** | `java --module-path mods -m com.example.app/com.example.app.Main alpha` | Resolve module và main class, cần module thực |
| **Source-file** | `java src/demo/LifecycleApp.java alpha` | Launcher Java 11+ biên dịch file nguồn rồi chạy class kết quả |

Với mã `LifecycleApp.java` đã biên dịch trong mục 5, có thể thử trực tiếp JAR mode:

~~~bash
jar --create --file lifecycle-app.jar --main-class demo.LifecycleApp -C out .
java -jar lifecycle-app.jar alpha "two words"
~~~

Các dòng output nghiệp vụ của `LifecycleApp` giống khi chạy class mode với cùng `args`. Với `-jar`, không được mặc định `-cp` cung cấp dependencies theo cùng cách class mode; hãy xem manifest và packaging. Module mode cần descriptor `module-info.java`, `--module-path` và artifact module phù hợp; ví dụ bảng không phải project module hoàn chỉnh. Source-file mode vẫn biên dịch class trong bộ nhớ, **không** phải JVM đọc trực tiếp cú pháp Java source.

Trên classpath có nhiều mục, Windows dùng dấu phân cách `;` và Linux/macOS dùng `:`. Bài chuyên đề về bốn chế độ launch phân tích sâu cơ chế này; ở bài tổng quan, điều cốt lõi là launcher có thể chọn điểm vào khác nhau **trước khi** chuyển quyền điều khiển cho cùng logic `main`.

## 7. `main` trả về chưa chắc tiến trình JVM đã kết thúc

Nhiều người thấy `main` là “điểm vào” rồi tưởng rằng **hễ `main` return thì JVM tắt ngay**. Điều này sai khi vẫn còn các **non-daemon thread** đang chạy.

Các điều kiện shutdown quan trọng theo API Java:

- JVM bắt đầu shutdown khi **không còn luồng non-daemon đang sống** (xét theo điều kiện của runtime), ngay cả khi trước đó `main` đã return.
- Lời gọi `System.exit(status)` yêu cầu runtime bắt đầu tiến trình shutdown với mã trạng thái.
- Một số sự kiện từ bên ngoài, ví dụ tín hiệu ngắt phù hợp, có thể khiến runtime bắt đầu shutdown; các cách cưỡng bức chấm dứt tiến trình không bảo đảm cùng hành vi.

Ví dụ **`BackgroundWorker.java`**:

```java
public class BackgroundWorker {
    public static void main(String[] args) {
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(1_000);
                System.out.println("worker finished");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "background-worker");

        worker.start(); // non-daemon theo mặc định khi được tạo từ main thread chuẩn
        System.out.println("main is finishing");
    }
}
```

Chạy:

```bash
javac BackgroundWorker.java
java BackgroundWorker
```

Trong lần chạy thông thường, hai dòng được in:

```text
main is finishing
worker finished
```

Dòng từ luồng worker thường xuất hiện sau khoảng một giây; thời điểm thực tế do hệ điều hành và scheduler quyết định. Điểm quan trọng là tiến trình **đợi luồng non-daemon** hoàn tất. Trên lý thuyết thời điểm các luồng được lập lịch có thể thay đổi; đừng dùng thứ tự log không có đồng bộ hóa làm chứng cứ tuyệt đối cho mọi lịch chạy.

Nếu `worker.setDaemon(true)` được gọi **trước** `worker.start()`, luồng đó không còn tự giữ JVM sống. Khi tất cả non-daemon thread đã kết thúc, daemon thread có thể bị dừng trong quá trình JVM kết thúc, kể cả khi nó chưa in dòng nào. Đừng dùng daemon thread cho tác vụ cần bảo đảm hoàn thành và lưu kết quả.

**Còn framework thì sao?** Một chương trình Spring Boot có thể khởi động server, thread pool và những thành phần duy trì vòng đời ứng dụng. Phương thức `main` thường gọi `SpringApplication.run(...)`; chuyện tiến trình tiếp tục sống phụ thuộc các luồng và cơ chế của ứng dụng/framework, không phải “`main` đang lặp vô hạn”.

## 8. Khi nào tiến trình thoát? Return, exit và dừng cưỡng bức

| Đường kết thúc | Điều kiện | Cleanup / kết quả |
| --- | --- | --- |
| **Tự nhiên** | `main` và non-daemon threads còn lại kết thúc | JVM bắt đầu shutdown, chạy registered hooks |
| **`System.exit(status)`** | Ứng dụng yêu cầu toàn JVM shutdown | Chạy hooks theo sequence; không tự đợi mọi worker hoặc unwind mọi `finally` |
| **`Runtime.halt(status)`** | Cưỡng bức dừng VM ngay | Bỏ qua hoặc cắt ngang hooks; nguy cơ mất dữ liệu |
| **Uncaught Java exception** | Thread mang lỗi chết trước | Không tự đồng nghĩa toàn JVM crash; các non-daemon khác có thể chạy tiếp |
| **OS kill / native fatal crash** | Process bị dừng ngoài đường Java thường | Không được bảo đảm hooks, logs hoặc cleanup |

Theo quy ước, `0` là exit status thành công, nonzero thường biểu thị lỗi, nhưng exit code không tự nói nguyên nhân dừng. Kiểu `void` của `main` Java 17 truyền thống không tự trả exit status; có thể gọi `System.exit(2)` khi cần một mã trạng thái, song nên đóng tài nguyên quan trọng **trước** lời gọi đó.

Đăng ký hook trong lúc JVM đang hoạt động bằng `Runtime.getRuntime().addShutdownHook(new Thread(...))`. Các hooks chạy đồng thời, không có thứ tự cố định; một hook đợi vô hạn có thể khiến shutdown treo. Khi bị `SIGKILL`, `Runtime.halt` hoặc mất điện, không được trông chờ hooks chạy. Dùng resource scopes, transactions và recovery thay vì coi hook là nơi duy nhất bảo vệ dữ liệu.

Như vậy **trả về từ main**, **một thread kết thúc**, **JVM shutdown có trình tự** và **tiến trình bị cưỡng bức dừng** là bốn sự kiện khác nhau. Những bài riêng trong chương đi sâu vào daemon/executor lifecycle, shutdown hooks và lỗi/exit-status.

## 9. Các lỗi thường gặp trước và sau `main`

| Biểu hiện | Thời điểm / nguyên nhân thường gặp | Hướng kiểm tra |
| --- | --- | --- |
| `java` không được nhận diện | Shell chưa tìm thấy launcher | Kiểm tra JDK, `PATH`, đường dẫn tuyệt đối |
| `Could not find or load main class` | Sai tên lớp đầy đủ hoặc sai classpath | Kiểm tra `package`, `-cp` và vị trí `.class` |
| `Main method not found` | Không có điểm vào đúng hợp đồng của phiên bản launcher | Kiểm tra chữ ký `public static void main(String[] args)` |
| `UnsupportedClassVersionError` | Class file biên dịch cho phiên bản mới hơn JVM đang chạy | Kiểm tra `java -version` và cấu hình biên dịch như `--release` |
| `NoClassDefFoundError` | Class phụ thuộc không khả dụng khi runtime cần sử dụng | Kiểm tra classpath/module path và dependency runtime |
| `ExceptionInInitializerError` | Lỗi không được xử lý trong khởi tạo tĩnh | Đọc nguyên nhân gốc từ stack trace và mã `static` |
| `No main manifest attribute` | JAR không chỉ định main class | Kiểm tra manifest, lệnh tạo JAR, hoặc dùng class mode |
| Chương trình đã in “main done” nhưng vẫn chạy | Còn non-daemon thread, server, executor, hook… | Xem thread dump/log, quản lý vòng đời thread và tài nguyên |
| JVM dừng quá sớm, worker chưa xong | Task chạy trên daemon thread hoặc bị cưỡng bức kết thúc | Đồng bộ tác vụ, đóng executor có kiểm soát, xem exit code |

Một lỗi phát sinh **trước `main`** thường thuộc các bước tìm lớp, liên kết, khởi tạo lớp, cấu hình hoặc kiểm tra điểm vào. Một lỗi **sau `main`** có thể do nghiệp vụ, luồng, tác vụ nền, I/O hoặc shutdown. Phân loại đúng thời điểm giúp tìm nguyên nhân nhanh hơn.

## 10. Kỹ thuật kiểm tra thực tế

**Kiểm tra Java nào đã chạy:** dùng `java -version` và cách tra đường dẫn lệnh của shell (PowerShell `Get-Command java` hoặc Bash `command -v java`). Không mặc định `JAVA_HOME` luôn quyết định chính xác launcher khi `PATH` chứa nhiều JDK.

**Xác minh class và JAR:** với ví dụ đã biên dịch, kiểm tra thư mục `out/demo/LifecycleApp.class`. Có thể dùng `jar --list --file lifecycle-app.jar` để xem các mục trong JAR và `jar --describe-module --file ...` khi đang điều tra artifact dạng module phù hợp. Cần kiểm tra manifest nếu `java -jar` không tìm ra main class.

**Quan sát tiến trình hiện tại:** `ProcessHandle.current().pid()` trong Java 17+ trả PID của tiến trình hiện tại. Cùng với `jps`/`jcmd` (khi môi trường cho phép), điều này giúp phân biệt một tiến trình Java chưa thoát với một lần chạy khác vừa khởi động lại.

**Chẩn đoán JVM không thoát:** kiểm tra thread đang sống và trạng thái daemon/non-daemon bằng thread dump hoặc debugger; tìm thread pool/executor không được đóng, server listener vẫn hoạt động hoặc shutdown hook đang treo. Đừng sửa bằng cách gọi `System.exit(0)` một cách máy móc vì có thể bỏ qua quy trình hoàn tất công việc đang chạy.

**Xem lệnh mà JVM nhận:** với JDK có hỗ trợ và có quyền phù hợp, lệnh `jcmd <PID> VM.command_line` hữu ích để đối chiếu JVM options với tham số khởi động; `jcmd <PID> Thread.print` có thể hỗ trợ kiểm tra thread. Đây là công cụ chẩn đoán gắn với triển khai/JDK, nên khả năng hỗ trợ có thể khác nhau.

**Khi chọn phiên bản Java:** nếu cần chạy trên runtime Java 17, có thể dùng JDK biên dịch hỗ trợ tùy chọn `--release 17`:

```bash
javac --release 17 -d out src/demo/LifecycleApp.java
```

Tùy chọn này đặt mức ngôn ngữ, class file và bề mặt Java SE API đích trong phạm vi compiler hỗ trợ; vẫn phải kiểm tra các thư viện bên thứ ba, môi trường hệ điều hành và cấu hình runtime riêng.

## 11. Tự kiểm tra

**Câu 1.** Trong lệnh `java -Xmx256m -cp out demo.LifecycleApp hello`, tham số của `main` là gì?

**Đáp án:** `args` gồm một chuỗi `"hello"`. Các tùy chọn `-Xmx256m` và `-cp out` thuộc launcher/runtime.

**Câu 2.** Vì sao chương trình có thể in nội dung từ khối `static` trước câu lệnh đầu tiên trong `main`?

**Đáp án:** JVM khởi tạo lớp chính, thực thi các bộ khởi tạo tĩnh cần thiết trước khi gọi `main`.

**Câu 3.** Hai terminal chạy cùng một class; liệu chúng cùng chia sẻ một biến `static int counter`?

**Đáp án:** Thông thường không. Hai lần khởi chạy `java` tạo hai tiến trình/JVM với vùng nhớ độc lập.

**Câu 4.** `main` đã return nhưng tiến trình vẫn xuất hiện trong Task Manager. Có chắc JVM bị treo không?

**Đáp án:** Không. Một thread non-daemon, server, executor hoặc shutdown hook có thể vẫn đang hoạt động. Phải quan sát thread và vòng đời ứng dụng trước khi kết luận.

**Câu 5.** Vì sao `java -jar app.jar` khác `java -cp app.jar demo.Main`?

**Đáp án:** `-jar` lấy lớp đầu vào từ `Main-Class` của manifest; class mode dùng tên lớp chính trên dòng lệnh và classpath. Quy tắc classpath khi dùng `-jar` cũng khác với class mode.

**Câu 6.** Nếu tệp `.class` đã được biên dịch, máy đích nhất thiết phải có `javac` không?

**Đáp án:** Không. Nó cần một môi trường runtime/JVM tương thích để chạy class và các dependency cần thiết; `javac` chỉ cần cho bước biên dịch.

## 12. Tổng kết bằng một chuỗi trách nhiệm

```text
Shell              : chọn và gọi chương trình java
Hệ điều hành       : tạo/quản lý tiến trình, PID, tài nguyên
Java launcher      : diễn giải options, chọn class/JAR/module/source
JVM                : chuẩn bị runtime, nạp/liên kết/khởi tạo lớp, thực thi bytecode
main(String[])     : điểm vào logic của ứng dụng; nhận application args
Các thread ứng dụng: quyết định công việc tiếp tục chạy sau main hay không
Shutdown           : điều kiện kết thúc, hooks, mã thoát của tiến trình
```

Nắm ranh giới này giúp giải thích **vì sao class không tìm thấy, vì sao khối `static` chạy trước `main`, vì sao `main` return mà JVM còn sống, vì sao PID thay đổi qua mỗi lần chạy, và vì sao `java -jar` khác `java -cp`**. Đây là nền tảng để học sâu hơn về class loading, memory areas, thread lifecycle, JIT và chẩn đoán JVM.

### Tài liệu chính thức tham khảo

- [The `java` Command — JDK 21](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): chế độ khởi chạy class/JAR/module/source và cú pháp đối số.
- [Java Language Specification `12: Execution](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html): nạp, liên kết, khởi tạo lớp và lời gọi `main`.
- [`java.lang.Runtime` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Runtime.html): shutdown sequence, hooks, exit và halt.
- [`java.lang.Thread` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): thread và quan hệ với điều kiện JVM shutdown.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-lifecycle-002">Chạy lớp, JAR, module và tệp nguồn: khác nhau ở cách khởi động</a>

<details>
<summary>Xem chi tiết</summary>

# Chạy lớp, JAR, module và tệp nguồn: khác nhau ở cách khởi động JVM

Một chương trình Java có thể được khởi chạy qua **tên lớp**, **JAR có điểm vào**, **module có tên**, hoặc trực tiếp bằng **tệp mã nguồn `.java`**. Bốn cách đều nhằm tìm được mã cần thực thi và gọi điểm vào của chương trình, nhưng khác nhau ở **đối tượng mà Java launcher nhận vào**, **cách tìm lớp và phụ thuộc**, **cách chọn `main`**, và việc **có cần biên dịch trước hay không**.

Ở đây, “khởi chạy JVM” nghĩa là chạy lệnh `java` trong JDK/runtime tương thích. Chúng ta khảo sát bốn chế độ với cú pháp được hỗ trợ trên **Java 17 trở lên**, tập trung vào vòng đời từ câu lệnh khởi chạy tới `main`. Để giữ ví dụ chạy được nhất quán, phương thức vào chương trình dùng chữ ký quen thuộc của Java 17: `public static void main(String[] args)`.

## 1. Bức tranh chung: launcher chọn cách tìm chương trình

```text
                    Người dùng gọi lệnh java
                               │
          ┌────────────────────┼─────────────────────┐
          │                    │                     │
   Tên lớp trên           JAR có manifest        Module có tên
     classpath                Main-Class            trong module path
   java -cp ...             java -jar ...             java -m ...
          │                    │                     │
          └─────────────┬──────┴─────────────────────┘
                        │
                 Tìm và nạp main class
                        │
                        ▼
             JVM liên kết/khởi tạo lớp
                        │
                        ▼
                   Gọi main(args)
                        │
                        ▼
                 Thực thi bytecode
             (interpreter/JIT tùy JVM)

  Đường thứ tư: java File.java → biên dịch mã nguồn trong bộ nhớ
                     → nạp main class → chạy như trên
```

Mô hình trên mô tả **chọn chế độ launch và chuyển quyền điều khiển vào ứng dụng**, không có nghĩa tất cả dependency được nạp cùng lúc. JVM còn thực hiện khởi tạo runtime, phân giải module khi dùng module, nạp/liên kết lớp khi cần, quản lý luồng và bộ nhớ, rồi thực thi bytecode theo cơ chế của bản JVM. Việc gọi `main` cũng không đồng nghĩa toàn bộ vòng đời JVM kết thúc ngay khi phương thức này trả về: các luồng còn sống và các điều kiện kết thúc tiến trình vẫn ảnh hưởng thời điểm JVM thoát.

### Bảng so sánh nhanh

| Chế độ | Câu lệnh mẫu | Đầu vào trực tiếp | Cách xác định điểm vào | Cần biên dịch trước? |
| --- | --- | --- | --- | --- |
| **Class** | `java -cp out demo.LaunchDemo` | Tên lớp đầy đủ | Tên lớp trên classpath và `main` của lớp đó | Thông thường có |
| **Executable JAR** | `java -jar app.jar` | Đường dẫn JAR | Thuộc tính `Main-Class` trong manifest | Có; thường còn cần đóng gói JAR |
| **Named module** | `java --module-path mods -m com.example.launch/demo.LaunchDemo` | Tên module và (tùy chọn) lớp main | Module được resolve, sau đó chọn main class chỉ định hoặc cấu hình sẵn | Có; module descriptor và class file |
| **Source file** | `java src/demo/LaunchDemo.java` | Đường dẫn tệp `.java` | Java launcher biên dịch mã nguồn trong bộ nhớ, rồi thực thi lớp chính trong tệp | **Không cần chạy `javac` riêng** |

Một JAR thông thường chứa các tệp `.class` và tài nguyên; **JAR không phải mã máy**. “Chạy JAR” là một cách phân phối/chọn điểm vào. “Chạy module” là cách tổ chức và phân giải module theo Java Platform Module System (JPMS), không phải một kiểu CPU mới.

## 2. Chuẩn bị một chương trình dùng chung cho cả bốn cách

Tạo thư mục dự án và tệp mã nguồn sau (đường dẫn tính từ thư mục dự án):

```text
project/
└── src/
    └── demo/
        └── LaunchDemo.java
```

Nội dung **`src/demo/LaunchDemo.java`**:

```java
package demo;

public class LaunchDemo {
    public static void main(String[] args) {
        String mode = args.length == 0 ? "không truyền" : args[0];
        System.out.println("Chế độ: " + mode);
    }
}
```

Lớp nằm trong package `demo`, vì thế **tên đầy đủ của lớp là `demo.LaunchDemo`**. Chương trình chỉ in tham số đầu tiên truyền cho `main`; điều này giúp phân biệt lệnh `java` xử lý **launcher options** hay **application arguments**. Các ví dụ dưới đây đều chạy từ thư mục `project/`, sử dụng JDK 17+, với tập tin `LaunchDemo.java` được lưu UTF-8.

```bash
java --version
javac --version
```

Nếu máy có nhiều JDK, hãy kiểm tra hai lệnh dùng đúng phiên bản mong muốn trước khi đọc lỗi của chương trình.

## 3. Chế độ 1 — Chạy một lớp bằng tên đầy đủ

### Biên dịch

```bash
mkdir out
javac -encoding UTF-8 -d out src/demo/LaunchDemo.java
```

Lệnh `javac` kiểm tra mã nguồn và tạo:

```text
out/
└── demo/
    └── LaunchDemo.class
```

Tham số `-d out` xác định nơi đặt class file; cấu trúc bên dưới gắn với tên package. Trong trường hợp này, thư mục **`out` mới là classpath root**, còn `out/demo` chỉ là thư mục package của lớp.

### Chạy

```bash
java -cp out demo.LaunchDemo class
```

**Standard output:**

```text
Chế độ: class
```

Phân tích lệnh:

- `java`: khởi động Java launcher và runtime.
- `-cp out` (viết đầy đủ `-classpath out` hoặc `--class-path out`): cung cấp nơi tìm các lớp/tài nguyên thuộc **classpath**.
- `demo.LaunchDemo`: **binary name** của main class có package, không phải đường dẫn hệ thống.
- `class`: tham số ứng dụng thứ nhất, nhận tại `args[0]`, không phải tên lớp hoặc một tùy chọn JVM.

Các lớp ứng dụng trên classpath thuộc **unnamed module**. Classpath không cần khai báo `module-info.java` và không có ranh giới `requires`/`exports` của named module theo JPMS.

**Vì sao `java LaunchDemo` có thể không chạy?** Lớp mẫu khai báo `package demo;`, nên tên cần chạy là `demo.LaunchDemo`. Cũng không dùng `java out/demo/LaunchDemo.class` ở chế độ này: launcher đang đòi **tên lớp**, không phải đường dẫn tệp `.class`.

### Khi nào dùng?

Chạy trực tiếp theo tên lớp tiện cho bài học, thử nhanh chương trình đã biên dịch, hoặc khi công cụ build/IDE quản lý classpath và biết chính xác main class. Với chương trình nhiều thư viện, classpath cần bao gồm đầy đủ thư mục/JAR phụ thuộc tại runtime. Trên nhiều hệ điều hành, dấu phân tách các mục classpath khác nhau: thường là `;` ở Windows và `:` ở Linux/macOS. Nếu dùng các đường dẫn phức tạp, đặt chuỗi classpath trong dấu nháy phù hợp shell.

## 4. Chế độ 2 — Chạy JAR có `Main-Class`

Từ **class file đã tạo ở bước 3**, đóng gói JAR:

```bash
jar --create --file app.jar --main-class demo.LaunchDemo -C out .
```

Lệnh này tạo `app.jar` chứa lớp `demo/LaunchDemo.class` và manifest có **`Main-Class: demo.LaunchDemo`**. Dấu `-C out .` có nghĩa là đổi vào thư mục `out` trong lúc thêm nội dung, để bên trong JAR là đường dẫn package `demo/LaunchDemo.class`, không thêm tiền tố `out/`.

Kiểm tra các mục trong JAR:

```bash
jar --list --file app.jar
```

Danh sách thường bao gồm `META-INF/MANIFEST.MF` và `demo/LaunchDemo.class`. Sự tồn tại của class file **không tự động làm một JAR chạy được bằng `-jar`**: manifest phải định danh main class phù hợp.

Khởi chạy:

```bash
java -jar app.jar jar
```

**Standard output:**

```text
Chế độ: jar
```

Ở chế độ này, launcher mở JAR và tìm `Main-Class` trong manifest để biết phải khởi chạy lớp nào. Nhờ đó người dùng không cần nhớ tên `demo.LaunchDemo`. `jar` đứng sau đường dẫn JAR nên trở thành `args[0]`.

### Classpath khi dùng `-jar`

Một điểm đặc biệt: với `java -jar app.jar`, **JAR được chỉ định là nguồn tìm lớp ứng dụng chính** và các thiết lập classpath bên ngoài từ `-cp`/`CLASSPATH` không được dùng như ở chế độ khởi chạy tên lớp. Có thể khai báo các JAR phụ thuộc thông qua manifest `Class-Path` hoặc dùng chiến lược đóng gói/phân phối tương ứng. Đừng cho rằng thêm `-cp libs/...` vào câu lệnh `java -jar` sẽ nạp dependency như khi gọi `java -cp`.

Ngược lại, một JAR **không có `Main-Class`** vẫn có thể được dùng trên classpath:

```bash
java -cp app.jar demo.LaunchDemo classpath-jar
```

**Standard output:**

```text
Chế độ: classpath-jar
```

Lần này bạn chủ động chọn **tên lớp**, dùng JAR làm một mục classpath; manifest main class không cần thiết cho lời gọi kiểu `-cp`.

### Khi nào dùng?

Executable JAR phù hợp để phân phối ứng dụng có entry point rõ ràng. Tuy nhiên JAR **không mặc nhiên chứa tất cả thư viện ngoài**. Một “fat JAR” do công cụ build tạo có thể mang cấu trúc riêng hoặc launcher chuyên biệt; cần xem hướng dẫn của đúng công cụ, không suy diễn từ ví dụ JAR thuần ở đây.

## 5. Chế độ 3 — Chạy named module bằng `--module-path` và `-m`

**Java Platform Module System (JPMS)** xuất hiện từ Java 9. Named module có tên và descriptor `module-info.java`; runtime có thể dùng thông tin phụ thuộc (`requires`) và mức độ công khai giữa các module (`exports`, `opens`) khi phân giải và truy cập thành phần. Đây là khác biệt trọng yếu so với classpath.

### Thêm descriptor

Tạo tệp **`src/module-info.java`** bên cạnh cây package `src/demo/`:

```java
module com.example.launch {
}
```

Cây mã nguồn:

```text
project/
└── src/
    ├── module-info.java
    └── demo/
        └── LaunchDemo.java
```

Module này dùng `java.base` mặc định nên không cần ghi `requires java.base;`. Cũng **không cần `exports demo;` chỉ để Java launcher khởi chạy main class của chính module**. `exports` dùng để điều khiển các package được module khác truy cập; không phải một chỉ thị chọn main class.

### Biên dịch thành exploded module

```bash
mkdir mods
mkdir mods/com.example.launch
javac -encoding UTF-8 -d mods/com.example.launch src/module-info.java src/demo/LaunchDemo.java
```

Sản phẩm:

```text
mods/
└── com.example.launch/
    ├── module-info.class
    └── demo/
        └── LaunchDemo.class
```

Thư mục `mods/com.example.launch` là một **exploded module** (module đã biên dịch ở dạng thư mục); `mods` là đường dẫn chứa các module.

### Chạy module và chỉ rõ lớp chính

```bash
java --module-path mods -m com.example.launch/demo.LaunchDemo module
```

**Standard output:**

```text
Chế độ: module
```

Giải thích:

- `--module-path mods` (viết gọn `-p mods`) cung cấp nơi JVM tìm các **observable modules** cần dùng.
- `-m` (tương đương `--module`) chọn module khởi chạy.
- `com.example.launch/demo.LaunchDemo` ghép **tên module** và **tên lớp main** bằng dấu `/`. Đây không phải đường dẫn tệp.
- `module` cuối câu lệnh được đưa vào `args[0]`.

Trong ví dụ này, **module name** là `com.example.launch`, còn **package/class name** là `demo.LaunchDemo`. Hai tên không cần trùng nhau.

Ngoài cú pháp `-m ten.module/ten.lop.Main`, một modular JAR hoặc module có thể có main class được cấu hình trong module descriptor; khi đó có thể chạy `java -m ten.module`. Descriptor `module-info.java` viết ở trên **chưa cấu hình main class**, nên bài này **phải** chỉ rõ `/demo.LaunchDemo`.

### Module path và classpath khác nhau ở đâu?

- **Classpath** liệt kê các thư mục/JAR chứa lớp; với các class thông thường, launcher tìm main class bằng tên lớp trên classpath.
- **Module path** giúp runtime quan sát các module có tên, đọc module descriptor, phân giải dependency và áp dụng ranh giới truy cập module.
- Cùng một chương trình có thể tương tác cả classpath và module path nếu cấu hình phù hợp, nhưng không được coi đây là hai danh sách đường dẫn có hành vi hoàn toàn giống nhau.
- **Một JAR thông thường đặt trên module path** có thể được xem như automatic module theo quy tắc JPMS; hành vi khác với modular JAR có `module-info.class`. Không phải mọi JAR trên module path đều có descriptor do tác giả tự khai báo.

Một module thiếu dependency được khai báo hoặc không thể resolve có thể thất bại **trước khi tới `main`**. Bởi vậy lỗi liên quan module path/descriptor là lỗi ở giai đoạn lập đồ thị module hoặc nạp/liên kết lớp, khác với việc `main` ném exception khi đã chạy.

### Đóng gói thành modular JAR (mở rộng)

Từ exploded module đã biên dịch, có thể tạo modular JAR và chọn main class cho nó:

```bash
jar --create --file launch-module.jar --main-class demo.LaunchDemo -C mods/com.example.launch .
java --module-path launch-module.jar -m com.example.launch modular-jar
```

**Standard output của lệnh `java`:**

```text
Chế độ: modular-jar
```

JAR trên có `module-info.class` nên được nhận diện là modular JAR. Tùy chọn `--main-class` của lệnh `jar` đặt thông tin lớp chính trong metadata phù hợp; do đó lời gọi `-m com.example.launch` có thể bỏ phần `/demo.LaunchDemo`. **Đừng lẫn** biểu diễn main class trong manifest khi chạy `-jar` với metadata main class dùng khi khởi chạy **named module**: phải xem chính xác cách công cụ đóng gói thiết lập mỗi loại.

## 6. Chế độ 4 — Chạy trực tiếp tệp nguồn bằng `java File.java`

Từ **Java 11**, Java launcher hỗ trợ **source-file mode**: biên dịch tệp nguồn trong bộ nhớ rồi thực thi lớp chính mà **không cần chạy riêng `javac` để tạo class file trên đĩa**.

Với tệp `src/demo/LaunchDemo.java` đã tạo ở bước 2:

```bash
java src/demo/LaunchDemo.java source
```

**Standard output:**

```text
Chế độ: source
```

Khác với `java -cp out demo.LaunchDemo`, đối số sau `java` ở đây là **đường dẫn tệp thực sự**, có đuôi `.java`; launcher nhận ra source-file mode, sử dụng compiler trong runtime để biên dịch nguồn vào bộ nhớ rồi chạy lớp chính. Việc khai báo `package demo;` trong tệp nguồn này không khiến bạn phải thay đường dẫn tệp bằng `demo.LaunchDemo` khi dùng source-file mode.

```text
java src/demo/LaunchDemo.java source
          │
          ├─ Nhận diện tệp nguồn / chọn source-file mode
          ├─ Biên dịch tệp nguồn trong bộ nhớ
          ├─ Nạp lớp chính đã biên dịch
          └─ Gọi main(new String[]{"source"})
```

**Không cần** thấy `out/demo/LaunchDemo.class` sau lệnh này: mặc định việc biên dịch phục vụ chính lần khởi chạy, không xuất class file bằng `-d out` như `javac`. Nếu tệp source mắc lỗi cú pháp hoặc lỗi kiểu, lỗi biên dịch có thể xuất hiện **ngay trong lệnh `java File.java`**, dù bạn không gọi `javac` tường minh.

Có thể chỉ rõ phiên bản ngôn ngữ nguồn (miễn JVM/JDK hỗ trợ):

```bash
java --source 17 src/demo/LaunchDemo.java source-17
```

**Standard output:**

```text
Chế độ: source-17
```

Một số cách viết nguồn hoặc API cần classpath/module path tương ứng. Source-file mode phù hợp cho ví dụ nhỏ, script và thử nghiệm; nó **không tự động giải quyết dependency, build nhiều module, tạo JAR phân phối hay thay thế toàn bộ quy trình build**. Với Java 17, hãy xem đây chủ yếu là cách chạy **một source file làm entry point**, không giả định nó tự quét và biên dịch cả cây dự án nhiều tệp.

### Phân biệt với `java demo.LaunchDemo`

| Câu lệnh | Launcher hiểu đối số là gì? |
| --- | --- |
| `java src/demo/LaunchDemo.java` | **Đường dẫn tệp mã nguồn**, đi qua source-file mode |
| `java -cp out demo.LaunchDemo` | **Tên lớp đã biên dịch** trên classpath |
| `java -cp out src/demo/LaunchDemo.java` | Đối số vị trí launch vẫn là đường dẫn tệp `.java`, do đó có thể kích hoạt source-file mode; `-cp out` là tùy chọn runtime/compiler chứ không đổi nó thành binary class name |
| `javac src/demo/LaunchDemo.java` | Lệnh của **compiler**, không phải lệnh khởi chạy JVM để gọi `main` |

## 7. Điểm khác nhau thực sự nằm ở giai đoạn nào trong vòng đời?

Từng chế độ thay đổi **cách tìm điểm vào** và **khâu chuẩn bị trước khi gọi `main`**, nhưng những bước JVM cốt lõi vẫn tồn tại.

### 7.1. Giai đoạn launch: phân tích lệnh và chọn entry point

Launcher đọc các tùy chọn JVM/launcher được đặt **trước launch target** rồi quyết định chế độ:

```text
java [options] <main-class> [application-arguments]
java [options] -jar <jar-file> [application-arguments]
java [options] -m <module>[/<main-class>] [application-arguments]
java [options] <source-file>.java [application-arguments]
```

Các dấu ngoặc nhọn/góc trong sơ đồ là ký hiệu mô tả, **không gõ nguyên văn vào shell**. Với cú pháp thật ở bước 3–6, `class`, `jar`, `module`, `source` lần lượt trở thành `args[0]`.

Ví dụ tùy chọn JVM phải nằm trước lớp cần chạy:

```bash
java -Xmx256m -cp out demo.LaunchDemo class
```

Kết quả vẫn là `Chế độ: class`. Nếu đặt `-Xmx256m` **sau** `demo.LaunchDemo`, chương trình nhận nó như chuỗi `args[0]`, còn bộ nhớ tối đa JVM không được cấu hình bằng đối số ấy.

### 7.2. Giai đoạn tìm và chuẩn bị lớp

- **Class launch:** loader dùng classpath để định vị `demo.LaunchDemo`.
- **JAR launch:** launcher đọc `Main-Class` từ manifest của JAR và sử dụng cách tìm lớp phù hợp chế độ `-jar`.
- **Module launch:** runtime phân giải named module từ module path, xét descriptor/dependency và tìm lớp main trong module.
- **Source-file launch:** compiler biên dịch mã nguồn trong bộ nhớ, sau đó runtime có thể nạp lớp kết quả để thực thi.

Sau khi tìm được định nghĩa lớp, JVM thực hiện **loading**, **linking** (bao gồm verification/preparation và resolution khi cần), **initialization** theo quy tắc JVM. Runtime thực thi static initialization thích hợp trước khi chạy phần tương ứng của chương trình. Không phải mọi lớp phụ thuộc đều được nạp/khởi tạo ngay lúc startup; nhiều việc diễn ra lười khi chương trình sử dụng tới.

### 7.3. Giai đoạn thực thi

Khi `main` được gọi thành công, cả bốn chế độ dùng cùng ngữ nghĩa Java cho chương trình mẫu. Bytecode có thể được interpreter xử lý hoặc JIT tối ưu/biên dịch thành mã máy; cách phân phối lớp qua thư mục, JAR, module hay source file **không tự quyết định chương trình luôn chạy bằng interpreter hay JIT**.

Trong ví dụ này, bốn kiểu khởi chạy chỉ khác **giá trị tham số truyền vào** nên có các dòng stdout khác nhau. Nếu truyền cùng một tham số, logic `LaunchDemo.main` cho cùng kết quả.

### 7.4. Giai đoạn kết thúc

`main` kết thúc không bảo đảm JVM kết thúc ngay nếu vẫn còn non-daemon threads. Khi toàn bộ điều kiện kết thúc tiến trình thỏa mãn hoặc `System.exit(...)` được gọi, runtime thực hiện quy trình shutdown thích hợp. Điều này đúng cho cả bốn kiểu launch; không có quy tắc “JAR tắt JVM khác class” chỉ vì định dạng đóng gói.

## 8. Các lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Chế độ hay liên quan | Kiểm tra |
| --- | --- | --- |
| `Could not find or load main class` | Class | `-cp` có chỉ **thư mục gốc package**? Tên có đúng `demo.LaunchDemo`? Class đã biên dịch chưa? |
| `no main manifest attribute` | `-jar` | JAR có khai báo `Main-Class` chưa? Đã dùng `jar --main-class` hoặc quy trình tạo manifest thích hợp chưa? |
| `ClassNotFoundException` / `NoClassDefFoundError` | Class/JAR và các biến thể | Dependency có tại runtime không? Classpath có phù hợp và đầy đủ không? Có lỗi khởi tạo lớp trước đó không? |
| Không tìm thấy module hoặc module không resolve | `-m` | Đúng tên module? `--module-path` trỏ đúng `mods`/modular JAR? Dependency trong `requires` có đủ? |
| Không tìm thấy main class bên trong module | `-m` | Đã ghi `-m com.example.launch/demo.LaunchDemo` hay đặt main-class metadata? |
| Lỗi `javac` ngay khi chạy `java File.java` | Source-file mode | Sửa cú pháp/kiểu mã nguồn và dependency; vì launcher đang biên dịch trong bộ nhớ |
| `UnsupportedClassVersionError` | Chạy class/JAR/module | Class file được biên dịch cho Java mới hơn runtime? Kiểm tra `java --version` và `javap -verbose` |
| Lựa chọn bộ nhớ `-Xmx` không có tác dụng | Mọi chế độ | Tùy chọn JVM có bị đặt **sau launch target** và trở thành tham số ứng dụng? |

### Bộ lệnh kiểm tra nhỏ

```bash
javap -classpath out demo.LaunchDemo
jar --list --file app.jar
java --describe-module java.base
java --version
```

- `javap` xác nhận lớp đã biên dịch và signature `main`.
- `jar --list` xem các entry của JAR; có thể kiểm tra manifest khi gặp vấn đề về `Main-Class`.
- `java --describe-module java.base` cho thấy Java runtime hiểu khái niệm named module và cung cấp module nền tảng; với ứng dụng cụ thể, mô tả module mục tiêu theo cấu hình module path phù hợp.
- `java --version` kiểm tra runtime đang dùng khi gặp lỗi tương thích bytecode.

## 9. Chọn cách khởi chạy phù hợp

| Nhu cầu | Cách thường hợp lý | Lý do |
| --- | --- | --- |
| Học nhanh luồng `.java → .class → JVM`, quan sát `javap` | `javac`, rồi `java -cp ... main.Class` | Nhìn rõ sản phẩm biên dịch và việc tìm class theo package |
| Chuyển một chương trình có entry point cho người khác chạy | `java -jar app.jar` | Một đường dẫn JAR và entry point ở manifest; vẫn phải đóng gói/cấp dependency đúng |
| Dự án thực sự thiết kế theo JPMS, muốn khai báo dependency/encapsulation module | `java --module-path ... -m ...` | Runtime phân giải module và áp dụng ranh giới module |
| Thử một source file ngắn trên JDK phù hợp | `java File.java` | Không cần lệnh `javac` riêng, ít bước thao tác |
| Muốn kiểm tra tác động của việc đóng gói | Chạy **cùng lớp** bằng `-cp`, `-jar`, `-m` | Giúp tách lỗi launch/dependency khỏi lỗi của `main` |

Tránh quyết định chỉ dựa trên tên file: **một JAR có thể được dùng như mục classpath hoặc executable JAR; một modular JAR cũng có thể được launch qua module path**. Lệnh `java` mới cho biết launcher đang chọn cơ chế nào.

## 10. Bài thực hành kiểm chứng bốn đường vào

Sử dụng đúng một mã nguồn `LaunchDemo.java` ở mục 2 và thực hiện:

1. **Class mode:** biên dịch vào `out/`; chạy `java -cp out demo.LaunchDemo class`; đọc `Chế độ: class`.
2. **JAR mode:** tạo `app.jar` từ `out/`; chạy `java -jar app.jar jar`; đọc `Chế độ: jar`.
3. **Module mode:** thêm `module-info.java`, biên dịch vào `mods/com.example.launch/`; chạy `java --module-path mods -m com.example.launch/demo.LaunchDemo module`; đọc `Chế độ: module`.
4. **Source-file mode:** chạy trực tiếp `java src/demo/LaunchDemo.java source`; đọc `Chế độ: source`.

Thử có kiểm soát:

- Bỏ `-cp out` ở lời gọi tên lớp và quan sát khả năng tìm lớp thay đổi ra sao. Kết quả còn phụ thuộc current directory và biến môi trường classpath; vì vậy hãy xem cấu hình thực tế.
- Đổi lệnh `java -jar app.jar jar` thành `java -cp app.jar demo.LaunchDemo classpath-jar`; chương trình chạy với cùng bytecode nhưng launcher nhận **tên lớp** thay vì đọc `Main-Class`.
- Ở module mode, thay tên `com.example.launch` thành tên không tồn tại; lỗi xảy ra khi resolve/chọn module, **trước** khi logic `main` bắt đầu.
- Đưa một lỗi kiểu vào source (chẳng hạn `int n = "sai";` trong `main`) và chạy source-file mode: lần này lệnh `java` báo vấn đề biên dịch vì compiler được gọi ngầm. Sau khi sửa, chạy lại để khôi phục chương trình hợp lệ.

### Tự kiểm tra

**1.** `java -jar app.jar` sẽ khởi chạy lớp `demo.LaunchDemo` dù manifest không có `Main-Class`? **Không**; chế độ `-jar` cần entry point thích hợp trong manifest.

**2.** Vì sao `java -cp out demo.LaunchDemo` dùng dấu chấm nhưng `java src/demo/LaunchDemo.java` dùng dấu gạch chéo? Câu đầu dùng **tên lớp trong package**, câu sau dùng **đường dẫn tệp mã nguồn**.

**3.** `java File.java` bỏ qua hoàn toàn biên dịch? **Không**; source-file mode vẫn biên dịch mã nguồn, chỉ là **không yêu cầu người dùng chạy `javac` riêng và không cần xuất class file ra đĩa**.

**4.** Có phải chỉ ứng dụng module mới chạy trên JVM hỗ trợ JIT? **Không**; JIT là cơ chế thực thi bytecode của JVM, độc lập lựa chọn class/JAR/module/source-file launch.

**5.** Khi nào tham số `-Xmx256m` trở thành tham số ứng dụng? Khi đặt **sau** tên main class/JAR/module/source-file launch target; launcher đã chuyển phần đối số tiếp theo vào `main(String[] args)`.

Điểm then chốt để ghi nhớ: **class mode** chọn main class từ classpath; **JAR mode** chọn qua manifest; **module mode** chọn bằng tên module và main class hoặc metadata module; **source-file mode** biên dịch mã nguồn trong bộ nhớ rồi chạy. Chỉ cần nhìn chính xác target và các đường dẫn của lệnh `java`, bạn sẽ khoanh vùng phần lớn lỗi “không khởi động được” trước khi đi sâu vào code của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-lifecycle-003">Phân biệt tham số JVM, thuộc tính hệ thống và đối số của ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt tham số JVM, thuộc tính hệ thống và đối số của ứng dụng

## 1. Mục tiêu và ngữ cảnh

Khi khởi chạy một chương trình Java, nhiều thông tin có thể xuất hiện trên cùng một dòng lệnh: giới hạn bộ nhớ, lựa chọn classpath, giá trị cấu hình, đường dẫn tệp đầu vào, chế độ hoạt động và cờ riêng của chương trình. Chúng trông giống nhau vì cùng là các chuỗi ký tự trong terminal, nhưng **khác nhau về người xử lý, thời điểm xử lý, phạm vi ảnh hưởng và API truy cập**.

Sau bài học, bạn cần:

- Phân biệt **tùy chọn khởi chạy Java/JVM** (launcher/VM options), **thuộc tính hệ thống Java** (system properties) và **đối số ứng dụng** (application arguments).
- Đặt đúng vị trí các tham số trong lệnh **java**, hiểu chính xác vai trò của **-D**.
- Đọc system property bằng **System.getProperty(...)** và đọc đối số thông qua **String[] args**.
- Hiểu cấu hình nào phải áp dụng khi tạo JVM, cấu hình nào có thể thay đổi khi chương trình đang chạy.
- Chẩn đoán tình huống tham số bị chuyển nhầm vào **args**, giá trị không được áp dụng hoặc phụ thuộc vào shell, công cụ build và môi trường.

Bài học tập trung vào quá trình khởi động một tiến trình Java. Một ứng dụng, framework hoặc script khởi chạy có thể tự định nghĩa quy tắc cấu hình bổ sung, nhưng các quy tắc đó phải được nhận diện riêng.

## 2. Một dòng lệnh, ba loại thông tin

Cú pháp phổ biến khi gọi một lớp có phương thức **main** là:

~~~text
java [tùy chọn của java/VM] <TênLớpMain> [đối số ứng dụng...]

Ví dụ:
java -Xmx256m -Ddemo.mode=dev -cp out Main --input=data.csv
     |       |              |       |    |
     |       |              |       |    +-- đối số của ứng dụng
     |       |              |       +------- tên lớp main
     |       |              +--------------- classpath cho Java launcher
     |       +------------------------------ tùy chọn đặt system property
     +-------------------------------------- tùy chọn cấu hình VM
~~~

Điểm phân cách là **tên lớp main** (hoặc đích khởi chạy như **-jar app.jar**). Những token ở phía sau đích khởi chạy được chuyển vào chương trình làm đối số ứng dụng. Java launcher xử lý các tùy chọn của mình trước khi gọi **main(String[] args)**. Shell cũng có giai đoạn xử lý dấu nháy, khoảng trắng và escape trước khi tiến trình **java** nhận được các token.

| Loại | Ví dụ | Ai xử lý trực tiếp? | Cách truy cập/quan sát |
| --- | --- | --- | --- |
| **Tùy chọn JVM/Java launcher** | **-Xmx256m**, **-ea**, **-cp out**, **-XX:+UseG1GC** | Java launcher và/hoặc VM khi khởi động | Hành vi VM, công cụ quản trị/quan sát; không nằm trong **args** nếu đặt đúng chỗ |
| **System property** | **-Ddemo.mode=dev** tạo property **demo.mode** với giá trị **dev** | Tùy chọn **-D** được launcher xử lý, sau đó JVM cung cấp property cho chương trình | **System.getProperty("demo.mode")** |
| **Đối số ứng dụng** | **--input=data.csv**, **--dry-run**, **report.csv** | **main(...)** hoặc framework/parser do ứng dụng dùng | **String[] args** |

**Quan hệ quan trọng:** **-D** vừa là **cú pháp tùy chọn của lệnh java**, vừa là cách **khởi tạo một system property**. “System property” là dữ liệu cấu hình nằm trong tiến trình Java; nó không phải một dạng tùy chọn tinh chỉnh VM như **-Xmx**. Không nên ép ba nhóm thành các tập hoàn toàn không giao nhau ở cấp độ cú pháp.

Tương tự, **-cp** là tùy chọn của **Java launcher** quyết định nơi tìm class, chứ không phải tham số điều chỉnh heap. Trong giao tiếp hằng ngày, các tùy chọn đặt trước main đôi khi đều được gọi chung là “tham số JVM”; khi phân tích lỗi cần phân biệt **tùy chọn VM** với **tùy chọn launcher**.

## 3. JVM options: cấu hình quá trình khởi chạy

Các tùy chọn này được tiêu thụ khi Java launcher/JVM chuẩn bị tiến trình, và thường không được chuyển cho **main(String[] args)**.

### 3.1. Ví dụ tiêu biểu

| Tùy chọn | Ý nghĩa thực tế | Ghi chú |
| --- | --- | --- |
| **-Xms128m** | Yêu cầu kích thước heap ban đầu | Tùy chọn phổ biến của HotSpot; việc cấp phát bộ nhớ vật lý thực tế phụ thuộc VM/OS |
| **-Xmx512m** | Đặt giới hạn kích thước heap tối đa cho Java heap | Không đồng nghĩa giới hạn toàn bộ RAM tiến trình; còn metaspace, stacks, native memory... |
| **-ea** hoặc **-enableassertions** | Bật assertion theo phạm vi mặc định/quy định của tùy chọn | Chỉ có tác dụng với các câu lệnh **assert**; không thay thế kiểm tra dữ liệu đầu vào |
| **-XX:+UseG1GC** | Chọn G1 GC trên HotSpot hỗ trợ tùy chọn này | Thuộc nhóm tùy chọn VM mang tính triển khai; cần kiểm tra JDK/VM đang dùng |
| **-cp out** hoặc **-classpath out** | Chỉ định classpath khi chạy lớp main | Do launcher xử lý; cú pháp và ý nghĩa khác với các tham số ứng dụng |
| **-Dapp.region=apac** | Đặt system property **app.region** | Là tùy chọn launcher để cung cấp dữ liệu thuộc tính cho Java |

Những tùy chọn bắt đầu bằng **-X** hoặc **-XX** không nên được mặc định xem là ổn định trên mọi phiên bản và mọi nhà cung cấp JVM. Cờ có thể không được hỗ trợ, đổi tên hoặc đổi ý nghĩa giữa các runtime. Khi một cờ không hợp lệ, chương trình có thể kết thúc **trước khi main được gọi**.

### 3.2. Thời điểm áp dụng và phạm vi

Các cấu hình như giới hạn heap hoặc chọn GC phải được thiết lập khi khởi động JVM tương ứng. Gọi:

~~~java
// Mã minh họa, không phải chương trình Java độc lập.
System.setProperty("Xmx", "1g");
~~~

**không** tăng heap tối đa lên 1 GB, bởi **Xmx** trong ví dụ chỉ là tên một system property tự tạo; JVM không diễn giải property đó thành tùy chọn **-Xmx**. Tương tự, chuỗi **"-Xmx1g"** xuất hiện trong **args** không tự thay đổi VM.

Một VM đang chạy có thể hỗ trợ những cơ chế quản trị động nhất định (phụ thuộc triển khai/cờ), nhưng điều đó không khiến tất cả startup options trở thành cấu hình có thể thay đổi bằng Java API.

## 4. System properties: bảng tên–giá trị trong tiến trình Java

**System properties** là tập cặp khóa/giá trị chuỗi do lớp **java.lang.System** cung cấp. Có property do runtime thiết lập, như **java.version**, **os.name**, **user.dir**; cũng có property do người khởi chạy đặt, ví dụ **demo.profile**, **app.port**.

Các thao tác thường dùng:

~~~java
// Các dòng minh họa đặt trong một phương thức Java.
String profile = System.getProperty("demo.profile");
String fallback = System.getProperty("demo.profile", "local");
System.setProperty("demo.profile", "test");
System.clearProperty("demo.profile");
~~~

- **System.getProperty(key)** trả về giá trị hiện có; khi khóa không tồn tại, kết quả là **null**.
- **System.getProperty(key, defaultValue)** trả về giá trị mặc định nếu khóa không có.
- **System.setProperty(key, value)** đổi giá trị trong cùng tiến trình JVM, trả về giá trị cũ hoặc **null** nếu trước đó chưa có; các phần khác trong tiến trình có thể quan sát thấy thay đổi.
- **System.clearProperty(key)** xóa property và trả về giá trị cũ nếu có.
- Java cũng cho phép truy cập tập properties qua **System.getProperties()**; không nên in toàn bộ tập này ra log khi có thể chứa dữ liệu nhạy cảm.

**-Ddemo.profile=dev** là cách thông dụng để khởi tạo property trước khi **main** chạy:

~~~bash
java -Ddemo.profile=dev Main
~~~

Tên **demo.profile** do ứng dụng tự chọn; JVM không biết “dev” nghĩa là gì nếu không có thành phần nào đọc property ấy. **-D** tự nó không tạo một hệ thống cấu hình có phân cấp, kiểu dữ liệu, validation hay cơ chế reload.

### 4.1. Thuộc tính không đồng nghĩa tùy chọn VM

Hãy so sánh:

~~~text
-Xmx512m                   JVM xử lý để đặt giới hạn heap
-Ddemo.maxItems=512        Tạo property tên demo.maxItems, giá trị chuỗi "512"
--max-items=512            Đối số ứng dụng nếu đặt sau main/JAR
~~~

Ba dòng này không thay thế được cho nhau. Application phải tự đọc, chuyển kiểu và kiểm tra **demo.maxItems**, còn **--max-items=512** chỉ có ý nghĩa nếu chính ứng dụng hoặc framework hiểu nó.

Một system property có thể được thay đổi lúc chạy, nhưng **không có bảo đảm** tất cả thư viện sẽ tự đọc lại giá trị mới. Một thư viện có thể đã chụp giá trị khi khởi tạo; JVM/OS cũng có những cấu hình được xác định từ trước khi **main** bắt đầu. Tránh dựa vào việc đổi property runtime để thay đổi hành vi hệ thống đã khởi tạo.

### 4.2. Thuộc tính hệ thống khác biến môi trường

Đây là hai kho dữ liệu khác nhau:

| Nội dung | System property | Environment variable |
| --- | --- | --- |
| API đọc | **System.getProperty("demo.profile")** | **System.getenv("DEMO_PROFILE")** |
| Nguồn phổ biến | Java launcher **-D**, runtime, code Java | Môi trường tiến trình do OS, shell, container hoặc process launcher truyền vào |
| Phạm vi | Trong JVM; có thể được mã Java thay đổi qua System API | Environment mà tiến trình nhận khi khởi động; Java không có API chuẩn để sửa environment của tiến trình theo cách tương đương **setProperty** |
| Quy ước tên | Ví dụ **demo.profile** | Ví dụ **DEMO_PROFILE** |
| Tự động ánh xạ giữa hai bên? | **Không** | **Không** |

Ví dụ, việc thiết lập biến môi trường **DEMO_PROFILE=dev** không khiến **System.getProperty("demo.profile")** tự trả về **dev**. Nếu muốn có quy tắc ánh xạ, ứng dụng hoặc framework phải lập trình điều đó.

Biến môi trường **JAVA_TOOL_OPTIONS** hoặc **JDK_JAVA_OPTIONS** là một trường hợp riêng: chúng có thể tác động tới JVM/launcher theo quy tắc của runtime, nhưng chúng vẫn **không** phải system properties và không đồng nghĩa với đối số ứng dụng. Chúng có giới hạn riêng và có thể khiến cờ xuất hiện “bất ngờ” dù không được gõ trực tiếp trên dòng lệnh.

## 5. Application arguments: dữ liệu được chuyển cho main

Với lớp Java truyền thống:

~~~java
public static void main(String[] args) {
    // args chứa các token nằm sau tên lớp main trong lệnh java.
}
~~~

Phần tử **args[0]** là đối số đầu tiên của chương trình, **không phải** tên lớp main, không phải **java**, và không chứa các tùy chọn JVM đã được launcher xử lý đúng vị trí.

Ví dụ:

~~~bash
java Main --mode=batch input.csv
~~~

Nếu chương trình được khởi chạy thành công, **args** có hai phần tử: **"--mode=batch"** và **"input.csv"**. Java SE không tự quy định ý nghĩa của **--mode**, **--input**, **--dry-run** hay **--port** trong **args**; đó là cú pháp do ứng dụng hoặc thư viện CLI thiết kế.

Các đối số thường phục vụ việc chọn chế độ chạy, tệp đầu vào, tác vụ, cổng nghiệp vụ và tùy chọn một lần cho quá trình chạy. Không nên tự suy luận rằng mọi đối số có dấu gạch ngang đều là JVM option.

### 5.1. Shell quyết định cách tạo ra từng token

Ví dụ:

~~~bash
java Main "first report.csv"
~~~

Với shell thông dụng hỗ trợ cú pháp nháy kép này, đối số ứng dụng là **một chuỗi** có chứa khoảng trắng: **first report.csv**. Nếu bỏ dấu nháy, shell có thể tách thành **hai** đối số là **first** và **report.csv**. Quy tắc escape và quoting chi tiết khác nhau giữa PowerShell, Command Prompt, Bash và môi trường container; khi cần tính khả chuyển, hãy kiểm tra shell đích.

Một số công cụ như IDE, Gradle hay container runtime có hai trường cấu hình tách biệt cho **VM options** và **program arguments**. Điền đúng giá trị nhưng sai trường vẫn có thể khiến Java nhận sai loại thông tin.

## 6. Ví dụ chạy được: quan sát cả ba loại

Tạo tệp **ConfigLayersDemo.java** (Java 17+):

~~~java
public class ConfigLayersDemo {
    public static void main(String[] args) {
        String profile = System.getProperty("demo.profile", "(unset)");
        String retries = System.getProperty("demo.retries", "(unset)");

        System.out.println("profile=" + profile);
        System.out.println("retries=" + retries);
        System.out.println("argCount=" + args.length);

        for (int i = 0; i < args.length; i++) {
            System.out.println("arg[" + i + "]=" + args[i]);
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 ConfigLayersDemo.java
java -Xmx256m -Ddemo.profile=dev -Ddemo.retries=3 ConfigLayersDemo --mode=batch report.csv
~~~

**Stdout chính xác của chương trình:**

~~~text
profile=dev
retries=3
argCount=2
arg[0]=--mode=batch
arg[1]=report.csv
~~~

Tại sao?

1. **-Xmx256m** được JVM xử lý trước khi gọi **main**. Nó không xuất hiện trong **args** và chương trình này không in giới hạn heap.
2. **-Ddemo.profile=dev** và **-Ddemo.retries=3** được dùng để đặt system properties. Hai lệnh **System.getProperty(...)** đọc ra chuỗi tương ứng.
3. **ConfigLayersDemo** là đích khởi chạy; Java launcher nạp lớp này và gọi **main**.
4. Hai token theo sau đích khởi chạy là **--mode=batch** và **report.csv**, được giữ nguyên trong **args**. Chương trình chỉ in chúng, chưa có logic xử lý chế độ batch hay đọc tệp.

### 6.1. Cùng chuỗi -D nhưng khác vị trí

Chạy:

~~~bash
java ConfigLayersDemo -Ddemo.profile=dev
~~~

**Stdout chính xác:**

~~~text
profile=(unset)
retries=(unset)
argCount=1
arg[0]=-Ddemo.profile=dev
~~~

Ở đây, **-Ddemo.profile=dev** đứng **sau** tên lớp main nên là dữ liệu của chương trình. Java không đặt property **demo.profile** trong trường hợp này. Lời giải chính xác là di chuyển **-D** lên trước **ConfigLayersDemo**, thay vì thay đổi code đọc property. Hai output trong mục này giả định không có cấu hình môi trường khác đặt sẵn hai property demo và không có mã ngoài ví dụ sửa chúng.

### 6.2. Sai vị trí của -Xmx

~~~bash
java ConfigLayersDemo -Xmx256m
~~~

Trong lệnh này, **-Xmx256m** được truyền vào **args[0]**; heap không được cấu hình theo token đó. Ngược lại:

~~~bash
java -Xmx256m ConfigLayersDemo
~~~

mới yêu cầu JVM đặt giới hạn heap tối đa tương ứng. Đừng xác nhận kích thước heap chính xác bằng cách lấy một số cứng từ output: VM có thể làm tròn/điều chỉnh giá trị và toàn bộ bộ nhớ tiến trình bao gồm nhiều vùng ngoài Java heap.

## 7. Với file JAR, điểm phân cách nằm ở đâu?

Ứng dụng đóng gói thành JAR có thể được khởi chạy theo cú pháp:

~~~text
java [tùy chọn launcher/JVM] -jar application.jar [đối số ứng dụng...]
~~~

**-jar** cùng đường dẫn **application.jar** chọn artifact chạy, thường dựa vào **Main-Class** trong manifest để tìm main class. Đây là cú pháp minh họa; muốn chạy được, JAR thực tế phải có main class hợp lệ và những dependency cần thiết.

Ví dụ đặt đúng vị trí:

~~~bash
java -Xmx512m -Ddemo.profile=prod -jar application.jar --mode=batch input.csv
~~~

- JVM nhận yêu cầu heap **-Xmx512m**.
- System property **demo.profile** được khởi tạo với **prod**.
- Ứng dụng nhận **--mode=batch** và **input.csv** theo cơ chế đối số của main.

So sánh lệnh:

~~~bash
java -jar application.jar -Xmx512m -Ddemo.profile=prod
~~~

Hai token phía sau **application.jar** là đối số ứng dụng. Chúng **không tự động** cấu hình heap hoặc property. Nếu framework cố tình phân tích chuỗi dạng **-D...** như một quy ước riêng, đó là logic ứng dụng, không phải hành vi mặc định của Java launcher.

**Lưu ý:** Khi khởi chạy bằng **-jar**, tùy chọn classpath **-cp** không được dùng theo cùng cách như khi chạy một lớp main trực tiếp; việc tìm class trong JAR dựa vào cơ chế riêng của launcher và metadata/dependency. Không xem **-jar** chỉ đơn giản là thay tên lớp trong mọi trường hợp.

## 8. Ứng dụng tự quy định cách diễn giải args và độ ưu tiên

**String[] args** chỉ chứa chuỗi. Java không tự chuyển **--port=8081** thành số nguyên, không tự xác minh cổng nằm trong khoảng hợp lệ và không tự quyết định **args** hay **-Ddemo.port** được ưu tiên. Ứng dụng cần nêu quy tắc.

Ví dụ độc lập tiếp theo dùng chiến lược cụ thể: mặc định **8080**, nếu có system property **demo.port** thì lấy nó, và nếu có đối số **--port=...** thì đối số được ưu tiên. Tham số **--dry-run** bật chế độ chạy thử.

Tạo **ApplicationArgsDemo.java**:

~~~java
public class ApplicationArgsDemo {
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getProperty("demo.port", "8080"));
        boolean dryRun = false;

        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.equals("--dry-run")) {
                dryRun = true;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }

        System.out.println("port=" + port);
        System.out.println("dryRun=" + dryRun);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ApplicationArgsDemo.java
java -Ddemo.port=9000 ApplicationArgsDemo --port=8081 --dry-run
~~~

**Stdout chính xác:**

~~~text
port=8081
dryRun=true
~~~

Cấu hình **9000** từ system property đã được đọc trước, nhưng đối số ứng dụng **--port=8081** ghi đè theo logic **for** do tác giả ứng dụng cài đặt. Đây là **quy tắc của ví dụ**, không phải quy luật ưu tiên bắt buộc trong Java SE.

Thử bỏ đối số port:

~~~bash
java -Ddemo.port=9000 ApplicationArgsDemo --dry-run
~~~

**Stdout chính xác:**

~~~text
port=9000
dryRun=true
~~~

Nếu không truyền **-Ddemo.port** và không có **--port=...**, chương trình lấy **8080**. Trong ví dụ học tập, giá trị port không phải số sẽ ném **NumberFormatException**; đối số không biết hoặc port ngoài 1–65535 sẽ ném **IllegalArgumentException**. Ứng dụng thực tế thường bắt lỗi, hiển thị hướng dẫn sử dụng và kết thúc với mã lỗi phù hợp.

Một framework có thể dùng tên tham số hoặc thứ tự ưu tiên khác; ví dụ Spring Boot có cơ chế command-line properties riêng. Không được áp dụng thứ tự ưu tiên cấu hình của một framework cho mọi chương trình Java.

## 9. Vị trí, vòng đời và phạm vi ảnh hưởng

Dòng thời gian khái quát:

~~~text
Người dùng / script / IDE / container
          |
          v
Shell hoặc công cụ khởi chạy xây dựng các token
          |
          v
Java launcher phân tích options và xác định main class/JAR
          |
          +--> VM options: cấu hình JVM tại khởi động
          |
          +--> -D: khởi tạo system properties
          |
          v
JVM khởi tạo, tải/liên kết/khởi tạo lớp theo nhu cầu
          |
          v
Gọi public static void main(String[] args)
          |
          +--> System.getProperty(...) đọc bảng properties
          |
          +--> args chứa các token chương trình nhận
          |
          v
Ứng dụng phân tích args, khởi tạo thành phần và chạy nghiệp vụ
~~~

Cần hiểu đây là sơ đồ khái quát, không phải danh sách mọi giai đoạn JVM theo thứ tự tuyệt đối: launcher và JVM có nhiều công việc nội bộ, lớp có thể được nạp trước hoặc sau main tùy nhu cầu.

| Câu hỏi | JVM/launcher option | System property | Application argument |
| --- | --- | --- | --- |
| Được cung cấp lúc chạy ở đâu? | Trước đích main/JAR | Thường bằng **-D** trước đích main/JAR | Sau đích main/JAR |
| Thời điểm xử lý đầu tiên | Trước khi chương trình vào main | Khi VM khởi tạo property; ứng dụng có thể đọc về sau | Khi main/framework nhận và phân tích |
| Ai sở hữu ý nghĩa? | Java launcher/JVM cụ thể | JVM sở hữu bảng property; ứng dụng/thư viện quy định ý nghĩa key tùy biến | Ứng dụng/framework sở hữu cú pháp và hành vi |
| Có tự nằm trong **args**? | Không, nếu đặt đúng vị trí | Không, đối với **-D** đã xử lý trước đích main | Có |
| Có thể sửa khi chạy? | Phần lớn tùy chọn startup không sửa qua một system property | Có API **setProperty/clearProperty**, nhưng thành phần dùng nó không nhất thiết reload | Có thể thay đổi dữ liệu logic do ứng dụng tạo sau khi parse; mảng **args** ban đầu không được Java tự “cập nhật” |

### 9.1. System properties được dùng chung trong một JVM

Các thành phần trong cùng tiến trình thường truy cập cùng bảng system properties. Nếu thư viện A gọi **System.setProperty("demo.profile", "test")**, mã B đọc bằng **System.getProperty(...)** về sau có thể thấy giá trị mới. Cách dùng property tùy tiện để lưu trạng thái nghiệp vụ động có thể tạo phụ thuộc ngầm và tình trạng tranh chấp giữa các thư viện/test.

Trong trường hợp ứng dụng có nhiều thread, việc đọc/ghi property phải được đánh giá theo tính nhất quán và cách thư viện tiêu thụ cấu hình; ngay cả khi bản thân thao tác bảng properties được đồng bộ an toàn, **cấu hình ứng dụng nhiều trường** không tự trở thành một giao dịch nguyên tử.

### 9.2. JVM options thường thuộc về một tiến trình cụ thể

Một máy có thể chạy nhiều tiến trình Java, mỗi tiến trình có options, properties và args riêng. Truyền **-Xmx512m** cho tiến trình A không tự thay đổi heap của B. Việc đặt **-Dapp.mode=dev** trong lệnh khởi chạy một chương trình cũng không biến nó thành system property của các tiến trình Java khác.

Ví dụ, lệnh **java** chạy ứng dụng do Gradle gọi có thể thuộc một tiến trình khác với **Gradle daemon**. Đặt **-Ddemo.profile=dev** trên lệnh gọi Gradle không mặc nhiên bảo đảm một tác vụ **JavaExec** hay ứng dụng được fork sẽ kế thừa property đó; cần cấu hình đúng nơi công cụ tạo tiến trình con cho phép.

## 10. Cách quan sát và chẩn đoán

### 10.1. Quan sát runtime và system properties

Trong mã Java:

~~~java
// Mã minh họa đặt trong một phương thức:
System.out.println(System.getProperty("java.version"));
System.out.println(System.getProperty("demo.profile", "(unset)"));
~~~

**java.version** phản ánh runtime đang thực thi. **demo.profile** phản ánh giá trị system property tại lúc gọi. Không có một output cố định cho **java.version** trên mọi máy.

Từ command line, công cụ **java -version** cho biết phiên bản/runtime nhưng không thay cho thử nghiệm chạy ứng dụng. Với HotSpot/JDK hỗ trợ, bạn có thể dùng các lệnh chẩn đoán:

~~~bash
java -XshowSettings:properties -version
java -XX:+PrintFlagsFinal -version
~~~

Đầu ra dài, phụ thuộc JDK và triển khai, thường gồm cả thông báo ở **stderr**. Đừng coi toàn bộ output của hai lệnh trên là ổn định giữa các phiên bản. Công cụ **jcmd** khi có quyền truy cập và được runtime hỗ trợ còn có các lệnh như **VM.command_line** hoặc **VM.system_properties** để điều tra một JVM đang chạy; danh sách lệnh khả dụng tùy JVM.

Trong chương trình, **ManagementFactory.getRuntimeMXBean().getInputArguments()** có thể cung cấp các đối số khởi chạy JVM do runtime ghi nhận, nhưng **không phải** API khôi phục nguyên dòng lệnh shell hoặc **String[] args** của ứng dụng. Đừng dùng nó thay parser CLI.

### 10.2. Các hiện tượng và hướng xử lý

| Triệu chứng | Kiểm tra đầu tiên | Cách xử lý |
| --- | --- | --- |
| Property trả về **null** hoặc mặc định | **-Dkey=value** có nằm trước tên main hoặc **-jar app.jar** không? Tên khóa có đúng chữ hoa/thường không? | Đặt **-D** đúng vị trí, dùng đúng tên và kiểm tra nguồn cấu hình |
| **args** chứa **-Xmx512m** hoặc **-D...** ngoài ý muốn | Tùy chọn có bị đặt phía sau main/JAR không? | Chuyển lên trước đích khởi chạy |
| JVM không khởi động vì cờ không hỗ trợ | Phiên bản và nhà cung cấp JDK có chấp nhận **-X/-XX** đó không? | Tham khảo tài liệu VM đang sử dụng; bỏ/thay cờ không tương thích |
| Đặt property mà heap không đổi | Có nhầm **-D...** với **-Xmx...** không? | Đặt VM option tương ứng trước main/JAR |
| Ứng dụng báo **Unknown argument** | Tham số có thuộc cú pháp riêng của ứng dụng không? | Đọc CLI help/contract của ứng dụng; sửa tên và vị trí |
| Property đúng nhưng thư viện vẫn dùng giá trị cũ | Thư viện có cache cấu hình ngay khi khởi tạo không? | Cấp giá trị sớm, hoặc dùng cơ chế reload chính thức nếu có |
| Chạy IDE đúng nhưng terminal sai | VM options, program arguments, environment variables có được nhập vào cùng vị trí không? | So sánh cấu hình chạy thực tế và working directory |
| Các tham số có khoảng trắng bị tách | Shell đã xử lý quoting như thế nào? | Quote phù hợp shell đích; xác minh **args** thực nhận |
| Đổi giá trị trong Gradle nhưng app fork không nhận | Đang cấu hình JVM của Gradle hay JVM ứng dụng? | Truyền VM options/properties/args trên đúng tác vụ và tiến trình |

### 10.3. Cẩn thận với dữ liệu nhạy cảm

Không nên đưa mật khẩu, token hay khóa bí mật vào command line hoặc system properties rồi in/log lại bừa bãi. Tham số dòng lệnh và một số system properties có thể được hiển thị bởi công cụ quản trị, thông tin tiến trình, báo lỗi hoặc chẩn đoán tùy môi trường. Hãy dùng cơ chế quản lý secret phù hợp và kiểm soát việc ghi log.

## 11. Bài tập tự thực hành

Dùng lớp **ConfigLayersDemo** ở mục 6 và dự đoán trước kết quả. Các câu hỏi dưới đây giả định không có biến môi trường hoặc công cụ khác chèn sẵn những demo properties.

**Bài 1.**

~~~bash
java -Ddemo.profile=test ConfigLayersDemo alpha beta
~~~

Câu hỏi: **profile**, **retries**, **argCount** và các phần tử **args** là gì?

**Đáp án:**

~~~text
profile=test
retries=(unset)
argCount=2
arg[0]=alpha
arg[1]=beta
~~~

**Bài 2.**

~~~bash
java ConfigLayersDemo --profile=test
~~~

Câu hỏi: Có system property **demo.profile=test** không?

**Đáp án:** Không tự có. **--profile=test** là **args[0]**; ví dụ chỉ in chuỗi này. Muốn application hiểu nó, phải viết parser CLI hoặc dùng framework tương ứng.

**Bài 3.**

~~~bash
java -Xmx256m ConfigLayersDemo --dry-run
~~~

Câu hỏi: Có cách nào đọc **-Xmx256m** bằng **args[0]** không?

**Đáp án:** Không trong lệnh này; **args[0]** là **--dry-run**. **-Xmx256m** đã được JVM tiêu thụ trước main. Muốn khảo sát giới hạn heap có thể dùng API theo hợp đồng runtime như **Runtime.getRuntime().maxMemory()**, nhưng không giả định con số phải đúng từng byte bằng giá trị viết trên dòng lệnh.

**Bài 4.** So sánh:

~~~bash
java -Ddemo.profile=dev ConfigLayersDemo
java ConfigLayersDemo -Ddemo.profile=dev
~~~

**Đáp án:** Lệnh đầu tạo system property và không truyền đối số ứng dụng; lệnh thứ hai không tạo property đó từ **-D** mà truyền nguyên token vào **args[0]**. Vị trí quyết định vai trò.

**Bài 5.** Trong **ApplicationArgsDemo**, tại sao **--port=8081** thắng **-Ddemo.port=9000**?

**Đáp án:** Code đọc system property làm giá trị ban đầu rồi duyệt **args** và gán lại biến **port**. Đổi thứ tự xử lý trong code có thể tạo quy tắc khác; JVM không áp đặt thứ tự ưu tiên ứng dụng.

## 12. Checklist khi triển khai

1. Xác định **ai** cần tham số: VM/launcher, hệ thống property, hay logic của ứng dụng.
2. Chọn **đúng cú pháp và vị trí**: options trước main/JAR; app args sau main/JAR.
3. Xác định phiên bản/JVM hỗ trợ các cờ **-X/-XX**, tránh áp dụng cờ không được hỗ trợ.
4. Với property do bạn tạo, đặt tên rõ ràng, chuyển kiểu/kiểm tra dữ liệu, mô tả mặc định và phạm vi thay đổi.
5. Với CLI args, định nghĩa parser, thông báo lỗi, help và quy tắc độ ưu tiên với các nguồn cấu hình khác.
6. Xác minh shell/IDE/build tool/container truyền **đúng token** vào **đúng tiến trình Java**.
7. Kiểm tra vận hành thực tế trên JVM mục tiêu; tránh log thông tin nhạy cảm.

**Ghi nhớ:** **-Xmx** thay đổi cấu hình VM; **-Dkey=value** là tùy chọn khởi động dùng để đặt **system property**, đọc bằng **System.getProperty(key)**; còn các token sau tên lớp main hoặc **-jar app.jar** đi vào **String[] args** và chỉ có nghĩa khi ứng dụng diễn giải chúng. Ba lớp này liên quan trong cùng một dòng lệnh nhưng không thể hoán đổi cho nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-lifecycle-004">Khởi động, khởi tạo lớp đầu tiên và gọi main theo phiên bản Java</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi động JVM, khởi tạo lớp đầu tiên và gọi main theo phiên bản Java

## 1. Bức tranh tổng thể

Khi chạy lệnh `java ...`, **JVM không nhảy ngay vào dòng đầu tiên của `main`**. Java launcher xác định chương trình cần chạy, thiết lập JVM và môi trường runtime; VM nạp, liên kết, khởi tạo initial class/interface rồi mới gọi một phương thức `main` hợp lệ. Dù `main` là điểm bắt đầu logic của ứng dụng, một số lớp của JDK và các `static initializer` cần thiết có thể thực thi trước đó.

~~~text
Shell / OS
   |
   | java [VM options] <main-class | -jar JAR | -m module[/class] | File.java> [app args]
   v
Java launcher: chọn cách khởi chạy, chuẩn bị JVM và môi trường
   |
   v
Xác định initial class / interface
   |
   v
Load class -> Link (Verify + Prepare + Resolve khi cần)
   |
   v
Initialize initial class (và superclasses / superinterfaces cần thiết)
   |   thực thi static field initializers, static blocks
   v
Chọn candidate main theo phiên bản Java
   |
   +---- static main ------> gọi trực tiếp
   |
   +---- instance main ---> tạo object với constructor không tham số
   |                         rồi gọi main trên object
   v
Ứng dụng chạy; các class khác có thể được load/initialize khi cần
~~~

Trong sơ đồ, **initial class** có nghĩa là loại ứng dụng được chọn làm đầu vào, **không phải lớp đầu tiên được JVM nạp trên toàn bộ đời process**. JVM đã có thể tải, liên kết hoặc khởi tạo nhiều lớp nền tảng trước khi ứng dụng được gọi. Cách chọn initial class có thể khác giữa class path, JAR, module, source-file launcher, và cả các môi trường nhúng JVM.

Bài học cần phân biệt ba thao tác:

- **Loading:** tìm/định nghĩa biểu diễn runtime của class hoặc interface.
- **Linking:** xác minh bytecode, chuẩn bị bộ nhớ trạng thái tĩnh, giải quyết tham chiếu biểu tượng theo quy tắc JVM.
- **Initialization:** chạy các biểu thức khởi tạo `static` và `static { ... }`, có thể kích hoạt khởi tạo các lớp liên quan.

Loading hoặc linking **không tự động đồng nghĩa** các `static initializer` đã chạy.

## 2. Ai chọn lớp ban đầu?

Theo **JLS `12.1** và **JVMS `5.2**, JVM bắt đầu thực thi chương trình bằng cách sử dụng một **initial class or interface** và gọi `main` tương ứng. Cách xác định initial class là chuyện của môi trường khởi chạy, thông dụng nhất là công cụ `java` thuộc JDK.

| Cách khởi chạy | Ví dụ | Cách chọn ứng dụng |
| --- | --- | --- |
| Class path | `java -cp out demo.App alpha beta` | Tên nhị phân của lớp `demo.App` |
| Executable JAR | `java -jar app.jar alpha` | `Main-Class` trong manifest của JAR |
| Named module | `java -m demo.app/demo.App alpha` | Main class được chọn trong module; có thể dùng main class được ghi trong module nếu bỏ `/demo.App` |
| Source-file mode, Java 11+ | `java App.java alpha` | Launcher biên dịch source rồi chọn lớp đầu vào từ file source |
| Compact source, Java 25+ | `java Hello.java` với `void main() { ... }` | Compiler tạo implicit class để JVM gọi `main` |

Ở class path:

~~~bash
java -Xmx256m -Dapp.mode=test -cp out demo.App first second
~~~

`-Xmx256m` là VM option, `-Dapp.mode=test` là system property, `-cp out` là đường tìm class, `demo.App` là lớp ban đầu. Hai token `first`, `second` đứng **sau tên lớp** là đối số ứng dụng: `args[0]` và `args[1]` nếu phương thức `main(String[])` được gọi. `-Xmx256m` đặt sau `demo.App` sẽ bị hiểu là đối số ứng dụng, không còn là tùy chọn khởi động VM.

Source-file launcher (`java App.java`) **biên dịch Java source trong khi khởi chạy rồi chạy class kết quả**, không có nghĩa JVM trực tiếp thông dịch source text `.java`. Còn khi chạy class đã biên dịch (`java App`), JVM lấy bytecode phù hợp từ class path/module path.

## 3. Nạp, liên kết và khởi tạo initial class

### Loading: tìm và định nghĩa lớp

Runtime có thể tải một class từ JAR, module, file `.class`, nguồn code được compiler sinh, hoặc custom class loader. Identity của một runtime class dựa trên **tên nhị phân và defining class loader**; vì vậy hai loader khác nhau có thể định nghĩa hai class cùng tên nhưng là hai class runtime riêng biệt.

Loading chỉ tạo điều kiện cho những bước tiếp theo; không đủ để khẳng định `static { System.out.println("loaded"); }` đã chạy. Tên class không tìm thấy, class file bị hỏng hoặc phiên bản class file không được VM hỗ trợ có thể làm startup thất bại ở giai đoạn này hoặc các giai đoạn liên quan.

### Linking: Verify, Prepare, Resolve

- **Verify:** class file/bytecode đáp ứng các quy tắc về định dạng và tính hợp lệ của máy ảo.
- **Prepare:** cấp phát và gán **giá trị mặc định** cho các biến `static` (`0`, `false`, `null`…), xử lý các hằng compile-time theo đặc tả. Đừng hiểu “prepare” là đã chạy mọi biểu thức khai báo field.
- **Resolve:** ánh xạ symbolic references (các chỉ mục/tên tượng trưng trong constant pool) sang các tham chiếu runtime cụ thể. Runtime có thể làm sớm hoặc trì hoãn; **không nhất thiết toàn bộ references đều resolve trước `main`**.

Ví dụ:

~~~java
static int count = calculate();
~~~

Khi prepare, `count` có giá trị mặc định của `int`. Biểu thức `calculate()` chỉ chạy tại thời điểm class **initialize** (trong quá trình initialization theo quy tắc Java), không phải vì loading vừa hoàn tất.

### Initialization: trước main và có thứ tự

Với class thông thường, các quy tắc chính:

1. Khởi tạo các **superclass** cần thiết trước class con. Một số superinterface khai báo default method cũng có thể cần khởi tạo theo quy tắc JLS `12.4`.
2. Chạy static field initializers và static initialization blocks của class **theo thứ tự văn bản nguồn**.
3. Với mỗi runtime class identity, initialization thành công không được chạy lại chỉ vì có thêm lời gọi method hoặc tạo object mới.
4. VM đồng bộ hoá quá trình initialization khi nhiều thread cố sử dụng class lần đầu. Thread có thể đợi thay vì đồng thời chạy `static` initialization hai lần.

Compiler có thể sinh phương thức đặc biệt `<clinit>` trong class file để thực thi logic khởi tạo class. `<clinit>` **khác** `<init>` (constructor/khởi tạo object) và **khác** `main` (phương thức điểm vào ứng dụng).

Không phải mọi class trong class path đều phải được initialize trước `main`. Class không được sử dụng có thể vẫn chưa khởi tạo; một class khác có thể được khởi tạo trước/sau `main` do active use như `new`, gọi `static` method hoặc đọc `static` field không phải compile-time constant.

## 4. Theo phiên bản Java, main hợp lệ ra sao?

### Java 8–20: chữ ký truyền thống

Mẫu được chấp nhận rộng nhất trong các Java release cũ:

~~~java
public class TraditionalMain {
    public static void main(String[] args) {
        System.out.println("Hello Java");
    }
}
~~~

Có thể viết `String... args` thay cho `String[] args` vì varargs có kiểu mảng `String[]`. Tên biến `args` có thể đổi. Chữ ký truyền thống yêu cầu **`public static`**, trả về **`void`**, và **một tham số `String[]`**. `int main`, `main(int[])` hoặc `static void main()` không phải điểm vào truyền thống.

Mẫu trên vẫn được Java 25+ chấp nhận, và nên dùng trong ứng dụng muốn hỗ trợ rộng các JDK hoặc build tool khác nhau.

### Java 21–24: những lần preview

Java bắt đầu thử nghiệm các cách viết entry `main` ngắn hơn và source file đơn giản hơn:

| Phiên bản | Tình trạng | JEP |
| --- | --- | --- |
| Java 21 | Preview lần đầu | JEP 445 |
| Java 22 | Preview tiếp theo | JEP 463 |
| Java 23 | Preview tiếp theo | JEP 477 |
| Java 24 | Preview lần thứ tư | JEP 495 |
| **Java 25+** | **Ổn định, không cần bật preview cho tính năng này** | **JEP 512** |

Với bản preview 21–24, cần đối chiếu chính xác cú pháp của bản đó, thường biên dịch với `--enable-preview --release N` hoặc `--enable-preview --source N` và chạy bytecode preview với `--enable-preview` trên runtime tương ứng. Không mặc định rằng mọi biến thể syntax preview đều như bản ổn định Java 25.

### Java 25+: ứng viên main mở rộng

Theo **JLS `12.1.4, Java SE 25 trở lên**, candidate `main` khai báo hoặc kế thừa trong initial class/interface phải trả về `void`, tên `main`, access `public` / `protected` / package access (**không phải `private`**), và nhận **một `String[]` hoặc `String...`**, hoặc **không có tham số**. Candidate có thể là **`static` hoặc instance method**, có thể có `throws`.

Các ví dụ chữ ký độc lập có thể dùng làm entry phù hợp:

~~~java
public static void main(String[] args) { } // truyền thống
static void main(String[] args) { }        // package access
protected static void main() { }           // không tham số
void main() { }                            // instance method
public void main(String... args) { }       // instance + varargs
~~~

Đây là **tập hợp chữ ký minh họa**, không phải một file Java chạy độc lập: khi ghép nhiều chữ ký vào cùng một class phải xét overload và quy tắc chọn `main`.

**Luật ưu tiên:** nếu có candidate `main(String[])` thì runtime chọn phiên bản nhận `String[]`. Chỉ khi không có candidate đó, mới chọn `main()` không tham số. Vì thế một **instance `main(String[])` có thể được chọn thay vì `static main()`**; `static` không phải tiêu chí ưu tiên cao hơn tham số. Trong cùng class, static và instance method **cùng chữ ký** không thể đồng thời tồn tại.

Nếu phương thức được chọn là instance `main`, VM tạo một instance initial class bằng constructor không tham số theo quy tắc tương ứng, rồi mới invoke method. Class initialization vẫn **đi trước**; constructor và instance field/block initializers chạy **trước** thân instance `main`. Nếu không thể tạo instance hợp lệ thì startup thất bại trước khi method chạy.

## 5. Ví dụ Java 17+: static initialization trước main

Tạo file `BootOrder.java`:

~~~java
class Parent {
    static int parentValue = mark("1. Parent static field");

    static {
        System.out.println("2. Parent static block");
    }

    static int mark(String message) {
        System.out.println(message);
        return 1;
    }
}

public class BootOrder extends Parent {
    static int childValue = mark("3. Child static field");

    static {
        System.out.println("4. Child static block");
    }

    {
        System.out.println("6. Instance initializer");
    }

    BootOrder() {
        System.out.println("7. Constructor");
    }

    public static void main(String[] args) {
        System.out.println("5. main args = " + String.join(",", args));
        new BootOrder();
    }
}
~~~

Lệnh biên dịch/chạy:

~~~bash
javac BootOrder.java
java BootOrder A B
~~~

**Stdout chính xác:**

~~~text
1. Parent static field
2. Parent static block
3. Child static field
4. Child static block
5. main args = A,B
6. Instance initializer
7. Constructor
~~~

**Giải thích:** Để invoke `BootOrder.main`, VM initialize `BootOrder`. Nó phải initialize `Parent` trước, nên các mốc 1–2 xuất hiện trước 3–4. Sau đó mới gọi `static main`, in mốc 5. Không cần instance `BootOrder` để gọi static main; chính câu lệnh `new BootOrder()` trong main mới gây ra mốc 6–7.

Nếu bỏ `new BootOrder()`, hai dòng cuối biến mất. Kết quả này phân biệt rõ:

- Class initialization: `static` initializer chạy trước `main`.
- Object initialization: instance initializer và constructor chạy khi `new`, **không mặc định** chạy trước một `static main`.
- Thứ tự trên chỉ mô tả các dấu in của class tự viết. Nó không liệt kê mọi lớp nội bộ mà JVM/JDK đã khởi tạo.

Quan sát bytecode:

~~~bash
javap -p -c BootOrder
javap -p -c Parent
~~~

`javap` giúp bạn nhận ra code khởi tạo class (hiện tương ứng `static {}`/`<clinit>`), code constructor và `main`. Không mặc định bytecode offsets/constant-pool indexes giống hệt mọi phiên bản compiler.

## 6. Ví dụ Java 17+: load không phải initialize

Tạo file `LazyInit.java`:

~~~java
class Configuration {
    static final String LABEL = "constant";

    static int PORT = initPort();

    static {
        System.out.println("Configuration static block");
    }

    static int initPort() {
        System.out.println("Configuration initPort");
        return 8080;
    }
}

public class LazyInit {
    public static void main(String[] args) {
        System.out.println("Before: " + Configuration.LABEL);
        System.out.println("Port: " + Configuration.PORT);
    }
}
~~~

~~~bash
javac LazyInit.java
java LazyInit
~~~

**Stdout chính xác:**

~~~text
Before: constant
Configuration initPort
Configuration static block
Port: 8080
~~~

`Configuration.LABEL` là **constant variable** có giá trị literal, có thể được compiler inline nên việc tham chiếu hằng này không bắt buộc class `Configuration` phải initialize. Khi đọc `Configuration.PORT` (field static không phải constant variable), VM khởi tạo class: gọi `initPort()`, chạy `static block` rồi mới in giá trị 8080.

Đừng áp dụng kết luận trên cho mọi `static final`. `static final String V = System.getenv("X")` không phải compile-time constant, nên lần active use field có thể kích hoạt initialization. Tương tự, `Configuration.class` chỉ lấy class literal, không tự khẳng định các static initializer đã chạy.

## 7. Ví dụ Java 25+: instance main và compact source

### A. Instance main buộc tạo object

Tạo `InstanceEntry.java` bằng **JDK 25+**:

~~~java
class InstanceEntry {
    static {
        System.out.println("1. static init");
    }

    {
        System.out.println("2. instance init");
    }

    InstanceEntry() {
        System.out.println("3. constructor");
    }

    void main() {
        System.out.println("4. instance main");
    }
}
~~~

~~~bash
javac InstanceEntry.java
java InstanceEntry
~~~

**Stdout:**

~~~text
1. static init
2. instance init
3. constructor
4. instance main
~~~

Ở đây runtime initialize class trước (mốc 1), **tạo object** bằng constructor không tham số (mốc 2–3), cuối cùng gọi instance `main()` (mốc 4). Không nên diễn đạt “JVM luôn gọi constructor trước `main`”: kết luận đó **chỉ đúng khi entry được chọn là instance main**.

### B. Main nhận String[] thắng main không tham số

Tạo `MainChoice.java` trên JDK 25+:

~~~java
class MainChoice {
    static void main() {
        System.out.println("static no-arg main");
    }

    void main(String[] args) {
        System.out.println("instance args main: " + String.join(",", args));
    }
}
~~~

~~~bash
javac MainChoice.java
java MainChoice x y
~~~

**Stdout:**

~~~text
instance args main: x,y
~~~

Runtime chọn `main(String[])` dù phương thức đó là instance và dù có `static main()`: **dạng có tham số được ưu tiên**. Runtime tạo instance bằng constructor không tham số mặc định; ví dụ không in dấu vết constructor. Nếu xóa `main(String[])`, `static main()` trở thành entry.

### C. Compact source file: Java source ngắn, VM vẫn chạy class

Tạo `Hello.java` (Java 25+):

~~~java
void main() {
    System.out.println("Hello from compact source");
}
~~~

~~~bash
java Hello.java
~~~

**Stdout:**

~~~text
Hello from compact source
~~~

Compiler tạo một **implicitly declared class** bao lấy `main`, source-file launcher biên dịch rồi VM thực thi class đó. Java 25 đưa cú pháp này ra khỏi preview (JEP 512). Không cần `--enable-preview` cho riêng tính năng này trên JDK 25+. Java 21–24 có những bản preview cần kiểm tra tùy release.

## 8. Lỗi khởi tạo và sự khác nhau giữa class với object

Nếu static initializer của **initial class** ném exception, chương trình có thể dừng **trước khi vào thân `main`**. Nếu class phụ được lần đầu sử dụng bên trong main rồi lỗi, main đã bắt đầu nhưng thao tác active use ấy thất bại. Theo JLS `12.4.2, lỗi khởi tạo bởi một exception thông thường có thể được bọc thành `ExceptionInInitializerError`; runtime class rơi vào trạng thái lỗi và những lần truy cập sau thường nhận `NoClassDefFoundError`.

Tạo file `InitFailure.java`:

~~~java
class BrokenConfig {
    static int VALUE = fail();

    static int fail() {
        throw new IllegalStateException("bad configuration");
    }
}

public class InitFailure {
    public static void main(String[] args) {
        for (int i = 1; i <= 2; i++) {
            try {
                System.out.println(BrokenConfig.VALUE);
            } catch (LinkageError error) {
                System.out.println(i + ": " + error.getClass().getSimpleName());
            }
        }
    }
}
~~~

~~~bash
javac InitFailure.java
java InitFailure
~~~

**Stdout:**

~~~text
1: ExceptionInInitializerError
2: NoClassDefFoundError
~~~

Vì `BrokenConfig` không phải initial class, `InitFailure.main` chạy được và đã chủ động truy cập `BrokenConfig.VALUE`. Truy cập đầu tiên khiến `<clinit>` thất bại; lần thứ hai cố sử dụng class đã ở erroneous state. Nếu lỗi nằm trong static initializer của `InitFailure`, bản thân `InitFailure.main` có thể chưa chạy.

Trong ứng dụng đồng thời, các thread không tự ý khởi tạo cùng một runtime class độc lập hai lần: VM quản lý initialization synchronization và trạng thái khởi tạo. Tuy nhiên, cùng tên class nhưng do **khác defining class loader** có thể là các runtime class khác nhau, với static state riêng.

## 9. Chẩn đoán startup theo đúng nguyên nhân

| Dấu hiệu | Câu hỏi nên hỏi | Kiểm tra |
| --- | --- | --- |
| `Could not find or load main class` | Tên class đầy đủ, package, class path/module path đúng chưa? | `java -cp ... fully.qualified.MainClass` và JAR content |
| `Main method not found` | Chữ ký có hợp lệ trên JDK chạy không? | `java -version`; dùng mẫu `public static void main(String[])` nếu cần tương thích cũ |
| `UnsupportedClassVersionError` | Bytecode được biên dịch cho release mới hơn runtime? | `javap -verbose ClassName` để xem class major version; cân nhắc `javac --release` |
| `ExceptionInInitializerError` | Static initializer/field initializer nào ném exception? | Xem `Caused by` và stack trace ban đầu; tìm `<clinit>` |
| `NoClassDefFoundError` sau lỗi static | Class đã ở erroneous state? | Truy ngược lỗi khởi tạo lần đầu, class loader và dependency |
| `constructor chạy trước main` | Entry có là instance main trên Java 25+? | Đối chiếu chữ ký được chọn và `new` trong ứng dụng |
| Hai main cùng tồn tại | `main(String[])` hay `main()` được ưu tiên? | Java 25+: `main(String[])` ưu tiên; xem JLS `12.1.4` |
| Static block không chạy | Đây chỉ là class literal/compile-time constant? | Kiểm tra active use thay vì suy từ loading |

Lệnh xem phiên bản và bytecode:

~~~bash
java -version
javac -version
javap -p -c BootOrder
javap -verbose BootOrder
java -Xlog:class+load=info BootOrder
~~~

`-Xlog:class+load=info` minh họa unified logging của **HotSpot** trên JDK hiện đại. Dòng log cho biết các class được load, **không phải bằng chứng rằng static initializer của mỗi class cũng đã chạy**. Output có thể rất dài và phụ thuộc bản dựng; xem `java -Xlog:help` trước khi áp dụng trên VM/version cụ thể. Trường hợp lỗi chỉ xảy ra ở runtime, hãy lưu cả `java -version` và `javac -version` vì dùng JDK biên dịch khác runtime là nguồn lỗi thường gặp.

Đừng quên ranh giới vòng đời: việc `main` trả về **không luôn đồng nghĩa process lập tức kết thúc**. Các non-daemon thread còn chạy có thể giữ JVM sống, và shutdown hooks có thể thực hiện khi JVM shutdown. Đây là giai đoạn sau khi đã vào `main`.

## 10. Những hiểu nhầm hay gặp

1. **“Lớp chứa `main` chắc chắn là class đầu tiên VM đã tải.”** Sai. Initial class là điểm vào ứng dụng; VM đã có thể xử lý các lớp runtime.
2. **“Class loaded tức static block đã chạy.”** Sai. Loading khác initialization; linking resolution còn có thể trì hoãn.
3. **“Prepare đã chạy `static int x = f()`.”** Sai. Preparation đặt giá trị mặc định; initialization mới gọi `f()`.
4. **“Mỗi lần gọi static method lại chạy static block.”** Sai khi class đã khởi tạo thành công trong cùng runtime identity.
5. **“Mọi class ứng dụng đều được initialize trước main.”** Sai. Nhiều class chỉ initialize khi active use.
6. **“`main` bắt buộc luôn `public static void main(String[])`.”** Đây là mẫu truyền thống và tương thích rộng, nhưng Java 25+ có các dạng entry khác.
7. **“Java 17 chạy `void main()` như Java 25.”** Sai với entry tiêu chuẩn: khả năng mở rộng là preview 21–24 và ổn định từ 25.
8. **“Constructor luôn chạy trước main.”** Chỉ xảy ra khi runtime phải tạo object cho **instance main**, hoặc chương trình tự tạo object trước khi gọi một method.
9. **“Compact source nghĩa là JVM đọc source Java trực tiếp.”** Sai. Launcher biên dịch thành class và JVM chạy bytecode.
10. **“Main kết thúc là process phải đóng ngay.”** Sai nếu còn non-daemon threads hay quá trình shutdown tiếp tục.

## 11. Tự kiểm tra

**1. Vì sao static block của `Parent` chạy trước `BootOrder`?**  
Do khởi tạo superclass bắt buộc diễn ra trước initialization của subclass.

**2. Sự khác nhau giữa `<clinit>` và `<init>`?**  
`<clinit>` biểu diễn logic class initialization; `<init>` là constructor cho một object. Static main tự nó không tạo instance của initial class.

**3. Java 17 chạy `class App { void main() {} }` bằng `java App` được không?**  
Không theo luật entry main truyền thống. Cần `public static void main(String[] args)` hoặc biến thể varargs tương đương.

**4. Trong Java 25+, giữa `static void main()` và `void main(String[] args)` thì chọn gì?**  
Chọn `void main(String[] args)`; runtime tạo object để gọi instance method.

**5. Tham chiếu `static final String LABEL = "x"` luôn làm class initialized không?**  
Không. Constant variable với initializer là constant expression có thể được inline và không kích hoạt class initialization khi đọc.

**6. Nếu lỗi static initializer của initial class xảy ra, có thể main chưa hề chạy không?**  
Có. JVM phải initialize initial class thành công trước khi invoke main.

## 12. Nguồn tra cứu

- [JLS Java SE 25, Chapter 12 — Execution](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html): startup, initialize initial class, entry `main`, initialization triggers và lỗi.
- [JLS Java SE 26, Chapter 12 — Execution](https://docs.oracle.com/javase/specs/jls/se26/html/jls-12.html): quy tắc cập nhật về candidate main và class initialization.
- [Java Language Changes by Release, Java 26](https://docs.oracle.com/en/java/javase/26/language/java-language-changes-release.html): chuỗi preview JEP 445/463/477/495 và phiên bản ổn định JEP 512 ở Java 25.
- [Oracle Java 25 — Compact Source Files and Instance Main Methods](https://docs.oracle.com/en/java/javase/25/language/compact-source-files-instance-main-methods.html): tài liệu tính năng ổn định.
- [Oracle Java 24 — Simple Source Files and Instance Main Methods](https://docs.oracle.com/en/java/javase/24/language/simple-source-files-and-instance-main-methods.html): tài liệu trạng thái preview của JDK 24.
- [Oracle Java 25 — java launcher](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): cú pháp class path, JAR, module, source-file mode và phân loại options. Đối với chữ ký `main` mở rộng, ưu tiên văn bản JLS `12.1.4` của release tương ứng.
- [Java SE Specifications](https://docs.oracle.com/javase/specs/): chọn bản **JVMS Chapter 5** phù hợp release để đọc chuẩn về loading/linking/initializing.

**Ghi nhớ:** JVM chuẩn bị môi trường và initial class **trước** khi invoke `main`; các block `static` có thể chạy trước thân `main`, còn constructor chỉ chạy khi cần object. Khi gặp lỗi “không vào main”, kiểm tra **launch mode, class path/module path, class-file version, static initialization và chữ ký main theo JDK đang dùng**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-lifecycle-005">Các luồng của ứng dụng và các luồng nội bộ của JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Các luồng của ứng dụng và các luồng nội bộ của JVM

Trong cùng một tiến trình Java, bạn có thể thấy `main`, `http-worker-1`, `pool-1-thread-1`, `Reference Handler`, `GC Thread#0` hoặc `C2 CompilerThread0`. Chúng đều liên quan đến hoạt động của chương trình, nhưng **không có cùng chủ sở hữu, nhiệm vụ hay vòng đời**. Phân biệt đúng các luồng này giúp giải thích vì sao JVM vẫn sống sau khi `main` kết thúc, vì sao bộ thu gom rác cần luồng riêng, và vì sao không nên đếm thread để kết luận mọi thread đều do ứng dụng tạo.

Bài học thuộc chủ đề **Vòng đời JVM**, tập trung vào: (1) luồng chạy công việc ứng dụng, (2) luồng hỗ trợ runtime/JVM, (3) daemon và điều kiện kết thúc tiến trình, (4) sự phối hợp giữa các luồng, (5) quan sát và chẩn đoán thực tế.

**Phạm vi triển khai:** khái niệm luồng/daemon dựa trên Java SE; ví dụ về GC workers, compiler threads, VM thread, safepoint và lệnh `jcmd` chủ yếu mô tả **HotSpot/OpenJDK trên JDK 17+**. Một số chi tiết, tên thread và số lượng thread phụ thuộc phiên bản, hệ điều hành, GC và tùy chọn JVM. Virtual thread của Java 21+ được giải thích riêng để tránh nhầm với luồng nền nội bộ.

## 1. Một JVM, nhiều luồng đang làm những việc khác nhau

Một tiến trình Java có thể đồng thời có:

- **Luồng chạy mã ứng dụng:** `main`, thread do `new Thread(...)` tạo, luồng pool `ExecutorService`, worker của web server hoặc thư viện. Chúng thực thi nhiệm vụ nghiệp vụ, có thể là **platform thread** hoặc (từ Java 21) **virtual thread**.
- **Luồng dịch vụ Java runtime / thư viện nền tảng:** ví dụ `Reference Handler` và một số cleaner/scheduler thread. Chúng có thể được biểu diễn bằng `java.lang.Thread`, thường là daemon, nhưng không phải tác vụ nghiệp vụ do bạn tự viết.
- **Luồng native nội bộ của VM:** ví dụ các GC worker, compiler worker và một số thread điều phối VM của HotSpot. Một số **không tương ứng với đối tượng `java.lang.Thread` bình thường**; danh sách `Thread.getAllStackTraces()` vì vậy không phải danh mục tuyệt đối mọi native thread của tiến trình.
- **Luồng OS từ thư viện native/JNI hoặc thành phần khác:** có thể tồn tại ngoài mô hình quản lý `java.lang.Thread` thông thường.

Đây là **phân nhóm theo trách nhiệm**, không phải bốn kiểu bắt buộc trong Java API. “Luồng của ứng dụng” và “luồng nội bộ” mô tả **ai dùng nó để làm gì**; “daemon/non-daemon”, “platform/virtual”, “Java-level/native VM” là **các trục phân loại khác nhau**.

```text
Một tiến trình JVM/HotSpot
│
├── Công việc ứng dụng
│   ├── main (platform thread)
│   ├── worker do ứng dụng tạo (platform thread)
│   ├── request/executor workers
│   └── virtual threads của ứng dụng (Java 21+)
│
├── Dịch vụ runtime nhìn thấy ở mức Java (tùy bản JDK)
│   ├── Reference Handler
│   └── Cleaner / dịch vụ nền khác
│
└── Hoạt động nội bộ VM (tùy HotSpot/cấu hình)
    ├── GC workers / concurrent GC threads
    ├── JIT compiler threads
    ├── VM / service / watcher / sampling threads
    └── thread phụ trợ khác
```

Một số hệ thống như HTTP server hoặc thư viện logging tự tạo worker nền. Dù tên nghe như “internal”, chúng vẫn là **thread phục vụ thành phần ứng dụng/thư viện**, không mặc nhiên là GC hoặc compiler thread của JVM.

## 2. Luồng ứng dụng: nơi chương trình thực hiện công việc

### 2.1. `main` là điểm vào, không phải luồng duy nhất

Với chương trình thông thường chạy bằng `java Main`, Java launcher gọi `public static void main(String[] args)` trong một thread có tên thường là `main` và thông thường **không phải daemon**.

`main` có thể tạo thêm thread, giao task cho thread pool hoặc gọi thư viện tự tạo thread. Những luồng ấy có thể tiếp tục chạy sau khi phương thức `main` trả về. Vì vậy **“hết phương thức main” không đồng nghĩa “JVM dừng”**.

Platform thread trên HotSpot thông thường được hỗ trợ bởi một luồng hệ điều hành, nên việc tạo quá nhiều thread đồng thời làm tăng chi phí stack/native resources và scheduling. Đây là quan hệ thực thi phổ biến, không phải lý do để suy ra **mọi** Java thread phải có một OS thread riêng (virtual thread là ngoại lệ rõ ràng từ Java 21).

### 2.2. Tạo và hoàn tất một platform thread

```java
Thread worker = new Thread(() -> {
    // Công việc ứng dụng thực hiện trên thread riêng.
    doBusinessWork();
}, "application-worker");

worker.start(); // Yêu cầu bắt đầu thực thi đồng thời.
worker.join();  // Thread hiện tại đợi worker kết thúc.
```

Đoạn này là **minh họa**, vì `doBusinessWork()` là tên đại diện chưa được định nghĩa. Chương trình chạy đầy đủ được đưa ở phần 6.

- `new Thread(...)` tạo đối tượng thread ở trạng thái `NEW`; chưa thực sự bắt đầu tác vụ.
- `start()` yêu cầu JVM lên lịch thực thi `run()` trong luồng mới; **không đảm bảo** luồng mới chạy ngay trước câu lệnh tiếp theo của bên gọi.
- `join()` giúp thread đang gọi **đợi** thread đích chấm dứt; `join()` không “tự hủy” task trước hạn. `join()` có thể bị gián đoạn bằng `InterruptedException`.
- Gọi trực tiếp `worker.run()` chỉ gọi phương thức như thông thường trên **thread hiện tại**, không sinh đường thực thi song song.
- Không được gọi `start()` lần thứ hai trên cùng một đối tượng `Thread`; sẽ gặp `IllegalThreadStateException`.

Sử dụng `ExecutorService`, structured concurrency (khi khả dụng) hoặc công cụ cấp cao giúp quản lý số task và vòng đời tốt hơn so với tạo thread không giới hạn. `ExecutorService` là cơ chế dùng thread, **không phải loại internal GC thread**.

### 2.3. Trạng thái luồng ở mức Java

`Thread.State` có sáu giá trị:

| Trạng thái | Ý nghĩa ở mức Java |
| --- | --- |
| `NEW` | Đối tượng đã tạo, `start()` chưa gọi |
| `RUNNABLE` | Đang thực thi hoặc sẵn sàng thực thi theo mô hình JVM; không đảm bảo đang chiếm CPU |
| `BLOCKED` | Chờ giành monitor lock để vào `synchronized` |
| `WAITING` | Chờ không có thời hạn tường minh qua một thao tác như `Object.wait()` hoặc `Thread.join()` |
| `TIMED_WAITING` | Chờ có thời hạn như `Thread.sleep(...)`, timed `wait/join` |
| `TERMINATED` | Đã kết thúc `run()` |

Đây là **trạng thái quan sát tại một thời điểm**, có thể đổi ngay khi bạn kiểm tra. Không đánh đồng `RUNNABLE` của Java với trạng thái “running trên CPU” của scheduler OS. Và `BLOCKED` có nghĩa cụ thể là **chờ monitor lock**, không phải cách gọi chung cho mọi I/O chậm.

## 3. Daemon/non-daemon và điều kiện JVM kết thúc

### 3.1. Daemon là một thuộc tính vòng đời, không phải nhãn “JVM nội bộ”

Với **platform thread**:

- **Non-daemon:** thường là thread thực hiện công việc ứng dụng mà tiến trình cần đợi hoàn thành. Chừng nào còn non-daemon thread đang sống, JVM thông thường tiếp tục sống.
- **Daemon:** thường làm việc phụ trợ. Daemon thread **không tự mình ngăn trình tự JVM shutdown bắt đầu** khi mọi thread non-daemon đã kết thúc.
- Từ Java 17, khi tạo một `Thread` theo constructor thông thường, trạng thái daemon ban đầu được **kế thừa từ thread tạo nó**, trừ khi được thay đổi trước `start()`. Gọi `setDaemon(true/false)` **trước khi khởi động**; đổi khi thread đang chạy gây `IllegalThreadStateException`.

**Quy tắc quan trọng:** thread do ứng dụng tạo rồi gọi `setDaemon(true)` **vẫn là thread ứng dụng**, chỉ mang thuộc tính daemon. Ngược lại, thread hỗ trợ JVM có thể không có đối tượng `Thread` công khai để ứng dụng đọc `isDaemon()`; không nên áp dụng quy tắc daemon của `java.lang.Thread` để mô tả mọi native thread nội bộ.

### 3.2. Vòng đời quy ước khi khởi động từ launcher

```text
JVM khởi động
   │
   ├─► Dịch vụ cần thiết của VM được tạo theo nhu cầu
   │
   └─► Luồng main thực thi main()
             │
             ├─► tạo app-worker-A (non-daemon)
             ├─► tạo app-monitor (daemon)
             └─► main() trả về
                      │
                      ├─ app-worker-A còn sống?
                      │     ├─ Có → tiến trình tiếp tục chạy
                      │     └─ Không → có thể bắt đầu shutdown
                      └─ app-monitor còn chạy cũng không tự giữ tiến trình
```

Đối với Java app khởi chạy thông thường, nếu tất cả các **non-daemon threads đã bắt đầu** đều kết thúc thì trình tự shutdown có thể bắt đầu và daemon threads còn lại không được bảo đảm hoàn tất công việc. Runtime còn có **shutdown hooks** và những cách kết thúc tường minh như `System.exit(...)`; hook hợp lệ có thể chạy và kéo dài quá trình shutdown. Gọi `Runtime.halt(...)` hay tiến trình bị kết liễu cưỡng bức có hệ quả khác. Không nên diễn giải “mọi non-daemon chết thì OS tức khắc thu hồi tiến trình” mà bỏ qua pha shutdown.

**Hệ quả thiết kế:** công việc cần hoàn thành, flush hoặc đóng resource không nên phó mặc cho một daemon thread bất tận. Hãy cung cấp lifecycle rõ ràng: nhận yêu cầu dừng, đóng executor, `join`/`awaitTermination`, `close()` và shutdown hooks khi phù hợp.

## 4. Các luồng nội bộ JVM/HotSpot thực hiện những gì?

HotSpot cần thực hiện nhiều tác vụ song song với bytecode của ứng dụng. Các mục dưới đây là **ví dụ triển khai**, không phải cam kết mọi JVM hay mọi tiến trình đều có cùng tên/số lượng thread.

| Nhóm | Nhiệm vụ chủ yếu | Quan hệ với ứng dụng |
| --- | --- | --- |
| **GC worker threads** | Thực hiện các bước thu gom heap như đánh dấu, sao chép/di chuyển hoặc các pha song song, tùy collector | Giúp thu hồi bộ nhớ; một số pha cần phối hợp dừng luồng ứng dụng |
| **Concurrent GC threads** | Thực hiện những pha GC có thể tiến hành đồng thời với mã ứng dụng | Giảm một phần công việc trong khoảng dừng; không có nghĩa không còn pause |
| **JIT compiler threads** | Biên dịch/ tối ưu bytecode nóng thành mã máy, xử lý tác vụ biên dịch | Chạy nền và có thể dùng CPU, trong khi ứng dụng tiếp tục thực thi |
| **VM / service / watcher threads** | Điều phối công việc VM, theo dõi và xử lý sự kiện hoặc tác vụ quản trị nội bộ | Cấu trúc và tên cụ thể tùy HotSpot/JDK |
| **Reference Handler** | Xử lý cơ chế `Reference` theo thiết kế thư viện/runtime Java | Có thể xuất hiện như một Java daemon thread trong thread dump |
| **Cleaner và dịch vụ nền Java khác** | Thực hiện cleanup được đăng ký hoặc tác vụ runtime/thư viện | Không nên dùng cleanup nền làm thay thế cho đóng resource tường minh |
| **JFR / profiling / attach-related threads** | Thu thập quan sát, profiling, hoặc phục vụ attach tools khi bật/cần | Có thể xuất hiện theo cấu hình và công cụ đang sử dụng |

**Không phải tất cả nhóm đều “chạy Java” như `new Thread(() -> ...)`.** Trong mã nguồn HotSpot tồn tại cả nhóm `JavaThread` và `NonJavaThread`; ví dụ compiler/GC/VM machinery có thể dùng cấu trúc runtime native. Vì thế `Thread.getAllStackTraces()`, JMX hay một thread dump mức Java **không nhất thiết liệt kê mọi luồng native nội bộ**.

### 4.1. GC và luồng ứng dụng

Khi ứng dụng tạo đối tượng, heap nhận áp lực cấp phát; collector có thể thực hiện chu kỳ GC tùy thuật toán và cấu hình. GC phải phối hợp với **mutator threads** (các luồng thay đổi đồ thị object), xác định GC roots, xử lý tham chiếu và có thể di chuyển đối tượng. Các ứng dụng thường quan sát kết quả như thời gian pause, throughput hoặc CPU tiêu thụ.

- **Stop-the-world (STW):** tại một số pha, JVM cần tạm dừng tiến triển thông thường của thread ứng dụng để lấy một trạng thái nhất quán; các thread GC có thể tiếp tục thực hiện công việc thu gom trong quãng đó.
- **Concurrent GC:** một phần công việc chạy đồng thời với ứng dụng, nhưng vẫn có thể cần các pha dừng hoặc handshake/safepoint.
- **Safepoint:** điểm/trạng thái mà VM có thể phối hợp thao tác cần ràng buộc nhất định trên các thread; không phải tín hiệu “GC luôn dừng mọi thread ở mọi thời điểm”.
- **Tham chiếu đang sống:** stack frame và những root khác góp phần quyết định khả năng truy cập của object. Một thread vẫn giữ reference trong `ThreadLocal` hoặc collection có thể làm object còn reachable và ảnh hưởng retention.

Các collector như G1, Parallel hoặc ZGC có chiến lược và luồng hỗ trợ khác nhau. Số thread GC thường phụ thuộc số CPU có sẵn, cờ cấu hình và bản JDK. **Không lấy tên hay con số mẫu trên mạng làm quy tắc cố định.**

### 4.2. JIT compiler và luồng ứng dụng

Ứng dụng có thể đang thực thi bytecode theo interpreter trong lúc HotSpot nhận diện phương thức/đoạn chạy thường xuyên. Compiler threads biên dịch mã đã quan sát thành native machine code; lần thực thi sau có thể dùng bản biên dịch đã tạo. Tối ưu hóa, deoptimization và lựa chọn tầng compiler là quyết định của JVM.

JIT có thể dùng CPU nền ngay khi thread nghiệp vụ vẫn chạy bình thường. Đừng nhầm việc thấy `CompilerThread` trong công cụ chẩn đoán với việc ứng dụng tự gọi `javac`: **`javac` là compiler trước runtime**, còn JIT compiler là thành phần runtime của VM.

### 4.3. Reference processing và cleanup

Java có các loại tham chiếu đặc biệt như `WeakReference`, `SoftReference` và `PhantomReference`. GC và cơ chế reference processing phối hợp để xử lý các tham chiếu này, với các hoạt động hỗ trợ ở runtime/Java library. Thread tên `Reference Handler` trong HotSpot/OpenJDK là một ví dụ quen thuộc.

Không nên kết luận “Reference Handler là GC worker” chỉ vì có chữ “Reference”, hoặc “Cleaner sẽ đóng socket đúng thời hạn”. Cleanup tự động có tính **không xác định về thời điểm**; còn file, socket, connection có vòng đời nên được quản lý bằng `try-with-resources` hoặc `close()` rõ ràng.

## 5. Hai chiều phân loại thường bị trộn lẫn

| Câu hỏi | Phân loại đúng | Điều nó **không** chứng minh |
| --- | --- | --- |
| Thread thực hiện nghiệp vụ hay hỗ trợ JVM? | Application/runtime/internal role | Không suy ra daemon status |
| Thread giữ JVM sống khi `main` đã xong? | Daemon hay non-daemon (platform threads, cùng quy tắc shutdown) | Không suy ra do ai tạo |
| Thread gắn với OS thread 1–1? | Platform hoặc virtual trong Java 21+ | Không suy ra “GC” hay “application” |
| Thread có đối tượng `java.lang.Thread`? | Java-level hoặc nội bộ native HotSpot | Không suy ra mọi OS thread sẽ hiện trong Java API |
| Task được xử lý đồng thời? | Scheduling/threading behavior | Không bảo đảm thực sự chạy song song trên nhiều core |

Đặc biệt, **daemon != JVM internal** và **JVM internal != tất cả daemon Java threads**. Một chương trình web có thể tự tạo daemon scheduling threads, còn HotSpot có native VM threads mà `Thread.isDaemon()` không phải API mô tả trực tiếp.

## 6. Ví dụ Java 17 chạy được: ứng dụng tạo hai loại thread

Tạo file **`ThreadRolesDemo.java`**:

```java
public class ThreadRolesDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(
                () -> System.out.println("worker: task completed"),
                "application-worker"
        );
        worker.setDaemon(false); // Cần hoàn tất trước khi JVM có thể tự kết thúc.

        Thread helper = new Thread(
                () -> System.out.println("helper: task completed"),
                "application-helper"
        );
        helper.setDaemon(true);  // Luồng do ứng dụng tạo nhưng là daemon.

        System.out.println("worker before start = " + worker.getState());
        System.out.println("worker daemon = " + worker.isDaemon());
        System.out.println("helper daemon = " + helper.isDaemon());

        worker.start();
        worker.join();           // Chốt thứ tự output, đợi worker kết thúc.
        System.out.println("worker after join = " + worker.getState());

        helper.start();
        helper.join();           // Cố ý đợi helper để demo luôn đủ output.
        System.out.println("main: done");
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac ThreadRolesDemo.java
java ThreadRolesDemo
```

**Stdout xác định của ví dụ:**

```text
worker before start = NEW
worker daemon = false
helper daemon = true
worker: task completed
worker after join = TERMINATED
helper: task completed
main: done
```

**Vì sao thứ tự này ổn định?** Hai thread riêng chỉ được `start()` tuần tự, sau mỗi `start()` đều có `join()` trước bước tiếp theo. `join` hoàn tất khi thread đích đã chấm dứt; vì vậy không phụ thuộc vào việc OS cấp CPU cho worker sớm hay muộn. Các dòng in trong ví dụ không phản ánh tên/số lượng GC threads thực tế, vì JVM thực hiện các hoạt động nội bộ độc lập với log nghiệp vụ này.

Ví dụ này chứng minh:

1. Một ứng dụng tự tạo **non-daemon worker** và **daemon helper**; cả hai đều là **application threads**.
2. `worker` từ `NEW` đi đến `TERMINATED` sau `start()/join()`.
3. `helper` là daemon nhưng **vẫn chạy đến hết** ở ví dụ vì `main` chủ động `join()`; nếu bỏ `join()`, không có bảo đảm helper in kịp hoặc hoàn tất trước shutdown.
4. Dòng `main: done` không khẳng định mọi thread nội bộ VM đã biến mất trước khi chương trình trả về. Runtime tự quản lý chúng trong quá trình kết thúc.

**Thay đổi thử nghiệm:** bỏ `helper.join()` rồi chạy nhiều lần. Output từ helper có thể vẫn hiện hoặc không, tùy scheduling. Không dùng sự có mặt/vắng mặt ngẫu nhiên của một dòng để kết luận daemon luôn hoặc không bao giờ chạy.

## 7. Java 21+: virtual thread không đồng nghĩa JVM background thread

Kể từ Java 21, ứng dụng có thể tạo **virtual threads**: `Thread.startVirtualThread(runnable)` hoặc `Thread.ofVirtual()...`. Chúng là luồng logic do Java runtime lập lịch trên các **carrier platform threads**. Chúng thường tiết kiệm tài nguyên hơn platform thread khi có nhiều task chờ I/O, nhưng không thay thế tất cả nhu cầu điều khiển concurrent workload.

- Một virtual thread là **thread của ứng dụng** nếu application tạo nó, dù được hỗ trợ bởi scheduler/runtime.
- Virtual threads luôn có **daemon status**; chúng **không giữ JVM sống** chỉ nhờ tồn tại.
- Carrier thread là platform thread phục vụ scheduling; **không phải chính virtual thread**, và cũng không nên gộp carrier với các worker chuyên trách của GC/JIT.
- `Thread.currentThread()` khi chạy bên trong một virtual thread trả về **virtual thread**, không trả carrier đang thực thi nó.
- Một virtual thread có thể lần lượt chạy trên các carrier khác nhau. Vì vậy không giả định mỗi virtual thread có một OS thread tương ứng.

Ví dụ nhỏ dành riêng **Java 21+**, không biên dịch được trên Java 17:

```java
public class VirtualThreadDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread task = Thread.ofVirtual().name("request-virtual").start(() -> {
            Thread current = Thread.currentThread();
            System.out.println("name = " + current.getName());
            System.out.println("virtual = " + current.isVirtual());
            System.out.println("daemon = " + current.isDaemon());
        });
        task.join();
        System.out.println("main: done");
    }
}
```

Chạy:

```bash
javac VirtualThreadDemo.java
java VirtualThreadDemo
```

Stdout:

```text
name = request-virtual
virtual = true
daemon = true
main: done
```

Dòng `task.join()` khiến chương trình đợi task hoàn tất. Nếu ứng dụng tạo virtual threads xử lý việc dài hạn mà không có cơ chế quản lý vòng đời, khi hết non-daemon platform threads, JVM vẫn có thể bắt đầu shutdown. **Đừng dùng daemon status để định nghĩa nguồn gốc hay mức quan trọng của tác vụ.**

## 8. Vai trò của thread trong vòng đời JVM: từ khởi động đến shutdown

| Mốc | Luồng ứng dụng | Luồng nội bộ/runtime |
| --- | --- | --- |
| **Khởi động** | Launcher đưa ứng dụng tới `main` theo mô hình khởi chạy | VM khởi tạo runtime, cấu trúc quản lý thread và các service cần thiết |
| **Tải/làm nóng ứng dụng** | `main` gọi code, tạo workers, chạm tới thư viện | Class loading, compiler threads và dịch vụ nền có thể được kích hoạt theo nhu cầu |
| **Xử lý tải** | Request/task threads xử lý dữ liệu; phát sinh allocation | GC có thể chạy, JIT tối ưu mã nóng, các service phối hợp liên tục |
| **Chờ I/O/khóa** | Có thể ở `WAITING`, `TIMED_WAITING`, `BLOCKED` hoặc còn `RUNNABLE` tùy thao tác | Runtime/OS phối hợp đánh thức, I/O, điều phối và một số pha GC |
| **Ứng dụng yêu cầu dừng** | Ngừng nhận task, hủy/đóng pools, chờ work quan trọng, đóng tài nguyên | VM xử lý shutdown hooks/sự kiện kết thúc theo cơ chế runtime |
| **Tiến trình kết thúc** | Không còn task chạy trong tiến trình | VM dừng hoạt động, hệ điều hành thu hồi tài nguyên của tiến trình |

**Trách nhiệm của người viết ứng dụng** là quản lý những thread/task mình tạo ra: đặt giới hạn concurrency hợp lý, phản hồi interruption, đóng executor/resource, kiểm soát rò rỉ ThreadLocal và đảm bảo công việc thiết yếu được hoàn tất khi shutdown. **Trách nhiệm của JVM** là lập lịch/thực thi theo runtime, quản lý bộ nhớ/GC/JIT và duy trì dịch vụ VM nội bộ. Không nên “stop GC thread” hay thao tác trực tiếp lên thread internal dựa trên tên tìm thấy trong dump.

## 9. Quan sát và chẩn đoán bằng công cụ

Trong ứng dụng Java, `Thread.currentThread()`, `Thread.getState()`, `Thread.isDaemon()` và `ThreadMXBean` giúp quan sát các **Java threads** ở mức API. Khi vận hành HotSpot:

```bash
# Chạy ví dụ có log GC và safepoint (có thể không phát sinh GC trong chương trình nhỏ)
java -Xlog:gc*,safepoint ThreadRolesDemo

# Liệt kê PID Java của tiến trình đang chạy
jcmd -l

# Lấy thread dump Java của tiến trình (thay <pid> bằng PID thực tế)
jcmd <pid> Thread.print -l

# Xem thông tin heap do HotSpot hỗ trợ
jcmd <pid> GC.heap_info
```

`jcmd` thường nằm trong JDK và cần có quyền/khả năng attach phù hợp. Một JVM ngắn như `ThreadRolesDemo` có thể thoát trước khi bạn kịp chạy `jcmd`; hãy dùng ứng dụng lâu hơn để khảo sát thực tế.

**Cách đọc an toàn:**

- Tìm `main` và thread của framework/application bằng **stack trace và nơi tạo thread**, chứ không chỉ dựa vào tên `pool-N-thread-M`.
- Nhận diện những thread hỗ trợ runtime như `Reference Handler` hoặc `Common-Cleaner` nếu chúng xuất hiện; không mặc định mọi JDK đều hiển thị cùng tên.
- GC và compiler threads có thể xuất hiện trong **OS-level/native diagnostics** hoặc các chế độ/log riêng, nhưng có thể **không hiện đầy đủ trong `Thread.print`**. Vì vậy một Java thread dump **không chứng minh** “JVM không có GC worker”.
- So sánh nhiều snapshot theo thời gian để phân biệt thread đang làm việc bình thường với thread kẹt lâu. `RUNNABLE` riêng lẻ chưa phải bằng chứng CPU cao; `WAITING` riêng lẻ chưa phải lỗi.
- Nếu phân tích chi phí native thread stacks/metaspace/code cache, HotSpot cung cấp **Native Memory Tracking (NMT)** khi bật từ lúc khởi động, chẳng hạn `-XX:NativeMemoryTracking=summary`, rồi dùng `jcmd <pid> VM.native_memory summary`. NMT tiêu tốn overhead và chỉ tổng hợp loại bộ nhớ nó theo dõi.

```bash
# Khi cần theo dõi native memory; bật từ lúc khởi động ứng dụng
java -XX:NativeMemoryTracking=summary ThreadRolesDemo
# Sau đó với ứng dụng còn sống:
jcmd <pid> VM.native_memory summary
```

Các lệnh là ví dụ cho HotSpot/JDK tương thích; không có stdout cố định vì phụ thuộc PID, cấu hình, tuổi tiến trình và hoạt động runtime.

### Dấu hiệu thường gặp và hướng kiểm tra

| Triệu chứng | Điều cần kiểm tra trước |
| --- | --- |
| `main` đã in “done” nhưng tiến trình chưa thoát | Non-daemon worker/executor/server đang sống, shutdown hooks hoặc luồng của thư viện |
| Ứng dụng thoát khi còn tác vụ nền | Có phải chỉ còn daemon/virtual threads? Có `join`/shutdown coordination cho công việc cần hoàn tất không? |
| Số lượng thread tăng không ngừng | Thread pool cấu hình thiếu giới hạn, task bị kẹt, thread leak, server framework tạo worker |
| `OutOfMemoryError: unable to create native thread` | Giới hạn OS/process, memory dành cho thread stacks, số platform/native threads; không vội quy nguyên nhân cho Java heap |
| Pause đáng kể hoặc CPU dùng bởi collector | Thu thập GC logs/JFR, allocation pressure, collector và safepoint |
| CPU dùng nhiều tại thread JIT hoặc lúc warm-up | Phân tích compilation activity và workload; không suy ra code nghiệp vụ sai chỉ từ tên thread |
| Thread dump có nhiều `WAITING` hoặc `BLOCKED` | Đọc stack, lock owners, wait/notification và snapshot nhiều lần; phân biệt idle pool với deadlock |
| Rò rỉ bộ nhớ gắn với thread | `ThreadLocal` giữ tham chiếu, queues, listeners và lifetime của executor/container |

**Cảnh báo thực hành:** không gọi `Thread.stop()` để “diệt” một luồng kẹt, vì phương thức này đã lỗi thời và không an toàn. Ưu tiên cơ chế hủy hợp tác: `interrupt()`, `Future.cancel(true)` khi phù hợp, timeout, đóng tài nguyên và xử lý interruption đúng cách.

## 10. Những nhầm lẫn thường gặp

1. **“Đóng `main` thì JVM kết thúc.”** Sai khi vẫn còn non-daemon thread đang chạy hoặc hệ thống đang trong quá trình shutdown.
2. **“Daemon chính là thread nội bộ JVM.”** Sai; ứng dụng cũng có thể tạo daemon threads, như `application-helper` trong ví dụ.
3. **“Mọi tên thread trong dump đều do JVM tự tạo.”** Sai; server, logging frameworks và thread pools tạo nhiều tên riêng.
4. **“Java thread dump liệt kê toàn bộ OS/native threads.”** Không được bảo đảm. HotSpot có các thread nội bộ không trình bày dưới dạng Java `Thread` thông thường.
5. **“Thread `RUNNABLE` chắc chắn ăn CPU.”** Sai; nó có thể đang sẵn sàng hoặc tham gia thao tác native phụ thuộc ngữ cảnh.
6. **“GC thread luôn chạy đồng thời, không bao giờ dừng application.”** Sai; chiến lược GC có những pha dừng và pha concurrent khác nhau.
7. **“JIT compiler thread biên dịch mã Java nguồn như `javac`.”** Sai; JIT là tối ưu runtime từ bytecode tới mã máy.
8. **“Tạo một virtual thread Java 21 tương đương tạo một OS thread.”** Sai; virtual thread thường chạy lần lượt qua carrier platform threads.
9. **“Dùng `setDaemon(true)` giải quyết hoàn toàn chuyện đóng ứng dụng.”** Sai; có thể khiến tác vụ quan trọng kết thúc dở dang và không thay thế đóng resource rõ ràng.
10. **“Nhiều thread là chắc chắn chạy nhanh.”** Sai; vẫn có contention, scheduling overhead, giới hạn CPU/memory và khối lượng công việc không thể song song.

## 11. Tự kiểm tra

1. Nếu `main` trả về trong khi một `ExecutorService` vẫn duy trì non-daemon workers, JVM có bắt buộc thoát ngay không? Cần đóng executor thế nào?
2. Trong `ThreadRolesDemo`, vì sao helper là daemon nhưng vẫn luôn in dòng của nó? Điều gì thay đổi khi bỏ `helper.join()`?
3. Khi thread dump Java không hiện các tên “GC Thread”, có thể kết luận JVM không chạy GC không? Cần loại quan sát nào khác?
4. Vì sao `Reference Handler` và `C2 CompilerThread` là hai ví dụ dịch vụ runtime khác nhau? Luồng nào có thể là `java.lang.Thread`?
5. Nếu ứng dụng Java 21 tạo hàng nghìn virtual threads, tại sao số OS threads thường nhỏ hơn rất nhiều? Tại sao virtual threads ấy vẫn là luồng ứng dụng?
6. Một thread có trạng thái `BLOCKED` và một thread `WAITING` khác nhau ở điểm nào? Vì sao chỉ số lượng thread chưa đủ để chẩn đoán deadlock?

**Điểm cốt lõi:** JVM khởi tạo và vận hành **những luồng phục vụ chính bản thân runtime** trong khi **luồng ứng dụng** thực thi công việc của người dùng. Daemon status chỉ ảnh hưởng quy tắc giữ tiến trình sống; nó **không** xác định chủ sở hữu luồng. Khi vận hành, phải quan sát đúng mức Java-level, native VM và OS, rồi quản lý lifecycle thread ứng dụng một cách tường minh.

### Tài liệu gốc

- [Java SE 17 — `java.lang.Thread`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): tạo thread, `start()`, `join()` và daemon behavior.
- [Java SE 17 — `Thread.State`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.State.html): sáu trạng thái được quan sát từ Java.
- [JVM Specification 17 — Runtime Data Areas, Threads (`2.5)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5): nền tảng đặc tả ngữ cảnh chạy và dữ liệu của thread.
- [JVM Specification 21 — JVM Termination (`5.7)](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.7): termination và shutdown.
- [OpenJDK JEP 444 — Virtual Threads](https://openjdk.org/jeps/444): virtual threads, carrier, scheduling.
- [Oracle JDK 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): mô hình thực thi virtual thread.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-lifecycle-006">Khi main kết thúc: luồng không daemon và điều kiện JVM thoát</a>

<details>
<summary>Xem chi tiết</summary>

# Khi `main` kết thúc: luồng không daemon và điều kiện JVM thoát

Lời gọi `main(String[] args)` là **điểm vào của ứng dụng Java**, nhưng hoàn tất phương thức `main` **không đồng nghĩa** toàn bộ tiến trình JVM đã kết thúc. Sau khi `main` trả về, các luồng khác vẫn có thể chạy hoặc đang đợi; một luồng **non-daemon** còn sống có thể giữ tiến trình tiếp tục tồn tại. Ngược lại, những luồng **daemon** còn sống không tự chúng ngăn JVM bắt đầu quá trình shutdown.

Bài này giải thích quy tắc kết thúc JVM theo Java SE, phân biệt `main` với toàn bộ vòng đời ứng dụng, cách nhìn trạng thái daemon, cách quản lý worker và executor, và những ngoại lệ như `System.exit`, shutdown hooks và virtual threads.

## 1. Mô hình đúng: `main` là phương thức của một luồng, JVM là tiến trình có thể chứa nhiều luồng

Thông thường, Java launcher khởi động JVM, tải/khởi tạo lớp chính rồi gọi `main` trên một **luồng non-daemon**. Trong quá trình chạy, chương trình hoặc thư viện có thể khởi tạo thêm những luồng khác.

Ba sự kiện phải phân biệt:

1. **Phương thức `main` trả về** (hoặc ném exception chưa được xử lý): lời gọi vào `main` kết thúc; trong cách chạy launcher thông thường, luồng chính sau đó cũng kết thúc.
2. **Luồng `main` kết thúc**: JVM kiểm tra điều kiện kết thúc của *toàn bộ* chương trình; những luồng non-daemon đã được khởi động khác có thể vẫn còn sống.
3. **JVM/tiến trình kết thúc**: các luồng không còn tiếp tục thực thi Java code; hệ điều hành nhận lại tài nguyên tiến trình. Shutdown hooks có thể đã được chạy khi JVM kết thúc theo đường shutdown thông thường.

Trong JLS Java SE 17 `12.8, điều kiện kết thúc chương trình thông thường là **tất cả luồng non-daemon đã kết thúc**. Một đường khác là lời gọi `System.exit`/`Runtime.exit` được phép thực hiện. Tài liệu `Runtime` giải thích thêm quá trình shutdown hooks; các đường dừng đột ngột cần được xem riêng.

### Luồng non-daemon có còn sống không?

| Tình huống sau `main` | JVM có thể tự kết thúc ngay khi xét luồng? | Vì sao |
| --- | --- | --- |
| Không còn luồng non-daemon sống; chỉ còn daemon | Có, JVM bắt đầu shutdown | Daemon không giữ JVM tồn tại |
| Còn non-daemon đang thực thi công việc | Chưa | Nó vẫn là luồng sống |
| Còn non-daemon `sleep()`, `wait()`, `join()`, blocked I/O hoặc chờ khóa | Chưa | `WAITING`/`BLOCKED`/`TIMED_WAITING` không đồng nghĩa `TERMINATED` |
| Có đối tượng `Thread` non-daemon nhưng chưa gọi `start()` | Không vì riêng đối tượng đó | Thread chưa bắt đầu thì không phải luồng đang thực thi giữ JVM |
| Có non-daemon trước đó nhưng `run()` đã hoàn tất hoặc thoát vì exception | Không vì riêng luồng đã chết | Luồng đã kết thúc, dù object `Thread` còn được tham chiếu |
| Có non-daemon sống và một luồng gọi `System.exit(0)` | Có thể bắt đầu shutdown ngay | `System.exit` là đường kết thúc chủ động, không đợi mọi worker hoàn tất theo cơ chế đếm non-daemon |

Bảng giả định ứng dụng được chạy bằng Java launcher thông thường, không có non-daemon khác hoặc shutdown hook đang giữ tiến trình. “Có thể kết thúc” không hứa rằng lệnh shutdown mất thời gian bằng 0.

## 2. Daemon là thuộc tính ảnh hưởng đến thời điểm JVM tự shutdown

Một luồng `Thread` có cờ daemon:

- **Non-daemon (user thread):** khi còn sống, nó giữ JVM không tự kết thúc theo điều kiện “không còn non-daemon”.
- **Daemon:** vẫn là luồng Java thực sự và vẫn được lên lịch thực thi khi JVM chạy; nhưng một mình nó **không đủ** để giữ JVM sống.
- Phân loại daemon/non-daemon **không** biểu thị độ ưu tiên, mức CPU, tính an toàn của dữ liệu hoặc công việc quan trọng hay không.
- `Thread.isDaemon()` đọc trạng thái daemon; `Thread.isAlive()` cho biết luồng đã `start()` và chưa kết thúc. Cần kết hợp cả hai để hiểu liệu luồng có thể giữ JVM sống.
- Với **platform threads** truyền thống, `new Thread(runnable)` thường kế thừa cờ daemon của luồng *tạo đối tượng Thread*. Vì `main` trên launcher thường non-daemon, worker tạo từ `main` mặc định non-daemon.
- `setDaemon(boolean)` phải được gọi **trước `start()`**. Gọi sau khi luồng đã sống gây `IllegalThreadStateException`. Không có cách hợp lệ dùng `setDaemon` để biến một luồng đang chạy từ non-daemon thành daemon.
- Việc gán `setDaemon(true)` không “hủy” luồng khi `main` kết thúc; nó cho phép JVM kết thúc dù luồng ấy vẫn đang hoạt động nếu không còn luồng non-daemon khác.

Lưu ý sự khác nhau: **“daemon không giữ JVM tồn tại”** khác với **“daemon dừng ngay khi `main` trả về”**. Nếu một non-daemon khác còn sống, daemon vẫn có thể tiếp tục chạy. Khi JVM thực sự kết thúc, công việc daemon còn dang dở có thể bị chấm dứt mà không đi qua các khối cleanup thông thường.

## 3. Ví dụ A — `main` đã kết thúc nhưng non-daemon vẫn chạy

Tạo file **`MainAndWorker.java`**:

```java
public class MainAndWorker {
    public static void main(String[] args) {
        Thread mainThread = Thread.currentThread();

        Thread worker = new Thread(() -> {
            try {
                // Chỉ in khi luồng main thật sự đã kết thúc.
                mainThread.join();
                System.out.println("worker: main thread terminated");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("worker: interrupted");
            }
        }, "worker");

        // Dòng này minh họa rõ lựa chọn; mặc định vốn đã non-daemon
        // vì worker được tạo trên luồng main thông thường.
        worker.setDaemon(false);
        worker.start();

        System.out.println("main: returning");
    }
}
```

Chạy với **JDK 17+**:

```bash
javac --release 17 MainAndWorker.java
java MainAndWorker
```

**Stdout khi không có can thiệp bên ngoài**:

```text
main: returning
worker: main thread terminated
```

**Vì sao thứ tự output xác định?** `main` in dòng đầu, rồi trả về. `worker` chờ `mainThread.join()`; lệnh đó chỉ trả về bình thường sau khi luồng `main` kết thúc. Sau đó worker mới in dòng thứ hai. JVM không kết thúc giữa hai dòng vì worker là **non-daemon** đang sống. Sau khi worker hoàn thành `run()`, không còn non-daemon nào trong ví dụ này; JVM có thể shutdown.

Ví dụ cố ý dùng `join()` từ worker để chứng minh `main` đã *thật sự* kết thúc trước khi worker tiếp tục; một ví dụ chỉ đặt `sleep(1000)` rồi đoán thứ tự từ màn hình sẽ phụ thuộc lịch chạy của OS.

## 4. Ví dụ B — Daemon có thể bị bỏ lại khi `main` kết thúc

Tạo **`DaemonDoesNotKeepJVM.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class DaemonDoesNotKeepJVM {
    public static void main(String[] args) {
        Thread background = new Thread(() -> {
            try {
                // Một công việc chờ vô hạn vì latch không được mở.
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "background");

        background.setDaemon(true); // trước start()
        background.start();

        System.out.println("main: returning");
    }
}
```

Chạy:

```bash
javac --release 17 DaemonDoesNotKeepJVM.java
java DaemonDoesNotKeepJVM
```

**Stdout**:

```text
main: returning
```

Trong chương trình chạy độc lập này, sau khi `main` kết thúc sẽ không còn non-daemon của ứng dụng; `background` là daemon dù đang đợi latch. JVM có thể đi tới shutdown mà không cần latch về 0.

Ví dụ cố tình đặt công việc daemon ở trạng thái chờ để kết quả không phụ thuộc “máy nhanh hay chậm”. Nếu thay chỗ đợi bằng việc ghi file/chuyển dữ liệu và hy vọng daemon luôn hoàn tất trước khi JVM thoát, đó là một lỗi về quản lý vòng đời. Không dùng daemon làm bảo đảm hoàn thành ghi log, lưu giao dịch, flush buffer hay giải phóng tài nguyên.

## 5. Ví dụ C — Cần kết quả thì `join()` để chờ chủ động

Cho dù công việc chạy trên daemon hay non-daemon, nếu `main` cần bảo đảm hoàn tất trước khi trả về, **đợi kết quả** một cách rõ ràng.

File **`WaitForTask.java`**:

```java
public class WaitForTask {
    public static void main(String[] args) throws InterruptedException {
        Thread background = new Thread(
                () -> System.out.println("task: completed"),
                "background"
        );
        background.setDaemon(true);
        background.start();

        background.join();
        System.out.println("main: completed");
    }
}
```

```bash
javac --release 17 WaitForTask.java
java WaitForTask
```

**Stdout**:

```text
task: completed
main: completed
```

`join()` khiến `main` chỉ in dòng cuối sau khi thread đã hoàn tất. Khi worker là daemon, chính `main` (non-daemon) vẫn còn sống trong lúc đợi, nên JVM không tự thoát sớm. Nếu worker bị kẹt mãi, `join()` không timeout cũng có thể đợi mãi; có thể dùng `join(milliseconds)` rồi kiểm tra `isAlive()` và thực hiện chính sách timeout/cancellation phù hợp. `join()` có thể ném `InterruptedException` và cần xử lý theo contract của chương trình.

### Điều `join()` không bảo đảm

- `join()` chỉ chờ luồng được chỉ định; không tự chờ tất cả luồng con, thread pool hay hoạt động I/O không gắn với luồng đó.
- Nếu luồng kết thúc vì exception, `join()` vẫn hoàn tất; cần kênh riêng để báo thành công/thất bại, chẳng hạn `Future.get()`.
- `Thread.interrupt()` yêu cầu phối hợp/hỗ trợ gián đoạn, **không** phải lệnh cưỡng bức kết thúc.
- Tránh tạo vòng chờ: luồng A `join()` B trong khi B lại `join()` A, sẽ không thể hoàn tất bình thường.

## 6. ExecutorService: một nguyên nhân thực tế khiến ứng dụng “đã in done nhưng chưa thoát”

Ví dụ thường gặp:

```java
ExecutorService executor = Executors.newSingleThreadExecutor();
executor.submit(() -> System.out.println("task done"));
System.out.println("main done");
// Thiếu executor.shutdown()
```

Đây chỉ là **đoạn minh họa lỗi**, không phải file độc lập. `Executors.newSingleThreadExecutor()` theo cách dùng mặc định tạo worker **non-daemon** khi cần để chạy task. Ngay cả khi task trả về và `main` kết thúc, executor vẫn có thể duy trì worker đang chờ job kế tiếp. JVM không tự thoát. Việc gọi `Future.get()` để đợi riêng task hoàn tất cũng **không thay thế** `executor.shutdown()`.

Cách đầy đủ cho **Java 17+** — file **`ExecutorLifecycle.java`**:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExecutorLifecycle {
    public static void main(String[] args)
            throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> result = executor.submit(() -> 6 * 7);
            System.out.println("result=" + result.get());
        } finally {
            stopExecutor(executor);
        }
    }

    private static void stopExecutor(ExecutorService executor) {
        executor.shutdown(); // không nhận thêm task; xử lý task đã nhận
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // best-effort interrupt
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("executor: still running");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

```bash
javac --release 17 ExecutorLifecycle.java
java ExecutorLifecycle
```

**Stdout bình thường**:

```text
result=42
```

- `result.get()` chờ task trả số, đồng thời chuyển lỗi task thành `ExecutionException`. Nó không tự đóng executor.
- `shutdown()` từ chối task mới, vẫn xử lý những task đã submit; `awaitTermination()` đợi pool kết thúc đến hết timeout.
- `shutdownNow()` chỉ **cố gắng** dừng task đang chạy bằng interrupt và lấy ra các task đang chờ; task không hợp tác có thể tiếp tục sống. Vì vậy code còn kiểm tra lần nữa và ghi cảnh báo nếu không dừng được.
- Khi thread gọi shutdown đang bị interrupt, ví dụ bảo toàn interrupt flag sau yêu cầu hủy; ứng dụng thực tế phải quyết định chính sách riêng nếu timeout kéo dài.
- Với Java 17, không mặc định dùng `try (ExecutorService executor = ...)` theo cú pháp AutoCloseable; `ExecutorService` trong API Java 17 chưa có `close()`. Với release mới hơn, cần đọc API đúng phiên bản khi dùng cơ chế đóng mới.

Nếu một web server/listener được thiết kế để chạy liên tục, non-daemon giữ JVM sống là **hành vi đúng**. Vấn đề chỉ xuất hiện khi bài toán yêu cầu kết thúc mà không có đường dừng server/executor.

## 7. Điều gì thay đổi khi gọi `System.exit`?

Hai đường đi khác nhau:

### A. Kết thúc tự nhiên

`main` kết thúc → các non-daemon còn lại vẫn chạy → khi non-daemon cuối cùng chết, JVM bắt đầu shutdown → chạy các shutdown hook đã đăng ký → sau khi hook kết thúc, JVM kết thúc.

### B. Gọi `System.exit(status)` (hoặc `Runtime.getRuntime().exit(status)`)

JVM **bắt đầu quá trình shutdown** theo yêu cầu, kể cả khi vẫn có non-daemon đang chạy. Các shutdown hooks đã đăng ký được khởi chạy và có thể chạy đồng thời với những luồng khác; sau khi hooks xong, runtime tiến tới kết thúc. `status` là mã trạng thái yêu cầu trả cho môi trường gọi chương trình; `0` thường biểu thị kết thúc thành công theo quy ước hệ điều hành.

Do đó, `System.exit(0)` **không phải** phương án thay cho `join()` hoặc đóng executor. Nó không bảo đảm tất cả task đã hoàn thành, buffer đã được flush, hoặc `finally` của các luồng bị chấm dứt sẽ được chạy. Trong một server, thư viện hoặc test suite, việc tự gọi `System.exit` còn có thể kết thúc toàn bộ JVM ngoài ý muốn.

### Shutdown hooks liên quan thế nào?

Đăng ký một hook khi JVM còn hoạt động:

```java
Runtime.getRuntime().addShutdownHook(new Thread(
        () -> System.out.println("shutdown hook"),
        "shutdown-hook"
));
```

Đây là **đoạn trích minh họa**, không phải chương trình đầy đủ. Hook là một đối tượng `Thread` được đăng ký để JVM khởi chạy ở giai đoạn shutdown. Một hook **chưa start** không phải non-daemon worker giữ chương trình sống trước khi shutdown. Hooks có thể chạy **song song**, thứ tự thực thi không được bảo đảm; hook treo có thể làm quá trình shutdown không hoàn tất. Vì vậy:

- Dùng hook cho thao tác dọn dẹp cần thiết trong phạm vi thời gian hợp lý; tránh chờ vòng tròn hoặc phụ thuộc vào dịch vụ đang tự shutdown.
- Không dùng thứ tự output của hai hook để suy ra trình tự chắc chắn.
- Không kỳ vọng hook luôn chạy khi bị cưỡng bức dừng: `Runtime.halt()` bỏ qua shutdown sequence; lỗi native nghiêm trọng, process bị kill cưỡng bức hoặc mất điện cũng có thể bỏ qua hooks.
- Dù một hook có kết thúc, các luồng thường còn chạy khác không được JVM “join” tự động theo đường `System.exit`.

## 8. Từ Java 21: virtual threads luôn là daemon

**Phần này áp dụng Java 21+**, không thuộc public API Java 17. Trong Javadoc `Thread` Java 21, **virtual threads là daemon threads** và bản thân chúng không ngăn JVM bắt đầu shutdown. Không thể chuyển một virtual thread thành non-daemon bằng `setDaemon(false)`.

Ví dụ độc lập, file **`VirtualThreadLifetime.java`**:

```java
public class VirtualThreadLifetime {
    public static void main(String[] args) throws InterruptedException {
        Thread task = Thread.startVirtualThread(
                () -> System.out.println("virtual: completed")
        );

        task.join();
        System.out.println("main: completed");
    }
}
```

```bash
javac --release 21 VirtualThreadLifetime.java
java VirtualThreadLifetime
```

**Stdout**:

```text
virtual: completed
main: completed
```

`Thread.startVirtualThread` có từ Java 21. Nếu bỏ `task.join()`, chương trình có thể shutdown ngay khi `main` và những non-daemon khác kết thúc, khiến công việc của virtual thread chưa kịp hoàn tất. **Không “sửa” bằng cách đổi virtual thread thành non-daemon:** hãy tổ chức vòng đời tác vụ để đợi kết quả, có timeout và hủy khi cần.

Điều này làm quy tắc non-daemon đặc biệt quan trọng khi chuyển từ mô hình thread pool truyền thống sang virtual threads: API tạo task và cơ chế hoàn thành khác nhau, nhưng **trách nhiệm đợi các tác vụ thiết yếu vẫn thuộc ứng dụng**.

## 9. Kiểm tra khi chương trình không thoát

Khi terminal/CI vẫn chờ sau khi log `main completed`, kiểm tra có hệ thống:

1. **Có `System.exit`/`Runtime.halt` hay shutdown hook nào đã bắt đầu chưa?** Nếu có, đây là nhánh shutdown chủ động/hook, không chỉ câu chuyện `main`.
2. **Có non-daemon sống không?** Nhận diện tên, `Thread.isDaemon()` và `Thread.isAlive()`; thread còn `WAITING`, `TIMED_WAITING`, `BLOCKED` vẫn là luồng còn sống.
3. **Luồng đó đến từ đâu?** `new Thread`, `ExecutorService`/`ScheduledExecutorService`, server socket, I/O listener, scheduler, thư viện HTTP/DB, GUI/event loop hoặc callback tự tạo.
4. **Luồng đợi điều kiện nào?** `Lock`/monitor, `CountDownLatch`, `BlockingQueue.take()`, `Object.wait()`, `Thread.join()`, socket/accept hoặc task queue rỗng.
5. **Có đường dừng hợp lệ?** Dừng nhận tác vụ mới, đóng socket/listener, phát tín hiệu hủy, `shutdown()` executor, `awaitTermination()` hoặc chờ bằng `join()`, với timeout và chính sách xử lý sự cố.

Với một **HotSpot JDK** có các công cụ chẩn đoán tương ứng, có thể lấy thread dump bằng:

```bash
jcmd <PID> Thread.print
```

`<PID>` là ID tiến trình Java cần kiểm tra. Kết quả giúp xem danh sách luồng, tên, stack và trạng thái; tùy JDK/cách hiển thị, thông tin daemon và virtual threads có thể khác nhau. Đừng quyết định chỉ bằng chữ “RUNNABLE”: luồng non-daemon đang `WAITING` cũng đủ để chặn shutdown tự nhiên. Thread dump là ảnh chụp tại một thời điểm, có thể cần quan sát nhiều lần khi điều tra deadlock hoặc pool đang chờ.

**Lưu ý:** trạng thái của một thread là đặc điểm vòng đời của thread ấy, không phải trạng thái toàn JVM. Số thread thay đổi liên tục; không kết luận process đã/chưa exit chỉ từ log của `main`.

## 10. Các lỗi hiểu sai thường gặp

| Ngộ nhận | Sửa lại |
| --- | --- |
| “Cuối hàm `main` là cuối chương trình” | `main` kết thúc chỉ hoàn tất một luồng; những non-daemon khác có thể tiếp tục giữ tiến trình sống |
| “Có tham chiếu `Thread` thì JVM sẽ đợi” | Chỉ luồng **đã khởi chạy và còn sống** mới có ý nghĩa; object chưa start/đã chết không giữ JVM |
| “Luồng `WAITING` thì xem như đã dừng” | `WAITING` vẫn còn sống và non-daemon vẫn ngăn shutdown tự nhiên |
| “`daemon=true` là ưu tiên thấp hoặc auto cleanup” | Daemon chỉ liên quan quyền giữ JVM sống; công việc có thể bị cắt khi JVM kết thúc |
| “`setDaemon(true)` bất cứ lúc nào cũng được” | Với platform thread, phải gọi trước `start()` |
| “`Future.get()` là đóng executor” | `get()` đợi kết quả task; `shutdown()` mới yêu cầu pool chấm dứt |
| “`shutdownNow()` luôn dừng mọi worker” | Interrupt mang tính phối hợp/best effort; worker không hợp tác có thể vẫn chạy |
| “`System.exit(0)` luôn hoàn thành mọi dọn dẹp” | Nó khởi đầu shutdown, chỉ hooks có cơ chế chờ trong đường này; task khác không được đảm bảo |
| “Virtual thread tự giữ JVM sống như platform thread” | Java 21+ virtual threads luôn daemon |
| “Hook chắc chắn được gọi với mọi cách kill process” | Có những trường hợp terminate đột ngột mà hook không chạy |

## 11. Cách thiết kế vòng đời JVM rõ ràng

Một ứng dụng dạng batch/CLI nên có **điều kiện hoàn tất rõ ràng**: nhận công việc → chờ task cần thiết → thu lỗi → đóng executor/tài nguyên → trả về từ `main`. Một ứng dụng server nên có **điều kiện dừng rõ ràng**: yêu cầu shutdown → dừng nhận request → đợi các tác vụ còn lại trong giới hạn → đóng listener/executor → cho các non-daemon hoàn tất.

Sơ đồ quyết định ngắn:

```text
main() trả về
    |
    v
Còn non-daemon đã start và chưa kết thúc?
    | Có                     | Không
    v                        v
JVM tiếp tục sống      Bắt đầu shutdown
    |                        |
    v                        v
Worker xử lý/đợi       Chạy shutdown hooks
    |                        |
    v                        v
Non-daemon cuối chết   Hooks hoàn tất?
    |                        | Có
    +------> shutdown -------+----> JVM kết thúc
```

`System.exit(...)` là một đường riêng đi vào shutdown mà không cần đợi non-daemon cuối cùng chết; `Runtime.halt(...)` và một số sự cố bên ngoài không đi theo đường hook thông thường. Đừng dùng sơ đồ để suy ra rằng mọi hook luôn kết thúc: hook bị treo cũng có thể chặn shutdown.

### Checklist khi đọc hoặc review code

- Worker nào cần chạy đến hết? Có `join()`, `Future.get()`, completion coordination hoặc cách đồng bộ khác để bảo đảm?
- Tạo thread mới trong ngữ cảnh nào, daemon flag có được kiểm tra hoặc chủ động đặt trước `start()`?
- Executor/scheduler có đường `shutdown()` và chờ kết thúc không? Nếu có timeout thì chuyện gì xảy ra tiếp?
- Có thành phần chạy lâu dài như server, listener, event loop, GUI và cơ chế đóng chủ động không?
- Có chỗ nào gọi `System.exit` thay vì quản lý worker/tài nguyên không?
- Dùng JDK 17 hay 21+? Với virtual threads, luồng daemon không giữ JVM sống; đã có cơ chế chờ chưa?
- Shutdown hook có tránh block vô thời hạn, deadlock và phụ thuộc thứ tự hook khác không?

## Nguồn chuẩn để đối chiếu

- [JLS Java SE 17, `12.8 Program Exit](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.8): điều kiện kết thúc chương trình theo non-daemon và `System.exit`.
- [`Thread` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): daemon flag, `start()`, `join()`, trạng thái và kế thừa thuộc tính.
- [`Runtime` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html): `exit`, `halt`, `addShutdownHook` và shutdown sequence.
- [`ExecutorService` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): `shutdown`, `shutdownNow`, `awaitTermination`.
- [`Thread` — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): virtual threads luôn daemon; `Thread.startVirtualThread` có từ Java 21.

**Ghi nhớ:** `main` hết chỉ nói rằng công việc của luồng chính đã hết. JVM chỉ tự shutdown theo đường thông thường khi **không còn non-daemon sống**; nếu muốn ứng dụng kết thúc đúng và an toàn, hãy quản lý việc hoàn tất của các tác vụ và việc đóng tài nguyên một cách tường minh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-lifecycle-007">Shutdown hook, System.exit và Runtime.halt trong quá trình kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# Shutdown hook, System.exit và Runtime.halt trong quá trình kết thúc JVM

## Mục tiêu bài học

Khi kết thúc một tiến trình Java, điều quan trọng không chỉ là *khi nào* JVM dừng mà còn là *có thời gian dọn dẹp tài nguyên hay không*. Bài này giúp bạn:
- Giải thích những sự kiện kích hoạt quá trình shutdown của JVM, kể cả khi `main()` đã kết thúc.
- Hiểu cơ chế đăng ký, bắt đầu và hoàn tất **shutdown hook**, tính đồng thời và các giới hạn của nó.
- Phân biệt **kết thúc tự nhiên**, **`System.exit(int)` / `Runtime.exit(int)`** và **`Runtime.halt(int)`**.
- Viết, chạy và giải thích chương trình Java 17+ minh họa; nhận diện những trường hợp `finally` hoặc shutdown hook không được thực thi.
- Xây dựng chiến lược **graceful shutdown** có giới hạn thời gian cho ứng dụng nhiều luồng.

## 1. JVM bắt đầu shutdown vào lúc nào?

Một ứng dụng Java không nhất thiết dừng ngay khi phương thức `main()` trả về. Phương thức `main()` chạy trên một luồng; nếu còn những **non-daemon thread** đang sống, JVM thông thường vẫn tiếp tục hoạt động. JVM có thể bắt đầu trình tự shutdown khi:

1. **Không còn non-daemon thread sống**: các công việc nền không phải daemon đã hoàn thành và không còn luồng giữ JVM hoạt động.
2. **Có yêu cầu thoát chủ động**: chương trình gọi `System.exit(status)` hoặc `Runtime.getRuntime().exit(status)`.
3. **Có sự kiện bên ngoài được JVM nhận diện là yêu cầu kết thúc**: ví dụ một số tín hiệu từ hệ điều hành hoặc thao tác ngắt ứng dụng mà JVM hỗ trợ.

Ba loại trên có thể kích hoạt **shutdown sequence**. Trái lại, một số tình huống cưỡng bức kết thúc như **SIGKILL** trên Unix, **TerminateProcess** trên Windows, mất điện, lỗi native nghiêm trọng hoặc trình quản lý tiến trình cưỡng bức kết thúc có thể làm JVM dừng mà không cho hook chạy. Không được xem shutdown hook là cơ chế bảo đảm thực thi trong *mọi* trường hợp chết tiến trình.

### Non-daemon và daemon thread

- **Non-daemon** còn sống thường giữ JVM tồn tại sau khi `main()` kết thúc, cho đến khi shutdown được yêu cầu.
- **Daemon thread** không ngăn JVM bắt đầu shutdown chỉ vì nó còn sống.
- Khi shutdown đã bắt đầu, các hook chạy **đồng thời với những thread đang sống**, kể cả daemon và non-daemon. Vì vậy thread khác có thể vẫn đang thao tác trên tài nguyên mà hook định đóng.

Điều kiện “không còn non-daemon thread” là quy tắc quan trọng trong ứng dụng Java thông thường; nếu JVM được nhúng và điều khiển qua JNI Invocation API, chi tiết vòng đời còn liên quan đến mã native điều khiển JVM.

## 2. Shutdown hook là gì?

**Shutdown hook** là một đối tượng `Thread` đã được tạo nhưng **chưa được `start()`**, được đăng ký với runtime để JVM chủ động bắt đầu chạy khi đi vào shutdown sequence.

API chính:

~~~java
Runtime runtime = Runtime.getRuntime();

Thread hook = new Thread(() -> {
    // Dọn dẹp ngắn gọn, có kiểm soát.
}, "cleanup-hook");

runtime.addShutdownHook(hook);
~~~

Đoạn trên **minh họa API**, chưa phải lớp chương trình Java độc lập. Runtime sẽ tự gọi `start()` cho hook ở thời điểm shutdown; người viết **không được tự `hook.start()`** để “chạy thử” cùng đối tượng đã đăng ký. Việc khởi động hook thủ công trước shutdown có thể đưa trình tự shutdown vào trạng thái hành vi không được đặc tả.

Các API có liên quan:

| API | Tác dụng |
| --- | --- |
| `Runtime.getRuntime()` | Lấy đối tượng runtime hiện hành của ứng dụng. |
| `addShutdownHook(Thread hook)` | Đăng ký một `Thread` chưa được khởi động để chạy lúc shutdown. |
| `removeShutdownHook(Thread hook)` | Bỏ đăng ký hook; trả về `true` nếu hook đã đăng ký và được xóa. |
| `System.exit(int status)` | Cách gọi thông dụng để yêu cầu JVM bắt đầu shutdown với mã trạng thái. |
| `Runtime.exit(int status)` | API nền mà `System.exit` sử dụng. |
| `Runtime.halt(int status)` | Chấm dứt JVM ngay, bỏ qua chờ các hook và không khởi tạo shutdown sequence. |

**Chỉ có thể thêm/bỏ đăng ký khi shutdown chưa bắt đầu.** Sau khi trình tự shutdown bắt đầu, `addShutdownHook` và `removeShutdownHook` sẽ ném `IllegalStateException`. Đăng ký cùng một đối tượng hook lần nữa, hoặc một `Thread` đã chạy, không hợp lệ và có thể ném `IllegalArgumentException`. Trong một số môi trường Java 17 có SecurityManager, kiểm tra quyền liên quan cũng có thể từ chối thao tác.

## 3. JVM chạy các shutdown hook như thế nào?

Mô hình khái niệm:

~~~text
Ứng dụng đang chạy
      |
      | hết non-daemon thread / System.exit / sự kiện JVM nhận diện
      v
BẮT ĐẦU SHUTDOWN SEQUENCE
      |
      +--> JVM start hook A ----------------------+
      |                                          |
      +--> JVM start hook B ----------------------+--> Tất cả hook kết thúc
      |                                          |          |
      +--> JVM start hook C ----------------------+          v
      |                                             JVM kết thúc
      +--> Các thread đang chạy vẫn có thể chạy
           đồng thời trong giai đoạn shutdown
~~~

Những điều bắt buộc phải nhớ:

1. **Thứ tự gọi `start()` và thứ tự hoàn thành các hook không được bảo đảm**. Đăng ký A trước B **không có nghĩa A sẽ chạy trước B**.
2. **Các hook chạy đồng thời**; mỗi hook là một thread riêng. Dữ liệu chung phải bảo đảm thread safety, việc đóng cùng một tài nguyên cần có kiểm soát.
3. **JVM đợi tất cả hook kết thúc** trong shutdown sequence bình thường. Một hook bị kẹt vô hạn có thể làm tiến trình treo ở shutdown.
4. Hook có thể chạy cùng các thread ứng dụng khác; không nên mặc định “toàn bộ ứng dụng đã dừng”.
5. Hook ném ngoại lệ không được bắt sẽ được xử lý như ngoại lệ của thread thông thường; hook đó kết thúc, còn các hook khác có thể tiếp tục. Không nên dùng exception để làm phương thức “chặn toàn JVM”.
6. Việc **tạo và khởi động thread khác** trong shutdown sequence có thể xảy ra, nhưng phải điều phối cẩn thận: trình tự kết thúc được xác định bởi việc *các hook* đã hoàn tất, không phải mọi công việc được tạo thêm.

Do đó, hai hook phụ thuộc chặt vào nhau (ví dụ “đóng database” và “flush các hàng đợi ghi database”) rất dễ gặp lỗi thứ tự. Giải pháp thường là **gom các bước cần đúng thứ tự vào cùng một hook** hoặc một thành phần quản lý lifecycle, thay vì trông chờ thứ tự đăng ký.

## 4. Kết thúc tự nhiên khác `System.exit` ra sao?

**Kết thúc tự nhiên:** khi luồng cuối cùng không phải daemon hoàn thành, JVM bắt đầu shutdown. Hook đã đăng ký chạy rồi JVM kết thúc.

**`System.exit(status)`:** yêu cầu kết thúc chủ động, **kể cả khi còn non-daemon thread**. Đây là lời gọi có tác động đến *toàn tiến trình JVM*, không đơn thuần dừng luồng đang gọi. `System.exit(status)` là cách thuận tiện để gọi `Runtime.getRuntime().exit(status)`.

- Quy ước phổ biến: **`status = 0`** biểu thị thành công; **nonzero** biểu thị lỗi hoặc kết thúc bất thường. Ý nghĩa cụ thể của mã thoát thuộc về ứng dụng và môi trường gọi.
- Khi exit được chấp nhận, shutdown sequence được kích hoạt; các hook được chạy và JVM kết thúc sau khi chúng hoàn thành.
- Lời gọi `exit` không quay về bình thường; các dòng mã phía sau nó trong cùng luồng sẽ không chạy.
- Không được giả định các thread đang chạy sẽ tự hoàn thành tác vụ trước khi JVM kết thúc: nếu chưa được phối hợp, chúng có thể bị dừng khi toàn bộ hook kết thúc.

### Ví dụ 1 — `System.exit` vẫn chạy shutdown hook

Tạo tệp **`ExitDemo.java`**:

~~~java
public class ExitDemo {
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> System.out.println("hook: cleanup"), "cleanup"));

        System.out.println("main: request exit");
        System.exit(7);

        System.out.println("main: unreachable");
    }
}
~~~

Biên dịch và chạy trên **JDK 17+**:

~~~bash
javac ExitDemo.java
java ExitDemo
~~~

**Stdout chính xác** trong lần chạy độc lập thông thường:

~~~text
main: request exit
hook: cleanup
~~~

Lời gọi `System.exit(7)` kích hoạt hook rồi JVM kết thúc với **exit status 7**. Dòng `main: unreachable` không được in. Trong ví dụ này chỉ có một hook và không có công việc ứng dụng khác, nên thứ tự hai dòng là rõ ràng; với **nhiều hook** hoặc thread chạy song song, thứ tự đầu ra có thể không cố định.

### Khi nào nên gọi `System.exit`?

Trong chương trình dòng lệnh, `System.exit` phù hợp nếu cần trả mã thoát cụ thể cho shell/CI hoặc buộc cả ứng dụng kết thúc. Trong thư viện, request handler hoặc ứng dụng do framework quản lý, gọi `System.exit` từ mã nghiệp vụ thường không phù hợp vì nó dừng **toàn bộ JVM**. Thường nên trả lỗi/exception cho nơi điều phối và để vòng đời ứng dụng được quản lý tập trung.

## 5. `Runtime.halt`: chấm dứt cưỡng bức ngay lập tức

**`Runtime.getRuntime().halt(status)`** buộc JVM dừng ngay. Khác với `exit`, nó:
- **Không bắt đầu shutdown sequence** nếu sequence chưa bắt đầu.
- **Không đợi** các shutdown hook đang chạy nếu sequence đã bắt đầu.
- Có thể bỏ qua công việc dọn dẹp, flush, hoàn tất phương thức, `finally`, `try-with-resources` và xử lý exception còn dang dở.
- Có thể dẫn đến mất dữ liệu, file/transaction ở trạng thái chưa hoàn chỉnh, log chưa ghi đủ hoặc hệ thống khác chưa nhận tín hiệu kết thúc sạch.

Khi `halt` được gọi trong lúc một `exit` đang chờ hook, mã trạng thái truyền cho `halt` có thể **ghi đè exit status** dự kiến của `exit`. `halt` là biện pháp cuối cùng khi cần dừng JVM một cách cưỡng bức, không phải cách mặc định để xử lý lỗi ứng dụng.

### Ví dụ 2 — `halt` bỏ qua shutdown hook

Tạo tệp **`HaltDemo.java`**:

~~~java
public class HaltDemo {
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> System.out.println("hook: should not run")));

        System.out.println("main: before halt");
        System.out.flush();

        Runtime.getRuntime().halt(9);

        System.out.println("main: unreachable");
    }
}
~~~

Chạy trong một **tiến trình riêng** để không làm dừng chương trình khác:

~~~bash
javac HaltDemo.java
java HaltDemo
~~~

**Stdout của chương trình**:

~~~text
main: before halt
~~~

Hook không chạy; dòng `main: unreachable` không xuất hiện. Tiến trình kết thúc với **status 9** khi lời gọi `halt(9)` được thực hiện. `System.out.flush()` giúp đảm bảo thông điệp trước khi halt được đẩy ra stream; không nên suy diễn rằng `halt` có thể flush mọi buffer I/O hoặc bảo đảm dữ liệu ghi bền vững.

> **Cảnh báo về thí nghiệm:** các ví dụ `System.exit`/`Runtime.halt` làm **kết thúc JVM đang chạy chúng**. Hãy thử bằng lệnh `java TênLớp` trong tiến trình riêng, tránh gọi trực tiếp trong IDE console/plugin host hoặc máy chủ chứa tác vụ đang phục vụ người dùng.

## 6. `finally`, try-with-resources và shutdown hook: phạm vi bảo đảm khác nhau

`finally` và **try-with-resources** giúp dọn tài nguyên trong **luồng điều khiển Java bình thường** hoặc khi exception khiến stack unwind. Chúng *không* phải một cơ chế hook toàn JVM.

Ví dụ:

~~~java
public class ExitFinallyDemo {
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> System.out.println("hook: executed")));

        try {
            System.out.println("try: before exit");
            System.exit(5);
        } finally {
            System.out.println("finally: not executed");
        }
    }
}
~~~

Lệnh:

~~~bash
javac ExitFinallyDemo.java
java ExitFinallyDemo
~~~

**Stdout:**

~~~text
try: before exit
hook: executed
~~~

Đoạn `finally` của luồng gọi exit **không được thực hiện**, vì `System.exit` không trả về và không unwind stack như một exception thông thường. Hook chạy do JVM bước vào shutdown sequence. Nếu đổi `System.exit(5)` thành `throw new IllegalStateException("failed");`, `finally` sẽ chạy khi stack unwind; đó là cơ chế hoàn toàn khác.

So sánh:

| Cơ chế | Khi nào sử dụng | Điều gì cần lưu ý |
| --- | --- | --- |
| `try-with-resources`, `finally` | Đóng kết nối/tệp/lock ở cuối phạm vi công việc, kể cả khi ném exception | Dọn dẹp có tính cục bộ, dễ kiểm soát; không được bảo đảm khi JVM bị halt/kill hoặc gọi exit trước khi unwind. |
| Shutdown hook | Dọn dẹp lúc JVM bắt đầu **shutdown sequence** | Chạy best effort, đồng thời, không bảo đảm với forced termination. |
| `System.exit` / `Runtime.exit` | Bắt đầu shutdown chủ động và cung cấp exit status | Gọi ở cấp tiến trình; hook có cơ hội chạy; có thể ảnh hưởng thread đang hoạt động. |
| `Runtime.halt` | Cưỡng bức kết thúc toàn JVM ngay | Bỏ qua hoặc cắt ngang cleanup và hook; nguy cơ mất dữ liệu. |

**Nguyên tắc thiết kế:** sử dụng **try-with-resources cho tài nguyên có phạm vi rõ ràng**, quản lý vòng đời chính thức cho thành phần dịch vụ, và dùng shutdown hook làm “lưới an toàn” cho những tài nguyên sống đến cuối tiến trình. Không dồn tất cả việc dọn dẹp của ứng dụng vào hook.

## 7. Thiết kế shutdown hook an toàn cho ứng dụng nhiều luồng

Một shutdown hook tốt cần **nhanh, có giới hạn thời gian, idempotent khi phù hợp và an toàn khi chạy đồng thời**. Các bước graceful shutdown thường là:

1. **Ngừng nhận công việc mới** (server/listener/job scheduler).
2. **Báo cho công việc đang chạy dừng có trật tự**; nếu có thể, chờ trong một khoảng thời gian hữu hạn.
3. **Flush/commit dữ liệu cần thiết**, rồi đóng queue, pool, kết nối, file và telemetry/logging theo thứ tự phụ thuộc.
4. **Trả quyền điều khiển khỏi hook** để JVM có thể hoàn tất shutdown sequence; nếu quá hạn, cân nhắc interrupt/cancel đúng giao thức của thành phần.
5. **Đặt thứ tự trong cùng một hook hoặc lifecycle coordinator**, thay vì hy vọng các hook độc lập chạy theo thứ tự đăng ký.

### Ví dụ 3 — Shutdown executor có timeout

Tạo **`GracefulPoolDemo.java`**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GracefulPoolDemo {
    public static void main(String[] args) {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("hook: stopping executor");
            pool.shutdown(); // Không nhận task mới.
            try {
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    pool.shutdownNow(); // Yêu cầu interrupt các task còn chạy.
                }
            } catch (InterruptedException ex) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }, "executor-shutdown"));

        pool.submit(() -> System.out.println("task: completed"));
        System.out.println("main: request exit");
        System.exit(0);
    }
}
~~~

Lệnh:

~~~bash
javac GracefulPoolDemo.java
java GracefulPoolDemo
~~~

**Các dòng có thể xuất hiện:**

~~~text
task: completed
main: request exit
hook: stopping executor
~~~

Đây là **một đầu ra hợp lệ, không phải thứ tự được bảo đảm**. Tác vụ của executor có thể chạy trước hoặc sau dòng `main: request exit`; hook cũng chạy đồng thời với worker. Trong ví dụ đơn giản, hook gọi `shutdown()`, chờ task hoàn tất (tối đa hai giây) rồi kết thúc. Nếu task không tự dừng khi nhận interrupt, `shutdownNow()` chỉ là **yêu cầu ngắt**, không phải cơ chế cưỡng ép task chắc chắn hoàn thành. Khi hook đã kết thúc, JVM vẫn có thể dừng dù còn công việc khác.

Với ứng dụng thực tế, nên **bắt và ghi nhận lỗi dọn dẹp từng tài nguyên** để một lần đóng thất bại không vô tình bỏ qua các bước đóng tiếp theo. Nếu tài nguyên có thể được đóng từ luồng khác nữa, dùng cơ chế đồng bộ/trạng thái để tránh đóng đôi nguy hiểm. Không nên block mãi trong `join()`, `awaitTermination()`, khóa chéo hay lời gọi mạng không có timeout.

## 8. Những điều tuyệt đối tránh trong shutdown hook

### Gọi `System.exit` từ chính hook

Đây là một lỗi nghiêm trọng: khi JVM đang chờ tất cả hook kết thúc, lời gọi `System.exit`/`Runtime.exit` bên trong hook có thể bị **chặn vô thời hạn** và khiến quá trình shutdown không hoàn thành. Muốn thông báo lỗi cleanup, **ghi nhận/log** rồi cho hook kết thúc; đừng tự gọi `exit` lần nữa.

### Đợi hook khác hoặc phụ thuộc tài nguyên hook khác đang đóng

Do các hook chạy đồng thời không có thứ tự, **hook A đợi B**, trong khi B cần lock do A giữ, sẽ deadlock. Tương tự, chờ một service (logging, AWT event thread, thread pool) vốn đã được hook khác đóng có thể treo. Nếu có chuỗi phụ thuộc A→B→C, hãy cho một lifecycle coordinator chủ động sắp thứ tự, hoặc thiết kế từng bước đóng với hợp đồng rõ ràng.

### Thực hiện giao dịch/phép tính quá dài

Nền tảng như container/orchestrator có thể chỉ cho tiến trình một **grace period** hữu hạn trước khi buộc dừng. Hook có nhiệm vụ kết thúc nhanh; không phải chỗ thích hợp cho backup kéo dài, tương tác người dùng, hoặc khởi chạy luồng công việc nghiệp vụ mới. Vẫn cần lưu/commit định kỳ trong luồng xử lý bình thường.

### Cho rằng hook luôn chạy

Một số lệnh hủy tiến trình, crash native, mất nguồn hoặc `Runtime.halt` có thể khiến hook không chạy. Thiết kế ứng dụng phải chịu được **crash recovery**, ví dụ dựa vào transaction, ghi log bền vững, message acknowledgment hoặc cơ chế retry/idempotency, thay vì dùng hook làm nơi duy nhất lưu trạng thái.

### Dùng hook để “cứu” dữ liệu chưa ghi khi mất tiến trình

Hook là **best-effort cleanup**. Nó không thay thế journal/transaction, write-ahead log, fsync khi cần, cơ chế khôi phục hay quy trình graceful shutdown của framework. Một lệnh `println` lúc kết thúc cũng không chứng minh dữ liệu đã ghi bền vững vào ổ đĩa.

## 9. Nhận biết vấn đề thực tế

| Triệu chứng | Nguyên nhân cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| Kết thúc `main()` nhưng tiến trình Java vẫn sống | Còn non-daemon thread, thread pool hoặc timer chưa dừng | Kiểm tra thread dump; đóng executor/service đúng vòng đời. |
| Gọi `System.exit` mà chương trình không thoát | Hook treo, deadlock hoặc chờ I/O vô hạn | Thread dump trong lúc shutdown; thêm timeout và log các bước cleanup. |
| Log của nhiều hook đảo thứ tự giữa các lần chạy | Hook chạy đồng thời, thứ tự không bảo đảm | Gộp các bước phụ thuộc vào một coordinator. |
| Hook không in log khi bị `kill -9` | SIGKILL không cho JVM shutdown sạch | Giám sát/crash recovery, đừng dựa vào hook cho độ bền dữ liệu. |
| Đăng ký hook muộn báo `IllegalStateException` | JVM đã vào shutdown sequence | Đăng ký từ giai đoạn khởi tạo ứng dụng; không đăng ký khi đang dừng. |
| Hook chưa chạy nhưng lập trình viên đã gọi `hook.start()` | Hook được tự khởi động; làm sai hợp đồng đăng ký | Chỉ tạo và `addShutdownHook`, để JVM tự start. |
| Thêm cùng hook lần thứ hai báo `IllegalArgumentException` | Đối tượng `Thread` đã đăng ký hoặc đã chạy | Quản lý một lần đăng ký cho mỗi hook. |
| Máy chủ bị mất request dở dang khi exit | Shutdown sequence không tự bảo đảm thread nghiệp vụ xong | Dừng nhận request, drain, timeout, rồi mới đóng tài nguyên. |
| Dùng `Runtime.halt` khiến log/file dở dang | `halt` bỏ qua cleanup và cắt ngang threads | Dùng `halt` chỉ khi bắt buộc cưỡng bức và có phương án recovery. |

### Góc nhìn trong server/container

Một ứng dụng service thường có **quy trình shutdown do framework điều phối**. Ví dụ, framework có thể đóng HTTP listener, hủy lịch job, đợi request đang xử lý, đóng pool, flush logging. Nếu bạn tự thêm hook, hãy kiểm tra nó có “tranh” tài nguyên với hook của framework hay không. Đối với container/orchestrator, kiểm tra tín hiệu gửi đến tiến trình Java, timeout cho việc dừng và giới hạn tài nguyên. Không giả định rằng tất cả môi trường đều gửi cùng tín hiệu hoặc luôn đủ thời gian.

## 10. Tự kiểm tra

**Câu 1.** `main()` đã return nhưng một `new Thread(...).start()` không phải daemon vẫn chạy. JVM có bắt buộc shutdown ngay không?

**Đáp án:** Không. Non-daemon thread còn sống thường giữ JVM hoạt động. JVM bắt đầu shutdown tự nhiên khi không còn non-daemon thread.

**Câu 2.** Hai hook A và B đăng ký theo thứ tự A rồi B. B có chắc chạy sau A và được bảo đảm nhìn thấy tài nguyên đã được A dọn không?

**Đáp án:** Không. Các hook được start theo thứ tự không xác định và chạy đồng thời. Phải tự thiết kế thứ tự/phối hợp.

**Câu 3.** `System.exit(4)` có làm code trong `finally` của luồng đang gọi được chạy tự động không? Hook đã đăng ký có được chạy không?

**Đáp án:** `System.exit` không unwind stack của luồng gọi như exception nên không tự chạy `finally` tại vị trí đó. Nếu JVM bắt đầu shutdown sequence thành công thì shutdown hook được chạy, trừ tình huống cưỡng bức hoặc lỗi ngoài khả năng bảo đảm.

**Câu 4.** Một hook chờ vô hạn; mọi hook khác hoàn tất. Khi nào JVM kết thúc bình thường?

**Đáp án:** Khi **mọi hook** kết thúc. Nếu một hook treo vô hạn, shutdown sequence có thể không hoàn thành; `halt` hoặc cưỡng bức từ môi trường có thể cắt ngang nhưng rủi ro mất dữ liệu.

**Câu 5.** Gọi `Runtime.halt(9)` trước khi shutdown bắt đầu. Hook có chạy không và `finally` có bảo đảm chạy không?

**Đáp án:** Không. `halt` không bắt đầu shutdown sequence, kết thúc JVM ngay; hook và `finally` không được bảo đảm thực thi.

**Câu 6.** Khi ứng dụng bị SIGKILL trên Linux, có nên mong đợi hook lưu transaction cuối cùng?

**Đáp án:** Không. SIGKILL không tạo cơ hội cho JVM chạy cleanup. Cần bảo đảm nhất quán bằng cơ chế lưu bền vững, transaction và phục hồi khi khởi động lại.

## 11. Mô hình ghi nhớ

~~~text
Kết thúc tự nhiên / System.exit / sự kiện được JVM xử lý
                        |
                        v
                SHUTDOWN SEQUENCE
                 /      |      \
              hook A  hook B   hook C  (đồng thời, không bảo đảm thứ tự)
                 \      |      /
                     xong tất cả
                        |
                        v
                     JVM dừng

Runtime.halt --------------> JVM dừng ngay, bỏ qua/chặn ngang hooks

SIGKILL/crash --------------> JVM có thể bị dừng, không bảo đảm hooks
~~~

**Điểm cốt lõi:** shutdown hook là **cơ hội dọn dẹp trước khi JVM tắt**, `System.exit` là **yêu cầu bắt đầu kết thúc có thực hiện shutdown sequence**, còn `Runtime.halt` là **cưỡng bức chấm dứt không chờ cleanup**. Với dịch vụ cần độ tin cậy cao, dọn dẹp phải ngắn, có thứ tự, giới hạn thời gian và có cơ chế khôi phục sau khi tiến trình chết đột ngột.

## Tài liệu chuẩn để tra cứu

- [Oracle JDK 17 — `java.lang.Runtime`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html): `addShutdownHook`, `removeShutdownHook`, `exit`, `halt` và các lưu ý shutdown.
- [Oracle JDK 17 — `java.lang.System`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html): `System.exit`.
- [Java Virtual Machine Specification 21 — §5.7 JVM Termination](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.7): các trường hợp kết thúc JVM.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-lifecycle-008">Khác biệt giữa kết thúc bình thường, ngoại lệ không được bắt và JVM bị dừng cưỡng bức</a>

<details>
<summary>Xem chi tiết</summary>

# Khác biệt giữa kết thúc bình thường, ngoại lệ không được bắt và JVM bị dừng cưỡng bức

## 1. Phân biệt thread, JVM và tiến trình

Khi nói “ứng dụng Java kết thúc”, phải xác định chính xác đối tượng đang dừng:

- **Thread kết thúc:** phương thức run() hoàn thành hoặc Throwable không được bắt thoát khỏi thread. Những thread khác vẫn có thể chạy.
- **JVM bắt đầu shutdown có trật tự:** không còn non-daemon thread hoặc mã gọi System.exit(status), hoặc môi trường đưa JVM vào luồng shutdown được hỗ trợ. JVM khởi chạy các shutdown hook đã đăng ký theo quy tắc của nó rồi kết thúc.
- **Tiến trình bị chấm dứt cưỡng bức:** Runtime.halt(status), SIGKILL, một số trường hợp native crash hoặc OS ép dừng có thể kết thúc mọi thread mà không chạy shutdown hook Java.

**Exit code** là kết quả của *tiến trình*, không phải đặc tính của từng thread. Theo quy ước, 0 thường chỉ thành công và khác 0 thường chỉ lỗi, nhưng code không đủ nói cơ chế dừng: System.exit(7) có thể chạy shutdown hook; Runtime.halt(7) thì không. Ngoại lệ không được bắt ở worker cũng không nhất thiết làm tiến trình có exit code khác 0.

~~~text
Một thread kết thúc
    |
    +-- Còn non-daemon thread? -- Có --> JVM tiếp tục chạy
    |                        |
    |                        +-- Không --> JVM bắt đầu shutdown
    |
    +-- System.exit(status) được gọi --> JVM bắt đầu shutdown
    |
Tiến trình bị Runtime.halt()/SIGKILL/crash nghiêm trọng
    |
    +-- Chấm dứt đột ngột; không trông đợi Java shutdown hook
~~~

Các đường này độc lập: SIGKILL từ hệ điều hành không cần chờ thread Java nào kết thúc.

## 2. Bảng so sánh ba tình huống

| Khía cạnh | Hoàn thành bình thường | Ngoại lệ không được bắt | Dừng cưỡng bức |
| --- | --- | --- | --- |
| Ví dụ | main() trả về và các non-daemon thread hết; hoặc System.exit(status) yêu cầu shutdown | IllegalStateException đi ra khỏi main()/run() | Runtime.halt(), SIGKILL, OS/container ép kill, một số native crash |
| Đối tượng kết thúc trước | Thread hoàn thành hoặc JVM nhận lệnh shutdown | **Thread mang exception** chết trước; JVM có thể vẫn hoạt động | Thường **toàn bộ tiến trình** bị dừng |
| finally | Chạy khi rời scope bình thường; riêng System.exit() **không unwind stack** để thực hiện finally | Chạy khi stack unwinding đi qua khối finally, trừ khi có đường dừng abrupt khác | Không được bảo đảm; halt/SIGKILL không tháo stack Java |
| Shutdown hook | Được chạy khi JVM thực sự shutdown có trật tự | Chỉ chạy **nếu JVM sau đó shutdown** | Không chạy với Runtime.halt()/SIGKILL; với crash cũng không thể tin cậy |
| Exit status | main hoàn tất tự nhiên thường là 0; System.exit(status) dùng status đã yêu cầu | Main uncaught exception thường tạo code khác 0 trên Java launcher; worker lỗi riêng chưa tạo process exit | Runtime.halt(status) dùng status; tín hiệu OS do nền tảng quyết định |
| Cleanup | Có thể dùng scope/try-with-resources; hook hỗ trợ shutdown | finally của thread lỗi có thể chạy; thread khác vẫn chịu trách nhiệm cleanup riêng | Phải giả định Java cleanup có thể chưa chạy |

Kết thúc **tự nhiên** và shutdown **có chủ đích** qua System.exit() đều có thể có hook nhưng không có cùng đường control flow: System.exit() không quay về bình thường, nên finally của stack gọi exit không được thực hiện chỉ vì shutdown đã bắt đầu.

## 3. Kết thúc tự nhiên: main return chưa chắc JVM exit

JVM thường bắt đầu shutdown tự nhiên khi **mọi non-daemon thread đã khởi chạy đều kết thúc**. Thread main do launcher tạo thường là non-daemon, nhưng nó không nhất thiết là non-daemon cuối cùng.

- main() return mà còn một worker non-daemon: tiến trình tiếp tục.
- main() return và không còn non-daemon thread: JVM bắt đầu shutdown.
- Daemon thread không giữ JVM sống; một daemon đang làm việc có thể không hoàn thành khi JVM shutdown.
- Thread mới thường kế thừa trạng thái daemon từ thread tạo ra nó (trừ khi factory/công cụ quy định khác). Không nên suy ra mọi thread do mọi thư viện tạo đều non-daemon.

Tạo tệp **MainReturns.java**:

~~~java
public class MainReturns {
    public static void main(String[] args) {
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(200);
                System.out.println("WORKER_DONE");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("WORKER_INTERRUPTED");
            }
        }, "worker");

        worker.start();
        System.out.println("MAIN_RETURN");
    }
}
~~~

~~~bash
javac --release 17 MainReturns.java
java MainReturns
~~~

Đường chạy không bị interrupt cho đầu ra:

~~~text
MAIN_RETURN
WORKER_DONE
~~~

main() đã return trước, nhưng worker non-daemon vẫn giữ JVM sống. Thời gian sleep chỉ minh họa thứ tự; trong ứng dụng thực dùng join(), Future, structured concurrency phù hợp hoặc vòng đời ExecutorService có quản lý. Nếu một non-daemon thread pool không đóng, ứng dụng có thể “không chịu thoát” dù main() đã kết thúc.

### System.exit() là yêu cầu shutdown, không phải return

System.exit(status) gọi cơ chế Runtime.exit(status), yêu cầu kết thúc JVM với mã trạng thái tương ứng, kể cả khi vẫn có những thread không daemon khác đang chạy. JVM vào shutdown sequence và chạy hook đã đăng ký. Lời gọi **không trả về bình thường**, không chủ động unwind toàn bộ stack của thread hiện tại; nếu cần đóng tài nguyên trong một khối try, thực hiện cleanup **trước khi gọi exit**.

## 4. Ngoại lệ không được bắt: chỉ thread gây lỗi kết thúc trước

Khi Throwable không được catch và thoát khỏi phương thức gốc của một thread, thread đó chết. JVM có thể chuyển Throwable cho một UncaughtExceptionHandler để báo lỗi. Handler ghi nhận sự cố, **không tiếp tục chạy** đoạn code đã ném exception.

Với thread main:

1. Khi ngoại lệ lan truyền qua các scope, finally và try-with-resources tương ứng có cơ hội thực hiện cleanup trong quá trình tháo stack.
2. Nếu ngoại lệ thoát khỏi main(), thread main chấm dứt. Handler mặc định của Java launcher thường in stack trace lên **stderr**.
3. Nếu còn non-daemon thread, JVM vẫn có thể chạy. Nếu không còn, JVM bắt đầu shutdown và các hook được khởi chạy.
4. Với Java launcher thông dụng, uncaught exception ở main thường cho exit status khác 0, nhưng đặc tả JVM không yêu cầu mọi lỗi ở mọi thread phải tạo một exit code cố định.

Với worker thread, những thread khác có thể hoạt động bình thường. Trong ExecutorService, hành vi phụ thuộc cách submit task: lỗi ở Future thường được lưu để Future.get() đưa ra ExecutionException; không nhất thiết đi qua uncaught handler mặc định. Không được đồng nhất “task throw” với “VM chết”.

UncaughtExceptionHandler là đường **quan sát thất bại**, không phải catch có khả năng cho thread tiếp tục. Nếu handler tự lỗi, handler đó cũng không giúp phục hồi thread.

## 5. JVM shutdown có trật tự và shutdown hook

Đăng ký trước khi shutdown bắt đầu:

~~~java
Runtime.getRuntime().addShutdownHook(
        new Thread(() -> System.out.println("cleanup"), "cleanup-hook"));
~~~

Khi JVM shutdown có trật tự, nó khởi chạy những hook đã đăng ký và chờ chúng hoàn thành. Những điểm cần hiểu chính xác:

- Nhiều hook chạy theo thứ tự **không xác định** và có thể **đồng thời**. Không thiết kế hook A phải hoàn thành rồi hook B mới chạy.
- Không được thêm/xóa hook khi shutdown đã khởi động; thao tác có thể ném IllegalStateException.
- Hook phải nhanh, có khả năng xử lý lỗi và tránh chờ vô hạn. Nếu hook tự deadlock, tiến trình có thể treo trong giai đoạn shutdown.
- Các thread ứng dụng khác có thể vẫn còn chạy trong shutdown sequence; không giả định chúng đã được join hoặc resource của chúng còn ở trạng thái sẵn sàng.
- Gọi System.exit() từ hook đang chạy có thể block vô thời hạn; không dùng nó để “exit lại”.
- Hook không bảo đảm chạy khi Runtime.halt(), SIGKILL hoặc crash/OS kill đột ngột; hook cũng có thể bị cắt ngang nếu orchestrator hết grace period.

Hook phù hợp cho cleanup cuối cùng có giới hạn và logging cuối; không phải nơi duy nhất chứa transaction commit, phát message quan trọng hay flush toàn bộ dữ liệu chưa bền vững.

## 6. Dừng cưỡng bức: Runtime.halt, SIGKILL, tín hiệu và crash

**Runtime.getRuntime().halt(status)** chấm dứt JVM ngay, **không khởi chạy shutdown hook** và không thực hiện Java stack unwinding cho finally còn chờ. Nó là cơ chế đặc biệt, không phải API chuẩn để kết thúc nghiệp vụ bình thường.

Một số đường dừng cấp tiến trình:

- **SIGKILL / kill -9** trên Unix/Linux không thể được tiến trình bắt hoặc trì hoãn; Java hook không có cơ hội chạy.
- **Container/OS ép kill:** hệ thống quản lý có thể gửi tín hiệu dừng và cho một grace period, sau đó giết tiến trình nếu nó chưa thoát.
- **Native/JVM crash:** có thể abort quá trình; một số JVM như HotSpot có thể sinh hs_err_pid*.log hoặc core dump nếu điều kiện cho phép, nhưng không cam kết có file cho mọi trường hợp.
- **Mất điện/OS chết:** dữ liệu chỉ nằm trong RAM, buffer hoặc chưa đồng bộ ổ lưu trữ có thể mất.

Phải phân biệt **SIGTERM** với **SIGKILL**. SIGTERM thường là yêu cầu dừng *có thể xử lý*: HotSpot trên nhiều môi trường Linux/Unix thường chuyển nó thành shutdown có hook, nhưng hành vi phụ thuộc JVM implementation, native signal handler và cấu hình/host. **SIGKILL** luôn là ép kill không thể catch. Ctrl+C, SIGINT, ProcessHandle.destroy() và thao tác stop service cũng có chi tiết phụ thuộc nền tảng. Không hứa tất cả hệ điều hành đều chạy hook khi nhận một “lệnh dừng”.

## 7. Thí nghiệm kiểm chứng trên Java 17+

Tạo tệp **JvmExitCases.java**. Mỗi lần chạy lệnh java là **một tiến trình riêng**, để exit/halt không ảnh hưởng thí nghiệm khác:

~~~java
public final class JvmExitCases {
    private static void log(String message) {
        System.out.println(message);
        System.out.flush();
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Choose normal, uncaught, worker, exit, or halt");
        }

        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> log("HOOK"), "cleanup-hook"));

        String mode = args[0];
        if ("worker".equals(mode)) {
            Thread worker = new Thread(() -> {
                throw new IllegalStateException("worker-boom");
            }, "worker");

            worker.setUncaughtExceptionHandler((thread, error) ->
                    log("HANDLER: " + thread.getName() + ": "
                            + error.getClass().getSimpleName()));

            worker.start();
            worker.join();
            log("MAIN_CONTINUES");
            return;
        }

        try {
            log("BODY");
            switch (mode) {
                case "normal" -> { }
                case "uncaught" -> throw new IllegalStateException("boom");
                case "exit" -> System.exit(7);
                case "halt" -> Runtime.getRuntime().halt(9);
                default -> throw new IllegalArgumentException(
                        "Unknown mode: " + mode);
            }
        } finally {
            log("FINALLY");
        }
    }
}
~~~

Biên dịch và chạy riêng từng mode:

~~~bash
javac --release 17 JvmExitCases.java
java JvmExitCases normal
java JvmExitCases uncaught
java JvmExitCases worker
java JvmExitCases exit
java JvmExitCases halt
~~~

Để xem mã kết thúc **ngay sau lệnh cần kiểm tra**:

~~~bash
# Bash trên Linux/macOS
java JvmExitCases exit
echo $?

# PowerShell trên Windows
java JvmExitCases exit
echo $LASTEXITCODE
~~~

### 7.1. normal

Stdout:

~~~text
BODY
FINALLY
HOOK
~~~

Main kết thúc, finally chạy; khi hết non-daemon thread, JVM vào shutdown và chạy hook. Exit code thông thường là **0**.

### 7.2. uncaught

Các dòng **stdout**:

~~~text
BODY
FINALLY
HOOK
~~~

Ngoài ra mặc định còn có stack trace IllegalStateException với thông điệp boom ở **stderr**. Khi gộp hai stream, thứ tự xuất hiện giữa stack trace và dòng HOOK có thể khác nhau. Trên Java launcher thông dụng mã kết thúc là khác 0 (OpenJDK thường là 1); không dùng giá trị 1 làm bảo đảm từ đặc tả JVM. Khác normal ở đây là **main chết do exception**, dù hook vẫn chạy khi JVM shutdown.

### 7.3. worker

Stdout:

~~~text
HANDLER: worker: IllegalStateException
MAIN_CONTINUES
HOOK
~~~

Worker chết; handler riêng ghi lỗi; main join() thấy thread đã hoàn tất rồi tiếp tục. JVM chỉ shutdown khi main return và không còn non-daemon thread. Mã kết thúc thông thường là **0**. Vì đã cài handler riêng không in stack trace, ví dụ không có stack trace mặc định của worker.

### 7.4. exit

Stdout:

~~~text
BODY
HOOK
~~~

Không có FINALLY. System.exit(7) yêu cầu shutdown có hook nhưng **không** thoát qua finally trên stack hiện thời; mã kết thúc là **7**.

### 7.5. halt

Stdout đã chủ động flush trước khi halt:

~~~text
BODY
~~~

Không có FINALLY và không có HOOK; mã kết thúc là **9**. Flush ở ví dụ giúp dòng BODY được quan sát, nhưng halt có thể làm mất dữ liệu ở buffer khác. Không dùng đường halt cho logic xử lý ứng dụng bình thường.

**Kiểm chứng chạy thực tế:** mã này đã được biên dịch bằng JDK 21 với --release 17 và chạy cả năm mode. Kết quả stdout và status tương ứng 0, 1, 0, 7, 9 trên môi trường kiểm chứng; dòng exception của main nằm trên stderr và cách các stream được công cụ log ghép không cố định.

## 8. finally, try-with-resources và shutdown hook khác nhau

| Cơ chế | Khi nào dùng | Giới hạn |
| --- | --- | --- |
| try-with-resources | Đóng AutoCloseable lúc rời scope, kể cả khi exception đi qua scope | Halt/kill trước khi rời scope có thể bỏ dở cleanup |
| finally | Xử lý khi thoát khỏi khối try theo control flow hay stack unwinding của exception | System.exit không tự chạy finally đang nằm trên stack; halt/kill cũng không |
| Shutdown hook | Dọn dẹp khi JVM thực sự bắt đầu shutdown có trật tự | Không chạy trên mọi đường dừng; thứ tự nhiều hook không xác định |

Ví dụ đóng writer theo scope, không chờ shutdown hook:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ScopedResource {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("sample.txt");
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write("important data");
        }
        System.out.println("FILE_CLOSED");
    }
}
~~~

Trong đường thành công, writer được đóng trước khi in FILE_CLOSED; nếu write() ném IOException, try-with-resources vẫn cố đóng writer. Dù vậy **close/flush tại Java layer không đồng nghĩa dữ liệu đã được fsync/bền vững trước mất điện**. Bảo toàn dữ liệu đòi hỏi thiết kế theo hệ lưu trữ, transaction, retry và phục hồi phù hợp.

Không đợi shutdown mới commit mọi message, transaction hoặc trạng thái nghiệp vụ. Việc ghi nhận tiến độ theo từng đơn vị công việc và cơ chế chạy lại an toàn giúp ứng dụng chịu được những lần bị SIGKILL hoặc crash.

## 9. Khi chương trình “không chịu thoát” hoặc chết bất ngờ, điều tra thế nào?

1. **Xác nhận JVM/process còn sống hay đã chết.** Nếu main kết thúc mà process không exit, kiểm tra non-daemon thread, thread pool, network service và thread dump.
2. **Xem exit status, stack trace, stderr và log vòng đời.** Exception không bắt của worker không chứng minh tiến trình chết; mã exit khác 0 không chứng minh SIGKILL.
3. **Xem nguồn yêu cầu dừng:** System.exit, signal, service manager, container orchestrator, OOM killer, timeout, crash native. Với container, kiểm tra cả nguyên nhân restart và grace period.
4. **Xem hook đã bắt đầu/kết thúc chưa:** hook có thể treo vì lock, join, network call hoặc phụ thuộc một thread đã ngừng. Thiếu dòng HOOK không tự chứng minh SIGKILL vì log cũng có thể bị mất.
5. **Kiểm chứng tài nguyên phía ngoài:** database commit, message ack, file flush/sync, lease timeout và khả năng replay. Chỉ nhìn thấy log cleanup không bảo đảm dữ liệu đã bền vững.
6. **Sửa tại vòng đời đúng:** đóng executor/server theo giao thức, chặn nhận công việc mới, đợi bounded timeout rồi hoàn tất có kiểm soát; giữ hook gọn và có giới hạn.

Các nhầm lẫn thường gặp:

- “main() return là process thoát”: sai nếu còn non-daemon thread.
- “Một exception không bắt luôn giết JVM”: sai, trước tiên nó chỉ kết thúc thread bị lỗi.
- “System.exit chạy mọi finally”: sai; nó dùng shutdown hook chứ không unwind mọi stack.
- “System.exit(7) giống Runtime.halt(7)”: sai, cùng status nhưng khác cleanup.
- “SIGTERM giống SIGKILL”: sai, SIGTERM có thể được xử lý, SIGKILL thì không.
- “Hook chạy theo thứ tự đăng ký”: sai, thứ tự không xác định và có thể song song.
- “Có shutdown hook là bảo đảm dữ liệu an toàn khi mất điện”: sai.

## 10. Câu hỏi tự kiểm tra

1. main ném RuntimeException không bắt, nhưng một non-daemon worker còn sống: JVM phải exit tức khắc không? **Không.**
2. Tại sao worker mode có handler in lỗi nhưng exit code bình thường vẫn là 0? **Worker chết, main tiếp tục hoàn thành và không yêu cầu status lỗi của process.**
3. System.exit(0) có thực thi finally bao quanh chính lời gọi đó không? **Không; nó bắt đầu shutdown chứ không trả khỏi scope theo control flow bình thường.**
4. Runtime.halt(0) có gọi shutdown hook không? **Không.**
5. Daemon thread có giữ JVM sống sau khi mọi non-daemon thread kết thúc không? **Không.**
6. Nhìn thấy HOOK có chứng minh mọi dữ liệu đã được ghi bền vững không? **Không; cần xác nhận commit/ack/fsync tương ứng.**

**Kết luận kỹ thuật:** hoàn thành tự nhiên và System.exit() có thể tạo shutdown có trật tự; ngoại lệ không bắt làm chết **thread mang lỗi** trước; Runtime.halt(), SIGKILL và kill cưỡng bức có thể dừng **toàn bộ tiến trình** mà không có shutdown hook. Thiết kế cleanup theo phạm vi tài nguyên, quản lý non-daemon thread, quan sát lỗi tại ranh giới tác vụ và xây dựng cơ chế khôi phục cho mọi công việc quan trọng.


</details>

- [Quay lại đầu trang](#back-to-top)
