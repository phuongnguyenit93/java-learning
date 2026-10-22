<a id="back-to-top"></a>

# Cấu hình tài nguyên

## Menu
- [Nhận diện đúng JDK, nhà cung cấp, phiên bản VM và kiến trúc đang chạy](#resource-configuration-001)
- [Tùy chọn chuẩn, -X, -XX và cách xác nhận cờ được hỗ trợ](#resource-configuration-002)
- [-Xms và -Xmx: dung lượng heap ban đầu, tối đa và chi phí đánh đổi](#resource-configuration-003)
- [-Xss, số luồng và ngân sách bộ nhớ ngoài heap](#resource-configuration-004)
- [Giới hạn Metaspace, direct memory và code cache theo nhu cầu quan sát](#resource-configuration-005)
- [JVM ergonomics: lựa chọn mặc định theo môi trường và tài nguyên](#resource-configuration-006)
- [Container: giới hạn CPU, bộ nhớ và khoảng dự phòng cho toàn tiến trình](#resource-configuration-007)
- [Heap còn chỗ nhưng tiến trình thiếu bộ nhớ: native memory và giới hạn hệ điều hành](#resource-configuration-008)
- [Quy trình điều chỉnh từng giả thuyết, đo lại và lưu cấu hình có thể tái lập](#resource-configuration-009)
- [Nâng cấp JDK: kiểm tra cờ bị đổi, bị xóa và thay đổi mặc định](#resource-configuration-010)

## <a id="resource-configuration-001">Nhận diện đúng JDK, nhà cung cấp, phiên bản VM và kiến trúc đang chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện đúng JDK, nhà cung cấp, phiên bản VM và kiến trúc đang chạy

## 1. Bài toán: “máy này cài Java 17” chưa trả lời được điều gì?

Khi cấu hình tài nguyên, chọn bộ thu gom rác, chạy chương trình cần JNI, điều tra một lỗi triển khai hoặc tối ưu HotSpot, câu hỏi cần trả lời không chỉ là “máy có Java hay không”. Cần biết chính xác **tiến trình Java đang chạy thật sự dùng runtime nào**, **nhà cung cấp nào**, **VM implementation/build nào** và **kiến trúc nào**.

Có ít nhất bốn lớp dễ bị lẫn:

1. **JDK dùng để build**: `javac`, `jar` và các công cụ mà CI/IDE đã gọi.
2. **Java launcher dùng để khởi chạy**: file thực thi `java` được giải quyết qua đường dẫn tuyệt đối, `PATH`, wrapper hoặc container entrypoint.
3. **JVM đang thực thi application process**: thông tin thật do `Runtime.version()`, `System` properties và `jcmd` của **đúng PID** báo.
4. **Host/OS/container/native ABI**: nơi tiến trình đang chạy, giới hạn CPU/memory, kiến trúc thực tế của binary và thư viện native.

Bốn lớp có thể **không trùng nhau**. Ví dụ:

```text
Developer laptop:
  JAVA_HOME = /opt/jdk17
  PATH      = /usr/local/bin:/opt/jdk21/bin:...

Build pipeline:
  ./gradlew chạy bằng JDK 21 nhưng --release 17 khi compile

Production container:
  /opt/runtime/bin/java là custom JDK 17 image
  host kernel báo x86_64, container giới hạn 2 CPUs

Ứng dụng:
  System.getProperty("java.vendor") và
  System.getProperty("java.vm.name") đến từ process runtime
```

Không lấy giá trị `JAVA_HOME` của shell hiện tại để khẳng định process khác đang dùng đúng runtime ấy; không nhìn `javac -version` rồi suy version JVM của service; không nhìn `uname -m` trên host rồi khẳng định architecture của mọi JVM trong container.

**Mục tiêu của bài**: nhận diện chính xác runtime identity, phân biệt **Java feature release / update / build**, **vendor / VM name / VM version**, **OS/CPU architecture / process bitness / native ABI**, đọc cấu hình của **đúng process**, và xây một checklist điều tra có thể tái lập trên **Java 17+**.

---

## 2. Bộ câu hỏi tối thiểu phải trả lời trước khi tuning

Trước khi áp dụng `-Xmx`, `-Xss`, `-XX:+UseG1GC` hay mở JNI library, ghi rõ:

| Câu hỏi | Nguồn thường dùng | Vì sao quan trọng? |
| --- | --- | --- |
| JVM đang dùng Java feature bao nhiêu? | `Runtime.version().feature()`, `java.version` | Kiểm tra APIs/class-file compatibility |
| Chính xác release/update/build nào? | `Runtime.version()`, `java -version` | Vá lỗi, compiler/GC behavior và sự cố vendor-specific |
| Ai phân phối runtime? | `java.vendor`, `java.vendor.url` | Hỗ trợ, security updates, distribution |
| JVM implementation nào? | `java.vm.name`, `java.vm.vendor` | HotSpot/OpenJ9 hay implementation khác có option khác |
| Phiên bản VM implementation? | `java.vm.version`, `jcmd PID VM.version` | Diagnostics và flags phụ thuộc build |
| Runtime được cài ở đâu? | `java.home`, executable command/path | Dò launcher hay thư viện đi kèm đúng runtime |
| Runtime process là kiến trúc gì? | `os.arch` cộng OS binary inspection | Chọn đúng JNI native lib, CPU-specific features |
| Runtime có phải 64-bit? | VM output, binary/ABI tools, HotSpot-specific `sun.arch.data.model` | Native pointer width, address space, memory limits |
| Host OS và container ra sao? | `os.name`, container/OS tools, `-XshowSettings:system` (khi hỗ trợ) | CPU quota, memory limits và Linux cgroup |
| Effective VM flags là gì? | `jcmd PID VM.flags`, `-XX:+PrintFlagsFinal` | Các ergonomic defaults có thể khác options khai báo |
| Java executable và javac có cùng JDK? | Absolute paths, `java -version`/`javac -version` | Tránh build đúng nhưng deploy sai |
| Đúng PID ứng dụng đang quan tâm chưa? | PID + `VM.command_line` + `VM.version` | Một máy có thể chạy nhiều Java services |

Chụp thông tin theo **process và thời điểm thực tế**, nhất là sau rolling deployment hoặc khi pods được thay thế.

## 3. Java release, patch, build: đừng chỉ đọc “17”

**Java feature version** là đời ngôn ngữ/API lớn như 8, 11, 17, 21. Các bản cập nhật của cùng feature release có thể khác đáng kể về bug fixes, security patches, hỗ trợ CPU/OS và HotSpot behavior.

Từ Java 10, version string được giải thích theo các thành phần:

```text
$FEATURE.$INTERIM.$UPDATE.$PATCH
       -<PRE_RELEASE>+<BUILD>-<OPTIONAL>
```

Những thành phần zero ở cuối thường được lược bỏ; nhãn prerelease/build/optional metadata phụ thuộc release và vendor.

Ví dụ **minh họa định dạng**, không phải khẳng định đó là bản mới nhất:

```text
17.0.12+7
  feature = 17
  interim = 0
  update  = 12
  patch   = 0 (vắng mặt trong string)
  build   = 7
```

Với Java 17:

```java
Runtime.Version v = Runtime.version();
int feature = v.feature();
int interim = v.interim();
int update = v.update();
int patch = v.patch();
java.util.Optional<Integer> build = v.build();
```

Đây là **đoạn API minh họa**; file chạy độc lập ở mục 10. `Runtime.Version` cũng cung cấp thông tin về pre-release/optional metadata. Không tự phân tích version string bằng `split("\\.")` rồi giả định mọi build chỉ có ba số thuần: có thể còn dấu `+`, prerelease tag hoặc vendor-specific metadata.

### 3.1. Vì sao version feature không đủ để đánh giá compatibility?

- **Class files:** Java 17 có major class-file version **61**. Runtime Java 11 không thực thi được class major 61 theo spec thông thường.
- **API:** `javac --release 17` kiểm soát source/target/public API compatibility cho Java 17, nhưng không biến native library của kiến trúc này chạy trên kiến trúc khác.
- **JVM options:** hai HotSpot 17 patch builds có thể khác behavior/diagnostics hỗ trợ hoặc default heuristics.
- **Binary archive:** một CDS archive tạo từ JDK build A không được coi là portable sang JDK build B chỉ vì đều in “17”.
- **Security:** một runtime cũ và một runtime mới cùng Java 17 có thể khác security fixes.

### 3.2. `java.version`, `java.runtime.version` và `java.vm.version` khác nhau

| Property | Ý nghĩa chính | Không nên suy ra |
| --- | --- | --- |
| `java.version` | Version của Java Runtime Environment | Không phải toàn bộ identity của JVM binary |
| `java.runtime.version` | Version string của Java runtime nếu implementation cung cấp | Không nhất thiết là same string với JVM version |
| `java.vm.version` | Version của VM implementation | Không tự xác định Java launcher path |
| `java.specification.version` | Java SE runtime specification feature version | Không gồm vendor/patch/build |
| `java.vm.specification.version` | JVM specification version | Không bằng JIT compiler implementation version |
| `java.class.version` | Highest class-file format version runtime báo hỗ trợ | Không có nghĩa từng class đang tải đều đã compile với version ấy |

**Lưu ý:** JDK 17 `System` API mô tả các chuẩn system properties, nhưng một số property mang tính implementation-specific, optional hoặc có thể bị code/lệnh chạy thay đổi. Kết luận quan trọng nên **đối chiếu ít nhất hai nguồn**, ví dụ `java -version`, `System` runtime properties và `jcmd PID VM.version`.

### 3.3. Không nhầm JVM specification vendor với runtime vendor

Ví dụ `java.vm.specification.vendor` mô tả **bên ban hành JVM specification**; nó không nhất thiết là nhà cung cấp binary HotSpot mà bạn đang chạy. Để xác định distribution/vendor, ưu tiên đọc `java.vendor`, và đối chiếu `java.vm.vendor`, `java.vm.name` và startup banner.

## 4. Vendor, distribution, implementation và “VM name”

**Vendor/distribution** trả lời “ai xây dựng/phân phối/support runtime mà bạn cài?”. **Implementation** trả lời “bộ máy JVM nào thực thi bytecode/JIT/GC?”. Một vendor có thể phân phối nhiều dạng runtime; ngược lại nhiều vendors cùng xây dựa trên OpenJDK/HotSpot.

| Thành phần | Ví dụ minh họa | Điều cần nhớ |
| --- | --- | --- |
| JDK distribution/vendor | Oracle JDK, Eclipse Temurin, Amazon Corretto, Azul Zulu, Microsoft Build of OpenJDK | Vendor không tự xác định GC/VM implementation |
| VM implementation | HotSpot, Eclipse OpenJ9, GraalVM Java runtime dựa trên HotSpot trong các cấu hình | Diagnostics/flags có thể khác |
| `java.vm.name` | Có thể báo `OpenJDK 64-Bit Server VM` hoặc dạng vendor-specific | Chuỗi không phải tiêu chuẩn định danh duy nhất |
| `java.vm.vendor` | VM implementation vendor/distributor metadata | Có thể khác `java.vendor` hoặc cùng giá trị |
| `java.vm.version` | VM build/version | Không suy “VM build number = JDK patch number” |
| `java.home` | Directory của Java runtime hiện tại | Không phải `JAVA_HOME` của build shell |
| `java.vendor.version` | Vendor version, nếu cung cấp | Có thể vắng mặt (`null`) |

**Tên “OpenJDK” không có nghĩa vendor duy nhất**. OpenJDK là dự án mã nguồn và nhiều distributions được xây từ nó. “OpenJDK 64-Bit Server VM” mô tả một loại VM; vendor ở field khác.

### 4.1. Vendor string có ổn định như một enum không?

Không. Vendor tên có thể thay đổi theo distribution/build hoặc cách đóng gói; là **chuỗi nhận diện**, không phải enum chuẩn có cố định toàn bộ các hãng.

Không viết logic production:

```java
if (System.getProperty("java.vendor").equals("Oracle Corporation")) {
    // Sai khi coi mọi implementation khác chắc chắn không hỗ trợ API Java 17.
}
```

Đoạn trên là **phản ví dụ**. Nếu cần Java API version, dùng `Runtime.version().feature()` và feature-detection hợp lý. Nếu cần một **HotSpot-only flag**, kiểm tra **VM implementation + actual flag availability**; đừng giả định chỉ một vendor mới có HotSpot.

### 4.2. “JDK 17” không đảm bảo VM là HotSpot

Một số Java 17 distributions có thể sử dụng VM implementation khác HotSpot. Những tools/options như `-XX:+PrintFlagsFinal`, Native Memory Tracking, `jcmd` subcommands và HotSpot code cache/G1 internals không phải Java SE portable API.

Nếu `java.vm.name` là OpenJ9 hoặc một VM khác, dùng **manual đúng implementation** thay vì áp một bộ HotSpot diagnostics và kết luận “JVM bị lỗi vì không nhận flag”.

## 5. JDK vs JRE/runtime image vs Java launcher vs `javac`

Trước đây người học hay phân biệt JDK/JRE, nhưng với JDK 17 bạn còn có **custom runtime image** được xây bằng `jlink`. Ứng dụng có thể chạy bằng một runtime image **không có `javac`**, dù Java 17 runtime đầy đủ cho modules ứng dụng cần.

```text
JDK đầy đủ:
  bin/java
  bin/javac
  bin/jar
  bin/jcmd
  ... modules/tools

Custom runtime (jlink):
  bin/java
  modules được chọn
  có thể không chứa javac/jcmd
```

**`java.home`** trong process chỉ ra runtime installation directory thực tế mà JVM báo. Việc nó không có compiler `javac` không chứng minh “không phải Java 17” hoặc “runtime hỏng”.

### 5.1. Các lệnh cần chạy trên đúng máy

**Linux/macOS (POSIX shell):**

```bash
command -v java
command -v javac
type -a java
type -a javac
java -version
javac -version
printf '%s\n' "$JAVA_HOME"
"$JAVA_HOME/bin/java" -version
```

Các lệnh dùng `"$JAVA_HOME/bin/java"` giả định `JAVA_HOME` **đã được thiết lập đúng và trỏ tới JDK hợp lệ**; nếu biến rỗng/không tồn tại, lệnh sẽ thất bại. `type -a` là tiện ích thường có trong Bash, không phải tính năng portable của mọi POSIX shells. Trên macOS, `readlink -f` không phải luôn sẵn có; đừng chép hướng dẫn Linux symlink resolution sang macOS mà không kiểm tra.

**Windows PowerShell:**

```powershell
Get-Command java -All
Get-Command javac -All
java -version
javac -version
$env:JAVA_HOME
& "$env:JAVA_HOME\bin\java.exe" -version
```

Cần phân biệt **PowerShell PATH resolution**, function/alias/wrapper và đường dẫn binary cuối cùng. Một shell có thể gọi script shim, package manager launcher hoặc symlink chứ không nhất thiết `java.exe` trực tiếp.

### 5.2. Vì sao `java -version` và `javac -version` có thể khác?

- `PATH` ưu tiên binary `java` của JDK A, còn `javac` của JDK B.
- Build tools dùng Java Toolchains và chọn riêng compiler của JDK khác.
- IDE dùng project SDK hoặc boot JDK riêng shell.
- Maven/Gradle daemon đã khởi động từ trước với một JDK khác.
- CI worker đã cấu hình `JAVA_HOME` sau khi daemon/service khởi động.
- Runtime image chỉ có Java launcher, compiler đến từ JDK khác.
- Container runtime image khác builder stage.

Nếu `javac` thuộc JDK 21 còn `java` thuộc JDK 17, lệnh compile mặc định tạo class version mới hơn có thể gây `UnsupportedClassVersionError` lúc chạy. Dùng `javac --release 17` để target Java 17 và **test trên chính runtime target**, không chỉ nhìn toolchain tên “Java”.

### 5.3. `java -version` không phải `java --version` của mọi JVM cổ

Trên Java 17 launcher, cả `-version` và `--version` đều được hỗ trợ. Một số hướng dẫn cũ dành cho Java 8 có syntax khác; xác định JDK trước khi áp flags hiện đại.

**Lưu ý output stream:** `java -version` truyền thống thường ghi banner ra **standard error**, còn `java --version` được thiết kế để in version ra standard output trong launcher phổ biến. Không xây pipeline phụ thuộc stdout của một variant nếu chưa kiểm tra shell redirection.

## 6. Architecture: host, process, JVM bitness, CPU và native ABI

**`os.arch` là tên kiến trúc được Java runtime báo**, ví dụ thường gặp `amd64`, `x86_64`, `aarch64` hay `arm64` tùy implementation/platform. Nó là **một tín hiệu về runtime environment**, không phải mô tả đầy đủ của CPU host, tập lệnh CPU thực tế hay layout của mọi thư viện native.

```text
                     Physical machine
                      architecture
                            │
                   OS kernel / hypervisor
                            │
             container / VM / process namespace
                            │
                   executable architecture
                            │
                 JVM process data model
                            │
                 os.arch / java.vm.name
                            │
               JNI native libraries (ABI)
```

Các giá trị có thể trùng nhau trong hệ thông thường, nhưng không được mặc định điều đó trong mọi trường hợp cross-arch emulation, Rosetta, WoW64, multi-arch containers hoặc remote development.

### 6.1. `os.arch` không phải `uname -m` trong mọi môi trường

- `System.getProperty("os.arch")` là giá trị JVM cung cấp cho **process runtime**.
- `uname -m` thường là công cụ shell hỏi về kernel/machine architecture; trong virtualized/emulated environments có thể khác điều JVM báo.
- OS/container image có thể là một architecture cụ thể trong khi developer host là architecture khác.
- CPU có thể hỗ trợ extension như AVX nhưng `os.arch` thường không cho biết toàn bộ extension flags.
- Chuỗi `amd64` và `x86_64` thường biểu thị cùng họ kiến trúc trong nhiều hệ sinh thái, nhưng không thể dùng simple string comparison làm chứng nhận native library compatibility.

**Nếu triển khai JNI/JNA**, cần chọn native binary đúng **OS + instruction set + process bitness + ABI + C runtime/linker requirements**, không chỉ trùng tên `os.arch`.

### 6.2. 32-bit vs 64-bit: ba phép đo khác nhau

1. **Java primitive `long` luôn 64-bit**, bất kể JVM là 32-bit hay 64-bit. `int` luôn 32-bit theo Java language specification.
2. **Native pointer size** thuộc process ABI; trên một JVM 64-bit thông thường có pointers 64-bit, nhưng **Compressed Oops** có thể dùng compressed references nội bộ.
3. **`sun.arch.data.model`** thường trả `32` hoặc `64` trong **HotSpot-related implementations**, nhưng là **implementation-specific property**, không được coi là Java SE standard portable API.

```java
System.out.println(Integer.SIZE);  // 32
System.out.println(Long.SIZE);     // 64
```

Đây là **snippet**; nó chứng minh width của Java primitive types, **không chứng minh** bitness của JVM process.

### 6.3. Những nguồn kiểm tra native architecture đáng tin hơn khi cần

**Linux:**

```bash
uname -m
file /path/to/java
readelf -h /path/to/java
```

`readelf` cần gói binutils và ELF executable; `file` có thể theo symlink nếu thêm option phù hợp. Trên macOS:

```bash
uname -m
file /path/to/java
lipo -info /path/to/java
```

`lipo` chỉ phù hợp với Mach-O/universal binaries. Trên Windows dùng PowerShell/system tools để kiểm tra **OS architecture, process architecture và PE executable architecture**; các API/công cụ cụ thể phụ thuộc Windows version và môi trường.

Không suy process bitness chỉ từ RAM size, heap `-Xmx` hay Java primitive sizes.

### 6.4. CPU architecture ảnh hưởng những quyết định JVM nào?

- Lựa chọn native JNI libraries `.so`/`.dll`/`.dylib`.
- CPU-specific JIT intrinsics/vector instructions và khả năng hỗ trợ hardware.
- Default thread stack size và memory layout theo platform.
- HotSpot ports và tập JVM flags có hỗ trợ hay không.
- Container multi-arch image selection, cross-build.
- Performance baseline: cùng code Java có thể có tốc độ khác trên x64/ARM64.
- CDS/custom VM archive compatibility và native-image artifacts.

Architecture không quyết định duy nhất GC latency; CPU quota, memory speed, JDK implementation, workload và compiler policy còn ảnh hưởng lớn.

## 7. Java System properties: đọc đúng nhưng không tuyệt đối hóa

Java SE 17 có bộ thuộc tính tiêu chuẩn/được runtime định nghĩa qua `System.getProperty(...)`. Chúng rất tiện cho **thông tin tiến trình đang chạy**, song có giới hạn:

- System properties là các giá trị **có thể đọc và một số trường hợp có thể ghi lại** bằng `System.setProperty` hoặc `-Dkey=value`; đừng lấy một string làm chứng thực chống giả mạo.
- Không phải mọi property mà tutorial trên mạng nêu đều là chuẩn Java SE.
- Vendor-specific properties có thể thiếu hoặc đổi tên.
- `java.home` chỉ ra runtime home, nhưng quá trình launch từ wrapper có thể che nguồn executable ngoài JVM.
- `os.arch` mô tả JVM environment, không chứng nhận mọi CPU feature/native ABI.
- Thu thập property list cần tránh đưa secrets/cấu hình nhạy cảm vào logs.

### 7.1. Nhóm nhận diện cốt lõi

| Property | Dùng để biết | Cẩn trọng |
| --- | --- | --- |
| `java.version` | Java runtime version | Phân biệt feature và full build |
| `java.runtime.version` | Runtime build string nếu có | Có thể khác `java.vm.version` |
| `java.vendor` | Java runtime vendor | Không suy VM implementation |
| `java.vendor.version` | Vendor-specific optional version | Có thể null |
| `java.home` | Runtime installation location | Không bằng shell JAVA_HOME bắt buộc |
| `java.vm.name` | VM implementation name | Không phải một enum portable |
| `java.vm.vendor` | Vendor VM | Có thể khác java.vendor |
| `java.vm.version` | VM implementation version | So cùng PID |
| `java.vm.info` | Một số HotSpot VMs báo build/mode info | Không phải portable Java SE property |
| `java.specification.version` | Spec feature version | Không chứa patch/security build |
| `java.vm.specification.version` | VM specification version | Khác implementation version |
| `java.class.version` | Max class-file version runtime nhận | Java 17 thường `61.0` |
| `os.name` | OS name | Không tự mô tả distro/kernel |
| `os.arch` | JVM-reported architecture | Không thay native ABI test |
| `os.version` | OS version property | Trong containers, host kernel và userspace khác |
| `sun.arch.data.model` | HotSpot-family-specific 32/64 model | Optional/nonportable; có thể thiếu |
| `java.class.path` | Java classpath system property | Có thể không mô tả đầy đủ module layer/custom loader |
| `java.library.path` | Native library search path | Không đảm bảo library ABI tương thích |
| `jdk.module.main` | Tên main module trên một số modular launches | Có thể null; không luôn có |
| `user.dir` | Current working directory | Không chứng minh JDK installation |

### 7.2. Không in mọi `System.getProperties()` trong production log

`System.getProperties()` có thể bao gồm classpath, user directories và những system properties do ứng dụng gắn chứa passwords/tokens/endpoint nội bộ. Một diagnostic tool in mọi property ra log sẽ tăng rủi ro lộ bí mật.

Ưu tiên **allowlist các trường nhận diện**. Khi dùng `-XshowSettings:properties` hoặc `jcmd PID VM.system_properties` trên môi trường production, hạn chế quyền truy cập và nơi lưu output.

## 8. `java -version`, `-XshowSettings` và `-XX:+PrintFlagsFinal`

### 8.1. Lệnh launcher

```bash
java -version
java --version
```

Thường cung cấp Java release, runtime distribution và VM banner. Ví dụ định dạng **minh họa**, không phải output chính xác ở mọi build:

```text
openjdk version "17.0.x" ...
OpenJDK Runtime Environment ...
OpenJDK 64-Bit Server VM ... (mixed mode, sharing)
```

“mixed mode” thường liên quan interpreter và JIT compiled execution; “sharing” có thể chỉ CDS support/use theo HotSpot banner. **Không lấy banner là bằng chứng duy nhất** về flags/GC mode đang dùng, đặc biệt trên VM implementation khác.

### 8.2. In các system properties của JVM đang khởi chạy

```bash
java -XshowSettings:properties -version
```

Có thể tìm:

```text
java.home = ...
java.runtime.version = ...
java.vendor = ...
java.version = ...
java.vm.name = ...
java.vm.vendor = ...
java.vm.version = ...
os.arch = ...
os.name = ...
sun.arch.data.model = ...  (nếu build có)
```

Đây là **hình thức hiển thị minh họa**, giá trị thực phụ thuộc môi trường; các dòng có thể xuất hiện ở standard error và theo format của launcher. Dùng `-XshowSettings:vm` để xem một số VM settings; đừng suy nó tương đương dump toàn bộ flags.

### 8.3. Cấu hình effective flags

```bash
java -XX:+PrintFlagsFinal -version
```

Trên HotSpot, lệnh có thể in rất nhiều flags, bao gồm ergonomic defaults do VM tự chọn. Một cờ có thể được:

- Đặt bằng command line.
- Đặt qua environment/wrapper.
- Chọn bởi ergonomics dựa trên CPU/heap/container settings.
- Có default khác giữa JDK versions.
- Không tồn tại trên VM implementation khác.

Vì vậy chỉ nhìn Dockerfile có dòng `-Xmx2g` không đủ để biết **mọi effective JVM flags** của process. Cần xem process thực đang chạy và runtime tooling.

**Cẩn trọng output:** `PrintFlagsFinal` rất dài, phần lớn không liên quan bài toán; lọc đúng flags và version thay vì dump vào issue tracker chứa bí mật.

### 8.4. Đọc thông tin container trên Linux

Với HotSpot Java 17 được hỗ trợ, launcher có:

```bash
java -XshowSettings:system -version
```

Trên **Linux**, category `system` cung cấp thông tin host hoặc container configuration theo implementation; trên OS khác có thể không hỗ trợ hoặc hiển thị khác.

Dùng để kiểm tra container-related CPU/memory limits **khi hoạt động**. Không dùng nó để thay toàn bộ cgroup tools khi cần forensic diagnosis; VM chỉ báo những gì nó thấy và hỗ trợ theo JDK update/build.

## 9. Nhận diện đúng process với `jcmd` trên HotSpot

Nếu cần điều tra một service đang chạy, **đừng mở terminal rồi chỉ gọi `java -version`**: lệnh đó có thể chạy binary khác với JVM process của service. Hãy hỏi chính **PID** ấy bằng công cụ phù hợp.

Trên HotSpot JDK 17:

```bash
jcmd -l
jcmd <pid> VM.version
jcmd <pid> VM.command_line
jcmd <pid> VM.system_properties
jcmd <pid> VM.flags
jcmd <pid> VM.info
```

`<pid>` là placeholder phải thay bằng PID hợp lệ. Không chép nguyên dấu `<`/`>` vào shell; đó không phải cú pháp PID.

- **`VM.version`:** thông tin phiên bản JVM đích.
- **`VM.command_line`:** cách JVM đích được khởi chạy, hỗ trợ đối chiếu options.
- **`VM.system_properties`:** system properties **trong JVM đích** (có thể có dữ liệu nhạy cảm).
- **`VM.flags`:** các JVM flags đang có hiệu lực theo khả năng của VM.
- **`VM.info`:** thông tin VM/environment/status mở rộng.
- **`jcmd -l`:** liệt kê JVM processes mà công cụ có thể nhìn thấy/attach phù hợp.

Diagnostic commands phụ thuộc runtime implementation/build và quyền thực thi. Cần cùng host/namespace thích hợp, OS permissions và khả năng attach; process có thể ngăn attach hoặc đã chết.

### 9.1. PID và container namespaces

Khi app chạy trong container:

```text
Host PID:            12345 (ví dụ)
Container PID:           1 (ví dụ)
JVM process namespace:   phụ thuộc container runtime
```

`jcmd` chạy trên host và `jcmd` chạy trong container **có thể thấy PID khác**. Đừng gửi `jcmd 1` trên host rồi nghĩ chắc chắn đang nói với PID 1 của container. Dùng `docker exec`/`kubectl exec` với đúng target container nếu quyền/hệ thống cho phép, hoặc map PID đúng qua các công cụ container/OS.

### 9.2. Lệnh `jcmd` không nhất thiết có trong runtime image

Custom image từ `jlink` hoặc container minimal có thể chỉ chứa `bin/java`, không có `jcmd`. Cần tool image cùng runtime family/version và quyền attach phù hợp, hoặc ghi allowlisted runtime identity ở startup. **Không cài lại JDK vào production container chỉ để có một lần `jcmd` nếu việc đó làm đổi runtime và phá tính reproducible**.

### 9.3. Công cụ đang chạy khác công cụ attach

`jcmd` từ JDK A có thể không hoàn toàn tương thích/quyền với JVM B. Kết quả đáng tin nhất là dùng tools từ cùng distribution/build hoặc bộ công cụ được nhà cung cấp hỗ trợ để quan sát process đích.

### 9.4. VM flags không phải Java language specification

`-XX:+UseG1GC` là HotSpot option, không phải Java source-level feature. `-Xmx` là launcher option phổ biến, nhưng interpretation và collector choices có implementation-specific dimensions. Nếu `java.vm.name` báo implementation khác, tra tài liệu đúng VM trước khi dùng các `-XX` flags.

## 10. Java 17 runnable demo: in “runtime fingerprint” của chính process

Lưu file **`RuntimeFingerprint.java`** sau đây. Đây là chương trình độc lập, có thể biên dịch bằng JDK 17. Nó in những fields cố định của Java 17 cùng những fields **phải thu thập từ máy thật**, thay vì bịa vendor/CPU/path.

```java
import java.lang.Runtime;
import java.lang.System;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuntimeFingerprint {
    public static void main(String[] args) {
        Runtime.Version version = Runtime.version();

        System.out.println("javaFeature=" + version.feature());
        System.out.println("java17=" + (version.feature() == 17));
        System.out.println("classFormat=" +
                System.getProperty("java.class.version"));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("java.version", System.getProperty("java.version"));
        fields.put("java.runtime.version",
                System.getProperty("java.runtime.version"));
        fields.put("java.vendor", System.getProperty("java.vendor"));
        fields.put("java.home", System.getProperty("java.home"));
        fields.put("java.vm.name", System.getProperty("java.vm.name"));
        fields.put("java.vm.vendor", System.getProperty("java.vm.vendor"));
        fields.put("java.vm.version", System.getProperty("java.vm.version"));
        fields.put("os.name", System.getProperty("os.name"));
        fields.put("os.arch", System.getProperty("os.arch"));
        fields.put("os.version", System.getProperty("os.version"));
        fields.put("sun.arch.data.model",
                System.getProperty("sun.arch.data.model", "<absent>"));

        for (Map.Entry<String, String> item : fields.entrySet()) {
            System.out.println(item.getKey() + "=" + item.getValue());
        }

        System.out.println("pid=" + ProcessHandle.current().pid());
        System.out.println("logicalProcessors=" +
                Runtime.getRuntime().availableProcessors());
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 RuntimeFingerprint.java
java RuntimeFingerprint
```

**Phần đầu stdout xác định khi chạy trên Java 17 chuẩn:**

```text
javaFeature=17
java17=true
classFormat=61.0
```

**Các dòng sau thay đổi theo môi trường**; ví dụ dưới đây là **hình thức diễn giải**, không phải output được đo thật:

```text
java.version=<runtime-release>
java.runtime.version=<runtime-build>
java.vendor=<runtime-vendor>
java.home=<runtime-home>
java.vm.name=<vm-implementation-name>
java.vm.vendor=<vm-vendor>
java.vm.version=<vm-build>
os.name=<os-name>
os.arch=<jvm-reported-architecture>
os.version=<os-version>
sun.arch.data.model=<value-or-absent>
pid=<process-id>
logicalProcessors=<count-visible-to-runtime>
```

Những chuỗi trong ngoặc nhọn là placeholder giải thích các dòng **biến thiên**; không nên dán lại làm evidence. Khi tự chạy, **đọc giá trị thật**.

**Giải thích quan trọng:**

1. `Runtime.version()` mô tả Java runtime hiện tại, không phải `javac` của một process build khác.
2. `java.vm.name`/`java.vm.vendor` cho biết VM implementation metadata, không chỉ Java specification.
3. `java.home` là path runtime của JVM hiện tại. Nó có thể là một custom `jlink` image và khác shell `JAVA_HOME`.
4. `os.arch` là giá trị runtime báo về architecture, cần đối chiếu native binary khi lựa chọn JNI/ABI.
5. `sun.arch.data.model` là **nonportable property**. Default `<absent>` cho biết không có property; code không giả sử JVM nào cũng có.
6. `pid` là PID theo process namespace được Java/OS cung cấp, không phải một số ổn định giữa lần chạy.
7. `availableProcessors()` báo số logical processors JVM nhìn thấy theo implementation và giới hạn môi trường; không chắc bằng số core vật lý trên host.
8. `classFormat=61.0` là class-file format version mà JVM Java 17 báo hỗ trợ; không phải thông tin compile version của tất cả các libraries đã load.

### 10.1. Vì sao không in `System.getenv()` toàn bộ?

Environment có thể chứa tokens, passwords, URLs nội bộ hoặc thông tin không nên đưa vào console/log. Trong demo, chỉ dùng các system properties liên quan runtime identity. Nếu cần `JAVA_HOME`, hãy ghi **đường dẫn cho phép chia sẻ** sau khi đã xác minh đó không phải secret/nhạy cảm.

### 10.2. Kiểm tra version patch/build một cách có cấu trúc

Bổ sung snippet sau vào `main` nếu muốn xem thông tin chi tiết:

```java
Runtime.Version version = Runtime.version();
System.out.println("feature=" + version.feature());
System.out.println("interim=" + version.interim());
System.out.println("update=" + version.update());
System.out.println("patch=" + version.patch());
System.out.println("build=" + version.build().orElse(-1));
```

Đoạn này là **snippet**, không phải file độc lập; `-1` là giá trị tự chọn để biểu diễn build metadata vắng mặt, **không phải build number thật**. Dùng API thay regex bóc tách chuỗi `java.version`.

## 11. Java 17 runnable demo thứ hai: feature gate có kết quả xác định

Một chương trình không nên yêu cầu đúng vendor nếu chỉ cần Java language/runtime feature. Tạo file **`FeatureGate.java`**:

```java
public final class FeatureGate {
    public static void main(String[] args) {
        int required = 17;
        int current = Runtime.version().feature();
        boolean supported = current >= required;

        System.out.println("requiredFeature=" + required);
        System.out.println("runtimeFeature=" + current);
        System.out.println("supported=" + supported);
    }
}
```

Biên dịch/chạy trên JDK 17:

```bash
javac --release 17 FeatureGate.java
java FeatureGate
```

**Stdout chính xác khi runtime đang là Java 17:**

```text
requiredFeature=17
runtimeFeature=17
supported=true
```

Trên Java 21, `supported=true` nhưng `runtimeFeature=21`. Đây không phải lỗi: kiểm tra yêu cầu **Java ít nhất 17**, không phải “chỉ Java 17 mới đúng”. Nếu app thực sự chỉ được certify trên Java 17, hãy thay policy thành `current == 17` và document/test như một **compatibility matrix** riêng — không nhầm policy release với API support.

### 11.1. Feature gate không kiểm tra JNI architecture

`supported=true` **không chứng minh** native libraries tương thích. Một Java 17 trên ARM64 và Java 17 trên x64 đều có feature=17 nhưng JNI `.so` không thể xem là interchangeable. Native binaries phải được kiểm tra OS/arch/ABI độc lập.

## 12. Java 17 runnable demo thứ ba: nhắc distinction giữa primitives và VM bitness

Tạo file **`PrimitiveVsArchitecture.java`**:

```java
public final class PrimitiveVsArchitecture {
    public static void main(String[] args) {
        System.out.println("intBits=" + Integer.SIZE);
        System.out.println("longBits=" + Long.SIZE);
        System.out.println("charBits=" + Character.SIZE);
        System.out.println("doubleBits=" + Double.SIZE);

        String arch = System.getProperty("os.arch", "unknown");
        System.out.println("architectureReported=" + arch);
    }
}
```

Biên dịch:

```bash
javac --release 17 PrimitiveVsArchitecture.java
java PrimitiveVsArchitecture
```

**Bốn dòng stdout đầu là bất biến theo Java primitive types:**

```text
intBits=32
longBits=64
charBits=16
doubleBits=64
```

Dòng `architectureReported=...` là **giá trị thực của JVM** và thay đổi theo platform. Nó không phải bằng chứng đầy đủ về bitness hoặc native library ABI.

**Giải thích:** Java `long` luôn là 64-bit, kể cả khi một JVM implementation ở process data model 32-bit; Java `char` là 16-bit UTF-16 code unit, không có nghĩa mọi Unicode character chiếm đúng 16 bits; `double` IEEE 754 64-bit. Không suy runtime pointer width từ các hằng `SIZE`.

## 13. Kiểm chứng JDK hiện tại từ Java code và OS command

Một quy trình so chéo đáng tin:

```text
A. Từ bên ngoài process:
   command -v java / Get-Command java
   java -version
   javac -version
   JAVA_HOME

B. Từ BÊN TRONG process:
   Runtime.version()
   java.vendor / java.vm.* / java.home
   os.arch / os.name
   availableProcessors()

C. Từ diagnostic attach vào đúng PID:
   jcmd PID VM.version
   jcmd PID VM.command_line
   jcmd PID VM.flags
   jcmd PID VM.system_properties

D. Từ OS/container:
   executable path / actual ELF/PE/Mach-O architecture
   cgroup CPU & memory limits
   container image digest / architecture
   process namespace mapping

Kết luận khi những nguồn có liên quan đã được đối chiếu.
```

### 13.1. Khi kết quả A và B không trùng nhau

Nếu terminal in `java -version` Java 21 nhưng `RuntimeFingerprint` trong service cho Java 17:

- Chưa thể coi service dùng Java 21; terminal có thể đang ưu tiên một executable khác.
- Xem `java.home` từ service, process command-line path và binary trong container.
- Kiểm tra Windows service/systemd unit, startup shell và environment lúc service khởi động.
- Tìm launcher wrapper/shim hoặc service manager option.
- Nếu process nằm trong container, chạy kiểm tra **trong chính container** thay vì host shell.

**Không tự restart production để “sửa” kết quả phiên bản** trước khi hiểu dependency/compatibility và deployment ownership.

### 13.2. Khi `java -version` và `javac -version` khác nhau

Ví dụ compiler báo 21, launcher báo 17:

1. Ghi đường dẫn tuyệt đối của cả hai.
2. Kiểm tra IDE/Maven/Gradle Java Toolchains.
3. Dùng `javac --release 17` khi build cho runtime 17.
4. Test artifact bằng đúng Java 17 runtime.
5. Nếu cần compiler APIs/toolchain khác, pin JDK build trong CI.
6. Dọn PATH/JAVA_HOME/configuration theo một nguồn rõ ràng, tránh sửa mù.

Không chỉ đổi tên `JAVA_HOME` rồi nghĩ mọi Gradle daemon/service đang chạy sẽ tự đổi JDK.

## 14. JDK đã cài trên máy không đồng nghĩa JDK đang chạy trong IDE/build tool

IDE và build systems có nhiều tầng:

- **IDE runtime JDK:** Java dùng để chạy IDE.
- **Project SDK:** Java APIs/project language level mà editor gán.
- **Gradle/Maven daemon JVM:** Java dùng chạy build process/plugin code.
- **Java compiler toolchain:** JDK thực hiện compilation.
- **Test JVM:** Process chạy unit/integration tests.
- **Production runtime JVM:** JVM cuối cùng chạy service.

Ví dụ compile class Java 17 bằng JDK 21 nhờ `--release 17` rồi test chỉ trên JDK 21 **chưa chứng minh** ứng dụng chạy tốt trên JDK 17: runtime còn có configuration/module/illegal access/native dependencies khác. Test ở target Java 17.

### 14.1. Class file format và `UnsupportedClassVersionError`

Nếu class được biên dịch ở major version cao hơn runtime hỗ trợ, JVM có thể ném:

```text
java.lang.UnsupportedClassVersionError: ...
```

Thông điệp đầy đủ phụ thuộc class/JVM. Về cơ bản:

| Java feature | Class major version thường gặp |
| --- | ---: |
| 8 | 52 |
| 11 | 55 |
| 17 | 61 |
| 21 | 65 |

Đây là mapping class format; không phải bảng version VM vendor. Dùng:

```bash
javap -verbose MyClass
```

rồi xem `major version`. `javap` phải chỉ đúng class file/artifact đã deploy, không phải class file cũ của project trong IDE. Nếu có multi-release JAR, actual class selected còn phụ thuộc runtime version và JAR layout.

### 14.2. Dùng `--release` thay vì chỉ `-source/-target`

`javac --release 17` kiểm soát cú pháp, class target và public platform API set tương ứng Java 17. Trong nhiều trường hợp nó an toàn hơn `-source 17 -target 17` khi compiler bản mới có APIs mới hơn và bạn không muốn vô tình compile call tới method chỉ tồn tại trên JDK của build machine.

**Ngoại lệ:** `--release` không tự đảm bảo tương thích **JNI library**, dynamic linkage dependencies, native crypto providers hoặc vendor-specific APIs. Chúng cần test trong target runtime.

## 15. Container và tài nguyên: “máy 64 cores” không có nghĩa JVM dùng 64

Bài nằm trong nhóm **Cấu hình tài nguyên**, nên runtime identity phải liên hệ tới **effective resources**. HotSpot Java 17 thường nhận diện Linux containers qua cgroups và áp dụng container awareness theo khả năng của bản JDK/platform.

Dù vậy:

- Host có thể có 64 CPUs nhưng container được quota tương đương 2 CPUs.
- `Runtime.getRuntime().availableProcessors()` thường phản ánh số logical processors JVM thấy, **không phải** số physical cores của host.
- `-XX:ActiveProcessorCount` có thể override số processors mà HotSpot dùng cho ergonomics và báo qua Java APIs tùy implementation.
- `-Xmx` có thể được đặt trực tiếp, được suy từ percentages/ergonomics hoặc từ wrapper configuration; không chỉ phụ thuộc host RAM.
- Container memory limit khác “host total memory” và khác “heap maximum”.
- JNI/native memory, thread stacks, direct buffers và code cache vẫn cần RAM ngoài Java heap.
- Architecture ảnh hưởng native dependencies, không phải cách duy nhất điều chỉnh `-Xmx`.

### 15.1. Xem effective CPUs ngay trong Java process

`RuntimeFingerprint` ở mục 10 có:

```java
Runtime.getRuntime().availableProcessors()
```

Dòng `logicalProcessors=<n>` phản ánh giá trị runtime báo **ở lần chạy ấy**. Nếu JVM có HotSpot option `-XX:ActiveProcessorCount=2`, nó có thể làm thay đổi CPU count dùng cho ergonomics. Nhưng đây vẫn không phải lời chứng nhận “container có đúng hai physical cores”.

### 15.2. Xem heap JVM như thế nào?

Snippet dùng trong Java 17:

```java
Runtime rt = Runtime.getRuntime();
long maxHeap = rt.maxMemory();
long committedHeap = rt.totalMemory();
long freeInCommitted = rt.freeMemory();
```

Đây là **snippet**, không phải file độc lập. `maxMemory()` là mức JVM sẽ cố dành cho heap tối đa, không phải memory limit của container; `totalMemory()`/`freeMemory()` phản ánh heap memory theo API chứ không phải tổng RSS/process memory.

Đừng tính “RAM còn trống trong container = maxMemory - totalMemory” hoặc “RSS bằng -Xmx”; cần tính cả native allocations, metaspace, JIT code cache và thread stacks.

### 15.3. `-XshowSettings:system` không thay cgroup forensics

Trên HotSpot/Linux Java 17, `-XshowSettings:system` hữu ích để biết JVM đang nhận host/container constraints. Nhưng khi CPU throttling/swap/oom-killer là vấn đề, cần dữ liệu OS/cgroup, container runtime metrics và Kubernetes events. Một JVM có thể báo logical processor count phù hợp mà vẫn bị CPU throttled nhiều do tải cạnh tranh.

## 16. Kiểm tra Java process thật trong Docker/Kubernetes

Quy trình gợi ý:

1. Xác định đúng deployment/pod/container, image **digest** và architecture target.
2. Kiểm tra entrypoint và command line thực, không chỉ Dockerfile template.
3. Vào đúng container nếu được phép: `java -version`, `command -v java`, `$JAVA_HOME`.
4. Ghi allowlisted runtime identity từ chính application (ví dụ `RuntimeFingerprint` hoặc telemetry startup).
5. Nếu có tooling và permissions, `jcmd <pid> VM.version`/`VM.command_line`.
6. Đối chiếu `os.arch` và native binary architecture, nhất là multi-arch manifests.
7. Kiểm tra effective CPU/memory limits thay vì host totals.
8. Bảo đảm JDK/runtime builder stage và production stage không bị nhầm.
9. Tái kiểm tra sau deploy/rollout, không dùng log của pod cũ.
10. Nếu dùng Java agents/CDS/AppCDS/JNI, xác định compatibility theo **runtime process**, không suy từ build machine.

### 16.1. Multi-stage container build

```text
Stage 1:
  JDK 21 builder
  javac --release 17
  build app.jar

Stage 2:
  Minimal Java 17 runtime image
  java -jar app.jar
```

Đây là workflow **hợp lệ về ý tưởng** nếu app không vô tình dùng APIs/native libraries không tồn tại ở target và được test. Hai stages có thể khác CPU architecture nếu multi-platform builder/emulation được dùng; kiểm tra artifact JAR (portable bytecode) riêng với **native libraries** (architecture-specific).

### 16.2. Docker `--platform` và host architecture

Một image có thể được build cho `linux/arm64` hoặc `linux/amd64`. Khi chạy trên host khác architecture qua emulation, performance/ABI behavior và JVM port compatibility có thể thay đổi. `os.arch` từ Java process là tín hiệu hữu ích, nhưng phải ghép với image platform và executable format.

Đừng lấy `docker info` host architecture làm câu trả lời thay cho application runtime nếu có emulation/multiarch.

## 17. VM options, flags và phiên bản khi chẩn đoán lỗi

### 17.1. `Unrecognized VM option`

Nếu một runtime không nhận `-XX:...`, có thể là:

- Flag chỉ có trên HotSpot nhưng bạn chạy OpenJ9.
- Flag đã đổi tên/xóa giữa phiên bản.
- Flag diagnostic phải unlock hoặc chỉ có trong debug build.
- Sai chính tả/định dạng/đơn vị.
- Build vendor đã tắt hoặc sửa option.
- Công cụ launch đang gọi nhầm binary.

Đừng chỉ kết luận “JDK 17 này bị hỏng”. Ghi `java.vm.name`, `java.vm.version` và absolute launcher path trước, rồi tra tài liệu VM đúng build.

### 17.2. `-XX:+UnlockDiagnosticVMOptions` không phải một giấy phép chung

Một số flags chỉ hoạt động khi unlock diagnostic options, một số chỉ có ở non-product builds; và có flags không hỗ trợ trong JVM nhất định. Không chép startup options từ tutorial C2/GC cho mọi VM với cùng Java feature.

### 17.3. `-XX:+PrintFlagsFinal` có “giá trị hiệu lực” nhưng phải hiểu nguồn

Ví dụ `MaxHeapSize` có thể do explicit `-Xmx`, ergonomics hoặc container sizing; `ActiveProcessorCount` có thể override CPU detected. Khi đọc flag output hãy xem implementation/build/command line, đặc biệt trong dịch vụ được script/wrapper quản lý.

Một flag báo giá trị cụ thể không chứng minh OS thật đang cấp đủ CPU/RAM liên tục; container cgroups và scheduler là lớp khác.

## 18. JNI/native libraries: kiểm tra đủ OS, arch và ABI

`System.loadLibrary("foo")` thường tìm native library theo conventions và `java.library.path`. Mỗi platform có suffix/cách load khác nhau:

- Linux: `libfoo.so`.
- macOS: `libfoo.dylib`.
- Windows: `foo.dll`.

Tên file **không chứng minh nội dung đã build đúng architecture**. Một `libfoo.so` có thể được build cho x86_64, trong khi JVM chạy trên aarch64. Những khác biệt còn gồm:

- 32-bit vs 64-bit process ABI.
- Calling conventions, C/C++ standard library, libc variant.
- Symbols exported/JNI mangling, native transitive dependencies.
- CPU instruction extensions và runtime linker path.
- Dynamic loader search policy và permissions.

Nếu gặp `UnsatisfiedLinkError`, phải phân biệt **không tìm thấy file** với **đã tìm thấy nhưng load không được do ABI/dependency/symbol**. Một phương pháp:

1. Xác minh `os.name`/`os.arch` trong chính Java process.
2. Xác minh VM 32/64-bit bằng startup banner/native binary inspection.
3. Xem `java.library.path` nếu liên quan.
4. Kiểm tra architecture của `.so/.dll/.dylib` và dependent libraries.
5. Đọc native loader error message và OS tool output.
6. Thử bằng đúng container/runtime image, không chỉ developer laptop.

**Không đặt `-Dsun.arch.data.model=64` để “biến JVM 32-bit thành 64-bit”**. Đó chỉ sửa string property nếu JVM cho phép, không đổi binary architecture/ABI.

## 19. Reproducible environment manifest: lưu gì để debug?

Để so sánh “local chạy, production lỗi”, nên ghi có kiểm soát:

```text
Application artifact ID/digest:
  app-version, checksum, dependencies digest

Build toolchain:
  compiler path, javac -version, --release target
  CI image digest, build OS/architecture

Production runtime:
  java.version, Runtime.version(), java.vendor
  java.vm.name, java.vm.vendor, java.vm.version
  java.home, os.name, os.arch, os.version

Execution identity:
  process PID/namespace, executable path
  VM.command_line (redacted), selected VM flags
  container image digest/platform

Resource constraints:
  availableProcessors, container CPU quota/cpuset
  -Xmx/-Xms, observed heap max, cgroup memory
  GC type, native-memory considerations

Native dependencies:
  artifact OS/architecture/ABI, library digests

Observations:
  exceptions, logs, timestamps, diagnostics build
```

**Redaction là bắt buộc trong production runbooks**: không copy toàn bộ `VM.system_properties`, launcher arguments hoặc environment chứa mật khẩu/tokens vào hệ thống public. Nếu thực sự cần lưu, áp dụng access controls và retention phù hợp.

### 19.1. “Environment fingerprint” nên làm bằng typed fields

Dùng structured metadata có field names rõ ràng, ví dụ:

```text
javaFeature=17
javaRuntimeVersion=<from actual VM>
javaVendor=<from actual VM>
vmName=<from actual VM>
vmVendor=<from actual VM>
vmVersion=<from actual VM>
osArchitecture=<reported value>
runtimeExecutable=<verified executable path>
imageDigest=<release digest>
```

Các placeholders chỉ để diễn giải schema, không phải các giá trị thực. Nếu dùng nó làm cache key cho JIT-specific artifacts, phải theo compatibility contract **của artifact provider**, không coi tất cả các trường trong bảng trên là điều kiện đủ hoặc cần của mọi loại cache.

## 20. Tình huống thực tế và cách điều tra

### 20.1. Case A — Local Java 21, production Java 17

Triệu chứng: local chạy, production có `UnsupportedClassVersionError`.

**Giả thuyết:** build ra class major 65 của Java 21, production runtime chỉ hỗ trợ major 61.

**Kiểm tra:** so `javac -version`/compiler toolchain, `javap -verbose` trên **artifact deploy**, `java -version` của đúng production container và `Runtime.version()` nếu process khởi động được.

**Xử lý:** compile với target `--release 17`, test artifact trên JDK 17, kiểm tra dependent JARs và plugin-generated classes.

### 20.2. Case B — Đặt `JAVA_HOME` JDK 17 nhưng server vẫn dùng JDK 11

Triệu chứng: shell mới báo JAVA_HOME đúng nhưng service vẫn cũ.

**Giả thuyết:** service daemon chạy từ trước, có executable absolute path hoặc systemd/Windows service config khác shell hiện tại.

**Kiểm tra:** PID service, actual executable path, `java.home`/`java.vm.version` từ **process**, startup script và service manager.

**Xử lý:** sửa cấu hình launcher bằng absolute runtime path và redeploy/restart có kiểm soát. Đừng lấy giá trị shell để sửa dữ liệu hoặc restart production ngoài change procedure.

### 20.3. Case C — ARM64 pod bị lỗi JNI

Triệu chứng: `UnsatisfiedLinkError` khi chạy trên ARM64.

**Giả thuyết:** image được chọn là arm64 nhưng native dependency là x86_64.

**Kiểm tra:** pod image architecture, runtime `os.arch`, native library binary headers và loader error.

**Xử lý:** cung cấp native library đúng ABI/arch hoặc chọn platform image đúng; compile Java source lại một mình không sửa được file native khác kiến trúc.

### 20.4. Case D — Host 32 CPUs nhưng JVM chỉ báo 2

Triệu chứng: GC/worker pools hoạt động theo số CPU thấp hơn mong đợi.

**Giả thuyết:** cgroup quota/cpuset hoặc `-XX:ActiveProcessorCount=2`.

**Kiểm tra:** Java `availableProcessors()` trong process, `-XshowSettings:system` trên HotSpot/Linux, `jcmd PID VM.flags` và cgroup config.

**Xử lý:** điều chỉnh CPU quota/active processor policy dựa trên workload và resource budgets; không giả định máy host cung cấp toàn bộ CPU cho mọi container.

### 20.5. Case E — GraalVM/HotSpot vs Native Image

**GraalVM JDK** có thể có `java` launcher chạy Java bytecode trên một JVM dựa trên HotSpot trong một số builds, còn một **native-image executable** là chương trình native đã được build AOT và không tương đương một JVM `java -jar` process. Đừng gộp hai artifact chỉ vì đều đến từ cùng vendor/toolchain.

Với native-image executable, `System` properties/VM diagnostics/GC/runtime capabilities có thể khác theo công cụ/bản build. Phải đọc documentation **của artifact và version cụ thể**.

### 20.6. Case F — Chạy JDK mới hơn nhưng flags cũ

Triệu chứng: VM in “Unrecognized VM option” hoặc warning về option bị deprecated.

**Kiểm tra:** vendor, VM implementation, version và output `-XX:+PrintFlagsFinal`/VM-specific docs; không dùng bảng flags của một JDK khác theo thói quen.

**Xử lý:** cập nhật flags theo target JDK/VM và benchmark collector/resource behavior. Không biến cảnh báo thành “JVM 17 chắc phải dùng G1” mà chưa xác định implementation.

## 21. Những lỗi diễn giải thường gặp

1. **“`java -version` trên host chính là runtime của mọi container.”** Sai; container có image riêng.
2. **“`JAVA_HOME` xác định chắc file `java` mà PATH gọi.”** Sai; launcher resolution/wrapper khác.
3. **“`javac -version` bằng Java version của service.”** Sai; compiler/process riêng.
4. **“Project language level trong IDE quyết định production VM.”** Sai.
5. **“`--release 17` chứng minh JNI native lib chạy trên mọi architecture.”** Sai.
6. **“Cùng Java 17 thì mọi vendor/VM đều có cùng HotSpot flags.”** Sai.
7. **“`java.vm.name` cho biết toàn bộ vendor/patch/build.”** Sai; cần thêm properties.
8. **“`java.vm.specification.vendor` luôn là JDK distributor.”** Sai.
9. **“`java.specification.version` là đầy đủ bản vá security.”** Sai.
10. **“`java.class.version` là compile version của từng class trong app.”** Sai.
11. **“`Runtime.version().feature()==17` chứng minh VM thuộc đúng patch release.”** Sai.
12. **“`Integer.SIZE` hoặc `Long.SIZE` đo JVM pointer width.”** Sai.
13. **“`os.arch` là danh sách CPU extensions/ISA features.”** Sai.
14. **“`uname -m` luôn bằng `os.arch` trong mọi container/emulation.”** Sai.
15. **“Cứ có `amd64` trong property là mọi native `.so` trên Linux chạy được.”** Sai.
16. **“JDK 17 chắc chắn có `javac` trong runtime image production.”** Sai nếu jlink/minimal image.
17. **“Không thấy `jcmd` nghĩa service không chạy JVM.”** Sai; runtime image có thể thiếu tool.
18. **“`-XX:+PrintFlagsFinal` là Java SE portable API.”** Sai; phụ thuộc VM implementation.
19. **“`-XshowSettings:system` luôn có output giống nhau trên Windows/macOS/Linux.”** Sai.
20. **“`os.name`/`os.version` giải thích đầy đủ container base distro.”** Sai; JVM nhìn kernel/userspace trong ngữ cảnh phù hợp.
21. **“`availableProcessors()` bằng số physical cores chắc chắn được cấp riêng.”** Sai.
22. **“Heap max là toàn bộ RAM limit container.”** Sai; off-heap/native memory còn dùng RAM.
23. **“Đổi `System.setProperty("os.arch", ...)` đổi native binary architecture.”** Sai.
24. **“Máy có JDK 21 nên class-file major 65 luôn chạy được trên Java 17 container.”** Sai.
25. **“Chạy success trên builder image chứng minh success trên runtime image.”** Sai.
26. **“`java.home` = môi trường `JAVA_HOME` của mọi process.”** Sai.
27. **“`java.vendor` luôn chỉ một enum chính thức của JVM vendors.”** Sai, là string.
28. **“`java.vm.version` bắt buộc giống `java.version`.”** Sai.
29. **“Java runtime version >= 17 bảo đảm không còn lỗi classpath/module reflection.”** Sai.
30. **“JDK feature/version fingerprint đủ để chứng minh CDS archive binary compatible.”** Sai; build/classpath/VM flags cũng quan trọng.
31. **“`java -version` cần khởi động mọi application main class.”** Sai; chỉ launcher.
32. **“`jcmd PID VM.version` trên PID bất kỳ trong host luôn thành công.”** Sai; attach permissions/namespaces.
33. **“`-XX:ActiveProcessorCount` bắt buộc bằng số core vật lý.”** Sai.
34. **“Có thể dump toàn bộ VM.system_properties lên public logs mà an toàn.”** Sai; có thể chứa sensitive data.
35. **“CPU architecture là lý do duy nhất khác hiệu năng giữa hai JDKs.”** Sai; VM build/GC/flags/workload/containers.
36. **“HotSpot 64-Bit Server VM” banner có nghĩa heap luôn 64 GiB hoặc lớn hơn.”** Sai; heap riêng và phụ thuộc flags/limits.
37. **“`java.vendor` cùng giá trị nghĩa hai JDK binaries có cùng checksum.”** Sai.
38. **“`ProcessHandle.current().pid()` luôn bằng host PID khi chạy container.”** Không bảo đảm, PID namespace.
39. **“Một JDK hỗ trợ Linux x64 mặc định hỗ trợ Linux ARM64.”** Sai; phải chọn correct port/build.
40. **“Java primitive widths thay đổi theo CPU architecture.”** Sai; Java language semantics xác định width.

## 22. Bài tập thực hành

### Bài 1 — Khám phá môi trường thực

Chạy `RuntimeFingerprint`. Ghi lại:

- Java feature/update/build theo `Runtime.Version`.
- Java vendor, VM name/vendor/version.
- `java.home` và absolute launcher path.
- `os.arch`, `os.name` và (nếu có) `sun.arch.data.model`.
- `availableProcessors` và container CPU quota (nếu liên quan).

**Câu hỏi:** Có cặp giá trị nào khác trực giác (ví dụ Java 17 từ runtime image nhưng `javac` của IDE là 21)? Giải thích bằng toolchain/runtime separation.

### Bài 2 — So builder và runtime images

Biên dịch `FeatureGate` với JDK 21 dùng `--release 17`, chạy trên JDK 17. Sau đó thử build không dùng `--release` rồi xác minh class-file major với `javap -verbose` trước khi đưa lên runtime.

**Yêu cầu:** Không tùy tiện gọi `java` trên host; dùng absolute paths hoặc container images đã pin.

### Bài 3 — Kiểm tra resource awareness

Trong một HotSpot Java 17 Linux container test, đo `availableProcessors()` với cgroup CPU quota khác nhau, và một lần cấu hình `-XX:ActiveProcessorCount` nếu runtime hỗ trợ.

**Yêu cầu:** Không suy số physical cores từ API này; ghi kernel/cgroup/runtime version.

### Bài 4 — Chọn đúng JNI artifact

Giả sử có `libcodec.so` build cho Linux x86_64 và production image chạy Linux aarch64. Trình bày các bước xác minh `os.arch`, binary headers, dependencies và cách xây thư viện đúng architecture.

**Đáp án định hướng:** Java bytecode có thể portable, native JNI binary thường không; cần build/dùng một phiên bản phù hợp OS/ABI.

### Bài 5 — Tạo incident report có dữ liệu tối thiểu

Viết báo cáo gồm Java runtime feature/build, VM vendor/name/version, executable path, container image digest/arch, selected VM flags, symptoms và exception. Không gửi full `System.getProperties()` hoặc tokens.

**Tiêu chí đạt:** Một kỹ sư khác có thể xác định **đúng runtime process và architecture** để lặp lại issue.

## 23. Câu hỏi tự kiểm tra (có đáp án)

**Câu 1.** Lệnh nào cho biết phiên bản JVM đang chạy trong đúng process PID trên HotSpot?

**Đáp án:** `jcmd <pid> VM.version` khi attach được; đối chiếu với `VM.command_line` và Java-side properties.

**Câu 2.** Nếu `javac -version` là 21 nhưng runtime Java feature là 17, đây có nhất thiết là lỗi?

**Đáp án:** Không. Có thể cố ý cross-compile bằng `--release 17`. Vấn đề là artifact có đúng Java 17 API/class-file/native compatibility và được test trên runtime target hay không.

**Câu 3.** `java.vendor` và `java.vm.name` có trả lời cùng câu hỏi không?

**Đáp án:** Không. Vendor nói về distribution, VM name nói về implementation.

**Câu 4.** `java.vm.specification.vendor` có nhất thiết là Oracle/Temurin/Corretto đang phân phối binary không?

**Đáp án:** Không. Specification vendor khác implementation/distribution vendor.

**Câu 5.** Vì sao `-Dsun.arch.data.model=64` không biến một JVM 32-bit thành JVM 64-bit?

**Đáp án:** Vì nó là property string, không thay đổi executable ABI/pointer width.

**Câu 6.** `Runtime.version().feature()` có thể dùng để check một API yêu cầu Java 17 không?

**Đáp án:** Có, nếu policy là tối thiểu feature 17, nhưng không thay dependency/native compatibility tests.

**Câu 7.** Java 17 class major version là bao nhiêu?

**Đáp án:** **61**; property `java.class.version` thường trả `61.0`.

**Câu 8.** Có thể coi `os.arch=amd64` là bằng chứng mọi CPU extensions mà JIT có thể dùng đều hiện diện không?

**Đáp án:** Không. Architecture family không nêu mọi ISA extensions/hardware features.

**Câu 9.** `Runtime.getRuntime().availableProcessors()` thể hiện host physical core count hay JVM-visible logical processor count?

**Đáp án:** JVM-visible logical processor count; có thể bị ảnh hưởng bởi container quotas/cpuset và VM flags.

**Câu 10.** `java.home` bằng `JAVA_HOME` không?

**Đáp án:** Không bắt buộc. `java.home` là runtime location của process, `JAVA_HOME` là environment variable có thể trỏ JDK khác.

**Câu 11.** Tại sao `jcmd` trong custom JRE image có thể không tìm thấy?

**Đáp án:** Runtime image có thể chỉ chứa modules/executables cần chạy app, không có diagnostic tools.

**Câu 12.** Làm gì trước khi thử `-XX:+PrintFlagsFinal` trên VM khác HotSpot?

**Đáp án:** Xác định `java.vm.name`/vendor/version và tham khảo VM-specific docs để biết option hỗ trợ.

**Câu 13.** Đo total RAM JVM bằng `maxMemory()` đúng không?

**Đáp án:** Không. `maxMemory` liên quan Java heap limit; native stacks, metaspace, code cache và direct buffers khác.

**Câu 14.** Vì sao host `uname -m` và Java `os.arch` có thể khác?

**Đáp án:** Container/emulation/VM process bitness/platform reporting khác; cần kiểm tra executable và native ABI của process.

**Câu 15.** Có an toàn để chia sẻ full `jcmd PID VM.system_properties` trên issue công khai không?

**Đáp án:** Không; nó có thể chứa đường dẫn/thông tin cấu hình hoặc secret, cần lọc/redact.

**Câu 16.** Một ứng dụng Java 17 chạy tốt trên local x86_64, lên ARM64 bị `UnsatisfiedLinkError`. Có đủ chứng cứ JVM thiếu Java feature không?

**Đáp án:** Không; nghi native library architecture/ABI mismatch và kiểm tra đường load/dependency.

**Câu 17.** Hai services cùng Java 17 nhưng Java.vm.name khác nhau. Có thể copy HotSpot-only tuning flags giữa chúng không?

**Đáp án:** Không mặc định. Phải dùng tài liệu/validation đúng VM implementation.

**Câu 18.** Nếu container image tag không đổi nhưng digest thay đổi, runtime identity có thể đã đổi không?

**Đáp án:** Có. Pin digest và kiểm chứng runtime mới.

**Câu 19.** `Runtime.Version.update()` và `feature()` khác nhau thế nào?

**Đáp án:** Feature là Java release family, update là thành phần update release trong scheme; không coi update là major feature.

**Câu 20.** Đâu là bộ thông tin tối thiểu có ích trước khi báo lỗi GC/container?

**Đáp án:** JDK feature/update/build, vendor, VM name/version, OS/arch, process PID/launch command, effective VM flags, CPU/memory limits, workload và logs được redact.

## 24. Checklist nhận diện runtime trước khi sửa cấu hình tài nguyên

- [ ] Xác nhận **đúng service/pod/container/PID**, không nhầm shell với process đích.
- [ ] Ghi **`Runtime.version()`** gồm feature/update/build khi có.
- [ ] Ghi `java.version`, `java.vendor`, `java.vm.name`, `java.vm.vendor`, `java.vm.version`.
- [ ] Ghi `java.home` và đường dẫn executable thực của service.
- [ ] So `java -version`/`javac -version` theo đường dẫn tuyệt đối.
- [ ] Phân biệt build JDK, test JDK, production runtime.
- [ ] Kiểm tra Java class major version (Java 17 → 61) của artifact nếu cần.
- [ ] Ghi `os.name`/`os.arch` và xác minh process/native binary ABI khi có JNI.
- [ ] Đọc `-XshowSettings:system` trên HotSpot/Linux nếu cần container awareness.
- [ ] Ghi `availableProcessors` và effective CPU quota/cpuset.
- [ ] Xác minh heap flags `-Xms/-Xmx`, GC, stack size và native memory constraints.
- [ ] Với HotSpot: thử `jcmd PID VM.version`/`VM.command_line`/`VM.flags` khi được phép.
- [ ] Xử lý chênh lệch giữa host/shell/app process rõ ràng thay vì giả định đồng nhất.
- [ ] Trong multi-arch images, pin image digest/platform và JNI artifacts tương ứng.
- [ ] Chỉ dùng JVM flags được **VM implementation/build thật** hỗ trợ.
- [ ] Redact secrets trước khi lưu diagnostic outputs.
- [ ] Kiểm thử trên runtime/deployment target sau khi đổi JDK hay flags.

## 25. Tổng kết bản đồ quyết định

```text
Bạn cần tối ưu hoặc debug một Java service?
                    │
                    ▼
          XÁC ĐỊNH ĐÚNG PROCESS
          PID + container + executable
                    │
                    ▼
           XÁC ĐỊNH JAVA VERSION
       Runtime.version(), java.version
         feature / update / patch / build
                    │
                    ▼
          XÁC ĐỊNH VM IMPLEMENTATION
       java.vm.name / vendor / version
                    │
                    ▼
          XÁC ĐỊNH ARCHITECTURE
        os.arch + process binary + ABI
                    │
                    ▼
         XÁC ĐỊNH RESOURCE ENVELOPE
       CPUs/quota, RAM/cgroup, -Xmx, GC
                    │
                    ▼
          KIỂM TRA BUILD VS RUNTIME
          javac / --release / class major
                    │
                    ▼
        THU THẬP EFFECTIVE VM FLAGS
           jcmd (nếu có và attach được)
                    │
                    ▼
       ÁP DỤNG TUNING / FIX PHÙ HỢP
       đúng JDK + đúng VM + đúng target
                    │
                    ▼
         VERIFY trên runtime production-like
```

**Ghi nhớ:** Mỗi lần nói “ứng dụng đang dùng Java 17”, hãy xác nhận **Java 17 của tiến trình nào, bản vá/build nào, vendor nào, JVM implementation nào, trên architecture nào và với giới hạn tài nguyên nào**. `java -version` trong shell, `JAVA_HOME`, `javac -version`, `os.arch` và `jcmd PID VM.version` **cung cấp những góc nhìn khác nhau**, không thay nhau được. Tốt nhất là đối chiếu **runtime properties của chính process**, **toolchain paths**, **native binary/OS/container facts** và **effective VM flags** trước khi đưa ra quyết định GC, heap, JIT hay JNI.

### Tài liệu chính thức

- [Oracle Java SE 17 — System Properties](https://docs.oracle.com/en/java/javase/17/docs/api/system-properties.html): `java.vendor`, `java.version`, `java.vm.name` và properties liên quan.
- [Java SE 17 — `java.lang.System` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html): định nghĩa `getProperty`/`getProperties`, danh sách chuẩn và caveats.
- [Java SE 17 — `java.lang.Runtime.Version`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.Version.html): các thành phần feature/interim/update/patch/build.
- [Oracle JDK 17 — Version String Format](https://docs.oracle.com/en/java/javase/17/install/version-string-format.html): giải thích release numbering.
- [Oracle JDK 17 — `java` launcher](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-version`, `--version`, `-XshowSettings`, `-XX:+PrintFlagsFinal` và HotSpot options.
- [Oracle JDK 17 — `javac` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html): `--release 17` và compilation compatibility.
- [Oracle JDK 17 — `javap` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): `-verbose` để đọc class-file major.
- [Oracle JDK 17 — `jcmd` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `VM.version`, `VM.command_line`, `VM.flags`, `VM.system_properties`.
- [Java SE 17 — `ProcessHandle` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ProcessHandle.html): PID và metadata của tiến trình.
- [JVMS SE 17 — Class File Format](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html): bytecode/class major version khác runtime VM implementation build.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-002">Tùy chọn chuẩn, -X, -XX và cách xác nhận cờ được hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Tùy chọn chuẩn, -X, -XX và cách xác nhận cờ được hỗ trợ trong JVM

## Mục tiêu

Trong một ứng dụng Java thực tế, JVM nhận được nhiều loại tham số: tùy chọn launcher, tham số dành cho JVM, system properties và **application arguments**. Chúng không cùng ngữ nghĩa, không cùng mức ổn định và không phải tất cả được hỗ trợ trên mọi vendor/JDK build.

Ví dụ:

~~~bash
java -Xms256m -Xmx1g -XX:+UseG1GC -Dapp.profile=prod -jar service.jar --port=8080
~~~

Lệnh minh họa trên có:
- `-Xms256m` và `-Xmx1g`: nhóm **-X** định cấu hình initial/minimum heap và maximum Java heap.
- `-XX:+UseG1GC`: nhóm **-XX** chọn G1 garbage collector theo HotSpot.
- `-Dapp.profile=prod`: một **standard launcher option** truyền system property cho Java code.
- `-jar service.jar`: launcher chạy JAR có Main-Class hợp lệ.
- `--port=8080`: **argument của ứng dụng**, không phải JVM option khi nằm sau `-jar service.jar`.

Một cờ được ghi trong cấu hình không đồng nghĩa nó có tác dụng: cờ có thể bị từ chối, cần unlock, chỉ hỗ trợ trên một platform, bị đánh dấu **deprecated**, **obsolete** (nhận nhưng bỏ qua), **expired/removed** (bị từ chối), hoặc được JVM thay đổi giá trị cuối vì ergonomics hay flag precedence.

Sau bài, bạn có thể:
1. Phân loại **standard options**, **extra/non-standard -X** và **advanced HotSpot -XX**.
2. Viết đúng cú pháp Boolean `-XX:+Flag`/`-XX:-Flag` và giá trị `-XX:Flag=value`; hiểu `-Xmx2g` không giống `-XX:+Xmx`.
3. Kiểm tra phạm vi hỗ trợ qua `java --help`, `java -X`, `-Xlog:help`, tài liệu bản JDK, `-XX:+PrintFlagsFinal` và `jcmd`.
4. Phân biệt **cờ được parser chấp nhận**, **đang bật**, **giá trị cuối**, **có tác dụng với subsystem** và **được hỗ trợ ổn định**.
5. Phát hiện nhầm JDK binary, cờ đặt sai phía `-jar`, môi trường `JDK_JAVA_OPTIONS`/`JAVA_TOOL_OPTIONS`, cờ cần unlock và options hết vòng đời.
6. Cấu hình Java heap, CPU, native resources và GC theo yêu cầu thật, không suy từ một command-line flag thành toàn bộ process memory.
7. Sử dụng ví dụ Java 17+ có stdout xác định, cùng quy trình smoke-test an toàn cho CI/CD và production.

## 1. Ba nhóm option: tên gọi và mức độ ổn định

Tài liệu chính thức về lệnh `java` phân biệt:

| Nhóm | Ví dụ | Đặc điểm |
| --- | --- | --- |
| **Standard Java launcher options** | `--class-path`, `-cp`, `-jar`, `-Dkey=value`, `--module-path`, `--enable-assertions`, `--version` | Được tài liệu Java command mô tả và có kỳ vọng tính tương thích rộng hơn giữa các JVM implementations |
| **Extra/non-standard options (-X)** | `-Xms`, `-Xmx`, `-Xss`, `-Xint`, `-XshowSettings`, `-Xlog` | HotSpot-specific hoặc không được bảo đảm mọi JVM hỗ trợ; có thể thay đổi |
| **Advanced HotSpot options (-XX)** | `-XX:+UseG1GC`, `-XX:MaxRAMPercentage=75`, `-XX:+PrintFlagsFinal` | Cấu hình sâu runtime, GC, JIT, serviceability; độ ổn định tùy flag và release |

**Không thể đánh giá tính ổn định chỉ bằng độ dài tên cờ.** `-Xmx` thuộc -X nhưng được sử dụng rất phổ biến; còn một flag `-XX` có thể là product flag quen thuộc, diagnostic flag cần unlock hoặc experimental flag chỉ phù hợp thử nghiệm. Dù vậy cả -X và -XX **không là hợp đồng Java SE áp dụng vô điều kiện cho mọi JVM**. <https://dev.java/learn/jvm/tools/core/java/>

### Standard không có nghĩa chương trình không cần kiểm tra version

Một option được ghi nhận trong một JDK hiện tại có thể **chưa tồn tại trong JDK đời trước**. Chẳng hạn các module options xuất hiện theo lịch sử Java Platform Module System, và `--enable-preview` yêu cầu sự phù hợp giữa compiler/runtime và bản phát hành. Với toolchains thực tế, luôn tra tài liệu của đúng JDK được triển khai.

### Một ứng dụng có nhiều “đường” nhận tham số

~~~text
Operating system / shell
         |
         +-- environment variables
         |      JDK_JAVA_OPTIONS / JAVA_TOOL_OPTIONS
         |
         +-- java launcher args
                  |
           JVM / module/class loader
                  |
            main(String[] args)
                  |
          application framework parser
~~~

Lỗi thường gặp là trộn `-Dserver.port=8080` (system property), `--server.port=8080` (có thể là application argument của framework), và `-XX:...` (VM flag). Chúng **không tự hoán đổi** cho nhau.

## 2. Cú pháp launcher: đặt JVM options trước main target

Ba kiểu chạy thông dụng:

~~~bash
java [options] com.example.Main [app-args]
java [options] -jar application.jar [app-args]
java [options] --module module.name[/MainClass] [app-args]
~~~

Trong code fence, các phần trong dấu ngoặc vuông là **ký hiệu minh họa** của command grammar, không được gõ nguyên nếu không có ý nghĩa cụ thể. `options` thường được parser của launcher đọc **trước main class hoặc -jar artifact**. Những token sau main target thông thường được chuyển vào `main(String[] args)` của ứng dụng.

So sánh:

~~~bash
java -Dapp.mode=study -Xmx128m -jar demo.jar input.txt
~~~

Đây là **mẫu lệnh**, cần JAR đúng. JVM thấy system property `app.mode=study` và heap maximum ~128 MiB; ứng dụng nhận `input.txt`.

Ngược lại:

~~~bash
java -jar demo.jar -Xmx128m
~~~

Ở đây `-Xmx128m` thường là **chuỗi argument của ứng dụng** sau JAR, **không đặt maximum heap**. Một framework có thể có parser riêng và thậm chí báo “unknown app argument”. Hãy xem actual JVM command line, không chỉ một string xuất hiện đâu đó trong launch script.

### `-jar` không giống classpath mode

~~~bash
java -cp lib/app.jar:lib/dependency.jar com.example.Main
~~~

Lệnh trên minh họa **Linux/macOS** với classpath separator `:`; Windows dùng `;`. Khi chạy `-jar`, launcher dùng entry point/JAR rules tương ứng; việc thêm `-cp` có thể không ảnh hưởng theo cách bạn dự đoán. Đừng cố “sửa thiếu dependency” bằng cách đổi vị trí flags mà không đọc tài liệu `-jar` và manifest/classpath.

### System properties

~~~bash
java -Ddemo.mode=study com.example.Main
~~~

Trong Java, đọc bằng `System.getProperty("demo.mode")`. Đây không phải environment variable. `System.getenv("DEMO_MODE")` đọc một nguồn khác. Một application framework có thể thêm cách ánh xạ giữa properties/environment nhưng đó không phải behavior của JVM option parser.

## 3. -X: các extra options thường gặp

| Flag | Tác dụng chính | Cảnh báo |
| --- | --- | --- |
| `-Xms256m` | Thiết lập kích thước heap khởi đầu/minimum theo semantics HotSpot | Không phải tổng process RSS |
| `-Xmx2g` | Hard maximum Java heap | Native memory còn có metaspace, thread stacks, code cache, direct memory... |
| `-Xss1m` | Thiết lập stack size cho platform threads theo HotSpot/OS | Không phải “tổng stack của mọi thread” |
| `-Xint` | Chế độ interpreted-only cho Java code | Thay đổi performance mạnh, không dùng để mô phỏng production tự nhiên |
| `-Xcomp` | Thiên về compile sớm | Có thể tốn compilation CPU và khác profile |
| `-Xbatch` | Chạy JIT compilation foreground thay vì background theo policy | Có thể làm warmup/latency rất khác |
| `-XshowSettings:vm` | In VM settings hữu ích | Output thay đổi theo bản JDK |
| `-Xlog:gc*=info` | Unified logging GC ở HotSpot bản hỗ trợ | Log I/O có overhead; không có output bất biến |
| `-Xshare:off` / `-Xshare:on` | Điều khiển CDS sharing | ON bắt buộc dùng archive, dễ fail khi mismatch |

**Không sử dụng tất cả cờ cùng lúc chỉ vì chúng xuất hiện trong bảng.** Nếu đang đo GC, bật `-Xint` hoặc `-Xcomp` sẽ đổi JIT execution model, làm so sánh mất ý nghĩa.

### Kích thước tài nguyên: đơn vị đúng

Ví dụ:

~~~bash
java -Xms512m -Xmx2g -Xss1m -jar application.jar
~~~

Tùy chọn -X thường có suffix `k`/`m`/`g` với cách diễn giải kích thước kiểu binary units trong HotSpot. Đọc actual `MaxHeapSize`/`InitialHeapSize` bằng VM diagnostics để xác nhận. Đừng kết luận “container RAM=2 GiB đủ vì -Xmx2g”: process cần bộ nhớ **ngoài Java heap**.

### -Xlog: cú pháp và cấu hình logging

Ví dụ:

~~~bash
java -Xlog:gc*=info:file=gc.log:time,uptime,level,tags -jar application.jar
~~~

Đây là **mẫu chạy**, cần file JAR thực. Unified logging chia selectors (tags/levels), output và decorators; các tags có thể khác release. Hãy xem `java -Xlog:help` trên chính JDK để tìm syntax và tags khả dụng.

Cảnh báo: verbose logging tạo CPU/disk I/O và có thể ghi chi tiết hoạt động ứng dụng. Ở production, cân nhắc dung lượng log, rotation, quyền đọc và phạm vi thông tin.

## 4. -XX: hai dạng cú pháp phổ biến

### Boolean flag: dấu + và -

~~~bash
java -XX:+UseG1GC -version
java -XX:-UseCompressedOops -version
~~~

- `-XX:+FlagName` nghĩa yêu cầu bật Boolean flag đó.
- `-XX:-FlagName` nghĩa yêu cầu tắt.
- **Không** dùng `-XX:UseG1GC=true` như một thay thế bừa bãi vì syntax được tài liệu mô tả cho boolean flags là `+/-`.
- Việc flag bị disable hoặc unsupported **không đồng nghĩa** JVM không thể chạy: tùy constraint hoặc dependency giữa flags, parser có thể fail hoặc runtime có behavior theo policy.

`UseCompressedOops` là flag HotSpot có thể chịu constraints về heap/platform/JDK release; đừng tắt tùy tiện vì ảnh hưởng memory/performance và có thay đổi theo phiên bản. Ví dụ trên **chỉ minh họa grammar**, không phải lời khuyên tuning.

### Flag nhận giá trị: dấu bằng

~~~bash
java -XX:MaxRAMPercentage=65 -version
java -XX:ActiveProcessorCount=2 -version
java -XX:MaxMetaspaceSize=256m -version
java -XX:ReservedCodeCacheSize=128m -version
~~~

Cần phân biệt:
- `MaxRAMPercentage` là một **percent value** (ví dụ 65 nghĩa 65%, không phải 0.65).
- `ActiveProcessorCount` điều chỉnh số processors JVM **báo/nhận diện cho ergonomics** như GC/ForkJoin; không tạo CPU cores hoặc giới hạn cứng scheduler của OS.
- `MaxMetaspaceSize` đặt giới hạn metaspace thích hợp; quá thấp có thể làm class loading fail.
- `ReservedCodeCacheSize` điều chỉnh vùng chứa JIT-compiled code; không phải Java heap.
- Với mỗi flag, đọc constraints và default trong **Java command docs của đúng release**.

Một số flag có syntax option-specific khác. Đừng tự giả định mọi option với argument đều dùng dấu bằng, hay thay suffix `m` vào một percent flag.

## 5. Bốn mức ý nghĩa khi nói “cờ được hỗ trợ”

Cần tách bốn câu hỏi:

| Câu hỏi | Kiểm tra chính | Vì sao khác nhau |
| --- | --- | --- |
| **Parser có nhận option?** | Thử khởi động `java [flag] -version`, kiểm tra exit code/stderr | Một obsolete flag đôi khi được nhận nhưng bị bỏ qua |
| **Flag có tồn tại ở runtime?** | `PrintFlagsFinal`, `jcmd VM.flags -all`, tài liệu đúng JDK | Một option có thể bị ẩn sau unlock hoặc chỉ tồn tại bản HotSpot khác |
| **Effective value cuối là gì?** | `PrintFlagsFinal` hoặc `jcmd PID VM.flags -all` | Ergonomics, default, command line và constraints có thể ảnh hưởng |
| **Feature thực sự hoạt động?** | Subsystem logs/behavior, metrics, smoke test workload | “flag true” không chứng minh GC/JIT/memory performance đạt kỳ vọng |

Ví dụ `-XX:+UseZGC` có thể được parser nhận, nhưng câu “mọi ZGC mode như JDK 21 còn tồn tại trong JDK 25” không đúng: underlying algorithms/legacy flags đã thay đổi. Tương tự, JVM chấp nhận một old flag và in warning có thể **không hề áp dụng effect**.

**Lưu ý:** `java -version` có thể trả 0 ngay cả khi một flag hợp lệ chỉ định tính năng hiếm dùng và runtime workload không kích hoạt nó. Smoke test kiểm tra khả năng khởi động, không đủ để chứng minh hiệu năng.

## 6. Bước 1: biết chính xác bản JDK đang thực thi

Bắt đầu:

~~~bash
java -version
java --version
javac -version
~~~

Output từ `java -version` gồm version/build/vendor/VM details tùy JDK; không có stdout chung cố định. `javac` có thể thuộc một JDK khác với `java` trong PATH.

Đặc biệt khi dùng:
- IDE run configurations;
- Maven/Gradle toolchains;
- application servers có private bundled JRE;
- Docker/Podman images hoặc Kubernetes Pods;
- systemd/windows services và shell aliases;
- nhiều cài đặt JDK17/21/25 cạnh nhau.

**Hãy chạy các lệnh kiểm tra trong chính runtime/container/launch environment sẽ khởi động service**, không suy từ shell máy cá nhân. Với ứng dụng đang chạy, lấy process command line và VM version bằng công cụ gắn đúng PID.

### Java home không tự bảo đảm binary được gọi

`JAVA_HOME` là biến môi trường thường được scripts/toolchains dùng; khi gõ `java`, shell còn áp dụng PATH/resolution rules. Trên Windows có `Get-Command java` trong PowerShell; trên Linux/macOS có `command -v java`. Không khẳng định `JAVA_HOME` và `java -version` luôn trùng nhau nếu không kiểm tra.

## 7. Bước 2: Java launcher tự công bố những options nào?

Các lệnh phổ biến:

~~~bash
java --help
java -X
java -Xlog:help
~~~

Ý nghĩa:
- `java --help` mô tả cú pháp launcher và **standard options** phù hợp.
- `java -X` hiển thị nhiều extra/non-standard options của HotSpot trên bản đó.
- `java -Xlog:help` cung cấp logging tags, levels, decorators và syntax được hỗ trợ.

**Không có lệnh `java -X` nào nhất thiết liệt kê mọi option -XX**, đặc biệt diagnostic/experimental/internal flags. Tương tự, một flag xuất hiện ở help không bảo đảm nó có cùng range/default trên một JDK khác.

Có thể dùng `java --help-extra` ở những JDK hỗ trợ để xem thêm option descriptions. Kết quả output phụ thuộc binary và version.

### Cách kiểm tra trên PowerShell

~~~powershell
java --help
java -X
java -Xlog:help
~~~

Đầu ra của một vài launcher options có thể đến stderr; khi lọc kết quả cần quan tâm cả stdout và stderr. Không dùng “không thấy trong một `Select-String`” làm bằng chứng tuyệt đối rằng flag không tồn tại.

## 8. Bước 3: xem giá trị các -XX flags của HotSpot

~~~bash
java -XX:+PrintFlagsFinal -version
~~~

Trên HotSpot bản hỗ trợ, lệnh này liệt kê nhiều VM flags cùng:
- loại (ví dụ bool, intx, uintx, size_t, double...);
- tên (ví dụ `MaxHeapSize`);
- giá trị cuối đã được tính;
- phân loại và/hoặc nguồn gốc (product, diagnostic, experimental, command line, ergonomic, default...) tùy bản build và định dạng output.

Ví dụ cấu trúc dòng **minh họa**, không phải log cố định:

~~~text
   size_t MaxHeapSize = 536870912 {product} {ergonomic}
     bool UseG1GC = true         {product} {ergonomic}
~~~

Các con số, dấu `=` hoặc `:=`, name/value, category/origin thay đổi theo JDK/flags/container. **Đừng copy kết quả minh họa để xác nhận production instance**.

### So default với explicitly set

~~~bash
java -XX:+PrintFlagsFinal -version
java -Xmx512m -XX:+PrintFlagsFinal -version
~~~

So `MaxHeapSize` giữa hai lần. Khi có `-Xmx512m`, expected maximum Java heap ở cấu hình HotSpot phù hợp là 512 MiB (536.870.912 bytes). Nhưng command line có thể bị ảnh hưởng bởi hidden environment flags; để kiểm soát thí nghiệm, kiểm tra cả môi trường và actual input args.

Với `-XX:MaxRAMPercentage=65` và **không** đặt `-Xmx`, maximum heap thường được tính theo tài nguyên container/host mà VM nhận diện và những constraints liên quan. Con số thật không thể suy chỉ từ “65% của 8 GiB host RAM” nếu container memory limit chỉ 2 GiB hoặc có minimum/maximum limits khác.

### Xem range nếu cần kiểm tra giá trị đầu vào

Trên các JDK HotSpot có hỗ trợ:

~~~bash
java -XX:+PrintFlagsRanges -version
~~~

Dữ liệu ranges chỉ xuất hiện đầy đủ cho các flags có range được khai báo; output không thay thế các **inter-flag constraints** hoặc runtime limitations. Một giá trị nằm trong range nhưng xung đột với memory budget/GC mode vẫn có thể làm startup fail.

### PrintFlagsInitial và PrintFlagsFinal

Một số HotSpot builds cho phép:

~~~bash
java -XX:+PrintFlagsInitial -version
java -XX:+PrintFlagsFinal -version
~~~

`PrintFlagsInitial` cho biết nhiều initial/default flag values khi thích hợp; `PrintFlagsFinal` phản ánh final chosen values của startup. Chúng không phải mô tả **mọi thay đổi động phát sinh suốt lifetime** và không có nghĩa tất cả cờ có thể chỉnh live.

## 9. Bước 4: kiểm tra process đang chạy, không chỉ thử một JVM mới

Một JVM được khởi động bằng Helm chart/script/IDE có thể dùng arguments khác nhiều so với lệnh thử `java -version` của bạn. Với target process HotSpot có quyền attach, sử dụng:

~~~bash
jcmd
jcmd PID VM.command_line
jcmd PID VM.flags
jcmd PID VM.flags -all
jcmd PID VM.version
~~~

**`PID` ở đây là ký hiệu cần thay bằng process ID thật**, không phải chuỗi hợp lệ để copy y nguyên. Dùng `jcmd PID help` để xem những diagnostics khả dụng trên chính process; availability và output có thể thay đổi theo JDK.

Diễn giải:
- `VM.command_line` cho biết actual VM/Java command line theo diagnostic implementation.
- `VM.flags` tóm tắt một số VM flags.
- `VM.flags -all` yêu cầu thêm danh sách các flags, thường với giá trị/nguồn gốc, nếu supported.
- `VM.version` xác nhận actual runtime VM build.

Một vài flags là **manageable** và có thể điều chỉnh live qua công cụ như `jinfo`/management, nhưng **phần lớn startup flags không có quyền đổi động**, và việc chỉnh live trên production là thao tác vận hành cần kiểm soát.

### Không phải mọi JVM đều cho attach

Hạn chế container namespaces, filesystem permissions, quyền OS, flag disable attach mechanism, JDK installation và bảo mật có thể khiến `jcmd` thất bại. Đừng coi attach failure là bằng chứng flag không được hỗ trợ; đầu tiên kiểm tra permission/PID/VM build.

### Java API tự đọc một số args

`ManagementFactory.getRuntimeMXBean().getInputArguments()` trả về các input arguments mà VM nhận theo contract API, hữu ích khi logging cấu hình khởi động. Nó **không nhất thiết đầy đủ mọi launcher/application argument**, và một số options chỉ thuộc launcher, không thuộc VM. Khi muốn biết flags cuối cùng, ưu tiên `PrintFlagsFinal`/`jcmd` hơn là parse một chuỗi Java args.

## 10. Các trạng thái vòng đời: active, deprecated, obsolete, expired

Các `-XX` options không có tuổi thọ vô hạn. OpenJDK HotSpot có cơ chế quản lý flags, mô tả trong nguồn `arguments.cpp`.

| Trạng thái | Thường có hiệu lực không? | Dấu hiệu |
| --- | --- | --- |
| **Active/supportable** | Có theo semantics và constraints | Launcher nhận flag và VM áp dụng |
| **Deprecated** | Thường còn effect | Warning, được cảnh báo có thể bị xóa trong bản sau |
| **Obsolete** | **Không còn effect** | VM có thể vẫn nhận cú pháp để tương thích, in warning và bỏ qua |
| **Expired/removed** | Không còn được nhận như option hợp lệ | VM từ chối startup, thường báo unrecognized flag |
| **Experimental** | Có thể có effect nhưng chưa ổn định | Cần `UnlockExperimentalVMOptions` nếu flag bị khóa |
| **Diagnostic** | Có thể có effect, dùng cho điều tra | Cần `UnlockDiagnosticVMOptions` đối với flags bị khóa |

**Phân biệt:** Một option bị obsolete vẫn có thể cho exit code 0. Do đó **startup success không đủ** để kết luận flag đang hoạt động. Nếu có warning “Ignoring option...” hoặc tương đương, phải xử lý cấu hình.

### Ví dụ về cờ bị loại bỏ theo release

Trên HotSpot JDK 17:
- `-XX:MaxPermSize=...` và `-XX:PermSize=...` là options lịch sử của PermGen đã bị loại bỏ; JVM hiện đại dùng metaspace.
- `-XX:+TraceClassLoading` bị loại bỏ, có replacement `-Xlog:class+load=info` trong Unified Logging.
- `--illegal-access=...` đã obsolete trong JDK 17: có thể được nhận nhưng không còn nới encapsulation như những release trước.

Trên JDK 24, một số `MaxRAMFraction`-style flags cũ đã bị loại bỏ, hướng tới các flags `MaxRAMPercentage` tương ứng. Trong JDK mới hơn, một flag khác có thể đi qua deprecated → obsolete → expired, không phải mọi flag theo chính xác một release schedule. Đọc danh sách **Deprecated / Obsolete / Removed Options** của **đúng JDK**.

### Khi migrate từ JDK 17 lên 21/25/26

Không suy từ “flag từng chạy trên Java 8” thành “flag vẫn hợp lệ trên Java 25”. Khi nâng version:
1. Liệt kê toàn bộ command line và environment-injected options.
2. Tra đúng Java launcher docs và release notes.
3. Tạo runtime image test đúng vendor/architecture.
4. Khởi động bằng exact production flags, capture exit code, warnings.
5. Đọc `PrintFlagsFinal`/`VM.flags` và subsystem logs.
6. Loại deprecated/obsolete flags và tìm replacement có tài liệu.
7. Chạy integration/performance tests trước rollout.

Nếu cờ đổi semantics giữa các JDK, có thể cần **version-conditional config** chứ không chỉ xóa warning.

## 11. Diagnostic và experimental flags: unlock đúng cách

Một số HotSpot options nằm trong các nhóm **diagnostic** hoặc **experimental** và không sẵn cho production command line nếu không unlock.

~~~bash
java -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation -version
java -XX:+UnlockExperimentalVMOptions -XX:+SomeExperimentalFlag -version
~~~

Dòng thứ hai là **minh họa cú pháp**, `SomeExperimentalFlag` không phải tên cờ thật và không được chạy nguyên vẹn. Với diagnostic flag như `LogCompilation`, cần áp dụng đúng yêu cầu của bản HotSpot. Đặt flag unlock **trước flags cần unlock** giúp tránh lỗi parse ở những versions tương ứng.

**Không có nghĩa:** “chỉ cần bật UnlockDiagnosticVMOptions là mọi flag bí mật được hỗ trợ và an toàn”. Diagnostic/experimental flags có thể không nằm trong API ổn định và có thể đổi/xóa. Chỉ bật khi có nhu cầu đo/điều tra rõ ràng, kèm rollout và rollback plan.

### Đừng dùng `-XX:+IgnoreUnrecognizedVMOptions` để che lỗi cấu hình

Một số HotSpot builds có flag cho phép bỏ qua options không nhận ra. Việc sử dụng nó để “đẩy một file JVM_FLAGS chung cho mọi JDK” có thể che sự cố:
- JVM vô tình không bật GC mode mà team kỳ vọng;
- heap/native memory bound bị bỏ qua;
- container chạy với default ngoài dự kiến;
- warning mất khỏi tuyến CI và chỉ lộ ra khi service quá tải.

Đối với flag ảnh hưởng correctness/resource budget, **fail-fast và xác minh** tốt hơn là silently ignore. Nếu một library/agent sử dụng điều kiện version khác nhau, hãy cấu hình nhánh theo runtime cụ thể.

## 12. Các cờ tài nguyên thiết yếu: heap không bằng RSS

Một ứng dụng JVM sử dụng nhiều vùng memory:

~~~text
JVM PROCESS
 |
 +-- Java heap        -Xms, -Xmx, MaxRAMPercentage
 +-- Class metadata   Metaspace, Compressed Class Space
 +-- JIT code cache   ReservedCodeCacheSize
 +-- Thread stacks    -Xss for eligible platform threads
 +-- Direct/native    ByteBuffers, JNI, JVM internals
 +-- GC bookkeeping   card tables, remembered sets, etc.
 +-- OS mapped data   CDS archives, mapped files, libraries
 |
 +-- CPU threads/scheduler limits
      ActiveProcessorCount, GC thread ergonomics, container quota
~~~

`-Xmx1g` chỉ hạn chế một phần **Java heap**, không phải tổng process resident set size. Một container có limit 1 GiB và `-Xmx1g` dễ gặp OOM kill do native components, ngay cả khi Java heap chưa đầy.

| Mục tiêu | Các flags thường xem | Rủi ro khi cấu hình |
| --- | --- | --- |
| Maximum Java heap | `-Xmx`, `-XX:MaxRAMPercentage` | Xmx thấp: GC pressure/OOME; cao: RSS/native headroom thiếu |
| Initial heap | `-Xms` | Khởi tạo lớn/committed memory; quá nhỏ có expansion churn |
| Thread stack | `-Xss` | Stack quá nhỏ: StackOverflowError; quá lớn: native memory pressure |
| Class metadata | `-XX:MaxMetaspaceSize` | Class loading fail nếu quá chặt |
| Direct memory | `-XX:MaxDirectMemorySize` | Native/direct buffer limits, tùy JDK semantics |
| JIT code cache | `-XX:ReservedCodeCacheSize` | Compilation có thể bị giới hạn khi quá thấp |
| CPU ergonomics | `-XX:ActiveProcessorCount` | Không cấp thêm CPU, chỉ thay logic sizing/policy |
| Collector | `-XX:+UseG1GC`, `-XX:+UseZGC` | Thuật toán/flags có version-specific behavior |
| GC logs | `-Xlog:gc*` | Giúp quan sát, tạo overhead/disk logs |

**Không nên** đặt đồng thời nhiều flags điều khiển cùng mục tiêu mà không hiểu thứ tự ưu tiên. Khi có explicit `-Xmx`, cờ `MaxRAMPercentage` có thể không còn quyết định heap maximum như lúc không đặt Xmx.

## 13. Cấu hình trong containers: một ví dụ vận hành

Giả sử container có memory limit 2 GiB, ứng dụng có heap live set vài trăm MiB và sử dụng direct buffers/native libraries. Một thử nghiệm có thể bắt đầu:

~~~bash
java -Xms256m -Xmx1g -XX:+UseG1GC -Xlog:gc*=info -jar service.jar
~~~

Đây là **mẫu thăm dò**, không phải khuyến nghị số tối ưu. Chỉ đặt 1 GiB heap nếu đã đo:
- peak live heap và allocation spikes;
- thread count/stacks;
- metaspace/class loading;
- direct/native memory;
- JIT code cache, GC native overhead;
- container RSS/working set và CPU throttling.

Nếu container limit 2 GiB, **đừng dùng một bảng cờ Java thay cho thực tế memory metrics**. Với process đang chạy, có thể dùng:
- `jcmd PID GC.heap_info` để xem heap picture;
- `jcmd PID VM.flags` để xem VM config;
- JFR, GC logs và monitoring systems;
- `jcmd PID VM.native_memory summary` nếu process đã bật Native Memory Tracking phù hợp từ startup, ví dụ `-XX:NativeMemoryTracking=summary`.

**NMT không thể bật đầy đủ tùy ý sau khi JVM đã chạy** trên nhiều HotSpot configurations và có overhead. Không tự ý bật `NativeMemoryTracking=detail` trên production trước khi đánh giá chi phí.

### Vì sao ActiveProcessorCount cần thận trọng?

`-XX:ActiveProcessorCount=2` ảnh hưởng JVM's view of processors trong các ergonomics như sizing worker pools, nhưng không phải Linux `cgroups` CPU quota, không khóa ứng dụng chỉ sử dụng đúng hai OS CPUs và không ngăn native libraries tạo threads. Hãy đo process CPU thực tế và xem container metrics.

## 14. Ví dụ Java 17+: chứng minh launcher vs system property vs app args

Tạo file **`JvmOptionProbe.java`**:

~~~java
import java.util.Arrays;

public class JvmOptionProbe {
    public static void main(String[] args) {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;

        String mode = System.getProperty("demo.mode", "missing");
        int sum = 19 + 23;

        System.out.println("mode=" + mode);
        System.out.println("assertions=" + assertionsEnabled);
        System.out.println("args=" + Arrays.toString(args));
        System.out.println("sum=" + sum);
    }
}
~~~

Biên dịch:

~~~bash
javac --release 17 JvmOptionProbe.java
~~~

Chạy trên JDK 17+:

~~~bash
java -ea -Ddemo.mode=study -Xms32m -Xmx128m JvmOptionProbe alpha beta
~~~

**Stdout nghiệp vụ chính xác:**

~~~text
mode=study
assertions=true
args=[alpha, beta]
sum=42
~~~

Giải thích:
- `-ea`/`--enable-assertions` bật đánh giá `assert` cho class trong ngữ cảnh này; assignment trong assert chỉ là **demo**, không phải pattern viết production logic.
- `-Ddemo.mode=study` tạo system property, không đi vào `args`.
- `-Xms32m` và `-Xmx128m` gửi option cho HotSpot, không đi vào `args`.
- `alpha beta` nằm sau main class, đi vào `String[] args`.
- Phép cộng 19+23 có thể được `javac` gấp thành constant, nhưng business output vẫn 42.

Thử đặt `-Xmx128m` sau class:

~~~bash
java -ea -Ddemo.mode=study JvmOptionProbe alpha -Xmx128m
~~~

Ứng dụng sẽ nhận `args=[alpha, -Xmx128m]`; token này **không phải JVM heap option** ở vị trí ấy. Đừng gán nghĩa cho substring `-Xmx` mà bỏ qua thứ tự argument.

## 15. Ví dụ Java 17+: quan sát effective heap trong ứng dụng

Trong HotSpot, có thể dùng management API để truy vấn một số VM flags. Tạo file **`HotSpotFlagProbe.java`**:

~~~java
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import java.lang.management.ManagementFactory;

public class HotSpotFlagProbe {
    public static void main(String[] args) {
        HotSpotDiagnosticMXBean bean =
                ManagementFactory.getPlatformMXBean(
                        HotSpotDiagnosticMXBean.class);

        if (bean == null) {
            System.out.println("hotspotBeanAvailable=false");
            return;
        }

        VMOption option = bean.getVMOption("MaxHeapSize");
        long bytes = Long.parseLong(option.getValue());

        System.out.println("hotspotBeanAvailable=true");
        System.out.println("maxHeapMiB=" + (bytes / 1024 / 1024));
        System.out.println("origin=" + option.getOrigin());
    }
}
~~~

Biên dịch và chạy trên **HotSpot JDK 17+**:

~~~bash
javac --release 17 HotSpotFlagProbe.java
java -Xmx128m HotSpotFlagProbe
~~~

Khi HotSpotDiagnosticMXBean được hỗ trợ và `-Xmx128m` được áp dụng, ví dụ thường in:

~~~text
hotspotBeanAvailable=true
maxHeapMiB=128
origin=VM_CREATION
~~~

Đây là **expected output trên HotSpot JDK 17+ chuẩn với cấu hình nêu trên**, không phải contract của mọi JVM vendor hoặc tương lai. Bean có thể không có ở một VM khác, và `VMOption.Origin` phản ánh cơ chế config mà runtime báo cáo. Đừng dùng dấu bằng giữa `Runtime.getRuntime().maxMemory()` và `MaxHeapSize` như một phép kiểm tuyệt đối trên mọi collector/build, vì ergonomics và alignment.

### Phân biệt management-origin với effective behavior

`origin=VM_CREATION` thường biểu thị một option được set ở JVM startup, nhưng không chứng minh business performance. Một option có thể đã được nhận và có origin phù hợp nhưng chỉ được dùng khi subsystem cụ thể hoạt động. Với GC, xác minh tiếp bằng `-Xlog:gc*`; với heap, xem actual memory metrics; với JIT, đọc compilation diagnostics.

## 16. Phương pháp xác nhận cờ bằng smoke test độc lập

Nếu muốn biết một flag Boolean có được HotSpot build hiện tại chấp nhận:

~~~bash
java -XX:+UseG1GC -version
~~~

Kiểm tra **exit status** và output. Khi thành công, lệnh chứng minh:
- option được launcher/VM chấp nhận;
- VM có thể khởi động tới bước in version;
- không có immediate syntax/constraint error trong configuration đó.

Nhưng chưa chứng minh:
- application thực chạy đúng;
- GC event cụ thể đã xảy ra;
- heap limit theo container không gây OOM khi tải;
- throughput hoặc latency có cải thiện.

Nên thêm:

~~~bash
java -XX:+UseG1GC -Xlog:gc*=info -version
java -XX:+UseG1GC -XX:+PrintFlagsFinal -version
~~~

Với flags Boolean khác, kiểm tra tên đúng, nhóm flag product/diagnostic/experimental và version-specific restrictions. Nếu parser trả lỗi, ghi exact error/exit code thay vì thêm `-XX:+IgnoreUnrecognizedVMOptions` để che.

### Cờ GC có thể đổi ý nghĩa theo release

Ví dụ ZGC:
- JDK 21 giới thiệu Generational ZGC dưới dạng mode có thể chọn;
- JDK 23 chọn generational mode mặc định khi đã chọn ZGC;
- JDK 24 loại bỏ non-generational ZGC;
- một số legacy flags như `ZGenerational` tiến tới obsolete/expired ở JDK mới.

**Không nên copy bộ cờ chuyên biệt của một JDK rồi áp dụng nguyên sang JDK khác.** Nếu cần một app image chạy nhiều release, tạo version-specific JVM_ARGS và smoke-test mỗi image.

## 17. System properties, environment variables và injected JVM options

Ba cơ chế khác nhau:

| Nguồn | Ví dụ | Ai xử lý |
| --- | --- | --- |
| CLI system property | `-Ddemo.mode=study` | Launcher/VM cung cấp `System.getProperty` |
| Environment variable ứng dụng | `DEMO_MODE=study` | OS/environment; app đọc `System.getenv` |
| `JDK_JAVA_OPTIONS` | `-Ddemo.mode=study -Xmx128m` | Java launcher prepend trước parsed CLI args |
| `JAVA_TOOL_OPTIONS` | `-Ddemo.mode=study` | JVM startup instrumentation/config path theo implementation/contract |
| `_JAVA_OPTIONS` | Cú pháp nội bộ thường thấy ở một số vendor | Không dùng làm cơ chế portable được cam kết công khai |

**Lưu ý JDK_JAVA_OPTIONS:** theo tài liệu Java command, nó được **prepend** vào options của launcher, in thông báo nhắc trên stderr và **không cho** các options chọn main class hoặc thoát launcher sớm như `-jar`, `-h`. Một `@argument-file` được cho phép theo launcher semantics.

**Lưu ý JAVA_TOOL_OPTIONS:** thường dùng để đưa VM options cho processes có startup được điều khiển bởi một wrapper, nhưng không có cùng quyền xử lý các launcher-only options như JDK_JAVA_OPTIONS; một số runtime environments còn giới hạn injection vì lý do bảo mật.

### Lỗi “đã xóa flag khỏi startup script nhưng vẫn thấy”

Có thể flag đang được inject qua:
- `JDK_JAVA_OPTIONS` hoặc `JAVA_TOOL_OPTIONS`;
- IDE configuration/Gradle JVM args;
- service wrapper/application server;
- Docker `ENTRYPOINT`/`CMD`;
- Helm chart config map/env;
- Kubernetes mutating admissions;
- shell alias hoặc script kế thừa;
- vendor-specific launcher.

Khi điều tra, xem **actual process VM.command_line** và environment injection thay vì chỉ đọc một file startup. Không công khai secrets có thể nằm trong `-Dpassword=...` khi chia sẻ logs; command lines/JVM input args có thể lộ thông tin nhạy cảm.

## 18. Argument files và quoting: tránh lỗi khó nhìn

Java launcher hỗ trợ **@argument file** để lưu nhiều options. Ví dụ file văn bản `vm.args`:

~~~text
-Xms64m
-Xmx256m
-Ddemo.mode=study
-ea
JvmOptionProbe
alpha
beta
~~~

Đây là **nội dung file tham số của launcher**, không phải application data. Nếu `vm.args` tồn tại:

~~~bash
java @vm.args
~~~

Thực hiện tương đương chương trình ở mục 14 nếu môi trường và quoting phù hợp. Cần lưu ý:
- Đây là feature **Java launcher**, không phải mọi wrapper/VM embedding API đều hiểu `@vm.args`.
- Encoding, escaping/quoting và wildcard expansion có quy tắc riêng; xem phần *java Command-Line Argument Files* của đúng JDK.
- Các options trước main class vẫn là launcher/VM options; các tokens sau class là app args.
- Nếu cấu hình chứa whitespace trong property value, dùng quoting đúng shell/argument-file rules.
- Không viết password/token vào argument file có quyền đọc rộng hoặc commit secrets vào source control.

Một số tools như Maven/Gradle cũng sử dụng `@argfile` nhưng **đừng tự động giả định quoting rules/option parser của Java launcher giống toàn bộ tools khác**.

## 19. Cách kiểm tra trên Windows PowerShell

Xác định binary đang dùng:

~~~powershell
Get-Command java
java -version
java -X
~~~

Xem flags có liên quan (lọc output; có thể cần cả stderr tùy tool):

~~~powershell
java -XX:+PrintFlagsFinal -version 2>&1 |
    Select-String "MaxHeapSize|InitialHeapSize|UseG1GC|ActiveProcessorCount"
~~~

Khi cần kiểm tra PID:

~~~powershell
jcmd
jcmd 12345 VM.command_line
jcmd 12345 VM.flags -all
~~~

**12345 là PID minh họa**, phải thay PID thật. Không đọc kết quả từ một Java process khác rồi gán cho service cần điều tra. `jcmd` có thể cần quyền tương ứng (cùng user, attach permissions).

PowerShell có xử lý quoting/escaping khác Bash. Đặc biệt với properties chứa whitespace, hãy kiểm tra argument thực mà JVM nhận bằng Java test app thay vì chỉ dựa vào cú pháp trông hợp lý.

## 20. Cách kiểm tra trên Linux/macOS

~~~bash
command -v java
java -version
java -X
java -XX:+PrintFlagsFinal -version
~~~

Ví dụ lọc:

~~~bash
java -XX:+PrintFlagsFinal -version 2>&1 |
    grep -E 'MaxHeapSize|UseG1GC|ActiveProcessorCount'
~~~

Dùng `jcmd PID VM.command_line` nếu JVM đang chạy và attach được. Trên container, PID trong host namespace có thể khác PID bên trong container; chạy diagnostics trong đúng namespace/context.

**Không dùng output `ps` làm nguồn duy nhất:** một số wrappers/launchers có thể thay đổi argv hiển thị, và environment-injected flags không nhất thiết xuất hiện đầy đủ ở những công cụ OS thông thường.

## 21. Phân biệt defaults, command line và ergonomics

HotSpot thường tự tính nhiều values theo:
- số logical processors mà JVM nhận diện;
- heap budget/memory limit;
- GC được chọn;
- OS/architecture;
- JDK build, flavor và VM tuning;
- tỷ lệ CPU/quota/container support;
- quan hệ ràng buộc giữa các flags.

Ví dụ, nếu không đặt `-Xmx`, `MaxHeapSize` được HotSpot chọn theo ergonomics. Khi đặt `-Xmx512m`, giá trị cuối khác. Nhưng không phải mọi default value được in là “đã dùng ở runtime”: một flag thuộc collector không được chọn có thể vẫn tồn tại và có giá trị mặc định.

### Tại sao cần nhìn origin?

Cột provenance giúp phân biệt:
- `{default}`: giữ default trong build/context;
- `{ergonomic}`: được VM điều chỉnh theo heuristic;
- `{command line}`: được set qua startup arguments;
- các origins khác có thể liên quan environment, management hoặc attach.

Định dạng/tên origins phụ thuộc build. Không suy rằng source luôn được biểu diễn cùng string qua JDK versions. Nếu nhiều layers thiết lập cùng flag, giá trị cuối và actual command line cần được xem cùng nhau.

### Một cờ không thay thế metric

`-XX:MaxRAMPercentage=65` không chứng minh JVM dùng đúng 65% host RAM; nó là một **input to heap-sizing policy**. `-XX:+UseG1GC` không chứng minh latency <200ms; `-XX:ActiveProcessorCount=2` không giới hạn process chỉ chạy trên hai CPU cores.

Tách *configuration intent*, *effective VM option*, *subsystem behavior* và *business outcome*. Đây là nền tảng của mọi bài kiểm tra cấu hình tài nguyên nghiêm túc.

## 22. Case study: JVM không khởi động sau khi nâng JDK

Một service từng dùng:

~~~text
JAVA_OPTS="-Xms512m -Xmx2g -XX:MaxRAMFraction=4 -XX:+TraceClassLoading"
~~~

Đây là **cấu hình ví dụ lịch sử**, không phải tập flags nên tiếp tục dùng. Trên JDK mới, `TraceClassLoading` hoặc `MaxRAMFraction` có thể bị loại bỏ và làm VM fail.

Quy trình xử lý:
1. Lấy exact stderr, exit code, JDK vendor/build/runtime image.
2. Xác định JVM options đến từ `JAVA_OPTS` của wrapper hay môi trường trực tiếp; `JAVA_OPTS` **không phải biến mà java launcher tự nhận theo chuẩn**, chỉ có effect nếu script sử dụng nó.
3. Chạy mỗi nhóm flags trên **exact JDK**; không sửa production ngay.
4. Tra phần *Removed Options* của release đang dùng.
5. Thay `-XX:+TraceClassLoading` bằng Unified Logging `-Xlog:class+load=info` nếu mục tiêu là quan sát class loading.
6. Cân nhắc thay `MaxRAMFraction` bằng `MaxRAMPercentage` **khi không còn explicit -Xmx**, thay vì giữ một flag thừa.
7. Chạy smoke test `java ... -version`, rồi start ứng dụng với test traffic.
8. Đối chiếu `PrintFlagsFinal`, `VM.command_line`, GC/heap/CPU metrics.
9. Ghi version-conditional config và rollback plan.

**Không chỉ thêm** `IgnoreUnrecognizedVMOptions` để service start, bởi bạn có thể mất các ràng buộc memory mà deployment đang dựa vào.

## 23. Tình huống cờ được nhận nhưng kết quả ngược kỳ vọng

### A. Đặt heap đúng nhưng container vẫn OOMKilled

Nguyên nhân có thể là native memory, metaspace, stacks, direct buffers, JIT code cache, file mappings và GC overhead. Giải pháp không phải chỉ tiếp tục giảm `-Xmx` một cách mù quáng; cần đo NMT/RSS/heap và xét budget toàn process.

### B. Đặt ActiveProcessorCount=2 mà nhìn process vẫn có nhiều threads

Flag ảnh hưởng JVM ergonomics, không phải strict OS thread cap. GC threads, JIT threads, application workers và native libraries vẫn tồn tại theo chính sách riêng.

### C. Bật PrintFlagsFinal nhưng không thấy user-defined property

`-Ddemo.mode=study` là Java system property, không phải `-XX` flag. Kiểm tra bằng Java API hoặc `-XshowSettings:properties` theo JDK thay vì tìm "demo.mode" trong HotSpot flag table.

### D. Đặt -Xmx trước JAR nhưng app báo nó là input

Nếu thật sự JVM thấy flag trước `-jar` và app báo `-Xmx` là argument, có thể launcher wrapper đã reorder tokens. Đọc actual command line từ `VM.command_line` và kiểm tra script/container ENTRYPOINT.

### E. Flag PrintFlagsFinal có value nhưng tính năng không hoạt động

Cờ có thể chỉ là heuristic, ở trạng thái obsolete (không còn effect), hoặc subsystem không được chọn. Đọc warnings, docs và log subsystem; không suy từ một value printed.

### F. Cùng flags nhưng hai JDK cho heap khác nhau

Defaults/ergonomics/container support/architecture/collector có thể khác. Nếu muốn đối chứng hiệu năng, cố định JDK hoặc ghi rõ version là một biến độc lập.

## 24. Quy trình kiểm định JVM args trong CI/CD

Một pipeline đáng tin nên dùng đúng runtime image production.

**Tầng A — Syntax / startup smoke:**
- Capture `java -version`.
- Chạy `java [JVM_FLAGS] -version` trên exact binary.
- Assert exit code = 0, không có warning không được chấp nhận.
- Không dùng `IgnoreUnrecognizedVMOptions` trừ khi đã chứng minh lý do.

**Tầng B — Effective configuration:**
- Chạy `java [JVM_FLAGS] -XX:+PrintFlagsFinal -version`.
- Lấy các flags quan trọng: `MaxHeapSize`, `InitialHeapSize`, selected GC, `ActiveProcessorCount`, metaspace/code cache nếu cần.
- So với budget và constraints của deployment, không compare nguyên văn output giữa releases.

**Tầng C — Application smoke:**
- Khởi động JAR/class thật, kiểm tra entrypoint, module/classpath và app arguments.
- Health check sẵn sàng, basic request, timeout, error logs, resource availability.
- Kiểm tra đúng runtime process bằng `jcmd PID VM.command_line` khi có quyền.

**Tầng D — Load/performance verification:**
- GC logs, heap/RSS/CPU throttling, NMT (nếu bật), p95/p99 end-to-end latency.
- Đặt budgets và limits, đừng dùng flags như KPI.
- Canary + rollback khi thay JDK/GC/heap.

**Tầng E — Configuration governance:**
- Lưu exact JDK vendor/build, JVM args, environment-provided args và generated manifests.
- Có owner cho mỗi non-default flag: vì sao bật, baseline đo, cách verify, kế hoạch xóa.
- Thêm test cho cả khi thiếu flag và khi bị deprecated/obsolete.
- Xem xét secrets trong system properties và logs.

## 25. Không dùng flags theo kiểu “bộ sưu tập tối ưu hiệu năng”

Cấu hình:

~~~text
-Xms4g
-Xmx4g
-XX:+AlwaysPreTouch
-XX:+UseG1GC
-XX:ActiveProcessorCount=2
-XX:MaxMetaspaceSize=128m
-XX:ReservedCodeCacheSize=64m
~~~

có thể phù hợp một số experiments nhưng **không phải cấu hình tốt cho mọi Java server**. Ví dụ:
- `-Xms=-Xmx` có thể làm tăng committed memory; đổi lại heap size ổn định hơn trong tình huống nhất định.
- `AlwaysPreTouch` có thể làm startup dài hơn và memory commitment khác.
- Metaspace 128 MiB có thể quá nhỏ cho ứng dụng dùng nhiều frameworks/proxies.
- Code cache 64 MiB có thể chặn JIT compilation trên workload lớn.
- ActiveProcessorCount=2 có thể làm GC/JIT/pool heuristics dưới mức hardware thực nếu được đặt sai.

Một configuration nên có **tài nguyên mục tiêu, profile workload, chứng cứ đo và rollback**. Đừng giữ flags chỉ vì copy từ một bài viết về Java 8.

## 26. Bài tập thực hành

### Bài 1 — Phân loại options

Phân loại trong lệnh:

~~~bash
java -Ddemo.env=test -Xmx512m -XX:+UseG1GC -jar app.jar hello
~~~

**Đáp án:** `-D` standard launcher property; `-Xmx` extra; `-XX:+UseG1GC` advanced; `-jar` standard launch mode; `hello` application argument.

### Bài 2 — Sai vị trí

Lệnh:

~~~bash
java -jar app.jar -Xmx512m
~~~

**Đáp án:** `-Xmx512m` đi vào app args theo launcher target grammar, không cấu hình maximum heap.

### Bài 3 — Boolean flag

Muốn bật G1 trong HotSpot dùng `-XX:+UseG1GC`, không dùng `-XX:UseG1GC` tùy tiện. Để tắt một Boolean flag, cú pháp cơ bản `-XX:-FlagName` — nhưng **đừng tắt G1** nếu không xác định GC thay thế và constraints.

### Bài 4 — Value flag

Muốn gợi ý JVM tự sizing heap từ memory budget với `MaxRAMPercentage`, dùng mẫu `-XX:MaxRAMPercentage=65`, không phải `-XX:+MaxRAMPercentage`.

### Bài 5 — Cờ có hỗ trợ hay không?

Đề xuất ba kiểm tra:
1. Tra Java command docs của **đúng release**.
2. Chạy một smoke test bằng exact binary và đọc exit status/warnings.
3. Kiểm tra `PrintFlagsFinal`/`jcmd` và feature logs trong application workload.

### Bài 6 — Vì sao `-Xmx=1g` chưa đủ khống chế container 1 GiB?

**Đáp án:** Heap không phải toàn bộ process memory. Native memory, metaspace, stacks, direct buffers, JIT và mappings còn chiếm thêm.

### Bài 7 — Vì sao `-XX:+IgnoreUnrecognizedVMOptions` rủi ro?

**Đáp án:** Nó có thể che cờ bị xóa/đánh sai, khiến JVM chạy với defaults thay vì resource limits dự kiến.

### Bài 8 — Phân biệt PrintFlagsFinal và VM.command_line

**Đáp án:** PrintFlagsFinal giúp xem effective VM flag values tại startup; VM.command_line phản ánh command-line JVM process đã nhận. Kết hợp cả hai để biết ý định và kết quả.

### Bài 9 — Vì sao `java -X` không liệt kê mọi -XX?

**Đáp án:** Nó là help cho extra options được launcher công bố; các -XX flags có nhóm/level riêng, nhiều flags còn bị gated/implementation-specific.

### Bài 10 — Một cờ obsolete được parser chấp nhận, có nghĩa nó còn hoạt động không?

**Đáp án:** Không. Obsolete thường được chấp nhận rồi bỏ qua với warning. Phải xem lifecycle và behavior.

### Bài 11 — -D không thấy trong PrintFlagsFinal?

**Đáp án:** Vì system property khác VM flag. Kiểm tra bằng `System.getProperty` hoặc VM/launcher property diagnostics thích hợp.

### Bài 12 — Một process chạy trong Docker nhận MaxHeapSize nhỏ hơn mong đợi dù host có 64 GiB?

**Đáp án:** Container memory limit/HotSpot ergonomics/flags có thể khiến JVM chỉ xem budget nhỏ hơn host. Xác minh exact process flags/container limits.

### Bài 13 — Java source-level preview option có phải -XX advanced flag?

**Đáp án:** Không. `--enable-preview` thuộc launcher/compiler workflow liên quan preview language/API features; nó không đồng nghĩa experimental VM GC flag.

### Bài 14 — Cần unlock flags chẩn đoán bằng cách nào?

**Đáp án:** Dùng `-XX:+UnlockDiagnosticVMOptions` trước những flags được đánh dấu diagnostic, trên HotSpot build có hỗ trợ. Không tự đặt cờ không tồn tại.

### Bài 15 — Một flag từng chạy trên JDK 8 nhưng JDK 25 fail: JVM bị lỗi?

**Đáp án:** Không nhất thiết. Flag có thể đã removed/expired. Tra docs đúng JDK, thay bằng option được hỗ trợ, test behavior.

## 27. Bản đồ tư duy

~~~text
                     java LAUNCHER
                           |
         +-----------------+-----------------+
         |                 |                 |
      STANDARD            -X               -XX
 -D, -cp, -jar         -Xms/-Xmx       +Flag / -Flag
 --module-path         -Xss, -Xlog     Flag=value
 --version, -ea        -Xint, -X       GC/JIT/memory
         |                 |                 |
         +-----------------+-----------------+
                           |
                 EXACT JDK VENDOR/BUILD
                           |
                   ARE FLAGS ACCEPTED?
               /                         \
             NO                           YES
             |                             |
        stop; fix                    ACTIVE vs OBSOLETE?
        version/syntax              /               \
                                   warning?         effect?
                                      |                |
                                inspect docs       PrintFlagsFinal
                                                   jcmd VM.flags
                                                      |
                                               SUBSYSTEM LOGS
                                                      |
                                                RESOURCE METRICS
                                                      |
                                               APPLICATION SLO
                           |
                  DO NOT CONFUSE WITH:
             app arguments after main/-jar
             env JAVA_TOOL_OPTIONS
             JDK_JAVA_OPTIONS
             process RSS vs Java heap
             parser acceptance vs semantic effect
~~~

**Tổng kết:** Tùy chọn **standard**, **-X** và **-XX** khác nhau về parser, phạm vi, mức ổn định và mục đích. Trước khi đưa một flag vào cấu hình tài nguyên, hãy kiểm tra **đúng Java binary/version**, đọc `java --help`/`java -X` và tài liệu release, chạy startup smoke, xem **effective VM flags** bằng `PrintFlagsFinal` hoặc `jcmd`, rồi xác nhận behavior và resources bằng logging/metrics. Một flag đã được chấp nhận vẫn có thể obsolete hoặc không phù hợp workload; một flag đã được set không phải bảo đảm hiệu năng. Hãy quản lý JVM args như **cấu hình vận hành có phiên bản, có bằng chứng và có rollback**, không phải tập hợp các mẹo không kiểm chứng.

## Tài liệu tham khảo

- [Oracle JDK 17 — The java Command, Standard/Extra/Advanced options](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).
- [Oracle JDK 21 — The java Command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).
- [Oracle JDK 25 — The java Command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html).
- [Oracle JDK 26 — The java Command](https://docs.oracle.com/en/java/javase/26/docs/specs/man/java.html).
- [Dev.java — Java Application Launcher: Standard, -X and -XX options](https://dev.java/learn/jvm/tools/core/java/).
- [Oracle JDK 17 — jcmd utility](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html).
- [Oracle JDK 17 — jinfo utility](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jinfo.html).
- [Oracle JDK 17 — Java ManagementFactory and RuntimeMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ManagementFactory.html).
- [Oracle JDK 17 — HotSpotDiagnosticMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.management/com/sun/management/HotSpotDiagnosticMXBean.html).
- [Oracle JDK 17 — VMOption and Origin](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.management/com/sun/management/VMOption.html).
- [OpenJDK HotSpot runtime — arguments.cpp, deprecated/obsolete/expired flags](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/runtime/arguments.cpp).
- [OpenJDK — JEP 158, Unified JVM Logging](https://openjdk.org/jeps/158).
- [OpenJDK — JEP 271, Unified GC Logging](https://openjdk.org/jeps/271).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-003">-Xms và -Xmx: dung lượng heap ban đầu, tối đa và chi phí đánh đổi</a>

<details>
<summary>Xem chi tiết</summary>

# `-Xms` và `-Xmx`: dung lượng heap ban đầu, tối đa và chi phí đánh đổi

**`-Xms`** và **`-Xmx`** là hai tham số JVM thường được dùng để định kích thước **Java heap**, nơi nhiều objects/arrays của ứng dụng được cấp phát và quản lý bởi garbage collector (GC). Chúng trả lời **hai câu hỏi khác nhau**:

- **`-Xms<size>`:** heap **ban đầu** mà HotSpot chuẩn bị/quản lý lúc khởi động; liên quan mức cam kết bộ nhớ và khả năng mở rộng/thu hẹp heap sau đó.
- **`-Xmx<size>`:** **giới hạn tối đa** của Java heap mà JVM được phép sử dụng, không phải lượng heap đã thực sự dùng và càng **không phải giới hạn tổng RAM** của process.

Ví dụ:

```bash
java -Xms256m -Xmx1g -jar application.jar
```

Yêu cầu JVM bắt đầu với heap gần mức 256 MiB và cho phép heap tăng tới khoảng 1 GiB, tùy rounding, vùng nhớ, collector và cách runtime báo kích thước. **Không nên diễn giải** lệnh này là “ứng dụng cấp phát ngay 256 MiB objects” hay “process không bao giờ dùng quá 1 GiB RAM”.

Bài thuộc nhóm **cấu hình tài nguyên** trong Java/JVM: trọng tâm là hiểu đúng các khái niệm và quan sát được đánh đổi giữa **startup, độ ổn định pause/latency, GC frequency, memory footprint, container OOM và headroom dành cho native memory**. Ví dụ chạy với Java 17+; cờ `-XX` và các chi tiết ergonomics nói tới chủ yếu là **HotSpot JVM**.

## 1. Nhận diện nhanh: hai cờ nhưng bốn lớp kích thước

Một heap có thể có nhiều thước đo tại cùng thời điểm:

| Khái niệm | Tên trong Java Management API | Ý nghĩa |
| --- | --- | --- |
| **Initial** | `MemoryUsage.getInit()` | Dung lượng ban đầu JVM yêu cầu dành cho heap management; có thể không được xác định |
| **Used** | `MemoryUsage.getUsed()` | Bộ nhớ hiện được dùng trong vùng heap theo VM management view |
| **Committed** | `MemoryUsage.getCommitted()` | Dung lượng mà VM đã được bảo đảm có thể sử dụng cho vùng này |
| **Max** | `MemoryUsage.getMax()` | Giới hạn tối đa có thể dùng cho heap, có thể không được xác định ở một số memory pools/VMs |

**`-Xms` gần với Initial**, **`-Xmx` gần với Max**. Nhưng số do API trả có thể được **điều chỉnh theo alignment/GC region size**, không phải luôn khớp từng byte với chuỗi command line. Xem `java.lang.management.MemoryUsage` để hiểu contract API.

Quan hệ thường quan sát trên Java heap:

```text
0 ≤ used ≤ committed ≤ max       (khi max được xác định)

               Max heap (-Xmx)
┌────────────────────────────────────────────────┐
│        Committed (VM đã cam kết)                │
│  ┌──────────────────────────────────────────┐  │
│  │       Used (object/heap occupancy)       │  │
│  │  ┌───────────────────────────────┐       │  │
│  │  │ Live objects + allocations    │       │  │
│  │  └───────────────────────────────┘       │  │
│  │                  committed - used       │  │
│  └──────────────────────────────────────────┘  │
│              max - committed                   │
└────────────────────────────────────────────────┘
```

Hình minh họa **không tỉ lệ**. Với collectors có heap address-space mappings đặc thù và những pools khác nhau, chi tiết `reserved`, `committed` hay cách báo virtual memory khác nhau. Nhưng distinction cơ bản **initial/used/committed/max** là thứ bắt buộc phải nắm.

### Hai đối tượng hay bị nhầm

- **Heap used:** số liệu VM về heap objects và vùng đã dùng.
- **Resident Set Size (RSS):** số trang bộ nhớ vật lý của **toàn tiến trình** đang cư trú theo thống kê OS, có thể gồm heap, native memory, code, stacks, file mappings...
- **Virtual memory reserved:** vùng địa chỉ ảo VM đặt trước, không có nghĩa toàn bộ đã có physical RAM backing.
- **Container memory usage:** chỉ số của cgroup có thể gồm những thành phần khác process RSS, ví dụ page cache; xem metric definition.

**Không lấy `heap used`, `heap committed`, `RSS`, `container memory.current` làm những tên gọi khác nhau của cùng một con số.**

## 2. Cú pháp cơ bản và cách hiểu đơn vị

Các cách ghi phổ biến:

```bash
java -Xms128m -Xmx512m Main
java -Xms1g -Xmx4g Main
java -Xmx1024m Main
```

Trong cờ sizing của HotSpot, suffix `k`, `m`, `g` thường tương ứng các bội **1024**; tài liệu `java` của đúng release là nguồn chuẩn về cú pháp và giới hạn hợp lệ. Với giao diện vận hành, nên ghi rõ **MiB/GiB** thay vì dùng “MB/GB” không nhất quán.

| Option | Mục tiêu |
| --- | --- |
| `-Xms128m` | Yêu cầu initial heap gần 128 MiB |
| `-Xmx512m` | Giới hạn heap tối đa gần 512 MiB |
| `-Xms1g -Xmx1g` | Yêu cầu initial và maximum bằng nhau: “fixed-size heap policy” theo cách dùng phổ biến |
| Chỉ `-Xmx512m` | Max heap được chỉ định; initial heap do VM ergonomics/flags khác quyết định |
| Không chỉ định cả hai | JVM tính sizing theo memory, collector, JVM/version/container ergonomics |

**Quy tắc hợp lệ:** Initial heap không được lớn hơn maximum heap; cấu hình `-Xms2g -Xmx1g` sẽ gây lỗi startup trên HotSpot thay vì tự động đảo các giá trị cho bạn.

### Lệnh đứng trước application entrypoint

```bash
java -Xms128m -Xmx512m -jar app.jar
```

Đúng khi `app.jar` có Main-Class manifest và build tương ứng. Những JVM options đứng **trước** `-jar`/main class. Ví dụ:

```bash
java -jar app.jar -Xmx512m
```

không tương đương: `-Xmx512m` lúc đó thường được chuyển thành **application argument**, không còn là launcher JVM option. Với nhiều script/container wrappers, kiểm tra command line **đúng process**, vì cấu hình tưởng đã truyền có thể bị lược bỏ hoặc chuyển sai vị trí.

## 3. “Ban đầu” không phải “đã dùng”

Nếu chạy:

```bash
java -Xms256m -Xmx1g MyApp
```

ở lúc process mới lên, ứng dụng có thể chỉ có vài chục MiB objects đang sống. **Heap committed** có thể đã gần initial setting; **heap used** thấp hơn rất nhiều. Collector có thể GC/điều chỉnh committed heap khi workload tăng hoặc giảm theo policy.

Một cách diễn đạt đúng:

> “`-Xms256m` yêu cầu kích thước heap ban đầu và chi phối hành vi cam kết/điều chỉnh heap của HotSpot; nó không khởi tạo 256 MiB đối tượng nghiệp vụ và không có nghĩa ứng dụng cần từng ấy live data.”

Điều này đặc biệt quan trọng với môi trường có **nhiều JVM processes**: nếu đặt initial heap quá cao cho hàng trăm processes, aggregate committed/resident footprint có thể làm máy thiếu tài nguyên, kể cả khi mỗi service chỉ giữ một live set nhỏ.

### Reserved, committed và actual physical pages

HotSpot có thể **reserve** một vùng địa chỉ ảo lớn cho heap tối đa, rồi **commit** theo nhu cầu. OS và page allocator quyết định việc backing memory/physical pages, có thể lazy fault. Do đó:

- Reserve địa chỉ ảo không đồng nghĩa RSS tăng tới Xmx.
- Commit không nhất thiết đồng nghĩa tất cả các trang đã **được chạm** ngay ở startup trên mọi nền tảng/collector.
- `-XX:+AlwaysPreTouch` (nếu HotSpot build hỗ trợ) có thể chạm các heap pages đã commit lúc startup, dời một phần chi phí page faults từ runtime sang startup.
- Large pages, GC và OS mapping policy có thể đổi chi tiết.

**Không nên dùng `-Xms` như công cụ “đặt số GB RSS chính xác”**; phải đo RSS/container metrics từ OS và heap data từ JVM.

## 4. “Tối đa” không phải “giới hạn bộ nhớ tiến trình”

Java heap chỉ là một phần JVM process. Mô hình:

```text
CONTAINER / PROCESS MEMORY BUDGET
├─ Java heap (thường giới hạn bởi -Xmx)
├─ Metaspace và class metadata
├─ Code cache / JIT compiled machine code
├─ Thread stacks
├─ Direct ByteBuffers, NIO/native allocations
├─ GC metadata, remembered sets, marking structures
├─ JVM runtime internal native data
├─ Native libraries, JNI, libc arenas, TLS, mmap
├─ Process executable / shared libraries / stacks
└─ Những khoản OS/cgroup accounting liên quan
```

Nếu container limit là **1 GiB** và bạn đặt `-Xmx1g`, **tổng process memory hoàn toàn có thể vượt 1 GiB** khi cộng thêm non-heap. Kernel/container runtime có thể **OOM-kill process** mặc dù JVM chưa ném `OutOfMemoryError: Java heap space`.

### Ngân sách ví dụ để lập kế hoạch

Giả sử container memory limit **1024 MiB**. Bạn thử `-Xmx512m`, để lại **512 MiB headroom danh nghĩa** cho native/JVM/OS-related overhead.

**Đây là phép phân bổ ban đầu, không phải tỷ lệ mặc định bắt buộc.** Service có 1.000 threads, nhiều direct buffers hoặc JNI có thể cần nhiều headroom hơn. Service nhỏ, ít threads và dependency đơn giản có thể dùng heap fraction khác, nhưng đều phải đo.

Cần theo dõi **peak RSS/cgroup usage**, không chỉ heap after GC. Nếu measurement cho thấy non-heap peak 450 MiB và heap gần 512 MiB, tổng có thể tiến sát giới hạn 1024 MiB; chưa tính mọi overhead/uncertainty. Chừa khoảng an toàn cho bursts và hoạt động chẩn đoán.

## 5. Tăng `-Xmx` mang lại điều gì?

Tăng maximum heap có thể giúp:

- Giảm frequency của collections do heap thiếu không gian.
- Cho phép app xử lý **live set lớn hơn**.
- Có headroom cho allocation bursts và các GC concurrency phases.
- Giảm rủi ro `OutOfMemoryError: Java heap space` nếu nguyên nhân thực là heap pressure.
- Tăng throughput trong một số workload vì giảm công việc GC/expansion.

Nhưng đổi lại:

- Có thể tăng memory footprint và tăng cạnh tranh RAM với processes khác.
- Với một số collectors/workloads, chu kỳ collection hoặc **tail latency** có thể khác theo dung lượng và live data, không có quy luật chung “gấp đôi heap → pause gấp đôi”.
- Nếu bật pre-touch/fixed large heap, startup có thể nặng hơn.
- GC log/heap dump lớn hơn; quá trình chẩn đoán tốn resource hơn.
- Nếu JVM process ở container nhỏ, dễ bị OOMKilled do native headroom quá thấp.
- **Memory leak không được sửa**: tăng heap chỉ kéo dài thời gian tới khi out-of-memory.

### Khi nào tăng `-Xmx` thực sự hợp lý?

Nếu thấy nhiều collections dưới tải mà after-GC live heap vẫn có khoảng cần thiết cho ứng dụng, hoặc GC liên tục lấy lại quá ít không gian vì heap nhỏ, tăng max heap **có thể** là một phương án. Nhưng phải phân biệt:

- **Healthy allocation churn:** nhiều objects ngắn hạn; collector xử lý được và SLO đạt.
- **Large legitimate live set:** dữ liệu cần giữ thật sự lớn hơn budget.
- **Memory leak/unbounded retention:** references không được giải phóng theo vòng đời; tăng Xmx chỉ trì hoãn lỗi.
- **Non-heap OOM:** metaspace/direct buffers/native threads; tăng Xmx không giải quyết và có thể làm thiếu headroom hơn.

**Đừng chọn Xmx bằng cảm giác “RAM máy có 16 GB nên cấp cả 16 GB”.** Xem cgroup limit và toàn bộ process/system budget.

## 6. Giảm `-Xmx` có lợi gì và hại gì?

Giảm maximum heap có thể:

- Giảm upper bound Java heap memory của mỗi JVM.
- Cho phép chạy nhiều instance hơn trên cùng node nếu non-heap phù hợp.
- Làm GC bắt đầu thu gom sớm hơn; đôi khi giảm lượng công việc retained/old-generation scan trong một số trường hợp.
- Bộc lộ unbounded retention ở môi trường thử nghiệm nhanh hơn.

Nhưng cũng có thể:

- Tăng GC frequency và GC CPU.
- Làm tăng allocation stalls, promotion failures, evacuation failures hoặc Full GC theo collector.
- Dẫn tới `OutOfMemoryError: Java heap space` khi live data/allocations không còn vừa.
- Giảm throughput, tăng tail latency vì collector thiếu headroom.
- Tăng CPU/energy do thu gom quá thường xuyên.

**Không nên mặc định** giảm Xmx luôn làm p99 tốt hơn vì “GC phải quét ít bộ nhớ”. Một bộ thu gom thiếu không gian có thể rất vất vả, khiến p99 xấu đi.

### Một quy tắc kinh nghiệm đúng hơn

**Xmx phải đủ chứa live set cộng headroom cho allocations và hoạt động GC** của **collector/workload** đang chạy. Dung lượng cần thiết không chỉ là số live objects trong một heap dump duy nhất.

## 7. `-Xms` nhỏ: startup nhẹ hơn, runtime có thể phải mở heap

Nếu initial heap thấp hơn max:

```bash
java -Xms64m -Xmx512m MyApp
```

VM có thể bắt đầu với committed heap nhỏ hơn, rồi **tăng committed heap** khi allocation demand tăng (kèm ràng buộc collector). Những đánh đổi:

**Ưu điểm tiềm năng:**

- Ban đầu giữ ít tài nguyên hơn nếu workload cold/light.
- Có thể phù hợp CLI, short-lived batch, serverless hoặc nhiều JVM trên một node.
- Cho phép VM co giãn/return unused memory nếu collector/VM support.

**Chi phí tiềm năng:**

- Heap expansion/commit có thể gây overhead/latency.
- Initial heap nhỏ có thể tạo nhiều collections ở giai đoạn startup/warm-up.
- JVM có thể phải commit/touch pages khi allocation tăng.
- Startup benchmark có thể trông nhanh hơn nhưng **first useful request** hoặc vài phút đầu lại xấu hơn.

**Quan trọng:** không có bảo đảm “-Xms nhỏ luôn startup nhanh” hoặc “-Xms lớn luôn giảm GC”. Class loading, framework initialization, compiler activity, page faults và filesystem cache cũng ảnh hưởng.

### Chọn initial heap dựa trên gì?

Nếu startup cần load vài trăm MiB objects thật, Xms quá nhỏ có thể dẫn tới churn/expansion. Nếu service chỉ có live heap 50 MiB nhưng đặt Xms nhiều GiB, có thể giữ footprint không cần thiết. Đo **startup class loading/allocation**, **initial GC logs**, **container RSS** và latency để quyết định.

## 8. `-Xms` bằng `-Xmx`: fixed-size heap policy

Cấu hình phổ biến:

```bash
java -Xms512m -Xmx512m MyApp
```

Lợi ích có thể có:

- Giảm một phần overhead do heap resizing/commit tại runtime.
- Có thể làm runtime behavior ổn định hơn trong workload có footprint tương đối cố định.
- Dễ dự tính upper bound Java heap; giảm một biến khi benchmark.
- Với **ZGC**, khi Xms = Xmx thì cơ chế uncommit heap trống bị vô hiệu theo semantics được tài liệu mô tả.

Đánh đổi:

- JVM có thể **commit** nhiều bộ nhớ từ startup dù workload chưa dùng.
- Nếu kết hợp `-XX:+AlwaysPreTouch`, có thể **touch** một vùng heap lớn lúc startup và tăng đáng kể startup latency/RSS.
- Không trả lại nhiều heap memory cho OS trong một số collector/mode.
- Trên node chạy nhiều services, memory pressure tổng có thể xấu hơn.
- Fixed-size **heap** không làm toàn JVM process memory fixed-size.

### Ba nhận định phải phân biệt

1. **`Xms = Xmx`**: yêu cầu initial/maximum heap bằng nhau.
2. **`AlwaysPreTouch`**: yêu cầu JVM touch committed heap pages trước khi cấp phát cho Java application (nếu được hỗ trợ).
3. **`RSS bằng Xmx`**: **không thể suy ra** chỉ từ hai điều trên nếu chưa xét OS, GC, memory mappings và đo thực tế.

Hãy tránh câu “Xms=Xmx sẽ chiếm toàn bộ 4 GB RAM vật lý ngay lập tức” dưới dạng định luật cho mọi collector/OS. Thay bằng số `committed`, `used`, RSS quan sát thực tế.

## 9. G1, Parallel, Serial, ZGC: cùng flags nhưng behavior khác

`-Xms`/`-Xmx` thiết lập các giới hạn tổng quan, **không chọn collector**. GC được lựa chọn bằng JVM ergonomics hoặc option khác, ví dụ `-XX:+UseG1GC` trên HotSpot.

| Collector | Mối quan tâm khi chọn heap |
| --- | --- |
| **Serial GC** | Một GC worker, STW; heap lớn/live set lớn có thể làm pauses đáng kể tùy workload |
| **Parallel GC** | Hướng throughput; dung lượng heap ảnh hưởng GC frequency, promotion và pause/throughput tradeoff |
| **G1 GC** | Phân vùng heap thành regions; Xmx ảnh hưởng số/kích thước regions và space budgets; pause target là mục tiêu, không bảo đảm deadline |
| **ZGC** | Concurrent low-pause collector; cần enough headroom theo allocation rate để tránh stalls, có uncommit khi Xms < Xmx |
| **Shenandoah (nếu build hỗ trợ)** | Concurrent collection, cần điều kiện/space cho concurrent work; flags và availability tùy release |

**Không có “Xmx 2g tối ưu cho mọi GC”**. Khi đổi GC, giữ nguyên heap/CPU/workload để so trước khi thay thêm 5 tham số cùng lúc.

### G1 và ergonomics

- G1 có young/old generations theo regions; nhiều hoạt động phụ thuộc live set/allocations, không chỉ Xmx.
- Heap region sizing và collector heuristics có thể thay khi đổi maximum heap.
- Một Xmx lớn hơn cho phép bớt GC cycles trong một số trường hợp, nhưng cũng có thể thay heap layout/footprint.
- **`-XX:MaxGCPauseMillis` là mục tiêu mềm**, không phải lời hứa pauses luôn dưới threshold.

### ZGC và `-Xms`

Trên HotSpot JDK 17 ZGC hỗ trợ theo platform, tài liệu mô tả cơ chế **uncommit unused heap pages**, nhưng không uncommit xuống dưới Xms. Nếu **Xms = Xmx**, uncommit bị vô hiệu theo cấu hình đó; nếu Xms nhỏ hơn, ZGC có thể trả lại bộ nhớ ít dùng sau delay.

Ví dụ:

```bash
java -XX:+UseZGC -Xms128m -Xmx1g MyApp
```

Chỉ là **lệnh minh họa cho HotSpot JDK/OS/architecture hỗ trợ ZGC**; một số collector/release có lower bound hoặc tính khả dụng khác. Mức heap trong ví dụ **không là khuyến nghị production**.

Flags khác:

```text
-XX:+ZUncommit
-XX:-ZUncommit
-XX:ZUncommitDelay=300
-XX:SoftMaxHeapSize=512m
```

`SoftMaxHeapSize` là **mục tiêu mềm** của ZGC trong những bản hỗ trợ; không thay hard max `-Xmx` và có thể bị vượt khi JVM cần memory để tránh stalls/OOM. **Đừng giả định** ý nghĩa và flags này áp dụng giống hệt cho Serial/Parallel/G1.

## 10. Container-aware heap sizing và phần trăm RAM

HotSpot JDK 17 phổ biến có nhận biết container resource limits thông qua `UseContainerSupport`, nhưng phải kiểm tra bản patch, cgroup version và runtime thật. Nếu không đặt Xmx rõ, VM có thể dùng memory ergonomics theo lượng RAM mà JVM nhận diện.

Một số options HotSpot:

| Flag | Ý nghĩa |
| --- | --- |
| `-XX:MaxRAMPercentage=...` | Dùng tỷ lệ RAM khả dụng theo logic VM để tính maximum heap nếu không có explicit Xmx |
| `-XX:InitialRAMPercentage=...` | Tính initial heap theo tỷ lệ RAM khi không có explicit Xms |
| `-XX:MinRAMPercentage=...` | **Tên dễ gây hiểu nhầm**: chủ yếu tham gia max-heap ergonomics trên cấu hình RAM nhỏ, không đơn giản là “minimum heap percentage” |
| `-XX:+UseContainerSupport` | Cho phép JVM sử dụng thông tin container constraints khi được hỗ trợ |
| `-XX:ActiveProcessorCount=...` | Override số processors VM coi là active; ảnh hưởng ergonomics GC/JIT khác ngoài heap |

Ví dụ trên environment/container phù hợp:

```bash
java -XX:InitialRAMPercentage=10 -XX:MaxRAMPercentage=60 -jar app.jar
```

Điều này không nói heap chắc chắn bằng chính xác 10%/60% từng byte vì alignment và heuristics. Các con số **10%/60% chỉ minh họa**, không là lựa chọn tối ưu chung cho mọi service.

### Explicit flags thường có ưu tiên

Khi đã dùng `-Xmx512m`, đừng kỳ vọng `-XX:MaxRAMPercentage=60` vẫn tự kéo Xmx lên 60% RAM. Trên HotSpot, **explicit Xmx thường có precedence** so với default ergonomic percentage. Tương tự với Xms so với initial percentage; hãy xem `PrintFlagsFinal` để kiểm chứng giá trị cuối.

### Container request khác container limit

- Kubernetes **memory request** phục vụ lập lịch/đảm bảo tương ứng, không nhất thiết là memory limit JVM được dùng để tính max.
- **Memory limit** được cgroup áp đặt và có thể dẫn tới OOMKilled nếu process/cgroup vượt.
- Nếu container **không có memory limit**, JVM có thể tính theo host/node hoặc policy khác; không được suy nó sẽ chia đều RAM node cho số pods.
- Ghi cả container `requests`, `limits` và JVM effective flags vào báo cáo.

**Thực hành:** đo real memory usage của container và headroom thay vì mặc định “60% heap có nghĩa 40% đủ mọi native overhead”.

## 11. Lệnh kiểm tra JVM thật và effective heap

### Nhận diện runtime

```bash
java -version
java --version
```

Ghi rõ **JDK major/update/build, vendor và VM implementation**. Với OpenJ9 hoặc Native Image, flags/diagnostics có thể khác; bài chủ yếu nói HotSpot.

### In flags trên HotSpot

```bash
java -Xms128m -Xmx512m -XX:+PrintFlagsFinal -version
```

Tìm các names như `InitialHeapSize`, `MaxHeapSize`, `MinHeapSize` và những cờ liên quan tới GC. Output gồm rất nhiều flags, đơn vị thường là bytes. Xem **effective values** thay vì chỉ suy từ câu lệnh.

Lưu ý `MinHeapSize` là một giá trị nội bộ/tuning trong HotSpot, **không được đồng nhất máy móc** với mọi điều kiện co heap của mọi collector/JVM port.

### Kiểm tra container support

```bash
java -XshowSettings:system -version
```

Trên HotSpot JDK 17 hỗ trợ option này, output có thể mô tả provider/container constraints, CPUs và memory. Cách in/nhãn biến động theo build; nếu VM không hỗ trợ, đọc chính xác `java --help-extra` hoặc tài liệu của distribution.

## 12. Java 17+ runnable: quan sát initial, used, committed và max

Tạo file **`HeapSizingDemo.java`**:

```java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

public final class HeapSizingDemo {
    private static final int BLOCK_SIZE = 128 * 1024;

    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("No arguments expected");
        }

        MemoryUsage before = ManagementFactory
                .getMemoryMXBean()
                .getHeapMemoryUsage();

        List<byte[]> retained = new ArrayList<>();
        int checksum = 0;

        for (int i = 0; i < 64; i++) {
            byte[] block = new byte[BLOCK_SIZE];
            block[0] = (byte) i;
            retained.add(block);
            checksum += Byte.toUnsignedInt(block[0]);
        }

        MemoryUsage after = ManagementFactory
                .getMemoryMXBean()
                .getHeapMemoryUsage();

        System.out.println("arrays: " + retained.size());
        System.out.println("payload bytes: "
                + (retained.size() * BLOCK_SIZE));
        System.out.println("checksum: " + checksum);
        System.out.println("first: "
                + Byte.toUnsignedInt(retained.get(0)[0]));
        System.out.println("last: "
                + Byte.toUnsignedInt(retained.get(63)[0]));
        System.out.println("done");

        // Những số này CỐ Ý biến động theo collector/JVM/run.
        System.err.println("heap init (bytes): " + after.getInit());
        System.err.println("heap max (bytes): " + after.getMax());
        System.err.println("heap used before (bytes): "
                + before.getUsed());
        System.err.println("heap committed before (bytes): "
                + before.getCommitted());
        System.err.println("heap used after (bytes): "
                + after.getUsed());
        System.err.println("heap committed after (bytes): "
                + after.getCommitted());
    }
}
```

Biên dịch bằng JDK 17+:

```bash
javac -encoding UTF-8 HeapSizingDemo.java
```

Chạy trên HotSpot với **hai cấu hình riêng**:

```bash
java -Xms32m -Xmx128m HeapSizingDemo
java -Xms128m -Xmx128m HeapSizingDemo
```

**Standard output phần chức năng của cả hai lần chạy bình thường:**

```text
arrays: 64
payload bytes: 8388608
checksum: 2016
first: 0
last: 63
done
```

**Standard error không có giá trị số cố định.** Ví dụ chỉ để hiểu format:

```text
heap init (bytes): [giá trị từ VM]
heap max (bytes): [giá trị từ VM]
heap used before (bytes): [giá trị runtime]
heap committed before (bytes): [giá trị runtime]
heap used after (bytes): [giá trị runtime]
heap committed after (bytes): [giá trị runtime]
```

Đây **không phải literal stderr**: các chuỗi trong ngoặc vuông là diễn giải của các số mà API in thật. Dữ liệu phụ thuộc VM, collector, alignment, background GC và thời điểm thu thập.

### Kiểm tra phép tính được xác định

- Mỗi array có `128 × 1024 = 131072` bytes.
- Có `64` arrays, tổng **8.388.608 bytes** payload.
- Byte đầu của array thứ `i` được gán `i` cho `i=0..63`, không có overflow; tổng `0+1+...+63 = 63×64/2 = 2016`.
- Phần tử đầu là `0`, phần tử cuối là `63`.
- `retained` còn được dùng sau vòng lặp, nên references được giữ tới các phép đọc rõ ràng.

**Không cần `System.gc()`** để quan sát heap sizing. Thuật toán GC và hoạt động nền có thể khiến `used after` không tăng đúng bằng 8.388.608 bytes so với `used before`; còn headers, metadata, các allocations khác và GC events.

### Cần so sánh điều gì?

Với `-Xms32m -Xmx128m`, kỳ vọng *initial heap* nhỏ hơn cấu hình `-Xms128m -Xmx128m`. **Đừng bắt stdout đổi:** cùng code/inputs phải cho cùng kết quả tính toán. Đọc `heap init`, `heap committed` và `heap max`, sau đó nếu muốn so footprint hãy đo **RSS/container memory** từ OS ngoài chương trình.

Vì chương trình nhanh, không nên kết luận “cấu hình B có startup time nhanh hơn” chỉ bằng hai lần dùng stopwatch. Hãy đo nhiều process launches và startup marker có định nghĩa.

## 13. Lab 2: tạo áp lực heap có kiểm soát

Để thấy maximum heap có thể trở thành giới hạn, viết **`HeapPressureDemo.java`**. Mẫu này có giới hạn số blocks, không cần làm ứng dụng bị OOM để chứng minh heap pressure.

```java
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public final class HeapPressureDemo {
    public static void main(String[] args) {
        List<byte[]> blocks = new ArrayList<>();
        long checksum = 0;

        for (int i = 0; i < 32; i++) {
            byte[] data = new byte[256 * 1024];
            data[0] = (byte) (i + 1);
            blocks.add(data);
            checksum += Byte.toUnsignedInt(data[0]);
        }

        System.out.println("blocks: " + blocks.size());
        System.out.println("payload bytes: "
                + (blocks.size() * 256 * 1024));
        System.out.println("checksum: " + checksum);

        long max = ManagementFactory.getMemoryMXBean()
                .getHeapMemoryUsage().getMax();
        System.err.println("heap max bytes (dynamic): " + max);
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 HeapPressureDemo.java
java -Xms32m -Xmx64m HeapPressureDemo
java -Xms64m -Xmx128m HeapPressureDemo
```

**Standard output phần chức năng nếu process hoàn tất bình thường:**

```text
blocks: 32
payload bytes: 8388608
checksum: 528
```

Giải thích: `32 × 256 × 1024 = 8.388.608` bytes; các giá trị byte đầu từ `1` đến `32` có tổng `32×33/2 = 528`. Đây là **retained payload**, không phải tổng bộ nhớ JVM thực sự dùng.

**Không cố ý tạo OOM trên shared machine**. Nếu cần kiểm thử OOME, dùng môi trường cô lập, giới hạn tài nguyên, có giám sát và tách khỏi production traffic. Không tạo vòng lặp không giới hạn chỉ để chờ crash.

### Quan sát GC thay vì đoán

Với HotSpot:

```bash
java -Xms32m -Xmx64m -Xlog:gc*=info HeapPressureDemo
java -Xms64m -Xmx128m -Xlog:gc*=info HeapPressureDemo
```

GC logs có format/duration khác theo collector/JDK/hardware; **không có expected log cố định**. Vì demo ngắn và retention chỉ vài MiB, **có thể không thấy collection event nào**. Điều đó không làm demo sai.

## 14. Đọc GC logs để hiểu heap sizing

Trên HotSpot JDK 17:

```bash
java -Xms128m -Xmx512m -Xlog:gc*=info:file=gc.log:uptime,level,tags -jar app.jar
```

Lưu ý `app.jar` phải là ứng dụng thật và có main manifest. Log có thể chứa những thông tin về heap trước/sau collection, collectors, regions, promotion, pauses, concurrent phases.

### Bảng thông số cần quan sát

| Metric | Nếu tăng/giảm có thể báo gì? | Điều KHÔNG nên kết luận |
| --- | --- | --- |
| **Heap used before GC** | Allocation pressure/lượng dữ liệu trước collection | Không tự là live set |
| **Heap used after GC** | Dữ liệu còn retained và cấu trúc heap sau collection | Một event riêng lẻ chưa là bằng chứng leak |
| **GC pause duration** | Delay STW ở event đó | Không phải toàn bộ request latency |
| **GC frequency** | Số collections per time hoặc per requests | Nhiều young GCs không luôn xấu |
| **Allocation rate** | Bytes allocated per second | Không phải live heap growth |
| **Promotion/old occupancy** | Objects đi qua generations, pressure old region | Không tự là leak |
| **Committed heap trend** | JVM tăng/giảm committed capacity | Không bằng RSS trên mọi collector |
| **Full GC / evacuation failure** | Dấu hiệu memory pressure hoặc collector-specific event | Không đủ để quy cho Xms nhỏ mà bỏ qua live-set |

Cần ghép **timestamp + workload + app latency + CPU/container memory**. Ví dụ việc giảm số GC events bằng cách tăng Xmx chưa đủ nếu latency p99 không tốt hơn hoặc memory footprint vượt giới hạn.

### Không dùng GC pause trung bình thay p99

Vài pauses dài có thể làm tail latency xấu dù mean thấp. Hãy báo **distribution** của request latency và GC pause, có căn cứ time alignment giữa request spans với GC events.

## 15. Quan sát process đang chạy với `jcmd` và JMX

Trên HotSpot có tool attach hỗ trợ:

```bash
jcmd -l
jcmd PID VM.flags
jcmd PID VM.command_line
jcmd PID GC.heap_info
jcmd PID VM.native_memory summary
```

`PID` là placeholder cho process ID thật. `VM.native_memory summary` cần **Native Memory Tracking** được bật từ startup để có dữ liệu có ích:

```bash
java -XX:NativeMemoryTracking=summary -Xms128m -Xmx512m -jar app.jar
```

**Lưu ý NMT:** thống kê phạm vi internal HotSpot/JDK, không phải tất cả memory OS có thể account; không giả định `NMT total = RSS`. Bật NMT có overhead và thường không thể kích hoạt đầy đủ một cách hồi tố nếu JVM chưa bật từ lúc start.

### Vì sao JMX hữu ích?

`MemoryMXBean.getHeapMemoryUsage()` cho phép ứng dụng/monitoring theo dõi `init/used/committed/max` qua thời gian, thay vì suy từ một lệnh launch đã cách đây nhiều giờ. Một heap có thể lớn lên/co xuống tùy collector và config; cần xem **diễn biến** chứ không chỉ snapshot.

### JVM flags có nhiều nguồn

Flags có thể đến từ:

- Command line được wrapper/script truyền.
- `JAVA_TOOL_OPTIONS` hoặc `JDK_JAVA_OPTIONS`.
- Container entrypoint và orchestrator.
- Tooling như build runners/app servers.
- JVM ergonomics khi cờ không đặt tường minh.

Hãy lưu **effective JVM command line/flags** từ process, đừng chỉ nhìn deployment YAML rồi cho rằng VM chắc chắn nhận các cờ y hệt.

## 16. `AlwaysPreTouch`: chuyển một phần chi phí sang lúc khởi động

Trên HotSpot hỗ trợ:

```bash
java -Xms512m -Xmx512m -XX:+AlwaysPreTouch -jar app.jar
```

`AlwaysPreTouch` yêu cầu VM touch heap pages lúc startup sau commit. Điều này có thể:

- Tăng startup duration và mức RSS ban đầu.
- Giảm một phần page faults/touch work trong thời gian phục vụ.
- Giúp phát hiện memory allocation/commit problems ở giai đoạn khởi động.
- Làm container nhỏ hoặc hệ thống đông JVM processes căng RAM sớm hơn.

**Không bảo đảm** luôn giảm tail latency; nếu workload nhỏ và JVM ít đụng heap, pre-touch nhiều pages có thể chỉ tốn công. Nếu bật pre-touch nhưng Xms rất nhỏ, chỉ initial committed heap có thể được chuẩn bị theo config; đừng mặc định toàn Xmx đã được touched.

### Một phép thử có ý nghĩa

So:

```bash
java -Xms512m -Xmx512m -XX:-AlwaysPreTouch -jar app.jar
java -Xms512m -Xmx512m -XX:+AlwaysPreTouch -jar app.jar
```

Chỉ thay **một flag**, đo cả **startup, RSS, CPU page faults, first-request latency và steady-state**. Đưa ra kết luận trong phạm vi OS/collector/kernel đang dùng.

## 17. `-Xms` bằng Xmx có cần thiết cho latency thấp?

Không phải mọi dịch vụ low-latency đều cần fixed heap. Cân nhắc:

- **Workload ổn định, memory đủ:** fixed heap có thể giảm resize overhead và predictability tốt hơn.
- **Workload theo chu kỳ/ngủ nhiều:** expandable/shrinkable heap có thể giảm footprint khi idle, nếu collector hỗ trợ.
- **Serverless/cold-start:** large initial heap + pre-touch có thể làm startup xấu.
- **Container memory nhỏ:** dành heap cố định quá lớn có thể gây OOMKilled dù used Java objects không cao.
- **ZGC:** uncommit có cost commit/uncommit; cần cân nhắc latency với khả năng trả lại pages cho OS.
- **Batch jobs:** thời gian từ launch tới kết quả cuối khác mục tiêu long-running server p99.

**Một chiến lược thường đúng:** bắt đầu bằng default/ergonomic sizing đủ an toàn, đo allocation/live set/GC/latency, rồi tối ưu có đối chứng. Không sao chép một “Xms=Xmx luôn tốt nhất” từ benchmark không cùng workload.

## 18. Cách chọn Xmx dựa trên live set và budget

### Bước 1 — Đo live set

Quan sát **used after representative collections** trong các giai đoạn tải khác nhau, heap histograms/heap dumps khi cần, object retention và allocation rate.

Một snapshot ngay sau startup chưa đại diện traffic peak; sau full/business cycle có thể khác nhiều.

### Bước 2 — Đo allocation bursts

Một service có live set 200 MiB nhưng có thể tạo 300 MiB short-lived buffers trong peak window. Không nên đặt `Xmx=220m` chỉ vì “live objects khoảng 200 MiB”. Collector cần không gian cho allocations và hoạt động thu gom/evacuation.

### Bước 3 — Dành headroom cho GC

Các collectors khác nhau cần space cho copying, old-gen pressure, concurrent allocations và marking/relocation. Không có một công thức duy nhất `Xmx = live × 1.2` dùng cho mọi G1/ZGC/Parallel workloads.

### Bước 4 — Tính native memory

Threads, metaspace, code cache, direct buffers và JNI có thể chiếm đáng kể. Đặt Xmx thấp hơn container limit, xét peak native usage và cgroup overhead.

### Bước 5 — Thử vài cấu hình và so sánh

Ví dụ A/B:

```text
A: -Xms128m -Xmx512m
B: -Xms256m -Xmx512m
C: -Xms512m -Xmx512m
D: -Xms128m -Xmx768m

Từng run giữ JDK, collector, workload, CPU/cgroup limits
tương đương, và báo số liệu thực.
```

Các số là **kịch bản thử**, không phải khuyến nghị universal. So A/B/C để tập trung effect **Xms**, so A/D để tập trung effect **Xmx**. Tránh vừa đổi collector vừa đổi Xms/Xmx rồi suy cái nào tạo speedup.

## 19. Lỗi hay gặp và cách chẩn đoán

### 19.1. `OutOfMemoryError: Java heap space`

Ý nghĩa thường là Java heap không còn đáp ứng allocation sau các biện pháp cần thiết. Hướng kiểm tra:

- Xmx hiệu lực đúng mong đợi chưa?
- Heap live set và retained paths có tăng không?
- Có unbounded cache, queue, collection hoặc large arrays không?
- Có allocation burst không phù hợp limit?
- GC logs có promotion/evacuation pressure hay Full GC không?
- Thay heap có đúng là fix bền vững không?

Không tăng Xmx vô hạn để che một leak. Heap dump có thể chứa secrets, cần quyền/phương án an toàn.

### 19.2. `OutOfMemoryError: GC overhead limit exceeded`

Có thể xuất hiện ở những collector/config phù hợp khi JVM tốn quá nhiều thời gian GC mà thu hồi quá ít heap. Thông báo không phải định nghĩa chung cho mọi GC; cần đọc logs và collector đang dùng. Chỉ tăng Xmx có thể che symptoms nếu retention không được sửa.

### 19.3. Container `OOMKilled` / exit 137

Process có thể bị OS/cgroup terminate mà JVM **không có cơ hội** ném `Java heap space`. Dấu hiệu:

- Pod `Last State: OOMKilled`.
- Container memory peak gần limit.
- JVM heap used chưa chạm Xmx.
- RSS/native memory cao, hoặc cgroup metrics gồm page cache.

**Cần điều tra process/container memory budget**, không chỉ Java heap. Exit code 137 cũng có thể do SIGKILL vì nguyên nhân khác; dùng pod events/OS logs để xác minh OOM, đừng suy từ số 137 đơn lẻ.

### 19.4. `OutOfMemoryError: Metaspace`

Metaspace thuộc non-heap. Xmx lớn lên không tự giải quyết; có thể làm giảm headroom cho metaspace. Cần xem class loading/class loaders, maximum metaspace nếu cấu hình và retention.

### 19.5. `OutOfMemoryError: Direct buffer memory`

Direct buffers thường dùng bộ nhớ ngoài Java heap (native backing), dù đối tượng wrapper vẫn ở heap. Cần xem `MaxDirectMemorySize`/NIO usage và lifecycle. Đổi Xmx có thể ảnh hưởng ergonomic default/direct buffer budget trong một số HotSpot releases nhưng không thay thế phân tích native allocations.

### 19.6. `unable to create native thread`

Có thể hết process/thread resource, address space/stack memory, OS limits hoặc cgroup PID limits. Tăng Xmx có thể càng làm ít memory dành cho native threads. Giảm leak threads/thread pool size hoặc điều chỉnh resources theo bằng chứng.

### 19.7. “Heap committed cao nhưng used thấp”

Không tự kết luận memory leak. Committed capacity có thể giữ lại để phục vụ allocations tương lai hoặc do Xms/fixed heap policy. So **after-GC used trend**, retained objects và actual native/OS footprint.

### 19.8. “Tăng Xmx mà ứng dụng vẫn chậm”

Xmx không chữa:

- Lock contention.
- Database/network I/O.
- CPU throttling.
- Memory leak.
- Over-allocation causing GC CPU even with plenty of heap.
- Slow startup class loading/JIT when heap không phải bottleneck.

Dùng JFR, request tracing, profiler và metrics thích hợp để phân biệt.

## 20. Những hiểu nhầm thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`-Xms` là dung lượng objects đã dùng.” | Xms thiết lập initial heap, không phải current used |
| “`-Xmx` là RAM tối đa của JVM process.” | Xmx là Java heap maximum; còn non-heap/native |
| “Đặt Xmx 2 GiB có nghĩa JVM dùng 2 GiB ngay startup.” | Có thể chỉ reserve/commit một phần tùy settings |
| “`-Xms=Xmx` chắc chắn khiến RSS bằng Xmx ngay.” | Cần xét commit/touch/OS/GC mapping |
| “Heap used, committed, RSS là một.” | Khác metric và accounting scopes |
| “Heap đủ lớn thì không thể OOMKilled.” | Native/cgroup memory vẫn có thể vượt limit |
| “Xmx càng lớn thì pauses luôn càng ngắn.” | GC frequency/pauses/CPU trade-off phụ thuộc collector |
| “Xmx càng nhỏ thì CPU luôn giảm.” | GC pressure có thể tăng |
| “Cùng Xmx nghĩa cùng GC behavior.” | GC, Xms, live set, CPU và VM ergonomics còn khác |
| “Xms nhỏ luôn có startup tốt.” | Allocation/heap expansions có thể gây overhead |
| “Xms lớn luôn tốt cho throughput.” | Footprint/startup/memory contention có thể xấu |
| “`AlwaysPreTouch` luôn giảm latency.” | Có thể chỉ tăng startup and RSS nếu không cần |
| “Volatile/heap size liên quan trực tiếp nhau.” | Đồng bộ bộ nhớ/JMM khác heap capacity |
| “`MaxRAMPercentage` vẫn override explicit Xmx.” | Explicit heap sizing thường có precedence trong HotSpot |
| “`MinRAMPercentage` là initial/minimum heap %.” | Nó tham gia maximum ergonomic sizing cho bộ nhớ thấp |
| “Kubernetes request chính là cgroup memory limit.” | Requests phục vụ scheduling; limits mới là cap thường gặp |
| “`-Xmx` có thể truyền sau `-jar`.” | Sau `-jar` là application arguments |
| “`System.gc()` là cách đáng tin để đo Xms/Xmx.” | GC scheduling không tạo heap sizing đúng và có overhead |
| “OOMKilled 137 chắc chắn là heap OOME.” | Có thể do native/cgroup/signal; cần kiểm chứng events |
| “Used tăng liên tục một lúc chắc chắn memory leak.” | Có thể GC chưa chạy hoặc live workload tăng hợp lệ |
| “Heap dump không cần bảo mật.” | Có thể chứa secrets/PII/session state |
| “Một lần đo startup có thể chọn Xms tối ưu.” | Cần nhiều runs, workload/GC/CPU equivalent |
| “Một tỷ lệ Xmx/container chuẩn cho mọi ứng dụng.” | Native footprint và workload rất khác nhau |

## 21. Bài tập tự kiểm tra có đáp án

**Bài 1.** `-Xms256m -Xmx1g` nghĩa là ứng dụng tạo sẵn 256 MiB business objects?  
**Đáp án:** Không. Xms điều khiển initial heap sizing, còn used objects phụ thuộc execution.

**Bài 2.** `-Xmx1g` có giới hạn tổng RSS tiến trình không?  
**Đáp án:** Không. Metaspace, code cache, thread stacks, direct buffers, native memory nằm ngoài heap.

**Bài 3.** Committed heap lớn hơn used heap có bất thường không?  
**Đáp án:** Không. Committed capacity có thể chứa free space cho allocations tiếp theo.

**Bài 4.** Tại sao không đặt Xmx đúng bằng container limit?  
**Đáp án:** Vì process còn non-heap/native/OS-accounted memory, có nguy cơ cgroup OOM.

**Bài 5.** `-Xms2g -Xmx1g` có hợp lệ không?  
**Đáp án:** Không trên HotSpot; initial không được vượt max heap.

**Bài 6.** `-Xms1g -Xmx1g` có bảo đảm OS RSS 1 GiB ngay tại startup không?  
**Đáp án:** Không. Commit, touch và OS mapping khác nhau; cần đo.

**Bài 7.** `-XX:+AlwaysPreTouch` tác động chủ yếu vào giai đoạn nào?  
**Đáp án:** Chạm heap pages đã commit ở startup, tăng upfront cost và có thể giảm page-fault work khi dùng sau.

**Bài 8.** Nếu tăng Xmx, memory leak có hết không?  
**Đáp án:** Không. Nếu có unbounded retention, leak vẫn tồn tại, thường chỉ chậm OOM hơn.

**Bài 9.** Tăng Xms nhưng giữ Xmx có thể cải thiện điều gì?  
**Đáp án:** Giảm nhu cầu heap expansion/initial pressure trong một số workload, đổi lại footprint/startup cost.

**Bài 10.** Giảm Xmx có thể làm frequency GC tăng không?  
**Đáp án:** Có; mức độ phụ thuộc live set, allocations, collector và heuristics.

**Bài 11.** `MaxRAMPercentage` có áp khi explicit `-Xmx` đã được truyền trên HotSpot không?  
**Đáp án:** Thông thường không quyết định Xmx nữa; explicit max heap option được ưu tiên.

**Bài 12.** `MinRAMPercentage` là phần trăm tối thiểu của heap ban đầu?  
**Đáp án:** Không. Nó liên quan maximum heap ergonomics trên hệ thống RAM nhỏ.

**Bài 13.** `java -jar app.jar -Xmx512m` có thật sự cấu hình heap không?  
**Đáp án:** Thường không; đó là application argument do vị trí sau `-jar`.

**Bài 14.** Trong `HeapSizingDemo`, payload arrays tổng bao nhiêu bytes?  
**Đáp án:** **8.388.608 bytes**, vì 64 × 128 KiB.

**Bài 15.** Checksum của `HeapSizingDemo` là gì?  
**Đáp án:** **2016**, tổng 0..63.

**Bài 16.** Hai cấu hình Xms khác nhau có làm stdout logic demo khác không?  
**Đáp án:** Không khi chạy thành công; timing và runtime heap metrics có thể khác.

**Bài 17.** `HeapPressureDemo` giữ bao nhiêu arrays và checksum?  
**Đáp án:** **32 arrays**, checksum **528**.

**Bài 18.** ZGC trên JDK 17 có thể trả heap pages chưa dùng về OS không?  
**Đáp án:** Có trên build hỗ trợ, theo `ZUncommit`; không uncommit xuống dưới Xms.

**Bài 19.** Với ZGC, Xms=Xmx ảnh hưởng uncommit thế nào?  
**Đáp án:** Vô hiệu hóa khả năng trả unused heap về OS theo cơ chế uncommit của ZGC.

**Bài 20.** `SoftMaxHeapSize` là hard limit?  
**Đáp án:** Không. Soft target có thể được vượt khi cần, còn Xmx là hard maximum heap.

**Bài 21.** Pod bị OOMKilled nhưng heap used chưa tới Xmx. Cần xem gì?  
**Đáp án:** RSS/cgroup memory, native/metaspace/direct/threads, GC/native allocations, pod/OS events.

**Bài 22.** G1 có cam kết giữ mọi pause dưới `MaxGCPauseMillis` không?  
**Đáp án:** Không. Đó là ergonomic target, không phải hard deadline.

**Bài 23.** Một phép đo heap used tăng trong 5 phút chứng minh leak?  
**Đáp án:** Không; cần live-set/retained-path trends qua collections và workloads tương đương.

**Bài 24.** Nếu startup là KPI chính, fixed large Xms + AlwaysPreTouch có thể gây bất lợi gì?  
**Đáp án:** Tăng startup work/memory commitment và RSS sớm, nhất là khi workload nhẹ.

**Bài 25.** Bạn muốn thay Xmx từ 512m thành 768m; giữ Xms hay đổi cả hai?  
**Đáp án:** Để cô lập tác động Xmx, trước hết giữ Xms, collector, workload và CPU/memory budget tương tự; kiểm tra headroom container rồi A/B.

## 22. Checklist tuning heap có trách nhiệm

- [ ] Xác định đúng HotSpot/JVM implementation, vendor, JDK version/build.
- [ ] Biết Xms, Xmx **thực sự có hiệu lực** (không chỉ trong YAML).
- [ ] Phân biệt initial, used, committed, max, virtual reserve, RSS và cgroup usage.
- [ ] Đã kiểm tra container memory **limits** và CPU quotas?
- [ ] Đã tính non-heap: metaspace, code cache, direct buffers, thread stacks, JNI, GC metadata?
- [ ] Có baseline live set ở nhiều workload phases, nhất là peak traffic?
- [ ] Có đo allocation rate và headroom cho bursts/collector?
- [ ] Có biết GC đang dùng (Serial/Parallel/G1/ZGC/...)?
- [ ] Có log GC events/pauses, heap occupancy trước/sau GC?
- [ ] Có đánh giá latency p99, throughput và error rates?
- [ ] Có thử Xms thay đổi **riêng** khỏi Xmx để cô lập tác động?
- [ ] Có thử Xmx thay đổi **riêng** khỏi GC choice?
- [ ] Có thử startup với/không AlwaysPreTouch khi KPI yêu cầu?
- [ ] Nếu fixed heap, đã kiểm tra idle memory/host density chưa?
- [ ] Nếu Xmx lớn gần memory limit, đã test native headroom và peak cgroup memory?
- [ ] Nếu OOMKilled, đã xem Kubernetes events/cgroup/OS logs?
- [ ] Nếu heap OOME, đã xem retained-path evidence và application object lifecycles?
- [ ] Có security/retention policy khi dump heap/thu diagnostic artifacts?
- [ ] Có nhiều A/B runs cùng inputs/JDK/container constraints?
- [ ] Có rollback criteria cho thay đổi sizing khi p99 hoặc memory xấu hơn?

## 23. Tóm tắt và quy tắc quyết định

```text
- Xms = initial heap sizing
- Xmx = maximum Java heap sizing
- used ≠ committed ≠ reserved ≠ RSS ≠ cgroup usage

Đặt Xms thấp:
  có thể nhẹ footprint/startup
  nhưng VM có thể resize/commit thêm khi tải lên

Đặt Xms cao hoặc Xms=Xmx:
  có thể giảm resizing/churn
  nhưng giữ footprint lớn hơn, nhất là khi pre-touch

Đặt Xmx quá thấp:
  nhiều GC / allocation stalls / heap OOME

Đặt Xmx quá cao gần RAM/container cap:
  native headroom thiếu / OOMKilled / cạnh tranh bộ nhớ

Mục tiêu:
  đủ heap cho live data + allocation bursts + GC
  và đủ native/container headroom cho process
```

**Không có kích thước heap tốt nhất trong mọi trường hợp.** Giá trị tốt được xác định bằng workload, GC, startup/latency SLO, native footprint và khả năng quản lý tài nguyên của OS/container. Bắt đầu từ thiết lập có căn cứ, đo các đại lượng đúng tên, thay **một biến mỗi lần** và chỉ giữ thay đổi khi benefit đủ mạnh, không làm rủi ro OOM/latency xấu đi.

### Nguồn tham khảo

1. **Oracle JDK 17 — `java` command, `-Xms` / `-Xmx` / `AlwaysPreTouch` / RAM percentages:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
2. **Oracle JDK 17 — Garbage Collection Tuning Guide:** https://docs.oracle.com/en/java/javase/17/gctuning/
3. **Oracle JDK 17 — Z Garbage Collector, heap sizing và uncommit:** https://docs.oracle.com/en/java/javase/17/gctuning/z-garbage-collector1.html
4. **Java SE 17 — `MemoryUsage` API:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryUsage.html
5. **Java SE 17 — `MemoryMXBean` API:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html
6. **Oracle JDK 17 — `jcmd` diagnostic command:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
7. **Oracle JDK 17 — Native Memory Tracking:** https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
8. **Inside Java — How ZGC allocates memory for Java heap (2025):** https://inside.java/2025/04/17/zgc-deep-dive/
9. **Inside Java — ZGC `SoftMaxHeapSize`:** https://inside.java/2020/07/02/zgc-softmaxheapsize/
10. **Red Hat — Java 17 container awareness and RAM percentage flags:** https://developers.redhat.com/articles/2022/04/19/java-17-whats-new-openjdks-container-awareness
11. **AWS — JVM memory/CPU/container recommendations (2026):** https://aws.amazon.com/blogs/containers/jvm-memory-cpu-and-classpath-best-practices-for-java-containers-on-aws/

**Bài học cốt lõi:** `-Xms` quyết định điểm bắt đầu của heap, `-Xmx` quyết định hard ceiling của Java heap; chúng điều chỉnh cân bằng **resizing/GC/startup/footprint**, nhưng không thay thế việc đo **used vs committed vs RSS** và dành bộ nhớ cho toàn bộ JVM process.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-004">-Xss, số luồng và ngân sách bộ nhớ ngoài heap</a>

<details>
<summary>Xem chi tiết</summary>

# -Xss, số luồng và ngân sách bộ nhớ ngoài heap

## 1. Bài toán: JVM còn cần bao nhiêu bộ nhớ khi heap đã được giới hạn?

Một cấu hình Java server có thể đặt `-Xmx512m` trong container giới hạn 1 GiB, nhưng vẫn bị hệ điều hành giết vì hết bộ nhớ; hoặc gặp `java.lang.OutOfMemoryError: unable to create native thread` dù biểu đồ **heap used** thấp. Nguyên nhân không có gì mâu thuẫn: **Java heap chỉ là một phần của bộ nhớ tiến trình**, còn **mỗi platform/native thread** cần stack và nhiều tài nguyên khác. 

Chủ đề thuộc nhóm **Cấu hình tài nguyên** trong JVM: phải hiểu quan hệ giữa `-Xss`, số platform threads, stack reservations/commit, giới hạn OS/cgroup và headroom dành cho **non-heap/native** memory, thay vì chỉ giảm `-Xmx` hay tăng `-Xss` theo cảm tính.

Hãy tách bốn câu hỏi:

1. **Độ sâu stack:** một luồng cần stack bao nhiêu để chạy các lời gọi Java/native an toàn? Việc này liên quan `StackOverflowError` và cấu trúc call chain.
2. **Số luồng sống đồng thời:** có bao nhiêu **platform/native threads** do ứng dụng và JVM tạo ra, gồm cả GC/JIT/runtime threads?
3. **Bộ nhớ ngoài heap:** metaspace, JIT code cache, direct buffers, GC bookkeeping, thread stacks, JNI/native allocations và mapped memory dùng bao nhiêu?
4. **Giới hạn thực thi:** OS/cgroup cho phép bao nhiêu resident/committed memory, native threads, PIDs/tasks và CPU?

**Không có công thức chính xác chung “native memory = số thread × `-Xss`”.** Đây chỉ là một thành phần **ước lượng về mức stack reservation**; actual committed/resident stack pages và phần native memory khác phải đo trên JVM/OS thật.

## 2. Phân biệt JVM stack, Java heap và OS/native stack

### 2.1. Java heap

Java objects và arrays thông thường nằm trong heap do JVM/GC quản lý. Các flags:

- `-Xms`: kích thước Java heap ban đầu.
- `-Xmx`: kích thước tối đa của Java heap.
- `-Xmx` **không** giới hạn toàn bộ RSS hoặc process committed memory.

Vì thế `-Xmx512m` không đồng nghĩa “Java chỉ được sử dụng 512 MiB RAM”.

### 2.2. JVM stack logic

Mỗi **Java execution thread** có call stack logic chứa frames của các method calls, locals, operand stack ở mức JVM bytecode và thông tin return/exception handling. Khi `a()` gọi `b()` rồi `c()`, stack tăng theo call depth; khi các lời gọi trả về, những frames không còn cần thiết được loại khỏi active call chain.

`StackOverflowError` là lỗi khi JVM không thể thỏa mãn yêu cầu thêm stack theo các giới hạn của runtime. Nó **không** có nghĩa Java heap đã dùng hết `Xmx`.

### 2.3. Native stack của platform thread

Trên HotSpot, một **platform thread** thường tương ứng một OS/native thread. OS cần native stack, thread ID, scheduling state, TLS/allocator/runtime resources. HotSpot có thể reserve vùng địa chỉ ảo cho native stack; các pages thực sự **committed** hoặc **resident** tùy OS và cách thread sử dụng stack.

~~~text
Java process
├── Java Heap          (-Xms, -Xmx)
├── Metaspace/Class metadata
├── JIT code cache
├── GC and JVM internal/native structures
├── Direct/native buffers, JNI, third-party allocations
├── Thread 1 (platform / OS) ── native stack 1
├── Thread 2 (platform / OS) ── native stack 2
├── ...
└── JVM service, GC and compiler native threads
~~~

Stack là tài nguyên của **từng OS thread** trong mô hình platform threads. Một pool có 500 workers bị blocking I/O nhưng vẫn còn sống có thể tiếp tục giữ **native stack reservations** của 500 threads, dù phần lớn không chạy CPU.

### 2.4. Virtual threads là trường hợp khác (Java 21+)

Từ Java 21, **virtual threads** có Java call stacks logic nhưng không buộc mỗi VT giữ một OS/native stack riêng suốt lifetime. Khi blocking operation hỗ trợ **unmount**, HotSpot có thể lưu continuation/stack chunks trên heap và dùng lại **carrier platform thread**.

**Điều này không biến stack memory thành 0**: mỗi VT vẫn có heap objects, stack chunks/locals và các references còn sống; carriers, GC/JIT/native threads vẫn dùng native stacks. Mã Java 17 chạy **platform threads** trong bài lab; `Thread.ofVirtual()` chỉ có API chuẩn từ Java 21.

**Kết luận phạm vi:** Công thức `số luồng × -Xss` là mô hình sơ bộ cho **platform thread stacks**, không được nhân với tổng số virtual threads.

## 3. `-Xss` là gì, đơn vị và default?

Theo Java launcher documentation của Oracle JDK 17, `-Xss` **sets the thread stack size**. Có thể dùng số bytes hoặc hậu tố `k`, `m`, `g`:

~~~bash
java -Xss256k -cp . StackDepthDemo
java -Xss512k -cp . StackDepthDemo
java -Xss1m   -cp . StackDepthDemo
~~~

Các ví dụ dùng Java 17+ trên **HotSpot/OS hỗ trợ**. VM có thể từ chối mức quá nhỏ, làm tròn theo alignment/pages hoặc áp các ràng buộc nền tảng. Không có một minimum portable áp cho mọi JVM/OS/architecture.

**Một số mặc định Oracle JDK 17 được tài liệu ghi nhận:**

| Nền tảng | Ví dụ default tham khảo |
| --- | --- |
| Linux/x64 64-bit | 1.024 KiB |
| macOS 64-bit | 1.024 KiB |
| Oracle Solaris 64-bit | 1.024 KiB |
| Windows | Phụ thuộc virtual memory/platform |

Đây là **giá trị trong tài liệu Java 17 của Oracle**, không phải giá trị bắt buộc cho mọi build ở mọi thời điểm. Kiểm tra đúng JVM thực bằng flags và NMT nếu quyết định capacity.

`-XX:ThreadStackSize=` là flag HotSpot liên quan, nhưng **đơn vị cơ bản của flag là kilobytes**, không giống cách trình bày bytes của `-Xss`. Tránh nhầm `-XX:ThreadStackSize=512` với 512 bytes; cũng tránh dùng cả hai flags chồng nhau khi không có lý do.

### `-Xss` không phải một ngưỡng live memory

Một platform thread cấu hình stack nominal 512 KiB **không nhất thiết làm RSS tăng đúng 512 KiB tại thời điểm tạo**. OS có thể reserve address space, commit/touch pages khi stack grow và có guard/shadow pages, alignment và metadata khác. Native stack limit còn có tác động từ JVM internals và OS mapping policies.

**Phân biệt rõ:**

- **Nominal/configured stack size:** input cho VM để cấu hình thread stack.
- **Reserved:** virtual address range dành trước, có thể lớn hơn số bytes touched.
- **Committed:** memory committed theo OS/JVM tracking.
- **Resident (RSS/Working Set):** pages đang thực sự resident theo OS accounting.
- **Guard/yellow/red/shadow pages:** vùng bảo vệ và an toàn khi stack gần chạm giới hạn, tính toán implementation-specific.

Đừng yêu cầu **NMT `Thread stack reserved`** bằng đúng `N_workers × Xss` vì category còn gồm main thread, GC/JIT/service threads và OS/VM overhead; size thực có thể được làm tròn.

## 4. Tác động lên `StackOverflowError`

Với nhiều lời gọi lồng sâu hoặc recursion, stack frames tích lũy trên thread stack. Giảm `-Xss` làm giảm ngân sách stack per platform thread trong nhiều tình huống, nên **có thể làm `StackOverflowError` xuất hiện ở độ sâu nhỏ hơn**.

Ví dụ:

~~~text
-Xss256k → ít không gian stack mỗi platform thread
-Xss512k → mức ở giữa
-Xss1m   → ngân sách stack lớn hơn

Không có hệ số cố định để biến giá trị này thành số method frames.
~~~

Kích thước một frame phụ thuộc Java bytecode, JVM interpreter vs JIT compiled code, inlining, locals, native transitions, architecture, debug flags và các optimizations. Cùng mã có thể tạo **độ sâu tối đa khác nhau** giữa JDK 17 và JDK mới, hoặc giữa cold và warm JIT state.

### Stack overflow khác out-of-memory do tạo thread

| Hiện tượng | Dấu hiệu | Hướng điều tra |
| --- | --- | --- |
| `StackOverflowError` | Call chain quá sâu/recursive bug; thêm stack frame thất bại | Stack trace, recursion/base condition, per-thread stack config |
| `OutOfMemoryError: Java heap space` | Cấp phát Java object thất bại | Heap live set, `-Xmx`, GC, allocations/retention |
| `OutOfMemoryError: Metaspace` | Class metadata/metaspace không đủ | Classloaders, generated classes, metaspace cap |
| `OutOfMemoryError: unable to create native thread` | JVM/OS không tạo được OS thread mới | Native memory, OS thread/PID quota, `pids.max`, `ulimit`, thread leaks |
| Container `OOMKilled` | Kernel/cgroup giết process; có thể không có Java exception | cgroup memory.current/events, RSS/anonymous/file charges |
| Direct buffer allocation failure | Native/direct buffer memory budget/cap hoặc pressure | Buffer pool/JNI/MaxDirectMemorySize/OS counters |

`unable to create native thread` **không nhất thiết chỉ do hết RAM**: OS có thể chặn vì **per-user process/thread limit, cgroup pids controller, global threads-max, security controls** hoặc resource reservation, ngay cả khi heap còn nhiều.

## 5. Java 17 runnable: giới hạn stack và recursion

Tạo file **`StackDepthDemo.java`**:

~~~java
public final class StackDepthDemo {
    private static volatile int depthReached;

    static void dive(int depth) {
        depthReached = depth;
        dive(depth + 1);
    }

    public static void main(String[] args) {
        try {
            dive(1);
        } catch (StackOverflowError expected) {
            System.out.println("caughtStackOverflow=true");
            System.out.println("depthPositive=" + (depthReached > 0));
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 StackDepthDemo.java
java -Xss256k StackDepthDemo
java -Xss1m StackDepthDemo
~~~

**Stdout đã biên dịch/chạy kiểm chứng trên JDK 24 Windows cho cả hai cấu hình:**

~~~text
caughtStackOverflow=true
depthPositive=true
~~~

Chương trình không in độ sâu cụ thể vì nó phụ thuộc VM build, platform, JIT state và stack frames. Field `volatile` lưu mức sâu đã chạm, nhưng thử nghiệm này **không** phải công cụ đo stack byte usage chính xác.

**Chỉ chạy kiểu lab có kiểm soát**, không bắt `StackOverflowError` rồi tiếp tục phục vụ production request như một phương án xử lý ổn định. Stack còn lại ở thời điểm lỗi có thể rất ít; xử lý/ghi log sâu hoặc tạo recursive error handler có thể lại gặp lỗi.

### Tại sao không khuyên tăng `-Xss` vô hạn?

Nếu method có recursive bug không có base case, tăng `-Xss` chỉ trì hoãn lỗi, tốn address space và có thể làm suy giảm khả năng tạo nhiều platform threads. Tối ưu thường là **sửa recursion** hoặc chuyển sang iteration/explicit work stack khi phù hợp. Tăng stack size nên dựa trên **độ sâu hợp lệ được kiểm thử** và ngân sách native memory thực.

## 6. Số platform threads tác động thế nào đến native memory?

Một mô hình **ước lượng đơn giản**:

\[
S_{\text{reserved, approx}} \approx N_{\text{platform}} \times S_{\text{per-thread nominal}}
\]

Trong đó `N_platform` là tổng platform/native threads tương ứng **không chỉ worker threads của ứng dụng**; `S_per-thread nominal` có thể khác cho JVM runtime/GC/compiler threads, main thread hoặc threads do native library tạo.

Đây **không phải phương trình chính xác cho RSS hoặc NMT Thread committed**. Đối với headroom, có thể lập một upper-bound style budget sử dụng số threads tối đa dự kiến nhân với stack size, cộng thêm các overhead, nhưng phải **đo lại** vì mỗi OS có reservation/commit khác nhau.

### Ví dụ tính tay mang tính minh họa

Một ứng dụng có **400 platform workers**, mỗi worker đặt nominal `-Xss512k`:

- Phần nominal workers stacks: `400 × 512 KiB = 204.800 KiB = 200 MiB` **quy mô vùng stack cấu hình**.
- Nếu thay bằng `-Xss1m`, phần nominal là `400 MiB`.
- Chênh nominal khoảng `200 MiB`, có thể quan trọng cho **virtual address reservations**, OS thread creation và committed memory headroom trong tình huống stack usage cao.
- **Không** tuyên bố 400 workers đang chờ chiếm đúng 200 MiB RSS; không tính được RSS chỉ bằng phép nhân này.

Ngoài 400 workers còn có main thread, ForkJoinPool workers, HTTP/JDBC pool/platform scheduler threads, GC workers, JIT compiler threads, JVM service threads và JNI threads. Số lượng OS threads cần đo từ tiến trình thật.

### Pool sizing trước stack tuning

Trong nhiều backend, thay vì giảm `-Xss` quá thấp để chứa 5.000 workers, cần hỏi:

- Tại sao phải có **5.000 platform threads**? Có thread leak hoặc unbounded pool không?
- Công việc chủ yếu chờ I/O hay chạy CPU?
- Downstream DB chỉ cho 50 connections hay có capacity lớn hơn?
- Có thể dùng bounded executor/queue, backpressure hoặc asynchronous APIs không?
- Nếu ứng dụng nâng lên Java 21+, virtual threads có hữu ích không, và giới hạn heap/DB còn đủ không?

`-Xss` là một phần của bài toán, không phải phép màu làm native threads không tốn memory.

## 7. Java 17 runnable: giữ tám platform threads đồng thời

Tạo file **`ThreadStackBudgetDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class ThreadStackBudgetDemo {
    public static void main(String[] args) throws Exception {
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch release = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < workers; i++) {
            Thread t = new Thread(() -> {
                ready.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }, "budget-worker-" + i);
            t.start();
            threads.add(t);
        }

        ready.await();
        System.out.println("workersReady=" + workers);

        long pauseMs = Long.getLong("demo.pauseMs", 0L);
        if (pauseMs > 0) {
            System.out.println("inspectionWindowMs=" + pauseMs);
            Thread.sleep(pauseMs);
        }

        release.countDown();
        for (Thread t : threads) t.join();
        System.out.println("workersJoined=" + threads.size());
        System.out.println("allTerminated=" +
                threads.stream().noneMatch(Thread::isAlive));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 ThreadStackBudgetDemo.java
java -Xss512k ThreadStackBudgetDemo
~~~

**Stdout chính xác của chạy mặc định không đặt `demo.pauseMs`:**

~~~text
workersReady=8
workersJoined=8
allTerminated=true
~~~

Tám `new Thread` là **platform threads** trên Java 17; tất cả tới `CountDownLatch.await()` rồi chờ main giải phóng. Chương trình không cố tình tạo hàng nghìn threads và không có vòng lặp vô hạn.

`CountDownLatch` làm mốc kiểm chứng rõ ràng: `workersReady=8` bảo đảm cả tám đã chạy tới điểm chờ trước khi main tiếp tục. Nó không chứng minh số OS threads của toàn JVM là tám.

### Tạo cửa sổ để đo NMT/OS cùng một JVM

~~~bash
java -Xss512k -Xms64m -Xmx128m \
  -XX:NativeMemoryTracking=summary \
  -Ddemo.pauseMs=10000 ThreadStackBudgetDemo
~~~

Trên PowerShell:

~~~powershell
java -Xss512k -Xms64m -Xmx128m -XX:NativeMemoryTracking=summary -Ddemo.pauseMs=10000 ThreadStackBudgetDemo
~~~

Trong 10 giây sau `workersReady=8`, ở terminal khác tìm **PID thật** rồi lấy snapshot:

~~~text
jcmd
jcmd PID VM.native_memory summary scale=KB
jcmd PID Thread.print
jcmd PID VM.flags
~~~

`PID` là ký hiệu cần thay bằng process ID thật. `Thread.print` có thể in stacks/metadata của Java/platform threads, phù hợp Java 17; xem chính sách bảo mật khi lưu thread dump. NMT phải **bật từ lúc JVM startup** mới hoạt động.

### Kết quả đo thực tế của một lần chạy

Trên **JDK 24 Windows**, target source Java 17, process bật `-Xss512k -Xms64m -Xmx128m` và tạo tám workers đang chờ, một lần chụp cho:

~~~text
NMT Total: reserved=1607709KB, committed=169577KB
NMT Java Heap: reserved=131072KB, committed=67584KB
NMT Thread: reserved=17997KB, committed=1333KB
NMT stack: reserved=17920KB, committed=1256KB
OS native thread count: 30
OS WorkingSet64: 40738816 bytes
~~~

Số liệu trên đã được thu từ **một tiến trình đang chạy** bằng `jcmd` và Windows `Get-Process`, không phải output stdout của Java. Trong chính tiến trình ấy, nguồn Java in `workersReady=8`, `inspectionWindowMs=5000`, sau đó `workersJoined=8`, `allTerminated=true`.

**Cách đọc:**

1. OS có **30 native threads**, bao gồm tám app workers **và** JVM/JIT/GC/runtime/native threads.
2. NMT `Thread reserved 17997 KiB` không bằng `8 × 512 KiB` vì JVM còn threads khác, rounding và stack/VM structures.
3. Stack `reserved 17920 KiB` lớn hơn rất nhiều stack `committed 1256 KiB`: **không dùng reserved làm resident RAM**.
4. NMT `Total committed 169577 KiB` **bao gồm Java Heap committed**, không cộng thêm 67584 KiB.
5. Windows Working Set 40.738.816 bytes phản ánh process resident pages tại snapshot, **không phải** NMT committed.
6. Số đo là **một run/JDK/OS cụ thể**, không dự đoán stack usage trên Linux hay Java 17 runtime chính xác.

Điểm học tập là **nguồn đo và phạm vi**, không phải tuyên bố “mỗi worker luôn cần 512 KiB committed” hoặc “HotSpot luôn có đúng 30 native threads”.

## 8. Native memory ngoài stack: không được bỏ sót phần nào?

Để cấu hình tài nguyên cho server, cần dành ngân sách cho các vùng khác `Java Heap`:

| Thành phần | Cách chịu ảnh hưởng | Công cụ quan sát |
| --- | --- | --- |
| **Metaspace / class metadata** | Số classes, classloaders, dynamic proxies, class generation | NMT `Class`, JMX memory pools |
| **Compressed class space** | Reserved address space lớn có thể commit nhỏ | NMT `Class` reserved/committed |
| **JIT code cache** | Native compiled code, compiler tiers, warm-up | NMT `Code`, `jcmd Compiler.codecache` nếu hỗ trợ |
| **GC structures** | Collector, heap region sizes, remembered sets, concurrency | NMT `GC`, GC logs |
| **Native thread stacks** | Platform thread count, configured stack sizes, call depth | NMT `Thread` stack reserved/committed, OS thread count |
| **DirectByteBuffer / NIO** | Direct buffer allocations và lifetimes | `BufferPoolMXBean`, OS/native tools |
| **JNI/JNA/native libs** | `malloc`, native caches, 3rd-party clients | OS/native allocation profilers; NMT có thể bỏ sót |
| **Mapped files, shared libs** | File mappings và physical residency | Linux `/proc/PID/smaps_rollup`, PSS; Windows process counters |
| **JVM internal/runtime** | Symbol tables, compiler arenas, service allocations | NMT categories theo JDK |
| **OS/container memory charges** | File cache, kernel memory, sidecars/child processes | cgroup `memory.stat`, `memory.current` |

**NMT không phải toàn bộ native memory của mọi thư viện**. Oracle JDK 17 ghi rõ NMT chủ yếu theo dõi HotSpot VM internal allocations, không bao quát đủ third-party native memory. Vì vậy heap + NMT vẫn không nhất thiết mô tả toàn bộ RSS/cgroup usage.

### Cấu hình giới hạn vài vùng riêng

~~~bash
java -Xms256m -Xmx512m \
  -Xss512k \
  -XX:MaxDirectMemorySize=128m \
  -XX:MaxMetaspaceSize=128m \
  -XX:ReservedCodeCacheSize=64m \
  -jar service.jar
~~~

Các giá trị **chỉ là ví dụ có chủ đích**, không phải “bộ flags tốt nhất”. `service.jar` phải là application artifact thật. Chú ý:

- `MaxDirectMemorySize` giới hạn một phần cơ chế direct-buffer allocation của JVM; **không** giới hạn mọi JNI/native malloc.
- `MaxMetaspaceSize` có thể gây `OutOfMemoryError: Metaspace` nếu quá thấp; không biến mọi memory class loading thành một cap RSS hoàn hảo.
- `ReservedCodeCacheSize` giới hạn code cache capacity theo VM, có thể ảnh hưởng compiled code/JIT nếu thiếu; **không phải toàn bộ compiler native memory**.
- `-Xmx`/`-Xss` không kiểm soát OS page cache hay toàn bộ thread/process resources.
- Các maximums này không bảo đảm từng subsystem **dùng hết** giá trị đó; đồng thời có overhead khác **ngoài các maximums**.

**Phương pháp tốt hơn:** đặt budgets trên giấy, đo NMT/OS thật với cùng workload, rồi điều chỉnh có tính đến headroom.

## 9. Ngân sách bộ nhớ: cách tính sơ bộ để tránh OOM

Giả sử container có **1 GiB = 1.024 MiB memory limit**, chạy một JVM chính. Một bản phân bổ **minh họa cho thí nghiệm**, không phải số đo thực:

| Nhóm ngân sách | Số MiB giả định | Ý nghĩa |
| --- | ---: | --- |
| Java heap maximum | 512 | `-Xmx512m` |
| Direct-buffer allowance | 128 | NIO pool cap ví dụ, không cover all JNI |
| Metaspace allowance | 128 | Cấp cho classes/metadata theo VM |
| Code cache allowance | 64 | Dự phòng compiled code |
| 100 platform stacks nominal × 512 KiB | 50 | **Quy mô reservation**, không phải resident RAM cố định |
| GC/VM/JNI/runtime/OS headroom khác | 80 | Dự phòng tổng hợp có tính phỏng đoán |
| **Tổng budget kiểu bảo thủ** | **962** | Tổng giả định, **không phải RSS đo được** |
| **Phần còn lại dưới 1.024 MiB** | **62** | Headroom theo bảng giả định |

**Diễn giải quan trọng:**

- `100 × 512 KiB = 50 MiB` là **nominal stack-space budget**, không tự là 50 MiB RSS.
- Các allowance **có thể chồng lắp khi lấy số thực từ NMT/JMX**; bảng này là **kế hoạch upper-bound style theo nhóm tài nguyên** đã định nghĩa riêng, không phải phép cộng những counters từ công cụ.
- 62 MiB headroom như ví dụ có thể **quá mỏng** khi app có nhiều JNI libs, sidecars, peak memory hoặc một phần budget bị sai. Phải đo thêm peak, steady, startup spike, OS/cgroup file cache và sai số.
- Nếu bạn đặt `Xmx=900 MiB` trong 1 GiB limit, khả năng OOMKilled rất đáng lo ngay cả khi heap trung bình thấp.
- **Không sử dụng bảng để dự đoán chính xác số thread tối đa**. Hãy thu native thread count, NMT Thread reserved/committed, RSS và container memory theo tải thật.

### Công thức cho headroom mang tính mô hình

Có thể viết một phép lập ngân sách (không phải đẳng thức đo đạc):

\[
B_{\text{available}} = L_{\text{container}} - H_{\text{planned}} - N_{\text{planned}} - O_{\text{headroom}}
\]

Trong đó `H_planned` là allowance heap, `N_planned` là các allowance **ngoài heap không chồng lắp theo định nghĩa của bản kế hoạch**, và `O_headroom` dự phòng hệ điều hành, runtime/third-party allocations và peaks. Nếu các allowance được lấy từ NMT `Total` vốn **đã chứa Java Heap**, phải **không** cộng heap lại.

Để quyết định chạy production, **đối chiếu bảng với peak `memory.current`/`memory.peak`, RSS/PSS và NMT** chứ không chỉ nhìn các giới hạn cấu hình.

## 10. Giới hạn số luồng có thể đến từ đâu?

Số lượng platform threads không được quyết định bởi một `MaxThreads` flag portable duy nhất trong Java 17. Những nguồn giới hạn thường gặp:

1. **OS/process/thread quota**: mỗi OS thread là một scheduling entity cần tài nguyên kernel.
2. **Linux per-user limit** (`ulimit -u` và thực tế RLIMIT_NPROC tùy quyền/user namespaces).
3. **Linux system-wide** thread/pid limits (`/proc/sys/kernel/threads-max`, `pid_max`, và các giới hạn khác).
4. **Cgroup pids controller** (`pids.max`, `pids.current`, `pids.events`), giới hạn tasks/PIDs trong cgroup.
5. **Native address space / memory commit**: nhiều stacks/reservations và thread resources có thể thất bại dù heap còn trống.
6. **CPU/scheduler overhead**: lượng threads runnable quá lớn khiến context-switching/lock contention làm latency kém.
7. **Native library/runtime constraints**, ví dụ JNI clients hoặc internal pool sizes.

Các giới hạn OS thay đổi theo Linux/Windows, container orchestration, architecture và bảo mật. `ulimit -u` **không nhất thiết là limit hữu hiệu duy nhất**; cgroup PID controller cũng có thể chặn trước.

### Cgroup PID khác container memory

Một container có `memory.current` thấp nhưng `pids.current` đã sát `pids.max` có thể **không tạo được native thread mới**, dù vẫn còn RAM. Ngược lại, `pids.max` rất lớn không bảo đảm memory đủ cho mọi thread stack.

Chẩn đoán phân biệt:

~~~bash
cat /sys/fs/cgroup/pids.current
cat /sys/fs/cgroup/pids.max
cat /sys/fs/cgroup/pids.events
cat /sys/fs/cgroup/memory.current
cat /sys/fs/cgroup/memory.max
cat /sys/fs/cgroup/memory.events
~~~

Đường dẫn là ví dụ với **cgroup v2 được mount tại đó**; với cgroup v1 hoặc container mount namespace khác phải xác định cgroup thực tế. `pids.max` có thể là `max` (không có hard limit theo cơ chế ấy). `pids.current` đếm các tasks trong cgroup theo kernel rules, không phải đếm Java application worker threads.

Không nhầm limit container CPU với limit native thread count: **CPU quota thấp** làm nhiều runnable threads tranh CPU, nhưng không trực tiếp định nghĩa số PIDs tối đa.

## 11. Quan sát trên Linux và Windows

### 11.1. Linux: thread count + process memory

~~~bash
ps -o pid,nlwp,rss,vsz,comm -p PID
ps -L -p PID -o pid,lwp,stat,comm
cat /proc/PID/status
cat /proc/PID/smaps_rollup
~~~

Trong các lệnh trên, thay **PID** bằng process ID thật.

- `NLWP`: OS lightweight threads của process ở thời điểm đo; **không chỉ tám workers**.
- `ps -L`: liệt kê native/lightweight threads.
- `VmRSS`/`Rss`: resident footprint theo OS.
- `VmSize`/`VSZ`: process address space mappings/reservations.
- `smaps_rollup`: RSS/PSS/private/shared memory breakdown, tùy kernel/quyền.
- Không lấy RSS trừ heap used rồi gọi phần chênh là “thread stacks”; còn nhiều nguồn native khác.

### 11.2. Windows PowerShell

~~~powershell
Get-Process -Id 12345 |
    Select-Object Id,
        @{Name='NativeThreads';Expression={$_.Threads.Count}},
        WorkingSet64, PrivateMemorySize64, VirtualMemorySize64
~~~

`12345` chỉ là **PID ví dụ**, phải thay số đúng process. Windows `WorkingSet64`, `PrivateMemorySize64` và `VirtualMemorySize64` có nghĩa khác nhau. Không coi `VirtualMemorySize64` là RAM resident và không lấy `PrivateMemorySize64` làm synonym RSS Linux.

### 11.3. JDK/HotSpot tools

~~~text
jcmd
jcmd PID Thread.print
jcmd PID VM.native_memory summary scale=KB
jcmd PID VM.flags
jcmd PID GC.heap_info
~~~

Tất cả lệnh dùng **PID thật** trên cùng OS/container PID namespace và cần quyền attach. Các lệnh `jcmd PID ...` là **shell instructions** sau khi thay PID, không phải cú pháp Java source.

`Thread.print` hiển thị JVM-recognized Java/native thread stacks; dùng để phát hiện threads đang WAITING/BLOCKED/RUNNABLE, pool growth và số tên threads lặp. Tuy nhiên thread dump là một **snapshot trạng thái**, không đo RSS của từng stack.

### 11.4. Java ThreadMXBean

Java 17 có `ManagementFactory.getThreadMXBean()` và `getThreadCount()`, `getPeakThreadCount()`, `getTotalStartedThreadCount()`:

- Cho bạn JVM-level metrics về live threads, peak live threads và cumulative started count.
- Không phải OS `NLWP` hoàn toàn chính xác 1:1 vì JVM còn service/native threads không được bean phơi ra theo cùng phạm vi.
- `getTotalStartedThreadCount()` **không phải** số threads đang sống: nó tích lũy số thread đã start kể từ JVM initialization.
- Trên Java 21+ với virtual threads, các legacy `ThreadMXBean` views không mặc nhiên tính đầy đủ VTs theo số Java tasks; cần virtual-thread-aware tools.

**Đối chiếu OS count với JVM thread counts**, thay vì đòi chúng bằng nhau.

## 12. NMT: reserved/committed và stack line

Native Memory Tracking của HotSpot có thể bật từ startup:

~~~bash
java -XX:NativeMemoryTracking=summary \
  -Xss512k -Xms64m -Xmx128m ThreadStackBudgetDemo
~~~

Với process còn sống, in `jcmd PID VM.native_memory summary scale=KB`. Một phần output thường có cấu trúc:

~~~text
- Thread (reserved=..., committed=...)
    (thread #...)
    (stack: reserved=..., committed=...)
    (malloc=... #...)
    (arena=... #...)
~~~

**Các số là placeholders mô tả format NMT**, không phải output thực của một JVM cụ thể. `Thread` category có thể bao gồm cấu trúc thread data, stacks, arenas và metadata. “Thread stack reserved” và “Thread stack committed” không đồng nghĩa.

### NMT total đã bao gồm Java Heap

Ví dụ NMT:

~~~text
Total: reserved=..., committed=...
- Java Heap (reserved=..., committed=...)
- Thread (reserved=..., committed=...)
- Class (reserved=..., committed=...)
- Code (reserved=..., committed=...)
- GC (reserved=..., committed=...)
~~~

**Không tính** `Total committed + Java Heap committed` là process memory: heap **đã nằm trong NMT total**.

NMT **không** theo dõi mọi memory do JNI/third-party native library cấp phát, và reserved/committed khác OS RSS. Đối chiếu NMT với OS mappings là cần thiết nếu nghi native leak.

### Baseline/diff khi pool threads tăng

Có thể lấy baseline sau khi app ready:

~~~text
jcmd PID VM.native_memory baseline
jcmd PID VM.native_memory summary.diff scale=KB
~~~

Khi tăng pool size dưới workload có kiểm soát, xem liệu category `Thread` tăng và có bao nhiêu native threads/stack reservations. Nhưng nếu còn GC/compiler/class loading thay đổi, một delta NMT không cô lập chỉ thread stacks. Cần **OS thread count, app thread metric, named thread dumps và workload timeline**.

## 13. Thực nghiệm an toàn để đo tác động của `-Xss`

Thay vì cố “tạo thread cho đến khi JVM chết”, dùng lab **một lượng workers nhỏ, cố định**:

1. Chọn 2 cấu hình `-Xss512k` và `-Xss1m` cùng JDK/OS.
2. Giữ `Xms/Xmx`, collector, app binary, số workers (=8 trong ví dụ) và workload giống nhau.
3. Bật NMT summary **cho cả hai runs**, không bật một run/tắt run kia.
4. Mỗi run khởi động JVM process mới và giữ `demo.pauseMs` giống nhau.
5. Chụp NMT `Thread reserved/committed`, OS native thread count, RSS/Working Set và process VSZ.
6. Lặp vài lần; tránh dùng một run duy nhất để kết luận exact bytes.
7. Kết hợp với `StackDepthDemo` cho thấy ảnh hưởng stack limit lên call depth, nhưng không gán một phép đổi tuyến tính sâu=stack size.
8. Chỉ dùng các kết quả đo sau khi đã kiểm tra PID/time/OS/JDK flags.

**Cố tình spawn hàng trăm nghìn platform threads tới khi OS từ chối là thí nghiệm nguy hiểm** trên máy đang chạy dịch vụ; không thực hiện trên shared/dev host. Dùng limits thấp trong isolated test environment và giới hạn workers.

### Vì sao không dùng `-Xss` để giảm heap?

Giảm stack size chỉ thay một phần **native/OS thread stack budget**. Nó không thu nhỏ Java heap object retention, direct-buffer allocations, metaspace, code cache hay memory cgroup của sidecars. Cần xác định **nút thắt thật** trước khi thay flag.

## 14. Nhiều câu trả lời sai khi tuning thread stack

### 14.1. “Nếu container 1 GiB, cứ đặt `-Xmx1g`”

Sai: toàn tiến trình còn non-heap/native memory, stack, GC/code cache, file mappings; có thể OOMKilled. Nên dành **đủ headroom đã đo**, không dùng gần 100% memory limit chỉ cho heap max.

### 14.2. “Nếu heap còn 50%, JVM không thể tạo thread vì heap”

Không đủ: native threads bị giới hạn bởi OS memory/address space/PID quotas, không phải chỉ heap free. Thậm chí giảm `-Xmx` không bảo đảm giải được limit `pids.max`.

### 14.3. “`-Xss512k` nghĩa mỗi platform thread đang dùng đúng 512 KiB RAM”

Sai: đó là cấu hình stack size, không phải giá trị RSS từng thread. Xem NMT reserved/committed và OS resident metrics riêng.

### 14.4. “`-Xss` càng nhỏ càng tốt vì có thể tạo thêm workers”

Sai: một số call chains hợp lệ, recursion, frameworks và native transitions có thể cần stack sâu. Cấu hình quá nhỏ có thể bị VM từ chối hoặc gây `StackOverflowError`.

### 14.5. “`-Xss` càng lớn thì method Java chạy nhanh hơn”

Sai: tăng stack capacity không mặc nhiên cải thiện throughput. Nó có thể tăng memory reservations và làm giảm số native threads khả thi. Đo hiệu năng/stack depth thật.

### 14.6. “`-Xss` = số thread tối đa”

Sai: không có phép tính chính xác nếu không biết OS quotas, native memory committed/resident, process limits, JVM service threads và memory headroom.

### 14.7. “`-XX:MaxDirectMemorySize` cap mọi native allocation”

Sai: chỉ một phần cơ chế direct buffer được hỗ trợ; JNI libraries có thể có native allocations ngoài nó.

### 14.8. “Nếu NMT Thread reserved 20 MiB thì process RSS tăng đúng 20 MiB”

Sai: reservation không phải memory resident. Ngược lại, native allocations khác không được tracking có thể làm RSS tăng mà category Thread không đổi.

### 14.9. “Mọi thread trong JVM đều do application thread pool tạo”

Sai: còn JVM runtime, compiler, GC, management và native library threads. OS thread count thường lớn hơn số workers do app tạo.

### 14.10. “Virtual threads Java 21 dùng `-Xss` native stack riêng mỗi task”

Sai: virtual threads có stack chunks/continuation trên heap và được mounted lên carriers; không dùng native stack per VT suốt lifetime.

### 14.11. “Cgroup memory OOM và pids OOM là một”

Sai: memory controller và pids controller có counters/events/limits khác. Cần xem riêng.

### 14.12. “`ThreadMXBean.getTotalStartedThreadCount()` bằng current live threads”

Sai: cumulative started count khác live count. So nhiều metrics để phát hiện churn/leaks.

## 15. Checklist trước khi chỉnh `-Xss` hay số worker threads

- [ ] Xác định JVM vendor/version/build, OS/architecture, collector và cgroup/container.
- [ ] Ghi effective `-Xss`, `-Xms`, `-Xmx`, metaspace/direct/code cache flags.
- [ ] Xác định platform threads vs virtual threads (nếu Java 21+).
- [ ] Đo OS native thread count, peak thread count, Java thread names/pools.
- [ ] Kiểm tra thread leak, unbounded executor, blocking I/O và backpressure.
- [ ] Kiểm tra thread stack depth hợp lệ và các trường hợp recursion/large call chains.
- [ ] Không giảm stack dưới mức VM/platform chấp nhận.
- [ ] Đo NMT Thread reserved **và** committed, không nhầm với RSS.
- [ ] Đo process RSS/PSS/Working Set cùng timestamp và PID.
- [ ] Đo container memory.current/peak/stat/events và pids.current/max/events.
- [ ] Kiểm tra metaspace, GC, code cache, direct buffers và JNI native memory.
- [ ] Dành headroom cho startup spikes, peak workloads và shared/kernel charges.
- [ ] Ghi errors cụ thể: StackOverflowError, unable to create native thread, heap/metaspace OOME, cgroup OOMKilled.
- [ ] Test dưới CPU quota giống production; nhiều runnable threads vẫn có scheduler cost.
- [ ] Chạy thử nhiều lần, đánh giá p99/p99.9 latency/throughput và error rate.
- [ ] Không stress tới exhaustion trên production/shared host.
- [ ] Sau khi tuning, kiểm tra lại chất lượng dịch vụ và khả năng phục hồi.

## 16. Câu hỏi tự kiểm tra và đáp án

1. `-Xss` cấu hình gì? **Thread stack size**, theo VM/OS có rounding/giới hạn.
2. `-Xmx512m` có giới hạn toàn bộ RSS dưới 512 MiB không? **Không**.
3. 100 workers nominal `-Xss512k` có quy mô stack cấu hình bao nhiêu? **50 MiB**, không phải 50 MiB resident chắc chắn.
4. JVM có tám workers thì OS chỉ có tám threads? **Không**, còn VM/GC/JIT/service threads.
5. NMT Thread reserved = NMT Thread committed? **Không**.
6. NMT Thread reserved 18 MiB có phải 18 MiB RAM vật lý đã resident? **Không**.
7. `-Xss256k` có thể gây StackOverflowError sớm hơn `-Xss1m` không? **Có**, tùy call frames/JIT/VM.
8. StackOverflowError có đồng nghĩa heap OOME? **Không**.
9. `unable to create native thread` chỉ do hết RAM? **Không**, còn OS/cgroup PID/thread limits.
10. `pids.max` có phải `memory.max` không? **Không**, là hai bộ controller/limits khác.
11. `ThreadMXBean.getTotalStartedThreadCount()` có phải current live threads? **Không**.
12. `-XX:MaxDirectMemorySize` giới hạn mọi JNI malloc? **Không**.
13. NMT total có chứa Java Heap? **Có**; không cộng heap thêm lần nữa.
14. NMT có thấy hoàn toàn third-party native allocations? **Không**.
15. Cgroup memory.current có bằng RSS JVM? **Không nhất thiết**, cgroup có cache/kernel/other processes.
16. `-XX:ThreadStackSize=512` có nghĩa 512 bytes? **Không**, HotSpot flag cơ bản dùng KiB.
17. Virtual threads có một native stack riêng cố định mỗi VT? **Không**.
18. Một triệu virtual threads có nghĩa không tốn memory? **Sai**, có objects, stack chunks, locals và payloads.
19. Giảm worker pool có thể giảm native stack budget không? **Có** nếu giảm số platform threads sống.
20. Một thread đang WAITING có tự giải phóng native stack như thread đã exit? **Không** theo mô hình platform threads.
21. NMT summary.diff tăng có tự chứng minh thread leak? **Không**, cần tương quan số threads/pool/workload.
22. Có nên tạo threads tới khi process crash trên máy chung? **Không**.
23. Nên xem cả reserved và committed khi đọc NMT? **Có**.
24. Quyết định `-Xss` nên dựa trên gì? **Độ sâu call stack hợp lệ, giới hạn OS, native memory, thread count và phép đo**.

## 17. Nguồn tài liệu chính thống

- Oracle Java SE 17, **`java` command**, `-Xss`, `-XX:ThreadStackSize`, `-Xms`, `-Xmx`, direct/memory flags: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Oracle JDK 17, **Native Memory Tracking**: https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
- Oracle JDK 17, **Diagnostic Tools / NMT categories**: https://docs.oracle.com/en/java/javase/17/troubleshoot/diagnostic-tools.html
- Oracle JDK 17, **`jcmd`**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- Java SE 17, **`Thread`** constructor and platform-thread lifecycle: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- Java SE 17, **`ThreadMXBean`**: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadMXBean.html
- Java SE 17, **`MemoryMXBean`**: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html
- Java Virtual Machine Specification 17, **JVM Stacks (§2.5.2)**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.2
- Oracle Java SE 21, **Virtual Threads** (khác biệt carrier native stacks): https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
- Linux kernel, **cgroup v2 pids controller**: https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html#pid
- Linux kernel, **cgroup v2 memory controller**: https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html#memory

**Kết luận:** `-Xss` kiểm soát **ngân sách stack của một platform thread**, còn số platform threads quyết định tổng quy mô stack reservations; cả hai phải được xem cùng heap, metaspace, code cache, direct/native allocations và giới hạn OS/container. **Reserved, committed và RSS là ba đại lượng khác nhau**. Khi gặp StackOverflowError, kiểm tra call depth; khi không tạo được native thread, xem cả native memory lẫn PID/thread quotas; khi bị OOMKilled, xem cgroup/RSS và memory ngoài heap. Tối ưu đáng tin dùng **NMT + OS thread counts + JMX/GC + cgroup counters** dưới workload thật, không suy từ mỗi `Xmx` hoặc `N × Xss`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-005">Giới hạn Metaspace, direct memory và code cache theo nhu cầu quan sát</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn Metaspace, direct memory và code cache theo nhu cầu quan sát

## 1. Vì sao cấu hình ba vùng nhớ này không thể dựa vào một con số chung?

Trong **HotSpot JVM**, `-Xmx` chỉ giới hạn **Java heap**, không phải toàn bộ bộ nhớ mà tiến trình Java có thể sử dụng. Ngoài heap còn có class metadata, các buffer cấp phát ngoài heap, JIT-generated code, stacks của threads, GC structures, JNI/native libraries và các nguồn tiêu thụ bộ nhớ khác.

Ba nhóm thường cần quan sát và cân nhắc giới hạn là:

1. **Metaspace:** nơi HotSpot lưu class metadata; bị tác động bởi số classes, class loaders và cơ chế unload.
2. **Direct memory:** trong phạm vi cờ `-XX:MaxDirectMemorySize`, phần cấp phát **NIO direct buffers** của HotSpot; không đồng nghĩa toàn bộ native memory.
3. **Code cache:** vùng lưu generated native code/JIT compiled methods và một số runtime code của HotSpot; có thể bị áp lực khi ứng dụng có nhiều hot methods.

Mỗi vùng có **cách tăng trưởng, chỉ số quan sát, giới hạn và biểu hiện lỗi riêng**. Đặt `MaxMetaspaceSize=128m`, `MaxDirectMemorySize=128m` và `ReservedCodeCacheSize=128m` **không làm “bộ nhớ JVM ngoài heap” tự đúng bằng 384 MiB**, cũng không có nghĩa tổng RSS của Java process được bảo đảm dưới `Xmx + 384 MiB`.

```text
                   Java process address space
    +------------------------------------------------------+
    | Java heap (-Xms, -Xmx)                               |
    |   objects, arrays, direct buffer wrapper objects      |
    +------------------------------------------------------+
    | Metaspace / class metadata                            |
    |   classes, methods, class loader lifecycles           |
    +------------------------------------------------------+
    | Direct NIO buffer backing storage (off-heap/native)   |
    +------------------------------------------------------+
    | Code cache (generated JIT/native runtime code)        |
    +------------------------------------------------------+
    | Java thread stacks, GC structures, VM internal memory |
    | JNI / third-party native allocations, mapped files... |
    +------------------------------------------------------+

             Tổng process memory != -Xmx
             Ba giới hạn không thay thế native RAM budget.
```

### Mục tiêu của bài

- Hiểu **Metaspace**, **direct buffers** và **code cache** lưu gì, không lưu gì.
- Phân biệt **reserved / committed / used / resident**.
- Dùng đúng các cờ HotSpot `MaxMetaspaceSize`, `MaxDirectMemorySize`, `ReservedCodeCacheSize` và tùy chọn liên quan.
- Biết cách quan sát qua `jcmd`, **Native Memory Tracking (NMT)**, **MXBeans**, logs và OS/container metrics.
- Phân tích riêng **Metaspace OOME**, **direct buffer OOME**, **code cache full/JIT disabled**.
- Chọn giới hạn **từ số liệu có tải đại diện**, chừa headroom cho sự biến động và tránh bóp nghẹt VM.
- Thực hành một chương trình **Java 17+** biên dịch được và có stdout ổn định cho phần logic.

**Phạm vi:** bài này ưu tiên **HotSpot JDK 17+**, không khẳng định tên/cờ/pool của HotSpot là API bắt buộc của mọi JVM implementation.

## 2. Bản đồ ngắn: các vùng, cờ giới hạn và tín hiệu chẩn đoán

| Vùng / nguồn | Bản chất | Giới hạn HotSpot | Chỉ số hữu ích | Triệu chứng nổi bật |
| --- | --- | --- | --- | --- |
| **Java heap** | Object/array runtime | `-Xmx` | Heap used/committed, GC, live-set | `OutOfMemoryError: Java heap space` |
| **Metaspace** | Class metadata | `-XX:MaxMetaspaceSize` | Metaspace used/committed, class count, loader count | `OutOfMemoryError: Metaspace` |
| **Compressed Class Space** | Phần metadata liên quan compressed class pointers | `-XX:CompressedClassSpaceSize` | Class-space used/committed/reserved | `OutOfMemoryError: Compressed class space` |
| **NIO direct buffers** | Backing memory thường nằm ngoài heap | `-XX:MaxDirectMemorySize` | BufferPoolMXBean direct count/capacity/memoryUsed | `OutOfMemoryError` khi không reserve được buffer memory |
| **Code cache** | JIT code và runtime code blobs | `-XX:ReservedCodeCacheSize` | `Compiler.codecache`, CodeHeap/CodeCache MXBeans, codecache logs | Warning code cache full, compilation có thể bị ảnh hưởng |
| **Threads** | Native/JVM stacks và structures | `-Xss` và OS limits, số threads | thread count, NMT Thread, OS | `unable to create native thread` / process resource failures |
| **Native libraries / JNI** | C/C++/OS allocations | Không bị ba cờ trên giới hạn chung | OS RSS/maps, native profilers | Native OOM, process killed/crash |
| **Mapped files** | Memory mappings như `MappedByteBuffer` | Không phải cùng budget với plain direct allocations | mapped buffer pool, OS maps | Mapping/OS resource failures |

**Đừng dùng một “max memory” cho tất cả.** Từng giới hạn bảo vệ một loại áp lực nhất định và có thể kích hoạt cơ chế thất bại khác nhau.

## 3. Reserved, committed, used và RSS không phải cùng số

Khi xem NMT, MXBeans, logs và OS tools, thường gặp:

- **Reserved:** phần **virtual address space** được VM giữ/đặt trước để có thể sử dụng. **Không đồng nghĩa** chừng ấy bytes đang nằm trong RAM vật lý.
- **Committed:** phần memory mà VM đã yêu cầu/cấp phát backing khả dụng theo mô hình quản lý của VM/OS. Không thể chuyển thành RSS bằng một phép bằng đơn giản.
- **Used:** phần đã cấp cho metadata/code/buffer theo định nghĩa của pool. Có thể nhỏ hơn committed vì chunks/free space/fragmentation.
- **Resident Set Size (RSS):** lượng physical pages đang resident và được tính vào process theo công cụ OS; có thể bao gồm shared mapped pages, không thể tự suy từ VM used.
- **PSS (Proportional Set Size):** trên hệ hỗ trợ, chia tỷ lệ shared pages giữa các processes, hữu ích khi tổng memory footprint quan trọng.
- **Virtual size/address space:** không phải lượng RAM thực dùng.

Ví dụ một output dạng:

```text
Class: reserved=112MB, committed=1MB
Metaspace: used=900KB
Code: reserved=64MB, committed=3MB
```

Đây là **mẫu minh họa định dạng/đơn vị**, không phải trạng thái mặc định của mọi máy. **Không được cộng 112 MB reserved Class + 64 MB reserved Code rồi kết luận có 176 MB physical memory đã tiêu thụ.**

### 3.1. Vì sao Metaspace có reserved lớn nhưng committed nhỏ?

HotSpot có thể reserve virtual address ranges để cấp phát metadata về sau, trong khi chỉ commit phần nhỏ đang cần. JEP 387 (*Elastic Metaspace*) cải tiến cách trả lại memory đã commit mà không còn cần thiết, tùy loader lifecycle và fragmentation.

### 3.2. Vì sao CodeCache pool max không giống used?

Code cache thường reserve capacity cho future compiled code; số byte đã dùng phụ thuộc compilation workload. Đặt một maximum không đồng nghĩa JVM commit toàn bộ maximum ngay khi start.

### 3.3. Không dùng ba giới hạn để tính container memory tuyệt đối

Một container có thể bị OOMKilled vì tổng RSS/cgroup charge tăng do heap, native memory, page cache, thread stacks, heap/metaspace committed và workloads đồng thời. Cgroup accounting và host policies có thể khác; cần quan sát **container memory metrics thực tế**, không chỉ các số max VM.

## 4. Metaspace là gì?

**Metaspace** là hệ thống native-memory management mà HotSpot dùng để lưu **metadata của classes** từ JDK 8 trở đi, thay thế Permanent Generation (PermGen) từng tồn tại trong HotSpot cũ.

Nội dung có thể gồm metadata về:

- Runtime classes/interfaces.
- Methods/fields/signatures.
- Runtime constant pools và các cấu trúc class liên quan.
- Class loader–scoped metadata allocations.

**Không** nhầm với heap objects của ứng dụng: một `new Customer()` thông thường nằm ở Java heap về mặt runtime, còn metadata mô tả class `Customer` được HotSpot quản lý qua các cấu trúc metadata.

### 4.1. Metaspace thuộc native memory, không bị `-Xmx` trực tiếp giới hạn

Ví dụ:

```bash
java -Xmx256m -XX:MaxMetaspaceSize=192m MyApplication
```

`MyApplication` là placeholder class ứng dụng. `-Xmx256m` không khiến Metaspace tự phải nằm trong 256 MiB heap đó; giới hạn 192 MiB là một cấu hình **riêng** cho class metadata.

### 4.2. Vì sao Metaspace tăng?

Một số nguồn:

- Nạp thêm application classes.
- Frameworks/proxies/bytecode generation tạo thêm classes.
- Nhiều class loader instances giữ những bộ class metadata riêng.
- Hot redeploy/plugin architectures tạo class loaders mới mà loaders cũ chưa collectible.
- Class initialization và reflection có thể kéo theo nhiều classes được nạp.

**Số classes đang sống và loaders còn reachable** có ý nghĩa lớn. Metaspace growth không mặc nhiên là memory leak: ứng dụng có thể vẫn đang warm paths hoặc preload các modules đúng thiết kế.

### 4.3. Class unloading và Metaspace reclamation

HotSpot có thể reclaim metadata khi classes/loaders tương ứng đủ điều kiện unload, dựa trên GC và runtime reachability. Nếu application vô tình giữ reference tới class loader cũ qua static registries, threads, caches hoặc ThreadContextClassLoader, nó có thể **ngăn class unloading**, khiến Metaspace tăng lâu dài.

Không giải quyết class-loader leak chỉ bằng tăng `MaxMetaspaceSize`; cần tìm nguyên nhân giữ loaders.

## 5. `-XX:MaxMetaspaceSize`: giới hạn đúng phạm vi

Cờ:

```bash
-XX:MaxMetaspaceSize=192m
```

giới hạn lượng memory class metadata mà HotSpot có thể sử dụng/commit theo accounting của Metaspace. Nếu chạm giới hạn mà không thể thu hồi/reclaim đủ, JVM có thể báo:

```text
java.lang.OutOfMemoryError: Metaspace
```

Oracle JDK 17 mô tả rằng mặc định **MaxMetaspaceSize không có upper bound do cờ này đặt**, nhưng vẫn bị giới hạn bởi address space/OS/container resources.

### 5.1. `MaxMetaspaceSize` không phải dung lượng đã commit khi start

`MaxMetaspaceSize=192m` là **cap**, không là lệnh JVM cấp phát/commit sẵn 192 MiB native memory ở startup.

### 5.2. `-XX:MetaspaceSize` khác `MaxMetaspaceSize`

`MetaspaceSize` là một **initial threshold/GC trigger liên quan metadata usage**, không phải lượng RAM initial commit mà người dùng có thể cộng thẳng vào RSS, cũng không phải một maximum.

Ví dụ:

```bash
java -XX:MetaspaceSize=32m -XX:MaxMetaspaceSize=192m MyApplication
```

Không diễn giải “Metaspace luôn dùng 32 MiB, không thể thấp hơn hoặc cao hơn trước GC”. Threshold được HotSpot điều chỉnh theo policy.

### 5.3. Đặt cap quá thấp gây hại gì?

- Class loading/verification/metadata allocation có thể thất bại.
- GC/class unloading bị gọi dồn dập nhằm reclaim Metaspace.
- Frameworks/proxies/plugins không thể nạp classes cần thiết.
- Startup hoặc một feature hiếm dùng có thể vỡ muộn trong production.

Một cấu hình chỉ đủ lúc **service vừa lên** không chắc đủ khi traffic kích hoạt thêm classes sau vài giờ.

### 5.4. Có nên đặt cap rất lớn để tránh OOME?

Giới hạn quá lớn có thể làm vấn đề class-loader leak khó nhận diện sớm và tạo rủi ro vượt memory limit của container. Cap cần dựa vào **peak class metadata dùng thực tế**, kịch bản deploy/plugin/lazy loading và headroom.

## 6. Compressed Class Space khác phần non-class Metaspace

Với compressed class pointers trên HotSpot 64-bit, class metadata có thể được chia thành nhóm:

- **Class space:** phần cho class pointer-related metadata.
- **Non-class Metaspace:** metadata khác.

Cờ:

```bash
-XX:CompressedClassSpaceSize=128m
```

điều chỉnh giới hạn vùng **compressed class space** khi tính năng tương ứng hoạt động. Nếu hết phần này, có thể gặp:

```text
java.lang.OutOfMemoryError: Compressed class space
```

**Không nhầm CompressedClassSpaceSize với MaxMetaspaceSize**. Hai giá trị ảnh hưởng các cơ chế giới hạn khác nhau; khi giới hạn cả hai, cần hiểu mối quan hệ account tổng metadata và các vùng con theo JDK.

### 6.1. Reserved class space có thể lớn

Ở một số HotSpot builds, compressed class space mặc định reserve virtual memory khá lớn (ví dụ 1 GiB); **virtual reservation không phải 1 GiB RAM vật lý đã commit**.

### 6.2. Đặt class space lớn hơn không tự cứu non-class Metaspace

Nếu non-class metadata mới là nguồn áp lực, tăng chỉ `CompressedClassSpaceSize` không giải quyết. Phải xem NMT Class, `VM.metaspace` và MemoryPoolMXBean tương ứng.

### 6.3. Quan hệ giới hạn trên JDK cụ thể cần kiểm tra

HotSpot có thể chuẩn hóa/điều chỉnh các flags kết hợp, ví dụ khi `MaxMetaspaceSize` nhỏ hơn compressed class space default. Hãy xem **`java -XX:+PrintFlagsFinal -version`**, không giả định đúng y nguyên các số bạn không kiểm tra.

## 7. Direct memory: chính xác là gì?

**Direct `ByteBuffer`** là một NIO buffer có backing storage được JVM cấp phát ngoài Java heap thông thường trên HotSpot. Một **Java wrapper object** vẫn tồn tại ở Java heap, nhưng phần data bytes của direct buffer thường nằm trong off-heap/native memory.

Ví dụ:

```java
java.nio.ByteBuffer b =
        java.nio.ByteBuffer.allocateDirect(1024 * 1024);
b.putInt(0, 42);
```

Đây là snippet có thể đặt trong method; nó cấp một direct buffer dung lượng 1 MiB, sau đó ghi integer tại offset 0.

### 7.1. Direct buffer khác heap buffer

```java
java.nio.ByteBuffer heapBuffer =
        java.nio.ByteBuffer.allocate(1024 * 1024);
java.nio.ByteBuffer directBuffer =
        java.nio.ByteBuffer.allocateDirect(1024 * 1024);
```

- `allocate` thường dùng mảng Java heap làm backing.
- `allocateDirect` dùng native/off-heap backing storage trong HotSpot.
- Cả hai vẫn có Java-level object metadata/wrapper.
- Direct buffer có thể giảm copy overhead trong một số native I/O paths, nhưng allocate/free có thể đắt hơn; không phải mọi workload nhanh hơn.

### 7.2. Direct buffer memory không phải toàn bộ native memory

Native memory của một process còn gồm:

- JNI libraries và native malloc.
- VM internal arenas.
- Thread stacks.
- Code cache.
- Metaspace.
- Mapped files, filesystem mappings.
- Native memory do thư viện ngoài Java quản lý.

**`MaxDirectMemorySize` không đặt global cap cho những nguồn trên.**

### 7.3. MappedByteBuffer không thể gộp vô điều kiện với direct buffers

`FileChannel.map` tạo mapped byte buffers, liên quan memory-mapped files và OS VM mappings. MXBeans thường có pool **`mapped`** riêng. Không suy rằng mọi mmap bytes đều thuộc cùng accounting với `MaxDirectMemorySize`.

## 8. `-XX:MaxDirectMemorySize`: giới hạn NIO direct buffers

Cờ HotSpot:

```bash
-XX:MaxDirectMemorySize=64m
```

đặt maximum cho **direct-buffer allocations thuộc accounting của java.nio/HotSpot**. Theo tài liệu `java` JDK 17, nếu không đặt rõ, VM chọn kích thước phù hợp; trường `MaxDirectMemorySize=0` khi in flags thường có thể biểu thị **ergonomic/default handling**, không phải “Java được phép cấp đúng 0 bytes direct memory”.

### 8.1. Ví dụ cấu hình an toàn cho chương trình demo

```bash
java -Xmx128m -XX:MaxDirectMemorySize=16m MemoryBudgetDemo
```

Demo cấp một direct buffer 1 MiB, dưới limit 16 MiB trong môi trường bình thường.

### 8.2. Khi chạm giới hạn

Một chương trình giữ nhiều direct buffers lớn có thể nhận `OutOfMemoryError` với thông báo liên quan không thể reserve/thêm **direct buffer memory**, ví dụ dạng:

```text
java.lang.OutOfMemoryError: Cannot reserve ... bytes of direct buffer memory
```

**Thông báo chính xác** phụ thuộc JDK; không nhất thiết là chính xác “Direct buffer memory” trên mọi release. Các lỗi OS/native allocator cũng có thể xảy ra mà không liên quan trực tiếp đến cap này.

### 8.3. Tại sao tăng `-Xmx` đôi khi không giải quyết direct OOME?

`-Xmx` giới hạn Java heap, còn direct buffer backing storage sử dụng memory bên ngoài heap. Một heap còn free vẫn có thể gặp direct allocation failure khi `MaxDirectMemorySize` hoặc OS/container resource cạn.

### 8.4. GC và lifetime của direct buffer

HotSpot direct buffers được quản lý thông qua Java object reachability và cơ chế cleanup phù hợp (như Cleaner). **Việc bỏ reference không đảm bảo native memory giải phóng ngay tại dòng code tiếp theo**.

Cần phân biệt:

- Buffer vẫn được ứng dụng/caches giữ reference: native memory **còn được sử dụng hợp lệ hoặc bị leak reference**.
- Buffer không còn reachable nhưng cleanup/GC chưa xảy ra: allocation pressure có thể tăng tạm thời.
- Native allocations từ JNI/Unsafe libraries: có lifecycle và công cụ chẩn đoán riêng, không mặc nhiên dùng direct-buffer cleaner.

### 8.5. Pool capacity khác memoryUsed

`BufferPoolMXBean.getTotalCapacity()` báo **ước tính tổng capacity** của buffers trong pool. `getMemoryUsed()` báo **ước tính bytes memory VM đang dùng** và có thể khác capacity vì allocation/alignment/overhead, hoặc trả -1 nếu không có estimate.

Đừng sử dụng hai số này như một guaranteed exact native-address accounting.

## 9. Code cache: nơi JVM lưu compiled code

**HotSpot code cache** là vùng memory runtime chứa compiled code **nmethods** được JIT tạo và một số non-method runtime code/stubs/adapters.

Khi code mới đủ hot:

```text
Java bytecode
     |
 interpreter / runtime profiling
     |
 JIT compiler C1/C2
     |
 machine code + runtime metadata
     |
 CODE CACHE
     |
 JVM chạy optimized native code nếu phù hợp
```

Code cache **khác Java heap**, **khác Metaspace** và **khác direct buffer backing storage**.

### 9.1. Vì sao code cache tăng?

- Nhiều methods/loops hot được JIT compile.
- Framework code và thư viện được tải theo nhu cầu.
- Inlining/optimization biến method code size khác đi.
- Deoptimization và recompilation có thể tạo version compiled mới.
- Method adapters/runtime stubs cũng sử dụng code-cache space.

JIT có thể reclaim/mark nmethods và sweeper có thể làm việc, nhưng việc reclaim phụ thuộc runtime state; không phải lúc nào old code được trả ngay.

### 9.2. Code cache đầy là vấn đề gì?

Khi code cache không còn đủ chỗ cho compilation code phù hợp, JVM có thể ghi warnings như:

```text
CodeCache is full. Compiler has been disabled.
```

Đó là **một mẫu thông báo HotSpot**, không phải một fatal Java exception bắt buộc. Khi không thể compile thêm, Java application có thể vẫn tiếp tục chạy, nhưng latency/throughput có thể giảm hoặc compilation bị dừng/khôi phục theo implementation/version.

**Không suy mọi code cache full đều ném `OutOfMemoryError: Java heap space`.** Đây là vùng khác và có cơ chế thất bại khác.

### 9.3. Code cache không phải CDS archive

CDS giúp reuse metadata lớp giữa các JVM processes. Code cache chứa generated code/runtime blobs riêng cho process, không tự được chia sẻ qua default AppCDS `.jsa`.

## 10. `-XX:ReservedCodeCacheSize` và segmented code cache

Cờ:

```bash
-XX:ReservedCodeCacheSize=128m
```

đặt maximum **reserved code cache size** mà JVM dùng cho compiled/runtime code cache theo HotSpot. Tài liệu Oracle JDK 17 ghi mức mặc định thường **240 MiB** khi tiered compilation; không mặc định rằng mọi release/config luôn có cùng con số. Khi tắt tiered compilation, default có thể nhỏ hơn.

### 10.1. Từ “reserved” không có nghĩa toàn bộ committed

Một JVM có thể reserve 128 MiB virtual code cache nhưng mới commit/vận hành vài MiB. `Compiler.codecache` và NMT Code giúp phân biệt.

### 10.2. Segmented Code Cache (HotSpot)

Khi enabled, code cache có thể chia các vùng:

- **Non-nmethod heap:** runtime stubs/adapters và code không gắn trực tiếp với compiled Java method.
- **Profiled nmethods heap:** C1 compiled code ở những profiling tiers.
- **Non-profiled nmethods heap:** C2/compiled code ít profiling.
  
Với một số cấu hình khác, JVM có thể không chia ba segments mà báo một pool **`CodeCache`**. Vì vậy khi đọc MXBeans:

```text
CodeHeap 'non-nmethods'
CodeHeap 'profiled nmethods'
CodeHeap 'non-profiled nmethods'
```

hoặc:

```text
CodeCache
```

đều có thể là output hợp lệ tùy HotSpot options/build. **Không hardcode test rằng phải luôn có đúng ba CodeHeap pools.**

### 10.3. `SegmentedCodeCache` và ngưỡng

Tài liệu HotSpot nêu segmentation được bật theo tiered compilation và code cache size policy (JDK 17 thường từ một ngưỡng nhất định, ví dụ 240 MiB). Khi giảm `ReservedCodeCacheSize` tới 64 MiB, output có thể đổi thành một **`CodeCache`** không segmented.

### 10.4. Đặt cache quá nhỏ

Một limit quá thấp có thể:

- Làm code cache đầy trong startup/steady workload.
- Giới hạn JIT compilations và nmethod replacements.
- Tăng rủi ro latency/throughput regressions.
- Làm runtime phải quản lý code cache quá gắt.

**Không khuyên hạ code cache để tiết kiệm virtual reserved pages mà chưa có OS RSS/PSS và code-cache usage evidence.**

## 11. Một cấu hình “tối thiểu” không phải công thức cộng số

Cú pháp đầy đủ ví dụ:

```bash
java -Xms64m -Xmx128m \
     -XX:MaxMetaspaceSize=128m \
     -XX:MaxDirectMemorySize=16m \
     -XX:ReservedCodeCacheSize=64m \
     MemoryBudgetDemo
```

Dấu `\` nối dòng dành cho Bash. Trong PowerShell dùng một dòng:

```powershell
java -Xms64m -Xmx128m -XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=16m -XX:ReservedCodeCacheSize=64m MemoryBudgetDemo
```

**Đây là cấu hình cho chương trình demo nhỏ, KHÔNG phải preset cho production services.** Một service dùng Netty/WebFlux có thể cần direct buffers nhiều hơn; framework/plugin system có thể có Metaspace lớn hơn; workload cần JIT có thể cần code cache lớn hơn.

### 11.1. Vì sao không cộng ra “JVM sẽ dùng tối đa 336 MiB”?

- Những cờ là caps/reservations cho các nhóm khác nhau, không phải committed usage thực.
- Java thread stacks, GC, JNI, native allocators, runtime libraries và mapped files chưa được tính.
- Native memory hoặc Linux RSS có thể vượt các thành phần đã cộng.
- Ngay một số flags còn giới hạn theo accounting nội bộ, không phải physical maximum cùng cách.
- Code cache **reserved** là virtual space, khác committed/resident.

Một budget đúng cần cộng **peak committed/native/heap/threads có quan sát thực tế** với headroom, sau đó so với container/OS limits.

## 12. Thứ tự hợp lý khi quyết định giới hạn

Đừng bắt đầu bằng “Metaspace 128m, direct 128m, code 128m vì dễ nhớ”. Thay vào đó:

1. Xác định **SLO**: latency, throughput, pod/container limit, startup deadline.
2. Ghi JDK/HotSpot version và **current JVM flags**.
3. Ghi **baseline** NMT, MemoryPoolMXBeans/BufferPoolMXBeans, code cache.
4. Tạo **representative load**, gồm peak concurrency và những code paths hiếm nhưng quan trọng.
5. Theo dõi **used/committed/reserved trends**, class count, direct pool capacity/count, compile status.
6. Phân biệt startup/warm-up và steady-state.
7. Xác định peak + biến động + kịch bản deploy/redeploy.
8. Chọn **cap có headroom**, không thấp hơn peak measured usage.
9. Kiểm tra lại trên container/VM resources thật.
10. Giảm/tăng từng flag **một biến mỗi lần**, thu rollback criteria.

### 12.1. Headroom không có tỷ lệ phổ quát

Một ứng dụng có rất ít dynamic classes có Metaspace ổn định; một service hot-reload/plugins có thể tăng đột biến. Direct buffers của một gateway chịu traffic burst khác batch CPU-bound. Code cache nhu cầu thay đổi theo số classes/hot methods/JIT policy.

Vì vậy không đưa lời hứa kiểu “+20% là luôn đủ” vào runbook. Headroom phải dựa vào **percentiles, spikes, deployment patterns, GC response và incidents**.

## 13. Native Memory Tracking (NMT): thấy memory của HotSpot theo subsystem

**NMT** là công cụ HotSpot theo dõi một phần **native memory allocations mà VM quản lý**, hỗ trợ chia nguồn sử dụng theo categories như Class, Code, Thread, GC, Compiler, Internal...

Bật tại lúc khởi động:

```bash
java -XX:NativeMemoryTracking=summary MemoryBudgetDemo
```

Có hai mức thường dùng:

- `-XX:NativeMemoryTracking=summary`: thống kê theo JVM subsystems.
- `-XX:NativeMemoryTracking=detail`: thêm thông tin các call sites/virtual memory regions; tốn overhead hơn.

Theo Oracle, **NMT mặc định tắt** và thường cần bật ngay khi VM start; không giả định có thể yêu cầu NMT full history từ một process trước đó không bật.

### 13.1. NMT không theo dõi mọi malloc bên ngoài VM

Điểm quan trọng từ tài liệu Oracle: NMT tập trung vào **HotSpot/JVM-managed native memory**, **không phải một native profiler toàn diện cho code JNI hoặc third-party native libraries**.

Do đó tổng NMT **không bắt buộc bằng RSS hoặc toàn bộ native bytes**; nếu điều tra native memory leak ngoài HotSpot, có thể cần OS maps, allocators/profilers hoặc thư viện chuyên dụng.

### 13.2. NMT summary không phải “một dòng direct memory”

Direct-buffer backing allocations có thể xuất hiện dưới một hoặc vài NMT categories tùy HotSpot version và allocator path; NMT **không bảo đảm có riêng một dòng tên `Direct buffer memory`** đúng bằng direct pool capacity.

Khi nghi direct buffer pressure, dùng **BufferPoolMXBean** trước, NMT/OS metrics để bổ sung ngữ cảnh.

### 13.3. Tác động overhead

Oracle ghi NMT có thể làm giảm hiệu năng và tăng memory overhead (các tài liệu troubleshooting nêu khoảng 5–10% cho một số workload). Cần bật có chủ đích trên môi trường đo/diagnostics, không mặc định giữ `detail` cho production mãi mãi.

## 14. `jcmd VM.native_memory`: summary và so sánh delta

Đầu tiên xác định JVM process:

```bash
jcmd -l
```

Ví dụ với PID đã biết:

```bash
jcmd <pid> VM.native_memory summary scale=MB
```

`<pid>` là placeholder, phải thay bằng PID thực tế; **không copy dấu `<`/`>` vào shell như một số shell sẽ coi là cú pháp redirect**.

Nếu JVM được start với NMT summary/detail, có thể lấy baseline:

```bash
jcmd <pid> VM.native_memory baseline
```

sau khi chạy workload/đợi trạng thái cần quan sát:

```bash
jcmd <pid> VM.native_memory summary.diff scale=MB
```

### 14.1. Baseline có ích ở đâu?

Bạn có thể so:

- Sau startup/ready.
- Sau load bài kiểm thử.
- Sau mở/đóng plugin.
- Sau redeploy.
- Sau idle và GC/class unloading, khi phù hợp.

Một số dạng output:

```text
Total: reserved=..., committed=...
- Java Heap (reserved=..., committed=...)
- Class (reserved=..., committed=...)
- Code (reserved=..., committed=...)
- Thread (reserved=..., committed=...)
```

Đây là **cấu trúc minh họa**. Đơn vị, dấu cộng/trừ và các categories phụ thuộc VM/report.

### 14.2. Không suy mỗi delta tăng là leak

Class metadata tăng khi lazy loads là bình thường; code cache tăng trong warm-up cũng bình thường. Một leak đáng nghi khi usage tăng liên tục **dưới workload/class-loader lifecycle dự kiến phải ổn định**, đặc biệt sau khi đã unload/release phù hợp.

### 14.3. Nếu `VM.native_memory` báo disabled

NMT cần bật từ JVM startup bằng `-XX:NativeMemoryTracking=summary` hoặc `detail`. Không làm lại truy vấn 10 lần trên cùng process rồi mong nó xuất hiện retroactively.

## 15. `jcmd VM.metaspace`: quan sát class metadata chuyên biệt

Theo tài liệu HotSpot/JDK phù hợp:

```bash
jcmd <pid> VM.metaspace basic
```

Báo cáo có thể cho thông tin:

- Class/non-class metadata usage.
- Reserved/committed.
- Virtual space, chunks, fragmentation.
- Class loader usage và class counts ở mức tùy option.

Tùy JDK/build, có thể có additional diagnostics:

```bash
jcmd <pid> VM.metaspace show-loaders
```

Kiểm tra capabilities bằng:

```bash
jcmd <pid> help
jcmd <pid> help VM.metaspace
```

### 15.1. Vì sao xem class loaders?

Nếu Metaspace tăng sau mỗi hot redeploy, có thể một số class loaders cũ vẫn reachable. Dùng breakdown theo loader để xem số classes và memory của các loaders có giảm như kỳ vọng khi unload hay không.

### 15.2. Đừng chỉ thấy `Metaspace used` mà bỏ class space

Với compressed class pointers, JVM có non-class và class metadata spaces. Những caps có thể bị chạm theo cách khác nhau; cần xem cả Metaspace và **Compressed Class Space**.

### 15.3. Không phải mọi `jcmd` command có trên mọi VM

HotSpot/JDK release hoặc quyền attach/container policy có thể hạn chế commands. Không suy một lệnh thất bại đồng nghĩa Metaspace không tồn tại.

## 16. `jcmd Compiler.codecache`: cách biết code cache còn chỗ không

Lệnh:

```bash
jcmd <pid> Compiler.codecache
```

trên HotSpot thường cho các số:

```text
CodeCache: size=65536Kb used=2701Kb max_used=2701Kb free=62834Kb
...
Compilation: enabled, stopped_count=0, restarted_count=0
```

Đây là **một mẫu output quan sát từ môi trường kiểm tra**, không phải số phải giống trên mọi JVM. Khi segmented code cache, output có thể hiện từng CodeHeap riêng biệt.

### 16.1. Đọc những số nào?

- **size:** capacity của code cache/segment.
- **used/free:** hiện trạng theo code cache accounting.
- **max_used:** peak usage theo run.
- **Compilation status:** compiler còn đang enabled hoặc đã stop/restart theo VM.

### 16.2. Phân tích sâu hơn

Một số HotSpot builds hỗ trợ:

```bash
jcmd <pid> Compiler.CodeHeap_Analytics
```

Output có thể chi tiết về code heaps/free space/method counts. Đây là diagnostic operation; tùy version/permissions và workload có overhead.

### 16.3. Nếu Code Cache liên tục đầy

Đừng chỉ tăng `ReservedCodeCacheSize` ngay. Hãy kiểm tra:

- Những hot methods/JIT levels và code cache occupancy.
- Khả năng class loading/code generation bùng nổ.
- Compiler stop/restart warnings.
- Code cache fragmentation/method sweeping.
- Heap/native memory pressure và container limits.
- Startup/steady-state difference.

Có thể cần tăng limit **nếu usage thật đang tiệm cận cap**, nhưng phải giữ đủ memory headroom.

## 17. `-Xlog`: class, metaspace và code cache events

HotSpot Unified Logging cung cấp những tags phù hợp để quan sát, tùy version:

```bash
java -Xlog:gc+metaspace=info MemoryBudgetDemo
```

Có thể ghi chi tiết Metaspace theo mức hỗ trợ:

```bash
java -Xlog:metaspace=info MemoryBudgetDemo
```

Code cache events theo tags mà runtime hỗ trợ:

```bash
java -Xlog:codecache=info MemoryBudgetDemo
```

**Quan trọng:** từng tag/tag combination và level phải được kiểm tra:

```bash
java -Xlog:help
```

Nếu tag trên không được VM hỗ trợ ở release đang dùng, đừng khẳng định mọi JVM bắt buộc in đúng câu mẫu. Một số thông tin CodeCache còn được đưa qua compiler logs hoặc `jcmd`.

### 17.1. Logging là công cụ giải thích, không baseline timing sạch

Verbose logs có CPU/I/O overhead và có thể thay đổi scheduling. Hãy thu performance metrics ở runs riêng với diagnostics khi cần benchmark nghiêm túc.

### 17.2. OOME log không phải lúc nào cho đủ nguyên nhân

`OutOfMemoryError` có thể báo tên vùng, nhưng để biết vì sao vùng tăng cần kiểm tra class-loader retention, buffer ownership, code cache activity và cả OS native allocations.

## 18. MemoryPoolMXBean: đo Metaspace và Code Cache từ Java

JDK cung cấp **`java.lang.management.MemoryPoolMXBean`**, có thể truy cập bằng:

```java
ManagementFactory.getMemoryPoolMXBeans()
```

Một số pool names trên HotSpot:

```text
Metaspace
Compressed Class Space
CodeHeap 'non-nmethods'
CodeHeap 'profiled nmethods'
CodeHeap 'non-profiled nmethods'
CodeCache
```

Không phải tất cả cùng xuất hiện. Với segmentation, thường có CodeHeap pools; với một cấu hình code cache nhỏ, có thể có một pool `CodeCache`.

### 18.1. `MemoryUsage` có những gì?

`MemoryPoolMXBean.getUsage()` có thể trả:

- `getUsed()`: bytes used theo pool.
- `getCommitted()`: bytes committed theo pool.
- `getMax()`: maximum theo pool, **-1** nếu undefined.
- `getInit()`: initial allocation estimate nếu có.

**Không lấy `getMax() == -1` nghĩa pool “vô hạn RAM vật lý”**. Nó chỉ có nghĩa management interface không định nghĩa maximum cụ thể.

### 18.2. MXBeans là ảnh chụp gần đúng

Các values có thể thay đổi ngay khi chương trình/JVM tiếp tục chạy. Chúng hữu ích để tracking trends, không là atomic snapshot mọi native allocations trên tất cả pools.

## 19. BufferPoolMXBean: đo NIO direct memory từ Java

JDK có interface:

```java
java.lang.management.BufferPoolMXBean
```

Lấy các pool:

```java
ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
```

Một số HotSpot pool names:

- `direct`.
- `mapped`.
- `mapped - 'non-volatile memory'` trên platform/configuration hỗ trợ.

Với từng pool:

- `getCount()` — estimate number of buffers.
- `getTotalCapacity()` — estimate aggregate buffer capacity bytes.
- `getMemoryUsed()` — estimate memory bytes, **có thể -1** hoặc khác capacity do alignment/allocator overhead.

**Chọn pool bằng tên và kiểm tra tồn tại**; không hardcode “mỗi JVM có đúng một direct pool và không có mapped pool”.

### 19.1. Direct pool tăng không chắc là leak

Một server có thể giữ pooled direct buffers hợp lệ để tránh frequent allocations. Cần biết pool ownership, idle reclamation policy và traffic concurrency. Kiểm tra plateau sau warm-up/peak load thay vì đòi usage phải về 0 sau mỗi request.

### 19.2. Off-heap library có thể không hiện trong direct pool

Native allocations từ bên ngoài Buffer APIs có thể không được quản lý qua cùng pool. Nếu process RSS tăng mà BufferPoolMXBean ổn định, xem thêm NMT, JNI/native libraries, thread stacks và OS maps.

## 20. Ví dụ Java 17+ độc lập để nhìn cả ba vùng

Tạo tệp **`MemoryBudgetDemo.java`**:

```java
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.nio.ByteBuffer;
import java.util.Locale;

public final class MemoryBudgetDemo {
    private static volatile long sink;

    static int transform(int x) {
        return (x * 31) ^ (x >>> 3);
    }

    public static void main(String[] args) throws Exception {
        ByteBuffer direct = ByteBuffer.allocateDirect(1024 * 1024);
        direct.putInt(0, 424242);
        System.out.println("directValue=" + direct.getInt(0));

        long sum = 0;
        for (int i = 0; i < 100_000; i++) {
            sum += transform(i);
        }
        sink = sum;
        System.out.println("checksum=" + sum);
        System.out.println("sinkMatches=" + (sink == sum));

        boolean metaspaceFound = false;
        boolean codeCacheFound = false;
        for (MemoryPoolMXBean pool :
                ManagementFactory.getMemoryPoolMXBeans()) {
            String name = pool.getName();
            if (name.equals("Metaspace")) metaspaceFound = true;
            if (name.startsWith("CodeHeap")
                    || name.startsWith("CodeCache")) {
                codeCacheFound = true;
            }

            if (name.equals("Metaspace")
                    || name.equals("Compressed Class Space")
                    || name.startsWith("CodeHeap")
                    || name.startsWith("CodeCache")) {
                MemoryUsage usage = pool.getUsage();
                if (usage != null) {
                    System.out.printf(Locale.ROOT,
                            "%s: usedKiB=%d committedKiB=%d maxKiB=%d%n",
                            name,
                            usage.getUsed() / 1024,
                            usage.getCommitted() / 1024,
                            usage.getMax() < 0
                                    ? -1 : usage.getMax() / 1024);
                }
            }
        }

        boolean directPoolFound = false;
        for (BufferPoolMXBean pool :
                ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if (pool.getName().equals("direct")) {
                directPoolFound = true;
                System.out.println("directPoolCount=" + pool.getCount());
                System.out.println("directPoolCapacityBytes="
                        + pool.getTotalCapacity());
                System.out.println("directPoolUsedBytes="
                        + pool.getMemoryUsed());
            }
        }

        System.out.println("metaspacePoolFound=" + metaspaceFound);
        System.out.println("codeCachePoolFound=" + codeCacheFound);
        System.out.println("directPoolFound=" + directPoolFound);

        if (args.length != 0 && args[0].equals("hold")) {
            System.out.println("pid=" + ProcessHandle.current().pid());
            System.out.println("waitingForDiagnostics=true");
            Thread.sleep(15_000);
        }
    }
}
```

### 20.1. Biên dịch

```bash
javac --release 17 MemoryBudgetDemo.java
```

### 20.2. Chạy mặc định

```bash
java MemoryBudgetDemo
```

**Ba dòng đầu có giá trị cố định khi chương trình chạy đúng:**

```text
directValue=424242
checksum=154999801808
sinkMatches=true
```

Phần tiếp theo là **metadata của pools**, có thể gồm:

```text
Metaspace: usedKiB=... committedKiB=... maxKiB=...
Compressed Class Space: usedKiB=... committedKiB=... maxKiB=...
CodeHeap 'non-nmethods': usedKiB=... committedKiB=... maxKiB=...
...
directPoolCount=...
directPoolCapacityBytes=...
directPoolUsedBytes=...
metaspacePoolFound=true
codeCachePoolFound=true
directPoolFound=true
```

Dấu `...` ở block này là **placeholder minh họa metric không cố định**, **không phải stdout chính xác**. Các pool-presence booleans đã được kiểm chứng bằng HotSpot local; **JVM khác HotSpot có thể dùng names khác**.

### 20.3. Chạy với ba giới hạn

```bash
java -Xmx128m -XX:MaxMetaspaceSize=128m \
     -XX:MaxDirectMemorySize=16m \
     -XX:ReservedCodeCacheSize=64m \
     MemoryBudgetDemo
```

Trên Windows PowerShell:

```powershell
java -Xmx128m -XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=16m -XX:ReservedCodeCacheSize=64m MemoryBudgetDemo
```

**Ba dòng checksum cố định không thay đổi.** Pool `CodeHeap` có thể chuyển sang `CodeCache` do segmentation thay đổi theo config; ví dụ đã tính tới cả hai.

### 20.4. Vì sao có một direct allocation mà memory pool chỉ “ước tính”?

Demo cấp 1 MiB bằng `ByteBuffer.allocateDirect` và giữ local reference tới buffer trong `main`. Buffer pool thường ghi nhận ít nhất allocation này. Nhưng GC/VM/library allocations khác có thể làm `getCount()` và `getMemoryUsed()` thay đổi theo thời điểm.

**Đừng so exact directPoolCount với hằng số 1 trên mọi JVM/runtime.**

### 20.5. Giá trị `checksum` tính bằng gì?

`transform(i)` tính `(i*31) ^ (i>>>3)` theo 32-bit Java integer arithmetic. Kết quả được cộng vào `long` qua 100.000 iterations, cho giá trị xác định **154999801808** ở input demo.

`volatile sink` tạo một observable side effect giúp tránh một số compiler optimizations bỏ hẳn tính toán; không dùng demo này làm microbenchmark performance thay cho JMH.

## 21. Chẩn đoán bằng `jcmd` trên cùng process

Chương trình hỗ trợ đối số `hold` để giữ JVM chạy trong một khoảng ngắn cho người học attach diagnostics:

```bash
java -XX:NativeMemoryTracking=summary -Xmx128m \
     -XX:MaxMetaspaceSize=128m \
     -XX:MaxDirectMemorySize=16m \
     -XX:ReservedCodeCacheSize=64m \
     MemoryBudgetDemo hold
```

Trong một terminal khác, khi process còn chạy, lấy PID in bởi demo hoặc:

```bash
jcmd -l
```

Sau đó:

```bash
jcmd <pid> VM.native_memory summary scale=MB
jcmd <pid> VM.metaspace basic
jcmd <pid> Compiler.codecache
```

Với `<pid>` là PID thực tế.

### 21.1. Đây là một cửa sổ thử nghiệm, không phải server production

`Thread.sleep(15_000)` chỉ giữ demo process khoảng 15 giây sau khi in các metrics. Lệnh `jcmd` phải được chạy khi process còn sống. Nếu lỡ hết cửa sổ, khởi chạy demo mới; **không giữ process vô hạn** chỉ để quan sát.

### 21.2. Ví dụ đã được kiểm chứng thực tế

Trên **OpenJDK HotSpot 24.0.2** (mã tương thích **Java 17** và biên dịch bằng `javac --release 17`), thử nghiệm cho thấy:

- `MemoryBudgetDemo` chạy thành công với flags trên, checksum đúng.
- `MemoryPoolMXBean` hiển thị Metaspace, Compressed Class Space và code cache.
- Khi code cache giới hạn 64 MiB, VM hiển thị pool **`CodeCache`**, không nhất thiết có ba CodeHeap pools.
- `BufferPoolMXBean` có pool `direct`, report 1 MiB direct buffer trong lúc đo.
- `jcmd VM.native_memory summary` cung cấp Class/Code/Heap/Thread categories.
- `jcmd VM.metaspace basic` hiển thị metadata usage.
- `jcmd Compiler.codecache` báo trạng thái compilation và code cache occupancy.

Đây là một **kiểm thử local**; số dùng/commit/reserve khác trên môi trường thật. Không nhầm “Java 17-compatible” với lời khẳng định thử đã chạy trên đúng binary JDK 17.

## 22. Tình huống A — Metaspace cứ tăng sau mỗi redeploy

**Triệu chứng:** sau mỗi lần deploy/hot reload, loaded class count/Metaspace không về mức dự kiến; dần gặp `OutOfMemoryError: Metaspace`.

Cách suy luận:

1. Ghi số classes và Metaspace used/committed trước deploy.
2. Deploy/reload, chờ/quan sát class unloading theo GC.
3. Dùng `jcmd VM.metaspace` và class-loading/unloading logs.
4. Kiểm tra xem class loaders cũ còn reachable không.
5. Tìm references qua static registries, thread local values, executors, caches, TCCL, lingering threads.
6. Sửa ownership/cleanup; chỉ tăng cap nếu workload thật sự cần và được đo.

### 22.1. Không coi việc GC một lần không giảm ngay là kết luận leak

Class unloading phụ thuộc GC cycle, loader reachability và VM policies. Có thể có delayed reclamation hoặc fragmentation. Dấu hiệu leak mạnh hơn khi **sau nhiều chu kỳ lifecycle tương tự**, số loaders/classes giữ lại tăng mà không có lý do nghiệp vụ.

### 22.2. `-Xmx` lớn có chữa Metaspace leak không?

Không trực tiếp. Còn native memory budget có thể bị áp lực hơn. Nguyên nhân là metadata/lifecycle, không chỉ object heap cap.

## 23. Tình huống B — Direct buffers tăng theo kết nối mạng

**Triệu chứng:** service NIO/Netty/network stack nhận traffic; direct pool `count/totalCapacity/memoryUsed` tăng, sau đó `OutOfMemoryError` trong reserve direct buffer memory hoặc container OOM.

Checklist:

- Đo **active connections × buffers per connection × average capacity**, cộng pipeline caches/pooling.
- Đọc `BufferPoolMXBean` direct pool và application buffer pool diagnostics.
- Kiểm tra pooling: buffer trả về pool đúng lúc không?
- Kiểm tra requests/errors/cancellation paths có giữ reference không.
- Kiểm tra burst/retry/queue backlog và container memory limit.
- Đừng chỉ tăng `MaxDirectMemorySize` nếu buffer references không được release.

### 23.1. Leak thật và pooling hợp lệ khác nhau

Pool giữ vài trăm MiB để tái sử dụng có thể là một thiết kế có chủ đích, không tự là leak. Nhưng nếu direct pool không plateau dưới tải ổn định, cần điều tra.

### 23.2. Native allocations ngoài DirectByteBuffer

Nếu BufferPoolMXBean direct gần như ổn định mà RSS tăng, mở rộng chẩn đoán sang JNI/native allocators, mapped pages, native arenas, stacks, GC và OS metrics.

## 24. Tình huống C — Code cache đầy khi số hot methods tăng

**Triệu chứng:** HotSpot báo code cache full/compiler disabled, application throughput giảm sau warm-up hoặc code cache usage sát limit.

Cách kiểm tra:

1. `jcmd <pid> Compiler.codecache`.
2. Kiểm tra `Compilation: enabled/stopped_count/restarted_count` nếu có.
3. Quan sát `-Xlog:codecache`/compiler events ở mức hỗ trợ.
4. Xem JVM code generation/proxy/lambda/framework classes tạo ra nhiều methods không.
5. So nhu cầu code cache với `ReservedCodeCacheSize`, segmented configuration và container budget.
6. Thử tăng limit từng bước nếu code cache là bottleneck được chứng minh.

### 24.1. Không suy code cache full là Java heap OOME

Vùng code cache có exhaustion behavior riêng. JVM vẫn có thể tiếp tục ở interpreter/compiled code còn sẵn; hiệu năng mới là dấu hiệu dễ thấy, không bắt buộc crash ngay.

### 24.2. Code cache dùng nhiều trong warm-up là thường gặp

Khi lượng hot code được biên dịch trong thời gian đầu, used code cache tăng. Nếu nó plateau xa cap và compilation vẫn enabled, không có lý do tự động tăng reserve chỉ vì giá trị cao hơn hôm qua.

## 25. Cấu hình theo tài nguyên container: budget thay vì các cap độc lập

Giả sử một container bị giới hạn ở một memory budget đã biết. Cách chuẩn không phải:

```text
container_limit = Xmx + MaxMetaspaceSize
                  + MaxDirectMemorySize + ReservedCodeCacheSize
```

đẳng thức này **sai** vì trộn **virtual reserve**, **committed** và **potential allocations**, đồng thời bỏ sót nhiều nguồn memory.

Một budgeting worksheet có thể là:

| Thành phần | Cần khảo sát | Có phụ thuộc workload? |
| --- | --- | --- |
| Heap | Peak live set, GC overhead, Xmx | Rất nhiều |
| Metaspace | Class count, loaders, peaks, MaxMetaspace | Có |
| Direct buffers | Peak outstanding bytes, pooling, bursts | Có |
| Code cache | Max used, compiler enabled, hot code footprint | Có |
| Threads | Number × stack usage/native footprint | Có |
| GC/compiler/native/JNI | NMT + OS metrics + library diagnostics | Có |
| Mapped/shared pages | RSS/PSS/cgroup accounting | Có |
| Headroom | traffic spikes, deploy change, instrumentation | Có |

### 25.1. Không đặt mọi cap sát giá trị used ở idle

Metaspace có thể tăng sau lần chạy chức năng hiếm; direct memory tăng khi concurrent requests cao; code cache có thể tăng khi JIT profile đủ. Chỉ đo idle và đặt limit sát idle usage sẽ dẫn tới failures muộn.

### 25.2. Bản thân việc giới hạn có thể ảnh hưởng behavior

Nếu hạ `MaxMetaspaceSize`, GC/class unloading policy có thể hoạt động nhiều hơn. Nếu hạ code cache, JIT compilation có thể chậm hoặc dừng. Nếu hạ direct memory, buffers có thể gây OOME/GC pressure. Các thay đổi cap phải được **load test**, không chỉ kiểm tra `java -version`.

## 26. Những hiểu lầm thường gặp

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “-Xmx là maximum memory của cả Java process.” | Nó giới hạn Java heap, không bao toàn bộ native memory. |
| “Metaspace nằm trong Java heap.” | HotSpot quản lý class metadata trong native memory. |
| “MaxMetaspaceSize=128m thì start đã commit 128m.” | Đây là cap, không phải allocation upfront. |
| “MetaspaceSize và MaxMetaspaceSize đồng nghĩa.” | Một cái liên quan threshold, một cái là maximum. |
| “Metaspace growth luôn chứng minh leak.” | Lazy class loading/initialization cũng làm tăng usage. |
| “Compressed Class Space chỉ là tên khác của toàn Metaspace.” | Có scope class metadata riêng với cap tương ứng. |
| “CompressedClassSpaceSize reserved 1 GiB = 1 GiB RAM đang dùng.” | Virtual reservation khác committed/RSS. |
| “Direct ByteBuffer wrapper là toàn bộ storage nằm trong heap.” | Backing data của direct buffer thường là off-heap. |
| “MaxDirectMemorySize chặn tất cả JNI malloc.” | Cờ này chủ yếu quản lý NIO direct-buffer accounting. |
| “Direct buffers không cần GC/reachability để reclaim.” | Cleaner/lifetime phụ thuộc reachability/VM mechanism. |
| “Capacity bằng memoryUsed trong mọi trường hợp.” | MXBean values là estimates, có alignment/allocator overhead. |
| “MappedByteBuffer luôn cùng pool/limit với direct buffers.” | Mapped buffers có scope/accounting khác. |
| “Code cache và Metaspace là một vùng.” | Metadata và generated code là hai vùng khác nhau. |
| “ReservedCodeCacheSize=64m nghĩa RSS tăng ngay 64m.” | Reservation không đồng nghĩa committed/resident. |
| “CodeCache phải luôn gồm đúng ba CodeHeap MXBeans.” | Segmentation phụ thuộc JVM build/config. |
| “Code cache full luôn ném Java heap OutOfMemoryError.” | Thường liên quan compilation disabled/warnings và performance. |
| “Một compiled method level 4 chứng minh code cache đầy.” | Compilation level và memory occupancy là hai metrics khác nhau. |
| “NMT tổng luôn bằng process RSS.” | NMT không đo mọi native allocation/OS memory. |
| “NMT summary bắt buộc có dòng Direct buffer memory.” | Direct buffers có thể xuất hiện qua VM categories không có tên đó. |
| “NMT mặc định luôn bật.” | Off theo mặc định ở HotSpot JDK17. |
| “Một lần tăng code cache used là leak.” | JIT warm-up có thể là tăng trưởng bình thường. |
| “Cộng Xmx + ba max là memory limit chính xác.” | Không; còn thread stacks, native/GC/JNI, committed/reserved khác nhau. |
| “Giới hạn càng nhỏ luôn giảm latency.” | Có thể kích hoạt GC/compilation stops/OOME. |
| “Một demo có poolFound=true chứng minh mọi JVM có tên pool đó.” | Đây là HotSpot-specific naming/behavior. |
| “JDK version không ảnh hưởng mặc định flags.” | VM ergonomics/options đổi theo build và architecture. |

## 27. Câu hỏi tự kiểm tra có đáp án

**Câu 1.** `-Xmx` có giới hạn Metaspace không?

**Đáp án:** Không. Heap và class metadata Metaspace có cơ chế giới hạn khác nhau trên HotSpot.

**Câu 2.** Cờ đặt cap cho class metadata là gì?

**Đáp án:** `-XX:MaxMetaspaceSize=...`.

**Câu 3.** `MetaspaceSize` có phải maximum không?

**Đáp án:** Không. Nó liên quan threshold/GC trigger, khác `MaxMetaspaceSize`.

**Câu 4.** Khi nào xuất hiện `OutOfMemoryError: Compressed class space`?

**Đáp án:** Khi vùng compressed class space hết capacity theo cấu hình và VM không thể đáp ứng allocation phù hợp.

**Câu 5.** Reserved 1 GiB có nghĩa RSS tăng 1 GiB không?

**Đáp án:** Không. Reserved virtual address space khác committed/resident physical memory.

**Câu 6.** Direct ByteBuffer nằm hoàn toàn trong Java heap không?

**Đáp án:** Không. Wrapper là Java object, backing storage direct buffer trên HotSpot thường là off-heap.

**Câu 7.** `MaxDirectMemorySize` có giới hạn mọi thư viện native/JNI malloc không?

**Đáp án:** Không. Nó nhắm vào NIO direct-buffer memory accounting.

**Câu 8.** Vì sao direct pool count/capacity không giảm ngay sau `buffer = null`?

**Đáp án:** Cleanup phụ thuộc reachability, GC/Cleaner và VM pooling; không có immediate-free guarantee.

**Câu 9.** Đọc direct pool bằng API nào?

**Đáp án:** `BufferPoolMXBean` qua `ManagementFactory.getPlatformMXBeans`.

**Câu 10.** `getMemoryUsed()` có thể khác `getTotalCapacity()` không?

**Đáp án:** Có. Alignment/allocator overhead và implementation-specific estimates làm khác biệt.

**Câu 11.** Code cache chứa loại dữ liệu gì?

**Đáp án:** JIT generated nmethods và runtime code/stubs của VM, khác heap class metadata.

**Câu 12.** Cờ code cache maximum thường là gì?

**Đáp án:** `-XX:ReservedCodeCacheSize=...`.

**Câu 13.** Code cache đầy có buộc application ném Java heap OOME không?

**Đáp án:** Không. VM có thể cảnh báo/dừng compilation, ảnh hưởng throughput/latency.

**Câu 14.** Tại sao có lúc thấy `CodeHeap`, lúc thấy `CodeCache`?

**Đáp án:** HotSpot có thể dùng segmented hoặc unsegmented code cache tùy runtime flags/build.

**Câu 15.** Reserved và committed khác thế nào?

**Đáp án:** Reserved giữ virtual address range; committed là memory đã được yêu cầu backing theo VM/OS mechanism.

**Câu 16.** NMT bật bằng flag nào?

**Đáp án:** `-XX:NativeMemoryTracking=summary` hoặc `detail` khi start.

**Câu 17.** Lệnh jcmd nào xem breakdown JVM native memory?

**Đáp án:** `jcmd <pid> VM.native_memory summary` nếu NMT được bật.

**Câu 18.** Lệnh xem class metadata chi tiết?

**Đáp án:** `jcmd <pid> VM.metaspace basic` trên HotSpot hỗ trợ.

**Câu 19.** Lệnh xem code cache usage/compilation status?

**Đáp án:** `jcmd <pid> Compiler.codecache`.

**Câu 20.** Vì sao NMT và RSS không nhất thiết bằng nhau?

**Đáp án:** Scope NMT chỉ một số allocations của VM; RSS còn gồm OS residency/shared pages và native allocations bên ngoài VM tracking.

**Câu 21.** Có thể tính total process RAM bằng `Xmx + MaxMetaspaceSize + MaxDirectMemorySize + ReservedCodeCacheSize` không?

**Đáp án:** Không, các metrics/caps khác định nghĩa và còn nhiều native areas chưa tính.

**Câu 22.** Metaspace tăng qua redeploy gợi ý kiểm tra gì?

**Đáp án:** Class loader retention/unloading, proxy class generation, cache/thread references, class counts và NMT/VM.metaspace.

**Câu 23.** Direct memory tăng dưới traffic gợi ý kiểm tra gì?

**Đáp án:** Buffer pooling/ownership, outstanding buffers/connections, async cancellation paths và BufferPoolMXBean direct usage.

**Câu 24.** Code cache gần đầy dưới load gợi ý kiểm tra gì?

**Đáp án:** Compiler.codecache, compiler logs, hot code footprint, code sweeper/fragmentation và JVM flags.

**Câu 25.** Vì sao không nên chọn memory caps chỉ từ idle metrics?

**Đáp án:** Các vùng có spikes theo class lazy-loading, request concurrency và JIT warm-up, cần peak/load data và headroom.

**Câu 26.** Demo chạy với JDK 24 có tự chứng minh mọi JDK 17 build hỗ trợ chính xác cùng pool names không?

**Đáp án:** Không. Code dùng API Java 17 và JDK 17-compatible flags; names/metrics cụ thể thuộc HotSpot implementation.

## 28. Checklist đưa vào production

1. Xác nhận **Java/JVM vendor, build, architecture** và flags hiện tại.
2. Xác định **container/OS memory budget**, chính sách OOMKilled và cách đo RSS/PSS.
3. Đo heap live set, Metaspace used/committed, compressed class space, direct pool và code cache.
4. Bật NMT summary **có chủ đích** trên môi trường chẩn đoán phù hợp.
5. Lấy NMT baseline và diff qua các pha startup/steady/peak/redeploy.
6. Dùng `VM.metaspace` để quan sát loaders và class metadata khi có dấu hiệu tăng trưởng.
7. Dùng `Compiler.codecache` để quan sát compilation enabled/stopped/usage.
8. Dùng `BufferPoolMXBean` cho `direct` và `mapped` pools nếu có.
9. Không đồng nhất `MaxDirectMemorySize` với mọi native allocations.
10. Không so reserved virtual code cache với RSS như hai chỉ số cùng đơn vị sử dụng.
11. Chạy representative workload gồm peak concurrency và lazy/rare code paths.
12. Tìm leaks/pools không plateau trước khi tăng caps.
13. Giữ headroom cho thread stacks/GC/JNI và non-VM allocations.
14. Thay đổi từng flag độc lập và ghi lại outcome/latency/GC/OOME.
15. Đừng đặt cap sát idle used.
16. Kiểm tra quy tắc lỗi/alert cho Metaspace OOME, direct OOME và code cache warning riêng.
17. Tránh giữ NMT detail hoặc logging verbose mãi mãi nếu overhead không được chấp nhận.
18. Benchmark performance không diagnostic I/O trước khi kết luận lợi ích.
19. Có rollback plan và kịch bản memory incident.
20. Không coi cấu hình demo 128/16/64 MiB là gợi ý production mặc định.

### Sơ đồ ghi nhớ nhanh

```text
                       JVM memory observation
                                 |
             +-------------------+-------------------+
             |                   |                   |
         Metaspace           Direct NIO            Code Cache
        class metadata       ByteBuffer             JIT/native
             |               backing                 blobs
             |                   |                   |
    MaxMetaspaceSize    MaxDirectMemorySize    ReservedCodeCacheSize
             |                   |                   |
     VM.metaspace         BufferPoolMXBean       Compiler.codecache
       / NMT Class          / NMT + OS             / NMT Code
             |                   |                   |
        Class loader        Pool lifetime          Compiler status
         growth/leaks      burst/caching            occupancy
             |                   |                   |
             +-------------------+-------------------+
                                 |
                   measured under real workload
                   compare used / committed / RSS
                   headroom + native/GC/thread costs
                                 |
                     change limits with evidence

Three independent controls != complete process memory cap.
```

**Kết luận:** Chỉ nên giới hạn Metaspace, NIO direct memory và code cache **theo dữ liệu quan sát được và yêu cầu vận hành**. Metaspace theo class metadata/class loader lifecycle; direct-buffer budget theo buffers/traffic/pooling; code cache theo hot methods/JIT behavior. Cả ba khác `-Xmx` và khác tổng native/RSS của process. Hãy dùng **NMT + đúng MXBean/jcmd của từng vùng + OS/container metrics**, rồi đặt limit có headroom đủ và load-test để kiểm tra, thay vì sao chép ba con số mặc định bất kỳ.

### Tài liệu chính thức và nguồn kỹ thuật

- [Oracle JDK 17 — `java` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `MaxMetaspaceSize`, `MaxDirectMemorySize`, `ReservedCodeCacheSize`, `NativeMemoryTracking`, segmented code cache và các VM flags liên quan.
- [Oracle JDK 17 — Troubleshooting Memory Leaks](https://docs.oracle.com/en/java/javase/17/troubleshoot/troubleshooting-memory-leaks.html): các dạng Java heap/Metaspace/Compressed Class Space OOME, native-memory diagnostics, NMT limits.
- [Oracle JDK 17 — GC Tuning, Other Considerations](https://docs.oracle.com/en/java/javase/17/gctuning/other-considerations.html): Metaspace, class metadata allocations, class space.
- [Oracle JDK 17 — Native Memory Tracking](https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html): NMT summary/detail, baseline/diff, overhead và scope.
- [Oracle JDK 17 — `jcmd` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `VM.native_memory`, `VM.metaspace`, `Compiler.codecache`.
- [BufferPoolMXBean API, Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/BufferPoolMXBean.html): direct/mapped pool metrics và estimation caveats.
- [MemoryPoolMXBean API, Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryPoolMXBean.html): used/committed/max/usage thresholds.
- [ByteBuffer API, Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): `allocate` vs `allocateDirect`.
- [OpenJDK JEP 387 — Elastic Metaspace](https://openjdk.org/jeps/387): reclaim/uncommit và quản lý native class metadata.
- [OpenJDK JEP 197 — Segmented Code Cache](https://openjdk.org/jeps/197): non-nmethods, profiled và non-profiled compiled code.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-006">JVM ergonomics: lựa chọn mặc định theo môi trường và tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# JVM ergonomics: lựa chọn mặc định theo môi trường và tài nguyên

## 1. Định nghĩa và mục tiêu

**JVM ergonomics** trong **HotSpot** là những chính sách tự chọn cấu hình ban đầu và điều chỉnh hoạt động runtime dựa vào tài nguyên được nhận diện, loại JVM, garbage collector (GC), bộ biên dịch JIT và hành vi ứng dụng. Mục tiêu là cung cấp default hợp lý mà không đòi hỏi người lập trình cấu hình hàng chục cờ ngay từ đầu.

Ví dụ cùng một ứng dụng Java:

- Khi JVM nhận diện máy có nhiều CPUs và RAM đủ lớn, HotSpot JDK 17 thường chọn G1 GC, heap sizing theo tỷ lệ memory và các thread counts phù hợp.
- Trong môi trường hiệu dụng chỉ có một CPU, VM có thể chọn Serial GC, ít GC/JIT worker threads hơn.
- Trong Linux container, JVM có thể dựa trên giới hạn **cgroups** thay vì toàn bộ tài nguyên host nếu build hỗ trợ và container detection bật.
- Khi người dùng đặt `-Xmx`, `-XX:+UseG1GC` hoặc `-XX:ActiveProcessorCount`, các explicit flags này thay đổi/cản một số ergonomic choices.

**Ergonomics không đảm bảo VM tự tối ưu nhất cho mọi workload**. Nó là baseline để đo rồi điều chỉnh theo nhu cầu, không phải danh sách giá trị cố định của Java Language Specification.

```text
                       HotSpot VM startup
                              |
        +---------------------+----------------------+
        |                     |                      |
  OS / architecture     Effective resources       User options
   JVM vendor/build    CPU/memory/cgroups       -Xmx, -XX:...
        |                     |                      |
        +---------------------+----------------------+
                              |
                       Ergonomic policies
                              |
          +-------------------+--------------------+
          |                   |                    |
      Heap sizes         GC selection         Compiler / GC
      Xms / Xmx          G1 / Serial          worker counts
          |                   |                    |
          +-------------------+--------------------+
                              |
                 App execution, GC/JIT heuristics
                              |
                 Measure → change if justified
```

### Cần phân biệt

1. **Product default:** giá trị được định nghĩa trong VM build, có thể mang origin `DEFAULT`.
2. **Ergonomic value:** giá trị được HotSpot tự chọn, thường mang origin `ERGONOMIC`.
3. **Explicit value:** do launch flags hoặc môi trường đặt, có thể mang origin `VM_CREATION` hay `ENVIRON_VAR`.

Một flag có thể hiển thị default 25%, nhưng derived heap maximum lại do `-Xmx` đặt rõ. **Hãy xem giá trị hiệu lực**, không chỉ xem input percentage.

## 2. Hai dạng ergonomics: startup và runtime

**Startup ergonomics** chọn những quyết định lúc JVM được tạo: default GC, initial/max heap, number of compiler threads, GC worker counts, code-cache-related defaults, allocation structures.

**Behavior-based ergonomics** là những heuristic adjustments trong lúc app chạy, ví dụ GC cố đáp ứng pause/throughput goals bằng cách thay đổi heap/young-generation sizing hay collection frequency trong giới hạn của collector và cấu hình.

JIT còn dùng counters, profiling, invocation/backedge patterns để quyết định compile hot methods/loops, deoptimize hoặc recompile nếu cần.

Không được suy mọi flag đang hiển thị từ `PrintFlagsFinal` là một giá trị VM sẽ tiếp tục thay đổi liên tục. Nhiều flags chỉ được quyết định lúc start; runtime adaptation nằm ở subsystem policies.

## 3. Đầu vào môi trường của HotSpot

Những yếu tố ảnh hưởng:

| Nhóm | Dữ liệu có thể được dùng | Hệ quả |
| --- | --- | --- |
| JVM build | Vendor, release, 32/64-bit, feature availability | GC/JIT defaults khác nhau |
| CPU | Số active processors, affinities/cpuset, quota | GC/compiler worker counts; GC choice |
| Memory | Physical RAM, virtual limits, container effective memory | Heap sizing, server-class classification |
| User flags | `-Xmx`, `ActiveProcessorCount`, `UseG1GC`… | Override các ergonomic choices |
| Collector | G1, Serial, Parallel, ZGC… | Tập heuristics/GC workers khác nhau |
| Workload | Allocation rate, survival, pause/throughput behavior | Adaptive GC và JIT policies |

**Không phải OS host resource = JVM effective resource.** Trong container, host có thể có 32 cores nhưng JVM thấy ít hơn. Ngoài ra, CPU quota, cpuset, CPU shares/request không cùng một khái niệm.

## 4. Heap ergonomics: tỷ lệ và giới hạn

Các cờ nổi bật trên HotSpot JDK 17:

- `-XX:MaxRAMPercentage=p`: chính sách heap maximum như một tỷ lệ của memory budget được VM sử dụng.
- `-XX:InitialRAMPercentage=p`: chính sách heap initial size.
- `-XX:MinRAMPercentage=p`: tham gia quy tắc small-memory heap sizing, **không** có nghĩa minimum heap luôn bằng p% RAM.
- `-XX:MaxRAM=<size>`: giá trị memory budget VM dùng cho một số sizing heuristics; **không phải** giới hạn RAM do OS enforce.
- `-Xms<size>`: đặt initial heap size.
- `-Xmx<size>`: đặt maximum Java heap size.

Trên nhiều HotSpot JDK 17 builds, defaults thường là `MaxRAMPercentage=25.0` và `InitialRAMPercentage=1.5625`. **Không hứa mọi JDK/vendor/platform hoặc heap size đều tính đúng bằng phép nhân này:** GC, alignment, small-memory constraints, address space và explicit flags còn tham gia.

### 4.1. Ví dụ có memory budget minh họa

```bash
java -XX:MaxRAM=512m -XX:MaxRAMPercentage=50 ErgonomicsProbe
```

Nếu không có `-Xmx` override và runtime đủ điều kiện, max heap có thể khoảng **256 MiB**. Trong thử nghiệm thực tế trên OpenJDK HotSpot 24.0.2, output `maxHeapMiB=256`.

### 4.2. Khi người dùng đặt `-Xmx`

```bash
java -Xmx192m -XX:MaxRAM=512m \
     -XX:MaxRAMPercentage=50 ErgonomicsProbe
```

`-Xmx192m` xác định maximum heap dù `MaxRAMPercentage` vẫn hiển thị 50. Trong kiểm thử, effective `MaxHeapSize=201326592` bytes, bằng 192 MiB.

**Đọc `MaxHeapSize` cùng flag origin** để không nhầm một percentage được cấu hình nhưng không quyết định kết quả.

### 4.3. Heap maximum không phải process RSS

Một Java process còn dùng Metaspace, compressed class space, code cache, direct buffers, JNI/native allocations, GC metadata và thread stacks. `MaxRAMPercentage=75` không đảm bảo toàn process chỉ dùng 75% RAM; ngược lại, nếu container quá nhỏ, heap 75% có thể không còn đủ native headroom.

## 5. `-Xms` và heap resizing

Nếu không đặt `-Xms`, HotSpot có thể chọn initial heap theo memory/GC heuristics và các min constraints. `-Xms` khác `-Xmx`:

- `Xms` ảnh hưởng dung lượng heap ban đầu và các quyết định expansion/shrink.
- `Xmx` là maximum capacity của Java heap theo cấu hình.
- `Xms=Xmx` có thể giảm heap resize và tăng predictability nhưng cũng tăng committed footprint sớm, có thể không phù hợp với workloads nhỏ/dao động.
- Heap **used**, heap **committed**, heap **reserved** và **process RSS** vẫn là những số khác nhau.

**Không mặc định ép `-Xms=-Xmx` cho mọi container.** Đo real memory footprint, GC pauses và startup.

## 6. Nguyên lý lựa chọn GC mặc định ở JDK 17

Theo tài liệu HotSpot GC ergonomics/JEP 248, **G1** là lựa chọn mặc định phổ biến từ JDK 9 trên **server-class machines**. Trong các HotSpot JDK 17 builds có server-class policy, với tài nguyên hiệu dụng thấp, VM có thể chọn **Serial GC**.

Mô tả server-class ở các tài liệu HotSpot thường dựa vào **ít nhất 2 processors** và bộ nhớ khoảng **1792 MiB trở lên**. VM có thể tính các giá trị này theo tài nguyên container được phát hiện thay vì tổng host resources.

```text
Effective resources
     |
    CPU and memory enough for server-class?
       /                            \
      Yes                            No
       |                              |
  G1 default                     Serial may be selected
       |                              |
  parallel GC work               simpler / low-overhead GC
```

Đây là **sơ đồ ergonomic policy điển hình JDK 17**, không phải guarantee ngôn ngữ và không ngụ ý mọi GC luôn được chọn đúng theo một sơ đồ áp dụng cho mọi JDK release.

### 6.1. Một CPU

```bash
java -XX:ActiveProcessorCount=1 -Xlog:gc=info -version
```

Trong thử nghiệm HotSpot 24.0.2, GC log hiển thị `Using Serial`.

### 6.2. Bốn CPUs

```bash
java -XX:ActiveProcessorCount=4 -Xlog:gc=info -version
```

Trong môi trường thử nghiệm có đủ memory, VM hiển thị `Using G1`. **Không suy bất kỳ hệ 4 processors cũng đủ memory để nhận G1**, hoặc build khác chọn y nguyên.

### 6.3. User override collector

```bash
java -XX:+UseG1GC ErgonomicsProbe
java -XX:+UseSerialGC ErgonomicsProbe
```

Chọn collector tường minh ghi đè một phần default policy. Không áp dụng G1 trên môi trường một CPU chỉ vì “server chuẩn phải dùng G1”; hãy đo pause latency, throughput, CPU và footprint.

## 7. GC pause và throughput goals là soft constraints

HotSpot collectors có các chính sách tối ưu hành vi. G1 cho phép:

```bash
java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 ErgonomicsProbe
```

`MaxGCPauseMillis` là **mục tiêu mềm**; VM cố gắng điều chỉnh collections để đạt mức pause mong muốn, không bảo đảm mọi pause nhỏ hơn 200 ms.

Các trade-offs:

- Pause target thấp hơn có thể làm collections thường xuyên hơn.
- Throughput giảm khi chi phí GC tăng.
- Heap/young-generation sizing thay đổi theo workload.
- GC không thể khắc phục live set quá lớn trong heap bị giới hạn.

**Đừng hạ pause goal mà không kiểm chứng GC logs và p95/p99 latency của ứng dụng.**

## 8. GC worker thread ergonomics

Các flags như:

- `-XX:ParallelGCThreads=N`.
- `-XX:ConcGCThreads=N`.
- `-XX:ActiveProcessorCount=N`.

liên quan số GC workers/concurrent GC workers ở những collector hỗ trợ. HotSpot có thể chọn số threads theo CPU resources, heap size và GC policy.

**Không có công thức portable “8 CPUs nghĩa 8 GC workers”.** Trong Serial GC, `ParallelGCThreads` có thể là 0/không áp dụng; đây không phải một lỗi.

Quá nhiều workers trong pod có CPU quota thấp có thể gây context switching/throttling. Quá ít workers trong heap lớn có thể làm pauses dài. Giữ defaults rồi chỉ override khi có profiling evidence.

## 9. Compiler/JIT ergonomics

HotSpot thường có tiered compiler phối hợp:

- Interpreter cho bytecode chưa đủ nóng.
- C1 cho quick compilation/profiling.
- C2 cho optimization sâu hơn ở hot paths.

```text
interpreter
    |
runtime invocation/backedge profiles
    |
 C1 compilation/profiling
    |
    +--> C2 optimized machine code where profitable
    |
 deoptimization/recompilation when assumptions change
```

Số threads cho compilation có thể được chọn qua `CICompilerCount` theo CPU resources và compiler policy. Không suy `CICompilerCount = availableProcessors`.

Code cache `ReservedCodeCacheSize` cũng có defaults tùy tiered settings/JDK release. Reserved code cache memory **không đồng nghĩa committed physical memory**.

### 9.1. Warm-up còn là yếu tố runtime

Lần gọi đầu của một method có thể interpreted; sau nhiều calls JVM biên dịch/inlining/OSR. Không có ngưỡng cố định “10.000 calls chắc chắn C2 trên mọi build”. `-XX:+PrintCompilation` có thể hỗ trợ diagnostics, nhưng output khác giữa runs.

### 9.2. Thay CPU có thể thay JIT threads

Trong kiểm thử, khi `ActiveProcessorCount=1`, `CICompilerCount=2`; khi `ActiveProcessorCount=4`, `CICompilerCount=3`. Đây là **giá trị quan sát của một build**, không phải API contract.

## 10. Linux container awareness

Trên các HotSpot JDK 17 Linux builds được hỗ trợ, **`UseContainerSupport`** thường bật mặc định. JVM có thể đọc cgroups memory limit/CPU quota/cpuset để tính effective resources.

```text
Host: many CPUs + large RAM
             |
   cgroups (memory, CPU quota, cpuset)
             |
          Container
             |
     Container-aware HotSpot
             |
   effective CPU/memory -> ergonomics
             |
    heap/GC/compiler default choices
```

### 10.1. Linux-specific diagnostics

```bash
java -Xlog:os+container=trace -version
```

Trên Linux build hỗ trợ, logs cho thông tin container detection. Nếu không rõ tag, dùng:

```bash
java -Xlog:help
```

**Trên Windows HotSpot, tag `os+container` có thể không được hỗ trợ** và `UseContainerSupport` có thể vắng trong HotSpotDiagnosticMXBean. Không kết luận đây là lỗi Java.

### 10.2. Tắt container support có rủi ro

```bash
java -XX:-UseContainerSupport ErgonomicsProbe
```

Lệnh mang tính chẩn đoán trên platform hỗ trợ. Trong container, tắt detection có thể làm JVM tính heap/threads theo **host resources** thay vì cgroup limits, làm tăng rủi ro container OOMKilled/CPU over-subscription.

### 10.3. CPU request khác CPU limit

Kubernetes CPU **request** hỗ trợ scheduling; **limit/quota** được kernel enforce; **cpuset** gán tập CPUs được phép chạy; JVM API `availableProcessors()` trả một integer theo detection policy. Không suy từ `requests.cpu=500m` rằng JVM luôn phải báo đúng 0.5 processor.

### 10.4. Memory request khác memory limit

Container memory request không là Java heap cap. Memory limit chịu kernel/cgroup enforcement, tính cả native memory và nhiều nguồn khác. HotSpot container support giúp ergonomic calculation nhưng **không tự đảm bảo process sẽ không bị OOMKilled**.

## 11. `-XX:ActiveProcessorCount` không tạo CPU quota

Cờ:

```bash
java -XX:ActiveProcessorCount=1 ErgonomicsProbe
java -XX:ActiveProcessorCount=4 ErgonomicsProbe
```

cho VM một số active processors được chọn thay cho autodetection trong nhiều ergonomic calculations. Nó **không giới hạn kernel scheduling**, không thay physical cores và không tự thay cgroup quota.

Khi ép 4 processors trong pod quota 1 CPU, VM có thể tạo nhiều GC/JIT worker threads hơn mà **không được cấp thêm CPU time**, dẫn tới contention/throttling.

### 11.1. Khi nào hữu ích?

- Thử nghiệm có kiểm soát CPU-view của VM.
- Workaround trên một platform có resource detection không phù hợp, sau khi kiểm tra.
- Chặn một số oversubscription nếu runtime/framework dựa vào availableProcessors.
- Kiểm chứng GC/CICompilerCount selection.

### 11.2. Khi nào không nên đặt?

Không nên áp một con số cố định cho mọi pod size nếu deployment có nhiều resource classes. Khi scale CPU limits từ 1 lên 8 nhưng giữ `ActiveProcessorCount=1`, VM có thể bỏ lỡ cơ hội parallelism.

## 12. Cgroup v1/v2 và phiên bản JDK

Các Linux containers có thể dùng **cgroups v1 hoặc v2** tùy host/runtime. Support/bug fixes container detection thay đổi qua JDK updates. Khi nâng kernel, Kubernetes hoặc JDK, một ứng dụng cùng YAML có thể báo processors/heap settings khác trước.

Checklist:

1. Kiểm tra cgroup memory limit, CPU quota/period, cpuset.
2. Kiểm tra container base image/JDK vendor/build.
3. Dùng `-Xlog:os+container=trace` trên HotSpot Linux.
4. So `Runtime.availableProcessors()`, `Runtime.maxMemory()` với runtime flags.
5. Xem CPU throttling và cgroup OOM events ở container metrics.

Nếu VM không phát hiện limits đúng, **không chỉ dùng `-XX:MaxRAM` để thay kernel memory enforcement**. Cần sửa detection/JDK/config và đảm bảo cgroup limit thực tế.

## 13. Các nguồn JVM options và precedence

Cờ JVM có thể đến từ:

- `java` command line.
- Entry point/startup shell scripts.
- `JAVA_TOOL_OPTIONS`.
- `JDK_JAVA_OPTIONS` (trên launchers hỗ trợ).
- Môi trường/kubernetes/Helm settings.
- Các công cụ deploy, Java agents hoặc framework launchers.

Các sources này có cơ chế precedence và constraint riêng; một số flags mutually exclusive có thể làm JVM startup thất bại. Đừng giả định “dòng lệnh xuất hiện cuối luôn thắng mọi flag” mà không xem actual runtime.

### 13.1. Xem origin và hiệu lực

`VMOption.getOrigin()` có thể báo `DEFAULT`, `ERGONOMIC`, `VM_CREATION`, `ENVIRON_VAR` hoặc nguồn khác. Dùng cùng effective value để biết VM thực sự chọn gì.

### 13.2. Không nhầm user input với derived result

Nếu `MaxRAMPercentage=50` được truyền nhưng `-Xmx192m` cũng có, output có thể gồm:

```text
MaxRAMPercentage=50.0 origin=VM_CREATION
MaxHeapSize=201326592 origin=VM_CREATION
```

Tham số 50% vẫn tồn tại, nhưng maximum heap không được tính từ tỷ lệ đó.

## 14. Công cụ dòng lệnh để xem quyết định

```bash
java -version
java -XX:+PrintFlagsFinal -version
java -XshowSettings:vm -version
java -Xlog:gc=info -version
```

Với Linux HotSpot container support:

```bash
java -Xlog:os+container=trace -version
```

Các lệnh có mục đích khác nhau:

| Lệnh | Câu hỏi |
| --- | --- |
| `java -version` | JVM vendor/build/version? |
| `PrintFlagsFinal` | Effective VM flags và origin? |
| `-XshowSettings:vm` | VM/max heap settings? |
| `-Xlog:gc=info` | Collector đang được chọn? |
| `-Xlog:os+container=trace` | Linux cgroup detection? |

Không lấy `PrintFlagsFinal` của **một launcher process khác** làm bằng chứng tuyệt đối cho một service chạy với options/env/container khác.

### 14.1. Process đang chạy

```bash
jcmd -l
jcmd <pid> VM.flags
jcmd <pid> VM.command_line
jcmd <pid> VM.info
```

`<pid>` là ký hiệu cần thay PID thật. Lệnh `jcmd` có thể bị giới hạn bởi permissions/attach mechanisms, containers hoặc thiếu JDK tools. Không đọc một lỗi attach là lỗi ergonomic VM.

## 15. Chương trình Java 17+ độc lập: ErgonomicsProbe

Tạo tệp **`ErgonomicsProbe.java`**:

```java
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import java.lang.management.ManagementFactory;

public final class ErgonomicsProbe {
    static void option(HotSpotDiagnosticMXBean bean, String key) {
        try {
            VMOption o = bean.getVMOption(key);
            System.out.println(key + "=" + o.getValue()
                    + " origin=" + o.getOrigin());
        } catch (IllegalArgumentException ex) {
            System.out.println(key + "=unavailable");
        }
    }

    public static void main(String[] args) {
        Runtime runtime = Runtime.getRuntime();

        System.out.println("probe=ErgonomicsProbe");
        System.out.println("visibleProcessors="
                + runtime.availableProcessors());
        System.out.println("maxHeapMiB="
                + (runtime.maxMemory() / 1048576));

        HotSpotDiagnosticMXBean bean =
                ManagementFactory.getPlatformMXBean(
                        HotSpotDiagnosticMXBean.class);

        for (String name : new String[] {
                "UseContainerSupport",
                "ActiveProcessorCount",
                "MaxRAMPercentage",
                "InitialRAMPercentage",
                "MaxHeapSize",
                "InitialHeapSize",
                "UseG1GC",
                "UseSerialGC",
                "UseParallelGC",
                "TieredCompilation",
                "CICompilerCount",
                "ParallelGCThreads",
                "ConcGCThreads",
                "ReservedCodeCacheSize"
        }) {
            option(bean, name);
        }
    }
}
```

### 15.1. Biên dịch và chạy

```bash
javac --release 17 ErgonomicsProbe.java
java ErgonomicsProbe
```

Chương trình được **biên dịch thật bằng `javac --release 17`**, chạy thành công trên OpenJDK HotSpot 24.0.2 với nhiều flags. Đây là **mã tương thích Java 17**, không phải khẳng định đã thử trên binary JDK 17 cụ thể.

### 15.2. Stdout

Dòng đầu **cố định**:

```text
probe=ErgonomicsProbe
```

Những dòng khác phụ thuộc runtime. **Một output quan sát** trên HotSpot/Windows:

```text
visibleProcessors=28
maxHeapMiB=30208
UseContainerSupport=unavailable
ActiveProcessorCount=-1 origin=DEFAULT
MaxRAMPercentage=25.0 origin=DEFAULT
InitialRAMPercentage=1.5625 origin=DEFAULT
MaxHeapSize=31675383808 origin=ERGONOMIC
InitialHeapSize=2147483648 origin=ERGONOMIC
UseG1GC=true origin=ERGONOMIC
UseSerialGC=false origin=DEFAULT
UseParallelGC=false origin=DEFAULT
TieredCompilation=true origin=DEFAULT
CICompilerCount=12 origin=ERGONOMIC
ParallelGCThreads=20 origin=DEFAULT
ConcGCThreads=5 origin=ERGONOMIC
ReservedCodeCacheSize=251658240 origin=ERGONOMIC
```

**Không đưa output mẫu này vào unit test bắt buộc**: các số, origins và flag availability phụ thuộc môi trường.

### 15.3. Ý nghĩa `unavailable`

Ở một số OS/VM builds, `UseContainerSupport` không tồn tại; `HotSpotDiagnosticMXBean.getVMOption` sẽ ném `IllegalArgumentException`. Demo cố ý in `unavailable` để không crash toàn bộ khi đọc một flag không có. Lỗi flag không tồn tại **không phải bằng chứng JDK không có garbage collector**.

### 15.4. Vì sao `maxHeapMiB` có thể khác `MaxHeapSize`?

`Runtime.maxMemory()` báo maximum memory dùng cho Java heap theo runtime; demo chia cho 1,048,576 bằng integer division để in MiB, có rounding. `MaxHeapSize` flag xuất raw bytes.

## 16. Thử nghiệm CPU: một vs bốn processors

```bash
java -XX:ActiveProcessorCount=1 ErgonomicsProbe
java -XX:ActiveProcessorCount=4 ErgonomicsProbe
```

Trong thử nghiệm HotSpot 24/Windows, một số dòng **được quan sát**:

| Flag/metric | 1 CPU | 4 CPUs |
| --- | --- | --- |
| `visibleProcessors` | 1 | 4 |
| `UseG1GC` | false | true |
| `UseSerialGC` | true | false |
| `CICompilerCount` | 2 | 3 |
| `ParallelGCThreads` | 0 | 4 |

**Các số này phụ thuộc CPU/memory environment và build**. Ở container memory thấp, bốn processors chưa đủ bảo đảm collector là G1.

### 16.1. Kết luận đúng

Effective processor count có thể tác động đồng thời **GC selection**, **GC worker count** và **JIT compiler threads**. Vì vậy không thể lấy chênh lệch hiệu năng của hai runs và quy nguyên nhân duy nhất cho “một thread chạy song song nhanh hơn”.

### 16.2. Không biến thử nghiệm thành CPU resource limit

`ActiveProcessorCount` chỉ đổi cách VM nhìn tài nguyên cho heuristic; kernel vẫn schedule theo OS/cgroups.

## 17. Thử nghiệm heap: percentage vs `-Xmx`

```bash
java -XX:MaxRAM=512m -XX:MaxRAMPercentage=50 ErgonomicsProbe
java -Xmx192m -XX:MaxRAM=512m \
     -XX:MaxRAMPercentage=50 ErgonomicsProbe
```

Trên HotSpot 24.0.2, các dòng quan trọng:

```text
# Không -Xmx
maxHeapMiB=256
MaxRAMPercentage=50.0 origin=VM_CREATION
MaxHeapSize=268435456 origin=ERGONOMIC

# Có -Xmx192m
maxHeapMiB=192
MaxRAMPercentage=50.0 origin=VM_CREATION
MaxHeapSize=201326592 origin=VM_CREATION
```

Đây là **trích đoạn output từ hai lần chạy riêng**, không phải một stdout nguyên khối của chương trình.

### 17.1. Số học minh họa

`512 MiB × 50% = 256 MiB`, và `192 MiB = 201326592 bytes`. Dù vậy, trên các configurations khác heap alignment và small-heap policy có thể làm con số final không khớp phép nhân naive.

### 17.2. Ý nghĩa của explicit max

`MaxRAMPercentage` có thể vẫn là 50 nhưng `MaxHeapSize` do `-Xmx192m` quyết định. Luôn đọc cả origin.

## 18. Container memory budget và native headroom

Giả sử một container có memory limit 2 GiB. Một JVM không chỉ dùng heap:

```text
Container memory budget
        |
        +--> Java heap (Xmx / RAM percentage)
        +--> Metaspace / compressed class space
        +--> JIT code cache
        +--> Thread stacks
        +--> GC structures / native VM internal memory
        +--> NIO direct buffers / JNI native allocations
        +--> Mapped files / shared libraries / page accounting
```

**Không dùng công thức container memory = heap maximum.** Nếu heap chiếm sát 100% effective memory, process có thể bị kernel OOMKilled dù Java heap chưa vượt max.

### 18.1. Phân biệt lỗi

- `OutOfMemoryError: Java heap space`: Java heap allocation failure trong limit.
- `OutOfMemoryError: Metaspace`: class metadata/native metadata pressure.
- Direct-buffer reserve-related `OutOfMemoryError`: NIO direct budget pressure.
- **OOMKilled**: kernel/cgroup enforcement, có thể không có Java stack trace.

### 18.2. Budget dựa theo quan sát

Ghi heap used/committed, native memory (NMT), Metaspace, threads, direct memory, code cache, RSS/PSS và cgroup memory.current. Chọn `-Xmx`/percentages phù hợp với **peak load + headroom**, không chỉ idle metrics.

## 19. Vì sao default tốt hơn việc ép mọi flag?

Một cấu hình “tự tối ưu” thủ công thường gồm nhiều flags:

```bash
java -Xms... -Xmx... -XX:+UseG1GC \
     -XX:ParallelGCThreads=... \
     -XX:ConcGCThreads=... \
     -XX:CICompilerCount=... \
     ErgonomicsProbe
```

Đây chỉ là **sơ đồ cú pháp**, không phải lệnh chạy được vì các giá trị `...` chưa được điền.

Khi ép tất cả cờ:

- JVM ít được phép thích ứng với thay đổi container resources.
- Các flags có thể conflict hoặc vô nghĩa với collector đã chọn.
- Tối ưu trên laptop có thể gây regression trong production.
- Không thể quy chênh lệch hiệu năng cho một thay đổi duy nhất.

**Bắt đầu với default** rồi đo, thử override từng biến và có rollback plan.

## 20. Khi nào chọn explicit heap vs ergonomic percentage?

**Explicit `-Xmx`** phù hợp khi:

- Deployment có memory budget ổn định, đã benchmark.
- Cần predictable maximum heap giữa nhiều environments.
- Native headroom đã được tính rõ.
- App có behavior ít thay đổi khi scale.

**`MaxRAMPercentage`** phù hợp khi:

- Dùng cùng artifact trong pods/VMs có memory budgets khác nhau.
- Muốn heap max thích ứng tương đối.
- Có container-aware JDK build và headroom hợp lý.
- Có monitoring đủ phát hiện thay đổi resource detection.

Dù dùng cách nào, **đừng giả định process native memory tự nằm trong cùng cap**.

## 21. Vấn đề default đổi theo JDK release

Tài liệu cũ (Java 5/8) có thể mô tả **Parallel GC** mặc định trên server-class và client/server compiler split. HotSpot JDK 17 khác: G1 là lựa chọn phổ biến trên server-class (JEP 248), tiered C1/C2 compiler và các cơ chế heap/container heuristics hiện đại.

Không copy trực tiếp “best JVM flags 2015” vào JDK 17 container. Khi nâng JDK:

1. Chạy lại `java -version` và `PrintFlagsFinal`.
2. Kiểm tra effective processor count/heap/GC.
3. Load test với workload thực tế.
4. Xem release notes về GC/container ergonomics.
5. Đánh giá native/container memory và latency regressions.

**Vendor differences:** OpenJ9, native-image runtime hoặc customized JDK có memory/GC/JIT policies khác. Các `-XX` options ở đây là HotSpot-specific, không phải JVM universal standard.

## 22. Khi nào cần can thiệp GC/JIT threads?

Ví dụ pod bị CPU throttling, GC pause tăng, JIT compile queue dài. Cần phân biệt:

- Processor count JVM nhìn thấy.
- Actual CPU quota/throttle metrics.
- GC collector và worker counts.
- Compiler threads/compilation events.
- Heap live set, allocation rate, GC logs.
- Workload/thread-pool concurrency.

Không vội đặt `-XX:ParallelGCThreads=1` hay `CICompilerCount=1` chỉ vì CPU limit “thấy nhỏ”. Điều chỉnh này có thể kéo dài GC/JIT work. Kiểm chứng ở cùng CPU quota/traffic thực tế.

### Sơ đồ quyết định

```text
Thấy latency/CPU vấn đề?
        |
        v
Thu GC logs + JIT stats + CPU throttling
        |
        +--> GC pauses?      → xem heap/collector/GC workers
        +--> JIT CPU spikes? → xem compiler/CICompilerCount
        +--> Native OOM?     → xem headroom, đừng chỉ tăng Xmx
        +--> First request?  → xem class init/warm-up/dependencies
        |
        v
Đổi ít flags có mục tiêu; kiểm tra phân phối kết quả
```

## 23. Đo và so sánh đúng

Một protocol:

1. Ghi rõ JDK vendor/build, OS, CPU quota và memory limit.
2. Dùng cùng class files/JAR và input workload.
3. Chạy **nhiều JVM processes mới** cho mỗi config.
4. Đo startup, first-request, steady-state throughput và pause distributions riêng.
5. Xen kẽ thứ tự các configs để giảm cache/order bias.
6. Không bật verbose logs trong baseline timing runs.
7. Chạy diagnostic runs riêng để xác nhận GC/flags/container detection.
8. Kiểm tra correctness/output như nhau.
9. Báo median, spread, p95/p99 thay vì chỉ run nhanh nhất.
10. Có rollback nếu thay đổi gây OOM, latency hoặc CPU regressions.

`ActiveProcessorCount` thay perceived CPUs nhưng không thay host quota; `MaxRAM` thay VM memory heuristics nhưng không tạo cgroup limit. **Thí nghiệm cần ghi đúng biến đã thay đổi.**

## 24. Những nhầm lẫn điển hình

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| “HotSpot có một default heap cho mọi máy.” | Heap ergonomics phụ thuộc memory/JDK/GC/flags. |
| “-Xmx giới hạn toàn process RSS.” | Chỉ giới hạn Java heap. |
| “MaxRAMPercentage=50 là toàn JVM tối đa 50% RAM.” | Liên quan maximum heap heuristic. |
| “MinRAMPercentage=50 luôn đặt minimum heap 50%.” | Chủ yếu thuộc small-memory sizing policy. |
| “MaxRAM đặt OS memory limit.” | Chỉ ảnh hưởng VM heuristics. |
| “Có -Xmx thì percentage phải tự đổi.” | Percentage vẫn hiển thị, nhưng derived max heap theo -Xmx. |
| “G1 luôn là default ở mọi môi trường JDK17.” | Có non-server-class/Serial cases. |
| “Một CPU và Serial GC là lỗi Java.” | Có thể là lựa chọn ergonomic đúng policy. |
| “Bốn CPUs chắc chắn G1 dù memory chỉ vài trăm MiB.” | Collector selection còn phụ thuộc memory/config. |
| “MaxGCPauseMillis bảo đảm không có pause dài hơn.” | Soft GC goal. |
| “ActiveProcessorCount thay CPU quota/cpuset.” | Nó thay CPU view của JVM, không giới hạn kernel. |
| “availableProcessors luôn báo physical core count.” | Có thể phụ thuộc cgroup/affinity/explicit VM flag. |
| “JDK_JAVA_OPTIONS là nguồn flags duy nhất.” | Có command line, scripts, env và orchestrator. |
| “UseContainerSupport luôn hiện trên Windows.” | Platform-specific. |
| “CPU request của Kubernetes bằng JVM processor count.” | Request, quota và cpuset khác nhau. |
| “CICompilerCount luôn đúng bằng CPUs.” | Heuristics có threshold/caps. |
| “Mọi GC đều dùng ParallelGCThreads.” | Tùy collector; Serial có thể không dùng parallel workers. |
| “Xms=Xmx luôn giảm latency và memory.” | Có trade-off committed footprint và resize. |
| “JDK8 defaults áp nguyên sang JDK17.” | Defaults đã đổi qua releases. |
| “Chỉ cần tăng heap là chữa OOMKilled.” | Native/headroom có thể mới là nguyên nhân. |
| “Một lần benchmark trên laptop đủ cho pod production.” | Cần workload và resource limits tương đồng. |

## 25. Bài tập tự kiểm tra kèm đáp án

**1. JVM ergonomics là gì?**

Là các chính sách của HotSpot tự chọn cấu hình và thích ứng theo resources/workload thay vì yêu cầu mọi flags được đặt rõ.

**2. `MaxRAMPercentage` giới hạn thứ gì?**

Nó tham gia maximum Java heap ergonomic sizing, không giới hạn toàn bộ native process memory.

**3. `MaxRAM=512m` có buộc container chỉ được dùng 512 MiB RAM không?**

Không. Đây là value VM dùng cho memory-sizing heuristics, không thay cgroup enforcement.

**4. 512 MiB với 50% cho heap max khoảng bao nhiêu?**

Khoảng 256 MiB trước những alignment/GC/min-max constraints.

**5. Nếu thêm `-Xmx192m` thì heap max theo gì?**

Theo explicit `-Xmx192m`.

**6. Vì sao 1 processor có thể dẫn tới Serial GC?**

VM có thể phân loại resource là non-server-class trong HotSpot JDK17 policy.

**7. Vì sao không nói “G1 luôn default JDK17”?**

Vì GC selection phụ thuộc available CPUs/effective memory và GC flags.

**8. `ActiveProcessorCount=4` có thêm CPU thật cho container quota 1 CPU không?**

Không; kernel quota không đổi.

**9. `MaxGCPauseMillis=200` có là hard upper bound?**

Không, là soft GC tuning goal.

**10. `CICompilerCount` có luôn bằng `availableProcessors` không?**

Không. VM chọn theo compiler policy/resources.

**11. `availableProcessors` lấy ở đâu?**

`Runtime.getRuntime().availableProcessors()`.

**12. Maximum Java heap xem bằng Java API nào?**

`Runtime.getRuntime().maxMemory()`.

**13. VM flag origin đọc bằng API nào trên HotSpot?**

`HotSpotDiagnosticMXBean.getVMOption(name).getOrigin()`.

**14. Origin `ERGONOMIC` khác `VM_CREATION` thế nào?**

Một cái là giá trị VM tự chọn, cái kia thường là option truyền khi VM được tạo.

**15. Vì sao Windows có thể báo `UseContainerSupport=unavailable`?**

Flag/container log hỗ trợ theo platform/JDK build; không chứng minh Linux không có.

**16. Diagnostic log cho Linux cgroup detection?**

`java -Xlog:os+container=trace -version` trên runtime hỗ trợ.

**17. Dùng lệnh nào biết collector?**

`java -Xlog:gc=info -version` hoặc runtime GC monitoring.

**18. Vì sao native headroom quan trọng trong pod?**

Vì Metaspace, code cache, stacks, JNI/direct buffers và GC native memory không nằm trong `-Xmx`.

**19. `-Xms=-Xmx` có bắt buộc tốt hơn dynamic sizing?**

Không; predictability đổi lấy memory footprint, cần đo workload.

**20. Khi nâng JDK nên kiểm tra gì?**

Actual flags, CPU detection, collector, heap sizes, GC/JIT behavior, latency và memory.

**21. Vì sao không đặt nhiều GC/compiler flags cùng lúc khi thử?**

Không thể quy tác động cho từng thay đổi và tăng nguy cơ regression.

**22. Tại sao CPU throttling vẫn xảy ra với `ActiveProcessorCount=4`?**

Vì flag không thay OS/cgroup quota.

## 26. Checklist sử dụng trong thực tế

1. Ghi JVM vendor/version/build, OS, architecture.
2. Đọc đầy đủ launch flags thực tế và environment-injected options.
3. Ghi `MaxRAMPercentage`, `InitialRAMPercentage`, `MaxHeapSize`, `InitialHeapSize` và origins.
4. Xác minh `Runtime.availableProcessors()` từ trong process/container.
5. Với Linux, so cgroup memory/CPU limits với `os+container` logs.
6. Ghi GC lựa chọn thực sự, không suy từ tên JDK.
7. Nếu CPU hạn chế, kiểm tra GC worker counts và `CICompilerCount`.
8. Phân biệt heap max với total RSS và cgroup memory charge.
9. Đánh giá headroom cho Metaspace, code cache, direct memory, thread stacks và JNI.
10. Dùng `-Xmx` nếu cần fixed predictable max; percentages khi phù hợp elastic environments.
11. Đo GC pause/throughput/heap occupancy cùng CPU throttling.
12. Không tự đặt pause goal quá chặt mà thiếu kết quả benchmark.
13. Trước khi override default, mô tả bottleneck cụ thể.
14. Thay từng cờ và giữ các điều kiện khác nhất quán.
15. Tách runtime diagnostics khỏi baseline performance runs.
16. Test startup, first-request, steady-state và peak load.
17. Kiểm chứng container OOMKilled khác Java heap OOME.
18. Có rollback plan và alerting.
19. Khi đổi JDK/container runtime, kiểm tra lại các chính sách ergonomic.
20. Tránh coi output ví dụ cục bộ là default universal.

### Tổng kết

```text
HotSpot ergonomics = defaults informed by environment
  |
  +-- Effective memory -> heap sizing
  +-- Effective CPUs   -> GC/compiler counts and policies
  +-- JVM release      -> GC/JIT feature/default selection
  +-- Explicit flags   -> override or constrain heuristics
  |
  +-- Runtime behavior -> GC/JIT adaptive decisions
  |
  +-- Reality check    -> observe VM flags/GC logs/JMX/OS metrics

Important:
  MaxRAMPercentage != full-process RAM cap
  ActiveProcessorCount != OS CPU quota
  Xmx != RSS
  Default != guaranteed optimal
```

**Kết luận:** Trước khi áp dụng “bộ cờ JVM tối ưu”, hãy quan sát **VM nhận diện bao nhiêu CPU/RAM**, **default collector nào được chọn**, **maximum heap hiệu lực**, **GC/compiler thread counts** và **memory headroom**. HotSpot ergonomics là xuất phát điểm hữu ích cho nhiều môi trường, đặc biệt trong container, nhưng phải được xác nhận bằng runtime diagnostics và kiểm thử thực tế.

### Tài liệu chuẩn

- [Oracle JDK 17 — GC Ergonomics](https://docs.oracle.com/en/java/javase/17/gctuning/ergonomics.html): heap, GC/compiler default selections và behavior-based tuning.
- [Oracle JDK 17 — java Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xmx`, `MaxRAMPercentage`, `ActiveProcessorCount`, `UseContainerSupport`, `-Xlog` và HotSpot options.
- [OpenJDK JEP 248 — Make G1 the Default Garbage Collector](https://openjdk.org/jeps/248): default G1 trên server-class environments.
- [HotSpotDiagnosticMXBean API (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.management/com/sun/management/HotSpotDiagnosticMXBean.html): runtime VM flags.
- [VMOption API (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.management/com/sun/management/VMOption.html): flag values và origins.
- [Runtime API (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html): processor count và heap max APIs.
- [Oracle JDK 17 — jcmd](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `VM.flags`, `VM.command_line` và `VM.info`.
- [Red Hat — Java 17 Container Awareness](https://developers.redhat.com/articles/2022/04/19/java-17-whats-new-openjdks-container-awareness): cgroups v1/v2, GC/heap resource detection và diagnostics.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-007">Container: giới hạn CPU, bộ nhớ và khoảng dự phòng cho toàn tiến trình</a>

<details>
<summary>Xem chi tiết</summary>

# Container: giới hạn CPU, bộ nhớ và khoảng dự phòng cho toàn tiến trình JVM

## Mục tiêu

Khi triển khai ứng dụng Java trong Docker hoặc Kubernetes, JVM **không sở hữu toàn bộ RAM và CPU của host**, và giới hạn **Java heap** không đồng nghĩa giới hạn **toàn tiến trình**. Một container có memory limit 1 GiB nhưng chạy `-Xmx1g` có thể bị kernel **OOMKilled** do metaspace, JIT code cache, native allocations, direct buffers, thread stacks, GC bookkeeping hoặc các phần bộ nhớ khác vượt giới hạn cgroup. Một container có CPU quota 1,5 CPU không được cấp “một lõi rưỡi” dành riêng; CPU time thường được quản lý theo quota/period và workload có thể bị **throttled**.

HotSpot JDK hiện đại có **container awareness**: nó có thể nhận diện cgroup memory/CPU để tính số processors và chọn JVM ergonomics. Tuy nhiên, kernel vẫn quản lý hard limits; các flags như `MaxRAMPercentage`, `Xmx` và `ActiveProcessorCount` chỉ tác động cách JVM sử dụng ngân sách, **không tăng ngân sách do hệ điều hành cấp**.

Sau bài học bạn có thể:

1. Phân biệt host capacity, container resource **requests**, **limits**, cgroup v1/v2 và resources JVM thật sự nhìn thấy.
2. Giải thích `cpu.max`, `cpu.stat`, `cpu.weight`, `cpuset.cpus.effective`, `memory.max`, `memory.current`, `memory.stat` và `memory.events`.
3. Đọc CPU quota/period thành budget tương đương CPUs; biết vì sao integer `availableProcessors()` không phải fractional quota hay OS thread limit.
4. Thiết kế ngân sách cho **tổng bộ nhớ cgroup/JVM process**: heap + metaspace + code cache + thread stacks + direct/native memory + file cache/overheads + safety margin.
5. Dùng `-Xms`, `-Xmx`, `MaxRAMPercentage`, `InitialRAMPercentage`, `UseContainerSupport`, `ActiveProcessorCount` đúng vai trò và kiểm tra effective values.
6. Phân biệt Java heap OOME, Metaspace/native OOME, container OOMKilled, Kubernetes evictions và CPU throttling.
7. Viết chương trình Java 17+ mô tả budget có stdout xác định, cùng ứng dụng kiểm tra runtime có output phụ thuộc thực tế.
8. Quan sát JVM bằng `java -XshowSettings:system`, `-Xlog:os+container=trace`, `PrintFlagsFinal`, `jcmd`, Native Memory Tracking (NMT), GC logs và cgroup metrics.
9. Làm load test trên cùng JDK/container limits, gắn p95/p99 latency với throttling, GC, memory peaks và downstream limits.
10. Lập checklist vận hành, cảnh báo và rollout mà không tự đặt một tỷ lệ heap đúng cho mọi service.

## 1. Ba lớp tài nguyên phải theo dõi

~~~text
                     HOST / NODE
                Physical CPU and RAM
                          |
                 CONTAINER RUNTIME
       cgroup quota/period, memory limit,
       cpuset, scheduling weights, swap...
                          |
                    JVM PROCESS
             +------------+------------+
             |                         |
         Java heap               Native/non-heap
      -Xmx / MaxRAM              Metaspace / Code Cache
                                 Thread stacks / JNI
                                 Direct buffers / GC
                                 Mapped files / caches
             |                         |
             +------------+------------+
                          |
             Application throughput, p99,
             OOM events, CPU throttling
~~~

- **Host CPU/RAM:** toàn máy/node; không mặc định bằng ngân sách của container.
- **Container hard limit:** OS/cgroup thực thi; giới hạn có thể thuộc một container và có thêm ancestor constraints.
- **JVM-selected heap maximum:** phần của tổng memory budget mà HotSpot cho Java objects sử dụng.
- **Process RSS:** bộ nhớ resident được OS ghi nhận cho process, khác cả heap used lẫn cgroup memory.current.
- **Cgroup memory.current:** số memory charge mà Linux cgroup ghi nhận, có thể gồm nhiều processes, file cache, kernel allocations tùy controller/OS.
- **Kubernetes request:** giá trị lập lịch/resource shares, không phải một cam kết physical resources luôn nằm riêng chờ JVM.

**Đừng suy** rằng `-Xmx768m` nghĩa toàn bộ container sử dụng tối đa 768 MiB, hoặc `Runtime.maxMemory()` cho biết hard memory limit của cgroup.

## 2. Cgroup v1 và cgroup v2

Linux cgroups quản lý tài nguyên theo controllers. Một môi trường có thể dùng cgroup v1 hoặc v2; mount path và delegation phụ thuộc runtime, OS và container configuration.

| Nội dung | cgroup v2 thường gặp | cgroup v1 thường gặp |
| --- | --- | --- |
| Hard memory limit | `memory.max` | `memory.limit_in_bytes` |
| Current usage | `memory.current` | `memory.usage_in_bytes` |
| Memory event counters | `memory.events` | `memory.failcnt` và counters tương ứng |
| CPU quota / period | `cpu.max` | `cpu.cfs_quota_us` / `cpu.cfs_period_us` |
| CPU throttling | `cpu.stat` | `cpu.stat` |
| Relative scheduling weight | `cpu.weight` | `cpu.shares` |
| CPU affinity mask | `cpuset.cpus.effective` | cpuset controller entries |

Kiểm tra Linux filesystem type:

~~~bash
stat -fc %T /sys/fs/cgroup
~~~

Một kết quả `cgroup2fs` thường biểu thị cgroup v2. Nếu khác, kiểm tra mountinfo/controller versions, **không kết luận v1 chỉ từ việc một file v2 thiếu**.

Các cgroup files trong bài thường nằm dưới `/sys/fs/cgroup/` nếu runtime expose unified hierarchy phù hợp. Container namespace, rootless runtime, security restrictions, OS distribution và nested cgroups có thể làm path khác. **Không hardcode đường dẫn như một Java SE portable API.**

## 3. CPU quota: ý nghĩa của cpu.max

Ví dụ giả định cho cgroup v2:

~~~text
150000 100000
~~~

Hai giá trị lần lượt là **CPU quota microseconds** và **CPU period microseconds**:
- 150000 ÷ 100000 = **1,5 CPU-equivalent**.
- Với 2 runnable threads trên hai logical CPUs, chúng có thể dùng phần lớn quota trước khi period kết thúc, rồi bị throttled.
- Đây là **budget thời gian CPU**, không phải 1,5 physical cores được dành riêng, không phải số workers cho Java thread pool.
- Với `cpu.max` chứa `max 100000`, controller đó không đặt hard CPU quota ở cấp hiện tại; host, cpuset và ancestor cgroups vẫn có thể giới hạn.

Công thức minh họa:

~~~text
cpu_equivalent = quota_us / period_us
150000 / 100000 = 1.5
250000 / 100000 = 2.5
100000 / 100000 = 1.0
~~~

**Không làm tròn 1,5 thành 2 rồi nói container sở hữu 2 CPU.** Một JVM API cần integer processor count có thể làm tròn khi lựa heuristics, nhưng kernel vẫn giới hạn tổng CPU bandwidth theo quota thực.

### cpu.stat và CPU throttling

Trên cgroup v2 hỗ trợ:

~~~bash
cat /sys/fs/cgroup/cpu.max
cat /sys/fs/cgroup/cpu.stat
cat /sys/fs/cgroup/cpuset.cpus.effective
~~~

`cpu.stat` có thể hiển thị `usage_usec`, `nr_periods`, `nr_throttled`, `throttled_usec`. Đây là **counters tích lũy**; để phân tích, lấy hai snapshots theo khoảng thời gian và tính delta:
- `Δnr_throttled / Δnr_periods` giúp mô tả tỷ lệ periods từng bị throttle.
- `Δthrottled_usec` thể hiện throttled duration theo metrics của kernel; không suy trực tiếp thành thời gian chờ của từng HTTP request.
- Correlate với throughput, request latency, CPU usage, GC/JIT bursts.

Nếu CPU quota thấp, tăng số executor workers có thể tăng runnable contention và throttling thay vì tăng throughput.

## 4. CPU weight không phải CPU limit

`cpu.weight` của cgroup v2 (hoặc `cpu.shares` trên v1) là trọng số **tương đối** dùng khi CPU có cạnh tranh. Nó khác với `cpu.max`:
- Weight cao có thể được nhiều CPU share hơn dưới contention.
- Weight không cam kết một số CPU core riêng.
- Khi node rảnh, workload không bị hard CPU quota có thể tận dụng capacity còn trống.
- Hard CPU quota vẫn có thể khiến một container bị throttle ngay cả khi một số CPU ở host chưa bận, tùy constraints và placement.

### cpuset/affinity

`cpuset.cpus.effective` là các logical CPUs mà cgroup được phép dùng. Ví dụ:

~~~text
0-3
~~~

không phải “được bảo đảm 4 core thời gian đầy đủ”. Nó nói tập CPUs có thể lập lịch, trong khi quota/host contention còn quyết định bandwidth. Một process có thể bị giới hạn bởi cả cpuset=0-3 và quota tương đương 1,5 CPUs.

## 5. Kubernetes CPU requests và limits

Một fragment hợp lệ khi đặt trong container specification:

~~~yaml
resources:
  requests:
    cpu: "250m"
    memory: "512Mi"
  limits:
    cpu: "1500m"
    memory: "1Gi"
~~~

- CPU request **250m** = 0,25 CPU-equivalent cho scheduling và shares policy, **không phải CPU hard minimum always reserved exclusively**.
- CPU limit **1500m** = 1,5 CPU-equivalent hard bandwidth limit khi Kubernetes/runtime áp dụng CPU quota.
- Memory request **512Mi** phục vụ scheduling/QoS/policies; nó **không quyết định Java heap** nếu VM lấy memory limit từ cgroup.
- Memory limit **1Gi** = 1024 MiB = 1.073.741.824 bytes; vượt giới hạn cgroup có thể dẫn tới OOM kill.
- `m` trong CPU là milliCPU; `Mi`/`Gi` trong memory là binary units.

**Request khác limit.** Kubernetes HPA CPU utilization thông thường tính tỷ lệ sử dụng trên **CPU requests**, không nhất thiết trên limits. Nếu requests được đặt rất thấp, % utilization có thể cao dù quota chưa dùng hết.

Không phải mọi cluster luôn có CPU limits: một số tổ chức cho phép bỏ quota để tránh throttling của latency-sensitive workloads, nhưng điều này phải được cân nhắc với fairness/multi-tenancy và governance, không áp dụng như công thức chung.

## 6. HotSpot và container awareness

HotSpot JDK 17/21 hiện đại trên Linux thường bật `UseContainerSupport` mặc định. JVM có thể đọc giới hạn cgroup để suy CPU count, memory budget và chọn GC/heap ergonomics. Cùng JDK version nhưng khác update/vendor và cgroup setup vẫn có thể nhận diện khác.

Kiểm tra:

~~~bash
java -version
java -XshowSettings:system -version
java -Xlog:os+container=trace -version
~~~

- `-XshowSettings:system`: xem system/container information JVM nhận diện; availability phụ thuộc Linux/JDK.
- `-Xlog:os+container=trace`: xem HotSpot đọc cgroup constraints như thế nào.
- `java -version`: xác định vendor, build, version của binary.
- Kết quả ở đây **không có stdout cố định**; diagnostic logs thay đổi theo runtime.

Flag điều khiển:

~~~bash
java -XX:+UseContainerSupport -version
java -XX:-UseContainerSupport -version
~~~

**Tắt UseContainerSupport không vô hiệu hóa cgroup memory/CPU limits.** Kernel vẫn enforce. JVM có thể dùng host resources làm cơ sở lựa chọn ergonomics khi tắt, khiến memory budget không phù hợp và dễ OOMKilled. Không tắt chỉ để “JVM thấy thêm CPU”.

### Vì sao cần đo lại khi nâng cấp JDK?

- HotSpot cgroup v1/v2 detection có thay đổi và backports.
- CPU quota/weight/affinity heuristics có thể thay đổi.
- JVM vendor builds và GC defaults khác nhau.
- Container orchestrator/cgroup ancestors có thể thay đổi mount layout.
- Version JDK mới có các tính năng và giới hạn tài nguyên khác.

Một phép đo trên HotSpot 17.0.x không phải guarantee cho tất cả Java 17, 21, 25 hoặc 26, nhất là CPU count từ cgroup shares/weights.

## 7. availableProcessors và ActiveProcessorCount

Java API:

~~~java
Runtime.getRuntime().availableProcessors()
~~~

trả về **số nguyên** do VM coi là processors available. Nó không trực tiếp báo fractional quota như 1,5; cũng không phải số CPU threads/GC workers cố định cho toàn process.

Flag HotSpot:

~~~bash
java -XX:ActiveProcessorCount=2 -version
~~~

dùng để override số processors VM sử dụng ở nhiều ergonomic computations, có thể ảnh hưởng:
- GC parallel/concurrent worker sizing.
- Compiler threads, runtime worker policies tùy version.
- Executor/library defaults đọc `availableProcessors()`.
- Framework thread pools tự suy CPU count.

**Nó không thay đổi `cpu.max`, không tạo CPU quota mới, không bind process vào hai OS cores và không giới hạn mọi native thread pool.** Khi workload CPU-bound và quota chỉ 1,5 CPU, tăng ActiveProcessorCount lên 8 có thể làm oversubscription nặng hơn.

Một số HotSpot policies làm tròn quota/cpuset để chọn integer processor count; không dự đoán giá trị trên mọi JDK mà không quan sát output thực.

## 8. Max heap so với memory limit

Hai cách thường dùng để cấu hình HotSpot maximum Java heap:

~~~bash
java -Xmx512m -jar service.jar
java -XX:MaxRAMPercentage=55 -jar service.jar
~~~

Đây là các lệnh minh họa cần JAR hợp lệ.

**`-Xmx512m`** đặt maximum Java heap 512 MiB trong những cấu hình hỗ trợ.  
**`MaxRAMPercentage=55`** dùng khoảng 55% memory VM nhận diện theo ergonomic policy làm cơ sở cho maximum heap. Với memory budget 1 GiB, tính thô 55% × 1024 = 563,2 MiB; giá trị cuối có thể được căn chỉnh/điều chỉnh bởi VM.

Nếu cả hai được đặt:

~~~bash
java -Xmx512m -XX:MaxRAMPercentage=70 -jar service.jar
~~~

Trên HotSpot, **explicit Xmx thường thắng khi quyết định maximum heap**. Không giả định JVM nhân hai con số hoặc tự lấy min. Kiểm tra `MaxHeapSize` hiệu dụng.

### Initial heap và các tỷ lệ RAM

- `-Xms`: initial/minimum heap configuration theo HotSpot.
- `-Xmx`: maximum Java heap.
- `InitialRAMPercentage`: ergonomic initial heap sizing khi không có explicit override.
- `MaxRAMPercentage`: ergonomic maximum heap sizing.
- `MinRAMPercentage`: **không phải “minimum heap percentage” theo tên trực giác**; trong một số HotSpot policies nó liên quan maximum sizing ở small-memory environments.

Không đặt `-Xms=-Xmx` chỉ vì thấy trong một tuning guide. Nó có thể giảm heap resizing nhưng tăng memory commit/touch và startup cost, nhất là khi bật `AlwaysPreTouch`.

## 9. Những khoản bộ nhớ nằm ngoài Java heap

| Khoản | Chức năng | Chỉ số/công cụ |
| --- | --- | --- |
| Java heap | Java objects và arrays | `-Xmx`, GC logs, heap MXBean |
| Metaspace | Class metadata | `-XX:MaxMetaspaceSize`, NMT |
| Compressed class space | Metadata phục vụ compressed class pointers | VM flags/NMT; reserved khác committed |
| Code cache | JIT-compiled native code | `jcmd PID Compiler.codecache` |
| Thread stacks | Native stacks cho platform threads | Thread count, `-Xss`, NMT |
| Direct buffers | Off-heap buffers theo JDK/framework paths | BufferPoolMXBeans, native tools |
| GC internals | Marking, card tables, remembered sets | GC/NMT |
| JNI/foreign code | Native allocations/third-party allocators | Native profilers, OS RSS |
| Mapped pages/files | CDS, mmap, shared libraries | OS RSS/PSS, cgroup stats |
| Page cache/kernel allocations | File cache và kernel-charged memory | `memory.current`/`memory.stat` |

Một JVM có heap used 300 MiB nhưng cgroup memory.current 800 MiB **không sai**. Phần còn lại có thể là non-heap, native buffers, cache và các charges khác. Cần tránh tính trùng shared/map pages khi cộng OS và VM metrics.

### Reserved, committed, used, resident

- **Reserved**: địa chỉ ảo giữ chỗ, không đồng nghĩa RAM vật lý.
- **Committed**: memory backing đã cam kết theo VM/OS semantics, chưa chắc bằng resident/RSS.
- **Used heap**: mức sử dụng bên trong heap theo GC/Java metrics.
- **RSS/PSS**: memory residency/accounting của OS.
- **Cgroup memory.current**: charged usage trong cgroup, không chỉ một process.

Không cộng trực tiếp “NMT reserved + process RSS + memory.current”: các con số **khác định nghĩa và có thể đếm trùng**.

## 10. Khoảng dự phòng cho toàn tiến trình: mô hình ngân sách

Đặt:

~~~text
M = container/cgroup memory hard limit
H = Java heap peak / budget
N = native and managed non-heap peak budget
F = additional cgroup-charged cache/overhead not already counted
S = safety headroom for spikes, uncertainty, fragmentation

Check: H + N + F + S <= M
~~~

Đây là **mô hình lập kế hoạch**, không phải đẳng thức kế toán tuyệt đối của kernel. Những categories phải được định nghĩa sao cho không đếm trùng pages. Cần đo thực tế bằng OS/cgroup metrics vì NMT thiếu native allocations từ một số thư viện ngoài JVM.

### Ví dụ 1 GiB (hoàn toàn giả định)

| Mục | Ngân sách |
| --- | ---: |
| Heap maximum | 512 MiB |
| Native/non-heap peak | 320 MiB |
| Cache/overhead ngoài các mục trên | 96 MiB |
| Safety margin | 96 MiB |
| **Tổng** | **1024 MiB** |

Một candidate:

~~~bash
java -Xms128m -Xmx512m -jar service.jar
~~~

Các số 320 MiB, 96 MiB và 96 MiB **không phải mức mặc định của HotSpot**. Một ứng dụng Spring với nhiều class loaders, 500 platform threads hoặc direct buffers lớn có thể cần native memory khác xa ví dụ. Không nói “96 MiB headroom là an toàn”; còn phải đo peak/variance và failure-path memory.

### Có nên dùng “Xmx = 75% limit”?

Không có tỷ lệ phổ quát. Với giới hạn 256 MiB, chừa 25% = 64 MiB có thể không đủ cho JVM/native framework floor. Với giới hạn 8 GiB, workload thuần heap ít native có thể dùng tỷ lệ khác. Tính budget theo **absolute native floor + growth with traffic + safety margin**, rồi test.

**Chừa headroom không phải cứ giảm Xmx thật thấp:** heap quá nhỏ có thể làm GC liên tục, tăng CPU quota pressure và kéo p99. Sizing là một trade-off đo được giữa heap capacity, GC performance và native headroom.

## 11. Java 17+ ví dụ có stdout hoàn toàn xác định

Tạo file **`ContainerBudgetMath.java`**:

~~~java
import java.util.Locale;

public class ContainerBudgetMath {
    public static void main(String[] args) {
        long limitMiB = 1024L;
        long heapMiB = 512L;
        long nativeMiB = 320L;
        long cacheMiB = 96L;

        long accounted = heapMiB + nativeMiB + cacheMiB;
        long headroomMiB = limitMiB - accounted;
        double cpuBudget = 150_000.0 / 100_000.0;

        System.out.println("limitMiB=" + limitMiB);
        System.out.println("heapMiB=" + heapMiB);
        System.out.println("nonHeapPeakMiB=" + nativeMiB);
        System.out.println("cacheMiB=" + cacheMiB);
        System.out.println("headroomMiB=" + headroomMiB);
        System.out.println("fitsBudget=" + (headroomMiB >= 96));
        System.out.printf(Locale.ROOT, "cpuQuota=%.1f%n", cpuBudget);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 ContainerBudgetMath.java
java ContainerBudgetMath
~~~

**Stdout chính xác:**

~~~text
limitMiB=1024
heapMiB=512
nonHeapPeakMiB=320
cacheMiB=96
headroomMiB=96
fitsBudget=true
cpuQuota=1.5
~~~

Lý do:
- 1024 − (512 + 320 + 96) = **96 MiB**.
- 150000 ÷ 100000 = **1,5 CPU-equivalent**.
- `Locale.ROOT` giữ decimal separator là dấu chấm.
- Đây là **phép toán minh họa**, không tự đọc Docker/Kubernetes và không làm thay đổi cgroup limits.

Một phép tính headroom dương trong mô hình **không bảo đảm** container thực không OOMKilled; dữ liệu đầu vào về native/peak/mappings phải được kiểm chứng.

## 12. Java 17+ ví dụ truy vấn JVM thực: output thay đổi theo máy

Tạo file **`ContainerRuntimeProbe.java`**:

~~~java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.MemoryMXBean;

public class ContainerRuntimeProbe {
    public static void main(String[] args) {
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        long mib = 1024L * 1024L;

        System.out.println("availableProcessors="
                + runtime.availableProcessors());
        System.out.println("runtimeMaxHeapMiB="
                + runtime.maxMemory() / mib);
        System.out.println("usedHeapMiB=" + heap.getUsed() / mib);
        System.out.println("committedHeapMiB="
                + heap.getCommitted() / mib);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 ContainerRuntimeProbe.java
java -Xmx512m ContainerRuntimeProbe
java -XX:MaxRAMPercentage=55 ContainerRuntimeProbe
java -XX:ActiveProcessorCount=2 ContainerRuntimeProbe
~~~

**Không có stdout số chính xác dùng chung cho các lần chạy này**: JDK, CPU quota, heap configuration, GC, class loading và used/committed memory khác nhau.

Điểm quan trọng:
- `availableProcessors()` không phải total OS threads, quota decimal hay physical core count.
- `Runtime.maxMemory()` phản ánh Java heap maximum, không phải container hard limit.
- `heap.getUsed()` phản ánh heap usage tại một snapshot, không phải RSS.
- `heap.getCommitted()` phản ánh committed heap memory, không phải toàn bộ process committed.
- Khi thử `-XX:ActiveProcessorCount=2` trên HotSpot hỗ trợ, có thể thấy 2 CPUs theo JVM's view, nhưng kernel quota không đổi.

## 13. Đọc cgroup v2 trên Linux

Nếu cgroup v2 files hiện diện ở layout thông thường:

~~~bash
cat /sys/fs/cgroup/memory.max
cat /sys/fs/cgroup/memory.current
cat /sys/fs/cgroup/memory.events
cat /sys/fs/cgroup/memory.stat
cat /sys/fs/cgroup/cpu.max
cat /sys/fs/cgroup/cpu.stat
cat /sys/fs/cgroup/cpuset.cpus.effective
~~~

**Đây là dữ liệu giả định để học cách đọc, không phải log máy thật:**

~~~text
memory.max=1073741824
memory.current=629145600
cpu.max=150000 100000
cpu.stat nr_throttled=120
memory.events oom_kill=1
~~~

Diễn giải:
- Memory limit 1.073.741.824 bytes = **1024 MiB**.
- Charged memory 629.145.600 bytes = **600 MiB**.
- CPU quota tương đương 1,5 CPU.
- `nr_throttled=120` là giá trị tích lũy, cần delta cùng measurement interval.
- `oom_kill=1` cho biết metric ghi nhận kill event theo scope/semantics cgroup; cần xem timestamp/Pod/container lifecycle để quy nguyên nhân.

Nếu `memory.max` là `max`, cấp cgroup đó không có hard memory.max limit; vẫn có thể có ancestor hard limit hoặc host memory pressure. Không suy “memory vô hạn”.

### memory.high vs memory.max

Trong cgroup v2:
- `memory.high` là soft/throttling-style boundary tạo áp lực reclaim và giới hạn allocation behavior khi vượt; không đơn giản là hard kill giống memory.max.
- `memory.max` là hard upper bound; khi reclaim thất bại có thể kích hoạt cgroup OOM mechanism.
- `memory.events` có thể có `high`, `max`, `oom`, `oom_kill`... tùy kernel configuration.

Không thấy `memory.high` tăng không có nghĩa app chắc chắn không bị OOM; cần đọc relevant events/limits và memory.current.

## 14. Cgroup v1 và giới hạn của công cụ đọc

Các đường v1 thường thấy:

~~~bash
cat /sys/fs/cgroup/memory/memory.limit_in_bytes
cat /sys/fs/cgroup/memory/memory.usage_in_bytes
cat /sys/fs/cgroup/cpu/cpu.cfs_quota_us
cat /sys/fs/cgroup/cpu/cpu.cfs_period_us
cat /sys/fs/cgroup/cpu/cpu.stat
~~~

Không hứa tất cả paths tồn tại trong mọi Docker/Kubernetes distribution; tùy mount points/namespaces. Một limit số cực lớn ở v1 có thể biểu thị **unlimited** theo implementation, không phải memory budget tính theo con số đó.

Không viết một tool Java dựa vào đường dẫn cgroup v1/v2 tuyệt đối rồi gọi là portable JVM API; trên Windows containers những controllers này không áp dụng.

## 15. JDK flags kiểm tra container và heap cuối

Trên HotSpot:

~~~bash
java -XX:+PrintFlagsFinal -version
java -Xmx512m -XX:+PrintFlagsFinal -version
java -XX:MaxRAMPercentage=55 -XX:+PrintFlagsFinal -version
~~~

Tìm `MaxHeapSize`, `InitialHeapSize`, `MaxRAMPercentage`, `ActiveProcessorCount`, `UseContainerSupport`, `UseG1GC` hoặc GC flags liên quan.

Khi ứng dụng đang chạy và có PID thực, trên JDK có `jcmd` phù hợp:

~~~bash
jcmd PID VM.command_line
jcmd PID VM.flags
jcmd PID VM.info
jcmd PID GC.heap_info
~~~

`PID` là placeholder, phải thay bằng process ID. `VM.info` có thể hiển thị cgroup information trên những bản HotSpot hỗ trợ. Các thông tin được lấy từ **chính runtime process**, có giá trị hơn việc chỉ chạy một Java executable khác trên laptop rồi suy ra cấu hình production.

### Không gộp intents và outcomes

Một số flags có thể bị override theo precedence hoặc environment injection. Kiểm tra:
1. **Intent:** flags được khai báo trong manifest/Docker ENTRYPOINT/JAVA_TOOL_OPTIONS.
2. **Parser acceptance:** JVM khởi động thành công, không bỏ qua option.
3. **Effective configuration:** PrintFlagsFinal, VM.flags, Java MXBeans.
4. **Actual runtime behavior:** cgroup, GC logs, NMT, memory.current, latency.

`-Xmx512m` được parser nhận không có nghĩa JVM đang dùng chính xác 512 MiB physical memory; `ActiveProcessorCount=2` không nói cgroup đã cấp 2 CPUs.

## 16. Native Memory Tracking (NMT)

NMT dùng để thấy một phần JVM-native memory theo categories như Class, Thread, Code, GC, Compiler, Internal. Bật từ lúc startup trên HotSpot:

~~~bash
java -XX:NativeMemoryTracking=summary -jar service.jar
~~~

Sau đó khi có PID:

~~~bash
jcmd PID VM.native_memory summary
jcmd PID VM.native_memory baseline
jcmd PID VM.native_memory summary.diff
~~~

**Những giới hạn quan trọng:**
- NMT **không đo mọi native allocation của bên thứ ba/JNI libraries**, nên NMT total không bằng process RSS.
- `reserved` là virtual address space reservation; không phải physical RAM.
- `committed` khác RSS/page residency và cgroup charging.
- `summary` có overhead; `detail` còn nặng hơn. Không bật trên production mà chưa đánh giá overhead.
- `baseline` và `summary.diff` hữu ích để tìm categories tăng trong warmup/load, không tự chứng minh mọi memory leak.
- NMT không thay OS/cgroup metrics vì file cache, mappings và native allocator behavior còn nằm ngoài scope thích hợp.

Nếu thread category phình, xem thread counts/platform thread stacks; nếu class category phình, xem classloader churn; nếu code category tăng, xem JIT/code cache. Không chỉ cộng số `reserved` rồi so với memory.max.

## 17. OOME Java khác container OOMKilled

### Java heap OutOfMemoryError

Ví dụ exception:

~~~text
java.lang.OutOfMemoryError: Java heap space
~~~

JVM không thể cấp phát thêm object trên Java heap theo điều kiện runtime, dù kernel/container còn memory. Thường cần:
- GC logs, heap live set, retained objects.
- `MaxHeapSize` thực, allocation rate, caches.
- Heap dump khi được cho phép và có đủ filesystem space.

Một Java OOME **không bắt buộc giết ngay process** theo mọi error-handling strategy, mặc dù production thường coi đây là sự cố nghiêm trọng.

### Kernel cgroup OOM kill

Nếu cgroup hard memory boundary bị vượt và reclaim không giải quyết được, kernel có thể kill process. Dấu hiệu:
- Kubernetes `OOMKilled` trong container last termination state.
- `memory.events oom_kill`/container runtime events.
- Có thể thấy exit code **137** theo quy ước 128+SIGKILL, nhưng **137 không tự chứng minh OOM**, bởi SIGKILL còn nhiều nguyên nhân.
- Có thể **không có Java stack trace hoặc heap dump** vì kernel kill không đi qua Java exception handling.

Nguyên nhân điển hình:
- Xmx quá sát container memory.max.
- Direct buffers/JNI/native allocations, thread stacks, metaspace.
- Memory bursts trong JVM/class initialization/GC.
- File-cache/mmap/kernel memory charge trong cgroup.
- Sidecar/process khác cùng cgroup budget hoặc ancestor pressure.

**Không chữa container OOMKilled bằng cách tăng -Xmx mà giữ limit**; thường làm headroom hẹp hơn.

### Các errors khác

~~~text
java.lang.OutOfMemoryError: Metaspace
java.lang.OutOfMemoryError: unable to create native thread
java.lang.OutOfMemoryError: Cannot reserve ... direct buffer memory
~~~

Exact message thay đổi theo bản JDK. Metaspace OOME liên quan metadata/limit, native thread error còn có thể do OS pids/resource limits. Direct buffer memory behavior khác tổng native/JNI memory, không thể gói tất cả vào `MaxDirectMemorySize`.

| Triệu chứng | Kiểm tra ban đầu |
| --- | --- |
| Heap-space OOME | Heap max/live-set, GC logs, heap dump |
| Metaspace OOME | Classloader counts, MaxMetaspaceSize, NMT |
| Native thread failure | Thread count, pids.max, stack/native memory |
| Direct buffer OOME | Direct buffer pool metrics, allocation lifecycle |
| OOMKilled | Pod status/events, cgroup memory events/current/peak |
| CPU-throttled p99 | cpu.stat deltas, CPU quota, CPU profile, GC/JIT load |
| Pod Evicted | Node pressure/events/QoS, khác OOMKilled |

## 18. Kubernetes vận hành: requests, limits và điều tra

Các lệnh minh họa khi có quyền đọc cluster:

~~~bash
kubectl get pod my-pod -n my-namespace -o wide
kubectl describe pod my-pod -n my-namespace
kubectl top pod my-pod -n my-namespace --containers
kubectl get pod my-pod -n my-namespace -o yaml
~~~

Thay `my-pod`/`my-namespace` bằng tên thực. `kubectl top` cần Metrics Server/metrics pipeline; không có output không có nghĩa CPU/memory usage bằng 0.

Kiểm tra trong Pod:
1. Requests/limits của **đúng app container**.
2. JVM args của đúng container process, có bị inject qua JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS không.
3. Restart count, last terminated reason, exit code, events.
4. CPU throttling/usage và RSS/memory working set từ monitoring.
5. Sidecars, init containers, node pressures và cgroup ancestor constraints.
6. JDK vendor/build, cgroup v1/v2, JVM container awareness logs.

Nếu Pod gồm nhiều containers, request/limit cho một app container không tự động bằng tổng Pod resources. Nếu **nhiều processes chạy bên trong cùng một container**, chúng có thể cùng tiêu thụ một memory.max budget. Scope metrics phải được xác định đúng.

## 19. CPU throttling và latency: cách điều tra đúng

Lấy dữ liệu theo cùng timeline:
- Δ `cpu.stat nr_throttled`, Δ `throttled_usec`, CPU utilization/quota.
- Application request throughput và p50/p95/p99.
- GC pause/concurrent worker CPU, JIT compile bursts.
- Runnable Java threads/CPU-bound worker pools.
- Node contention, CPU affinity/cpuset, networking/downstream latency.

Một thí nghiệm A/B có kiểm soát:
1. Dùng **cùng JDK image, JVM flags, application build, input distribution**.
2. Giữ memory limit/requests ổn định, thay CPU limit từ A sang B theo cluster policy.
3. Cùng offered request rate/concurrency, warmup và sampling windows.
4. Đo CPU throttling delta và p99; không chỉ nhìn % CPU tổng.
5. Quan sát GC/JIT behavior và application CPU profile.
6. Báo chi phí tài nguyên so với SLO; chỉ giữ thay đổi có bằng chứng.

Không kết luận mọi p99 spike đều do quota chỉ vì `nr_throttled` tăng. CPU load, GC, lock contention, database latency và network cũng có thể tạo tail.

### GC/JIT cũng dùng CPU quota

Compilation threads và GC workers cũng tiêu thụ CPU bandwidth của cgroup. Khi app threads đã saturation, GC/JIT có thể bị cạnh tranh quota, làm warmup và một số collection phases kéo dài. Trước khi chỉnh `ParallelGCThreads`, `CICompilerCount` hoặc `ActiveProcessorCount`, hãy kiểm tra JVM ergonomics và real load metrics.

## 20. Ví dụ ngân sách 2 GiB: model, không phải khuyến nghị

Giả định container memory hard limit **2 GiB = 2048 MiB**. Một candidate:

~~~bash
java -Xms256m -Xmx1024m -XX:+UseG1GC -jar service.jar
~~~

Chọn numbers từ đo đạc giả định:
- Heap max: 1024 MiB.
- Native/metaspace/code cache/threads/direct peak: 430 MiB.
- Cache/other cgroup charged budget không đếm trùng: 220 MiB.
- Safety reserve: 250 MiB.

Tổng = 1024 + 430 + 220 + 250 = **1924 MiB**. Về mô hình còn **124 MiB** ngoài safety reserve. Không lấy các con số 430/220/250 áp cho mọi ứng dụng.

Nếu thay bằng:

~~~bash
java -XX:MaxRAMPercentage=50 -jar service.jar
~~~

Khi HotSpot thấy 2Gi limit và không có explicit Xmx, max heap có thể xấp xỉ 1Gi (có rounding/VM constraints). Khi deploy cùng image với memory limit chỉ 512Mi, 50% để lại khoảng 256Mi cho native, có thể **không đủ** cho framework/native floor. Percent-based config cần test nhiều resource profiles.

## 21. Memory requests và memory limits khác nhau

Kubernetes memory request dùng để schedule capacity/QoS; memory limit là hard boundary liên quan OOM kill ở cgroup. **Memory request không bắt buộc JVM giới hạn heap theo request**; trên Linux/HotSpot cgroup awareness, JVM thường nhận hard memory limit ở controller có hiệu lực.

Một Pod có request 512Mi và limit 1Gi:
- Scheduler ước lượng/đặt chỗ 512Mi theo policy.
- Container có thể dùng hơn request tới gần limit.
- Nếu nhiều workloads trên node dùng hơn requests cùng lúc, node pressure/eviction risk có thể tăng.
- Cgroup memory.max enforcement vẫn có thể OOMKill ở limit dù host còn RAM free nếu container riêng vượt limit.

QoS classes như Guaranteed/Burstable/BestEffort phụ thuộc requests/limits các containers liên quan; không tự suy từ một field của một container.

## 22. Tài nguyên khác cũng có hard limits

Ngoài RAM/CPU:
- `pids.max`: giới hạn số tasks/processes/threads của cgroup.
- File descriptor limits `ulimit -n`: số sockets/files có thể mở.
- Ephemeral storage/disk quota: heap dumps/GC logs/tmp files.
- Network/DB pool/quota: không tăng cùng số virtual threads.
- Swap policies và memory pressure.
- OS thread stack reserves và native library allocations.

Nếu thấy `OutOfMemoryError: unable to create native thread`, phải kiểm tra **pids/max threads/natively reserved memory**, không chỉ `-Xmx`. Java virtual threads trong JDK 21+ có thể giảm nhu cầu một OS thread trên mỗi logical request, nhưng request buffers/connection slots vẫn tiêu thụ tài nguyên thực.

## 23. Checklist load test có headroom

**Trước test:**
1. Cố định JDK vendor/build, image digest, JVM flags, CPU/memory requests/limits.
2. Đo cgroup version, quota/period, memory.max và ancestor constraints.
3. Chọn candidate Xmx/MaxRAMPercentage với native headroom dự kiến.
4. Kiểm tra activeProcessorCount/availableProcessors, pool sizing và CPU-bound concurrency.
5. Bật GC logs, JFR hoặc NMT nếu overhead chấp nhận được.

**Trong test:**
6. Chạy startup, warmup/JIT ổn định tương đối, steady load và peak burst.
7. Đo heap used/committed, native categories và cgroup memory.current/peak/events.
8. Đo cpu.stat deltas, throttling và app p95/p99.
9. Có failure-path test khi DB/network chậm, retries/backlog tăng.
10. Theo dõi thread counts, FD/pids, direct buffers và class loading.
11. Đặt alerts trước hard limits; không xem “không OOM trong 5 phút” là bằng chứng vĩnh viễn.

**Sau test:**
12. So peak usage/variance với hard budgets và reserve.
13. Đọc OOMKilled, restart, evictions, GC overhead và CPU throttling correlations.
14. Thay đúng một nhóm config nếu cần rồi chạy lại baseline/test.
15. Ghi số liệu và rollback plan khi nâng JDK/GC/driver/traffic.

## 24. Các lỗi thường gặp

| Ngộ nhận | Sự thật |
| --- | --- |
| “Container 1Gi, Xmx1g an toàn.” | Không còn chỗ hợp lý cho metaspace, code cache, stacks, native/mappings. |
| “Xmx bằng toàn bộ process RSS.” | Heap maximum khác physical resident memory. |
| “MaxRAMPercentage là phần trăm của Kubernetes request.” | JVM sizing theo memory VM nhận diện, thường gắn limit; kiểm tra effective heap. |
| “availableProcessors() có thể trả 1,5.” | API trả integer, không trực tiếp biểu diễn fractional CPU quota. |
| “ActiveProcessorCount=2 tăng kernel quota lên 2.” | Chỉ override processor view/ergonomics của JVM. |
| “cpu.weight=200 nghĩa hard cap 2 CPUs.” | Weight là relative share; quota ở cpu.max. |
| “CPU request=500m là được giữ riêng 0,5 physical core.” | Request dùng scheduling/share, không độc quyền một nửa core. |
| “Không đặt cpu.max thì không có bất kỳ CPU constraint nào.” | Còn ancestor, cpuset, host competition. |
| “UseContainerSupport=false sẽ thoát cgroup limits.” | OS constraints không thay đổi. |
| “NMT reserved 4Gi tức RSS 4Gi.” | Reserved virtual address space khác committed/resident. |
| “NMT theo dõi tất cả JNI malloc.” | Có native allocations bên ngoài scope NMT. |
| “Heap used nhỏ thì container không thể OOMKilled.” | Native, direct, file cache và other cgroup charge vẫn có thể gây OOM. |
| “Exit code 137 luôn là OOMKilled.” | Có thể là SIGKILL từ nguyên nhân khác; cần status/events. |
| “memory.current chỉ gồm Java heap.” | Chứa nhiều loại charged memory của cgroup. |
| “memory.high và memory.max đều gây hard kill như nhau.” | High thường gây reclaim/throttling; max là hard limit. |
| “CPU throttling cao là do số Java threads quá ít.” | Có thể là CPU quota contention; thêm threads đôi khi tệ hơn. |
| “JVM max heap giữ 80% RAM là quy tắc chung.” | Native floor/heap profile thay đổi theo service; đo mới quyết định. |
| “Càng giảm heap càng an toàn.” | Heap quá nhỏ có thể tăng GC/CPU pressure và giảm SLO. |
| “Kubernetes memory request là hard RAM cap.” | Hard cap gắn với memory limit/cgroup policy. |
| “Đổi JDK không ảnh hưởng sizing heuristics.” | Container awareness/GC defaults có thể đổi giữa releases. |
| “Một test nhẹ là đủ kết luận production an toàn.” | Cần burst/failure cases/long-run measurements. |

## 25. Câu hỏi tự kiểm tra

**1. Hard memory limit 1Gi, -Xmx1g có an toàn?** Không thể coi an toàn vì còn nhiều khoản ngoài Java heap.

**2. Java heap maximum khác `memory.current` thế nào?** Maximum heap là JVM flag/property; memory.current là toàn charged cgroup memory.

**3. `cpu.max=150000 100000` nghĩa gì?** Quota tương đương 1,5 CPU time mỗi period theo cgroup.

**4. `cpu.max=max 100000` có vô hạn CPU không?** Không, chỉ không có quota cap cấp đó; host/cpuset/ancestor còn giới hạn.

**5. `cpu.weight` có dùng làm hard quota không?** Không, nó là relative scheduling weight.

**6. Vì sao `availableProcessors()` không thể in 1.5?** Kiểu trả về int; JVM chọn integer effective processor count.

**7. `-XX:ActiveProcessorCount=4` có thay cgroup cpu.max không?** Không; nó tác động VM ergonomics.

**8. `-XX:-UseContainerSupport` có tắt OOMKilled không?** Không; kernel vẫn enforce cgroup limits.

**9. Vì sao native headroom cần xét thread count?** Mỗi platform thread có native stack/reservations và runtime overhead.

**10. NMT committed có bằng RSS?** Không nhất thiết; scope/accounting/residency khác nhau.

**11. Heap used 300Mi mà memory.current 800Mi có sai?** Không; native, metaspace, code, buffers, caches và kernel charges.

**12. OOMKilled khác heap-space OOME?** OOMKilled do OS/cgroup kill, có thể không có Java exception; heap-space OOME là exception trong JVM.

**13. Exit 137 tự xác nhận OOMKilled?** Không. Cần events/status liên quan.

**14. Memory request 512Mi, limit 1Gi; JVM heap được chọn 512Mi theo request?** Không tự động; tùy JVM flags/container detection, thường gắn memory limit.

**15. MaxRAMPercentage=60 có thể giải thích chính xác heap size không?** Chỉ là ergonomic input; kiểm tra MaxHeapSize cuối và VM constraints.

**16. Khi cpu.stat throttling tăng, cần đo gì tiếp?** Delta counters theo khoảng thời gian, application latency, GC/JIT work, offered load và quota.

**17. Tăng CPU limit có chắc tăng throughput?** Không, nếu workload I/O-bound hoặc bottleneck khác.

**18. JVM có một Java heap 512Mi nghĩa memory RSS tối đa 512Mi?** Không.

**19. Bật NMT làm mọi native allocation lộ ra?** Không; NMT giới hạn ở phần nó theo dõi.

**20. Thế nào là sizing được chứng minh?** Có xác nhận limits, effective JVM values, peak metrics, headroom, SLO và test failure paths.

## 26. Sơ đồ ghi nhớ

~~~text
                    CONTAINER RESOURCE PLAN
                              |
                 +------------+-----------+
                 |                        |
              CPU QUOTA               MEMORY LIMIT
                 |                        |
         cpu.max / cpu.stat       memory.max / current
         weight vs cpuset         memory.events / stat
                 |                        |
           JVM CPU VIEW              JVM HEAP VIEW
         availableProcessors        Xmx / MaxRAMPercentage
         ActiveProcessorCount       MaxHeapSize
                 |                        |
         GC/JIT/app workers          TOTAL PROCESS BUDGET
                 |                 +----------------------+
          THROTTLING DELTAS         | Heap                 |
          + request latency         | Metaspace / code     |
                                    | Stacks / native      |
                                    | Direct / mapped      |
                                    | Cache / safety       |
                                    +----------------------+
                                             |
                                      OOME vs OOMKilled
                                             |
             -------------------------------------------------
             Load test + GC logs + NMT + OS/cgroup metrics
                                             |
                                   SLO AND HEADROOM VERIFIED
~~~

**Kết luận:** Container-aware Java sizing là bài toán **tài nguyên toàn tiến trình và giới hạn cgroup**, không chỉ việc chọn `-Xmx`. CPU quota là bandwidth theo period, khác số threads và CPU requests. Memory limit là hard cgroup budget, còn heap chỉ là một thành phần. Muốn tránh OOMKilled/throttling và giữ latency ổn định, hãy đo **cả JVM lẫn kernel/container metrics**, chừa safety headroom theo native peaks, kiểm tra đúng JDK build và lặp lại load tests sau mỗi thay đổi.

## Tài liệu tham khảo

- [Oracle Java 17 — The java Command, container support và resource flags](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).
- [Oracle Java 21 — The java Command, -XshowSettings:system, UseContainerSupport](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).
- [Oracle Java 26 — The java Command, JVM flags](https://docs.oracle.com/en/java/javase/26/docs/specs/man/java.html).
- [OpenJDK HotSpot — Linux cgroup CPU implementation](https://github.com/openjdk/jdk/blob/master/src/hotspot/os/linux/cgroupSubsystem_linux.cpp).
- [OpenJDK HotSpot — cgroup v2 subsystem](https://github.com/openjdk/jdk/blob/master/src/hotspot/os/linux/cgroupV2Subsystem_linux.cpp).
- [Linux Kernel — Control Group v2 specification](https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html).
- [Kubernetes — Resource Management for Pods and Containers](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/).
- [Kubernetes — Assign Memory Resources](https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource/).
- [Kubernetes — Assign CPU Resources](https://kubernetes.io/docs/tasks/configure-pod-container/assign-cpu-resource/).
- [Oracle Java 21 — jcmd diagnostic commands/NMT](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html).
- [Oracle Java 17 — ManagementFactory/MemoryMXBean](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ManagementFactory.html).
- [Red Hat Developer — OpenJDK 17 container awareness](https://developers.redhat.com/articles/2022/04/19/java-17-whats-new-openjdks-container-awareness).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-008">Heap còn chỗ nhưng tiến trình thiếu bộ nhớ: native memory và giới hạn hệ điều hành</a>

<details>
<summary>Xem chi tiết</summary>

# Heap còn chỗ nhưng tiến trình thiếu bộ nhớ: native memory và giới hạn hệ điều hành

**Một JVM vẫn có thể thiếu bộ nhớ hoặc bị hệ điều hành kết thúc dù Java heap còn nhiều chỗ trống.** Lý do là **Java heap chỉ là một trong nhiều vùng bộ nhớ** của tiến trình HotSpot. Cấp phát native, metaspace, thread stacks, JIT code cache, direct buffers, bộ nhớ của GC, thư viện JNI, memory mappings và các giới hạn của OS/container có thể gây lỗi độc lập với lượng heap used.

Tình huống điển hình:

- `-Xmx1g`, heap used mới khoảng 400 MiB nhưng container có limit 1,5 GiB và tổng native/JVM overhead vượt phần còn lại.
- JVM có `OutOfMemoryError: Metaspace` dù heap chưa đầy, vì class metadata ở một vùng quản lý khác.
- Tạo quá nhiều threads gây `unable to create native thread` khi process bị chặn bởi memory/PID limits.
- NIO direct buffers không còn đủ budget; exception có thông báo `Cannot reserve ... bytes of direct buffer memory` tùy JDK.
- Container bị **OOMKilled** bởi cgroup/kernel trước khi HotSpot có cơ hội ném `OutOfMemoryError: Java heap space`.

Bài học thuộc nhóm **cấu hình tài nguyên** và tập trung **HotSpot/JDK 17+**. Những cờ `-XX`, NMT và lệnh `jcmd` được nhắc đến là **implementation-specific**, không mặc nhiên áp dụng cho OpenJ9 hoặc GraalVM Native Image.

## 1. Đầu tiên: heap free không đồng nghĩa process memory free

Một sơ đồ ngân sách thực tế:

```text
Container / OS memory budget
┌──────────────────────────────────────────────────────────┐
│ JVM process                                              │
│ ┌───────────────────────┐  ┌───────────────────────────┐ │
│ │ Java heap             │  │ Non-heap / native         │ │
│ │ objects, arrays       │  │ Metaspace/class metadata │ │
│ │ used / committed      │  │ JIT code cache           │ │
│ │ max qua -Xmx          │  │ Java/native thread stacks│ │
│ │ GC-managed            │  │ direct ByteBuffer backing│ │
│ └───────────────────────┘  │ GC native structures      │ │
│                            │ JNI, native libs, mmap    │ │
│                            └───────────────────────────┘ │
│ JVM + OS còn có executable/shared-library mappings,       │
│ stacks, runtime metadata và những khoản accounting khác. │
└──────────────────────────────────────────────────────────┘
     ▲
     └─ cgroup/container accounting còn tùy memory model,
        có thể gồm cache/other charged pages, không chỉ heap.
```

**`-Xmx` giới hạn Java heap**, không giới hạn **RSS của JVM process** hay cgroup memory usage. Nếu heap có 300 MiB unused thì số unused đó **không tự chuyển thành** 300 MiB physical memory để một native `malloc` thành công, vì memory reservation/commit, address-space constraints, limits và accounting là những vấn đề khác nhau.

### Các khái niệm bắt buộc phân biệt

| Metric/khái niệm | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| **Heap used** | Bộ nhớ đang dùng trong Java heap theo VM management view | Không bao gồm phần lớn native memory |
| **Heap committed** | Dung lượng heap JVM đã commit để có thể dùng | Có thể cao hơn heap used |
| **Heap max** | Giới hạn heap, thường gắn `-Xmx` | Không phải giới hạn RSS |
| **Native reserved** | Không gian địa chỉ ảo được đặt trước cho vùng native/JVM | Không đồng nghĩa resident RAM |
| **Native committed** | Dung lượng JVM coi là đã commit trong phạm vi tracking | Không nhất thiết trùng RSS |
| **VmSize/VSZ** | Tổng vùng địa chỉ ảo được map/reserve theo OS | Có thể lớn hơn rất nhiều RAM cư trú |
| **RSS** | Các trang resident của process theo OS | Gồm heap và phần khác; có shared mappings |
| **PSS** | Proportional Set Size, chia phần shared mappings | Hữu ích hơn RSS trong vài bài toán nhiều processes |
| **cgroup memory.current** | Bộ nhớ đang được hạch toán cho cgroup v2 | Không phải riêng RSS của một JVM |
| **Pod/Container memory limit** | Ngưỡng quản lý tài nguyên được môi trường áp | Vi phạm có thể bị OOM kill |

**Không cộng máy móc** các chỉ số NMT, RSS, cgroup và BufferPoolMXBean như thể tất cả thuộc những tập hợp không giao nhau. Các metrics **có phần chồng nhau hoặc scope khác nhau**.

## 2. Triệu chứng “heap còn chỗ” phải được phát biểu chính xác

Một phát biểu đúng dạng:

> “Trong khoảng 14:10–14:20 UTC, trên process PID X, heap used khoảng 420 MiB và heap max khoảng 1024 MiB theo MemoryMXBean; nhưng container đã bị OOMKilled ở memory limit 1536 MiB. Đang điều tra non-heap/native memory hoặc memory charged bởi cgroup.”

Một phát biểu **không đủ chứng cứ**:

> “Heap mới dùng 40%, do đó chắc chắn không thể bị thiếu RAM; đây là lỗi GC.”

Cần ghi rõ **thời điểm**, vì heap metric được chụp sau GC còn RSS peak được ghi trước đó sẽ không cùng giai đoạn. Khi process đã chết, giá trị cuối trong dashboard có thể là **mẫu cũ**, không nhất thiết đo đúng thời điểm OOM.

### Vì sao chỉ nhìn heap used dễ sai?

- JVM có thể commit nhiều heap hơn used; OS tính resident pages theo việc các trang đã được chạm.
- Non-heap memory tăng độc lập.
- Native threads có stacks, JNI có allocations không phải Java objects.
- Container memory includes các trang/cache được hạch toán theo cgroup.
- Cấp phát thất bại còn phụ thuộc **address-space limit**, virtual memory mappings, PID limits và OS policy.
- Kernel có thể kill process khi cgroup chạm ngưỡng mà HotSpot không tự nhận được một Java exception.

**Kết luận gốc:** cần cùng lúc xem **Java heap, native/OS memory và resource limits**, rồi mới quy loại lỗi.

## 3. Các nguồn native memory quan trọng của HotSpot

| Nguồn | Vì sao chiếm bộ nhớ? | Dấu hiệu/đầu mối |
| --- | --- | --- |
| **Metaspace** | Metadata cho classes, methods và class loaders | `OutOfMemoryError: Metaspace`, class count/loader churn |
| **Compressed class space** | Vùng metadata liên quan klass pointers được nén | `OutOfMemoryError: Compressed class space` |
| **JIT code cache** | Machine code, adapters/stubs, compiled nmethods | `Compiler.codecache`, code cache warnings |
| **Thread stacks** | Frames cho Java/native call stacks của mỗi thread | Thread count, `-Xss`, OS thread/PID limits |
| **Direct ByteBuffers** | Native/off-heap backing; Java wrapper vẫn ở heap | BufferPoolMXBean, direct-memory OOME |
| **Mapped buffers** | File-backed mapped address ranges | Mapped BufferPool, OS memory maps, page cache |
| **GC metadata** | Card tables, remembered sets, marking/relocation structures | NMT GC category, GC/version-specific sizing |
| **VM runtime structures** | Symbols, compiler arenas, internal caches, tracking structures | NMT categories |
| **JNI/native libraries** | `malloc`, native malloc arenas, third-party allocators | OS profiler/maps; NMT không nhất thiết thấy |
| **TLS/network/compression** | Library and protocol buffers, IO rings, native caches | Resource pools, OS metrics, heap/direct accounting |

**Một nguồn native memory không nhất thiết là leak.** Thread pool có kích thước lớn, code cache đã warm-up, mapping lớn hoặc cache native có thể là allocation chủ đích. Cần so với lifecycle và giới hạn thay vì chỉ thấy tổng số tăng.

## 4. Metaspace: khi class metadata tăng dù Java heap còn chỗ

**Tín hiệu:** loaded-class count và Metaspace used/committed tăng cùng class-loader churn, lỗi `OutOfMemoryError: Metaspace` hoặc `Compressed class space`. Đây là **metadata native** của HotSpot, không phải object payload được tính trong `-Xmx`.

**Chẩn đoán theo sự cố:**
1. Ghi class count và `jcmd PID VM.metaspace basic` trên đúng JVM nếu command được build hỗ trợ.
2. So các mốc trước/sau plugin loading, redeploy hoặc dynamic proxy/code generation; kiểm tra class unloading, class-loader reachability.
3. Đối chiếu `MaxMetaspaceSize`, `CompressedClassSpaceSize` và memory budget tổng thể; một cap được khai báo không có nghĩa đã commit toàn bộ.
4. Nếu class metadata tăng không giới hạn vì loaders bị giữ, **tăng cap chỉ kéo dài thời gian đến failure**.

Bài chuyên đề Metaspace/direct/code cache trong cùng chương giải thích sâu flags và các vùng; bài này tập trung xác định **Metaspace có thực sự gây native-memory incident hay không**.

## 5. Code cache: kiểm tra khi JIT bị giới hạn

**Tín hiệu phân biệt:** HotSpot báo code cache full, JIT compilation disabled/throttled, compiler log bất thường hoặc throughput giảm sau warmup. Code cache chứa machine code/stubs được JIT tạo, **ngoài Java heap**; `-XX:ReservedCodeCacheSize` là configuration limit cho vùng này trên HotSpot phù hợp.

Trên đúng PID (thay `PID` bằng số thật), dùng:

~~~bash
jcmd PID Compiler.codecache
~~~

Đọc `used/free`, segmented code cache và trạng thái compilation theo bản JDK, rồi correlate với memory pressure/cgroup accounting. **Không tăng code cache chỉ vì reserved virtual size lớn**: reserved không phải resident bytes; cần bằng chứng nó gần cap hoặc JIT đã ngừng hoạt động. Các bài về code cache và ergonomics trình bày cơ chế đầy đủ.

## 6. Native thread stacks và giới hạn số luồng

**Tín hiệu:** `java.lang.OutOfMemoryError: unable to create native thread`, lỗi OS `pthread_create`, thread-count tăng sát `pids.max`/`ulimit -u`, hoặc memory RSS tăng theo platform-thread count. Thread stack và runtime/OS bookkeeping không thuộc giới hạn Java heap.

Quy trình ngắn:
1. Đo platform-thread count, thread-pool queue/active workers, `/proc/PID/status` (Linux) hoặc metric tương đương.
2. Kiểm tra effective `-Xss`, stack reservations/commits trong NMT nếu đã bật, và OS/cgroup PID/memory limits.
3. Tìm thread leak, executor không shutdown, unbounded concurrency trước khi hạ `-Xss` để “chữa cháy”.
4. Cẩn thận: giảm stack có thể tăng `StackOverflowError`; reserved stack size không đồng nhất RSS thực; virtual threads có model khác platform threads.

Bài `-Xss` của chương dạy recursion/thread budget có bài thực hành riêng; ở đây cần phân biệt **hết native thread resources** với **hết Java heap**.

## 7. Direct ByteBuffer: tách dữ liệu native khỏi Java wrapper

`ByteBuffer.allocateDirect(n)` tạo Java wrapper nhưng backing storage thường được cấp ngoài ordinary Java heap trên HotSpot. Vì vậy heap dùng thấp mà NIO direct allocation hoặc container native memory tăng vẫn có thể là vấn đề nghiêm trọng.

**Những câu hỏi cần trả lời trong incident:**
- `BufferPoolMXBean` của pool `direct` có `getCount()`, `getTotalCapacity()` và `getMemoryUsed()` tăng theo connections/buffer-pool lifecycle không?
- Có `OutOfMemoryError` khi reserve direct buffer memory và `MaxDirectMemorySize` đang hiệu lực bao nhiêu?
- Nếu direct pool **không tăng** nhưng RSS tăng, có JNI/native allocator, Netty unmanaged arenas, mapped files, page cache hoặc C libraries không thuộc cùng accounting không?
- Có thiếu release/close ở framework/driver theo ownership contract không?

Không cộng trực tiếp `BufferPoolMXBean.memoryUsed` + NMT committed + RSS để suy “total memory”: các số có phạm vi khác nhau và có thể đếm trùng. **NMT không theo dõi hết native allocations của thư viện JNI/third-party**; cần phối hợp OS/cgroup metrics. Bài Metaspace/direct memory trước đó trình bày chi tiết giới hạn `MaxDirectMemorySize`; ở đây mục tiêu là **nhận diện đúng pool gây memory pressure**. Xem thêm tài liệu Oracle về Native Memory Tracking và troubleshooting native memory leaks.

## 8. Mapped files: VSZ rất lớn không có nghĩa RSS lớn

Memory-mapped files có thể reserve/map vùng địa chỉ ảo lớn. `MappedByteBuffer` và hệ thống page cache của OS có những cách accounting khác nhau với Java heap.

```text
Large mapped address range
         │
         ├──► VSZ / VmSize có thể tăng đáng kể
         │
         ├──► Resident pages được nạp khi truy cập
         │
         └──► Một phần file-backed pages có thể shared/cached
```

**Không suy “VSZ 100 GiB = process đã chiếm 100 GiB RAM”.** Trên hệ 64-bit, reserved virtual address-space có thể lớn. Nhưng mapping vẫn có thể ảnh hưởng address-space limits, page faults và cgroup/file cache accounting.

Nếu cgroup memory.current tăng và process RSS không tăng tương ứng, cần kiểm tra file cache, anonymous vs file-backed charges, shared mappings và **scope** metric. Không tự kết luận JVM có native leak.

## 9. JVM memory khác kernel/cgroup accounting thế nào?

Container memory limit không phải một `-Xmx` khác do Java runtime quản lý. Linux cgroup quản lý **memory charges của cgroup**, có thể gồm anonymous memory, file cache và các components khác theo kernel/cgroup version.

### Linux cgroup v2: các file thường dùng

```bash
cat /sys/fs/cgroup/memory.max
cat /sys/fs/cgroup/memory.current
cat /sys/fs/cgroup/memory.events
cat /sys/fs/cgroup/memory.stat
cat /sys/fs/cgroup/pids.max
cat /sys/fs/cgroup/pids.current
```

Những đường dẫn này là **ví dụ phổ biến khi process ở root của cgroup mount v2**. Trên Kubernetes/node có namespace/mount layout khác hoặc cgroup v1, file paths và semantics có thể khác. Đừng tuyên bố luôn có đúng `/sys/fs/cgroup/memory.max` nếu chưa xác minh cgroup version/path qua `/proc/self/cgroup` và mount information.

| File | Câu hỏi |
| --- | --- |
| `memory.max` | Hard memory limit là bao nhiêu, hay `max` (không đặt cgroup cap)? |
| `memory.current` | Memory đang charged cho cgroup bao nhiêu? |
| `memory.events` | Có events như `oom`/`oom_kill` không? |
| `memory.stat` | Anonymous/file-backed/other categories chiếm phần nào? |
| `pids.max` | Giới hạn số tasks/threads trong cgroup |
| `pids.current` | Số tasks/threads hiện tại, có sát cap không? |

**Không hiểu nhầm `memory.max=max` nghĩa host có RAM vô hạn.** Vẫn có host-wide memory pressure, OS limits, user resource limits và các process khác cùng host.

### cgroup v1 và container khác

Trên hệ v1, các tệp có thể là `memory.limit_in_bytes`, `memory.usage_in_bytes` và các metrics liên quan. Dữ liệu accounting không nhất thiết quy đổi 1:1 với cgroup v2. Khi báo cáo incident, ghi chính xác **v1/v2, container runtime và giới hạn đang hiệu lực**.

## 10. Kubernetes OOMKilled: JVM có thể không ghi OOME

Nếu memory cgroup vượt giới hạn và kernel chọn kill JVM, process có thể kết thúc **không có Java stack trace** vì tín hiệu SIGKILL không cho Java catch/finally/shutdown hooks xử lý như exception bình thường.

Một số lệnh Kubernetes khi có quyền:

```bash
kubectl describe pod POD_NAME -n NAMESPACE
kubectl get pod POD_NAME -n NAMESPACE -o yaml
kubectl top pod POD_NAME -n NAMESPACE
kubectl logs POD_NAME -n NAMESPACE --previous
```

`POD_NAME` và `NAMESPACE` là những **placeholder** phải thay bằng dữ liệu thực; đừng copy nguyên tên sang môi trường thật. `kubectl top` cần metrics server và có thể khác định nghĩa/mẫu thời gian so với process RSS.

### Phân loại sự kiện

- **Pod last state reason: `OOMKilled`:** tín hiệu đáng tin rằng container/process bị OOM kill trong môi trường đang xét; đối chiếu memory limit và cgroup events.
- **Exit code 137:** thường liên quan `SIGKILL` (=128+9), nhưng **không đủ một mình** kết luận OOM; có thể bị kill vì lý do khác.
- **Java `OutOfMemoryError` stack trace:** JVM đã tạo/thông báo lỗi Java; không nhất thiết kernel kill.
- **`hs_err_pid*.log`:** thường liên quan fatal VM error/crash; không phải mọi OOMKilled đều có `hs_err`.
- **Container restarted nhiều lần:** có thể do health checks, deployment, OOM, crash; xem pod status/events.

**Quan trọng:** trong môi trường có nhiều processes cùng cgroup, cgroup memory.current **không phải** RSS riêng JVM. Phải biết số processes/sidecars và phần memory charges thuộc phạm vi nào.

## 11. Khi `-Xmx` quá gần container limit: điều tra thay vì cộng trừ máy móc

Giả định container hard memory limit 1024 MiB và `-Xmx900m`. Khoảng chênh **124 MiB theo hạn mức danh nghĩa** không tự chứng minh còn 124 MiB RAM thực sự rảnh cho native allocations: heap committed/resident, metaspace, thread stacks, direct buffers, page cache và kernel/cgroup charges biến đổi theo workload.

Đối với một OOMKilled incident, đo **cùng time window**: heap used/committed/max, RSS/PSS, container memory.current/peak/events, NMT khi có và traffic/concurrency. Xác định vùng tăng trước khi hạ heap hoặc giới hạn threads/direct pools. Bài `-Xms/-Xmx` và bài container trước đó dạy cách chọn heap và headroom; ở đây cần chứng minh *nguồn nào thực sự vượt budget*.

## 12. Heap percentages và container awareness: kiểm tra hiệu lực

Trên HotSpot JDK 17+ theo build/OS hỗ trợ, `-XX:MaxRAMPercentage` và `-XX:InitialRAMPercentage` có thể đóng vai trò khi VM tự chọn heap. Nhưng explicit `-Xmx` có thể override chính sách maximum tự tính; `UseContainerSupport`/cgroup v1–v2 behavior phụ thuộc JDK update và platform.

**Trong một incident:** chụp `java -version`, command line **của process thật**, `jcmd PID VM.flags`, cgroup memory limits và `Runtime.maxMemory()` rồi mới giải thích vì sao maximum heap khác dự kiến. Không dùng `-XX:+IgnoreUnrecognizedVMOptions` để che flags không còn hiệu lực. Chi tiết cơ chế auto-sizing thuộc bài JVM ergonomics/container; ở đây chú trọng **đối chiếu desired config và effective budget**.

## 13. Native Memory Tracking (NMT): khả năng và giới hạn

**Native Memory Tracking (NMT)** là công cụ **HotSpot** giúp theo dõi nhiều allocations nội bộ của VM, phân loại theo subsystems như `Java Heap`, `Class`, `Thread`, `Code`, `GC`, `Compiler`, `Internal` và các categories khác tùy release.

**Giới hạn quan trọng từ Oracle JDK 17 documentation:** NMT **không bao phủ toàn bộ** native allocations của ứng dụng, thư viện JNI/third-party native code hoặc tất cả allocations của các JDK class libraries; thông tin CDS archives cũng có giới hạn. Vì vậy **“NMT total nhỏ hơn RSS” không chứng minh có leak**, cũng không tự nói toàn bộ chênh lệch thuộc thư viện X.

### Bật NMT từ startup

```bash
java -XX:NativeMemoryTracking=summary -Xms128m -Xmx512m -jar app.jar
```

Hoặc để có mức chi tiết hơn:

```bash
java -XX:NativeMemoryTracking=detail -Xms128m -Xmx512m -jar app.jar
```

NMT mặc định **off** trên HotSpot; `summary` có subsystem aggregates, `detail` cung cấp thêm allocation call-site/native-map information được VM theo dõi. **NMT phải được bật từ startup**; một JVM đang chạy với NMT off không thể được `jcmd` bật lại đầy đủ.

### Các lệnh `jcmd`

```bash
jcmd -l
jcmd PID VM.native_memory summary
jcmd PID VM.native_memory baseline
jcmd PID VM.native_memory summary.diff
```

Nếu đã bật `detail`, có thể dùng:

```bash
jcmd PID VM.native_memory detail
jcmd PID VM.native_memory detail.diff
```

`PID` là process ID thực. `baseline` và `summary.diff` chỉ có nghĩa khi **cùng process** đang chạy và NMT đang bật; không dùng baseline của PID A để “diff” một process PID B sau redeploy. `jcmd PID help` cho biết commands available trên JDK build đó.

### Đọc reserved/committed trong báo cáo NMT

Một report có thể có dạng **minh họa**:

```text
Total: reserved=..., committed=...
  Java Heap (reserved=..., committed=...)
  Class (reserved=..., committed=...)
  Thread (reserved=..., committed=...)
  Code (reserved=..., committed=...)
  GC (reserved=..., committed=...)
```

Chúng là **placeholder**, không phải NMT output đã đo. Tổng reserved có thể cực lớn vì địa chỉ ảo; committed là thống kê trong phạm vi NMT, **không bằng RSS** theo định nghĩa chung. Native memory footprint còn thay đổi theo GC, class loading, compiler, stacks và lifecycle.

### Chi phí của NMT

Tài liệu Oracle cảnh báo khoảng **5–10% performance overhead** khi bật NMT, tùy workload; không được biến khoảng này thành dự báo chính xác cho mọi app. Đối với production, cần quyết định thời gian/bật level theo chính sách và tính observer effect khi benchmark.

## 14. Không có NMT vẫn điều tra được

Nếu incident đã xảy ra trên process **không bật NMT**, đừng thông báo rằng “không thể làm gì”. Có thể dùng:

- OS/process metrics: RSS, PSS, VSZ, memory maps.
- GC/heap metrics và JVM MXBeans có sẵn.
- Thread count/stacks, class loading count.
- BufferPoolMXBean cho direct/mapped buffers theo contract JDK.
- Container events, cgroup accounting.
- Memory usage theo framework pools, network stacks, native libs.
- Heap dump **khi thật sự cần** để phân biệt Java-side retaining references.

Nhưng phải ghi rõ **NMT coverage missing**. Nếu cần NMT diff ở lần sau, bật từ startup trong **một reproduction process** và kiểm tra overhead/diagnostic policy.

### Các công cụ Linux hữu ích

```bash
cat /proc/PID/status
cat /proc/PID/smaps_rollup
pmap -x PID
ps -o pid,rss,vsz,nlwp,comm -p PID
```

`/proc/PID/smaps_rollup` có thể không có trên kernel/permission set cụ thể. `pmap` cần cài package phù hợp; output là snapshots. RSS trong `ps` thường theo KiB nhưng xác nhận units của tool/environment. Không thể từ một `pmap` snapshot đơn lẻ xác nhận “native memory leak” nếu chưa có trend.

### Linux memory pressure và cgroup

Nên ghi:

- `memory.max`/limit.
- `memory.current` theo thời gian.
- `memory.stat` categories như `anon` và `file` nếu v2.
- `memory.events` trước/sau incident, nhất là `oom_kill`.
- Process RSS/PSS và số processes trong cgroup.
- CPU throttling nếu performance issue đi kèm.

Đừng nhầm memory usage tăng do file cache với Java heap live-set tăng.

## 15. Java 17+ runnable: quan sát heap và NIO buffer pools

Ví dụ này **không chủ động gây OOM**. Nó tạo 4 heap buffers và 4 direct buffers, mỗi cái 256 KiB; giữ strong references tới lúc in kết quả, rồi truy vấn runtime metrics. Nó minh họa **Java heap và native direct storage là hai cơ chế khác nhau**, nhưng không giả định các metrics động phải khớp nhau từng byte.

Tạo file độc lập **`HeapAndNativeDemo.java`**:

```java
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class HeapAndNativeDemo {
    private static final int COUNT = 4;
    private static final int BLOCK = 256 * 1024;

    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("No arguments expected");
        }

        List<ByteBuffer> heap = new ArrayList<>();
        List<ByteBuffer> direct = new ArrayList<>();
        long checksum = 0;

        for (int i = 0; i < COUNT; i++) {
            ByteBuffer h = ByteBuffer.allocate(BLOCK);
            ByteBuffer d = ByteBuffer.allocateDirect(BLOCK);

            h.put(0, (byte) (i + 1));
            d.put(0, (byte) (i + 2));

            heap.add(h);
            direct.add(d);

            checksum += Byte.toUnsignedInt(h.get(0));
            checksum += Byte.toUnsignedInt(d.get(0));
        }

        System.out.println("heap buffers: " + heap.size());
        System.out.println("direct buffers: " + direct.size());
        System.out.println("each capacity: " + BLOCK);
        System.out.println("heap payload bytes: "
                + (heap.size() * BLOCK));
        System.out.println("direct payload bytes: "
                + (direct.size() * BLOCK));
        System.out.println("checksum: " + checksum);
        System.out.println("last direct first byte: "
                + Byte.toUnsignedInt(direct.get(3).get(0)));
        System.out.println("done");

        // Đây là các số RUNTIME-DEPENDENT; in stderr để
        // không trộn vào expected stdout ổn định.
        MemoryUsage usage = ManagementFactory
                .getMemoryMXBean().getHeapMemoryUsage();
        System.err.println("heap used bytes: " + usage.getUsed());
        System.err.println("heap committed bytes: "
                + usage.getCommitted());
        System.err.println("heap max bytes: " + usage.getMax());

        for (BufferPoolMXBean pool :
                ManagementFactory.getPlatformMXBeans(
                        BufferPoolMXBean.class)) {
            System.err.println("buffer pool: " + pool.getName()
                    + ", count=" + pool.getCount()
                    + ", memoryUsed=" + pool.getMemoryUsed()
                    + ", totalCapacity=" + pool.getTotalCapacity());
        }
    }
}
```

### Biên dịch và chạy trên JDK 17+

```bash
javac -encoding UTF-8 HeapAndNativeDemo.java
java -Xms32m -Xmx128m -XX:MaxDirectMemorySize=16m HeapAndNativeDemo
```

**Standard output chính xác khi chương trình hoàn tất bình thường:**

```text
heap buffers: 4
direct buffers: 4
each capacity: 262144
heap payload bytes: 1048576
direct payload bytes: 1048576
checksum: 24
last direct first byte: 5
done
```

### Tự kiểm tra các con số

- Mỗi `BLOCK = 256 × 1024 = 262.144` bytes.
- Bốn buffers có tổng capacity **1.048.576 bytes** mỗi nhóm.
- Heap first bytes: **1, 2, 3, 4**, tổng **10**.
- Direct first bytes: **2, 3, 4, 5**, tổng **14**.
- `checksum = 10 + 14 = 24`.
- Last direct buffer có byte đầu = **5**.
- `MaxDirectMemorySize=16m` trong lệnh mẫu đủ lớn cho 1 MiB direct payload, ngoài một số overhead JVM; nếu môi trường đặc biệt không hỗ trợ flag/có nguồn native shortage thật, process vẫn có thể thất bại vì resource limits khác.

**Standard error có định dạng phụ thuộc runtime**:

```text
heap used bytes: (số động)
heap committed bytes: (số động)
heap max bytes: (số động)
buffer pool: direct, count=..., memoryUsed=..., totalCapacity=...
buffer pool: mapped, count=..., memoryUsed=..., totalCapacity=...
```

Đây chỉ là **mẫu trường dữ liệu**, không phải literal stdout/stderr cố định. Buffer pools được JVM/JDK expose có thể nhiều/ít hơn; `getMemoryUsed()` có thể báo `-1` khi implementation không xác định; số có thể gồm các buffers do code/thư viện khác tạo.

### BufferPoolMXBean không phải hệ thống đo toàn native memory

`BufferPoolMXBean` giúp theo dõi **direct/mapped buffer pools** được JDK expose. Nó **không** đo toàn bộ JNI `malloc`, native library arenas, thread stacks hay code cache.

`totalCapacity` đếm buffer capacity theo contract pool; `memoryUsed` có thể khác capacity do alignment/accounting. Không tự lấy `direct totalCapacity` rồi kết luận **cgroup memory.current − heap used** phải đúng bằng con số đó.

### Vì sao không thêm vòng lặp tạo direct buffers vô hạn?

Bài nhằm hướng dẫn **phân loại và đo**, không yêu cầu làm process/container OOM. Không dùng stress test native allocations không giới hạn trên môi trường chia sẻ hoặc production. Khi cần tìm ngưỡng failure, thực hiện trong **isolated resource-limited test** với kill/timeout và đo chuẩn.

## 16. Lab quan sát NMT trên demo có thời gian sống rất ngắn

`HeapAndNativeDemo` thoát nhanh, có thể không kịp attach `jcmd`. Để luyện cách xem NMT, có thể chạy một application/service test **còn sống đủ lâu** với:

```bash
java -XX:NativeMemoryTracking=summary -Xms32m -Xmx128m -XX:MaxDirectMemorySize=16m YourLongRunningApp
```

`YourLongRunningApp` là tên class ứng dụng bạn có, **không phải một class được định nghĩa trong bài**. Sau đó trên môi trường có quyền attach:

```bash
jcmd -l
jcmd PID VM.native_memory summary
jcmd PID VM.native_memory baseline
jcmd PID VM.native_memory summary.diff
jcmd PID GC.heap_info
```

Các command `baseline` và `summary.diff` nên được thực hiện ở **hai thời điểm khác nhau** với workload/traffic đã ghi lại. Nếu chạy liên tiếp trong vài milliseconds, diff có thể không cho thông tin hữu ích về memory trend.

**Đừng suy direct ByteBuffer backing chắc chắn hiện đủ trong một NMT category**: phạm vi NMT bị giới hạn theo HotSpot VM internal allocations và không cho phép đo toàn native process memory.

## 17. Quan sát từ bên ngoài: OS và Java metrics kết hợp

Một bảng đo nên có ít nhất:

| Mốc | JVM heap used | Heap committed | Direct pool | NMT committed | RSS | cgroup current | cgroup limit |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Sau startup | Có số từ MXBean | Có số từ MXBean | Có số nếu hỗ trợ | Có nếu bật từ startup | Có từ OS | Có nếu cgroup support | Có nếu configured |
| Sau warm-up | Như trên | Như trên | Như trên | So NMT diff | Như trên | Như trên | Như trên |
| Trước sự cố | Như trên | Như trên | Như trên | So NMT diff | Như trên | Như trên | Như trên |
| Sau can thiệp | Như trên | Như trên | Như trên | So NMT diff | Như trên | Như trên | Như trên |

**Không cần cố điền mọi ô nếu tool không hỗ trợ**. Ghi rõ “không có dữ liệu” thay vì lấy một số đo khác scope rồi đặt sai tên.

### Đồng bộ timestamp

JVM GC/NMT logs có thể ghi **process uptime**, còn container metrics sử dụng wall-clock timestamps. Phải align các timebases trước khi nói “RSS tăng ngay sau GC”. Nếu không có đồng bộ clock, báo rõ giới hạn về correlation.

## 18. Bảng chẩn đoán từ thông báo lỗi

| Triệu chứng | Khả năng thường xét trước | Không nên làm ngay |
| --- | --- | --- |
| `OutOfMemoryError: Java heap space` | Live set/heap max/allocation spike trong Java heap | Kết luận mọi memory ngoài heap cũng đã đầy |
| `OutOfMemoryError: Metaspace` | Class metadata, loader retention, MaxMetaspaceSize | Chỉ tăng `-Xmx` rồi coi xong |
| `OutOfMemoryError: Compressed class space` | Class metadata region/cap tương ứng | Đồng nhất với heap objects |
| `Cannot reserve ... direct buffer memory` | MaxDirectMemorySize, direct buffer retention, native pressure | Gọi GC lặp vô hạn |
| `unable to create native thread` | Native stack/OS PID/thread limits, too many threads | Tăng Xmx không kiểm soát |
| `CodeCache is full` | Code cache space và compilation lifecycle | Cho rằng chắc chắn Java heap OOME |
| `Out of swap space?` / native allocation failure | Native VM/OS memory/address-space pressure | Chỉ xem MemoryMXBean.getHeapMemoryUsage() |
| Pod/container `OOMKilled` | Kernel cgroup limit/memory pressure | Chờ Java exception stack trace |
| Exit 137 | SIGKILL, **có thể** OOM | Khẳng định OOM nếu không có events |
| Native crash + `hs_err_pid` | VM/native fatal error; inspect map/stack | Khẳng định nguyên nhân là heap leak |

**Không có một mapping luôn đúng 100%** từ thông báo tới một bug cụ thể. Ví dụ `Metaspace` có thể do cap quá thấp hoặc class-loader leak; direct-memory exhaustion có thể do cap JDK hoặc actual OS memory pressure.

## 19. Quy trình điều tra thực tế theo thứ tự ưu tiên

### Bước 1 — Phân loại failure

Ghi **nguyên văn thông báo** hoặc Kubernetes termination reason:

- Java `OutOfMemoryError` loại nào?
- VM fatal crash (`hs_err`)?
- SIGKILL/OOMKilled?
- Application timeout không có crash?
- Failed allocation khi tạo thread/direct buffer?

Chỉ một dòng “heap còn 40%” không đủ.

### Bước 2 — Xác định scope và giới hạn

- JVM process ID, vendor/build, OS/architecture.
- Process RSS/VSZ/PSS, container memory.current/max.
- Cgroup v1/v2 và memory.events nếu có.
- Pod `limits` và `requests`, sidecars/các processes khác.
- `-Xmx`, `-Xms`, `-Xss`, `MaxDirectMemorySize`, metaspace/code cache flags.
- Các user/system limits liên quan PID/tasks/address-space.

### Bước 3 — Quan sát memory categories

Nếu NMT có sẵn, lấy summary/baseline/diff. Song song, xem:

- Heap used/committed/max, GC events.
- Metaspace/class counts, class loader churn.
- Thread count/stack usage.
- Direct/mapped BufferPoolMXBean.
- Code cache và JIT metadata.
- OS mappings và native memory categories không được NMT cover.

### Bước 4 — Lập giả thuyết có thể bác bỏ

Ví dụ:

**H1: direct buffer retention.** Dự đoán direct pool count/total capacity tăng cùng RSS; giảm buffer pool retention có thể ổn định trend.

**H2: thread leak.** Dự đoán live thread count và pids.current tăng; thread dumps thấy thread lifecycle không kết thúc.

**H3: class-loader leak.** Dự đoán class count/metaspace committed tăng sau mỗi redeploy, retained loader paths còn sống.

**H4: cgroup file cache pressure.** Dự đoán `memory.stat file` tăng trong khi process anonymous RSS tương đối ổn định.

**H5: native library allocation.** Dự đoán RSS tăng nhưng NMT tracked categories không giải thích đủ, OS-level native profiling chỉ rõ allocator/call paths.

### Bước 5 — Thử can thiệp có đối chứng

- Giảm pool retention/đóng buffers đúng lifecycle.
- Sửa thread ownership/executor shutdown.
- Sửa class loader/static reference leak.
- Điều chỉnh heap/native budget nếu cần và hợp lệ.
- Giảm concurrency hoặc giới hạn direct memory với thông số phù hợp.
- Nếu native library leak, sửa library usage/version và thu native heap profile.

Dùng **cùng workload/JVM/limits** khi so trước-sau, thu nhiều cửa sổ đo, ghi uncertainty và trade-offs.

### Bước 6 — Kiểm tra sau fix

Không chỉ nhìn “heap còn trống”:

- RSS/cgroup peak có giảm và không còn OOM events?
- Non-heap trend có ổn định qua nhiều cycles?
- p95/p99 và error rate có tốt hơn?
- GC/throughput có regression không?
- Các limits và cảnh báo đã được xác minh?
- Có thể rollback khi production profile khác staging không?

## 20. Ví dụ sự cố giả định: container OOM dù heap used thấp

**Mọi số sau chỉ là ví dụ mô phỏng cách viết báo cáo, không phải một production incident đã được đo.**

- Pod memory limit: **1024 MiB**.
- Explicit heap maximum: **768 MiB**.
- Heap used cuối cùng dashboard thấy: **420 MiB**.
- Last state: **OOMKilled**.
- cgroup memory.current ngay trước lỗi: **1018 MiB**.
- OS RSS process gần thời điểm đó: **900 MiB**.
- NMT chưa được bật.

### Giả thuyết

- H1: direct buffer pool lớn do keep-alive/session caching.
- H2: thread stacks hoặc native library allocations tăng.
- H3: cgroup file-backed memory pressure.
- H4: một process khác trong cgroup sử dụng RAM.

### Kết luận được phép từ số liệu này

**Có bằng chứng container memory pressure và OOM kill**, nhưng **chưa có bằng chứng xác định component native cụ thể là root cause**. Heap used < Xmx không bác bỏ OOMKill. NMT không bật nên không thể retroactively lấy NMT diff từ process đã chết.

### Phép đo cần lần sau

- BufferPoolMXBean direct/mapped counts/capacities.
- Thread counts + pids.current.
- NMT summary/diff bật từ startup trong staging/canary.
- OS anonymous/file-backed memory breakdown.
- Process RSS và số processes cùng cgroup.
- Heap committed/after-GC trend để loại trừ heap capacity accounting misinterpretation.

### Rủi ro của cách “sửa” thiếu căn cứ

Tăng `-Xmx768m` lên `-Xmx900m` **có thể làm tình hình tệ hơn** nếu total limit vẫn 1024 MiB: headroom danh nghĩa giảm từ **256 MiB** xuống **124 MiB**. Điều này không chứng minh giảm Xmx luôn là lời giải; cần đo live-set + native footprint để chọn budget an toàn.

## 21. Những cách sửa sai dễ gặp

### “Tăng `-Xmx` là hết mọi OOME”

Sai. OOME có detail messages khác nhau, và OS OOMKilled có thể xảy ra khi heap vẫn trống. Đặt Xmx lớn hơn còn có thể làm native headroom giảm.

### “Tăng `-XX:MaxDirectMemorySize` để tránh direct-buffer OOME”

Có thể **chỉ dời lỗi** từ direct-memory budget sang container memory cap, nếu direct buffers vẫn bị giữ quá nhiều. Trước hết kiểm tra retention, pooling, lifecycle và workload.

### “Giảm `-Xss` tới mức rất thấp để tạo được nhiều threads”

Có thể gây `StackOverflowError` hoặc không giải quyết pids.max/OS task limits. Sửa unbounded thread creation, đóng executor và đo stack depth trước.

### “NMT không thấy tăng nên chắc chắn không có native leak”

Sai. NMT JDK 17 giới hạn VM internal native allocations, không đo mọi JNI/third-party/native library allocations. Cần OS profiling/metrics để điều tra phần chênh.

### “RSS lớn hơn heap used thì chắc leak”

Sai. JVM có metaspace/code cache/threads/GC structures và OS mappings; chúng có thể hợp lệ. Cần trend và ownership, không chỉ một snapshot.

### “Full GC giải phóng được toàn bộ native memory”

Không. GC reclaim Java heap objects theo reachability/collector policy. Nó có thể gián tiếp giúp một số Cleaner-managed resources khi wrappers không còn reachable, nhưng không có guarantee nó giải phóng tất cả native allocations, thread stacks, native caches hay VM mappings.

### “Dùng heap dump để tìm mọi native leak”

Heap dump hữu ích cho Java object retaining references. Nó **không** chứa toàn bộ native malloc arenas, native thread stacks hoặc file-backed mappings. Với JNI leaks có thể cần native profiling hoặc allocator instrumentation.

### “Nhìn một NMT total lớn là root cause đã rõ”

Không. NMT categories có reserved và committed, không đồng nghĩa resident RAM; còn cần time-series, diff và OS accounting để gắn với failure.

## 22. Tình huống nâng cao: lỗi address space và commit

Trên môi trường 32-bit, limited virtual address space có thể khiến VM reserve/commit vùng mới thất bại dù physical RAM của hệ thống còn nhiều. Trên 64-bit, address-space capacity lớn hơn nhưng:

- `ulimit -v`/`RLIMIT_AS` có thể giới hạn address space.
- Mapped files/large reserved regions có thể bị giới hạn bởi OS/VM settings.
- Native allocator có thể thất bại do fragmentation/overcommit/commit policy.
- Container cgroup limit có thể gây kill theo charged usage.
- Sự cạn kiệt swap/physical RAM có thể khiến OS và allocator xử lý khác theo platform.

**Không gom tất cả thành “RAM vật lý hết”.** Một yêu cầu reserve virtual memory và một yêu cầu commit actual pages có những điều kiện thành công khác nhau. Đọc OS/native failure message và `hs_err` nếu có.

### Linux overcommit và OOM killer

Linux cho phép một số virtual memory allocations thành công ngay cả khi toàn bộ physical backing chưa sẵn, tùy kernel overcommit policy. Lỗi có thể đến **lúc trang được chạm** chứ không phải đúng lúc vùng địa chỉ được reserve. Do đó số `VSZ` rất lớn không đồng nghĩa một process đang giữ tương ứng số bytes resident.

## 23. Security và chi phí thu thập chẩn đoán

Các artifacts điều tra memory có thể chứa dữ liệu nhạy cảm:

- Heap dump chứa object values, credentials, tokens, PII.
- Native core dump có thể chứa cả vùng native secrets.
- Thread dump có thể lộ tên thread, stack arguments/paths.
- NMT/`/proc/PID/maps` có thể để lộ memory layout và binary paths.
- JFR và logs có thể chứa class names, method names, paths hoặc custom events.

**Thu thập trong phạm vi cho phép**, chọn storage/quyền truy cập/encryption và retention policy đúng, xóa sau khi mục đích hoàn thành.

### Đừng gây sự cố thứ hai khi debug

- `GC.heap_dump` có thể gây pause đáng kể và tạo file lớn.
- Bật NMT/JFR/profiler có overhead; đo trước impact khi production dùng.
- `jcmd Thread.print` với hàng chục nghìn threads có thể tạo output rất lớn.
- Đừng cố tình allocate direct buffers hoặc threads vô hạn trên shared environment.
- Trước khi điều chỉnh memory caps, đặt success criteria và rollback plan.

## 24. Bài tập có đáp án

**Bài 1.** `-Xmx1g` có nghĩa RSS process tối đa 1 GiB?  
**Đáp án:** Không. Xmx chỉ giới hạn Java heap; native memory và OS/cgroup accounting còn độc lập.

**Bài 2.** Heap used 300 MiB, committed 700 MiB, max 1 GiB. Có “700 MiB RAM trống” không?  
**Đáp án:** Không được kết luận vậy. Heap max-used là **heap capacity headroom**, không phải free physical/system RAM.

**Bài 3.** Container memory limit 1024 MiB, Xmx 900 MiB. Headroom danh nghĩa cho phần khác là bao nhiêu?  
**Đáp án:** **124 MiB** khi chỉ lấy limit trừ max heap; không phải dự báo chính xác peak RSS.

**Bài 4.** Java `OutOfMemoryError: Metaspace` có buộc tăng `-Xmx`?  
**Đáp án:** Không. Cần class loading/metadata retention và metaspace limits.

**Bài 5.** Code cache đầy có nhất thiết là heap đầy?  
**Đáp án:** Không. Code cache chứa JIT native code, không phải Java heap objects.

**Bài 6.** `unable to create native thread` có thể do gì ngoài RAM?  
**Đáp án:** cgroup `pids.max` hoặc OS thread/process limits, stack address space, task limits.

**Bài 7.** `-Xss` nhỏ đi có luôn làm thread count tăng an toàn?  
**Đáp án:** Không. Còn PID limits, native overhead và rủi ro `StackOverflowError`.

**Bài 8.** `ByteBuffer.allocateDirect` có phải toàn bộ dữ liệu ở Java heap?  
**Đáp án:** Không. Backing storage thường off-heap/native, wrapper có object/reference phía Java.

**Bài 9.** `MaxDirectMemorySize` có cap mọi JNI malloc?  
**Đáp án:** Không. Đây là phạm vi direct buffer memory theo HotSpot/JDK, không phải all native allocations.

**Bài 10.** Direct pool capacity ổn định nhưng RSS tăng, có chứng minh không có native leak?  
**Đáp án:** Không. JNI/other native allocations có thể không thuộc direct buffer pool.

**Bài 11.** NMT của JDK 17 có đo tất cả third-party native memory?  
**Đáp án:** Không, Oracle nêu giới hạn rõ về native allocations ngoài HotSpot VM/JDK.

**Bài 12.** Có bật NMT từ process đang chạy với NMT off bằng `jcmd` không?  
**Đáp án:** Không theo NMT HotSpot JDK 17 thông thường; phải bật từ startup.

**Bài 13.** NMT reserved lớn hơn committed rất nhiều có chắc process chiếm tương ứng physical RAM?  
**Đáp án:** Không. Reserved là address space, committed khác RSS.

**Bài 14.** `memory.current` cgroup v2 có phải JVM heap used?  
**Đáp án:** Không. Là charged memory của cgroup theo kernel accounting.

**Bài 15.** `memory.events` có thể giúp xác minh gì?  
**Đáp án:** Những sự kiện memory pressure/OOM/OOM kill do cgroup ghi nhận.

**Bài 16.** Exit code 137 có đủ kết luận OOMKilled?  
**Đáp án:** Không. Nó thường là SIGKILL; đối chiếu pod state, events và kernel/cgroup logs.

**Bài 17.** Vì sao pod OOMKilled có thể không có Java OOME stack trace?  
**Đáp án:** Kernel/cgroup kết thúc process qua signal thay vì HotSpot chủ động ném Java exception.

**Bài 18.** Trong `HeapAndNativeDemo`, tổng payload heap buffers là bao nhiêu?  
**Đáp án:** **1.048.576 bytes**.

**Bài 19.** Tổng payload direct buffers trong demo?  
**Đáp án:** **1.048.576 bytes**; không suy nó bằng NMT/RSS increase.

**Bài 20.** Checksum demo là gì?  
**Đáp án:** **24**, do tổng heap first-byte values 10 cộng direct first-byte values 14.

**Bài 21.** Vì sao BufferPoolMXBean `memoryUsed` có thể khác `totalCapacity`?  
**Đáp án:** Alignment/accounting overhead và implementation, thậm chí `-1` khi không xác định.

**Bài 22.** Có thể tìm mọi native leak chỉ bằng heap dump?  
**Đáp án:** Không. Heap dump cho Java object graph; native malloc arenas có thể cần OS/native tools.

**Bài 23.** Vừa có `Metaspace` OOME vừa loaded class count tăng sau mỗi hot deploy, nên thử gì?  
**Đáp án:** Điều tra class-loader retention và life cycle, metaspace trend; cap chỉ là một phần.

**Bài 24.** K8s memory request 512 MiB và limit 2 GiB: JVM tự tính Xmx theo request hay limit?  
**Đáp án:** Trên HotSpot container-aware, sizing thường căn cứ constraint/container limit khi được detect, không đơn giản theo request; phải kiểm tra effective values.

**Bài 25.** Nếu heap used thấp nhưng cgroup `file` memory rất cao, kết luận gì?  
**Đáp án:** Có file-backed cache/mappings charges cần điều tra; không tự quy cho Java heap hoặc leak.

**Bài 26.** Khi native memory pressure tăng, bước đầu nên tăng Xmx không?  
**Đáp án:** Không mặc định. Phân loại nguồn, kiểm tra native headroom, OS limits và workload; tăng Xmx còn có thể làm tình trạng tệ hơn.

## 25. Checklist điều tra “heap chưa đầy nhưng process hết memory”

- [ ] Đã xác minh JVM vendor/build, JDK version, OS và architecture?
- [ ] Đã lưu thông báo lỗi/termination reason **nguyên văn**?
- [ ] Phân biệt Java `OutOfMemoryError` với OS/container SIGKILL?
- [ ] Có metric heap **used, committed, max** ở cùng mốc thời gian?
- [ ] Có RSS/PSS/VSZ từ OS và memory limit/cgroup usage?
- [ ] Có phân biệt cgroup v1/v2 và đường dẫn metrics đúng?
- [ ] Có xem `memory.events`, pod lastState reason và exit code?
- [ ] Có biết các processes/sidecars khác cùng cgroup hay không?
- [ ] Có native memory budget cho metaspace, code cache, GC, threads?
- [ ] Có đo direct/mapped buffer pools qua JMX/JDK APIs?
- [ ] Có theo dõi class-loader count và loaded class metadata?
- [ ] Có kiểm tra thread count và PID/task limits?
- [ ] Nếu bật NMT, có so summary/diff trong **cùng process**?
- [ ] Nếu NMT không bật, đã ghi rõ lỗ hổng coverage?
- [ ] Có tránh đồng nhất reserved, committed, RSS và heap used?
- [ ] Có xét JNI/native library allocations không được NMT cover?
- [ ] Có so nhiều time windows đủ lâu, không chỉ một snapshot?
- [ ] Có kiểm tra memory pressure, CPU throttling, load shape ở container?
- [ ] Có thiết kế một hypothesis test thay một biến mỗi lần?
- [ ] Có đánh giá nguy cơ khi tăng Xmx/lowering native caps?
- [ ] Có kiểm soát overhead và bảo mật diagnostic artifacts?
- [ ] Đã xác minh sửa lỗi bằng p99/error/peak memory/OOM events thực tế?

## 26. Tổng kết và định hướng xử lý

```text
Heap used thấp
      │
      ▼
Process thiếu memory / OOM?
      │
      ├── Java OOME: heap space?
      │      └─ heap retention, live set, Xmx, GC
      │
      ├── Metaspace / compressed class space?
      │      └─ class loading, loader retention, caps
      │
      ├── Direct buffer memory?
      │      └─ NIO pools, lifecycle, direct budget
      │
      ├── Unable to create native thread?
      │      └─ stacks, thread leak, pids.max, OS limits
      │
      ├── JVM/native allocation failure?
      │      └─ OS maps, NMT coverage, JNI/native libs
      │
      └── Container OOMKilled / SIGKILL?
             └─ cgroup charges, limit, RSS, file cache,
                all processes + headroom
```

**Nguyên tắc:** bộ nhớ “còn chỗ trong Java heap” chỉ nói về **một vùng** của JVM. Khi process thiếu tài nguyên, phải kiểm tra **nguồn cấp phát nào thất bại, ở ranh giới hạn mức nào**, và dùng **các metric đúng scope** để giải thích. Tăng `-Xmx` không phải lời giải mặc định; nó có thể chiếm thêm phần memory mà native code hoặc OS đang cần.

### Tài liệu chính thống

1. **Oracle JDK 17 — Native Memory Tracking** (bật từ startup, summary/detail, giới hạn không bao trùm JNI/third-party): https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
2. **Oracle JDK 17 — Troubleshoot Memory Leaks** (heap, native, metaspace, compressed class space, OOME taxonomy): https://docs.oracle.com/en/java/javase/17/troubleshoot/troubleshooting-memory-leaks.html
3. **Oracle JDK 17 — `jcmd` command**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
4. **Oracle JDK 17 — `java` command, `-Xmx`, `-Xss`, `MaxDirectMemorySize` và RAM sizing:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
5. **Java SE 17 — `BufferPoolMXBean`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/BufferPoolMXBean.html
6. **Java SE 17 — `MemoryMXBean` / `MemoryUsage`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html
7. **Linux kernel — Control Group v2 Memory Controller:** https://docs.kernel.org/admin-guide/cgroup-v2.html#memory
8. **Kubernetes — Assign Memory Resources to Containers and Pods:** https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource/
9. **OpenJDK JEP 197 — Segmented Code Cache:** https://openjdk.org/jeps/197
10. **OpenJDK JEP 387 — Elastic Metaspace:** https://openjdk.org/jeps/387

**Bài học quan trọng nhất:** đọc đúng **Java heap metrics, native tracking có giới hạn, OS RSS/mappings, cgroup accounting và termination reason** trước khi chẩn đoán. Chỉ khi có chuỗi bằng chứng khớp, mới quyết định sửa leak, giới hạn pool/threads, đổi heap/native budget hay điều chỉnh môi trường chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-009">Quy trình điều chỉnh từng giả thuyết, đo lại và lưu cấu hình có thể tái lập</a>

<details>
<summary>Xem chi tiết</summary>

# Quy trình điều chỉnh từng giả thuyết, đo lại và lưu cấu hình có thể tái lập

## 1. Điều chỉnh JVM là một thí nghiệm, không phải sưu tầm flags

Khi một ứng dụng Java chậm, tiêu thụ nhiều bộ nhớ hoặc có latency không ổn định, rất dễ thử hàng loạt tùy chọn JVM cùng lúc: tăng **-Xmx**, đổi GC, giảm số GC threads, tăng code cache và giới hạn CPU. Nếu sau đó hệ thống nhanh hơn, bạn vẫn không biết **thay đổi nào gây ra cải thiện**, thay đổi nào gây tác dụng phụ, hoặc liệu kết quả chỉ do warm-up, traffic, JDK build hay máy đo khác nhau.

Trong phạm vi **Cấu hình tài nguyên JVM**, cách làm tốt hơn là **điều chỉnh theo giả thuyết có thể kiểm chứng**, so với một baseline đã lưu, lặp lại phép đo trong điều kiện kiểm soát, đánh giá cả chỉ số mục tiêu lẫn chỉ số bảo vệ, và giữ lại đầy đủ cấu hình cùng bằng chứng của từng lần chạy.

Sơ đồ quy trình:

~~~text
Triệu chứng/SLO không đạt
          |
          v
Thu thập baseline + cấu hình hiện hành
          |
          v
Xác định bottleneck có bằng chứng
          |
          v
Viết MỘT giả thuyết có thể bác bỏ
          |
          v
Chọn đúng MỘT biến can thiệp
          |
          v
Khóa các biến còn lại, xác định guardrails
          |
          v
Warm-up + chạy các lần đo A/B lặp lại
          |
          v
So latency/throughput/CPU/memory/GC/errors
          |
          +--- Khác biệt không đáng tin? ---> đo lại/sửa thiết kế test
          |
          +--- Guardrail vi phạm? ----------> rollback
          |
          +--- Giả thuyết được ủng hộ? -----> canary có kiểm soát
                                                  |
                                                  v
                                         Ghi cấu hình, artifacts
                                                  |
                                                  v
                                         Giữ/rollback + theo dõi
~~~

### Sau bài này bạn phải làm được

1. Phát biểu giả thuyết có **triệu chứng, cơ chế dự kiến, một biến điều chỉnh, kết quả kỳ vọng và cách bác bỏ**.
2. Tạo **baseline** mô tả đúng hệ chạy: JDK vendor/build, flags, heap, GC, CPU/memory limits, OS, container, workload và dữ liệu thử.
3. Chọn metrics cho **độ trễ p95/p99/p99.9**, throughput, GC pauses, allocation, CPU, RSS/native memory, startup và lỗi.
4. Phân biệt **chỉ số cần cải thiện** với **guardrails không được vi phạm**, tránh tối ưu một chiều.
5. Chạy A/B với **warm-up, nhiều lần lặp, thứ tự chạy kiểm soát, phương sai và khoảng tin cậy** thay vì nhìn một lần chạy đẹp.
6. Xác minh **effective JVM configuration**, không chỉ tin vào flags đã viết trong script.
7. Lưu được **run manifest**, logs, JFR/GC evidence, lệnh chạy, application build và decision record để tái lập.
8. Biết lúc nào **nên dừng tuning JVM** để sửa memory leak, code path, DB latency, CPU throttling hoặc allocation patterns.
9. Thiết kế **canary, rollback và theo dõi sau triển khai**.
10. Thực hành Java 17+ với chương trình chức năng có stdout xác định và một chương trình minh họa decision gates, không nhầm chúng với production benchmarks.

**Phạm vi:** Java 17+; các lệnh và tùy chọn HotSpot được minh họa theo **JDK 17**, với xác minh từ Oracle/OpenJDK. JVM khác hoặc JDK major mới hơn có thể thay flags, ergonomics, GC defaults, cgroup behavior và diagnostic commands. Tài liệu này là **quy trình thực nghiệm**, không đề xuất một bộ flags phù hợp mọi hệ thống.

## 2. Chốt câu hỏi kỹ thuật trước khi thay cấu hình

Một tuyên bố kiểu “JVM chậm, tăng heap xem sao” chưa đủ điều kiện để thiết kế phép thử.

Mẫu phát biểu tốt:

> Ở traffic 600 requests/s, p99 endpoint /checkout vượt 120 ms khi old-generation occupancy cao; GC logs cho thấy các pause G1 thường xuyên trùng request latency spikes. Tôi dự đoán tăng -Xmx từ 1 GiB lên 1.5 GiB, giữ nguyên mọi biến khác, sẽ giảm GC frequency và p99 ít nhất 15% mà không làm RSS vượt budget 2.2 GiB hoặc throughput giảm hơn 2%. Nếu GC pauses không giảm, p99 không cải thiện lặp lại được hoặc memory guardrail bị vi phạm, bác bỏ thay đổi.

**Các con số trên hoàn toàn minh họa**, không phải kết quả đo có thật. Ưu điểm: xác định được cơ chế, biến duy nhất, chỉ số tác động và điều kiện bác bỏ.

### 2.1. Mô tả theo sáu trường

| Trường | Nội dung cần viết |
| --- | --- |
| **Observation** | Triệu chứng được đo: p99 tăng, GC pause spike, CPU 100%, RSS cao... |
| **Hypothesis** | Cơ chế nghi ngờ: heap headroom, allocation rate, JIT warm-up, thread count... |
| **Intervention** | Chính xác **một** thay đổi: -Xmx 1g → 1536m |
| **Expected outcome** | Dự đoán cụ thể: GC frequency giảm, p99 giảm |
| **Success/guardrails** | Ngưỡng p99, error rate, throughput, CPU và RSS |
| **Falsification** | Điều kiện cho thấy giả thuyết sai hoặc không thể kết luận |

Nếu không chỉ rõ được **tại sao flag đó ảnh hưởng chỉ số đang xấu**, nên tiếp tục quan sát thay vì thử flags ngẫu nhiên.

### 2.2. Giả thuyết phải hướng vào nguyên nhân, không chỉ tương quan

Ví dụ:

- “Heap used cao” không tự chứng minh cần tăng heap: có thể cache sống lâu, memory leak logic hoặc allocation churn.
- “CPU cao” không tự chứng minh JIT/GC là vấn đề: có thể workload CPU-bound, busy spin hoặc serialization.
- “p99 cao” không tự chứng minh GC pause là nguồn gốc: DB locks, network, safepoint, application monitors và queuing cũng gây tail latency.
- “Nhiều threads WAITING” không tự chứng minh phải tăng thread pool; có thể workers idle đúng thiết kế.
- “GC cycles nhiều” không tự chứng minh giảm pause goal sẽ tốt; phải xem throughput và latency tổng.

**Thứ tự đúng:** đo bottleneck → nêu cơ chế → can thiệp hẹp → đo lại.

## 3. Biến độc lập, biến phụ thuộc và biến gây nhiễu

Trong một thử nghiệm JVM:

- **Biến độc lập:** flag/hạn mức đang cố ý thay đổi; ví dụ -XX:MaxGCPauseMillis.
- **Biến phụ thuộc:** p99, throughput, GC pause distribution, CPU, RSS...
- **Biến kiểm soát:** cùng binary, JVM build, CPU quota, memory limit, traffic mix, dữ liệu, JDK options khác, backend capacity.
- **Biến gây nhiễu:** JIT warm-up, OS page cache, thermal throttling, VM host noisy neighbors, autoscaling, DB cache state, clock drift, GC phase khác nhau, JFR overhead...
- **Một run:** một lần thực thi có timestamp, args, environment, workload và metrics.
- **Một experiment:** baseline cùng các candidate runs có protocol so sánh và quyết định.

Không nhất thiết mọi phép test phải “hoàn toàn giống nhau” theo nghĩa không có nhiễu; điều quan trọng là **ghi nhận/kiểm soát các nguồn nhiễu** để sự khác biệt có thể được quy cho can thiệp với độ tin cậy thích hợp.

### 3.1. Một lần chỉ đổi một biến có nghĩa gì?

Nếu baseline là:

~~~bash
java -XX:+UseG1GC -Xms256m -Xmx1024m -jar service.jar
~~~

và candidate:

~~~bash
java -XX:+UseG1GC -Xms256m -Xmx1536m -jar service.jar
~~~

thì flag cố ý đổi là **-Xmx**. Nhưng thay Xmx có thể **kéo theo những thay đổi ergonomics** như số lượng regions, heap sizing hoặc GC heuristics. Đó là **tác động trung gian của biến can thiệp**, không phải lỗi “đã đổi nhiều biến thủ công”. Phải đo/log các hiệu ứng này để hiểu kết quả.

Ngược lại, candidate:

~~~bash
java -XX:+UseZGC -Xms512m -Xmx1536m -XX:ConcGCThreads=2 -jar service.jar
~~~

đã đồng thời thay **GC algorithm, Xms, Xmx, concurrency**. Nếu hiệu năng đổi, không quy được cho một nguyên nhân rõ. Nếu chủ đích của bài test là so sánh hai **cấu hình hệ thống hoàn chỉnh**, có thể làm; nhưng phải gọi đó là **package-level comparison**, không phải một-variable causal experiment.

### 3.2. Dùng một biến theo vòng lặp

~~~text
Baseline A:
  GC=G1; Xms=256m; Xmx=1024m; pauseGoal=default

Experiment H1:
  GC=G1; Xms=256m; Xmx=1536m; pauseGoal=default
  Chỉ Xmx đổi.

Sau H1 đạt và được chấp nhận, tạo baseline mới A1.

Experiment H2:
  GC=G1; Xms=256m; Xmx=1536m; pauseGoal=150ms
  Chỉ MaxGCPauseMillis đổi so với A1.

Không kết hợp H1+H2 rồi nói kết quả là tác động riêng của H1.
~~~

Nếu các flags tương tác mạnh, có thể cần thí nghiệm **factorial** sau khi đã hiểu từng biến; ghi rõ thiết kế đó. “Một giả thuyết một biến” là cách học/phân tích nguyên nhân đơn giản và minh bạch, không phải cấm mọi phương pháp nghiên cứu đa biến.

## 4. Những giới hạn tài nguyên cần chụp trước khi đo

### 4.1. Java heap và process memory khác nhau

- **-Xms:** initial/minimum heap size configuration theo JVM.
- **-Xmx:** hard maximum Java heap.
- **-XX:MaxRAMPercentage:** sizing heap theo phần trăm RAM mà JVM nhận diện, khi không có giới hạn trực tiếp ưu tiên hơn.
- **Metaspace:** class metadata native memory.
- **Code cache:** JIT compiled machine code và runtime code blobs.
- **Thread stacks:** số threads và -Xss ảnh hưởng reserved/committed stack memory.
- **GC native memory:** collector metadata và buffers.
- **Direct/native buffers:** nằm ngoài managed Java heap ở nhiều triển khai.
- **Container RSS/working set:** tổng tài nguyên thực cần đánh giá so với cgroup memory limit.

Một JVM **-Xmx2g** trong container 2 GiB có thể bị OOMKilled trước khi Java heap chạm Xmx vì những thành phần native. Không coi -Xmx là memory limit toàn process.

### 4.2. CPU quota và JVM perceived processors

Các biến đáng lưu:

- Container CPU request/limit/quota.
- Cpuset và affinity.
- **Runtime.getRuntime().availableProcessors()** mà JVM báo.
- **-XX:ActiveProcessorCount** nếu đặt để override số cores mà JVM dùng trong ergonomics.
- Concurrent/parallel GC threads và compiler thread counts.
- Các ứng dụng khác cùng host/node.

Ví dụ nếu container CPU quota thực tế là 2 vCPU mà JVM chọn nhiều GC/compiler threads, throughput/tail latency có thể thay đổi theo CPU scheduling. Không tự giả định “máy có 32 cores nên process được dùng 32 cores”.

### 4.3. Xms, Xmx và phần trăm RAM

Trong HotSpot JDK 17, **-Xmx** có thể override phần trăm heap sizing khi cùng được đưa vào launch options; **-Xms** cũng có precedence tương ứng với initial size percentage. Vì vậy ghi:

~~~bash
java -Xmx1g -XX:MaxRAMPercentage=70 -version
~~~

không đồng nghĩa hard max heap = 70% container. Cần xem **effective MaxHeapSize**, không đoán qua list flags.

**Không trộn lẫn** Xmx absolute và percentage sizing trong một thử nghiệm nếu không cố ý nghiên cứu precedence/ergonomics.

### 4.4. Các flags có thể thay behavior cùng nhau

- Đổi -Xmx có thể làm đổi GC ergonomics.
- Đổi CPU quota/ActiveProcessorCount có thể làm đổi GC/JIT thread ergonomics.
- Đổi G1 pause goal có thể làm G1 thay young sizing và frequency.
- Đổi -Xms thành bằng -Xmx có thể thay startup, committed footprint, uncommit/headroom.
- Đổi GC collector có thể làm các flags cũ không còn phù hợp.
- Đổi JDK update build có thể thay optimizations/defaults dù launch flags giống nhau.

Bởi vậy **cấu hình có thể tái lập** phải gồm **effective runtime flags, JVM build và environment**, không chỉ file chứa ý định của người cấu hình.

## 5. Thiết lập baseline đáng tin

Một baseline tốt là ảnh chụp khách quan của **hệ thống trước thay đổi**, không chỉ một dòng p99.

### 5.1. Checklist baseline

1. **Application artifact:** source commit/tag, build ID, checksum JAR/container image digest.
2. **JDK:** vendor, java -version, runtime build, architecture, JVM implementation.
3. **OS/container:** image, kernel/platform, CPU quota, memory limit, cpuset, node class.
4. **JVM flags:** input args thực, environment injection, inherited JAVA_TOOL_OPTIONS, final flags.
5. **Workload:** user journeys, RPS pattern, payload distributions, concurrency, arrival rate và dataset revision.
6. **External dependencies:** DB version/indexes/cache state, remote service latency, connections/timeouts.
7. **Warm-up plan:** bao nhiêu phút/iterations và tiêu chí “đã warm”.
8. **Measurement window:** duration, start/end timestamps, timezone và clock synchronization.
9. **Key metrics:** p50/p95/p99/p99.9, error/timeout rate, throughput, CPU, RSS, heap, GC pauses.
10. **Raw evidence:** GC logs, JFR (nếu dùng), request traces, OS/container metrics, benchmark raw files.
11. **Replications:** số runs, thứ tự chạy, số liệu phân tán.
12. **Decision record:** hypothesis, expected improvement, gate thresholds, acceptance/rollback rule.

### 5.2. Không dùng duy nhất một lần đo

Giả sử p99 của một run giảm từ 120 ms xuống 90 ms, nhưng đồng thời traffic giảm 20%, DB cache vừa warm hoặc CPU quota tăng. Bạn **không** thể kết luận giảm 25% nhờ flag.

Dùng nhiều replications, traffic pattern và dataset nhất quán. Khi có khả năng, dùng thứ tự A/B xen kẽ như:

~~~text
A1 -> B1 -> B2 -> A2 -> A3 -> B3
~~~

hoặc randomized block order để giảm bias thời gian/host drift. Giữa các lần, quyết định rõ cold-start vs warmed service có reset hay không, tránh để cache của run trước làm lợi cho run sau.

**Không dùng thứ tự A-A-A rồi B-B-B** mà bỏ qua nguy cơ máy/traffic thay đổi theo thời gian.

## 6. Chỉ số mục tiêu và guardrails

Một tuning run thường có **mục tiêu chính** và **rào chắn**:

| Metric | Vai trò | Cạm bẫy |
| --- | --- | --- |
| **Request p99** | Tail latency SLO | GC pause không phải toàn bộ request latency |
| **Request p99.9** | Extreme tails, khi đủ samples | Ước lượng nhiễu nếu sample size quá nhỏ |
| **Throughput (RPS/TPS)** | Năng lực phục vụ | Tăng RPS do giảm correctness không hợp lệ |
| **Error rate/timeout rate** | Guardrail bắt buộc | Tuning nhanh hơn nhưng nhiều lỗi là regression |
| **GC pause distribution** | Chẩn đoán collector | Mean thấp không đủ nếu tails lớn |
| **GC CPU/time** | Chi phí collector | Concurrent work vẫn cạnh tranh CPU |
| **Allocation rate** | Áp lực cấp phát | Có thể đổi theo business traffic |
| **Heap live set/occupancy** | Memory requirement | High occupancy có thể do cache/leak logic |
| **Process RSS/container working set** | Physical memory budget | Không đồng nhất với reserved virtual memory |
| **CPU throttling/quota** | Resource availability | Có thể làm p99 xấu dù flag đúng |
| **Startup/warm-up duration** | SLO cho deploy/serverless | Steady-state nhanh không đủ nếu startup chậm |
| **JIT/code cache/native threads** | Chi phí runtime ngoài heap | Không suy bằng -Xmx |

### 6.1. Thiết lập gate trước khi đo

Ví dụ giả định:

~~~text
Primary objective:
  Candidate p99 <= 90 ms.

Guardrails:
  Error rate <= baseline + 0.1 percentage point.
  Throughput >= 98% of baseline.
  Peak container RSS <= 700 MiB.
  CPU utilization <= 75% of allocated quota.
  No OOMKill; no unhandled JVM fatal errors.

Decision:
  Reject nếu bất kỳ guardrail fail.
  Nếu chưa đủ samples để so p99: inconclusive.
  Accept only khi nhiều replications và canary ủng hộ.
~~~

Không điều chỉnh thresholds **sau** khi nhìn candidate cho đến khi nó “đạt”. Làm thế là post-hoc cherry-picking và mất ý nghĩa kiểm chứng giả thuyết.

### 6.2. Trung bình không thay thế tail latency

Một candidate có mean 10 ms tốt hơn baseline mean 11 ms nhưng p99 tăng từ 80 ms lên 300 ms có thể tệ hơn đối với API có SLO p99 dưới 100 ms.

Nếu hệ thống có yêu cầu p99, đo p99 **trên cùng workload và cửa sổ đủ samples**, đừng quyết định từ mean. Nếu dữ liệu quá ít, nói **inconclusive**.

### 6.3. Không coi p99.9 của 100 requests là đáng tin

Ước lượng quantile cao cần nhiều observations độc lập/phù hợp và chú ý autocorrelation, queueing bursts, phối hợp nhiều endpoints. Cần aggregate/histogram method nhất quán qua A/B. Không trung bình cộng các p99 per-minute để thay cho p99 trên toàn tập requests nếu muốn quantile hợp nhất.

## 7. Chẩn đoán trước khi chọn JVM flag

Nếu latency tăng, xem chứng cứ:

~~~text
Request p99 spike
   |
   +-- GC STW pause cùng timestamps?
   |       +-- Có -> xét allocation/live set/heap/GC goal
   |
   +-- High CPU throttling?
   |       +-- Có -> xét resource quota/CPU work
   |
   +-- Many BLOCKED threads?
   |       +-- Có -> lock contention/deadlock analysis
   |
   +-- WAITING on DB/network/Future?
   |       +-- Có -> external queue/dependencies
   |
   +-- Startup slow, compiler threads busy?
   |       +-- Có -> JIT warm-up/compile queue
   |
   +-- Native RSS near limit?
           +-- Có -> heap/native memory budget
~~~

Không thay collector nếu 90% request time nằm ở database. Không tăng MaxRAMPercentage để chữa thread-pool starvation. Không hạ MaxGCPauseMillis để chữa một memory leak logic.

### 7.1. Lưu bằng chứng và độ tin cậy

Đối với mỗi hypothesis, phân loại:

- **Observed:** metric/log cho thấy dấu hiệu trực tiếp.
- **Inferred:** giả định cơ chế từ bằng chứng.
- **Not measured:** chưa có dữ liệu.
- **Conflicting evidence:** có dữ liệu phản bác.

Ghi rõ để review sau không biến phỏng đoán thành “thực tế được đo”.

## 8. Một nhóm flags HotSpot Java 17 thường được nghiên cứu

| Flag | Câu hỏi giả thuyết phù hợp | Rủi ro/cần đo |
| --- | --- | --- |
| **-Xmx** | Heap headroom có thiếu gây GC thường xuyên? | RSS tăng, ergonomics thay đổi |
| **-Xms** | Heap resizing/initial commit ảnh hưởng warm-up? | Startup/RSS/uncommit |
| **-XX:MaxRAMPercentage** | Autosizing trong container có phù hợp? | Phụ thuộc detected memory và Xmx precedence |
| **-XX:ActiveProcessorCount** | JVM đang nhìn nhận sai effective CPU? | GC/JIT thread heuristics, throughput |
| **-XX:+UseG1GC** | G1 phù hợp cân bằng latency-throughput không? | GC phases, overhead, JDK |
| **-XX:MaxGCPauseMillis** | G1 pause target có cần khác? | Soft goal, throughput/GC frequency |
| **-XX:+UseZGC** | Low-pause collection cải thiện p99 đủ bù CPU/RAM? | Headroom, allocation stalls, version |
| **-XX:ConcGCThreads** | Concurrent collector có không theo kịp? | CPU contention |
| **-XX:ParallelGCThreads** | Pause parallelism phù hợp cores/quota? | CPU spikes, diminishing returns |
| **-XX:ReservedCodeCacheSize** | JIT code cache đang cạn? | Native memory, codecache fragmentation |
| **-XX:CICompilerCount** | Compilation backlog do JIT CPU/threads? | Warm-up và competition |
| **-Xss** | Stack memory/thread count có là budget issue? | StackOverflowError nếu quá nhỏ |
| **-XX:MaxMetaspaceSize** | Cần giới hạn class metadata không? | Có thể gây OOME: Metaspace; không chữa leak |

**Không hiểu bảng là danh sách flags nên bật cùng lúc.** Một số có ràng buộc theo collector, JDK/version, hardware hoặc build. Tham số GC mục tiêu như **MaxGCPauseMillis là mục tiêu mềm, không phải bảo đảm hard realtime**.

### 8.1. Tại sao thường nên giữ mặc định trước?

HotSpot ergonomics có thể chọn giá trị hợp lý cho nhiều workloads. Một cấu hình copy từ JDK 8/11 cho JDK 17 có thể vô hiệu hoặc tệ hơn. Nếu chưa xác định symptom, bắt đầu bằng defaults hợp lệ rồi điều chỉnh những giới hạn thực sự cần thiết, đặc biệt **heap size** và **container resources**.

### 8.2. JDK 17 và GC mode

Ví dụ:

~~~bash
java -XX:+UseG1GC -Xms256m -Xmx1g -jar service.jar
~~~

Đây là command minh họa, service.jar phải là executable JAR thực. G1 là collector phổ biến trên HotSpot server workloads trong JDK17.

Đổi sang ZGC:

~~~bash
java -XX:+UseZGC -Xms256m -Xmx1g -jar service.jar
~~~

Đây là **đổi collector** chứ không phải chỉ giảm pause goal G1. ZGC JDK17 là **non-generational**; generational ZGC là một hướng phát triển về sau. Khi so collector, giữ các resource budgets/workload phù hợp, không giữ nguyên mọi collector-specific flags một cách máy móc.

## 9. Cấu hình có thể tái lập: chụp argv và effective flags

Trên HotSpot Java 17, có thể dùng:

~~~bash
java -version
java -XX:+PrintFlagsFinal -version
java -XX:+PrintCommandLineFlags -version
~~~

**-XX:+PrintFlagsFinal** in nhiều flags cùng các giá trị/nguồn gốc được JVM quyết định khi startup. Dùng để so effective values, nhưng lệnh trên khởi động **một JVM mới**, có thể không có đúng môi trường/argv của service đang chạy.

Đối với **process đang chạy**:

~~~bash
jcmd
jcmd <PID> VM.version
jcmd <PID> VM.command_line
jcmd <PID> VM.flags -all
jcmd <PID> GC.heap_info
~~~

Thay **<PID>** bằng PID thực, cần permission và JDK diagnostic commands được hỗ trợ. Không coi output VM.command_line là toàn bộ container/environment policy; nó chủ yếu giúp nhìn JVM startup command và selected flags.

### 9.1. Kiểm tra environment injected options

Các nguồn JVM options có thể gồm:

- Command line trực tiếp.
- Launch scripts/entrypoints.
- JAVA_TOOL_OPTIONS.
- JDK_JAVA_OPTIONS.
- Buildpack/framework deployment layers.
- Runtime-selected ergonomic defaults.
- System properties, application configs.

Có thể xảy ra tình huống file service.yaml định Xmx=1g nhưng entrypoint bổ sung flags cuối khác. Muốn tái lập cần **effective command line và flags** tại process thật.

### 9.2. Không ghi secrets vào experiment archive

JVM arguments/system properties/environment đôi khi chứa passwords, auth tokens, database URLs và internal hostnames. Trước khi lưu vào Git, tickets hoặc artifact registry, **redact credentials** theo quy trình an toàn. Nếu redact, ghi rằng manifest là sanitized; lưu raw sensitive evidence trong hệ kiểm soát truy cập phù hợp, không công khai.

### 9.3. Lưu cả build digest

Hai JARs cùng tên **service.jar** có thể chứa code khác nhau. Lưu SHA-256 digest hoặc Git commit của binary/container image. Đừng chỉ lưu tên file và giờ chạy.

Linux/macOS:

~~~bash
sha256sum service.jar
~~~

Nếu môi trường không có sha256sum, dùng hashing tool thích hợp. Windows PowerShell có:

~~~powershell
Get-FileHash -Algorithm SHA256 .\service.jar
~~~

Hash này nhận diện bytes artifact; không chứng minh toàn bộ environment giống nhau.

## 10. Lưu run manifest rõ ràng

Ví dụ **manifest minh họa** dưới dạng JSON:

~~~json
{
  "experimentId": "heap-headroom-h1",
  "runId": "candidate-b3",
  "hypothesis": "Increasing Xmx reduces GC frequency without violating RSS budget",
  "variant": "B",
  "app": {
    "commit": "EXAMPLE_COMMIT_HASH",
    "artifactSha256": "EXAMPLE_SHA256_REPLACE_WITH_REAL_VALUE",
    "configRevision": "config-v12"
  },
  "jvm": {
    "vendor": "Record actual java.vendor",
    "version": "Record actual java.runtime.version",
    "vm": "Record actual java.vm.name",
    "inputArguments": [
      "-XX:+UseG1GC",
      "-Xms256m",
      "-Xmx1536m"
    ],
    "effectiveFlagsPath": "candidate-b3/vm-flags.txt"
  },
  "environment": {
    "osAndArchitecture": "Record actual environment",
    "cpuQuota": "Record actual value",
    "memoryLimit": "Record actual value",
    "containerImageDigest": "Record actual digest"
  },
  "workload": {
    "datasetRevision": "dataset-v5",
    "scenario": "checkout-600-rps-v2",
    "warmup": "10 min under representative load",
    "measurement": "15 min after warmup",
    "clientConcurrency": "Record actual load-generator settings"
  },
  "evidence": {
    "gcLog": "candidate-b3/gc.log",
    "jfr": "candidate-b3/run.jfr",
    "requestHistogram": "candidate-b3/latency.hdr",
    "resourceMetrics": "candidate-b3/metrics.csv"
  },
  "result": {
    "decision": "PENDING_REVIEW",
    "reviewer": "Record actual reviewer"
  }
}
~~~

Các token có chữ **EXAMPLE/Record actual** chỉ để biểu diễn **template manifest**, không phải một run đã thực hiện hoặc artifact digest thực. Khi tái lập, điền bằng dữ liệu thật và thống nhất units, timestamp, time zone.

### 10.1. Cấu trúc thư mục lưu bằng chứng

~~~text
experiments/
  heap-headroom-h1/
    hypothesis.md
    protocol.md
    baseline-a1/
      manifest.json
      launch.txt
      vm-flags.txt
      gc.log
      metrics.csv
      histogram.hdr
    candidate-b1/
      manifest.json
      launch.txt
      vm-flags.txt
      gc.log
      metrics.csv
      histogram.hdr
    baseline-a2/
      ...
    candidate-b2/
      ...
    decision.md
    rollback.md
~~~

Sơ đồ này là **gợi ý cách lưu**, không bắt buộc đúng file extensions. Điều quan trọng là mỗi run độc lập có manifest đầy đủ, raw data, outputs và decision có thể audit.

### 10.2. Manifest cần giữ “input” và “observed”

Phân biệt:

- **Intended config:** flags trong deployment YAML hoặc experiment protocol.
- **Observed config:** java.vendor, java.runtime.version, VM.command_line, effective flags và environment của process thật.
- **Observed result:** metrics thực, GC logs, request histogram, errors.

Nếu intended và observed khác nhau, không lặng lẽ điều chỉnh log rồi báo candidate thành công. Đây là **run invalid**, cần sửa setup và chạy lại.

## 11. Demo Java 17+ #1: logic workload có stdout cố định

Tạo file **TuningWorkload.java**:

~~~java
public class TuningWorkload {
    private static volatile long lastChecksum;

    private static long compute(int size) {
        long checksum = 0;
        for (int i = 0; i < size; i++) {
            checksum += i % 7;
        }
        return checksum;
    }

    public static void main(String[] args) {
        long sum = 0;
        for (int round = 0; round < 20; round++) {
            sum += compute(10_000);
        }
        lastChecksum = sum;

        System.out.println("rounds=20");
        System.out.println("checksum=" + lastChecksum);
        System.out.println("correct="
                + (lastChecksum == 599_880L));
    }
}
~~~

Biên dịch:

~~~bash
javac --release 17 -encoding UTF-8 TuningWorkload.java
~~~

Chạy baseline A:

~~~bash
java -XX:+UseG1GC -Xms64m -Xmx128m TuningWorkload
~~~

**Stdout từ Java application nếu hoàn tất bình thường:**

~~~text
rounds=20
checksum=599880
correct=true
~~~

Tính đúng:

- Trong 10.000 giá trị từ 0 tới 9999, phép i%7 có 1428 chu kỳ đủ 7 số, mỗi chu kỳ tổng 21; còn 4 giá trị có tổng 0+1+2+3=6.
- Tổng một round = 1428 × 21 + 6 = **29.994**.
- 20 rounds × 29.994 = **599.880**.

Chạy candidate B **chỉ đổi Xmx**:

~~~bash
java -XX:+UseG1GC -Xms64m -Xmx256m TuningWorkload
~~~

Java-level stdout vẫn giống. Đây là **functional equivalence smoke test**, không chứng minh Xmx nào tối ưu. Chương trình nhỏ này có thể không tạo GC pressure, JIT có thể constant-fold/optimize loop và elapsed time có thể nhiễu mạnh. **Không đo performance JVM tuning bằng một lần thời gian chạy của demo này.**

### 11.1. Vì sao functional checksum vẫn quan trọng?

Khi thử flags, điều kiện cơ bản là **không làm sai Java semantics hoặc application correctness**. Nếu candidate làm test output sai, lỗi ứng dụng/concurrency/benchmark harness phải được điều tra trước mọi kết luận về performance.

Nhưng một checksum đúng **chưa chứng minh** performance hay correctness của toàn hệ thống. Với production, phải có integration tests, error rates, business invariants và realistic traffic.

## 12. Demo Java 17+ #2: chụp một phần effective runtime

Tạo **JvmSnapshotDemo.java**:

~~~java
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Properties;

public class JvmSnapshotDemo {
    public static void main(String[] args) {
        RuntimeMXBean runtime =
                ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtime.getInputArguments();
        Properties properties = System.getProperties();

        System.out.println("java.version="
                + properties.getProperty("java.version"));
        System.out.println("java.vendor="
                + properties.getProperty("java.vendor"));
        System.out.println("java.vm.name="
                + properties.getProperty("java.vm.name"));
        System.out.println("java.vm.version="
                + properties.getProperty("java.vm.version"));
        System.out.println("os.arch="
                + properties.getProperty("os.arch"));
        System.out.println("availableProcessors="
                + Runtime.getRuntime().availableProcessors());
        System.out.println("maxHeapBytes="
                + Runtime.getRuntime().maxMemory());
        System.out.println("inputArgumentsCount="
                + arguments.size());

        for (int i = 0; i < arguments.size(); i++) {
            System.out.println("jvmArg[" + i + "]="
                    + arguments.get(i));
        }
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 -encoding UTF-8 JvmSnapshotDemo.java
java -XX:+UseG1GC -Xmx256m JvmSnapshotDemo
~~~

**Không có stdout chung cố định** cho vendor, version, heap bytes, availableProcessors, danh sách flags hoặc argument count. Runtime có thể tự thêm flags và giới hạn processor/memory phụ thuộc host/container. **Không bịa expected numbers**.

### 12.1. Vì sao vẫn phải xem VM.flags?

ManagementFactory.getRuntimeMXBean().getInputArguments() hiển thị JVM **input arguments**, không phải mọi ergonomic effective flag hoặc OS/container limit. Tương tự Runtime.maxMemory có thể chịu ảnh hưởng heap alignment/ergonomics. Phải kết hợp **jcmd VM.flags -all** và process/container metrics.

### 12.2. Cẩn thận với properties/arguments nhạy cảm

JVM args có thể chứa -Dpassword=..., connection strings hoặc tokens. Đọc stdout trước khi lưu/chia sẻ; demo chỉ hỗ trợ quan sát, không có chức năng redaction chuẩn cho mọi naming convention.

## 13. Demo Java 17+ #3: quyết định theo nhiều guardrails

Giả sử A/B test tạo một bộ số liệu **minh họa**:

~~~text
Metric                  Baseline A     Candidate B
p99 latency (ms)             120             85
Throughput (req/s)           980            970
Peak RSS (MiB)               600            650
CPU utilization (%)           58             67
Error rate (%)               0.10           0.10

Policy:
  Candidate p99 <= 90 ms
  Throughput >= 98% of baseline
  Peak RSS <= 700 MiB
  CPU utilization <= 75%
  Error rate <= baseline error rate
~~~

**Đây là dữ liệu giả định nhằm giải thích acceptance gates**, không phải benchmark thực tế hoặc bằng chứng giá trị nào của Xmx/G1.

Tạo **TuningDecisionDemo.java**:

~~~java
import java.util.Locale;

public class TuningDecisionDemo {
    public static void main(String[] args) {
        double baselineP99 = 120.0;
        double candidateP99 = 85.0;
        double baselineRps = 980.0;
        double candidateRps = 970.0;
        double candidateRssMiB = 650.0;
        double candidateCpuPercent = 67.0;
        double baselineErrorPercent = 0.10;
        double candidateErrorPercent = 0.10;

        double p99Improvement =
                (baselineP99 - candidateP99)
                        / baselineP99 * 100.0;
        double throughputRetention =
                candidateRps / baselineRps * 100.0;

        boolean meetsP99 = candidateP99 <= 90.0;
        boolean meetsRps = throughputRetention >= 98.0;
        boolean meetsRss = candidateRssMiB <= 700.0;
        boolean meetsCpu = candidateCpuPercent <= 75.0;
        boolean meetsErrors =
                candidateErrorPercent <= baselineErrorPercent;

        boolean allGates = meetsP99 && meetsRps
                && meetsRss && meetsCpu && meetsErrors;

        System.out.printf(Locale.ROOT,
                "p99ImprovementPercent=%.2f%n",
                p99Improvement);
        System.out.printf(Locale.ROOT,
                "throughputRetentionPercent=%.2f%n",
                throughputRetention);
        System.out.println("gatesPass=" + allGates);
        System.out.println("decision="
                + (allGates ? "ELIGIBLE_FOR_CANARY" : "REJECT"));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 TuningDecisionDemo.java
java TuningDecisionDemo
~~~

**Stdout chính xác của ví dụ:**

~~~text
p99ImprovementPercent=29.17
throughputRetentionPercent=98.98
gatesPass=true
decision=ELIGIBLE_FOR_CANARY
~~~

Phép tính:

- (120−85)/120×100 = **29.1666…%**, in 29.17.
- 970/980×100 = **98.9795…%**, in 98.98.
- p99=85 ≤90; throughput retention ≥98%; RSS=650 ≤700; CPU=67 ≤75; error rate không tăng.
- Tất cả gates đúng nên candidate **đủ điều kiện thử canary**, **chưa tự động được deploy production**.

Trong một thử nghiệm thật phải kiểm tra **phương sai, confidence, dataset validity, correctness và production canary**. Việc thay 5 hardcoded numbers bằng values khác không biến demo thành một thống kê benchmark tự động tin cậy.

## 14. GC log: chứng cứ cho giả thuyết về collector

G1/GC tuning nên có GC logs đúng thời gian. Ví dụ HotSpot Java 17:

~~~bash
java -XX:+UseG1GC -Xms256m -Xmx1g -Xlog:gc*,safepoint=info:file=gc-baseline.log:time,uptime,level,tags:filecount=5,filesize=20M -jar service.jar
~~~

File gc-baseline.log sẽ được ghi ở working directory phù hợp khi process có quyền; filecount/filesize thực hiện rotation theo cấu hình. Nếu chạy Windows, đường dẫn log chứa dấu hai chấm hoặc syntax shell có thể cần điều chỉnh.

Trong candidate, chỉ đổi **Xmx** ở launch arguments; nếu đổi tên log filename theo variant để lưu artifacts, đó là **thay đổi telemetry destination**, không phải một can thiệp vào GC algorithm, nhưng phải ghi nhận cả telemetry overhead là tương đương.

Đọc khả năng logging của đúng JVM:

~~~bash
java -Xlog:help
~~~

### 14.1. Những mục nên rút từ GC logs

- GC type và nguyên nhân collections.
- Collection frequency và thời điểm.
- STW pause durations/tails.
- Concurrent phases và CPU overhead khi có.
- Used before/after và heap occupancy trend.
- Humongous allocation hoặc evacuation-related pressure ở G1 khi log có.
- Full GC/compaction bất thường.
- Safepoint timings và sự trùng khớp request latency spikes.

### 14.2. Không đồng nhất GC pause với request latency

Một request p99=120ms không có nghĩa GC pause p99=120ms. Một request có thể đi qua DB, thread pool queue, network, JIT, lock contention và GC. Chỉ kết luận causal contribution của GC khi có tương quan thời gian và, lý tưởng, cải thiện nhất quán sau can thiệp.

### 14.3. G1 MaxGCPauseMillis là goal

Lệnh:

~~~bash
java -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -jar service.jar
~~~

đặt **mục tiêu** pause cho G1, không tạo SLA cứng “mọi pause ≤100ms”. Thay goal có thể ảnh hưởng young sizing, pause frequency, throughput và memory reclamation. Vì thế sau mỗi tuning phải đo **cả latency lẫn throughput/CPU**.

## 15. JFR: quan sát có chiều sâu khi cần

Java Flight Recorder là subsystem profiling/diagnostics của JDK, có thể ghi GC, execution samples, allocation, locks, thread activity và JIT events tùy recording settings.

Khởi động ghi cho thí nghiệm:

~~~bash
java -XX:StartFlightRecording=filename=baseline.jfr,duration=60s,settings=profile -jar service.jar
~~~

Hoặc từ một HotSpot process đang chạy:

~~~bash
jcmd <PID> JFR.start name=baseline settings=profile duration=60s filename=baseline.jfr
jcmd <PID> JFR.check
~~~

Kiểm tra lệnh từ runtime:

~~~bash
jcmd <PID> help JFR.start
~~~

**Dùng cùng recording settings cho A/B** nếu JFR overhead có thể ảnh hưởng metrics; profile settings chi tiết có overhead lớn hơn default. JFR event absence không chứng minh behavior không tồn tại: threshold, interval và configuration có thể bỏ qua.

### 15.1. Kết hợp JFR và request tracing

- Request trace cung cấp thời gian end-to-end và downstream spans.
- JFR cung cấp JVM-level timeline.
- GC log làm rõ pause/cycle details.
- OS/cgroup metrics phản ánh CPU throttling, memory pressure.
- Application counters cung cấp throughput/errors/queue depth.

Kết hợp theo timestamps; không quy mọi latency spike gần JIT/GC event thành lỗi GC khi thiếu bằng chứng.

### 15.2. JFR không thay thế JMH hoặc load test

JFR giúp **quan sát**; JMH giúp benchmark micro-level có warm-up/forks; load test đánh giá service end-to-end. Mỗi công cụ có phương pháp khác nhau. Dùng JFR screenshot một lần để chọn GC tuning vĩnh viễn là thiếu dữ liệu.

## 16. Native Memory Tracking (NMT) khi nghi RSS

HotSpot Java 17 hỗ trợ NMT để phân loại nhiều native allocations. Nên bật **từ lúc JVM startup** nếu cần dùng:

~~~bash
java -XX:NativeMemoryTracking=summary -jar service.jar
~~~

Trên process:

~~~bash
jcmd <PID> VM.native_memory summary
jcmd <PID> VM.native_memory baseline
jcmd <PID> VM.native_memory summary.diff
~~~

NMT có chi phí instrumentation và không phải profiler hoàn chỉnh của mọi third-party native allocations. Các nhóm có thể gồm **Java Heap, Class, Thread, Code, GC, Compiler, Internal**, với reserved/committed riêng.

### 16.1. Ví dụ cách đọc memory

~~~text
Java heap:         reserved 1024 MiB; committed 768 MiB
Code cache:        reserved 240 MiB; committed  40 MiB
Metaspace/native:  other committed allocations
Process RSS:       measured separately by OS
~~~

Các con số chỉ minh họa, không phải NMT output thật. **Reserved ≠ committed ≠ used ≠ RSS**; không cộng thẳng reserved sizes rồi kết luận physical memory đang sử dụng.

### 16.2. Không tự bật NMT ở candidate mà baseline không có

Nếu baseline không chạy NMT còn candidate có NMT, bạn vừa thay cả **instrumentation overhead**. Khi NMT cần để điều tra, chạy cả A và B với cùng mode, hoặc thực hiện một vòng diagnostics riêng và ghi rõ mục tiêu khác.

## 17. Warm-up: phân biệt startup và steady state

JVM HotSpot có **tiered JIT compilation**, class loading, cache warming, lazy initialization và GC state thay đổi trong giai đoạn đầu. Nếu đo candidate ngay sau startup và baseline sau 30 phút chạy, so sánh không công bằng.

Một protocol có thể:

~~~text
Run startup:
  Launch same app build
  Wait until health ready
  Warm-up 10 min with representative traffic
  Verify throughput/GC/JIT metrics stabilize

Measurement:
  15 min with traffic profile held consistent
  Collect histograms, CPU/RSS/GC/errors
  Record pauses during the whole window

Shutdown/reset:
  Stop service cleanly
  Record artifacts
  Restore defined initial state for next run
~~~

Các thời lượng **10/15 phút chỉ là ví dụ**. Dựa vào workload, steady-state criterion, noise và cost để chọn. Short-lived/serverless applications có thể cần đo **cold startup** như mục tiêu riêng và không nên cố warm-up trước nếu business SLA tính từ start.

### 17.1. JIT code khác bytecode

Một số methods được compile C1 rồi C2 sau profiling; code cache tăng. Vì vậy chạy trong 1 giây có thể chủ yếu đo interpreter/C1, không đại diện throughput C2 steady state. Nếu nghiên cứu method microbenchmark, dùng **OpenJDK JMH** với fork/warmup/measurement semantics phù hợp.

### 17.2. Không lấy System.nanoTime quanh một loop làm proof

JIT có thể constant-fold, inline, vectorize hoặc dead-code-eliminate benchmark body. Kết quả đo thiếu blackhole/escaping values có thể phản ánh compiler tối ưu bỏ công việc hơn là hiệu năng mong muốn. Dùng JMH hoặc workload thực tế, không claim ratio từ output của TuningWorkload demo.

### 17.3. Cold vs warm tests phải được gắn nhãn

Một thử nghiệm có thể có:

- **Cold-start trial:** từ process mới, OS page cache/caches state xác định.
- **Warm steady-state trial:** đã ổn định JIT, app caches và load.
- **Burst trial:** traffic spike/hot path mix thay đổi.
- **Endurance trial:** nhiều giờ để phát hiện leaks/GC degradation.

Không gộp p99 của những loại workload khác nhau thành một số duy nhất nếu quyết định cần phân tích nguyên nhân.

## 18. Chạy lặp lại và đánh giá sự biến động

Giả sử có 5 p99 của A và 5 p99 của B:

~~~text
A (ms): 121, 118, 125, 119, 122
B (ms):  88,  85,  92,  87,  86
~~~

Dữ liệu **minh họa**; kết quả B nhìn chung thấp hơn nhưng phải kiểm tra workload cùng điều kiện, events/outliers, error rates, throughput và sample counts.

### 18.1. So sánh median/dispersion trước

Lấy trung vị và phạm vi các run-level p99 để biết noise. Nếu các run p99 của A dao động 50–150ms và B dao động 55–145ms, khác biệt vài ms từ một run không đáng coi là decisive.

### 18.2. Phân biệt hai tầng thống kê

- **Request-level samples:** dùng để tính p99 trong mỗi run.
- **Run-level repetitions:** dùng để đo độ biến động giữa các lần thử độc lập.

Không coi một triệu requests trong một run là một triệu **independent experiment replications** nếu chúng chịu cùng host/DB/GC state và có temporal correlation.

### 18.3. Confidence intervals và practical significance

Khoảng tin cậy/bootstrap/permutation tests có thể hữu ích, nhưng cần thiết kế sampling phù hợp với workload, autocorrelation và số runs. Không bịa “p<0.05” nếu chưa chạy statistical test đúng.

**Practical significance** cũng cần thiết: p99 giảm 1ms có thể không đáng tăng RSS 40%, ngay cả khi statistically detectable.

### 18.4. Kiểm tra regression ngoài mục tiêu

Candidate có thể giảm p99 nhưng làm:

- CPU +30%.
- RSS vượt limit.
- Error rate tăng.
- Startup chậm quá SLO.
- Shutdown time tăng.
- GC allocation stalls mới.
- Thread pool starvation hoặc user-visible timeouts.

**Không chấp nhận chỉ vì một biểu đồ đẹp.**

## 19. Phân tích kết quả theo quyết định ba trạng thái

Không ép mọi experiment thành thắng/thua tức thời.

| Quyết định | Khi nào? | Hành động |
| --- | --- | --- |
| **ACCEPT FOR CANARY** | Mục tiêu đạt lặp lại, correctness và guardrails pass | Rollout canary có kiểm soát |
| **REJECT / ROLLBACK** | Bác bỏ giả thuyết hoặc vi phạm guardrail | Quay lại known-good configuration |
| **INCONCLUSIVE** | Thiếu samples, nhiều confounders hoặc các runs mâu thuẫn | Thiết kế lại thí nghiệm và đo lại |

**ACCEPT FOR CANARY ≠ triển khai mọi production nodes ngay lập tức.** Canary xác nhận workload thực và heterogeneous hosts; quyết định rollout sau đó phải theo production SLO và operational policy.

### 19.1. Mẫu bảng quyết định

~~~text
Hypothesis ID:
Variant A baseline ID:
Variant B candidate ID:
Only intended variable changed:
Run count:
Traffic profile:
Warm-up completed:
Primary KPI delta:
Confidence/noise assessment:
Error rate:
CPU/cgroup throttling:
RSS/memory limit:
GC/JFR evidence:
Correctness checks:
Decision:
Reasoning:
Reviewer:
Rollback command/config reference:
~~~

Một bản report tốt khiến người mới trong team có thể hiểu **tại sao giữ flag** và lặp lại khi upgrade JDK.

## 20. Canary và rollback: phần bắt buộc để giảm rủi ro

Một candidate đạt benchmark trong staging có thể vẫn fail trong production vì traffic mix, data distribution, CPU contention hoặc server variability.

Canary protocol điển hình:

1. Triển khai trên một phần nhỏ instances/pods có version/config rõ ràng.
2. Giữ nhóm control A và candidate B chạy cùng khoảng thời gian/traffic profile phù hợp.
3. Gắn variant ID vào metrics/logs/traces.
4. Theo dõi p99, error, throughput, RSS, CPU throttling, GC và crashes.
5. Xác định trước guardrail breach triggers.
6. Nếu không đạt, rollback về config artifact/digest đã lưu.
7. Nếu đạt sau window đủ dài, tăng traffic theo từng stage có review.
8. Theo dõi sau full rollout vì long-running GC/memory conditions có thể khác staging.

### 20.1. Rollback phải tái lập được

Lưu bản config **known-good** với:

- JDK build/image digest.
- JVM startup arguments.
- CPU/memory limits.
- App version/config revision.
- Deployment descriptors.
- Database/config dependencies nếu có.
- Một lệnh hoặc pipeline để trả về trạng thái A.
- Người/nhóm quyết định rollback và monitoring window.

Không rollback chỉ bằng “đổi -Xmx về số cũ” nếu trong candidate deployment đồng thời đã cập nhật JDK, app build hoặc resource requests.

### 20.2. Không chỉ dùng cảnh báo CPU/RSS muộn

Một canary có thể tăng tail latency/error rate trước khi CPU/RSS chạm hard limit. Thiết kế alert theo **business SLO, error budget và process stability**; memory/CPU metrics là diagnostics và guardrails hỗ trợ.

## 21. Tình huống giả thuyết H1: thiếu heap headroom

**Observation:** Application có allocation rate cao, GC thường xuyên, G1 pause events trùng p99 spikes, heap gần Xmx.

**Hypothesis:** Tăng hard heap ceiling có thể tạo headroom, giảm GC frequency và tail latency.

**One-variable intervention:** -Xmx1024m → -Xmx1536m; giữ Xms, GC, app build, CPU quota, workload và profiling giống nhau.

**Evidence:**

- MaxHeapSize của process mới thực sự tăng?
- RSS vẫn dưới container memory limit?
- GC frequency/pauses có giảm?
- P99 end-to-end có giảm tương ứng?
- Live set có đang tăng vì leak logic không?

**Reject nếu:** RSS vượt budget, p99 không cải thiện lặp lại, error increases hoặc GC không phải dominant cause.

**Lưu ý:** Live set tăng vô hạn vì static cache leak không được chữa bằng việc tăng Xmx; chỉ trì hoãn sự cố.

## 22. Tình huống giả thuyết H2: CPU quota và GC/JIT ergonomics

**Observation:** Containers có CPU quota thấp, process liên tục CPU throttled; compiler/GC threads có thể cạnh tranh CPU với request threads.

**Hypothesis:** JVM nhìn nhận active processor count không phù hợp, làm chọn concurrency levels gây contention.

**Intervention có kiểm soát:** nghiên cứu **-XX:ActiveProcessorCount** với một giá trị phù hợp để so A/B, nếu runtime build/platform hỗ trợ.

**Measurement:**

- Runtime.availableProcessors() và VM flags trước/sau.
- GC parallel/concurrent thread sizing.
- Compiler queue/warm-up.
- CPU quota/throttling.
- Request throughput và p99.
- Errors/timeouts và heap/GC progress.

**Reject nếu:** throughput bị giảm do quá ít workers/GC tiến chậm, p99 không cải thiện hoặc cấu hình ảnh hưởng negative tới workload.

Không kết luận rằng ActiveProcessorCount thay **CPU quota thực của container**: nó điều chỉnh JVM perceived processors, không tự sửa cgroup quota do orchestrator áp đặt.

## 23. Tình huống giả thuyết H3: G1 pause goal

**Observation:** GC logs cho thấy GC pause distribution có tails gây SLO breach, trong khi memory/cpu headroom còn.

**Hypothesis:** G1 pause target khác giúp giảm per-pause latency.

**Intervention:** chỉ thay **-XX:MaxGCPauseMillis**; ví dụ goal 200 → 100, giữ các flags khác và tải giống nhau.

**Measurement:**

- p95/p99 STW pause durations.
- Số collections và elapsed GC work.
- Mutator throughput.
- Application p99/error rate.
- CPU and memory pressure.
- G1 young sizing/occupancy behavior.

**Reject nếu:** pause goal mới làm GC frequency/CPU tăng và app p99/throughput xấu đi, hoặc pause goal không đạt do bottleneck khác.

**Cạm bẫy:** goal là **soft target**. Chỉ nhìn 100ms trong flag không chứng minh mỗi pause thực dưới 100ms.

## 24. Tình huống giả thuyết H4: Native memory và stack

**Observation:** Container memory usage tăng và OOMKilled dù Java heap used chưa sát Xmx.

**Hypothesis:** native memory của thread stacks, direct buffers, Metaspace hoặc GC/JIT structures chiếm phần lớn headroom.

**Điều tra trước flags:**

- RSS/cgroup working set.
- NMT summary nếu đã bật đồng nhất.
- Thread count/stacks.
- Classloader/metaspace trend.
- Direct buffer allocation policies.
- Heap committed/reserved/used.
- JVM container memory detection.

**One-variable intervention** chỉ khi đã thấy bottleneck. Ví dụ điều chỉnh **-Xss** có rủi ro StackOverflowError và ảnh hưởng recursion depth; không hạ tùy tiện. Hoặc điều chỉnh process thread pool capacity có thể là **application config experiment**, cần phân biệt nó với JVM flag experiment.

### 24.1. Tại sao không đặt -Xmx bằng 100% container limit?

Vì process còn native allocations. Budget cần đủ heap + native + overhead + bursts + safety margin; tỷ lệ cụ thể phụ thuộc workload và native usage measured, không nên dùng một “magic percentage” áp mọi ứng dụng.

## 25. Tình huống giả thuyết H5: JIT/code cache

**Observation:** Throughput giảm sau warm-up, compiler logs cảnh báo code cache full, compilation disabled hoặc JIT tasks backlog.

**Hypothesis:** Code cache quá nhỏ hoặc compilation budget không phù hợp, làm methods tiếp tục chạy interpreted/C1 tier.

**Intervention:** chỉ đổi **ReservedCodeCacheSize** sau khi đã xác nhận nó là bottleneck; không tăng đồng thời CICompilerCount và Xmx.

**Measurement:**

~~~bash
jcmd <PID> Compiler.codecache
jcmd <PID> Compiler.queue
jcmd <PID> Compiler.codelist
~~~

- Code heaps occupancy, segmentation, fragmentation.
- JIT compile queue và threads CPU.
- Warm-up duration, steady-state throughput.
- Process RSS/native memory.
- Application p99/error rates.

**Reject nếu:** cache vốn đủ, throughput không đổi hoặc native memory overhead vượt budget. Một method hot không nhất thiết được biên dịch chỉ vì tăng code cache; có thể do policy/profiling/code shape.

## 26. Khi nào KHÔNG nên tune JVM?

Không dùng JVM flags để che những vấn đề sau nếu có bằng chứng trực tiếp:

1. **N+1 SQL queries** khiến DB latency chi phối request.
2. **Static cache không giới hạn** giữ objects forever.
3. **Unbounded thread creation** khiến stacks/native RSS tăng.
4. **Network retries không có budget** làm tail latency phình.
5. **Synchronized lock giữ qua I/O** làm hàng đợi request.
6. **Future.get trên single-thread executor** gây thread-pool starvation.
7. **Allocation churn do tạo objects không cần thiết** có thể tối ưu logic.
8. **Request payload distribution đổi** làm benchmark mất tương đồng.
9. **Logging synchronous quá nặng** gây I/O stalls.
10. **Autoscaling/redeployment thay đổi tài nguyên** mà thí nghiệm chưa kiểm soát.

JVM tuning có thể hữu ích **sau khi** tối ưu application/code/dependencies, nhưng nó không phải phương án thay thế chữa root cause.

## 27. Những lỗi thực nghiệm thường gặp

1. **Thay 5 flags cùng lúc** rồi cho rằng biết flag nào hiệu quả.
2. **Không lưu baseline command line** nên không thể rollback.
3. **Chỉ lưu desired YAML**, bỏ qua effective JVM flags và JAVA_TOOL_OPTIONS.
4. **So JDK17 build A với JDK21 build B** mà gọi là chỉ thay heap.
5. **Thay CPU limit cùng lúc đổi GC**, làm kết quả bị confounded.
6. **Không warm-up** dù đo steady-state throughput.
7. **Warm-up baseline và candidate khác nhau**.
8. **Chỉ chạy một lần**, bỏ qua run-to-run noise.
9. **Không kiểm tra correctness/error rate**, cho phép candidate bỏ requests mà vẫn có RPS cao.
10. **Dùng mean khi SLA là p99**.
11. **Coi GC pause p99 là toàn bộ request p99**.
12. **Lấy một GC log từ một thời điểm khác** để giải thích incident.
13. **Đo RSS ở một run, heap used ở run khác rồi so trực tiếp**.
14. **Cộng reserved memory như physical RSS**.
15. **Bật JFR/NMT ở B nhưng không ở A**, thêm instrumentation confounder.
16. **Bật verbose GC debug log một phía**, làm overhead khác nhau.
17. **Không lưu dataset revision và traffic mix**.
18. **Không ghi random seed**, khiến tải ngẫu nhiên không tái lập được.
19. **Không ghi load generator version/capacity**, để client thành bottleneck.
20. **So A sáng thứ Hai với B tối thứ Sáu** khi traffic/downstream khác.
21. **Tối ưu p99 nhưng RSS OOMKilled**, bỏ qua memory guardrail.
22. **Đặt -XX:MaxGCPauseMillis như hard SLA**.
23. **Cho rằng tăng Xmx đồng nghĩa toàn process memory không đổi**.
24. **Dùng -Xint hoặc -Xcomp như default production tuning không benchmark**.
25. **Tin kết quả của một micro-loop có thể bị JIT eliminate**.
26. **Không lưu SHA-256 build**, khiến candidate chạy JAR khác.
27. **Không kiểm tra configuration precedence** giữa Xmx và MaxRAMPercentage.
28. **Bỏ qua container cgroup CPU quota**, đo GC threads trên host cores.
29. **Thấy GC concurrent CPU rồi nhầm là toàn application STW**.
30. **Chạy profiling quá nặng khiến kết quả production không đại diện**.
31. **Không phân biệt cold-start và warm steady-state runs**.
32. **Đặt thresholds sau khi nhìn candidate**, tạo bias.
33. **Chỉ báo percent improvement không có absolute SLO**.
34. **Không đo timeout/errors khi load tăng**.
35. **Không lưu raw histograms nên không thể tái tính percentiles**.
36. **Báo statistically significant dù chưa kiểm tra autocorrelation**.
37. **Bỏ qua độ phân tán giữa independent runs**.
38. **Quyết định dựa trên một đồ thị không có units**.
39. **Không ghi phiên bản OS/container image**, khó tái lập.
40. **Không có rollback route đã kiểm thử**.
41. **Canary không có control hoặc không gắn variant labels**.
42. **Tăng traffic canary quá nhanh** trước khi long-running memory behavior xuất hiện.
43. **Gắn mọi sự cố CPU/RSS cho JVM** mà bỏ qua application/native libraries.
44. **Không tái kiểm định sau JDK upgrade**, giả định flags semantics/defaults bất biến.
45. **Không biết khi nào experiment inconclusive**, ép mọi run thành success/failure.

## 28. Bài tập thực hành có lời giải

### Bài 1 — Xác định biến can thiệp

Baseline: G1, Xms=256m, Xmx=1g. Candidate: G1, Xms=512m, Xmx=2g.

**Đáp án:** Có **hai thay đổi thủ công** Xms và Xmx. Chưa cô lập tác động của Xmx. Muốn kiểm tra giả thuyết heap ceiling, chỉ đổi Xmx và giữ Xms.

### Bài 2 — Cải thiện trung bình nhưng p99 xấu

A mean=20ms, p99=80ms; B mean=18ms, p99=150ms, SLO p99≤100ms.

**Đáp án:** B không đạt SLO dù mean tốt. Không accept chỉ vì mean.

### Bài 3 — RSS budget

Container limit=2 GiB; đặt Xmx=2 GiB và thấy OOMKilled.

**Đáp án:** Process còn Metaspace, code cache, stacks, GC/native memory. Cần đo RSS/NMT, dành native headroom và kiểm tra cgroup semantics; không chỉ tăng Xmx.

### Bài 4 — G1 pause target

Từ 200ms đổi 100ms, các GC pauses có chắc đều ≤100ms?

**Đáp án:** Không. MaxGCPauseMillis là soft target, không hard realtime guarantee; phải đo actual pauses và throughput.

### Bài 5 — JIT warm-up

A được chạy 30 phút mới đo, B được đo ngay sau startup.

**Đáp án:** Không công bằng cho steady-state comparison. Cần cùng protocol và warm-up criterion.

### Bài 6 — Repeated runs

Có một run A=120ms, B=112ms, noise giữa runs ±20ms.

**Đáp án:** Chưa đủ tin B cải thiện. Lặp lại, so distributions và confounders; quyết định INCONCLUSIVE nếu không chắc.

### Bài 7 — CPU quota

Container cấp 2 vCPU nhưng ActiveProcessorCount=8.

**Đáp án:** JVM có thể dùng perceived processors cho ergonomics khác effective quota. Xem GC/JIT thread settings, CPU throttling và thử nghiệm có kiểm soát.

### Bài 8 — Xmx và MaxRAMPercentage

Đặt đồng thời -Xmx1024m và -XX:MaxRAMPercentage=75. Kết luận max heap 75% container?

**Đáp án:** Không. Xmx explicit có precedence phù hợp trong HotSpot; xem effective MaxHeapSize thực.

### Bài 9 — JFR overhead

A không bật JFR, B bật profile.jfc. Có thể so CPU trực tiếp không?

**Đáp án:** Có instrumentation confounder. Giữ profile settings giống nhau hoặc tách vòng diagnostics.

### Bài 10 — Build khác nhau

A dùng commit C1, B dùng C2, đồng thời đổi Xmx.

**Đáp án:** Không thể quy thay đổi metrics riêng cho Xmx. Cần cùng binary digest hoặc thiết kế experiment gọi rõ package-level changes.

### Bài 11 — H1 không được xác nhận

Tăng Xmx giúp GC frequency giảm nhưng p99 gần như không đổi và DB traces vẫn 95% request time.

**Đáp án:** Giả thuyết “GC đang chi phối p99” không được ủng hộ; tối ưu DB trước, có thể không cần tăng heap.

### Bài 12 — Thread pool starvation

Many threads WAITING trên Future.get, Executor có một worker đang chờ task được queued vào chính executor.

**Đáp án:** Đây là liveness bug/pool starvation; tăng heap/đổi collector không chữa. Sửa dependency/architecture.

### Bài 13 — Throughput gate

Baseline 980 req/s, B=970 req/s. B giữ ≥98% throughput không?

**Đáp án:** Có, 970/980×100≈98.98%.

### Bài 14 — P99 improvement

A p99=120ms, B p99=85ms. Improvement bao nhiêu?

**Đáp án:** (120−85)/120≈29.17%.

### Bài 15 — Candidate nhanh nhưng crash

B p99 tốt hơn 30% nhưng error rate 5% thay vì baseline 0.1%.

**Đáp án:** Reject/rollback theo guardrail correctness/availability.

### Bài 16 — Trạng thái reserved

NMT Code reserved=240MiB, committed=40MiB. Có chắc code cache đang dùng 240MiB RSS?

**Đáp án:** Không; reserved virtual memory khác committed/physical RSS.

### Bài 17 — Thiếu manifest

Chỉ có launch command nhưng không có OS, dataset, CPU quota và build digest.

**Đáp án:** Chưa đủ để tái lập; bổ sung environment, workload, artifacts và effective flags.

### Bài 18 — Candidate đã vượt guardrail

P99 goal đạt, nhưng peak RSS > memory limit.

**Đáp án:** Reject hoặc dừng rollout/rollback; không bỏ guardrail chỉ vì một KPI tốt.

### Bài 19 — Đổi collector

A G1, B ZGC; các flags G1-specific vẫn được giữ nguyên.

**Đáp án:** Phải kiểm tra flags support/collector-specific interactions; đây là collector experiment, nên có configuration review và same resource/workload controls, không copy mù.

### Bài 20 — Canary và quyết định

Staging test B đạt tất cả gates. Có nên full rollout ngay không?

**Đáp án:** Nên thử canary có control, rollback guardrails và measurement windows phù hợp, sau đó mới mở rộng.

### Bài 21 — Histogram p99

A có một triệu requests; B có 200 requests nhưng báo p99.9 giảm mạnh.

**Đáp án:** Khả năng ước lượng tail quá thiếu dữ liệu. Không dùng trực tiếp p99.9 của 200 requests để kết luận.

### Bài 22 — GC log

GC pauses A thấp, nhưng request p99 tăng nhiều. Có thể quy ngay cho GC không?

**Đáp án:** Không. Xem DB, network, locks, queues, CPU throttling và tracing timeline.

### Bài 23 — Nhầm căn nguyên

Code cache full được xác nhận, nhưng người dùng tăng -Xmx.

**Đáp án:** Xmx điều chỉnh Java heap, không trực tiếp code cache. Điều tra ReservedCodeCacheSize, compiled nmethods, JIT churn.

### Bài 24 — Time series drift

Candidate chạy trên node ít tải hơn baseline.

**Đáp án:** Confounded environment. Cần matched hosts, randomized order hoặc A/B đồng thời có controls phù hợp.

### Bài 25 — Khi nào chọn INCONCLUSIVE?

Metrics giữa runs mâu thuẫn, raw data thiếu và tool instrumentation không giống nhau.

**Đáp án:** INCONCLUSIVE; hoàn thiện instrumentation/protocol và thử lại, không gán success/failure tùy cảm giác.

## 29. Checklist trước, trong và sau mỗi experiment

### Trước khi chạy

1. Mô tả symptom bằng metrics có đơn vị và timestamps.
2. Viết một hypothesis có cơ chế giải thích, predicted outcome và falsification rule.
3. Chọn một biến intervention, ghi chính xác before/after.
4. Chọn primary KPI và tất cả guardrails.
5. Khóa source commit/artifact hash và external config revision.
6. Khóa JDK vendor/build/image/OS theo khả năng.
7. Ghi CPU/memory quotas, cpusets, node class và dependencies.
8. Ghi workload mix, RPS, concurrency, dataset, random seed.
9. Ghi instrumentation/log settings giống nhau giữa A/B.
10. Định nghĩa warm-up/cold-start và measurement windows.
11. Chọn số replications và run order giảm time drift.
12. Chuẩn bị rollback/safety stop thresholds.

### Trong khi chạy

1. Tạo unique run ID, variant ID và timestamp.
2. Lưu launch command và effective VM.command_line/VM.flags.
3. Kiểm tra actual heap/CPU limits trước load.
4. Thu request histograms và error/timeout counts.
5. Thu GC/safepoint logs nếu hypothesis liên quan.
6. Thu JFR/NMT nếu instrumentation phù hợp và đối xứng.
7. Theo dõi CPU throttling, RSS, process crashes, GC health.
8. Giữ không thay app build/traffic mix giữa run tương ứng.
9. Nếu một guardrail khẩn cấp vượt ngưỡng, dừng/rollback an toàn.
10. Log deviations và anomalies, không xóa runs xấu.

### Sau khi chạy

1. Kiểm tra tất cả runs có kết thúc đúng và đủ raw samples.
2. Tính p50/p95/p99 bằng cùng thuật toán/histogram units.
3. So run-level variability và confounders.
4. Kiểm tra correctness/error rate trước performance conclusions.
5. So guardrails về CPU/RSS/throughput/latency/startup.
6. Diễn giải log/JFR theo mechanism hypothesized.
7. Ghi ACCEPT FOR CANARY, REJECT hoặc INCONCLUSIVE.
8. Ghi lý do, reviewer, uncertainty và related evidence paths.
9. Lưu config và artifact hashes vào version-controlled repository an toàn.
10. Đảm bảo secrets được redact và quyền truy cập evidence thích hợp.
11. Thực hiện canary hoặc rollback theo quyết định.
12. Theo dõi dài hạn và đánh giá lại khi workload/JDK thay đổi.

## 30. Tài liệu tham khảo chính thức và thực hành

- **Oracle JDK 17 — java Command, JVM options, GC, container sizing, PrintFlagsFinal:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **Oracle JDK 17 — jcmd, VM.command_line, VM.flags, GC.heap_info, JFR.start, VM.native_memory:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- **Oracle JDK 17 — G1 Garbage-First Collector:** https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html
- **Oracle JDK 17 — G1 Garbage Collector Tuning:** https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-garbage-collector-tuning.html
- **Oracle JDK 17 — HotSpot Garbage Collection Tuning Guide:** https://docs.oracle.com/en/java/javase/17/gctuning/
- **Oracle JDK 17 — Java Flight Recorder (jfr command):** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html
- **Oracle JDK 17 — Native Memory Tracking:** https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
- **Oracle JDK 17 — JFR API overview:** https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/module-summary.html
- **OpenJDK JMH — Java Microbenchmark Harness:** https://openjdk.org/projects/code-tools/jmh/
- **OpenJDK JMH source/documentation:** https://github.com/openjdk/jmh
- **Java SE 17 — RuntimeMXBean:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/RuntimeMXBean.html
- **Java SE 17 — Runtime.maxMemory/availableProcessors:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html
- **Red Hat — Java 17 OpenJDK container awareness, Xmx/MaxRAMPercentage precedence:** https://developers.redhat.com/articles/2022/04/19/java-17-whats-new-openjdks-container-awareness
- **ops.java — Tuning G1, version-specific best practices:** https://ops.java/performance/jvm/articles/tuning-g1/

## 31. Kết luận

Điều chỉnh tài nguyên JVM hiệu quả không có dạng “thử nhiều flags đến khi benchmark đẹp”. Nó là một vòng lặp có thể kiểm chứng:

1. **Quan sát** triệu chứng bằng các metrics đúng với SLO.
2. **Giả thuyết** về cơ chế gây bottleneck, có cách bác bỏ.
3. **Thay đúng một biến có chủ đích**, giữ application/JDK/workload/environment trong phạm vi kiểm soát.
4. **Đo nhiều lần** với warm-up, raw histograms, CPU/RSS/GC/JIT diagnostics và guardrails.
5. **Ra quyết định có điều kiện**: chấp nhận để canary, bác bỏ/rollback hoặc inconclusive/đo lại.
6. **Lưu manifest và effective configuration**: flags thực, JDK build, artifact digest, container limits, dataset, lệnh chạy, logs, timeline, reviewer và rollback route.

**Nguyên tắc quyết định:** Một cấu hình tốt không chỉ cải thiện một con số. Nó phải **đúng chức năng**, ổn định dưới workload phù hợp, không vi phạm ngân sách CPU/bộ nhớ và **có thể tái lập, kiểm toán, rollback** khi hệ thống hoặc JDK thay đổi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-configuration-010">Nâng cấp JDK: kiểm tra cờ bị đổi, bị xóa và thay đổi mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Nâng cấp JDK: kiểm tra cờ bị đổi, bị xóa và thay đổi mặc định

## 1. Bài toán: source không đổi nhưng cấu hình JVM có thể khác

Nâng cấp từ JDK 8, 11, 17 hoặc 21 lên JDK mới không đơn thuần là đổi `JAVA_HOME`. Một chương trình có thể vẫn biên dịch, nhưng JVM khởi động khác đi, GC pauses và heap sizing thay đổi, log parser không còn hiểu dữ liệu, hoặc process **không chạy được trước khi vào `main`** vì một cờ đã bị xóa.

Ba câu hỏi cần trả lời khi nâng JDK:

1. **Flag availability:** option cũ có được JVM mới nhận và còn có hiệu lực không?
2. **Effective defaults:** nếu bỏ flags hoặc không đặt explicit value, JVM mới thực sự chọn giá trị nào?
3. **Observed behavior:** startup, throughput, P95/P99 latency, heap/native RSS, GC và diagnostics trên cùng workload thay đổi thế nào?

Không có một danh sách defaults đúng cho mọi nơi. Cấu hình phụ thuộc **JDK release và build, VM vendor (HotSpot/OpenJ9…), OS, CPU architecture, cgroup/container quotas, launch arguments và ergonomics**.

**Mục tiêu bài học:** lập inventory JVM flags, phân biệt supported/deprecated/obsolete/removed, so sánh `PrintFlagsFinal` và GC logs, kiểm thử startup có chủ đích, xây canary matrix và rollback plan. Không “cứ đổi JDK rồi bật `IgnoreUnrecognizedVMOptions` để process chạy”.

## 2. Bốn loại thay đổi rất dễ bị nhầm

Theo **Oracle JDK 25 `java` manual**, vòng đời flags liên quan:

| Trạng thái | JVM chấp nhận cờ? | Còn tác động? | Việc cần làm |
| --- | --- | --- | --- |
| **Supported** | Có trên platform/build phù hợp | Có theo documented semantics | Kiểm tra effective value |
| **Deprecated** | Thường vẫn nhận | **Có** nhưng có warning | Đổi sang replacement có chứng cứ |
| **Obsolete** | Có thể nhận rồi bỏ qua | **Không** còn tác động | Xóa khỏi scripts, kiểm tra default mới |
| **Removed** | Thường **không nhận** | Không | Sửa trước khi deploy, vì startup có thể fail |
| **Changed default/semantics** | Có thể vẫn nhận mà không warning | Có nhưng chính sách hoặc value khác | Snapshot + đo runtime |

### Deprecated chưa phải removed

Trong Oracle JDK 25, `-Xloggc:filename` được liệt kê **deprecated**, và tài liệu khuyến nghị `-Xlog:gc:filename`. Đây không phải cờ bị remove trong JDK 25. Nếu pipeline dùng cờ cũ, chuyển sang Unified Logging trước khi cờ hết vòng đời, nhưng không ghi sai rằng cờ lập tức không chạy được.

### Obsolete không đồng nghĩa còn hiệu lực

`--illegal-access=...` được Oracle JDK 25 liệt kê obsolete. JVM có thể chấp nhận và cảnh báo, nhưng tùy chọn **không còn mở reflective access như các release cũ**. Muốn sửa lỗi strong encapsulation, hãy dùng API được hỗ trợ hoặc đánh giá `--add-opens` cụ thể, không dựa vào một option ignored.

### Removed có thể chặn JVM trước `main`

Oracle JDK 25 liệt kê các flags **RTM** bị removed:

~~~text
-XX:+UseRTMLocking
-XX:+UseRTMDeopt
-XX:RTMAbortRatio=...
-XX:RTMRetryCount=...
~~~

Chúng có thể tạo `Unrecognized VM option` và khiến process exit. Đây là ví dụ JDK 25 **có nguồn tài liệu rõ**, không phải khẳng định mọi legacy flag đều trả cùng một warning.

### Silent default change mới là điều khó phát hiện

Một service có thể start bình thường nhưng default GC, heap sizing, GC threads hoặc compilation policy khác. **Không warning không chứng minh behavior giữ nguyên**. Đặc biệt khi ứng dụng không truyền `-Xmx`, heap max có thể do physical RAM, cgroup memory limit và `MaxRAMPercentage` quyết định.

## 3. Cơ sở quan sát: đúng Java executable trước đã

Trước khi so flags, kiểm tra:

~~~bash
java -version
javac -version
java -XshowSettings:vm -version
~~~

Phân biệt:

- `java` là launcher/runtime đang chạy.
- `javac` là compiler; PATH có thể trỏ sang JDK khác.
- `--release 17` của `javac` chỉ kiểm soát target API/class-file, **không** làm GC/JIT của JVM JDK 25 trở thành JDK 17.
- `-Dkey=value` tạo system property; `-XX:...` điều khiển HotSpot VM policy; chúng không thuộc cùng nhóm.

Dùng absolute path khi có nhiều JDK:

~~~bash
/opt/jdks/jdk17/bin/java -version
/opt/jdks/jdk25/bin/java -version
~~~

**Đường dẫn ví dụ**, hãy thay bằng path có thật. Trên PowerShell:

~~~powershell
& 'C:\path\to\jdk17\bin\java.exe' -version
& 'C:\path\to\jdk25\bin\java.exe' -version
~~~

Khi chạy qua systemd, Docker, Kubernetes hay CI, phải kiểm tra Java path từ **đúng môi trường triển khai**, không suy từ terminal local.

## 4. Inventory từ mọi nguồn startup

Một Java process có thể nhận cờ từ nhiều nơi:

~~~text
Docker ENTRYPOINT/CMD
        |
        +-- shell start script
        +-- JAVA_TOOL_OPTIONS
        +-- JDK_JAVA_OPTIONS
        +-- Kubernetes Helm values / ConfigMap
        +-- systemd service / Windows Service wrapper
        +-- buildpack / managed runtime launcher
        +-- APM javaagent / instrumentation
        +-- application-specific JVM opts
        |
        v
Effective Java process arguments
~~~

**`JAVA_TOOL_OPTIONS` và `JDK_JAVA_OPTIONS` không đồng nghĩa**; chúng được VM/launcher xử lý theo documented rules khác nhau. Không ghép chuỗi mù quáng và coi mọi argument được parse như nhau.

Tạo bảng inventory:

| Flag / option | Nguồn cấu hình | Giá trị hiện tại | Mục tiêu ban đầu | Quyết định khi nâng |
| --- | --- | --- | --- | --- |
| `-Xmx` | Helm chart | Theo config | Max heap | Xác nhận cgroup headroom |
| `-XX:+UseG1GC` | Docker entrypoint | Explicit | GC policy | Kiểm tra có cần giữ không |
| `-Xloggc` | legacy wrapper | File path | GC log | Chuyển `-Xlog` |
| `-XX:+UseConcMarkSweepGC` | script rất cũ | Enabled | Low-pause GC | Loại bỏ/rebenchmark |
| `--add-opens` | application wrapper | Package cụ thể | Reflection | Test framework/library compatibility |

**Không chia sẻ raw env/flags công khai** vì option có thể chứa connection URLs, credentials hay secrets. Chỉ lưu trữ báo cáo đã redaction.

## 5. Thu snapshot flags trên HotSpot

Các command hữu ích:

~~~bash
java -XX:+PrintFlagsFinal -version
java -XX:+PrintCommandLineFlags -version
java -XshowSettings:vm -version
java -XshowSettings:system -version
java -Xlog:help
~~~

### Các nguồn gốc giá trị

`PrintFlagsFinal` liệt kê tên, type, value và thường có metadata về origin như `{default}`, `{ergonomic}`, `{command line}` (format tùy build).

- **Default:** build/VM đặt sẵn.
- **Ergonomic:** VM tự chọn theo environment và policy.
- **Command line:** override explicit.
- **Nguồn khác:** environment/management/internal theo khả năng VM.

Hai JVM có thể cùng flag name nhưng khác value hoặc origin. **Đừng chỉ so giá trị hardcode trong Dockerfile** và bỏ qua policy JVM chọn lúc runtime.

### Linux/macOS — lưu snapshot

~~~bash
java -version > version.txt 2>&1
java -XX:+PrintFlagsFinal -version > flags.txt 2>&1
java -XX:+PrintCommandLineFlags -version > selected.txt 2>&1
java -XshowSettings:vm -version > vm-settings.txt 2>&1
~~~

### Windows PowerShell — lưu snapshot

~~~powershell
java -version 2>&1 | Out-File -Encoding utf8 version.txt
java -XX:+PrintFlagsFinal -version 2>&1 | Out-File -Encoding utf8 flags.txt
java -XX:+PrintCommandLineFlags -version 2>&1 | Out-File -Encoding utf8 selected.txt
java -XshowSettings:vm -version 2>&1 | Out-File -Encoding utf8 vm-settings.txt
~~~

**PowerShell 5.1 có nuances với stderr/encoding**; để compare tự động, dùng đồng nhất tool/shell và normalize line endings khi trình bày. Không sửa nội dung nguồn logs nếu cần giữ làm chứng cứ.

## 6. So sánh old/new JDK có thể tái lập

Trên Linux/macOS, giả sử đã có đường dẫn JDK thật:

~~~bash
/opt/jdks/jdk17/bin/java -XX:+PrintFlagsFinal -version > flags-17.txt 2>&1
/opt/jdks/jdk25/bin/java -XX:+PrintFlagsFinal -version > flags-25.txt 2>&1
diff -u flags-17.txt flags-25.txt
~~~

Đường dẫn vẫn chỉ là ví dụ.

Raw diff có ích nhưng nhiều **false positives** vì flags bổ sung/bỏ, reordering, origin format và build IDs. Một parser chuẩn hơn nên nhóm theo **flag name**, rồi tạo các tập:

1. **Only-old:** flag không còn trong snapshot mới.
2. **Only-new:** mới xuất hiện.
3. **Changed value:** cùng tên nhưng value khác.
4. **Changed origin:** cùng value nhưng nguồn default/ergonomic/command khác.
5. **Same effective value:** không thấy thay đổi ở một cấu hình cụ thể.

**Only-old chưa tự chứng minh removed:** có thể flag diagnostic bị ẩn khi không unlock hoặc platform không hỗ trợ. Phải kết hợp release notes và thử startup.

### Không so hai environments khác nhau rồi kết luận do JDK

Nếu JDK 17 chạy trên bare metal 32 CPUs/64 GiB còn JDK 25 chạy trong container 2 CPUs/2 GiB, nhiều GC/heap values sẽ khác do **resource environment**, không chỉ JDK version. Nên chạy cả hai với cùng limits và workload.

## 7. Các nhóm flags tài nguyên phải xem đầu tiên

| Nhóm | Những flags/khái niệm trọng yếu | Kiểm tra |
| --- | --- | --- |
| Heap limit | `-Xmx`, `MaxHeapSize` | Effective max heap |
| Initial heap | `-Xms`, `InitialHeapSize` | Startup footprint |
| RAM-aware sizing | `MaxRAMPercentage`, `InitialRAMPercentage`, `MinRAMPercentage` | Cgroup/physical RAM policy |
| CPU-aware sizing | `ActiveProcessorCount` | JIT/GC/concurrency ergonomics |
| Container awareness | `UseContainerSupport` | Cgroup limits đúng không |
| GC selection | `UseG1GC`, `UseParallelGC`, `UseZGC`, `UseSerialGC` | Collector thực dùng |
| GC workers | `ParallelGCThreads`, `ConcGCThreads` | CPU budget |
| Pause target | `MaxGCPauseMillis` | Soft target, không là hard SLA |
| Stack | `-Xss` / `ThreadStackSize` | Native memory/recursion depth |
| Metaspace | `MaxMetaspaceSize` | Class metadata |
| Code cache | `ReservedCodeCacheSize` | JIT compiled-code memory |
| JIT | `TieredCompilation`, `TieredStopAtLevel` | Warmup/throughput |
| GC logging | `-Xlog:gc*` | Parser/rotation |
| CDS/AOT | `SharedArchiveFile`, `AOTCache` (JDK tương ứng) | Startup artifacts |
| Native memory | `NativeMemoryTracking` | NMT overhead và diagnostics |

**Một flag xuất hiện trong bảng không đảm bảo nó được mọi JVM/vendor/JDK hỗ trợ.** `-XX` nói chung là implementation-specific, không phải contract của JLS/JVMS.

### `-Xmx` không phải RSS

Heap max chỉ là một thành phần memory. Còn Metaspace, code cache, thread stacks, direct buffers, JNI/native allocations, GC structures. Đừng đặt `-Xmx` bằng đúng container memory limit và kỳ vọng process không OOMKilled.

## 8. Một thay đổi lịch sử quan trọng: G1 trở thành GC mặc định

Oracle Migration Guide và OpenJDK **JEP 248** mô tả từ JDK 9, **G1 GC** trở thành default trong cấu hình HotSpot server-class thông dụng, thay cho default **Parallel GC** của JDK 8.

| Thời kỳ điển hình | Collector default | Ảnh hưởng đáng kiểm |
| --- | --- | --- |
| JDK 8 server-class | Parallel GC | Throughput và stop-the-world pauses |
| JDK 9+ server-class | G1 GC | Region-based collection, concurrent work và pause goals |

**Không khẳng định** “mọi Java 9+ process đều dùng G1”. VM có thể chọn collector khác theo resource sizing, cấu hình nhỏ hoặc explicit flag; vendor khác còn có GC khác.

### Cách quan sát

~~~bash
java -Xlog:gc=info -version
java -XX:+PrintFlagsFinal -version
~~~

Một `java -version` rất ngắn có thể không gây actual GC pause nên ít log. Chạy workload test phù hợp hoặc quan sát GC MXBeans/JFR/JMX khi cần.

Nếu ứng dụng dùng CMS hoặc tuning Parallel từ JDK 8, **không đổi tên collector rồi giữ nguyên mọi tuning knob**. Đo heap occupancy, p99 pauses, CPU, allocation rate và throughput của collector mới.

## 9. CMS đã bị remove ở JDK 14

OpenJDK **JEP 363** loại bỏ **Concurrent Mark Sweep (CMS)** trong JDK 14. Một launch script cũ có:

~~~bash
java -XX:+UseConcMarkSweepGC -version
~~~

**không còn là cách chọn CMS hợp lệ** trên JDK 25. Tùy giai đoạn/VM build, option cũ có thể được báo obsolete/ignored hoặc không được nhận. Không gắn một exact error message cho mọi JDK, nhưng tuyệt đối **không** viết README rằng CMS vẫn available trên HotSpot 25.

Cách xử lý đúng:

1. Xác định **mục tiêu** cũ: giảm pause hay throughput.
2. Bỏ flags CMS-specific.
3. Đánh giá G1, ZGC hoặc collector khác **được target build hỗ trợ**.
4. Đặt heap limits, có GC logs.
5. Benchmark production-like workload, không giả định một-to-one mapping.

Một ví dụ hiểu nhầm khác: `MaxPermSize`/`PermSize` là flags của **Permanent Generation đã bị loại từ JDK 8**; nếu script legacy còn chúng, cảnh báo/ignore không có nghĩa JVM vẫn dùng PermGen. Metadata hiện đại thuộc Metaspace với sizing policy khác.

## 10. Chuyển GC logging sang Unified Logging

Với **JEP 158** và **JEP 271**, HotSpot dùng `-Xlog`, cho phép tags, levels, decorators và output.

| Cấu hình đời cũ | Cách thay thế thường dùng | Lưu ý |
| --- | --- | --- |
| `-verbose:gc` | `-Xlog:gc=info` | Dạng text khác |
| `-Xloggc:gc.log` | `-Xlog:gc:gc.log` | Cũ deprecated trong Oracle JDK 25 |
| `-XX:+PrintGCDetails` | `-Xlog:gc*=info` | Không bảo đảm fields một-một |
| `-XX:+PrintGCTimeStamps` | `-Xlog:gc:gc.log:time,uptime,level,tags` | Decorators rõ ràng hơn |
| Một số cờ `Trace*` cũ | `-Xlog:<tags>` liên quan | Tra docs từng release, không đoán tag |

Các lệnh lab:

~~~bash
java -Xlog:help
java -Xlog:gc=info -version
java -Xlog:gc*=info:file=gc.log -version
java -Xlog:gc*,safepoint=info:file=runtime.log:time,uptime,level,tags -version
~~~

**Không tự động bật logging level debug/trace cho mọi pod**. GC logs có thể chiếm disk/I/O; cần file rotation, retention và security permissions.

**Quan trọng:** Một GC parser viết cho JDK 8 có thể mất metrics hoặc đọc sai trên JDK 25. Test cả **monitoring pipeline và alert rules**, không chỉ ứng dụng startup.

## 11. Container memory/CPU ergonomics thay đổi hành vi

Trên HotSpot hiện đại, heap/GC threads/JIT compilation threads có thể phụ thuộc:

- Physical RAM hoặc cgroup available memory.
- CPU quota, cpuset hoặc `ActiveProcessorCount`.
- `MaxRAMPercentage`, `InitialRAMPercentage`, `MinRAMPercentage`.
- GC/JDK release-specific ergonomics.

~~~bash
java -XX:+PrintFlagsFinal -version
java -XshowSettings:system -version
java -Xlog:os+container=info -version
~~~

Kết quả `XshowSettings:system` và log `os+container` phụ thuộc JVM/OS/cgroup support; nếu một tag không xuất hiện, tra `java -Xlog:help` hoặc tăng log level trong môi trường test.

### Fraction vs Percentage

Một số flags cũ như `-XX:MaxRAMFraction=4` được thay bằng `-XX:MaxRAMPercentage=25.0` trên JDK mới hơn.

~~~text
1/4 = 25% của giá trị RAM làm cơ sở sizing,
nhưng KHÔNG đảm bảo effective heap giống nhau khi môi trường/JDK đổi.
~~~

Oracle `java` manual JDK 24 liệt kê nhiều `*RAMFraction` options là removed. Vì thế khi nâng lên JDK 24/25, cần chuyển syntax phù hợp và **kiểm tra `MaxHeapSize`**, không chỉ tìm/replace tên.

`MinRAMPercentage` dễ hiểu nhầm: dù có “Min”, Oracle docs liên hệ nó với đường heap sizing trong **small-heap scenario**, không phải một hard minimum heap size.

### Ví dụ explicit heap trong lab

~~~bash
java -Xms64m -Xmx256m -XX:+PrintFlagsFinal -version
~~~

Ví dụ chỉ để kiểm tra flags, không phải heap recommendation production. Phải chừa native memory headroom trong container.

## 12. JDK 25: RTM removed, ZGC behavior và những điều mới

**RTM options removed ở JDK 25**, ví dụ:

~~~text
-XX:+UseRTMLocking
-XX:+UseRTMDeopt
-XX:RTMAbortRatio=...
-XX:RTMRetryCount=...
~~~

Cũng theo JDK 25 release notes, `UseCompressedClassPointers` **deprecated trong JDK 25** và có kế hoạch loại bỏ một mode ở tương lai; **không viết rằng nó đã removed trong JDK 25**.

Với ZGC, **JEP 490** bỏ non-generational mode trong JDK 24; đến JDK 25 collector ZGC sử dụng generational mode. **`UseZGC` vẫn tồn tại** khi VM/build hỗ trợ; thay đổi mode không đồng nghĩa toàn collector bị loại.

Đặc biệt, JDK 25 release notes còn ghi một số optional/experimental compiler capabilities bị loại; không nên đồng nhất chuyện một compiler tùy chọn trong OpenJDK bị loại với việc **GraalVM Native Image** không còn là công nghệ có thể dùng.

**Tra release path đầy đủ:** nếu chuyển từ 8 → 25, xem biến động của JDK 9, 11, 14, 17, 21, 24 và 25, không chỉ mục “Removed Java Options in JDK 25”. Những option bị loại ở version trung gian không nhất thiết được lặp lại trong danh sách removed riêng JDK 25.

## 13. Tự thử flag: phân biệt error và ignored

Một negative test có chủ đích trên Oracle HotSpot JDK 25:

~~~bash
java -XX:+UseRTMLocking -version
~~~

Lệnh dự kiến **thất bại**, vì option thuộc nhóm removed; không dùng trong production startup.

Một positive test:

~~~bash
java -Xmx128m -XX:+PrintFlagsFinal -version
~~~

Lệnh thành công cho biết flag được chấp nhận, nhưng **không chứng minh service sẽ đủ heap/đạt SLA**.

| Phản hồi | Diễn giải | Bước tiếp theo |
| --- | --- | --- |
| Exit 0, không warning | VM chấp nhận options | So effective values |
| Exit 0, deprecated warning | Flag có thể vẫn hoạt động | Lên kế hoạch thay |
| Exit 0, ignored/obsolete | Flag không còn tác động | Gỡ, đo default mới |
| Exit khác 0, unrecognized | Removed/typo/vendor mismatch | Chỉnh flag trước deploy |
| Exit khác 0, experimental option | Cần unlock/VM capability | Tra docs |
| Exit khác 0, incompatible options | Conflict giữa flags | Thử từng cờ và tổ hợp |
| Exit 0 nhưng value sai | Override/origin/ergonomic policy | Audit toàn startup sources |

**Không lạm dụng `-XX:+IgnoreUnrecognizedVMOptions`** để application “chạy đã”. Flag này có thể làm launcher bỏ qua một tuning option cần thiết, vô hiệu hóa ý định cấu hình mà không làm rõ nguyên nhân.

### Linux/macOS: xác nhận exit status

~~~bash
java -XX:+UseRTMLocking -version > probe.out 2> probe.err
result=$?
echo "exit=$result"
cat probe.err
~~~

### Windows PowerShell

~~~powershell
java -XX:+UseRTMLocking -version 2> probe.err
Write-Output "exit=$LASTEXITCODE"
Get-Content .\probe.err
~~~

Không hardcode một error text vì vendor, version và localization có thể khác.

## 14. Java 17+ runnable: smoke test có stdout xác định

Lưu file **`JdkUpgradeSmoke.java`**:

~~~java
public final class JdkUpgradeSmoke {
    private JdkUpgradeSmoke() { }

    static long sumTo(int n) {
        long total = 0;
        for (int i = 1; i <= n; i++) {
            total += i;
        }
        return total;
    }

    public static void main(String[] args) {
        System.out.println("sum=" + sumTo(100));
        System.out.println("profile=" +
                System.getProperty("app.profile", "default"));
    }
}
~~~

Biên dịch:

~~~bash
javac -g JdkUpgradeSmoke.java
java JdkUpgradeSmoke
~~~

**Stdout chính xác khi không có property `app.profile`:**

~~~text
sum=5050
profile=default
~~~

Vì tổng `1+2+...+100 = 100*101/2 = 5050`.

Thử explicit system property và heap flag:

~~~bash
java -Dapp.profile=upgrade -Xmx128m JdkUpgradeSmoke
~~~

**Stdout:**

~~~text
sum=5050
profile=upgrade
~~~

**`-D` và `-Xmx` khác vai trò**: một thiết lập Java system property, một đặt max heap. Hai outputs không xác nhận GC/JIT/heap-effective settings; phải đo flags riêng.

Nếu đang dùng JDK 25 compiler để build cho runtime Java 17, có thể dùng:

~~~bash
javac --release 17 JdkUpgradeSmoke.java
~~~

**`--release 17` không làm JVM JDK 25 có GC defaults của JDK 17**.

## 15. Java 17+ runnable: quan sát runtime VM context

Lưu **`VmSnapshotDemo.java`**:

~~~java
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public final class VmSnapshotDemo {
    public static void main(String[] args) {
        RuntimeMXBean runtime =
                ManagementFactory.getRuntimeMXBean();

        System.out.println("specVersion=" +
                System.getProperty("java.specification.version"));
        System.out.println("vmName=" +
                System.getProperty("java.vm.name"));
        System.out.println("maxHeapBytes=" +
                Runtime.getRuntime().maxMemory());
        System.out.println("processors=" +
                Runtime.getRuntime().availableProcessors());

        boolean hasXmx = runtime.getInputArguments().stream()
                .anyMatch(arg -> arg.startsWith("-Xmx"));
        System.out.println("explicitXmx=" + hasXmx);

        for (GarbageCollectorMXBean bean :
                ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("gcBean=" + bean.getName());
        }
    }
}
~~~

~~~bash
javac -g VmSnapshotDemo.java
java -Xmx128m VmSnapshotDemo
~~~

**Output biến thiên theo JVM/OS; ví dụ minh họa, không là output cố định:**

~~~text
specVersion=25
vmName=OpenJDK 64-Bit Server VM
maxHeapBytes=134217728
processors=4
explicitXmx=true
gcBean=G1 Young Generation
gcBean=G1 Old Generation
~~~

Những giá trị `maxHeapBytes`, `processors` và GC bean names **không bảo đảm giống nhau trên mọi HotSpot build**. `Runtime.maxMemory()` có thể chịu heap alignment, VM ergonomics và collector specifics.

**Chương trình giúp quan sát app nhìn thấy gì**, chứ không thay cho `PrintFlagsFinal`: nó không in toàn bộ VM flags, origins, hidden diagnostic options hay tổng RSS.

### Vì sao cần cả app-level và VM-level snapshots?

- JVM flags nêu **ý định và effective defaults**.
- Runtime MXBeans/API cho thấy **giá trị ứng dụng thực quan sát**.
- GC logs và OS metrics cho thấy **behavior dưới tải**.
- Release notes giải thích **vì sao** một cờ mất/đổi semantics.

Kết luận nâng JDK có chất lượng khi cả bốn lớp thông tin thống nhất.

## 16. Script nhỏ kiểm startup: Bash

Ví dụ file **`check-jvm.sh`**:

~~~bash
#!/usr/bin/env bash

JAVA_BIN=java

"$JAVA_BIN" -Xmx128m -Xlog:gc=info -version \
  > vm-stdout.txt 2> vm-stderr.txt
status=$?

echo "exit=$status"

if [ "$status" -ne 0 ]; then
  echo "VM startup failed"
  cat vm-stderr.txt
  exit "$status"
fi

if grep -Eiq 'deprecated|obsolete|ignoring option' vm-stderr.txt; then
  echo "Review option warnings"
else
  echo "Startup passed; compare effective defaults next"
fi
~~~

Script yêu cầu **Bash**, không phải POSIX `sh`. Đây là validator nhẹ, không phải parser hoàn chỉnh. Cảnh báo có thể khác text, in stdout hoặc theo vendor. **Phải đối chiếu release notes và effective flags**, không coi regex “không thấy warning” là đạt hết yêu cầu.

## 17. Script nhỏ kiểm startup: PowerShell

Ví dụ file **`check-jvm.ps1`**:

~~~powershell
$java = 'java'
& $java '-Xmx128m' '-Xlog:gc=info' '-version' 2>&1 |
    Out-File -FilePath vm-output.txt -Encoding utf8

$code = $LASTEXITCODE
Write-Output "exit=$code"

if ($code -ne 0) {
    Get-Content .\vm-output.txt
    throw 'VM startup failed'
}

$text = Get-Content .\vm-output.txt -Raw
if ($text -match 'deprecated|obsolete|ignoring option') {
    Write-Warning 'Review JVM option warnings'
}
~~~

Trên **Windows PowerShell 5.1**, redirection native stderr/encoding có quirks; nếu không thu đủ output, dùng bản PowerShell/launcher wrapper phù hợp. Thay `java` bằng **absolute path của JDK đích** trong CI để tránh test nhầm binary.

## 18. Kiểm tra thay đổi về logging và observability

Một nâng cấp thành công về code vẫn có thể phá vỡ metrics pipeline:

- Format GC logs thay đổi, regex cũ không match.
- `-Xlog` output file/decorators khác lệnh logging JDK 8.
- JFR event types/fields có thể đổi.
- Tên GC MXBeans thay đổi theo collector.
- GC pause metrics từ app/Prometheus có label khác.
- JVM default log output tăng, tràn disk quota.
- Heap dumps chứa dữ liệu nhạy cảm cần bảo vệ.

Tách hai loại kiểm thử:

1. **Functional:** service start, request đúng, không exception.
2. **Observability:** logs, dashboards, alarms, heap/GC metric và tracing vẫn thể hiện đúng thực trạng.

**Không thay đổi parser dựa vào ví dụ log của blog đời cũ.** Thu sample logs từ chính target JDK và điều chỉnh bộ parser bằng fixture tests.

## 19. Ma trận kiểm thử nâng JDK

| Kịch bản | Mục tiêu |
| --- | --- |
| Old JDK + old flags | Baseline có số đo |
| New JDK + old flags | Bắt deprecated/obsolete/removed/conflict |
| New JDK + cleaned flags | Startup ổn và effective values hợp lý |
| New JDK + defaults mới | Xem liệu legacy tuning còn cần không |
| New JDK + explicit tuning có chứng cứ | Chỉ điều chỉnh các vấn đề đo được |
| Canary production-like | Quan sát traffic, GC, P95/P99, CPU, RSS |
| Rollback image/config | Giữ đường trở về nếu SLO bị ảnh hưởng |

Quy trình đề xuất:

1. Inventory đầy đủ JVM args + origins.
2. Đọc migration guide và release notes toàn **release path**.
3. Ghi snapshots flags/version/settings old và new ở cùng limits.
4. Loại options không hỗ trợ, migrate deprecated syntax đúng.
5. Smoke test + integration tests với target JDK.
6. Load test để đo startup, memory, CPU, throughput và tail latency.
7. Canary rồi rollout có rollback criteria rõ ràng.

Đừng cùng lúc đổi JDK, Java application version, collector, heap limit và container CPU quota; nếu benchmark regress sẽ khó truy nguyên.

## 20. Anti-patterns khi kiểm tra JVM flags

| Anti-pattern | Vì sao nguy hiểm |
| --- | --- |
| Giữ toàn bộ Java 8 GC tuning cho JDK 25 | Collector, defaults và flags đã thay đổi |
| Bật `IgnoreUnrecognizedVMOptions` để bỏ lỗi | Che flags không còn hiệu lực |
| Chỉ xem JVM exit 0 | Obsolete có thể accepted nhưng ignored |
| Chỉ so Dockerfile | Flags có thể đến từ env/agents/scripts khác |
| Không đọc `PrintFlagsFinal` | Không thấy defaults/ergonomic changes |
| So JDK 17 local với JDK 25 container | CPU/memory environment không tương đương |
| Xem `-Xmx` bằng container RSS | Heap max khác total process memory |
| Dùng GC parser của Java 8 | Unified Logging format có thể khác |
| Tin một flag name là portable | `-XX` phụ thuộc JVM implementation |
| Nói CMS vẫn dùng trên Java 25 | CMS đã removed ở JDK 14 |
| Nói `-Xloggc` đã removed trên JDK 25 | Oracle JDK 25 vẫn xếp deprecated |
| Nói `UseZGC` đã removed | Non-generational mode bỏ, collector vẫn tồn tại |
| Xóa warning bằng cách suppress logs | Không sửa cấu hình sai |
| Chỉ chạy một microbenchmark lạnh | Không đo steady throughput/tail |
| Thay flags rồi không load test | Không chứng minh production SLO |
| Đặt `-Xmx` sát bằng cgroup memory | Không chừa native headroom |
| Bỏ qua thay đổi platform/CPU/JDK vendor | Ergonomics khác không chỉ bởi JDK version |

## 21. Câu hỏi tự kiểm tra

**1. Deprecated và obsolete khác nhau thế nào?**  
Deprecated thường còn hiệu lực và có warning; obsolete được nhận nhưng thường **ignored**, nên app start chưa đủ chứng minh tuning hoạt động.

**2. Removed option gây gì?**  
JVM có thể từ chối startup với `Unrecognized VM option` trước khi chạy `main`.

**3. Cờ nào lấy effective JVM flags?**  
`java -XX:+PrintFlagsFinal -version` trên HotSpot.

**4. Vì sao phải xem origin metadata?**  
Cùng một value có thể từ default, ergonomics hoặc command line; audit phải biết nguồn quyết định.

**5. Vì sao đổi JDK có thể đổi max heap dù startup script không thay?**  
Ergonomics, cgroup detection, RAM percentages và build defaults thay đổi.

**6. Trong HotSpot thông dụng, JDK 8 → 9 đổi default collector thế nào?**  
Từ Parallel GC sang G1 theo JEP 248, với các caveats resource/explicit settings.

**7. CMS còn dùng được trên HotSpot 25?**  
Không; đã removed từ JDK 14 theo JEP 363.

**8. `-Xloggc` được Oracle JDK 25 phân loại ra sao?**  
Deprecated, không phải removed; migrate sang Unified Logging.

**9. `UseRTMLocking` ở JDK 25?**  
Oracle JDK 25 liệt kê nó trong nhóm removed RTM options.

**10. Một warning “ignored option” cho biết gì?**  
VM không áp dụng ý nghĩa cờ đó; cần gỡ và kiểm tra effective default.

**11. `javac --release 17` có làm JDK 25 chọn GC của JDK 17?**  
Không. Nó chỉ ảnh hưởng compiler target, không pin runtime GC/JIT.

**12. `Xmx512m` có đảm bảo RSS nhỏ hơn 512 MiB?**  
Không, JVM còn native memory, threads, code cache và các overhead.

**13. Vì sao thử cờ với `-version` chưa đủ?**  
Chỉ xác nhận startup/config parse, không xác nhận ứng dụng đúng và performance/GC/memory dưới tải.

**14. Nâng JDK nên test những gì sau startup?**  
Integration correctness, GC logs/metrics parser, startup latency, throughput, P95/P99, CPU, heap/RSS, code cache và rollback.

**15. Vì sao không đánh giá defaults bằng hai machines khác nhau?**  
OS/CPU/cgroup quotas, VM vendor và flags cũng tác động, không thể quy mọi khác biệt cho JDK release.

## 22. Checklist trước khi rollout

- [ ] Xác minh Java binary/vendor/build, OS, arch và resource limits.
- [ ] Inventory `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, launch scripts và agents.
- [ ] Chụp `PrintFlagsFinal`, `PrintCommandLineFlags` ở JDK cũ và mới.
- [ ] Phân loại supported/deprecated/obsolete/removed/changed defaults.
- [ ] Loại CMS và các flags thật sự không còn được hỗ trợ.
- [ ] Migrate GC logging và kiểm tra parser/retention.
- [ ] Rà `*RAMFraction` cũ và chuyển `*RAMPercentage` khi phù hợp.
- [ ] Kiểm tra JDK 25 removed RTM options nếu có.
- [ ] Đánh giá collector, heap, cgroup và GC thread ergonomics.
- [ ] Không lạm dụng `IgnoreUnrecognizedVMOptions`.
- [ ] Chạy negative/positive option probes đúng target JDK.
- [ ] Biên dịch và chạy smoke/integration apps Java 17+.
- [ ] Load test cùng resource limits, so startup/P99/CPU/RSS/GC.
- [ ] Canary có SLO thresholds và rollback artifact.
- [ ] Chỉ giữ tuning có hiệu quả đo được trên JDK đích.

## 23. Tài liệu tham khảo

- [Oracle JDK 25 — java launcher options](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): `-XX:+PrintFlagsFinal`, `-Xlog`, deprecated/obsolete/removed, GC/heap flags.
- [Oracle JDK 25 — Consolidated release notes](https://www.oracle.com/java/technologies/javase/25all-relnotes.html): RTM, `UseCompressedClassPointers` và các thay đổi release.
- [Oracle — Migrating from JDK 8 to later releases](https://docs.oracle.com/en/java/javase/25/migrate/migrating-jdk-8-later-jdk-releases.html): default G1, GC logs, CMS removal và PermGen.
- [Oracle — JDK release notes index](https://www.oracle.com/java/technologies/javase/jdk-relnotes-index.html): tìm đúng phiên bản trung gian.
- [Oracle JDK 24 — java options](https://docs.oracle.com/en/java/javase/24/docs/specs/man/java.html): lịch sử `*RAMFraction` và removed flags.
- [OpenJDK JEP 248 — G1 as default](https://openjdk.org/jeps/248): thay default collector trong JDK 9.
- [OpenJDK JEP 363 — Remove CMS](https://openjdk.org/jeps/363): collector bị loại ở JDK 14.
- [OpenJDK JEP 158 — Unified JVM Logging](https://openjdk.org/jeps/158): tags/levels/outputs.
- [OpenJDK JEP 271 — Unified GC Logging](https://openjdk.org/jeps/271): modern GC logging.
- [OpenJDK JEP 490 — ZGC remove non-generational mode](https://openjdk.org/jeps/490): khác biệt ZGC JDK 24+.
- [Oracle JDK 25 — GC Tuning Guide](https://docs.oracle.com/en/java/javase/25/gctuning/): collector/ergonomics trên release mới.
- [Oracle JDK 25 — jcmd](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html): runtime `VM.command_line` khi được phép attach.

**Kết luận:** Khi nâng JDK, đừng chỉ kiểm tra flag có được JVM parse thành công không. Hãy phân biệt **deprecated** (vẫn có tác dụng), **obsolete** (bị bỏ qua), **removed** (có thể chặn startup), và **changed defaults/semantics** (thường im lặng). Quy trình đáng tin là **inventory → release notes → snapshot effective flags/origins → cleanup → smoke/integration → load-test canary → rollout/rollback**. Mọi tuning cũ cần được kiểm tra lại trên đúng JDK, VM vendor, CPU, container resource limits và workload thực.

</details>

- [Quay lại đầu trang](#back-to-top)
