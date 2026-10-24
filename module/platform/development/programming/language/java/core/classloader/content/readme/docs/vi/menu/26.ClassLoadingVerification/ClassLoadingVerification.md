<a id="back-to-top"></a>

# Kiểm chứng hành vi

## Menu
- [Tạo fixture có cùng tên lớp trong hai JAR](#class-loading-verification-001)
- [Kiểm chứng thứ tự ủy quyền và định danh Class](#class-loading-verification-002)
- [Kiểm chứng loadClass khác Class.forName về khởi tạo](#class-loading-verification-003)
- [Kiểm chứng thứ tự static initializer và lỗi khởi tạo](#class-loading-verification-004)
- [Kiểm chứng package-private khi dùng hai loader](#class-loading-verification-005)
- [Kiểm chứng tìm tài nguyên và provider dịch vụ](#class-loading-verification-006)
- [Kiểm chứng nạp đồng thời và xử lý tên lớp trùng](#class-loading-verification-007)
- [Kiểm chứng vòng đời đóng loader, dừng plugin và giải phóng tham chiếu](#class-loading-verification-008)
- [Tách từng kịch bản bằng loader hoặc tiến trình JVM mới](#class-loading-verification-009)

## <a id="class-loading-verification-001">Tạo fixture có cùng tên lớp trong hai JAR</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo fixture có cùng tên lớp trong hai JAR để kiểm chứng ClassLoader

## 1. Vì sao cần một fixture có class trùng tên?

Trong ứng dụng Java lớn, hai phiên bản của một thư viện có thể cùng đóng gói `com.example.Library.class`. Vấn đề quan trọng khi chẩn đoán không chỉ là “cả hai JAR đều chứa lớp” mà còn là **JVM đã chọn định nghĩa từ nguồn nào, qua ClassLoader nào và theo thứ tự nào**.

Nếu kiểm thử trên các JAR của dự án thực, kết quả dễ bị ảnh hưởng bởi dependency graph, parent delegation, module path, manifest `Class-Path`, wildcard classpath, build cache hoặc thứ tự classpath tình cờ. Một **fixture** là bộ đầu vào nhỏ, có thể tái tạo: ta tự tạo **hai JAR độc lập chứa cùng binary name**, nhưng nội dung lớp trả về hai kết quả phân biệt. Sau đó dùng các ClassLoader được cấu hình rõ ràng để kiểm tra từng giả thuyết.

Fixture của bài học:

```text
A.jar
├── fixture/same/Versioned.class
│   └── origin() trả "A"
└── fixture/label.txt
    └── A

B.jar
├── fixture/same/Versioned.class
│   └── origin() trả "B"
└── fixture/label.txt
    └── B
```

**Tên nhị phân** của cả hai bản là `fixture.same.Versioned`. Tên file entry trong JAR của cả hai là `fixture/same/Versioned.class`. Chỉ có **thân method** và tài nguyên marker khác, giúp xác minh nguồn được chọn mà không phải dựa vào tên JAR hay timestamp.

Mục tiêu kiểm chứng:

1. **Tạo đúng hai JAR thật**, mỗi JAR chứa cùng một class entry.
2. Nạp class qua `URLClassLoader` có URL order `[A.jar, B.jar]` và `[B.jar, A.jar]`.
3. Chứng minh URL order khác nhau có thể chọn **hai bản method khác nhau** khi parent không có class.
4. Chứng minh mỗi loader độc lập tạo **runtime class identity** riêng ngay cả khi binary name giống nhau.
5. Tạo **parent→child** để chứng minh parent-first delegation thường thắng bản trùng ở child.
6. Phân biệt **class definition** với **resource enumeration** khi một tên resource xuất hiện ở nhiều JAR.
7. Đảm bảo ví dụ có lệnh chạy và output xác định, tự dọn fixture, không cần dependency ngoài JDK.

## 2. Một số khái niệm cần đúng trước khi tạo JAR

### 2.1. Binary name khác đường dẫn entry trong JAR

Với source:

```java
package fixture.same;

public final class Versioned {
    public String origin() {
        return "A";
    }
}
```

Đây là **source minh họa** cho bản A. Bản B có khai báo package/class/method giống hệt nhưng trả `"B"`.

- **Binary name:** `fixture.same.Versioned`, dùng cho `loader.loadClass(...)`.
- **Class file entry:** `fixture/same/Versioned.class`, dùng khi đưa file vào JAR.
- **Root:** file JAR chứa entry ấy, không phải thư mục Java package.
- **Dấu phân tách:** tên entry trong JAR dùng `/` trên cả Linux, macOS và Windows.
- **Bytecode:** hai bản phải được **biên dịch riêng**, không phải biên dịch hai file có cùng fully-qualified name cùng lúc vào một output folder.

Hai class file có thể cùng tồn tại trong **hai JAR** mà không làm `javac` hoặc JAR format tự động từ chối. Xung đột được quan sát khi chúng **cùng tham gia vào đường tìm** của một loader/runtime hoặc khi bytecode khác nhau không tương thích với code gọi.

### 2.2. Parent-first delegation thường xảy ra trước URL order

`URLClassLoader` kế thừa cơ chế `loadClass` parent-first mặc định:

```text
child.loadClass("fixture.same.Versioned")
    |
    +-- đã được ghi nhận nạp? -> trả Class cũ
    |
    +-- parent có class?     -> trả Class của parent
    |
    +-- child.findClass     -> tìm các URL cục bộ theo đường tìm
           |
           +-- A.jar trước B.jar -> có thể chọn A
           +-- B.jar trước A.jar -> có thể chọn B
```

**Vì thế, “JAR A đứng trước JAR B” chỉ mô tả ưu tiên của đường tìm cục bộ trong điều kiện parent chưa cung cấp class.** Nếu parent đã có `fixture.same.Versioned`, child nhận định nghĩa của parent, dù child chỉ có B.jar.

Một plugin loader tùy chỉnh có thể thay đổi thành child-first hoặc theo package, nhưng đó **không phải** chính sách mặc định của `URLClassLoader`.

### 2.3. Cùng binary name không có nghĩa cùng runtime type

Runtime class/interface được xác định bằng cặp:

```text
(binary name, defining ClassLoader)
```

Do đó:

```text
("fixture.same.Versioned", LoaderAB) !=
("fixture.same.Versioned", LoaderBA)
```

Ngay cả khi hai loader dùng cùng một JAR và class file byte-for-byte giống nhau, nếu mỗi loader **tự định nghĩa** class thì hai `Class<?>` objects đại diện **hai runtime classes khác nhau**.

Dấu hiệu kiểm chứng là `Class.getClassLoader()`, `Class<?> ==`, `Class.isInstance(...)`, không phải chỉ so `getName()`.

## 3. Thiết kế fixture: đơn giản nhưng đủ cách phân biệt

Ta cố ý sử dụng các điều kiện sau:

| Thành phần | Thiết kế | Lý do |
| --- | --- | --- |
| Java version | Java 17+ | Chạy được với JDK phổ biến; hỗ trợ `JavaCompiler` và try-with-resources |
| Nguồn bytecode | `javax.tools.JavaCompiler` | Sinh class thật, không phải mảng byte giả |
| JAR packaging | `java.util.jar.JarOutputStream` | JAR thật với entry có tên chính xác, không lệ thuộc tool bên ngoài |
| Binary name | `fixture.same.Versioned` ở cả A và B | Tạo xung đột có chủ ý |
| Method phân biệt | `origin()` trả `A` hoặc `B` | Quan sát định nghĩa nào thắng qua stdout |
| Resource trùng | `fixture/label.txt` | Tách resource lookup khỏi class lookup |
| Parent của AB/BA | Platform ClassLoader | Tránh application loader tình cờ thấy class fixture |
| Parent-child test | Parent[A.jar] → Child[B.jar] | Xác minh parent-first |
| Resource cleanup | Close loaders, `JarFile`, streams, xóa thư mục tạm | Không giữ file handle trong ví dụ |
| Assertion/quan sát | In boolean và giá trị có tên rõ ràng | Không lệ thuộc object hash, PID, temp path |

Fixture có **hai JAR**, nhưng tạo **bốn đối tượng URLClassLoader** trong một lượt thử để tách rõ thứ tự URL và quan hệ parent. Không có nhu cầu dùng hai JAR bên ngoài hoặc thư viện kiểm thử riêng.

### Những giả định cần giữ

- Parent platform không chứa `fixture.same.Versioned` (đúng với JDK chuẩn).
- Chương trình không đặt A.jar/B.jar lên application classpath một cách riêng.
- Class được sinh là class thông thường trong unnamed module của loader riêng, không phải named module trong JPMS.
- Không dùng classloader policy tùy chỉnh, bytecode agent biến đổi hoặc manifest `Class-Path`.
- Máy có JDK với compiler module `jdk.compiler` và quyền đọc/ghi thư mục tạm.

Nhờ các điều kiện trên, stdout của chương trình có thể được dự đoán và kiểm chứng chính xác.

## 4. Chương trình Java 17 hoàn chỉnh tạo và thử hai JAR

Lưu **toàn bộ** mã dưới đây thành file `DuplicateJarFixture.java`. Chương trình:

1. Tạo thư mục tạm.
2. Sinh và biên dịch hai bản `fixture.same.Versioned` vào hai output roots khác nhau.
3. Đóng gói mỗi bản vào một JAR cùng resource marker.
4. Kiểm tra hai JAR đều chứa class entry.
5. Thử các ClassLoader với URL order và parent hierarchy khác nhau.
6. Đọc resource và kiểm tra runtime class identity.
7. Đóng JAR/loader và xóa các file tạm.

```java
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public final class DuplicateJarFixture {
    private static final String BINARY = "fixture.same.Versioned";
    private static final String CLASS_ENTRY = "fixture/same/Versioned.class";
    private static final String LABEL_ENTRY = "fixture/label.txt";

    private static Path createJar(JavaCompiler compiler, Path root,
                                  String marker) throws IOException {
        Path source = root.resolve("source-" + marker + "/" + CLASS_ENTRY)
                .resolveSibling("Versioned.java");
        Path classes = root.resolve("classes-" + marker);
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source,
                "package fixture.same; public final class Versioned {"
              + " public String origin() { return \"" + marker + "\"; } }",
                StandardCharsets.UTF_8);
        int result = compiler.run(null, null, null,
                "--release", "17", "-encoding", "UTF-8",
                "-d", classes.toString(), source.toString());
        if (result != 0) {
            throw new IllegalStateException("Compilation failed: " + marker);
        }
        Path jar = root.resolve(marker + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry(CLASS_ENTRY));
            out.write(Files.readAllBytes(classes.resolve(CLASS_ENTRY)));
            out.closeEntry();
            out.putNextEntry(new JarEntry(LABEL_ENTRY));
            out.write(marker.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }

    private static String origin(Class<?> type) throws ReflectiveOperationException {
        Object instance = type.getDeclaredConstructor().newInstance();
        return (String) type.getMethod("origin").invoke(instance);
    }

    private static String read(URL resource) throws IOException {
        try (var in = resource.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean hasEntry(Path jar, String entry) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.getJarEntry(entry) != null;
        }
    }

    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Run with a full JDK 17+");
        }
        Path root = Files.createTempDirectory("duplicate-jar-fixture-");
        try {
            Path aJar = createJar(compiler, root, "A");
            Path bJar = createJar(compiler, root, "B");
            System.out.println("twoJarFiles="
                    + (Files.isRegularFile(aJar) && Files.isRegularFile(bJar)));
            System.out.println("bothContainClass="
                    + (hasEntry(aJar, CLASS_ENTRY) && hasEntry(bJar, CLASS_ENTRY)));
            URL a = aJar.toUri().toURL();
            URL b = bJar.toUri().toURL();
            ClassLoader platform = ClassLoader.getPlatformClassLoader();
            try (URLClassLoader ab = new URLClassLoader(new URL[]{a, b}, platform);
                 URLClassLoader ba = new URLClassLoader(new URL[]{b, a}, platform);
                 URLClassLoader parent = new URLClassLoader(new URL[]{a}, platform);
                 URLClassLoader child = new URLClassLoader(new URL[]{b}, parent)) {
                Class<?> classAB = ab.loadClass(BINARY);
                Class<?> classBA = ba.loadClass(BINARY);
                Class<?> classChild = child.loadClass(BINARY);
                System.out.println("abOrigin=" + origin(classAB));
                System.out.println("baOrigin=" + origin(classBA));
                System.out.println("abDefinedByAB=" + (classAB.getClassLoader() == ab));
                System.out.println("baDefinedByBA=" + (classBA.getClassLoader() == ba));
                System.out.println("sameBinaryName="
                        + classAB.getName().equals(classBA.getName()));
                System.out.println("sameRuntimeClass=" + (classAB == classBA));
                System.out.println("crossAssignable="
                        + classAB.isInstance(classBA.getDeclaredConstructor().newInstance()));
                System.out.println("repeatSameClass=" + (classAB == ab.loadClass(BINARY)));
                System.out.println("childOrigin=" + origin(classChild));
                System.out.println("childDefinedByParent="
                        + (classChild.getClassLoader() == parent));
                System.out.println("abFirstResource=" + read(ab.getResource(LABEL_ENTRY)));
                Enumeration<URL> resources = ab.getResources(LABEL_ENTRY);
                List<String> found = new ArrayList<>();
                while (resources.hasMoreElements()) {
                    found.add(read(resources.nextElement()));
                }
                System.out.println("abResources=" + String.join(",", found));
                System.out.println("resourceCount=" + found.size());
                System.out.println("stringFromBootstrap="
                        + (ab.loadClass("java.lang.String").getClassLoader() == null));
            }
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
```

### 4.1. Biên dịch và chạy

Mở terminal trong thư mục chứa file `DuplicateJarFixture.java` và dùng JDK 17 trở lên:

```bash
javac --release 17 DuplicateJarFixture.java
java DuplicateJarFixture
```

**Không cần tạo sẵn** `A.jar` hoặc `B.jar`. Chúng được chương trình tạo trong thư mục tạm khi chạy, rồi xóa ở cuối. Vì mã nguồn sinh ra là Java 17-compatible, chương trình có thể chạy trên JDK 17+ với compiler đầy đủ.

### 4.2. Stdout kỳ vọng khi chạy fixture trên JDK 17+

Với Java compiler và bộ JAR do chương trình tạo, stdout dự kiến gồm 16 dòng:

```text
twoJarFiles=true
bothContainClass=true
abOrigin=A
baOrigin=B
abDefinedByAB=true
baDefinedByBA=true
sameBinaryName=true
sameRuntimeClass=false
crossAssignable=false
repeatSameClass=true
childOrigin=A
childDefinedByParent=true
abFirstResource=A
abResources=A,B
resourceCount=2
stringFromBootstrap=true
```

Nếu chạy trên một JDK tối giản không có `JavaCompiler`, chương trình cố ý báo `IllegalStateException` kèm hướng dẫn dùng JDK đầy đủ. Nếu không được phép tạo/xóa file tạm, lỗi I/O sẽ xuất hiện, không nên tự diễn giải thành lỗi parent-first.

### 4.3. Giải thích kết quả từng nhóm

**`twoJarFiles=true`:** file A.jar và B.jar thật đã được tạo. Điều này kiểm tra tồn tại file, nhưng một JAR tồn tại chưa chắc có class entry đúng — nên cần bước tiếp theo.

**`bothContainClass=true`:** dùng `JarFile.getJarEntry("fixture/same/Versioned.class")` để xác nhận cả hai JAR chứa cùng đường dẫn entry. Đây là kiểm tra cấu trúc JAR, không tự khẳng định JVM đã nạp class từ cả hai.

**`abOrigin=A`, `baOrigin=B`:** với parent platform không có fixture class, loader có URL[A,B] nhận bản A còn URL[B,A] nhận bản B. Đây là bằng chứng thực nghiệm về **thứ tự tìm local**.

**`abDefinedByAB=true`, `baDefinedByBA=true`:** `getClassLoader()` của các runtime classes đúng bằng loader tự định nghĩa chúng. Nếu parent có sẵn class, điều này có thể không còn đúng.

**`sameBinaryName=true`, `sameRuntimeClass=false`:** hai class có cùng tên Java nhưng khác defining loader, nên là **hai runtime types**. Không thể coi kết quả `getName().equals(...)` là bằng chứng cùng class.

**`crossAssignable=false`:** instance do class B tạo không phải instance của class A. Nếu cố ép kiểu trực tiếp giữa hai runtime class này, có thể gặp `ClassCastException` (trong ngữ cảnh cast phù hợp). Ví dụ dùng `Class.isInstance` để quan sát an toàn mà không cố tình làm chương trình thất bại.

**`repeatSameClass=true`:** loader AB đã định nghĩa `fixture.same.Versioned`, nên `ab.loadClass(BINARY)` lần sau trả lại cùng class object; đổi bytecode trên đĩa sẽ không tự tạo một định nghĩa mới trong cùng loader.

**`childOrigin=A`, `childDefinedByParent=true`:** child chỉ có B.jar, nhưng parent chứa A.jar. Theo parent-first delegation, child nhận Class do **parent** định nghĩa từ A.jar. Chỉ đổi JAR của child không làm bản B tự thắng.

**`abFirstResource=A`, `abResources=A,B`, `resourceCount=2`:** `getResource` trả resource đầu tiên; `getResources` liệt kê cả hai resource cùng tên trong các JAR. Resource lookup và class definition là **hai cơ chế khác nhau**. Hai resource không có nghĩa một loader đã định nghĩa hai class cùng binary name.

**`stringFromBootstrap=true`:** `java.lang.String` thuộc runtime JDK và do bootstrap loader định nghĩa. `null` từ `getClassLoader()` biểu diễn bootstrap trong API.

## 5. Đọc sơ đồ classloader của fixture

Hai thử nghiệm khác nhau đã được đưa vào cùng chương trình:

### 5.1. Kiểm thử thứ tự URL bằng hai loader độc lập

```text
Platform
    |
    +-- AB Loader: [A.jar, B.jar] -> Versioned.origin() = "A"
    |
    +-- BA Loader: [B.jar, A.jar] -> Versioned.origin() = "B"
```

Vì AB và BA là **hai defining loaders**, nên chúng có thể cùng định nghĩa tên `fixture.same.Versioned` như hai runtime classes độc lập. Trong từng loader riêng, không có hai định nghĩa class cùng binary name được tạo.

### 5.2. Kiểm thử parent-first bằng parent/child

```text
Platform
    |
    +-- Parent: [A.jar]
           |
           +-- Child: [B.jar]

Child.loadClass("fixture.same.Versioned")
    -> parent.loadClass(...)
    -> Parent trả class từ A.jar
    -> Child KHÔNG tự định nghĩa bản B
```

Đây là lý do phải có cả test **đảo URL** và test **parent delegation**. Nếu chỉ thử một loader AB, ta chưa chứng minh được trường hợp parent cung cấp class trước.

Nếu ứng dụng dùng child-first custom loader, phải viết test khác phản ánh đúng policy của nó, không lấy output parent-first ở đây làm kỳ vọng cho mọi framework.

## 6. Khi nào hai JAR được coi là “trùng lớp”?

Có nhiều kiểu trùng cần phân biệt:

| Hiện tượng | Ví dụ | Có nhất thiết là lỗi? |
| --- | --- | --- |
| Trùng **entry path** | Cả A.jar và B.jar chứa `fixture/same/Versioned.class` | Không tự động; loader có thể chọn một bản |
| Trùng **binary name** | Hai class files đều khai báo `fixture.same.Versioned` | Là xung đột cần quản lý nếu cùng đường tìm |
| Trùng bytecode byte-for-byte | Hai JAR sao chép đúng một `.class` | Vẫn có thể tạo runtime types khác nhau qua hai defining loaders |
| Trùng tên nhưng bytecode khác | Fixture A trả `A`, B trả `B` | Có thể ảnh hưởng logic hoặc tương thích ABI |
| Trùng **resource** | Cả hai chứa `fixture/label.txt` | Có thể có chủ đích, như service descriptors |
| Trùng package nhưng khác class | A.jar có `pkg.X`, B.jar có `pkg.Y` | Có thể hợp lệ, còn xét sealing/signers/JPMS |
| Hai module cùng package | Named modules trên module path | Có các quy tắc resolution/encapsulation riêng |

JAR format cho phép hai JAR **khác nhau** cùng chứa một entry path. Điều này khác với việc có **hai entry trùng tên trong cùng một JAR**, vốn cần tránh và thường bị công cụ tạo JAR từ chối bằng lỗi trùng entry.

### Phải kiểm tra cả tên thực trong class file

Đường `fixture/same/Versioned.class` trong JAR không tự chứng minh class file bên trong thật sự khai báo `fixture.same.Versioned`. Một file bị rename hoặc bytecode bị biến đổi có thể chứa binary name khác, dẫn đến lỗi “wrong name” khi nạp.

Trong fixture, `javac` biên dịch source cùng package/class name rồi chương trình lấy đúng path output, giúp tên path và binary name nhất quán. Với JAR nghi vấn, dùng `javap -verbose` hoặc công cụ phân tích class-file format thay vì dựa hoàn toàn vào tên entry.

## 7. Thử fixture trong dự án thực: giữ điều kiện tái lập

### 7.1. Không đưa hai class source cùng tên vào một lần javac

Sai lầm hay gặp là tạo hai file:

```text
src/a/fixture/same/Versioned.java
src/b/fixture/same/Versioned.java
```

rồi biên dịch cả hai cùng một invocation với chung output:

```bash
javac -d build/classes src/a/fixture/same/Versioned.java src/b/fixture/same/Versioned.java
```

Đây là **ví dụ lệnh sai có chủ ý**: khi cùng tạo `fixture.same.Versioned`, compiler có thể báo duplicate class. Nó **không** phải cách tạo hai JAR với bản bytecode độc lập.

Cách đúng là **hai invocation độc lập**, mỗi invocation có output root riêng:

```text
src-a/fixture/same/Versioned.java --javac--> classes-a/
src-b/fixture/same/Versioned.java --javac--> classes-b/

classes-a/fixture/same/Versioned.class --jar--> A.jar
classes-b/fixture/same/Versioned.class --jar--> B.jar
```

Chương trình fixture đã tự làm quy trình này bằng `compiler.run(...)` hai lần và `JarOutputStream`.

### 7.2. Không đặt fixture classes vào parent vô tình

Nếu chạy host với classpath:

```bash
java -cp "fixture/A.jar:fixture/B.jar:app/classes" my.app.Main
```

(đây là ví dụ Linux/macOS, Windows dùng `;`), application parent loader có thể nạp `fixture.same.Versioned` trước plugin child. Lúc đó việc đổi URL ở child chưa chắc làm kết quả thay đổi, vì parent-first đã ưu tiên parent.

Bài demo tránh sai số này bằng cách dùng **platform loader** làm parent của các URLClassLoader độc lập. Với ứng dụng thật, phải mô hình hóa class loader tree đúng với môi trường triển khai.

### 7.3. Wildcard không phải cách đảm bảo thứ tự JAR

Lệnh như:

```bash
java -cp "lib/*:app/classes" my.app.Main
```

được Java launcher mở rộng wildcard thành các JAR trong thư mục. Thứ tự wildcard expansion không được đảm bảo là alphabetic hoặc “bản mới nhất trước”; không nên xây dựng test kỳ vọng A thắng B chỉ vì tên file.

Trong fixture, mảng `new URL[]{a,b}` và `new URL[]{b,a}` được khai báo **rõ thứ tự**, nên không phụ thuộc wildcard.

### 7.4. Tính lặp lại khi build/test

Để test dễ tái lập, nên:

- Dùng cùng JDK toolchain/runtime target.
- Biên dịch tách A/B để không phụ thuộc classpath tình cờ.
- Gán binary name cố định và marker output rõ ràng.
- Đặt tên JAR/path tạm khác nhau, tách khỏi project artifact.
- Để test tự tạo và dọn fixture thay vì dùng một JAR sửa tay từ lần test trước.
- Không chọn nguồn class dựa trên timestamp, file size, filename version.
- Đảm bảo manifest không thêm classpath gián tiếp làm thứ tự tìm khó kiểm soát.
- Dùng class loaders riêng cho các tình huống khác nhau, đóng mỗi loader.
- Không để IDE/test runner tự thêm fixture classes vào application classpath.
- Kiểm tra kết quả **Class<?> thực tế**, không chỉ in mọi file tìm được trong JAR.

## 8. Giữ fixture để kiểm tra bằng jar và javap

Chương trình hoàn chỉnh ở phần 4 **xóa A.jar và B.jar sau khi chạy**, vì đây là test tự dọn. Muốn kiểm tra thủ công bằng `jar tf` và `javap`, có hai lựa chọn:

1. Dùng debugger hoặc một bản test phụ **được thiết kế để giữ artifacts** trong thư mục cụ thể thay vì xóa.
2. Dùng một quy trình build riêng để đóng gói hai bản source vào hai JAR ổn định phục vụ kiểm thử tích hợp.

Không nên chạy `jar tf A.jar` sau khi chương trình đã xóa thư mục tạm rồi kết luận fixture lỗi vì file không còn tồn tại.

Khi đã có hai JAR **được giữ lại**, lệnh kiểm tra:

```bash
jar tf A.jar
jar tf B.jar
javap -classpath A.jar -public fixture.same.Versioned
javap -classpath B.jar -public fixture.same.Versioned
```

`jar tf` liệt kê entry; `javap` hiển thị class và signatures. Vì bản A/B có cùng method descriptor `origin(): String`, `javap -public` **không cho thấy trực tiếp giá trị A/B** trong method body. Muốn soi bytecode method có thể dùng:

```bash
javap -c -classpath A.jar fixture.same.Versioned
javap -c -classpath B.jar fixture.same.Versioned
```

Lệnh này hiển thị instructions, trong đó có hằng `"A"` hoặc `"B"` trong bytecode tương ứng. Với nhiều class đang bị trùng, hãy đối chiếu cả tên, method descriptors và dependencies chứ không chỉ một method trả string.

### Kiểm chứng bằng CodeSource

Nếu cần biết JAR nào thật sự cung cấp một class đã nạp, có thể dùng:

```java
java.security.CodeSource codeSource =
        type.getProtectionDomain().getCodeSource();

System.out.println(codeSource == null
        ? "<unknown>"
        : codeSource.getLocation());
```

Đây là snippet, cần `Class<?> type` đã lấy từ lời gọi runtime. Với `URLClassLoader` nạp class từ JAR bình thường, `CodeSource.getLocation()` thường trỏ về URL JAR được chọn. Tuy nhiên, CodeSource có thể `null` với bootstrap/custom memory loaders; không nên coi thiếu CodeSource là bằng chứng class không được định nghĩa.

Trong test A/B, có thể bổ sung assertion so `type.getClassLoader()` và CodeSource URI với `A.jar`/`B.jar` theo điều kiện môi trường. Đây là **kiểm chứng bổ sung**, còn kết quả chính là method marker cùng loader identity.

## 9. Resource trùng tên và class trùng tên cho hai loại bằng chứng

Fixture đặt `fixture/label.txt` ở cả A.jar và B.jar. `ab.getResource("fixture/label.txt")` trả tài nguyên đầu tiên theo thứ tự tìm, còn `ab.getResources(...)` có thể liệt kê cả hai. Điều này **không có nghĩa AB loader đã định nghĩa hai runtime classes** với binary name `fixture.same.Versioned`: `loadClass` trả một `Class<?>` cho mỗi định nghĩa trong loader đó.

Trong ứng dụng thực tế, `META-INF/services/...` cũng có thể có nhiều bản resource hợp lệ và được `ServiceLoader` tiêu thụ. Phép thử resource ở đây chỉ kiểm tra *candidate resources* của fixture; bài **Kiểm chứng tìm tài nguyên và provider dịch vụ** dành riêng cho descriptor, API/service identity, TCCL và provider instantiation.

## 10. Từ fixture tới kiểm thử regression tự động

Fixture có thể chuyển thành các assert thay vì chỉ in stdout. Một test suite nên kiểm chứng:

| Assertion | Kỳ vọng | Điều được bảo vệ |
| --- | --- | --- |
| Hai file JAR tồn tại | A.jar và B.jar đều là file | Packaging đúng |
| Hai JAR có cùng class entry | `JarFile.getJarEntry(CLASS_ENTRY) != null` | Duplicate binary-name candidate |
| URL[A,B] → `origin()` | `A` | URL order local |
| URL[B,A] → `origin()` | `B` | Đảo URL đổi bản class |
| `classAB.getClassLoader() == ab` | `true` | AB là defining loader |
| `classBA.getClassLoader() == ba` | `true` | BA là defining loader |
| Hai `Class<?>` objects | `classAB != classBA` | Type identity khác |
| Instance B kiểm bởi class A | `isInstance(...) == false` | Không tự cast xuyên loader |
| Lặp `ab.loadClass(...)` | Trả cùng `Class<?>` | Cached class trong loader |
| Child(B) có Parent(A) | Nhận class từ parent A | Parent-first delegation |
| Resource A/B | Enumeration có hai marker | Class vs resource semantics |
| JDK String | Defining loader là bootstrap | Nạp class nền tảng |

Một hàm assertion Java 17 có thể dùng:

```java
static void check(boolean condition, String description) {
    if (!condition) {
        throw new AssertionError(description);
    }
}
```

Đây là helper, không phải chương trình độc lập. Bạn có thể thay các dòng `System.out.println` trong demo bằng `check(...)` với các kết quả mong đợi.

### Kiểm tra thứ tự và tránh flakiness

Khi kiểm tra fixture:

- **Không** dùng `ClassLoader.toString()`, object identity hash hoặc đường dẫn temp ngẫu nhiên làm stdout chính xác.
- **Không** dựa vào thời điểm JVM chạy class initializer nếu không chủ động kích hoạt/quan sát.
- **Không** yêu cầu garbage collector unload loader ngay sau `close()`.
- **Không** dùng cache từ lượt test trước khi cần nạp định nghĩa mới; tạo loader mới.
- **Không** chạy nhiều test dùng cùng binary name trong application parent classpath khiến kết quả phụ thuộc thứ tự test.
- Nếu test chạy nhiều thread, kiểm soát trạng thái môi trường và không dùng mutable URL list chung.
- Nên dùng tên package marker riêng, tránh xung đột với dependency thật trong test runner.

Fixture nhỏ có thể được nâng cấp thành test kiểm tra binary compatibility bằng cách cho A.jar và B.jar có **API khác nhau**, nhưng phải phân biệt kết quả cố ý fail do ABI với lỗi setup classloader.

## 11. Biến thể nâng cao: xung đột phiên bản API

Trong fixture chính, cả A và B cung cấp:

```java
public String origin()
```

nên code gọi method có cùng chữ ký ở hai bản. Để minh họa `NoSuchMethodError` do compile/runtime version mismatch, tạo hai bản library khác:

**Phiên bản biên dịch A:**

```java
package fixture.same;
public final class Versioned {
    public String origin() {
        return "A";
    }
    public String newMethod() {
        return "only-in-A";
    }
}
```

**Phiên bản runtime B:**

```java
package fixture.same;
public final class Versioned {
    public String origin() {
        return "B";
    }
}
```

Đây là **hai source versions thay thế nhau**, không được compile vào cùng một output root. Nếu consumer đã được biên dịch để gọi `Versioned.newMethod()` của A nhưng runtime classloader lại định nghĩa `Versioned` từ B, JVM có thể ném `NoSuchMethodError` khi resolution/invocation cần method ấy.

Kịch bản này **không phải stdout của demo chính**, mà là hướng mở rộng có chủ đích. Muốn kiểm thử chính xác:

1. Compile consumer với A.jar trên compile classpath.
2. Runtime loại A.jar khỏi loader sẽ định nghĩa `Versioned`, cung cấp B.jar thay thế.
3. Quan sát exception thật, stack trace và defining loader.
4. So method descriptors bằng `javap -classpath ... -p`.
5. Không dùng `catch Throwable` che lỗi link-time rồi báo test pass giả.

**Kết luận:** duplicate class không luôn gây exception ngay ở thời điểm nạp; đôi khi logic âm thầm thay đổi, đôi khi mới lỗi ở lần gọi method phụ thuộc vào API chỉ có trong phiên bản khác.

## 12. Giới hạn khi chuyển fixture sang JAR thật

Thí nghiệm dùng classpath/unnamed modules và hai class đơn giản. Với JAR có **package sealing** hoặc các chữ ký signer khác nhau, định nghĩa các class trong cùng package có thể thất bại trước khi ta quan sát phương thức `origin()`; các lỗi `SecurityException` hoặc ràng buộc certificate/package phải được điều tra riêng. Khi dùng **JPMS named modules**, module resolution, readability, exports và package conflicts áp dụng khác với thứ tự URL của classpath.

Không suy rộng output `AB=A` thành quy tắc chọn class cho mọi module layer, application server hay plugin loader có policy riêng. Bài kiểm chứng package-private tập trung vào **runtime package identity và loader** thay vì cùng đường dẫn package.

## 13. Lỗi thường gặp khi tạo và chạy duplicate-JAR fixture

| Triệu chứng | Nguyên nhân khả dĩ | Cách xử lý |
| --- | --- | --- |
| `duplicate class` tại compile time | Biên dịch hai source cùng binary name vào một invocation/output | Compile A/B độc lập và tách output |
| `ClassNotFoundException` | JAR entry path sai, URL không tồn tại, tên binary không đúng | Xác minh `JarFile`, path, `loadClass` name |
| `NoClassDefFoundError: wrong name` | Đổi tên entry file nhưng bytecode khai báo tên khác | Kiểm tra constant pool/class identity |
| `ClassFormatError` / `VerifyError` | Bytes hỏng/sinh không hợp lệ | Dùng `javac`, `javap -verbose`, kiểm tra source artifact |
| `UnsupportedClassVersionError` | Biên dịch bằng JDK mới hơn runtime hỗ trợ | Dùng `--release 17` và chạy JDK 17+ |
| A/B không đổi khi đảo URL | Parent đã có class, class đã cached hoặc đang kiểm tra cùng loader | In `getClassLoader()`, tạo loader riêng |
| `ClassCastException` cùng tên class | Hai defining loaders khác nhau | Chia sẻ API/SPI qua parent, đừng cast tùy tiện |
| `NoSuchMethodError` | Runtime chọn class version không có method compile-time | So ABI của hai JAR và CodeSource |
| Resource trả A dù class local là B | Parent-first resource lookup hoặc custom policy | Phân biệt resource vs class search |
| `SecurityException: sealing violation` | Package sealed bị định nghĩa từ nguồn không hợp lệ | Kiểm tra manifest và code source |
| JAR không xóa được trên Windows | File handle/JarFile/stream/loader chưa đóng | Dùng try-with-resources cho tất cả |
| `ToolProvider.getSystemJavaCompiler() == null` | Đang chạy runtime image không có compiler module | Dùng JDK đầy đủ |
| Stdout chứa path/ID ngẫu nhiên | Test log phụ thuộc temp directory, identity hash | Chỉ assert marker/boolean ổn định |

### Sai lầm: kiểm tra mỗi File tồn tại

`Files.exists(A.jar)` và `Files.exists(B.jar)` chưa đủ. JAR có thể rỗng, chứa sai class entry hoặc chứa cùng bytecode không phân biệt được bằng output. Cần kiểm tra bằng `JarFile`, rồi nạp class và xác nhận method trả marker dự kiến.

### Sai lầm: tin rằng tên file JAR cho biết “bản thắng”

Một file `lib-2.0.jar` không mặc nhiên được JVM ưu tiên hơn `lib-1.0.jar`. Với URLClassLoader, parent delegation, URL order và các nguồn gián tiếp liên quan mới là điều cần phân tích. Với Maven/Gradle, bước dependency resolution còn có thể chọn/loại phiên bản trước khi runtime được cấu hình.

### Sai lầm: không đóng các nguồn JAR

Nếu giữ `JarFile`, `JarInputStream` hoặc `URLClassLoader` mở vô hạn, test có thể để lại file handles và ảnh hưởng việc xóa/cập nhật fixture trên Windows. Ví dụ sử dụng try-with-resources và dọn thư mục tạm trong `finally`.

## 14. Ba phân biệt khi diễn giải fixture

- **Hai JAR cùng entry** khác với **một runtime loader định nghĩa hai class cùng tên**. Trong từng loader AB/BA, class được định nghĩa một lần và được cache theo identity.
- **URL order** quyết định lựa chọn cục bộ khi parent chưa cung cấp type; parent delegation có thể chọn version từ parent trước.
- **Hai `Class<?>` cùng `getName()`** không bảo đảm `==` hoặc cast hợp lệ nếu defining loaders khác nhau. Hãy đối chiếu defining loader và thực nghiệm `Class.isInstance(...)`.

## 15. Bài tập tự kiểm tra

**Câu 1.** Vì sao fixture phải biên dịch A và B trong hai output roots riêng?

**Đáp án:** Vì hai source cùng binary name; biên dịch chung có thể gây duplicate-class compile error, còn ta muốn tạo hai JAR độc lập mỗi JAR chứa một định nghĩa.

**Câu 2.** Nếu URLClassLoader AB có URL[A,B] và parent platform không có fixture class, `origin()` trả gì?

**Đáp án:** `A` theo đường tìm local thông thường; fixture xác nhận `abOrigin=A`.

**Câu 3.** Nếu đảo URL thành [B,A] trong loader **mới**, kết quả là gì?

**Đáp án:** `B`, và class do loader BA định nghĩa là runtime type khác bản do AB định nghĩa.

**Câu 4.** Child có B.jar nhưng parent có A.jar. Theo parent-first, `child.loadClass(BINARY)` trả bản nào?

**Đáp án:** Bản A do parent định nghĩa, nếu class hợp lệ và parent nạp được.

**Câu 5.** Hai `Class<?>` có `getName()` bằng nhau, có chắc `classA == classB`?

**Đáp án:** Không; cần cùng defining loader. Demo trả `sameRuntimeClass=false`.

**Câu 6.** Vì sao `getResources("fixture/label.txt")` có thể thấy cả A và B dù `loadClass` chọn bản A?

**Đáp án:** Resource enumeration trả nhiều URL trùng tên, còn class loading chọn/ghi nhận một định nghĩa theo delegation và loader.

**Câu 7.** Sau khi `ab.loadClass(BINARY)` đã trả class A, nếu sửa JAR A trên đĩa, gọi lại cùng loader có trả code mới không?

**Đáp án:** Không tự động. Loader đã ghi nhận class, và việc thay artifact đang mở còn tạo rủi ro file handle/trạng thái; phải dùng loader và fixture lifecycle phù hợp.

**Câu 8.** Hai bản class có API khác nhau. Dạng lỗi runtime điển hình khi bytecode consumer được compile với A nhưng runtime chọn B thiếu method là gì?

**Đáp án:** `NoSuchMethodError`, nếu lời gọi method bị thiếu được JVM phân giải/thực thi.

**Câu 9.** Khi lớp được nạp “sai JAR”, thông tin nào nên log đầu tiên?

**Đáp án:** Class thực tế, `type.getClassLoader()`, `CodeSource` nếu có, parent chain, URL order và runtime dependency graph. Không chỉ tên file trong lib directory.

**Câu 10.** Vì sao fixture không đặt hai JAR thành named modules có module-info.java?

**Đáp án:** Vì mục tiêu là kiểm chứng URLClassLoader/classpath search; JPMS có quy tắc module resolution và split package khác, không nên nhập nhằng.

## 16. Checklist kiểm chứng trước khi xem test là đạt

- [ ] Hai source classes có **cùng package và class binary name**.
- [ ] Hai source khác ở method marker, giúp nhận biết A/B.
- [ ] Biên dịch độc lập bằng JDK 17-compatible bytecode.
- [ ] Đóng gói đúng `fixture/same/Versioned.class` vào mỗi JAR.
- [ ] Kiểm tra JAR files tồn tại và `JarFile.getJarEntry` trả non-null.
- [ ] URL array AB/BA khai báo rõ thứ tự; không dùng wildcard phụ thuộc môi trường.
- [ ] Parent của AB/BA không có class fixture.
- [ ] Thử riêng parent A / child B để chứng minh parent-first.
- [ ] Đối chiếu `Class.getClassLoader()`, tên nhị phân, `Class<?> ==`, `isInstance`.
- [ ] Gọi cùng loader hai lần để xác nhận không tự định nghĩa lại class.
- [ ] Kiểm tra resource enumeration riêng với class lookup.
- [ ] Đóng JarFile/streams/URLClassLoader và xóa tệp tạm.
- [ ] Không ép GC/unload làm một phần stdout test.
- [ ] Với test sản phẩm, kiểm tra cả nguồn JAR và ABI của class thực tế.
- [ ] Không dùng các kết quả dựa vào tên file, timestamp, temp path hoặc hash làm assertion duy nhất.

## 17. Kết luận và tài liệu tham khảo

Fixture có hai JAR cùng định nghĩa `fixture.same.Versioned` nhưng trả marker A/B là một cách kiểm chứng rất trực quan: **URL order** có ý nghĩa khi loader tìm local, **parent-first delegation** có thể vượt qua thứ tự local của child, và **hai defining loaders riêng** có thể tạo hai runtime classes cùng tên nhưng không tương thích kiểu. Việc thêm resource trùng tên giúp tách biệt **resource enumeration** với **class definition**, tránh chẩn đoán sai chỉ từ `getResources`.

Khi xây dựng fixture, cần biên dịch hai source riêng, đóng gói đúng class entry, đảm bảo parent không vô tình chứa class, dùng JDK version ổn định và có cleanup. Từ đó có thể mở rộng thành regression test cho JAR collision, plugin isolation, dependency version mismatch và classloader routing.

Tài liệu chuẩn:

- Java SE 17 `URLClassLoader` API (URL search, `getURLs`, parent): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- Java SE 17 `ClassLoader` API (`loadClass`, `getResources`, delegation): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 `JarOutputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarOutputStream.html
- Java SE 17 `JarFile`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarFile.html
- Java SE 17 `JavaCompiler` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.compiler/javax/tools/JavaCompiler.html
- Java SE 17 launcher `java` (classpath wildcard): https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- JVMS 17 §5.3, *Creation of Classes and Interfaces* (binary name + defining loader): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-verification-002">Kiểm chứng thứ tự ủy quyền và định danh Class</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng thứ tự ủy quyền và định danh Class trong Java

Một class có tên `sample.Widget` xuất hiện ở **cả thư mục parent và thư mục child** thì Java sẽ nạp phiên bản nào? Nếu hai class loaders cùng tải `sample.Widget` từ cùng một file bytecode, hai kết quả có phải **cùng một `Class<?>`** không? Vì sao hai object có cùng tên class vẫn có thể gây `ClassCastException`?

Đây là ba câu hỏi khác nhau:

1. **Thứ tự ủy quyền (*delegation order*):** khi loader nhận yêu cầu nạp class, nó ưu tiên hỏi **parent** trước hay tự tìm trong nguồn của mình trước?
2. **Defining loader:** class thực tế được **loader nào định nghĩa** bằng hành vi JVM như `defineClass` hoặc cơ chế built-in?
3. **Định danh runtime của class:** một class không chỉ được xác định bởi binary name; theo JVMS, type identity gắn với **cặp (binary name, defining class loader)**.

Trong Java core – classloader, nhóm **Kiểm chứng hành vi**, bài này hướng dẫn kiểm chứng bằng **bằng chứng thực nghiệm**, không chỉ đoán từ classpath. Ta xây **hai phiên bản cùng binary name**, một loader parent-first mặc định, hai loaders chọn child-first có giới hạn; in nguồn method, defining loader, so `Class<?>` bằng `==`, và thử ép kiểu giữa hai namespace. Ví dụ chạy được trên **JDK 17+**, có lệnh biên dịch, stdout đã đối chiếu với kết quả thực tế, và phân tích những lỗi thiết kế thường gặp.

## 1. Quy tắc ủy quyền cần kiểm chứng

`ClassLoader.loadClass(name)` thông thường kiểm tra `findLoadedClass`, hỏi parent rồi mới gọi `findClass` của loader hiện tại. Vì vậy **parent-first** có thể trả bản `sample.Widget` từ parent, ngay cả khi child có file cùng tên. Đây là hành vi mặc định của `URLClassLoader` và `ClassLoader.loadClass`, **không phải điều kiện bắt buộc cho mọi custom loader**.

Bài này so trực tiếp parent-first với một **child-first có giới hạn theo namespace `sample.`**, thay vì đảo ưu tiên cho các lớp `java.*` hoặc shared API của host. Cách tổ chức hai JAR cùng tên từ đầu đã được minh họa ở bài fixture trước.

## 2. Defining loader và initiating loader không luôn giống nhau

Hai khái niệm theo JVMS:

- **Defining loader:** loader **đã thực sự định nghĩa** class. Runtime type identity dùng defining loader.
- **Initiating loader:** loader **khởi xướng** việc tải class, có thể tự định nghĩa hoặc delegate sang loader khác. JVM có thể ghi nhận một loader là initiating loader của class mà nó không define.

Ví dụ:

```text
Child.loadClass("sample.Widget")
            |
            v
      Parent.loadClass(...)
            |
            v
      Parent defines Widget
            |
            v
      Return Class<Widget> tới Child
```

Child có thể là initiating loader được ghi nhận, **nhưng** `Widget.class.getClassLoader()` trả **Parent** vì Parent mới là defining loader.

Điều này giải thích tại sao:

```java
Class<?> result = child.loadClass("sample.Widget");
System.out.println(result.getClassLoader() == child);
```

có thể in `false` dù **chính child đã nhận lời yêu cầu**. Đoạn trên là minh họa trong method, giả định `child` có sẵn và exception được xử lý.

Một chi tiết dễ bỏ sót: `ClassLoader.findLoadedClass(name)` không đơn thuần chỉ tìm classes do loader **tự define**; theo API Java 17, nó tìm class mà JVM đã ghi nhận loader này là **initiating loader**. Đó là lý do dùng `findLoadedClass` để chống định nghĩa trùng trong custom implementation, nhưng không được suy mọi kết quả nó trả đều do loader này define.

## 3. Định danh class khi đổi delegation policy

JVM phân biệt type bằng **(binary name, defining loader)**. Nếu `sample.Widget` do parent định nghĩa và được child trả về sau khi ủy quyền, `child.loadClass(...).getClassLoader()` là **parent**. Nếu custom child-first thực sự định nghĩa một bản cùng tên, hai `Class<?>` khác nhau dù cùng `getName()`; `Class.isInstance` và `Class.cast` cho thấy chúng không hoán đổi được.

Package name dạng chuỗi cũng chưa đủ làm hai classes thuộc cùng **runtime package**: defining loader khác nhau có thể thay đổi access control với package-private. Bài riêng về **Kiểm chứng package-private khi dùng hai loader** có thí nghiệm `IllegalAccessError`.

## 4. Tạo thí nghiệm: parent và child có class cùng tên, bytecode khác nhau

Ta viết:

**Bản ở parent:**

```java
package sample;

public class Widget {
    public String origin() {
        return "PARENT";
    }
}
```

**Bản ở child:**

```java
package sample;

public class Widget {
    public String origin() {
        return "CHILD";
    }
}
```

Cả hai có cùng binary name `sample.Widget`, cùng public method `origin():String`, nhưng phần thân method cố ý trả về giá trị khác để **quan sát class nào được thực thi**.

Không compile hai file trên trong **cùng một source directory** vì sẽ có duplicate class definition. Ta biên dịch riêng parent/child vào hai output directories, rồi host tạo loaders bằng `URLClassLoader`.

### Cấu trúc project

```text
delegation-lab/
├── src-parent/
│   └── sample/
│       └── Widget.java          # trả "PARENT"
├── src-child/
│   └── sample/
│       └── Widget.java          # trả "CHILD"
├── src-host/
│   └── VerifyDelegation.java
├── parent-classes/              # output riêng của parent
├── child-classes/               # output riêng của child
└── host-classes/                # output của application host
```

Tạo ba thư mục output trước khi chạy lệnh. Host **không import trực tiếp `sample.Widget`**, nên có thể compile độc lập với hai bản. Khi chạy, chỉ đưa `host-classes` vào application classpath; nếu thêm parent/child output vào `-cp` của host, kết quả có thể bị thay đổi do system loader định nghĩa class trước.

## 5. Host tạo loader mặc định và loader child-first có giới hạn

File `src-host/VerifyDelegation.java` (độc lập, có thể biên dịch với JDK 17+):

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class VerifyDelegation {

    static final class SelectiveChildFirst extends URLClassLoader {
        SelectiveChildFirst(
                String name, URL[] urls, ClassLoader parent) {
            super(name, urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {

            // Chỉ đảo thứ tự với namespace ứng dụng chủ động sở hữu.
            if (!name.startsWith("sample.")) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);

                if (c == null) {
                    try {
                        c = findClass(name);       // Child thử trước.
                    } catch (ClassNotFoundException missing) {
                        c = super.loadClass(name, false); // Fallback.
                    }
                }

                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }
    }

    static Object create(Class<?> type) throws Exception {
        return type.getConstructor().newInstance();
    }

    static String origin(Class<?> type, Object instance)
            throws Exception {
        return (String) type.getMethod("origin").invoke(instance);
    }

    public static void main(String[] args) throws Exception {
        URL[] parentUrls = {
            Path.of("parent-classes").toUri().toURL()
        };
        URL[] childUrls = {
            Path.of("child-classes").toUri().toURL()
        };

        try (URLClassLoader parent = new URLClassLoader(
                     "parent", parentUrls,
                     ClassLoader.getPlatformClassLoader());

             URLClassLoader normal = new URLClassLoader(
                     "normal", childUrls, parent);

             SelectiveChildFirst first = new SelectiveChildFirst(
                     "child-first-A", childUrls, parent);

             SelectiveChildFirst second = new SelectiveChildFirst(
                     "child-first-B", childUrls, parent)) {

            Class<?> p = parent.loadClass("sample.Widget");
            Class<?> n = normal.loadClass("sample.Widget");
            Class<?> a = first.loadClass("sample.Widget");
            Class<?> b = second.loadClass("sample.Widget");

            System.out.println("normal chose parent=" + (n == p));
            System.out.println("normal defining loader="
                    + n.getClassLoader().getName());
            System.out.println("normal origin="
                    + origin(n, create(n)));

            System.out.println("child-first chose child=" + (a != p));
            System.out.println("child-first defining loader="
                    + a.getClassLoader().getName());
            System.out.println("child-first origin="
                    + origin(a, create(a)));

            System.out.println("same binary name="
                    + a.getName().equals(b.getName()));
            System.out.println("distinct Class objects=" + (a != b));

            System.out.println("repeat stable="
                    + (first.loadClass("sample.Widget") == a));

            System.out.println("bootstrap String="
                    + (first.loadClass("java.lang.String")
                        == String.class));

            try {
                a.cast(create(b));
                System.out.println("unexpected cast success");
            } catch (ClassCastException expected) {
                System.out.println("cross-loader cast="
                        + expected.getClass().getSimpleName());
            }
        }
    }
}
```

Đọc kỹ các quyết định:

- `parent` có URLs trỏ tới `parent-classes` và parent là **platform loader**.
- `normal` có URLs trỏ `child-classes` nhưng **parent = parent loader**. Đây là một `URLClassLoader` bình thường, không override `loadClass`, nên hành vi parent-first.
- `first` và `second` đều dùng các bytes ở `child-classes`, nhưng là **hai objects loader độc lập**, mỗi instance có name riêng.
- `SelectiveChildFirst` chỉ đảo thứ tự cho những binary names bắt đầu bằng `sample.`. Các tên ngoài namespace ấy, bao gồm `java.lang.String`, vẫn đi qua default parent-first.
- `findLoadedClass` kiểm tra trước khi define lại, tránh trùng định nghĩa `sample.Widget` trong cùng initiating namespace.
- `getClassLoadingLock(name)` bảo vệ đường load khi nhiều threads yêu cầu cùng class. Java ClassLoader có những ràng buộc đồng bộ/parallel capable; đoạn này phục vụ demo, **không tự được xem như fully production-safe parallel loader**.
- `findClass` được thử **trước** parent đối với `sample.`; chỉ khi child không có file mới gọi `super.loadClass(name, false)`.
- `resolveClass(c)` được gọi khi `resolve` được yêu cầu; method này không chạy `static initializer` như `Class.forName(name, true, loader)`.

**Lưu ý về fallback:** không bao `LinkageError`/`ClassFormatError`/`SecurityException` vào catch rồi âm thầm thử parent. Đoạn demo chỉ bắt `ClassNotFoundException`, vì **file không tồn tại ở child** là tình huống fallback hợp lý; bytes bị hỏng, package sai, tên không khớp hoặc bytecode có vấn đề là lỗi cần xử lý, không phải “coi như child không có class”.

## 6. Biên dịch và chạy

Từ thư mục `delegation-lab`:

```bash
javac --release 17 -d parent-classes src-parent/sample/Widget.java
javac --release 17 -d child-classes src-child/sample/Widget.java
javac --release 17 -d host-classes src-host/VerifyDelegation.java
java -cp host-classes VerifyDelegation
```

Các lệnh không chứa nhiều classpath entries nên giống nhau trên Windows/Linux/macOS.

Stdout **đã kiểm chứng sau khi compile với `javac --release 17` và chạy JVM**:

```text
normal chose parent=true
normal defining loader=parent
normal origin=PARENT
child-first chose child=true
child-first defining loader=child-first-A
child-first origin=CHILD
same binary name=true
distinct Class objects=true
repeat stable=true
bootstrap String=true
cross-loader cast=ClassCastException
```

Kết quả này **không phải suy đoán**: host đã được biên dịch độc lập, class parent và child được compile thành hai outputs khác nhau, JVM đã chạy và stdout khớp.

### Thử nghiệm A: parent-first chọn class của parent

```text
normal.loadClass("sample.Widget")
      |
      +-- parent.loadClass(...) -> sample.Widget từ parent-classes
      |
      +-- normal.findClass(...) KHÔNG CẦN GỌI
```

Các dòng:

```text
normal chose parent=true
normal defining loader=parent
normal origin=PARENT
```

chứng minh `normal.loadClass` trả **chính `Class<?>` do parent define**. Parent loader trỏ tới code trả `PARENT`, dù child cũng có `sample.Widget.class` trả `CHILD`.

**Không nói normal “nạp cả hai bản rồi chọn bản parent”.** Nó trả class mà parent đã tìm, không cần tự define bản child.

### Thử nghiệm B: child-first chọn class của child

```text
first.loadClass("sample.Widget")
      |
      +-- first.findLoadedClass(...) -> null lần đầu
      |
      +-- first.findClass(...) -> sample.Widget từ child-classes
      |
      +-- không cần fallback sang parent
```

Các dòng:

```text
child-first chose child=true
child-first defining loader=child-first-A
child-first origin=CHILD
```

chứng minh `first` tự định nghĩa `sample.Widget` mặc dù parent có class cùng tên.

### Thử nghiệm C: hai loader child-first độc lập định nghĩa hai types

```text
first  -> sample.Widget (child-first-A)
second -> sample.Widget (child-first-B)

getName() đều "sample.Widget"
Class<?> identity khác nhau
```

Dòng:

```text
same binary name=true
distinct Class objects=true
```

đặt **bằng chứng trực tiếp** cho JVMS runtime identity. **Cùng đường dẫn `child-classes` và cùng class-file bytes** không hợp nhất hai class đã được hai loader khác nhau define.

### Thử nghiệm D: cache/ổn định trong cùng một loader

```text
repeat stable=true
```

Lần thứ hai `first.loadClass("sample.Widget")` trả **cùng `Class<?>`**. Một loader hoạt động đúng không được tự ý liên tục define class mới cùng binary name trong cùng namespace; JVM có ràng buộc và có thể phát `LinkageError: attempted duplicate class definition` nếu subclass gọi `defineClass` trùng.

### Thử nghiệm E: không đảo thứ tự platform classes

```text
bootstrap String=true
```

Vì child-first policy chỉ áp dụng `sample.`, `java.lang.String` dùng default delegation. `String.class` thường do bootstrap loader định nghĩa, và `Class.getClassLoader()` của bootstrap-defined class biểu diễn bằng `null`.

Dòng này **không chứng minh mọi loader platform luôn parent-first**; nó chứng minh **policy do ví dụ viết** tránh tự define `java.*` hoặc can thiệp class nền tảng.

### Thử nghiệm F: ép kiểu chéo dẫn đến lỗi

```text
cross-loader cast=ClassCastException
```

Trong demo, `a` là `sample.Widget` do loader A define; object `create(b)` thuộc `sample.Widget` do loader B define. `a.cast(objectOfB)` ném `ClassCastException` dù source-level tên class giống nhau. Đây là lỗi **type identity**, không phải tên class viết sai.

**Không chữa bằng cách cast qua `Object` rồi cast lại**; object vẫn thuộc type của loader B. Cần giao tiếp qua contract/interface chung hoặc serialize dữ liệu sang DTO/API boundary phù hợp.

## 7. Bằng chứng mạnh hơn: Class object, loader, CodeSource và resource

Một phương pháp kiểm chứng đầy đủ gồm:

| Quan sát | API/lệnh | Giúp xác nhận |
| --- | --- | --- |
| Binary class name | `clazz.getName()` | Hai class có cùng tên hay không |
| Defining loader | `clazz.getClassLoader()` | **Ai định nghĩa** runtime class |
| Loader name | `clazz.getClassLoader().getName()` | Nhận diện loader đã đặt tên (không phải unique ID) |
| Class identity | `clazzA == clazzB` | Hai references có cùng `Class<?>` object không |
| Object/type identity | `clazzA.isInstance(objectB)`, `clazzA.cast(objectB)` | Instance của type A hay B |
| Origin behavior | Reflective invoke `origin()` | Bytecode đã chọn chạy bản parent hay child |
| Parent chain | `loader.getParent()` | Quan hệ delegation, **không phải inheritance** |
| Code source | `clazz.getProtectionDomain().getCodeSource()` | Nơi class binary có thể đến (nếu runtime cung cấp) |
| JVM class load | `-Xlog:class+load=info` trên HotSpot | Dòng load, loader/source khi có |
| Loader hierarchy | `jcmd PID VM.classloaders` | Parent tree trong JVM chạy thật |

Trong example, `getClassLoader().getName()` dễ quan sát vì constructors đặt tên `parent`/`child-first-A`/`child-first-B`. Tuy nhiên **name của loader không phải global unique identifier**: hai instance `URLClassLoader` có thể được đặt cùng name. Muốn biết có thực sự là một loader, so **object identity** của loader bằng `==`.

### In CodeSource khi cần điều tra JAR precedence

Một đoạn code minh họa:

```java
Class<?> type = first.loadClass("sample.Widget");

java.security.ProtectionDomain pd = type.getProtectionDomain();
java.security.CodeSource source =
        pd == null ? null : pd.getCodeSource();

System.out.println(
        source == null ? "<unknown>" : source.getLocation());
```

Đây **không phải file biên dịch độc lập**, giả định `first` đã được tạo và method xử lý `ClassNotFoundException`. Với URLClassLoader thông thường, location có thể là file URL của `child-classes`; với bootstrap, byte-array loader, generated class hoặc một số custom loader, CodeSource có thể `null`.

**Không suy từ URL path duy nhất rằng class đã được nạp:** resource `getResource("sample/Widget.class")` có thể đọc file từ URL, nhưng class resolution có thể delegate và chọn class của loader khác.

## 8. Đổi parent-first sang child-first trong ứng dụng thật: khi nào nên làm?

Child-first có thể hữu ích khi plugin cần dùng phiên bản implementation dependency riêng, ví dụ hai plugin cùng dùng hai phiên bản thư viện JSON. Nhưng **không child-first mọi package**.

Thiết kế thường gặp:

```text
Host/System loader
  +-- api.Plugin
  +-- api.Message
  +-- shared interfaces, DTOs và framework contracts
          ^
          | parent-first (bắt buộc thống nhất shared API identity)
          |
Plugin loader A
  +-- plugin.impl.*
  +-- private dependencies version A
          ^
          |
Plugin loader B
  +-- plugin.impl.*
  +-- private dependencies version B
```

Trong mô hình đúng:

- **`java.*` và platform API:** để runtime/platform loader phụ trách; không tìm cách tự define.
- **`api.*` shared contracts:** parent-first để host và plugin cùng nhận **một `Class<?>`**.
- **`plugin.*` hoặc namespace tư hữu:** có thể áp dụng child-first có giới hạn để tránh parent che class phiên bản plugin.
- **Native libraries/JNI:** có lifecycle/loader restrictions riêng; child-first không tự cô lập native state trong cùng process.
- **JPMS named modules:** còn phải xét module resolution, exports/opens/readability; chỉ đảo classpath search không vượt được JPMS access controls.
- **Security:** classloader isolation **không phải sandbox** đủ để chạy bytecode không tin cậy.

Nếu parent đã define `api.Plugin` và child lại tự define `api.Plugin` bằng child-first, object implementation của child có thể **không cast được** sang interface host mặc dù cùng fully qualified name. Vì vậy allowlist child-first phải chừa namespace shared API.

## 9. Một custom loader an toàn cần chú ý điều gì?

Trong ví dụ `SelectiveChildFirst`, đoạn quan trọng:

```java
synchronized (getClassLoadingLock(name)) {
    Class<?> result = findLoadedClass(name);
    if (result == null) {
        try {
            result = findClass(name);
        } catch (ClassNotFoundException missing) {
            result = super.loadClass(name, false);
        }
    }
    if (resolve) resolveClass(result);
    return result;
}
```

Đây là **đoạn trích** của file `VerifyDelegation.java`, không phải chương trình độc lập.

Các yêu cầu thiết kế:

1. **Tránh duplicate define:** kiểm tra `findLoadedClass` trước khi gọi `findClass`/`defineClass`.
2. **Dùng lock đúng:** `getClassLoadingLock(name)` liên quan concurrency; chú ý `registerAsParallelCapable` nếu muốn parallel capable loader và hiểu ràng buộc JVM cho loader hierarchy.
3. **Đừng child-first `java.*`:** loader không được tự define classes thuộc restricted `java.*` package, và có thể tạo xung đột nghiêm trọng với platform classes.
4. **Giữ shared API parent-first:** tránh tạo hai runtime interfaces cùng tên.
5. **Fallback chỉ khi class không tìm thấy:** không nuốt `LinkageError`/`SecurityException` và dùng parent sai phiên bản một cách âm thầm.
6. **Giữ cùng object class cho cùng binary name trong cùng loader:** không define trùng trong một loader.
7. **Quản lý `Package`:** custom loader dùng `defineClass` phải tuân protection domain, package definition/sealing và các ràng buộc classloader.
8. **Quản lý lifecycle:** `URLClassLoader.close()` không unload class ngay; tránh đóng trong lúc còn task lazy-load class/resource.
9. **Giữ debugging information:** đặt loader name, log binary name, selected defining loader và source khi phù hợp.
10. **Rà class dependencies:** type được child define có thể phụ thuộc class do parent define, nên phải bảo đảm loader constraints và version compatibility.

### Về resolveClass

`resolveClass(c)` **liên kết** class theo định nghĩa ClassLoader API; nó không bảo đảm mọi referenced class đã được resolve hoặc static initializer đã chạy theo thứ tự mà application suy đoán. Không thay `resolveClass` bằng lời gọi `Class.forName(name, true, loader)` một cách tùy tiện trong override `loadClass`, vì điều đó đổi thời điểm initialization và dễ sinh recursion/side effects.

### Về lỗi name mismatch

Nếu custom loader tự đọc bytes và gọi:

```java
defineClass("sample.Widget", bytes, 0, bytes.length);
```

thì bytes phải biểu diễn class có binary name phù hợp. Nếu chứa `sample.Other` hoặc package không hợp lệ, JVM có thể ném `NoClassDefFoundError`/linkage error. Với `URLClassLoader.findClass` trong demo, Java xử lý file lookup và định nghĩa theo quy tắc URL source.

## 10. Resource lookup cần một phép thử riêng

`loadClass` xác định một runtime class; `getResource` và `getResources` tìm một hay nhiều URL tài nguyên, còn `META-INF/services` có thể chứa nhiều provider descriptors. Custom child-first class lookup **không tự động đổi** chính sách resource lookup nếu không override các phương thức liên quan. Bài về resource/SPI thực hành toàn bộ chuỗi lookup và instantiation; ở đây chỉ kết luận về class identity từ `loadClass`.

## 11. Cách tự kiểm chứng class identity bằng reflection

Trong ví dụ chính:

```java
Class<?> a = first.loadClass("sample.Widget");
Class<?> b = second.loadClass("sample.Widget");

System.out.println(a.getName());
System.out.println(b.getName());
System.out.println(a == b);
System.out.println(a.getClassLoader() == b.getClassLoader());
```

Đây là **đoạn minh họa** thêm vào method `main` khi `first`/`second` tồn tại. Output kỳ vọng:

```text
sample.Widget
sample.Widget
false
false
```

So sánh:

- `getName()` chỉ kiểm tra binary names.
- `==` so `Class<?>` objects đúng runtime identity.
- `getClassLoader()` so defining-loader identity.
- `Class.getModule()` còn thêm góc nhìn Java 9+ về named/unnamed modules, nhưng module name không thay JVM pair binary name + defining loader.

### ClassCastException và reflection

```java
Object fromB = b.getConstructor().newInstance();
System.out.println(a.isInstance(fromB));
Object fromA = a.cast(fromB);
```

Đoạn minh họa trên sẽ in `false` rồi ném `ClassCastException`, nên dòng `Object fromA = ...` không hoàn thành. Không phải vì constructor trả object sai, mà vì A và B là **hai types runtime riêng**.

Nếu cần host giao tiếp với hai plugin implementations, định nghĩa interface `api.Plugin` ở parent rồi cho cả hai class child implement **chính interface parent**. Khi đó host có thể cast về `api.Plugin` mà không đòi hai implementation classes giống hệt nhau.

## 12. Phải chỉ rõ loader ở bước dùng reflection

`Class.forName(name, false, loader)` cho phép chọn loader cụ thể khi tìm type mà chưa yêu cầu initialization. Thay Thread Context ClassLoader (TCCL) **không tự đổi** defining loader mà overload một đối số `Class.forName(name)` sử dụng theo quy tắc caller. Với plugin cần tra `sample.Widget` của child, truyền đúng child loader và xác minh `type.getClassLoader()`; xem bài kiểm chứng `loadClass`/`forName` cho stdout tương ứng.

## 13. Những lỗi hay gặp khi quan sát kết quả

| Triệu chứng | Nguyên nhân có thể | Phép kiểm tra đúng |
| --- | --- | --- |
| Mong chọn CHILD nhưng in PARENT | Loader vẫn parent-first và parent có cùng class name | `clazz.getClassLoader()`, `origin()` và parent URL |
| Mong parent-first nhưng in CHILD | Parent không thấy class hoặc loader override `loadClass` | Parent `loadClass` trực tiếp và inspect policy |
| Hai loaders trả cùng `Class<?>` | Cả hai delegate về cùng parent defining loader | `clazzA == clazzB` và `getClassLoader()` |
| Hai loaders dùng cùng file nhưng trả class khác | Hai loaders **tự define** cùng binary name | `getName()` giống, `==` false, defining loader khác |
| `ClassCastException` dù tên class giống | Runtime class identity khác nhau | Shared parent interface hoặc cùng defining loader |
| `LinkageError: duplicate class definition` | Loader định nghĩa lại cùng name | `findLoadedClass` và locking trước `defineClass` |
| `NoClassDefFoundError` cho dependency | Child class được define nhưng dependency không tìm được hoặc initialization trước đó lỗi | Stack trace, runtime path và loader của caller |
| `NoSuchMethodError` giữa plugin/host | Version skew với shared API, parent che bản mới | Bytecode descriptors, defining loader và CodeSource |
| `IllegalAccessError` package-private | Tên package giống nhưng runtime package khác do loader | Package + defining loader, module access |
| `getResource` trả config của parent | Resource delegation khác policy class | In resource URL và loader lookup |
| Lỗi chỉ xảy ra khi callback chạy | TCCL/caller loader hoặc dependency load muộn | In loader/context tại callback |
| Sau `close()` class đã nạp vẫn chạy | Close không tương đương class unloading | Resource/lifecycle check, không suy từ method call |

Cần phân biệt **ClassNotFoundException** khi một API gọi `loadClass` không thấy binary name với **NoClassDefFoundError** lúc VM resolve dependency thiếu/initialization fail, và **ClassCastException** khi hai runtime types không tương thích. Ba lỗi này không thể chữa cùng một cách.

## 14. Không bỏ quên khóa class loading

Trong custom loader override `loadClass(name, resolve)`, việc kiểm tra `findLoadedClass` và `findClass` phải giữ vùng tới hạn thích hợp (`getClassLoadingLock(name)`). Nếu hai thread cùng tự `defineClass` cho một binary name trong **cùng loader**, lần định nghĩa trùng có thể bị `LinkageError`. Bài **Kiểm chứng nạp đồng thời và xử lý tên lớp trùng** dành riêng cho parallel-capable, per-name locks và bài thử dùng latch.

## 15. Chẩn đoán bằng JVM tools ngoài printf

Khi code đã chạy trong server lớn và không tiện in debug ở mọi điểm, có thể dùng:

**HotSpot class loading log:**

```bash
java -Xlog:class+load=info -cp host-classes VerifyDelegation
```

Tìm `sample.Widget` trong log. Dòng load có thể cho biết source/loader tùy runtime/logging level. Cảnh báo: source chỉ cho biết nơi bytecode đến nếu được báo; nó không thay phép so defining loader trong code.

**jcmd class loader tree:**

```bash
jcmd <PID> VM.classloaders
jcmd <PID> VM.classloaders show-classes=true fold=false
```

Thay `<PID>` bằng PID thật của JVM đang chạy, ví dụ từ `ProcessHandle.current().pid()`. Ví dụ main rất ngắn có thể kết thúc trước khi `jcmd` attach; nếu muốn thử, dùng server chạy lâu hoặc thêm khoảng chờ có chủ đích **trong mã test**.

**jcmd statistics/hierarchy:**

```bash
jcmd <PID> VM.classloader_stats
jcmd <PID> VM.class_hierarchy sample.Widget
```

- `VM.classloader_stats` giúp nhìn số class và loader-data.
- `VM.class_hierarchy` hiển thị **class inheritance**, không phải parent delegation.
- `VM.classloaders` mới là **parent loader hierarchy**.
- Cùng tên class nhưng do loader khác định nghĩa có thể gắn với `ClassLoaderData` khác trong diagnostics.

**`javap`** đọc class files trên đĩa, không đo trực tiếp JVM lựa chọn class version nào. Khi nghi `NoSuchMethodError` hay API mismatch, phối hợp `javap -s -p` trên các **binaries được runtime thực sự nạp** với `CodeSource` và classloading logs.

## 16. Bài tập thực hành có hướng giải

### Bài 1 — Chuyển normal loader sang platform parent trực tiếp

Thay:

```java
new URLClassLoader("normal", childUrls, parent)
```

bằng:

```java
new URLClassLoader(
        "normal", childUrls, ClassLoader.getPlatformClassLoader())
```

Đây là **đoạn minh họa thay constructor**, không phải chương trình hoàn chỉnh. Vì platform loader **không có `sample.Widget` trong thí nghiệm**, URLClassLoader `normal` có thể tự tìm bản CHILD khi parent lookup thất bại. **Parent-first vẫn là parent-first**, chỉ khác parent không có class cần tìm.

Bài học: “Kết quả origin=CHILD” **không đủ để suy classloader là child-first**; còn phải kiểm tra parent có cùng class và chính sách lookup.

### Bài 2 — Loại file class child

Giữ normal và child-first loaders nhưng bỏ `child-classes/sample/Widget.class` trước khi chạy (đảm bảo parent còn bản PARENT).

Kết quả kỳ vọng: `SelectiveChildFirst.findClass` không tìm thấy child, bắt `ClassNotFoundException` rồi fallback parent, trả bản **PARENT**. Bài học: child-first cũng có thể trả class của parent.

**Đây là bài tập dự đoán có điều kiện**, không phải stdout chính của demo đã kiểm chứng; khi bỏ child file, những so sánh trong main như `child-first chose child`/`cross-loader cast` cũng thay đổi.

### Bài 3 — Đặt shared API ở parent

Tạo `api.Plugin` interface trong parent output, rồi để hai child implementations khác nhau implement API đó. Kiểm tra:

```java
api.Plugin.class.isAssignableFrom(pluginAClass)
api.Plugin.class.isAssignableFrom(pluginBClass)
```

Đây là **biểu thức minh họa**, giả định đã tạo `api.Plugin` trong classpath/loader phù hợp. Nếu implementations delegate `api.Plugin` về cùng defining loader parent, cả hai assignable; nếu child tự define bản sao API, có thể không assignable với `api.Plugin.class` của host.

### Bài 4 — Không dùng `getName()` để quyết định identity

In:

```java
System.out.println(a.getName().equals(b.getName()));
System.out.println(a == b);
```

Trong ví dụ chính, dòng đầu `true`, dòng sau `false`. Mục tiêu là nhắc rằng **class name equality ≠ class identity**.

### Bài 5 — Tạo loader mới từ cùng thư mục

Sau khi loader A và B đóng, tạo một loader C cũng trỏ `child-classes`. C có thể tự define thêm một `sample.Widget` thứ ba. Không suy rằng `URLClassLoader.close()` của A/B đã unload type cũ; class unloading phụ thuộc reachability/GC và không diễn ra ngay.

## 17. Những hiểu lầm cần tránh

| Phát biểu sai | Đính chính |
| --- | --- |
| “Child gọi loadClass nghĩa child nhất định define class.” | Sai. Parent có thể define class rồi trả về. |
| “Nếu class file nằm trong child directory thì child sẽ ưu tiên bản ấy.” | Không khi parent-first và parent có class trùng tên. |
| “Hai `Class<?>` có cùng `getName()` chắc chắn là một type.” | Sai. Cần xét defining loader. |
| “Hai loaders đọc cùng bytes thì JVM coi là cùng class.” | Sai nếu cả hai tự define class. |
| “Một loader có thể define lại cùng binary name mỗi lần gọi.” | Sai; duplicate definition có thể phát `LinkageError`. |
| “TCCL làm Class.forName(String) tự dùng child loader.” | Sai; overload một tham số dùng caller defining loader. |
| “ClassLoader.getResource và loadClass chắc chắn cùng thứ tự.” | Không, resource lookup policy cần xét độc lập. |
| “`resolveClass` chạy static initializer.” | Không; linking khác class initialization. |
| “Package names bằng nhau đồng nghĩa package-private access xuyên loaders.” | Sai; runtime package còn phụ thuộc defining loader. |
| “ClassCastException giữa plugins là do compiler không hiểu tên package.” | Không; type identity trong JVM có loader component. |
| “Chỉ cần bật child-first cho mọi package sẽ sửa dependency conflicts.” | Có thể phá shared API/platform classes và gây linkage errors. |
| “Đóng URLClassLoader là xoá Class identity cũ ngay.” | Không; JVM có thể còn giữ loader/class/instances. |

## 18. Câu hỏi tự kiểm tra có đáp án

**Câu 1.** Trong demo, cả parent và child đều có `sample.Widget`. Vì sao `normal` chọn bản PARENT?

**Đáp án:** `normal` là `URLClassLoader` mặc định theo parent-first. Nó delegate `parent.loadClass` trước và nhận class do parent define; vì vậy không cần `normal.findClass`.

**Câu 2.** Vì sao `first` chọn CHILD dù parent có class cùng tên?

**Đáp án:** `SelectiveChildFirst` override `loadClass` cho namespace `sample.`, gọi `findClass` ở child trước rồi chỉ fallback parent nếu child không có class.

**Câu 3.** `a.getName().equals(b.getName())` trả true, `a == b` trả false. Mâu thuẫn không?

**Đáp án:** Không. Cùng binary name nhưng defining loaders khác nhau, nên đó là hai runtime types riêng theo JVMS.

**Câu 4.** `a.cast(objectOfB)` ném lỗi gì và vì sao?

**Đáp án:** `ClassCastException`, vì objectOfB có runtime class được loader B define, không phải `Class<?> a` của loader A.

**Câu 5.** Initiating loader khác defining loader thế nào?

**Đáp án:** Initiating loader khởi xướng việc nạp (có thể delegate); defining loader thực sự định nghĩa type. Runtime identity dựa defining loader.

**Câu 6.** Vì sao gọi `first.loadClass("sample.Widget")` lần hai trả lại class cũ?

**Đáp án:** Loader duy trì namespace/ghi nhận class đã load, và implementation kiểm tra `findLoadedClass` trước khi define. Điều đó tránh duplicate definition cùng binary name trong một loader.

**Câu 7.** Vì sao chỉ in method `origin()` không đủ để xác minh loader policy?

**Đáp án:** Nếu parent không có class, ngay cả loader parent-first cũng fallback child và in CHILD. Cần thử với **hai bản cùng tên** ở cả parent và child, đồng thời in defining loader.

**Câu 8.** Nếu hai plugins cần giao tiếp với host qua `api.Plugin`, nên define API ở đâu?

**Đáp án:** Ở parent/shared loader, để host và plugin nhận **cùng runtime interface type**. Implementation riêng ở child loaders vẫn có thể khác nhau.

**Câu 9.** `Class.getClassLoader() == null` thường biểu thị điều gì?

**Đáp án:** Class do bootstrap loader định nghĩa (ví dụ `String` trong JVM thông dụng). Nó không có nghĩa class không tồn tại hoặc bị unload.

**Câu 10.** Dùng `VM.class_hierarchy` để đọc parent loader chain đúng không?

**Đáp án:** Không. Nó là class inheritance hierarchy; parent loader tree xem bằng `VM.classloaders`.

**Câu 11.** Tại sao `findLoadedClass` và locking cần có khi override `loadClass`?

**Đáp án:** Tránh nạp/định nghĩa lặp cùng binary name và race giữa nhiều threads. Cần hiểu parallel-capable loader rules nếu muốn triển khai concurrent production.

**Câu 12.** `Class.forName("sample.Widget")` trong host helper có chắc dùng loader child-first đang nằm ở TCCL?

**Đáp án:** Không. Overload một tham số dùng defining loader của host caller; muốn dùng loader cụ thể, truyền vào overload ba tham số.

**Câu 13.** Nếu `a.getClassLoader() != b.getClassLoader()` thì chắc `a != b` với class bình thường không?

**Đáp án:** Có, vì một runtime class có defining loader xác định; hai defining loader khác nhau thì không cùng một runtime class definition.

**Câu 14.** Loader child-first có thể fallback parent được không?

**Đáp án:** Có. Child-first chỉ thay đổi **ưu tiên**; nếu child không tìm thấy, implementation có thể hỏi parent, như demo `SelectiveChildFirst`.

## 19. Checklist kiểm chứng trên dự án thực

1. **Tạo hai bản cùng binary name** ở parent/child outputs độc lập để phân biệt thứ tự.
2. **Kiểm tra runtime classpath của host** không vô tình chứa cả hai bản qua application loader.
3. **In loader parent** bằng `getParent()` và xác nhận URL của parent/child.
4. **In `getClassLoader()` của kết quả**, không chỉ name của loader nhận yêu cầu.
5. **So `Class<?>` bằng `==`** khi đánh giá class identity.
6. **Gọi method khác output** giữa hai bản để chứng minh bytes phiên bản nào được thực thi.
7. **Lặp `loadClass`** để kiểm tra reuse, không duplicate define.
8. **Kiểm tra namespace allowlist** cho child-first, giữ shared API/platform parent-first.
9. **Dùng reflection `Class.cast` / `isInstance`** khi muốn chứng minh tách types.
10. **Xem CodeSource và classloading logs** khi điều tra JAR/classpath precedence.
11. **Đọc `ClassLoader.loadClass` contract** trước khi override và không bắt nhầm linkage errors làm fallback.
12. **Kiểm tra TCCL riêng** nếu framework/SPI dùng context lookup.
13. **Phân biệt `load`, `link`, `initialize`**, không suy static initializer chạy chỉ vì `loadClass`.
14. **Không hiểu `getResource` như bằng chứng class đã define**; resource policy có thể khác.
15. **Trong plugin thực tế**, giữ loader mở trong suốt lifecycle cần lazy loading và cleanup callbacks/tasks trước close.
16. **Khi dùng JPMS**, bổ sung module exports/opens/readability vào phân tích.
17. **Kiểm thử concurrent loading** và classloader constraints nếu viết custom loader production.
18. **Ghi kết quả có kiểm chứng**: tên class, loader object, defining loader, origin output, equality/cast và runtime version.

## Kết luận

**Thứ tự ủy quyền** quyết định **loader nào có cơ hội tìm class trước**, còn **định danh runtime của `Class<?>`** phụ thuộc **binary name + defining loader**. Trong thí nghiệm, loader `normal` parent-first trả đúng class của `parent`; loader `child-first-A` tự define bản CHILD; hai loaders child-first khác nhau tự define **hai runtime types** dù cùng tên, và ép kiểu chéo phát `ClassCastException`.

Muốn kiểm chứng đúng, không chỉ dựa vào một dòng `System.out.println(clazz.getName())`. Hãy kiểm tra **parent chain → loader policy → defining loader → `Class<?>` equality → method origin → instance assignability**. Đây cũng là nền tảng để thiết kế plugin isolation mà vẫn chia sẻ ổn định interfaces giữa host và plugins.

**Tài liệu chính thức Java 17:**

- [ClassLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html) — `loadClass`, `findLoadedClass`, `findClass`, `getClassLoadingLock`, parent delegation và loader name.
- [URLClassLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html) — URL classpath, constructors named loader, `close`.
- [JVMS 17 5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3) — defining/initiating loader, runtime type identity, loader constraints.
- [JLS 17 12 — Execution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html) — loading, linking, initialization, unloading.
- [Class API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html) — `getName`, `getClassLoader`, `cast`, `isInstance`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-verification-003">Kiểm chứng loadClass khác Class.forName về khởi tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng loadClass khác Class.forName về khởi tạo lớp trong Java

`ClassLoader.loadClass(...)` và `Class.forName(...)` đều có thể trả về `Class<?>` đại diện cho một kiểu Java ở runtime, nhưng **không có cùng cam kết về class initialization**. Đây là điểm quan trọng khi code dùng reflection, plugin, SPI, dependency injection hoặc tự xây `ClassLoader`: nhìn thấy `Class<?>` **chưa có nghĩa** static fields đã nhận giá trị cuối hoặc khối `static { ... }` đã chạy.

**Kết luận cốt lõi cho Java 17+:**

- **`loader.loadClass("p.Target")`:** theo triển khai chuẩn của `ClassLoader`, tìm/nạp lớp theo binary name và **không yêu cầu khởi tạo** lớp. Overload này tương đương gọi `loadClass(name, false)`. Subclass tùy biến có thể thêm side effects; không được suy luận về code override không kiểm soát.
- **`Class.forName("p.Target")`:** dùng class loader của **caller** và **yêu cầu khởi tạo** class nếu chưa initialized.
- **`Class.forName("p.Target", true, loader)`:** chọn loader được truyền tường minh, **yêu cầu khởi tạo** class nếu cần.
- **`Class.forName("p.Target", false, loader)`:** tìm/nạp class qua loader chỉ định nhưng **không yêu cầu khởi tạo**.
- **`Class.forName(module, "p.Target")`:** overload theo `Module` (Java 9+) chỉ cố tìm/nạp, **không link hoặc khởi tạo theo contract API**, trả `null` nếu không tìm thấy; không đồng nhất với overload một `String`.

Bài học thuộc **Java core – classloader / Kiểm chứng hành vi**. Mục tiêu là **chứng minh bằng stdout có kiểm soát** lúc nào `<clinit>` chạy, phân biệt loading/linking/initialization, xử lý lớp đã khởi tạo từ trước, TCCL, compile-time constants, arrays và lỗi initializer. Các thí nghiệm ưu tiên **Java 17+**, cung cấp mã độc lập, lệnh `javac`/`java` và giải thích tại sao output đúng.

## 1. Ba giai đoạn: loading, linking, initialization

Một mô hình dễ nhớ:

```text
Tên lớp + ClassLoader
         │
         ▼
       LOADING
      đọc/tìm bytes,
      tạo runtime Class<?>
         │
         ▼
       LINKING
 verification / preparation / resolution
         │
         ▼
    INITIALIZATION
  thực thi static field initializers
  và static initializer blocks
  (<clinit>) khi JVM yêu cầu
         │
         ▼
  CLASS ĐÃ INITIALIZED
```

- **Loading:** tạo hoặc tra cứu runtime class bằng tên và loader. Parent có thể trả class do parent định nghĩa, thay vì child tự define.
- **Linking:** JVM kiểm tra bytecode, chuẩn bị vùng static data và resolve symbolic references theo các ràng buộc của đặc tả. Một số resolution có thể xảy ra sớm hoặc muộn; **không nên coi sơ đồ là lịch trình cứng**.
- **Initialization:** thực thi các static field initializers và static blocks có liên quan, theo thứ tự quy định; JVM bảo đảm protocol initialization chỉ diễn ra một lần thành công cho **mỗi runtime class/interface**.

**Preparation không phải initialization:** một static field có thể đã được chuẩn bị với giá trị mặc định trước khi initializer Java gán giá trị mong muốn. Do đó đọc `Class<?>` qua `loadClass` chưa thể chứng minh static state “đã sẵn sàng”.

**Object creation còn là bước khác:** `ClassLoader.loadClass` hoặc `Class.forName` không tự gọi constructor instance; `new Target()` hay `Constructor.newInstance()` mới tạo object. Khi class chưa initialized, việc tạo instance yêu cầu initialization trước khi constructor hoạt động.

## 2. Bảng so sánh các overload trên Java 17

| Lời gọi | Loader sử dụng | Yêu cầu initialize? | Khi không tìm thấy |
| --- | --- | --- | --- |
| `loader.loadClass(name)` | `loader` object | **Không** theo implementation chuẩn | `ClassNotFoundException` |
| `Class.forName(name)` | Defining loader của **caller class** | **Có** | `ClassNotFoundException` |
| `Class.forName(name, true, loader)` | Loader được chỉ định; `null` nghĩa bootstrap | **Có** | `ClassNotFoundException` |
| `Class.forName(name, false, loader)` | Loader được chỉ định | **Không** | `ClassNotFoundException` |
| `Class.forName(module, name)` | Module được chỉ định và loader tương ứng | **Không**, và không link theo Javadoc | Trả `null` |
| `Target.class` | Compile-time type reference | **Không tự yêu cầu** | N/A; type phải hợp lệ trong ngữ cảnh biên dịch |
| `new Target()` | Type đã resolution từ bytecode | **Có nếu cần** | Có thể gặp lỗi loading/linking/initialization |

**Quan trọng:** `loadClass(name, false)` với `resolve=false` **không có nghĩa JVM chắc chắn không làm linking nào**. Một số linking/verification có thể xảy ra như một phần của runtime. Kết luận được hỗ trợ ở đây là **không yêu cầu thực thi initializer**, chứ không phải “bytes chưa từng được kiểm tra”.

**Không nhầm `resolveClass` với initialization.** `ClassLoader.resolveClass(Class<?>)` dùng cho linking; nó không phải API bảo đảm chạy `<clinit>`.

## 3. Cách tạo một thí nghiệm đáng tin

Thí nghiệm sai phổ biến là:

```java
System.out.println(Target.value);
Class<?> result = loader.loadClass("demo.Target");
```

Đây là **snippet sai mục đích kiểm chứng** nếu `Target.value` là static field không phải compile-time constant: lần đọc field đầu tiên có thể **đã khởi tạo `Target` trước khi gọi `loadClass`**. Khi đó không thấy static block chạy ở câu lệnh thứ hai không chứng minh điều mong muốn.

Để quan sát đúng:

1. Dùng **lớp đích chưa được active-use trước đó**, lý tưởng mỗi phép thử một class khác nhau.
2. In các dấu mốc **trước và sau** API cần kiểm tra.
3. Đặt dấu mốc `System.out.println("INIT ...")` bên trong static initializer của lớp đích.
4. Dùng **binary name string** để yêu cầu load mà không vô tình truy cập static member của lớp đích.
5. Chạy **mỗi chương trình trong một tiến trình JVM mới** khi cần reset trạng thái. Initialization thành công không thể chạy lại bằng cách gọi `forName` nhiều lần trên cùng runtime class.
6. Nếu so hai loaders, giữ rõ **defining loader identity** và tách classpath plugin khỏi host.
7. Không dùng `Thread.sleep` để suy đoán đã initialize hay chưa; dựa trên markers và hành vi Java/JVM được đặc tả.

Trên HotSpot, logging `-Xlog:class+load=info` có thể hỗ trợ quan sát loading, nhưng output dài và phụ thuộc JVM. **Static initializer marker là minh chứng đơn giản hơn cho thời điểm initialization** trong các demo nhỏ.

## 4. Thực hành A — `loadClass`, `forName(false)` và `forName(true)`

Tạo project:

```text
initialization-lab/
└── src/demo/LoadVsForNameDemo.java
```

**`src/demo/LoadVsForNameDemo.java`:**

```java
package demo;

public final class LoadVsForNameDemo {
    static final class LoadOnly {
        static {
            System.out.println("INIT LoadOnly");
        }
    }

    static final class FalseTarget {
        static {
            System.out.println("INIT FalseTarget");
        }
    }

    static final class TrueTarget {
        static {
            System.out.println("INIT TrueTarget");
        }
    }

    static final class ActiveTarget {
        static {
            System.out.println("INIT ActiveTarget");
        }

        static void ping() {
            System.out.println("PING ActiveTarget");
        }
    }

    private static String binaryName(String simpleName) {
        return LoadVsForNameDemo.class.getName() + "$" + simpleName;
    }

    public static void main(String[] args) throws Exception {
        ClassLoader app = LoadVsForNameDemo.class.getClassLoader();

        System.out.println("1 before loadClass");
        Class<?> loaded = app.loadClass(binaryName("LoadOnly"));
        System.out.println("2 loaded=" + loaded.getSimpleName());

        System.out.println("3 before forName(false)");
        Class<?> notInitialized = Class.forName(
                binaryName("FalseTarget"), false, app);
        System.out.println("4 loaded=" +
                notInitialized.getSimpleName());

        System.out.println("5 before forName(true)");
        Class<?> initialized = Class.forName(
                binaryName("TrueTarget"), true, app);
        System.out.println("6 loaded=" +
                initialized.getSimpleName());

        System.out.println("7 initialize the earlier LoadOnly");
        Class<?> later = Class.forName(
                binaryName("LoadOnly"), true, app);
        System.out.println("8 same Class=" + (loaded == later));

        System.out.println("9 before active use");
        ActiveTarget.ping();
        System.out.println("10 finished");
    }
}
```

Từ thư mục `initialization-lab`, biên dịch/chạy trên **JDK 17+**:

```sh
javac --release 17 -d out src/demo/LoadVsForNameDemo.java
java -cp out demo.LoadVsForNameDemo
```

**Stdout dự kiến trong một JVM mới:**

```text
1 before loadClass
2 loaded=LoadOnly
3 before forName(false)
4 loaded=FalseTarget
5 before forName(true)
INIT TrueTarget
6 loaded=TrueTarget
7 initialize the earlier LoadOnly
INIT LoadOnly
8 same Class=true
9 before active use
INIT ActiveTarget
PING ActiveTarget
10 finished
```

### Đọc kết quả từng đoạn

- Ở dòng **1–2**, `app.loadClass(...)` trả `Class<?>` của `LoadOnly` mà **không in `INIT LoadOnly`**. `getSimpleName()` chỉ lấy metadata tên, không yêu cầu static initialization của class đích.
- Dòng **3–4** cũng không in `INIT FalseTarget`: overload `Class.forName(name, false, app)` tìm/nạp class nhưng **không yêu cầu khởi tạo**.
- Dòng **5–6** có `INIT TrueTarget` **giữa hai markers**: overload `Class.forName(name, true, app)` yêu cầu initialization trước khi trả về thành công.
- Sau dòng **7**, `Class.forName(..., true, app)` nhận **chính runtime `LoadOnly` đã được `loadClass` trả trước đó**, nhưng lần này yêu cầu initialization. Vì thế xuất hiện `INIT LoadOnly`, rồi `loaded == later` bằng `true`.
- Ở dòng **9–10**, gọi `ActiveTarget.ping()` là **active use** của static method, buộc khởi tạo `ActiveTarget` trước khi in `PING`.
- `FalseTarget` **không hề in initializer** trong toàn bộ chương trình, vì không có hoạt động nào buộc nó initialize.

**Điểm đo thật sự:** thí nghiệm không chứng minh `loadClass` “không đọc bytecode”; nó chứng minh **không kích hoạt static initialization** trong cách gọi này.

## 5. `Class.forName(String)` khác thế nào với overload ba tham số?

Dạng rút gọn:

```java
Class<?> type = Class.forName("demo.Target");
```

Dựa trên Javadoc, nó dùng **defining loader của caller class** và **yêu cầu initialize**. Không có tham số `initialize=false` trong overload này.

Tương đương khái niệm khi đang ở code thuộc class `Caller`:

```java
Class<?> type = Class.forName(
        "demo.Target", true, Caller.class.getClassLoader());
```

Đây là **snippet minh họa** với `Caller` tồn tại và caller thực sự là `Caller`. Khi code nằm trong framework/library, **caller class loader** có thể khác plugin loader mà ứng dụng muốn dùng. Bởi vậy với plugin, thường nên truyền **loader tường minh** vào overload ba tham số.

**TCCL không tự được dùng bởi `Class.forName(String)`.** `Thread.currentThread().getContextClassLoader()` là cơ chế khác, thường được thư viện SPI như `ServiceLoader.load(service)` sử dụng. Thay TCCL không biến `Class.forName(name)` thành “tìm bằng TCCL”.

## 6. Thực hành B — thay TCCL không đổi loader của `forName(String)`

Bài này minh họa hai điểm độc lập: (1) class mà **child URLClassLoader** có thể nạp không nhất thiết có trên host classpath; (2) `Class.forName(name)` dùng loader của caller, **không tự đọc TCCL**.

Cây tệp:

```text
loader-choice-lab/
├── src/
│   ├── plugin/feature/PluginTarget.java
│   └── host/demo/LoaderChoiceDemo.java
├── plugin-out/
└── host-out/
```

**`src/plugin/feature/PluginTarget.java`:**

```java
package feature;

public final class PluginTarget {
    static {
        System.out.println("INIT PluginTarget");
    }

    public static String message() {
        return "plugin ready";
    }
}
```

**`src/host/demo/LoaderChoiceDemo.java`:**

```java
package demo;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class LoaderChoiceDemo {
    public static void main(String[] args) throws Exception {
        String name = "feature.PluginTarget";
        URL pluginRoot = Path.of("plugin-out")
                .toAbsolutePath().toUri().toURL();

        try (URLClassLoader pluginLoader = new URLClassLoader(
                    new URL[] {pluginRoot},
                    ClassLoader.getPlatformClassLoader())) {

            Class<?> loaded = pluginLoader.loadClass(name);
            System.out.println("loaded by plugin=" +
                    (loaded.getClassLoader() == pluginLoader));

            Thread thread = Thread.currentThread();
            ClassLoader oldContext = thread.getContextClassLoader();

            try {
                thread.setContextClassLoader(pluginLoader);

                try {
                    Class.forName(name);
                    System.out.println("unexpected one-arg success");
                } catch (ClassNotFoundException expected) {
                    System.out.println("one-arg not found=true");
                }

                Class<?> noInit =
                        Class.forName(name, false, pluginLoader);
                System.out.println("false same Class=" +
                        (loaded == noInit));

                System.out.println("before explicit true");
                Class<?> yesInit =
                        Class.forName(name, true, pluginLoader);
                System.out.println("true same Class=" +
                        (loaded == yesInit));
            } finally {
                thread.setContextClassLoader(oldContext);
            }
        }
    }
}
```

Biên dịch từ `loader-choice-lab`:

```sh
javac --release 17 -d plugin-out src/plugin/feature/PluginTarget.java
javac --release 17 -d host-out src/host/demo/LoaderChoiceDemo.java
```

Chạy (cố ý **chỉ** đưa `host-out` vào application classpath):

```sh
java -cp host-out demo.LoaderChoiceDemo
```

**Stdout dự kiến trong cấu hình trên:**

```text
loaded by plugin=true
one-arg not found=true
false same Class=true
before explicit true
INIT PluginTarget
true same Class=true
```

Giải thích:

1. Parent là platform loader, không biết `feature.PluginTarget` trong thư mục `plugin-out`. `URLClassLoader` tìm class từ URL riêng.
2. `pluginLoader.loadClass` trả runtime `Class<?>`, chưa in `INIT PluginTarget`.
3. Dù current thread TCCL đã đổi sang `pluginLoader`, `Class.forName(name)` trong `LoaderChoiceDemo` vẫn dùng **loader của caller host**; host không có plugin trên `-cp` nên `ClassNotFoundException`.
4. `Class.forName(name, false, pluginLoader)` nhận đúng class child vừa nạp và vẫn không initialize.
5. `Class.forName(name, true, pluginLoader)` kích hoạt `<clinit>`, in `INIT PluginTarget` trước khi trở về.
6. Ba class references `loaded`, `noInit`, `yesInit` đều là **cùng `Class<?>`** của plugin loader. Sự khác nhau là **initialization state**, không phải class identity.
7. `finally` khôi phục TCCL cũ; với pool threads, việc quên khôi phục có thể làm rò plugin loader hoặc ảnh hưởng SPI discovery sau đó.

**Không thêm `plugin-out` vào `-cp`** khi chạy ví dụ: như vậy `Class.forName(name)` của host có thể tìm class, mất giá trị so sánh.

## 7. Các thao tác làm thay đổi trạng thái của `Class`

`loadClass` và `Class.forName(name, false, loader)` **không tự yêu cầu initialization**. `new`, gọi static method, đọc nonconstant static field, gán static field hoặc `Class.forName(name, true, loader)` lại có thể là trigger. `T.class` và đọc compile-time constant không tự buộc `T` initialized. Đối với static field kế thừa, trigger nhắm tới **kiểu khai báo field**; việc initialize class còn kéo theo superclass và một số superinterface có default methods. Các bài về class initialization đã giải thích luật nền; bài này kiểm tra chính xác hiệu ứng các overload.

## 8. Thực hành C — constant, class literal và field runtime

Source file `src/demo/ConstantVsInitDemo.java`:

```java
package demo;

public final class ConstantVsInitDemo {
    static final class Values {
        static {
            System.out.println("INIT Values");
        }

        static final int COMPILE_TIME = 7;
        static final int RUNTIME = Integer.parseInt("8");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("constant=" + Values.COMPILE_TIME);

        System.out.println("literal=" +
                Values.class.getSimpleName());

        ClassLoader app = ConstantVsInitDemo.class.getClassLoader();
        Class<?> c = Class.forName(
                ConstantVsInitDemo.class.getName() + "$Values",
                false, app);

        System.out.println("forName(false) returned=" +
                c.getSimpleName());

        System.out.println("before runtime field");
        System.out.println("runtime=" + Values.RUNTIME);
        System.out.println("runtime again=" + Values.RUNTIME);
    }
}
```

Biên dịch/chạy trong project chứa `src/demo/ConstantVsInitDemo.java`:

```sh
javac --release 17 -d out src/demo/ConstantVsInitDemo.java
java -cp out demo.ConstantVsInitDemo
```

**Stdout:**

```text
constant=7
literal=Values
forName(false) returned=Values
before runtime field
INIT Values
runtime=8
runtime again=8
```

**Tại sao có sự khác biệt?**

- `COMPILE_TIME` là **constant variable** vì `static final int` được gán bằng một constant expression `7`. Java compiler có thể nhúng giá trị vào code caller; việc dùng field này không kích hoạt initialization của `Values`.
- `Values.class` cung cấp `Class<?>` mà **không yêu cầu** chạy static initializer.
- `Class.forName(..., false, app)` lấy `Class<?>` nhưng vẫn để target chưa initialized.
- `RUNTIME` **không phải compile-time constant** dù là `static final`, vì `Integer.parseInt("8")` là lời gọi method. Lần đọc `Values.RUNTIME` đầu yêu cầu initialization: in marker rồi đặt `RUNTIME=8`.
- Dòng `runtime again=8` không in marker lần nữa vì `Values` **đã initialized thành công**.

**Cạm bẫy lớn:** `static final` **không đồng nghĩa** compile-time constant. Chỉ một subset các kiểu và biểu thức đạt định nghĩa *constant variable* của JLS. Ví dụ `static final String S = new String("x")` không là compile-time constant.

## 9. `Class.forName(..., true, ...)` không tạo object instance

Xét:

```java
Class<?> type = Class.forName(name, true, loader);
```

Lời gọi có thể khiến `static { ... }` của target chạy, nhưng nó **không phải `new`**. Constructor instance chưa chạy cho đến khi có thao tác tạo object.

Đối với reflection:

```java
Class<?> type = Class.forName(name, false, loader);
// Tại đây target chưa bắt buộc initialized.

Object object = type.getDeclaredConstructor().newInstance();
// Khi instance được tạo, JVM yêu cầu target initialized nếu cần.
```

Đây là **snippets** cần `name` và `loader` cùng xử lý exceptions. Nếu constructor không public, còn có thể vướng `IllegalAccessException` hoặc JPMS access rules; không tự gọi `setAccessible(true)` rồi cho rằng mọi module đều mở.

Phân biệt bytecode-level:

- `<clinit>`: class/interface initialization từ static initializers và static field assignments.
- `<init>`: instance initialization và constructor chain của từng object.

JVM thực thi `<clinit>` **tối đa một lần thành công cho mỗi runtime class**; rất nhiều object của class đó có thể được tạo sau, với `<init>` tương ứng mỗi lần.

## 10. `forName(true)` gọi lại có chạy initializer lần nữa không?

**Không**, nếu class đã initialized thành công. Một lần sau có thể trả cùng runtime `Class<?>` mà không lặp lại side effects của `<clinit>`.

Ví dụ:

```java
Class<?> first = Class.forName(name, true, loader);
Class<?> second = Class.forName(name, true, loader);
System.out.println(first == second);
```

Đây là **snippet** giả định `name` và `loader` giống nhau, target tồn tại và không có custom loading behavior phá contract. `true` cho so sánh cùng runtime definition; static initializer của target **không được chạy lại** vì lời gọi `forName` thứ hai.

### Một class có thể initialized nhiều lần trong JVM theo tên không?

Nếu **hai defining loaders khác nhau** tự định nghĩa **cùng binary name**, JVM coi đó là **hai runtime classes riêng**, nên mỗi class có initialization state riêng. Một ví dụ plugin reload:

```text
("plugin.Widget", loader-generation-1)
    → runtime Class A → initializer A

("plugin.Widget", loader-generation-2)
    → runtime Class B → initializer B

A.getName().equals(B.getName()) → true
A == B                           → false
```

Nhưng nếu `loader-generation-2` parent-first **nhận class A từ parent**, nó không tự tạo class B. Log defining loader bằng `Class.getClassLoader()` khi cần khẳng định isolation.

## 11. Thực hành D — lỗi initializer chỉ xuất hiện khi yêu cầu initialize

Chương trình này phân biệt **“load được bytes”** với **“class initialization thành công”**. Ta dùng một class có static initializer cố tình ném exception, và chủ động theo dõi hai lần active use.

Tệp `src/demo/InitFailureDemo.java`:

```java
package demo;

public final class InitFailureDemo {
    static final class Broken {
        static int value = compute();

        private static int compute() {
            System.out.println("INIT Broken");
            throw new IllegalStateException("invalid configuration");
        }
    }

    public static void main(String[] args) throws Exception {
        String name = InitFailureDemo.class.getName() + "$Broken";
        ClassLoader app = InitFailureDemo.class.getClassLoader();

        Class<?> loaded = app.loadClass(name);
        System.out.println("loaded without init=" +
                (loaded.getClassLoader() == app));

        try {
            Class.forName(name, true, app);
        } catch (ExceptionInInitializerError error) {
            System.out.println("first error=" +
                    error.getClass().getSimpleName());
            System.out.println("root cause=" +
                    error.getCause().getClass().getSimpleName());
        }

        try {
            Class.forName(name, true, app);
        } catch (NoClassDefFoundError error) {
            System.out.println("second error=" +
                    error.getClass().getSimpleName());
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+ trong một JVM mới:

```sh
javac --release 17 -d out src/demo/InitFailureDemo.java
java -cp out demo.InitFailureDemo
```

**Stdout:**

```text
loaded without init=true
INIT Broken
first error=ExceptionInInitializerError
root cause=IllegalStateException
second error=NoClassDefFoundError
```

**Giải thích:**

1. `loadClass` trả runtime type của `Broken` thành công nhưng chưa buộc chạy `compute()`.
2. `Class.forName(name, true, app)` kích hoạt initialization; `compute()` in marker rồi ném `IllegalStateException`.
3. Vì exception ném từ initializer không phải một `Error`, JVM bọc thành `ExceptionInInitializerError`, đồng thời đánh dấu class ở trạng thái **erroneous**.
4. Gọi `Class.forName(..., true, ...)` lần nữa trên **cùng runtime class** không chạy initializer lại; JVM báo `NoClassDefFoundError` liên quan tới trạng thái erroneous.
5. `NoClassDefFoundError` không phải lúc nào cũng nghĩa file `.class` biến mất. Nó có thể là hậu quả **initialization thất bại trước đó**.

**Tại sao không đặt `throw new IllegalStateException(...)` trực tiếp trong static block vô điều kiện?** Với Java source, một static initializer không được “cannot complete normally” theo kiểm tra cú pháp/luồng điều khiển. Viết lời gọi `compute()` trong field initializer tạo một ví dụ compile được, có failure ở runtime.

**Vận hành:** đừng “sửa” bằng cách gọi `loadClass` lại. Trong cùng runtime class, initialization không reset. Cần xem **root cause đầu tiên** và xử lý vòng đời class loader nếu thật sự cần nạp generation mới.

## 12. `Class.forName(Module, String)` là overload khác hẳn

Java 9+ bổ sung:

```java
Class<?> found =
        Class.forName(SomeType.class.getModule(), "pkg.Target");
```

Đây là **snippet minh họa**; cần module/type tồn tại. Theo **Java 17 Javadoc**:

- Phương thức nhận **`Module` object**, không phải `ClassLoader`.
- Nó cố tìm/nạp class thuộc đúng module đã cho.
- Nó **không link** target class và **không chạy class initializer**.
- Nếu không có class theo binary name trong module ấy, kết quả là **`null`**, không giống `ClassNotFoundException` của overload ba tham số.
- Nó không tự bảo đảm class được access bởi caller; runtime module access checks vẫn phải được xét khi sử dụng members.
- Nếu loader của module định nghĩa những module khác, tên thuộc module khác có thể được nạp nhưng phương thức **trả null** vì không thuộc module được chỉ định.

**Đừng viết bài kiểm thử chỉ so string `Class.forName` mà bỏ chữ ký**. `Class.forName(String)` và `Class.forName(Module, String)` không có cùng initialization semantics.

## 13. Trường hợp đặc biệt: array class không initialize component type

Javadoc `Class.forName(name, boolean, loader)` có ngoại lệ: nếu `name` biểu thị **array class**, JVM nạp component type nhưng **không initialize component type chỉ vì array lookup**.

Ví dụ name của `demo.Item[]` ở dạng `Class.getName()`:

```text
[Ldemo.Item;
```

Cú pháp có dấu `[L` và dấu `;` là JVM binary descriptor-like name dành cho object arrays, khác tên source `demo.Item[]`.

### Thực hành E — array lookup và element initialization

**`src/demo/ArrayInitDemo.java`:**

```java
package demo;

public final class ArrayInitDemo {
    static final class Element {
        static {
            System.out.println("INIT Element");
        }

        Element() {
            System.out.println("CONSTRUCT Element");
        }
    }

    public static void main(String[] args) throws Exception {
        String elementName = ArrayInitDemo.class.getName() + "$Element";
        String arrayName = "[L" + elementName + ";";
        ClassLoader app = ArrayInitDemo.class.getClassLoader();

        System.out.println("before array forName(true)");
        Class<?> arrayClass = Class.forName(arrayName, true, app);
        System.out.println("array loaded=" + arrayClass.isArray());

        Element[] values = new Element[2];
        System.out.println("array length=" + values.length);

        System.out.println("before new element");
        values[0] = new Element();
        System.out.println("after new element");
    }
}
```

Biên dịch/chạy:

```sh
javac --release 17 -d out src/demo/ArrayInitDemo.java
java -cp out demo.ArrayInitDemo
```

**Stdout:**

```text
before array forName(true)
array loaded=true
array length=2
before new element
INIT Element
CONSTRUCT Element
after new element
```

**Vì sao?** `Class.forName(arrayName, true, app)` trả runtime **array class**, nhưng không buộc component `Element` initialized. `new Element[2]` tạo array với các phần tử ban đầu `null`, không tạo một `Element` object. Chỉ `new Element()` ở dòng sau yêu cầu initialize component class, rồi gọi constructor.

Đừng tổng quát một cách sai rằng `forName(..., true, ...)` luôn chạy initializer của **mọi type được nhắc đến trong name**. Phải xem class/interface/array case theo Javadoc.

## 14. Runtime class identity: cùng tên, cùng `Class<?>`, khác trạng thái

Trong thực hành A:

```text
app.loadClass("demo.LoadVsForNameDemo$LoadOnly")
    └── trả Class<?> C, chưa yêu cầu init

Class.forName("demo.LoadVsForNameDemo$LoadOnly", true, app)
    └── trả cũng Class<?> C, nhưng yêu cầu init trước khi trả
```

Sự khác nhau **không phải bytecode khác hay loader khác**. `Class<?>` object là cùng identity trong hai lần tra cứu. Chỉ **initialization side effect** khác.

Hãy kiểm tra:

```java
System.out.println(loaded == later);
System.out.println(loaded.getClassLoader() == app);
```

Hai snippets giả định tên biến từ thực hành A. Để hiểu tại sao static field có giá trị mặc định hay hoàn chỉnh, cần phân biệt state **prepared but not initialized** và **initialized** — `ClassLoader` API không cung cấp một public method `isInitialized(Class<?>)` tổng quát để kiểm tra trạng thái mà không làm gì thêm.

**Không cố suy trạng thái initialized chỉ từ việc có `Class<?>` hoặc `classLoader.findLoadedClass`.** `findLoadedClass` trả thông tin liên quan tới class đã được loader khởi xướng nạp, không phải một status API cho `<clinit>`.

## 15. Khởi tạo kiểu cha khi thử `forName(true)`

Yêu cầu initialize một class có thể chạy static initializer của superclass chưa initialized và các superinterface khai báo default methods trước phần mã static của class đó. Nhưng yêu cầu initialize một interface không mặc nhiên initialized mọi superinterface. Khi test `forName(true)`, đặt marker tại đúng kiểu dự kiến và ghi nhận **thứ tự** nếu có kế thừa. Quy tắc này khác riêng biệt với loader nào tìm/định nghĩa type.

## 16. Vì sao cần một fresh runtime class cho mỗi thử nghiệm

JVM đồng bộ hóa initialization để chỉ một thread thực hiện initializer của **cùng runtime class**, các thread khác chờ kết quả. Khi thành công, lặp `Class.forName(name, true, loader)` không chạy lại marker. Khi lỗi, cùng định nghĩa class trở thành erroneous; xem thí nghiệm lỗi mục 11. Muốn đo trigger đầu tiên độc lập, dùng defining loader mới thực sự tạo class mới hoặc khởi chạy JVM mới, như bài cuối chương hướng dẫn.

## 17. Không nhầm lỗi khởi tạo với lỗi nạp và reflection

- `ClassNotFoundException` khi tìm tên không chứng minh initializer đã chạy.
- `ExceptionInInitializerError` thường bọc unchecked exception từ initializer đầu tiên; sau đó cùng runtime class có thể báo `NoClassDefFoundError` vì erroneous.
- Reflection/constructor còn có `InvocationTargetException`, `IllegalAccessException` và các failure khác. Xem cause và vị trí bước thử nghiệm; không quy mọi lỗi do gọi `forName` cho **class loading**.

## 18. Kiểm chứng khi dùng plugin hoặc `URLClassLoader`

Một plugin framework thường thực hiện:

```java
Class<?> impl = pluginLoader.loadClass("plugin.ServiceImpl");
Object service = impl.getDeclaredConstructor().newInstance();
```

Đây là **snippet**, cần plugin loader và xử lý exceptions. Nếu implementation có static initializer:

- `loadClass` theo cách chuẩn **chưa yêu cầu** chạy initializer.
- `getDeclaredConstructor()` chỉ lấy constructor metadata, không tự gọi body constructor.
- `Constructor.newInstance()` là reflective instance creation và có thể **yêu cầu class initialization**.
- Lỗi `ExceptionInInitializerError` có thể xuất hiện khi tạo instance, dù `loadClass` đã hoàn tất thành công.

Nếu framework thay `loadClass` bằng `Class.forName(name, true, pluginLoader)` trong giai đoạn **discovery**, nó có thể chạy static code của plugin sớm hơn dự kiến. Điều này có thể gây I/O, kết nối mạng, khởi tạo thread hoặc throw lỗi trước giai đoạn lifecycle do host quản lý.

**Khuyến nghị thiết kế:** trong discovery, phân biệt rõ “biết type nào có thể dùng” và “cho phép code initializer chạy lúc nào”. Dùng loader và API phù hợp, sau đó kích hoạt service trong startup phase có logging, timeout và quyền hạn theo kiến trúc.

## 19. `ServiceLoader` và TCCL là thí nghiệm khác

`ServiceLoader.load(SPI)` thường sử dụng TCCL để tìm descriptors và providers; còn `Class.forName(String)` có lựa chọn loader gắn với caller. Trong plugin, đừng dựa vào thay TCCL để kết luận `forName(String)` sẽ thấy mọi class của plugin. Bài **Kiểm chứng tìm tài nguyên và provider dịch vụ** tách kiểm tra SPI identity, resource descriptor và provider instantiation.

## 20. Reflection: chỉ tra metadata hay thật sự sử dụng kiểu?

`type.getDeclaredConstructors()` và `type.getDeclaredMethods()` thường chỉ đọc metadata, còn `constructor.newInstance()`, `Field.get(null)` trên static field hoặc `Method.invoke(null, ...)` cho static method có thể kích hoạt initialization. Vì vậy kết quả “`loadClass` không in marker” chỉ có giá trị nếu **chưa có thao tác tiếp theo** tạo instance hoặc gọi static member. Tách từng hành động bằng marker riêng như mục 4.

## 21. Trường hợp đã được initialize từ trước: vì sao “không thấy marker” chưa đủ?

Nếu class `Target` đã được sử dụng trước phần đo:

```java
Target.ping();             // lần này có thể đã initialize
loader.loadClass("Target");
Class.forName("Target");
```

Đây là **pseudocode** với binary name cần chỉnh đúng. Khi gọi hai API sau, không in static initializer nữa dù `Class.forName` vẫn yêu cầu initialization theo contract, bởi JVM nhận thấy class **đã initialized**.

Do đó không thể chạy một chương trình rồi kết luận:

> “`Class.forName` không initialize vì tôi chẳng thấy `static { }` in gì.”

Kết luận đúng phải xét **trạng thái trước lời gọi**. Các bài thực hành trong README cố tình dùng các classes riêng hoặc tiến trình mới.

### Cách cải thiện test

- Tạo một target class **mới chưa active-use** cho mỗi phép thử.
- Đặt markers cạnh statements của API.
- Không dùng static non-constant field target để tạo tên class.
- Nếu cần test nhiều lần, chạy `java` process mới cho mỗi lượt thay vì trông chờ class initializer được reset.
- Nếu test reload qua loader mới, kiểm tra `type.getClassLoader()` để đảm bảo parent không âm thầm cung cấp cùng type cũ.

## 22. Thực hành F — `Class.forName(String)` có cùng kết quả init với true khi caller nhìn thấy class

Để xác nhận overload **một tham số**, dùng class đích mới trong project trên application classpath.

Tệp **`src/demo/OneArgumentForNameDemo.java`**:

```java
package demo;

public final class OneArgumentForNameDemo {
    static final class Target {
        static {
            System.out.println("INIT Target");
        }
    }

    public static void main(String[] args) throws Exception {
        String name = OneArgumentForNameDemo.class.getName()
                + "$Target";

        System.out.println("before one-arg");
        Class<?> type = Class.forName(name);
        System.out.println("after one-arg");
        System.out.println("same defining loader=" +
                (type.getClassLoader()
                        == OneArgumentForNameDemo.class.getClassLoader()));
    }
}
```

Biên dịch/chạy:

```sh
javac --release 17 -d out src/demo/OneArgumentForNameDemo.java
java -cp out demo.OneArgumentForNameDemo
```

**Stdout:**

```text
before one-arg
INIT Target
after one-arg
same defining loader=true
```

Lệnh `Class.forName(name)` yêu cầu initialization trước khi trả về, vì thế `INIT Target` xuất hiện giữa hai markers. `type.getClassLoader()` trùng loader của caller `OneArgumentForNameDemo` vì toàn bộ chương trình nằm trong application classpath theo lệnh chạy này.

**So với thực hành B:** overload một tham số ở đây thành công vì target nằm trên **classpath của caller**; ở thực hành B target chỉ nằm trong URL riêng của plugin loader, nên overload ấy thất bại dù TCCL đã được chuyển.

## 23. Cách kiểm tra bytecode để tránh “do optimizer làm mất initializer”

Khi dùng compile-time constants, compiler có thể sinh `ldc`/`bipush`/`sipush` thay vì `getstatic`. Xem bytecode giúp giải thích vì sao dùng constant không initialize declaring class.

Ví dụ từ thực hành C:

```sh
javap -classpath out -c -p demo.ConstantVsInitDemo
javap -classpath out -c -p 'demo.ConstantVsInitDemo$Values'
```

Trong shell POSIX/PowerShell, single quotes quanh nested binary name giúp bảo toàn ký tự `$`. `javap` có thể cho thấy `<clinit>` của `Values` chứa phần in marker và `Integer.parseInt("8")`; method `main` có thể sử dụng giá trị constant đã nhúng.

**Cẩn thận:** `javap` chỉ đọc class files trên đĩa, **không tự chứng minh** JVM đã chọn class file ấy qua runtime loader graph. Nếu classpath có nhiều versions của cùng binary name, phải kết hợp `Class.getClassLoader()`, `ProtectionDomain.getCodeSource()` và `-Xlog:class+load`.

## 24. Diagnostic logging có ích cho sự khác biệt loading/initialization

Một helper đơn giản khi đã có `Class<?>`:

```java
static void describe(Class<?> type) {
    ClassLoader definingLoader = type.getClassLoader();
    Module module = type.getModule();

    System.out.println("class=" + type.getName());
    System.out.println("loader=" +
            (definingLoader == null
                    ? "<bootstrap>" : definingLoader.getClass().getName()));
    System.out.println("module=" +
            (module.isNamed() ? module.getName() : "<unnamed>"));
}
```

Đây là **method minh họa**; các dòng metadata **không buộc chạy `<clinit>` của target**. Không dùng `Class.forName(name)` chỉ để log metadata của class chưa được tìm vì overload ấy có thể initialize target. Khi chỉ muốn biết class nào được load qua một loader, ưu tiên `Class.forName(name, false, loader)` nếu có lý do thực sự cần lookup.

Nếu phải ghi **CodeSource** của class, dùng đường null-safe:

```java
var domain = type.getProtectionDomain();
var source = domain == null ? null : domain.getCodeSource();
var url = source == null ? null : source.getLocation();
System.out.println(url == null ? "<unknown>" : url.toExternalForm());
```

Snippet cần `type` đã có; URL có thể chứa filesystem path hoặc thông tin nội bộ, nên tránh lộ ra client logs. CodeSource không tự chứng minh bytecode integrity hoặc khẳng định target đã initialized.

### JDK Unified Logging

Trên HotSpot hỗ trợ Unified Logging:

```sh
java -Xlog:class+load=info -cp out demo.LoadVsForNameDemo
java -Xlog:help
```

Log loading có thể hiển thị lúc class được tìm/nạp, nhưng **không phải log xác nhận static block đã chạy**. Một số HotSpot versions cũng cung cấp tag `class+init`; kiểm tra `-Xlog:help` trước. Không cố định stdout của những lệnh logging này, vì metadata/timestamps/VM versions khác nhau.

## 25. Debugging: nếu kết quả khác stdout dự kiến thì kiểm tra gì?

| Triệu chứng | Giả thuyết phổ biến | Cách kiểm tra |
| --- | --- | --- |
| `INIT` xuất hiện trước `loadClass` | Target đã active-use trước lúc đo | Tách target class; chạy JVM mới |
| `INIT` không xuất hiện ở `forName(true)` | Class đã initialized trước đó; hoặc target không có initializer in | Kiểm tra toàn bộ code path và loader identity |
| `Class.forName(name)` thất bại nhưng `child.loadClass(name)` thành công | Caller loader không thấy URL riêng của child | Truyền `child` vào overload ba tham số |
| Đổi TCCL mà `forName(String)` vẫn fail | Một tham số không tự dùng TCCL | In caller loader và TCCL; dùng explicit loader |
| `Class.forName(name,false,loader)` gặp `LinkageError` | Việc không initialize **không** loại trừ lỗi loading/linking | Đọc stacktrace, verify dependencies, bytecode |
| `Class.forName(name,true,loader)` ném `ExceptionInInitializerError` | Static initializer ném exception hoặc phụ thuộc init thất bại | Xem root cause/initializer code |
| Lần `forName(true)` tiếp ném `NoClassDefFoundError` | Class đã erroneous do init lần trước | Sửa lỗi gốc, reset bằng lifecycle phù hợp |
| Array `forName(true)` không in component marker | Array lookup không initialize element type | Active-use element class riêng |
| `static final` không tạo `INIT` | Field là compile-time constant | So `javap`, dùng non-constant static field để test |
| Plugin class lại do host loader định nghĩa | Parent-first hoặc plugin directory ở host classpath | In `type.getClassLoader()`, kiểm tra `-cp` và URLs |
| Thấy constructor message sau `forName(true)` | Code trong `<clinit>` có thể tự tạo object; không phải forName tự tạo instance | Xem static initializer và stacktrace |
| Test chạy khác trên IDE | IDE đưa plugin output vào host classpath hoặc chạy reused JVM/test order | Chạy lệnh `javac/java` riêng và xác định classpath |

Một kết quả “khác kỳ vọng” thường do **môi trường và thứ tự active use** hơn là do JVM ngẫu nhiên thay đổi hợp đồng API.

## 26. Những ngộ nhận cần tránh

- **“`loadClass` và `Class.forName` chỉ khác cách viết.”** Sai: initialization semantics và loader selection có thể khác.
- **“Có `Class<?>` tức là static block đã chạy.”** Sai; class có thể chỉ loaded/linked.
- **“`loadClass` chắc chắn không thể gây lỗi linking.”** Sai; không yêu cầu init khác với không thực hiện linking.
- **“`Class.forName(name, false, loader)` bảo đảm không tải bytecode.”** Sai; nó vẫn có thể tải class, chỉ không yêu cầu initializer.
- **“`Class.forName(name)` luôn dùng TCCL.”** Sai; dùng loader của caller class.
- **“`Class.forName(name, true, loader)` luôn tạo một object.”** Sai; trả `Class<?>`, không chạy constructor instance.
- **“`static final` luôn khiến class không initialize khi đọc.”** Sai; chỉ compile-time constant variables có ngoại lệ.
- **“`Target.class` sẽ chạy static initializer.”** Không theo hoạt động class literal thông thường.
- **“Gọi `forName(true)` hai lần sẽ chạy static block hai lần.”** Sai với cùng runtime class đã initialized thành công.
- **“Một binary name chỉ initialize một lần trong toàn JVM.”** Sai nếu các defining loaders khác nhau tạo runtime class identities khác nhau.
- **“`Class.forName(module,name)` giống `Class.forName(name)`.”** Sai: module overload không link/init và trả `null` nếu không tìm được.
- **“Array `forName(true)` luôn initialize component class.”** Sai theo Javadoc.
- **“`NoClassDefFoundError` luôn có nghĩa thiếu file `.class`.”** Sai nếu class initialization trước đó lỗi.
- **“Dùng `loadClass` là đủ bảo đảm plugin không chạy code trước startup.”** Không tuyệt đối: loader custom, linking/dependencies và application code có thể tạo side effects; phân tích đầy đủ execution path.
- **“Một JVM test rerun sẽ reset static initializer giữa test methods.”** Không nếu vẫn dùng cùng runtime class trong cùng process/loader.
- **“In log class name chứng minh parent hay child đã define.”** Không; cần `Class.getClassLoader()`.

## 27. Checklist kiểm chứng nhanh

1. Chọn target class **chưa active-use**; static initializer in một marker duy nhất.
2. In markers trước/sau từng API.
3. Dùng đúng binary name (`Outer$Inner` cho nested class).
4. Ghi rõ caller loader, explicit loader, parent và TCCL nếu có.
5. Thử `loadClass` → kiểm tra không có `INIT` trước active use.
6. Thử `Class.forName(name, false, loader)` trên target riêng → kiểm tra không có `INIT`.
7. Thử `Class.forName(name, true, loader)` → marker `INIT` trước khi method trả thành công.
8. Thử `Class.forName(name)` với class thuộc caller loader → xác nhận initializer và caller loader.
9. Thử `static final` compile-time constant so với runtime-initialized static field.
10. Thử array class lookup và tạo array, rồi tạo element instance.
11. Thử initializer ném lỗi → `ExceptionInInitializerError` trước và `NoClassDefFoundError` sau, giữ root cause.
12. Chạy mỗi thí nghiệm trong JVM mới khi cần reset.
13. Không vô tình thêm plugin output vào application classpath.
14. Kiểm tra `Class<?>` identity và defining loader nếu phải so sánh hai loader instances.
15. Với production, không dùng `Class.forName(..., true, ...)` chỉ để discovery khi static initialization chưa được cho phép.

## 28. Câu hỏi tự kiểm tra có đáp án

**1. `loader.loadClass("p.T")` theo mặc định có yêu cầu chạy `<clinit>` không?** Không.

**2. `Class.forName("p.T")` khác `loadClass` ở đâu về initialization?** Nó yêu cầu initialize target nếu chưa initialized.

**3. Làm sao nạp class qua loader chỉ định mà không yêu cầu initialization?** `Class.forName("p.T", false, loader)` hoặc `loader.loadClass("p.T")` theo hành vi chuẩn.

**4. `Class.forName("p.T", true, loader)` trả object instance hay `Class<?>`?** `Class<?>`; không tự chạy instance constructor.

**5. Đổi TCCL có đổi loader dùng bởi `Class.forName(String)` không?** Không. Overload đó dùng caller class loader.

**6. `Class.forName(Module,String)` khác gì?** Không link/init theo Javadoc và trả `null` nếu class không thuộc module được yêu cầu.

**7. Vì sao `static final int N=7` không in initializer khi đọc?** Nó là constant variable, có thể được compiler inline.

**8. Vì sao `static final int N=Integer.parseInt("7")` có thể in initializer?** Nó không phải constant expression, nên đọc field kích hoạt initialization.

**9. Hai calls `Class.forName(name,true,loader)` có chạy initializer hai lần không?** Không nếu cùng runtime class đã initialized thành công.

**10. `getDeclaredConstructor().newInstance()` có thể initialize class không?** Có, vì tạo instance là active use.

**11. Array `Class.forName("[Lp.T;", true, loader)` có buộc initialize `p.T` không?** Không theo Javadoc cho array class.

**12. Vì sao lần active use sau class initialization lỗi có thể ném `NoClassDefFoundError`?** Runtime class ở trạng thái erroneous, initializer không được thử lại.

**13. Hai class loaders định nghĩa cùng binary name có chung initialization state không?** Không; chúng là hai runtime types khác nhau.

**14. `resolveClass` có tương đương `Class.forName(..., true, ...)` không?** Không. `resolveClass` liên quan tới linking, không yêu cầu class initialization.

**15. Nếu chương trình không in `INIT` ở `Class.forName(name)`, kiểm tra gì trước?** Target đã initialized trước đó chưa, binary name/loader identity và thí nghiệm có dùng lớp mới/JVM mới không.

**16. Vì sao test class initialization bằng một static constant có thể gây hiểu lầm?** Vì compiler có thể inline giá trị và không phát sinh active use `getstatic` của declaring class.

## 29. Tài liệu chuẩn

- [Java SE 17 — ClassLoader Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass(String)` và `loadClass(String, boolean)`, parent delegation, `resolveClass`.
- [Java SE 17 — Class Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `forName(String)`, `forName(String, boolean, ClassLoader)`, `forName(Module, String)` và initialization semantics.
- [JLS Java SE 17 — 12.4.1 When Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1): active use, static field, static method, superclass/interface initialization.
- [JLS Java SE 17 — 12.4.2 Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2): trạng thái initialization, coordination và class erroneous.
- [JVMS Java SE 17 — Chapter 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): loading/linking/initialization và runtime class identity.
- [Java SE 17 — Constructor Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html): reflective instance creation và liên quan initialization.
- [Java SE 17 — URLClassLoader Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): classpath-style plugin loader và parent delegation.
- [JDK 17 — javap](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): xem bytecode `<clinit>` và constant folding.

**Kết luận:** `loadClass` chuẩn và `Class.forName(name, false, loader)` giúp tìm/nạp class **mà không yêu cầu khởi tạo**; `Class.forName(name)` và `Class.forName(name, true, loader)` **yêu cầu khởi tạo**, nhưng chỉ khi runtime class chưa initialized. Phân biệt **loader được chọn, trạng thái initialization, active use và class identity**, rồi kiểm chứng bằng những static initializer markers trong **một JVM mới** để tránh kết luận sai từ việc class đã initialized trước đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-verification-004">Kiểm chứng thứ tự static initializer và lỗi khởi tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng thứ tự static initializer và lỗi khởi tạo

## 1. Mục tiêu

Khi học Java ClassLoader, cần phân biệt **nạp lớp** với **khởi tạo lớp**. Một ClassLoader có thể tìm được bytecode và trả `Class<?>` trong khi các `static` blocks của lớp chưa chạy. Ngược lại, khi JVM bắt đầu class initialization, các static field initializers, static blocks và quan hệ superclass phải tuân thủ thứ tự chặt chẽ.

Bài này cung cấp một chương trình Java 17+ biên dịch được để kiểm chứng:

- Class literal và `ClassLoader.loadClass` không mặc nhiên kích hoạt static initialization.
- `Class.forName(name, false, loader)` khác `Class.forName(name, true, loader)`.
- Khi `new Derived()`, static initializers của superclass chạy trước subclass, và trong từng class chúng chạy theo **thứ tự xuất hiện trong source**.
- `static final` compile-time constants có thể được inline mà không initialize class; static field được tính ở runtime thì có thể kích hoạt.
- Static initialization ném exception có thể dẫn tới `ExceptionInInitializerError` ở lần đầu và `NoClassDefFoundError` ở lần dùng sau.
- Một class khởi tạo hỏng không làm tất cả các classes trong JVM tự động hỏng; trạng thái initialization gắn với **runtime class**, kể cả defining loader.

## 2. Phân biệt thời điểm nạp, liên kết và chạy initializer

**Loading** tạo/tra cứu định nghĩa `Class<?>`, **linking** gồm verification, preparation (các static fields nhận giá trị mặc định) và resolution có thể xảy ra muộn. **Initialization** mới thực hiện các static field initializers và static blocks cần chạy. `ConstantValue` (nếu có) được gán trong initialization trước `<clinit>`, không phải bởi mã `static {}` trong source. Bài này đo **thứ tự observable output** và trạng thái erroneous bằng một chương trình, thay vì chỉ nhìn log tải class.

## 3. Quy tắc thứ tự static initialization

Ví dụ:

~~~java
// Mã minh họa; mark(...) là helper in nhãn rồi trả số.
class Sample {
    static int a = mark("a", 1);

    static {
        System.out.println("block");
    }

    static int b = mark("b", 2);
}
~~~

Khi `Sample` được initialize, output sẽ theo thứ tự:

~~~text
a
block
b
~~~

Static fields và blocks được thực thi **theo thứ tự văn bản** trong class. Không có quy tắc “mọi field trước mọi block”.

Với kế thừa:

~~~text
class Base
  static Base.field
  static Base.block

class Derived extends Base
  static Derived.field
  static Derived.block

new Derived():
  Base.field
  Base.block
  Derived.field
  Derived.block
  Base constructor
  Derived constructor
~~~

Superclass chưa initialized được initialize trước subclass. Khi các static initializers xong, object construction mới thực hiện constructor superclass và subclass.

**Ngoại lệ cần hiểu riêng:** Interfaces có quy tắc đặc thù; initialization của class còn liên quan một số superinterfaces khai báo default methods. Không dùng câu “mọi interface mà class implements đều được initialize trước class” như một quy tắc tuyệt đối.

## 4. Điều kiện để dự đoán output

`Derived.class`, `loader.loadClass(...)` và `Class.forName(name,false,loader)` không tự khởi tạo lớp đích. Nhưng `new Derived()`, truy cập static field không phải hằng, gọi static method và `Class.forName(name,true,loader)` yêu cầu initialization nếu chưa hoàn tất. Đọc một primitive/`String` `final` được gán constant expression có thể được compiler inline, nên không chạy marker. Bài trước kiểm chứng từng overload sâu hơn; ở đây sẽ cùng lúc theo dõi cả **thứ tự field/block, superclass, success và failure**.

## 5. Chương trình Java 17+ tự kiểm chứng

Tạo file duy nhất **`InitializationLab.java`**:

~~~java
public final class InitializationLab {
    private InitializationLab() {
    }

    private static void log(String text) {
        System.out.println(text);
    }

    private static int mark(String label, int value) {
        log(label);
        return value;
    }

    private static int fail() {
        log("Broken.fail");
        throw new IllegalStateException("boom");
    }

    static class Base {
        static int first = mark("Base.field", 1);

        static {
            log("Base.block");
        }

        static int last = mark("Base.last", 2);

        Base() {
            log("Base.ctor");
        }
    }

    static final class Derived extends Base {
        static int first = mark("Derived.field", 3);

        static {
            log("Derived.block");
        }

        Derived() {
            log("Derived.ctor");
        }
    }

    static final class Probe {
        static {
            log("Probe.<clinit>");
        }
    }

    static final class OnlyLoaded {
        static {
            log("OnlyLoaded.<clinit>");
        }

        static String value() {
            return "LOADED";
        }
    }

    static final class Constants {
        static final int NUMBER = 42;
        static final String TEXT = "CONST";
        static final Integer BOXED = mark("Constants.<clinit>", 99);
    }

    static final class Broken {
        static int VALUE = fail();
    }

    static final class Healthy {
        static {
            log("Healthy.<clinit>");
        }

        static int VALUE = 7;
    }

    public static void main(String[] args) throws Exception {
        log("01 main");

        // Class literal không khởi tạo Derived.
        Class<?> literal = Derived.class;
        log("02 class literal: " + literal.getSimpleName());

        log("03 construct");
        new Derived();

        log("04 static again: " + Derived.first);

        Class<?> passive = Class.forName(
                InitializationLab.class.getName() + "$Probe",
                false,
                InitializationLab.class.getClassLoader());
        log("05 forName(false): " + passive.getSimpleName());

        Class.forName(
                InitializationLab.class.getName() + "$Probe",
                true,
                InitializationLab.class.getClassLoader());
        log("06 forName(true)");

        Class<?> loaded =
                InitializationLab.class.getClassLoader().loadClass(
                        InitializationLab.class.getName()
                                + "$OnlyLoaded");
        log("07 loadClass: " + loaded.getSimpleName());

        log("08 invoke: " + OnlyLoaded.value());

        log("09 int constant: " + Constants.NUMBER);
        log("10 String constant: " + Constants.TEXT);
        log("11 boxed: " + Constants.BOXED);

        Class<?> brokenType = Broken.class;
        log("12 Broken.class: " + brokenType.getSimpleName());

        try {
            log("13 Broken first: " + Broken.VALUE);
        } catch (ExceptionInInitializerError first) {
            log("13 first: " + first.getClass().getSimpleName());
            log("13 cause: "
                    + first.getCause().getClass().getSimpleName());
        }

        try {
            log("14 Broken second: " + Broken.VALUE);
        } catch (NoClassDefFoundError second) {
            log("14 second: " + second.getClass().getSimpleName());
        }

        log("15 Healthy: " + Healthy.VALUE);
    }
}
~~~

### 5.1. Biên dịch và chạy

Yêu cầu JDK **17 trở lên**. Mở terminal tại thư mục chứa file:

~~~bash
javac -encoding UTF-8 InitializationLab.java
java InitializationLab
~~~

Lệnh sử dụng được trên Bash, PowerShell và Command Prompt nếu `javac`/`java` nằm trong `PATH`; chương trình không cần thư viện ngoài JDK.

### 5.2. Stdout chính xác với source trên

~~~text
01 main
02 class literal: Derived
03 construct
Base.field
Base.block
Base.last
Derived.field
Derived.block
Base.ctor
Derived.ctor
04 static again: 3
05 forName(false): Probe
Probe.<clinit>
06 forName(true)
07 loadClass: OnlyLoaded
OnlyLoaded.<clinit>
08 invoke: LOADED
09 int constant: 42
10 String constant: CONST
Constants.<clinit>
11 boxed: 99
12 Broken.class: Broken
Broken.fail
13 first: ExceptionInInitializerError
13 cause: IllegalStateException
14 second: NoClassDefFoundError
Healthy.<clinit>
15 Healthy: 7
~~~

Các nhãn số chỉ là mốc do chương trình in, không phải timestamp. Lời gọi `Class.forName` dùng chính defining loader của `InitializationLab` để tránh phụ thuộc vào context loader của thread hoặc môi trường bên ngoài.

## 6. Phân tích phần class literal và kế thừa

Trước lệnh `new Derived()`, output:

~~~text
01 main
02 class literal: Derived
03 construct
~~~

`Derived.class` và `getSimpleName()` không gây static initialization trong ví dụ. Nếu đã chạy `<clinit>`, bạn sẽ nhìn thấy `Base.field` hoặc `Derived.field` xuất hiện **trước** dòng `02`; thực tế không có.

Sau mốc `03`:

~~~text
Base.field
Base.block
Base.last
Derived.field
Derived.block
Base.ctor
Derived.ctor
~~~

Quan sát được cả **thứ tự giữa các class** và **thứ tự bên trong mỗi class**. `Base` phải initialize trước `Derived`; trong `Base`, initializer của `first`, block rồi initializer của `last`; sau đó tới `Derived.first` và block. Chỉ khi mọi static initializer hoàn tất, constructor `Base` và `Derived` mới chạy.

Nếu gọi `new Derived()` lần thứ hai trong cùng JVM, hai constructor sẽ chạy lại, nhưng các static initializers của runtime classes đã initialize không tự chạy thêm lần nữa.

## 7. Class.forName và loadClass có cùng hành vi không?

Không. Phần tiếp của stdout:

~~~text
05 forName(false): Probe
Probe.<clinit>
06 forName(true)
07 loadClass: OnlyLoaded
OnlyLoaded.<clinit>
08 invoke: LOADED
~~~

- `Class.forName(..., false, loader)` tìm/chuẩn bị class phù hợp nhưng **không yêu cầu chạy `Probe.<clinit>`**.
- `Class.forName(..., true, loader)` yêu cầu initialization, nên log `Probe.<clinit>` xuất hiện **trước** khi in mốc `06`.
- `ClassLoader.loadClass` tìm class `OnlyLoaded` nhưng không tự initialize nó; sau mốc `07` mới có `OnlyLoaded.<clinit>`.
- Static method `OnlyLoaded.value()` là active use, kích hoạt initialization trước khi kết quả `LOADED` được in.

**Không đồng nhất “chưa initialize” với “không thể lỗi”.** Loading và linking vẫn có thể gặp `ClassNotFoundException`, `ClassFormatError` hay `LinkageError`. Sự khác biệt ở đây chỉ về bước chạy class initialization method `<clinit>`.

## 8. Vì sao hằng static final không làm Constants khởi tạo?

Đầu ra:

~~~text
09 int constant: 42
10 String constant: CONST
Constants.<clinit>
11 boxed: 99
~~~

Hai trường:

~~~java
static final int NUMBER = 42;
static final String TEXT = "CONST";
~~~

là **constant variables**: kiểu primitive/String và có constant expression. Java compiler có thể inline giá trị vào bytecode của `InitializationLab.main`. Đọc chúng trong ví dụ không làm initialize `Constants`.

Trong khi đó:

~~~java
static final Integer BOXED = mark("Constants.<clinit>", 99);
~~~

không phải constant variable: `Integer` là reference type và giá trị được tính khi class initialization. Khi đọc `BOXED`, JVM initialize `Constants` và gọi `mark`.

Điều này liên quan versioning JAR: nếu một library đổi giá trị của `public static final int` compile-time constant, caller **đã biên dịch từ trước** có thể vẫn dùng literal cũ. Không thể suy từ giá trị ấy rằng ClassLoader “đã nạp phiên bản mới”.

## 9. Lần đầu lỗi initialization và lần dùng sau

Một đoạn quan trọng:

~~~java
static final class Broken {
    static int VALUE = fail();
}

private static int fail() {
    log("Broken.fail");
    throw new IllegalStateException("boom");
}
~~~

`Broken.class` không thực thi `fail()`. Khi lần đầu đọc `Broken.VALUE`, JVM bắt đầu initialize class và gọi `fail()`. Code ném `IllegalStateException` (không phải subclass của `Error`), nên JVM bọc lỗi vào `ExceptionInInitializerError`.

Trong stdout:

~~~text
12 Broken.class: Broken
Broken.fail
13 first: ExceptionInInitializerError
13 cause: IllegalStateException
14 second: NoClassDefFoundError
~~~

`Broken.fail` **chỉ xuất hiện một lần**. Sau lần khởi tạo thất bại, runtime class `Broken` ở trạng thái **erroneous**; JVM không tự retry `<clinit>` khi cùng runtime class được active use lần nữa. Lần dùng sau có thể nhận `NoClassDefFoundError`.

`NoClassDefFoundError` còn có thể do thiếu dependency hoặc sai tên class file, nên khi thấy thông báo `Could not initialize class` cần **truy tìm exception khởi tạo đầu tiên**, không mặc định là thiếu JAR.

### Một lưu ý về Error trực tiếp

Nếu static initializer ném `AssertionError` hoặc một `Error` khác, JVM có thể truyền chính `Error` ban đầu ra ngoài thay vì bọc vào `ExceptionInInitializerError`. Lần dùng sau của class erroneous vẫn có thể báo `NoClassDefFoundError`.

Không nên viết code production bắt `ExceptionInInitializerError` hoặc `NoClassDefFoundError` rồi tiếp tục vô điều kiện. Cần ghi nhận nguyên nhân gốc và sửa initialization code/config; một runtime class erroneous không được “sửa” bằng cách gọi constructor lần nữa.

## 10. Superclass initialization thất bại sẽ ngăn subclass

Ví dụ minh họa, **không phải file chạy độc lập**:

~~~java
class FailingBase {
    static int value = failure();

    static int failure() {
        throw new IllegalStateException("base init failed");
    }
}

class Child extends FailingBase {
    static {
        System.out.println("Child initializer");
    }

    static void go() {
    }
}
~~~

Khi `Child.go()` kích hoạt initialization của `Child`, JVM cần initialize `FailingBase` trước. Nếu `FailingBase` khởi tạo thất bại, `Child` không hoàn thành initialization và dòng `Child initializer` không được in.

Các side effects của những static initializers đã chạy **không được JVM rollback theo giao dịch**. Nếu chúng đã ghi file, đăng ký listener hoặc thay đổi global state trước khi ném exception, application phải tự thiết kế cleanup/khôi phục nếu cần.

## 11. Static field đọc giá trị mặc định trong khởi tạo vòng

Static fields được cấp giá trị mặc định trong preparation trước khi chạy initializer Java. Ví dụ minh họa:

~~~java
class Defaults {
    static int observed = readLater();
    static int later = 10;

    static int readLater() {
        return later;
    }
}
~~~

Nếu `Defaults` được initialize bình thường, `observed` có thể nhận `0` do `later` chưa được gán `10` ở thời điểm `readLater()` chạy. Sau khi toàn bộ initialization xong, `later == 10`.

Vì vậy:

- Đừng tự cho rằng một static field luôn nhìn thấy các giá trị final dự kiến của những fields khác trong lúc `<clinit>` đang chạy.
- Tránh dependency cycle giữa static initializers của nhiều classes.
- Hạn chế I/O, mutable shared state và đăng ký callback trong static initialization.
- Khi cần quản lý thứ tự dependency phức tạp, dùng explicit initialization/factory lifecycle với xử lý lỗi rõ ràng.

**Java memory/concurrency:** JVM đồng bộ hóa class initialization. Hai thread cùng active use một runtime class chưa initialized không tự chạy `<clinit>` hai lần. Nhưng hai static initializers của **hai classes khác nhau** gọi chéo có thể tạo deadlock; synchronization tự động không giải quyết mọi vòng phụ thuộc.

## 12. Một test mới phải có runtime class chưa initialized

Trạng thái initialized/erroneous thuộc về cặp **(binary name, defining loader)**. Chạy lại cùng class trong cùng loader không làm initializer chạy lần nữa, kể cả sửa `.class` trên đĩa. Cần một defining loader mới hoặc tiến trình JVM mới khi muốn reset thí nghiệm; bài cuối chương có cách tách kịch bản và xác minh loader mới thật sự sở hữu class.

## 13. Quan sát bytecode

Để hiểu vì sao static fields chạy theo thứ tự, dùng `javap` sau khi biên dịch:

~~~bash
javap -c -p InitializationLab
javap -c -p 'InitializationLab$Base'
javap -c -p 'InitializationLab$Derived'
javap -c -p 'InitializationLab$Constants'
~~~

Lệnh Bash/PowerShell dùng dấu nháy đơn để shell không xử lý ký tự `$` trong binary name của nested class. Trên Command Prompt có thể dùng dấu nháy kép.

Hãy tìm:

- `<clinit>` trong bytecode của `Base`, `Derived`, `Constants`.
- Các lệnh khởi tạo `first`, gọi `log/mark` và thứ tự lệnh bytecode.
- Constant values được compiler nhúng vào caller và lệnh `getstatic` cho `Constants.BOXED`.
- Phần `main` gọi `Class.forName` với boolean `false`/`true`.

Diagnostic logs của JVM có thể hỗ trợ quan sát class loading. Hãy kiểm tra tùy chọn có sẵn bằng:

~~~bash
java -Xlog:help
~~~

Log kiểu Unified Logging không phải stdout cố định: tag support và cách trình bày có thể khác nhau giữa các triển khai JVM.

## 14. Các lỗi thường gặp

| Nhầm lẫn | Cách hiểu đúng |
|---|---|
| `loadClass` trả class nghĩa static block chạy rồi | Loading và initialization khác nhau |
| `SomeClass.class` luôn khởi tạo class | Class literal không mặc nhiên kích hoạt `<clinit>` |
| Static fields luôn chạy hết trước static blocks | Tất cả static initializers chạy theo thứ tự source |
| Constructor Base chạy trước static initializer Derived | Khi tạo Derived object, class initialization cần hoàn tất trước object construction |
| Đọc constant field luôn initialize class | Compile-time constants có thể được inline |
| Lần dùng thứ hai của Broken sẽ gọi `fail()` thêm lần nữa | Class erroneous không tự retry initialization |
| `NoClassDefFoundError` chỉ xuất hiện khi thiếu file/JAR | Có thể sau class initialization failure |
| Catch một exception nghĩa đã rollback static side effects | Không có tự động rollback |
| Tạo object mới khiến static block chạy lại | Constructor mới chạy lại; `<clinit>` không chạy lại cho class đã initialized |
| ClassLoader mới tự sửa lỗi initializer | Có runtime class mới nhưng cùng lỗi code/config vẫn tồn tại |
| Class loader thành công nghĩa đã resolve mọi dependency | Resolution có thể trì hoãn |
| Hai thread chạy một class initializer hai lần | JVM đồng bộ hóa class initialization, nhưng vẫn có nguy cơ deadlock đa class |

## 15. Kiểm chứng theo kịch bản

Để tự kiểm tra, chạy chương trình mẫu trong **JVM mới** mỗi lần và đối chiếu stdout ở mục 5.2. Các thay đổi sau là **bài tập tùy chọn**; nếu thực hiện thì output sẽ thay:

1. Thêm lệnh `new Derived();` lần thứ hai; dự đoán constructor logs nhưng không có static logs lần hai.
2. Đổi `Class.forName(..., false, ...)` thành `true`; `Probe.<clinit>` sẽ xuất hiện sớm hơn.
3. Thay `static final int NUMBER = 42` bằng một giá trị runtime được tính qua method; lúc đọc trường, class initialization sẽ có tác dụng.
4. Đổi `Broken.fail()` để ném `AssertionError`, điều chỉnh catch và so sánh lần thất bại đầu.
5. Thêm một static field initializer trước `Base.block` rồi quan sát thay đổi thứ tự log.
6. Tách code vào JAR, chạy với loaders riêng; so sánh class identity và vòng đời initializer thay vì chỉ so tên class.

Hãy giữ log của **lỗi đầu tiên**: lần thứ hai có thể chỉ còn `NoClassDefFoundError` và không nói rõ nguyên nhân static initializer ban đầu.

## 16. Câu hỏi tự kiểm tra

**1.** Vì sao `Derived.class` không in `Base.field`?

**Đáp án:** Class literal không mặc nhiên khởi tạo class; `Base` và `Derived` chưa bị active use bởi biểu thức đó.

**2.** Khi `new Derived()`, vì sao `Base.block` trước `Derived.field`?

**Đáp án:** Superclass initialize trước subclass; mỗi class thực thi static initializers theo thứ tự source.

**3.** `Class.forName(name, false, loader)` và `Class.forName(name, true, loader)` khác gì?

**Đáp án:** `false` không yêu cầu initialization; `true` yêu cầu initialization.

**4.** Vì sao hai constant fields của `Constants` không kích hoạt static block, còn `BOXED` có?

**Đáp án:** Primitive/String constant variables có thể inline; `BOXED` là reference field được tính ở runtime, truy cập sẽ trigger initialization.

**5.** Vì sao lần đầu dùng `Broken.VALUE` có `ExceptionInInitializerError`?

**Đáp án:** Static field initializer ném `IllegalStateException`, JVM bọc lỗi không phải `Error` vào `ExceptionInInitializerError`.

**6.** Vì sao lần thứ hai có `NoClassDefFoundError` mà không chạy lại `Broken.fail`?

**Đáp án:** Runtime class đã ở trạng thái erroneous, JVM không tự khởi tạo lại.

**7.** Cùng binary name nhưng một class do loader L2 định nghĩa thay vì L1 có bắt buộc giữ trạng thái erroneous của L1 không?

**Đáp án:** Không. Hai defining loaders có thể định nghĩa hai runtime classes với vòng đời initialization riêng; nhưng nguyên nhân lỗi vẫn có thể tái diễn.

**8.** Một lỗi static initializer có tự rollback side effects không?

**Đáp án:** Không. JVM không tự đảo các thao tác đã chạy như ghi file hay đăng ký callback.

## 17. Tài liệu tham khảo

- [JLS Java SE 17 — Class Initialization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4): quy định khi nào và theo thứ tự nào initialization xảy ra.
- [JLS 17 — Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2): đồng bộ hóa và trạng thái lỗi.
- [JVMS 17 — Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5): quy tắc JVM và lỗi trong `<clinit>`.
- [Java 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): loading và class loader identity.
- [Java 17 — Class.forName](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)): overload điều khiển initialization.
- [Java 17 — ExceptionInInitializerError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ExceptionInInitializerError.html): lỗi khởi tạo lớp.
- [Java 17 — NoClassDefFoundError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html): lỗi định nghĩa class.

**Ghi nhớ:** **Loading ≠ initialization.** Static field initializers và static blocks chạy theo source order, sau initialization cần thiết của superclass; `loadClass`, class literal và `Class.forName(..., false, ...)` không ép `<clinit>`. Nếu `<clinit>` thất bại, class có thể thành erroneous: lượt đầu báo `ExceptionInInitializerError`, lượt sau báo `NoClassDefFoundError`. Một defining loader mới có thể tạo runtime class mới, nhưng không tự sửa lỗi ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-verification-005">Kiểm chứng package-private khi dùng hai loader</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng package-private khi dùng hai ClassLoader trong Java

## 1. Vấn đề: cùng tên package chưa chắc cùng quyền truy cập

Java cho phép các kiểu trong cùng một package sử dụng thành viên **package-private** của nhau. Một field, method hoặc constructor không ghi `public`, `protected` hay `private` có mức truy cập package-private (còn gọi là *package access*). Một top-level class không có `public` cũng chỉ có package access.

Ví dụ:

```java
package p;

public final class Gate {
    static String secret() {
        return "package ok";
    }
}
```

Một class khác có `package p;` thường có thể gọi `Gate.secret()` tại compile time. Tuy nhiên, khi tách các classes vào **hai defining ClassLoaders**, JVM có thể từ chối thao tác ấy dù **tên package không thay đổi**.

Theo **JVMS 17 `5.3**, một runtime package được xác định bởi **package name và defining ClassLoader**. Hai classes thông thường có cùng tên `p` nhưng được hai loaders khác nhau định nghĩa sẽ thuộc **hai runtime packages khác nhau**.

Đây là một lỗi dễ gặp trong kiến trúc plugin, servlet container, test runner và custom class loading:
- Maven/Gradle/IDE biên dịch hợp lệ vì source classes cùng `package p`.
- Sau deployment, class A do parent loader định nghĩa, class B do plugin/child loader định nghĩa.
- Lời gọi một method package-private từ B sang A không còn hợp lệ tại runtime.
- JVM ném **`IllegalAccessError`**, chứ không phải `ClassNotFoundException`.

Bài học hướng dẫn kiểm chứng **từng mắt xích**, có chương trình Java 17+ chạy được và stdout xác định.

## 2. Phân biệt compile-time package và runtime package

### 2.1. Khi Java compiler kiểm tra source

Hai source files:

```java
// File p/Gate.java
package p;

public class Gate {
    static String secret() {
        return "package ok";
    }
}
```

```java
// File p/Caller.java
package p;

public class Caller {
    public static String call() {
        return Gate.secret();
    }
}
```

Cả hai cùng được compiler nhìn nhận thuộc package `p`. Khi compile trong một module/source context thích hợp, `Gate.secret()` được phép vì Caller ở cùng package.

Compiler sẽ sinh một symbolic reference tới method `p/Gate.secret:()Ljava/lang/String;` trong bytecode Caller. Reference này **chưa trỏ trực tiếp tới một đối tượng Class cố định trong JVM chạy sau này**.

### 2.2. Khi JVM giải quyết reference

Ở runtime, JVM xác định:
- `p.Caller` là runtime class do loader nào định nghĩa?
- `p.Gate` được tìm/định nghĩa bởi loader nào?
- Hai runtime classes có **cùng runtime package** không?
- Method `secret()` có access flags gì?
- Trong named modules, readability/exports còn ảnh hưởng tới các kiểu công khai không?

Nếu Caller và Gate ở hai defining loaders khác nhau, chỉ cùng text package name `"p"` là **không đủ** để cho package-private access.

### 2.3. Sơ đồ hai trạng thái

```text
Trường hợp A: cùng defining loader
  SharedLoader
    ├── p.Gate   (package p, loader SharedLoader)
    └── p.Caller (package p, loader SharedLoader)
  Caller.call() → Gate.secret() → OK

Trường hợp B: hai defining loaders
  GateLoader
    └── p.Gate   (package p, loader GateLoader)
         ▲
         │ parent delegation
  CallerLoader
    └── p.Caller (package p, loader CallerLoader)
  Caller.call() → Gate.secret() → IllegalAccessError
```

**Định nghĩa type identity:** runtime class thường được xác định bởi **binary name + defining loader**. Runtime package dùng **package name + defining loader**. Hai quy tắc có liên quan nhưng không được gộp thành một.

## 3. Initiating loader không nhất thiết là defining loader

`ClassLoader.loadClass("p.Gate")` có thể được gọi trên `CallerLoader` nhưng kết quả thực sự do `GateLoader` (parent) định nghĩa.

Ví dụ:

```text
CallerLoader.loadClass("p.Gate")
     │
     ├── findLoadedClass("p.Gate") không có
     │
     ▼
GateLoader.loadClass("p.Gate")
     │
     └── GateLoader.findClass → defineClass
               │
               ▼
         Trả Class<?> của GateLoader
```

Vì vậy:
- **Initiating loader:** loader được dùng để khởi xướng hoặc ghi nhận yêu cầu load.
- **Defining loader:** loader thực sự định nghĩa runtime class.
- `Class.getClassLoader()` trả **defining loader**.
- Gọi `loadClass` trên loader con không biến mọi class nó trả về thành class do loader con định nghĩa.

Điều này giải thích tại sao `CallerLoader` tìm thấy `Gate` bình thường mà **không** có quyền package-private với `Gate`.

## 4. Một chương trình Java 17+ tái hiện đúng lỗi

Thí nghiệm sẽ tạo **một tập bytecode duy nhất** cho `Gate` và `Caller`, sau đó chạy hai cấu hình loader khác nhau. Ta không thay source/bytecode giữa hai cấu hình. Như vậy khác biệt quan sát được đến từ **defining ClassLoader identity**.

### 4.1. Cấu trúc thư mục

```text
package-access-demo/
├── src/
│   └── p/
│       ├── Gate.java
│       └── Caller.java
├── Host.java
├── compiled/
│   └── p/
│       ├── Gate.class
│       └── Caller.class
└── host-out/
    └── Host.class
```

Ban đầu chỉ cần tạo `src/p/Gate.java`, `src/p/Caller.java` và `Host.java`. Các thư mục output sẽ được compiler tạo khi chạy lệnh cuối bài.

### 4.2. File `src/p/Gate.java`

```java
package p;

public final class Gate {
    static String secret() {
        return "package ok";
    }
}
```

`Gate` là class public. Riêng method `secret` không có access modifier, tức **package-private**.

### 4.3. File `src/p/Caller.java`

```java
package p;

public final class Caller {
    public static String call() {
        return Gate.secret();
    }
}
```

`Caller.call()` là public entry point để host gọi qua reflection. Phép truy cập package-private xảy ra **bên trong method Caller's bytecode**.

### 4.4. File `Host.java` — toàn bộ source

```java
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class Host {
    static final class DiskLoader extends ClassLoader {
        private final Path root;
        private final Set<String> owned;

        DiskLoader(Path root, ClassLoader parent,
                   Set<String> owned) {
            super(parent);
            this.root = root;
            this.owned = owned;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    result = owned.contains(name)
                            ? findClass(name)
                            : super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(result);
                }
                return result;
            }
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!owned.contains(name)) {
                throw new ClassNotFoundException(name);
            }

            Path file = root.resolve(
                    name.replace('.', '/') + ".class");
            try {
                byte[] bytes = Files.readAllBytes(file);
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException error) {
                throw new ClassNotFoundException(name, error);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("compiled");

        // Cấu hình A: cùng loader định nghĩa cả hai lớp.
        DiskLoader shared = new DiskLoader(
                root,
                Host.class.getClassLoader(),
                Set.of("p.Gate", "p.Caller"));

        Class<?> goodCaller = shared.loadClass("p.Caller");
        Class<?> goodGate = shared.loadClass("p.Gate");

        System.out.println("same-loader class access: "
                + goodCaller.getMethod("call").invoke(null));
        System.out.println("same-loader defining identity: "
                + (goodCaller.getClassLoader()
                   == goodGate.getClassLoader()));

        // Cấu hình B: parent định nghĩa Gate,
        // child định nghĩa Caller.
        DiskLoader gateLoader = new DiskLoader(
                root,
                Host.class.getClassLoader(),
                Set.of("p.Gate"));

        DiskLoader callerLoader = new DiskLoader(
                root,
                gateLoader,
                Set.of("p.Caller"));

        Class<?> gate = gateLoader.loadClass("p.Gate");
        Class<?> caller = callerLoader.loadClass("p.Caller");

        System.out.println("same package text: "
                + gate.getPackageName().equals(
                        caller.getPackageName()));
        System.out.println("same defining loader: "
                + (gate.getClassLoader()
                   == caller.getClassLoader()));
        System.out.println("same runtime class Gate: "
                + (gate == goodGate));

        try {
            System.out.println("cross-loader call: "
                    + caller.getMethod("call").invoke(null));
        } catch (InvocationTargetException error) {
            // Method.invoke bọc lỗi ném từ target method.
            if (error.getCause() instanceof IllegalAccessError) {
                System.out.println(
                        "cross-loader call: IllegalAccessError");
            } else {
                throw error;
            }
        } catch (IllegalAccessError error) {
            System.out.println(
                    "cross-loader call: IllegalAccessError");
        }

        // Reflection từ Host ngoài package p:
        Method secret = gate.getDeclaredMethod("secret");
        try {
            secret.invoke(null);
            System.out.println(
                    "host reflection: unexpectedly allowed");
        } catch (IllegalAccessException error) {
            System.out.println(
                    "host reflection: IllegalAccessException");
        }
    }
}
```

### 4.5. Biên dịch và chạy

Ở thư mục `package-access-demo` trên Bash/Linux/macOS:

```bash
mkdir -p compiled host-out
javac --release 17 -d compiled src/p/Gate.java src/p/Caller.java
javac --release 17 -d host-out Host.java
java -cp host-out Host
```

Trên Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force compiled,host-out | Out-Null
javac --release 17 -d compiled src/p/Gate.java src/p/Caller.java
javac --release 17 -d host-out Host.java
java -cp host-out Host
```

**Quan trọng:** Chỉ đưa `host-out` vào host application classpath. **Không** thêm `compiled` vào `-cp`, nếu không application parent loader có thể tự load Gate/Caller trước custom loader và vô tình phá giả thiết thí nghiệm.

`--release 17` chọn class-file version/API target Java 17; chương trình có thể chạy trên JDK 17+ phù hợp.

### 4.6. Stdout đã được kiểm chứng

```text
same-loader class access: package ok
same-loader defining identity: true
same package text: true
same defining loader: false
same runtime class Gate: false
cross-loader call: IllegalAccessError
host reflection: IllegalAccessException
```

Thí nghiệm đã biên dịch bằng `javac --release 17` và chạy trên OpenJDK 21. Các dòng in là các giá trị/loại lỗi, không phụ thuộc vào message chi tiết có thể khác giữa JVM implementations.

## 5. Tại sao cấu hình A thành công?

Trong cấu hình A:

```java
DiskLoader shared = new DiskLoader(
        root,
        Host.class.getClassLoader(),
        Set.of("p.Gate", "p.Caller"));
```

`shared` tự định nghĩa cả hai binary names. JVM thấy:
- `p.Gate` thuộc package `p` do `shared` định nghĩa.
- `p.Caller` cũng thuộc package `p` do `shared` định nghĩa.
- Cả hai có cùng **runtime package (p, shared)**.
- `Caller.call()` được phép tham chiếu method package-private `Gate.secret()`.

Kết quả:

```text
same-loader class access: package ok
same-loader defining identity: true
```

Host **không gọi `Gate.secret` trực tiếp**. Host gọi method `public Caller.call`, và JVM thực thi bytecode bên trong Caller — nơi runtime package access hợp lệ.

Sự khác nhau rất quan trọng: nếu Host trực tiếp reflectively invoke `Gate.secret` mà chưa có access override, reflective caller là Host (ở ngoài package), nên bị từ chối.

## 6. Tại sao cấu hình B ném IllegalAccessError?

Trong cấu hình B:

```java
DiskLoader gateLoader = new DiskLoader(
        root,
        Host.class.getClassLoader(),
        Set.of("p.Gate"));

DiskLoader callerLoader = new DiskLoader(
        root,
        gateLoader,
        Set.of("p.Caller"));
```

`callerLoader` sở hữu `p.Caller`, còn `p.Gate` được parent `gateLoader` định nghĩa. Khi Caller cần tham chiếu Gate, loader của Caller có thể delegate lên gateLoader, nên **class lookup thành công**.

Nhưng access check khác class lookup:
- `gate.getPackageName()` = `"p"`.
- `caller.getPackageName()` = `"p"`.
- `gate.getClassLoader() != caller.getClassLoader()`.
- Vậy **hai runtime packages khác nhau**.
- `Gate.secret()` vẫn package-private.
- Khi JVM resolve/access method reference, Caller không có quyền gọi.

Kết quả:

```text
same package text: true
same defining loader: false
same runtime class Gate: false
cross-loader call: IllegalAccessError
```

**Điều kiện gây lỗi không phải bytes hỏng**. Hai source files/bytecode giống hệt cấu hình A, và JVM đã tìm thấy cả hai. Khác biệt là **ai định nghĩa từng class**.

`IllegalAccessError` là subclass của `IncompatibleClassChangeError` rồi `LinkageError`. Nó không phải checked exception để code nghiệp vụ thông thường có thể xử lý như dữ liệu lỗi đầu vào.

## 7. Hai loại lỗi truy cập khác nhau: IllegalAccessError và IllegalAccessException

Mục cuối chương trình in:

```text
host reflection: IllegalAccessException
```

Tại đây Host lấy method qua reflection:

```java
Method secret = gate.getDeclaredMethod("secret");
secret.invoke(null);
```

`getDeclaredMethod` có thể tìm mô tả method, kể cả member không public. Nhưng `Method.invoke` thực hiện **reflection access check đối với reflective caller**. Host không thuộc runtime package của Gate, nên lời gọi khi chưa override gây **checked `IllegalAccessException`**.

Phân biệt:

| Lỗi | Cơ chế | Ví dụ |
|---|---|---|
| `IllegalAccessError` | JVM linkage/bytecode member hoặc type access | Caller cross-loader gọi Gate.secret |
| `IllegalAccessException` | Reflective invocation từ caller không có quyền | Host gọi Method.invoke chưa override |
| `InaccessibleObjectException` | `AccessibleObject.setAccessible` bị từ chối, thường vì JPMS encapsulation | Deep reflection từ module không có opens |
| `ClassCastException` | Runtime object không assignable tới target type | Hai bản api.Service khác defining loaders |
| `ClassNotFoundException` | Loader lookup không tìm thấy binary name | Directory/JAR không có class |

Nếu phản chiếu một method **public** của một **public class** mà module access phù hợp, reflection thường có thể gọi từ code ngoài package. Nhưng `getDeclaredMethod` “tìm thấy metadata” không làm package-private method tự thành public.

### Vì sao demo bắt InvocationTargetException?

`Method.invoke` có thể **bọc lỗi do target method ném** trong `InvocationTargetException`. Khi `Caller.call()` thực thi rồi ném `IllegalAccessError`, Host có thể nhận wrapper và phải kiểm tra `getCause()`. Do thời điểm resolution/linking có thể thay đổi, chương trình cũng bắt trực tiếp `IllegalAccessError` ở vùng thử nghiệm.

**Đừng biến mọi InvocationTargetException thành IllegalAccessError.** Phải kiểm tra cause thực tế và giữ lỗi khác, như chương trình đã làm.

## 8. Có thể dùng trySetAccessible để “sửa” không?

Đối với **unnamed modules** của ví dụ classpath này, một reflective caller có thể bật access override vào method package-private:

```java
// Trích đoạn, sau khi biến gate đã tồn tại.
Method secret = gate.getDeclaredMethod("secret");
boolean allowed = secret.trySetAccessible();

System.out.println("reflective override: " + allowed);

if (allowed) {
    System.out.println("reflected value: "
            + secret.invoke(null));
}
```

Trong fixture thuộc unnamed modules và với quyền truy cập thích hợp, kết quả dự kiến:

```text
reflective override: true
reflected value: package ok
```

**Nhưng đây không phải bản sửa cho direct bytecode access**:
- `trySetAccessible` thay đổi accessibility của **reflection operation** thông qua `Method` cụ thể.
- Nó không thay đổi defining ClassLoader của Gate/Caller.
- Nó không thay method access flags trong bytecode.
- Nó không làm hai runtime packages khác nhau thành một.
- `Caller.call()` vẫn gọi `invokestatic p/Gate.secret` không hợp lệ trong split-loader configuration.

Với **named modules**, deep reflection còn chịu điều kiện `opens` và các module access rules, nên `trySetAccessible()` có thể trả `false`. `setAccessible(true)` có thể ném `InaccessibleObjectException`.

**Đừng coi mở reflection là cách thiết kế plugin boundary.** Nếu host/plugin cần giao tiếp, nên sử dụng public shared API, không phụ thuộc vào reflective override.

## 9. Nếu Gate.secret là public thì chuyện gì đổi?

Giữ class Gate `public` nhưng đổi method thành:

```java
package p;

public final class Gate {
    public static String secret() {
        return "public API";
    }
}
```

Trong **mô hình unnamed-module classpath** của demo, caller thuộc runtime package khác vẫn có thể gọi public method trên public class nếu các điều kiện truy cập khác không cản trở.

Sau khi sửa source:
1. Biên dịch lại Gate/Caller đúng target.
2. Tạo **JVM hoặc loader mới** để tránh runtime class cũ đã được định nghĩa.
3. Chạy lại hai trường hợp và xác nhận method thực sự public trên artifact đích.

Nếu nội bộ framework không muốn method công khai, **đừng tùy tiện đổi toàn bộ package-private thành public**. Thiết kế tốt hơn:
- giữ nhóm implementation cooperating classes trong **cùng defining loader**;
- tạo **public facade/interface** nhỏ tại biên host/plugin;
- đưa shared API lên parent/common loader hoặc shared module.

### Quyền truy cập theo từng cấu hình

| Cấu hình | Package-private method qua direct bytecode | Public method trên public class |
|---|---|---|
| Cùng package name, cùng defining loader | Hợp lệ theo package access | Hợp lệ nếu không bị điều kiện khác chặn |
| Cùng package name, khác defining loaders | Không chỉ nhờ tên package | Có thể hợp lệ nếu class/member/module access phù hợp |
| Khác package name, cùng loader | Không có quyền package-private | Có thể hợp lệ |
| Child nhìn class parent qua delegation | Không thành cùng runtime package nếu defining loaders khác | Public API là cách chia sẻ phù hợp |
| Named modules/ModuleLayers | Còn phải xét module graph/runtime package | Còn phải xét readability/exports |

## 10. Package-private top-level type cũng bị ảnh hưởng

Một trường hợp nghiêm trọng hơn là class `Helper` tự nó package-private:

```java
package p;

final class Helper {
    static String value() {
        return "internal";
    }
}
```

Class khác trong package:

```java
package p;

public final class Entry {
    public static String run() {
        return Helper.value();
    }
}
```

Đây là **hai source files minh họa**, không phải một runnable project độc lập ở phần này. Compiler có thể cho phép `Entry` truy cập `Helper` nếu source thuộc cùng package.

Nhưng nếu runtime `Helper` được Loader-A định nghĩa, `Entry` được Loader-B định nghĩa, JVM có thể chặn việc truy cập **chính kiểu `Helper`** vì package-private type access, trước khi xét method bên trong.

Case chính cố ý dùng **public class Gate, package-private method secret** để minh họa một loại lỗi **member access** riêng, không trộn thêm quyền truy cập của class declaration.

## 11. Class.getPackageName và Package object dễ gây hiểu nhầm

`Class.getPackageName()` cho chuỗi tên package, không chứa defining loader identity. Do đó:

```java
boolean sameText =
        gate.getPackageName().equals(caller.getPackageName());
```

`sameText` là `true` trong case B nhưng Java vẫn từ chối package-private call.

`java.lang.Package` là metadata về package:
- `Class.getPackage()` trả thông tin Package tương ứng class.
- `ClassLoader.getDefinedPackage(name)` tra package do chính loader đó định nghĩa.
- `ClassLoader.getPackage(name)` đã deprecated từ Java 9 và có thể tìm qua parent loader; dùng nó để suy từ đó về quyền package-private rất dễ sai.

**Runtime package identity** không được suy chỉ từ `Package.getName()`, `Package.isSealed()` hoặc annotations. Nên lấy hai `Class<?>` và kiểm tra **tên package + defining loader**.

Cũng không nên dùng riêng `CodeSource`:
- Hai loaders có thể đọc **cùng tệp .class** trên đĩa nhưng tạo hai runtime classes khác nhau.
- Custom `defineClass` có thể gắn CodeSource null/không hữu ích.
- Java agent có thể transform bytes, khiến source URL và bytes cuối khác nhau.

`CodeSource` hữu ích để truy vết artifact, nhưng **không thay ClassLoader identity**.

## 12. ClassCastException cũng liên quan hai loader, nhưng là lỗi khác

Giả sử host và plugin tự định nghĩa riêng một interface cùng binary name:

```text
HostLoader
  └── api.Service [Class A]

PluginLoader
  ├── api.Service [Class B]
  └── plugin.Impl implements Class B
```

Host muốn ép object `plugin.Impl` sang `api.Service` của host (Class A). Hai runtime interfaces cùng tên nhưng khác defining loader nên **không phải cùng kiểu**. Phép cast có thể ném `ClassCastException`.

Đây **không phải** lỗi package-private của `Gate.secret`. Phân biệt:
- **`IllegalAccessError`:** quyền truy cập type/member không hợp lệ.
- **`ClassCastException`:** object không thuộc runtime target type.
- **`NoSuchMethodError`:** caller compiled method reference nhưng runtime class không có method signature phù hợp.

Ba lỗi đều có thể xuất hiện khi loader topology thay đổi, nhưng cách sửa không giống nhau.

## 13. Thiết kế shared API cho plugin

Kiến trúc nên ưu tiên:

```text
          Common Parent ClassLoader
                  │
                  └── api.PluginService
                      public interface
                         ▲
                         │ implements
            ┌────────────┴────────────┐
            │                         │
       PluginLoader-A             PluginLoader-B
       plugin.AImpl               plugin.BImpl
       plugin internals           plugin internals
```

Service contract có thể là:

```java
package api;

public interface PluginService {
    String execute();
}
```

Đây là file `api/PluginService.java` minh họa, biên dịch được nhưng không có main. Host và plugin phải dùng **cùng runtime Class<?> của interface này**, do parent/common loader định nghĩa.

Với mỗi plugin:
- Implementation classes và package-private helpers nên ở cùng defining loader nếu chúng cần package access.
- Giao tiếp cross-loader phải đi qua public shared types/contract hoặc dữ liệu trung gian.
- Tránh cho plugin child tự định nghĩa lại host API packages.
- Nếu dùng child-first để cô lập dependencies, bảo đảm shared API namespaces được delegate đúng parent.
- Chú ý method signatures của shared API: DTO/exception types cũng có thể bị định nghĩa trùng ở loader khác gây identity problems.
- Test plugin trong loader architecture thật, không chỉ với flat classpath IDE.

## 14. Tại sao child-first dễ tạo “split runtime package”?

Một custom child-first loader có thể ưu tiên class files trong plugin JAR:
- `p.Caller` có trong plugin → child tự define.
- `p.Gate` không có trong plugin → child delegate parent.
- Hai classes cùng package `p` trên source → nhưng runtime package khác.

Một số framework có parent-first ngoại lệ cho platform API, nhưng local-first với application classes. Mỗi container/plugin framework có rules riêng. Không được suy rằng loader nào cũng thực hiện parent-first chuẩn trong mọi namespace.

**Cách phòng ngừa:** Đừng split nửa nhóm internal classes giữa parent và child rồi kỳ vọng package-private. Hãy chọn một loader owner nhất quán cho nhóm đó hoặc publicize đúng extension boundary.

Những sự thay đổi như:
- chuyển JAR giữa common/lib và plugin/lib;
- shadow một package của host;
- nâng application server;
- chuyển test runner từ một loader sang nhiều loaders;
- tạo ModuleLayer mới;

đều cần regression test về runtime class identity và access.

## 15. JPMS bổ sung quyền nhưng không xóa runtime package

Java 9+ có named modules:
- `requires` / readability;
- `exports` cho API truy cập thông thường;
- `opens` cho deep reflection runtime;
- `ModuleLayer` và module-to-loader mapping.

**Exports không làm package-private members trở thành public.** **Opens không hợp pháp hóa direct bytecode invocation từ class khác runtime package.**

Ví dụ descriptor:

```java
module app.api {
    exports app.api;
}
```

```java
module app.plugin {
    requires app.api;
    provides app.api.Service with app.plugin.Impl;
}
```

Đây là **hai descriptors minh họa**, cần thực sự có `app.api.Service` và `app.plugin.Impl` khi đưa vào dự án hoàn chỉnh. Plugin có thể cung cấp implementation qua `ServiceLoader` mà không export toàn bộ implementation package cho consumer.

Với named modules, còn cần xác nhận các điều kiện module access cho public types. Nhưng **dù export package `p`, class ở hai defining loaders vẫn không được dùng package-private lẫn nhau chỉ nhờ trùng tên `p`**.

Đặc biệt, `--add-opens` là giải pháp có phạm vi cho deep reflection khi hợp lệ, không phải “fix” cho một symbolic `invokestatic` package-private cross-loader.

## 16. Debug đúng lỗi trong container, test runner và plugin host

Checklist thực tế:

1. **Ghi error gốc**, class nào gọi member nào, không chỉ dòng “plugin failed”.
2. **Đọc cause chain**. Nếu gọi qua `Method.invoke`, phân biệt error trực tiếp và `InvocationTargetException.getCause()`.
3. **Lấy `Class<?>` của caller và target** từ đúng loader context nếu có thể.
4. **In tên package** bằng `getPackageName()`.
5. **In defining loaders** bằng `getClassLoader()` và so identity bằng `==`.
6. **Kiểm tra parent delegation graph** và nơi hai class được định nghĩa, không chỉ nơi caller gọi `loadClass`.
7. **Xem class file thực** bằng `javap -p -c`; xác định method/field có package access hay public/protected/private.
8. **Kiểm tra binary API mismatch** nếu có `NoSuchMethodError` thay vì `IllegalAccessError`.
9. **Kiểm tra JPMS** nếu class ở named modules hoặc reflection bị opens/exports từ chối.
10. **Kiểm tra test/prod classpaths** và việc host/container cung cấp shared classes bên ngoài plugin.
11. **Tạo loader/JVM mới cho thử nghiệm**, tránh cached class definitions.
12. **Chọn sửa API hoặc loader placement** rồi chạy integration test trên kiến trúc thực tế.

### ClassLoader probe độc lập

```java
public final class PackageProbe {
    private PackageProbe() {}

    public static void print(String name, Class<?> type) {
        System.out.println(name + " class=" + type.getName());
        System.out.println(name + " package="
                + type.getPackageName());
        System.out.println(name + " defining loader="
                + type.getClassLoader());
        System.out.println(name + " module="
                + type.getModule());
    }
}
```

Đây là file `PackageProbe.java` biên dịch được, **không có main**; tích hợp vào host/test code. Giá trị loader/module string là dữ liệu runtime, **không có stdout cố định**.

### Kiểm tra bytecode

Sau khi chạy ví dụ mục 4:

```bash
javap -p -c -classpath compiled p.Gate
javap -p -c -classpath compiled p.Caller
```

- `-p` hiển thị members không public.
- `-c` hiển thị instructions.
- Có thể thấy `Caller.call` chứa `invokestatic` tới `Gate.secret`.
- `javap` trên output files xác nhận **bytecode trên đĩa**; nó không tự nói JVM process đang dùng defining loader nào.

## 17. Vì sao Package Sealing không phải giải pháp?

JAR manifest có thể **seal package**, làm việc định nghĩa classes trong package tuân các ràng buộc về code source trong một loader. Package sealing và package-private access là hai khái niệm khác nhau.

Không suy từ:
- `Package.isSealed() == true`;
- cùng tên package;
- cùng JAR filename;
- cùng CodeSource URL;

rằng hai classes thuộc cùng runtime package nếu **defining loaders khác nhau**.

Sealing violation có thể có lỗi riêng, nhưng nó không gộp hai runtime packages thành một và không nâng quyền package-private.

## 18. Các hiểu nhầm phổ biến

| Nhận định sai | Điều đúng |
|---|---|
| Hai classes có cùng `package p;` thì chắc cùng package runtime | Phải có cùng defining loader (theo JVMS `5.3) |
| Code compile được thì package-private chắc chạy được | Deployment có thể thay loader boundaries |
| Loader child tìm class parent nên class đó thuộc child | Parent vẫn có thể là defining loader |
| `getPackageName` giống là đủ | Cần so defining loaders |
| Hai class files giống hệt bytes nghĩa là cùng runtime Class | Không nếu do hai loaders độc lập định nghĩa |
| `ClassLoader.getPackage(name)` đủ xác nhận package-private | Có thể tìm Package metadata từ parent; không kết luận runtime package |
| `CodeSource` giống thì runtime class giống | Không; type identity phụ thuộc loader |
| `setAccessible(true)` sửa mọi lỗi package-private | Chỉ ảnh hưởng reflection access theo điều kiện |
| `--add-opens` sửa direct package-private bytecode access | Không |
| Cứ đổi child-first sang parent-first là an toàn | Cần kiểm tra shared API, versions và classpath |
| `IllegalAccessException` và `IllegalAccessError` là một | Reflection checked exception khác linkage Error |
| `ClassCastException` hai loaders cũng chính là package-private error | Khác: type assignability so với member access |
| JPMS exports mọi thứ trong package là public | Không làm non-public members thành public |
| Thay JAR trên disk làm JVM dùng class mới ngay | Runtime classes đã loaded thường cần loader/process mới |
| Đặt plugin internal cùng package name của host là được phép truy cập host internals | Không nếu defining loaders khác, còn module constraints |

## 19. Câu hỏi tự kiểm tra

1. **Package-private cần modifier nào?** Không ghi `public`, `protected` hoặc `private`.
2. **Dòng `package p;` có đủ xác nhận package runtime identity không?** Không.
3. **JVMS xác định runtime package bằng gì?** Package name và defining ClassLoader.
4. **Defining loader có phải luôn là loader mà client gọi `loadClass` không?** Không.
5. **Hai loaders khác nhau đọc cùng bytecode có tạo cùng runtime class không?** Không nếu mỗi loader tự define.
6. **Vì sao cấu hình A gọi `Gate.secret` được?** Gate/Caller thuộc cùng runtime package do shared loader định nghĩa.
7. **Vì sao cấu hình B bị từ chối?** Gate/Caller do hai loaders khác nhau định nghĩa; package-private không accessible.
8. **Lỗi trong Caller.call ở cấu hình B?** `IllegalAccessError`.
9. **Lỗi Host reflective invoke `secret` chưa override?** `IllegalAccessException`.
10. **`InvocationTargetException` có thể bọc lỗi nào?** Throwable do target method ném, gồm `IllegalAccessError` trong tình huống minh họa.
11. **`trySetAccessible` có làm JVM package identity thay đổi không?** Không.
12. **Mở module bằng `opens` có làm direct cross-loader package-private call hợp lệ không?** Không.
13. **`Class.getPackageName()` hai classes bằng nhau mà loader khác thì sao?** Chúng vẫn khác runtime package.
14. **Top-level class package-private có thể bị access check từ loader khác không?** Có.
15. **Khi Gate.secret đổi thành public, mô hình unnamed-module còn bị package-private chặn không?** Không, miễn các điều kiện access khác thỏa.
16. **Một public interface để chia sẻ host/plugin nên đặt ở đâu?** Shared parent loader/module ổn định.
17. **Vì sao Host chỉ chạy `-cp host-out`?** Tránh parent định nghĩa các target classes từ compiled.
18. **Muốn chẩn đoán runtime package, nên in những gì?** Class name, package name, defining loader, module và source nếu có.
19. **Sealing package có gộp hai loaders không?** Không.
20. **Compile pass nhưng runtime IllegalAccessError nên kiểm tra gì trước?** Member flags, caller/target defining loaders, loader topology và module rules.

## 20. Tài liệu tham khảo chính thức

- [JVMS 17 5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3): defining/initiating loader, runtime class identity, **runtime package**.
- [JVMS 17 5.4.4 — Access Control](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.4): JVM checks khi resolution classes/members.
- [JLS 17 6.6 — Access Control](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6): package-private, protected, public và private trong Java source.
- [Java SE 17 ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findClass`, `defineClass` và `getDefinedPackage`.
- [Java SE 17 Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getPackageName`, `getClassLoader` và `getModule`.
- [Java SE 17 IllegalAccessError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/IllegalAccessError.html): lỗi truy cập member/type lúc linkage/runtime.
- [Java SE 17 Method](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): `invoke` và InvocationTargetException.
- [Java SE 17 AccessibleObject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html): `canAccess`, `trySetAccessible`, các quy tắc reflection access.
- [Java SE 17 Module](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html): module readability, exports và opens.

**Kết luận:** Khi code Java dùng package-private, **cùng tên package trong source không đủ**. Hai classes phải thuộc **cùng runtime package**, tức có package name và defining ClassLoader tương ứng. Trong ví dụ, **cùng loader → thành công**, còn **tách Gate và Caller qua parent/child defining loaders → IllegalAccessError**. Để thiết kế plugin/container đáng tin, hãy đặt các internal classes cần package access ở **cùng loader**, và giao tiếp qua **public shared API** khi đi qua ranh giới loader.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-verification-006">Kiểm chứng tìm tài nguyên và provider dịch vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng tìm tài nguyên và provider dịch vụ trong Java ClassLoader

Một hệ thống plugin có thể **nạp được class** nhưng vẫn **không đọc được resource** hoặc **không tìm thấy service provider**. Ngược lại, một `getResource(...)` có thể trả URL hợp lệ nhưng lấy **bản resource của parent**, không phải bản cùng tên ở plugin child. Do đó, khi kiểm thử classloader, không thể chỉ assert “`loadClass` thành công” rồi kết luận phần resources và SPI cũng đúng.

Bài này xây dựng một quy trình **kiểm chứng hành vi** cho ba thành phần:

1. **Tìm resource:** `ClassLoader.getResource`, `getResources`, `getResourceAsStream`, `URLClassLoader.findResource`, `Class.getResource`.
2. **Phát hiện service provider:** SPI, `META-INF/services/<binary-service-name>`, `ServiceLoader`, lazy discovery và provider constructor.
3. **Đúng classloader boundary:** parent delegation, defining loader của provider, shared SPI type, TCCL và named modules khi áp dụng.

Kết quả phải được kiểm bằng **tên/path thực sự được yêu cầu, loader được dùng, URL nguồn trả về, số provider và runtime type identity**. Không dựa vào việc một thư mục/JAR “có file đó trên đĩa” như bằng chứng đủ.

## 1. Mục tiêu của kiểm thử resource và service provider

Giả sử ứng dụng:

~~~text
ApplicationLoader
  |
  +-- api.Formatter (SPI interface)
  +-- config/marker.txt         (resource: parent-data)
  |
  +-- PluginLoader (child)
        |
        +-- plugin.PluginFormatter.class
        +-- META-INF/services/api.Formatter
        +-- config/marker.txt   (resource: plugin-data)
~~~

Ta muốn xác minh:

- Parent load được **`api.Formatter`** và child dùng **cùng runtime API type**, không tự định nghĩa một bản SPI khác.
- `PluginLoader.getResource("config/marker.txt")` theo delegation mặc định có thể trả **parent resource**.
- `PluginLoader.findResource("config/marker.txt")` (nếu là `URLClassLoader`) tìm nguồn URL **riêng child**.
- `PluginLoader.getResources("config/marker.txt")` có thể cho thấy **cả hai tài nguyên**, không chỉ kết quả đầu.
- `ServiceLoader.load(api.Formatter.class, parent)` không tìm provider của plugin vì descriptor/class ở child.
- `ServiceLoader.load(api.Formatter.class, pluginLoader)` tìm đúng **một provider**, khởi tạo được và provider implement đúng runtime SPI.
- `ServiceLoader.load(api.Formatter.class)` dùng **thread context class loader (TCCL)**; đặt đúng pluginLoader trong phạm vi có kiểm soát giúp tìm provider; cuối cùng phải restore TCCL.

Đây là một **contract test của loader architecture**: nếu bất kỳ assertion nào khác dự kiến, cần xem lại cấu trúc classpath, descriptor, parent chain, runtime types hoặc resource policy.

## 2. Các API resource có quy tắc đường dẫn khác nhau

### 2.1. `ClassLoader.getResource(String)`

~~~java
URL url = pluginLoader.getResource("config/marker.txt");
~~~

Đây là mảnh minh họa, cần biến `pluginLoader` đã có và import `java.net.URL`.

- Nhận tên resource theo **path từ gốc search path**, sử dụng dấu `/`.
- Trả **một URL** hoặc `null`.
- Hành vi mặc định của `ClassLoader` hỏi parent trước, rồi tới `findResource` của loader hiện tại.
- Nếu parent và child đều chứa resource cùng tên, **parent có thể che child**.
- Trong named modules, những resource thuộc package của named module có thể chịu quy tắc `opens` và encapsulation; có URL physical không đồng nghĩa ClassLoader API nhất thiết trả nó.

### 2.2. `ClassLoader.getResources(String)`

~~~java
Enumeration<URL> urls =
        pluginLoader.getResources("config/marker.txt");
~~~

- Trả `Enumeration<URL>` nhiều ứng viên, có thể gồm parent và child.
- Dùng khi kiểm chứng **trùng resource names** hoặc định dạng có nhiều files cùng tên.
- Không tự coi thứ tự enumeration là priority nghiệp vụ lâu dài nếu plugin architecture có custom overrides.
- Bảo đảm đóng streams **sau khi mở** từng URL; `getResources` chỉ trả URL enumeration, không phải mọi streams đã mở.

Kiểm đếm:

~~~java
List<URL> all = Collections.list(
        pluginLoader.getResources("config/marker.txt"));

System.out.println("resource candidates: " + all.size());
~~~

Đây là mảnh minh họa với imports `java.util.List`, `java.util.Collections`.

### 2.3. `URLClassLoader.findResource(String)`

~~~java
URL own = pluginLoader.findResource("config/marker.txt");
~~~

Ở đây giả định biến `pluginLoader` có kiểu `URLClassLoader`. API `findResource` trên `URLClassLoader` được public và tìm **resource trong URL search path của loader ấy**, không đi hỏi parent như `getResource` mặc định. Với classloader subclass khác, `findResource` thường là protected hook và không nhất thiết gọi được từ host.

**Không kết luận:** `findResource` “luôn tốt hơn” `getResource`. Chỉ dùng khi **có yêu cầu rõ ràng chọn resource của plugin**, hoặc để debug nguồn. Trong framework mà resource policy riêng đã được định nghĩa, cần tuân thủ policy đó.

### 2.4. `getResourceAsStream(String)`

~~~java
try (InputStream in =
        pluginLoader.getResourceAsStream("config/marker.txt")) {
    if (in == null) {
        throw new IllegalStateException("Missing marker");
    }
    String text = new String(in.readAllBytes(),
            StandardCharsets.UTF_8);
}
~~~

Đây là đoạn minh họa, cần imports `InputStream` và `StandardCharsets`. Stream có thể là `null` nếu không tìm được. **Đọc xong phải đóng**, kể cả khi URLClassLoader được đóng ở cuối vòng đời plugin.

Không cache/chia sẻ một `InputStream` cho mọi thread; nó có trạng thái con trỏ đọc và vòng đời `close`. Nếu muốn cache resource data dùng chung, cache snapshot bytes hoặc immutable representation rồi tạo stream riêng cho mỗi caller.

### 2.5. `Class.getResource` và tên tương đối package

Giả sử class `plugin.PluginFormatter` ở package `plugin`:

~~~java
Class<?> type =
        pluginLoader.loadClass("plugin.PluginFormatter");

// Relative với package plugin:
URL a = type.getResource("settings.txt");

// Absolute từ root:
URL b = type.getResource("/plugin/settings.txt");

// ClassLoader nhận root-relative name, KHÔNG có slash đầu:
URL c = pluginLoader.getResource("plugin/settings.txt");
~~~

Đây là mảnh minh họa; các URL chỉ khác null khi file thực sự có ở đúng path.

| Lời gọi | Resource name được tra |
|---|---|
| `PluginFormatter.class.getResource("settings.txt")` | `plugin/settings.txt` |
| `PluginFormatter.class.getResource("/plugin/settings.txt")` | `plugin/settings.txt` |
| `pluginLoader.getResource("plugin/settings.txt")` | `plugin/settings.txt` |
| `pluginLoader.getResource("settings.txt")` | `settings.txt` ở root |

**Không trộn quy ước này** khi viết test: `ClassLoader.getResource("/plugin/settings.txt")` không phải cách truyền path chuẩn như `Class.getResource("/plugin/settings.txt")`.

## 3. Resource có trên đĩa không có nghĩa nằm trong artifact runtime

Một lỗi hay gặp là code source có:

~~~text
src/main/resources/config/marker.txt
~~~

nhưng artifact chạy không chứa:

~~~text
config/marker.txt
~~~

`javac` biên dịch `.java`, **không mặc nhiên copy mọi file text** vào output class directory. Gradle/Maven có resource processing riêng; khi dùng lệnh `javac` trực tiếp cần copy resource thủ công.

Nếu phân phối plugin JAR, kiểm tra:

~~~bash
jar --list --file dist/plugin.jar
~~~

Các entry phải có tên dùng dấu `/`, ví dụ:

~~~text
plugin/PluginFormatter.class
META-INF/services/api.Formatter
config/marker.txt
~~~

**`META-INF/services` là dữ liệu resource, không phải class Java.** Nó phải nằm đúng trong JAR/thư mục mà loader dùng để khám phá providers.

Nếu `getResource` trả `null`, kiểm tra theo thứ tự:

1. Name có dùng `/` đúng không?
2. Tệp đã được copy vào runtime artifact chưa?
3. URL root/classpath root có đang trỏ đúng cấp thư mục không?
4. Loader có nhìn thấy nguồn hay chỉ parent/child/sibling loader khác nhìn thấy?
5. Named-module resource có bị package encapsulation hạn chế không?
6. Resource lookup policy có bị subclass override theo hướng khác không?

## 4. SPI và ServiceLoader: kiểm chứng thêm metadata, không chỉ bytecode

**Service Provider Interface (SPI)** là một interface/abstract class mô tả hợp đồng, ví dụ:

~~~java
package api;

public interface Formatter {
    String format();
}
~~~

Provider:

~~~java
package plugin;

public final class PluginFormatter implements api.Formatter {
    public PluginFormatter() {
    }

    @Override
    public String format() {
        return "plugin-provider";
    }
}
~~~

Với **classpath/unnamed-module provider**, tệp đăng ký cần là:

~~~text
META-INF/services/api.Formatter
~~~

và nội dung:

~~~text
plugin.PluginFormatter
~~~

Đúng hai loại “tên”:

- **Tên file descriptor:** binary name của service `api.Formatter`.
- **Nội dung mỗi dòng:** binary name đầy đủ của provider `plugin.PluginFormatter`.

Không dùng `plugin/PluginFormatter.class` làm dòng provider config. File config được mã hóa UTF-8; comment dùng `#`, dòng trống được bỏ qua; provider duplicate names được lọc theo quy tắc API.

**ServiceLoader không quét toàn bộ các class để tìm mọi `implements Formatter`.** Nó khám phá qua resource descriptor và module metadata.

### Tìm đúng descriptor

~~~java
Enumeration<URL> descriptors = pluginLoader.getResources(
        "META-INF/services/api.Formatter");
~~~

Nếu không có descriptor, hãy tìm nguyên nhân ở **resource visibility/packaging**, không lập tức sửa constructor reflection.

### Provider phải cùng runtime service type

Với hai class loaders khác nhau:

~~~text
Host interface:   (api.Formatter, AppLoader)
Plugin interface: (api.Formatter, PluginLoader)
~~~

Dù cùng tên, đó là **hai interfaces khác nhau** nếu đều tự define. Provider implement bản plugin thì `api.Formatter.class.isAssignableFrom(providerClass)` từ host có thể trả `false`.

Thiết kế đúng: **shared SPI được parent loader định nghĩa**, plugin loader delegate việc nạp API về parent, chỉ tự define implementation.

### Provider constructor trong classpath mode

Ở unnamed module/classpath, provider class cần phù hợp yêu cầu `ServiceLoader`, thường là **public class có public constructor không tham số** và assignable về SPI. Nếu descriptor chỉ tới class không phải subtype, thiếu constructor hoặc provider không thể initialize, iterator/stream của ServiceLoader có thể ném `ServiceConfigurationError`.

Kiểm tra:

~~~java
Class<?> providerClass = pluginLoader.loadClass(
        "plugin.PluginFormatter");

System.out.println(api.Formatter.class
        .isAssignableFrom(providerClass));
~~~

Đây là mảnh minh họa cần `api.Formatter` và `pluginLoader` đã tồn tại. `true` chứng minh assignability theo các runtime class identities đang dùng; nó không bảo đảm constructor/provider invocation không thất bại.

## 5. Cách ServiceLoader chọn class loader

### `ServiceLoader.load(SPI.class, loader)`

~~~java
ServiceLoader<api.Formatter> found =
        ServiceLoader.load(api.Formatter.class, pluginLoader);
~~~

Đây là **explicit loader mode**. Dùng khi đã biết plugin loader chính xác và muốn tránh phụ thuộc trạng thái thread bên ngoài.

### `ServiceLoader.load(SPI.class)`

~~~java
ServiceLoader<api.Formatter> found =
        ServiceLoader.load(api.Formatter.class);
~~~

Overload một tham số dùng **thread context class loader (TCCL)** ở thread hiện tại. Trong một plugin runtime:

- TCCL có thể là application loader, không thấy plugin descriptors.
- TCCL có thể được framework đặt thành plugin loader, thấy providers.
- TCCL có thể là loader cũ sau một lần redeploy nếu thread pool không restore context.

Đừng cho rằng TCCL luôn bằng system class loader hay defining loader của SPI.

### Đặt TCCL có scope và restore

~~~java
Thread thread = Thread.currentThread();
ClassLoader before = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    ServiceLoader<api.Formatter> providers =
            ServiceLoader.load(api.Formatter.class);
    // Duyệt providers ở đây.
} finally {
    thread.setContextClassLoader(before);
}
~~~

Đây là mảnh minh họa. Phải **restore trong `finally`** để thread pool/request tiếp theo không bị rò rỉ loader context.

### Lazy discovery và cache

Lời gọi `ServiceLoader.load(...)` thường tạo đối tượng ServiceLoader; việc khám phá và instantiate providers diễn ra khi iterate hoặc dùng stream. Do đó:

~~~java
ServiceLoader<api.Formatter> services =
        ServiceLoader.load(api.Formatter.class, pluginLoader);

// Chưa được bảo đảm mọi provider đã instantiate.
for (api.Formatter formatter : services) {
    System.out.println(formatter.format());
}
~~~

Các lỗi provider configuration có thể xuất hiện **ở lần iterate**, không nhất thiết ngay tại `load`. Khi kiểm thử, phải thật sự **duyệt iterator/stream**, không chỉ assert ServiceLoader instance != null.

`reload()` xóa cache của ServiceLoader instance; nó **không tự redefine Java classes, unload classloader hoặc rebuild JAR**. ServiceLoader bản thân **không thread-safe** nếu nhiều threads dùng chung/iterate/reload cùng instance mà thiếu synchronization.

## 6. Ví dụ Java 17+ chạy thật: parent resource khác child resource, provider chỉ ở child

Thiết kế test harness:

~~~text
                App loader
             /     |       \
        api.Formatter    host code
             |          config/marker.txt = parent-data
             |
        URLClassLoader child
              |
         plugin.PluginFormatter.class
         config/marker.txt = plugin-data
         META-INF/services/api.Formatter
              |
        ServiceLoader with explicit loader/TCCL
~~~

Kết quả sẽ chứng minh **thành công của classloading không đủ để suy ra resource/source/provider semantics**.

### 6.1. Cấu trúc source files

~~~text
project/
  src/
    api/
      api/
        Formatter.java
    plugin/
      plugin/
        PluginFormatter.java
    host/
      ResourceAndProviderCheck.java
  resources/
    host/
      config/
        marker.txt
    plugin/
      config/
        marker.txt
      META-INF/
        services/
          api.Formatter
  out/
    api/
    host/
    plugin/
~~~

### 6.2. File `src/api/api/Formatter.java`

~~~java
package api;

public interface Formatter {
    String format();
}
~~~

### 6.3. File `src/plugin/plugin/PluginFormatter.java`

~~~java
package plugin;

import api.Formatter;

public final class PluginFormatter implements Formatter {
    public PluginFormatter() {
    }

    @Override
    public String format() {
        return "plugin-provider";
    }
}
~~~

### 6.4. File `resources/host/config/marker.txt`

~~~text
parent-data
~~~

### 6.5. File `resources/plugin/config/marker.txt`

~~~text
plugin-data
~~~

Hai resources cố ý dùng **cùng relative path** `config/marker.txt` nhưng khác nội dung để test thứ tự delegation.

### 6.6. File `resources/plugin/META-INF/services/api.Formatter`

~~~text
plugin.PluginFormatter
~~~

Descriptor chỉ đặt ở plugin source, **không** đặt trong host output. Như vậy ServiceLoader dùng parent sẽ không tìm provider này, nhưng child có URL đúng sẽ tìm thấy.

### 6.7. File `src/host/ResourceAndProviderCheck.java`

~~~java
import api.Formatter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

public final class ResourceAndProviderCheck {
    private static final String DATA = "config/marker.txt";
    private static final String DESCRIPTOR =
            "META-INF/services/api.Formatter";

    static String read(URL url) throws IOException {
        if (url == null) {
            return "(missing)";
        }

        try (InputStream input = url.openStream()) {
            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8).strip();
        }
    }

    static int count(ServiceLoader<Formatter> loader) {
        int result = 0;
        for (Formatter ignored : loader) {
            result++;
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: ResourceAndProviderCheck <plugin-dir>");
        }

        ClassLoader parent = Formatter.class.getClassLoader();
        URL pluginRoot = Path.of(args[0]).toAbsolutePath()
                .toUri().toURL();

        try (URLClassLoader child = new URLClassLoader(
                "plugin", new URL[]{pluginRoot}, parent)) {

            System.out.println("parent resource: "
                    + read(parent.getResource(DATA)));

            System.out.println("child getResource: "
                    + read(child.getResource(DATA)));

            System.out.println("child findResource: "
                    + read(child.findResource(DATA)));

            List<URL> all = Collections.list(
                    child.getResources(DATA));

            System.out.println("resource candidates: "
                    + all.size());

            List<URL> descriptors = Collections.list(
                    child.getResources(DESCRIPTOR));

            System.out.println("provider descriptors: "
                    + descriptors.size());

            System.out.println("parent providers: "
                    + count(ServiceLoader.load(
                            Formatter.class, parent)));

            ServiceLoader<Formatter> services =
                    ServiceLoader.load(Formatter.class, child);

            int providerCount = 0;

            for (Formatter service : services) {
                providerCount++;

                System.out.println("provider value: "
                        + service.format());

                System.out.println("provider defined by child: "
                        + (service.getClass().getClassLoader()
                           == child));
            }

            System.out.println("explicit child providers: "
                    + providerCount);

            Class<?> apiFromChild =
                    child.loadClass("api.Formatter");

            System.out.println("shared SPI identity: "
                    + (apiFromChild == Formatter.class));

            Thread thread = Thread.currentThread();
            ClassLoader previous =
                    thread.getContextClassLoader();

            try {
                thread.setContextClassLoader(child);

                System.out.println("TCCL providers: "
                        + count(ServiceLoader.load(
                                Formatter.class)));
            } finally {
                thread.setContextClassLoader(previous);
            }

            System.out.println("TCCL restored: "
                    + (thread.getContextClassLoader()
                       == previous));
        }
    }
}
~~~

### 6.8. Compile và copy resources

Các lệnh sau chạy từ thư mục `project` bằng **Bash trên Linux/macOS** hoặc môi trường Bash tương thích:

~~~bash
mkdir -p out/api out/host/config
mkdir -p out/plugin/config out/plugin/META-INF/services

javac -d out/api src/api/api/Formatter.java

javac -cp out/api -d out/plugin \
    src/plugin/plugin/PluginFormatter.java

javac -cp out/api -d out/host \
    src/host/ResourceAndProviderCheck.java

cp resources/host/config/marker.txt \
    out/host/config/marker.txt

cp resources/plugin/config/marker.txt \
    out/plugin/config/marker.txt

cp resources/plugin/META-INF/services/api.Formatter \
    out/plugin/META-INF/services/api.Formatter

java -cp "out/api:out/host" \
    ResourceAndProviderCheck out/plugin
~~~

**Điều kiện quan trọng:**

- `out/api` và `out/host` thuộc **application classpath**.
- `out/plugin` **không** được đưa vào `-cp`; nó chỉ xuất hiện trong URL search path của child.
- Cả parent và child chứa `config/marker.txt`, nhưng chỉ child chứa descriptor và provider implementation.
- Plugin compile dựa vào `out/api` nhưng artifact plugin **không copy `api/Formatter.class`**, vì SPI phải được parent định nghĩa một lần.

Trên Windows PowerShell, dùng `New-Item -ItemType Directory -Force` để tạo thư mục và `Copy-Item` để copy resources. Thay classpath separator `:` thành `;`:

~~~powershell
java -cp "out/api;out/host" ResourceAndProviderCheck out/plugin
~~~

### 6.9. Stdout dự kiến với đúng cấu trúc trên

~~~text
parent resource: parent-data
child getResource: parent-data
child findResource: plugin-data
resource candidates: 2
provider descriptors: 1
parent providers: 0
provider value: plugin-provider
provider defined by child: true
explicit child providers: 1
shared SPI identity: true
TCCL providers: 1
TCCL restored: true
~~~

**Đọc và hiểu kết quả:**

1. **`parent resource: parent-data`:** parent loader nhìn resource tại `out/host/config/marker.txt`.
2. **`child getResource: parent-data`:** URLClassLoader mặc định dùng parent-first resource lookup; bản của parent được chọn.
3. **`child findResource: plugin-data`:** `findResource` bỏ qua parent để tìm trong URL search path của child.
4. **`resource candidates: 2`:** `getResources` cho thấy cả hai candidates; trong cấu hình test, đúng hai file cùng path.
5. **`provider descriptors: 1`:** chỉ plugin có `META-INF/services/api.Formatter`; chứng minh descriptor nằm trong search scope của child.
6. **`parent providers: 0`:** parent không thấy child plugin descriptor, nên không biết provider.
7. **`provider value: plugin-provider`:** ServiceLoader không chỉ thấy descriptor mà còn load và instantiate được provider.
8. **`provider defined by child: true`:** implementation class thuộc child; không bị parent che.
9. **`explicit child providers: 1`:** chỉ một provider đã đăng ký trong test fixture.
10. **`shared SPI identity: true`:** child delegate `api.Formatter` về parent; host và provider dùng cùng runtime interface.
11. **`TCCL providers: 1`:** overload `ServiceLoader.load(Formatter.class)` dùng TCCL tạm thời là child.
12. **`TCCL restored: true`:** cuối cùng thread được trả về context loader cũ, không giữ child plugin loader trong thread pool.

**Không hard-code kết quả này cho mọi ứng dụng:** nếu parent classpath vô tình chứa một provider cùng SPI, nếu có thêm JAR cung cấp descriptor hoặc URLClassLoader có policy tùy biến, số candidates/providers và thứ tự có thể khác. Đây là stdout dự kiến của **fixture độc lập với các file đúng như hướng dẫn**.

### 6.10. Đóng plugin thành JAR để kiểm chứng packaging

Thay child directory bằng JAR:

~~~bash
mkdir -p dist

jar --create --file dist/formatter-plugin.jar -C out/plugin .

jar --list --file dist/formatter-plugin.jar

java -cp "out/api:out/host" \
    ResourceAndProviderCheck dist/formatter-plugin.jar
~~~

JAR phải chứa ít nhất:

~~~text
plugin/PluginFormatter.class
config/marker.txt
META-INF/services/api.Formatter
~~~

Host chuyển đối số thành `URL` bằng `Path.toUri().toURL()` nên có thể dùng **directory hoặc JAR** làm URL source. Với fixture tương đương, các assertions/output giữ nguyên.

## 7. Vì sao phải kiểm riêng provider descriptor và provider class?

`ServiceLoader` hoạt động qua nhiều giai đoạn:

~~~text
ServiceLoader.load(SPI, loader)
                  |
                  v
       ServiceLoader instance
                  |
                  v
     Iterator / Stream được duyệt
                  |
                  v
 Tìm META-INF/services/<SPI binary name>
                  |
                  v
 Đọc tên provider implementation
                  |
                  v
 class loader tìm class implementation
                  |
                  v
 Kiểm tra assignability về runtime SPI
                  |
                  v
 Instantiate provider
                  |
                  v
 Return service object
~~~

Một test chỉ assert `ServiceLoader.load(...) != null` **hầu như vô nghĩa** để chứng minh provider đã tìm được. Một ServiceLoader trống vẫn là object khác `null`.

Cần các assert riêng:

- Descriptor URL tồn tại trong đúng loader scope.
- Descriptor có UTF-8 và provider binary names hợp lệ.
- Provider class load được và defining loader đúng.
- Provider là subtype của **chính SPI `Class<?>` của consumer**.
- Constructor/factory đúng mode và chạy được.
- Iterate thực sự trả provider với expected behavior.

Nếu một bước thất bại, đừng gộp mọi nguyên nhân thành “ClassLoader không hoạt động”.

## 8. Các negative tests đáng có

### 8.1. Thiếu file descriptor

Giữ `plugin/PluginFormatter.class` nhưng bỏ:

~~~text
META-INF/services/api.Formatter
~~~

Kết quả:

- `child.loadClass("plugin.PluginFormatter")` vẫn có thể thành công.
- `ServiceLoader.load(Formatter.class, child)` không khám phá provider này vì thiếu registration.
- Test `provider descriptors == 0` giúp khoanh vùng packaging.

Đây là **tình huống biến thể**, không phải output của fixture chuẩn.

### 8.2. Descriptor đặt sai tên

Sai:

~~~text
META-INF/services/Formatter
~~~

Đúng:

~~~text
META-INF/services/api.Formatter
~~~

Tên file phải là **binary name đầy đủ của SPI**. Việc package folder của provider đúng không giúp nếu descriptor name sai.

### 8.3. Descriptor chứa sai provider name

Sai:

~~~text
plugin/PluginFormatter.class
~~~

Đúng:

~~~text
plugin.PluginFormatter
~~~

Nếu ServiceLoader đọc descriptor nhưng không tìm được tên provider, có thể ném `ServiceConfigurationError` **khi iterate**, thay vì trả empty list. Cần bắt/log lỗi ở boundary của plugin chứ không nuốt.

### 8.4. Provider không assignable về SPI

Nếu plugin được nạp theo child-first policy và tự define thêm bản `api.Formatter`, provider có thể implement **một runtime interface khác**:

~~~text
(api.Formatter, ParentLoader)
(api.Formatter, ChildLoader)
~~~

Cùng tên source/binary không đủ. `Formatter.class.isAssignableFrom(providerClass)` từ host sẽ `false`; ServiceLoader có thể ném `ServiceConfigurationError` khi provider type không phù hợp.

**Fix:** shared SPI do một loader chung định nghĩa; child delegate về đó. Không chỉ copy cùng interface bytes vào mỗi plugin JAR.

### 8.5. Provider constructor lỗi

Trong unnamed-module/classpath mode, provider thường cần **public no-arg constructor** và class public phù hợp. Nếu thiếu hoặc constructor ném exception, có thể sinh `ServiceConfigurationError` khi ServiceLoader instantiate provider.

Test bắt buộc phải duyệt providers thật, không dừng ở descriptor URL. Với named modules, explicit module providers còn có provider factory method theo quy tắc riêng; **không dùng nhầm quy tắc provider factory của JPMS cho unnamed-module descriptor**.

### 8.6. Resource bị parent che

Cả parent và child có:

~~~text
config/marker.txt
~~~

`getResource` trả bản parent theo mặc định. Test phải phân biệt:

- **Resource visibility:** có tìm được resource không?
- **Resource ownership/source:** bản nào được chọn?
- **Resource content:** bytes có đúng version/môi trường không?

Không chỉ assert `getResource(...) != null`. Nếu ứng dụng phải dùng config private của plugin, kiểm tra resource URL hoặc dùng namespace/lookup API có chủ đích.

### 8.7. TCCL sai trong thread pool

Một test chỉ chạy main thread có thể pass, nhưng production sử dụng thread pool có TCCL khác. Nếu thư viện gọi `ServiceLoader.load(SPI.class)`, kết quả có thể thay đổi theo thread.

Test cần tạo task với TCCL được đặt trước, verify provider count rồi **restore trong finally**, không để context của plugin A rò sang request plugin B.

### 8.8. Duplicate descriptors và thứ tự provider

Khi nhiều JAR cung cấp cùng SPI, `getResources("META-INF/services/SPI")` có thể trả nhiều URLs. ServiceLoader có thể tổng hợp provider names và bỏ qua khai báo trùng theo quy tắc.

Đừng giả định “provider đầu tiên” luôn là provider ưu tiên trong business logic. Nếu cần chọn implementation, thu thập danh sách provider và sử dụng **policy tường minh** (ID/priority/compatible version/capabilities) thay vì phụ thuộc classpath order ngoài kiểm soát.

## 9. Tạo bộ assertion tự động cho CI

Trong test suite (JUnit hoặc một `main` tự kiểm tra như demo), nên thiết kế assertion theo tầng:

| Tầng | Assertion | Nguyên nhân nó bảo vệ |
|---|---|---|
| Artifact | JAR có class + resource + descriptor | Tránh thiếu file từ build |
| Binary name | `loadClass("plugin.PluginFormatter")` thành công | Đúng package/classpath root |
| Defining loader | `providerClass.getClassLoader() == pluginLoader` | Provider thuộc plugin |
| Shared SPI | `pluginLoader.loadClass("api.Formatter") == Formatter.class` | Không duplicate API |
| Parent resource | Nội dung `parent-data` | Parent view đúng |
| Child own resource | Nội dung `plugin-data` | Plugin file được đóng gói |
| Delegated resource | `getResource` trả expected parent content | Thứ tự mặc định đúng |
| All resources | Có đúng hai URLs trong fixture | Phát hiện collisions |
| Provider descriptor | Descriptor URL count đúng | Registration nhìn thấy được |
| ServiceLoader parent | Không có provider plugin | Parent không nhìn xuống child |
| ServiceLoader child | Có đúng một provider | Discovery + instantiate |
| TCCL | Overload một tham số thấy provider khi set child | Thread context đúng |
| Lifecycle | TCCL được restore, URL loader đóng sau dùng | Không giữ loader thừa |

### Kiểm tra robust thay vì hard-code ngoài fixture

Với test fixture có dữ liệu cố định, `== 2` hoặc `== 1` là kiểm tra tốt. Nhưng với ứng dụng thật, test nên dùng **nguồn artifact đã biết**, giới hạn root và clear runtime dependencies để không chịu ảnh hưởng của plugins khác.

Nếu có custom resource lookup policy, expected order có thể **khác URLClassLoader mặc định**; khi đó test phải mô tả chính sách mong muốn rồi kiểm tra theo contract, không áp dụng mù quáng “parent luôn thắng” cho mọi loader subclass.

## 10. Phân biệt classpath provider và named-module provider

Ví dụ đầy đủ ở trên chạy theo **classpath / unnamed-module mode**. Với JPMS named modules, provider registration có thể nằm trong `module-info.java`:

~~~java
module formatter.consumer {
    requires formatter.api;
    uses api.Formatter;
}
~~~

Provider module:

~~~java
module formatter.provider {
    requires formatter.api;
    provides api.Formatter with plugin.PluginFormatter;
}
~~~

Đây là **hai file module-info.java minh họa**, không phải tệp thay thế cho classpath demo. Module graph/layer phải được resolve phù hợp.

### Khác biệt cần kiểm chứng với JPMS

- Consumer named module cần `uses api.Formatter;` khi sử dụng ServiceLoader theo module model.
- Provider module khai báo `provides api.Formatter with plugin.PluginFormatter;`.
- Provider module phải được **resolved** trong module layer mà ServiceLoader tìm.
- `ServiceLoader.load(ModuleLayer layer, Formatter.class)` có quy tắc tìm trong layer/parent layers khác với `load(SPI, ClassLoader)`.
- Không coi `META-INF/services` và `provides` là hai cách luôn tương đương trong mọi module configuration.
- Provider trong explicit named module có thể sử dụng **public static provider() factory method** đúng điều kiện theo JDK specification; đó không phải quy tắc constructor chung của mọi classpath providers.

### Module resource encapsulation

Đối với resource trong **named module package**, `ClassLoader.getResource` có thể chỉ thấy resource khi package được mở theo yêu cầu của API (trừ một số đường dẫn đặc biệt như `.class` theo hợp đồng). Do đó:

~~~java
URL url = loader.getResource("p/secret.txt");
~~~

trả `null` **không nhất thiết** có nghĩa file `p/secret.txt` không tồn tại trong module artifact.

Khi kiểm thử modular resources, xem:

1. Module có tên và được resolved không?
2. Resource nằm trong package nào?
3. `opens`/`exports`/readability được cấu hình thế nào?
4. API đang dùng là `Module.getResourceAsStream`, `Class.getResourceAsStream` hay `ClassLoader.getResource`?
5. Lookup được thực hiện từ caller module nào và theo rule nào?

Không sử dụng `--add-opens` như một “fix chung” cho mọi resource path sai, và không trộn module access error với duplicate classloader SPI identity.

## 11. ServiceLoader, TCCL và lifecycle khi hot redeploy

Trong hệ plugin, thường có những generation liên tiếp:

~~~text
Generation 1:
  URLClassLoader-A -> provider-A
          |
          +-- ServiceLoader cache / TCCL / callbacks

Generation 2:
  URLClassLoader-B -> provider-B
          |
          +-- provider mới
~~~

Nếu host còn giữ `ServiceLoader` của generation A, provider instances hoặc TCCL trỏ tới loader A, classloader cũ có thể chưa được GC/unload ngay dù đã gọi `URLClassLoader.close()`.

**Lưu ý kỹ thuật:**

- `ServiceLoader.reload()` reset provider cache của ServiceLoader instance, **không unload/redefine Java classes**.
- `URLClassLoader.close()` đóng JAR/file handles của loader, **không buộc class unloading**.
- `getResourceAsStream` streams cần đóng riêng.
- Plugin tasks/threads, scheduled jobs, callbacks, ThreadLocals và cached Class/Method objects có thể giữ loader sống.
- Thread context class loader cần restore sau khi gọi framework/ServiceLoader trong phạm vi plugin.
- Khi đóng loader, tránh đồng thời còn thread khác đang load class; API không bảo đảm kết quả xác định cho concurrent close/load.

Một lifecycle tốt:

~~~text
ACTIVE
   |
   v
DRAINING (ngừng request mới; đợi jobs hoàn tất)
   |
   +-- close resource streams
   +-- stop tasks/threads
   +-- remove provider/listener callbacks
   +-- restore TCCL
   +-- drop ServiceLoader/provider caches
   +-- URLClassLoader.close()
   |
   v
RETIRED (drop strong references)
   |
   v
GC có thể unload khi đủ điều kiện, không cam kết ngay
~~~

Nếu plugin resources hoặc service provider data được cập nhật sang version mới, **tạo loader generation mới** có thể là cách đúng; không giả định sửa file JAR trên đĩa làm loader cũ tự nhìn thấy mọi version mới.

## 12. Chẩn đoán cụ thể khi kiểm thử thất bại

### `getResource` trả null

Hỏi:

- Resource path đúng root relative không?
- File có trong class output/JAR không?
- URL của child có đúng và accessible không?
- Package resource thuộc named module có cần `opens` không?
- Subclass có override `findResource` hoặc `getResource` sai không?

### `getResource` có URL nhưng đọc sai config

- In URL được trả về, không chỉ data content.
- So `getResource` với `findResource`.
- Dùng `getResources` để thấy parent/child candidates.
- Đặt resource private dưới namespace không xung đột hoặc chọn rõ source.
- Kiểm tra cache và version/generation.

### Descriptor tồn tại nhưng ServiceLoader trống

- Có đang dùng **đúng SPI class** không?
- Loader truyền cho ServiceLoader có nhìn thấy descriptor không?
- Descriptor có tên đầy đủ `META-INF/services/<binary-name>` không?
- Có đang chạy named modules mà phải dùng `uses`/`provides` không?
- Có chạm tới iterator/stream để kích hoạt lazy discovery chưa?

### ServiceLoader ném `ServiceConfigurationError`

- Descriptor có provider binary name hợp lệ không?
- Provider class có được loader tìm thấy không?
- Provider có subtype của **đúng runtime SPI** không?
- Constructor/provider method có đúng mode/visibility không?
- Có `NoClassDefFoundError`/`ExceptionInInitializerError`/lỗi dependency ẩn bên trong không?
- Có duplicate API class được child-first nạp riêng không?

**Không catch toàn bộ `Throwable` rồi coi lỗi như “không có provider”.** Nó che linkage, native, memory và initialization failures nghiêm trọng. Hãy giữ stack trace/cause chain và ghi artifact/loader identity.

### Lỗi chỉ xảy ra trong một thread

- Ghi `Thread.currentThread().getContextClassLoader()`.
- Ghi defining loader của SPI và provider.
- Thử `ServiceLoader.load(SPI.class, explicitPluginLoader)`.
- Kiểm tra restore TCCL trong thread pools.
- Đảm bảo không có generation cũ còn trong callbacks/caches.

## 13. Công cụ quan sát để xác nhận

**Log resource URLs:**

~~~java
Enumeration<URL> urls =
        pluginLoader.getResources("config/marker.txt");
while (urls.hasMoreElements()) {
    System.out.println(urls.nextElement());
}
~~~

Đây là mảnh minh họa; thứ tự/đường dẫn tuyệt đối tùy máy, do đó không phù hợp hard-code nguyên chuỗi URL vào test cross-platform.

**Log defining loaders:**

~~~java
Class<?> impl =
        pluginLoader.loadClass("plugin.PluginFormatter");

System.out.println(impl.getName());
System.out.println(impl.getClassLoader());
System.out.println(impl.getModule());
~~~

**Log SPI identity:**

~~~java
Class<?> viaChild = pluginLoader.loadClass("api.Formatter");

System.out.println(viaChild == api.Formatter.class);
~~~

**Kiểm tra JAR:**

~~~bash
jar --list --file dist/formatter-plugin.jar
~~~

**JVM classloading logs (HotSpot JDK 17):**

~~~bash
java -Xlog:class+load=info \
    -cp "out/api:out/host" ResourceAndProviderCheck out/plugin
~~~

Các lệnh chẩn đoán có thể in class names, sources và runtime details, nhưng **không tự thay thế kiểm chứng provider descriptor/resources**; classloading log không liệt kê đầy đủ nội dung mọi text resource hoặc nguyên nhân mỗi ServiceLoader failure.

## 14. Những ngộ nhận thường gặp

1. **“`loadClass` thành công thì `getResource` chắc chắn thành công.”** Sai: resource lookup là đường khác.
2. **“Resource có trong thư mục `src/resources` tức có trong JAR.”** Sai: phải copy/package.
3. **“`ClassLoader.getResource("x.txt")` tìm từ package của class đang gọi.”** Sai: root-relative.
4. **“`Class.getResource("x.txt")` luôn tìm từ root.”** Sai: không có slash đầu là tương đối package.
5. **“Child-first class lookup sẽ tự child-first resource lookup.”** Sai: overrides riêng.
6. **“`getResource` cho thấy toàn bộ candidate URLs.”** Sai: dùng `getResources`.
7. **“`findResource` của URLClassLoader cũng hỏi parent.”** Sai: nó tìm ở URLs của chính loader.
8. **“Chỉ cần provider class implements SPI là ServiceLoader tự quét thấy.”** Sai: phải đăng ký descriptor/JPMS provides.
9. **“`META-INF/services` đặt tên theo provider class.”** Sai: đặt theo **SPI binary name**.
10. **“Provider config liệt kê file path `plugin/Impl.class`.”** Sai: dòng chứa binary name `plugin.Impl`.
11. **“`ServiceLoader.load` đã chứng minh provider instantiate được.”** Sai: lazy lookup/instantiation khi iterate.
12. **“ServiceLoader.load(SPI.class)` luôn dùng system class loader.”** Sai: dùng TCCL.
13. **“Đặt TCCL một lần trên thread pool rồi khỏi restore.”** Sai: gây cross-plugin visibility và lifecycle leak.
14. **“Hai interface `api.Formatter` cùng source ở hai loaders là một.”** Sai: runtime identity khác.
15. **“Provider descriptor có trong JAR chắc chắn provider chạy được.”** Sai: constructor/type/linkage/dependencies có thể lỗi.
16. **“`ServiceConfigurationError` là tín hiệu có zero providers bình thường.”** Sai: thường là provider configuration/loading problem.
17. **“Đọc URL từ child mà thấy parent config là JVM đọc sai.”** Không: parent-first resource lookup mặc định.
18. **“Named-module resource rules giống hoàn toàn classpath resources.”** Sai: JPMS encapsulation có thể hạn chế.
19. **“`ServiceLoader.reload()` unload class/plugin ngay.”** Sai: chỉ reset cache.
20. **“`URLClassLoader.close()` sẽ GC loader ngay.”** Sai: còn reference/lifecycle/GC.

## 15. Checklist kiểm chứng khi hoàn thiện một plugin

- [ ] SPI được định nghĩa trong shared API thuộc loader cha phù hợp.
- [ ] Plugin artifact không tự định nghĩa lại SPI khi chính sách child-first không kiểm soát.
- [ ] Provider class được đóng gói đúng package path.
- [ ] `META-INF/services/<SPI binary name>` tồn tại đúng tên và UTF-8.
- [ ] Provider config liệt kê đúng binary implementation names.
- [ ] Provider có public no-arg constructor phù hợp classpath mode hoặc JPMS provider method đúng mode.
- [ ] Test thực sự iterate providers và assert behavior, không chỉ assert ServiceLoader != null.
- [ ] Test provider defining loader, SPI assignability và runtime type identity.
- [ ] Test parent provider visibility khác child khi kiến trúc yêu cầu.
- [ ] Test parent resource và child own resource dưới cùng path.
- [ ] Test `getResource`, `findResource` và `getResources` riêng.
- [ ] Assert đúng content/hash của resource, không chỉ URL != null.
- [ ] Đóng streams sau khi đọc.
- [ ] Test TCCL selected provider count và restore.
- [ ] Test missing descriptor, malformed provider names và constructor/linkage failures.
- [ ] Test duplicate provider registrations/candidate resource order khi có nhiều JAR.
- [ ] Test đóng gói JAR cuối, không chỉ source folder.
- [ ] Nếu dùng named modules, kiểm tra `uses`/`provides`, module resolution/layer và `opens` cho resources khi cần.
- [ ] Có log source URLs, loader identities và provider errors.
- [ ] Có lifecycle clean-up/redeploy không giữ loader cũ qua TCCL hoặc caches.
- [ ] Không dùng resource/path test không đáng tin từ file system làm thay thế runtime lookup thật.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Cả parent và child đều chứa `config/marker.txt`. Vì sao `child.getResource("config/marker.txt")` có thể trả bản parent?

**Đáp án:** Vì ClassLoader resource lookup mặc định tìm parent trước. `URLClassLoader.findResource` có thể dùng để tìm resource trong URL riêng của child.

**Câu 2.** `getResource` và `getResources` khác nhau thế nào?

**Đáp án:** `getResource` trả một URL đầu hoặc null; `getResources` trả Enumeration các URLs được nhìn thấy theo cơ chế loader.

**Câu 3.** Vì sao `type.getResource("settings.txt")` khác `loader.getResource("settings.txt")`?

**Đáp án:** Class API ghép package của `type` khi tên không bắt đầu bằng `/`; ClassLoader API tra từ root search path.

**Câu 4.** Provider class có trong JAR và implement SPI nhưng ServiceLoader không tìm. Kiểm gì trước?

**Đáp án:** `META-INF/services/<SPI binary name>` hoặc `provides` trong JPMS, tên provider đầy đủ, loader/TCCL nhìn thấy descriptor, và việc thật sự iterate ServiceLoader.

**Câu 5.** Provider do child định nghĩa và SPI do parent định nghĩa. Host có thể cast provider về SPI không?

**Đáp án:** Có nếu provider implement **chính runtime SPI type** do parent định nghĩa và method/access rules đúng. Không cần provider implementation class thuộc parent.

**Câu 6.** Tại sao một file provider descriptor đúng vẫn có thể gây `ServiceConfigurationError`?

**Đáp án:** Provider class không tìm thấy, không subtype của SPI, thiếu constructor phù hợp, lỗi initialization, dependency/linkage hoặc cú pháp config lỗi.

**Câu 7.** Cần test `ServiceLoader.load(SPI.class)` từ thread pool khác. Yếu tố gì quan trọng?

**Đáp án:** TCCL của thread; đặt/restore có scope và tránh loader cũ giữ lại sau task.

**Câu 8.** Nếu `getResource` trả null cho một named-module resource, có chắc file đã bị xóa không?

**Đáp án:** Không. Resource có thể bị JPMS encapsulation/`opens` hạn chế, hoặc path/loader không đúng.

**Câu 9.** `ServiceLoader.reload()` có thay bytecode provider đã nạp không?

**Đáp án:** Không. Nó reset provider cache của instance ServiceLoader, không redefine class hoặc unload defining loader.

**Câu 10.** Vì sao CI phải chạy với JAR được đóng gói cuối cùng thay vì chỉ test `src/main/resources`?

**Đáp án:** Resource config/descriptors có thể bị quên copy hoặc build packaging sai; runtime lookup dùng artifact cuối, không quét source folders.

## 17. Mô hình quyết định kiểm chứng

~~~text
             Plugin provider không hoạt động
                         |
             Java class có thể load?
                  /              \
                Không             Có
                 |                 |
             check URL          check resource
             root/name         + descriptor
                                   |
                     getResource/findResource/getResources
                                   |
                      đúng config source?
                             /       \
                           Không       Có
                            |          |
                      resource policy  ServiceLoader iterate
                                        |
                              provider được tìm?
                                  /        \
                                Không       Có
                                 |          |
                          descriptor/TCCL  kiểm type identity,
                          module graph     constructor/behavior
                                            |
                                      stop/redeploy:
                                      close streams,
                                      restore TCCL,
                                      clear cached refs
~~~

**Kết luận:** Kiểm chứng resource và service provider là kiểm chứng **toàn bộ đường dữ liệu**, từ artifact, loader visibility, URL source, provider descriptor đến runtime SPI type và lifecycle. Với plugin classloader, phải kiểm tra riêng **`loadClass`, `getResource`/`findResource`/`getResources` và `ServiceLoader`**; những API này không tự động có cùng chính sách tìm kiếm. Một test harness có parent/child chứa resource trùng path và descriptor chỉ ở child sẽ cho thấy ngay các lỗi ẩn mà một test nạp class đơn giản bỏ sót.

### Tài liệu chính thức

- [Java SE 17 — ClassLoader: getResource/getResources/getResourceAsStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — URLClassLoader: findResource và close](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html)
- [Java SE 17 — Class.getResource](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getResource(java.lang.String))
- [Java SE 17 — ServiceLoader: classpath providers/TCCL/JPMS](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html)
- [Java SE 17 — Module.getResourceAsStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html#getResourceAsStream(java.lang.String))
- [JLS 17 7.7 — uses/provides module declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-verification-007">Kiểm chứng nạp đồng thời và xử lý tên lớp trùng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng nạp đồng thời và xử lý tên lớp trùng trong Java ClassLoader

## 1. Mục tiêu: chứng minh hành vi, không chỉ nhìn sơ đồ

Trong hệ Java plugin, application server, test runner hoặc custom `ClassLoader`, nhiều thread có thể yêu cầu nạp class cùng lúc. Một số yêu cầu sử dụng **cùng binary name**; các yêu cầu khác sử dụng **hai binary names khác nhau**. Đồng thời, hai JAR hoặc hai plugin có thể chứa **class cùng fully qualified name**. Những tình huống này liên quan nhau nhưng **không phải cùng một vấn đề**.

Bài thực hành này tập trung vào ba câu hỏi có thể kiểm chứng bằng Java 17+:

1. **Hai thread đồng thời gọi `loadClass("demo.Alpha")` trên cùng loader sẽ nhận gì?** Với triển khai đúng, chúng nhận **cùng `Class<?>`**; loader không được định nghĩa trùng tên trong cùng defining loader.
2. **Hai thread nạp `demo.Alpha` và `demo.Beta` trên cùng loader có thể tiến vào `findClass` cùng lúc không?** Có, nếu loader được đăng ký **parallel-capable**, không bị khóa toàn cục khác chặn và dependencies cho phép.
3. **Hai loader độc lập cùng định nghĩa `demo.Alpha` thì sao?** JVM có thể có **hai runtime classes khác nhau nhưng cùng binary name**, mỗi class gắn với defining loader riêng. Không được mặc định cast object của loader này thành class do loader kia định nghĩa.

Sau khi đọc bài, bạn sẽ tự xây:
- Một chương trình quan sát **khóa theo tên** qua `getClassLoadingLock`.
- Một chương trình Java 17+ có **hai thread, CountDownLatch, ClassLoader.findClass và bytecode thực**, kiểm chứng cả nạp khác tên lẫn cùng tên.
- Một thử nghiệm gọi `defineClass` trùng tên để quan sát **`LinkageError`** và so sánh việc định nghĩa cùng tên ở hai loaders.

**Điểm cần giữ:** “concurrent class loading” không đồng nghĩa “hai thread được phép tạo hai class cùng tên trong cùng loader”; ngược lại, **đồng bộ đủ hẹp theo tên** vừa bảo vệ nạp cùng tên, vừa cho phép nạp các tên khác nhau khi phù hợp.

## 2. Các thuật ngữ phải phân biệt

| Thuật ngữ | Ý nghĩa |
|---|---|
| **Binary name** | Tên dùng khi `ClassLoader.loadClass` tìm kiểu tham chiếu thông thường, ví dụ `demo.Alpha`; nested class thường có `$` trong binary name |
| **Initiating loader** | Loader đứng ở phía khởi xướng yêu cầu load và có thể delegate cho loader khác |
| **Defining loader** | Loader thực sự gọi `defineClass` hoặc tương đương để định nghĩa runtime class |
| **Runtime class identity** | Đối với lớp thông thường, xác định bởi **binary name + defining ClassLoader** |
| **`findLoadedClass(name)`** | Kiểm tra loader/JVM đã biết runtime class cho binary name này chưa |
| **`findClass(name)`** | Hook dành cho custom loader tìm bytecode và gọi `defineClass` khi cần |
| **`defineClass(...)`** | Yêu cầu JVM biến class file bytes hợp lệ thành runtime class |
| **Parallel-capable loader** | ClassLoader subclass đã đăng ký để sử dụng khóa theo tên class trong thuật toán load mặc định |
| **`getClassLoadingLock(name)`** | Đối tượng lock dùng khi loading tên đó |
| **Class initialization** | Chạy static initializers theo quy tắc JVM; không phải cùng cơ chế với per-name class-loading lock |

### “Tên trùng” có ít nhất ba nghĩa

**A. Hai requests cùng tên, cùng một loader:**

```text
T1 → Loader-X.loadClass("demo.Alpha")
T2 → Loader-X.loadClass("demo.Alpha")
            └── kỳ vọng: cùng một Class<?> kết quả
```

**B. Hai class files cùng tên xuất hiện trong nhiều classpath/JAR entries:**

```text
lib-old.jar: demo/Alpha.class
lib-new.jar: demo/Alpha.class
            └── loader/delegation/search order quyết định class nào
```

**C. Hai defining loaders độc lập cố ý dùng cùng tên:**

```text
Loader-X.defineClass("demo.Alpha", ...)
Loader-Y.defineClass("demo.Alpha", ...)
            └── có thể tạo hai Class<?> khác nhau
```

Ba trường hợp có cách chẩn đoán khác nhau. Không gom tất cả thành “duplicate class error”.

## 3. Vì sao cần khóa cho `loadClass`?

Một loader tự định nghĩa class có thể làm những bước như:

```text
loadClass(name)
    │
    ▼
findLoadedClass(name)
    │ class đã có? ── Có ──► trả Class<?> cũ
    │ Không
    ▼
ủy quyền cho parent
    │ parent tìm được? ── Có ──► trả Class<?> của parent
    │ Không
    ▼
findClass(name)
    └── đọc class file bytes
        └── defineClass(name, bytes, ...)
                └── Class<?> mới
```

Nếu hai threads đồng thời làm **“check rồi define”** mà không có đồng bộ:
- Cả hai có thể thấy `findLoadedClass(name) == null`.
- Cả hai cùng đọc bytes.
- Cả hai gọi `defineClass` cho cùng **binary name và defining loader**.
- JVM phải từ chối lần định nghĩa trùng, thường bằng **`LinkageError`**.

Vì vậy, điều kiện **kiểm tra trạng thái chưa loaded rồi định nghĩa class** phải được điều phối như một vùng tới hạn phù hợp theo tên.

### Thuật toán load mặc định đã làm việc này

`ClassLoader.loadClass(String name, boolean resolve)` có triển khai đồng bộ dạng tương đương:

```java
// PSEUDOCODE: mô tả cấu trúc, không phải copy mã JDK nội bộ.
synchronized (getClassLoadingLock(name)) {
    Class<?> result = findLoadedClass(name);

    if (result == null) {
        try {
            result = parentOrBootstrapLoad(name);
        } catch (ClassNotFoundException notFound) {
            result = findClass(name);
        }
    }

    if (resolve) {
        resolveClass(result);
    }
    return result;
}
```

Đoạn trên chỉ minh họa trình tự và lock; `parentOrBootstrapLoad` không phải API Java thực. Cách an toàn nhất khi xây loader đơn giản là **override `findClass`**, giữ lại `loadClass` chuẩn để tận dụng:
- `findLoadedClass` trước `defineClass`;
- parent delegation;
- khóa per-name hoặc whole-loader thích hợp;
- xử lý `resolve` mặc định.

**Không nên tự override `loadClass` chỉ để đọc bytes từ directory/JAR mới**. Chỉ cần override `findClass` khi parent-first phù hợp.

## 4. Khóa whole-loader và per-name khác nhau thế nào?

Theo contract `ClassLoader.getClassLoadingLock(String)`:

- Nếu class loader **không đăng ký parallel-capable**, mặc định trả về **`this`**, tức monitor của cả loader.
- Nếu class loader **đã đăng ký parallel-capable**, trả về lock gắn với **binary name** được yêu cầu.
- Với **cùng loader và cùng tên**, các lần gọi trả lock thích hợp cho tên ấy.
- Với hai tên khác nhau, parallel-capable loader có thể dùng **hai lock khác nhau**, nhờ vậy hai `findClass` calls có khả năng chồng thời gian.

### Minh họa

```text
Loader bình thường:
    T1 loadClass("demo.Alpha") ──lock Loader-X──▶ tìm/define A
    T2 loadClass("demo.Beta")  ──chờ Loader-X─────────────▶ tìm B

Parallel-capable loader:
    T1 loadClass("demo.Alpha") ──lock K_Alpha──▶ tìm/define A
    T2 loadClass("demo.Beta")  ──lock K_Beta ──▶ tìm/define B

Cùng tên trên parallel-capable loader:
    T1 loadClass("demo.Alpha") ──lock K_Alpha──▶ define A
    T2 loadClass("demo.Alpha") ──chờ K_Alpha───▶ dùng A đã loaded
```

Đây là sơ đồ **cơ chế khóa**, không cam kết lịch scheduler sẽ cho hai thread chạy đồng thời trên mọi máy. Nếu có dependency, shared I/O lock hoặc synchronized toàn loader ở code khác thì việc nạp vẫn có thể bị tuần tự hóa.

## 5. Đăng ký parallel-capable đúng cách

Một subclass muốn dùng per-name locks theo triển khai mặc định cần đăng ký trong static initializer:

```java
public final class MyLoader extends ClassLoader {
    static {
        if (!registerAsParallelCapable()) {
            throw new ExceptionInInitializerError(
                    "ClassLoader parallel registration failed");
        }
    }

    public MyLoader(ClassLoader parent) {
        super(parent);
    }
}
```

Đây là một class Java hợp lệ, có thể lưu trong `MyLoader.java`. Nó kế thừa thuật toán `loadClass` chuẩn, chưa thêm logic tìm bytes riêng.

**Các yêu cầu quan trọng:**
- `registerAsParallelCapable()` là `protected static`; gọi trong initialization của **chính loader subclass** là cách phổ biến.
- Đăng ký phải thực hiện **trước khi tạo instance đầu tiên** của loader class đó.
- Mọi superclass thuộc chuỗi loader class (trừ `Object`) phải đăng ký theo API. Chính `ClassLoader` đã đăng ký, nhưng **subclass không tự động được tính là đã đăng ký**.
- Không bỏ qua giá trị `boolean` trả về; trả `false` nghĩa là **không đăng ký thành công**.
- Nếu định nghĩa `BaseLoader extends ClassLoader` nhưng **BaseLoader không đăng ký**, `ChildLoader extends BaseLoader` không thể đăng ký thành công theo contract.
- `isRegisteredAsParallelCapable()` trên loader instance xác nhận trạng thái (Java 9+).

**Quan trọng:** việc đăng ký là **khai báo khả năng hỗ trợ nạp đồng thời**, không làm các cache, maps, JAR readers hay code `findClass` tự thread-safe.

## 6. Ví dụ 1 — In ra sự khác biệt của khóa

### 6.1. Tạo `LockGranularityDemo.java`

```java
public final class LockGranularityDemo {
    static class Serial extends ClassLoader {
        Object lock(String name) {
            return getClassLoadingLock(name);
        }
    }

    static class Parallel extends ClassLoader {
        static {
            if (!registerAsParallelCapable()) {
                throw new ExceptionInInitializerError(
                        "registration failed");
            }
        }

        Object lock(String name) {
            return getClassLoadingLock(name);
        }
    }

    public static void main(String[] args) {
        Serial serial = new Serial();
        Parallel parallel = new Parallel();

        System.out.println("serial registered: "
                + serial.isRegisteredAsParallelCapable());
        System.out.println("serial A/B same lock: "
                + (serial.lock("demo.Alpha")
                   == serial.lock("demo.Beta")));
        System.out.println("serial lock is loader: "
                + (serial.lock("demo.Alpha") == serial));

        System.out.println("parallel registered: "
                + parallel.isRegisteredAsParallelCapable());
        System.out.println("parallel A/B same lock: "
                + (parallel.lock("demo.Alpha")
                   == parallel.lock("demo.Beta")));
        System.out.println("parallel same name same lock: "
                + (parallel.lock("demo.Alpha")
                   == parallel.lock(new String("demo.Alpha"))));
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac --release 17 LockGranularityDemo.java
java LockGranularityDemo
```

Stdout đã được kiểm chứng:

```text
serial registered: false
serial A/B same lock: true
serial lock is loader: true
parallel registered: true
parallel A/B same lock: false
parallel same name same lock: true
```

### 6.2. Giải thích

- `Serial` chưa gọi `registerAsParallelCapable`, nên lock cho Alpha và Beta cùng là đối tượng `serial`.
- `Parallel` đăng ký thành công, nên lock cho Alpha và Beta là hai objects khác nhau.
- `new String("demo.Alpha")` tạo **String object khác**, nhưng nội dung tên vẫn là `demo.Alpha`. Cùng binary name sẽ dùng lại lock tương ứng trong cùng loader.
- `==` ở đây được dùng để so **đối tượng khóa**, không phải so tên bằng reference identity.

Đây là thử nghiệm **khóa**, chưa chứng minh `findClass` thật sự chạy đồng thời. Ví dụ tiếp theo sẽ dùng bytecode hợp lệ, hai threads và latches để kiểm chứng đường tải thực tế.

## 7. Thiết kế bài kiểm chứng concurrency đúng

Để quan sát một race, nhiều ví dụ sai chỉ tạo hai threads rồi `Thread.sleep(100)` và đọc thứ tự log. Cách này không đáng tin vì:
- Scheduler có thể chạy một task xong rồi mới chạy task kia.
- Máy CI có thể chậm hơn, timeout bất thường.
- Thread sleep không bảo đảm các thao tác đã vào cùng đoạn code.
- Thứ tự của `System.out.println` không tương đương với thứ tự vào `findClass`.
- Hai classes có thể đã được parent nạp trước, khiến custom `findClass` không được gọi.

Trong bài này ta dùng:
- `CountDownLatch(2)` để chứng minh **hai binary names khác nhau đã cùng đi vào `findClass` trước khi bất kỳ thao tác nào được nhả ra**.
- `CountDownLatch(1)` để giữ tác vụ bên trong `findClass` cho tới khi thread chính cho phép tiếp tục.
- Hai `Future<Class<?>>` từ `ExecutorService.newFixedThreadPool(2)`.
- `AtomicInteger` để đếm số lần mỗi class đi qua `findClass` một cách thread-safe.
- Một loader mới cho kịch bản **cùng tên**, tránh cache từ kịch bản khác.
- Một lớp `RawLoader` độc lập cho việc **cố ý định nghĩa trùng** (không dùng `loadClass`) để quan sát JVM từ chối.

Timeout trong latch là **biện pháp chống test treo vô hạn**, không phải yếu tố xác định stdout. Khi máy bị nghẽn bất thường, test có thể fail vì timeout; thay vì in thứ tự ngẫu nhiên, nó báo sự cố kiểm chứng rõ ràng.

## 8. Ví dụ 2 — Nạp hai class khác tên và cùng tên bằng thread thật

### 8.1. Cấu trúc source và output

Tạo project:

```text
loader-race/
├── src/
│   └── demo/
│       ├── Alpha.java
│       └── Beta.java
├── LoadRaceDemo.java
├── plugin-classes/
└── host-classes/
```

**Không đặt `plugin-classes` lên host application classpath.** Nếu host parent loader đã tìm thấy Alpha hoặc Beta thì custom `findClass` không được gọi; bài kiểm chứng mất giá trị.

### 8.2. File `src/demo/Alpha.java`

```java
package demo;

public final class Alpha {
    public static String id() {
        return "Alpha";
    }
}
```

### 8.3. File `src/demo/Beta.java`

```java
package demo;

public final class Beta {
    public static String id() {
        return "Beta";
    }
}
```

Hai classes độc lập; không tham chiếu trực tiếp nhau, vì mục tiêu là kiểm tra per-name concurrency mà không vô tình tạo dependency giữa Alpha và Beta.

### 8.4. File `LoadRaceDemo.java` — chương trình đầy đủ

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class LoadRaceDemo {
    static class ParallelDirectoryLoader extends ClassLoader {
        static {
            if (!registerAsParallelCapable()) {
                throw new ExceptionInInitializerError(
                        "parallel registration failed");
            }
        }

        private final Path root;
        final AtomicInteger alphaFinds = new AtomicInteger();
        final AtomicInteger betaFinds = new AtomicInteger();
        volatile CountDownLatch entered;
        volatile CountDownLatch release;

        ParallelDirectoryLoader(Path root) {
            super(LoadRaceDemo.class.getClassLoader());
            this.root = root;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!name.equals("demo.Alpha")
                    && !name.equals("demo.Beta")) {
                throw new ClassNotFoundException(name);
            }

            if (name.equals("demo.Alpha")) {
                alphaFinds.incrementAndGet();
            } else {
                betaFinds.incrementAndGet();
            }

            CountDownLatch started = entered;
            CountDownLatch gate = release;
            if (started != null && gate != null) {
                started.countDown();
                try {
                    if (!gate.await(10, TimeUnit.SECONDS)) {
                        throw new ClassNotFoundException(
                                "gate timed out for " + name);
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new ClassNotFoundException(
                            "interrupted: " + name, error);
                }
            }

            try {
                byte[] bytes = Files.readAllBytes(
                        root.resolve(
                                name.replace('.', '/') + ".class"));
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException error) {
                throw new ClassNotFoundException(name, error);
            }
        }
    }

    static class RawLoader extends ClassLoader {
        RawLoader() {
            super(null);
        }

        Class<?> define(String name, byte[] data) {
            return defineClass(name, data, 0, data.length);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("plugin-classes");

        // KỊCH BẢN 1: hai binary names khác nhau.
        ParallelDirectoryLoader different =
                new ParallelDirectoryLoader(root);
        different.entered = new CountDownLatch(2);
        different.release = new CountDownLatch(1);

        ExecutorService two = Executors.newFixedThreadPool(2);
        try {
            Future<Class<?>> alpha = two.submit(
                    () -> different.loadClass("demo.Alpha"));
            Future<Class<?>> beta = two.submit(
                    () -> different.loadClass("demo.Beta"));

            boolean bothEntered =
                    different.entered.await(10, TimeUnit.SECONDS);

            different.release.countDown();
            Class<?> a = alpha.get();
            Class<?> b = beta.get();

            System.out.println("parallel capable: "
                    + different.isRegisteredAsParallelCapable());
            System.out.println("two names entered findClass: "
                    + bothEntered);
            System.out.println("different classes: " + (a != b));
            System.out.println("same defining loader: "
                    + (a.getClassLoader() == b.getClassLoader()));
            System.out.println("find counts: "
                    + different.alphaFinds.get() + ","
                    + different.betaFinds.get());
        } finally {
            different.release.countDown();
            two.shutdownNow();
        }

        // KỊCH BẢN 2: hai requests cùng binary name.
        ParallelDirectoryLoader same =
                new ParallelDirectoryLoader(root);
        same.entered = new CountDownLatch(1);
        same.release = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Class<?>> first = pool.submit(
                    () -> same.loadClass("demo.Alpha"));

            if (!same.entered.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "first load did not enter findClass");
            }

            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<Class<?>> second = pool.submit(() -> {
                secondStarted.countDown();
                return same.loadClass(new String("demo.Alpha"));
            });

            if (!secondStarted.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "second task did not start");
            }

            same.release.countDown();
            Class<?> c1 = first.get();
            Class<?> c2 = second.get();

            System.out.println("same name same Class: "
                    + (c1 == c2));
            System.out.println("same name findClass calls: "
                    + same.alphaFinds.get());
        } finally {
            same.release.countDown();
            pool.shutdownNow();
        }

        // KỊCH BẢN 3: cố ý define trùng trên cùng loader.
        byte[] bytecode = Files.readAllBytes(
                root.resolve("demo/Alpha.class"));

        RawLoader raw1 = new RawLoader();
        Class<?> r1 = raw1.define("demo.Alpha", bytecode);

        try {
            raw1.define("demo.Alpha", bytecode);
            System.out.println("duplicate unexpectedly allowed");
        } catch (LinkageError expected) {
            System.out.println("duplicate define rejected: "
                    + expected.getClass().getSimpleName());
        }

        // KỊCH BẢN 4: cùng binary name, hai defining loaders.
        RawLoader raw2 = new RawLoader();
        Class<?> r2 = raw2.define("demo.Alpha", bytecode);

        System.out.println("two loaders same binary name: "
                + r1.getName().equals(r2.getName()));
        System.out.println("two loaders same Class: " + (r1 == r2));
    }
}
```

**Ghi chú về thiết kế fixture:**
- `ParallelDirectoryLoader` chỉ cho phép đúng hai tên trong fixture, tránh custom loader cố vô tình nạp classes của JDK/host từ directory.
- `entered` và `release` là **latches dành cho test**, không phải cơ chế cần có trong production loader.
- `volatile` bảo đảm worker threads quan sát các tham chiếu latches mà main thread cấu hình trước khi submit tasks.
- `AtomicInteger` bảo đảm phép đếm `findClass` không bị data race khi các tên khác nhau được nạp đồng thời.
- `RawLoader` có parent `null` (bootstrap delegation); nó cố ý phơi một method `define` để kiểm chứng lỗi duplicate. **Không sử dụng RawLoader như loader tổng quát cho ứng dụng thật.**

### 8.5. Biên dịch và chạy Java 17+

Trong thư mục `loader-race`, tạo hai output directories:

Bash:

```bash
mkdir -p plugin-classes host-classes
```

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force plugin-classes,host-classes | Out-Null
```

Biên dịch và chạy:

```bash
javac --release 17 -d plugin-classes src/demo/Alpha.java src/demo/Beta.java
javac --release 17 -d host-classes LoadRaceDemo.java
java -cp host-classes LoadRaceDemo
```

`--release 17` tạo bytecode target Java 17 và giới hạn API Java theo release đó. Vì source dùng switch-free syntax cơ bản, `ExecutorService` và `Path` đều có thể chạy trên Java 17+.

**Điều kiện cực kỳ quan trọng:** chỉ `host-classes` có trong `-cp`, **không phải `plugin-classes`**. Nếu thêm `plugin-classes` vào classpath của host:
- application parent loader có thể nạp Alpha/Beta trực tiếp;
- `ParallelDirectoryLoader.findClass` không được gọi;
- `entered.await(...)` timeout vì fixture không vào đoạn code cần đo;
- đó là lỗi **thiết lập test**, không phải chứng cứ loader không hỗ trợ concurrency.

### 8.6. Stdout đã chạy kiểm chứng

```text
parallel capable: true
two names entered findClass: true
different classes: true
same defining loader: true
find counts: 1,1
same name same Class: true
same name findClass calls: 1
duplicate define rejected: LinkageError
two loaders same binary name: true
two loaders same Class: false
```

Kết quả đã được biên dịch với `javac --release 17` và chạy trên OpenJDK 21. Khi chạy trên JDK 17+, trên máy có đủ tài nguyên và không vượt timeout kiểm thử, các dòng logic có kết quả như trên. Không đo hoặc dự đoán tốc độ thực tế từ các dòng stdout này.

## 9. Đọc kết quả kịch bản 1 — hai tên khác nhau thật sự vào findClass đồng thời

Đầu tiên:

```java
different.entered = new CountDownLatch(2);
different.release = new CountDownLatch(1);
```

Mỗi lần `findClass` bắt đầu cho một tên hợp lệ:
- tăng counter;
- gọi `entered.countDown()`;
- chờ `release.await(...)` trước khi đọc bytes và gọi `defineClass`.

Nếu loader **khóa toàn bộ instance**, thread đầu tiên giữ monitor và đứng ở `findClass` chờ `release`. Thread thứ hai bị chặn **trước khi có thể đi vào `findClass`**, nên latch đếm hai arrivals sẽ không về 0 trước khi release.

Với **parallel-capable loader** có per-name locks, thread A giữ khóa Alpha và thread B giữ khóa Beta — khác nhau. Cả hai đều tiến đến `entered.countDown()` trước khi main thread nhả `release`. Vì thế:

```text
two names entered findClass: true
```

Đây là bằng chứng mạnh hơn việc chỉ nhìn thứ tự log: **hai thread đã đồng thời có mặt trong đoạn findClass** (một hoặc cả hai đang chờ release, trước khi main thread cho phép bất kỳ cái nào hoàn tất).

Các dòng còn lại:
- `different classes: true` vì Alpha và Beta khác binary name/runtime type.
- `same defining loader: true` vì cả hai do **cùng instance `different`** định nghĩa.
- `find counts: 1,1` vì mỗi binary name cần một lần `findClass` trong kịch bản mới.

### Có phải luôn hai tên khác nhau sẽ vào đồng thời?

Không. Trong production có thể có:
- Code `findClass` dùng `synchronized(this)` hoặc global monitor khác.
- Một thread đang giữ khóa của shared cache/JAR resource.
- Class A cần load B và tạo dependency-driven blocking.
- Parent loader có thể tìm lớp trước custom `findClass`.
- Scheduler không cho hai tasks chạy ngay trong cùng một thời điểm.
- Application đã load classes từ trước nên `findLoadedClass` trả kết quả trực tiếp.
- Container hoặc custom `loadClass` có chính sách khóa riêng.

Do đó nói đúng là **parallel-capable mở khả năng load các tên khác nhau song song**, không phải cam kết hai operation luôn overlap hoặc tăng tốc.

## 10. Đọc kết quả kịch bản 2 — cùng tên chỉ define một lần

Dùng một `ParallelDirectoryLoader same` **mới** để Alpha chưa có trong bộ nhớ của loader đó.

Thread thứ nhất gọi:

```java
same.loadClass("demo.Alpha")
```

Nó đi vào `findClass` rồi chờ `release`. Thread thứ hai bắt đầu:

```java
same.loadClass(new String("demo.Alpha"))
```

Chuỗi mới có identity khác, nhưng binary name giống nhau. Thuật toán `getClassLoadingLock` dựa trên **giá trị tên class**, không đòi hai `String` phải là cùng object.

Theo thuật toán mặc định:
1. Thread 1 lấy per-name lock cho Alpha.
2. `findLoadedClass` trả `null`; parent không có Alpha.
3. Thread 1 vào `findClass` và chờ latch.
4. Thread 2 cũng muốn lock **Alpha**, nên không thể cùng chạy đoạn “check then define”.
5. Sau release, thread 1 `defineClass` và trả `Class<?>`.
6. Thread 2 lấy lock, `findLoadedClass("demo.Alpha")` thấy runtime class đã loaded, trả lại class đó.
7. Không gọi `findClass` lần hai.

Vì thế stdout:

```text
same name same Class: true
same name findClass calls: 1
```

**Tại sao dùng reference equality `c1 == c2`?** Vì đây là hai references tới `Class<?>`. So `c1.getName().equals(c2.getName())` chỉ xác nhận **tên giống**, không chứng minh hai threads nhận **cùng runtime class object**.

**Giới hạn cách kiểm tra:** `secondStarted.countDown()` chạy trước `loadClass` của thread 2, nên chứng minh task thứ hai đã bắt đầu chứ không bắt buộc chứng minh nó đã đứng chờ đúng tại monitor tại thời điểm release. Tuy vậy, việc cả hai `Future` trả cùng `Class<?>` và `alphaFinds=1` xác minh tính đúng của kết quả. Có thể thêm ThreadMXBean/thread dump nếu cần quan sát chính xác trạng thái blocked, nhưng bài kiểm chứng không cần phụ thuộc vào thread scheduling details.

## 11. Đọc kết quả kịch bản 3 — cố tình defineClass trùng một loader

Khác với `loadClass` chuẩn, test cố ý gọi trực tiếp method exposed:

```java
Class<?> r1 = raw1.define("demo.Alpha", bytecode);
raw1.define("demo.Alpha", bytecode); // lần hai → LinkageError
```

`RawLoader.define` gọi `defineClass` mà **không** đi qua `findLoadedClass` hoặc per-name load lock. JVM không cho một defining loader định nghĩa hai runtime classes riêng biệt có **cùng binary name**.

Do đó lần hai bị từ chối. Trong JVM đã thử, error class đơn giản là `LinkageError`:

```text
duplicate define rejected: LinkageError
```

**Không nên phụ thuộc vào exact message** như “attempted duplicate class definition”, vì text stack trace có thể khác giữa JVM versions. Bản chất là **duplicate definition của cùng loader và binary name**.

Đừng “khắc phục” bằng cách nuốt `LinkageError` và tự trả một class tùy ý. Nếu custom loader gặp duplicate trong lúc concurrent load, điều cần sửa là:
- check `findLoadedClass` đúng thứ tự;
- lock đúng tên trong `loadClass`;
- không gọi `defineClass` trùng;
- không có race trong cache mapping tên → definition;
- không override `loadClass` làm mất synchronization mặc định.

### Khi nào cần tự gọi defineClass?

`findClass` custom loader cần `defineClass` để định nghĩa bytes từ nguồn tùy chỉnh. Nhưng thông thường **không nên cung cấp public `define(name, bytes)`** mà mọi caller đều có thể gọi không qua load policy. RawLoader ở đây cố ý phá luồng chuẩn **để biểu diễn JVM từ chối**.

## 12. Đọc kết quả kịch bản 4 — cùng tên ở hai defining loaders là hợp lệ

```java
RawLoader raw2 = new RawLoader();
Class<?> r2 = raw2.define("demo.Alpha", bytecode);
```

`raw1` và `raw2` là hai loader instances riêng. Mỗi cái định nghĩa một runtime class của `demo.Alpha` từ cùng bytes. Kết quả:

```text
two loaders same binary name: true
two loaders same Class: false
```

Điều này không mâu thuẫn với quy tắc từ chối duplicate definition:

- **Một defining loader + một binary name**: không được định nghĩa hai class versions riêng.
- **Hai defining loaders khác nhau + cùng binary name**: có thể có hai runtime classes khác nhau.
- Mỗi runtime class có thể có **static fields riêng** và initialization riêng.
- Một object do `raw1` định nghĩa không tự cast thành class `demo.Alpha` do `raw2` định nghĩa.
- Nếu hai plugins cần giao tiếp, đặt **shared interface/API** ở loader cha chung thay vì tự định nghĩa lại interface ở từng child loader.

Cần lưu ý rằng hai loader con có thể delegate và nhận cùng class do parent định nghĩa. **Hai loader objects khác nhau không tự chứng minh hai class objects khác nhau** nếu bạn chỉ gọi `loadClass` parent-first. Demo dùng `defineClass` riêng để kiểm chứng rõ defining-loader identity.

## 13. “Trùng tên class trên classpath” là bài toán khác “trùng defineClass”

Giả sử:

```text
lib-a.jar ── demo/util/Codec.class [version A]
lib-b.jar ── demo/util/Codec.class [version B]
```

Nếu cả hai nằm trong **application classpath của cùng loader**:
- Loader thường tìm class theo quy tắc tìm nguồn/đường dẫn tương ứng.
- Một binary name được định nghĩa **một lần** trong loader đó; không phải cả hai files tự biến thành hai runtime classes trong cùng loader.
- Sau khi class đã được load, việc đổi thứ tự classpath hoặc ghi đè JAR trên đĩa không tự thay runtime class đã tồn tại.
- Kết quả có thể phụ thuộc launch configuration, manifest, parent loader và custom search order.

Nếu hai JAR chứa API không tương thích:
- Compiler dùng version A.
- Runtime load version B.
- Khi code resolve một method/field chỉ có ở A, có thể nhận `NoSuchMethodError`/`NoSuchFieldError`.
- Đây là **binary compatibility/duplicate source conflict**, không phải **`defineClass` duplicate trong cùng loader**.

Để kiểm tra:
- In `Class.getClassLoader()` và `CodeSource` của **class đã thực sự được JVM định nghĩa**.
- Dùng `-Xlog:class+load=info` khi phù hợp.
- Xem runtime classpath/module path thật, không chỉ Maven/Gradle dependency tree.
- Dùng `ClassLoader.getResources("demo/util/Codec.class")` như manh mối các nguồn trùng; kết quả **không tự chứng minh nguồn nào được define**.
- Kiểm tra parent delegation và classloader graph của application server/plugin host.

**Maven/Gradle có thể chọn một version cho dependency graph**, nhưng container hoặc launcher còn có thể cung cấp một JAR khác ở runtime. Vì vậy đừng cho rằng dependency resolution thành công là bảo đảm không có binary name collision ở hệ thống chạy thật.

## 14. Khi nào “duplicate binary name” gây ClassCastException?

Giả sử service API bị đóng gói hai lần:

```text
HostLoader
  └── api.Service [Class A]

PluginLoader
  ├── api.Service [Class B, cùng binary name]
  └── impl.Plugin [implements Class B]
```

Host nhận plugin object và muốn cast thành `api.Service` của host (Class A). JVM kiểm tra runtime type identity, không chỉ `getName()`. Nếu B và A khác defining loaders, cast không hợp lệ.

**Giải pháp thường dùng:**

```text
Shared ParentLoader
   └── api.Service [một runtime Class<?> duy nhất]
          /                          \
    PluginLoader-1             PluginLoader-2
      impl.One                   impl.Two
        implements api.Service qua parent
```

- Đặt hợp đồng chung ở loader cha hoặc module chia sẻ.
- Không đóng gói trùng service API trong plugin nếu plugin loader định nghĩa lại nó.
- Nếu cần child-first cho implementation, whitelist `api.*` và platform API dùng parent-first.
- Kiểm tra `serviceType == pluginInstance.getClass().getInterfaces()[i]` chỉ khi biết đúng interface position; tốt hơn dùng `serviceType.isInstance(instance)` để xác định assignability.
- Với JPMS, xem thêm `Class.getModule()`, readability và exports/opens.

**Lỗi cast và duplicate definition có thể cùng được gọi nôm na là “trùng class”, nhưng root cause hoàn toàn khác nhau**.

## 15. Khi override loadClass: giữ nguyên vùng tới hạn

Một custom loader muốn child-first có thể override `loadClass`. Khi đó, việc chỉ đăng ký `registerAsParallelCapable()` **không tự vá** implementation không thread-safe.

Mẫu định hướng:

```java
// PSEUDOCODE TRONG SUBCLASS: chưa đủ production validation.
@Override
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
            if (isPrivatePluginNamespace(name)) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException absent) {
                    loaded = getParent().loadClass(name);
                }
            } else {
                loaded = super.loadClass(name, false);
            }
        }
        if (resolve) {
            resolveClass(loaded);
        }
        return loaded;
    }
}
```

**Không copy thành production source**: `isPrivatePluginNamespace` là placeholder, `getParent()` có thể `null`, và policy phải xử lý Java platform classes, package/module constraints, security và delegation theo kiến trúc.

Những điểm đúng cần chú ý:
- Cùng tên phải dùng cùng per-name lock.
- Phải hỏi `findLoadedClass` trước định nghĩa.
- Chỉ gọi `defineClass` một lần cho cùng binary name/loader.
- Không catch `LinkageError` của parent rồi cố định nghĩa một bản khác tùy tiện.
- Không giữ `synchronized(this)` quanh toàn bộ `loadClass` nếu muốn các tên khác nhau có thể nạp đồng thời.
- Không cho nhiều threads tự bypass `loadClass` để gọi `defineClass` trực tiếp.

Nếu có thể, **chỉ override `findClass`** và giữ thuật toán mặc định là lựa chọn ít lỗi hơn.

## 16. Dấu hiệu loader “parallel-capable nhưng nạp vẫn tuần tự”

Đăng ký thành công không bảo đảm throughput tăng. Dù `isRegisteredAsParallelCapable() == true`, có thể có:

1. Override `public synchronized Class<?> loadClass(String)` hoặc `synchronized(this)` ở code khác.
2. Một global lock bảo vệ cache, zip reader, module mapping hoặc code generation.
3. Cả hai class names được parent tìm thấy, khiến custom `findClass` không chạy.
4. Class A phụ thuộc vào B, chờ dependency dù per-name locks khác nhau.
5. JVM linkage/verification/class initialization tạo điểm chờ riêng.
6. OS/filesystem I/O hoặc CPU bị nghẽn dù logic loader không chặn.
7. Custom `getClassLoadingLock` trả một lock chung cho mọi tên.
8. Loader implementation có synchronization nhiều tầng hoặc một thư viện không thread-safe.

### Vì sao synchronized(this) làm mất lợi ích?

```java
// ANTI-PATTERN minh họa.
@Override
public synchronized Class<?> loadClass(String name)
        throws ClassNotFoundException {
    return super.loadClass(name);
}
```

Nếu dùng trong một parallel-capable loader, monitor của cả instance vẫn bị giữ cho lời gọi `loadClass` ở method này. Hai tên khác nhau bị tuần tự hóa ở outer method dù `super.loadClass` dùng per-name locks.

Cách tốt hơn là dùng khóa hẹp cho mutable resources cụ thể hoặc concurrent collections, không đặt global monitor phủ toàn bộ quá trình tìm class/I/O/delegation.

## 17. Thread safety trong findClass: đừng quên shared state

Trong ví dụ, mỗi lời gọi `findClass`:
- dùng một `String name` cục bộ;
- tính `Path` cục bộ từ `root`;
- đọc `byte[]` riêng;
- tăng counters bằng `AtomicInteger`;
- đồng bộ theo per-name lock ở `loadClass` mặc định.

Trong loader thực tế, phải kiểm soát:
- `HashMap` cache bytes/classes mutable;
- `JarFile`/`ZipInputStream` chia sẻ;
- package definition và certificates/sealing constraints;
- plugin classpath URLs đang cập nhật;
- bytecode transformer dùng mutable buffers;
- module-specific class mapping;
- log/metrics không an toàn khi nhiều threads ghi.

### Một cache concurrent đơn giản

```java
// Trích đoạn trong custom ClassLoader:
private final java.util.concurrent.ConcurrentMap<String, byte[]>
        bytecodeCache = new java.util.concurrent.ConcurrentHashMap<>();
```

Map concurrent tránh race map, nhưng **không thay thế tính đúng của `findLoadedClass` + per-name lock**. Hai threads vẫn không được gọi `defineClass` hai lần cùng tên chỉ vì cache có cùng bytes. Các API `computeIfAbsent` cũng không tự làm mọi thao tác load/class definition không deadlock; tránh side effects đệ quy hoặc khóa chéo trong mapping function.

Nếu loader có thể được dỡ/reload, đừng giữ mãi cache chứa `Class<?>`/ClassLoader/instances ở singleton host: chúng có thể giữ classloader generation cũ reachable.

## 18. Tránh deadlock khi nhiều ClassLoaders ủy quyền chéo

Một tình huống:

```text
Thread T1:
   giữ lock Loader-A khi nạp a.One
       └── gọi Loader-B nạp b.Two

Thread T2:
   giữ lock Loader-B khi nạp b.Three
       └── gọi Loader-A nạp a.Four
```

Với whole-loader locks, có thể thành:
- T1 giữ A, chờ B.
- T2 giữ B, chờ A.
- **Deadlock**.

Per-name locks giảm một nguyên nhân: nạp những **tên khác nhau** không bắt buộc tranh toàn bộ monitor của loader A/B. Nhưng:
- Vòng chờ theo **cùng per-name locks** vẫn có thể xảy ra.
- Custom locks/delegation cycles vẫn có thể deadlock.
- Static initialization có locking riêng và có thể gây vòng chờ ngay cả với parallel-capable loaders.
- Parallel-capable không thay policy parent-first/child-first.

**Kết luận chẩn đoán:** nếu thread dump cho thấy deadlock, cần tìm **vòng chờ thực tế và khóa cụ thể**, không chỉ bật registration rồi xem là xử lý triệt để.

## 19. Phân biệt class loading đồng thời với initialization đồng thời

`ClassLoader.loadClass("demo.Alpha")` chủ yếu yêu cầu class được load (và một số linking tùy JVM/option); nó **không mặc nhiên chạy `Alpha.<clinit>`**. Ngược lại:

```java
// Trích đoạn: loader đã có trong ứng dụng.
Class<?> type = Class.forName("demo.Alpha", true, loader);
```

Overload trên yêu cầu **initialize class** nếu chưa initialized; JVM quản lý đồng bộ initialization theo quy tắc riêng.

Nếu hai threads cùng lần đầu sử dụng static field/method của một runtime class, JVM không cho cả hai cùng chạy một lần static initialization thành công như hai instances độc lập. Điều này **không có nghĩa** class loader per-name lock chính là initialization lock.

Khi test:
- Đếm `findClass` để kiểm tra class loading.
- Đếm/quan sát static initialization riêng, nếu đó là nội dung bạn cần đo.
- Đừng suy từ thứ tự `System.out.println` trong static block ra điều gì về locking của `findClass`.
- `Class.forName(name, false, loader)` có thể dùng để lấy `Class<?>` mà chưa yêu cầu initialization, nhưng có thể có linking/verification theo contract.

Với trường hợp circular static initializers, còn có thể thấy giá trị static fields chưa hoàn tất hoặc deadlock do multi-thread code; **không đánh đồng với `ClassCircularityError`** (vòng superclass/superinterface hierarchy).

## 20. Nạp mảng, primitive, nested class: tên cần chính xác

Trong Java, `ClassLoader.loadClass` nhận **binary name** cho class/interface thông thường. Dùng:

```text
demo.Alpha
demo.Outer$Nested
java.util.ArrayList
```

Đừng truyền:
- source-like `demo/Alpha.class` vào `loadClass` như binary name;
- `demo.Alpha.class` (có suffix `.class`) làm binary name;
- `demo.Outer.Nested` nếu runtime binary name thật là `demo.Outer$Nested`;
- tên kiểu nguyên thủy `int` rồi nghĩ rằng loader sẽ tìm `int.class`.

Với `Class.forName`:
- `Class.forName("int")` **không trả `int.class`** theo overload thông thường.
- Tên descriptor như `[Ljava.lang.String;` dùng cho class mảng trong overload phù hợp và có quy tắc loader/component riêng.
- Mảng không được custom loader định nghĩa bằng cách đọc một `[L...;.class` theo đường file như class thường.
- Đối với named modules, `Class.forName(Module, String)` có semantics khác overload dùng loader và có thể trả `null` khi không tìm thấy trong module; không trộn lẫn các API khi viết test.

Những khác biệt đó giải thích một số “duplicate/not found” logs mà nguyên nhân thực sự là **sai dạng tên**, không phải concurrency.

## 21. Dùng ClassLoader nào để test? Parent và TCCL

Một framework có thể có:
- loader định nghĩa chính framework;
- loader cha cung cấp shared API;
- loader con định nghĩa plugin implementation;
- **Thread Context ClassLoader (TCCL)** dùng cho SPI/discovery;
- loader riêng cho JPMS ModuleLayer.

`Thread.currentThread().getContextClassLoader()` **không bắt buộc trùng với defining loader** của một class đang chạy.

Đặc biệt:
- `Class.forName(String)` dùng loader của caller, không tự dùng TCCL.
- `ServiceLoader.load(S.class)` thường dùng TCCL để tìm providers.
- `ClassLoader.loadClass(name)` dùng receiver loader và policy delegation.
- `type.getClassLoader()` cho biết defining loader thật của runtime class.
- `type.getModule()` cho biết runtime module trong JPMS.

Khi thử duplicate names, log cả **loader receiver** và **defining loader**. Hai requests đi vào hai receiver loaders không đủ chứng minh classes khác nhau nếu chúng cùng delegate cho parent.

## 22. Kiểm chứng thực tế trong app server hoặc plugin host

Trong production có thể không tự viết `findClass`, vì Tomcat, OSGi-style runtimes, build/test runners hoặc framework loaders đã quản lý class search. Quy trình chẩn đoán hợp lý:

1. **Xác định binary name** thật của class nghi vấn.
2. **Ghi process/thread IDs** và thời gian các yêu cầu load xảy ra.
3. **Ghi loader identity**: `ClassLoader` object, class của loader, defining loader của `Class<?>` kết quả.
4. Kiểm tra trạng thái `isRegisteredAsParallelCapable()` nếu API khả dụng.
5. Xem code loader override `loadClass` hay `findClass`, có global synchronization không.
6. Kiểm tra parent delegation và classpath/module-path/resources.
7. Kiểm tra nếu cùng name xuất hiện trong nhiều JARs: **artifact nào thật sự cung cấp bytes**.
8. Nếu gặp `LinkageError`, tìm điểm gọi `defineClass` và đường check/lock trước đó.
9. Nếu gặp `ClassCastException` dù names giống nhau, xem **defining loaders** và shared API placement.
10. Nếu nghi deadlock, lấy thread dumps ở thời điểm treo và phân tích vòng monitor/lock.
11. Nếu nghi leak/redeploy, xem loader generations còn reachable, TCCL và registries.
12. Tái hiện bằng test hai tên/cùng tên trong loader mới thay vì thay đổi source JAR của class đã load sẵn.

### Probe chẩn đoán tối thiểu

```java
public final class ClassOriginProbe {
    public static void print(Class<?> type) {
        System.out.println("name: " + type.getName());
        System.out.println("loader: " + type.getClassLoader());
        System.out.println("module: " + type.getModule());

        var domain = type.getProtectionDomain();
        var source = domain == null ? null : domain.getCodeSource();
        System.out.println("source: "
                + (source == null ? "<unavailable>"
                                  : source.getLocation()));
    }

    public static void main(String[] args) {
        print(ClassOriginProbe.class);
    }
}
```

Chạy:

```bash
javac --release 17 ClassOriginProbe.java
java ClassOriginProbe
```

Không đưa stdout cố định cho loader identity/CodeSource, vì tùy JDK, đường dẫn và môi trường. **CodeSource có thể `null` hoặc là URL trừu tượng**; với dynamic code, class bytes đã transform không nhất thiết trùng artifact disk hiện tại.

## 23. Instrumentation và JVM logs khi race khó tái hiện

### Java 17+ class loading log

Trên HotSpot:

```bash
java -Xlog:class+load=info -cp host-classes LoadRaceDemo
```

Lệnh dựa vào output của bài thực hành mục 8. Log có thể cho thấy `demo.Alpha`/`demo.Beta` được nạp từ byte array hoặc nguồn tùy loader. Thông tin chính xác của log phụ thuộc JDK; không coi đường log là cố định.

### Thread dump

```bash
jcmd <pid> Thread.print -l
```

`<pid>` là placeholder, thay bằng process ID thật. Trong dump, tìm threads đang:
- chờ `ClassLoader.loadClass` hoặc custom synchronized block;
- thực thi `findClass`/`defineClass`;
- giữ monitor của loader hoặc lock object theo tên;
- chờ JAR/cache/filesystem I/O;
- ở static initialization hoặc callback loader khác.

Cần chú ý rằng JVM có các đồng bộ nội bộ không nhất thiết được một công cụ deadlock detection báo đầy đủ. Đối chiếu nhiều thread dumps theo thời gian và logic code thay vì suy chỉ một snapshot.

### Heap/classloader stats

Nếu nhiều runs/redeploy làm classloader count tăng bất thường:

```bash
jcmd <pid> VM.classloader_stats
```

Đây là tiện ích phụ trợ, không phải phép thử correctness của nạp concurrent. Một class đã loaded trên nhiều independent plugin loaders có thể hoàn toàn hợp lệ; câu hỏi lifecycle là những loader cũ có còn cần thiết sau unload không.

## 24. Chẩn đoán lỗi theo hiện tượng

| Hiện tượng | Nguyên nhân khả dĩ | Cách kiểm tra |
|---|---|---|
| Hai threads cùng name, `findClass` gọi hai lần | Override `loadClass` thiếu synchronization hoặc không kiểm tra loaded | Per-name lock, `findLoadedClass`, trace |
| `LinkageError: duplicate class definition` | `defineClass` lại cùng binary name trong cùng loader | Find/check/define order, race window |
| Hai tên khác nhau vẫn bị tuần tự hóa | Non-parallel loader, global monitor/cache lock | `isRegisteredAsParallelCapable` và thread dump |
| Latch không về 0 ở demo | Parent đã load class, sai `-cp`, registration lỗi, timeout/scheduler | Parent classpath, target directory, console |
| Hai loaders nạp cùng tên nhưng `Class<?>` vẫn bằng nhau | Cả hai delegate cho một parent defining loader | `getClassLoader` của kết quả |
| `ClassCastException` dù tên class giống nhau | Hai defining loaders độc lập | So `Class<?>` identity, shared API |
| `NoSuchMethodError` khi đổi classpath/JAR | Compile/runtime binary API mismatch | CodeSource, class loading log, dependency versions |
| `NoClassDefFoundError` ở class dependency | Dependency thiếu hoặc initialization failure khác | Lỗi gốc, loader hierarchy |
| `ClassCircularityError` | Vòng superclass/superinterface hierarchy, không phải chỉ hai threads | Kiểm tra class file/type graph |
| Deadlock khi loaders ủy quyền chéo | Whole-loader locks hoặc chu trình theo tên/global locks | Thread dumps và lock graph |
| Lỗi chỉ trên test runner | Test JVM/loader khác IDE/main app; TCCL khác | TestRuntimeClasspath, process/loader |
| Một class cũ vẫn được dùng sau thay JAR | Loader đã định nghĩa class và cache | Loader generation mới, process restart |

Không sửa tất cả các triệu chứng trên bằng cách “đăng ký parallel-capable”. Chỉ một số thuộc locking/concurrency; những lỗi khác thuộc identity, ABI, module access hoặc deployment.

## 25. Làm test concurrency đáng tin cậy

Một bài kiểm chứng tốt có **điều kiện quan sát cụ thể**:

### 25.1. Dùng fresh loader

Mỗi kịch bản cần loader mới hoặc tên class mới để tránh `findLoadedClass` trả cache hit trước khi test. Một lần đã load Alpha bằng một loader, gọi lại nó không còn test việc tìm bytes hay define class.

### 25.2. Điều khiển điểm vào, không điều khiển lịch scheduler bằng sleep

`CountDownLatch`/`CyclicBarrier` tạo điểm đồng bộ có thể chờ, nhưng cần:
- timeout có ý nghĩa và báo failure rõ;
- `finally` luôn release gates;
- không chờ hai arrivals trong một whole-loader monitor khi không có thread nào bên ngoài có thể release;
- không cố đo tốc độ từ một lần chạy.

### 25.3. Test cả khác tên và cùng tên

**Khác tên:** hai tasks phải có thể vào `findClass` khi tên không phụ thuộc nhau và policy không có global lock.

**Cùng tên:** kết quả hai futures phải là **cùng `Class<?>`** và custom loader chỉ **define một lần**.

Không chỉ test khác tên: một loader có thể cho tất cả tên chạy song song **nhưng sai** vì không bảo vệ duplicate define. Không chỉ test cùng tên: loader serial toàn bộ cũng làm đúng việc chống trùng nhưng mất concurrency.

### 25.4. Đừng “chứng minh” bằng log có thứ tự ngẫu nhiên

Một số dòng log có thể interleave bất kỳ. Hãy đưa ra:
- boolean assertions;
- counts;
- identity comparisons;
- latches/queues ghi nhận trạng thái;
- classloader IDs;
- errors theo loại, không phụ thuộc exact message.

Demo mục 8 làm đúng hướng này: stdout quyết định bởi invariants khi test hoàn tất, không dựa vào thread nào in trước.

### 25.5. Tránh biến test thành stress vô hạn

Nếu muốn stress:
- tạo nhiều loaders độc lập hoặc nhiều names khác nhau;
- giới hạn thread/heap/class count;
- chạy nhiều iterations;
- thu failure logs, không nuốt exceptions;
- cleanup executors và loaders/resource handles;
- lưu nhận diện JDK/OS/CI config.

Không viết test mở hàng ngàn JAR/threads không giới hạn chỉ để “kiểm chứng race”; nó có thể tự tạo OutOfMemoryError/file descriptor exhaustion.

## 26. Gợi ý assertion cho automated test

Nếu dùng JUnit, có thể kiểm tra tương tự:

```java
// TRÍCH ĐOẠN JUnit — yêu cầu project có JUnit Jupiter dependency.
assertSame(classFromT1, classFromT2);
assertEquals(1, loader.alphaFinds.get());
assertSame(loader, classFromT1.getClassLoader());
```

Đây là trích đoạn, không phải file Java SE độc lập. Thứ tự assertions cần phối hợp sau khi các `Future` hoàn tất, không đọc counters trong lúc workers chưa xong.

Đối với **khác tên**:
- Chứng minh `CountDownLatch(2)` đã đếm về 0 trong khi gate chưa release.
- Kiểm tra `Class<?>` được định nghĩa bởi đúng loader.
- Kiểm tra counter cho mỗi tên bằng 1.

Đối với **same name**:
- `assertSame(c1, c2)`;
- `alphaFinds == 1`;
- không gặp duplicate `LinkageError` trong luồng loading chuẩn.

Đối với **direct duplicate define**:
- `assertThrows(LinkageError.class, () -> raw.define(...))` sau lần define đầu tiên thành công.

Các ví dụ `assertSame`/`assertThrows` là API JUnit 5 khi đã import và cấu hình đúng; **các file Java chạy độc lập** trong mục 6 và 8 không phụ thuộc JUnit.

## 27. Những nhầm lẫn thường gặp

| Nhầm lẫn | Sự thật |
|---|---|
| “Parallel-capable nghĩa là hai thread có thể định nghĩa cùng tên hai lần” | Không; cùng tên vẫn phải dùng chung lock/loaded class |
| “Chỉ `registerAsParallelCapable()` là mọi loader code đã thread-safe” | Không; shared caches/resources và overrides cần đúng |
| “Subclass kế thừa trạng thái registered của ClassLoader tự động” | Không; subclass cần đăng ký riêng và superclass chain hợp lệ |
| “`getClassLoadingLock` luôn trả chính loader” | Chỉ mặc định khi non-parallel-capable |
| “Hai String objects khác nhau nghĩa là hai class names khác nhau” | Không; xét nội dung binary name |
| “Hai ClassLoaders có tên class giống thì Class<?> chắc bằng nhau” | Không; còn defining loader và delegation |
| “Hai loader objects khác nhau thì Class<?> chắc khác” | Không; có thể cùng delegate parent |
| “Trùng class ở hai JAR luôn gây `LinkageError` duplicate” | Không; loader thường chỉ chọn một definition cho cùng tên |
| “Gọi `defineClass` trực tiếp giống gọi `loadClass`” | Không; bỏ check/delegation/lock |
| “`loadClass` luôn chạy static initializer” | Không; initialization có quy tắc riêng |
| “Khác binary names là chắc có tốc độ gấp đôi” | Không; scheduler, dependencies, I/O và locks khác |
| “Loader parallel-capable không thể deadlock” | Không; cycles và custom locks vẫn có thể |
| “`findLoadedClass` là một cache ứng dụng có thể xóa tùy ý” | Không; gắn với runtime loader state |
| “Đổi file .class trên disk là JVM tự nạp lại definition cũ” | Không; thường phải dùng loader/process mới |
| “Catch mọi LinkageError rồi child-first fallback sẽ sửa class collision” | Dễ che lỗi thật và phá type identity |
| “Dùng Thread.sleep là đủ chứng minh cùng lúc” | Không; dùng synchronization primitives và kiểm tra invariants |
| “Log từ một thread đủ biết class source thực tế” | Cần defining loader/CodeSource/log và parent hierarchy |

## 28. Câu hỏi tự kiểm tra

1. **Hai threads cùng gọi `loadClass` cho cùng tên qua cùng loader chuẩn nhận mấy runtime classes?** Một class object được chia sẻ sau khi load thành công.
2. **Mục đích `findLoadedClass` là gì?** Trả class đã loaded, tránh định nghĩa lại và giữ identity.
3. **Không parallel-capable, `getClassLoadingLock` mặc định trả gì?** Chính loader instance (`this`).
4. **Parallel-capable, hai tên khác nhau có thể dùng locks khác nhau không?** Có.
5. **Hai `new String("demo.Alpha")` khác references nhưng cùng content có nên dùng cùng class loading lock không?** Có, cùng binary name.
6. **Vì sao cần `registerAsParallelCapable` trong static initializer?** Đăng ký loader class trước khi tạo instance, với superclass chain hợp lệ.
7. **Chỉ override `findClass` có lợi gì?** Giữ thuật toán `loadClass`, loaded check, delegation và locking mặc định.
8. **`AtomicInteger` trong demo dùng làm gì?** Đếm findClass calls an toàn khi các threads vào đồng thời.
9. **CountDownLatch(2) ở khác tên chứng minh gì?** Hai lời gọi `findClass` đã đến điểm gate trước release.
10. **Kết quả `same name findClass calls: 1` chứng minh gì?** Hai requests cùng name không làm loader định nghĩa/tìm lại class hai lần trong kịch bản test.
11. **Direct `defineClass` hai lần cùng binary name và loader gây gì?** `LinkageError`.
12. **Hai loaders độc lập define cùng binary name có hợp lệ không?** Có thể, nhưng tạo runtime classes khác nhau.
13. **Hai class cùng binary name nhưng defining loader khác nhau cast được không?** Không mặc nhiên.
14. **Hai loader con cùng delegate parent có nhận cùng Class<?> không?** Có thể.
15. **Một parent đã load Alpha, child `findClass` có luôn được gọi không?** Không trong parent-first delegation mặc định.
16. **Tại sao không đưa plugin-classes vào host classpath của demo?** Tránh parent định nghĩa Alpha/Beta trước custom loader.
17. **`ClassLoader.loadClass` khác `Class.forName(name,true,loader)` thế nào về initialization?** loadClass không tự bắt buộc init; forName overload với `true` yêu cầu init.
18. **Nạp class cùng lúc nghĩa là code static initialization chạy hai lần không?** Không.
19. **Để kiểm tra duplicates runtime, chỉ xem Maven dependency tree có đủ không?** Không; phải xem loader và class source thực tế.
20. **Nếu worker test timeout, nên suy loader sai ngay không?** Không; kiểm tra classpath, parent, scheduler, latches và resource starvation.

## 29. Tài liệu chuẩn

- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findLoadedClass`, `findClass`, `defineClass`, `registerAsParallelCapable`, `getClassLoadingLock`.
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): runtime `Class<?>`, `getClassLoader()`, `forName()`, type identity.
- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): đồng bộ điểm bắt đầu và đợi tín hiệu không dùng `Thread.sleep`.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): submit tasks và `Future` kết quả.
- [Java SE 17 — LinkageError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/LinkageError.html): lỗi linking/definition không tương thích.
- [Java Virtual Machine Specification 17 — 5 Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): class creation, defining/initiating loader, constraints và runtime identity.
- [Oracle — Multithreaded Custom Class Loaders](https://docs.oracle.com/javase/8/docs/technotes/guides/lang/cl-mt.html): nguyên tắc parallel-capable và custom loader đa luồng.

**Kết luận:** Một ClassLoader đúng phải thực hiện hai điều **cùng lúc**: **đảm bảo duy nhất một definition cho mỗi binary name trên một defining loader**, và khi đã đăng ký parallel-capable thì **không khóa rộng vô ích khiến các tên khác nhau không thể nạp đồng thời**. Kiểm chứng bằng **fresh loaders, CountDownLatch, `Class<?>` identity, `findClass` counters và lỗi duplicate `defineClass`**. Với các JARs hoặc plugins trùng tên, luôn phân biệt **trùng bytes nguồn**, **trùng định nghĩa trong một loader** và **hai runtime classes cùng tên ở hai loaders** trước khi quyết định cách xử lý.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-verification-008">Kiểm chứng vòng đời đóng loader, dừng plugin và giải phóng tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng vòng đời đóng loader, dừng plugin và giải phóng tham chiếu

## 1. Mục tiêu: chứng minh teardown đúng, không chỉ gọi close()

Trong ứng dụng Java có plugin hoặc hot redeploy, vòng đời hợp lý thường là:

~~~text
Tạo ClassLoader cho plugin
           |
           v
Load class / tạo plugin instance
           |
           v
Đăng ký listener, task, scheduler, threads, resources
           |
           v
Plugin hoạt động
           |
           v
STOPPING: chặn tác vụ và callback mới
           |
           v
Gỡ registrations, dừng workers, đóng resources
           |
           v
Đóng URLClassLoader nếu plugin sở hữu nó
           |
           v
Bỏ strong references của host/plugin
           |
           v
Loader có thể đủ điều kiện được GC thu hồi
           |
           v
JVM có thể unload classes vào một thời điểm sau đó
~~~

Ba sự kiện dưới đây **khác nhau** và phải kiểm tra riêng:

1. **Plugin đã dừng đúng chức năng**: không nhận thêm callback/tác vụ, executor không còn chạy, tài nguyên do plugin sở hữu đã được đóng.
2. **Loader đã được đóng**: ví dụ **URLClassLoader.close()** giải phóng JAR/file handles do loader mở và không dùng loader đó để tìm *class/resource mới* của chính nó.
3. **Không còn tham chiếu mạnh giữ loader**: host registry, threads, thread context class loader (TCCL), ThreadLocal, instances, Class objects, Futures và caches không còn giữ phiên bản cũ.

**Đóng loader không đồng nghĩa JVM đã unload class.** Việc một loader có thể được GC thu hồi là điều kiện cần cho unloading của các class thông thường do loader ấy định nghĩa; quá trình GC/unload có thể xảy ra muộn hoặc không diễn ra trong thời gian quan sát. Bài này tập trung vào **kiểm chứng có bằng chứng**, không khẳng định “gọi System.gc() là PASS”.

### Kết quả học tập

Sau bài, bạn sẽ biết:

- Định nghĩa đúng trạng thái **STARTING/RUNNING/STOPPING/STOPPED/CLOSED/RELEASED**.
- Xây phép thử cho **URLClassLoader.close()** và hiểu điều gì vẫn hoạt động sau close.
- Tạo plugin nhỏ có **listener đăng ký ở host** và **ExecutorService riêng**, sau đó teardown deterministically.
- Chứng minh shared thread pool có thể giữ loader cũ qua **TCCL**; phục hồi đúng bằng **try/finally**.
- Dùng **WeakReference/ReferenceQueue** như diagnostic probe mà không tạo bài test GC dễ flake.
- Tìm **Path to GC Roots**, threads, queued tasks và metrics class loading/unloading.
- Phân biệt **điều kiện đủ để báo STOPPED** với **quan sát GC**.
- Thiết kế negative tests và kiểm tra lặp lại qua nhiều lần reload.

## 2. URLClassLoader.close() thực sự làm gì?

Theo Javadoc Java SE 17 của **java.net.URLClassLoader**:

- **close()** đóng loader để nó không thể tiếp tục tìm/nạp **những class hoặc resources mới do chính loader này định nghĩa**.
- Class/resources đã được loader tìm/nạp trước đó **vẫn có thể truy cập**.
- Class/resources được định nghĩa bởi **parent loaders** vẫn có thể được sử dụng qua delegation.
- Với URL dạng JAR/file, nó cố gắng đóng các opened files của loader.
- Nếu một thread đang load class khi thread khác gọi close, **kết quả load đồng thời đó không được xác định**.
- Nếu close nhiều lần, lời gọi sau trên loader đã đóng không có tác động thêm.
- close() có thể ném **IOException**, vì việc đóng các file handles có thể thất bại.

Vì vậy, không thể dùng:

~~~java
loader.close();
~~~

để thay thế tất cả thao tác **stop plugin**, **cancel tasks**, **unregister callbacks**, **clear ThreadLocal**, **release references** hay **class unloading**. Đó chỉ là một bước trong chu kỳ đóng.

### 2.1 Bốn tính chất cần tách riêng

| Việc kiểm chứng | Phương pháp | Kết luận phù hợp |
| --- | --- | --- |
| Class đã nạp còn dùng được sau close? | Giữ instance/Class trước khi close rồi gọi method sau close. | Close không invalidate instance/Class hiện có. |
| Class mới chỉ có trong JAR còn nạp được sau close? | Cố load một class chưa nạp trước close, bắt ClassNotFoundException. | Loader đã ngừng tìm class mới của chính nó. |
| Parent classes còn sẵn không? | loader.loadClass("java.lang.String"). | Parent/bootstrap delegation vẫn dùng được. |
| File/JAR handles đã đóng chưa? | Ghi nhận close() thành công và kiểm chứng thao tác file phù hợp môi trường. | Resource handles do URLClassLoader sở hữu được xử lý; không suy ra GC/unload. |

**Đừng dùng việc class đã load vẫn chạy sau close làm bằng chứng close thất bại.** Đó là behavior đã được API quy định.

### 2.2 Ai sở hữu URLClassLoader?

- **Host/container** thường tạo và sở hữu loader cho một deployment.
- **Plugin** sở hữu các resources/task/listeners mà nó tự tạo.
- Host chỉ gọi **loader.close()** sau khi plugin đã stop hợp tác và công việc đang chạy đã được drain/cancel theo chính sách.
- Không close một loader đang được dùng chung bởi nhiều plugin vẫn hoạt động.
- Không close **system/application loader** như thể nó là plugin loader do bạn sở hữu.
- Một custom ClassLoader khác URLClassLoader có thể **không có close()** và không phải AutoCloseable.

## 3. Một state machine teardown có thể kiểm thử

Một plugin manager nên xác định trạng thái rõ:

~~~text
NEW
 |
 v
STARTING -> RUNNING
             |
             v
          STOPPING
             |
             +-- chặn submit/callback mới
             +-- unregister listener và scheduler
             +-- chờ/cancel running tasks
             +-- close sockets, streams, files, sessions
             +-- dọn TCCL và ThreadLocal trên owner threads
             |
             v
           STOPPED
             |
             +-- URLClassLoader.close()
             |
             v
           CLOSED
             |
             +-- xóa references từ registry/cache/futures
             +-- bỏ các references host còn giữ
             |
             v
       RELEASED / GC-ELIGIBLE (chỉ là đánh giá reachability)
             |
             v
       Observed unload/GC (không bảo đảm thời điểm)
~~~

**STOPPED không bằng RELEASED.** Bạn có thể đã dừng mọi task nhưng còn một static map trong host giữ instance plugin. Ngược lại, bỏ reference trong registry khi worker vẫn đang chạy code plugin không có nghĩa teardown an toàn.

**GC-ELIGIBLE không phải một trạng thái JVM cam kết báo tức thì.** Đây là một trạng thái thiết kế/phân tích: bạn đã cắt mọi đường strong reference được quản lý trong hệ thống và không còn công việc plugin đang chạy. Heap analyzer/ReferenceQueue có thể bổ sung bằng chứng, nhưng không có API phổ thông bảo đảm trả lời ngay rằng tất cả class được unload.

## 4. Thiết kế tiêu chí PASS/FAIL theo mức độ

| Mức | Câu hỏi | Cách PASS đáng tin |
| --- | --- | --- |
| **Functional stop** | Plugin ngừng xử lý request/event mới? | Chặn intake, unregister callbacks; kiểm tra zero active registrations. |
| **Thread shutdown** | Executor/worker của plugin kết thúc? | shutdown hoặc shutdownNow phù hợp; awaitTermination thành công, thread đã dừng. |
| **Context cleanup** | Host worker có mang plugin TCCL sau task? | Kiểm tra context trở về previous ClassLoader trên worker. |
| **Resource cleanup** | Streams/sockets/handles đã đóng? | close hoặc try-with-resources; kiểm tra trạng thái/phép thử I/O thích hợp. |
| **Loader close** | URLClassLoader được đóng? | close thành công; không dùng lại để nạp class mới do nó định nghĩa. |
| **Reference cleanup** | Host còn callback, queue, cache, Future hay Class<?> giữ loader? | Kiểm tra owner registries rỗng; heap Path to GC Roots không còn retention path dự kiến. |
| **GC observation** | Weak ref bị clear, class unload event/metric xuất hiện? | Bằng chứng bổ sung có điều kiện; không bắt buộc để unit test teardown deterministically PASS. |

**Tránh một boolean "unloaded=true" tự báo do plugin đã stop.** Chỉ có thể nói rõ phần nào đã được xác nhận và phần nào còn phụ thuộc GC.

## 5. Ví dụ Java 17+ số 1 — close() không xóa Class đã nạp

Ví dụ đơn giản này tách hành vi **resource close** khỏi **Class identity và lifetime**.

### 5.1 Cấu trúc thư mục

~~~text
project/
├── plugin-src/lab/plugin/
│   ├── Ready.java
│   └── Later.java
└── CloseDemo.java
~~~

**plugin-src/lab/plugin/Ready.java**:

~~~java
package lab.plugin;

public class Ready {
    public String text() {
        return "ready";
    }
}
~~~

**plugin-src/lab/plugin/Later.java**:

~~~java
package lab.plugin;

public class Later {
    public String text() {
        return "later";
    }
}
~~~

**CloseDemo.java**:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class CloseDemo {
    public static void main(String[] args) throws Exception {
        URL jar = Path.of("plugin.jar").toUri().toURL();

        URLClassLoader loader = new URLClassLoader(
                new URL[]{jar},
                ClassLoader.getPlatformClassLoader());

        Class<?> ready = loader.loadClass("lab.plugin.Ready");
        Object object = ready.getConstructor().newInstance();

        System.out.println("before close = "
                + ready.getMethod("text").invoke(object));

        loader.close();

        System.out.println("after close = "
                + ready.getMethod("text").invoke(object));

        System.out.println("parent available = "
                + (loader.loadClass("java.lang.String")
                        == String.class));

        try {
            loader.loadClass("lab.plugin.Later");
            System.out.println("unexpected load");
        } catch (ClassNotFoundException expected) {
            System.out.println("new plugin class rejected = true");
        }
    }
}
~~~

Đứng tại thư mục **project**, dùng JDK 17+:

~~~bash
javac --release 17 -d plugin-out plugin-src/lab/plugin/Ready.java plugin-src/lab/plugin/Later.java
jar --create --file plugin.jar -C plugin-out .
javac --release 17 CloseDemo.java
java CloseDemo
~~~

Trên Windows PowerShell, các lệnh trên cũng chạy được vì classpath trong ví dụ chỉ chứa một root; **không cần** thay dấu phân tách classpath.

**Stdout đã biên dịch/chạy thực tế trên OpenJDK 21:**

~~~text
before close = ready
after close = ready
parent available = true
new plugin class rejected = true
~~~

### 5.2 Vì sao output đúng?

1. **Ready** được định nghĩa từ plugin.jar và một instance được tạo **trước close**.
2. Loader đóng JAR/file handles qua close.
3. Method **Ready.text()** đã có sẵn vẫn chạy được. **close không invalidate object hoặc Class object**.
4. **java.lang.String** được định nghĩa bởi bootstrap loader, vẫn có thể tìm qua delegation.
5. **Later** chưa được nạp trước close và chỉ tồn tại trong plugin.jar. Do URLClassLoader đã close, lookup của class mới thất bại bằng **ClassNotFoundException**.

**Bẫy kiểm thử:** Nếu gọi **loader.loadClass("lab.plugin.Later")** trước close, nó đã cached/initiated và có thể vẫn truy cập sau close. Phải chọn **một class chắc chắn chưa nạp** để kiểm tra đúng điều kiện.

**Giới hạn:** Demo giữ mạnh biến **loader**, **ready** và **object** trong main khi in output. Nó **không** kiểm chứng class unloading. Nó chỉ chứng minh close semantics và khả năng dùng classes/resources đã load.

## 6. Ví dụ Java 17+ số 2 — dừng plugin, gỡ listener và kết thúc executor

Ví dụ này dùng **host API contract** chung, plugin implementation được nạp bằng URLClassLoader riêng. Plugin sở hữu:

- Một **Runnable listener** đăng ký trong host registry; nó giữ tham chiếu đến plugin instance qua method reference.
- Một **single-thread ExecutorService** do plugin tạo, worker mang **plugin ClassLoader làm TCCL**.
- Một giao thức **start → stop** đóng các đường giữ ở host và chờ worker termination.

### 6.1 Cấu trúc dự án

~~~text
lifecycle/
├── host-src/
│   ├── host/api/Plugin.java
│   ├── host/api/Registry.java
│   └── HostLifecycleDemo.java
└── plugin-src/
    └── plug/PluginImpl.java
~~~

Thư mục output:

~~~text
host-out/          class files của host và shared API
plugin-out/        class files implementation của plugin
plugin-impl.jar    JAR plugin, không chứa bản sao host.api.*
~~~

**Quan trọng:** Interface **host.api.Plugin** phải do host loader định nghĩa. Plugin implementation dùng **cùng Class object** khi cast. Đừng gói bản sao host.api.Plugin riêng vào plugin-impl.jar rồi child-first redefine interface chung.

### 6.2 Shared interface

**host-src/host/api/Plugin.java**:

~~~java
package host.api;

public interface Plugin {
    void start(Registry registry) throws Exception;
    boolean stop(Registry registry) throws Exception;
}
~~~

Method **stop** trả true khi plugin-owned worker đã kết thúc trong thời hạn. Ví dụ đơn giản hóa lifecycle; ứng dụng thật thường có status object chứa chi tiết lỗi, timeout và cleanup status cho từng resource.

### 6.3 Host registry

**host-src/host/api/Registry.java**:

~~~java
package host.api;

import java.util.HashSet;
import java.util.Set;

public final class Registry {
    private final Set<Runnable> listeners = new HashSet<>();

    public void add(Runnable listener) {
        listeners.add(listener);
    }

    public void remove(Runnable listener) {
        listeners.remove(listener);
    }

    public int count() {
        return listeners.size();
    }
}
~~~

Registry là ví dụ đồng bộ đơn giản do tất cả lời gọi add/remove/count trong fixture đến từ một thread host. Trong ứng dụng nhiều threads, cần thêm synchronization hoặc concurrent data structure và giao thức ngăn callback mới sau STOPPING.

**Registry có thể giữ plugin lâu hơn dự tính:** Vì listener là object do plugin tạo và có thể capture plugin instance, host registry nếu giữ listener sẽ trở thành **đường tham chiếu mạnh** tới plugin và defining loader.

### 6.4 Plugin implementation

**plugin-src/plug/PluginImpl.java**:

~~~java
package plug;

import host.api.Plugin;
import host.api.Registry;
import java.util.concurrent.*;

public final class PluginImpl implements Plugin {
    private ExecutorService pool;
    private Runnable callback;

    private void onEvent() {
        // Một callback của plugin.
    }

    @Override
    public void start(Registry registry) throws Exception {
        callback = this::onEvent;
        registry.add(callback);

        pool = Executors.newSingleThreadExecutor(task -> {
            Thread worker = new Thread(task, "plugin-worker");
            worker.setContextClassLoader(
                    getClass().getClassLoader());
            return worker;
        });

        // Future.get(): bảo đảm worker đã được tạo/chạy
        // trước khi host tiến hành kiểm thử stop.
        pool.submit(() -> {}).get();
    }

    @Override
    public boolean stop(Registry registry) throws Exception {
        registry.remove(callback);
        callback = null;

        pool.shutdownNow();
        boolean terminated = pool.awaitTermination(
                5, TimeUnit.SECONDS);

        pool = null;
        return terminated;
    }
}
~~~

### 6.5 Launcher của host

**host-src/HostLifecycleDemo.java**:

~~~java
import host.api.Plugin;
import host.api.Registry;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class HostLifecycleDemo {
    static WeakReference<ClassLoader> cycle(
            Registry registry) throws Exception {

        URL url = Path.of("plugin-impl.jar").toUri().toURL();

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{url},
                HostLifecycleDemo.class.getClassLoader())) {

            Class<?> pluginClass =
                    loader.loadClass("plug.PluginImpl");

            Plugin plugin = (Plugin) pluginClass
                    .getConstructor().newInstance();

            plugin.start(registry);

            System.out.println("listeners while active = "
                    + registry.count());

            System.out.println("worker terminated = "
                    + plugin.stop(registry));

            System.out.println("listeners after stop = "
                    + registry.count());

            // WeakReference không giữ loader sống.
            return new WeakReference<>(loader);
        } // URLClassLoader.close() qua try-with-resources.
    }

    public static void main(String[] args) throws Exception {
        Registry registry = new Registry();

        WeakReference<ClassLoader> probe = cycle(registry);

        System.out.println("cleanup checks passed = "
                + (registry.count() == 0));

        // Chỉ xác nhận ta có probe, KHÔNG khẳng định GC đã chạy.
        System.out.println("probe installed = "
                + (probe != null));
    }
}
~~~

### 6.6 Biên dịch/chạy

Đứng tại thư mục **lifecycle**:

~~~bash
javac --release 17 -d host-out host-src/host/api/Plugin.java host-src/host/api/Registry.java host-src/HostLifecycleDemo.java
javac --release 17 -cp host-out -d plugin-out plugin-src/plug/PluginImpl.java
jar --create --file plugin-impl.jar -C plugin-out .
java -cp host-out HostLifecycleDemo
~~~

**Stdout đã chạy thực tế trên OpenJDK 21:**

~~~text
listeners while active = 1
worker terminated = true
listeners after stop = 0
cleanup checks passed = true
probe installed = true
~~~

### 6.7 Vì sao ví dụ này kiểm chứng được teardown?

**Bước RUNNING:**

- Plugin object được tạo từ Class do URLClassLoader mới định nghĩa.
- Host nhận một Runnable callback là method reference **this::onEvent**, nên registry giữ đường tham chiếu tới plugin instance.
- Plugin-owned executor tạo thread, TCCL của worker trỏ tới plugin loader.
- **submit(...).get()** bảo đảm worker đã nhận task; nếu chỉ gọi newSingleThreadExecutor mà không submit, worker có thể chưa được tạo.

**Bước STOPPING/STOPPED:**

- **registry.remove(callback)** cắt host listener reference.
- **callback = null** dọn reference nội bộ của plugin.
- **shutdownNow()** phát tín hiệu dừng bằng interrupt và cancel queued tasks phù hợp.
- **awaitTermination(5s)** xác nhận worker đã kết thúc trong timeout. Với fixture no-op task, trả true.
- **pool = null** bỏ reference plugin giữ tới executor sau khi dừng.
- **try-with-resources** close loader sau khi stop return.

**Bước RELEASE CANDIDATE:**

- Method **cycle** trả **WeakReference** thay vì trả instance/Class/loader mạnh.
- Sau khi cycle return, biến local **plugin**, **pluginClass**, **loader** của frame đó không còn được dùng bởi host launcher.
- Host registry vẫn tồn tại, nhưng **count() = 0**, nên không còn callback do ví dụ quản lý.

Đây là **bằng chứng đã dọn những đường giữ mà fixture tạo ra**. Không tự suy rằng toàn JVM hết mọi tham chiếu mạnh hoặc loader được unload ngay. WeakReference có thể vẫn trả non-null một thời gian vì GC chưa diễn ra hoặc runtime còn references khác.

### 6.8 Điều kiện production phải bổ sung

Fixture ưu tiên ngắn gọn và stdout deterministically kiểm chứng được. Một hệ thực cần:

- Nếu **stop() ném exception giữa chừng**, cleanup registry, executor và resource vẫn phải chạy trong finally/đường bù.
- Nếu **awaitTermination trả false**, không được xóa reference rồi báo worker đã chết. Giữ trạng thái **STOP_TIMEOUT/BLOCKED**, ghi thread dump và xử lý theo policy.
- **InterruptedException** khi host đợi shutdown phải phục hồi interrupt flag hoặc tuân theo hợp đồng hủy.
- **shutdownNow** không ép thread lập tức dừng; task phải hợp tác với interrupt.
- Phải dọn **queued tasks/Futures/listeners** ở cả host và plugin, không chỉ những biến có trong fixture.
- Plugin có thể sở hữu thêm **ScheduledExecutorService, ThreadLocal, socket, file handle, JDBC connection, MBean, driver, timers, JMX/logging hooks**.
- Nếu **close()** ném IOException, không được coi resource close PASS mặc nhiên; JDK có thể gắn nhiều failures trong suppressed exceptions.
- **Chặn registration mới trước khi drain**, nếu không plugin có thể thêm listener mới ngay sau khi host vừa xóa registry.
- Không gọi close loader trong khi threads đang nạp class. URLClassLoader Javadoc nói behavior của load đồng thời với close là không xác định.

## 7. Kiểm chứng TCCL: shared host worker có thể giữ plugin cũ

Trong môi trường server dài hạn, **Thread Context ClassLoader** của một thread có thể là đường giữ mạnh:

~~~text
GC root -> live Host worker Thread
                 |
                 +-- contextClassLoader
                           |
                           v
                      OldPluginLoader
                           |
                           +-- class metadata/objects có liên quan
~~~

Một task plugin có thể đặt TCCL là loader của plugin để dùng ServiceLoader, resource loading hoặc framework APIs. Nếu task return nhưng không **restore previous context**, host pool giữ worker sống lâu và tiếp tục giữ loader cũ.

### 7.1 Bad pattern

~~~java
Thread.currentThread()
        .setContextClassLoader(pluginLoader);
invokePlugin();
// Nếu task throw hoặc return sớm -> không restore.
~~~

Đây là snippet với pluginLoader/invokePlugin có sẵn. Nó **không phải chương trình độc lập** và là anti-pattern cố ý.

Một task sau có thể chạy với **TCCL của phiên bản plugin cũ**, gây cả **memory retention** và **lookup sai class/provider/resource**.

### 7.2 Correct pattern

~~~java
Thread current = Thread.currentThread();
ClassLoader previous = current.getContextClassLoader();

current.setContextClassLoader(pluginLoader);
try {
    invokePlugin();
} finally {
    current.setContextClassLoader(previous);
}
~~~

**Luôn restore chính previous**, không đoán system loader là đúng và không mặc định set null. Previous có thể là host loader, framework loader hoặc null.

Nếu API nhận loader rõ ràng (ví dụ một số ServiceLoader overloads), có thể **truyền loader tường minh** để tránh thay shared thread state khi phù hợp.

## 8. Ví dụ Java 17+ số 3 — host pool leak TCCL và khôi phục bằng finally

Tạo file **TcclPoolDemo.java** cùng thư mục có plugin.jar từ ví dụ 1:

~~~java
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.concurrent.*;

public class TcclPoolDemo {
    public static void main(String[] args) throws Exception {
        ClassLoader host =
                TcclPoolDemo.class.getClassLoader();

        URLClassLoader plugin = new URLClassLoader(
                new java.net.URL[]{
                        Path.of("plugin.jar").toUri().toURL()
                },
                host);

        ExecutorService pool =
                Executors.newSingleThreadExecutor(job -> {
            Thread t = new Thread(job, "host-worker");
            t.setContextClassLoader(host);
            return t;
        });

        try {
            // Cố ý quên restore trong một task.
            pool.submit(() -> Thread.currentThread()
                    .setContextClassLoader(plugin)).get();

            System.out.println("after unsafe task = "
                    + pool.submit(() -> Thread.currentThread()
                        .getContextClassLoader() == plugin).get());

            // Dọn context cũ trên CHÍNH host worker.
            pool.submit(() -> Thread.currentThread()
                    .setContextClassLoader(host)).get();

            System.out.println("restored baseline = "
                    + pool.submit(() -> Thread.currentThread()
                        .getContextClassLoader() == host).get());

            try {
                pool.submit(() -> {
                    Thread thread = Thread.currentThread();
                    ClassLoader prior =
                            thread.getContextClassLoader();

                    thread.setContextClassLoader(plugin);
                    try {
                        throw new IllegalStateException(
                                "task failed");
                    } finally {
                        thread.setContextClassLoader(prior);
                    }
                }).get();
            } catch (ExecutionException expected) {
                System.out.println("error = "
                        + expected.getCause()
                                .getClass().getSimpleName());
            }

            System.out.println("after failed task = "
                    + pool.submit(() -> Thread.currentThread()
                        .getContextClassLoader() == host).get());
        } finally {
            pool.shutdown();

            if (!pool.awaitTermination(
                    5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                pool.awaitTermination(5, TimeUnit.SECONDS);
            }

            plugin.close();
        }

        System.out.println("pool terminated = "
                + pool.isTerminated());
    }
}
~~~

Biên dịch và chạy (trong thư mục của plugin.jar ví dụ 1):

~~~bash
javac --release 17 TcclPoolDemo.java
java TcclPoolDemo
~~~

**Stdout đã chạy thực tế trên OpenJDK 21:**

~~~text
after unsafe task = true
restored baseline = true
error = IllegalStateException
after failed task = true
pool terminated = true
~~~

### 8.1 Giải thích kết quả

- **after unsafe task = true:** Single-thread executor tái sử dụng worker; task đầu đã đặt TCCL tới plugin loader nhưng không restore. Task tiếp theo thấy plugin context vẫn còn.
- **restored baseline = true:** Một task cleanup chạy **trên cùng worker** phục hồi host loader. Không đổi TCCL của worker bận từ một thread khác một cách tùy tiện.
- **error = IllegalStateException:** Task thử nghiệm cố ý ném lỗi. Khi Future.get() phát hiện lỗi, nó bọc nguyên nhân trong ExecutionException.
- **after failed task = true:** **finally** vẫn chạy để restore TCCL khi task thất bại.
- **pool terminated = true:** Host shutdown và await trong fixture thành công.

**Điểm quan trọng:** Ví dụ không dùng GC làm điều kiện PASS. Nó kiểm tra **trực tiếp một retention path**, nên kết quả deterministic dưới đúng môi trường chạy.

### 8.2 TCCL kế thừa khi tạo thread mới

Theo Thread API, một thread mới thường **kế thừa contextClassLoader của thread tạo ra nó**, trừ khi cấu hình khác. Nếu tạo plugin worker trong khi thread tạo đang mang plugin TCCL, thread con có thể giữ plugin loader **kể cả sau khi parent đã restore**.

Vì vậy:

- Host **ThreadFactory** cho shared pools nên đặt baseline TCCL phù hợp, thay vì vô tình kế thừa plugin context.
- Plugin-owned thread có thể dùng plugin TCCL trong suốt vòng đời của plugin nhưng **phải dừng và giải phóng** khi plugin stop.
- Đừng dựa vào thread name hay daemon flag để kết luận loader không bị giữ. Một live daemon thread vẫn có thể giữ references trong JVM đang chạy.
- Executor có thể tạo worker **lúc task đầu được submit**, không bắt buộc lúc constructor executor được gọi.

## 9. ThreadLocal và các reference paths khác

**TCCL chỉ là một đường giữ mạnh**. Trong production, có thể còn:

~~~text
Live Thread
    +-- contextClassLoader -> OldPluginLoader
    +-- ThreadLocalMap value -> PluginObject
    +-- Runnable/Future đang thực thi -> PluginObject

Host static Registry
    +-- callback/listener -> PluginObject
    +-- service cache -> Plugin Class
    +-- plugin metadata -> URLClassLoader

Scheduler / Executor queue
    +-- Delayed task -> Plugin Runnable
    +-- Future -> Callable / result / class from plugin
~~~

### 9.1 Dọn ThreadLocal trên owner thread

~~~java
ThreadLocal<Object> local = new ThreadLocal<>();

try {
    local.set(pluginScopedValue);
    executePluginTask();
} finally {
    local.remove();
}
~~~

Đây là snippet minh họa với pluginScopedValue và executePluginTask có sẵn.

**Restore TCCL không gọi ThreadLocal.remove().** Ngược lại, remove ThreadLocal không xóa TCCL. Phải cleanup cả hai nếu scope dùng cả hai.

ThreadLocalMap có weak keys, nhưng values có thể vẫn bị giữ trong thread lâu sống khi entry chưa được làm sạch. Việc không còn reference tới ThreadLocal key **không phải** bằng chứng ngay lập tức value đã được thu hồi.

### 9.2 Các nguồn giữ hay bị quên

- **ScheduledFuture** của periodic task, kể cả sau khi plugin “không làm gì nữa”.
- Host-managed **ExecutorService** còn queued tasks do plugin submit.
- **CompletableFuture** chains hoặc callbacks capture plugin state.
- Global **Map/Set/caches** chứa Class, Method, MethodHandle hoặc object do plugin class tạo.
- Logging handlers, metrics listeners, event bus registrations, MBeans/JMX, JDBC drivers, ShutdownHooks.
- Thread-created **TCCL inheritance** và custom ThreadFactory.
- ThreadLocal values trên shared workers.
- Class<?> và instance **được lưu vào static field thuộc host**.
- Resources mở ngoài URLClassLoader như FileInputStream, ZipFile, Socket, native libraries.

Các loại giữ này **không được đóng tự động chỉ vì URLClassLoader.close() trả về**.

## 10. WeakReference/ReferenceQueue: dùng đúng để quan sát, không biến GC thành assertion cứng

Một công cụ chẩn đoán là:

~~~java
WeakReference<ClassLoader> weak =
        new WeakReference<>(pluginLoader);
~~~

WeakReference không tự giữ đối tượng được tham chiếu sống, khác với một reference mạnh. Nhưng **chỉ tạo weak reference khi vẫn giữ biến pluginLoader mạnh chưa chứng minh gì**.

### 10.1 Điều kiện quan trọng nhất

Bạn phải đưa quá trình **tạo loader + load classes + instantiate plugin + stop + close** vào một method riêng và chỉ trả về **weak reference** sau khi frame đó kết thúc; đồng thời host phải unregister mọi references do nó quản lý.

Mô hình ở ví dụ 2:

~~~text
static WeakReference<ClassLoader> cycle(...) {
    URLClassLoader loader = new URLClassLoader(...);
    try {
        ... start ...
        ... stop/drain/unregister ...
        return new WeakReference<>(loader);
    } finally {
        loader.close();
    }
}

WeakReference<ClassLoader> probe = cycle(...);
~~~

Đây là pseudocode minh họa cấu trúc; chương trình runnable đầy đủ ở phần 6 dùng try-with-resources.

**Mục đích:** giảm khả năng local variables trong caller vô tình giữ loader trong khi bạn kiểm tra reachability.

### 10.2 ReferenceQueue cho tín hiệu một chiều

Nếu muốn nhận thông báo khi GC xử lý reference:

~~~java
ReferenceQueue<ClassLoader> queue =
        new ReferenceQueue<>();

WeakReference<ClassLoader> probe =
        new WeakReference<>(loader, queue);
~~~

Snippet trên cần imports **java.lang.ref.ReferenceQueue/WeakReference** và biến loader có sẵn.

- Khi **probe được clear/enqueue** theo cơ chế GC, đó là bằng chứng hữu ích rằng referent không còn được weak reference giữ và GC đã xử lý nó.
- **queue.poll() == null** chưa chứng minh loader bị leak; GC có thể chưa xử lý hoặc chưa enqueue.
- **probe.get() != null** chưa chứng minh có một đường tham chiếu mạnh bất thường; có thể JVM chưa thu hồi đối tượng.
- Ngay cả khi loader được thu hồi, việc quan sát **class unloading** còn là sự kiện JVM riêng, không được đồng bộ tức thì với user code.
- ReferenceQueue không mang metadata chính xác về **GC root** đã từng giữ loader; nếu thấy retention, dùng heap analyzer.

### 10.3 Vì sao không viết unit test “System.gc rồi phải null”?

~~~java
System.gc();
assert weak.get() == null;
~~~

Đây là **phản ví dụ**: phép kiểm tra này không đáng tin.

**System.gc() là request/hint**, không có cam kết theo thời gian rằng loader sẽ bị thu hồi trong thời điểm đó. GC có thể không thực thi theo yêu cầu, có thể trì hoãn class unloading, hoặc code test/debugger/JIT vẫn giữ reference tạm. Do đó test dễ **flaky** giữa JDK/JVM/CI environments.

Nếu cần stress/probe để quan sát, có thể thử nhiều vòng với timeout hữu hạn và in:

~~~text
OBSERVED: weak reference cleared
~~~

hoặc:

~~~text
INCONCLUSIVE: not collected within observation window
~~~

Đây là **hai kết quả thay thế**, không phải stdout cố định. **INCONCLUSIVE không đồng nghĩa FAILED** trừ khi test có giả định/điều kiện GC được kiểm soát và định nghĩa rõ.

### 10.4 Negative control giúp kiểm tra probe đúng hơn

Trong lab test riêng:

1. Giữ một **strong reference cố ý** từ host registry tới plugin/loader.
2. Xác nhận bằng kiểm tra registry rằng reference ấy còn tồn tại.
3. Thực hiện cleanup và bỏ reference.
4. Dùng WeakReference/ReferenceQueue hoặc heap snapshot để quan sát.
5. Nếu chỉ dùng GC signal mà không đo đường root, không tuyên bố chắc chắn negative control là bằng chứng leak thực tế.

**Điểm quyết định là chẩn đoán đường giữ cụ thể**, không phải đếm số lần gọi System.gc.

## 11. Kiểm thử repeated reload và nhận diện memory leak thực

Một loader leak thường rõ nhất sau nhiều lần reload:

~~~text
Deploy #1 -> Loader1 -> RUNNING -> STOP
Deploy #2 -> Loader2 -> RUNNING -> STOP
Deploy #3 -> Loader3 -> RUNNING -> STOP

Nếu host registry/thread còn giữ:
GC roots -> Loader1, Loader2, Loader3 ...

Nếu cleanup đúng:
references owned by host về loaders cũ được bỏ
-> loaders có thể được GC thu hồi về sau
~~~

### 11.1 Một kế hoạch đo hợp lý

1. **Warm-up baseline:** đo classloader counts/metaspace sau khi host startup ổn định.
2. **Deploy:** ghi module/plugin version, loader identity, loaded class count, số callbacks/tasks.
3. **Run:** tạo workload đại diện, bao gồm tasks async, resources, ServiceLoader và callbacks.
4. **Stop:** ngừng intake; hủy pending tasks; chờ running tasks kết thúc theo timeout.
5. **Close/release:** unregister; close resources; close loader; clear host refs.
6. **Observe:** đo sau nhiều chu kỳ bằng công cụ JVM; dùng heap dump nếu loaders cũ tích tụ.
7. **Investigate GC roots:** tìm **Reference path từ GC root tới old loader**, không chỉ nhìn biểu đồ heap.
8. **Regression test:** bổ sung asserts cho chính listener registry, executor termination, TCCL và closure policies.

Đừng kết luận memory leak chỉ vì metaspace cao ngay sau deploy: HotSpot có thể giữ/commit memory và unload vào thời điểm phù hợp. Nhưng **số lượng old plugin loaders vẫn strongly reachable tăng đều** cùng cụ thể các paths qua threads/caches là tín hiệu đáng điều tra.

### 11.2 Các chỉ số có ý nghĩa và giới hạn

| Quan sát | Cho biết | Không cho biết |
| --- | --- | --- |
| **ClassLoadingMXBean.getLoadedClassCount()** | Số class hiện loaded ở JVM theo metric | Không chỉ ra loader cụ thể giữ class nào. |
| **ClassLoadingMXBean.getUnloadedClassCount()** | Tổng class đã unloaded tích lũy | Không bảo đảm loader vừa stop đã được unload. |
| **JFR class load/unload events** | Sự kiện JVM khi cấu hình thu thập phù hợp | Không nhất thiết xác định GC root gây retention. |
| **jcmd VM.classloader_stats** | Classloader/class metadata statistics, tùy JVM hỗ trợ | Không tự chỉ ra callback/ThreadLocal nào đang giữ loader. |
| **Heap dump + MAT** | Path to GC Roots, dominator tree, retained objects | Snapshot phụ thuộc thời điểm và GC trước đó. |
| **Thread dump** | Worker threads, stack, blocking state | Không đủ chứng minh mọi Java object retention path. |
| **OS file handles** | Có còn JAR/file handle mở không | Không phải bằng chứng class unloaded. |

**Nên có nhiều góc nhìn** vì close, GC eligibility, class unloading và OS resource handles là những lớp trạng thái khác nhau.

## 12. Chẩn đoán bằng jcmd, JFR và heap dump

Trên HotSpot JDK phù hợp, một số lệnh thường dùng:

~~~bash
jcmd
jcmd <PID> help
jcmd <PID> VM.classloader_stats
jcmd <PID> GC.class_histogram
jcmd <PID> GC.heap_dump /path/to/heap.hprof
~~~

**<PID>** ở trên là placeholder process ID, không phải ký tự gõ nguyên văn. Tên command có thể tùy JDK/JVM, do đó hãy **jcmd <PID> help** trước khi áp dụng.

**Cảnh báo vận hành:** Heap dump có thể rất lớn, gây pause/I/O và chứa dữ liệu nhạy cảm (tokens, PII, business objects). Chỉ lấy khi được phép và có chính sách lưu giữ/xóa phù hợp.

### 12.1 GC root path thường gặp

Nếu live worker giữ TCCL:

~~~text
GC Root: Java Thread
   |
   +-- Thread.contextClassLoader
         |
         +-- URLClassLoader (old plugin)
~~~

Nếu host registry giữ callback:

~~~text
GC Root: host static field
   |
   +-- ListenerRegistry
         |
         +-- Runnable callback
             |
             +-- PluginImpl instance
                  |
                  +-- Class PluginImpl / defining loader
~~~

Nếu executor queue giữ task:

~~~text
GC Root: Host ExecutorService
   |
   +-- workQueue
         |
         +-- FutureTask / Runnable
              |
              +-- captured plugin object
                    |
                    +-- defining loader
~~~

Có thể tồn tại **nhiều đường root cùng lúc**. Sửa TCCL mà quên listener registry vẫn giữ loader; sửa listener mà còn task queue cũng chưa đủ.

### 12.2 Quy trình MAT/JFR

- Heap dump: tìm instance của URLClassLoader/custom PluginLoader, đặc biệt loader phiên bản cũ.
- Xem **Incoming references** và **Path to GC Roots**; loại trừ weak/soft refs trong phân tích để tập trung strong paths phù hợp.
- Xem retained size và số loader versions sau nhiều deploy cycles.
- So sánh trước/sau cleanup ở workloads tương đương.
- Dùng JFR/class unloading events nếu muốn biết JVM có thực sự unload classes, nhưng đừng biến absence of event trong cửa sổ ngắn thành lỗi teardown.
- Dùng thread dumps để xác định plugin-owned threads còn sống/blocked và xử lý shutdown hang.

### 12.3 Không nhầm close với file-delete portability

**URLClassLoader.close()** giúp đóng file/JAR handles do loader đã mở, hữu ích khi cần thay/deploy JAR, đặc biệt trên hệ điều hành khóa file. Nhưng một process còn **file stream/JarFile khác** tự mở ngoài loader vẫn có thể giữ handle; đóng loader không đóng mọi resource bên ngoài nó.

Ngoài ra, OS/file-system behavior khác nhau; khả năng thay file thành công không chứng minh JVM đã unload classes từ phiên bản trước.

## 13. Lifecycle tối thiểu nên có trong production

Một lifecycle manager có thể biểu đạt dưới dạng pseudo-code:

~~~java
// Pseudocode cấu trúc, không phải class chạy độc lập.
stopAcceptingNewWork(pluginId);

try {
    unregisterHostCallbacks(pluginId);
    cancelOrDrainQueuedTasks(pluginId);
    stopPluginOwnedExecutors(pluginId);
    awaitWorkersStopped(pluginId, timeout);

    closePluginOwnedStreamsSocketsAndResources(pluginId);
    verifyThreadLocalsAndTcclCleanup(pluginId);

    // Chỉ khi không còn active class/resource loading.
    pluginLoader.close();

    clearHostReferences(pluginId);
} finally {
    recordShutdownOutcome(pluginId);
}
~~~

**Không coi đoạn này là đủ cho mọi trường hợp:** nó trình bày thứ tự ý tưởng. Một triển khai thật phải có đường xử lý lỗi cho từng bước, timeout, idempotence, reentrancy, concurrent stop/start, interrupt policy và resource owner.

### 13.1 Idempotent stop

Plugin.stop có thể được gọi hai lần vì retry hoặc shutdown signal lặp. Một lifecycle vững cần:

- Không re-register khi STOPPING.
- Unregister idempotent hoặc an toàn khi listener đã được gỡ.
- shutdown executor nhiều lần không gây state bất nhất.
- close loader multiple times không tạo thêm vấn đề.
- Persist/report trạng thái để host không lầm timeout là STOPPED.

### 13.2 Trường hợp stop bị treo

Nếu plugin thread block I/O không phản hồi interrupt:

- **shutdownNow không bảo đảm termination**.
- Không dùng **Thread.stop()** để cưỡng bức kết thúc vì nguy hiểm, có thể phá invariants.
- Dùng I/O timeouts, cancel/callback cooperative protocol, đóng socket để unblock, thread dump và escalation policy.
- Không close loader trong lúc còn thread có thể cần load class mới hoặc resource; Javadoc coi concurrent close/load là undefined.
- Khi không thể đạt trạng thái an toàn, báo **STOP_TIMEOUT/BLOCKER**, không đánh dấu RELEASED.

## 14. Những anti-pattern thường tạo loader leaks

### 14.1 Chỉ close URLClassLoader mà không stop plugin

~~~java
loader.close();
plugin.stop();
~~~

Thứ tự này có thể đóng nguồn classes/resources **trong khi task plugin còn chạy**. Nếu task sau đó cần class chưa nạp hoặc resource mới, có thể lỗi. Hãy quiesce/stop workers trước, rồi close khi quyền sở hữu rõ ràng.

### 14.2 Đánh dấu plugin STOPPED rồi quên registry

~~~text
plugin.state = STOPPED
registry.listeners vẫn chứa plugin callback
~~~

Host vẫn giữ object plugin, nghĩa là loader có thể chưa collectible. Dùng register/unregister đối xứng với lifecycle.

### 14.3 Đổi TCCL mà không restore

Host worker thuộc thread pool lâu sống sẽ tiếp tục mang plugin loader sau khi task return. Dùng try/finally trên worker; kiểm thử task **ném exception** như ví dụ 3.

### 14.4 Tin rằng daemon thread không giữ references

Daemon thread vẫn là live Thread khi JVM còn chạy, vẫn có thể giữ plugin loader/TCCL/ThreadLocals. Daemon chỉ ảnh hưởng quy tắc JVM exit; không biến references thành weak.

### 14.5 Gọi System.gc() rồi coi weak.get()!=null là FAIL

GC không bảo đảm thu hồi ngay. Bài test như vậy có thể flaky trên CI, các JVM flags, các GC algorithms và debugger.

### 14.6 Chỉ kiểm thử trường hợp STOP sạch

Lỗi lifecycle thường xảy ra khi:

- Plugin.start thất bại **sau khi đã register callback**.
- Task đang chạy ném exception.
- Executor shutdown timeout.
- Close một stream ném IOException.
- Hai threads cùng gọi stop.
- Callback mới đến trong lúc đang unregister.
- Worker tạo thêm child Thread kế thừa plugin TCCL.
- Plugin hot reload nhiều lần làm old instances tích tụ.

Phải có negative tests cho những đường lỗi, không chỉ happy path.

### 14.7 Giữ ClassLoader trong metrics/test harness

Test tự tạo một list:

~~~java
static final java.util.List<ClassLoader> history =
        new java.util.ArrayList<>();
~~~

rồi thêm loader của từng deploy vào mà không xóa. Danh sách đó trở thành **strong reference leak do test tạo ra**. Nếu cần thống kê loader lifecycles, chỉ lưu string ID, deployment timestamps hoặc WeakReferences phù hợp.

### 14.8 Static field của host giữ Class/Method/MethodHandle

Plugin đã stop nhưng host cache reflection có thể giữ Class của plugin. Vì runtime Class của class thông thường liên hệ với defining loader, những references này có thể ngăn unloading. Clear caches theo plugin ID/version.

### 14.9 Chỉ xét heap mà quên native/file resources

Có thể không có loader leak nhưng có file/socket/native leak; và ngược lại loader leak dù OS handles đã đóng. Theo dõi hai thứ riêng.

## 15. Bộ checklist kiểm thử tự động

### 15.1 Trước stop

- [ ] Loader mới định nghĩa đúng plugin class (không nhầm parent-defined class).
- [ ] Plugin API type đến từ shared loader phù hợp.
- [ ] Plugin đã đăng ký listener/task/scheduler đúng expected count.
- [ ] Plugin-owned executor đã tạo worker theo nhu cầu thực tế.
- [ ] TCCL đang dùng đúng trong phạm vi công việc; host worker baseline được lưu.
- [ ] Có deployment/plugin ID rõ để truy tìm references khi reload.

### 15.2 Trong STOPPING

- [ ] Chặn callback/task mới trước khi drain.
- [ ] Unregister đầy đủ listeners/subscriptions/metrics hooks.
- [ ] Cancel queued tasks và kết thúc running tasks theo cooperative policy.
- [ ] awaitTermination thành công hoặc báo timeout rõ, không giả PASS.
- [ ] ThreadLocal.remove() trên các workers thích hợp.
- [ ] TCCL khôi phục previous ClassLoader ở shared host worker.
- [ ] Đóng plugin-owned streams/sockets/JDBC/JMX/resources.
- [ ] Không còn thread thực thi code plugin khi close loader.

### 15.3 Sau stop/close

- [ ] Close URLClassLoader do deployment sở hữu và ghi nhận IOException nếu có.
- [ ] Bỏ references từ registries, caches, Futures và metrics.
- [ ] Kiểm tra host registry count đúng baseline.
- [ ] Kiểm tra executor isTerminated/threads ended.
- [ ] Kiểm tra host worker context không còn old plugin loader.
- [ ] Nếu có GC probe, báo **OBSERVED/INCONCLUSIVE** theo kết quả thực, không assert tức thời.
- [ ] Có kế hoạch heap dump/JFR nếu repeated reload làm retained old loaders tăng.

Checklist là **hợp đồng kiểm thử đề xuất**; không phải mọi mục đều áp dụng cho mọi plugin. Nhưng mọi thành phần mà plugin **thực sự sở hữu** phải có cleanup đối xứng.

## 16. Một test matrix cho các tình huống thực tế

| Test | Thiết lập | Kết quả cần xác nhận |
| --- | --- | --- |
| **Close loaded class** | Load Ready, tạo instance, close loader, gọi Ready.text. | Instance đã load vẫn chạy. |
| **Close new class** | Giữ Later chưa load, close trước rồi yêu cầu Later. | Không tìm class mới từ loader đã close. |
| **Parent after close** | close plugin loader rồi yêu cầu String. | Parent/bootstrap class vẫn dùng được. |
| **Listener unregister** | Plugin start đăng ký callback. | Host registry count về baseline sau stop. |
| **Executor termination** | Plugin worker được start bằng submit. | awaitTermination thành công hoặc báo timeout. |
| **Unsafe TCCL negative** | Host worker chạy task đổi TCCL không restore. | Task sau nhìn thấy plugin TCCL vẫn bị giữ. |
| **finally on exception** | Plugin task throw sau khi set TCCL. | TCCL của host worker trở về previous. |
| **ThreadLocal cleanup** | Task đặt value từ plugin trên shared worker. | remove() trên worker trong finally. |
| **Partial start failure** | start ném lỗi sau khi register. | Startup rollback gỡ listener/stop workers. |
| **Repeated reload** | Deploy/stop 20-100 lần trong lab. | Registries/threads baseline; retention paths không tích tụ. |
| **GC probe** | Trả WeakReference sau cleanup, bỏ strong refs. | Quan sát GC nếu có; timeout là INCONCLUSIVE. |
| **Forced stalled task** | Task không phản hồi interrupt (chỉ lab). | Stop báo timeout, không close/đánh dấu safe tùy tiện. |

**Không cố định một số lần GC hoặc thời gian thu hồi chung cho mọi JVM** trong test matrix. Kiểm tra deterministic cleanup trước rồi mới dùng GC observations để phân tích bổ sung.

## 17. Tự kiểm tra kiến thức

**Câu 1.** Gọi URLClassLoader.close() rồi instance class đã nạp trước đó vẫn chạy. Có phải close lỗi không?

**Đáp án:** Không. Javadoc cho phép tiếp tục sử dụng classes/resources đã nạp.

**Câu 2.** Loader.close() có shutdown Thread do plugin tự tạo không?

**Đáp án:** Không. Thread/executor phải được plugin/container quản lý riêng, stop/cancel và await termination.

**Câu 3.** Plugin đã unregister listener nhưng host worker còn contextClassLoader=oldPluginLoader. Loader có thể bị giữ không?

**Đáp án:** Có. Live Thread giữ strong reference qua TCCL.

**Câu 4.** Vì sao phải restore previous TCCL trong finally?

**Đáp án:** Vì task có thể return sớm hoặc throw; host pool tái sử dụng cùng worker sau khi task xong.

**Câu 5.** Thread mới tạo khi parent thread đang mang plugin TCCL, parent restore sau đó thì child tự restore không?

**Đáp án:** Không. Thread con có context riêng đã kế thừa từ thời điểm tạo, trừ khi cấu hình khác.

**Câu 6.** awaitTermination trả false nhưng registry rỗng. Có nên báo plugin fully stopped không?

**Đáp án:** Không. Worker có thể còn chạy; cần xử lý timeout và xem resource/thread ownership.

**Câu 7.** WeakReference.get() chưa null sau System.gc(). Có chứng minh memory leak không?

**Đáp án:** Không. GC/unloading không có cam kết xảy ra ngay; kết quả là INCONCLUSIVE nếu không có thêm bằng chứng.

**Câu 8.** Tại sao giữ Class<?> của plugin trong host static Map có thể ngăn class unloading?

**Đáp án:** Class object thuộc class thông thường liên hệ với defining loader; strong reference giữ Class có thể giữ loader reachable.

**Câu 9.** close() thành công có nghĩa mọi FileInputStream do plugin mở đều được đóng không?

**Đáp án:** Không. URLClassLoader đóng resources do nó sở hữu; streams do plugin tự mở phải đóng riêng.

**Câu 10.** Khi nào nên close loader theo thứ tự lifecycle?

**Đáp án:** Sau khi chặn công việc mới, stop/drain threads, unregister callbacks, đóng resources và bảo đảm không còn concurrent loading không an toàn.

**Câu 11.** Vì sao repeated reload dễ phát hiện leak hơn một lần stop?

**Đáp án:** Các old loaders/callbacks/threads có thể tích tụ qua mỗi vòng, làm retention paths và metaspace pressure rõ hơn.

**Câu 12.** Công cụ nào trả lời ai đang giữ old ClassLoader?

**Đáp án:** Heap dump với Path to GC Roots/dominator tree, kết hợp thread dumps, JFR/jcmd metrics và code ownership.

## 18. Kết luận

~~~text
ĐÓNG LOADER != DỪNG PLUGIN != CLASS UNLOADING

STOP/QUIESCE:
  stop nhận công việc -> unregister -> drain/cancel tasks
  -> await worker termination -> cleanup TCCL/ThreadLocal
  -> close tài nguyên plugin

CLOSE:
  URLClassLoader.close()
  -> đóng handles do loader sở hữu
  -> không tìm new classes/resources của loader
  -> old Class/instances vẫn có thể dùng

RELEASE:
  clear host registries/caches/Futures/Class references
  -> loader có thể không còn strongly reachable
  -> GC có thể thu hồi vào thời điểm không xác định
  -> JVM có thể unload class sau đó

VERIFY:
  deterministic: trạng thái/tasks/threads/context/resources
  diagnostic: WeakReference, ReferenceQueue, JFR, jcmd, heap roots
~~~

**Bản kiểm thử tốt nhất không bắt GC làm theo một thời hạn tùy ý.** Nó chứng minh các bước cleanup thuộc quyền kiểm soát của ứng dụng bằng assertions deterministic, đồng thời dùng heap/GC observations để điều tra những tham chiếu còn tồn tại hoặc việc unload diễn ra sau đó.

### Tài liệu tham khảo

- Java SE 17 **URLClassLoader.close()**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- Java SE 17 **ClassLoader API**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 **Thread context ClassLoader**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- Java SE 17 **ExecutorService shutdown/awaitTermination**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Java SE 17 **WeakReference / ReferenceQueue**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html
- Java SE 17 **ClassLoadingMXBean**: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ClassLoadingMXBean.html
- Java SE 17 **JLS §12.7 Class Unloading**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7
- Java SE 17 **jcmd**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-verification-009">Tách từng kịch bản bằng loader hoặc tiến trình JVM mới</a>

<details>
<summary>Xem chi tiết</summary>

# Tách từng kịch bản bằng loader hoặc tiến trình JVM mới

Khi kiểm chứng cơ chế **Java class loading**, việc chạy nhiều kịch bản liên tiếp trong cùng một `ClassLoader` hoặc cùng tiến trình JVM có thể tạo **kết quả sai lệch do trạng thái còn sót**. Một class đã nạp được cache bởi loader, các `static` fields đã được khởi tạo, singleton đã tồn tại, `ServiceLoader` có cache provider, JVM đã liên kết class hoặc khởi tạo class thất bại trước đó. Những lần chạy sau có thể không còn thật sự bắt đầu từ điểm xuất phát giống lần đầu.

Để so sánh công bằng và tái hiện lỗi, có hai mức cô lập chính:

1. **Tạo một class loader mới cho mỗi kịch bản:** tách namespace và trạng thái gắn với **những class thực sự do loader mới định nghĩa**. Nhanh, phù hợp thử parent-first/child-first, class identity, `ClassCastException`, khởi tạo `static` của plugin, SPI và hot redeploy.
2. **Tạo một tiến trình JVM mới cho mỗi kịch bản:** tạo một runtime Java độc lập, thường cô lập cả system class loader, heap, static state, JIT, JVM flags, loaded classes, thread pool nội bộ và nhiều trạng thái toàn cục cấp tiến trình. Tốn chi phí khởi động hơn, phù hợp kiểm thử cách JVM được khởi động, initialization failure, native libraries, `System` properties, classpath/module path, GC/Metaspace và những thí nghiệm có thể treo hoặc làm hỏng process.

**Không đồng nhất hai mức này.** Một loader mới **không tạo JVM mới**; hai JVM mới **không bảo đảm cô lập tài nguyên bên ngoài** như file, database, Redis, network service, port, hoặc user credentials. Mức cô lập phải được chọn theo **trạng thái thực sự cần reset**.

Bài Knowledge này trình bày cách thiết kế thí nghiệm độc lập, giải thích ranh giới kỹ thuật, cung cấp **ví dụ Java 17 đã biên dịch và chạy kiểm chứng** với hai `URLClassLoader` cùng hai JVM con, sau đó hướng dẫn tổ chức test matrix, vòng đời loader/tiến trình, kết quả dự kiến và các lỗi dễ gây “test pass giả”.

## 1. Vì sao cùng một JVM có thể làm méo kết quả?

Một JVM không nạp lại cùng một class trong **cùng defining class loader** mỗi lần code gọi `loadClass`. Nếu lần đầu đã tạo `Class<?>`, lần sau trả về cùng định nghĩa cho cùng binary name theo cơ chế cache/loading chuẩn.

~~~text
JVM hiện tại
   |
   +-- Loader A
   |     +-- plugin.Counter   → Class #A
   |     +-- static count = 2
   |
   +-- Loader B
   |     +-- plugin.Counter   → Class #B
   |     +-- static count = 1
   |
   +-- System/Application loader
         +-- HostRegistry    → global cache của host
         +-- các library class shared qua delegation
~~~

Trong sơ đồ:

- `Class #A` và `Class #B` là **hai class JVM khác nhau** vì defining loader khác nhau, dù binary name giống hệt.
- `static count` thuộc về **định nghĩa class**, vì thế có thể tách giữa A/B.
- `HostRegistry` do parent/system loader định nghĩa vẫn là **một bản chung** trong cùng process; A/B có thể cùng sửa static state của nó.
- JVM có thể tiếp tục giữ class/loader cũ nếu còn reference, dù đã gọi `URLClassLoader.close()`.

### Các trạng thái làm test nhiễu nhau

| Trạng thái | Loader mới có tự reset không? | JVM mới có tách không? |
| --- | --- | --- |
| `static` fields thuộc class do plugin loader định nghĩa | **Có**, nếu mỗi loader thực sự tự định nghĩa bản class riêng | **Có** giữa các process |
| Class identity của `pkg.Plugin` do plugin định nghĩa | **Có** giữa hai defining loader | **Có** giữa process; không thể so `Class<?>` trực tiếp xuyên process |
| `static` fields của class do system/parent loader định nghĩa | **Không**; dùng chung parent `Class<?>` | **Có**, trong process mới |
| `System.setProperty` / process-wide Java system properties | **Không**, cùng đối tượng properties ở JVM hiện tại | **Có** ở mức JVM instance; startup args có thể truyền cùng giá trị ban đầu |
| Host singleton, cache, `ThreadLocal` trên worker sống lâu | **Không mặc định** | **Có** trong process con riêng khi kết thúc, nếu không nối tới resource ngoài |
| `ServiceLoader` object/cache host giữ | **Không**; cần tạo object mới, dùng đúng loader, dọn reference | **Có** trong child process, trừ dữ liệu service ngoài JVM |
| Class initialization failure của cùng định nghĩa class | **Không** được reset bằng gọi lại trong loader cũ | **Có** với JVM mới; loader mới cũng có thể có class definition mới |
| `Thread.currentThread().getContextClassLoader()` | **Không** tự đổi theo loader đang được tạo | Worker/thread mới có context mới theo cách runtime khởi tạo |
| JVM flags, module path lúc launch, default charset/runtime image | **Không** | **Có thể thay đổi** qua lệnh launch mới |
| JIT code cache, GC state, Metaspace trong runtime | **Không** | **Có** khi chạy JVM process khác |
| File, database, network, OS service | **Không** | **Không tự động**; cần sandbox/fixtures/tên tài nguyên riêng |

Các ô “có” trong cột JVM mới là **cô lập theo tiến trình**, không có nghĩa JVM mới quên các file đã ghi, DB rows, network writes, logs hay shared kernel state.

## 2. Loader mới chỉ cô lập khi định nghĩa đúng lớp cần thử

JVM xác định runtime type bằng **(binary name, defining loader)**. Hai `URLClassLoader` độc lập có thể định nghĩa riêng `lab.Counter`, nhưng nếu parent-first trả về class đã có từ parent thì `new URLClassLoader(...)` chưa chứng minh có type mới. Mỗi kịch bản cần kiểm tra `type.getClassLoader()` và `typeA == typeB`. Fixture duplicate-JAR và bài delegation trước đó chỉ ra cách chọn đúng defining loader.

## 3. Phạm vi reset được của trạng thái static

Initialization thành công xảy ra một lần theo runtime class; class erroneous cũng không được JVM tự retry trong cùng defining loader. Khi loader mới **thực sự định nghĩa** một `lab.Counter` mới, các static fields có trạng thái riêng, còn các singleton/static state thuộc loader cha và tài nguyên cấp process vẫn có thể được chia sẻ. Vì vậy thử nghiệm dưới đây so **hai fresh plugin loaders** với **hai tiến trình JVM mới**, để phân biệt isolation theo type và theo process.

## 4. Ví dụ Java 17: hai class loader và hai JVM process độc lập

Ví dụ tách rất rõ **class của plugin** khỏi **host classpath**. `lab.Counter` chỉ được biên dịch vào `plugin-classes`, còn host `ScenarioIsolationDemo` chạy trong thư mục hiện tại. Hai `URLClassLoader` có cùng URL nguồn bytecode nhưng **định nghĩa riêng** `lab.Counter`, do parent là platform loader và không tìm `lab.Counter` trong phần runtime chuẩn.

Sau đó host dùng `ProcessBuilder` chạy `lab.Counter.main` trong **hai JVM mới**. Mỗi child JVM có một `Counter` mới và `static count` ban đầu bằng 0.

### Cấu trúc thư mục

~~~text
isolation-demo/
├── ScenarioIsolationDemo.java
├── src/
│   └── lab/
│       └── Counter.java
└── plugin-classes/          (thư mục output của javac)
    └── lab/
        └── Counter.class
~~~

### File 1: src/lab/Counter.java

~~~java
package lab;

public final class Counter {
    private static int count;

    public static int next() {
        return ++count;
    }

    public static void main(String[] args) {
        System.out.println(next());
    }
}
~~~

`count` là `static` field gắn với **định nghĩa class `lab.Counter`**. `main` cho phép chạy cùng class như entry point của một JVM con, còn `next` cho phép host gọi qua reflection từ những Class được child loaders định nghĩa.

### File 2: ScenarioIsolationDemo.java

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ScenarioIsolationDemo {
    private static int next(ClassLoader loader) throws Exception {
        Class<?> type = loader.loadClass("lab.Counter");
        return (int) type.getMethod("next").invoke(null);
    }

    private static String runFreshJvm(Path classes) throws Exception {
        String executable = System.getProperty("os.name").toLowerCase()
                .contains("win") ? "java.exe" : "java";

        String java = Path.of(System.getProperty("java.home"),
                "bin", executable).toString();

        Process process = new ProcessBuilder(java, "-cp",
                classes.toString(), "lab.Counter")
                .redirectErrorStream(true)
                .start();

        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim();

        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException(
                    "Child JVM exit=" + exit + ": " + output);
        }
        return output;
    }

    public static void main(String[] args) throws Exception {
        Path classes = Path.of("plugin-classes")
                .toAbsolutePath().normalize();

        if (!Files.isRegularFile(classes.resolve("lab/Counter.class"))) {
            throw new IllegalStateException("Compile Counter first");
        }

        URL[] urls = { classes.toUri().toURL() };
        ClassLoader parent = ClassLoader.getPlatformClassLoader();

        try (URLClassLoader a = new URLClassLoader(urls, parent);
             URLClassLoader b = new URLClassLoader(urls, parent)) {

            Class<?> fromA = a.loadClass("lab.Counter");
            Class<?> fromB = b.loadClass("lab.Counter");

            System.out.println("Same Class in A: "
                    + (fromA == a.loadClass("lab.Counter")));
            System.out.println("A next: " + next(a));
            System.out.println("A next again: " + next(a));
            System.out.println("B next: " + next(b));
            System.out.println("Class A equals B: " + (fromA == fromB));
            System.out.println("A loader owns class: "
                    + (fromA.getClassLoader() == a));
            System.out.println("B loader owns class: "
                    + (fromB.getClassLoader() == b));
        }

        System.out.println("JVM run 1: " + runFreshJvm(classes));
        System.out.println("JVM run 2: " + runFreshJvm(classes));
    }
}
~~~

### Build và run trên JDK 17+

Dùng Bash/Linux/macOS từ thư mục `isolation-demo`:

~~~bash
mkdir -p plugin-classes
javac --release 17 -d plugin-classes src/lab/Counter.java
javac --release 17 ScenarioIsolationDemo.java
java ScenarioIsolationDemo
~~~

Với Windows PowerShell, thay lệnh tạo thư mục:

~~~powershell
New-Item -ItemType Directory -Force plugin-classes | Out-Null
javac --release 17 -d plugin-classes src/lab/Counter.java
javac --release 17 ScenarioIsolationDemo.java
java ScenarioIsolationDemo
~~~

**Stdout chính xác** với project/file được tạo đúng như trên:

~~~text
Same Class in A: true
A next: 1
A next again: 2
B next: 1
Class A equals B: false
A loader owns class: true
B loader owns class: true
JVM run 1: 1
JVM run 2: 1
~~~

Chương trình này đã được **biên dịch với `javac --release 17` và chạy trên OpenJDK**, thu được đúng toàn bộ output trên.

### Giải thích chi tiết

1. `a` và `b` đều có URL trỏ `plugin-classes`, nhưng chúng là **hai object URLClassLoader riêng** và parent `platform` không chứa `lab.Counter`.
2. `a.loadClass("lab.Counter")` gọi lại vẫn trả chính `fromA`, nên dòng đầu `true`.
3. `next(a)` thực thi static method của Class #A lần đầu và lần tiếp theo: kết quả **1**, rồi **2**.
4. `next(b)` dùng Class #B với static state riêng, cho kết quả **1**.
5. `fromA == fromB` là `false`; name giống nhau nhưng defining loader khác.
6. Hai phép kiểm tra `getClassLoader() == a/b` là `true`: bằng chứng class được định nghĩa đúng nơi yêu cầu, chứ không bị parent system loader chiếm.
7. `try-with-resources` gọi `URLClassLoader.close()`. Nó đóng tài nguyên loader; **không bảo đảm class unloading/GC ngay lập tức**. Với mục đích demo, việc đóng không ảnh hưởng kết quả đã in.
8. `ProcessBuilder` tìm executable Java bên trong `java.home/bin` của runtime hiện tại, dùng `java.exe` trên Windows hoặc `java` ở Linux/macOS.
9. Mỗi `ProcessBuilder.start()` tạo **một OS process/JVM riêng**, classpath chỉ tới `plugin-classes`, sau đó gọi `lab.Counter.main`. Static count của child process bắt đầu 0, nên mỗi lần in **1**.
10. Host đọc child output bằng UTF-8, chờ exit và kiểm tra exit code. `redirectErrorStream(true)` gom stderr vào stdout để tránh treo đọc một pipe khác không được tiêu thụ; trong ví dụ thành công, child chỉ in một số nguyên.

**Giới hạn thử nghiệm:** Class `Counter` không có dependency ngoài Java runtime nên `getPlatformClassLoader()` là parent phù hợp. Nếu plugin cần gọi API interface của host, không thể mặc định đưa platform loader làm parent: cần **shared API loader** và bảo đảm API identity đúng.

### Phép thử sẽ sai thế nào nếu sắp classpath không đúng?

Giả sử bạn biên dịch `Counter.java` vào chính thư mục host `.` hoặc đặt `plugin-classes` lên host classpath rồi dùng:

~~~java
new URLClassLoader(urls);
~~~

Đây là **fragment**, có parent mặc định là application/system loader. Parent-first có thể thấy `lab.Counter` và trả về cùng parent Class cho cả hai loaders. Khi đó:

~~~text
Class A equals B: true
B next: 3  (nếu A đã gọi next hai lần trên cùng parent Class)
~~~

Output ở đây là **minh họa kịch bản cấu hình sai có điều kiện**, không phải kết quả của demo chuẩn. Đừng sửa bằng cách ép child-first mọi package: với API chung, nhân bản class thường gây `ClassCastException`. Sửa **test packaging/classpath và delegation policy đúng mục tiêu**.

## 5. Khi nào loader mới là đủ?

Loader mới phù hợp khi đại lượng đang đo gắn trực tiếp với **class definition riêng của plugin**:

- Hai Class cùng binary name khác defining loader.
- Static field/initializer của class được loader mới định nghĩa.
- `ClassCastException` khi object của loader A bị cast sang Class tương ứng loader B.
- Parent-first/child-first lookup và `ClassNotFoundException`/`LinkageError` theo namespace.
- SPI/provider discovery trong một custom loader hoặc module layer cụ thể.
- Tải lại implementation plugin khi host/API contract vẫn ổn định.
- Kiểm tra lifecycle `URLClassLoader.close()`, weak references, class unloading sau khi thả reference.

### Nhưng loader mới không reset trạng thái parent

Một test ví dụ có class `HostMetrics` trong **application loader**:

~~~java
public final class HostMetrics {
    public static int count;
}
~~~

Đây là **fragment** (chưa có `package` và file riêng). Dù plugin A và B thuộc loader khác nhau, nếu cả hai ủy quyền class `HostMetrics` cho parent, chúng sẽ dùng **cùng field `HostMetrics.count`**. Loader mới không reset cache, singleton, logger registry hay `ThreadLocal` của class parent.

Cần xác định:

~~~text
State cần reset?
   |
   +-- ở class do child định nghĩa? → child loader mới có thể đủ
   |
   +-- ở class do parent/JDK định nghĩa? → reset tường minh hoặc fork JVM
   |
   +-- ngoài process (file/DB/network)? → fixtures riêng, cleanup ngoài JVM
~~~

Với **module layer động**, mỗi layer có thể tạo loader mới theo `defineModulesWithOneLoader`/`defineModulesWithManyLoaders`, nhưng các parent modules vẫn được chia sẻ; không đồng nhất “layer mới” với “JVM mới”. `ModuleLayer.boot()` là boot layer hiện hành, không được tạo lại khi thêm child layer.

## 6. Khi nào nên fork một JVM mới?

**JVM process mới** là lựa chọn mạnh hơn cho những bài kiểm chứng có thể để lại state khó reset hoặc làm JVM không còn ở trạng thái sạch:

| Mục tiêu kiểm thử | Vì sao fork JVM có giá trị |
| --- | --- |
| Static state trong **system/JDK classes** | Có runtime, system/application loader và heap riêng. |
| JVM/class initialization lỗi và không muốn retry cùng `Class<?>` | Process mới tạo lại class loading/initialization timeline. |
| `System.setProperty`, locale, default config và property initialization | Có bộ startup properties độc lập; cần truyền giá trị rõ bằng command-line arguments. |
| `-Xmx`, `-XX:*`, `--module-path`, `--add-opens`, `--add-exports` | Nhiều tùy chọn khởi động không thể thay giữa chừng trong cùng process. |
| GC/Metaspace/JFR hoặc class unloading benchmark | Mỗi lần chạy có JVM state và lịch sử allocation/GC riêng. |
| Deadlock hoặc một phép thử gây `System.exit`, crash, native code | Không làm chết/treo JVM đang chạy test khác; supervisor có thể hủy child. |
| Test ClassLoader+JNI native library conflicts | Native library load scope có quy tắc riêng; process mới tách native address space. |
| Bảo đảm thứ tự startup/classpath/module resolution | Mỗi child được launch với classpath và module graph từ đầu. |

**Nhược điểm:** Thời gian khởi động, CPU/RAM tăng; phải quản lý command-line flags, environment, stdin/stdout/stderr, timeout, exit code, dọn process tree và file artifacts. Một JVM con chạy cùng user/quyền OS với parent **không tự là security sandbox**. Muốn chạy plugin thật sự không tin cậy, kết hợp process isolation với OS account, sandbox/container, network policy, filesystem permissions và secrets boundaries.

### ProcessBuilder khác ClassLoader thế nào?

`new URLClassLoader(...)` tạo object **bên trong JVM hiện tại**. `new ProcessBuilder(...).start()` tạo process mới. Child process không chia sẻ địa chỉ object, `Class<?>`, singleton hay `static` field trực tiếp với parent; kết nối cần IPC/file/socket hoặc giao thức khác.

~~~text
Host JVM                               Child JVM
  heap / Class objects riêng              heap / Class objects riêng
  system loader / static state             system loader / static state
  URLClassLoader A                         plugin.Counter class
  URLClassLoader B                         static count = 1
      |                                          |
      +------------- ProcessBuilder ------------+
                             |
                        stdout / exit code
~~~

Giữa các JVM không thể viết `hostObject == childObject` theo nghĩa Java reference (hai object không nằm chung heap). Khi cần trao đổi kết quả, serial hóa dữ liệu hoặc truyền chuỗi/json/protobuf qua IPC và kiểm tra status.

## 7. Quản lý ProcessBuilder để test không bị treo

Ví dụ đầy đủ phần 4 dùng lối đơn giản vì child rất nhỏ và luôn thoát. Với test có thể deadlock, start server hoặc chạy lâu, cần **timeout và cleanup**.

Fragment mẫu (giả định `Process process` đã start):

~~~java
boolean finished = process.waitFor(
        10, java.util.concurrent.TimeUnit.SECONDS);

if (!finished) {
    process.destroyForcibly();
    process.waitFor();
    throw new IllegalStateException("Child JVM timeout");
}

int exitCode = process.exitValue();
if (exitCode != 0) {
    throw new IllegalStateException("Child JVM failed: " + exitCode);
}
~~~

Đây là **fragment**, không phải chương trình độc lập. Khi dùng trong ứng dụng thực:

- **Đọc/redirect stdout và stderr** để tránh child chặn vì pipe buffer đầy. Nếu child tạo output lớn, dùng thread đọc stream đồng thời hoặc redirect ra file; `readAllBytes()` giữ toàn bộ output trong memory, không nên áp dụng cho log vô hạn.
- **Timeout** phải đo trường hợp child treo do deadlock/chờ I/O. Nếu đọc `readAllBytes()` trước khi áp timeout thì chính việc đọc có thể **chờ tới EOF mãi**. Với child có khả năng treo, thiết kế đọc bất đồng bộ/redirect file và chờ có timeout một cách chủ động.
- **Dọn process descendants** khi child tự spawn grandchildren; `destroyForcibly()` của một Process không tự hứa hủy mọi process con/cháu trên mọi OS.
- **Chỉ truyền environment/working directory cần thiết**, tránh thừa kế bí mật/biến môi trường có thể thay hành vi runtime ngoài ý muốn. `ProcessBuilder.environment()` cho phép cấu hình environment của process con.
- **Bắt exit code và stderr**; không coi “stdout có dòng expected” là PASS nếu process cuối cùng thoát lỗi.
- **Không phụ thuộc absolute PID**: PID khác giữa lần chạy và có thể được tái sử dụng, không phải định danh logic cho scenario.

### Một kịch bản lỗi có thể phá các test tiếp theo

Nếu một test chạy:

~~~java
System.setProperty("demo.mode", "broken");
~~~

Đây là **fragment** chạy trong cùng JVM; property tồn tại cho những test khác trừ khi được restore/reset. Loader mới **không thay** `System.getProperties()` toàn process. Một forked JVM mới có properties riêng; tuy nhiên command-line `-Ddemo.mode=...` và environment có thể lại làm state giống nhau nếu runner truyền cùng cấu hình.

Nếu muốn test độc lập mà không fork, dùng `try/finally` giữ/restoring giá trị gốc cho property và các global hooks **chỉ khi đảm bảo không có thread khác cùng sửa state đó**. Với parallel test suite, reset toàn cục có thể cạnh tranh; fork JVM thường dễ tin cậy hơn.

## 8. Phân biệt “tạo ClassLoader mới” và “tạo ModuleLayer mới”

Từ Java 9, named modules có **module layer** và class loader mapping. Một module loader có thể được tạo khi:

~~~java
ModuleLayer layer = ModuleLayer.boot()
        .defineModulesWithOneLoader(configuration, parentLoader);
~~~

Đây là **fragment**; `configuration` phải là resolved `Configuration` hợp lệ và `parentLoader` phù hợp, chưa phải chương trình độc lập. `defineModulesWithOneLoader` tạo loader mới cho các modules của layer mới, dùng loader truyền vào làm parent theo API.

Nhưng:

- Parent layer/loader và các module runtime vẫn được chia sẻ theo cấu hình.
- `ModuleLayer.boot()` không khởi động một JVM mới và không bị tái tạo cho mỗi dynamic layer.
- Hai layer mới riêng biệt có thể định nghĩa cùng binary name bằng loaders khác nhau, nhưng điều đó **không biến object của chúng thành cùng type**.
- Module readability, `exports`, `opens`, `uses`, `provides` vẫn ảnh hưởng behavior. Cô lập loader chỉ là một phần của module isolation.
- Một layer không có `close()`; việc thu hồi phụ thuộc reachability của layer, Class, loader, threads và các references khác.

Trong test module/plugin, nếu đang đo **ModuleLayer resolution** cần bảo đảm parent configuration không vô tình chứa module target. Nếu muốn đo full **startup module graph**, hãy chạy child JVM mới với `--module-path`/`-m` tương ứng.

## 9. Không để trạng thái framework rò giữa hai scenario

`ServiceLoader` có cache provider riêng và các API `load(SPI)` có thể dùng **Thread Context ClassLoader**. Nếu scenario A thay TCCL hoặc giữ cache/listener của plugin cũ, loader mới ở scenario B vẫn có thể bị ảnh hưởng. Khi xong, khôi phục TCCL trong `finally`, bỏ registry/listener và bỏ cache tham chiếu plugin. Bài resource/SPI và bài lifecycle trong chương có các bài thử riêng về những reference paths này.

## 10. Đóng loader xong chưa đồng nghĩa đã unload class

`URLClassLoader.close()` đóng đường tìm class/resource do loader đó quản lý, nhưng không tự xóa các `Class<?>` đã load hay dọn mọi strong references. Khi test isolation pass, nó mới chứng minh state quan sát đúng theo kịch bản; chưa đủ khẳng định loader cũ đã được GC. Bài **Kiểm chứng vòng đời đóng loader, dừng plugin và giải phóng tham chiếu** đưa ra tiêu chí stop, TCCL cleanup và cách quan sát unload bằng weak references/heap tooling mà không giả định GC tất định.

## 11. Lập ma trận kiểm chứng và tiêu chí PASS

Một test suite tốt phải xác định **trạng thái cần reset, loader ownership và quan sát PASS** trước khi chạy.

| Scenario | Mức cô lập nên chọn | Assertion quan trọng |
| --- | --- | --- |
| Same-loader cache | Cùng loader trong một test | `a.loadClass(name) == a.loadClass(name)` |
| Static state của plugin | Hai loader mới, class chỉ child sở hữu | A: 1→2; B: 1; `ClassA != ClassB` |
| Duplicate API class | Hai defining loaders, hoặc shared-parent control | Compare `Class<?> ==`, `isInstance` và expected cast/error |
| Parent-first vs child-first | Loader cấu hình riêng, sources cố định | Class được định nghĩa ở loader được mong muốn |
| Initialization failure của plugin | New defining loader cho mỗi scenario | Lỗi xảy ra độc lập trên định nghĩa mới |
| Global `System` property / host singleton | JVM mới hoặc reset đồng bộ | Property/singleton baseline giống nhau mỗi run |
| Native library/`System.exit`/crash | Child JVM/process mới | Exit code, timeout, logs, không làm chết test runner |
| GC/Metaspace/class unloading | JVM mới cho các run đo độc lập | JVM flags, workload, GC logs, JFR, số lần GC/loader counts |
| Plugin hot redeploy | Loader/layer mới theo vòng deploy | Type identity mới, resources/old refs được dọn |
| Full startup module path | Fork với `--module-path`/`-m` | Named modules/resolution theo launch args |
| File/DB mutation | Process + test resource/fixture riêng | Không ghi lên dữ liệu dùng chung, cleanup sau test |

Nếu test cần **so sánh performance**, còn phải kiểm soát warm-up/JIT compilation, GC, CPU contention và môi trường. JVM mới giúp giảm nhiễu từ warm-up trước đó nhưng **không tự làm hai benchmark có điều kiện giống nhau**. Khi benchmark nghiêm túc nên dùng harness phù hợp như JMH, đo nhiều forks/iterations và báo sai số.

### Pseudocode tổ chức suite

~~~text
for each scenario:
    decide isolation policy:
        LOADER:
            allocate distinct loader, verify defining loader identity
            execute assertions
            cleanup tasks/caches/TCCL/resources
        JVM:
            spawn java process with explicit args, env, classpath/module path
            capture stdout/stderr, enforce timeout, read exit status
            cleanup process tree and external fixtures
    record test evidence and repeatability
~~~

Đây là **pseudocode**, không phải một test framework cụ thể. Cả hai mức cô lập nên đi kèm cleanup và assertions về **đúng state**, không chỉ kiểm tra “không có exception”.

## 12. Những lỗi kiểm thử thường gặp

**Lỗi 1 — “new loader chắc chắn new class”.** Sai. Parent-first có thể trả class do parent định nghĩa. Luôn so `type.getClassLoader()` và `Class<?> ==`.

**Lỗi 2 — “new loader reset toàn bộ static fields”.** Sai. Chỉ những class do loader mới định nghĩa có bản static state riêng. Static state của parent/JDK classes còn chung.

**Lỗi 3 — “ClassLoader.close() làm JVM quên class ngay”.** Sai. Closing không cưỡng bức class unloading; các strong references khác còn giữ lớp/loader.

**Lỗi 4 — “đổi TCCL rồi mọi loadClass dùng nó”.** Sai. Chỉ các API chủ động lấy context loader (một số ServiceLoader/resource lookup) bị tác động. `Class.forName(name, true, explicitLoader)` dùng loader được truyền tường minh.

**Lỗi 5 — “reload() trên ServiceLoader reset loader”.** Sai. `reload()` xóa cache provider của object ServiceLoader đó; không tạo loader mới hoặc reset class initialization.

**Lỗi 6 — “chạy test trong thứ tự khác mà pass là đủ”.** Không đủ. Nếu outcome phụ thuộc thứ tự, có leak hoặc shared state chưa được khai báo. Dùng fresh loader/fork và kiểm soát fixtures.

**Lỗi 7 — “JVM mới hoàn toàn sạch mọi thứ”.** Sai. File/database/network/external service, thời gian hệ thống, environment hoặc shared temp directory có thể còn dữ liệu từ run trước.

**Lỗi 8 — “ProcessBuilder.start rồi không đọc stderr có sao đâu”.** Có thể treo khi pipe đầy; cần drain/redirect stdout/stderr và quản lý exit/timeouts.

**Lỗi 9 — “process fork đồng nghĩa sandbox”.** Sai. Child Java chạy theo OS user/quyền của process nếu không đổi; cần isolation ở tầng OS/container cho code không tin cậy.

**Lỗi 10 — “dùng System.gc() để reset JVM giữa các bài test”.** Sai. GC không reset singleton/parent classes/system properties, không bảo đảm class unloading và không làm JVM giống process mới.

**Lỗi 11 — “ClassNotFoundException là kết quả giống ở mọi loader”.** Không đúng: source classpath, parent delegation, module graph và dynamic URLs có thể khác. Phải ghi rõ class loader được hỏi và đường tìm thực tế.

**Lỗi 12 — “cùng tên class đủ để cast an toàn”.** Sai. Defining loader tham gia identity. Nếu hai loader cùng define một API interface, có thể gặp `ClassCastException`/`ServiceConfigurationError`; đặt API chung ở parent.

## 13. Hướng dẫn debug khi hai kịch bản ảnh hưởng lẫn nhau

Khi test A chạy riêng PASS, nhưng chạy sau test B FAIL:

1. **In defining loader:** `type.getClassLoader()`, class name và module. Xác định A/B có thật sự dùng Class khác không.
2. **Kiểm tra parent delegation:** ClassFile có bị system classpath chứa trước không? Có shared API và implementation bị trộn không?
3. **In static state trước và sau:** Những class nào do parent định nghĩa? Singleton/cache của host có cần cleanup?
4. **Kiểm tra TCCL ngay tại worker:** Submitter và worker không mặc định giống nhau.
5. **Xem `ServiceLoader`/SPI provider cache:** Có giữ object từ scenario cũ không?
6. **Xem threads còn sống:** Worker/task/callback/plugin thread có kết thúc không? ThreadLocal và TCCL đã restore chưa?
7. **Dọn file, database, system properties:** Nếu fork vẫn fail, hướng tới state ngoài JVM.
8. **Chạy fork độc lập:** Nếu lỗi biến mất chỉ khi fork, tìm process-wide state/shared runtime như static host caches, JNI, JVM startup options.
9. **Chụp heap dump/JFR khi cần:** Nếu loader cũ còn reachable sau undeploy, tìm `Path To GC Roots` từ cache/threads/listeners thay vì chỉ tin `close()`.
10. **Viết test regression deterministic:** Thiết lập đầy đủ tiền điều kiện, tách scenario, assert kết quả và cleanup theo lifecycle.

Một diagnostic fragment hữu ích:

~~~java
Class<?> type = loader.loadClass("lab.Counter");
System.out.println("Name: " + type.getName());
System.out.println("Defining loader: " + type.getClassLoader());
System.out.println("Module: " + type.getModule());
System.out.println("TCCL: "
        + Thread.currentThread().getContextClassLoader());
~~~

Đây là **fragment**, cần biến `loader`. Các dòng loader/module/TCCL phụ thuộc môi trường, nên **không gắn stdout cố định** cho fragment này. Có thể dùng `Class<?> ==` thay vì chỉ so chuỗi tên.

## 14. Tự kiểm tra kiến thức

**Câu 1.** Gọi `new URLClassLoader(urls)` hai lần nhưng cả hai trả `String.class`. Có nghĩa lớp `String` đã được định nghĩa riêng bởi từng loader không?

**Đáp án:** Không. `String` là kiểu nền tảng do bootstrap loader định nghĩa; parent delegation trả cùng định nghĩa.

**Câu 2.** Có hai loader A/B riêng, `lab.Counter` chỉ có trên application classpath của parent. `A.loadClass("lab.Counter") == B.loadClass("lab.Counter")` có thể bằng `true` không?

**Đáp án:** Có. Với parent-first, cả hai có thể dùng cùng Class parent. Muốn kiểm tra cô lập child, phải để class target không bị parent lấy mất và xác nhận defining loader.

**Câu 3.** Vì sao static counter ở A có thể là 2 nhưng ở B là 1?

**Đáp án:** Vì `lab.Counter` do hai defining loader khác nhau định nghĩa; mỗi định nghĩa có static fields và initialization state riêng.

**Câu 4.** Một test thay `System.setProperty`; đổi URLClassLoader có reset property không?

**Đáp án:** Không, vì properties tồn tại ở phạm vi JVM/process. Cần reset đúng cách với đồng bộ thích hợp hoặc fork JVM mới.

**Câu 5.** Vì sao fork JVM phù hợp để kiểm tra `System.exit` hoặc `-XX:MaxMetaspaceSize`?

**Đáp án:** Có process độc lập để kết thúc/crash mà không giết test runner; JVM flags được xác định từ lúc launch riêng.

**Câu 6.** Dùng JVM process mới có tự rollback database writes của JVM trước không?

**Đáp án:** Không. Database là state ngoài process; cần database fixture/transaction cleanup/namespace cô lập.

**Câu 7.** `URLClassLoader.close()` có bảo đảm `Class<?>` của plugin biến mất không?

**Đáp án:** Không. Nếu còn Class/object/thread/static cache reference, class/loader có thể còn reachable. Unloading do GC/JVM quyết định.

**Câu 8.** Vì sao cần so `fromA.getClassLoader() == loaderA` khi test static isolation?

**Đáp án:** Để chứng minh class thực sự do loader A định nghĩa, thay vì bị parent loader cung cấp và vô tình chia sẻ static state.

**Câu 9.** Nếu runner tạo JVM con nhưng không đặt timeout, hậu quả là gì?

**Đáp án:** Test có thể treo vô hạn khi child deadlock/chờ I/O; cần timeout, giám sát exit và dọn process.

**Câu 10.** Hai loader mới có bảo đảm hai ServiceLoader trả đúng provider khác nhau không?

**Đáp án:** Không mặc định. Cần service API identity, TCCL/explicit loader, metadata SPI, parent delegation và cache đúng. Loader mới chỉ tạo namespace; discovery policy còn phải xác định.

## Ghi nhớ

**Muốn kiểm chứng độc lập, trước hết hỏi “trạng thái này thuộc class định nghĩa bởi loader nào, hay thuộc JVM/process nào?”** Dùng **loader mới** để kiểm tra class identity, static fields và plugin namespace; dùng **JVM mới** khi cần reset state toàn process, runtime flags, initialization history hoặc cô lập lỗi nặng. Với cả hai, kiểm soát **parent delegation, shared API, classpath/module path, TCCL, caches và cleanup**. JVM fork không thay thế việc cách ly **file/database/network**, và `URLClassLoader.close()` không có nghĩa unloading lập tức.

**Tài liệu chuẩn Java 17:**

- `ClassLoader` và `getClassLoadingLock`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- `URLClassLoader` và `close()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- `ProcessBuilder`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ProcessBuilder.html
- `Process`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Process.html
- `ModuleLayer` và `defineModulesWithOneLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html
- `ServiceLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- JLS `12, Execution, Loading, Linking and Initialization: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html
- JVMS `5, Loading, Linking and Initializing: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html


</details>

- [Quay lại đầu trang](#back-to-top)
