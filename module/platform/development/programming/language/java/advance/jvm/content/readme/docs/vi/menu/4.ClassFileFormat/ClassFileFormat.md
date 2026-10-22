<a id="back-to-top"></a>

# Tệp class

## Menu
- [Cấu trúc ClassFile: magic number, major version và minor version](#class-file-format-001)
- [Tương thích phiên bản class và giới hạn của cờ --enable-preview](#class-file-format-002)
- [Tên nhị phân, tên nội bộ và descriptor của trường, phương thức, mảng](#class-file-format-003)
- [Constant pool: hằng số, tên kiểu và tham chiếu ký hiệu](#class-file-format-004)
- [Access flags, lớp cha, giao diện, trường và phương thức](#class-file-format-005)
- [Thuộc tính Code: max_stack, max_locals, mã lệnh và bảng xử lý ngoại lệ](#class-file-format-006)
- [StackMapTable và thông tin kiểu phục vụ xác minh bytecode](#class-file-format-007)
- [LineNumberTable, LocalVariableTable và giới hạn khi khôi phục mã nguồn](#class-file-format-008)
- [Signature, annotation, synthetic và bridge: dữ liệu bổ sung ngoài descriptor](#class-file-format-009)
- [Thuộc tính cho module, nestmate, record và sealed class](#class-file-format-010)
- [Giới hạn kích thước phương thức, constant pool và các ràng buộc định dạng](#class-file-format-011)
- [Đọc javap -c -v -s -l để nối cấu trúc class với mã Java](#class-file-format-012)

## <a id="class-file-format-001">Cấu trúc ClassFile: magic number, major version và minor version</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu trúc ClassFile: magic number, major version và minor version

Một tệp `.class` của Java là **tệp nhị phân theo định dạng ClassFile do JVM Specification quy định**. Nó thường được trình biên dịch `javac` tạo ra từ mã nguồn, nhưng cũng có thể được các công cụ sinh hoặc biến đổi bytecode tạo ra. ClassFile chứa định danh định dạng, phiên bản class file, constant pool, mô tả lớp, trường, phương thức và các thuộc tính. JVM dùng các thông tin này để nhận diện, đọc, kiểm tra, liên kết và thực thi lớp.

Ba trường đầu tiên của cấu trúc ClassFile chính là trọng tâm bài học:

- **`magic`**: bốn byte đầu có giá trị **`0xCAFEBABE`**, giúp nhận diện đây là dữ liệu dự kiến theo định dạng ClassFile.
- **`minor_version`**: số nguyên không dấu **16 bit**, cho biết phiên bản phụ theo quy tắc của định dạng class file.
- **`major_version`**: số nguyên không dấu **16 bit**, cho biết phiên bản chính và là cơ sở quan trọng để JVM quyết định có hỗ trợ class file hay không.

Một class file mang đúng magic number **chưa chắc hợp lệ hoặc thực thi được**. JVM còn phải đọc và kiểm tra cấu trúc còn lại, kiểu dữ liệu, bytecode, khả năng tương thích phiên bản, classpath/module path và những phụ thuộc được tham chiếu.

## 1. ClassFile là cấu trúc nhị phân, không phải mã nguồn Java

Sơ đồ đơn giản của byte đầu tiên trong một `.class` thông thường được biên dịch cho Java 17:

```text
Offset (hex)   00 01 02 03 | 04 05 | 06 07 | 08 09 | ...
Giá trị        CA FE BA BE | 00 00 | 00 3D | ...   | ...
                  magic       minor    major   constant_pool_count ...

magic         = 0xCAFEBABE
minor_version = 0
major_version = 0x003D = 61
```

Mỗi trường **`u1`** có 1 byte, **`u2`** có 2 byte và **`u4`** có 4 byte. Theo định dạng ClassFile, các giá trị nhiều byte được lưu ở thứ tự **big-endian** (byte quan trọng nhất đứng trước). Vì vậy:

- Dãy byte `CA FE BA BE` biểu diễn số 32 bit `0xCAFEBABE`.
- Dãy `00 00` biểu diễn `minor_version = 0`.
- Dãy `00 3D` biểu diễn `major_version = 0x003D = 61`, ứng với class-file version chuẩn của Java 17.

**Đây là ví dụ header, không phải toàn bộ file.** Byte ở offset 8–9 là `constant_pool_count`; giá trị thực tế phụ thuộc số mục trong constant pool do compiler tạo. Không đoán nó từ ví dụ nếu chưa đọc tệp.

### Vì sao không thể mở `.class` như văn bản?

Tệp `.java` chứa ký tự mã nguồn; `.class` chứa một trình tự có cấu trúc gồm byte, số nguyên, chỉ mục, chuỗi mã hóa và bytecode. Trình soạn thảo văn bản có thể hiển thị ký tự vô nghĩa khi cố diễn giải byte nhị phân thành Unicode. Dùng `javap -verbose`, trình xem hex hoặc một chương trình đọc nhị phân để kiểm tra. Các lệnh trong tệp `.class` là **JVM bytecode**, không phải trực tiếp mã máy x86-64 hay ARM.

## 2. Cấu trúc tổng quát của `ClassFile`

Phần đầu và các nhóm thành phần được mô tả trong JVM Specification có dạng:

```text
ClassFile {
    u4             magic;
    u2             minor_version;
    u2             major_version;
    u2             constant_pool_count;
    cp_info        constant_pool[constant_pool_count - 1];
    u2             access_flags;
    u2             this_class;
    u2             super_class;
    u2             interfaces_count;
    u2             interfaces[interfaces_count];
    u2             fields_count;
    field_info     fields[fields_count];
    u2             methods_count;
    method_info    methods[methods_count];
    u2             attributes_count;
    attribute_info attributes[attributes_count];
}
```

Đây là **mô tả cấu trúc dữ liệu của định dạng file**, không phải cú pháp Java để đặt vào `ClassFile.java`. Ký hiệu `u1/u2/u4` là các số nguyên không dấu có độ rộng cố định, không phải kiểu biến nguyên thủy độc lập của Java.

| Thành phần | Chức năng chính |
| --- | --- |
| `magic` | Dấu hiệu nhận diện định dạng ClassFile |
| `minor_version`, `major_version` | Cặp phiên bản để JVM xác định quy tắc đọc và hỗ trợ |
| `constant_pool_count`, `constant_pool` | Bảng các hằng số và symbolic reference cho tên lớp, trường, phương thức, chuỗi, literal, descriptor... |
| `access_flags` | Các cờ mô tả thuộc tính của class/interface/module như `public`, `final`, `abstract` ở mức định dạng |
| `this_class`, `super_class` | Chỉ mục tham chiếu tới biểu diễn lớp hiện tại và lớp cha tương ứng trong constant pool |
| `interfaces_count`, `interfaces` | Danh sách interface được lớp hoặc interface này khai báo mở rộng/triển khai |
| `fields_count`, `fields` | Mô tả các field, kiểu, cờ và thuộc tính liên quan |
| `methods_count`, `methods` | Mô tả các method, descriptor, cờ và thuộc tính; một method có mã có thể chứa thuộc tính `Code` |
| `attributes_count`, `attributes` | Metadata như `SourceFile`, `Signature`, `BootstrapMethods`, `InnerClasses`... khi áp dụng |

Ba trường `magic`, `minor_version`, `major_version` **nằm tại vị trí cố định**. Từ `constant_pool` trở đi, kích thước các khối có thể biến đổi. Không thể tìm `methods_count` bằng một offset cố định giống như major version; phải giải mã tuần tự các thành phần trước đó.

**Chú ý `constant_pool_count`:** bảng constant pool được đánh chỉ số bắt đầu từ **1** và thông thường có `constant_pool_count - 1` vị trí được chỉ số hóa. Các hằng `CONSTANT_Long` và `CONSTANT_Double` chiếm hai **vị trí chỉ số** theo quy tắc format dù nội dung là một mục; vị trí tiếp theo bị dành riêng. Vì vậy số mục thực sự không nhất thiết bằng số vị trí chỉ số sử dụng.

## 3. Magic number: bốn byte `CA FE BA BE`

### 3.1. Định nghĩa

Đầu class file phải có đúng:

```text
CA FE BA BE
```

Viết dưới dạng số nguyên hex 32 bit: **`0xCAFEBABE`**. Trong tài liệu Java, đôi khi các byte được viết nối liền thành `CAFEBABE`, nhưng đây là **một trường `u4`**, không phải bốn ký tự ASCII `C`, `A`, `F`, `E`.

Ví dụ so sánh:

```text
Byte của ClassFile:  CA FE BA BE
Byte ASCII "CAFE":  43 41 46 45
```

Hai chuỗi byte khác nhau hoàn toàn. Khi xem hex dump, phải kiểm tra **byte thực**, không phải nội dung chữ được render.

### 3.2. Magic dùng để làm gì và không làm được gì?

Magic number giúp công cụ/JVM sớm phát hiện một đầu vào không có header đúng định dạng. Nếu file bắt đầu bằng `50 4B ...`, chẳng hạn, nhiều khả năng đó là một ZIP/JAR container chứ không phải class file thô: JAR có thể **chứa nhiều `.class`**, nhưng bản thân JAR không bắt đầu bằng magic của ClassFile.

Magic **không phải chữ ký mật mã**, không xác minh nguồn gốc hoặc tính toàn vẹn của file. Ai cũng có thể tạo một tệp gồm bốn byte `CA FE BA BE` mà không có cấu trúc còn lại; tệp đó vẫn không phải class file hợp lệ. JVM phải kiểm tra tiếp phiên bản, cấu trúc constant pool, thuộc tính và bytecode.

## 4. `major_version`: phiên bản ClassFile mà JVM hỗ trợ

**`major_version`** nằm tại offset byte 6–7 (đếm từ 0). Nó thường tăng theo các bản phát hành Java, thể hiện cấp độ class-file format của tệp. Một JVM cũ **không bắt buộc** hiểu mọi định dạng class file được biên dịch bằng JDK mới.

Một số mốc thường gặp:

| Java SE dùng làm đích biên dịch | `major_version` | Hex của trường `u2` |
| --- | ---: | --- |
| Java 8 | 52 | `00 34` |
| Java 11 | 55 | `00 37` |
| Java 17 | 61 | `00 3D` |
| Java 21 | 65 | `00 41` |
| Java 25 | 69 | `00 45` |

Các mốc trên áp dụng cho class file chuẩn của từng release, thường có **`minor_version = 0`**. Với các Java phiên bản lịch sử, quy tắc minor/major có thể khác; không nên áp dụng máy móc công thức tuyến tính cho toàn bộ lịch sử Java.

Ví dụ:

- Biên dịch cho Java 17 thường tạo class file phiên bản **61.0**.
- Biên dịch cho Java 21 thường tạo class file phiên bản **65.0**.
- Một runtime chỉ hiểu class file tối đa phiên bản **61** sẽ từ chối class file yêu cầu **65**, thường bằng **`UnsupportedClassVersionError`** khi thử nạp lớp.
- JVM mới hơn có thể hỗ trợ các class file phiên bản cũ hơn, nhưng **chỉ tương thích class format chưa đủ**: dependency, API, quyền truy cập/module, hành vi runtime hay thư viện native vẫn có thể gây lỗi.

### 4.1. JDK chạy compiler khác với release đích

Nếu bạn dùng JDK 21 để chạy:

```bash
javac --release 17 Hello.java
```

thì compiler được yêu cầu tạo output phù hợp **Java 17**, bao gồm class-file version 61 và giới hạn API nền tảng được hỗ trợ bởi release đích. Tức là **JDK của công cụ biên dịch** có thể mới hơn **release mà class file nhắm tới**.

Ngược lại, chỉ vì máy phát triển có JDK 21 không có nghĩa mọi class được sinh ra **bắt buộc** là major 65. Phải kiểm tra cờ biên dịch và tệp kết quả.

Nếu muốn đảm bảo bytecode/runtime API cho một release cụ thể, ưu tiên `--release N` khi JDK đang dùng hỗ trợ đích đó. `-source N` chỉ điều khiển cấp ngôn ngữ; `-target N` điều khiển định dạng output, nhưng kết hợp hai cờ đó **không tự động bảo đảm** không dùng nhầm API chuẩn của JDK mới, trong khi `--release` có kiểm soát phần này.

### 4.2. Phiên bản file khác phiên bản runtime/API

Cần phân biệt:

```text
JDK dùng để chạy javac   → toolchain version
--release hoặc -target   → target bytecode / class-file major version
java --version           → Java runtime đang khởi chạy
javap -verbose           → phiên bản được ghi trong file .class cụ thể
```

Không thể kết luận target chỉ từ tên thư mục chứa mã nguồn hoặc tên JAR. Trong một số trường hợp, cùng một JAR còn có thể chứa **nhiều biến thể class file theo release** (multi-release JAR); vì vậy phải kiểm tra **đúng entry class file** đang được chọn.

## 5. `minor_version`: phiên bản phụ và preview class file

**`minor_version`** nằm tại offset byte 4–5. Nó là số nguyên không dấu từ 0 đến 65535. Với class file thông thường do compiler Java hiện đại sinh ra, minor thường bằng **0**.

### 5.1. Minor bằng 0

Đây là trường hợp thường gặp nhất khi biên dịch ứng dụng Java ổn định:

```text
CA FE BA BE 00 00 00 3D ...
             └─┘   └─┘
            minor  major
              0     61 (Java 17)
```

Đọc `61.0` là **major 61, minor 0**; đừng đọc nhầm 61.0 thành version của ngôn ngữ Java “61”.

### 5.2. Giá trị đặc biệt `65535` (`0xFFFF`) và tính năng preview

Kể từ cơ chế preview được quy định cho các Java release hiện đại, class-file version của **preview features** có thể được đánh dấu bằng `minor_version = 65535` (`0xFFFF`). Ví dụ lớp được biên dịch sử dụng preview feature của Java 17 có thể được ghi phiên bản **61.65535**.

Điểm cần nhớ:

- Class file có minor **65535** dành cho cơ chế preview theo release phù hợp, **không tương đương bản class file ổn định** với minor 0.
- Một JVM hỗ trợ preview cần **`--enable-preview`** khi khởi chạy class sử dụng preview theo luật của release đó.
- Class file mang dấu preview gắn với **release tương ứng**; không nên kỳ vọng class preview của phiên bản cũ tự động chạy trên JVM của một Java release mới hơn chỉ vì JVM mới hỗ trợ major cao hơn.
- `--enable-preview` trong `javac` chỉ hỗ trợ các preview feature của **chính release mà compiler đang dùng**, không dùng tùy ý để bật preview của một release cũ khi chạy compiler mới.

Không phải cứ có `--enable-preview` là mọi class file được tạo nhất thiết có minor 65535; điều này còn phụ thuộc mã được biên dịch có thực sự dùng tính năng cần dấu preview và quy tắc compiler/version tương ứng. Đọc **giá trị được ghi trong chính class file** luôn chính xác hơn suy đoán.

### 5.3. Kiểm tra đầy đủ cặp major/minor

Một JVM cần xem **cặp phiên bản** chứ không chỉ major, theo tập các phiên bản được hỗ trợ và quy tắc preview. Với các phiên bản class file hiện đại, minor thông thường chỉ có ý nghĩa chuẩn khi bằng 0, hoặc là 65535 trong trường hợp preview được hỗ trợ. Những tổ hợp khác có thể bị từ chối dù magic đúng.

Lỗi phiên bản thường xuất hiện **lúc lớp được tải/kiểm tra**, trước khi bytecode trong `main` bắt đầu chạy. Có thể một chương trình đã khởi chạy nhưng về sau mới gặp lỗi khi một lớp khác được nạp lần đầu.

## 6. Thực hành với JDK 17+: tạo và xem header của một class file

Để có kết quả tái lập được trên JDK 17 trở lên, dùng **`--release 17`** thay vì phụ thuộc vào target mặc định của JDK hiện tại.

### 6.1. Tạo lớp mẫu `Hello.java`

Tạo tệp **`Hello.java`** với nội dung:

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, JVM!");
    }
}
```

Từ thư mục chứa tệp:

```bash
javac --release 17 -encoding UTF-8 Hello.java
java Hello
```

**Standard output của `java Hello`:**

```text
Hello, JVM!
```

Sau `javac`, bạn có tệp **`Hello.class`**, là đầu vào để xem ClassFile header. Nếu có lỗi `invalid flag: --release` hoặc `release version 17 not supported`, hãy kiểm tra `javac --version`: bạn cần JDK hỗ trợ target 17.

### 6.2. Dùng công cụ chuẩn `javap -verbose`

```bash
javap -verbose Hello
```

Trong output dài của `javap`, tìm các dòng sau:

```text
minor version: 0
major version: 61
```

Đây là các dòng về **class-file version**. `javap` còn có thể hiển thị constant pool, tên class, descriptor, flags, metadata, method và bytecode. Đừng nhầm dòng `major version` với phiên bản JVM đang chạy: `javap` đang **đọc một class file**.

Để biết JDK/runtime thực tế:

```bash
javac --version
java --version
```

Output hai lệnh này phụ thuộc vào máy; không lấy phiên bản JDK từ ví dụ trong sách rồi áp vào môi trường thực tế.

### 6.3. Đọc chính xác 8 byte đầu bằng Java

Tạo tệp **`ClassHeaderReader.java`** trong cùng thư mục với `Hello.java`:

```java
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClassHeaderReader {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java ClassHeaderReader <file.class>");
            return;
        }

        Path file = Path.of(args[0]);
        try (DataInputStream input =
                     new DataInputStream(Files.newInputStream(file))) {
            long magic = Integer.toUnsignedLong(input.readInt());
            int minor = input.readUnsignedShort();
            int major = input.readUnsignedShort();

            System.out.printf("magic: 0x%08X%n", magic);
            System.out.println("minor_version: " + minor);
            System.out.println("major_version: " + major);
            System.out.println("magic_valid: " + (magic == 0xCAFEBABEL));
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 ClassHeaderReader.java
java ClassHeaderReader Hello.class
```

**Standard output chính xác khi `Hello.class` được biên dịch bằng lệnh ở mục 6.1:**

```text
magic: 0xCAFEBABE
minor_version: 0
major_version: 61
magic_valid: true
```

Cơ chế của chương trình:

1. `Files.newInputStream(file)` mở file dưới dạng **luồng byte nhị phân**.
2. `DataInputStream.readInt()` đọc **4 byte big-endian**; dùng `Integer.toUnsignedLong` để biểu diễn giá trị như `u4` mà không bị hiểu nhầm do dấu của Java `int`.
3. `readUnsignedShort()` đọc **2 byte big-endian** và trả về `int` trong phạm vi `0..65535`, phù hợp kiểu `u2` của ClassFile.
4. `System.out.printf("0x%08X", magic)` in 8 chữ số hex của magic.
5. So sánh với `0xCAFEBABEL` xác minh riêng **trường magic**, chưa kiểm tra toàn bộ ClassFile hoặc bytecode.

Chương trình này đọc **đúng 8 byte đầu**, không phân tích các phần phía sau. Nếu file quá ngắn, phương thức đọc có thể ném `EOFException` (một loại `IOException`); nếu không tồn tại hoặc không có quyền truy cập, thao tác mở tệp có thể ném `IOException`. Đây là **công cụ học tập**, không phải trình xác thực ClassFile đầy đủ.

### 6.4. Kiểm tra trực tiếp bằng hex viewer

Nếu có công cụ xem hex phù hợp nền tảng, mở `Hello.class` và kiểm tra tám byte đầu:

```text
CA FE BA BE 00 00 00 3D
```

Dấu cách chỉ để dễ đọc, không phải byte trong file. Công cụ hex có thể hiển thị cột offset, byte và chữ ASCII tương ứng; cột **hex bytes** mới là nguồn đáng tin để đọc header. Không sửa trực tiếp byte phiên bản để “nâng cấp/hạ cấp” tệp: định dạng, constant pool và bytecode phía sau phải thực sự tương thích.

## 7. `ClassFile` và quá trình JVM nạp lớp

Quá trình đọc class file nằm trong vòng đời runtime của JVM:

```text
File .class / bytes do class loader cung cấp
                │
                ▼
    Kiểm tra magic và version được hỗ trợ
                │
                ▼
    Phân tích cấu trúc ClassFile
    constant pool, field, method, attribute...
                │
                ▼
    Kiểm tra hợp lệ và bytecode verification
                │
                ▼
    Linking / initialization khi cần
                │
                ▼
    Thực thi method bytecode bằng JVM
```

Sơ đồ nhằm làm rõ **các trách nhiệm**, không cam kết thứ tự chi tiết mọi thao tác giống hệt trên tất cả JVM hoặc mọi lớp được verify/resolve/initialize hoàn toàn ngay một lúc.

- **Class loader** cung cấp định nghĩa lớp dưới dạng các byte; nguồn có thể là file `.class`, một mục trong JAR, module hoặc cơ chế loader khác.
- **Nhận diện format**: magic number và class-file version giúp runtime xác định định dạng có thể đọc và được phép xử lý.
- **Phân tích cấu trúc**: JVM kiểm tra tổ chức các khối và ràng buộc của thông tin như constant pool, tên class, descriptor và thuộc tính.
- **Bytecode verification**: JVM kiểm tra điều kiện an toàn kiểu/trạng thái stack và tính hợp lệ của lệnh theo quy tắc class file tương ứng.
- **Linking, initialization và thực thi**: runtime chuẩn bị và khởi tạo lớp ở thời điểm thích hợp, rồi thực thi các phương thức cần thiết.

Các lớp có thể được nạp **lười** khi chương trình sử dụng đến. Vì vậy lỗi class format hoặc incompatibility ở một dependency có thể xuất hiện **sau** khi main class đã chạy được một phần. Không được hiểu rằng magic number thay thế vai trò của verifier.

### Các lỗi liên quan ClassFile

| Lỗi/triệu chứng | Ý nghĩa cần kiểm tra |
| --- | --- |
| `ClassFormatError` | Dữ liệu ClassFile có cấu trúc không hợp lệ; xem class bytes và công cụ tạo/biến đổi class |
| `UnsupportedClassVersionError` | JVM không hỗ trợ phiên bản class file yêu cầu, hoặc tổ hợp phiên bản/preview không hợp lệ |
| `VerifyError` | Bytecode không đáp ứng các yêu cầu kiểm tra của verifier |
| `NoClassDefFoundError` | Runtime cần một lớp nhưng không lấy được định nghĩa thích hợp; có thể do dependency, classpath hoặc lỗi khởi tạo trước đó |
| `ClassNotFoundException` | Cơ chế tải lớp theo tên không tìm được lớp trong ngữ cảnh tìm kiếm; phụ thuộc cách gọi |
| `IOException` từ `ClassHeaderReader` | Công cụ đọc file demo không thể mở/đọc đủ byte; chưa phải lỗi JVM khi chạy chương trình |

`UnsupportedClassVersionError` là một dạng liên quan `ClassFormatError`, và `VerifyError` thuộc nhóm `LinkageError`. Trong ứng dụng thực tế, giữ nguyên stack trace và kiểm tra **tên lớp cụ thể** gây lỗi; đừng mặc định lỗi nằm ở lớp được chỉ rõ trong câu lệnh `java`.

## 8. Vì sao một class file mới có thể không chạy trên JVM cũ?

Giả sử code nguồn tương thích về cú pháp, nhưng bạn biên dịch bằng `javac` JDK 21 không đặt release đích và nhận output major **65**. JVM Java 17 chỉ hiểu tới major **61** sẽ từ chối class file đó.

```text
javac mặc định JDK 21
       │
       └── class file 65.0
                │
                └── JVM Java 17 (tối đa major 61) → không hỗ trợ

javac JDK 21 --release 17
       │
       └── class file 61.0
                │
                └── JVM Java 17 → class-file version phù hợp
```

Dòng cuối chỉ nói **phiên bản file được hỗ trợ**, không cam kết ứng dụng sẽ chạy thành công nếu thư viện ngoài, cấu hình, dữ liệu hoặc hành vi runtime có vấn đề.

### Phân biệt bốn trường hợp

1. **Biên dịch lỗi**: `javac` không tạo bản class hợp lệ cho mã nguồn bị lỗi, ví dụ sai cú pháp hoặc dùng API không thuộc `--release` đích.
2. **Class file có major quá mới**: JVM từ chối ở giai đoạn nạp lớp, có thể báo `UnsupportedClassVersionError`.
3. **Major phù hợp nhưng bytes sai cấu trúc**: class file vẫn có thể bị bác với `ClassFormatError` hoặc `VerifyError`.
4. **Major và bytes hợp lệ nhưng dependency thiếu**: lỗi nạp/liên kết lớp khác vẫn có thể xuất hiện.

Để chẩn đoán, dùng:

```bash
java --version
javac --version
javap -verbose Hello
```

Sau đó đọc dòng `major version`/`minor version` trong output `javap` rồi đối chiếu release được hỗ trợ của runtime thực tế.

## 9. Điều không nên suy luận chỉ từ 8 byte header

**Không thể kết luận chương trình an toàn.** Header không kiểm tra signature số, nguồn tải về, package, bytecode độc hại hay ý nghĩa nghiệp vụ.

**Không thể biết toàn bộ API mà chương trình dùng.** Major version có thể nói về mức định dạng class file, nhưng muốn xác định method/field/dependency cụ thể phải xem constant pool, method bytecode, descriptor và những lớp phụ thuộc. `--release` giới hạn API Java chuẩn khi biên dịch nhưng không kiểm tra mọi thư viện bên ngoài.

**Không thể biết JIT có tối ưu gì.** Header thuộc định dạng lưu trữ; JIT là cơ chế runtime xử lý bytecode đã được nạp. `.class` không trực tiếp chứa mã máy JIT sinh ra cho CPU hiện tại.

**Không thể giả định file chỉ có một loại metadata.** `attributes` có thể chứa dữ liệu của compiler/debugger/framework; JVM có quy tắc nhận diện thuộc tính, bỏ qua thuộc tính không nhận ra khi phù hợp, và kiểm tra thuộc tính có ý nghĩa chuẩn.

**Không thể kiểm tra nguyên vẹn JAR bằng magic ClassFile.** JAR là container ZIP; phải mở đúng entry `.class` bên trong và đọc từng header. Cùng một ứng dụng có thể có nhiều class file với major khác nhau do quá trình build hoặc thiết kế multi-release JAR.

## 10. Bài tập thực hành và câu hỏi tự kiểm tra

### Bài tập A — Tính phiên bản từ byte

Bạn đọc thấy tám byte đầu:

```text
CA FE BA BE 00 00 00 41
```

- Magic là **`0xCAFEBABE`**.
- Minor = **0**.
- Major = **`0x0041 = 65`**, tương ứng class-file version chuẩn Java 21.

Nếu cố mở bằng JVM Java 17, bước kiểm tra phiên bản sẽ thất bại vì Java 17 không hỗ trợ major 65.

### Bài tập B — Suy luận preview marker

Header:

```text
CA FE BA BE FF FF 00 3D
```

- Magic vẫn là `0xCAFEBABE`.
- Minor = `0xFFFF = 65535`.
- Major = `0x003D = 61`.

Đây là cặp giá trị gắn với **class preview version Java 17** theo quy tắc của định dạng. Nó cần điều kiện hỗ trợ preview thích hợp, không thể coi như class ổn định 61.0.

### Bài tập C — Vì sao magic đúng chưa đủ?

Tự tạo một tệp chỉ chứa bốn byte:

```text
CA FE BA BE
```

Nó có magic đúng nhưng **không có đủ byte cho `minor_version` và `major_version`**, chưa kể các thành phần bắt buộc khác. Tệp không tạo thành ClassFile hợp lệ; chương trình `ClassHeaderReader` ở trên sẽ gặp lỗi đọc thiếu dữ liệu.

### Câu hỏi ôn tập

**1. `major_version` là phiên bản của JDK chạy `java` hay của `.class`?**  
Là trường **phiên bản định dạng của class file**. Nó được ghi tại lúc tạo/biến đổi file; JDK runtime là một thông tin riêng.

**2. Vì sao `00 3D` cho kết quả 61?**  
Vì ClassFile lưu số nguyên **big-endian**, `0x003D` ở hệ 16 bằng **61** ở hệ 10.

**3. `minor_version = 0` luôn đúng cho mọi ClassFile lịch sử?**  
**Không.** Với class file Java ổn định hiện đại minor thường 0; các bản cũ có quy tắc lịch sử riêng và preview hiện đại có thể dùng 65535.

**4. Một tệp có magic đúng nhưng bytecode sai có được JVM chạy không?**  
Không thể bảo đảm. JVM còn phải kiểm tra **cấu trúc, kiểu, verifier, phiên bản và các quy tắc liên kết**.

**5. Tại sao không sửa `00 41` thành `00 3D` để chạy Java 21 bytecode trên Java 17?**  
Đổi hai byte chỉ đổi **nhãn phiên bản**, không hạ các cấu trúc/opcode/API đã được mã hóa cho release cao hơn. File có thể không còn hợp lệ hoặc vẫn không chạy được.

**6. `javap -verbose` giúp kiểm tra gì?**  
Đọc thông tin **thực tế của class file**: phiên bản major/minor, constant pool, cấu trúc class/method, descriptor và những thuộc tính mà công cụ hỗ trợ hiển thị.

Khi điều tra một tệp `.class`, hãy kiểm tra theo thứ tự: **4 byte magic `CAFEBABE` → 2 byte minor → 2 byte major → toàn bộ cấu trúc và bytecode → khả năng tương thích runtime/dependency**. Ba trường đầu giúp phát hiện sớm sai định dạng hoặc không tương thích phiên bản; chính các bước đọc và kiểm tra tiếp theo mới xác định ClassFile có thể được JVM nạp và thực thi hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-file-format-002">Tương thích phiên bản class và giới hạn của cờ --enable-preview</a>

<details>
<summary>Xem chi tiết</summary>

# Tương thích phiên bản class và giới hạn của cờ `--enable-preview`

Khi biên dịch Java, `javac` tạo ra các tệp `.class` chứa bytecode và metadata cho JVM. **Một JVM không thể nạp mọi tệp `.class` của mọi bản Java.** Nó kiểm tra phiên bản định dạng class file, cấu trúc và các ràng buộc khác trước khi thực thi. Với tính năng **preview**, còn có một quy tắc nghiêm ngặt hơn: class file phụ thuộc preview của **Java N** chỉ được nạp bằng **Java N** khi preview đã được bật. Việc bật `--enable-preview` trên **Java N+1** không cho phép chạy class file preview của Java N.

Bài này giải thích `major_version`, `minor_version`, nguyên nhân phổ biến của `UnsupportedClassVersionError`, cách `javac --release` tác động đến tương thích, và giới hạn chính xác của `--enable-preview` ở cả lúc **biên dịch** lẫn **chạy**. Các ví dụ dùng JDK 17 để có một trường hợp preview thực tế; bảng và quy tắc chuẩn dựa trên *Java Virtual Machine Specification* (JVMS).

## 1. Tệp `.class` chứa thông tin phiên bản nào?

Trong class file, phần đầu có cấu trúc khái niệm:

```text
Địa chỉ byte   Dữ liệu (big-endian)   Ý nghĩa
0..3           CA FE BA BE          magic number
4..5           u2 minor_version     phiên bản phụ
6..7           u2 major_version     phiên bản chính
8..9           u2 constant_pool_count
...            ...                  constant pool, class metadata, method code...
```

Đây là metadata của **định dạng class file**, không trực tiếp là chuỗi `java -version` của máy biên dịch. JVM sử dụng cặp `major.minor` để quyết định liệu nó có hỗ trợ cách mã hóa class file hay không.

- **`major_version`** cho biết thế hệ định dạng bytecode gắn với một release Java.
- **`minor_version`** là phiên bản phụ; từ Java 12, giá trị đặc biệt **65535** có ý nghĩa dành cho class file **phụ thuộc preview features** của release đó.
- **`CAFEBABE`** là magic number nhận diện định dạng, không phải số phiên bản Java.
- Một tệp `.jar` là container ZIP chứa nhiều tệp `.class` và tài nguyên; **không có một major version duy nhất cho toàn JAR** nếu các class bên trong khác phiên bản.

Ví dụ kết quả từ `javap -verbose Example`:

```text
minor version: 0
major version: 61
```

Nghĩa là định dạng class file **61.0**, tương ứng **Java 17**, không phải “Java phiên bản 61”. Nếu `minor version: 65535` với `major version: 61`, class đó mang dấu phụ thuộc **preview Java 17**.

## 2. Class-file major version và release Java

Các release Java có các mức major phổ biến:

| Java SE / JDK | Class-file major | Ví dụ |
| --- | ---: | --- |
| Java 8 | 52 | `52.0` |
| Java 11 | 55 | `55.0` |
| Java 12 | 56 | `56.0` hoặc `56.65535` nếu phụ thuộc preview |
| Java 17 | 61 | `61.0` hoặc `61.65535` |
| Java 21 | 65 | `65.0` hoặc `65.65535` |
| Java 22 | 66 | `66.0` hoặc `66.65535` |
| Java 23 | 67 | `67.0` hoặc `67.65535` |
| Java 24 | 68 | `68.0` hoặc `68.65535` |
| Java 25 | 69 | `69.0` hoặc `69.65535` |

Với các release Java hiện đại, major của Java N thường là **N + 44** (ví dụ Java 17 → 61); đây là mẹo nhớ, còn **bảng của JVMS** là nguồn chuẩn. Java 1.0/1.1 có chi tiết lịch sử riêng.

**Trường hợp class file thông thường (`minor = 0`):** một JVM mới hơn nhìn chung hỗ trợ major cũ hơn được đặc tả cho release đó. Chẳng hạn, bytecode `61.0` của Java 17 được JVM Java 21 hỗ trợ ở mức **định dạng class file**.

Nhưng “JVM nhận major” **chưa đủ** để bảo đảm chương trình chạy thành công. Vẫn phải xét API có tồn tại không, hệ module, thư viện phụ thuộc, thay đổi hành vi, reflection/encapsulation, native libraries, cấu hình hệ thống và những đặc điểm runtime liên quan.

**Trường hợp major cao hơn JVM hỗ trợ:** Java 17 không nạp được class `65.0` của Java 21. Cờ `--enable-preview` trên Java 17 **không tăng giới hạn major** lên 65.

### 2.1. Minor version: tại sao 65535 đặc biệt?

Theo JVMS, với **major từ 56 trở lên**, minor chỉ có hai giá trị hợp lệ trong định dạng class file: **0** hoặc **65535**.

- `M.0`: class file thông thường, không được đánh dấu phụ thuộc preview bằng cặp phiên bản.
- `M.65535`: class file phụ thuộc preview **của đúng release có major M**.
- `M.1`, `M.2`, ... không phải cách đặt version cho Java hiện đại.
- Những major cũ từ 45 đến 55 có quy tắc minor lịch sử khác; **không được suy diễn 65535 ở mọi class file cũ đều là preview**.

Ví dụ `61.65535` có nghĩa “preview Java 17”, **không** có nghĩa “một bytecode Java 17 có thể chạy trên bất kỳ JDK nào bật preview”.

## 3. Ma trận tương thích quan trọng nhất

Giả sử định dạng class file bình thường đúng quy cách và không vướng vấn đề thư viện/API khác:

| Class file | JVM chạy | Bật `--enable-preview` lúc chạy? | Kết quả ở mức class-version |
| --- | --- | --- | --- |
| `61.0` (Java 17 thường) | Java 17 | Không | **Được phép** |
| `61.0` | Java 21 | Không | **Được phép** |
| `65.0` (Java 21 thường) | Java 17 | Có hay không | **Không**, major quá mới |
| `61.65535` (preview Java 17) | Java 17 | Không | **Không**, preview mặc định tắt |
| `61.65535` | Java 17 | Có | **Được phép** |
| `61.65535` | Java 21 | Không hoặc có | **Không**, preview thuộc release khác |
| `65.65535` (preview Java 21) | Java 21 | Có | **Được phép** |
| `65.65535` | Java 17 | Có | **Không**, major quá mới |

“Được phép” chỉ có nghĩa **qua cổng version/preview**, không cam kết toàn bộ ứng dụng chạy được. Nếu class dùng API chưa có, thư viện còn thiếu, module bị chặn hoặc bytecode sai, chương trình vẫn có thể lỗi khi nạp, liên kết hay thực thi.

Quy tắc cốt lõi với preview class:

```text
class file = N_major . 65535
                       │
                       ▼
Runtime phải là Java N  +  --enable-preview lúc chạy
                       │
         Không được dùng Java N+1, N+2, ...
         dù các runtime mới cũng có --enable-preview
```

Đó là **chủ ý thiết kế**: preview features có thể đổi cú pháp/ngữ nghĩa, được hoàn thiện thành permanent feature, hoặc bị rút bỏ ở một release tiếp theo. JVM không hứa giữ khả năng chạy preview bytecode của bản trước.

## 4. Vì sao phiên bản major gây `UnsupportedClassVersionError`?

Ví dụ bạn cài **JDK 21** trên máy xây dựng, biên dịch mặc định:

```bash
javac App.java
```

Nếu `App.class` mang `major version: 65`, đem chạy bằng Java 17:

```bash
java App
```

Runtime Java 17 chỉ hiểu class-file major tối đa 61; lỗi điển hình là `java.lang.UnsupportedClassVersionError`, với thông điệp kiểu “class file version 65.0 ... recognizes up to 61.0”. Chuỗi lỗi thực tế phụ thuộc JVM và môi trường.

Một cách sửa khi **mã và API thực sự tương thích Java 17** là biên dịch bằng JDK đủ mới với target release 17:

```bash
javac --release 17 App.java
```

Khi đó `javac` tạo class format cho Java 17 và kiểm tra ngôn ngữ/API của release 17. **Không** có cơ sở để tin một ứng dụng sử dụng APIs/cú pháp chỉ có trên Java 21 sẽ vẫn biên dịch thành công bằng `--release 17`.

Nếu ứng dụng bắt buộc cần Java 21, cách xử lý là nâng runtime triển khai lên Java 21 tương ứng (hoặc điều chỉnh/mã hóa lại để tương thích bản đích).

### 4.1. Khác nhau giữa `--release`, `--source`, `--target`

| Tùy chọn `javac` | Điều điều khiển |
| --- | --- |
| `--source N` hoặc `-source N` | Quy tắc cú pháp/ngôn ngữ của Java release N |
| `--target N` | Phiên bản class file được tạo |
| `--release N` | Kết hợp điều kiện nguồn, đích và **bề mặt API công khai** của platform release N mà công cụ hỗ trợ |

Trong các dự án cần hỗ trợ một Java runtime cũ, **ưu tiên `--release N`** khi thích hợp, vì chỉ đặt `--target N` không ngăn mọi trường hợp mã tham chiếu API Java mới hơn tại lúc biên dịch. Khi chạy trên máy cũ, API đó có thể gây `NoSuchMethodError`, `NoClassDefFoundError` hoặc lỗi liên kết tương ứng.

Ví dụ biên dịch mã Java 11-compatible trên JDK hỗ trợ `--release 11`:

```java
public class CompatibleApp {
    public static void main(String[] args) {
        System.out.println("Compatible class");
    }
}
```

Tệp lưu là **`CompatibleApp.java`**:

```bash
javac --release 11 CompatibleApp.java
javap -verbose CompatibleApp
java CompatibleApp
```

Khi `javac` hoàn tất, `javap` cho class này sẽ có:

```text
minor version: 0
major version: 55
```

Stdout của chương trình:

```text
Compatible class
```

Class `55.0` có thể được JVM Java 11 trở lên nạp **ở khía cạnh phiên bản class file**, miễn các yêu cầu runtime còn lại được đáp ứng. Tùy hệ thống bạn có thể cần chỉ định đầy đủ đường dẫn tới `javac`/`java` của đúng JDK, vì nhiều JDK cùng tồn tại.

### 4.2. Build tool cũng phải nhất quán

Trong dự án Maven/Gradle, kiểm tra **Java toolchain**, `sourceCompatibility` / `targetCompatibility`, `--release` tương ứng, bytecode của **thư viện phụ thuộc** và JDK thực sự dùng ở máy chạy.

Ví dụ mô tả cấu hình Gradle Groovy DSL để chọn JDK 17 và target Java 17:

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType(JavaCompile).configureEach {
    options.release = 17
}
```

Đoạn trên là **minh họa cho một dự án có plugin `java` phù hợp**, không phải một chương trình Java độc lập. Chọn toolchain giúp điều khiển compiler được dùng, còn `options.release` kiểm soát mục tiêu biên dịch. Cần kiểm tra thêm dependencies, plugins và JVM chạy thử nghiệm/triển khai. Đừng mặc định setting của một IDE đã ảnh hưởng mọi tác vụ build hoặc container.

## 5. Preview feature là gì? Bật cờ được những gì?

**Preview feature** là tính năng Java được đặc tả và hiện thực đủ để thử nghiệm nhưng **chưa được cam kết vĩnh viễn**. Nó có thể là cú pháp ngôn ngữ, khả năng VM hoặc preview API được đưa ra lấy phản hồi trước khi đưa vào chính thức.

Hai bước có thể cần trong vòng đời chương trình:

1. **Compile time:** bật preview trên đúng bản `javac` nếu mã sử dụng preview feature được bản JDK đó hỗ trợ.
2. **Run time:** bật preview trên **đúng bản Java** khi chạy những class files yêu cầu preview, hoặc cần thực thi tính năng runtime preview.

Một số tính năng về sau trở thành chuẩn và không cần `--enable-preview` trong phiên bản đã chính thức hóa. Một tính năng preview của Java N cũng có thể bị thay đổi hoặc bị loại bỏ ở N+1; không nên phụ thuộc vào một preview feature như hợp đồng tương thích lâu dài.

### 5.1. Cú pháp biên dịch điển hình

Khi dùng **JDK 17** và sử dụng tính năng preview ở Java 17:

```bash
javac --enable-preview --release 17 Preview17.java
```

Cách tương đương thường dùng với compiler thích hợp:

```bash
javac --enable-preview --source 17 Preview17.java
```

Với `javac` hiện đại, muốn bật preview của một release phải chọn **release hiện tại mà compiler đó hỗ trợ preview**. Ví dụ, `javac` từ JDK 21 **không thể** dùng `--enable-preview --release 17` để tái tạo hay bật preview của Java 17. Lệnh biên dịch preview Java 17 ở trên cần **JDK 17**.

Nếu chỉ viết:

```bash
javac --enable-preview Preview17.java
```

Cú pháp cụ thể về việc yêu cầu `--release`/`--source` khi bật preview phụ thuộc quy tắc của `javac` tương ứng; cách minh bạch, dễ tái lập là **nêu rõ `--enable-preview --release <phiên bản JDK hiện tại>`** như ví dụ.

### 5.2. Cú pháp chạy

Nếu class file đã được đánh dấu phụ thuộc preview của **Java 17**, chạy bằng **Java 17**:

```bash
java --enable-preview Preview17
```

Chạy bằng Java 17 nhưng bỏ cờ có thể gặp `UnsupportedClassVersionError` báo preview chưa bật. Chạy bằng Java 21, dù có `--enable-preview`, vẫn không được nạp class `61.65535`: đó là preview **Java 17**, không phải preview Java 21.

`--enable-preview` áp dụng cho preview features **của release đang dùng**; không cho chọn riêng từng preview feature. Cờ này cũng không đồng nghĩa với việc kích hoạt incubator modules, truy cập API nội bộ, mở module packages hay bỏ qua class verifier.

## 6. Ví dụ preview Java 17: nhìn thấy `61.65535`

Java 17 có **pattern matching for switch** ở trạng thái *preview*. Dùng ví dụ này **trên JDK 17** để minh họa ranh giới thực tế.

Tạo file **`Preview17.java`**:

```java
public class Preview17 {

    static String describe(Object value) {
        return switch (value) {
            case String text -> "String: " + text;
            case Integer number -> "Integer: " + number;
            default -> "Other";
        };
    }

    public static void main(String[] args) {
        System.out.println(describe("JVM"));
        System.out.println(describe(42));
    }
}
```

Biên dịch bằng **JDK 17**:

```bash
javac --enable-preview --release 17 Preview17.java
```

Compiler có thể in ghi chú/cảnh báo liên quan đến preview và tùy chọn chi tiết hóa cảnh báo; đó là **diagnostic trên stderr**, không phải stdout của chương trình. Sau khi biên dịch, kiểm tra metadata:

```bash
javap -verbose Preview17
```

Các dòng phiên bản cần thấy:

```text
minor version: 65535
major version: 61
```

Chạy trên **Java 17**:

```bash
java --enable-preview Preview17
```

**Stdout chính xác:**

```text
String: JVM
Integer: 42
```

Giải thích:

1. `switch` dùng `case String text` và `case Integer number` là pattern matching for switch, preview ở Java 17.
2. `javac` JDK 17 khi biên dịch theo chế độ preview đánh dấu class dùng tính năng này bằng phiên bản **61.65535**.
3. Java 17 chạy class đó khi bật preview; không cần “hai lớp runtime” hoặc dịch lại ngay trong lệnh `java`.
4. Nếu chuyển `Preview17.class` sang Java 21 thì JVM Java 21 **không được nạp nó**, dù bạn chạy `java --enable-preview Preview17`.
5. Muốn chạy trên Java 21, bạn phải **biên dịch lại từ source với compiler và cú pháp/feature hợp lệ của Java 21**, rồi kiểm tra lại major/minor. Không có lời hứa tính năng preview cùng hành vi giữa hai release.

**Chú ý độ tin cậy:** ví dụ yêu cầu cài JDK 17 cụ thể; compiler JDK 24 hoặc JDK 21 không thể đóng vai trò “compiler preview Java 17” bằng cách đặt lại `--release 17`. Đây là giới hạn thật của preview, không phải lỗi của mã nguồn.

### 6.1. Ba phép thử lỗi có mục đích

Sau khi bạn có `Preview17.class` dạng `61.65535`:

**Thử 1: Java 17 không bật preview**

```bash
java Preview17
```

Kết quả mong đợi: JVM từ chối nạp class, thường là `UnsupportedClassVersionError` với nội dung giải thích preview chưa bật. **Không kỳ vọng stdout chương trình** vì `main` chưa được thực thi.

**Thử 2: Java 17 bật preview**

```bash
java --enable-preview Preview17
```

Kết quả: chạy thành công và in hai dòng đã nêu.

**Thử 3: Java 21 bật preview**

```bash
java --enable-preview Preview17
```

Nếu `java` lúc này thực sự là Java 21, class `61.65535` **không hợp lệ cho release Java 21**. Việc Java 21 bình thường hỗ trợ nhiều class major cũ **không** mở rộng sang preview class của Java 17. Lỗi cụ thể có thể thay đổi, nhưng runtime phải từ chối theo quy tắc JVMS.

Khi thử, hãy dùng đường dẫn tuyệt đối hoặc thiết lập `JAVA_HOME`/`PATH` một cách rõ ràng để bảo đảm lệnh đang gọi **đúng runtime muốn kiểm tra**.

## 7. `--enable-preview` KHÔNG giải quyết những gì?

| Tình huống | Vì sao cờ không giúp? | Hướng xử lý phù hợp |
| --- | --- | --- |
| Java 17 chạy class `65.0` | Major 65 lớn hơn mức runtime Java 17 hỗ trợ | Nâng runtime lên Java 21 hoặc biên dịch lại target 17 nếu source/API cho phép |
| Java 21 chạy class `61.65535` | Preview bytecode gắn với đúng Java 17 | Biên dịch lại source theo Java 21 hợp lệ; không thể bật preview cũ trên Java 21 |
| `javac` JDK 21 bật preview của Java 17 | Compiler chỉ hỗ trợ preview của chính release hiện tại | Dùng JDK 17 để biên dịch preview Java 17 hoặc cập nhật mã sang Java 21 |
| Class có lỗi verification/format | Cờ không bỏ qua bộ kiểm tra class file | Sửa bytecode, generator/instrumentation hoặc dependency |
| Thiếu thư viện hay API lúc chạy | Cờ không tự cung cấp module/JAR hoặc API | Đồng bộ dependency, module path/classpath, release API |
| Truy cập package nội bộ bị module chặn | Preview khác với mở đóng gói module | Dùng public API hoặc cấu hình module phù hợp khi thực sự cần |
| Dùng **incubator module** như Vector API trong một số JDK | Incubator không tự bật chỉ nhờ preview flag | Xem tài liệu module và cấu hình `--add-modules` riêng |
| Muốn chạy chương trình preview không sửa mã về lâu dài | Preview không cam kết tương thích lâu dài | Theo dõi trạng thái từng release và chuyển sang API/cú pháp chuẩn khi sẵn sàng |

Đặc biệt, **`--enable-preview` không phải “bỏ giới hạn kiểm tra phiên bản”**. Nó thay đổi trạng thái kích hoạt **những preview features được chính runtime/compiler của release đó hỗ trợ**, và runtime vẫn kiểm tra major/minor chặt chẽ.

## 8. Preview API, preview language feature và class file

Một class file `M.65535` có thể được tạo khi compiler cần ghi nhận phụ thuộc preview, chẳng hạn source sử dụng **preview language feature**. Không nên cho rằng mọi tệp `.class` được biên dịch trong một lệnh có `--enable-preview` đều nhất thiết phải có minor `65535` nếu chúng không phụ thuộc preview; **luôn xem phiên bản thực tế của từng class file**.

Các khía cạnh cần phân biệt:

- **Preview language feature:** cú pháp/biểu thức mới; compiler có thể phải đánh dấu output class file là preview-dependent.
- **Preview VM feature:** khả năng của JVM được xác định bởi JVMS của release đó; chạy phụ thuộc runtime hỗ trợ và trạng thái bật preview.
- **Preview API:** API được trình bày là preview của release; việc sử dụng có điều kiện/cảnh báo của compiler và quy tắc triển khai riêng.
- **Incubator API/module:** cơ chế thử nghiệm khác; `--enable-preview` **không** tự động cấp quyền truy cập hoặc giải quyết cấu hình module cho incubator.

Không thể xác nhận “class này dùng preview hay không” chỉ bằng cách nhìn chuỗi ký tự `--enable-preview` trong một file build. Cần kiểm tra cả **source, dependencies, compiler diagnostics và class-file metadata**, đặc biệt khi các tác vụ build biên dịch nhiều module ở các release khác nhau.

### 8.1. Preview đã trở thành chuẩn thì sao?

Khi một tính năng được chuẩn hóa trong bản Java mới, việc dùng **hình thức cú pháp/API được bản mới công nhận** có thể không cần preview flag nữa. Tuy nhiên, **class preview được tạo ở bản cũ vẫn mang minor 65535 của bản cũ** và không tự biến thành class thường.

Ví dụ pattern matching for `switch` trở thành permanent từ Java 21; dù source tương đương có thể biên dịch trên JDK 21 mà không dùng preview cho tính năng đó, tệp `Preview17.class` đã đánh dấu `61.65535` **không đổi metadata** chỉ vì đem tới Java 21.

**Cách đúng:** biên dịch lại source trên toolchain mới với cấu hình mới, sau đó kiểm tra class format được tạo.

## 9. Tương thích giữa nhiều phiên bản và JAR nhiều release

Với thư viện, có thể gặp **multi-release JAR**: một JAR có class cơ sở và class chuyên biệt ở đường dẫn dạng:

```text
lib.jar
├── META-INF/MANIFEST.MF     (Multi-Release: true)
├── com/example/Api.class
└── META-INF/versions/17/com/example/Api.class
```

Runtime phù hợp có thể chọn class chuyên biệt cho phiên bản hỗ trợ; cấu trúc cụ thể và quy tắc lựa chọn theo tài liệu multi-release JAR. Cơ chế này giúp cung cấp implementation theo phiên bản, **không** biến preview bytecode Java 17 thành mã chạy hợp lệ trên Java 21.

Ví dụ, nếu class trong `META-INF/versions/17` là `61.65535`, bản đó vẫn chịu quy tắc **preview đúng release Java 17**. Khi dùng multi-release JAR, cần thiết kế từng class/path đúng target JVM và kiểm tra class được runtime thực sự chọn.

Một JAR có thể chứa một class `65.0` ở nhánh dành cho Java 21 và class `55.0` ở nhánh cơ sở cho Java 11; điều đó không có nghĩa Java 11 được phép nạp trực tiếp class major 65. Đường dẫn và metadata manifest quyết định khả năng lựa chọn bản thích hợp.

## 10. Kiểm tra phiên bản thực sự của toolchain, class và runtime

### 10.1. Bộ lệnh cơ bản

```bash
# Java runtime đang được gọi
java -version

# Compiler đang được gọi
javac -version

# Chi tiết header/class bytecode; lệnh cần JDK
javap -verbose -p CompatibleApp

# Trên JAR, kiểm tra class cụ thể sau khi giải nén theo cách phù hợp
jar tf application.jar
```

`java` và `javac` có thể đến từ **hai JDK khác nhau** nếu `PATH` hoặc IDE/CI không nhất quán. Cần xác minh đường dẫn executable và cấu hình toolchain trong CI/container.

`javap` hiển thị metadata class (ví dụ major/minor, constant pool, method code), nhưng JVM đang chạy là một thành phần khác. `javap` từ JDK mới có thể đọc định dạng không chạy được trên Java runtime cũ.

### 10.2. Cách đọc ba dạng lỗi

**Lỗi A — class file quá mới:**

```text
... UnsupportedClassVersionError ...
... class file version 65.0 ...
... recognizes class file versions up to 61.0 ...
```

Đây là **minh họa mẫu thông báo**, không phải stdout cố định của mọi JVM. Runtime ở mức Java 17 gặp class Java 21 thường sẽ báo chênh lệch major 65 và 61. Sửa target/build hoặc runtime.

**Lỗi B — class file preview nhưng chưa bật:**

```text
... UnsupportedClassVersionError ...
... class file version 61.65535 ...
... preview features are not enabled ...
```

Kiểm tra đúng Java 17 và thêm `--enable-preview` **lúc chạy**, nếu bạn thật sự chấp nhận dùng preview của release đó.

**Lỗi C — preview thuộc release khác:**

```text
... UnsupportedClassVersionError ...
... class file version 61.65535 ...
... preview features ... unsupported for this runtime ...
```

Trên Java 21, class `61.65535` không chạy được dù bật preview. Sửa bằng cách **biên dịch lại từ mã nguồn tương thích Java 21** hoặc chạy đúng Java 17 với preview.

### 10.3. Quy trình chẩn đoán 5 bước

1. **Xác định file thực sự được nạp:** classpath/module path, JAR và version-specific path; không đoán dựa vào tên thư viện.
2. **Đọc major/minor:** dùng `javap -verbose` hoặc trình đọc class-file metadata phù hợp.
3. **Kiểm tra runtime thực sự:** `java -version`, toolchain/launcher của IDE, CI image/container.
4. **Phân nhánh:** major quá cao, preview minor 65535 sai release/chưa bật, hay lỗi khác như thiếu API/dependency?
5. **Sửa ở đúng lớp:** chỉnh `--release` khi source/APIs cho phép; đồng bộ JDK lúc build và chạy; biên dịch lại preview source trên đúng release; cập nhật dependency, sau đó kiểm tra lại class thực tế.

Không nên lấy `--enable-preview` làm một bước “thử đại” mỗi khi gặp `UnsupportedClassVersionError`. Thường chỉ nhìn **major.minor** đã đủ phân biệt hai trường hợp rất khác nhau.

## 11. Áp dụng vào build và triển khai

Một ứng dụng cần hỗ trợ nhiều runtime nên đặt **Java target nhỏ nhất** theo yêu cầu sản phẩm, dùng CI thử nghiệm thực trên các runtime đích, và thiết kế phụ thuộc tương thích. Nếu chọn preview feature để học hoặc thử nghiệm, cần khóa công cụ và runtime vào đúng **Java release**, đồng thời có kế hoạch chuyển đổi khi preview đổi trạng thái.

Checklist cho developer:

- Xác định version thực của `java` và `javac` ở máy local, CI và container.
- Chọn `--release N` hoặc toolchain theo runtime muốn hỗ trợ; kiểm tra dependencies cũng tương thích.
- Nếu dùng preview: khai báo release cụ thể của JDK compiler, dùng `--enable-preview` đúng lúc biên dịch/chạy và chạy test trên **chính release đó**.
- Xem `javap -verbose` ở class quan trọng; với JAR/multi-module kiểm tra class files được chọn thay vì chỉ một class.
- Khi nâng JDK, **biên dịch lại preview source**; không mang nguyên `N.65535` sang N+1.
- Với tính năng đã trở thành chuẩn, cập nhật cú pháp/toolchain theo release mới rồi xác minh `minor` của class file được tạo.
- Giữ build có khả năng tái lập: ghi rõ JDK/toolchain version, release flags và image triển khai.

## 12. Bài tập tự kiểm tra (có đáp án)

**Câu 1.** Class file có `major 61`, `minor 0`. Trên Java 21, có cần `--enable-preview` không?

**Đáp án:** **Không** vì class không đánh dấu phụ thuộc preview. Java 21 chấp nhận class major 61 ở mức định dạng; còn khả năng chạy trọn vẹn phụ thuộc APIs/libraries/runtime.

**Câu 2.** Class file có `major 61`, `minor 65535`. Trên Java 21 thêm `--enable-preview` có chạy được không?

**Đáp án:** **Không.** Đó là preview **Java 17**; class phải chạy bằng Java 17 với preview enabled, hoặc biên dịch lại source cho Java 21.

**Câu 3.** Dùng `javac` JDK 21 để chạy `--release 17 --enable-preview` có thể bật preview Java 17 không?

**Đáp án:** **Không**. Preview chỉ được compiler bật cho release hiện tại mà compiler đó hỗ trợ. Với source preview Java 17, cần compiler JDK 17 (hoặc chuyển mã sang cấu trúc được JDK 21 chấp nhận).

**Câu 4.** Vì sao `javac --target 11` có thể là lựa chọn chưa đủ khi hỗ trợ Java 11?

**Đáp án:** Vì target class format không nhất thiết chặn tham chiếu tới những API nền tảng chỉ có trên JDK biên dịch mới hơn. `--release 11` kiểm soát cả ngôn ngữ, target và bề mặt API được hỗ trợ.

**Câu 5.** Nếu chạy class `65.0` trên JVM Java 17 và nhận `UnsupportedClassVersionError`, có nên chỉ thêm `--enable-preview`?

**Đáp án:** **Không.** Đây là vấn đề **major 65 > 61**, không phải preview minor 65535. Nâng runtime hoặc biên dịch với target 17 nếu mã và dependencies cho phép.

**Câu 6.** Pattern matching for switch đã thành chuẩn ở Java 21. Có thể chạy file `61.65535` đã biên dịch Java 17 preview trực tiếp trên Java 21 không?

**Đáp án:** **Không.** Class-file minor vẫn đánh dấu preview Java 17; cần **biên dịch lại** để tạo class-file version thích hợp.

## Ghi nhớ cuối bài

- **`major.minor` mô tả định dạng class file**; major liên quan release Java, minor `65535` từ Java 12 biểu thị phụ thuộc preview của chính release đó.
- **Class thường `N.0`** có thể được runtime mới hơn nạp ở mức định dạng, nhưng compatibility của API/module/dependency vẫn phải kiểm tra.
- **Class preview `N.65535`** chỉ được nạp trên **Java N với `--enable-preview`**. Runtime mới hơn dù có preview flag cũng không nhận preview class của N.
- **`--release`** hữu ích cho target Java cũ khi không phụ thuộc preview; **`--enable-preview`** là cơ chế thử nghiệm preview của đúng JDK, không phải công cụ chuyển đổi bytecode, backport tính năng hoặc bỏ qua verification.

### Tài liệu chuẩn

- [JVMS Java SE 21 — ClassFile structure (`4.1`), version và preview](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.1).
- [JVMS Java SE 21 — Preview features (`1.5`)](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-1.html#jvms-1.5).
- [JEP 12 — Preview Language and VM Features](https://openjdk.org/jeps/12).
- [JDK 17 `javac` manual: `--release`, `--enable-preview`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html).
- [JDK 17 `java` launcher manual: `--enable-preview`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).
- [JDK 17 `javap` manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-file-format-003">Tên nhị phân, tên nội bộ và descriptor của trường, phương thức, mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Tên nhị phân, tên nội bộ và descriptor của trường, phương thức, mảng

## 1. Vì sao phải phân biệt những cách biểu diễn tên?

Trong Java, một lớp có thể được nhắc đến dưới nhiều hình thức:

~~~text
Mã nguồn Java:             java.lang.String
Tên nhị phân (binary):     java.lang.String
Tên nội bộ JVM (internal): java/lang/String
Descriptor kiểu tham chiếu: Ljava/lang/String;
Descriptor mảng String[]:  [Ljava/lang/String;
~~~

Những chuỗi này **không hoán đổi tùy ý**. Chúng phục vụ những vai trò khác nhau trong Java source, Java Virtual Machine Specification (JVMS), tệp **.class**, công cụ đọc bytecode và Reflection API. Sự nhầm lẫn thường chỉ lộ ra khi viết code xử lý bytecode, tìm lớp bằng reflection, sinh proxy, dùng instrumentation, truy vấn phương thức theo descriptor hoặc giải thích thông tin constant pool.

Sau bài học, bạn có thể:

- Nhận diện tên nhị phân, tên nội bộ và tên canonical/source của lớp thông thường và lớp lồng nhau.
- Đọc và tự tạo **field descriptor** cho primitive, tham chiếu và mảng một hay nhiều chiều.
- Đọc và tự tạo **method descriptor**, bao gồm danh sách tham số, kiểu trả về và **void**.
- Giải thích vì sao **java.lang.String**, **java/lang/String**, **Ljava/lang/String;** và **[Ljava.lang.String;** xuất hiện ở những API/ngữ cảnh khác nhau.
- Hiểu cách các tên/descriptor xuất hiện trong **CONSTANT_Class**, **CONSTANT_NameAndType**, **CONSTANT_Fieldref** và **CONSTANT_Methodref**.
- Kiểm tra kết quả bằng **javap -s -p -v**, **Class.getName()**, **Class.getCanonicalName()**, **Class.forName()** và **MethodType**.

Trọng tâm là ý nghĩa chính xác của định dạng class file; bài học dùng Java 17+ để minh họa.

## 2. Tên trong Java: source, canonical và binary name

### 2.1. Tên nhị phân (binary name) là gì?

**Binary name** là tên được Java Language Specification (JLS, §13.1) định nghĩa để định danh kiểu lớp/interface theo những quy tắc sử dụng ở dạng nhị phân. Với lớp top-level phổ biến, tên nhị phân giống tên đầy đủ thường thấy trong mã nguồn:

~~~text
java.lang.String
java.util.Map
com.example.app.Customer
~~~

Dấu chấm **.** phân tách thành phần package. Với một **member class** được khai báo trong lớp khác, binary name dùng **$** để ghép tên nhị phân của lớp bao ngoài và tên đơn giản của lớp thành viên:

~~~java
// Ví dụ mô tả cấu trúc khai báo:
package com.example.app;

class Outer {
    static class Inner {}
}
~~~

~~~text
Tên source/canonical của member class: com.example.app.Outer.Inner
Tên binary của member class:          com.example.app.Outer$Inner
Tên binary của top-level class:       com.example.app.Outer
~~~

Không phải mọi ký tự **$** đều chứng minh có lớp lồng nhau: Java cho phép **$** trong identifier, và các lớp local/anonymous có quy tắc binary name riêng do compiler tạo. Cũng đừng dùng quy tắc “thay mọi dấu chấm bằng $” để biến source name thành binary name: dấu chấm package vẫn là dấu chấm.

Tên nhị phân còn gắn với danh tính lớp ở runtime cùng **defining class loader**; hai class loader khác nhau có thể định nghĩa các lớp có cùng binary name mà JVM vẫn coi là những runtime class khác nhau.

### 2.2. Canonical name không đồng nghĩa binary name

**Canonical name** là cách viết tên theo quy tắc ngôn ngữ Java cho những kiểu có canonical name. Với member class, cách viết này dùng dấu chấm giữa **Outer** và **Inner**; binary name dùng **$**.

Reflection thường cho thấy:

| Lớp | **Class.getName()** | **Class.getCanonicalName()** |
| --- | --- | --- |
| **java.lang.String** | **java.lang.String** | **java.lang.String** |
| **Outer.Inner** | **Outer$Inner** (nếu không có package) | **Outer.Inner** |
| **int** | **int** | **int** |
| **String[]** | **[Ljava.lang.String;** | **java.lang.String[]** |
| **int[][]** | **[[I** | **int[][]** |

**getCanonicalName()** có thể trả **null** cho lớp local hoặc anonymous không có canonical name. **getName()** của mảng dùng một biểu diễn dạng descriptor nhưng **tên reference component được viết bằng dấu chấm**, khác với object descriptor trong class file dùng dấu gạch chéo. Phần sau sẽ giải thích rõ.

### 2.3. Internal name: tên nội bộ của JVM

Khi biểu diễn binary name của lớp/interface trong những cấu trúc class file cần **internal form**, JVMS thay các dấu chấm package **.** thành dấu gạch chéo **/**.

~~~text
Binary name:   java.lang.String
Internal name: java/lang/String

Binary name:   com.example.app.Outer$Inner
Internal name: com/example/app/Outer$Inner
~~~

**Dấu $ của member class được giữ nguyên**, không đổi thành dấu gạch chéo. **/** trong internal name là dấu phân tách thành phần package/class theo ngữ pháp JVM, **không phải lời cam kết đường dẫn tệp thực trên ổ đĩa**.

Ví dụ, trong một JAR, entry **com/example/app/Outer$Inner.class** thường tương ứng với lớp đó; nhưng việc thực thi có thể dùng module, class loader tùy biến, byte array hay các nguồn lớp khác. Internal name là khái niệm định danh trong class file, không nên đồng nhất nó với API filesystem.

## 3. Ba chuỗi dễ bị dùng sai

Xét kiểu **java.lang.String**:

| Chuỗi | Vai trò đúng | Dùng ở đâu? |
| --- | --- | --- |
| **java.lang.String** | Binary name | **Class.forName("java.lang.String")**, metadata/tên lớp theo quy tắc Java |
| **java/lang/String** | Internal name của lớp | **CONSTANT_Class** cho non-array class; phần tên lớp bên trong object descriptor |
| **Ljava/lang/String;** | Field descriptor của một tham chiếu đến String | **field_info.descriptor_index**; tham số/kiểu trả về của method descriptor |

**Tiền tố L và dấu ;** là thành phần ngữ pháp descriptor của một **object/reference type**. Vì vậy **java/lang/String** riêng lẻ **không phải field descriptor** và **Ljava/lang/String;** **không phải internal name của class**.

Một số công cụ bytecode nhận tham số tên dưới dạng internal name, còn API reflection nhận binary name. Trước khi truyền một chuỗi tên vào thư viện, phải đọc rõ contract: một API yêu cầu **owner internal name**, một API khác yêu cầu **type descriptor**, và API khác nữa cần **binary name**. Chuyển đổi giữa chúng chỉ được thực hiện theo đúng loại dữ liệu.

## 4. Descriptor là gì trong class file?

**Descriptor** là chuỗi cú pháp ngắn gọn biểu diễn **kiểu của field** hoặc **tập tham số và kiểu trả về của method**. Chuỗi descriptor được lưu trong constant pool dưới dạng chuỗi UTF-8 biến thể theo định dạng class file; các cấu trúc **field_info**, **method_info** hoặc **NameAndType** tham chiếu đến nó bằng chỉ số.

Đặc tả JVMS phân biệt:

- **Field descriptor**: biểu diễn một kiểu giá trị, bao gồm primitive, object/reference hoặc array.
- **Method descriptor**: biểu diễn lần lượt các kiểu tham số trong **(...)** rồi đến kiểu trả về.
- **Generic signature**: thông tin bổ sung về generic type arguments/type variables; dùng ngữ pháp **Signature** khác descriptor thông thường.

Descriptor là dữ liệu mang ý nghĩa thực thi/liên kết; **generic signature không thay thế descriptor**.

## 5. Bảng mã field descriptor cho primitive

Mỗi kiểu primitive dùng đúng một ký tự ASCII:

| Kiểu Java | Descriptor | Ví dụ trường |
| --- | --- | --- |
| **byte** | **B** | **byte count** → **B** |
| **char** | **C** | **char initial** → **C** |
| **double** | **D** | **double score** → **D** |
| **float** | **F** | **float ratio** → **F** |
| **int** | **I** | **int age** → **I** |
| **long** | **J** | **long total** → **J** |
| **short** | **S** | **short level** → **S** |
| **boolean** | **Z** | **boolean active** → **Z** |

Hai điều cần nhớ:

1. **J** là mã của **long**, không phải **L**. Ký tự **L** dành cho kiểu tham chiếu object.
2. **V** biểu diễn **void** khi nó đứng ở **vị trí kiểu trả về của method**; **void không phải kiểu field**, cũng không được làm thành phần của array descriptor.

Ví dụ **int** là **I**, không phải **int**; **boolean** là **Z**, không phải **B** hay **boolean**.

Các ký tự descriptor nhận diện **kiểu khai báo**. Việc JVM dùng computational **int** cho nhiều lệnh thao tác với byte/short/char/boolean không làm các descriptor **B/S/C/Z** trở thành **I**.

## 6. Descriptor của kiểu tham chiếu đối tượng

Ngữ pháp object type:

~~~text
L<internal-name>;
~~~

Trong đó **L** là ký tự bắt đầu, **internal-name** dùng dấu **/** và dấu **;** kết thúc kiểu.

Ví dụ:

| Kiểu Java | Internal name | Field descriptor |
| --- | --- | --- |
| **String** | **java/lang/String** | **Ljava/lang/String;** |
| **Object** | **java/lang/Object** | **Ljava/lang/Object;** |
| **java.util.List** | **java/util/List** | **Ljava/util/List;** |
| **com.example.Customer** | **com/example/Customer** | **Lcom/example/Customer;** |
| **com.example.Outer.Inner** | **com/example/Outer$Inner** | **Lcom/example/Outer$Inner;** |

Với **Lcom/example/Outer$Inner;**:

~~~text
L        com/example/Outer$Inner       ;
^        ^                              ^
bắt đầu  internal binary name           kết thúc object descriptor
~~~

Không viết **Lcom.example.Outer.Inner;** trong class-file descriptor. Dấu chấm giữa **Outer.Inner** của canonical/source name cũng không thay thế được **$** của member-class binary name.

### 6.1. Các tên không đủ điều kiện (unqualified names)

Tên field hoặc tên method được lưu dưới dạng **unqualified name**, ví dụ **count**, **parse**, **equals**. Chúng không chứa dấu **.**, **;**, **[**, **/**; phần lớn method name còn bị hạn chế **<** và **>**, ngoại trừ tên đặc biệt **<init>** và **<clinit>**.

Tên field hoặc tên method **không được gộp cùng descriptor thành một chuỗi trong class file**. Chúng xuất hiện ở các mục riêng: tên **count** + descriptor **I**, hoặc tên **accept** + descriptor **(I)Z**. Cách trình bày kiểu **accept(I)Z** thường gặp trong tài liệu/log là cách ghép để dễ đọc, không phải cú pháp field descriptor.

## 7. Descriptor của mảng: thêm một dấu [ cho mỗi chiều

**Array descriptor** được tạo bằng một hoặc nhiều ký tự **[** đứng trước descriptor của loại phần tử cuối cùng. Mỗi dấu **[** tương ứng với một chiều.

~~~text
T[]     -> [ + descriptor(T)
T[][]   -> [[ + descriptor(T)
T[][][] -> [[[ + descriptor(T)
~~~

Ví dụ:

| Java type | Array descriptor | Cách đọc |
| --- | --- | --- |
| **int[]** | **[I** | Mảng một chiều, thành phần là int |
| **int[][]** | **[[I** | Mảng hai chiều, thành phần cuối là int |
| **boolean[]** | **[Z** | Mảng boolean một chiều |
| **byte[][]** | **[[B** | Mảng byte hai chiều |
| **String[]** | **[Ljava/lang/String;** | Mảng String một chiều |
| **String[][]** | **[[Ljava/lang/String;** | Mảng String hai chiều |
| **Outer.Inner[]** | **[Lcom/example/Outer$Inner;** | Mảng member class một chiều |
| **double[][][]** | **[[[D** | Mảng double ba chiều |

**Vị trí dấu [ quan trọng:** descriptor **[I** (int[]) khác **I** (int); **[[I** (int[][]) cũng khác **[I** (int[]). Trong Java, mảng nhiều chiều thực chất là mảng chứa các phần tử là mảng; descriptor biểu diễn cấp lồng, không quy định mọi hàng phải có cùng độ dài.

**Giới hạn class file:** một array type descriptor hợp lệ có tối đa **255 chiều**; điều này không có nghĩa ứng dụng nên hoặc có đủ RAM để tạo mảng 255 chiều. Không thể tạo mảng kiểu **void[]** hay descriptor **[V**.

### 7.1. Mảng tham chiếu: dấu chấm hay dấu gạch chéo?

Phân biệt rất cẩn thận ba cách ghi:

~~~text
Kiểu source:
    java.lang.String[]

Field descriptor trong class file:
    [Ljava/lang/String;

Class.getName() của String[].class:
    [Ljava.lang.String;
~~~

**Class.getName()** đặc biệt: với mảng tham chiếu, nó sử dụng **dấu chấm trong phần tên binary của component type**, trong khi descriptor của mảng trong class file dùng **internal name với dấu /**. Hai biểu diễn trông gần giống nhau nhưng khác chuỗi ký tự.

Với primitive array, không có tên package phải chuyển đổi nên **int[]** cho chuỗi **[I** ở cả dạng array descriptor lẫn **getName()**.

### 7.2. Mảng cũng có Class object

Một đối tượng lớp mảng có thể được nhận qua **String[].class** hoặc **int[][].class**, và có tên hiển thị theo quy tắc runtime. Đây không làm phát sinh nguồn Java khai báo lớp **[I**. Các kiểu mảng là kiểu runtime riêng, và class file constant pool có thể chứa **CONSTANT_Class** mang tên mảng ở dạng descriptor, như **[Ljava/lang/String;**.

Đối với **CONSTANT_Class**:
- Nếu tham chiếu đến lớp/interface thông thường, chuỗi tên thường là **internal form**, ví dụ **java/lang/String**.
- Nếu tham chiếu đến array class, chuỗi biểu diễn **array descriptor**, ví dụ **[Ljava/lang/String;**.

Do vậy, khẳng định “mọi **CONSTANT_Class** đều chứa một internal name không có dấu [” là sai. Đây là khác biệt rất đáng kiểm tra khi đọc constant pool.

## 8. Method descriptor: (tham số)kiểu trả về

Ngữ pháp cốt lõi:

~~~text
MethodDescriptor:
    ( ParameterDescriptor* ) ReturnDescriptor

ParameterDescriptor:
    FieldType

ReturnDescriptor:
    FieldType | V
~~~

Nghĩa là: dấu **(**, nối trực tiếp các field descriptor của tất cả tham số **theo đúng thứ tự**, dấu **)**, rồi một descriptor của kiểu trả về hoặc **V** cho **void**.

Không có dấu phẩy hoặc khoảng trắng phân cách tham số trong descriptor chuẩn.

| Khai báo Java | Method descriptor |
| --- | --- |
| **void run()** | **()V** |
| **int sum(int a, int b)** | **(II)I** |
| **boolean valid(String text)** | **(Ljava/lang/String;)Z** |
| **String join(String a, String b)** | **(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;** |
| **long convert(int n, double d)** | **(ID)J** |
| **String[] lookup(int[] ids, String[][] names)** | **([I[[Ljava/lang/String;)[Ljava/lang/String;** |
| **void accept(boolean[] flags, long n)** | **([ZJ)V** |
| **Object identity(Object x)** | **(Ljava/lang/Object;)Ljava/lang/Object;** |

Phân tích ví dụ:

~~~text
([I[[Ljava/lang/String;)[Ljava/lang/String;
 | |                | |  
 | +-- int[]       | +-- String[] (return)
 +-- parameter 1   +-- parameter 2 is String[][]
~~~

Chính xác hơn, nội dung giữa dấu ngoặc **(** và **)** gồm hai tham số nối liền: **[I** và **[[Ljava/lang/String;**. Sau dấu **)** là **[Ljava/lang/String;**. Với ký tự **;**, phải biết nó kết thúc object descriptor; các ký tự tiếp theo có thể là descriptor của tham số tiếp theo.

### 8.1. Kiểu trả về là một phần của method descriptor

Hai method descriptors **(I)I** và **(I)J** khác nhau vì kiểu trả về khác nhau. JVM có thể phân biệt thông tin symbolic reference ở mức tên và descriptor; còn **Java source không cho overload một phương thức chỉ bằng cách thay kiểu trả về**.

~~~java
// Hai khai báo này trong cùng một lớp là lỗi ở Java source:
// int find(int id)  { return id; }
// long find(int id) { return id; }
~~~

Không được suy luận luật overload của ngôn ngữ Java hoàn toàn giống mọi quy tắc cấu trúc của tệp class. Tên method và descriptor cùng tham gia định danh/lien kết bytecode; compile-time rules của Java còn phụ thuộc signature/overload resolution.

### 8.2. Tham số this có nằm trong descriptor?

Không. Với phương thức instance, **this** được truyền ngầm bởi instruction gọi phương thức, **không có descriptor tham số this đứng ở đầu**.

~~~java
class Account {
    int deposit(int amount) {
        return amount;
    }
}
~~~

Method descriptor của **deposit** là **(I)I**, **không phải** **(LAccount;I)I**.

Khi xem bytecode của method instance, local slot 0 thường chứa **this**, nhưng layout local variables không biến **this** thành một tham số explicit của method descriptor.

### 8.3. Giới hạn số slot của tham số

JVMS giới hạn tổng **parameter length** của method descriptor ở **255 đơn vị**. Tham số **long** và **double** mỗi cái tính **2**; những kiểu còn lại, kể cả array/reference, tính **1**. Với invocation của phương thức instance/interface, giới hạn còn tính thêm **this** ngầm **1** đơn vị, dù **this** không xuất hiện trong chuỗi descriptor.

Đây là giới hạn của mô hình JVM, **không** phải giới hạn số ký tự văn bản của descriptor.

## 9. Nằm ở đâu trong class file và constant pool?

Một class file có cấu trúc **constant_pool**, rồi các bảng khai báo field và method. Ở mức tối giản:

~~~text
ClassFile
  constant_pool
    CONSTANT_Utf8        "demo/names/NameDemo"
    CONSTANT_Class       -> Utf8 internal name
    CONSTANT_Utf8        "count"
    CONSTANT_Utf8        "I"
    CONSTANT_Utf8        "check"
    CONSTANT_Utf8        "(I[Ljava/lang/String;)Z"
    CONSTANT_NameAndType -> name + descriptor
    CONSTANT_Fieldref    -> class + name_and_type
    CONSTANT_Methodref   -> class + name_and_type
  fields[]
    field_info
      name_index        -> "count"
      descriptor_index  -> "I"
  methods[]
    method_info
      name_index        -> "check"
      descriptor_index  -> "(I[Ljava/lang/String;)Z"
~~~

Sơ đồ thể hiện **vai trò** của các phần tử, không phải offset thực của một class file cụ thể. Constant pool dùng nhiều loại entry có quan hệ bằng chỉ số; một field declaration không nhất thiết tự có **Fieldref** trỏ ngược đến chính nó. **Fieldref/Methodref** xuất hiện khi có symbolic reference tương ứng (chẳng hạn một lệnh bytecode cần tham chiếu field/method).

Trong class file:
- **field_info** lưu **tên riêng** và **field descriptor**.
- **method_info** lưu **tên riêng** và **method descriptor**.
- **CONSTANT_NameAndType** ghép tên thành viên với descriptor để sử dụng trong symbolic reference và các cấu trúc khác.
- **CONSTANT_Fieldref**, **CONSTANT_Methodref** hoặc **CONSTANT_InterfaceMethodref** kết hợp owner class/interface và **NameAndType**.
- **CONSTANT_Class** thường chỉ tới internal name của class hoặc array descriptor, tùy đối tượng tham chiếu.

### 9.1. Ví dụ giải nghĩa một lời gọi

Giả sử mã Java:

~~~java
// Mã minh họa; cần lớp java.lang.String và đoạn mã trong một phương thức:
int length = "Java".length();
~~~

Symbolic reference của phương thức **String.length()** mô tả:

~~~text
Owner class internal name: java/lang/String
Method name:               length
Method descriptor:         ()I
~~~

Cách ghi quen thuộc:

~~~text
java/lang/String.length:()I
~~~

Đây là **ký pháp trình bày một symbolic reference**, chứ không phải một string method descriptor đơn lẻ. Descriptor chỉ là **()I**; owner và tên phương thức là thành phần riêng.

### 9.2. Phương thức khởi tạo

Constructor thường xuất hiện trong class file với method name **<init>** và descriptor kết thúc bằng **V**, chẳng hạn:

~~~text
<init>(Ljava/lang/String;I)V
~~~

Tên đặc biệt **<clinit>** biểu diễn phương thức khởi tạo lớp/interface trong class file, có descriptor **()V** theo quy tắc của JVM. Đây không phải những method được viết trực tiếp với tên **<init>** hoặc **<clinit>** trong Java source.

## 10. Ví dụ Java 17+ độc lập để quan sát tên và descriptor

Tạo cấu trúc tệp:

~~~text
demo/
  names/
    NameDemo.java
~~~

Nội dung của **demo/names/NameDemo.java**:

~~~java
package demo.names;

import java.lang.invoke.MethodType;

public class NameDemo {
    private int count;
    private String[] titles;
    private int[][] grid;
    private Item item;

    public static class Item {
    }

    public static boolean check(int limit, String[] names) {
        return limit > 0 && names != null;
    }

    public static String[] convert(int[] ids, Item item) {
        return new String[] {String.valueOf(ids.length), item.getClass().getSimpleName()};
    }

    public static void main(String[] args) {
        Class<?> nested = Item.class;
        Class<?> nestedArray = Item[][].class;

        System.out.println("outer=" + NameDemo.class.getName());
        System.out.println("nestedBinary=" + nested.getName());
        System.out.println("nestedCanonical=" + nested.getCanonicalName());

        System.out.println("arrayGetName=" + nestedArray.getName());
        System.out.println("arrayCanonical=" + nestedArray.getCanonicalName());
        System.out.println("intArrayGetName=" + int[][].class.getName());
        System.out.println("stringArrayGetName=" + String[].class.getName());

        MethodType method = MethodType.methodType(
                boolean.class, int.class, String[].class);
        System.out.println("methodDescriptor=" + method.toMethodDescriptorString());
    }
}
~~~

Từ thư mục **chứa folder demo**, sử dụng JDK 17+:

~~~bash
mkdir out
javac -encoding UTF-8 -d out demo/names/NameDemo.java
java -cp out demo.names.NameDemo
~~~

Nếu **out** đã tồn tại, bỏ qua lệnh **mkdir out**. Kết quả **stdout của chương trình**:

~~~text
outer=demo.names.NameDemo
nestedBinary=demo.names.NameDemo$Item
nestedCanonical=demo.names.NameDemo.Item
arrayGetName=[[Ldemo.names.NameDemo$Item;
arrayCanonical=demo.names.NameDemo.Item[][]
intArrayGetName=[[I
stringArrayGetName=[Ljava.lang.String;
methodDescriptor=(I[Ljava/lang/String;)Z
~~~

Giải thích kết quả:

1. **getName()** của lớp thường/member class trả **binary name** với dấu chấm package và dấu **$** giữa nested member class.
2. **getCanonicalName()** của member class dùng dấu **.** giữa lớp ngoài/lớp trong.
3. **Item[][].class.getName()** có tiền tố **[[** và phần reference component dùng tên binary với **dấu chấm**: **[[Ldemo.names.NameDemo$Item;**.
4. **Item[][].class.getCanonicalName()** hiển thị dạng source **demo.names.NameDemo.Item[][]**.
5. Mảng primitive **int[][]** dùng **[[I**.
6. **MethodType.toMethodDescriptorString()** cung cấp descriptor đúng theo cú pháp class file: **(I[Ljava/lang/String;)Z**. Phương thức **check** có đúng tham số và kiểu trả về như vậy.

Các field khai báo để quan sát trong bytecode; không cần gán giá trị runtime để **javap** hiển thị descriptor.

### 10.1. Xem field và method descriptors bằng javap

Chạy:

~~~bash
javap -classpath out -p -s demo.names.NameDemo
javap -classpath out -p -s 'demo.names.NameDemo$Item'
~~~

Trong một số shell (như PowerShell), dấu nháy đơn bao quanh tên lớp chứa **$** giúp ngăn cơ chế nội suy biến. Cú pháp quote cần điều chỉnh theo shell đang dùng; với Bash cũng có thể dùng dấu nháy đơn như trên.

Ở kết quả của class **NameDemo**, hãy tìm những cặp tên/descriptor:

~~~text
private int count;
  descriptor: I

private java.lang.String[] titles;
  descriptor: [Ljava/lang/String;

private int[][] grid;
  descriptor: [[I

private demo.names.NameDemo$Item item;
  descriptor: Ldemo/names/NameDemo$Item;

public static boolean check(int, java.lang.String[]);
  descriptor: (I[Ljava/lang/String;)Z

public static java.lang.String[] convert(int[], demo.names.NameDemo$Item);
  descriptor: ([ILdemo/names/NameDemo$Item;)[Ljava/lang/String;
~~~

Đây là **những dòng trọng tâm cần đối chiếu**, không cam kết mọi phần của output **javap** giống hệt giữa các phiên bản/cách biên dịch. Kiểu được trình bày trong dòng khai báo theo cách dễ đọc; chuỗi sau **descriptor:** tuân theo cú pháp JVM.

### 10.2. Xem constant pool và symbolic references

Chạy:

~~~bash
javap -classpath out -v -p demo.names.NameDemo
~~~

Tìm các chuỗi tương tự:

~~~text
demo/names/NameDemo
demo/names/NameDemo$Item
java/lang/String
(I[Ljava/lang/String;)Z
([ILdemo/names/NameDemo$Item;)[Ljava/lang/String;
~~~

Với **-v**, output có thông tin class file, constant pool, các attributes và code; số thứ tự **#n**, vị trí constant pool và các phần phụ khác có thể thay đổi. Quan sát mối liên hệ giữa **Class**, **Utf8**, **NameAndType** và các method/field references thay vì học thuộc các chỉ số.

### 10.3. Đọc bytecode mà không nhầm tên

Một dòng disassembly có thể hiển thị:

~~~text
Method java/lang/String.valueOf:(I)Ljava/lang/String;
~~~

Dòng này chứa **owner internal name** (java/lang/String), **method name** (valueOf) và **method descriptor** ((I)Ljava/lang/String;). Khi được hiển thị cùng nhau, chúng vẫn là ba thành phần có ngữ nghĩa độc lập.

## 11. Reflection API và cách nạp lớp theo tên

Java Reflection cung cấp nhiều phép nhìn tên, nhưng **các phép này không đồng nhất về định dạng chuỗi**.

| API | Dùng cho | Ví dụ |
| --- | --- | --- |
| **Class.getName()** | Binary name của class thường; encoding đặc biệt cho array class | **String.class.getName()** → **java.lang.String** |
| **Class.getCanonicalName()** | Canonical name khi có | **String[].class.getCanonicalName()** → **java.lang.String[]** |
| **Class.getSimpleName()** | Tên ngắn, thân thiện hơn | **NameDemo.Item.class.getSimpleName()** → **Item** |
| **Class.getTypeName()** | Tên kiểu để trình bày, array có dấu **[]** | **String[][].class.getTypeName()** → **java.lang.String[][]** |
| **Class.forName(String)** | Tìm lớp bằng tên binary (hoặc array-name encoding hỗ trợ) | **Class.forName("java.lang.String")** |
| **MethodType.toMethodDescriptorString()** | Descriptor của loại phương thức | **MethodType.methodType(void.class, int.class)** → **(I)V** |

Với member class, **Class.forName** dùng binary name chứa **$**:

~~~java
// Đoạn minh họa đặt trong một phương thức có throws ClassNotFoundException:
Class<?> c = Class.forName("demo.names.NameDemo$Item");
~~~

Chuỗi **demo.names.NameDemo.Item** là canonical/source name và **không** thay thế binary name của member class cho **Class.forName**.

### 11.1. Class.forName và tên mảng

Với array class, **Class.forName** nhận chuỗi mã hóa tên array giống định dạng **Class.getName()**:

~~~java
// Đoạn minh họa, có thể ném ClassNotFoundException:
Class<?> strings = Class.forName("[Ljava.lang.String;");
Class<?> ints2d = Class.forName("[[I");
Class<?> nested2d = Class.forName("[[Ldemo.names.NameDemo$Item;");
~~~

Chú ý **[Ljava.lang.String;** dùng **dấu chấm** trong tên binary component. Đưa chuỗi descriptor class-file **[Ljava/lang/String;** trực tiếp vào **Class.forName** là sai định dạng tên trong API này.

Không dùng **Class.forName("int[]")** hoặc **Class.forName("[Ljava/lang/String;")** theo thói quen khi chưa xác định contract: lớp array có kiểu tên đặc biệt, còn **int[].class** là cách biểu đạt trực tiếp và an toàn trong mã nguồn.

### 11.2. Khi nào cần chuyển tên?

- Từ **binary name của lớp thường** sang internal name: thay **dấu chấm package** thành **/** và giữ **$** của member class.
- Từ internal name lớp tham chiếu sang field descriptor: ghép **L** + internal name + **;**.
- Từ descriptor của kiểu thành mảng: thêm **[** cho mỗi chiều.
- Từ descriptor trở lại loại Java: cần parser tuân theo grammar, không đủ dùng thao tác thay dấu chấm đơn giản.
- Với reflection, nên thao tác trên **Class<?>**, **Method**, **MethodType** và API chuẩn khi có thể; chỉ tạo chuỗi descriptor thủ công khi thực sự cần tích hợp định dạng JVM/bytecode tooling.

## 12. Generic signature không giống descriptor

Giả sử có field:

~~~java
// Mã minh họa một field của lớp:
java.util.List<String> names;
~~~

**Field descriptor** vẫn là:

~~~text
Ljava/util/List;
~~~

Thông tin **String** là type argument không xuất hiện trong descriptor cơ bản, vì Java generics chủ yếu dựa vào **type erasure**. Nếu class file giữ metadata generic phù hợp, **Signature** attribute sẽ mang biểu diễn generic (chẳng hạn **Ljava/util/List<Ljava/lang/String;>;**).

Tương tự, hai phương thức **List<String>** và **List<Integer>** ở tham số không trở thành hai overload Java hợp lệ chỉ nhờ khác generic type arguments, vì erasure dẫn đến cùng parameter type/signature ở tầng kiểm tra tương ứng.

Phân biệt các thuật ngữ:

| Thuật ngữ | Nó chứa gì? |
| --- | --- |
| **Descriptor** | Kiểu field hoặc kiểu tham số/trả về được JVM sử dụng cho linking/execution |
| **Signature attribute** | Thông tin generic parameterized types, type variables, bounds trong những khai báo có dữ liệu generic |
| **Method name** | Tên riêng, ví dụ **find** |
| **Binary/internal name** | Tên lớp/interface, không phải toàn bộ kiểu phương thức |
| **Source declaration** | Cú pháp Java gồm modifiers, generic arguments, tên tham số... mà descriptor không lưu đầy đủ |

Đừng lấy chuỗi generic signature đưa vào nơi yêu cầu field descriptor; dấu **<...>** thuộc grammar khác và có thể khiến thư viện bytecode từ chối đầu vào.

## 13. Cách tự đọc và dựng descriptor không cần công cụ

Hãy làm theo thứ tự:

**Với field:**

1. Xác định kiểu Java sau khi bỏ qua tên biến, modifier và generic arguments đối với descriptor thông thường.
2. Nếu là primitive, chuyển sang một ký tự **B/C/D/F/I/J/S/Z**.
3. Nếu là class/interface, tìm **binary name** chính xác (đặc biệt với nested class), chuyển sang internal name rồi bọc **L...;**.
4. Nếu là array, đếm số **[]** và thêm số lượng dấu **[** tương ứng trước descriptor của component type.

Ví dụ:

~~~text
java.util.Map.Entry<String, Integer>[] entries
    -> raw member class: java.util.Map$Entry
    -> internal name:    java/util/Map$Entry
    -> field descriptor: [Ljava/util/Map$Entry;
~~~

**Với method:**

1. Đọc lần lượt từng tham số và tạo field descriptor cho chúng.
2. Nối tất cả **không có dấu phẩy** bên trong **(...)**.
3. Sau **)**, thêm field descriptor của return type hoặc **V** nếu **void**.
4. Không thêm tham số **this** chỉ vì method là instance.
5. Không đưa tên method, tên tham số, **throws** hay generic type arguments vào descriptor cơ bản.

Ví dụ:

~~~java
// Mã minh họa khai báo:
static long evaluate(byte flag, String[][] names, int[] offsets)
~~~

~~~text
byte         -> B
String[][]   -> [[Ljava/lang/String;
int[]        -> [I
long         -> J

Method descriptor:
(B[[Ljava/lang/String;[I)J
~~~

**Chuỗi parser đọc từ trái sang phải:** Sau dấu **(** là **B** (tham số 1), **[[Ljava/lang/String;** (tham số 2), **[I** (tham số 3); dấu **)** đóng danh sách; **J** là return type.

## 14. Các lỗi phổ biến

### 14.1. Dùng dấu chấm trong object descriptor

**Sai:** **Ljava.lang.String;**.

**Đúng:** **Ljava/lang/String;**.

Object descriptor trong class file dùng internal name, không dùng binary name có dấu chấm.

### 14.2. Dùng tên canonical của nested class khi API cần binary name

**Sai ở ngữ cảnh binary name:** **com.example.Outer.Inner**.

**Đúng:** **com.example.Outer$Inner** khi **Inner** là member class của **Outer**.

Nhưng nhớ rằng **$** cũng là ký tự Java identifier có thể xuất hiện ở nơi khác; không nên suy diễn cấu trúc nested class chỉ bằng thao tác string thiếu metadata.

### 14.3. Thiếu chữ L hoặc dấu ; ở object descriptor

**Sai:** **java/lang/String**, khi vị trí được yêu cầu là *field descriptor*.

**Đúng:** **Ljava/lang/String;**.

Bản thân **java/lang/String** có thể hoàn toàn đúng khi API đang yêu cầu *internal class name*; chỉ sai vì **không khớp loại dữ liệu đầu vào**.

### 14.4. Viết descriptor method với dấu phẩy hoặc kiểu đọc được

**Sai:** **(int,String[])boolean** hoặc **(I,[Ljava/lang/String;)Z**.

**Đúng:** **(I[Ljava/lang/String;)Z**.

Method descriptor không chứa dấu phẩy phân cách tham số.

### 14.5. Nhầm long và object reference

**Sai:** dùng **L** cho kiểu **long**.

**Đúng:** **J** cho **long**; **L<internal-name>;** cho reference class/interface.

### 14.6. Dùng V cho field hoặc phần tử mảng

**Sai:** **V** làm field descriptor hoặc **[V** cho mảng void.

**Đúng:** **V** chỉ dùng làm return descriptor của method trả **void**, ví dụ **()V**.

### 14.7. Quên chiều của mảng

**int[]** là **[I**; **int[][]** là **[[I**. Thiếu một dấu **[** làm thay đổi kiểu hoàn toàn. Một method trả **String[][]** phải kết thúc bằng **[[Ljava/lang/String;**, không phải **[Ljava/lang/String;**.

### 14.8. Đọc getName() của mảng như descriptor trong class file

**String[].class.getName()** → **[Ljava.lang.String;**, còn field descriptor → **[Ljava/lang/String;**. Hai chuỗi không bằng nhau từng ký tự. Với primitive array, sự giống nhau về chuỗi không chứng minh quy tắc áp dụng tương tự cho reference array.

### 14.9. Nhầm descriptor với generic signature

**List<String>** có field descriptor **Ljava/util/List;**; biểu diễn **String** generic không nằm trong descriptor chính. Reflection generic metadata hay **Signature** không phải nơi lấy descriptor thay thế cho code generation.

### 14.10. Nhầm method descriptor với toàn bộ symbolic reference

**java/lang/String.length:()I** chứa owner, method name và descriptor. Phần method descriptor thực sự chỉ là **()I**.

## 15. Bài tập tự kiểm tra

**Câu 1.** Binary name, internal name và field descriptor của **java.util.Map.Entry** là gì?

**Đáp án:**

~~~text
Binary name:    java.util.Map$Entry
Internal name:  java/util/Map$Entry
Descriptor:     Ljava/util/Map$Entry;
~~~

**Câu 2.** Descriptor của **boolean[][][]** và **long[]**?

**Đáp án:** **[[[Z** và **[J**. **boolean** dùng **Z**, **long** dùng **J**.

**Câu 3.** Phân tích **([Ljava/lang/String;I)V**.

**Đáp án:** Method nhận hai tham số **String[]** rồi **int**, trả **void**. Dấu **;** kết thúc descriptor của String component; dấu **I** tiếp theo là tham số thứ hai.

**Câu 4.** **(ILjava/lang/Object;)[[D** nghĩa là gì?

**Đáp án:** Method nhận **int** và **Object**, trả **double[][]**. Đầu ra là mảng hai chiều kiểu double.

**Câu 5.** Field **java.util.List<String> list** có descriptor gì?

**Đáp án:** **Ljava/util/List;**. Thông tin generic String chỉ nằm ở metadata generic khi có, không phải field descriptor.

**Câu 6.** Tại sao **Class.forName("[Ljava.lang.String;")** đúng trong ngữ cảnh Reflection mà **[Ljava/lang/String;** là dạng class-file array descriptor?

**Đáp án:** Reflection **Class.forName** sử dụng array-name encoding với **binary name có dấu chấm** cho reference component, còn class file descriptor dùng **internal name với /**.

**Câu 7.** Một phương thức instance **String read(int n)** có descriptor **(Lmy/pkg/Reader;I)Ljava/lang/String;** để bao gồm **this** không?

**Đáp án:** Không. Descriptor của phương thức là **(I)Ljava/lang/String;**. **this** được truyền ngầm tại runtime, không nằm trong descriptor.

**Câu 8.** Bạn thấy trong **javap -v** một entry **CONSTANT_Class** tham chiếu chuỗi **[I**. Nó sai vì không chứa đường dẫn package hay không?

**Đáp án:** Không. **CONSTANT_Class** có thể mô tả array class bằng **array descriptor**; **[I** là array type int[].

**Câu 9.** Tại sao **(I)V** không thể được hiểu là field descriptor?

**Đáp án:** Ngoặc đơn và return **V** thuộc ngữ pháp **method descriptor**. Field descriptor chỉ mô tả một kiểu, không chứa danh sách tham số hay void return.

## 16. Checklist khi xử lý class file, Reflection và bytecode tooling

1. Xác định API đang đòi **binary name**, **internal name**, **field descriptor**, **method descriptor**, hay **generic signature**.
2. Với nested member class, lấy đúng **binary name** có **$** trước khi chuyển package dots thành **/**.
3. Với primitive, dùng mã một ký tự; với object dùng **L...;**; với array thêm đúng số dấu **[**.
4. Với method, ghép **(parameter descriptors) + return descriptor**, kể cả **V** nếu trả void; không đưa tên và **this** vào.
5. Với array reflection, phân biệt **Class.getName()** (reference component có dấu **.**) với class-file descriptor (dấu **/**).
6. Đừng coi generic Signature là descriptor thường, hoặc coi tên class trong constant pool luôn ở dạng không có **[**.
7. Đọc output **javap -s -p -v** và so sánh với source declaration, method descriptor, constant pool; khi có thể dùng **MethodType** và API typed để tạo chuỗi chính xác.

### Tài liệu chuẩn

- **JVMS Java SE 17, §4.2 — Names**, đặc biệt §4.2.1 Binary Class and Interface Names: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.2
- **JVMS Java SE 17, §4.3.2 — Field Descriptors**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3.2
- **JVMS Java SE 17, §4.3.3 — Method Descriptors**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3.3
- **JVMS Java SE 17, §4.4 — Constant Pool**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4
- **JLS Java SE 17, §13.1 — The Form of a Binary**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.1
- **Java 17 API, Class**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java 17 API, MethodType**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodType.html

**Ghi nhớ:** **Binary name** dùng dấu chấm cho package và dấu **$** cho member class; **internal name** dùng **/** thay package dots; **field descriptor** mã hóa một kiểu với **B/C/D/F/I/J/S/Z**, **L...;** hoặc **[...]**; **method descriptor** ghép **(tham số)kiểu trả về**. Với **array class**, cách **Class.getName()** biểu diễn tên tham chiếu dùng dấu chấm và không giống hệt descriptor trong class file. Đọc đúng các ranh giới này giúp bạn tránh phần lớn lỗi khi phân tích class file và làm việc với Reflection/bytecode.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-file-format-004">Constant pool: hằng số, tên kiểu và tham chiếu ký hiệu</a>

<details>
<summary>Xem chi tiết</summary>

# Constant pool trong tệp class: hằng số, tên kiểu và tham chiếu ký hiệu

## Mục tiêu

Khi biên dịch mã nguồn Java, `javac` tạo tệp `.class` chứa bytecode cùng nhiều bảng và thuộc tính mô tả lớp. Một thành phần trung tâm là **constant pool**: bảng có chỉ mục dùng để mô tả giá trị hằng, chuỗi ký tự, tên lớp, tên trường/phương thức, kiểu dữ liệu và các tham chiếu ký hiệu. Dù gọi là “pool hằng”, nó **không chỉ chứa những giá trị bất biến như số và chuỗi**.

Sau bài học, bạn có thể:

- Chỉ ra vị trí và cấu trúc `constant_pool` trong `ClassFile`, phân biệt **class-file constant pool** với **run-time constant pool**.
- Đọc các nhóm mục quan trọng: `Utf8`, `Integer`, `Long`, `Class`, `String`, `NameAndType`, `Fieldref`, `Methodref`, `InterfaceMethodref` và nhóm phục vụ liên kết động.
- Giải thích chuỗi tham chiếu `Methodref → Class + NameAndType → Utf8`, cách dùng *internal name* và *descriptor*.
- Hiểu bytecode `ldc`, `getstatic`, `invokestatic`, `invokevirtual` truy cập constant pool như thế nào, khi nào JVM cần **resolution**.
- Tự biên dịch Java 17+, quan sát bảng bằng `javap -v` và phân tích lỗi liên kết thường gặp mà không nhầm với lỗi cú pháp.

## 1. Constant pool dùng để làm gì?

Bytecode JVM chủ yếu làm việc với operand stack, local variables và các toán hạng có định dạng gọn. Nó **không mã hóa địa chỉ bộ nhớ cuối cùng của mọi lớp, trường và phương thức** trong từng lệnh. Thay vào đó, các lệnh có thể tham chiếu **một chỉ mục** trong constant pool của lớp hiện hành.

Ví dụ về ý tưởng:

~~~text
getstatic    #18   // tham chiếu ký hiệu đến System.out
ldc          #24   // nạp hằng String
invokevirtual #30  // tham chiếu ký hiệu đến PrintStream.println
~~~

Đây là **minh họa**; các số `#18`, `#24`, `#30` chỉ là ví dụ, **không phải chỉ số ổn định** của một class cụ thể. `javac` và các công cụ có thể sắp xếp mục constant pool khác nhau.

Constant pool giúp **chia sẻ thông tin trong cùng tệp lớp** và biểu diễn các phụ thuộc dưới dạng ký hiệu (tên, loại, descriptor). Khi JVM nạp/liên kết và thực thi lớp, nó có thể chuyển những ký hiệu đó thành thực thể runtime tương ứng.

Phân biệt ngay ba thuật ngữ:

| Thuật ngữ | Nằm ở đâu/vai trò | Điều dễ nhầm |
| --- | --- | --- |
| **Class-file constant pool** | Bảng `constant_pool` được mã hóa trực tiếp bên trong mỗi tệp `.class` | Không chỉ gồm literal; còn có tên kiểu và tham chiếu đến field/method. |
| **Run-time constant pool** | Cấu trúc runtime được tạo từ constant pool khi JVM tạo một lớp/interface; về mặt đặc tả là thành phần của **method area** | Có thể chứa trạng thái/thông tin đã resolve; không đồng nghĩa một bảng String duy nhất toàn JVM. |
| **String intern pool** | Cơ chế canonicalization cho các đối tượng `String`, được `String.intern()` và string literal sử dụng theo quy tắc Java | Không đồng nghĩa bảng `CONSTANT_String` của từng class file. |

Mỗi class file sở hữu constant pool của **chính nó**. Hai lớp đều nhắc đến `java/lang/String` có thể có **hai mục `CONSTANT_Class` riêng** trong hai class file. Không vì cùng tên mà mặc định chúng tham chiếu một vị trí vật lý chung trong tệp hoặc trong bộ nhớ JVM.

## 2. Vị trí bảng trong cấu trúc ClassFile

Theo Java Virtual Machine Specification (JVMS), một `ClassFile` có bố cục khái quát:

~~~text
ClassFile {
    u4 magic;                  // 0xCAFEBABE
    u2 minor_version;
    u2 major_version;
    u2 constant_pool_count;
    cp_info constant_pool[constant_pool_count - 1];
    u2 access_flags;
    u2 this_class;
    u2 super_class;
    u2 interfaces_count;
    ...
    fields[];
    methods[];
    attributes[];
}
~~~

Đây là **sơ đồ rút gọn**; cấu trúc đầy đủ còn có các bộ đếm và mảng con tương ứng với interfaces, fields, methods, attributes. Ký hiệu `u1`, `u2`, `u4` là số nguyên không dấu có độ rộng lần lượt 1, 2, 4 byte; thứ tự byte của định dạng class file là **big-endian**.

- `magic` là `0xCAFEBABE`, nhận diện class file.
- `major_version`/`minor_version` cho biết **phiên bản định dạng class file**, không phải bản thân phiên bản JDK đã cài. Java 17 thường tạo major **61**; Java 21 thường tạo major **65** với target mặc định tương ứng.
- `constant_pool_count` là một `u2`; chỉ số hợp lệ của các vị trí constant pool chạy từ **1** đến **`constant_pool_count - 1`**. **Chỉ số 0 không phải một entry**.
- Mỗi entry vật lý bắt đầu bằng một `tag` 1 byte, tiếp theo là trường dữ liệu thay đổi theo loại entry.
- Các vị trí sau một mục `CONSTANT_Long` hoặc `CONSTANT_Double` có quy tắc đặc biệt: **mỗi giá trị loại này chiếm hai vị trí chỉ số liên tiếp**, nhưng chỉ có một entry dữ liệu thực. Vị trí kế tiếp là không dùng được. Vì vậy không được suy luận số entry lưu thực bằng `constant_pool_count - 1` trong mọi trường hợp.

### Ví dụ vị trí chỉ mục có khoảng trống

~~~text
constant_pool_count = 8
#1 Utf8     "Sample"
#2 Class    name_index = 1
#3 Long     3000000000
#4 <không dùng: vị trí sau Long>
#5 Utf8     "java/lang/Object"
#6 Class    name_index = 5
#7 Utf8     "<init>"
~~~

Ví dụ minh họa cấu trúc chỉ số, **không phải toàn bộ một class file hợp lệ**. Với `Long` ở `#3`, bytecode không được tham chiếu entry `#4`. Cùng quy tắc áp dụng cho `Double`; những mục khác chỉ chiếm một chỉ số.

Một số field trong cấu trúc `ClassFile` cũng tham chiếu pool. Ví dụ `this_class` và `super_class` chứa chỉ mục đến entry `CONSTANT_Class`; tên của class được đi tiếp qua entry `CONSTANT_Utf8`.

## 3. Các loại entry trong constant pool

JVMS định nghĩa **17 loại tag** ở định dạng class file hiện đại. Không phải class file nào cũng chứa đủ chúng; loại xuất hiện tùy nguồn, compiler, mục đích của class, target và những tính năng bytecode đã sử dụng.

| Tag (thập phân) | Tên entry | Ý nghĩa chính |
| --- | --- | --- |
| 1 | `CONSTANT_Utf8` | Văn bản dưới dạng **modified UTF-8**: tên, descriptor, tên thuộc tính, text của literal... |
| 3 | `CONSTANT_Integer` | Hằng số số nguyên 32-bit (cũng có thể dùng cho giá trị `boolean`, `byte`, `short`, `char` theo encoding thích hợp). |
| 4 | `CONSTANT_Float` | Hằng số dấu phẩy động 32-bit. |
| 5 | `CONSTANT_Long` | Hằng số nguyên 64-bit; chiếm hai vị trí chỉ số. |
| 6 | `CONSTANT_Double` | Hằng số dấu phẩy động 64-bit; chiếm hai vị trí chỉ số. |
| 7 | `CONSTANT_Class` | Tham chiếu ký hiệu đến kiểu lớp/interface hoặc descriptor kiểu mảng theo quy định. |
| 8 | `CONSTANT_String` | Hằng tham chiếu đối tượng `String`; trỏ tới nội dung ở `CONSTANT_Utf8`. |
| 9 | `CONSTANT_Fieldref` | Tham chiếu ký hiệu đến trường của lớp/interface. |
| 10 | `CONSTANT_Methodref` | Tham chiếu ký hiệu đến phương thức (hoặc constructor) của class theo quy định. |
| 11 | `CONSTANT_InterfaceMethodref` | Tham chiếu ký hiệu đến phương thức của interface. |
| 12 | `CONSTANT_NameAndType` | Ghép tên member/call site với **descriptor** kiểu trường hoặc phương thức. |
| 15 | `CONSTANT_MethodHandle` | Mô tả method handle với reference kind và reference index. |
| 16 | `CONSTANT_MethodType` | Mô tả kiểu phương thức qua descriptor. |
| 17 | `CONSTANT_Dynamic` | Hằng động (*dynamic constant*) được tính và liên kết bằng bootstrap method. |
| 18 | `CONSTANT_InvokeDynamic` | Call site động dùng bởi lệnh `invokedynamic` và bootstrap method. |
| 19 | `CONSTANT_Module` | Mô tả tên module, thường dùng với `module-info.class`. |
| 20 | `CONSTANT_Package` | Mô tả tên package, thường dùng với `module-info.class`. |

**Tag không liên tục**: không có nghĩa mọi số từ 1 đến 20 đều hợp lệ; ví dụ 2, 13 và 14 **không được dùng** trong bảng loại ở trên.

Một nhóm hỗ trợ liên kết động (`MethodHandle`, `MethodType`, `Dynamic`, `InvokeDynamic`) sẽ được giải thích kỹ ở phần sau. Không nên hiểu chữ “constant” trong `CONSTANT_InvokeDynamic` là một literal có thể nạp bằng `ldc`: đó là mô tả *điểm gọi động*.

### `CONSTANT_Utf8` và modified UTF-8

`CONSTANT_Utf8_info` có `length` kiểu `u2` và một dãy byte được mã hóa theo **modified UTF-8** của JVM. Điều này **không hoàn toàn giống UTF-8 tiêu chuẩn**; cách biểu diễn null character và một số đơn vị UTF-16 khác nhau. Giới hạn của trường là **65.535 byte mã hóa**, không phải 65.535 ký tự Java hoặc ký tự Unicode. Khi tự đọc file nhị phân, phải dùng đúng quy tắc modified UTF-8, độ dài byte và chỉ mục; không tùy ý dùng decoder UTF-8 thông thường.

Một entry `Utf8` chỉ chứa văn bản; nó **không tự động là đối tượng `String` Java**. Nó có thể đại diện tên lớp, tên trường, descriptor, tên thuộc tính hoặc phần text mà `CONSTANT_String` tham chiếu.

## 4. Tên kiểu: `Class` và internal name

Ví dụ một lớp Java:

~~~java
package org.example;

public class Invoice {
}
~~~

Đây là mã nguồn minh họa riêng. Trong mã Java, tên đầy đủ của lớp là `org.example.Invoice`. Trong **internal name** của JVM, dấu chấm phân cách package được biểu diễn bằng dấu gạch chéo:

~~~text
Tên Java:        org.example.Invoice
JVM internal:    org/example/Invoice

Một dạng mục constant pool:
#10 = Class             #11
#11 = Utf8              org/example/Invoice
~~~

Chỉ số `#10`/`#11` **minh họa**. `CONSTANT_Class` chứa `name_index` trỏ đến `CONSTANT_Utf8` lưu tên; nó **không chứa toàn bộ đối tượng `java.lang.Class` ở dạng serialized**.

Với kiểu mảng, tên trong `CONSTANT_Class` có thể dùng **array descriptor**, ví dụ `[Ljava/lang/String;` (mảng `String[]`), theo yêu cầu cụ thể của JVMS. Một class file cũng có thể chứa tên kiểu phụ thuộc như `java/lang/Object`, `java/lang/System`, `java/io/PrintStream` nếu cần.

### Class identity còn phụ thuộc class loader

**Tên giống nhau chưa đủ để bảo đảm cùng một lớp runtime.** Khi JVM nạp lớp, danh tính của class/interface gắn với **binary name và defining class loader**. Hai class loader khác nhau có thể định nghĩa hai lớp có cùng tên binary nhưng là hai kiểu khác nhau. Vì vậy quá trình resolve một `CONSTANT_Class` còn phụ thuộc bối cảnh lớp đang tham chiếu và quy tắc nạp/liên kết, không chỉ nội dung text tên lớp.

## 5. Tên trường/phương thức và descriptor: `NameAndType`

Để liên kết tới member, JVM cần nhiều hơn chỉ tên. Một tên như `print` hoặc `value` không nói đủ thông tin về kiểu tham số, kiểu trả về hay kiểu trường.

**Field descriptor** ký hiệu kiểu của một trường; **method descriptor** mã hóa danh sách kiểu tham số và kiểu trả về. Một số ký hiệu cơ bản:

| Descriptor | Kiểu Java tương ứng |
| --- | --- |
| `B` | `byte` |
| `C` | `char` |
| `D` | `double` |
| `F` | `float` |
| `I` | `int` |
| `J` | `long` |
| `S` | `short` |
| `Z` | `boolean` |
| `V` | `void` (chỉ hợp lệ ở vị trí kiểu trả về phương thức, không phải kiểu trường/tham số) |
| `Ljava/lang/String;` | Tham chiếu `java.lang.String` |
| `[I` | `int[]` |
| `[[Ljava/lang/String;` | `String[][]` |

Ví dụ:

~~~java
int count;
long sum(int a, long b) { return a + b; }
String join(String s, int n) { return s + n; }
~~~

Đoạn code chỉ minh họa các member bên trong một class, không phải chương trình độc lập. Các descriptor tương ứng:

~~~text
count : I
sum   : (IJ)J
join  : (Ljava/lang/String;I)Ljava/lang/String;
main  : ([Ljava/lang/String;)V
~~~

Phân tích `(IJ)J`:
- Dấu ngoặc `(...)` bao các kiểu tham số, theo thứ tự `int` (`I`) và `long` (`J`).
- Phần sau dấu `)` là kiểu trả về `long` (`J`).
- Descriptor **không chứa tên tham số Java**, chú thích, generic source-level hoặc keyword `public`/`static`.

Generics được **erase** ở mức descriptor JVM thông thường: ví dụ `List<String>` thường được biểu diễn bằng kiểu thô `Ljava/util/List;` trong field/method descriptor. Thông tin generic khai báo có thể xuất hiện ở thuộc tính `Signature` riêng, không phải một kiểu descriptor mới kiểu `List<String>`.

### `NameAndType` tham chiếu hai `Utf8`

Một mục `CONSTANT_NameAndType` có hai chỉ mục:
- `name_index` → `CONSTANT_Utf8` chứa tên, ví dụ `println`.
- `descriptor_index` → `CONSTANT_Utf8` chứa descriptor, ví dụ `(Ljava/lang/String;)V`.

Từ đó, `CONSTANT_Methodref`/`Fieldref` kết hợp **Class** với **NameAndType** để xác định ký hiệu đầy đủ.

## 6. Cấu trúc tham chiếu ký hiệu `Fieldref`, `Methodref`, `InterfaceMethodref`

Giả sử có lời gọi Java:

~~~java
System.out.println("Hello");
~~~

Đây là một lệnh minh họa bên trong một phương thức Java. Nó cần truy cập trường tĩnh `java.lang.System.out`, sau đó gọi phương thức `java.io.PrintStream.println(String)`.

Một đồ thị minh họa:

~~~text
Fieldref: java/lang/System.out:Ljava/io/PrintStream;
  |
  +--> Class #A ---------> Utf8 "java/lang/System"
  |
  +--> NameAndType #B ---> Utf8 "out"
                         +-> Utf8 "Ljava/io/PrintStream;"

Methodref: java/io/PrintStream.println:(Ljava/lang/String;)V
  |
  +--> Class #C ---------> Utf8 "java/io/PrintStream"
  |
  +--> NameAndType #D ---> Utf8 "println"
                         +-> Utf8 "(Ljava/lang/String;)V"
~~~

Các nhãn `#A`, `#B`, `#C`, `#D` là placeholder giải thích **quan hệ**, không phải chỉ số thực của class file.

Tại dạng nhị phân:
- `CONSTANT_Fieldref_info`, `CONSTANT_Methodref_info` và `CONSTANT_InterfaceMethodref_info` đều chứa `class_index` và `name_and_type_index`.
- `class_index` trỏ tới `CONSTANT_Class`; `name_and_type_index` trỏ tới `CONSTANT_NameAndType`.
- Điểm khác nhau của ba loại phản ánh **quy tắc resolve/loại member** cho trường, phương thức lớp hoặc phương thức interface.
- **Phương thức overload** có thể có cùng tên nhưng descriptor khác nhau, nên tên `println` không đủ để chỉ chính xác overload.
- Constructor dùng tên JVM đặc biệt `<init>` với descriptor trả về `V`; class initializer `<clinit>` có vai trò riêng trong cấu trúc method, không phải một constructor thông thường.

**Symbolic reference** là bộ thông tin “lớp nào, tên gì, descriptor gì”, **không phải địa chỉ thực tế** của phương thức/trường trong process. Tên và descriptor được lấy từ constant pool để JVM resolve trong bối cảnh nạp/liên kết thích hợp.

## 7. Ví dụ đầy đủ: biên dịch, xem constant pool bằng `javap`

Tạo đúng tệp **`ConstantPoolDemo.java`**:

~~~java
public final class ConstantPoolDemo {
    private static final String CATEGORY = "JVM";
    private static final long BASE = 3_000_000_000L;

    public static long add(long delta) {
        return BASE + delta;
    }

    public static void main(String[] args) {
        String source = "class-file";
        long result = add(7L);

        System.out.println(CATEGORY);
        System.out.println(source);
        System.out.println(result);
    }
}
~~~

Biên dịch và chạy với **JDK 17+**:

~~~bash
javac --release 17 ConstantPoolDemo.java
java ConstantPoolDemo
~~~

**Stdout chính xác:**

~~~text
JVM
class-file
3000000007
~~~

Vì sao kết quả như vậy? `BASE` là hằng `long` có giá trị 3.000.000.000; phương thức `add(7L)` trả 3.000.000.007. Hai chuỗi `"JVM"` và `"class-file"` được in trước kết quả số.

Quan sát file đã biên dịch:

~~~bash
javap -v -p ConstantPoolDemo
javap -c -p ConstantPoolDemo
javap -s -p ConstantPoolDemo
~~~

Ý nghĩa:
- `javap -v`: hiển thị metadata class file, phiên bản và bảng **Constant pool**, cùng chi tiết mã/attributes.
- `-p`: hiển thị cả member private.
- `-c`: disassemble bytecode của phương thức.
- `-s`: in **descriptor** JVM của field/method.

Bạn nên nhận diện **các dạng** mục, thay vì học thuộc số index cụ thể. Với compiler Java thông thường, kết quả quan sát có thể bao gồm:

| Dạng mục/bytecode | Nguồn gốc trong ví dụ | Điều cần quan sát |
| --- | --- | --- |
| `CONSTANT_Class`, `CONSTANT_Utf8` | `ConstantPoolDemo`, `java/lang/Object`, `java/lang/System`, `java/io/PrintStream` | Lớp được tham chiếu qua tên internal. |
| `CONSTANT_String` → `CONSTANT_Utf8` | `"JVM"` và `"class-file"` | `String` tham chiếu đến entry text; lệnh `ldc` có thể nạp string literal. |
| `CONSTANT_Long` | `3_000_000_000L` và `7L` | Hằng 64-bit được nạp bằng `ldc2_w` nếu compiler phát lệnh nạp trực tiếp. |
| `CONSTANT_Fieldref` | `java/lang/System.out` | Bytecode `getstatic` tham chiếu tới trường tĩnh. |
| `CONSTANT_Methodref` | `ConstantPoolDemo.add`, `PrintStream.println` | `invokestatic`, `invokevirtual` chỉ mục đến các method reference. |
| `CONSTANT_NameAndType` | `out:Ljava/io/PrintStream;`, `add:(J)J`, `println:(J)V` | Phân biệt tên member với descriptor. |
| `ConstantValue` attribute | `CATEGORY` và `BASE` là compile-time constants | Giá trị hằng của field được lưu thông qua attribute riêng tham chiếu vào pool; các cách tối ưu có thể inline ở call site. |

**Không sao chép nguyên số `#N` từ một máy làm chuẩn học liệu.** Compiler/JDK/target hoặc phương án sinh bytecode khác nhau có thể tạo chỉ số, thứ tự, lượng entry và instruction khác nhau mà chương trình vẫn đúng.

### Đọc đường đi từ bytecode đến constant pool

Trong `main` của chương trình trên, các bytecode dạng:

~~~text
ldc          #N   // chuỗi "class-file" hoặc "JVM"
ldc2_w       #N   // giá trị long lớn
invokestatic #N   // ConstantPoolDemo.add:(J)J
getstatic    #N   // java/lang/System.out
invokevirtual #N  // PrintStream.println overload tương ứng
~~~

là **đường đi có thể quan sát tùy nơi compiler chọn instruction**, không phải dump exact của `javap`. Để xác định thực tế, tìm số `#N` trên lệnh disassembly rồi tìm cùng số trong bảng `Constant pool`. Trong bản in của `javap`, chú thích bên phải thường đã resolve các chỉ mục ở mức hiển thị thành dạng đọc được như `Method ConstantPoolDemo.add:(J)J`.

Đặc biệt, `CATEGORY` và `BASE` là **compile-time constant variables** (`static final` với initializer hằng phù hợp). Compiler có thể **inline** giá trị sử dụng, khiến `main` hoặc `add` không dùng lệnh `getstatic` cho chính các field này; sự tồn tại của field trong class file không có nghĩa mọi chỗ đọc nó đều phải có `CONSTANT_Fieldref` tương ứng.

## 8. Giá trị nào thật sự cần entry và bytecode nào tham chiếu pool?

Không phải mọi literal nguồn Java đều sinh một entry constant pool để nạp bằng `ldc`.

Ví dụ:

~~~java
int zero = 0;
int small = 5;
int medium = 100;
long large = 3_000_000_000L;
String name = "JVM";
~~~

Đoạn code minh họa bên trong một phương thức. Compiler thường có thể phát:
- `iconst_0`, `iconst_5` cho các số nguyên nhỏ.
- `bipush` hoặc `sipush` để mang một giá trị nguyên ngắn **ngay trong instruction**.
- `ldc` hoặc `ldc_w` cho hằng tải từ pool (số nguyên/float, String, class literal, method type/handle hoặc dynamic constant phù hợp).
- `ldc2_w` cho `long`/`double` (hoặc dynamic constant có kiểu 64-bit phù hợp).
- Một số giá trị `long`/`double` nhỏ như 0 hoặc 1 có **instruction riêng** (`lconst_0`, `dconst_1`) nên không cần mục pool để nạp literal đó.

Đừng kết luận “không thấy số 5 trong constant pool là compiler mất dữ liệu”; có thể giá trị đã nằm trong opcode. Ngược lại, `CONSTANT_Integer` không nhất thiết chỉ phục vụ biến `int` trong mã Java: đặc tả class file dùng loại hằng này cho nhiều kiểu nguyên nhỏ hơn khi phù hợp.

**`ldc` vs `ldc_w`:** hai lệnh nạp hằng với operand chỉ mục có độ rộng khác nhau (1 byte và 2 byte). `ldc2_w` dùng hai byte chỉ mục để nạp một giá trị category 2 như `long`/`double`. Chữ `w` ở đây liên quan **độ rộng chỉ mục**, không có nghĩa một chuỗi hay số tự chuyển sang “precision lớn hơn”.

## 9. Constant pool trong file và quá trình resolve trong JVM

Khi nạp lớp, JVM tạo cấu trúc run-time constant pool tương ứng. Các **symbolic references** cần được liên kết với lớp, trường, phương thức hoặc thực thể call site thực tế. Resolution là một phần của **linking**; các bước lớn trong vòng đời lớp là:

~~~text
.class trên disk
    |
    v
Loading: tạo cấu trúc runtime của class/interface
    |
    v
Linking:
    +-- Verification: kiểm tra tính hợp lệ/các ràng buộc
    +-- Preparation: chuẩn bị biến static với giá trị mặc định
    +-- Resolution: ánh xạ tham chiếu ký hiệu sang thực thể cụ thể
    |
    v
Initialization: chạy class initialization khi có tác nhân kích hoạt
    |
    v
Execution: bytecode dùng constant pool và kết quả liên kết
~~~

Sơ đồ mang tính khái niệm: JVM có thể **resolve theo nhu cầu** hoặc **resolve sớm**, miễn tuân thủ thời điểm báo lỗi/hành vi theo đặc tả. Vì vậy **không được khẳng định mọi `Methodref` và `Fieldref` đều resolve hết ngay khi class được load**, cũng không được khẳng định resolution chỉ xảy ra sau khi bắt đầu thực thi một instruction cụ thể.

### Ví dụ resolution method reference

Để chạy lời gọi đến một phương thức như `PrintStream.println(String)`, VM phải:
1. Đọc một `CONSTANT_Methodref` từ run-time constant pool của lớp đang thực thi.
2. Tìm tên kiểu chủ sở hữu bằng `Class` và tên/descriptor bằng `NameAndType`.
3. Theo quy tắc class loading, access control và method resolution, xác định kiểu và member phù hợp hoặc báo lỗi liên kết.
4. Khi instruction được thực thi, áp dụng semantics của loại invoke, trong đó `invokevirtual` còn có cơ chế **chọn phương thức thực thi** dựa vào kiểu receiver tại runtime.

**Resolution và virtual dispatch không phải một khái niệm.** Resolution xác lập ý nghĩa tham chiếu ký hiệu; runtime method selection (ví dụ virtual method dispatch) quyết định implementation nào sẽ chạy trên receiver cụ thể.

Tương tự, `CONSTANT_Fieldref` không chứa “offset cố định” đáng tin cậy của field trong mọi đối tượng. JVM triển khai có thể lưu cache/quickening khác nhau, nhưng đó là chi tiết thực thi, không phải định dạng gốc của file.

### Những lỗi linking liên quan

| Lỗi | Tình huống thường gặp |
| --- | --- |
| `NoClassDefFoundError` | Lớp cần được định nghĩa/khởi tạo không sẵn có tại runtime hoặc không thể nạp/khởi tạo theo yêu cầu; phải xem nguyên nhân gốc. |
| `NoSuchFieldError` | Bytecode tham chiếu field với tên/descriptor nhưng bản class runtime không còn field phù hợp. |
| `NoSuchMethodError` | Bytecode tham chiếu method với tên/descriptor nhưng runtime không tìm thấy method phù hợp, thường do dependency không tương thích nhị phân. |
| `IllegalAccessError` | Link tới class/member không được phép truy cập theo các quy tắc runtime, dù mã nguồn có thể từng biên dịch với cấu hình khác. |
| `IncompatibleClassChangeError` | Dạng class/member thay đổi không tương thích, ví dụ khác static/instance hoặc class/interface. |
| `ClassFormatError` | Dữ liệu class file không thỏa định dạng/constraints khi JVM cố nạp. |
| `VerifyError` | Vi phạm kiểm tra tính hợp lệ của bytecode trong quá trình xác minh. |
| `BootstrapMethodError` | Bootstrap của hằng/call site động bị lỗi theo điều kiện đặc tả. |

Các lỗi trên không đơn giản là “chuỗi sai trong constant pool”. Nên xác minh **JAR thực tế trên classpath/module path, phiên bản bytecode, phiên bản dependency và class loader**. Một chương trình biên dịch thành công có thể vẫn liên kết thất bại nếu runtime cung cấp API khác bộ đã dùng lúc compile.

## 10. Liên kết động: MethodHandle, MethodType, InvokeDynamic và Dynamic

Các ngôn ngữ và tính năng JVM hiện đại cần biểu diễn những liên kết linh hoạt hơn tham chiếu `Methodref` thông thường:

- **`CONSTANT_MethodHandle` (tag 15):** mô tả một method handle, gồm `reference_kind` và `reference_index` trỏ đến loại reference thích hợp. Đây là mô tả liên kết theo semantics method handle, không nhất thiết là lời gọi trực tiếp.
- **`CONSTANT_MethodType` (tag 16):** trỏ đến một `Utf8` chứa **method descriptor**, ví dụ `(I)Ljava/lang/String;`.
- **`CONSTANT_InvokeDynamic` (tag 18):** mô tả **call site động**: bootstrap method index và `NameAndType`. Bytecode `invokedynamic` dùng entry này; JVM thực hiện bootstrap/link để tạo `CallSite` tương ứng.
- **`CONSTANT_Dynamic` (tag 17):** mô tả một **giá trị hằng được tính tại thời điểm resolution**, cũng dùng bootstrap và `NameAndType`, nhưng name-and-type descriptor ở đây là **field descriptor** của giá trị, khác với method descriptor của `InvokeDynamic`.

`CONSTANT_InvokeDynamic` có từ Java 7/JVM class file major 51; `CONSTANT_Dynamic` có từ Java 11/class file major 55. **Chúng không giống nhau**: một bên là *call site* để gọi, bên kia là *giá trị hằng* có thể tải.

### Vai trò của `BootstrapMethods` attribute

Nếu class file có `CONSTANT_Dynamic` hoặc `CONSTANT_InvokeDynamic`, nó phải có đúng **một** thuộc tính `BootstrapMethods` ở cấp class. Thuộc tính này chứa bảng các mục bootstrap:

~~~text
BootstrapMethods:
  bootstrap_method_ref  -> CONSTANT_MethodHandle
  bootstrap_arguments[] -> các entry hằng có loại được phép
~~~

Trường `bootstrap_method_attr_index` trong `CONSTANT_InvokeDynamic`/`CONSTANT_Dynamic` trỏ đến **chỉ số bắt đầu từ 0 trong bảng bootstrap_methods** của attribute, **không phải** một chỉ mục constant pool. Trong khi đó `bootstrap_method_ref` bên trong attribute là chỉ mục đến `CONSTANT_MethodHandle` của constant pool. Hai loại chỉ số cần phân biệt rõ khi đọc file.

**Bootstrap method** thực hiện liên kết thực sự theo contract của loại dynamic entry; một constant pool entry là thông tin mô tả, không phải toàn bộ logic Java trực tiếp được nhúng trong entry.

### Ví dụ Java 17 với lambda và string concatenation

Tạo tệp **`DynamicPoolDemo.java`**:

~~~java
import java.util.function.IntUnaryOperator;

public class DynamicPoolDemo {
    public static void main(String[] args) {
        IntUnaryOperator increment = value -> value + 1;
        int result = increment.applyAsInt(4);
        System.out.println("result=" + result);
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 DynamicPoolDemo.java
java DynamicPoolDemo
javap -v -p DynamicPoolDemo
~~~

**Stdout của ứng dụng:**

~~~text
result=5
~~~

Khi dùng **`javac` thông thường cho Java 17**, lambda thường được phát qua `invokedynamic` với bootstrap liên quan đến `LambdaMetafactory`; string concatenation thường dùng `invokedynamic` với bootstrap liên quan đến `StringConcatFactory`. Trong output `javap -v`, tìm `InvokeDynamic`, `MethodHandle`, `MethodType` và attribute `BootstrapMethods` để thấy các tham chiếu liên quan.

**Không cần Java source chứa từ khóa `invokedynamic`.** Đây là instruction của JVM; compiler chọn cách dùng. Không phải bytecode `invokedynamic` nào cũng là lambda; các mục trong constant pool phản ánh thực tế **của file được biên dịch**, không đủ để kết luận duy nhất một cú pháp Java nguồn.

## 11. `CONSTANT_Module` và `CONSTANT_Package`

Kể từ Java 9 (class file major 53), constant pool còn có hai loại để hỗ trợ hệ thống module:

- `CONSTANT_Module`: trỏ tới `Utf8` chứa tên module.
- `CONSTANT_Package`: trỏ tới `Utf8` chứa tên package ở dạng nội bộ phù hợp quy định.

Các mục này thường xuất hiện trong **`module-info.class`**, cùng attribute `Module` và các cấu trúc liên quan. Ví dụ mô tả module:

~~~java
module org.example.payments {
    exports org.example.payments.api;
    requires java.logging;
}
~~~

Đây là nội dung minh họa của **`module-info.java`**; muốn biên dịch cần cấu trúc module/source thích hợp và package export phải tồn tại. Khi xem `javap -v module-info.class`, ta có thể thấy `Module`/`Package` entries. Không nên yêu cầu mọi class Java không mô đun đều có những tag này.

## 12. Constant pool và thuộc tính khác trong class file

Có nhiều tên/metadata **được tham chiếu qua constant pool nhưng dữ liệu không nằm hoàn toàn trong constant pool**:

- **`ConstantValue`:** attribute của một field, có `constantvalue_index` trỏ tới giá trị trong pool. Nó mô tả giá trị hằng ban đầu của một field phù hợp; constant variables còn có thể bị compiler inline khi được sử dụng nơi khác.
- **`Code`:** attribute của method chứa bytecode, max stack/locals, exception table và attributes lồng. Lệnh trong `Code` dùng một số chỉ mục constant pool làm operand.
- **`Signature`:** có thể lưu thông tin generic source-level mà descriptor đã erase; giá trị text nằm ở `Utf8` được trỏ tới, không thay đổi ngữ nghĩa field/method descriptor chuẩn.
- **`SourceFile`, `LineNumberTable`, `LocalVariableTable`:** phục vụ nguồn/debug nếu có; các tên attribute và nhiều chuỗi nằm trong `Utf8`, nhưng thông tin chi tiết ở attribute riêng.
- **`StackMapTable`:** hỗ trợ type checking của verifier qua các frame; có thể tham chiếu kiểu theo constant pool nhưng không “biến thành” một entry hằng đơn lẻ.
- **Annotation attributes** và những attribute khác có thể dùng các chỉ mục đến `Utf8`, class/const entry theo format cụ thể.

Như vậy, **constant pool là bảng tham chiếu có cấu trúc cho toàn class file**, không phải nơi lưu bytecode, thông tin debug, object graph hay toàn bộ metadata trong một khối duy nhất.

### Có phải compiler luôn sinh một entry cho mỗi lần xuất hiện literal?

Không. Compiler có thể:
- Dùng lại cùng entry constant pool cho những chỗ dùng giống nhau khi hợp lệ.
- Inline giá trị compile-time constant hoặc tính trước biểu thức hằng.
- Dùng instruction với immediate operand, không qua pool.
- Sinh thông tin phụ, synthetic method, bootstrap arguments khi biên dịch lambda, nối chuỗi, record hoặc tính năng khác.
- Dùng các entry `Utf8` riêng cho cùng văn bản hoặc sắp xếp khác nhau; không nên viết công cụ dựa trên thứ tự bất biến.

Đặc tả mô tả **định dạng hợp lệ và semantics**, không hứa một layout `#1, #2, #3,...` cố định cho cùng mã nguồn ở mọi compiler.

## 13. Chuỗi literal và String intern pool: ví dụ tránh nhầm lẫn

Một `CONSTANT_String` trong class file chứa chỉ mục tới `CONSTANT_Utf8` cho nội dung literal. Khi JVM resolve hằng String phù hợp, chương trình nhận **tham chiếu đến một đối tượng `String`** theo semantics của string literal/interning; entry trong file **không phải byte array đóng gói đối tượng String runtime**.

Ví dụ đầy đủ **`StringPoolContrast.java`**:

~~~java
public class StringPoolContrast {
    public static void main(String[] args) {
        String a = "hello";
        String b = "hello";
        String c = new String("hello");

        System.out.println(a == b);
        System.out.println(a == c);
        System.out.println(a.equals(c));
        System.out.println(a == c.intern());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 StringPoolContrast.java
java StringPoolContrast
~~~

**Stdout:**

~~~text
true
false
true
true
~~~

Giải thích:
- Hai literal `"hello"` được canonicalize theo quy tắc string literal nên `a == b` đúng.
- `new String("hello")` tạo đối tượng khác nên `a == c` sai.
- `a.equals(c)` so sánh nội dung và đúng.
- `c.intern()` trả tham chiếu canonicalized của chuỗi đó nên `a == c.intern()` đúng.

**Không kết luận “mọi `Utf8` entry được intern thành String Java”.** Hầu hết entry `Utf8` trong class file có thể chỉ là tên lớp, descriptor, field hoặc attribute; cơ chế intern dành cho chuỗi runtime là một khái niệm riêng.

## 14. Các vấn đề thường gặp khi đọc `javap` hoặc tự xử lý class file

| Hiện tượng | Nguyên nhân/giải thích đúng | Cách kiểm tra |
| --- | --- | --- |
| Thấy `Methodref #A.#B` nhưng không thấy ngay tên phương thức | `Methodref` trỏ gián tiếp qua `Class` + `NameAndType`, rồi tới `Utf8` | Theo từng chỉ mục hoặc đọc comment của `javap -v`. |
| Chỉ số bắt đầu từ `#1` và có chỗ trống sau `Long` | Chỉ số 0 không hợp lệ; `Long`/`Double` chiếm hai slot | Đọc `constant_pool_count` và đặc tả `4.4.5`. |
| Có chuỗi literal nhưng không thấy dữ liệu String object | Class file lưu `CONSTANT_String` → `Utf8`, không lưu object heap | Tách khái niệm file constant pool với heap/String intern. |
| Biên dịch `5` nhưng constant pool không có `Integer 5` | `iconst_5` có thể đặt literal ngay trong opcode | Dùng `javap -c` xem instruction. |
| Field `static final` xuất hiện nhưng không thấy `getstatic` tại nơi dùng | Compile-time constant có thể bị inline | Xem `ConstantValue` và lệnh thật ở call site. |
| Chỉ số `#N` khác máy khác dù stdout như nhau | Compiler, target, công cụ tạo class file có thể thay layout | Đừng hardcode chỉ số pool vào bài kiểm thử. |
| `InvokeDynamic` có bootstrap index 0, tưởng là `#0` | BootstrapMethods index bắt đầu từ 0, khác chỉ số constant pool | Đọc đúng hai bảng. |
| Thấy `Ljava/util/List;` nhưng không thấy `String` trong descriptor generic | Type erasure; generic có thể nằm ở `Signature` | Dùng `javap -v -s -p`. |
| `NoSuchMethodError` xuất hiện dù compile đã thành công | Bản class runtime có member khác với bản dùng khi compile | So JAR/dependency, descriptor, class loader và module path. |
| Tự parse class file bị sai khi gặp tiếng Việt hoặc ký tự null | `Utf8` dùng modified UTF-8 với `length` byte | Đọc cấu trúc `u2 length` rồi decode đúng MUTF-8. |
| Công cụ parse gặp tag 17/18/19/20 không nhận ra | Parser cũ chưa hỗ trợ class file hiện đại | Nâng thư viện/parser và tôn trọng version/tag tương ứng. |
| Dùng tên `java.lang.String` trực tiếp làm internal name | JVM dùng `java/lang/String` trong Class/descriptor | Chuyển đúng binary/internal name theo định dạng. |

### Cách điều tra lỗi liên kết

Khi có lỗi như `NoSuchMethodError`, đừng sửa tùy tiện source chỉ vì lỗi có chữ *method*. Xác định:

1. Lỗi nhắc **owner class**, **method name** và **descriptor** nào.
2. Class nào phát sinh lời gọi? Trong bytecode của class đó, `javap -c -v` chỉ đến `Methodref` hoặc `InterfaceMethodref` nào.
3. Runtime thực sự nạp **phiên bản class/JAR** nào, bằng class loader nào; kiểm tra classpath/module path và các bản dependency trùng.
4. Bản class đích có đúng method với descriptor/access/staticness phù hợp hay không.
5. Build lại và chạy cùng tập artifact runtime. Lỗi nhị phân do thay thư viện mà không tái biên dịch caller có thể không xuất hiện lúc compile source của một module độc lập.

Một method trùng tên nhưng **khác descriptor** không được xem là cùng member để liên kết. Đây là một trong những lý do constant pool lưu **tên và kiểu** thành hai thành phần rõ ràng.

## 15. Tự kiểm tra

**Câu 1.** Constant pool trong `.class` có chỉ chứa số nguyên và String literal không?

**Đáp án:** Không. Nó còn chứa `Utf8` names, `Class`, `NameAndType`, field/method/interface method references, các entry phục vụ liên kết động và module.

**Câu 2.** Một `CONSTANT_Methodref` đủ để biết lớp, tên method, kiểu tham số và kiểu trả về bằng cách nào?

**Đáp án:** Từ `class_index` đến `CONSTANT_Class`/`Utf8` để lấy owner; từ `name_and_type_index` đến `CONSTANT_NameAndType`/hai `Utf8` để lấy tên và method descriptor.

**Câu 3.** Vì sao `Long` trong constant pool khiến một chỉ số tiếp theo không dùng được?

**Đáp án:** Định dạng class file quy định `CONSTANT_Long` và `CONSTANT_Double` chiếm **hai slot chỉ số**, dù là một entry có dữ liệu; slot kế tiếp không được tham chiếu.

**Câu 4.** `java/lang/String` và `Ljava/lang/String;` có giống vai trò không?

**Đáp án:** `java/lang/String` là internal name của lớp; `Ljava/lang/String;` là **field descriptor** cho kiểu tham chiếu đến lớp ấy. Descriptor có thêm `L` và `;`.

**Câu 5.** `(IJ)J` biểu diễn method như thế nào?

**Đáp án:** Hai tham số `int` và `long`, kiểu trả về `long`. Tên method và tên tham số không nằm trong chuỗi descriptor này.

**Câu 6.** Vì sao không nên hardcode `#12` ở `javap` thành “lời gọi println” trong một công cụ?

**Đáp án:** Thứ tự/chỉ số pool là chi tiết class file cụ thể, compiler có thể sinh layout khác. Phải đọc entry/chỉ mục thực tế từ chính file.

**Câu 7.** Trường `static final long BASE = 3_000_000_000L;` luôn gây `getstatic` ở mọi chỗ dùng không?

**Đáp án:** Không. Giá trị compile-time constant có thể được inline; nơi sử dụng có thể dùng `ldc2_w` thay vì `getstatic` của field.

**Câu 8.** `CONSTANT_InvokeDynamic` khác `CONSTANT_Dynamic` thế nào?

**Đáp án:** `InvokeDynamic` mô tả **điểm gọi động** để instruction `invokedynamic` liên kết ra `CallSite`; `Dynamic` mô tả **giá trị hằng động** có kiểu field descriptor, được tính khi resolution thông qua bootstrap.

**Câu 9.** Bootstrap index 0 có phải truy cập `constant_pool[0]` không?

**Đáp án:** Không. `bootstrap_method_attr_index` là chỉ số bắt đầu từ **0** trong danh sách bootstrap methods của attribute; constant pool bắt đầu từ **1**.

**Câu 10.** Cùng tên `com/example/Plugin` có bảo đảm cùng class runtime không?

**Đáp án:** Không. Class identity phụ thuộc cả defining class loader. Hai loader có thể định nghĩa những lớp cùng tên nhưng khác danh tính runtime.

## 16. Ghi nhớ trọng tâm

~~~text
ClassFile
  |
  +-- constant_pool (#1 .. #count-1)
  |      |
  |      +-- Literal       : Integer / Float / Long / Double / String
  |      +-- Text          : Utf8 (MUTF-8)
  |      +-- Type          : Class, Module, Package
  |      +-- Member symbols: Class + NameAndType -> Fieldref/Methodref/InterfaceMethodref
  |      +-- Dynamic links : MethodHandle/MethodType/Dynamic/InvokeDynamic
  |
  +-- fields / methods / Code / attributes
               |
               +-- tham chiếu chỉ mục pool

JVM: Loading -> Linking (bao gồm Resolution) -> Initialization -> Execution
        |
        +-- run-time constant pool và liên kết tham chiếu ký hiệu
~~~

**Ý chính:** constant pool là **bảng cấu trúc ký hiệu và hằng số** giúp class file biểu diễn phụ thuộc một cách độc lập với layout bộ nhớ runtime. Hiểu đường đi `Fieldref/Methodref → Class + NameAndType → Utf8`, cách descriptor mã hóa kiểu và cách JVM resolve là nền tảng để đọc `javap`, điều tra lỗi linking và tìm hiểu bytecode nâng cao.

## Tài liệu tham khảo chính thức

- [JVMS Java SE 21 — `4.1` The ClassFile Structure](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.1).
- [JVMS Java SE 21 — `4.3` Descriptors](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.3).
- [JVMS Java SE 21 — `4.4` The Constant Pool](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.4).
- [JVMS Java SE 21 — `4.7.23` BootstrapMethods](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.23).
- [JVMS Java SE 21 — `5.1` The Run-Time Constant Pool](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.1).
- [JVMS Java SE 21 — `5.4 Linking, đặc biệt `5.4.3 Resolution](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.4.3).
- [JDK 21 — javap tool](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-file-format-005">Access flags, lớp cha, giao diện, trường và phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Access flags, lớp cha, giao diện, trường và phương thức trong tệp `.class`

## 1. Mục tiêu và vị trí của các thành phần

Tệp `.class` không đơn thuần chứa một danh sách lệnh bytecode. Nó còn mô tả **danh tính lớp**, các **đặc điểm truy cập/loại lớp**, **lớp cha trực tiếp**, **các giao diện trực tiếp**, **các trường khai báo** và **các phương thức khai báo**. JVM dựa vào thông tin này để nạp, liên kết, xác minh, kiểm tra truy cập, thực thi và phản chiếu (reflection) đối với chương trình.

Bài này tập trung vào sáu thành phần của cấu trúc `ClassFile` trong **JVMS `4.1`**: `access_flags`, `this_class`, `super_class`, `interfaces[]`, `fields[]` và `methods[]`. Chúng dùng nhiều chỉ số trỏ vào **constant pool** và chứa các bảng con có cấu trúc riêng.

Sau khi học, bạn sẽ đọc được đầu ra `javap -v -p` để trả lời:

- Đây là `class`, `interface`, `enum`, annotation interface hay `module-info`? Loại nào là `public`, `final`, `abstract`?
- Lớp này trực tiếp `extends` và `implements` ai? Vì sao `super_class` của interface vẫn là `java/lang/Object`?
- Field và method nào được **khai báo trực tiếp** trong tệp, và thành viên nào chỉ được **kế thừa**?
- Flag `ACC_FINAL` của lớp/field/method có khác nhau không? Vì sao giá trị bit `0x0040` không thể hiểu nếu chưa biết nó thuộc bảng nào?
- Constructor `<init>`, class initializer `<clinit>`, field descriptor, method descriptor và `Code` attribute được biểu diễn ở đâu?
- Những trường hợp đặc biệt như `record`, `sealed class`, compiler-generated bridge method và `module-info.class` nên đọc thế nào?

## 2. Bản đồ cấu trúc `ClassFile`

Một tệp class hợp lệ có khung nhị phân (lược đồ đúng theo JVMS; `u1`, `u2`, `u4` lần lượt là các số không dấu 1, 2, 4 byte):

~~~text
ClassFile {
    u4             magic;
    u2             minor_version;
    u2             major_version;
    u2             constant_pool_count;
    cp_info        constant_pool[constant_pool_count - 1];

    u2             access_flags;
    u2             this_class;
    u2             super_class;

    u2             interfaces_count;
    u2             interfaces[interfaces_count];

    u2             fields_count;
    field_info     fields[fields_count];

    u2             methods_count;
    method_info    methods[methods_count];

    u2             attributes_count;
    attribute_info attributes[attributes_count];
}
~~~

Trình tự xuất hiện ở trên là **thứ tự dữ liệu trong file**, không phải thứ tự VM “thực thi các phần”. `magic` thông thường là `0xCAFEBABE`; `major_version` giúp nhận biết mức class-file format (ví dụ Java 17 → major 61, Java 21 → 65, Java 25 → 69). `constant_pool` chứa các biểu diễn tên, chuỗi, descriptor, tham chiếu lớp/field/method… mà các bảng phía sau liên hệ bằng **index**.

| Mục | Dạng dữ liệu | Ý nghĩa |
| --- | --- | --- |
| `access_flags` | `u2` bitmask | Các thuộc tính của **class/interface/module đang định nghĩa** |
| `this_class` | `u2` constant-pool index | Danh tính của chính class/interface này |
| `super_class` | `u2` constant-pool index hoặc 0 trong trường hợp đặc biệt | **Superclass trực tiếp** ở cấp class-file format |
| `interfaces_count`, `interfaces[]` | `u2` count và dãy index | Danh sách **direct superinterfaces** |
| `fields_count`, `fields[]` | count và dãy `field_info` | Các field **được khai báo** trong class/interface |
| `methods_count`, `methods[]` | count và dãy `method_info` | Các method/constructor/class initializer **được khai báo** |
| `attributes[]` cuối file | dãy `attribute_info` | Metadata ở cấp lớp như `SourceFile`, `Signature`, `Record`, `PermittedSubclasses`… |

**Nguyên tắc đọc:** `this_class`, `super_class`, từng phần tử `interfaces[]` là **chỉ số** tới `CONSTANT_Class_info` trong constant pool, không phải tên viết thẳng vào các trường `u2`. `fields[]` và `methods[]` lại chứa **các cấu trúc con**, mỗi cấu trúc cũng trỏ về constant pool để giải nghĩa tên và descriptor.

~~~text
ClassFile
  |
  +-- access_flags: ACC_PUBLIC | ACC_FINAL | ACC_SUPER
  +-- this_class -------> constant_pool[#X] = Class "demo/ClassLayout"
  +-- super_class ------> constant_pool[#Y] = Class "demo/Base"
  +-- interfaces[] -----> constant_pool[#A] = Class "demo/Labeled"
  |                    -> constant_pool[#B] = Class "java/io/Serializable"
  +-- fields[] ---------> field_info { flags, name_index, descriptor_index, attributes[] }
  +-- methods[] --------> method_info { flags, name_index, descriptor_index, attributes[] }
  +-- attributes[] -----> SourceFile, Signature, Record, ...
~~~

`#X`, `#Y`, `#A`, `#B` chỉ là **ký hiệu minh họa**: số index thực tế do compiler và nội dung class file quyết định.

## 3. `access_flags` của class/interface

`access_flags` là mặt nạ bit 16-bit: mỗi bit cho biết một quyền truy cập hoặc đặc điểm của **class/interface/module**. Nhiều cờ có thể cùng bật, nhưng JVMS có các quy tắc giới hạn tổ hợp.

| Cờ cấp class | Giá trị hex | Diễn giải đúng |
| --- | --- | --- |
| `ACC_PUBLIC` | `0x0001` | Class/interface được khai báo `public` |
| `ACC_FINAL` | `0x0010` | Class không thể bị subclass |
| `ACC_SUPER` | `0x0020` | Cờ tương thích lịch sử với ngữ nghĩa `invokespecial` |
| `ACC_INTERFACE` | `0x0200` | Đây là interface, không phải class thông thường |
| `ACC_ABSTRACT` | `0x0400` | Abstract, không được khởi tạo instance trực tiếp |
| `ACC_SYNTHETIC` | `0x1000` | Loại được compiler sinh, không tương ứng trực tiếp khai báo source |
| `ACC_ANNOTATION` | `0x2000` | Đây là annotation interface |
| `ACC_ENUM` | `0x4000` | Liên quan kiểu enum theo quy tắc JVMS |
| `ACC_MODULE` | `0x8000` | Tệp đặc biệt mô tả module, không phải class/interface thông thường |

Một class được khai báo `public final class ClassLayout` thường có `ACC_PUBLIC`, `ACC_FINAL` và `ACC_SUPER` trong file do `javac` hiện đại sinh. Để minh họa bitmask:

~~~text
ACC_PUBLIC = 0x0001
ACC_FINAL  = 0x0010
ACC_SUPER  = 0x0020
-------------------
Tổng       = 0x0031
~~~

Khi thấy `flags: (0x0031) ACC_PUBLIC, ACC_FINAL, ACC_SUPER`, bạn đang nhìn **quyền và đặc điểm của lớp**, chứ không phải flags của field hay method.

Các quy tắc đáng nhớ:

- `ACC_INTERFACE` yêu cầu `ACC_ABSTRACT`; interface không thể đồng thời đặt `ACC_FINAL` hay `ACC_SUPER`. Annotation interface đồng thời có `ACC_ANNOTATION` và `ACC_INTERFACE`.
- Class bình thường không được vừa `ACC_FINAL` vừa `ACC_ABSTRACT`.
- `ACC_SUPER` là cờ lịch sử; từ Java SE 8 trở đi, JVM coi cờ này như được bật cho mọi class file bất kể bit lưu trong file. **Không** dùng việc có/không có flag này để kết luận source có từ khóa `super`.
- `ACC_MODULE` là trường hợp đặc biệt của `module-info.class` với quy tắc cấu trúc riêng: flag này không được kết hợp với các cờ class khác.

### Những đặc điểm hiện đại không phải cờ class thông thường

- **Record** (`record Point(int x, int y) {}`) sử dụng **`Record` attribute** và các quy tắc cấu trúc tương ứng. Đừng tự coi `ACC_RECORD` là một bit `access_flags` chính thức trong bảng `ClassFile` của JVMS: một số thư viện bytecode có thể dùng hằng `ACC_RECORD` nội bộ hoặc dạng pseudo flag cho việc biểu diễn metadata.
- **Sealed class/interface** dùng **`PermittedSubclasses` attribute** để lưu danh sách các lớp con được phép; không có `ACC_SEALED` trong bảng access flags ở `4.1`.
- **Nest-based access** dùng metadata như `NestHost`/`NestMembers`, liên quan kiểm tra truy cập `private`; không suy từ `ACC_PRIVATE` của một “top-level class” (top-level class không có flag `ACC_PRIVATE` ở đây).
- `ACC_SYNTHETIC` của **class** chỉ nói về class do compiler sinh; `ACC_SYNTHETIC` còn có thể xuất hiện với field/method theo bảng tương ứng.

## 4. `this_class` và `super_class`: phân biệt chính mình với cha trực tiếp

### `this_class`

`this_class` bắt buộc trỏ tới một `CONSTANT_Class_info` biểu diễn **lớp hoặc interface đang được định nghĩa**. Tên nội bộ thường dùng dấu `/` thay dấu chấm:

~~~text
Source: package demo; public class App { }
Binary name: demo.App
JVM internal name: demo/App
Field descriptor (tham chiếu App): Ldemo/App;
~~~

`demo/App` trong constant pool là tên kiểu ở dạng nội bộ; `Ldemo/App;` lại là **field descriptor**. Phải phân biệt chúng vì cú pháp và vị trí sử dụng khác nhau.

### `super_class`

Trong class thông thường, `super_class` trỏ tới `CONSTANT_Class_info` của **direct superclass**:

~~~java
class Animal { }
class Dog extends Animal { }
class Poodle extends Dog { }
~~~

Với `Poodle.class`:

~~~text
this_class  -> Poodle
super_class -> Dog
~~~

`Animal` là **superclass gián tiếp**, không nằm trực tiếp trong `super_class` của `Poodle.class`. JVM biết chuỗi kế thừa bằng cách đi tiếp sang `Dog.class` rồi tra superclass của `Dog`.

Các trường hợp đặc biệt quan trọng:

1. **`java/lang/Object`**: class thông thường duy nhất không có direct superclass, nên `super_class = 0`.
2. **Interface**: trong **class-file format**, `super_class` của interface trỏ tới `java/lang/Object`. Điều này **không có nghĩa** interface `extends Object` trong source Java; direct superinterfaces nằm ở `interfaces[]`.
3. **`module-info.class` với `ACC_MODULE`**: `super_class = 0` theo quy tắc riêng và `interfaces_count = fields_count = methods_count = 0`. Đừng áp dụng quy tắc “chỉ Object có super_class 0” vào module descriptor mà bỏ qua ngoại lệ này.

Với một class không ghi `extends` trong source, superclass thông thường là `java/lang/Object`. `final` class có thể **kế thừa** một lớp khác nhưng không cho lớp thứ ba kế thừa chính nó.

## 5. `interfaces_count` và `interfaces[]`

`interfaces_count` là số lượng **direct superinterfaces**. Mảng `interfaces[]` chứa từng **index** tới `CONSTANT_Class_info` tương ứng, giữ **thứ tự từ trái qua phải trong source** theo JVMS.

Ví dụ:

~~~java
interface Identified {
    long id();
}

interface Named {
    String name();
}

class Base implements Identified {
    @Override public long id() { return 1; }
}

class Item extends Base implements Named, java.io.Serializable {
    @Override public String name() { return "Item"; }
}
~~~

Với `Item.class`:

~~~text
this_class    = Item
super_class   = Base
interfaces[]  = [Named, java/io/Serializable]
interfaces_count = 2
~~~

**`Identified` không xuất hiện trong `Item.class.interfaces[]`**, dù `Item` kế thừa việc triển khai interface đó qua `Base`. Bạn cần đọc `Base.class` để thấy quan hệ `Base implements Identified`.

Nếu một interface viết `interface B extends A, C`, file của `B` có `super_class -> java/lang/Object` nhưng `interfaces[] -> [A, C]`. Đây là cấu trúc cấp bytecode; không suy ngược rằng Java source cho phép class `extends` nhiều lớp cha: Java chỉ cho **một superclass trực tiếp**, nhưng có thể nhiều interface.

Các phần tử `interfaces[]` không chứa phương thức triển khai. Bảng này chỉ định **quan hệ kiểu trực tiếp**; các method riêng thuộc `methods[]` của từng loại tương ứng.

## 6. `fields[]`: các field khai báo trực tiếp

Mỗi field có một `field_info` độc lập theo JVMS `4.5`:

~~~text
field_info {
    u2 access_flags;
    u2 name_index;
    u2 descriptor_index;
    u2 attributes_count;
    attribute_info attributes[attributes_count];
}
~~~

`name_index` trỏ tới tên field dạng `CONSTANT_Utf8_info`, còn `descriptor_index` trỏ tới descriptor biểu diễn **kiểu dữ liệu ở cấp JVM**. Các attribute cho biết metadata bổ sung như `ConstantValue`, `Signature`, annotations hoặc thông tin deprecation.

| Field Java | Descriptor | Cờ tiêu biểu |
| --- | --- | --- |
| `private int count;` | `I` | `ACC_PRIVATE` |
| `public static final long MAX = 10L;` | `J` | `ACC_PUBLIC, ACC_STATIC, ACC_FINAL` |
| `volatile boolean running;` | `Z` | `ACC_VOLATILE` |
| `transient String secret;` | `Ljava/lang/String;` | `ACC_TRANSIENT` |
| `String[] names;` | `[Ljava/lang/String;` | theo modifier |
| `List<String> items;` | `Ljava/util/List;` | `Signature` có thể giữ generic signature |

Các cờ field chính:

| Tên cờ | Hex | Ngữ nghĩa |
| --- | --- | --- |
| `ACC_PUBLIC` / `ACC_PRIVATE` / `ACC_PROTECTED` | `0x0001` / `0x0002` / `0x0004` | Quyền truy cập field |
| `ACC_STATIC` | `0x0008` | Field thuộc class thay vì mỗi instance |
| `ACC_FINAL` | `0x0010` | Field có ràng buộc gán của final |
| `ACC_VOLATILE` | `0x0040` | Field volatile với ngữ nghĩa bộ nhớ Java |
| `ACC_TRANSIENT` | `0x0080` | Field transient, có ý nghĩa cho cơ chế serialization liên quan |
| `ACC_SYNTHETIC` | `0x1000` | Field do compiler sinh |
| `ACC_ENUM` | `0x4000` | Field đại diện hằng của enum |

**Các giới hạn:** một field không thể đồng thời `public` và `private`; cũng không thể đồng thời `ACC_FINAL` và `ACC_VOLATILE`. Các field được khai báo trong interface phải có `ACC_PUBLIC`, `ACC_STATIC` và `ACC_FINAL` (có thể thêm `ACC_SYNTHETIC` theo quy tắc).

### Field khai báo, field kế thừa và giá trị khởi tạo

`fields[]` của một class chỉ chứa các field **được khai báo trong class đó**, không nhét lại field kế thừa từ cha. `new Child()` có thể có state thuộc `Base` và `Child` nhưng `Child.class.fields[]` vẫn chỉ liệt kê phần khai báo trong `Child`.

`ACC_STATIC | ACC_FINAL` **không tự chứng minh** có `ConstantValue` attribute: chỉ một số hằng compile-time phù hợp dùng attribute này. Những initializer khác có thể được compiler đưa vào `<clinit>` với static field, hoặc `<init>`/instance initializer với instance field. `field_info` định nghĩa field và metadata; không phải chứa toàn bộ logic thực thi hàm gán giá trị.

Ví dụ `static int created;` có giá trị mặc định `0` ở preparation và không cần `<clinit>` chỉ để nhận 0. Còn `static int created = calculate();` thường phát sinh code khởi tạo class.

## 7. `methods[]`: method và các hàm khởi tạo đặc biệt

Mỗi phần tử `method_info` theo JVMS `4.6`:

~~~text
method_info {
    u2 access_flags;
    u2 name_index;
    u2 descriptor_index;
    u2 attributes_count;
    attribute_info attributes[attributes_count];
}
~~~

`name_index` cho biết tên, `descriptor_index` cho biết **tham số và kiểu trả về**. Cú pháp method descriptor:

~~~text
(ParameterDescriptors)ReturnDescriptor

()V                      -> void noArgs()
(I)I                     -> int f(int)
(JLjava/lang/String;)V   -> void g(long, String)
([Ljava/lang/String;)V   -> void main(String[])
()Ljava/lang/String;     -> String get()
~~~

| Cờ cấp method | Hex | Diễn giải |
| --- | --- | --- |
| `ACC_PUBLIC` / `ACC_PRIVATE` / `ACC_PROTECTED` | `0x0001` / `0x0002` / `0x0004` | Quyền truy cập method |
| `ACC_STATIC` | `0x0008` | Static method |
| `ACC_FINAL` | `0x0010` | Không được override method này |
| `ACC_SYNCHRONIZED` | `0x0020` | Thực thi có cơ chế monitor khi invoke |
| `ACC_BRIDGE` | `0x0040` | Bridge method do compiler sinh |
| `ACC_VARARGS` | `0x0080` | Method khai báo varargs |
| `ACC_NATIVE` | `0x0100` | Có phần triển khai native |
| `ACC_ABSTRACT` | `0x0400` | Không có thân method |
| `ACC_SYNTHETIC` | `0x1000` | Method do compiler sinh |
| `ACC_STRICT` | `0x0800` trong class major 46–60 | Flag lịch sử của `strictfp`; không còn có ý nghĩa đó ở major ngoài khoảng này |

Đọc bit theo **ngữ cảnh** là cực kỳ quan trọng:

- `0x0020` ở **class flags** là `ACC_SUPER`, ở **method flags** là `ACC_SYNCHRONIZED`.
- `0x0040` ở **field flags** là `ACC_VOLATILE`, còn ở **method flags** là `ACC_BRIDGE`.
- `0x0080` ở **field flags** là `ACC_TRANSIENT`, còn ở **method flags** là `ACC_VARARGS`.
- `ACC_FINAL` xuất hiện ở cả class/field/method, nhưng mang ràng buộc tương ứng từng loại; không được suy luận tất cả là một thao tác runtime giống nhau.

### `Code` attribute, abstract và native

Với method thông thường không có `ACC_ABSTRACT` hoặc `ACC_NATIVE`, implementation được mô tả bằng **`Code` attribute** (bytecode, stack/locals, exception table và một số metadata). `abstract` method không có body để lưu bytecode; `native` method triển khai theo cơ chế native chứ không có `Code` như Java method thông thường.

Một tệp `.class` có thể khai báo:

- `<init>`: **instance initialization method**, tương ứng constructor ở cấp JVM, descriptor có kiểu trả về `V`; mỗi constructor overload có descriptor riêng.
- `<clinit>`: **class/interface initialization method**, compiler sinh khi cần thực thi static initialization; không phải method bạn gọi bằng Java source như `obj.<clinit>()`.
- `main`: chỉ là tên một method trong `methods[]`. Việc launcher chọn `main` làm điểm vào là luật khởi chạy riêng, không khiến method này trở thành mục đặc biệt trong format `ClassFile`.

`methods[]` cũng chỉ chứa **phương thức khai báo** trong class/interface đó, không chép các method kế thừa. Một `default method` do interface định nghĩa thuộc `methods[]` **của interface**, và một class chỉ kế thừa implementation đó không nhất thiết có cùng `method_info` trong file của mình.

### Generics, overload và bridge method

JVM sử dụng **descriptor** để xác định field/method type. Với `List<String>`, descriptor JVM thông thường là `Ljava/util/List;` sau type erasure; thông tin generic có thể được giữ qua **`Signature` attribute**, không trở thành một descriptor runtime dạng `List<String>`.

Overload được phân biệt bởi descriptor khác nhau. Trong class file, không có hai `field_info` cùng **name + descriptor**, và không có hai `method_info` cùng **name + descriptor**. Vì descriptor method chứa **kiểu trả về**, bytecode và ngôn ngữ Java không hoàn toàn cùng quy tắc đánh giá một “chữ ký” hợp lệ; đừng tự áp dụng nguyên tắc overload source Java như quy tắc toàn diện của class-file verifier.

Đối với generic overriding hoặc covariant return, compiler có thể phát sinh **bridge method** để giữ dispatch đúng sau erasure. Một bridge thường mang `ACC_BRIDGE | ACC_SYNTHETIC`, có method descriptor riêng và `Code` chuyển tiếp đến implementation thật. Đây là lý do `javap -p -v` đôi khi hiển thị nhiều method hơn số method rõ ràng trong source.

## 8. Ví dụ Java 17+: đọc một class từ source đến `javap`

Tạo file `ClassLayout.java` trong thư mục trống. Đoạn code sau **biên dịch và chạy độc lập bằng Java 17+**:

~~~java
interface Labeled {
    String label();

    default String formatted() {
        return "[" + label() + "]";
    }
}

abstract class Base {
    protected final long id;

    Base(long id) {
        this.id = id;
    }

    public long id() {
        return id;
    }
}

public final class ClassLayout extends Base
        implements Labeled, java.io.Serializable {

    private static final long serialVersionUID = 1L;
    private static int created;
    private final String name;

    public ClassLayout(long id, String name) {
        super(id);
        this.name = name;
        created++;
    }

    @Override
    public String label() {
        return name;
    }

    public int score(int bonus) {
        return name.length() + bonus;
    }

    public static int created() {
        return created;
    }

    public static void main(String[] args) {
        ClassLayout item = new ClassLayout(7L, "JVM");
        System.out.println(item.formatted());
        System.out.println("id=" + item.id()
                + ",score=" + item.score(2)
                + ",created=" + created());
    }
}
~~~

Chạy:

~~~bash
javac ClassLayout.java
java ClassLayout
~~~

**Stdout chính xác:**

~~~text
[JVM]
id=7,score=5,created=1
~~~

Giải thích: `ClassLayout` thừa hưởng `id()` và field `id` từ `Base`, triển khai `label()`, kế thừa default `formatted()` của `Labeled`, có field `name` của riêng mình. Constructor tăng `created` một lần. `"JVM".length() + 2 = 5`.

Khi `javac ClassLayout.java` chạy, thư mục sẽ có các file `Labeled.class`, `Base.class`, `ClassLayout.class`; mỗi class/interface có **ClassFile riêng**. Class `Base` không bị “nhét nguyên” vào `ClassLayout.class`.

### Dùng `javap` để đọc cấu trúc

~~~bash
javap -p ClassLayout
javap -v -p ClassLayout
javap -v -p Base
javap -v -p Labeled
javap -c -p ClassLayout
~~~

`-p` cho thấy cả thành viên `private`, `-v` cho metadata/constant pool/descriptor/flags/attributes, `-c` disassemble `Code`. Output đầy đủ gồm nhiều `#index`, BCI và constant pool entries có thể thay đổi theo phiên bản compiler; vì vậy bên dưới là **những quan hệ cần kiểm chứng**, không khẳng định từng dòng output byte-for-byte giống trên mọi JDK.

| Vị trí kiểm tra | Điều nên quan sát với `ClassLayout.class` |
| --- | --- |
| Class header/flags | `public final class ClassLayout extends Base implements Labeled, java.io.Serializable`; cờ tương ứng `ACC_PUBLIC`, `ACC_FINAL` và thường `ACC_SUPER` |
| `this_class` | Constant-pool `Class` trỏ tới `ClassLayout` |
| `super_class` | Constant-pool `Class` trỏ tới `Base`, **không phải `java/lang/Object`** |
| `interfaces[]` | Hai direct interfaces: `Labeled` và `java/io/Serializable` theo thứ tự source |
| `fields[]` | `serialVersionUID`, `created`, `name` được khai báo trực tiếp |
| `methods[]` | Constructor `<init>`, `label`, `score`, `created`, `main`; các compiler-generated members khác chỉ khi compiler cần |
| `Code` của constructor | Gọi `Base.<init>(long)`, gán `name` và tăng `created` |
| `Code` của `main` | Tạo `ClassLayout`, gọi `formatted`, `id`, `score` và in kết quả |

Những gì **không nên tìm trong `ClassLayout.class` như phần tử khai báo của nó**:

- Field `id`: thuộc `Base.fields[]`.
- Method `id()`: thuộc `Base.methods[]`.
- Default `formatted()`: thuộc `Labeled.methods[]`; `ClassLayout` không override method đó.
- `java/lang/Object` như `super_class` trực tiếp: class này extends `Base`, còn `Base` mới trực tiếp extends `Object`.

### Descriptor thật của các method/field

| Khai báo | Descriptor của bản trong `ClassLayout.class` |
| --- | --- |
| `private static final long serialVersionUID` | `J` |
| `private static int created` | `I` |
| `private final String name` | `Ljava/lang/String;` |
| `ClassLayout(long, String)` | `(JLjava/lang/String;)V` |
| `String label()` | `()Ljava/lang/String;` |
| `int score(int bonus)` | `(I)I` |
| `static int created()` | `()I` |
| `static void main(String[])` | `([Ljava/lang/String;)V` |

`serialVersionUID` là compile-time constant `long` và thường có `ConstantValue: long 1l`, còn `created` được mặc định bằng 0 và tăng trong constructor. Không suy từ hai field cùng `static` rằng chúng phải có cùng attribute hoặc cùng code khởi tạo.

### Đọc một field và một method dưới dạng schematic

Đầu ra dưới đây **rút gọn để học cấu trúc**; không phải dump thực tế:

~~~text
fields_count: 3
  field_info:
    access_flags: ACC_PRIVATE | ACC_FINAL
    name_index:        #... -> Utf8 "name"
    descriptor_index:  #... -> Utf8 "Ljava/lang/String;"
    attributes: ...

methods_count: ...
  method_info:
    access_flags: ACC_PUBLIC
    name_index:        #... -> Utf8 "score"
    descriptor_index:  #... -> Utf8 "(I)I"
    attributes:
      Code -> bytecode, max_stack, max_locals, exception_table, ...
~~~

Mỗi `#...` là index vào constant pool, khác với các offset bytecode in ra bởi `javap -c`.

## 9. Trường hợp có thể gây bất ngờ trong file thật

### A. Giao diện kế thừa giao diện

~~~java
interface Root { void root(); }
interface Left extends Root { }
interface Right extends Root { }
interface Combined extends Left, Right { }
~~~

`Combined.class` có `super_class -> java/lang/Object` và `interfaces[] -> [Left, Right]`. `Root` là tổ tiên giao diện **gián tiếp**, không tự động xuất hiện trong danh sách direct superinterfaces. `Combined.methods[]` có thể **không khai báo method mới nào** vì `root()` được kế thừa.

### B. `enum` và annotation interface

Enum có class-level `ACC_ENUM`; những field đại diện từng enum constant cũng có `ACC_ENUM` **ở field flags**. Annotation interface có `ACC_INTERFACE`, `ACC_ABSTRACT` và `ACC_ANNOTATION`; các method mô tả phần tử annotation, khác với method instance thông thường của class. Khi gặp flags, hãy đọc theo **loại tệp** đang phân tích.

### C. Record và sealed class

~~~java
record Coordinate(int x, int y) { }

sealed interface Shape permits Circle, Rectangle { }
record Circle(double radius) implements Shape { }
record Rectangle(double width, double height) implements Shape { }
~~~

Đây là **ví dụ source Java 17+**, nhưng không phải chương trình có `main` để chạy trực tiếp. Với record, kiểm tra `Record` attribute, superclass `java/lang/Record`, component metadata, fields/accessor methods được compiler sinh. Với sealed interface, tra `PermittedSubclasses` attribute. **Không** tìm `ACC_RECORD` hoặc `ACC_SEALED` trong bảng class-level `access_flags` của JVMS `4.1`.

### D. Method do compiler tạo

Khi xem source generics/bridge, lambda hoặc nested class, `methods_count` không nhất thiết bằng số lời khai báo method trong source. Compiler có thể sinh method bổ trợ `ACC_SYNTHETIC`, `ACC_BRIDGE`, và các attributes metadata khác. Khi đọc `javap`, hãy tách **khai báo có thật trong class file** khỏi **khai báo bạn thấy trong source**.

### E. Module descriptor

`module-info.class` là cấu trúc đặc biệt. Nó dùng `ACC_MODULE`, `this_class = module-info`, `super_class = 0`, và `interfaces_count = fields_count = methods_count = 0`, đồng thời chứa `Module` attribute theo quy tắc đặc tả. Nếu công cụ parser nghĩ đây là class kế thừa `Object` thông thường hoặc luôn đòi có constructor, parser đang đọc sai trường hợp `ACC_MODULE`.

## 10. Những lỗi phổ biến khi phân tích hoặc viết parser

| Lỗi | Sửa cách hiểu |
| --- | --- |
| Coi số `this_class`/`super_class` là chuỗi tên đã mã hóa | Đó là **index** tới `CONSTANT_Class_info` trong constant pool |
| Cho rằng `super_class` bằng 0 nghĩa class không có cha trong mọi trường hợp | `Object` là class thông thường có zero; `module-info.class` có quy tắc đặc biệt |
| Nghĩ `interfaces[]` liệt kê mọi interface đã kế thừa qua superclass | Chỉ liệt kê **direct superinterfaces** của class/interface đang đọc |
| Nhìn `fields[]` thiếu field cha và kết luận object không có state đó | Field cha thuộc `fields[]` của superclass, không được copy vào class con |
| Coi `methods[]` là tất cả method mà object có thể gọi | Chỉ các method khai báo trong class; inherited/default methods nằm nơi định nghĩa |
| Diễn giải `0x0040` không xét cấu trúc | Là `ACC_VOLATILE` với field, `ACC_BRIDGE` với method |
| Coi `ACC_SUPER` là source `super` hoặc class đang extends lớp nào | Nó là cờ lịch sử của `invokespecial`; hãy đọc `super_class` để biết cha trực tiếp |
| Tìm `ACC_RECORD` hay `ACC_SEALED` trong JVMS ClassFile flags | Kiểm tra `Record` và `PermittedSubclasses` attributes |
| Mặc định field `static final` luôn có giá trị trong `ConstantValue` | Chỉ một số hằng phù hợp; static initialization khác có thể nằm ở `<clinit>` |
| Nghĩ `main` là trường đặc biệt của `ClassFile` bên ngoài `methods[]` | `main` là một `method_info` thông thường, được launcher chọn theo luật entry |
| Đồng nhất method descriptor với generic signature | Descriptor dùng kiểu JVM sau erasure; `Signature` có thể bổ sung metadata generic |
| Dùng `javap -c` để suy toàn bộ API và metadata | Kết hợp `javap -v -p` để thấy flags, descriptors, constant pool và attributes |

## 11. Quy trình thực hành và checklist đọc class file

Khi nhận một tệp `.class` bất kỳ, trình tự này giúp phân tích có hệ thống:

1. **Kiểm tra nguồn và phiên bản file:** `javap -verbose TênClass`, tìm `major version`, `minor version`, `this_class` và `flags`.
2. **Đọc danh tính/quan hệ kiểu:** xác nhận `this_class`, `super_class`, `interfaces_count` và thứ tự `interfaces[]`; nếu có nghi vấn kế thừa, mở thêm file superclass/interface.
3. **Đọc access flags đúng bảng:** class ở JVMS `4.1`; field ở `4.5`; method ở `4.6`. Không diễn giải bit mask xuyên bảng.
4. **Tách members:** `fields_count`/`methods_count` có bao nhiêu phần tử khai báo thực tế; so với source và các inherited members.
5. **Đọc descriptors:** chuyển ký hiệu `I`, `J`, `Ljava/lang/String;`, `[I`, `(I)I` sang kiểu dữ liệu tương ứng.
6. **Đọc attributes theo nơi chúng thuộc về:** `ConstantValue` ở field; `Code` ở method thực thi; `Signature` cho generics; `Record`, `PermittedSubclasses`, `Nest*` ở cấp loại theo đặc tả.
7. **Nếu lỗi link/verify/access:** đối chiếu class-file version, cấu trúc constant pool, quy tắc tổ hợp flags, descriptor và quyền truy cập runtime. Đừng suy từ source mà bỏ qua class-file thực tế.

Lệnh thường dùng (Java 17+):

~~~bash
javap -help
javap -p ClassLayout
javap -v -p ClassLayout
javap -c -p ClassLayout
javap -v -p Labeled
~~~

Nếu class nằm trong package hoặc JAR, sử dụng fully qualified name với classpath:

~~~bash
javap -v -p -classpath app.jar demo.ClassLayout
~~~

`javap` là công cụ quan sát class file, không cần chạy `main`. Với file không rõ nguồn, hãy ưu tiên kiểm tra offline và không tải/chạy các lớp chỉ để “xem thử”. Đầu ra `javap` không phải một bản dump nguyên byte, nhưng phù hợp để học các quan hệ cấu trúc nói trên.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `ClassLayout` extends `Base`, `Base` extends `Object`. `ClassLayout.class.super_class` là gì?  
**Đáp án:** `Base`. `Object` là ancestor gián tiếp của `ClassLayout`.

**Câu 2.** `ClassLayout` chỉ trực tiếp implements `Labeled` và `Serializable`. `interfaces_count` bằng bao nhiêu?  
**Đáp án:** 2, vì đếm các **direct superinterfaces** trong file của chính lớp đó.

**Câu 3.** Tại sao `ClassLayout.methods[]` không nhất thiết chứa `formatted()`?  
**Đáp án:** Vì `formatted()` là default method được khai báo trong `Labeled`; class kế thừa implementation qua giao diện, không cần một `method_info` cho method đó nếu không override.

**Câu 4.** Nếu thấy `0x0020` trong flags của class và method, nghĩa giống nhau không?  
**Đáp án:** Không. Class: `ACC_SUPER`; method: `ACC_SYNCHRONIZED`.

**Câu 5.** `private final String name` có descriptor và flag chính nào?  
**Đáp án:** Descriptor `Ljava/lang/String;`, các cờ `ACC_PRIVATE` và `ACC_FINAL`.

**Câu 6.** Vì sao `main([Ljava/lang/String;)V` vẫn nằm trong `methods[]`?  
**Đáp án:** Vì `main` là method của class; launcher chọn nó làm entry nhưng format class không tạo bảng `main` riêng.

**Câu 7.** Khi đọc `record` hoặc `sealed class`, tìm bit `ACC_RECORD`/`ACC_SEALED` có đúng không?  
**Đáp án:** Không trong bảng `ClassFile.access_flags` chuẩn. Xem `Record`/`PermittedSubclasses` attributes và metadata liên quan.

**Câu 8.** Vì sao generics `List<String>` thường hiện descriptor `Ljava/util/List;`?  
**Đáp án:** Descriptor JVM sử dụng kiểu erased; `Signature` attribute có thể lưu thêm thông tin kiểu generic.

## 13. Tài liệu chuẩn để tra cứu

- [JVMS Java SE 25 — `4.1` The ClassFile Structure](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.1): toàn bộ khung class file, class `access_flags`, `this_class`, `super_class`, `interfaces`, `fields` và `methods`.
- [JVMS Java SE 25 — `4.2` Names](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.2): tên dạng nội bộ và tên nhị phân.
- [JVMS Java SE 25 — `4.3` Descriptors](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.3): field descriptors và method descriptors.
- [JVMS Java SE 25 — `4.5` Fields](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.5): `field_info`, flags, ràng buộc static/final/volatile và interface fields.
- [JVMS Java SE 25 — `4.6` Methods](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.6): `method_info`, flags, phương thức `<init>`/`<clinit>`.
- [JVMS Java SE 25 — `4.7` Attributes](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7): `Code`, `ConstantValue`, `Signature`, `Record`, `PermittedSubclasses` và các metadata khác.
- [Java `javap` manual, JDK 25](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javap.html): các tùy chọn `-v`, `-p`, `-c` và cách kiểm tra binary.
- [Java SE Specifications](https://docs.oracle.com/javase/specs/): chọn đúng release của JVMS nếu đang phân tích class file do JDK khác tạo ra.

**Ghi nhớ:** `access_flags` mô tả **tính chất loại đang khai báo**; `this_class`/`super_class`/`interfaces[]` mô tả **danh tính và quan hệ trực tiếp** thông qua constant pool; `fields[]` và `methods[]` mô tả **thành viên được khai báo tại chính loại đó**. Đọc cùng descriptors và attributes sẽ giúp bạn hiểu cấu trúc class file mà không đánh đồng nó với cú pháp source Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-file-format-006">Thuộc tính Code: max_stack, max_locals, mã lệnh và bảng xử lý ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Thuộc tính Code trong tệp class: `max_stack`, `max_locals`, bytecode và bảng xử lý ngoại lệ

Một phương thức Java đã biên dịch thường không còn lưu source code trong tệp `.class`. Thay vào đó, phương thức có một **`Code` attribute** mô tả mã lệnh JVM, số slot tối đa của operand stack, số slot biến cục bộ cần cấp cho frame, các vùng bắt ngoại lệ và một số metadata phụ trợ. Đọc đúng cấu trúc này giúp giải thích vì sao một lời gọi Java hoạt động, vì sao cast/branch/`try-catch` xuất hiện thành nhiều bytecode instructions, và vì sao một tệp `.class` có thể bị từ chối khi verification.

Bài này dùng **Java SE 17 (class-file major version 61)** làm mốc. Các quy tắc cấu trúc lấy từ Java Virtual Machine Specification (JVMS), chủ yếu `4.7.3, `2.6, `4.10, `6.5. Các bản JDK khác có thể tạo chuỗi bytecode khác nhau mà vẫn đúng ngữ nghĩa; đừng mặc định ảnh chụp từ `javap` là cấu trúc duy nhất một compiler được phép tạo.

## 1. Thuộc tính `Code` nằm ở đâu?

Một tệp `.class` gồm phần đầu, constant pool, thông tin class, fields, methods và attributes. Trong bảng `methods[]`, mỗi `method_info` có một bảng `attributes[]`. Với phương thức có phần thân mà JVM thực thi, một phần tử của bảng ấy là **`Code`**.

```text
ClassFile
├── magic, minor_version, major_version
├── constant_pool
├── access_flags, this_class, super_class, interfaces
├── fields[]
├── methods[]
│   └── method_info của một phương thức cụ thể
│       ├── access_flags, name_index, descriptor_index
│       └── attributes[]
│           └── Code_attribute
│               ├── max_stack
│               ├── max_locals
│               ├── code_length + code[] (bytecode)
│               ├── exception_table[]
│               └── attributes[] của chính Code
└── attributes[] của class
```

Điểm cần phân biệt:

- **`method_info`** mô tả một phương thức bằng tên, descriptor (kiểu tham số/kiểu trả về theo định dạng JVM), flags và attributes.
- **`Code_attribute`** mô tả code và thông tin hỗ trợ thực thi **cho một phương thức**, không phải cho toàn class, một lời gọi riêng lẻ hay toàn bộ JVM.
- **Một lần gọi phương thức** có một JVM frame riêng với operand stack và local variables; mỗi frame dùng giới hạn từ `max_stack` và `max_locals` của method tương ứng. Nhiều lời gọi đệ quy cùng một method tạo nhiều frame, cùng khuôn metadata nhưng giá trị locals khác nhau.
- Phương thức **`abstract` hoặc `native`** không có `Code` attribute trong `method_info`. Phương thức bình thường có code phải có **đúng một** `Code` attribute theo quy tắc class file.
- Constructor là method `<init>`; class/interface initializer là `<clinit>` nếu compiler phát sinh. Chúng cũng có thể chứa `Code`. Một method `default` của interface có thân cũng thuộc nhóm có code.
- `Code` không đồng nghĩa `SourceFile` hoặc `SourceDebugExtension`: các metadata ấy có vai trò khác.

Nếu class có mười method có body, thông thường bạn sẽ thấy nhiều mục `Code:`, mỗi mục gắn với một method. Đọc một mục không thể áp dụng `max_stack` hay `exception_table` của nó cho method khác.

## 2. Cấu trúc nhị phân chuẩn của `Code_attribute`

JVMS `4.7.3` định nghĩa cấu trúc sau. Đây là **pseudocode đặc tả định dạng class file**, không phải chương trình Java độc lập:

```text
Code_attribute {
    u2 attribute_name_index;
    u4 attribute_length;
    u2 max_stack;
    u2 max_locals;
    u4 code_length;
    u1 code[code_length];
    u2 exception_table_length;
    {
        u2 start_pc;
        u2 end_pc;
        u2 handler_pc;
        u2 catch_type;
    } exception_table[exception_table_length];
    u2 attributes_count;
    attribute_info attributes[attributes_count];
}
```

`u1`, `u2`, `u4` lần lượt là **số nguyên không dấu** mã hóa trên 1, 2, 4 byte trong class file; các giá trị nhiều byte trong định dạng class file dùng thứ tự byte big-endian.

| Trường | Kích thước/ý nghĩa | Cách đọc |
| --- | --- | --- |
| `attribute_name_index` | `u2`, chỉ số constant pool | Trỏ tới `CONSTANT_Utf8_info` có giá trị `Code` |
| `attribute_length` | `u4` | Số byte của nội dung attribute, **không tính 6 byte đầu** dành cho name index và length |
| `max_stack` | `u2` | Độ sâu operand stack **tối đa** mà method cần, tính theo đơn vị slot |
| `max_locals` | `u2` | Số slot trong local-variable array của mỗi frame method |
| `code_length` | `u4` | Số **byte** trong mảng mã lệnh, không phải số lệnh và không phải số dòng Java |
| `code[]` | `u1[code_length]` | Opcode cùng byte operand, branch offsets, dữ liệu switch, v.v. |
| `exception_table_length` | `u2` | Số entry xử lý ngoại lệ |
| `exception_table[]` | Mỗi entry gồm 4 trường `u2` | Dải bytecode được bảo vệ, đích handler và kiểu exception |
| `attributes_count` | `u2` | Số attribute **lồng trong Code**, ví dụ `StackMapTable` hoặc `LineNumberTable` |
| `attributes[]` | Độ dài thay đổi | Metadata verification và debug gắn với mã của method |

Giới hạn quan trọng: `code_length` **lớn hơn 0 và nhỏ hơn 65536**, nghĩa là một `Code` chỉ chứa tối đa **65535 byte** bytecode. Một lệnh nhiều byte vẫn tính theo từng byte. `javap -c` cho thấy **bytecode offset**, không cho số thứ tự của lệnh theo hàng source.

Đừng nhầm tên `Code` attribute với constant pool entry chứa literal chuỗi `Code`: `attribute_name_index` chỉ **định danh loại attribute**; mã thực nằm trong `code[]`.

## 3. `max_stack`: số slot tối đa của operand stack trong một frame

Mỗi JVM frame có **operand stack** riêng. Bytecode thường đưa giá trị lên stack bằng `iload`/`lload`/`iconst_*`, lấy toán hạng ra bằng phép cộng hoặc phép gọi, rồi đẩy kết quả lên. `max_stack` cho biết độ sâu **tối đa tính theo đơn vị stack slot** cần cho method này.

Ví dụ phép cộng hai `int` trong một method:

```text
Trạng thái operand stack (đáy → đỉnh):
ban đầu             []
iload_0             [a]        // 1 slot
iload_1             [a, b]     // 2 slot
iadd                [a+b]      // 1 slot
ireturn             []         // trả giá trị về caller
```

Độ sâu tối đa của đoạn này là **2 slot**, nên một method chỉ thực hiện chuỗi ấy có thể có `max_stack = 2`. `max_stack` **không** phải số opcode, số lượng biến Java, số byte của tệp class, dung lượng RAM dành cho mọi stack của tiến trình, hoặc giá trị thực tế tại mọi thời điểm.

### Giá trị loại category 1 và category 2

Trong mô hình JVMS:

- `int`, `float`, reference và các giá trị tính toán thuộc **category 1** chiếm **1 slot** của operand stack.
- `long` và `double` thuộc **category 2** chiếm **2 slot**.
- Các giá trị Java `byte`, `short`, `char` thường được biểu diễn/tính bằng `int` trên operand stack; không có tập opcode cộng `byte` hoàn toàn độc lập.

Ví dụ method `sumLong(long a, long b)` có thể có dãy:

```text
lload_0             [a(long)]              // 2 slot
lload_2             [a(long), b(long)]     // 4 slot
ladd                [a+b(long)]           // 2 slot
lreturn             []                    // kết thúc frame
```

Vì cần cùng lúc hai `long`, độ sâu tối đa ở đây là **4 slot**. Đếm “có hai số nên max_stack bằng 2” là sai. Một giá trị `long` vẫn là **một giá trị logic**, nhưng đóng góp **hai đơn vị** vào độ sâu stack.

### `max_stack` là mức tối đa tĩnh, không phải stack trace runtime

- Nó là metadata của **method** do compiler/class-file generator điền để phục vụ tổ chức thực thi và kiểm chứng hợp lệ.
- `max_stack = 4` không hứa luôn luôn có bốn slot dữ liệu trên stack; một số nhánh có thể dùng ít hơn.
- Không được lấy `max_stack` để suy ra độ sâu **call stack**, số lần đệ quy có thể thực hiện hay giá trị `-Xss`. Mỗi lần gọi method lại tạo frame riêng.
- Nếu bytecode cố đẩy nhiều slot hơn mức đã khai báo trên một đường thực thi hợp lệ, class file không thỏa yêu cầu verification; JVM có thể từ chối bằng `VerifyError` hoặc lỗi định dạng/liên kết thích hợp tùy vi phạm.

## 4. `max_locals`: số slot dành cho tham số và biến cục bộ

Mỗi frame chứa mảng **local variables** được đánh chỉ số `0, 1, 2, ...`. `max_locals` xác định tổng số **slot** cấp cho mảng này. Nó bao gồm slot cho tham số truyền vào method và các biến tạm do compiler sinh ra khi cần.

Bảng quy tắc:

| Tình huống | Vị trí local variables |
| --- | --- |
| Method instance | Slot `0` thường chứa `this`, sau đó là tham số |
| Method `static` | Không có `this` ẩn; tham số đầu tiên bắt đầu ở slot `0` |
| Tham số `int`/`float`/reference | Chiếm một slot |
| Tham số `long`/`double` | Chiếm **hai slot liên tiếp** |
| Biến trong các scope không giao nhau | Compiler có thể tái sử dụng một slot |
| Exception local ở `catch` | Compiler thường dùng thêm slot để chứa `Throwable` được handler nhận |

Với method `public static long sumLong(long left, long right)`, `left` chiếm slot **0–1**, `right` chiếm slot **2–3**; method chỉ cần hai tham số này nên có thể có **`max_locals = 4`**.

Với method `public int increment(int value)`, slot **0** chứa `this`, slot **1** chứa `value`; có thể có **`max_locals = 2`**.

Một method `static void run()` không nhận tham số và không dùng biến cục bộ có thể có `max_locals = 0`. Đừng cho rằng mọi method đều có `this`.

### Hai lưu ý về index và tên biến

1. Chỉ số local variable là **slot trong frame**, không phải số thứ tự khai báo trong source. Tham số `double` hoặc `long` tạo khoảng cách hai slot; compiler còn có thể thêm slot cho giá trị trung gian hoặc exception.
2. `max_locals` không lưu tên biến `left` hay `right`. Tên biến/ranh giới scope có thể xuất hiện trong attribute tùy chọn `LocalVariableTable` khi biên dịch với debug info, nhưng khi không có bảng đó, JVM vẫn hiểu và thực thi bytecode qua **index** và **kiểu**.

Trong `javap`, trường `locals=4` là thông tin từ `max_locals`; phần `LocalVariableTable` (nếu xuất hiện) cho biết một số tên biến và khoảng sống gắn với các slot, **không phải** kích thước thay thế cho `max_locals`.

## 5. `code_length` và `code[]`: nơi đặt bytecode thực sự

`code[]` là dãy **byte** của các JVM instructions. Một số opcode không có operand; một số opcode đọc thêm một hoặc nhiều byte. Lệnh có độ dài biến thiên như `tableswitch` và `lookupswitch` còn có yêu cầu căn chỉnh và các phần dữ liệu bổ sung. Vì thế:

- `code_length` đo **số byte**; từ độ dài không suy ra ngay số instruction.
- **Bytecode index/offset (BCI)** của `javap -c` là **vị trí byte bắt đầu opcode** trong `code[]`. Nó không phải số dòng Java.
- `goto`/`if_*`/`tableswitch` nhảy đến bytecode index hợp lệ; mục tiêu nhảy cần rơi vào **đầu một instruction**, không được nhảy vào giữa phần operand của lệnh.
- `ireturn`, `lreturn`, `areturn`, `return` kết thúc bình thường phần thực thi method tương ứng với kiểu kết quả.
- `athrow` ném reference kiểu `Throwable`, có thể làm JVM tìm exception handler trong cùng method hoặc các frame gọi bên ngoài.

Ví dụ đọc output:

```text
  0: iload_0
  1: iload_1
  2: idiv
  3: ireturn
  4: astore_2
  5: iconst_m1
  6: ireturn
```

Các số `0, 1, 2, 3, 4, 5, 6` ở đây là **offset theo byte**. Chúng liên tục vì từng opcode trong ví dụ chiếm đúng một byte; trong method khác, bytecode index có thể tăng 2, 3 hoặc nhiều hơn giữa hai instruction được in.

### Bytecode thường gặp trong nội dung bài

| Nhóm | Ví dụ opcode | Vai trò |
| --- | --- | --- |
| Nạp local lên stack | `iload_0`, `iload_1`, `lload_0`, `aload_0` | Đọc slot local với loại phù hợp |
| Ghi stack xuống local | `istore`, `astore_2`, `lstore` | Lưu giá trị vào slot local |
| Hằng đơn giản | `iconst_m1`, `iconst_1`, `bipush` | Đẩy giá trị số nguyên |
| Tính toán | `iadd`, `idiv`, `ladd` | Lấy operands từ stack, trả kết quả lên stack |
| Chuyển kiểu | `i2l`, `l2i`, `i2b` | Thay cách biểu diễn/độ rộng theo ngữ nghĩa JVM |
| Điều khiển | `ifeq`, `if_icmpge`, `goto` | Thay BCI thực thi |
| Gọi/return | `invokestatic`, `invokevirtual`, `ireturn`, `lreturn` | Tương tác giữa các frame |
| Exception | `athrow` | Ném exception, có thể đi tới handler |

Trường `code[]` chứa tất cả opcode và các operand bytes tương ứng, chứ không lưu tên ký hiệu `iload_0` dưới dạng text. `javap` là công cụ **disassembler** chuyển mã nhị phân thành mnemonic đọc được.

## 6. `exception_table[]`: xác định handler cho các vùng bytecode

Một entry trong exception table gồm bốn trường:

```text
start_pc  end_pc  handler_pc  catch_type
```

| Trường | Ý nghĩa |
| --- | --- |
| `start_pc` | BCI bắt đầu **bao gồm** trong vùng code được bảo vệ |
| `end_pc` | BCI kết thúc **không bao gồm**; có thể bằng `code_length` |
| `handler_pc` | BCI của instruction đầu tiên trong handler |
| `catch_type` | Index của `CONSTANT_Class_info` mô tả kiểu exception được bắt, hoặc `0` nếu handler bắt tất cả |

Một handler áp dụng khi exception xảy ra ở instruction có offset `pc` thỏa **`start_pc <= pc < end_pc`**. `start_pc` phải là offset instruction hợp lệ; `end_pc` phải là offset instruction hợp lệ hoặc bằng `code_length`, đồng thời `start_pc < end_pc`. `handler_pc` phải chỉ tới đầu instruction hợp lệ trong `code[]`.

Quan trọng: **`end_pc` là exclusive**. Nếu bảng ghi `from 0 to 3 target 4`, những instruction bắt đầu ở BCI `0, 1, 2` thuộc vùng được bảo vệ; instruction ở BCI `3` không thuộc vùng đó.

### JVM làm gì khi có exception?

1. Khi một instruction ném exception (ví dụ `idiv` chia cho 0) hoặc khi code thực hiện `athrow`, JVM xác định method và instruction nơi exception được ném.
2. JVM xét các entry **theo thứ tự trong `exception_table[]`**, tìm entry có BCI nằm trong vùng bảo vệ và kiểu exception phù hợp.
3. Nếu tìm thấy handler, luồng điều khiển chuyển đến `handler_pc`. **Operand stack của frame được dọn** và chỉ còn exception object ở đỉnh để handler xử lý, thường bắt đầu bằng `astore` đưa exception vào local variable.
4. Nếu không có handler phù hợp ở method hiện tại, exception lan ra caller; quá trình tìm handler tiếp tục trong các frame ngoài, với hiệu ứng unwinding thích hợp. Exception không được xử lý có thể kết thúc thread theo cơ chế xử lý uncaught exception.

Tại một entry có `catch_type != 0`, JVM kiểm tra loại của exception có tương thích với lớp chỉ ra bởi constant pool hay không. Nếu `catch_type == 0`, handler áp dụng cho mọi exception (catch-all). Compiler dùng catch-all trong những cơ chế xử lý tương tự `finally` hoặc code cleanup; đừng suy ra mọi handler catch-all đều được người lập trình viết bằng cú pháp `catch (Throwable ...)`.

### Vì sao thứ tự handler quan trọng?

Các vùng `try` và các kiểu catch có thể chồng lấn hoặc cùng áp dụng; **entry phù hợp đầu tiên** trong table quyết định handler. Java compiler tạo thứ tự phù hợp với các quy tắc source; các công cụ sửa class file không được tùy tiện đảo thứ tự vì có thể thay đổi ngữ nghĩa.

Đừng nhầm **exception table** của `Code` với:

- **`Exceptions` attribute** tại cấp `method_info`: mô tả những **checked exception types được khai báo** trong `throws`, không phải danh sách handler cho `try/catch`.
- **`LineNumberTable`**: ánh xạ BCI sang số dòng source, không xử lý ngoại lệ.
- **`StackMapTable`**: hỗ trợ verifier kiểm tra các loại giá trị trên stack/locals ở những điểm trong control flow; không thay thế exception handler table.
- **Exception stack trace** được in ra runtime: mô tả chuỗi lời gọi khi lỗi xảy ra, không phải bảng `exception_table[]` lưu trong class.

## 7. Ví dụ chạy được Java 17: từ `try/catch` đến `Code`

Tạo file **`CodeAttributeDemo.java`** đúng tên class:

```java
public class CodeAttributeDemo {

    public static int divideOrMinusOne(int dividend, int divisor) {
        try {
            return dividend / divisor;
        } catch (ArithmeticException error) {
            return -1;
        }
    }

    public static long sumLong(long left, long right) {
        return left + right;
    }

    public int increment(int value) {
        return value + 1;
    }

    public static void main(String[] args) {
        System.out.println("normal=" + divideOrMinusOne(12, 3));
        System.out.println("fallback=" + divideOrMinusOne(12, 0));
        System.out.println("sum=" + sumLong(20L, 22L));
        System.out.println("increment=" + new CodeAttributeDemo().increment(4));
    }
}
```

Biên dịch bằng compiler JDK 17+ có hỗ trợ `--release 17` và yêu cầu đầy đủ debug metadata:

```bash
javac --release 17 -g CodeAttributeDemo.java
java CodeAttributeDemo
javap -c -v -p CodeAttributeDemo
```

**Stdout của riêng lệnh `java CodeAttributeDemo`** (không bao gồm output dài của `javap`):

```text
normal=4
fallback=-1
sum=42
increment=5
```

Ở `divideOrMinusOne(12, 3)`, instruction `idiv` thành công. Khi `divisor == 0`, `idiv` ném `ArithmeticException`, handler trả về `-1`. Hai lời gọi thực thi **cùng một Code attribute** nhưng đi theo hai đường control flow khác nhau.

### Đọc `javap -c -v -p` theo từng phần

Trong output của method `divideOrMinusOne(int,int)` do `javac` phiên bản 17 thông thường sinh ra, phần liên quan có dạng:

```text
Code:
  stack=2, locals=3, args_size=2
     0: iload_0
     1: iload_1
     2: idiv
     3: ireturn
     4: astore_2
     5: iconst_m1
     6: ireturn
  Exception table:
     from    to  target type
        0     3     4   Class java/lang/ArithmeticException
```

**Chú ý:** đây là đoạn disassembly tiêu biểu của `javac`, không phải cam kết mọi compiler/version sẽ chọn cùng offsets hoặc bố trí handler. Các phần `LineNumberTable`, `LocalVariableTable`, `StackMapTable` và constant pool có thể xuất hiện xen kẽ hoặc phía dưới trong output `javap -v`.

Giải thích từng trường:

- **`stack=2`:** khi chuẩn bị `idiv`, stack cùng lúc chứa `dividend` và `divisor`, mỗi số `int` chiếm một slot. Trong handler chỉ cần một slot để nhận exception; độ sâu tối đa vẫn là **2**.
- **`locals=3`:** `dividend` ở slot 0, `divisor` ở slot 1, biến `error` trong `catch` ở slot 2. Chương trình có hai tham số nhưng cần ba local slots.
- **`args_size=2`:** `javap` tóm tắt hai tham số được khai báo; đây **không phải** trường độc lập có tên `args_size` trong `Code_attribute`.
- **`0–3`:** handler bảo vệ instruction bắt đầu tại BCI 0, 1, 2; `idiv` ở BCI 2 được bảo vệ. `ireturn` ở BCI 3 nằm **ngoài** khoảng vì `to` exclusive.
- **`target 4`:** khi chia cho 0, JVM chuyển tới `astore_2` để lưu exception object vào slot 2, sau đó đẩy `-1` và trả về qua `ireturn`.
- **`type Class java/lang/ArithmeticException`:** `catch_type` trỏ tới constant pool class entry; chỉ bắt exception phù hợp với loại này.

Đừng hiểu `stack=2` là “method có hai câu lệnh” hoặc `locals=3` là “có ba biến được khai báo trong source”; chúng là những **slot** dùng trong frame.

### Đọc `long` và method instance

Với `sumLong(long,long)`, output tiêu biểu:

```text
Code:
  stack=4, locals=4, args_size=2
     0: lload_0
     1: lload_2
     2: ladd
     3: lreturn
```

Đây là method `static`: `left` chiếm local slots 0 và 1, `right` chiếm 2 và 3. Đặt hai `long` lên operand stack sử dụng **4 slot** trước khi `ladd`. Giá trị `args_size=2` đếm **hai đối số logic**, không có nghĩa chỉ cần hai local slots.

Với `increment(int)` là method **instance**, output tiêu biểu:

```text
Code:
  stack=2, locals=2, args_size=2
     0: iload_1
     1: iconst_1
     2: iadd
     3: ireturn
```

`this` chiếm slot 0 dù body không sử dụng nó; `value` nằm slot 1. `args_size=2` trong cách in của `javap` đếm receiver `this` và một tham số truyền vào method invocation.

### Tự kiểm tra các giả thiết

Nếu bạn dùng một compiler khác và thấy mã lệnh hoặc offset khác, hãy tự kiểm tra giá trị do nó in rồi áp dụng **quy tắc JVMS** tương ứng. Không nên ghi test yêu cầu bytecode giống nguyên văn một ví dụ, trừ khi dự án đang kiểm thử chính công cụ/compiler được cố định phiên bản.

## 8. StackMapTable và các attribute lồng bên trong Code

Sau `exception_table[]`, `Code` còn có bảng `attributes[]` của riêng nó. Một số thuộc tính hay gặp:

| Thuộc tính | Mục đích | Có phải danh sách opcode hoặc handler không? |
| --- | --- | --- |
| `StackMapTable` | Lưu các stack map frames giúp kiểm tra kiểu locals/operand stack tại các điểm trong control flow | Không; phục vụ verification |
| `LineNumberTable` | Ánh xạ bytecode offsets sang số dòng source (hỗ trợ debug/stack trace) | Không |
| `LocalVariableTable` | Tên, descriptor, index và khoảng sống của một số local variables | Không |
| `LocalVariableTypeTable` | Thông tin generic signatures bổ sung cho locals khi có | Không |
| Attribute khác được định nghĩa phù hợp | Metadata khác theo chuẩn và mở rộng được phép | Không được tùy ý thay đổi ngữ nghĩa core của code |

**`StackMapTable` không phải operand stack đang chạy.** Nó mô tả **trạng thái kiểu kỳ vọng** ở một số BCI để verifier xác nhận đường đi control flow hợp lệ. Ví dụ một exception handler cần nhận reference tương thích với `Throwable`, không thể được tùy tiện xem như `int` rồi đưa vào `iadd`.

Ở class-file major 50 trở lên, nếu không xuất hiện `StackMapTable` tường minh thì đặc tả có cơ chế **implicit stack map** tương đương một bảng không chứa entry; điều này không đồng nghĩa JVM bỏ qua verification. Một class file có branch/handler phức tạp phải có thông tin verification hợp lệ theo các quy tắc áp dụng cho phiên bản class file ấy.

Khi bật `javac -g`, `LineNumberTable` và `LocalVariableTable` thường đầy đủ hơn; khi biên dịch không kèm debug info, tên biến có thể không có dù code vẫn chạy. Không dựa vào việc tồn tại `LocalVariableTable` để xác định xem class file có hợp lệ hay không.

### Đọc và sửa class file có thể bị `VerifyError` vì sao?

Nếu tự viết bytecode bằng thư viện hoặc sửa tệp `.class`, bạn cần bảo đảm:

- Các đường control flow dùng operand stack **đúng loại và đúng số slot**; nhánh merge hợp lệ.
- `max_stack` và `max_locals` đủ cho tất cả đường thực thi hợp lệ.
- BCI trong branch, exception-table bounds, target handler, stack map frames đều hợp lệ.
- Các instruction nhận đúng kiểu operand/local; không đọc slot chưa có kiểu hợp lệ.
- `StackMapTable` (nếu cần) khớp với frame state ở các mục tiêu điều khiển.

Lỗi verification thường xảy ra trước khi JVM chạy method tương ứng; nó khác lỗi runtime `ArithmeticException` được ví dụ tạo ra rồi bắt bằng `catch`. Khi debug, đọc **thông báo lỗi và BCI**, dùng `javap -v`, đối chiếu descriptor, locals, exception table và stack map frames.

## 9. Xử lý exception đi qua nhiều method

`exception_table[]` chỉ chứa handler **của method đang xét**. Khi method A gọi B:

```text
A gọi B
  └── B thực thi một instruction ném exception
      ├── B có handler phù hợp trong phạm vi BCI? → chạy handler của B
      └── Nếu không:
          └── exception lan ra A tại call site
              ├── A có handler phù hợp? → chạy handler của A
              └── Không → tiếp tục ra caller cao hơn
```

Vì vậy, một `try/catch` trong A có thể bắt exception phát sinh sâu trong B dù exception table của A chỉ bảo vệ BCI chứa invocation và **không** liệt kê tất cả instruction bên trong B.

Về mặt bytecode, các phần `try-with-resources`, `finally`, synchronized code và synthetic paths do compiler sinh có thể làm exception table nhiều entry hơn source thể hiện. Đừng suy rằng “một `catch` trong source luôn đúng một entry”, hay thấy `catch_type=0` rồi mặc định người viết source cố ý bắt mọi `Throwable`.

Khi exception được chuyển tới handler, operand stack của frame được đặt để chứa exception object. Code của handler thường lưu nó vào local bằng `astore`; nếu exception handler tái ném, bạn có thể thấy `aload` rồi `athrow`. Các lệnh `astore` và `athrow` nằm trong **`code[]`**, còn exception table chỉ mô tả **vùng và điểm chuyển điều khiển**.

## 10. Các giới hạn và nhầm lẫn thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`max_stack` là kích thước JVM thread stack” | Nó chỉ là độ sâu **operand stack** tối đa của **một method frame**; `-Xss` là khái niệm khác |
| “`max_stack` = số biến đang sống” | Nó đếm slot tạm trên operand stack; locals có giới hạn riêng `max_locals` |
| “`max_locals` = số biến đặt tên trong source” | Đếm local **slots**, gồm tham số, `this`, biến compiler tạo/tái dùng; `long/double` cần hai slot |
| “Mọi method có `this` ở slot 0” | Chỉ method instance; method `static` không có receiver slot |
| “Hai tham số `long` nên `locals=2`” | Hai `long` chiếm tổng cộng **4 slot** |
| “`code_length` = số lệnh trong method” | Đó là **số byte**; lệnh JVM dài ngắn khác nhau |
| “`Exception table` giống `throws`” | Exception table là handler trong Code; `Exceptions` attribute mô tả checked exceptions khai báo |
| “`end_pc` vẫn thuộc phạm vi `try`” | `end_pc` là **exclusive**, chỉ bảo vệ BCI trong `[start_pc,end_pc)` |
| “Handler nào cũng có `catch_type` khác 0” | Giá trị **0** là catch-all handler |
| “JVM tìm handler không theo thứ tự” | **Entry phù hợp đầu tiên** trong exception table quyết định xử lý |
| “`StackMapTable` là ảnh chụp operand stack runtime” | Đó là thông tin **verification types** ở các BCI |
| “Bỏ debug info thì JVM mất logic xử lý exception” | Logic nằm trong bytecode và exception table; debug metadata có chức năng khác |
| “`javap` in ra bytecode source Java nguyên văn” | `javap` phân rã `code[]` thành mnemonic; source đã qua compiler transform/optimization |
| “Method abstract có `Code` trống” | Phương thức `abstract`/`native` không có Code attribute |
| “Chỉ cần tăng `max_stack` là bytecode sai sẽ chạy” | Còn cần kiểm tra kiểu, control flow, stack map frames, locals và mọi ràng buộc khác |

## 11. Quy trình kiểm tra `Code` khi debug class file

1. **Chốt JDK và class-file version**: dùng `java -version`, `javac -version`; `javap -v MyClass` in `major version` của file. Java 17 thường là major 61.
2. **Chọn đúng method**: phân biệt tên, descriptor, static/instance, constructor, overload. Một class có nhiều `Code` attributes.
3. **Đọc `stack=..., locals=..., args_size=...`**: tính số slot cần dùng, nhớ `long/double` chiếm hai; receiver ở slot 0 chỉ khi method instance.
4. **Theo dõi `code[]` theo BCI**: ghi input operand stack/local cho mỗi instruction trên những nhánh quan trọng.
5. **Đọc exception table**: đánh dấu mỗi đoạn `[from,to)` và `target` trên disassembly. Tại `target`, stack nhận exception object.
6. **Đọc `StackMapTable` và metadata debug khi cần**: xem verification states, line numbers, local variable lifetimes; tránh coi chúng là source business logic.
7. **Phân biệt lỗi**: `VerifyError`/`ClassFormatError` là vấn đề hợp lệ class file/linking; exception do code chạy như `ArithmeticException` là vấn đề runtime logic.
8. **Đối chiếu với đặc tả đúng edition**: đừng suy ra một thứ là bắt buộc chỉ vì `javac` của một release in đúng chuỗi đó.

## 12. Câu hỏi ôn tập

**Câu 1:** `max_stack=4` trong `sumLong(long,long)` có nghĩa có bốn biến Java?

Không. Có hai đối số `long`, mỗi giá trị chiếm hai slot trên operand stack; khi hai toán hạng cùng hiện diện thì độ sâu là bốn **slot**.

**Câu 2:** Method instance `increment(int value)` có một biến tham số, vậy `max_locals` tối thiểu có phải 1?

Không. Local slot 0 chứa `this`, slot 1 chứa tham số `value`; cần tối thiểu hai slot.

**Câu 3:** Handler `from=0,to=3,target=4` có bắt exception xảy ra tại BCI 3?

Không. BCI 3 nằm ngoài khoảng vì `to` exclusive; nó bắt các instruction có BCI trong `[0,3)`.

**Câu 4:** `catch_type=0` có nghĩa không có exception nào được bắt?

Không. Ngược lại, đó là **catch-all handler**, có thể được compiler dùng cho cleanup/`finally`.

**Câu 5:** Nếu không thấy `LocalVariableTable` thì method không có biến cục bộ?

Không. `max_locals` và instructions load/store đủ để thực thi. `LocalVariableTable` chỉ là metadata tên/kiểu/khoảng sống tùy chọn.

**Câu 6:** Khi `idiv` ném exception, có bắt buộc phải có `athrow` ngay sau `idiv` mới dùng được exception table?

Không. JVM tự ném `ArithmeticException` cho phép chia nguyên cho 0; exception table được xét theo vị trí instruction phát sinh exception.

## Tài liệu gốc để tra cứu

- [JVMS Java SE 17 `4.7.3` — The Code Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.3): cấu trúc và ràng buộc của `max_stack`, `max_locals`, `code_length`, exception table.
- [JVMS Java SE 17 `2.6` — Frames](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6): local variables, operand stack và frame.
- [JVMS Java SE 17 `2.10` — Throwing Exceptions](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.10): exception propagation và handler.
- [JVMS Java SE 17 `4.7.4 — StackMapTable](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.4) và [`4.10 — Verification](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.10): kiểm tra kiểu/flow của class file.
- [JVMS Java SE 17 `6.5` — Instructions](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5): `iload`, `idiv`, `astore`, `ladd`, `athrow` và các opcode khác.
- [Java SE 17 `javap` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): sử dụng `-c`, `-v`, `-p` để xem bytecode và các thuộc tính class file.

**Điểm ghi nhớ:** `Code_attribute` là bản mô tả **một method** gồm giới hạn frame (`max_stack`, `max_locals`), bytecode (`code[]`), handler (`exception_table[]`) và metadata lồng bên trong. Đọc đúng số **slot**, đúng **BCI**, đúng phạm vi **[start_pc,end_pc)** và đúng thứ tự handler sẽ nối được source Java, JVM execution và lỗi verification một cách có căn cứ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-file-format-007">StackMapTable và thông tin kiểu phục vụ xác minh bytecode</a>

<details>
<summary>Xem chi tiết</summary>

# StackMapTable và thông tin kiểu phục vụ xác minh bytecode

## 1. Bài toán: JVM kiểm tra một phương thức bytecode bằng cách nào?

Mã nguồn Java được trình biên dịch kiểm tra kiểu, nhưng tệp `.class` có thể đến từ nhiều nguồn: `javac`, Kotlin/Scala, công cụ sinh mã, trình biến đổi bytecode, hoặc tệp được tải từ một nguồn không đáng tin cậy. JVM **không thể chỉ dựa vào lời hứa rằng tệp đã được biên dịch đúng**. Trước khi chạy, nó phải áp dụng những quy tắc về cấu trúc và kiểu của class file, giúp phát hiện bytecode không hợp lệ.

Xem một tình huống giả định:

```text
Vị trí A: operand stack có int
       |
       | nhảy đến B
       v
Vị trí B: lệnh invokevirtual của một phương thức instance
```

`invokevirtual` cần một **tham chiếu đối tượng** phù hợp, không thể lấy một `int` làm receiver. Dù bytecode được mã hóa theo cú pháp đúng, luồng kiểu như vậy phải bị từ chối khi xác minh.

Với lệnh rẽ nhánh, tình huống còn khó hơn: **nhiều đường chạy có thể hội tụ ở cùng một bytecode offset**. JVM cần biết những đường đó mang cùng một trạng thái kiểu hợp lệ hay không. `StackMapTable` cung cấp các **mốc trạng thái kiểu đã được mã hóa trong class file** để việc xác minh có thể kiểm tra các bước chuyển của bytecode hiệu quả và có cơ sở rõ ràng.

```text
Mã nguồn Java
   |
   v
javac / compiler / bytecode generator
   |
   v
.class
  ├── constant pool
  ├── method_info
  └── Code
       ├── max_stack, max_locals
       ├── code[] (bytecode)
       ├── exception_table
       └── attributes[]
            └── StackMapTable (nếu có)
                  ├── frame tại điểm điều khiển 1
                  ├── frame tại điểm điều khiển 2
                  └── ...
   |
   v
JVM: nạp lớp → xác minh cấu trúc và kiểu → thực thi khi hợp lệ
```

**Ý chính:** `StackMapTable` không chứa mã nguồn, không thay thế bytecode, và không “chạy” thay JVM. Nó mô tả **kiểu xác minh được kỳ vọng** của **các biến cục bộ** (*local variables*) và **ngăn xếp toán hạng** (*operand stack*) ở những vị trí quan trọng của một phương thức.

## 2. Phân biệt ba lớp thông tin

Để đọc `StackMapTable` không nhầm, cần tách ba khái niệm:

| Khái niệm | Nội dung | Dùng để làm gì |
| --- | --- | --- |
| **Kiểu trong ngôn ngữ Java** | `boolean`, `byte`, `char`, `int`, `String`, generic type… | Compiler kiểm tra tính hợp lệ của mã nguồn |
| **Kiểu của giá trị và câu lệnh JVM** | `int`, `long`, `float`, `double`, `reference`, category 1/2; opcode như `iload`, `aload` | JVM thực thi và tuân theo hợp đồng của opcode |
| **Verification type** | Các kiểu trừu tượng dùng để kiểm tra an toàn của luồng bytecode: `Integer`, `Object`, `Null`, `Uninitialized`… | Bytecode verifier kiểm tra các trạng thái kiểu và phép chuyển giữa chúng |

Ví dụ, ở mức bytecode, `boolean`, `byte`, `char` và `short` được thao tác thông qua các kiểu và lệnh liên quan `int` trong nhiều ngữ cảnh; `StackMapTable` không có một tag riêng cho từng primitive nhỏ đó. Một `String` reference trong frame được mô tả bằng `Object_variable_info` trỏ đến class thích hợp trong constant pool; còn một null reference có tag xác minh riêng.

**Verification type không phải đối tượng kiểu Java có thể khai báo trong code.** `UninitializedThis` là một trạng thái của bộ xác minh trong constructor; nó không phải một kiểu khai báo của ngôn ngữ Java. Tương tự, `Top` là một kiểu xác minh đặc biệt, không phải class `java.lang.Object` và không phải nguyên lý “mọi giá trị đều phù hợp”.

## 3. Stack map frame là gì?

Một **stack map frame** mô tả trạng thái kiểu tại **một offset của bytecode** bên trong **một phương thức**. Trạng thái gồm:

- **Local variables:** kiểu của các slot biến cục bộ trong frame thực thi phương thức.
- **Operand stack:** kiểu của những giá trị đang nằm trên ngăn xếp toán hạng tại offset đó.

Ví dụ trừu tượng:

```text
Phương thức: static String pick(boolean flag)

Tại vị trí đầu (initial frame):
  locals       = [Integer]       // flag: boolean được biểu diễn kiểu verifier Integer
  operandStack = []

Sau khi đi qua hai nhánh và hội tụ:
  locals       = [Integer, Object(java/lang/String)]
  operandStack = []              // trước khi nạp value để return

Lệnh aload_1:
  locals       = [Integer, Object(java/lang/String)]
  operandStack = [Object(java/lang/String)]

Lệnh areturn:
  lấy reference tương thích từ operand stack để trả về
```

Sơ đồ này chỉ minh họa **kiểu kỳ vọng**, không phải dump bytecode chính xác của mọi compiler. Compiler có thể chọn cách bố trí slot, nhánh và frame khác nhưng phải tạo class file hợp lệ.

### 3.1. Vì sao cần nhìn cả locals lẫn operand stack?

JVM là một **máy ngăn xếp** (*stack-based virtual machine*). Lệnh `iadd` lấy hai giá trị tương thích kiểu `int` từ operand stack; `aload_1` lấy một giá trị reference từ local slot 1 và đẩy lên operand stack. `invokestatic`, `invokevirtual`, `ireturn`, `areturn` đều đặt yêu cầu về số lượng và kiểu của các giá trị này.

Một trạng thái có thể hợp lệ với lệnh `ireturn` nhưng không hợp lệ với `areturn`. Kiểm tra từng opcode cần theo dõi loại giá trị trước và sau khi thực thi trừu tượng (*type transition*).

### 3.2. Không phải mỗi instruction đều có một stack map frame được lưu

`StackMapTable` thường cung cấp frame ở **đầu các basic block**, nhất là **đích của lệnh nhảy** và **đầu exception handler**, theo các ràng buộc xác minh cụ thể. Các lệnh nằm giữa những điểm này được verifier kiểm tra bằng cách áp dụng quy tắc chuyển trạng thái kiểu của từng opcode.

Vì vậy:

- `max_stack` là giới hạn độ sâu operand stack, **không** phải danh sách trạng thái kiểu.
- `max_locals` là giới hạn slot biến cục bộ, **không** phải nội dung `StackMapTable`.
- Một frame có thể có operand stack rỗng.
- Một phương thức có thể không có entry `StackMapTable` tường minh, nếu trạng thái kiểu mặc định và luồng bytecode đủ để xác minh theo quy tắc class file tương ứng.

## 4. Vị trí và cấu trúc của `StackMapTable` trong tệp `.class`

Theo **JVMS `4.7.4`**, `StackMapTable` là một **attribute có độ dài biến thiên** nằm **bên trong bảng attributes của `Code` attribute** của một phương thức có mã bytecode. Trong một `Code` attribute có tối đa **một** `StackMapTable`.

Dạng nhị phân trừu tượng:

```text
StackMapTable_attribute {
    u2 attribute_name_index;
    u4 attribute_length;
    u2 number_of_entries;
    stack_map_frame entries[number_of_entries];
}
```

Ý nghĩa:

| Trường | Ý nghĩa |
| --- | --- |
| `attribute_name_index` | Chỉ số tới `CONSTANT_Utf8_info` chứa chuỗi `StackMapTable` trong constant pool |
| `attribute_length` | Độ dài phần thân attribute, không bao gồm 6 byte đầu của attribute header |
| `number_of_entries` | Số frame **tường minh** trong `entries` |
| `entries[]` | Các frame có thứ tự theo vị trí bytecode; có thể được mã hóa dạng delta và kế thừa thông tin từ frame trước |

Một số nhầm lẫn quan trọng:

1. **Không đặt `StackMapTable` ngang cấp toàn bộ class một cách tùy tiện.** Nó là attribute của một `Code` attribute cụ thể, tức gắn với việc xác minh **một phương thức**.
2. **Không phải frame đầu tiên của phương thức được ghi trong `entries[0]`.** Trạng thái đầu tiên là một **initial frame ngầm định**, tính từ mô tả phương thức và các quy tắc liên quan; `entries[0]` biểu diễn frame tường minh tiếp theo.
3. **Thiếu attribute không nhất thiết nghĩa là class hỏng.** Với class file phiên bản **50.0 trở lên**, nếu `Code` không có `StackMapTable` thì theo đặc tả tồn tại một **stack map attribute ngầm định có `number_of_entries = 0`**. Class chỉ qua xác minh nếu luồng mã của phương thức phù hợp với những gì được phép trong trường hợp này.
4. **Frame không nói về giá trị cụ thể.** Ví dụ ghi `Integer` có nghĩa slot phải chứa giá trị kiểu verifier Integer; nó không ghi số 10 hay 20 đang lưu tại runtime.

### 4.1. Kiểu xác minh và tag trong `verification_type_info`

Mỗi slot được mô tả bằng một cấu trúc `verification_type_info`. Các tag theo JVMS:

| Tag | Tên cấu trúc | Vai trò |
| ---: | --- | --- |
| 0 | `Top_variable_info` | Trạng thái `Top` dùng trong kiểm tra slot và kiểu trừu tượng; không là một đối tượng Java |
| 1 | `Integer_variable_info` | Giá trị `int` theo mô hình xác minh (bao gồm các giá trị primitive nhỏ khi được biểu diễn tương ứng) |
| 2 | `Float_variable_info` | Giá trị `float` |
| 3 | `Double_variable_info` | Giá trị `double` |
| 4 | `Long_variable_info` | Giá trị `long` |
| 5 | `Null_variable_info` | Null reference |
| 6 | `UninitializedThis_variable_info` | `this` chưa khởi tạo hoàn chỉnh trong constructor |
| 7 | `Object_variable_info` | Reference có kiểu class, interface hoặc array được chỉ định qua constant pool |
| 8 | `Uninitialized_variable_info` | Tham chiếu tới object vừa được tạo bằng `new` nhưng chưa gọi xong constructor tương ứng; gắn với offset lệnh `new` |

**Tag 7:** cấu trúc `Object_variable_info` chứa `cpool_index` tới `CONSTANT_Class_info` hợp lệ. Đối tượng có thể là class, interface hoặc array type tùy thông tin tương ứng; không hiểu chữ “Object” ở tên cấu trúc là lúc nào cũng `java/lang/Object`.

**Tag 8:** cấu trúc `Uninitialized_variable_info` chứa `offset` tham chiếu đến một **instruction `new`** có liên quan. Điều này cho phép verifier phân biệt các object chưa khởi tạo ở những lệnh `new` khác nhau.

**`long` và `double`:** mỗi giá trị chiếm **hai đơn vị slot** trong mô hình local variables/operand stack; bảng frame mã hóa theo quy tắc riêng để khớp slot. Không suy luận `number_of_locals` luôn bằng đúng số slot của `max_locals` vì các entry biểu diễn `long` hoặc `double` có thể chiếm hai slot.

### 4.2. Bảy dạng `stack_map_frame`

Vì trạng thái kiểu giữa các basic block thường gần giống nhau, class file có các dạng nén:

| Kiểu frame | Mã `frame_type` | Nội dung |
| --- | --- | --- |
| `same_frame` | 0–63 | Locals như frame trước; operand stack rỗng; offset delta nằm trong tag |
| `same_locals_1_stack_item_frame` | 64–127 | Locals giữ nguyên; operand stack có một item; delta nằm trong tag |
| `same_locals_1_stack_item_frame_extended` | 247 | Giống dạng có 1 stack item nhưng `offset_delta` được ghi bằng `u2` |
| `chop_frame` | 248–250 | Operand stack rỗng; bỏ 1–3 mục locals ở cuối so với frame trước |
| `same_frame_extended` | 251 | Locals giữ nguyên, stack rỗng; delta viết riêng bằng `u2` |
| `append_frame` | 252–254 | Operand stack rỗng; thêm 1–3 mục locals so với frame trước |
| `full_frame` | 255 | Mã hóa tường minh tập locals và operand stack cho frame mới |

Dải tag **128–246** được dành riêng trong định dạng, không phải một kiểu frame người đọc có thể dùng tùy ý.

**Nén frame không làm mất thông tin cần thiết cho verifier.** Ví dụ nếu locals không thay đổi và stack rỗng, `same_frame` chỉ cần ghi tag; muốn hiểu đầy đủ thì tái tạo từ trạng thái frame trước.

### 4.3. `offset_delta` là khoảng cách giữa frame, không phải offset tuyệt đối trong mọi trường hợp

Các frame tường minh được mã hóa theo **thứ tự tăng của offset bytecode**. Công thức xác định vị trí:

- **Frame tường minh đầu tiên:** `offset = offset_delta` (so với initial frame ngầm định).
- **Các frame tường minh tiếp theo:** `offset = previous_offset + offset_delta + 1`.

Ví dụ mô hình:

```text
Initial frame : offset = 0 (ngầm định)
Explicit #1   : offset_delta = 8   → offset 8
Explicit #2   : offset_delta = 4   → offset 8 + 4 + 1 = 13
Explicit #3   : offset_delta = 5   → offset 13 + 5 + 1 = 19
```

Các offset trên là **ví dụ toán học giả định**, không phải output compiler. Phải đảm bảo frame áp dụng tại offset hợp lệ của **instruction** và đáp ứng các quy tắc target/control flow. Cộng `+1` ở những frame sau giúp phân biệt offset và ngăn tạo frame tường minh trùng vị trí do nhầm delta.

## 5. JVM tạo trạng thái đầu tiên (initial frame) như thế nào?

Verifier cần một điểm xuất phát trước khi xét các frame tường minh. **Initial frame** được suy ra từ **method descriptor**, việc phương thức là `static` hay instance, tên `<init>` trong constructor và những quy tắc có liên quan.

Ví dụ:

```java
static String describe(int id, Object value) {
    return id + ":" + value;
}
```

Đây là **đoạn minh họa phương thức** cần đặt trong một lớp, không phải chương trình độc lập. Với phương thức `static`, các tham số ở đầu locals theo thứ tự:

```text
locals[0] = Integer
locals[1] = Object(java/lang/Object)
stack     = []
```

Trong phương thức **instance thông thường**, local slot 0 chứa receiver `this`, các tham số bắt đầu sau đó. Với phương thức `static`, **không có `this` ở slot 0**.

Trong **constructor `<init>`** của một lớp, receiver có trạng thái `UninitializedThis` trước khi việc khởi tạo hợp lệ hoàn thành. Đó là lý do Java không thể tùy tiện dùng một `this` chưa khởi tạo như một đối tượng hoàn chỉnh trong mọi tình huống.

### 5.1. `long`/`double` và slot đôi

```java
static void consume(long id, double ratio, Object object) {
    System.out.println(object);
}
```

Ví dụ khái niệm về local slot index:

```text
slot 0–1 : long id
slot 2–3 : double ratio
slot 4   : Object object
```

Đừng lấy số **giá trị khai báo** làm số **slot**. Ngay cả khi frame dùng một entry cho `Long` hoặc `Double`, thông tin vị trí local tiếp theo vẫn phải tính theo quy tắc hai slot.

### 5.2. Kiểu sau rẽ nhánh và merge

Giả sử hai nhánh cùng ghi một giá trị `String` vào local 1:

```java
static String pick(boolean flag) {
    String value;
    if (flag) {
        value = "left";
    } else {
        value = "right";
    }
    return value;
}
```

Nếu compiler tạo các nhánh và một điểm hội tụ, verifier cần thấy rằng ở các đường đến điểm này, local chứa kiểu tham chiếu tương thích `java/lang/String`. Nếu một nhánh mang `int` còn nhánh kia yêu cầu tham chiếu `String` rồi dùng `aload` tại điểm hội tụ, class file có thể **không qua xác minh**.

Khi gặp các kiểu tham chiếu khác nhau mà mã hợp lệ, trạng thái kiểu được phép tại điểm hội tụ phải đáp ứng quan hệ gán được chấp nhận bởi quy tắc verifier. Có thể xuất hiện một kiểu chung thích hợp, nhưng không nên dùng trực giác “mọi thứ đổi thành `Object`” để bỏ qua các quy tắc thực tế về interface, mảng, khả năng khởi tạo và opcode.

## 6. Verifier sử dụng `StackMapTable` như thế nào?

### 6.1. Kiểm tra luồng điều khiển và chuyển trạng thái kiểu

Với mô hình **verification by type checking** (JVMS `4.10.1`):

1. JVM có cấu trúc phương thức: code array, giới hạn locals/stack, exception table, method descriptor.
2. Verifier xác định **expected type state** tại những vị trí được mô tả bởi stack map frame, bao gồm initial frame.
3. Ở mỗi instruction, nó áp dụng quy tắc của opcode đối với locals và operand stack: đọc kiểu nào, tạo kiểu nào, pop/push bao nhiêu phần tử.
4. Khi một instruction chuyển điều khiển sang offset khác (ví dụ `if`, `goto`, exception handler), trạng thái được suy ra ở đường chuyển phải tương thích với trạng thái mà frame đích yêu cầu.
5. Những thao tác không thỏa quy tắc kiểu, stack height, tính hợp lệ của target hay ràng buộc đối tượng chưa khởi tạo dẫn tới **verification failure**.

Không coi đây là lời khẳng định JVM “chạy thử” toàn bộ chương trình với dữ liệu thật. Verifier hoạt động trên **kiểu trừu tượng**, không thực thi nghiệp vụ, không biết các giá trị người dùng nhập và không chứng minh chương trình không ném exception hợp lệ khi chạy.

### 6.2. Rẽ nhánh: điểm hội tụ cần một hợp đồng kiểu

Sơ đồ giả định:

```text
                  [đầu phương thức]
                         |
                  iload_0, ifeq
                    /          \
                nhánh A       nhánh B
                  |              |
             astore_1       astore_1
                    \          /
                     \        /
                    [join @ offset J]
                    locals[1] = String
                    operandStack = []
                           |
                        aload_1
                           |
                        areturn
```

Frame ở `J` cho biết **kiểu được kỳ vọng khi mọi đường hợp lệ đi tới**. Bytecode gây ra stack height khác nhau ở hai nhánh hoặc kiểu locals không phù hợp có thể bị từ chối.

**Không có StackMapTable “giải quyết giúp” bytecode sai.** Nếu dòng nhảy dẫn vào giữa một instruction, các stack không khớp, hoặc một giá trị primitive bị dùng như object reference, việc thêm frame tùy tiện không làm chương trình thành hợp lệ.

### 6.3. Exception handler cũng là đích luồng điều khiển

Giả sử bytecode có một `try/catch`. Khi exception được chuyển đến handler, operand stack lúc bắt đầu handler phải chứa **tham chiếu ngoại lệ** tương ứng theo hợp đồng; không phải giữ nguyên toàn bộ operand stack của instruction đang lỗi.

Ví dụ Java:

```java
static int safeLength(String value) {
    try {
        return value.length();
    } catch (NullPointerException ex) {
        return -1;
    }
}
```

Exception handler cần trạng thái kiểu phù hợp với nhánh exception. Đặc tả yêu cầu xác minh đúng entry state của handler; compiler sinh `StackMapTable` tương ứng khi target class format yêu cầu.

### 6.4. `new`, `Uninitialized` và gọi constructor

Bytecode tạo một object thường theo chuỗi khái niệm:

```text
new SomeClass
dup
invokespecial SomeClass.<init>:()V
... dùng tham chiếu đã khởi tạo ...
```

Sau `new` nhưng trước lời gọi constructor hợp lệ, reference ở trạng thái **uninitialized**. Việc gán/gọi phương thức hoặc để nó thoát khỏi nơi cho phép bị giới hạn chặt; verifier cần biết **lệnh `new` nào** sinh ra giá trị đó.

Hai trạng thái đáng phân biệt:

- **`UninitializedThis`:** receiver đặc biệt của `<init>` trước khi khởi tạo theo hợp đồng.
- **`Uninitialized(offset)`:** tham chiếu tới object mới tạo tại offset `new` cụ thể.

Mã bytecode do compiler tạo phải bảo đảm transition từ uninitialized sang initialized theo đúng quy tắc constructor. Điều này liên quan đến **an toàn về cấu trúc đối tượng**, khác với việc kiểm tra biến Java có bằng `null` hay không.

## 7. Thí nghiệm chạy được với Java 17+: xem `StackMapTable` bằng `javap -v`

Tạo tệp **`FrameDemo.java`** ở một thư mục riêng:

```java
public class FrameDemo {
    static String pick(boolean flag) {
        String selected;
        if (flag) {
            selected = "left";
        } else {
            selected = "right";
        }
        return selected;
    }

    static int safeLength(String text) {
        try {
            return text.length();
        } catch (NullPointerException exception) {
            return -1;
        }
    }

    static String create() {
        return new String("created");
    }

    public static void main(String[] args) {
        System.out.println(pick(true));
        System.out.println(pick(false));
        System.out.println(safeLength("abc"));
        System.out.println(safeLength(null));
        System.out.println(create());
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 FrameDemo.java
java FrameDemo
```

**Stdout chính xác**:

```text
left
right
3
-1
created
```

Giải thích đầu ra:

- Hai lời gọi `pick` đi qua hai nhánh khác nhau nhưng trả cùng **kiểu `String`**.
- `safeLength("abc")` trả độ dài 3; `safeLength(null)` kích hoạt `NullPointerException`, được handler chuyển thành `-1`.
- `create()` tạo một object `String` mới; bytecode của nó thể hiện quá trình cấp phát và gọi constructor.

Để quan sát phần metadata class file:

```bash
javap -v -p FrameDemo
```

`-v` in thông tin chi tiết, trong đó có `Code` attribute và `StackMapTable` của các phương thức phù hợp; `-p` cho phép hiển thị cả thành viên không public. Tìm các dòng như:

```text
Code:
  stack=..., locals=..., args_size=...
  ...
  StackMapTable: number_of_entries = ...
    frame_type = ... /* ... */
```

**Đây là sơ đồ minh họa định dạng hiển thị, không phải stdout nguyên văn của `javap`**. Các dấu `...` đại diện cho dữ liệu phụ thuộc compiler và phương thức. Hãy dựa vào output thực tế trên máy để xem `frame_type` và offset đúng của build đang dùng.

### 7.1. Phân tích `pick`

Có thể nhìn thấy các instruction thuộc nhóm:

- `iload_0` để đọc giá trị cờ (boolean được xử lý theo dạng integer ở bytecode).
- `ifeq` hoặc một dạng nhánh phù hợp.
- `ldc` để nạp hằng chuỗi.
- `astore_1` để lưu reference vào local.
- `goto` để đi qua nhánh không được chọn.
- `aload_1` và `areturn` để trả reference.

Compiler có thể sắp xếp nhánh hoặc thực hiện tối ưu khác. Điều cần tìm là **điểm đích nhảy** và **frame mô tả trạng thái ở điểm đó**. Nếu các nhánh cập nhật `selected`, frame ở vị trí hội tụ phải tương thích với các đường đi.

Ví dụ trạng thái kiểu **sơ đồ, không cố định theo offset**:

```text
locals = [Integer(flag), Object(java/lang/String)]
stack  = []
```

Ở trạng thái này, `aload_1` được phép lấy reference tại local 1 để return. Cách mã hóa thật có thể dùng `append_frame`, `same_frame`, `full_frame` hay dạng phù hợp khác.

### 7.2. Phân tích `safeLength`

Trong output `javap -v`, xem cả:

- **Exception table:** vùng bytecode được bảo vệ và đích handler.
- **StackMapTable:** trạng thái tại handler, thường bao gồm một stack item kiểu ngoại lệ phù hợp.
- **Bytecode:** lệnh `invokevirtual` để gọi `String.length()`, rồi các lệnh chuyển sang nhánh return `-1`.

Ở đầu handler, operand stack được khôi phục theo cơ chế exception dispatch, có exception reference để mã handler xử lý. Vì vậy frame handler không cần mang toàn bộ nội dung stack của phép gọi `length()` trước đó.

### 7.3. Phân tích `create`

Với `new String("created")`, compiler thông thường sinh chuỗi opcode thể hiện `new`, `dup`, nạp tham số constructor và `invokespecial`. Kiểm tra `new` và `invokespecial` sẽ giúp liên hệ tag `Uninitialized` với quy tắc xác minh constructor.

**Không nhất thiết `create` phải có StackMapTable tường minh nhiều entries.** Vì phương thức có luồng điều khiển thẳng, initial frame ngầm định có thể đủ; không thấy dòng `StackMapTable` ở phương thức này **không phải** bằng chứng JVM bỏ qua verification.

### 7.4. Thử thay đổi cờ debug mà không thay đổi bản chất verification

`javac -g FrameDemo.java` bổ sung thông tin debug như line/local variable tables khi phù hợp. `javac -g:none FrameDemo.java` giảm các bảng debug tùy chọn. `StackMapTable` là **metadata phục vụ verifier**, không đơn thuần là bảng ánh xạ dòng mã nguồn như `LineNumberTable`.

Lệnh `javap -c -p FrameDemo` đủ để đọc instruction; dùng `-v` khi muốn xem các attribute và frame. Không lẫn **`LocalVariableTable`** (tên và phạm vi biến phục vụ debug) với **`StackMapTable`** (verification types theo bytecode offsets). Hai bảng phục vụ mục đích khác nhau.

## 8. Tại sao phải có frame ở những điểm điều khiển quan trọng?

Bất kỳ công cụ nào đọc bytecode cũng có thể xây dựng **control-flow graph**: các node là basic block, các cạnh là nhánh điều khiển hoặc exception. Nếu không có trạng thái kiểu ràng buộc ở mỗi điểm cần thiết, verifier có thể phải lặp suy luận trạng thái đi qua các cạnh cho tới khi đạt kết quả ổn định.

Stack map frames giúp bản class file cung cấp những **trạng thái được công bố** từ trước. Verifier vẫn cần kiểm tra chúng **đúng** và **tương thích với opcode/luồng**; không tin mù quáng. Đó là điểm khác biệt giữa:

- **Thông tin được khai báo trong class file:** trạng thái kiểu kỳ vọng tại offset.
- **Kết quả xác minh của JVM:** khẳng định rằng các lệnh và mọi cạnh luồng điều khiển thỏa các quy tắc bắt buộc.

Về mặt vận hành, cơ chế này hỗ trợ xác minh bằng kiểm tra kiểu, đặc biệt hữu ích cho các công cụ sinh/biến đổi class file đúng chuẩn. Nhưng **mục đích an toàn** mới là trọng tâm; `StackMapTable` không được coi là “một bộ tối ưu tốc độ ứng dụng” cho JIT và không trực tiếp bảo đảm mã chạy nhanh hơn.

### 8.1. Basic block không đồng nghĩa với mỗi dòng Java

Một dòng Java có thể tạo nhiều instruction bytecode; ngược lại, compiler có thể tối ưu và tái cấu trúc dòng nguồn. `StackMapTable` dùng **bytecode offsets**, không dùng số dòng nguồn. Vì vậy khi `javap` in:

```text
17: goto          26
20: ...
26: aload_1
```

Các số 17, 20, 26 trong ví dụ này chỉ là **offset minh họa**. Frame áp dụng cho offset theo code array thực tế, không nhất thiết trùng số dòng của `FrameDemo.java`.

### 8.2. Vì sao exception handler phải được kiểm tra?

Một exception có thể xuất hiện tại nhiều instruction trong vùng `try`. Khi JVM chuyển sang handler, stack đầu vào có dạng đặc biệt chứa exception reference. Nếu handler mong đợi sai kiểu hoặc cấu trúc stack, bytecode đó không hợp lệ. Frame tại handler là một điểm kiểm soát kiểu rõ ràng.

### 8.3. Vì sao không thể suy luận an toàn từ một đường chạy thử?

Chương trình có thể chạy ổn với đầu vào `true` nhưng đi vào nhánh khác với `false`. Bytecode verifier không chỉ quan sát một input mẫu; nó kiểm tra những ràng buộc cấu trúc/kiểu cần thỏa trên các đường điều khiển được phép. Tuy nhiên, nó không chứng minh lỗi logic, race condition hoặc ngoại lệ hợp lệ là không tồn tại.

## 9. Phiên bản class file và quy tắc verification

Phạm vi quan trọng theo **JVMS `4.10.1`**:

- Class file phiên bản **50.0 trở lên** (bắt đầu từ Java 6) được xác minh theo cơ chế **type checking** như đặc tả mô tả.
- Có một quy định tương thích đặc biệt: **chỉ với version 50.0**, khi type checking thất bại, một bản triển khai JVM có thể lựa chọn fallback sang **verification by type inference** theo quy tắc đặc tả. Đây không phải hành vi mà ứng dụng được quyền yêu cầu cho mọi phiên bản.
- Với class file thuộc định dạng cũ hơn, việc xác minh có quy định cho cơ chế **type inference**, trong những JVM thuộc phạm vi áp dụng tương ứng.
- Đối với **class file version mới hơn** (ví dụ class Java 17 biên dịch mặc định, major 61), không nên dựa vào fallback riêng của version 50.0 để chạy class thiếu frame bắt buộc ở các đích rẽ nhánh.

Ví dụ trong `javap -v` sau khi chạy:

```bash
javac --release 17 FrameDemo.java
javap -v FrameDemo
```

Phần đầu mô tả class file có **major version: 61** đối với target Java 17. Đây là **phiên bản định dạng class file**, không phải số frame trong phương thức. Phần `StackMapTable` nằm trong từng `Code` attribute phù hợp.

**Không suy diễn “không có `StackMapTable` là luôn sai”.** Nếu phương thức đủ đơn giản và initial frame ngầm định đáp ứng mọi yêu cầu, số entry tường minh có thể bằng 0. Ngược lại, với nhánh điều khiển/handler cần frame, thiếu hoặc sai frame có thể khiến verifier từ chối class.

## 10. `VerifyError` xảy ra khi nào?

`java.lang.VerifyError` là một loại `LinkageError`. Nó có thể được ném khi JVM cố xác minh và phát hiện class file có bytecode vi phạm ràng buộc cần thiết. Thời điểm quan sát lỗi có thể là khi nạp/liên kết/sử dụng lớp tùy hoạt động của runtime và cách tổ chức kiểm tra.

Một số dạng thông báo thường gặp từ triển khai JVM:

```text
java.lang.VerifyError: Expecting a stackmap frame at branch target ...
java.lang.VerifyError: Bad type on operand stack
java.lang.VerifyError: Inconsistent stackmap frames at branch target ...
```

**Các dòng này là mẫu thông báo minh họa**, không phải stdout tất yếu của `FrameDemo`, cũng không phải văn bản ổn định bắt buộc trong mọi JVM.

### 10.1. Những nguyên nhân thực tế

1. **Chỉnh opcode nhưng giữ frame cũ.** Chèn một instruction làm bytecode offsets đổi; frame trước đó còn trỏ vị trí không đúng.
2. **Thay cấu trúc nhánh nhưng không cập nhật trạng thái.** Thêm nhánh mới dẫn vào điểm hội tụ mà stack hoặc locals không tương thích.
3. **Thay kiểu của giá trị trên stack.** Một instruction dùng opcode reference trong khi đường đi cung cấp primitive.
4. **Sinh mã constructor sai.** Dùng reference `Uninitialized` trước bước khởi tạo hợp lệ.
5. **Cập nhật exception table nhưng quên handler frame.** Handler mới cần trạng thái kiểu phù hợp.
6. **Sinh class file không phù hợp major version.** Công cụ tạo bytecode dùng luật frame lỗi thời hoặc đặt metadata không hợp lệ.
7. **Class instrumented sai.** Agent/transformer thay đổi code hoặc control flow, nhưng không tái tính các stack map frame và giới hạn locals/stack khi cần.

**Không phải mọi lỗi class file là `VerifyError`.** Sai cấu trúc class file nghiêm trọng có thể gây `ClassFormatError`; class biên dịch cho runtime quá cũ có thể gây `UnsupportedClassVersionError`; lỗi tìm class có thể là `ClassNotFoundException` hoặc `NoClassDefFoundError` tùy ngữ cảnh. Cần đọc **loại lỗi và thông điệp thật** trước khi sửa.

### 10.2. Quy trình debug an toàn

1. Xác định **class chính xác** bị từ chối (đừng vội sửa source khác chỉ vì stack trace có nhiều lớp).
2. Kiểm tra runtime và target bytecode: `java -version`, compiler target, `javap -v` để xem major version.
3. Nếu class bị transformer/agent chỉnh sửa, so sánh **class gốc** và **class đã biến đổi** trong môi trường phát triển; xác định các instruction/branch/handler mới.
4. Dùng `javap -v -p <ClassName>` hoặc công cụ kiểm tra class file tương thích để đối chiếu bytecode offsets, `StackMapTable` và exception table.
5. Tại offset lỗi, mô phỏng **kiểu** mà instruction trước đó tạo ra; so với trạng thái mà frame đích công bố.
6. Sửa logic biến đổi để cập nhật **control-flow targets, frame states, max stack, max locals** theo API thư viện sinh bytecode đang dùng.
7. Build và chạy lại bằng **JDK mục tiêu** để JVM xác minh output cuối.

Tránh chữa triệu chứng bằng cách tìm cách **tắt bytecode verification** trong môi trường thật. Điều đó che giấu lỗi class file và có thể không khả dụng trên các runtime mới. Mục tiêu đúng là tạo bytecode cùng metadata hợp lệ.

## 11. StackMapTable khi chỉnh bytecode bằng ASM, Byte Buddy hoặc agent

Bài này không hướng dẫn triển khai một transformer cụ thể, nhưng cần hiểu hậu quả của việc thay mã:

```text
Original method:
  Code instructions
  Branch targets
  Exception handlers
  StackMapTable states

Transformer:
  insert logging / guards / branches / try-catch
           |
           v
New control-flow graph
           |
           v
Recompute or carefully update:
  instruction offsets
  branch and handler targets
  frame states
  max_stack / max_locals
           |
           v
Valid transformed .class
```

Một phép thay đổi **không đổi luồng điều khiển** vẫn có thể thay đổi trạng thái stack/local ở những điểm cụ thể. Khi transformer thay đổi bytecode, đừng cho rằng “chỉ thêm một println” luôn giữ nguyên StackMapTable hợp lệ.

Các thư viện như **ASM** cung cấp cơ chế tính toán frame/maxs trong những cấu hình thích hợp (ví dụ hướng tiếp cận với `ClassWriter.COMPUTE_FRAMES` trong ASM). Tuy vậy, việc tính toán này cần biết các **kiểu lớp chung**, hierarchy và classpath phù hợp. Nếu resolver không tìm được lớp cần thiết hoặc class loader khác với runtime đích, quá trình tính frame có thể thất bại hoặc không diễn đạt đúng quan hệ kiểu cần thiết.

Điểm quan trọng hơn tên API: **output cuối phải được JVM verifier chấp nhận**. Khi nâng cấp JDK, thay target class version, tạo branch mới hoặc đổi cấu trúc constructor, nên kiểm chứng lại class đã sinh.

### Phân biệt metadata verifier với metadata debug, GC và JIT

| Dữ liệu | Mục đích |
| --- | --- |
| `StackMapTable` trong class file | Khai báo trạng thái **verification type** tại bytecode offsets của phương thức |
| `LineNumberTable` | Ánh xạ bytecode offsets tới số dòng nguồn để debug/stack trace |
| `LocalVariableTable` | Tên, phạm vi và descriptor của biến cục bộ phục vụ debug |
| `max_stack` và `max_locals` | Giới hạn tài nguyên cho operand stack/local variable slots của một frame phương thức |
| **GC stack maps / oop maps** của một JVM | Dữ liệu nội bộ về vị trí reference tại các điểm runtime phù hợp để hỗ trợ GC |
| **JIT compiler metadata** | Thông tin phục vụ tối ưu và thực thi mã máy đã biên dịch |

Hai thuật ngữ cùng có chữ **stack map** không nhất thiết chỉ cùng một cấu trúc. Đặc biệt, **`StackMapTable` trong `.class` không phải trực tiếp bản đồ GC roots ở safe point** và không mô tả trạng thái thực tế của từng thread tại thời điểm chạy.

## 12. Ví dụ tư duy: phân biệt lỗi verification và exception hợp lệ

Xét hai tình huống:

**Tình huống A — mã Java hợp lệ nhưng có thể ném ngoại lệ:**

```java
static int divide(int a, int b) {
    return a / b;
}
```

Nếu `b == 0`, chương trình có thể ném `ArithmeticException` ở runtime. Đây **không** phải bằng chứng bytecode có kiểu sai. Verification bảo đảm phép `idiv` được dùng trên kiểu phù hợp, chứ không bảo đảm số chia luôn khác 0.

**Tình huống B — bytecode sai kiểu:**

```text
Giả định bytecode:
  iconst_1       // đẩy int lên operand stack
  invokevirtual  // giả định lời gọi yêu cầu receiver reference ở vị trí này
```

Nếu không có receiver reference thích hợp và chữ ký/stack operand hợp lệ, bytecode vi phạm quy tắc kiểu/stack. Đây là **lỗi cấu trúc hoặc xác minh**, khác với `ArithmeticException` hợp lệ ở A.

Tương tự, `String s = null; s.length();` là mã Java có thể biên dịch và có thể ném `NullPointerException` khi chạy. `StackMapTable` có thể xác nhận slot `s` thuộc nhóm **reference type**, nhưng **không chứng minh runtime reference đó khác null**. Verifier xử lý an toàn kiểu, không chứng minh tất cả điều kiện nghiệp vụ.

## 13. Những nhầm lẫn phổ biến

1. **“StackMapTable là call stack của JVM.”** Sai: nó là attribute trong class file, mô tả trạng thái kiểu theo bytecode offsets, không lưu lịch sử lời gọi lúc chạy.
2. **“Mỗi dòng Java tương ứng một frame.”** Sai: frame gắn với các vị trí bytecode phục vụ verifier; không phải cứ mỗi source line có một entry.
3. **“Frame ghi giá trị thật của biến.”** Sai: frame ghi verification type, không lưu giá trị như 42, `"hello"` hay PID.
4. **“StackMapTable kiểm tra runtime null-safety.”** Sai: reference có thể mang `null`; verifier không ngăn mọi `NullPointerException`.
5. **“Một phương thức thiếu StackMapTable là chắc chắn invalid.”** Sai: có initial frame ngầm định và quy tắc cho attribute ngầm định có 0 entry ở class version phù hợp.
6. **“Một frame có thể chèn vào bất kỳ byte offset nào.”** Sai: frame phải đáp ứng quy tắc offset/instruction/control-flow của verifier.
7. **“`Top` đồng nghĩa với `Object`.”** Sai: `Top` là verification type đặc biệt; `Object(java/lang/Object)` là một reference verification type cụ thể.
8. **“`Uninitialized` chính là null.”** Sai: uninitialized reference liên quan object đang trong quá trình tạo; `Null` là null reference đặc biệt.
9. **“`max_stack` lớn là đủ sửa mọi `VerifyError`.”** Sai: kích thước đủ không tự sửa kiểu stack, local, branch target hoặc initialization state.
10. **“Thêm bytecode không đổi line number nên không cần cập nhật frame.”** Sai: frame dựa trên **offset và luồng bytecode**, không dựa vào line number.
11. **“StackMapTable là dữ liệu GC/JIT runtime.”** Sai: nó thuộc giai đoạn class file verification; GC/JIT có metadata nội bộ khác.
12. **“`javap -c` và `javap -v` cho cùng mức thông tin.”** `-c` tập trung vào instruction; `-v` cung cấp chi tiết attribute, trong đó có `StackMapTable` khi được ghi.

## 14. Bài tập tự kiểm tra (kèm đáp án)

**Câu 1.** `StackMapTable` được gắn ở đâu?

**Đáp án:** Trong bảng attributes của **`Code` attribute** thuộc một phương thức có bytecode, tối đa một `StackMapTable` cho mỗi `Code`.

**Câu 2.** Vì sao `entries[0]` không phải frame ở offset đầu tiên của phương thức?

**Đáp án:** Vì **initial frame** được verifier tính ngầm định từ method descriptor và các quy tắc phương thức; entry đầu là frame tường minh kế tiếp.

**Câu 3.** Phương thức `static void f(long x, Object y)` cần bao nhiêu local slots tối thiểu cho tham số?

**Đáp án:** Ít nhất **3 slots**: `long` chiếm slot 0–1, `Object` ở slot 2. Với phương thức `static` không có receiver `this`.

**Câu 4.** Tag nào dùng cho reference null? Tag nào cho `this` chưa khởi tạo trong constructor?

**Đáp án:** `Null_variable_info` tag **5**; `UninitializedThis_variable_info` tag **6**.

**Câu 5.** Frame `same_frame` có thể biểu diễn operand stack chứa hai `int` được không?

**Đáp án:** Không. `same_frame` kế thừa locals nhưng yêu cầu operand stack **rỗng**. Nếu trạng thái cần stack chứa hai giá trị, phải mã hóa bằng dạng phù hợp.

**Câu 6.** Tại sao sau khi thêm một `if` vào phương thức bằng transformer, class có thể bị `VerifyError`?

**Đáp án:** Vì có target luồng điều khiển mới và các trạng thái locals/operand stack tại target cần khớp; frame hoặc offsets cũ có thể không còn hợp lệ.

**Câu 7.** `StackMapTable` có ngăn `NullPointerException` trong `text.length()` khi `text == null` không?

**Đáp án:** Không. Reference null vẫn có thể được lưu trong một slot reference; dereference ở runtime mới ném `NullPointerException`.

**Câu 8.** Có phải cả ba offset của những frame tường minh đều bằng tổng các `offset_delta` tương ứng?

**Đáp án:** Không. Frame tường minh đầu tiên ở `offset_delta`; mỗi frame tiếp theo ở `previous_offset + offset_delta + 1`.

**Câu 9.** Dùng công cụ JDK nào xem `StackMapTable`?

**Đáp án:** `javap -v -p TenClass` sau khi đã biên dịch; có thể đọc cả bytecode, exception table và các thuộc tính của phương thức.

**Câu 10.** Vì sao output `javap -v` chỉ có một số frame dù phương thức có hàng chục bytecode instructions?

**Đáp án:** Frame được lưu tại những offset cần thiết, thường là đầu basic block; các instruction khác được verifier kiểm tra bằng các quy tắc type transition.

## 15. Tổng kết và tài liệu chuẩn

**StackMapTable là hợp đồng trạng thái kiểu của phương thức ở cấp bytecode.** Nó xuất hiện bên trong `Code` attribute, lưu các frame tường minh được nén, mỗi frame mô tả kiểu của locals và operand stack ở một offset. Verifier kết hợp initial frame ngầm định, frame ở các điểm rẽ nhánh/exception và quy tắc của từng opcode để bảo đảm class file không vi phạm các ràng buộc cấu trúc/kiểu mà JVM yêu cầu.

Nếu chỉ ghi nhớ một quy trình khi sửa bytecode:

```text
Đổi instructions / branches / handlers
             ↓
Kiểm tra lại control-flow graph
             ↓
Tính lại trạng thái locals + operand stack ở các điểm cần thiết
             ↓
Sinh/cập nhật frame và giới hạn stack/locals hợp lệ
             ↓
Đọc output bằng javap -v
             ↓
Kiểm chứng class trên JVM mục tiêu
```

Tài liệu đặc tả chính thức:

- [JVMS `4.7.3` — The Code Attribute (Java SE 25)](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.3): nơi chứa bytecode, exception table và attributes của phương thức.
- [JVMS `4.7.4` — The StackMapTable Attribute (Java SE 25)](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.4): cấu trúc frame, verification_type_info, kiểu frame và cách tính offset.
- [JVMS `4.10.1` — Verification by Type Checking (Java SE 25)](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1): quy tắc xác minh bằng kiểm tra kiểu và initial frame.
- [JVMS `2.6` — Frames (Java SE 25)](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6): local variables và operand stacks của JVM.
- [`javap` tool documentation (JDK 25)](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javap.html): các cờ `-c`, `-v`, `-p` để đọc class file.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-file-format-008">LineNumberTable, LocalVariableTable và giới hạn khi khôi phục mã nguồn</a>

<details>
<summary>Xem chi tiết</summary>

# LineNumberTable, LocalVariableTable và giới hạn khi khôi phục mã nguồn

## 1. Tệp `.class` có thể tiết lộ điều gì về mã nguồn?

Trình biên dịch Java chuyển source code thành **class file** chứa bytecode và các cấu trúc nhị phân theo đặc tả JVM. Class file đủ thông tin để JVM tải, kiểm chứng, liên kết và thực thi một lớp; nó **không phải bản lưu nguyên văn** của tệp `.java`.

Trong class file, những **thuộc tính (attributes) tùy chọn** có thể cung cấp dữ liệu cho công cụ gỡ lỗi, chẳng hạn:

- **`LineNumberTable`**: ánh xạ một số vị trí bytecode của phương thức tới **số dòng mã nguồn**.
- **`LocalVariableTable`**: mô tả **tên biến cục bộ**, kiểu dưới dạng descriptor, vị trí slot và khoảng bytecode biến có hiệu lực.
- **`LocalVariableTypeTable`**: có thể lưu thêm **generic signature** cho một số biến cục bộ có kiểu tổng quát; nó không thay thế `LocalVariableTable`.
- **`SourceFile`**: tên tệp nguồn dùng để mô tả nơi class được biên dịch, thường phục vụ debug/stack trace; đây chỉ là tên, không chứa nội dung tệp.
- **`SourceDebugExtension`** (khi có): có thể mang thông tin ánh xạ nguồn mở rộng do công cụ/ngôn ngữ tạo ra, hữu ích cho tình huống nhiều tầng sinh mã.

Hai bảng đầu là trọng tâm của bài. Sự hiện diện của chúng giúp debugger, IDE và `javap` hiển thị thông tin dễ hiểu hơn, **không làm JVM cần mã nguồn gốc mới thực thi được**. Một class file không có các bảng này vẫn có thể là class file hoàn toàn hợp lệ và chạy đúng.

Cũng cần phân biệt ba hoạt động:

1. **Disassembly**: hiển thị instruction JVM, ví dụ `iload`, `iadd`, `invokevirtual` từ bytecode. Đây là việc `javap -c` thực hiện.
2. **Debugging**: đối chiếu vị trí thực thi với dòng/biến nguồn khi metadata và trạng thái runtime cho phép. Debugger có thể xem dữ liệu đã được JIT/runtime duy trì, nhưng không tạo lại thông tin nguồn vốn chưa từng được lưu.
3. **Decompilation**: suy đoán một đoạn source cấp cao có hành vi tương ứng từ bytecode. Source được decompiler dựng lại **không mặc nhiên là source ban đầu**.

## 2. Các thuộc tính nằm ở đâu trong cấu trúc class file?

Theo JVM class-file format, một class có constant pool, khai báo field/method và các attribute. Mỗi method có thể có một thuộc tính **`Code`** nếu phương thức đó có bytecode. Bên trong `Code` gồm bytecode, thông tin stack/locals, bảng exception handler và danh sách attribute **gắn riêng với đoạn code ấy**.

~~~text
ClassFile
├── constant_pool
├── fields[]
├── methods[]
│   └── method_info
│       └── attributes[]
│           └── Code (nếu method có bytecode)
│               ├── max_stack
│               ├── max_locals
│               ├── code[]                  ← các byte instruction
│               ├── exception_table[]
│               └── attributes[]
│                   ├── LineNumberTable    ← tùy chọn
│                   ├── LocalVariableTable ← tùy chọn
│                   ├── LocalVariableTypeTable (nếu có)
│                   └── StackMapTable/...  ← phục vụ mục đích khác
└── attributes[]
    └── SourceFile (nếu có)
~~~

`LineNumberTable` và `LocalVariableTable` là **nested attribute của `Code`**, nên gắn với **một method cụ thể**, không phải bảng duy nhất áp dụng toàn bộ class. `SourceFile` là thuộc tính của class, không nằm trong `Code`.

Mặc dù dữ liệu này nằm trong `.class`, nó **không phải instruction có tác dụng lên thuật toán Java**. JVM có thể sử dụng hoặc cung cấp dữ liệu cho công cụ debug; việc có hay không có metadata không biến `if`, `for` hoặc `return` thành thuật toán khác.

Bên cạnh đó:

- **`StackMapTable`** hỗ trợ kiểm chứng kiểu ở những điểm bytecode liên quan. Đây không phải danh sách tên biến nguồn và không thay thế LocalVariableTable.
- **`Exceptions`** mô tả những exception được khai báo ở cấp phương thức; không chứa cách viết nguyên văn `throws` trong source.
- **`Signature`** có thể ghi generic signature của class/field/method; generic source không vì vậy được phục dựng đầy đủ.
- **`MethodParameters`** có thể chứa tên và flags của **tham số method**, tùy compiler option như `javac -parameters`; nó **khác** `LocalVariableTable`.

## 3. LineNumberTable: ánh xạ vị trí bytecode tới dòng nguồn

Một entry của `LineNumberTable` gồm hai giá trị ý nghĩa:

| Trường | Ý nghĩa |
| --- | --- |
| `start_pc` | Offset bytecode tính từ đầu mảng `code[]` của **method hiện tại** |
| `line_number` | Số dòng nguồn mà compiler liên hệ với bytecode bắt đầu từ vị trí ấy |

Ví dụ **minh họa** (offset/dòng do compiler quyết định, không phải một output `javap` được cam kết cho mọi phiên bản):

~~~text
LineNumberTable:
  line 10: 0
  line 11: 4
  line 12: 9
~~~

Cách đọc: instruction bắt đầu tại offset 0 được gắn với dòng 10; offset 4 gắn dòng 11; offset 9 gắn dòng 12. Những instruction giữa các mốc có thể được debugger liên hệ với mốc dòng phù hợp theo bảng và vị trí thực thi.

**LineNumberTable không phải bản đồ một-một giữa dòng nguồn và instruction:**

- Một dòng Java có thể sinh **nhiều instruction**.
- Một statement hoặc biểu thức có thể trải qua **nhiều dòng**.
- Nhiều instruction có thể được liên hệ với **cùng một dòng**.
- Compiler có thể chọn hoặc bỏ qua mốc dòng tùy cách sinh bytecode.
- Code được sinh tổng hợp hoặc do compiler biến đổi có thể có ánh xạ dòng khác trực giác từ source.
- Bảng chứa **số dòng**, không chứa chữ trong dòng, indentation, comment, tên biến hoặc cột ký tự.

### Tác dụng với stack trace

Khi exception được ghi thành stack trace, JVM/runtime có thể dùng tên class, tên method, `SourceFile` và `LineNumberTable` để báo, ví dụ:

~~~text
at demo.DebugInfoDemo.compute(DebugInfoDemo.java:7)
~~~

Đây chỉ là **ví dụ định dạng**, không phải dòng stack trace khẳng định sẽ xuất hiện trong chương trình thực hành bên dưới. Con số `7` chỉ là số dòng nguồn khi metadata cho phép xác định. Nếu không có thông tin dòng, stack trace có thể hiển thị dạng `Unknown Source`, hoặc còn tên file nhưng không có số dòng, tùy metadata và cách biểu diễn.

Có `LineNumberTable` **không đảm bảo** luôn xác định đúng một câu lệnh nguồn cho mọi PC. Với compiler sinh code, lambda, `switch`, inline ở runtime, instrumentation, hoặc mã từ ngôn ngữ khác, các mốc dòng có thể không khớp kỳ vọng của người debug.

## 4. LocalVariableTable: tên, kiểu, slot và phạm vi tồn tại

Mỗi entry `LocalVariableTable` được mô tả bằng các trường:

| Trường | Ý nghĩa |
| --- | --- |
| `start_pc` | Offset bytecode nơi khoảng hiệu lực của biến bắt đầu |
| `length` | Số byte trong khoảng hiệu lực được mô tả |
| `name_index` | Chỉ số constant pool trỏ đến tên biến |
| `descriptor_index` | Chỉ số constant pool trỏ đến descriptor của kiểu biến |
| `index` | Chỉ số **local-variable slot** trong frame của method |

Khoảng hiệu lực được mô tả bởi `[start_pc, start_pc + length)`, tính theo offset bytecode. Không được đọc `length` là số ký tự, số dòng Java, số lần biến được dùng hoặc số byte mà object chiếm trong heap.

Ví dụ mô tả gần với `javap -l` (dòng/offset là minh họa, không phải kết quả cố định):

~~~text
LocalVariableTable:
  Start  Length  Slot  Name      Signature
      0      14     0  input     I
      4      10     1  doubled   I
      9       5     2  result    I
~~~

- `input`, `doubled`, `result` là **tên nguồn compiler đã lưu**, không phải những giá trị số thực của chúng khi chương trình chạy.
- `Signature` trong đầu ra `javap` của bảng trên hiển thị **descriptor** như `I`, `Ljava/lang/String;`; đây không có nghĩa bảng `LocalVariableTable` tự lưu toàn bộ generic type.
- `Slot` xác định ô logic trong local-variable array của một **frame method**; nó **không phải địa chỉ stack trong RAM**.
- Trên method instance, slot 0 thường giữ `this`; trên method `static` không có `this`.
- `long` và `double` dùng **hai local-variable slot liên tiếp** theo quy tắc frame JVM; nhiều kiểu khác, gồm reference, dùng một slot.

### Scope ở source và khoảng bytecode

Hai biến có cùng tên trong các scope tách biệt có thể sử dụng **cùng local slot ở hai khoảng sống không giao nhau**. Một compiler có thể tái sử dụng slot dù tên và kiểu khác nhau; nếu đọc `index = 2` ở hai entry khác nhau thì không thể kết luận đó là **một biến nguồn**.

Ngược lại, source có thể khai báo một biến nhưng compiler phân bổ, tái sắp xếp hoặc loại bỏ cách biểu diễn vật lý khi tối ưu. LocalVariableTable phản ánh metadata của **class file đã được phát hành**, không phải ảnh chụp trạng thái register/stack của JIT ở thời điểm T.

### LocalVariableTypeTable và generic

Ví dụ Java:

~~~java
java.util.List<String> words = java.util.List.of("JVM");
~~~

Với class file có đủ debug metadata, `LocalVariableTable` có thể mô tả descriptor của `words` là `Ljava/util/List;`. `LocalVariableTypeTable` có thể lưu thêm generic signature như `Ljava/util/List<Ljava/lang/String;>;` để debugger hiểu kiểu tổng quát. Thông tin ấy **tùy chọn** và không phục dựng đầy đủ mọi biểu thức generic hay cách suy luận kiểu nguồn.

Không nhầm `LocalVariableTypeTable` với **`Signature` attribute** của field/method/class, hoặc `StackMapTable` hỗ trợ verification; tên tương tự nhưng mục đích và vị trí khác nhau.

## 5. Thực hành Java 17+: có và không có debug metadata

Tạo tệp **`src/demo/DebugInfoDemo.java`** với toàn bộ nội dung:

~~~java
package demo;

public final class DebugInfoDemo {
    static int compute(int input) {
        int doubled = input * 2;
        int result = doubled + 3;
        return result;
    }

    public static void main(String[] args) {
        int answer = compute(4);
        System.out.println("Result=" + answer);
    }
}
~~~

Biên dịch với JDK 17+; tạo hai thư mục đầu ra riêng trước:

~~~bash
mkdir out-debug
mkdir out-minimal
javac --release 17 -g:lines,vars,source -d out-debug src/demo/DebugInfoDemo.java
javac --release 17 -g:none -d out-minimal src/demo/DebugInfoDemo.java
~~~

Chạy hai class file bằng cùng logic:

~~~bash
java -cp out-debug demo.DebugInfoDemo
java -cp out-minimal demo.DebugInfoDemo
~~~

**Mỗi lệnh in đúng một dòng stdout**:

~~~text
Result=11
~~~

Cả hai bản chạy cùng ý nghĩa vì `-g` thay đổi lượng **debug metadata** compiler đưa vào class, không thay đổi thuật toán cần thiết để tính `4 * 2 + 3`.

### 5.1. Dùng javap đọc phiên bản có debug info

~~~bash
javap -classpath out-debug -c -l -p demo.DebugInfoDemo
javap -classpath out-debug -v -p demo.DebugInfoDemo
~~~

Quan sát:

1. `-c` hiển thị bytecode của `compute` và `main` (như phép nhân, phép cộng, gọi `println`, trả về).
2. `-l` yêu cầu in **line number** và **local variable tables** nếu có.
3. `-p` yêu cầu hiển thị cả thành viên không public; `compute` của ví dụ là package-private.
4. `-v` in thêm thông tin chi tiết như class-file version, constant pool và các `Code` attributes.
5. Với `--release 17`, class file được sinh cho Java 17 có **major version 61**.

Bản `out-debug` được yêu cầu phát hành line number, tên local variable và thông tin source. Bởi vậy trong phần `compute`, bạn có thể tìm thấy những biến như `input`, `doubled`, `result` trong `LocalVariableTable`. Đồng thời `LineNumberTable` ánh xạ những offset bytecode tới các số dòng của tệp bạn vừa tạo. **Số offset và bảng in đầy đủ do compiler quyết định**: hãy đọc kết quả thực tế thay vì chép một bảng ví dụ rồi xem là tiêu chuẩn.

### 5.2. So với phiên bản loại bỏ debug metadata

~~~bash
javap -classpath out-minimal -c -l -p demo.DebugInfoDemo
javap -classpath out-minimal -v -p demo.DebugInfoDemo
~~~

Với `-g:none` của `javac`, class file không mang các thuộc tính debug `LineNumberTable` và `LocalVariableTable` do những tùy chọn `-g` sinh ra. `SourceFile` debug attribute cũng không được yêu cầu ở bản này. Dù vậy, bytecode cần thiết để thực hiện `compute(4)` và gọi `println` vẫn tồn tại; `javap -c` vẫn hiển thị instruction và phương thức, và `java` vẫn chạy được.

Không rút ra kết luận rằng **mọi metadata đã biến mất**: class file vẫn phải có tên class/method, descriptor, constant pool cùng cấu trúc phục vụ runtime; một số attribute khác vẫn cần cho kiểm chứng hoặc tính năng ngôn ngữ.

### 5.3. Phân biệt các công tắc thường dùng

| Tùy chọn javac | Thông tin cần quan sát |
| --- | --- |
| `-g` | Sinh tất cả debug information được hỗ trợ, gồm line, source, vars |
| `-g:lines,vars,source` | Yêu cầu ba nhóm thông tin trên một cách tường minh |
| `-g:lines,source` | Có line/source nhưng không yêu cầu tên local variables |
| `-g:none` | Không sinh debug information |
| `-parameters` | Có thể lưu **tên tham số method** trong `MethodParameters`; khác LocalVariableTable |
| `--release 17` | Nhắm ngữ nghĩa API/platform và class-file target của Java 17; không phải công tắc debug |

`javac` ở chế độ mặc định thường sinh **lines và source** nhưng không sinh **vars**. Khi cần xem chắc chắn tên local variables trong bài tập, dùng `-g:vars` hoặc `-g` theo compiler cụ thể.

## 6. Đọc `javap -v` mà không nhầm metadata với mã chương trình

Khi mở phần `Code`, bạn sẽ thường thấy cấu trúc liên quan:

~~~text
Code:
  stack=..., locals=..., args_size=...
     0: ...
     1: ...
     ...
  LineNumberTable:
    line ...: ...
  LocalVariableTable:
    Start Length Slot Name Signature
    ...
~~~

Đây là **khung trình bày**, không phải output thực của một phiên bản `javac` cụ thể.

- `stack=...` và `locals=...` biểu thị giới hạn operand stack và số local slots **theo frame bytecode**, không phải kích thước native call stack hoặc tổng bộ nhớ chương trình.
- Mã lệnh ở địa chỉ 0, 1, ... là **offset trong `code[]` của method**, không phải địa chỉ RAM.
- Một method abstract hoặc native không có phần `Code` tương ứng, vì vậy không kỳ vọng nó chứa `LineNumberTable`/`LocalVariableTable` như method Java có bytecode.
- Method được tạo bởi compiler (constructor mặc định, bridge, lambda helper, synthetic member...) có thể không có cấu trúc mapping tương ứng với một method nguyên vẹn trong source.

Nếu tên biến nguồn vắng mặt ở `LocalVariableTable`, debugger/decompiler có thể tự đặt tên như `var1`, `local2` hoặc `arg0`. Những tên ấy **là sản phẩm suy đoán hoặc hiển thị của công cụ** nếu không có metadata đáng tin, không chứng minh tệp Java gốc có cùng tên.

## 7. Giới hạn khi khôi phục mã nguồn từ class file

### 7.1. Biên dịch là phép biến đổi nhiều-đầu-vào thành ít thông tin hơn

Nhiều đoạn source khác nhau có thể tạo **bytecode tương đương, thậm chí trùng** sau tối ưu tại thời điểm biên dịch. Ví dụ:

~~~java
int result = 4 + 5;
~~~

và:

~~~java
int result = 9;
~~~

Một Java compiler có thể constant-fold biểu thức `4 + 5` thành hằng `9`. Khi xem bytecode, không có bảo đảm phân biệt người viết từng gõ phép cộng hay hằng trực tiếp. Nếu nguồn có `final int n = 9` hoặc import rút gọn, thông tin nguyên dạng cũng có thể bị compiler chuẩn hóa hay loại bỏ.

Ngay cả khi giữ `LineNumberTable`, hai cách viết trên có thể nằm ở **cùng một dòng** nên chỉ biết mốc dòng không đủ phân biệt cách viết. Có `LocalVariableTable` có thể giữ tên `result`, nhưng không giữ biểu thức `4 + 5` dưới dạng text.

### 7.2. Những thông tin thường mất hoặc biến dạng

| Thông tin ở source | Có thể khôi phục chính xác từ class file? |
| --- | --- |
| Bytecode instruction và descriptor đã biên dịch | **Có**, khi class file hợp lệ và có thể đọc, `javap` xem được cấu trúc thực |
| Tên class/method/field hiện diện trong ABI | Thường **có**, nhưng có thể đã bị obfuscate/rename trước khi nhận file |
| Dòng source gắn với bytecode | **Có một phần** nếu tồn tại LineNumberTable và thông tin tương ứng |
| Tên biến cục bộ | **Có một phần** nếu tồn tại LocalVariableTable và compiler lưu tên |
| Generic type của một số local variable | **Có một phần** nếu có LocalVariableTypeTable và thông tin hợp lệ |
| Tên tham số method | Có thể qua LocalVariableTable hoặc MethodParameters, tùy compiler flags |
| Comment, whitespace, indentation, cách xuống dòng | **Không**, thông thường không được lưu |
| Import statement nguyên văn, alias import, cách đặt qualifier | **Không**; class file thường lưu tham chiếu kiểu đã được compiler giải quyết |
| Biểu thức gốc trước constant folding hoặc desugaring | **Không đảm bảo** |
| Cách viết `for` hay `while`, `switch` ở source | Chỉ có thể **suy luận**; nhiều cấu trúc hạ xuống bytecode tương tự |
| Chính xác tên/ranh giới mọi biến trong source | **Không đảm bảo**; scope/slot có thể bị thay đổi hoặc metadata thiếu |
| Tên file gốc, nội dung tệp `.java` đầy đủ | SourceFile chỉ là tên khi có; **không chứa toàn văn tệp** |

Một decompiler thường dựng code **dễ hiểu và có vẻ giống Java**, nhưng đó là **một lời giải** cho bytecode đã biết, không chứng minh đây là source mà tác giả đã viết.

### 7.3. Compiler biến đổi ngữ nghĩa cấp cao xuống JVM

Những tính năng cấp nguồn có thể bị **lowering/desugaring** khi biên dịch:

- Lambda hoặc method reference có thể gắn với `invokedynamic` và bootstrap methods thay vì tồn tại như biểu thức lambda nguyên văn.
- Try-with-resources được chuyển thành cấu trúc đóng tài nguyên và xử lý exception/suppressed exception, thường dài hơn đáng kể so với cú pháp `try (...)`.
- Enhanced `for` có thể trở thành vòng lặp iterator hoặc truy cập theo index tùy kiểu dữ liệu.
- String switch, enum switch, generic erasure, autoboxing, bridge methods và synthetic method/class đều có thể làm bytecode không còn trùng cấu trúc câu lệnh ban đầu.
- Kotlin, Scala hoặc ngôn ngữ JVM khác có thể tạo state machine, helper class, metadata và tên method không có cú pháp Java nguồn tương đương một-một. **Decompile được Java-like output** không có nghĩa đã khôi phục được source Kotlin/Scala ban đầu.

Ngay cả khi decompiler khôi phục một cách viết Java **biên dịch lại được** và chạy tương đương, comment, tên biến không có trong metadata, ý đồ nghiệp vụ, cấu trúc file/package nguồn và lựa chọn style vẫn có thể khác bản gốc.

### 7.4. Obfuscation và stripping metadata

Công cụ đóng gói/obfuscation có thể đổi tên class, method hoặc field không bắt buộc giữ tên, xóa debug attributes, thay đổi cấu trúc bytecode hoặc tối ưu. Vì vậy một file tải về có thể khác class file được compiler phát hành lần đầu. `LocalVariableTable` có thể bị xóa hoàn toàn hoặc giữ với tên không còn giá trị gốc.

Mất LineNumberTable làm việc đọc stack trace và đặt breakpoint theo dòng khó hơn; mất LocalVariableTable làm debugger khó trình bày tên biến cục bộ. **Không có bảng nào ngăn JVM chạy** nếu class file sau biến đổi vẫn tuân thủ ràng buộc thực thi.

### 7.5. JIT không phải quá trình khôi phục ngược từ source

JVM có thể JIT-compile và tối ưu instruction lúc chạy: inline method, loại bỏ allocation, thay đổi vị trí dữ liệu trong register/stack. Đây là tối ưu **runtime**, tách với class-file debug attributes compiler đã sinh trước đó.

Decompiler thường đọc class file trên đĩa hoặc bytecode đang có; nó **không đọc được toàn văn source ban đầu từ code máy đã JIT tối ưu**. Khi debug ở chế độ tối ưu, một biến nguồn có thể tạm không có giá trị quan sát trực tiếp tại PC được chọn, dù LocalVariableTable vẫn mô tả tên/slot ở mức bytecode. Công cụ debug có thể dùng metadata runtime bổ sung để hiển thị thông tin, nhưng không có cam kết mọi local luôn đọc được.

## 8. Ứng dụng thực tế của hai bảng

### 8.1. Stack trace và điều tra production

Khi lỗi xuất hiện trong log, `LineNumberTable` giúp gắn stack frame tới số dòng nguồn. Điều này hữu ích **chỉ khi nguồn đang xem đúng phiên bản class đã chạy**. Nếu bản production được build từ commit khác hoặc qua instrumentation/obfuscation, dòng 52 trên stack trace không nhất thiết chỉ cùng statement ở checkout hiện tại.

Quy trình chẩn đoán:

1. Xác định đúng artifact/version đang chạy, JDK, classpath/module path và build identifier.
2. Đọc stack trace, tách **stderr** với stdout/log theo thời gian nếu có thể.
3. Kiểm tra class runtime chứa LineNumberTable bằng `javap -v` trên **artifact thực tế**.
4. Mở nguồn ở đúng commit/build; không ghép số dòng của binary A với source B.
5. Nếu artifact đã obfuscate, dùng mapping tương ứng do build cung cấp, nếu được lưu giữ.

### 8.2. Debugger và IDE

Khi bấm “Step Over/Into” hoặc đặt breakpoint ở dòng, debugger dùng mapping từ **dòng nguồn ↔ vị trí thực thi bytecode** theo dữ liệu compiler/runtime. Do LineNumberTable không phải ánh xạ một-một, một breakpoint dòng có thể dừng ở vị trí không đúng trực giác, hoặc không thể đặt khi không có code tương ứng.

LocalVariableTable hỗ trợ hiển thị tên local tại một phạm vi code. Không có bảng này, debugger vẫn có thể kiểm tra field, tham số có metadata riêng, stack/frame và giá trị khác tùy khả năng, nhưng không hứa khôi phục tên local ban đầu. Những thông báo “variable not available” có thể do **scope đã hết, metadata bị thiếu, hoặc runtime tối ưu**, không chỉ một nguyên nhân.

### 8.3. Decompile để hiểu bytecode, không thay thế source control

Decompile có thể giúp:

- Hiểu public API và luồng điều khiển của thư viện thiếu source.
- Nhận ra synthetic/bridge methods hoặc code được compiler sinh.
- Đối chiếu hành vi bytecode với giả định lúc code review.
- Điều tra khác biệt giữa nguồn và binary do build, bytecode instrumentation hoặc obfuscation.

Giới hạn: kết quả decompile **phải được kiểm chứng bằng bytecode và thử nghiệm** trước khi coi là hành vi đáng tin. Khôi phục chính xác file nguồn vẫn cần bản lưu source code tương ứng trong Git/source archive hoặc artifact gốc nếu có.

## 9. Checklist đọc một class file lạ

Với `some-library.jar`, có thể làm việc như sau:

~~~bash
jar tf some-library.jar
javap -classpath some-library.jar -public -s example.pkg.LibraryClass
javap -classpath some-library.jar -c -l -p example.pkg.LibraryClass
javap -classpath some-library.jar -v -p example.pkg.LibraryClass
~~~

`jar tf` liệt kê nội dung archive; `javap -public -s` cho API/descriptor; `-c` xem instruction, `-l` xem bảng line/local nếu có; `-v` kiểm tra version, constant pool và attributes. Thay `example.pkg.LibraryClass` bằng tên class thật có trong JAR.

Nếu các bảng hiện diện, tự hỏi:

- `LineNumberTable` có bao phủ vùng code cần điều tra không? Tên nguồn `SourceFile` là gì?
- `LocalVariableTable` có tên local và phạm vi PC nào? Slot có được dùng lại ở khoảng khác không?
- Có `LocalVariableTypeTable` hoặc `MethodParameters` không? Chúng trả lời **câu hỏi khác** so với LNT/LVT.
- Có dấu hiệu synthetic/bridge method, lambda hoặc compiler transformation không?
- Artifact đã obfuscate/strip hay tương ứng commit/build nào?

Không lấy số offset `javap` rồi coi đó là **địa chỉ vật lý**, không lấy tên local từ decompiler rồi kết luận chắc chắn đó là tên nguồn.

## 10. Những hiểu lầm cần tránh

1. **“LineNumberTable lưu code từng dòng.”** Không: nó lưu **offset bytecode và số dòng**, không lưu ký tự trên dòng.
2. **“LocalVariableTable chứa giá trị hiện thời của từng biến.”** Không: nó mô tả metadata tên/descriptor/slot/range; giá trị là trạng thái runtime.
3. **“Có LVT thì compiler phải giữ tên biến sau mọi tối ưu.”** Không: compiler, obfuscator hoặc build pipeline có thể bỏ/thay metadata, và JIT runtime có thể tối ưu state.
4. **“Slot index là địa chỉ RAM.”** Không: đó là index logic của local-variable array trong JVM frame.
5. **“SourceFile đủ để khôi phục source.”** Không: nó thường chỉ lưu tên file, không có văn bản `.java`.
6. **“`javap -c` chính là decompiler ra Java.”** Không: `javap -c` **disassemble JVM instruction**; decompiler phải suy diễn cấu trúc cấp cao.
7. **“Không có debug table thì class không chạy.”** Không: metadata debug là tùy chọn, JVM vẫn thực thi class hợp lệ.
8. **“Một class file sẽ chỉ tương ứng một source Java y hệt.”** Không: nhiều source có thể sinh bytecode tương đương, nhất là sau constant folding/desugaring.
9. **“Generic parameter, lambda, try-with-resources luôn còn nguyên như lúc viết.”** Không: compiler có thể đổi cách biểu diễn mà giữ ngữ nghĩa cần thiết.
10. **“Có stack trace dòng N là biết chắc lỗi ở dòng N của source mới nhất.”** Không: phải đối chiếu build/version và biến đổi class.

## 11. Bài tập tự kiểm tra

1. Một `LineNumberTable` ghi `line 18: 6`. Số 6 là gì? **Offset bytecode trong mảng code[] của method**, không phải cột ký tự hoặc địa chỉ RAM.
2. `LocalVariableTable` ghi `Start=3, Length=8, Slot=1, Name=total, Signature=I`. Dữ liệu đó cho biết giá trị total khi PC=5 không? **Không**; chỉ biết biến được mô tả ở khoảng `[3,11)`, slot 1, kiểu int.
3. Hai entry cùng slot 1 nhưng có khoảng sống khác nhau có nhất thiết là một biến nguồn không? **Không**; compiler có thể dùng lại slot.
4. Dùng `javac -g:none` khiến `java Demo` không chạy nữa không? **Không**, nếu class file vẫn hợp lệ và đủ dependency.
5. Tại sao decompiler không chắc biết người viết gõ `4 + 5` hay `9`? Vì compiler có thể constant-fold cả hai về cùng bytecode.
6. `MethodParameters` và `LocalVariableTable` có thay thế hoàn toàn cho nhau không? **Không**; một bên mô tả metadata parameter, bên kia mô tả các local variables theo vùng code.
7. Tại sao có tên biến trong LVT mà debugger vẫn có thể nói “optimized out”? Vì **metadata bytecode không bảo đảm giá trị đó luôn còn ở một vị trí đọc được sau JIT tối ưu**.
8. Để đối chiếu stack trace production, cần nguồn mới nhất hay **nguồn đúng với build đã chạy**? Cần **đúng build**.

## 12. Tài liệu chuẩn

- JVM Specification Java SE 17, **The Class File Format**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
- JVMS §4.7.12, **LineNumberTable Attribute**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.12
- JVMS §4.7.13, **LocalVariableTable Attribute**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.13
- JVMS §4.7.14, **LocalVariableTypeTable Attribute**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.14
- JDK `javap` tool: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html
- JDK `javac` options (`-g`, `-parameters`, `--release`): https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html

**Kết luận kỹ thuật:** `LineNumberTable` nối **vị trí bytecode với dòng nguồn**; `LocalVariableTable` nối **scope/slot bytecode với tên và kiểu local variable**. Hai bảng giúp debug, nhưng **không lưu nguyên văn mã nguồn**. Decompiler có thể dựng mã tương đương để tham khảo, chứ không thể từ những metadata ấy bảo đảm khôi phục đúng comment, cấu trúc và câu chữ source Java ban đầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-file-format-009">Signature, annotation, synthetic và bridge: dữ liệu bổ sung ngoài descriptor</a>

<details>
<summary>Xem chi tiết</summary>

# Signature, annotation, synthetic và bridge: dữ liệu bổ sung ngoài descriptor của ClassFile

Một tệp `.class` không chỉ chứa bytecode và **descriptor** của field/method. Nó còn có các **attribute**, cờ truy cập và metadata giúp trình biên dịch, công cụ phân tích, thư viện reflection và framework hiểu thêm thông tin ở cấp ngôn ngữ Java. Bốn khái niệm quan trọng trong nhóm này là **`Signature`**, **annotation**, **`synthetic`** và **bridge method**.

Các thông tin này liên quan nhưng không đồng nhất:

- **Descriptor** là biểu diễn kiểu ở mức JVM để mô tả field hoặc danh sách tham số và kiểu trả về của method. Nó phục vụ cấu trúc ClassFile, kiểm tra kiểu và liên kết lời gọi ở runtime.
- **`Signature` attribute** bổ sung mô tả kiểu generic: tham số kiểu `<T>`, giới hạn kiểu, tham số hóa như `List<String>`, wildcards và quan hệ `implements Interface<String>`. Descriptor thường chỉ giữ kiểu sau **type erasure**.
- **Annotation attributes** lưu những annotation mà compiler quyết định ghi vào class file theo retention/target, bao gồm cả annotation có thể được Java reflection đọc tại runtime.
- **`ACC_SYNTHETIC`** đánh dấu thành phần được tạo bởi công cụ và không xuất hiện như khai báo tương ứng trực tiếp trong nguồn; **`ACC_BRIDGE`** đánh dấu method mang chức năng bridge. Nhiều bridge method do `javac` tạo có **cả hai cờ**, nhưng hai khái niệm không đồng nghĩa.

Bài này nằm trong chủ đề **tệp class**: điều cần hiểu là metadata được lưu ở đâu, dùng để làm gì và khác dữ liệu mà JVM trực tiếp cần khi thực thi ra sao. Các ví dụ sử dụng JDK **17 trở lên**; chi tiết bytecode và thứ tự constant pool có thể khác giữa compiler.

## 1. Descriptor trả lời câu hỏi gì?

Trong ClassFile, `field_info` và `method_info` tham chiếu tới **field descriptor** hoặc **method descriptor** bằng các mục trong constant pool. Một số ký hiệu thường gặp:

| Kiểu Java | Descriptor tương ứng |
| --- | --- |
| `int` | `I` |
| `boolean` | `Z` |
| `long` | `J` |
| `double` | `D` |
| `String` | `Ljava/lang/String;` |
| `int[]` | `[I` |
| `List` | `Ljava/util/List;` |
| `void` khi là kết quả method | `V` |

Method descriptor có dạng `(tham_so_1tham_so_2...)kieu_tra_ve`:

```text
Java:       int sum(int a, long b)
Descriptor: (IJ)I

Java:       String get()
Descriptor: ()Ljava/lang/String;

Java:       void accept(String[] values)
Descriptor: ([Ljava/lang/String;)V
```

Descriptor dùng **tên nội bộ** với dấu `/` như `java/lang/String`, không phải dấu `.` theo cú pháp mã Java. Nó không lưu đầy đủ các kiểu generic được viết trong source. Ví dụ cả `List<String>` và `List<Integer>` đều có **raw descriptor** `Ljava/util/List;`; JVM vẫn phân biệt field theo tên và descriptor chứ không phân biệt hai tham số generic trong descriptor này.

Với method, **kiểu trả về cũng là một phần của JVM method descriptor**. Đây là khác biệt cần nhớ so với quy tắc overload ở Java source, nơi không được chỉ tạo hai method trùng tên và cùng tham số mà chỉ khác kiểu trả về.

## 2. `Signature`: lưu lại thông tin generic sau type erasure

### 2.1. Vì sao cần một thuộc tính riêng?

Java generics giúp `javac` kiểm tra kiểu tĩnh, nhưng cơ chế JVM truyền thống chạy với những descriptor đã được **erasure**. Mã:

```java
java.util.List<String> names;
```

có thể được biểu diễn trong ClassFile bằng:

```text
descriptor: Ljava/util/List;
Signature:  Ljava/util/List<Ljava/lang/String;>;
```

Các dòng này minh họa thông tin có thể thấy bằng `javap -v`, không phải một chương trình Java độc lập.

Descriptor `Ljava/util/List;` cho biết kiểu reference mà runtime xử lý. `Signature` giữ lại thông tin **tham số hóa bằng `String`** để công cụ/reflective API có thể phân tích. Nếu chỉ đọc descriptor, bạn không thể biết field nguồn khai báo `List<String>` hay `List<Integer>`.

### 2.2. `Signature` có thể gắn ở đâu?

Trong ClassFile, `Signature` là một attribute có thể xuất hiện ở **class**, **field** và **method** khi cần ghi generic signature.

| Nơi xuất hiện | Ví dụ Java nguồn | Nội dung generic mà `Signature` có thể bảo lưu |
| --- | --- | --- |
| Class | `class Box<T> {}` | Khai báo type parameter `T`, bound của nó, lớp cha/interface generic |
| Field | `List<String> names;` | Tham số kiểu `String` của trường |
| Method | `<T> T identity(T input)` | Type parameter của method, tham số, kết quả và thrown types generic khi liên quan |

Ví dụ ở cấp class:

```java
class TextBox implements java.util.function.Supplier<String> {
    @Override
    public String get() {
        return "hello";
    }
}
```

Class này có thể mang `Signature` cho quan hệ `implements Supplier<String>`, dù descriptor ở method thực thi là `()Ljava/lang/String;`. Nếu một method có tham số kiểu `T`, erasure thường thay `T` bằng bound phù hợp, mặc định `Object` khi không có bound chặt hơn.

### 2.3. Cách đọc generic signature

Một vài biểu thức thường gặp:

| Java source | Biểu diễn signature minh họa |
| --- | --- |
| `List<String>` | `Ljava/util/List<Ljava/lang/String;>;` |
| `List<? extends Number>` | `Ljava/util/List<+Ljava/lang/Number;>;` |
| `List<? super Integer>` | `Ljava/util/List<-Ljava/lang/Integer;>;` |
| `List<?>` | `Ljava/util/List<*>;` |
| Type variable `T` | `TT;` |

Các ký hiệu `+`, `-`, `*`, `T...;` thuộc **ngữ pháp generic signature**, không phải bytecode instruction để cộng, trừ hoặc nhân.

**Giới hạn quan trọng:** `Signature` bổ sung metadata để công cụ hiểu **khai báo kiểu generic**; bản thân nó không biến `List<String>` và `List<Integer>` thành hai runtime class khác nhau. Cơ chế kiểm tra và chuyển kiểu của Java generics chịu ảnh hưởng type erasure; ví dụ sau khi lấy phần tử qua một giao diện generic đã erase, compiler có thể sinh `checkcast`.

### 2.4. Khi thiếu hoặc hỏng `Signature`

Không phải mọi class, field, method đều có `Signature`: một khai báo không có thông tin generic cần bảo lưu có thể chỉ cần descriptor. Các thư viện reflection có API đọc kiểu thô (`Field.getType`, `Method.getReturnType`) và API đọc kiểu generic (`Field.getGenericType`, `Method.getGenericReturnType`, `Class.getGenericInterfaces`). API generic có thể đọc attribute `Signature` khi có.

Với class file được sửa bằng bytecode tool, `Signature` không hợp lệ có thể khiến một số thao tác generic reflection ném lỗi như `GenericSignatureFormatError` hoặc `TypeNotPresentException`; điều này khác với kết luận “mọi method đều cần `Signature` để JVM gọi được”.

## 3. Annotation: metadata được lưu thành attribute

Annotation trong Java được khai báo bằng `@interface` và có thể gắn vào class, field, method, parameter, type use... tùy `@Target`. ClassFile có các attribute cho nhiều loại annotation:

| Attribute thường gặp | Vai trò |
| --- | --- |
| `RuntimeVisibleAnnotations` | Annotation tại các declaration được lưu để có thể quan sát qua các API reflection tương ứng |
| `RuntimeInvisibleAnnotations` | Annotation được giữ trong class file nhưng thường **không** được Java reflection trực tiếp trả về |
| `RuntimeVisibleParameterAnnotations`, `RuntimeInvisibleParameterAnnotations` | Annotation gắn vào tham số được đánh chỉ số theo cấu trúc method |
| `RuntimeVisibleTypeAnnotations`, `RuntimeInvisibleTypeAnnotations` | Annotation tại **type use** hoặc type parameter/bound với vị trí được mã hóa trong class file |
| `AnnotationDefault` | Giá trị mặc định của một phần tử trong **annotation type** |

Các thuộc tính này được mã hóa theo cấu trúc dữ liệu của JVM (constant pool references, element-value pairs, target/path của type annotation...). Chúng **không phải** đoạn văn `@MyAnnotation(...)` được chèn nguyên xi vào bytecode.

### 3.1. Retention quyết định annotation tồn tại tới giai đoạn nào

| `@Retention` | Sau khi biên dịch | Java reflection thông thường |
| --- | --- | --- |
| `RetentionPolicy.SOURCE` | Compiler bỏ annotation theo chính sách SOURCE; không ghi thành annotation tương ứng trong ClassFile | Không thấy |
| `RetentionPolicy.CLASS` | Được ghi vào class file; thường dưới dạng runtime-invisible annotation | Không thấy qua API đọc annotation thông thường |
| `RetentionPolicy.RUNTIME` | Được ghi thành metadata có thể truy vấn ở runtime | Có thể đọc theo vị trí và API phù hợp |

Nếu một annotation type **không khai báo `@Retention`**, mặc định là **`CLASS`**. `@Target` giới hạn nơi dùng annotation (khai báo class, method, parameter, `TYPE_USE`...), **không** tự quy định retention.

Đừng suy luận rằng mọi annotation có `RUNTIME` đều hiển thị qua cùng một lệnh reflection: declaration annotations và type-use annotations dùng các API khác nhau. Việc inherited annotations cũng có quy tắc riêng; `@Inherited` chỉ có tác dụng cho một số cách tra cứu annotation trên **class**, không tự làm method/field annotation kế thừa tùy ý.

### 3.2. `AnnotationDefault` không phải giá trị object runtime

Ví dụ:

```java
@interface Status {
    String value() default "new";
}
```

Giá trị `"new"` được ghi thành `AnnotationDefault` trên **method biểu thị phần tử `value` của annotation type**. Nó không đòi mỗi lần sử dụng annotation phải lưu lại một bản sao giá trị mặc định. Reflection có thể tra cứu bằng `Method.getDefaultValue()`.

### 3.3. Annotation chỉ bổ sung metadata, không tự chạy nghiệp vụ

Viết `@Transactional`, `@Test`, `@Deprecated` hoặc annotation tự tạo **không mặc nhiên khiến JVM thực hiện hành động đặc biệt** mỗi khi method được gọi. Hành vi bổ sung thường do compiler, annotation processor, framework, agent hoặc tooling đọc metadata và triển khai. Một số annotation chuẩn được compiler/VM xử lý theo quy tắc cụ thể; không nên khái quát rằng mọi annotation đều thuần chú thích hoặc đều tự thực thi.

## 4. `synthetic`: dấu vết thành phần được sinh ra

Một số thành phần của class file không có khai báo Java source tương ứng theo cách thông thường, bởi compiler hoặc công cụ đã sinh thêm để hỗ trợ ngữ nghĩa chương trình. ClassFile có cờ **`ACC_SYNTHETIC`** đối với các thành phần thích hợp và, trong định dạng lịch sử, có attribute tên **`Synthetic`**. Cờ và attribute là hai hình thức biểu diễn cần phân biệt theo phiên bản class file.

Các tình huống có thể gặp:

- Một **bridge method** do compiler tạo để bảo toàn dispatch sau generic erasure hoặc hỗ trợ covariant return.
- Một method phụ trợ được compiler sinh từ một số cấu trúc ngôn ngữ, ví dụ method triển khai lambda trên một số phiên bản/chiến lược compiler.
- Một field hoặc method hỗ trợ truy cập/biểu diễn nội bộ của compiler trong những cách dịch mã nguồn nhất định.

**Không phải mọi thành phần không nhìn thấy rõ ở source đều được đánh `synthetic`.** Ví dụ constructor mặc định do compiler tạo không nhất thiết mang cờ synthetic. Java record, enum và nested class cũng có các thành phần được định nghĩa bởi đặc tả với quy tắc riêng. Cần kiểm tra flag thật trên class file, không đoán chỉ từ tên như `lambda$...` hoặc `access$...`.

Phần tử synthetic vẫn là thành phần **thực sự tồn tại trong class file** và có thể chứa bytecode được JVM gọi. Reflection có thể liệt kê nó, và API như `Method.isSynthetic()`, `Field.isSynthetic()` giúp nhận biết theo metadata. Synthetic **không đồng nghĩa** private, invisible, không thể gọi hay lỗi biên dịch.

## 5. Bridge method: tại sao descriptor đã erase cần thêm một method?

### 5.1. Vấn đề phát sinh khi dùng generics

Xét interface và class:

```java
interface Provider<T> {
    T get();
}

class StringProvider implements Provider<String> {
    @Override
    public String get() {
        return "OK";
    }
}
```

Đây là **đoạn minh họa**, cần đặt vào tệp phù hợp mới biên dịch. Ở Java source, `StringProvider.get()` thực hiện hợp đồng `Provider<String>.get()`. Nhưng sau type erasure:

```text
Provider<T>.get()            → method descriptor: ()Ljava/lang/Object;
StringProvider.get()         → method descriptor: ()Ljava/lang/String;
```

Tại cấp JVM, hai descriptor **khác nhau về kiểu trả về**. Để một lời gọi qua `Provider` vẫn tìm đúng implementation sau erasure, `javac` có thể sinh thêm vào `StringProvider` method đại loại:

```java
// Pseudocode diễn giải mục đích, KHÔNG được chép vào class nguồn.
// Do compiler sinh thành bytecode trong StringProvider:
public Object get() {
    return this.get(); // gọi bản get() trả về String
}
```

Đây **không phải Java source hợp lệ** khi đặt cùng `String get()` trong class vì Java không overload chỉ theo return type. Trong **ClassFile**, hai method có descriptor khác nhau và method trả về `Object` được compiler đánh dấu **`ACC_BRIDGE`**, thông thường đồng thời có **`ACC_SYNTHETIC`**. Bridge chuyển tiếp lời gọi tới implementation chính, giúp polymorphism hoạt động đúng.

Ở mức bytecode minh họa:

```text
StringProvider.get():Ljava/lang/String;   // method gốc
StringProvider.get():Ljava/lang/Object;   // method bridge
    aload_0
    invokevirtual StringProvider.get:()Ljava/lang/String;
    areturn
```

Chỉ số constant pool và offset của từng instruction tùy kết quả `javap` thực tế.

### 5.2. Bridge không chỉ gắn với `T` của generic

Java cho phép **covariant return type** trong override (method con trả kiểu reference cụ thể hơn). Ví dụ:

```java
class Parent {
    Object value() {
        return "parent";
    }
}

class Child extends Parent {
    @Override
    String value() {
        return "child";
    }
}
```

Trong các bản dịch cần tương thích với lời gọi qua descriptor của `Parent.value():Object`, compiler có thể tạo method bridge trên `Child`. **Không nên suy ra cứ có bridge thì source nhất định khai báo generic.** Điều kiện và cách sinh bridge là việc compiler bảo đảm ngữ nghĩa dispatch/binary compatibility phù hợp.

### 5.3. `ACC_BRIDGE` và `ACC_SYNTHETIC` khác nhau

| Trường/cờ | Ý nghĩa | Cách kiểm tra |
| --- | --- | --- |
| `ACC_BRIDGE` | Method có vai trò bridge do compiler tạo | `Method.isBridge()`, `javap -v` |
| `ACC_SYNTHETIC` | Thành phần synthetic, không tương ứng một khai báo source thông thường | `Member.isSynthetic()` trên các member phù hợp, `javap -v` |
| `Signature` | Metadata mô tả generic signature | `javap -v`, API generic reflection |
| `RuntimeVisibleAnnotations` | Annotation có thể xem được ở runtime theo API thích hợp | `javap -v`, Java reflection |

Cờ `ACC_BRIDGE` chỉ có ý nghĩa cho **method**, không phải field hay class. Một bridge thường là synthetic, nhưng **synthetic method chưa chắc là bridge**; `Method.isSynthetic()` một mình không xác định được mục đích của method.

## 6. Thực hành hoàn chỉnh Java 17+: quan sát cả bốn khái niệm

Tạo file **`ClassMetadataDemo.java`**, lưu UTF-8:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class ClassMetadataDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface RuntimeTag {
        String value() default "default";
    }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    @interface ClassTag {}

    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface SourceTag {}

    interface Provider<T> {
        T get();
    }

    @RuntimeTag("demo")
    @ClassTag
    @SourceTag
    static final class TextProvider implements Provider<String> {
        @Override
        public String get() {
            return "xin chào";
        }
    }

    static final class Holder {
        List<String> names = List.of("An", "Bình");
    }

    public static void main(String[] args) throws Exception {
        Field names = Holder.class.getDeclaredField("names");
        System.out.println("raw field: " + names.getType().getTypeName());
        System.out.println("generic field: "
                + names.getGenericType().getTypeName());

        RuntimeTag tag = TextProvider.class.getAnnotation(RuntimeTag.class);
        System.out.println("runtime annotation: " + tag.value());
        System.out.println("annotation default: "
                + RuntimeTag.class.getDeclaredMethod("value").getDefaultValue());
        System.out.println("class-retention via reflection: "
                + TextProvider.class.isAnnotationPresent(ClassTag.class));
        System.out.println("source-retention via reflection: "
                + TextProvider.class.isAnnotationPresent(SourceTag.class));

        int bridges = 0;
        boolean bridgeIsSynthetic = false;
        for (Method method : TextProvider.class.getDeclaredMethods()) {
            if (method.isBridge()) {
                bridges++;
                bridgeIsSynthetic = method.isSynthetic();
            }
        }
        System.out.println("bridge methods: " + bridges);
        System.out.println("bridge is synthetic: " + bridgeIsSynthetic);

        Provider<String> provider = new TextProvider();
        System.out.println("interface dispatch: " + provider.get());
    }
}
```

Trong terminal ở thư mục chứa file:

```bash
mkdir out
javac -encoding UTF-8 -d out ClassMetadataDemo.java
java -cp out ClassMetadataDemo
```

**Standard output dự kiến từ `javac` JDK 17+ thông thường:**

```text
raw field: java.util.List
generic field: java.util.List<java.lang.String>
runtime annotation: demo
annotation default: default
class-retention via reflection: false
source-retention via reflection: false
bridge methods: 1
bridge is synthetic: true
interface dispatch: xin chào
```

Giải thích chính xác từng phần:

1. `names.getType()` trả kiểu thô của field, tương ứng descriptor `Ljava/util/List;`.
2. `names.getGenericType()` đọc thông tin generic `List<String>` được bảo lưu trong `Signature`.
3. `RuntimeTag` là declaration annotation được đánh retention `RUNTIME`, nên `getAnnotation` có thể trả về instance annotation; `value()` trả `"demo"`.
4. Giá trị `"default"` của phần tử annotation được chứa qua `AnnotationDefault` trong annotation type. Giá trị được ghi khi sử dụng `@RuntimeTag("demo")` là `"demo"`, nên không nhầm hai nguồn giá trị.
5. `ClassTag` có retention `CLASS`: có thể quan sát trong ClassFile bằng công cụ đọc metadata nhưng **không** xuất hiện qua `isAnnotationPresent` thông thường. `SourceTag` chỉ tồn tại ở giai đoạn xử lý source.
6. `TextProvider` cung cấp `String get()`, trong khi `Provider<T>.get()` có descriptor sau erasure trả `Object`. Compiler tạo một **bridge method** `Object get()` để lời gọi qua interface vẫn dispatch đúng, và bridge này thường có cả cờ synthetic.
7. Biến `provider` có kiểu `Provider<String>`; gọi `provider.get()` thông qua interface vẫn trả chuỗi `"xin chào"`.

Kết quả có chữ tiếng Việt phụ thuộc terminal hiển thị UTF-8 đúng; file được yêu cầu biên dịch bằng `-encoding UTF-8` để xác định encoding của nguồn.

### 6.1. Dùng `javap -v` để xem các thành phần thật

Sau khi biên dịch, chạy:

```bash
javap -classpath out -v -p ClassMetadataDemo
javap -classpath out -v -p 'ClassMetadataDemo$Holder'
javap -classpath out -v -p 'ClassMetadataDemo$TextProvider'
javap -classpath out -v -p 'ClassMetadataDemo$RuntimeTag'
```

Các dấu nháy đơn cho tên nested class phù hợp với shell Bash và PowerShell; nếu dùng Windows `cmd.exe`, có thể thay chúng bằng dấu nháy kép để bảo vệ ký tự `$`. Tên file/class chính là `ClassMetadataDemo`; các nested types có tên nhị phân chứa `$`.

**Ở `ClassMetadataDemo$Holder`**, tìm thông tin đại loại:

```text
java.util.List<java.lang.String> names;
  descriptor: Ljava/util/List;
  Signature: ... // Ljava/util/List<Ljava/lang/String;>;
```

Các ký hiệu `...` ở đây là **phần output minh họa đã lược bỏ chỉ số constant pool**, không phải dòng output chính xác để so sánh từng ký tự. Mấu chốt là `descriptor` biểu diễn raw `List`, còn `Signature` giữ `<String>`.

**Ở `ClassMetadataDemo$TextProvider`**, tìm cả hai method `get`:

```text
public java.lang.String get();
  descriptor: ()Ljava/lang/String;
  flags: ... ACC_PUBLIC ...

public java.lang.Object get();
  descriptor: ()Ljava/lang/Object;
  flags: ... ACC_PUBLIC, ACC_BRIDGE, ACC_SYNTHETIC ...
```

Đây là **trích mô tả những dòng cần đối chiếu**, không sao chép nguyên một listing `javap`. Có thể thấy thêm `Signature` của class cho `Provider<String>`, `RuntimeVisibleAnnotations` cho `RuntimeTag` và `RuntimeInvisibleAnnotations` cho `ClassTag`. `SourceTag` không được giữ thành annotation trong class file.

**Ở `ClassMetadataDemo$RuntimeTag`**, tìm `AnnotationDefault` của phần tử `value`; có thể đọc nội dung `default_value: s#...` với một mục constant pool tương ứng chuỗi `"default"`.

### 6.2. Nhìn bytecode bridge

```bash
javap -classpath out -c -p 'ClassMetadataDemo$TextProvider'
```

Trong method bridge trả `Object`, `javap -c` thường thể hiện mẫu hành vi tương đương:

```text
aload_0
invokevirtual ... get:()Ljava/lang/String;
areturn
```

Ý nghĩa:

- `aload_0` đưa reference `this` lên operand stack.
- `invokevirtual` gọi method implementation thực sự `get():String`.
- `areturn` trả reference `String` dưới kiểu trả về JVM `Object` của bridge.

`String` là subtype của `Object`, nên bytecode có thể trả chính tham chiếu đó mà không sao chép đối tượng. Trong các tình huống override với tham số generic, bridge còn có thể cần `checkcast` trước khi ủy quyền, vì kiểu tham số cũng bị erasure. Không mặc định mọi bridge có cùng ba lệnh trên.

## 7. Reflection nhìn thấy bao nhiêu metadata?

Java reflection cho phép đọc **một phần metadata được ghi trong class file**, không cho phép phục dựng hoàn toàn source Java ban đầu.

| Nhu cầu | API ví dụ | Dữ liệu đọc |
| --- | --- | --- |
| Kiểu thô của field | `Field.getType()` | Class đại diện kiểu đã erase |
| Kiểu generic của field | `Field.getGenericType()` | `Type`, có thể dựa trên `Signature` |
| Kiểu trả về thô | `Method.getReturnType()` | `Class<?>` |
| Kiểu trả về generic | `Method.getGenericReturnType()` | `Type` |
| Tham số kiểu của class | `Class.getTypeParameters()` | `TypeVariable<?>[]` |
| Các generic interfaces | `Class.getGenericInterfaces()` | `Type[]` |
| Annotation runtime của class | `Class.getAnnotation(...)` | Annotation nhìn thấy theo retention/API |
| Giá trị mặc định của annotation element | `Method.getDefaultValue()` | Giá trị `AnnotationDefault` |
| Method có phải bridge không? | `Method.isBridge()` | Cờ `ACC_BRIDGE` |
| Method có synthetic không? | `Method.isSynthetic()` | Metadata synthetic |
| Type-use annotations | Các API `AnnotatedType` thích hợp | Metadata type annotation runtime mà API hỗ trợ |

**Giới hạn của reflection:** `RetentionPolicy.CLASS` được thiết kế để có thể lưu trong class file nhưng không được trả như runtime-visible annotation bởi API reflection tiêu chuẩn. Muốn phân tích loại thông tin đó, cần công cụ đọc ClassFile như `javap` hoặc thư viện bytecode. Một số tên biến cục bộ, thông tin gỡ lỗi hoặc generic metadata có thể không có tùy compiler flags và cấu trúc đầu vào.

Khi reflection liệt kê `getDeclaredMethods()`, bạn có thể thấy **bridge/synthetic methods**, khiến số method lớn hơn số khai báo method trong source. Framework quét method nên có quy tắc xử lý bridge nếu nó cần định danh method triển khai logic, tránh gọi/đăng ký hai lần ngoài ý muốn. Tuy nhiên không nên **xóa tất cả synthetic** một cách mù quáng: synthetic không đồng nghĩa “không có tác dụng runtime”.

## 8. Những nhầm lẫn thường gặp

| Hiểu nhầm | Cách hiểu chính xác |
| --- | --- |
| “Descriptor của `List<String>` chứa `String`.” | Descriptor thường chỉ có `Ljava/util/List;`; thông tin generic nằm trong `Signature` khi được bảo lưu |
| “`Signature` tự ngăn mọi việc đưa `Integer` vào raw `List` tại runtime.” | Kiểm tra generic chủ yếu diễn ra khi compile; erasure và các `checkcast` thích hợp giúp bảo vệ ngữ nghĩa tại nơi dùng |
| “Mọi annotation trong `.class` đều đọc được qua reflection.” | `CLASS` retention có thể lưu trong class file nhưng không được reflection thông thường hiển thị |
| “Nếu không ghi `@Retention`, annotation mặc định là `RUNTIME`.” | Mặc định là `CLASS` |
| “`@Target` quyết định annotation còn tồn tại ở runtime.” | `@Target` giới hạn vị trí dùng; `@Retention` xác định mức lưu giữ |
| “`synthetic` nghĩa là không thực thi.” | Synthetic method có thể chứa bytecode thực sự được gọi, ví dụ bridge |
| “Mọi synthetic method là bridge.” | Bridge chỉ là một trường hợp của method được sinh; phải kiểm tra `isBridge()` |
| “Bridge chỉ xuất hiện khi lập trình generic.” | Covariant return type cũng có thể làm compiler tạo bridge |
| “JVM không thể có hai method cùng tên và cùng tham số mà khác kiểu trả về.” | Ở ClassFile, method descriptor **có kiểu trả về**, nên compiler có thể sinh bridge khác descriptor; Java source không overload kiểu này |
| “`javap -v` luôn tái tạo chính xác file `.java` ban đầu.” | Nó hiển thị bytecode và metadata có trong `.class`, không phục dựng source đã mất |

## 9. Bài tập phân tích ClassFile

### Bài tập 1 — Kiểm tra `Signature` trên field

Trong ví dụ, thay `List<String> names` bằng:

```java
List<? extends Number> names;
```

Đây là **dòng thay thế trong nested class `Holder`**, không phải một class độc lập. Vì field initializer `List.of("An", "Bình")` không còn đúng kiểu, hãy thay bằng `List.of(1, 2)`. Biên dịch lại và quan sát: descriptor vẫn là `Ljava/util/List;`, còn generic signature thay đổi để thể hiện wildcard `? extends Number`. Trước khi sửa, đọc kỹ khác biệt giữa **raw JVM type** và **generic declaration**.

### Bài tập 2 — Đổi `@Retention`

Thay `@Retention(RetentionPolicy.CLASS)` của `ClassTag` thành `RUNTIME`, biên dịch và chạy lại. Dòng:

```text
class-retention via reflection: true
```

sẽ xuất hiện dù tên thông báo trong chương trình vẫn giữ nguyên. Dùng `javap -v` để quan sát annotation của class được chuyển sang nhóm runtime-visible. Đổi về `CLASS` để giữ nguyên ví dụ ban đầu.

### Bài tập 3 — Kiểm tra bridge khi đổi kiểu trả về

Trong `TextProvider`, tên method `get` giữ nguyên và trả `String` là một phần thực hiện `Provider<String>`. Mở `javap -v -p` rồi tự trả lời:

1. Method nguồn `get():String` có descriptor nào?
2. Method bridge `get():Object` có descriptor nào?
3. Method nào có `ACC_BRIDGE` và `ACC_SYNTHETIC`?
4. Khi gọi `Provider<String> p = new TextProvider(); p.get()`, vì sao kết quả vẫn là `String` hợp lệ?

### Câu hỏi tự kiểm tra

**1. Vì sao cần `Signature` khi descriptor đã có?**  
Descriptor cho biết kiểu JVM cần để tổ chức/kiểm tra lời gọi; `Signature` giữ metadata generic phong phú hơn sau erasure cho compiler và tooling.

**2. Một annotation `RetentionPolicy.CLASS` có thể tồn tại trong class file nhưng `getAnnotation` trả `null`?**  
Có. Nó thuộc nhóm runtime-invisible annotation theo các quy tắc lưu trữ thích hợp.

**3. `ACC_SYNTHETIC` và `ACC_BRIDGE` có phải một cờ?**  
Không. Cờ synthetic đánh dấu nguồn gốc kiểu compiler-generated, còn cờ bridge xác định vai trò bridge của method.

**4. Method `Object get()` bridge có phải lỗi compiler tạo trùng method không?**  
Không. Java source không cho overload chỉ bằng kiểu trả về, nhưng JVM method descriptor bao gồm return type; compiler tạo bridge có descriptor khác để bảo đảm lời gọi polymorphic sau erasure.

**5. Annotation có tự làm JVM gọi một interceptor không?**  
Không phải theo mặc định. Hành vi framework như interceptor/proxy cần một cơ chế khác đọc và xử lý annotation.

**6. Tại sao `getDeclaredMethods()` có thể thấy nhiều method hơn source?**  
Bởi class file chứa cả method do compiler sinh, trong đó có bridge và những method synthetic khác.

Khi đọc ClassFile bằng `javap`, hãy xác định riêng **descriptor (kiểu thực thi)**, **`Signature` (generic metadata)**, **annotation attributes (metadata theo retention/vị trí)** và **flags `ACC_SYNTHETIC`/`ACC_BRIDGE` (vai trò thành phần được sinh)**. Bốn nhóm giải thích vì sao JVM vẫn thực thi theo type erasure, trong khi reflection/công cụ có thể quan sát thêm generic declaration, annotation và các method không hiển thị thành khai báo Java source thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-file-format-010">Thuộc tính cho module, nestmate, record và sealed class</a>

<details>
<summary>Xem chi tiết</summary>

# Thuộc tính class file cho module, nestmate, record và sealed class

Một tệp Java `.class` chứa nhiều hơn danh sách phương thức và các chỉ thị bytecode. **Class-file attributes** lưu metadata bổ sung để JVM kiểm soát quyền truy cập, mô tả một module, nhận diện record components hoặc kiểm tra những lớp được phép kế thừa. Bốn nhóm quan trọng trong Java hiện đại là **`Module` / `ModulePackages` / `ModuleMainClass`**, **`NestHost` / `NestMembers`**, **`Record`** và **`PermittedSubclasses`**.

Các thuộc tính này dễ bị nhầm với modifier trong source. Chẳng hạn, Java dùng từ khóa `sealed`, nhưng không có bit `ACC_SEALED` tương ứng trong `access_flags`: class file dùng `PermittedSubclasses`. Tương tự, `record` được phản ánh qua `Record` attribute; `module-info.class` là class file dạng đặc biệt có bit `ACC_MODULE`, không phải lớp Java thông thường có `main`.

Bài học đặt mỗi thuộc tính trong cấu trúc class file, giải thích ràng buộc runtime, quan hệ với mã Java và cách đọc bằng `javap`. Ví dụ sử dụng **Java 17+** (bốn nhóm đều đã là tính năng chuẩn trong Java 17).

## 1. Nhìn attributes đúng vị trí trong `ClassFile`

Theo Java Virtual Machine Specification (JVMS), class file có dạng giản lược:

```text
ClassFile
├── magic, minor_version, major_version
├── constant_pool[]           // tên, kiểu, descriptor, symbol references...
├── access_flags              // ACC_PUBLIC, ACC_FINAL, ACC_MODULE, ...
├── this_class, super_class, interfaces[]
├── fields[]                  // field_info, có attributes riêng
├── methods[]                 // method_info, Code và attributes riêng
└── attributes[]              // thuộc tính cấp CLASS
    ├── Module | ModulePackages | ModuleMainClass
    ├── NestHost | NestMembers
    ├── Record
    ├── PermittedSubclasses
    └── các thuộc tính cấp class khác nếu có
```

Đây là mô hình minh họa các **khả năng**, không phải một class file thực tế nhất thiết chứa tất cả thuộc tính trên. `module-info.class` có ràng buộc riêng, và `NestHost` không đồng thời xuất hiện với `NestMembers` trên cùng class file.

Mỗi attribute nói chung có phần đầu:

```text
u2 attribute_name_index    // index trong constant pool, trỏ tới tên UTF-8
u4 attribute_length        // số byte nội dung, không tính 6 byte header
... payload ...            // cấu trúc khác nhau theo từng attribute
```

`u2` và `u4` là số nguyên không dấu theo độ rộng 2 hoặc 4 byte trong định dạng JVM, không phải kiểu khai báo field Java. `javap -v` đọc dữ liệu nhị phân và diễn giải ra tên/giá trị dễ hiểu; source file không lưu nguyên văn mọi modifier thành chuỗi Java.

### 1.1. Bảng bốn nhóm cần ghi nhớ

| Nhóm | Attribute trong class file | Dùng từ phiên bản class file chính thức | Ý nghĩa chính |
| --- | --- | --- | --- |
| Module | `Module`, `ModulePackages`, `ModuleMainClass` | **53.0 / Java 9** | Module descriptor và metadata về package/main class |
| Nestmate | `NestHost`, `NestMembers` | **55.0 / Java 11** | Khai báo quan hệ nest để kiểm tra quyền `private` |
| Record | `Record` | **60.0 / Java 16** | Khai báo record class và danh sách record components |
| Sealed class/interface | `PermittedSubclasses` | **61.0 / Java 17** | Liệt kê các loại được phép trực tiếp `extends` / `implements` |

Các mốc trên là **class-file format version mà attribute được định nghĩa chính thức**, không có nghĩa trước đó hoàn toàn không có preview source nào: record và sealed từng trải qua giai đoạn preview ở các JDK trước khi trở thành chuẩn. Preview class file có quy tắc phiên bản/`--enable-preview` riêng; không coi bytecode preview của bản trước tương thích tự động với bản sau.

### 1.2. Attribute và access flag khác nhau

- **Access flag** là bit trong `access_flags`. Ví dụ `ACC_MODULE` nhận diện module descriptor, còn `ACC_FINAL` thể hiện lớp không thể bị kế thừa.
- **Attribute** là cấu trúc có tên, độ dài, payload riêng. `Record` và `PermittedSubclasses` thuộc nhóm này.
- **`InnerClasses`** mô tả thông tin về nested/member classes để các công cụ hiểu cấu trúc nguồn; nó **không thay thế** `NestHost`/`NestMembers` trong quyết định quyền truy cập `private` ở JVM Java 11+.
- `Record` attribute không thay cho `fields[]` và `methods[]`; chúng vẫn tồn tại để mô tả field, constructor và accessor thực tế.

Mỗi attribute có **vị trí được phép xuất hiện**, số lần tối đa và giới hạn tương thích. Không thể chèn tùy tiện một `Module` attribute vào method và mong JVM hiểu đó là module.

## 2. Module attributes: mô tả ranh giới module ở cấp class file

Java Platform Module System (JPMS, Java 9) giới thiệu `module-info.java`. Biên dịch file này tạo **`module-info.class`**, một class file có cấu trúc dành riêng cho module descriptor.

Ví dụ source:

```java
module com.example.catalog {
    requires java.logging;
    exports com.example.catalog.api;
    opens com.example.catalog.internal to com.example.consumer;
    uses com.example.catalog.spi.Provider;
    provides com.example.catalog.spi.Provider
        with com.example.catalog.internal.DefaultProvider;
}
```

Đây là **đoạn minh họa cú pháp module descriptor**, không thể tự biên dịch nếu thiếu các package, kiểu và module được tham chiếu. Ví dụ chạy đầy đủ xuất hiện ở mục 7.

Tương ứng, `Module` attribute ghi những nhóm dữ liệu:

| Thành phần trong `Module` | Vai trò |
| --- | --- |
| `module_name_index`, `module_flags`, `module_version_index` | Tên và metadata của module |
| `requires[]` | Module được yêu cầu, cờ như `transitive`/`static` khi áp dụng, thông tin version tùy chọn |
| `exports[]` | Package được xuất cho module khác, có thể xuất có điều kiện |
| `opens[]` | Package mở cho deep reflection theo điều kiện khai báo |
| `uses[]` | Service interface được khai báo sử dụng |
| `provides[]` | Service và danh sách provider implementations |

Tên module trong `Module` được tham chiếu qua mục `CONSTANT_Module_info` của constant pool; package qua `CONSTANT_Package_info`, khác `CONSTANT_Class_info` thường thấy ở tên lớp. `requires java.logging` là metadata quan hệ module, **không phải một lệnh bytecode gọi logging**.

### 2.1. `ACC_MODULE` và ràng buộc của `module-info.class`

Khi `ACC_MODULE` được bật:

- `this_class` biểu thị tên đặc biệt **`module-info`**.
- Không được đồng thời bật các access flags khác trong `access_flags`.
- `super_class`, số interfaces, fields và methods bằng **0**. `module-info.class` không có constructor Java thông thường, không có phương thức `main()` hay instance.
- Có **đúng một `Module` attribute** theo ràng buộc class file loại module; một số attributes khác được phép, trong đó có `ModulePackages` và `ModuleMainClass`.
- Cần class-file format tối thiểu **53.0** (Java 9).

Vì vậy, `module-info.class` **không phải** `com.example.ModuleInfo` có thể `new` hoặc gọi như class thông thường. JVM/toolchain dùng descriptor đó để hiểu **module**, cùng với quá trình resolution/layer configuration của JPMS.

### 2.2. `ModulePackages` khác `exports` thế nào?

`ModulePackages` ghi **danh sách package thuộc module** theo cơ chế của class-file attribute. Danh sách có thể bao gồm package **không được exports hay opens**, trong khi `Module.exports[]` chỉ nói về package **được phép truy cập theo quan hệ module**.

- `exports p.api`: cho phép truy cập các public types trong package được xuất từ các module đọc được module này.
- `opens p.internal`: mở khả năng deep reflection theo chính sách module, không biến toàn bộ kiểu thành public API compile-time.
- `ModulePackages`: phản ánh membership/package inventory; **không cấp quyền đọc, exports hay opens**.

`ModulePackages` **có thể không xuất hiện** trong `module-info.class` đơn giản do `javac` tạo. Các bước đóng gói/công cụ khác (chẳng hạn công cụ JAR hoặc runtime image) có thể bổ sung metadata cần thiết. **Không kết luận module không có package** chỉ vì không nhìn thấy attribute này.

### 2.3. `ModuleMainClass` có phải `Main-Class` của JAR?

`ModuleMainClass` là class-file attribute chỉ định **main class của module** nếu thông tin này được lưu trong module descriptor. Nó **khác vị trí lưu** với `Main-Class` trong `META-INF/MANIFEST.MF` của JAR truyền thống.

`module-info.java` **không khai báo main class bằng cú pháp `main` của module descriptor**. Thông tin này có thể được công cụ đóng gói bổ sung, chẳng hạn khi tạo modular JAR với tùy chọn main class phù hợp. Khi không có `ModuleMainClass`, một module vẫn có thể chạy bằng cách **chỉ rõ module và class chính**:

```bash
java --module-path out -m com.example.catalog/com.example.catalog.Main
```

Đây là cú pháp minh họa; chương trình chạy hoàn chỉnh với tên module khác được hướng dẫn ở mục 7. Không phải mọi `module-info.class` đều có `ModuleMainClass`, và không nên lẫn metadata class file với tên file/classpath của ứng dụng.

## 3. Nestmate attributes: quyền private của các lớp cùng nest

Trước Java 11, khi một nested class do `javac` biên dịch cần truy cập member `private` của enclosing class, compiler thường phải tạo **synthetic access methods** (ví dụ các bridge mang tên kiểu `access$000`) để bytecode có thể truy cập hợp lệ. Từ Java 11, JVM hiểu **nestmates**: các lớp thuộc **cùng một nest hợp lệ** được quyền truy cập private members của nhau.

### 3.1. Hai attributes bổ trợ nhau

- **`NestMembers`** nằm trong class file của **nest host**, chứa danh sách các lớp/interface được **phép tuyên bố** là thành viên của nest.
- **`NestHost`** nằm trong class file của **thành viên**, chỉ định class làm host.
- Host **tự là một thành viên** của nest do nó quản lý, không phải khai báo nó trong danh sách `NestMembers`.
- Một class file không được chứa đồng thời **`NestHost` và `NestMembers`**.

Ví dụ Java:

```java
public class Vault {
    private int secret = 42;

    static class Reader {
        int read(Vault value) {
            return value.secret;
        }
    }
}
```

Với `javac` Java 17:

```text
Vault.class
  NestMembers:
    Vault$Reader

Vault$Reader.class
  NestHost:
    class Vault
```

Đây là **sơ đồ các dòng liên quan**, không phải toàn bộ output cố định từ `javap -v`. `Reader` có thể truy cập `private int secret` của `Vault` nhờ quan hệ nest được JVM xác nhận, thay vì buộc compiler tạo accessor public/package-visible.

### 3.2. Không chỉ cần tên host trên một phía

JVM không tin vô điều kiện mọi class tự khai `NestHost: SomeHost`. Khi kiểm tra nest membership, các class phải thỏa điều kiện đặc tả như **cùng runtime package** và thông tin host–member được xác thực theo quy tắc `NestHost`/`NestMembers`.

**Runtime package** gắn với tên package **và defining class loader**; hai lớp có cùng dòng `package com.example;` nhưng bị định nghĩa bởi loader khác nhau chưa chắc thuộc cùng runtime package. Nestmate không mở quyền `private` xuyên loader/package một cách tùy ý.

Điều kiện tương trợ này ngăn một class file ngoài ý muốn giả danh là nestmate để đọc private field của class khác. Nếu bị phá vỡ, quá trình access check hoặc class loading/linking có thể thất bại bằng lỗi kiểu như `IllegalAccessError` hoặc `IncompatibleClassChangeError`, tùy trường hợp chính xác.

### 3.3. Khác với `InnerClasses` và `EnclosingMethod`

| Metadata | Câu hỏi nó trả lời |
| --- | --- |
| `InnerClasses` | Kiểu này được khai báo/lưu quan hệ nested/member như thế nào? |
| `EnclosingMethod` | Kiểu local/anonymous này được bao trong phương thức hay constructor nào? |
| `NestHost` / `NestMembers` | Những class nào được JVM công nhận cùng nest để kiểm tra `private` access? |

Vì vậy “class có tên `Outer$Inner`” **không tự động** có mọi quyền private; dấu `$` chỉ là tên được compiler thường sử dụng. Với Java 11+, **nest metadata hợp lệ** mới là nền tảng của quyền truy cập private giữa các class files.

Reflection cung cấp `Class.getNestHost()`, `getNestMembers()`, `isNestmateOf(...)` để quan sát kết quả nhìn từ JVM, không chỉ đọc các chuỗi tên trong source.

## 4. `Record` attribute: metadata của record components

`record` là cấu trúc ngôn ngữ được chuẩn hóa từ **Java 16**. Ví dụ:

```java
public record Point(int x, int y) {}
```

Biên dịch ra `Point.class` vẫn có **class file thông thường** với constructor, các private final instance fields `x`, `y`, accessor methods `x()`, `y()` và các method `equals`, `hashCode`, `toString` theo hợp đồng record.

Điểm đặc biệt là có **`Record` attribute** ở cấp `ClassFile`, biểu diễn danh sách record components. `Record` không chứa “mọi phương thức” hay toàn bộ source record; nó chủ yếu mô tả tên/kiểu và attributes đi kèm component.

### 4.1. Dữ liệu của một `Record` attribute

```text
Record_attribute
├── attribute_name_index
├── attribute_length
├── components_count
└── components[]
    ├── name_index
    ├── descriptor_index
    ├── attributes_count
    └── attributes[]   // ví dụ Signature, annotations...
```

Với `Point(int x, int y)`:

| Component | Name | JVM field descriptor |
| --- | --- | --- |
| 1 | `x` | `I` (int) |
| 2 | `y` | `I` (int) |

Với `record User(String name, long id) {}`, descriptors tương ứng là `Ljava/lang/String;` và `J`. Nếu component có kiểu generic, `Signature` attribute có thể bổ sung thông tin generic bên cạnh descriptor đã erasure.

**Record component** là khái niệm trong header của record. Nó có quan hệ với một field, accessor và constructor parameter tương ứng trong mô hình Java, nhưng **không phải** `field_info` hay `method_info`. Vì vậy Java Reflection có `getRecordComponents()` riêng thay vì buộc suy luận components từ tất cả declared fields.

### 4.2. Không có `ACC_RECORD` trong JVMS Java 17

Trong `access_flags` của class file ở JVMS 17 **không có bit `ACC_RECORD` chính thức**. Class của một Java record thường có `ACC_FINAL` theo quy tắc ngôn ngữ và kế thừa `java.lang.Record`; **`Record` attribute** đánh dấu cấu trúc record và components.

Cần phân biệt:

- `ACC_FINAL` không đủ để kết luận class là record; rất nhiều class final bình thường không phải record.
- Một class kế thừa `java.lang.Record` và có `Record` attribute trong class file phù hợp với cách records được biên dịch; **không suy ra record chỉ từ tên file hoặc từ `final`**.
- Method `Class.isRecord()` và `getRecordComponents()` là API thích hợp để quan sát record metadata tại runtime.
- Record component names/types nằm trong attribute theo hợp đồng class-file, không phụ thuộc vào việc có bật `javac -g` để giữ debug local variables.

### 4.3. Những gì `Record` KHÔNG thay thế

`Record` không “tự chứa bytecode” của canonical constructor, accessor, `equals`, `hashCode` hoặc `toString`. Compiler vẫn sinh method/field structures; JVM runtime/library phối hợp thực thi theo class và bytecode bình thường.

Trên các JDK phổ biến, code do `javac` sinh cho các method record có thể sử dụng `invokedynamic` và `java.lang.runtime.ObjectMethods` để hiện thực `equals`/`hashCode`/`toString`. Đây là **chi tiết codegen có thể quan sát**, không phải quy tắc “`Record` attribute chứa sẵn thuật toán hash”.

Không phải mọi annotation trên component được lưu cùng một vị trí: tùy `@Target`, annotation có thể áp dụng cho record component, field, accessor hoặc constructor parameter theo quy tắc ngôn ngữ. Khi inspect phải xem đúng vị trí metadata.

## 5. `PermittedSubclasses`: sealed class/interface cho phép ai kế thừa?

`sealed` trở thành tính năng chuẩn ở **Java 17**. Ví dụ:

```java
public sealed interface Shape permits Circle, Rectangle {}

final class Circle implements Shape {}

final class Rectangle implements Shape {}
```

Về mặt source, `permits Circle, Rectangle` nói hai loại này có thể **trực tiếp** `implements Shape`. Trong class file của `Shape`, metadata `PermittedSubclasses` chứa các tham chiếu `CONSTANT_Class_info` tương ứng.

```text
Shape.class
  PermittedSubclasses:
    Circle
    Rectangle
```

### 5.1. Không có bit `ACC_SEALED`

JVMS dùng **sự hiện diện của `PermittedSubclasses`** trên class/interface để thể hiện ranh giới sealed. Không có `ACC_SEALED` được định nghĩa như đối ứng trực tiếp của modifier `sealed`.

- `final class`: không cho lớp khác kế thừa, dùng `ACC_FINAL`; không được đồng thời có `PermittedSubclasses`.
- `sealed class/interface`: cho phép một danh sách cụ thể các **direct subclasses/direct subinterfaces**, dùng `PermittedSubclasses`.
- `non-sealed`: thể hiện sự mở rộng trở lại nhánh không còn bị giới hạn bởi parent sealed; **không có bit `ACC_NON_SEALED`**. Runtime biểu diễn các giới hạn và modifier hiện có theo cấu trúc class thông thường.

Một lớp được phép trực tiếp kế thừa sealed parent trong **mã nguồn Java** thường phải khai báo một trong `final`, `sealed` hoặc `non-sealed` (record mặc định final); những quy tắc source và bytecode của class cuối cùng có thể khác cách biểu đạt.

### 5.2. Direct, không phải mọi hậu duệ

Giả sử:

```java
sealed interface Command permits ReadCommand, WriteCommand {}

non-sealed class ReadCommand implements Command {}

final class WriteCommand implements Command {}

final class SpecialRead extends ReadCommand {}
```

`PermittedSubclasses` của `Command` chứa **ReadCommand** và **WriteCommand**, **không cần** chứa `SpecialRead` vì `SpecialRead` chỉ là cháu kế thừa qua một nhánh `non-sealed`.

Nếu một class không có trong `PermittedSubclasses` mà cố gắng **trực tiếp** kế thừa/implement sealed parent, JVM phải từ chối theo cơ chế class derivation/linking. Đó không chỉ là kiểm tra cú pháp của `javac`: metadata giúp ràng buộc cả khi JVM nạp class files đã được biên dịch/chỉnh sửa ngoài bộ compiler thông thường.

### 5.3. Ràng buộc module và package

Đối với source Java:

- Nếu sealed parent thuộc **named module**, các direct permitted subclasses phải ở **cùng module**.
- Nếu sealed parent thuộc **unnamed module**, các direct permitted subclasses phải ở **cùng package**.
- Điều kiện Java source còn yêu cầu các subtypes trực tiếp hợp lệ, accessibility và đặc điểm modifier tương ứng.

Trong runtime, JVM kiểm tra thêm quan hệ trực tiếp, tính hợp lệ của class-loader/runtime-module/package theo JVMS. Không thể chỉ chép tên vào `PermittedSubclasses` để vượt qua ranh giới module/package hoặc private access.

### 5.4. Reflection

Với một sealed class/interface, `Class.isSealed()` cho biết tình trạng sealed; `Class.getPermittedSubclasses()` trả mảng `Class<?>` cho các lớp được cho phép theo runtime. Đây không phải danh sách mọi đối tượng hoặc mọi subclass gián tiếp trong heap.

**Thứ tự duyệt mảng** có thể phản ánh metadata/runtime, nhưng bài học không nên dùng thứ tự như một quy tắc sắp xếp nghiệp vụ. Nếu cần kết quả ổn định theo tên, ứng dụng phải tự sắp xếp.

## 6. Một source Java 17 minh họa nestmate + record + sealed

Tạo tệp **`ClassAttributeDemo.java`**:

```java
public class ClassAttributeDemo {

    private final String secret = "OK";

    static class Reader {
        static String read(ClassAttributeDemo owner) {
            return owner.secret;
        }
    }

    sealed interface Shape permits Point, Circle {}

    record Point(int x, int y) implements Shape {}

    record Circle(int radius) implements Shape {}

    public static void main(String[] args) {
        ClassAttributeDemo owner = new ClassAttributeDemo();
        Point point = new Point(2, 3);

        System.out.println("nestmate=" + Reader.read(owner));
        System.out.println("record=" + point);
        System.out.println("record-components=" +
                Point.class.getRecordComponents().length);
        System.out.println("sealed=" + Shape.class.isSealed());
        System.out.println("permitted=" +
                Shape.class.getPermittedSubclasses().length);
    }
}
```

Biên dịch/chạy với **JDK 17+**:

```bash
javac ClassAttributeDemo.java
java ClassAttributeDemo
```

Stdout chính xác:

```text
nestmate=OK
record=Point[x=2, y=3]
record-components=2
sealed=true
permitted=2
```

**Vì sao đúng?**

1. `Reader` là nested class, có thể đọc `private String secret` của enclosing class nhờ quan hệ nestmate.
2. `Point` là record, có hai components `x` và `y`; mặc định `toString()` biểu diễn chúng dưới dạng `Point[x=2, y=3]`.
3. `Shape` là sealed interface; danh sách hai direct permitted implementations là `Point` và `Circle`.
4. Hai record implementations mặc định final theo source record, phù hợp quy tắc direct subclass của sealed parent.
5. Ví dụ không dùng module descriptor; ba nhóm metadata nest/record/sealed có thể xuất hiện trong class files trên **classpath**, độc lập với việc chương trình có named module hay không.

### 6.1. Các class files được tạo

Một compiler `javac` theo cách thường gặp sẽ tạo:

```text
ClassAttributeDemo.class
ClassAttributeDemo$Reader.class
ClassAttributeDemo$Shape.class
ClassAttributeDemo$Point.class
ClassAttributeDemo$Circle.class
```

Dấu `$` là ký pháp tên nhị phân thông thường cho nested types do compiler tạo, không phải một attribute. File cụ thể tùy compiler, và thông tin thuộc tính quan trọng nằm **bên trong** các file.

Đọc metadata:

```bash
javap -v -p ClassAttributeDemo
javap -v -p 'ClassAttributeDemo$Reader'
javap -v -p 'ClassAttributeDemo$Point'
javap -v -p 'ClassAttributeDemo$Shape'
```

Trên shell PowerShell hoặc POSIX, dấu nháy đơn bảo vệ `$` khỏi biến shell. Trong một số môi trường, `javap` chấp nhận cả tên nhị phân và đường dẫn file `.class`. Ở đây đã dùng tên nhị phân, bao gồm dấu `$`.

Dự kiến tìm:

| Inspect | Metadata quan trọng |
| --- | --- |
| `ClassAttributeDemo` | `NestMembers` liệt kê nested types do compiler nạp vào nest |
| `ClassAttributeDemo$Reader` | `NestHost: ClassAttributeDemo` |
| `ClassAttributeDemo$Point` | `Record` chứa components `x:I` và `y:I`; `NestHost` cho nested type |
| `ClassAttributeDemo$Shape` | `PermittedSubclasses` liệt kê `...$Point` và `...$Circle`; cũng có `NestHost` |
| `ClassAttributeDemo$Circle` | `Record` có component `radius:I` |

`javap` có thể bổ sung constant pool, `InnerClasses`, method code, bootstrap metadata, `SourceFile` và nhiều thông tin khác. **Không đòi output chỉ chứa những dòng liệt kê ở bảng**; đây là trọng tâm cần tìm.

### 6.2. Dòng metadata và runtime liên hệ thế nào?

```text
Mã nguồn Java 17
  │
  ├─ class Outer + nested Reader
  │     └─ ClassFile: NestMembers / NestHost
  │               └─ JVM dùng để xét private access
  │
  ├─ record Point
  │     └─ ClassFile: Record (component names + descriptors)
  │               └─ Reflection đọc getRecordComponents()
  │
  └─ sealed interface Shape
        └─ ClassFile: PermittedSubclasses
                  └─ JVM kiểm tra direct subtype được phép
```

Một class file có thể có **nhiều attributes cùng lúc**. `Point` vừa là **record** vừa là **nest member**, và là direct subtype được liệt kê trong `Shape`. `PermittedSubclasses` nằm tại **parent `Shape`**, không phải tại từng record implementer chỉ vì nó được phép.

## 7. Ví dụ Java 17 độc lập: tạo `module-info.class`

Để thấy nhóm `Module*` rõ ràng, dùng dự án nhỏ riêng có hai file:

```text
src/
├── module-info.java
└── demo/
    └── api/
        └── Hello.java
```

**`src/module-info.java`**:

```java
module demo.catalog {
    exports demo.api;
}
```

**`src/demo/api/Hello.java`**:

```java
package demo.api;

public class Hello {
    public static void main(String[] args) {
        System.out.println("module=" + Hello.class.getModule().getName());
    }
}
```

Ở thư mục chứa `src`, dùng JDK 17+:

```bash
javac -d out src/module-info.java src/demo/api/Hello.java
java --module-path out -m demo.catalog/demo.api.Hello
javap -v out/module-info.class
```

Stdout từ **lệnh `java`**:

```text
module=demo.catalog
```

Lệnh `javap` sinh output phân tích class file, trong đó tìm `ACC_MODULE`, `Module`, `module-info`, phần export `demo.api` và phụ thuộc `java.base` được compiler/module descriptor phản ánh. Hai lệnh `java` và `javap` không có “stdout chung” cố định, nên ở trên chỉ ghi rõ kết quả của lệnh chạy ứng dụng.

**Giải thích chi tiết:**

- `javac` sinh `out/module-info.class` và `out/demo/api/Hello.class`.
- `module-info.class` mang thông tin module `demo.catalog` với package được export là `demo.api`.
- `Hello.class` là class thông thường chứa method `main`; `module-info.class` không chứa chương trình chạy.
- `Hello.class.getModule().getName()` phản ánh module runtime của class, trả `demo.catalog` trong cách chạy này.
- Không bắt buộc có `ModuleMainClass`: lệnh `java -m demo.catalog/demo.api.Hello` đã chỉ rõ tên main class.
- Nếu `javap -v out/module-info.class` không hiển thị `ModulePackages`, điều đó không có nghĩa module không có package; attribute này có thể được công cụ đóng gói bổ sung.

Ví dụ này không cần Maven/Gradle, module path chỉ trỏ tới **thư mục output** của một module đã được biên dịch. Với ứng dụng nhiều module, bố cục thư mục/module path và các phần `requires` sẽ phức tạp hơn.

## 8. Đọc class file và nhận biết từng nhóm bằng `javap`

`javap` là công cụ đi kèm JDK, cho phép hiển thị thuộc tính theo dạng text. Một quy trình thực tế:

1. Xác định **đúng file `.class`** mà JVM/toolchain sẽ dùng. Trong JAR có thể có nhiều bản class theo `META-INF/versions/`.
2. Chạy `javap -v -p` cho class cụ thể. `-v` in thêm các attributes, `-p` gồm private members.
3. Kiểm tra `major version` và `minor version` để biết release class file. Class có `Record` được định nghĩa chuẩn từ 60.0, `PermittedSubclasses` từ 61.0.
4. Phân biệt **name của attribute** và **dữ liệu tham chiếu trong constant pool**: nhiều `#123` là index, không phải ID ổn định giữa hai lần biên dịch.
5. Nếu cần kiểm chứng runtime, dùng Reflection hoặc thử nạp/chạy chương trình trên JDK hỗ trợ; class file chỉ là một phần của hành vi thực tế.

Ví dụ đọc output rút gọn:

```text
ClassAttributeDemo$Shape
  major version: 61
  ...
  NestHost: class ClassAttributeDemo
  PermittedSubclasses:
    ClassAttributeDemo$Point
    ClassAttributeDemo$Circle
```

Đây là **trích xuất minh họa** để chỉ cách tìm thuộc tính, không cam kết thứ tự/indent/khoảng trắng chính xác trong mọi bản `javap`.

### 8.1. Cẩn thận khi tìm bằng chuỗi

Nếu `javap` chỉ in chữ “Record” trong **constant pool**, đó chưa chắc là bằng chứng đã có **`Record` attribute** ở cấp class; constant pool chứa rất nhiều chuỗi và tên dùng bởi code. Hãy xác nhận nó xuất hiện trong **attributes section** tương ứng và có cấu trúc components.

Tương tự:

- Chữ `sealed` trong source không tương đương một access flag có tên `ACC_SEALED`.
- Một `ClassFile` có `InnerClasses` không tự đảm bảo nest access.
- Tên `Module` xuất hiện như tên Java package/identifier trong bytecode không có nghĩa file là `module-info.class`.
- `Record` không phải “record object lưu trong constant pool”; nó là metadata cấp class.

## 9. Tương thích JDK và lỗi hay gặp

| Tình huống | Lý do | Cách chẩn đoán |
| --- | --- | --- |
| Chạy class Java 17 (`major 61`) trên JVM Java 11 (đến 55) | Class-file version quá mới | `java -version`, `javap -v`, `UnsupportedClassVersionError` |
| Chạy class record được biên dịch Java 16+ trên JDK cũ | Thiếu hỗ trợ định dạng/language/runtime cho record | Kiểm tra major, toolchain và `java.lang.Record` |
| Class file dùng `PermittedSubclasses` sai/không hợp lệ | JVMS yêu cầu cấu trúc attribute và direct subtype hợp lệ | `javap -v`, class generation, lỗi verification/linkage |
| Nested class truy cập `private` nhưng nest metadata không khớp | Nest membership không được JVM xác nhận | Kiểm tra `NestHost`, `NestMembers`, runtime package/loader |
| Một class file vừa có `NestHost` và `NestMembers` | Vi phạm ràng buộc định dạng | Sửa class generator/instrumentation |
| `module-info.class` chứa method/field bình thường | Vi phạm ràng buộc `ACC_MODULE` | Sửa compiler/generator; inspect `module-info.class` |
| Không thấy `ModulePackages` sau `javac` | Đây có thể là attribute tùy chọn do packaging bổ sung | Kiểm tra metadata cuối cùng trong JAR/image |
| Reflection thấy record components nhưng code không tìm field bằng tên dự đoán | Component, field và method là các cấu trúc khác nhau | Dùng `getRecordComponents()`; inspect class fields/methods |
| `Class.isSealed()` đúng nhưng danh sách subtype không đúng kỳ vọng | Metadata lưu **direct permitted subclasses**, không phải mọi hậu duệ | Inspect `PermittedSubclasses` ở parent |

**Ranh giới quan trọng:** các attributes này giúp JVM hiểu class, nhưng không làm một JDK cũ đột nhiên hiểu cú pháp hoặc class format mới. Một chương trình cần support Java 11 không thể giữ nguyên bytecode record/sealed Java 17 rồi chỉ thay cờ lúc chạy. Muốn hỗ trợ runtime cũ, phải thay đổi cách triển khai và biên dịch phù hợp.

### 9.1. Attributes chưa được runtime cũ biết đến

JVMS có quy tắc với các attributes định nghĩa mới: JVM nhận biết các predefined attributes theo version mà nó hỗ trợ. Tuy nhiên **không được suy ra** JVM cũ sẽ an toàn bỏ qua class mới chỉ vì attribute lạ: nó còn kiểm tra **major version**, cấu trúc class và các ràng buộc khác. Với class format mới, lỗi version thường xảy ra **trước khi** chương trình có thể tiếp tục xử lý metadata.

Vì vậy không có quy trình hợp lệ kiểu “xóa dòng `Record` khỏi file Java 17 rồi đổi major thành 55 để chạy Java 11”. Chỉnh bytecode nhị phân thủ công có thể phá hỏng constant pool, constructor contract, references và những yếu tố khác.

## 10. Các lỗi hiểu sai phổ biến

1. **“`record` được biểu diễn bằng flag `ACC_RECORD`.”** Không có flag chuẩn như vậy trong JVMS 17; `Record` là class-level attribute.
2. **“`sealed` được biểu diễn bằng flag `ACC_SEALED`.”** Không; `PermittedSubclasses` ở sealed parent xác định tập direct permitted subtypes.
3. **“Mọi nested class đều là nestmate chỉ vì tên có dấu `$`.”** Không; runtime áp dụng kiểm tra nest theo metadata và các điều kiện package/loader.
4. **“`NestMembers` cho phép class kế thừa nhau.”** Không; nó phục vụ quan hệ nest/private access, không phải inheritance.
5. **“`PermittedSubclasses` liệt kê tất cả descendants.”** Không; danh sách là direct subtypes được phép, nhánh `non-sealed` có thể có hậu duệ tiếp.
6. **“`Record` chứa bytecode của `equals`, `hashCode` và `toString`.”** Không; methods và code nằm ở cấu trúc phương thức/implementation.
7. **“`ModulePackages` là danh sách mọi package được export.”** Không; nó có thể gồm cả package nội bộ không export.
8. **“`ModuleMainClass` phải có mới chạy được module.”** Không; Java launcher có thể nhận main class tường minh.
9. **“`module-info.class` là một class thường có thể tạo object.”** Không; `ACC_MODULE` đi cùng các ràng buộc cấu trúc đặc biệt.
10. **“Nếu JVM thấy tên attribute mới thì chắc chắn sẽ nạp được bytecode mới.”** Không; major/minor và toàn bộ cấu trúc/đặc tả vẫn phải tương thích.
11. **“`InnerClasses` là cơ chế duy nhất cho truy cập private của nested class trong Java 17.”** Không; `NestHost`/`NestMembers` là nền tảng kiểm tra nestmate.
12. **“Mọi class file của một Java program đều có đủ bốn nhóm thuộc tính.”** Không; chỉ có metadata áp dụng cho cấu trúc đã biên dịch.

## 11. Bài tập thực hành và đáp án

**Câu 1.** `module-info.class` báo flag `ACC_MODULE` và có `Module` attribute. Nó có thể chứa `public static void main` không?

**Đáp án:** Không. Class file module descriptor có cấu trúc đặc biệt với `methods_count = 0`. Main class của ứng dụng, nếu có, là class khác; metadata `ModuleMainClass` cũng không tạo method `main`.

**Câu 2.** `Outer$Inner.class` có `NestHost: Outer`, nhưng class `Outer` không chấp nhận thành viên đó. Có được tự động truy cập `private` của `Outer` không?

**Đáp án:** Không. JVM cần xác thực quan hệ nest hợp lệ (kèm điều kiện package/loader). Một khai báo một phía không trao quyền private tùy ý.

**Câu 3.** Trong `record Book(String name, int pages) {}`, `Record` attribute có bao nhiêu components và descriptors nào?

**Đáp án:** Hai components: `name` có descriptor `Ljava/lang/String;`, `pages` có descriptor `I`. Fields/accessors tương ứng được mô tả ở những cấu trúc class-file khác.

**Câu 4.** Sealed interface `Shape permits Circle, Rectangle` có lớp `SpecialCircle extends Circle` ở nhánh `non-sealed Circle`. `PermittedSubclasses` của `Shape` có bắt buộc liệt kê `SpecialCircle` không?

**Đáp án:** Không. Chỉ hai direct permitted implementations `Circle` và `Rectangle`; `SpecialCircle` không phải direct subtype của `Shape`.

**Câu 5.** Nếu cần đọc danh sách record components qua Reflection, chọn `getDeclaredFields()` hay `getRecordComponents()`?

**Đáp án:** Dùng `getRecordComponents()` để lấy **record components**. `getDeclaredFields()` trả field declarations, một khái niệm khác.

**Câu 6.** Tại sao file có `major version: 55` không thể dựa vào `PermittedSubclasses` chuẩn Java 17 để đảm bảo sealed enforcement?

**Đáp án:** `PermittedSubclasses` được định nghĩa chính thức từ class-file version **61.0** (Java 17). JVM hiểu major 55 (Java 11) không có hợp đồng runtime sealed tương ứng.

**Câu 7.** Một `module-info.class` không có `ModulePackages` nhưng có `Module.exports[]`. Có phải lỗi biên dịch không?

**Đáp án:** Không nhất thiết. `ModulePackages` là metadata bổ sung có thể xuất hiện do packaging; `Module` có thể chứa thông tin exports dù không có `ModulePackages` trong file vừa biên dịch.

## 12. Ghi nhớ

- **`Module`** mô tả quan hệ module (requires/exports/opens/uses/provides), còn **`ModulePackages`** và **`ModuleMainClass`** là metadata module bổ sung. Chúng nằm trong class-file descriptor `module-info.class` với **`ACC_MODULE`**.
- **`NestHost` / `NestMembers`** giúp JVM xác nhận nestmates và cho phép truy cập private có kiểm soát; không thay thế logic kế thừa và không tự phát sinh từ tên `$`.
- **`Record`** lưu record components và thông tin liên quan; record vẫn có fields/methods/constructors thực sự. **Không có `ACC_RECORD` chuẩn ở JVMS 17**.
- **`PermittedSubclasses`** cho phép JVM xác minh **direct subtype** của sealed class/interface; **không có `ACC_SEALED`**.
- Đọc đúng cả **class-file version, constant pool, access flags và class-level attributes**, rồi xác nhận hành vi bằng `javap`/Reflection. Đó là cách nối các modifier Java hiện đại với ràng buộc mà JVM thực sự hiểu.

### Tài liệu tham khảo

- [JVMS Java SE 17 — ClassFile structure (`4.1`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.1).
- [JVMS Java SE 17 — Predefined attributes (`4.7`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7).
- [JVMS Java SE 17 — Module (`4.7.25`), ModulePackages (`4.7.26), ModuleMainClass (`4.7.27)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.25).
- [JVMS Java SE 17 — NestHost (`4.7.28), NestMembers (`4.7.29)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.28).
- [JVMS Java SE 17 — Record (`4.7.30`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.30).
- [JVMS Java SE 17 — PermittedSubclasses (`4.7.31`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.31).
- [JEP 181 — Nest-Based Access Control](https://openjdk.org/jeps/181).
- [JEP 395 — Records](https://openjdk.org/jeps/395).
- [JEP 409 — Sealed Classes](https://openjdk.org/jeps/409).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-file-format-011">Giới hạn kích thước phương thức, constant pool và các ràng buộc định dạng</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn kích thước phương thức, constant pool và các ràng buộc định dạng tệp class

## 1. Mục tiêu

Tệp **.class** có một cấu trúc nhị phân cụ thể, không phải một kho dữ liệu có thể mở rộng vô hạn. Nhiều trường trong định dạng này dùng số nguyên không dấu 8, 16 hoặc 32 bit; kích thước của chúng và những quy tắc kiểm tra hợp lệ tạo ra giới hạn đối với bytecode, constant pool, phương thức, tham số và metadata. Các giới hạn này ảnh hưởng rõ nhất đến code generation, annotation processing, compiler, proxy, bytecode instrumentation và các lớp Java quá lớn do công cụ sinh ra.

Sau bài học, bạn có thể:

- Giải thích vì sao mã bytecode của **một phương thức có Code attribute** không được vượt **65.535 byte**.
- Đọc chính xác ý nghĩa **constant_pool_count**, phân biệt số slot, số entry thực và số literal trong mã nguồn.
- Phân biệt **giới hạn của một chuỗi CONSTANT_Utf8** theo số byte Modified UTF-8 với giới hạn ký tự Java.
- Nhận biết giới hạn tham số phương thức, local variable slots, operand stack, số field/method và số chiều mảng.
- Phân biệt lỗi do **định dạng class file**, lỗi **xác minh bytecode**, lỗi **liên kết** và giới hạn tài nguyên thực tế.
- Dùng **javac**, **javap** và chương trình Java 17+ để kiểm tra class header, constant pool và dữ liệu liên quan.
- Biết cách chia nhỏ code sinh tự động khi chạm giới hạn, đồng thời không nhầm “chia phương thức” với “giảm constant pool”.

## 2. Tại sao cần nhìn cấu trúc class file?

Trong Java source, một lớp có thể chứa rất nhiều dòng, nhiều biểu thức hoặc hằng chuỗi. Nhưng JVM không chạy trực tiếp văn bản Java; trình biên dịch tạo cấu trúc **ClassFile** gồm header, constant pool, khai báo field, khai báo method, attributes và nhiều tham chiếu bằng chỉ số.

Sơ đồ rút gọn:

~~~text
ClassFile
  u4 magic                      = 0xCAFEBABE
  u2 minor_version
  u2 major_version
  u2 constant_pool_count
  cp_info constant_pool[constant_pool_count - 1]
  u2 access_flags
  u2 this_class
  u2 super_class
  u2 interfaces_count
  u2 interfaces[interfaces_count]
  u2 fields_count
  field_info fields[fields_count]
  u2 methods_count
  method_info methods[methods_count]
  u2 attributes_count
  attribute_info attributes[attributes_count]
~~~

Các ký hiệu số nguyên không dấu:

| Ký hiệu | Số byte | Miền số có thể mã hóa | Ví dụ sử dụng |
| --- | ---: | ---: | --- |
| **u1** | 1 | 0–255 | Constant-pool tag, opcode, số chiều trong instruction **multianewarray** |
| **u2** | 2 | 0–65535 | **constant_pool_count**, **methods_count**, **max_locals**, nhiều chỉ số |
| **u4** | 4 | 0–4294967295 | **magic**, **attribute_length**, **code_length** ở mức khai báo trường |

**Điểm mấu chốt:** miền số của trường nhị phân là **điều kiện cần**, chưa đủ để kết luận mọi giá trị đó đều hợp lệ. Chẳng hạn **code_length** được lưu bằng **u4**, nhưng JVMS quy định nó phải **lớn hơn 0 và nhỏ hơn 65536**. Vì vậy không thể dùng toàn bộ miền u4 cho phần instruction của một phương thức.

Class file còn có quy tắc kiểm tra phiên bản, index, kiểu entry, cú pháp tên/descriptor, vị trí attributes, bytecode instruction, control flow và stack types. Một tệp có đúng magic và các độ dài nằm trong giới hạn vẫn có thể không hợp lệ.

## 3. Giới hạn code của một phương thức: 65.535 byte

Mã thực thi của phương thức thông thường nằm trong **Code attribute** của **method_info**:

~~~text
Code_attribute {
  u2 attribute_name_index;
  u4 attribute_length;
  u2 max_stack;
  u2 max_locals;
  u4 code_length;
  u1 code[code_length];
  u2 exception_table_length;
  exception_table_entry exception_table[exception_table_length];
  u2 attributes_count;
  attribute_info attributes[attributes_count];
}
~~~

Theo **JVMS §4.7.3**:

- **code_length** là số byte của mảng **code[]**.
- **code_length phải từ 1 đến 65.535 byte**, tính cả operand bytes, không chỉ tính một byte opcode cho mỗi instruction.
- **Code attribute** của phương thức có thân Java/bytecode thông thường chứa đúng một phần mã theo cấu trúc được yêu cầu. Phương thức **abstract** và **native** không có Code attribute.
- **attribute_length** là kích thước payload attribute, không phải số byte instructions; nó còn bao gồm các trường **max_stack**, **max_locals**, exception table và nested attributes.
- **LineNumberTable**, **LocalVariableTable**, **StackMapTable**... là metadata nằm trong/đi kèm Code attribute theo cấu trúc quy định; chúng không được tính vào **code_length** của mảng instructions.

### 3.1. Giới hạn theo bytecode, không phải số dòng Java

Ví dụ hai phương thức cùng có 100 dòng source vẫn có thể tạo ra số byte instruction rất khác nhau:

- Một phép gán hằng ngắn có thể dùng instruction 1 byte.
- Lệnh nạp một constant-pool reference có thể cần nhiều byte.
- **invokevirtual**, **invokestatic**, **new**, **getfield**, **putfield**, **ldc_w**... có opcode và operand chiếm nhiều byte.
- **tableswitch/lookupswitch** có phần căn chỉnh/padding và dữ liệu nhánh khiến kích thước tăng nhanh.
- Lambda, string concatenation, try/finally, switch, pattern matching hoặc compiler-generated code có thể đưa thêm instructions và metadata.

Do đó, đếm dòng **.java** không cho biết trực tiếp có vượt giới hạn **code_length** hay không. Một method source rất dài đôi khi biên dịch được; một method được sinh bằng hàng ngàn nhánh/statement có thể gặp lỗi “**code too large**”.

### 3.2. 65.535 byte là giới hạn cho một method, không phải cả class

Một class file có thể chứa nhiều phương thức, mỗi phương thức có Code attribute riêng. Giới hạn **code_length** áp dụng độc lập cho từng phương thức có mã. Nó **không có nghĩa** cả tệp **.class** chỉ được 64 KiB hoặc JVM không thể chứa một phương thức dài hơn 64 KiB mã máy JIT.

So sánh:

| Đối tượng | Giới hạn liên quan |
| --- | --- |
| Bytecode instructions của **một** method | **1–65.535 byte** trong **Code.code[]** |
| Toàn bộ **Code attribute** | Có phần header, exception table, nested attributes; không đồng nhất với **code_length** |
| Toàn bộ tệp **.class** | Không có giới hạn chung “64 KiB” từ **code_length** |
| Mã máy sau JIT | Chi tiết của runtime/JIT, không bị đồng nhất với trường **Code.code_length** |

Một method lớn hơn ngưỡng không được “cứu” bằng cách tăng **-Xmx**; đây là giới hạn của định dạng bytecode, không phải lỗi thiếu heap.

### 3.3. Exception handler ở biên code_length

Exception table dùng **start_pc**, **end_pc** và **handler_pc** kiểu **u2**; khoảng được bảo vệ là **[start_pc, end_pc)**, tức gồm đầu và không gồm cuối. **end_pc** có thể bằng **code_length**.

Vì **code_length** tối đa 65.535 và instruction được đánh chỉ số byte từ 0, handler ở sát biên có thể gặp ràng buộc khó xử lý khi đoạn try phải bao trùm instruction cuối cùng. JVMS ghi nhận giới hạn lịch sử của việc dùng exclusive **end_pc**; trong thực hành công cụ sinh bytecode cần kiểm tra kỹ vùng handler, target offset và các mốc 65.535/65.536 thay vì chỉ đảm bảo tổng số byte không vượt ngưỡng.

## 4. Constant pool: giới hạn slot và ý nghĩa của count

Mỗi class/interface có một **constant pool** chứa các cấu trúc biểu diễn tên class, kiểu, chuỗi, số, tham chiếu field/method, dynamic constants, invokedynamic, method handle và những metadata có liên quan. Chúng không đơn giản là danh sách các literal được khai báo trong code nguồn.

Trong class header:

~~~text
u2 constant_pool_count;
cp_info constant_pool[constant_pool_count - 1];
~~~

Quy tắc:

- **constant_pool_count** lưu bằng **u2**, vì vậy giá trị lớn nhất có thể biểu diễn là **65.535**.
- Chỉ số hợp lệ của các entry thông thường nằm trong khoảng **1 đến constant_pool_count - 1**.
- **Index 0 không đại diện cho một constant-pool entry thực**; nó có ý nghĩa riêng ở vài cấu trúc tham chiếu.
- Nếu **constant_pool_count = 65.535**, các vị trí có thể đánh chỉ số trong bảng là **1...65.534**, tức nhiều nhất **65.534 slot sau vị trí 0**.
- Entry **CONSTANT_Long** và **CONSTANT_Double** chiếm **hai vị trí liên tiếp** trong cách đánh chỉ số constant pool, dù cấu trúc dữ liệu chỉ chiếm một cp_info thực. Vị trí ngay sau entry này bị dành riêng, không phải entry độc lập có thể truy cập.

Đây là khác biệt quan trọng giữa câu mô tả ngắn “giới hạn constant pool là 65.535” (giới hạn trường count) và phép tính chính xác **65.534 vị trí có chỉ số**, trong đó một số vị trí có thể là slot dự trữ sau long/double.

### 4.1. Ví dụ đánh số và long/double

Giả sử một phần bảng được ghi như sau:

~~~text
#1  Utf8          "Example"
#2  Class         #1
#3  Utf8          "answer"
#4  Integer       42
#5  Long          9000000000
#6  (reserved by Long entry at #5)
#7  Utf8          "main"
...
~~~

Entry **#5** chiếm hai slot **#5 và #6**, nên entry tiếp theo là **#7**. Một parser class file phải tăng chỉ số thêm hai sau **CONSTANT_Long**/**CONSTANT_Double**, nếu không mọi index tiếp theo sẽ bị lệch.

**Không** cộng thêm một entry thực cho slot dự trữ khi thống kê số cp_info được mã hóa trên byte stream; nhưng phải tính nó khi kiểm tra số slot/chỉ số.

### 4.2. Constant pool không đếm số lần dùng hằng trong mã nguồn

Một chuỗi literal xuất hiện nhiều lần có thể tái dùng constant-pool entry tương ứng. Ngược lại, một lời gọi method có thể cần nhiều entry: ví dụ **Class**, **Utf8** tên class, **NameAndType**, **Utf8** tên method, **Utf8** descriptor và **Methodref**. Chúng có thể tái sử dụng nhau hoặc được compiler/tool tạo mới tùy trường hợp.

Vì vậy:

- “100.000 lần dùng một literal” không nhất thiết tạo 100.000 constant-pool entries.
- “10.000 dòng code” không cho biết có bao nhiêu constant-pool slots.
- Một class sinh ra chứa rất nhiều **field/method names**, **method descriptors**, **literals** hoặc **dynamic call sites** có thể tràn constant pool dù mỗi method riêng lẻ ngắn.
- Việc **chia một method thành nhiều method trong cùng một class** giúp giảm **code_length** cho từng method, nhưng **không bảo đảm** constant pool của **class** nhỏ đi; thậm chí có thể cần thêm tên và Methodref.

### 4.3. Những loại entry tiêu biểu

| Constant-pool tag | Nội dung chính | Chiếm slot |
| --- | --- | ---: |
| **CONSTANT_Utf8** (1) | Tên, descriptor, văn bản metadata theo Modified UTF-8 | 1 |
| **CONSTANT_Integer** (3), **Float** (4) | Hằng số tương ứng | 1 |
| **CONSTANT_Long** (5), **Double** (6) | Hằng số 64 bit | **2** |
| **CONSTANT_Class** (7) | Tham chiếu tên class/interface hoặc mảng theo quy tắc class file | 1 |
| **CONSTANT_String** (8) | Tham chiếu literal String qua Utf8 entry | 1 |
| **CONSTANT_Fieldref** (9) | Symbolic reference tới field | 1 |
| **CONSTANT_Methodref** (10) | Symbolic reference tới class method | 1 |
| **CONSTANT_InterfaceMethodref** (11) | Symbolic reference tới interface method | 1 |
| **CONSTANT_NameAndType** (12) | Ghép tên và descriptor | 1 |
| **CONSTANT_MethodHandle** (15), **MethodType** (16) | Thành phần method handle và method type | 1 |
| **CONSTANT_Dynamic** (17), **InvokeDynamic** (18) | Dynamic constant / call site có bootstrap | 1 |

Một “slot” trong bảng không có nghĩa các entry có cùng kích thước byte trên disk. **Utf8** có độ dài thay đổi; **Long** dùng hai slot index nhưng byte payload thuộc một entry, và **Class/NameAndType** còn tham chiếu những entry khác.

## 5. CONSTANT_Utf8: giới hạn 65.535 **byte**, không phải 65.535 ký tự

Trong constant pool, tên field/method, descriptor, string text và nhiều metadata được lưu bằng **CONSTANT_Utf8_info**:

~~~text
CONSTANT_Utf8_info {
  u1 tag;         // 1
  u2 length;      // số byte của bytes[], không tính tag và length
  u1 bytes[length];
}
~~~

Điều quan trọng:

- **length tối đa 65.535 byte** do lưu bằng **u2**.
- Nội dung dùng **Modified UTF-8 (MUTF-8)** của class file.
- Dữ liệu **không** kết thúc bằng byte NUL.
- Ký tự Unicode có thể sử dụng nhiều byte; số ký tự Java, số Unicode code points và số byte MUTF-8 là các phép đếm khác nhau.
- Giới hạn áp dụng cho **mỗi entry CONSTANT_Utf8**, không phải tổng kích thước mọi chuỗi trong class.

### 5.1. Modified UTF-8 khác UTF-8 thông thường ở đâu?

Ví dụ:

| Giá trị chuỗi | Độ dài Java UTF-16 code units | Số byte MUTF-8 |
| --- | ---: | ---: |
| **A** | 1 | 1 |
| **é** | 1 | 2 |
| **中** | 1 | 3 |
| Ký tự **U+0000** | 1 | 2 |
| **😀** (ký tự ngoài BMP) | 2 | **6** |

Với U+0000, MUTF-8 mã hóa thành hai byte **C0 80**, không đặt một byte 0 trong dữ liệu. Với emoji ngoài Basic Multilingual Plane, MUTF-8 dùng hai mã UTF-16 surrogate, mỗi mã chiếm ba byte; khác UTF-8 thông thường vốn có thể mã hóa emoji thành bốn byte.

Hệ quả:

- 65.535 chữ **A** có thể vừa 65.535 byte MUTF-8, nhưng một ký tự thêm sẽ vượt giới hạn của một Utf8 entry.
- 32.768 ký tự **é** chiếm 65.536 byte MUTF-8, vượt giới hạn dù số phần tử UTF-16 mới là 32.768.
- Một **String** runtime có thể dài hơn giới hạn một CONSTANT_Utf8 entry vì được tạo động, ghép từ nhiều hằng hoặc đọc từ dữ liệu ngoài; đừng nhầm giới hạn lưu một literal trong class file với giới hạn toàn bộ String trên heap.

### 5.2. Đừng tự động dùng UTF-8 chuẩn để dự đoán độ dài

Một generator có thể sai khi:

1. Đếm **String.length()** rồi kết luận đó là số byte trong CONSTANT_Utf8.
2. Dùng UTF-8 tiêu chuẩn của **String.getBytes(StandardCharsets.UTF_8)** để đo hằng chứa NUL hoặc emoji.
3. Dùng số code point mà bỏ qua surrogate pairs.
4. Cắt chuỗi MUTF-8 giữa một mã được mã hóa nhiều byte để vừa giới hạn.

Cần dùng bộ mã hóa MUTF-8 đúng contract. **DataOutputStream.writeUTF** trong Java SE là công cụ minh họa thuận tiện vì nó ghi một trường độ dài **u2** rồi chuỗi theo Modified UTF-8. Cấu trúc tổng thể **không phải toàn bộ CONSTANT_Utf8_info** vì constant pool còn có **tag = 1**, nhưng cách tính byte và giới hạn length là tương ứng.

## 6. Ví dụ thực hành 1: kiểm tra Modified UTF-8

Tạo tệp **ModifiedUtfLimitDemo.java**:

~~~java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;

public class ModifiedUtfLimitDemo {
    static int encodedLength(String value) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeUTF(value);
        }
        byte[] bytes = buffer.toByteArray();
        return ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
    }

    public static void main(String[] args) throws IOException {
        System.out.println("ascii=" + encodedLength("A".repeat(65535)));
        System.out.println("nul=" + encodedLength(String.valueOf((char) 0)));
        System.out.println("emoji=" + encodedLength("😀"));

        try {
            encodedLength("A".repeat(65536));
            System.out.println("tooLong=unexpected");
        } catch (UTFDataFormatException expected) {
            System.out.println("tooLong=UTFDataFormatException");
        }
    }
}
~~~

Biên dịch/chạy bằng **JDK 17+**:

~~~bash
javac -encoding UTF-8 ModifiedUtfLimitDemo.java
java ModifiedUtfLimitDemo
~~~

**Stdout:**

~~~text
ascii=65535
nul=2
emoji=6
tooLong=UTFDataFormatException
~~~

Giải thích: **DataOutputStream.writeUTF** ghi hai byte length theo thứ tự big-endian rồi chuỗi MUTF-8. Chương trình đọc hai byte này như số nguyên không dấu. Với **65536** chữ A, số byte không còn vừa **u2** nên API ném **UTFDataFormatException**.

Đây là minh họa giới hạn **một MUTF-8 payload**, không phải một cách tự tạo hoặc đo toàn bộ constant pool của class file đang chạy.

## 7. Giới hạn số field, method, interface và attribute

Ngoài Code và constant pool, nhiều bảng trong ClassFile sử dụng count dạng **u2**:

| Vùng | Trường đếm | Ý nghĩa giới hạn định dạng |
| --- | --- | --- |
| Field do class/interface **khai báo** | **fields_count** | Tối đa **65.535** phần tử **field_info** trong bảng |
| Method do class/interface **khai báo** | **methods_count** | Tối đa **65.535** phần tử **method_info** trong bảng |
| Direct superinterfaces | **interfaces_count** | Tối đa **65.535** phần tử |
| Class-level attributes | **attributes_count** | Số mục attribute trong bảng hiện tại tối đa 65.535 theo u2 |
| Các attribute tables bên trong field/method/Code | Các trường **attributes_count** riêng | Chịu giới hạn riêng và các quy tắc hiện diện/không trùng lặp |

**Fields_count và methods_count không bao gồm thành viên chỉ được thừa kế**. Ngược lại, các phương thức được compiler sinh trong chính class (ví dụ synthetic/bridge khi cần) vẫn góp vào số mục thực tế của **methods[]**.

Một con số nằm trong khoảng u2 **không bảo đảm class hợp lệ**:

- Field/method phải có tên, descriptor, flags và constraints phù hợp.
- Không thể tùy ý khai báo hai field với cùng name+descriptor hoặc hai method với cùng name+descriptor trong một class file.
- Một số attribute chỉ xuất hiện trong vị trí nhất định, có đúng số lần cho phép, và có cấu trúc riêng.
- Interface và class có quy tắc class-file validation khác nhau ở một số flags/attributes.
- Số lượng thành viên “được phép biểu diễn” khác năng lực hợp lý của compiler, runtime và tài nguyên máy thực.

Khi một code generator tạo quá nhiều thành viên trong **cùng một class**, có thể cần tạo thêm helper classes. Nhưng chuyển bớt method sang class khác cũng phải giữ đúng access, initialization, class loading và API contract.

## 8. max_locals và max_stack: slot của frame JVM

Mỗi Code attribute có:

~~~text
u2 max_stack;
u2 max_locals;
~~~

Các trường này mô tả nhu cầu frame theo mô hình JVM:

- **max_locals**: số vị trí local variable slots dành cho frame. Giới hạn tối đa **65.535 slots**.
- **max_stack**: độ sâu tối đa của operand stack, được đo theo units/slots trong mô hình đặc tả. Giới hạn tối đa **65.535 units**.
- Một giá trị **long** hoặc **double** chiếm **2 units** trong tính toán **max_locals/max_stack**.
- Các kiểu category 1 như int, float hoặc reference chiếm **1 unit**.
- Với instance method, local slot đầu thường chứa tham chiếu **this**; các tham số tiếp theo chiếm số slot theo loại.

Ví dụ khái quát:

~~~java
// Ví dụ phương thức instance, không phải chương trình Java độc lập.
class Calculator {
    long combine(int a, long b, double c) {
        long result = a + b;
        return result + (long) c;
    }
}
~~~

Vào method **combine**:

~~~text
Local slots lúc vào method:
  slot 0     this               (1)
  slot 1     int a              (1)
  slots 2-3  long b             (2)
  slots 4-5  double c           (2)
  slots ...  các local khác do compiler bố trí
~~~

Vì vậy chỉ riêng receiver và ba tham số đã cần **6 slots** ở đầu frame. Không nên nói rằng **long/double** là “một local” khi kiểm tra giới hạn u2 này.

Có giới hạn về index: với **max_locals = N**, local thuộc category 1 có chỉ số cao nhất **N - 1**, còn một giá trị long/double cần hai slots nên chỉ số bắt đầu cao nhất là **N - 2**. Instruction **wide** giúp mã hóa các local indexes lớn hơn dạng instruction thông thường, nhưng **không bãi bỏ giới hạn max_locals**.

**Không suy ra bộ nhớ:** slot trong JVM frame là đơn vị trừu tượng, không phải cam kết từng slot luôn tiêu thụ đúng bốn byte native memory.

## 9. Giới hạn tham số: 255 parameter units

**Method descriptor** biểu diễn kiểu tham số và trả về, ví dụ:

~~~text
(IJLjava/lang/String;)V
 | | |                  |
 int long String       return void
~~~

Theo **JVMS §4.3.3**, tổng độ dài danh sách tham số phải **không quá 255 parameter units**:

- **int, byte, short, char, boolean, float, reference hoặc array**: mỗi tham số tính **1 unit**.
- **long, double**: mỗi tham số tính **2 units**.
- Với lời gọi instance/interface method, tính thêm **1 unit** cho **this** ngầm.
- Với static method, không có receiver **this** ngầm theo quy tắc này.

Bởi vậy giới hạn thực dụng:

| Dạng method | Tổng units tham số khai báo tối đa theo descriptor/invocation |
| --- | --- |
| Static method | **255 units** |
| Instance/interface method | **254 units** tham số khai báo + **1 unit this** |
| Static có 128 tham số long | 256 units, vượt giới hạn |
| Static có 127 tham số long và 1 tham số int | 255 units, vừa về parameter units |
| Instance có 127 tham số long | 254 + 1 this = 255 units, vừa về parameter units |

Các ví dụ trên chỉ kiểm tra phép đếm parameter units; một khai báo cụ thể còn phải đáp ứng các ràng buộc tên, descriptor, class file và ngôn ngữ Java. Giá trị trả về **long/double** không được cộng vào độ dài danh sách tham số.

Chỉ nhìn **số lượng dấu phẩy** trong Java source sẽ cho kết quả sai nếu method có **long/double** hoặc là instance method. **varargs** như **String... values** chỉ là một array parameter về mặt descriptor nên tính **1 unit**, dù lúc gọi có thể truyền nhiều phần tử.

**Độc lập với max_locals:** giới hạn 255 đơn vị tham số và giới hạn 65.535 local slots là hai ràng buộc khác nhau; một method có ít tham số vẫn có thể dùng rất nhiều biến cục bộ, và không được dùng dung lượng max_locals để vượt giới hạn descriptor.

## 10. Ràng buộc về số chiều mảng

JVM cho phép mảng có tối đa **255 dimensions** theo định dạng và constraints của instruction, nhất là **multianewarray**:

~~~text
[I             int[]
[[I            int[][]
[[[I           int[][][]
[Ljava/lang/String;  String[]
~~~

Mỗi dấu **[** ứng với một chiều trong array descriptor. Mã lệnh **multianewarray** mang một operand **dimensions** dạng **u1**, có giá trị thích hợp từ 1 đến số chiều của array class được tham chiếu.

Giới hạn **255** là giới hạn biểu diễn/tập lệnh. Nó không có nghĩa việc tạo mảng 255 chiều luôn khả thi về heap, stack hay business logic. JVM có thể phải tuân theo các ràng buộc verifier và cấp phát; một chương trình hợp lệ vẫn có thể thất bại do tài nguyên.

## 11. Phiên bản class file và các điều kiện định dạng khác

Class header dùng:

~~~text
magic          u4 = CAFEBABE
minor_version  u2
major_version  u2
~~~

Ví dụ, class file mục tiêu **Java 17** dùng **major version 61**; Java 21 dùng **65**. Runtime chỉ có thể tải định dạng và tính năng mà phiên bản JVM của nó hỗ trợ. Class file phiên bản mới đưa vào runtime cũ thường dẫn tới **UnsupportedClassVersionError**.

**Phiên bản class file không giống giới hạn code_length**: tạo class file với major mới hơn không tự nâng giới hạn Code attribute lên hàng megabyte. Khi triển khai bytecode generator, phải tuân cả version-specific rules, constant-pool tags/attributes và các constraint của class file.

Ngoài độ dài có một số ràng buộc cấu trúc cốt lõi:

1. **Magic, version** phải hợp lệ đối với runtime mục tiêu.
2. Mọi chỉ số **constant pool** được tham chiếu phải nằm trong phạm vi và trỏ đúng **tag/type** mà cấu trúc yêu cầu.
3. Tên class, field, method và **descriptors** phải theo grammar JVM, ví dụ field descriptor **I**, **Ljava/lang/String;**, method descriptor **(I)V**.
4. Các chỉ số trong **Code** phải tham chiếu đúng operand, local variables, exception table và instruction boundaries.
5. Branch targets phải nhảy tới vị trí đầu instruction hợp lệ, không nhảy vào giữa operands hoặc vượt vùng **code[]**.
6. Stack/local typing tại điểm nối control flow phải hợp lệ theo quy tắc bytecode verification; StackMapTable dùng khi yêu cầu bởi phiên bản/kiểu xác minh tương ứng.
7. Những attributes cần cho việc hiểu class file phải đúng vị trí, đúng version và đúng constraints; một số metadata tùy chọn có thể bị bỏ qua theo đặc tả.
8. **Concrete method** phải có Code attribute hợp lệ; method **native/abstract** không có Code attribute.

Các điều kiện này giải thích vì sao một tệp có các counter trong phạm vi u2/u4 vẫn có thể bị JVM từ chối.

### 11.1. attribute_length có u4 nhưng không giúp tăng bytecode method

Một attribute có **u2 attribute_name_index** và **u4 attribute_length**. Dung lượng 32 bit của phần payload không có nghĩa **Code.code_length** cũng được phép dài tới 2^32-1 byte. **Code** có ràng buộc riêng **1–65.535 byte**.

Nhiều metadata như debug tables và annotation content có thể đóng góp kích thước vào class file, nhưng không phải bytecode instructions. Đừng lẫn **kích thước file .class**, **kích thước Code attribute**, **số byte của code[]** và **dung lượng constant pool**.

## 12. Ví dụ thực hành 2: đọc header của chính class file

Tạo tệp **ClassHeaderLimitDemo.java**:

~~~java
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ClassHeaderLimitDemo {
    static int sumBefore(int limit) {
        int result = 0;
        for (int i = 0; i < limit; i++) {
            result += i;
        }
        return result;
    }

    public static void main(String[] args) throws IOException {
        try (InputStream raw =
                     ClassHeaderLimitDemo.class.getResourceAsStream(
                             "/ClassHeaderLimitDemo.class")) {
            if (raw == null) {
                throw new IllegalStateException("Cannot locate class file");
            }
            DataInputStream in = new DataInputStream(raw);

            int magic = in.readInt();
            int minor = in.readUnsignedShort();
            int major = in.readUnsignedShort();
            int constantPoolCount = in.readUnsignedShort();

            System.out.printf("magic=%08X%n", magic);
            System.out.println("major=" + major);
            System.out.println("minor=" + minor);
            System.out.println("constantPoolNonEmpty=" + (constantPoolCount > 1));
            System.out.println("sumBefore5=" + sumBefore(5));
        }
    }
}
~~~

Biên dịch cho Java 17 và chạy bằng JVM tương thích:

~~~bash
javac --release 17 -encoding UTF-8 ClassHeaderLimitDemo.java
java ClassHeaderLimitDemo
~~~

**Stdout chính xác khi dùng lệnh trên:**

~~~text
magic=CAFEBABE
major=61
minor=0
constantPoolNonEmpty=true
sumBefore5=10
~~~

Giải thích:

- **readInt()** đọc magic 4 byte, được in theo dạng hex 8 chữ số.
- **readUnsignedShort()** đọc lần lượt minor, major, **constant_pool_count** theo đúng thứ tự header.
- **--release 17** tạo class file major **61** và, khi không dùng preview, minor **0**.
- Class này có constant pool không rỗng; **constantPoolNonEmpty** là **true**.
- **sumBefore(5)** cộng **0 + 1 + 2 + 3 + 4 = 10**.

Chương trình đọc **constant_pool_count**, nhưng chỉ in một kiểm tra boolean để stdout ổn định giữa các compiler. Khi thực hành, bạn có thể tự thêm:

~~~java
// Dòng minh họa thêm vào main để xem giá trị thực:
System.out.println("constant_pool_count=" + constantPoolCount);
~~~

Con số được in khi thêm dòng này có thể thay đổi theo compiler, tối ưu và những sửa đổi trên mã. Nếu muốn phân tích toàn bộ constant pool, phải đọc lần lượt từng **tag** và dung lượng của tag, đồng thời bỏ qua vị trí dành riêng cho **Long/Double**. Không thể coi toàn bộ constant pool là một mảng giá trị có kích thước cố định.

## 13. Xác minh bằng javap

Dùng class file vừa biên dịch:

~~~bash
javap -verbose ClassHeaderLimitDemo
javap -c -p ClassHeaderLimitDemo
javap -s -p ClassHeaderLimitDemo
~~~

Với **javap -verbose**, hãy tìm:

- **major version: 61**, **minor version: 0**;
- Những entry **#n = Utf8**, **Class**, **Methodref**, **NameAndType**... trong **Constant pool**;
- Trong phần **Code**, các chỉ số bytecode và thông tin **stack=...**, **locals=...**, **args_size=...**;
- Các attributes phụ như **LineNumberTable**, **StackMapTable** (nếu compiler sinh).

**javap -c** giải mã opcode và operand; offset bên trái instruction là vị trí byte trong code, không phải số thứ tự dòng source. Để lấy **code_length** chính xác, cần parse trực tiếp trường **Code.code_length** hoặc dùng công cụ bytecode chuyên dụng; không nên lấy “số dòng output” của javap làm code_length.

**javap -s** cho descriptors để kiểm tra parameter types khi có nhiều tham số **long/double**. Nếu dùng công cụ ASM hoặc bytecode library, nhớ phân biệt giới hạn theo slot, độ dài instruction array và cặp index/tag của constant pool.

## 14. Lỗi vượt giới hạn: xảy ra ở bước nào?

Một số lỗi liên quan nhưng ý nghĩa khác nhau:

| Hiện tượng | Khả năng nguyên nhân | Hướng xác minh |
| --- | --- | --- |
| Compiler báo **code too large** hoặc tương tự | **Một method** có instruction bytes vượt giới hạn | Xác định method nào, đo Code byte length, tách luồng xử lý |
| Compiler báo **too many constants** hoặc tương tự | Constant pool của **một class** không chứa thêm đủ entry/slot | Đếm constant pool và loại entry, phân bổ code/data sang class khác |
| Compiler báo chuỗi UTF-8 quá dài | Một Utf8 entry vượt **65.535 byte MUTF-8** | Đo số byte MUTF-8, chia dữ liệu thành nhiều phần để ghép runtime |
| Compiler từ chối method có quá nhiều tham số | Method descriptor/invocation vượt **255 units** hoặc có ràng buộc source/tool riêng | Tính long/double = 2 và this = 1 cho instance |
| **ClassFormatError** khi nạp class | Cấu trúc class file không hợp lệ: count/index/tag/descriptor/attribute... | Kiểm tra byte stream, spec, generator và phiên bản class file |
| **VerifyError** | Bytecode có vấn đề về stack/local types, control flow, frames hoặc ràng buộc xác minh | Kiểm tra StackMapTable, stack heights, branch target, opcode |
| **UnsupportedClassVersionError** | Runtime không hỗ trợ class-file version yêu cầu | Kiểm tra major/minor và phiên bản JVM |
| **NoClassDefFoundError** / lỗi liên kết khác | Thiếu dependency hoặc tham chiếu không giải quyết được | Kiểm tra classpath/module path và binary compatibility |
| **OutOfMemoryError** | Thiếu tài nguyên runtime theo một cơ chế cụ thể | Quan sát heap/metaspace/native memory; không suy ngay là tràn constant pool |

Tên thông báo compiler có thể thay đổi giữa các phiên bản hoặc công cụ; không nên gán một thông báo duy nhất thành yêu cầu chuẩn của JVM. Trái lại, **ClassFormatError**, **VerifyError**, **UnsupportedClassVersionError** là các loại lỗi Java có ý nghĩa định danh tương ứng; thời điểm xuất hiện có thể phụ thuộc vào quá trình loading/linking/verification.

**Lưu ý:** Nếu tự sinh tệp class không hợp lệ, không cần thực thi nó để “kiểm tra lỗi” trên hệ thống quan trọng. Hãy ưu tiên dùng parser/verifier chuyên dụng trong môi trường thử nghiệm, rồi chạy trên JVM mục tiêu khi đã đủ điều kiện.

## 15. Những cách giảm độ phức tạp class file

### 15.1. Khi một phương thức vượt code_length

Cách phù hợp thường là **chia method lớn thành các helper methods** có trách nhiệm rõ ràng, đảm bảo thứ tự điều khiển, exception semantics, synchronization, visibility và side effects không đổi.

Ví dụ thay vì sinh một phương thức có hàng nghìn công đoạn nối tiếp:

~~~java
// Minh họa cấu trúc trước khi chia:
static void processAll() {
    // hàng nghìn statement do generator sinh
}
~~~

Tách thành các đoạn:

~~~java
// Minh họa cấu trúc sau khi chia:
static void processAll() {
    processPart1();
    processPart2();
    processPart3();
}

static void processPart1() { /* công việc phần 1 */ }
static void processPart2() { /* công việc phần 2 */ }
static void processPart3() { /* công việc phần 3 */ }
~~~

Đây là minh họa phương án tổ chức mã, **không phải chương trình độc lập**. Nếu logic ban đầu có một **try/finally**, một trạng thái local được chia sẻ hoặc một nhánh **break/continue/return**, việc tách method cần thiết kế cách truyền state và giữ nguyên hành vi. Không thể tách bằng cách cắt tùy ý ở mỗi 10.000 dòng.

Khi cắt bớt code của một method, hãy biên dịch lại và kiểm tra **code_length** bằng công cụ thực tế. Không suy ra chỉ cần giảm số dòng đến một ngưỡng bất kỳ.

### 15.2. Khi constant pool của class vượt giới hạn

Các lựa chọn:

- Phân chia generated code sang **nhiều class** thay vì dồn vào một class.
- Loại bỏ những string/descriptor/member references dư thừa nếu generator tạo trùng mà công cụ có thể tái sử dụng.
- Đưa khối dữ liệu lớn mang tính nghiệp vụ sang **resource file** hoặc định dạng dữ liệu phù hợp, thay vì mã hóa mọi mục thành field/literal riêng trong class file.
- Đổi cách biểu diễn bảng tra cứu sinh ra, dùng cấu trúc dữ liệu runtime được khởi tạo từ nguồn rõ ràng, nếu điều đó phù hợp yêu cầu khởi tạo/hiệu năng.
- Đo số **slot** và từng loại constant-pool entry sau mỗi lần sinh, đừng chỉ đo số method hoặc số literal.

**Tách method nhưng giữ nguyên một class** chưa chắc giúp constant pool; những helper mới còn có thể làm tăng **Utf8 tên method** và **Methodref**.

### 15.3. Khi một hằng chuỗi/metadata quá dài

Nếu một literal không vừa **CONSTANT_Utf8**, có thể chia dữ liệu và ghép tại runtime hoặc đọc từ một resource. Cần chú ý **thời điểm** nối chuỗi: compiler có thể gộp các biểu thức hằng thành một literal lớn hơn nếu chỉ viết:

~~~java
// Minh họa: các thành phần đều là constant expression.
// Compiler có thể gộp chúng thành một string literal.
static final String TEXT = "part1" + "part2";
~~~

Nếu dùng cách chia, hãy xác nhận bytecode/constant pool cuối cùng thật sự có các payload nhỏ hơn ngưỡng và kết quả runtime khôi phục chính xác dữ liệu. Với dữ liệu văn bản lớn, resource thường dễ quản lý hơn generated source quá dài.

### 15.4. Khi method có quá nhiều tham số

Có thể chuyển một nhóm tham số liên quan thành object/record phù hợp, hoặc thiết kế API nhận một cấu trúc dữ liệu có validation rõ. Đừng tự đổi 200 tham số thành một array nếu việc đó làm mất contract, tên trường, kiểu dữ liệu hoặc logic sử dụng.

Mỗi quyết định cần cân bằng ABI/binary compatibility, khả năng đọc hiểu, serialization, nullability và performance. Các giới hạn class file là điều kiện kỹ thuật; refactoring phải giữ đúng hợp đồng ứng dụng.

## 16. Bẫy thường gặp

1. **“Một method có tối đa 65.535 dòng.”** Sai; giới hạn áp dụng cho **byte trong Code.code[]**, không phải dòng Java.
2. **“code_length là u4 nên một method có thể chứa 4 GiB bytecode.”** Sai; JVMS quy định **code_length < 65.536**.
3. **“Một class file không được lớn hơn 64 KiB.”** Sai; giới hạn 64 KiB thuộc một vùng code của một method, không phải tổng kích thước class file.
4. **“constant_pool_count = 65535 nghĩa là có 65535 literal.”** Sai; index bắt đầu 1, mức tối đa số slot có chỉ số là 65534, và constant pool chứa nhiều loại cấu trúc.
5. **“Một Long chỉ dùng một slot vì nó là một constant.”** Sai ở cách đánh chỉ số; **CONSTANT_Long/Double** chiếm hai slot.
6. **“65535 ký tự Unicode luôn vừa một Utf8 entry.”** Sai; giới hạn là **65.535 byte MUTF-8**, có thể ít ký tự hơn rất nhiều.
7. **“long/double chỉ là một tham số.”** Đúng về số tham số Java được viết, nhưng mỗi cái chiếm **2 parameter units** khi tính giới hạn JVM.
8. **“Tăng heap sẽ sửa lỗi code too large hoặc constant pool too large.”** Không; đây là giới hạn biểu diễn của class file.
9. **“Chia phương thức luôn giải quyết mọi trường hợp.”** Không; nó giải quyết code_length khi thực hiện đúng, còn class-level constant pool có thể vẫn vượt giới hạn.
10. **“ClassFormatError và VerifyError là một.”** Không; kiểm tra cấu trúc và xác minh kiểu/control flow là các phạm trù liên quan nhưng khác nhau.
11. **“Số field, method thừa kế cộng vào fields_count/methods_count.”** Không; các bảng đó chứa member được khai báo trong class file tương ứng.
12. **“Đã đúng count thì class chắc chắn chạy.”** Không; còn version, descriptor, flags, references, attributes, verification, linking và dependency.

## 17. Bài tập tự kiểm tra

**Câu 1.** Code attribute có **code_length = 65536** nhưng **attribute_length** vẫn vừa u4. Nó hợp lệ không?

**Đáp án:** Không. Trường **code_length** được lưu bằng u4 nhưng đặc tả giới hạn giá trị hợp lệ trong khoảng **1–65535**.

**Câu 2.** Với **constant_pool_count = 100**, các chỉ số thường hợp lệ có miền nào?

**Đáp án:** **1–99**; vị trí 0 không là entry thường. Nếu có **Long/Double**, slot tiếp ngay sau entry đó bị dành riêng và không chứa cp_info độc lập.

**Câu 3.** Vì sao 32.768 ký tự **é** có thể vượt giới hạn một Utf8 entry?

**Đáp án:** Mỗi **é** chiếm **2 byte MUTF-8**, tổng **65.536 byte**, lớn hơn mức u2 cho phép **65.535**.

**Câu 4.** Một static method có 127 tham số **double** và một **int**. Parameter length bằng bao nhiêu?

**Đáp án:** **127 × 2 + 1 = 255 units**, vừa giới hạn descriptor đối với static method, giả sử các ràng buộc khác được thỏa mãn.

**Câu 5.** Cũng tập tham số trên nhưng là instance method thì sao?

**Đáp án:** Phải tính thêm **this** = 1 unit, thành **256**, vượt giới hạn.

**Câu 6.** Một phương thức có khoảng 20.000 dòng Java nhưng code_length chỉ 50.000 byte; một phương thức khác có 5.000 dòng nhưng code_length 66.000 byte. Phương thức nào không hợp lệ theo giới hạn Code?

**Đáp án:** Phương thức thứ hai; tiêu chí là **số byte instructions**, không phải số dòng source.

**Câu 7.** Nếu tách một generated method lớn thành 20 helper method trong cùng một class, constant pool có chắc giảm?

**Đáp án:** Không. Nó có thể tăng do tên helper và symbolic references mới. Muốn giảm constant pool class-level phải xét tổng entry/slot của class và thường cần phân bổ sang nhiều class hoặc thay cách lưu dữ liệu.

**Câu 8.** **max_locals** có giá trị 20. Có thể đặt một **double** bắt đầu ở local slot index 19 không?

**Đáp án:** Không; **double** chiếm hai slot. Slot bắt đầu cao nhất là **18** nếu **max_locals = 20**.

**Câu 9.** JVM báo **UnsupportedClassVersionError** khi nhận class file mới. Có nên giảm **Code.code_length** để sửa không?

**Đáp án:** Không giải quyết đúng nguyên nhân. Cần chạy runtime hỗ trợ class-file version hoặc biên dịch với **--release**/target phù hợp cùng các API tương thích.

**Câu 10.** Một class file có **major=61**, **constant_pool_count=20**, mọi counts đều không vượt u2. Đã đủ kết luận hợp lệ chưa?

**Đáp án:** Chưa. Còn phải kiểm tra từng tag, index, descriptor, flags, attributes, Code, stack frames, targets và các ràng buộc định dạng/xác minh khác.

## 18. Checklist dành cho compiler, bytecode generator và người đọc class file

- **Xác định đơn vị đo:** byte trong code[], slot constant pool, byte MUTF-8, parameter units, local slots hay toàn bộ file.
- **Đo sau khi sinh bytecode thực:** nhiều compiler tối ưu/sinh helper, synthetic methods và metadata nên số dòng Java không đủ.
- **Mỗi method:** bảo đảm **code_length 1–65535**, branch/handler offset hợp lệ, max_stack/max_locals nhất quán và StackMapTable đúng nếu cần.
- **Mỗi class:** bảo đảm **constant_pool_count ≤ 65535**, index/tag hợp lệ; nhớ slot trống sau Long/Double và giới hạn số field/method/interface.
- **Mỗi Utf8 entry:** kiểm tra **MUTF-8 encoded bytes ≤ 65535**; đừng dùng Java String.length() làm đại diện độ dài byte.
- **Mỗi descriptor:** kiểm tra cú pháp và tối đa **255 parameter units**, có tính this đối với instance/interface invocation.
- **Mảng:** tối đa **255 chiều** trong các ràng buộc liên quan.
- **Phiên bản:** sinh class-file major/minor và attributes/tags mà JVM đích hỗ trợ.
- **QA:** dùng **javap -v/-c/-s**, parser/class verifier thích hợp và test chạy thực trên JDK mục tiêu; phân biệt lỗi format, verification và linking.
- **Khi vượt giới hạn:** tách method/class hoặc đưa dữ liệu sang resource theo đúng giới hạn bị chạm; kiểm tra lại hành vi và kích thước sản phẩm sau biên dịch.

### Tài liệu chuẩn

- **Java Virtual Machine Specification (Java SE 17), §4.1 — The ClassFile Structure**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.1
- **§4.3.3 — Method Descriptors**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3.3
- **§4.4 — The Constant Pool**, đặc biệt §4.4.5 Long/Double và §4.4.7 Utf8: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4
- **§4.7.3 — The Code Attribute**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.3
- **§4.9 — Constraints on Java Virtual Machine Code**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.9
- **§4.11 — Limitations of the Java Virtual Machine**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.11

**Ghi nhớ:** giới hạn **65.535 byte** áp dụng cho instruction array của **một phương thức**, còn constant pool là tài nguyên dùng chung **trong một class** với **constant_pool_count u2**, slot đánh số từ 1 và quy tắc Long/Double chiếm hai slot. Một **CONSTANT_Utf8** chỉ chứa tối đa **65.535 byte Modified UTF-8**. Những giới hạn 255 parameter units, 65.535 local/stack units, 255 chiều mảng và các ràng buộc descriptor/verification là các lớp kiểm tra độc lập. Muốn xử lý đúng lỗi, hãy xác định **vùng nào của định dạng đã vượt ngưỡng**, rồi sửa tại đúng ranh giới đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-file-format-012">Đọc javap -c -v -s -l để nối cấu trúc class với mã Java</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc `javap -c -v -s -l` để nối cấu trúc class với mã Java

## 1. Vì sao cần đọc `javap`?

Mã nguồn Java cho bạn biết **ý định lập trình**: khai báo class, trường, phương thức, kiểu dữ liệu, điều kiện và lời gọi. File `.class` cho JVM biết **cấu trúc nhị phân và mã lệnh cần thực thi**. Công cụ `javap` của JDK giúp nhìn từ class file trở lại những cấu trúc và hành vi có liên quan tới source.

Hãy nghĩ đến một hành trình:

~~~text
BytecodeTour.java
      |
      | javac -g BytecodeTour.java
      v
BytecodeTour.class
      |
      +--- header, major version, access flags, this/super_class
      +--- constant pool, fields[], methods[], attributes[]
      +--- Code: bytecode + max_stack + max_locals + exception table
      +--- debug attributes: LineNumberTable, LocalVariableTable
      |
      | javap -c -v -s -l -p BytecodeTour
      v
Bản đọc được của bytecode, descriptors, metadata và debug tables
      |
      v
Đối chiếu lại: dòng source -> BCI -> opcode -> constant-pool entry
~~~

**`javap` là disassembler, không phải decompiler đầy đủ.** Nó hiển thị những thông tin có thật trong class file, nhưng không bảo đảm khôi phục nguyên văn source ban đầu, mọi tên biến, chú thích, cách viết lambda hay biểu thức gốc. Các chỉ số constant pool và bytecode offset phụ thuộc compiler, phiên bản Java và tùy chọn biên dịch.

Mục tiêu thực hành của bài:

1. Chạy được một ví dụ Java 17+ và xác định stdout.
2. Dùng `-c` để nối `if`, phép cộng và `return` với lệnh bytecode.
3. Dùng `-s` để hiểu descriptor của field, constructor và method.
4. Dùng `-v` để đọc header, constant pool, access flags, `Code`, các attribute và liên kết như `#12`.
5. Dùng `-l` và `javac -g` để nối source line và local variable với **BCI** (bytecode index).
6. Biết giới hạn của việc suy ngược từ class file về source.

## 2. Bốn tùy chọn trọng tâm và những cờ bổ trợ

| Tùy chọn | Thông tin chính | Câu hỏi trả lời |
| --- | --- | --- |
| **`-c`** | Disassemble `Code` thành opcode bytecode, offset, operand, branch target và lời gọi | “Đoạn Java này hạ thành các lệnh gì?” |
| **`-v`** hoặc `-verbose` | Metadata chi tiết: phiên bản class, flags, constant pool, descriptors, attributes, thông tin `Code`, stack maps… | “Class file thực tế lưu những gì?” |
| **`-s`** | **JVM type descriptors** cho field và method | “Kiểu dữ liệu, tham số, kiểu trả về được mã hóa thế nào?” |
| **`-l`** | `LineNumberTable` và `LocalVariableTable` **nếu có** | “BCI tương ứng dòng source/biến local nào?” |
| `-p` hoặc `-private` | Hiện **mọi** members, kể cả private | “Tại sao method/field private không xuất hiện mặc định?” |
| `-classpath` / `-cp` | Chỉ nơi tìm class/JAR người dùng | “Đang disassemble class được biên dịch ở đâu?” |
| `--module` / `-m` | Chọn module chứa class khi duyệt named modules | “Class nằm trong module nào?” |
| `--multi-release N` | Hỗ trợ chọn phiên bản phù hợp khi phân tích multi-release JAR | “Mình có đang đọc đúng biến thể class cho Java N?” |
| `-sysinfo` | In thông tin file như đường dẫn, kích thước, ngày và hash theo bản công cụ | “Đang kiểm tra đúng file chưa?” |

Lệnh kết hợp được sử dụng trong bài:

~~~bash
javap -c -v -s -l -p BytecodeTour
~~~

**Các tùy chọn có phần thông tin chồng nhau.** Chẳng hạn `-v` đã có thể hiển thị descriptor và debug attributes; `-s`, `-l` vẫn hữu ích khi bạn muốn học từng góc nhìn riêng. Khi dùng tất cả cùng lúc, đầu ra có thể dài và lặp lại một số thông tin. Không có nghĩa bốn cờ tạo nên bốn file kết quả hay bốn giai đoạn chạy JVM.

Lệnh nhanh theo nhu cầu:

~~~bash
javap BytecodeTour
javap -p BytecodeTour
javap -c -p BytecodeTour
javap -s -p BytecodeTour
javap -l -p BytecodeTour
javap -v -p BytecodeTour
~~~

**Chú ý dấu `-l`:** đó là chữ **L thường** (*line/local*), không phải số 1 hoặc chữ I hoa. `javap` nhận **tên class** đã có trên classpath hoặc đường dẫn đến file `.class`. Đừng chạy `javap BytecodeTour.java` rồi chờ chương trình biên dịch source; việc biên dịch là trách nhiệm của `javac`.

## 3. Ví dụ chạy được: một source, nhiều góc nhìn class file

Tạo file **`BytecodeTour.java`** trong thư mục mới:

~~~java
public class BytecodeTour {
    private final String label;

    public BytecodeTour(String label) {
        this.label = label;
    }

    public int compute(int left, int right) {
        int sum = left + right;
        if (sum > 10) {
            return sum * 2;
        }
        return sum + 1;
    }

    public String message(int value) {
        return label + ":" + value;
    }

    public static void main(String[] args) {
        BytecodeTour tour = new BytecodeTour("JVM");
        int result = tour.compute(4, 8);
        System.out.println(tour.message(result));
    }
}
~~~

Biên dịch bằng **JDK 17 trở lên** với **đầy đủ debug metadata** và chạy:

~~~bash
javac -g BytecodeTour.java
java BytecodeTour
~~~

**Stdout chính xác:**

~~~text
JVM:24
~~~

Vì `compute(4,8)` có `sum = 12`, điều kiện `12 > 10` đúng nên trả về `24`. `message(24)` tạo `"JVM:24"`. Lệnh `-g` trong `javac` bật mọi thông tin debug thông thường, gồm cả tên local variables cần để `javap -l` hiển thị.

Trong cùng thư mục, chạy:

~~~bash
javap -c -v -s -l -p BytecodeTour
~~~

Bạn sẽ thấy rất nhiều dòng; cách đọc hiệu quả là chia ra các tầng **header → constant pool → member descriptors → Code → debug tables → attributes**.

### Quan hệ của source với các thành phần class file

| Dòng/khái niệm Java | Nơi quan sát trong class file |
| --- | --- |
| `public class BytecodeTour` | `this_class`, `flags: ACC_PUBLIC, ACC_SUPER` (theo build thông dụng) |
| Không ghi `extends` | `super_class` tham chiếu `java/lang/Object` |
| `private final String label` | Một `field_info` với flags `ACC_PRIVATE, ACC_FINAL` và descriptor `Ljava/lang/String;` |
| `BytecodeTour(String label)` | `method_info` tên đặc biệt `<init>`, descriptor `(Ljava/lang/String;)V` |
| `int compute(int,int)` | `method_info` tên `compute`, descriptor `(II)I`, `Code` có `iadd`/so sánh/`ireturn` |
| `String message(int)` | `method_info` tên `message`, descriptor `(I)Ljava/lang/String;` |
| `static void main(String[])` | `method_info` tên `main`, descriptor `([Ljava/lang/String;)V` |
| `new BytecodeTour("JVM")` | Bytecode cấp phát và constructor call (`new`, `dup`, `invokespecial`) |
| `System.out.println(...)` | `getstatic` và `invokevirtual` trỏ đến constant pool |
| Dòng và biến local trong `compute` | `LineNumberTable` / `LocalVariableTable` khi có debug info |

**Không suy luận rằng bytecode luôn giống Java source theo tỷ lệ “một câu lệnh Java = một opcode”.** Một biểu thức Java thường cần nhiều bytecode; một số hằng được compiler gộp/inline; một số lambda, nối chuỗi, switch và thao tác generic tạo ra bytecode hoặc method tổng hợp mà người viết không thấy trong source.

## 4. Đọc `-c`: từ điều kiện Java sang operand stack và branch

Chạy:

~~~bash
javap -c -p BytecodeTour
~~~

Tập trung vào phương thức `compute(int,int)`. Với `javac` phiên bản hiện đại, bạn thường quan sát một chuỗi **tương đương** như sau (đã rút gọn thành các lệnh quan trọng; offset và cách sinh branch có thể khác theo compiler):

~~~text
public int compute(int, int);
  Code:
       0: iload_1
       1: iload_2
       2: iadd
       3: istore_3
       4: iload_3
       5: bipush        10
       7: if_icmple     14
      10: iload_3
      11: iconst_2
      12: imul
      13: ireturn
      14: iload_3
      15: iconst_1
      16: iadd
      17: ireturn
~~~

Đây là **ví dụ disassembly minh họa cho logic**, không phải cam kết mọi bản `javac` xuất ra đúng từng BCI ở trên. Điểm quan trọng là dòng source và ý nghĩa opcode:

- `iload_1`, `iload_2`: lấy `left` và `right` từ **local variable slots 1 và 2** đưa lên operand stack.
- `iadd`: lấy hai `int` từ operand stack, tính `left + right`, đẩy kết quả.
- `istore_3`: ghi `sum` vào **local slot 3**.
- `bipush 10`: đẩy hằng số nguyên 10 lên operand stack.
- `if_icmple 14`: so sánh hai `int`. Nếu `sum <= 10` thì nhảy đến BCI 14; ngược lại tiếp tục nhánh `sum > 10`.
- `iconst_2; imul; ireturn`: nhân `sum * 2` và trả về `int` từ method.
- `iconst_1; iadd; ireturn`: nhánh còn lại trả về `sum + 1`.

**BCI** là **bytecode index**: offset byte bên trong `Code.code[]` của một method, không phải thứ tự dòng Java và cũng không phải địa chỉ lệnh CPU. Một opcode có thể dùng nhiều byte nên BCI không nhất thiết tăng từng 1. Operand branch mà `javap` in là **địa chỉ đích trong phương thức**, không phải số dòng source.

### Mô phỏng operand stack

Tại thời điểm bắt đầu `compute` (instance method):

~~~text
Local variables:
  slot 0 : this (BytecodeTour)
  slot 1 : left = 4
  slot 2 : right = 8
  slot 3 : sum (chưa gán tại đầu method)

Operand stack (vùng trừu tượng):
  []                       ; trước iload_1
  [4]                      ; sau iload_1
  [4, 8]                   ; sau iload_2
  [12]                     ; sau iadd
  []                       ; sau istore_3
  [12, 10]                 ; trước if_icmple
~~~

Nếu điều kiện “`sum <= 10`” **sai**, VM đi qua các lệnh nhân 2, rồi `ireturn` trả `24`. Nếu điều kiện đúng, VM nhảy đến nhánh trả `sum + 1`. Local slot **không phải** biến Java ở cấp source một cách vĩnh viễn: compiler được tái sử dụng slot khi phạm vi sống không trùng nhau.

### Constructor và entry method trong `-c`

Ở constructor `BytecodeTour(String)`, bạn sẽ gặp chuỗi ý nghĩa:

~~~text
aload_0                  ; this
invokespecial ...        ; gọi Object.<init>()
aload_0                  ; this
aload_1                  ; label argument
putfield ...             ; gán this.label
return
~~~

Trong `main`, việc tạo object thường thể hiện bởi:

~~~text
new ... BytecodeTour
dup
ldc ... "JVM"
invokespecial ... BytecodeTour."<init>"
~~~

`new` tạo object, `dup` giữ một bản tham chiếu trên operand stack để dùng sau constructor call, `ldc` nạp một hằng (ở đây là chuỗi), còn `invokespecial` gọi constructor. Những lệnh này giúp nối ý nghĩa `new BytecodeTour("JVM")` với class file. Đừng nhầm `return` của constructor `void` với `ireturn` của `compute` trả `int`.

## 5. Đọc `-s`: descriptors và sự khác biệt với kiểu Java

Chạy:

~~~bash
javap -s -p BytecodeTour
~~~

Các descriptor quan trọng bạn cần tìm:

~~~text
private final java.lang.String label;
  descriptor: Ljava/lang/String;

public BytecodeTour(java.lang.String);
  descriptor: (Ljava/lang/String;)V

public int compute(int, int);
  descriptor: (II)I

public java.lang.String message(int);
  descriptor: (I)Ljava/lang/String;

public static void main(java.lang.String[]);
  descriptor: ([Ljava/lang/String;)V
~~~

Từng ký hiệu được giải nghĩa theo **JVMS `4.3`**:

| Ký hiệu | Ý nghĩa |
| --- | --- |
| `B` / `C` / `D` / `F` | `byte` / `char` / `double` / `float` |
| `I` / `J` / `S` / `Z` | `int` / `long` / `short` / `boolean` |
| `V` | `void` — chỉ dùng làm kết quả method descriptor |
| `Lpkg/ClassName;` | Một reference type, tên trong JVM dùng dấu `/` |
| `[I` | Mảng `int[]` |
| `[Ljava/lang/String;` | Mảng `String[]` |
| `(II)I` | Hai tham số `int`, kết quả `int` |
| `([Ljava/lang/String;)V` | Một `String[]`, kết quả `void` |

Ví dụ `(Ljava/lang/String;I)J` là method nhận `String` và `int`, trả về `long`. Số lượng ký tự descriptor **không** bằng số lượng local-variable slots: một `long`/`double` chiếm hai local slots trong model JVM, còn một `reference` hay `int` thường một slot.

### Field descriptor khác method descriptor

- Field có **một** ký hiệu kiểu: `Ljava/lang/String;`, `I`, `[I`.
- Method có **dấu ngoặc bao quanh tham số** rồi tới return descriptor: `(II)I`.
- Constructor `<init>` có method descriptor trả về `V` dù constructor không khai báo kiểu trả về trong source.
- `-s` cho **descriptors** đã erase generics. Để thấy generic type như `List<String>`, đọc thêm `Signature` attribute (nếu có) trong `-v`.

Đừng coi `-s` là tùy chọn “in phương thức Java source hoàn chỉnh”: descriptors không nói tên tham số, code, exception logic hay comment.

## 6. Đọc `-v`: header, constant pool và attributes

Chạy:

~~~bash
javap -v -p BytecodeTour
~~~

### 6.1. Header/class identity

Phần đầu thường có:

~~~text
Classfile .../BytecodeTour.class
  Last modified ...; size ... bytes
  SHA-256 checksum ...
  Compiled from "BytecodeTour.java"
public class BytecodeTour
  minor version: 0
  major version: ...
  flags: (...) ACC_PUBLIC, ACC_SUPER
  this_class: #...           // BytecodeTour
  super_class: #...          // java/lang/Object
  interfaces: 0, fields: 1, methods: ..., attributes: ...
~~~

Những dòng đường dẫn, thời gian, checksum, file size, constant-pool index, method count và version là **đầu ra phụ thuộc bản dựng**. Với `javac` Java 17 mặc định, major version thường là **61**; Java 21 mặc định **65**, Java 25 mặc định **69**. Khi dùng `javac --release 17` trên JDK mới, class vẫn có thể là major 61 vì target bytecode được hạ về Java 17.

`this_class` và `super_class` là **chỉ số constant pool**, còn flags thể hiện đặc tính class. Field `label` cũng có flags riêng (`ACC_PRIVATE, ACC_FINAL`); method `compute` có `ACC_PUBLIC`. Không đọc nhầm cờ cấp class với cờ cấp method/field.

### 6.2. Constant pool: nối một chỉ số với ý nghĩa

`Constant pool` có thể chứa các loại entry như:

| Loại entry | Nội dung |
| --- | --- |
| `Utf8` | Tên class, tên method, descriptor, chuỗi literal và metadata tên |
| `Class` | Tham chiếu tới tên class/interface |
| `NameAndType` | Tên thành viên kèm descriptor |
| `Fieldref` | Tham chiếu field được dùng trong bytecode |
| `Methodref` / `InterfaceMethodref` | Tham chiếu phương thức class/interface |
| `String` / `Integer` / các numeric constants | Hằng mà bytecode có thể sử dụng |
| `InvokeDynamic` | Call site động; được liên kết với bootstrap method |
| `MethodHandle` / `MethodType` | Thành phần mô tả liên kết, bootstrap hoặc method handle |

Một vài dòng dạng sơ đồ:

~~~text
#3  = Class              #4         // java/lang/Object
#4  = Utf8               java/lang/Object
#17 = Fieldref           #...#...   // BytecodeTour.label:Ljava/lang/String;
#21 = Methodref          #...#...   // BytecodeTour."<init>":(Ljava/lang/String;)V
#30 = Utf8               compute
#31 = Utf8               (II)I
~~~

Các con số `#3`, `#17`, `#21`… trong sơ đồ **không bảo đảm** khớp file bạn sinh: chúng chỉ minh họa loại entry. Hãy lấy **index thực tế** của một lệnh trong `javap -c` và đối chiếu **cùng số đó** trong phần `Constant pool` của output `-v`.

Ví dụ nhìn thấy:

~~~text
... getfield  #17  // Field label:Ljava/lang/String;
~~~

Đọc theo chuỗi:

1. `getfield` là opcode lấy **instance field** từ một object reference.
2. `#17` là chỉ số constant pool **do compiler sinh trong file đang xem**.
3. Entry tại `#17` là `Fieldref`; nó liên hệ class owner, tên `label` và descriptor `Ljava/lang/String;`.
4. Muốn biết câu lệnh Java nào dẫn tới opcode này, quay sang `LineNumberTable` và source.

**`#17` không phải địa chỉ heap, không phải line 17 và không phải local slot 17.** Cùng ký hiệu `#N` còn xuất hiện ở bootstrap methods, descriptors và thuộc tính khác; luôn đọc tiêu đề của bảng.

### 6.3. Thông tin `Code` của một phương thức

Trong `-v`, một method có thân bytecode thường có:

~~~text
Code:
  stack=2, locals=4, args_size=3
  0: iload_1
  1: iload_2
  ...
LineNumberTable:
  ...
LocalVariableTable:
  ...
StackMapTable:
  ...
~~~

Ý nghĩa:

- `stack=2`: `max_stack` của method, tức số **operand-stack slots tối đa** mà bytecode của method cần (ví dụ minh họa `compute` này cần 2).
- `locals=4`: `max_locals` gồm slot 0 của `this`, slot 1–2 của `left`/`right` và slot 3 của `sum`.
- `args_size=3`: số slot dùng cho tham số theo JVM invocation frame: `this` + hai tham số `int`. Một static method không có slot `this`; `long` và `double` chiếm hai slots.
- `StackMapTable`: mô tả các frame kiểu dùng để **xác minh bytecode** tại các điểm control flow; **không** phải Java stack trace khi ứng dụng ném exception.
- `Exception table` (nếu method có vùng bắt ngoại lệ): xác định protected ranges, target handlers và catch type.

Các con số ở đây phụ thuộc phương thức được chọn, và phần `StackMapTable` có thể khác theo phiên bản class/compiler. Nếu không có nhánh exception trong `compute`, bạn không cần tìm một exception handler vô cớ.

### 6.4. Phân biệt các attributes

| Attribute | Thuộc vị trí | Dùng để |
| --- | --- | --- |
| `SourceFile` | Cấp class | Tên file nguồn; không chứa toàn bộ source |
| `Code` | Bên trong `method_info` thích hợp | Bytecode, `max_stack`, `max_locals`, exception table, code attributes |
| `LineNumberTable` | Bên trong `Code` | Ánh xạ một số **BCI → số dòng source** |
| `LocalVariableTable` | Bên trong `Code` | Tên, loại, slot, khoảng BCI còn hiệu lực của biến local |
| `LocalVariableTypeTable` | Bên trong `Code` | Generic signature của local variables khi có |
| `Signature` | Class/field/method thích hợp | Thông tin generics đã bị erase khỏi descriptor JVM |
| `Exceptions` | Method | Các checked-exception types khai báo trong `throws` |
| `BootstrapMethods` | Cấp class | Metadata bootstrap cho dynamic constants / invokedynamic |
| `StackMapTable` | Bên trong `Code` | Khai báo các type-verification frames |

Không phải mọi `.class` đều có mọi attribute. Nhiều mục optional, phụ thuộc source, debug options, Java release, compiler và tối ưu. Công cụ phân tích cần phân biệt **thiếu debug metadata** với **class file bị lỗi**.

## 7. Đọc `-l`: nối BCI với line number và local variable

Đây là bước biến bản bytecode thành lời giải thích dễ hiểu cho người học.

### 7.1. `LineNumberTable` cho biết điều gì?

Ở method `compute`, một `LineNumberTable` có thể thể hiện ý như:

~~~text
LineNumberTable:
  line <dòng khai báo sum>: 0
  line <dòng if>: 4
  line <dòng return sum * 2>: 10
  line <dòng return sum + 1>: 14
~~~

Các nhãn `<dòng ...>` ở đây là **minh họa quan hệ**, không phải dòng output thật của `javap`. Công cụ sẽ in **số dòng nguyên** trong `BytecodeTour.java` và BCI bắt đầu tương ứng. Một dòng Java có thể sinh nhiều opcode, đôi khi hai dòng source liên quan cùng một khoảng BCI, và compiler có thể bỏ một số dòng không cần lệnh.

Theo JVMS, `LineNumberTable` là metadata **tùy chọn**. Nó cho debugger/tracing biết BCI liên quan đến dòng source nào; bản thân nó **không chứa source code** và không cam kết mọi opcode có một dòng Java khác nhau.

### 7.2. `LocalVariableTable` cho biết điều gì?

Khi biên dịch bằng `javac -g`, bạn có thể thấy vùng mô tả dạng:

~~~text
LocalVariableTable:
  Start  Length  Slot  Name   Signature
  ...    ...      0   this   LBytecodeTour;
  ...    ...      1   left   I
  ...    ...      2   right  I
  ...    ...      3   sum    I
~~~

Đây cũng là **sơ đồ giá trị**, không phải dump cụ thể về Start/Length. Bảng cần đọc như sau:

- `Start` là BCI đầu khoảng tồn tại của local variable.
- `Length` là số bytecode-unit của phạm vi sống: thông thường biến có hiệu lực trong khoảng **[Start, Start + Length)**.
- `Slot` là chỉ số vùng local variables của frame, **không phải số dòng Java**.
- `Name` là tên local được compiler lưu để hỗ trợ debug.
- `Signature` trong output của bảng này thường là **field descriptor**; với generic local variables, có thể có `LocalVariableTypeTable` để giữ thông tin generic.

Ví dụ, `slot 3` chứa `sum` trong `compute`. Nếu compiler tái sử dụng slot 3 cho một local variable khác ở đoạn code không trùng phạm vi, bảng có thể có **hai entry cùng Slot nhưng khác Start/Length/Name**. Không có mâu thuẫn: local slots là vùng lưu trữ được quản lý theo bytecode scope chứ không phải định danh biến source vĩnh viễn.

### 7.3. Vì sao `-l` có thể không thấy tên local variables?

Điểm mấu chốt: debug attributes **không bắt buộc** trong class file. `javac` mặc định thường chỉ ghi một phần debug metadata (ví dụ file nguồn và line numbers), còn **local variable names** có thể không được ghi trừ khi bật tùy chọn thích hợp.

So sánh ba cách biên dịch (mỗi lần lệnh sau **ghi đè BytecodeTour.class** trong cùng thư mục; làm tuần tự và xem output ngay sau mỗi lần):

~~~bash
javac -g:none BytecodeTour.java
javap -l -p BytecodeTour

javac -g:lines,source BytecodeTour.java
javap -l -p BytecodeTour

javac -g BytecodeTour.java
javap -l -p BytecodeTour
~~~

Kỳ vọng về metadata:

| Biên dịch | `LineNumberTable` | `LocalVariableTable` |
| --- | --- | --- |
| `javac -g:none` | Thường **không có** | Thường **không có** |
| `javac -g:lines,source` | Có khi method có code tương ứng | Không có local-name debug table do option này |
| `javac -g` | Có khi áp dụng | Có cho các local/parameters compiler giữ metadata |

Đừng suy “không có LocalVariableTable = method không có biến local”. JVM vẫn có `max_locals` và vẫn thực thi `iload`/`istore`; **chỉ tên và phạm vi ánh xạ từ source bị lược bỏ**. Tên tham số cũng có thể được giữ qua `MethodParameters` nếu dùng `javac -parameters`, độc lập phần nào với `LocalVariableTable`; hai attribute có chức năng không hoàn toàn giống nhau.

Để làm lại bài chính sau các lần so sánh:

~~~bash
javac -g BytecodeTour.java
javap -c -v -s -l -p BytecodeTour
~~~

## 8. Nối cả bốn tùy chọn thành một quy trình đọc

Khi muốn giải thích vì sao lời gọi `compute(4,8)` trả `24`, hãy lần theo đúng thứ tự sau:

**Bước 1 — từ source nhận diện method.** `public int compute(int left, int right)` nhận hai `int`, trả `int`.

**Bước 2 — dùng `-s` xác định descriptor.** Tìm `compute` với `(II)I`. Nếu class có nhiều overload, descriptor giúp chọn đúng method, tránh nhầm `compute(long,long)`.

**Bước 3 — dùng `-c` theo operand stack.** Tìm `iload_1` và `iload_2`, `iadd`, `istore_3`, các lệnh so sánh, hai nhánh `ireturn`. Thử giá trị 4 và 8 để suy kết quả 24.

**Bước 4 — dùng `-v` hiểu operands.** Nếu có `getfield #N`, `invokevirtual #M` hoặc `invokedynamic #K`, tra entries `#N`/`#M`/`#K` trong constant pool và các bootstrap attributes liên quan. Biết chính xác owner/name/descriptor thay vì đoán từ mnemonic.

**Bước 5 — dùng `-l` kiểm tra ánh xạ source.** Tìm BCI tương ứng dòng `sum`/`if`/`return` và slot của `left`, `right`, `sum`. Nếu metadata thiếu, bật `-g` rồi biên dịch lại khi bạn có source và có quyền; không giả vờ rằng `javap` tự hồi sinh tên biến.

**Bước 6 — tách bằng chứng khỏi suy luận.** Bytecode là bằng chứng về cấu trúc class file **đã được compiler tạo ra**; không bảo đảm JVM thực thi từng opcode bằng đúng một lệnh CPU. JVM có thể interpreter hoặc JIT-compile tùy VM, nhưng các hành vi quan sát phải đúng yêu cầu của Java/JVMS.

### Sơ đồ đối chiếu cho một biểu thức

~~~text
Source Java:
    int sum = left + right;
          |
          v
Method descriptor (-s):
    compute(II)I
          |
          v
Bytecode (-c):
    iload_1 -> iload_2 -> iadd -> istore_3
          |
          v
Debug info (-l, nếu có):
    BCI 0 -> dòng source có phép cộng
    slots: 1=left, 2=right, 3=sum
          |
          v
Verbose (-v):
    Code { max_stack, max_locals, LineNumberTable,
           LocalVariableTable, StackMapTable, ... }
~~~

Điểm tinh tế: biểu thức `left + right` chỉ thao tác local ints, nên những lệnh `iload` và `iadd` **không cần** constant-pool entry cho mỗi biến. Ngược lại, `getfield`/`invokevirtual`/`new` thường tham chiếu constant pool vì chúng cần biết class/field/method được gọi.

## 9. Một số mẫu opcode thường gặp và ý nghĩa Java

| Java source minh họa | Bytecode thường gặp | Cách diễn giải |
| --- | --- | --- |
| `int x = a + b;` | `iload`, `iadd`, `istore` | Nạp, cộng, lưu số nguyên |
| `if (x > 10)` | `iload`, `bipush`, `if_icmple` | Nhảy theo **điều kiện đảo** để bỏ qua nhánh true |
| `return x;` trong method `int` | `iload`, `ireturn` | Trả `int` về caller |
| `new Foo()` | `new`, `dup`, `invokespecial <init>` | Cấp phát và khởi tạo object |
| `obj.name` | `aload`, `getfield` | Đọc instance field |
| `Foo.count` | `getstatic` | Đọc static field |
| `obj.work()` | `invokevirtual` hoặc cơ chế invoke phù hợp | Gọi method, dispatch phụ thuộc kiểu và opcode |
| `Interface m = ...; m.work()` | Có thể `invokeinterface` | Gọi qua interface reference |
| `System.out.println(x)` | `getstatic`, `invokevirtual` | Lấy `PrintStream` rồi gọi method |
| `label + ":" + value` | Có thể `invokedynamic` với `StringConcatFactory` trên compiler hiện đại | Nối chuỗi không nhất thiết dựng `StringBuilder` như compiler cũ |
| `try/catch` | `Exception table` + handlers | Mô tả phạm vi exception; không chỉ nhìn opcode branch |
| `switch` trên số nguyên | `tableswitch` hoặc `lookupswitch` | Lựa chọn nhánh theo giá trị, tùy mật độ case |

Bảng này mô tả **khuynh hướng biên dịch**, không phải hợp đồng “một cấu trúc source luôn đúng một chuỗi opcode”. Compiler có thể thay đổi chiến lược mà vẫn tạo class hợp lệ. Đặc biệt, đừng xem việc không thấy `StringBuilder` là lỗi: từ Java 9, compiler thường dùng `invokedynamic` cho nối chuỗi.

## 10. Mở rộng: generics và bridge method làm class khác source

Source Java dùng generics nhưng JVM thực thi dựa trên descriptors và, khi cần, compiler-generated bridge methods. Ví dụ này chạy độc lập bằng Java 17+; tạo file **`BridgeDemo.java`**:

~~~java
interface Handler<T> {
    T process(T input);
}

final class TextHandler implements Handler<String> {
    @Override
    public String process(String input) {
        return input + "!";
    }
}

public class BridgeDemo {
    public static void main(String[] args) {
        Handler<String> handler = new TextHandler();
        System.out.println(handler.process("ok"));
    }
}
~~~

~~~bash
javac -g BridgeDemo.java
java BridgeDemo
javap -c -v -s -l -p TextHandler
~~~

**Stdout chương trình:**

~~~text
ok!
~~~

Hãy tìm trong `TextHandler.class`:

- Method thực do source khai báo, có descriptor `(Ljava/lang/String;)Ljava/lang/String;`.
- Một **bridge method** do compiler tạo để đáp ứng hợp đồng kiểu sau generic erasure, mang flags như `ACC_BRIDGE` và `ACC_SYNTHETIC`; descriptor thường là `(Ljava/lang/Object;)Ljava/lang/Object;`.
- Bytecode của bridge có thể `checkcast` từ `Object` sang `String` rồi gọi method thực `process(String)`.
- `Signature` attribute lưu generic type information của `Handler<String>`, trong khi JVM method descriptors dùng erased types.

Ví dụ này minh họa tại sao **`methods_count` và `javap -p` có thể cho thấy phương thức không xuất hiện trực tiếp trong source**. Để hiểu class file, bạn cần nhìn đồng thời `flags`, `descriptor`, `Signature`, `Code` và các quan hệ kế thừa/triển khai.

## 11. Đọc class trong package, JAR và module

### Class nằm trong package

Nếu source khai báo `package demo;` và tên class là `demo.BytecodeTour`, biên dịch vào thư mục `out` (thư mục `out` phải tồn tại hoặc tạo trước) rồi dùng **fully qualified class name**:

~~~bash
javac -g -d out src/demo/BytecodeTour.java
java -cp out demo.BytecodeTour
javap -c -v -s -l -p -classpath out demo.BytecodeTour
~~~

Lệnh minh họa này **giả định bạn đã chuyển ví dụ vào file `src/demo/BytecodeTour.java` và thêm `package demo;` ở đầu**; không chạy nguyên xi với file `BytecodeTour.java` không có package từ mục 3.

### Class nằm trong JAR

~~~bash
javap -c -v -s -l -p -classpath app.jar demo.BytecodeTour
~~~

Không cần khởi chạy JAR để xem class. Nếu ứng dụng có nhiều JAR phụ thuộc, bảo đảm class path bạn đang dùng trỏ tới đúng binary cần phân tích. Nếu là **multi-release JAR**, lưu ý có thể tồn tại biến thể class khác nhau cho các phiên bản Java; hãy xác định đúng entry/phiên bản và dùng tùy chọn `--multi-release` (khi được JDK hỗ trợ) hoặc chỉ định chính xác file class cần xem. Đừng lấy bản base rồi kết luận đó là bản code đang được runtime dùng nếu JAR có versioned entries.

### Class thuộc module của JDK

~~~bash
javap -v -p --module java.base java.lang.String
~~~

Ở đây `--module java.base` giúp chọn module; `java.lang.String` là tên class. Output rất dài và phụ thuộc bản JDK. Đây là bài tập đọc **một class thực của JDK**, không bắt buộc phải hiểu mọi chi tiết của source implementation.

## 12. Các ngộ nhận phổ biến

1. **“`javap -c` chạy bytecode.”** Không. Nó **disassemble** class file; chạy chương trình dùng `java`.
2. **“`javap` luôn khôi phục source Java gốc.”** Không. Source comments, nhiều tên local, biểu thức ban đầu hoặc code đã tối ưu có thể không còn đủ thông tin.
3. **“BCI 17 là dòng source 17.”** Không. BCI là vị trí bytecode, phải dùng `LineNumberTable` mới ánh xạ được sang dòng source khi metadata có mặt.
4. **“`#17` trong `getfield #17` là local slot 17.”** Không. Dấu `#` chỉ **constant-pool index** trong ngữ cảnh này.
5. **“`javap -s` hiển thị chữ ký generic đầy đủ.”** Chủ yếu là **JVM descriptors** đã erase; thông tin generics bổ sung nằm ở `Signature`/`LocalVariableTypeTable` khi được ghi.
6. **“Không có `LocalVariableTable` thì method không có biến local.”** Sai. `max_locals` và bytecode hoạt động bình thường; thiếu metadata debug không ngăn JVM chạy.
7. **“`-v` và `-l` yêu cầu runtime có debug agent.”** Không. Chúng đọc metadata từ class file; việc bảng local/line có hay không phụ thuộc quá trình biên dịch.
8. **“`stack=2` là VM có đúng hai Java stack frames.”** Sai. `max_stack` là số **operand stack slots** tối đa của **một method**.
9. **“Lệnh `invokevirtual` luôn thấy method owner là class của object runtime.”** Không nhất thiết. Constant pool lưu symbolic method reference; runtime dispatch có thể chọn implementation của subclass.
10. **“`javap` nhìn thấy đúng JIT machine code HotSpot đang chạy.”** Sai. `javap` xem **class-file bytecode**; JIT-compiled native code là tầng runtime khác.
11. **“Có `main` trong class nên `javap` tự gọi main.”** Sai. `javap` đọc `method_info`, không thực thi thân method.
12. **“Đầu ra `javap` ở mọi JDK giống nhau từng index và opcode.”** Sai. Biến thể compiler/target version có thể thay đổi constant pool, bytecode và metadata mà vẫn giữ ngữ nghĩa ứng dụng.

## 13. Tự kiểm tra

**Câu 1.** Lệnh nào dùng để vừa xem bytecode vừa xem constant pool, descriptor và line/local tables?  
**Đáp án:** `javap -c -v -s -l -p ClassName`, với lưu ý metadata debug có thể thiếu.

**Câu 2.** `(II)I` và `([Ljava/lang/String;)V` lần lượt nghĩa gì?  
**Đáp án:** Method nhận 2 `int`, trả `int`; method nhận một `String[]`, trả `void`.

**Câu 3.** Trong method instance `compute(int,int)`, slot 0 thường chứa gì?  
**Đáp án:** Tham chiếu `this`. Hai `int` parameters nằm ở slot 1/2; local `sum` có thể ở slot 3.

**Câu 4.** Đầu ra có `5: bipush 10` và `7: if_icmple 14`. Số 5 và 7 là dòng Java?  
**Đáp án:** Không, đó là **BCI**. Địa chỉ 14 là branch target BCI do `javap` trình bày.

**Câu 5.** Tại sao `-l` không hiện `left`, `right` mặc dù bytecode có `iload_1` và `iload_2`?  
**Đáp án:** Có thể class không chứa `LocalVariableTable` do compiler không được bật `-g:vars` hoặc `-g`. Slots vẫn tồn tại; tên local có thể thiếu.

**Câu 6.** `-s` hiện descriptor của `List<String>` là gì và xem generic ở đâu?  
**Đáp án:** Descriptor thường là `Ljava/util/List;` do erasure. Tra `Signature` attribute trong `-v` nếu được compiler ghi.

**Câu 7.** Tại sao `TextHandler` có thể có hai method `process` dù source chỉ viết một?  
**Đáp án:** Compiler tạo **synthetic bridge** phục vụ generic interface method sau erasure.

**Câu 8.** Chỉ có `javap -c`, có thể biết chính xác native assembly do HotSpot JIT tạo không?  
**Đáp án:** Không. `-c` hiển thị JVM bytecode từ class file, không phải máy lệnh native sau JIT.

## 14. Tài liệu chuẩn

- [Oracle JDK 25 — `javap` Command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javap.html): các tùy chọn `-c`, `-v`, `-s`, `-l`, `-p`, classpath/module và multi-release.
- [dev.java — `javap`, the Disassembler](https://dev.java/learn/jvm/tools/core/javap/): ví dụ và cách đọc disassembly trong hệ công cụ JDK.
- [JVMS Java SE 25 — Chapter 4: The class File Format](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html): `ClassFile`, constant pool, descriptors, access flags, `Code`, `Signature`, `LineNumberTable`, `LocalVariableTable`, `LocalVariableTypeTable` và `StackMapTable`.
- [JVMS Java SE 25 — `4.3` Descriptors](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.3): các mã như `I`, `J`, `L...;`, `[` và dấu ngoặc của method descriptor.
- [JVMS Java SE 25 — `4.7.3` Code](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.3): `max_stack`, `max_locals`, bytecode, exception table và code attributes.
- [JVMS Java SE 25 — `4.7.12` LineNumberTable](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.12): ánh xạ BCI sang line number.
- [JVMS Java SE 25 — `4.7.13` LocalVariableTable](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.13): local slots, tên biến, descriptor và phạm vi.
- [Oracle JDK 25 — `javac` Command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javac.html): cách dùng `-g`, `-g:lines,vars,source`, `-g:none` và `--release`.

**Cách học hiệu quả:** Đọc **một phương thức** mỗi lần theo chuỗi **source → descriptor (`-s`) → bytecode (`-c`) → constant pool và attributes (`-v`) → BCI, dòng và local slots (`-l`)**. Ghi nhận những gì được class file thực sự lưu, sau đó mới suy luận về Java source hoặc cách JVM thực thi.

</details>

- [Quay lại đầu trang](#back-to-top)
