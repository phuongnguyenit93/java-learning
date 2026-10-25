<a id="back-to-top"></a>

# Đường dẫn tìm kiếm

## Menu
- [Classpath là gì và cách xác định thư mục gốc](#classpath-and-module-path-001)
- [Thiết lập -cp, --class-path và biến CLASSPATH](#classpath-and-module-path-002)
- [Classpath lúc biên dịch khác classpath lúc chạy](#classpath-and-module-path-003)
- [Thứ tự tìm kiếm và lớp trùng tên trong nhiều JAR](#classpath-and-module-path-004)
- [Wildcard trong classpath và giới hạn tìm kiếm](#classpath-and-module-path-005)
- [JAR, manifest Main-Class và Class-Path](#classpath-and-module-path-006)
- [Chế độ java -jar và các quy tắc classpath liên quan](#classpath-and-module-path-007)
- [Multi-release JAR và chọn bytecode theo phiên bản Java](#classpath-and-module-path-008)
- [Phân biệt classpath, module path và đường dẫn native library](#classpath-and-module-path-009)
- [Khác biệt giữa dependency của công cụ build và đường dẫn thực tế của JVM](#classpath-and-module-path-010)

## <a id="classpath-and-module-path-001">Classpath là gì và cách xác định thư mục gốc</a>

<details>
<summary>Xem chi tiết</summary>

# Classpath là gì và cách xác định thư mục gốc trong Java

Một chương trình Java muốn nạp `com.example.app.Main` cần biết **tìm bytecode ở đâu**. JVM không quét toàn bộ ổ đĩa để tìm file tên `Main.class`; trong mô hình classpath truyền thống, class loader dùng **tên nhị phân** của class và những **classpath entry** được cấu hình để xác định vị trí tương đối chính xác.

Điểm quan trọng nhất: **classpath root là thư mục đứng trước toàn bộ đường dẫn package**. Nếu file nằm tại `project/out/com/example/app/Main.class` và khai báo `package com.example.app;` thì root là `project/out`, không phải `project/out/com/example/app`.

Bài thuộc nhóm **Đường dẫn tìm kiếm** của Java core – classloader. Mục tiêu là hiểu định nghĩa classpath, xác định thư mục gốc cho package/default package, biên dịch và chạy Java 17+ với đúng root, dùng JAR/resource/classpath wildcard và chẩn đoán lỗi.

## 1. Classpath là gì?

Classpath là danh sách vị trí dùng để tìm class và resource trong mô hình class loading dựa trên class path. Một entry thông dụng gồm:

- **Thư mục** chứa cây package đã biên dịch, ví dụ `out` hoặc `build/classes/java/main`.
- **JAR** chứa các entry `.class` và resource, ví dụ `lib/helper.jar`.
- **Wildcard** `lib/*` được Java launcher mở rộng thành các JAR trực tiếp trong thư mục đó.

Classpath của lệnh `javac` giúp compiler tìm những class/type phụ thuộc khi biên dịch. Classpath của lệnh `java` giúp runtime tìm các class/resource lúc thực thi. Hai tập này có thể khác nhau; dependency cần dùng lúc chạy phải có trong đường tìm runtime thích hợp.

**Các loại đường dẫn khác nhau:**

| Khái niệm | Dùng cho việc gì? | Ví dụ |
| --- | --- | --- |
| Source root | Chứa file nguồn `.java` cho compiler/build tool | `src/main/java` |
| Classpath root | Chứa cây package của `.class` hoặc các resource | `out`, `target/classes` |
| Working directory | Thư mục hiện tại của tiến trình, làm mốc cho path tương đối | `project`, `user.dir` |
| `PATH` | Biến của hệ điều hành tìm lệnh `java`/`javac` | `JDK/bin` |
| `CLASSPATH` | Biến môi trường có thể cung cấp classpath khi không có `-cp` | `out;lib/x.jar` trên Windows |
| Module path | Nơi Java module finder tìm module artifacts của JPMS | `--module-path mods` |

Không nhầm `PATH` với `CLASSPATH`. Nếu máy nhận lệnh `java` nhưng ném `ClassNotFoundException`, việc chỉ sửa `PATH` thường không giải quyết được nguyên nhân.

`import com.example.Foo;` trong source cũng **không tự thêm** thư viện vào classpath. `import` chỉ là cú pháp Java cho phép tham chiếu tên ngắn; compiler và JVM vẫn cần nguồn class thích hợp.

## 2. Cách tính classpath root từ package

Ví dụ:

```java
package com.example.app;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

Binary name: `com.example.app.Main`.

Đường dẫn bytecode **tương đối với một directory classpath entry**: `com/example/app/Main.class`. Dấu chấm ngăn package trong tên nhị phân được ánh xạ thành ký tự phân tách thư mục tương ứng trong đường dẫn class file, rồi thêm hậu tố `.class`.

Nếu cây output là:

```text
project/
├── src/
│   └── com/example/app/Main.java
└── out/
    └── com/
        └── example/
            └── app/
                └── Main.class
```

thì root đúng là `out`:

```text
Classpath entry        Relative class path
      out         +    com/example/app/Main.class
                       |
                       v
             out/com/example/app/Main.class
```

Từ thư mục `project`, chạy:

```bash
java -cp out com.example.app.Main
```

Lệnh dưới đây **sai root**:

```bash
java -cp out/com/example/app com.example.app.Main
```

JVM sẽ thử tìm `out/com/example/app/com/example/app/Main.class`, nên có thể báo `Could not find or load main class com.example.app.Main`.

### Phương pháp xác định root khi biết file thực tế

1. Tìm **binary name** từ `package` và tên class, ví dụ `org.acme.Parser`.
2. Chuyển thành đường dẫn tương đối `org/acme/Parser.class`.
3. Bỏ đường dẫn tương đối đó khỏi cuối đường dẫn class file thật; phần còn lại là **directory root**.

| File thực tế | Binary name | Root |
| --- | --- | --- |
| `D:\work\build\classes\org\acme\Parser.class` | `org.acme.Parser` | `D:\work\build\classes` |
| `/srv/demo/out/a/b/Task.class` | `a.b.Task` | `/srv/demo/out` |
| `out/Hello.class`, không khai báo package | `Hello` | `out` |
| `target/classes/com/demo/Main.class` | `com.demo.Main` | `target/classes` |

**Default package:** Nếu class không có dòng `package`, binary name là `Hello`, đường tương đối chỉ là `Hello.class`, root chính là thư mục trực tiếp chứa file. Không khuyến khích default package cho ứng dụng nhiều module/package vì khó quản lý namespace.

**Nested class:** `com.example.Outer.Inner` trong cú pháp source thường có binary name `com.example.Outer$Inner`; file có thể là `Outer$Inner.class` dưới cây `com/example`. Classpath root vẫn là thư mục trước `com`.

**Source layout so với runtime:** Vị trí `src/com/example/Main.java` là source path. `javac -d out` có thể tự đặt output đúng cây package vào `out`. Runtime cần thư mục chứa **class output**, không tự nạp mã nguồn dưới `src` chỉ vì đã `import`.

## 3. Cấu hình với `-cp` và sự khác nhau của hệ điều hành

Java launcher có ba cách viết tương đương: `-cp`, `-classpath` và `--class-path`.

```bash
java --class-path out com.example.app.Main
```

Nhiều entry được nối bằng ký tự do OS quy định:

| Hệ điều hành | Separator | Ví dụ |
| --- | --- | --- |
| Windows | Dấu chấm phẩy `;` | `java -cp "out;resources;lib/helper.jar" com.example.app.Main` |
| Linux/macOS | Dấu hai chấm `:` | `java -cp "out:resources:lib/helper.jar" com.example.app.Main` |

Đặt giá trị classpath trong dấu nháy là thói quen tốt, đặc biệt nếu path có khoảng trắng hoặc ký tự shell đặc biệt. `:` trong `C:\work` của Windows **không phải** ký tự phân cách giữa các classpath entry.

Nếu `out` là đường tương đối, nó được diễn giải dựa trên **working directory** nơi tiến trình được khởi động:

```bash
cd out
java -cp . com.example.app.Main
```

Lúc này `.` chính là `out`. Nếu chạy từ `project`, `-cp .` chỉ trỏ tới `project` và **không tự tìm xuống `project/out`**. Classpath không phải cơ chế quét đệ quy mọi thư mục con.

### Giá trị mặc định và biến môi trường

Với chế độ chạy class trên classpath, khi **không cung cấp `-cp` và không đặt biến `CLASSPATH`**, classpath mặc định là working directory `.`. Nhưng **khi đã cung cấp `-cp`**, Java không tự thêm `.` vào cuối. Muốn dùng cả working directory lẫn JAR, ghi rõ:

```bash
java -cp ".;lib/helper.jar" com.example.app.Main
```

Dòng trên dành cho Windows; trên Unix đổi `;` thành `:`. Nếu class output nằm trong `out`, phải ghi `out` thay cho `.`.

Nên cung cấp classpath tường minh trong script/IDE/build tool thay vì dựa vào `CLASSPATH` toàn máy, vì biến môi trường rất dễ gây khác biệt giữa các máy. `-cp` trên command line ghi đè `CLASSPATH` cho tiến trình launcher đó.

## 4. Ví dụ Java 17+ độc lập: hai class, hai root

Tạo cấu trúc:

```text
classpath-demo/
├── src/
│   └── com/
│       └── example/
│           ├── app/Main.java
│           └── util/Greeter.java
├── resources/
│   └── com/example/app/config.properties
└── out/   (được tạo trước khi biên dịch)
```

File `src/com/example/util/Greeter.java`:

```java
package com.example.util;

public final class Greeter {
    private Greeter() {}

    public static String message() {
        return "Hello from Greeter";
    }
}
```

File `src/com/example/app/Main.java`:

```java
package com.example.app;

import com.example.util.Greeter;
import java.io.InputStream;
import java.util.Properties;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println(Greeter.message());

        Properties properties = new Properties();
        try (InputStream stream =
                Main.class.getResourceAsStream("config.properties")) {
            if (stream == null) {
                throw new IllegalStateException("Resource not found");
            }
            properties.load(stream);
        }

        System.out.println("mode=" + properties.getProperty("mode"));
        System.out.println("main=" + Main.class.getName());
    }
}
```

File `resources/com/example/app/config.properties`:

```properties
mode=demo
```

Từ thư mục `classpath-demo`, nếu chưa có `out` thì tạo:

```bash
mkdir out
```

Biên dịch bằng JDK 17 trở lên:

```bash
javac --release 17 -d out src/com/example/util/Greeter.java src/com/example/app/Main.java
```

Với `-d out`, compiler tạo:

```text
out/
└── com/example/
    ├── app/Main.class
    └── util/Greeter.class
```

Đường dẫn thực tế của resource vẫn là `resources/com/example/app/config.properties`; `javac` không tự copy file resource vào `out`.

Chạy trên **Windows**:

```bash
java -cp "out;resources" com.example.app.Main
```

Chạy trên **Linux/macOS**:

```bash
java -cp "out:resources" com.example.app.Main
```

Stdout chính xác của cả hai:

```text
Hello from Greeter
mode=demo
main=com.example.app.Main
```

Giải thích:

- Root `out` cho phép tìm `com/example/app/Main.class` và `com/example/util/Greeter.class`.
- Root `resources` cho phép tìm `com/example/app/config.properties`.
- `Main.class.getResourceAsStream("config.properties")` tìm tương đối theo package của `Main`, tức `com/example/app`.
- `Main.class.getName()` trả binary name `com.example.app.Main`.

Thử **bỏ `resources`**:

```bash
java -cp out com.example.app.Main
```

Chương trình vẫn tìm được `Main` và `Greeter`, in dòng đầu rồi ném `IllegalStateException: Resource not found`. Điều này cho thấy **root của class** và **root của resource** có thể là hai entry riêng. Nếu resource được copy vào `out/com/example/app` khi build, chỉ `out` cũng có thể đủ.

Thử **chạy nhầm tên `Main`**:

```bash
java -cp out Main
```

Lệnh không tìm được `Main` trong default package; binary name thực sự là `com.example.app.Main`. Đây là vấn đề tên class, không chỉ đường dẫn.

## 5. `javac -cp` khác `javac -d`

Hai tùy chọn thường xuất hiện cùng nhau nhưng có chức năng khác:

- `-d out`: **đưa output `.class` vào root `out`**, kèm cây package.
- `-cp something`: **chỉ nơi compiler tìm class và thư viện phụ thuộc**.

Giả sử `Greeter.class` đã được biên dịch vào `out`, và chỉ muốn compile lại `Main.java`:

```bash
javac --release 17 -cp out -d out src/com/example/app/Main.java
```

Compiler dùng `out` để resolve `com.example.util.Greeter`, còn output mới của `Main` cũng được ghi vào `out`.

Classpath lúc chạy là lựa chọn khác:

```bash
java -cp "out;resources" com.example.app.Main
```

(Windows; trên Linux/macOS thay `;` bằng `:`.)

Một dependency có trong compile classpath **không tự động xuất hiện** trên runtime classpath. Điều này giải thích lỗi “`javac` chạy tốt nhưng `java` thiếu class” khi khởi chạy thủ công mà quên JAR cần thiết.

Trong dự án dùng Gradle/Maven, build tool tổng hợp compile và runtime classpath từ các dependency scopes/configurations. Khi chạy qua IDE hoặc build tool, kiểm tra **run configuration thực tế** thay vì suy rằng compiler tự ghi lại mọi thư viện phụ thuộc vào `Main.class`.

## 6. Classpath root bên trong JAR

JAR là archive ZIP chứa các entry tương đối tính từ **root của archive**. Nếu muốn JAR cung cấp `com.example.app.Main`, entry phải là:

```text
com/example/app/Main.class
```

Không được vô tình lưu thành `out/com/example/app/Main.class` hoặc `src/com/example/app/Main.java` rồi mong loader hiểu đó là class runtime tương ứng.

Sau khi biên dịch ví dụ ở mục 4, đóng gói bằng JDK `jar` trên **một dòng tương thích đa nền tảng**:

```bash
jar --create --file app.jar --main-class com.example.app.Main -C out . -C resources .
```

`-C out .` yêu cầu `jar` lấy nội dung dưới `out` và lưu **tương đối từ root `out`**. `-C resources .` làm tương tự với resource. `--main-class` ghi `Main-Class` trong manifest.

Kiểm tra:

```bash
jar --list --file app.jar
java -jar app.jar
```

Trong danh sách của `jar --list`, ngoài manifest và các directory entries, các tệp quan trọng sẽ là:

```text
com/example/app/Main.class
com/example/util/Greeter.class
com/example/app/config.properties
```

Stdout của `java -jar app.jar` vẫn là:

```text
Hello from Greeter
mode=demo
main=com.example.app.Main
```

Có thể chạy JAR như một classpath entry:

```bash
java -cp app.jar com.example.app.Main
```

### `-jar` không vận hành giống `-cp`

`java -cp app.jar com.example.app.Main` nạp main class theo tên bạn truyền từ classpath entry. `java -jar app.jar` lấy main class từ manifest và **không dùng classpath được chỉ định bởi `-cp` hoặc biến `CLASSPATH` như trong chế độ chạy class thông thường**. Để tham chiếu dependency nằm ngoài executable JAR, có thể dùng manifest `Class-Path` hoặc launcher/packaging được thiết kế phù hợp.

Manifest `Class-Path` tham chiếu các URL/JAR theo quy tắc liên quan **vị trí của JAR chứa manifest**, không đơn giản là mọi path tương đối lấy mốc từ `user.dir`.

Đặc biệt, **Spring Boot executable JAR** thường có cấu trúc `BOOT-INF/classes` và `BOOT-INF/lib` với launcher riêng. Không coi cấu trúc đó là flat JAR mà `java -cp boot.jar com.example.Main` luôn hoạt động; dùng launcher hoặc `java -jar` được hỗ trợ.

## 7. `lib/*` có thêm tất cả mọi thứ không?

Không. Ký hiệu wildcard của Java launcher dạng `lib/*` mở rộng đến các JAR trực tiếp trong thư mục `lib`. Ví dụ:

```bash
java -cp "out;lib/*" com.example.app.Main
```

trên Windows; Linux/macOS:

```bash
java -cp "out:lib/*" com.example.app.Main
```

Giới hạn:

- `lib/*` **không quét đệ quy** `lib/plugins/**` hay các thư mục con.
- `lib/*` **không tự thêm** directory root `lib/classes` hoặc những file `.class` nằm trực tiếp dưới `lib`.
- Nếu cần cả class directory `lib/classes`, thêm entry `lib/classes` tường minh.
- Thứ tự mở rộng các JAR bởi wildcard không nhất thiết ổn định; nếu nhiều JAR chứa cùng tên class, đừng dựa vào “JAR nào thắng” như hợp đồng lâu dài.
- Đặt dấu nháy quanh classpath thường giúp tránh shell tự mở rộng wildcard trước khi Java launcher xử lý.

Classpath entry được tìm theo cơ chế của loader tương ứng. Trong đường dẫn classpath thông thường, nhiều bản của một class trong các entry khác nhau có thể dẫn tới nạp nhầm phiên bản dependency và lỗi `NoSuchMethodError` hoặc `NoSuchFieldError` khi thực thi.

## 8. Resource path: khác class binary name

Classpath có thể chứa file `config.properties`, JSON, XML, template, ảnh hoặc tài nguyên khác. Với `Main` thuộc package `com.example.app`:

```java
java.net.URL relative =
        Main.class.getResource("config.properties");

java.net.URL absolute =
        Main.class.getResource(
                "/com/example/app/config.properties");

java.net.URL loaderResource =
        Main.class.getClassLoader()
                .getResource("com/example/app/config.properties");
```

Đây là **đoạn mã minh họa chèn vào class `Main`**. Cả ba đường dẫn trên cùng trỏ tới tài nguyên phù hợp trong ví dụ mục 4 nếu đang dùng classloader ứng dụng thông thường.

| API | Quy tắc tìm |
| --- | --- |
| `Class.getResource("x")` | Tương đối theo package của class đó |
| `Class.getResource("/a/b/x")` | Tuyệt đối từ root resource (dấu `/` đầu được API này xử lý) |
| `ClassLoader.getResource("a/b/x")` | Tên resource tương đối từ root loader; thường **không** có dấu `/` đầu |

Với class bootstrap, `SomeCoreClass.class.getClassLoader()` có thể là `null`, nên không gọi `getResource` lên giá trị `null`.

Khi resource nằm trong JAR, URL có thể giống `jar:file:/.../app.jar!/com/example/app/config.properties`. Không phải mọi resource là một `java.io.File` vật lý trên filesystem. Đọc bằng `getResourceAsStream` thường an toàn hơn trong cả directory và JAR.

Named modules còn áp dụng module encapsulation, đặc biệt cho resource trong package không mở. Vì vậy cách resource được nhìn thấy qua API có thể khác khi chuyển một ứng dụng từ classpath sang module path.

## 9. Classpath và module path khác nhau

Từ Java 9, JPMS có **module path**. Ví dụ dạng chạy named module:

```bash
java --module-path mods --module m.example/com.example.Main
```

Đây là **cú pháp minh họa**, chỉ hoạt động khi `mods` có named module `m.example` phù hợp. Không áp dụng trực tiếp cho chương trình không có `module-info.java` ở mục 4.

| Classpath | Module path |
| --- | --- |
| Thường chứa class của unnamed module theo loader | Module finder xác định named modules |
| Cấu hình bằng `-cp`/`--class-path` | Cấu hình bằng `-p`/`--module-path` |
| Không cần `module-info.java` | Explicit module dùng `module-info.class` |
| Mapping directory/JAR entry theo package | Thêm module graph, readability, exports, opens |
| Phù hợp ứng dụng Java truyền thống | Phù hợp code được tổ chức theo JPMS |

Dù module cũng có class file theo cấu trúc package, việc đặt artifact trên module path **không hoàn toàn đồng nghĩa** đặt chính artifact đó trên classpath. Module boundaries và ánh xạ loader của `ModuleLayer` có thể thay đổi khả năng truy cập và hành vi resolution.

## 10. Làm sao xác định classpath thực tế của ứng dụng đang chạy?

Một số API quan sát:

```java
System.out.println("classpath="
        + System.getProperty("java.class.path"));

System.out.println("working directory="
        + System.getProperty("user.dir"));

System.out.println("defining loader="
        + Main.class.getClassLoader());

System.out.println("code source="
        + Main.class.getProtectionDomain().getCodeSource());
```

Đây là **đoạn minh họa thêm vào `Main.main`**, không có stdout cố định vì còn phụ thuộc OS, launcher và build tool.

- `java.class.path` hữu ích với app chạy theo classpath; nó **không liệt kê toàn bộ mọi nguồn** mà custom loader có thể đọc.
- `user.dir` xác định working directory của tiến trình tại startup; không nhất thiết bằng thư mục chứa tệp `Main.java` hoặc JAR.
- `Main.class.getClassLoader()` cho biết **defining loader** của `Main`.
- `getProtectionDomain().getCodeSource()` có thể gợi ý artifact/thư mục gốc, nhưng **có thể `null`** hoặc không mang filesystem location hữu ích với bootstrap/custom loader.

Công cụ kiểm tra:

```bash
javap -classpath out com.example.app.Main
jar --list --file app.jar
```

Lệnh `javap` kiểm tra class file có tìm thấy từ root `out` và in thông tin class/bytecode; `jar --list` cho biết đường dẫn các entry trong JAR. Nếu cần điều tra HotSpot class loading, có thể bật:

```bash
java -Xlog:class+load=info -cp out com.example.app.Main
```

Log dạng này khá dài và định dạng phụ thuộc JDK. Nó giúp tìm nguồn class, nhưng không chứng minh rằng tất cả class loader của ứng dụng đều dùng cùng classpath.

### Quy ước output trong build tools

| Build tool | Source root mặc định | Classes runtime root thường gặp | Resource output thường gặp |
| --- | --- | --- | --- |
| Gradle Java plugin | `src/main/java` | `build/classes/java/main` | `build/resources/main` |
| Maven | `src/main/java` | `target/classes` | `target/classes` |

Các đường dẫn là **quy ước có thể tùy biến**. IDE hoặc build tool có thể tổng hợp nhiều root và dependency JAR cho run configuration. Không chỉ nhìn `src/main/java` rồi giả định thư mục đó là runtime classpath.

## 11. Classpath có phải cách tìm của mọi ClassLoader không?

Không. Java có các loader tích hợp và custom loaders:

- **Bootstrap loader** định nghĩa những lớp cốt lõi của JVM/Java SE; `String.class.getClassLoader()` thường là `null`.
- **Platform loader** phụ trách các platform classes phù hợp trong runtime image/module system.
- **Application loader** thường định nghĩa lớp chạy từ application classpath và những module phù hợp.
- **Custom loader** có thể tự tìm class từ URL, memory, database, plugin archive hoặc nguồn khác; nó có thể dùng classpath hoặc không.

Ví dụ:

```java
ClassLoader system = ClassLoader.getSystemClassLoader();
Class<?> type = system.loadClass("com.example.app.Main");
System.out.println(type.getClassLoader());
```

Đây là **đoạn minh họa**, cần đặt trong method xử lý `ClassNotFoundException`, và class phải thấy được từ system loader. `loadClass` có thể delegate tới parent; loader nhận yêu cầu không nhất thiết là loader **định nghĩa** class.

Một runtime class được xác định bằng **binary name + defining loader**. Hai loader khác nhau có thể định nghĩa hai class cùng tên nhưng là hai kiểu runtime khác nhau, dễ dẫn tới `ClassCastException`. Khi nhiều plugin/container tham gia, cần kiểm tra loader và module layer, không chỉ chuỗi `-cp`.

## 12. Bảng lỗi phổ biến và cách xử lý

| Hiện tượng | Nguyên nhân cần kiểm tra | Hành động |
| --- | --- | --- |
| `Could not find or load main class` | Binary name sai hoặc root sai | Ghép root với đường tương đối `a/b/C.class` và so file |
| `NoClassDefFoundError: ... (wrong name: ...)` | Class file được tìm nhưng tên bên trong không khớp | Kiểm tra `package`, vị trí output, tên chạy |
| `ClassNotFoundException` ở `Class.forName` | Loader được chọn không thấy class | Kiểm tra caller loader/explicit loader, dependency và binary name |
| `NoClassDefFoundError` trong runtime | Dependency mất, lỗi linking hoặc initialization thất bại từ trước | Đọc nguyên nhân ban đầu và runtime artifacts |
| `NoSuchMethodError` / `NoSuchFieldError` | Version JAR runtime không tương thích | Kiểm tra dependency duplicate/version, method/field descriptor |
| Resource luôn `null` | Root tài nguyên thiếu hoặc đường lookup khác package | Kiểm tra file bên trong root/JAR và API `getResource` |
| Chạy trong IDE được, terminal không | IDE có runtime classpath khác | So lệnh Java và output roots thật |
| Chạy `-jar` thì thiếu thư viện | Đặt nhầm niềm tin vào `-cp` của chế độ `-jar` | Kiểm tra manifest/launcher/đóng gói dependency |
| `lib/*` bỏ sót `.class` trong thư mục con | Wildcard chỉ xử lý các JAR phù hợp | Bổ sung directory entry thực tế |
| Spring Boot JAR không chạy bằng `java -cp` thường | JAR có layout đặc thù `BOOT-INF` | Chạy bằng launcher hỗ trợ, thường `java -jar` |

### Quy trình debug có thể áp dụng ngay

1. **Xác định binary name.** Xem `package`, tên class và dấu `$` của nested class.
2. **Tìm `.class` thực tế.** Trong output folder hoặc entry trong JAR, có phải `a/b/C.class` không?
3. **Tìm root.** Bỏ đúng cây `a/b/C.class` khỏi đường dẫn thực tế; phần trước nó mới là root.
4. **Xác nhận command và OS.** `-cp` đang là gì, separator đúng chưa, đang chạy từ working directory nào?
5. **So compile/runtime.** `javac -cp` có dependency nhưng `java -cp` thiếu không?
6. **Kiểm tra resource riêng.** `Class.getResource` và `ClassLoader.getResource` xử lý tên path khác nhau.
7. **Nếu vẫn lỗi, kiểm tra loader/module.** Ai định nghĩa class, có custom policy hoặc named module không?
8. **Kiểm tra nguyên nhân gốc.** Lỗi còn có thể do binary incompatibility và initialization, không đơn giản là đường dẫn thiếu.

## 13. Tự kiểm tra

**Câu 1.** `Main.class` ở `C:\work\out\com\acme\Main.class` và package là `com.acme`. Dùng `-cp` nào?

**Đáp án:** `C:\work\out` vì JVM sẽ tìm `com/acme/Main.class` bên dưới root.

**Câu 2.** Từ thư mục dự án ví dụ, tại sao `java -cp . com.example.app.Main` không chạy nếu chỉ compile bằng `-d out`?

**Đáp án:** `.` chỉ vào thư mục dự án, còn file thực tế nằm dưới `out/com/example/app`. Cần dùng `-cp out` hoặc chuyển working directory sang `out` và dùng `-cp .`.

**Câu 3.** Trên Windows, muốn class ở `out` và resource ở `resources` thì lệnh nào đúng?

**Đáp án:** `java -cp "out;resources" com.example.app.Main`; trên Linux/macOS dùng `"out:resources"`.

**Câu 4.** `-cp lib/*` có tự tìm `lib/classes/com/acme/Thing.class` không?

**Đáp án:** Không. Wildcard launcher xử lý JAR trực tiếp trong `lib`, không đệ quy tới directory class root `lib/classes`.

**Câu 5.** Khi `com.example.app.Main` được đóng gói trong `app.jar`, entry trong JAR tên gì?

**Đáp án:** `com/example/app/Main.class`, không mang tiền tố `out/` hoặc `src/`.

**Câu 6.** Vì sao `Main.class.getResource("config.properties")` tìm file dưới `resources/com/example/app`?

**Đáp án:** API này dùng tên resource tương đối theo package `com.example.app`. Runtime còn phải có entry `resources` trong classpath.

**Câu 7.** Có thể dùng `java -cp "extra.jar" -jar app.jar` để tùy ý thêm dependency vào classpath của chế độ `-jar` không?

**Đáp án:** Không theo cơ chế lựa chọn classpath thông thường của launcher `-jar`. Cần dùng manifest `Class-Path` hoặc launcher/packaging hỗ trợ.

**Câu 8.** Biên dịch thành công bằng `javac -cp lib/helper.jar` nhưng lúc chạy gặp thiếu `helper.SomeType`. Điều gì cần xem?

**Đáp án:** Runtime classpath có `helper.jar` hoặc cách cung cấp dependency tương đương chưa. `javac` không ghi nhớ và tự bổ sung `-cp` cho lệnh `java`.

**Câu 9.** Vì sao class có trong classpath vẫn có thể không dùng được trong một plugin?

**Đáp án:** Loader plugin có thể dùng namespace/source/policy khác, class thực tế được định nghĩa ở loader khác, hoặc có giới hạn module access. Cần kiểm tra defining loader và module layer.

**Câu 10.** Default package có class `Hello.class` ở `out`; chạy thế nào?

**Đáp án:** `java -cp out Hello` vì binary name là `Hello`, và relative class path là `Hello.class`.

## Ghi nhớ

**Root không phải folder chứa trực tiếp class có package: root đứng ở phía trên toàn bộ cây thư mục package.** Hãy luôn tính:

```text
Classpath root + binaryName.replace('.', '/') + ".class"
        = đường dẫn class trong một directory entry
```

Với JAR, phần tương đối phải là entry từ **root bên trong archive**. Sau đó kiểm tra riêng resource root, runtime dependencies, launcher mode (`-cp` hay `-jar`) và class loader thực tế khi dự án phức tạp hơn.

Nguồn chuẩn:

- [Java 17 `java` launcher](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html) – classpath, wildcard, `-jar` và module path.
- [Java 17 `javac`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html) – `-d`, classpath biên dịch và tìm type.
- [Java 17 `jar`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jar.html) – archive entries và manifest.
- [Java SE 17 `ClassLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html) – tìm class/resource và parent delegation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-and-module-path-002">Thiết lập -cp, --class-path và biến CLASSPATH</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết lập `-cp`, `--class-path` và biến `CLASSPATH` trong Java

## 1. Classpath là gì, dùng để làm gì?

**Classpath** (*đường dẫn tìm kiếm lớp*) là danh sách các vị trí mà công cụ Java sử dụng để tìm **class của ứng dụng và thư viện trên classpath**, thông thường gồm thư mục chứa các tệp `.class`, tệp `.jar` hoặc `.zip`. Đây không phải đường dẫn tới Java source tùy ý, cũng không phải toàn bộ nơi JVM có thể tìm mọi lớp: JVM còn có các loader nền tảng, module path và các custom class loader khác.

Trong cách khởi chạy một chương trình Java cổ điển:

~~~text
                 java launcher
                       |
            Chọn effective classpath
                       |
           +-----------+------------+
           |                        |
   Thư mục class gốc          JAR/ZIP chứa class
   out/classes/               lib/feature.jar
           |                        |
           +-----------+------------+
                       |
          Application/System ClassLoader
                       |
              tìm class theo tên
              demo.Main -> demo/Main.class
~~~

Ví dụ chương trình cần lớp `demo.Main` và `util.Helper`. Nếu class files nằm ở:

~~~text
project/
├── out/
│   ├── app/demo/Main.class
│   └── lib/util/Helper.class
└── lib/helper.jar
~~~

thì đường dẫn classpath có thể chứa **`out/app` và `out/lib`**. Tên lớp `demo.Main` được ánh xạ tương đối thành `demo/Main.class`. Nếu chỉ thêm đường dẫn `out/app/demo`, JVM sẽ tìm sai vị trí cho tên `demo.Main`; **classpath entry phải là gốc của cây package**.

Đối với JAR, entry ngay trong kho lưu trữ cũng phải có đường dẫn package tương ứng, ví dụ `util/Helper.class`. Truyền trực tiếp đường dẫn tới một tệp `.class` cụ thể không phải cách thiết lập classpath chuẩn cho class có package.

## 2. Ba tùy chọn `-cp`, `-classpath`, `--class-path`

Với **`java` launcher** và **`javac` compiler**, ba cách viết sau là các tên tùy chọn tương đương để chỉ định classpath của user:

~~~bash
java -cp out/classes demo.Main
java -classpath out/classes demo.Main
java --class-path out/classes demo.Main
~~~

Dùng một trong ba cách là đủ. Cú pháp tổng quát:

~~~text
java [JVM options] -cp <classpath> <fully.qualified.MainClass> [app arguments]
javac -cp <classpath> -d <output-directory> <source-files>
~~~

Các dấu ngoặc nhọn/ngoặc nhọn góc trong sơ đồ trên là **ký hiệu mô tả**, không nhập nguyên văn. `-cp` là tùy chọn của trình chạy hoặc trình biên dịch, cần đặt trước tên lớp chính khi chạy class mode. Nếu đặt ở **sau** `demo.Main`, nó sẽ thành đối số `String[] args` của ứng dụng thay vì cấu hình cho launcher.

| Tùy chọn | Công cụ | Ý nghĩa |
| --- | --- | --- |
| `-cp path` | `java`, `javac` | Viết ngắn; tiện dùng trong terminal/script. |
| `-classpath path` | `java`, `javac` | Tên dài tương đương với `-cp`. |
| `--class-path path` | `java`, `javac` | Dạng tùy chọn dài; dễ đọc trong script. |
| `-d output` | `javac` | Nơi **ghi file `.class` đầu ra**; **không tự** thêm vị trí này vào runtime classpath. |

Hãy phân biệt **classpath để biên dịch** với **classpath lúc chạy**:

- `javac -cp ...`: compiler dùng các thư mục/JAR để kiểm tra và resolve kiểu khi biên dịch.
- `java -cp ...`: JVM dùng các nguồn tại runtime để tìm class chính và các dependency do system/application loader cần.
- Hai danh sách này thường liên quan nhưng **không tự đồng bộ**. Biên dịch thành công không có nghĩa runtime tìm đủ JAR.
- Khi đã tự chỉ định classpath, phải đưa thư mục chứa class đầu ra và dependency cần thiết vào danh sách một cách rõ ràng.

## 3. Ghép nhiều đường dẫn: dấu phân cách theo hệ điều hành

Một classpath là **một giá trị** có thể chứa nhiều phần tử. Ký tự ngăn cách do **hệ điều hành** quy định:

| Hệ điều hành | Path separator | Ví dụ |
| --- | --- | --- |
| Windows (PowerShell hoặc cmd) | `;` | `out\app;out\lib;lib\helper.jar` |
| Linux/macOS | `:` | `out/app:out/lib:lib/helper.jar` |

Ví dụ chạy trên Windows PowerShell:

~~~powershell
java -cp "out\app;out\lib" demo.Main
java --class-path "out\app;lib\helper.jar" demo.Main
~~~

Ví dụ tương ứng trên Linux/macOS:

~~~bash
java -cp "out/app:out/lib" demo.Main
java --class-path "out/app:lib/helper.jar" demo.Main
~~~

**Nên đặt toàn bộ classpath trong dấu nháy** khi có khoảng trắng hoặc ký tự mà shell có thể xử lý riêng. Đặc biệt trên PowerShell, dấu `;` ngoài chuỗi được dùng để ngăn các lệnh, nên chuỗi classpath có nhiều phần tử phải được trích dẫn.

Đường dẫn tương đối được giải quyết từ **thư mục làm việc hiện tại** của tiến trình, không tự động từ thư mục chứa `Main.class` hay một source `.java`. Đổi working directory có thể làm cùng một lệnh đột nhiên không tìm thấy lớp.

**Không sử dụng dấu `:` để nối path trên Windows** (vì còn xuất hiện ở ký tự ổ đĩa như `C:`), và cũng đừng bê nguyên dấu `;` từ Windows sang shell Unix.

## 4. Biến môi trường `CLASSPATH` được áp dụng khi nào?

`CLASSPATH` là **biến môi trường** mà Java launcher và compiler có thể đọc để lấy user classpath **khi không có tùy chọn classpath tường minh**.

Quy tắc ưu tiên thực tế cho class mode cổ điển:

~~~text
Có -cp / -classpath / --class-path tường minh?
             |
       +-----+------+
       |            |
      Có           Không
       |            |
       v            v
Dùng giá trị    CLASSPATH có được đặt?
ở command           |
line             +---+---+
                 |       |
                Có      Không
                 |       |
                 v       v
           Dùng CLASSPATH  Dùng "."
~~~

Nếu `-cp` hiện diện, nó **ghi đè giá trị effective user classpath từ biến `CLASSPATH`** trong lệnh đó; hai giá trị không được tự nối với nhau. Nếu cả tùy chọn và biến không được đặt, classpath người dùng mặc định là **thư mục hiện tại `.`**.

Ví dụ:

~~~text
CLASSPATH=out/app:out/lib
java -cp out/other demo.Main
~~~

Lời gọi cuối sẽ **không** tự thêm `out/app:out/lib` vào `out/other`. Nếu cần chúng, hãy liệt kê đủ trong giá trị `-cp`. Cũng lưu ý classpath tường minh **không tự thêm `.`**, nên nếu cần current directory thì viết nó ra:

~~~bash
java -cp ".:lib/helper.jar" demo.Main       # Linux/macOS
~~~

Trên Windows là `java -cp ".;lib\helper.jar" demo.Main`.

### Thiết lập trong Windows PowerShell

~~~powershell
$env:CLASSPATH = "out\app;out\lib"
java demo.Main
~~~

Thiết lập này áp dụng cho **phiên PowerShell hiện tại** và các process được nó khởi tạo. Muốn bỏ biến trong phiên hiện tại:

~~~powershell
Remove-Item Env:CLASSPATH
~~~

Nếu chỉ muốn xem giá trị đang áp dụng:

~~~powershell
$env:CLASSPATH
~~~

Trong Command Prompt (`cmd.exe`) tương ứng:

~~~bat
set CLASSPATH=out\app;out\lib
java demo.Main
set CLASSPATH=
~~~

### Thiết lập trong Bash/zsh trên Linux hoặc macOS

~~~bash
export CLASSPATH="out/app:out/lib"
java demo.Main
unset CLASSPATH
~~~

Biến chỉ ảnh hưởng các process Java được khởi chạy từ môi trường đó. Thay đổi `CLASSPATH` sau khi JVM đã bắt đầu **không tự cập nhật** classpath của JVM đang chạy.

**Khuyến nghị:** ưu tiên `-cp` hoặc `--class-path` tường minh trong command, task build, hoặc script của dự án. Một biến `CLASSPATH` toàn cục thường gây lỗi khó tái hiện khi nhiều dự án cần thư viện khác nhau. Tài liệu `javac` của Oracle cũng khuyến nghị không cần đặt biến này khi có thể truyền tùy chọn rõ ràng.

## 5. Ví dụ Java 17+ hoàn chỉnh: compile classpath và runtime classpath

Bài thực hành gồm **hai class ở hai package khác nhau**. Không cần framework hay dependency ngoài JDK.

Tạo cấu trúc nguồn:

~~~text
example/
└── src/
    ├── util/
    │   └── Helper.java
    └── demo/
        └── Main.java
~~~

### `src/util/Helper.java`

~~~java
package util;

public class Helper {
    public static String message() {
        return "Hello from helper";
    }
}
~~~

### `src/demo/Main.java`

~~~java
package demo;

import util.Helper;

public class Main {
    public static void main(String[] args) {
        System.out.println(Helper.message());
    }
}
~~~

**Lưu ý:** hai đoạn trên là **hai tệp source độc lập**, phải tạo ở đúng đường dẫn và package tương ứng.

### Chạy trên Windows PowerShell

Đứng trong thư mục `example`:

~~~powershell
New-Item -ItemType Directory -Force out\lib, out\app | Out-Null

javac -d out\lib src\util\Helper.java
javac -cp out\lib -d out\app src\demo\Main.java

java -cp "out\app;out\lib" demo.Main
java --class-path "out\app;out\lib" demo.Main
~~~

Cả hai lệnh `java` đều in:

~~~text
Hello from helper
~~~

Giải thích:

1. Lệnh `javac -d out\lib src\util\Helper.java` tạo `out/lib/util/Helper.class`.
2. Lệnh biên dịch `Main` phải dùng `-cp out\lib` để compiler tìm được kiểu `util.Helper`. Đồng thời `-d out\app` đưa class chính vào `out/app/demo/Main.class`.
3. Khi chạy, `java` phải thấy **cả** `out\app` (nơi có `demo.Main`) và `out\lib` (nơi có `util.Helper`).
4. Tên lớp chính là **`demo.Main`**, không phải `Main` và không phải `out\app\demo\Main.class`.

Thử cách dùng biến môi trường trong PowerShell:

~~~powershell
$env:CLASSPATH = "out\app;out\lib"
java demo.Main
Remove-Item Env:CLASSPATH
~~~

Kết quả vẫn là:

~~~text
Hello from helper
~~~

Nếu muốn dùng `CLASSPATH` để biên dịch, biến cũng có thể làm classpath mặc định của `javac`; nhưng ví dụ cố ý dùng `-cp` lúc compile để tránh phụ thuộc trạng thái máy.

### Chạy trên Linux/macOS

Đứng trong thư mục `example` có hai file source đã tạo:

~~~bash
mkdir -p out/lib out/app

javac -d out/lib src/util/Helper.java
javac -cp out/lib -d out/app src/demo/Main.java

java -cp "out/app:out/lib" demo.Main
java --class-path "out/app:out/lib" demo.Main
~~~

Kết quả của mỗi lệnh chạy là:

~~~text
Hello from helper
~~~

Phiên shell dùng biến môi trường:

~~~bash
export CLASSPATH="out/app:out/lib"
java demo.Main
unset CLASSPATH
~~~

Kết quả:

~~~text
Hello from helper
~~~

**Đã kiểm chứng:** mã Java trên biên dịch và chạy được bằng JDK hiện đại trên Windows với cả `-cp`, `--class-path` và `CLASSPATH`. Dạng Linux/macOS chỉ thay dấu phân cách và dấu gạch đường dẫn theo cú pháp hệ điều hành.

## 6. Dùng file JAR và wildcard `lib/*`

Một classpath entry có thể là một **JAR** cụ thể:

~~~powershell
java -cp "out\app;lib\helper.jar" demo.Main  # Windows
~~~

~~~bash
java -cp "out/app:lib/helper.jar" demo.Main   # Linux/macOS
~~~

Để thử chính ví dụ phần 5, trước hết đóng gói class `util.Helper` đã biên dịch:

~~~powershell
New-Item -ItemType Directory -Force lib | Out-Null
jar --create --file lib\helper.jar -C out\lib .
java -cp "out\app;lib\helper.jar" demo.Main
~~~

Lệnh trên chạy trên Windows PowerShell và in `Hello from helper`. Tương ứng Linux/macOS:

~~~bash
mkdir -p lib
jar --create --file lib/helper.jar -C out/lib .
java -cp "out/app:lib/helper.jar" demo.Main
~~~

**Nếu có nhiều JAR trong cùng thư mục**, Java launcher hỗ trợ phần tử wildcard có **basename đúng bằng dấu `*`**:

~~~powershell
java -cp "out\app;lib\*" demo.Main   # Windows
~~~

~~~bash
java -cp "out/app:lib/*" demo.Main    # Linux/macOS
~~~

Các điểm cần hiểu chính xác về wildcard:

- `lib/*` mở rộng thành **các JAR có đuôi `.jar` hoặc `.JAR` nằm trực tiếp trong `lib`**, không tìm JAR ở thư mục con một cách đệ quy.
- `lib/*` **không tự thêm** thư mục `lib` làm classpath entry cho các tệp `.class` rời. Nếu có `lib/com/example/X.class`, phải thêm chính thư mục `lib`.
- Thứ tự các JAR khi mở rộng wildcard **không được bảo đảm**. Nếu nhiều JAR chứa lớp cùng binary name, hành vi có thể phụ thuộc thứ tự dò thực tế và dẫn đến lỗi phiên bản.
- Không dùng wildcard như một phép tìm file bất kỳ `lib/special-*.jar`; hãy chỉ định tên JAR cụ thể hoặc dùng `lib/*` theo quy tắc của Java.
- Đặt `lib/*` trong dấu nháy để **shell không tự mở rộng** wildcard theo quy tắc của nó trước khi Java launcher xử lý.

Tương tự, có thể sử dụng wildcard trong giá trị `CLASSPATH`, nhưng vẫn cần chú ý dấu phân cách và không nên để một thư viện phụ thuộc vào thứ tự không xác định.

## 7. Thứ tự phần tử trong classpath có ý nghĩa gì?

Thông thường application loader tìm các nguồn theo một thứ tự xác định bởi classpath hiệu lực (đồng thời tuân theo chính sách ủy quyền tới loader cha). Nếu hai JAR cùng chứa `com/example/Util.class`, class được định nghĩa có thể đến từ JAR được xét trước; các phiên bản cùng tên nhưng bytecode khác nhau là nguyên nhân phổ biến của lỗi linkage như `NoSuchMethodError`.

Ví dụ:

~~~bash
java -cp "app:lib/api-v1.jar:lib/api-v2.jar" demo.Main
~~~

Không nên dùng việc đặt nhiều bản một thư viện trên classpath làm cơ chế chọn phiên bản. Hãy bảo đảm danh sách dependency và phiên bản nhất quán, đặc biệt khi dùng `lib/*` vì thứ tự mở rộng JAR không được bảo đảm.

Tuy nhiên, **thứ tự classpath không vượt qua mọi chính sách class loader**: parent-first có thể ưu tiên một lớp đã được định nghĩa ở loader cha; custom loader có thể dùng chiến lược khác. Classpath chủ yếu mô tả **nguồn tìm kiếm** cho loader/công cụ liên quan, không thay đổi danh tính kiểu đã được định nghĩa bởi loader khác.

## 8. `java -jar` không dùng classpath theo cách class mode

Đây là trường hợp nhiều người gặp lỗi:

~~~bash
java -cp "lib/*" -jar app.jar
~~~

Khi dùng **`java -jar app.jar`**, Java launcher chọn JAR thực thi có `Main-Class` trong `META-INF/MANIFEST.MF`. Theo tài liệu launcher, các thiết lập classpath thông thường như `-cp`/biến `CLASSPATH` **không được áp dụng làm user classpath của JAR mode**. Vì vậy đừng kỳ vọng ví dụ trên thêm mọi JAR ở `lib/*` cho ứng dụng `-jar`.

Muốn chạy với classpath rõ ràng và biết tên lớp chứa `main`, dùng **class mode**:

~~~powershell
java -cp "app.jar;lib\*" com.example.Main  # Windows
~~~

~~~bash
java -cp "app.jar:lib/*" com.example.Main   # Linux/macOS
~~~

Trong trường hợp thật, cần thay `com.example.Main` bằng binary name đúng của lớp chứa `public static void main(String[] args)`.

Một **executable JAR** còn có thể khai báo thuộc tính `Class-Path` trong manifest để tham chiếu tới JAR phụ thuộc (thường là các URL tương đối tới vị trí JAR), theo quy tắc manifest của JAR. Cơ chế đó khác với việc truyền `-cp` để thêm thư viện cho `-jar`.

Các JAR của framework có launcher riêng hoặc nested JAR loader (ví dụ JAR đóng gói theo kiểu framework) có thể thêm cơ chế tìm phụ thuộc không trùng với classpath truyền thống; cần đọc hợp đồng của launcher đó, tránh suy luận chỉ từ tên tệp `.jar`.

## 9. Classpath khác với module path và custom loader

Từ Java 9, Java có **Java Platform Module System**:

| Khái niệm | Dùng cho | Thiết lập ví dụ |
| --- | --- | --- |
| Classpath | Class/JAR chạy ở chế độ classpath, thuộc **unnamed module** của loader tương ứng. | `java -cp "out:lib/*" demo.Main` |
| Module path | Đường tìm **named modules** được resolve theo module system. | `java --module-path mods --module example.module/com.example.Main` |
| Custom loader URLs | Nguồn riêng của một loader do ứng dụng tạo. | `new URLClassLoader(urls, parent)` |

Lớp chỉ đặt trên **module path** không mặc nhiên trở thành class trên classpath, và ngược lại. Một class trong JAR do custom `URLClassLoader` đọc không nhất thiết xuất hiện trên `java.class.path`. Tương tự, những lớp được bootstrap/platform loader nạp không phải vì bạn đã liệt kê mọi module JDK trong `-cp`.

Nếu học về parent delegation: `-cp`/ `CLASSPATH` chủ yếu ảnh hưởng nguồn tìm của **system/application loader** khi khởi chạy class mode. Không nên coi đó là lệnh yêu cầu loader cha quét nguồn phụ thuộc của mọi loader con.

## 10. Phân biệt classpath với các đường dẫn Java khác

Những tùy chọn dễ bị nhầm:

- **`-d output` của `javac`**: nơi lưu kết quả compile, không phải classpath lúc chạy.
- **`--source-path` của `javac`**: nơi compiler có thể tìm source files khi cần, khác nơi tìm compiled classes/JAR.
- **`--module-path` / `-p`**: nơi tìm named modules, khác `--class-path`.
- **`PATH` của hệ điều hành**: nơi shell tìm chương trình thực thi `java` hoặc `javac`; không cho JVM biết `demo.Main` nằm ở đâu.
- **`JAVA_HOME`**: thường trỏ tới cài đặt JDK, không phải classpath.
- **`CLASSPATH`**: biến môi trường tùy chọn làm mặc định cho công cụ Java khi không được override; không quyết định JVM chọn JDK nào.
- **`java.library.path`**: liên quan tìm native libraries (JNI), khác đường tìm tệp `.class`.

Nếu gõ `java -version` thành công nhưng `java demo.Main` báo không thấy main class, `PATH` đã có thể ổn trong khi classpath hoặc tên lớp chính vẫn sai.

## 11. Kiểm tra runtime và xử lý lỗi thường gặp

### 11.1. Kiểm tra classpath đang dùng

Trong chương trình:

~~~java
System.out.println(System.getProperty("java.class.path"));
~~~

Đây là **một dòng minh họa**, có thể đặt trong phương thức `main` để xem đường tìm kiếm classpath do launcher khai báo. Kết quả hữu ích khi debug, nhưng **không đại diện toàn bộ** nguồn từ bootstrap/platform/module path hoặc custom loaders. Đặc biệt, đừng dùng nó để kết luận một plugin do loader riêng nạp đã nằm trên system classpath.

Trên terminal, có thể dùng thêm:

~~~bash
java -XshowSettings:properties -version
~~~

Tìm thuộc tính `java.class.path` trong phần property settings. Ngoài ra `java -verbose:class ...` hoặc tùy chọn log class loading thích hợp giúp quan sát nguồn lớp được nạp, nhưng output nhiều và không nên dùng như cách dự đoán mọi đường tìm class tiềm năng.

### 11.2. Lỗi sai classpath phổ biến

| Biểu hiện | Nguyên nhân thường gặp | Cách kiểm tra/sửa |
| --- | --- | --- |
| `Error: Could not find or load main class demo.Main` | Thiếu thư mục gốc chứa `demo/Main.class`, sai package hoặc sai tên lớp chính. | Xem cây file, dùng `-cp out/app demo.Main`, kiểm tra `package demo;`. |
| `ClassNotFoundException` | Mã hoặc loader không tìm được lớp có binary name được yêu cầu. | Kiểm tra nguồn của **loader đang tìm**, classpath/module path, tên binary name. |
| `NoClassDefFoundError` khi đang chạy | Dependency không còn thấy ở runtime dù compile thành công; cũng có thể do khởi tạo lớp trước đó thất bại. | Kiểm tra classpath runtime, cause/stack trace và lệnh thực thi thực tế. |
| `NoSuchMethodError` hoặc `NoSuchFieldError` | Runtime chọn phiên bản thư viện không tương thích với lúc biên dịch. | Kiểm tra trùng JAR, phiên bản và thứ tự classpath. |
| Command chạy ở thư mục A được, sang B thì lỗi | Dùng đường dẫn tương đối từ working directory. | Dùng absolute path hoặc nhất quán `cd` tới project root. |
| Chạy `-cp lib/*` mà class chính không thấy | Wildcard chỉ chọn JAR, không thêm `out/app` hoặc thư mục `.class` rời. | Thêm gốc class chính rõ ràng vào chuỗi classpath. |
| Đặt `CLASSPATH` rồi chạy `-cp ...` mà biến dường như bị bỏ qua | Tùy chọn CLI override biến môi trường. | Dùng một cấu hình classpath tường minh chứa đủ phần tử. |
| `java -cp ... -jar app.jar` vẫn thiếu dependency | JAR mode không lấy user classpath từ `-cp`. | Dùng manifest `Class-Path`, launcher của app hoặc chạy class mode. |
| Windows lỗi khi copy command Linux | Sai dấu phân cách `:` thay vì `;`, hoặc thiếu quote trong PowerShell. | Dùng `;` và bọc toàn chuỗi bằng dấu nháy trên Windows. |

### 11.3. Checklist tự kiểm tra

1. **Có chạy class mode hay `-jar` mode?** Nếu là `-jar`, xem manifest và cơ chế đóng gói JAR.
2. **Đã thêm thư mục gốc của package?** Ví dụ `demo.Main` đòi `<root>/demo/Main.class`.
3. **Compile classpath và runtime classpath có khớp nhu cầu?** Runtime phải tìm được thư viện thật sự được tham chiếu.
4. **Có một `-cp` / `--class-path` tường minh?** Nếu có, đừng trông chờ `CLASSPATH` tự nối vào.
5. **Có vô tình bỏ quên `.`?** Khi classpath chỉ định rõ, current directory không tự xuất hiện nếu bạn không thêm.
6. **Separator hợp OS và shell chưa?** Windows `;`, Linux/macOS `:`, thường quote toàn bộ chuỗi.
7. **Có JAR trùng class/phiên bản?** Tránh dựa vào thứ tự wildcard không xác định.
8. **Có custom loader/module path không?** Lớp ở nguồn khác không nhất thiết nằm trong user classpath.
9. **Đang dùng build tool?** Maven/Gradle/IDE thường tự sinh runtime classpath; cần kiểm tra lệnh Java thực mà chúng khởi chạy thay vì đoán bằng biến máy.

## 12. Câu hỏi ôn tập

**Câu 1:** Trên Windows, `CLASSPATH=out\app;out\lib` đã đặt, nhưng chạy `java -cp out\app demo.Main` gặp `NoClassDefFoundError` đối với `util.Helper`. Vì sao?

**Đáp:** `-cp out\app` override `CLASSPATH`, nên system classpath không có `out\lib`. Sửa thành `java -cp "out\app;out\lib" demo.Main`.

**Câu 2:** Dùng `-cp lib/*` có tự tìm mọi JAR con trong `lib/a/` và mọi `.class` rời dưới `lib` không?

**Đáp:** Không. Wildcard chỉ mở rộng các JAR trực tiếp trong `lib`, không đệ quy; muốn tìm class rời phải thêm chính thư mục gốc class.

**Câu 3:** Vì sao `javac -cp lib/helper.jar` thành công mà `java -cp out/app demo.Main` lại lỗi?

**Đáp:** Compiler có JAR lúc biên dịch nhưng JVM không có cùng dependency ở runtime. Thêm JAR vào runtime classpath.

**Câu 4:** `java -jar app.jar` và `java -cp "app.jar:lib/*" com.example.Main` có tương đương không?

**Đáp:** Không. JAR mode dùng manifest/main-class của JAR và cách giải dependency riêng; class mode chọn tên lớp chính cùng classpath tường minh.

## Tài liệu chính thức

- [Java SE 21 – Lệnh `java`, tùy chọn `--class-path`, wildcard và `-jar`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html)
- [Java SE 21 – Lệnh `javac`, `-cp`, biến `CLASSPATH` và `-d`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html)
- [Java SE 21 – `ClassLoader` và parent delegation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-and-module-path-003">Classpath lúc biên dịch khác classpath lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Classpath lúc biên dịch khác classpath lúc chạy

## 1. Vấn đề cốt lõi: biên dịch thành công chưa bảo đảm chạy thành công

Một dự án Java có thể biên dịch hoàn toàn bình thường nhưng khi thực thi lại báo:

```text
java.lang.NoClassDefFoundError: com/acme/lib/Formatter
```

Tình huống này thường xuất hiện khi **dependency có mặt trong classpath lúc biên dịch, nhưng không có mặt (hoặc không đúng phiên bản) trong classpath mà JVM sử dụng lúc chạy**. Trình biên dịch cần tìm kiểu để kiểm tra tên, phương thức, trường và quan hệ kiểu; JVM phải tìm được các định nghĩa thật tại thời điểm cần nạp/liên kết/khởi tạo lớp.

Đây là hai môi trường riêng biệt:

- **Compile-time classpath:** tập thư mục, JAR và các artifact mà công cụ biên dịch (thường là `javac`, thông qua CLI/IDE/build tool) sử dụng để phân giải các loại phụ thuộc bên ngoài source đang biên dịch.
- **Runtime classpath:** tập thư mục, JAR và các artifact mà launcher/JVM cùng các class loader liên quan có thể dùng để nạp các class cần thiết khi chạy ứng dụng trên classpath.
- **Module path:** từ Java 9, ứng dụng có thể dùng JPMS; module path và quy tắc module resolution không đồng nghĩa với classpath truyền thống. Khi dùng module, phải kiểm tra cả quan hệ `requires`, `exports` và cách launcher cấu hình module path.

Hai classpath có thể giống nhau, nhưng **không mặc nhiên giống nhau**. Chúng thường khác khi dùng IDE, dependency scope, ứng dụng đóng gói bằng JAR, chạy Docker, triển khai server, CI/CD, plugin hoặc phiên bản thư viện không thống nhất.

### Ví dụ đời thực

Ứng dụng gọi `Formatter.format()` của thư viện `shared-formatting.jar`. Máy lập trình có JAR trong compile classpath nên `javac` xác nhận phương thức tồn tại và tạo `Main.class`. Khi đưa riêng `Main.class` lên server nhưng quên `shared-formatting.jar`, JVM vẫn có thể tìm được entry point `Main`, song lúc cần `Formatter` thì không tìm ra lớp.

Vì vậy, câu hỏi “Tại sao `javac` không báo lỗi?” được trả lời bằng việc **javac và JVM đang tìm phụ thuộc theo hai cấu hình khác nhau**.

## 2. Classpath biên dịch và classpath runtime trả lời hai câu hỏi khác nhau

Một thư mục `out/app` chứa `com/acme/app/Main.class`, còn `out/lib` chứa `com/acme/lib/Formatter.class`. Nếu biên dịch `Main.java` với `javac -cp out/lib -d out/app ...`, compiler tìm được kiểu `Formatter`; **nó không tự sao chép `Formatter` vào output ứng dụng**.

| Giai đoạn | Lệnh ví dụ trên Linux/macOS | Kết quả cần quan sát |
| --- | --- | --- |
| Compile | `javac -cp out/lib -d out/app src/app/com/acme/app/Main.java` | Compiler tìm được các kiểu bên ngoài |
| Runtime đủ thư viện | `java -cp "out/app:out/lib" com.acme.app.Main` | Class và dependency có thể được nạp |
| Runtime thiếu thư viện | `java -cp out/app com.acme.app.Main` | Main có thể khởi động nhưng liên kết tới Formatter thất bại |

Windows dùng dấu chấm phẩy `;` giữa các classpath entries, còn Linux/macOS dùng dấu hai chấm `:`. `javac -d` chọn **nơi ghi** bytecode, không định nghĩa đường tìm thư viện cho tiến trình `java` sẽ chạy sau.

## 3. Khi nào không chỉ là chuyện thiếu file?

Classpath entry phải là **root cây package** hoặc JAR phù hợp. Đặt JAR ở một thư mục không có nghĩa loader của ứng dụng tự tìm nó. Ngoài việc thiếu thư viện, compile-time và runtime còn có thể chọn **hai phiên bản class cùng tên**: JVM tìm ra class nhưng API trong bytecode không khớp, dẫn tới `NoSuchMethodError`, `NoSuchFieldError` hoặc lỗi linkage. Thí nghiệm tiếp theo cô lập một dependency để minh họa đúng nguyên nhân; việc Gradle/Maven chọn và phân phối dependency được trình bày chuyên sâu ở bài cuối chương.

## 4. Thí nghiệm Java 17+: compile có Formatter, runtime lại thiếu Formatter

Ta tạo hai phần độc lập: một lớp thư viện và một class ứng dụng. Ví dụ không phụ thuộc Maven/Gradle; chỉ yêu cầu JDK 17+.

Tạo cây thư mục theo đường dẫn:

```text
src/
├── lib/
│   └── com/acme/lib/Formatter.java
└── app/
    └── com/acme/app/Main.java
```

Nội dung file `src/lib/com/acme/lib/Formatter.java`:

```java
package com.acme.lib;

public final class Formatter {
    private Formatter() {}

    public static String format(String text) {
        return "Formatted: " + text;
    }
}
```

Nội dung file `src/app/com/acme/app/Main.java`:

```java
package com.acme.app;

import com.acme.lib.Formatter;

public final class Main {
    public static void main(String[] args) {
        System.out.println("Application started");
        System.out.println(Formatter.format("Java"));
    }
}
```

Hai class này có **package** và **đường dẫn** nhất quán. Không có giả định IDE hoặc công cụ build tự thêm thư viện.

### Bước 1 — Biên dịch thư viện

Từ thư mục gốc của ví dụ:

```bash
javac --release 17 -d out/lib src/lib/com/acme/lib/Formatter.java
```

Thư viện được ghi thành:

```text
out/lib/com/acme/lib/Formatter.class
```

### Bước 2 — Biên dịch ứng dụng với compile-time classpath có thư viện

```bash
javac --release 17 -cp out/lib -d out/app src/app/com/acme/app/Main.java
```

Lệnh này thành công vì `javac` tìm thấy `com.acme.lib.Formatter` từ `out/lib`. Kết quả ứng dụng được ghi vào:

```text
out/app/com/acme/app/Main.class
```

**Điểm quan trọng:** `javac -cp out/lib` chỉ cho compiler sử dụng thư viện; nó **không chép `Formatter.class` vào `out/app`**. Các file vẫn ở hai thư mục đầu ra riêng biệt.

### Bước 3 — Chạy với runtime classpath đầy đủ

Linux/macOS:

```bash
java -cp "out/app:out/lib" com.acme.app.Main
```

Windows (PowerShell hoặc Command Prompt):

```powershell
java -cp "out/app;out/lib" com.acme.app.Main
```

Stdout xác định:

```text
Application started
Formatted: Java
```

Có cả root chứa `Main.class` và root chứa `Formatter.class`, nên JVM có thể nạp lớp của chương trình và phụ thuộc.

### Bước 4 — Chạy với runtime classpath thiếu thư viện

```bash
java -cp out/app com.acme.app.Main
```

Ở lần chạy này, `Main.class` vẫn có trong `out/app`. Nhưng `out/lib` bị loại khỏi runtime classpath, nên JVM không có đường tìm lớp `com.acme.lib.Formatter` theo cơ chế thông thường.

Trên JVM thông dụng, stdout thường đã in `Application started` trước khi lệnh gọi `Formatter.format(...)` gây lỗi; phần stderr sẽ chứa thông tin tương tự:

```text
Exception in thread "main" java.lang.NoClassDefFoundError: com/acme/lib/Formatter
    ...
Caused by: java.lang.ClassNotFoundException: com.acme.lib.Formatter
    ...
```

Các dòng stack trace cụ thể, thứ tự xuất hiện giữa stdout/stderr khi redirect và thời điểm JVM chọn resolution có thể phụ thuộc triển khai; vì vậy đây là **mẫu dạng lỗi**, không phải khẳng định toàn bộ output bất biến trên mọi VM. Không cần đoán số dòng source trong stack trace.

**Tại sao `Main` biên dịch được?** Vì tại compile time có `out/lib`. **Tại sao lại lỗi khi chạy?** Vì tại runtime chỉ có `out/app`, không có nguồn để load `Formatter`. Đây là ví dụ tách bạch nhất về hai classpath.

### Bước 5 — Chạy lại sau khi sửa đường dẫn

Thêm `out/lib` trở lại runtime classpath như bước 3 và chạy lại; output của chương trình sẽ trở về hai dòng thành công. Với một tiến trình đã gặp lỗi khởi tạo hoặc linkage trong class loader đang hoạt động, cách sửa classpath thường cần **khởi chạy tiến trình mới**, không thể chỉ sửa file rồi kỳ vọng loader đang dùng tự đổi cấu hình.

## 5. Lúc compile và lúc runtime có thể cùng tên nhưng khác phiên bản

Một lỗi khó hơn trường hợp hoàn toàn thiếu class: **cả hai môi trường đều có `com.acme.lib.Formatter`, nhưng bytecode thực tế khác nhau**.

Ví dụ compile-time có thư viện v1:

```java
package com.acme.lib;

public final class Formatter {
    private Formatter() {}

    public static String format(String value) {
        return "v1:" + value;
    }
}
```

Ứng dụng được biên dịch với lời gọi `Formatter.format("Java")`. Sau khi triển khai, runtime lại dùng bản v2:

```java
package com.acme.lib;

public final class Formatter {
    private Formatter() {}

    public static String render(String value) {
        return "v2:" + value;
    }
}
```

Hai đoạn là **hai phiên bản thay thế nhau của cùng binary name**, không phải hai file được biên dịch đồng thời trong cùng output. Nếu khi chạy JVM nạp bản v2 vốn không còn `format(String)`, lời gọi đã biên dịch trong `Main.class` vẫn tham chiếu tới method signature cũ. Khi JVM giải quyết lời gọi đó, có thể ném:

```text
java.lang.NoSuchMethodError: 'java.lang.String com.acme.lib.Formatter.format(java.lang.String)'
```

Tình huống tương tự xảy ra khi xóa/đổi field (`NoSuchFieldError`), đổi loại instance/static method, thay class thành interface, hoặc thay đổi quan hệ kế thừa theo cách không tương thích nhị phân.

Vì thế, không nên chỉ kiểm tra “JAR có tồn tại?”; phải kiểm tra **đúng phiên bản runtime đã nạp** và ABI mà mã đã biên dịch kỳ vọng.

### Bytecode compatibility không phải luôn source compatibility

- **Source compatibility:** mã nguồn có thể biên dịch lại được với API mới không?
- **Binary compatibility:** file `.class` đã biên dịch từ trước còn liên kết/chạy với API mới không?
- **Behavioral compatibility:** chạy được nhưng kết quả có đúng kỳ vọng không?

Một thay đổi dependency có thể làm source không biên dịch được, nhưng binary cũ vẫn chạy; hoặc source cũ biên dịch với bản A trong khi runtime nạp bản B rồi sinh `NoSuchMethodError`. Ba khái niệm này cần được phân biệt khi chẩn đoán.

## 6. ClassLoader quyết định thực tế lớp nào được thấy ở runtime

Một classpath cấu hình đúng còn phải xét **class loader đang xử lý yêu cầu**.

Trong ứng dụng Java chạy trên classpath đơn giản, application/system loader thường tìm lớp từ những đường dẫn mà launcher cung cấp. Nhưng ở container, plugin framework, servlet container hoặc hệ module, nhiều loader có thể có vùng nhìn thấy khác nhau.

```text
Application / Parent Loader
    |- app.Main
    |- lib.Formatter v1
    |
    +-- Plugin Loader
            |- plugin.Entry
            |- lib.Formatter v2
```

Với parent-first delegation, yêu cầu `PluginLoader.loadClass("lib.Formatter")` có thể được parent thỏa mãn bởi v1 **trước khi** loader con tìm tới v2. Dù JAR v2 có mặt trong thư mục của plugin, class được nạp thực tế vẫn là bản v1.

Ngược lại, child-first được cấu hình có chủ đích có thể chọn bản v2 ở plugin trước; lúc đó hai loader có thể định nghĩa hai bản cùng tên. Nhưng nếu kiểu thư viện riêng của plugin bị truyền qua API host dùng bản v1, sẽ có nguy cơ `ClassCastException` hoặc `LinkageError` liên quan type identity.

### Danh tính kiểu còn phụ thuộc defining loader

Một kiểu runtime được nhận diện bởi cặp **(binary name, defining loader)**. Hai loader độc lập định nghĩa `lib.Formatter` tạo ra hai kiểu riêng, kể cả khi bytecode giống nhau.

Vì vậy, muốn chẩn đoán lớp thực sự nào đang được dùng, có thể in:

```java
static void describe(Class<?> type) {
    System.out.println("class=" + type.getName());
    System.out.println("loader=" + type.getClassLoader());
    java.security.ProtectionDomain pd = type.getProtectionDomain();
    java.security.CodeSource cs = pd == null ? null : pd.getCodeSource();
    System.out.println("source="
            + (cs == null ? "<unknown>" : cs.getLocation()));
}
```

Đây là **phương thức hỗ trợ**, cần đặt trong class ứng dụng và gọi với đối tượng `Class<?>` đã được tải; nó không phải chương trình `main` riêng. `getClassLoader()` trả `null` khi class do bootstrap loader định nghĩa. `CodeSource` có thể `null`, đặc biệt với các lớp nền tảng, module hoặc class được định nghĩa tùy biến. Không kết luận classpath sai chỉ vì `source=<unknown>`.

**TCCL không đồng nghĩa defining loader:** `Thread.currentThread().getContextClassLoader()` chỉ là context loader mà một số API, như service discovery, lựa chọn để tìm dependency; nó không tự thay đổi loader đã định nghĩa một lớp cụ thể.

## 7. Thời điểm lỗi xuất hiện phụ thuộc loading và linking

Có lúc ứng dụng chạy được vài giây rồi mới báo thiếu class, dù thư viện không được đưa vào runtime từ đầu. Điều đó liên quan đến vòng đời lớp:

1. **Loading:** JVM tìm định nghĩa lớp khi cần; superclass và superinterface là phụ thuộc cấu trúc cần thiết khi định nghĩa.
2. **Linking:** verifier, preparation, resolution của constant pool. Resolution có thể diễn ra sớm hoặc khi tham chiếu được dùng, trong giới hạn đặc tả.
3. **Initialization:** khi active use yêu cầu, JVM khởi tạo lớp và những phụ thuộc cần khởi tạo theo quy tắc JLS/JVMS.

Ví dụ:

```java
class OptionalFeature {
    static void run() {
        LibraryOnlyAtRuntime.execute();
    }
}
```

Đây là **mã minh họa, không phải chương trình chạy độc lập**; giả định `LibraryOnlyAtRuntime` có mặt lúc biên dịch. Việc một code path chưa chạy không chứng minh tất cả phụ thuộc đều hiện diện; JVM có thể phân giải tham chiếu ở thời điểm khác và fail khi chức năng được sử dụng.

Trái lại, thiếu superclass hoặc superinterface trực tiếp/gián tiếp của class đang được định nghĩa có thể khiến quá trình nạp lớp thất bại **trước cả khi gọi phương thức**.

Vì vậy, không nên dùng riêng thời điểm stack trace xuất hiện để kết luận lỗi chỉ nằm ở câu lệnh Java đang hiển thị. Phải xem **lớp phụ thuộc nào được JVM tìm ở thời điểm đó**, loader nào, và quá trình linking/initialization có liên quan hay không.

## 8. Những loại lỗi điển hình khi classpath compile và runtime khác nhau

| Lỗi / Triệu chứng | Diễn giải có khả năng | Cần kiểm tra |
| --- | --- | --- |
| Compile: `package com.acme.lib does not exist` | Compile classpath thiếu thư viện | `javac -cp`, dependency scope, đường dẫn JAR/classes |
| Compile: `cannot find symbol` | Trình biên dịch không phân giải được tên kiểu, method hoặc field | Import, tên package, phiên bản API, source path |
| Runtime: `Could not find or load main class` | Launcher không tìm hoặc không nạp được entry class | Runtime `-cp`, root package, tên main, JAR manifest |
| Runtime: `ClassNotFoundException` | API như `Class.forName` / `loadClass` tìm theo tên không thấy lớp | Tên nhị phân, loader được yêu cầu, file/JAR nằm ở đâu |
| Runtime: `NoClassDefFoundError` | JVM không thể dùng lớp được kỳ vọng; thường thiếu phụ thuộc hoặc lớp từng khởi tạo lỗi | Chuỗi `Caused by`, lỗi đầu tiên, dependency đã đóng gói |
| Runtime: `NoSuchMethodError` / `NoSuchFieldError` | Binary chạy với API khác phiên bản compile | Vị trí class runtime, ABI method/field, dependency conflicts |
| Runtime: `IncompatibleClassChangeError` | Thay đổi không tương thích về class/interface, static/instance hoặc quan hệ kiểu | Các JAR compile/runtime và method descriptor |
| Runtime: `UnsupportedClassVersionError` | Class được biên dịch cho JVM mới hơn JVM đang chạy | `java -version`, target/release, pipeline JDK |
| Runtime: `ClassCastException` giữa các lớp cùng tên | Có thể do hai defining loader độc lập | `clazz.getClassLoader()` ở cả hai phía |
| Runtime: `ServiceConfigurationError` | Service provider thiếu hoặc không thể nạp/khởi tạo/thực thi SPI | Provider JAR, `META-INF/services`, loader, module config |

**Đọc nguyên nhân, không chỉ loại lỗi.** `NoClassDefFoundError` không đồng nghĩa “classpath chắc chắn thiếu”: initializer từng thất bại có thể khiến lớp ở trạng thái erroneous và các lần gọi sau sinh lỗi cùng loại. `ClassNotFoundException` thường có thể nằm trong `Caused by` của `NoClassDefFoundError` khi loader không tìm ra phụ thuộc.

**Có mặt trên classpath chưa chắc sử dụng được.** Thư viện có thể chứa class đúng tên nhưng được compile cho Java phiên bản cao hơn, phụ thuộc transitive thiếu, classloader không thấy nó, hoặc JPMS không cho phép truy cập package.

## 9. Dependency trực tiếp, bắc cầu và runtime

Ứng dụng có thể compile với `A.jar` trong khi A còn dùng `B.jar`. Nếu runtime thiếu B, code chỉ lỗi khi đường thực thi cần B. Với Gradle, `compileOnly` có trong compile classpath nhưng không tự vào runtime classpath; `runtimeOnly` có chiều ngược lại. Maven `provided` có nghĩa môi trường triển khai được kỳ vọng cung cấp dependency phù hợp.

| Hiện tượng | Cần đối chiếu |
| --- | --- |
| Compile thành công, runtime thiếu class | `compileClasspath` và `runtimeClasspath`, các JAR hiện diện ở runtime |
| Tìm được class, thiếu method | Phiên bản class runtime so với signature compile |
| Có dependency trong build nhưng triển khai thiếu | Artifact cuối cùng, launcher và distribution, không chỉ file khai báo |

Bài **Khác biệt giữa dependency của công cụ build và đường dẫn thực tế của JVM** tập trung vào configuration/scope, resolution và việc một plain JAR khác task `run`. Trong bài hiện tại, hãy theo dõi *thời điểm JVM cần nạp/liên kết class* và phân tích lỗi đầu tiên.

## 10. Chạy JAR: khác biệt giữa -jar và -cp

Ví dụ đóng gói entry class vào `app.jar`:

```bash
jar --create --file app.jar --main-class com.acme.app.Main -C out/app .
```

Lệnh này chỉ đưa class trong `out/app` vào `app.jar` theo cấu trúc đã tạo; nó **không tự bao gồm** `Formatter.class` đang ở `out/lib`.

Nếu sử dụng:

```bash
java -jar app.jar
```

Runtime sẽ thiếu dependency nếu nó không xuất hiện qua các cơ chế mà Java launcher/JAR manifest hỗ trợ. Một executable JAR thông thường có thể dùng **manifest `Class-Path`** chỉ đến các JAR phụ thuộc bên ngoài (đường dẫn tương đối theo quy tắc manifest), hoặc có thể được build thành uber/fat JAR theo công cụ đóng gói phù hợp.

Đừng kỳ vọng:

```bash
java -cp out/lib -jar app.jar
```

sẽ tự thêm `out/lib` vào user classpath cho mode `-jar` như khi gọi main class bằng `-cp`. Theo hợp đồng launcher, khi dùng `-jar`, classpath do `-cp` chỉ định bị bỏ qua cho việc chọn user classes; manifest và cách đóng gói JAR là thành phần phải kiểm tra.

Đối với **plain JAR**, có thể chạy theo tên main class và classpath chứa cả JAR + dependency:

Linux/macOS:

```bash
java -cp "app.jar:out/lib" com.acme.app.Main
```

Windows:

```powershell
java -cp "app.jar;out/lib" com.acme.app.Main
```

Lựa chọn giữa `java -jar` và `java -cp` phải tương thích với loại artifact. Ví dụ, executable JAR do framework đóng gói với nested JAR có launcher riêng; lệnh Java classpath thuần có thể không nhìn thấy thư viện nested theo cách người dùng mong đợi.

## 11. Classpath, module path và môi trường từ Java 9 trở đi

Từ Java 9, JPMS bổ sung **module path**. Khi sử dụng named modules, các tùy chọn quan trọng gồm:

- `--module-path` / `-p`: đường tìm các module (JAR modular hoặc exploded module).
- `--module` / `-m`: yêu cầu launcher chạy main class của named module.
- `requires`: quan hệ đọc module được khai báo trong `module-info.java`.
- `exports`: package mà module cho phép truy cập ở mức module.
- `opens`: package mở cho reflective access theo quy tắc JPMS.

**Module path không phải “classpath mới” có semantics y hệt.** Trên classpath truyền thống, code thường nằm trong unnamed module; trên module path, việc phân giải module và visibility/encapsulation bổ sung ràng buộc.

Nếu ứng dụng compile thành công nhờ module có mặt trên compile module path nhưng runtime module graph thiếu nó, lỗi có thể xuất hiện ngay khi Java launcher resolve graph, chẳng hạn thông báo kiểu:

```text
Error occurred during initialization of boot layer
java.lang.module.FindException: Module ... not found, required by ...
```

Các dấu `...` ở đây **minh họa vị trí tên module**, không phải stdout cố định. Biện pháp là kiểm tra runtime module path, tên module và graph resolved, không chỉ thêm ngẫu nhiên một JAR vào classpath rồi kỳ vọng named module đọc được.

Không nên chữa lỗi module encapsulation bằng cách đổi classpath bừa bãi: nếu thiếu `exports`/`opens` hoặc module readability, loại lỗi và giải pháp khác với việc hoàn toàn thiếu file `.class`.

## 12. Thực hành xác minh compile và runtime classpath

Có thể thực hiện theo quy trình, đặc biệt khi “works in IDE, fails in production”:

1. **Ghi lệnh biên dịch thực tế.** Không chỉ ghi “mình dùng javac”: lấy compile classpath hoặc classpath do IDE/Gradle/Maven cung cấp; lưu bản phân giải dependency và phiên bản.
2. **Ghi lệnh khởi động thực tế.** Nhìn đúng `java` command line, `-cp`, `-jar`, `--module-path`, working directory, environment và các JVM launch options.
3. **Kiểm tra file được đóng gói.** Với JAR thuần có thể dùng `jar --list --file app.jar` hoặc `jar tf app.jar`; lưu ý nested JAR/framework launcher có quy tắc riêng.
4. **Kiểm tra binary name và root path.** Class `com.acme.app.Main` phải được tìm qua gốc chứa `com/acme/app/Main.class`, không phải gốc nằm sâu ở package directory.
5. **Nếu có lỗi liên kết, xác định lớp thực sự được nạp.** Xem defining loader và code source khi class có thể load.
6. **Dò JAR trùng class và xung đột phiên bản.** Hai JAR cùng chứa binary name không có nghĩa JVM tự chọn bản mới nhất; thứ tự và loader selection ảnh hưởng.
7. **So sánh JDK compile với JDK runtime.** Một class file target cao hơn có thể gây `UnsupportedClassVersionError`.
8. **Xem chuỗi `Caused by` và lỗi đầu tiên trong log.** Triệu chứng thứ hai sau initialization failure có thể che mất nguyên nhân gốc.

### Công cụ dòng lệnh hữu ích

```bash
java -version
javac -version
jar tf app.jar
javap -classpath out/app -public com.acme.app.Main
```

- `java -version` và `javac -version` giúp phát hiện hai JDK/JRE được chọn khác nhau.
- `jar tf` cho biết thành phần bên trong artifact (không cho biết tất cả dependency bên ngoài).
- `javap -classpath ...` cho phép xem cấu trúc class/bytecode đã compile trong root/JAR chỉ định, hữu ích khi nghi ngờ mã chạy không cùng bản với source.

Để quan sát class loading trên JVM hỗ trợ unified logging (JDK 17+):

```bash
java -Xlog:class+load=info -cp "out/app:out/lib" com.acme.app.Main
```

Lệnh ví dụ dùng separator Linux/macOS; trên Windows đổi dấu `:` trong `-cp` thành `;`. Log class-load giúp nhận biết nhiều class được nạp từ nguồn nào, nhưng có thể rất dài; phân biệt nguồn bytecode với **defining loader** và đọc đúng class đang gặp lỗi.

### Classpath ngay trong JVM

Đối với một ứng dụng chạy bằng classpath thuần, có thể in:

```java
System.out.println(System.getProperty("java.class.path"));
```

Đây là **snippet minh họa**, không phải chương trình độc lập. Giá trị này hữu ích để xem Java launcher cấu hình user classpath, nhưng **không biểu diễn mọi URL của mọi custom class loader**, module path hoặc các nguồn bytecode động. Vì vậy, không thấy plugin JAR trong chuỗi property chưa đủ kết luận plugin loader không thể nạp nó.

## 13. Những ngộ nhận thường gặp

| Nhận định | Thực tế |
| --- | --- |
| “`javac` compile được thì JVM luôn thấy các class ấy.” | Compile classpath và runtime classpath độc lập; build không tự đóng gói mọi JAR đã dùng để compile. |
| “Đã `-d out/app` thì `java` tự biết tìm ở out/app.” | Phải dùng đường chạy thích hợp như `java -cp out/app ...`. |
| “Classpath có JAR là chắc chắn JVM dùng đúng bản thư viện.” | Có thể tồn tại JAR/class trùng tên, loader cha ưu tiên bản khác, hoặc module graph không dùng JAR ấy. |
| “`-cp` luôn được áp dụng khi kèm `-jar`.” | Mode `-jar` dùng quy tắc classpath từ JAR/manifest và không dùng `-cp` để tìm user classes theo cách thông thường. |
| “`lib/*` tự quét mọi JAR trong mọi thư mục con.” | Wildcard launcher không phải recursive file discovery; cần đóng gói và bố trí thư mục đúng. |
| “Cứ thấy `NoClassDefFoundError` là bị thiếu JAR.” | Có thể thiếu dependency, hoặc lớp từng khởi tạo lỗi; phải xem cause và bối cảnh. |
| “Trường hợp `NoSuchMethodError` không liên quan classpath.” | Rất thường do runtime nạp phiên bản library khác compile-time ABI. |
| “Chỉ cần `CLASSPATH` môi trường thì khỏi cần biết câu lệnh.” | `-cp`/`--class-path` trực tiếp có thể thay đổi/ghi đè classpath mặc định; launch tool/framework có thể tự thiết lập. |
| “`--release 17` đóng gói dependency Java 17.” | `--release` kiểm soát mục tiêu biên dịch, không gom thư viện bên thứ ba vào artifact. |
| “Tất cả class đều dùng system classpath.” | Class loader tùy biến, parent delegation và JPMS có thể tạo nhiều phạm vi tìm kiếm. |

## 14. Câu hỏi tự kiểm tra

**Câu 1.** `javac -cp lib/api.jar -d classes Main.java` chạy thành công. Nếu `Main` dùng class trong `api.jar`, lệnh `java -cp classes Main` có chắc chạy được không?

**Trả lời:** Không. Runtime cần có dependency khi lớp được dùng; nếu JVM không tìm được class API, có thể xảy ra lỗi nạp/liên kết. Phải đưa dependency vào runtime classpath hoặc cơ chế launcher tương ứng.

**Câu 2.** Trên Windows, hai root classpath `out/app` và `out/lib` được ngăn bằng ký tự nào?

**Trả lời:** Dấu `;`. Trong PowerShell, sử dụng `java -cp "out/app;out/lib" ...` để giữ argument classpath là một chuỗi.

**Câu 3.** Trên Linux, lệnh `java -cp out/app com.acme.app.Main` báo `NoClassDefFoundError: com/acme/lib/Formatter`, trong khi compile đã dùng `-cp out/lib`. Nguyên nhân trực tiếp đáng kiểm tra nhất?

**Trả lời:** Runtime đã bỏ `out/lib`. Dùng `java -cp "out/app:out/lib" com.acme.app.Main`.

**Câu 4.** Runtime có `Formatter.class`, nhưng báo `NoSuchMethodError` khi gọi `Formatter.format(String)`. Vì sao?

**Trả lời:** Runtime có thể nạp phiên bản `Formatter` khác ABI mà `Main.class` được biên dịch với; phải so sánh class thực tế, chữ ký phương thức và phiên bản JAR.

**Câu 5.** Plugin có JAR v2 nhưng parent loader đã nạp class cùng tên từ JAR v1. Thêm JAR v2 vào plugin classpath có chắc sửa được không?

**Trả lời:** Không. Parent-first có thể tiếp tục trả bản v1. Cần kiểm tra defining loader và thiết kế delegation/ranh giới API thích hợp.

**Câu 6.** `java -jar app.jar` không tìm được dependency, nhưng thêm `-cp lib/*` cùng lệnh `-jar` vẫn thất bại. Hướng xử lý?

**Trả lời:** Kiểm tra loại executable JAR, manifest `Class-Path`, cơ chế framework launcher hoặc chạy main class bằng `-cp` chứa cả app.jar và dependency nếu là plain JAR phù hợp.

**Câu 7.** Vì sao `Class.forName("com.acme.lib.Formatter")` có thể ném `ClassNotFoundException` trong khi một lần invoke code đã compile lại ném `NoClassDefFoundError`?

**Trả lời:** `Class.forName` biểu lộ ngoại lệ tìm lớp qua API; khi JVM đang giải quyết phụ thuộc bytecode đã được biên dịch, lỗi liên kết do class không khả dụng có thể là `NoClassDefFoundError`. Phải đọc chuỗi cause để biết cơ chế gây lỗi.

## 15. Checklist triển khai đáng tin cậy

Trước khi coi bản build là có thể chạy được trên môi trường khác:

- [ ] Artifact triển khai gồm **đúng** main class, các class của ứng dụng và mọi dependency runtime cần thiết, hoặc có hợp đồng rõ về dependency do môi trường cung cấp.
- [ ] Runtime JDK tương thích với target class file và các thư viện.
- [ ] Lệnh chạy sử dụng đúng `-cp` / `-jar` / `--module-path` theo kiểu đóng gói đã chọn.
- [ ] Không vô tình sử dụng bản dependency khác do JAR trùng binary name hoặc class loader parent-first.
- [ ] Đã chạy smoke test bằng **artifact và lệnh khởi động thực tế** trên môi trường càng gần production càng tốt.
- [ ] Khi có lỗi, thu thập stack trace đầy đủ, class loader, nguồn bytecode và dependency graph đã phân giải.

## 16. Kết luận và nguồn tham khảo

Classpath lúc biên dịch trả lời câu hỏi **“compiler phải tìm được những kiểu nào để tạo bytecode hợp lệ?”**; classpath lúc chạy trả lời **“JVM và các class loader thật sự có thể tìm, nạp và liên kết những kiểu nào khi chương trình thực thi?”**. Hai tập phụ thuộc có thể khác nhau về **thư mục, JAR, phiên bản và cách delegation**. Để tránh lỗi, hãy kiểm tra cả compile configuration, runtime launch configuration và artifact được triển khai, thay vì suy luận từ thông báo “BUILD SUCCESSFUL”.

Tài liệu chính thức:

- Java SE 17 `javac`: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html
- Java SE 17 `java` launcher: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Java SE 17 `ClassLoader` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java Virtual Machine Specification 17 §5, Loading, Linking, and Initializing: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-and-module-path-004">Thứ tự tìm kiếm và lớp trùng tên trong nhiều JAR</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự tìm kiếm và lớp trùng tên trong nhiều JAR

## 1. Vấn đề: JVM chọn lớp nào khi nhiều JAR chứa cùng tên?

Giả sử cả `a.jar` và `b.jar` đều chứa file bytecode:

~~~text
demo/dupe/Message.class
~~~

Mã nguồn trong JAR A có thể trả chuỗi `"A"`, còn JAR B trả `"B"`. Nếu một ClassLoader được cấu hình để nhìn thấy **cả hai JAR**, lớp nào sẽ được dùng khi chương trình yêu cầu `demo.dupe.Message`?

Câu trả lời phụ thuộc vào **loader nhận yêu cầu**, **quan hệ ủy quyền tới parent** và **thứ tự tìm kiếm trong những nguồn mà loader đó quản lý**. Với `URLClassLoader` chuẩn, cơ chế phổ biến là:

1. Kiểm tra loader đã nạp class đó chưa.
2. **Parent-first:** hỏi loader cha trước.
3. Nếu cha không tìm thấy, tìm trong các URL của loader con theo thứ tự được cấu hình.
4. Khi một class đã được định nghĩa cho tên đó trong loader, những lần yêu cầu sau trả lại class tương ứng; đổi thứ tự danh sách URL **không tự thay thế class đã nạp**.

“Class trùng tên” có hai kết quả khác nhau cần phân biệt:

- **Một loader duy nhất thấy hai JAR:** thông thường chỉ class ở nguồn **được tìm thấy trước** trở thành class được loader đó định nghĩa; phiên bản nằm ở nguồn sau bị che khuất đối với tên đó.
- **Hai loader riêng biệt tự định nghĩa class cùng tên:** JVM có thể đồng thời tồn tại **hai runtime class khác nhau** mặc dù binary name giống nhau. Bản sắc runtime dựa trên tên nhị phân **và loader đã định nghĩa**, không dựa trên tên JAR.

Đây là nguồn gốc của nhiều lỗi “chạy máy này được, máy kia sai”, thư viện version xung đột, `NoSuchMethodError` hoặc `ClassCastException` khi ứng dụng dùng plugin, application server hoặc classpath chứa các bản JAR chồng chéo.

## 2. Thứ tự tìm kiếm là thứ tự *theo từng loader*

Mô hình parent-first của `ClassLoader.loadClass` thường được diễn đạt:

~~~text
Child.loadClass("demo.dupe.Message")
    │
    ├─ Đã được nạp trong child?
    │      └─ Có → trả Class<?> đã biết
    │
    ├─ Nhờ parent tìm class
    │      └─ Parent thấy → trả class của parent
    │
    └─ Parent không thấy → child.findClass(...)
           ├─ URL đầu tiên có bytecode hợp lệ → dùng class đó
           └─ Tiếp tục tới URL kế tiếp nếu chưa tìm thấy
~~~

Đây là sơ đồ khái niệm cho **ClassLoader tuân parent-first**, không phải lời hứa về mọi loader tùy biến. Một loader child-first có thể đảo thứ tự tra cứu. Java Platform Module System (JPMS) còn áp dụng module resolution/readability và không thể diễn đạt toàn bộ bằng thứ tự JAR trên classpath.

Trong `URLClassLoader` được tạo từ một mảng URL, thứ tự URL quyết định **ưu tiên tìm class và resource trong đường tìm kiếm của chính loader ấy**, khi parent không có kết quả và không có cấu hình đặc biệt khác như manifest `Class-Path` làm mở rộng đường tìm kiếm. Vì thế:

~~~java
// Mã minh họa (cần tạo aUrl, bUrl và parent).
URLClassLoader ab = new URLClassLoader(new URL[] {aUrl, bUrl}, parent);
URLClassLoader ba = new URLClassLoader(new URL[] {bUrl, aUrl}, parent);
~~~

Nếu parent không thấy `demo.dupe.Message`:

- `ab` thường chọn class từ `a.jar`.
- `ba` thường chọn class từ `b.jar`.
- Hai class được định nghĩa bởi **hai loader độc lập**, do đó không phải cùng runtime class.

Nếu parent **đã** thấy `demo.dupe.Message`, cả `ab` lẫn `ba` theo parent-first sẽ thường nhận **class của parent**. Khi đó việc đổi thứ tự URL trong child không thay được kết quả.

## 3. Classpath khi khởi chạy Java và các đường tìm kiếm

Ở chế độ **classpath/unnamed modules** thông thường:

~~~bash
# Linux/macOS: dấu : ngăn cách các entry.
java -cp "a.jar:b.jar:classes" demo.app.Main

# Windows: dấu ; ngăn cách các entry.
java -cp "a.jar;b.jar;classes" demo.app.Main
~~~

Khi **cùng loader ứng dụng** tìm một class trong các entry classpath, vị trí entry thường tạo ra thứ tự ưu tiên: entry phía trước được xét trước khi class chưa nạp. Nếu dùng wildcard `lib/*`, **thứ tự mở rộng các JAR bởi launcher không được đặc tả ổn định**: không thiết kế chương trình dựa trên “tên JAR sắp xếp alphabet” hoặc thứ tự liệt kê thư mục.

Lưu ý:

- Thứ tự classpath không vượt qua **parent delegation**. Class do parent cung cấp thắng các URL nằm trong child trong mô hình parent-first, bất kể tên file.
- Classpath có thể gồm thư mục và JAR. Một thư mục `classes/` chứa class cùng FQCN cũng có thể che JAR sau nó.
- `MANIFEST.MF` có thuộc tính `Class-Path` có thể thêm entry vào đường tìm kiếm của loader/launcher theo cơ chế riêng, nên quan sát mỗi danh sách `-cp` đôi khi chưa đủ để lý giải kết quả.
- Trên Java 9+, system class loader không được đảm bảo là `URLClassLoader`; không cast bừa để sửa URL của system loader đang chạy.
- `--module-path` không phải một danh sách classpath có quy tắc “JAR đứng trước thắng” tương đương. Named modules có cơ chế resolution, package ownership và class loading riêng.
- `Class-Path` của manifest không hoạt động giống lệnh `-cp` trong mọi hoàn cảnh: chỉ có ý nghĩa trong phạm vi các loại URL/JAR và quy tắc được hỗ trợ.

## 4. Bài thực nghiệm Java 17+: hai JAR trùng tên và một parent JAR

Ta tạo ba JAR với **cùng binary name** `demo.dupe.Message` nhưng kết quả khác nhau, và một host **không tham chiếu trực tiếp class này khi biên dịch**. Nhờ vậy host có thể chủ động kiểm tra loại được chọn qua reflection.

### 4.1. Cấu trúc dự án

~~~text
jar-order-demo/
├── host-src/
│   └── demo/host/JarOrderDemo.java
├── a-src/
│   └── demo/dupe/Message.java
├── b-src/
│   └── demo/dupe/Message.java
├── parent-src/
│   └── demo/dupe/Message.java
├── a-resources/
│   └── label.txt
├── b-resources/
│   └── label.txt
└── parent-resources/
    └── label.txt
~~~

Ba file `Message.java` ở **ba thư mục source khác nhau** đều khai báo `package demo.dupe;` và `public class Message`. Chúng được biên dịch **riêng thành ba output directory**, sau đó đóng thành ba JAR độc lập. Không biên dịch chung ba source trùng FQCN vào một lệnh `javac` vì điều đó dẫn tới định nghĩa lớp trùng nhau trong cùng compilation.

### 4.2. Class trong JAR A

File **`a-src/demo/dupe/Message.java`**:

~~~java
package demo.dupe;

public final class Message {
    public static String origin() {
        return "A";
    }
}
~~~

File **`a-resources/label.txt`** chứa chính xác:

~~~text
A
~~~

### 4.3. Class trong JAR B

File **`b-src/demo/dupe/Message.java`**:

~~~java
package demo.dupe;

public final class Message {
    public static String origin() {
        return "B";
    }
}
~~~

File **`b-resources/label.txt`**:

~~~text
B
~~~

### 4.4. Class tương ứng ở JAR của parent

File **`parent-src/demo/dupe/Message.java`**:

~~~java
package demo.dupe;

public final class Message {
    public static String origin() {
        return "PARENT";
    }
}
~~~

File **`parent-resources/label.txt`**:

~~~text
PARENT
~~~

`parent.jar` cho phép thực hiện thêm thí nghiệm về **parent-first precedence** mà không thay đổi host hay hai JAR A/B.

### 4.5. Host: tự chọn URL order và kiểm tra định danh lớp

File **`host-src/demo/host/JarOrderDemo.java`**:

~~~java
package demo.host;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JarOrderDemo {
    private JarOrderDemo() {
    }

    private static String origin(ClassLoader loader)
            throws ClassNotFoundException, NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {
        Class<?> type = loader.loadClass("demo.dupe.Message");
        return (String) type.getMethod("origin").invoke(null);
    }

    private static String resourceLabel(ClassLoader loader)
            throws IOException {
        try (InputStream stream = loader.getResourceAsStream("label.txt")) {
            if (stream == null) {
                return "(missing)";
            }
            return new String(
                    stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }

    private static boolean parentCanLoad(ClassLoader parent) {
        try {
            parent.loadClass("demo.dupe.Message");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: JarOrderDemo <a.jar> <b.jar>");
        }

        Path a = Path.of(args[0]).toAbsolutePath().normalize();
        Path b = Path.of(args[1]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(a) || !Files.isRegularFile(b)) {
            throw new IllegalArgumentException("Both JARs must exist");
        }

        URL aUrl = a.toUri().toURL();
        URL bUrl = b.toUri().toURL();
        ClassLoader parent = JarOrderDemo.class.getClassLoader();

        // AB và BA là hai loader khác nhau; mỗi loader có URL order riêng.
        try (URLClassLoader ab =
                     new URLClassLoader(new URL[] {aUrl, bUrl}, parent);
             URLClassLoader ba =
                     new URLClassLoader(new URL[] {bUrl, aUrl}, parent)) {

            System.out.println("AB class: " + origin(ab));
            System.out.println("BA class: " + origin(ba));

            System.out.println("AB resource: " + resourceLabel(ab));
            System.out.println("BA resource: " + resourceLabel(ba));

            Class<?> classAB = ab.loadClass("demo.dupe.Message");
            Class<?> classBA = ba.loadClass("demo.dupe.Message");

            System.out.println("AB and BA same Class: "
                    + (classAB == classBA));
            System.out.println("AB defined by child: "
                    + (classAB.getClassLoader() == ab));
            System.out.println("BA defined by child: "
                    + (classBA.getClassLoader() == ba));
            System.out.println("AB load again same Class: "
                    + (classAB == ab.loadClass("demo.dupe.Message")));
            System.out.println("Parent can load duplicate: "
                    + parentCanLoad(parent));
        }
    }
}
~~~

Điểm chủ chốt:

- Host không import `demo.dupe.Message`; nó chỉ biết tên nhị phân dạng chuỗi để thử nạp.
- `ab` có thứ tự URL `[a.jar, b.jar]`, còn `ba` là `[b.jar, a.jar]`.
- Cả hai được tạo với **cùng parent**: loader đã định nghĩa `JarOrderDemo`.
- `origin()` là phương thức `public static`, gọi qua reflection để minh họa class nào thực sự được chọn.
- `resourceLabel()` đọc `label.txt` bằng `getResourceAsStream`. Ví dụ giả định file chỉ có một bản trong mỗi JAR và không có resource cùng tên ở các entry khác ngoài những nguồn đã khai báo.
- `try-with-resources` đóng hai `URLClassLoader` để giải phóng nguồn JAR. `close()` **không buộc JVM unload class ngay**.

## 5. Biên dịch và đóng gói từng JAR

Yêu cầu JDK 17+ có `javac`, `jar` và `java` trên `PATH`. Đứng tại thư mục gốc `jar-order-demo/` đã tạo đủ file như trên.

**Bash (Linux/macOS/Git Bash):**

~~~bash
mkdir -p build/host build/a build/b build/parent dist

javac -encoding UTF-8 -d build/host \
  host-src/demo/host/JarOrderDemo.java

javac -encoding UTF-8 -d build/a a-src/demo/dupe/Message.java
javac -encoding UTF-8 -d build/b b-src/demo/dupe/Message.java
javac -encoding UTF-8 -d build/parent parent-src/demo/dupe/Message.java

cp a-resources/label.txt build/a/label.txt
cp b-resources/label.txt build/b/label.txt
cp parent-resources/label.txt build/parent/label.txt

jar --create --file dist/a.jar -C build/a .
jar --create --file dist/b.jar -C build/b .
jar --create --file dist/parent.jar -C build/parent .
~~~

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force build/host,build/a,build/b,build/parent,dist | Out-Null

javac -encoding UTF-8 -d build/host host-src/demo/host/JarOrderDemo.java

javac -encoding UTF-8 -d build/a a-src/demo/dupe/Message.java
javac -encoding UTF-8 -d build/b b-src/demo/dupe/Message.java
javac -encoding UTF-8 -d build/parent parent-src/demo/dupe/Message.java

Copy-Item a-resources/label.txt build/a/label.txt
Copy-Item b-resources/label.txt build/b/label.txt
Copy-Item parent-resources/label.txt build/parent/label.txt

jar --create --file dist/a.jar -C build/a .
jar --create --file dist/b.jar -C build/b .
jar --create --file dist/parent.jar -C build/parent .
~~~

Mỗi JAR có class `demo/dupe/Message.class` và resource `label.txt` riêng. Có thể kiểm tra:

~~~bash
jar --list --file dist/a.jar
jar --list --file dist/b.jar
jar --list --file dist/parent.jar
~~~

Các lệnh `jar --list` cũng có thể liệt kê `META-INF/MANIFEST.MF` và các directory entry. Đây là JAR thường, không dùng manifest `Class-Path`, không dùng multi-release JAR và không có module descriptor; do đó kết quả thí nghiệm chỉ phụ thuộc URL order/parent của loader nêu trên.

## 6. Chạy lần 1: parent không có class trùng tên

**Bash hoặc PowerShell:**

~~~bash
java -cp build/host demo.host.JarOrderDemo dist/a.jar dist/b.jar
~~~

**Stdout chính xác:**

~~~text
AB class: A
BA class: B
AB resource: A
BA resource: B
AB and BA same Class: false
AB defined by child: true
BA defined by child: true
AB load again same Class: true
Parent can load duplicate: false
~~~

**Giải thích:**

1. Parent là application loader của `JarOrderDemo` và chỉ chứa `build/host`; không có `Message.class`. Khi `ab` hỏi parent, parent không tìm thấy, nên `ab` tìm A trước B và lấy class `Message` của A.
2. Loader `ba` độc lập, tìm B trước A, nên lấy class của B.
3. Hai loader khác nhau **tự định nghĩa** `demo.dupe.Message`; `classAB == classBA` là `false`. Việc cả hai có cùng `getName()` không làm chúng thành cùng runtime type.
4. Gọi `ab.loadClass(...)` lần nữa trả về class đã biết, không tải một phiên bản khác từ B.
5. Đối với resource `label.txt`, `URLClassLoader` hỏi parent trước rồi tìm trong các URL của mình. Khi parent không chứa resource, thứ tự A/B cũng cho ra `A` hoặc `B`.
6. `parentCanLoad(parent)` trả `false` vì parent không tự duyệt URL của các loader con.

**Không đọc kết quả “A thắng” thành quy tắc toàn JVM.** Chính loader `ab` ưu tiên A trong đường tìm kiếm **riêng** của nó; loader `ba` lại ưu tiên B. Nếu có hai loader khác nhau, cả hai class có thể tồn tại cùng lúc.

## 7. Chạy lần 2: parent có class trùng tên

Bây giờ **không sửa code, không sửa JAR, không tạo loader mới bằng cách thay bytecode đang chạy**. Hãy **khởi động một JVM mới** nhưng thêm `dist/parent.jar` vào **application classpath**.

**Bash (Linux/macOS):**

~~~bash
java -cp "build/host:dist/parent.jar" \
  demo.host.JarOrderDemo dist/a.jar dist/b.jar
~~~

**Windows PowerShell:**

~~~powershell
java -cp "build/host;dist/parent.jar" demo.host.JarOrderDemo dist/a.jar dist/b.jar
~~~

**Stdout chính xác với cấu hình parent-first mặc định:**

~~~text
AB class: PARENT
BA class: PARENT
AB resource: PARENT
BA resource: PARENT
AB and BA same Class: true
AB defined by child: false
BA defined by child: false
AB load again same Class: true
Parent can load duplicate: true
~~~

**Vì sao cả AB và BA đều nhận PARENT?**

- Khi `ab` và `ba` nhận `loadClass("demo.dupe.Message")`, chúng hỏi parent trước.
- Parent thấy `demo.dupe.Message` từ `dist/parent.jar` trên application classpath và trả class do nó định nghĩa.
- Hai child đều nhận **cùng đối tượng `Class<?>`** của parent; thứ tự A/B bên trong child trở nên không còn tác dụng đối với class name đó.
- Với `label.txt`, parent-first resource lookup cũng tìm thấy resource của parent trước; `getResourceAsStream` trả nội dung `PARENT`.

Điểm thực tế: thay thứ tự URL của child **không đủ** để giải quyết “JAR của host che JAR của plugin”. Nếu thư viện đã có ở parent và loader dùng parent-first, phiên bản parent thường được ưu tiên.

## 8. Điều gì xảy ra khi hai bản class có API không tương thích?

Thí nghiệm A/B chỉ thay đổi chuỗi trả về, nên việc chọn bản nào vẫn chạy được. Trong ứng dụng thật, hai JAR có thể chứa các phiên bản API khác nhau:

~~~java
// Bản v1 (mã minh họa, không phải file trong demo):
public final class Client {
    public static String build(String text) {
        return text;
    }
}

// Bản v2 (mã minh họa):
public final class Client {
    public static String build(String text, int count) {
        return text.repeat(count);
    }
}
~~~

Giả sử caller **được biên dịch với v2** và gọi `Client.build("x", 2)`. Nếu loader runtime lại chọn class v1 (không có method descriptor tương ứng), lần thực thi lời gọi có thể phát sinh `NoSuchMethodError`. Đây là vấn đề **binary compatibility** và quyết định class được nạp, không chỉ là “có file Class trên classpath”.

Các lỗi thường gặp liên quan:

| Triệu chứng | Khả năng thường gặp |
|---|---|
| `NoSuchMethodError` | Bytecode caller và phiên bản class được chọn không có cùng method descriptor |
| `NoSuchFieldError` | Phiên bản class runtime thiếu field mà caller đã biên dịch để truy cập |
| `ClassCastException` | Hai loader đã định nghĩa hai class cùng tên hoặc class khác không phù hợp kiểu đích |
| `LinkageError` | Có xung đột type/loading constraints hoặc không tương thích nhị phân |
| `NoClassDefFoundError` | Class đã được yêu cầu liên kết/khởi tạo nhưng dependency không sẵn có ở runtime hoặc lỗi khởi tạo đã được ghi nhận |
| `ServiceConfigurationError` | Provider sai cấu hình hoặc không tương thích với service type mà host yêu cầu |
| Hành vi khác nhau giữa môi trường | Thứ tự `-cp`/URL, wildcard, dependency transitive hoặc parent loader khác nhau |

**Bước chẩn đoán:** xác định **class thực sự được JVM dùng**, không đoán từ JAR “đã đặt vào thư mục lib”. Kiểm tra classloader topology, classpath/module path và nguồn định nghĩa của class. `Class.getProtectionDomain().getCodeSource()` thường hữu ích khi có CodeSource, nhưng có thể trả `null` và không nên coi là chỉ báo bắt buộc trong mọi môi trường.

## 9. Resource trùng tên và `getResources`

Đối với `URLClassLoader` thông thường:

- `getResource(name)` trả URL của resource **đầu tiên** mà loader tìm được theo chuỗi tra cứu parent-first rồi tới URL path của chính loader.
- `getResources(name)` có thể trả **nhiều URL** (ví dụ cả A lẫn B), thường theo thứ tự tài nguyên ở parent rồi tài nguyên loader con.
- `getResourceAsStream(name)` mở một resource đầu tiên tương ứng; ứng dụng phải đóng stream.
- Với tệp cấu hình ghép từ nhiều JAR, ví dụ `META-INF/services/com.example.Service`, `ServiceLoader` có thể đọc **nhiều** resource descriptor, không đơn giản lấy một file đầu tiên rồi bỏ qua toàn bộ còn lại.

Trong ứng dụng thực tế, thứ tự resource cũng có thể ảnh hưởng tệp cấu hình `log4j2.xml`, `application.properties` hoặc các resource mặc định. Tuy nhiên mỗi framework có quy tắc nạp/gộp cấu hình riêng; **không** mặc định mọi framework dùng đúng logic `getResource` của Java rồi chọn file đầu tiên.

Ví dụ nếu muốn kiểm tra tất cả `label.txt` từ loader AB, dùng đoạn minh họa này bên trong method đã có `ab`:

~~~java
// Mã minh họa, cần ab và import java.util.Enumeration.
java.util.Enumeration<URL> urls = ab.getResources("label.txt");
while (urls.hasMoreElements()) {
    System.out.println(urls.nextElement());
}
~~~

Nội dung URL phụ thuộc đường dẫn tuyệt đối nên ví dụ này **không cam kết stdout cụ thể**. Khi ứng dụng cần liệt kê resource trùng, đây là thao tác phù hợp hơn `getResource`. Với resource trong **named modules**, luật encapsulation/`opens` có thể ảnh hưởng khả năng hiển thị.

## 10. Phân biệt ba cấp ưu tiên

| Cấp | Câu hỏi | Ví dụ |
|---|---|---|
| **ClassLoader topology** | Child có hỏi parent trước không? | Parent có `Message` nên A/B trong child đều bị che |
| **URL/classpath order của một loader** | Khi parent không có, nguồn nào được loader xét trước? | `[a.jar, b.jar]` chọn A; `[b.jar, a.jar]` chọn B |
| **Runtime class identity và cache** | Loader đã định nghĩa/từng nhận class nào cho binary name đó? | Yêu cầu `ab.loadClass` lần hai vẫn là class A; loader khác có thể là class B |

Cần tách hai khái niệm thường bị gộp:

**“First found”** là quy tắc tìm kiếm *bên trong một lookup path* tại thời điểm cần tìm class/resource.

**“First loaded/defined”** nói tới trạng thái một loader đối với một class. Sau khi loader đã định nghĩa class, JVM không cho cùng loader tùy ý định nghĩa lại cùng binary name như một bản thay thế; cố định nghĩa lại có thể gây `LinkageError`. Đổi thứ tự URL của loader đang chạy không biến class A đã định nghĩa thành class B.

Nếu muốn thay một version plugin trong cùng JVM, thường tạo **loader mới**, ngừng dùng object/loader cũ, giải phóng references và tài nguyên rồi tạo instance mới. Không có cơ chế đổi class object cũ chỉ bằng sửa JAR trên đĩa.

## 11. Một số tình huống đặc biệt

### 11.1. Wildcard classpath

~~~bash
java -cp "lib/*" demo.app.Main
~~~

Launcher mở rộng `*` thành các JAR phù hợp trong thư mục. **Thứ tự mở rộng không được bảo đảm** theo Javadoc/launcher specification; không cố ý đặt hai bản cùng FQCN vào `lib/` rồi kỳ vọng file `a.jar` thắng `b.jar` vì tên đứng trước. Dùng dependency graph rõ ràng và loại bỏ bản dư thừa.

### 11.2. Multi-release JAR

Một **multi-release JAR** có thể chứa các phiên bản class dành cho những Java release khác nhau dưới `META-INF/versions/...`. Việc chọn bản class khi đọc JAR còn phụ thuộc phiên bản runtime/quy tắc multi-release; nó **khác** với cạnh tranh giữa hai JAR có cùng FQCN.

### 11.3. Named modules và split packages

Khi chạy với `--module-path`, JPMS giải quyết các module theo quan hệ phụ thuộc và luật package/module. Hai module cùng cung cấp một package cho cùng module consumer có thể làm configuration/resolution hoặc compilation thất bại; không có phép tổng quát “module path đứng trước thì class trùng tên thắng”.

### 11.4. Child-first trong plugin containers

Một số application server triển khai child-first cho thư viện của từng ứng dụng để tránh xung đột với host. Khi ấy plugin có thể chủ động chọn bản class trong JAR con trước parent, **nhưng** rất dễ tạo hai runtime interface/DTO cùng tên nếu không “parent-first” riêng cho API dùng chung. Khả năng cô lập dependency cần đi kèm ranh giới kiểu rõ ràng.

### 11.5. Thứ tự compile-time khác runtime

`javac` có thể chọn API từ classpath lúc biên dịch, nhưng `java` khi chạy lại thấy JAR khác do thứ tự dependency khác. Compile thành công **không chứng minh** class runtime có cùng method/field signature. Bộ quản lý dependency (Gradle/Maven) nên khóa/kiểm tra phiên bản và loại bỏ dependency trùng ngoài ý muốn.

## 12. Cách kiểm tra nhanh trên dự án thật

1. **Liệt kê entry chứa class trùng**: kiểm tra JAR với `jar --list --file path/to/library.jar` hoặc công cụ phân tích dependency. Lệnh `jar --list` chỉ chứng minh **file có class**, không chứng minh JVM đã dùng bản ấy.
2. **Ghi lại runtime ClassLoader**: gọi `SomeClass.class.getClassLoader()`; so sánh identity loader bằng `==`, không phụ thuộc chuỗi `toString()`.
3. **Xác định nguồn bytecode**: nếu có, xem `SomeClass.class.getProtectionDomain().getCodeSource()`; có thể là `null` đối với một số loader/class.
4. **Kiểm tra launch config**: `java -cp`, IDE classpath, test runtime classpath, `java.system.class.loader`, module path, agent và plugin loader.
5. **Kiểm tra thứ tự có ý nghĩa hay không**: nếu parent đã có class, đảo thứ tự child URL thường không đổi kết quả; nếu không có parent, URL order có thể quyết định kết quả.
6. **Đối chiếu dependency version**: nếu thấy `NoSuchMethodError`, kiểm tra class đã thực sự được nạp có signature mong đợi bằng `javap` hoặc công cụ tương đương; đừng chỉ thêm JAR ngẫu nhiên.
7. **Tách API chung khỏi implementation**: host/plugin giao tiếp bằng kiểu do parent chung định nghĩa để tránh cast lỗi giữa nhiều loader.

## 13. Lỗi thường gặp và cách sửa

| Sai lầm | Vì sao sai | Cách sửa |
|---|---|---|
| “Đặt JAR mới cuối `-cp` sẽ được ưu tiên” | Trong cùng classpath thông thường, entry đứng trước thường được tìm trước | Làm rõ thứ tự và loại bản trùng |
| “Đảo thứ tự child URL chắc chắn đổi class” | Parent có thể đã nạp hoặc tìm được class | Kiểm tra parent và nơi class được định nghĩa |
| “Một JVM không thể có hai class cùng FQCN” | Hai defining loader khác nhau có thể tạo hai runtime type khác nhau | So sánh `Class<?>` và `getClassLoader()` |
| “URLClassLoader luôn chọn JAR theo tên alphabet” | Thứ tự đến từ mảng URL, không phải phép sort tên file | Truyền thứ tự URL tường minh |
| “Wildcards `lib/*` có thứ tự ổn định” | Quy tắc mở rộng wildcard không bảo đảm thứ tự | Loại bỏ class trùng, quản lý classpath có kiểm soát |
| “Class có trong một JAR nên chắc chắn JVM đã nạp nó” | Có thể bị che bởi class từ JAR/parent khác | Kiểm tra defining loader và CodeSource nếu có |
| “Classpath Java 17 và module path giống nhau” | JPMS có luật module/package, visibility và resolution khác | Phân biệt classpath và `--module-path` |
| “Thay JAR trên đĩa sẽ cập nhật class đã tải” | Class object/bytecode đã được loader định nghĩa không tự thay | Tạo loader mới hoặc khởi động lại tiến trình |

## 14. Tự kiểm tra

**Câu 1.** Parent không có `demo.dupe.Message`. Một loader con có URL theo thứ tự `[a.jar, b.jar]`, mỗi JAR chứa cùng FQCN. Theo `URLClassLoader` chuẩn trong ví dụ, implementation nào được chọn?

**Đáp án:** Bản trong `a.jar`, vì đây là URL đầu tiên có class được yêu cầu.

**Câu 2.** Hai loader con độc lập lần lượt nhận `[a.jar, b.jar]` và `[b.jar, a.jar]`. Hai kết quả `Class<?>` có bằng nhau bằng `==` không?

**Đáp án:** Không, khi mỗi loader tự định nghĩa class; bản sắc runtime là binary name + defining loader.

**Câu 3.** Parent có class cùng tên. Nếu đảo URL A/B ở child mà không thay parent, kết quả thường thế nào?

**Đáp án:** Cả hai child đều nhận class do parent định nghĩa (parent-first); đổi thứ tự URL bên trong child không đủ đổi class.

**Câu 4.** `child.getResources("label.txt")` có thể thấy mấy resource khi hai JAR cùng chứa file đó?

**Đáp án:** Nó có thể liệt kê **hai URL** của A và B (và thêm resource từ parent nếu có). `getResource` chỉ trả một kết quả đầu tiên.

**Câu 5.** Vì sao mã biên dịch đúng nhưng gặp `NoSuchMethodError` khi chạy?

**Đáp án:** Caller có thể được biên dịch với class ở JAR v2, trong khi loader runtime chọn class ở JAR v1 không còn method descriptor cần gọi.

**Câu 6.** Có nên dùng `-cp "lib/*"` để bảo đảm phiên bản thư viện được chọn theo tên JAR không?

**Đáp án:** Không. Thứ tự mở rộng wildcard không được bảo đảm. Tốt nhất xử lý dependency trùng và đảm bảo version rõ ràng.

## 15. Nguồn tham khảo

- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `loadClass`, class identity và tài nguyên.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): đường tìm kiếm URL và cách đóng loader.
- [Java SE 17 — Java launcher](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `--class-path`, dấu phân cách và wildcard expansion.
- [Java Virtual Machine Specification 17 — Loading, Linking and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): khái niệm defining loader và loading constraints.
- [JAR File Specification](https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html): thuộc tính manifest `Class-Path` và cấu trúc JAR.
- [Java SE 17 — Class#getProtectionDomain](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getProtectionDomain()): thông tin domain/source khi khả dụng.

**Điểm cần nhớ:** Quyết định JAR nào “thắng” phải xét **parent delegation trước**, **thứ tự tìm class trong loader cụ thể sau**, và **class đã được định nghĩa/cached**. Nếu có nhiều ClassLoader, hai class cùng FQCN vẫn có thể cùng tồn tại như hai runtime type khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-and-module-path-005">Wildcard trong classpath và giới hạn tìm kiếm</a>

<details>
<summary>Xem chi tiết</summary>

# Wildcard trong classpath và giới hạn tìm kiếm

> **Chủ đề:** Java Core → ClassLoader → Đường dẫn tìm kiếm. Bài học làm rõ cú pháp `*` trong **application classpath**, đối tượng được tìm kiếm, thời điểm mở rộng wildcard, giới hạn thư mục/JAR, thứ tự nạp lớp và khác biệt với module path, manifest và custom class loaders. Ví dụ chạy trên Java 17+.

## 1. Vấn đề thực tế: vì sao có lib/* nhưng vẫn ClassNotFoundException?

Giả sử ứng dụng có thư mục:

```text
project/
├── app-classes/
│   └── demo/App.class
└── libs/
    ├── core.jar
    ├── helper.JAR
    ├── tools.zip
    ├── loose/Loose.class
    └── nested/
        └── plugin.jar
```

Một lệnh quen thuộc:

```bash
java -cp "app-classes:libs/*" demo.App
```

Theo hợp đồng của Java launcher, phần tử `libs/*` được mở rộng thành **các file có đuôi `.jar` hoặc `.JAR` nằm trực tiếp trong `libs`**. Nó không có nghĩa “tìm mọi file và mọi thư mục con”.

Trong sơ đồ:

| Đối tượng | `libs/*` có đưa vào classpath trực tiếp? | Vì sao |
| --- | --- | --- |
| `libs/core.jar` | Có | JAR ngay trong thư mục được chỉ định. |
| `libs/helper.JAR` | Có | Hỗ trợ đuôi `.JAR`. |
| `libs/tools.zip` | Không | Wildcard chỉ liệt kê JAR; vẫn có thể đưa ZIP vào classpath bằng đường dẫn tường minh nếu phù hợp. |
| `libs/loose/Loose.class` | Không | `*` không tự bổ sung thư mục chứa loose class files. |
| `libs/nested/plugin.jar` | Không | Không tìm JAR trong thư mục con theo đệ quy. |
| `app-classes/demo/App.class` | Có, do `app-classes` | Đây là một phần tử classpath riêng, không phải kết quả của `libs/*`. |

Vì vậy, nếu `demo.App` cố `Class.forName("plugin.Main")` nhưng class đó chỉ nằm trong `libs/nested/plugin.jar`, lệnh trên **không tự tìm thấy** nó. Tương tự, `loose.Loose` dưới `libs/loose/Loose.class` cần bổ sung **`libs`** như một *directory classpath root*.

## 2. Classpath là danh sách root tìm kiếm, không phải phép quét mọi class

Classpath (`-cp`, `-classpath` hoặc `--class-path`) gồm các phần tử chỉ đến:

- **Thư mục gốc** chứa cấu trúc package; ví dụ `classes` chứa `classes/com/acme/Thing.class`, tên nhị phân là `com.acme.Thing`.
- **JAR file** lưu class/resource theo entry name tương ứng; ví dụ `lib/tool.jar` chứa `com/acme/Thing.class`.
- **ZIP archive** khi được khai báo tường minh làm classpath element; bản thân dấu `*` không liệt kê ZIP.

Java launcher dùng các phần tử này để thiết lập môi trường tìm class/resource của ứng dụng. Nó **không** chạy trước một tác vụ “nạp tất cả class từ mọi JAR”; loader thường tìm và định nghĩa class **khi chương trình cần**. Có một path tới JAR không bảo đảm mọi class trong JAR sẽ được nạp, liên kết, khởi tạo hoặc truy cập được.

Chuyển tên nhị phân thành đường dẫn resource:

```text
Binary name:      com.acme.Thing
Class entry:      com/acme/Thing.class

Root:             classes
Full path:        classes/com/acme/Thing.class

Root:             lib/tool.jar
JAR entry:        com/acme/Thing.class
```

Đừng đặt `classes/com/acme` vào classpath rồi mong loader tìm `com.acme.Thing`: root đúng phải là `classes`. Việc tìm kiếm tài nguyên (`getResource`, `getResources`) có quy tắc riêng và thường dùng đường dẫn dấu `/`; không nhập nhằng resource path với binary name.

## 3. Cú pháp wildcard chính xác

Đối với Java launcher:

- `libs/*`: mọi file JAR trực tiếp trong `libs` có đuôi `.jar` hoặc `.JAR`.
- `*`: mọi JAR trực tiếp trong **working directory hiện tại** (nếu đường dẫn classpath phần tử chỉ là `*`).
- `classes:libs/*` (Linux/macOS), hoặc `classes;libs/*` (Windows): kết hợp một thư mục classes với JAR wildcard.
- `libs/*:libs` hoặc `libs;libs/*`: kết hợp cả JARs và loose classes ở thư mục `libs`; thứ tự hai phần tử có thể ảnh hưởng khi class/resource trùng tên.

Đặc tả **không** cho phép hiểu `*` như glob tổng quát. Những cách dùng không có semantics “liệt kê tự động” tương ứng của classpath wildcard:

| Cú pháp muốn dùng | Sai kỳ vọng | Thay bằng |
| --- | --- | --- |
| `libs/foo*.jar` | Match theo prefix `foo` | Liệt kê file JAR cụ thể, hoặc quản lý dependency bằng công cụ build. |
| `libs/**/*.jar` | Tìm JAR đệ quy | Tạo classpath từ từng thư mục/JAR được phép, hoặc dùng build tool. |
| `libs/*/plugin.jar` | Tìm trong mọi thư mục con | Nêu rõ `libs/nested/plugin.jar` hoặc root classpath khác. |
| `libs/*` | Tìm mọi `.class` rời ở `libs` | Thêm `libs` thành phần tử riêng. |
| `libs/*` | Tìm `.zip` trong `libs` | Thêm đường dẫn ZIP cụ thể. |
| `libs/*` | Tìm thư viện trong các JAR lồng ở bên trong JAR khác | Sử dụng cơ chế loader đóng gói phù hợp; Java launcher thông thường không giải nén nested JAR tự động. |

JAR bắt đầu bằng dấu chấm (*hidden file*) vẫn có thể được liệt kê nếu phù hợp đuôi JAR. Wildcard mở rộng được xử lý **sớm bởi launcher trước khi chạy `main`**, không phải cú pháp động mà `ClassLoader.loadClass` phân tích mỗi lần tìm class.

**Thứ tự JAR trong một wildcard không được bảo đảm.** Ngay cả khi `libs` có `a.jar` và `b.jar`, đừng suy luận cái nào được xét trước chỉ từ tên file. Nếu cả hai JAR chứa `com.acme.Widget` khác phiên bản, kết quả có thể phụ thuộc môi trường hoặc thứ tự mở rộng. Muốn thứ tự ưu tiên xác định, hãy liệt kê JAR rõ ràng hoặc dùng cơ chế dependency resolution có kiểm soát.

## 4. Ký tự phân cách khác nhau giữa Linux/macOS và Windows

Một phần tử wildcard giữ nguyên `libs/*` ở hai hệ điều hành; khác biệt quan trọng là **ký tự phân cách các phần tử classpath**:

| Hệ điều hành | Separator | Lệnh minh họa |
| --- | --- | --- |
| Linux/macOS | `:` | `java -cp "classes:libs/*" demo.App` |
| Windows PowerShell/CMD | `;` | `java -cp "classes;libs/*" demo.App` |

Dùng dấu nháy kép quanh **toàn bộ chuỗi classpath**, đặc biệt trong shell Unix: nếu để `*` trần, **shell có thể mở rộng nó trước Java launcher**, khiến dòng lệnh bị tách/diễn giải khác ý định. Ví dụ đáng tin:

```bash
java --class-path "classes:libs/*" demo.App
```

Trên Windows:

```powershell
java --class-path "classes;libs/*" demo.App
```

Đường dẫn tương đối trong classpath được diễn giải dựa vào **working directory khi khởi chạy**, không tự động dựa theo vị trí file `.class` hay thư mục chứa JAR. Nếu mở IDE, service/container hoặc chạy script từ một thư mục khác, `libs/*` có thể trỏ vào chỗ khác. Khi debug, hãy kiểm tra working directory:

```java
System.out.println(System.getProperty("user.dir"));
```

Đây là snippet để đặt trong method; `user.dir` chỉ hỗ trợ chẩn đoán ngữ cảnh đường dẫn, không bảo đảm có thể cập nhật classpath đang chạy bằng việc gán lại property.

## 5. Thời điểm wildcard được mở rộng và ý nghĩa java.class.path

Java launcher mở rộng `libs/*` thành một chuỗi các JAR path cụ thể **trước khi `main` bắt đầu**. Vì vậy, trong chương trình có thể xem:

```java
System.out.println(System.getProperty("java.class.path"));
```

Ví dụ `libs` có `a.jar` và `b.jar` thì có thể xuất hiện một chuỗi tương đương với:

```text
classes:libs/a.jar:libs/b.jar
```

Đây chỉ là **một thứ tự minh họa**, không phải output cố định: thứ tự JAR có thể khác; trên Windows separator là `;`. Quan trọng hơn, giá trị `java.class.path` có thể đã là **danh sách mở rộng**, trong khi `System.getenv("CLASSPATH")` (nếu có) chỉ là chuỗi môi trường ban đầu.

Đừng coi `java.class.path` là một API thay đổi classpath tại runtime:

- `System.setProperty("java.class.path", "...")` chỉ sửa **giá trị property trong process**; nó **không có hợp đồng** yêu cầu application class loader cập nhật các nguồn đang dùng.
- Custom loaders, plugin JARs nạp riêng, manifest dependencies và named modules có thể nằm ngoài chuỗi này.
- Trên JDK 9+, application loader tích hợp mặc định không phải `URLClassLoader`; đừng ép `ClassLoader.getSystemClassLoader()` thành `URLClassLoader` chỉ để gọi `getURLs()`.
- JVM có thể đang chạy trong container/framework với loader delegation không giống môi trường chạy `java` thuần.

Khi cần **thay đổi dependencies**, hãy quyết định ở launch/build time hoặc tạo loader riêng có nguồn rõ ràng, không sửa property rồi chờ lớp mới xuất hiện.

## 6. Ví dụ Java 17+ tái tạo ba giới hạn tìm kiếm

Thí nghiệm dùng một JAR ngay trong thư mục `libs`, một JAR nằm trong thư mục con và một file `.class` rời. Chương trình kiểm tra bằng `Class.forName`, không dùng thư viện ngoài JDK.

### Cấu trúc source

Tạo các file:

```text
src/demo/PathDemo.java
src/greet/example/Greeter.java
src/extra/deep/Extra.java
src/loose/loose/Loose.java
```

**File `src/demo/PathDemo.java`:**

```java
package demo;

public class PathDemo {
    public static void main(String[] args) {
        for (String name : new String[]{
                "example.Greeter", "deep.Extra", "loose.Loose"}) {
            try {
                Class.forName(name);
                System.out.println(name + ": FOUND");
            } catch (ClassNotFoundException e) {
                System.out.println(name + ": MISSING");
            }
        }
    }
}
```

**File `src/greet/example/Greeter.java`:**

```java
package example;
public class Greeter {}
```

**File `src/extra/deep/Extra.java`:**

```java
package deep;
public class Extra {}
```

**File `src/loose/loose/Loose.java`:**

```java
package loose;
public class Loose {}
```

### Chuẩn bị artifacts và chạy (Linux/macOS)

Đứng tại thư mục chứa `src`. Biên dịch ba class thành ba vị trí khác nhau rồi đóng gói hai JAR:

```bash
mkdir -p libs/nested
javac -d app-classes src/demo/PathDemo.java
javac -d build/greet src/greet/example/Greeter.java
javac -d build/extra src/extra/deep/Extra.java
javac -d libs src/loose/loose/Loose.java
jar --create --file libs/greet.jar -C build/greet .
jar --create --file libs/nested/extra.jar -C build/extra .

java -cp "app-classes:libs/*" demo.PathDemo
```

**Stdout chính xác của lần chạy thứ nhất, đã kiểm chứng trên OpenJDK 21:**

```text
example.Greeter: FOUND
deep.Extra: MISSING
loose.Loose: MISSING
```

Giải thích: `example.Greeter` nằm trong `libs/greet.jar` (JAR ngay trong `libs`), còn `deep.Extra` nằm trong `libs/nested/extra.jar` và `loose.Loose` nằm trong `libs/loose/Loose.class`. Cả hai nguồn sau không được `libs/*` tự động liệt kê.

Chạy lần hai, **nêu thêm** wildcard cho thư mục con và root của loose class:

```bash
java -cp "app-classes:libs/*:libs/nested/*:libs" demo.PathDemo
```

**Stdout chính xác của lần thứ hai:**

```text
example.Greeter: FOUND
deep.Extra: FOUND
loose.Loose: FOUND
```

Mã nguồn không thay đổi; **chỉ cấu hình đường tìm class khác đi**. Đây là bằng chứng rằng mỗi phần tử classpath tạo một root tìm kiếm và `*` không quét đệ quy.

### Chạy tương đương trên Windows PowerShell

Sau khi tạo cùng cấu trúc source, chuẩn bị `libs\nested` (nếu chưa có) và chạy các lệnh `javac`/`jar` như trên; PowerShell cũng chấp nhận dấu `/` cho tên file trong lệnh Java. Các lệnh chạy ứng dụng là:

```powershell
java -cp "app-classes;libs/*" demo.PathDemo
java -cp "app-classes;libs/*;libs/nested/*;libs" demo.PathDemo
```

Kết quả `FOUND/MISSING` giống hai lần chạy trên. Không đổi dấu `:` thành `;` ở bên trong package name, binary class name hoặc resource path; separator này **chỉ dùng giữa các classpath elements**.

## 7. Thứ tự classpath, class trùng tên và resource trùng tên

Classpath thường được tổ chức thành **một danh sách có thứ tự**. Khi nguồn tương đương chứa cùng tên class, thứ tự đường đi và delegation ảnh hưởng class nào được tìm thấy:

```text
-cp "app-classes:lib/new.jar:lib/old.jar"

- app-classes trước
- new.jar sau
- old.jar cuối
```

Ở ví dụ minh họa này, nếu `new.jar` và `old.jar` cùng chứa `com/acme/Client.class` thì đường đi classpath của loader mặc định thường ưu tiên entry ở phía trước **nếu delegation tới parent không nạp class đó trước**. Nhưng nếu dùng `lib/*` thay cho hai tên file tường minh, **thứ tự giữa JAR trong wildcard là không xác định theo contract**.

Với các class có cùng binary name, định danh runtime còn phụ thuộc **defining class loader**. Nhiều loader khác nhau có thể nạp hai class cùng tên và tạo hai kiểu không tương thích; wildcard classpath không hợp nhất class được loader riêng định nghĩa.

Resources cũng có rủi ro trùng tên:

```java
ClassLoader loader = Thread.currentThread().getContextClassLoader();
if (loader != null) {
    var resources = loader.getResources("config.properties");
    while (resources.hasMoreElements()) {
        System.out.println(resources.nextElement());
    }
}
```

Đoạn này là snippet trong method có `throws IOException` hoặc xử lý exception. `getResources` có thể liệt kê nhiều URL nếu tài nguyên trùng tên nằm trong các nguồn loader tìm thấy; thứ tự và tập hợp phụ thuộc loader/module và thứ tự classpath thực tế. Nếu chỉ dùng `getResource`, thường lấy một tài nguyên được tìm trước theo quy tắc loader, nên **có thể lấy nhầm bản cấu hình** khi nhiều JAR đóng cùng file `config.properties`.

Đừng giải quyết collision chỉ bằng cách đổi tên file JAR theo thứ tự alphabet rồi dựa vào wildcard; tốt hơn là loại dependency trùng, đặt resource ở namespace riêng, hoặc cấu hình thứ tự classpath rõ ràng.

## 8. CLASSPATH, -cp, java -jar và JAR Manifest

### CLASSPATH environment variable

Nếu không dùng `-cp`/`-classpath`/`--class-path`, Java launcher có thể lấy classpath từ biến môi trường `CLASSPATH`. Classpath wildcard được hỗ trợ trong biến này tương tự khi truyền tùy chọn `-cp`.

Tuy vậy, **khi có tùy chọn `-cp` tường minh, nó ghi đè `CLASSPATH`**, không tự nối vào biến môi trường. Với ứng dụng/CI, cấu hình `-cp` tường minh hoặc dùng build tool thường rõ ràng hơn là phụ thuộc biến môi trường toàn máy.

### Khi dùng java -jar

`java -jar app.jar` khởi động một executable JAR theo `Main-Class` của manifest. Theo tài liệu launcher, trong chế độ này **những classpath setting khác bị bỏ qua**. Ví dụ:

```bash
java -cp "libs/*" -jar app.jar
```

Không bảo đảm nạp `libs/*` cho ứng dụng; cấu hình `-cp` không có tác dụng theo cách người viết lệnh dễ kỳ vọng. Nếu muốn tự kiểm soát classpath, dùng dạng:

```bash
java -cp "app.jar:libs/*" demo.Main
```

Với điều kiện `demo.Main` là tên nhị phân của main class, đây là lệnh Linux/macOS. Trên Windows dùng `"app.jar;libs/*"`. Đây là mẫu cấu hình, **không phải một chương trình độc lập**; tên JAR và main class phải được thay bằng artifact thật.

Một JAR executable cũng có thể khai báo dependencies bằng **`Class-Path` trong manifest** hoặc dùng launcher/loader riêng của framework. Các cơ chế ấy khác Java classpath wildcard.

### Class-Path trong JAR manifest

Ví dụ manifest:

```text
Manifest-Version: 1.0
Main-Class: demo.Main
Class-Path: lib/a.jar lib/b.jar
```

Header này liệt kê các URL tương đối so với JAR chứa manifest. **Không hỗ trợ `*` wildcard** như trên command line: `Class-Path: lib/*` không có nghĩa load mọi JAR trong `lib`. Cần ghi từng JAR hợp lệ hoặc dùng công cụ đóng gói/launcher hỗ trợ mô hình dependencies của ứng dụng.

Các đường manifest còn chịu quy tắc chuẩn hóa, giới hạn URL/path và truy cập của JAR Specification; không tự suy từ chúng thành phép duyệt file system không giới hạn.

## 9. Classpath wildcard khác module path

Từ Java 9, JPMS thêm **module path**, được chỉ định bởi `--module-path` hoặc `-p`:

```bash
java --module-path mods --module com.acme.app/com.acme.Main
```

Classpath và module path giải quyết hai phạm vi khác nhau:

| Nội dung | Classpath | Module path |
| --- | --- | --- |
| Tham số | `-cp`, `-classpath`, `--class-path` | `--module-path` hoặc `-p` |
| Khả năng liệt kê JAR ngay thư mục với `libs/*` | Có, là wildcard của classpath | **Không nên áp dụng cú pháp wildcard classpath** để đoán cách tìm module. |
| Thư mục chứa nhiều artifacts | Với classpath, phải liệt kê JAR hoặc `libs/*`; bản thân `libs` là root class files | Với module path, một thư mục **chứa modules** có thể là một phần tử module path. |
| Cách module được dùng | Classpath classes thuộc unnamed module của loader phù hợp | Named modules phải được phát hiện, giải quyết (`resolution`) và có readability/exports phù hợp. |
| Tìm kiếm | Theo classpath entries và loader behavior | Theo module finder/graph, module layers và class loaders liên quan. |

Thường chỉ cần `--module-path mods` nếu `mods` là thư mục chứa các modular JAR/exploded modules; không viết `mods/*` theo thói quen classpath. Ngoài đường dẫn tồn tại, named module còn phải được **resolved** vào configuration; module chỉ nằm trong thư mục không có nghĩa mọi class của nó tự nạp hoặc mọi package được export.

Classpath wildcard không bypass `module-info.java`, `exports`, `opens`, module readability hoặc kiểm tra truy cập. Nếu thấy `ClassNotFoundException` hoặc `IllegalAccessError` với named modules, hãy phân biệt lỗi đường tìm và lỗi cấu hình/khả năng truy cập module.

## 10. Giới hạn thực sự của phạm vi tìm kiếm do class loader quyết định

Tùy chọn `-cp` thiết lập đường tìm cho application loader của môi trường launcher thông thường; JVM còn có bootstrap/platform loader và có thể có nhiều **custom loaders**.

Mô hình hay gặp:

```text
Code gọi Class.forName / loadClass
             |
             v
          Caller loader
             |
             | parent-first delegation nếu loader dùng chính sách đó
             v
       Parent / platform / bootstrap
             |
             '--- lớp ở ứng dụng / classpath do loader phù hợp quản lý
```

Dù `libs/greet.jar` có được mở rộng vào application classpath, **một plugin loader bị cô lập có thể không nhìn thấy nó** nếu không delegate/không sử dụng nguồn tương ứng. Ngược lại, một custom `URLClassLoader` có thể có các URL/JAR riêng **không xuất hiện** trong `java.class.path`.

Các API quan trọng:

- `ClassLoader.getSystemClassLoader()`: thường là application loader, nhưng kiểu triển khai không được Java SE cam kết; có thể tùy chỉnh.
- `clazz.getClassLoader()`: biết **defining loader** của kiểu `clazz`; trả `null` cho lớp bootstrap như `java.lang.String`.
- `Thread.currentThread().getContextClassLoader()`: context loader, hay được SPI/framework dùng; có thể khác system loader hoặc `null`.
- `Class.forName(name, false, chosenLoader)`: chủ động yêu cầu nạp theo loader chỉ định, **không khởi tạo** do lời gọi này.
- `loader.getResource("com/acme/config.properties")` và `getResources`: truy vấn resources, không bảo đảm tìm mọi nguồn module/JAR ngoài phạm vi loader.

Ngoài tìm được bytes, JVM phải kiểm tra tên, class-file version, linking, dependency, visibility và một số package/module access rules. Class có thể nằm trong JAR **nhưng vẫn không sử dụng được** vì dependency transitively bị thiếu, API bị đổi, conflict loader hoặc module access.

Một launcher/framework đóng gói JAR lồng trong một executable JAR có thể tự triển khai loader để đọc chúng; **Java launcher classpath wildcard thông thường không tự mở JAR nằm bên trong một JAR khác**.

## 11. Các lỗi thường gặp và cách kiểm tra

| Triệu chứng | Nguyên nhân hay gặp | Cách xử lý |
| --- | --- | --- |
| `ClassNotFoundException: plugin.Main` | `plugin.jar` ở `libs/nested`; `libs/*` không quét đệ quy. | Thêm `libs/nested/*` hoặc path tới JAR đó. |
| `ClassNotFoundException: loose.Loose` | Chỉ dùng `libs/*`; file `libs/loose/Loose.class` không nằm trong JAR. | Thêm classpath root `libs`. |
| `Could not find or load main class demo.App` | Không đưa root chứa `demo/App.class` vào classpath hoặc launch từ working directory khác. | Thêm `app-classes` và kiểm tra package path. |
| Chạy trên Linux đúng, Windows lỗi | Dùng `:` thay vì `;` giữa các classpath elements. | Dùng separator của OS, đặt classpath trong dấu nháy. |
| Chạy khi gọi trực tiếp đúng, script shell lỗi | Shell mở rộng `*` hoặc quote classpath sai. | Quote toàn bộ `-cp` value, kiểm tra câu lệnh thực thi. |
| `NoClassDefFoundError` khi code tham chiếu lớp | Thiếu dependency transitively, lỗi initialization hoặc binary compatibility. | Đọc `Caused by`; kiểm tra runtime JAR và loader, không chỉ main JAR. |
| Resource bị nạp từ JAR khác | Có nhiều entry resource trùng, wildcard JAR enumeration order bất định. | Sửa xung đột / liệt kê classpath xác định. |
| `java -jar` không nhận JAR khai báo ở `-cp` | Classpath settings khác bị bỏ qua khi dùng `-jar`. | Dùng manifest `Class-Path`, launcher đúng, hoặc chạy bằng tên main class với `-cp`. |
| Manifest có `Class-Path: lib/*` nhưng dependency thiếu | Header không có wildcard expansion của launcher. | Liệt kê URL JAR cụ thể. |
| Thêm JAR sau startup nhưng loader không thấy | Wildcard đã mở rộng trước khi chạy `main`, loader có nguồn tìm cố định. | Restart với classpath mới hoặc thiết kế loader động riêng. |
| `ClassCastException` giữa hai kiểu có cùng tên | Hai defining class loaders khác nhau. | Kiểm tra `clazz.getClassLoader()`, chia sẻ API qua parent phù hợp. |
| JAR có class nhưng module vẫn báo access error | Visibility/readability/exports/opens của JPMS. | Kiểm tra module configuration thay vì mở rộng classpath ngẫu nhiên. |

### Quy trình debug có thể lặp lại

1. In **lệnh Java thật** trong script/container/IDE, version `java -version`, working directory, các giá trị `-cp` và `CLASSPATH`.
2. Quan sát `System.getProperty("java.class.path")` để thấy các classpath entries đã được khởi động; nhớ rằng nó không mô tả mọi module/custom loader.
3. Kiểm tra nội dung JAR bằng `jar --list --file libs/greet.jar` (hoặc `jar tf libs/greet.jar`). Tìm đúng entry `example/Greeter.class`, phân biệt archive root với package path.
4. Kiểm tra xem JAR nằm **ngay trong** thư mục wildcard hay trong subdirectory; phân biệt `.jar/.JAR` với `.zip` và loose class.
5. Nếu có duplicate classes/resources, thử **liệt kê từng JAR theo thứ tự tường minh**; xử lý conflict qua dependency graph thay vì dựa vào wildcard.
6. Nếu vẫn lỗi, so sánh loader của caller và kiểu đang cần, parent delegation và cấu hình module; đừng cho rằng JAR thấy được ở OS sẽ tự thấy được qua mọi loader.
7. Với HotSpot, có thể dùng `-Xlog:class+load=info` để chẩn đoán class loading, nhưng log **không bảo đảm** cung cấp toàn bộ quá trình resource resolution hay mọi đường tìm không thành công.

Ví dụ lệnh chẩn đoán (Linux/macOS; thay tên class thực):

```bash
jar tf libs/greet.jar
java -Xlog:class+load=info -cp "app-classes:libs/*" demo.PathDemo
```

`-Xlog` in thêm nhiều dòng JVM, vì vậy **không dùng output của lệnh này làm đầu ra chính xác trong thí nghiệm mục 6**.

## 12. Tự kiểm tra

1. **`libs/*` có nạp `libs/sub/helper.jar` không?** Không, wildcard classpath không duyệt đệ quy.
2. **`libs/*` có bao gồm `libs/Thing.class` không?** Không, `*` dùng để liệt kê các file JAR; muốn loose class thì thêm `libs`.
3. **Thứ tự `libs/a.jar` và `libs/b.jar` trong mở rộng wildcard có cố định không?** Không được đặc tả; liệt kê JAR tường minh nếu thứ tự quan trọng.
4. **Tại sao `System.getProperty("java.class.path")` có thể không còn chứa `*`?** Launcher mở rộng wildcard trước khi `main` chạy.
5. **`java -cp "libs/*" -jar app.jar` có bảo đảm nạp JAR từ `libs` không?** Không. Chế độ `-jar` bỏ qua classpath settings khác.
6. **Có thể viết `Class-Path: libs/*` trong manifest để liệt kê JAR không?** Không; manifest `Class-Path` không dùng launcher wildcard.
7. **`--module-path mods` khác `-cp "mods/*"` ở điểm nào?** Một bên cấu hình finder/graph cho named modules, bên kia mở rộng các JAR thành classpath entries cho các lớp ứng dụng phù hợp.
8. **Tại sao chương trình có JAR đúng mà vẫn `ClassNotFoundException`?** Có thể sai working directory, binary name, loader, module visibility hoặc dependency; phải xem đường đi thực và code triggering.
9. **Nếu thay `java.class.path` bằng `System.setProperty` rồi chạy lại `Class.forName`, JVM phải nhìn thấy JAR mới?** Không có hợp đồng như vậy.
10. **Hai plugin loaders cùng nhìn thấy một file JAR có chắc chắn chia sẻ cùng class identity?** Không. Nếu chúng tự define cùng binary name riêng, đó là hai kiểu runtime khác nhau.

## 13. Tài liệu chuẩn

- [Java SE 21 — `java` launcher](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): `--class-path`, `-jar`, wildcard classpath và module path.
- [Oracle — Understanding class path wildcards](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/classpath.html): wildcard chỉ tìm JAR, không đệ quy, thứ tự không bảo đảm, hành vi `CLASSPATH` và manifest.
- [Java SE 21 — `javac`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html): `-cp`, `--class-path`, source/class output roots.
- [Java SE 21 — JAR File Specification](https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html): `Class-Path` trong manifest và URL tương đối.
- [Java SE 21 — `ClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `getResource`, `getResources`, parent delegation.
- [Java SE 21 — Module System / `ModuleLayer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html): module configuration và class loaders của named modules.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-and-module-path-006">JAR, manifest Main-Class và Class-Path</a>

<details>
<summary>Xem chi tiết</summary>

# JAR, manifest Main-Class và Class-Path trong cơ chế tìm lớp Java

Một tệp **JAR (Java ARchive)** đóng gói các tệp `.class`, tài nguyên và metadata vào một archive dạng ZIP. Với một **executable JAR** thông thường, JVM launcher sử dụng tệp `META-INF/MANIFEST.MF` trong JAR để xác định **lớp khởi động** qua thuộc tính `Main-Class`. Thuộc tính `Class-Path` trong cùng manifest có thể chỉ ra **những JAR/thư mục phụ thuộc** để class loader mở rộng đường tìm kiếm.

Ví dụ:

```text
app/
├── app.jar
└── lib/
    ├── helper.jar
    └── parser.jar
```

Manifest bên trong `app.jar`:

```text
Manifest-Version: 1.0
Main-Class: demo.Main
Class-Path: lib/helper.jar lib/parser.jar

```

Sau khi đóng gói đúng, lệnh `java -jar app.jar` tìm `demo.Main`, gọi `public static void main(String[] args)`, và có thể tìm lớp phụ thuộc trong `lib/helper.jar`, `lib/parser.jar`. Các mục `Class-Path` được hiểu **tương đối với vị trí của JAR chứa manifest**, không đơn thuần tương đối với working directory của terminal.

Đây là ba thành phần riêng biệt:

- **JAR**: lưu bytecode và tài nguyên theo tên entry như `demo/Main.class`.
- **`Main-Class`**: chỉ định lớp có điểm vào của ứng dụng được `java -jar` sử dụng.
- **`Class-Path`**: bổ sung các đường dẫn tìm class/resource khi loader xử lý JAR chứa manifest.

## 1. JAR chứa những gì?

JAR thường được tạo bằng công cụ `jar` của JDK. Nó là một ZIP archive với quy ước metadata dành cho Java.

Ví dụ cấu trúc bên trong:

```text
app.jar
├── META-INF/
│   └── MANIFEST.MF
├── demo/
│   ├── Main.class
│   └── Config.class
└── application.properties
```

Tệp `demo/Main.class` là **JAR entry** tương ứng với binary name `demo.Main`. Tài nguyên `application.properties` nằm tại gốc JAR có thể được tìm bằng các API resource khi JAR nằm trong phạm vi lookup phù hợp.

Không được dùng `Main-Class: demo/Main.class` hoặc `Main-Class: demo.Main.class`. `Main-Class` nhận **tên lớp**, ví dụ `demo.Main`, chứ không nhận đường dẫn tài nguyên `.class`.

Đối với classloader, đường đi có thể hiểu là:

```text
java -jar app.jar
        |
        v
Đọc META-INF/MANIFEST.MF
        |
        +--> Main-Class: demo.Main
        |          |
        |          v
        |     tìm demo/Main.class trong app.jar
        |
        +--> Class-Path: lib/helper.jar
                   |
                   v
              thêm nguồn tìm lớp/tài nguyên
                   |
                   v
            gọi demo.Main.main(String[])
```

Đây là mô hình giúp học. Trên Java 9+, runtime có thêm module, bootstrap/platform loaders và các chi tiết JVM/JDK; sơ đồ không có nghĩa toàn bộ lớp nền tảng chỉ được tìm trong các JAR bên ngoài.

## 2. Manifest là gì?

Manifest tiêu chuẩn nằm đúng đường dẫn **`META-INF/MANIFEST.MF`** trong JAR. Nó là một tệp văn bản có cú pháp riêng, gồm **main section** và tùy chọn các section cho từng entry.

Một manifest tối thiểu dùng cho `java -jar`:

```text
Manifest-Version: 1.0
Main-Class: demo.Main

```

- `Manifest-Version` chỉ phiên bản định dạng manifest; nên đặt ở đầu tệp.
- `Main-Class` là tên nhị phân của lớp khởi chạy, **không kèm `.class`**. Class phải nằm trong phạm vi tìm thấy và có điểm vào `main` hợp lệ.
- Dòng trống sau các thuộc tính dùng để kết thúc **main section**. Khi tự tạo manifest, bảo đảm có ký tự xuống dòng kết thúc dòng cuối; dùng công cụ `jar` để hợp nhất và chuẩn hóa manifest nếu có thể.

Ví dụ có thêm dependency:

```text
Manifest-Version: 1.0
Main-Class: demo.Main
Class-Path: lib/helper.jar lib/parser.jar

```

Tên thuộc tính manifest có cú pháp `Name: value`, **có dấu hai chấm và khoảng trắng phân cách**. Tên thuộc tính không phân biệt chữ hoa/chữ thường theo specification, nhưng nên viết theo dạng chuẩn `Main-Class` và `Class-Path` để dễ đọc.

Đối với manifest dài, giới hạn một dòng vật lý là **72 byte khi mã hóa UTF-8**, không phải 72 ký tự. Nội dung dài phải được **gập dòng** và dòng tiếp theo bắt đầu bằng một dấu cách; dấu cách đó là ký hiệu nối dòng, không phải ký tự tùy ý thêm vào đường dẫn. Hãy để `jar` hoặc công cụ manifest chuẩn xử lý việc gập dòng. Không tự chèn newline giữa một đường dẫn rồi kỳ vọng JVM ghép lại theo ý người viết.

**Phân biệt quan trọng:** metadata trong manifest không đồng nghĩa loader tự chạy toàn bộ class. `Main-Class` chọn điểm vào cho launcher; những class khác thường chỉ được tải khi chương trình/JVM thực sự cần chúng.

## 3. Main-Class: xác định entry point

Với source Java:

```java
package demo;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from JAR");
    }
}
```

Sau khi biên dịch, class file phải có entry:

```text
demo/Main.class
```

Nếu `Main-Class: demo.Main`, launcher có thể tìm main class và gọi phương thức:

```java
public static void main(String[] args)
```

Đối với Java 17 trong bài, đây là chữ ký entry point cần dùng. `main` phải `public`, `static`, trả `void` và nhận mảng `String[]`. Một constructor của `Main` không bắt buộc được gọi.

Hai lệnh có semantics khác nhau:

```bash
java -cp out demo.Main
java -jar app.jar
```

- Với `-cp`/`--class-path`, người chạy **ghi rõ main class trên dòng lệnh** và xác định classpath.
- Với `-jar`, launcher **lấy main class từ manifest của JAR**; tên JAR là đối số cho launcher.
- Các tham số **sau** tên JAR là tham số chương trình truyền vào `main(String[] args)`.

Ví dụ:

```bash
java -jar app.jar input.txt --verbose
```

Trong `main`, `args[0]` là `input.txt`, `args[1]` là `--verbose`. Không được hiểu các từ phía sau tên JAR là tùy chọn `-cp` của JVM.

### Khi thiếu Main-Class

Một thư viện `utils.jar` vẫn hoàn toàn hữu dụng dù không có `Main-Class`: ứng dụng khác có thể đưa nó vào classpath để sử dụng lớp bên trong.

Nhưng nếu chạy `java -jar utils.jar` mà manifest không khai báo entry point, launcher có thể báo:

```text
no main manifest attribute, in utils.jar
```

Đó là lỗi **đóng gói/khởi chạy**, không chứng minh JAR bị hỏng hoặc mọi lớp trong nó không nạp được.

## 4. Class-Path trong manifest được hiểu như thế nào?

Theo *JAR File Specification*, giá trị `Class-Path` là danh sách **URL tương đối** đến JAR hoặc thư mục phụ thuộc, cách nhau bằng một hay nhiều khoảng trắng.

Ví dụ:

```text
Class-Path: lib/helper.jar lib/parser.jar config/
```

Trong bản phân phối:

```text
dist/
├── app.jar
├── config/
│   └── config.properties
└── lib/
    ├── helper.jar
    └── parser.jar
```

Các mục tương ứng với vị trí JAR chứa manifest:

| Manifest entry | Nguồn tìm tương ứng khi JAR ở `dist/app.jar` |
|---|---|
| `lib/helper.jar` | `dist/lib/helper.jar` |
| `lib/parser.jar` | `dist/lib/parser.jar` |
| `config/` | `dist/config/`, một thư mục tài nguyên/class |

Các đường dẫn này **không phụ thuộc vào nơi bạn đứng khi gõ `java -jar`**. Nếu chạy `java -jar /opt/app/dist/app.jar` trong thư mục làm việc `/tmp`, `lib/helper.jar` vẫn được hiểu dựa trên `/opt/app/dist/app.jar`.

Một số quy tắc quan trọng của specification:

1. `Class-Path` khai báo ở **main section** và chỉ nên có **một header** `Class-Path` trong một manifest; nhiều entry liệt kê trên cùng thuộc tính.
2. Mỗi entry là một **relative URL** hợp lệ, có thể là JAR hoặc thư mục. Dấu `/` là dấu phân tách theo URL, không thay bằng `\` của Windows.
3. Entry **kết thúc bằng `/`** được hiểu như thư mục; entry không kết thúc bằng `/` được giả định là JAR.
4. Entry được resolve so với URL của **context JAR**, tức JAR chứa manifest đang được xử lý.
5. URL không hợp lệ, không tìm thấy hoặc trùng lặp có thể bị bỏ qua trong quá trình xây đường tìm kiếm. Vì thế path viết sai có thể dẫn tới lỗi chỉ lúc một class thực sự được sử dụng.
6. Quy tắc URL an toàn ưu tiên dependency nằm dưới thư mục chứa context JAR. Một số ngoại lệ tương thích áp dụng khi JAR được nạp từ file system (bao gồm trường hợp liên quan `../` và `file:`), nhưng không nên phụ thuộc vào chúng nếu muốn gói triển khai đơn giản và dễ di chuyển.

**Không đặt ký tự wildcard** `lib/*` trong manifest với kỳ vọng nó giống wildcard của tùy chọn `java -cp`. Manifest `Class-Path` dùng danh sách URL, **không có cơ chế wildcard expansion giống command-line classpath**. Hãy liệt kê `lib/a.jar lib/b.jar` cụ thể hoặc dùng công cụ đóng gói/deployment khác.

Đường dẫn chứa dấu cách phải được thể hiện bằng URL hợp lệ (ví dụ percent encoding khi phù hợp), không chỉ đặt trong dấu nháy theo cú pháp shell. Cách bền vững hơn là đặt tên file/thư mục phụ thuộc không có khoảng trắng và đóng gói theo cấu trúc cố định.

## 5. Thực hành Java 17+: tạo executable JAR có dependency riêng

Bài tập sau cho thấy rõ `Main-Class` chọn entry point và `Class-Path` mở rộng search path đến một thư viện nằm **ngoài** JAR chính.

Tạo cấu trúc nguồn:

```text
jar-demo/
├── src/
│   ├── demo/
│   │   └── Main.java
│   └── util/
│       └── Message.java
└── app.mf
```

### 5.1. Viết class phụ thuộc

Tệp **`src/util/Message.java`**:

```java
package util;

public final class Message {
    private Message() {
    }

    public static String text() {
        return "Dependency OK";
    }
}
```

### 5.2. Viết main class

Tệp **`src/demo/Main.java`**:

```java
package demo;

import util.Message;

public final class Main {
    public static void main(String[] args) {
        System.out.println("Main class: " + Main.class.getName());
        System.out.println(Message.text());
        System.out.println("Main resource visible: "
                + (Main.class.getClassLoader()
                        .getResource("demo/Main.class") != null));
    }
}
```

Ở đây `demo.Main` có tham chiếu đến `util.Message`. Khi chạy ứng dụng, class loader cần tìm `util/Message.class`. Ta sẽ đưa class đó vào `helper.jar` thay vì nhúng vào `app.jar`.

### 5.3. Tạo manifest

Tệp **`app.mf`**:

```text
Manifest-Version: 1.0
Main-Class: demo.Main
Class-Path: lib/helper.jar

```

Giữ newline sau dòng cuối và dòng trống kết thúc phần main khi tự soạn nội dung. Khi tạo archive, công cụ `jar` sẽ xử lý định dạng manifest.

### 5.4. Biên dịch và đóng gói

Trên **Bash** (Linux/macOS/Git Bash), từ thư mục `jar-demo`:

```bash
mkdir -p classes/helper classes/app dist/lib

javac --release 17 -d classes/helper src/util/Message.java
jar --create --file dist/lib/helper.jar -C classes/helper .

javac --release 17 -cp dist/lib/helper.jar -d classes/app src/demo/Main.java
jar --create --file dist/app.jar --manifest app.mf -C classes/app .

java -jar dist/app.jar
```

Trên **Windows PowerShell**, thay bước tạo thư mục đầu tiên bằng:

```powershell
New-Item -ItemType Directory -Force -Path classes/helper, classes/app, dist/lib | Out-Null
```

Các lệnh `javac`, `jar`, `java` sau đó có cùng cú pháp như trên (miễn JDK đã nằm trên `PATH`).

Đầu ra trên JDK 17+ với ví dụ này:

```text
Main class: demo.Main
Dependency OK
Main resource visible: true
```

Ý nghĩa các lệnh:

- `javac --release 17` biên dịch tương thích API/class-file target Java 17.
- `-d classes/helper` và `-d classes/app` đặt file class đúng cây package tương ứng.
- `jar --create --file dist/lib/helper.jar -C classes/helper .` đóng gói thư viện chứa `util/Message.class`.
- `javac -cp dist/lib/helper.jar ...` giúp **compiler** tìm `util.Message` khi biên dịch `Main`.
- `jar --create --file dist/app.jar --manifest app.mf ...` đóng gói ứng dụng và đưa các thuộc tính manifest vào archive.
- `java -jar dist/app.jar` dùng manifest của `app.jar`, rồi tìm dependency qua `Class-Path: lib/helper.jar`.

**Quan trọng:** `-cp` trong lệnh `javac` là **compile-time classpath**, không tự trở thành runtime classpath. Ở ví dụ này, chương trình runtime tìm dependency được là do **manifest `Class-Path`**, không phải vì compiler trước đó có tham số `-cp`.

Cấu trúc đầu ra:

```text
dist/
├── app.jar
└── lib/
    └── helper.jar
```

Nếu sao chép cả thư mục `dist` sang một máy khác có JRE/JDK phù hợp, quan hệ đường dẫn `lib/helper.jar` vẫn giữ nguyên. Nếu chỉ chép `app.jar` nhưng quên `lib/helper.jar`, đến lúc code dùng `util.Message`, runtime không thể tìm định nghĩa lớp cần thiết.

### 5.5. Kiểm tra nội dung JAR

Dùng công cụ có sẵn của JDK:

```bash
jar --list --file dist/app.jar
jar --list --file dist/lib/helper.jar
```

Các entry quan trọng cần thấy:

```text
# Trong app.jar
META-INF/
META-INF/MANIFEST.MF
demo/Main.class

# Trong helper.jar
META-INF/
META-INF/MANIFEST.MF
util/Message.class
```

Trên `jar --list` thực tế còn có thể có entry/thứ tự khác; khối trên **chỉ liệt kê các entry cần xác minh**, không phải stdout cố định toàn bộ lệnh. Điều cần kiểm tra là tên entry tương ứng chính xác với package/binary name.

Để kiểm tra nội dung manifest bằng công cụ ZIP/JAR viewer, mở `META-INF/MANIFEST.MF` bên trong `app.jar`. Khi dùng công cụ `jar --extract`, hãy giải nén vào **thư mục làm việc riêng** để không trộn file metadata vào source hiện tại.

## 6. Điều gì xảy ra nếu bỏ Class-Path?

Giữ `app.jar` và `helper.jar` như bài tập nhưng tạo manifest chỉ có:

```text
Manifest-Version: 1.0
Main-Class: demo.Main

```

Chạy `java -jar dist/app.jar` ở chế độ classpath thông thường sẽ không còn đường dẫn manifest đến `lib/helper.jar`. Khi code bắt đầu dùng `util.Message`, bạn có thể thấy lỗi dạng:

```text
java.lang.NoClassDefFoundError: util/Message
Caused by: java.lang.ClassNotFoundException: util.Message
```

Đây là **mẫu phần đầu exception/cause**, còn stack trace thực tế phụ thuộc nơi class được sử dụng và runtime. Nó cho biết JVM không giải quyết được class phụ thuộc lúc chạy. `Main-Class` vẫn có thể đúng.

Hãy phân biệt:

| Quan sát | Vấn đề thường gặp |
|---|---|
| `no main manifest attribute` | Thiếu/sai `Main-Class` trong executable JAR |
| `Could not find or load main class ...` | Main class không có ở vị trí đúng, tên sai, hoặc lỗi liên quan khi nạp main class |
| `ClassNotFoundException` | API loader được gọi tường minh không tìm được class theo đường tìm kiếm |
| `NoClassDefFoundError: util/Message` | JVM cần class dependency nhưng không có định nghĩa phù hợp lúc sử dụng |
| `UnsupportedClassVersionError` | Class file biên dịch cho Java mới hơn runtime đang chạy |
| `NoSuchMethodError` | Dependency tìm được nhưng phiên bản binary không còn method được code tham chiếu |
| `SecurityException` về sealing/signature | Ràng buộc package hoặc JAR signing không thỏa mãn |

**Không suy diễn rằng mọi `NoClassDefFoundError` đều do thiếu dependency JAR.** Lớp đã thất bại trong static initialization cũng có thể dẫn đến `NoClassDefFoundError` trong những lần sử dụng sau. Phải đọc thông điệp và cause.

## 7. Vì sao java -jar không dùng -cp theo cách thường gặp?

Đây là một trong những điểm quan trọng nhất về **đường dẫn tìm kiếm**.

Theo tài liệu lệnh `java` của JDK 17, khi chạy chế độ **`-jar`**, JAR được chỉ định là nguồn chính của user classes; **các thiết lập classpath khác bị bỏ qua** đối với chế độ khởi chạy này. Nguồn phụ thuộc bổ sung có thể đến từ **manifest `Class-Path`** và các cơ chế JVM/module phù hợp.

Ví dụ muốn thêm `other.jar` bằng cách viết:

```bash
java -cp other.jar -jar dist/app.jar
```

thì **không thể dựa vào `-cp other.jar`** để mở rộng đường tìm class của chế độ `-jar`. Cách chuẩn của bài tập là ghi `Class-Path: lib/helper.jar` trong manifest hoặc chọn một kiểu khởi chạy khác.

Khi muốn tự điều khiển classpath và main class, bỏ `-jar` và ghi rõ tên lớp:

```bash
# Linux/macOS
java -cp "dist/app.jar:dist/lib/helper.jar" demo.Main
```

```powershell
# Windows PowerShell
java -cp "dist/app.jar;dist/lib/helper.jar" demo.Main
```

**Lưu ý dấu phân cách:** Windows dùng `;`, Linux/macOS dùng `:` cho các phần tử classpath của lệnh `java -cp`. Đây là khác biệt của **command-line classpath**, không thay đổi quy tắc dùng dấu `/` bên trong thuộc tính manifest `Class-Path`.

Với cách chạy `-cp`, tên `demo.Main` trên command line chọn entry point. `Main-Class` trong manifest của `app.jar` không còn là nơi quyết định entry point cho lệnh đó. Tuy nhiên, nếu JAR chứa manifest `Class-Path` và loader đang sử dụng có hỗ trợ manifest class-path, các dependency trong manifest vẫn có thể được xử lý theo cơ chế đó; không nên hiểu chế độ `-cp` là “tắt mọi metadata của JAR”.

## 8. Class-Path và class loader có tương tác ra sao?

Các entry `Class-Path` ảnh hưởng đến **nguồn mà application class loader có thể tìm kiếm** trong mô hình classpath/JAR thông thường. Nó không thay thế quy tắc **parent delegation**:

```text
Application loader cần demo.Main
        |
        +--> parent/bootstrap/platform trước (nếu phù hợp)
        |
        +--> app.jar
        |
        +--> lib/helper.jar (theo manifest Class-Path)
```

Khi code gọi:

```java
Class<?> type = Class.forName("util.Message");
```

lệnh đó dùng defining loader của **calling class** theo hợp đồng overload một tham số. Nếu `demo.Main` được application loader định nghĩa trong mô hình ví dụ, loader đó có thể tìm `util.Message` qua entry `lib/helper.jar`. Nhưng việc manifest có đường dẫn đến JAR **không tự động nạp tất cả lớp** ngay khi JVM bắt đầu.

Ba khái niệm cần tách:

- **Class path:** nguồn JAR/thư mục để tìm class/resource.
- **Class loader:** đối tượng/cơ chế thực hiện lookup, delegation và định nghĩa lớp.
- **Runtime class identity:** `(binary name, defining class loader)`. Hai loader khác nhau có thể định nghĩa hai class cùng tên nhưng không cùng runtime type.

Đây là lý do đặt hai bản `shared.api.Service` trong các JAR của loader khác nhau có thể gây lỗi ép kiểu, dù mỗi JAR đều khai báo `Class-Path` hợp lệ. Manifest `Class-Path` xác định đường tìm kiếm; nó **không hòa nhất namespace của hai defining loaders**.

## 9. Những tình huống dễ nhầm

### 9.1. Đường dẫn tương đối tính theo JAR, không theo terminal

Giả sử:

```text
/opt/demo/app.jar
/opt/demo/lib/helper.jar
```

Chạy:

```bash
cd /tmp
java -jar /opt/demo/app.jar
```

Khi manifest có `Class-Path: lib/helper.jar`, dependency vẫn được tìm tương đối với `/opt/demo/app.jar`. Nếu di chuyển riêng `app.jar` sang `/tmp/app.jar` nhưng không mang theo `lib`, quan hệ đường dẫn không còn đúng.

### 9.2. Wildcard không giống trong manifest

Dòng:

```text
Class-Path: lib/*
```

không tương đương với tùy chọn `java -cp "lib/*"`. Khi dùng manifest, hãy liệt kê từng entry URL cần thiết, ví dụ `lib/a.jar lib/b.jar`. Với CLI, wildcard classpath có quy tắc mở rộng JAR riêng của launcher.

### 9.3. Tên JAR đúng nhưng package entry sai

Giả sử code yêu cầu `org.example.Tool` nhưng archive chỉ chứa `Tool.class` tại gốc. Đây không phải tên entry tương ứng; classloader kỳ vọng đường resource dạng `org/example/Tool.class` trong JAR thông thường. Cần biên dịch và đóng gói từ **gốc của cây package**, ví dụ `jar -C classes .`, thay vì đóng gói nhầm thư mục `classes` như một entry bên trong.

### 9.4. Quên dependency lúc chạy

Lệnh `javac -cp lib/helper.jar` chỉ giúp **biên dịch**. Nếu JAR ứng dụng không chứa dependency đó, không có manifest `Class-Path` hợp lệ và launcher không được cấu hình classpath phù hợp, runtime có thể ném lỗi khi class `util.Message` được giải quyết.

### 9.5. Main-Class ghi đường dẫn thay vì binary name

Sai:

```text
Main-Class: demo/Main.class
```

Đúng:

```text
Main-Class: demo.Main
```

Cũng không nên dùng tên canonical `Outer.Inner` khi entry point là nested class có binary name `Outer$Inner`; với lớp lồng nhau, manifest cần binary name mà launcher có thể giải quyết. Thực tế nên dùng top-level main class cho ứng dụng để đơn giản.

### 9.6. JAR chứa nhiều main classes

Một archive có thể chứa `demo.Main`, `demo.AdminMain` và `demo.BatchMain`, nhưng manifest `Main-Class` chọn **một** entry point mặc định cho `java -jar`. Khi muốn khởi chạy một entry point khác, có thể dùng `java -cp app.jar demo.BatchMain` (kèm dependencies phù hợp) hoặc tạo executable JAR/launcher riêng.

### 9.7. Xung đột dependency và thứ tự tìm kiếm

Nếu nhiều JAR chứa class cùng binary name, class loader thường chọn theo chính sách tìm kiếm của nó (classpath ordering, parent delegation, module mapping, custom loader). Đừng trông chờ `Class-Path` giải quyết version conflict. Nó chỉ mô tả URL thư viện phụ thuộc; bản class thực sự được dùng phải kiểm tra theo loader/classpath hiện hành.

### 9.8. Executable JAR khác fat JAR/nested JAR

Executable JAR chuẩn trong bài chứa trực tiếp `demo/Main.class`; dependency đặt bên ngoài dưới `lib/helper.jar` và được tham chiếu bởi manifest.

Một **fat/uber JAR** có thể gộp nội dung nhiều thư viện vào một JAR phẳng. Một **Spring Boot executable JAR** phổ biến lại bố trí classes và nested JAR ở cấu trúc như `BOOT-INF/classes` và `BOOT-INF/lib`, sử dụng launcher/classloader chuyên dụng. Đừng cho rằng JVM launcher mặc định sẽ tự quét mọi nested JAR bên trong một JAR ZIP thông thường.

`Main-Class` của dạng đóng gói framework có thể là **launcher** của framework, còn entry point nghiệp vụ được mô tả bằng metadata riêng. Những thuộc tính đó là cơ chế của công cụ đóng gói, không phải quy tắc chung của mọi JAR Java.

### 9.9. Manifest và JPMS

Trong ứng dụng **module path** dùng named modules, cơ chế module resolution, module descriptor `module-info.class` và quy tắc module access khác với classpath truyền thống. `Main-Class` của executable JAR và `Class-Path` trong manifest không thể thay thế `requires`, `exports` hay `opens` của JPMS. Nếu chạy module bằng `java --module-path ... -m module[/mainclass]`, phải phân tích startup và module-path theo hợp đồng chế độ đó.

## 10. Tạo JAR với jar --main-class thay vì file manifest thủ công

Trong trường hợp không cần thuộc tính `Class-Path` hoặc metadata đặc biệt, công cụ `jar` hỗ trợ ghi main class ngay khi đóng gói:

```bash
jar --create --file app.jar --main-class demo.Main -C classes/app .
```

Nó tạo manifest phù hợp để launcher biết entry point. Tuy nhiên, nếu ứng dụng cần `Class-Path: lib/helper.jar` như bài thực hành, phải **đảm bảo thuộc tính này có mặt** (ví dụ cung cấp `--manifest app.mf`).

Một số tùy chọn viết tắt truyền thống của `jar`:

```bash
jar cfm dist/app.jar app.mf -C classes/app .
```

Lệnh trên tương đương về mục đích với `jar --create --file dist/app.jar --manifest app.mf -C classes/app .` trong bài; các ký tự `c`, `f`, `m` lần lượt đại diện cho create, file và manifest. Với người mới học, cú pháp dài làm vai trò từng tham số rõ hơn.

**Đừng khai báo trùng thuộc tính `Main-Class` trong nhiều nguồn manifest** rồi mặc định rằng mọi công cụ đều merge theo thứ tự bạn muốn. Chọn một cơ chế cấu hình đóng gói rõ ràng và kiểm tra manifest cuối cùng.

## 11. Kiểm tra nhanh và xử lý lỗi triển khai

Khi `java -jar dist/app.jar` thất bại, có thể làm theo chuỗi sau:

1. **Xác định JAR nào đang chạy.** Đường dẫn file và working directory có đúng không? Lệnh có dùng `-jar` không?
2. **Kiểm tra `META-INF/MANIFEST.MF`.** `Main-Class` có chính xác binary name? `Class-Path` có các entry cần thiết?
3. **Kiểm tra entry class trong JAR.** `jar --list --file dist/app.jar` có `demo/Main.class` ở đúng gốc package?
4. **Kiểm tra dependency files.** `dist/lib/helper.jar` có tồn tại? `jar --list --file dist/lib/helper.jar` có `util/Message.class`?
5. **Kiểm tra cách resolve đường dẫn.** `Class-Path: lib/helper.jar` gắn với vị trí `app.jar`, không phải bất kỳ thư mục bạn `cd` tới.
6. **Đừng cố “sửa” `-jar` bằng `-cp`.** Nếu muốn lựa chọn classpath tường minh, chạy dạng `java -cp ... demo.Main`.
7. **Đọc throwable/cause thực sự.** Thiếu class, class version sai và mismatch binary có thể có giải pháp khác nhau.
8. **Kiểm tra classloader/module khi có framework hoặc plugin.** Có thể cùng binary name nhưng lớp đang được nạp bởi loader không mong muốn.

Để quan sát nơi code đang được nạp, trong một phương thức có thể dùng:

```java
System.out.println(Main.class.getClassLoader());
System.out.println(Main.class.getProtectionDomain()
        .getCodeSource().getLocation());
```

Đây là **đoạn minh họa** với `Main` tồn tại và điều kiện code source phù hợp, không phải chương trình độc lập. `getCodeSource()` hoặc thông tin nguồn có thể vắng trong một số môi trường, nên production code cần kiểm tra `null`. Kết quả loader/URL phụ thuộc runtime và không có stdout cố định.

## 12. Câu hỏi tự kiểm tra

**1. JAR có bắt buộc chứa `Main-Class` không?** Không. JAR thư viện thường không có entry point. `Main-Class` quan trọng khi muốn khởi chạy JAR bằng `java -jar`.

**2. `Main-Class` chứa `demo/Main.class` hay `demo.Main`?** Chứa binary name `demo.Main`, không thêm hậu tố `.class`.

**3. `Class-Path: lib/x.jar` được giải quyết so với thư mục nào?** So với URL/vị trí của JAR chứa manifest, tức **context JAR**.

**4. `Class-Path` cho phép `lib/*` giống `java -cp "lib/*"` không?** Không; manifest dùng các relative URL entries, không dùng wildcard classpath kiểu CLI.

**5. `javac -cp helper.jar` có tự làm `java -jar app.jar` tìm helper.jar không?** Không. Compile-time và runtime lookup là hai cấu hình khác nhau.

**6. Nếu `java -jar` báo `no main manifest attribute` thì có nghĩa bytecode trong JAR không dùng được?** Không. Có thể đó chỉ là library JAR hoặc bước đóng gói thiếu entry point.

**7. Vì sao chạy `java -jar app.jar` trên một working directory khác vẫn tìm được `lib/helper.jar`?** Vì manifest relative URL dựa trên context JAR, không dựa trên current working directory.

**8. `Class-Path` có đảm bảo class được nạp bởi đúng loader không?** Không. Classpath cung cấp nguồn tìm kiếm; định nghĩa/ủy quyền do class loader và môi trường runtime quyết định.

**9. Một JAR thông thường có tự hiểu thư viện được nhúng dưới `lib/x.jar` bên trong chính ZIP không?** Không. Cần đóng gói class phẳng, external dependency qua manifest, hoặc launcher/classloader chuyên dụng nếu dùng nested JAR.

**10. Dùng `java -cp ... demo.Main` có đọc `Main-Class` để chọn main không?** Không. Entry point được xác định bởi tên `demo.Main` trên command line.

## 13. Tài liệu chuẩn

- *JAR File Specification*, phần **JAR Manifest**, **Main Attributes**, **Class-Path Attribute** và quy tắc resolve URL (Java 17): https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html
- Java SE 17, *The java Command*: các chế độ `-jar`, `-cp`/`--class-path` và entry point: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Java SE 17, *The jar Command*: các tùy chọn `--create`, `--file`, `--manifest`, `--main-class`, `--list`: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jar.html
- Java SE 17 API, `java.lang.ClassLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html

**Ghi nhớ:** `Main-Class` giải thích **chạy từ đâu**, còn `Class-Path` chỉ **tìm các class/resource phụ thuộc ở đâu** trong mô hình classpath. Với `java -jar`, hãy đóng gói đúng entry `demo/Main.class`, khai báo `Main-Class: demo.Main`, liệt kê dependency bằng các URL tương đối như `lib/helper.jar` và phân phối đầy đủ cấu trúc thư mục bên cạnh JAR chính.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-and-module-path-007">Chế độ java -jar và các quy tắc classpath liên quan</a>

<details>
<summary>Xem chi tiết</summary>

# Chế độ `java -jar` và các quy tắc classpath liên quan

`java -jar` là một chế độ khởi chạy ứng dụng Java từ **executable JAR**. Điểm cần hiểu không phải chỉ là cách gọi lệnh, mà là **JVM xác định lớp `main` và đường dẫn tìm những lớp/phụ thuộc khác như thế nào**. Khi chọn chế độ này, các tùy chọn classpath thông thường bị bỏ qua; thay vào đó, JAR được chỉ định cùng thuộc tính `Class-Path` trong manifest (nếu có) quyết định các mục ứng dụng trên đường tìm kiếm lớp.

Một mô hình ngắn:

```text
java [tùy chọn JVM] -jar app.jar [tham số chương trình]
                             |
                             v
                  Đọc META-INF/MANIFEST.MF
                             |
                    Main-Class: demo.Main
                             |
                    Class-Path: lib/helper.jar
                             |
                 +-----------+-------------+
                 |                         |
             app.jar               lib/helper.jar
                 |                         |
          demo/Main.class        helper/Message.class
                 |                         |
                 +----- ClassLoader -------+
                             |
                       main(String[])
```

**Kết luận trọng tâm:** `java -jar app.jar` khác `java -cp app.jar demo.Main`. Hai cách có thể chạy cùng bytecode nhưng **cơ chế chọn main class và cấu hình classpath không giống nhau**.

## 1. Các hình thức khởi chạy phải phân biệt

| Chế độ | Ví dụ | Cách chọn entry point | Classpath ứng dụng |
|---|---|---|---|
| Main class | `java -cp "out:lib/*" demo.Main` | Ghi rõ tên binary của class có `main` | Từ `-cp` / `--class-path`, hoặc `CLASSPATH` nếu không ghi option |
| Executable JAR | `java -jar app.jar` | Đọc `Main-Class` từ manifest của `app.jar` | JAR được chỉ định, cộng các mục `Class-Path` hợp lệ trong manifest |
| Named module | `java --module-path mods -m my.module/demo.Main` | Module/main class theo quy tắc JPMS | Module path và module resolution; không đồng nhất với chế độ classpath |

Với ví dụ dòng đầu, trên **Windows** dùng dấu `;` thay vì dấu `:` để phân cách các mục classpath:

```powershell
java -cp "out;lib/*" demo.Main
```

Đừng dùng dấu `;` để phân tách các mục trong manifest `Class-Path`: cú pháp manifest khác với cú pháp đối số `-cp`.

### Cú pháp `java -jar`

```bash
java [JVM options] -jar path/to/app.jar [program arguments]
```

- `java`: trình khởi chạy JVM.
- Các tùy chọn JVM, ví dụ `-Xmx512m` hay `-Dapp.env=dev`, đặt **trước `-jar`**.
- `-jar`: chuyển sang chế độ tìm entry point từ manifest.
- `path/to/app.jar`: tệp executable JAR.
- Những từ phía sau tên JAR là **đối số chương trình** mà `main(String[] args)` nhận được; chúng không còn được hiểu là tùy chọn JVM của launcher.

Ví dụ:

```bash
java -Xmx512m -Dapp.env=dev -jar app.jar alpha beta
```

Ứng dụng nhận hai argument `alpha` và `beta`, còn `app.env` là system property do JVM đặt. Nếu viết `java -jar app.jar -Xmx512m` thì `-Xmx512m` sẽ được truyền cho chương trình dưới dạng argument (trừ khi ứng dụng tự xử lý nó), **không** đặt giới hạn heap.

## 2. `Main-Class` chọn entry point cho `-jar`

Một executable JAR chuẩn chứa manifest tại `META-INF/MANIFEST.MF`, ví dụ:

~~~text
Manifest-Version: 1.0
Main-Class: demo.Main
Class-Path: lib/helper.jar

~~~

`Main-Class: demo.Main` dùng **binary name**; entry class thực tế trong archive là `demo/Main.class`. Trên Java 17, bài này dùng `public static void main(String[] args)`. Thiếu `Main-Class` thường báo `no main manifest attribute` nhưng không làm JAR thư viện trở thành vô giá trị.

Có thể ghi entry point bằng `jar --create --file app.jar --main-class demo.Main -C build/app .` hoặc cung cấp manifest rõ ràng qua `--manifest MANIFEST.MF`. Hai cách đều **không tự nhúng dependency**. Định dạng manifest và quy tắc đóng gói chi tiết được dạy ở bài **JAR, manifest Main-Class và Class-Path**; phần này tập trung vào việc launcher chọn chế độ chạy.

## 3. Điều gì xảy ra với `-cp` và `CLASSPATH` khi có `-jar`?

Với `java -jar app.jar`, launcher sử dụng JAR được chỉ định làm nguồn class ứng dụng, cùng các entry hợp lệ từ `Class-Path` trong manifest. Những cấu hình **classpath thông thường** khác không được dùng để mở rộng user classpath của chế độ này.

| Cấu hình | `java -cp ... demo.Main` | `java -jar app.jar` |
|---|---|---|
| `-cp` / `-classpath` / `--class-path` | Được dùng làm classpath | **Bị bỏ qua** trong chế độ `-jar` |
| Biến môi trường `CLASSPATH` | Được dùng nếu không có classpath option thay thế | **Bị bỏ qua** đối với tìm user classes trong chế độ `-jar` |
| `Main-Class` của `app.jar` | Không cần nếu đã ghi class trên CLI | **Cần** để xác định main class |
| Manifest `Class-Path` | Có thể được áp dụng cho những JAR trên classpath phù hợp | **Có thể được áp dụng** để tìm dependency của executable JAR |
| Các option JVM như `-D...`, `-Xmx...` | Hoạt động khi đặt đúng vị trí | **Vẫn hoạt động** khi đặt trước `-jar` |

Ví dụ dễ mắc lỗi:

```bash
java -cp "lib/*" -jar app.jar
```

Không được suy luận rằng `lib/*` sẽ được dùng để thêm mọi JAR trong thư mục `lib`. Khi chọn `-jar`, `-cp "lib/*"` không có hiệu lực như bạn kỳ vọng.

Tương tự, chỉ đặt `CLASSPATH=lib/helper.jar` ngoài môi trường rồi chạy `java -jar app.jar` **không thay thế** việc khai báo dependency đúng cách.

**Phân biệt:** quy tắc trên nói về classpath ứng dụng khi khởi chạy `-jar`, **không có nghĩa tất cả tùy chọn JVM khác đều bị tắt**. Những cơ chế như bootstrap/platform classes, native library path, module system, java agents hoặc framework launcher có quy tắc riêng.

### Còn wildcard `*` thì sao?

Với classpath CLI bình thường:

```bash
java -cp "app.jar:lib/*" demo.Main
```

Launcher hỗ trợ wildcard cho **tệp `.jar` trong thư mục** theo cú pháp classpath. Dấu phân cách ví dụ trên là `:` ở Linux/macOS; trên Windows là `;`. Wildcard classpath không đệ quy, và thứ tự JAR được mở rộng không nên được dùng để bảo đảm thứ tự phụ thuộc.

Nhưng manifest `Class-Path` có cú pháp **relative URL entries được ngăn cách bằng dấu cách**; không có phép mở rộng wildcard kiểu `-cp "lib/*"`. Vì vậy:

```text
Class-Path: lib/*
```

**Không** tương đương “nạp tất cả JAR trong thư mục lib”. Cần liệt kê các file hợp lệ, ví dụ:

```text
Class-Path: lib/helper.jar lib/json.jar lib/logging.jar
```

Các thư viện được ghi cách nhau bằng khoảng trắng, **không** bằng dấu `:`, `;` hoặc dấu phẩy. Việc mỗi mục có tìm thấy được hay không còn phụ thuộc quy tắc đường dẫn ở phần tiếp theo.

## 4. `Class-Path` trong manifest khi chạy `-jar`

Giả sử `release/app.jar` nằm cạnh `release/lib/helper.jar`. Manifest `app.jar` khai báo `Class-Path: lib/helper.jar` thì dependency được resolve **tương đối với vị trí của `app.jar`**, kể cả khi khởi chạy từ thư mục khác.

Không nhầm dấu phân cách của hai chế độ: với `java -cp`, Windows dùng `;` còn Unix dùng `:`; manifest `Class-Path` dùng **danh sách relative URL cách nhau bằng khoảng trắng**. Nó **không mở rộng wildcard**: `Class-Path: lib/*` không có nghĩa mọi JAR trong thư mục. Entry kết thúc bằng `/` dùng cho directory, entry không kết thúc như vậy dùng cho JAR; path không hợp lệ/không tồn tại có thể bị bỏ qua. Manifest cần một main-section `Class-Path` hợp lệ; với dòng vật lý quá dài, công cụ `jar` xử lý quy tắc gập dòng theo byte UTF-8.

Phân phối executable JAR vì thế phải mang đúng cây thư mục library bên cạnh JAR, hoặc dùng launcher/đóng gói khác. Bài trước giải thích chi tiết chuẩn URL/manifest; ví dụ sau đây kiểm chứng trực tiếp việc `-jar` bỏ qua `-cp` với một class `side.Extra` nằm ở ngoài.

## 5. Ví dụ đầy đủ Java 17+: `-jar` tìm dependency qua manifest nhưng bỏ qua `-cp`

Ví dụ tạo hai JAR:

- `app.jar`: chứa class entry point `demo.Main`.
- `lib/helper.jar`: chứa class thư viện `helper.Message`.

Ta còn biên dịch riêng `side.Extra` vào thư mục `extra/` để chứng minh rằng classpath `-cp extra` bị bỏ qua khi dùng `-jar`. Nếu chạy ở classpath mode thông thường, class `side.Extra` sẽ được tìm thấy.

### 5.1. Cấu trúc nguồn

```text
project/
  src/
    helper/
      Message.java
    demo/
      Main.java
    side/
      Extra.java
  MANIFEST.MF
  build/                 (tạo khi biên dịch)
  extra/                 (tạo khi biên dịch)
  lib/
    helper.jar           (tạo khi đóng gói)
  app.jar                (tạo khi đóng gói)
```

### 5.2. `src/helper/Message.java`

```java
package helper;

public final class Message {
    private Message() { }

    public static String text() {
        return "Hello from helper";
    }
}
```

### 5.3. `src/side/Extra.java`

```java
package side;

public final class Extra {
    private Extra() { }
}
```

`Extra` không được đưa vào `app.jar` và cũng không nằm trong manifest `Class-Path`. Nó tồn tại riêng trong `extra/` sau biên dịch.

### 5.4. `src/demo/Main.java`

```java
package demo;

import helper.Message;

public class Main {
    public static void main(String[] args) {
        System.out.println("message: " + Message.text());
        System.out.println("args: " + args.length);
        System.out.println("mode: "
                + System.getProperty("mode", "unset"));

        try {
            Class.forName("side.Extra");
            System.out.println("extra: visible");
        } catch (ClassNotFoundException ex) {
            System.out.println("extra: absent");
        }
    }
}
```

Ý nghĩa:

- `Main` cần `helper.Message` để biên dịch và để thực thi.
- `Class.forName("side.Extra")` tìm lớp bằng tên; nếu không nhìn thấy ở loader của caller, nó ném `ClassNotFoundException`.
- Dùng `Class.forName` trong `try/catch` để chương trình **vẫn chạy được** ngay cả khi Extra không có trên classpath.
- `args.length` cho phép thấy tham số sau `app.jar` được chuyển đến chương trình.
- Property `mode` cho phép thấy tùy chọn `-Dmode=...` phải đứng trước `-jar`.

### 5.5. `MANIFEST.MF`

Tạo file text **`MANIFEST.MF`** ở thư mục `project`:

```text
Manifest-Version: 1.0
Main-Class: demo.Main
Class-Path: lib/helper.jar

```

**Bắt buộc có dòng kết thúc (và tốt nhất là dòng trống cuối)** cho main section. `Main-Class` dùng tên `demo.Main`, còn `Class-Path` trỏ tới `lib/helper.jar` tính từ vị trí của `app.jar`.

### 5.6. Biên dịch và đóng gói

Các lệnh sau dùng **Bash trên Linux/macOS** (hoặc môi trường Bash tương ứng trên Windows) và được chạy từ thư mục `project`:

```bash
mkdir -p build/helper build/app lib extra

javac -d build/helper src/helper/Message.java
jar --create --file lib/helper.jar -C build/helper .

javac -cp lib/helper.jar -d build/app src/demo/Main.java
jar --create --file app.jar --manifest MANIFEST.MF -C build/app .

javac -d extra src/side/Extra.java
```

Giải thích thứ tự:

1. Biên dịch thư viện `Message` trước.
2. Tạo `lib/helper.jar` từ các `.class` của thư viện.
3. Biên dịch `Main` với `-cp lib/helper.jar` để **javac** nhìn thấy `helper.Message`.
4. Tạo executable `app.jar` chứa `demo.Main` và manifest. **Đây là bước đóng gói**, không tự chép `helper.jar` vào `app.jar`.
5. Biên dịch `Extra` vào thư mục riêng để thử hai chế độ.

**Lưu ý:** `javac -cp ...` là **classpath tại thời điểm biên dịch**. Nó không tự động cấu hình classpath cho lần chạy `java -jar` sau này. Đó là lý do manifest cần `Class-Path`.

### 5.7. Chạy đúng chế độ `-jar`

```bash
java -jar app.jar
```

**Stdout chính xác:**

```text
message: Hello from helper
args: 0
mode: unset
extra: absent
```

Lệnh chạy tìm được `helper.Message` dù `app.jar` không chứa thư viện, vì manifest chỉ tới `lib/helper.jar`. Ngược lại, `side.Extra` nằm riêng ở `extra/` không có trong đường tìm kiếm.

Thử thêm `-cp extra` trước `-jar`:

```bash
java -cp extra -jar app.jar
```

**Stdout vẫn giống hệt:**

```text
message: Hello from helper
args: 0
mode: unset
extra: absent
```

`-cp extra` **không** làm class `side.Extra` xuất hiện: chế độ `-jar` bỏ qua classpath option. Đây là khác biệt mà ví dụ muốn chứng minh.

### 5.8. Chạy ở classpath mode thay vì `-jar`

Trên Linux/macOS:

```bash
java -cp "app.jar:lib/helper.jar:extra" demo.Main
```

Trên Windows PowerShell/cmd, dùng dấu `;`:

```powershell
java -cp "app.jar;lib/helper.jar;extra" demo.Main
```

**Stdout trong trường hợp này:**

```text
message: Hello from helper
args: 0
mode: unset
extra: visible
```

Ở đây launcher dùng classpath do chúng ta chỉ định và lấy `demo.Main` từ đối số CLI, nên `side.Extra` cũng tìm được. Kết quả chứng minh **chế độ khởi chạy**, không phải tên đuôi `.jar`, quyết định cách cấu hình classpath.

### 5.9. Tùy chọn JVM và arguments ở đâu?

```bash
java -Dmode=prod -jar app.jar alpha beta
```

**Stdout:**

```text
message: Hello from helper
args: 2
mode: prod
extra: absent
```

Vì `-Dmode=prod` đứng trước `-jar`, JVM nhận property này. `alpha beta` đứng sau JAR nên `args.length == 2`. Nếu chuyển `-Dmode=prod` ra sau `app.jar` thì ứng dụng sẽ nhận nó như một argument và property `mode` giữ giá trị mặc định `unset` (giả sử chưa có nguồn cấu hình khác).

## 6. Khi thư viện nằm ở vị trí khác JAR

Giả sử:

```text
dist/
  app.jar
  lib/helper.jar
```

Chỉ cần manifest trong `app.jar` khai báo `Class-Path: lib/helper.jar`. Lệnh:

```bash
java -jar dist/app.jar
```

sẽ giải quyết dependency theo vị trí của **`dist/app.jar`**, nghĩa là `dist/lib/helper.jar`, dù người dùng thực thi lệnh từ thư mục cha.

Nếu di chuyển `app.jar` vào thư mục khác nhưng không chuyển thư mục `lib` tương ứng, manifest trở thành tham chiếu sai. Đây là nguyên nhân phổ biến dẫn đến lỗi “chạy được trên máy build, lỗi trên máy triển khai”.

### Manifest `Class-Path` có cho phép JAR lồng bên trong JAR không?

Không theo cách mặc định của Java launcher. Nếu bạn đóng gói `lib/helper.jar` **như một entry nằm bên trong `app.jar`**, JVM class loader mặc định không tự coi entry JAR lồng đó như một mục classpath độc lập.

Các hướng đóng gói phổ biến:

1. **Thin JAR + thư viện ngoài:** dùng `Main-Class` và manifest `Class-Path` tới các JAR ở thư mục kề bên.
2. **Uber/Fat JAR:** công cụ build thường gộp các `.class` và tài nguyên phụ thuộc vào một JAR có cấu trúc phù hợp, đồng thời xử lý xung đột service resources khi cần.
3. **Executable archive với launcher riêng:** framework (chẳng hạn launcher chuyên dụng cho nested JARs) tự cung cấp cách tìm dependency; cần đọc quy tắc của framework thay vì áp dụng mặc định `java -jar` cho cấu trúc nested.

**Đừng giả định** mọi “fat jar” có cấu trúc giống nhau. Một JAR có thể chạy bằng `java -jar` nhờ một `Main-Class` của launcher framework chứ không phải class chứa `main` ứng dụng mà bạn viết.

## 7. Classpath, class loader và tính nhìn thấy của lớp

Với ứng dụng Java dùng classpath thông thường, các mục executable JAR và manifest dependency được dùng để cấu hình **đường tìm kiếm** cho bộ nạp lớp ứng dụng (system/application class loader trong cấu hình phổ biến).

```text
ClassLoader
    |
    | loadClass("helper.Message")
    v
Ứng dụng tìm class theo loader hierarchy và classpath
    |
    +-- app.jar               (không có helper/Message.class)
    |
    +-- lib/helper.jar        (có helper/Message.class)
    |
    v
Runtime Class<helper.Message>
```

Việc khai báo đường tìm kiếm không có nghĩa mọi class đều được load ngay. Khi mã chạy `Message.text()`, runtime cần lớp `helper.Message` thích hợp. Nếu file thư viện không tìm được, có thể phát sinh lỗi tại thời điểm JVM cần resolve/load kiểu, không nhất thiết ngay khi `java` bắt đầu.

**Một điểm khác:** `java -jar` không tự nạp lại hoặc override các lớp Java nền tảng do bootstrap/platform loader cung cấp. Class loader hierarchy, định danh kiểu `(binary name, defining loader)` và các giới hạn của JPMS vẫn áp dụng.

### Vì sao không nên lẫn `-jar` và `-cp` khi điều tra lỗi?

Hai lệnh:

```bash
java -jar app.jar
java -cp "app.jar:lib/helper.jar" demo.Main
```

có thể cho cùng output ở ví dụ đơn giản, **nhưng không chứng minh hai chế độ đồng nhất**. Chế độ đầu dựa vào `Main-Class` và manifest `Class-Path`; chế độ thứ hai lấy tên class và classpath trực tiếp từ CLI.

Nếu `Class-Path` trong manifest sai, lệnh thứ hai có thể chạy thành công vì `-cp` được chỉ định rõ, còn lệnh thứ nhất thất bại. Đây là phép đối chiếu rất hữu ích khi debug packaging.

## 8. Liên hệ với module path (JPMS)

Java 9+ có hệ thống module, nhưng cần phân biệt rõ:

- `java -jar app.jar` là **khởi chạy JAR theo manifest**, và lớp ứng dụng thông thường nằm trong **unnamed module** của loader liên quan.
- `java --module-path mods -m module.name/main.Class` là **khởi chạy module**, dựa vào module descriptor/JPMS.
- Một JAR có thể được đặt trên classpath trong một chế độ, nhưng khi chạy dưới module path ở ngữ cảnh khác có thể trở thành named/automatic module theo các quy tắc tương ứng.
- Cờ `--module-path` không phải một cách thay thế trực tiếp cho `Class-Path` trong manifest, và việc một dependency có cần JPMS resolution còn phụ thuộc kiểu chương trình.

Đối với phần lớn ứng dụng học Java Core bằng `-jar` + thư viện thông thường, trước tiên hãy debug **classpath của executable JAR**; chỉ chuyển sang phân tích module khi dự án thực sự sử dụng named modules.

## 9. Những lỗi thường gặp và cách xác định nguyên nhân

### 9.1. `no main manifest attribute, in app.jar`

**Nguyên nhân:** thiếu `Main-Class` trong main section của manifest.

**Kiểm tra:**

```bash
jar --describe-module --file app.jar
unzip -p app.jar META-INF/MANIFEST.MF
```

Lệnh `unzip -p` là tiện ích hệ điều hành tùy chọn; nếu không có, dùng `jar --extract --file app.jar META-INF/MANIFEST.MF` ở thư mục kiểm tra riêng rồi mở tệp manifest. Lệnh `jar --describe-module` chỉ hữu ích cho thông tin module, **không thay thế việc đọc manifest** để xác minh `Main-Class`.

**Khắc phục:** tạo JAR với `--main-class` hoặc một manifest có `Main-Class: demo.Main` đúng tên, sau đó đóng gói lại.

### 9.2. `Could not find or load main class demo.Main`

**Nguyên nhân có thể:** manifest chỉ sai binary name, package/directory layout trong JAR không đúng, class không được đóng gói, class không nạp được do thiếu dependency, hoặc tên lớp trong cấu hình chạy nhầm.

**Kiểm tra:**

```bash
jar --list --file app.jar
```

Bên trong JAR phải có entry class đúng tên tương ứng, ví dụ `demo/Main.class`. Đừng nhầm `Main-Class: demo.Main` với `Main-Class: demo/Main`.

### 9.3. `NoClassDefFoundError` hoặc `ClassNotFoundException` cho dependency

**Nguyên nhân thường thấy:** `helper.Message` không có trong `app.jar` và `Class-Path` không trỏ đúng tới `lib/helper.jar`, hoặc dependency của helper còn thiếu.

**Kiểm tra theo thứ tự:**

1. Xác định tên nhị phân của class không nạp được.
2. Tìm class đó trong JAR phụ thuộc thích hợp.
3. Kiểm tra `Class-Path` trong manifest của JAR thực sự đang chạy.
4. Resolve đường dẫn **tương đối với JAR**; xác nhận file tồn tại ở máy chạy.
5. Kiểm tra tên file, chữ hoa/thường, ký tự URL, quyền đọc và manifest không bị lỗi xuống dòng.
6. So sánh `java -jar app.jar` với classpath mode chỉ định rõ cả JAR.
7. Xem nguyên nhân gốc và stack trace; `NoClassDefFoundError` còn có thể xuất hiện sau một lần class initialization thất bại, không chỉ khi thiếu JAR.

### 9.4. `java -cp lib/* -jar app.jar` vẫn không tìm được class

**Giải thích:** các classpath option thông thường không có hiệu lực trong chế độ `-jar`. Đặt dependency trong manifest `Class-Path`, đóng gói phù hợp, hoặc chuyển hẳn sang chế độ `java -cp ... demo.Main`.

### 9.5. Đặt `Class-Path: lib/*` nhưng không tìm được thư viện

**Giải thích:** wildcard của `-cp` không chuyển sang manifest `Class-Path`. Ghi từng JAR theo relative URL hợp lệ, hoặc dùng công cụ build sinh manifest.

### 9.6. Chạy từ thư mục khác bị lỗi

**Điều tra:** nếu thư viện được tham chiếu từ manifest, URL được giải quyết theo vị trí JAR; nếu ứng dụng mở file cấu hình bằng đường dẫn tương đối trong code Java (ví dụ `Path.of("config/app.properties")`), đường dẫn code có thể lại tương đối với **working directory**. Hai phép resolve khác nhau.

**Khắc phục:** phân biệt dependency URL trong manifest với file system path do ứng dụng tự sử dụng. Đừng sửa `Class-Path` khi lỗi thực chất ở mã mở tệp cấu hình.

### 9.7. Chạy từ IDE thành công, phát hành JAR thất bại

IDE thường cấu hình toàn bộ dependency thành classpath riêng. Khi tạo executable JAR, manifest có thể thiếu `Class-Path` hoặc artifact đóng gói thiếu nội dung.

**Khắc phục:** kiểm tra lệnh JVM mà IDE dùng, manifest artifact cuối cùng và cấu trúc thư mục triển khai; thử chạy bằng chính `java -jar` trong môi trường clean.

## 10. Checklist đóng gói executable JAR

Trước khi phát hành:

- [ ] Có file JAR thật sự tồn tại và đọc được.
- [ ] Manifest có `Main-Class` hợp lệ trong main section.
- [ ] Tên package và đường dẫn `.class` trong JAR khớp với `Main-Class`.
- [ ] Tất cả dependency runtime tồn tại ở vị trí mà manifest `Class-Path` hợp lệ trỏ tới, hoặc đã được đóng gói đúng kiểu fat/launcher.
- [ ] Không dựa vào `CLASSPATH`, `-cp` hay đường dẫn chỉ có trong IDE để chạy `java -jar`.
- [ ] Nếu dùng manifest `Class-Path`, các entry được ngăn bằng dấu cách, không dùng wildcard, và relative path được resolve theo vị trí JAR.
- [ ] Tùy chọn JVM được đặt trước `-jar`; argument chương trình đặt phía sau JAR.
- [ ] Kiểm tra chương trình từ một thư mục làm việc khác để phát hiện nhầm lẫn working-directory.
- [ ] Kiểm tra module/launcher riêng nếu dự án dùng JPMS hoặc executable archive không theo cấu trúc classpath JAR thông thường.
- [ ] Dùng Java runtime tương thích với version của `.class` và thư viện, rồi chạy thử artifact sau khi đóng gói.

## 11. Tự kiểm tra

**Câu 1.** Manifest có `Main-Class: demo.Main` nhưng không có `Class-Path`. `demo.Main` dùng một thư viện JAR nằm ở `lib/helper.jar`. Lệnh `java -cp lib/helper.jar -jar app.jar` có phải cách chuẩn để đưa helper vào đường tìm kiếm không?

**Đáp án:** Không. `-cp` bị bỏ qua trong `-jar`. Cần cấu hình manifest `Class-Path: lib/helper.jar`, đóng gói dependency theo một cách khác phù hợp, hoặc chuyển sang `java -cp ... demo.Main`.

**Câu 2.** Manifest của `release/app.jar` ghi `Class-Path: lib/helper.jar`. Chạy lệnh từ thư mục cha. Đường dẫn nào được dùng?

**Đáp án:** `release/lib/helper.jar`, vì manifest `Class-Path` resolve theo vị trí của JAR chứa manifest.

**Câu 3.** Viết `Class-Path: lib/*` có giống `java -cp "lib/*"` không?

**Đáp án:** Không. Wildcard expansion là quy tắc của classpath CLI; manifest cần relative URL entries hợp lệ, thường là từng file JAR.

**Câu 4.** Hai lệnh `java -jar app.jar` và `java -cp app.jar demo.Main` có luôn tương đương không?

**Đáp án:** Không. Lệnh đầu lấy entry point từ manifest và dùng các phụ thuộc manifest; lệnh sau lấy class name và classpath từ CLI. Chúng chỉ có thể tình cờ cho cùng hành vi trong cấu hình đơn giản.

**Câu 5.** `java -Dmode=prod -jar app.jar hi` và `java -jar app.jar -Dmode=prod` có đặt cùng JVM property không?

**Đáp án:** Không. Ở lệnh đầu `-Dmode=prod` là option JVM; ở lệnh sau nó là argument của `main`.

**Câu 6.** Một JAR chứa file `lib/helper.jar` **bên trong archive**. Manifest ghi `Class-Path: lib/helper.jar`. JVM mặc định có tự truy cập nested archive đó như một dependency không?

**Đáp án:** Không. Mục manifest là relative URL tham chiếu bên ngoài JAR theo ngữ cảnh JAR; nested JAR cần launcher/cơ chế đóng gói đặc biệt hoặc gộp class/tài nguyên đúng cách.

## 12. Sơ đồ quyết định khi ứng dụng không tìm thấy class

```text
Chương trình chạy theo cách nào?
             |
       +-----+------+
       |            |
    java -jar      java -cp / --class-path
       |            |
Đọc manifest       Kiểm tra classpath CLI
       |            |  
Main-Class đúng?   Các mục theo dấu phân cách OS?
       |
Class-Path có relative URL hợp lệ?
       |
Resolve theo thư mục của JAR
       |
Dependency có tồn tại trong thư mục/JAR được chỉ?
       |
JVM/loader có quyền truy cập và bytecode phù hợp?
       |
Nếu vẫn lỗi: kiểm tra type name, module, phụ thuộc bắc cầu,
class initialization, classloader và lỗi gốc.
```

**Tóm tắt:** `java -jar` tìm `main` qua `Main-Class` và thiết lập đường tìm kiếm ứng dụng từ executable JAR cùng `Class-Path` trong manifest. `-cp`/`CLASSPATH` thường dùng với classpath mode nhưng **không mở rộng classpath của `-jar`**. Manifest `Class-Path` dùng **relative URLs**, phân cách **bằng dấu cách** và resolve **tương đối vị trí JAR**, chứ không phải wildcard/path separator của shell.

### Nguồn tham khảo chính thức

- [Java SE 17 — The java Command: các chế độ chạy và `-jar`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html)
- [Java SE 17 — JAR File Specification: manifest và `Class-Path`](https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html)
- [Java SE 17 — ClassLoader API: system loader và JAR manifest classpath](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-and-module-path-008">Multi-release JAR và chọn bytecode theo phiên bản Java</a>

<details>
<summary>Xem chi tiết</summary>

# Multi-release JAR và chọn bytecode theo phiên bản Java

## 1. Multi-release JAR giải quyết vấn đề gì?

Một thư viện Java đôi khi cần **chạy trên nhiều phiên bản JVM** nhưng muốn tận dụng API hoặc cách cài đặt hiệu quả hơn ở JDK mới. Nếu chỉ đưa class được biên dịch bằng Java 17 vào JAR, Java 8 không thể nạp class đó. Nếu chỉ giữ class tương thích Java 8, thư viện không khai thác được những khả năng xuất hiện từ Java 11, 17 hoặc mới hơn.

**Multi-release JAR (MR-JAR)**, được giới thiệu ở **Java 9 bởi JEP 238**, cho phép một file JAR chứa **nhiều bản class/resource cho những Java release khác nhau**. JVM và class loader hỗ trợ MR-JAR chọn phiên bản phù hợp với **Java runtime thực sự đang chạy**, theo cấu trúc bên trong JAR và thuộc tính manifest.

Hình dung cùng một binary name `demo.Selector`:

```text
library.jar
├── META-INF/
│   ├── MANIFEST.MF
│   └── versions/
│       ├── 11/
│       │   └── demo/Selector.class      (bản Java 11)
│       └── 17/
│           └── demo/Selector.class      (bản Java 17)
└── demo/
    ├── Main.class                       (bản nền)
    └── Selector.class                   (bản nền, Java 8)
```

Các class `demo.Selector` cùng tên là **các biến thể thay thế nhau**. Trong một lượt nạp tên này qua loader đọc MR-JAR, JVM chọn **một class file phù hợp**. Nó không chạy ba bản đồng thời và không tạo ba đối tượng `Class<?>` chỉ vì cả ba tệp tồn tại trong archive.

Bài này tập trung vào **đường dẫn tìm kiếm trong JAR**, **thuật toán chọn bytecode**, **cách xây dựng một MR-JAR có thể chạy**, **tác động của ClassLoader** và **các lỗi tương thích**.

## 2. Điều kiện để JAR thực sự là multi-release

Chỉ đặt các file bên dưới `META-INF/versions/` **chưa đủ**. JAR phải có main manifest attribute:

```text
Manifest-Version: 1.0
Multi-Release: true

```

File vật lý là `META-INF/MANIFEST.MF`. Thuộc tính `Multi-Release: true` cần nằm ở **main section** (trước dòng trống phân tách các section). Giá trị `true` không phân biệt chữ hoa/chữ thường theo đặc tả, nhưng nên ghi nhất quán như trên.

- Nếu thuộc tính này **không có** hoặc khác `true`, runtime xử lý JAR như JAR thông thường khi tìm class; các mục `META-INF/versions/N` không được dùng làm bản thay thế theo phiên bản.
- `N` trong `META-INF/versions/N` là **số feature release của Java** (ví dụ `9`, `11`, `17`, `21`), **không phải** số major của class file.
- Phiên bản thấp nhất của thư mục versioned có ý nghĩa là **9**. Thư mục `META-INF/versions/8/` không hoạt động như một nhánh dành riêng Java 8 theo quy tắc MR-JAR.
- Mục cho Java 8 hoặc thấp hơn phải nằm ở **thư mục gốc của JAR** nếu thư viện tuyên bố hỗ trợ chúng.
- Thư mục `META-INF/versions/N` phải chứa đúng đường dẫn package như bản root, ví dụ `META-INF/versions/17/com/acme/Foo.class`, không được thay thế bằng tên file khác rồi mong JVM tự map.

Tên `N` phải theo biểu diễn số nguyên dương hợp lệ của đặc tả, không dùng `v17`, `017` hay các chuỗi tùy ý. Dùng đúng `META-INF/versions/17` để tạo nhánh cho Java 17.

**Phân biệt hai loại phiên bản ngay từ đầu:**

| Giá trị | Ví dụ | Ý nghĩa |
|---|---|---|
| Java feature release | `17` | JDK/JVM Java 17 |
| Thư mục MR-JAR | `META-INF/versions/17` | Biến thể được phép chọn từ Java 17 trở lên khi không có bản phù hợp cao hơn |
| Class file major | `61` | Major của class file Java 17 |
| Class file minor | `0` | Bản class thông thường không dùng preview |

`META-INF/versions/17` là **directory targeting Java 17**; nó không đồng nghĩa với `major_version=17` bên trong bytecode.

## 3. Thuật toán chọn entry: số cao nhất không vượt quá runtime

Giả sử runtime đang chạy Java feature version `R` và class loader MR-aware cần tìm một logical entry, chẳng hạn `demo/Selector.class`.

Với Java 9 trở lên và JAR có `Multi-Release: true`, quy tắc khái quát là:

1. Tìm entry cùng đường dẫn tại `META-INF/versions/R/`.
2. Nếu chưa có, tìm lần lượt các thư mục phiên bản thấp hơn, từ `R-1` xuống **`9`**.
3. Lấy **entry có phiên bản cao nhất ≤ R** nếu nó tồn tại.
4. Nếu không thấy bản versioned nào phù hợp, dùng entry trong **thư mục gốc**.
5. Nếu root cũng không có entry phù hợp, quá trình tìm class đó thất bại.

```text
Runtime Java R ≥ 9
  tìm demo/Selector.class
       │
       ▼
META-INF/versions/R/demo/Selector.class?
       │ không có
       ▼
META-INF/versions/(R-1)/demo/Selector.class?
       │ không có
       ▼
... tìm giảm dần đến 9 ...
       │ không có
       ▼
demo/Selector.class ở JAR root
       │
       ▼
ClassLoader định nghĩa class từ bytecode được chọn
```

**Quan trọng:** Runtime **không nhất thiết chọn nhánh có số đúng bằng phiên bản nó đang chạy**. Nếu Java 21 chỉ thấy `versions/17` và `versions/11`, nó chọn **17**, vì đây là bản mới nhất **không vượt 21**. Nếu Java 15 không có bản 15 nhưng có 11, chọn 11. Java 8 không thực thi thuật toán MR-JAR của Java 9 và dùng đường dẫn root như JAR thông thường.

### Ví dụ bảng lựa chọn

Giả sử JAR có:

```text
demo/Selector.class
META-INF/versions/11/demo/Selector.class
META-INF/versions/17/demo/Selector.class
META-INF/versions/21/demo/Helper.class
```

Khi hỏi **`demo.Selector`**:

| Java runtime | Entry được chọn cho `demo.Selector` |
|---|---|
| Java 8 | `demo/Selector.class` (root) |
| Java 9, 10 | `demo/Selector.class` (root) |
| Java 11–16 | `META-INF/versions/11/demo/Selector.class` |
| Java 17–20 | `META-INF/versions/17/demo/Selector.class` |
| Java 21+ | `META-INF/versions/17/demo/Selector.class`, nếu chưa có bản Selector ở nhánh cao hơn |

Entry `versions/21/demo/Helper.class` **không ảnh hưởng** lựa chọn `Selector.class`: JVM chọn **riêng theo từng logical path**, không chọn duy nhất một thư mục cho toàn bộ JAR.

Nếu chỉ có phiên bản `17` mà không có `11`, Java 11 **quay về root**; nó không thử nạp class Java 17 từ nhánh cao hơn rồi hy vọng bytecode hoạt động.

## 4. “Chọn đúng nhánh” và “bytecode tương thích” là hai phép kiểm tra khác nhau

MR-JAR ưu tiên thư mục theo **Java runtime version**, nhưng JVM vẫn phải đọc và xác minh **class file version** thực sự của bytes được chọn.

Trong class file, các trường đầu là magic, `minor_version` và `major_version`. Một số ánh xạ:

| Java release | Class file major | Minor thông thường |
|---|---:|---:|
| Java 8 | 52 | 0 |
| Java 9 | 53 | 0 |
| Java 11 | 55 | 0 |
| Java 17 | 61 | 0 |
| Java 21 | 65 | 0 |
| Java 25 | 69 | 0 |
| Java 26 | 70 | 0 |

Một file dưới `META-INF/versions/17/` **không được** yêu cầu class file version cao hơn khả năng Java 17 hỗ trợ theo đặc tả MR-JAR. Ví dụ đặt bytecode **major 65** (biên dịch cho Java 21) vào nhánh `versions/17` là cấu trúc sai: Java 17 sẽ **chọn** entry này theo đường dẫn nhưng **không thể nạp** bytecode đó.

- Dùng `javac --release 8` để tạo bản root hỗ trợ Java 8.
- Dùng `javac --release 11` để tạo biến thể Java 11.
- Dùng `javac --release 17` để tạo biến thể Java 17.
- Một build dùng JDK 17 trở lên có thể biên dịch cả ba target nếu compiler hỗ trợ chúng; không cần chạy `javac` của từng runtime đích.

`javac --release N` hạn chế cú pháp, class target và tập API chuẩn của release N khi biên dịch. Chỉ đổi tên thư mục thành `versions/N` **không biến** bytecode của bản mới thành bytecode của N.

**Cảnh báo preview bytecode:** `minor=65535` biểu thị phụ thuộc preview của đúng bản Java; bytecode preview không tương thích tự động với các Java release tương lai dù major thấp hơn. Không đóng gói class preview như biến thể dài hạn chỉ vì MR-JAR có nhánh tương ứng. Ưu tiên code dùng API và tính năng Java đã được chuẩn hóa cho đúng runtime.

## 5. Ví dụ hoàn chỉnh: một JAR với ba bản Java 8, 11 và 17

Ta sẽ tạo một `mr-demo.jar` gồm:

- `demo.Main` nằm ở root, biên dịch tương thích Java 8.
- `demo.Selector` bản root trả chuỗi `"base-8"`.
- `demo.Selector` bản Java 11 trả `"java-11"`.
- `demo.Selector` bản Java 17 trả `"java-17"`.

Tất cả các biến thể `Selector` giữ **cùng public API** `public static String selected()`. Đây là mẫu tốt để thấy JVM thực sự thay đổi implementation theo runtime.

### 5.1. Chuẩn bị cấu trúc nguồn

Tạo các file và thư mục như sau:

```text
mr-demo/
├── src/
│   ├── base/
│   │   └── demo/
│   │       ├── Main.java
│   │       └── Selector.java
│   ├── v11/
│   │   └── demo/
│   │       └── Selector.java
│   └── v17/
│       └── demo/
│           └── Selector.java
└── out/
    ├── base/
    ├── v11/
    └── v17/
```

Các thư mục `out` chưa có file, sẽ chứa kết quả biên dịch. Mỗi phiên bản `Selector.java` nằm trong **source root riêng** vì chúng có cùng package/binary name và không thể đặt cùng một đường source compilation thông thường.

### 5.2. Bản Java 8: src/base/demo/Selector.java

```java
package demo;

public final class Selector {
    private Selector() {
    }

    public static String selected() {
        return "base-8";
    }
}
```

### 5.3. Lớp chạy chung: src/base/demo/Main.java

```java
package demo;

public final class Main {
    public static void main(String[] args) {
        System.out.println("Selected: " + Selector.selected());
    }
}
```

Chú ý `Main` không gọi API chỉ có từ Java 9 trở lên, vì bản root sẽ được biên dịch với `--release 8`. Nó tham chiếu cùng logical type `demo.Selector`; phiên bản nào thực thi phương thức đó được xác định khi runtime nạp `Selector`.

### 5.4. Bản Java 11: src/v11/demo/Selector.java

```java
package demo;

public final class Selector {
    private Selector() {
    }

    public static String selected() {
        return "java-11";
    }
}
```

### 5.5. Bản Java 17: src/v17/demo/Selector.java

```java
package demo;

public final class Selector {
    private Selector() {
    }

    public static String selected() {
        return "java-17";
    }
}
```

Bản 11 và 17 trong bài chỉ đổi chuỗi output để dễ kiểm chứng. Trong thư viện thực tế, các nhánh có thể triển khai khác nhau (ví dụ dùng API hay thuật toán có mặt ở JDK mới), **nhưng phải giữ hợp đồng API công khai được phép sử dụng chung**.

### 5.6. Biên dịch đúng từng release

Từ thư mục `mr-demo`, với **JDK 17 hoặc mới hơn** có hỗ trợ các target 8, 11, 17, tạo ba thư mục output. Nếu dùng Bash:

```bash
mkdir -p out/base out/v11 out/v17
```

Trên Windows PowerShell, có thể tạo chúng bằng:

```powershell
New-Item -ItemType Directory -Force out/base,out/v11,out/v17 | Out-Null
```

Tiếp theo, chạy các lệnh `javac` sau trong terminal:

```bash
javac --release 8 -d out/base src/base/demo/Main.java src/base/demo/Selector.java
javac --release 11 -d out/v11 src/v11/demo/Selector.java
javac --release 17 -d out/v17 src/v17/demo/Selector.java
```

Kết quả:

```text
out/
├── base/
│   └── demo/
│       ├── Main.class
│       └── Selector.class
├── v11/
│   └── demo/
│       └── Selector.class
└── v17/
    └── demo/
        └── Selector.class
```

Không biên dịch cả ba `Selector.java` vào **một output directory**, vì các class cùng tên sẽ ghi đè hoặc xung đột. Cũng không cần ba bản `Main`; `Main` ở root có thể gọi implementation của `Selector` mà JVM đã chọn.

### 5.7. Đóng gói multi-release JAR

Dùng `jar` tool của JDK 17+:

```bash
jar --create --file mr-demo.jar --main-class demo.Main -C out/base . --release 11 -C out/v11 . --release 17 -C out/v17 .
```

Trong lệnh này có **hai nghĩa khác nhau của `--release`**:

- `javac --release N` chọn cấp độ biên dịch và API tương thích Java N.
- `jar --release N` yêu cầu đóng gói những file theo sau vào `META-INF/versions/N/`.

`jar` tool tạo metadata để archive được nhận diện là MR-JAR, gồm `Multi-Release: true` trong manifest. Tùy phiên bản JDK, quá trình đóng gói có thể kiểm tra một số ràng buộc về các bản class cùng tên.

Archive kết quả có cấu trúc logic:

```text
mr-demo.jar
├── META-INF/
│   ├── MANIFEST.MF
│   └── versions/
│       ├── 11/
│       │   └── demo/Selector.class
│       └── 17/
│           └── demo/Selector.class
└── demo/
    ├── Main.class
    └── Selector.class
```

Trong `META-INF/MANIFEST.MF`, các thuộc tính quan trọng sẽ gồm:

```text
Manifest-Version: 1.0
Main-Class: demo.Main
Multi-Release: true

```

Có thể có thêm thuộc tính do `jar` tool thêm vào (chẳng hạn `Created-By`). Không yêu cầu file manifest trong archive phải chứa **chính xác ba dòng duy nhất** như trích đoạn.

### 5.8. Chạy với những JVM khác nhau

Lệnh chung:

```bash
java -jar mr-demo.jar
```

Nhưng kết quả phụ thuộc vào **phiên bản Java của lệnh `java` đang thực thi**:

| JVM chạy lệnh | Biến thể Selector | Stdout |
|---|---|---|
| Java 8 | Root, target Java 8 | `Selected: base-8` |
| Java 9 hoặc 10 | Root | `Selected: base-8` |
| Java 11–16 | `versions/11` | `Selected: java-11` |
| Java 17–20 | `versions/17` | `Selected: java-17` |
| Java 21 hoặc mới hơn | `versions/17` nếu chưa có biến thể cao hơn | `Selected: java-17` |

Ví dụ nếu đang chạy bằng Java 17, stdout **chính xác** của chương trình là:

```text
Selected: java-17
```

Nếu máy chỉ cài JDK 21, bảng này mô tả hành vi của các JVM tương ứng chứ **không** có nghĩa một lần chạy bằng Java 21 có thể mô phỏng nguyên vẹn JVM 8. Muốn xác nhận Java 8 thực sự chạy, phải sử dụng runtime Java 8; muốn xác nhận Java 11, dùng runtime Java 11. Lệnh `javac --release 8` **không ép `java -jar` chạy như Java 8**.

## 6. Kiểm chứng JAR và bytecode thực tế

Không nên xác nhận MR-JAR chỉ bằng tên file `mr-demo.jar`. Sau build, hãy kiểm tra cả **cấu trúc**, **manifest**, **class file version** và **runtime selection**.

### 6.1. Liệt kê entry vật lý trong archive

```bash
jar --list --file mr-demo.jar
```

Tìm những entry quan trọng:

```text
demo/Main.class
demo/Selector.class
META-INF/versions/11/demo/Selector.class
META-INF/versions/17/demo/Selector.class
META-INF/MANIFEST.MF
```

Đây là **các dòng cần tìm**, không phải toàn bộ stdout có thứ tự cố định. Liệt kê physical entries cho thấy các file tồn tại nhưng **không nói** JVM cụ thể đã chọn file nào.

### 6.2. Xem class file major bằng javap

```bash
javap -verbose -classpath out/base demo.Selector
javap -verbose -classpath out/v11 demo.Selector
javap -verbose -classpath out/v17 demo.Selector
```

Trong output dài của từng lệnh, giá trị mong đợi lần lượt là:

```text
base: major version: 52
v11:  major version: 55
v17:  major version: 61
```

Ba dòng trên là **bảng tóm tắt kết quả**, không phải một đoạn stdout nguyên văn từ một lệnh `javap`. `javap -verbose` cũng hiển thị các trường constant pool, phương thức và attributes.

Với một MR-JAR, `javap` hỗ trợ tùy chọn `--multi-release` ở những JDK có hỗ trợ. Ví dụ:

```bash
javap --multi-release 11 -classpath mr-demo.jar -c demo.Selector
javap --multi-release 17 -classpath mr-demo.jar -c demo.Selector
```

Có thể dùng các lệnh này để xem phiên bản class có hiệu lực ở từng release mục tiêu, thay vì vô tình chỉ xem root. Nếu phân tích trực tiếp file trong JAR bằng công cụ ZIP, phải tự chọn đúng entry vật lý.

### 6.3. Dùng jar --validate

```bash
jar --validate --file mr-demo.jar
```

Công cụ `jar` có khả năng kiểm tra một số ràng buộc, đặc biệt khi tồn tại versioned classes. Tuy nhiên **thành công ở bước validate không chứng minh 100% API, dependency hoặc hành vi runtime của mọi nhánh là tương thích**. Đặc tả không yêu cầu công cụ hoặc JVM kiểm tra đầy đủ tính tương đương của mọi API công khai giữa các bản class.

### 6.4. Xác định runtime thật đang chạy

```bash
java -version
javac -version
```

Hai câu lệnh có thể dùng hai installation khác nhau qua `PATH`, IDE, Gradle toolchain, Docker hay CI. **JVM runtime** quyết định nhánh MR-JAR khi nạp; **compiler** quyết định bytecode major khi build. Trường hợp phát hành từ CI với JDK 21 rồi triển khai lên Java 17 phải được kiểm chứng bằng Java 17 thực tế.

## 7. Mỗi class/resource được chọn riêng, không chọn cả JAR thành “bản 17”

Một hiểu nhầm phổ biến là: nếu runtime chọn version 17 thì **mọi class trong JAR** đều lấy từ `versions/17`. Thực tế, **mỗi logical path** được tra theo chuỗi versioned directories độc lập.

Ví dụ:

```text
demo/Api.class
demo/FeatureA.class
demo/FeatureB.class

META-INF/versions/11/demo/FeatureA.class
META-INF/versions/17/demo/FeatureB.class
```

Trên Java 17:

- `demo.Api` không có entry versioned, nên dùng `demo/Api.class` ở root.
- `demo.FeatureA` dùng `versions/11/demo/FeatureA.class` (cao nhất ≤17 trong các bản của chính FeatureA).
- `demo.FeatureB` dùng `versions/17/demo/FeatureB.class`.

Ba logical classes tồn tại đồng thời trong **một ứng dụng** và có thể dùng các bản tối ưu khác nhau. Điều này giải thích vì sao mã ở một nhánh có thể gặp lỗi nếu API nội bộ giữa các lớp không tương thích.

**Tài nguyên cũng có thể được versioned**, nhưng không phải bất kỳ file nào bên dưới `META-INF` cũng được phép thay thế. Đặc tả quy định **resources nằm dưới `META-INF` không được versioned**, ví dụ cấu hình service provider `META-INF/services/...` không được tùy tiện đặt bản riêng bên dưới `META-INF/versions/N/META-INF/services/...` rồi mong cơ chế multi-release chọn nó.

Nếu ứng dụng/plugin đọc ZIP entries ở mức thấp, nó có thể thấy đường dẫn vật lý của từng phiên bản. Kết quả đọc resource qua `ClassLoader.getResource(...)` hoặc `Class.getResource(...)` có thể phụ thuộc runtime và triển khai JAR-aware của loader; không suy ra mọi API đọc file ZIP đều tự động áp dụng virtual view của MR-JAR.

## 8. Khi có nhiều JAR/classpath entry: thứ tự tìm kiếm nào thắng?

Quy tắc `versions/N` áp dụng **sau khi một class loader đã chọn nguồn JAR thích hợp để tìm tên class**. Nó không thay thế quy tắc delegation giữa ClassLoader hay thứ tự các classpath entries.

Ví dụ:

```text
Host Application ClassLoader
    ├── shared.jar (có demo.Selector)
    └── mr-demo.jar (có demo.Selector + versions/11 + versions/17)

Plugin ClassLoader
    └── plugin.jar
```

Nếu loader được hỏi thấy `demo.Selector` từ `shared.jar` trước theo chính sách và thứ tự classpath đang dùng, bản `Selector` trong `mr-demo.jar` **có thể không được xét**. Ngược lại, nếu nó chọn `mr-demo.jar`, khi đọc logical path `demo/Selector.class` thì MR-JAR chọn entry thích hợp với JVM version.

Tương tự, nếu custom loader kế thừa `ClassLoader.loadClass` mặc định, nó thường dùng thứ tự:

```text
findLoadedClass(name)
    ↓ chưa thấy
parent.loadClass(name) / bootstrap
    ↓ parent không tìm thấy
findClass(name)
    ↓
resolveClass(c) nếu được yêu cầu
```

Chính sách này là **parent-first**. Nếu parent đã định nghĩa `demo.Selector`, loader con thường không tự đọc biến thể `Selector` trong MR-JAR của nó.

**Hai tầng lựa chọn phải tách rõ:**

1. **ClassLoader nào** nhận và giải quyết yêu cầu class theo quy tắc delegation/classpath/module path?
2. **Bên trong MR-JAR đã được chọn**, entry versioned nào phù hợp với Java runtime?

Một loader tự viết `findClass` bằng `ZipFile.getEntry("demo/Selector.class")` chỉ đọc root có thể **bỏ qua** MR-JAR versioning. Loader muốn hỗ trợ MR-JAR phải sử dụng cơ chế đọc JAR phù hợp thay vì giả định bản root luôn là bytecode cuối cùng.

## 9. JarFile, JarEntry và “runtime view”

`java.util.jar.JarFile` là API đọc nội dung JAR. Với MR-JAR, cần phân biệt:

- **Physical view**: toàn bộ đường dẫn thực bên trong ZIP/JAR, gồm root và `META-INF/versions/11`, `META-INF/versions/17`.
- **Runtime-selected view**: khi lookup logical name `demo/Selector.class`, phiên bản cao nhất phù hợp với release được cấu hình sẽ được xem là entry cần dùng.

Theo Java API, một `JarFile` mở theo constructor thông thường **không nhất thiết tự áp dụng** MR-aware lookup cho Java runtime hiện tại; hành vi mặc định của `JarFile` có thể xem JAR như archive không versioned. Khi viết một class loader/JAR reader, có constructor và tham số `Runtime.Version` để yêu cầu nhìn theo một phiên bản mục tiêu, ví dụ `JarFile(File, boolean, int, Runtime.Version)`.

### Ví dụ đọc entry theo runtime version (Java 17+)

Tạo file `JarViewDemo.java`:

```java
import java.io.File;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

public final class JarViewDemo {
    private static void check(String path, int feature)
            throws Exception {
        Runtime.Version version = Runtime.Version.parse(
                Integer.toString(feature)
        );

        try (JarFile jar = new JarFile(
                new File(path),
                true,
                ZipFile.OPEN_READ,
                version
        )) {
            JarEntry entry = jar.getJarEntry("demo/Selector.class");
            System.out.println(feature + ": " + entry.getRealName());
        }
    }

    public static void main(String[] args) throws Exception {
        check("mr-demo.jar", 8);
        check("mr-demo.jar", 11);
        check("mr-demo.jar", 17);
        check("mr-demo.jar", 21);
    }
}
```

Giả sử `mr-demo.jar` được tạo đúng như mục 5, biên dịch/chạy chương trình này bằng JDK 21 (hoặc mới hơn) để có môi trường bao quát các mức 8, 11, 17, 21:

```bash
javac JarViewDemo.java
java JarViewDemo
```

Các đường dẫn thực được lựa chọn sẽ là:

```text
8: demo/Selector.class
11: META-INF/versions/11/demo/Selector.class
17: META-INF/versions/17/demo/Selector.class
21: META-INF/versions/17/demo/Selector.class
```

**Điểm cần phân biệt:** Ở ví dụ này, `JarFile` được **cấu hình view theo Runtime.Version** để đọc entry; nó **không khởi chạy code Java dưới nhiều JVM khác nhau**. Kết quả in cho feature 8 là base entry trong virtual lookup. Để chứng minh ứng dụng thực sự chạy ở Java 8, vẫn phải chạy `java -jar mr-demo.jar` với JRE/JDK 8.

Lưu ý: Tham số `Runtime.Version` không thể biến môi trường JVM hiện tại thành phiên bản khác hoặc giúp nạp bytecode mới trên JVM cũ. Đây là API để **chọn entry đọc** từ JAR, phục vụ công cụ phân tích/đóng gói/loader thích hợp.

## 10. Public API phải nhất quán giữa các biến thể

Đặc tả MR-JAR có ràng buộc đối với **public API** của các bản class. Nếu một class public/protected nằm trong một versioned directory của MR-JAR thông thường, nó phải “preside over” định nghĩa tương ứng có tên và mức truy cập thích hợp ở mức thấp hơn/root; các API công khai được kỳ vọng tương đương qua các phiên bản.

Điều này nhằm đảm bảo code của người dùng không phải compile lại theo từng runtime chỉ vì họ chuyển từ Java 11 lên 17.

### Ví dụ API ổn định — đúng hướng

Root:

```java
package api;

public final class Feature {
    public static String value() {
        return "fallback";
    }
}
```

Phiên bản ở `versions/17`:

```java
package api;

public final class Feature {
    public static String value() {
        return "optimized";
    }
}
```

Đây là **hai lựa chọn source thay thế nhau** có cùng binary name, không nên compile chung một output directory. Chúng giữ cùng public method signature và cùng đặc điểm lớp, chỉ khác implementation.

### Ví dụ API bị lệch — nguy hiểm

Root:

```java
package api;

public final class Feature {
    public static String value() {
        return "fallback";
    }
}
```

Versioned branch thay đổi thành:

```java
package api;

public final class Feature {
    // Đã xóa public static String value()!
    public static int valueAsInt() {
        return 1;
    }
}
```

Nếu ứng dụng được compile để gọi `Feature.value()` nhưng JVM chọn bản versioned không còn phương thức này, có thể gặp **`NoSuchMethodError`** lúc liên kết/sử dụng, dù class file major hợp lệ và entry nằm đúng nhánh. Không dùng MR-JAR để lén thay đổi signature của API công khai.

**Có ngoại lệ đối với non-exported packages trong modular multi-release JAR** theo đặc tả module, nhưng đó là trường hợp thiết kế có chủ đích. Đối với thư viện thông thường chạy trên classpath, giữ **API nhị phân công khai nhất quán** giữa mọi nhánh là nguyên tắc quan trọng.

Không dựa hoàn toàn vào `jar --validate` để chứng minh hợp đồng API: công cụ và runtime không bắt buộc kiểm chứng mọi điều kiện. Nên có kiểm tra API binary bằng test/tool chuyên dụng và test ứng dụng trên những JDK được hỗ trợ.

## 11. ClassLoader và định danh kiểu trong MR-JAR

Khi application ClassLoader định nghĩa `demo.Selector` từ `versions/17`, runtime class đó vẫn có binary name **`demo.Selector`**. Đường dẫn `META-INF/versions/17` là **địa chỉ của class file được chọn**, không phải một phần của binary name hoặc Java package.

Vì vậy:

```java
Class<?> selected = Class.forName("demo.Selector");
System.out.println(selected.getName()); // demo.Selector
```

Đây là **trích đoạn**; để chạy độc lập cần một `main` và xử lý `ClassNotFoundException` hoặc khai báo `throws`. `getName()` không trả `META-INF.versions.17.demo.Selector`.

Đối tượng `Class<?>` đại diện một runtime class do **defining loader** đã định nghĩa. Một runtime không nạp nhiều biến thể của cùng binary name vào **cùng defining ClassLoader** rồi thay đổi chúng khi phiên bản Java đã cố định. Một khi `demo.Selector` được định nghĩa, những lời gọi `loadClass` sau thông thường lấy lại class đó.

Nếu có **hai defining ClassLoader độc lập**, chúng có thể tạo hai runtime classes cùng tên từ hai JAR hay thậm chí hai cách xem bytecode. Chúng không tự thành một kiểu, nhưng đây là **cơ chế danh tính ClassLoader**, tách biệt với việc MR-JAR chọn phiên bản theo runtime.

Cũng cần phân biệt:
- **Loading**: ClassLoader/JVM tìm và định nghĩa runtime class từ bytes đã chọn.
- **Linking**: JVM kiểm tra/chuẩn bị và phân giải các tham chiếu khi cần.
- **Initialization**: JVM chạy các biểu thức/khối khởi tạo `static` khi có active use phù hợp.

MR-JAR **chọn class file được nạp**; nó không tự thay đổi quy tắc initialization của Java.

## 12. Java 8, classpath, module path và giới hạn tương thích

### 12.1. Java 8 nhìn thấy gì?

Java 8 **chưa có** cơ chế multi-release. Nó nhận `mr-demo.jar` như JAR thông thường và dùng các mục class ở **root**. Các file nằm bên dưới `META-INF/versions/11` hoặc `META-INF/versions/17` là các archive entries, không trở thành phiên bản được chọn bởi JVM 8.

Điều đó có nghĩa:

- Nếu cần hỗ trợ Java 8, các class bắt buộc để chạy ứng dụng phải có bản phù hợp Java 8 tại root.
- Root không thể sử dụng source/API chuẩn chỉ có ở Java 11/17.
- Không đặt entry point `Main.class` chỉ ở `versions/17` rồi tuyên bố Java 8 có thể chạy `java -jar`.
- Một dependency nằm bên ngoài JAR cũng phải tương thích Java 8; có root bytecode 52.0 thôi chưa đủ.

### 12.2. Java 9+ trên classpath

Với class loader chuẩn hỗ trợ MR-JAR, các phiên bản phù hợp được chọn tự động khi JAR nằm trên classpath. Người dùng không cần gọi riêng `Class.forName("META-INF.versions.17.demo.Selector")` — đây thậm chí không phải binary name của class.

Chạy:

```bash
java -cp mr-demo.jar demo.Main
```

Với JDK 17, chương trình tương đương lựa chọn `versions/17` như `java -jar mr-demo.jar` ở mục 5, cho stdout:

```text
Selected: java-17
```

### 12.3. Module path

MR-JAR có thể xuất hiện trên module path (automatic module hoặc explicit modular multi-release JAR) từ Java 9 trở lên, nhưng **module descriptor** cũng phải tuân theo những giới hạn đặc thù:

- `module-info.class` có thể ở root hoặc vị trí versioned theo quy tắc MR modular JAR.
- Descriptor versioned phải giữ tính nhất quán theo JAR specification; có một số ngoại lệ xác định đối với yêu cầu module nền tảng và `uses`.
- Public/protected classes trong các package **không export** có quy tắc “preside over” khác so với API export.

Đây là các yêu cầu **thêm** của hệ module. Ví dụ ở mục 5 là **classpath / executable JAR không có module-info**, để thấy rõ cơ chế chọn bytecode nền tảng.

### 12.4. Bootstrap loader không hỗ trợ MR-JAR lookup

Đặc tả lưu ý rằng **bootstrap class loader không hỗ trợ MR-JAR** theo cùng cách với class loaders thông thường. Nếu đưa MR-JAR vào bootstrap search path bằng cơ chế như `-Xbootclasspath/a` (trên JVM còn hỗ trợ), archive được xử lý như JAR bình thường. Không suy rộng ví dụ classpath ở mục 5 sang mọi vị trí nạp đặc biệt của JVM.

## 13. Các lỗi thường gặp và cách phát hiện

| Hiện tượng | Nguyên nhân có thể có | Cách xử lý |
|---|---|---|
| Java 17 vẫn dùng bản root | Thiếu `Multi-Release: true`, sai đường dẫn, không có đúng entry, loader không MR-aware hoặc parent chọn class khác | Kiểm tra manifest, `jar --list`, defining loader và classpath |
| Java 11 báo `UnsupportedClassVersionError` | Root hoặc nhánh 11 có bytecode vượt major được Java 11 hỗ trợ | Biên dịch với `javac --release 11` và xác minh major ≤55 |
| Java 8 không chạy JAR | Root chứa class mới hơn Java 8 hoặc thiếu class/dependency ở root | Biên dịch root với `--release 8`; test trên Java 8 |
| Java 21 chọn nhánh 17 | Không có bản 21 cho logical class đang xét | Đây là fallback đúng: lấy bản phù hợp cao nhất ≤21 |
| Class versioned có tên khác hoặc package khác | Entry path không tương ứng binary name | Đặt bản class đúng cùng package và tên như root |
| `jar --validate`/build báo API mismatch | Branch thay signature/modifiers/constructor công khai | Giữ API tương thích hoặc thiết kế lại các nhánh |
| `NoSuchMethodError` khi nâng JVM | Runtime chọn branch có API không khớp caller | So sánh public binary APIs và test trên runtime đích |
| Chỉ dùng root khi đọc bằng công cụ custom | Loader/JarFile mở physical view hoặc bỏ qua versioned entries | Chọn MR-aware lookup/`Runtime.Version` phù hợp |
| Debug qua `Class.getName()` không thấy phiên bản | Binary name không chứa `versions/N` | Xem code source/JAR entry hoặc runtime view đúng |
| Nhánh versioned chứa class chỉ hợp lệ với preview | JVM khác bản preview từ chối class dù chọn nhánh | Không phụ thuộc preview cho MR-JAR phát hành đa JDK |
| “Đã nâng JDK nhưng class cũ không đổi” | Class đã được loader định nghĩa; chưa khởi động lại/chưa thay loader | Khởi động lại process hoặc dùng cơ chế reload được thiết kế |

### Một chuỗi debug có hệ thống

1. Xác nhận **runtime** thực tế: `java -version` tại chính môi trường chạy.
2. Xác nhận JAR thực sự trên **classpath/module path** và không bị JAR khác cùng class name/parent loader che.
3. Liệt kê **physical entries** bằng `jar --list --file <jar>`.
4. Xem main manifest có **`Multi-Release: true`** và đường dẫn `META-INF/versions/N/...` đúng.
5. Xác nhận **entry được chọn** cho logical name ở runtime đó — bằng `JarFile` MR-aware, `javap --multi-release` hoặc chạy thử.
6. Xác nhận **class file major** của chính biến thể đã chọn và API binary với compile target.
7. Chạy test/smoke test trên **mỗi JDK tối thiểu mà thư viện tuyên bố hỗ trợ**, không chỉ JDK build.

Không sửa bằng cách đổi thủ công số `major_version` trong file class: các instruction, constant pool và API của bytecode có thể vẫn yêu cầu tính năng JVM mới.

## 14. Khi nào nên dùng và khi nào không cần?

MR-JAR hữu ích khi thư viện cần đồng thời:

- Một **baseline runtime cũ** còn được khách hàng sử dụng.
- Một hoặc nhiều **implementation tối ưu/tận dụng API mới** trên JDK 9+.
- Một artifact phân phối duy nhất giữ public API ổn định và cho JVM chọn tự động.

Ví dụ thư viện mã hóa, I/O, runtime diagnostics hoặc networking có thể có implementation riêng cho Java 11/17, trong khi API ứng dụng nhìn thấy giống nhau.

Tuy nhiên, MR-JAR làm tăng trách nhiệm maintain: nhiều build targets, nhiều bản bytecode cho cùng binary name, kiểm tra API và test matrix nhiều JDK. Nếu library chỉ hỗ trợ Java 17 trở lên và không cần implementation khác theo runtime, **một JAR thông thường biên dịch `--release 17` có thể đủ**. Không thêm các nhánh versioned chỉ để tăng số thư mục mà không có lợi ích học tập/vận hành rõ ràng.

### Quy trình release nên áp dụng

- Quy định **JDK tối thiểu** và danh sách **nhánh versioned thực sự cần**.
- Giữ root có đầy đủ API/implementation tối thiểu.
- Biên dịch **từng nhánh với `--release N`**, cách ly output directories.
- Đóng gói với `jar --release N` hoặc build task tạo đúng entry và manifest.
- Kiểm tra cấu trúc MR-JAR, class file versions và API consistency.
- Chạy smoke/integration test với các Java runtimes đại diện, đặc biệt **baseline**, **mỗi điểm chuyển nhánh**, **runtime mới nhất được hỗ trợ**.
- Cảnh giác các công cụ shading, repackaging hoặc fat-JAR layout có thể thay đổi entry paths/manifest. Sau khi xuất artifact cuối cùng, **đọc lại chính artifact phát hành** thay vì chỉ kiểm tra thư mục build trung gian.

## 15. Câu hỏi tự kiểm tra

1. **MR-JAR xuất hiện từ bản Java nào?** Java 9, theo JEP 238.
2. **Chỉ tạo `META-INF/versions/17` đã đủ kích hoạt chưa?** Chưa; cần main manifest attribute `Multi-Release: true`.
3. **Đang chạy Java 21, JAR có Selector ở root, versions/11 và versions/17. JVM dùng gì?** `versions/17/demo/Selector.class`.
4. **Đang chạy Java 10 nhưng JAR chỉ có versioned 11, 17 và root. JVM dùng gì?** Root.
5. **Nằm dưới `versions/17` có nghĩa class file major=17 không?** Không; Java 17 tương ứng major 61.
6. **Một class file major 65 đặt dưới `versions/17` có hợp lệ để Java 17 chạy không?** Không; Java 17 không hỗ trợ class file Java 21.
7. **`javac --release 11` và `jar --release 11` làm cùng một việc không?** Không; một cái chọn target biên dịch/API, một cái chọn directory trong MR-JAR.
8. **Java 8 có chọn `versions/11` nếu entry root không tồn tại không?** Không; nó không hỗ trợ multi-release lookup.
9. **Mỗi runtime chỉ chọn duy nhất một thư mục cho toàn JAR không?** Không; chọn phiên bản cao nhất phù hợp **riêng cho mỗi logical entry**.
10. **Cùng binary name `demo.Selector` từ root và versions/17 có tạo hai class khác nhau trong cùng defining ClassLoader khi load một lần không?** Không; loader chọn một entry cho runtime đó.
11. **Thiếu phiên bản 21 có khiến Java 21 không chạy được MR-JAR có versions/17 không?** Không; Java 21 có thể dùng bản 17 nếu tương thích.
12. **`jar --validate` thành công có bảo đảm mọi public API và dependency hoàn toàn tương thích không?** Không; cần thêm kiểm tra binary API và runtime tests.
13. **Đặt `META-INF/services/...` dưới `META-INF/versions/N/META-INF/services` có phải cách chuẩn để version service configuration không?** Không; tài nguyên thuộc `META-INF` không được versioned theo cơ chế đó.
14. **Nếu custom loader chỉ gọi `ZipFile.getEntry("demo/Selector.class")`, nó có tự chọn branch 17 không?** Không; đó là truy vấn đường dẫn vật lý root, phải dùng cơ chế MR-aware nếu muốn lựa chọn theo runtime.

## Tài liệu chuẩn

- [JEP 238 — Multi-Release JAR Files](https://openjdk.org/jeps/238): động cơ và thiết kế cơ chế nhiều phiên bản trong một JAR.
- [JAR File Specification — Multi-release JAR files, Java SE 26](https://docs.oracle.com/en/java/javase/26/docs/specs/jar/jar.html): cấu trúc `META-INF/versions/N`, manifest, thuật toán tìm, ràng buộc bytecode/API và quy tắc module.
- [The jar Command, JDK 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jar.html): cú pháp `jar --create`, `--release`, `--list` và `--validate`.
- [JarFile API, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/jar/JarFile.html): runtime version view và `getRealName()`.
- [javap command, JDK 21](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html): phân tích class file và tùy chọn multi-release.

**Điều quan trọng nhất:** Với MR-JAR hợp lệ, ClassLoader hỗ trợ cơ chế này sẽ tìm **entry theo logical name**, chọn bản versioned có **N cao nhất nhưng không vượt phiên bản JVM đang chạy**, và cuối cùng fallback về root. Bytecode được chọn vẫn phải tương thích với JVM; một JAR chứa nhiều bản không thay thế được việc biên dịch đúng target và kiểm thử trên từng runtime được hỗ trợ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-and-module-path-009">Phân biệt classpath, module path và đường dẫn native library</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt classpath, module path và đường dẫn native library

> Java Core → ClassLoader → Đường dẫn tìm kiếm. Ba loại đường dẫn này phục vụ ba cơ chế khác nhau: tìm Java classes/resources, tìm Java named modules và tìm native binaries. Không thể sửa lỗi của cả ba chỉ bằng cách thêm file vào một thư mục.

## 1. So sánh ba đường dẫn

| Loại | Artifact được tìm | Cấu hình thường dùng | Cơ chế |
| --- | --- | --- | --- |
| **Classpath** | Directory chứa cây `.class`, Java JAR và resources | `-cp`, `-classpath`, `--class-path` | ClassLoader và unnamed module |
| **Module path** | Exploded module, modular JAR, automatic-module JAR | `--module-path` hoặc `-p` | JPMS discovery, resolution và access graph |
| **Native library path** | `.dll`, `.so`, `.dylib` chứa mã máy | `-Djava.library.path=...`, `System.load`, `System.loadLibrary` | JNI/VM và OS dynamic loader |

~~~text
                         Java process
            +-----------------+------------------+
            |                 |                  |
       Classpath         Module path       Native search
     .class / JAR       Named modules       DLL/SO/dylib
            |                 |                  |
       ClassLoader        Module graph       OS/VM linker
~~~

Classloader nạp Java classes không tự động mở DLL chỉ vì DLL ở classpath. Ngược lại, `java.library.path` không dùng để tìm `.class`. Module path cũng không chỉ là classpath mang tên mới.

## 2. Classpath: Java classes và resources

Nếu class `demo.app.Main` được biên dịch vào `out/classes/demo/app/Main.class`, **classpath root** là `out/classes`, không phải `out/classes/demo/app`.

Linux/macOS:

~~~bash
java -cp "out/classes:libs/helper.jar" demo.app.Main
~~~

Windows PowerShell/CMD:

~~~powershell
java -cp "out/classes;libs/helper.jar" demo.app.Main
~~~

Phân cách classpath entries bằng **`:`** trên Unix và **`;`** trên Windows. `libs/*` mở rộng tới **JAR trực tiếp trong thư mục `libs`** khi Java launcher xử lý, không quét đệ quy. Luôn dùng binary class name (`demo.app.Main`), không dùng đường file của Main.class.

Classpath lúc biên dịch và lúc chạy là **hai cấu hình có thể khác nhau**: `javac -cp` dùng tìm kiểu lúc compile, `java -cp` dùng nạp class runtime. Nếu có JAR ở bước compile nhưng không có ở bước chạy, bạn có thể gặp `NoClassDefFoundError` hoặc `ClassNotFoundException` tùy tình huống.

- `CLASSPATH` là biến môi trường cho giá trị mặc định khi không có `-cp`; ưu tiên cấu hình explicit để dễ tái lập.
- Khi chạy main class qua classpath không cấu hình `-cp`/`CLASSPATH`, current directory thường là classpath mặc định.
- `java -jar app.jar` dùng `Main-Class` từ manifest. Trong launcher mode này, `-cp`/`CLASSPATH` không phải cách ghép thêm user classpath thông thường. Cần manifest `Class-Path`, distribution hợp lệ hoặc chạy với main class qua `-cp`.
- `System.getProperty("java.class.path")` không liệt kê toàn bộ class do custom loaders lấy từ plugin/network. Sửa property sau startup không buộc loader đọc lại classpath.

### 2.1. Unnamed module trên JDK 9+

Class từ classpath thuộc **unnamed module** của defining classloader; Java runtime vẫn chứa các named modules như `java.base`.

Tệp **ClasspathInfo.java**:

~~~java
public class ClasspathInfo {
    public static void main(String[] args) {
        Module current = ClasspathInfo.class.getModule();
        System.out.println("named=" + current.isNamed());
        System.out.println("name=" + current.getName());
        System.out.println("core=" + Object.class.getModule().getName());
    }
}
~~~

~~~bash
javac --release 17 ClasspathInfo.java
java -cp . ClasspathInfo
~~~

Stdout:

~~~text
named=false
name=null
core=java.base
~~~

`null` chỉ là tên của unnamed module, không phải lỗi nạp class.

### 2.2. Biên dịch library JAR và dùng nó từ classpath

Cây source:

~~~text
classpath-demo/
├─ src-lib/demo/lib/Greeter.java
└─ src-app/demo/app/Main.java
~~~

Tệp **src-lib/demo/lib/Greeter.java**:

~~~java
package demo.lib;

public final class Greeter {
    private Greeter() {}

    public static String message() {
        return "hello from classpath";
    }
}
~~~

Tệp **src-app/demo/app/Main.java**:

~~~java
package demo.app;

import demo.lib.Greeter;

public class Main {
    public static void main(String[] args) {
        System.out.println(Greeter.message());
        System.out.println(Greeter.class.getModule().isNamed());
    }
}
~~~

Từ thư mục classpath-demo (POSIX shell):

~~~bash
mkdir -p out/lib out/app dist
javac --release 17 -d out/lib src-lib/demo/lib/Greeter.java
jar --create --file dist/greeter.jar -C out/lib .
javac --release 17 -cp dist/greeter.jar -d out/app src-app/demo/app/Main.java
java -cp "out/app:dist/greeter.jar" demo.app.Main
~~~

Stdout:

~~~text
hello from classpath
false
~~~

Bước cuối là nơi xác định **runtime classpath**. Bỏ `dist/greeter.jar` khỏi lệnh Java có thể khiến Greeter không được tìm thấy lúc chạy dù `javac` đã thành công.

## 3. Module path: discovery và resolution của JPMS

Java 9 giới thiệu Java Platform Module System. `--module-path` chỉ cho module system nơi tìm module artifacts. Nó còn cần chọn **root modules**, theo `requires` để xây dựng dependency graph. Một JAR đặt trên module path **không tự động được resolve**.

Ba loại thường gặp:

- **Explicit named module**: có `module-info.class`; descriptor khai báo `requires`, `exports`, `opens`, services.
- **Automatic named module**: JAR không có descriptor nhưng ở module path; khi được resolve, module name có thể từ manifest `Automatic-Module-Name` hoặc tên file.
- **Unnamed module**: code ở classpath, không phải automatic named module, ngay cả khi JAR chứa manifest Automatic-Module-Name.

Điều kiện sử dụng named-module API:

1. **Resolution**: module cần ở graph.
2. **Readability**: caller module phải đọc module cung cấp.
3. **Exports**: package public API phải được export đến caller.
4. **Opens** khi cần deep reflection phù hợp module API; `opens` không đồng nghĩa `exports`.

Một classloader có thể định nghĩa nhiều modules; **loader parent delegation** và **module readability** là hai quan hệ độc lập.

### 3.1. Ví dụ hai named modules, Java 17+

Cấu trúc:

~~~text
src/
├─ demo.greeting/
│  ├─ module-info.java
│  └─ demo/greeting/Greeter.java
└─ demo.consumer/
   ├─ module-info.java
   └─ demo/consumer/Main.java
~~~

**src/demo.greeting/module-info.java**

~~~java
module demo.greeting {
    exports demo.greeting;
}
~~~

**src/demo.greeting/demo/greeting/Greeter.java**

~~~java
package demo.greeting;

public final class Greeter {
    private Greeter() {}

    public static String message() {
        return "hello from module";
    }
}
~~~

**src/demo.consumer/module-info.java**

~~~java
module demo.consumer {
    requires demo.greeting;
}
~~~

**src/demo.consumer/demo/consumer/Main.java**

~~~java
package demo.consumer;

import demo.greeting.Greeter;

public class Main {
    public static void main(String[] args) {
        System.out.println(Greeter.message());
        System.out.println(Greeter.class.getModule().getName());
        System.out.println(Main.class.getModule()
                .canRead(Greeter.class.getModule()));
    }
}
~~~

Biên dịch và chạy từ thư mục cha của src (POSIX shell):

~~~bash
mkdir -p mods/demo.greeting mods/demo.consumer
javac --release 17 -d mods/demo.greeting \
    src/demo.greeting/module-info.java \
    src/demo.greeting/demo/greeting/Greeter.java
javac --release 17 --module-path mods -d mods/demo.consumer \
    src/demo.consumer/module-info.java \
    src/demo.consumer/demo/consumer/Main.java
java --module-path mods --module demo.consumer/demo.consumer.Main
~~~

Stdout:

~~~text
hello from module
demo.greeting
true
~~~

`mods` chứa **hai exploded modules**, mỗi subfolder có module-info.class và package tree. Nếu xóa requires, consumer không đọc dependency; nếu xóa exports, public Greeter không truy cập như API cho consumer.

### 3.2. Kết hợp classpath và module path

~~~bash
java --module-path mods --class-path "legacy/*" \
    --module demo.consumer/demo.consumer.Main
~~~

Cấu hình hai đường đồng thời **không** khiến named module đọc toàn bộ unnamed module. Không thể viết `requires unnamed;` trong module descriptor. Khi cần migration, cân nhắc chuyển legacy JAR thành automatic/explicit named module hoặc dùng `--add-reads module=ALL-UNNAMED` với hiểu biết rõ về hạn chế.

Kiểm tra module:

~~~bash
java --list-modules
jar --describe-module --file libs/library.jar
java --show-module-resolution --module-path mods \
    --module demo.consumer/demo.consumer.Main
~~~

Các lệnh chỉ chạy khi JAR/modules ở ví dụ thực sự tồn tại. `java --list-modules` liệt kê system modules; `jar --describe-module` cho biết metadata/tên automatic module của JAR; `--show-module-resolution` cho phép theo dõi graph ở lần chạy.

## 4. Native library path: tìm binary của hệ điều hành

JNI/native code dùng shared library đã biên dịch cho **OS, kiến trúc CPU và ABI**. Logical name `codec` có mapping điển hình như `codec.dll` trên Windows, `libcodec.so` trên Linux và `libcodec.dylib` trên macOS. Tên thực được xác định bởi platform/JVM: hỏi `System.mapLibraryName("codec")`.

Hai API:

~~~java
System.loadLibrary("codec");                 // logical name
System.load("/opt/app/native/libcodec.so");  // absolute path Linux
~~~

Đây là snippet giải thích, không chạy nếu thiếu native binary thật.

- `System.loadLibrary` nhận **logical name**, không kèm thư mục hay extension.
- `System.load` nhận **đường dẫn tuyệt đối** tới native binary.
- JVM có thể gọi `ClassLoader.findLibrary(String)` của loader liên quan để nhận absolute path; nếu không, tìm theo native library paths có hiệu lực, như `java.library.path`.
- Đóng DLL/SO **bên trong JAR resource** chưa đủ: native loader không tự load file từ jar: URL. Một JNI wrapper có thể giải nén binary phù hợp ra file được kiểm tra và gọi `System.load(absolutePath)`.
- Native library gắn với các ràng buộc loader/lifecycle riêng; cùng binary cố load trong hai defining loaders khác nhau có thể nhận `UnsatisfiedLinkError`. `URLClassLoader.close()` không bảo đảm native library được unload tức thì.

Ví dụ Linux/macOS:

~~~bash
java -Djava.library.path=/opt/app/native -cp "app.jar:libs/*" com.acme.Main
~~~

Windows:

~~~powershell
java -Djava.library.path="C:\app\native" -cp "app.jar;libs/*" com.acme.Main
~~~

`-Djava.library.path` phải nằm **trước tên main class**. Đặt phía sau sẽ thành application argument. Đổi `System.setProperty("java.library.path", "...")` sau JVM startup **không bảo đảm** runtime cập nhật bảng tìm native.

### 4.1. JVM native lookup và OS dynamic linker là hai tầng

Tầng 1: JVM tìm `codec` theo `loadLibrary`/`load`. Tầng 2: OS loader tìm **dependent binaries** của `codec` như `libcrypto.so` hay DLL runtime khác. Linux có `LD_LIBRARY_PATH`/RPATH/RUNPATH; Windows có DLL search rules và PATH trong một số trường hợp; macOS có `@rpath`/loader paths/signing. Không thể suy ra chỉ cần `java.library.path` đúng thì mọi dependency native đều chắc chắn tìm được.

### 4.2. Ví dụ Java chỉ quan sát cấu hình

**NativePathView.java**

~~~java
public class NativePathView {
    public static void main(String[] args) {
        System.out.println("classpath=" +
                System.getProperty("java.class.path"));
        System.out.println("nativePath=" +
                System.getProperty("java.library.path"));
        System.out.println("mapped=" +
                System.mapLibraryName("codec"));
    }
}
~~~

~~~bash
javac --release 17 NativePathView.java
java -cp . NativePathView
~~~

Không có stdout toàn cục cố định: classpath, nativePath và mapped filename phụ thuộc OS/JDK/launcher. Mẫu này không thực sự load thư viện nào.

### 4.3. JNI class được nạp từ Java path, còn JNI binary là file khác

~~~java
public final class NativeBridge {
    static {
        System.loadLibrary("codec");
    }

    public static native int sum(int a, int b);
}
~~~

**NativeBridge.java** có thể biên dịch bằng:

~~~bash
javac --release 17 NativeBridge.java
~~~

Nhưng việc khởi tạo/gọi `sum` cần JNI binary thật có tên/ABI/symbol phù hợp. Class Java có thể có trên classpath/module path mà binary không có trên native search; lỗi khi đó là native loading/linking, không phải ClassNotFoundException đối với Java bridge class.

## 5. Native access restrictions từ JDK 24/25

Theo JEP 472, JDK 24+ kiểm soát restricted native operations như `System.load`/`System.loadLibrary`. JDK 25 mặc định cảnh báo cho native access chưa được cấp quyền; chế độ `deny` có thể ném `IllegalCallerException`.

Caller ở classpath/unnamed module:

~~~bash
java --enable-native-access=ALL-UNNAMED \
    -Djava.library.path=/opt/app/native \
    -cp "app.jar:libs/*" com.acme.Main
~~~

Caller ở named module:

~~~bash
java --enable-native-access=demo.nativeapp \
    --module-path mods \
    --module demo.nativeapp/demo.nativeapp.Main
~~~

**Tách biệt:** `--enable-native-access` cấp quyền; `java.library.path` tìm binary; `--module-path` tìm modules; `-cp` tìm Java class/resource. Bật quyền không làm xuất hiện file thiếu, và có file đúng path không tự cấp quyền. JDK 17/21 không có cùng chính sách/flag như JDK 25.

## 6. Resource lookup không phải native lookup

Một file `config/app.properties` đóng gói như resource thường đọc qua ClassLoader:

~~~java
try (var stream = Thread.currentThread()
        .getContextClassLoader()
        .getResourceAsStream("config/app.properties")) {
    if (stream == null) {
        throw new IllegalStateException("Missing resource");
    }
    // Xử lý stream tại đây.
}
~~~

Snippet này cần nằm trong method xử lý IOException. Nó tra classloader resources, không dùng `java.library.path`. Named module có thêm quy tắc resource encapsulation và `Module.getResourceAsStream`; resource không tìm thấy không đồng nghĩa Java class không load được.

## 7. Chẩn đoán theo đúng lỗi

| Lỗi | Tầng cần kiểm tra |
| --- | --- |
| `ClassNotFoundException` | Binary name, classpath, loader delegation |
| `NoClassDefFoundError` | Runtime dependency, linkage hoặc init failure trước đó |
| `FindException` “Module not found” | Module path, root modules, requires |
| `ResolutionException` | Module conflicts, split packages, readability graph |
| `IllegalAccessError`/`InaccessibleObjectException` | Exports, opens, module access |
| `UnsupportedClassVersionError` | Java runtime version và major bytecode |
| `UnsatisfiedLinkError` | Native filename/location, native ABI, missing dependency hoặc symbol |
| `IllegalCallerException` trên JDK mới | Native access policy của caller module |
| Chạy IDE được nhưng `java -jar` fail | Manifest/dependency distribution/classpath |

**Quy trình thực tế:** đọc lỗi đầu tiên và cause → xác định artifact thiếu (`.class`, named module hay DLL/SO) → ghi `java -version` và lệnh launch → kiểm tra đúng path/loader/module graph/native linker → sửa dependency packaging/version rồi chạy lại. Không đổi đồng thời cả ba paths theo phỏng đoán.

Lệnh hỗ trợ:

~~~bash
java -version
java --help
java --list-modules
jar --list --file libs/library.jar
jar --describe-module --file libs/library.jar
java --show-module-resolution --module-path mods \
    --module demo.consumer/demo.consumer.Main
~~~

Các ví dụ module/JAR trong lệnh cần được tạo hoặc thay bằng file thật. Nếu dùng HotSpot có unified logging, `java -Xlog:class+load=info ...` có thể cung cấp dấu vết class loading, nhưng định dạng log không phải hợp đồng JLS.

## 8. Checklist và câu hỏi ôn tập

- Classpath tìm Java class/resource trong unnamed module, module path làm module discovery/resolution, native path tìm binary của OS.
- Một JAR có mặt trên module path chưa chắc đã được **resolved**.
- Automatic module có tên; unnamed module từ classpath không có tên.
- Một loader thấy class chưa chắc module caller được phép truy cập package.
- Classpath separator Unix là `:`, Windows là `;`.
- `System.loadLibrary("codec")` không nhận nguyên tên `libcodec.so` làm argument.
- Đặt DLL trong JAR không tự khiến OS linker mở DLL.
- `java.library.path` không quyết định toàn bộ việc tìm dependent native libraries.
- Chạy `java -jar` không ghép thêm `-cp` theo cách launch main class.
- `--enable-native-access` ở JDK mới không thay `java.library.path`.
- Thay system properties sau startup không bảo đảm reload đường tìm của built-in loader.

## 9. Tài liệu chính thức

1. [Java launcher JDK 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html) — `--class-path`, `--module-path`, `--module`, `--enable-native-access`.
2. [javac JDK 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javac.html) — compiler paths.
3. [ClassLoader Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html) — class loading, resource lookup, `findLibrary`.
4. [System Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html) — `load`, `loadLibrary`, `mapLibraryName`.
5. [ModuleFinder Java SE 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/module/ModuleFinder.html) — explicit và automatic modules.
6. [JEP 261](https://openjdk.org/jeps/261) — Java Platform Module System.
7. [JEP 472](https://openjdk.org/jeps/472) — restricted JNI native access và cảnh báo từ JDK 24.

**Ghi nhớ:** Trước khi sửa đường tìm kiếm, phân loại đúng Java class, Java module hoặc native binary. Đây là ba cơ chế tách biệt, cần công cụ và phương án xử lý riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-and-module-path-010">Khác biệt giữa dependency của công cụ build và đường dẫn thực tế của JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Khác biệt giữa dependency của công cụ build và đường dẫn thực tế của JVM

## 1. Tình huống: khai báo dependency rồi mà JVM vẫn không tìm thấy lớp

Một ứng dụng Java khai báo thư viện trong Gradle `build.gradle` hoặc Maven `pom.xml`, biên dịch thành công, thậm chí các unit test đều chạy tốt. Nhưng khi lấy file JAR sang máy khác và chạy `java -jar app.jar`, ứng dụng có thể gặp:

~~~text
java.lang.NoClassDefFoundError: com/example/lib/Service
~~~

Điều này không mâu thuẫn. **Dependency do công cụ build quản lý** và **các class/JAR mà JVM có thể tìm lúc chạy** là hai lớp trách nhiệm khác nhau:

- **Build tool** (Gradle, Maven) đọc cấu hình, chọn phiên bản, tải artifacts, giải quyết phụ thuộc bắc cầu, hình thành các tập file cho biên dịch, kiểm thử, chạy task hoặc đóng gói.
- **Java compiler** (`javac`) kiểm tra mã nguồn với **compile classpath** (hoặc module path) được cung cấp.
- **Java launcher và ClassLoader** tìm bytecode khi chương trình chạy dựa trên **runtime classpath/module path, module resolution, manifest, cách đóng gói, launcher và các ClassLoader tùy chỉnh**. JVM không tự đọc `pom.xml`/`build.gradle` để tìm thư viện.
- **Artifact được phát hành** có thể chỉ chứa mã ứng dụng, hoặc đi kèm thư viện, hoặc có boot/shaded loader đặc biệt. Kết quả phụ thuộc task/plugin đóng gói thực tế.

**Mục tiêu bài học:** có khả năng xác định “thư viện có trong graph build” khác “class có thể tìm bởi loader lúc chạy” như thế nào; biết kiểm tra scope/configuration, lệnh launch, nội dung JAR/distribution và phiên bản thực tế; từ đó sửa đúng chỗ thay vì chỉ thêm dependency rồi đoán.

## 2. Bốn lớp cần theo dõi từ source đến runtime

~~~text
Khai báo build:
  implementation / compileOnly / runtimeOnly / api / Maven scope
                        |
                        v
Build tool resolve thành FILES và MODULES
  compileClasspath ----> javac
  runtimeClasspath ----> Gradle run / test task / packaging task (nếu được cấu hình)
                        |
                        v
Artifact và deployment thực tế
  classes + JAR riêng / app.jar + lib/*.jar / distribution / custom executable JAR
                        |
                        v
Lệnh khởi chạy JVM
  java -cp ... Main
  java -jar ... (manifest/launcher)
  java --module-path ... --module ...
                        |
                        v
ClassLoader tại runtime
  classpath URLs / resolved modules / parent delegation / custom loaders
                        |
                        v
Class<?> và các lỗi ClassNotFoundException / NoClassDefFoundError / LinkageError
~~~

Mỗi mũi tên là **một bước cấu hình khác nhau**. Artifact đã được tải vào Gradle cache hoặc Maven local repository **không** tự biến thành classpath của một tiến trình `java` độc lập. Tương tự, có JAR trong image/container chưa đủ: launcher và ClassLoader phải thực sự nhìn thấy nó.

### Các thuật ngữ cần phân biệt

| Thuật ngữ | Diễn giải |
|---|---|
| **Declared dependency** | Ý định của project trong build file, chưa khẳng định một JAR cụ thể đã được resolve |
| **Resolved dependency** | Artifact/version cụ thể được công cụ build chọn theo repositories, variant, scope, conflict rules |
| **Compile classpath** | Bộ lớp `javac` dùng để biên dịch source; có thể khác bộ file khi chạy |
| **Runtime classpath** | Bộ entries dành cho chạy ứng dụng ở một launch configuration nhất định |
| **Module path** | Đường dẫn modules cho JPMS, được dùng cùng module resolution/visibility, không đồng nghĩa classpath |
| **Artifact/distribution** | File JAR, directory, ZIP, image hoặc layout mà task build phát hành |
| **Actual JVM launch** | Command line, working directory, JVM options, môi trường, launcher script và class loader policies |
| **Defining ClassLoader** | Loader thực sự định nghĩa lớp; việc có file trên một path khác không buộc loader này nhìn thấy file đó |

Một dependency có thể xuất hiện trong **test runtimeClasspath** nhưng không có trong **production runtimeClasspath**. Một dependency cũng có thể có trong `runtimeClasspath` của Gradle nhưng không đi vào JAR thông thường khi bạn chỉ chạy task `jar`.

## 3. Gradle: configuration mô tả nhu cầu cho từng giai đoạn

Với project áp dụng plugin `java` hoặc `java-library` của Gradle, các configuration thường gặp là:

| Gradle configuration | Compile của mã chính | Runtime của mã chính | Ý nghĩa |
|---|---|---|---|
| `implementation` | Có | Có | Thư viện mã chính cần để biên dịch và chạy; mặc định nằm ngoài public compile API của consumers khi dùng `java-library` |
| `api` (**java-library**) | Có | Có | Thư viện nằm trong public API, được lộ tới compile classpath của consumer và cần ở runtime |
| `compileOnly` | Có | Không | Chỉ cần khi biên dịch; runtime phải có nguồn cung cấp nếu code thực sự sử dụng lúc chạy |
| `runtimeOnly` | Không | Có | Cần lúc chạy, không cần trực tiếp để biên dịch mã chính |
| `annotationProcessor` | Dùng cho tác vụ annotation processing | Không phải dependency runtime thông thường | Processor chạy trong giai đoạn biên dịch |
| `testImplementation` | Dành cho compile/runtime của test | Không phải dependency production runtime chỉ vì có chữ `test` | Thư viện dành cho mã kiểm thử |
| `testRuntimeOnly` | Không để compile test | Có trong test runtime | Phụ thuộc chỉ dùng lúc thực thi test |

Ví dụ với build Groovy DSL:

~~~groovy
plugins {
    id 'java'
}

dependencies {
    implementation 'org.example:api-client:1.2.0'
    compileOnly 'org.example:provided-api:1.2.0'
    runtimeOnly 'org.example:provider-impl:1.2.0'
    testImplementation 'org.example:testing-kit:1.2.0'
}
~~~

**Đây là mã mô tả cấu hình**, tọa độ `org.example` chỉ là ví dụ khái niệm, không được coi là thư viện tồn tại để download. Hãy thay bằng dependency có thật trong dự án của bạn.

Gradle dùng các configuration có thể resolve, đặc biệt:

~~~text
compileClasspath  <= implementation + compileOnly (và các quan hệ phù hợp của plugin)
runtimeClasspath  <= implementation + runtimeOnly
test...Classpath  <= testImplementation / testRuntimeOnly và dependencies được kế thừa
~~~

Đây là **mô hình chính của Java plugin**. Plugin bổ sung và custom configurations có thể thay đổi dependency graph thực tế. Để xác minh, đọc output của configuration/task thật thay vì suy luận chỉ từ chữ `implementation`.

### Lệnh Gradle kiểm tra

Trên Linux/macOS:

~~~sh
./gradlew dependencies --configuration compileClasspath
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencyInsight --dependency api-client --configuration runtimeClasspath
~~~

Trên Windows PowerShell/CMD, dùng `gradlew.bat` tương ứng:

~~~powershell
.\gradlew.bat dependencies --configuration compileClasspath
.\gradlew.bat dependencies --configuration runtimeClasspath
.\gradlew.bat dependencyInsight --dependency api-client --configuration runtimeClasspath
~~~

`dependencies` cho thấy graph ở configuration yêu cầu. `dependencyInsight` giúp tìm **vì sao** một dependency/version được chọn. Cả hai chứng minh resolution phía build; **không tự chứng minh** artifact đã được copy vào Docker image hoặc classpath của lệnh `java` ngoài Gradle.

### `run` và `jar` không giống nhau

- Plugin `application` của Gradle có task `run` dùng runtime classpath ứng dụng đã cấu hình; distribution tasks có thể mang dependencies đi kèm trong layout riêng.
- Task `jar` của Java plugin về cơ bản tạo JAR chứa classes/resources của project, **không tự gộp toàn bộ các JAR `implementation` và `runtimeOnly`** thành một fat/uber JAR.
- Nếu bạn mang **chỉ** JAR kết quả của `jar` sang máy khác rồi chạy bằng `java -jar` hoặc `-cp` mà thiếu các dependencies bên ngoài, khả năng runtime tìm thấy class sẽ khác lúc dùng `gradlew run`.
- Nếu dùng task/plugin đóng gói khác (shadow, bootJar, distribution, container plugin...), phải kiểm tra hợp đồng và nội dung **artifact đó**. Không đồng nhất behavior của các task.

Một trường hợp thường gặp: `compileOnly` hợp lý với Servlet API do servlet container cung cấp; chạy standalone mà không có container thì class có thể thiếu. Mặt khác, thay mọi dependency thành `implementation` không bảo đảm plain JAR tự chứa chúng.

## 4. Maven: dependency scope không phải đường dẫn JVM tự sinh

Một `pom.xml` có thể khai báo:

~~~xml
<dependencies>
  <dependency>
    <groupId>org.example</groupId>
    <artifactId>core-lib</artifactId>
    <version>1.0.0</version>
  </dependency>

  <dependency>
    <groupId>org.example</groupId>
    <artifactId>container-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
  </dependency>

  <dependency>
    <groupId>org.example</groupId>
    <artifactId>driver</artifactId>
    <version>1.0.0</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
~~~

**Đây là cấu hình minh họa**, các tọa độ `org.example` không được bảo đảm có artifact thật. Nó làm rõ ba ý nghĩa scope chứ không phải dự án mẫu để chạy Maven.

| Maven scope | Compile | Runtime của mã chính | Test | Lưu ý |
|---|---|---|---|---|
| `compile` (mặc định) | Có | Có | Có | Dùng phổ biến; dependency có thể truyền qua dependency graph tùy quy tắc transitivity |
| `provided` | Có | Không | Có | Giả định môi trường/JDK/container cung cấp lúc chạy |
| `runtime` | Không | Có | Có | Ví dụ JDBC driver khi mã ứng dụng dùng API chuẩn để kết nối |
| `test` | Không | Không | Có | Không dành cho runtime production |

Các scope khác như `system`/`import` phục vụ ngữ cảnh đặc biệt; không xem chúng là runtime classpath mặc định của một chương trình `java`. Các quy tắc dependency bắc cầu và dependency management có thể làm version/artifact thực sự khác mong đợi.

**Điểm quyết định:** Maven `compile` có nghĩa artifact là ứng viên cho **classpath của các tác vụ Maven**. Task đóng gói thông thường vẫn có thể sinh một JAR chỉ chứa lớp của chính project. Việc chạy một plain JAR bằng launcher độc lập cần runtime dependencies được phát hành/tham chiếu riêng hoặc bundling theo plugin cấu hình.

### Kiểm tra Maven

~~~sh
mvn dependency:tree
mvn dependency:tree -Dscope=runtime
~~~

Cây dependency trả lời “Maven chọn những dependencies nào” theo scope/filter. Muốn biết **artifact deploy cuối** chứa gì, phải inspect `target/*.jar`, thư mục distribution hoặc layout container. Đừng suy luận packaging chỉ bằng việc dependency xuất hiện trong `dependency:tree`.

## 5. Thực hành Gradle: task `run` khác plain `jar`

Ví dụ Java thuần `javac`/`java` đã có trong bài **Classpath lúc biên dịch khác classpath lúc chạy**. Để thấy vai trò riêng của build tool, thí nghiệm này sử dụng **cùng code và cùng dependency**, nhưng chạy qua `gradle run` rồi so với `java -jar` của artifact do task `jar` tạo.

### 5.1. Chuẩn bị dự án, không cần download thư viện từ internet

Tạo cấu trúc:

~~~text
build-path-demo/
├── settings.gradle
├── build.gradle
├── helper-src/demo/lib/Greeting.java
└── src/main/java/demo/app/Main.java
~~~

**`settings.gradle`:**

~~~groovy
rootProject.name = 'build-path-demo'
~~~

**`helper-src/demo/lib/Greeting.java`:**

~~~java
package demo.lib;

public final class Greeting {
    private Greeting() {}

    public static String message() {
        return "LIB_READY";
    }
}
~~~

**`src/main/java/demo/app/Main.java`:**

~~~java
package demo.app;

import demo.lib.Greeting;

public final class Main {
    public static void main(String[] args) {
        System.out.println("mainLoaded=true");
        System.out.println("library=" + Greeting.message());
    }
}
~~~

**`build.gradle` (Groovy DSL):**

~~~groovy
plugins {
    id 'application'
}

application {
    mainClass = 'demo.app.Main'
}

dependencies {
    implementation files('lib/greeting.jar')
}

tasks.named('jar') {
    manifest {
        attributes('Main-Class': 'demo.app.Main')
    }
}
~~~

Thư viện được tạo cục bộ bằng JDK 17+ tại root của ví dụ:

~~~bash
mkdir -p out/helper lib
javac --release 17 -d out/helper helper-src/demo/lib/Greeting.java
jar --create --file lib/greeting.jar -C out/helper .
~~~

Trên PowerShell, thay `mkdir -p` bằng `New-Item -ItemType Directory -Force out/helper,lib | Out-Null`; các lệnh Java giữ nguyên. Dùng `jar --list --file lib/greeting.jar` để kiểm tra entry `demo/lib/Greeting.class`.

### 5.2. Một dependency, hai đường chạy

Với Gradle được cài đặt hoặc wrapper có sẵn (khi có wrapper, dùng `./gradlew` thay `gradle`):

~~~bash
gradle run
gradle jar
java -jar build/libs/build-path-demo.jar
~~~

Task `run` dùng runtime classpath của plugin `application`, nên stdout ứng dụng có:

~~~text
mainLoaded=true
library=LIB_READY
~~~

Mặc dù `implementation files('lib/greeting.jar')` đưa thư viện vào `compileClasspath` và `runtimeClasspath`, task `jar` mặc định **chỉ đóng gói classes/resources của chính project**. Bản plain JAR ở `build/libs/build-path-demo.jar` có manifest `Main-Class`, nhưng không nhúng `Greeting.class` và cũng không có `Class-Path` manifest tới `greeting.jar`. Khi chạy bằng `java -jar`, ứng dụng có thể gặp `NoClassDefFoundError: demo/lib/Greeting`. Thời điểm resolve và chi tiết stack trace phụ thuộc JVM; đọc lỗi gốc thay vì dự đoán mọi dòng output.

Hãy chạy cùng JAR với classpath rõ ràng:

~~~bash
# Linux/macOS
java -cp "build/libs/build-path-demo.jar:lib/greeting.jar" demo.app.Main
~~~

~~~powershell
# Windows
java -cp "build/libs/build-path-demo.jar;lib/greeting.jar" demo.app.Main
~~~

Output lại có `mainLoaded=true` và `library=LIB_READY`. Source và dependency không đổi; **packaging và lệnh khởi chạy** mới là biến số của thí nghiệm.

### 5.3. Truy nguyên đúng tầng

| Câu hỏi | Kiểm tra |
| --- | --- |
| Gradle có resolve library cho runtime? | `gradle dependencies --configuration runtimeClasspath` |
| Task `run` có thấy library? | `gradle run` và dòng `LIB_READY` |
| Plain JAR thực sự chứa gì? | `jar --list --file build/libs/build-path-demo.jar` |
| JVM được cấu hình classpath ra sao? | So sánh `java -jar ...` với `java -cp ... demo.app.Main` |

Với Spring Boot/Shadow/application distribution hoặc Docker, việc đóng gói và launcher có thể khác. Cần xem artifact phát hành thật, thay vì giả định task nào cũng tạo fat JAR.

## 6. JVM tìm lớp qua những con đường nào?

### 6.1. Classpath của launcher

Khi chạy:

~~~sh
java -cp "out/classes:lib/a.jar:lib/b.jar" demo.app.Main
~~~

Trên hệ dùng dấu `:`, các mục cách nhau bởi dấu này. Một mục có thể là **thư mục gốc chứa hierarchy package**, JAR/ZIP, hoặc mục phù hợp theo hợp đồng launcher. Trên Windows dùng dấu `;`.

- Với class tên `demo.lib.Greeting` và classpath root là `out/classes`, JVM mong có đường tương ứng `out/classes/demo/lib/Greeting.class`.
- File `lib/a.jar` chỉ hỗ trợ tra cứu khi nó là entry được launcher hoặc ClassLoader sử dụng.
- Thư mục chứa `a.jar` không đồng nghĩa JAR đó đã được thêm vào classpath. Với cú pháp wildcard classpath `lib/*`, launcher Java mở rộng tới các JAR có đuôi .jar/.JAR trực tiếp trong thư mục; không nên giả định mọi thư mục con đều được quét đệ quy.
- Khi tự tạo `URLClassLoader`, nó có thể dùng URL khác hoàn toàn với classpath của launcher.

### 6.2. ClassLoader có thể ủy quyền và cô lập

Ba tên quen thuộc — bootstrap, platform và application/system — chỉ mô tả nhóm loader tích hợp. Framework có thể tạo loader riêng cho plugin, servlet application, module layer hoặc dynamic loading.

Vì thế:

- `System.getProperty("java.class.path")` thường giúp xem user classpath do launcher đặt, **không** là danh sách đầy đủ của mọi URL/module mà mọi loader tùy chỉnh có thể dùng.
- `SomeClass.class.getClassLoader()` xác định **defining loader** của class; với một số class nền tảng được bootstrap định nghĩa, kết quả là `null` hợp lệ.
- Một JAR có mặt trong process file system chưa chắc loader **đang yêu cầu** có quyền tìm tới.
- Hai loader khác nhau có thể định nghĩa hai class cùng binary name, dẫn tới khác type identity; không nên “sửa” bằng cách copy JAR vào mọi nơi mà không xét cơ chế cô lập.

### 6.3. Module path không phải classpath thứ hai đơn giản

Nếu dùng JPMS/named modules, launcher thường có dạng:

~~~sh
java --module-path mods --module com.example.app/com.example.app.Main
~~~

Các module được tìm/resolved theo root modules, `requires` và cấu hình; có JAR trên module path không có nghĩa **mọi** module trong thư mục sẽ được resolve. Các quy tắc `exports`/`opens`/readability còn ảnh hưởng đến truy cập sau khi tìm được class.

Ví dụ dạng phân biệt:

~~~text
build tool resolves JAR
  -> được chọn cho module path?
  -> module có được resolve?
  -> class thuộc module nào?
  -> module ánh xạ tới loader nào?
  -> package/class có quyền truy cập?
~~~

Vì vậy, nếu classpath đã đúng nhưng ứng dụng modular vẫn báo lỗi access hoặc module không được resolve, cần kiểm tra JPMS riêng thay vì tiếp tục thêm mục vào `-cp`.

## 7. Phụ thuộc bắc cầu, phiên bản và cách đóng gói

### 7.1. Dependency bắc cầu không chắc đi vào mọi artifact

Ví dụ:

~~~text
app -> client.jar -> codec.jar
~~~

Build tool có thể resolve cả `client.jar` và `codec.jar` vào runtime configuration nhờ metadata. Nhưng khi tự chạy:

~~~sh
java -cp "app.jar:client.jar" demo.app.Main
~~~

và `client.jar` thật sự cần `codec.jar`, runtime vẫn có thể thiếu `codec.jar`. **Classpath do bạn ghi không tự động đọc lại toàn bộ dependency metadata của Maven/Gradle để tải file còn thiếu.**

Cần đưa đủ artifact cần thiết vào runtime classpath/module path hoặc dùng distribution/packaging có hỗ trợ.

### 7.2. Compile version và runtime version có thể khác

Ví dụ build compile với `client-v2.jar` chứa method `Client.newFeature()`. Runtime lại dùng `client-v1.jar` có class `Client` nhưng **thiếu method đó**. ClassLoader vẫn có thể tìm được class, nhưng lúc gọi method sẽ có thể ném `NoSuchMethodError`.

| Triệu chứng | Một hướng nguyên nhân | Việc kiểm chứng |
|---|---|---|
| `ClassNotFoundException` | Lookup theo tên không tìm thấy class | Runtime entries, binary name, loader |
| `NoClassDefFoundError` | JVM không có định nghĩa class cần thiết, hoặc trạng thái lỗi initialization trước đó | Cause/stack trace; runtime classpath, initializer, version |
| `NoSuchMethodError` / `NoSuchFieldError` | Binary ABI mismatch giữa compile và runtime | Version/JAR thực tế, chữ ký member trong class |
| `UnsupportedClassVersionError` | Classfile được biên dịch cho JVM mới hơn runtime | `java -version`, target của compiler |
| `ClassCastException` khi class names giống nhau | Khác defining loader hoặc cast logic sai | Đối tượng `Class` và defining loader thực tế |
| `ServiceConfigurationError` | Provider declaration/class/dependency của `ServiceLoader` lỗi | Metadata `META-INF/services`/`provides` và visibility runtime |

**Không suy luận tất cả `NoClassDefFoundError` đều do thiếu JAR.** Một lớp đã thất bại trong static initialization cũng có thể khiến các lần sử dụng sau báo lỗi dạng này. Đọc `Caused by` và lỗi đầu tiên trong log.

### 7.3. Plain JAR, fat JAR, executable JAR và distribution

- **Plain JAR:** thường chứa classes/resources của project; dependencies ở JAR khác.
- **Fat/uber/shaded JAR:** build process đưa các classes/dependencies vào cùng một archive theo quy tắc cụ thể; có thể có đổi package (*relocation*) hoặc xung đột resource nếu cấu hình sai.
- **Executable JAR của framework:** có thể chứa nested libraries và dùng **custom launcher/ClassLoader** để tìm chúng; không nên giả định `java -cp framework.jar SomeClass` có cùng hiệu ứng với `java -jar framework.jar`.
- **Application distribution:** có thể gồm thư mục `lib/`, launcher script và file cấu hình; chính script tạo classpath khi chạy.
- **Container image:** phải chứa đủ các artifacts và dùng đúng `ENTRYPOINT`/`CMD` hoặc script; image copy thiếu `lib/` vẫn làm lỗi dù build local thành công.

Không có cách suy luận chắc chắn “dependency có trong build file nên có trong output” mà không biết task đóng gói cụ thể.

## 8. Quy trình kiểm tra đúng đường dẫn JVM đang dùng

Khi lỗi xảy ra sau deployment, thực hiện theo thứ tự:

1. **Xác định lệnh launch thật:** `java -cp` hay `java -jar`? Có `--module-path`/`--module`? Process chạy từ thư mục nào? Nếu dùng launcher script, container hoặc IDE, kiểm tra command mà chúng sinh ra.
2. **Đối chiếu compile/runtime dependencies:** trong Gradle kiểm tra `compileClasspath` vs `runtimeClasspath`; trong Maven xem dependency scopes và effective resolution.
3. **Xem artifact thực tế:** `jar --list --file ...` và layout thư mục deploy; library đã được copy/chèn vào JAR đúng cách hay chưa?
4. **So binary name và đường dẫn:** package path trong directory/JAR có đúng như tên class đang bị báo lỗi?
5. **Kiểm tra đúng JVM/JDK và version:** `java -version`, compiler release target, metadata của JAR dependency, sự khác nhau local/container.
6. **Xác định loader đang dùng:** custom ClassLoader, classloader isolation, plugin/servlet container có thể bỏ qua user classpath hoặc dùng parent khác.
7. **Nếu dùng named modules:** kiểm tra module path, resolution, readability, `exports` và class nằm trong module nào.
8. **Thu hẹp thành một lệnh tái hiện:** chạy với classpath tối thiểu, thêm đúng dependency còn thiếu, và so kết quả. Giữ nguyên source để xác định lỗi nằm ở runtime wiring.

### Chẩn đoán bằng câu lệnh và API

Trên JVM có hỗ trợ unified logging (ví dụ HotSpot 17+), có thể thử:

~~~sh
java -Xlog:class+load=info -cp "build/app:build/lib.jar" demo.app.Main
~~~

Log giúp quan sát **class nào được load từ nguồn nào**; khối lượng rất lớn nên nên lọc theo tên class cần điều tra. Đầu ra log khác nhau giữa runtime/vendor nên **không** dùng nó làm stdout cố định của bài.

Trong một phương thức Java, có thể dùng:

~~~java
System.out.println(System.getProperty("java.class.path"));
System.out.println(demo.app.Main.class.getClassLoader());
System.out.println(demo.app.Main.class.getModule().getName());
~~~

Đây là **mã minh họa**, không phải chương trình độc lập. `java.class.path` không liệt kê đầy đủ classpath của mọi custom loader. `getModule().getName()` có thể `null` với unnamed module; `getClassLoader()` có thể `null` với bootstrap-defined class. Đó là các giá trị hợp lệ cần giải thích theo ngữ cảnh.

### Bảng quyết định thực tế

| Quan sát | Suy luận có cơ sở | Bước tiếp theo |
|---|---|---|
| Có dependency trong `compileClasspath`, không có trong `runtimeClasspath` | Scope/configuration không đưa library vào runtime của mã chính | Xem `compileOnly`/`provided` và nguồn cung cấp từ môi trường |
| Có trong `runtimeClasspath`, nhưng không có trong deploy | Packaging/distribution/CI chưa đưa artifact vào output | Kiểm tra task tạo JAR/image và bước copy/upload |
| Có trong thư mục deploy nhưng launcher không thấy | Lệnh `-cp`/manifest/working directory không tham chiếu đúng | Sửa launcher hoặc distribution script |
| Có class trong JAR nhưng vẫn `ClassNotFoundException` | Loader đang tra cứu khác hoặc module/path/name không đúng | In loader, binary name, URL/module layout |
| Tìm được class nhưng lỗi `NoSuchMethodError` | Khả năng cao runtime đang dùng version không tương thích | Kiểm tra artifact/version thực sự được nạp |
| Chạy với Gradle task `run` được, `java -jar` lỗi | Runtime wiring của task khác executable JAR/deploy | So sánh classpaths và launcher/packaging |
| Local chạy tốt nhưng container lỗi | File copy, workdir, launch hoặc Java version khác | Kiểm tra image filesystem và runtime command |

## 9. Những ngộ nhận cần tránh

1. **“Có `implementation` là JAR ứng dụng chứa luôn dependency.”** Không đúng với plain `jar`; runtime configuration và packaging là hai bước khác.
2. **“`compileOnly` chạy thành công lúc compile thì runtime cũng chắc chắn có.”** Không đúng: dependency đã bị chủ động loại khỏi runtime classpath của mã chính.
3. **“Maven `provided` tự cài thư viện vào server.”** Không đúng: scope chỉ biểu thị kỳ vọng môi trường cung cấp; bạn vẫn phải bảo đảm môi trường có thư viện phù hợp.
4. **“`testImplementation` làm dependency sẵn có ở production.”** Không đúng theo cấu hình Java plugin thông thường.
5. **“`gradlew dependencies` chứng minh image đã đóng gói JAR.”** Chỉ chứng minh graph theo configuration, không phải artifact deploy.
6. **“Chạy `java -jar app.jar -cp lib.jar` sẽ thêm thư viện.”** Không đúng: tham số sau tên JAR đi vào `args` của main; `-jar` và app classpath phải được cấu hình theo hợp đồng launcher.
7. **“Thêm JAR vào cùng thư mục là JVM tự tìm thấy.”** Không đúng nếu launcher không tham chiếu nó qua classpath/manifest/loader hoặc cơ chế phù hợp.
8. **“Mọi lỗi class đều là thiếu file.”** Có thể là version mismatch, lỗi initialization, quyền truy cập JPMS hoặc classloader identity.
9. **“`java.class.path` chứa mọi class mà JVM có thể nhìn thấy.”** Không đúng với bootstrap/platform modules hoặc custom loaders.
10. **“Đưa cùng tên class vào nhiều ClassLoader chỉ làm nó dễ tìm hơn.”** Có thể tạo hai runtime types khác nhau, dẫn tới `ClassCastException`/`LinkageError`.
11. **“`--module-path` nghĩa là mọi JAR đã được load.”** Module resolution và class loading còn là các bước riêng.
12. **“Build trên JDK mới thì runtime JDK cũ vẫn chạy miễn classpath đúng.”** Có thể thất bại vì classfile version, API không tồn tại hoặc module compatibility.

## 10. Bài tập vận dụng

1. Gradle project khai báo `compileOnly 'org.example:api:1.0'`. Code compile, nhưng chạy standalone bị `NoClassDefFoundError`. Bạn kiểm tra configuration nào đầu tiên và vì sao?
2. Vì sao `./gradlew run` chạy được trong khi `java -jar build/libs/app.jar` thiếu class, dù không đổi source?
3. Maven `scope=provided` có đồng nghĩa JAR đóng gói xong luôn chứa thư viện không? Môi trường nào thường cung cấp dependency đó?
4. Trong thực hành, tại sao `gradle run` thấy `Greeting` nhưng `java -jar build/libs/build-path-demo.jar` lại thiếu thư viện?
5. Viết runtime classpath gồm `build/libs/build-path-demo.jar` và `lib/greeting.jar` trên Linux và Windows; dấu phân cách khác nhau thế nào?
6. Vì sao `java -jar build/app.jar` vẫn thiếu library sau khi đã tạo manifest có `Main-Class`?
7. Nếu project phụ thuộc A rồi A phụ thuộc B, tại sao copy mỗi A.jar sang server vẫn có thể không đủ?
8. Nếu class tìm thấy nhưng method vắng mặt, vì sao nên kiểm tra `NoSuchMethodError` và phiên bản runtime?
9. Cách nào kiểm tra JAR chứa class cụ thể? Cách nào kiểm tra Gradle đã resolve library cho runtime?
10. Vì sao `Class.getClassLoader()` và `Module.getName()` giúp phân tích vấn đề mà `System.getProperty("java.class.path")` không giải thích hết?
11. Một app chạy từ module path gặp lỗi `exports`; chỉ thêm dependency vào build file đã giải quyết chắc chắn chưa?
12. Với container image, hãy mô tả ba thứ phải khớp: file đã copy, runtime command và dependency/loader visibility.

**Tiêu chí hoàn thành:** chạy chính xác hai trường hợp của ví dụ Java 17+; giải thích được lý do compile thành công nhưng runtime thiếu class; phân biệt configuration build, artifact packaging, launcher và defining loader; biết chọn phép kiểm tra đúng khi lỗi phát sinh trên máy deploy.

## 11. Tài liệu chính thức

- [Gradle Java Plugin — Dependency management](https://docs.gradle.org/current/userguide/java_plugin.html): `compileClasspath`, `runtimeClasspath` và các dependency configurations của mã chính/test.
- [Gradle Java Library Plugin](https://docs.gradle.org/current/userguide/java_library_plugin.html): phân biệt `api`, `implementation` và việc công bố dependency cho consumers.
- [Maven — Introduction to the Dependency Mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html): các scope `compile`, `provided`, `runtime`, `test` và dependency transitivity.
- [Java Launcher — `java`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): `-classpath`, `-jar`, `--module-path`, `--module` và cơ chế khởi chạy.
- [JAR File Specification](https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html): manifest `Main-Class`, `Class-Path` và cấu trúc archive.
- [Java SE 21 — `ClassLoader` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): delegation, defining loaders và việc tìm lớp.

Bài học tập trung vào **đường đi từ dependency của build tool đến khả năng tìm lớp ở runtime**. Ví dụ dùng Java chuẩn, không giả định một framework, plugin đóng gói hoặc CI cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)
