<a id="back-to-top"></a>

# Thực nghiệm JVM

## Menu
- [Biên dịch chương trình nhỏ và giải thích từng vùng của kết quả javap](#jvm-experiments-001)
- [Theo dấu local variables và operand stack qua lời gọi lồng nhau](#jvm-experiments-002)
- [Dự đoán rồi kiểm chứng nạp, liên kết và khởi tạo qua ví dụ nhiều lớp](#jvm-experiments-003)
- [Tạo hai bộ nạp cho cùng tên lớp và giải thích định danh kiểu](#jvm-experiments-004)
- [Vẽ đồ thị tham chiếu để phân biệt đối tượng rác với bộ nhớ bị giữ](#jvm-experiments-005)
- [So sánh mẫu cấp phát sống ngắn và giữ lâu bằng log GC](#jvm-experiments-006)
- [Thử các bộ thu gom trong cùng điều kiện và báo cáo cả thông lượng lẫn độ trễ](#jvm-experiments-007)
- [Quan sát warm-up và nhận diện sai lệch do gấp hằng hoặc loại bỏ mã](#jvm-experiments-008)
- [Tái hiện tràn stack và thiếu heap trong tiến trình thử nghiệm có giới hạn](#jvm-experiments-009)
- [Đối chiếu heap, native memory và bộ nhớ tiến trình từ nhiều nguồn đo](#jvm-experiments-010)
- [So sánh khởi động có và không có CDS trên cùng ứng dụng](#jvm-experiments-011)
- [Ghi báo cáo kết nối triệu chứng → giả thuyết → bằng chứng → kết luận → giới hạn](#jvm-experiments-012)

## <a id="jvm-experiments-001">Biên dịch chương trình nhỏ và giải thích từng vùng của kết quả javap</a>

<details>
<summary>Xem chi tiết</summary>

# Biên dịch chương trình nhỏ và giải thích từng vùng của kết quả `javap`

`javap` là **trình phân tích/disassembler class file** đi kèm JDK. Khi đưa cho nó một Java class đã biên dịch, đặc biệt với `-v` và `-c`, ta có thể xem **phiên bản class-file, cờ truy cập, constant pool, descriptors, khai báo field/method, bytecode, thông tin local variables, mapping source lines, exception handlers và những attributes khác**. Đây là một bài thực nghiệm JVM thiết yếu: hiểu source Java được chuyển thành dạng nào trước khi class loader/verifier/interpreter/JIT làm việc.

Mục tiêu là tự làm được vòng lặp:

```text
Java source (.java)
   │
   │ javac -g -encoding UTF-8
   ▼
Class file (.class)
   │
   ├── java ClassName       → chạy chương trình, xem stdout
   │
   └── javap -c -p -l -s -v ClassName
                              │
                              ▼
                  Đọc từng vùng output có chứng cứ
                              │
                              ▼
                  Nối source ↔ descriptor ↔ bytecode
                              │
                              ▼
                  Phân biệt class-file facts với
                  runtime/JIT/compiler implementation
```

**Điểm quan trọng:** `javap` **không chạy chương trình** và không in machine code sau HotSpot JIT. Nó đọc metadata và Java bytecode của class files (hoặc modules/JAR classes). Kết quả từ `javap -v` là **mô tả một artifact biên dịch cụ thể**, không phải bản ghi trạng thái operand stack thật theo từng mili-giây.

Bài dùng **JDK 17+**. Các địa chỉ offset, chỉ số constant pool (`#1`, `#2`...), chuỗi bytecode dùng để nối String và nhiều attributes có thể thay theo **JDK/javac version**, debug flags và source constructs. Vì thế các khung ghi “minh họa” không được coi là bản sao nguyên văn bắt buộc trên mọi JVM.

## 1. Chuẩn bị công cụ và nhận diện phiên bản

Dùng một JDK có đủ `javac`, `java` và `javap`:

```bash
java -version
javac -version
javap -version
javap --help
```

Trên một máy có nhiều JDK installations, `java` dùng để chạy có thể **khác** `javac` dùng để biên dịch. Nên ghi rõ:

- Java launcher/runtime vendor, version và build.
- Compiler `javac` version.
- `javap` version.
- Hệ điều hành và architecture.
- Class file path, nếu cần thì SHA-256 digest.
- Các compiler options, đặc biệt `-g` và `--release`.

### Không nhầm Java source level với class-file version

Một class file chứa `major version` và `minor version`. Với upstream Java class-file format thường gặp:

| Target release | Class-file major version |
| --- | --- |
| Java 8 | 52 |
| Java 11 | 55 |
| Java 17 | 61 |
| Java 21 | 65 |
| Java 25 | 69 |

Nếu biên dịch bằng JDK 21 với `--release 17` thì mục tiêu là API/language/class-file compatibility của Java 17, **không** phải “class file luôn có major=65 chỉ vì compiler binary là JDK 21”.

**`UnsupportedClassVersionError`** thường xuất hiện khi một JVM **cũ hơn class-file target** cố load class mới. Cần kiểm tra đúng major version, compiler target và runtime; không đổi byte major bằng hex editor để “sửa”.

### Dùng `--release` khi cần tương thích mục tiêu

Trên JDK mới có hỗ trợ target đó:

```bash
javac --release 17 -g -encoding UTF-8 JavapFieldGuide.java
```

`--release 17` giới hạn language/API target theo Java 17; không “biến JVM đang chạy thành 17”. Với JDK 17, lệnh `javac -g -encoding UTF-8` là đủ cho bài lab, vì target mặc định tương ứng JDK đó.

## 2. Chương trình Java 17+ duy nhất cho thực nghiệm

Tạo file **`JavapFieldGuide.java`**:

```java
public final class JavapFieldGuide {
    private static final int SCALE = 3;
    private final int bias;

    public JavapFieldGuide(int bias) {
        this.bias = bias;
    }

    static int sum(int left, int right) {
        int total = left + right;
        return total;
    }

    int compute(int input) {
        int adjusted = sum(input, bias);
        if (adjusted >= 10) {
            return adjusted * SCALE;
        }
        return adjusted + 1;
    }

    static int safeDivide(int numerator, int denominator) {
        try {
            return numerator / denominator;
        } catch (ArithmeticException ex) {
            return -1;
        }
    }

    public static void main(String[] args) {
        JavapFieldGuide demo = new JavapFieldGuide(4);
        int first = demo.compute(6);
        int second = demo.compute(2);
        int divided = safeDivide(9, 0);
        String label = "value=" + first;

        System.out.println("first: " + first);
        System.out.println("second: " + second);
        System.out.println("divide: " + divided);
        System.out.println("label: " + label);
        System.out.println("done");
    }
}
```

Đây là một source file **độc lập**; không cần Maven, Gradle, thư viện bên ngoài hay download dependencies.

### Biên dịch

```bash
javac -g -encoding UTF-8 JavapFieldGuide.java
```

`-g` yêu cầu phát sinh debug information gồm source/line/local-variable tables khi thích hợp. Nếu không có `-g`, mặc định `javac` có thể giữ source/line nhưng **không** giữ LocalVariableTable chứa mọi tên biến; đây là lý do bài yêu cầu `-g`.

### Chạy và kiểm tra chức năng

```bash
java JavapFieldGuide
```

**Standard output chính xác** khi chương trình chạy thành công và không bật logging tùy chọn:

```text
first: 30
second: 7
divide: -1
label: value=30
done
```

**Giải thích xác định:**

1. `demo` có `bias=4`.
2. `compute(6)` → `sum(6,4)=10`, điều kiện `10 >= 10` đúng, trả `10*3=30`.
3. `compute(2)` → `sum(2,4)=6`, điều kiện sai, trả `6+1=7`.
4. `safeDivide(9,0)` ném `ArithmeticException` từ `idiv` rồi được handler bắt, trả `-1`.
5. `label` được nối từ chuỗi `"value="` với `first=30`, thành `"value=30"`.
6. Năm lời gọi `println` tạo năm dòng theo thứ tự của thread `main`.

**Không dùng output chức năng này để suy JIT đã compile hay GC đã chạy:** chương trình quá nhỏ và output là kết quả Java semantics, không phải benchmark.

## 3. Bộ lệnh `javap` đọc theo cấp độ

Các lệnh trên class vừa tạo:

```bash
javap JavapFieldGuide
javap -p JavapFieldGuide
javap -c -p JavapFieldGuide
javap -s -p JavapFieldGuide
javap -l -p JavapFieldGuide
javap -v -p JavapFieldGuide
```

Để có bản đầy đủ phục vụ phân tích:

```bash
javap -c -l -s -p -v JavapFieldGuide
```

| Option | Công dụng | Điều cần lưu ý |
| --- | --- | --- |
| `-c` | In disassembled Java bytecode trong từng method | Không phải machine assembly |
| `-p` / `-private` | Hiện cả private members | Default có thể che fields/methods |
| `-l` | In line/local-variable tables | Chỉ có tên locals khi class chứa debug metadata |
| `-s` | In internal type descriptors | Khác Java source signature và generic Signature |
| `-v` / `-verbose` | In thêm class header, constant pool, attributes... | Output dài; thường chứa nhiều vùng khác |
| `-sysinfo` | In path/size/modified-time/hash theo JDK implementation | Hữu ích đối chiếu artifact, không là runtime state |
| `-classpath <path>` | Chọn nơi tìm class file | Tránh đọc nhầm class cũ hoặc khác build |
| `-public` / `-protected` / `-package` | Giới hạn mức hiển thị access | Không dùng nếu cần private fields |
| `--module <name>` | Phân tích class trong module hỗ trợ | Cần class/module thực tồn tại |
| `-constants` | Hiện final constants theo format tool | Không thay Code/ConstantValue analysis |

Các option có thể kết hợp. `-v` thường cho nhiều thông tin mà các option khác cũng in, nhưng ghi riêng từng option giúp hiểu **phần nào của output xuất hiện vì lý do nào**.

### Đọc đúng file class

Nếu có nhiều class files cùng tên ở những directories khác nhau:

```bash
javap -classpath . -v -p JavapFieldGuide
```

`-classpath .` chọn current directory trong ví dụ một thư mục đơn giản. Với ứng dụng module/JAR/class-loader phức tạp, classpath/module resolution cần khai báo đúng. `javap` phân tích **class được tìm thấy**, không bảo đảm đó chính là implementation class mà production process đang load.

### Không cần chạy chương trình trước `javap`

`javac` sinh `.class`; ngay sau khi compile thành công đã có thể dùng `javap`. Không có yêu cầu class phải được JVM load hoặc thực thi một lần mới xuất hiện Constant pool/Code attributes.

## 4. Bản đồ tổng quát các vùng thường gặp trong `javap -v`

Một output của `javap -v -p JavapFieldGuide` có thể được chia **theo ý nghĩa** như sau:

```text
Classfile .../JavapFieldGuide.class
  Last modified ...
  SHA-256 checksum ...
  Compiled from "JavapFieldGuide.java"
  public final class JavapFieldGuide
  minor version: ...
  major version: 61             (nếu target Java 17)
  flags: ACC_PUBLIC, ACC_FINAL, ACC_SUPER
  this_class: #...
  super_class: #...
  interfaces: 0, fields: 2, methods: 5, attributes: ...
Constant pool:
  #1  = Methodref ...
  #2  = Class ...
  #3  = NameAndType ...
  ...
{
  private static final int SCALE;
    descriptor: I
    flags: ...
    ConstantValue: int 3

  private final int bias;
    descriptor: I
    flags: ...

  public JavapFieldGuide(int);
    descriptor: (I)V
    flags: ACC_PUBLIC
    Code:
      stack=..., locals=..., args_size=...
      0: aload_0
      ...
      LineNumberTable:
      LocalVariableTable:

  static int sum(int, int);
    descriptor: (II)I
    Code:
      ...

  int compute(int);
    descriptor: (I)I
    Code:
      ...
      StackMapTable:

  static int safeDivide(int, int);
    descriptor: (II)I
    Code:
      ...
      Exception table:
      StackMapTable:

  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    Code:
      ...
}
SourceFile: "JavapFieldGuide.java"
BootstrapMethods:
  ... (nếu javac dùng invokedynamic để nối String)
InnerClasses:
  ... (có thể xuất hiện từ bootstrap-linked helpers)
```

**Đây là bản đồ minh họa, không phải output nguyên văn.** Một số phần có thể xuất hiện ở thứ tự/định dạng khác, hoặc không có nếu compiler sinh bytecode khác. `fields: 2` đếm `SCALE` và `bias`; methods đếm constructor, `sum`, `compute`, `safeDivide`, `main`. Không có static initializer `<clinit>` bắt buộc trong source này vì `SCALE` là một compile-time constant.

## 5. Vùng Classfile và `Compiled from`

Đầu output verbose thường có metadata của artifact:

```text
Classfile /.../JavapFieldGuide.class
  Last modified ...; size ... bytes
  SHA-256 checksum ...
  Compiled from "JavapFieldGuide.java"
```

Đây là **mẫu với dữ liệu hệ thống bị lược bỏ**, không phải values cố định.

- **Classfile path:** nơi `javap` tìm thấy binary, không nhất thiết là đường dẫn production.
- **Last modified/size:** thuộc filesystem/artifact đang đọc, có thể đổi khi rebuild.
- **Checksum:** xác nhận bytes của artifact khi tool hiển thị; hữu ích để so hai builds.
- **Compiled from:** lấy từ `SourceFile` attribute nếu có; không bắt buộc bao gồm toàn bộ path/các dòng source.
- **Tên public class:** từ class-file structures, không cần source hiện diện lúc `javap` chạy.

**Không dựa vào tên file một mình** để nói hai lớp giống nhau. Nếu một JAR được rebuild với dependencies hoặc toolchain khác, content/metadata có thể thay dù tên class giống.

### `javap` không hiển thị được gì ở đây?

Header không cho biết workload production, heap used, JIT C2 đã inline thế nào, số threads đang chạy hay JVM flags của process khác. Những câu hỏi đó dùng monitoring, `jcmd`, JFR hoặc profiler.

## 6. Minor/Major version và preview class files

`major version: 61` là dấu hiệu Java 17 class-file target; **không chứng minh** compiler binary phải là JDK 17, bởi JDK mới có thể compile target 17.

`minor version` thường 0 trong class files bình thường. Với **preview features** theo quy tắc JVMS, các class files ở preview-capable format có thể dùng special minor version `65535` cùng những điều kiện/version tương ứng và yêu cầu preview enablement khi runtime load.

**Không dùng preview bytecode trên runtime chỉ vì major version hợp:** preview có rules về version/platform matching và runtime flags, không phải tùy ý trộn JDK update hay chỉ thêm `--enable-preview` vào một runtime cũ hơn.

### Một lỗi thực tế

```text
java.lang.UnsupportedClassVersionError: ...
```

Hướng khắc phục:

1. Đọc message về class-file version và runtime maximum.
2. Kiểm tra `javap -v` của class đang nạp.
3. Kiểm tra `java -version` của **process/launcher thực**.
4. Nếu phải chạy Java 17, biên dịch với JDK tương thích và `--release 17`, kiểm tra APIs cũng tương thích.
5. Không sửa class-file major byte tùy tiện; bytecode/API mới có thể không được runtime cũ hiểu.

## 7. `flags` và `access_flags` của class

Header:

```text
flags: (0x...) ACC_PUBLIC, ACC_FINAL, ACC_SUPER
```

Số hex tùy flags thực, nên không coi “0x...” là byte được in y hệt.

| Cờ | Ý nghĩa trong class-file context |
| --- | --- |
| `ACC_PUBLIC` | Class/member có access level public |
| `ACC_FINAL` | Class không thể được subclass theo Java class semantics |
| `ACC_SUPER` | Class-file flag liên quan `invokespecial` historical/special semantics |
| `ACC_INTERFACE` | Đây là interface |
| `ACC_ABSTRACT` | Abstract class/interface/method tùy context |
| `ACC_SYNTHETIC` | Thành phần được đánh dấu synthetic |
| `ACC_ENUM` | Enum class/field theo class-file rules |
| `ACC_ANNOTATION` | Annotation interface |
| `ACC_MODULE` | Module descriptor class-file construct |

**Không lẫn flags của class với flags của field/method**. Một field `final` và một method `final` có ngữ nghĩa khác, dù đều có bit flag tên `ACC_FINAL`.

### `this_class`, `super_class`, interfaces

```text
this_class: #...     // JavapFieldGuide
super_class: #...    // java/lang/Object
interfaces: 0, ...
```

`this_class` và `super_class` trỏ tới **constant-pool class entries**. Chỉ số `#...` là **index của entry**, không phải object address hoặc heap identity. `interfaces: 0` ở ví dụ vì class không khai báo `implements`.

## 8. Vùng Constant pool: bảng tham chiếu biểu tượng

`Constant pool` là phần nhiều người mới học `javap` tưởng là bảng “các hằng số Java literal” thuần túy. Thật ra nó chứa cả **class names, field references, method references, string constants, descriptors, name/type pairs, method handles, invokedynamic call sites và UTF-8 strings**.

Ví dụ minh họa:

```text
Constant pool:
   #1 = Methodref          #2.#3
   #2 = Class              #4          // java/lang/Object
   #3 = NameAndType        #5:#6       // "<init>":()V
   #4 = Utf8               java/lang/Object
   #5 = Utf8               <init>
   #6 = Utf8               ()V
   ...
```

Những `#n` **minh họa**, compiler của bạn có thể đặt `java/lang/Object` hoặc `Methodref` ở indices khác. Hãy đọc số chính xác của file đã biên dịch, không học thuộc chỉ số.

### Các entry hay gặp

| Tag / output | Ý nghĩa |
| --- | --- |
| `Utf8` | Chuỗi nội bộ class file cho tên và descriptor/metadata |
| `Class` | Symbolic representation của class/interface |
| `String` | String constant tham chiếu UTF8 nội bộ |
| `Integer`/`Float`/`Long`/`Double` | Giá trị số constant-pool khi cần |
| `NameAndType` | Cặp tên member + descriptor |
| `Fieldref` | Symbolic reference tới field |
| `Methodref` | Symbolic reference tới class method/constructor |
| `InterfaceMethodref` | Symbolic reference tới interface method |
| `MethodHandle` | Handle tới field/method theo reference kind |
| `MethodType` | Method descriptor được biểu diễn thành method type constant |
| `InvokeDynamic` | Dynamic call-site specifier, liên hệ bootstrap methods |
| `Dynamic` | Dynamically computed constant (constant dynamic) |
| `Module`/`Package` | Module/package constant-pool entries trong những class files phù hợp |

Không phải mọi class đều có đủ các loại trên. `InvokeDynamic` chỉ có khi compiler/runtime pattern cần; `Long`/`Double` constants có thể dùng hai **constant pool index positions** trong class file theo JVMS rules.

### `#12` không phải source line 12

Khi đọc:

```text
invokestatic #12  // Method sum:(II)I
```

số `#12` là **constant-pool index** được instruction tham chiếu. Nó **không** phải bytecode offset, Java source line, heap address hoặc local variable slot. Những hệ đánh số này hoàn toàn khác nhau.

## 9. Liên kết Constant pool với bytecode

Ví dụ constructor gọi `Object.<init>`:

```text
aload_0
invokespecial #... // Method java/lang/Object."<init>":()V
```

- `aload_0` đẩy reference `this` từ local slot 0 lên operand stack.
- `invokespecial` nhận symbolic method reference từ constant pool.
- JVM linking/resolution chọn implementation theo rules; thực thi constructor của superclass.
- Callee frame tuân thủ invocation semantics; constructor return `void`.
- Sau đó constructor của `JavapFieldGuide` lưu `bias` bằng `putfield`.

Ví dụ method `compute` có:

```text
invokestatic #... // Method sum:(II)I
```

Dòng comment từ `javap` giúp con người hiểu symbolic target `sum` có **hai int arguments** và **int return**. Runtime có thể tối ưu lời gọi bằng JIT/inlining, nhưng **class-file bytecode vẫn có entry và instruction tương ứng**.

**Không đọc constant pool như bảng ánh xạ địa chỉ JIT native cố định.** JVM có dynamic linking, class-loader identity, resolution và runtime dispatch semantics.

## 10. Method descriptors vs Java source signatures

`javap -s -p JavapFieldGuide` cho internal descriptors:

```text
static int sum(int, int);
  descriptor: (II)I

int compute(int);
  descriptor: (I)I

static int safeDivide(int, int);
  descriptor: (II)I

public JavapFieldGuide(int);
  descriptor: (I)V

public static void main(java.lang.String[]);
  descriptor: ([Ljava/lang/String;)V
```

Đây là những descriptors đúng theo **source**, dù thứ tự method/spacing output có thể đổi.

| Ký hiệu | Java type |
| --- | --- |
| `I` | `int` |
| `J` | `long` |
| `D` | `double` |
| `F` | `float` |
| `Z` | `boolean` |
| `B` | `byte` |
| `C` | `char` |
| `S` | `short` |
| `V` | `void` ở return type |
| `Ljava/lang/String;` | Reference `java.lang.String` |
| `[I` | Array `int[]` |
| `[Ljava/lang/String;` | Array `String[]` |

### Ba ví dụ cần tự dịch

```text
(II)I                    (int,int) → int
(I)V                     (int) → void
([Ljava/lang/String;)V   (String[]) → void
```

**Constructor `<init>` có return type `V` ở descriptor**, dù trong Java source constructor không viết `void`. JVM dùng một special instance initialization method với descriptor `(I)V` trong ví dụ.

### Generics không được giữ nguyên trong descriptor thông thường

Với `List<String>`, descriptor dùng erased type (thường `Ljava/util/List;`). Generic type information, nếu được phát hành, nằm ở **Signature attribute**, **không** thay descriptor phục vụ bytecode invocation/linkage. Đây là hệ quả của Java generic type erasure.

## 11. Vùng fields: descriptor, flags và ConstantValue

Ví dụ hai fields:

```java
private static final int SCALE = 3;
private final int bias;
```

`javap -v -p` có thể cho:

```text
private static final int SCALE;
  descriptor: I
  flags: (...) ACC_PRIVATE, ACC_STATIC, ACC_FINAL
  ConstantValue: int 3

private final int bias;
  descriptor: I
  flags: (...) ACC_PRIVATE, ACC_FINAL
```

**`SCALE` là compile-time constant** kiểu primitive và value constant expression, nên class file có thể có `ConstantValue`. Trong `compute`, compiler có thể trực tiếp dùng `iconst_3`/`imul`, **không cần `getstatic SCALE`**: giá trị đã được inline ở cấp compile-time semantics.

`bias` là field của **từng object instance**, không phải local variable slot 1 tồn tại vĩnh viễn. Khi một method đọc `bias`, bytecode dùng object reference và `getfield`.

### `ConstantValue` khác `Code`

- `ConstantValue` là **field attribute** dành cho loại constant fields phù hợp, không phải lệnh gán biến chạy trong một method.
- `Code` là **method attribute** chứa bytecode và thông tin thực thi.
- Static initialization cần thực hiện tại runtime cho những trường hợp khác có thể nằm trong `<clinit>`, nhưng **không phải field nào cũng yêu cầu một `<clinit>` method riêng**.

### Vì sao không có phương thức `setScale`?

`javap` không tự bịa method cho một field. Nó in các method definitions có trong class file, kể cả synthetic methods nếu compiler thật sự phát hành và command hiển thị. Không suy từ việc field tồn tại mà chắc chắn có getter/setter.

## 12. Vùng methods: access flags, name, descriptor và attributes

Một method có thể hiển thị:

```text
static int sum(int, int);
  descriptor: (II)I
  flags: (0x0008) ACC_STATIC
  Code:
    stack=2, locals=3, args_size=2
    ...
```

Với `javac` và source của bài, đây là **mẫu điển hình** cho `sum`; số `stack=2, locals=3, args_size=2` có lý do cụ thể:

- `left` ở local slot 0.
- `right` ở local slot 1.
- `total` ở local slot 2.
- `iadd` cần hai int values trên operand stack, nên max depth **2**.
- Có hai parameter int slots, vì `sum` là static và **không có `this`**.

Đối với **instance method** như `compute(int)`:

- Local slot **0** là `this`.
- Slot **1** là argument `input`.
- Slot sau đó có thể chứa `adjusted`.

### Method flags khác class flags

| Method flag | Ý nghĩa |
| --- | --- |
| `ACC_PUBLIC` | Method public |
| `ACC_PRIVATE` | Method private |
| `ACC_STATIC` | Không có receiver `this` |
| `ACC_FINAL` | Không được override theo ngữ nghĩa Java |
| `ACC_SYNCHRONIZED` | Method synchronized, monitor semantics khi invocation |
| `ACC_NATIVE` | Body native; không có `Code` Java-bytecode attribute |
| `ACC_ABSTRACT` | Không có method body/`Code` |
| `ACC_BRIDGE` | Compiler-generated bridge, thường liên quan generics |
| `ACC_SYNTHETIC` | Đánh dấu compiler-generated/synthetic method |

**Không mọi method đều có Code**: abstract và native methods không có Java bytecode body như methods thường. Đừng cho rằng `javap -c` lỗi nếu một abstract declaration không có `Code`.

## 13. Code attribute: `stack`, `locals`, `args_size`

Theo JVMS `4.7.3`, `Code` của non-abstract/non-native method chứa:

- `max_stack`: **max operand-stack depth** theo units.
- `max_locals`: số local variable **slots** cần cho frame method.
- `code_length`/`code[]`: instruction bytes.
- `exception_table`: những vùng mã được bảo vệ và handlers.
- `attributes` lồng trong Code, như `LineNumberTable`, `LocalVariableTable` và `StackMapTable` khi có.

Một output dạng:

```text
Code:
  stack=2, locals=3, args_size=2
```

không nói rằng **JVM call stack có hai nested frames**. `stack=2` là giới hạn **operand stack của một invocation**. `locals=3` là ba slots dành cho `sum`; nó không có nghĩa có ba object heap.

### Category-2 values

`long` và `double` chiếm **hai local variable slots** và hai operand-stack units theo JVMS. Vì vậy method có hai values `long` trên operand stack có depth 4 units dù bạn đếm “hai giá trị”.

### Per-invocation frame

Mỗi lần `sum(1,2)` hoặc `sum(3,4)` được gọi có **local array/operand stack logic riêng**. Không có một local slot 2 chung toàn JVM cho mọi method `sum`.

### `args_size` thuộc hiển thị `javap`

Con số `args_size` do `javap` trình bày dựa trên descriptor và instance/static convention. Nó không phải một field độc lập có tên `args_size` trong `Code_attribute` structure của JVMS; cấu trúc spec chính thức dùng `max_stack` và `max_locals`.

## 14. Đọc từng instruction của `sum` và theo dõi operand stack

Một bytecode sequence thông thường của `sum` trên JDK 17:

```text
static int sum(int, int);
  Code:
     0: iload_0
     1: iload_1
     2: iadd
     3: istore_2
     4: iload_2
     5: ireturn
```

Đối với `sum(6,4)`, locals lúc vào:

```text
locals: [L0=6, L1=4, L2=?]
operand stack ban đầu: []
```

| Offset | Opcode | Local variables sau bước | Operand stack sau bước |
| --- | --- | --- | --- |
| 0 | `iload_0` | `[6,4,?]` | `[6]` |
| 1 | `iload_1` | `[6,4,?]` | `[6,4]` |
| 2 | `iadd` | `[6,4,?]` | `[10]` |
| 3 | `istore_2` | `[6,4,10]` | `[]` |
| 4 | `iload_2` | `[6,4,10]` | `[10]` |
| 5 | `ireturn` | Callee kết thúc | Giá trị 10 được trả về caller |

**Những offset ở khung này là dạng compiler có thể phát sinh với source rất đơn giản**, nhưng phải lấy output `javap` của class đã biên dịch để xác minh exact bytes. Compiler khác có thể không lưu `total` theo đúng cùng sequence nếu implementation cho phép.

### Phân biệt local variable và operand stack

- `iload_0` **copy int value** từ local slot lên operand stack, không xóa slot.
- `iadd` lấy hai operands từ operand stack và push tổng.
- `istore_2` pop value để gán vào local slot 2.
- `ireturn` chuyển kết quả cho caller, không “đổi thẳng” source variable trong caller bằng tên.

**Không có một operand stack toàn cục chứa dữ liệu của mọi method**; mỗi frame có operand stack riêng.

## 15. Đọc bytecode constructor `JavapFieldGuide(int)`

Source:

```java
public JavapFieldGuide(int bias) {
    this.bias = bias;
}
```

Code dạng điển hình:

```text
0: aload_0
1: invokespecial #... // Method java/lang/Object."<init>":()V
4: aload_0
5: iload_1
6: putfield #...      // Field bias:I
9: return
```

Cách hiểu:

1. `aload_0` push reference tới receiver `this` (local slot 0).
2. `invokespecial` gọi superclass constructor `Object.<init>()`.
3. `aload_0` push `this` một lần nữa.
4. `iload_1` push constructor argument `bias`.
5. `putfield` tiêu thụ object reference và int value, ghi field `bias` trên object.
6. `return` hoàn tất constructor method có descriptor `(I)V`.

### Những gì constructor không làm

- Không copy toàn bộ object vào local array.
- Không tự tạo một biến `this` thứ hai nằm ở Java heap.
- Không dùng `ireturn`, bởi `<init>` có descriptor result `V`.
- Không chứng minh object đã bị publish an toàn tới thread khác; constructor completion và Java Memory Model publication là vấn đề riêng.

Nếu có bytecode `new`/`dup` tại caller, `dup` sao chép **reference**, không cấp phát một object thứ hai.

## 16. Đọc `compute(int)`: gọi method, field access, branch

Method:

```java
int compute(int input) {
    int adjusted = sum(input, bias);
    if (adjusted >= 10) {
        return adjusted * SCALE;
    }
    return adjusted + 1;
}
```

Các loại instructions dự kiến:

- `iload_1`: load argument `input`.
- `aload_0`: load `this`.
- `getfield`: đọc `bias` từ object.
- `invokestatic`: gọi `sum:(II)I`.
- `istore_2`: lưu `adjusted`.
- `bipush 10` / `if_icmp*`: so sánh với ngưỡng 10.
- `iconst_3` và `imul`: phép nhân với compile-time constant `SCALE`.
- `iconst_1` và `iadd`: nhánh còn lại.
- `ireturn`: trả `int`.

Đây là **hình dạng dự kiến**, không cam kết một opcode branch cụ thể hoặc offsets cố định: `javac` có thể chọn `if_icmplt` rồi jump qua nhánh true, hay kiểu control flow hợp lệ khác.

### Theo dấu một lần chạy có nhánh true

```text
this.bias = 4; input = 6

local0 = this
local1 = input = 6
local2 = adjusted = ?

iload_1                → [6]
aload_0                → [6, this]
getfield bias          → [6, 4]
invokestatic sum       → callee locals [6,4]
sum returns 10         → caller stack [10]
istore_2               → L2=10, stack []
comparison with 10     → condition is true
load adjusted          → [10]
push SCALE constant 3  → [10,3]
imul                   → [30]
ireturn                → caller receives 30
```

### Theo dấu nhánh false

Với `input=2`:

```text
sum(2,4) = 6
adjusted = 6
6 >= 10 is false
6 + 1 = 7
return 7
```

**Giá trị Java source và bytecode control flow phải đồng nhất.** Nếu `javap` dùng `if_icmplt` thay vì `if_icmpge`, đó có thể chỉ là cách compiler **đảo điều kiện để nhảy sang nhánh else**, không phải bug.

## 17. Branch offsets không phải Java source line

Trong `Code`:

```text
12: if_icmplt  21
15: iload_2
16: iconst_3
17: imul
18: ireturn
21: iload_2
...
```

Đây là **offset minh họa**. Các số đứng trước opcode (12, 15, 21...) là **bytecode instruction offsets trong code[] của method**; operand của jump chỉ target offset theo disassembler presentation.

Đừng nhầm:

- **Bytecode offset**: vị trí instruction trong `Code.code[]`.
- **Constant pool `#n`**: index symbolic reference.
- **Local slot**: vị trí trong local variable array của một frame.
- **Source line number**: dòng của `.java` qua `LineNumberTable`.
- **Runtime machine address**: implementation address sau JIT, không hiện trong output `javap` thông thường.

### Một instruction không nhất thiết dài đúng 1 byte

Opcode có thể đi kèm operand bytes. Ví dụ `invokestatic #n` thường chiếm opcode + 2 bytes index nên offsets có bước nhảy lớn hơn 1. Một bytecode `tableswitch` hoặc `lookupswitch` còn có alignment/padding và offsets dài. Không thể suy “offset tăng 1” cho mọi lệnh.

## 18. `LineNumberTable`: ánh xạ bytecode sang dòng Java

Khi compile với debug line information, `javap` có thể in:

```text
LineNumberTable:
  line 11: 0
  line 12: 4
  line 13: 9
```

**Số dòng/offset chỉ minh họa**, không phải mapping đã đo của file này.

- Mỗi entry chỉ mapping từ **start_pc** của một đoạn bytecode sang **Java source line number**.
- Một source line có thể phát sinh nhiều instructions.
- Một source expression có thể không tương ứng duy nhất một bytecode instruction.
- Compiler-generated instructions cũng có thể được gắn chung line.
- Optimizations/JIT ở runtime có thể làm execution/stack-trace location phức tạp hơn.
- Debug table **không có nghĩa trình disassembler khôi phục lại được Java source hoàn chỉnh**.

### Khi nào LineNumberTable vắng mặt?

`javac -g:none` loại bỏ những debug attributes mà compiler có thể sinh, bao gồm source/line/local-variable tables theo lựa chọn. Khi thiếu `LineNumberTable`, class vẫn có thể chạy bình thường, nhưng stack traces/debugging có thể kém thông tin source line.

**Không tuyên bố class không có `LineNumberTable` là sai định dạng**. Nhiều class files hợp lệ không mang debug metadata.

## 19. `LocalVariableTable`: names, slots và live ranges

Nếu compile với `-g`:

```bash
javac -g -encoding UTF-8 JavapFieldGuide.java
javap -l -p JavapFieldGuide
```

Một local-variable table của `sum` về mặt ý nghĩa có thể trông như:

```text
LocalVariableTable:
  Start  Length  Slot  Name   Signature
  0      ...      0    left    I
  0      ...      1    right   I
  4      ...      2    total   I
```

Các `Start`/`Length` là **bytecode offset ranges**, không phải wall-clock lifetime milliseconds. Compiler/bytecode build quyết định exact values.

### Phân tích các cột

| Cột | Ý nghĩa |
| --- | --- |
| `Start` | Bắt đầu phạm vi bytecode mà debug entry mô tả |
| `Length` | Độ dài phạm vi code tính theo bytecode offsets |
| `Slot` | Local-variable slot index |
| `Name` | Tên source/debug mà compiler lưu |
| `Signature` | Descriptor kiểu của local theo bảng hiển thị |

Một slot có thể được compiler **tái sử dụng** cho nhiều locals có scopes/liveness không trùng nhau. Đừng suy “slot 2 luôn là biến `total` suốt toàn method” nếu table có nhiều entries cùng slot với ranges khác.

### LocalVariableTable là optional debug attribute

Không có table không có nghĩa method không có local variables: `Code.max_locals` và bytecode `iload/istore/aload` vẫn xác định local slots. Đây là sự phân biệt giữa **runtime execution data** và **debug name metadata**.

## 20. `LocalVariableTypeTable` và generic Signature

Với một class dùng generics như:

```java
static <T extends Number> T identity(T value) {
    return value;
}
```

Đây là **snippet minh họa độc lập về cú pháp method**, không thuộc `JavapFieldGuide.java`. Compiler thường erase `T` thành upper bound `Number` ở method descriptor, trong khi generic type parameters có thể được bảo toàn ở **Signature attribute**. Nếu có debug local-variable type metadata, `LocalVariableTypeTable` có thể thể hiện generic type signatures.

**Không nhầm ba trường:**

1. **Descriptor**: kiểu erased phục vụ bytecode linking/verifier.
2. **Signature attribute**: generic type declarations/arguments được giữ theo class-file format.
3. **LocalVariableTable/LocalVariableTypeTable**: debug mapping tên/kiểu locals trong phạm vi offsets.

Việc `javap` hiển thị `List<String>` trong signature **không có nghĩa JVM tự giữ String type parameter reified** ở runtime object của một `List` bình thường.

## 21. `Exception table`: nơi JVM tìm catch handler

Trong `safeDivide`:

```java
static int safeDivide(int numerator, int denominator) {
    try {
        return numerator / denominator;
    } catch (ArithmeticException ex) {
        return -1;
    }
}
```

Một trình tự logical bytecode:

```text
iload_0
iload_1
idiv
ireturn
handler:
  astore_2            // exception object
  iconst_m1
  ireturn
```

Cùng một `Code` có **Exception table** mô tả:

```text
Exception table:
   from   to   target   type
   ...    ...   ...      Class java/lang/ArithmeticException
```

Các con số cụ thể phải lấy từ `javap` trên file bạn build. `from` inclusive, `to` exclusive trong cách JVM định nghĩa protected interval. `target` là bytecode offset của handler.

### Cơ chế điều khiển

1. `idiv` với denominator 0 ném `ArithmeticException`.
2. JVM tìm handler phù hợp trong method (và unwind nếu cần).
3. Tại handler, operand stack chứa **exception reference** theo JVMS.
4. `astore_2` có thể lưu reference vào local slot cho `ex`.
5. `iconst_m1; ireturn` trả `-1`.

**Không có normal `ireturn` của nhánh chia khi `idiv` đã throw**. Catch handler không phải “hàm được gọi” bằng `invokestatic`; JVM chuyển control flow trong cùng method frame khi match handler.

### `Exception table` khác `Exceptions attribute`

- **Exception table trong `Code`**: mô tả các handlers cho exceptions được throw trong instruction ranges.
- **Exceptions attribute của method**: liệt kê declared checked exceptions từ `throws` (nếu có).
- Một method có `try/catch` **không cần khai báo `throws`**, và method có `throws IOException` **không nhất thiết có catch handler**.

**Sai lầm phổ biến:** thấy `Exceptions` attribute rồi kết luận method đã xử lý exception; nó có thể chỉ thông báo checked exception declaration cho compiler/caller.

## 22. `StackMapTable`: verification state, không phải stack trace

Với `if/else` và `catch`, class file thường cần **StackMapTable** phục vụ bytecode verification, đặc biệt khi có control-flow merges và handlers.

```text
StackMapTable: number_of_entries = ...
  frame_type = ...
  locals = [...]
  stack = [...]
```

Đó là **mẫu định dạng**, không phải values cố định.

- Mô tả **types/states tại các bytecode verification points**.
- Không phải bản ghi operands thực tế của một lần chạy.
- Không phải `Thread.getStackTrace()`.
- Không là trace thời gian JIT, CPU registers hoặc heap objects.
- Có thể encode compressed frames như `same_frame`, `append_frame`, `same_locals_1_stack_item_frame`, tùy code structure.

### Vì sao handlers thường có một reference trên stack?

Khi vào catch handler, execution state cho verifier tương ứng exception object reference ở operand stack. StackMapTable có thể mô tả điều đó bằng frame type với một stack item, trong khi **giá trị exception instance thực tế** chỉ xuất hiện khi chương trình chạy.

**Không đồng nhất** `stack=1` trong StackMapTable với `Code.max_stack=1`; một cái là type state tại checkpoint, một cái là giới hạn depth toàn method.

## 23. `BootstrapMethods` và `invokedynamic` ở Java 17

Từ Java 9, `javac` thường biên dịch **String concatenation** thành `invokedynamic` gắn với `StringConcatFactory` thay vì bắt buộc tạo `new StringBuilder` trong bytecode. Trong ví dụ:

```java
String label = "value=" + first;
```

`javap -c -v` trên một `javac` phù hợp có thể hiện:

```text
iload_2
invokedynamic #... // makeConcatWithConstants:(I)Ljava/lang/String;
astore ...
```

Đây là **minh họa**; local slot của `first` trong `main` và CP index phải lấy trực tiếp từ class file, và compiler có thể chọn sequence hợp lệ khác.

Ở cuối output có thể thấy:

```text
BootstrapMethods:
  0: #... REF_invokeStatic java/lang/invoke/StringConcatFactory.makeConcatWithConstants:...
    Method arguments:
      #... value=
```

- `InvokeDynamic` constant-pool entry mô tả một dynamic call site.
- `BootstrapMethods` class-level attribute ghi bootstrap method handles và static arguments.
- Runtime bootstrap/linkage tạo call site theo semantics của `invokedynamic`.
- `makeConcatWithConstants` là implementation strategy tiêu biểu của `javac` Java 17.
- Dấu `` trong concat recipe có thể đóng vai trò placeholder cho argument trong recipe syntax; không phải String literal “backslash-u0001” mà Java app nhất thiết in.
- **Application stdout** vẫn là `label: value=30`.

**Không suy** vì có `invokedynamic` thì ứng dụng tự động multi-threaded hay gọi reflection. Đây là JVM invocation mechanism có nhiều ứng dụng: lambdas, method references và String concatenation, tùy compiler/runtime.

### `BootstrapMethods` không có trong mọi class

Nếu class file không có `CONSTANT_InvokeDynamic_info`/`CONSTANT_Dynamic_info`, attribute có thể vắng. Đừng gắn lỗi “javap thiếu BootstrapMethods” cho một class không dùng constructs cần bootstrap linkage.

## 24. `SourceFile`, `InnerClasses`, `NestMembers` và attributes khác

Ngoài `Code` còn nhiều attributes cấp class/method/field. `javap -v` chỉ in những gì có trong artifact.

| Attribute | Ý nghĩa |
| --- | --- |
| `SourceFile` | Tên file nguồn được compiler lưu |
| `Signature` | Generic type signature nếu cần |
| `ConstantValue` | Constant field value cho những fields hợp lệ |
| `Deprecated` | Marker deprecated theo class-file semantics |
| `RuntimeVisibleAnnotations` | Metadata annotations có runtime visibility |
| `RuntimeInvisibleAnnotations` | Annotations không cần runtime visibility |
| `MethodParameters` | Names/flags của formal parameters khi phát hành theo options như `-parameters` |
| `LineNumberTable` | Source lines mapping trong Code |
| `LocalVariableTable` | Local name/slot/scope debug mapping |
| `LocalVariableTypeTable` | Generic local type signatures debug mapping |
| `StackMapTable` | Verification frames |
| `BootstrapMethods` | Bootstrap specs cho invokedynamic/dynamic constants |
| `InnerClasses` | Nested/inner class descriptors khi cần |
| `NestHost` / `NestMembers` | Java 11+ nest-based access representation khi phù hợp |
| `Record` | Record component metadata trong những class files phù hợp |
| `PermittedSubclasses` | Sealed-class permitted subclasses metadata |
| `Module` | Module-info class-file description |

### `-parameters` không giống `-g`

`javac -parameters` thêm **MethodParameters** information dùng được cho reflection parameter names trên những methods phù hợp. `javac -g` thêm **debug info** như LocalVariableTable/LineNumberTable. Một tool dựa trên MethodParameters (reflection) và một tool dựa trên LocalVariableTable (debugger) không nhất thiết nhận cùng names nếu build flags khác.

**Ví dụ:** muốn cả debug locals lẫn reflection parameter names:

```bash
javac -g -parameters -encoding UTF-8 JavapFieldGuide.java
```

Vì source có `main(String[] args)` và methods static/instance, bạn có thể so output `MethodParameters` khi bật option, nhưng class-file `Code` semantics không thay.

## 25. `Signature` vs `descriptor`: generics và type erasure

Một ví dụ độc lập (không chạy cùng file chính nếu chưa tạo class) có thể là:

```java
import java.util.List;

final class GenericBox<T extends Number> {
    T first(List<T> items) {
        return items.get(0);
    }
}
```

Nếu tạo file riêng `GenericBox.java` rồi biên dịch:

```bash
javac -g -encoding UTF-8 GenericBox.java
javap -v -p GenericBox
```

Ở tầng descriptor JVM, type parameter `T extends Number` thường **erase** thành `java.lang.Number`, và `List<T>` erase thành `java.util.List`. Generic types có thể hiện ở `Signature`, chẳng hạn định dạng gần `<T:Ljava/lang/Number;>Ljava/lang/Object;` với context class.

**Không mong** class file chứa một method overload riêng cho mỗi `List<Integer>` và `List<Double>`; erasure semantics khác reified generics.

### Vì sao ví dụ phụ có ích?

Nó cho thấy `javap` không chỉ có `Code`, mà còn phản ánh **thông tin biên dịch hỗ trợ compiler/reflection**. Khi học bytecode linking, ưu tiên **method descriptor**; khi học generics, kiểm tra thêm **Signature**.

## 26. `-g:none`: khi mất debug information thì còn gì?

Dùng riêng một thư mục output khác để tránh ghi đè class có `-g`:

```bash
javac -g -encoding UTF-8 -d debug JavapFieldGuide.java
javac -g:none -encoding UTF-8 -d nodebug JavapFieldGuide.java

javap -classpath debug -c -l -p JavapFieldGuide
javap -classpath nodebug -c -l -p JavapFieldGuide
```

**Lưu ý:** những thư mục `debug` và `nodebug` phải tồn tại hoặc `javac -d` được dùng với output paths hợp lệ; để thao tác chắc chắn, hãy tạo chúng trước:

```bash
mkdir debug
mkdir nodebug
```

Trên Windows PowerShell hoặc Linux/macOS shell, `mkdir` đều có thể tạo thư mục đơn giản như trên. Chạy tạo thư mục **trước** hai dòng `javac -d`. Nếu thư mục đã có thì không cần tạo lại; không xóa hoặc ghi đè dữ liệu quan trọng.

### Điều dự kiến thay đổi

- `LocalVariableTable` có thể có trong build `-g` và vắng ở build `-g:none`.
- `LineNumberTable` và `SourceFile` cũng có thể vắng khi bỏ debug.
- **Code/constant pool indices/order** có thể khác do có/bỏ debug attributes và names.
- Core bytecode execution semantics phải giữ tương đương cho cùng Java source nếu compile hợp lệ.
- Với debug info vắng, `javap -c` vẫn có thể in bytecode.
- Stack trace exception có thể thiếu tên source line/file tùy attributes.

**Không so cp indices bằng cách giả định chỉ số giống nhau** giữa hai class files. So semantic roles, descriptors, opcode behavior và functional stdout thay vì diff từng dòng khi debug metadata thay đổi.

## 27. `-sysinfo` và nguồn gốc artifact

Dùng:

```bash
javap -sysinfo JavapFieldGuide
```

Lệnh có thể cung cấp path, file size, modified time và hash theo phiên bản công cụ. Nó hữu ích để xác minh người đọc đang phân tích **đúng class bytes**.

### Khi nào quan trọng?

- IDE và terminal đang dùng hai output directories.
- CI compile class cũ vì incremental build stale.
- Classpath có hai versions của cùng binary class name.
- Build output trong JAR khác production.
- Developer sửa source nhưng quên biên dịch lại.

**Một `javap` output đẹp không giúp gì nếu nó đọc sai artifact**. Trước khi kết luận field/method vắng, xác nhận classpath/multi-release JAR/module selection và digest khi cần.

## 28. Phân tích class bên trong JAR

Nếu đã đóng gói class files đúng cách, có thể yêu cầu `javap` đọc qua classpath:

```bash
jar --create --file app.jar JavapFieldGuide.class
javap -classpath app.jar -c -p JavapFieldGuide
```

Vì `JavapFieldGuide` không cần nested class files của chính nó hay thư viện ngoài, bài lab này có thể tạo JAR chứa duy nhất class file đó cho mục đích **disassembly**. JAR trên **không có Main-Class manifest**, vì vậy `java -jar app.jar` không được bảo đảm chạy; dùng:

```bash
java -cp app.jar JavapFieldGuide
```

Hoặc tạo JAR executable hợp lệ với Main-Class manifest theo `jar --create --file ... --main-class` trên JDK hỗ trợ.

### Tránh đóng gói class khác build

Nếu bạn đã chạy `javac -g:none` và ghi đè `JavapFieldGuide.class` trong current directory, JAR có thể chứa **bản không có debug names**. Muốn so output, hãy dùng `debug/JavapFieldGuide.class` và `nodebug/JavapFieldGuide.class` theo đường dẫn riêng.

Không lấy số dòng từ một JAR rồi so với source đã sửa nhiều lần mà chưa rebuild.

## 29. `javap` không phải decompiler tái tạo source 1:1

`javap` disassembles bytecode và in metadata. Nó **không** có nghĩa tái tạo toàn bộ:

- Source comments, formatting, blank lines.
- Tên tất cả local variables nếu thiếu debug metadata.
- Tất cả generic source-level distinctions khi đã erase.
- Cấu trúc `try-with-resources` y hệt như source.
- Mẫu `for` vs `while` nếu bytecode control flow tương đương.
- Lambda source body text và tên source variables nếu compiler tạo synthetic method.
- Một source expression có thể được compiler gộp/chuyển thành invokedynamic hoặc helper methods.

**Có thể infer chương trình làm gì từ bytecode**, nhưng không thể luôn khôi phục source nguyên văn từ `.class`. Ngược lại, `javap` có thể cho bằng chứng cụ thể hơn source khi muốn xem **compiler đã phát hành instructions nào**.

## 30. `javap` không phải profiler và không cho số liệu runtime

Một nhầm lẫn phổ biến:

> “Tôi thấy ba `invokestatic` trong `javap`, nên mỗi request chắc chắn thực hiện ba native calls.”

**Sai.** HotSpot JIT có thể inline methods, optimize away dead operations, compile alternative shapes hoặc dùng deoptimization guards. Bytecode mô tả **semantic instructions** trong class file; runtime machine code có thể khác miễn giữ Java semantics.

Dùng đúng công cụ theo câu hỏi:

| Câu hỏi | Công cụ |
| --- | --- |
| Class file chứa opcodes gì? | `javap -c` |
| Đúng constant pool/member descriptor nào? | `javap -v/-s` |
| Vì sao source line X được map tới offset Y? | `javap -l` với debug attributes |
| Method có try/catch handler nào? | `javap -v` → Exception table |
| JIT có inline một call không? | HotSpot compiler diagnostics/JFR, flags tùy build |
| Heap used/GC pauses runtime? | `jcmd`, GC logs, MXBeans, JFR |
| Method CPU hotspots? | JFR/profiler |
| Compiler warm-up thay đổi theo thời gian? | JFR/compiler logs và benchmark design |
| Giao diện VM/JDK đang chạy? | `java -version`, `jcmd PID VM.version` |

**Không biến số bytecode instructions thành số CPU instructions** để dự báo trực tiếp performance. Các hardware architectures, JIT tiers, receiver type profiles và workload làm runtime behavior khác nhau.

## 31. Những lỗi thực hành thường gặp

### 31.1. `Error: class not found`

Kiểm tra:

- Đã chạy `javac` thành công chưa?
- Đang ở đúng working directory?
- Binary class name/package đúng?
- `-classpath` có thư mục/JAR chứa class không?
- Có module-path thay vì classpath cần chỉ định?
- Trên Windows/Linux, path separators khác nhau (`;` vs `:`).

Trong ví dụ file không có `package`, nên `javap JavapFieldGuide` từ thư mục chứa class thường dùng được. Nếu bạn thêm `package com.example;`, cần directory layout/class name phù hợp như `com.example.JavapFieldGuide`.

### 31.2. `javap` thấy constructor nhưng không thấy `main`

Có thể bạn đang đọc class khác do classpath. Hoặc build output cũ chưa compile lại. Kiểm tra `-p`, `-sysinfo`, class file path và source chính xác.

### 31.3. Không thấy local variable names

Hãy compile với `-g`, không chỉ `-c`. `-p` cho phép xem private members, **không làm xuất hiện LocalVariableTable nếu class không chứa nó**.

### 31.4. Không thấy `BootstrapMethods`

Compiler có thể dùng cách khác để concat Strings hoặc class không có dynamic call sites. Kiểm tra `javac` version/source; không mặc định thiếu BootstrapMethods là lỗi.

### 31.5. Thấy `StackMapTable`, tưởng đó là stack trace lỗi

StackMapTable là type-verification metadata; stack trace runtime là danh sách method/line thực thi theo `Throwable` hoặc tool. Hai thứ không phải một.

### 31.6. Constant pool index khác sách

Chỉ số CP phụ thuộc layout class file/compiler; tham chiếu comment symbolic target và descriptor thay vì học thuộc `#17`/`#22`.

### 31.7. Đọc `Code.stack` như độ sâu call stack

`max_stack` giới hạn operand stack trong một method frame. Số nested calls/stack overflow phụ thuộc call depth/runtime stack budget, không được suy từ một `stack=3`.

### 31.8. Thấy field `SCALE` nhưng không thấy `getstatic`

`SCALE` là compile-time constant, có thể dùng `iconst_3` trực tiếp. Không phải lỗi compiler thiếu field.

### 31.9. Compile và run bằng hai Java versions khác

Có thể xuất hiện `UnsupportedClassVersionError`. Dùng `java -version`, `javac -version`, `major version` và `--release` để cô lập.

### 31.10. So output từ build khác nhau

`-g`, `-parameters`, compiler version và annotation processors có thể thêm attributes/members. Với thực nghiệm, giữ **cùng source, flags, digest và paths**.

## 32. Quy trình phân tích một vùng `javap` bất kỳ

Khi gặp một output verbose dài, không nên đọc từ đầu đến cuối mà không có câu hỏi. Thực hiện:

1. **Nhận diện artifact**: path/hash/class name/version.
2. **Xác định compatibility**: major/minor, compiler/runtime target.
3. **Xác định class structure**: flags, superclass/interfaces, field/method counts.
4. **Đọc Constant pool**: class names, Methodref/Fieldref/NameAndType/descriptors.
5. **Đọc fields**: static/final, descriptors, ConstantValue nếu có.
6. **Chọn method quan tâm**: access flags và method descriptor.
7. **Đọc Code**: max_stack, max_locals, arguments convention.
8. **Theo opcode**: stack effects, local slots, method invocation và return.
9. **Kiểm tra branches/handlers**: target offsets, Exception table.
10. **Liên hệ debug metadata**: LineNumberTable, LocalVariableTable, StackMapTable.
11. **Đọc class-level attributes cuối file**: SourceFile, BootstrapMethods, InnerClasses...
12. **Kiểm tra functional output**: chạy Java và đối chiếu behavior.
13. **Chứng minh giới hạn**: output phụ thuộc compiler nào, đâu là classfile vs runtime.
14. **Ghi chú bằng chứng**: command exact, flags, JDK version, đường dẫn artifact.

### Mẫu phiếu ghi nhanh

```text
Classfile name: JavapFieldGuide.class
Compiler: <javac version kiểm tra thực tế>
Compiler flags: -g -encoding UTF-8
Major/minor: <javap -v output>
Class flags: <javap -v output>
Method: sum:(II)I
Locals: L0=left, L1=right, L2=total
max_stack / max_locals: <javap output>
Relevant opcodes: iload, iadd, istore, ireturn
Exception handlers: none for sum
Debug attrs: LineNumberTable/LocalVariableTable nếu có
Functional oracle: compute(6)=30, compute(2)=7
Uncertainties: CP indices/offsets/compiler-specific details
```

Các giá trị trong dấu ngoặc nhọn dạng mô tả ở đây là **nội dung cần điền từ phép đo**, không phải kết quả đã kiểm tra trên một JVM thật.

## 33. Bài tập có đáp án

**Bài 1.** `javac` và `javap` làm gì khác nhau?  
**Đáp án:** `javac` biên dịch Java source thành class files; `javap` phân tích/disassemble class file, không tạo runtime profile.

**Bài 2.** Cần xem private field `bias` trong disassembly?  
**Đáp án:** Dùng `javap -p JavapFieldGuide` hoặc kết hợp `-p -v`.

**Bài 3.** Muốn xem opcodes của `sum`?  
**Đáp án:** `javap -c -p JavapFieldGuide`.

**Bài 4.** Muốn xem class-file major version và constant pool?  
**Đáp án:** `javap -v -p JavapFieldGuide`.

**Bài 5.** Java 17 target có major version nào?  
**Đáp án:** **61**, với class-file target phù hợp.

**Bài 6.** Java 21 target có major version nào?  
**Đáp án:** **65**.

**Bài 7.** Constant pool `#12` là gì?  
**Đáp án:** Index 12 trong constant pool, không phải source line hay local slot.

**Bài 8.** `(II)I` có nghĩa gì?  
**Đáp án:** Hai arguments `int`, trả về `int`.

**Bài 9.** `([Ljava/lang/String;)V` nghĩa là gì?  
**Đáp án:** Method nhận `String[]` và trả `void`, như `main`.

**Bài 10.** Constructor `JavapFieldGuide(int)` có descriptor gì?  
**Đáp án:** `(I)V`.

**Bài 11.** Tại sao `sum` có `args_size=2` trong output điển hình?  
**Đáp án:** Nó là static method có hai parameters `int`, mỗi loại chiếm một argument slot.

**Bài 12.** `sum` tính `6+4`, sau `iadd` operand stack có gì?  
**Đáp án:** `[10]`; `istore` tiếp theo mới đưa kết quả vào local.

**Bài 13.** `iload_0` có xóa local slot 0 không?  
**Đáp án:** Không, nó push bản sao `int value` lên operand stack.

**Bài 14.** `max_stack` đo gì?  
**Đáp án:** Maximum operand stack depth theo units của một frame method, không phải recursion depth.

**Bài 15.** `max_locals` đo gì?  
**Đáp án:** Số local slots của method invocation, gồm receiver/arguments/temps theo compiler layout.

**Bài 16.** Field `SCALE=3` vì sao có thể không cần `getstatic`?  
**Đáp án:** Là compile-time constant, compiler có thể inline giá trị int 3 trong Code.

**Bài 17.** `compute(6)` trả gì?  
**Đáp án:** `30` vì `sum(6,4)=10` và `10×3=30`.

**Bài 18.** `compute(2)` trả gì?  
**Đáp án:** `7` vì `sum(2,4)=6` và `6+1=7`.

**Bài 19.** `safeDivide(9,0)` in kết quả gì?  
**Đáp án:** `-1` nhờ catch `ArithmeticException`.

**Bài 20.** `Exception table` mô tả gì?  
**Đáp án:** Bytecode protected ranges và exception handler offsets/types.

**Bài 21.** `Exceptions attribute` có giống Exception table?  
**Đáp án:** Không; Exceptions attribute mô tả declared exceptions/`throws`, còn Code Exception table phục vụ catch handlers.

**Bài 22.** `LineNumberTable` có phải toàn bộ Java source?  
**Đáp án:** Không; chỉ ánh xạ bytecode offsets sang source line numbers khi có debug metadata.

**Bài 23.** `LocalVariableTable` không có nghĩa JVM không có local slots?  
**Đáp án:** Đúng; `Code.max_locals` và bytecode vẫn cần local slots, debug name table là optional.

**Bài 24.** `Start`/`Length` trong LocalVariableTable đo milliseconds?  
**Đáp án:** Không; là phạm vi bytecode offsets/length.

**Bài 25.** `StackMapTable` có phải snapshot operands của runtime?  
**Đáp án:** Không; metadata type verification ở control-flow points.

**Bài 26.** Java 17 String concatenation thường dùng opcode gì?  
**Đáp án:** `invokedynamic` với `StringConcatFactory` trên `javac` phổ biến, nhưng kiểm chứng class file thật.

**Bài 27.** `BootstrapMethods` dùng cho gì?  
**Đáp án:** Lưu bootstrap method specifiers/arguments liên quan dynamic call sites hoặc constants.

**Bài 28.** `javap -c` có cho biết compiler C2 đã inline method?  
**Đáp án:** Không; `javap` đọc bytecode class file, không JIT machine code.

**Bài 29.** `javac -g:none` thường làm mất gì?  
**Đáp án:** Debug metadata như source/line/local-variable tables, không có nghĩa mất Java logic.

**Bài 30.** Muốn reflection nhìn parameter names thay vì `arg0`, compiler option thường là gì?  
**Đáp án:** `-parameters`, khác với `-g`.

**Bài 31.** `-sysinfo` hữu ích để làm gì?  
**Đáp án:** Xác định path/size/timestamp/hash của class artifact đang phân tích.

**Bài 32.** Tại sao đọc nhầm class file dễ gây kết luận sai?  
**Đáp án:** Có thể đang phân tích build khác source, thiếu member/có opcode khác; cần verify classpath/artifact.

**Bài 33.** Class file chứa `Signature` có nghĩa generic types reified trong mọi runtime object?  
**Đáp án:** Không; thông tin generic metadata khác descriptor erasure và runtime type representation.

**Bài 34.** Biên dịch JDK 21 với `--release 17` có thể sinh major 61 không?  
**Đáp án:** Có; target class-file/API/language behavior phải theo Java 17 trong giới hạn tool hỗ trợ.

**Bài 35.** Có thể thay `major version: 65` bằng 61 thủ công để Java 17 chạy không?  
**Đáp án:** Không an toàn; bytecodes/APIs/attributes có thể không tương thích, phải compile đúng target.

## 34. Những hiểu nhầm thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`javap` chạy Java code.” | Nó đọc/disassemble class file |
| “`javap -v` là JIT machine-code dump.” | Là class-file metadata/bytecode |
| “Mọi source line sinh một opcode.” | Một dòng có thể sinh nhiều instructions hoặc được compiler thay đổi |
| “Offset trước opcode là line number.” | Là bytecode offset |
| “`#n` là slot local.” | Constant-pool index |
| “`flags ACC_FINAL` luôn là field.” | Context class/field/method khác nhau |
| “`major version` là số version của JVM đang chạy.” | Là class-file format target |
| “`-g:none` xóa local variables.” | Xóa debug names/line metadata, không xóa semantics |
| “`-parameters` và `-g` giống nhau.” | MethodParameters vs debug tables |
| “`Code.stack=2` có nghĩa hai nested calls.” | Max operand stack units của frame |
| “`LocalVariableTable` có mọi tên biến trên mọi build.” | Optional debug attribute |
| “`StackMapTable` là runtime stack trace.” | Verifier type frames |
| “`Exception table` và `Exceptions` giống nhau.” | Handler vs declared exceptions |
| “`ConstantValue` là instruction đã chạy trong method.” | Field attribute |
| “`SCALE` có field thì `getstatic` bắt buộc.” | Compile-time constant có thể inline |
| “`invokedynamic` là dấu hiệu chạy đa luồng.” | Call-site linkage mechanism |
| “`BootstrapMethods` có mặt ở mọi class.” | Chỉ khi có dynamic bootstrap cases phù hợp |
| “CP indices giống nhau sau đổi compiler.” | Chỉ số phụ thuộc artifact |
| “Generic `Signature` là erased method descriptor.” | Thông tin generic và descriptor tách nhau |
| “Một class file giống tên sẽ giống source.” | Artifact bytes/build/version có thể khác |
| “`javap` xác định được CPU p99 hoặc heap leak.” | Dùng runtime diagnostics/profilers |
| “Bytecode có `invoke` nghĩa native call chắc chắn còn sau JIT.” | JIT có thể inline |
| “`javap -c` cần chương trình chạy trước.” | Không; chỉ cần class file |
| “JAR không có Main-Class thì không thể `javap`.” | Có thể disassemble qua classpath; `java -jar` là chuyện khác |
| “Muốn xem code chính xác chỉ cần web example.” | Phải chạy `javap` trên class artifact thực |

## 35. Checklist hoàn thành bài thực nghiệm

- [ ] Dùng đúng JDK 17+ với `java`, `javac` và `javap`?
- [ ] Đã lưu toàn bộ file `JavapFieldGuide.java` giống ví dụ?
- [ ] Đã biên dịch thành công bằng `javac -g -encoding UTF-8`?
- [ ] Đã xác nhận stdout: 30, 7, -1, value=30, done?
- [ ] Đã đọc `javap -v -p` và hiểu major/minor?
- [ ] Đã chỉ được class flags và superclass/interfaces?
- [ ] Đã phân biệt CP indices với bytecode offsets/local slots/source lines?
- [ ] Đã dịch `(II)I`, `(I)V`, `([Ljava/lang/String;)V`?
- [ ] Đã xác định hai fields và ConstantValue của `SCALE`?
- [ ] Đã tìm constructor `<init>` và lệnh `putfield bias`?
- [ ] Đã theo từng opcode của `sum` với operand-stack table?
- [ ] Đã thấy call `invokestatic sum` trong `compute`?
- [ ] Đã hiểu nhánh true/false của `if` dù compiler đảo điều kiện?
- [ ] Đã tìm Exception table cho `safeDivide`?
- [ ] Đã phân biệt Exception table với Exceptions attribute?
- [ ] Đã đọc `LineNumberTable` và `LocalVariableTable` sau `-g`?
- [ ] Đã hiểu StackMapTable là verifier metadata, không runtime trace?
- [ ] Nếu có `invokedynamic`, đã kiểm tra `BootstrapMethods`?
- [ ] Đã thử `-g:none` trong thư mục riêng để không làm mất build `-g`?
- [ ] Đã tránh hard-code constant-pool #/offsets từ một JDK khác?
- [ ] Đã ghi path/hash của artifact nếu có nhiều builds?
- [ ] Đã phân biệt bytecode với HotSpot compiled machine code?
- [ ] Đã không suy runtime GC/JIT latency chỉ từ bytecode count?
- [ ] Đã lưu command, JDK version và giới hạn quan sát trong báo cáo?

## 36. Kết luận và nguồn chuẩn

**`javap` giúp biến một class file vốn khó đọc thành bằng chứng cụ thể về cách chương trình Java được compiler mã hóa.** Đọc đúng từng vùng cho phép trả lời:

```text
Classfile HEADER
  → Đây là artifact nào? class-file version nào?

FLAGS / THIS_CLASS / SUPER_CLASS
  → Kiểu và quan hệ class/interface ra sao?

CONSTANT POOL
  → Những symbolic names, methods, fields, descriptors nào?

FIELDS
  → Static/instance, final, descriptor, ConstantValue?

METHODS / CODE
  → Method descriptor, stack/locals bounds, bytecode opcodes?

BRANCH / EXCEPTION TABLE
  → Luồng điều khiển, catch handlers có cấu trúc thế nào?

LINE / LOCAL TABLES
  → Source lines, local names/slots/ranges khi có debug info?

STACKMAPTABLE / BOOTSTRAPMETHODS
  → Verification states và dynamic linkage artifacts?

RUNTIME JAVA
  → Functional output có đúng với semantics đã phân tích không?
```

Sau bài này, cách học hiệu quả là **thay một yếu tố nhỏ của source**, biên dịch lại vào thư mục riêng, đối chiếu `javap` và stdout — ví dụ đổi `SCALE`, thay `>=` thành `>`, thêm `throws`, thêm một generic method hoặc bỏ `-g`. Mỗi thay đổi có thể làm một hoặc vài vùng class file đổi; những phần không thay cho thấy compiler/JVMS tách concerns như thế nào.

### Tài liệu chính thống để đối chiếu

1. **Oracle JDK 17 — `javap` command/options:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html
2. **Oracle JDK 17 — `javac` command, `-g`, `--release`, `-parameters`:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html
3. **Java Virtual Machine Specification SE 17 — ClassFile structure (`4.1`):** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.1
4. **JVMS `4.3` — Field/Method descriptors:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3
5. **JVMS `4.4` — Constant pool:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4
6. **JVMS `4.5/4.6` — Fields/Methods:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.5
7. **JVMS `4.7.3` — Code attribute, max_stack/max_locals:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.3
8. **JVMS `4.7.4` — StackMapTable:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.4
9. **JVMS `4.7.12/4.7.13` — LineNumberTable/LocalVariableTable:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.12
10. **JVMS `4.7.23` — BootstrapMethods:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.23
11. **JVMS Chapter 6 — Java bytecode instructions:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html
12. **Java SE 17 — StringConcatFactory:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/StringConcatFactory.html

**Điểm nhớ lâu nhất:** `javap` là **bản đồ class-file**. Biết cách đọc header → constant pool → fields/methods → Code → debug/verification/dynamic-linkage attributes sẽ giúp bạn giải thích compiler output có căn cứ và phân biệt chính xác **Java source, JVM bytecode, runtime Java semantics và HotSpot JIT machine code**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-experiments-002">Theo dấu local variables và operand stack qua lời gọi lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Theo dấu local variables và operand stack qua lời gọi lồng nhau

Khi đọc Java source, ta nhìn thấy biến cục bộ, phép tính và chuỗi lời gọi như `main() → compute() → twicePlusOne() → combine()`. Nhưng Java Virtual Machine (JVM) thực thi **bytecode**, trong đó mỗi lần gọi method tạo ra một **frame logic** riêng gồm **local variables array**, **operand stack**, thông tin liên kết tới runtime constant pool cùng dữ liệu phục vụ return/exception. Một method đang chạy có thể tạm dừng trong khi method con được thực thi; kết quả trả về được đặt lại vào **operand stack của caller**, không phải “ghi thẳng” vào tên biến Java bằng phép thuật.

Bài học này hướng dẫn **lần vết từng lệnh** với `javap -c -p -l -v`, theo dõi local slots và các phép `iload`/`istore`/`iadd`/`invokestatic`/`invokevirtual`/`ireturn`, rồi mở rộng sang instance methods, category-2 values (`long`/`double`), exception handling, recursion, stack overflow và ranh giới với **HotSpot JIT inlining**.

**Phạm vi:** Java 17+; mô hình frame/bytecode dựa trên **Java Virtual Machine Specification (JVMS), ``2.5–2.6, 4.7.3, 6**. Output số học trong ví dụ Java là xác định. Offset constant-pool, slot tối ưu và cách sắp xếp bytecode do compiler phát sinh có thể thay đổi theo `javac` version/flags: **luôn lấy `javap` từ class file bạn vừa biên dịch làm bằng chứng cuối cùng**.

## 1. Mô hình cơ bản: JVM stack chứa các frames theo từng thread

```text
Thread T: Java Virtual Machine stack
┌──────────────────────────────────────────────────────────┐
│ Current frame: combine(left, right)                      │
│   local variables: [left=3, right=3, sum=?]               │
│   operand stack:   [... values đang thao tác ...]         │
├──────────────────────────────────────────────────────────┤
│ Suspended frame: twicePlusOne(value=3)                   │
│   local variables: [value=3, doubled=?]                   │
│   operand stack:   [... phần còn lại trước invoke ...]    │
├──────────────────────────────────────────────────────────┤
│ Suspended frame: compute(base=3)                         │
│   local variables: [base=3, first=?, second=?]            │
├──────────────────────────────────────────────────────────┤
│ Suspended frame: main(args)                              │
│   local variables: [args reference, ...]                 │
└──────────────────────────────────────────────────────────┘
    Call → tạo frame mới / chuyển current frame
    Return → trả kết quả cho caller / loại frame callee
```

Sơ đồ chỉ minh họa **mô hình thực thi ở cấp JVMS**. Thực tế HotSpot có thể inline nhiều methods thành machine code duy nhất hoặc dùng registers, safepoint metadata, compiled/native frames; đừng coi mỗi khung trong sơ đồ là một khối RAM riêng y hệt mà debugger sẽ luôn thấy.

### Mỗi invocation, một bộ locals và operand stack độc lập

Gọi `combine(3, 3)` hai lần không có nghĩa dùng chung một “ô biến sum” cho cả hai lần. **Mỗi lần gọi** tạo một execution context logic riêng. Đệ quy `factorial(5)` tạo các invocations `factorial(5)`, `factorial(4)`, ... với locals và operand stack tương ứng cho từng mức.

### Current frame và frame bị tạm dừng

Theo JVMS `2.6`, tại một thời điểm trong một thread chỉ có **một current frame**. Khi method gọi method khác:

1. Caller chuẩn bị các argument values trên operand stack.
2. Lệnh `invoke*` lấy chúng theo descriptor, tạo frame của callee và khởi tạo argument locals.
3. Callee trở thành current frame; caller tạm dừng.
4. Nếu callee trả bình thường, nó đưa giá trị trả về (nếu có) tới operand stack của caller và bị loại.
5. Nếu callee thoát đột ngột do exception chưa bắt, việc tìm handler có thể loại nhiều frames.

**Không thread nào được trực tiếp đọc mảng local variables của frame đang thuộc thread khác**. Các references trong locals vẫn có thể trỏ tới **objects dùng chung trên heap**, vì vậy frame isolation không đồng nghĩa mọi object data đều thread-safe.

## 2. Những thành phần của một frame

| Thành phần | Công dụng | Không nên nhầm |
| --- | --- | --- |
| **Local variable array** | Giữ parameters và temporary local values của invocation | Không phải heap object properties; không phải tên biến Java còn nguyên |
| **Operand stack** | LIFO cho constants, loads, arithmetic, arguments, return value, exception | Không phải Java call stack; nó nằm **bên trong frame** |
| **Runtime constant pool reference** | Phục vụ symbolic references/dynamic linking khi cần | Không phải một bản sao pool riêng mỗi frame |
| **Return/continuation state** | Khôi phục caller/PC khi invocation kết thúc | Không phải một biến Java có tên `returnAddress` trong mọi method |
| **Exception dispatch state** | Tìm handler, unwind các frames khi cần | Không có nghĩa exception value luôn nằm trong một local slot cố định |
| **Implementation metadata** | Debugging, deoptimization, compiled code state, stack maps | Không bắt buộc có layout byte-for-byte trong JVMS |

**Mỗi frame** có một local variable array và operand stack **với kích thước được xác định trong bytecode metadata**, thông thường `Code.max_locals` và `Code.max_stack`. Điều đó không có nghĩa mọi phần tử local đều đã được khởi tạo bằng giá trị Java hợp lệ khi frame vừa được tạo.

## 3. Local variable slots: chỉ số, loại dữ liệu và phạm vi

JVMS `2.6.1` mô tả local variables thành một **mảng slot được đánh số từ 0**. Nhiều bytecode instructions đọc/ghi slot theo index:

| Bytecode | Hiệu ứng khái niệm |
| --- | --- |
| `iload_0` | Đọc giá trị `int` ở slot 0, push lên operand stack |
| `iload 4` | Đọc `int` từ local slot 4 |
| `istore_1` | Pop một `int` từ operand stack vào slot 1 |
| `aload_0` | Đọc object/array reference ở slot 0 |
| `astore_2` | Pop reference vào slot 2 |
| `lload_0` / `lstore_0` | Load/store `long` dùng hai slots liên tiếp trong local array |
| `dload_2` / `dstore_2` | Load/store `double` dùng hai slots liên tiếp |
| `iinc 1, 1` | Tăng giá trị `int` trong local slot 1 **trực tiếp**, không push rồi pop `iadd` |
| `ret` | Lệnh lịch sử liên quan subroutines cũ; không đại diện return Java thông thường |

### Static vs instance methods

Ví dụ:

```java
static int plus(int a, int b) {
    return a + b;
}

int plusWithBias(int a) {
    return a + bias;
}
```

Đây là **snippets**, `bias` là instance field chưa khai báo trong ví dụ này.

- **Static method:** các arguments bắt đầu từ slot **0**: `a→0`, `b→1`.
- **Instance method:** slot **0** dành cho **`this`**; các arguments bắt đầu từ slot **1**.
- Parameter category-2 (`long`/`double`) chiếm **hai slots** trong local variables array: tham số tiếp theo dịch hai indices.

### Slots không đồng nghĩa Java source variable names

Class file không bắt buộc lưu đầy đủ tên biến nguồn. Nếu compile không có `-g` hoặc không có `LocalVariableTable`, debugger/`javap` vẫn có thể xác định slots và types từ bytecode, nhưng không chắc đọc được tên `a`, `b` hoặc `sum`. Tên biến mang tính debug information; **slot indices mới là dữ kiện runtime bytecode quan trọng**.

## 4. Operand stack: nơi toán hạng được đẩy vào và lấy ra

Operand stack là cấu trúc **LIFO** (last-in, first-out). Khi biểu diễn:

```text
Bottom ─────────────► Top

[]                  stack rỗng
[3]                 một int value
[3, 4]              top là 4
[7]                 sau iadd
```

Các bytecodes thường dùng:

| Instruction | Trước → Sau | Ghi chú |
| --- | --- | --- |
| `iconst_3` | `[] → [3]` | Push hằng `int 3` |
| `bipush 10` | `[] → [10]` | Push một `int` sign-extended từ immediate byte |
| `iload_0` | `[] → [locals[0]]` | Không xóa local |
| `iadd` | `[..., a, b] → [..., a+b]` | Pop 2 ints rồi push kết quả |
| `isub` | `[..., a, b] → [..., a-b]` | **Thứ tự** rất quan trọng |
| `imul` | `[..., a, b] → [..., a*b]` | Pop operands |
| `istore_2` | `[..., x] → [...]` | Pop x vào slot 2 |
| `dup` | `[..., x] → [..., x, x]` | Với một category-1 value |
| `pop` | `[..., x] → [...]` | Bỏ top category-1 value |
| `ireturn` | `[..., x] → caller nhận x` | Hoàn thành method có kiểu int-compatible |
| `return` | `[] → caller` | Method `void`; không có return value |

**Không thể lấy một value ở giữa operand stack mà không tuân theo bytecode semantics**. JVM verifier bảo đảm type/height của stack hợp lệ trên các control-flow paths đối với class file được chấp nhận.

### Operand stack không lưu local names

Khi `iload_0` push số 3, operand stack mang **giá trị kiểu int**, không mang nhãn “biến a”. Hai local slots có thể chứa cùng một int 3, hoặc operand stack có thể giữ hai bản sao int 3. Đây là **hai giá trị trong execution state**, không có quan hệ aliasing phức tạp như object references.

### Reference values lại khác

Khi `aload_0` push một object reference, cả local slot và operand stack có thể trỏ tới **cùng object trên heap**. Sao chép reference **không copy object**; mutation qua một alias có thể quan sát qua alias khác nếu đồng bộ đúng Java Memory Model.

## 5. Chương trình Java 17+ hoàn chỉnh để theo dấu

Tạo file **`NestedFramesLab.java`**:

```java
public final class NestedFramesLab {
    static int combine(int left, int right) {
        int sum = left + right;
        return sum;
    }

    static int twicePlusOne(int value) {
        int doubled = combine(value, value);
        return combine(doubled, 1);
    }

    static int compute(int base) {
        int first = twicePlusOne(base);
        int second = combine(first, 4);
        return second;
    }

    static final class Node {
        final int bias;

        Node(int bias) {
            this.bias = bias;
        }

        int transform(int input) {
            int adjusted = combine(input, bias);
            return twicePlusOne(adjusted);
        }
    }

    static long widen(long base, double adjustment) {
        long extra = (long) adjustment;
        return base + extra;
    }

    static int divide(int numerator, int denominator) {
        return numerator / denominator;
    }

    static int recover(int input) {
        try {
            return divide(input, 0);
        } catch (ArithmeticException ex) {
            return combine(input, 100);
        }
    }

    static int factorial(int n) {
        if (n <= 1) {
            return 1;
        }
        return n * factorial(n - 1);
    }

    public static void main(String[] args) {
        int computed = compute(3);
        Node node = new Node(2);
        int transformed = node.transform(3);
        long wide = widen(10_000_000_000L, 2.75);
        int recovered = recover(5);
        int fact = factorial(5);

        System.out.println("compute: " + computed);
        System.out.println("transform: " + transformed);
        System.out.println("widen: " + wide);
        System.out.println("recover: " + recovered);
        System.out.println("factorial: " + fact);
        System.out.println("done");
    }
}
```

### Lệnh biên dịch và chạy

```bash
javac -g -encoding UTF-8 NestedFramesLab.java
java NestedFramesLab
```

**Standard output chính xác** khi không bật thêm JVM diagnostic logging:

```text
compute: 11
transform: 11
widen: 10000000002
recover: 105
factorial: 120
done
```

### Tự kiểm toán phép tính

- `compute(3)` gọi `twicePlusOne(3)` → `combine(3,3)=6` → `combine(6,1)=7` → `combine(7,4)=11`.
- `new Node(2).transform(3)`: `combine(3,2)=5` rồi `twicePlusOne(5)=11`.
- `widen(10_000_000_000L,2.75)`: Java cast `(long)2.75` = 2 (cắt phần thập phân về 0) nên tổng = 10.000.000.002.
- `divide(5,0)` ném `ArithmeticException`; `recover(5)` bắt exception và `combine(5,100)=105`.
- `factorial(5)=5×4×3×2×1=120`.

**Mục tiêu của chương trình là correctness và bytecode tracing**, không phải benchmark performance. HotSpot JIT có thể inline khi chạy nóng; bytecode do `javap` in vẫn phản ánh class file, không phải trực tiếp native machine code đã JIT.

## 6. `javap`: khám phá Code, LocalVariableTable và operand instructions

Chạy trên **chính file class vừa compile**:

```bash
javap -c -p NestedFramesLab
javap -c -l -p NestedFramesLab
javap -v -p NestedFramesLab
```

Để xem nested class `Node`, dùng quoting tránh shell mở rộng ký tự `$`:

```bash
javap -c -l -p 'NestedFramesLab$Node'
```

`javap` in **bytecode**, có thể có:

- `Code:` các bytecode instructions và offsets.
- `stack=...` → `Code.max_stack`: **giới hạn depth operand stack** theo units mà class file cho method.
- `locals=...` → `Code.max_locals`: số local variable slots của method.
- `args_size=...` do công cụ diễn giải: đếm slot arguments, gồm implicit `this` nếu instance.
- `LineNumberTable` và `LocalVariableTable` nếu compiler emit debug attributes tương ứng.
- `StackMapTable` có thể xuất hiện để phục vụ **bytecode type verification** ở control-flow merge points.
- `Exception table` chỉ các khoảng bytecode được bảo vệ và handler targets.
- Constant-pool entries cho symbolic method references, field references và invokedynamic.

### Các lệnh `-g` vs `-g:none`

```bash
javac -g -encoding UTF-8 NestedFramesLab.java
javap -c -l -p NestedFramesLab
```

`-g` giúp giữ source/line/local-variable debug tables. Để tự kiểm tra điều gì xảy ra khi thiếu debug names, có thể build một bản khác trong **thư mục khác**, không ghi đè bài đang quan sát:

```bash
javac -g:none -encoding UTF-8 NestedFramesLab.java
javap -c -l -p NestedFramesLab
```

**Lưu ý:** lệnh `-g:none` sau cùng nếu chạy cùng thư mục sẽ ghi đè `NestedFramesLab.class` từ lệnh `-g`. Vì vậy nếu muốn so cả hai, hãy đặt compiled outputs vào hai thư mục riêng bằng `javac -d` và dùng `javap -classpath`, hoặc compile/đọc lần lượt với ý thức rõ file thay đổi.

**Tên local variable trong source không quyết định index duy nhất**: compiler có thể reuse slots khi hai variables không có live ranges giao nhau.

## 7. Theo dấu `combine(3,3)` — từ stack rỗng đến `ireturn`

Với `javac` JDK 17 và source trong bài, một chuỗi bytecode điển hình của `combine`:

```text
static int combine(int, int);
  Code:
     0: iload_0
     1: iload_1
     2: iadd
     3: istore_2
     4: iload_2
     5: ireturn
```

**Đây là mô tả offset điển hình; đối chiếu `javap` thực tế trên môi trường của bạn.** Mapping:

```text
local slots của combine(3,3)
  slot 0 = left  = 3
  slot 1 = right = 3
  slot 2 = sum   = chưa được gán

Stack notation: bottom → top
```

| Bước | Bytecode | Local variables sau bước | Operand stack sau bước |
| --- | --- | --- | --- |
| Vào method | Khởi tạo frame/arguments | `[3,3,?]` | `[]` |
| 0 | `iload_0` | `[3,3,?]` | `[3]` |
| 1 | `iload_1` | `[3,3,?]` | `[3,3]` |
| 2 | `iadd` | `[3,3,?]` | `[6]` |
| 3 | `istore_2` | `[3,3,6]` | `[]` |
| 4 | `iload_2` | `[3,3,6]` | `[6]` |
| 5 | `ireturn` | frame kết thúc | `6` được trả về caller |

### Điều gì **không** xảy ra?

- `iadd` không trực tiếp “gán vào local sum”; nó pop 2 operands, push kết quả lên **operand stack**. `istore_2` mới chuyển giá trị từ operand stack vào local slot.
- `iload` không xóa giá trị tại local slot.
- `ireturn` không “ghi trực tiếp” vào local variable tên `doubled` trong caller. Nó hoàn tất callee; kết quả được đẩy lên **operand stack của caller**, rồi caller có thể dùng `istore`.
- Không có một operand stack toàn cục duy nhất cho tất cả methods trong process.

## 8. Theo dấu `twicePlusOne(3)` qua hai lời gọi lồng nhau

Method:

```java
static int twicePlusOne(int value) {
    int doubled = combine(value, value);
    return combine(doubled, 1);
}
```

Bytecode dạng thường thấy:

```text
static int twicePlusOne(int);
  Code:
     0: iload_0
     1: iload_0
     2: invokestatic #... // combine:(II)I
     5: istore_1
     6: iload_1
     7: iconst_1
     8: invokestatic #... // combine:(II)I
    11: ireturn
```

`#...` là **ký hiệu minh họa cho constant-pool index**; số thật phải đọc trong `javap` của class đã compile. Method descriptor `(II)I` nghĩa **hai arguments kiểu int, return int**.

### Pha A: từ caller tới callee lần thứ nhất

| Caller action | Caller locals | Caller operand stack |
| --- | --- | --- |
| Mới vào `twicePlusOne(3)` | `[value=3, doubled=?]` | `[]` |
| `iload_0` | Không đổi | `[3]` |
| `iload_0` | Không đổi | `[3,3]` |
| `invokestatic combine` | Caller chờ | Hai args bị tiêu thụ để khởi tạo locals của callee |
| `combine` trả `6` | Không đổi | `[6]` |
| `istore_1` | `doubled=6` | `[]` |

Trong lúc `combine` đang chạy, **caller chưa thực hiện `istore_1`**. Nó phải chờ kết quả được trả về operand stack trước.

### Pha B: lời gọi lần thứ hai

| Caller action | Locals | Operand stack |
| --- | --- | --- |
| `iload_1` | `[value=3, doubled=6]` | `[6]` |
| `iconst_1` | Không đổi | `[6,1]` |
| `invokestatic combine` | Caller tạm dừng | Callee nhận args 6 và 1 |
| `combine` trả `7` | Không đổi | `[7]` |
| `ireturn` của `twicePlusOne` | Frame kết thúc | `7` chuyển tới operand stack của `compute` |

Sơ đồ những current frames theo thời gian:

```text
compute(base=3)
    │
    └─ twicePlusOne(value=3)
          │
          ├─ combine(3,3)   → returns 6
          │                    doubled = 6
          └─ combine(6,1)   → returns 7
                                │
                                ▼
compute receives 7 on its own operand stack
```

**Quan trọng:** trong suốt hai lời gọi, `value` và `doubled` thuộc **frame của twicePlusOne**, không tự xuất hiện thành locals của combine. `combine` chỉ nhận hai arguments theo **value** (primitive ints) vào **local slots riêng** của nó.

## 9. Theo dấu `compute(3)` và toàn chuỗi `main → compute → twicePlusOne → combine`

Method:

```java
static int compute(int base) {
    int first = twicePlusOne(base);
    int second = combine(first, 4);
    return second;
}
```

Một chuỗi bytecode điển hình:

```text
static int compute(int);
  Code:
     0: iload_0
     1: invokestatic #... // twicePlusOne:(I)I
     4: istore_1
     5: iload_1
     6: iconst_4
     7: invokestatic #... // combine:(II)I
    10: istore_2
    11: iload_2
    12: ireturn
```

Vẽ một chuỗi “đúng địa chỉ của giá trị”:

```text
main frame operand stack:        [3]
  invokestatic compute  ----►  compute local slot0=3, stack=[]
                                  iload_0: [3]
                                  invokestatic twicePlusOne
                                     ----► twicePlusOne slot0=3
                                                 stack: [3,3]
                                               invokestatic combine
                                                ----► combine slots=[3,3]
                                                       result=6
                                               returns into caller: [6]
                                                 istore_1 → doubled=6
                                                 stack: [6,1]
                                               invokestatic combine
                                                ----► combine slots=[6,1]
                                                       result=7
                                               returns into caller: [7]
                                                 ireturn 7
                                  compute operand stack: [7]
                                  istore_1 → first=7
                                  stack: [7,4]
                                  invokestatic combine
                                     ----► combine slots=[7,4]
                                            returns 11
                                  compute stack: [11]
                                  istore_2 → second=11
                                  iload_2; ireturn 11
main frame operand stack:        [11]
  istore_<slot> → computed=11
```

**`istore_<slot>` cuối cùng là schematic** vì compiler chọn local index của `computed` trong `main` sau `args` và các locals khác; xem `javap -c -p` để biết số thật. Ở các lệnh in chuỗi trong `main`, compiler cũng có thể sinh `invokedynamic` cho string concatenation: chúng không làm sai value flow bên trên.

### Một giá trị đi qua bao nhiêu operand stacks?

Giá trị `6` xuất hiện ở operand stack của `combine(3,3)` trước `ireturn`, rồi được chuyển theo return semantics sang **operand stack của twicePlusOne**. `7` được chuyển từ `combine(6,1)` về `twicePlusOne`, rồi từ `twicePlusOne` về `compute`. `11` được chuyển từ `combine(7,4)` về `compute`, rồi `compute` về `main`.

Đó là **logical transfer theo JVM** chứ không phải yêu cầu máy ảo bắt buộc copy từng word qua một vùng RAM cố định khi code đã được JIT.

## 10. `invoke*` thao tác những gì?

Các opcode chính để gọi method trong bytecode:

| Opcode | Trường hợp điển hình | Dữ liệu callee nhận |
| --- | --- | --- |
| `invokestatic` | Static method | Arguments từ operand stack, không có `this` |
| `invokevirtual` | Instance virtual method | Receiver + arguments; dynamic method selection |
| `invokeinterface` | Gọi method qua interface reference | Receiver + arguments; dispatch theo runtime type |
| `invokespecial` | Constructor, private method, một số super calls | Receiver + arguments theo semantics đặc biệt |
| `invokedynamic` | Lambda/string concatenation/other bootstrap-linked call sites | Theo call site descriptor và bootstrap linkage |

### Ví dụ lời gọi static

Với `combine:(II)I`:

```text
caller operand stack trước:
  [..., 7, 4]       // top=4
          │
          ▼  invokestatic combine:(II)I
callee locals:
  slot0=7
  slot1=4
callee operand stack:
  []                // ban đầu rỗng
          │
          ▼  ireturn 11
caller operand stack:
  [..., 11]
```

Điểm cần học: caller giữ những values **nằm dưới arguments**, còn argument values bị tiêu thụ bởi call. Sau khi callee return, một return value phù hợp descriptor được đặt lên stack caller nếu kiểu trả về không phải `void`.

### Thứ tự truyền arguments

Các expressions trong invocation Java được đánh giá theo thứ tự Java quy định, về bản chất **left-to-right** với operands/arguments. Điều đó không có nghĩa compiler phải dùng duy nhất một chuỗi bytes cho mọi cách viết; bytecode/JIT phải bảo toàn observable semantics, kể cả exception order và side effects.

## 11. Lời gọi instance: implicit `this` và `invokevirtual`

Trong `Node.transform(int input)`:

```java
int transform(int input) {
    int adjusted = combine(input, bias);
    return twicePlusOne(adjusted);
}
```

Local slots logic:

| Slot | Nội dung |
| --- | --- |
| 0 | `this`: reference tới `Node` hiện tại |
| 1 | `input`: int được truyền vào |
| 2 | `adjusted`: int local được gán sau `combine` |

Một bytecode sequence thường có dạng:

```text
0: iload_1         // input
1: aload_0         // this
2: getfield #...   // Node.bias:I
5: invokestatic #... // combine:(II)I
8: istore_2        // adjusted
9: iload_2
10: invokestatic #... // twicePlusOne:(I)I
13: ireturn
```

**Sequence này mang tính minh họa theo `javac`; đọc lại offsets và indices thực tế.** Lệnh `getfield` dùng reference lấy từ slot 0 để đọc `bias` trên object heap; `bias` **không nằm trong local variable array** chỉ vì code gọi `bias` không có `this.` tường minh.

### Trace trong `new Node(2).transform(3)`

```text
Node object trên heap: bias=2

Callee transform locals:
  local0 = this → Node{bias=2}
  local1 = input=3
  local2 = adjusted=?

transform:
  iload_1         stack [3]
  aload_0         stack [3, nodeRef]
  getfield bias   stack [3, 2]
  invokestatic combine → combine locals [3,2]
  return 5        stack [5]
  istore_2        local2=5, stack []
  iload_2         stack [5]
  invokestatic twicePlusOne → return 11
  ireturn         caller nhận 11
```

**Phân biệt:** gọi `new Node(2)` cũng chạy constructor `<init>` thông qua `invokespecial` sau `new/dup` và các arguments tương ứng. Việc “new object” và “invoke constructor” là các bytecode actions khác nhau, dù Java source viết liền trong `new Node(2)`.

### `Node` là `final` có ảnh hưởng tới trace không?

Mô hình bytecode frame vẫn như mô tả. Ở runtime, HotSpot JIT có thể nhận ra receiver type và inline `transform`; JVM specification vẫn định nghĩa invocation semantics phải được bảo toàn.

## 12. Object references: copy reference, không copy object

Ví dụ:

```java
static void mutateBox(Box box) {
    box.value = 99;
}
```

Đây là snippet, `Box` là một class giả định chưa được khai báo trong chương trình runnable chính. Khi caller push reference để gọi:

- Callee nhận **một reference value** trong local slot parameter.
- Caller vẫn giữ reference của mình nếu có.
- Hai reference values có thể trỏ cùng object.
- `box.value = 99` thay đổi **heap object**, caller nhìn thấy qua reference của mình.
- Gán lại `box = new Box()` trong callee **không thay** biến reference local trong caller.

Java truyền arguments **by value**: đối với objects, **giá trị được copy là reference**. Không có “pass-by-reference” theo nghĩa callee có quyền thay local variable slot của caller.

## 13. Category 1 và category 2: `long`/`double` chiếm hai local slots

JVMS chia computational types thành **category-1** (ví dụ `int`, `float`, references) và **category-2** (`long`, `double`). Trong local array, `long` và `double` chiếm **hai local variable indices liên tiếp**.

Method runnable trong `NestedFramesLab`:

```java
static long widen(long base, double adjustment) {
    long extra = (long) adjustment;
    return base + extra;
}
```

Vì method là `static`, positions logic:

| Local slot | Vai trò |
| --- | --- |
| 0–1 | `base` (long, chiếm 2 slots) |
| 2–3 | `adjustment` (double, chiếm 2 slots) |
| 4–5 | `extra` (long, chiếm 2 slots) |

Không được đọc `slot1` như một local `int` riêng chỉ vì nó có index 1. JLS/JVMS có rules để truy cập wide locals, và verifier cấm thao tác type-inconsistent. **Các bit representation** của category-2 values trong hai local slots do implementation tự lựa chọn theo ràng buộc JVMS.

Một bytecode pattern có thể gồm:

```text
dload_2        // load adjustment double từ slots 2–3
d2l            // double → long, truncation toward zero
lstore 4       // store extra ở slots 4–5
lload_0        // load base từ slots 0–1
lload 4        // load extra từ slots 4–5
ladd
lreturn
```

### Operand stack depth cũng tính units

Một operand stack có `long` và `double` ở đó không nhất thiết có đúng hai units nếu bạn chỉ đếm hai **values**. JVMS định nghĩa category-2 values góp hai units vào maximum depth accounting. Vì vậy:

```text
values trên stack: [longBase, longExtra]
số values: 2
số stack depth units: 4
```

**Không áp `dup` hay `swap` tùy ý cho category-2 values**: những lệnh này có type/category constraints; xem variants `dup2`, `dup2_x1`, `dup2_x2` trong JVMS instruction set.

### Cảnh giác kiểu `byte`, `short`, `char`, `boolean`

Trong nhiều bytecodes, các kiểu hẹp được thao tác bằng **int-compatible computational type** với các conversion/narrowing instructions khi cần; không phải JVM có một opcode arithmetic độc lập cho mọi phép cộng `byte` hoặc `boolean`.

## 14. `max_stack`, `max_locals` không phải số object/stack frames

Từ `javap -v -p NestedFramesLab`, mỗi method có `Code: stack=N, locals=M, args_size=K` hoặc tương đương. Các giá trị có ý nghĩa:

- **`max_locals`:** số slot local array **tối đa/được cấp** cho một invocation của method này, gồm implicit `this` và arguments.
- **`max_stack`:** operand stack depth bound **theo units**, đảm bảo bytecode có thể thực thi không stack overflow trong frame nếu class hợp lệ.
- **`args_size`:** công cụ giải thích số slot arguments/receiver theo method descriptor.
- **`LocalVariableTable`:** debug scope/slot mapping nếu có, không phải heap data.
- **`StackMapTable`:** verification type states ở control-flow transitions, **không phải** dump operand stack runtime tại mỗi instruction.

**Đặc biệt:** `max_stack=2` không có nghĩa thread chỉ được có hai nested method calls. Nó là giới hạn operand stack **của một method's frame**, không phải độ sâu call stack toàn thread. `StackOverflowError` thường liên quan call-stack depth/stack space của thread, không phải `max_stack` vượt ngưỡng trong method đã verified.

### Vùng local slots có thể được reuse

Compiler được phép tái sử dụng một slot cho nhiều source locals có lifetimes không trùng nhau. Vì thế **số biến bạn đếm trong source ≠ bắt buộc số `max_locals`**, và chỉ số cao nhất compiler dùng quan trọng hơn số tên biến.

## 15. Method descriptor định nghĩa các loại giá trị qua biên gọi

Dạng descriptor của Java methods theo JVMS `4.3.3`:

```text
combine(int,int) → int                 (II)I
twicePlusOne(int) → int                 (I)I
widen(long,double) → long              (JD)J
println(java.lang.String) → void       (Ljava/lang/String;)V
factorial(int) → int                   (I)I
```

Các ký hiệu:

| Descriptor | Java type |
| --- | --- |
| `I` | `int` |
| `J` | `long` |
| `D` | `double` |
| `F` | `float` |
| `V` | `void`, **chỉ** ở vị trí result type |
| `Z` | `boolean` |
| `B` | `byte` |
| `C` | `char` |
| `S` | `short` |
| `Lpkg/Class;` | Reference class/interface type |
| `[I` | Array of `int` |
| `[[Ljava/lang/String;` | Two-dimensional array of String references |

`javap -v` cho constant-pool symbolic refs như `Methodref ... combine:(II)I`. Số `#...` trong constant pool **không phải local slot number** và không phải method address CPU.

### Vì sao phải biết descriptor?

Vì lệnh `invoke*` phải lấy đúng **number/types** of argument values từ operand stack, rồi khởi tạo parameters trong callee locals. Khi có `long`/`double`, **number of values** khác **number of local slots** và có thể khác **stack depth units**.

## 16. Exception trong lời gọi lồng nhau: callee không trả return value

Trong demo:

```java
static int divide(int numerator, int denominator) {
    return numerator / denominator;
}

static int recover(int input) {
    try {
        return divide(input, 0);
    } catch (ArithmeticException ex) {
        return combine(input, 100);
    }
}
```

Trace:

```text
recover(input=5)
   │
   ├─ push [5, 0]
   ├─ invokestatic divide
   │       │ locals=[numerator=5, denominator=0]
   │       ├─ iload_0 → [5]
   │       ├─ iload_1 → [5,0]
   │       └─ idiv    → ArithmeticException
   │                    (no ireturn result)
   │
   ├─ divide frame unwinds abruptly
   ├─ recover exception handler receives exception object
   ├─ store exception reference into local ex (typical astore)
   ├─ push [input=5, 100]
   ├─ invokestatic combine → returns 105
   └─ ireturn 105
```

### Handler operand stack có trạng thái đặc biệt

Khi control transfers tới một matching exception handler, JVM chuyển đến handler với operand stack **chứa exception object reference** theo quy tắc exception handling, không phải giữ nguyên những temporary operands từ bytecode đang ném lỗi.

`javap` có thể hiển thị `Exception table` chứa `from`, `to`, `target`, `type`. Range `from..to` được hiểu theo offsets bytecode mà exception handler bảo vệ. Với `javac` version khác, compiler có thể tổ chức handler bytecodes hơi khác; xem output thực tế.

### Exception không là return value

Callee không push một `int 0` hay `int 105` khi `idiv` ném exception. `105` được tính **sau đó bởi catch block của recover**, khi frame `recover` vẫn còn và handler chạy.

Nếu không có handler ở `recover`, exception tiếp tục truyền lên caller; JVM có thể loại các frames không có handler cho tới khi tìm thấy handler thích hợp hoặc thread kết thúc do exception uncaught. `finally` blocks/resources có thể chạy trong quá trình unwind theo Java semantics; không suy chỉ có một lệnh `athrow` rồi mọi state biến mất tức thì.

## 17. Đệ quy: cùng một method, nhiều frames

Trong `factorial(n)`:

```java
static int factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}
```

Call chain:

```text
factorial(5): local0 n=5, chờ factorial(4)
  factorial(4): local0 n=4, chờ factorial(3)
    factorial(3): local0 n=3, chờ factorial(2)
      factorial(2): local0 n=2, chờ factorial(1)
        factorial(1): local0 n=1 → returns 1
      factorial(2): multiplier 2, child return 1 → 2
    factorial(3): multiplier 3, child return 2 → 6
  factorial(4): multiplier 4, child return 6 → 24
factorial(5): multiplier 5, child return 24 → 120
```

Mỗi invocation có **`local0 n` riêng**. Giá trị `n=5` của outer frame không bị thay thành 4 khi call recursive method. Caller có thể để một bản sao `n` trong **operand stack** khi chuẩn bị nhân với return value của recursive call.

Một bytecode shape thường gặp ở nhánh đệ quy:

```text
iload_0             // push n
iload_0             // push n again
iconst_1
isub                // n - 1
invokestatic #...   // factorial:(I)I, returns child result
imul                // n * childResult
ireturn
```

**Không vẽ stack states thiếu giá trị đang chờ:** trước lệnh recursive `invokestatic`, operand stack có thể chứa `n` (được giữ dưới argument `n-1`). Lệnh call pop argument nhưng vẫn giữ `n` ở operand stack của caller. Khi return, child result push lên **cùng operand stack caller**, nên `imul` mới có hai operands.

### Độ sâu có giới hạn

Nếu recursion quá sâu, mỗi nested call có thể tiêu tốn stack space, dẫn tới `StackOverflowError` theo runtime conditions. `-Xss` có thể tác động stack budget per thread, nhưng **không có độ sâu recursion cố định** đảm bảo cho mọi HotSpot build/JIT/OS. Đừng benchmark stack depth bằng recursion vô hạn trên production.

## 18. StackMapTable vs operand stack đang thực thi

**StackMapTable** trong class file lưu **verification type states** tại một số control-flow points, không phải “bản chụp toàn bộ mọi lần push/pop runtime”.

So sánh:

| Thành phần | Dùng làm gì? |
| --- | --- |
| Operand stack của frame đang chạy | Các values hiện thời được instructions push/pop |
| `Code.max_stack` | Bound độ sâu stack cho method trong class file |
| `StackMapTable` | Verifier kiểm tra types/state ở bytecode branches/handlers |
| `LocalVariableTable` | Debug mapping từ slot đến tên/type/scope |
| Runtime Java stack trace | Danh sách các method calls được VM/tác nhân debugging trình bày |
| HotSpot compiled frame/oop maps | Implementation metadata cho optimized machine code/deoptimization/GC |

### Vì sao `javap -v` có StackMapTable?

Các paths của `if/else`, loops và `catch` merge phải có type-safe locals/operand stack. Verifier dùng class-file constraints và stack maps để bảo đảm bytecode không dùng incompatible types. Thấy StackMapTable entry như `same_locals_1_stack_item_frame` thường báo stack type frame cho handler, **không** nghĩa ở mọi lần chạy chương trình luôn có một value ở đó.

### Hậu quả khi bytecode không hợp lệ

Một class file tùy biến có `iadd` nhưng operand stack chỉ có một int, hoặc dùng `aload` với local type không tương thích, có thể bị JVM verifier từ chối, chẳng hạn `VerifyError`. JVM không cố “tự đoán” thiếu operand hoặc gán một biến Java để hoàn thiện stack.

## 19. Source scope và lifetime của local slots không giống nhau

```java
static int scopeDemo(int start) {
    int result = start;
    {
        int first = start + 1;
        result += first;
    }
    {
        int second = start + 2;
        result += second;
    }
    return result;
}
```

Đây là một method snippet, **chưa được thêm vào file runnable chính**. Java source có hai block scopes: `first` và `second` không cùng phạm vi lexical. Compiler có thể reuse một local slot cho hai biến không đồng thời live. `LocalVariableTable` (khi có) ghi range offsets; một slot index có thể gắn hai names ở **hai ranges khác nhau**.

**Không suy** một local slot thuộc vĩnh viễn về một source identifier, cũng không gắn an toàn bộ nhớ Java heap với việc “out of lexical scope”: strong references có thể còn reachable theo các hình thức và thời điểm khác; collector/JIT liveness analysis có quyền vận dụng semantics.

### Ghi đè slot category-2

Một `long` chiếm slots `i` và `i+1`. Ghi một value vào một trong các slots có thể làm value `long` trước đó không còn hợp lệ để load, theo JVMS slot handling. Đừng diễn giải slot numbers như biến C primitive layout có thể truy cập từng nửa `long` bằng cách load `int`.

## 20. Các opcode thao tác stack dễ bị nhầm

| Opcode | Ý tưởng | Lưu ý |
| --- | --- | --- |
| `dup` | Copy top category-1 value | Hữu ích trong `new/dup/invokespecial` |
| `dup2` | Copy top two category-1 hoặc one category-2 theo hợp lệ | Không đơn giản là “dup hai Java source vars” |
| `swap` | Đổi thứ tự hai category-1 values trên đỉnh | Không áp thẳng lên category-2 values |
| `pop` | Bỏ top category-1 value | Với category-2 cần `pop2` thích hợp |
| `pop2` | Bỏ one category-2 hoặc two category-1 values | Có type/category constraints |
| `iinc` | Tăng/giảm int local slot | Không cần push/pop qua operand stack |
| `checkcast` | Kiểm tra cast đối với reference value | Có thể ném `ClassCastException` |
| `instanceof` | Pop reference rồi push int result 0/1 | Không trả raw Java boolean stack type |
| `athrow` | Ném Throwable reference | Điều khiển tới handler/unwind |
| `new` | Tạo uninitialized object reference | Constructor được invoke bằng `invokespecial` |
| `getfield` | Đọc field từ object reference | Field storage không nằm trong local slot của method |

### Tại sao `new Node(2)` thường cần `dup`?

Một bytecode pattern khái quát:

```text
new Node          stack [uninitNode]
dup               stack [uninitNode, uninitNode]
iconst_2          stack [uninitNode, uninitNode, 2]
invokespecial ... // Node.<init>:(I)V, consumes receiver & 2
                  // còn một initialized Node ref trên caller stack
astore_1          // lưu Node reference vào local
```

Lúc chưa chạy thành công constructor, verifier áp đặt restrictions đối với **uninitialized object reference**. `dup` ở đây giữ reference cho expression Java sau khi constructor hoàn tất; **không tạo thêm Node object**.

## 21. Đọc stack trace đúng mức độ: logical call chain vs optimized execution

Nếu ứng dụng ném exception, Java stack trace thường in chuỗi method names/line numbers. Nhưng stack trace **không liệt kê operand stack contents**, cũng không hiển thị local values theo cách `javap` tables làm.

- `Throwable.getStackTrace()` là **stack trace elements**, không là dump mọi local slots.
- Debugger với JDWP/JVMTI có thể quan sát local values trong những conditions phù hợp.
- HotSpot JIT có thể **inline** nhiều methods; VM có metadata/deoptimization mechanisms để hỗ trợ semantics và các forms debugging/stack walking theo contract.
- Không được tuyên bố một stack trace thiếu method do “có inlining” nếu chưa biết runtime, stack-walking contract hoặc logging config.
- Stack trace trong một exception được tạo/capture tại thời điểm liên quan, không phải transcript từng instruction của quá khứ.
- `javap` class file vẫn có `invokestatic` ngay cả khi machine code optimized đã inline ở runtime.

### Hai tầng quan sát khác nhau

```text
javap:
  “bytecode quy định invocation như thế nào?”

Debugger/JFR/profiler:
  “runtime tại thời điểm quan sát đang làm gì?”

JIT assembly (khi có công cụ phù hợp):
  “CPU thực sự được giao instruction nào sau optimize?”
```

Bài đang lần theo **mô hình frame/bytecode**. Đó là cơ sở đúng cho việc học JVM, nhưng không hứa rằng profiler sẽ hiện đúng từng interpreter frame sau JIT compilation.

## 22. JIT inlining có làm mất tính đúng của frame semantics không?

Không. JVMS xác định **observable behavior** của method calls, returns và exceptions. HotSpot JIT có thể inline method body, đặt values vào machine registers, dùng stack slots khác hoặc scalar replacement nếu tối ưu được mà vẫn bảo toàn behavior.

Ví dụ:

```java
static int combine(int a, int b) {
    return a + b;
}
```

Với caller nóng, C2 có thể inline body thay vì phải thực hiện native call/return thật. **Không kết luận** “vì không còn native call instruction nên Java không có local variables” — local variables/operand stack vẫn là mô hình class-file/JVMS. Khi compiled code cần deopt hay JVM hỗ trợ debugging, implementation phải duy trì thông tin cần thiết theo semantics.

### Một thay đổi nhỏ có thể đổi profile

Nếu `combine` được gọi triệu lần, có thể JIT compile; nếu chỉ gọi một lần trong `NestedFramesLab`, `javap` vẫn in bytecode nhưng VM có thể chỉ interpret hoặc dùng tiers khác. Không dùng demo ngắn làm bằng chứng inlining đã xảy ra. Muốn kiểm tra compiler decisions, trên HotSpot hỗ trợ:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining NestedFramesLab
```

Log có thể khác build, tăng overhead và gây output xen với stdout; **không có log inlining xác định**. Dùng để học compiler behavior, không thay `javap` để phân tích logical stack.

## 23. Multi-threading và stack: không chia sẻ frames, vẫn chia sẻ heap

Giả sử hai threads đều gọi `compute(3)`:

```text
Thread A JVM stack                 Thread B JVM stack
┌──────────────────┐               ┌──────────────────┐
│ combine frame A  │               │ combine frame B  │
├──────────────────┤               ├──────────────────┤
│ twicePlusOne A   │               │ twicePlusOne B   │
├──────────────────┤               ├──────────────────┤
│ compute frame A  │               │ compute frame B  │
└──────────────────┘               └──────────────────┘
                                     /
               _____ shared heap ____/
```

- Hai threads có **separate frames/locals/operand stacks**.
- Cùng `static` method code có thể được sử dụng, nhưng không cùng local variable array.
- Cả hai có thể giữ **reference tới cùng object**. Race conditions nảy sinh nếu truy cập shared mutable state thiếu đồng bộ, **không phải** vì local slots của hai threads bị trộn.
- `ThreadLocal<T>` là API giữ dữ liệu gắn với từng thread trong cơ chế riêng, **không đồng nghĩa local variable slot của mọi method frame**.
- `Thread.start()`/`join()` có happens-before semantics để phối hợp data trên shared heap, là chủ đề liên quan nhưng **khác** bài theo dấu operand stack.

## 24. Không nhầm call stack, operand stack, heap, native stack

| Khái niệm | Scope | Chứa gì? |
| --- | --- | --- |
| **JVM stack** | Per thread | Các frames theo calls/returns |
| **Frame local variable array** | Per invocation | Parameters, temp local values |
| **Frame operand stack** | Per invocation | Intermediate values theo bytecode instructions |
| **Heap** | JVM runtime shared | Objects/arrays được GC quản lý |
| **Runtime constant pool** | Per class/interface representation | Method/field symbolic refs, constants và linking info |
| **Native method stack** | Implementation/OS specific | Invocation context cho native methods khi được dùng |
| **CPU register/native stack** | Machine code thực thi | Compiler/runtime-specific physical state |

**Một `StackOverflowError` không đồng nghĩa `OutOfMemoryError: Java heap space`**, và `-Xmx` không trực tiếp xác định số lượng nested frames có thể tạo. Stack size constraints như `-Xss` khác heap capacity, nhưng giá trị thực vẫn phụ thuộc platform/JVM.

### Native methods không có bytecode `Code` giống Java methods

Một method khai báo `native` không có Java bytecode `Code` attribute; việc gọi có native calling convention và implementation details. Không áp trace `iload → iadd → ireturn` khi method body thực sự ở C/JNI.

## 25. Quy trình tự luyện trace từng instruction

Thay vì nhìn bảng bytecode rồi phỏng đoán, thực hiện theo thứ tự:

1. Xác định **method descriptor** và static/instance.
2. Ghi local slots ban đầu: `this` nếu instance, arguments theo đúng slots/categories.
3. Bắt đầu với **operand stack rỗng** cho frame mới.
4. Với mỗi bytecode, ghi **stack effect**: push/pop; cập nhật local slot khi `store/iinc`.
5. Gặp `invoke*`: kiểm tra receiver/arguments, **giữ phần operand stack dưới arguments** của caller; tạo frame callee với stack rỗng.
6. Theo callee cho tới `return/ireturn/areturn/lreturn/dreturn/freturn` hoặc exception.
7. Nếu normal return: phá frame callee, push result (nếu có) lên caller stack.
8. Nếu exception: theo `Exception table`/handler, unwind nếu caller không bắt.
9. Ở branch join points, kiểm tra stack/type states theo verifier/StackMapTable nếu cần.
10. Cuối cùng so **functional stdout** với kết quả source Java và `javap` actual.

### Mẫu bảng trace

```text
Method:  <tên + descriptor>
Caller frame: <tên method>
Current frame: <tên method>
Locals: L0=?, L1=?, L2=?...
Operand stack: []     (bottom → top)

offset | opcode | locals sau lệnh | operand stack sau lệnh
-------+--------+-----------------+-------------------------
...    | ...    | ...             | ...

Invocation transition:
  caller state trước → callee initial state
  callee final result  → caller resumed state
```

Mỗi hàng phải biểu diễn **state sau instruction** hoặc **state trước instruction** nhất quán; đừng lẫn hai kiểu vì rất dễ đếm thiếu/pop nhầm stack element.

## 26. Lỗi thường gặp khi trace

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| “Operand stack là nơi chứa toàn bộ nested calls.” | JVM stack chứa frames; mỗi frame có operand stack riêng |
| “Locals của `compute` được con `combine` đọc trực tiếp.” | Arguments được chuyển bằng invoke semantics sang locals mới |
| “`iload` di chuyển variable khỏi local array.” | Nó load **giá trị** lên operand stack, local vẫn còn |
| “`iadd` tự động gán kết quả vào biến Java.” | Nó push kết quả, `istore` mới lưu local |
| “`ireturn` gán trực tiếp tên biến caller.” | Return result push vào caller operand stack |
| “`this` nằm ở local slot 0 trong static method.” | Static method không có implicit receiver |
| “Instance method parameter đầu tiên là slot 0.” | Slot 0 là `this`, parameter bắt đầu slot 1 |
| “`long`/`double` chỉ cần một local slot.” | Hai local indices/category-2 |
| “Một category-2 value luôn có một unit stack.” | Tính hai units theo max_stack accounting |
| “`dup` có thể dùng với mọi category.” | Có constraints, dùng variants phù hợp |
| “`StackMapTable` là trace operand stack của một run.” | Là metadata verifier/type states |
| “`LocalVariableTable` là danh sách source variables bắt buộc.” | Debug attribute optional, compiler có thể reuse slots |
| “`max_stack` là độ sâu recursion tối đa.” | Là per-frame operand stack limit |
| “Tất cả Java vars ở heap.” | Local primitive values/references ở logical frames; referenced objects ở heap |
| “Copy object reference có nghĩa clone object.” | Chỉ copy reference value |
| “Callee ném exception nhưng vẫn `ireturn` một giá trị.” | Abrupt completion không return normal value |
| “Handler giữ mọi operands cũ của throwing instruction.” | Handler state chứa exception reference theo JVM semantics |
| “Lời gọi static dùng `invokevirtual`.” | Static dùng `invokestatic` |
| “`javap` là machine code C2.” | `javap` là class-file bytecode |
| “JIT inline làm Java correctness không còn theo JVMS.” | Runtime vẫn phải bảo toàn observable semantics |
| “Bytecode offsets luôn giống tất cả JDK vendors.” | Class-file output/compiler có thể khác |
| “Một Java thread dùng locals của thread khác.” | Frames per thread; heap references có thể chia sẻ |
| “`new/dup/invokespecial` tạo hai objects.” | `dup` copy một reference value, không tạo object khác |
| “StackOverflowError là Java heap OOME.” | Stack frame budget khác heap capacity |

## 27. Bài tập có đáp án

**Bài 1.** Gọi `combine(3,4)`, locals ban đầu và operand stack ban đầu?  
**Đáp án:** `L0=3, L1=4`, các locals khác chưa gán, operand stack `[]`.

**Bài 2.** Sau `iload_0; iload_1; iadd` trong `combine(3,4)`, stack chứa gì?  
**Đáp án:** `[7]`, local slots vẫn giữ hai arguments.

**Bài 3.** Sau `istore_2` thì sao?  
**Đáp án:** `L2=7`, stack rỗng.

**Bài 4.** `ireturn` làm gì với caller?  
**Đáp án:** Hoàn thành callee, đẩy return int result lên operand stack của caller.

**Bài 5.** `twicePlusOne(3)` trả bao nhiêu?  
**Đáp án:** `combine(3,3)=6`, rồi `combine(6,1)=7`.

**Bài 6.** `compute(3)` trả bao nhiêu?  
**Đáp án:** `twicePlusOne(3)=7`, `combine(7,4)=11`.

**Bài 7.** Trong lúc `combine(3,3)` chạy, `doubled` của caller đã được gán chưa?  
**Đáp án:** Chưa; caller chỉ thực hiện `istore_1` sau khi result 6 trả lại.

**Bài 8.** Local 0 của `Node.transform(3)` là gì?  
**Đáp án:** Reference `this` tới object `Node`; `input` ở slot 1.

**Bài 9.** `bias` có tự nằm trong local array của `transform` không?  
**Đáp án:** Không. `bias` là object field được đọc qua `aload_0`/`getfield`.

**Bài 10.** `Node(2).transform(3)` trả mấy?  
**Đáp án:** `combine(3,2)=5`, sau đó `twicePlusOne(5)=11`.

**Bài 11.** Descriptor `(II)I` nghĩa gì?  
**Đáp án:** Hai arguments kiểu int, trả về int.

**Bài 12.** Descriptor `(JD)J` nghĩa gì?  
**Đáp án:** Một long và một double, trả về long.

**Bài 13.** Với static `widen(long,double)`, `adjustment` bắt đầu ở slot nào?  
**Đáp án:** Slot 2 (chiếm slots 2–3), vì long base chiếm 0–1.

**Bài 14.** Local `extra` của `widen` thường chiếm slots nào?  
**Đáp án:** Hai slots liên tiếp 4–5 trong bytecode điển hình của `javac` JDK 17.

**Bài 15.** Operand stack có hai `long` values, depth units bao nhiêu?  
**Đáp án:** 4 units dù có hai values.

**Bài 16.** `d2l` trong ví dụ `2.75` cho giá trị gì?  
**Đáp án:** `2L` vì cast double sang long cắt phần fractional về 0.

**Bài 17.** `widen(10_000_000_000L,2.75)` trả gì?  
**Đáp án:** `10000000002`.

**Bài 18.** `recover(5)` trả bao nhiêu sau khi divide-by-zero?  
**Đáp án:** `105` do catch `ArithmeticException` và `combine(5,100)`.

**Bài 19.** Handler của `recover` bắt đầu với loại operand gì?  
**Đáp án:** Reference tới exception object trên operand stack của handler.

**Bài 20.** `factorial(5)` có một hay nhiều local `n`?  
**Đáp án:** Nhiều bản logical local `n`, mỗi invocation/frame riêng.

**Bài 21.** `factorial(5)` cuối cùng trả bao nhiêu?  
**Đáp án:** `120`.

**Bài 22.** Recursion bao sâu trước `StackOverflowError` có cố định giữa các JVM không?  
**Đáp án:** Không; phụ thuộc stack size/JIT/runtime/platform và call shape.

**Bài 23.** `max_stack=3` là có tối đa 3 nested calls?  
**Đáp án:** Không; giới hạn operand stack depth của method theo units.

**Bài 24.** `LocalVariableTable` không có sau `javac -g:none` nghĩa method không có locals?  
**Đáp án:** Không; method vẫn có local slots trong Code.max_locals, chỉ thiếu debug table.

**Bài 25.** `StackMapTable` có ghi giá trị 11 runtime sau `compute` không?  
**Đáp án:** Không. Nó mô tả verifier type states tại bytecode control-flow points.

**Bài 26.** Hai source locals không cùng live range có thể chung slot?  
**Đáp án:** Có; compiler có thể reuse local indices.

**Bài 27.** Vì sao `dup` ở `new Node(2)` không tạo hai Nodes?  
**Đáp án:** `dup` nhân đôi reference value, không cấp phát object lần hai.

**Bài 28.** Một call bị inline bởi C2 thì `javap` còn `invokestatic` không?  
**Đáp án:** Có, `javap` đọc class-file bytecode, không native JIT code.

**Bài 29.** Hai threads cùng gọi `combine` có cùng locals array không?  
**Đáp án:** Không; frames và locals là per-thread/per-invocation.

**Bài 30.** Nếu callee trả `void` thì caller stack nhận gì?  
**Đáp án:** Không có return value được push; operand stack còn những values trước args nếu có.

## 28. Checklist kiểm chứng một trace bytecode

- [ ] Đã dùng đúng class file/source build, `javac -g` nếu cần debug locals?
- [ ] Đã đọc method descriptor đúng static/instance và category types?
- [ ] Local slot 0 có đúng `this` hay argument đầu theo loại method?
- [ ] Đã tính category-2 slots cho `long/double`?
- [ ] Đã bắt đầu mỗi callee operand stack từ `[]`?
- [ ] Đã ghi từng `load/const/store/arithmetic` trước-sau nhất quán?
- [ ] Đã pop đúng receiver/arguments khi gặp `invoke*`?
- [ ] Đã giữ các values **bên dưới arguments** của caller trong khi callee chạy?
- [ ] Đã chuyển result về caller operand stack trước khi caller `store`?
- [ ] Với exception, đã tách abrupt completion khỏi normal return?
- [ ] Đã kiểm tra `Exception table` và handler stack có exception reference?
- [ ] Đã phân biệt object field `getfield` với local variables?
- [ ] Đã dùng `max_stack/max_locals` đúng nghĩa?
- [ ] Đã không nhầm StackMapTable với runtime operand values?
- [ ] Đã xem compiler có thể reuse local slots?
- [ ] Đã phân biệt primitive value copy và object reference copy?
- [ ] Đã lưu ý `javap` có thể in offset/CP index khác theo build?
- [ ] Đã đối chiếu stdout deterministic với số học?
- [ ] Đã nêu giới hạn JIT inlining/compiled frames khi so runtime profiler?
- [ ] Đã không suy stack depth cố định hay dùng recursion vô hạn để test?
- [ ] Đã tránh sửa source/lệnh khác rồi đọc nhầm class file chưa compile lại?

## 29. Kết luận

Lời gọi lồng nhau trong Java không diễn ra bằng cách mọi method chia sẻ một bảng biến. Mỗi **method invocation** nhận **frame logic độc lập**; arguments được chuẩn bị trên **operand stack caller**, chuyển vào **local slots callee**, callee tính toán bằng loads/stores và operand instructions, rồi **return result được đẩy về operand stack caller**. Nếu exception không được xử lý trong callee, các frames có thể unwind cho tới handler phù hợp; không có normal return value.

```text
CALLER:
  locals → iload/aload... → caller operand stack chứa arguments
                 │
                 ▼ invoke*
CALLEE:
  new locals[args...] + new empty operand stack
  bytecodes push/pop/store/invoke nested calls
                 │
                 ▼ return/ireturn/...  hoặc exception
CALLER:
  receives returned value on operand stack
  → istore/other operation → continues
```

Muốn học chắc: **biên dịch ví dụ Java 17+, xem `javap -c -l -v -p`, ghi bảng stack/locals trước và sau từng instruction**, rồi đối chiếu normal return, exception, instance `this`, category-2 slots và recursion. Đó là nền tảng cần thiết để hiểu bytecode verification, debugging JVM, deoptimization, memory model ở mức có cơ sở — mà không lẫn **mô hình bytecode** với **machine code JIT**.

### Tài liệu chuẩn

1. **Java Virtual Machine Specification, Java SE 17 — Chapter 2, JVM Structure / Frames (`2.6`):** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6
2. **JVMS `2.6.1` — Local Variables:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6.1
3. **JVMS `2.6.2` — Operand Stacks:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6.2
4. **JVMS `2.6.4–2.6.5` — Normal and Abrupt Invocation Completion:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6.4
5. **JVMS `4.3.3` — Method Descriptors:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3.3
6. **JVMS `4.7.3 — Code Attribute (`max_stack / max_locals):** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.3
7. **JVMS Chapter 6 — Bytecode Instruction Set:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html
8. **Java SE 17 — `javap` Disassembler:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html
9. **JVMS `4.7.4` — StackMapTable Attribute:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.4

**Thông điệp cuối:** hãy luôn xác định **“frame nào đang current, local slot nào đang giữ giá trị nào, operand stack đang có những values gì, và lệnh invoke/return chuyển điều khiển ra sao”**. Trả lời được bốn câu đó thì chuỗi lời gọi lồng nhau trở nên có thể theo dấu, thay vì chỉ là một loạt tên method trên stack trace.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-experiments-003">Dự đoán rồi kiểm chứng nạp, liên kết và khởi tạo qua ví dụ nhiều lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Dự đoán rồi kiểm chứng nạp, liên kết và khởi tạo qua ví dụ nhiều lớp

## 1. Học vòng đời lớp bằng cách dự đoán trước khi chạy

Một class có thể được **nạp (loading)**, được **liên kết (linking)** một phần hoặc hoàn toàn, nhưng khối `static` của nó **chưa chạy**. Một class khác có thể khởi tạo superclass, interface có default method hoặc kích hoạt khởi tạo class phụ thuộc trước khi bản thân nó thực thi initializer.

Trong bài thực nghiệm JVM Java 17 này, ta dùng **nhiều class và interface**, dự đoán **stdout** rồi kiểm chứng bằng `javac`, `java`, `javap` và log HotSpot. Kết quả quan trọng:

- **Loading không đồng nghĩa initialization.**
- **Verification, preparation và resolution** là các hoạt động linking khác nhau; resolution có thể sớm hoặc muộn trong phạm vi JVMS cho phép.
- **Constant variable** `static final int` hoặc `static final String` với compile-time constant expression có thể được caller inline; đọc nó không tự yêu cầu khởi tạo class khai báo.
- `SomeType.class`, tạo array với component type và `Class.forName(name,false,loader)` không tự chạy `<clinit>` của class ấy.
- `new`, đọc/ghi static nonconstant field, gọi static method, một số reflection/MethodHandle operations có thể là **active use** yêu cầu initialization.
- Với class, superclass phải được initialize trước subclass; những superinterfaces khai báo default methods cũng tham gia quy trình.
- Với interface, initialize interface **không tự động initialize superinterfaces**.
- Hai threads không chạy `<clinit>` hai lần song song cho cùng runtime class; recursion cùng thread và lỗi initialization có quy tắc riêng.
- **Loader identity** và **initialization state** khác nhau; đừng coi binary name là đủ để xác định duy nhất runtime class.

Bài dùng **JLS/JVMS Java SE 17** cho những cam kết bắt buộc, và **HotSpot/OpenJDK 17** để quan sát logging. Không đoán cụ thể thời điểm HotSpot tải hoặc resolve từng dependency vì specification cho phép nhiều chiến lược hợp lệ.

## 2. Bản đồ các bước

```text
class bytes
    |
    v
Loading: tạo runtime class/interface, gắn với defining loader
    |
    v
Linking:
    +-- Verification: kiểm tra cấu trúc/bytecode/type safety
    +-- Preparation: static fields nhận default values
    +-- Resolution: chuyển symbolic references thành runtime entities
         (có thể diễn ra eager hoặc lazy, không nhất thiết xong toàn bộ)
    |
    v
Initialization khi quy tắc active use yêu cầu:
    +-- đồng bộ initialization state
    +-- initialize superclass/default-method interfaces phù hợp
    +-- thực thi static field initializers/static blocks qua <clinit>
    |
    v
Sử dụng class đã initialized

Lưu ý: một số resolutions vẫn có thể diễn ra sau initialization.
```

### Loading

Class loader tìm/nhận binary class representation, JVM tạo runtime `Class`. Trong Java 17, cùng **binary name** với **hai defining class loaders** khác nhau có thể tạo **hai runtime types** khác nhau. Custom loader có thể delegation cho parent; vì vậy hai initiating loaders chưa chắc là hai defining loaders.

`Class<?>` tồn tại không có nghĩa class đã initialized. Nó cũng không bảo đảm mọi constant-pool reference đã resolved.

### Linking

| Bước | Mục đích | Điều chưa xảy ra nhất thiết |
| --- | --- | --- |
| **Verification** | Class file hợp lệ, rules của bytecode và type checking | Không mặc định chạy static code |
| **Preparation** | Chuẩn bị storage static fields, đặt giá trị default `0`, `false`, `null` tùy kiểu | Không chạy `static int x = calculate()` |
| **Resolution** | Tìm runtime class/field/method từ symbolic reference trong constant pool | Không buộc resolve tất cả trước khi init |

**Đặc biệt:** trong JVMS 17, các `final static` fields với `ConstantValue` được gán theo **bước của initialization procedure `5.5`**, sau kiểm tra trạng thái khởi tạo, trước superclass/default-method interface initialization tương ứng. Phân biệt điều ấy với việc **preparation tạo static fields ở default values** và việc **javac inline constant tại caller**.

JVM có thể chọn resolution **lazy** hoặc **eager** cho nhiều loại symbolic references; nếu lớp hiện tại loaded rồi, một lỗi liên kết của reference hiếm dùng vẫn có thể bị phát hiện muộn trên execution path ấy.

### Initialization

`<clinit>` là **class/interface initialization method** mà compiler tạo từ static field initializers và static blocks khi cần. Không phải constructor `<init>`. JVM bảo đảm initialize type khi một trong những điều kiện được quy định xảy ra; static initializers trong cùng type được thực hiện theo thứ tự văn bản phù hợp JLS.

**Không có dòng log initializer** chưa phải bằng chứng class chưa initialized: class có thể không có static initializer tạo output.

## 3. Quy trình thực nghiệm

1. Với mỗi thí nghiệm, đọc các classes và **viết dự đoán stdout trước**.
2. Biên dịch bằng `javac --release 17 FileName.java`.
3. Chạy trong **JVM process mới** bằng `java MainClass`.
4. Đối chiếu stdout nghiệp vụ, không trộn với diagnostic output.
5. Dùng `javap -c -v -p` để xem bytecode/cờ/constant pool và `<clinit>`.
6. Dùng `-Xlog:class+load` hoặc tag init được HotSpot hỗ trợ để nhìn những sự kiện runtime có ích.
7. Thay **một thao tác active use** rồi dự đoán lại trước khi chạy.
8. Với concurrency hoặc lỗi, đọc thêm quy tắc synchronization/init state ở JVMS `5.5`.

Nếu chạy lại một method trong **cùng JVM**, một class đã init thành công sẽ không tự chạy `<clinit>` lần nữa. Vì vậy **mỗi ví dụ phải là process mới** để so expected stdout.

## 4. Thí nghiệm 1 — hằng, class literal, reflection không init và tạo subclass

Tạo file **`LifecycleLab.java`**. Chỉ `LifecycleLab` là `public`; các classes còn lại ở cùng file:

```java
public class LifecycleLab {
    public static void main(String[] args) throws Exception {
        System.out.println("main");
        System.out.println("constant=" + Anchor.MAGIC);
        System.out.println("label=" + Anchor.LABEL);
        System.out.println("literal=" + Anchor.class.getSimpleName());

        Class<?> ref = Class.forName(
                "Anchor",
                false,
                LifecycleLab.class.getClassLoader());
        System.out.println("forNameFalse=" + ref.getSimpleName());

        System.out.println("beforeActive");
        System.out.println("dynamic=" + Anchor.VALUE);
        System.out.println("afterActive");

        System.out.println("newChild");
        ChildL child = new ChildL();
        System.out.println("created=" + child.getClass().getSimpleName());
    }
}

class Anchor {
    static final int MAGIC = 41;
    static final String LABEL = "constant";
    static int VALUE = initialize();

    static int initialize() {
        System.out.println("Anchor.<clinit>");
        return 42;
    }
}

class BaseL {
    static {
        System.out.println("BaseL.<clinit>");
    }

    BaseL() {
        System.out.println("BaseL.<init>");
    }
}

class ChildL extends BaseL {
    static {
        System.out.println("ChildL.<clinit>");
    }

    ChildL() {
        System.out.println("ChildL.<init>");
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 LifecycleLab.java
java LifecycleLab
```

**Stdout xác định theo Java 17:**

```text
main
constant=41
label=constant
literal=Anchor
forNameFalse=Anchor
beforeActive
Anchor.<clinit>
dynamic=42
afterActive
newChild
BaseL.<clinit>
ChildL.<clinit>
BaseL.<init>
ChildL.<init>
created=ChildL
```

### Vì sao từng dòng có vị trí đó?

- `LifecycleLab` được initialized để gọi `main`, nhưng không có static initializer in.
- `Anchor.MAGIC` và `Anchor.LABEL` là **constant variables**: đọc chúng trong caller không bắt buộc initialize `Anchor`, và `javac` có thể inline giá trị.
- `Anchor.class` lấy một class literal. JVM có thể phải load/resolve `Anchor`, nhưng **không buộc chạy `Anchor.<clinit>`**.
- `Class.forName("Anchor", false, loader)` tìm class và **không yêu cầu initialize** khi flag là false.
- `Anchor.VALUE` không phải constant variable, vì field không `final`. Đọc nó kích hoạt class initialization, gọi `initialize()`, in `Anchor.<clinit>` rồi trả 42.
- `new ChildL()` buộc initialize `ChildL`. Nó phải initialize `BaseL` trước; sau đó mới thực hiện constructors theo superclass-to-subclass chain.
- Vì vậy **`BaseL.<clinit>` và `ChildL.<clinit>`** xuất hiện trước **`BaseL.<init>` và `ChildL.<init>`**.

Bài học: **load/link có thể xảy ra trước mà không in**. Stdout static blocks chứng minh **code đã chạy**, không đo trực tiếp thời điểm verification, preparation hoặc resolution.

## 5. Phân biệt compile-time constant với field final runtime

| Khai báo | Constant variable? | Thường có active-use initialization khi đọc? |
| --- | --- | --- |
| `static final int N = 41;` | Có | Không chỉ vì đọc field ấy |
| `static final String S = "hello";` | Có | Không |
| `static final long T = 2L * 3;` | Có | Không |
| `static final Integer BOX = 41;` | Không: reference type khác String | Có |
| `static final int N = compute();` | Không: method call | Có |
| `static final String S = new String("x");` | Không: object construction | Có |
| `static int N = 41;` | Không: không final | Có |

**Constant variable** theo JLS `4.12.4` là một `final` variable của primitive type hoặc `String`, được khởi tạo bằng **constant expression** theo JLS `15.29`. Không phải mọi field có chữ `final` đều đủ điều kiện.

Bản class file có thể có `ConstantValue` cho compile-time constants. Caller đã biên dịch có thể chứa literal thay vì `getstatic` tới constant ấy.

### Bẫy binary compatibility

Nếu thư viện public khai báo:

```java
public static final int API_VERSION = 1;
```

và một client đã compile, client có thể inline literal 1. Chỉ thay `Library.class` để hằng thành 2 nhưng **không biên dịch lại Client** có thể khiến client vẫn thấy 1. Không phải “JVM quên reload class”; là semantics của compile-time constant và binary compatibility. Tránh thay đổi giá trị public constant mà không xét consumers.

## 6. Thí nghiệm 2 — field kế thừa: chỉ type khai báo mới init

Tạo **`InheritedStaticDemo.java`**:

```java
public class InheritedStaticDemo {
    public static void main(String[] args) {
        System.out.println("begin");
        System.out.println("inherited=" + SubCounter.VALUE);
        System.out.println("beforeOwn");
        System.out.println("own=" + SubCounter.OWN);
    }
}

class SuperCounter {
    static int VALUE = initialize();

    static int initialize() {
        System.out.println("SuperCounter.<clinit>");
        return 11;
    }
}

class SubCounter extends SuperCounter {
    static int OWN = initializeChild();

    static int initializeChild() {
        System.out.println("SubCounter.<clinit>");
        return 22;
    }
}
```

```bash
javac --release 17 InheritedStaticDemo.java
java InheritedStaticDemo
```

**Stdout:**

```text
begin
SuperCounter.<clinit>
inherited=11
beforeOwn
SubCounter.<clinit>
own=22
```

`SubCounter.VALUE` là field **thật sự được khai báo trong `SuperCounter`**. Cú pháp tên subclass ở bên trái dấu chấm **không** buộc subclass init. Khi đọc `SubCounter.OWN`, subclass mới bị active use. Superclass đã initialized nên không in thêm lần hai.

Tương tự với một **static method kế thừa**: viết `SubClass.inheritedStaticMethod()` không tự khiến `SubClass.<clinit>` chạy khi method được khai báo bởi superclass. Quy tắc nói tới **declaring type của resolved field/method**, không phải qualifier source.

## 7. Thí nghiệm 3 — `ClassLoader.loadClass`, `forName(false)` và `Field.get`

Tạo **`ReflectionLoadingDemo.java`**:

```java
import java.lang.reflect.Field;

public class ReflectionLoadingDemo {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = ReflectionLoadingDemo.class.getClassLoader();

        Class<?> first = loader.loadClass("ReflectTarget");
        System.out.println("loadClass=" + first.getSimpleName());

        Class<?> second = Class.forName("ReflectTarget", false, loader);
        System.out.println("sameClass=" + (first == second));

        Field field = second.getDeclaredField("VALUE");
        System.out.println("fieldName=" + field.getName());

        System.out.println("beforeGet");
        System.out.println("value=" + field.get(null));
        System.out.println("afterGet");

        Class<?> third = Class.forName("ReflectTarget", true, loader);
        System.out.println("sameAgain=" + (second == third));
    }
}

class ReflectTarget {
    static int VALUE = initialize();

    static int initialize() {
        System.out.println("ReflectTarget.<clinit>");
        return 77;
    }
}
```

```bash
javac --release 17 ReflectionLoadingDemo.java
java ReflectionLoadingDemo
```

**Stdout:**

```text
loadClass=ReflectTarget
sameClass=true
fieldName=VALUE
beforeGet
ReflectTarget.<clinit>
value=77
afterGet
sameAgain=true
```

- `loadClass(String)` không mặc định yêu cầu initialize; loading không phải active use của static field.
- `Class.forName(name,false,loader)` cố tình không initialize.
- `getDeclaredField` lấy metadata, không đọc giá trị.
- `Field.get(null)` truy cập static field nên JVM phải initialize `ReflectTarget` để đọc 77.
- `Class.forName(name,true,loader)` không in thêm `<clinit>` vì class đã init.

**Tự thử:** chuyển `forName(true)` lên trước `Field.get(null)` rồi dự đoán. Lúc ấy `ReflectTarget.<clinit>` phải xuất hiện tại chỗ `forName(true)`, không đợi `get`.

Khi không tìm được class, `forName`/`loadClass` có thể ném `ClassNotFoundException`. Nếu initialize thất bại hoặc linkage có lỗi, các `LinkageError`/`ExceptionInInitializerError` có semantics riêng. Không trộn mọi loại lỗi thành “file class thiếu”.

## 8. Thí nghiệm 4 — interface init không tự init superinterface

Tạo **`InterfaceInitDemo.java`**:

```java
public class InterfaceInitDemo {
    public static void main(String[] args) {
        System.out.println("begin");
        System.out.println("baseConstant=" + LeafInterface.BASE);
        System.out.println("leafDynamic=" + LeafInterface.LEAF);
        System.out.println("baseDynamic=" + RootInterface.ROOT);
    }
}

class InterfaceTrace {
    static int value(String message, int value) {
        System.out.println(message);
        return value;
    }
}

interface RootInterface {
    int BASE = 10;
    int ROOT = InterfaceTrace.value("RootInterface.<clinit>", 20);
}

interface LeafInterface extends RootInterface {
    int LEAF = InterfaceTrace.value("LeafInterface.<clinit>", 30);
}
```

```bash
javac --release 17 InterfaceInitDemo.java
java InterfaceInitDemo
```

**Stdout:**

```text
begin
baseConstant=10
LeafInterface.<clinit>
leafDynamic=30
RootInterface.<clinit>
baseDynamic=20
```

`LeafInterface.BASE` là constant variable khai báo bởi `RootInterface`, access không tự initialize interface ấy. `LeafInterface.LEAF` không phải compile-time constant, nên initialize **LeafInterface**. **Initialization của interface không tự initialize superinterface**. Cuối cùng đọc `RootInterface.ROOT` mới làm RootInterface chạy initializer.

**Không suy ra** khi khởi tạo một class implementing interfaces thì không interface nào initialized: **superinterfaces có default methods** có quy tắc khác, xem thí nghiệm tiếp.

## 9. Thí nghiệm 5 — class implements interface có default method

Tạo **`DefaultMethodInitDemo.java`**:

```java
public class DefaultMethodInitDemo {
    public static void main(String[] args) {
        System.out.println("before");
        DefaultChild value = new DefaultChild();
        System.out.println("created=" + value.getClass().getSimpleName());
    }
}

class DefaultTrace {
    static int value(String text) {
        System.out.println(text);
        return 1;
    }
}

interface HasDefaultMethod {
    int TOKEN = DefaultTrace.value("HasDefaultMethod.<clinit>");

    default void ping() {
    }
}

class DefaultParent {
    static {
        System.out.println("DefaultParent.<clinit>");
    }
}

class DefaultChild extends DefaultParent implements HasDefaultMethod {
    static {
        System.out.println("DefaultChild.<clinit>");
    }
}
```

```bash
javac --release 17 DefaultMethodInitDemo.java
java DefaultMethodInitDemo
```

**Stdout:**

```text
before
DefaultParent.<clinit>
HasDefaultMethod.<clinit>
DefaultChild.<clinit>
created=DefaultChild
```

Lần đầu `new DefaultChild` kích hoạt class initialization. JVM init superclass `DefaultParent` trước, sau đó interface có default method `HasDefaultMethod` theo danh sách liên quan, rồi `DefaultChild`. Các class constructors trong ví dụ không in gì.

**Thứ tự với nhiều interfaces rất phức tạp** được quy định bởi JVMS `5.5` bằng thứ tự duyệt superinterface hierarchy. Không áp đặt quy tắc đơn giản “mọi interface trong implements đều init ngay từ trái sang phải” cho bất kỳ graph tùy ý.

## 10. Thí nghiệm 6 — static values mặc định trong circular initialization

Preparation tạo `int` static field với default `0`. Khi class A đang initialize và cần B, B có thể đọc giá trị **chưa được A gán cuối cùng** nếu B yêu cầu lại init A từ **cùng thread**.

Tạo **`CyclicInitializationDemo.java`**:

```java
public class CyclicInitializationDemo {
    public static void main(String[] args) {
        System.out.println("main");
        System.out.println("finalA=" + CycleA.a);
        System.out.println("finalB=" + CycleB.b);
    }
}

class CycleA {
    static int a = CycleB.b + 1;

    static {
        System.out.println("CycleA.a=" + a + ",b=" + CycleB.b);
    }
}

class CycleB {
    static int b = CycleA.a + 1;

    static {
        System.out.println("CycleB.b=" + b + ",a=" + CycleA.a);
    }
}
```

```bash
javac --release 17 CyclicInitializationDemo.java
java CyclicInitializationDemo
```

**Stdout:**

```text
main
CycleB.b=1,a=0
CycleA.a=2,b=1
finalA=2
finalB=1
```

**Giải thích tuần tự:**

1. Main đòi `CycleA.a` → JVM bắt đầu init A.
2. A cần `CycleB.b` → bắt đầu init B.
3. B cần `CycleA.a`, nhưng A đang initialize bởi **chính thread ấy** → yêu cầu đệ quy được coi là hoàn thành, **không chạy lại A initializer**.
4. `CycleA.a` vẫn là default `0`, vì A chưa hoàn thành RHS.
5. B gán `b=1` và in `CycleB.b=1,a=0`.
6. A tiếp tục, gán `a=1+1=2`, in `CycleA.a=2,b=1`.
7. Main in kết quả cuối cùng.

**Cảnh báo:** đây là **circular static initialization**, không phải `ClassCircularityError`. Error ấy liên quan vòng kế thừa hoặc class derivation không hợp lệ. Một chương trình có circular static references hợp lệ vẫn có thể thấy default values và logic bug.

Thiết kế production nên tránh side effects/phụ thuộc vòng trong static initializers, nhất là I/O hoặc code có thể block. Đưa initialization phức tạp vào explicit lifecycle/factory khi cần kiểm soát lỗi và retries.

## 11. Thí nghiệm 7 — hai threads cùng active-use một class

JVM phải đồng bộ initialization state theo runtime class. Nếu một thread đang initialize và thread khác đòi dùng class ấy, thread thứ hai không chạy `<clinit>` lặp lại song song.

Tạo **`ConcurrentInitializationDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class ConcurrentInitializationDemo {
    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        int[] results = new int[2];

        Thread a = new Thread(() -> {
            await(start);
            results[0] = SlowClass.VALUE;
        }, "worker-A");

        Thread b = new Thread(() -> {
            await(start);
            results[1] = SlowClass.VALUE;
        }, "worker-B");

        a.start();
        b.start();
        start.countDown();
        a.join();
        b.join();

        System.out.println("a=" + results[0]);
        System.out.println("b=" + results[1]);
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

class SlowClass {
    static final int VALUE = init();

    static int init() {
        System.out.println("SlowClass.<clinit>");
        return 7;
    }
}
```

```bash
javac --release 17 ConcurrentInitializationDemo.java
java ConcurrentInitializationDemo
```

**Stdout khi workers kết thúc bình thường:**

```text
SlowClass.<clinit>
a=7
b=7
```

`SlowClass.VALUE` là `static final` nhưng **không phải compile-time constant**, do gọi `init()`. Chỉ một thread chạy class initializer. Thread còn lại phải chờ/quan sát successful initialization theo JVM rules. Main `join()` cả hai rồi in kết quả.

**Giới hạn:** `CountDownLatch` khiến hai workers bắt đầu gần nhau, nhưng không đảm bảo thực tế có contention ở initialization lock mỗi lần. Đừng dùng stdout để khẳng định một thread đã thật sự từng ở trạng thái BLOCKED; cần quan sát thread dump/diagnostics thích hợp khi điều tra sâu.

### Initialization locks và happens-before

JVMS `5.5` mô tả một initialization lock cho mỗi class/interface, các trạng thái `in progress`, `fully initialized`, `erroneous` và quy tắc chờ threads khác. JVM có thể tối ưu fast path của class đã init nhưng vẫn phải duy trì những **happens-before** semantics tương ứng.

Không cần tự viết `synchronized` mỗi khi đọc một static field immutable sau initialization thành công.

## 12. Thí nghiệm 8 — lỗi trong `<clinit>`, lần đầu và lần sau

Tạo **`InitializationFailureDemo.java`**:

```java
public class InitializationFailureDemo {
    public static void main(String[] args) {
        try {
            System.out.println(BrokenClass.VALUE);
        } catch (ExceptionInInitializerError e) {
            System.out.println("first=" + e.getClass().getSimpleName());
        }

        try {
            System.out.println(BrokenClass.VALUE);
        } catch (NoClassDefFoundError e) {
            System.out.println("second=" + e.getClass().getSimpleName());
        }
    }
}

class BrokenClass {
    static int VALUE = initialize();

    static int initialize() {
        System.out.println("BrokenClass.<clinit>");
        throw new IllegalStateException("boom");
    }
}
```

```bash
javac --release 17 InitializationFailureDemo.java
java InitializationFailureDemo
```

**Stdout:**

```text
BrokenClass.<clinit>
first=ExceptionInInitializerError
second=NoClassDefFoundError
```

Lần đầu access, `BrokenClass` bắt đầu initialization rồi ném `IllegalStateException` (không phải subclass của `Error`). JVM bọc nó bằng `ExceptionInInitializerError` và đánh dấu class **erroneous**. Thử init class ấy lần sau **không retry initializer**, mà gặp `NoClassDefFoundError`.

**Đừng suy ra** mọi `NoClassDefFoundError` nghĩa là thiếu class file trên disk: một previous initialization failure cũng tạo triệu chứng ấy. Lần đầu initializer ném một subclass của `Error` trực tiếp có quy tắc propagation khác; ví dụ được chọn có chủ ý để đầu ra xác định.

Nếu khởi động **JVM process mới**, runtime class state mới, lần đầu init lại có thể lặp lỗi. Nếu nhiều defining class loaders, mỗi runtime class identity có initialization state riêng.

## 13. Thí nghiệm 9 — tạo array không khởi tạo component

Tạo **`ArrayClassInitDemo.java`**:

```java
public class ArrayClassInitDemo {
    public static void main(String[] args) {
        System.out.println("begin");
        ArrayTarget[] values = new ArrayTarget[2];

        System.out.println("length=" + values.length);
        System.out.println("component="
                + values.getClass().getComponentType().getSimpleName());

        System.out.println("classLiteral="
                + ArrayTarget.class.getSimpleName());
        System.out.println("beforeNew");

        values[0] = new ArrayTarget();
        System.out.println("afterNew");
    }
}

class ArrayTarget {
    static {
        System.out.println("ArrayTarget.<clinit>");
    }

    ArrayTarget() {
        System.out.println("ArrayTarget.<init>");
    }
}
```

```bash
javac --release 17 ArrayClassInitDemo.java
java ArrayClassInitDemo
```

**Stdout:**

```text
begin
length=2
component=ArrayTarget
classLiteral=ArrayTarget
beforeNew
ArrayTarget.<clinit>
ArrayTarget.<init>
afterNew
```

`new ArrayTarget[2]` tạo **một array** với hai phần tử reference ban đầu `null`, không tạo hai instances của `ArrayTarget`. Tạo array class hoặc lấy `Class` của component không tự yêu cầu initialize component class. `new ArrayTarget()` mới dẫn đến `ArrayTarget.<clinit>` rồi constructor.

JVM có thể load component type sớm khi xử lý array class; thí nghiệm **không xác định timestamp loading chính xác**.

## 14. Những bytecode nào thường liên quan active use?

| Cú pháp/hành động | Bytecode thường liên quan | Có yêu cầu initialize type? |
| --- | --- | --- |
| `new Widget()` | `new` rồi `invokespecial` | Có đối với Widget |
| `Widget.N`, field runtime | `getstatic` | Có đối với declaring type |
| `Widget.N = 3` | `putstatic` | Có đối với declaring type |
| `Widget.run()`, static method | `invokestatic` | Có đối với declaring type |
| `Widget.CONST` compile-time constant | literal `ldc`/push instruction | Không riêng constant access |
| `Widget.class` | `ldc` class constant | Không riêng class literal |
| `new Widget[10]` | `anewarray` | Không tự init Widget component |
| `obj instanceof Widget` | `instanceof` | Không riêng type test |
| `(Widget) obj` | `checkcast` | Không riêng cast |
| `Class.forName(..., false, loader)` | method calls tới Class API | Không init target theo flag false |
| `Class.forName(..., true, loader)` | method calls tới Class API | Có khi target chưa initialized |
| `loader.loadClass(name)` | method call tới ClassLoader API | Không mặc định initialize target |
| `Field.get(null)` với static field | reflection | Có khi chưa initialized |

Cột opcode là **dạng compiler thường sinh**, không phải cam kết bytecode luôn giống hệt một instruction pattern. JVM có thêm các trường hợp MethodHandle bootstrap/dynamic call sites và reflection như JVMS `5.5` mô tả.

**Bẫy:** một expression như `new Widget[Other.size()]` có thể khởi tạo **Other** khi gọi static method trong kích thước array, dù vẫn không init **Widget** chỉ vì tạo array.

## 15. Đọc bytecode để giải thích thí nghiệm

Sau khi biên dịch từng file bằng `javac --release 17`, dùng:

```bash
javap -v -c -p LifecycleLab
javap -v -c -p Anchor
javap -v -c -p BaseL
javap -v -c -p ChildL
javap -v -c -p InheritedStaticDemo
javap -v -c -p CycleA
javap -v -c -p CycleB
```

Tìm:

- `Constant pool`: `CONSTANT_Class`, `CONSTANT_Fieldref`, `CONSTANT_Methodref`, `CONSTANT_String` và symbolic references.
- `ConstantValue` attribute của những compile-time constants được lưu trong class file.
- `<clinit>`: JVM class/interface initialization method chứa static block và code static initialization khi có.
- `getstatic Anchor.VALUE` trong caller: active use của field runtime.
- `ldc`/constant-push operations dùng thay `getstatic` khi caller đọc constant variable.
- `new ChildL` và `invokespecial ChildL.<init>` cho tạo instance.
- `anewarray` khi tạo arrays.
- `invokedynamic` có thể xuất hiện cho string concatenation ở bytecode Java 17.

**Không hiểu nhầm `javap`:** nó đọc class-file bytes **tĩnh**, không chỉ ra reference nào đã được runtime resolved vào timestamp nào; cũng không hiển thị machine code JIT sau inlining.

**Không hứa constant pool indexes/BCI offsets giống nhau** giữa các builds. Dự đoán ở đây về **hành vi Java-level**, còn thứ tự opcode phụ và dạng concatenation có thể đổi.

## 16. HotSpot class loading/initialization logging

Bật logging trên HotSpot/OpenJDK Java 17:

```bash
java -Xlog:help
java -Xlog:class+load=info LifecycleLab
```

Nếu build hỗ trợ tag set `class+init`:

```bash
java '-Xlog:class+load=info,class+init=info' LifecycleLab
```

Hoặc ghi file riêng để tránh trộn với stdout:

```bash
java '-Xlog:class+load=info,class+init=info:file=class-events.log:time,uptime,level,tags' LifecycleLab
```

**Không có output class log cố định**. Runtime có thể load hàng trăm JDK classes và thực hiện work ở thời điểm phụ thuộc implementation. `-Xlog:help` cho biết tags/levels có thật trên binary đang chạy.

Đọc log đúng:

1. Tìm `Anchor`, `BaseL`, `ChildL` trong class-load messages.
2. Xác nhận load event **không đồng nghĩa** initializer đã chạy.
3. So class-init events nếu có, với dòng `Anchor.<clinit>` do application tự in.
4. Đừng mặc định verification/resolution của mọi field/method đã hoàn thành khi thấy class-load message.
5. Nếu chương trình chạy trong nhiều JVM processes, gắn PID/startup timestamp để tránh trộn class loading events.
6. Khi thí nghiệm yêu cầu stdout deterministic, chạy **không bật logs** trước, rồi mới chạy lượt diagnostics.

Một log dạng ví dụ:

```text
[0.123s][info][class,load] Anchor source: file:/.../
```

là **dòng minh họa**, không phải kết quả đã đo hay bảo đảm format tương ứng mọi JDK vendors. Uptime 0.123s không tự diễn tả khoảng thời gian của `Anchor.<clinit>`.

## 17. Loading với identity của class loader

Runtime class identity được xác định bởi **binary name cộng defining loader**. Nếu loader A và loader B cùng định nghĩa `example.Widget` từ bytes riêng, hai runtime classes khác nhau có thể có:

- Static fields và initialization states độc lập.
- Cast/`instanceof` không tương thích dù tên binary giống nhau.
- `Class<?>` objects không bằng nhau.
- Different class-init events theo loader riêng.
- `LinkageError` nếu loading constraints hoặc binary contracts bị vi phạm.

Nhưng nếu loaders A và B **delegation** cho cùng parent định nghĩa Widget, kết quả có thể chính là **cùng runtime class**. Không khẳng định cứ có hai `ClassLoader` instances thì mỗi cái phải định nghĩa bản sao class.

Đối với bootstrap-defined `java.lang.String`, Java API thường trả `null` từ `Class.getClassLoader()` để biểu diễn bootstrap loader. `null` ở đây không có nghĩa class không được load.

## 18. Thực nghiệm initialization failures cần cẩn thận

Các lỗi liên quan class thường bị trộn:

| Lỗi | Gợi ý thường gặp |
| --- | --- |
| `ClassNotFoundException` | API class lookup không tìm binary name |
| `NoClassDefFoundError` | Runtime dependency không khả dụng **hoặc class đã init thất bại** |
| `ClassFormatError` | Class bytes không hợp lệ ở mức format |
| `VerifyError` | Class file/bytecode vi phạm verification constraints |
| `NoSuchMethodError` | Binary caller tham chiếu method không còn đúng signature |
| `NoSuchFieldError` | Binary caller tham chiếu field không còn trong runtime class |
| `IllegalAccessError` | Access constraints vi phạm ở runtime |
| `IncompatibleClassChangeError` | Class/interface/method/field binary shape không tương thích |
| `ExceptionInInitializerError` | Exception không phải `Error` phát sinh từ class initialization |
| `ClassCircularityError` | Inheritance/type derivation circularity không hợp lệ |

**Phân biệt trước khi sửa:** `ExceptionInInitializerError` có cause chain từ static initializer, không phải bằng chứng “JVM bug”. `NoSuchMethodError` có thể xảy ra **muộn** khi một symbolic reference ít dùng được resolve; class caller có thể loaded từ lâu.

Kiểm tra classpath/module path, dependency binary versions và stack trace **trước khi** tăng heap, tắt verifier hoặc đổi loader. Đừng tự dùng những flags vô hiệu hóa verification để che lỗi binary.

## 19. Nếu code “chạy trước” theo wall-clock thì có nghĩa JVM đã resolve trước không?

**Không.** Các câu lệnh Java có Java-level ordering/side effects theo JLS, nhưng **thời điểm internal loading/linking** có các lựa chọn. Các type references có thể được resolve trước khi branch được chạy hoặc trì hoãn tới lúc dùng.

Ví dụ câu “`Class.forName(false)` không in static block” chứng minh **không bắt buộc initialization**, nhưng không chứng minh class chưa được verified/prepared. Ngược lại, một class-load log xuất hiện sớm không chứng minh toàn bộ constant pool đã resolved.

Một phép đo `System.nanoTime` quanh `Anchor.VALUE` có thể gồm cả class-init work, code cache/JIT, runtime libraries hoặc OS scheduling; không dùng nó để suy thời gian riêng của loading, verification, preparation hay resolution mà không có instrumentation phù hợp.

## 20. Hai kiểu cyclic behavior khác nhau

**Cùng thread:** `CycleA/CycleB` là circular use trong static initializers. JVM cho phép current thread gặp recursive initialization request, nên B nhìn thấy `A.a` mặc định 0 rồi hoàn tất.

**Khác threads:** nếu thread T1 đang init A, T2 init B, mà static initializers của A/B mỗi bên chờ tiến triển của type bên kia, có thể xuất hiện **class initialization deadlock**. Đây là lỗi concurrency/lifecycle, không phải một ví dụ hợp lệ để đưa vào chương trình học nếu không có timeout/khả năng dừng an toàn.

Tránh static initializer:

- Tạo worker thread rồi `join()` worker khi worker lại dùng chính class đang init.
- Gọi remote service/DB mà có thể block vô hạn.
- Công bố `this`/state chưa fully initialized.
- Phụ thuộc vòng tới static fields chưa ready.
- Nuốt exception mà khiến class init thành công trong trạng thái hỏng.

Nếu startup treo, cân nhắc `jcmd <PID> Thread.print -l` hoặc thread dump thích hợp, kết hợp class initialization logs; đừng đổ lỗi tự động cho GC.

## 21. Bảng tổng hợp dự đoán–kiểm chứng

| Hành động | Class/interface khởi tạo do hành động đó | Thí nghiệm |
| --- | --- | --- |
| `Anchor.MAGIC` | Không yêu cầu init Anchor chỉ vì constant | 1 |
| `Anchor.class` | Không yêu cầu init Anchor chỉ vì class literal | 1 |
| `Class.forName("Anchor",false,loader)` | Không yêu cầu init Anchor | 1 |
| `Anchor.VALUE` | Init Anchor | 1 |
| `new ChildL()` | Init BaseL trước ChildL; rồi constructors | 1 |
| `SubCounter.VALUE` | Init SuperCounter, không cần SubCounter | 2 |
| `SubCounter.OWN` | Init SubCounter khi chưa init | 2 |
| `loader.loadClass("ReflectTarget")` | Load, không yêu cầu init | 3 |
| `Field.get(null)` với static field | Init declaring class | 3 |
| `LeafInterface.LEAF` | Init LeafInterface, chưa tự init RootInterface | 4 |
| `new DefaultChild()` | Init DefaultParent, HasDefaultMethod, DefaultChild | 5 |
| `CycleA.a` lần đầu | CycleB thấy A.a mặc định 0 khi recursive request | 6 |
| Hai threads đọc `SlowClass.VALUE` | Một initializer, cả hai nhận 7 sau success | 7 |
| `BrokenClass.VALUE` lần 1 | Init fail, `ExceptionInInitializerError` | 8 |
| `BrokenClass.VALUE` lần 2 | Không retry, `NoClassDefFoundError` | 8 |
| `new ArrayTarget[2]` | Không tự init ArrayTarget | 9 |
| `new ArrayTarget()` | Init ArrayTarget rồi constructor | 9 |

Đây là **bảng Java-level semantics**. Runtime có thể load/link classes sớm hơn; bảng không quy định precise loading timestamps hoặc thứ tự tất cả symbolic resolutions.

## 22. Checklist tự dự đoán một đoạn mã nhiều lớp

Trước khi chạy code lạ:

1. Đâu là **initial class** chứa main?
2. Các type nào được nhắc tới bởi class literals, field/method descriptors, casts, arrays?
3. Type nào có **static blocks** hay static field initializers?
4. Field nào thật sự là **constant variable**?
5. Với `obj.field`/`Sub.staticField`, ai là **declaring type**?
6. Dòng nào chứa `new`, `getstatic`, `putstatic`, `invokestatic` hay reflection triggering init?
7. Với subclass, superclass nào chưa initialized?
8. Interface nào khai báo default method liên quan đến class initialization?
9. Một interface initialized có superinterface không bị init tự động không?
10. Có vòng static initializer khiến giá trị vẫn bằng default không?
11. Có hai threads cùng yêu cầu class initialization không?
12. Có initializer ném exception làm class chuyển trạng thái erroneous không?
13. Có nhiều defining class loaders khiến cùng name nhưng khác runtime type không?
14. Giai đoạn nào là **không quan sát trực tiếp** bằng stdout?
15. Thí nghiệm có thật sự bắt đầu trong JVM mới hay đã reuse initialized classes?

Sau khi viết dự đoán, mới chạy test. Nếu khác, kiểm tra **nghĩa Java** và hoạt động thực tế trước khi viện dẫn tối ưu JIT hoặc CPU cache.

## 23. Các hiểu lầm thường gặp

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “Đã load class thì static initializer chạy ngay” | Load khác init |
| “Linking chỉ có một bước resolution” | Verification, preparation, resolution là các hoạt động khác nhau |
| “Preparation chạy mọi `static = expression`” | Preparation tạo default values; executable initializer thuộc init |
| “Mọi reference resolved trước `<clinit>`” | JVM có quyền resolution lazy/eager có giới hạn |
| “`static final` nào cũng không init” | Chỉ constant variables có exception đặc thù |
| “`Widget.class` có nghĩa new Widget” | Class literal không tạo Widget instance |
| “`Class.forName(false)` vẫn bắt buộc chạy static block” | False không yêu cầu init |
| “`loadClass` và `forName(true)` y hệt” | Loading khác initialization |
| “`Sub.field` luôn init Sub” | Tùy declaring type của resolved field |
| “Interface init tự init mọi superinterface” | Không; có quy tắc riêng cho class với default-method interfaces |
| “Subclass constructor chạy trước class initializer” | Class init cần thiết xong trước constructors |
| “Vòng static fields chắc gây ClassCircularityError” | Circular static initialization có thể đọc default values hợp lệ |
| “Hai threads khởi tạo cùng class hai lần” | JVM có per-class initialization synchronization |
| “Initializer fail sẽ tự chạy lại lần sau” | Class erroneous cho cùng runtime type |
| “NoClassDefFoundError chỉ có nghĩa thiếu file” | Có thể previous initialization failure |
| “`new Foo[5]` gọi Foo constructor 5 lần” | Chỉ tạo array với null components |
| “Cùng binary name là cùng runtime class” | Defining class loader cũng là identity |
| “javap cho biết resolution đã diễn ra lúc mấy giờ” | Class-file bytecode không phải runtime trace |
| “Log class load chứng minh static blocks đã chạy” | Nó chỉ nói về loading |
| “Một JVM cũ cho kết quả khác nên quy tắc JLS sai” | Static classes có thể đã initialized từ trước |

## 24. Câu hỏi tự kiểm tra

**1.** `static final int X=10` có bắt buộc init declaring class khi in `X` không? — **Không**, đó là compile-time constant variable.

**2.** `static final Integer X=10` có như trường hợp trên không? — **Không**, boxed reference không là constant variable.

**3.** `MyClass.class` có chạy `MyClass.<clinit>` không? — **Không chỉ vì literal**.

**4.** `Class.forName(name,false,loader)` khác `true` ở đâu? — `false` không yêu cầu initialization; `true` yêu cầu khi chưa init.

**5.** `Child.PARENT_FIELD` có luôn init Child không? — **Không**, declaring parent là type liên quan cho nonconstant static field.

**6.** Một class mới được `new` có static block và constructor in log, cái nào trước? — Initialization của class/superclass trước, constructor sau.

**7.** Interface `Leaf extends Root` bị init có bắt buộc init Root không? — **Không**.

**8.** Khi class implements default-method interface bị init, interface có thể init theo quy tắc không? — **Có**.

**9.** Vì sao B thấy `A.a==0` trong circular initializer? — A đang init cùng thread nhưng assignment chưa chạy xong; default value vẫn tồn tại.

**10.** Hai threads cùng đọc nonconstant static field làm initializer chạy bao nhiêu lần? — **Một lần thành công cho cùng runtime class**.

**11.** `<clinit>` ném `IllegalStateException`, lần đầu caller gặp gì? — Thường `ExceptionInInitializerError`.

**12.** Lần sau active use một class erroneous gặp gì? — `NoClassDefFoundError`.

**13.** `javap` có hiện timestamp một constant-pool reference được runtime resolve không? — **Không**.

**14.** Sao phải chạy process mới cho mỗi thí nghiệm? — Static initializer không tự chạy lại cho class đã initialized.

**15.** `new Foo[3]` khởi tạo Foo class và ba objects không? — **Không**, tạo một array với ba references null.

**16.** Muốn biết JVM 17 của mình hỗ trợ log tags gì, dùng gì? — `java -Xlog:help`.

## 25. Nguồn chuẩn

- [JLS Java SE 17 `12.1` — Java Virtual Machine Startup](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.1): initial class, link, init trước main.
- [JLS Java SE 17 `12.2` — Loading](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2): quá trình loading và class loaders.
- [JLS Java SE 17 `12.3` — Linking](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.3): verify/prepare/resolve và tính linh hoạt timing.
- [JLS Java SE 17 `12.4.1` — When Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1): active-use conditions, declaring static members, interfaces/default methods.
- [JLS Java SE 17 `12.4.2` — Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2): recursive initialization và concurrency.
- [JLS Java SE 17 `4.12.4` — Constant Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4): điều kiện constant variable.
- [JLS Java SE 17 `15.29` — Constant Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.29): compile-time constant expressions.
- [JVMS Java SE 17 `5.3` — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3): runtime class identity.
- [JVMS Java SE 17 `5.4` — Linking](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4): verification, preparation và resolution.
- [JVMS Java SE 17 `5.5` — Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5): `new`/`getstatic`/`putstatic`/`invokestatic`, initialization locks/states, errors.
- [JVMS Java SE 17 `2.9.2` — Class Initialization Method](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.9.2): `<clinit>`.
- [JVMS Java SE 17 `4.7.2` — ConstantValue](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.2): constant fields.
- [Java SE 17 — `Class.forName` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)): `initialize` flag.
- [Java SE 17 — `ClassLoader.loadClass` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#loadClass(java.lang.String)): nạp không tự init.
- [Java SE 17 — `javap` tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): đọc class file/constant pool/code.
- [Java SE 17 — `java` launcher](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): Unified Logging `-Xlog`.

**Kết luận:** Để dự đoán đúng kết quả nhiều lớp, luôn xác định **active use**, **declaring class của static member**, **constant variable**, **superclass/default-method interfaces**, **class initialization state** và **class loader identity**. Loading không kéo theo initialization, linking resolution có thể sớm hoặc muộn, còn stdout static blocks chỉ xác nhận initializer code đã chạy. Hãy **dự đoán → chạy trong JVM mới → so `javap` → kiểm tra class logs**, và chỉ kết luận trong phạm vi dữ liệu thật sự quan sát được.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-experiments-004">Tạo hai bộ nạp cho cùng tên lớp và giải thích định danh kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo hai bộ nạp cho cùng tên lớp và giải thích định danh kiểu

## 1. Vấn đề cần chứng minh bằng thực nghiệm

Một class trong JVM không chỉ được nhận diện bằng tên đầy đủ như **lab.plugin.Greeter**. Với non-array class/interface, Java Virtual Machine Specification (JVMS) §5.3 xác định runtime type theo **cặp (binary name, defining class loader)**. Hai class loaders độc lập có thể đọc **cùng một file .class**, cùng binary name, cùng bytecode, nhưng tạo **hai runtime classes khác nhau** nếu mỗi loader tự định nghĩa class.

~~~text
Runtime identity = (binary name, defining ClassLoader)

("lab.plugin.Greeter", loaderA) !=
("lab.plugin.Greeter", loaderB)

Khi A và B thực sự DEFINE lớp:
  classA.getName().equals(classB.getName()) -> true
  classA == classB                         -> false
  classA.isInstance(objectB)              -> false
  classA.cast(objectB)                    -> ClassCastException
~~~

Ngược lại, **hai ClassLoader objects khác nhau chưa chắc tạo hai classes**. Nếu cả hai đều dùng parent-first delegation để nhờ **cùng parent** định nghĩa lớp, hai kết quả có thể là **chính cùng một Class object**.

Đây là cơ sở của plugin systems, ứng dụng servlet containers, hot reload, framework isolation và nhiều lỗi kiểu “một lớp không thể cast về chính tên lớp đó”. Bài này thiết kế thí nghiệm Java 17+ có đầu vào, lệnh biên dịch, stdout cụ thể và đối chứng để chứng minh cả hai tình huống.

### Mục tiêu học tập

Sau bài học, bạn có thể:

- Phân biệt **binary name, internal name, Class object, object instance, defining loader và initiating loader**.
- Tạo một file .class duy nhất và nạp nó bằng **hai URLClassLoader instances** sao cho mỗi loader tự define.
- Giải thích Class ==, Class.getName, Class.getClassLoader, isInstance, isAssignableFrom và Class.cast.
- Chứng minh **static variables và class initialization** tách biệt giữa hai runtime classes trùng tên.
- Thực hiện đối chứng parent delegation: **hai loaders, một runtime type**.
- Tạo plugin API chung ở parent, để hai implementation classes riêng ở children vẫn có thể cast về cùng interface.
- Hiểu khi nào có **ClassCastException, ClassNotFoundException, NoClassDefFoundError, LinkageError, IllegalAccessError, ServiceConfigurationError**.
- Hiểu liên hệ với runtime package identity, class-loader lifecycle, Metaspace và class unloading.
- Chẩn đoán bằng Class.getClassLoader, -Xlog:class+load, jcmd, JFR và kiểm tra classpath.
- Không nhầm **class loader isolation** với một security sandbox.

Tất cả chương trình sau dùng Java 17 public APIs và classpath/unnamed modules. Phần JVMS là quy tắc chuẩn; các thông tin về log format, class unloading, Metaspace và commands là chi tiết HotSpot version-specific. **Special Note không có yêu cầu bổ sung**.

## 2. Loading, linking, initialization và định danh kiểu

Quá trình JVM xử lý class có các bước liên quan nhưng khác nhau:

1. **Loading:** dùng loader tạo hoặc tìm runtime class từ một binary name và nguồn class representation.
2. **Verification:** kiểm tra tính hợp lệ/type safety của class file.
3. **Preparation:** cấp phát class variables và gán initial default values theo quy tắc JVMS.
4. **Resolution:** resolve symbolic references khi cần tới runtime entities.
5. **Initialization:** thực hiện static initializers và những field initialization expressions phù hợp.

Có thể JVM trì hoãn một số resolution; không nên hình dung lúc load xong chắc chắn mọi references đều đã resolve. Class.forName(name, true, loader) yêu cầu initialization, còn Class.forName(name, false, loader) không chủ động yêu cầu điều đó.

### 2.1. Binary name và internal name

Ví dụ source:

~~~java
package lab.plugin;

public final class Greeter {
}
~~~

- **Binary name:** lab.plugin.Greeter.
- **Internal name trong class file:** lab/plugin/Greeter.
- **Resource path:** lab/plugin/Greeter.class, tính tương đối với classpath root.
- **Reflection:** Class.getName() cho non-array class trả về lab.plugin.Greeter.

Không truyền tên resource path có dấu slash hoặc đuôi .class vào Class.forName(name,...), vì method yêu cầu binary name.

### 2.2. Defining loader khác initiating loader

JVMS §5.3 phân biệt:

- **Defining loader:** thực thể class loader **trực tiếp định nghĩa** class. Nó là một phần của runtime type identity.
- **Initiating loader:** loader đã khởi xướng hoặc tham gia thao tác load theo quy tắc JVMS, có thể chỉ delegate class loading.

Ví dụ:

~~~text
childA --loadClass("lab.plugin.Greeter")--> parent
childB --loadClass("lab.plugin.Greeter")--> parent

parent reads and DEFINES Greeter

childA sees parent's Class
childB sees parent's Class

Both results have defining loader = parent
and are the SAME runtime type.
~~~

Do đó in chỉ child loader object mà ứng dụng tạo ra **không đủ**. Cần kiểm tra **Class.getClassLoader()** trên Class thực sự trả về.

### 2.3. Parent-first delegation thông dụng

ClassLoader.loadClass(String, boolean) mặc định tìm theo mô hình:

~~~text
Request: loadClass(binaryName)
              |
              v
findLoadedClass(binaryName)
       | found -> return previous Class
       v
Ask parent loader (or bootstrap mechanism)
       | found -> return parent's Class
       v
findClass(binaryName) in this loader
       | found -> define/get Class
       v
ClassNotFoundException if none available
~~~

Đây là hành vi của **default ClassLoader.loadClass**, không phải luật cấm mọi child-first custom loader. Custom frameworks có thể override loading policy, nhưng phải bảo toàn type-safety constraints, synchronization và API/module packages.

### 2.4. Bootstrap, platform và system loader

Trên Java 17:

- **Bootstrap loader** xử lý nhiều lớp Java cốt lõi; khi Class.getClassLoader() trả null thường là bootstrap-defined class.
- **Platform loader** xử lý platform classes/modules phù hợp.
- **System/application loader** thường dùng application classpath/module setup.
- User-defined URLClassLoader có thể có parent được truyền khi tạo.

Khi truyền **parent=null** cho URLClassLoader, lookup vẫn có thể tải bootstrap classes, bao gồm Object và String. Không có nghĩa loader được quyền nhìn toàn bộ modules do platform loader định nghĩa. Để thí nghiệm đơn giản, plugin Greeter chỉ dùng **java.base**.

## 3. Chuẩn bị môi trường và cấu trúc thư mục

Yêu cầu:

- Có **JDK 17+**, không chỉ JRE không chứa javac.
- Java và javac cùng một bộ công cụ phù hợp.
- Có quyền tạo directories và class files.
- Lệnh chạy từ thư mục project root.

Kiểm tra:

~~~bash
java -version
javac -version
~~~

Cấu trúc dự kiến:

~~~text
classloader-lab/
├── src/
│   └── lab/
│       └── plugin/
│           └── Greeter.java
├── LoaderIdentityDemo.java
├── ParentDelegationDemo.java
├── HostContractDemo.java
├── out/
│   ├── host/
│   │   └── LoaderIdentityDemo.class
│   ├── plugin/
│   │   └── lab/
│   │       └── plugin/
│   │           └── Greeter.class
│   └── isolated-plugin/
└── ...
~~~

Có thể tạo bằng:

~~~bash
mkdir -p src/lab/plugin out/host out/plugin out/isolated-plugin
~~~

Trong Windows PowerShell, dùng **New-Item -ItemType Directory -Force** hoặc tạo directories trong IDE, và viết mỗi lệnh javac trên một dòng thay vì dùng Bash backslash line continuation.

**Quan trọng:** Chúng ta **không** đưa Greeter.class vào out/host ở thí nghiệm thứ nhất. Driver host không import lab.plugin.Greeter, để tránh việc system loader vô tình trở thành defining loader của plugin class.

## 4. Mã plugin: Greeter.java

File **src/lab/plugin/Greeter.java**:

~~~java
package lab.plugin;

public final class Greeter {
    private static int instancesCreated;
    private final String tag;

    public Greeter(String tag) {
        this.tag = tag;
        instancesCreated++;
    }

    public String greeting() {
        return "hello " + tag;
    }

    public static int instancesCreated() {
        return instancesCreated;
    }
}
~~~

Ý nghĩa các fields:

- **tag** thuộc từng object instance.
- **instancesCreated** thuộc runtime class do một defining loader tạo ra.
- Nếu có hai runtime classes khác nhau cùng Greeter binary name, mỗi class có **static field riêng** và class initialization state riêng.
- Demo chỉ có hai constructor invocations tuần tự trên hai class definitions, nên không vướng data race trong phép increment này.

Biên dịch plugin riêng:

~~~bash
javac --release 17 -encoding UTF-8 -d out/plugin src/lab/plugin/Greeter.java
~~~

Kiểm tra kết quả nằm ở:

~~~text
out/plugin/lab/plugin/Greeter.class
~~~

Không copy class file này sang out/host và không thêm out/plugin vào **application classpath** khi chạy test isolation bên dưới.

## 5. Chương trình chủ: LoaderIdentityDemo.java

Tạo file **LoaderIdentityDemo.java** tại project root:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class LoaderIdentityDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: LoaderIdentityDemo <plugin-directory>");
        }

        URL pluginUrl = Path.of(args[0])
                .toAbsolutePath().toUri().toURL();
        URL[] locations = {pluginUrl};

        try (URLClassLoader loaderA =
                     new URLClassLoader(locations, null);
             URLClassLoader loaderB =
                     new URLClassLoader(locations, null)) {

            String name = "lab.plugin.Greeter";
            Class<?> classA =
                    Class.forName(name, true, loaderA);
            Class<?> classB =
                    Class.forName(name, true, loaderB);

            Object objectA = classA
                    .getConstructor(String.class)
                    .newInstance("left");
            Object objectB = classB
                    .getConstructor(String.class)
                    .newInstance("right");

            System.out.println("sameBinaryName="
                    + classA.getName().equals(classB.getName()));
            System.out.println("sameClassObject="
                    + (classA == classB));
            System.out.println("differentDefiningLoaders="
                    + (classA.getClassLoader()
                    != classB.getClassLoader()));

            System.out.println("AisInstanceOfB="
                    + classA.isInstance(objectB));
            System.out.println("BisInstanceOfA="
                    + classB.isInstance(objectA));

            System.out.println("greetingA="
                    + classA.getMethod("greeting").invoke(objectA));
            System.out.println("greetingB="
                    + classB.getMethod("greeting").invoke(objectB));

            System.out.println("staticCountA="
                    + classA.getMethod("instancesCreated")
                            .invoke(null));
            System.out.println("staticCountB="
                    + classB.getMethod("instancesCreated")
                            .invoke(null));

            try {
                classA.cast(objectB);
                System.out.println("crossCast=unexpected-success");
            } catch (ClassCastException expected) {
                System.out.println("crossCast=ClassCastException");
            }

            Class<?> again =
                    Class.forName(name, false, loaderA);
            System.out.println("repeatLookupSameClass="
                    + (again == classA));
        }
    }
}
~~~

### 5.1. Lệnh biên dịch/chạy

~~~bash
javac --release 17 -encoding UTF-8 -d out/host LoaderIdentityDemo.java
java -cp out/host LoaderIdentityDemo out/plugin
~~~

**Chỉ out/host xuất hiện trong java -cp.** Chương trình có thể nhìn thấy plugin class khi và chỉ khi hai URLClassLoader đọc đúng plugin URL. Nếu plugin path sai sẽ có ClassNotFoundException; cần sửa đường dẫn, không thay semantics.

### 5.2. Stdout chính xác

Trong setup Java17 đã mô tả, không có code phụ hoặc flags in extra logs:

~~~text
sameBinaryName=true
sameClassObject=false
differentDefiningLoaders=true
AisInstanceOfB=false
BisInstanceOfA=false
greetingA=hello left
greetingB=hello right
staticCountA=1
staticCountB=1
crossCast=ClassCastException
repeatLookupSameClass=true
~~~

### 5.3. Giải thích từng phép đo

| Dòng | Kết luận |
| --- | --- |
| sameBinaryName=true | Hai classes có cùng binary name lab.plugin.Greeter |
| sameClassObject=false | Class A/B là hai runtime types khác nhau |
| differentDefiningLoaders=true | A và B được define bởi hai URLClassLoader objects khác nhau |
| AisInstanceOfB=false | Object B không thuộc runtime class A |
| BisInstanceOfA=false | Object A không thuộc runtime class B |
| greetingA/B | Cả hai thực thi cùng implementation bytecode và có object state khác |
| staticCountA/B=1 | Mỗi runtime class quản lý static field riêng |
| crossCast=ClassCastException | Cast object từ classB thành classA không hợp lệ |
| repeatLookupSameClass=true | Lookup lại trong cùng loaderA không define thêm class |

Đặc biệt, **Class.getName() chỉ so text**, còn **Class object equality** phản ánh runtime class identity. Hai Class objects đều có tên lab.plugin.Greeter nhưng không bằng nhau.

### 5.4. Vì sao dùng Reflection?

Host không compile-time import Greeter, tránh class resolution qua application class loader. Reflection tạo instances:

- Class<?> từ **Class.forName**.
- Object từ **getConstructor(...).newInstance(...)**.
- Gọi public instance methods bằng **getMethod(...).invoke(instance)**.
- Kiểm tra compatibility bằng **isInstance** và **cast**.

Reflection **không miễn** Java type safety. Class.cast vẫn kiểm tra đúng runtime types và ném ClassCastException nếu mismatch.

### 5.5. Hai URLClassLoader khác nhau thực sự cùng đọc một file?

Cả loaderA và loaderB nhận **cùng URL[] chứa thư mục out/plugin**. Greeter class bytes được tìm từ **cùng path**. Đây không phải hai phiên bản source code hoặc hai tên class khác nhau; chính **defining loader identity** làm hai runtime classes khác nhau.

Không cần copy file .class thành hai bản để thí nghiệm thành công. Cũng không cần thay class bytecode, hash hay class file version.

## 6. So sánh object identity, class identity và loader identity

Ba mối quan hệ cần tách:

1. **Object identity:** objectA == objectB? Hai instance khác nhau nên false.
2. **Class identity:** objectA.getClass() == objectB.getClass()? Trong demo là false.
3. **Loader identity:** objectA.getClass().getClassLoader() == objectB.getClass().getClassLoader()? Trong demo là false.

Một Class object có thể có vô số instances. Hai object instances khác nhau **không tự chứng minh class khác nhau**. Ví dụ:

~~~java
String first = new String("one");
String second = new String("two");

System.out.println(first == second); // false
System.out.println(first.getClass()
        == second.getClass());        // true
~~~

Đây là đoạn minh họa trong một method, không phải file độc lập; hai String objects khác nhau nhưng cùng runtime class String.

Trong demo URLClassLoader, Class A/B khác nhau **vì loader definitions**, không phải chỉ do tạo hai objects.

### 6.1. Các API so sánh

- **Class.getName()**: tên dạng String, không đủ làm khóa định danh duy nhất khi có plugin isolation.
- **Class.equals** và **==**: cùng Class object hay không.
- **Class.getClassLoader()**: defining loader của class; trả null nếu bootstrap.
- **Class.isInstance(value)**: kiểm tra runtime assignment/type hierarchy.
- **Class.isAssignableFrom(otherClass)**: otherClass có thể gán cho this Class theo rules không.
- **Class.cast(value)**: cast có kiểm tra hoặc ClassCastException.
- **object.getClass()**: runtime Class object thực của instance.

Nếu cùng package/name nhưng khác loader, isInstance và cast có thể thất bại. Tuy nhiên nếu cả hai classes implement **một parent-shared interface**, objects vẫn assignable tới common interface đó.

## 7. Tại sao staticCount mỗi bên là 1, không phải 2?

Class variables là static state gắn với một runtime class. Với hai definitions:

~~~text
Greeter_A = ("lab.plugin.Greeter", loaderA)
  private static instancesCreated = 1
  one objectA

Greeter_B = ("lab.plugin.Greeter", loaderB)
  private static instancesCreated = 1
  one objectB

Cùng binary name; không cùng static storage.
~~~

Nếu tạo thêm một object từ **classA**:

~~~java
// Fragment đặt sau objectA/objectB trong main của LoaderIdentityDemo:
Object secondA = classA
        .getConstructor(String.class).newInstance("again");
System.out.println("staticCountAAfterSecond="
        + classA.getMethod("instancesCreated").invoke(null));
~~~

**Stdout bổ sung của fragment:**

~~~text
staticCountAAfterSecond=2
~~~

ClassB vẫn giữ count 1 vì không tạo thêm objectB. Đây chỉ là **fragment tùy chọn**, không nằm trong stdout chuẩn ở §5.2.

### 7.1. Initialization là riêng theo Class object

Nếu thêm vào Greeter.java:

~~~java
static {
    System.out.println("Greeter initialized");
}
~~~

và biên dịch lại, khi driver gọi **Class.forName(name, true, loaderA)** rồi loaderB, static initializer của Greeter_A và Greeter_B có thể chạy **hai lần tổng cộng** (mỗi runtime Class được initialize một lần). Đây là **thay đổi thử nghiệm**, nên stdout sẽ có thêm hai dòng; không còn đúng với stdout gốc.

**Không suy Class.forName gọi 5 lần nghĩa static initializer chạy 5 lần.** Cùng runtime class chỉ được initialize một lần theo các quy tắc initialization.

## 8. Thí nghiệm đối chứng: hai child loaders cùng nhận Class từ parent

Nếu hai child loaders parent-first delegate cho **cùng system loader** và system loader có thể load Greeter, kết quả **khác hoàn toàn** với thí nghiệm một.

### 8.1. ParentDelegationDemo.java

File **ParentDelegationDemo.java**:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class ParentDelegationDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: ParentDelegationDemo <plugin-directory>");
        }

        URL url = Path.of(args[0])
                .toAbsolutePath().toUri().toURL();
        ClassLoader parent = ClassLoader.getSystemClassLoader();

        try (URLClassLoader childA =
                     new URLClassLoader(new URL[]{url}, parent);
             URLClassLoader childB =
                     new URLClassLoader(new URL[]{url}, parent)) {

            Class<?> a = Class.forName(
                    "lab.plugin.Greeter", true, childA);
            Class<?> b = Class.forName(
                    "lab.plugin.Greeter", true, childB);

            System.out.println("differentChildObjects="
                    + (childA != childB));
            System.out.println("sameRuntimeClass="
                    + (a == b));
            System.out.println("definedBySystemLoader="
                    + (a.getClassLoader() == parent));
            System.out.println("definedByChildA="
                    + (a.getClassLoader() == childA));
        }
    }
}
~~~

Biên dịch:

~~~bash
javac --release 17 -encoding UTF-8 -d out/host ParentDelegationDemo.java
~~~

Chạy **trong JVM process mới** và cho system loader nhìn thấy plugin:

~~~bash
# Linux/macOS:
java -cp out/host:out/plugin ParentDelegationDemo out/plugin
~~~

Với Windows PowerShell, classpath separator là **dấu chấm phẩy**:

~~~powershell
java -cp "out/host;out/plugin" ParentDelegationDemo out/plugin
~~~

### 8.2. Stdout đối chứng

~~~text
differentChildObjects=true
sameRuntimeClass=true
definedBySystemLoader=true
definedByChildA=false
~~~

Giải thích:

- childA != childB là true, nhưng điều đó chỉ nói về **ClassLoader objects**.
- System loader có out/plugin trong classpath và định nghĩa lab.plugin.Greeter.
- Mỗi child hỏi parent trước theo URLClassLoader default delegation.
- Cả hai trả về Class do system loader định nghĩa; vì thế runtime identity **giống nhau**.
- Đây là phản ví dụ của kết luận sai “cứ tạo hai loaders là chắc chắn có hai types”.

### 8.3. Biến điều kiện quan trọng

**Lần một:** parent=null, chỉ out/host trên classpath, Greeter từ plugin URL → two types.

**Lần hai:** parent=system, out/plugin có trong system classpath, children delegate → one type.

Nếu vô tình thêm out/plugin vào app classpath ở lần một nhưng vẫn để parent=null, result lần một vẫn có thể là two types, bởi parent=null chỉ delegate bootstrap (không delegate system loader). Đừng nhầm “class có trên system classpath” với “mọi custom loader đều tự hỏi system loader”.

Ngược lại, nếu demo lần hai system classpath **thiếu** out/plugin, parent không tìm thấy Greeter và hai children có thể tự define riêng → kết quả đổi. Vì thế phải chép đúng câu lệnh khi tái lập.

## 9. Shared API: hai plugin implementation classes nhưng một interface chung

Một kiến trúc plugin hữu ích:

~~~text
                   host/system ClassLoader
                             |
                 shared.Contract interface
                    /                \
                   /                  \
           child loader A        child loader B
                  |                   |
          plugins.Implementor_A  plugins.Implementor_B

Implementor_A != Implementor_B
But both implement SAME shared.Contract
Host casts to shared.Contract successfully.
~~~

Tư tưởng:

- **API type** mà host và plugins cần trao đổi nên được parent/common loader định nghĩa.
- **Implementation type** có thể do từng plugin child loader riêng định nghĩa.
- Nhờ chia sẻ API runtime identity, host không cần cast một plugin implementation thành implementation của loader khác.

### 9.1. Shared interface

File **src/shared/Contract.java**:

~~~java
package shared;

public interface Contract {
    String message();
}
~~~

### 9.2. Plugin implementation

File **src/plugins/Implementor.java**:

~~~java
package plugins;

import shared.Contract;

public final class Implementor implements Contract {
    @Override
    public String message() {
        return "from-plugin";
    }
}
~~~

### 9.3. Driver: HostContractDemo.java

File **HostContractDemo.java**:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import shared.Contract;

public class HostContractDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: HostContractDemo <plugin-directory>");
        }

        URL url = Path.of(args[0])
                .toAbsolutePath().toUri().toURL();
        ClassLoader parent = ClassLoader.getSystemClassLoader();

        try (URLClassLoader childA =
                     new URLClassLoader(new URL[]{url}, parent);
             URLClassLoader childB =
                     new URLClassLoader(new URL[]{url}, parent)) {

            Class<?> a = Class.forName(
                    "plugins.Implementor", true, childA);
            Class<?> b = Class.forName(
                    "plugins.Implementor", true, childB);

            Contract serviceA = (Contract) a
                    .getConstructor().newInstance();
            Contract serviceB = (Contract) b
                    .getConstructor().newInstance();

            System.out.println("sameImplClass=" + (a == b));
            System.out.println("sameApiClass="
                    + (a.getInterfaces()[0]
                    == b.getInterfaces()[0]));
            System.out.println("apiFromHost="
                    + (a.getInterfaces()[0] == Contract.class));
            System.out.println("bothAssignable="
                    + (serviceA instanceof Contract
                    && serviceB instanceof Contract));
            System.out.println("messages="
                    + serviceA.message() + ","
                    + serviceB.message());
        }
    }
}
~~~

### 9.4. Biên dịch đúng classpath layers

~~~bash
mkdir -p src/shared src/plugins out/host out/isolated-plugin

javac --release 17 -encoding UTF-8 -d out/host \
  src/shared/Contract.java HostContractDemo.java

javac --release 17 -encoding UTF-8 \
  -cp out/host \
  -d out/isolated-plugin src/plugins/Implementor.java

java -cp out/host HostContractDemo out/isolated-plugin
~~~

**Không** đưa out/isolated-plugin vào host classpath. Compile plugin with -cp out/host chỉ để compiler biết shared API, không sao chép Contract.class vào child output.

### 9.5. Stdout chính xác

~~~text
sameImplClass=false
sameApiClass=true
apiFromHost=true
bothAssignable=true
messages=from-plugin,from-plugin
~~~

Giải thích:

1. System loader không có plugins.Implementor nên childA và childB đều tự define bản riêng.
2. Mỗi plugin implementation trong cùng source khai báo implements shared.Contract.
3. Khi plugin classes resolve interface, children delegate shared.Contract lên system loader.
4. Cả hai interface entries cùng trỏ **host Contract.class**.
5. Host cast hai plugin objects về Contract thành công và gọi đúng methods.

**Kiểu implementation vẫn khác nhau.** Dùng được cùng interface không biến plugin classes thành một runtime class.

## 10. Sai lầm khi mỗi plugin đóng gói riêng shared API

Giả sử plugin bundle có cả:

~~~text
plugins/Implementor.class
shared/Contract.class   <-- duplicate API
~~~

Nếu cấu hình **child-first** khiến plugin loader định nghĩa **shared.Contract** thay vì delegate parent, plugin implementation của nó implements **Contract_B**, còn host cast sang **Contract_H** do host loader định nghĩa.

~~~text
Host:
  shared.Contract_H  defining loader host

Plugin:
  shared.Contract_B defining loader child

Names match:
  Contract_H.getName() == Contract_B.getName()

Types not equal:
  Contract_H != Contract_B
~~~

Hệ quả: host cast có thể ném ClassCastException; Java ServiceLoader cũng có thể báo service provider không phải subtype của service type đang được request.

Khắc phục:

1. Xác định những shared API packages và DTO contracts.
2. Dùng dependency packaging chính xác, ví dụ compileOnly/provided theo framework để tránh bundle duplicate API.
3. Delegate các shared API packages về common parent.
4. In Class.getClassLoader() của Contract.class phía host và runtime interface của plugin.
5. Đảm bảo signature/API versions tương thích, không chỉ String name giống nhau.
6. Tránh child-first override toàn bộ JDK/host API packages không có chiến lược rõ.

**Không coi reflection hay ép cast qua Object là một bản vá type identity.** Khi dùng typed APIs, JVM vẫn kiểm tra compatibility.

## 11. Runtime package identity cũng gắn với defining loader

Source package:

~~~java
package lab.plugin;
~~~

không đủ để kết luận hai classes cùng runtime package nếu loaders khác nhau.

Theo JVMS, runtime package quan hệ dựa trên package name và defining loader; ở Java 9+ còn có module access/readability/exports/opens phải xét khi liên quan.

Hệ quả:

- Hai classes cùng package source name do loader khác define **không mặc nhiên được dùng package-private members của nhau**.
- Protected/access checks có thể dẫn đến IllegalAccessError hoặc reflective access errors tùy path.
- Module boundaries vẫn có hiệu lực; Loader isolation không bỏ qua JPMS rules.
- Nestmates cũng chịu runtime package/member access constraints.

**Không kết luận** hai classes có cùng textual package là có quyền truy cập package-private “như ở cùng JAR”.

### 11.1. Package name khác class identity

Lớp lab.plugin.Greeter và package lab.plugin là những đối tượng khái niệm khác nhau. Kiểm tra Class.getPackageName() có thể trả cùng String lab.plugin cho A và B; điều đó không làm hai runtime packages thành một nếu defining loaders khác.

## 12. Array classes và component-loader identity

JVMS §5.3.3 quy định array classes được JVM tạo theo cơ chế riêng.

- Với reference array types, defining loader gắn với defining loader của component class.
- Primitive arrays dùng bootstrap loader.
- Hai array classes có cùng tên mô tả nhưng component runtime class khác nhau sẽ là **hai array types khác nhau**.

Chèn fragment này vào **bên trong try của LoaderIdentityDemo**, sau khi đã có classA và classB:

~~~java
Class<?> arrayA = java.lang.reflect.Array
        .newInstance(classA, 0).getClass();
Class<?> arrayB = java.lang.reflect.Array
        .newInstance(classB, 0).getClass();

System.out.println("sameArrayClass=" + (arrayA == arrayB));
System.out.println("sameArrayName="
        + arrayA.getName().equals(arrayB.getName()));
~~~

**Stdout bổ sung của fragment:**

~~~text
sameArrayClass=false
sameArrayName=true
~~~

Các tên reflected array có thể ở dạng **[Llab.plugin.Greeter;**. Đừng nhầm array descriptor với binary name của Greeter non-array class.

Cả hai arrays vẫn có Object, Cloneable và Serializable là supertypes theo Java array rules. Có common supertype **không xóa bỏ** sự khác nhau của concrete array runtime identities.

## 13. Loading constraints: không thể tùy tiện nối type namespaces

Custom loaders có thể define classes same-named trong namespaces khác nhau, nhưng JVM còn áp các **loading constraints** để bảo vệ type safety trong field/method linkage.

Ví dụ conceptual:

~~~text
Class in loaderA references method of class in loaderB:
  consume(shared.Message)

LoaderA resolves shared.Message -> Message_A
LoaderB resolves shared.Message -> Message_B

If JVM requires these signature types to be equal
but the definitions disagree:
  linking constraint violation
  potentially LinkageError
~~~

Không ép kết luận một exception cụ thể sẽ xuất hiện ở mọi framework. Lỗi được phát hiện khi các phần references được loaded/linked/resolved theo tình huống. Khi gặp LinkageError, cần xem descriptor types, defining vs initiating loaders và version skew.

### 13.1. loadClass, findClass và defineClass

- **loadClass(name):** thường kiểm tra đã load, delegate parent, sau đó mới findClass.
- **findLoadedClass(name):** tìm class mà loader được ghi nhận đã initiate trước đó.
- **findClass(name):** hook loader override để tự locate/define bytes.
- **defineClass(name, bytes,...):** protected API tạo Class<?> từ class file representation.
- **resolveClass(Class):** thực hiện linking phù hợp.
- **Class.forName(name, initialize, loader):** tiện ích gọi load và optionally initialize.

**Load lại cùng name trong một well-behaved loader** trả class đã có, không tạo new type. Nếu cố **defineClass lần nữa cùng binary name trong cùng defining loader**, JVM có thể ném **LinkageError** do duplicate definition.

### 13.2. Không định nghĩa java.* classes tùy ý

ClassLoader.defineClass có những restrictions đặc biệt với tên bắt đầu bằng **java.** và package/certificate/security constraints. Không dùng custom loader làm cách thay thế Java core APIs hoặc vượt cơ chế type/linkage safety.

## 14. Reload và class unloading trong HotSpot

Vì class identity gắn với defining loader, reload plugin thường:

~~~text
Old loader v1 -> PluginClass_v1
New loader v2 -> PluginClass_v2
Same binary name; different runtime Class identity.
~~~

Objects cũ **không tự biến** thành instances của new plugin class. Host phải dùng shared API, chuyển state qua một giao thức, hoặc recreate instances theo version-specific contracts.

### 14.1. URLClassLoader.close chỉ đóng resource handles

**URLClassLoader** implement AutoCloseable và close có thể đóng JAR/file handles. Nhưng:

~~~text
loader.close()
  != unload all classes immediately
  != reclaim all Metaspace instantly
  != invalidate every old object reference
~~~

Class unload phụ thuộc reachability, GC implementation và chính sách của JVM. Không lấy việc try-with-resources kết thúc làm bằng chứng loader đã được GC unload.

### 14.2. Những nguồn giữ loader sống

- Host static cache lưu Class<?>, Constructor, Method hoặc loader.
- Plugin objects còn referenced trong application collections.
- Plugin-created threads chưa kết thúc.
- Thread.currentThread().getContextClassLoader trỏ tới plugin loader.
- ThreadLocal entries trong long-lived pooled threads.
- Event listeners hoặc callbacks chưa unregister.
- ServiceLoader/provider caches, reflection/method-handle caches.
- Native/JNI global references.
- Active stack frames đang chạy code được loader define.

Classloader leak có thể biểu hiện qua tăng Metaspace/class counts theo thời gian. NMT Class/Metaspace giúp thấy footprint; heap dump/retention graph cần để tìm ai giữ loader reachable.

### 14.3. Biện pháp reload sạch

1. Dừng plugin services/tasks trước khi thay.
2. Cancel scheduled work và join/terminate threads do plugin quản lý.
3. Unregister listeners/metrics/hooks.
4. Dọn thread context class loader và ThreadLocal của plugin.
5. Xóa host caches giữ Class/instances.
6. Close URLs/JAR resources.
7. Đảm bảo new plugin chỉ dùng chung contracts ở parent.
8. Quan sát GC class-unloading evidence khi có.
9. So Metaspace/classloader counts qua nhiều reloads.
10. Không dựa vào System.gc() để cam kết unload tức thì.

**Mỗi file .class thường có metadata riêng theo runtime class definitions**, nên nhiều loaders có thể tăng class metadata footprint. Nhưng không dùng phép nhân file-size × số-loaders để dự đoán chính xác Metaspace.

## 15. Công cụ quan sát và debug trên HotSpot Java 17

### 15.1. In defining loader

Trong runtime có một Object:

~~~java
static void inspect(Object value) {
    Class<?> type = value.getClass();
    ClassLoader definingLoader = type.getClassLoader();

    System.out.println("className=" + type.getName());
    System.out.println("loaderType="
            + (definingLoader == null ? "bootstrap"
            : definingLoader.getClass().getName()));
}
~~~

Đây là **method fragment** để đặt trong một class, không phải file độc lập. Muốn so loader identities đáng tin, dùng reference equality **==** trong cùng JVM; đừng dùng chỉ một String từ toString của loader hay identityHashCode làm unique ID vĩnh viễn.

### 15.2. Unified logging

~~~bash
java -Xlog:help
~~~

Theo HotSpot Java17, bật class loading logs khi cần:

~~~bash
java -Xlog:class+load=info -cp out/host LoaderIdentityDemo out/plugin
~~~

Để quan sát unload events:

~~~bash
java -Xlog:class+load=info,class+unload=info \
  -cp out/host LoaderIdentityDemo out/plugin
~~~

Các logging lines xen với application stdout và khác theo version/platform; **không có expected JVM log cố định**. Không thấy class unload event trong thí nghiệm ngắn không chứng minh memory leak.

### 15.3. jcmd classloader và Metaspace

Với **JVM đang chạy**:

~~~bash
jcmd
jcmd <PID> help
jcmd <PID> VM.classloader_stats
jcmd <PID> VM.metaspace
~~~

PID là ID process thực, không phải chuỗi literal. Các commands/args phụ thuộc JDK build và attach permissions; đọc help của đúng JVM. Driver thí nghiệm chạy rất nhanh, nên phải chủ động xây một diagnostic variant sống đủ lâu nếu muốn attach, không giả định jcmd sẽ kịp đọc một process đã kết thúc.

### 15.4. JFR và NMT

- **Java Flight Recorder** có class-loading/unloading-related events tùy recording configuration.
- **NMT Class/Metaspace** có thể giúp xem native metadata commit/reserve trends.
- **Heap dump** giúp xác định references đang giữ loaders sống.

NMT không phải heap object graph profiler, còn một classloader stats snapshot không tự chứng minh leak. Cần baseline/retention analysis qua thời gian.

## 16. Các lỗi dễ gặp và cách nhận diện

| Lỗi | Điều có thể đang xảy ra | Hướng kiểm tra |
| --- | --- | --- |
| **ClassNotFoundException** | Loader không tìm thấy binary name | Correct URL, package directory, classpath, delegation |
| **NoClassDefFoundError** | VM không thể hoàn tất loading/linking referenced class, hoặc class init trước đó fail | Cause chain, dependencies, initialization |
| **ClassCastException** | Runtime object type không assignable tới target | Class equality và defining loaders |
| **LinkageError** | Duplicate class definition hoặc loading constraints conflict | Loader namespace, method/field descriptors |
| **NoSuchMethodError** | Incompatible runtime method signature/version skew | API/implementation JAR version |
| **IllegalAccessError** | Runtime visibility/access rules fail | Runtime package, modules, defining loaders |
| **ServiceConfigurationError** | Provider metadata/incompatibility, duplicated service API | META-INF/services, common API loader |
| **UnsupportedClassVersionError** | JVM không hiểu class-file major version | javac --release và runtime version |

### 16.1. ClassCastException với hai tên trông giống nhau

Khi thấy lỗi cast “Greeter cannot be cast to Greeter”, đừng khẳng định file bytecode corrupt. Trước hết in:

- Actual object.getClass().
- Target Class<?> thực.
- getName() của hai types.
- getClassLoader() của hai types.
- isAssignableFrom/isInstance, nếu target reflection object có sẵn.
- URLs/JAR locations, parent delegation policy.

Runtime message có thể chứa loader/module details, nhưng **không dựa vào exact exception message** làm output chuẩn giữa các JDK.

### 16.2. ClassNotFoundException dù file tồn tại

Kiểm tra plugin URL có trỏ **directory root**, không phải trực tiếp tệp Greeter.class. Phải có out/plugin/lab/plugin/Greeter.class. Binary name cho Class.forName là lab.plugin.Greeter, không dùng slash hay đuôi .class.

Một dependency khác không tồn tại cũng có thể làm loading/linking thất bại, dù Greeter.class có thật.

### 16.3. Parent-first “phá” mong đợi two types

Nếu loaderA/B dùng parent=system và app classpath chứa Greeter, cả hai trả parent-defined Class. **Đây là hành vi đúng** của delegation, không phải lỗi JVM. Để thấy two definitions, cô lập plugin khỏi parent hoặc dùng loader parent phù hợp với experiment.

### 16.4. Cùng tên interface nhưng host cast fail

Plugin child-first có thể tự define shared API một lần nữa. Cần đưa API vào parent/shared loader; đánh dấu dependency provided/compileOnly phù hợp với build framework. Đảm bảo version API và method descriptors không xung đột.

### 16.5. Class-loader lifecycle và Metaspace

Nếu classloader count tăng không plateau sau mỗi redeploy, đừng chỉ tăng MaxMetaspaceSize. Tìm retained loader references qua thread contexts, ThreadLocal, caches, listeners, native handles và heap snapshots.

## 17. Lưu ý về modular Java và security

Trong Java 9+, classloading còn liên quan JPMS:

- Modules và **ModuleLayer**.
- Readability, exports và opens.
- Runtime access constraints ngoài package name.
- Different module loaders và dependency relationships.

Các demos cố ý dùng classpath/unnamed modules để tập trung vào defining loader/type identity. Không suy module name sẽ thay thế hoàn toàn quy tắc binary name + defining loader.

**Classloader isolation không phải security sandbox.** Hai plugins vẫn dùng cùng process, CPU, memory, files, network privileges trong phạm vi OS/process/JVM access. SecurityManager đã deprecated for removal ở JDK17 (JEP 411); cần process/container sandbox hoặc permissions thực khi chạy untrusted code, không dựa vào việc tạo loader mới.

## 18. Checklist thực nghiệm tái lập

### Trước chạy

- [ ] Java và javac version 17+.
- [ ] Greeter.java khai báo package lab.plugin và compiled vào out/plugin.
- [ ] Driver compiled vào out/host, không static-import Greeter.
- [ ] Java -cp của demo đầu chỉ chứa out/host.
- [ ] Hai URLClassLoader riêng biệt nhận cùng thư mục out/plugin.
- [ ] Cả hai dùng parent=null trong experiment two definitions.
- [ ] Không thêm logger flags nếu muốn stdout khớp hoàn toàn.

### Kiểm chứng

- [ ] classA/classB có cùng getName nhưng không cùng Class object.
- [ ] classA.getClassLoader() và classB.getClassLoader() khác nhau.
- [ ] classA.isInstance(objectB) là false.
- [ ] Class.cast cross-loader ném ClassCastException.
- [ ] staticCountA=1 và staticCountB=1.
- [ ] Lookup lại trong cùng loader trả đúng class cũ.
- [ ] Run ParentDelegationDemo với out/plugin trong system classpath và thấy sameRuntimeClass=true.
- [ ] Shared API ở out/host, plugin Implementor chỉ ở out/isolated-plugin.
- [ ] HostContractDemo cast được hai implementations về cùng Contract.

### Sau chạy

- [ ] Lưu command lines, directory/classpath paths và JDK build.
- [ ] Nếu plugin prod khác behavior, kiểm tra parent-first/child-first policies.
- [ ] Nếu cast/linkage fails, log defining loaders và cause chain.
- [ ] Nếu reload leak, chụp classloader/metaspace/heap evidence.
- [ ] Không coi URLClassLoader.close là guarantee class unloading.
- [ ] Không dùng loader separation làm security policy.

## 19. Những nhận định sai cần tránh

1. **“Tên class quyết định duy nhất runtime type.”** Sai, có defining loader.
2. **“Cùng .class bytes thì cùng type.”** Sai khi loaders tự define riêng.
3. **“Hai loaders khác objects thì chắc hai Classes khác nhau.”** Sai vì delegation.
4. **“Initiating loader luôn là defining loader.”** Sai.
5. **“Class.getName giống nhau nên cast thành công.”** Sai.
6. **“Class.getClassLoader()==null là lỗi.”** Sai với bootstrap-defined classes.
7. **“Static fields share toàn JVM qua textual class name.”** Sai.
8. **“Class.forName chạy static initialization mỗi lần gọi.”** Sai.
9. **“Hai Class<?> objects khác nhau nghĩa Object.equals bị override.”** Không liên quan.
10. **“Reflection bỏ qua JVM type checks.”** Sai.
11. **“Hai classes trùng package string có package-private access như một.”** Sai nếu defining loader khác.
12. **“Interface từ plugin luôn cast được interface ở host nếu cùng name.”** Sai nếu duplicate interface definition.
13. **“Child loader không bao giờ trả Class của parent.”** Sai, default thường parent-first.
14. **“URLClassLoader.close giải phóng mọi metadata ngay.”** Sai.
15. **“Tăng Xmx là cách trực tiếp sửa mọi Metaspace leak.”** Sai.
16. **“ClassCastException luôn vì hai loaders.”** Sai, còn mismatch thường.
17. **“ClassNotFoundException đồng nghĩa NoClassDefFoundError.”** Sai.
18. **“Có file Greeter.class thì mọi loader phải tìm thấy.”** Sai: scope URLs/classpath.
19. **“Hai classes có thể được define lại vô hạn trong một loader cùng name.”** Sai, duplicate definition error.
20. **“JVM không có loading constraints giữa loader namespaces.”** Sai.
21. **“Primitive/reference array identities chỉ dựa name String.”** Sai với reference component loaders.
22. **“JVM logs luôn in cùng thứ tự và số dòng.”** Sai.
23. **“NMT Class tăng một lần chứng minh classloader leak.”** Sai.
24. **“URLClassLoader parent=null không dùng được java.lang.String.”** Sai.
25. **“Shared parent interface làm hai child implementations thành cùng class.”** Sai, chúng chỉ có common supertype.
26. **“Cùng JVM nên plugin isolation là bảo mật sandbox.”** Sai.
27. **“Thay child-first luôn sửa ClassCastException mà không có tác dụng phụ.”** Sai; có thể làm trùng API hơn.
28. **“System.identityHashCode(loader) không bao giờ collision.”** Không có guarantee.
29. **“Một classpath experiment áp thẳng sang named modules.”** Cần xét JPMS.
30. **“Plugin reload tự biến old instances thành new class.”** Sai, cần migrate theo contract.

## 20. Bài tập có đáp án

**Câu 1.** Runtime identity theo JVMS là gì?

**Đáp án:** Cặp (binary name, defining ClassLoader) đối với non-array class/interface.

**Câu 2.** Hai URLClassLoader khác nhau đọc cùng Greeter.class và tự define. ClassA == ClassB?

**Đáp án:** False, vì defining loaders khác nhau.

**Câu 3.** ClassA.getName().equals(ClassB.getName())?

**Đáp án:** True, nếu đều define lab.plugin.Greeter.

**Câu 4.** ClassA.isInstance(objectB) trong demo thứ nhất?

**Đáp án:** False.

**Câu 5.** ClassA.cast(objectB)?

**Đáp án:** Ném ClassCastException.

**Câu 6.** Hai static counters trong demo có giá trị nào?

**Đáp án:** 1 và 1, vì mỗi runtime class có own static fields.

**Câu 7.** Gọi Class.forName(name,false,loaderA) sau khi A đã load có tạo class mới?

**Đáp án:** Không, trả lại cùng Class A.

**Câu 8.** ParentDelegationDemo tạo hai child loaders nhưng sameRuntimeClass=true, vì sao?

**Đáp án:** Hai children delegate tới same system loader định nghĩa Greeter.

**Câu 9.** Initiating loader có thể khác defining loader khi nào?

**Đáp án:** Khi initiating loader hỏi parent, parent trực tiếp define target class.

**Câu 10.** Vì sao không import plugin implementation ở host driver của thí nghiệm thứ nhất?

**Đáp án:** Tránh host/system loader load/resolve plugin type như một compile-time dependency.

**Câu 11.** Shared Contract đặt ở đâu cho host và plugins?

**Đáp án:** Common parent loader để tất cả resolve về một runtime interface identity.

**Câu 12.** Hai Implementor classes từ children có cast về Contract được không?

**Đáp án:** Có nếu cùng implement host-defined Contract.

**Câu 13.** Nếu child-first plugin tự define Contract duplicate thì sao?

**Đáp án:** Host Contract và plugin Contract khác type, cast/ServiceLoader assignment có thể fail.

**Câu 14.** Runtime package có chỉ phụ thuộc textual package declaration không?

**Đáp án:** Không, còn defining loader và access/module rules tương ứng.

**Câu 15.** Reference arrays của Greeter_A và Greeter_B cùng runtime array Class không?

**Đáp án:** Không, component identities khác nhau.

**Câu 16.** Hai Class loaders tự define same-named class có chạy trong hai JVM riêng không?

**Đáp án:** Không, vẫn cùng JVM process.

**Câu 17.** Cố defineClass cùng binary name hai lần trong một loader thường gây gì?

**Đáp án:** LinkageError do duplicate definition.

**Câu 18.** ClassNotFoundException nghĩa chính xác gì?

**Đáp án:** Yêu cầu load class name qua loader không tìm được definition phù hợp; cần xem name, URL, parent, classpath.

**Câu 19.** ClassCastException cùng class name có thể do gì?

**Đáp án:** Two runtime types trùng binary name nhưng khác defining loaders, hoặc type mismatch khác; cần so Class identity.

**Câu 20.** Class-loader leak có thể do Thread.contextClassLoader không?

**Đáp án:** Có, TCCL references từ long-lived threads có thể giữ plugin loader reachable.

**Câu 21.** URLClassLoader.close có tức khắc free Metaspace không?

**Đáp án:** Không.

**Câu 22.** Dùng lệnh nào quan sát classloader statistics ở HotSpot JDK17?

**Đáp án:** jcmd PID VM.classloader_stats nếu được JVM hỗ trợ.

**Câu 23.** Classloader isolation có phải security sandbox cho untrusted code?

**Đáp án:** Không.

**Câu 24.** Chỉ so class names trong plugin metadata cache có thể sai gì?

**Đáp án:** Collide hai runtime Class identities từ different defining loaders.

**Câu 25.** Cách tổng quát để reload plugin mà vẫn typed interoperability?

**Đáp án:** Dùng loader mới cho implementation, giữ shared API/DTO ở common parent, dọn old loader references và migrate state qua contract rõ ràng.

## 21. Nguồn tham khảo chính thức

- **JVMS Java SE 17 §5.3 — Creation and Loading; defining và initiating loaders; type identity:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **JVMS SE 17 §5.3.2 — Loading classes with a user-defined class loader:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.2
- **JVMS SE 17 §5.3.3 — Array classes:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.3
- **JVMS SE 17 §5.3.4 — Loading constraints:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.4
- **JVMS SE 17 §5.4 — Linking:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4
- **JVMS SE 17 §5.5 — Initialization:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- **JLS Java SE 17 §12.2 — Loading and loader consistency:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2
- **Java SE 17 ClassLoader API:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 URLClassLoader API:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- **Java SE 17 Class API (cast, isInstance, isAssignableFrom):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java SE 17 java.lang.reflect.Array:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Array.html
- **Java SE 17 ServiceLoader:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- **Oracle Java 17 jcmd:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- **OpenJDK JEP 411 — Deprecate Security Manager for Removal:** https://openjdk.org/jeps/411

## 22. Kết luận

**Hai bộ nạp khác nhau có thể định nghĩa hai classes có cùng binary name từ chính cùng một file .class nhưng chúng không phải một runtime type.** Theo JVMS, định danh class/interface là **(binary name, defining loader)**, không phải chỉ một String name. Thí nghiệm LoaderIdentityDemo chứng minh: sameBinaryName=true nhưng sameClassObject=false, cross-cast thất bại và mỗi runtime class có static state riêng.

**Điểm đối chứng không thể bỏ qua:** Hai loaders khác nhau nhưng parent-first delegate tới cùng system loader có thể trả về **một Class object**. Vì vậy phải phân biệt initiating loader với defining loader và luôn kiểm tra **Class.getClassLoader()**.

Trong plugin architecture, giữ **shared APIs/interfaces** tại common parent loader, chỉ cô lập implementation classes khi cần, và xử lý reload/lifecycle/class-unloading đúng cách. ClassLoader là công cụ định danh kiểu, tổ chức code và isolation, **không phải security sandbox** và không loại bỏ type safety của JVM.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-experiments-005">Vẽ đồ thị tham chiếu để phân biệt đối tượng rác với bộ nhớ bị giữ</a>

<details>
<summary>Xem chi tiết</summary>

# Vẽ đồ thị tham chiếu để phân biệt đối tượng rác với bộ nhớ bị giữ

## 1. Mục tiêu thực nghiệm

Trong Java, đối tượng **không trở thành rác chỉ vì một biến cục bộ được gán `null`**, và đối tượng **không trở thành memory leak chỉ vì nó chiếm nhiều byte**. Muốn trả lời chính xác, cần vẽ **đồ thị tham chiếu** (object reference graph) và xét **đường đi từ GC roots**.

Một bài toán rất thường gặp:

- Hai đối tượng `A` và `B` trỏ qua lại tạo thành vòng: chúng có phải là “rác không thu được”?
- Một `HashMap` tĩnh giữ 100.000 session đã hết hạn: tại sao GC không giải phóng chúng?
- Xóa một entry khỏi cache có làm tất cả các đối tượng con có thể thu hồi ngay không?
- Một `WeakReference` còn trỏ đến đối tượng thì nghĩa là đối tượng vẫn được giữ mạnh?
- Trong heap dump, “shallow size” và “retained size” có ý nghĩa gì? Vì sao object lớn nhất chưa chắc là thủ phạm giữ RAM?
- Vì sao `System.gc()` hoặc mất một biến Java không phải bằng chứng đã có garbage collection?
- Làm thế nào dùng một đồ thị vẽ tay, một mô hình duyệt đồ thị Java 17 và một heap dump thật để kiểm chứng?

**Điểm cốt lõi:** Một đối tượng là ứng viên để reclaim theo cơ chế tracing GC khi **không còn đường tham chiếu mạnh hợp lệ từ GC roots** tới nó, xét những quy tắc reachability của JVM. **Vòng tham chiếu không phải ngoại lệ**. Ngược lại, đối tượng vẫn được giữ bởi một root path thì GC không thể tùy tiện giải phóng chỉ vì logic ứng dụng đã “không còn cần” nó.

Bài trong nhóm **Thực nghiệm JVM**. Ta sẽ có sơ đồ trước–sau, tự tay duyệt graph, các chương trình Java 17 chạy độc lập, lệnh `javac`/`java` và stdout đúng, cùng một quy trình thu heap dump và đọc **Paths to GC Roots / Dominator Tree**. Những hành vi phụ thuộc lịch GC sẽ được đánh dấu **không có stdout cố định**, thay vì dựng một kết quả giả.

---

## 2. Đồ thị tham chiếu: đỉnh, cạnh, root

Một đồ thị tham chiếu có thể được diễn giải như sau:

- **Đỉnh (node):** một đối tượng Java, hoặc một nút khái niệm biểu diễn root.
- **Cạnh có hướng (edge):** một field, một phần tử mảng hoặc một reference mà JVM có thể dùng để đi từ đối tượng/bối cảnh này tới đối tượng khác.
- **GC root:** điểm xuất phát mà bộ thu gom rác nhận là còn sống, chẳng hạn những references từ các thread stacks đang hoạt động, các JNI global references, những cấu trúc VM nội bộ và các static fields thông qua classes/classloaders còn sống; tập root cụ thể do implementation và thời điểm quyết định.
- **Reachable:** có ít nhất một đường đi theo những cạnh phù hợp từ một GC root tới node ấy.
- **Unreachable:** không có đường đi như vậy. Đối tượng có thể được GC reclaim ở thời điểm thích hợp, nhưng **không nhất thiết được thu ngay**.
- **Retained:** đối tượng hoặc đồ thị con vẫn sống do một số owner/root path giữ lại, kể cả khi nghiệp vụ không còn cần.

Sơ đồ:

```text
   GC ROOT (thread local)
            │
            ▼
        Request A ──────► Payload P

   GC ROOT (class/static state, class còn sống)
            │
            ▼
       CacheManager
            │
            ▼
          Map ─────► Session B ─────► byte[2 MiB]

      C ─────► D
      ▲       │
      └───────┘
        (vòng C-D không có đường từ root)
```

Trong sơ đồ này, `Request A`, `Payload P`, `CacheManager`, `Map`, `Session B` và mảng bytes đều **có đường đi** từ root. Hai đối tượng `C/D` chỉ trỏ lẫn nhau nhưng **không có root path**, nên có thể được xác định là rác bởi tracing collector.

### 2.1. Phân biệt “biến”, “tham chiếu”, “đối tượng”

```java
Node first = new Node();
Node second = first;
first = null;
```

Đây là **snippet**, cần khai báo class `Node`. Khi `first=null`:

- Không có thao tác trực tiếp xóa đối tượng bằng phép gán.
- `second` vẫn là một biến tham chiếu tới **cùng một object**.
- Nếu `second` còn được JVM coi là strong live reference từ thread stack, object vẫn strongly reachable.
- Nếu `second` cũng hết sống, object có thể đủ điều kiện thu hồi khi không còn root path khác.

**Không đồng nhất biến Java với object trên heap.** Một object có thể có nhiều references cùng trỏ đến nó; ngược lại một biến có thể được gán qua nhiều object identities theo thời gian.

### 2.2. Gán `null` vào field khác với gán `null` vào local

```text
root ─► owner ─► child

Gán owner.child = null:
root ─► owner       child (không còn đường này)

Gán localCopy = null:
root ─► owner ─► child
            ▲
         localCopy bị xóa nhưng owner.child vẫn còn
```

Câu “đã `null` biến rồi mà vẫn leak” cần được trả lời bằng cách **tìm các đường root khác**, thay vì dựa trên tên biến đã xóa.

---

## 3. GC roots trong thực nghiệm JVM

GC roots không chỉ là “biến `static`”. Một số bối cảnh phổ biến:

| Nhóm root/bối cảnh sống | Đường đi có thể hình thành | Lỗi diễn giải thường gặp |
| --- | --- | --- |
| Thread stack/local references còn sống | Thread → local/argument → object | Tưởng mọi local trong method luôn giữ object đến khi return; JIT có thể giảm liveness |
| Static fields của class còn sống | VM/classloader context → Class → static field → object | Tưởng static field là GC root bất biến ngay cả sau class unloading |
| JNI global references | VM/native handle → Java object | Chỉ xem Java heap owners, bỏ qua native roots |
| JNI/local native roots liên quan đang sống | Native frame/context → object | Tưởng object Java không thể bị giữ bởi native code |
| JVM-internal roots | VM structures → objects | Không phải mọi root đều là một field nhìn thấy trong source |
| Running/live threads | Threads/frames → references | Tưởng một Java `Thread` object nhất thiết giữ mọi thread-local vĩnh viễn |
| Classloaders/classes còn reachable | Loader → defined classes/static state → objects | Bỏ qua classloader leak khi undeploy plugin |
| Reference-processing/monitor/JVM state | Phụ thuộc HotSpot và pha GC | Vẽ mọi implementation root như một API Java SE bất biến |

**GC root là một khái niệm của collector/heap analysis**, không phải danh sách một bộ Java object references được `java.lang.System` public hóa thành collection có thể iterate.

### 3.1. “Static field là root” là cách nói rút gọn

Một static field được đặt trong class state; nếu class/defining loader còn sống theo quy tắc runtime, object graph qua static field có thể được giữ. Nhưng nếu cả custom classloader và classes của nó không còn reachable, VM có thể unload class và giải phóng static state tương ứng. Vì vậy:

> Đọc static field như một **đường giữ tham chiếu khi class context còn sống**, không phải một “global eternal root” không có lifecycle.

### 3.2. Local variable và liveness do JIT

Trong một source method, biến `x` vẫn nằm trong lexical scope chưa chắc object mà nó từng trỏ tới sẽ được giữ mạnh cho tới dấu `}`. JVM/JIT có thể xác định rằng reference không còn được dùng trong những phần code kế tiếp.

Trong những trường hợp có native resources/cleanup phụ thuộc liveness, `Reference.reachabilityFence(x)` bảo đảm object còn strongly reachable ít nhất đến sau lời gọi đó. Nhưng phương thức này **không yêu cầu JVM chạy GC**, không khiến object “bị thu ngay” sau đó và không thay thế thiết kế resource cleanup explicit.

Để debug heap roots, **snapshot phản ánh liveness tại thời điểm JVM thu dump**, không phải mọi biến có tên đang nằm trong IDE debugger.

---

## 4. Tracing GC khác reference counting như thế nào?

Một mô hình thu gom rác thuần reference counting sẽ gặp khó với chu trình không còn root:

```text
              (không có root bên ngoài)
                 ┌─────────┐
                 ▼         │
                A ──────► B
                ▲         │
                └─────────┘

A vẫn được B tham chiếu.
B vẫn được A tham chiếu.
Nhưng KHÔNG CÓ đường root → A/B.
```

Trong JVM hiện đại dùng tracing/marking-based GC, collector khám phá objects từ roots; đồ thị `A/B` không được đánh dấu reachable chỉ vì chúng trỏ qua lại. Do đó **cycle có thể được collect**.

Ngược lại:

```text
GC root ─► Cache ─► A ◄────► B
```

Bây giờ `A` và `B` **đều reachable** thông qua cache. Chu trình ở đây **không phải nguyên nhân giữ chính**; nguyên nhân là cạnh root → cache → A.

### 4.1. Thu hồi và “đủ điều kiện thu hồi” khác nhau

- **Unreachable/garbage candidate:** về mặt reachability, đối tượng không còn được giữ theo đường tham chiếu sống tương ứng.
- **Reclaimed:** collector đã xử lý và tái sử dụng/giải phóng vùng nhớ vật lý/heap allocation region theo implementation.
- **Heap committed:** JVM đã cấp hoặc giữ một lượng heap với OS; sau GC con số này không nhất thiết giảm.
- **RSS/process memory:** gồm heap và nhiều native/off-heap/mapped regions khác.

Vì vậy **đồ thị không có root path** không có nghĩa `Runtime.totalMemory()`, `freeMemory()` hoặc process RSS lập tức thay đổi. GC policy và heap sizing có thể giữ committed memory để tái sử dụng.

### 4.2. Vì sao `System.gc()` không chứng minh điều gì?

`System.gc()` là yêu cầu/đề nghị JVM thực hiện GC theo contract/platform semantics; collector có thể bỏ qua, cấu hình `-XX:+DisableExplicitGC` có thể thay hành vi, concurrent collector chạy nhiều pha và object liveness có thể phụ thuộc JIT.

Không viết một demo:

```java
object = null;
System.gc();
System.out.println(weakReference.get() == null);
```

rồi cam kết `true` trên mọi JVM/run. Dòng output ở trên **không tất định**. Để chứng minh quy tắc root path, dùng bài duyệt đồ thị độc lập bên dưới; để quan sát GC thật, dùng **repeatable workload + GC logs/heap dump**.

---

## 5. Thực hành vẽ đồ thị: thuật toán xác định reachable set

Trước khi đo collector, có thể mô hình hóa object graph bằng **tập đỉnh và cạnh mạnh** rồi duyệt **DFS/BFS** từ GC roots:

```text
Input:
  V = tất cả objects/nodes đã được mô hình hóa
  E = các cạnh strong trong graph
  R = GC root nodes

Algorithm:
  reachable = empty set
  worklist = R

  while worklist not empty:
      x = pop(worklist)
      if x chưa được thăm:
          reachable.add(x)
          for each strong edge x -> y:
              worklist.add(y)

  unreachable = V - reachable
```

Đây là **mô hình** của bước reachability marking, không phải code HotSpot collector. Collector thực có thể dùng các thuật toán concurrent/generational, write barriers, compressed oops, soft/weak/phantom reference processing và chi tiết tối ưu khác.

### 5.1. Đồ thị ví dụ, trạng thái 1

```text
  STATIC ─► CACHE ─► A ─► B
                      ▲    │
                      └────┘

  STACK ─► E

  C ─► D ─► C
  (C-D không nối root)
```

Tập root: `STATIC` và `STACK`.

Tập reachable: `STATIC, STACK, CACHE, A, B, E`.

Tập unreachable: `C, D`.

### 5.2. Đồ thị ví dụ, trạng thái 2 — ngắt cache edge

```text
  STATIC ─► CACHE        A ─► B
                        ▲    │
                        └────┘

  STACK ─► E

  C ─► D ─► C
```

Khi loại cạnh `CACHE → A`, không còn root path đến `A/B`; tập unreachable trở thành `A, B, C, D`. `CACHE` và `E` vẫn reachable.

**Chú ý:** `B` không cần bị `null` trực tiếp. Khi cạnh duy nhất nối cả component `A/B` với root bị cắt, **toàn bộ component** trở thành unreachable trong mô hình.

---

## 6. Chương trình Java 17 #1 — mô phỏng duyệt đồ thị, stdout chắc chắn

Lưu **`GraphReachabilityDemo.java`**:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class GraphReachabilityDemo {
    private static final Map<String, List<String>> EDGES = new HashMap<>();
    private static final Set<String> VERTICES = new TreeSet<>(
            Arrays.asList("STATIC", "STACK", "CACHE", "A", "B", "C", "D", "E"));
    private static final List<String> ROOTS = List.of("STATIC", "STACK");

    private static Set<String> traverse() {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(ROOTS);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!seen.add(current)) {
                continue;
            }
            for (String next : EDGES.getOrDefault(current, List.of())) {
                queue.addLast(next);
            }
        }
        return new TreeSet<>(seen);
    }

    private static void printState(String name) {
        Set<String> reachable = traverse();
        Set<String> unreachable = new TreeSet<>(VERTICES);
        unreachable.removeAll(reachable);
        System.out.println(name + ".reachable=" + reachable);
        System.out.println(name + ".unreachable=" + unreachable);
    }

    public static void main(String[] args) {
        EDGES.put("STATIC", List.of("CACHE"));
        EDGES.put("CACHE", List.of("A"));
        EDGES.put("A", List.of("B"));
        EDGES.put("B", List.of("A"));
        EDGES.put("STACK", List.of("E"));
        EDGES.put("C", List.of("D"));
        EDGES.put("D", List.of("C"));

        System.out.println("roots=" + ROOTS);
        printState("before");

        EDGES.put("CACHE", new ArrayList<>());
        printState("after");
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 GraphReachabilityDemo.java
java GraphReachabilityDemo
```

**Stdout đúng và không phụ thuộc lịch chạy GC:**

```text
roots=[STATIC, STACK]
before.reachable=[A, B, CACHE, E, STACK, STATIC]
before.unreachable=[C, D]
after.reachable=[CACHE, E, STACK, STATIC]
after.unreachable=[A, B, C, D]
```

Giải thích:

1. `TreeSet` sắp xếp tên nodes, nên thứ tự output không tùy thuộc thứ tự iteration của `HashMap`.
2. `HashSet` của `seen` chỉ phục vụ membership; kết quả cuối được đổi thành `TreeSet` để in ổn định.
3. Có chu trình `A ↔ B` và `C ↔ D`, nhưng `seen` bảo đảm BFS dừng, không lặp vô hạn.
4. Trước khi xóa `CACHE → A`, component `A/B` có root path, còn `C/D` không.
5. Sau xóa, `A/B` mất root path và trở thành unreachable theo **graph model**.
6. Chương trình **không gọi `System.gc()`** và không khẳng định object nội bộ `String`, `List` hoặc `Map` của chính chương trình đã được JVM reclaim. Các strings `"A"`/`"B"` ở đây là **nhãn đỉnh**, không phải objects JVM đang được xét bằng một collector thực.

### 6.1. Tự thay đổi đồ thị để kiểm chứng

- Thêm `EDGES.put("STACK", List.of("E","C"));` trước lần in: `C/D` sẽ reachable.
- Xóa cạnh `A → B` nhưng vẫn giữ `CACHE → A`: `A` reachable, `B` có thể unreachable nếu không có đường khác.
- Thêm cạnh `E → B`: sau khi xóa `CACHE → A`, `B` và `A` vẫn reachable thông qua `STACK → E → B → A`.

**Bài học:** Muốn xác định một object có thể thu hồi, phải kiểm tra **tất cả root paths**, không chỉ một field dễ nhìn thấy.

---

## 7. Chương trình Java 17 #2 — đồ thị object thật với static holder

Lưu **`RealReferenceGraphDemo.java`**:

```java
public final class RealReferenceGraphDemo {
    static final class Node {
        final String name;
        Node next;

        Node(String name) {
            this.name = name;
        }
    }

    private static Node cacheRoot;

    private static void makeDisconnectedCycle() {
        Node c = new Node("C");
        Node d = new Node("D");
        c.next = d;
        d.next = c;
        // Chỉ có các biến cục bộ của method này tham chiếu C/D.
        // Sau khi method kết thúc, không còn đường từ cacheRoot tới C/D.
    }

    public static void main(String[] args) {
        Node a = new Node("A");
        Node b = new Node("B");
        a.next = b;
        b.next = a;

        cacheRoot = a;
        makeDisconnectedCycle();

        System.out.println("rootName=" + cacheRoot.name);
        System.out.println("cycle=" +
                cacheRoot.name + "->" + cacheRoot.next.name +
                "->" + cacheRoot.next.next.name);

        a = null;
        b = null;

        System.out.println("staticHoldsCycle=" + (cacheRoot != null));
        cacheRoot = null;
        System.out.println("staticCleared=" + (cacheRoot == null));
        System.out.println("gcResultAsserted=false");
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 RealReferenceGraphDemo.java
java RealReferenceGraphDemo
```

**Stdout xác định:**

```text
rootName=A
cycle=A->B->A
staticHoldsCycle=true
staticCleared=true
gcResultAsserted=false
```

**Giải thích sâu:**

- `a` và `b` trước tiên tham chiếu hai objects khác nhau.
- `a.next=b` và `b.next=a` tạo chu trình mạnh.
- `cacheRoot=a` giữ một đường static-field → `A/B` khi class còn sống.
- Trong `makeDisconnectedCycle()`, `C/D` có chu trình nhưng không được trả về/cất vào field; sau method, các locals không còn sống theo semantics cần thiết.
- `a=null; b=null` **không** cắt đường static cache tới `A/B`, vì `cacheRoot` vẫn trỏ `A`.
- `cacheRoot=null` cắt đường đó. Tuy vậy **mã Java không chứng minh collectors đã reclaim A/B** hay có root VM/JIT phụ khác tại thời điểm nào. Dòng `gcResultAsserted=false` nhắc rõ không hứa thời điểm thu hồi.
- `makeDisconnectedCycle()` có thể được JIT optimize đi trong những tình huống tương ứng; bạn **không được dùng lab này làm phép đo allocation count chính xác** mà không profiling.

### 7.1. Vẽ đường đi thực tế trước và sau

```text
Trước:
    ROOT/class state ─► cacheRoot ─► A ─► B
                                  ▲       │
                                  └───────┘

    local a ─► A
    local b ─► B
    C ◄────► D  (không được lưu ra ngoài method)

Sau a=null, b=null:
    ROOT/class state ─► cacheRoot ─► A ◄────► B

Sau cacheRoot=null:
    A ◄────► B      (không còn đường này từ static field)
```

Một ứng dụng thật có thể có root khác (thread local, queue, listeners, JNI handle...) vẫn giữ `A/B`. Vì vậy sơ đồ là **mô hình cho đúng code path của lab**, không bảo đảm toàn bộ heap có đúng bấy nhiêu roots.

---

## 8. Garbage vs “bộ nhớ bị giữ” vs memory leak

**Đối tượng rác (garbage candidate):** đối tượng mà collector không còn đường reachable phù hợp từ GC roots, tùy semantics strong/soft/weak/phantom.

**Đối tượng đang được giữ (retained object):** vẫn reachable nhờ một hoặc nhiều strong root paths, do đó bình thường **không** bị thu gom theo cách phá vỡ Java semantics.

**Memory leak ở Java:** chương trình vô tình giữ references tới objects không còn cần theo nhu cầu nghiệp vụ, khiến live set hoặc memory usage tích lũy không mong muốn. GC hoạt động đúng — **chính reference graph đang giữ object**.

```text
             ROOT
              │
              ▼
      SessionRegistry (static)
              │
              ▼
       ConcurrentHashMap
              │
      ┌───────┼───────┐
      ▼       ▼       ▼
   session1 session2 session3
      │       │       │
      ▼       ▼       ▼
   byte[]   byte[]   byte[]

Nếu session2 đã hết hạn nhưng registry vẫn tham chiếu:
  session2 chưa phải garbage.
  Đây có thể là retention không mong muốn (logical leak).

Nếu xóa entry session2:
  session2 có thể trở thành unreachable
  NHƯNG chỉ nếu không có root path khác.
```

### 8.1. Không phải đối tượng giữ lâu nào cũng là leak

Ví dụ object cố tình giữ:

- Metadata bất biến dùng suốt lifetime của service.
- Bounded cache theo LRU/time-to-live.
- Connection pool được quản lý, kích thước hữu hạn.
- Session còn active trong giới hạn business.
- Lookup tables và class metadata cần thiết.

Một cache 1 GiB có thể là thiết kế hợp lý nếu có resource budget, bound và hit rate hiệu quả. Ngược lại, một `HashMap` chỉ vài MiB nhưng tăng liên tục và không có chính sách eviction có thể là leak.

**Kết luận có cơ sở cần ít nhất:** expectation/lifecycle của dữ liệu, trend qua thời gian và root path đang giữ dữ liệu quá lâu.

### 8.2. Unreachable không có nghĩa `heapUsed` lập tức giảm

JVM có thể chưa cần GC. Một bộ thu gom có thể đánh dấu unreachable objects nhưng chưa trả heap committed memory cho OS. Heap-used/committed/maximum và process RSS là các phép đo khác nhau.

Đừng dùng:

```text
memory leak = RSS sau request > RSS trước request
```

Đây là **tiêu chí sai**. RSS còn gồm code cache, metaspace, thread stacks, native allocations và memory-mapped regions. Với heap objects, xem **post-GC live-set trend và retained object graphs**.

---

## 9. Chương trình Java 17 #3 — bounded retention, giải phóng đường tham chiếu

Lưu **`CacheRetentionDemo.java`**:

```java
import java.util.LinkedHashMap;
import java.util.Map;

public final class CacheRetentionDemo {
    private static final Map<String, byte[]> CACHE = new LinkedHashMap<>();

    private static int payloadBytes() {
        int total = 0;
        for (byte[] data : CACHE.values()) {
            total += data.length;
        }
        return total;
    }

    public static void main(String[] args) {
        CACHE.put("s1", new byte[1024]);
        CACHE.put("s2", new byte[1024]);
        CACHE.put("s3", new byte[1024]);

        System.out.println("entriesBefore=" + CACHE.size());
        System.out.println("payloadBefore=" + payloadBytes());
        System.out.println("hasS2Before=" + CACHE.containsKey("s2"));

        CACHE.remove("s2");

        System.out.println("entriesAfter=" + CACHE.size());
        System.out.println("payloadAfter=" + payloadBytes());
        System.out.println("hasS2After=" + CACHE.containsKey("s2"));
    }
}
```

Chạy:

```bash
javac --release 17 CacheRetentionDemo.java
java CacheRetentionDemo
```

**Stdout xác định:**

```text
entriesBefore=3
payloadBefore=3072
hasS2Before=true
entriesAfter=2
payloadAfter=2048
hasS2After=false
```

Giải thích:

1. Cache được giữ qua static field `CACHE`, như một owner sống dài hạn trong service.
2. Mỗi map value là `byte[1024]`; phép cộng là **payload bytes**, **không** phải shallow heap size hay retained size chính xác của `LinkedHashMap`, entries, strings và array headers.
3. `remove("s2")` xóa một cạnh `map entry → byte[]`; nó **không gọi GC** và không cam kết reclaimed tức thì.
4. Trong demo, không cố ý có một owner khác giữ mảng s2 sau khi `remove`, nhưng JVM/JIT liveness vẫn khiến ta không kết luận một thời điểm reclaim.
5. Nếu code ngoài cache giữ strong reference tới s2 array, entry removal **không** làm nó unreachable.

### 9.1. Đồ thị trước–sau

```text
Trước:
  ROOT → CACHE → map
                   ├─ entry("s1") → byte[1024] A
                   ├─ entry("s2") → byte[1024] B
                   └─ entry("s3") → byte[1024] C

Sau remove("s2"):
  ROOT → CACHE → map
                   ├─ entry("s1") → byte[1024] A
                   └─ entry("s3") → byte[1024] C

  byte[1024] B:
      không còn đường QUA CACHE; phải kiểm tra root khác.
```

**Bài học:** khi xóa owner edge, hãy viết “không còn đường qua owner này”, không tự nhảy đến “object đã được GC”. Việc object trở thành unreachable cần xác minh **mọi root path**.

---

## 10. Vẽ sơ đồ khi có nhiều đường root

Bẫy phổ biến của việc vẽ chỉ một đường:

```text
GC ROOT 1 ─► Registry ─► Session X ─► Payload
                                     ▲
GC ROOT 2 ─► WorkerQueue ─► Task ───┘
```

Nếu `Registry.remove(X)`, `Payload` vẫn reachable qua `WorkerQueue → Task`. Không thể giải phóng `Payload` chỉ từ việc cắt một edge.

**Cách vẽ chặt chẽ:**

1. Đặt **tất cả roots liên quan** ở đầu hoặc bên trái sơ đồ.
2. Vẽ các owners (registry, queues, pools, static caches, listeners).
3. Vẽ các strong edges rõ chiều.
4. Vẽ những edge đã xóa bằng đường gạch/nhãn “removed”, không xóa lịch sử một cách mơ hồ.
5. Tô/ghi danh sách các nodes reachable sau mỗi thay đổi.
6. Kiểm tra có đường đi thay thế nào từ root tới node nghi vấn.
7. Nếu dùng weak/soft/phantom references, **vẽ dạng cạnh khác** và áp dụng semantics riêng, không mặc định tất cả đều strong edges.

### 10.1. Nếu một object được nhiều owner giữ

```text
         ROOT
         /  \
        ▼    ▼
       A      B
        \    /
         ▼  ▼
          P
```

Cả `A` và `B` giữ strong edge tới `P`. Xóa `A→P` **không** làm P unreachable vì `ROOT→B→P` vẫn tồn tại.

Đây cũng là điểm bắt đầu phân biệt **dominator** với “object có field trỏ tới P”: một object có outgoing edge tới P **không nhất thiết dominate P**.

---

## 11. Shallow size, retained size và dominator

### 11.1. Shallow size

**Shallow heap** của object `O` là bộ nhớ footprint cho **bản thân object** (header, fields, padding theo JVM layout), **không** bao gồm toàn bộ objects mà nó tham chiếu.

Ví dụ `ArrayList` có object header/fields riêng, một backing array và các element objects; shallow size của `ArrayList` object không tự cộng sizes của elements.

### 11.2. Retained size

**Retained heap** của `O` là tổng kích thước objects có thể trở thành reclaimable nếu ta loại bỏ owner `O` cùng các incoming references tới nó theo mô hình dominator/heap analysis. Nói chính xác về graph: objects **dominated** bởi O nằm trong retained set của O. Retained size của O gồm shallow sizes của các objects trong retained set.

Đó là **một phép tính trên snapshot object graph**, không phải số bytes OS chắc chắn sẽ trả về khi thực hiện `o=null` ở một biến.

### 11.3. Dominator là gì?

Trong đồ thị có super-root đại diện tập GC roots, một node `D` **dominates** node `X` nếu **mọi đường từ root đến X đều đi qua D**.

```text
             ROOT
               │
               ▼
             Cache
            /     \
           ▼       ▼
          A         B
           \       /
            ▼     ▼
             Payload
```

- `Cache` dominates `Payload` nếu mọi root paths đến `Payload` đều qua Cache.
- `A` **không** dominates Payload vì vẫn có đường `ROOT→Cache→B→Payload`.
- `B` cũng **không** dominates Payload.
- Khi loại Cache hoàn toàn (và không có root path khác), A/B/Payload có thể thành unreachable.
- Chỉ bỏ A không đủ khiến Payload unreachable.

### 11.4. Dominator tree khác object reference graph

Trong object graph, A và B **đều có edge** tới Payload. Nhưng trong **dominator tree**, mỗi node có một **immediate dominator** (ngoại trừ root), vì đây là cây biểu diễn quan hệ thống trị chứ không sao chép mọi cạnh tham chiếu.

```text
Object graph:
  ROOT → Cache → A → Payload
                 B → Payload

Dominator tree (minh họa):
  ROOT
   └── Cache
       ├── A
       ├── B
       └── Payload
```

Vì cả A và B có đường riêng tới Payload, neither A nor B is the immediate dominator; Cache là owner chung. Tính toán retained size theo dominator tree giúp thấy **những owners giữ cả đồ thị con**, thay vì chỉ chọn class có nhiều instances.

### 11.5. Bảng phân tích minh họa

| Đối tượng | Shallow (giả lập) | Retained (giả lập) | Câu hỏi nên hỏi |
| --- | ---: | ---: | --- |
| `CacheManager` | 40 B | 210 MiB | Những graph con nào hoàn toàn do cache giữ? |
| `HashMap` | 48 B | 208 MiB | Entries/values có đường root nào khác không? |
| Một `byte[]` | 1 MiB | 1 MiB | Mảng này có nhiều incoming strong references không? |
| `Session` | 64 B | 12 MiB | Có dominate payloads hay payload còn owner khác? |

Các số là **ví dụ lý thuyết**; object header, alignment và graph cụ thể tùy JDK/collector/heap dump analyzer. Không dùng bảng này làm kết quả `jol`/`MAT` đo được.

### 11.6. Đừng cộng retained sizes ngây thơ

Retained sets có thể lồng nhau: nếu `CacheManager` dominates `HashMap`, retained set của HashMap nằm trong retained set của CacheManager. **Cộng retained size của cả hai** có thể đếm trùng.

Để đánh giá tổng bộ nhớ: xem roots, distinct object sets và cây dominator thay vì cộng mọi dòng retained bytes.

---

## 12. Java `java.lang.ref`: các cạnh “yếu” khác strong edge

Một Java reference object như `WeakReference<T>` nằm trong heap và bản thân nó có thể được giữ mạnh bởi một collection. Tuy nhiên **mối liên hệ từ reference object tới referent được collector xử lý theo semantics riêng**, không giống field strong bình thường.

### 12.1. Các mức reachability

Từ mạnh đến yếu theo package `java.lang.ref` của Java 17:

| Loại | Diễn giải | Thận trọng |
| --- | --- | --- |
| **Strong** | Có đường từ thread/root tới referent không đi qua `Reference` objects | GC không được phá một strong live reference hợp lệ |
| **Soft** | Không strongly reachable nhưng có đường qua `SoftReference` | Collector có thể giữ hoặc clear theo chính sách/memory demand |
| **Weak** | Không strongly/softly reachable, chỉ còn qua `WeakReference` | Collector có thể clear weak refs ở giai đoạn xử lý thích hợp |
| **Phantom** | Trạng thái phantom reachability/`PhantomReference` dùng cho hậu kiểm/cleanup scheduling | `PhantomReference.get()` luôn trả `null` |
| **Unreachable** | Không ở những mức reachability trên | Eligible reclamation, không cam kết thời điểm |

**Không suy** “tồn tại `WeakReference` ⇒ referent chắc chắn còn sống”. `WeakReference.get()` có thể trả non-null rồi ở lần sau null; nếu application cần dùng referent, hãy lấy strong local `T obj=ref.get()` và kiểm tra null.

### 12.2. Vẽ cạnh weak bằng nét đứt

```text
   ROOT ─► Owner ─► WeakReference - - -► Referent X
                        weak edge

   Không có strong root path khác tới X:
     X có thể trở thành weakly reachable
     và weak edge có thể được clear.

   ROOT ─► OtherOwner ───────────────► X
   ROOT ─► Owner ─► WeakReference - -► X

   Khi có strong path qua OtherOwner:
     X vẫn strongly reachable.
```

Một reference object đang reachable chưa đảm bảo referent mạnh. **Đừng vẽ cạnh weak như strong** rồi kết luận cache weak giữ leak.

### 12.3. Chương trình Java 17 #4 — WeakReference, kết quả không phụ thuộc GC

Lưu **`WeakEdgeDemo.java`**:

```java
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public final class WeakEdgeDemo {
    public static void main(String[] args) {
        Object target = new Object();
        WeakReference<Object> weak = new WeakReference<>(target);

        boolean nonNullWhileStrong = weak.get() != null;
        Reference.reachabilityFence(target);

        target = null;
        System.out.println("weakNonNullWhileStrong=" +
                nonNullWhileStrong);
        System.out.println("localCleared=" + (target == null));
        System.out.println("gcOutcomeAsserted=false");
    }
}
```

Chạy:

```bash
javac --release 17 WeakEdgeDemo.java
java WeakEdgeDemo
```

**Stdout xác định với Java 17:**

```text
weakNonNullWhileStrong=true
localCleared=true
gcOutcomeAsserted=false
```

Giải thích:

- `weak.get()` được gọi trong khi strong local `target` còn được bảo đảm sống tới `Reference.reachabilityFence(target)`, nên lần kiểm tra ban đầu non-null.
- `target=null` xóa **một** strong local edge.
- `weak` vẫn trỏ theo semantics weak; việc GC clear referent hay chưa **không được kiểm tra** như một kết quả tất định.
- Sau khi `target=null`, `WeakReference.get()` vẫn **có thể** trả non-null cho tới khi collector clear nó; không cam kết sẽ lập tức null.
- `reachabilityFence` **không trigger GC** và không chứng minh đã thu hồi.

### 12.4. Vì sao không dùng `System.gc()` làm test unit?

Một bài test:

```java
target = null;
System.gc();
assert weak.get() == null;
```

không phải test portable/deterministic. VM được phép xử lý explicit GC theo policy, GC có thể chưa chạy hoặc referent vẫn strongly reachable do những root paths khác. Nếu cần đo reachability thật, thiết kế test có timeouts và môi trường phù hợp, **nhưng không biến kết quả GC timing thành semantic invariant của Java 17**.

---

## 13. `WeakHashMap` vẫn có thể giữ object — bẫy value tham chiếu ngược key

`WeakHashMap<K,V>` giữ keys bằng weak references, còn **values** được map giữ bằng **strong references**.

Sơ đồ khó thấy:

```text
GC ROOT
   │
   ▼
WeakHashMap
   │
   ├─ weak key - - -► Key K
   │
   └─ strong value ─► Value V
                       │
                       └─ strong field ─► Key K
```

Dù map dùng weak keys, `Key K` vẫn **strongly reachable** qua `Map → Value V → Key K`, nên entry có thể không biến mất như kỳ vọng. Đây là vấn đề tham chiếu **gián tiếp**, không giải quyết bằng “WeakHashMap tự quản memory”.

### 13.1. Khi nào dùng weak key hợp lý?

- Cần mapping metadata theo một key mà owner ngoài map quyết định vòng đời.
- Không muốn map **một mình** giữ key sống.
- Value không vô tình giữ key trực tiếp/gián tiếp qua một đồ thị khác.
- Ứng dụng chấp nhận entry có thể biến mất theo lịch GC, không coi WeakHashMap là persistent store.

**Nếu cần cache hữu hạn và eviction ổn định theo chính sách**, thường nên dùng bounded cache với size/TTL thay vì dựa vào GC để điều tiết business data lifecycle.

### 13.2. WeakReference và ReferenceQueue

Khi một reference object được đăng ký với `ReferenceQueue`, VM có thể enqueue reference khi reachability thay đổi theo quy tắc. Nhưng queue **không tự giữ reference object đã đăng ký sống** mãi: application cần giữ reference object reachable nếu muốn nhận notification.

Không dùng queue events làm thứ tự business transaction. Chúng phụ thuộc GC activity, không có deadline business-level, và có thể không tới trước khi process kết thúc.

---

## 14. Soft/phantom references và Cleaner khi vẽ đồ thị

### 14.1. SoftReference

`SoftReference<T>` thường dùng cho memory-sensitive caching, nhưng collector có thể clear theo policy/memory pressure. Không có guarantee “giữ N giây”, “mỗi 10 MB mất một entry” hay “không bao giờ bị xóa khi RAM còn trống”.

Sơ đồ:

```text
ROOT → Cache → SoftReference - - -► ImageBuffer
                                   (soft edge)
```

Nếu `ImageBuffer` không có strong root path khác, nó softly reachable. Heap dump analyzer có thể cho lựa chọn exclude weak/soft paths khi tìm suspect dominators; cần biết analyzer đang áp dụng filter nào.

### 14.2. PhantomReference

`PhantomReference.get()` trả `null` theo contract. Nó dùng cho phối hợp post-mortem/cleanup notifications cùng `ReferenceQueue` hoặc abstractions như `Cleaner`. Không phải “weak reference nhưng chậm hơn” để lấy object khi cần.

Một đồ thị reference cần ký hiệu phantom edge riêng. **Không tính phantom edge như strong root path giữ nghiệp vụ alive**.

### 14.3. Cleaner và không giữ referent trong cleaning action

`Cleaner` dùng phantom references để đăng ký cleanup actions. Cleaning action **không nên giữ strong reference tới chính referent** qua lambda capture/inner class; nếu giữ, bạn tạo một root path làm referent không thể phantom-reachable.

```text
Cleaner ─► CleaningAction ─► Resource  (sai: strong capture referent)
                          ▲
                          └── Ngăn referent trở nên phantom reachable

Đúng hơn:
Cleaner ─► StaticStateOnly (không tham chiếu Resource)
```

Ngay cả khi dùng Cleaner đúng, **không có deadline cleanup chắc chắn**. Với file/socket/native handles, ưu tiên `AutoCloseable` và `try-with-resources` để release deterministic. Cleaner chỉ là safety net.

---

## 15. Đồ thị bị giữ bởi ThreadLocal và thread pool

Một kiểu leak khó phát hiện:

```text
ROOT → PoolWorkerThread
                │
                ▼
         ThreadLocalMap
                │
                ▼
             Entry
                │
                ▼
         RequestContext
                │
                ▼
            byte[8 MiB]
```

Nếu worker thread sống lâu và request handler dùng `ThreadLocal` mà không `remove()` trong `finally`, value có thể bị giữ sau khi request đã kết thúc.

**Đặc biệt:** trong HotSpot, weak key ở entry ThreadLocalMap **không làm value tự biến mất tức thì** chỉ vì `ThreadLocal` key bị GC. Có thể còn stale entry có value strong cho tới khi map thực hiện cleanup, phụ thuộc access/lifecycle.

### 15.1. Thực hành phòng ngừa

```java
private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

static void handle(String requestId) {
    try {
        CONTEXT.set(requestId);
        // xử lý request
    } finally {
        CONTEXT.remove();
    }
}
```

Đây là **snippet** trong class, không phải file chạy độc lập. Ý chính là `try/finally` đưa lifecycle của value gắn với request, tránh thread pool giữ context vô hạn.

### 15.2. Tại sao heap dump giúp hơn heap-used gauge?

Heap-used gauge chỉ cho biết bao nhiêu bytes đang dùng. Heap dump có **Paths to GC Roots** sẽ chỉ ra đường qua `ThreadLocalMap.Entry.value`, giúp tìm owner thực sự.

Chú ý thread stacks/traversals tại thời điểm dump và JNI native roots; phải đọc đúng path từ root, không chọn object gần nhất có tên `ThreadLocal` rồi kết luận.

---

## 16. Đồ thị bị giữ bởi listener, scheduler, queue và classloader

Nhiều object leaks là vấn đề **registration lifecycle**:

```text
  ROOT → EventBus → listenerList → Listener
                                   │
                                   ▼
                           ApplicationComponent
                                   │
                                   ▼
                              huge graph
```

Component “đóng” ở tầng business nhưng quên unregister listener → root path vẫn tồn tại.

Những pattern khác:

- **Scheduled executor:** task định kỳ giữ lambda closure trỏ vào application state; task không bị cancel.
- **Executor queue:** backlog tasks giữ request payloads dù request phía client đã timeout.
- **Static collection:** `static Map`/`List` giữ keys hoặc values không bounded.
- **Classloader leak:** singleton/daemon thread/thread context class loader giữ loader cũ khi undeploy plugin/webapp.
- **Callback registrations:** event bus/listeners/observable references không release.
- **ThreadLocal values:** request-scoped objects bị giữ trong pooled workers.
- **Native/JNI global refs:** Java object bị giữ bởi native owner, Java code không thấy static field.
- **Unclosed resource:** socket/file handle leak là tài nguyên ngoài heap; một object graph có thể chỉ ra owner nhưng heap-size không đo số file descriptors còn mở.

### 16.1. Viết đúng graph cho lambda captures

```java
byte[] largeData = new byte[1_048_576];
Runnable job = () -> System.out.println(largeData.length);
queue.add(job);
```

Đây là **snippet**, giả định `queue` đã khai báo. Nếu closure capture `largeData` và queue giữ `job`, sơ đồ có thể là:

```text
ROOT → TaskQueue → Runnable lambda object → byte[1 MiB]
```

Dù method khởi tạo `job` đã return, queue vẫn giữ payload. Nếu queue bị kẹt hoặc không bounded, retained graph có thể tăng. Sửa có thể là bounded queue, cancellation, cleanup hoặc thiết kế dữ liệu nhỏ hơn; không phải luôn tăng `-Xmx`.

---

## 17. Đọc histogram, heap dump và Paths to GC Roots trong Java 17

Với HotSpot JDK 17, công cụ `jcmd` hỗ trợ:

```bash
jcmd <pid> VM.version
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
jcmd <pid> GC.heap_dump /path/to/snapshot.hprof
```

Các dòng là mẫu **cần thay `<pid>` bằng PID thật** và path writable của **target JVM**. `jcmd` cần quyền attach, đúng process/container namespace và tooling phù hợp.

Theo Oracle JDK 17:

- `GC.class_histogram` là **High impact**; thống kê objects/bytes theo class. Nó **không cho đường reference tới GC roots**.
- `GC.heap_dump` là **High impact**; tạo HPROF heap dump. Mặc định có thể yêu cầu **full GC** trước dump; `-all` thay đổi chế độ để ghi cả unreachable objects.
- `GC.heap_info` báo thông tin heap chung, không phải object graph.
- `-XX:+HeapDumpOnOutOfMemoryError` có thể tạo dump khi xuất hiện Java `OutOfMemoryError` theo các điều kiện phù hợp, nhưng cần đủ disk/permissions và cân nhắc bảo mật.

### 17.1. Vì sao histogram không chứng minh leak?

Giả sử:

```text
# instances    # bytes     class
  300000        96 MiB       [B
  100000        12 MiB       Session
    1000         2 MiB       HashMap$Node
```

**Đây là dữ liệu minh họa**, không phải output được đo. Bạn biết nhóm `byte[]` chiếm nhiều shallow bytes, nhưng chưa biết:

- Ai đang giữ những arrays?
- Chúng nằm trong cache nào?
- Có nhiều root paths tới một array không?
- Chúng còn cần cho nghiệp vụ không?
- Khi nào được giải phóng?
- 300.000 arrays là post-GC retained set hay gồm unreachable objects theo mode?

Cần heap dump và owner paths.

### 17.2. Mở heap dump trong Eclipse MAT

Quy trình gợi ý:

1. Lấy dump đúng JVM/PID/workload/time window và lưu ở nơi được kiểm soát.
2. Mở HPROF trong **Eclipse Memory Analyzer** trong môi trường có đủ RAM/disk.
3. Xem **Histogram** để biết top classes theo instances/shallow bytes.
4. Xem **Dominator Tree** để tìm owners có retained size lớn.
5. Chọn một suspect object rồi xem **Paths to GC Roots**; chọn filter về weak/soft references có chủ đích.
6. Xem owner field/collection entry gần root nhất mà application kiểm soát.
7. Đối chiếu source code và lifecycle thực: cache bound/TTL, listener remove, ThreadLocal cleanup, executor queue và classloader.
8. Nếu cần, so hai hoặc nhiều dumps từ **cùng workload, cùng lifecycle phase** để xem retained graph thay đổi.

**MAT là công cụ phân tích object graph snapshot**, không tự biết business policy “bao lâu thì session nên hết hạn”.

### 17.3. Cần biết filter “exclude weak references”

Một số công cụ có tùy chọn **exclude weak/soft reference edges** khi tìm paths to GC roots. Điều này hợp lý nếu muốn tìm **strong retention**, nhưng không phải mọi reference path hiển thị/ẩn đều theo cùng semantics.

Nếu MAT cho thấy object chỉ có weak path, hãy kiểm tra có strong owner khác hay không. Đừng chụp hình đường weak rồi kết luận “WeakReference gây memory leak”, cũng đừng loại tất cả soft references khỏi investigation khi đang tìm memory-sensitive cache pressure.

### 17.4. Heap dump có thể làm thay đổi đối tượng quan sát

Do cách `GC.heap_dump` mặc định request GC và safepoint/traversal:

- Một số unreachable objects có thể đã được thu trước khi snapshot được ghi.
- Finalization/reference processing có thể làm tập objects/paths khác ảnh chụp mental graph trước đó.
- Heap dump không phải luôn phản ánh chính xác “trước GC” mà bạn tưởng.
- `-all` không phải “đúng hơn”: nó có thể giữ cả unreachable objects trong output, làm dễ nhầm với retained leaks.

**Luôn ghi capture flags** và câu hỏi đang điều tra. Nếu nghi memory leak, một live-heap oriented snapshot sau collection thường thuận lợi để xem retention; nếu nghi allocation churn, dùng JFR allocation profiling/time-series phù hợp hơn.

### 17.5. Heap dump chứa dữ liệu nhạy cảm

HPROF có thể chứa plaintext credentials, tokens, session data, request payloads, PII nằm trong strings/byte arrays. Vì vậy:

- Không upload dump production lên kho public.
- Chỉ cấp quyền cho người điều tra được phép.
- Bảo mật file at rest/in transit, đặt retention policy.
- Kiểm tra disk capacity và container filesystem trước khi dump.
- Sau điều tra xóa artifact theo chính sách.
- Ưu tiên dữ liệu giả khi làm lab.

---

## 18. Cách thu dump cho một object graph thật sự tồn tại đủ lâu

Chương trình nhanh như `CacheRetentionDemo` có thể kết thúc trước khi kịp attach `jcmd`. Dưới đây là một lab riêng có time window để quan sát. Mục tiêu chỉ là **giữ một static graph còn sống để nhìn trong heap dump**, không cố ý làm service leak trong production.

### 18.1. Chương trình Java 17 #5 — giữ graph trong 20 giây

Lưu **`HeapGraphHoldDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public final class HeapGraphHoldDemo {
    static final class Payload {
        final int id;
        final byte[] bytes;

        Payload(int id, int size) {
            this.id = id;
            this.bytes = new byte[size];
        }
    }

    static final class Registry {
        final List<Payload> items = new ArrayList<>();
    }

    private static Registry registry;

    public static void main(String[] args) throws InterruptedException {
        Registry current = new Registry();
        for (int i = 0; i < 4; i++) {
            current.items.add(new Payload(i, 8192));
        }

        registry = current;

        System.out.println("retainedItems=" + registry.items.size());
        System.out.println("payloadBytes=" + (4 * 8192));

        long holdMillis = Long.getLong("holdMillis", 0L);
        if (holdMillis > 0) {
            Thread.sleep(holdMillis);
        }

        System.out.println("finished=true");
    }
}
```

Biên dịch:

```bash
javac --release 17 HeapGraphHoldDemo.java
java HeapGraphHoldDemo
```

**Stdout của run bình thường, không bị interrupt:**

```text
retainedItems=4
payloadBytes=32768
finished=true
```

Để thu HPROF trên môi trường lab:

```bash
java -DholdMillis=20000 HeapGraphHoldDemo
```

Trong một terminal khác **trước khi hết 20 giây**, xác định PID đúng rồi chạy:

```bash
jcmd <pid> GC.class_histogram
jcmd <pid> GC.heap_dump /tmp/heap-graph.hprof
```

Nếu app đã kết thúc trước khi bạn attach, chạy lại lab, tăng `holdMillis` hợp lý. File được tạo ở **filesystem của target JVM** và theo quyền của process ấy. Trên Windows dùng đường dẫn writable phù hợp thay `/tmp`.

### 18.2. Đồ thị kỳ vọng trong heap dump

```text
GC roots / class metadata
         │
         ▼
HeapGraphHoldDemo.registry
         │
         ▼
      Registry
         │
         ▼
     ArrayList
         │
         ▼
    Object[] backing
     ├── Payload(0) ─► byte[8192]
     ├── Payload(1) ─► byte[8192]
     ├── Payload(2) ─► byte[8192]
     └── Payload(3) ─► byte[8192]
```

Ngoài ra khi method `main` vẫn đang chạy, biến local `current` **có thể** còn được VM xem là live reference trong frame tùy tối ưu và cách sử dụng. MAT có thể hiển thị thêm root paths, vậy **không cam kết static registry là root path duy nhất** ở snapshot thực.

### 18.3. Những thứ không thể đoán cứng từ HPROF

- Exact class instance count trong JVM (có hàng nghìn objects khác).
- Exact shallow/retained size của registry, payload, arrays.
- Số GC roots khác do VM internals.
- Thời gian dừng/pause để dump.
- Heap addresses, object IDs và references được JIT tối ưu.
- Nếu app quá nhanh và đã exit, file dump không được tạo.

**Không in transcript MAT như một kết quả chắc chắn**. Chỉ in stdout nghiệp vụ deterministic ở ví dụ; số liệu HPROF phải đọc từ run thật.

---

## 19. Dùng số đo theo thời gian để xác nhận retention thật

Một snapshot graph cho biết “ai đang giữ gì **ngay lúc chụp**”, nhưng không chứng minh **leak tăng theo thời gian**. Cần so các mốc có điều kiện tương đương.

### 19.1. Hai chuỗi dữ liệu giả lập

**Trường hợp A: allocation churn có thể bình thường**

| Mốc | Heap trước GC | Heap sau GC | Lượt request |
| --- | ---: | ---: | ---: |
| T1 | 1,8 GiB | 0,5 GiB | 10.000 |
| T2 | 2,0 GiB | 0,5 GiB | 10.000 |
| T3 | 1,9 GiB | 0,5 GiB | 10.000 |

Sau-GC baseline ổn định, gợi ý objects tạm thời được thu. Không đủ bằng chứng retained leak.

**Trường hợp B: tăng retained live set đáng nghi**

| Mốc | Heap trước GC | Heap sau GC | Lượt request |
| --- | ---: | ---: | ---: |
| T1 | 1,8 GiB | 0,5 GiB | 10.000 |
| T2 | 2,2 GiB | 1,0 GiB | 10.000 |
| T3 | 2,7 GiB | 1,6 GiB | 10.000 |

Nếu workload và lifecycle phase tương đương, post-GC baseline tăng có thể là growth của live/retained objects. Cần đối chiếu caches, warm-up, class loading, session counts, GC logs và heap dumps rồi mới gọi leak.

Đây là **dữ liệu mô phỏng**, không phải kết quả benchmark đo được.

### 19.2. Những số liệu nên thu

- Heap used **sau các lần collection tương đương** theo collector.
- GC counts, pause durations, allocation rate.
- Object counts theo class và retained sizes từ dumps.
- Active sessions, cache entries, queue depths, live thread counts.
- GC roots/dominators cho những object groups tăng.
- Process RSS, cgroup memory và native allocations nếu tổng RAM tăng nhưng heap ổn.
- JDK vendor/build, GC implementation/flags và workload.

### 19.3. Allocation hotspot ≠ retention hotspot

Một method tạo hàng triệu `StringBuilder` objects trong thời gian ngắn có thể có allocation rate rất cao, nhưng phần lớn objects nhanh chóng unreachable. Heap dump sau GC có thể không còn thấy chúng.

Một static cache chỉ thêm 100 objects/phút nhưng mỗi object giữ vài MiB có thể gây retained leak nghiêm trọng.

**Hệ quả:** dùng JFR/allocation profiling khi tìm **allocation churn**, dùng dominator/GC-root graph khi tìm **retention**.

---

## 20. Checklist vẽ đồ thị cho một incident

Với một object `X` bị nghi giữ quá lâu:

1. Xác định **class + object ID** trong snapshot, không chỉ string name.
2. Tìm ít nhất một **Path to GC Roots**.
3. Ghi roots/owners theo chiều `ROOT → … → X`.
4. Kiểm tra **mọi root path có ý nghĩa**, không chọn một đường ngắn nhất rồi suy là duy nhất.
5. Phân biệt **strong** và soft/weak/phantom paths.
6. Xác định owner mà **application kiểm soát**: cache, queue, map, listener, ThreadLocal, singleton, loader hoặc JNI.
7. Kiểm tra object có còn **được nghiệp vụ cần** không.
8. Nếu muốn cắt edge, chỉ rõ field/entry/callback nào phải bị remove/clear/cancel.
9. Vẽ đồ thị **sau khi cắt edge** và tìm đường root thay thế.
10. Dự kiến kết quả: unreachable candidates, chứ không ghi “GC lập tức trả RAM”.
11. Verify với lifecycle tests và số liệu heap sau GC/retained sizes.
12. Nếu hiện tượng vẫn còn, cập nhật graph từ dump mới; đừng tin bản vẽ giả thuyết hơn evidence.

### 20.1. Template mô tả một root path

```text
Suspect object:
  class = com.example.SessionContext
  observed_count = <count from heap analyzer>
  retained_bytes = <from snapshot, not guessed>

Strong root path:
  <root category>
    -> <application owner>
    -> <collection / field name>
    -> <entry / task / listener>
    -> SessionContext
    -> byte[]

Business expectation:
  session should be released after logout

Actual:
  registry never removes session key

Proposed intervention:
  remove registration at lifecycle end
  add bounded/TTL policy and tests

Verification:
  same workload + post-GC metrics
  repeat dump and check retained root paths
```

Đây là **mẫu thu thập**, các placeholder phải được thay bằng dữ liệu thật. Đừng đưa số count/bytes tự đoán vào báo cáo production.

---

## 21. Trường hợp object graph có cycle và nhiều root paths

Hãy phân tích:

```text
               ROOT1                    ROOT2
                 │                        │
                 ▼                        ▼
                Q1                       Q2
                 │                        │
                 ▼                        ▼
                 A ─────────► B ─────────► C
                 ▲                        │
                 └────────────────────────┘
```

Giả sử:

- `ROOT1 → Q1 → A`.
- `ROOT2 → Q2 → C`.
- `A→B→C→A` tạo chu trình.
- Nếu remove `Q1→A`, component vẫn reachable từ `ROOT2→Q2→C→A→B`.
- Nếu remove `Q2→C`, component vẫn reachable từ ROOT1.
- Chỉ khi **cả hai đường root** bị cắt và không có root paths khác, `A/B/C` mới unreachable.

### 21.1. Vì sao “xóa một reference” không phải phép đo leak?

Một object có thể còn tham chiếu từ:

- Thread stack của một request chưa hoàn tất.
- A long-lived scheduler task.
- Một global cache khác.
- JNI global handle.
- Container plugin registry.
- Framework reflection metadata/loader graph.
- Listener list đã quên unregister.

Do đó nếu thay code xóa một map entry rồi heap usage không giảm, cần xem liệu **còn đường root khác** hay không trước khi kết luận collector lỗi.

---

## 22. Biểu diễn weak vs strong edges trên cùng graph

Một đồ thị có cả root path mạnh và yếu:

```text
ROOT ──strong──► CACHE ──strong──► WeakReference W
                                       |
                                       | weak
                                       ▼
                                       X
ROOT ──strong──► LIVE_OWNER ──────────► X

Nếu LIVE_OWNER vẫn giữ X:
  X strongly reachable

Nếu bỏ LIVE_OWNER → X:
  X có thể còn được W tham chiếu yếu
  nhưng không còn strongly reachable qua hai owners trên
  weak.get() có thể bị clear theo GC processing.
```

**Không kết luận từ số edge.** Một edge strong duy nhất từ root có thể đủ giữ cả component lớn. Hàng nghìn weak references không làm đối tượng strongly reachable nếu không có strong path khác.

### 22.1. Soft references và memory pressure

Soft-reachable objects có thể được giữ lại hoặc clear tùy collector/memory pressure. Một heap dump được lấy trước khi collector clear soft refs có thể cho bức tranh khác dump sau pressure/GC.

Đừng dùng SoftReference cache để quản lý thời hạn session nghiệp vụ; TTL/eviction là policy ứng dụng, GC reachability không phải business deadline.

### 22.2. PhantomReference và Cleaner không phục hồi object

Phantom reference không dùng để lấy lại object; `get()` luôn trả `null`. Nếu cần release file/socket/native resource đúng thời điểm, dùng `AutoCloseable`/`try-with-resources`. Đồ thị tham chiếu giúp phát hiện Cleaner action **vô tình mạnh giữ referent** khiến cleanup không thể chạy.

---

## 23. Những nhầm lẫn cần tránh

1. **“Hai objects trỏ vào nhau thì Java GC không thể thu.”** Sai; không còn root path thì cycle collectible.
2. **“Một biến `null` nghĩa object đã mất khỏi heap.”** Sai; có thể có references khác và chưa GC.
3. **“Object không còn được business cần nghĩa JVM sẽ coi là garbage.”** Sai; JVM dựa reachability, không đọc business intent.
4. **“GC tự biết cache entry đã hết hạn.”** Sai; phải có eviction/lifecycle policy.
5. **“Static field mãi mãi là GC root dù classloader có thể unload.”** Sai; class/lifecycle quan trọng.
6. **“Mỗi local trong source còn scope thì JIT phải giữ reference mạnh tới hết method.”** Không nhất thiết.
7. **“`System.gc()` luôn chạy full GC ngay lập tức.”** Sai.
8. **“Heap used không giảm sau gán null chứng minh GC bị lỗi.”** Sai.
9. **“Heap committed bằng heap live objects.”** Sai.
10. **“RSS bằng Java heap used.”** Sai.
11. **“Heap dump chỉ chứa objects còn reachable trong mọi capture mode.”** Sai; `-all` có thể đưa unreachable objects vào.
12. **“`GC.class_histogram` cung cấp Paths to GC Roots.”** Sai; chỉ counts/bytes theo class.
13. **“Shallow size của HashMap chứa luôn mọi byte[] values.”** Sai.
14. **“Retained size của mọi owner có thể cộng đơn giản.”** Sai; retained sets có thể lồng nhau.
15. **“Nếu A trỏ tới P thì A chắc dominate P.”** Sai; P có thể còn paths từ B.
16. **“Một shortest Path to GC Roots chứng minh không có path thứ hai.”** Sai.
17. **“Một object nhìn thấy trong heap dump là leak.”** Sai; có thể hợp lệ.
18. **“GC root path giải thích business requirement của object.”** Sai; đó là việc của code/lifecycle.
19. **“WeakReference giữ referent giống strong field.”** Sai.
20. **“WeakReference.get() sẽ null ngay sau khi xóa local.”** Sai.
21. **“SoftReference giữ object cố định tới khi RAM dưới N%.”** Không có portable threshold như vậy.
22. **“PhantomReference.get() trả object trước khi cleanup.”** Sai; luôn null.
23. **“ReferenceQueue sẽ giữ reference objects đã đăng ký sống mãi.”** Sai; application phải quản lý liveness của reference object.
24. **“WeakHashMap tự đảm bảo mọi key được GC kể cả values strong trỏ ngược key.”** Sai.
25. **“ThreadLocal key weak nghĩa values không bao giờ bị giữ sau request.”** Sai.
26. **“Bỏ listener registration không ảnh hưởng memory retention.”** Sai; listeners thường là root paths.
27. **“Queue đã nhận task thì payload chắc được GC nếu caller trả về.”** Sai; queue/task closures giữ refs.
28. **“Heap snapshot một mốc đủ chứng minh memory leak tăng đều.”** Sai; cần trend/workload.
29. **“Post-GC live set tăng luôn là leak, không thể là cache warm-up.”** Sai; cần domain context.
30. **“Allocation hotspot cũng chính là retained hotspot.”** Sai.
31. **“JIT không thể loại allocations vì source có `new`.”** Sai; compiler có tối ưu nếu giữ semantics.
32. **“Phải force GC để đồ thị BFS code có kết quả đúng.”** Sai; BFS chỉ mô hình hóa graph.
33. **“GraphReachabilityDemo chứng minh actual HotSpot GC đã chạy.”** Sai; là thuật toán tính reachability.
34. **“Một static cache entry bị remove chắc toàn bộ payload đã thu hồi ngay.”** Sai; root khác có thể giữ.
35. **“Một object có strong outgoing reference nghĩa chính nó được giữ từ root.”** Sai; hướng cạnh quan trọng.
36. **“Có strong cycle thì mặc định toàn bộ graph sống.”** Sai; phải có root path.
37. **“Object với nhiều incoming references chắc bị giữ mạnh.”** Chưa đủ; các incoming owners cũng có thể unreachable.
38. **“Dominator tree mô tả y nguyên mọi object field edge.”** Sai; đây là quan hệ dominance.
39. **“GC heap dump có overhead thấp nên thu vô hạn trên production.”** Sai; Oracle phân loại High impact.
40. **“File HPROF là dữ liệu an toàn để gửi công khai.”** Sai; có thể chứa tokens/PII.
41. **“Native RSS tăng chỉ cần xem heap dump là đủ.”** Sai; có native/metaspace/stacks/code cache.
42. **“Heap graph và Java object lifecycle hoàn toàn giống nhau ở mọi GC implementation.”** Semantics reachability có contract, nhưng snapshot roots/processing/timing khác.
43. **“Objects unreachable bắt buộc trở thành `null` trong `WeakReference.get()` theo lịch cố định.”** Sai.
44. **“Xóa field trong một object luôn làm mọi descendants unreachable.”** Sai; có alternate paths.
45. **“Một graph chỉ có nodes reachable thì không thể có memory leak.”** Sai; **leak Java chính là retained graph ngoài ý muốn**.
46. **“Bộ thu gom rác có thể tự suy object hết giá trị nghiệp vụ.”** Sai.
47. **“Tất cả GC roots đều tương ứng một Java static field.”** Sai.
48. **“Một snapshot MAT có thể tự chỉ ra bug cụ thể trong source mà không cần review.”** Sai; graph chỉ hỗ trợ suy luận.

---

## 24. Bài tập thực hành (có gợi ý đáp án)

**Bài 1 — Cycle độc lập.** Cho graph `A→B, B→C, C→A` và root chỉ tới `D`. A/B/C có collectible không?

**Đáp án:** Có, nếu không có root path nào khác tới component và xét strong reachability. Vòng không tự giữ cả component sống.

**Bài 2 — Cycle có root.** Thêm `D→A`. A/B/C thế nào?

**Đáp án:** Cả ba strongly reachable qua root → D → A → B → C. Xóa cạnh `B→C` làm C vẫn reachable? Với `C→A` và không có incoming khác tới C, **không**; C trở thành unreachable, còn A/B được giữ từ D.

**Bài 3 — Hai đường giữ object.** Roots R1/R2; R1→A→P và R2→B→P. Xóa A→P. P đã garbage chưa?

**Đáp án:** Chưa, vì còn đường R2→B→P.

**Bài 4 — Dominance.** Ở sơ đồ R1→A→P và R2→B→P, A có dominate P không?

**Đáp án:** Không, vì đường qua B không đi qua A. Một super-root nối R1/R2 mới là common dominator trong mô hình giản lược.

**Bài 5 — WeakReference.** ROOT→WeakReference W- -►X, không có strong path nào khác. W còn sống mạnh thì X có chắc còn sống mạnh không?

**Đáp án:** Không. Bản thân W reachable nhưng referent X không mạnh chỉ vì W. X có thể weakly reachable/clear tùy GC.

**Bài 6 — Static cache.** `static Map<String,Object> CACHE` giữ các session cũ. Tại sao GC không giải phóng chúng?

**Đáp án:** Vì cache (khi class state còn sống) tạo strong root paths tới sessions. Cần eviction/remove phù hợp và kiểm tra những owners khác.

**Bài 7 — WeakHashMap.** `WeakHashMap<Key,Value>`; Value chứa field `Key owner` trỏ tới đúng key. Vì sao key không tự bị xóa?

**Đáp án:** Map giữ value mạnh, value giữ key mạnh, nên key còn strong root path gián tiếp; weak key không đủ.

**Bài 8 — ThreadLocal.** Worker pool thread sống lâu, request context đặt vào ThreadLocal nhưng không remove. Cần sửa ở đâu?

**Đáp án:** Dùng `try/finally { ThreadLocal.remove(); }` ở request boundary, cùng lifecycle/context policy.

**Bài 9 — Heap histogram.** `[B` chiếm 2 GiB shallow bytes. Có thể kết luận owner leak là `byte[]` không?

**Đáp án:** Chưa. Cần dominator/root paths để biết owners và business expected lifetime.

**Bài 10 — Hai dumps.** Dump sau startup 500 MiB, sau peak 1,5 GiB. Có leak chưa?

**Đáp án:** Chưa chắc; có thể cache warm-up hoặc allocation phase. So post-GC live sets, workload tương đương và root graph.

**Bài 11 — Chương trình graph.** Trong `GraphReachabilityDemo`, sau khi xóa `CACHE→A`, vì sao A/B trở thành unreachable nhưng E vẫn reachable?

**Đáp án:** A/B không còn root path; E vẫn có `STACK→E`.

**Bài 12 — Collector timing.** Sau `cacheRoot=null`, object đã chắc được reclaim?

**Đáp án:** Không; root paths khác và GC scheduling khác. Code chỉ chứng minh field không còn giữ nó.

**Bài 13 — Cạnh từ root bị ngược chiều.** `A→ROOT` nhưng ROOT không trỏ tới A. A có reachable từ root nhờ cạnh đó không?

**Đáp án:** Không. Edges directed; GC reachability đi từ roots theo chiều outgoing references.

**Bài 14 — Giá trị của `reachabilityFence`.** Có phải nó ép GC thu object sau lời gọi?

**Đáp án:** Không. Nó giữ strong reachability tới sau invocation, không yêu cầu GC.

**Bài 15 — Retained size.** Vì sao retained size của cache manager có thể lớn hơn shallow size hàng nghìn lần?

**Đáp án:** Cache manager dominate một graph lớn các map entries, payloads và arrays. Shallow chỉ tính object chính nó.

**Bài 16 — HPROF bảo mật.** Có nên upload raw heap dump production vào issue tracker công khai để hỏi về leak?

**Đáp án:** Không; dữ liệu có thể chứa secrets/PII. Dùng công cụ được phê duyệt, quyền truy cập hạn chế và retention policy.

---

## 25. Checklist kiểm tra chất lượng sơ đồ và thực nghiệm

- [ ] Tôi đã ghi đúng **đối tượng/nodes**, **tên owners** và **chiều cạnh**.
- [ ] Tôi đã vẽ **tất cả roots liên quan**, không chỉ static field đầu tiên thấy được.
- [ ] Tôi đã phân biệt **root path strong** với soft/weak/phantom reference edges.
- [ ] Tôi đã kiểm tra **cycle có đường từ root hay không**.
- [ ] Tôi không dùng “đếm references” làm thay thuật toán reachability.
- [ ] Tôi không suy “`null` một local” nghĩa object được reclaim ngay.
- [ ] Tôi đã mô tả trước/sau thao tác cắt **edge cụ thể**.
- [ ] Tôi đã tìm các **alternate incoming/root paths**.
- [ ] Tôi phân biệt **garbage candidate**, **retained object**, **logical memory leak**, và **GC reclaim**.
- [ ] Tôi phân biệt shallow size, retained size và **dominator tree**.
- [ ] Tôi không cộng retained sizes của nested owner graphs như các tập rời nhau.
- [ ] Tôi biết `WeakReference.get()` có kết quả phụ thuộc GC ngoài đoạn deterministic đã bảo vệ bằng reachabilityFence.
- [ ] Tôi không coi `System.gc()` như một cách kiểm thử bắt buộc thành công.
- [ ] Tôi đã chọn đúng **PID/JDK/build/workload** trước khi lấy histogram/HPROF.
- [ ] Tôi đã đánh giá High-impact heap dump và disk/storage permissions.
- [ ] Tôi đã kiểm tra **Paths to GC Roots** và những weak/soft path filters của analyzer.
- [ ] Tôi đã đối chiếu retained graph với **business lifecycle** trước khi gọi leak.
- [ ] Tôi đã đo trước/sau cùng workload và chú ý post-GC baseline.
- [ ] Tôi đã bảo vệ heap dump, không lộ credentials/session data.
- [ ] Sau fix, tôi đã chạy lại test và xác nhận dữ liệu không bị giữ quá lâu.

## 26. Bản đồ quyết định: đối tượng có phải rác hay bị giữ?

```text
                 OBJECT X ĐANG NGHI VẤN
                            │
                            ▼
                 CÓ ROOT PATH MẠNH?
                            │
              ┌─────────────┴─────────────┐
              │                           │
             CÓ                         KHÔNG
              │                           │
              ▼                           ▼
      X strongly reachable        Kiểm tra soft/weak/
              │                   phantom reachability
              ▼                           │
   Business còn cần X không?                ▼
       │             │           Có thể được clear/
      CÓ           KHÔNG          reclaim theo GC
       │             │           nhưng KHÔNG có
       ▼             ▼            deadline thu hồi
  Retention     Logical leak
  hợp lệ?       đáng nghi
       │             │
       ▼             ▼
   Giới hạn       Xác định owner
   /TTL/cost       /dominator path
       │             │
       └──────┬──────┘
              ▼
        VẼ GRAPH SAU FIX
              │
              ▼
   Kiểm tra root path còn lại?
              │
              ▼
   Đo post-GC live set và heap dump khi cần
```

**Điểm nhớ:** Trong Java, “rác” không có nghĩa “không ai gọi method nữa” và “memory leak” không có nghĩa “GC không biết thu các vòng”. Hãy **bắt đầu từ roots, theo chiều mạnh của các cạnh**, tìm toàn bộ đường tới suspect object, rồi so **lifecycle nghiệp vụ** với đồ thị đã vẽ. Một cycle hoàn toàn không còn root path có thể được tracing GC xử lý. Một cached object vẫn reachable là bộ nhớ đang được giữ, hợp lý hay leak tùy chính sách. **Dominator/retained size** giúp chỉ ra owner nào đang giữ cả graph; **histogram** chỉ cho biết nhiều instances/bytes, **heap dump và Paths to GC Roots** mới giúp trả lời “ai giữ?”. **Không dùng thời điểm `System.gc()` hay RSS giảm làm phép chứng minh reachability**.

### Tài liệu chính thức và công cụ phân tích (Java 17)

- [Oracle Java SE 17 — `java.lang.ref` package](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/package-summary.html): strong/soft/weak/phantom reachability, reference queues.
- [Oracle Java SE 17 — `Reference.reachabilityFence`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html): strong reachability guarantee, không trigger GC.
- [Oracle Java SE 17 — `WeakReference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html): weak referent lifecycle.
- [Oracle Java SE 17 — `WeakHashMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html): weak keys, values strong và bẫy value→key.
- [Oracle Java SE 17 — `Cleaner`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Cleaner.html): phantom-based cleanup, tránh capture referent.
- [Oracle JDK 17 — `jcmd`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `GC.heap_dump`/`GC.class_histogram` impact và tùy chọn `-all`.
- [Oracle JDK 17 — Troubleshooting Memory Leaks](https://docs.oracle.com/en/java/javase/17/troubleshoot/troubleshooting-memory-leaks.html): heap dumps, GC/JFR và retained objects.
- [Eclipse Memory Analyzer — Basic Tutorial](https://help.eclipse.org/latest/topic/org.eclipse.mat.ui.help/gettingstarted/basictutorial.html): histogram, dominator, Paths to GC Roots.
- [Eclipse Memory Analyzer — Dominator Tree](https://help.eclipse.org/latest/topic/org.eclipse.mat.ui.help/concepts/dominatortree.html): dominator definition và retained subtrees.
- [Eclipse Memory Analyzer — Finding Memory Leak](https://help.eclipse.org/latest/topic/org.eclipse.mat.ui.help/reference/findingmemoryleak.html): investigation flow và leak suspects.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-experiments-006">So sánh mẫu cấp phát sống ngắn và giữ lâu bằng log GC</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh mẫu cấp phát sống ngắn và giữ lâu bằng log GC

## 1. Mục tiêu của thí nghiệm

Một ứng dụng Java có thể tạo ra **rất nhiều objects** nhưng vẫn hoạt động tốt nếu phần lớn chúng **nhanh chóng không còn reachable**. Ngược lại, một ứng dụng tạo cùng tổng lượng dữ liệu nhưng **giữ tham chiếu lâu** có thể gây áp lực lớn hơn lên heap sau GC, survivor spaces, old generation, tần suất thu gom và cuối cùng là throughput/latency.

Bài thực nghiệm so sánh hai workload kiểm soát được:

- **SHORT (sống ngắn):** cấp phát nhiều mảng byte, chủ yếu bỏ tham chiếu sau mỗi vòng lặp.
- **RETAINED (giữ lâu):** cấp phát **cùng số mảng, cùng kích thước**, nhưng giữ lại một tỷ lệ mảng bằng một danh sách còn reachable tới cuối chương trình.

Ta dùng **HotSpot Unified GC Logging** để trả lời:

1. Tổng dung lượng được yêu cầu cấp phát có giống nhau không?
2. Sau những lần GC, lượng bộ nhớ còn sống khác nhau như thế nào?
3. Với **Serial GC**, vùng **Tenured/old generation** tăng khác nhau ra sao?
4. Vì sao có thể có **cùng số Young GC** nhưng old occupancy rất khác?
5. Điều gì có thể suy ra từ log và điều gì **không** được khẳng định?
6. Kết quả có lặp lại trên G1, heap size khác, JDK khác hoặc khi JIT tối ưu không?

**Đích học tập không phải tạo OOME hoặc khiến Full GC xuất hiện cho bằng được.** Đích là đọc được sự khác nhau giữa **allocation volume**, **object lifetime**, **survival/promotion** và **memory retained**.

### Hình dung trực quan

```text
                2,400 allocations × 128 KiB
                         = 300 MiB
                            |
              +-------------+-------------+
              |                           |
              v                           v
          SHORT                       RETAINED
  phần lớn sớm chết          cứ 12 mảng giữ 1 mảng
              |                           |
     GC có thể thu hồi       200 mảng còn reachable
       ở young region          ~25 MiB byte capacity
              |                           |
  old/tenured thường         nhiều mảng sống qua GC
     tăng ít hơn                rồi có thể được promote
              |                           |
  GC log Tenured thấp        GC log Tenured tăng dần

  Cùng allocated bytes ≠ cùng live bytes ≠ cùng GC cost
```

**Phạm vi:** Java 17+, diễn giải cụ thể của **HotSpot Serial GC** để đơn giản hóa việc đọc log. Sau đó nêu cách quan sát với G1 mà không đánh đồng tên regions giữa hai collectors.

## 2. Các khái niệm phải tách biệt

| Khái niệm | Ý nghĩa | Không được nhầm thành |
| --- | --- | --- |
| **Allocation** | JVM cung cấp không gian cho object/array mới | Object sẽ sống lâu |
| **Allocation rate** | Bytes được cấp phát trong một đơn vị thời gian | Heap đang sử dụng tại một thời điểm |
| **Reachability** | Object có thể được truy cập từ tập GC roots theo semantics runtime | Phạm vi lexical của biến trong source |
| **Live set** | Các objects còn được coi là sống tại một mốc thu gom | Tổng bytes từng cấp phát từ đầu process |
| **Young generation** | Vùng phục vụ các allocations mới theo collector có generations | Mọi object ở đây chắc chắn còn trẻ về thời gian thực |
| **Survivor** | Object sống qua một hay nhiều lần young collection | Object bắt buộc đã lên old generation |
| **Promotion / tenuring** | Di chuyển/xếp object vào old generation theo policy GC | Memory leak |
| **Old/Tenured occupancy** | Dung lượng vùng old đang dùng | Tổng bytes cấp phát trong toàn bộ ứng dụng |
| **GC pause** | Quãng thời gian GC phase tạm dừng application threads | Toàn bộ elapsed time của chương trình |
| **Retention** | Một object graph còn được giữ reachable | Luôn là bug |

Ví dụ một cache giữ 25 MiB để tái sử dụng có thể là thiết kế đúng. Chỉ khi lượng giữ lại **không còn cần thiết** hoặc tăng không giới hạn, ta mới có cơ sở điều tra leak.

### 2.1. GC không thu gom theo thời gian sống đếm bằng đồng hồ

Một mảng được tạo 1 giây trước không “đương nhiên bị thu” khi hết 1 giây. GC xét **reachability tại collection**, cùng trạng thái generational và policy. Một object tồn tại 10 ms nhưng sống qua nhiều young GCs có thể được promote; object tồn tại 5 phút vẫn có thể chưa cần move nếu collector không thực hiện collection liên quan trong khoảng ấy.

### 2.2. Biến cục bộ rời scope không là lệnh `free`

GC có thể thu hồi object **khi không còn được coi là reachable**, theo semantics và tối ưu của JVM. Cú pháp đóng dấu `}` không phải lệnh gọi bộ thu gom. **Đừng gọi `System.gc()` sau mỗi vòng lặp** vì làm thí nghiệm không còn đại diện cho GC tự nhiên.

### 2.3. Live set không bắt buộc bằng toàn bộ bytes trong danh sách

Dữ liệu mảng có **headers/alignment** và danh sách có backing array/reference slots. Ngoài ra process có JDK objects, class metadata liên quan, threads, JIT runtime state. Vì vậy giữ 25 MiB **payload capacity** không có nghĩa heap used chính xác 25,000 KiB hay old generation đúng 25 MiB.

## 3. Generational hypothesis: vì sao hai mẫu có thể khác nhau?

Nhiều HotSpot collectors sử dụng giả thuyết thực nghiệm rằng **phần lớn objects chết trẻ**. Vì vậy thế hệ trẻ (young generation) là nơi GC thường tập trung thu hồi objects mới.

Một mô hình Serial GC giản lược:

```text
           Allocation into Eden
                    |
               Young GC
                    |
         +----------+----------+
         |                     |
         v                     v
   Object unreachable     Object still live
         |                     |
     Reclaim bytes       Copy to survivor,
                         or promote to old
                               |
                        Next young GC(s)
                               |
                          Possible promotion
                               |
                         Old / Tenured area
```

Quyết định object ở survivor hay promote phụ thuộc **age threshold, survivor capacity, available space, object size và GC ergonomics**. Không có quy luật Java Specification rằng mọi object phải trải qua đúng N collections trước khi vào old.

### 3.1. Đối với SHORT

Nếu mỗi mảng chỉ cần thiết trong một vòng lặp, khi xảy ra young GC phần lớn mảng đã tạo trước đó có thể là garbage. Bộ thu gom thu hồi bộ nhớ này; lượng dữ liệu cần copy/promote tương đối nhỏ.

### 3.2. Đối với RETAINED

Một số mảng được giữ bằng `static final List<byte[]>`. Danh sách reachable thông qua static field nên các phần tử mảng có strong reference; chúng có thể sống qua nhiều young collections và chuyển sang Tenured.

### 3.3. GC log là bằng chứng về hành vi VM, không phải máy đo “ý định source”

Đọc source cho biết chủ đích giữ tham chiếu; log xác nhận **JVM thực sự ghi nhận heap/GC state như thế nào**. Tuy nhiên log tổng hợp không tự chỉ ra chính xác mảng nào đã được promote — muốn biết object-level retention paths cần profiler/heap dump hay công cụ tương ứng, có overhead riêng.

## 4. Thiết kế thí nghiệm công bằng

Hai runs phải giữ cố định:

- **Chính class file/JAR** được biên dịch một lần.
- **JDK vendor/version/build**, operating system và architecture.
- **`-Xms64m -Xmx64m`**.
- **`-XX:+UseSerialGC`** để tên vùng GC đơn giản và ổn định hơn.
- **2.400 lần cấp phát**, mỗi lần tạo mảng **128 KiB**.
- Cùng logic đọc/ghi mảng và checksum.
- Cùng GC logging options/decorators.
- Cùng JVM process lifecycle; mỗi mode chạy trong **process mới**.
- Không thêm `System.gc()`, JFR/heap dump hoặc profiler chỉ vào một mode.

**Biến độc lập duy nhất trong application logic là chính sách giữ mảng:** SHORT không thêm vào list, RETAINED giữ một mảng sau mỗi 12 lần tạo.

### Bảng đối chứng dự kiến

| Thông số | SHORT | RETAINED |
| --- | ---: | ---: |
| Số mảng tạo | 2.400 | 2.400 |
| Payload mỗi mảng | 128 KiB | 128 KiB |
| Tổng payload yêu cầu | 300 MiB | 300 MiB |
| Tỷ lệ lưu trong list | 0% | 1/12 ≈ 8,33% |
| Số references trong list khi kết thúc | 0 | 200 |
| Capacity mảng được list giữ | 0 MiB | 25 MiB |
| Mảng mới nhất được giữ qua volatile field | Có | Có |
| JVM heap / collector | 64 MiB / Serial | 64 MiB / Serial |

**Lưu ý quan trọng:** SHORT không đồng nghĩa toàn heap còn 0 byte. Demo còn giữ **mảng cuối cùng qua `mostRecent`**, class/runtime objects và các cấu trúc chung. “0 MiB retained” trong stdout chỉ là **dung lượng được giữ bởi danh sách `retained`**, không phải toàn live heap.

## 5. Một chương trình Java 17+ chạy được

Tạo file **`AllocationLifetimeLab.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public final class AllocationLifetimeLab {
    private static final int BLOCK_BYTES = 128 * 1024;
    private static final int ITERATIONS = 2_400;
    private static final int KEEP_EVERY = 12;

    private static final List<byte[]> retained =
            new ArrayList<>();

    private static volatile byte[] mostRecent;
    private static volatile long checksumSink;

    public static void main(String[] args) {
        if (args.length != 1
                || (!args[0].equals("short")
                    && !args[0].equals("retained"))) {
            System.err.println(
                    "Usage: java AllocationLifetimeLab short|retained");
            System.exit(2);
        }

        boolean keep = args[0].equals("retained");
        long checksum = 0;

        for (int i = 0; i < ITERATIONS; i++) {
            byte[] block = new byte[BLOCK_BYTES];
            block[0] = (byte) i;
            block[BLOCK_BYTES - 1] = (byte) (i >>> 8);

            // Một mảng mới nhất vẫn reachable ở cả hai mode.
            mostRecent = block;

            // Cùng code tính toán ở cả hai mode.
            checksum += (block[0] & 0xff)
                    + (block[BLOCK_BYTES - 1] & 0xff);

            if (keep && i % KEEP_EVERY == 0) {
                // Chỉ mode retained có các references lâu dài.
                retained.add(block);
            }
        }

        checksumSink = checksum;

        System.out.println("mode=" + args[0]);
        System.out.println("iterations=" + ITERATIONS);
        System.out.println("requestedMiB="
                + ((long) ITERATIONS * BLOCK_BYTES / (1024 * 1024)));
        System.out.println("retainedReferences=" + retained.size());
        System.out.println("retainedCapacityMiB="
                + ((long) retained.size() * BLOCK_BYTES / (1024 * 1024)));
        System.out.println("checksum=" + checksumSink);
        System.out.println("lastByte="
                + (mostRecent[BLOCK_BYTES - 1] & 0xff));
    }
}
```

### 5.1. Điểm thiết kế cần hiểu

- `BLOCK_BYTES = 128 * 1024`: mỗi mảng chứa đúng **131.072 bytes payload**.
- `ITERATIONS = 2_400`: có 2.400 requests cấp phát mảng trong source loop.
- `KEEP_EVERY = 12`: mode RETAINED giữ indices 0, 12, 24, ... 2.388, tức **200 mảng**.
- `retained` là một static final list, có thể giữ references tới cuối `main`.
- `mostRecent` là **volatile reference**, được gán mỗi lần. Nó giúp biến allocation có observable effect và giữ đúng một block gần nhất; **không biến mọi block thành sống lâu**.
- `checksumSink` là volatile long giữ kết quả tính toán có thể quan sát.
- Mảng được ghi ở đầu/cuối và được đọc lại, để việc cấp phát thực sự tham gia vào observable behavior.
- Chương trình **không yêu cầu GC** và **không dùng `Thread.sleep`** để ép thời điểm.

### 5.2. Tại sao dùng `byte[]` lớn?

Mảng byte cho phép tính rõ payload capacity và tạo đủ áp lực cấp phát để GC events xuất hiện trong heap 64 MiB. Điều này **không đại diện** cho mọi loại object của ứng dụng: kích thước headers, alignment, references, object layouts và allocator tối ưu khác nhau theo VM.

### 5.3. Vì sao không dùng vòng tạo `new Object()` rỗng?

Một object ngắn hạn không escape có thể bị JIT tối ưu/loại bỏ allocation nhờ escape analysis hoặc scalar replacement. Với mảng 128 KiB được ghi/đọc và xuất hiện ở volatile field, ví dụ có tín hiệu cấp phát thực tế rõ hơn. Tuy nhiên **đừng khẳng định mọi JIT mode/build luôn không có bất kỳ optimization nào**; ta dùng log để kiểm chứng behavior, không chỉ đọc source.

## 6. Biên dịch, chạy và kiểm tra stdout

Dùng **OpenJDK hoặc Oracle JDK 17+** với tools `javac` và `java`.

Biên dịch:

```bash
javac --release 17 AllocationLifetimeLab.java
```

Run 1:

```bash
java AllocationLifetimeLab short
```

**Stdout logic chính xác (không bật GC logging):**

```text
mode=short
iterations=2400
requestedMiB=300
retainedReferences=0
retainedCapacityMiB=0
checksum=308400
lastByte=9
```

Run 2:

```bash
java AllocationLifetimeLab retained
```

**Stdout logic chính xác:**

```text
mode=retained
iterations=2400
requestedMiB=300
retainedReferences=200
retainedCapacityMiB=25
checksum=308400
lastByte=9
```

Đây là các output đã **biên dịch bằng `javac --release 17` và kiểm chứng khi chạy trên một HotSpot OpenJDK 24.0.2**. Mã sử dụng Java 17 APIs/language features; **không tuyên bố đã thử trên binary JDK 17 cụ thể**.

### 6.1. Phép tính 300 MiB

`2.400 × 128 KiB = 307.200 KiB = 300 MiB`. Đây là **payload byte capacities yêu cầu tạo**, chưa tính object headers, references, GC bookkeeping và những allocations nhỏ của `ArrayList`.

### 6.2. Phép tính 25 MiB

`200 × 128 KiB = 25.600 KiB = 25 MiB`. Trong RETAINED, list giữ 200 array references; đây là dung lượng payload, không phải exact old-generation used bytes.

### 6.3. Vì sao checksum giống nhau?

Cả hai modes dùng **cùng vòng lặp, cùng bytes và cùng phép cộng**, chỉ khác việc list giữ references. `checksum=308400` giúp phát hiện việc sửa code/vòng lặp vô tình làm workload không còn tương đương.

## 7. Bật GC log theo cú pháp JDK 17

Chạy SHORT:

```bash
java -Xms64m -Xmx64m -XX:+UseSerialGC \
     -Xlog:gc*,gc+heap=debug:file=short.log:uptime,level,tags:filecount=1,filesize=15m \
     AllocationLifetimeLab short
```

Chạy RETAINED:

```bash
java -Xms64m -Xmx64m -XX:+UseSerialGC \
     -Xlog:gc*,gc+heap=debug:file=retained.log:uptime,level,tags:filecount=1,filesize=15m \
     AllocationLifetimeLab retained
```

Dấu `\` nối dòng dành cho **Bash/POSIX shell**. Trong PowerShell, viết **một dòng mỗi command**:

```powershell
java -Xms64m -Xmx64m -XX:+UseSerialGC "-Xlog:gc*,gc+heap=debug:file=short.log:uptime,level,tags:filecount=1,filesize=15m" AllocationLifetimeLab short
java -Xms64m -Xmx64m -XX:+UseSerialGC "-Xlog:gc*,gc+heap=debug:file=retained.log:uptime,level,tags:filecount=1,filesize=15m" AllocationLifetimeLab retained
```

### 7.1. Giải thích từng phần

- `-Xms64m -Xmx64m`: giữ initial và maximum heap cùng 64 MiB trong cả hai runs.
- `-XX:+UseSerialGC`: ép HotSpot dùng Serial collector để dễ đọc `DefNew` và `Tenured`.
- `-Xlog:gc*`: bật các tổ hợp log tags bắt đầu/bao gồm GC theo Unified Logging.
- `gc+heap=debug`: bổ sung details về heap usage trước/sau collection.
- `file=short.log` hoặc `file=retained.log`: ghi JVM logs ra file, tách khỏi stdout ứng dụng.
- `uptime,level,tags`: bổ sung thời gian tính từ VM start, severity và log tags.
- `filecount=1,filesize=15m`: đặt file rotation budget nhỏ cho thí nghiệm. Trên JDK, rotation size là **mục tiêu xấp xỉ**, không phải giới hạn cứng tuyệt đối và có thể overwrite log cũ cùng tên.

**Chọn hai tên file khác nhau để không ghi đè chéo.** Lưu ý chạy lại cùng tên file vẫn có thể overwrite logs đã có; đổi thư mục/tên nếu muốn giữ các lần chạy để so sánh.

### 7.2. Những flags cũ cần tránh

`-XX:+PrintGCDetails` và `-XX:+PrintGCTimeStamps` là cú pháp cũ; với JDK 17 nên ưu tiên Unified Logging `-Xlog`. Tài liệu JDK 17 có mapping/một số alias, nhưng không dựa vào các legacy flags để dạy cách thu log hiện đại.

### 7.3. Không phải mọi dòng GC log luôn xuất hiện

Nếu một run không tạo enough memory pressure, JVM có thể không cần GC trong khoảng đo. Với heap 64 MiB và 300 MiB allocations trong ví dụ, các lần chạy thử đã có nhiều Young GCs; **số lần/timestamps vẫn không được bảo đảm cố định**.

## 8. Đọc cấu trúc GC log của Serial GC

Một nhóm dòng trong log thường có dạng:

```text
[0.037s][info ][gc,start] GC(0) Pause Young (Allocation Failure)
[0.037s][info ][gc,heap ] GC(0) DefNew: ...K(...K)->...K(...K)
[0.037s][info ][gc,heap ] GC(0) Tenured: 1136K(43712K)->1136K(43712K)
[0.037s][info ][gc      ] GC(0) Pause Young (Allocation Failure) ...M->...M(64M) ...ms
```

Đây là **mẫu rút gọn**. Timestamps, region capacities, heap before/after, durations và số GC IDs phụ thuộc runtime. Nhận diện các thành phần:

| Thành phần | Ý nghĩa |
| --- | --- |
| `[0.037s]` | Thời gian tính từ JVM start, **không** phải duration GC |
| `[info][gc,start]` | GC event bắt đầu |
| `GC(0)` | ID nhóm log lines thuộc một GC event |
| `Pause Young (Allocation Failure)` | Một young collection do allocation pressure |
| `DefNew: A->B` | Young generation used trước/sau |
| `Tenured: C->D` | Old generation used trước/sau |
| `...M->...M(64M)` | Tổng heap used trước/sau, capacity |
| `...ms` | Elapsed GC pause duration được VM báo |

### 8.1. Đọc “trước → sau” đúng

`Tenured: 1221K(43712K)->1221K(43712K)` có nghĩa **used old generation** không đổi trong event đó (ở độ phân giải KiB của log).

`Tenured: 22858K(43712K)->24266K(43712K)` có nghĩa old used **tăng** trong event; có thể do promotion từ young generation dù collector vừa thu garbage ở young.

### 8.2. GC giảm tổng used nhưng Tenured vẫn tăng

Young collection có thể thu hồi nhiều dead objects và đồng thời promote survivors; do đó:

- **Young used sau GC giảm mạnh**.
- **Old used sau GC có thể tăng**.
- **Total heap used sau GC có thể thấp hơn trước GC**.

Không có mâu thuẫn ở đây. Đọc riêng old occupancy và tổng heap thay vì chỉ thấy “GC reclaimed bytes”.

### 8.3. KiB và MiB

`K`/`M` trong logs thường biểu thị kích thước theo đơn vị binary 1024 trong HotSpot output. Dung lượng `24266K` tương đương khoảng **23,70 MiB** old used, không phải số retained capacity chính xác.

## 9. Kết quả kiểm chứng thực tế

Hai runs đã được thực hiện với **cùng Java source/class file, same JVM build**, các JVM options kể trên và mỗi mode trong một process mới trên **OpenJDK HotSpot 24.0.2**.

| Chỉ số trong lần chạy thử | SHORT | RETAINED |
| --- | ---: | ---: |
| Java payload được yêu cầu cấp phát | 300 MiB | 300 MiB |
| References giữ trong list khi kết thúc | 0 | 200 |
| List-retained payload capacity | 0 MiB | 25 MiB |
| `checksum` | 308400 | 308400 |
| Young GC events hoàn tất | 17 | 17 |
| Full GC events hoàn tất | 0 | 0 |
| Tenured used ở log GC cuối | khoảng 1.192 KiB | khoảng 24.266 KiB |
| GC log file size | khoảng 48 KiB | khoảng 48 KiB |

**Các GC counts, tenured occupancy và file sizes chỉ là SAMPLE của một lần chạy, không phải output bắt buộc của các flags trên mọi máy/JDK.** Có thể có variation do timing, JIT, object alignment, GC ergonomics, platform và load bên ngoài.

### 9.1. Minh họa hai dòng Tenured cuối

SHORT:

```text
... GC(16) Tenured: 1221K(43712K)->1221K(43712K)
```

RETAINED:

```text
... GC(16) Tenured: 22858K(43712K)->24266K(43712K)
```

Kết quả trên cùng hướng với thiết kế: workload giữ tham chiếu tạo **old-generation pressure** lớn hơn, dù tổng allocation volume bằng nhau.

### 9.2. Hai runs có 17 Young GCs: có đáng ngạc nhiên?

Không. Với khoảng 300 MiB được tạo, kích thước young generation tương tự và kích thước mỗi mảng không đổi, số young collections có thể khá giống nhau. Nhưng **survivor/promotion bytes và old occupancy** là điều khác biệt lớn. GC count bằng nhau **không** chứng minh heap workload hay total GC cost giống nhau.

### 9.3. Vì sao RETAINED không sinh Full GC trong thử nghiệm?

Heap 64 MiB vẫn có đủ capacity cho 25 MiB retained payload cộng runtime/working set dưới điều kiện thực tế. Không chạm old capacity đến mức cần Full GC. **Retention cao hơn không bắt buộc đồng nghĩa Full GC/OOME ngay**.

### 9.4. Kết luận nào không thể suy?

Không được suy:

- “Mọi Java 17 VM sẽ có chính xác 17 Young GCs.”
- “Giữ 25 MiB chắc làm Tenured tăng đúng 25 MiB.”
- “SHORT không có object sống lâu nào.”
- “RETAINED là memory leak.”
- “Không có Full GC nghĩa không có GC pressure.”
- “Bộ nhớ 25 MiB chắc nằm hoàn toàn trong Tenured ở mọi collector.”
- “Một log đủ dự đoán p99 latency production.”

## 10. Đọc file log theo bước

Trên Linux/macOS với `grep`:

```bash
grep -E "Pause Young|Pause Full|Tenured:" short.log
grep -E "Pause Young|Pause Full|Tenured:" retained.log
```

Trên PowerShell:

```powershell
Select-String -Path short.log -Pattern "Pause Young|Pause Full|Tenured:"
Select-String -Path retained.log -Pattern "Pause Young|Pause Full|Tenured:"
```

Một bảng ghi chép khi phân tích:

| Metric | Cách lấy / cách hiểu |
| --- | --- |
| Number of young GC events | Đếm **GC IDs hoàn tất** của `Pause Young`, không đếm cả start và completion thành 2 |
| Number of full GC events | Tìm `Pause Full`, gắn đúng GC ID/cause |
| Old used trước/sau | Các dòng `Tenured: A->B` khi dùng Serial GC |
| Young used trước/sau | Các dòng `DefNew: A->B` |
| Total heap after-GC | Dòng tổng `A->B(capacity)` |
| GC pause distribution | Dòng completion có duration `...ms` |
| Allocation pressure | Correlate số requested bytes và GC events; log không tự cho exact alloc-bytes per object |
| Promotion | Quan sát old occupancy increase/age logs, cần lưu ý concurrent/old collection effects |

### 10.1. Không đếm events bằng số dòng `Pause Young` đơn thuần

Unified Logging có thể in **một dòng bắt đầu** và **một dòng kết thúc** của cùng `GC(n)`. Nếu bạn đếm cả hai sẽ **gấp đôi số collections**. Hãy lọc completion lines hoặc group theo GC IDs và loại event.

### 10.2. Dùng GC ID để ghép đúng before/after

Nếu nhiều dòng `GC(7)` chứa DefNew/Tenured/total memory, chúng thuộc cùng GC event. Không lấy Tenured ở `GC(7)` so với duration của `GC(8)` để suy promotion cost.

### 10.3. Đừng diễn giải một line Tenured như exact retained list size

Tenured còn có nhiều JVM/application objects khác. Một số mảng list-retained có thể vẫn ở survivor spaces, và mảng cuối qua `mostRecent` có thể nằm ở young. Overhead/alignment và GC timing cũng ảnh hưởng.

## 11. Một chỉ số quan trọng: allocation rate khác GC rate

Hai modes đều có **300 MiB tổng requested payload**, nhưng allocation **rate** cần một mốc thời gian:

\[
\text{requested allocation rate} \approx
\frac{300\ \text{MiB}}{\text{elapsed seconds of allocation loop}}
\]

Không lấy `300 MiB / toàn process wall time` rồi coi là exact application allocation rate; thời gian JVM launch, JIT, GC và stdout nằm ngoài hoặc xen kẽ loop. Thêm timer quanh loop nếu muốn nghiên cứu riêng, nhưng điều đó vẫn bao gồm GC pauses diễn ra trong loop và không thay thế JFR allocation profiling.

### 11.1. Allocation rate cao chưa đủ kết luận xấu

Một request handler tạo nhiều temporary strings/buffers rồi loại bỏ nhanh có thể gây **frequent young GCs** nhưng throughput vẫn tốt. Cần xem GC pause time fraction và SLO, không chỉ bytes/s.

### 11.2. Live set cao cũng không luôn là leak

Một in-memory cache hoặc precomputed table giữ 25 MiB có thể là chi phí cố ý để giảm latency. Cần kiểm tra lý do giữ, kích thước giới hạn và behavior sau release.

### 11.3. Allocation pressure và retention pressure độc lập phần nào

Mức cấp phát quyết định tốc độ lấp young generation; retention quyết định bao nhiêu objects phải copy/survive/promote, có thể đẩy pressure sang old generation. Hai trục khác nhau và cần được đo riêng.

## 12. Survivor age và tenuring threshold

Ở collectors có generations, object sống qua young GCs có thể được copy qua survivor spaces và về sau promoted.

Một phần GC logs có thể bật bằng:

```bash
java -Xms64m -Xmx64m -XX:+UseSerialGC \
     -Xlog:gc*,gc+age=trace:file=age-retained.log:uptime,level,tags:filecount=1,filesize=15m \
     AllocationLifetimeLab retained
```

Log age/threshold giúp quan sát survivor age distribution theo HotSpot build.

**Không hứa rằng every retained array sống đúng 15 collections mới được promote.** Threshold có thể thích ứng, survivor space pressure có thể khiến promotion sớm, và không phải mọi collector dùng cơ chế/nhãn log giống nhau.

### 12.1. Mảng lớn có được phân bổ ngay vào old?

Trong một số JVM/GC configurations, large objects có thể có allocation path khác normal small objects. Trong ví dụ Serial heap 64 MiB, 128 KiB arrays và các cờ đã kiểm tra, log xác nhận old occupancy behavior, nhưng **đừng khái quát mọi collectors đều xử lý 128 KiB array y hệt**.

### 12.2. Hãy nhìn old occupancy trước khi suy từ object age

Age histogram cho thêm manh mối, nhưng muốn biết số bytes đã promote trong một event phải đọc phù hợp với collector, và phân biệt net old occupancy change với gross promoted bytes khi old reclamation có thể xen vào.

## 13. Dùng G1 thì log cần đọc theo từ vựng khác

Thực hiện đối chứng **mới**, giữ các điều kiện khác nhất quán và chỉ đổi collector:

```bash
java -Xms64m -Xmx64m -XX:+UseG1GC \
     -Xlog:gc*,gc+heap=info:file=g1-short.log:uptime,level,tags:filecount=1,filesize=15m \
     AllocationLifetimeLab short
```

```bash
java -Xms64m -Xmx64m -XX:+UseG1GC \
     -Xlog:gc*,gc+heap=info:file=g1-retained.log:uptime,level,tags:filecount=1,filesize=15m \
     AllocationLifetimeLab retained
```

G1 thường báo **Eden regions**, **Survivor regions**, **Old regions**, và có thể có **Humongous regions**. Không tìm `Tenured:` rồi kết luận “G1 không có old generation”; tên log khác nhau.

### 13.1. G1 region-based young/old

```text
G1 heap (region-based)
   +-- Eden regions
   +-- Survivor regions
   +-- Old regions
   +-- Humongous regions (khi có)
```

Khi đổi GC, **số young events và promotion/region usage có thể thay đổi**. Chỉ so xu hướng short vs retained **trong cùng collector**, sau đó mới phân tích trade-offs giữa Serial và G1.

### 13.2. Humongous allocation là một confounder

Ở G1, object lớn hơn một tỷ lệ nhất định của region size (thường khoảng nửa region) có thể được phân loại **humongous**. Nếu thay 128 KiB thành một mảng vài MiB, bạn có thể đo humongous allocation behavior thay vì mô hình young-survivor-promotion đơn giản.

### 13.3. Không dùng số old regions như bytes trực tiếp

Một region có size do G1 chọn. Muốn diễn giải old bytes, cần region size và cách log báo occupancy; `Old regions: 20->25` không phải 20→25 MiB trừ khi đã xác nhận region size đúng 1 MiB và accounting tương ứng.

## 14. Tại sao không cố ép Full GC trong bài?

Một cách nhiều người dùng khi học GC:

```java
System.gc();
```

Nhưng `System.gc()` chỉ là **request**, không có portable guarantee một Full GC synchronous sẽ chạy ngay; HotSpot flags/collector quyết định behavior. Việc thêm request này vào một hoặc cả hai modes làm:

- Thay đổi GC schedule một cách nhân tạo.
- Có thể làm objects được thu hoặc promote khác.
- Tăng pause times không phản ánh workload tự nhiên.
- Khiến so sánh Young GC/old occupancy mất tính đại diện.
- Che mất hiện tượng retention ở thời điểm trước GC.

Thí nghiệm chính không gọi `System.gc()`. Nếu làm thử riêng với explicit GC, phải tạo **third treatment group**, ghi options và không đem số đo so như cùng phương pháp với original two groups.

## 15. Làm sao giữ điều kiện so sánh công bằng hơn?

Một số nguồn nhiễu:

- **JIT warm-up:** các methods được biên dịch ở thời điểm khác nhau.
- **JDK build:** compiler/GC heuristics và logging details thay đổi.
- **Heap ergonomics:** flags khác ngoài `Xms/Xmx` có thể ảnh hưởng young/survivor capacities.
- **OS CPU scheduling:** load ngoài tiến trình gây variance.
- **Filesystem cache/log I/O:** đặc biệt trên storage chậm.
- **Automatic GC timing:** application chạy quá ngắn tạo variation.
- **Allocation optimization:** JIT có thể thay biểu diễn một số objects.
- **GC logging level:** debug/trace có overhead khác info.
- **Collections không đồng bộ:** đặc biệt với concurrent collectors.

### 15.1. Lặp nhiều lần, đừng lấy một run làm chuẩn

Chạy luân phiên SHORT/RETAINED nhiều lần dưới cùng điều kiện. Báo **median, min/max/spread và số GC events**. Nếu các counts dao động, đó là dữ kiện cần giải thích, không phải test phải “fix lại cho đúng 17”.

### 15.2. Không bật profiler chỉ ở một mode

Nếu mode RETAINED bật JFR profile còn SHORT không bật, CPU/memory/timing khác nhau một phần do instrumentation. Các diagnostics phụ trợ nên được thu theo chính sách tương đương hoặc trong riêng các diagnostic runs.

### 15.3. Nên ghi phiên bản JVM trong báo cáo

```bash
java -version
java -XX:+PrintFlagsFinal -version
```

Tối thiểu ghi GC/heap options, JDK version, OS và CPU quota để người khác tái lập.

## 16. Phân biệt retention với leak

Trong bài, list giữ 200 mảng tới cuối chương trình. Đây là **deliberate retention**. Nó chứng minh GC không tự xóa object còn reachable, **không chứng minh ứng dụng bị leak**.

Leak là khi object graph **bị giữ ngoài ý định hoặc ngoài lifecycle cần thiết**, làm memory tăng không phù hợp, chẳng hạn:

- Cache không giới hạn keys/eviction.
- Static list giữ mọi request.
- Listener/subscriber không unregister.
- Class loader cũ bị references giữ sau hot redeploy.
- ThreadLocal values còn giữ dữ liệu không còn cần.
- Queue backlog tăng vô hạn vì producer nhanh hơn consumer.

### 16.1. Memory leak Java khác leak native

Một Java heap leak thường thể hiện live set tăng sau GC. Native leak từ JNI/direct memory có thể làm RSS tăng mà heap/GC logs trông bình thường. Không kết luận “không leak” chỉ vì Serial Tenured ổn định trong một bài thử.

### 16.2. Phân tích retention path khi cần

GC logs cho **trend/capacity/events**, không chứa full reference graph. Khi cần tìm owner giữ objects, cân nhắc JFR/object allocation profiling, heap histogram, heap dump + MAT; các operations này có chi phí CPU/pause/storage/bảo mật.

### 16.3. Làm sao chứng minh object có thể thu sau release?

Có thể tạo một **thí nghiệm khác**: clear list, tiếp tục cấp phát để GC tự nhiên chạy, rồi quan sát old occupancy sau các collections phù hợp. Tuy nhiên không áp logic “clear xong thì old used ngay về 0”; GC có thể chưa thu old generation, và array references khác còn tồn tại.

## 17. Heap occupancy sau GC: cách đọc cẩn trọng

Một dòng:

```text
GC(16) Tenured: 22858K(43712K)->24266K(43712K)
```

có thể minh họa promotion trong young GC. Nhưng:

- `24266K` là **old used sau event**, không phải live bytes của riêng retained list.
- Object headers/alignment có overhead.
- Một số app/runtime objects vốn đã ở old.
- Những retained arrays có thể chưa được promote; vẫn ở young survivors.
- Khi có major/Full GC hoặc concurrent old reclamation, old occupancy có thể giảm sau một collection khác.

Vì vậy đừng suy “24.266 KiB old used = 24.266 KiB leaked memory”. Đây là metric của region, không phải phân loại semantic “leaked”.

## 18. Phân tích GC pause times

Trong GC completion lines, bạn thường thấy `...ms`. Có thể thống kê:

- Tổng duration của các GC pauses trong khoảng test.
- Số collections theo young/full.
- Trung vị, lớn nhất, percentile của pause durations (nếu đủ samples).
- Tỷ lệ tổng pause time so với application elapsed time.
- Old occupancy trend.

Nhưng mẫu trong demo chạy rất nhanh và **không đủ để kết luận p99 production latency**. Một log với 17 collections không có sample size thích hợp để diễn giải p99 ổn định.

### 18.1. Không suy “ít GC hơn luôn nhanh hơn”

Ít collections hơn có thể đi kèm **pause dài hơn** hoặc **old occupancy lớn hơn**. Một collector dùng nhiều short young GCs có thể tốt hơn một collector ít nhưng dài. Đánh giá cùng throughput/latency mục tiêu.

### 18.2. Không suy “retained mode GC count bằng short thì không đắt hơn”

Even with equal GC count, retained mode can copy/promote more bytes, increase old occupancy, and change future GC work. Số lượng events đơn lẻ là thước đo quá thô.

### 18.3. Không lấy wall time của hai JVM process ngắn làm benchmark chắc chắn

JVM launch, class loading, compilation, OS scheduling và log I/O có thể chiếm phần đáng kể. Nếu muốn benchmark allocation throughput, dùng JMH hoặc một harness đo lặp/giữ JVM process dài hơn, có workload isolation.

## 19. Các tình huống thực tế tương tự SHORT

- Chuỗi tạm trong request parsing.
- DTOs và intermediate collections ngắn hạn.
- Objects tạo trong một batch rồi bị loại bỏ sau pipeline.
- Request-scoped data hết lifecycle sau response.
- Tính toán cần arrays/buffers tạm rồi bỏ.

**Không có nghĩa mọi temporary allocation đều miễn phí.** Allocation rate quá cao có thể làm frequent young GC, CPU dành cho zeroing/copying, memory bandwidth và latency. Nhưng live set nhỏ thường giúp collector thu hồi hiệu quả.

## 20. Các tình huống thực tế tương tự RETAINED

- Caches có bounded maximum size.
- Session stores hoặc connection states.
- In-memory indexes/tables tồn tại lâu.
- Message queues và retry buffers.
- List/map giữ references từ requests đã kết thúc.
- Objects bị giữ qua listener, static field hoặc class loader.

Retention có thể tốt hoặc xấu theo mục tiêu. Điều quan trọng là **data lifetime phù hợp business lifecycle và budget heap**, không phải “hễ object lên old generation là lỗi”.

## 21. Nếu muốn tăng độ tương phản của thí nghiệm

Có thể thay đổi các biến **trong một experiment mới**, ví dụ:

1. Giữ mỗi 6 thay vì 12 mảng → khoảng 50 MiB payload retention trên heap 64 MiB, **rất dễ gây OOME/GC thrash**; không khuyến khích ở môi trường làm việc khác.
2. Tăng heap lên 128 MiB trong cả hai runs → có thể có ít Young GCs và old pressure thấp hơn tương đối.
3. Thay block size bằng 64 KiB → số objects và throughput allocation thay đổi.
4. Tạo một **bounded rolling window** để mảng sống qua vài GC nhưng không sống tới cuối process.
5. Đổi sang G1 để xem Eden/Survivor/Old region trends.

**Chỉ thay một yếu tố mỗi lượt** và ghi lý do. Không cần gây OutOfMemoryError để học GC.

### 21.1. Cẩn thận với tỷ lệ giữ quá cao

Khi retained bytes vượt heap khả dụng, application có thể gặp `OutOfMemoryError: Java heap space` sau nhiều GC attempts. Đó là kết quả hiển nhiên của việc cố giữ nhiều data hơn capacity, nhưng không có giá trị khi mục tiêu đang là **quan sát differences safely**.

### 21.2. Không dùng production service làm đồ chơi

Chạy bản thực nghiệm ở thư mục riêng, heap nhỏ 64 MiB và không attach vào JVM thật. Những cờ GC logging có thể dùng production khi được phê duyệt, nhưng thay collector và heap sizes chỉ để thử trên production không phù hợp.

## 22. Phòng sai lầm khi đo GC logging

| Sai lầm | Cách sửa |
| --- | --- |
| “Cấp phát 300 MiB nghĩa heap phải ≥300 MiB.” | Objects có thể chết và heap memory được tái sử dụng nhiều lần. |
| “SHORT có zero live objects.” | Có mảng cuối qua volatile, JDK/runtime và class metadata; 0 chỉ là list-retained payload. |
| “Giữ 25 MiB nghĩa old used đúng 25 MiB.” | Có headers/alignment, runtime objects và survivor/old phân bố. |
| “GC xảy ra ở cuối mọi scope.” | GC schedule do VM quyết định, không phải source lexical braces. |
| “Mọi arrays mới đều đi vào Eden và ở đó mãi.” | Collector/size/path/GC policy khác nhau; survivors có thể promote. |
| “Đếm `Pause Young` lines là số GC.” | Start/completion có thể là hai log lines cho cùng GC ID. |
| “Tenured tăng sau Young GC là log sai.” | Promotion có thể làm old occupancy tăng trong lúc young used giảm. |
| “Equal young GC count nghĩa equal retained memory.” | Survival/promotion/old occupancy khác nhau. |
| “Không Full GC thì không có memory pressure.” | Young GC và old occupancy có thể tăng mà chưa cần Full GC. |
| “Retention luôn là leak.” | Caches/indexes có thể intentionally retained và bounded. |
| “GC log chỉ cần một dòng để tìm owner của objects.” | GC logs aggregate; object graph cần tooling khác. |
| “`System.gc()` giúp kết quả thí nghiệm tự nhiên hơn.” | Nó thay scheduling, có thể làm kết quả mất đại diện. |
| “JIT không bao giờ tối ưu allocation.” | Escape analysis/scalar replacement có thể giảm allocations khác. |
| “Tenured là tên vùng ở mọi collectors.” | G1 thường dùng `Old regions`, không phải Serial Tenured label. |
| “G1 Old regions count đồng nghĩa MiB.” | Cần biết region size/accounting. |
| “Heap used cao ngay trước GC chứng minh leak.” | Short-lived allocations có thể làm occupancy tăng rồi được thu. |
| “Một lần test 17 GCs là tiêu chuẩn Java 17.” | Counts/timings thay đổi theo platform/JDK/GC heuristics. |
| “Ít GC hơn luôn tốt hơn.” | Cần xem pause time/throughput/latency. |
| “File log 48 KiB luôn đủ cho mọi run.” | Dung lượng phụ thuộc events/verbosity/workload. |
| “Tắt GC log để đo mà không thay instrumentation policy.” | Cần so công bằng, tách diagnostic runs với benchmark runs. |
| “Các biến không còn trong source scope chắc ngay lập tức được free.” | Reachability/GC scheduling và JVM optimizations mới quyết định. |

## 23. Bài tập tự kiểm tra kèm đáp án

**1.** Hai modes khác nhau ở điểm nào?

**Đáp án:** Chính sách giữ references trong static `retained` list; tổng số allocations, mảng size và checksum giống nhau.

**2.** Vì sao tổng requested allocation bằng 300 MiB dù heap chỉ 64 MiB?

**Đáp án:** Phần lớn arrays được thu hồi và heap memory được tái sử dụng qua nhiều GC cycles.

**3.** RETAINED giữ bao nhiêu mảng trong list?

**Đáp án:** 200 mảng, một mảng sau mỗi 12 lần cấp phát trong 2.400 lần lặp.

**4.** 200 × 128 KiB bằng bao nhiêu MiB?

**Đáp án:** 25 MiB capacity.

**5.** SHORT có thật sự 0 live objects không?

**Đáp án:** Không. `mostRecent` giữ mảng cuối và runtime có nhiều objects khác; `retainedReferences=0` chỉ nói list trống.

**6.** Mảng trong static list có thể bị GC thu hồi khi list vẫn reachable không?

**Đáp án:** Thông thường không; strong references từ reachable list giữ các mảng reachable.

**7.** Young GC có thể tăng Tenured used không?

**Đáp án:** Có. Surviving young objects có thể được promote sang old generation.

**8.** Hai runs đều 17 Young GCs có nghĩa old occupancy bằng nhau không?

**Đáp án:** Không; kết quả thử cho old occupancy khác lớn dù số events trùng.

**9.** Tại sao số `GC(16)` không phải GC duration?

**Đáp án:** Nó là GC event identifier; duration nằm ở completion log line có `ms`.

**10.** Vì sao đếm tất cả lines `Pause Young` có thể sai?

**Đáp án:** Start và completion có thể xuất hiện riêng cho một event.

**11.** `Tenured: 1221K->1221K` thể hiện gì?

**Đáp án:** Old used không đổi trong event đó ở độ phân giải log.

**12.** `Tenured: 22858K->24266K` có nghĩa application leak đúng 1.408 KiB không?

**Đáp án:** Không. Nó là net old occupancy change; không định danh owner hay leak.

**13.** Nếu không có Full GC thì RETAINED không tạo pressure đúng không?

**Đáp án:** Sai. Old occupancy có thể tăng nhiều mà chưa cần Full GC.

**14.** Vì sao không nên gọi `System.gc()` để ép mỗi phase?

**Đáp án:** Nó can thiệp GC scheduling và làm biến dạng thí nghiệm natural allocation pressure.

**15.** Các cờ nào cố định heap và collector?

**Đáp án:** `-Xms64m -Xmx64m -XX:+UseSerialGC`.

**16.** Vì sao không so `Tenured:` giữa Serial và G1?

**Đáp án:** G1 dùng region-based logs như `Old regions`; vocabulary/accounting khác.

**17.** `-Xlog:gc+age=trace` cho manh mối gì?

**Đáp án:** Survivor age distribution/tenuring thresholds trên HotSpot hỗ trợ.

**18.** Trong demo, 25 MiB là payload hay exact JVM memory footprint?

**Đáp án:** Payload bytes mà list giữ; actual heap occupancy còn overhead và những objects khác.

**19.** Kết quả thời gian pause của 17 events dùng dự đoán p99 production ổn không?

**Đáp án:** Không; sample nhỏ, workload và JVM process quá ngắn.

**20.** Điều kiện cần cho so sánh công bằng?

**Đáp án:** Cùng class file/JDK/GC/heap/log flags/workload; thay mỗi retention policy, lặp nhiều runs.

**21.** Retention có luôn là lỗi không?

**Đáp án:** Không; caches và indexes có thể là retention hữu ích, miễn có budget/lifecycle.

**22.** Khi nào cần heap dump?

**Đáp án:** Khi phải tìm object graph/GC root retention path và các metrics/JFR/histogram khác chưa đủ; cân nhắc chi phí, dữ liệu nhạy cảm và thời điểm chạy.

**23.** Nếu `retained.clear()`, old memory giảm ngay không?

**Đáp án:** Không được bảo đảm; cần object đủ điều kiện unreachable và collector thực hiện thu hồi thích hợp, có thể còn references khác.

**24.** Tại sao nên ghi JDK version và log settings?

**Đáp án:** GC policy/ergonomics, output format và flags có thể thay đổi giữa versions/platforms.

## 24. Checklist tái lập kết quả

1. Dùng JDK có `javac` và `java` tương thích Java 17+.
2. Lưu đúng file `AllocationLifetimeLab.java`.
3. Biên dịch `javac --release 17 AllocationLifetimeLab.java`.
4. Kiểm tra mode SHORT và RETAINED có stdout logic như mô tả.
5. Giữ `-Xms64m -Xmx64m -XX:+UseSerialGC` **y hệt**.
6. Bật Unified Logging vào hai **file riêng**.
7. Không thêm explicit GC request hoặc profiler khác nhau giữa modes.
8. Ghi actual `java -version`, OS, CPU quota.
9. Đọc `GC(n)` và phân biệt start/completion lines.
10. So Young events, Tenured before/after và total heap occupancy.
11. Không ép GC count phải là 17.
12. Không suy 25 MiB retained payload = exact Tenured used.
13. Kiểm tra không có Full GC/OOME rồi mới phân tích trạng thái.
14. Nếu muốn G1, chạy **một thí nghiệm đối chứng mới**.
15. Lặp vài runs, so distributions, đừng dùng một sample duy nhất.
16. Nếu nghi leak thật, kết hợp GC logs, heap usage trends và reference-graph diagnostics.
17. Không thu heap dump trên JVM production chỉ để hoàn thành bài thực hành.
18. Ghi rõ kết luận trong phạm vi JDK/collector/workload đang đo.

### Tóm tắt kết quả cần nhớ

```text
REQUESTED ALLOCATIONS:
    short    = 300 MiB
    retained = 300 MiB

STRONGLY REACHABLE VIA LIST AT END:
    short    = 0 arrays
    retained = 200 arrays ≈ 25 MiB payload

GC PATTERN IN ONE REAL TEST (HotSpot, Serial, 64 MiB heap):
    short:    17 Young GC, last Tenured ~1.2 MiB
    retained: 17 Young GC, last Tenured ~23.7 MiB

  Exact GC counts and region occupancies may differ on your system.

Key:
  allocated bytes != live-set bytes
  young GC count != promoted bytes
  retained objects != necessarily a memory leak
  GC log is an aggregate view, not an object ownership graph
```

**Kết luận:** Với cùng **300 MiB allocation requests** và heap **64 MiB**, thay đổi một yếu tố nhỏ ở code — **giữ lại strong references tới 200 mảng** — có thể làm old-generation occupancy tăng đáng kể. Sự khác biệt cần đọc từ **after-GC heap/old occupancy**, chứ không chỉ từ **tổng bytes cấp phát** hay **số lần Young GC**. GC logs trả lời được “heap đang phản ứng thế nào”; muốn xác định “ai giữ objects” cần kết hợp phân tích object lifecycle và tools chuyên sâu.

### Tài liệu chính thức

- [Oracle JDK 17 — Introduction to GC Tuning](https://docs.oracle.com/en/java/javase/17/gctuning/introduction-garbage-collection-tuning.html): GC, live objects và các thế hệ thu gom.
- [Oracle JDK 17 — The Young Generation](https://docs.oracle.com/en/java/javase/17/gctuning/the-young-generation.html): survivor spaces, age/tenuring và `NewRatio`.
- [Oracle JDK 17 — Serial Garbage Collector](https://docs.oracle.com/en/java/javase/17/gctuning/serial-collector.html): cơ chế Serial GC.
- [Oracle JDK 17 — Garbage-First GC Tuning](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-garbage-collector-tuning.html): regions, young/old logs và GC behavior với G1.
- [Oracle JDK 17 — `java` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xms`, `-Xmx`, `-XX:+UseSerialGC`, Unified Logging `-Xlog`, file rotation.
- [OpenJDK JEP 271 — Unified GC Logging](https://openjdk.org/jeps/271): kiến trúc Unified GC logging và các log tag combinations.
- [Oracle Java Tutorials — Reachability and GC](https://docs.oracle.com/javase/8/docs/technotes/guides/ref/overview.html): tổng quan references/reachability; phân biệt nguồn JDK 8 tham khảo nguyên lý với options JDK 17 thực hành.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-experiments-007">Thử các bộ thu gom trong cùng điều kiện và báo cáo cả thông lượng lẫn độ trễ</a>

<details>
<summary>Xem chi tiết</summary>

# Thử các bộ thu gom trong cùng điều kiện và báo cáo cả thông lượng lẫn độ trễ

## 1. Mục tiêu bài học

Garbage collector (GC) của HotSpot quản lý những object không còn reachable trong Java heap. Một collector có thể tối ưu **thông lượng (throughput)**, trong khi collector khác tập trung giảm **độ dài các lần dừng ứng dụng (GC pause latency)**. Tuy nhiên **GC pause** không phải **độ trễ request**, và GC chạy đồng thời vẫn tiêu thụ CPU/memory khi application threads tiếp tục hoạt động.

Bài này là **thực nghiệm JVM**: cần so sánh các collectors dưới **cùng điều kiện**, đo **cả throughput và latency**, thu log chứng minh GC được chọn, ghi rõ failures và tránh bịa kết quả. Mục tiêu không phải tuyên bố “ZGC/G1/Parallel tốt nhất cho mọi ứng dụng”, mà là trả lời bằng dữ liệu:

> Với cùng ứng dụng, JDK binary, giới hạn CPU/RAM, input và mức tải, bộ thu gom nào đạt yêu cầu completed operations/s hoặc requests/s, đồng thời giữ p95/p99 latency và các tiêu chí memory/CPU trong ngưỡng?

Sau khi học, bạn có thể:
1. Hiểu vai trò **Serial GC**, **Parallel GC**, **G1 GC**, **ZGC** và collector tùy chọn **Shenandoah**.
2. Phân biệt **successful throughput**, **operation latency**, **request tail latency**, **GC pause durations**, **concurrent GC CPU overhead**, allocation rate và peak memory.
3. Chạy thí nghiệm Java 17+ với cùng `-Xms`, `-Xmx`, cùng bytecode và cùng OS/container resources, chỉ thay một collector flag.
4. Viết Java chương trình tự đo throughput và per-operation latency, có **checksum xác định** để phát hiện run hỏng.
5. Lặp nhiều JVM processes, kiểm soát warmup, đảo thứ tự, ghi raw CSV và báo medians/ranges thay vì chọn một lần chạy nhanh nhất.
6. Hiểu **coordinated omission**: latency closed-loop thấp không chứng minh tail latency khi requests đến độc lập.
7. Đọc `-Xlog:gc=info`, GC/safepoint debug logs, JFR và OS metrics; không suy user-facing latency chỉ từ GC log.
8. Tổ chức vòng benchmark fixed-heap và vòng fixed-container-budget, bởi các collectors có native overhead/headroom khác nhau.
9. Tách khác biệt collector khỏi khác biệt phiên bản JDK: generational ZGC đã đổi giữa JDK 17, 21, 23 và 24.
10. Đưa ra báo cáo production theo **throughput–latency–CPU–memory Pareto trade-off**.

## 2. GC thay đổi những gì?

Java source dùng `new` tạo objects. Khi chúng không còn reachable, JVM có thể thu hồi heap space. GC thực hiện nhiều loại công việc tùy collector, ví dụ xác định live objects, tracing, copying/evacuation, compaction, concurrent marking hoặc reference processing.

~~~text
      JAVA MUTATOR THREADS
      allocation / application work
                    |
                    v
                 HEAP DATA
                    |
         +----------+----------+
         |                     |
     SHORT-LIVED            LONG-LIVED
         |                     |
     Young collections     Old/major collections
         |                     |
         +----------+----------+
                    |
          GC strategy changes
          stop-the-world work
          concurrent CPU usage
          heap/native overhead
                    |
           APP THROUGHPUT & LATENCY
~~~

Các collectors không chỉ khác “thời gian pause”:
- **Stop-the-world (STW):** các application threads bị dừng ở safepoint/handshake thích hợp để JVM thực hiện một công việc nhất định.
- **Concurrent GC:** GC workers làm việc song song theo thời gian với mutators; giảm pauses nhưng tranh CPU/bandwidth với ứng dụng.
- **Collector-native overhead:** remembered sets, relocation metadata, thread buffers, GC state và code/data structures; cùng Java heap maximum không đảm bảo cùng RSS.
- **Heap ergonomics:** vùng/generation sizing, concurrent cycles, worker counts và adaptive feedback khác nhau.
- **Allocation pressure:** short-lived allocation nhiều hay live-set lớn có thể ưu tiên collector khác nhau.

Một workload không phát sinh GC trong measurement window **không đủ để kết luận đặc tính thu gom**. Phải kiểm tra logs, allocation rate và heap occupancy.

## 3. Những collectors thường gặp

| Collector | Cờ HotSpot phổ biến | Thiết kế nổi bật | Phép thử thích hợp |
| --- | --- | --- | --- |
| Serial | `-XX:+UseSerialGC` | Thu gom tương đối đơn giản, ít GC worker threads | CLI/heap nhỏ/CPU hạn chế |
| Parallel | `-XX:+UseParallelGC` | GC work song song, thường hướng throughput | Batch throughput cao, chấp nhận pauses |
| G1 | `-XX:+UseG1GC` | Regions, một phần concurrent, pause goal heuristic | Cân bằng throughput và GC pause |
| ZGC | `-XX:+UseZGC` | Phần lớn thu gom concurrent, low-pause goals | Tail-sensitive services, có CPU/RAM headroom |
| Shenandoah | `-XX:+UseShenandoahGC` nếu build hỗ trợ | Low-pause concurrent collection | Phép thử mở rộng tùy JDK vendor |

**Không mọi HotSpot/JDK vendor đều hỗ trợ Shenandoah.** Một option được ghi trong bài học không chứng minh JDK runtime cụ thể có collector đó. Kiểm tra `java -version`, `java -X` và thử flag trên exact image.

### Parallel GC

Parallel collector dùng nhiều workers cho stop-the-world GC tasks, tối ưu sử dụng nhiều CPU cores để xử lý allocation/collection với throughput tốt. Nhưng GC pauses có thể dài với old-generation collection hoặc live set lớn. Một batch tool chạy độc lập có thể chấp nhận pause này, trong khi HTTP API latency-sensitive có thể không.

**Không tuyên bố Parallel luôn có ops/s cao nhất** mà không đo application thực; VM defaults, CPU quota, heap size và workload có thể đảo chiều kết quả.

### G1 GC

G1 được thiết kế cân bằng throughput và predictable pause goals. HotSpot thường chọn G1 mặc định ở server-like configurations. G1 chia heap thành regions, thực hiện incremental evacuation và nhiều hoạt động concurrently với mutators.

Cờ `-XX:MaxGCPauseMillis=100` đặt **mục tiêu thời gian pause mềm**, không phải guarantee mọi pause ≤100 ms, càng không phải bảo đảm request p99≤100 ms. Oracle G1 tuning guide khuyên bắt đầu từ defaults, và tránh tùy tiện ép young generation size vì G1 cần tự điều chỉnh young size để đạt pause goal.

### ZGC

ZGC được thiết kế để có GC pauses ngắn, dùng concurrent marking/relocation theo implementation. CPU usage và headroom có thể cao hơn trong một số workloads; trong container CPU quota nhỏ, background GC có thể bị throttled hoặc tranh CPU với application.

**ZGC không đồng nghĩa GC pause=0 hoặc request latency≈0**. Một HTTP request còn có scheduling, network, locks, DB, queues và application CPU time.

### Serial và Shenandoah có cần đo?

Nếu workload chỉ có một CPU quota nhỏ, Serial đôi khi đáng thử vì đơn giản, nhưng phải chấp nhận pauses. Nếu deployment JDK hỗ trợ Shenandoah, có thể bổ sung một mode, nhưng không được thay JDK distribution cho riêng Shenandoah rồi gọi thí nghiệm còn “cùng điều kiện”.

## 4. Phiên bản ZGC thay đổi theo JDK

- **JDK 17:** ZGC non-generational.
- **JDK 21:** Generational ZGC được cung cấp bằng mode opt-in `-XX:+ZGenerational` khi JVM build hỗ trợ (JEP 439).
- **JDK 23:** Generational mode thành mặc định (JEP 474).
- **JDK 24 trở đi:** Non-generational ZGC bị loại bỏ (JEP 490).

Do đó không so ZGC trên JDK 25 với G1 trên JDK 17 rồi kết luận chênh lệch là “tác dụng collector”. JIT, VM, GC semantics và runtime libraries đều thay đổi. Một thí nghiệm cô lập GC phải dùng **cùng exact JDK binary**, tốt nhất in cả vendor/build/architecture và ghi lại flags.

Nếu muốn nghiên cứu **tác động nâng JDK**, tổ chức một ma trận khác: so các collectors **bên trong từng JDK version**, rồi phân tích tương tác version×GC, không trộn tất cả vào một bảng chung.

## 5. Throughput là gì và tính như thế nào?

Cần nêu rõ mẫu số và đơn vị:

~~~text
throughput_ops_s =
    successful_completed_operations / measurement_wall_seconds

successful_rps =
    successful_HTTP_responses / measurement_wall_seconds
~~~

Ví dụ **chỉ là số giả định**: 48.000 requests hoàn thành thành công trong 24 giây cho successful throughput = 2.000 requests/s.

Phải tách:
- **offered rate:** requests mà load generator cố gửi mỗi giây.
- **successful completed rate:** requests xử lý thành công.
- **error rate / timeout rate:** requests thất bại.
- **throughput at SLO:** lượng tải tối đa vẫn đạt yêu cầu latency/error/memory.

Nếu một collector đạt 15.000 attempts/s nhưng 10% requests timeout, không báo “throughput=15k successful rps”. Nên có một cột **success**, một cột **errors**, và denominator rõ ràng.

### CPU efficiency

Một collector chạy concurrent GC threads có thể giảm STW nhưng tiêu thụ CPU. Vì vậy cùng lúc quan sát CPU seconds, CPU throttling, runtime application throughput và cgroup limits. “Ít stop-the-world time” **không chứng minh ít GC CPU tổng**.

## 6. Latency và percentiles: đừng lẫn đại lượng

Latency là thời gian một operation/request hoàn thành, đo bằng clock/harness thống nhất.

| Chỉ số | Câu hỏi |
| --- | --- |
| p50 | Một operation/request điển hình chờ bao lâu? |
| p95 | 95% completions có latency không vượt bao nhiêu? |
| p99 | 99% completions có latency không vượt bao nhiêu? |
| max | Observation lâu nhất trong dữ liệu đã thu? |
| Timeout rate | Bao nhiêu requests chưa hoàn thành trong thời hạn? |
| GC pause p99 | Phân phối **GC pauses**, không phải requests |
| Request p99 | Phân phối **end-to-end requests**, có thể có queues/I/O |

Percentiles phụ thuộc sample size và cách thu thập:
- Với 600.000 operations, một GC pause hiếm có thể gây **max** cao, nhưng chưa làm p99 thay đổi.
- Với một service 100.000 requests/s, p99 vẫn bỏ qua tail 1% lâu nhất; nếu SLA nhắm p99.9, phải đo và báo thêm.
- Một collector có GC pause p99=2 ms nhưng request p99=250 ms là điều hoàn toàn có thể nếu DB/network/CPU/queues gây chậm.

**Không lấy GC pause p99 thay cho request p99.** GC log đo sự kiện VM; load generator/latency histogram đo units nghiệp vụ.

## 7. Hai định nghĩa “cùng điều kiện” cần tách

### Vòng A — same heap, cô lập flag collector

Chạy:
- Cùng Java source/classes/JAR, JVM binary.
- Cùng CPU limit, cpuset, OS, inputs.
- Cùng `-Xms256m -Xmx256m`.
- Cùng logging mode, iteration count, timing method.
- Chỉ thay `-XX:+UseParallelGC` / `-XX:+UseG1GC` / `-XX:+UseZGC`.

Vòng này **dễ giải thích cơ chế**, nhưng có giới hạn: collector-native memory usage khác nhau; cùng 256 MiB Java heap không có nghĩa cùng cgroup RSS và khả năng chạy bền vững.

### Vòng B — same container budget, gần production

Ví dụ chung hard resources: CPU 2 cores-equivalent, container memory 1 GiB, SLO p99≤80 ms. Mỗi collector được cho một số tuning trials nhất định trong cùng ngân sách. Phải báo effective heap/GC flags, native RSS, total CPU/memory, throughput và latency.

Đây không còn là “chỉ thay một flag”, mà là so **cấu hình đã điều chỉnh của mỗi collector dưới cùng budget**. Nên công bố kết quả cả vòng A và vòng B, không lẫn.

## 8. Ma trận kiểm soát biến

| Yếu tố | Điều kiện |
| --- | --- |
| Java runtime | Exact same HotSpot vendor/build, JDK version, JVM options khác ngoài collector |
| Application | Same compiled artifacts, dependencies, input data, runtime behavior |
| Workload | Same allocation sizes, retention, offered rps, concurrency, data distribution |
| Heap | Vòng A: same `Xms/Xmx`; vòng B: explicitly recorded GC tuning under same memory limit |
| CPU | Same quota/cpuset, no unequal host contention, same thread-pool policy |
| RAM | Same container limit, same sidecars and native budget awareness |
| GC logging | Same `-Xlog:gc=info` selector and output handling |
| Warmup | Same policy per JVM fork, check steady state |
| Forks | Multiple independent JVM processes for each mode |
| Measurement | Same clock, response definitions, histograms, duration |
| Failure handling | OOME, SIGKILL, bad checksum, timeout = report invalid/failure |
| Scheduling | Alternate or randomized mode order; avoid cache/temperature drift |
| Observability | JFR/profiling on all modes or separate diagnostic runs |
| Reporting | Throughput + p95/p99/max + errors + CPU + memory + GC stats |

**Giữ chỉ một biến thay đổi mỗi lần** là thiết kế baseline tốt, nhưng khác biệt về intrinsic collector implementation chính là điều đang nghiên cứu. Không cố “sửa bằng cách ép mọi GC dùng cùng GC worker counts” nếu điều đó làm mất khả năng default ergonomics; flags chuyên biệt nên đưa vào vòng tuning có ghi rõ.

## 9. Xác minh collector thực sự được chạy

Trước mọi phép đo:

~~~bash
java -version
javac -version
java -X
java -Xlog:help
~~~

Smoke test từng collector trên cùng runtime:

~~~bash
java -Xms256m -Xmx256m -XX:+UseParallelGC -version
java -Xms256m -Xmx256m -XX:+UseG1GC -version
java -Xms256m -Xmx256m -XX:+UseZGC -version
~~~

Nếu một option không được hỗ trợ hoặc không đủ resources: **dừng / đánh dấu Unsupported**, không silently skip và không dùng `-XX:+IgnoreUnrecognizedVMOptions`.

Kiểm tra GC ở chính workload bằng log:

~~~bash
java -Xms256m -Xmx256m -XX:+UseG1GC -Xlog:gc=info GcTradeoffBench
~~~

HotSpot thường ghi collector name và các event. Log format, số GC events và version strings **khác theo bản JDK**, không có một stdout diagnostic cố định.

Không chỉ đọc `PrintFlagsFinal` rồi suy rằng mọi feature options có semantics giống nhau trong JVM mới. Flag acceptance, effective VM choice và behavior của GC phải được xác minh bằng startup/logs.

## 10. Chương trình Java 17+ chạy độc lập: GC workload + throughput + latency

Đây là **single-thread closed-loop synthetic benchmark**. Nó tạo arrays kích thước khác nhau, giữ 256 arrays mới nhất trong một reference ring để tránh tối ưu loại bỏ toàn bộ allocations, lưu timestamps từng operation và in timing metrics. Mục tiêu là **luyện đo**, không tự đại diện production service.

Tạo file **`GcTradeoffBench.java`**:

~~~java
import java.util.Arrays;
import java.util.Locale;

public class GcTradeoffBench {
    private static final int WARMUP = 50_000;
    private static final int OPERATIONS = 600_000;
    private static final byte[][] RETAINED = new byte[256][];
    private static volatile long checksumSink;

    private static int operation(int index) {
        int size = 1024 + (index & 7) * 256;
        byte[] data = new byte[size];
        data[0] = (byte) (index & 127);
        data[data.length - 1] = (byte) (index >>> 7);
        RETAINED[index & 255] = data;

        return Byte.toUnsignedInt(data[0])
                + Byte.toUnsignedInt(data[data.length - 1]);
    }

    private static long percentile(long[] sorted, double fraction) {
        int index = (int) Math.ceil(fraction * sorted.length) - 1;
        return sorted[Math.max(0, index)];
    }

    public static void main(String[] args) {
        long checksum = 0;
        for (int i = 0; i < WARMUP; i++) {
            checksum += operation(i);
        }

        long[] latencyNs = new long[OPERATIONS];
        long started = System.nanoTime();

        for (int i = 0; i < OPERATIONS; i++) {
            int index = WARMUP + i;
            long t0 = System.nanoTime();
            checksum += operation(index);
            latencyNs[i] = System.nanoTime() - t0;
        }

        long elapsed = System.nanoTime() - started;
        checksumSink = checksum;
        Arrays.sort(latencyNs); // outside timed interval

        System.out.println("operations=" + OPERATIONS);
        System.out.println("checksum=" + checksumSink);
        System.out.printf(Locale.ROOT, "elapsedMs=%.3f%n",
                elapsed / 1_000_000.0);
        System.out.printf(Locale.ROOT,
                "throughputOpsSec=%.2f%n",
                OPERATIONS * 1_000_000_000.0 / elapsed);
        System.out.printf(Locale.ROOT, "p50Us=%.3f%n",
                percentile(latencyNs, 0.50) / 1_000.0);
        System.out.printf(Locale.ROOT, "p95Us=%.3f%n",
                percentile(latencyNs, 0.95) / 1_000.0);
        System.out.printf(Locale.ROOT, "p99Us=%.3f%n",
                percentile(latencyNs, 0.99) / 1_000.0);
        System.out.printf(Locale.ROOT, "maxUs=%.3f%n",
                latencyNs[latencyNs.length - 1] / 1_000.0);
    }
}
~~~

Biên dịch:

~~~bash
javac --release 17 GcTradeoffBench.java
~~~

Chạy một lần để kiểm tra:

~~~bash
java -Xms256m -Xmx256m -XX:+UseG1GC GcTradeoffBench
~~~

**Hai dòng stdout có giá trị chính xác:**

~~~text
operations=600000
checksum=123575256
~~~

Giá trị checksum được tạo bằng cách cộng `(index & 127) + ((index >>> 7) & 255)` cho tất cả indexes từ 0 tới 649999, bao gồm cả warmup, nên giống nhau ở mọi GC khi workload hoàn tất đúng. Những dòng còn lại là:

~~~text
elapsedMs=<depends on machine>
throughputOpsSec=<depends on machine>
p50Us=<depends on machine>
p95Us=<depends on machine>
p99Us=<depends on machine>
maxUs=<depends on machine>
~~~

Đây là **sơ đồ output**, các token trong dấu góc không phải con số in ra của chương trình. Java in những số thực định dạng ba chữ số sau dấu chấm cho latency, hai chữ số cho throughput, theo Locale.ROOT.

### Vì sao giữ checksum và retained references?

Dùng `RETAINED[index & 255]=data` khiến array reference thoát ra khỏi method và tồn tại trong dữ liệu tĩnh, giảm khả năng JIT loại hoàn toàn việc allocation. `checksumSink` volatile giúp kết quả được quan sát. **Điều này không biến demo thành JMH**, nhưng tốt hơn một vòng `new byte[1000]` không hề dùng kết quả.

Trong thí nghiệm, cả ba collectors phải in **checksum chính xác 123575256**. Nếu checksum sai, exit nonzero, crash hoặc ném OutOfMemoryError: đánh dấu invalid.

### Giới hạn của timing trong demo

- `System.nanoTime()` per operation có overhead đáng kể nếu operation chỉ vài microseconds.
- Đơn vị đo là **một allocation operation** trong single application thread, không phải request latency.
- Một pause rất hiếm có thể làm `maxUs` tăng mạnh mà không làm `p99Us` tăng.
- Single-thread loop không mô phỏng network concurrency, database, lock contention, connection pools.
- Warmup 50.000 operations không đảm bảo JIT/GC đã đạt steady state trên mọi máy.
- `Arrays.sort` chạy **sau** timed loop; thời gian sort không được tính vào throughput.
- Đây là **closed-loop**: operation sau không bắt đầu khi operation trước chưa kết thúc.

Thực nghiệm nghiêm túc hơn về microbenchmark dùng **JMH** và đủ JVM forks/warmup; thực nghiệm production service dùng load generator end-to-end.

## 11. Chạy ba mode thủ công

Trên shell Linux/macOS:

~~~bash
java -Xms256m -Xmx256m -XX:+UseParallelGC \
  -Xlog:gc=info GcTradeoffBench

java -Xms256m -Xmx256m -XX:+UseG1GC \
  -Xlog:gc=info GcTradeoffBench

java -Xms256m -Xmx256m -XX:+UseZGC \
  -Xlog:gc=info GcTradeoffBench
~~~

Dấu `\` nối dòng thuộc shell Unix. Trên Windows PowerShell, dùng một dòng hoặc script phía dưới.

**Các điều kiện cố định:** same Java classes, same `-Xms256m -Xmx256m`, same GC logging selector, same CPU/memory limit. **Biến chính:** collector flag.

GC logs có thể xen với app stdout tùy output selector, do đó trong script benchmark nên ghi GC logs ra file riêng, giữ phần output metrics dễ parse. Tất cả cases phải có logging comparable, không bật verbose logging riêng một collector.

## 12. Script PowerShell 5.1+ lặp nhiều JVM forks và xuất CSV

Lưu file **`measure-gc.ps1`** trong thư mục có `GcTradeoffBench.class`, sử dụng cùng Java từ PATH cho cả ba collectors. Script preflight flags, chạy hai JVM launches warmup/mode, thực hiện 12 measured forks/mode và đảo thứ tự giữa các rounds. Mỗi fork tạo một file GC log riêng.

~~~powershell
$ErrorActionPreference = "Stop"

$javaExe = (Get-Command java).Source
$classFile = Join-Path (Get-Location) "GcTradeoffBench.class"

if (-not (Test-Path -LiteralPath $classFile)) {
    throw "Compile GcTradeoffBench.java first"
}

New-Item -ItemType Directory -Path "gc-logs" -Force | Out-Null

$cases = @{
    parallel = @("-XX:+UseParallelGC")
    g1       = @("-XX:+UseG1GC")
    zgc      = @("-XX:+UseZGC")
}

foreach ($name in @("parallel", "g1", "zgc")) {
    [string[]]$argsToCheck = @("-Xms256m", "-Xmx256m") + $cases[$name]
    $null = & $javaExe @argsToCheck -version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Unsupported collector or failed JVM startup: $name"
    }
}

function Invoke-Bench([string]$name, [int]$number, [bool]$warmup) {
    [string[]]$vmArgs = @("-Xms256m", "-Xmx256m") + $cases[$name]

    $stage = if ($warmup) { "warm" } else { "measure" }
    $logFile = "gc-logs/$name-$stage-$number.log"
    $vmArgs += "-Xlog:gc=info:file=$($logFile):time,uptime,level,tags"

    $lines = & $javaExe @vmArgs GcTradeoffBench 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        throw "Failure: $name exit=$exitCode, output=$($lines -join ' | ')"
    }

    $values = @{}
    foreach ($line in $lines) {
        $s = [string]$line
        if ($s -match '^([A-Za-z][A-Za-z0-9]*)=([0-9.]+)$') {
            $values[$matches[1]] = $matches[2]
        }
    }

    foreach ($key in @(
        "operations", "checksum", "elapsedMs",
        "throughputOpsSec", "p50Us", "p95Us", "p99Us", "maxUs"
    )) {
        if (-not $values.ContainsKey($key)) {
            throw "Missing metric: $name / $key"
        }
    }

    if ([int]$values["operations"] -ne 600000 -or
        [long]$values["checksum"] -ne 123575256) {
        throw "Workload integrity failure: $name"
    }

    $culture = [System.Globalization.CultureInfo]::InvariantCulture

    return [pscustomobject]@{
        round = $number
        mode = $name
        throughputOpsSec = [double]::Parse($values["throughputOpsSec"], $culture)
        p95Us = [double]::Parse($values["p95Us"], $culture)
        p99Us = [double]::Parse($values["p99Us"], $culture)
        maxUs = [double]::Parse($values["maxUs"], $culture)
        elapsedMs = [double]::Parse($values["elapsedMs"], $culture)
    }
}

for ($warm = 1; $warm -le 2; $warm++) {
    foreach ($name in @("parallel", "g1", "zgc")) {
        $null = Invoke-Bench $name $warm $true
    }
}

$rows = @()
for ($round = 1; $round -le 12; $round++) {
    $order = if (($round % 2) -eq 1) {
        @("parallel", "g1", "zgc")
    } else {
        @("zgc", "g1", "parallel")
    }

    foreach ($name in $order) {
        $rows += Invoke-Bench $name $round $false
    }
}

$rows | Export-Csv -Path "gc-results.csv" -NoTypeInformation -Encoding UTF8

function Get-Median([double[]]$numbers) {
    $sorted = @($numbers | Sort-Object)
    $n = $sorted.Count
    $mid = [int][Math]::Floor($n / 2)
    if (($n % 2) -eq 0) {
        return ($sorted[$mid - 1] + $sorted[$mid]) / 2.0
    }
    return $sorted[$mid]
}

$summary = foreach ($name in @("parallel", "g1", "zgc")) {
    $group = @($rows | Where-Object { $_.mode -eq $name })
    $t = [double[]]@($group | ForEach-Object { $_.throughputOpsSec })
    $p = [double[]]@($group | ForEach-Object { $_.p99Us })
    $m = [double[]]@($group | ForEach-Object { $_.maxUs })

    [pscustomobject]@{
        mode = $name
        runs = $group.Count
        throughputMedianOpsSec = [Math]::Round(
            (Get-Median -numbers $t), 2)
        p99MedianUs = [Math]::Round(
            (Get-Median -numbers $p), 3)
        maxLatencyMedianUs = [Math]::Round(
            (Get-Median -numbers $m), 3)
    }
}

$summary | Format-Table -AutoSize
Write-Host "Raw measurements: gc-results.csv"
Write-Host "GC logs: gc-logs/"
~~~

Chạy:

~~~powershell
powershell -NoProfile -File .\measure-gc.ps1
~~~

Nếu dùng PowerShell 7, chọn `pwsh -NoProfile -File ./measure-gc.ps1`. Không thay đổi execution policy trên toàn hệ thống chỉ để chạy một script.

### Nội dung báo cáo của script

- `gc-results.csv`: 36 rows nếu cả ba collectors chạy hợp lệ (12/mode).
- `gc-logs/...`: log GC từng JVM run, có thể đối chiếu pause count/time.
- Console summary: median throughput, median of per-fork p99, median of max latency.
- Không có số benchmark được đặt sẵn; thời gian phát sinh từ JVM thực.

**Nếu ZGC unsupported hoặc gặp OOME trong resource budget này, script sẽ dừng với lỗi.** Đó không phải bằng chứng collector kém trong mọi workload. Ghi mode **UNSUPPORTED/INVALID** cùng message, rồi thiết kế lại thí nghiệm hợp lệ; không tự chạy ZGC với heap 2 GiB riêng mà vẫn tuyên bố fixed-heap comparison.

### Per-fork percentiles không cộng/trung bình thành global percentile

Ví dụ p99 của fork 1=10µs và fork 2=100µs: “trung bình p99=55µs” **không phải p99 của tập request kết hợp**. Script báo **median across forks of each fork's p99**, hữu ích để so mức điển hình theo independent processes. Để có global p99, giữ raw per-request latencies/histograms và gộp đúng trọng số/buckets.

### Warmup ngoài và trong process

- Java code warmup 50.000 operations trong mỗi process, nhưng không guarantee JIT hoàn toàn đạt steady-state.
- Script warmup launches giúp filesystem/OS cache ổn định, **không truyền JIT compiled code từ JVM process trước sang process sau** trong thí nghiệm này.
- Một benchmark nghiêm túc nên tăng measurement duration, forks và kiểm tra stability trước khi kết luận.

## 13. Phân tích GC logs thay vì chỉ lấy ops/s

Mỗi GC log cần trả lời:
1. Collector name có đúng với mode?
2. Có GC events/allocations trong measurement period không?
3. Young/old/full collections xảy ra bao nhiêu lần và vì sao?
4. Stop-the-world pause durations phân bố ra sao?
5. Có evacuation failure, allocation stall, concurrent cycle bị chậm không?
6. Heap before/after mỗi collection như thế nào?
7. GC CPU time và concurrent work có làm application bị saturation?
8. Có warnings, heap OOME, native OOME hoặc OOMKilled không?

Ví dụ **dòng G1 giả lập**:

~~~text
[2.114s][info][gc] GC(5) Pause Young (Normal) (G1 Evacuation Pause) 90M->20M(256M) 4.122ms
~~~

Đây **không phải log chắc chắn xuất hiện**. Nó cho biết VM đã ghi một G1 young evacuation event kéo dài khoảng 4,122ms. Heap figures là metric liên quan vùng heap theo VM, không phải toàn bộ RSS.

Không áp parser chỉ tìm `Pause Young` cho ZGC rồi kết luận ZGC không có pauses. Các collectors có tag/phases/line formats khác; muốn parse tự động, dùng parser biết release/collector hoặc JFR structured events.

### Debug logging

Tại diagnostic runs có thể dùng:

~~~bash
java -XX:+UseG1GC -Xms256m -Xmx256m \
  -Xlog:gc*=debug,safepoint=info:file=gc-debug.log:time,uptime,level,tags \
  GcTradeoffBench
~~~

Xác nhận syntax/tags trên JVM bằng `java -Xlog:help`. Verbose logs thay đổi overhead; benchmark chính nên sử dụng cùng log selector tối thiểu cho cả ba modes, còn debug/JFR tiến hành riêng.

## 14. GC pause fraction khác GC CPU overhead

Một phép tính sơ bộ:

~~~text
STW_fraction = sum(non-overlapping_STW_pause_duration) / wall_duration

successful_throughput =
    successful_completed_operations / wall_duration
~~~

Ví dụ **giả định**: 0,5 giây STW pauses trong cửa sổ 10 giây tương đương 5% STW wall-time fraction. **Không suy** rằng total GC overhead chỉ 5%:
- G1/ZGC có concurrent work tranh CPU trong thời gian app chạy.
- Safepoints ngoài GC cũng có thể làm threads dừng.
- GC threads có thể chạy song song: User CPU time cộng qua threads có thể lớn hơn Real wall-time.
- Application có I/O, locks, scheduling và CPU throttling.

Oracle G1 tuning guide phân biệt `User`, `Sys` và `Real` time trong GC logs. Khi CPU quota hạn chế, ratio các metric này có thể rất khác so với máy không throttling.

**Không so hai collectors chỉ bằng count GC events hoặc total STW milliseconds**; cần throughput + end-to-end latency + CPU/RSS.

## 15. Closed-loop và coordinated omission

Demo Java có mô hình:

~~~text
request 1 begins -> ends -> request 2 begins -> ends -> ...
~~~

Trong lúc JVM pause 200ms, loop không đưa thêm operations vào hệ thống. Nhưng traffic ngoài đời thường không đợi response trước để quyết định có bao nhiêu clients khác gửi request.

Ở **open-loop**, arrivals có thể tiếp tục:

~~~text
t=0ms     request A arrives
t=10ms    request B arrives
t=20ms    request C arrives
...
server paused; queue/backlog grows
...
requests experience increasing end-to-end delays
~~~

Nếu benchmark chỉ gửi request tiếp theo sau khi request trước hoàn tất, **coordinated omission** có thể làm tail latency thấp giả dưới overload. Vì vậy production GC comparison cần load generator có **arrival-rate policy** và latency reporting phù hợp, hoặc sửa histogram để tính các arrivals bị bỏ sót theo phương pháp được công nhận.

### So cùng offered load

Nếu Parallel chạy tối đa 12k requests/s còn ZGC chạy tối đa 9k rps, so p99 trực tiếp tại “max load” của từng mode là so **khác mức tải**. Hãy chọn chung các load points như 5k, 7k, 9k rps, rồi báo capacity của từng mode riêng.

Một service đo đúng:
- offered requests/s,
- successful requests/s,
- p50/p95/p99,
- timeouts/errors,
- CPU/GC usage,
- queue lengths và memory footprint.

## 16. JMH, JFR và load generator: công cụ phục vụ những câu hỏi khác

### JMH cho microbenchmarks

**JMH (Java Microbenchmark Harness)** kiểm soát forks, warmup, measurement iterations, dead-code elimination, state và một số JVM optimizations dễ làm benchmark sai. Nếu cần so allocation costs của một Java method trong ba GC, JMH là bước phát triển hợp lý từ demo.

Nhưng **JMH throughput của một method không thay HTTP request p99**. Nó có thể không tái tạo lock contention, network, database hoặc queuing của production.

### Java Flight Recorder (JFR)

JFR ghi nhiều runtime events như GC, allocation sampling, CPU, safepoints và thread activity. Ví dụ trên JDK có JFR:

~~~bash
java -XX:StartFlightRecording=filename=gc-run.jfr,settings=profile \
  -XX:+UseG1GC -Xms256m -Xmx256m GcTradeoffBench
~~~

Nếu JFR được bật trong benchmark, bật **giống nhau trên mọi mode**. Nếu mục tiêu là đo ít overhead nhất, chạy benchmark chính không JFR, rồi thực hiện diagnostic runs riêng.

### Service load testing

Có thể dùng k6 với constant arrival rate, Gatling hoặc một công cụ đo end-to-end tương đương; phải đọc docs đúng chế độ để xác nhận coordinated omission, client-side bottleneck và cách tính timeouts. Mẫu này không phụ thuộc vào một sản phẩm benchmark bắt buộc.

**Một tool có logo đẹp không thay việc kiểm soát offered rate, load generator CPU saturation và server backlog.**

## 17. Warmup và experimental design

HotSpot trải qua:
- Class loading.
- Interpreter và JIT profiling/compilation.
- Heap expansion, object retention/live-set changes.
- GC cycles và concurrent worker startup.
- Application caches, connection pooling.
- OS page cache/CPU frequency transitions.

Nếu bạn đo JVM mới khởi động 2 giây, kết quả có thể chủ yếu phản ánh **startup/warmup**. Nếu mục tiêu là startup latency, hãy tạo thí nghiệm riêng. Nếu mục tiêu steady-state GC, phải warmup đủ và đo dài hơn multiple GC cycles.

### Điều kiện dừng warmup

Thay vì cố định “warmup 5 giây” vô điều kiện:
- Quan sát rolling throughput và latency.
- Kiểm tra JIT/compiler activity.
- Xem heap/live-set đã ổn định đại diện chưa.
- Đảm bảo các modes cùng mức tải và cùng warmup policy.
- Đặt upper bound để không bị warmup vô hạn nếu workload có memory leak.

### Điều kiện loại mẫu

Một run không được coi “PASS” khi:
- Exit code khác 0, Java exception/OOME.
- Checksum sai.
- Sample operations count không hoàn tất.
- CPU/memory limit thay đổi hoặc host bị noisy neighbor nặng.
- GC selector không thực sự được bật.
- Logs bị mất không xác minh được mode (nếu validation là bắt buộc).
- Application results/timeouts không giống expected behavior.
- GC logs quá khác về mức chi tiết giữa collectors.

Ghi invalid count trong report; không lặng lẽ bỏ các mẫu xấu để đẹp biểu đồ.

## 18. Kết quả giả định và cách diễn giải

Các số sau là **giả định phục vụ phép tính**, không phải một phép benchmark đã đo:

| GC | Throughput median (ops/s) | Median of per-fork p99 (µs) | Median max latency (µs) | Ghi chú |
| --- | ---: | ---: | ---: | --- |
| Parallel | 380.000 | 4,3 | 81.000 | Throughput cao, long-tail spike |
| G1 | 350.000 | 3,9 | 19.000 | Cân bằng |
| ZGC | 290.000 | 3,7 | 5.200 | Low max-latency in sample |

Dưới **các số giả định trên**:
- Parallel có throughput cao hơn G1 khoảng `(380000-350000)/350000 ×100 ≈ 8,6%`.
- ZGC giảm maximum latency trong các forks minh họa nhưng throughput thấp hơn.
- Chênh per-operation p99 chỉ vài phần mười microseconds rất dễ nằm trong timer/system noise.
- Không được suy request p99 cho một server từ các số microbenchmark này.

Nếu production SLO yêu cầu throughput≥300k ops/s và max latency<20.000 µs trong synthetic test, G1 đạt, Parallel không, ZGC có max tốt nhưng throughput thấp hơn threshold. Đây vẫn **chỉ là tình huống giả định**.

### Nguyên tắc ra quyết định

**Không chọn mode chỉ có thông lượng cao nhất** khi p99 vi phạm SLO. **Không chọn mode chỉ có pause thấp nhất** khi throughput không đủ, CPU quota bị throttle hoặc RAM vượt limit.

Công bố điều kiện “winning”: *cho workload W, JDK V, heap H, CPU quota C, container memory M và target SLO S, mode X đạt tất cả thresholds*.

## 19. Vòng tuning sau baseline: quản lý công bằng

Sau vòng fixed-heap, có thể thử collector-specific tuning:
- G1: nới/giảm `MaxGCPauseMillis`, cân nhắc heap và để young sizing adaptive.
- Parallel: chọn heap sizing/parallel workers theo CPU budget và throughput.
- ZGC: đảm bảo concurrent GC có đủ CPU, memory headroom và allocation rate phù hợp.
- Shenandoah nếu hỗ trợ: dùng tài liệu JDK vendor cho flags tương ứng.

**Cần công bằng về effort:** ví dụ tối đa 5 cấu hình và 30 phút thử cho mỗi GC, cùng tải và hardware. Không tuning G1 suốt tuần còn ZGC chỉ thử default 5 giây rồi kết luận chênh quá lớn.

Tuned comparison trả lời “collector + config nào tốt hơn dưới budget”, không còn là phép cô lập collector flag duy nhất. Ghi exact command lines, report baseline và tuned results riêng.

## 20. Container CPU/RAM làm lệch kết quả thế nào?

Trên Linux/Kubernetes:
- CPU quota tạo throttling khi GC threads và application threads cùng dùng hết budget.
- ActiveProcessorCount điều chỉnh JVM ergonomics, **không làm quota của cgroup tăng**.
- Heap `-Xmx` không giới hạn total RSS: code cache, thread stacks, GC-native structures, direct buffers và file cache vẫn chiếm memory.
- GC nào có nhiều concurrent workers có thể cạnh tranh CPU mạnh hơn dưới quota hẹp.
- Với memory limit sát heap, OOMKilled có thể xảy ra mà không có Java heap OOME.

Thu đồng thời:
- cgroup v2 `cpu.max`/`cpu.stat` và `memory.max`/`memory.current`/`memory.events` khi có.
- Process CPU time/RSS/PSS.
- `jcmd PID GC.heap_info` hoặc JFR/GC logs.
- GC events, application p99, timeout/errors.

Không chọn GC từ laptop 16 cores rồi áp vô điều kiện lên production container quota 1 CPU. Cần thử đúng target resource envelope.

## 21. Kiểm tra p99 và tail risk đúng cách

Với latency histogram, p99 của 1.000 requests thường chỉ bỏ qua 10 requests chậm nhất. Nếu một sự cố hiếm xảy ra 1 lần/10.000 requests, p99 có thể **không thấy** trong một mẫu quá nhỏ.

Để đo p99.9/p99.99 cần:
- Số requests lớn tương ứng.
- Stable load generator không saturation.
- Histogram với resolution và rounding thích hợp.
- Accounting timeouts và incomplete requests.
- Measurement windows đủ dài cho collector cycles/humongous allocations/peak load.
- Nếu có biến thiên giữa forks, report distribution of per-fork metrics và confidence ranges.

**Không lấy trung bình percentiles từ từng window rồi gọi là global percentile.** Nếu cần toàn bộ distribution, gộp underlying observations hoặc histograms tương thích.

### p99 GC pause cũng phải có đủ sự kiện

Nếu một collector chỉ có 4 GC pauses trong measurement, “p99 GC pause” gần như chính là max của bốn events, rất bất ổn. Báo **count**, percentile method, max và duration; tránh suy “GC p99 ổn định 1,2 ms” từ vài GC events.

## 22. Báo cáo chuẩn nên chứa những gì?

~~~text
EXPERIMENT
  goal: compare collectors under same conditions
  JDK vendor/build/version:
  OS / architecture / container image digest:
  Host resources and cgroup CPU/memory limits:
  Application binary hash / workload definition:
  JVM flags shared by all modes:
  Collector-specific flags:
  Warmup / measured duration / number of forks:
  GC logs/JFR/profiling policy:

VALIDATION
  correct collector from JVM logs:
  same checksum/response validity:
  successful runs and invalid runs:
  exit codes, OOME, OOMKilled, warnings:

RESULTS PER COLLECTOR
  offered requests/s:
  successful throughput ops/s or rps:
  response p50/p95/p99/max:
  timeouts/errors:
  GC pauses p50/p95/p99/max and count:
  GC concurrent CPU or total process CPU:
  allocation rate/live set/heap after GC:
  process RSS/container memory peak:
  variability / confidence interval:

CONCLUSION
  modes that meet SLO and resource budget:
  trade-off and Pareto candidates:
  limits of the benchmark:
  next experiment / tuning and rollback:
~~~

Nên giữ raw CSV, GC logs, exact commands và tool versions để đồng nghiệp có thể tái lập. Nếu công bố một bảng chỉ có “collector, throughput” mà thiếu p99 và memory, nó **chưa hoàn thành yêu cầu đánh giá GC cho latency-sensitive service**.

## 23. Những sai lầm phổ biến

| Ngộ nhận/sai lầm | Sự thật |
| --- | --- |
| “GC pause ngắn nhất nghĩa HTTP p99 tốt nhất.” | Còn queues, app CPU, DB, network, locks, throttling. |
| “Một run đủ xác nhận.” | OS/JIT/GC behavior có variance; cần independent forks. |
| “Chỉ báo ops/s là đủ.” | Pauses/tail latency có thể vi phạm SLO. |
| “Chỉ báo p99 là đủ.” | Mode có thể throughput thấp, memory cao hoặc timeouts nhiều. |
| “Cùng Xmx = cùng total process memory.” | GC-native overhead/direct/stacks/RSS khác. |
| “Cùng máy đủ, không cần cố định quota.” | cgroup CPU/RAM và host contention quan trọng. |
| “GC event count ít nhất = tốt nhất.” | Ít long pauses có thể tệ hơn nhiều short pauses. |
| “Low STW fraction = low total GC CPU overhead.” | Concurrent GC threads vẫn tiêu CPU. |
| “p99 của GC log bằng p99 HTTP.” | Đo những events khác nhau. |
| “p99 trung bình của 12 forks là global p99.” | Percentiles không cộng/trung bình trực tiếp. |
| “Chỉ đo completed requests, bỏ timeouts.” | Tail/error rate bị che giấu. |
| “So p99 ở max rps khác nhau là công bằng.” | Hai modes chạy tại offered load khác nhau. |
| “Coordinated omission không quan trọng khi GC pause lớn.” | Closed-loop generator bỏ arrivals trong khi service bị dừng. |
| “Một synthetic one-thread loop đại diện production.” | Object graph/concurrency/I/O khác thực tế. |
| “Cứ thêm -XX:MaxGCPauseMillis là đạt latency SLO.” | G1 goal mềm và không kiểm soát non-GC latency. |
| “Tuning flags G1 có cùng semantics trên ZGC.” | Collector-specific options khác nhau. |
| “ZGC Java 17/21/25 giống nhau.” | Generational mode thay đổi giữa releases. |
| “Nếu ZGC không chạy trên 128Mi heap thì mọi nơi ZGC kém.” | Resource configurations khác; báo invalid/suitability. |
| “Log debug nhiều hơn ở riêng ZGC không sao.” | Đã thay logging overhead, mất controlled comparison. |
| “JFR miễn phí nên bật riêng một mode.” | JFR có overhead; phải dùng policy giống nhau hoặc diagnostic run riêng. |
| “Một collector thắng mọi metric mọi lúc.” | Đánh đổi throughput/latency/CPU/RAM tùy workload. |
| “Không thấy Full GC là benchmark tốt.” | Cần data về throughput, allocation stalls, pauses, app SLO. |
| “Collector xếp hạng theo thời gian chạy command -version.” | Không có workload GC đại diện. |

## 24. Câu hỏi tự kiểm tra

**1. Vì sao phải báo throughput và latency cùng nhau?**  
Bởi GC có thể tối ưu một mặt bằng cách tiêu CPU, memory hoặc tạo pauses ảnh hưởng mặt còn lại.

**2. G1 `MaxGCPauseMillis=100` có bảo đảm response p99≤100ms?**  
Không. Nó chỉ là soft GC pause target, không phải request SLO.

**3. Vì sao `-Xmx256m` bằng nhau chưa đảm bảo memory fairness?**  
Vì GC-native/mapped memory, threads, code cache và RSS khác nhau.

**4. Tại sao phải dùng cùng JDK binary?**  
Nếu đổi version, không thể cô lập tác động GC từ JIT/runtime/library changes.

**5. Java 17 ZGC giống Java 25 ZGC không?**  
Không; generational mode đã trở thành default và non-generational bị loại bỏ.

**6. Nếu `-XX:+UseZGC` không được JVM nhận, nên làm gì?**  
Đánh dấu UNSUPPORTED; không bật ignore unknown options hoặc đổi JDK riêng cho ZGC mà vẫn gọi controlled experiment.

**7. `throughputOpsSec` của demo đo gì?**  
Số measured allocation operations hoàn thành trên mỗi giây trong single-thread closed-loop loop.

**8. `p99Us` của demo có phải HTTP request latency?**  
Không. Nó đo thời gian một synthetic Java operation gồm timer overhead.

**9. Vì sao `maxUs` có thể cao trong khi `p99Us` thấp?**  
Vì số operation bị long STW pauses có thể quá ít để ảnh hưởng p99.

**10. Vì sao checksum phải giống nhau mọi mode?**  
Để phát hiện chương trình chạy thiếu/sai/corrupt hoặc bị lỗi trước khi dùng thời gian.

**11. Một GC bị heap OOME giữa đo có được xếp hạng với throughput=0?**  
Không. Báo failure/invalid và resource conditions.

**12. Closed-loop generator có thể che điều gì?**  
Missed/scheduled arrivals và queueing trong các pause dài (coordinated omission).

**13. Per-fork median p99 có bằng global p99?**  
Không. Đó là thống kê theo forks; gộp raw data/histogram mới có global percentile đúng.

**14. JMH dùng để làm gì?**  
Làm microbenchmark có kiểm soát JIT/forks/iterations; không thay end-to-end service test.

**15. JFR dùng để làm gì trong thử GC?**  
Quan sát runtime GC/allocations/threads/CPU events, có overhead và nên dùng công bằng.

**16. Tại sao số GC events ít hơn không chắc tốt hơn?**  
Vì một vài pauses dài có thể gây p99 xấu hơn nhiều short pauses.

**17. Cùng load point nghĩa gì?**  
Cùng offered request arrival rate và input mix để so latency fairness; capacity là metric riêng.

**18. Làm sao phát hiện CPU throttling?**  
Quan sát cgroup cpu.stat deltas/quota và correlate application p99/throughput.

**19. Khi nào Parallel GC đáng chọn?**  
Batch/throughput-oriented workload chấp nhận long pauses, sau khi đo chứng minh đạt mục tiêu.

**20. Kết luận hợp lệ trông thế nào?**  
“Trên ứng dụng W, JDK V, heap H, CPU limit C và offered load L, mode X đạt throughput T và p99 P trong RAM M; các modes khác và uncertainty được báo đầy đủ.”

## 25. Bản đồ ghi nhớ

~~~text
                         SAME APPLICATION
                         SAME EXACT JDK
                         SAME WORKLOAD
                         SAME CPU / RAM
                                |
             +------------------+------------------+
             |                  |                  |
          PARALLEL              G1                ZGC
             |                  |                  |
             +------------------+------------------+
                                |
                     VALIDATE GC + CHECKSUM
                                |
                     WARMUP + JVM FORKS
                                |
                     ALTERNATE RUN ORDER
                                |
                +---------------+----------------+
                |                                |
         THROUGHPUT                          LATENCY
         successful ops/s               p50 / p95 / p99 / max
                |                                |
                +---------------+----------------+
                                |
                   GC pauses + concurrent CPU
                   heap, native, RSS, errors
                                |
                       SAME LOAD POINTS
                       SLO / HEADROOM
                                |
                     PARETO TRADE-OFF
                                |
                   OPTIONAL TUNING PER GC
                     SAME TOTAL BUDGET
                                |
                       REPRODUCIBLE REPORT
~~~

**Kết luận:** Một phép thử GC đáng tin phải giữ nguyên **JDK, ứng dụng, input, CPU/RAM budgets và phương pháp đo**, thay collector trong những runs độc lập, warmup và lặp đủ lâu. Báo **successful throughput** song song với **application latency p95/p99**, đối chiếu với **GC pause distributions**, CPU và native memory. GC nào “thắng” phụ thuộc workloads, SLO và headroom. Đừng dùng một benchmark ngắn, một JDK khác hoặc chỉ một log pause để kết luận hiệu năng sản xuất.

## Tài liệu tham khảo chính thức

- [Oracle JDK 17 — The java Command: UseParallelGC, UseG1GC, UseZGC, Xlog](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).
- [Oracle JDK 21 — The java Command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).
- [Oracle JDK 21 — Garbage Collection Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/).
- [Oracle JDK 21 — G1 Garbage Collector Overview](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html).
- [Oracle JDK 21 — G1 Performance Tuning](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-garbage-collector-tuning.html).
- [Oracle JDK 21 — Parallel Collector](https://docs.oracle.com/en/java/javase/21/gctuning/parallel-collector1.html).
- [Oracle JDK 21 — ZGC](https://docs.oracle.com/en/java/javase/21/gctuning/z-garbage-collector.html).
- [OpenJDK JEP 439 — Generational ZGC](https://openjdk.org/jeps/439).
- [OpenJDK JEP 474 — ZGC: Generational Mode by Default](https://openjdk.org/jeps/474).
- [OpenJDK JEP 490 — ZGC: Remove the Non-Generational Mode](https://openjdk.org/jeps/490).
- [OpenJDK JEP 158 — Unified JVM Logging](https://openjdk.org/jeps/158).
- [OpenJDK JEP 271 — Unified GC Logging](https://openjdk.org/jeps/271).
- [OpenJDK JMH — Java Microbenchmark Harness](https://github.com/openjdk/jmh).
- [Java Flight Recorder API (JDK 21)](https://docs.oracle.com/en/java/javase/21/jfapi/).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-experiments-008">Quan sát warm-up và nhận diện sai lệch do gấp hằng hoặc loại bỏ mã</a>

<details>
<summary>Xem chi tiết</summary>

# Quan sát warm-up và nhận diện sai lệch do gấp hằng hoặc loại bỏ mã

## 1. Tại sao một phép đo Java nhanh bất thường có thể là phép đo sai?

Trong JVM HotSpot, thực thi Java không chỉ có một trạng thái hiệu năng cố định. Ban đầu một method có thể được interpreter thực hiện, sau đó được JIT biên dịch bởi **C1**, thu thập thêm hồ sơ thực thi và có thể được tối ưu sâu bởi **C2**. Trong quá trình ấy, GC, class initialization, hệ thống cache và các thay đổi trạng thái runtime cũng có thể diễn ra.

Vì vậy, nếu chạy một vòng lặp bằng `System.nanoTime()` rồi nói “Java thực hiện 100 triệu phép toán chỉ trong vài microseconds”, có ít nhất ba khả năng rất khác nhau:

1. **Warm-up chưa ổn định:** bạn đo lẫn chi phí tải lớp, nạp thư viện, interpreter, profiling, JIT compilation và những thay đổi hiệu năng giữa các tiers.
2. **Constant folding / constant propagation:** phép tính dùng đầu vào mà JIT chứng minh luôn cố định; compiler có thể thay phép tính bằng kết quả đã biết.
3. **Dead-code elimination (DCE) / dead-store elimination:** kết quả không ảnh hưởng bất kỳ hành vi quan sát được nào; JIT có thể loại toàn bộ phép tính hoặc cả vòng lặp.

Một phép đo nhanh không tự chứng minh implementation của thuật toán tốt. Trước khi so sánh nanoseconds/op cần hỏi **“công việc có còn thật sự xảy ra trong máy mã đã biên dịch không?”** và **“ta đang đo giai đoạn thực thi nào?”**

Bài thuộc **Thực nghiệm JVM**, dùng **Java 17, HotSpot, Java Microbenchmark Harness (JMH)** và các diagnostic tools để xác định ba hiện tượng trên. Các chi tiết tiered compilation, compiler flags và thời điểm tối ưu thuộc implementation cụ thể; JVM specification không bắt buộc mọi JVM phải có đúng C1/C2, log format hay compile threshold như HotSpot.

## 2. Warm-up là gì và những gì có thể thay đổi theo thời gian?

**Warm-up** là giai đoạn chạy code trước thời điểm lấy kết quả measurement chính để hệ thống đạt trạng thái đủ đại diện cho mục đích benchmark. Nó không chỉ là một số vòng lặp bỏ qua tùy ý; tùy workload, nó có thể bao gồm:

- **Class loading, verification, initialization:** các classes cần thiết được nạp và chạy `<clinit>` khi lần đầu active use.
- **Interpreter execution:** bytecode được xử lý trước hoặc đồng thời với compiled code ở các phương thức khác.
- **Invocation/backedge profiling:** JVM quan sát hot methods/loops để quyết định compilation và OSR.
- **C1 compilation:** tạo mã máy tương đối nhanh, đôi khi tiếp tục thu type/branch profiles.
- **C2 compilation:** tối ưu sâu, có thể inline, constant-fold, scalar-replace và eliminate dead work.
- **Profile stabilization:** các receiver types, branch frequencies và loop behavior trở nên đại diện hơn cho tải thật.
- **Code cache activity:** compiled code được cài đặt, thay thế, loại khỏi đường gọi mới hoặc biên dịch lại khi giả định đổi.
- **Garbage collection:** allocations tạo ra trong quá trình khởi động và chạy thử có thể gây GC pauses.
- **JIT/GC threads và CPU frequency:** background work, OS scheduling, thermal effects và CPU power management cũng làm số đo thay đổi.
- **Application caches:** caches/JDBC pools/TLS contexts được khởi tạo; một phép đo startup có thể rất khác steady state.

Từ “đã warm” không phải lời cam kết rằng mọi run về sau có latency bất biến. HotSpot có thể **deoptimize, recompile, load thêm classes** hoặc gặp profile shift giữa lúc benchmark đang chạy.

### Timeline minh họa

```text
Thời gian ---------------------------------------------------->

Startup       Warm-up                           Measurement
 |                |                                   |
 Class init       Interpreter / C1                     |
 JIT queue        Profile, OSR, C2                     |
 First GC         Caches warming                       |
 |                |                                   |
 latency thay đổi   phần lớn hotspot ổn định?           nhiều mẫu
                                                       + thống kê

Đây là mô hình; các pha có thể chồng lấn và tái diễn.
```

**Không được đặt mốc “sau đúng 10.000 calls mọi method đều C2”**. HotSpot có dynamic tiered policy, compiler queues và nhiều compile modes; JIT có thể chọn OSR khi method chỉ được invoke ít nhưng loop chạy rất lâu.

## 3. Warm-up và compiler tiers trên HotSpot Java 17

Các level thường gặp trong HotSpot tiered compilation:

| Level | Execution engine | Ý nghĩa khi quan sát |
| --- | --- | --- |
| **0** | Interpreter | Bytecode được diễn giải; VM có thể ghi hotness/profile |
| **1** | C1 native code, không full profiling | Mã máy nhanh hơn nhưng không nhất thiết có C2 tối ưu sâu |
| **2** | C1 với limited profiling | Ghi một số invocation/backedge signals |
| **3** | C1 với richer profiling | Thu type/branch/receiver feedback dùng cho tối ưu sâu |
| **4** | C2 | Mã tối ưu cao; constant folding, DCE, inlining, EA có thể tham gia |

Các mức này **không phải một chuỗi bắt buộc 0→1→2→3→4**. Có thể nhảy mức, OSR compile, giữ ở tier thấp hoặc không compile.

Một benchmark vòng lặp dài có thể được **OSR compiled giữa invocation**. Trong log `PrintCompilation`, dấu `%` và `@ BCI` thường gợi ý OSR tại một bytecode index. Nhưng một dòng compile event không chứng minh mã C2 của method đã chạy trong toàn bộ thời gian measurement.

### Constant folding và DCE có thể bắt đầu ngay trong javac không?

Một số constant expressions được **Java compiler `javac`** gấp ngay từ source khi phù hợp ngữ nghĩa Java; các phép khác chỉ có thể được **JIT** suy ra sau inlining và phân tích runtime.

Do đó khi thấy kết quả không chứa phép tính ban đầu, cần phân biệt:

- **Source constant folding by javac**: class-file bytecode đã chứa hằng.
- **JIT constant folding**: bytecode vẫn chứa operation, nhưng compiled machine code được thay đổi dựa trên thông tin dynamic/static trong compiled graph.
- **DCE**: giá trị hoặc toàn bộ path không ảnh hưởng hành vi quan sát được nên compiler không cần thực thi phần tính ấy.

Đừng tuyên bố mọi `int x = 2*3` là “C2 cực mạnh”: `javac` đã có quy tắc compile-time constant expressions từ Java language.

## 4. Gấp hằng (constant folding) là gì?

Nếu compiler xác định một biểu thức cho **một kết quả không đổi**, nó có thể tính trước và thay expression bằng constant.

```java
int total = 6 * 7;
System.out.println(total);
```

Đây là fragment. `javac` có thể tạo bytecode đưa thẳng `42` vào operand stack, không cần một bytecode `imul`. Đây là **source-level constant expression**.

Một tình huống khác:

```java
static int polynomial(int x) {
    return x * x + 3 * x + 1;
}

static int byLiteral() {
    return polynomial(7);
}
```

Đây là fragments. Bytecode có thể còn `invokestatic polynomial`. Nhưng nếu JIT inline `polynomial` vào `byLiteral` và thấy tham số `x=7`, phép tính có thể được gấp thành `49 + 21 + 1 = 71` ở compiled graph. Điều này không có nghĩa method `polynomial` **luôn** trả 71 khi các caller khác truyền giá trị khác.

**Constant folding hợp lệ** nếu compiler giữ mọi tác dụng mà chương trình phải quan sát. Ví dụ gọi method có I/O, volatile accesses, exceptions hay cập nhật shared state không thể bị loại chỉ vì final output vô tình là một constant.

### Constant propagation và loop-invariant code motion

Constant folding thường đi cùng:

- **Constant propagation**: truyền giá trị đã biết qua assignments/calls sau inlining.
- **Loop invariant code motion (LICM)**: đưa phép tính không phụ thuộc iteration ra ngoài loop, nếu hợp lệ.
- **Strength reduction**: thay phép toán bằng cách rẻ hơn khi giữ đúng semantics.
- **Loop unrolling/vectorization**: thay đổi control flow, số instructions và memory accesses trên compiled path.

Vì vậy khi một benchmark dùng constant input trong mỗi iteration, nó có thể thực tế đo **trả về một hằng**, không phải thuật toán mình muốn so.

## 5. Java 17 runnable: so javac constant và giá trị runtime

Tạo file **`ConstantFoldingDemo.java`**:

```java
public class ConstantFoldingDemo {
    private static volatile int current = 9;

    static int polynomial(int x) {
        return x * x + 3 * x + 1;
    }

    static int byLiteral() {
        return polynomial(7);
    }

    static int byRuntimeInput() {
        return polynomial(current);
    }

    public static void main(String[] args) {
        int compileTime = 6 * 7;
        System.out.println("compileTime=" + compileTime);
        System.out.println("literalCall=" + byLiteral());
        System.out.println("runtimeInput=" + byRuntimeInput());
    }
}
```

Biên dịch và chạy Java 17:

```bash
javac --release 17 ConstantFoldingDemo.java
java ConstantFoldingDemo
```

**Stdout chính xác khi không có thread khác sửa `current`:**

```text
compileTime=42
literalCall=71
runtimeInput=109
```

Kiểm tra: `polynomial(7) = 49+21+1=71` và `polynomial(9) = 81+27+1=109`.

### Hiểu kết quả đúng

- `compileTime=42` là ví dụ cổ điển của constant expression ở source, không cần JIT để có 42.
- `byLiteral()` là **ứng viên** JIT inline/gấp hằng, nhưng ba dòng output **không chứng minh** C2 đã biên dịch hoặc thật sự gấp lời gọi đó.
- `current` là `volatile`; JIT không được thay mọi volatile reads bằng một constant vĩnh viễn khi có khả năng thay đổi hợp lệ. Nhưng nó vẫn có thể tối ưu **trong phạm vi thông tin thực tế và mọi JMM constraints cho phép**.
- Một chương trình chỉ gọi mỗi method một lần có thể kết thúc trước khi các method được JIT compiled.

Quan sát bytecode:

```bash
javap -c -p ConstantFoldingDemo
```

Tìm các method `byLiteral` và `byRuntimeInput`: compiler/JDK có thể giữ `invokestatic polynomial` và `getstatic current`. **`javap` không hiển thị mã máy C2**, vì vậy không thể dùng chỉ một bytecode listing để kết luận JIT đã hay chưa gấp hằng.

## 6. Dead-code elimination (DCE) là gì?

**DCE** loại bỏ computations không còn ảnh hưởng tới những hành vi mà chương trình phải quan sát. Ví dụ:

```java
static int expensive(int x) {
    int result = x;
    for (int i = 0; i < 100; i++) {
        result = result * 31 + i;
    }
    return result;
}

static void benchmarkWrong() {
    expensive(7); // Bỏ kết quả.
}
```

Đây là fragments. Nếu `expensive` không có observable side effects và compiler thấy kết quả bị bỏ, HotSpot C2 có thể inline và xóa toàn bộ phép tính. Một phép đo thời gian `benchmarkWrong` sau JIT có thể rất nhỏ **vì chẳng còn đo phép tính**.

**Đây không phải lỗi của JVM**. Compiler làm đúng nghĩa vụ: Java không yêu cầu phải tiêu CPU cho kết quả bị vứt đi khi không ảnh hưởng trạng thái và exceptions quan sát được.

### DCE khác dead-store elimination

- **Dead-code elimination:** loại một statement/subgraph không ảnh hưởng output.
- **Dead-store elimination:** bỏ ghi vào một field/local/array slot nếu giá trị bị ghi đè trước khi có thể đọc và không có tác dụng quan sát khác.
- **Allocation elimination:** bỏ tạo object khi compiler chứng minh object không cần materialize (có thể liên quan escape analysis/scalar replacement).
- **Loop elimination:** nếu vòng lặp chỉ tạo dead values và điều kiện/termination cho phép, compiler có thể loại toàn bộ loop.
- **Inlining** thường làm compiler phát hiện đường dữ liệu chết ở phạm vi rộng hơn.

Không khẳng định mọi vòng lặp kết quả bỏ đi **chắc chắn** biến mất: exceptions, volatile accesses, synchronization, method calls chưa inline, side effects và khó khăn phân tích có thể giữ nó tồn tại.

## 7. Side effects: những điều compiler không thể đơn giản xóa

Ví dụ khác:

```java
static volatile long published;

static long meaningful(int x) {
    long value = x;
    for (int i = 0; i < 100; i++) {
        value = value * 31 + i;
    }
    published = value;
    return value;
}
```

Đây là fragment. Ghi `published` là một observable volatile write; compiler không thể xóa write ấy nếu nó là một phần behavior của method được chạy. Tuy nhiên compiler vẫn có thể **gấp hoặc đơn giản hóa cách tính** giá trị trước write nếu biết input.

Cần phân biệt:

- **Giữ output có thể quan sát** tránh loại hết đường dữ liệu một cách tùy tiện.
- **Giữ đúng lượng công việc dự định** lại là vấn đề khác: nếu inputs quá dễ đoán, JIT vẫn có thể biến thuật toán thành phép tính ít hơn.
- **Ghi một `volatile` mỗi iteration** có overhead và ordering semantics, làm benchmark không còn đo chủ yếu thuật toán.
- **`System.out.println` mỗi iteration** tốn I/O, synchronization và formatting, che khuất JIT/CPU cost.

Tốt hơn là có một benchmark harness tiêu thụ kết quả ngoài đường tính chính, như **JMH return value** hoặc **`Blackhole.consume`**.

## 8. Demo Java 17: kết quả được sử dụng, nhưng không chứng minh DCE

Tạo file **`ObservedResultDemo.java`**:

```java
public class ObservedResultDemo {
    static volatile int input = 8;
    static volatile int sink;

    static int calculate(int x) {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += (x + i) % 7;
        }
        return sum;
    }

    static void discardedWork() {
        calculate(input);
    }

    static void observedWork() {
        sink = calculate(input);
    }

    public static void main(String[] args) {
        discardedWork();
        observedWork();
        System.out.println("result=" + sink);
    }
}
```

```bash
javac --release 17 ObservedResultDemo.java
java ObservedResultDemo
```

**Stdout chính xác** trong demo không có writer nào khác:

```text
result=297
```

Vì `(8+i)%7` trải 14 chu kỳ đủ 7 residues, mỗi chu kỳ tổng `0+1+...+6=21`, được 294; còn hai residues 1 và 2, tổng 297.

**Giải thích đúng:**

- `discardedWork()` bỏ kết quả; tùy compilation/inlining, phần loop tính toán có thể bị eliminate. `volatile input` vẫn là một read có semantics và không được loại tùy tiện.
- `observedWork()` publish kết quả qua `volatile sink` nên output phải chính xác, nhưng compiler có thể tối ưu phép toán trong `calculate` nếu vẫn đúng.
- Chạy hai methods đúng một lần **không** đo được mức DCE hay performance difference. Đây là một chương trình correctness và ví dụ về **tính quan sát được**.

## 9. Warm-up: vì sao một lần đầu chậm hơn các lần sau?

Một lần đầu tiên gọi method có thể chịu:

1. Class initialization và static fields setup.
2. JIT chưa có compiled code hoặc mới có C1 tier thấp.
3. Lấy profile branch/receiver để xác định hot call sites.
4. Compilation threads đang xử lý queue.
5. Class libraries/caches chưa warm.
6. Memory pages chưa resident, CPU branch predictor/caches chưa thích nghi.
7. GC hoặc safepoints trùng vào đo.
8. JIT compilation/deoptimization có thể xảy ra ngay trong phase measurement.

Nếu sau 20 lần gọi throughput tăng, **có thể** JIT đã giúp, nhưng không đủ kết luận “C2 xảy ra đúng lần thứ 20”. Có thể input data/cache khác, OS CPU scaling hoặc GC không trùng nữa.

### Dấu hiệu bằng đồ thị

```text
Thời gian mỗi batch (ns/op)     (mô hình, không phải số liệu thật)
^
|   X X
|    X      X
|      X  X
|        X     X
|          X       X X  X X   X
+------------------------------------------> batch index
     startup/interpreter    optimized, noisier plateau

Một đồ thị thật có thể không giảm đều và có thể tăng lại.
```

Không “chọn batch nhanh nhất” làm headline mà không báo variance và measurement protocol. Một số batches có spikes do GC, deoptimization hoặc OS scheduler; đó là dữ liệu cần hiểu chứ không phải luôn là outliers có thể bỏ.

## 10. Java 17: bài quan sát phases nhưng không kết luận từ một con số

Tạo file **`WarmupPhasesDemo.java`**:

```java
public class WarmupPhasesDemo {
    private static volatile long checkpoint;

    static long calculation(int n) {
        long sum = 0;
        for (int i = 0; i < n; i++) {
            int k = i % 10;
            sum += k * k;
        }
        return sum;
    }

    public static void main(String[] args) {
        final int phases = 3;
        final int callsPerPhase = 100;
        long allResults = 0;

        for (int phase = 0; phase < phases; phase++) {
            long started = System.nanoTime();
            long partial = 0;
            for (int call = 0; call < callsPerPhase; call++) {
                partial += calculation(10_000);
            }
            long elapsed = System.nanoTime() - started;
            checkpoint = partial;
            allResults += partial;

            // Thời gian động; không có expected timing chung.
            System.err.println("phase=" + phase
                    + ",elapsedNs=" + elapsed);
        }

        System.out.println("phases=" + phases);
        System.out.println("calls=" + phases * callsPerPhase);
        System.out.println("checksum=" + allResults);
    }
}
```

```bash
javac --release 17 WarmupPhasesDemo.java
java WarmupPhasesDemo
```

**Ba dòng stdout của chương trình** (không kể stderr đo thời gian):

```text
phases=3
calls=300
checksum=85500000
```

Lý do: bình phương `0..9` có tổng `285`; trong 10.000 iterations lặp 1.000 chu kỳ nên một `calculation` trả `285.000`, gọi 300 lần tổng là `85.500.000`.

**Thời gian trên stderr biến thiên theo runtime** và **không được cho output mẫu giả như benchmark đã đo**. `System.nanoTime()` dùng một time source thích hợp để lấy độ chênh lệch ngắn, nhưng vòng lặp ở đây:

- Chưa có JMH forks/warmup/measurement iterations chuẩn.
- Có thể bị compiler tối ưu nhờ `calculation(10_000)` luôn cùng input; viết `checkpoint` không ngăn constant folding toàn phần.
- Có độ nhiễu từ OS/JIT/GC, và chỉ 3 phases quá ít để kết luận thống kê.
- Chỉ đo một method rất giống nhau nhiều lần; không đại diện cho phân bố production inputs.

**Bài học:** thấy `elapsedNs` giảm **không đủ** chứng minh code thật đã nhanh lên do JIT, cũng không chứng minh vòng lặp vẫn tiêu cùng số CPU operations ở mỗi phase.

## 11. Làm sao quan sát JIT compilation trong khi chạy?

Trên HotSpot Java 17:

```bash
java -XX:+PrintCompilation WarmupPhasesDemo
```

Thông tin compiler có thể chứa timestamp, compilation ID, level (C1/C2), tên class/method, bytecode size, trạng thái `made not entrant`, và `%`/`@ BCI` cho OSR.

Cần ghi rõ **không có expected log duy nhất**: compiler threads bất đồng bộ, JDK vendor/build, CPU và flags có thể khác nhau. Một method được listed at tier 4 không bảo đảm mọi call sau đều dùng cùng compiled version; những methods khác có thể được inline vào callers.

Để xem lý do inlining trên build hỗ trợ:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining WarmupPhasesDemo
```

Có thể thấy compilation/inlining của nhiều JDK methods ngoài code đang nghiên cứu. Diagnostic log có overhead và làm thay đổi timing; khi benchmark performance, nên **tách lượt đo có instrumentation và lượt đo clean**.

### OSR và loop-specific warm-up

Khi `calculation` chỉ được gọi ít lần nhưng loop rất dài, backedge counters có thể làm HotSpot compile một OSR entry giữa lần gọi. Do đó “một invocation” không đồng nghĩa toàn bộ method đã interpreted suốt lần ấy.

Đừng gắn một công thức “10.000 iterations là threshold OSR” cho mọi build: threshold và policy phụ thuộc nhiều yếu tố.

## 12. `-Xint` và compiler tiers: so gì là hợp lệ?

Thử các cấu hình sau **trong những process riêng**:

```bash
java -Xint WarmupPhasesDemo
java -XX:TieredStopAtLevel=1 WarmupPhasesDemo
java -XX:+PrintCompilation WarmupPhasesDemo
```

- `-Xint`: HotSpot chạy ở chế độ interpreted-only; không phải một “JIT baseline” có cùng semantics về compiler profile với default tiers.
- `-XX:TieredStopAtLevel=1`: giới hạn tiered compiler tới C1 level 1 trong mô hình phổ biến, không tắt toàn bộ JIT.
- Default: có thể sử dụng C1/C2 và nhiều optimizations.

Có thể dùng chúng để **học execution stages**, nhưng **không so sánh một lần timing** rồi công bố “C2 nhanh gấp X lần”:

- Có khác biệt compiler overhead, warm-up và OSR.
- `-Xint` làm cả library/helper method chậm đi, không chỉ method mục tiêu.
- Measurement code/`println` cũng bị ảnh hưởng.
- JVM flag, GC, CPU power mode và OS scheduling phải được kiểm soát.
- Phương pháp/định nghĩa throughput, p50/p99 và sample count cần ổn định.

## 13. `javap` phân biệt bytecode từ source với JIT optimization

Dùng:

```bash
javap -c -p ConstantFoldingDemo
javap -c -p ObservedResultDemo
```

`javap` đọc `.class`, không nhìn thấy mã máy đã tối ưu. Nó giúp:

- Xác nhận một constant expression đã được `javac` gấp thành `bipush`/`ldc`.
- Xác nhận lời gọi method vẫn tồn tại trong bytecode và có cơ hội inlining ở runtime.
- Xác nhận vòng lặp trong bytecode qua `goto`/conditional branches.
- Phân biệt source statement với instruction sequences sau `javac`.

Nếu `javap` còn `imul`/`invokevirtual`, **không** suy chắc machine code vẫn chứa chúng. Ngược lại, nếu bytecode đã constant-fold, không được gán công lao ấy cho C2 khi phân tích riêng JIT.

## 14. JMH là công cụ chuẩn cho Java microbenchmark

**Java Microbenchmark Harness (JMH)** do dự án OpenJDK phát triển nhằm giảm các bẫy phổ biến:

- Chạy **warm-up iterations** và **measurement iterations** tách biệt.
- Chạy **forks** là các JVM processes riêng để giảm ảnh hưởng trạng thái compilation cũ.
- Cung cấp **`@State`** để giữ input/workload state và tránh những biến constant thiếu đại diện.
- Có cơ chế tiêu thụ kết quả bằng **return value** hoặc **`Blackhole.consume`**.
- Có các **modes** như throughput, average time, sample time, single-shot.
- Cho phép kiểm soát threads, operations per invocation, JVM args, profilers và reporting.
- Có thống kê nhưng vẫn cần đủ samples, confidence/variance và phân tích ứng dụng.

JMH **không phải** một thành phần Java SE mặc định đi cùng `javac`. Cần tải dependencies/build project phù hợp, thường dùng Maven/Gradle hoặc JMH archetype. Các phiên bản JMH cũng có thay đổi; dùng release hiện được project xác nhận, không đoán Maven version cứng từ trí nhớ.

### Tại sao cần fork?

Một JMH fork tạo một JVM mới với JIT profile, code cache, class loading và runtime state riêng. Nếu đo hai candidates trong **cùng một JVM sống rất lâu**, thứ tự chạy có thể ảnh hưởng compiled code và nhiệt độ CPU/caches. Fork không loại mọi yếu tố môi trường, nhưng giúp tránh một loại state carryover lớn.

### Warm-up vẫn phải hợp workload

JMH warm-up không “tự chứng minh” bạn đã đạt steady-state. Một chương trình có late initialization, adaptive behavior, background GC hoặc profile shift sau vài phút vẫn có thể đổi characteristics khi measurement đã bắt đầu.

Với dịch vụ production, còn cần **load test end-to-end**, không chỉ một JMH method chạy trong isolation.

## 15. Constant folding trong benchmark: literal và state khác gì?

Một benchmark sai thường viết:

```java
@Benchmark
public int wrong() {
    return compute(42);
}
```

Đây là **fragment yêu cầu JMH class/imports**. Nếu compiler thấy input luôn 42 và inline `compute`, có thể fold giá trị. Benchmark đo chi phí của **một constant** chứ không phải chi phí tính `compute(x)` cho runtime inputs.

Mẫu có state:

```java
@State(Scope.Thread)
public class BenchState {
    int input = 42;
}

@Benchmark
public int inputDriven(BenchState state) {
    return compute(state.input);
}
```

Đây cũng là **JMH fragment**, cần đúng project/class declarations. Một **non-final field trong `@State`** làm input khó được coi là compile-time literal hơn; tuy nhiên compiler vẫn có thể tối ưu một phần hoặc suy thêm facts trong một benchmark đơn giản. Để đại diện workload, hãy nghĩ đến **nhiều input values**, realistic distributions và những constraints mà real application có.

Các JMH samples chính thức về constant folding cảnh báo rằng **`final` state fields hoặc local constants** có thể khiến phép tính được thay kết quả bởi compiler.

## 16. DCE trong benchmark: việc trả kết quả và Blackhole

Ví dụ sai:

```java
@Benchmark
public void ignored() {
    compute(state.input); // result bị bỏ
}
```

Đây là fragment. Nếu `compute` không có side effect và JIT inline, work có thể biến mất.

Hai cách thường dùng:

**A. Trả giá trị từ `@Benchmark`:**

```java
@Benchmark
public int returned(BenchState state) {
    return compute(state.input);
}
```

JMH harness có trách nhiệm sử dụng kết quả phù hợp để tránh compiler coi cả benchmark result là dead.

**B. Dùng `Blackhole` khi cần tiêu thụ nhiều kết quả:**

```java
@Benchmark
public void twoOutputs(BenchState state, Blackhole bh) {
    int first = compute(state.input);
    int second = compute(state.input + 1);
    bh.consume(first);
    bh.consume(second);
}
```

Đây là JMH fragment; `Blackhole` thuộc `org.openjdk.jmh.infra`. Theo nguồn JMH chính thức, cần tránh tự tạo một “black hole” bằng `volatile` writes trong inner loop vì write wall/cache effects có thể áp đảo phép tính.

**Lưu ý lớn:** Blackhole giúp **ngăn DCE của kết quả đã cung cấp**, **không bảo đảm compiler giữ nguyên từng step bên trong phép tính**. Constant folding, strength reduction, inlining, vectorization và những tối ưu hợp lệ khác vẫn có thể xảy ra — và nhiều khi chúng chính là điều bạn muốn đo trong ứng dụng thực tế.

## 17. JMH benchmark hoàn chỉnh: nhận diện hai bẫy

Trong một project JMH hợp lệ có dependencies `jmh-core` và annotation processing `jmh-generator-annprocess`, tạo file **`src/main/java/lab/jvm/FoldAndDceBenchmark.java`**:

```java
package lab.jvm;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4)
@Measurement(iterations = 5)
@Fork(2)
public class FoldAndDceBenchmark {
    int input = 7;
    final int finalInput = 7;

    private int compute(int value) {
        int result = value;
        for (int i = 0; i < 10; i++) {
            result = result * 31 + i;
        }
        return result;
    }

    @Benchmark
    public int constantCandidate() {
        return compute(7);
    }

    @Benchmark
    public int finalFieldCandidate() {
        return compute(finalInput);
    }

    @Benchmark
    public int stateDriven() {
        return compute(input);
    }

    @Benchmark
    public void discarded() {
        compute(input);
    }

    @Benchmark
    public void consumed(Blackhole blackhole) {
        blackhole.consume(compute(input));
    }
}
```

**Đây là code đầy đủ trong ngữ cảnh project JMH có dependencies**, không thể biên dịch bằng `javac --release 17` đơn lẻ mà không thêm JMH jars và annotation processor.

Các benchmark methods có mục đích khác nhau:

| Method | Vấn đề cần nhìn | Điều không được kết luận tự động |
| --- | --- | --- |
| `constantCandidate` | Literal input cố định khiến constant folding dễ xảy ra | “Java tính compute(7) trong vài ns cho mọi x” |
| `finalFieldCandidate` | `final` state field có thể bị suy ra hằng khi inline | “JMH @State tự ngăn folding” |
| `stateDriven` | Non-final field tạo input runtime hơn literal | “Mọi phép toán đều giữ nguyên” |
| `discarded` | Return bị bỏ, có thể DCE rất mạnh | “CPU thực sự tính đầy đủ nhưng nhanh hơn” |
| `consumed` | Blackhole giữ result observable cho harness | “Chắc chắn giữ nguyên tất cả loop iterations” |

**Kết quả số nanoseconds/op không có giá trị cố định**; CPU/JDK/JMH version/profile đều ảnh hưởng. Một method `discarded` cho thời gian cực nhỏ chỉ là **tín hiệu nghi DCE**, cần thêm compiler/assembly evidence nếu muốn khẳng định chính xác graph nào bị xóa.

### Tạo project JMH và chạy thử

Cách thông dụng là sử dụng **JMH Maven archetype** được dự án hướng dẫn, ví dụ (cần Maven, truy cập Maven Central và phiên bản plugin phù hợp):

```bash
mvn archetype:generate -DinteractiveMode=false -DarchetypeGroupId=org.openjdk.jmh -DarchetypeArtifactId=jmh-java-benchmark-archetype -DgroupId=lab.jvm -DartifactId=jit-observe -Dversion=1.0-SNAPSHOT
```

Sau khi tạo project, đặt class vào thư mục package `lab.jvm` và chạy trong thư mục project:

```bash
mvn clean verify
java -jar target/benchmarks.jar FoldAndDceBenchmark
```

**Thực tế build:** một số Maven/JMH archetype versions hoặc môi trường không có Internet có thể yêu cầu chỉ định archetype version, proxy hoặc cấu hình compiler release 17. Tránh copy lệnh rồi coi nó là bảo đảm project luôn tạo được trên mọi môi trường. Kiểm tra project POM và `java -version`, đảm bảo forked benchmark JVM dùng Java 17. Một số shells có thể xử lý dấu `-D` khác nhau; các lệnh trên hướng tới Bash/CMD thông thường.

## 18. Đừng so sánh `discarded` với `consumed` như hai thuật toán tương đương

Đây là một so sánh có chủ ý để **phát hiện bẫy** chứ không nhằm chọn thuật toán nhanh:

- `discarded` không có ý nghĩa nghiệp vụ nếu kết quả không sử dụng, nên compiler được phép bỏ work.
- `consumed` giữ result observable cho harness, thay đổi nghĩa vụ codegen.
- Nếu `discarded` nhanh hơn 100 lần, đó không phải speedup của thuật toán mà là **một số work không còn cần**.
- Không dùng `Blackhole` rồi tuyên bố mọi loại overhead đã được loại. Blackhole có implementation cost, và JMH phải cân bằng nó cẩn thận.

Nếu benchmark một function có output thực tế luôn được dùng trong production, design benchmark cũng phải **consume output** tương ứng. Nếu production path thật sự bỏ output, có thể Java optimizer hợp lệ bỏ work và hiệu năng thực tế khác.

## 19. Các bẫy khác làm benchmark JVM sai

| Bẫy | Biểu hiện | Biện pháp |
| --- | --- | --- |
| **Constant folding** | Thời gian gần như zero vì input literal/final | Input từ realistic non-final state; kiểm tra optimized code |
| **Dead-code elimination** | Hàm `void` tính xong vứt result | Return/Blackhole consumption |
| **Loop elimination** | Một triệu iterations hoàn thành cực nhanh | Đảm bảo loop có data-dependent observable result |
| **Loop invariant hoisting** | Tính toán tưởng lặp N lần nhưng chuyển ra ngoài | Data thay đổi theo iteration nếu production thật sự như thế |
| **Inlining differences** | Benchmark tiny helper vs big service sai lệch | Giữ call-site/context tương đương |
| **Escape analysis** | Object allocation biến mất | Đo bytes/op; quyết định có cần object thật |
| **Autoboxing/cache** | Một số Integer allocations biến mất | Kiểm soát type, range, allocation profile |
| **Branch prediction** | Input luôn cùng nhánh nhanh lạ | Representative branch distributions |
| **Monomorphic receiver** | Một implementation được inline | Đo đúng polymorphism trong workload |
| **Cold classes** | First call rất chậm | Warm-up và báo riêng cold-start |
| **GC noise** | Tail latency spikes | GC logging/JFR cùng benchmark |
| **Safepoints/JIT background work** | Iteration time không ổn định | Warm-up, long duration, diagnostics |
| **Timer overhead** | Work quá ngắn hơn khả năng đo | Harness batching/iterations hợp lý |
| **Per-op println** | I/O thống trị phép tính | Không log trong hot loop |
| **Wrong baseline** | Setup tính trong measure cho A, ngoài measure cho B | Thiết kế scope/fixtures đối xứng |
| **Resource contention** | Benchmark chịu tải nền/thermal throttling | Giữ môi trường kiểm soát, nhiều forks |
| **False sharing/cache effects** | Threads ảnh hưởng cache lines | State scope, data layout, threads phù hợp |
| **Mixing modes** | So ns/op với ops/s sai | Ghi rõ mode và đơn vị |

Những bẫy này có thể cùng xuất hiện. Ví dụ state final vừa cho phép constant folding vừa làm DCE dễ hơn nếu benchmark discard result.

## 20. Setup/teardown: đo đúng phần nghiệp vụ

Trong JMH, `@Setup` và `@TearDown` dùng để chuẩn bị/dọn dữ liệu ở những level phù hợp. Nếu application thực tế chỉ parse payload **một lần** rồi query nhiều lần, đừng đặt parsing vào mỗi benchmark invocation trừ khi đó đúng use case.

Ngược lại, nếu production mỗi request phải parse một payload mới, benchmark chỉ gọi method trên pre-parsed object sẽ bỏ qua phần lớn cost thật.

Các scopes:

- `Scope.Thread`: state riêng cho từng benchmark thread; tránh data sharing không chủ đích.
- `Scope.Benchmark`: state chung trong cùng benchmark fork; thích hợp khi cố đo contention nhưng phải hiểu thread-safety.
- `@Setup(Level.Trial)`, `Level.Iteration`, `Level.Invocation` có lifecycle/overhead khác nhau; setup per invocation có thể làm measurement bị nhiễu nếu work quá nhỏ.

**Không “che” một thao tác đắt ngoài measurement chỉ để số đẹp.** Quy trình hợp lý là đo **micro-level kernel** và **end-to-end request** như hai câu hỏi riêng.

## 21. Blackhole dùng khi nào, không dùng khi nào?

JMH samples chính thức khuyến nghị:

- Nếu benchmark chỉ có **một kết quả**, thường **return trực tiếp** là rõ ràng.
- Nếu cần consume **nhiều kết quả** hoặc kết quả không nằm ở vị trí return, dùng `Blackhole`.
- Nếu benchmark trả `void` nhưng chỉ thực hiện pure computation, xem lại nguy cơ DCE.
- Không dùng `System.out.println` hay `volatile` per-op làm cách tiêu thụ mặc định; overhead có thể áp đảo microbenchmark.
- Không coi `Blackhole.consume` là lời hứa “không có compiler optimization nào được phép xảy ra”.

### Ví dụ phần lõi có hai outputs

```java
@Benchmark
public void twoKernels(Blackhole blackhole) {
    int a = compute(input);
    int b = compute(input + 1);
    blackhole.consume(a);
    blackhole.consume(b);
}
```

Đây là JMH fragment đặt trong class `FoldAndDceBenchmark`. JMH harness xử lý việc tiêu thụ kết quả, còn compiler vẫn có thể optimize những phần thực sự redundant theo Java semantics.

## 22. Có nên đưa giá trị đầu vào vào `volatile` để ngăn folding?

Đọc volatile có semantics quan sát được nên compiler không thể tùy ý thay nó bằng constant trong mọi execution. Nhưng đặt volatile read **bên trong hot loop** làm benchmark đo thêm chi phí volatile access và ordering, khiến code khác production.

Thay vào đó:

1. Dùng `@State` non-final fields khi phù hợp.
2. Khởi tạo/đổi input qua `@Setup` ở scope phù hợp.
3. Đảm bảo benchmark tiêu thụ result.
4. Dùng nhiều inputs/param values sát phân bố thực tế.
5. Dùng compiler diagnostics/assembly nếu cần chứng minh transformation.
6. Chỉ dùng volatile nếu **volatile access chính là thứ cần benchmark**.

Một phép tính với input `@State` non-final vẫn có thể được C2 vectorize, unroll hoặc hoist một phần — đây không phải lỗi nếu production context cũng cho phép.

## 23. `@Param` và dữ liệu kiểm thử đại diện

JMH cho phép khai báo tham số:

```java
@State(Scope.Thread)
public class WorkloadState {
    @Param({"7", "13", "101"})
    public int input;
}
```

Đây là JMH fragment. Các giá trị khác nhau cho biết performance có nhạy với input không, nhưng **mỗi fork/benchmark case vẫn có thể specialize** theo configuration; không có gì bảo đảm mọi loại constant folding bị vô hiệu hoàn toàn chỉ nhờ `@Param`.

Ví dụ một thuật toán mà production dùng `n` theo phân bố rộng:

- Nếu chỉ test `n=7`, compiler/caches/branch predictor có thể được “huấn luyện” vào trường hợp đẹp.
- Nếu production chủ yếu là `n=10_000`, test trên 7 là sai workload.
- Nếu thuật toán có early-exit paths, test cả inputs khiến early exit và inputs kích hoạt worst-case path.
- Nếu input là string/array, kiểm soát length, character distributions, memory layout và allocation lifecycle.

Đừng chọn parameters chỉ nhằm có mức thời gian “dễ báo cáo”. Mục tiêu là đại diện tình huống thực, không phải khiến JIT bối rối.

## 24. Quan sát warm-up bằng đồ thị đo nhiều iterations

Một bản phân tích hợp lý nên ghi cùng mỗi interval:

| Metric | Ý nghĩa |
| --- | --- |
| elapsed ns/op hoặc ops/s | Thay đổi throughput/latency theo iteration |
| GC count, pauses, allocation rate | Nhận biết samples chịu GC pressure |
| JIT compile events/tier transitions | Biết method nào có compiler activity gần thời điểm thay đổi |
| CPU usage và OS scheduling | Tách mất CPU do workload với nhiễu hệ thống |
| Histogram/p95/p99 khi liên quan | Tail latency không bị trung bình che khuất |
| Input parameters/workload version | Đảm bảo các intervals đo cùng điều kiện |
| JDK version, JVM flags, CPU | Tạo khả năng tái lập kết quả |

Nếu một đồ thị đổi đột ngột sau compilation log level 4, đó là **bằng chứng liên hệ thời gian**, chưa phải chứng minh duy nhất C2 gây khác biệt. GC, CPU scaling hoặc warm caches có thể trùng lúc.

Nếu đồ thị **không ổn định sau warm-up**, cách đúng là tìm nguyên nhân hoặc ghi giới hạn về steady-state; không tùy tiện bỏ các intervals chậm chỉ để biểu đồ phẳng.

## 25. JIT activity không đồng nhất với branch/receiver warm-up

Warm-up có thể thay vì code input distributions:

- Khi chạy chỉ một implementation của interface, JIT có thể thấy call site gần monomorphic và inline/specialize.
- Khi production đưa thêm implementations, profile trở nên bimorphic/polymorphic, JIT có thể fallback, deopt hoặc recompile.
- Constant folding có thể thuận lợi nếu `@State` value cố định trên cả warm-up và measurement.
- Data structures trong cache có thể thay đổi kích thước trong measurement, gây branch frequency khác.

Để benchmark đúng, warm-up phải có **đường thực thi tương đương workload đo**. Nếu warm-up dùng loại request A nhưng measurement dùng loại B, “ổn định” của A không chắc áp dụng cho B.

Một compilation event có dấu `made not entrant` có thể là trạng thái bình thường khi level cũ bị thay hoặc khi assumptions bị invalidated; không phải luôn là lỗi hoặc dấu hiệu memory leak.

## 26. Điều tra DCE: dùng bằng chứng nào?

Không có một API Java SE chung hỏi “phép cộng dòng 72 đã bị DCE chưa?”. Có thể kết hợp:

1. **Source/code review:** result có được dùng không? Có side effects/exception semantics hay không?
2. **`javap -c`:** compiler `javac` đã gấp hay còn bytecode? Chưa nói JIT.
3. **JIT logs:** method có được compile, inline hay không?
4. **Machine-code inspection** qua tooling thích hợp (ví dụ `-XX:+PrintAssembly` khi có disassembler plugin `hsdis` tương thích, perf-asm profiler/JITWatch), trong môi trường chẩn đoán.
5. **JMH A/B:** trả result/Blackhole so với discard result, có warm-up, forks và cùng input.
6. **CPU sampled profiling:** xem machine instructions/hot regions có phản ánh công việc được dự đoán không.
7. **Allocation profiling:** nếu object allocations biến mất, xem escape analysis, scalar replacement hay optimization khác.
8. **Correctness tests:** bảo đảm refactor không làm thay đổi output thật khi đưa workload trở lại production.

Nếu `discarded` nhanh bất thường và `consumed` chậm đáng kể, kết luận có trách nhiệm là **“phép thử discard có nguy cơ DCE; cần xác nhận bằng diagnostics”**, chứ không khẳng định đã quan sát chính xác optimizer IR khi chưa kiểm tra.

## 27. Cách phân biệt gấp hằng với loại bỏ mã

Hai hiện tượng có thể chồng lấn:

```java
static int compute(int x) {
    return x * 2 + 1;
}

static void wrongMeasurement() {
    for (int i = 0; i < 1_000_000; i++) {
        compute(7);
    }
}
```

Đây là fragment.

- `compute(7)` có thể được constant-fold thành `15` nhờ inlining.
- Vì `15` không được dùng, DCE có thể loại cả việc tính `15`.
- Vòng lặp có thể thành một đoạn trống hoặc bị eliminate nếu semantics cho phép.
- Một phép đo gần-zero không cho biết có bước nào đã xảy ra trước trong optimizer pipeline.

Mô hình:

```text
Source:  for (...) compute(7)
   |
   v
Inlining:        for (...) [7*2+1]
   |
   v
Constant folding:for (...) [15]
   |
   v
DCE:             for (...) [nothing]
   |
   v
Loop elimination (nếu hợp lệ): [nothing]
```

JIT không bắt buộc biến đổi đúng theo chuỗi trên; đây là cách **giải thích khả năng tối ưu**. Quan trọng là **có hay không tác dụng quan sát được**.

## 28. Benchmark đáng tin cần ghi metadata gì?

Một báo cáo benchmark có chất lượng nên kèm:

- **Tên thao tác** đo và phiên bản source/commit của code.
- **JDK version/vendor/build** (`java -version`), CPU/OS/architecture.
- JVM args: GC, `-Xms/-Xmx`, tier/compiler flags, code cache khi có liên quan.
- JMH version, mode, unit, warm-up/measurement iterations, thời lượng mỗi iteration.
- Fork count, benchmark threads, state scope và input parameters.
- Mean/median/p95/p99 và variance/confidence theo loại metric tương ứng.
- Allocation bytes/op hoặc GC events nếu work có allocates.
- Có bật instrumentation/logging trong measurement không.
- Workload có constant literal, final state field, mutable state input hay real input dataset.
- Kết quả có được consumed/returned để tránh DCE không.
- Lời giải thích các samples bất thường và những gì chưa kiểm chứng.

**Đừng báo một con số `2.1 ns/op` không gắn JVM và môi trường**. Nó không đủ để đồng nghiệp tái lập hoặc suy ra hiệu năng trên production.

## 29. Khi nào tự viết `System.nanoTime` vẫn có ích?

Tự viết đo thời gian không phải lúc nào vô ích. Nó thích hợp cho:

- **Smoke-check** rằng program chạy đúng trên môi trường mục tiêu.
- Quan sát định tính quá trình startup/first call, khi mục tiêu là **end-to-end cold start**.
- Đo một tác vụ lớn trong ứng dụng mà overhead timer nhỏ so với work, với nhiều trials và kế hoạch kiểm soát.
- Kiểm thử deadline/time budget ở mức ứng dụng khi semantics rõ ràng.

Nhưng nếu đo microseconds/nanoseconds cho một method nhỏ mà JIT có thể optimize, **JMH thường an toàn hơn** nhờ harness và compiler-awareness.

Nếu mục tiêu thật sự là **cold start**, đừng warm-up rồi đo và báo là startup latency. Ngược lại, nếu mục tiêu là steady-state throughput, đừng đo lần gọi đầu rồi so mã nguồn tối ưu.

## 30. Sai lầm khi dùng vòng lặp để giảm timer overhead

Một pattern phổ biến:

```java
long started = System.nanoTime();
for (int i = 0; i < 10_000_000; i++) {
    perform();
}
long elapsed = System.nanoTime() - started;
System.out.println(elapsed / 10_000_000.0);
```

Đây là fragment. Chia thời gian cho N không bảo đảm đã đo `perform()` N lần:

- `perform()` có thể inline rồi constant-fold hoặc DCE.
- Compiler có thể unroll/hoist/loop-eliminate khi semantics cho phép.
- Nếu work quá nhỏ, loop-control/timer overhead vẫn ảnh hưởng.
- Nếu `perform()` có shared side effects, synchronization/GC có thể là yếu tố chi phối.
- Nếu data luôn trong cache, kết quả khác production working set.
- Warm-up có thể diễn ra ngay trong loop, khiến measurement trộn interpreter, C1, C2.

**Đổi N thật lớn** không sửa được các vấn đề ngữ nghĩa benchmark. Cần đảm bảo result observability, representative input và measurement protocol.

## 31. Một JMH benchmark “không hợp lý” có thể giúp học, nhưng không thể công bố speedup

Nếu cố tình tạo:

```java
@Benchmark
public void nothingObservable() {
    for (int i = 0; i < 1_000; i++) {
        int answer = 6 * 7;
    }
}
```

Đây là fragment trong project JMH. `answer` không được dùng và constant đã biết. JIT có thể bỏ toàn bộ loop. Thời gian method này **không đại diện** cho “chi phí 1.000 phép nhân” và không dùng làm baseline để nói “Java có phép nhân miễn phí”.

Dùng trường hợp này để dạy **mối nguy tối ưu mất đối tượng cần đo**, rồi sửa bằng:

- Input runtime representative.
- Tác vụ có kết quả được return/consume.
- Không cho phép một constant được trá hình thành benchmark input nếu production vốn có nhiều values.
- Kiểm chứng optimized machine code khi cần.

## 32. Một protocol benchmark thực tế từng bước

**Bước 1 — Viết câu hỏi cụ thể:** đo cold-start, throughput hot path, allocation bytes/request, hay p99 latency end-to-end?

**Bước 2 — Xác định code under test:** method nào, với input sizes/distribution thực tế, có I/O/GC/synchronization hay không?

**Bước 3 — Thiết kế correctness test:** xác minh method trả đúng nhiều loại inputs, exception paths, boundary values.

**Bước 4 — Chặn DCE:** return/Blackhole phù hợp; nếu method có side effects thật thì đo đúng side-effect cost.

**Bước 5 — Chặn constant folding không đại diện:** dùng `@State` fields không final, realistic state updates/`@Param`, tránh pass literal cho method vốn nhận runtime input.

**Bước 6 — Thiết kế JMH harness:** forks, warm-up, iterations, time units, state scope và benchmark modes.

**Bước 7 — Đo lần thử, xem distribution:** nếu các iterations còn có rõ trends (thay đổi theo thời gian), điều tra trước khi công bố steady state.

**Bước 8 — Tách đo với chẩn đoán:** một run clean để benchmark, một run có `PrintCompilation`/JFR/profiler để giải thích.

**Bước 9 — Kiểm tra allocations, GC và CPU:** nếu throughput tăng do DCE hoặc heap allocation biến mất, đánh giá đó có đúng production semantics không.

**Bước 10 — So variants đối xứng:** cùng JDK, input, JVM flags, forks và warm-up; thay một yếu tố rồi đo lại.

**Bước 11 — Báo variance và nguồn nhiễu:** CPU frequency, GC, C2, scheduling, thermal, caching. Không chỉ cherry-pick sample nhanh nhất.

**Bước 12 — Xác nhận end-to-end:** microbenchmark có thể nhanh hơn nhưng request latency của service không thay đổi nếu bottleneck khác chi phối.

## 33. Những nhầm lẫn nên tránh

| Nhầm lẫn | Hiểu đúng |
| --- | --- |
| “Warm-up là ngủ 5 giây trước khi đo” | Warm-up là **chạy workload đại diện**, sleep không ép JIT compile method |
| “Cứ gọi 10.000 lần là chắc C2” | Tiers/thresholds/OSR/queues khác runtime/build |
| “Mọi lần gọi đầu đều interpreted” | Library/classes có thể đã compiled, OSR/tiering thay đổi |
| “JIT compiled nghĩa thời gian về sau không bao giờ đổi” | Deopt/recompile/GC/caches vẫn có thể làm khác |
| “Constant folding là bug compiler vì không chạy phép nhân” | Optimizer được phép thay constant nếu semantics đúng |
| “DCE là lỗi vì source có phép tính” | Pure computation không có output có thể được loại hợp lệ |
| “`javap` thấy loop thì CPU chắc đã lặp N lần” | `javap` là bytecode, không phải compiled machine code |
| “`Blackhole` cấm mọi JIT optimization” | Nó giúp giữ result observable, không khóa optimization pipeline |
| “`volatile` input mọi nơi là benchmark tốt” | Có thể đo volatile overhead thay vì algorithm |
| “`@State` luôn chặn folding” | Final/literal fields và constant context vẫn có thể specialize |
| “`@Param` đảm bảo compiler không biết giá trị” | Mỗi measurement case có thể được optimize theo input context |
| “Một lần nanoTime đủ cho ngàn ns/op” | Cần warm-up, samples, forks, statistical reporting |
| “Output đúng chứng minh code chưa bị tối ưu” | Optimizations được thiết kế giữ output đúng |
| “Discarded nhanh hơn consumed nên thuật toán tốt hơn” | Hai methods không cùng observable semantics |
| “Không thấy C2 log tức code không chạy” | Nó có thể chạy interpreter/C1 hoặc đã inline |
| “C2 compilation log chính là CPU profile” | Compiler activity khác CPU-time hotness |
| “0 B/op luôn nghĩa heap allocation không xảy ra trong production” | Context/inlining/input/measurement có thể khác |
| “JMH fork là một Java thread khác” | **Fork** là một JVM process mới |
| “JMH warm-up đảm bảo steady state tuyệt đối” | Cần kiểm tra trends/workload/caches/GC |
| “GC pause nên loại tùy ý để báo mean đẹp” | GC là một phần của workload; báo đủ điều kiện |

## 34. Câu hỏi tự kiểm tra

**Câu 1:** Vì sao 100 triệu lần `x = 6 * 7` có thể hoàn thành gần như tức thì?

Vì `6*7` là compile-time constant expression và `x` có thể không được dùng; compiler có thể constant-fold/DCE loop tùy semantics.

**Câu 2:** `javap` còn instruction `imul`, vậy C2 chắc chắn vẫn nhân tại runtime?

**Không.** `javap` chỉ xem bytecode; JIT còn có thể inline/fold.

**Câu 3:** Kết quả benchmark không được return/consume có vấn đề gì?

JIT có thể xem nó dead và loại work; thời gian đo không đại diện thao tác định đánh giá.

**Câu 4:** Blackhole có chắc giữ nguyên mỗi phép nhân trong function không?

**Không.** Nó giữ result observable, nhưng compiler vẫn có thể optimize phép tính hợp lệ.

**Câu 5:** Vì sao dùng `@State` field không final tốt hơn literal input?

JIT không dễ xem đó là compile-time constant từ source, giúp test gần runtime-input workload hơn, dù không chống mọi optimization.

**Câu 6:** Nếu warm-up iteration số 3 tăng đột ngột, chắc có bug?

Không. Có thể C2 compilation/deopt/GC hoặc OS noise; cần thêm evidence.

**Câu 7:** JMH `@Fork(2)` có nghĩa 2 threads chạy đồng thời không?

**Không.** Nghĩa là thực hiện benchmark trong hai JVM processes riêng theo harness.

**Câu 8:** Benchmark có `@Warmup(4)` chắc đã ổn định ở iteration thứ 5?

**Không.** Cần xem trends, workload và runtime; annotation không chứng minh steady state.

**Câu 9:** Vì sao `-Xint` không phải một benchmark baseline so tuyệt đối với default JIT?

Nó thay đổi cả execution engine của application và libraries; chi phí khác rộng hơn một method mục tiêu.

**Câu 10:** Lệnh nào giúp quan sát compilation events?

Trên HotSpot, `java -XX:+PrintCompilation ClassName`; format và events phụ thuộc build.

**Câu 11:** Loop tính toán trả về nhưng không dùng có thể DCE dù chạy từ interpreter trước đó không?

**Có thể** khi JIT compiled/inline; interpreter trước warm-up không đại diện steady-state machine code.

**Câu 12:** Vì sao demo `WarmupPhasesDemo` không phải microbenchmark đủ chuẩn?

Input constant, quá ít measurement samples, JIT/GC/OS noise chưa kiểm soát, không có JMH forks/representative state; nó chỉ dạy cách quan sát phases.

**Câu 13:** `volatile` field input có bảo đảm thuật toán chưa constant-fold theo mọi cách không?

Không; nó đảm bảo volatile read semantics, còn optimizer được phép làm mọi phép biến đổi bảo toàn kết quả và memory semantics.

**Câu 14:** Một phép tính có side effect thật nhưng output bị bỏ có chắc bị xóa hết không?

**Không.** Compiler phải bảo toàn I/O, volatile writes, exception và những side effects quan sát được.

**Câu 15:** Khi muốn đo cold startup, có nên chạy 30s warm-up rồi bắt đầu timing không?

Không nếu câu hỏi là cold startup. Mỗi metric cần protocol tương ứng; JMH steady-state và application startup latency là **hai vấn đề khác nhau**.

## 35. Tài liệu tham khảo và ví dụ chuẩn

- [OpenJDK JMH — JMHSample_10_ConstantFold](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_10_ConstantFold.java): literal/final inputs có thể bị constant-fold; mutable `@State` inputs là cách tổ chức benchmark phù hợp hơn.
- [OpenJDK JMH — JMHSample_08_DeadCode](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_08_DeadCode.java): kết quả bị bỏ và cách return result để tránh đo nhầm “không làm gì”.
- [OpenJDK JMH — JMHSample_09_Blackholes](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_09_Blackholes.java): cách dùng `Blackhole` và trả kết quả khi có một/nhiều outputs.
- [OpenJDK JMH — repository/README](https://github.com/openjdk/jmh): JMH setup, Maven archetype, modes, warm-up, forks và nguồn benchmark samples.
- [OpenJDK JMH — Blackhole source](https://github.com/openjdk/jmh/blob/master/jmh-core/src/main/java/org/openjdk/jmh/infra/Blackhole.java): cách harness tiêu thụ values mà hạn chế DCE/overhead.
- [Oracle Java SE 17 — java command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xint`, `-XX:TieredStopAtLevel`, diagnostic/compiler options tùy build.
- [Oracle Java SE 17 — javap](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): bytecode/class-file inspection; phân biệt với mã máy JIT.
- [Oracle HotSpot — tiered compilation overview](https://docs.oracle.com/en/java/javase/17/vm/java-hotspot-virtual-machine-performance-enhancements.html): interpreter, profiling, C1/C2 và hiệu quả của tiered compilation.
- [Java Language Specification 17 — `15.29` Constant Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.29): quy tắc compile-time constants ở Java language level, không nhầm với JIT constant folding.
- [Java SE 17 — System.nanoTime](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#nanoTime()): time source và việc dùng hiệu thời gian thay vì diễn giải absolute value.
- [Java Flight Recorder (JFR) API, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/module-summary.html): quan sát runtime/compiler/GC events và performance profiles.
- [OpenJDK JITWatch](https://github.com/AdoptOpenJDK/jitwatch): phân tích JIT compilation logs và inline decisions khi tooling/build hỗ trợ.

**Ghi nhớ:** Trong JVM, “nhanh” có thể vì **đã warm-up, chuyển C1/C2**, nhưng cũng có thể vì **JIT đã gấp hằng, đưa phép tính ra ngoài vòng lặp hoặc loại bỏ toàn bộ work không quan sát được**. Không đoán từ một lần `System.nanoTime` hoặc số dòng trong source. Hãy làm cho input và output của benchmark **đại diện cho production**, giữ result observable bằng **return/Blackhole**, chạy warm-up và nhiều JVM forks bằng **JMH**, theo dõi compilation/GC khi cần, rồi kết luận từ dữ liệu đo thật và diagnostics trên **đúng JDK 17 build**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-experiments-009">Tái hiện tràn stack và thiếu heap trong tiến trình thử nghiệm có giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Tái hiện tràn stack và thiếu heap trong tiến trình thử nghiệm có giới hạn

## 1. Mục tiêu: tái hiện đúng lỗi, nhưng không làm cạn tài nguyên của máy

Bài này hướng dẫn **thực nghiệm có kiểm soát** hai nhóm lỗi bộ nhớ thường gặp trong Java:

1. **`java.lang.StackOverflowError`** khi một Java thread cần thêm stack frame nhưng vượt giới hạn stack được cấu hình/cho phép.
2. **`java.lang.OutOfMemoryError: Java heap space`** khi ứng dụng cần thêm Java heap allocation mà garbage collector không thể cung cấp đủ bộ nhớ heap.

Hai lỗi có nguyên nhân, vùng bộ nhớ và hướng xử lý khác nhau. Tạo chúng **trong một JVM con riêng, có giới hạn bộ nhớ nhỏ, một workload chặn trên, không spawn vô hạn threads và không mở rộng heap toàn máy** giúp quan sát an toàn hơn.

**Nguyên tắc thực nghiệm:**

- Không chạy thử nghiệm này **trong JVM chạy dịch vụ, IDE chính, CI worker dùng chung hoặc container production**.
- Dùng **hai lần chạy Java độc lập**, để một thử nghiệm không làm bẩn runtime state của thử nghiệm còn lại.
- Dùng `-Xss512k` cho ca stack và `-Xms32m -Xmx48m` cho ca heap. **`-Xmx` chỉ giới hạn Java heap**, không giới hạn toàn bộ native memory, code cache, metaspace hoặc stacks.
- Chương trình sử dụng **điều kiện giới hạn 1.000.000 lần gọi đệ quy** và **tối đa 128 allocations của mảng 1 MiB**, thay vì cấp phát vô hạn không có điểm dừng.
- Chỉ chạy trong môi trường đủ bộ nhớ để khởi động JVM, có quyền kết thúc process và kiểm tra log; có thể bổ sung **OS/container-level limits** khi lab dành riêng cho thử nghiệm.
- Không kích hoạt heap dump mặc định trong ca OOME đơn giản; file `.hprof` có thể rất lớn và chứa dữ liệu nhạy cảm.
- Không coi `catch (OutOfMemoryError)` hoặc `catch (StackOverflowError)` là cơ chế recovery đáng tin cậy cho ứng dụng thực tế; ở đây chỉ dùng để đánh dấu thí nghiệm.

**Mục đích là hiểu cấu trúc JVM và đọc được tín hiệu chẩn đoán**, không phải cố làm JVM/crash máy bằng mọi giá.

## 2. Java VM stack và heap khác nhau ở đâu?

Theo **JVMS `2.5.2**, mỗi JVM thread có một **private JVM stack**, được dùng để lưu frames khi gọi methods. Frame chứa local variables, operand stack và thông tin hỗ trợ dynamic linking/return theo JVMS `2.6. **Độ sâu các lời gọi lồng nhau** có thể làm stack tăng dần.

Theo **JVMS `2.5.3`**, Java heap là vùng **chia sẻ giữa các threads** và là nguồn cấp phát cho **class instances và arrays**, được quản lý bởi hệ thống automatic storage management (GC).

~~~text
JVM process
+-----------------------------------------------------------------+
| Java heap (shared)                                              |
|   byte[] arrays, objects, data structures                       |
|   -Xms / -Xmx (HotSpot flags)                                    |
|                                                                 |
| Thread A                  Thread B                              |
| +-------------------+     +-------------------+                 |
| | JVM stack A       |     | JVM stack B       |                 |
| | main frame        |     | worker frame      |                 |
| | recursive frame   |     | helper frame      |                 |
| | recursive frame   |     | ...               |                 |
| | ...               |     +-------------------+                 |
| +-------------------+     thread-specific stack                 |
|                                                                 |
| Other memory: Metaspace, Code Cache, VM native allocations,     |
| direct buffers and supporting runtime memory                    |
+-----------------------------------------------------------------+
~~~

Sơ đồ mô tả **khái niệm**, không phải layout địa chỉ vật lý. Native stacks và code cache **không phải đối tượng nằm trong Java heap**. Các HotSpot options `-Xss`/`-Xmx` điều khiển hai loại tài nguyên khác nhau.

| Tiêu chí | Stack | Java heap |
| --- | --- | --- |
| Quyền sở hữu | Mỗi thread có JVM stack riêng | Nhiều threads chia sẻ |
| Tăng do | Gọi method, tạo frames, recursion | Cấp phát class instances/arrays còn cần memory |
| Giới hạn lab | `-Xss512k` | `-Xmx48m` |
| Lỗi mô phỏng | `StackOverflowError` | `OutOfMemoryError: Java heap space` |
| GC có thu frame stack đang chạy? | Không như object heap; frame kết thúc khi method return/unwind | GC có thể reclaim unreachable objects |
| Một `new byte[]` thường được cấp phát ở đâu? | Mảng là Java object trên heap (trừ tối ưu VM không quan sát được) | Heap theo JVM semantics |
| Sửa lỗi thường gặp | Giảm độ sâu recursion, sửa base case, dùng iteration | Giảm retention/allocations hoặc sizing phù hợp |

**Không nhầm** `StackOverflowError` với `OutOfMemoryError`. Một lỗi stack-depth có thể xuất hiện dù `-Xmx` rất lớn; một lỗi Java heap space có thể xuất hiện dù call stack nông.

## 3. Hai lỗi được JVM Specification định nghĩa thế nào?

### `StackOverflowError`

JVMS `2.5.2` nêu: nếu phép tính của một thread cần stack lớn hơn mức được cho phép, JVM ném **`StackOverflowError`**.

Ví dụ nguyên nhân:

- Đệ quy không có base case hoặc base case không bao giờ đạt.
- Recursion hợp lệ nhưng call depth quá lớn.
- Chuỗi gọi methods lồng nhau rất sâu, kể cả gián tiếp qua framework/serialization/graph traversal.
- Thread stack được cấu hình quá nhỏ so với nhu cầu thật.

**Đừng kết luận** cứ có `StackOverflowError` là recursion vô hạn. Một thuật toán duyệt cây sâu và có base case vẫn có thể vượt stack.

### `OutOfMemoryError`

JVMS `2.5.3` nêu: nếu computation cần thêm heap mà automatic storage management không thể cấp được, JVM ném **`OutOfMemoryError`**. Oracle troubleshooting guide phân biệt nhiều nhóm nguyên nhân dựa trên **detail message**:

- `Java heap space`: heap allocation không thể đáp ứng.
- `GC overhead limit exceeded`: một số collector/policy quyết định GC tốn quá nhiều công mà reclaim quá ít.
- `Metaspace`: thiếu capacity cho class metadata.
- `Direct buffer memory`: direct buffer allocation limit/pressure.
- `unable to create native thread`: không thể tạo OS/native thread theo resource limits.
- Những dạng native/OOM khác tùy JVM/OS.

**Bài này chỉ cố tái hiện `Java heap space`**, không phải ép Metaspace hoặc tạo hàng trăm nghìn threads. Tạo quá nhiều threads có thể gây resource exhaustion cấp hệ thống, không thích hợp cho lab này.

## 4. Khung an toàn trước khi chạy

1. Dùng JDK **17+**, kiểm tra `java -version` và `javac -version`.
2. Tạo thư mục lab riêng, không dùng source/project production.
3. Đảm bảo **không** truyền `-Xmx` hàng gigabytes hoặc tham số không kiểm soát vào demo.
4. Chạy mỗi mode **một lần** trước, không chạy song song nhiều bản sao.
5. Tránh dùng máy đang có ứng dụng quan trọng thiếu memory.
6. Đảm bảo log files không ghi vào thư mục nhạy cảm hoặc phân vùng sắp đầy.
7. Nếu dùng container, đặt tổng memory/container limit hợp lý **cao hơn `-Xmx`** để JVM còn native overhead; không đặt cgroup limit bằng đúng Java heap size.
8. Trong hệ thống automation, đặt thêm **timeout bên ngoài JVM** để tránh test treo hoặc chậm bất thường.
9. Không thu thập heap dump có dữ liệu thật nếu không có chính sách bảo vệ thông tin.
10. Khi kết thúc lab, kiểm tra process đã thoát và dọn các file chỉ thuộc thư mục lab.

**Số 48 MiB trong `-Xmx48m` là giới hạn tối đa heap thử nghiệm, không là giới hạn RSS toàn process.** JDK/JVM khởi động cũng cần native memory và các data structures khác.

### Kiểm tra phiên bản

~~~bash
java -version
javac -version
java -Xlog:help
~~~

Trong JDK 17/21/25 HotSpot, `-Xlog:gc` và `-Xlog:gc*` thuộc Unified Logging. Nếu dùng JVM vendor khác, xác nhận options tương ứng thay vì mặc định mọi `-XX:` được hỗ trợ.

## 5. Tạo một chương trình Java 17+ có hai chế độ độc lập

Lưu file **`ResourceLimitsLab.java`**:

~~~java
public final class ResourceLimitsLab {
    private static volatile int calls;
    private static volatile byte[][] retained;

    private ResourceLimitsLab() { }

    // Không dùng recursion vô hạn: luôn có giới hạn logic.
    private static int recurse(int n) {
        calls++;
        if (n >= 1_000_000) {
            return 0;
        }

        // Có công việc sau recursive call; tránh ngộ nhận đây
        // là ví dụ tail-recursion được JVM bảo đảm tối ưu.
        return 1 + recurse(n + 1);
    }

    private static void stackMode() {
        System.out.println("mode=stack");
        try {
            recurse(0);
            System.out.println("result=LIMIT_NOT_REACHED");
        } catch (StackOverflowError expected) {
            // Chỉ bắt để ghi nhận kết quả trong tiến trình lab.
            // Không printStackTrace(): stack trace rất dài.
            System.out.println("result=StackOverflowError");
        }
    }

    private static void heapMode() {
        System.out.println("mode=heap");

        // 128 tham chiếu, mỗi slot sẽ giữ một byte[] 1 MiB.
        // Tổng dữ liệu mong muốn vượt xa -Xmx48m.
        byte[][] slots = new byte[128][];
        retained = slots; // giữ graph reachable

        try {
            for (int i = 0; i < slots.length; i++) {
                byte[] block = new byte[1_048_576];
                block[0] = (byte) i;
                slots[i] = block;
            }
            System.out.println("result=LIMIT_NOT_REACHED");
        } catch (OutOfMemoryError expected) {
            // Chỉ để quan sát thí nghiệm. Không dựa vào catch
            // làm cơ chế recovery của hệ thống production.
            retained = null;
            System.out.println("result=OutOfMemoryError");
        } finally {
            retained = null;
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("usage: stack|heap");
            return;
        }

        switch (args[0]) {
            case "stack" -> stackMode();
            case "heap" -> heapMode();
            default -> System.out.println("usage: stack|heap");
        }
    }
}
~~~

**Điểm thiết kế giúp lab có giới hạn:**

- `recurse` tăng `calls` qua `volatile` để đường recursion có một side effect có thể quan sát. Có **base case ở 1.000.000**, không phải gọi vô hạn bất tận.
- Sau recursive call còn biểu thức `1 + ...` nên không được trình bày như một phép tail-call JVM bảo đảm xóa frames. Java/JVMS không yêu cầu general tail-call optimization.
- Heap mode giữ references trong `byte[][]` và công bố qua `retained`, nhằm khiến các blocks **còn reachable**, không dễ bị thu hồi trước khi đạt giới hạn heap.
- Có tối đa **128 × 1.048.576 bytes = 134.217.728 bytes** payload dự kiến, vượt đáng kể maximum heap 48 MiB, chưa tính object headers/array references/VM structures.
- Không spawn bất kỳ child thread nào và không tạo vô hạn native resources.
- Mọi output là **string literals có sẵn**, giảm overhead trong các catch blocks. Dù vậy khi JVM thực sự kiệt tài nguyên, không thể bảo đảm 100% code trong `catch` luôn tiếp tục chạy.
- Sau khi catch, reference static được xóa; ví dụ không dựa vào recovered heap để tiếp tục business logic.

### Biên dịch (không gây tràn bộ nhớ)

~~~bash
javac -g ResourceLimitsLab.java
~~~

`javac` sẽ tạo `ResourceLimitsLab.class`. Không cần Maven, thư viện ngoài hoặc thay đổi các tham số mặc định của chính tiến trình IDE.

**Nếu chạy không có argument:**

~~~bash
java ResourceLimitsLab
~~~

Stdout:

~~~text
usage: stack|heap
~~~

Lệnh này chỉ kiểm tra đường main/argument, không gây stress.

## 6. Thực nghiệm A — tái hiện StackOverflowError

Chạy **một JVM process riêng**:

~~~bash
java -Xss512k ResourceLimitsLab stack
~~~

### Kết quả kỳ vọng

Trên một HotSpot JDK 17+ phổ biến chấp nhận stack 512 KiB và có execution thông thường, JVM thường dừng recursion trước base case 1.000.000, bắt được lỗi và in:

~~~text
mode=stack
result=StackOverflowError
~~~

**Đây là output dự kiến trong điều kiện nêu trên**, không phải lời hứa mọi JVM/version/CPU compiler configuration ném tại cùng depth hoặc cùng chương trình sẽ luôn có khả năng catch thành công nếu process đã hỏng. Có thể thấy `result=LIMIT_NOT_REACHED` nếu một runtime tối ưu hoặc cấu hình có khả năng xử lý recursion khác dự kiến; khi đó **không được nói StackOverflowError đã được tái hiện**.

### Cơ chế từng bước

1. `main` gọi `stackMode()`.
2. `stackMode` gọi `recurse(0)`.
3. Mỗi lần `recurse(n)` gọi `recurse(n+1)`, JVM cần quản lý một activation/frame tương ứng cho execution.
4. Vì chưa đạt base case, các frames sống lồng nhau tăng.
5. Khi vượt stack capacity cho phép, HotSpot/JVMS tạo `StackOverflowError`.
6. Các frames đang mở được unwind để quay lại catch ở `stackMode`; nếu còn đủ runtime resources, in marker.

~~~text
main()
  stackMode()
    recurse(0)
      recurse(1)
        recurse(2)
          recurse(3)
            ...
              stack limit reached
              -> StackOverflowError
                unwind back to stackMode catch
~~~

**Độ sâu tuyệt đối không cố định** vì frame size thay đổi theo bytecode, JIT inlining/tier, optimization, OS, CPU, word size, stack guard pages và JVM implementation.

### Điều chỉnh `-Xss` cẩn thận

| Cờ | Ý nghĩa |
| --- | --- |
| `-Xss512k` | Đặt stack size của mỗi Java thread cho HotSpot phù hợp |
| `-Xss1m` | Thường tăng headroom recursion so với 512k |
| `-Xss256k` | Có thể cho recursion overflow sớm hơn, nhưng phải được JVM/OS chấp nhận |
| `-Xmx48m` | Không phải cờ dùng để giảm JVM stack |
| `-XX:ThreadStackSize` | Tùy chọn HotSpot liên quan thread stack; cần xem đúng docs/version |

Nếu `-Xss256k` quá nhỏ, JVM có thể từ chối startup với thông báo yêu cầu stack size tối thiểu. **Không ép kích thước ngày càng thấp** để đạt một lỗi có vẻ “đẹp”. `-Xss512k` là khởi điểm thực hành, có thể nâng theo yêu cầu platform.

### Đừng đo độ sâu recursion rồi suy frame size chính xác

Ví dụ, 512 KiB chia cho số recursive calls **không bằng** chính xác frame size: stack dành chỗ cho main/runtime frames, native transitions, guard pages và compiler optimizations. Muốn nghiên cứu sâu cần stack traces, JIT diagnostics và platform-specific tooling.

## 7. Thực nghiệm B — tái hiện OutOfMemoryError: Java heap space

**Không** chạy heap mode trong cùng JVM đã test stack. Mở một **process Java mới** với giới hạn heap:

~~~bash
java -Xms32m -Xmx48m ResourceLimitsLab heap
~~~

### Kết quả kỳ vọng

Trong môi trường HotSpot bình thường và process có đủ native resources để khởi động, việc cố giữ 128 mảng 1 MiB **không thể vừa trong heap tối đa 48 MiB**. Do đó một lần cấp phát trên heap sẽ thất bại sau nỗ lực GC thích hợp; chương trình thường bắt và in:

~~~text
mode=heap
result=OutOfMemoryError
~~~

**Khẳng định độ đúng có điều kiện:** JVM có thể ném `OutOfMemoryError` với detail message như `Java heap space`; marker của ví dụ chỉ in **class name**, không tự chứng minh subtype chi tiết. Để xác nhận đúng heap cause, xem GC/VM diagnostics hoặc chạy trong lab có kiểm soát với exception detail. Với mã trên và `-Xmx48m`, nguyên nhân có chủ đích là retained Java heap arrays, **không** phải native thread exhaustion.

### Vì sao GC không “cứu” được?

~~~text
Heap limit: -Xmx48m

retained -> slots[0] -> byte[1 MiB]
         -> slots[1] -> byte[1 MiB]
         -> ...
         -> slots[n] -> byte[1 MiB]

Các arrays có strong references và vẫn live
       |
       v
GC không được thu hồi các arrays đang reachable
       |
       v
Allocation cần thêm heap > khả năng cấp phát
       |
       v
OutOfMemoryError: Java heap space (dự kiến)
~~~

Garbage collector có thể cố thu dọn temporary/unreachable objects, nhưng **không được thu hồi arrays mà chương trình vẫn giữ references hợp lệ**. Đây là **intentional retention để mô phỏng pressure**, không phải bằng chứng một ứng dụng thực đang bị memory leak.

### Vì sao chọn 1 MiB arrays và 48 MiB heap?

Các allocations đủ lớn để OOME xuất hiện sau một số hữu hạn lần lặp. Giới hạn tổng allocation requested trong vòng lặp là ~128 MiB payload, lớn hơn heap 48 MiB; không cần đẩy heap lên hàng gigabytes.

Cách G1/Serial/Parallel/ZGC phân vùng heap và xử lý large objects khác nhau. Ví dụ **không dự đoán chính xác lần lặp thứ bao nhiêu** gặp OOME. Với G1, một mảng 1 MiB có thể được xếp vào đường humongous allocation tùy region size; điều này ảnh hưởng thời điểm thu gom, không làm thay đổi nguyên tắc retained data > maximum heap.

**`-Xms32m` là initial heap size**, còn `-Xmx48m` là maximum. Ghi `-Xms32m -Xmx48m` không có nghĩa process được giới hạn tổng 48 MiB RSS.

## 8. So sánh hai experiments bằng bảng

| Thuộc tính | Stack mode | Heap mode |
| --- | --- | --- |
| Command | `java -Xss512k ResourceLimitsLab stack` | `java -Xms32m -Xmx48m ResourceLimitsLab heap` |
| Nguồn tăng tài nguyên | Recursive call frames | Live `byte[]` allocations |
| Giới hạn chính | Per-thread JVM stack | Shared Java heap |
| Lỗi chủ đích | `StackOverflowError` | `OutOfMemoryError` |
| Có thể giảm bằng GC? | GC không giải quyết recursion depth | GC chỉ reclaim unreachable objects |
| Marker đầu ra | `result=StackOverflowError` | `result=OutOfMemoryError` |
| Giá trị đo không cố định | Recursion depth | Số mảng cấp phát trước OOME |
| Chiến lược sửa code | Base case/iterative algorithm | Bỏ retention, giới hạn cache, giảm allocations |
| Có nên catch rồi chạy tiếp production? | Không nên mặc định | Không nên mặc định |

**Hai modes là hai process khác nhau**, nên kết quả không bị phụ thuộc vào việc JVM trước có thể rơi vào trạng thái resource exhaustion.

## 9. Quan sát GC logs ở heap mode

Để xem JVM đã thực hiện những lần GC nào trước OOME:

~~~bash
java -Xms32m -Xmx48m -Xlog:gc=info:file=heap-gc.log ResourceLimitsLab heap
~~~

Ứng dụng vẫn thường in hai dòng marker. Phần `heap-gc.log` chứa GC events nếu collector phát sinh.

Ví dụ **mẫu log minh họa, không phải stdout thật cố định**:

~~~text
[info][gc] GC(0) Pause Young ... 20M->18M(48M) ...
[info][gc] GC(1) Pause Young ... 30M->29M(48M) ...
[info][gc] GC(2) Pause Full ... 45M->44M(48M) ...
~~~

Không được học thuộc số GC, pause cause, timestamps, heap sizes hoặc thứ tự cụ thể. Các dòng trên chỉ minh họa cách đọc: heap sử dụng trước/sau collection và heap capacity. Real logs phụ thuộc **JDK version, collector, object layout, GC ergonomics và runtime**.

### Chi tiết hơn

~~~bash
java -Xms32m -Xmx48m -Xlog:gc*=info:file=heap-gc-detail.log ResourceLimitsLab heap
~~~

`gc*` bật nhiều tags GC ở mức info; log có thể dài hơn. Khi chỉ học giới hạn, bắt đầu bằng `gc=info`, không bật `trace` ồ ạt.

**Nếu chọn collector rõ ràng**, có thể dùng `-XX:+UseSerialGC` cho thí nghiệm nhỏ trong HotSpot nhằm giảm một số biến số ergonomics; nhưng đó là **một experiment configuration**, không phải chứng minh rằng Java default collector là Serial GC.

~~~bash
java -Xms32m -Xmx48m -XX:+UseSerialGC -Xlog:gc=info:file=heap-serial-gc.log ResourceLimitsLab heap
~~~

Collector có thể thay đổi số/cách pauses, nhưng retained array graph vẫn vượt max heap. Không ép một log phrase duy nhất cho mọi collector.

## 10. Quan sát stack case và stack trace

Với ca stack, marker chỉ cho biết `StackOverflowError` đã được ném/bắt. Muốn nhìn nguồn lỗi thật trong một ứng dụng, thông thường stack trace sẽ lặp lại frames:

~~~text
java.lang.StackOverflowError
    at ResourceLimitsLab.recurse(ResourceLimitsLab.java:...)
    at ResourceLimitsLab.recurse(ResourceLimitsLab.java:...)
    at ResourceLimitsLab.recurse(ResourceLimitsLab.java:...)
    ...
~~~

Đây là **dạng minh họa**, line numbers/frames phụ thuộc mã đã compile và VM; không phải output của chương trình chính (vì chương trình bắt lỗi và chỉ in marker).

### Vì sao demo không `printStackTrace()`?

Một recursion sâu có thể tạo stack trace rất dài. Trong lab, in hàng nghìn frames không tăng hiểu biết về cơ chế và có thể gây I/O/log-file pressure. Nếu cần chẩn đoán trên ứng dụng thật, ghi stack trace theo giới hạn/logging policy và xác định vòng gọi lặp, recursive base case hoặc cycle trong dữ liệu.

### Xác định nguyên nhân bằng call graph

~~~text
A() -> B() -> C() -> A() -> B() -> ...
                    ^
                    | cycle lặp lại

Hoặc:
walk(node)
  -> walk(node.child)
  -> walk(node.child.child)
  -> ... cây rất sâu
~~~

**Cycle recursion** và **deep-but-finite recursion** đều có thể tràn stack. Cách sửa tương ứng là chặn cycle/memoization/visited set, giảm call depth hoặc chuyển sang explicit stack/queue trên heap (kèm giới hạn dữ liệu hợp lý).

## 11. Không phải mọi OOME đều do heap Java đầy

Oracle troubleshooting nêu rõ phải xem **detail message**, không chỉ exception class.

| Thông điệp/triệu chứng | Vùng/nguồn thường liên quan | Hướng điều tra |
| --- | --- | --- |
| `Java heap space` | Java object heap | Heap dump/histogram, GC logs, retained objects, max heap |
| `GC overhead limit exceeded` | GC lặp tốn nhiều thời gian, reclaim rất ít theo collector/policy | GC logs, heap pressure, sizing/retention |
| `Metaspace` | Class metadata/native memory | Class loaders/classes retention, `MaxMetaspaceSize` |
| `Direct buffer memory` | NIO direct buffer allocation/accounting | Buffer lifecycle, native memory, `MaxDirectMemorySize` |
| `unable to create native thread` | OS threads/native stacks/process limits | Thread count, `ulimit`/cgroup, `-Xss`, leak threads |
| JVM process bị OS kill, exit 137 trên Linux container | Cgroup/OS process memory limit | Container metrics/OOMKill logs, tổng RSS |
| `StackOverflowError` | Stack depth/stack frames | Recursive call graph, `-Xss`, guard pages |

**`-Xmx` không giới hạn mọi nguồn native memory.** Nếu JVM trong container bị OOMKilled trước khi Java ném `OutOfMemoryError`, đó **không phải** bằng chứng rằng code đã gây `Java heap space` exception. Phải xem OS/container events.

**Không dùng `-Xmx` để chữa Metaspace leak** nếu căn nguyên là classloader retention; tăng heap có thể chỉ dời áp lực bộ nhớ sang nơi khác.

## 12. Tùy chọn heap dump: hữu ích nhưng không nên bật vô điều kiện

HotSpot hỗ trợ:

~~~bash
java -Xms32m -Xmx48m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=heap-lab.hprof ResourceLimitsLab heap
~~~

Khi **OOME do Java heap exhaustion** phù hợp, JVM có thể tạo file `heap-lab.hprof` để phân tích object graph. File HPROF chứa dữ liệu của heap **tại thời điểm lỗi** và có thể lớn gần cỡ heap, nên:

- Chỉ chạy ở **lab riêng** có đủ disk, không sử dụng dữ liệu sản xuất.
- Giữ quyền truy cập file phù hợp; heap dump có thể chứa **token, mật khẩu hoặc dữ liệu cá nhân** nếu dump ứng dụng thật.
- Không tự động commit hoặc chia sẻ dump lên hệ thống công khai.
- Xác minh file có được tạo thật thay vì mặc định `HeapDumpOnOutOfMemoryError` luôn hoạt động.
- Không coi heap dump của một lỗi `StackOverflowError` là điều đương nhiên.
- Nếu đã tồn tại file cùng tên, HotSpot có thể không ghi đè được; không dựa vào nội dung file cũ.
- Dọn file sau khi phân tích và tuân thủ retention policy.

**Theo Oracle JDK 25 `java` manual**, `HeapDumpOnOutOfMemoryError` áp dụng cho OOME liên quan **Java heap exhaustion**, không cho một `OutOfMemoryError` mà code Java tự `throw new OutOfMemoryError()` hoặc mọi kiểu native resource exhaustion.

### Heap dump giúp thấy điều gì?

Trong một công cụ phân tích như Eclipse Memory Analyzer (MAT) hoặc các công cụ heap-profiler phù hợp, có thể xem:

- Class histogram — nhiều `byte[]` sẽ hiện diện trong demo.
- Dominator tree và retained sizes.
- Reference path từ GC roots tới các mảng.
- Tại sao các objects vẫn sống do `retained`/`slots`.

**Một heap dump không tự chứng minh “memory leak”**. Trong demo, retention là **chủ đích** và số bytes không thể vượt limit, chứ không phải mã sản xuất vô tình giữ objects lâu.

## 13. Phân biệt allocation rate với live-set và leak

Một chương trình có thể cấp phát **tổng 10 GiB temporary arrays theo thời gian** trên heap `-Xmx256m` mà không OOME nếu những mảng cũ trở nên unreachable và GC reclaim đủ nhanh.

Trong demo heap này, các mảng được giữ:

~~~text
retained (static volatile reference)
     |
     v
byte[][] slots
  |      |      |      ...
  v      v      v
byte[] byte[] byte[] ...
~~~

Đó là **retained live-set tăng đến gần max heap**. GC không thu hồi được vì còn strong references.

Nếu muốn sửa một chương trình thật:

- Xác định object ownership và thời hạn sống hợp lệ.
- Đặt eviction policy/size limits cho caches.
- Tránh giữ toàn bộ dữ liệu khi có thể dùng streaming/chunking.
- Bỏ listeners/thread-locals/queues không còn dùng.
- Đo live-set sau GCs, allocation pressure và retained paths.
- Sau khi tối ưu memory lifecycle, sizing heap theo workload/latency objective.

**Không chữa memory leak chỉ bằng cách tăng `-Xmx`.** Điều đó có thể trì hoãn OOME nhưng không sửa nguyên nhân retention tăng không kiểm soát.

## 14. Cơ chế khắc phục StackOverflowError trong code thật

### 14.1. Sửa điều kiện dừng và recursion cycle

Ví dụ:

~~~java
// Ví dụ minh họa, không phải file độc lập.
static int factorial(int n) {
    if (n < 0) {
        throw new IllegalArgumentException("n");
    }
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}
~~~

`factorial` này có base case nhưng vẫn có rủi ro **`int` overflow** nếu `n` quá lớn, và deep recursion không hợp lý nếu input size không giới hạn. Cần giải quyết **cả tính đúng đắn bài toán lẫn giới hạn tài nguyên**, không chỉ thêm base case mà quên input validation.

### 14.2. Chuyển recursion thành iteration khi phù hợp

~~~java
// Phương án iterative, minh họa method độc lập.
static long sumTo(int n) {
    if (n < 0) {
        throw new IllegalArgumentException("n");
    }
    long total = 0;
    for (int i = 1; i <= n; i++) {
        total += i;
    }
    return total;
}
~~~

Loop dùng một mức call depth và có thể giảm stack pressure đáng kể. **Không suy** mọi recursion đều chuyển thành O(1) memory: duyệt graph/tree thường vẫn cần explicit data structure để giữ trạng thái — nó chuyển một phần memory từ call stack sang heap, vẫn phải có giới hạn.

### 14.3. `-Xss` là biện pháp điều chỉnh, không phải sửa thuật toán sai

Nâng `-Xss` có thể cho phép call depth sâu hơn, nhưng nhiều threads × stack size tăng **native memory reservation/commit pressure**; còn có thể tăng chi phí process resources. Với recursion vô hạn, tăng stack chỉ làm lỗi xuất hiện muộn hơn.

**Không tạo hàng nghìn threads mỗi thread `-Xss8m`** để “thử stack”. Điều đó chuyển lab sang thiếu native memory, khó kiểm soát và dễ làm host cạn tài nguyên.

## 15. Có nên catch StackOverflowError và OutOfMemoryError?

Cả hai là subclasses của `VirtualMachineError` trong cây lỗi Java qua `Error`; nói chung chúng biểu thị vấn đề resource/capacity nghiêm trọng mà **không nên** xử lý như một exception nghiệp vụ thông thường.

Với **lab có kiểm soát**:

~~~java
try {
    // Chỉ code thử nghiệm với giới hạn rõ ràng
} catch (StackOverflowError expected) {
    System.out.println("result=StackOverflowError");
}
~~~

và:

~~~java
try {
    // Chỉ code tạo heap pressure có cap
} catch (OutOfMemoryError expected) {
    System.out.println("result=OutOfMemoryError");
}
~~~

được dùng để có **marker quan sát**. Đây là **code fragment minh họa**, chưa phải chương trình riêng.

**Production:** không tiếp tục nhận requests ngay sau một OOME chỉ nhờ catch. JVM/runtime có thể đang thiếu resources để log, allocate, flush hoặc trả response. Thường cần alert, diagnostics và restart/redeploy strategy phù hợp. Tương tự, bắt SOE ở tầng request mà không sửa recursive cycle sẽ không giải quyết nguyên nhân và có thể tái phát.

**Không dùng `catch (Throwable)` tràn lan** để nuốt các lỗi VM và báo success.

## 16. Tại sao phải giới hạn cả “số bước” lẫn “tài nguyên JVM”?

Giới hạn JVM:

- `-Xss512k` giảm per-thread stack headroom cho một lab.
- `-Xmx48m` giới hạn maximum Java heap.

Giới hạn workload:

- Recursion có base case ở `1_000_000`, nên không chạy vô hạn nếu không overflow.
- Vòng cấp phát có đúng tối đa `128` iterations.
- Không tạo thêm threads hoặc processes.

Giới hạn hệ thống ngoài JVM (khi cần):

- OS/container **memory + PID/resource limits**.
- Per-process timeout từ test harness.
- Disk quota/giới hạn log và heap-dump storage.
- Tách process trong môi trường disposable, không dùng production shared heap.

**Lý do:** một flag chỉ hạn chế một vùng memory. Ví dụ `-Xmx48m` không chặn code mở vô hạn threads/native memory allocations; còn `-Xss512k` không chặn code giữ gigabytes arrays nếu `-Xmx` lớn.

### Timeouts trong test automation

Với Linux có GNU coreutils:

~~~bash
timeout 30s java -Xss512k ResourceLimitsLab stack
timeout 30s java -Xms32m -Xmx48m ResourceLimitsLab heap
~~~

`timeout` là công cụ **của hệ điều hành**, **không phải Java option**. Nó giúp kết thúc process nếu runtime unexpectedly chậm/treo. Exit code 124 thường biểu thị timeout theo GNU coreutils; code này không được coi là kết quả SOE/OOME thành công.

Trên Windows, dùng test runner/IDE/JVM-launch wrapper có **process timeout và kill tree** tương đương; không kỳ vọng lệnh GNU `timeout` có sẵn trong PowerShell mặc định.

## 17. Lệnh chạy trên Windows PowerShell

Từ thư mục chứa `ResourceLimitsLab.java`:

~~~powershell
javac -g ResourceLimitsLab.java
java -Xss512k ResourceLimitsLab stack
java -Xms32m -Xmx48m ResourceLimitsLab heap
~~~

Nếu muốn log GC vào file:

~~~powershell
java -Xms32m -Xmx48m -Xlog:gc=info:file=heap-gc.log ResourceLimitsLab heap
Get-Content .\heap-gc.log
~~~

Nếu dùng PowerShell transcript, redirection hoặc debugger, hãy lưu ý log formatting và stdout/stderr có thể khác. Trong PowerShell 5.1, cú pháp kết hợp command chaining khác Bash; các lệnh trên chạy từng dòng.

**Không ép Java GUI IDE launch configuration** dùng các flags OOME thử nghiệm trên các dự án khác. Đây là flags chỉ cho tiến trình lab.

## 18. Tự kiểm tra cấu hình HotSpot

Một số lệnh chẩn đoán read-only:

~~~bash
java -version
java -XshowSettings:vm -version
java -XX:+PrintFlagsFinal -version
java -Xlog:help
~~~

Bạn có thể tra các flags như `MaxHeapSize`, `InitialHeapSize`, `ThreadStackSize` và collector, tùy version/cách hiện output.

**Không dùng `PrintFlagsFinal` một mình để kết luận stack frame đang chiếm chính xác bao nhiêu bytes**, và đừng xem output heap capacity/GC logs là total process RSS. Một số VM flags được ergonomically selected theo cgroup/OS memory, nên áp dụng explicit `-Xmx` trong lab giúp giới hạn phần Java heap dễ tái lập.

### JFR/JMC và profiler

Trong ứng dụng thật, JDK Flight Recorder/JDK Mission Control có thể theo dõi allocation hot spots, GC pauses và các sự kiện liên quan. Dùng tools theo permissions và operational policy của tổ chức.

**Không phải mọi SOE đều có heap dump**; stack trace, call graph và code inspection mới là dữ liệu trực tiếp cho recursion-depth bug.

## 19. Bảng đối chứng: thay cờ có làm thay lỗi không?

| Thay đổi thử nghiệm | Kỳ vọng hợp lý | Không được kết luận |
| --- | --- | --- |
| Stack mode `-Xss512k` → `-Xss1m` | Thường tăng recursion headroom | Độ sâu chính xác gấp đôi |
| Heap mode `-Xmx48m` → `-Xmx96m` | Giữ được nhiều arrays hơn trước lỗi | Tổng RSS giới hạn ở 96 MiB |
| Heap mode bỏ reference retention | GC có thêm cơ hội reclaim arrays | GC chắc chạy ngay tại dòng bỏ ref |
| Heap mode thêm `-Xlog:gc` | Quan sát GC events | Số pauses cố định giữa các runs |
| Dùng Serial GC thay G1 | Khác GC policy/logging/regions | Cả hai luôn ném ở cùng iteration |
| Bật `HeapDumpOnOutOfMemoryError` | Có thể tạo `.hprof` khi Java heap exhaustion | Mọi OOME đều sinh heap dump |
| Bật `-Xshare:off` | Tắt CDS, có thể ảnh hưởng startup | Là cờ sửa SOE/OOME |
| Tăng `-Xss` rất lớn khi có nhiều threads | Có thể tăng native memory pressure | Là giải pháp miễn phí cho recursion vô hạn |

**Nếu chương trình hết lỗi sau khi tăng heap**, chưa thể khẳng định “đã sửa memory leak”. Cần quan sát live-set/retention theo tải thời gian dài.

## 20. Những ngộ nhận nguy hiểm

| Ngộ nhận | Sự thật |
| --- | --- |
| “Stack và heap là cùng một vùng memory.” | JVM stack per thread, heap shared, lifecycle khác. |
| “`-Xmx` điều khiển thread stack.” | `-Xmx` điều khiển maximum Java heap. |
| “`-Xss` điều khiển lượng heap Java.” | `-Xss` điều khiển stack size cho thread trên HotSpot phù hợp. |
| “SOE luôn đồng nghĩa infinite recursion.” | Deep finite recursion cũng có thể overflow. |
| “Stack size 512 KiB nghĩa đúng 512 KiB chỉ dành cho recursive frames.” | Còn base frames, guards và runtime details. |
| “Mỗi recursive call dùng cùng một số bytes trên mọi CPU/JDK.” | Frame layout/JIT/platform thay đổi. |
| “Tăng heap sẽ sửa SOE.” | Không giải quyết recursion/call-depth. |
| “Tăng stack sẽ sửa Java heap space OOME.” | Không giải quyết heap retention. |
| “GC có thể thu mảng vẫn được static field giữ.” | Strongly reachable objects phải được bảo vệ. |
| “OOME luôn là memory leak.” | Có thể do heap quá nhỏ hoặc legitimate live-set lớn. |
| “OOME chỉ có Java heap space.” | Metaspace/direct/native threads và dạng khác tồn tại. |
| “`-Xmx48m` chắc chắn process không bao giờ dùng quá 48 MiB.” | JVM còn non-heap/native memory. |
| “Catch OOME rồi tiếp tục server là an toàn.” | Không có guarantee về resources/runtime state. |
| “Đoàn recursion không có base case là cách test tốt nhất.” | Bounded experiment an toàn và dễ diễn giải hơn. |
| “In toàn stack trace vô hạn không có chi phí.” | Log/I/O có thể phình to, obscures root cause. |
| “`-Xlog:gc` làm JVM hết OOME.” | Logging chỉ quan sát; không sửa allocation/retention. |
| “Heap dump luôn chứa dữ liệu vô hại.” | Có thể chứa secrets và dữ liệu cá nhân. |
| “Một exit code khác 0 đồng nghĩa Java heap OOME.” | Có thể timeout, OS kill, native OOM hoặc lỗi startup. |
| “GC log của G1 và Serial có số events giống nhau.” | Collector-specific scheduling. |
| “Một lần chạy không tái hiện lỗi nghĩa ứng dụng thread-safe/memory-safe.” | Không; workload và môi trường có thể chưa kích hoạt vấn đề. |
| “Có thể dùng `new OutOfMemoryError()` để thử heap exhaustion.” | Đó chỉ là tạo/throw object Error, không ép JVM cạn heap thật. |
| “Heap dump từ SOE có sẵn nếu dùng HeapDumpOnOutOfMemoryError.” | SOE không thuộc OOME heap exhaustion. |

## 21. Câu hỏi ôn tập kèm đáp án

**1.** SOE liên quan vùng bộ nhớ nào?  
**Đáp án:** JVM stack của một thread; khi execution cần stack nhiều hơn mức cho phép.

**2.** Java heap space OOME liên quan vùng nào?  
**Đáp án:** Java heap, khi allocation không được GC/heap cung cấp thêm đủ memory.

**3.** Vì sao cần chạy stack và heap modes trong các JVM riêng?  
**Đáp án:** Tránh ảnh hưởng của trạng thái lỗi trước tới phép đo sau và giới hạn tài nguyên độc lập.

**4.** `-Xss512k` và `-Xmx48m` khác nhau thế nào?  
**Đáp án:** Cờ đầu đặt thread stack size; cờ sau đặt maximum Java heap.

**5.** Vì sao byte arrays của demo không thể GC reclaim ngay?  
**Đáp án:** References được giữ qua `slots`/`retained`, nên arrays vẫn reachable trong thời gian cấp phát.

**6.** `OutOfMemoryError` có luôn báo `Java heap space` không?  
**Đáp án:** Không, còn Metaspace, direct buffer, native thread resources và chi tiết khác.

**7.** Tại sao mã stack có base case 1.000.000?  
**Đáp án:** Để workload hữu hạn ngay cả nếu runtime không gặp SOE ở độ sâu thông thường.

**8.** Marker `result=StackOverflowError` có cho biết chính xác recursion depth?  
**Đáp án:** Không. Frame size, JVM flags và JIT/CPU khiến depth khác.

**9.** Bật `-Xlog:gc=info:file=...` mang lại gì?  
**Đáp án:** Quan sát những GC events trong JVM; không thay Java API semantics.

**10.** Có thể dùng `System.gc()` để chắc chắn dọn tất cả arrays còn reachable không?  
**Đáp án:** Không. GC không reclaim strongly reachable objects; explicit GC request cũng không có deadline/guarantee giải phóng chúng.

**11.** Tại sao heap dump cần bảo vệ?  
**Đáp án:** Nó có thể chứa object graphs cùng dữ liệu nhạy cảm và có kích thước đáng kể.

**12.** Một JVM bị container OOMKilled có phải luôn ném Java heap OOME không?  
**Đáp án:** Không. OS/cgroup có thể kill process trước khi Java code nhận một exception.

**13.** Có nên bắt OOME rồi cho server tiếp tục phục vụ?  
**Đáp án:** Không nên coi đó là recovery mặc định; ưu tiên diagnostics, alert, restart và sửa nguyên nhân.

**14.** Nếu tăng `-Xmx` làm chương trình chạy lâu hơn trước khi OOME, đã sửa leak chưa?  
**Đáp án:** Chưa, phải xác định retention/live-set và sửa lifecycle của objects.

## 22. Checklist thử nghiệm sạch và có thể tái hiện

- [ ] Có JDK 17+ HotSpot và thư mục lab riêng.
- [ ] Biên dịch `ResourceLimitsLab.java` thành công.
- [ ] Chạy `java ResourceLimitsLab` in `usage: stack|heap`.
- [ ] Chạy một process với `-Xss512k` và argument `stack`.
- [ ] Nhìn marker `mode=stack` và `result=StackOverflowError`, hoặc ghi rõ điều kiện khác nếu không tái hiện.
- [ ] Process stack đã thoát hoàn toàn trước khi chạy heap.
- [ ] Chạy process mới với `-Xms32m -Xmx48m` và argument `heap`.
- [ ] Nhìn marker `mode=heap` và `result=OutOfMemoryError`, hoặc điều tra nếu JVM bị OS kill/không khởi động.
- [ ] Nếu bật GC logs, lưu trong file riêng và **không** giả định số GC cố định.
- [ ] Không bật heap dump khi chưa có disk quota/quy định bảo vệ dữ liệu.
- [ ] Nếu chạy tự động, có timeout và quan sát exit code ngoài JVM.
- [ ] Không còn child process chạy ngầm sau khi test.
- [ ] Xóa artifacts chỉ trong thư mục lab khi không cần nữa.
- [ ] Phân biệt rõ nguyên nhân SOE vs OOME và hướng khắc phục.

## 23. Nguồn tham khảo chính thức

- [JVMS Java SE 25 — `2.5.2` Java Virtual Machine Stacks](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.2): mỗi thread có JVM stack; `StackOverflowError` và `OutOfMemoryError` liên quan stack expansion.
- [JVMS Java SE 25 — `2.5.3` Heap](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.3): heap shared và điều kiện OOME khi memory không thể cấp phát.
- [JVMS Java SE 25 — `2.6` Frames](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6): local variables, operand stack và method frames.
- [Oracle JDK 25 — java Command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): `-Xss`, `-Xms`, `-Xmx`, `-Xlog:gc`, `-XX:+HeapDumpOnOutOfMemoryError`, `-XX:HeapDumpPath`, collector flags.
- [Oracle JDK 25 — Troubleshoot Memory Leaks](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshooting-memory-leaks.html): phân biệt `Java heap space`, GC overhead, native memory và memory leaks.
- [Oracle JDK 25 — Prepare Java for Troubleshooting](https://docs.oracle.com/en/java/javase/25/troubleshoot/prepare-java-troubleshooting.html): GC logging, JFR, heap dumps và diagnostics.
- [Java SE 25 API — `StackOverflowError`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/StackOverflowError.html): định nghĩa lỗi khi stack overflow do recursion quá sâu hoặc nguyên nhân khác.
- [Java SE 25 API — `OutOfMemoryError`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/OutOfMemoryError.html): error khi JVM không thể allocate và GC không cung cấp thêm đủ memory.
- [OpenJDK JEP 271 — Unified GC Logging](https://openjdk.org/jeps/271): nền tảng của `-Xlog:gc`.
- [Oracle Java `jcmd` command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html): diagnostics trên JVM process có quyền attach, bao gồm GC heap information.
- [Eclipse Memory Analyzer](https://eclipse.dev/mat/): công cụ đọc HPROF, dominator tree và retained paths; đảm bảo xử lý dumps an toàn.

**Kết luận:** `StackOverflowError` là vấn đề **thread stack depth/capacity**; `OutOfMemoryError: Java heap space` là vấn đề **heap allocation so với heap capacity và reachable live-set**. Để thực nghiệm, giới hạn **per-thread stack bằng `-Xss`**, **heap bằng `-Xmx`**, **số bước trong code bằng finite bounds** và **process bằng isolation/timeout**. Chỉ coi demo thành công khi quan sát đúng marker/diagnostics; không suy từ một exit code hoặc từ memory usage duy nhất. Khi sửa ứng dụng thật, ưu tiên giải quyết recursion/retention đúng nghĩa thay vì chỉ tăng memory flags.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-experiments-010">Đối chiếu heap, native memory và bộ nhớ tiến trình từ nhiều nguồn đo</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu heap, native memory và bộ nhớ tiến trình từ nhiều nguồn đo

## 1. Tại sao các công cụ đo cùng một Java process lại cho kết quả khác nhau?

Một JVM có thể báo **heap used 600 MiB**, HotSpot NMT báo **1,2 GiB committed**, Linux báo **RSS 1,5 GiB**, và container báo **memory.current 1,7 GiB**. Những con số khác nhau không tự chứng minh công cụ sai; chúng phản ánh **phạm vi, đơn vị, thời gian chụp và cách hạch toán** khác nhau.

Chủ đề này thuộc **Thực nghiệm JVM**: mục tiêu là đối chiếu các nguồn, hiểu cái gì có thể so trực tiếp, cái gì không được cộng trừ, và xây quy trình điều tra sự cố memory/RSS/OOM với dữ liệu đo có căn cứ.

Các khái niệm phải phân biệt trước:

- **Heap used**: vùng heap được JVM ghi nhận đang được sử dụng cho Java objects và các trạng thái tương ứng theo memory management.
- **Heap committed**: lượng heap memory VM đã bảo đảm dành cho usage tại thời điểm đo. Không đồng nghĩa tất cả pages resident.
- **Heap max**: giới hạn heap theo VM/API, thường chịu ảnh hưởng `-Xmx`. Không phải giới hạn RSS của process.
- **NMT reserved**: vùng địa chỉ ảo VM đặt trước cho subsystem; có thể rất lớn.
- **NMT committed**: phần memory được VM tracked và committed; NMT total **đã bao gồm Java Heap**.
- **RSS/Working Set**: resident physical pages gắn với process theo OS accounting; có thể gồm shared pages.
- **PSS**: proportional share của resident pages tính đến việc các pages được nhiều processes chia sẻ.
- **Virtual size/VSZ/VIRT**: không gian địa chỉ ảo; **không đồng nghĩa RAM resident**.
- **Container/cgroup memory**: memory charge cho một cgroup; có thể chứa cả file cache, kernel memory và process khác.

> **Câu hỏi đúng không phải “heap + native có bằng RSS không?” mà là “mỗi số đo đã bao phủ và bỏ sót những vùng nào, tại thời điểm nào, và có chồng lắp với số kia không?”**

## 2. Bản đồ các vùng memory quanh JVM

~~~text
                     Java process address space
+-------------------------------------------------------------+
| Java Heap: object instances, arrays, runtime Java objects   |
|   tracked in MemoryMXBean and NMT "Java Heap"                |
+-------------------------------------------------------------+
| HotSpot internal memory                                     |
|   Metaspace / compressed class space / metadata            |
|   JIT code cache and compiler arenas                        |
|   GC tables, remembered sets, auxiliary structures          |
|   Platform/carrier threads and native stacks                |
|   Runtime/internal allocations                              |
+-------------------------------------------------------------+
| Other memory and mappings                                   |
|   DirectByteBuffer / native allocations                      |
|   JNI/JNA/third-party malloc, TLS/compression libraries     |
|   File-backed mappings, shared libraries, executable pages  |
+-------------------------------------------------------------+
| OS counters: virtual mappings, committed, resident, shared  |
+-------------------------------------------------------------+
          |
          v
Container/cgroup: may include file cache/kernel/other processes
~~~

Đây là sơ đồ **phân loại**, không phải phép cộng toán học chính xác: một vùng có thể được nhiều công cụ nhìn thấy theo các góc khác nhau. Ví dụ `Java Heap` trong NMT đã bao phủ vùng heap mà `MemoryMXBean` mô tả, dù chỉ số `committed` và `used` khác nhau.

Một container có thể có cgroup memory cao hơn RSS Java process do kernel memory/file cache hoặc các processes phụ. Shared mappings làm tổng RSS của nhiều tiến trình có thể đếm cùng physical pages nhiều lần. NMT lại có phạm vi hẹp hơn OS maps vì không đo toàn bộ các thư viện ngoài VM.

## 3. Heap: `used`, `committed`, `max` và `init`

`ManagementFactory.getMemoryMXBean().getHeapMemoryUsage()` trả `MemoryUsage` với các fields:

| Field | Diễn giải | Không thể suy ra |
| --- | --- | --- |
| `used` | Memory heap JVM báo đang sử dụng tại thời điểm gọi | Tổng Java process memory hoặc toàn bộ live-object size chính xác |
| `committed` | Heap đã được VM commit và bảo đảm cho memory usage | Số pages đang resident vật lý |
| `max` | Giới hạn tối đa của usage khi JVM xác định được | Total memory cgroup; có thể là -1 nếu không xác định |
| `init` | Initial amount mà VM yêu cầu theo API, có thể -1 | Resident heap ban đầu |

Ví dụ **giả định**:

~~~text
Heap used       = 120 MiB
Heap committed  = 256 MiB
Heap max        = 1024 MiB
Process RSS     = 430 MiB
~~~

Không được nói JVM đang chiếm **1024 MiB RAM vật lý** chỉ vì max 1024 MiB. Cũng không nói native memory = 430 − 120 = 310 MiB: phần chênh còn chứa heap committed-but-unused **nếu resident**, code/cache/metadata, mappings và shared libraries.

### `-Xms`, `-Xmx`

~~~bash
java -Xms64m -Xmx128m MemorySourcesDemo
~~~

Trên HotSpot, `-Xms` đặt initial heap size, `-Xmx` đặt maximum heap size. `-Xmx` **không** giới hạn metaspace, native thread stacks, direct memory, JNI allocations, file cache hay toàn bộ RSS. Nếu Java heap chưa đầy mà container bị OOMKilled, đây vẫn là tình huống hoàn toàn có thể xảy ra.

`Runtime.totalMemory()`, `Runtime.freeMemory()` và `Runtime.maxMemory()` là một góc nhìn khác vào heap, không phải counter OS. Khi cần các pool như Eden/Old/Metaspace/CodeHeap, xem `MemoryPoolMXBean` và chú ý pool names phụ thuộc GC/JDK.

## 4. Non-heap JMX khác toàn bộ native memory

`MemoryMXBean.getNonHeapMemoryUsage()` là tổng non-heap **memory areas mà JVM hỗ trợ quản lý qua bean**, thường liên quan class metadata/metaspace/code cache. Nó **không có hợp đồng bao phủ** mọi native allocation từ libc, JNI, stacks hoặc tất cả direct buffers.

Do đó:

- `heapUsed + nonHeapUsed` **không phải** RSS.
- `nonHeapUsed` **không phải** “mọi memory ngoài heap” của tiến trình.
- Không có công thức đơn giản lấy nonheap rồi cộng với NMT summary, bởi có các thành phần cùng đo lại một vùng.

### `BufferPoolMXBean`: direct/mapped buffers

Java 17 có `BufferPoolMXBean` với `getCount()`, `getMemoryUsed()` và `getTotalCapacity()` cho những buffer pools VM công bố như `direct` hoặc `mapped`.

Ý nghĩa:

- `directMemoryUsed` ước lượng native memory pool cho buffers được theo dõi.
- `totalCapacity` là tổng logical buffer capacities tương ứng.
- Cả hai thường phản ánh **cùng nhóm buffers**, không cộng hai trường như hai cấp phát độc lập.
- Không phải mọi off-heap `malloc`, JNI data, foreign allocator đều xuất hiện trong `direct` pool.

Nếu một thư viện native dùng `malloc` 500 MiB ngoài NIO, `BufferPoolMXBean direct` có thể vẫn gần như không đổi.

## 5. HotSpot Native Memory Tracking (NMT) là công cụ gì?

**NMT** theo dõi memory mà **HotSpot VM** quản lý theo những instrumentation nội bộ. Nó chia theo categories như:

| Category | Chi phí thường đại diện |
| --- | --- |
| `Java Heap` | Reserved/committed heap memory |
| `Class` | Metaspace, compressed class space, class metadata |
| `Thread` | JVM thread structures, thread stack reservations/commit |
| `Code` | JIT compiled code, code cache |
| `GC` | GC tables, remembered sets, VM GC bookkeeping |
| `Compiler` | JIT compiler working memory |
| `Internal`, `Symbol`, `Other` | Những subsystems khác tùy JDK |
| `Native Memory Tracking` | Overhead của NMT |
| `Arena Chunk` | Arena allocations/caches theo cách VM quản lý |

Tên và phạm vi categories có thể khác qua JDK/version. `Other` không đồng nghĩa `DirectByteBuffer` một cách phổ quát.

**Giới hạn được Oracle JDK 17 nêu rõ:** NMT chủ yếu theo dõi **HotSpot VM internal allocations**, **không theo dõi đầy đủ cấp phát của third-party native code và JDK class libraries**, và không mô tả đầy đủ CDS mappings. Vì vậy NMT không thay thế OS-level native tools.

### Bật từ startup; không bật sau khi process đã chạy

~~~bash
java -XX:NativeMemoryTracking=summary -Xms64m -Xmx128m MemorySourcesDemo
~~~

Các giá trị `off`, `summary` và `detail`:

- `off`: mặc định.
- `summary`: memory theo category.
- `detail`: chi tiết allocation call sites và virtual memory maps, tốn overhead hơn.

NMT có thể tạo **khoảng 5–10% performance overhead** theo hướng dẫn Oracle trong một số kịch bản. Không bật vô điều kiện trên mọi production service để rồi so performance với một JVM không bật NMT.

`jcmd` có thể **tắt** NMT; **không thể bật hoặc restart NMT** cho JVM vốn khởi động với NMT off. Muốn dùng `detail` cần restart process với flag `detail`.

### Lệnh inspect qua `jcmd`

~~~text
jcmd
jcmd PID VM.native_memory summary scale=MB
jcmd PID VM.native_memory baseline
jcmd PID VM.native_memory summary.diff scale=MB
jcmd PID VM.native_memory detail scale=MB
jcmd PID VM.native_memory detail.diff scale=MB
~~~

**Phải thay `PID` bằng process ID thực.** `detail` chỉ có ý nghĩa nếu tracking level phù hợp được bật khi JVM start. Với các giá trị nhỏ, dùng `scale=KB` để tránh làm tròn các fields xuống 0 MB.

`VM.native_memory baseline` tạo snapshot tham chiếu. `summary.diff` hoặc `detail.diff` so hiện tại với baseline; nó **không dọn memory**. Chênh lệch dương không tự chứng minh leak: class loading, compiler, buffer pools và caches có thể tăng trong warm-up.

### Reserved không bằng committed và cũng không bằng RSS

Một NMT category như `Class reserved=1024MB, committed=0MB` có thể là **reservation địa chỉ ảo rất lớn**, còn commit thực tế ít hơn 1 MB và bị làm tròn khi scale=MB. Không nói 1 GiB resident đã bị chiếm.

Một process có NMT reserved 1,5 GiB có thể có Working Set thấp hơn rất nhiều. Ngược lại, OS virtual size có thể lớn hơn NMT reserved vì có những mappings không được NMT tính.

## 6. Ví dụ Java 17 chạy được: heap 8 MiB, direct buffer 8 MiB

Tạo **`MemorySourcesDemo.java`**:

~~~java
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.Locale;

public final class MemorySourcesDemo {
    private static volatile Object retained;

    static String mib(long bytes) {
        if (bytes < 0) return "undefined";
        return String.format(Locale.ROOT, "%.1f", bytes / 1048576.0);
    }

    static void snapshot(String phase) {
        MemoryUsage heap =
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap =
                ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();

        System.out.println(phase + ".heapUsedMiB=" +
                mib(heap.getUsed()));
        System.out.println(phase + ".heapCommittedMiB=" +
                mib(heap.getCommitted()));
        System.out.println(phase + ".heapMaxMiB=" +
                mib(heap.getMax()));
        System.out.println(phase + ".nonHeapUsedMiB=" +
                mib(nonHeap.getUsed()));

        for (BufferPoolMXBean pool :
                ManagementFactory.getPlatformMXBeans(
                        BufferPoolMXBean.class)) {
            if ("direct".equals(pool.getName())) {
                System.out.println(phase + ".directCount=" +
                        pool.getCount());
                System.out.println(phase + ".directMemoryUsedMiB=" +
                        mib(pool.getMemoryUsed()));
                System.out.println(phase + ".directCapacityMiB=" +
                        mib(pool.getTotalCapacity()));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("pid=" + ProcessHandle.current().pid());
        snapshot("before");

        byte[] heapArray = new byte[8 * 1024 * 1024];
        ByteBuffer direct = ByteBuffer.allocateDirect(8 * 1024 * 1024);
        for (int i = 0; i < heapArray.length; i += 4096) {
            heapArray[i] = 1;
        }
        for (int i = 0; i < direct.capacity(); i += 4096) {
            direct.put(i, (byte) 1);
        }
        retained = new Object[] {heapArray, direct};

        snapshot("after");
        System.out.println("requestedPayloadMiB=16");
        System.out.println("heapFirstByte=" + heapArray[0]);
        System.out.println("directFirstByte=" + direct.get(0));

        long holdMillis = Long.getLong("lab.holdMillis", 0L);
        if (holdMillis > 0) {
            System.out.println("inspectionWindowMs=" + holdMillis);
            Thread.sleep(holdMillis);
        }
        Reference.reachabilityFence(retained);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 MemorySourcesDemo.java
java -Xms64m -Xmx128m -XX:NativeMemoryTracking=summary MemorySourcesDemo
~~~

Những dòng **bất biến**:

~~~text
requestedPayloadMiB=16
heapFirstByte=1
directFirstByte=1
~~~

`pid=...`, `before/after.heapUsedMiB`, non-heap, direct pool counts và committed figures **phụ thuộc runtime**, không có stdout cố định. Một ví dụ đã được **biên dịch bằng `javac --release 17` và chạy thực tế với JDK 24** trên Windows.

### Vì sao requested 16 MiB không có nghĩa RSS tăng đúng 16 MiB?

- Mảng `byte[]` tiêu thụ heap space; direct buffer giữ **native memory** với Java wrapper trên heap.
- Heap có thể đã commit đủ pages; cấp phát mảng không nhất thiết làm heap `committed` tăng.
- Allocator/page granularity, GC, native wrapper metadata và JMX sampling đều ảnh hưởng.
- Chạm mỗi trang bằng một byte khuyến khích memory accesses nhưng OS vẫn quản lý residency theo chính sách riêng.
- `retained` và `reachabilityFence` tránh để JIT/GC coi các references đã hết sử dụng trước lúc quan sát.
- `getMemoryUsed()` của direct buffer pool là một **estimate của pool**, không phải counter mọi `malloc`.

### Một lần chạy thực tế trên JDK 24 / Windows

Trong một lượt đã đo (không phải cam kết trên Java 17/runtime khác):

~~~text
before.heapUsedMiB=2.1
before.heapCommittedMiB=66.0
before.heapMaxMiB=128.0
before.nonHeapUsedMiB=2.0
before.directCount=0
before.directMemoryUsedMiB=0.0
before.directCapacityMiB=0.0
after.heapUsedMiB=12.1
after.heapCommittedMiB=66.0
after.heapMaxMiB=128.0
after.nonHeapUsedMiB=4.4
after.directCount=1
after.directMemoryUsedMiB=8.0
after.directCapacityMiB=8.0
requestedPayloadMiB=16
heapFirstByte=1
directFirstByte=1
~~~

Heap used tăng **không đúng 8 MiB** vì còn các allocations VM/application khác. Heap committed **không đổi** trong thử nghiệm này; direct pool được báo tăng khoảng 8 MiB. **Không kết luận** rằng OS Working Set tăng chính xác 16 MiB chỉ từ các số Java API.

## 7. Tạo inspection window để đo cả NMT và OS trên đúng PID

Chạy chương trình với khoảng chờ:

~~~bash
java -Xms64m -Xmx128m -XX:NativeMemoryTracking=summary \
  -Dlab.holdMillis=30000 MemorySourcesDemo
~~~

PowerShell:

~~~powershell
java -Xms64m -Xmx128m -XX:NativeMemoryTracking=summary -Dlab.holdMillis=30000 MemorySourcesDemo
~~~

Ngay khi app in `pid=<số PID thực>` và `inspectionWindowMs=30000`, ở terminal khác thực hiện:

~~~text
jcmd PID VM.native_memory summary scale=MB
jcmd PID GC.heap_info
jcmd PID VM.flags
~~~

- `VM.native_memory`: HotSpot internal reserved/committed tracked theo category.
- `GC.heap_info`: heap layout/counters VM công bố theo collector.
- `VM.flags`: xác nhận flags thực (heap, NMT, GC).
- Cần quyền process attach phù hợp; lệnh `jcmd PID` sai PID/đã exit sẽ thất bại, không tự thử trên process khác.
- Không dùng `GC.heap_dump` tùy tiện trên production: heap dump có thể tốn disk, gây pause và chứa dữ liệu nhạy cảm.

### Sample NMT cùng tiến trình đã kiểm chứng

Một lần chạy **JDK 24/Windows**, các dòng chọn lọc từ `jcmd ... summary scale=MB`:

~~~text
Total: reserved=1589MB, committed=182MB
- Java Heap (reserved=128MB, committed=66MB)
- Class     (reserved=1024MB, committed=0MB)
- Thread    (reserved=21MB, committed=1MB)
- Code      (reserved=242MB, committed=8MB)
- GC        (reserved=75MB, committed=74MB)
- Internal  (reserved=1MB, committed=1MB)
- Other     (reserved=8MB, committed=8MB)
~~~

Đây là **trích lọc**, không phải toàn bộ NMT report. Những con số có ý nghĩa:

- `Total committed 182 MB` **bao gồm** `Java Heap committed 66 MB`. Không cộng 182+66.
- `Java Heap reserved 128 MB` phù hợp giới hạn heap được đặt, **không phải heap used**.
- `Class reserved 1024 MB` không có nghĩa 1 GiB vật lý đang chứa class metadata.
- `Other committed 8 MB` gần với direct buffer trong lab cụ thể, **không phải quy luật NMT luôn cho direct buffers ở Other**.
- `GC committed` là VM structures cho collector, **không phải** “GC pause” hay “heap objects còn sống”.
- `scale=MB` có rounding, cần `scale=KB` để điều tra các số nhỏ.

## 8. Linux process memory: RSS, VSZ, PSS và mapped pages

Trên Linux, có thể dùng:

~~~bash
ps -o pid,rss,vsz,comm -p PID
cat /proc/PID/status
cat /proc/PID/smaps_rollup
pmap -x PID
~~~

`PID` là chỗ thay bằng **số PID thật**. Các files trong `/proc` và `pmap` còn tùy kernel, namespaces và quyền đọc process, đặc biệt trong container. Đừng xử lý lỗi Permission denied bằng việc suy đoán giá trị zero.

### RSS

**Resident Set Size** là resident pages gắn với process, có thể bao gồm shared file-backed mappings. Nó khác heap used và khác `NMT committed`.

Nếu JVM commit 512 MiB heap nhưng chưa touch hết memory pages thì resident footprint có thể thấp hơn 512 MiB (tùy OS/VM flags). Ngược lại, native stacks, metaspace, shared libraries, off-heap buffers có thể làm RSS lớn hơn heap committed.

### VSZ / VIRT

**Virtual size** phản ánh address space mappings/reservations, không phải mọi byte đang chiếm RAM. Một JVM có `VSZ=20 GiB` không bắt buộc có `RSS=20 GiB` và cũng không mặc nhiên OOM trong container có memory limit nhỏ hơn 20 GiB.

**Không tính** `VSZ − RSS` thành “native memory”: vùng chênh còn gồm reserved-but-not-committed mappings, page residency, code, libraries và nhiều yếu tố khác.

### PSS

**Proportional Set Size** chia shared resident pages theo số processes đang chia sẻ. Nếu hai JVM cùng map shared library pages hoặc CDS archive, cộng RSS của chúng có thể đếm lặp shared pages. PSS phù hợp hơn khi muốn phân bổ chi phí resident/shared.

### `smaps_rollup`

Một mẫu **minh họa**, không phải dữ liệu của lab Windows:

~~~text
Rss:               500000 kB
Pss:               450000 kB
Shared_Clean:       90000 kB
Shared_Dirty:        1000 kB
Private_Clean:      15000 kB
Private_Dirty:     394000 kB
~~~

Không cộng `Rss + Pss + Private_Dirty`; các trường mô tả các cách nhìn/phân loại **chồng lắp**. Kernel có thể cung cấp thêm `Anonymous`, `Swap`, `Locked` và nhiều trường khác tùy phiên bản và quyền.

## 9. Container: memory cgroup không bằng RSS Java process

Trong Linux **cgroup v2**, với cgroup được mount vào `/sys/fs/cgroup` ở namespace đang dùng:

~~~bash
cat /sys/fs/cgroup/memory.current
cat /sys/fs/cgroup/memory.peak
cat /sys/fs/cgroup/memory.max
cat /sys/fs/cgroup/memory.stat
cat /sys/fs/cgroup/memory.events
~~~

Lưu ý `cgroup v1` và các Kubernetes/container runtimes có thể dùng đường dẫn hoặc layout khác. Dùng `/proc/self/cgroup`, mountinfo hoặc container tooling để xác định cgroup thực của workload khi cần.

- `memory.current`: bytes được charge vào cgroup; không chỉ Java heap.
- `memory.peak`: mức đỉnh theo cơ chế kernel và availability của phiên bản.
- `memory.max`: hard limit hoặc `max` khi không được đặt theo cách đó.
- `memory.stat`: breakdown, có thể có `anon`, `file`, `kernel`.
- `memory.events`: events liên quan high/max/oom/oom_kill theo kernel.

Một cgroup chứa JVM + sidecar + file cache + kernel memory có thể báo memory usage khác RSS của **một JVM process**. Shared pages/cgroup accounting cũng có quy tắc riêng.

### Vì sao OOMKilled dù `heapUsed < Xmx`?

Process có thể bị kernel/cgroup kill vì:

1. Native/direct allocations, JNI libraries hoặc mmap.
2. Platform thread stacks, GC/JIT/code cache/class metadata.
3. Committed heap pages chưa được dùng hết nhưng đã resident.
4. File cache/kernel memory charge hoặc các processes khác trong group.
5. Container limit thấp hơn tổng working set thực.
6. Overhead khi JVM tăng threads/heap/native allocations nhanh.

`OutOfMemoryError: Java heap space` là lỗi Java heap allocation; **cgroup OOM kill** là hành động của kernel, có thể chấm dứt JVM trước khi Java kịp ném exception hay in heap dump.

Không thấy Java heap OOME không chứng minh container có đủ memory.

## 10. Windows: Working Set, Private Bytes và Virtual Size

Trên PowerShell:

~~~powershell
Get-Process -Id 12345 |
    Select-Object Id, WorkingSet64, PrivateMemorySize64, VirtualMemorySize64
~~~

Thay `12345` bằng process PID đang chạy.

- `WorkingSet64`: trang resident được tính trong working set của process theo Windows, có thể gồm shared.
- `PrivateMemorySize64`: private memory counter của Windows/.NET process, **không đồng nghĩa Working Set**.
- `VirtualMemorySize64`: virtual address usage counter, **không đồng nghĩa RAM đang resident**.

Trong một lượt chạy đã thu được **trên chính process demo có NMT 182 MB committed**:

~~~text
WorkingSet64        = 70266880 bytes  (khoảng 67.0 MiB)
PrivateMemorySize64 = 196624384 bytes (khoảng 187.5 MiB)
VirtualMemorySize64 = 6251425792 bytes (khoảng 5961 MiB)
~~~

NMT báo `reserved 1589 MB` và `committed 182 MB`, OS báo Working Set chỉ ~67 MiB và virtual memory ~5961 MiB. **Không có dấu bằng bắt buộc** giữa NMT tracked memory, Windows working set và Windows virtual mappings. Các snapshots cũng được gọi liên tiếp, không phải giao dịch atomic ở cùng nanosecond.

Đừng lấy `VirtualMemorySize64 − NMTreserved` rồi gọi đó là “unknown native leak”: một công cụ chỉ đếm VM-tracked reservations, một công cụ xem whole-process mappings.

## 11. Bảng đối chiếu các nguồn đo

| Nguồn | Giá trị chính | Bao phủ điều gì? | Không bao phủ/giới hạn |
| --- | --- | --- | --- |
| `MemoryMXBean` | Heap used/committed/max | JVM managed heap | Native stacks/JNI/RSS |
| `MemoryMXBean` nonheap | Non-heap used | JVM exposed non-heap pools | Không phải tổng native |
| `MemoryPoolMXBean` | Pools memory usage | Heap generations/Metaspace/Code cache tùy VM | Không phải whole-process |
| `BufferPoolMXBean` | Direct/mapped count/capacity/memory | NIO pools được VM công bố | Không phải mọi off-heap |
| `jcmd GC.heap_info` | Heap info | Collector-specific snapshot | Không phải RSS |
| `jstat -gc` | GC counters | Heap generation/pool capacity/usage | Không phải tổng native |
| HotSpot NMT | Reserved/committed categories | VM-tracked internal allocations | JNI/third-party allocations có thể không thấy |
| Linux `ps/status` | RSS/VSZ | Process view | Không tự tách heap/native |
| Linux `smaps_rollup` | RSS/PSS/private/shared | Process page mappings | Phụ thuộc kernel/permissions |
| Windows `Get-Process` | WS/private/virtual | OS process counters | Khác Linux accounting |
| cgroup v2 files | Current/peak/stat/events | Memory charge cgroup | Không chỉ một process |
| JFR/GC logs | GC events, heap summaries, allocation timelines | Enabled JVM events | Không đo mọi native allocator |

**Không có phép cộng “heap + NMT + RSS”.** Chúng là những thước đo cùng memory từ những lớp khác nhau; phải xác định intersection khi phân tích.

## 12. Sáu công thức sai thường xuất hiện

### Sai: `nativeMemory = RSS − Xmx`

`Xmx` là maximum heap config, không phải resident heap. Giải pháp: xem heap used/committed và mappings thực, đồng thời đối chiếu NMT + OS.

### Sai: `nativeMemory = RSS − heapUsed`

Chênh có thể chứa heap pages committed nhưng unused, shared libs, JIT code, GC bookkeeping, file mappings và account differences, không chỉ native malloc.

### Sai: `totalProcess = NMTtotalCommitted + heapCommitted`

NMT total đã chứa category Java Heap → **đếm trùng**.

### Sai: `totalOffHeap = nonHeapUsed + directMemoryUsed + NMTOther`

Một số phần có thể chồng lắp; NMT Other không bằng direct trong mọi JVM, còn third-party malloc có thể không ở nguồn nào trong ba.

### Sai: `containerMemory = JVM RSS`

Cgroup còn file cache/kernel/sidecars/process khác và cách charge riêng.

### Sai: `NMTreserved = RAM đang sử dụng`

Reservation là address-space accounting, không phải pages resident.

## 13. Phân tích kịch bản heap ổn định nhưng RSS tăng

Dữ liệu **giả định**, cùng một service ở bốn thời điểm:

| Metric | 0 phút | 10 phút | 20 phút | 30 phút |
| --- | ---: | ---: | ---: | ---: |
| Heap used (MiB) | 350 | 360 | 345 | 355 |
| NMT committed (MiB) | 650 | 660 | 665 | 667 |
| RSS (MiB) | 780 | 900 | 1020 | 1200 |
| Cgroup current (MiB) | 860 | 1010 | 1160 | 1370 |

Cách suy luận an toàn:

1. Heap used ổn định → chưa thấy Java heap usage tăng lớn; chưa loại trừ heap retention theo GC timing.
2. NMT committed tăng nhẹ hơn RSS → có thể có tăng residency, allocator caches hoặc memory nằm ngoài phạm vi NMT.
3. Kiểm tra `smaps_rollup` / mappings: tăng `Private_Dirty` anonymous hay file-backed resident?
4. Kiểm tra `memory.stat`: tăng `anon`, `file` hay `kernel`?
5. Kiểm tra buffer pool, thread count, metaspace, JIT code cache và native/JNI libraries.
6. Nếu nghi third-party malloc leak, dùng OS/native allocator profiler phù hợp và debug symbols; NMT không thể thấy hết.
7. So lại dưới **cùng workload**, sau warm-up và qua nhiều cycles trước khi kết luận **unbounded leak**.

### NMT category Thread tăng

Xem platform-thread count, native stack reservations và thread pools. `Thread reserved` tăng không nghĩa physical RSS tăng cùng lượng, vì reserved khác resident.

### NMT Class tăng

Kiểm tra dynamic class loaders, class generation, metaspace pressure. Một `Class reserved` lớn nhưng committed ổn định có thể là bình thường.

### NMT Other tăng cùng buffer pool direct

Đây có thể là một **manh mối** về NIO direct buffers trên đúng build, không phải bằng chứng mọi native allocation nằm trong Other. Kiểm tra direct buffer allocations/lifetime, `MaxDirectMemorySize` và app code.

### NMT gần như ổn định, RSS vẫn tăng

Thử kiểm tra JNI/third-party allocators, memory-mapped files, OS residency và allocator fragmentation/caching. NMT không phải nguồn để kết luận “không có native leak” khi RSS tăng mà heap ổn định.

## 14. Trường hợp committed lớn nhưng RSS thấp

Giả sử (ví dụ) NMT Java Heap committed = 2 GiB nhưng RSS mới 1 GiB. Điều này **có thể** xảy ra theo OS/JVM mapping/page-touch policies: không phải mọi committed page đã resident tại thời điểm đo; OS có thể reclaim pages.

Khi workload chạm nhiều object/pages nằm trong committed space, **RSS có thể tăng dù committed không thay đổi**. Đó không nhất thiết là thêm heap.

HotSpot flag `-XX:+AlwaysPreTouch` có thể chạm memory pages sớm hơn khi heap được commit, làm **startup time và RSS đầu tiến trình** thay đổi. Benchmark memory phải ghi rõ các flags/collector/JDK, không so một JVM bật pre-touch với JVM khác tắt rồi quy hoàn toàn sự khác biệt cho collector.

## 15. NMT baseline và time-series phục vụ điều tra leak

Quy trình đề xuất:

1. Khởi động HotSpot với `-XX:NativeMemoryTracking=summary` (hoặc `detail` cho điều tra sâu).
2. Chạy workload đại diện tới trạng thái ổn định phù hợp; nếu muốn đo startup growth thì đặt baseline trước startup work một cách có chủ đích.
3. Chụp heap MXBeans, NMT, OS RSS/PSS/working set và cgroup usage kèm **timestamp, PID, run ID**.
4. Tạo baseline:

~~~text
jcmd PID VM.native_memory baseline
~~~

5. Sau một khoảng 10 phút cùng workload, lấy tất cả nguồn lần nữa và:

~~~text
jcmd PID VM.native_memory summary.diff scale=MB
~~~

6. Nếu tăng bất thường, lặp 3–5 thời điểm để xem trend, không chỉ một delta.
7. Nếu NMT tăng ở VM subsystem, phân tích category, khi cần restart với NMT detail rồi xem call sites.
8. Nếu OS memory tăng ngoài NMT, chuyển sang OS memory mappings/native profiler.
9. Ghi GC events, allocation rate, number of threads, classloader/libraries và OS cache.
10. Xác minh có đạt **plateau** sau warm-up hoặc allocator cache growth chưa. Leak là retention không giới hạn/không được giải phóng khi đáng lẽ phải giải phóng, không phải cứ committed tăng một lần.

NMT `baseline` là mốc so, không phải lệnh reset heap. `summary.diff` không tự sửa leak.

## 16. JFR, GC logs và heap dumps dùng ở đâu?

GC logs giúp biết heap/GC behavior:

~~~bash
java '-Xlog:gc=info,gc+heap=debug:file=gc-heap.log:uptime,level,tags' \
  -Xms64m -Xmx128m MemorySourcesDemo
~~~

JFR có thể thu GC heap summaries và events được bật:

~~~bash
java '-XX:StartFlightRecording=filename=memory-lab.jfr,settings=profile' \
  -Xms64m -Xmx128m MemorySourcesDemo
~~~

Sau khi có recording:

~~~bash
jfr summary memory-lab.jfr
jfr print --events jdk.GCHeapSummary,jdk.GarbageCollection memory-lab.jfr
~~~

JFR event enablement, threshold và tên có thể thay đổi giữa JDKs. Không thấy event **không có nghĩa** không có activity tương ứng. Một đoạn lab chạy vài milliseconds không phải benchmark memory leak.

`jcmd PID GC.class_histogram` hoặc `GC.heap_dump` giúp điều tra heap objects/retention, nhưng có thể ảnh hưởng VM, tốn disk/memory/pause và chứa dữ liệu nhạy cảm như tokens/passwords. Chỉ thực hiện với quyền thích hợp, kế hoạch impact và kiểm tra help của đúng JDK.

**GC log không phải native memory profiler**. Nó không đo mọi `malloc` của JNI libraries.

## 17. Time alignment: không đòi số khớp byte-per-byte ở những thời điểm khác nhau

Thu dữ liệu ở nhiều nguồn **không phải atomic snapshot**:

~~~text
t0  Java API MemoryMXBean
t1  jcmd Native Memory Tracking
t2  Linux /proc hoặc Windows Get-Process
t3  cgroup memory.current
t4  JFR/GC events
~~~

Giữa t0 và t4, compiler, GC, application threads và OS paging vẫn hoạt động. Nên:

- Lưu cùng **run ID, PID, container id, UTC timestamp, JDK build, flags và GC type**.
- Dùng source clocks rõ ràng, mapping uptime/UTC khi cần.
- Lấy nhiều samples trong **steady workload** thay vì chỉ một snapshot.
- Đừng so NMT của PID mới với RSS của PID cũ sau restart.
- Không trừ những counter có definitions/units khác.
- Tách **startup/warm-up/steady/peak** để tránh hiểu allocation growth bình thường thành leak.
- Tính **delta cùng metric/cùng nguồn** trước khi so delta cross-source theo lý giải phạm vi.

Không gọi `System.gc()` trước mọi sample như một “memory normalization”: nó chỉ là GC request, có thể bị bỏ qua và làm sai workload. Nếu cần after-GC live-set measurement, dùng protocol có kiểm soát và nói rõ GC/flags.

## 18. Những quan niệm sai

1. **“Heap used chính là process RAM.”** Sai.
2. **“Xmx giới hạn RSS.”** Sai.
3. **“NonHeapMXBean = toàn bộ native memory.”** Sai.
4. **“NMT đo mọi JNI/third-party native malloc.”** Sai.
5. **“NMT total không bao gồm Java Heap.”** Sai.
6. **“NMT committed = RSS.”** Không có cam kết.
7. **“Reserved là RAM thực đã chiếm.”** Sai.
8. **“Class reserved 1024MB chứng tỏ 1GiB classes đã resident.”** Sai.
9. **“Direct buffer pool đo mọi off-heap.”** Sai.
10. **“Heap + NMT total = process memory.”** Sai: đếm trùng.
11. **“RSS − heapUsed chính xác là native leak.”** Sai.
12. **“RSS − Xmx chính xác là native.”** Sai.
13. **“VSZ=20GiB nghĩa process chiếm 20GiB RAM.”** Sai.
14. **“PSS luôn bằng RSS.”** Sai nếu shared pages.
15. **“Windows WorkingSet=PrivateBytes.”** Sai.
16. **“Cgroup memory.current là Java process heap.”** Sai.
17. **“Heap chưa full thì container không thể OOMKilled.”** Sai.
18. **“JVM có thể bật NMT khi process đã chạy bằng jcmd.”** Sai.
19. **“Một NMT diff dương chứng minh leak.”** Sai.
20. **“GC logs theo dõi mọi native allocations.”** Sai.
21. **“Native buffer 8 MiB làm RSS tăng đúng 8 MiB.”** Không bảo đảm.
22. **“NMT categories và số liệu y hệt trên mọi JDK.”** Sai.
23. **“OS snapshots với JMX/JFR luôn đồng bộ tuyệt đối.”** Sai.
24. **“Container memory cao hơn RSS bao nhiêu thì đó chính là kernel memory.”** Sai.
25. **“Cần tăng Xmx khi RSS cao.”** Không thể quyết định nếu chưa biết phân bố memory.

## 19. Câu hỏi tự kiểm tra

1. Heap used 256 MiB, committed 512 MiB, Xmx 1GiB: memory used theo bean là bao nhiêu? **256 MiB**.
2. NMT `Total committed` đã chứa `Java Heap committed` chưa? **Có**.
3. `NMT reserved` 5 GiB có đồng nghĩa RSS 5 GiB? **Không**.
4. Heap used 400MiB, RSS 900MiB: 500MiB còn lại có chắc là native malloc? **Không**.
5. JMX non-heap có thể thiếu JNI allocation không? **Có**.
6. Direct buffer MXBean có bao phủ mọi native malloc? **Không**.
7. Dùng chỉ số nào xem shared pages phân bổ theo tỷ lệ trên Linux? **PSS**.
8. `smaps_rollup` dùng làm gì? **Xem RSS/PSS/private/shared mapping tổng hợp**, nếu kernel/permissions cho.
9. NMT bật vào thời điểm nào? **JVM startup với flag**.
10. `summary.diff` dùng để làm gì? **So baseline NMT**, không tự dọn memory.
11. Class reserved lớn/committed ít có mặc định là leak? **Không**.
12. JNI malloc tăng, NMT flat, RSS tăng: làm gì tiếp? **OS mappings/native allocator profiler**.
13. Cgroup OOMKilled có thể xảy ra trước Java heap OOME? **Có**.
14. `memory.current` của cgroup có thể chứa file cache không? **Có**.
15. Windows WorkingSet và PrivateMemory khác nhau ở đâu? **Resident working set vs private counter**.
16. VM commit thêm pages có bắt buộc resident ngay không? **Không**.
17. `ByteBuffer.allocateDirect` có Java wrapper trên heap không? **Có**.
18. Demo yêu cầu 8MiB heap + 8MiB direct thì RSS tăng đúng 16MiB không? **Không**.
19. Một JFR sample không có GC event có chứng minh không có GC? **Không**, còn settings/timing.
20. Kết quả NMT JDK24 target Java17 có phải NMT runtime JDK17 không? **Không**, VM runtime vẫn JDK24.
21. Vì sao không cộng `NMT total + MemoryMXBean committed`? **Vì đếm trùng heap**.
22. Một process mới có PID khác thì so snapshots thế nào? **Run riêng, không ghép nguồn sai PID**.

## 20. Tài liệu tham khảo

- Oracle Java SE 17, **Native Memory Tracking**, cách bật, `jcmd`, giới hạn NMT: https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
- Oracle JDK 17, **Diagnostic Tools / Native Memory Tracking**, reservations, commits, categories: https://docs.oracle.com/en/java/javase/17/troubleshoot/diagnostic-tools.html
- Oracle JDK 17, **jcmd** (VM.native_memory, GC.heap_info, VM.flags): https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- Java SE 17, **MemoryMXBean**: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html
- Java SE 17, **MemoryUsage**: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryUsage.html
- Java SE 17, **BufferPoolMXBean**: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/BufferPoolMXBean.html
- Java SE 17, **ByteBuffer.allocateDirect()**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html#allocateDirect(int)
- Linux kernel, **The /proc Filesystem**: https://www.kernel.org/doc/html/latest/filesystems/proc.html
- Linux kernel, **cgroup v2 memory controller**: https://www.kernel.org/doc/html/latest/admin-guide/cgroup-v2.html#memory
- Microsoft .NET **Process.WorkingSet64**, Windows process counter: https://learn.microsoft.com/en-us/dotnet/api/system.diagnostics.process.workingset64
- Inside Java, **Troubleshooting Native Memory Leaks**: https://inside.java/2020/12/17/troubleshooting-native-memory-leaks/

**Kết luận:** Heap used, heap committed, non-heap JMX, HotSpot NMT reserved/committed, Linux RSS/PSS/VSZ, Windows Working Set/Private Bytes và container memory charge là **các nguồn đo khác phạm vi**. Đừng lấy `RSS − Xmx` hoặc cộng `NMT total + Java heap` rồi gọi là native usage. Để phân tích đáng tin, thu **JMX + NMT + OS process + cgroup + GC/JFR** trên **đúng PID, thời điểm, units, workload/JDK/flags**, dùng NMT baseline/diff và nhiều snapshots, rồi chỉ kết luận leak sau khi loại trừ các hiệu ứng commit, shared pages, caching và warm-up.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-experiments-011">So sánh khởi động có và không có CDS trên cùng ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh khởi động có và không có CDS trên cùng ứng dụng

## Mục tiêu bài học

**Class Data Sharing (CDS)** của HotSpot lưu một phần thông tin class đã được xử lý vào **shared archive** để những JVM processes khác có thể tái sử dụng thay vì luôn parse/xử lý lại từ đầu. Điều này có thể rút ngắn **thời gian khởi động** và giảm một phần **footprint** nhờ các trang bộ nhớ được chia sẻ. **Application CDS (AppCDS)** mở rộng phạm vi sang classes của ứng dụng và thư viện. Lợi ích thực tế không cố định: Java process chạy trong 50 ms và service khởi tạo database trong 5 giây có thể hưởng lợi theo tỷ lệ rất khác nhau.

Bài học này thuộc **Thực nghiệm JVM**, nên trọng tâm là một phép đo có thể tái lập và có kiểm chứng. Bạn sẽ thực hành với **cùng một file JAR và một bản JDK**, thay đổi duy nhất CDS flags rồi so sánh ba chế độ:

1. **OFF**: `-Xshare:off`, không dùng CDS.
2. **ON**: `-Xshare:on`, buộc dùng **default CDS archive** của JDK (dành cho kiểm thử).
3. **APP**: `-Xshare:on -XX:SharedArchiveFile=app.jsa`, dùng dynamic AppCDS archive đã tạo cho đúng ứng dụng.

Mục tiêu cần đạt:
- Giải thích class data sharing, default archive, dynamic archive, class loading, memory mapping, AppCDS và compatibility.
- Biết `-Xshare:auto` không phải bằng chứng một run đã thực sự bật CDS, còn `-Xshare:on` có thể khiến startup fail khi archive unavailable.
- Viết một chương trình **Java 17+** với business output chính xác; tạo một JAR duy nhất và không thay đổi sau lúc xây archive.
- Dùng `-Xlog:cds` và `-Xlog:class+load` xác minh **archive đang được load**, không dựa chỉ vào tên flag.
- Đo **wall-clock process startup-to-exit** bằng công cụ ngoài Java, tách khỏi thời gian kể từ đầu `main()`.
- Thiết kế thí nghiệm với warmup, nhiều runs, đảo thứ tự modes, cùng môi trường và đo median/p95.
- Phân biệt startup-to-exit của CLI, time-to-main, time-to-ready của server, steady-state throughput và JVM warmup.
- Phân tích cả các trường hợp có/no improvement, statistical noise, classpath mismatches, archived classes coverage và OS page cache.
- Nhận biết **CDS/AppCDS Java 17/21** khác phạm vi **AOT cache JDK 24+**, không quy mọi tối ưu mới cho cùng một flag.

## 1. CDS giải quyết chi phí nào?

Trong một JVM mới, một số bước đáng chú ý gồm:
1. OS tạo process Java.
2. JVM bootstrap native components, heap/runtime services.
3. Class loader tìm class bytes trong JDK runtime image hoặc các JAR/module paths.
4. VM thực hiện những công việc thích hợp như parse class structure, verify/link và thiết lập internal class metadata.
5. Class được khởi tạo theo Java semantics ở thời điểm phải initialize.
6. `main()` chạy, chương trình hoặc framework còn tiếp tục khởi tạo thành phần khác.
7. Với server, sockets mở, dependencies sẵn sàng và readiness endpoint mới trở nên healthy.

CDS có thể lưu **một phần kết quả xử lý class trước đó** trong archive để dùng lại ở bước runtime phù hợp. Khi JVM chạy, shared archive có thể được **memory-mapped**, giúp giảm parse/loading work và cho các JVMs cùng host chia sẻ read-only pages.

~~~text
                    BUILD / ARCHIVE PHASE
                             |
                 JDK classes / app classes
                             |
                 HotSpot xử lý trước phần phù hợp
                             |
                             v
                      archive (.jsa)
                             |
                    memory mapping at startup
                             |
                    +--------+--------+
                    |                 |
                  JVM A             JVM B
                    |                 |
             dùng shared data   dùng shared data
                    |                 |
               less startup work, potentially
                 some shared memory savings
~~~

**Không hiểu nhầm:**
- `.jsa` không phải `.jar` và không thay thế JAR.
- CDS không phải **GraalVM native image** và không nhất thiết có compiled Java machine code.
- CDS không có nghĩa every Java class được archived; phụ thuộc archive contents, loader và JVM restrictions.
- CDS không xóa class initialization side effects theo cách phá Java semantics.
- Một JVM có CDS vẫn có JIT compilation, GC, execution, memory allocation và application I/O bình thường.
- Shared metadata giữa nhiều JVM processes khác với chia sẻ **Java heap mutable objects** tùy tiện.

## 2. Ba loại archive hoặc cách chạy thường gặp

| Khái niệm | Nội dung | Công cụ/tùy chọn |
| --- | --- | --- |
| **Default CDS archive** | Một tập core JDK classes, thường được JDK distribution chuẩn bị sẵn | Runtime `-Xshare:auto` / `-Xshare:on` |
| **Static AppCDS archive** | Một tập classes được thu từ class list rồi archive trước | `-XX:DumpLoadedClassList`, `-Xshare:dump`, `-XX:SharedClassListFile` |
| **Dynamic AppCDS archive** | Classes đủ điều kiện đã load trong một trial run và chưa ở base archive | `-XX:ArchiveClassesAtExit=app.jsa`; sử dụng `-XX:SharedArchiveFile=app.jsa` |
| **AOT cache (JDK 24+)** | Container cho các artifacts AOT, có thể gồm class data và tối ưu khác | `-XX:AOTCache`, `-XX:AOTMode`, bản JDK cụ thể |

Từ JDK 12, Oracle JDK thường đóng gói default CDS archive sẵn. Trên một số distribution khác có thể có khác biệt. Nhiều JDK 17/21 có mặc định CDS-enabled khi archive khả dụng; vì vậy **không thể coi “chạy Java bình thường” là nhóm không CDS**.

### `-Xshare` và ý nghĩa đo lường

| Tùy chọn | Ý nghĩa | Vai trò thí nghiệm |
| --- | --- | --- |
| `-Xshare:off` | Không dùng shared class archive | Baseline OFF |
| `-Xshare:auto` | Sử dụng CDS khi có thể; fallback khi không thể | Phù hợp production; phải xác minh nếu dùng làm ON |
| `-Xshare:on` | Yêu cầu dùng CDS; lỗi startup nếu không thể | Forced-ON để test, không khuyến nghị production |
| `-Xshare:dump` | Tạo static archive trong workflow phù hợp | Setup, **không phải thời gian ứng dụng khởi động** |

Oracle tài liệu cảnh báo `-Xshare:on` chỉ nên dùng để thử nghiệm. Nếu JDK đổi, archive không tương thích hoặc flags không cho phép sharing, forced-on có thể khiến process không khởi động. Đừng triển khai production service theo chiến lược “archive không hợp lệ thì chết” chỉ vì benchmark dùng `-Xshare:on`.

## 3. Xác định đại lượng thực sự cần đo

Một đường khởi động:

~~~text
t0  OS nhận lệnh khởi chạy Java process
    |
    +-- tạo process + runtime bootstrap
    +-- class loading/linking/initialization cần thiết
    |
t1  bắt đầu vào Java main()
    |
    +-- business/framework setup
    |
t2  dịch vụ sẵn sàng phục vụ requests (nếu là server)
    |
t3  process kết thúc (nếu là CLI)
~~~

Có ít nhất ba đại lượng khác nhau:

- **Process-to-exit elapsed** = t3 − t0, đo bằng timer ngoài process. Phù hợp CLI ngắn như ví dụ dưới.
- **Time-to-main** = t1 − t0. Không đo được bằng cách đặt `System.nanoTime()` ngay dòng đầu `main` vì phần startup trước `main` đã xong.
- **Time-to-readiness** = t2 − t0. Với web service, cần đo tới health endpoint/signal ready thực sự, không coi dòng log “Started” là chắc chắn nhận được traffic.

CDS thường tác động **class-related startup work**, nhưng không tự rút ngắn thời gian DB migrations, remote config fetch, network DNS/TLS, socket binding hoặc business initialization khác. Khi app chạy nhiều phút hoặc hàng giờ, startup savings vẫn có giá trị ở restart/cold start, nhưng không tự thành throughput gain tương ứng.

## 4. Thiết kế thí nghiệm công bằng

Trước khi đo:
- **Cố định binary:** chính một path đến `java` và một `java -version`. Không dùng JDK 17 ở OFF và JDK 21 ở ON.
- **Cố định artifact:** một `cds-demo.jar`, không recompile, regenerate hoặc thay JAR bytes sau khi tạo `app.jsa`.
- **Cố định JVM config:** heap flags, GC, locale/timezone, module path và mọi startup args khác ngoài CDS flags được nghiên cứu.
- **Cố định machine:** CPU quota/cpuset, RAM, OS, filesystem, disk cache policy, background services/antivirus và nhiệt độ có thể ảnh hưởng.
- **Cố định behavior:** output, inputs, exit code và classpath; không để mode khác đọc data/network khác.
- **Đo nhiều lần:** warmup từng mode để giảm ảnh hưởng cache, rồi đảo thứ tự để tránh OFF đo lúc máy lạnh còn ON đo sau khi cache nóng.
- **Xác minh trước measurement:** CDS thực sự được bật ở ON, AppCDS dynamic archive được load ở APP.
- **Tách setup costs:** tạo JAR, tạo archive, compilation và training **không tính trong startup measurements**.
- **Giữ measurement method giống nhau:** nếu đo PowerShell shell overhead, overhead đó tồn tại ở mọi mode.

Một biến thay đổi có chủ đích cho phép bạn quy chênh lệch quan sát với CDS **trong phạm vi thí nghiệm**. Nhưng để kết luận thực tế cần đủ runs và mức chênh lớn hơn nhiễu.

## 5. Ứng dụng Java 17+ dùng cho cả OFF/ON/APP

Tạo file **`CdsStartupDemo.java`** trong một thư mục trống dành riêng cho thí nghiệm.

~~~java
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

public class CdsStartupDemo {
    static final class Item {
        final String sku;
        final int quantity;

        Item(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
    }

    public static void main(String[] args) {
        List<Item> items = new ArrayList<>();
        items.add(new Item("A", 2));
        items.add(new Item("BB", 3));
        items.add(new Item("CCC", 4));

        int checksum = items.stream()
                .mapToInt(i -> i.sku.length() * i.quantity)
                .sum();

        String first = items.stream()
                .map(i -> i.sku)
                .min(Comparator.naturalOrder())
                .orElse("none");

        String date = LocalDate.of(2024, 2, 29)
                .plusDays(1)
                .toString();

        String encoded = Base64.getEncoder()
                .encodeToString(new byte[] {1, 2, 3});

        System.out.println("checksum=" + checksum);
        System.out.println("first=" + first);
        System.out.println("date=" + date);
        System.out.println("encoded=" + encoded);
    }
}
~~~

**Stdout nghiệp vụ chính xác ở tất cả ba modes:**

~~~text
checksum=20
first=A
date=2024-03-01
encoded=AQID
~~~

Giải thích:
- `A` dài 1 và quantity 2 → 2.
- `BB` dài 2 và quantity 3 → 6.
- `CCC` dài 3 và quantity 4 → 12.
- Checksum = 2 + 6 + 12 = **20**.
- Chuỗi nhỏ nhất theo natural ordering là `A`.
- 29/02/2024 cộng một ngày ra 01/03/2024.
- Base64 bytes [1,2,3] là `AQID`.

Chương trình tạo và sử dụng classes thuộc core JDK lẫn một nested application class. Điều này có ý nghĩa minh họa, nhưng **không bảo đảm lợi ích AppCDS lớn** vì ứng dụng khá nhỏ. Với một framework-heavy app, archive coverage và class-loading workload có thể khác đáng kể.

Không gọi `System.nanoTime()` để in “startupMs” trong chương trình này: timer đặt trong `main` không quan sát bootstrap work quan trọng mà CDS có thể tiết kiệm.

## 6. Compile và đóng JAR đúng một lần

Dùng một JDK 17+ có `javac` và `jar`, từ thư mục chứa source:

~~~bash
java -version
javac --release 17 -d classes CdsStartupDemo.java
jar --create --file cds-demo.jar --main-class CdsStartupDemo -C classes .
java -jar cds-demo.jar
~~~

Kết quả dòng cuối là **bốn dòng chuẩn** ở mục 5. Các công cụ `javac` và `jar` là thành phần JDK, không phải phải cài Maven/Gradle để chạy bài này.

**Quan trọng:**
- Archive tạo từ artifacts cụ thể; **đừng build lại JAR sau khi tạo archive**.
- Câu lệnh `-C classes .` đóng tất cả compiled class files (bao gồm `CdsStartupDemo$Item.class`) vào JAR.
- Dùng một folder sạch, tránh `classes` có files không liên quan.
- Giữ path JAR ổn định, thống nhất với các flags khi tạo AppCDS.
- Tạo archive bằng cùng JDK binary/build mà bạn dùng lúc đo.
- Đặt working directory ổn định để relative file names có cùng nghĩa.

Đừng lấy kết quả thời gian từ lần biên dịch `javac`, đóng `jar` hoặc tạo `app.jsa` làm startup sample.

## 7. Nhánh OFF — không dùng CDS

Chạy:

~~~bash
java -Xshare:off -jar cds-demo.jar
~~~

Kỳ vọng business output giống mục 5. Với OFF, HotSpot không dùng shared class archive nhưng vẫn có các tối ưu JVM khác như interpreter, JIT, GC và các JVM subsystems. Không đánh đồng tắt CDS với tắt mọi tối ưu.

Kiểm tra riêng:

~~~bash
java -Xshare:off -Xlog:cds=info -jar cds-demo.jar
~~~

Log diagnostics phụ thuộc bản JDK; có thể thấy lý do CDS không được dùng. **Đừng dùng những logs verbose chỉ ở một nhóm trong phép đo chính**: log I/O làm sai so sánh.

## 8. Nhánh ON — default CDS archive

Chạy:

~~~bash
java -Xshare:on -jar cds-demo.jar
~~~

Với JDK có default shared archive phù hợp, ứng dụng phải in cùng output và exit code 0. Nếu JVM báo archive unavailable/incompatible và không khởi động, nhóm ON **FAILED**, không được ghi là một startup sample 0 ms.

Để xác minh:

~~~bash
java -Xshare:on -Xlog:cds=info -jar cds-demo.jar
java -Xshare:on -Xlog:class+load=info -jar cds-demo.jar
~~~

Tìm log map/load default archive và những class sources kiểu `shared objects file`. Tên tag/format/line order tùy HotSpot build. **Không viết một log exact expected stdout** bởi JVM load classes theo thứ tự/điều kiện khác nhau.

### Vì sao không chỉ dùng `-Xshare:auto`?

Vì `auto` cho phép fallback khi không thể sử dụng CDS, nên “chạy thành công” không bảo đảm CDS thật sự được bật. `on` dùng như **test assertion**: nếu archive unusable, JVM báo lỗi để bạn phát hiện ngay. Production thường dùng `auto` để linh hoạt hơn.

## 9. Nhánh APP — tự tạo dynamic AppCDS archive

Trên HotSpot Java 17/21 hỗ trợ dynamic archive, khi đã có JAR **không thay đổi**:

~~~bash
java -XX:ArchiveClassesAtExit=app.jsa -jar cds-demo.jar
~~~

Chương trình chạy một lần để HotSpot thu thông tin classes đủ điều kiện và ghi **dynamic archive `app.jsa` khi JVM thoát**. Đây là **training/archive generation**, không tính vào benchmark của nhóm APP.

Kiểm tra file tồn tại và training command kết thúc thành công. Nếu tạo archive bị lỗi, không đo APP.

Tiếp theo:

~~~bash
java -Xshare:on -XX:SharedArchiveFile=app.jsa -jar cds-demo.jar
~~~

Thông thường dynamic archive biết base archive mà nó phụ thuộc, nên chỉ định `app.jsa` là đủ trên workflow tài liệu JDK 17/21. Cách dùng base + dynamic tùy platform/JDK: trên Windows, khi chỉ định hai archive paths trong một option, dấu phân tách là dấu chấm phẩy chứ không phải dấu hai chấm. **Bài thí nghiệm sử dụng một đường dẫn dynamic archive để tránh phụ thuộc delimiter hai paths**.

Kiểm chứng APP:

~~~bash
java -Xshare:on -XX:SharedArchiveFile=app.jsa -Xlog:cds=info -jar cds-demo.jar
java -Xshare:on -XX:SharedArchiveFile=app.jsa -Xlog:class+load=info -jar cds-demo.jar
~~~

Xem log của classes như `CdsStartupDemo` hoặc `CdsStartupDemo$Item`; nếu eligible và archived, các dòng load có thể cho biết chúng đến từ shared objects file. Một số classes có thể vẫn load từ JAR, không có lời hứa archive mọi class.

**Đừng dùng sự tồn tại file `app.jsa` làm bằng chứng APP đã có tác dụng:** phải kết hợp startup success, log archive mapping và class source khi thích hợp.

### Những khác biệt cần ghi

| OFF | ON | APP |
| --- | --- | --- |
| No CDS archive | Default JDK archive | Default base + dynamic application archive |
| Baseline không sharing | Reuse JDK classes metadata | Có thể thêm application/library classes |
| `-Xshare:off` | `-Xshare:on` | `-Xshare:on -XX:SharedArchiveFile=app.jsa` |

Với ví dụ CLI rất nhỏ, ON→APP có thể cải thiện ít hơn OFF→ON, nhưng đây **không phải kết quả cố định**.

## 10. Đo đúng mốc: timer bên ngoài JVM

Lược đồ:

~~~text
External Stopwatch.Start()
        |
        +-- create Java process
        +-- native VM bootstrap
        +-- map CDS archive / load classes
        +-- Java main executes
        +-- stdout flushed / process exit
        |
External Stopwatch.Stop()
        |
        v
Elapsed.TotalMilliseconds
~~~

Nếu bạn chạy:

~~~java
long t0 = System.nanoTime();
work();
System.out.println(System.nanoTime() - t0);
~~~

đó chỉ là đoạn ví dụ chưa có định nghĩa `work()`. Timer bắt đầu **sau khi JVM đã vào main**, nên kết quả **không đủ** để đánh giá savings của CDS trong bootstrap. Dùng `Stopwatch` của PowerShell, `time` của shell hoặc process-measuring tool tương tự.

Đối với **server**, thay mục tiêu từ process-to-exit sang **process-to-ready** (health check thực sự healthy, không chỉ một dòng log). Cần xác định cả readiness dependencies và HTTP probe polling overhead.

## 11. PowerShell 5.1+: script benchmark hoàn chỉnh có warmup, nhiều lần đo

Trên Windows, PowerShell có sẵn `[System.Diagnostics.Stopwatch]`. Lưu script sau thành **`measure-cds.ps1`** trong folder chứa `cds-demo.jar` và `app.jsa`. Nó kiểm tra output, fail-fast khi JVM exit error, đo mỗi mode **20 lần**, xen kẽ thứ tự các modes, xuất CSV và tính median, p95, min/max.

~~~powershell
$ErrorActionPreference = "Stop"

$javaExe = (Get-Command java).Source
$jarPath = Join-Path (Get-Location) "cds-demo.jar"
$archivePath = Join-Path (Get-Location) "app.jsa"

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Missing cds-demo.jar"
}
if (-not (Test-Path -LiteralPath $archivePath)) {
    throw "Missing app.jsa; create archive before measuring"
}

$cases = @{
    off = @("-Xshare:off")
    on  = @("-Xshare:on")
    app = @("-Xshare:on", "-XX:SharedArchiveFile=$archivePath")
}

$expected = @(
    "checksum=20"
    "first=A"
    "date=2024-03-01"
    "encoded=AQID"
) -join [char]10

function Invoke-Mode([string]$name) {
    [string[]]$options = $cases[$name]

    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $outLines = & $javaExe @options -jar $jarPath 2>&1
    $exit = $LASTEXITCODE
    $timer.Stop()

    $actual = ($outLines |
        ForEach-Object { $_.ToString() }) -join [char]10

    if ($exit -ne 0) {
        throw "$name JVM failed: exit=$exit output=$actual"
    }
    if ($actual.Trim() -ne $expected) {
        throw "$name business stdout mismatch: $actual"
    }
    return [double]$timer.Elapsed.TotalMilliseconds
}

# Warmup: 5 process launches per mode. Exclude them from CSV.
for ($warm = 0; $warm -lt 5; $warm++) {
    foreach ($name in @("off", "on", "app")) {
        $null = Invoke-Mode $name
    }
}

$rows = @()
for ($round = 1; $round -le 20; $round++) {
    if (($round % 2) -eq 1) {
        $order = @("off", "on", "app")
    } else {
        $order = @("app", "on", "off")
    }

    foreach ($name in $order) {
        $ms = Invoke-Mode $name
        $rows += [pscustomobject]@{
            round = $round
            mode = $name
            elapsed_ms = $ms
        }
    }
}

$rows | Export-Csv -Path "cds-results.csv" -NoTypeInformation -Encoding UTF8

$summary = foreach ($group in ($rows | Group-Object mode)) {
    $times = @($group.Group |
        Sort-Object elapsed_ms |
        ForEach-Object { [double]$_.elapsed_ms })
    $n = $times.Count
    $mid = [int][Math]::Floor($n / 2)

    if (($n % 2) -eq 0) {
        $median = ($times[$mid - 1] + $times[$mid]) / 2.0
    } else {
        $median = $times[$mid]
    }
    $index95 = [int]([Math]::Ceiling(0.95 * $n) - 1)

    [pscustomobject]@{
        mode = $group.Name
        count = $n
        median_ms = [Math]::Round($median, 3)
        p95_ms = [Math]::Round($times[$index95], 3)
        min_ms = [Math]::Round($times[0], 3)
        max_ms = [Math]::Round($times[$n - 1], 3)
    }
}

$summary | Sort-Object mode | Format-Table -AutoSize
Write-Host "Raw samples: cds-results.csv"
~~~

Chạy từ folder thí nghiệm:

~~~powershell
powershell -NoProfile -File .\measure-cds.ps1
~~~

Nếu script execution policy bị hạn chế, tuân thủ chính sách của môi trường; không tự thay đổi security settings hệ thống chỉ để chạy benchmark. Trên PowerShell 7, thay bằng `pwsh -NoProfile -File ./measure-cds.ps1`.

**Vì sao output không có thời gian “dự kiến”?** Thời gian tính theo process launch trên máy thực, có thể khác vài chục đến hàng trăm milliseconds hoặc hơn, tùy CPU, OS cache, antivirus, VM build và tải nền. Script tự tính số thực, không bịa benchmark.

### Những gì script thực sự đo

- Process launch và JVM bootstrap.
- Java work, stdout/exit/shutdown.
- PowerShell command invocation và text capture overhead tương đương giữa modes.

Nó **không đo** JAR compilation/packaging hoặc archive training. Nó cũng không đo readiness của web server. Nếu ứng dụng dài hàng phút, process-to-exit không còn là KPI startup hữu ích; hãy thay stop condition bằng READY signal.

### Một lưu ý với error stream

Script dùng `2>&1` và yêu cầu output chỉ có bốn dòng business. Vì vậy các warning của JVM/launcher trên stderr có thể làm script fail. Đây là **cách phát hiện môi trường không sạch**, không phải nhược điểm: nếu mode ON phát sinh warning/error ngoài dự kiến, cần xử lý trước khi đo. Nếu production runtime luôn in một warning xác định, có thể lọc riêng trong script sau khi ghi nhận lý do, nhưng phải áp dụng nhất quán cho mọi mode.

## 12. Diễn giải bảng kết quả

Script xuất `cds-results.csv` chứa các cột:

~~~text
round,mode,elapsed_ms
~~~

Bảng dưới đây **CHỈ LÀ GIẢ ĐỊNH**, không phải một phép đo đã thực hiện:

| Chế độ | Median (ms, giả định) | p95 (ms, giả định) |
| --- | ---: | ---: |
| OFF | 112 | 136 |
| ON | 94 | 117 |
| APP | 87 | 109 |

Nếu máy bạn thật sự có các medians này:
- OFF→ON tiết kiệm **18 ms**, tức `(112−94)/112 × 100 ≈ 16,1%` thời gian.
- OFF→APP tiết kiệm **25 ms**, xấp xỉ **22,3%**.
- ON→APP tiết kiệm **7 ms**, xấp xỉ **7,4%** so với ON.
- Nếu xét `speedup = OFF / ON`, speedup là `112/94 ≈ 1,19×`. Điều này khác phép nói giảm thời gian 16,1% do mẫu số thay đổi.

Không dùng bảng giả định để khẳng định “Java CDS nhanh hơn đúng 16,1%” trên máy khác. Mỗi kết luận phải dựa vào **CSV của chính môi trường thử nghiệm**.

### Kết quả gần bằng nhau là bình thường

Nếu OFF median=61 ms, ON median=60,7 ms nhưng variation 5–8 ms giữa các lần, chênh 0,3 ms có thể nằm trong nhiễu. Kết luận đúng là **chưa có bằng chứng thuyết phục về lợi ích** trên workload và thiết lập hiện tại. Không vội tuyên bố CDS “hỏng” hoặc “vô ích”.

Nếu APP median cao hơn ON một chút:
- Kiểm tra app archive thực sự load các classes mong muốn.
- Kiểm tra jar path/bytes/JDK build compatibility.
- Tăng số runs và kiểm soát page cache/background load.
- Phân tích storage/VM work; archive bổ sung cũng có cost mmap/load riêng.

## 13. Tại sao phải warmup và đảo thứ tự?

Warmup ở thí nghiệm nhiều **process launches** có ý nghĩa chính là làm môi trường như **OS filesystem page cache, class/JAR/archive file pages và antivirus/process launcher behavior** bớt lạnh. Nó không làm **JIT profile của Java process #1 tự truyền sang Java process #2** trong CDS cổ điển của JDK 17/21.

Nếu chạy 20 lần OFF liên tiếp rồi 20 lần ON:
- OFF có thể bị đo ở giai đoạn archive/JAR chưa nằm trong OS page cache.
- ON có thể được hưởng page cache nóng do những lần OFF trước.
- CPU temperature/frequency và host load thay đổi theo thời gian.

Đảo OFF→ON→APP ở round lẻ và APP→ON→OFF ở round chẵn để giảm một phần drift. Đây chưa phải mô hình thống kê hoàn hảo; có thể randomize/block theo seed và tăng runs khi cần.

### Cold vs warm cache

- **Cold JVM:** mỗi sample là một process mới, VM state từ process cũ không được giữ nguyên.
- **Warm OS cache:** JAR/archive disk pages có thể còn trong RAM hệ điều hành.
- **Cold OS cache:** phải đọc thêm từ storage, có thể chậm hơn và ảnh hưởng so sánh.
- **Warm long-running service:** không còn là startup experiment.

Không tự ý drop OS caches trên máy shared/prod vì có thể làm gián đoạn ứng dụng khác. Nếu cần true cold-cache benchmark, thiết kế môi trường riêng có quyền kiểm soát.

## 14. Xác minh class source bằng -Xlog

Một lần benchmark đúng không chỉ có số milliseconds: cần chứng cứ rằng treatment đã được áp dụng.

Chạy ngoài vùng timing:

~~~bash
java -Xshare:on -Xlog:cds=info -jar cds-demo.jar
java -Xshare:on -Xlog:class+load=info -jar cds-demo.jar
java -Xshare:on -XX:SharedArchiveFile=app.jsa -Xlog:class+load=info -jar cds-demo.jar
~~~

Tìm các dòng cho JDK classes và `CdsStartupDemo`, có thể có source: `shared objects file`. Logs phụ thuộc JDK. **Có class load event không nói class chưa được archive**: JVM vẫn có thể có sự kiện logically load một class mà dữ liệu lấy từ shared archive.

Nếu default ON chỉ archive JDK classes, app classes vẫn có thể load từ JAR. Dynamic APP có thể archive một phần các classes chưa có trong base, nhưng không phải mọi class chắc chắn eligible. Mức cải thiện phụ thuộc classes thực sự được archive và công việc VM tiết kiệm được.

### Không được trộn logging overhead vào time delta

Verbose class loading logs có thể:
- Thêm I/O/stderr writes,
- Tăng text processing overhead khi PowerShell thu output,
- Gây biến thiên theo số lines.

Vì vậy xác minh class source **trong một bước riêng**, còn measurement OFF/ON/APP không bật logs verbose. Nếu muốn benchmark cả logging configuration, phải bật **cùng kiểu logging ở cả ba modes**.

## 15. Khi AppCDS archive không dùng được

| Triệu chứng | Nguyên nhân khả dĩ | Hành động |
| --- | --- | --- |
| `-Xshare:on` error ngay startup | Archive thiếu, incompatible flags/path/JDK | Kiểm tra logs, không ghi sample |
| Không có `app.jsa` | Trial run fail, thiếu quyền ghi, base archive unavailable | Sửa trial command/setup |
| Vừa rebuild JAR là APP fail | Classpath/JAR contents/timestamp khác archived runtime assumptions | Tạo lại AppCDS sau build cuối |
| Archive tạo trên Java 21 nhưng chạy Java 17 | Không cùng runtime build | Tạo lại bằng JDK benchmark |
| ON chỉ chạy với `auto` chứ không chạy với `on` | Auto có thể silently fallback | Xác minh logs trước, không dán nhãn ON nếu archive không dùng |
| Class `CdsStartupDemo` không ghi source shared | Không eligible hoặc không được đưa vào archive | Kiểm tra dynamic training path/loader/class rules |
| APP không nhanh hơn ON | Ít app classes hoặc chi phí nằm ngoài class load | Tăng runs, xem logs và startup breakdown |
| ON có vài sample chậm đột biến | OS scheduling, antivirus, cache, CPU throttling | Dùng median, quan sát host metrics, repeat |
| JVM flag gây CDS disabled | JVM options/module/classpath restriction | Đọc Java command docs cùng release |
| Một archive dùng trên máy/JDK khác | Archive compatibility phụ thuộc build/OS/architecture | Build lại artifact và xác minh trên target |

Java 17/21 docs mô tả một số restrictions:
- Classpath/module path chứa **non-empty directories** có thể không dùng được trong những custom archive workflows.
- Thứ tự/đường dẫn classpath khi tạo archive có compatibility constraints với runtime.
- Nếu JAR files thay đổi sau lúc dump, archive có thể invalid.
- Các flags như `--patch-module`, `--upgrade-module-path` hoặc `--limit-modules` có thể khiến CDS bị vô hiệu hóa theo tài liệu release.
- Một số custom class loader paths/classes không eligible cho mọi loại archive.

Đó là lý do ví dụ dùng một **JAR và cùng command line** thay vì chạy .class files từ thư mục non-empty rồi phỏng đoán archive có tác dụng.

## 16. CDS, RAM, RSS và nhiều JVM processes

CDS có mục tiêu phụ là giảm footprint khi những JVMs dùng cùng archive:
- Mapping từ cùng file có thể dùng chung một số physical pages.
- **RSS** một process thường tính cả mapped shared pages; cộng RSS nhiều processes có thể **đếm trùng** pages.
- Trên Linux, **PSS** chia chi phí shared pages giữa các processes và phù hợp hơn để so tổng physical footprint trong một số trường hợp.
- Disk size của `app.jsa` không thể đồng nhất với RAM saved.
- Một process đơn lẻ có thể không cho thấy lợi ích sharing rõ như nhiều JVMs chạy đồng thời.

Nếu muốn đo footprint, xây **thí nghiệm riêng** chạy N JVMs đồng thời ở cùng trạng thái, giữ heap/GC options như nhau, so PSS/private memory và host aggregate; không dùng median startup time để suy memory savings.

## 17. CDS không phải AOT compilation thông thường

Một số người nghe chữ “archive” và tưởng JVM đã biên dịch mọi method thành machine code. Không đúng đối với CDS cổ điển:
- CDS chủ yếu chia sẻ **class-related data/metadata** đã xử lý.
- HotSpot vẫn có interpreter/JIT, method profiling và compilation nếu workload đủ nóng.
- Dynamic AppCDS không phải heap dump lưu toàn bộ application state.
- CDS không làm application ignore Java class initialization semantics.
- CDS không tự biến JVM process thành native binary.

### JDK 24+ có AOT cache với phạm vi khác

JDK 24 có **Ahead-of-Time Class Loading & Linking** theo JEP 483 và một loại **AOT cache**, với các flags version-specific như:

~~~text
-XX:AOTMode
-XX:AOTConfiguration
-XX:AOTCache
-XX:AOTCacheOutput
-Xlog:aot
~~~

JDK 25 docs mô tả cache tương thích với combination của application, JDK release và OS/CPU architecture, cùng các phases như training, assembly, production. AOT cache có thể chứa thêm artifacts ngoài class data truyền thống. **Không lấy một JDK 25 AOT-cache result rồi gọi là “lợi ích của -Xshare:on trên Java 17”**.

Cách nghiên cứu tốt:
1. Đo OFF vs default ON trên chính một JDK.
2. Đo APP nếu dynamic AppCDS workflow phù hợp.
3. Nếu chuyển sang AOT cache mới, thêm **mode AOT riêng** và làm theo tài liệu của bản JDK đó.
4. Nếu muốn so JDK 17 và 25, làm thí nghiệm OFF/ON **trong từng JDK riêng**, sau đó mới phân tích hiệu ứng đổi phiên bản.

## 18. Nếu muốn mở rộng sang Spring Boot, Quarkus hoặc một service thật

Ứng dụng Java body ngắn lý tưởng để bắt đầu vì ít variables; với production service, cần đo:
- Command line JVM thực, classpath/boot loader thực.
- Class load coverage trong application/framework.
- Time to bind port và health endpoint thành READY.
- DB migrations, schema validation, remote config, secrets fetch.
- Application cold-start memory budget và CPU quota.
- Container image pull/startup overhead **tách riêng** khỏi JVM startup.
- Resource cleanup khi shutdown, PID reuse, startup errors.
- Versioned app artifacts và archive build pipeline.

Một service có thể log “started in 2 seconds” nhưng readiness chỉ thành công sau 5 giây vì connections. Nếu mục tiêu autoscaling cold-start là **time-to-ready**, phải dùng chính event đó. Giảm 50 ms class loading là lợi ích thật nhưng không khắc phục 3 giây DB wait.

**Đừng coi sample Java CLI là benchmark của framework**. Nó chỉ minh họa đúng phương pháp so sánh và cú pháp flags.

## 19. Tổng hợp một bảng dữ liệu giả định

Đây **không phải dữ liệu đo** mà là ví dụ để luyện cách ra quyết định:

| Chỉ số | OFF | ON | APP |
| --- | ---: | ---: | ---: |
| Successful launches | 20/20 | 20/20 | 20/20 |
| Median startup-to-exit | 120 ms | 101 ms | 94 ms |
| p95 startup-to-exit | 140 ms | 118 ms | 112 ms |
| stdout hash/equality | PASS | PASS | PASS |
| archive verified | OFF confirmed | default mapped | dynamic mapped |

Trong bảng giả định:
- Default CDS tiết kiệm 19 ms (15,8% so OFF).
- Dynamic APP tiết kiệm thêm 7 ms so ON.
- Tổng OFF→APP tiết kiệm 26 ms (21,7% so OFF).
- APP có thể là một lựa chọn tốt khi ứng dụng được launch rất nhiều lần và CI archive creation/compatibility costs thấp.

Nếu ngược lại median OFF=101, ON=100,9, APP=101,2 với run-to-run variation 6 ms, **không có căn cứ đủ mạnh** để tuyên bố APP tốt hơn hoặc tệ hơn. Cần báo noise và repeat.

### Startup reduction không đồng nghĩa chi phí CPU giảm cùng tỷ lệ

Một process wall-clock nhanh hơn 20% không có nghĩa billed CPU seconds của cloud job tự giảm 20%. Có process launching overhead, I/O wait, concurrent host usage, memory mapping và provisioning. Muốn đánh giá tiết kiệm chi phí, đo process CPU time, wall-clock cold-start distribution và billing model riêng.

## 20. Checklist báo cáo chuẩn

Báo cáo thực nghiệm nên ghi:

~~~text
Experiment: CDS startup on same application

JDK:
  vendor/build/version/architecture
OS/container:
  CPU quota, memory limit, filesystem
Application:
  SHA-256 of cds-demo.jar, classpath, input
Archive:
  default CDS present? app.jsa created? same JDK?
Cases:
  OFF: -Xshare:off
  ON:  -Xshare:on
  APP: -Xshare:on -XX:SharedArchiveFile=app.jsa
Validation:
  exit code, stdout identical, -Xlog:cds/class+load evidence
Method:
  external Stopwatch, 5 warmups, 20 samples/mode
  alternating order, same environment
Raw:
  cds-results.csv
Summary:
  median/p95/min/max and variation
Decision:
  improvement with caveats, compatibility/build cost
~~~

Không cần có đủ ba mode mới học được CDS; OFF/ON là thí nghiệm tối thiểu. APP mở rộng hữu ích khi bạn muốn lưu dữ liệu class ứng dụng. Nhưng khi một case fail, **không silently drop nó khỏi kết quả** rồi báo “benchmark PASS 20/20”.

## 21. Ngộ nhận phổ biến

| Ngộ nhận | Điều đúng |
| --- | --- |
| “Chạy `java -jar` tức CDS chắc chắn OFF.” | Nhiều JDK dùng default CDS khi có thể. |
| “`-Xshare:auto` nghĩa archive chắc chắn loaded.” | Auto có thể fallback. |
| “Dùng `-Xshare:on` production luôn tốt.” | Forced ON có thể fail startup khi archive incompatible; testing only. |
| “CDS biến Java thành native executable.” | Shared archive không phải native-image. |
| “Có `app.jsa` tức JVM đang đọc nó.” | Cần verify logs, mode và path. |
| “CDS làm mọi class load count giảm.” | Số logical classes được sử dụng có thể giữ nguyên; thay nguồn/chi phí class loading. |
| “Tạo archive bằng JDK khác vẫn được.” | Archive thường gắn với runtime build và application inputs. |
| “Có thể đo OFF trên JDK 17, ON trên JDK 21.” | Chênh lệch trộn nhiều thay đổi ngoài CDS. |
| “CDS archive thay thế JAR.” | JAR/application dependencies vẫn cần trong workflow phù hợp. |
| “Thời gian trong main() là JVM startup time.” | Mất phần bootstrap trước main. |
| “Một run nhanh 80ms, chậm 90ms đủ kết luận.” | Cần repeated samples và kiểm soát noise. |
| “Tạo archive là một phần mỗi startup APP.” | Archive thường là setup/deployment artifact, không tính vào usage-phase median. |
| “Page cache không ảnh hưởng CDS.” | Archive/JAR file I/O chịu OS cache effects. |
| “Nếu ON không giảm median, CDS hỏng.” | Có thể chi phí class load nhỏ hoặc noise cao; xem logs. |
| “Nếu APP chậm hơn ON 0,1ms, APP dở.” | Chênh nhỏ hơn variance không đủ chứng cứ. |
| “CDS rút ngắn cả DB connection wait.” | CDS chủ yếu class-related work. |
| “Startup tốt hơn nghĩa steady-state CPU throughput luôn tăng.” | Đây là KPI khác. |
| “RSS thấp đi đúng bằng size `.jsa`.” | Shared mapping và OS accounting phức tạp hơn. |
| “Một app nhỏ đại diện cho Spring Boot startup.” | Framework/service load path khác. |
| “JDK24 AOT cache và JDK17 AppCDS y hệt.” | Phạm vi và workflow khác theo release. |

## 22. Câu hỏi tự kiểm tra kèm lời giải

**1. CDS là gì?**  
Class Data Sharing, cơ chế sử dụng shared archive cho một phần class-related VM data nhằm cải thiện startup và có thể memory sharing.

**2. Vì sao không lấy `java -jar` là OFF baseline?**  
Vì default CDS có thể đã được bật; cần chủ động `-Xshare:off`.

**3. Ưu điểm của `-Xshare:on` khi làm test?**  
Nếu archive không dùng được JVM fail, tránh đo một run tưởng ON nhưng thực tế fallback.

**4. Tại sao không dùng forced ON trong production?**  
Archive mismatch/options/JDK thay đổi có thể khiến application không khởi động; Oracle khuyến cáo testing only.

**5. Cách tạo dynamic AppCDS archive JDK17/21?**  
`java -XX:ArchiveClassesAtExit=app.jsa -jar cds-demo.jar`, sau đó kiểm tra file và logs.

**6. Cách chạy với archive đó?**  
`java -XX:SharedArchiveFile=app.jsa -jar cds-demo.jar`, có thể thêm `-Xshare:on` khi test.

**7. Nếu archive tạo trên Java 21 mà chạy bằng Java 17, điều gì sai?**  
Runtime compatibility không được bảo đảm; tạo archive bằng chính JDK benchmark.

**8. Nếu file JAR đổi sau khi tạo archive?**  
Archive có thể vô dụng/không tương thích; tạo lại từ artifact cuối cùng.

**9. Vì sao `System.nanoTime()` tại main không đủ?**  
Nó bắt đầu sau JVM native/bootstrap/class loading đã diễn ra một phần.

**10. Startup-to-exit có phải server time-to-ready?**  
Không. Server readiness phải đo tới khi thật sự nhận được requests.

**11. Tại sao dùng cùng business stdout?**  
Xác nhận application semantics tương đương giữa modes; nhưng điều này vẫn chưa chứng minh archive loaded.

**12. Bằng chứng archive loaded?**  
CDS initialization logs và class-load source logs trên chính JDK thực.

**13. Lợi ích của APP đo so với mode nào?**  
Cả ON (lợi ích thêm của app classes) và OFF (tổng lợi ích).

**14. Vì sao dùng median?**  
Giảm tác động của một số scheduling/outlier spikes so với trung bình, nhưng vẫn cần variance/số samples đủ.

**15. 20 samples có đủ để khẳng định p99 SLA?**  
Không. Với 20 samples, tail percentiles rất thiếu ổn định; chỉ là minh họa phương pháp.

**16. Nếu OFF median=100 và ON=80 thì reduction và speedup là bao nhiêu?**  
Reduction=20%; speedup=1,25×.

**17. Khi JDK vào 24+, còn vấn đề gì phải chú ý?**  
AOT cache/class-linking options mới có phạm vi rộng hơn; tách thí nghiệm riêng theo đúng docs release.

**18. Khi chạy nhiều JVMs, nên chỉ dùng tổng RSS để tính tiết kiệm RAM?**  
Không; RSS có thể double-count shared pages, cân nhắc PSS/private/summed physical metrics.

**19. Có nên bỏ bước warmup để đo cold OS cache?**  
Chỉ khi đó là một thiết kế cold-cache độc lập và có kiểm soát; không trộn cold/warm samples giữa modes.

**20. Nếu app load rất ít classes thì APP luôn nhanh hơn ON đáng kể?**  
Không. Số eligible archived classes và phần thời gian class-related nhỏ có thể khiến hiệu ứng không đáng kể.

## 23. Bản đồ tổng kết

~~~text
                  ONE JAR / ONE JDK
                        |
                   FIXED INPUTS
                        |
                DEFAULT CDS ARCHIVE
                        |
       +----------------+----------------+
       |                |                |
       v                v                v
      OFF               ON              APP
  -Xshare:off       -Xshare:on       dynamic app.jsa
       |                |                |
       +----------------+----------------+
                        |
                    VERIFY first
                stdout / exit / logs
                        |
                  WARMUP BY MODE
                        |
                 ALTERNATE ORDER
                        |
               EXTERNAL STOPWATCH
                        |
                  20+ RUNS EACH
                        |
                  CSV raw samples
                        |
                   median / p95
                        |
               OFF vs ON vs APP
                        |
                 CHECK VARIANCE
                        |
             REPORT ONLY MEASURED GAINS
                        |
               OPTIONAL: AOT CACHE
               as a separate experiment
~~~

**Kết luận:** Phép so sánh CDS đúng không phải là bật một flag rồi quan sát ứng dụng chạy. Phải **giữ nguyên một JAR và một JDK**, dùng `-Xshare:off` cho baseline, `-Xshare:on` cho forced default CDS, và tùy chọn `SharedArchiveFile` cho dynamic AppCDS; sau đó kiểm tra archive thực sự được dùng, đo **wall-clock bên ngoài JVM**, warmup/xen kẽ nhiều runs, đối chiếu output và báo median/p95 có ghi rõ biến thiên. Hiệu quả phụ thuộc workload/môi trường; mọi con số minh họa đều không thay thế phép đo thật.

## Nguồn tài liệu chính thức

- [Oracle JDK 17 — The java Command: Application Class Data Sharing, archives, restrictions](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).
- [Oracle JDK 21 — The java Command: CDS/AppCDS](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).
- [Oracle JDK 17 — Class Data Sharing](https://docs.oracle.com/en/java/javase/17/vm/class-data-sharing.html).
- [Oracle JDK 25 — Class Data Sharing](https://docs.oracle.com/en/java/javase/25/vm/class-data-sharing.html).
- [Dev.java — CDS and AppCDS in HotSpot](https://dev.java/learn/jvm/cps-appcds/).
- [Dev.java — Java launcher, Dynamic CDS archive workflow](https://dev.java/learn/jvm/tools/core/java/).
- [Oracle JDK 25 — The java Command, Ahead-of-Time Cache](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html).
- [OpenJDK JEP 310 — Application Class-Data Sharing](https://openjdk.org/jeps/310).
- [OpenJDK JEP 341 — Default CDS Archives](https://openjdk.org/jeps/341).
- [OpenJDK JEP 350 — Dynamic CDS Archives](https://openjdk.org/jeps/350).
- [OpenJDK JEP 483 — Ahead-of-Time Class Loading & Linking (JDK 24)](https://openjdk.org/jeps/483).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-experiments-012">Ghi báo cáo kết nối triệu chứng → giả thuyết → bằng chứng → kết luận → giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi báo cáo kết nối triệu chứng → giả thuyết → bằng chứng → kết luận → giới hạn

Một **báo cáo thực nghiệm JVM** tốt không phải tập hợp ảnh chụp GC log, biểu đồ CPU và các câu khẳng định “Java chậm vì GC”. Báo cáo cần trình bày **chuỗi lập luận có thể kiểm tra và bác bỏ**:

```text
TRIỆU CHỨNG
    │ Vấn đề được quan sát trong bối cảnh nào?
    ▼
GIẢ THUYẾT
    │ Cơ chế nào có thể giải thích? Dự đoán nào có thể kiểm chứng?
    ▼
BẰNG CHỨNG
    │ Log/metric/profiler/đối chứng nào hỗ trợ hoặc phản bác?
    ▼
KẾT LUẬN
    │ Kết quả thực sự cho phép nói điều gì, với độ tin cậy bao nhiêu?
    ▼
GIỚI HẠN
    │ Chưa loại trừ được yếu tố nào? Cần kiểm tra tiếp gì?
    └──────────────────────────────► Cập nhật giả thuyết
```

Đích cuối là để đồng nghiệp **hiểu vấn đề, tái hiện được phép đo, đánh giá được sức mạnh của bằng chứng và biết giới hạn của kết luận**. Người đọc phải phân biệt được **observation (điều đo được)**, **inference (điều suy ra)**, **hypothesis (điều muốn thử)** và **action (thay đổi khuyến nghị)**.

Bài này áp dụng cho thí nghiệm **Java 17+**, đặc biệt khi xem GC, heap, JIT, locks/threads, allocation, native memory và latency. Cú pháp `jcmd`, `-Xlog`, Native Memory Tracking và một số events là đặc trưng **HotSpot**; không tự áp toàn bộ cho OpenJ9 hoặc Native Image mà chưa xác minh runtime.

## 1. Vì sao báo cáo cần cả năm mắt xích?

Một dòng log, một mẫu stack hoặc một con số trung bình **không phải nguyên nhân**. Hãy so sánh:

**Thiếu chặt chẽ:**

> “Ứng dụng chậm; heap sử dụng 70%; chắc chắn memory leak. Tăng heap và gọi `System.gc()`.”

**Có khả năng kiểm chứng:**

> “Trong 30 phút sau khi bật tính năng xuất dữ liệu, p99 latency tăng từ 180 ms lên 680 ms. Đồng thời old-generation occupancy sau các GC hoàn thành tăng đều qua sáu chu kỳ, từ 900 MiB lên 1.8 GiB. Giả thuyết H1: một collection được giữ bởi static cache khiến live set tăng và kéo dài GC pauses. Heap dump trong môi trường staging cho thấy cache đang giữ các entry của những request đã kết thúc. Khi giới hạn cache và chạy lại cùng workload, after-GC live set ổn định. Bằng chứng **hỗ trợ** H1 ở staging; chưa chứng minh production chỉ có một nguyên nhân, vì traffic mix và cấu hình heap còn khác.”

Những số liệu trong ví dụ trên là **dữ liệu giả định để minh họa cách viết báo cáo**, không phải kết quả đã đo trên một hệ thống có thật.

Một kết luận hữu ích cần trả lời đồng thời:

- **Vấn đề là gì và tác động ra sao?**
- **Cơ chế nào được đề xuất?**
- **Bằng chứng nào có thể kiểm tra lại?**
- **Có giả thuyết cạnh tranh nào không?**
- **Thay đổi nào thực sự được thử, trước/sau có tương đồng?**
- **Điều gì vẫn chưa được biết hoặc chưa thể khẳng định?**

## 2. Bắt đầu bằng định nghĩa triệu chứng có thể đo

Một câu “JVM chạy chậm” quá rộng. Hãy diễn đạt bằng **metric + thời gian + phạm vi + mức độ ảnh hưởng + đối tượng so sánh**.

| Triệu chứng mơ hồ | Phát biểu hữu ích hơn |
| --- | --- |
| “CPU cao” | CPU process cao hơn baseline trong 15 phút khi request rate tương tự |
| “GC nhiều” | Trong cửa sổ 10 phút, GC pause total tăng và số pauses trên 200 ms tăng |
| “Có memory leak” | Live heap sau các collection hoàn thành tăng qua nhiều giai đoạn tải tương đương |
| “Thread bị treo” | Có requests vượt timeout; nhiều thread stacks được ghi nhận chờ một lock |
| “JIT tối ưu tệ” | Sau warm-up cùng workload, một method mất inlining và CPU samples tăng tại callsite đó |
| “RSS tăng” | Process RSS tăng dù heap used/committed gần ổn định; cần tách native memory |
| “Latency tăng khi deploy” | p99 của endpoint A tăng trong 30 phút sau release; p50 gần ổn định |

### Ghi đủ bối cảnh

Một symptom record nên có:

1. **Khoảng thời gian:** ISO 8601 với timezone rõ, hoặc UTC nhất quán.
2. **Hệ thống và workload:** service, endpoint, batch job, transaction shape, traffic volume, concurrency.
3. **Kết quả định lượng:** latency p50/p95/p99, throughput, error rate, CPU, GC, memory.
4. **Baseline:** giai đoạn trước, một nhóm đối chứng, hoặc SLO đã định nghĩa.
5. **Ảnh hưởng vận hành:** số requests vượt SLO, timeout, retry, OOME, instance restart.
6. **Phạm vi:** một instance, một availability zone, một node pool hay cả cluster.
7. **Sự kiện đồng thời:** deploy, scaling, config, traffic, upstream service, node move.

**Ví dụ biểu đạt:** “Ở service order-api, endpoint `GET /orders` có p99 = 620 ms trong 14:00–14:30 UTC, so với 210 ms ở cửa sổ cùng tải trước release; dữ liệu lấy từ server-side request histograms, mỗi cửa sổ có trên 20.000 requests.” Đây là **ví dụ cách trình bày**, không phải một bộ số liệu thật.

### Đừng trộn đơn vị

- `ms` khác `µs`, `ns`.
- `MB` theo thập phân khác `MiB` theo cơ số 2.
- Heap **used**, **committed**, **max** và OS **RSS** không cùng đại lượng.
- CPU `100%` có thể có nghĩa **một core** hay **toàn máy** tùy nguồn; container quota có thể thay mẫu số.
- p99 của các requests **không** tính bằng trung bình của p99 từ từng instance nếu không có phân bố/samples phù hợp.

## 3. Giả thuyết phải có cơ chế, dự đoán và cách bác bỏ

Một giả thuyết tốt không chỉ đặt tên thành phần JVM.

**Không tốt:** “GC có vấn đề.”  
**Tốt hơn:** “Sau release, nhiều `byte[]` còn được giữ bởi cache; live heap tăng, dẫn tới old/full GC thường xuyên hơn và request p99 tăng.”

Hãy gắn ba thành phần:

| Thành phần | Nội dung nên ghi |
| --- | --- |
| **Cơ chế** | Đường ảnh hưởng kỹ thuật được dự đoán: ví dụ cache giữ references → old occupancy tăng → long GC pauses |
| **Dự đoán kiểm chứng** | Nếu đúng, sẽ thấy retained paths từ cache, after-GC live set tăng, pauses tương ứng với request spikes |
| **Bằng chứng phản bác** | Nếu heap dump không cho paths như dự đoán, live set ổn định hoặc latency spikes không trùng GC pauses, H1 yếu đi |

### Không biến giả thuyết thành kết luận từ đầu

Một báo cáo nên dùng ngôn ngữ:

- “**Giả thuyết H1:** ...”
- “**Nếu H1 đúng:** ...”
- “**Đã quan sát:** ...”
- “**Không quan sát được:** ...”
- “**Kết quả hỗ trợ/bác bỏ/chưa phân biệt được H1:** ...”

Tránh “vì chắc chắn là GC nên chúng tôi bật GC log để chứng minh GC”; cách đó dễ dẫn đến **confirmation bias**: chỉ tìm bằng chứng phù hợp với ý nghĩ ban đầu.

### Luôn có ít nhất một giả thuyết cạnh tranh hợp lý

Ví dụ latency p99 tăng:

- H1: GC pauses kéo dài.
- H2: lock contention cao.
- H3: database/downstream latency tăng.
- H4: CPU throttling hoặc noisy neighbor.
- H5: JIT warm-up/recompilation sau rolling restart.

Báo cáo không cần thử mọi nguyên nhân có thể tưởng tượng, nhưng nên nêu những giả thuyết **có thể tạo triệu chứng tương tự** và bằng chứng để phân biệt.

## 4. Tạo bảng “giả thuyết → dự đoán → chứng cứ”

Ví dụ cho service có p99 latency bất thường:

| Giả thuyết | Dự đoán | Bằng chứng phù hợp | Điều làm giả thuyết yếu đi |
| --- | --- | --- | --- |
| H1: GC pause | Request spikes trùng mốc pauses; GC pause duration đáng kể | GC logs/JFR + aligned request traces | Request spikes không trùng pauses trong các cửa sổ đã kiểm tra |
| H2: lock contention | Nhiều threads BLOCKED/waiting on same monitor; monitor-enter events tăng | Thread dumps nhiều thời điểm, JFR Java Monitor Blocked events | CPU samples/trace cho thấy chờ database là chính |
| H3: native memory | RSS tăng không tương ứng heap; NMT categories tăng nếu được track | OS RSS, HotSpot NMT diff, direct buffer metrics | RSS tăng do page cache của hệ thống hay OS accounting khác |
| H4: JIT warm-up | Biến động tập trung sau startup và giảm khi code hot ổn định | Compilation logs, JFR compiler events, warm-up profile | Sau hours uptime tình trạng vẫn giống giai đoạn cold |
| H5: downstream | Server-side spans trỏ tới client calls tăng độ trễ | Distributed traces, connection pool metrics | Upstream local CPU/GC rõ ràng giải thích phần lớn latency |

**Bảng không thay thế phân tích:** “có GC log” là **nguồn dữ liệu**, không phải bằng chứng rằng GC gây ra p99. Cần xem **sự kiện, thời điểm, độ lớn, đối chứng và sự thay đổi sau can thiệp**.

## 5. Phân loại sức mạnh bằng chứng

Một cách xếp hạng thực hành, **không phải chuẩn thống kê bắt buộc**:

1. **Lời kể/ấn tượng:** “máy có vẻ chậm”; gợi hướng điều tra nhưng chưa đủ quyết định.
2. **Một snapshot:** một thread dump hay một biểu đồ heap; cho biết tình trạng tại thời điểm đó.
3. **Chuỗi quan sát có thời gian:** aligned GC events và latency theo nhiều cửa sổ.
4. **Bằng chứng cơ chế trực tiếp:** heap retained paths, blocked lock owner, compiler inlining decision, allocation stack trace phù hợp.
5. **Đối chứng/can thiệp:** giữ workload tương đương, chỉ thay một yếu tố và quan sát hiệu ứng lặp lại.
6. **Tái lập độc lập:** trên những JVM runs hoặc môi trường đại diện khác với cùng kết luận.

Mức 5–6 mạnh hơn mức 1, nhưng **không tự động chứng minh causal claim** nếu vẫn còn một confounder chưa kiểm soát. Trong báo cáo, ghi rõ **chứng cứ nào hỗ trợ phần nào của chuỗi lập luận**.

### Observation ≠ causation

Nếu p99 tăng cùng thời điểm GC pause tăng, có thể:

- GC là một phần nguyên nhân latency.
- Một **allocation spike** gây cả GC và overload; GC chỉ là một nhánh của hệ quả.
- Traffic tăng làm cả hai metrics xấu đi.
- Hệ thống metrics lấy mẫu khác cửa sổ/clock và tạo tương quan giả.

Muốn tuyên bố “H1 gây X” đáng tin hơn, cần **temporal alignment**, mechanism và **can thiệp/đối chứng** có kiểm soát, không chỉ hai đường biểu đồ đi lên cùng lúc.

## 6. Cấu trúc báo cáo ngắn gọn mà kiểm chứng được

Một báo cáo thực nghiệm JVM độc lập thường gồm:

1. **Tiêu đề và phạm vi:** triệu chứng, hệ thống, environment, thời gian.
2. **Tóm tắt điều hành:** 2–4 câu về điều đã biết và điều chưa thể kết luận.
3. **Triệu chứng:** baseline, test window, metrics/units/nguồn.
4. **Giả thuyết và dự đoán:** H1, H2...; những gì có thể bác bỏ.
5. **Thiết kế phép đo:** protocol, versions/flags/workload, repeats và cách kiểm soát.
6. **Bằng chứng:** bảng results; log excerpts có timestamps; link tới raw artifacts.
7. **Phân tích:** cơ chế, các kết quả phản bác, confounders.
8. **Kết luận với mức tin cậy:** supported / contradicted / inconclusive.
9. **Giới hạn:** gaps về data, tooling, thời gian, mẫu, production realism.
10. **Hành động tiếp theo:** ưu tiên, owner, điều kiện kiểm chứng hiệu quả và rollback.

Không bắt buộc tất cả báo cáo đều dài. Với một câu hỏi hẹp như “cờ collector nào đang bật?”, câu trả lời có thể ngắn. Nhưng **năm mắt xích** trong tiêu đề bài vẫn phải có nếu muốn trình bày kết quả điều tra nguyên nhân.

### Nêu rõ chủ thể của mỗi câu

Ví dụ:

- **Quan sát:** “Theo GC log, event ID 87 có duration 240 ms.”
- **Suy luận:** “Pause này có khả năng góp phần vào latency request cùng khoảng thời gian.”
- **Chưa xác nhận:** “Chưa có trace đồng bộ timestamp chứng minh request A bị stall trọn 240 ms.”
- **Đề xuất:** “Thu JFR + request spans đồng bộ ở lần thử tới.”

Cách tách câu giúp tránh vô tình nâng mức tin cậy của một nhận xét.

## 7. Mẫu thông tin nền môi trường không được bỏ qua

Báo cáo nên ghi lại:

| Thuộc tính | Lý do |
| --- | --- |
| Java version + build | Different JDK updates có thể đổi GC/JIT behavior |
| JVM implementation/vendor | HotSpot vs OpenJ9 vs Native Image có diagnostics khác |
| OS, kernel, CPU architecture | Scheduler, memory và instruction support khác |
| CPU quota/cores | JVM ergonomics và thực tế throttling khác |
| Container image digest | Giúp biết chính xác runtime binary, không chỉ tag |
| `-Xms` / `-Xmx` | Heap policy, allocation/GC pressure |
| Collector and GC flags | Thay đổi pause/throughput trade-offs |
| JVM other flags | Tiered/JIT, NMT, native memory, page configuration |
| Heap/non-heap metrics | Phân biệt Java heap và memory ngoài heap |
| Traffic profile | Request sizes, rate, concurrency, payload mixture |
| Version ứng dụng | Source artifact, git SHA, dependency versions |
| Recording/log settings | Event sampling và observer overhead |
| Time sync/timezone | Ghép GC events với external monitoring/traces |
| Experiment run IDs | Tránh gộp logs hai JVM processes khác nhau |

Nếu thiếu một thuộc tính trọng yếu, báo cáo phải nói **không có dữ liệu** thay vì tự suy từ số phiên bản Java hoặc máy của người phân tích.

## 8. Thiết kế phép đo có baseline và đối chứng

Để kiểm tra một giả thuyết JVM, bắt đầu bằng **baseline tương đối ổn định**. Ví dụ khi thử thay cache policy:

```text
Baseline A: cùng app/version, cache policy cũ
  ├─ warm-up  →  đo N lần dưới workload W
  └─ lưu GC logs, JFR/CPU, heap, latency

Treatment B: cùng app/version, chỉ đổi cache policy
  ├─ warm-up  →  đo N lần dưới workload W
  └─ lưu cùng metric set

Lặp A/B theo thứ tự phù hợp; ghi rõ runtime/host/load.
```

### Những điều cần giữ ổn định

- JVM vendor/build, collector, heap, container limits.
- Cùng request mix, arrival rate hoặc closed-loop concurrency model.
- Dữ liệu database/cache và background traffic tương đương.
- Giai đoạn warm-up đối với JIT, class loading và connections.
- Cùng measurement window duration.
- Cùng quan sát logging/JFR để không thay overhead giữa A và B.
- Cùng cách tổng hợp percentiles, units và outlier policy.
- Cùng đo startup hay steady state, không so hai pha khác nhau.

### Đừng đo một lượt duy nhất

Các nguồn biến động lớn:

- C1/C2 JIT profiling, OSR và deoptimization.
- GC trigger time và live-set biến thiên.
- Tần suất CPU, thermal throttling, power plan.
- Container CPU throttling, noisy neighbors.
- Page faults, cache warm state, disk/network.
- Lock contention và scheduler.
- Load generator bottleneck.
- Dynamic class loading và startup initialization.

Kết quả A thắng B trong một run có thể đảo ở run sau. Ghi **số lần chạy, mean/median, p95/p99, confidence/variability** hợp lý với loại metric; không cherry-pick lần đẹp nhất.

### Ngữ nghĩa workload phải tương đương

Nếu A xử lý 10.000 requests thật còn B bỏ qua validation hoặc trả cached constant result, thời gian thấp hơn không chứng minh triển khai B tối ưu hơn **với cùng yêu cầu nghiệp vụ**. Trước khi so performance, có correctness oracle, input equivalence và error-rate tracking.

## 9. Thu thập bằng chứng HotSpot an toàn và đúng câu hỏi

**Trước tiên:** xác định JVM đích thực tế. Trên JDK 17:

```bash
java -version
java --version
jcmd -l
```

`jcmd -l` có thể không liệt kê JVM trong container/PID namespace khác; cần chọn PID nhìn thấy từ cùng môi trường được phép, rồi chạy `jcmd PID help` để xác minh command availability. `PID` trong các ví dụ là ký hiệu phải thay bằng process ID thật, không phải nội dung cần gõ nguyên trong sản xuất.

### Các lệnh quan sát thường dùng

```bash
jcmd PID VM.version
jcmd PID VM.flags
jcmd PID VM.command_line
jcmd PID GC.heap_info
jcmd PID Thread.print
jcmd PID Compiler.queue
jcmd PID Compiler.codecache
```

**Lưu ý phạm vi:** đây là những commands thường có ở HotSpot JDK 17; availability và tác động phụ thuộc build, quyền attach và runtime. `Thread.print` có thể in nhiều stack traces, `GC.heap_info` chỉ tóm tắt heap, `Compiler.queue` phản ánh một thời điểm. Không coi một snapshot là timeline nhiều phút.

### Chọn công cụ theo giả thuyết

| Giả thuyết | Nguồn chứng cứ ưu tiên |
| --- | --- |
| GC pause gây latency | GC unified logs + request traces + JFR GC events |
| Java heap bị giữ sống | Heap dump + dominator tree + Paths to GC Roots |
| Allocation surge | JFR allocation events, allocation profiler + GC log |
| Monitor/lock contention | Thread dumps nhiều thời điểm, JFR Java Monitor Blocked |
| Deadlock | `jcmd Thread.print`, lock-owning stacks + deadlock detection có hỗ trợ |
| JIT chưa warm | `PrintCompilation`, JFR compiler events, code cache/compile queues |
| Native memory tăng | RSS + HotSpot Native Memory Tracking nếu đã bật + OS metrics |
| CPU high | JFR CPU profiling, CPU samples, OS perf tools |
| Downstream I/O | Distributed tracing, client metrics, thread state samples |
| OOME | Exception type, heap/native evidence, allocation/retaining data |

**Đừng bắt đầu bằng heap dump chỉ vì CPU tăng**, cũng đừng chỉ dùng `top` để kết luận GC là nguyên nhân. Chọn instrument **theo dự đoán của giả thuyết**, cân đối overhead và rủi ro.

## 10. Đọc GC evidence đúng giới hạn

Trên HotSpot JDK 17, có thể bật GC logging từ startup:

```bash
java -Xlog:gc*:info:file=gc.log:uptime,level,tags MyApp
```

`MyApp` là ứng dụng thật đã được biên dịch. Log giúp biết types/causes/timing của GC events, heap before/after và pauses theo collector/build.

### Những chỉ số cần lưu

- GC event ID và pause type/cause.
- Start timestamp/uptime, duration.
- Used heap before/after, committed/available capacity.
- Young vs old/full cycles nếu collector expose thông tin đó.
- Allocation rate/promoted bytes nếu được quan sát.
- Dòng sự kiện cùng khoảng thời gian với request latency spikes.
- Số events trên mỗi đơn vị thời gian và tổng pause time.

### Những điều GC log không tự chứng minh

- **GC event có mặt** không có nghĩa GC là nguyên nhân chính của toàn bộ slowdown.
- **Heap used tăng** không tự chứng minh memory leak; workload sống có thể tăng hợp lệ.
- **Không thấy Full GC** không có nghĩa hoàn toàn không có pressure.
- **After-GC heap used giảm** không có nghĩa process RSS giảm tương ứng.
- **Một `System.gc()` test** không phản ánh automatic GC behavior của workload bình thường.
- **Pause 200 ms** không chứng minh một request đã chậm chính xác 200 ms; phải align request span/timestamp.

### Ví dụ cách dẫn bằng chứng

```text
E-GC-01 — Log của process JVM 4182
Nguồn: HotSpot GC unified log (uptime + level + tags)
Cửa sổ: 10:00–10:30 UTC
Quan sát: 4 events lớn hơn 150 ms, peak 240 ms.
Đối chiếu: 3/4 events trùng với request latency spikes theo cửa sổ 1 s.
Giới hạn: độ phân giải metrics 1 s; chưa có request-to-pause
correlation ở cấp từng request.
```

Các con số trên là **ví dụ minh họa**, không phải kết quả đo. Chỗ quan trọng là **nguồn + thời gian + dữ kiện + giới hạn của phép nối**, không phải có log là đủ.

## 11. Heap dump: retained paths không đồng nghĩa leak theo nghiệp vụ

HotSpot có thể thu heap dump thông qua `jcmd`:

```bash
jcmd PID GC.class_histogram
jcmd PID GC.heap_dump heap-incident.hprof
```

**Cảnh báo vận hành quan trọng:**

- `GC.class_histogram` có thể có impact cao tùy heap size và content.
- `GC.heap_dump` có thể dừng ứng dụng đáng kể, tạo file lớn; cách xử lý live/unreachable objects và full GC tùy options.
- Heap dump có thể chứa **tokens, PII, session data, secrets**; cần quyền truy cập, mã hóa, retention policy và vị trí lưu an toàn.
- `heap-incident.hprof` là đường dẫn ví dụ; phải chọn filesystem còn đủ disk/quota.
- Trên production, nên có quy trình phê duyệt thu thập và cân nhắc snapshot/impact.

### Điều gì là bằng chứng của giữ sống?

Một heap analyzer có thể cho:

```text
GC Root / class loader state
    → static Cache.entries
    → ConcurrentHashMap.table
    → Entry payload
    → request-related byte[]
```

Đây là **đường giữ sống giả định**, không phải graph đã lấy từ dump nào. Nó cho thấy ở snapshot, object vẫn reachable qua chain. Nhưng việc object được giữ có phải **bug/leak** hay là một **cache hợp lệ** còn trong TTL? Cần đối chiếu expected lifecycle, eviction và workload.

### Nên báo điều gì?

- Snapshot được chụp **khi nào** so với incident?
- Heap dump phản ánh live-only hay gồm unreachable objects theo options?
- Top dominators, retained sizes và paths từ GC roots.
- Object lifetime mong đợi theo nghiệp vụ.
- Thống kê cùng type qua nhiều snapshots có tăng sau các GC không?
- Đối chứng sau khi thay eviction/cleanup logic.

Không được rút ra kết luận “fix leak” từ một histogram class count giảm mà không đối chiếu traffic/load và object ownership.

## 12. Thread dump: phân biệt BLOCKED, WAITING, TIMED_WAITING và RUNNABLE

Khi latency tăng, thu một số thread dumps cách nhau theo một protocol được phê duyệt:

```bash
jcmd PID Thread.print -l
```

Đọc:

- Stack frames: thread đang ở code path nào?
- `java.lang.Thread.State`: `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED`.
- Locks: thread đang chờ monitor nào, thread nào sở hữu nếu công cụ expose?
- Nhiều requests có cùng wait site/lock identity không?
- Có deadlock cycle rõ trong nhiều stacks không?
- Sự kiện JFR lock contention có cùng thời gian/monitor không?

**Không gắn nghĩa quá mức cho state:** `RUNNABLE` trong thread dump không nhất thiết CPU core đang thực thi liên tục; nó cũng có thể chờ I/O ở một dạng runtime/OS path. `WAITING` có thể là trạng thái bình thường trong thread pool hoặc queue.

### Một snapshot không đủ mô tả timeline

Một thread `BLOCKED` tại thời điểm dump chỉ chứng minh **thread đang chờ lock ở snapshot đó**. Nó không chứng minh “thread bị kẹt 20 phút” nếu chưa có nhiều samples, timestamps hoặc monitor events.

### Phân biệt triệu chứng và cơ chế

Nếu nhiều threads chờ một monitor và owner đang làm I/O trong critical section, có cơ sở giả thuyết lock convoy. Nhưng trước khi khẳng định, kiểm tra **owner stack, thời gian giữ lock, contention events, request traces**. Biện pháp có thể là giảm scope synchronized region, không phải bật thêm GC workers.

## 13. Native memory: RSS tăng có thể ngoài Java heap

JVM process memory gồm heap, metaspace, code cache, thread stacks, direct/native buffers và nhiều vùng VM khác. **OS RSS không bằng Java heap used**.

HotSpot có **Native Memory Tracking (NMT)**:

```bash
java -XX:NativeMemoryTracking=summary MyApp
```

Khi đã bật **từ startup**:

```bash
jcmd PID VM.native_memory summary
jcmd PID VM.native_memory baseline
jcmd PID VM.native_memory summary.diff
```

**Cần nhớ:**

- NMT của HotSpot **tắt mặc định** và phải được bật từ lúc khởi động để thu dữ liệu theo levels hỗ trợ.
- `summary` cho categories tổng hợp, `detail` cung cấp call-site detail khi được bật thích hợp.
- `baseline` rồi `summary.diff` giúp so chênh lệch trong **cùng JVM process**.
- NMT có overhead và **không theo dõi toàn bộ native allocations**, đặc biệt những allocations bên ngoài phạm vi HotSpot/JDK mà tài liệu nêu.
- Đừng tuyên bố “NMT total khớp 100% RSS”; accounting scopes khác nhau.
- Nếu NMT chưa bật, báo **không có dữ liệu NMT**, không tự dùng một câu lệnh attach để giả định có thể bật tracking đầy đủ về sau.

### Báo cáo đúng

Ví dụ giả định: RSS tăng 700 MiB, heap used ổn định, NMT cho thấy một category tăng 300 MiB. **Kết luận được:** có một thành phần native memory được NMT quan sát đang tăng, cần điều tra tiếp. **Chưa được kết luận:** 400 MiB chênh còn lại nhất định là leak ở thư viện X, bởi OS accounting và phạm vi NMT khác nhau.

## 14. JFR: nối thời gian của nhiều hiện tượng

**Java Flight Recorder (JFR)** có thể thu nhiều runtime events với timestamp và context. Trên HotSpot/JDK 17 hỗ trợ `jcmd`:

```bash
jcmd PID JFR.start name=IncidentRecord settings=profile duration=2m filename=incident.jfr
jcmd PID JFR.check
```

`duration=2m` là ví dụ hai phút; đổi theo cửa sổ điều tra. `settings=profile` thường thu nhiều sự kiện hơn `default`, có thể tăng overhead; cần kiểm soát và so sánh cùng settings khi làm A/B.

Các nhóm events có thể hữu ích:

- GC cycles và pauses.
- Allocation statistics/samples.
- CPU execution samples.
- Java monitor/blocking events.
- Thread lifecycle/activity.
- Class loading, compilation/code cache trong phạm vi events được bật.
- Exceptions/I/O nếu cấu hình ghi nhận.

**Không giả định mọi event luôn bật:** events, thresholds, stack depth và sampling settings quyết định dữ liệu có trong recording. Một event không xuất hiện không chứng minh hiện tượng không xảy ra.

### Ghép timeline có kiểm soát

```text
10:00:05.100  request R42 bắt đầu
10:00:05.220  GC pause bắt đầu (theo nguồn runtime phù hợp)
10:00:05.430  GC pause kết thúc
10:00:05.700  request R42 hoàn thành

Điều quan sát: request span chồng thời gian với pause.
Chưa thể kết luận: toàn bộ latency R42 là do GC.
```

Đây là **timeline giả định**. Cần đồng bộ clock/timezone/uptime conversion giữa JFR, GC logs và distributed traces. Đừng so `uptime` trong GC log với wall-clock của tracing system như thể cùng hệ quy chiếu.

## 15. JIT và code cache: bằng chứng hiệu năng không chỉ là GC

Nếu p99/throughput chỉ xấu ngay sau restart, **JIT warm-up** là một giả thuyết cạnh tranh:

```bash
java -XX:+PrintCompilation MyApp
jcmd PID Compiler.queue
jcmd PID Compiler.codecache
```

Những dấu hiệu có thể xét:

- Methods nóng chuyển từ interpreter/C1 sang C2.
- Compilation queue backlog dài.
- Code cache occupancy/cảnh báo compilation disabled.
- Repeated deoptimization/recompilation.
- CPU dành cho compiler threads.
- Latency biến thiên theo thời gian kể từ process start.

**Không được kết luận** level 4 C2 xảy ra sau đúng số invocations cố định hoặc biên dịch thành công bảo đảm tăng throughput. `PrintCompilation` có thể sinh nhiều output, thay timing và không thay thế profiling trên workload thật.

### Không biến tình trạng JIT thành nguyên nhân nếu chỉ thấy compile logs

JIT compile trong lúc service chậm có thể chỉ là một hoạt động bình thường sau startup. Muốn quy trách nhiệm, cần đối chiếu compiler CPU, request spans, runtime phases và kết quả thay đổi khi warm-up/flags được kiểm soát.

## 16. Benchmark: thời gian chạy không là giá trị cố định

Trong báo cáo, **output chức năng** của chương trình và **metric thời gian** phải được tách riêng. Ví dụ code Java có thể tính checksum hoàn toàn xác định nhưng thời gian thực thi thay đổi theo CPU/JIT/GC/OS.

### Microbenchmark

Sử dụng **OpenJDK Java Microbenchmark Harness (JMH)** khi đo một hot method. Các điểm cần báo cáo:

- JVM forks, warm-up iterations, measurement iterations.
- Time per iteration, unit, operation count.
- Input distribution và trạng thái `@State`.
- Chống dead-code elimination/constant folding bằng result consumption/Blackhole.
- JVM flags, collector, compiler version.
- Kết quả trung bình và độ phân tán/sai số; không chỉ một best run.

JMH không bảo đảm một benchmark được thiết kế sai sẽ tự động đúng. Ví dụ A benchmark phép cộng, B benchmark phép nhân thì không thể quy toàn bộ chênh lệch cho virtual call overhead.

### Service benchmark

Dùng load-generator/harness với:

- Open-loop arrival rates hoặc closed-loop concurrency được mô tả.
- Response times từ server/client có nguồn nhất quán.
- Throughput achieved vs target, error rate, retries/timeouts.
- CPU/memory/container quotas.
- Warm-up, steady-state windows và số repetitions.
- Tách startup/first-request latency khỏi long-run throughput.

## 17. Java 17+ runnable lab: phân biệt facts cố định và metrics động

Chương trình dưới đây tạo nhiều short-lived arrays trong cả hai modes; mode `retain` **cố ý giữ strong references** tới một phần arrays. Mục đích chính là thực hành viết báo cáo **không kết luận vượt quá những gì chương trình và số liệu thật cho phép**.

Tạo file độc lập **`JvmEvidenceLab.java`**:

```java
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public final class JvmEvidenceLab {
    private static volatile Object last;

    public static void main(String[] args) {
        if (args.length != 1
                || (!args[0].equals("baseline")
                    && !args[0].equals("retain"))) {
            System.err.println(
                    "Usage: java JvmEvidenceLab baseline|retain");
            System.exit(2);
        }

        boolean retain = args[0].equals("retain");
        List<byte[]> kept = new ArrayList<>();
        long checksum = 0;
        long start = System.nanoTime();

        for (int i = 0; i < 40_000; i++) {
            byte[] data = new byte[4096];
            data[0] = (byte) (i % 100);

            checksum += Byte.toUnsignedInt(data[0]);

            if (retain && i % 10 == 0) {
                kept.add(data);
            }
            // Có hiệu ứng observable: reference mới nhất thoát
            // ra static volatile field trong cả hai modes.
            last = data;
        }

        long elapsedNanos = System.nanoTime() - start;
        long used = ManagementFactory.getMemoryMXBean()
                .getHeapMemoryUsage().getUsed();

        System.out.println("mode: " + args[0]);
        System.out.println("iterations: 40000");
        System.out.println("checksum: " + checksum);
        System.out.println("retained arrays: " + kept.size());
        System.out.println("done");

        // DYNAMIC METRICS: cố ý ghi stderr, không coi là
        // stdout kết quả cố định để assert trong bài học.
        System.err.println("elapsed_ms: " + elapsedNanos / 1_000_000.0);
        System.err.println("heap_used_bytes: " + used);
    }
}
```

Biên dịch:

```bash
javac -encoding UTF-8 JvmEvidenceLab.java
```

Chạy hai modes trên HotSpot JDK 17, không bật logging khi kiểm tra stdout:

```bash
java -Xms32m -Xmx64m JvmEvidenceLab baseline
java -Xms32m -Xmx64m JvmEvidenceLab retain
```

**Standard output chính xác của `baseline`:**

```text
mode: baseline
iterations: 40000
checksum: 1980000
retained arrays: 0
done
```

**Standard output chính xác của `retain`:**

```text
mode: retain
iterations: 40000
checksum: 1980000
retained arrays: 4000
done
```

**Standard error biến động**, ví dụ có dạng:

```text
elapsed_ms: (số thực phụ thuộc thời gian chạy)
heap_used_bytes: (số nguyên phụ thuộc VM và heap)
```

Hai dòng trong khung stderr trên là **mô tả định dạng**, không phải literal output; đừng ghi thời gian hoặc số bytes cố định vào expected results.

### Giải thích checksum và retention

- `i % 100` tạo chu kỳ 0–99, tổng một chu kỳ là `4950`.
- 40.000 iterations tương ứng **400 chu kỳ** nên checksum là **1.980.000**.
- `baseline` không thêm phần tử vào `kept`, do đó kích thước danh sách là **0**.
- `retain` giữ reference khi `i % 10 == 0`, có đúng **4.000** phần tử.
- Mỗi array dài 4096 bytes, nên payload arrays được danh sách giữ strong references tổng cộng **4.000 × 4096 = 16.384.000 bytes**. Con số này **không phải retained heap size đầy đủ**, vì còn headers, references, alignment và list metadata.
- `last` giữ một reference tới array cuối cùng ở cả hai modes. Nhờ vậy source không đơn giản chỉ tạo một chuỗi allocations hoàn toàn không quan sát được; nhưng VM vẫn có những tối ưu hợp lệ khác.
- Code không gọi `System.gc()` để “ép” một chu kỳ; GC timing phụ thuộc HotSpot/heap/load.
- `elapsed_ms` **không phải kết quả hợp đồng Java** và có thể khác rất nhiều giữa các máy, lần chạy hoặc cấu hình GC.

### Câu hỏi thí nghiệm

**H1:** “Giữ thêm những arrays trong danh sách làm tăng live heap tương đối sau GC dưới workload tương tự.”

**Dự đoán:** trong mode `retain`, arrays đang được `kept` tham chiếu phải reachable tại những điểm mà chương trình còn dùng `kept`; nếu thu heap snapshot phù hợp tại thời điểm có giữ reference, sẽ thấy một graph tương ứng.

**Không được nhảy ngay tới kết luận:** “`retain` chắc chắn gây memory leak.” Đây là **retention có chủ đích** trong ví dụ; có phải leak hay không cần lifecycle yêu cầu. Tương tự, used heap trong **một snapshot** có thể không so được trực tiếp nếu GC chưa xảy ra ở cùng pha.

## 18. Cách biến lab thành thí nghiệm có chứng cứ

### 18.1. Thu GC logs từng mode

Trên HotSpot hỗ trợ unified logging:

```bash
java -Xms32m -Xmx64m -Xlog:gc*:info:file=gc-baseline.log:uptime,level,tags JvmEvidenceLab baseline
java -Xms32m -Xmx64m -Xlog:gc*:info:file=gc-retain.log:uptime,level,tags JvmEvidenceLab retain
```

**Lưu ý:** đường dẫn log là hai files khác nhau; không trộn hai JVM processes vào một timeline nếu thiếu process/run ID. Java stdout vẫn như các khung `text` ở mục 17; Java stderr có metrics động.

### 18.2. Đo lặp lại

Chạy A/B nhiều lần với cùng JDK, heap, OS/load và warm-up policy. Mỗi run lưu:

- Run ID, mode, JVM flags.
- `elapsed_ms` và `heap_used_bytes`; không mặc định cái sau là after-GC live heap.
- GC cycle count/pauses/heap occupancy khi log thực sự thể hiện.
- CPU usage và process/container limits.
- Các cảnh báo/error nếu có.

**Quan trọng:** thời gian được đo ở đây tính cả allocation và `volatile` store; vì hai modes làm **khác work thật**, chênh lệch duration không chỉ là GC overhead. Bài lab nhằm minh họa **retention + báo cáo bằng chứng**, không phải A/B chuẩn để đo riêng hiệu năng collector.

### 18.3. Đọc số liệu với đúng scope

Nếu số `heap_used_bytes` của `retain` cao hơn:

- **Quan sát được:** snapshot heap used tại thời điểm code đọc cao hơn.
- **Có thể phù hợp H1:** có strong references tới nhiều arrays.
- **Chưa chứng minh:** after-GC live set dài hạn tăng cùng tỷ lệ, hay GC là bottleneck của ứng dụng.
- **Giới hạn:** chưa đồng bộ GC phases/timestamps, số liệu chỉ trong một JVM process rất ngắn.
- **Bước tiếp:** giữ process đủ lâu để lấy heap snapshot có kiểm soát hoặc thêm protocol đo nhiều vòng; so retained paths và GC post-collection occupancy.

Nếu hai số `heap_used_bytes` bằng nhau hoặc đảo chiều, **không sửa dữ liệu hoặc loại run**. Có thể GC schedule khác. Báo thẳng kết quả, tìm phương pháp đo tốt hơn để kiểm tra dự đoán.

## 19. Ví dụ báo cáo hoàn chỉnh: nghi latency do GC

**Tình huống giả định:** cùng ứng dụng Java 17 HotSpot, service request p99 tăng sau thay đổi cache retention. Số liệu dưới đây **hoàn toàn giả lập phục vụ minh họa cách viết báo cáo**, không phải đo từ lab mục 17 hay một production system.

### 19.1. Triệu chứng

- Trong 15 phút treatment, request p99 = **640 ms**, baseline cùng traffic giả định là **210 ms**.
- Throughput xấp xỉ **800 requests/s** ở hai cửa sổ.
- Error rate dưới **0,2%** trong cả hai.
- Peak GC pause ở treatment = **290 ms**; baseline = **75 ms**.
- After-GC used heap ở treatment có xu hướng tăng qua các cửa sổ.

**Phát biểu cẩn thận:** p99 tăng **430 ms**, tức xấp xỉ **3,05 lần** so với baseline. Đây là so sánh của **hai cửa sổ minh họa**, chưa là causal inference.

### 19.2. Giả thuyết

- **H1:** cache mới giữ nhiều objects hơn, tăng live set/old occupancy và GC pauses, góp phần vào request latency.
- **H2:** một lock ở cache implementation làm nhiều requests BLOCKED.
- **H3:** backend database chậm đồng thời với deployment.

**Dự đoán H1:** heap dump có retained paths từ cache, GC post-collection heap tăng, request spikes trùng pauses; giới hạn hoặc eviction cache giúp những metrics đó ổn định dưới tải tương đương.

### 19.3. Bằng chứng (giả định)

| ID | Nguồn | Quan sát | Đánh giá |
| --- | --- | --- | --- |
| E1 | Request histograms | p99 210 → 640 ms, throughput gần tương đương | Xác nhận triệu chứng, chưa xác nhận nguyên nhân |
| E2 | GC log | Peak pause 75 → 290 ms; nhiều long pauses hơn | Hỗ trợ khả năng GC góp phần, chưa định lượng request-level delay |
| E3 | Heap dump staging | Cache map có strong paths tới entries không còn cần theo nghiệp vụ | Hỗ trợ mechanism H1, cần xác minh trên production |
| E4 | Thread dumps | Không thấy nhiều threads chờ cùng một monitor ở các samples đã thu | Làm H2 ít được hỗ trợ hơn, không chứng minh không có lock contention |
| E5 | Client spans | Database p99 gần baseline trong các samples đang có | Làm H3 yếu đi, nhưng sampling/window coverage còn giới hạn |
| E6 | Canary có eviction | p99 và after-GC live heap giảm qua ba repeated windows | Tăng sức mạnh của H1 nếu traffic/flags/capacity thật sự tương đồng |

### 19.4. Phân tích

H1 có bằng chứng theo **nhiều tầng**: triệu chứng (E1), tương quan GC (E2), retaining path có ý nghĩa nghiệp vụ (E3), một phép can thiệp có hướng (E6). H2 và H3 chưa có chứng cứ mạnh trong mẫu đang xét (E4–E5).

Điều **không được suy** là toàn bộ 430 ms p99 chênh do GC: peak pause chỉ là 290 ms, không có request-level attribution đầy đủ, p99 là một quantile tổng hợp và có thể còn các queue delays/phần mềm khác.

### 19.5. Kết luận

> “Các dữ liệu giả định hỗ trợ **mức tin cậy trung bình–cao** cho giả thuyết cache retention làm tăng live set và góp phần làm GC pauses/latency xấu đi trong thí nghiệm staging/canary. Chưa thể phân bổ chính xác bao nhiêu milliseconds của p99 do GC. Khuyến nghị giữ giới hạn cache, giám sát retained heap, GC pauses và request latency qua vài chu kỳ tải, rồi quyết định rollout dựa trên SLO.”

**Mức tin cậy trung bình–cao trong ví dụ là đánh giá định tính minh họa**, không phải một xác suất thống kê ước lượng từ dữ liệu thực.

### 19.6. Giới hạn và việc tiếp theo

- Staging và production có thể khác traffic mix/class loaders/CPU quotas.
- Heap dump chỉ là snapshot, việc lấy dump có thể gây pause.
- GC logs và metrics có thể lệch clock/resolution.
- Chưa có request-to-GC attribution đầy đủ.
- Three-window canary có thể chưa đại diện peak-hour traffic.
- Cần theo dõi ít nhất một chu kỳ workload đầy đủ theo yêu cầu tổ chức.
- Có phương án rollback nếu p99/error rate vượt thresholds đã định trước.

**Điểm học quan trọng:** kể cả báo cáo có kết luận rõ ràng vẫn phải ghi **điều chưa chứng minh**, thay vì dùng một từ “confirmed root cause” cho mọi liên hệ quan sát được.

## 20. Ví dụ báo cáo thứ hai: nghi memory leak nhưng kết quả chưa đủ

**Triệu chứng (giả định):** process RSS tăng từ 1,3 GiB lên 2,2 GiB sau sáu giờ, trong khi Java heap used đo tại những thời điểm tương đương gần 600 MiB.

**Giả thuyết:**

- H1: Java heap objects đang leak.
- H2: direct/native allocations tăng.
- H3: JVM memory committed hoặc OS memory accounting tăng mà không có logical leak.

**Bằng chứng giả định:** GC post-collection live heap không có xu hướng tăng rõ; `VM.native_memory summary.diff` (đã bật NMT) cho thấy category Thread/Code/GC biến động nhỏ, còn OS RSS tăng đáng kể.

**Kết luận đúng:** “Dữ liệu hiện tại chưa hỗ trợ mạnh H1. Có **mismatch** giữa RSS và heap used cần giải thích. Chưa đủ thông tin xác định H2 hay H3; NMT không bao quát mọi allocations.”

**Giới hạn:** snapshot timing, page cache/native arenas, NMT coverage và không có OS-level allocation profiler.

**Kế hoạch:** kiểm tra số threads/stacks, mapped memory, direct buffers, NMT detail nếu đã bật, OS memory maps và thay đổi native dependencies. Không viết “GC bug” chỉ vì RSS không giảm sau `System.gc()`.

## 21. Ví dụ báo cáo thứ ba: thread pool chậm

**Triệu chứng (giả định):** p99 API tăng vào giờ cao điểm, CPU cores không bị sử dụng hết; queue length của worker pool tăng.

**H1:** lock contention bên trong shared cache.  
**H2:** workers chờ database connection pool.  
**H3:** executor có quá ít workers cho workload.

**Bằng chứng cần tìm:**

- Multiple thread dumps với timestamps, top blocked/waiting stacks và lock owners.
- JFR monitor blocked events và durations.
- Connection pool active/idle/wait times.
- Executor queue length, task duration, rejection rate.
- Distributed traces trên requests đã vượt SLO.

**Kết luận có thể viết khi bằng chứng vẫn thiếu:** “Observed queue buildup, chưa xác định nguyên nhân chính là lock contention hay downstream wait. Cần phân biệt hai tình trạng trước khi tăng thread pool size; tăng threads khi đang chờ một lock chung có thể làm contention tệ hơn.”

Báo cáo như thế **không kém giá trị** chỉ vì kết luận chưa xác định. Nó chỉ ra quyết định hiện tại **không nên làm** và phép đo tiếp theo có **information gain** cao nhất.

## 22. Ghi độ tin cậy và giới hạn một cách thực dụng

Một kết luận nên có mức độ rõ:

| Tình trạng | Cách viết |
| --- | --- |
| **Đã quan sát** | “Metric X có giá trị Y ở cửa sổ W theo nguồn Z.” |
| **Hỗ trợ tốt** | “H1 phù hợp E1–E3, đã kiểm tra đối chứng A/B và lặp lại.” |
| **Bằng chứng chưa đủ** | “Hiện không thể phân biệt H1 và H2.” |
| **Bị phản bác** | “Dự đoán chính của H1 không xuất hiện trong điều kiện thử này.” |
| **Ngoài phạm vi** | “Thí nghiệm không đánh giá hiệu ứng ở traffic cao hơn 2x.” |
| **Khuyến nghị** | “Triển khai canary và kiểm tra p99/GC pause qua N cửa sổ trước rollout.” |

### “Không thấy” không đồng nghĩa “không tồn tại”

- Không thấy deadlock trong một thread dump **không** chứng minh không từng có lock contention.
- Không thấy GC pause lớn trong cửa sổ đo không chứng minh JVM sẽ không có pauses dài ở peak load.
- Không thấy memory leak sau 5 phút không chứng minh không leak sau vài ngày.
- Không thấy deoptimization log không chứng minh JIT không sử dụng speculation.
- Không thấy allocation event có thể do JFR sampling/settings, không phải do ứng dụng không allocate.

**Giới hạn phải gắn với từng bằng chứng:** sampling rate, thời điểm chụp, duration, overhead, tool support, test representativeness và độ phân giải timestamp.

## 23. Ghi mâu thuẫn và kết quả âm tính

Một báo cáo khoa học phải giữ cả bằng chứng **không phù hợp** với H1.

Ví dụ:

- H1 dự đoán GC pauses tương quan với latency; nhưng ở hai cửa sổ latency cao, **không có GC pause tương ứng**.
- H1 dự đoán heap live set tăng; dữ liệu after-GC ổn định.
- H1 dự đoán cache retains request contexts; heap dump chỉ thấy contexts của requests còn active.
- Can thiệp eviction không cải thiện p99 ở lần thử thứ hai.

Đừng xóa các observations này. Hãy ghi rõ: “H1 không giải thích toàn bộ triệu chứng; cần kiểm tra H2/H3 hoặc thiết kế phép đo tốt hơn.” Một giả thuyết có thể đúng **một phần** và vẫn không là root cause duy nhất.

### Phân biệt lỗi phép đo và dữ liệu ngược giả thuyết

Nếu metrics từ một node có clock lệch 90 giây, việc GC không trùng request spike có thể là **lỗi alignment**. Không tự gán đó là phản chứng H1 trước khi sửa việc đồng bộ timestamp. Nhưng cũng không được “dịch” logs thủ công cho đến khi chúng có vẻ khớp: phép align cần quy tắc khách quan và được ghi vào report.

## 24. Giới hạn về thống kê và suy luận nhân quả

### Percentile không cộng tuyến tính

Nếu latency gồm queue time + compute time + GC impact, **p99(total)** không nhất thiết bằng **p99(queue) + p99(compute) + p99(GC)**. Các thành phần có tương quan và quantiles không cộng đơn giản như tổng trung bình.

### Averages che tail behavior

Một service có average 30 ms nhưng 1% requests mất 1 giây có thể vẫn vi phạm SLO. Báo mean không thay p95/p99 và error rate. Với latency phải ghi **số lượng mẫu**, histogram/measurement window và phương pháp tính.

### Correlation cần kiểm soát confounders

Số GC cycles tăng cùng traffic có thể do workload tăng. So sánh treatment mà traffic cao hơn 2x với baseline thấp hơn không đủ chứng minh GC tuning làm hiệu năng tệ đi.

### Overhead của công cụ

Heap dump, high-frequency profiling, CPU tracing, `PrintInlining` và JFR `profile` settings có chi phí. Báo cáo phải ghi việc bật instrument có thể **thay chính timing đang đo**. Với A/B, cần settings tương đương hoặc trình bày rõ sự khác biệt.

### “Không có giá trị p” không đồng nghĩa thiếu rigor

Không phải mọi incident cần hypothesis-testing theo p-value. Nhưng một báo cáo có nhiều benchmark runs nên nêu uncertainty/error bars khi phù hợp, tránh khẳng định cải thiện nếu confidence intervals chồng nhau rộng hoặc run-to-run variance lớn.

## 25. Một quyết định nên dựa trên bằng chứng nào?

Trước khi đưa recommendation:

1. Có **hệ thống/metric đang cần cải thiện** được định nghĩa đúng không?
2. Có **cơ chế** hợp lý và một dự đoán đã được thử không?
3. Có bằng chứng **quan sát trực tiếp** và bằng chứng **đối chứng**?
4. Có **nguyên nhân cạnh tranh** chưa kiểm tra?
5. Có ước lượng **mức tác động** và **độ tin cậy**?
6. Thay đổi có **trade-off** không: CPU, heap, native memory, p99, correctness?
7. Có test hồi quy chức năng và kiểm tra SLO không?
8. Có rollout, canary, rollback, alert thresholds không?
9. Có thể **tái hiện report** từ scripts/logs/versions không?
10. Có phải quyết định cần thực hiện ngay hay vẫn nên đo thêm?

**Đề xuất hành động không nên mạnh hơn bằng chứng.** Nếu kết luận chỉ là “GC có thể góp phần”, không nên viết “bắt buộc thay GC để chữa leak”. Nếu root-path evidence cho thấy cache retaining objects không cần, thay cache lifecycle có logic nhân quả mạnh hơn một GC flag thay đổi.

## 26. Checklist chống kết luận quá đà

- [ ] Symptom có thời gian, metric, unit, baseline, workload, scope?
- [ ] Dữ liệu là raw hay đã aggregate? Aggregation window có ghi rõ?
- [ ] Có JVM vendor/build, OS/CPU/quota, heap/GC/JIT flags?
- [ ] H1 được phát biểu có thể bác bỏ, có predictions và mechanism?
- [ ] Có H2/H3 cạnh tranh khi hợp lý?
- [ ] Mỗi evidence có ID, nguồn, timestamp, sampling/overhead và đường dẫn lưu?
- [ ] Đã align clock/uptime/JFR/traces đúng hệ quy chiếu?
- [ ] Có raw artifacts để người khác kiểm tra lại?
- [ ] Có kiểm tra data quality, mất mẫu, sampling và outliers?
- [ ] Có lặp lại cùng workload, cùng measurement conditions?
- [ ] Có phân biệt GC pause, heap used, RSS, native memory?
- [ ] Có hiểu snapshot heap/thread chỉ là một thời điểm?
- [ ] Có cảnh giác JIT warm-up, class loading, code cache và GC?
- [ ] Có phân biệt retained path với business-logic leak?
- [ ] Có ghi cả evidence không phù hợp H1?
- [ ] Có nêu rõ điều mới là **correlation** và điều được kiểm tra bằng intervention?
- [ ] Có tách observations, inferences, conclusions và recommendations?
- [ ] Có giải thích các giới hạn của kết luận ở từng phần?
- [ ] Có đánh giá rủi ro của việc thu heap dump/JFR/log chứa sensitive data?
- [ ] Có kế hoạch tiếp theo giúp giải quyết uncertainty còn lại?
- [ ] Có tránh dùng kết quả một run ngắn để kết luận cho production dài hạn?
- [ ] Có xác định rõ bằng chứng nào sẽ làm thay đổi kết luận?

## 27. Bài tập có đáp án

**Bài 1 — Đặt lại symptom.** “Java CPU cao” có đủ không?  
**Đáp án:** Chưa. Cần process/container CPU source, mẫu số cores/quota, thời gian, throughput, baseline và phạm vi.

**Bài 2 — Giả thuyết.** “GC tệ” là giả thuyết tốt không?  
**Đáp án:** Không đủ kiểm chứng. Cần cơ chế và dự đoán như pauses trùng latency spikes, live heap tăng hoặc promotion pressure.

**Bài 3 — Tương quan.** p99 và GC pause cùng tăng. Có chắc GC là root cause?  
**Đáp án:** Không. Traffic/allocation có thể gây cả hai; cần timeline, mechanism và đối chứng.

**Bài 4 — Thu heap dump.** Heap dump thấy 100.000 entries trong cache. Có chắc leak?  
**Đáp án:** Không. Cần expected TTL/capacity/lifecycle và retention paths phù hợp.

**Bài 5 — Native memory.** RSS tăng 1 GiB nhưng Java heap used không đổi. Có nên gọi `System.gc()` liên tục?  
**Đáp án:** Không. Phân biệt committed memory, NMT coverage, direct buffers, stacks, OS accounting và native allocations.

**Bài 6 — Thread dump.** Một thread BLOCKED duy nhất chứng minh hệ thống deadlock?  
**Đáp án:** Không. Cần lock ownership/cycle, thời gian và nhiều observations.

**Bài 7 — JIT.** Bật `PrintCompilation` và không thấy method hot; suy ra JVM không JIT?  
**Đáp án:** Không. Method có thể chưa hot, được inline trong caller hoặc logs chưa đủ.

**Bài 8 — A/B.** A chạy 2 cores, B chạy 1 core, B chậm hơn. Có quy chênh cho GC flag không?  
**Đáp án:** Không. CPU quota là confounder trọng yếu.

**Bài 9 — Đơn vị.** 1 GiB bằng bao nhiêu bytes?  
**Đáp án:** 1.073.741.824 bytes. 1 GB theo thập phân bằng 1.000.000.000 bytes.

**Bài 10 — Lab checksum.** `JvmEvidenceLab` in checksum bao nhiêu?  
**Đáp án:** **1.980.000**, do 400 chu kỳ của 0..99, mỗi chu kỳ có tổng 4950.

**Bài 11 — Lab retention.** Mode `retain` lưu bao nhiêu arrays?  
**Đáp án:** **4.000**, vì mỗi 10 iterations trong 40.000 iterations lưu một array.

**Bài 12 — Dùng số heap used.** Có thể khẳng định mode retain luôn in `heap_used_bytes` lớn hơn mode baseline?  
**Đáp án:** Không. Snapshot timing/GC policy/JIT khác; cần phép đo live heap sau collection và retention paths.

**Bài 13 — Báo cáo mẫu.** p99 từ 210 lên 640 ms, chênh bao nhiêu?  
**Đáp án:** Tăng **430 ms**, xấp xỉ **3,05 lần** so baseline.

**Bài 14 — NMT.** Có thể bật NMT đầy đủ trên JVM đang chạy bằng một lệnh `jcmd` nếu startup không bật?  
**Đáp án:** Không theo cơ chế HotSpot NMT thông thường; phải bật từ startup bằng `-XX:NativeMemoryTracking=summary` hoặc `detail`.

**Bài 15 — JFR.** Không thấy allocation event trong recording có chứng minh app không allocate?  
**Đáp án:** Không. Sampling/event configuration/thresholds ảnh hưởng cái được ghi nhận.

**Bài 16 — Dữ liệu âm.** Nếu H1 dự đoán GC pause trùng spike mà trong hai windows không có pause, nên làm gì?  
**Đáp án:** Kiểm tra timestamp alignment và coverage; nếu hợp lệ, hạ mức tin cậy H1 và thử H2/H3, không bỏ dữ liệu bất lợi.

**Bài 17 — Percentiles.** Có thể cộng p99(queue) với p99(compute) để suy p99(total) không?  
**Đáp án:** Không nói chung; quantiles không cộng tuyến tính.

**Bài 18 — Khả năng tái hiện.** Ghi “chạy Java 17” đã đủ?  
**Đáp án:** Chưa. Cần JVM vendor/build, flags, OS/CPU, workload, run IDs, recording settings.

**Bài 19 — Impact của đo.** Heap dump có thể làm app chậm?  
**Đáp án:** Có. Cần kiểm tra impact, disk, quyền và bảo mật vì heap dump có thể chứa secrets.

**Bài 20 — Độ tin cậy.** “H1 phù hợp E1 và E2” có ngang “H1 được chứng minh nhân quả” không?  
**Đáp án:** Không. Tương quan và mechanic consistency là bằng chứng hỗ trợ; can thiệp/đối chứng và confounder analysis củng cố causal claim.

**Bài 21 — Kết luận âm.** Không chứng minh H1 thì report có vô dụng?  
**Đáp án:** Không. Bằng chứng âm tính giúp loại trừ hướng sai, xác định những phép đo còn thiếu.

**Bài 22 — Khuyến nghị.** Một mình GC log cho thấy pauses 250 ms, có nên đổi sang collector khác ngay?  
**Đáp án:** Chưa chắc. Trước hết xem GC là bottleneck chính, live set/heap/config và thử collector phù hợp với workload/SLO.

**Bài 23 — Report rõ ràng.** Câu “ứng dụng memory leak” nên thay bằng gì nếu mới có heap used tăng?  
**Đáp án:** “Quan sát heap used tăng trong cửa sổ W; chưa có evidence về retained paths/after-GC trend để xác định leak.”

**Bài 24 — Kiểm chứng canary.** Trước rollout rộng, cần điều gì?  
**Đáp án:** Canary có metric p99/error/GC/memory, success thresholds và rollback criteria, tải đại diện và so sánh baseline.

## 28. Kết luận và tài liệu đối chiếu

Báo cáo thực nghiệm JVM là **một lập luận minh bạch**, không phải tập hợp logs. Nó phải nối được:

```text
Symptom đã đo
   ↓
Hypothesis có mechanism và prediction
   ↓
Evidence có nguồn, timestamp, điều kiện thử và đối chứng
   ↓
Conclusion với mức tin cậy phù hợp
   ↓
Limitations, competing hypotheses và next test
```

Một báo cáo đáng tin **không sợ ghi “chưa đủ bằng chứng”**. Nó trình bày rõ điều đã quan sát, tránh nhầm correlation với causation, nêu các confounders và giải thích phép đo tiếp theo sẽ thay đổi kết luận như thế nào.

### Nguồn chính thống và công cụ tham khảo

1. **Oracle JDK 17 — `jcmd` command** (JFR, thread, heap, VM flags/commands và impact): https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
2. **Oracle JDK 17 — Native Memory Tracking** (cấu hình từ startup, baseline/diff và giới hạn coverage): https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
3. **Oracle JDK 17 — Troubleshooting Guide** (quy trình sử dụng GC/JFR/thread/heap diagnostics): https://docs.oracle.com/en/java/javase/17/troubleshoot/
4. **Oracle JDK 17 — JFR Runtime Guide** (recording, events, profiling): https://docs.oracle.com/en/java/javase/17/jfapi/
5. **Oracle JDK 17 — Garbage Collection Tuning Guide:** https://docs.oracle.com/en/java/javase/17/gctuning/
6. **OpenJDK JMH** (benchmark harness, warm-up, forks, JMH samples): https://github.com/openjdk/jmh
7. **OpenJDK Java Mission Control** (phân tích JFR recordings): https://github.com/openjdk/jmc
8. **OpenJDK JCStress** (các thí nghiệm JMM và concurrency outcomes): https://github.com/openjdk/jcstress

**Thông điệp cuối:** báo cáo có giá trị khi một người khác có thể đọc và nói: “Tôi hiểu symptom, biết giả thuyết nào đã thử, tìm lại được nguồn evidence, thấy rõ kết luận hợp lý ở mức nào, và biết chính xác điều gì chưa được kiểm chứng.”

</details>

- [Quay lại đầu trang](#back-to-top)
