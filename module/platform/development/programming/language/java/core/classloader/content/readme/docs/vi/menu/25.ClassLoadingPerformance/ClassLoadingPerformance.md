<a id="back-to-top"></a>

# Hiệu năng

## Menu
- [Chi phí đọc JAR, verification, linking và initialization](#class-loading-performance-001)
- [Phân biệt độ trễ khởi động với chi phí lời gọi sau khi đã nạp](#class-loading-performance-002)
- [Nạp lười và tác động của preloading](#class-loading-performance-003)
- [Tái sử dụng loader so với tạo loader cho từng yêu cầu](#class-loading-performance-004)
- [Cache đúng phạm vi mà không giữ loader đã hết vòng đời](#class-loading-performance-005)
- [CDS, AppCDS và giới hạn theo loader hoặc cấu hình chạy](#class-loading-performance-006)
- [Đo hiệu năng với cùng JDK, classpath và tập lớp được sử dụng](#class-loading-performance-007)
- [Đánh đổi giữa số loader, mức cô lập và dung lượng metaspace](#class-loading-performance-008)

## <a id="class-loading-performance-001">Chi phí đọc JAR, verification, linking và initialization</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí đọc JAR, verification, linking và initialization trong Java ClassLoader

Khi một ứng dụng Java gặp **độ trễ ở lần gọi đầu tiên (cold path)**, nguyên nhân có thể nằm ở **tìm và đọc class từ JAR**, **nạp class**, **bytecode verification**, **linking/resolution**, **chạy static initializer**, hoặc ở **JIT compilation, nạp dependency, I/O và framework code** xảy ra sau đó. Đây là các khoản chi phí **khác nhau** về cơ chế, thời điểm và cách tối ưu.

**Điều quan trọng nhất:** Không được coi mọi thời gian ở `ClassLoader.loadClass(...)` là thời gian **đọc JAR**, và cũng không được coi mọi độ trễ khi gọi method đầu tiên là **verification**. Java cho phép JVM thực hiện một số bước linking/resolution **sớm hoặc muộn** miễn tuân thủ Java Language Specification (JLS) và Java Virtual Machine Specification (JVMS). Muốn chẩn đoán đúng cần hiểu ranh giới ngữ nghĩa, quan sát class loading/initialization và đo workload thực.

Bài học sử dụng **Java 17+**, phân biệt code chạy trên classpath, trong JAR và qua `URLClassLoader`. Với named modules/JPMS, JVM có thêm bước module resolution và quy tắc access, nhưng mô hình nền về load → link → initialize vẫn là điểm xuất phát.

## 1. Bản đồ vòng đời và điểm có thể phát sinh chi phí

```text
Lần đầu chương trình cần một class
          |
          v
[1] Chọn ClassLoader / delegation / tra class đã loaded
          |
          v
[2] Tìm bytecode của class
    - tìm entry trong JAR/directory/module
    - đọc bytes, có thể giải nén ZIP/JAR
    - có thể kiểm tra JAR signer/metadata
          |
          v
[3] Loading / tạo Class<?> và metadata của runtime type
          |
          v
[4] Linking
    ├── Verification: kiểm tra class/bytecode hợp lệ
    ├── Preparation: chuẩn bị storage, static mặc định
    └── Resolution: giải symbolic references (có thể lazy)
          |
          v
[5] Initialization khi JVM yêu cầu
    - khởi tạo superclass/những superinterfaces liên quan
    - chạy static field initializers và static blocks (<clinit>)
          |
          v
[6] Thực thi method/constructor
    - bytecode interpreter/JIT
    - I/O, network, DB, framework initialization nếu có
```

Sơ đồ chỉ giúp **phân loại hoạt động**, không khẳng định mỗi bước luôn diễn ra tuần tự, một lần và tách biệt được bằng một timer. Verification/preparation phải hoàn thành trước initialization, nhưng symbolic reference resolution được JVM phép thực hiện tại những thời điểm phù hợp, kể cả sau khi class đã initialized.

Một số hoạt động còn có thể xảy ra **đệ quy**: nạp class A dẫn tới nạp B, kiểm tra B, rồi nạp C; static initializer của A lại đọc config và mở mạng. Một phép đo “load A mất 200 ms” có thể bao gồm hành vi của nhiều dependencies/callbacks, không chỉ chính file `A.class`.

## 2. Các loại chi phí và cách nhìn đúng

| Giai đoạn | Công việc có thể phát sinh | Dấu hiệu thường thấy | Hiểu nhầm cần tránh |
|---|---|---|---|
| **Class lookup** | `findLoadedClass`, parent delegation, classpath/JAR/module search | Nhiều classpath entries, plugin loaders, lớp chưa có trong cache | “Mỗi `loadClass` đều đọc lại JAR” |
| **Đọc JAR/ZIP** | Mở archive, tìm central directory/entry, đọc/giải nén bytes | Cold filesystem/cache, archive lớn, nhiều class loads | “JAR càng lớn luôn càng chậm theo tỷ lệ thuận” |
| **JAR signature/metadata** | Xử lý manifest, signer/certificates theo cách loader/JAR verifier làm việc | Signed JAR, nhiều entries, security/package checks | “JAR signature verification = bytecode verification” |
| **Class definition** | Tạo runtime type, metadata, constant pool, quan hệ loader/module/package | Metaspace và class count tăng | “Gọi `Class.forName` luôn định nghĩa class mới” |
| **Bytecode verification** | Kiểm tra cấu trúc, type correctness, stack/control flow | `VerifyError` hoặc thời gian class linking | “Tắt verifier là tối ưu production nên làm” |
| **Preparation** | Tạo static fields mặc định, method tables/VM structures | Bắt đầu có metadata/static storage | “Preparation chạy `static {}`” |
| **Resolution** | Tra symbolic references tới class/field/method và kiểm tra hợp lệ | First-use latency, `NoSuchMethodError` khi liên kết | “Mọi reference đều resolve hết khi load” |
| **Initialization** | Chạy `<clinit>`, static field expressions, có thể I/O/locks | Log từ static block, first-use spike | “`loadClass` luôn chạy static block” |
| **JIT/runtime warmup** | Interpreter profiling, compilation, GC, caches | Sau nhiều lượt chạy nhanh hơn | “Lượt thứ hai nhanh hoàn toàn do class cache” |

Các giai đoạn có thể chồng lấn trong cùng một lần thực thi. Thông số microbenchmark đơn lẻ không đủ chia tuyệt đối tổng latency thành từng phần.

## 3. Chi phí tìm và đọc lớp từ JAR

### 3.1. JAR là định dạng ZIP có cấu trúc tra cứu

JAR chuẩn về cơ bản là ZIP archive với manifest/metadata và các entries, ví dụ:

```text
plugins/greeter.jar
├── META-INF/MANIFEST.MF
├── demo/Plugin.class
├── demo/settings.txt
└── demo/internal/Helper.class
```

Khi `URLClassLoader` tìm `demo.Plugin`, nó cần tìm resource entry `demo/Plugin.class` (hoặc theo chính sách nguồn phù hợp), mở/đọc bytes và định nghĩa class. Công việc có thể gồm:

1. Chọn **loader đang chịu trách nhiệm** và kiểm tra parent/đã nạp.
2. Tìm JAR/URL chứa class; có thể dò qua nhiều URLs khi class không có ở nguồn đầu.
3. Đọc thông tin ZIP central directory/entry metadata theo implementation.
4. Đọc dữ liệu entry; nếu ZIP entry dùng **DEFLATED** thì phải giải nén.
5. Áp dụng xử lý manifest, package sealing, signer/certificate khi liên quan.
6. Đưa bytecode vào cơ chế class definition/linking của JVM.

**Không có quy tắc “JAR 100 MB nghĩa là phải giải nén toàn bộ 100 MB khi cần một class”.** Loader thường truy cập entry phù hợp, và OS/JDK có caching. Mức độ I/O và decompress phụ thuộc implementation, compression, thứ tự tìm, cache và cách đóng gói.

### 3.2. Classpath dài không đồng nghĩa chậm tuyến tính với mỗi lần gọi

Classpath có nhiều JAR khiến việc **tìm class chưa biết ở JAR nào** có thể cần xem xét nhiều nguồn. Nhưng:

- JVM/loader duy trì kết quả đã load theo runtime class identity.
- ZIP index, file handles, archive reader và filesystem page cache có thể được tái sử dụng.
- Với parent delegation, một class nền tảng có thể được trả ngay từ parent mà **không cần quét plugin JAR**.
- Custom class loader có thể có cache/index/lookup policy khác `URLClassLoader`.
- Phần lớn ứng dụng chỉ nạp **một tập con** trong tổng class entries của mọi dependencies.

Vì vậy **số lượng JAR** và **tổng số bytes JAR** là chỉ báo cần khảo sát, không phải phép tính thời gian tin cậy.

### 3.3. Cold filesystem cache và warm cache

Đọc JAR lần đầu sau khi máy khởi động/deploy có thể chạm storage thật. Các lần sau có thể được phục vụ bởi **OS page cache**, cache ZIP/JAR của runtime hoặc filesystem cache. Ngược lại, disk/network filesystem chậm, antivirus/file scanning, storage cạnh tranh hoặc archive từ nguồn xa có thể khiến chi phí tăng mạnh.

Một phép so sánh trên **cùng JVM** còn chịu class-loader cache. Một phép so sánh trên **JVM mới** có thể vẫn tận dụng OS page cache. Vì vậy phải định nghĩa rõ “cold” là:

- JVM mới hay JVM đã chạy?
- Loader mới hay loader đang tái sử dụng?
- Process mới nhưng OS page cache còn nóng?
- JIT đã warm chưa?
- Framework/plugin đã khởi tạo chưa?
- JAR ở local SSD hay network mount?

Đừng gọi tất cả những trường hợp này chung là “cold start” rồi so sánh số đo.

### 3.4. JAR signing khác bytecode verification

Khi sử dụng **signed JAR**, JAR verification/signature processing có thể kiểm tra digest, signer metadata và certificate associations của entries tùy cách truy cập/loader. Công việc này khác với **JVM bytecode verification**, vốn xác minh cấu trúc class file và tính an toàn về kiểu, stack, branch, instruction.

| Hành động | Mục tiêu |
|---|---|
| Xác minh JAR entry/signature | Nội dung entry, signer metadata/chữ ký phù hợp với quy tắc JAR |
| Package signer consistency | Các classes trong **cùng runtime package/defining loader** có certificate set nhất quán |
| Bytecode verification | Instructions, type safety, stack/control flow/class file hợp lệ |
| Authorization/trust | Ứng dụng/tổ chức có quyết định tin cậy nguồn JAR không — là câu hỏi riêng |

**Không bỏ kiểm tra chữ ký hay bytecode safety chỉ để tăng tốc startup.** Các kiểm tra có mục tiêu khác nhau; tối ưu thực tế nên bắt đầu từ dependency scope, I/O và initialization code.

## 4. Loading: lấy bytecode và tạo runtime class

JLS `12.2 và JVMS `5.3 mô tả **loading**: tìm binary representation của class/interface và tạo runtime class tương ứng.

`ClassLoader.loadClass("demo.Plugin")` sử dụng **binary name**, không phải đường `demo/Plugin.class`. Parent-first implementation thông thường:

```text
loadClass(name)
  |
  +-- class đã được loaded/ghi nhận? -> trả Class<?>
  |
  +-- thử parent loader trước
  |       |
  |       +-- parent có class -> trả class của parent
  |
  +-- findClass(name) ở loader này
          |
          +-- tìm class bytes và defineClass(...)
```

Class identity:

```text
(binary name, defining loader)
```

Gọi `loadClass` lần nữa trên cùng loader đối với một class đã có thường trả lại cùng `Class<?>`; không lặp lại `defineClass`, bytecode verification và static initialization như đang nạp một runtime class mới.

**Nhưng chi phí repeat lookup không bằng 0.** Vẫn có method call, kiểm tra lookup/locking tùy loader, và tác động JIT/runtime. Loader custom có thể làm I/O/logic thừa trước khi cache lookup nếu code viết sai.

### Loader mới tạo “cold class” mới

Hai loader khác nhau có thể định nghĩa cùng binary name thành **hai runtime classes riêng**. Với plugin reload:

```text
LoaderV1 -> demo.Plugin (runtime class V1)
LoaderV2 -> demo.Plugin (runtime class V2)
```

V2 phải thực hiện các công việc load/link/init cho runtime type của nó theo những điều kiện tương ứng; không thể coi V2 “warm” chỉ vì V1 đã được nạp trước đó trong một defining loader khác. Một số caches cấp JVM/OS vẫn có thể giúp nhưng không thể dùng lại tất cả runtime type identity/state.

Tạo hàng trăm class loaders để “song song hóa startup” chưa chắc hiệu quả: có thêm class metadata, trùng dependencies, I/O contention, verification/initialization lặp lại và áp lực bộ nhớ.

## 5. Verification: JVM kiểm tra bytecode trước khi thực thi

Verification là một phần của **linking**. JVM kiểm tra class representation/bytecode có tuân thủ các quy tắc của Java Virtual Machine không, ví dụ:

- Class file đúng cấu trúc, constant pool và metadata hợp lệ.
- Instruction opcode và operand có định dạng hợp lệ.
- Branch targets nằm ở ranh giới instruction hợp lệ.
- Operand stack/local variables có types thích hợp trong các nhánh control flow.
- Method descriptor, return operation và một số quan hệ kiểu được kiểm tra.
- Các thông tin verification như `StackMapTable` có cấu trúc/ý nghĩa phù hợp khi cần.

**Chi phí verification** phụ thuộc kích thước/độ phức tạp code, số lượng methods/classes, dependency loading và các quyết định của JVM implementation. Nó không nhất thiết tỷ lệ với **dung lượng compressed JAR**.

Đặc biệt, class loader tìm bytes thành công **không bảo đảm** bytecode hợp lệ. JVM có thể ném:

- `ClassFormatError`: binary class file sai cấu trúc.
- `VerifyError`: bytecode không qua type/control-flow checks.
- `UnsupportedClassVersionError`: class-file version mới hơn runtime hỗ trợ.
- Các `LinkageError` khác nếu quan hệ runtime types/references không hợp lệ.

**Không dùng các tùy chọn tắt/né bytecode verification như một “tối ưu mặc định”.** Chúng có thể không được hỗ trợ hoặc có ngữ nghĩa thay đổi theo JDK, làm suy yếu invariant JVM. Nếu verification xuất hiện đáng kể trong profile, hãy đo lượng class/method mới được nạp và lý do codegen/transform pipeline sinh nhiều lớp.

## 6. Linking: preparation và resolution không phải initialization

**Linking** gồm verification, preparation và resolution của symbolic references. Nhưng resolution có thể thực hiện **eager hoặc lazy** theo JVM, không phải lúc nào cũng hoàn tất toàn bộ trước khi class được dùng.

### 6.1. Preparation

Preparation tạo storage cho static fields và đặt **giá trị mặc định**, đồng thời VM có thể chuẩn bị các cấu trúc nội bộ phục vụ method dispatch. Ví dụ:

```java
class State {
    static int count = compute();
    static Object cache = new Object();

    static int compute() { return 7; }
}
```

Đây là **snippet minh họa**, không có `main` riêng.

Theo mô hình JLS, preparation có trách nhiệm chuẩn bị static storage; assignment `count = compute()` và `new Object()` thuộc **initialization**, không phải preparation. JVM có quyền tối ưu/chuẩn bị thêm metadata, nhưng không được làm thay đổi Java observable semantics.

Một trường hợp đặc biệt là **constant variables** theo JLS (như `static final int LIMIT = 10;`): compiler/JVM có xử lý giá trị compile-time constant riêng. Không được suy diễn rằng mọi `static final` đều là constant variable; ví dụ `static final Integer BOXED = 10;` hoặc `static final String ID = fetch();` phải tính trong initialization.

### 6.2. Resolution

Bytecode có symbolic references đến:

- Class/interface khác.
- Field và method của class/interface khác.
- Method handles, dynamically-computed constants, invokedynamic call sites khi thích hợp.

Resolution thay/kiểm tra references symbolic để runtime có thể dùng chúng theo đúng types/access rules. Ví dụ class A có method gọi `helper.Missing.run()`; JVM có thể **chưa resolve** method/class liên quan cho đến đường code yêu cầu.

Sơ đồ:

```text
A loaded
  |
  +-- A symbolic reference -> B.method
          |
          +-- JVM có thể resolve trước hoặc tại lần dùng
          +-- kiểm tra B/lookup/access/descriptor
          +-- dùng kết quả resolve theo runtime implementation
```

**Sai lầm:** “Lời gọi `loadClass("A")` thành công thì mọi method/dependency của A chắc chắn usable”. Một số lỗi như `NoSuchMethodError`, `NoSuchFieldError`, `IllegalAccessError` hoặc `NoClassDefFoundError` có thể chỉ xuất hiện khi code path cần reference ấy.

JLS `12.3 cho phép implementation linh hoạt về thời điểm resolution nhưng yêu cầu errors xuất hiện ở thời điểm chương trình có thể trực tiếp hoặc gián tiếp yêu cầu linkage.

### 6.3. Linking có thể phân tán qua thời gian

Một startup profiler không nên cố gắn toàn bộ **resolution cost** vào giai đoạn `Class.forName` đầu tiên. Một phần có thể phát sinh trong later invocations, reflection, lambda bootstrap hoặc dynamic linkage. Hãy đo cả **cold first request** và **steady-state** nếu mục tiêu là latency dịch vụ.

## 7. Initialization: nơi static code có thể làm startup cực chậm

Initialization thực thi **class variable initializers** và **static initializer blocks** theo quy tắc JLS `12.4. Đây là **code ứng dụng tùy ý**: có thể tạo cache, parse configuration, mở database connection, đọc JAR resources, khởi động thread hoặc chờ lock.

Ví dụ:

```java
public final class ExpensiveConfig {
    static final java.util.Map<String, String> SETTINGS = loadAll();

    private static java.util.Map<String, String> loadAll() {
        // Đọc file, parse JSON, tra môi trường, v.v.
        return java.util.Map.of("mode", "default");
    }
}
```

Snippet biên dịch được trong một file tên `ExpensiveConfig.java`, nhưng **không có `main`**; các comment mô tả công việc thực tế có thể phát sinh. Thời gian class initialization có thể lớn nếu `loadAll()` thực hiện I/O/network.

**Phân biệt quan trọng:**

- `loader.loadClass("demo.Plugin")` **không chủ động yêu cầu initialization** theo contract thông thường.
- `Class.forName("demo.Plugin", false, loader)` tìm và liên kết class theo contract, **không yêu cầu initialization**.
- `Class.forName("demo.Plugin", true, loader)` **yêu cầu initialization** nếu class chưa được initialized.
- Tạo instance của class hoặc gọi static method/ghi static field có thể kích hoạt initialization.
- Đọc **compile-time constant variable** qua tên class không nhất thiết kích hoạt initialization của declaring class.
- Lấy class literal `SomeClass.class` thường **không tự kích hoạt initialization** của class đó.
- Các đường reflection/method handles/initialization dependencies có quy tắc riêng; đọc JavaDoc/JLS cụ thể.

Không nên khẳng định `loadClass` chỉ làm I/O rồi “không link gì” — chỉ có thể khẳng định `loadClass` **không tự yêu cầu chạy `<clinit>`**.

### 7.1. Initialization chạy một lần trên mỗi runtime class

Trong một JVM, một runtime class cụ thể được initialized theo thủ tục JVM bảo đảm đồng bộ. Nếu hai threads cùng yêu cầu khởi tạo class chưa initialized, có thể có **thời gian chờ initialization lock**. Static initializer có I/O chậm hoặc lock/dependency cycle có thể khuếch đại latency và tạo deadlock ngoài ý muốn.

Tạo loader thế hệ mới cho cùng binary name tạo **runtime class mới** với initialization độc lập. Việc plugin reload nhiều lần có thể kích hoạt lại static setup/I/O/allocations trên mỗi thế hệ.

### 7.2. Failure có thể biến thành class unusable

Nếu static initializer ném exception, JVM có thể ném `ExceptionInInitializerError` ở lần đầu. Runtime class bị ghi nhận ở **erroneous state**, và các lần sử dụng tiếp theo có thể ném `NoClassDefFoundError` với thông tin initialization thất bại. Không “retry” thành công chỉ bằng cách gọi `Class.forName` lại trên **cùng runtime class/defining loader**.

Với plugin cần retry logic, hãy tách I/O hoặc network initialization sang lifecycle method có quản lý trạng thái và error handling rõ, không giấu tất cả trong `static {}`.

## 8. Demo Java 17+: nạp từ JAR và quan sát load so với initialize

Demo tạo:

- Một class `demo.Plugin` nằm **chỉ trong plugin.jar**, không nằm trên runtime classpath của host.
- Một resource `demo/settings.txt` trong cùng JAR.
- Một `URLClassLoader` tạo ở host.
- Các bước đọc JAR resource, load class, `Class.forName(..., false, loader)` rồi yêu cầu initialize.

**Mục đích:** Minh họa **thứ tự quan sát được** bằng stdout và chứng minh `<clinit>` không chạy ngay khi loader trả `Class<?>`. Ví dụ này **không cố gán thời gian mỗi bước chính xác**.

### 8.1. Cấu trúc thư mục

```text
phase-demo/
├── src/
│   ├── plugin/demo/Plugin.java
│   └── host/host/PhaseProbe.java
├── out/
│   ├── plugin/demo/settings.txt
│   └── host/
└── plugin.jar
```

### 8.2. Code plugin

Tệp **`src/plugin/demo/Plugin.java`**:

```java
package demo;

public final class Plugin {
    static {
        System.out.println("PLUGIN <clinit>");
    }

    public static String greet() {
        return "hello";
    }
}
```

Chuỗi `PLUGIN <clinit>` là **dòng do static block của ứng dụng in**, không phải VM log. Chúng ta dùng nó để nhận diện lúc initialization code được thực thi.

### 8.3. Code host

Tệp **`src/host/host/PhaseProbe.java`**:

```java
package host;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class PhaseProbe {
    public static void main(String[] args) throws Exception {
        URL jar = Path.of(args[0]).toUri().toURL();

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {jar},
                PhaseProbe.class.getClassLoader())) {

            System.out.println("loader created");

            try (InputStream in = loader.getResourceAsStream(
                    "demo/settings.txt")) {
                if (in == null) {
                    throw new IllegalStateException(
                            "missing resource");
                }
                System.out.println("resource: "
                        + new String(in.readAllBytes(),
                                StandardCharsets.UTF_8).trim());
            }

            Class<?> type = loader.loadClass("demo.Plugin");

            System.out.println("loaded by child: "
                    + (type.getClassLoader() == loader));
            System.out.println("after loadClass");

            System.out.println("forName(false) same: "
                    + (Class.forName(
                            "demo.Plugin", false, loader) == type));
            System.out.println("before initialization request");

            Class.forName("demo.Plugin", true, loader);

            System.out.println("after initialization request");
            System.out.println("call: "
                    + type.getMethod("greet").invoke(null));

            System.out.println("cached type: "
                    + (loader.loadClass("demo.Plugin") == type));
        }
    }
}
```

**Giải thích cơ chế:**

1. Tạo `URLClassLoader` chưa có nghĩa đã nạp `demo.Plugin` hoặc đọc toàn bộ JAR.
2. `getResourceAsStream("demo/settings.txt")` đọc một resource trong JAR mà không cần initialize `demo.Plugin`.
3. `loadClass("demo.Plugin")` tìm và nạp class, nhưng không yêu cầu chạy static block của `Plugin`.
4. `Class.forName(..., false, loader)` trả cùng runtime class, vẫn không chạy `<clinit>`.
5. `Class.forName(..., true, loader)` yêu cầu initialization, và static block in dòng `PLUGIN <clinit>`.
6. Gọi `greet` sau đó dùng class đã initialized; lookup cuối trả đúng cùng đối tượng `Class<?>`.
7. `try-with-resources` đóng loader/file handles khi scope kết thúc; không phải class unload ngay lập tức.

### 8.4. Build/run trên Linux, macOS hoặc Git Bash

Từ thư mục `phase-demo`:

```bash
mkdir -p out/plugin/demo out/host

javac --release 17 -d out/plugin \
  src/plugin/demo/Plugin.java

javac --release 17 -d out/host \
  src/host/host/PhaseProbe.java

printf 'mode=fast\n' > out/plugin/demo/settings.txt

jar --create --file plugin.jar -C out/plugin .

java -cp out/host host.PhaseProbe plugin.jar
```

**Không thêm `plugin.jar` vào `-cp` của host**, vì mục đích là để `URLClassLoader` con **thực sự định nghĩa plugin class**. `Path.of(args[0]).toUri().toURL()` chuyển đường JAR từ command argument thành URL để loader mở.

### 8.5. Build/run trên PowerShell

```powershell
New-Item -ItemType Directory -Force -Path out/plugin/demo, out/host | Out-Null

javac --release 17 -d out/plugin src/plugin/demo/Plugin.java
javac --release 17 -d out/host src/host/host/PhaseProbe.java

[System.IO.File]::WriteAllText(
    "out/plugin/demo/settings.txt",
    "mode=fast",
    [System.Text.UTF8Encoding]::new($false))

jar --create --file plugin.jar -C out/plugin .

java -cp out/host host.PhaseProbe plugin.jar
```

### 8.6. Kết quả đã kiểm chứng

Ví dụ được **biên dịch với `javac --release 17`** và chạy thành công trên **OpenJDK 24**; stdout chính xác:

```text
loader created
resource: mode=fast
loaded by child: true
after loadClass
forName(false) same: true
before initialization request
PLUGIN <clinit>
after initialization request
call: hello
cached type: true
```

Kết quả cho thấy:

- `loaded by child: true` xác nhận defining loader plugin không phải host loader.
- Dòng `PLUGIN <clinit>` chỉ xuất hiện **sau** `before initialization request` và **trước** `after initialization request`.
- Không có `PLUGIN <clinit>` mới khi gọi static method hay tra lại class, vì runtime class đã initialized.
- Việc `loader.getResourceAsStream` tìm đúng entry **không chứng minh** bytecode plugin đã được verify/initialized ngay lúc đọc resource.

**Không suy luận quá mức:** Stdout không tiết lộ chính xác thời điểm JVM verify từng method hay resolve từng reference. JLS cho phép thời điểm linking linh hoạt, vì vậy muốn khảo sát chi tiết phải dùng JVM logging/profiler.

## 9. Quan sát class load, verification và initialization bằng JVM logging

Trên HotSpot/OpenJDK Java 17+, **unified logging** giúp quan sát các sự kiện class loading/initialization. Có thể chạy lại demo với:

```bash
java '-Xlog:class+load=info,class+init=info' \
  -cp out/host host.PhaseProbe plugin.jar
```

PowerShell (một dòng):

```powershell
java "-Xlog:class+load=info,class+init=info" -cp out/host host.PhaseProbe plugin.jar
```

Lệnh đã được kiểm tra trên OpenJDK 24; log JVM chứa các dòng tương tự:

```text
[...][info][class,load] demo.Plugin source: file:/.../plugin.jar
[...][info][class,init] Start class verification for: demo.Plugin
[...][info][class,init] End class verification for: demo.Plugin
[...][info][class,init] ... Initializing 'demo/Plugin' ... by thread "main"
```

Các dấu `[...]` và đường `file:/.../plugin.jar` là **ký hiệu rút gọn**: timestamp, địa chỉ, thread, vị trí JAR và thứ tự log phụ thuộc JVM, OS, tùy chọn logging và những classes khác nạp trong lúc chạy. Đây là **dạng log minh họa từ phép kiểm thử**, không phải stdout cố định để assert.

Khi cần ít log hơn, ghi ra file rồi lọc theo tên class:

```bash
java '-Xlog:class+load=info,class+init=info:file=classes.log' \
  -cp out/host host.PhaseProbe plugin.jar
```

Tùy JVM vendor/phiên bản, tag và mức độ chi tiết có thể khác. Kiểm tra những log tags JVM hỗ trợ bằng:

```bash
java -Xlog:help
```

**Logging cũng tiêu tốn thời gian.** Không đo startup latency production rồi kết luận “bật verbose logs khiến verification chậm N%” nếu chưa tách overhead ghi log và I/O của chính công cụ đo.

### 9.1. Log không phải stopwatch cho từng bước

`[class,load]` báo sự kiện lớp được nạp theo mức logging, nhưng không trực tiếp cho phép trừ timestamp và khẳng định phần chênh lệch là **I/O JAR thuần túy**. Khoảng giữa `class,load` và `class,init` còn có scheduling, other class loading, linking, application code, safepoints hoặc tool overhead.

`-Xlog:class+init` cho nhiều sự kiện về verification/initialization nhưng **không biến mọi hoạt động linking thành một biểu đồ nhân quả đầy đủ**. Cần công cụ kết hợp nếu mục tiêu là truy tìm bottleneck trong môi trường thực.

## 10. Tại sao phép đo một lần với System.nanoTime dễ gây nhầm?

Một đoạn code có thể đo wall-clock elapsed (thực chất là elapsed monotonic time) của một thao tác:

```java
long t0 = System.nanoTime();
Class<?> type = loader.loadClass("demo.Plugin");
long elapsedNs = System.nanoTime() - t0;

System.out.println("loadClass elapsed ns: " + elapsedNs);
```

Đây là **snippet minh họa trong method** với biến `loader` đã có; không phải ứng dụng độc lập. Số in ra **thay đổi mỗi lần chạy** và không đại diện cho “chi phí chỉ đọc bytes của plugin.jar”.

Tại sao?

- `loadClass` có thể ủy quyền parent, tìm JAR, nạp dependencies và thực hiện một phần linking theo JVM.
- Scheduling, GC, OS page cache, CPU frequency scaling và I/O cạnh tranh thay đổi elapsed time.
- `System.nanoTime` đo thời gian trải qua giữa hai thời điểm, **không gán nhãn nguyên nhân**.
- Đo lần đầu và lần thứ hai trên **cùng loader** là so sánh **class chưa nạp** với **class đã nạp**, hai workload khác nhau.
- Nếu đo nhiều lần trên một loader và cùng binary name, thường bạn chỉ benchmark cached lookup.
- Nếu tạo loader mới mỗi vòng, bạn lại thêm chi phí tạo loader, archive handling, GC/metaspace và class generation; có thể bị nhiễu bởi pressure mới.

### Phép đo đầu-cuối vẫn hữu ích khi xác định đúng mục tiêu

Ví dụ bạn muốn biết **người dùng chờ bao lâu đến khi plugin sẵn sàng**. Hãy đo đúng pipeline:

```text
start request
   -> tạo loader
   -> load provider
   -> verify/link theo JVM
   -> initialize/instantiate
   -> đọc config
   -> start plugin
   -> ready
```

Đó là một **end-to-end latency metric**, có giá trị cho người dùng. Nhưng đừng gọi nó “thời gian verification” chỉ vì method `Class.forName(...)` xuất hiện trong call stack.

### Phân loại ít nhất ba phép đo

| Loại đo | Cách tổ chức | Trả lời câu hỏi |
|---|---|---|
| **Cold startup** | JVM process mới, workload/OS cache/flags được ghi nhận | Khởi động dịch vụ/plugin mất bao lâu? |
| **First use** | Một JVM đang chạy; class/module/provider lần đầu được sử dụng | Request đầu tiên chịu chi phí gì? |
| **Steady state** | Nhiều request sau warmup, JVM/JIT đã ổn định tương đối | Độ trễ thông thường sau nạp lớp là bao nhiêu? |

Cả ba cần xem **p50, p95/p99, variance**, không chỉ một lần đo. Với benchmark micro-level Java, dùng **JMH** khi phù hợp để tổ chức warmup, forks và đo lặp lại; vẫn phải thiết kế state để không vô tình đo cùng class đã được load hoặc dùng OS cache không tương ứng scenario cần khảo sát.

## 11. Đo và tìm bottleneck bằng công cụ phù hợp

### 11.1. Unified logging và Java Flight Recorder

Bộ công cụ hữu ích trên nhiều HotSpot/JDK:

- **`-Xlog:class+load=info`**: tìm class nào đang nạp, từ nguồn nào; quan sát nhiều loads.
- **`-Xlog:class+init=info`**: theo dõi sự kiện liên quan initialization và, tùy release, verification.
- **Java Flight Recorder (JFR)**: phân tích hoạt động JVM trong giai đoạn startup/first request; quan sát class-loading events, CPU samples, allocation/GC, I/O khi events/config được bật phù hợp.
- **`jcmd <pid> VM.classloader_stats`**: thống kê class-loader footprint khi JVM hỗ trợ.
- **Async profiler / sampling profiler**: xem CPU stacks/thời gian trong ZIP, verifier, linker, reflection, class initialization hoặc chính app code.
- **I/O/file diagnostics**: file opens, bytes read, page faults, storage latency; công cụ cụ thể phụ thuộc OS.

Không phải mọi event đều bật mặc định hoặc có mặt trên mọi vendor/JDK. Phải kiểm tra cấu hình JFR và logging trước khi kết luận một event “không xảy ra” chỉ vì trace không ghi nhận.

### 11.2. Đọc stack profile theo nhóm nguyên nhân

| Stack/signals thấy nhiều | Giả thuyết cần kiểm tra |
|---|---|
| ZIP/JAR reading, `JarFile`, filesystem read | Nhiều JAR opens/class bytes/resource reads; cache/storage |
| `ClassLoader.loadClass`, custom `findClass`, URL search | Lookup policy, parent delegation, scanning, custom overhead |
| Class verification/linking VM code | Số class/method/code generation hoặc resolution |
| Class initialization routines, `<clinit>` | Static initializer làm CPU/I/O/locks |
| Reflection, service provider discovery | Framework scanning, class/link/init, provider constructors |
| JIT compilation threads | Warmup/code generation khác class loading |
| GC/safepoint pressure | Allocation/metadata churn, plugin loader generations |
| DB/network client connect | Application initialization code, không phải JAR I/O |

**Tìm root cause rồi mới tối ưu.** Nếu 95% startup delay nằm trong một static block mở mạng, việc chuyển DEFLATE sang STORED cho JAR có thể ít hiệu quả hơn nhiều so với tách I/O ra khỏi `<clinit>`.

## 12. Tối ưu đọc JAR/class loading đúng mục tiêu

### 12.1. Giảm số class thực sự phải load lúc startup

- Tránh scan và initialize mọi plugin khi người dùng chỉ cần một số ít.
- Với SPI, tránh iterate/instantiate toàn bộ providers sớm nếu yêu cầu chưa dùng.
- Hoãn load optional features tới lúc cần (có state machine và error handling).
- Kiểm tra framework annotation/reflection scanning, auto-configuration và dependency discovery.
- Giảm plugin reload/loader creation không cần thiết; tái sử dụng loader trong vòng đời hợp lệ.

**Lưu ý:** “lazy hơn” có thể giảm startup nhưng **chuyển độ trễ sang first request**. Cần quyết định ngân sách latency ở đâu thay vì coi lazy là thắng tuyệt đối.

### 12.2. Đóng gói và classpath hợp lý

- Loại bỏ dependencies thật sự không dùng, đặc biệt JARs bị framework scan hoặc mở trong startup path.
- Giảm split/duplicate classes và package conflicts.
- Đặt những resources/classes thường đi cùng nhau trong deployment dễ truy cập.
- Với plugin loader, giới hạn URLs đúng nhóm plugin thay vì đưa hàng trăm unrelated JARs vào mọi loader.
- Tránh mở/đóng cùng archive không cần thiết trong vòng lặp nghiệp vụ.
- Kiểm tra compression level nếu profiling cho thấy inflate/decompress thật sự đáng kể; phải đo tradeoff kích thước file, I/O và CPU.

**Không có quy tắc “nén JAR ít nhất luôn nhanh nhất”**: giảm nén tiết kiệm CPU giải nén nhưng tăng bytes phải đọc, ảnh hưởng I/O/cache. Trên network filesystem, tradeoff có thể đảo ngược. Cũng không có quy tắc mọi ứng dụng đều chạy nhanh nhất khi đóng thành một fat JAR — nested JAR loaders, scanning/indexing và framework behavior có thể khác classpath thường.

### 12.3. Không tự xây custom ClassLoader cho mục tiêu nhỏ

`URLClassLoader` và system/module loaders đã triển khai nhiều tối ưu/ràng buộc cần thiết. Custom loader chỉ nên dùng khi có yêu cầu như:

- Nguồn class bytes không phải JAR/directory thông thường.
- Namespace/dependency isolation riêng thật sự cần.
- Bytecode transformation/loading policy có chủ đích.
- Plugin lifecycle/domain khác loader hiện tại.

Một loader tự viết đọc toàn bộ JAR trên mỗi `findClass` hoặc không lưu trạng thái có thể biến class lookup thành bottleneck nghiêm trọng. Nhưng **không tự cache `Class<?>` sai quy tắc hoặc bỏ parent delegation/linking** để “tăng tốc”: dễ gây `LinkageError` và class identity mismatch.

### 12.4. CDS và AppCDS

**Class Data Sharing (CDS)** trên HotSpot có thể giúp giảm chi phí khởi động, tăng khả năng chia sẻ class metadata và giảm memory footprint cho những class/loader/archive configurations được hỗ trợ. **Application CDS (AppCDS)** mở rộng khả năng chia sẻ tới một số application classes.

Có thể đọc Java launcher docs/HotSpot CDS docs và kiểm tra:

```bash
java -Xshare:auto -version
```

Đây là lệnh minh họa lựa chọn chia sẻ dữ liệu khi có archive thích hợp; **không đo một mình** để suy ra ứng dụng đã tăng tốc bao nhiêu. Có thể dùng unified logging liên quan CDS để xác nhận runtime có dùng shared archive không.

Giới hạn quan trọng:

- CDS **không** làm `static {}` chứa network call trở thành miễn phí.
- CDS không bảo đảm mọi class từ **custom class loaders** hoặc plugins được share đúng như bootstrap/platform/application loader classes.
- Chia sẻ class metadata và chia sẻ **runtime static state/plugin instances** là hai việc khác nhau.
- Lợi ích phụ thuộc archive, JDK, classpath/module path, deployment và workload thực tế.

Đừng tuyên bố “bật CDS bỏ hoàn toàn verification, linking và initialization của mọi class”. Cần đo theo cấu hình hỗ trợ và xác minh hoạt động thực.

## 13. Tối ưu initialization thay vì làm JVM phải “lười kiểm tra”

Một class có static initializer phức tạp:

```java
final class ExpensiveStart {
    static final Map<String, String> CONFIG = readRemoteConfig();

    static Map<String, String> readRemoteConfig() {
        // I/O đồng bộ ở lần sử dụng class đầu tiên.
        throw new UnsupportedOperationException(
                "Illustrative only");
    }
}
```

Đây là **đoạn minh họa không độc lập**, cần `import java.util.Map;`. Lệnh ném exception ở đây nhằm nhấn mạnh tính nguy hiểm của I/O/lỗi trong static initialization, **không phải mẫu áp dụng**.

Vấn đề:

- Lần active use đầu tiên có thể chịu chi phí network và timeout.
- Các threads khác chờ initialization cùng runtime class.
- Nếu initialization lỗi, class có thể ở erroneous state, khó retry.
- Reload loader generation có thể khởi chạy lại toàn bộ công việc.

Thay thế thường tốt hơn: **lifecycle rõ ràng**.

```java
public final class PluginService {
    private volatile Config config;

    public void start(Config loaded) {
        if (loaded == null) {
            throw new IllegalArgumentException("config required");
        }
        config = loaded;
    }

    public String mode() {
        Config c = config;
        if (c == null) {
            throw new IllegalStateException("not started");
        }
        return c.mode();
    }

    public record Config(String mode) {}
}
```

Đây là class có thể biên dịch trong `PluginService.java` trên Java 17+, **không có `main`**. Nó cho phép host quyết định **khi nào đọc config**, đặt timeout/retry và đánh dấu plugin sẵn sàng. `volatile` chỉ phục vụ visibility của reference trong ví dụ; production lifecycle đa bước cần synchronization/state machine thích hợp.

**Đánh đổi:** Initialization tường minh không làm tổng công việc biến mất; nó giúp di chuyển, đo, retry và quản lý công việc theo đúng lifecycle. Đừng chỉ chuyển code sang constructor rồi tuyên bố đã tối ưu nếu startup vẫn phải tạo toàn bộ objects ngay lập tức.

### Lazy holder cho dữ liệu nội bộ thuần CPU

Nếu muốn lazy khởi tạo một cấu trúc bất biến khi thật sự dùng:

```java
public final class LookupTable {
    private LookupTable() {}

    private static final class Holder {
        static final java.util.Map<String, Integer> TABLE =
                java.util.Map.of("a", 1, "b", 2);
    }

    public static int get(String key) {
        return Holder.TABLE.getOrDefault(key, -1);
    }
}
```

Đây là utility class có thể biên dịch trong `LookupTable.java`. `Holder` được initialized khi đường code sử dụng trường `Holder.TABLE` theo quy tắc active use; thường tránh làm công việc đó khi chỉ nạp outer class.

Tuy vậy **không** đặt network I/O có khả năng thất bại lâu dài vào lazy holder mà không có cơ chế retry/timeout: failure vẫn là class initialization failure của `Holder`.

## 14. JIT warmup và initialization là hai hiện tượng khác nhau

Đôi khi sau 1.000 request, method chạy nhanh hơn đáng kể. Có thể là:

- JVM đã nạp những classes chưa được dùng trước đó.
- Class initialization hoàn tất.
- OS/JAR caches nóng.
- JIT compiler có đủ profiling data để compile/optimize methods.
- Heap caches/application data structures đã được tạo.
- Network/DB connection pools đã sẵn sàng.

Một lời gọi method lần thứ 1 có thể trả chậm vì `<clinit>`; lời gọi lần thứ 1000 nhanh vì JIT compilation. Hai việc đều thuộc “warmup” theo ngôn ngữ đời thường nhưng cần **phân biệt khi profiling**.

JIT cũng có chi phí CPU/compilation threads và code cache; đó **không phải verification**. Nếu muốn đo thời gian Java business method ở steady state, hãy dùng công cụ benchmark/profiler theo đúng mục đích, tránh benchmark class loading lẫn vào algorithm performance.

## 15. Ảnh hưởng của JPMS, ServiceLoader và plugin architecture

### Named module và module graph

JPMS có module resolution, readability, exports/opens và service provider declarations. Những hoạt động này có thể ảnh hưởng startup/discovery:

- Việc resolve module configuration xảy ra khi launcher hoặc `ModuleLayer` được thiết lập theo contract.
- Class load/link/init vẫn áp dụng cho classes trong named modules.
- `ModuleLayer.defineModulesWithOneLoader` và `defineModulesWithManyLoaders` tạo những loader mappings khác nhau, ảnh hưởng số runtime namespaces.
- Class-loader cache và resource access còn phụ thuộc module membership/encapsulation.

Không quy toàn bộ thời gian module resolution thành “JAR read”. Module configuration và runtime class linking có mục tiêu/chi phí khác nhau.

### ServiceLoader

`ServiceLoader.load(Service.class)` dùng **thread context class loader** theo Java SE 17 API; overload nhận loader tường minh dùng loader đó. Providers được phát hiện/khởi tạo **lazy** theo cơ chế của `ServiceLoader`; duyệt toàn bộ provider instances có thể khởi chạy:

```text
descriptor/module discovery
    -> provider class loading
    -> verification/linking
    -> initialization
    -> provider constructor/factory
    -> application initialization
```

Vì thế một framework chạy `for (Service s : ServiceLoader.load(Service.class))` trong startup có thể chịu nhiều giai đoạn cùng lúc. Khi tính latency, hãy phân biệt **khởi tạo đối tượng ServiceLoader** với **duyệt và thực sự instantiate providers**.

### Per-plugin loader vs shared loader

- **Mỗi plugin một loader** có thể cách ly dependencies và vòng đời, nhưng có thể nạp/verify/initialize những class private giống nhau nhiều lần qua nhiều defining loaders.
- **Một loader chung** có thể chia sẻ classes/static state và giảm trùng metadata, nhưng gây version conflicts hoặc không cho các plugins hot-reload độc lập bằng class identity mới.
- Cả hai cần giữ **shared SPI/DTO contracts** ở loader chung thích hợp nếu host và plugins trao đổi typed objects.
- Nhiều loader generations chưa được cleanup có thể làm tăng metaspace và GC pressure, ảnh hưởng hiệu năng lâu dài.

Đừng chuyển sang shared loader chỉ để giảm startup mà không kiểm tra class identity, dependency versioning và lifecycle.

## 16. Lỗi thường gặp và cách phân loại

### A. `ClassNotFoundException`

Loader được chọn không tìm thấy binary class name. Kiểm tra classpath/JAR URL, parent delegation, TCCL và custom loader resource mapping. Đây thường là **vấn đề lookup**, không chứng minh “verification quá chậm”.

### B. `NoClassDefFoundError`

Có thể do dependency cần thiết không hiện diện khi JVM resolve, binary name mismatch hoặc class đã rơi vào trạng thái **initialization thất bại**. Đọc đầy đủ cause/message và biết class nào lỗi đầu tiên.

### C. `ClassFormatError` / `VerifyError`

Class-file bytes sai format hoặc bytecode verification thất bại. Đừng “fix” bằng cách tắt verifier để startup nhanh hơn. Kiểm tra transformer/code generator, class-file version và nguyên nhân sai bytecode.

### D. `NoSuchMethodError` / `NoSuchFieldError`

Symbolic reference không resolve như bytecode kỳ vọng, thường do runtime dependency version mismatch. Lỗi có thể xuất hiện **lúc method path lần đầu thực sự chạy**, không nhất thiết lúc load class ban đầu.

### E. `ExceptionInInitializerError`

Static initializer ném exception. Class có thể ở erroneous state; retry trên cùng defining loader thường thất bại. Đưa I/O/logic dễ thất bại khỏi `static {}` nếu cần retry/lifecycle.

### F. `SecurityException` khi class loading

Có thể là JAR package sealing, signer/certificate set inconsistency hoặc namespace `java.*` bị cấm. Đây là class loading constraints, **không nhất thiết do SecurityManager**.

### G. `OutOfMemoryError: Metaspace`

Nhiều loader generations/classes/metadata có thể tăng memory pressure. Kiểm tra classloader retention, TCCL, reflection caches, `ServiceLoader` instances, thread pools và GC/unloading behavior. Không chỉ tăng `MaxMetaspaceSize` để che classloader leak.

## 17. Quy trình tối ưu hiệu năng có thể áp dụng ngay

1. **Xác định chỉ số mục tiêu:** process startup đến ready, latency first request hay steady-state throughput/p99.
2. **Chốt môi trường:** Java vendor/version, CPU, storage, OS, heap/metaspace, GC, CDS, JAR packaging, container CPU quota.
3. **Phân biệt cold/warm:** JVM mới, OS cache, loader generation và JIT state.
4. **Ghi class-load/class-init logs** một lần để tìm classes và nguồn JAR liên quan; nhớ đo riêng overhead logging.
5. **Thu JFR/CPU/I/O profile** đúng đoạn slow path, không chỉ tổng milliseconds.
6. **Tách giai đoạn ứng dụng:** module/plugin discovery, class load, provider instantiation, config I/O, framework startup, readiness.
7. **Tối ưu đúng nguyên nhân:** giảm scan/unused deps, giảm loader churn, lazy optional features, cải thiện filesystem access, thay static I/O bằng lifecycle có quản lý.
8. **Giữ invariant và security:** không bỏ JVM verification, không lách signer/sealing/module checks.
9. **Benchmark lại** nhiều runs/forks, theo p50/p95/p99 và class/memory footprint; so sánh trên workload tương đương.
10. **Kiểm thử chức năng** dưới cả cold/warm/parallel startup, nhất là thread-safety, dependency failures và plugin reload.

Đừng đo trên một JVM đã nạp tất cả classes rồi kết luận “đọc JAR không đáng kể” cho startup mới. Cũng đừng đo một JVM mới duy nhất có background antivirus đang quét file rồi kết luận mọi users sẽ chậm như vậy.

## 18. Câu hỏi tự kiểm tra

**1. Gọi `loader.loadClass("demo.Plugin")` có nhất thiết chạy `static {}` không?** Không. Method không tự yêu cầu initialization theo hợp đồng thông thường.

**2. `Class.forName("demo.Plugin", false, loader)` khác overload `true` ở điểm nào?** `false` không yêu cầu initialize class; `true` yêu cầu initialization nếu chưa thực hiện.

**3. `Preparation` có chạy `static int n = expensive()` không?** Không. Preparation đặt default static values; static initializer expression được thực thi trong initialization.

**4. JVM có bắt buộc resolve tất cả symbolic references ngay lúc linking ban đầu không?** Không. Có thể eager/lazy theo yêu cầu chuẩn.

**5. Mỗi lần gọi `loadClass` có đọc lại class bytes từ JAR không?** Không nhất thiết; class đã nạp có thể được trả từ loader cache.

**6. Đọc `settings.txt` từ JAR có tự khởi tạo `Plugin` cùng JAR không?** Không. Resource lookup không đồng nghĩa class initialization.

**7. Vì sao class-file verification khác JAR signature verification?** Một bên kiểm tra bytecode/type safety; bên kia liên quan metadata/chữ ký/tính toàn vẹn của JAR entry.

**8. `Class.forName(..., true, ...)` mất 300 ms có nghĩa verifier mất 300 ms?** Không. Thời gian có thể gồm loading/linking, dependencies và application static initializer.

**9. `NoSuchMethodError` chỉ xuất hiện ngay lúc `loadClass` sao?** Không. Symbolic resolution có thể được trì hoãn tới điểm dùng liên quan.

**10. CDS có làm network I/O trong `<clinit>` biến mất không?** Không. CDS có thể tối ưu một số khía cạnh class metadata/load path nhưng không tự bỏ application initialization code.

**11. Vì sao tạo loader mới cho mỗi request có thể gây chi phí lớn?** Mỗi loader tạo namespace riêng, có thể làm lặp class definition/link/init, metadata và archive work.

**12. Cách so sánh đúng cold startup và steady state?** Định nghĩa riêng test conditions, đo nhiều lần/forks, ghi OS/JIT/cache state và profile bottleneck.

## 19. Tài liệu chính thức

- **Java Language Specification Java SE 17, Chapter 12 — Execution:** loading (`12.2), linking (`12.3), initialization (`12.4), timing và lazy resolution: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html
- **Java Virtual Machine Specification Java SE 17, Chapter 5 — Loading, Linking, and Initializing:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- **Java SE 17 `ClassLoader` API:** delegation, `loadClass`, `findClass` và class loading rules: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 `Class.forName` API:** tham số `initialize`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java SE 17 `URLClassLoader`:** nạp từ URLs/JARs và resource lookup: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- **Java SE 17 JAR File Specification:** manifest, entry structure, signed JAR và package metadata: https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html
- **Java SE 17 `java` launcher reference:** `-Xlog`, `-Xshare` và JVM options: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **Java SE 17 `ServiceLoader` API:** provider loading/discovery: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- **OpenJDK JEP 310 — Application Class-Data Sharing:** https://openjdk.org/jeps/310

**Ghi nhớ:** Tìm bytecode trong JAR, JVM verification, linking/resolution và chạy `<clinit>` là những **chi phí có nguồn gốc khác nhau**. `loadClass` không đồng nghĩa `initialize`, `getResourceAsStream` không đồng nghĩa nạp lớp, và resolution có thể lazy. Để tối ưu hiệu quả, phải **đo đúng giai đoạn, xác định cold/warm và cải thiện bottleneck thực**, không đánh đổi tính đúng đắn hoặc an toàn của JVM để lấy những con số startup tưởng như nhanh hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-performance-002">Phân biệt độ trễ khởi động với chi phí lời gọi sau khi đã nạp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt độ trễ khởi động với chi phí lời gọi sau khi đã nạp

## 1. Đừng đánh đồng cold start với steady-state execution

Trong Java, **lần đầu cần một class** và **một lần gọi method khi class đã sẵn sàng** là hai workloads khác nhau. JVM có thể cần tìm class bytes, hỏi parent classloader, tạo runtime `Class<?>`, verify/link class, thực thi static initializer và nạp thêm dependencies ở lần dùng đầu. Sau đó các lời gọi cùng method thường **không** lặp lại toàn bộ quá trình nạp, định nghĩa và khởi tạo class ấy.

Cần phân biệt ba câu hỏi:

1. **Độ trễ khởi động (startup latency):** từ một mốc bắt đầu được định nghĩa đến khi JVM/app/plugin đạt điều kiện sẵn sàng.
2. **Độ trễ lần dùng đầu (cold/first-use latency):** một feature đã được gọi lần đầu, có thể kéo theo lazy loading, initialization, resource I/O và provider discovery.
3. **Chi phí sau khi đã nạp (warm/steady-state invocation):** khi class, object và tài nguyên phù hợp đã sẵn sàng, một method/request hoạt động nhanh đến đâu.

Cả ba đều quan trọng nhưng **không nên đo một rồi đặt tên là cái khác**. Một ứng dụng lazy khởi động rất nhanh có thể khiến request đầu chậm; một ứng dụng eager startup lâu hơn có thể bảo đảm p99 sau readiness ổn định hơn.

**Điểm cốt lõi:** nếu `loader.loadClass("plugin.Action")` lần đầu mất nhiều thời gian hơn `action.execute()` trong vòng lặp nóng, điều đó không chứng minh ClassLoader “làm chậm mọi method call”. Hai phép đo đã bao gồm công việc khác nhau.

## 2. Bản đồ chi phí từ khi tạo JVM tới request nóng

~~~text
OS launches java process
          |
          v
JVM bootstrap + app/bootstrap code
          |
          v
Tạo classloader/module layer + tìm JARs/resources
          |
          v
Loading: delegation, findClass, defineClass
          |
          v
Linking: verify, prepare, resolve khi cần
          |
          v
Initialization: static fields/blocks (<clinit>)
          |
          v
SPI/ServiceLoader discovery + constructor + config I/O
          |
          v
FIRST SUCCESSFUL FEATURE RESPONSE
          |
          v
JIT profiling/compilation + caches warming
          |
          v
WARM REQUEST/INVOCATION (steady state)
~~~

Đây là **mô hình phân tích**, không phải một chuỗi tuyệt đối cho mọi ứng dụng. JVM có thể giải quyết symbolic references muộn; code có thể lazy-load plugin sau readiness; JIT có thể compile/recompile khi ứng dụng đã nhận traffic. Những điểm đo phải mô tả **đúng công việc được thực hiện**.

| Thành phần | Cold/first-use | Mỗi lời gọi method nóng? |
|---|---|---|
| JVM bootstrap | Thường có ở process startup | Không |
| Tạo `URLClassLoader`/`ModuleLayer` | Khi tạo generation | Không nếu dùng lại |
| Đọc class bytes từ JAR/URL | Khi class cần định nghĩa | Không cho class đã loaded trong loader đó |
| Parent delegation/class definition | Khi class cần loaded | Không lặp toàn bộ pipeline mỗi method call |
| Verification/linking | Khi class/reference cần xử lý | Không phải chi phí cố định cho mỗi invocation |
| `<clinit>` | Khi class initialize lần đầu | Một lần thành công trên mỗi runtime class |
| `ServiceLoader` provider discovery | Thường khi iterate lần đầu | Không nhất thiết nếu cache/provider reused |
| Đọc/parse config resource | First use/cache miss | Phụ thuộc cache strategy |
| JIT compilation/profiling | Warm-up và tùy VM | Không tương đương một lần compile mỗi call |
| Business logic/allocations/locks | Có | Có theo workload |
| Method dispatch | Có | Có, có thể được inline/tối ưu |

**Không có hằng số “mỗi class loading tốn N ms”** áp dụng mọi JDK, CPU, JAR và classloader policy.

## 3. Định nghĩa chính xác mốc startup

`System.nanoTime()` ở dòng đầu `main()` chỉ cho thấy khoảng đo **sau khi `main` đã bắt đầu**. Nó bỏ qua phần JVM launcher/bootstrap trước đó. Vì vậy, muốn đo startup thực:

- Với CLI: có thể đo bên ngoài process từ lúc gọi `java` tới kết quả/exit.
- Với HTTP service: đo từ start process/container tới **readiness endpoint thực sự phản ánh công việc bắt buộc**, và tới response đầu tiên nếu SLA yêu cầu.
- Với plugin runtime: đo từ activation request tới lúc plugin registered, dependencies initialized và thực hiện call contract thành công.
- Với modular runtime: nếu tạo `ModuleLayer` hoặc resolve modules trong activation, phải tính đó vào workload nếu sản phẩm thật cũng thực hiện như vậy.

Một cột mốc `health=200` được trả **trước** khi các plugin bắt buộc sẵn sàng không nên được dùng để tuyên bố startup nhanh hơn. Phải so cùng định nghĩa “ready”.

### First use không nhất thiết trùng startup

Một feature hiếm dùng có thể chỉ lần đầu được gọi sau một tuần mới:

- Gọi `Class.forName` hoặc `ServiceLoader`.
- Parse config.
- Load JNI library.
- Mở network connection.
- Kích hoạt code path JIT chưa warm.

Độ trễ của request ấy là **feature cold path**, không phải JVM process startup. Cần log separate spans để thấy đúng nguyên nhân.

## 4. ClassLoader.loadClass lần đầu và lần sau

Trong implementation mặc định, ClassLoader làm gần như:

~~~text
loadClass(name)
     |
     v
getClassLoadingLock(name)
     |
     v
findLoadedClass(name)
     |
     +-- Có -> return existing Class<?>
     |
     +-- Không -> parent/bootstrap delegation
                       |
                       +-- tìm thấy -> return parent's Class
                       |
                       +-- không -> findClass(name)
                                      |
                                      v
                                defineClass(bytes)
                                      |
                                      v
                                 Class<?>
~~~

Nếu lần trước class đã được load thành công, gọi lại cùng tên thông qua cùng loader có thể trả **cùng runtime `Class<?>`**.

~~~java
Class<?> first = loader.loadClass("plugin.Action");
Class<?> second = loader.loadClass("plugin.Action");
System.out.println(first == second);
~~~

Đây là **mảnh minh họa**, cần biến `loader` đúng và class tồn tại; theo hợp đồng class loading của trường hợp đó, kết quả là `true`.

**Không suy ra** `loadClass` lặp lại là không tốn CPU: vẫn có method call, kiểm tra và đồng bộ tùy implementation. Tuy nhiên nó **không đồng nghĩa phải đọc JAR lại hoặc define class mới**. Nếu plugin object đã có, business code nên gọi qua shared interface/object ấy thay vì `Class.forName` theo string cho mọi request mà không có lý do.

### Loader generation mới làm thay đổi ý nghĩa benchmark

~~~text
Generation A: ("plugin.Action", LoaderA)
Generation B: ("plugin.Action", LoaderB)
~~~

Hai runtime classes khác nhau ngay cả khi bytecode/binary name trùng. Nếu mỗi vòng benchmark tạo một loader mới, phép đo đang bao gồm **class loading/linking/initialization và lifecycle generation**, không chỉ invocation.

## 5. Loading, linking, initialization và gọi method khác nhau

Các lời gọi dưới đây **đo những phạm vi khác nhau**; đặt chúng cùng tên “chi phí class loading” sẽ khiến kết quả sai ý nghĩa:

~~~java
Class<?> a = loader.loadClass("plugin.Action");
Class<?> b = Class.forName("plugin.Action", false, loader);
Class<?> c = Class.forName("plugin.Action", true, loader);
~~~

- `loadClass` yêu cầu tìm/nạp class theo delegation, **không chủ động khởi tạo** class.
- `Class.forName(..., false, ...)` có thể load/link, nhưng **không yêu cầu chạy `<clinit>`**.
- `Class.forName(..., true, ...)` yêu cầu initialization nếu class chưa initialized; số đo có thể bao gồm static initializer và side effects.
- `action.execute()` trên một instance đã sẵn sàng đo business invocation, có thể chịu JIT/GC/I/O nhưng không định nghĩa lại runtime class ấy ở mỗi call.

Static initializer thành công chỉ chạy một lần cho **mỗi runtime class**. Loader generation khác tự định nghĩa cùng binary name thì có class và initialization riêng. Symbolic references còn có thể resolve khi method dùng chúng, nên `loadClass` thành công **không đảm bảo tất cả lazy dependencies đã sẵn sàng**.

Ví dụ startup có static config reader chậm nên đặt timer tách `<clinit>` và first request, thay vì quy toàn bộ delay cho JAR/verification. Nếu static initialization có network I/O khó retry, cần lifecycle startup có kiểm soát, không dùng `Class.forName` lặp lại như retry.

## 6. Ví dụ Java 17+ chạy được: initialization một lần, method gọi nhiều lần

### File `StartupVsSteadyState.java`

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public final class StartupVsSteadyState {
    private static final AtomicInteger INIT_EVENTS =
            new AtomicInteger();

    public static final class Worker {
        static {
            INIT_EVENTS.incrementAndGet();
        }

        public static long calculate(int value) {
            return 31L * value + 7;
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader =
                StartupVsSteadyState.class.getClassLoader();

        // Class literal không yêu cầu initialize Worker.
        String name = Worker.class.getName();

        long t0 = System.nanoTime();
        Class<?> type = Class.forName(name, false, loader);
        long t1 = System.nanoTime();

        System.out.println("after load(false): "
                + INIT_EVENTS.get());

        long t2 = System.nanoTime();
        Class.forName(name, true, loader);
        long t3 = System.nanoTime();

        System.out.println("after initialize: "
                + INIT_EVENTS.get());

        long checksum = 0;
        int iterations = 100_000;
        long t4 = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            checksum += Worker.calculate(i);
        }

        long t5 = System.nanoTime();

        System.out.println("after calls: "
                + INIT_EVENTS.get());
        System.out.println("same Class: "
                + (type == Worker.class));
        System.out.println("checksum positive: "
                + (checksum > 0));
        System.out.println("load_ns=" + (t1 - t0));
        System.out.println("init_ns=" + (t3 - t2));
        System.out.println("calls_ns=" + (t5 - t4));
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac StartupVsSteadyState.java
java StartupVsSteadyState
~~~

**Năm dòng đầu có kết quả logic xác định:**

~~~text
after load(false): 0
after initialize: 1
after calls: 1
same Class: true
checksum positive: true
~~~

**Ba dòng cuối có định dạng sau, nhưng số chỉ để minh họa:**

~~~text
load_ns=123456
init_ns=45678
calls_ns=789012
~~~

Không có số `ns` cố định cho mọi JDK/CPU/chạy lại.

### Giải thích

1. `Worker.class.getName()` lấy binary name nhưng không tự yêu cầu initialize Worker.
2. `Class.forName(name,false,loader)` không kích hoạt initializer; `INIT_EVENTS` vẫn 0.
3. `Class.forName(name,true,loader)` chạy static block lần đầu; counter = 1.
4. Sau 100.000 lời gọi `calculate`, static block **không** chạy lại; counter vẫn 1.
5. `type == Worker.class` là `true` vì cùng classloader identity, không tạo generation mới.
6. Các khoảng `load_ns`/`init_ns`/`calls_ns` có scope đo **khác nhau** và còn noise/JIT effects.

### Tại sao `calls_ns / 100000` không phải p99 latency?

- Chỉ có một phép đo aggregate; percentile cần distribution nhiều samples.
- JVM có thể compile/inlining method trong vòng lặp.
- Loop có thể được tối ưu hóa/giảm chi phí theo biểu thức dễ đoán.
- Clock và compiler/scheduler/GC có ảnh hưởng.
- Một phép gọi tiny arithmetic khác xa request gồm I/O, allocations và concurrency.
- Dùng `checksum` giúp kết quả được quan sát nhưng chưa bảo đảm loại bỏ mọi dead-code elimination/loop transformation.

Chương trình chứng minh **lifecycle**, không thay thế công cụ benchmark chuyên nghiệp.

## 7. Ví dụ Java 17+: hai loader generation và hai runtime classes

Một test riêng cho thấy vì sao benchmark liên tục tạo classloader mới đo một workload khác.

### File `src/plugin/demo/PluginAction.java`

~~~java
package demo;

public final class PluginAction {
    static {
        System.out.println("plugin initialized");
    }

    public String call() {
        return "ok";
    }
}
~~~

### File `src/host/LoaderGenerations.java`

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class LoaderGenerations {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: LoaderGenerations <plugin-dir>");
        }

        URL root = Path.of(args[0]).toAbsolutePath()
                .toUri().toURL();

        try (URLClassLoader loaderA = new URLClassLoader(
                    new URL[]{root},
                    ClassLoader.getPlatformClassLoader());
             URLClassLoader loaderB = new URLClassLoader(
                    new URL[]{root},
                    ClassLoader.getPlatformClassLoader())) {

            String name = "demo.PluginAction";
            Class<?> first = loaderA.loadClass(name);
            Class<?> same = loaderA.loadClass(name);
            Class<?> second = loaderB.loadClass(name);

            System.out.println("repeat in A: " + (first == same));
            System.out.println("same binary name: "
                    + first.getName().equals(second.getName()));
            System.out.println("same runtime type: "
                    + (first == second));

            Object a = first.getConstructor().newInstance();
            Object b = second.getConstructor().newInstance();

            System.out.println("A call: "
                    + first.getMethod("call").invoke(a));
            System.out.println("B call: "
                    + second.getMethod("call").invoke(b));
        }
    }
}
~~~

Các lệnh Bash tại thư mục project:

~~~bash
mkdir -p out/plugin out/host
javac -d out/plugin src/plugin/demo/PluginAction.java
javac -d out/host src/host/LoaderGenerations.java
java -cp out/host LoaderGenerations out/plugin
~~~

**Không đưa `out/plugin` vào application classpath.** Stdout khi dùng fixture trên:

~~~text
repeat in A: true
same binary name: true
same runtime type: false
plugin initialized
plugin initialized
A call: ok
B call: ok
~~~

Giải thích: `loaderA` định nghĩa class lần đầu rồi trả chính runtime `Class<?>` đó khi gọi lại; `loaderB` định nghĩa class **khác** với cùng binary name. Mỗi class được initialize khi tạo instance; do đó initializer in hai lần. Đây là chi phí **hai loader generations**, không phải của hai method calls vào cùng một instance.

## 8. Thiết kế phép đo theo đúng câu hỏi

| Câu hỏi | Mốc đo | Định nghĩa kết quả |
|---|---|---|
| JVM/app cold start? | Bên ngoài process: launch → readiness | Milliseconds, distribution qua nhiều fresh processes |
| Plugin activation? | Activation requested → plugin usable | Bao gồm loader, class, providers, resources và init bắt buộc |
| Class loading riêng? | Trước/sau explicit `loadClass` trên cold loader | Chỉ công việc của API đó; có thể lazy các bước sau |
| Static initialization? | Trước/sau first `Class.forName(...,true,loader)` | Bao gồm initializer side effects |
| Provider discovery? | Trước iterator → provider ready | Bao gồm lazy ServiceLoader work |
| Warm method invocation? | Receiver/object ổn định, method lặp với JMH | ns/op hoặc ops/s, phân tích thống kê |
| Real request p99? | HTTP/RPC end-to-end | Histogram theo concurrency và workload |
| Hot redeploy? | Stop old generation → new generation ready | Teardown, memory pressure, loader activation |

Không đặt `new URLClassLoader` bên trong measurement loop nếu mục tiêu chỉ là call một object đã tồn tại. Ngược lại, nếu ứng dụng thật phải tạo loader mỗi request, hãy **đưa chi phí đó vào benchmark** và đặt tên đúng.

## 9. Đo cold startup từ bên ngoài JVM

Để đo **process startup** phải bao gồm phần trước `main`, nên cần external harness hoặc instrumentation phù hợp:

~~~text
Harness:
  start monotonic timer
  launch java process
  wait for readiness marker / successful endpoint
  record elapsed time
  terminate process safely
  repeat in fresh process
~~~

Một lệnh shell `time java MyMain` đo khoảng process chạy đến kết thúc (phù hợp CLI nhỏ), **không tự là** thời gian server trở nên ready nếu server vẫn chạy mãi. Với HTTP server, cần readiness signal tường minh và timer bên ngoài.

Nên cố định:

- Cùng JDK/build/VM flags.
- Cùng artifact và module/classpath/JAR graph.
- Cùng CPU/quota, memory, OS và container limits.
- Cùng required/optional plugins.
- Cùng conditions của network/DNS/DB và secrets.
- Phân biệt warm/cold OS page cache.
- Nhiều fresh processes, không dùng duy nhất lần đầu có nhiều nhiễu.
- Báo median, p95/p99 nếu số samples đủ; không chọn fastest run.

Nếu variant A báo ready trước khi provider quan trọng initialized, còn variant B đợi provider usable, hai phép đo **không cùng định nghĩa** và không nên so như nhau.

## 10. JMH dành cho steady-state method benchmark

**Java Microbenchmark Harness (JMH)** hỗ trợ benchmark JVM với forks, warm-up, measurement iterations, state, result consumption và báo cáo thống kê.

Mảnh JMH **cần dependency JMH và class riêng**, không phải JDK thuần:

~~~java
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class InvokeBenchmark {
    private final BusinessService service = new BusinessService();

    @Benchmark
    public long invoke() {
        return service.compute(42);
    }
}
~~~

`BusinessService` là type minh họa của ứng dụng; cần tự cung cấp implementation và cấu hình project JMH. Mấu chốt là **service được chuẩn bị ngoài method benchmark**, không tạo loader/plugin object mới cho mỗi invocation.

Quy trình JMH phù hợp:

1. Chạy warm-up iterations trước khi lấy số.
2. Fork process khi cần để tránh class/JIT state bị benchmark khác ảnh hưởng.
3. Chuẩn bị receiver/provider theo `@State` hoặc setup lifecycle phù hợp.
4. Consume kết quả để giảm rủi ro dead-code elimination.
5. Kiểm soát benchmark params/dataset để compiler không chỉ đo một constant-folded expression.
6. Chạy nhiều iterations/samples; ghi confidence/error bounds.
7. Phân biệt throughput và average time; microbenchmark không tự cho p99 request production.
8. Không so operation A gồm reflection lookup với B chỉ gồm direct invocation rồi nói “invocation chậm X” nếu scope khác nhau.

**JMH không thay thế load testing:** network, thread pools, GC, DB, queues và p99 real requests vẫn cần đo bằng công cụ thích hợp với production workloads.

## 11. Reflection, MethodHandle và direct interface calls

Plugin frameworks đôi khi tìm method bằng reflection:

~~~java
java.lang.reflect.Method method =
        type.getMethod("execute");
Object result = method.invoke(instance);
~~~

Đây là mảnh minh họa khi `type`/`instance` đã có. Có hai phần khác nhau:

- **Lookup:** `getMethod(...)`, access validation.
- **Invocation:** `method.invoke(...)`.

Nếu trong một variant đặt `getMethod` mỗi iteration, variant khác gọi trực tiếp `sharedInterface.execute()`, ta đang đo cả lookup cộng invoke so với chỉ invoke. Để so công bằng, cache `Method`/`MethodHandle` phù hợp trong setup, hoặc đo lookup riêng.

Một architecture có shared API ở parent thường cho phép host giữ một provider instance:

~~~java
MyService service = factory.create();
service.execute();
~~~

Đoạn trên chỉ minh họa mô hình; trong dự án thật cần khai báo `MyService`/`factory`. Caller sau đó không cần tìm class lại bằng binary name cho mọi request.

Hiệu năng dynamic dispatch phụ thuộc JIT, inline caches, polymorphism, interface implementations và workload, **không có một hằng số overhead classloader áp dụng mỗi lời gọi**.

## 12. JIT warm-up khác static class initialization

**Initialization** chạy `<clinit>` một lần trên runtime class nếu thành công. Còn **JIT** hoạt động khi JVM đánh giá các hot execution paths và có thể compile/recompile sau nhiều invocations.

~~~text
Class loaded & initialized
          |
Call 1: cold code/data caches
          |
Call N: profiling, tiered compilation
          |
Call later: optimized/inlined code possible
          |
Deoptimization/recompile possible if profile changes
~~~

Sơ đồ không khẳng định phải có đúng N calls hoặc mọi JVM mode luôn JIT compile. Trong `-Xint`, execution model khác với default HotSpot tiered compilation.

**Hệ quả:** chạy `Class.forName(name,true,loader)` sớm có thể giúp tránh static init ở request đầu, nhưng **không đảm bảo JIT và business data caches đã warm**. Nếu SLA yêu cầu first request nhanh, cần warm-up những critical request paths một cách có kiểm soát.

## 13. ServiceLoader và resource I/O là cold work riêng

### ServiceLoader lazy discovery

~~~java
ServiceLoader<MyService> services =
        ServiceLoader.load(MyService.class, pluginLoader);
~~~

Đây là mảnh minh họa, yêu cầu `MyService`/`pluginLoader` tồn tại.

`ServiceLoader.load` tạo một ServiceLoader, trong khi nhiều bước tìm metadata/provider và instantiate diễn ra **khi iterate/stream**. Vì vậy, đo `ServiceLoader.load(...)` mà không duyệt providers có thể **không đo activation đầy đủ**.

Đặt mốc:

~~~text
create ServiceLoader
   |
iterate descriptors / module provider metadata
   |
load implementation class
   |
check runtime SPI assignability
   |
instantiate provider
   |
invoke provider startup method
~~~

Thêm hoặc xóa descriptor trong `META-INF/services`, TCCL/explicit loader hay named module setup có thể thay đổi work thực tế. Benchmark cần cùng artifact/provider behavior, không chỉ một API call giống tên.

### Resource lookup khác đọc/parsing

~~~java
URL location = pluginLoader.getResource("config/settings.properties");
~~~

`getResource` chỉ tìm URL (hoặc `null`); nó chưa tương đương đọc toàn bộ bytes, parse và validation. Nếu code tiếp tục `openStream()` rồi parse Properties, đó là phase I/O/CPU khác, có thể cache hoặc bị page cache ảnh hưởng.

Đừng dùng resource URL “trông đúng” để kết luận bản data của plugin đã được dùng; parent-first có thể che một file cùng tên ở child.

## 14. Quan sát class loading bằng ClassLoadingMXBean

JDK 17 có `ClassLoadingMXBean`:

~~~java
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

ClassLoadingMXBean bean =
        ManagementFactory.getClassLoadingMXBean();

long total = bean.getTotalLoadedClassCount();
int current = bean.getLoadedClassCount();
long unloaded = bean.getUnloadedClassCount();
~~~

Đây là mảnh Java minh họa.

- `total` là **counter tích lũy** số class loaded kể từ lúc JVM khởi động.
- `current` là **gauge** số class hiện loaded.
- `unloaded` là **counter tích lũy** class đã unloaded.

Chúng giúp biết startup hoặc activation đã làm JVM load thêm class hay chưa, nhưng **không đo latency của từng class**. `total` tăng không tự chứng minh classloader leak hay method call chậm; cần xem trend, metaspace, loading logs/JFR và retained classloader references.

Khi tính delta, lấy samples trong **cùng một JVM**; mỗi process restart reset counters. Ghi thêm JVM process start time và timestamp để không tính delta âm giữa hai JVM thành “unloaded”.

## 15. JVM logs, JFR và tracing

Trên HotSpot JDK 17, bật class load/unload logs:

~~~bash
java -Xlog:class+load=info,class+unload=info \
    StartupVsSteadyState
~~~

Lệnh chẩn đoán tạo thêm log, **không** còn stdout gọn như demo; tùy JVM có thể khác tag/format. Classloading logs cho biết event/sources nhưng không tự đưa ra p95/p99 business latency.

**Java Flight Recorder (JFR)** có thể giúp xem execution samples, allocation, GC, class loading và những events khác theo cấu hình JDK. Khi phân tích cold start:

- Bật recording sớm đủ để bao gồm startup/cold feature.
- Ghi timeline của plugin loader creation, class defined, first provider ready và request first response.
- So JIT/GC/I/O/locks cùng classloading activity.
- Không mặc định mọi JFR event đều bật, hoặc có đầy đủ stacktrace/duration.
- So sánh nhiều runs và bảo đảm JDK/flags giống nhau.

Application spans cũng hữu ích:

~~~text
process-started
required-services-ready
plugin-loader-created
plugin-class-found
plugin-initialized
provider-ready
first-successful-result
warm-request-completed
~~~

Để tính elapsed durations trong process dùng monotonic clock, kết hợp wall clock khi cần correlate distributed traces. Không cộng hai spans chạy song song rồi coi là total.

## 16. Class unloading và metaspace trong lifecycle benchmark

Một hệ thống hot redeploy liên tục có:

~~~text
Generation A -> Loader A -> Class A
Generation B -> Loader B -> Class B
Generation C -> Loader C -> Class C
~~~

Mỗi generation có runtime class objects riêng; nếu host cache `Class<?>`, plugin objects, TCCL, threads, callbacks hoặc JNI globals từ generation cũ, memory/class count có thể tăng.

`URLClassLoader.close()` đóng JAR/file handles của URL loader, **không bắt JVM unload class ngay**. `System.gc()` cũng không bảo đảm class unloading xảy ra lập tức.

Nếu workload thực sự là tạo/retire loaders liên tục, phải đo thêm:

- Load/activation time cho mỗi generation.
- Total/current/unloaded classes theo khoảng.
- Metaspace usage, heap và GC pauses.
- Thời gian đóng streams/JAR handles.
- Strong-reference retention và classloader leaks.
- Tail latency của request đang chạy khi redeploy.

Không dùng benchmark một loader trên fresh JVM để khẳng định app redeploy 100 lần sẽ không tăng bộ nhớ.

## 17. Chiến lược eager, lazy và warm-up theo SLA

| Chiến lược | Lợi ích | Đánh đổi |
|---|---|---|
| Eager loading/init trước readiness | Lỗi lộ sớm, first request nhanh hơn nếu đủ chuẩn bị | Startup lâu, footprint lớn |
| Lazy init khi feature được gọi | Ready nhanh, không nạp feature ít dùng | Cold request chậm, lỗi xuất hiện muộn |
| Targeted warm-up | Tập trung vào critical paths | Phải chọn đúng paths và đo real effect |
| Reuse loader/provider | Giảm repeated activation work | Cần lifecycle/cache consistency |
| New loader per generation | Hỗ trợ plugin version isolation | Chi phí load/GC/metaspace cao hơn |
| Separate process cho plugin | Cô lập mạnh hơn | IPC/process startup overhead |

Một thiết kế có thể đặt readiness:

~~~text
BOOTING -> REQUIRED_PLUGINS_READY ->
WARM_CRITICAL_PATHS -> SERVING
~~~

Optional feature có thể lazy, nhưng phải ghi rõ first-use SLA và fallback policy. Nếu chỉ chuyển một công việc nặng từ startup sang request đầu mà không đo p99, **chưa đủ bằng chứng** cải thiện tổng thể.

## 18. Bẫy benchmark thường gặp

| Ngộ nhận | Vì sao sai | Cách thay thế |
|---|---|---|
| First invocation chậm nên tất cả Java method calls chậm | Lẫn loading/init/JIT | Tách cold và warm |
| `Class.forName(..., true)` làm JIT đã warm | Initialization khác JIT | Warm đúng code paths |
| `loadClass` đọc JAR ở mọi call | Thường trả class đã loaded | Kiểm tra `findLoadedClass` và timing |
| Tạo new loader mỗi iteration là invoke benchmark | Đang đo generation | Reuse loader/receiver |
| Mean từ một loop là p99 | Mean khác distribution percentile | Histogram, nhiều samples |
| Một lần `nanoTime` là benchmark chuẩn | Noise/JIT/GC/scheduler | JMH + repeat/forks |
| Timing từ `main` là process startup đầy đủ | Bỏ JVM bootstrap | External harness |
| `ServiceLoader.load` đã đo mọi provider cost | Discovery lazy | Iterate/instantiate |
| `getResource` đã đọc config | Chỉ lookup URL | Đo open/read/parse |
| Reflection lookup+invoke so với direct invoke là công bằng | Extra work ở case reflection | Tách lookup khỏi invocation |
| Class total loaded tăng là leak chắc chắn | Counter tích lũy vốn tăng | Current/metaspace/retention trend |
| GC chưa unload ngay là leak | Không deterministic | Quan sát lifecycle/reference graph |
| “Chạy nhanh” nhưng provider fail vẫn đạt SLA | Correctness không bảo đảm | Xác nhận successful feature behavior |

## 19. Checklist trước khi kết luận

- [ ] Định nghĩa rõ startup/activation/first use/steady-state metric.
- [ ] Đo startup **từ ngoài process** nếu mục tiêu gồm JVM bootstrap.
- [ ] Dùng cùng JDK, flags, hardware và artifact khi so sánh.
- [ ] Cùng classpath/module path và loader policy.
- [ ] Dùng fresh JVM cho cold-start samples.
- [ ] Không tạo ClassLoader mỗi vòng của benchmark method đã loaded.
- [ ] Phân biệt `loadClass` với `Class.forName(..., true)`.
- [ ] Xác nhận static initializer chỉ chạy theo runtime class identity.
- [ ] Đo ServiceLoader provider iteration, không chỉ tạo object loader.
- [ ] Đo resource lookup riêng với đọc/parsing.
- [ ] Có warm-up và multiple forks khi đo tiny methods bằng JMH.
- [ ] Consume results, chống dead-code elimination.
- [ ] Báo distribution/tail latency cho request thật.
- [ ] Theo dõi JIT/GC/class count/metaspace khi cần.
- [ ] Verify startup/readiness đã thực sự phục vụ đúng tính năng.
- [ ] Kiểm soát TCCL/caches/streams và loader lifecycle khi redeploy.
- [ ] Không lấy một cấu hình benchmark để khẳng định mọi JVM.

## 20. Bài tập tự kiểm tra

**Câu 1.** `ClassLoader.loadClass` gọi lại cùng tên trên cùng loader có phải luôn định nghĩa class mới không?

**Đáp án:** Không. ClassLoader thông thường trả lại runtime class đã loaded; không gọi `defineClass` lần hai cho cùng binary name trong một loader.

**Câu 2.** `Class.forName(name, false, loader)` khác `true` ở khía cạnh nào?

**Đáp án:** `false` không yêu cầu initialize, `true` yêu cầu initialize class chưa initialized; chi phí static initializer có thể xuất hiện ở lần đầu.

**Câu 3.** Đo thời gian từ đầu `main` tới khi sẵn sàng có phải đo đủ JVM process startup không?

**Đáp án:** Không, đã bỏ thời gian JVM launcher/bootstrap trước `main`.

**Câu 4.** Vì sao hai URLClassLoader định nghĩa cùng `plugin.Action` có thể làm initializer chạy hai lần?

**Đáp án:** Vì đó là hai runtime classes khác defining loaders; mỗi class có lifecycle initialization riêng.

**Câu 5.** JMH đo 100.000 method invocations đã warm có phản ánh p99 HTTP request không?

**Đáp án:** Không. Real request còn network, queues, concurrency, allocations, I/O và GC.

**Câu 6.** `ServiceLoader.load(SPI.class)` hoàn thành nhanh có chứng minh provider instantiated không?

**Đáp án:** Không, provider discovery/instantiation có thể lazy khi iterate.

**Câu 7.** Nếu chuyển config read từ startup sang request đầu, startup có thể nhanh hơn. Có chắc tổng trải nghiệm tốt hơn không?

**Đáp án:** Không; first-use latency có thể tăng. Cần đo mốc readiness và p99 first requests.

**Câu 8.** Vì sao `getTotalLoadedClassCount()` không phải byte-size metaspace?

**Đáp án:** Đây là số lượng runtime classes từng loaded trong JVM, không phải memory usage; footprint/class không cố định.

**Câu 9.** Khi benchmark classloader hot redeploy, có cần xét GC/memory không?

**Đáp án:** Có. Những generations khác nhau tạo runtime types mới; retention/GC/metaspace có thể ảnh hưởng latency và throughput.

**Câu 10.** Nếu một method cực nhỏ chạy nhanh trong vòng loop sau warm-up, có chắc dynamic dispatch trên production cũng nhanh như vậy không?

**Đáp án:** Không. JIT có thể inline/constant fold, còn production nhiều implementations, concurrency và workload khác.

## 21. Cây quyết định

~~~text
Phát hiện Java/plugin chậm
              |
              v
Mốc nào?
 /             |              \
Startup     First use      Warm calls
   |            |               |
external    load/init,     JMH / real
harness     providers,     request traces
   |        config I/O          |
   |            |               |
   +------------+---------------+
                |
       Xác định bottleneck thật
                |
    Eager/lazy/warm-up/reuse loaders
                |
  Đo lại same JVM/artifact/workload
                |
      Correctness + tail latency
                |
      Memory/GC/lifecycle kiểm tra
~~~

**Kết luận:** **Startup**, **first use**, **warm-up** và **steady-state invocation** là những phase khác nhau. `ClassLoader` tạo runtime type identities, không chạy lại toàn bộ pipeline load/define/init mỗi khi một method đã nạp được gọi. Muốn tối ưu hiệu năng chính xác, hãy phân tách các mốc đo, tính đến lazy linking/provider/resource work, JIT và GC, chọn đúng công cụ (external startup harness, JMH, JFR, ClassLoadingMXBean, load testing), rồi xác nhận kết quả trên workload và artifact thật.

### Nguồn tham khảo

- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — Class.forName](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [JVMS 17 — Loading, Linking and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html)
- [Java SE 17 — ClassLoadingMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ClassLoadingMXBean.html)
- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html)
- [OpenJDK — Java Microbenchmark Harness](https://openjdk.org/projects/code-tools/jmh/)
- [Java 17 launcher / Unified JVM Logging](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-performance-003">Nạp lười và tác động của preloading</a>

<details>
<summary>Xem chi tiết</summary>

# Nạp lười và tác động của preloading đến hiệu năng Java ClassLoader

## 1. Tổng quan: trả chi phí ở startup hay ở lần dùng đầu tiên?

Một ứng dụng Java thường không cần thực sự chạy tất cả chức năng ngay khi khởi động. Một phần class chỉ được sử dụng khi có request cụ thể, người dùng kích hoạt tính năng hoặc hệ thống phát hiện plugin. Vì vậy runtime có thể **trì hoãn việc nạp, liên kết hoặc khởi tạo** một số class cho tới thời điểm cần thiết. Ta thường gọi lối vận hành này là **nạp lười (lazy loading)** theo nghĩa kiến trúc.

Ngược lại, **preloading** là một chiến lược **chủ động thực hiện sớm một phần công việc** với class, resource hoặc framework components để hạn chế độ trễ khi tính năng lần đầu được truy cập.

~~~text
LAZY                                      EAGER / PRELOADING
Process start                             Process start
   |                                          |
   | nạp/khởi tạo các lớp cần thiết             | chủ động nạp/khởi tạo thêm các lớp
   v                                          | dựng cache, chuẩn bị framework...
Server ready                                  v
   |                                      Server ready
   | first request                            |
   | nạp/link/init thêm class?                 | first request
   v                                          | ít việc còn phải làm hơn?
Response                                     v
                                          Response
~~~

Sự đánh đổi thường thấy:

- **Nạp lười:** có thể rút ngắn thời gian đến khi ứng dụng sẵn sàng và giảm số class thực sự phải xử lý nếu một tính năng hiếm khi dùng; nhưng request đầu tiên tới tính năng ấy có thể trả thêm chi phí.
- **Preloading:** có thể dịch chi phí vào startup hoặc giai đoạn warm-up, giúp giảm độ trễ ở lần sử dụng đầu tiên; đổi lại startup lâu hơn, có thể tăng bộ nhớ/resident metadata và gây thêm I/O/CPU hoặc tác dụng phụ của static initialization.

**Không có quy tắc “preload luôn nhanh hơn”**. Hiệu quả phụ thuộc tập class, cách nạp, JIT, class data sharing, lưu trữ, concurrency, hot paths, chính sách triển khai và metric được tối ưu (startup, p99 latency, RAM, throughput hay thời gian cập nhật).

Điểm quan trọng nhất của bài: **loading, linking, initialization và “warming” là các công việc khác nhau.** Một lệnh chủ động “load class” không mặc nhiên thực hiện static initializer, không khởi tạo Spring bean và không buộc JIT biên dịch tất cả methods.

## 2. Ba giai đoạn thường bị gọi chung là “nạp lớp”

Để chọn đúng mức preload, tách ba mốc JVM (JLS/JVMS): **loading** tìm bytes và tạo runtime `Class<?>`; **linking** gồm verification, preparation và resolution (resolution có thể diễn ra muộn); **initialization** chạy static field expressions và `<clinit>` khi active use yêu cầu.

| Mục tiêu preload | Có thể đã xong | Vẫn có thể phát sinh ở first request |
| --- | --- | --- |
| `loadClass(name)` | Tra class/define khi cần, một phần linking | `<clinit>`, lazy resolution, tạo instance, I/O/JIT |
| `Class.forName(name, false, loader)` | Nạp/link class theo API nhưng chưa yêu cầu init | Static initializer, provider/discovery và business setup |
| `Class.forName(name, true, loader)` | Thêm yêu cầu initialization | Constructor, DI graph, resource access khác, first-use/JIT |

### 2.1. “Lazy” không phải một bảo đảm thời điểm tuyệt đối cho loading

JVM và framework có thể chủ động nạp/link một số classes trước code path mà ứng dụng tưởng là lần dùng đầu. **API `false` chỉ bảo đảm không chủ động initialize class đích**, không bảo đảm class chưa từng được nạp trước đó; cũng không ép toàn bộ symbolic references resolve. Phép so sánh lazy/preload phải dựa trên class-load/init evidence và workload thật, không dựa vào suy đoán thời điểm.

## 3. Các API Java 17+ và điều thực sự xảy ra

| Cách dùng | Loading/linking | Initialization của class đích |
| --- | --- | --- |
| `loader.loadClass("demo.Feature")` | Yêu cầu loader tìm/nạp class; parent delegation áp dụng | **Không tự yêu cầu** initialization |
| `Class.forName("demo.Feature", false, loader)` | Tìm/nạp/liên kết theo API và quy tắc VM | **Không yêu cầu** initialization |
| `Class.forName("demo.Feature", true, loader)` | Tìm/nạp/liên kết và yêu cầu initialization | **Có**, nếu cần |
| `Class.forName("demo.Feature")` | Dùng defining loader của caller theo API tương ứng; yêu cầu initialization | **Có**, nếu cần |
| `Feature.class` | Lấy Class literal, có thể kích hoạt công việc class-loading cần thiết | **Không tự làm** Feature initialized |
| `new Feature()` | Cần class được chuẩn bị | **Có**, trước khi tạo instance theo quy tắc |
| `Feature.runStatic()` | Thực hiện static method invocation | **Có**, nếu chưa initialized |
| `Feature.nonConstantStatic` | Truy cập static field không phải constant variable | **Có**, nếu chưa initialized |
| `Feature.COMPILE_TIME_CONSTANT` | Constant variable thường được inline vào caller | **Có thể không** làm declaring class initialized |

**Lưu ý:** `ClassLoader.loadClass(String, boolean resolve)` là protected overload; `resolve=true` nghĩa yêu cầu **linking thông qua resolveClass**, **không phải initialization**. Không tự equate “resolve class” với “chạy static initializer”.

Lệnh `Class.forName(name, false, loader)` thích hợp khi cần lấy `Class<?>` để xem metadata, giữ một handle hoặc kiểm tra class có thể tải, nhưng **không nên gọi đó là full application warm-up**.

## 4. Ví dụ chạy được: ba chế độ lazy, load và preload

Thí nghiệm dưới đây cố định thứ tự các thông báo để minh họa **thời điểm static initialization**. Nó không cố đo benchmark thời gian bằng số milliseconds giả định, vì thời gian thực tế thay đổi theo máy và JDK.

### 4.1. File LoadingDemo.java

~~~java
public class LoadingDemo {
    static final class Feature {
        static {
            System.out.println("Feature initialized");
        }

        static void serve() {
            System.out.println("Feature.serve");
        }
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "lazy" : args[0];
        ClassLoader loader = LoadingDemo.class.getClassLoader();
        String name = "LoadingDemo$Feature";

        switch (mode) {
            case "lazy" -> {
                // Không chủ động nạp hay khởi tạo Feature.
            }
            case "load" -> {
                loader.loadClass(name);
                System.out.println("Class loaded without initialization");
            }
            case "lookup" -> {
                Class.forName(name, false, loader);
                System.out.println("Class resolved without initialization");
            }
            case "preload" -> {
                Class.forName(name, true, loader);
                System.out.println("Preload completed");
            }
            default -> throw new IllegalArgumentException(mode);
        }

        System.out.println("Application ready");
        System.out.println("First request");
        Feature.serve();
        System.out.println("Request completed");
    }
}
~~~

Dùng **Java 17+**, trong một thư mục mới chứa `LoadingDemo.java`:

~~~bash
javac --release 17 -d out LoadingDemo.java
java -cp out LoadingDemo lazy
java -cp out LoadingDemo load
java -cp out LoadingDemo lookup
java -cp out LoadingDemo preload
~~~

**Chạy từng lệnh trong một JVM process mới**, bởi initialization của cùng một Class runtime xảy ra tối đa một lần trong vòng đời tương ứng. Nếu gọi lần thứ hai trong cùng JVM, sự khác biệt có thể biến mất vì class đã initialized.

### 4.2. Kết quả chính xác: lazy

~~~text
Application ready
First request
Feature initialized
Feature.serve
Request completed
~~~

Tại thời điểm in `Application ready`, code chưa cố chủ động khởi tạo `Feature`. Sau dòng `First request`, lời gọi static method `Feature.serve()` cần đảm bảo class initialized, nên thông báo của static initializer xuất hiện trước khi chạy body của `serve`.

### 4.3. Kết quả chính xác: loadClass

~~~text
Class loaded without initialization
Application ready
First request
Feature initialized
Feature.serve
Request completed
~~~

`loadClass` đã trả về Class trước khi ứng dụng ready, **nhưng static initializer vẫn chỉ chạy khi method được sử dụng**. Lúc này một phần công việc loading có thể đã được chuyển tới startup, nhưng chi phí `<clinit>` chưa được chuyển.

### 4.4. Kết quả chính xác: Class.forName với initialize=false

~~~text
Class resolved without initialization
Application ready
First request
Feature initialized
Feature.serve
Request completed
~~~

Chương trình in dòng `Class resolved without initialization` làm **nhãn mô tả thao tác lookup của demo**; đừng suy rằng mọi symbol trong bytecode đã được eagerly resolved. Điều chắc chắn là API không yêu cầu initialization, thể hiện qua thứ tự stdout.

### 4.5. Kết quả chính xác: preload có initialization=true

~~~text
Feature initialized
Preload completed
Application ready
First request
Feature.serve
Request completed
~~~

Lần này `Class.forName(name, true, loader)` buộc `Feature` được initialized trước `Application ready`. Request đầu không còn in thông báo khởi tạo vì static initializer chỉ chạy một lần trong namespace của loader.

**Điều ví dụ chứng minh:** preloading có thể **di chuyển thời điểm initialization**. Nó **không chứng minh** rằng p99 latency trong hệ thống thật nhất định giảm, startup nhất định tăng một con số cụ thể, hoặc mọi thành phần của framework đã được warm.

## 5. Định nghĩa chính xác “preloading” theo mức độ

Trong kiến trúc, từ preloading đôi khi được dùng cho những việc rất khác nhau:

| Cấp độ | Việc thực hiện | Công việc có thể còn dồn vào request đầu |
| --- | --- | --- |
| **Name lookup / load** | `loader.loadClass(name)` | Initialization, reflection cache, method first-use |
| **Load without init** | `Class.forName(name, false, loader)` | `<clinit>` và logic phụ thuộc |
| **Initialize** | `Class.forName(name, true, loader)` | Constructor, dependency initialization theo đường khác, request-specific logic |
| **Instantiate** | `Constructor.newInstance` hoặc `new` để tạo object | Lazy fields, caches, network connections, business paths |
| **Framework warm-up** | Dựng DI graph, route mapping, serializers, proxies | Paths chưa thực thi, class resolution khác, JIT compilation |
| **Application exercise** | Chạy một request an toàn đại diện | Dữ liệu khác, locks/cache misses, workload-dependent logic |
| **JIT warm-up** | Chạy hot methods đủ để JIT tối ưu theo profile | Deoptimization, profile shift, các paths hiếm |
| **CDS/AppCDS** | Chia sẻ class metadata được archive khi khởi động thích hợp | Static initialization và request-specific caches thường vẫn còn |

Đừng viết trong tài liệu vận hành rằng “đã preload xong” khi thực tế hệ thống chỉ gọi `loadClass` rồi bỏ qua startup error từ static initializer hoặc chưa kích hoạt đường code mà khách hàng sắp dùng.

### 5.1. Nạp trước không đồng nghĩa tạo bean ngay

Trong Spring hoặc framework DI khác, việc class được JVM load không đồng nghĩa **bean object đã được tạo, dependency graph hoàn chỉnh hay lifecycle hooks đã chạy**. Nạp lớp và initialization của một class cũng không đồng nghĩa với application-level initialization của framework component.

Nếu mục tiêu là tránh chậm request đầu cho DI container, cần đo và warm **đúng component path**, không chỉ gọi `Class.forName` trên các bean classes.

### 5.2. Nạp trước không đồng nghĩa JIT đã biên dịch

`Class.forName` không gọi toàn bộ methods của class và không đảm bảo JVM JIT compiler đã tối ưu chúng. JIT optimization chủ yếu phản ứng với actual execution profiles/hotness. Một “preload” thuần metadata không thay thế warm-up workload.

## 6. Tác động tới startup latency

Ở ứng dụng CLI, serverless, job chạy ngắn hoặc desktop launch, **thời gian tới hành động hữu ích đầu tiên** có thể quan trọng nhất.

Chi phí startup liên quan class loading có thể bao gồm:

- Đọc JAR entries hoặc class files từ storage.
- Tìm class trên classpath/module path qua parent delegation, URLClassLoader hoặc custom lookup.
- Verification, preparation và một phần resolution.
- Static initialization của class được sử dụng sớm.
- Framework classpath scanning/reflection index generation/proxy generation.
- Đọc cấu hình, dựng dependency graph và caches.
- I/O và page-cache warm-up ở filesystem/JAR processing.

Preload toàn bộ plugin classes ở startup có thể làm service sẵn sàng chậm, nhất là nếu chỉ một plugin được gọi ở phần nhỏ requests. Ngược lại, môi trường có health/readiness traffic ngay sau start có thể chấp nhận warm-up có kiểm soát để tránh latency spike của request đầu.

**Cần đo startup theo một định nghĩa cụ thể:**

- **Process-start to bound port?**
- **Process-start to readiness true?**
- **Readiness true to first successful request?**
- **First request p50/p95/p99 latency?**
- **Time for a batch job to finish?**

Chuyển chi phí từ trước readiness sang sau readiness có thể làm “startup metric” đẹp hơn nhưng **trải nghiệm người dùng đầu tiên** xấu hơn. Chuyển ngược lại có thể làm readiness chậm hơn trong khi request ngay sau readiness ổn định hơn. Không có lựa chọn đúng nếu chưa xác định SLA/SLO.

## 7. Tác động tới độ trễ request đầu tiên và tail latency

Lazy loading đặc biệt rõ khi một endpoint hoặc feature **chưa từng được gọi** trong process hiện tại:

~~~text
Request A đến khi class chưa initialized:
  routing
  -> discovery / class loading / linking (nếu cần)
  -> static initialization
  -> tạo dependency/proxy/cache (nếu thiết kế có)
  -> xử lý logic
  -> response

Request B đến khi class đã initialized:
  routing
  -> xử lý logic (có thể vẫn gặp warm-up khác)
  -> response
~~~

Một single-use cold path có thể tạo spike ở **p99** dù phần lớn requests nhanh. Nhưng:

- Không phải mọi lần request đầu đều phải load class; framework có thể đã load nó khi scan.
- Load và initialization có thể được trigger bởi một thread khác trước request A.
- Request đầu có thể chậm do DNS/TLS, connection pool, disk cache, JIT hoặc external service, không do class loading.
- Một preload có thể đẩy chi phí sang startup nhưng cũng làm nhiều services cùng preload đồng thời, tranh CPU và I/O, gây throughput giảm tạm thời.
- Metric chỉ đo average latency có thể che spike rõ ở requests lạnh.

Để phân tích, ghi **thời gian và thông tin event** quanh class initialization và request path, rồi đối chiếu trace/JFR thay vì chỉ đo bằng `System.currentTimeMillis` và đoán nguyên nhân.

## 8. Tác động tới memory: loaded classes không phải miễn phí

Khi một class được định nghĩa, JVM thường phải giữ metadata trong **Metaspace**, dữ liệu về constant pool, method metadata và những cấu trúc khác. Thao tác reflection/JIT/initialization cũng có thể tạo thêm objects/code caches/caches tùy ứng dụng.

Preloading nhiều classes có thể làm:

1. Tăng số **loaded classes** trong tiến trình.
2. Tăng Metaspace usage và một phần native memory.
3. Tạo static caches, singletons hoặc thread/resources khi khởi tạo.
4. Giảm cơ hội **không bao giờ dùng** những classes của features hiếm.
5. Tăng startup I/O và heap activity, có thể kéo theo GC sớm.

Nhưng không khẳng định một lớp **đã load** chắc chắn vĩnh viễn chiếm toàn bộ bộ nhớ trong cả đời process. **Class unloading** có thể xảy ra khi defining class loader và các associated classes không còn reachable theo các điều kiện JVM; các references từ static caches, threads, context class loaders, ServiceLoader/proxy caches có thể làm plugin loaders còn sống lâu hơn dự kiến.

Với classloader riêng cho nhiều plugin, preloading tất cả implementation classes vào nhiều loaders có thể tạo **nhiều bản metadata và static state** thay vì dùng một shared parent loader. Khi tối ưu startup và memory, xem cả **type identity và loader lifecycle**.

## 9. Parent delegation, ClassLoader locks và preloading song song

Một custom `ClassLoader` thường theo mô hình:

~~~text
loadClass(binaryName)
    -> findLoadedClass
    -> parent delegation
    -> findClass / defineClass
    -> resolveClass (khi yêu cầu)
~~~

Mỗi lần tự đọc class bytes từ file/mạng có thể tốn I/O. Một loader có cache và reuse `Class<?>` phù hợp sẽ tránh định nghĩa lặp.

Khi nhiều threads cùng muốn nạp một class, class loader phải đồng bộ thích hợp; `ClassLoader.loadClass` dùng class loading lock theo chính sách của loader. Với **parallel-capable class loaders**, `getClassLoadingLock(name)` có thể cấp lock theo class name để tăng concurrency; custom loaders cần đăng ký parallel-capable đúng cách nếu thiết kế hỗ trợ.

### 9.1. Chạy preload song song không phải lúc nào cũng nhanh

- Nhiều workers đọc cùng storage/JAR có thể gây I/O contention.
- Verification/initialization có thể tranh CPU ở startup.
- JVM có **class initialization lock**, nên nhiều threads cùng active-use một class có thể đợi thread đang thực hiện `<clinit>`.
- Custom loader với đồng bộ toàn loader có thể tuần tự hóa các yêu cầu mặc dù preloading được phân luồng.
- Circular initialization hoặc side effects của `<clinit>` có thể gây deadlock/hang, nhất là khi static initializer tự tạo threads và đợi chúng hoàn tất.

**Ưu tiên dùng một kế hoạch warm-up có giới hạn concurrency, phân nhóm theo hot paths và đo thực tế**, thay vì tung hàng trăm `Class.forName(name,true,loader)` cùng lúc.

## 10. Static initialization có tác dụng phụ

Đặt network/database/file I/O nặng trong `static { }` hoặc static field initializers khiến cả lazy loading lẫn preload khó dự đoán.

Ví dụ minh họa:

~~~java
final class RemoteConfig {
    static final String VALUE = fetchConfiguration();

    static String fetchConfiguration() {
        // Giả sử thực hiện I/O và có thể ném lỗi, timeout.
        return "configured";
    }
}
~~~

**Đây là snippet minh họa**, không thực sự gọi network. Khi class được initialized, `fetchConfiguration()` chạy. Preload theo `Class.forName(..., true, ...)` sẽ chạy logic đó **trước readiness** nếu target chưa initialized.

Tác động:

- **Lỗi initialization** có thể ngăn startup, thay vì chỉ làm feature hiếm gặp lỗi ở lần dùng đầu.
- **Timeout/external dependency** có thể biến readiness thành phụ thuộc vào một dịch vụ không cần thiết cho mọi chức năng.
- **CPU/memory spikes** có thể xảy ra khi nhiều static caches cùng được tạo.
- **Order dependencies** không rõ: initializer chạy sớm khi environment/config chưa sẵn sàng.

Vì vậy nên cân nhắc giữ `<clinit>` đơn giản, chuyển I/O nặng sang initialization có lifecycle rõ, có timeout, reporting và khả năng retry phù hợp. Preload static initializers để “hâm nóng” ứng dụng chỉ khi đã hiểu chính xác những side effects.

## 11. Lỗi class initialization và retry

Nếu một static initializer thất bại với exception thường, lần đầu khởi tạo có thể báo `ExceptionInInitializerError`; lần sử dụng sau của class ấy có thể báo `NoClassDefFoundError` rằng class không thể initialized. Điều này **không được giải quyết bằng cách gọi Class.forName nhiều lần trên cùng class loader**.

Ví dụ:

~~~java
public final class Broken {
    static {
        if (System.getProperty("config.ready") == null) {
            throw new IllegalStateException("missing config");
        }
    }

    public static void run() { }
}
~~~

Đây là **đoạn minh họa**; Java compiler có thể yêu cầu static initializer hoàn thành bình thường theo flow rules, còn nhánh if có điều kiện runtime như trên cho phép class compile. Nếu code chủ động preload `Broken` khi chưa cấu hình hệ thống, nó có thể **đẩy lỗi vào startup** và khiến class ở trạng thái erroneous trong defining loader.

**Nạp sớm để fail fast** có thể là một tính năng tốt nếu ứng dụng buộc cần class ấy để phục vụ bất kỳ request nào. Nhưng với optional plugin, fail fast toàn bộ process có thể làm độ sẵn sàng tệ hơn: nên cách ly lỗi và chỉ đánh dấu plugin unavailable khi phù hợp.

Muốn nạp class phiên bản mới sau khi initialization thất bại, trong một plugin architecture có thể cần **loader mới** với class definition mới và quản lý lifecycle; không thể “reset” trạng thái initialization của cùng Class tùy ý.

## 12. Một ví dụ thứ hai: preloading API mà không kích hoạt static side effects

Trong môi trường plugin, đôi khi chỉ cần xác nhận binary class name tồn tại mà **không chạy static initializer**.

~~~java
public final class PluginCatalog {
    public static Class<?> find(String name, ClassLoader loader)
            throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }
}
~~~

Đây là **class có thể biên dịch nhưng không có main**. Nó cung cấp lookup Class mà không yêu cầu initialization. Tuy nhiên:

- Chỉ việc tìm thấy Class chưa chứng minh plugin có dependencies tương thích tại mọi call site.
- Classfile có thể được link/resolved theo JVM semantics khác nhau.
- Một static initializer có thể thất bại khi plugin thực sự được sử dụng.
- Không thể coi đây là probe hoàn chỉnh để đảm bảo ServiceLoader provider hay DI bean đã sẵn sàng.

Nếu muốn rà soát API trước readiness, có thể dùng `Class.getDeclaredMethods()` hoặc `isAssignableFrom` theo nhu cầu, nhưng các thao tác reflection cũng có thể yêu cầu nạp những kiểu được tham chiếu và gây errors riêng. Đừng suy luận rằng introspection tất cả classes là zero-cost hoặc không có tương tác module/access.

## 13. ServiceLoader: khám phá provider cũng thường lazy

`ServiceLoader.load(Service.class, loader)` trả về một ServiceLoader; việc tìm metadata, tải class provider hoặc tạo instance thường diễn ra **lười khi iterate**.

Ví dụ:

~~~java
ServiceLoader<MyService> services =
        ServiceLoader.load(MyService.class, pluginLoader);

for (MyService provider : services) {
    provider.run();
}
~~~

Đây là **snippet minh họa**, giả sử `MyService` và `pluginLoader` đã tồn tại. Chỉ tạo `ServiceLoader` ở startup mà không duyệt providers không bảo đảm provider classes đã được nạp/khởi tạo.

Để warm provider thực sự, cần **duyệt và khởi tạo** các providers chủ đích (và có thể gọi các paths an toàn phù hợp). Tuy nhiên:

- Plugin constructor có thể thực hiện I/O và đăng ký resources.
- Nếu plugin bytecode không tương thích service interface runtime, iteration có thể ném `ServiceConfigurationError`.
- Nếu provider optional, preloading tất cả provider có thể làm startup chịu lỗi của plugin không dùng.
- `reload()` làm mới cache ServiceLoader, **không thay Class đã được defining loader nạp/khởi tạo**.

Đặt service API ở common parent loader nếu host và provider cần chia sẻ cùng runtime type, để tránh preloading làm lộ các lỗi “provider not a subtype” trong lúc startup.

## 14. Reflection, Dynamic Proxy và framework startup

Reflection frameworks có thể tạo nhiều chi phí “đầu tiên”:

- Tìm annotations trên classes.
- Lấy `Method`, `Field`, constructors và kiểm tra access.
- Sinh dynamic proxy classes.
- Dựng object graph và injection metadata.
- Phân tích route/controller mappings.
- Cấu hình serializers/deserializers.
- Scan classpath/JAR entries.

**Chủ động nạp class bằng Class.forName(false) không tự thực hiện tất cả các công việc này.**

Nếu framework cung cấp chế độ eager initialization, lazy bean creation hoặc startup hooks, hãy hiểu **đơn vị được preload là gì**. Chẳng hạn một framework có thể load class để đọc annotation nhưng trì hoãn tạo bean; request đầu vẫn phải chạy constructor và proxy generation.

Trong hệ thống có nhiều ClassLoader hoặc ModuleLayer, proxy class được tạo phải nhìn thấy **đúng interface Class runtime** từ loader được chỉ định. Tăng preload bừa bãi không sửa được parent delegation/API duplication hay module opens/exports issues.

## 15. JIT warm-up khác class preloading

**Class loading và JIT compilation là hai hiện tượng riêng.**

Một JVM có thể interpreter-run một method trước, thu execution profiles rồi biên dịch bằng JIT khi method trở nên hot. Preloading một Class mà không gọi methods của nó không tạo đủ profile để làm mọi method hot.

Sơ đồ:

~~~text
Class bytes --> loading/linking --> initialization
                       |
                       +--> first method invocation
                                 |
                                 +--> interpreter / tiered compilation
                                 +--> hotness / profile / optimization
                                 +--> potential deoptimization and recompilation
~~~

Nếu mục tiêu giảm latency request đầu do JIT, hãy thiết kế **warm-up workload** đại diện với data đủ giống production, nhưng kiểm soát side effects:

- Gọi test endpoint nội bộ khi ứng dụng ở trạng thái warming nhưng chưa nhận traffic.
- Không tạo giao dịch thật hay gửi email thật chỉ để “warm”.
- Không mặc định JIT state và profile của test request sẽ bao phủ production distributions.
- Đo lại performance sau warm-up, không dùng số vòng lặp cố định áp dụng cho mọi JDK và CPU.
- Với serverless/short-lived CLI, prewarming dài có thể tốn hơn lợi ích của nó.

CDS và AppCDS có thể giúp giảm một phần chi phí loading/memory bằng archive được JVM hỗ trợ. **Chúng không phải lệnh “chạy mọi static initializer”** và không loại bỏ JIT/runtime warm-up của ứng dụng.

## 16. Measuring: phân biệt cold start, warm start và cold path

Một kế hoạch benchmark đáng tin nên xác định:

| Metric | Câu hỏi | Cách đo gợi ý |
| --- | --- | --- |
| Time to process ready | JVM có thể nhận traffic hợp lệ sau bao lâu? | Đo process start đến readiness event |
| Cold first request | Request đầu sau launch chậm bao nhiêu? | Đo end-to-end client-side latency |
| Warm request latency | Sau khi đã dùng feature, latency ra sao? | Nhiều iterations, phân phối latency |
| p95/p99 | Có long-tail spikes lúc class init không? | Request traces + histograms |
| Classes loaded | Bao nhiêu Class được nạp, từ đâu? | JVM Unified Logging / JFR |
| Metaspace | Preload thêm metadata bao nhiêu? | JFR, MemoryPoolMXBean, native memory tracking |
| Heap allocations/GC | Init caches có gây pressure không? | JFR, GC logging, heap metrics |
| CPU/I/O during startup | Preload chiếm CPU và disk bao nhiêu? | Process/host-level metrics |
| Throughput under load | Preloading có làm xử lý tổng thể tốt hơn? | Load test sau readiness |

### 16.1. Chạy benchmark mỗi phương án trong JVM process mới

Nếu chạy lazy rồi preload tuần tự trong **cùng JVM với cùng defining loader**, lần thứ hai có thể không còn class initialization cost: kết quả thiên lệch vì class đã initialized. Muốn đo cold effects, tạo **process mới** cho từng trial hoặc có một loader namespace thực sự mới khi phù hợp với kiến trúc.

Nên:

1. Build artifact cố định, cùng JDK/version, cùng hardware/CPU quota và configuration.
2. Chạy nhiều vòng độc lập cho **lazy**, **preload-load-only**, **preload-initialize**.
3. Tách cold OS page-cache và warm OS page-cache nếu nó ảnh hưởng.
4. Ghi startup/readiness, first-use latency và p99; không chỉ average.
5. Đo memory (Metaspace, heap, code cache) và GC, cùng số classloaded.
6. Báo p50/p95/p99, confidence range hoặc phân phối; không công bố một con số đại diện cho mọi máy.
7. Chạy A/B ở cùng traffic/load; ghi rõ preload có sử dụng CPU cores cạnh tranh với workloads không.

Với microbenchmarks về method execution, ưu tiên JMH và tránh dùng một vòng lặp `nanoTime` đơn giản để kết luận JIT optimization toàn diện. Với first-request latency, **end-to-end process-level measurements** thường hữu ích hơn.

### 16.2. Quan sát class loading với JVM Unified Logging

Trên OpenJDK/HotSpot Java 17+:

~~~bash
java -Xlog:class+load=info -cp out LoadingDemo lazy
~~~

Lệnh này chạy ví dụ ở mục 4 nếu đã compile. Nó sẽ in **rất nhiều log từ JVM**, tùy phiên bản và classpath; không có transcript cố định. So `class+load` events với log `Feature initialized` để thấy **đã load** không đồng nghĩa **đã initialize**.

Unified logging bản thân cũng có overhead, do đó chỉ bật ở giai đoạn chẩn đoán hoặc benchmark có cấu hình nhất quán giữa các biến thể.

### 16.3. Dùng Java Flight Recorder

Có thể bắt đầu JFR khi launch:

~~~bash
java -XX:StartFlightRecording=filename=loading.jfr,settings=profile \
     -cp out LoadingDemo preload
~~~

Đây là lệnh **Unix shell** minh họa. Trên PowerShell/CMD, viết cùng trên một dòng:

~~~powershell
java -XX:StartFlightRecording=filename=loading.jfr,settings=profile -cp out LoadingDemo preload
~~~

Tập events thu được phụ thuộc cấu hình JFR và JVM; nếu cần **class loading/initialization events chi tiết**, bật những events khả dụng trong runtime mà bạn đang dùng. Không suy rằng profile settings mặc định chứa mọi class event hoặc tự đo được tất cả static initialization cost ở cấp method.

JFR giúp quan sát timeline, allocation và các events hoạt động JVM, nhưng **không thay request latency measurements** từ phía client.

## 17. Preloading theo thứ tự, ưu tiên và “readiness budget”

Thay vì preload **mọi class**, ta có thể phân loại:

**Nhóm A — bắt buộc trước khi sẵn sàng:** shared service API, những modules/beans luôn dùng bởi mọi request, config bắt buộc, failure nên fail fast.

**Nhóm B — hot paths quan trọng:** authentication/authorization, routing/serialization của endpoint thường gặp. Có thể warm trước readiness hoặc trong giai đoạn canary/warm-up ngắn sau startup.

**Nhóm C — optional/rare paths:** exporters ít dùng, report builders, admin-only endpoints, plugin tùy chọn. Thường phù hợp lazy initialization nếu first-use latency vẫn đáp ứng SLO.

**Nhóm D — dynamic plugins/reloadable:** load theo demand với quản lý loader lifecycle, cache bounded và isolation. Không tự động eager-init plugin từ nguồn chưa được phép.

Một kế hoạch mẫu:

~~~text
Process start
  |
  +-- initialize mandatory core (fail-fast)
  |
  +-- build routing table / dependency metadata
  |
  +-- optional: warm critical endpoints safely
  |
  +-- readiness ON
  |
  +-- background/low-priority warm optional warmable paths
  |      (chỉ nếu có orchestration, quotas, cancellation)
  |
  +-- normal user traffic
~~~

Đây là **sơ đồ thiết kế**, không phải chức năng tự có của `ClassLoader`. Nếu production chạy nhiều replicas, orchestrator cần biết lúc nào instance nhận traffic; khi hệ thống không có warm-up hooks và budgets rõ ràng, “preload nền” có thể tranh CPU với request và làm tail latency tệ hơn.

## 18. Preloading không phải tự động an toàn

Khi chủ động chạy `Class.forName(name, true, loader)` trên các class names lấy từ manifest:

- Một số class có static initializer gửi requests hoặc đọc credentials.
- Một số plugin chỉ được phép chạy khi tenant/feature flag cụ thể được bật.
- Một initializer có thể tạo threads, register shutdown hooks hoặc giữ loader references.
- A plugin may be untrusted code even if bytecode verified.
- Eager initialization làm những lỗi optional có thể xuất hiện trước readiness.
- Eager scanning trên network storage có thể gây timeout/retry storms.

Do đó, **class names phải thuộc allowlist đáng tin**, source artifact phải được approve, và preload policy cần dựa trên những class **được phép khởi tạo**; không dùng một danh sách path tùy ý từ user input rồi tự tạo static side effects.

**Security note:** verifier và custom ClassLoader không phải sandbox quyền I/O cho code plugin. Khi code từ bên không tin cậy phải bị hạn chế, dùng isolation tại process/container/VM và các OS permissions phù hợp.

## 19. Những lỗi thường gặp khi đánh giá lazy loading

### Lỗi 1: Đồng nhất loadClass với initialize

`loadClass` không tự yêu cầu static initialization. Muốn chuyển công việc `<clinit>` sớm hơn, phải chọn API/cách sử dụng có thể yêu cầu initialization, ví dụ `Class.forName(..., true, loader)` — nhưng phải cân nhắc side effects.

### Lỗi 2: “Class.forName(false) đã warm toàn bộ feature”

Không. Nó không yêu cầu static initialization, không tạo object theo DI lifecycle và không thực thi hot methods để warm JIT.

### Lỗi 3: Preload càng nhiều càng tốt

Sai. Nhiều class hiếm có thể tăng startup, metadata/memory và I/O mà không cải thiện SLO cho traffic thực.

### Lỗi 4: Đánh giá trong cùng JVM đã warm

Class đã initialized không chạy `<clinit>` lần nữa chỉ vì gọi `Class.forName`. Test cold cần process/layer/loader thực sự mới theo bài toán, không reuse một runtime warmed-up.

### Lỗi 5: Chỉ so startup time

Mô hình preload có thể chậm startup nhưng giảm latency request đầu; mô hình lazy ngược lại. Cần đo cả readiness và first-use/p99.

### Lỗi 6: Giả định mọi delay đầu tiên do class loading

Class initialization, first connection, JIT, disk cache, TLS, DNS, data cache, reflection/proxy generation cũng có thể tạo delay. Dùng traces và JFR để phân biệt.

### Lỗi 7: Quên static initializer side effects

Preload có thể kích hoạt I/O, throw exception hoặc chờ locks ngay khi deployment. Khởi tạo có chủ đích phải có observability và error handling phù hợp.

### Lỗi 8: Bỏ qua class loader identity

Hai loaders tự định nghĩa cùng binary name sẽ tạo hai Class runtime riêng. Warm class bằng loader A không bảo đảm plugin loader B đã được warm; preload sai loader có thể làm tăng overhead.

### Lỗi 9: Bỏ qua memory overhead của metadata

Class đã loaded có metadata và có thể giữ static state. Preload mọi plugin tăng pressure lên Metaspace/caches, và giữ references có thể chặn unloading.

### Lỗi 10: Bật preload nhiều luồng không giới hạn

Có thể tranh CPU/IO, làm initializer lock contention và tăng latency. Hạn chế concurrency, đo rate và tách critical/non-critical work.

### Lỗi 11: Tưởng CDS/AppCDS thay thế workload warm-up

CDS hỗ trợ archive metadata/class data theo VM; không đảm bảo static initializers được chạy hoặc methods đã JIT optimized cho traffic.

### Lỗi 12: Dùng ServiceLoader.load nhưng không iterate

ServiceLoader dùng lazy discovery/instantiation. Chỉ tạo ServiceLoader không đảm bảo providers đã được tạo hoặc validated toàn diện.

## 20. Gợi ý thiết kế API warm-up có kiểm soát

Nếu cần warm service có interface chung, ưu tiên **warm tại tầng use case** thay vì reflection vô hạn:

~~~java
public interface Warmable {
    void warmUp();
}
~~~

Service có thể implement contract và warm một đường code **an toàn, idempotent, có timeout/quota**. Orchestrator gọi nó theo danh sách các components đã approve.

Những lưu ý:

- `warmUp()` có thể chạy nhiều lần hoặc bị retry; cần biết có an toàn không.
- Nếu hệ thống dùng DI, hãy gọi sau khi config và dependencies sẵn sàng.
- Tránh ghi dữ liệu sản xuất, gửi external notifications hoặc tạo giao dịch thật khi chỉ muốn warm.
- Expose readiness **sau** warm-up bắt buộc; warm-up optional có thể chạy ở trạng thái giảm ưu tiên và có cancellation.
- Ghi metric riêng cho warm time, failure và CPU/memory cost.
- Với plugins, expose shared interface `Warmable` từ common parent loader, không cho child-first định nghĩa một bản copy.

Đây là **thiết kế minh họa**, không phải một API Java SE tên “preload all applications”. Mọi chiến lược cần tích hợp vào lifecycle của chính ứng dụng.

## 21. Bài tập tự kiểm tra

**1. Loading khác initialization ở điểm nào?** Loading tạo runtime Class từ bytes, initialization thực thi `<clinit>` theo quy tắc active use.

**2. `loader.loadClass(name)` có tự chạy static initializer không?** Không yêu cầu. Đó là khác biệt quan trọng so với `Class.forName(name, true, loader)`.

**3. `Class.forName(name,false,loader)` giúp gì?** Lấy/tìm Class mà không yêu cầu initialization; không tự warm DI/JIT.

**4. Tại sao `Feature.class` chưa chắc in thông báo static initializer?** Class literal không tự làm class initialized.

**5. Gọi static method lần đầu có thể làm gì?** Active use kích hoạt initialization của declaring class nếu cần trước khi chạy method.

**6. Preloading có thể cải thiện metric nào?** First-use latency và một số tail spikes, nếu thực sự chuyển công việc cần thiết khỏi request path.

**7. Preloading có thể làm xấu metric nào?** Startup/readiness latency, I/O/CPU contention và memory footprint.

**8. Vì sao benchmark phải dùng JVM process mới?** Nếu dùng lại Class đã initialized, kết quả cold latency không còn hợp lệ.

**9. Vì sao JIT warm-up không tương đương class loading?** JIT cần execution profiles/hotness; load Class không chạy hết methods.

**10. Vì sao preloading static initializer có thể làm startup fail?** Nó có thể ném exception, chờ I/O, tạo threads hoặc phụ thuộc config chưa sẵn sàng.

**11. `ServiceLoader.load` có bảo đảm provider class đã khởi tạo không?** Không; khám phá/tạo provider thường lazy khi iterate.

**12. Vì sao RAM có thể tăng sau preload?** Nhiều Class metadata trong Metaspace, static caches và có thể thêm heaps/code caches.

**13. Class loader mới load cùng binary name có tận dụng initialization của class loader cũ không?** Không mặc nhiên; đó có thể là hai runtime Class khác nhau.

**14. Có nên preload tất cả optional plugins khi process start không?** Không mặc định. Phân nhóm theo SLO, tải có chọn lọc, đo startup/first-use và bảo vệ nguồn code.

**15. Preload có thể thay thế readiness/liveness probe không?** Không. Readiness phải phản ánh hệ thống có thể phục vụ hợp lệ; preloading chỉ là một chiến lược lifecycle.

## 22. Tài liệu tham khảo

- **JLS Java 17 `12.2 — Loading of Classes and Interfaces:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2
- **JLS Java 17 `12.3 — Linking of Classes and Interfaces:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.3
- **JLS Java 17 `12.4 — Initialization of Classes and Interfaces:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4
- **JVMS Java 17 `5 — Loading, Linking and Initializing:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- **Java SE 17 — ClassLoader:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 — Class.forName:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java SE 17 — ServiceLoader:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- **Java SE 17 — JFR APIs:** https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/module-summary.html
- **Java SE 17 — java launcher (Unified Logging, JFR, CDS options):** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-performance-004">Tái sử dụng loader so với tạo loader cho từng yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Tái sử dụng loader so với tạo loader cho từng yêu cầu

## 1. Bài toán: vòng đời ClassLoader nên gắn với cái gì?

Trong ứng dụng Java có plugin, rule engine, application server hay hệ thống nạp mã từ JAR, một class có thể phục vụ hàng nghìn yêu cầu. Có hai cách tổ chức:

- **Reuse loader:** tạo một `ClassLoader` cho môi trường code của một plugin/version, dùng lại nó cho nhiều request; mỗi request có thể tự tạo object mới.
- **Loader-per-request:** mỗi request tạo một `ClassLoader` riêng, nạp class qua nó, xử lý yêu cầu rồi đóng hoặc loại bỏ loader.

Sự khác biệt không chỉ là chi phí của toán tử `new`. Nó ảnh hưởng đến **class identity, bộ nhớ metaspace, verification/linking/initialization, static state, resource/JAR handles, tốc độ warm-up, JIT, khả năng reload và các lỗi cast**.

~~~text
REUSE THEO PLUGIN VERSION

Plugin v1 --> Loader L1 --> demo.Worker (Class A)
                         |         |         |
                      Request 1  Request 2  Request 3
                      object #1  object #2  object #3


LOADER THEO TỪNG REQUEST

Request 1 --> Loader L1 --> demo.Worker (Class A)
Request 2 --> Loader L2 --> demo.Worker (Class B)
Request 3 --> Loader L3 --> demo.Worker (Class C)

Nếu mỗi child tự define Worker:
Class A != Class B != Class C
~~~

**Khuyến nghị mặc định:** tái sử dụng class loader **theo phiên plugin/version hoặc môi trường nạp mã**, không tạo loader mới cho từng HTTP/job request nếu các request dùng chung code và classpath. Điều này không đồng nghĩa phải chia sẻ một service instance có trạng thái mutable; **vòng đời loader và vòng đời object là hai quyết định độc lập**.

Cũng không phải mọi trường hợp đều nên dùng một loader duy nhất cho cả JVM. Nếu các versions, tenants hay modules thực sự cần code khác nhau, ranh giới loader phải phản ánh ranh giới đó.

## 2. ClassLoader không phải object thông thường

Trong JVM, runtime class identity gồm **binary name + defining class loader**. Vì vậy tạo một loader mới *có thể* tạo một bản `demo.Worker` mới với static state, initialization và metadata riêng, ngay cả khi bytes giống hệt.

~~~text
Loader A defines demo.Worker -> Class A
Loader B defines demo.Worker -> Class B
Class A != Class B
~~~

**Điều kiện thiết yếu:** hai loader mới không nhất thiết tạo hai classes. Nếu parent-first tìm thấy `demo.Worker` ở parent, cả hai child có thể trả cùng runtime class ấy. Khi so reuse với loader-per-request, phải kiểm tra `type.getClassLoader()` và `typeA == typeB`, thay vì chỉ đếm `new URLClassLoader`.

Với class do child tự định nghĩa, mỗi generation có static fields và initialization riêng; `Class<?>`, reflection metadata hay instance của generation cũ còn bị giữ có thể ngăn GC/unloading. Do đó quyết định reuse ảnh hưởng cả CPU, identity và vòng đời tài nguyên.

## 3. Tái sử dụng loader có thể tiết kiệm chi phí nào?

Trong lần đầu nạp code riêng, một loader có thể phải đi qua delegation/lookup, đọc class bytes từ directory/JAR, định nghĩa class và thực hiện verification, linking, initialization tại những thời điểm JVM yêu cầu. Khi dùng lại cùng runtime class, không phải định nghĩa lại nó mỗi request.

| Nhóm chi phí | Reuse loader | Tạo loader per request |
| --- | --- | --- |
| Tạo và cấu hình loader | Một lần cho phiên code | Lặp lại mỗi request |
| Tìm class/JAR entries | Có thể tận dụng class đã loaded/lookup metadata | Có thể tìm lại theo loader mới |
| Define, verify, link | Bản runtime class đã tồn tại | Có thể lặp nếu child mới tự define |
| Static initialization | Một lần trên mỗi runtime class | Có thể lặp trên mỗi bản class mới |
| Metadata/metaspace | Ít bản class riêng hơn khi đúng lifecycle | Có thể tạo nhiều bản class trong thời gian ngắn |
| JIT profiling/code cache | Có cơ hội warm up và tái sử dụng methods cùng class | Nhiều bản class/method riêng có thể tăng áp lực |
| File/JAR handles | Giữ và đóng theo plugin lifecycle | Phải mở/đóng thường xuyên và đúng owner |
| Cleanup | Đóng khi retire version | Đóng trên mọi request, khó kiểm soát khi bất đồng bộ |
| Type identity | Ổn định giữa request cùng version | Có thể khác theo request |

Không được hiểu bảng này là tốc độ nhanh hơn được bảo đảm theo một tỷ lệ nào đó. Nếu request tốn phần lớn thời gian vì network/database, hoặc class thực tế đều do parent loader trả về, khác biệt có thể nhỏ. Nếu request luôn nạp hàng trăm implementation classes qua JAR riêng, chi phí lặp lại có thể đáng kể.

Một mô hình minh họa, không phải phép đo:

~~~text
N = số request trong một plugin version
S = chi phí khởi tạo loader + load/link cần thiết
W = công việc nghiệp vụ cho một request
L = lookup/cache overhead khi dùng lại lớp
R = chi phí cleanup một loader

Reuse:           T ≈ S + N × (W + L) + R
Loader/request:  T ≈ N × (S + W + R)
~~~

Đây là biểu thức đơn giản hóa; thực tế có caching của OS/JVM, initialization lazy, compilation và GC. Nó cho thấy **setup có thể được amortize** khi reuse loader, không phải một khẳng định định lượng.

### Reuse Class không có nghĩa reuse instance

~~~text
Loader L
    |
    +--> Class<demo.Worker>
             |
             +--> instance per request #1
             +--> instance per request #2
             +--> instance per request #3
~~~

Bạn có thể load `Class<?>` hoặc tạo factory một lần, nhưng mỗi request vẫn dùng object riêng. Đây thường là lựa chọn tốt hơn việc tạo một runtime class mới chỉ để tách dữ liệu request.

## 4. Cache và findLoadedClass

Thuật toán mặc định của `ClassLoader.loadClass(String, boolean)` kiểm tra `findLoadedClass(name)` trước, sau đó delegate tới parent rồi mới dùng `findClass` của child khi cần.

~~~java
Class<?> first = loader.loadClass("demo.Worker");
Class<?> again = loader.loadClass("demo.Worker");
System.out.println(first == again);
~~~

Đây là đoạn minh họa bên trong method có biến `loader`. Với class loading hợp lệ, hai lần lookup cùng binary name qua cùng initiating loader trả về cùng `Class<?>`, không define class mới ở lần thứ hai.

**Đừng nhầm cache này với một Map do ứng dụng viết.** `findLoadedClass` và JVM quản lý identity của runtime classes. Một `Map<String, Class<?>>` tự tạo vẫn có thể giữ strong references tới loader sau reload và gây retention. Nếu cache theo class, cần có **lifecycle clear** hoặc mô hình weak references thích hợp; không giả định GC tự thu dọn map static của host.

`ClassLoader.loadClass` còn có khóa nạp lớp để tránh race khi cùng tên được yêu cầu đồng thời. Dùng một loader chung không có nghĩa một request có thể define cùng class tùy tiện nhiều lần; JVM và loader phối hợp quản lý class loading. Với custom loader, phải tuân thủ `getClassLoadingLock(name)` và parallel-capable rules khi override.

## 5. Ví dụ Java 17+ tái hiện sự khác nhau

Ví dụ biên dịch hai file riêng biệt:

~~~text
example/
├── src/
│   ├── plugin/
│   │   └── demo/Worker.java
│   └── host/
│       └── LoaderReuseDemo.java
└── out/
    ├── plugin/       (output bytecode plugin)
    └── host/         (output bytecode host)
~~~

**Chỉ out/host được đưa vào application classpath** khi chạy. Out/plugin được nạp bằng một URLClassLoader riêng; nếu lỡ thêm plugin vào host classpath, parent có thể định nghĩa Worker trước và làm thay đổi điều kiện thí nghiệm.

### File src/plugin/demo/Worker.java

~~~java
package demo;

public final class Worker {
    private static int constructions;

    public Worker() {
        constructions++;
    }

    public String work() {
        return "ok";
    }

    public static int constructions() {
        return constructions;
    }
}
~~~

Bộ đếm static chỉ để thấy state theo class loader. `constructions++` **không thread-safe**; ví dụ chạy một thread để đầu ra xác định. Trong chương trình concurrent, cần dùng cơ chế đồng bộ/atomic phù hợp, không lấy đoạn này làm mã production.

### File src/host/LoaderReuseDemo.java

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class LoaderReuseDemo {
    public static void main(String[] args) throws Exception {
        URL url = Path.of(args[0]).toAbsolutePath().toUri().toURL();
        ClassLoader parent = LoaderReuseDemo.class.getClassLoader();

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{url}, parent)) {
            Class<?> type = loader.loadClass("demo.Worker");
            Object first = type.getDeclaredConstructor().newInstance();
            type.getDeclaredConstructor().newInstance();

            System.out.println("reuse same class: "
                    + (type == loader.loadClass("demo.Worker")));
            System.out.println("reuse work: "
                    + type.getMethod("work").invoke(first));
            System.out.println("reuse constructions: "
                    + type.getMethod("constructions").invoke(null));
        }

        Class<?> previous = null;
        boolean distinct = false;
        for (int request = 1; request <= 2; request++) {
            try (URLClassLoader loader = new URLClassLoader(
                    new URL[]{url}, parent)) {
                Class<?> type = loader.loadClass("demo.Worker");
                type.getDeclaredConstructor().newInstance();

                if (previous != null) {
                    distinct = previous != type;
                }
                System.out.println("fresh " + request + " constructions: "
                        + type.getMethod("constructions").invoke(null));
                previous = type;
            }
        }
        System.out.println("fresh types differ: " + distinct);
    }
}
~~~

**Kịch bản reuse:** duy nhất một loader nạp Worker. Hai instances làm static count tăng thành 2; lookup lặp về cùng `Class<?>`.

**Kịch bản per-request:** mỗi request tạo và đóng một loader. Mỗi loader định nghĩa riêng Worker nên bộ đếm bắt đầu lại; hai bản Worker khác Class identity.

Biến `previous` cố ý giữ tham chiếu tới một Class cũ để so sánh. Vì vậy ví dụ **không** chứng minh class unloading đã xảy ra ngay khi đóng loader.

### Compile và chạy Linux/macOS

Đứng trong thư mục example:

~~~bash
mkdir -p out/plugin out/host

javac --release 17 -d out/plugin \
  src/plugin/demo/Worker.java

javac --release 17 -d out/host \
  src/host/LoaderReuseDemo.java

java -cp out/host LoaderReuseDemo out/plugin
~~~

### Compile và chạy Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force out\plugin, out\host | Out-Null
javac --release 17 -d out\plugin src\plugin\demo\Worker.java
javac --release 17 -d out\host src\host\LoaderReuseDemo.java
java -cp out\host LoaderReuseDemo out\plugin
~~~

### Stdout chính xác đã chạy thử

~~~text
reuse same class: true
reuse work: ok
reuse constructions: 2
fresh 1 constructions: 1
fresh 2 constructions: 1
fresh types differ: true
~~~

Ví dụ biên dịch bằng `javac --release 17` và đã chạy thực tế trên JDK; kết quả khớp sáu dòng trên.

### Những gì thí nghiệm chứng minh

1. Tạo hai Worker objects trong **cùng loader** không tạo hai bản runtime Worker Class.
2. Static counter được chia sẻ giữa những objects của một runtime class.
3. Loader mới tự define Worker tạo static state mới cho mỗi bản class.
4. Hai `Class<?>` từ các defining loaders khác nhau có identity khác nhau.
5. `URLClassLoader.close()` được gọi bằng try-with-resources khi từng phiên kết thúc.

**Không kết luận** từ ví dụ rằng per-request chậm hơn bao nhiêu phần trăm, đã phát sinh bao nhiêu bytes metaspace hay class unload ngay tức thì. Để đo performance cần benchmark có warmup, concurrency và theo dõi JVM memory phù hợp.

## 6. Vì sao tạo loader mới mỗi request có thể gây hậu quả ngoài CPU?

### Class identity và lỗi cast

Nếu host giữ một `Class<?>` Worker của loader A, rồi cố dùng object Worker từ loader B như thể cùng concrete type, JVM có thể ném `ClassCastException` dù binary name giống nhau.

~~~text
Loader A defines demo.Worker -> Worker A
Loader B defines demo.Worker -> Worker B

WorkerA.class != WorkerB.class
~~~

Để trao đổi giữa các plugin/request loaders, cần một **shared API interface** do loader cha định nghĩa, ví dụ `api.Task`. Hai bản Worker riêng có thể cùng triển khai **một interface host** nếu chúng delegate đúng interface về parent.

Nếu chính interface cũng bị đóng gói và define trong từng child, tên `api.Task` giống nhau chưa đủ, cast sang API của host vẫn có thể thất bại. Đây là lý do per-request isolation đòi hỏi thiết kế kỹ type boundary, không chỉ performance benchmark.

### Static initialization và state

Khi một loader khác tự define Worker, static initialization áp dụng cho **class mới**. Việc static cache/counter/reset không phải reset một instance trước đó; nó là **một bản class runtime khác**.

Tạo loader mới chỉ để tránh state bị rò giữa các requests thường là biện pháp quá nặng. Nên ưu tiên thiết kế request-scoped data hoặc instance lifecycle rõ ràng. Nếu service có static mutable state thật sự phải reset, hãy thiết kế cache/session ownership chính xác; không dùng class loader như object pool.

### Metaspace và class unloading

Mỗi bản class definition có metadata do JVM quản lý. Nếu có nhiều loaders với cùng hàng trăm classes đang sống, peak metaspace và class counts có thể tăng. Sau khi close loader, class unloading **không diễn ra tức thì**: class/loader phải thỏa điều kiện reachability và JVM thực hiện GC/unloading thích hợp.

Nếu host giữ một `Class<?>`, Method, Constructor, instance, thread context class loader (TCCL), ThreadLocal, listener hoặc map reference từ loader cũ, nó có thể vẫn reachable. Loader-per-request mà không quản lý reference sẽ tăng churn, thậm chí gây memory leak.

### JIT/warm-up

JVM có thể thu thập profiling và biên dịch methods của các runtime classes. Tạo nhiều định nghĩa Worker riêng liên tục có thể làm những lợi ích warm-up của bản class đang sống khó phát huy, đồng thời tăng áp lực metadata/code cache. Nhưng tác động **phụ thuộc JVM, workload và tối ưu hóa runtime**; không đưa ra con số speedup nếu chưa đo.

## 7. Benchmark công bằng: cần đo những gì?

Để quyết định trên dữ liệu thực tế, nên phân biệt ít nhất:

| Metric | Ý nghĩa |
| --- | --- |
| Cold-start latency | Thời gian từ tạo loader đến xử lý xong request đầu |
| Steady-state p50/p95/p99 | Latency sau warmup trong tải tương đương |
| Throughput | Số requests mỗi giây tại concurrency định sẵn |
| Allocations | Object allocations và garbage collection pressure |
| Class counts/metaspace | Các runtime class definitions và memory metadata |
| CPU/JIT/code cache | Phần thời gian JVM xử lý class loading, optimization |
| JAR/file handles | Số resource handles còn mở trong vòng đời |
| Reload duration | Thời gian chuyển version và retire loader cũ |

### Kịch bản thử nghiệm hợp lý

1. Cố định JDK, JVM options, machine, plugin JAR, business logic và các dependencies.
2. Scenario A: **một loader cho cùng plugin version**, khởi tạo trước rồi xử lý N requests bằng object riêng.
3. Scenario B: **một loader cho mỗi request**, cùng số requests và cùng business logic.
4. Đo **cold startup riêng**; warm up trước khi đo steady-state.
5. Với latency/throughput cần nhiều vòng đo, nhiều JVM forks nếu thích hợp; không suy từ một lần chạy.
6. Giữ concurrency, input và classpath giống nhau; đảm bảo server logging/background tasks không làm sai lệch.
7. **Kiểm tra defining loader** để chắc scenario B thực sự define class riêng, chứ không chỉ đều trả Class của parent.
8. Đo memory/metaspace cùng thời gian, không chỉ wall-clock latency.
9. Báo p95/p99, throughput, allocations và chính xác workload; không gán một hằng số overhead chung cho mọi Java application.

Có thể dùng **JMH** cho microbenchmark JVM vì nó hỗ trợ warmup, forks, measurement và các biện pháp tránh lỗi đo thường gặp. Nhưng ngay cả JMH cũng cần benchmark mô phỏng đúng lifecycle (setup + load + instance + work + close), không chỉ đo constructor URLClassLoader.

### Vì sao System.nanoTime một lần dễ gây sai?

~~~java
long start = System.nanoTime();
// Một thao tác bạn muốn đo.
long nanos = System.nanoTime() - start;
~~~

Đây chỉ là đoạn minh họa. Thời gian có thể bị ảnh hưởng bởi JIT warmup, class initialization, OS file cache, GC, thread scheduling hoặc CPU frequency. Nếu benchmark không làm kết quả tính toán quan sát được, JVM cũng có thể tối ưu bỏ một số phép tính.

**Đo công việc tương đương:** đối với per-request, phải tính cả load/link/instance/close. Đối với reuse, phải tính setup vào cold-start và chia chi phí trên số request thích hợp khi đánh giá tổng chi phí. Không so lượt chạy cold của A với steady-state của B rồi rút kết luận.

### JVM diagnostics

Với JDK hỗ trợ unified logging, có thể chạy:

~~~bash
java -Xlog:class+load=info -cp out/host LoaderReuseDemo out/plugin
~~~

Log có thể nhiều dòng và thay đổi theo môi trường, không nên chép thành stdout mẫu. Có thể xem thêm JFR/Java Mission Control, heap dumps và JVM metrics, class unloading logs khi phân tích memory/loader retention. **Đừng coi class unloading logs đồng nghĩa JAR handles đã được đóng**: đây là hai tài nguyên khác nhau.

## 8. Concurrency: reuse loader không đồng nghĩa reuse mutable object

Một ClassLoader được nhiều threads dùng để nạp class vẫn phải tuân thủ đồng bộ/locking của `loadClass`. Nhưng các objects và static state mà nó định nghĩa **không tự động thread-safe**.

Trong ví dụ:

~~~java
private static int constructions;

public Worker() {
    constructions++;
}
~~~

Nếu nhiều threads cùng tạo Worker, phép `++` không atomic, có thể mất updates. Một lựa chọn minh họa:

~~~java
private static final java.util.concurrent.atomic.AtomicInteger
        COUNT = new java.util.concurrent.atomic.AtomicInteger();

public Worker() {
    COUNT.incrementAndGet();
}
~~~

Hai đoạn này là thành phần của class, không phải chương trình độc lập. Với real service, có thể dùng immutable values, request-scoped objects, pooling thích hợp, locks hoặc concurrent collections tùy bài toán.

**Không giải quyết thread safety bằng cách mở một URLClassLoader mỗi request** nếu các requests chia sẻ tài nguyên khác như database/file/host registry. Nó có thể tách một số static fields, nhưng không thay một thiết kế concurrency đúng.

### Có cần một pool ClassLoader?

Pool nhiều class loaders cho cùng plugin/version có thể làm tăng số runtime class definitions, static copies và JAR resources; object từ loader A có thể không cast sang concrete Class của loader B. Nếu không cần cô lập namespace, thường không có lý do để tạo pool loaders lớn. Hãy cân nhắc pool **service instances** thay vì pool loader objects khi có bottleneck thực sự.

## 9. Reuse đúng vòng đời: per plugin, per version, đôi khi per tenant

Một lựa chọn kiến trúc thông dụng:

~~~text
Application Loader
  ├── api.Task       (shared contract)
  └── PluginManager
           |
           +-- Plugin "pricing" v1 --> Loader L1
           |                           Request A/B/C
           |
           +-- Plugin "pricing" v2 --> Loader L2
                                       Request D/E/F
~~~

Ưu điểm: các request của cùng version chia sẻ một code namespace, còn v1/v2 không bị ép chia cùng concrete Class identity.

Với hot reload:

1. Đặt JAR mới dưới tên file riêng theo version, tránh ghi đè file đang được loader cũ giữ.
2. Tạo loader mới L2 và kiểm tra plugin/service readiness.
3. Chuyển routing request mới sang v2; request cũ có thể hoàn thành ở v1.
4. Khi v1 quiesced, dừng plugin tasks/threads/callbacks, đóng InputStreams, JarFile/ZipFile riêng.
5. Đóng URLClassLoader v1 khi không còn hoạt động nạp class đồng thời.
6. Xóa strong references tới L1, old Class/Method caches, registry entries, ThreadLocals/TCCL khi appropriate.
7. Dọn file JAR cũ khi các file handles đã được giải phóng.

`URLClassLoader.close()` có contract riêng: nó đóng những file do loader mở và hạn chế nạp class/resource mới, nhưng không hủy mọi instances/classes đã được định nghĩa. Nếu có thread đang loading khi close, kết quả không được bảo đảm; do đó quản lý request-in-flight và stop lifecycle là cần thiết.

**Đừng reuse mãi một loader cho mọi version.** Khi Worker đã được loader L1 định nghĩa, thay JAR trên ổ đĩa không làm JVM định nghĩa lại Worker v2 trong L1. Loader mới là một ranh giới version hợp lý.

### Trường hợp cần nhiều loaders hơn

- Tenant A và B có code/classpath/dependencies thực sự khác nhau.
- Plugin versions khác nhau cần tồn tại song song.
- Những batch dynamic code có lifecycle unload độc lập.
- Named module configurations/ModuleLayers được resolve khác nhau.
- Test class loading policy hoặc class identity.

Khi đó hãy tạo loader **theo ranh giới cô lập cần thiết**, quản lý số lượng và thời điểm retire rõ ràng. Loader riêng không phải sandbox bảo mật cho code không tin cậy: class loader không tự cô lập process, filesystem, network hoặc CPU.

## 10. Các lỗi thiết kế thường gặp

| Sai lầm | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| Tạo URLClassLoader cho mỗi request không cần thiết | Repeated setup/loading, allocation & cleanup churn | Reuse theo plugin/version |
| Chia sẻ một service instance mutable cho nhiều requests | Race condition/state leakage | Instance riêng hoặc service thread-safe |
| Giả định loader mới luôn trả Class mới | Parent có thể define class, benchmark sai | So sánh Class object và defining loader |
| Coi static field là biến dùng chung toàn JVM | Class copies có static state riêng | Dùng API/shared state ở parent hoặc explicit store |
| Đóng loader ngay sau loadClass rồi trả instance sử dụng lâu | Lỗi lazy class/resource loading | Giữ loader sống theo phiên phục vụ |
| Không close loader/stream sau retire | JAR file handles và resource leakage | Try-with-resources/lifecycle shutdown |
| Cache Class/Method trong singleton host không xóa khi reload | Loader cũ không được GC | Cache có version scope và clear |
| Đưa plugin JAR vào host application classpath | Parent-first phá expected isolation/reload | Tách source plugin khỏi -cp host |
| Dùng per-request loader như security sandbox | Không cô lập quyền OS | Process/container isolation thích hợp |
| Benchmark chỉ đo new URLClassLoader | Không bao gồm class lookup, initialize và close | Đo trọn request lifecycle |
| Benchmark một lần trên JVM cold | Lẫn startup, GC và JIT | Warmup, forks, multiple runs |
| Dùng một loader cho v1 và v2 rồi sửa file JAR | Class bytecode đã define không tự cập nhật | Tạo loader mới cho version mới |
| Dùng child-first cho shared API | Hai bản API runtime, ClassCastException | Parent-first cho contract package |

## 11. Checklist chẩn đoán hiệu năng

1. Mỗi request có thực sự cần **code namespace mới** không, hay chỉ cần instance/state mới?
2. Mỗi plugin/version tạo bao nhiêu loaders? Có hạn mức và owner không?
3. Class plugin được **loader nào định nghĩa**? `type.getClassLoader()` và `Class<?>` identity có đúng không?
4. Parent đã định nghĩa class từ trước hay child tự define?
5. Thời gian nạp/verify/link/initialize lần đầu so với xử lý request steady-state thế nào?
6. Metaspace/class counts, heap allocations, JIT/GC có tăng khi tạo nhiều loaders?
7. TCCL, ThreadLocal, task queues, registries hoặc static caches có giữ loaders cũ?
8. Có resource/JAR handle còn mở, hoặc file bị lock trên Windows sau reload không?
9. Loader có còn bị requests sử dụng khi gọi `close()` không?
10. Shared API/interfaces có nằm ở parent và dùng cùng Class identity giữa plugins không?
11. Test load/cast/reload có trường hợp concurrent request và nhiều versions không?
12. Benchmark có đo cùng workload, N requests, concurrency, warm-up và ghi p95/p99/throughput không?

## 12. Tự kiểm tra kiến thức

**Câu 1.** Một loader nạp `demo.Worker` rồi gọi `loadClass("demo.Worker")` thêm 100 lần. JVM có tạo 100 Class objects khác nhau không?

**Đáp:** Không theo hợp đồng class loading thông thường. Cùng binary name và initiating loader sẽ có Class identity nhất quán; có thể còn lookup overhead nhưng không define mới cho mỗi lần gọi.

**Câu 2.** Hai URLClassLoader khác nhau cùng đọc Worker.class có bắt buộc trả hai runtime Class khác nhau không?

**Đáp:** Không. Nếu đều delegate về parent đã định nghĩa Worker, chúng có thể cùng trả bản parent. Nếu mỗi child thực sự tự define Worker, lúc đó hai Class identities khác nhau.

**Câu 3.** Reuse loader có bắt buộc dùng chung một service object giữa requests không?

**Đáp:** Không. Nhiều request có thể tạo instances riêng từ cùng một runtime Class.

**Câu 4.** Vì sao static counter khởi đầu lại ở mỗi loader mới?

**Đáp:** Khi child mới tự định nghĩa bản Worker runtime riêng, mỗi Class có static storage/initialization riêng.

**Câu 5.** Đóng loader per request có đảm bảo class unload lập tức không?

**Đáp:** Không. Unloading phụ thuộc reachability và JVM GC; `close()` chủ yếu quản lý nguồn class/resource loading và file handles.

**Câu 6.** Khi nào cần tạo loader mới thay vì reuse?

**Đáp:** Khi muốn classpath/version/tenant/batch code namespace độc lập. Thường tạo theo **plugin version** hoặc ranh giới nạp mã, không phải per request.

**Câu 7.** Có thể kết luận reuse nhanh gấp mười lần chỉ từ ví dụ sáu dòng stdout không?

**Đáp:** Không. Ví dụ chứng minh runtime class identity và static state, **không đo performance**. Cần benchmark cold/steady-state, throughput, allocations và memory.

**Câu 8.** Vì sao reuse loader nhưng vẫn có thể gặp memory leak?

**Đáp:** Loader sống quá lâu, caches/threads/registries giữ old Class references qua nhiều versions, hoặc không close resources khi retire. Reuse phải đi cùng lifecycle rõ ràng.

## Tài liệu tham khảo chính thức

- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findLoadedClass`, delegation và getClassLoadingLock.
- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): tìm class từ URL và `close()`.
- [JVMS Java SE 21 §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): runtime class identity và defining loader.
- [JVMS Java SE 21 §5.5 — Initialization](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.5): static initialization.
- [Java SE 21 — Class](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): Class objects và `getClassLoader`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-performance-005">Cache đúng phạm vi mà không giữ loader đã hết vòng đời</a>

<details>
<summary>Xem chi tiết</summary>

# Cache đúng phạm vi mà không giữ ClassLoader đã hết vòng đời

## 1. Vấn đề: cache nhanh hơn, nhưng có thể khiến plugin không bao giờ được dỡ

Một hệ thống plugin thường tạo `ClassLoader` riêng cho từng plugin hoặc từng phiên bản. Để giảm chi phí reflection, lookup hay xử lý metadata, host có thể cache `Class<?>`, `Method`, `Constructor`, provider instance, parser hoặc các đối tượng đã tính toán.

Ví dụ:

```java
static final Map<String, Class<?>> CLASSES = new ConcurrentHashMap<>();
```

Đây là đoạn mã minh họa cần imports và đặt trong một class. Nếu map này là **static field của host sống suốt tiến trình** và chứa class do plugin loader định nghĩa, map có thể giữ class → defining loader → toàn bộ tập class metadata của plugin còn reachable. Plugin đã được “stop” về mặt nghiệp vụ nhưng Java heap/Metaspace vẫn giữ dấu vết của loader thế hệ cũ.

**Nguyên tắc chính:** **vòng đời của cache không được dài hơn vòng đời của dữ liệu/lớp/plugin mà nó sở hữu, trừ khi cache dùng cơ chế không giữ đối tượng cũ sống và có chính sách eviction được hiểu đúng.**

Một bảng quyết định nhanh:

| Đang cache gì? | Chủ sở hữu hợp lý | Bao giờ giải phóng? |
|---|---|---|
| Metadata bất biến của JDK/shared API | Cache của host, nếu không chứa plugin-private references | Theo vòng đời host hoặc chính sách giới hạn dung lượng |
| `Class<?>`/`Method` của plugin | Cache gắn với **plugin session/generation** | Khi plugin dừng hoặc reload |
| Provider/plugin instance | Registry gắn với deployment đang hoạt động | Khi stop, sau khi dừng callbacks/tasks |
| Kết quả parse của một request | Request-local cache | Kết thúc request |
| Metadata tính theo runtime `Class<?>` | Có thể cân nhắc `ClassValue` | Theo lifecycle của Class association; vẫn xét references bên ngoài |
| Kết quả âm “class không tìm thấy” | Tối đa trong phạm vi finder/version snapshot xác định | Hủy khi sources thay đổi, không cache vô hạn |
| Reflection method lookup | Per-plugin cache keyed đúng class + signature | Clear cùng plugin loader |

Không có câu trả lời đơn giản “luôn dùng weak refs” hoặc “luôn dùng static map”. Phải biết **ai đang giữ cache**, **cache giữ gì**, **key là gì**, **giá trị có trỏ ngược tới loader hay không**, và **sự kiện lifecycle nào loại bỏ entry**.

## 2. Cơ sở JVM: vì sao giữ Class<?> có thể giữ loader?

Theo JLS 17 `12.7, một class/interface **chỉ có thể được unload nếu defining class loader của nó có thể được GC thu gom**. “Có thể” không có nghĩa JVM sẽ unload ngay.

Một graph thường gặp:

```text
GC root của host
     |
     v
static ConcurrentHashMap<String, Class<?>>
     |
     v
plugin.Worker.class
     |
     v
Defining PluginClassLoader
     |
     v
Các runtime classes / metadata do loader định nghĩa
```

Cache có thể giữ loader theo đường gián tiếp:

```text
Host static cache
   -> java.lang.reflect.Method
   -> declaring Class<?> của plugin
   -> defining loader của plugin
```

Hoặc:

```text
Host event registry
   -> plugin callback/lambda
   -> plugin object/class
   -> plugin defining loader
```

Vì vậy, chỉ đặt biến `loader = null` không đủ nếu vẫn còn `Method`, `Class<?>`, provider object, callback hoặc thread context class loader đang trỏ tới thế hệ plugin đó.

### Defining loader khác initiating loader

`pluginLoader.loadClass("api.Plugin")` có thể ủy quyền cho parent để lấy **cùng `api.Plugin.class` của host**. Cache class shared API này không đồng nghĩa giữ một class do plugin loader định nghĩa. Ngược lại, `pluginLoader.loadClass("plugin.Worker")` thường trả class do plugin loader định nghĩa nếu parent không có tên đó.

Phải phân biệt:

- **Defining loader:** loader thực sự định nghĩa runtime class; có ảnh hưởng trực tiếp tới khả năng class unloading.
- **Initiating loader:** loader mà JVM ghi nhận đã khởi xướng lookup, có thể nhận lại class từ parent.
- **Class identity:** binary name + defining loader, không chỉ class name.

Đừng suy “tôi gọi loadClass qua plugin loader nên mọi Class<?> nhận về đều do plugin loader sở hữu”.

## 3. ClassLoader đã có cơ chế nhớ class được nạp; không nên cache trùng vô ích

Trong `ClassLoader.loadClass(String, boolean)` mặc định, JVM/loader kiểm tra `findLoadedClass(name)` trước khi tìm parent hoặc `findClass`. Một lần gọi lại:

```java
Class<?> a = loader.loadClass("plugin.Worker");
Class<?> b = loader.loadClass("plugin.Worker");
System.out.println(a == b);
```

Đây là snippet cần biến `loader` và phải xử lý `ClassNotFoundException`. Với loader hoạt động đúng, cùng một runtime class name/loader lookup policy thường trả cùng `Class<?>`; trong tình huống đó, in `true`. Nói cách khác, lập thêm `static Map<String, Class<?>>` chỉ để “tránh load class lần hai” có thể đem lại lợi ích rất ít nhưng rủi ro giữ plugin cũ rất lớn.

Cache bổ sung chỉ nên có khi đã đo/biết chi phí cần tối ưu, chẳng hạn:

- Reflection lookup với `getMethod`, `getDeclaredConstructor` hoặc member scanning.
- Metadata phân tích annotation/schema.
- Method handle, serializer/deserializer được compile cho một class cụ thể.
- Kết quả resolution của một plugin registry với cấu trúc đã biết.

**Phải đo cache hit rate, allocation và lookup latency** trước khi biến một cache nội bộ thành static global. Caching có chi phí đồng bộ hóa, key construction, memory và invalidation riêng.

## 4. Chọn cache scope theo lifecycle, không theo mức tiện của static field

Có ít nhất bốn scope:

| Scope | Ví dụ | Có phù hợp plugin-private Class<?> không? |
|---|---|---|
| Method/request | Local map dùng trong request hiện tại | Có thể, nếu thực sự chỉ cần trong request |
| Operation/job | Cache của một batch/job thực thi trong plugin | Có thể, miễn job kết thúc và không bị queue giữ vô hạn |
| Plugin session/generation | `PluginSession` chứa loader và reflection caches | **Thường là lựa chọn tốt** |
| Host process/global singleton | Static map của application loader | **Nguy hiểm** nếu giữ private class/loader qua nhiều reloads |

**Plugin generation** là một lần nạp xác định: plugin X v1 có loader L1; reload plugin X v2 tạo loader L2. Hai lần deploy có thể dùng cùng logical plugin ID và cùng binary class name, nhưng chúng là **hai cache scopes khác nhau**.

### Sai: global cache keyed bằng binary name

```java
static final Map<String, Class<?>> TYPES = new ConcurrentHashMap<>();

// Host gọi trong nhiều plugin generations:
TYPES.put("plugin.Worker", workerClass);
```

Đoạn này minh họa và cần imports/class chứa field. Nếu plugin A và B đều có `plugin.Worker` nhưng do loaders khác nhau định nghĩa, key chỉ là `"plugin.Worker"` sẽ gây **đụng cache**. Host có thể trả class của plugin A cho B, rồi cast/linking sai hoặc tiếp tục pin old loader.

**Hai lỗi độc lập:**

1. **Sai identity:** class name không đủ làm cache key giữa nhiều defining loaders.
2. **Sai lifetime:** static map có thể giữ class của những generations đã dừng.

Dù thêm `pluginId` vào key, ví dụ `"plugin-a:plugin.Worker"`, vẫn cần version/generation và chính sách eviction. Một ID có thể được tái sử dụng khi reload, còn loader object mới có identity khác.

### Đúng hơn: cache trong một PluginSession

```text
Host registry (chỉ chứa sessions đang active)
  -> Session plugin-A generation 42
     -> URLClassLoader L42
     -> Method / Class<?> cache thuộc L42
  -> Session plugin-B generation 7
     -> URLClassLoader L7
     -> cache riêng

Khi stop plugin-A:
  dừng task/callback
  registry.remove(plugin-A)
  session.clearCaches()
  session.closeLoader()
  bỏ mọi strong references tới session cũ
```

Trong mô hình này, **strong references bên trong session không sai**. Chúng có ích khi plugin đang hoạt động. Điều quan trọng là session phải thật sự được **gỡ khỏi các roots sống lâu** khi plugin kết thúc.

## 5. Ví dụ Java 17 hoàn chỉnh: cache Method trong phạm vi PluginSession

Ví dụ dùng `URLClassLoader` riêng để nạp class `plugin.Greeter`, memoize reflection `Method` theo class và signature, rồi xóa cache/đóng loader khi session kết thúc.

Không đoán rằng việc đóng loader sẽ tạo class-unload event ngay lập tức; chỉ chứng minh deterministic cache hit và **cleanup theo lifecycle**.

### 5.1. Cấu trúc file

```text
src/
  plugin/
    plugin/
      Greeter.java
  host/
    SessionCacheDemo.java

out/
  plugin/
  host/
```

`out` được tạo ở bước biên dịch.

### 5.2. Plugin class

File `src/plugin/plugin/Greeter.java`:

```java
package plugin;

public final class Greeter {
    static {
        System.out.println("PLUGIN_INITIALIZED");
    }

    public static String greet(String name) {
        return "Hello " + name;
    }
}
```

### 5.3. Host với cache riêng cho từng session

File `src/host/SessionCacheDemo.java`:

```java
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SessionCacheDemo {
    static final class PluginSession implements AutoCloseable {
        private record MethodKey(
                String owner, String name, List<Class<?>> parameterTypes) {
        }

        private final URLClassLoader loader;
        private final Map<MethodKey, Method> methodCache =
                new HashMap<>();
        private int misses;
        private boolean closed;

        PluginSession(URL source, ClassLoader parent) {
            loader = new URLClassLoader(
                    "plugin-session", new URL[]{source}, parent);
        }

        Method method(String owner, String name, Class<?>... params)
                throws ReflectiveOperationException {
            if (closed) {
                throw new IllegalStateException("Session closed");
            }

            MethodKey key = new MethodKey(
                    owner, name, List.copyOf(Arrays.asList(params)));
            Method cached = methodCache.get(key);
            if (cached != null) {
                return cached;
            }

            Class<?> type = Class.forName(owner, false, loader);
            Method found = type.getMethod(name, params);
            methodCache.put(key, found);
            misses++;
            return found;
        }

        ClassLoader loader() {
            return loader;
        }

        int missCount() {
            return misses;
        }

        int cacheSize() {
            return methodCache.size();
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                methodCache.clear();
                loader.close();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        URL pluginRoot = Path.of("out/plugin").toUri().toURL();
        ClassLoader sharedParent =
                SessionCacheDemo.class.getClassLoader();

        try (PluginSession session =
                     new PluginSession(pluginRoot, sharedParent)) {

            Method first = session.method(
                    "plugin.Greeter", "greet", String.class);
            Method second = session.method(
                    "plugin.Greeter", "greet", String.class);

            System.out.println("Same cached Method: "
                    + (first == second));
            System.out.println("Reflection cache misses: "
                    + session.missCount());
            System.out.println("Declaring loader is plugin: "
                    + (first.getDeclaringClass().getClassLoader()
                       == session.loader()));
            System.out.println("Before invocation");

            System.out.println(first.invoke(null, "Ada"));
            System.out.println(second.invoke(null, "Bao"));

            System.out.println("Cache entries before close: "
                    + session.cacheSize());
        }

        System.out.println("Session closed");
    }
}
```

**Tại sao key có `Class<?>` thay vì chuỗi tên kiểu tham số?** `MethodKey` chứa declaring binary name, method name và **runtime parameter classes**. Các `Class<?>` trong `List` được so sánh bằng identity theo Java; `Class<?>` do hai loaders khác nhau định nghĩa sẽ không bị coi như cùng type chỉ vì `getName()` giống nhau. Phạm vi session bảo đảm declaring name chỉ được tìm theo loader của session đó.

`List.copyOf(Arrays.asList(params))` giữ một bản immutable snapshot của danh sách parameter types; không sử dụng một mảng varargs mutable làm key.

**Đối với production:** key có thể dùng trực tiếp declaring `Class<?>` nếu đã có type và các method signatures chính xác; nếu cache dùng chung giữa classes từ nhiều loaders, tuyệt đối không chỉ dùng tên chuỗi. Nếu có inherited methods hoặc overloads/varargs, phải kiểm soát lookup contract và parameter descriptors đúng.

### 5.4. Biên dịch/chạy trên JDK 17

Bash:

```bash
mkdir -p out/plugin out/host
javac -d out/plugin src/plugin/plugin/Greeter.java
javac -d out/host src/host/SessionCacheDemo.java
java -cp out/host SessionCacheDemo
```

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force out/plugin,out/host
javac -d out/plugin src/plugin/plugin/Greeter.java
javac -d out/host src/host/SessionCacheDemo.java
java -cp out/host SessionCacheDemo
```

**Không** đưa `out/plugin` vào `-cp` của host, nếu không parent loader có thể cung cấp `plugin.Greeter` trước khi `URLClassLoader` của session được hỏi theo parent-first policy.

Stdout xác định khi chạy sạch:

```text
Same cached Method: true
Reflection cache misses: 1
Declaring loader is plugin: true
Before invocation
PLUGIN_INITIALIZED
Hello Ada
Hello Bao
Cache entries before close: 1
Session closed
```

### 5.5. Hiểu kết quả

1. Hai lookup cùng `MethodKey` trong một session trả cùng `Method` instance, chứng minh cache hit.
2. Chỉ lookup đầu tiên thực hiện `Class.forName(..., false, loader)` và `getMethod`; `missCount` bằng 1.
3. Declaring `Class<?>` của `Method` thực sự do session URLClassLoader định nghĩa.
4. `Class.forName(..., false, ...)` tìm class nhưng không yêu cầu initialization. Static initializer `PLUGIN_INITIALIZED` chỉ chạy khi reflective invocation của static method được thực hiện.
5. Cache vẫn giữ mạnh `Method` và những `Class<?>` mà key/value tham chiếu tới **trong lúc session active**. Điều đó là chủ đích.
6. `close()` xóa cache và đóng tài nguyên URLClassLoader.
7. Sau khi thoát scope và không còn roots khác giữ session/Method/Class, defining loader **có thể** trở nên eligible cho GC. Ví dụ **không** khẳng định JVM đã GC hay unload class ngay khi in `Session closed`.

Các biến local `first` và `second`, cùng stack/liveness của JVM, cũng có thể ảnh hưởng khả năng GC tại thời điểm gần khối `try`. Đó là lý do demo cố tình **không** in một boolean “unloaded=true” sau khi close.

### 5.6. Điều kiện triển khai song song

`HashMap` trong demo chỉ dành cho access tuần tự. Nếu một `PluginSession` phục vụ nhiều threads đồng thời, không chia sẻ `HashMap` không đồng bộ như thể nó thread-safe. Có thể:

- Dùng `ConcurrentHashMap<MethodKey, Method>` và `computeIfAbsent` với chính sách exception/lookup phù hợp.
- Hoặc đồng bộ vùng cache lookup một cách có kiểm soát.
- Quan trọng hơn, **phối hợp close với in-flight tasks** để không đóng loader và clear cache trong lúc worker còn cần nạp lớp hoặc chạy plugin.

Không dùng một khóa global cho tất cả plugins nếu chúng có thể hoạt động độc lập; tránh contention không cần thiết.

## 6. Dọn cache khi dừng plugin: thứ tự quan trọng

Một lifecycle hợp lý:

```text
ACTIVE
  |
  v
STOPPING: ngừng nhận request mới
  |
  v
DRAIN: chờ in-flight tasks hoàn tất
  |
  v
UNREGISTER: listeners / scheduled jobs / providers
  |
  v
CLEAR: method/class/provider caches của session
  |
  v
CLOSE: streams / JAR handles / URLClassLoader
  |
  v
REMOVE: gỡ session khỏi registry của host
  |
  v
Để old loader không còn strong reachability
  |
  v
JVM có thể GC/unload khi thích hợp (không có deadline)
```

Trật tự cụ thể có thể khác theo framework; yêu cầu bất biến là **không có công việc mới truy cập session đã đóng** và phải xóa mọi root không cần thiết. Đôi khi cần remove registry entry **trước khi** đóng loader để cấm lookup mới, nhưng vẫn giữ một local/session handle cho quá trình drain rồi close. Cần đồng bộ trạng thái `ACTIVE → STOPPING → CLOSED` để tránh race.

### Registry của host giữ active sessions

Snippet kiến trúc:

```java
Map<String, PluginSession> active = new ConcurrentHashMap<>();

// Start: thêm session đang hoạt động
active.put(pluginId, session);

// Stop: ngăn request mới trước, drain tasks rồi:
PluginSession old = active.remove(pluginId);
if (old != null) {
    old.close();
}
```

Đây là **snippet**, không phải program độc lập: cần khai báo types/variables và chính sách chặn request/drain phù hợp. Không giữ session đã remove trong một `List<PluginSession> oldSessions` static “để debug” mãi; điều đó tái tạo leak theo đường khác.

**Một cache có thể dùng strong references miễn vòng đời của nó được giới hạn và dọn rõ ràng.** Không bắt buộc mọi cache phải yếu.

## 7. Bẫy WeakHashMap: khóa yếu nhưng giá trị giữ loader sống

`WeakHashMap<K,V>` giữ **keys qua weak references**, nhưng **values qua strong references**. Tài liệu Java SE 17 đặc biệt cảnh báo: nếu value có đường mạnh **trực tiếp hoặc gián tiếp** quay về key, key sẽ không bị GC bỏ đi.

Ví dụ nguy hiểm:

```java
static final Map<ClassLoader, Class<?>> CACHE =
        new WeakHashMap<>();

CACHE.put(pluginLoader, pluginLoader.loadClass("plugin.Greeter"));
```

Đây là snippet với variables/imports phù hợp. Map static của host chứa:

```text
Host static WeakHashMap
    |
    +-- weak key ----> pluginLoader
    |
    +-- STRONG value -> plugin.Greeter.class
                             |
                             v
                      defining pluginLoader
```

Dù key yếu, **value** là `Class<?>` của class do chính loader ấy định nghĩa, nên giá trị có thể giữ loader key còn strongly reachable. Entry không tự biến mất như bạn kỳ vọng.

Tương tự:

```java
Map<ClassLoader, Method> methods = new WeakHashMap<>();
methods.put(pluginLoader, pluginMethod);
```

`Method` liên hệ với declaring class, có thể kéo theo defining loader. Nếu map sống cùng host, “đổi HashMap thành WeakHashMap” **không tự sửa** mọi classloader leak.

### Nếu value chỉ là String thì sao?

`WeakHashMap<ClassLoader, String>` với giá trị không giữ lại key hoặc plugin graph có thể phù hợp trong một số bài toán. Nhưng vẫn phải xét:

- Tập các other strong references đến key.
- `WeakHashMap` không thread-safe mặc định.
- Cleanup xảy ra phụ thuộc GC; không có deadline deterministic.
- Không dùng weak-key cache để cam kết business correctness/lifecycle shutdown.

Dùng weak keys chỉ khi **thật sự cần semantic weak ownership** và đã kiểm tra không có đường strong reference ngược từ value tới key.

### WeakReference cho value có tự động giải quyết mọi vấn đề?

Có thể thử:

```java
Map<ClassLoader, WeakReference<Class<?>>> cache =
        new WeakHashMap<>();
```

Nhưng đây vẫn chỉ là **snippet kiến trúc**. Value cũng yếu có thể tránh một đường giữ root, song giá trị cache có thể bị GC bất cứ lúc nào, khiến hit/miss không ổn định. Từ `ref.get()` nếu nhận non-null, cần giữ referent trong strong local trong suốt thao tác sử dụng. Phải xét concurrency, key identity, stale entries và cleanup. Không dùng weak refs như một công cụ ép unload.

**Với plugin có lifecycle rõ, per-session strong cache + explicit teardown thường dễ hiểu và kiểm soát hơn** một mạng weak maps phức tạp.

## 8. ClassValue: metadata gắn với Class<?> thay vì global Map<Class<?>, V>

`java.lang.ClassValue<T>` giúp gắn một giá trị tính toán lười (*lazy computed value*) với một runtime `Class<?>`. Nó hữu ích cho per-class metadata, dispatch tables hoặc thông tin reflection, và tránh việc tự xây một static map global keyed bằng `Class<?>` mà có thể pin plugin classes.

Ví dụ Java 17 độc lập — file `ClassValueDemo.java`:

```java
import java.lang.reflect.Method;

public final class ClassValueDemo {
    private static final ClassValue<Integer> PUBLIC_METHOD_COUNT =
            new ClassValue<>() {
                @Override
                protected Integer computeValue(Class<?> type) {
                    return type.getMethods().length;
                }
            };

    public static void main(String[] args) {
        Class<?> type = String.class;
        int a = PUBLIC_METHOD_COUNT.get(type);
        int b = PUBLIC_METHOD_COUNT.get(type);

        System.out.println("Same computed value: " + (a == b));
        System.out.println("Nonnegative method count: " + (a >= 0));

        PUBLIC_METHOD_COUNT.remove(type);
        int afterRemove = PUBLIC_METHOD_COUNT.get(type);
        System.out.println("Recomputed value is nonnegative: "
                + (afterRemove >= 0));
    }
}
```

Biên dịch/chạy:

```bash
javac ClassValueDemo.java
java ClassValueDemo
```

Stdout xác định:

```text
Same computed value: true
Nonnegative method count: true
Recomputed value is nonnegative: true
```

Chọn ví dụ `Integer` và so sánh giá trị số, không dựa vào identity của Integer wrappers hay số lượng public methods cụ thể của `String` vốn có thể thay đổi theo JDK.

**Các giới hạn cần hiểu:**

1. `ClassValue.get(type)` tính/lấy value gắn với **runtime class identity**, không chỉ binary name.
2. `computeValue` có thể được gọi nhiều lần trong race; API bảo đảm một value được chọn khi cài đặt, **không hứa mọi compute chỉ chạy một lần**.
3. `remove(type)` xóa association để lần `get` sau có thể tính lại; không phải lệnh unload class.
4. `ClassValue` không tự biến một metadata value thành an toàn để chia sẻ ngoài loader boundary.
5. Nếu value hoặc class token được giữ ở **một global cache khác**, ClassValue không sửa được đường strong reference bên ngoài đó.
6. Không cache `Method`/MethodHandle/`Class<?>` do plugin định nghĩa thêm trong static maps khác mà quên lifecycle.

Dùng `ClassValue` khi **cache thực sự là thuộc tính của class**. Dùng per-session cache khi logic phụ thuộc plugin configuration, version, permissions hoặc lifecycle riêng.

## 9. Chọn khóa cache đúng class identity, không chỉ dùng getName()

Một loader generation A và B có thể cùng định nghĩa `plugin.Service`:

```text
("plugin.Service", LoaderA) != ("plugin.Service", LoaderB)
```

Nếu cache đặt tại host:

```java
Map<String, Method> cache = new ConcurrentHashMap<>();
cache.put(method.getDeclaringClass().getName() + "#run", method);
```

Snippet này không phân biệt loader A/B, và value `Method` có thể pin old loader. Ngay cả khi key mở rộng theo `pluginId`, nếu plugin v1/v2 dùng cùng pluginId, sẽ cần generation discriminator và explicit invalidation.

### Khi cache nằm trong session

Nếu session chỉ sở hữu đúng một loader thế hệ và được dọn khi stop, binary name có thể là một phần của key; scope đã cung cấp identity. Tuy nhiên method overload phải xét parameter types:

```java
record MethodKey(
    Class<?> declaringClass,
    String methodName,
    List<Class<?>> parameterTypes) {}
```

Đây là type declaration minh họa cần `import java.util.List`. Các `Class<?>` keys giữ mạnh runtime classes — **được phép trong session đang active**, nhưng không nên giữ trong một static map vô hạn.

### Cache lookup vs cache compiled metadata

Nếu computed metadata độc lập instance và thuộc về runtime class, cân nhắc `ClassValue`. Nếu metadata phụ thuộc application configuration, locale, plugin version hoặc tenant, key phải bao gồm các dimensions ảnh hưởng kết quả; không lấy `Class<?>` làm khóa duy nhất khi kết quả thực ra phụ thuộc nhiều tham số.

**Cache sai key có thể làm sai chức năng ngay cả khi không có memory leak.**

## 10. Caching “không tìm thấy class” là một trường hợp đặc biệt

Một số hệ plugin cache kết quả âm để tránh mỗi request lại quét JAR:

```java
Set<String> notFound = ConcurrentHashMap.newKeySet();
```

Snippet này cần imports và nằm trong class. Negative caching chỉ đúng trong **source snapshot cố định** với lookup policy cố định.

Nếu plugin directory/ModuleLayer/classpath được cập nhật, tên trước đây không có có thể trở thành có. Global negative cache có thể khiến hệ thống báo `ClassNotFoundException` mãi dù artifact đã triển khai. Ngược lại, một cache dương có thể giữ class của version cũ sau reload.

Quy tắc:

- Gắn negative cache với **plugin generation**.
- Khi version/artifact/finder/layer thay đổi, bỏ negative cache cũ.
- Không cache mọi lỗi như “không tìm thấy”: `LinkageError`, `SecurityException`, initialization failure và dependency failure có semantics khác.
- Nếu dùng TTL, nhớ rằng TTL không thay lifecycle event: khi stop/reload phải invalidate ngay, không chờ thời gian trôi hết.
- Nếu loader không thực sự đọc lại dynamic sources trong cùng generation, thiết kế một **immutable snapshot** sẽ khiến negative caching dễ lý giải hơn.

## 11. TCCL, thread pools và ThreadLocal là những cache roots không rõ ràng

Dù session cache đã xóa, loader có thể vẫn còn sống vì worker thread giữ **thread context class loader (TCCL)**:

```text
Long-lived Executor worker
   -> Thread.contextClassLoader
   -> Old PluginClassLoader
```

Hoặc:

```text
Worker Thread
   -> ThreadLocalMap.value
   -> plugin-specific context
   -> plugin class
   -> plugin loader
```

Đổi TCCL ở thread submit **không nhất thiết** đổi TCCL của worker có sẵn trong pool. Nếu một framework bắt buộc sử dụng TCCL, phải đặt/restore **trong đúng worker**:

```java
Thread thread = Thread.currentThread();
ClassLoader old = thread.getContextClassLoader();
try {
    thread.setContextClassLoader(pluginLoader);
    invokeLegacyFramework();
} finally {
    thread.setContextClassLoader(old);
}
```

Đây là snippet minh họa, cần `pluginLoader` và `invokeLegacyFramework()` trong scope. Không dùng `setContextClassLoader(null)` như một cách reset mặc định; phải restore chính giá trị `old`.

Với `ThreadLocal`, gọi `remove()` trong `finally` của chính worker thread trước khi trả worker cho pool. Việc key `ThreadLocal` yếu không làm value trở thành yếu; một value plugin sống trong worker lâu dài vẫn có thể pin loader.

### Async task sau khi session đóng

Một callback bị giữ trong queue có thể chứa lambda class/closure được định nghĩa bởi plugin loader, hoặc capture plugin object. Thậm chí registry entry chính đã xóa, scheduled task còn tồn tại vẫn có thể giữ old generation sống.

**Lifecycle cache và lifecycle tác vụ cần phối hợp**: stop nhận việc mới, cancel scheduled tasks, drain in-flight work, unregister listeners/callbacks, dọn ThreadLocals và TCCL, sau đó dọn caches/loader.

## 12. ServiceLoader, proxy, reflection và MethodHandle

Các framework thường cache metadata đa dạng:

| Loại cached value | Có thể giữ gì? | Gợi ý scope |
|---|---|---|
| `ServiceLoader<S>` | Service/provider metadata và instances, loader/layer tùy cấu hình | Theo plugin/provider registry, không cache vô hạn theo TCCL |
| `Method` / `Constructor` / `Field` | Declaring class và related metadata | Per-class `ClassValue` hoặc per-session |
| `MethodHandle` / `MethodHandles.Lookup` | Method/type/access context | Per-session hoặc per-class theo ownership |
| Dynamic proxy class/instance | Interface types, invocation handler, defining loader | Không để singleton host giữ proxy của plugin cũ |
| Lambda/callback | Captured objects/instances và classes | Unregister/cancel khi plugin dừng |
| Serializer/deserializer compiled cho private DTO | Runtime class metadata | Per-class/per-session, không global unbounded |
| `URLClassLoader` | JAR/resource handles và class loader object | Đóng khi hết công việc; bỏ strong references |

Một `ServiceLoader.load(Plugin.class)` sử dụng TCCL, trong khi overload `ServiceLoader.load(Plugin.class, chosenLoader)` cho phép chọn loader rõ ràng. Với plugin registry đã biết loader, nên hạn chế dependency vào TCCL ngầm.

Nếu muốn cache provider, cache trong **session/layer có lifecycle**, chứ không giữ `ServiceLoader` của plugin v1 trong host singleton sau khi đã reload v2.

## 13. Một cache yếu không thay được lifecycle quản lý resources

`WeakReference` và `ReferenceQueue` giúp theo dõi khả năng GC, nhưng chúng không tự gọi `URLClassLoader.close()` đúng thời điểm hay chấm dứt thread đang chạy.

Ví dụ thông tin quan sát:

```java
WeakReference<ClassLoader> weak = new WeakReference<>(loader);
```

Snippet cần import `java.lang.ref.WeakReference` và loader variable. Nếu có strong reference khác, `weak.get()` vẫn có thể trả loader. Nếu `weak.get() == null`, collector đã xử lý referent theo weak-reference semantics; **không bảo đảm class-unload log hoặc RSS giảm ngay**.

Đừng viết test dựa trên:

```java
System.gc();
if (weak.get() != null) {
    throw new AssertionError("Leak!");
}
```

`System.gc()` là yêu cầu, không phải một cam kết collector đã reclaim loader. Một lần `get() != null` không chứng minh leak; JVM có thể chưa collect.

Nếu dùng `WeakReference.get()` để thao tác:

```java
ClassLoader alive = weak.get();
if (alive != null) {
    // Dùng alive ngay trong phạm vi strong local này.
    java.lang.ref.Reference.reachabilityFence(alive);
}
```

Đoạn này minh họa: `alive` không được lưu vào host cache vô thời hạn; `reachabilityFence` bảo đảm reachability qua điểm fence theo contract. Nó không thay lifecycle owner.

### WeakReference là công cụ quan sát, không là nguồn chân lý duy nhất

Khi một loader được thu gom, Java API không cho một “sự kiện unload class” đồng bộ, portable cho mọi JVM. Kết hợp weak reference monitoring với GC/class-unload logs, classloader statistics và heap reference graph phù hợp.

Nếu dùng `Cleaner` để dọn tài nguyên, cẩn thận action **không capture chính referent** hoặc plugin instance/loader, nếu không có thể lại giữ nó sống. Với `URLClassLoader`, nên đóng chủ động theo lifecycle thay vì chỉ trông chờ cleaner/finalization.

## 14. ClassValue, WeakHashMap và per-session map: so sánh thực dụng

| Thiết kế | Ưu điểm | Rủi ro/cần nhớ | Khi nào phù hợp |
|---|---|---|---|
| Static `Map<String, Class<?>>` | Lookup đơn giản | Sai key giữa loaders, giữ old Class/loader | Tránh cho plugin-private classes |
| Static `Map<Class<?>, Method>` | Identity key chính xác | Key và value mạnh → pin plugin | Tránh nếu không có eviction theo lifecycle |
| Static `WeakHashMap<ClassLoader, Class<?>>` | Key yếu | Value Class trỏ gián tiếp về key → pin loader | **Thường không đúng** khi value là class của key |
| `WeakHashMap<ClassLoader, WeakReference<V>>` | Hạn chế giữ mạnh value | GC nondeterministic, phức tạp race/cleanup | Chỉ khi weak semantics thực sự được thiết kế |
| `ClassValue<V>` | Tự gắn value với runtime Class identity | Không giải quyết roots nằm trong cache khác, compute có race | Per-class derived metadata |
| `PluginSession` + `HashMap`/`ConcurrentHashMap` | Lifecycle rõ, lookup nhanh, dễ test | Phải đóng/clear đúng lúc và đồng bộ in-flight | **Lựa chọn mặc định tốt** cho nhiều plugin systems |
| Host bounded cache với explicit eviction | Có giới hạn số entries | Eviction không đồng nghĩa ngừng plugin; key/value vẫn phải đúng | Shared, non-plugin metadata hoặc cache có versioned ownership |

**Không có lựa chọn nào mặc nhiên đúng cho mọi workload.** Trước tiên chọn ownership/lifecycle; sau đó mới chọn collection type và concurrency policy.

## 15. Concurrency: race giữa cache hit, lookup và close

Nếu request R1 đang lookup method trong session thì R2 ra lệnh stop plugin, có thể xảy ra:

```text
R1: đọc session ACTIVE
R2: đặt session STOPPING
R2: clear cache và close loader
R1: cố findClass từ JAR đã close
R1: ClassNotFoundException hoặc behavior không như mong muốn
```

Java SE 17 `URLClassLoader.close()` ghi rõ: khi thread khác đang load class đồng thời với `close`, **kết quả class loading là không xác định** theo contract. Do đó đơn thuần dùng `ConcurrentHashMap` không đủ để bảo vệ lifecycle.

Một thiết kế production có thể dùng:

1. Một state machine `ACTIVE / STOPPING / CLOSED`.
2. Bộ đếm active operations hoặc read–write lock.
3. Khi stop: ngăn mở operations mới, chờ operations đã bắt đầu kết thúc.
4. Hủy/cancel scheduled work, xóa caches và đóng loader.
5. Gỡ khỏi registry và thả references.

**Không nên giữ một global lock** cho mọi plugin khi chỉ cần serialize stop/lookup trong cùng plugin session. Với cached `Method`, nếu value đã được trả về một worker, clear map **không thu hồi reference mà worker đang giữ**; cần drain worker trước.

### Có cần ConcurrentHashMap.computeIfAbsent?

Dùng được khi mapping function không tạo side effects nguy hiểm và exception behavior phù hợp. `getMethod`/`Class.forName` ném checked exceptions nên cần wrapper/synchronization hợp lý thay vì nuốt lỗi hoặc biến mọi failure thành `null`.

Một `ConcurrentHashMap` keyed theo `MethodKey` vẫn giữ strong keys/values. Nó sửa **thread safety**, không sửa **classloader retention** nếu đặt trong host singleton sống vô hạn.

## 16. Đo hiệu năng trước khi cache

Chi phí của một cache không chỉ là thời gian lookup class:

- **Cache hit rate**: bao nhiêu lượt thật sự reuse metadata?
- **Miss cost**: reflective lookup/annotation parsing/method handle creation tốn bao nhiêu?
- **Memory footprint**: bao nhiêu `Class<?>`, `Method`, metadata objects và plugin generations bị giữ?
- **Invalidation cost**: có xóa chính xác entry khi plugin reload không?
- **Concurrency**: lock contention, racing computations, publish visibility có ảnh hưởng?
- **Cold start**: cache warm-up có tạo nhiều classes/proxies không cần?
- **Lifecycle effects**: Metaspace có tăng qua 100 lần reload mặc dù active plugins không đổi?

Nếu class loading đã được VM cache, tạo thêm một global map cho `ClassLoader.loadClass` có thể chỉ thêm chi phí và leak. Chỉ tối ưu sau khi profiler/JMH hoặc benchmark thực tế chứng minh bottleneck.

**Không dùng `System.nanoTime` đo một lần gọi cold reflection rồi suy ra “cache nhanh hơn X lần”**: JIT, warm-up, GC và class initialization có thể gây sai lệch lớn. Với workload thực, đo cả p95/p99 latency và memory retention theo chu kỳ deploy.

## 17. Quan sát Metaspace và class unloading

Để đánh giá **cache có pin loader hay không**, thử nhiều vòng start/stop/reload với số plugin active không đổi. Nếu sau khi quiesce vẫn còn class loaders của các generations cũ, điều tra **retaining path từ host static map, `Method`/`Class<?>`, `ServiceLoader`, TCCL hoặc queued tasks**. Một cache sai lifetime thường hiện ra là chuỗi root → cached value → declaring class → defining loader.

Trên HotSpot JDK 17 có thể xem `-Xlog:class+unload=info`, `jcmd PID VM.classloader_stats`, JFR, heap dump và Metaspace used/committed. Ví dụ session rất ngắn có thể kết thúc **không có unload event**: close/clear tạo điều kiện, nhưng JVM không cam kết GC/unload đúng thời điểm. Đừng dùng một lần `System.gc()` hay WeakReference check để kết luận leak.

Muốn chứng minh bản sửa cache có hiệu quả, so cùng workload nhiều generations trước/sau, log cache entries bị xóa, current loaded classes, số old loaders reachable và heap **path to GC root**. Metaspace committed hoặc RSS có thể không giảm lập tức dù metadata đã được reclaimed; tránh kết luận chỉ từ một metric.

## 18. Vấn đề ClassLoader.close và ClassValue.remove

**`URLClassLoader.close()` không phải “dỡ tất cả class ngay”**. JDK 17 mô tả rằng các classes/resources đã load vẫn có thể sử dụng sau close; close chủ yếu dừng tìm mới từ URLs và đóng các tài nguyên mở. Nếu host còn giữ `Method`/instance thì class/loader có thể tiếp tục reachable.

**`ClassValue.remove(clazz)` không phải “yêu cầu unload clazz”**. Nó chỉ gỡ association của đúng ClassValue cho clazz. Nếu clazz còn reachable qua host map hoặc static field, loader có thể tiếp tục sống.

**Xóa một `HashMap` cũng không bảo đảm hết roots khác**: worker có thể đang giữ Method trong local, listener registry giữ plugin object, TCCL giữ loader, `ModuleLayer` có thể đang được host quản lý, hoặc service provider cache còn reference.

**Dọn theo lifecycle là điều kiện thiết kế để có cơ hội GC, không phải API bảo đảm unloading**.

## 19. Checklist review cache ở ranh giới plugin

- [ ] Cache nằm trong đúng plugin session/generation hoặc có eviction rõ ràng.
- [ ] Không có static host cache giữ `Class<?>`/`Method` của plugin không giới hạn.
- [ ] Key phân biệt đúng runtime `Class<?>` identity; không chỉ dùng binary name khi nhiều loaders cùng tồn tại.
- [ ] Có metrics hits/misses/size để biết cache có đáng tồn tại hay không.
- [ ] Không dùng `WeakHashMap` một cách mù quáng khi value có thể tham chiếu về key/loader.
- [ ] Nếu dùng `ClassValue`, metadata thật sự là per-class và không được lưu strong ra global map khác.
- [ ] Khi reload, cache/negative cache của thế hệ cũ bị bỏ.
- [ ] Stop plugin chặn task mới, drain active tasks, unregister listeners/jobs rồi clear/close.
- [ ] ThreadLocal được `remove()` đúng thread; TCCL được restore trong `finally`.
- [ ] `ServiceLoader`/provider registry và reflection/proxy caches được giải phóng theo owner.
- [ ] Concurrent lookup và close không được race trên `URLClassLoader`.
- [ ] Có regression test cho nhiều chu kỳ start/stop/reload và kiểm tra old loader roots.
- [ ] Không dùng `System.gc()` hoặc một lần WeakReference.get để quyết định test pass/fail về unload.
- [ ] Đã tham khảo ClassLoader, WeakHashMap và ClassValue contract của đúng JDK.

## 20. Các lỗi tư duy thường gặp

| Quan niệm sai | Cách hiểu đúng |
|---|---|
| “Cache bằng String name là đủ.” | Runtime type identity còn có defining loader; cùng tên có thể là hai classes. |
| “ClassLoader.loadClass luôn định nghĩa lại class.” | Mặc định kiểm tra class đã loaded và có thể trả cùng `Class<?>`. |
| “WeakHashMap dùng khóa yếu nên không thể leak loader.” | Value được giữ mạnh và có thể trỏ ngược tới loader key. |
| “Method không giữ Class<?>.” | Reflection metadata liên hệ với declaring class; cần xét reachability. |
| “Clear map đồng nghĩa loader đã unload.” | Chỉ gỡ một tập references; JVM GC/unloading độc lập. |
| “URLClassLoader.close sẽ dỡ class ngay.” | Close resources/lookup không bằng unload class. |
| “Đổi plugin version nhưng cache cùng tên vẫn hợp lệ.” | Generation mới có loader/type identity mới. |
| “ConcurrentHashMap sửa được classloader leak.” | Nó sửa thread safety, không tự thay key/lifecycle semantics. |
| “TTL 10 phút là đủ để stop plugin.” | Stop phải invalidate theo lifecycle, không chờ timer nếu còn pin resources. |
| “ClassValue bảo đảm mọi reference của plugin được dọn.” | Nó chỉ quản lý association per-class; cache roots ngoài vẫn tồn tại. |
| “Phải dùng weak reference mọi chỗ mới an toàn.” | Per-session strong cache + explicit cleanup thường đơn giản hơn. |
| “Không có unload event trong một test ngắn nghĩa leak.” | GC/class unloading là nondeterministic và phụ thuộc nhiều roots. |
| “Host giữ parent loader nên child bắt buộc còn sống.” | Child thường giữ parent; parent không mặc nhiên giữ child. |
| “Một plugin hết requests tức là threads/lambdas đã hết.” | Worker queues, callbacks và ThreadLocal còn có thể giữ loader. |

## 21. Bài tập tự kiểm tra

**Câu 1.** Host có static `Map<String, Class<?>>` lưu `plugin.Worker` của phiên bản cũ. Plugin đã được stop và biến loader local = null. Loader cũ đủ điều kiện reclaim chưa?

**Đáp án:** Chưa thể kết luận đủ điều kiện; static map còn giữ `Class<?>` của plugin, có thể giữ defining loader reachable. Phải xóa entry và các roots khác.

**Câu 2.** Dùng `WeakHashMap<ClassLoader, Method>` là đủ vì key weak?

**Đáp án:** Không. Value `Method` có thể giữ declaring class → defining loader quay lại key; value được WeakHashMap giữ mạnh.

**Câu 3.** Vì sao `ClassLoader.loadClass` nhiều lần không bắt buộc tạo nhiều runtime classes?

**Đáp án:** Mặc định loader kiểm tra class đã được nạp và trả lại nếu phù hợp. Cache Class<?> global có thể là tối ưu thừa.

**Câu 4.** Hai plugin loaders định nghĩa cùng binary name `plugin.Service`. Cache key chỉ dùng `"plugin.Service"` có an toàn không?

**Đáp án:** Không nếu cache dùng chung. Key thiếu loader/runtime class identity, có nguy cơ trả sai class generation.

**Câu 5.** `ClassValue` khác `static Map<Class<?>, Metadata>` chủ yếu ở đâu?

**Đáp án:** ClassValue cung cấp association gắn với runtime class, tránh giữ per-class data qua một map key mạnh global tự quản. Tuy nhiên không chữa được strong roots khác và phải hiểu `computeValue` concurrency.

**Câu 6.** `ClassValue.remove(clazz)` có unload class đó không?

**Đáp án:** Không. Nó chỉ xóa associated value của ClassValue; unloading phụ thuộc defining loader reachability và quyết định VM.

**Câu 7.** Tại sao phải drain in-flight tasks trước `URLClassLoader.close()`?

**Đáp án:** Concurrent class load khi close có kết quả không xác định theo contract; worker còn giữ plugin instances/metadata cũng cản cleanup.

**Câu 8.** Cache `ClassNotFoundException` hoặc trạng thái “không thấy tên này” qua các plugin versions có rủi ro gì?

**Đáp án:** Artifact/snapshot mới có thể chứa tên đó; cache âm cũ sẽ đưa ra kết quả stale. Invalidate theo generation/source change.

**Câu 9.** Dùng weak reference để kiểm tra loader có leak sau một lần `System.gc()`, kết quả `weak.get() != null` đủ chứng minh leak chưa?

**Đáp án:** Chưa. GC/unloading không có bảo đảm diễn ra đúng thời điểm; cần phân tích GC-root paths và nhiều chu kỳ lifecycle.

**Câu 10.** Vì sao trong demo `Same cached Method` là true và `Reflection cache misses` là 1?

**Đáp án:** Hai lookup dùng cùng MethodKey trong một PluginSession, nên lần đầu tạo entry, lần sau nhận lại chính Method instance.

**Câu 11.** TCCL còn là plugin loader cũ trên worker thread sau reload có tác dụng gì?

**Đáp án:** Worker còn sống có thể giữ old loader strongly reachable, dù session cache đã clear. Restore TCCL đúng worker trong finally.

**Câu 12.** Cache Method/MethodHandle của plugin nên tồn tại trong host singleton suốt tiến trình không?

**Đáp án:** Chỉ khi có ownership/eviction rõ ràng và không giữ thế hệ plugin đã hết hạn; mặc định per-plugin session là thiết kế dễ quản lý hơn.

## 22. Ghi nhớ và tài liệu chính thức

**Năm kết luận trọng tâm:**

1. **Cache đúng scope trước khi tối ưu collection**: per-plugin/per-generation cache có strong references là bình thường nếu được clear và không còn root ngoài khi stop.
2. **Sai cache key vừa gây lỗi logic vừa gây leak**: class name không đủ phân biệt defining loader, còn Method/Class token có thể giữ loader cũ.
3. **WeakHashMap không là thuốc chữa tự động**: value mạnh có thể tham chiếu về weak key qua Class<?> hoặc Method.
4. **ClassValue hợp với per-class metadata**, nhưng không thay lifecycle plugin và không xóa root references từ host caches/threads.
5. **Close + clear + unregister + drain tạo cơ hội reclaim**, không bảo đảm class unload ngay; phải quan sát GC roots và Metaspace theo nhiều chu kỳ.

**Nguồn chính thức Java 17:**

- [ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findLoadedClass`, parent delegation.
- [URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): classpath URL, `close` và concurrency caveats.
- [WeakHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html): weak keys, strong values và vòng tham chiếu key/value.
- [ClassValue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassValue.html): `computeValue`, `get`, `remove`.
- [WeakReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html): weak reachability.
- [Reference.reachabilityFence](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html): bảo đảm reachability khi dùng referent.
- [JLS 17 `12.7 — Unloading](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7): điều kiện unload theo defining loader.
- [JVMS 17 `5 — Loading/Linking](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): class identity và loading.
- [Java SE 17 java command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xlog:class+unload` và chẩn đoán JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-performance-006">CDS, AppCDS và giới hạn theo loader hoặc cấu hình chạy</a>

<details>
<summary>Xem chi tiết</summary>

# CDS, AppCDS và giới hạn theo ClassLoader hoặc cấu hình chạy

> **Java Core → ClassLoader → Hiệu năng.** Class Data Sharing (**CDS**) và Application Class Data Sharing (**AppCDS**) là kỹ thuật của HotSpot/OpenJDK nhằm lưu các dạng biểu diễn lớp đã xử lý vào **archive**, để lần khởi động JVM sau có thể nạp chúng nhanh hơn và chia sẻ một phần bộ nhớ metadata giữa các JVM tương thích. Điểm quan trọng trong hệ plugin là: **CDS không đồng nghĩa chỉ bootstrap loader được lợi**, nhưng khả năng archive và tái sử dụng lớp do custom loader nạp còn phụ thuộc **JDK, đường đi nạp class, archive, classpath/module path, JAR và tùy chọn khởi chạy**. Không có cam kết tất cả class của mọi loader đều được archive. Ví dụ Java 17+ ở phần thực hành tạo AppCDS động và kiểm tra bằng JVM logs, kể cả class từ một `URLClassLoader` riêng.

## 1. CDS giải quyết chi phí nào?

Khi khởi động một ứng dụng Java, JVM cần làm nhiều việc:

1. Tìm class theo loader/delegation.
2. Đọc tệp `.class` từ runtime image, directory, JAR hoặc nguồn khác.
3. Phân tích class-file format, kiểm tra cấu trúc và thực hiện những giai đoạn linking/verification theo thời điểm JVM yêu cầu.
4. Tạo và quản lý metadata của lớp.
5. Chạy initialization khi Java semantics yêu cầu, tạo instances và phục vụ logic ứng dụng.

CDS đưa một số kết quả chuẩn bị về class metadata vào archive có thể được JVM **memory-map** trong các lần khởi động sau. Mục tiêu là giảm chi phí đọc/phân tích/thiết lập metadata và có cơ hội chia sẻ các trang dữ liệu phù hợp giữa nhiều JVM processes.

```text
Không dùng application CDS:
   JVM start -> tìm JAR/class -> parse class bytes -> metadata -> run

Dùng AppCDS hợp lệ:
   Trial/training run -> tạo archive *.jsa
   JVM start tiếp theo -> map compatible archive
                        -> dùng archived representation khi eligible
                        -> fallback load bình thường với lớp khác
                        -> run
```

CDS có thể cải thiện **startup time**, và có thể giảm **footprint tổng** khi nhiều JVM cùng dùng các trang archive chia sẻ. Đây **không** phải cách thay thế heap của mọi process, không cache sẵn mọi Java object, không bảo đảm throughput của business logic hoặc peak heap giảm một lượng cụ thể. Chi phí tạo archive và lợi ích phụ thuộc workload, filesystem, OS page cache, JVM build và tập lớp được nạp.

### “Shared class data” không làm mất type identity

Một class runtime vẫn được định danh theo JVM bởi **(binary name, defining loader)**. CDS không cho phép hai custom loaders độc lập trở thành cùng một defining loader, cũng không làm hai `Class<?>` vốn không cùng loader trở thành một object vì chúng từng xuất hiện trong cùng `.jsa`. JVM quản lý các lớp và kiểm tra điều kiện load/archive theo semantics tương thích với Java.

Vì vậy:

- AppCDS là **tối ưu nạp lớp**, không phải mechanism thay đổi thứ tự parent delegation.
- Không thay thế dependency isolation/namespace separation trong hệ plugin.
- Không là cơ chế bảo mật/sandbox cho bytecode không đáng tin.
- Không biến class đã load từ plugin-v1 thành plugin-v2 chỉ vì thay JAR trong lúc JVM còn chạy.

## 2. Phân biệt CDS, AppCDS, archive mặc định và dynamic archive

| Thuật ngữ | Ý nghĩa | Điểm cần nhớ |
| --- | --- | --- |
| **CDS** | Class Data Sharing của JVM, ban đầu phục vụ các lớp runtime nền tảng. | Nhiều JDK phân phối sẵn **default CDS archive**. |
| **AppCDS** | Mở rộng tập lớp eligible để chứa lớp ứng dụng và thư viện. | Có thể tăng lợi ích startup ngoài các core classes. |
| **Default/base archive** | Archive từ JDK distribution hoặc được tạo phù hợp, thường `classes.jsa`. | Thường được JVM dùng mặc định khi có thể. |
| **Static/custom archive** | Archive được build bằng classlist và `-Xshare:dump` hoặc công cụ phù hợp. | Nhiều bước, hợp với pipeline build/train có kiểm soát. |
| **Dynamic/top archive** | Archive bổ sung được tạo trong một trial run, ví dụ `-XX:ArchiveClassesAtExit=app.jsa`. | Có thể nằm **trên** default/base archive, dễ thực hành AppCDS. |
| **AOT cache mới hơn** | Cơ chế tiến hóa từ CDS, mở rộng cache tới class loading/linking và nhiều tối ưu. | JDK 24+ có JEP 483; **không đánh đồng** lệnh AOT với lệnh CDS cổ điển. |

Theo Oracle Java SE 25 java launcher docs, JVM có thể dùng **một base archive và một dynamic archive**. Trong file log, dynamic archive thường được gọi là **top** archive. Nếu chỉ truyền tên `app.jsa` của dynamic archive, header archive chứa liên hệ tới base archive thích hợp; JVM có thể tìm default base.

### CDS mặc định

Trong nhiều bản phân phối OpenJDK/Oracle JDK hiện đại, CDS mặc định đã được bật nếu có archive hợp lệ. Một vị trí thường gặp:

```text
JAVA_HOME/lib/server/classes.jsa      (Linux/macOS, tùy distribution)
JAVA_HOME/bin/server/classes.jsa      (Windows, tùy distribution)
```

Đây là **đường dẫn minh họa**, phải kiểm tra JDK bạn thật sự chạy. Từ JDK 12, Oracle JDK thường phân phối sẵn default archive. Không nên luôn giả định một build JDK tùy chỉnh có default archive hoặc một đường dẫn cố định.

`java -Xshare:auto`: sử dụng CDS khi có thể; đây là hành vi mặc định trong nhiều cấu hình.

`java -Xshare:off`: tắt CDS để thử nghiệm đối chứng.

`java -Xshare:on`: yêu cầu CDS; **nếu không thể dùng archive có thể khiến JVM báo lỗi và thoát**. Theo Oracle launcher, đây là chế độ chẩn đoán, **không phải tùy chọn nên ép trong production**.

`-Xshare` thuộc HotSpot/OpenJDK; các JVM khác có thể có tên option, scope và hỗ trợ khác.

## 3. Vì sao AppCDS không “cache mọi class”?

Tập lớp có thể dùng archive thường là **những lớp được quan sát và đủ điều kiện** trong quá trình tạo archive, với các ràng buộc format/loader/configuration.

Ví dụ:

```text
Ứng dụng có 20.000 class files trong dependencies
Training run chỉ dùng 1.800 classes
Archive có thể chứa subset của 1.800 classes (eligible)
Khởi động production:
   - các lớp khớp và eligible có thể lấy từ archive
   - các lớp khác nạp bình thường
```

Các con số ở đây là **giả định minh họa**, không phải cam kết tỉ lệ coverage. Điều thực sự xảy ra phải được xem qua class-load/ CDS logs.

Một class có thể không được archive hoặc không tái sử dụng khi:

- Trial run chưa kích hoạt code path chứa class đó.
- Class được định nghĩa bằng một custom loader/bytecode transformation không đáp ứng điều kiện của implementation.
- Classpath/JAR/module options thay đổi khiến JVM không tin dùng archive cũ.
- JVM khác phiên bản/build/kiến trúc hoặc archive header không tương thích.
- Loader ở lần chạy tiếp theo dùng nguồn/identity/delegation không phù hợp archived metadata.
- JVM không map được archive và fallback theo chính sách `-Xshare`.
- Một số loại class được tạo động/hidden class hoặc mảng có quy tắc hỗ trợ riêng, tùy release.

**Đừng kết luận rằng mọi lớp được in ra bởi `-XX:DumpLoadedClassList` sẽ chắc chắn được phục hồi từ archive.** Danh sách loaded classes, tập eligible classes và tập classes thực sự **được dùng từ archive** là ba nhóm khác nhau.

## 4. Giới hạn theo ClassLoader: hiểu đúng nhất trong hệ plugin

Phân biệt ba trường hợp:

### A. Bootstrap, platform và application class loaders

Các lớp Java runtime/core thường có nhiều cơ hội sử dụng default CDS archive. Với AppCDS, classes từ system/application loader và platform loader cũng có thể eligible. Đây là đường đi phổ biến của ứng dụng được đóng gói từ JARs.

Nhưng ngay cả với application loader, không nên tuyên bố **mọi class** đều được archive. Nếu class chưa xuất hiện trong trial run hoặc môi trường mismatch, nó vẫn có thể load theo đường bình thường.

### B. Custom URLClassLoader

Một kết luận **sai** là “CDS hoàn toàn không hỗ trợ user-defined class loaders”. Tài liệu Oracle CDS trên Java 24 mô tả AppCDS có thể phục vụ **custom class loaders**. Dynamic archive từ JDK 13 cải thiện hỗ trợ cho use case này.

Một ứng dụng có thể:

```java
URL jar = Path.of("plugin.jar").toUri().toURL();
try (URLClassLoader pluginLoader = new URLClassLoader(
        new URL[]{jar}, ClassLoader.getPlatformClassLoader())) {
    Class<?> plugin = Class.forName(
            "plugin.Greeter", true, pluginLoader);
}
```

Snippet cần `import java.net.URL; import java.net.URLClassLoader; import java.nio.file.Path;` và là phần code trong method. Nó tạo plugin loader riêng. Nếu code path và archive đáp ứng điều kiện, một class do loader ấy định nghĩa **có thể** được lưu/tái sử dụng từ AppCDS.

**Đã kiểm chứng trên OpenJDK 24:** dynamic archive của ví dụ phần 8 chứa lớp `plugin.Greeter`, và `-Xlog:class+load=info` ở lần chạy sau báo:

```text
plugin.Greeter source: shared objects file (top)
```

Dòng được rút gọn bỏ timestamp/decorations, còn chuỗi source là kết quả quan sát thực. Đồng thời chương trình trả `c.getClassLoader() == pluginLoader` là `true`. Archive reuse **không làm mất defining loader**.

### C. Custom loader tải bytes không chuẩn hoặc định nghĩa class động

Một loader có thể đọc bytes từ database/network, tạo bytecode qua ASM, instrument từng class hoặc override `loadClass` để đổi delegation. Với trường hợp này:

- `CodeSource` tùy chỉnh **không phải bảo đảm** class được CDS hỗ trợ.
- Việc cùng binary name ở hai lần chạy chưa đủ để đảm bảo archive class match.
- Có thể cần cơ chế ghi nhận/định danh class và hỗ trợ archive dành riêng cho implementation/JDK release.
- Nếu bytes sinh phụ thuộc thời điểm/random/env, archive reuse có thể không khả thi.
- Nếu phần class chưa archived, JVM thường load theo đường chuẩn; đừng tuyên bố tối ưu bị “hỏng” toàn bộ chỉ vì một class fallback.

**Quy tắc thực hành:** hỏi bằng log **“class X, do loader Y, ở run này đã được lấy từ shared objects file chưa?”**. Không suy đoán theo tên `URLClassLoader` hoặc `SecureClassLoader`.

## 5. Static archive so với dynamic archive: nên dùng khi nào?

### Static dump dựa trên class list

Quy trình Oracle Java launcher điển hình:

```bash
java -Xshare:off -XX:DumpLoadedClassList=app.classlist \
     -cp app.jar demo.CdsDemo
java -Xshare:dump -XX:SharedArchiveFile=app-static.jsa \
     -XX:SharedClassListFile=app.classlist -cp app.jar
java -XX:SharedArchiveFile=app-static.jsa \
     -cp app.jar demo.CdsDemo
```

Đây là **mẫu giải thích flags**, cần ứng dụng/args/dependencies đúng thực tế. Bài tập chính phần 8 chọn **dynamic** để tránh phụ thuộc nội dung classlist và trình tự static dump. Với static CDS, Oracle Java 25 docs yêu cầu đường `-cp` chứa JARs thích hợp khi ghi classlist và tạo archive; các restrictions theo release phải được kiểm tra.

### Dynamic AppCDS

Phương án ít bước hơn:

```bash
java -XX:ArchiveClassesAtExit=app.jsa \
     -cp app.jar demo.CdsDemo
java -XX:SharedArchiveFile=app.jsa \
     -cp app.jar demo.CdsDemo
```

Tại lần chạy thứ nhất, JVM có thể ghi top archive vào `app.jsa` khi quá trình kết thúc bình thường. Tại lần chạy thứ hai, JVM map archive nếu tương thích. Nó không bảo đảm mọi class load trong trial run đều sẽ archive thành công hoặc mọi lần chạy đều có cache hit.

**Chi phí:** trial/dump run có thêm xử lý khi shutdown, tạo tệp archive và tiêu thụ I/O/disk. Theo Oracle, archived form đôi khi **lớn hơn nhiều** so với bytes trong JAR. Không tạo archive mỗi lần khởi động production như thể đây là bước miễn phí.

### Có thể tạo archive từ JVM đang chạy?

Một số JDK hỗ trợ `jcmd <pid> VM.cds static_dump ...` / `dynamic_dump ...`. Dynamic dump bằng `jcmd` có thể cần JVM được khởi động với `-XX:+RecordDynamicDumpInfo`. Cú pháp/capabilities phụ thuộc release; đọc `java`/`jcmd` docs của đúng JDK.

**Không** đọc `jcmd` command minh họa như công thức có thể gọi từ mọi JDK vendor/process; công cụ có thể yêu cầu attach permission, JVM flags và điều kiện run nhất định.

## 6. Giới hạn do classpath, module path và layout JAR

Trong Oracle `java` launcher CDS/AppCDS docs, các restrictions quan trọng gồm:

1. **Đường classpath/module path:** việc tạo/reuse archives cho app classes thường yêu cầu nguồn JAR phù hợp. Non-empty class directories hoặc exploded modules có thể không được hỗ trợ cho archive creation/reuse của cấu hình nhất định. Tránh dùng `-cp out/classes` của IDE để kết luận coverage production tương đương JAR.
2. **Classpath order:** với static/dynamic archive, đường classpath lúc dùng archive phải thỏa điều kiện tương thích với lúc tạo, thường là **bằng hoặc mở rộng từ một prefix** theo quy tắc được tài liệu từng JDK mô tả. Thay đổi thứ tự JAR có thể khiến classpath check thất bại.
3. **Jar contents:** khi JAR được sửa sau archive generation, JVM có thể từ chối archive hoặc bỏ các phần không còn compatible. Đừng reuse archive vô điều kiện sau deploy plugin-v2.
4. **Module path:** chỉ một số layout/module paths được hỗ trợ; exploded modules, patched modules, upgrade module path và các flags nhất định có thể tắt CDS hoặc archived module graph optimization tùy JDK.
5. **JDK build:** archive gắn với runtime build/architecture/format. Nó **không phải JAR portable** có thể tạo trên JDK A rồi áp dụng cho mọi JDK B.
6. **Platform/JVM options:** module resolution options, heap/object layout và các flags ảnh hưởng archive có thể thay khả năng tái sử dụng.
7. **Current working directory/relative paths:** nếu archive/classpath URLs được tạo dựa trên relative paths, thay working directory giữa trial run và production có thể làm không khớp. Ưu tiên đường dẫn/cấu hình triển khai ổn định.

Một nguyên nhân thường gặp: developer tạo `app.jsa` bằng một JDK 24 build local, sau đó đặt trên Linux container JDK 21. Không nên kỳ vọng sử dụng được: **tạo archive với đúng JDK distribution/build/architecture của môi trường đích**, hoặc để pipeline production tạo archive.

**Nhấn mạnh:** các restrictions có khác biệt giữa JDK 17, 21, 24, 25 và 26; dùng docs phù hợp phiên bản. Đừng biến một điều kiện của static dump thành khẳng định “không có class nào từ directory được JVM sử dụng default CDS”, vì default archive vẫn có thể nạp core classes dù app classes không eligible.

## 7. Module options và archive: vì sao thay flags có thể mất lợi ích?

Với modular applications, không chỉ `-cp` mà `--module-path` và các options như `--module`, `--add-modules`, `--enable-native-access` có thể ảnh hưởng việc khớp archive. Oracle Java 25 docs yêu cầu một số tùy chọn module liên quan có giá trị đồng nhất giữa lúc tạo và lúc dùng archive để tối ưu đầy đủ.

Ví dụ:

```bash
java --module-path libs -m m.app/com.example.Main \
     -XX:ArchiveClassesAtExit=module-app.jsa

java --module-path libs -m m.app/com.example.Main \
     -XX:SharedArchiveFile=module-app.jsa
```

Đây là **mẫu cấu hình**, chưa phải chương trình chạy vì phải có modular artifacts thật. Nếu trial run dùng `--add-modules m.analytics` mà production dùng `--add-modules m.reporting`, JVM có thể **không dùng archived full module graph** hoặc bỏ tối ưu tùy class/linking data.

Một số flags như `--upgrade-module-path`, `--patch-module`, `--limit-modules` có thể vô hiệu hóa CDS theo Oracle docs. Không tự giả định một cấu hình build-time chạy với `--patch-module` của test cũng cho archive reuse trong production.

### Custom ModuleLayer

Class loaders được tạo bởi `ModuleLayer.defineModulesWithOneLoader`/`defineModulesWithManyLoaders` có module configurations và mapping riêng. AppCDS có thể tối ưu một số classes tùy cách JVM ghi nhận chúng, nhưng **không hứa toàn bộ class trong mọi layer đều được archive**.

Hỏi:

- Module `X` đã được resolved trong training run chưa?
- Class `Y` có được load qua đúng loader/module path lúc tạo archive không?
- Lúc chạy production, configuration/loader identity/URLs có còn match?
- Class đang xuất hiện trong `-Xlog:class+load` từ shared archive hay từ file/module image?

Không nên sử dụng `--add-reads`/`--add-exports`/`--add-opens` như cách “bật AppCDS”; chúng điều chỉnh JPMS access graph, không phải lệnh tạo archive.

## 8. Thực hành đầy đủ Java 17+: app.jar + plugin.jar + URLClassLoader

Thí nghiệm này tạo một ứng dụng `demo.CdsDemo` trong `app.jar` và một plugin `plugin.Greeter` trong `plugin.jar`. `CdsDemo` dùng `URLClassLoader` với parent là platform loader để định nghĩa class plugin. Khi dùng `-XX:ArchiveClassesAtExit` và sau đó `-XX:SharedArchiveFile`, ta quan sát **JVM logs** để xác định class có từ archived data hay không.

### Cấu trúc thư mục

```text
src/
  demo/CdsDemo.java
  plugin/Greeter.java
classes/
  app/            (javac tạo)
  plugin/         (javac tạo)
app.jar           (jar tạo)
plugin.jar        (jar tạo)
app.jsa           (lần chạy dump tạo)
```

**File `src/demo/CdsDemo.java`:**

```java
package demo;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class CdsDemo {
    public static void main(String[] args) throws Exception {
        System.out.println("main class: "
                + CdsDemo.class.getName());
        System.out.println("main system loader: "
                + (CdsDemo.class.getClassLoader()
                    == ClassLoader.getSystemClassLoader()));

        URL pluginUrl = Path.of("plugin.jar")
                .toAbsolutePath().toUri().toURL();

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{pluginUrl},
                ClassLoader.getPlatformClassLoader())) {

            Class<?> c = Class.forName(
                    "plugin.Greeter", true, loader);

            System.out.println("plugin defining loader: "
                    + (c.getClassLoader() == loader));
            System.out.println("plugin message: "
                    + c.getMethod("greet").invoke(null));
        }
    }
}
```

**File `src/plugin/Greeter.java`:**

```java
package plugin;

public class Greeter {
    public static String greet() {
        return "hello-appcds";
    }
}
```

### Biên dịch bằng JDK 17+ và đóng JAR

Đứng trong thư mục gốc chứa `src`:

```bash
javac --release 17 -d classes/app src/demo/CdsDemo.java
javac --release 17 -d classes/plugin src/plugin/Greeter.java
jar --create --file app.jar -C classes/app .
jar --create --file plugin.jar -C classes/plugin .
```

Lưu ý `plugin.jar` **không được thêm vào application classpath**: chỉ `-cp app.jar` ở các bước sau. Plugin được tìm qua `URLClassLoader` riêng, giúp kiểm tra đúng trường hợp custom defining loader.

### Chạy baseline

```bash
java -cp app.jar demo.CdsDemo
```

**Stdout chính xác trên OpenJDK 24.0.2 (cùng một JVM build đã dùng ở các lần sau):**

```text
main class: demo.CdsDemo
main system loader: true
plugin defining loader: true
plugin message: hello-appcds
```

### Tạo dynamic AppCDS archive

```bash
java -XX:ArchiveClassesAtExit=app.jsa \
     -cp app.jar demo.CdsDemo
```

Lệnh dùng xuống dòng kiểu Bash; PowerShell chạy trên **một dòng**:

```powershell
java -XX:ArchiveClassesAtExit=app.jsa -cp app.jar demo.CdsDemo
```

Sau run, file `app.jsa` xuất hiện khi JVM hỗ trợ/đủ điều kiện và archive creation thành công. **Thí nghiệm trên OpenJDK 24.0.2 đã tạo `app.jsa` thành công**, chương trình vẫn in bốn dòng như baseline.

### Dùng archive và xem CDS logs

```bash
java -Xlog:cds=info -XX:SharedArchiveFile=app.jsa \
     -cp app.jar demo.CdsDemo
```

Trên bản chạy kiểm thử, log gồm các tín hiệu:

```text
[info][cds] Opened archive ...classes.jsa.
[info][cds] Opened archive app.jsa.
```

Đây là **trích đoạn rút gọn bỏ timestamp/đường dẫn cụ thể**, không phải toàn bộ stdout/stderr cố định. Archive base và top được mở; chương trình vẫn trả bốn dòng giống baseline.

### Chứng minh lớp đã được dùng từ archive

```bash
java -Xlog:class+load=info \
     -XX:SharedArchiveFile=app.jsa \
     -cp app.jar demo.CdsDemo
```

Trong test OpenJDK 24, hai log entries rút gọn thành:

```text
demo.CdsDemo source: shared objects file (top)
plugin.Greeter source: shared objects file (top)
```

**Đây mới là bằng chứng class-level cụ thể**, thay vì chỉ thấy file `app.jsa` tồn tại. Đặc biệt, `plugin.Greeter` là lớp do **custom `URLClassLoader`** định nghĩa (theo stdout).

### Đối chứng tắt CDS

```bash
java -Xshare:off -Xlog:class+load=info \
     -cp app.jar demo.CdsDemo
```

Với cùng artifact trong thử nghiệm, class-load logs đã chuyển về nguồn JAR:

```text
demo.CdsDemo source: file:/.../app.jar
plugin.Greeter source: file:/.../plugin.jar
```

Các đường dẫn là **trích đoạn đã lược phần absolute path**; không coi hai dòng này là stdout đầy đủ. Cả hai lượt chạy đều in kết quả chức năng giống nhau: chương trình đúng cả khi có archive lẫn khi không.

### Giải thích kỹ thuật

1. `app.jar` chứa main class và đi qua **system loader**.
2. `plugin.jar` chỉ nằm trong `URLClassLoader` mà chương trình tạo, không thuộc `-cp`.
3. Trong trial run, class plugin được load nên có cơ hội được JVM ghi nhận để archive.
4. Lần chạy tiếp theo cùng JDK/JAR/configuration, các classes eligible có thể được tìm trong top archive; test cho thấy cả `demo.CdsDemo` và `plugin.Greeter` được báo `shared objects file (top)`.
5. `plugin defining loader: true` vẫn đúng: CDS không biến plugin class thành system-loader class.
6. `-Xshare:off` là đối chứng xác nhận Java vẫn hoạt động khi archive không tham gia, còn JVM đọc class từ JAR bytes.

**Kết quả có tính môi trường:** JDK 24.0.2 được kiểm thử. Với JDK khác, custom loader khác hoặc archive mismatch, `plugin.Greeter` có thể fallback hoặc không eligible. Hãy kiểm tra log của **chính JVM và bản triển khai của bạn**, không sao chép log trong bài làm giả định.

## 9. Đọc class-load logs và CDS diagnostics đúng cách

Các lựa chọn thường gặp trên HotSpot:

```text
-Xlog:cds=info
-Xlog:class+load=info
-Xlog:class+load=debug
-Xshare:off
-Xshare:auto
-Xshare:on
```

Ý nghĩa:

- `-Xlog:cds=info`: có mở archive nào, mapping/configuration và các vấn đề cơ bản không?
- `-Xlog:class+load=info`: class **cụ thể** được báo source như `shared objects file`, `shared objects file (top)`, `file:/...jar` hoặc nguồn runtime image.
- `-Xlog:class+load=debug`: thêm thông tin có thể hỗ trợ chẩn đoán, nhưng log dài và version-specific.
- `-Xshare:off`: phép đối chứng tắt CDS.
- `-Xshare:on`: hữu ích để bắt mismatch trong test vì có thể fail startup, **tránh ép production**.

**Không coi `source: shared objects file` của `java.lang.String` là bằng chứng AppCDS cho plugin thành công.** Nó có thể chỉ là **default base archive**; bạn cần tìm chính tên plugin class và nhìn `(top)`/source phù hợp.

Một bộ lọc thực tế:

```bash
java -Xlog:class+load=info -XX:SharedArchiveFile=app.jsa \
     -cp app.jar demo.CdsDemo 2>&1 |
     grep -E 'demo.CdsDemo|plugin.Greeter'
```

Lệnh `grep` là cú pháp Bash/Linux/macOS; trên PowerShell có thể thay bằng `Select-String 'demo.CdsDemo|plugin.Greeter'`. Vì `-Xlog` in rất nhiều classes, lọc giúp xác định đúng loại loader và source. Khi dùng command trên production, logging có thể ảnh hưởng hiệu năng/I/O và tiết lộ đường dẫn triển khai.

### Những điều KHÔNG được suy ra từ logs

- Một class load từ archive không chứng minh **toàn bộ application** nhanh hơn trên production.
- Log `shared objects file` không cho biết mức savings bytes/RSS cho từng class chính xác.
- Có `app.jsa` không chứng minh toàn bộ classes đều load từ top archive.
- Nhiều classes không archived không tự chứng minh CDS tổng thể vô dụng; default JDK classes vẫn có thể hưởng lợi.
- `CodeSource.location` phản ánh provenance metadata của loader, còn class-load log phản ánh JVM source ở lượt load, và archive-use là một lớp thông tin khác. Cần đọc chung thay vì trộn hai khái niệm.

## 10. Lỗi cấu hình thường gặp với plugin framework

| Triệu chứng | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| `app.jsa` có nhưng plugin class vẫn `source: file:/...plugin.jar` | Plugin class chưa archived, loader không eligible, config không khớp, archive chưa được chọn. | Quan sát training path, class-loader behavior, logs lúc dump/use. |
| `app.jsa` không được tạo | Trial run không thoát đúng, sai archive path/quyền ghi, không có compatible base, JDK flags xung đột. | Xem stderr/exit code, kiểm tra base archive và quyền. |
| Chạy JDK mới sau cập nhật JDK thì archive không dùng | JDK build/format mismatch. | Tạo lại archive bằng đúng JDK runtime mới. |
| Cập nhật plugin.jar nhưng vẫn dùng archive cũ | Artifact/path/checksum không còn khớp; JVM có thể từ chối/bỏ archived entries. | Đánh version archive cùng image/artifacts; regenerate archive. |
| IDE `-cp out/classes` thử thành công app nhưng AppCDS ít hiệu quả | Directory layout không giống production JAR/config và có restrictions với archived app classes. | Train từ packaged artifacts giống production. |
| `-Xshare:on` làm service không start | CDS archive không dùng được, và chế độ `on` bắt buộc. | Không dùng `on` trong prod; kiểm tra mismatch, dùng `auto`. |
| Static classlist nhiều nhưng archive không chứa class custom loader | Eligibility/loader/classlist constraints khác nhau. | Thử dynamic archive, kiểm tra logs; đừng đảm bảo mọi loader được support. |
| Parent-first load plugin API từ host thay vì plugin JAR | Loader khác với dự tính; archive không phải nguyên nhân trực tiếp. | Kiểm tra defining loader và delegation. |
| Tắt CDS nhưng startup vẫn chậm | Bottleneck do I/O, DI container, reflection, network, DB, JIT warmup hoặc app init. | Profile startup, tách parse/link và application init. |
| Nhiều archive files chiếm dung lượng | AppCDS tạo bản lưu metadata, có thể lớn hơn class bytes. | Chọn tập class có giá trị, dọn archives theo version. |
| Dùng `--patch-module` khi dump archive | Một số JVM/module options vô hiệu hóa CDS. | Đọc restrictions đúng JDK, bỏ flags không phù hợp trong pipeline. |

Một custom `ClassLoader` **không nên override delegation/defineClass chỉ để ép được archived classes**; làm vậy có thể phá class identity, package sealing, xác thực nguồn hoặc logic plugin. Trước hết kiểm thử CDS của JVM hiện hữu, rồi tối ưu với cơ chế được JDK/framework hỗ trợ.

## 11. Đo hiệu quả CDS/AppCDS có ý nghĩa

Muốn biết AppCDS có đáng đưa vào vận hành, hãy đo ba cấu hình bằng **cùng artifacts và JDK build**:

A. **Không dùng CDS:** `-Xshare:off`.

B. **CDS mặc định:** không truyền `-XX:SharedArchiveFile`, để JVM dùng default base archive nếu có.

C. **AppCDS:** `-XX:SharedArchiveFile=app.jsa` với archive được tạo từ trial run phù hợp.

Mục tiêu đo:

| Chỉ số | Vì sao quan trọng | Cách đo gợi ý |
| --- | --- | --- |
| Startup latency (p50/p95) | AppCDS thường cải thiện phần cold start. | Timestamp ready-to-serve, nhiều lần chạy độc lập. |
| Time-to-first-request | Gần nhu cầu người dùng hơn tổng main duration. | Kích hoạt health/readiness endpoint và đo end-to-end. |
| RSS/PSS của nhiều JVM processes | Xem lợi ích shared pages trong cùng máy. | OS memory tools; tránh chỉ cộng heap sizes. |
| Heap/metaspace memory | Phân tích áp lực bộ nhớ khác nhau. | JFR/jcmd/metrics, hiểu CDS metadata được mmap ngoài heap. |
| Archive size | Chi phí disk và deploy/cache management. | Kích thước `.jsa` và artifact images. |
| Number of actual archived class loads | Đánh giá coverage thay vì đoán. | `-Xlog:class+load=info` đã lọc tên. |

**Không công bố số % cải thiện không đo được.** Một app chủ yếu đợi network/database có thể thấy lợi ích nhỏ dù hàng trăm core classes được share. Một CLI tool khởi động thường xuyên có thể thấy lợi ích đáng kể.

### Quy trình benchmark đơn giản

1. Dùng cùng host, JDK build, JVM flags quan trọng, artifacts và môi trường.
2. Tách chi phí **archive generation** khỏi lần khởi động đo production.
3. Chạy đủ số lần (ví dụ vài chục lần) cho A/B/C; đảo thứ tự để giảm thiên lệch page cache/thermal effects.
4. Nếu cần cold-cache behavior, mô tả chính xác trạng thái OS page cache, không gọi mọi lượt sau run đầu tiên là “cold”.
5. Không bật `-Xlog:class+load` trong đo latency chuẩn nếu logger gây I/O/overhead; dùng logs trong vòng kiểm tra archive coverage riêng.
6. Báo số mẫu, JDK version, archive/JAR hashes, median/p95 và sai số/độ phân tán.
7. Đo nhiều JVM processes song song nếu mục tiêu là footprint chia sẻ; một JVM duy nhất không phản ánh đầy đủ saving cross-process.

Ví dụ lệnh chạy từng cấu hình (đo bằng công cụ ngoài như `time`, `Measure-Command` hoặc hệ telemetry):

```bash
java -Xshare:off -cp app.jar demo.CdsDemo
java -cp app.jar demo.CdsDemo
java -XX:SharedArchiveFile=app.jsa -cp app.jar demo.CdsDemo
```

Các lệnh này **không tự tính benchmark thống kê**. Phần 8 chỉ kiểm chứng chức năng và archive source, không đo tốc độ.

## 12. AppCDS trong plugin hot reload: archive và lifecycle

Trong hệ plugin có thể reload:

```text
Host process
   |
   +-- PluginLoader v1 -> plugin.jar v1
   |        (được dùng trong AppCDS trial run)
   |
   +-- PluginLoader v2 -> plugin.jar v2
            (artifact/bytes khác; không thể giả định dùng archive v1)
```

CDS cache **không** bảo đảm plugin v2 tương thích archived representation của v1. Khi đổi version, hãy xem archive là **artifact cùng vòng đời deploy/JDK image** chứ không phải database vĩnh viễn có thể dùng cho mọi plugin revision.

Cũng cần phân biệt:

- **Đóng loader**: `URLClassLoader.close()` giải phóng file/JAR handles của loader theo API; không ép ngay class unloading.
- **Đỡ load work nhờ archive**: một phần class metadata có thể phục hồi nhanh ở JVM startup hoặc class loading, nhưng class vẫn tuân thủ defining-loader identity.
- **Hot reload trong cùng process**: có thể dùng loader generation mới; archive reuse cho một generation không có nghĩa runtime thay class objects thế hệ cũ.
- **GC/unloading**: phụ thuộc reachability của loader, threads, TCCL, ThreadLocal, host static caches; AppCDS không sửa leaks lifecycle.
- **Security**: archive không sandbox plugin; bytecode không đáng tin vẫn cần boundary phù hợp.

Một hướng vận hành tốt: pin phiên bản JDK/JAR/plugin set, tạo archive như một phần **deployment image có version**, kiểm tra logs khi khởi động, tạo archive lại khi build/JAR thay đổi, và có fallback không CDS nếu môi trường không phù hợp.

## 13. CDS so với JDK 24+ AOT cache: không đánh đồng

Từ **JDK 24**, JEP 483 giới thiệu *Ahead-of-Time Class Loading & Linking*: một bước tiến so với CDS truyền thống, cho phép cache cả những trạng thái nạp/link classes được xây trong training run cho các lần JVM startup kế tiếp. JDK 25 tiếp tục bổ sung cải tiến tạo và khai thác AOT cache (JEP 514, 515).

**Khác biệt về học tập:**

| CDS/AppCDS truyền thống | AOT cache hiện đại |
| --- | --- |
| Tập trung lưu archived class data/metadata để load nhanh hơn. | Có thể lưu thêm class loading/linking state và các tối ưu mở rộng. |
| Thường dùng `.jsa` với `-XX:ArchiveClassesAtExit`, `-XX:SharedArchiveFile`. | Dùng quy trình/flags `-XX:AOT...`, khác theo JDK release. |
| Nền tảng lâu đời và tương thích nhiều JDK 17+. | JDK 24+ và cú pháp tiếp tục phát triển; không copy lệnh JDK 25 vào JDK 17. |
| Không biến loader identity thành một namespace chung. | Cũng phải bảo toàn class/module semantics. |

Đừng nhầm “JDK 24+ khuyến nghị AOT cache cho startup” với “CDS ngừng hoạt động”. Kết quả thực nghiệm của phần 8 trên OpenJDK 24 cho thấy **dynamic CDS vẫn chạy được**. Khi chọn giải pháp production, xem tài liệu JDK và framework thực tế, so sánh profiling và chi phí vận hành.

Nguồn: [JEP 483](https://openjdk.org/jeps/483), [Oracle Java SE 25 java launcher](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html) và [JEP 514](https://openjdk.org/jeps/514).

## 14. Những nhầm lẫn cần tránh

1. **“CDS chỉ giúp bootstrap classes.”** Không đúng tổng quát. AppCDS có thể hỗ trợ application/platform/custom-loader classes, tùy eligibility; test phần 8 xác nhận custom URLClassLoader trên JDK 24.
2. **“Có JAR trên classpath là được archive.”** Không. Class cần được load trong trial run và đáp ứng điều kiện archive.
3. **“AppCDS làm class của loader A và loader B thành cùng Class object.”** Không. Defining loader vẫn là thành phần class identity.
4. **“`getURLs()` chứng minh class được dùng từ archive.”** Không. Nó cho cấu hình search URL; muốn biết archive source phải xem JVM load logs.
5. **“File app.jsa tồn tại nghĩa AppCDS đang hoạt động.”** Không. Có thể mismatch và fallback; kiểm tra `-Xlog:cds` + `-Xlog:class+load`.
6. **“Dùng archive từ JDK build khác vẫn được.”** Không nên giả định; archive gắn với JVM distribution/build/architecture, không portable như JAR.
7. **“Archive từ app v1 phải giúp plugin v2.”** Không chắc; thay JAR/bytes/classpath có thể vô hiệu archived class reuse.
8. **“`-Xshare:on` là best practice production.”** Không; Oracle mô tả là testing option có thể abort startup nếu archive unusable.
9. **“Archive có hàng nghìn classes nghĩa startup chắc nhanh hơn một tỉ lệ cố định.”** Không; phải benchmark workload thực.
10. **“CDS chia sẻ toàn bộ Java heap của nhiều processes.”** Không; JVM processes vẫn có heaps/states riêng, chỉ một phần archived memory metadata có thể chia sẻ.
11. **“Không có class plugin nào archived nên nên bỏ mọi class loader abstraction.”** Không; plugin loaders giải quyết correctness/lifecycle, còn CDS là tối ưu tùy khả năng.
12. **“Dùng `-Xshare:off` sẽ khiến app sai logic.”** Một app không nên phụ thuộc CDS để có logic đúng; nếu khác hành vi, đó là dấu hiệu cần điều tra compatibility/bug.

## 15. Câu hỏi ôn tập

1. **CDS cải thiện chủ yếu phần nào?** Startup/class-loading metadata và memory footprint giữa một số JVMs; không phải mọi runtime CPU operations.
2. **AppCDS thêm gì vào CDS?** Cơ hội archive app/library classes ngoài core runtime classes.
3. **Default base archive khác top/dynamic archive thế nào?** Base thường đi cùng JDK; top chứa lớp bổ sung được ghi sau training run.
4. **Custom `URLClassLoader` có thể dùng archived class không?** Có trong điều kiện phù hợp, phụ thuộc JDK/eligibility; ví dụ OpenJDK 24 đã kiểm chứng.
5. **Một class có `source: shared objects file (top)` nghĩa gì?** JVM báo class được dùng từ top/dynamic shared archive trong lần load.
6. **`CodeSource.location == file:/...plugin.jar` có chứng minh CDS hit?** Không. Nó là metadata nguồn artifact, không phải class-load archive source.
7. **Tại sao training run cần đi qua nhiều chức năng?** Chỉ class được load/eligible trong trial run mới có cơ hội được archive.
8. **JAR thay sau tạo archive thì sao?** Tính tương thích archive có thể mất; nên regenerate cùng artifacts và JDK.
9. **`-Xshare:on` khác `-Xshare:auto`?** `on` yêu cầu sử dụng archive và có thể khiến startup lỗi, `auto` dùng khi có thể.
10. **Tại sao `-Xshare:off` hữu ích?** Làm đối chứng tắt CDS để quan sát class-file loads và đo chênh lệch.
11. **Vì sao không ép non-empty directory trong classpath vào pipeline dump?** Restrictions của CDS/AppCDS theo JVM release/layout; dùng JARs khớp production và xác minh doc.
12. **CDS có chữa leak TCCL/ThreadLocal giữ custom loader không?** Không; đó là bài toán lifecycle/reference.
13. **Nếu `String` được nạp từ `shared objects file` có chứng minh app plugin archived không?** Không; đó có thể là default base archive.
14. **Khi nào nên cân nhắc AOT cache mới hơn?** Trên JDK 24+ khi framework/JVM hỗ trợ và đo cho thấy có lợi; không trộn tùy chọn các phiên bản.

## 16. Tài liệu chính thức

- [Oracle Java SE 25 — java command: Application Class Data Sharing](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): `-Xshare`, `-XX:ArchiveClassesAtExit`, `-XX:SharedArchiveFile`, static/dynamic archives, classpath/module restrictions và AOT.
- [Oracle Java SE 24 — Class Data Sharing](https://docs.oracle.com/en/java/javase/24/vm/class-data-sharing.html): CDS/AppCDS, default archives, mục tiêu startup/footprint, class loaders.
- [Oracle Java SE 17 — java command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): lựa chọn cho Java 17 baseline.
- [dev.java — CDS and AppCDS in HotSpot](https://dev.java/learn/jvm/cps-appcds/): cách tạo static/dynamic archives và giới hạn thực hành.
- [OpenJDK JEP 310 — Application Class-Data Sharing](https://openjdk.org/jeps/310): lịch sử AppCDS.
- [OpenJDK JEP 350 — Dynamic CDS Archives](https://openjdk.org/jeps/350): dynamic archive và custom loader support.
- [OpenJDK JEP 483 — Ahead-of-Time Class Loading & Linking](https://openjdk.org/jeps/483): AOT cache JDK 24.
- [OpenJDK JEP 514 — Ahead-of-Time Command-Line Ergonomics](https://openjdk.org/jeps/514): cải tiến quy trình tạo AOT cache JDK 25.
- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): runtime class identity, defining loader và delegation.
- [Java SE 21 — URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): URL/JAR lookup, `getURLs()` và close behavior.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-performance-007">Đo hiệu năng với cùng JDK, classpath và tập lớp được sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Đo hiệu năng class loading với cùng JDK, classpath và tập lớp được sử dụng

## 1. Mục tiêu và nguyên tắc của một phép so sánh công bằng

Khi chẩn đoán class loader chậm, thời gian khởi động lâu, plugin load chậm hoặc ứng dụng mất nhiều thời gian trước request đầu tiên, rất dễ đo hai lần rồi kết luận “cấu hình A nhanh hơn B”. Nhưng thời gian đo chỉ có ý nghĩa nếu **hai lượt thực hiện cùng loại công việc trên cùng môi trường**, đặc biệt:

1. **Cùng bản JDK/JVM**: vendor, version, build, kiến trúc, chế độ JIT, collector và các JVM flags liên quan.
2. **Cùng classpath/module path**: nội dung artifact, phiên bản dependency, thứ tự các entry, module resolution và cơ chế parent delegation.
3. **Cùng tập lớp thật sự được yêu cầu sử dụng**: cùng tên binary, cùng defining loader theo thiết kế, cùng số lần tạo loader/định nghĩa lớp, cùng yêu cầu initialization và cùng đường đi của ứng dụng.
4. **Cùng điều kiện đo**: JVM process mới hay JVM đã warm-up, CPU/RAM, trạng thái page cache, concurrent load, GC, logging, dữ liệu đầu vào và số lần lặp.
5. **Chỉ thay đổi một yếu tố cần đánh giá** giữa A/B, rồi đối chiếu cả **thời gian** lẫn **bằng chứng class loading thực tế**.

Cần xác định chính xác **đang đo cái gì** trước khi chọn công cụ:

| Phép đo | Bắt đầu → kết thúc | Có thể bao gồm | Điều không được suy ra |
|---|---|---|---|
| **Process startup** | Trước lúc tạo process → thông báo ứng dụng ready | JVM startup, bootstrap, CDS, framework, class loading, I/O | Không tự suy ra riêng class loader mất bao lâu |
| **First-use latency** | Trước lần dùng tính năng → sau lần trả kết quả đầu | Load, verify/link, initialize, JIT, cache, I/O, business logic | Không xem toàn bộ là “class load time” |
| **Per-class resolution/initialization operation** | Trước → sau lần `Class.forName(...)` | Tra cứu loader, có thể load/link/initialize hoặc chỉ tìm class đã có | Không đồng nghĩa chi phí JVM load thuần túy |
| **Steady-state latency/throughput** | Sau warm-up, trong các đợt đo | Code đã nạp, JIT, GC, thread scheduling, logic ứng dụng | Không dùng để khẳng định cold-start nhanh |
| **Class count/metaspace** | Snapshot trước → sau | Số lớp được JVM ghi nhận, bộ nhớ metadata | Không đo thay cho latency; thay đổi counter không gán được cho class mục tiêu |

**Điều kiện kiểm soát quan trọng nhất của bài:** cùng JDK nhưng khác dependency JAR, hoặc cùng JAR nhưng lần A kích hoạt thêm nhiều class, thì kết quả không còn đo cùng một workload. Cần chứng minh **tập lớp được sử dụng tương đương**, chứ không chỉ ghi “classpath trông giống nhau”.

## 2. Class loading thực tế có nhiều lớp chi phí

Phép đo `Class.forName` hay `loader.loadClass` **không tự tách** chi phí đọc JAR, verification, linking, initialization và I/O ứng dụng. JVM có thể trì hoãn symbolic resolution; `Class.forName(name, true, loader)` chủ động yêu cầu initialize, còn `false` thì không. Một lớp đã được nạp thường được tra lại từ cùng loader mà không define class lần nữa.

Để A/B thực sự tương đương, audit riêng:

- **Class identity:** binary name cùng defining loader phù hợp với thiết kế, không chỉ string class name.
- **Search path:** thứ tự JAR, checksum và parent delegation; parent có thể chọn một version khác trước child.
- **Work unit:** số class definitions, lần initialize, resource/provider/bean setup và kết quả nghiệp vụ.
- **JVM state:** fresh process hay warm class/JIT/CDS, OS page cache và số concurrent loaders.

Nếu A tạo một loader cho mỗi request còn B tái sử dụng một loader, chính việc thay đổi **số class definitions** là biến cần báo cáo, chứ không thể gọi hai workload có cùng số lớp nạp. Ngược lại, nếu chỉ đang thử tốc độ classpath lookup, đừng để một variant vô tình load thêm framework classes.

Bằng chứng `ClassLoadingMXBean` là số liệu toàn JVM; hãy kết hợp event logs và defining-loader diagnostics riêng để biết tập class mục tiêu thật sự được xử lý.

## 3. Thiết kế thí nghiệm: giả thuyết, biến và tiêu chí hợp lệ

Trước khi chạy, lập hồ sơ kiểm soát:

| Thành phần | Giữ cố định hoặc ghi rõ |
|---|---|
| JVM | `java -version`, `java -XshowSettings:properties -version`, `java -XX:+PrintFlagsFinal -version` khi cần; vendor/build/architecture |
| Ứng dụng | Commit/build ID; bytes của tất cả `.class`/JAR có thể được nạp |
| Classpath/module path | Danh sách entry **theo đúng thứ tự**, phiên bản và checksum artifact, `--module-path`/`--add-reads`/`--add-opens` nếu dùng |
| Loader topology | Bootstrap/platform/app loader; parent-child delegation; số loader mới mỗi lượt |
| Tập lớp mục tiêu | Tên binary + loader/source và sự kiện load/init; không so chỉ theo tên khi có nhiều loader |
| Workload | Cùng request/entry point/feature flags/input; cùng thao tác bắt buộc và cùng kết quả đúng |
| Runtime state | Cold JVM hay warmed JVM, vòng lặp warm-up, JIT, CDS, GC, memory flags |
| Host | CPU, RAM, load hệ thống, governor/container limits, file cache và I/O |
| Quan sát | Metric, đơn vị, ranh giới timer, số run, số bản phân phối và cách loại lỗi |

Ví dụ giả thuyết hợp lệ: “Với cùng bộ JAR và cùng 60 lớp plugin cần khởi tạo, thay đổi một chính sách tìm resource trong **cùng kiểu lifecycle loader** làm giảm median latency của lần load plugin”. Khi kiểm chứng, nếu log cho thấy A load 60 lớp còn B load 90 lớp, phải điều tra sự khác biệt trước khi nói chênh lệch do riêng chính sách tìm resource.

Ví dụ so sánh **không hợp lệ**:

- A chạy JDK 17, B chạy JDK 21; đồng thời khác CDS và dependency tree.
- A đo JVM lần đầu, B đo lời gọi lại `Class.forName` trong **cùng** JVM khi class đã load và initialize.
- A đo với `-Xlog:class+load=trace` ghi file, B tắt logging.
- A có 3 JAR trong classpath, B có 4 JAR hoặc thay thứ tự.
- A chạy request tạo 10 lazy-loaded classes, B chạy request ít tính năng hơn.
- A báo tỉ lệ nhanh hơn dựa trên **một lần chạy** khi CPU đang không ổn định.

Một so sánh có điều kiện có thể được thiết kế chủ đích khác biệt; cần ghi đó là **biến độc lập** (ví dụ JDK 17 so với 21), và giữ các yếu tố khác cố định. Trong bài này, bài toán yêu cầu **giữ nguyên JDK, classpath và tập lớp sử dụng**.

## 4. Ví dụ Java 17+: đo một thao tác nạp và khởi tạo tập lớp cố định

Ví dụ sau chạy bằng một file source Java, tạo ra ba class lồng nhau và cố định tên chúng. Mục tiêu là có một workload nhỏ, dễ lặp lại và có kết quả kiểm chứng được. Nó **không phải benchmark vi mô chuẩn** cho mọi implementation class loader.

Tạo file **`BenchClassLoad.java`**:

```java
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

public final class BenchClassLoad {
    private static final String[] TARGETS = {
        "BenchClassLoad$Alpha",
        "BenchClassLoad$Beta",
        "BenchClassLoad$Gamma"
    };

    private static final class Alpha {
        static int marker = 11;
    }

    private static final class Beta {
        static int marker = 22;
    }

    private static final class Gamma {
        static int marker = 33;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("Run without arguments");
        }

        // Lấy bean trước khi bắt đầu timer, để giảm nhiễu setup.
        ClassLoadingMXBean mx =
                ManagementFactory.getClassLoadingMXBean();
        ClassLoader loader =
                BenchClassLoad.class.getClassLoader();

        long beforeCount = mx.getTotalLoadedClassCount();
        long startNs = System.nanoTime();

        long signature = 17L;
        for (String name : TARGETS) {
            // true: yêu cầu initialize lớp lần đầu khi thích hợp.
            Class<?> type = Class.forName(name, true, loader);
            signature = signature * 31 + type.getName().length();
        }

        long elapsedNs = System.nanoTime() - startNs;
        long loadedDelta =
                mx.getTotalLoadedClassCount() - beforeCount;

        System.out.println("targets=" + TARGETS.length);
        System.out.println("signature=" + signature);
        System.out.println("duration_ns=" + elapsedNs);
        System.out.println("jvm_loaded_delta=" + loadedDelta);
    }
}
```

Biên dịch một lần trên JDK 17+:

```bash
javac BenchClassLoad.java
```

Chạy nhiều **JVM process độc lập**, luôn từ cùng thư mục và cùng classpath:

```bash
java -cp . BenchClassLoad
java -cp . BenchClassLoad
java -cp . BenchClassLoad
```

Trong mỗi process, hai dòng sau có giá trị đúng như nhau:

```text
targets=3
signature=526276
```

Hai dòng còn lại là **số đo động**. Ví dụ kiểu hiển thị:

```text
duration_ns=<giá trị nanosecond đo được>
jvm_loaded_delta=<số lớp JVM nạp thêm trong khoảng đo>
```

Các dòng trong dấu ngoặc nhọn ở trên là **mô tả trường output**, không phải chuỗi literal do Java in; khi chạy, chúng là các số nguyên thực tế và có thể khác nhau giữa các lượt. Chương trình đo **thời gian thực thi ba lời gọi `Class.forName(..., true, loader)`**, bao gồm chi phí tra cứu, nạp/verify/link/initialize khi chưa thực hiện, chứ **không tách riêng** I/O hay VM define-class time. Số `jvm_loaded_delta` là **toàn JVM**, có thể bao gồm cả lớp hỗ trợ từ JDK được nạp trong khoảng đó; không nhất thiết đúng bằng 3.

Tính `signature` giúp xác nhận đoạn loop dùng đủ ba class đã định danh: với độ dài tên `BenchClassLoad$Alpha` = 20, `...$Beta` = 19, `...$Gamma` = 20, phép cập nhật từ 17 tạo ra kết quả 526276. Nhưng chữ ký này chỉ là **sanity check ở code**, chưa chứng minh nguồn bytes/loader identity của lớp trong ứng dụng phức tạp. Với tập lớp thật, cần kiểm tra class loading log riêng.

### Vì sao mỗi lượt nên là JVM process mới?

Cùng một loader không “load lần đầu” class đã define rồi. Nếu đặt hai vòng đo liên tiếp trong cùng `main`, vòng thứ hai sẽ thường đo **lấy class đã load/initialized** chứ không làm lại công việc của vòng đầu. Muốn đo first-use/class-loading cold theo vòng đời JVM, hãy chạy các process mới, cùng cấu hình. Đừng dùng `System.gc()` để xem như “reset” toàn bộ class đã load; GC không bắt JVM quên những lớp vẫn được giữ bởi loader sống.

Trong ví dụ, JVM mới bảo đảm namespace ứng dụng được tạo lại. Tuy nhiên **OS disk page cache** có thể đã ấm từ lần đầu và JVM startup state vẫn khác giữa process. Vì vậy gọi đây là *fresh JVM* hoặc *first-use per process* chính xác hơn gọi *true cold hardware/disk start*.

### Thu thập nhiều lần chạy

Bash/macOS/Linux, từ thư mục chứa `.class`:

```bash
for i in $(seq 1 20); do
  java -cp . BenchClassLoad
done
```

Nếu chỉ cần lưu các phép đo thời gian:

```bash
for i in $(seq 1 20); do
  java -cp . BenchClassLoad
done | awk -F= '/^duration_ns=/{print $2}' > timings-ns.txt
```

PowerShell (Windows), mỗi iteration cũng tạo JVM mới:

```powershell
1..20 | ForEach-Object {
    java -cp . BenchClassLoad
}
```

Đây là ví dụ **thu thập thô**, chưa có phép so sánh A/B hay suy luận ý nghĩa thống kê. Đối với thí nghiệm có A/B, thực hiện cả hai điều kiện với cùng số process, ngẫu nhiên hóa hoặc xen kẽ thứ tự `A/B/B/A` để giảm thiên lệch do máy nóng lên và các tác động thời gian.

## 5. Xác minh cùng classpath, JDK và class set

### A. Cố định JVM thực sự được dùng

```bash
java -version
javac -version
java -XshowSettings:properties -version
```

Giữ output nguyên văn và ghi `java.home`, `java.version`, `java.vendor`, `os.arch`, JVM flags. Đảm bảo `java` và `javac` không trỏ hai JDK khác nhau do `PATH`/`JAVA_HOME`, CI image hoặc IDE launcher. Trường hợp build JDK khác runtime JDK có thể làm không còn tương thích hoặc khác cách thực thi.

Nếu muốn khảo sát riêng startup theo mặc định, giữ cùng **CDS mode** và cùng cờ `-Xshare`/archive. Bật/tắt CDS giữa hai điều kiện làm công việc class metadata thay đổi; đây là **một biến thí nghiệm khác**. Các cờ `-XX` là implementation-specific, nên phải xác minh trên JVM đang chạy.

### B. Chứng minh classpath không đổi

Với demo, `-cp .` và cùng bộ bốn file `BenchClassLoad.class`, `BenchClassLoad$Alpha.class`, `BenchClassLoad$Beta.class`, `BenchClassLoad$Gamma.class` là cấu hình đầu vào nhỏ nhất. Khi dùng JAR thật:

- Ghi **đường dẫn và thứ tự** từng classpath entry; thứ tự ảnh hưởng khi nhiều JAR chứa cùng tên binary class.
- Ghi version và checksum SHA-256 của JAR/class bytes. Chỉ cùng tên file hay cùng số version trên manifest chưa bảo đảm nội dung giống nhau.
- Nếu dùng Gradle/Maven, phân biệt `compileClasspath`, `runtimeClasspath`, classpath của test, plugin runtime và classpath do launcher tự tạo.
- Nếu dùng `--module-path`, kiểm soát module graph, module descriptors, resolution và tùy chọn `--add-reads`, `--add-opens`.
- Với Spring Boot executable JAR, kiểm tra `BOOT-INF/lib`/`BOOT-INF/classes`; launcher riêng có thể không biểu diễn classpath giống `java -cp ...`.
- Trong hệ thống plugin, cùng JAR nhưng **parent delegation khác** có thể dùng bytes từ loader cha thay cho bản trong plugin. Ghi cả loader topology.

Ví dụ dùng lệnh kiểm tra checksum ngoài Java code:

```bash
sha256sum path/to/dependency.jar
```

Trên Windows PowerShell:

```powershell
Get-FileHash -Algorithm SHA256 path/to/dependency.jar
```

Dòng `path/to/dependency.jar` chỉ là placeholder, thay bằng JAR thực tế. Không thay đổi thứ tự classpath hoặc xóa cache khi đang thu thập số đo trừ khi đó là biến chủ đích của thí nghiệm.

### C. Xác minh tập lớp được sử dụng bằng logging riêng

Trên HotSpot JDK 17+, một **lượt audit độc lập** có thể dùng:

```bash
java -Xlog:class+load=info:file=class-load-%p.log:uptime,level,tags -cp . BenchClassLoad
```

Trong log, tìm ba tên:

```text
BenchClassLoad$Alpha
BenchClassLoad$Beta
BenchClassLoad$Gamma
```

Chúng có thể xuất hiện cùng nhiều lớp Java khác. Kiểm tra **nguồn load**, số dòng phù hợp, thời điểm và loader identity (nếu log/diagnostic command của runtime có cung cấp). Chỉ so ba tên chưa đủ nếu giữa hai điều kiện có thêm 200 class framework hoặc một class được định nghĩa nhiều lần bởi loader khác nhau.

Nếu đang khảo sát khởi tạo:

```bash
java -Xlog:class+load=info,class+init=info:file=class-events-%p.log:uptime,level,tags -cp . BenchClassLoad
```

`class+load` và `class+init` phản ánh hai giai đoạn khác nhau. Dùng cùng kiểu event khi đếm, không ghép tổng loaded của điều kiện A với initialized của B.

**Không dùng lượt bật verbose logging làm lượt đo hiệu năng chính:** ghi log cho từng class tăng CPU/I/O và có thể tác động mạnh đến thời gian. Thực hiện audit để chứng minh workload/class set; sau đó đo timing với logging **giống nhau và tối thiểu** ở cả hai nhánh (thường tắt log class mức info/debug). Nếu bật JFR/profiler, cũng giữ **cùng cấu hình instrument** cho các lượt đem so trực tiếp.

### D. Phân biệt target set với thống kê toàn JVM

Trong ví dụ, `TARGETS` là **tập class chủ đích**. `ClassLoadingMXBean.getTotalLoadedClassCount()` là **tổng class đã load từ khi JVM bắt đầu**; delta trong timer có thể tăng vì lớp trợ giúp khác. `getLoadedClassCount()` là số class hiện load, `getUnloadedClassCount()` là tổng đã unload. Các giá trị này **không định danh class nào, JAR nào hay loader nào**.

Vì vậy nên lưu hai loại bằng chứng:

1. **Danh sách mục tiêu**: các tên/class identity cần được tác vụ sử dụng, số lượt load/initialize dự kiến, kết quả checksum/chức năng.
2. **Dữ liệu process**: counter tổng JVM, log event load/unload, CPU/GC và footprint trong điều kiện đo.

Nếu hai process có delta loaded khác nhau, đừng vội “chuẩn hóa thời gian bằng cách chia cho loaded count”, vì các class có chi phí rất khác nhau, nhiều lớp đã nạp trước timer, và loader có thể có cache. Hãy điều tra lớp khác nhau trước.

## 6. Chọn công cụ theo câu hỏi đo

| Công cụ | Dùng khi nào | Giới hạn |
|---|---|---|
| `System.nanoTime()` | Đo khoảng thời gian elapsed nhỏ, cùng phạm vi rõ | Timer overhead/JIT/scheduling; một mẫu quá nhiễu |
| Java Flight Recorder (JFR) | Quan sát startup, class loading, CPU, allocation, GC, các event được bật | Event/overhead phụ thuộc cấu hình; không phải mọi class event đều mặc định bật |
| `-Xlog:class+load`/`class+init` | Audit lớp thực sự nạp/khởi tạo và nguồn nạp | Logging thêm overhead; output khổng lồ nếu app nhiều class |
| `ClassLoadingMXBean` | Counters loaded/current/unloaded toàn JVM | Không định danh lớp; không phải đo latency |
| `jcmd <pid> VM.classloader_stats` | Snapshot loader/class count tại runtime | Snapshot; bản thân attach/diagnostic có thể có overhead |
| `jcmd <pid> VM.metaspace` | Phân tích memory metadata trong HotSpot | Footprint không tương đương thời gian load |
| JMH | Benchmark **steady-state micro-operations**, có warm-up/forks/measurement được cấu hình | Nếu mục tiêu là first load một lần của JVM, JMH method chạy lặp lại thường đo class **đã load** |
| OS profiler/CPU profiler | Tìm I/O, CPU và method hot trong loading/initialization | Profiling là quan sát có overhead; không thay A/B kiểm soát |

Dùng `java -Xlog:help` để xem tag trên JVM đang chạy. Các lệnh `jcmd` dưới đây là ví dụ đọc snapshot, thay `<pid>` bằng PID JVM thực và cần có quyền attach:

```bash
jcmd <pid> VM.classloader_stats
jcmd <pid> VM.metaspace
```

Khi đo **wall-clock process startup** bên ngoài JVM, nên dùng launcher/script đo từ khi tạo process tới mốc “ready” nhất quán; `System.nanoTime()` bên trong `main` **không bao gồm** giai đoạn JVM bootstrap đã xảy ra trước `main`.

## 7. Warm-up, JIT, CDS, GC và OS page cache

**Warm-up/JIT:** Nếu phép đo là gọi lại một class lookup/API nhiều lần sau khi class đã nạp, chạy warm-up đủ dài và đo steady-state. Dùng JMH với forks phù hợp để giảm nhiễu và tránh JIT loại bỏ phép đo. Nhưng nếu mục tiêu là **load class lần đầu**, mỗi process chỉ có một cold first-use cho cùng loader; việc “warm-up” bằng cách gọi chính class mục tiêu trước sẽ làm mất hiện tượng cần đo. Tách rõ hai loại benchmark này.

**CDS:** Class Data Sharing có thể giảm một phần công việc loading của các lớp phù hợp. Giữ giống nhau trạng thái archive/`-Xshare` và runtime image; không thể quy mọi lợi ích startup cho custom class loader khi CDS thay đổi đồng thời.

**Garbage collection:** Một số lượt có GC gần hoặc trong khoảng timer. Cần so tương quan thời điểm GC với ngoại lệ trong phân phối; không đơn giản xóa mọi lượt chậm vì chúng chậm. Cấu hình heap, collector và allocation pattern phải giống nhau.

**OS cache và I/O:** Khi classpath nằm trên disk, filesystem cache có thể khiến những lần đọc JAR sau nhanh hơn. Muốn mô phỏng startup production, xác định trạng thái cold/warm cache tương ứng và tái hiện công bằng. Không đo lượt A ngay sau reboot và lượt B sau nhiều lần đọc JAR rồi coi đó là hiệu năng JVM A/B.

**CPU scheduling/thermal:** CPU governor, turbo boost, thermal throttling, container quota, dịch vụ nền và tải máy gây nhiễu. Trên CI shared runners, biến thiên có thể lớn; nên chạy nhiều lượt, ghi host metadata và báo phân phối thay vì số duy nhất.

**Class initialization side effects:** `<clinit>` có thể làm I/O, đọc config, init logging hay đăng ký driver. Nếu profile cho thấy thời gian nằm ở static initializer thì tối ưu class loader search sẽ không giải quyết phần lớn độ trễ. Phải đặt timer/phân tích sao cho biết giai đoạn nào đang chiếm chi phí.

## 8. Từ số đo thô tới kết luận

Giả sử thử nghiệm A và B chỉ khác một chính sách tìm class resource; **các con số dưới đây hoàn toàn minh họa, không phải kết quả đo một ứng dụng có thật**:

| Điều kiện | JDK / classpath / target set | Median first-use latency | P95 |
|---|---|---:|---:|
| A | Cùng JDK 17 / cùng hash JAR / 60 lớp mục tiêu | 48 ms | 64 ms |
| B | Cùng JDK 17 / cùng hash JAR / 60 lớp mục tiêu | 41 ms | 58 ms |

Trong ví dụ này, chênh lệch median là `48 - 41 = 7 ms`, tương đương `7 / 48 ≈ 14,6%`. Tuy vậy để kết luận “B cải thiện hiệu năng”, còn phải kiểm tra số run, phân phối, nhiễu hệ thống, JIT, I/O, logging, không có class set mới phát sinh và đúng tính năng trả cùng kết quả.

Báo cáo tốt không chỉ ghi một tỷ lệ phần trăm. Nó nêu:

- **Workload:** thao tác được đo, class names và defining-loader semantics, số loader/class, kết quả chức năng.
- **Setup:** JVM version/vendor, flags, dependency hashes/classpath ordering, module path, machine, cache state.
- **Method:** điểm bắt đầu/kết thúc timer, warm-up hoặc fresh process, số forks/runs, thứ tự A/B và các run lỗi.
- **Kết quả:** median, P95, khoảng phân tán (min/max hoặc IQR), unit, raw data/path log để người khác tái hiện.
- **Bằng chứng phụ:** audit class loading/initialization, GC/counter, nơi phần lớn thời gian tiêu tốn theo profiler.
- **Giới hạn:** tác động JIT/cache còn lại, độ đại diện của synthetic benchmark với workload production.

Nên ưu tiên **median** và percentile trong dữ liệu lệch/phân tán, cùng biểu đồ phân phối theo run nếu cần. Số liệu ít hoặc phân phối chồng lấp mạnh có thể chưa đủ chứng minh thay đổi thật. Với phép đo có overhead instrumentation, so A/B **cùng một bộ công cụ/setting**.

## 9. Các lỗi đo phổ biến và cách sửa

| Sai lầm | Vì sao sai | Cách sửa |
|---|---|---|
| Khác JDK, vendor hoặc JVM flags | Compilation, GC, CDS và runtime behavior khác | Chốt runtime image và lưu `java -version`/flags |
| Cùng tên dependency nhưng JAR khác | Binary được nạp có thể không giống nhau | So SHA-256 và runtime class source |
| Đổi thứ tự classpath | Class cùng tên có thể được tìm ở entry khác | Giữ đúng ordered classpath |
| Quên parent/child loader topology | Cùng class name chưa chắc cùng runtime class | Ghi defining loader, delegation và số loader |
| A load 50 lớp, B load 80 lớp | Khác workload nên latency không cùng ý nghĩa | Audit class set, chức năng và thứ tự |
| Vòng đo đầu và vòng sau cùng process | Class ở vòng hai đã load/initialized | Fresh JVM cho first-use; warm-up riêng cho steady state |
| Tính thời gian `main` là JVM startup | Bootstrap đã xảy ra trước `main` | Đo từ launcher ngoài process tới ready |
| Bật class trace chỉ ở A | Logging thay đổi CPU/I/O | Audit riêng, giống logging ở mọi lượt đo |
| Đọc `getTotalLoadedClassCount()` là số lớp của plugin | Counter chứa toàn JVM | Lọc event theo class/loader hoặc có instrumentation đúng phạm vi |
| Dùng `System.gc()` để “reset” class | GC không unload những lớp còn được loader giữ | Thiết kế vòng đời loader/process phù hợp |
| Chỉ chạy một lần, lấy số nhanh nhất | Nhiễu và cache bias | Lặp nhiều lần, báo median, P95, raw data |
| Chỉ báo “B nhanh hơn 25%” | Không biết tác vụ, runtime và biến thay đổi | Ghi protocol, workload và checksums |
| Tối ưu dựa trên số class giảm bất kỳ | Ít class không tự đồng nghĩa latency thấp hoặc chức năng giống | Kiểm tra mục tiêu và đo trực tiếp latency/CPU |

## 10. Checklist tái lập

Trước khi chấp nhận so sánh A/B:

- [ ] Đã xác định cần đo **process startup**, **first-use** hay **steady state**, ranh giới timer rõ ràng.
- [ ] JDK/JVM vendor, version, build, flags, collector, CDS và kiến trúc **giống nhau**.
- [ ] Classpath/module path **cùng entry, thứ tự và checksum artifact**; class loader topology được ghi.
- [ ] Cùng tập class mục tiêu được gọi, nạp, khởi tạo; kiểm tra bằng log audit riêng.
- [ ] Cùng số lượt loader/class-definition, cùng feature flags và dữ liệu đầu vào.
- [ ] Logging/profiler/JFR được bật cùng cách ở các lượt đo và không làm sai phép so sánh.
- [ ] Trạng thái JVM (fresh/warmed), JIT, page cache, GC, tải CPU/RAM có quy tắc kiểm soát.
- [ ] Có nhiều run độc lập, lưu raw data, median/P95 và đơn vị.
- [ ] Các phép đo có thể được người khác chạy lại với đúng commands và artifacts.
- [ ] Kết luận nói rõ điều **đã đo**, điều **chưa tách riêng được** và phạm vi áp dụng.

## 11. Câu hỏi tự kiểm tra

**1.** Vì sao gọi `Class.forName("X", true, loader)` hai lần liên tiếp trong cùng JVM không tương đương hai phép đo cold loading?  
**Đáp án:** Lần thứ hai có thể chỉ tra cứu lớp đã define và initialize. Muốn first-use cold của cùng defining loader thì cần lifecycle/loader hoặc JVM mới; đồng thời phải giữ workload tương đương.

**2.** Có thể kết luận A nhanh hơn B nếu B vô tình load thêm 100 class framework?  
**Đáp án:** Không thể quy chênh lệch riêng cho thay đổi mong muốn; trước hết cần phân loại nguyên nhân khiến tập lớp thực sử dụng khác nhau.

**3.** Vì sao giữ cùng tên JAR chưa đủ bảo đảm cùng classpath?  
**Đáp án:** Nội dung JAR, thứ tự entry, đường dẫn và loader delegation có thể khác; cần kiểm tra artifact hashes và runtime source.

**4.** Dùng `-Xlog:class+load` để lấy timing benchmark có rủi ro nào?  
**Đáp án:** Logging làm tăng CPU/I/O, làm nhiễu thời gian; nên audit riêng và đo với cùng logging level/cấu hình tối thiểu ở các điều kiện.

**5.** Nếu `jvm_loaded_delta` của chương trình mẫu là 8 thay vì 3, có nghĩa code không chỉ nạp ba class mục tiêu?  
**Đáp án:** Vòng gọi vẫn xác định ba target names; counter toàn JVM có thể bao gồm lớp Java/runtime khác được tải trong khoảng đo. Cần xem class load log để định danh.

## Tài liệu tham khảo

- [Java SE 17 — `Class.forName`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)): phân biệt yêu cầu initialize khi tìm lớp.
- [Java SE 17 — `ClassLoadingMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ClassLoadingMXBean.html): counter class loaded/unloaded toàn JVM.
- [JDK 17 `java` manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xlog`, class load/initialization/unloading tags, cấu hình classpath, `-Xshare`.
- [Java Language Specification 17, chương 12](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html): loading, linking, initialization và unloading.
- [Java Microbenchmark Harness (OpenJDK JMH)](https://github.com/openjdk/jmh): công cụ cho microbenchmark với warm-up, measurement và forks; cần chọn đúng cho câu hỏi steady-state hay cold first-use.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-performance-008">Đánh đổi giữa số loader, mức cô lập và dung lượng metaspace</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh đổi giữa số loader, mức cô lập và dung lượng metaspace

> **Java Core → ClassLoader → Hiệu năng.** Trong kiến trúc plugin, nhiều `ClassLoader` có thể giúp các module dùng dependency khác phiên bản, nạp/hủy riêng từng thế hệ và bảo vệ định danh kiểu giữa các plugin. Nhưng nếu mỗi loader **tự định nghĩa lại hàng nghìn runtime classes**, JVM phải giữ thêm metadata cho những class definitions ấy. Bài học phân tích trade-off giữa **số lượng loader**, **granularity của isolation**, **Metaspace đã dùng/committed/reserved**, **class unloading**, chi phí heap/JIT/I/O và cách benchmark đúng trên Java 17+ (HotSpot). Mục đích là chọn ranh giới loader theo nhu cầu thực tế, không mặc định “một loader luôn tốt” hoặc “càng nhiều loader càng tốt”.

## 1. Ba biến cần cân bằng

Một plugin system thường phải đạt ba mục tiêu:

1. **Cô lập:** tránh xung đột binary names và versions giữa các plugins, có khả năng thay plugin mà không restart toàn JVM.
2. **Bộ nhớ:** giới hạn số classes/metadata trùng lặp, heap caches, JIT-compiled code, class-loading infrastructure và số JAR handles.
3. **Vận hành:** khởi tạo đủ nhanh, shutdown/unload được, định danh shared APIs luôn đúng và chẩn đoán được khi có leak.

Sơ đồ khái niệm:

~~~text
       ÍT LOADER                             NHIỀU LOADER
            |                                      |
  +--------------------+               +------------------------+
  | Loader chung       |               | Loader P1  | Loader P2  |
  | plugin A + B + C   |               | lib 1.0    | lib 2.0    |
  +--------------------+               +------------+-----------+
            |                                      |
  + Ít định nghĩa trùng                     + Isolate versions/ABI
  + Ít overhead loader/JAR                  + Retire P1 độc lập
  - Khó unload A riêng                      - Class metadata có thể trùng
  - Dễ đụng version                        - Thêm loaders, caches, locks,
                                              lifecycle/resource owners
~~~

**Không thể kết luận dung lượng chính xác chỉ từ `loaderCount`.** Cần biết mỗi loader định nghĩa bao nhiêu classes, classes lớn đến đâu, số dependencies shared hay private, có module layer/generated classes hay không và bao nhiêu generations cũ còn reachable.

## 2. Cơ chế gốc: runtime type identity

Theo JVM Specification, một runtime class/interface được xác định bởi **binary name và defining class loader**.

~~~java
Class<?> a = loaderA.loadClass("plugins.Widget");
Class<?> b = loaderB.loadClass("plugins.Widget");
boolean sameType = a == b;
~~~

Đây là snippet minh họa; `sameType` **có thể true** nếu cả A và B delegate tới một parent đã định nghĩa `plugins.Widget`. Nó **false** nếu mỗi loader tự `defineClass` ra `plugins.Widget`. Vì vậy số **loader objects** không bằng số **class definitions**.

### 2.1. Hai trường hợp phải phân biệt

**Trường hợp parent sharing:**

~~~text
               SharedParentLoader
                 define api.Service
                 define common.JsonCodec
                   /        \
          PluginLoaderA   PluginLoaderB
          define AImpl    define BImpl
                   \        /
                   shared API classes
~~~

Với parent-first delegation, các child loaders có thể trả về cùng `api.Service.class` và `common.JsonCodec.class`, nên không cần tự nhân đôi metadata cho **các runtime classes được parent định nghĩa**.

**Trường hợp duplicate private definitions:**

~~~text
         PluginLoaderA                 PluginLoaderB
       define vendor.Parser          define vendor.Parser
       define vendor.Model           define vendor.Model
       define vendor.Util            define vendor.Util

         vendor.Parser@A != vendor.Parser@B
~~~

Mỗi loader có một class definition riêng, static fields riêng và identity khác nhau. VM có thể tái sử dụng hoặc chia sẻ một số dữ liệu ở những cấp khác tùy implementation, nhưng **không xem hai runtime classes như một type duy nhất**, và phải quản lý metadata của từng definition.

**Quan trọng:** Một runtime `Class<?>` nạp qua child delegation vẫn có `getClassLoader()` trỏ về **defining parent**, không phải nhất thiết child phát lệnh `loadClass` (initiating loader). Khi đo duplication, nên đếm **definitions theo defining loaders**, không chỉ số lần `loadClass` được gọi.

## 3. Metaspace chứa gì, khác Java heap thế nào?

Trên HotSpot hiện đại, Metaspace dùng vùng native memory để quản lý nhiều loại **class metadata** (ví dụ representations của classes, methods và constant-pool metadata). Java heap và Metaspace là các miền khác nhau trong phân tích hiệu năng.

| Thành phần | Khu vực/vòng đời khái niệm | Hệ quả khi nhân bản plugin |
| --- | --- | --- |
| Runtime class metadata | HotSpot Metaspace; gắn với class loader data/lifetime | Định nghĩa cùng bytecode ở nhiều loaders có thể tăng metadata |
| Compressed class pointers/class metadata | Có thể nằm trong compressed class space trên HotSpot với cấu hình tương ứng | Có reserved virtual space lớn nhưng committed ít |
| `Class<?>` mirrors, instances, arrays, static object graphs | Heap và runtime VM references | Static cache/instances cũng có thể tăng với mỗi generation |
| Dynamic proxies/lambda-generated classes | Runtime class definitions + heap instances | Có thể tăng cả metadata, JIT/code cache và object counts |
| Compiled methods | Code cache / JIT-managed native memory | Hot methods của từng runtime class có thể cần compilation riêng |
| Loader objects / URLs / cache structures | Java heap | Phụ thuộc loader implementation và số resources |
| `JarFile`/open streams/file handles | VM/OS resources | Phụ thuộc lifecycle và URLClassLoader.close() |
| Threads/executors | Heap + native stacks/OS resources | Plugin per-loader thread pools có thể rất tốn chi phí khác Metaspace |

### 3.1. Ba số khác nhau: used, committed, reserved

Các thống kê HotSpot/JMX thường phân biệt:

- **Used:** dung lượng metadata mà allocator đang dùng theo phép đo tương ứng.
- **Committed:** dung lượng physical/virtual-memory backing đã được commit cho pool; có thể lớn hơn used vì allocator có khoảng trống/quản lý chunks.
- **Reserved:** dải **virtual address space** đã đặt trước để có thể commit về sau; không đồng nghĩa process thực dùng lượng RAM tương đương.

Ví dụ một dòng `reserved = 1 GiB` cho compressed class space **không nói JVM đang dùng 1 GiB RAM cho class metadata**. Phải so used/committed, và với memory pressure kiểm tra thêm process RSS/cgroup, GC/JIT/thread/native allocations.

**Không cộng bừa** JMX Metaspace pool và mọi Native Memory Tracking (NMT) buckets như hai khoản độc lập: các công cụ có thể đang mô tả **cùng** allocations ở mức phân loại/chi tiết khác nhau.

### 3.2. Metaspace không phải PermGen

Java 8 bỏ PermGen và giới thiệu Metaspace trong HotSpot (JEP 122). Java 17/21/25 không nên cấu hình `-XX:MaxPermSize` hay dùng PermGen như cách gọi cho metadata hiện đại. Tuy nhiên “Metaspace” là một đặc điểm **HotSpot JVM implementation**, không phải JLS bắt buộc mọi JVM có pool với đúng tên và layout này.

Bộ nhớ tăng trong plugin system không phải lúc nào cũng ở Metaspace: `static byte[]` lớn tăng **heap**, thread stacks tăng **native memory**, còn JIT compiled code làm tăng **code cache**.

## 4. Mô hình ước lượng: không có số MB cố định cho mỗi loader

Một mô hình tư duy (không phải công thức HotSpot chính thức):

\[
M_{\mathrm{metadata}} \approx M_{\mathrm{shared}} +
  \sum_{i=1}^{N} M_{\mathrm{classes\ defined\ by\ loader}\ i}
  + M_{\mathrm{allocator\ overhead}}
\]

Mỗi `M_classes` phụ thuộc class count, methods/fields/constant pool, invokedynamic/generated classes và tình trạng live. Loader có thể tạo **0** class definitions (toàn bộ delegation); loader khác định nghĩa hàng chục nghìn types.

Ví dụ **để lập luận, không phải benchmark**:

- 10 loaders, mỗi loader chỉ định nghĩa 3 classes riêng và dùng 1.000 classes từ parent: duplication thấp.
- 10 loaders, mỗi loader định nghĩa độc lập 1.000 classes tương tự: có thể có khoảng 10.000 runtime definitions plugin-side thay vì 1.000.
- 100 loaders mà mỗi loader giữ JAR scan caches, URL streams và threads riêng: tăng tổng footprint ở nhiều khu vực khác Metaspace.

**Không được nhân “số loader × 1 MB” rồi công bố là kết quả đo.** Một vài lớp cực lớn có thể tốn hơn nhiều lớp nhỏ; đồng thời allocator fragmentation, GC events và JVM flags khiến used/committed khác nhau.

### 4.1. Sharing nào vẫn khả thi?

ClassLoader delegation giúp **runtime class identity** được chia sẻ ở parent. Nếu plugins cần dùng `api.Plugin` và các DTO ở boundary, chia sẻ chúng qua parent không chỉ tiết kiệm metadata mà còn tránh `ClassCastException` kiểu “X cannot be cast to X”.

Tuy nhiên sharing **không đồng nghĩa** parent phải giữ mọi thư viện. Khi hai plugin yêu cầu binary-incompatible versions của một library, nếu đưa cả library vào một parent-first shared loader, host có thể gặp `NoSuchMethodError`, `ClassNotFoundException`, `IllegalAccessError` hoặc behavior sai. Với dependency version conflict có thật, **isolation đáng trả giá**.

## 5. Các phương án thiết kế và trade-off

| Cấu hình | Isolation | Chi phí bộ nhớ dự kiến | Vòng đời/reload |
| --- | --- | --- | --- |
| Một loader cho host + tất cả plugins | Thấp | Ít duplicated definitions | Khó unload plugin độc lập; thường cần restart |
| Shared parent API + một loader cho mỗi plugin | Tốt giữa implementations | Metadata riêng cho private classes, shared API không nhân bản | Thay/retire từng plugin tương đối rõ |
| Một loader cho mỗi tenant có cùng bộ bytecode | Rất cao giữa tenants | Có thể nhân bản rất nhiều definitions/caches | Tách tenant đời ngắn, nhưng dễ OOM |
| Một loader cho mỗi plugin generation | Tách V1/V2 | **Peak overlap** khi V1 và V2 cùng tồn tại | Phù hợp rolling update; phải drain V1 |
| Group loader cho các plugins tin cậy, dependency tương thích | Trung bình | Giảm duplication trong group | Unload một plugin khó hơn; coupling tăng |
| Một loader mới cho mỗi request | Rất cao nhưng thường không cần | Startup/link/JIT/metadata churn lớn | Hầu như không hợp lý cho service request thông thường |
| Plugin trong process/JVM riêng | Boundary mạnh hơn classloader | Tổng JVM baselines lớn hơn | Quản lý độc lập, có thể giới hạn RAM/CPU theo OS |

Không có một lựa chọn thắng mọi cột. Tùy yêu cầu **bảo mật, ABI, tenant isolation, release cadence, Metaspace budget, latency SLO và độ tin cậy của cleanup**.

### 5.1. Khi nên dùng một loader mỗi plugin?

Hợp lý khi:

- Plugin có dependencies riêng (kể cả versions xung đột).
- Cần hot swap plugin V1/V2 mà không restart ứng dụng.
- Muốn có resource ownership/routing boundaries rõ.
- Số plugins và số class definitions còn trong ngân sách bộ nhớ.
- Shared API types nằm ở loader sống ổn định và được versioning.

Đây là lựa chọn mặc định tốt cho nhiều plugin managers, **nhưng phải đo** với workload và số plugins thật, không coi là “miễn phí”.

### 5.2. Khi nên group loaders?

Có thể group khi nhiều modules dùng **cùng phiên bản dependencies** và phải luôn deploy/retire cùng nhau. Ưu điểm là bớt duplicate classes và JAR scan metadata. Nhược điểm:

- Một group loader là một namespace; không thể nạp hai phiên bản cùng binary name.
- Static singletons và mutable state trong group có thể bị dùng chung ngoài dự kiến.
- Reload một module có thể buộc thay cả group.
- Sai hỏng liên quan class initialization của một type có thể ảnh hưởng nhiều modules trong group.

Chọn group **theo compatibility và lifecycle coupling**, không chỉ theo mong muốn tiết kiệm vài MB.

### 5.3. Một loader mỗi tenant: cẩn thận hệ số nhân

Giả sử hệ SaaS có 500 tenants dùng bytecode plugin giống nhau. Nếu mỗi tenant có loader riêng và định nghĩa lại toàn bộ framework/dependencies, class metadata/caches có thể tăng theo số tenants dù business logic giống hệt.

Nếu không yêu cầu **runtime type identity isolation**, thường tốt hơn:

- Shared application/plugin classes cho các tenants.
- Isolate mutable tenant state ở **objects và request contexts**, không tạo mới ClassLoader.
- Giới hạn quyền ở API/business boundary (authorization/capabilities), không coi ClassLoader là sandbox bảo mật.
- Nếu thật sự cần tenant-level security/process limits, xem xét process/container isolation và measure baseline costs.

**ClassLoader không là security boundary đầy đủ.** Từ JDK 24, Java Security Manager đã bị vô hiệu hóa vĩnh viễn (JEP 486); custom loader không tự ngăn plugin đọc file/network trong cùng process.

## 6. Peak Metaspace khi rolling update và hot reload

Một hệ thống có V1 active và đang chuẩn bị V2:

~~~text
Time ----->
      V1 active metadata
      |===========================|
                        V2 prepare/live metadata
                        |===========================|
                        ^ cutover         ^ V1 có thể GC/unload sau drain

Khoảng OVERLAP:
  V1 metadata + V2 metadata + associated heap/resources
~~~

Ở thời điểm cutover, code V1 có thể vẫn xử lý **in-flight requests**. Nếu V2 được prewarm với nhiều classes, peak Metaspace có thể cao hơn trạng thái ổn định. Trong trường hợp V1 chưa unload vì cache/TCCL/listener/stack còn giữ loader, generations tích lũy và mức dùng tăng qua các lượt reload.

### 6.1. Ví dụ tính capacity không giả số liệu giả

Giả sử đo thực tế:

- `M_live`: committed metadata khi một generation active ổn định.
- `Delta_prepare`: phần committed metadata tăng thêm khi load/prewarm V2.
- `Delta_overlap_other`: heap/code cache/thread/JAR native memory tăng ở overlap.
- `Headroom`: memory margin cho GC/JIT/traffic bursts.

Sau khi xác lập baseline và đo lặp, cần ngân sách ít nhất đủ cho **steady state + peak overlap + margin**, và không vượt giới hạn process/cgroup/host. Nếu các thông số chưa đo, **không** gán số MB tùy ý cho chúng.

### 6.2. Vì sao chỉ `active = newGeneration` không giải phóng V1?

References có thể còn từ:

- Request đang thực thi method của V1.
- `Thread.currentThread().getContextClassLoader()` (TCCL) của pool worker.
- `ThreadLocal` value, static map trong host, reflection `Class/Method/MethodHandle` caches.
- `ServiceLoader` provider caches và DI/framework registries.
- Executor tasks, event listeners, logging/JDBC registrations, JNI references.
- `ModuleLayer` hoặc `ClassLoader` được registry còn giữ.

Phải **quiesce → drain → unregister/close → clear caches/restore TCCL → close loader → release strong references**. `URLClassLoader.close()` giải phóng các JAR/file handles do loader quản lý; không trực tiếp hủy các `Class<?>` đã nạp và không ép JVM class unload.

## 7. Elastic Metaspace và điều không nên hứa

JEP 387 **Elastic Metaspace** (tích hợp từ JDK 16) cải tiến cách HotSpot quản lý và **uncommit memory không còn cần** sau class unloading. Nó giúp một workload tạo/hủy nhiều loader generations có cơ hội trả một phần committed memory về hệ điều hành linh hoạt hơn trước.

Nhưng:

- JEP 387 **không có nghĩa** mọi byte class metadata từ loader cũ sẽ lập tức biến mất sau `close()`.
- Đầu tiên loader cần **eligible for GC**; còn strong roots thì metadata vẫn cần tồn tại.
- JVM/GC có thể chưa chạy unload cycle; allocator có thể giữ chunks để tái sử dụng hoặc còn committed phần đang dùng.
- **Reserved virtual space** có thể lớn và ổn định dù committed/used giảm; không nhầm điều đó với native RAM leak.
- Cấu hình collector, version, flags như `-XX:MetaspaceReclaimStrategy`, compressed class space và OS ảnh hưởng reclamation/metrics; đừng lấy một flag làm thuốc chữa classloader leak.

**Nên tối ưu nguyên nhân** (loader reuse hợp lý, sharing, cleanup) trước khi tăng giới hạn memory. Nếu có retention path từ host, tăng `-XX:MaxMetaspaceSize` chỉ trì hoãn sự cố chứ không xóa root.

## 8. Lab 1 Java 17+: cùng bytecode, hai defining loaders

Bài lab so sánh một loader dùng lại và hai loader thực sự định nghĩa cùng binary name. Chạy Java 17+; **không** đưa plugin JAR lên application classpath.

### 8.1. Cây thư mục

~~~text
loader-tradeoff-lab/
├─ plugin-src/demo/plugin/Action.java
├─ host-src/LoaderIdentityDemo.java
└─ build/
   ├─ plugin/      (tạo khi build)
   ├─ host/
   └─ action.jar
~~~

**`plugin-src/demo/plugin/Action.java`**

~~~java
package demo.plugin;

public final class Action {
    public String name() {
        return "action";
    }
}
~~~

**`host-src/LoaderIdentityDemo.java`**

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class LoaderIdentityDemo {
    public static void main(String[] args) throws Exception {
        URL jar = Path.of("build", "action.jar")
                .toAbsolutePath().toUri().toURL();

        ClassLoader parent = ClassLoader.getPlatformClassLoader();

        try (URLClassLoader shared =
                     new URLClassLoader(new URL[] {jar}, parent);
             URLClassLoader isolatedA =
                     new URLClassLoader(new URL[] {jar}, parent);
             URLClassLoader isolatedB =
                     new URLClassLoader(new URL[] {jar}, parent)) {

            Class<?> shared1 = shared.loadClass("demo.plugin.Action");
            Class<?> shared2 = shared.loadClass("demo.plugin.Action");
            Class<?> separateA = isolatedA.loadClass("demo.plugin.Action");
            Class<?> separateB = isolatedB.loadClass("demo.plugin.Action");

            System.out.println("one loader, same class="
                    + (shared1 == shared2));
            System.out.println("different loaders, same class="
                    + (separateA == separateB));
            System.out.println("same binary name="
                    + separateA.getName().equals(separateB.getName()));
            System.out.println("different defining loaders="
                    + (separateA.getClassLoader()
                        != separateB.getClassLoader()));
        }
    }
}
~~~

### 8.2. Biên dịch và chạy (POSIX shell)

~~~bash
mkdir -p build/plugin build/host

javac --release 17 -d build/plugin \
    plugin-src/demo/plugin/Action.java

jar --create --file build/action.jar -C build/plugin .

javac --release 17 -d build/host \
    host-src/LoaderIdentityDemo.java

java -cp build/host LoaderIdentityDemo
~~~

**Stdout kỳ vọng:**

~~~text
one loader, same class=true
different loaders, same class=false
same binary name=true
different defining loaders=true
~~~

Điều lab **chứng minh**: cùng một binary name có thể chỉ có một runtime type trong shared loader, nhưng có **hai runtime types** khi mỗi isolated loader tự define bytes của JAR. Điều lab **không tự đo**: số byte Metaspace tăng chính xác, thời gian nạp class, throughput hay GC class unloading.

`try-with-resources` đóng loader-owned JAR handles. Các `Class<?>` local variables vẫn tồn tại trong scope, và loader classes có thể tiếp tục sống sau close nếu còn references khác; không khẳng định “đóng xong là trả hết Metaspace”.

### 8.3. Shared API trong thiết kế thực tế

Khi host và plugins cần cast object về một interface chung, **parent phải define shared API**. Một plugin class khác defining loader có thể có cùng tên interface nhưng **không assignable** với interface host nếu child tự define lại. Vì vậy sharing `api.PluginService` vừa tiết kiệm metadata vừa bảo vệ type identity; plugin-specific implementation vẫn nên để child tự define.

## 9. Lab 2 Java 17+: quan sát ClassLoadingMXBean và Metaspace MXBean

Dùng Java Management API đọc được **loaded class count**, số classes đã unload và các memory pools hiện có. Giá trị là runtime observations; **không có stdout số cố định giữa JDK/OS/GC**.

**`host-src/LoaderMetrics.java`**

~~~java
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.MemoryType;

public final class LoaderMetrics {
    public static void main(String[] args) {
        ClassLoadingMXBean classes =
                ManagementFactory.getClassLoadingMXBean();

        System.out.println("loaded-now=" +
                classes.getLoadedClassCount());
        System.out.println("loaded-total=" +
                classes.getTotalLoadedClassCount());
        System.out.println("unloaded-total=" +
                classes.getUnloadedClassCount());

        for (MemoryPoolMXBean pool :
                ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.NON_HEAP) {
                continue;
            }
            MemoryUsage usage = pool.getUsage();
            if (usage == null) {
                continue;
            }
            System.out.printf("%s: used=%d committed=%d max=%d%n",
                    pool.getName(), usage.getUsed(),
                    usage.getCommitted(), usage.getMax());
        }
    }
}
~~~

~~~bash
javac --release 17 -d build/host host-src/LoaderMetrics.java
java -cp build/host LoaderMetrics
~~~

**Không có expected numerical stdout**: tên pools, counts và values khác nhau tùy JVM/startup; `getMax()` trả giới hạn ước tính của pool hoặc `-1` nếu không xác định. **Nhãn `max` là giới hạn pool do `MemoryUsage.getMax()` trả về (hoặc `-1` nếu không xác định), không phải reserved address space.** `MemoryUsage` trả `init, used, committed, max`, **không có trường reserved**.

### 9.1. Kết hợp metrics vào phép so sánh hợp lệ

Thực hành đúng:

1. Chạy workload A với một shared loader trong một JVM riêng; warm up ổn định.
2. Chạy workload B với K isolated loaders trong một JVM khác, cùng JDK/heap/GC flags, cùng set classes đã dùng.
3. Lặp nhiều lần, chụp JMX/class count/Metaspace metrics tại **các mốc tương đương**.
4. Đo cả `System.nanoTime` cho từng phase load/initialization, latency/throughput, CPU, allocations và file handles nếu đây là bottleneck.
5. Với benchmark có nhiều loaders, mỗi class nên thực sự được định nghĩa bởi loader cần test, không vô tình delegate implementation về host.
6. Trừ/so baseline và mô tả jitter; không so hai giá trị đọc liên tiếp mà mặc định tất cả biến động chỉ do loader count.

**Lưu ý:** `ClassLoadingMXBean.getTotalLoadedClassCount()` là cumulative từ khi VM chạy, **không phải số đang sống**; `getLoadedClassCount()` là số currently loaded. Khi đánh giá leak, theo dõi xu hướng **current loaded classes/loaders** và class unloading sau nhiều vòng, không chỉ cumulative count luôn tăng theo tự nhiên.

## 10. Đo bằng jcmd, Native Memory Tracking và logging trên HotSpot

Các công cụ sau được tài liệu JDK 25 mô tả; **support và flags phụ thuộc JVM/distribution**:

~~~bash
# Với PID đã xác định từ jcmd -l:
jcmd <pid> help

# Thống kê class loaders:
jcmd <pid> VM.classloader_stats
jcmd <pid> VM.classloaders

# Metaspace summary và breakdown theo loader (nếu hỗ trợ):
jcmd <pid> VM.metaspace basic
jcmd <pid> VM.metaspace show-loaders

# Native Memory Tracking summary (nếu đã bật khi startup):
jcmd <pid> VM.native_memory summary

# Theo dõi class loading/unloading trong một lần chạy:
java -Xlog:class+load=info,class+unload=info \
    -cp build/host LoaderIdentityDemo
~~~

Đây là **mẫu lệnh chẩn đoán**, thay `<pid>` bằng PID thật và đảm bảo class files của lab có sẵn. Nếu command bị từ chối, chạy `jcmd PID help`/`jcmd PID help VM.metaspace` để xem options của runtime thật.

### 10.1. NMT phải bật trước khi đo

Khởi chạy ứng dụng với:

~~~bash
java -XX:NativeMemoryTracking=summary \
    -cp build/host LoaderIdentityDemo
~~~

Trong process chạy dài, bạn có thể dùng:

~~~bash
jcmd <pid> VM.native_memory baseline
jcmd <pid> VM.native_memory summary.diff
~~~

**Không dùng chuỗi lệnh hai process ngắn để tin rằng PID còn tồn tại.** Lab trên kết thúc nhanh; các lệnh jcmd/NMT phải chạy với JVM sống đủ lâu, hoặc áp dụng vào service thực / harness giữ JVM chờ.

NMT theo dõi **JVM native memory**, không phải toàn bộ allocations của arbitrary native libraries do bên thứ ba tạo. NMT cũng có overhead runtime; không bật `detail` khắp production nếu chưa đo và có nhu cầu rõ. Các nhóm NMT như Class, Thread, Code, GC phản ánh các thành phần khác nhau của footprint.

### 10.2. Một biểu đồ capacity nên gồm gì?

Nếu hệ thống có dashboard, theo dõi ít nhất:

- Active plugin/tenant/generation count và số defining loaders còn live.
- Classes currently loaded, classes loaded/unloaded theo thời gian.
- Metaspace used/committed (và compressed class space nếu có).
- Native memory category Class/Code/Thread, process RSS và cgroup memory.
- Heap used/GC pause times, số JAR file handles, số threads, JIT/code cache.
- Hot reload latency: chuẩn bị generation, cutover, drain, resource close.
- 95th/99th percentile request latency trong lúc load/unload/rollout.
- Rate của `OutOfMemoryError: Metaspace`, class-loader retention và leaked generations.

Cần đánh dấu **deployment/reload events** để giải thích spikes; không coi mọi peak ngay lúc rollout là leak.

## 11. Cách xác minh classloader leak thay vì đoán

Nếu Metaspace tăng qua mỗi lần plugin reload:

~~~text
ClassLoadingMXBean current count tăng
       |
       v
Các generations cũ đã STOP thật chưa?
       |
       +-- No --> còn in-flight tasks/callbacks: hợp lệ hoặc lifecycle lỗi
       |
       +-- Yes --> thấy ClassLoader V1 trong heap dump?
                     |
                     +-- Host static Map -> Class/Method/instance
                     +-- Worker -> TCCL hoặc ThreadLocal value
                     +-- EventBus -> listener/captured lambda
                     +-- ServiceLoader/DI/serializer cache -> provider/type
                     +-- ModuleLayer/driver/JMX registry -> classes/handles
~~~

Các bước:

1. Log generation id và defining loader identity khi load/retire.
2. Quiesce requests, stop executors/timers và unregister listeners.
3. Dọn host caches/ThreadLocal, restore TCCL, close owned streams/JARs.
4. Bỏ references host tới `Class<?>`, `Method`, MethodHandle, instances, layer/loader cũ.
5. Quan sát qua **nhiều vòng GC/class unload**, không đòi một deadline.
6. Nếu generations vẫn reachable, dùng heap dump/dominator tree để tìm **path to GC root**.
7. Đo lại với cùng workload; một lần `System.gc()` không chứng minh có hoặc không có leak.

JLS `12.7` cho phép class unloading khi defining loader có thể reclaimed; không yêu cầu JVM unload ngay. `URLClassLoader.close()` không phải `ClassLoader.unloadClass`. Trên một số JDK, metaspace used có thể giảm nhưng committed/RSS không giảm tức thì vì allocator/OS pages và GC policies.

## 12. Tối ưu kiến trúc trước khi tối ưu flags

Trình tự ưu tiên hợp lý:

1. **Gỡ duplication không cần thiết**: shared parent loader cho APIs/dependencies mà tất cả plugins thật sự dùng cùng version và lifecycle.
2. **Tách đúng private packages**: dùng loader riêng nơi cần version conflict hoặc hot reload.
3. **Giảm số generations đồng thời**: prewarm V2 có giới hạn, drain V1 sớm nhưng không gây lỗi request.
4. **Giới hạn số plugins/tenants trong một JVM** dựa trên profile memory thực, không dựa trên một hằng số suy đoán.
5. **Giảm eager loading và reflection scanning** nếu plugin chỉ dùng một phần lớn số classes; đo latency trước khi lazy load mọi thứ.
6. **Dọn cache/resource roots**: ServiceLoader, DI metadata, Method/MethodHandle caches, TCCL, ThreadLocal, threads, JAR handles.
7. **Benchmark pool/group strategy** trên lượng plugin thật, không giả định một benchmark micro cho biết SLO production.
8. **Sau cùng** mới cân nhắc `-XX:MaxMetaspaceSize`, container limits và reclaim flags trên JVM cụ thể; thử nghiệm với service-level metrics.

**MaxMetaspaceSize** là một giới hạn có thể giúp fail-fast/lập ngân sách trên HotSpot nhưng quá nhỏ gây `OutOfMemoryError: Metaspace`, quá lớn có thể che retention. Đừng tăng vô hạn khi biết rõ classloader leak.

### 12.1. Khi class sharing có thể phá compatibility

Nếu `Plugin A` cần `lib.JsonParser` version 1, `Plugin B` cần version 2 và cả hai không binary-compatible, ép chúng cùng dùng một parent class `lib.JsonParser` có thể làm một plugin chạy sai. Đổi lại, nạp riêng library trong A và B chiếm thêm metadata nhưng **đảm bảo chính sách type identity riêng**, miễn là public host boundary không expose private library types.

Giải pháp đúng là chia:
- `api.*` và DTO/shared contracts: **shared parent**.
- `plugin.impl.*` và private versioned dependencies: **child**.
- Không chuyển class phụ thuộc API giữa loaders bằng cast theo tên; dùng shared interface hoặc serialization/schema rõ ràng.
- Nếu custom child-first, limit theo packages, bảo vệ `java.*` và shared contract; việc tự viết `loadClass` đòi locking/parent-delegation chính xác.

## 13. So sánh isolation theo mục tiêu thực sự

| Yêu cầu | Cần riêng ClassLoader? | Thay thế/cân nhắc |
| --- | --- | --- |
| Hai phiên bản cùng binary name trong một JVM | Thường **có** | Process riêng hoặc relocation nếu phù hợp |
| Hot reload độc lập từng plugin | Thường **có**, loader thế hệ mới | Restart JVM khi thay trọn application |
| Mỗi tenant có thông tin cấu hình riêng | **Không nhất thiết** | Separate instances/config context/authorization |
| Cách ly quyền filesystem/network với code không tin cậy | **Không đủ** | Process/container/OS boundary |
| Cần unloading một phần ứng dụng | Loader scope theo lifecycle giúp đạt được | Không giữ references, GC eligibility |
| Giảm tối đa class metadata cho thư viện chung | Shared loader hữu ích nếu phiên bản tương thích | CDS/archiving tùy JDK nhưng phải đo, không thay class identity |
| Rút ngắn startup plugin | Có thể giảm eager scanning/prewarm | Đo class loading costs và lazy init phù hợp |
| Tách crash của native code | ClassLoader không đủ | Process isolation |
| Tối ưu throughput nhiều plugin | Phụ thuộc workload | Locking, pool use, JIT, GC, bytecode generation |

**Kết luận thiết kế:** “Cách ly càng sâu” càng nên được chứng minh bằng requirement rõ ràng. Isolate **state** bằng objects khi chỉ cần nhiều tenants dùng một phiên bản code; isolate **code/version** bằng loaders khi thật sự có nhiều runtime definitions; isolate **trust/resource failure** bằng process/OS boundaries khi cần security và fault containment.

## 14. Bài tập tự kiểm tra

1. Hai `URLClassLoader` cùng URL và cùng parent sẽ **luôn** có hai runtime classes khi gọi `loadClass("X")`? **Không**; nếu parent define X, cả hai có thể delegate cùng class.
2. Hai loaders tự define cùng binary name có phải một runtime type không? **Không**, định danh gồm defining loader.
3. Số loader objects bằng số class definitions? **Không**, một loader có thể define nhiều, ít hoặc zero classes.
4. Nhiều loaders tự define cùng library có thể tăng Metaspace không? **Có**, vì metadata của runtime definitions không giống một shared Class.
5. Thêm loader có luôn tốn một số MB cố định? **Không**; footprint tùy definitions, allocator, dependencies.
6. `reserved=1GiB` có đồng nghĩa JVM giữ 1GiB RAM thực trong Metaspace không? **Không**; xem used/committed.
7. Dùng nhiều loaders có thể giúp unload một plugin mà không restart JVM không? **Có thể**, khi isolation và lifecycle/root cleanup đúng.
8. `URLClassLoader.close()` lập tức unload classes? **Không**.
9. `System.gc()` bảo đảm class unload? **Không**.
10. V1 và V2 cùng tồn tại lúc rollout có thể tăng peak Metaspace? **Có**, nếu cả hai generations live.
11. Một `Method` trong host static cache có thể giữ plugin loader V1 không? **Có**.
12. Static field của plugin chỉ tự tham chiếu trong unreachable cycle có nhất thiết pin loader không? **Không**.
13. NMT `summary` dùng để quan sát gì? **Các nhóm JVM native memory**, không phải mọi allocations native của thư viện bên ngoài.
14. `ClassLoadingMXBean.getTotalLoadedClassCount()` là số đang loaded? **Không**, là cumulative kể từ JVM startup.
15. `getLoadedClassCount()` và `getUnloadedClassCount()` khác gì? **Currently live và cumulative unloaded**.
16. Một loader mỗi tenant khi mọi tenant dùng cùng code có luôn tốt hơn shared classes? **Không**, có thể nhân metadata/caches không cần thiết.
17. Dùng một loader cho plugin A và B khác dependency version có thể gặp gì? **Binary compatibility/class name conflict**.
18. Tại sao `MaxMetaspaceSize` không nên tăng trước khi tìm leak? **Chỉ trì hoãn áp lực bộ nhớ nếu retention path còn nguyên**.
19. JEP 387 có làm loader đang pinned tự unload được không? **Không**, trước hết phải giải phóng strong roots.
20. ClassLoader có đủ để cô lập plugin không đáng tin ở mức OS permission không? **Không**.

## 15. Checklist review trước khi đưa vào production

- [ ] Biết **số defining class loaders và class definitions**, không chỉ số object `ClassLoader`.
- [ ] Phân loại shared API, versioned dependency và private implementation packages.
- [ ] Có giới hạn tối đa active plugins/tenants/generations theo capacity test.
- [ ] Đo Metaspace **used/committed** và không nhầm reserved với RSS.
- [ ] Đo heap, thread stacks, code cache, JAR handles bên cạnh metadata.
- [ ] Load tests cover steady state, burst plugin startup và V1/V2 overlap.
- [ ] Có shutdown protocol cho loader generation: quiesce, drain, unregister, close, release.
- [ ] Có test class identity `SharedApi.class == pluginVisibleSharedApiClass`.
- [ ] Không còn TCCL/ThreadLocal/reflection cache giữ V1 sau retirement.
- [ ] Có dashboard classloader stats/class unload events theo generation.
- [ ] Kiểm tra JVM vendor/version/GC và tác động của NMT/diagnostic flags.
- [ ] Khi OOM Metaspace, kiểm tra loader retention trước khi nâng memory limit.
- [ ] Không dùng ClassLoader làm security sandbox cho untrusted code.
- [ ] Ghi rõ trade-off group loader vs per-plugin loader vs process isolation trong quyết định kiến trúc.

## 16. Nguồn tham khảo

1. [JVMS `5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): định danh runtime class theo binary name và defining loader.
2. [JLS `12.7 — Unloading of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.7): điều kiện GC eligibility trước khi class unloading.
3. [ClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `loadClass`, `defineClass`, `findLoadedClass`.
4. [URLClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/URLClassLoader.html): close và giới hạn của loader/resource lifecycle.
5. [JEP 387 — Elastic Metaspace](https://openjdk.org/jeps/387): cải thiện chunk allocation/uncommit memory khi class metadata được giải phóng.
6. [JEP 122 — Remove the Permanent Generation](https://openjdk.org/jeps/122): PermGen → Metaspace.
7. [ClassLoadingMXBean — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.management/java/lang/management/ClassLoadingMXBean.html): currently loaded, total loaded và total unloaded counts.
8. [MemoryPoolMXBean — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.management/java/lang/management/MemoryPoolMXBean.html): đo pool used/committed/max.
9. [The jcmd Command — JDK 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html): `VM.classloader_stats`, `VM.classloaders`, `VM.metaspace`, `VM.native_memory`.
10. [Native Memory Tracking — JDK 25 Troubleshooting](https://docs.oracle.com/en/java/javase/25/troubleshoot/diagnostic-tools.html): NMT categories, baseline/diff và overhead.
11. [JEP 486 — Permanently Disable the Security Manager](https://openjdk.org/jeps/486): vì sao ClassLoader không thay sandbox.
12. [ManagementFactory — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.management/java/lang/management/ManagementFactory.html): truy vấn class-loading và memory-pool MXBeans.

**Ghi nhớ:** Tăng số loader không **tự động** tương đương tăng Metaspace theo một hệ số cố định; chi phí đến từ **những runtime class definitions, allocator/metadata và resources thực sự sống**. Chia sẻ các types tương thích qua parent để giảm nhân bản, dùng loader riêng cho nơi thật sự cần version isolation/hot reload, và đo **peak overlap, used/committed, classes loaded/unloaded và strong roots** thay vì suy từ loader count hay reserved virtual memory.


</details>

- [Quay lại đầu trang](#back-to-top)
