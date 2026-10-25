<a id="back-to-top"></a>

# Tra cứu

## Menu
- [Bảng đối chiếu loadClass, forName, defineClass và resolveClass](#reference-tables-001)
- [Bảng thao tác kích hoạt khởi tạo lớp hoặc interface](#reference-tables-002)
- [Bảng lỗi theo giai đoạn nạp, liên kết, khởi tạo và sử dụng](#reference-tables-003)
- [Bảng thay đổi class loading giữa Java 8 và Java hiện đại](#reference-tables-004)
- [Thuật ngữ defining loader, initiating loader, runtime package và layer](#reference-tables-005)
- [Tra cứu JVMS, JLS, Javadoc và công cụ JDK theo chủ đề](#reference-tables-006)

## <a id="reference-tables-001">Bảng đối chiếu loadClass, forName, defineClass và resolveClass</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng đối chiếu `loadClass`, `forName`, `defineClass` và `resolveClass` trong Java

## 1. Nhìn nhanh: bốn API làm những việc khác nhau

Khi xử lý class loader, plugin, reflection hoặc lỗi liên kết lớp trong Java, bốn tên API này rất dễ bị hiểu là bốn cách gọi tương đương để “nạp một lớp”. Chúng thực ra đảm nhiệm các vai trò khác nhau: **tìm/nạp lớp**, **tìm lớp và tùy chọn khởi tạo**, **định nghĩa lớp từ bytecode** và **yêu cầu liên kết một lớp đã có**.

Bảng dưới dùng ngữ nghĩa Java SE 17, đặc biệt với `java.lang.ClassLoader` và `java.lang.Class`. Các chi tiết bên trong JVM, thời điểm liên kết từng symbolic reference và log chẩn đoán có thể thay đổi tùy implementation.

| API | Vị trí và quyền truy cập | Đầu vào chính | Công việc chính | Tự yêu cầu initialize? | Kết quả |
|---|---|---|---|---|---|
| `loader.loadClass(String name)` | `ClassLoader`, `public` | Binary name và loader đang gọi | Tìm/nạp lớp theo thuật toán loader, tương đương `loadClass(name, false)` | **Không** | `Class<?>` |
| `loader.loadClass(String name, boolean resolve)` | `ClassLoader`, `protected` | Binary name và cờ `resolve` | Tìm/nạp; khi `resolve=true`, gọi `resolveClass` cho lớp tìm được (trong triển khai mặc định) | **Không** | `Class<?>` |
| `Class.forName(String name)` | `Class`, `public static` | Tên lớp; **loader định nghĩa class đang gọi** được dùng theo đặc tả | Tìm/nạp và yêu cầu **initialize** nếu chưa khởi tạo | **Có** | `Class<?>` |
| `Class.forName(String name, boolean initialize, ClassLoader loader)` | `Class`, `public static` | Tên, cờ initialize và loader được chỉ định | Tìm/nạp bằng loader này; `true` yêu cầu initialize, `false` không | **Theo cờ** | `Class<?>` |
| `defineClass(String name, byte[] bytes, int off, int len)` | `ClassLoader`, `protected final` | Tên kỳ vọng và bytes của file class | Chuyển bytecode thành một **runtime class do loader này định nghĩa** | **Không** | `Class<?>` |
| `resolveClass(Class<?> c)` | `ClassLoader`, `protected final` | `Class` đã có | **Link** lớp đã được định nghĩa; nếu đã linked thì không làm thêm | **Không** | `void` |

**Điểm then chốt:** `resolve` trong `loadClass(name, true)` nghĩa là **liên kết**, không phải “initialize = true”. Hai cờ `resolve` và `initialize` thuộc **hai giai đoạn khác nhau**. Gọi `defineClass` chỉ tạo định nghĩa runtime; nó không tương đương “gọi static initializer”. Và `Class.forName` không tự tạo instance bằng constructor.

Ngoài các overload trên, Java 9+ còn có `Class.forName(Module module, String name)`: đây là API tra lớp theo **module**, trả về `null` nếu không tìm thấy; theo Javadoc, overload này **không liên kết và không chạy class initializer**. Không áp dụng mặc định “mọi `forName` đều initialize” cho overload `Module`.

## 2. Đặt vào vòng đời lớp để không nhầm thuật ngữ

Một cách hình dung thực dụng:

```text
Tên binary của lớp, ví dụ sample.ProbeTarget
                 │
                 ▼
       ClassLoader.loadClass(name)
                 │
     ┌───────────┴───────────┐
     │ Đã được nạp?          │
     ├─ Có ─► trả Class      │
     │ Chưa                  │
     ▼                       │
 Parent delegation / findClass
     │
     ▼
 Đọc bytes từ nguồn phù hợp
     │
     ▼
 defineClass(name, bytes, ...)
     │
     └───────────► Class<?> ───────────────┐
                                         │
                                         ▼
                               resolveClass(c)
                                         │
                                         ▼
                                 Linking / verification,
                                 preparation và resolution
                                         │
                    khi active use hoặc yêu cầu initialize
                                         ▼
                              Initialization (`<clinit>`)
```

Đây là **sơ đồ khái niệm**, không phải lời cam kết JVM phải thực thi đầy đủ mọi bước theo đúng một thứ tự log cứng. Linking gồm verification, preparation và resolution; resolution của một số symbolic reference có thể được thực hiện muộn. JVM cũng có quyền nạp các lớp phụ thuộc khi cần.

Phân biệt ba kết quả:

- **Loaded/defined:** một `Class<?>` đại diện cho một runtime class. Nó có thể vẫn **chưa initialization**.
- **Linked:** JVM đã thực hiện các bước liên kết theo ngữ nghĩa cần thiết. Điều này **không** có nghĩa mọi class mà mã nguồn từng tham chiếu đều đã được tải/khởi tạo.
- **Initialized:** JVM thực hiện quy trình khởi tạo lớp, bao gồm static initializers và static field initializers khi cần. Một lớp có thể được load và linked từ trước nhưng chưa init.

**Danh tính lớp runtime** được xác định bằng **binary name và defining class loader**. Hai loader có thể định nghĩa hai `Class<?>` khác nhau cùng tên `sample.ProbeTarget`. Cùng tên class không đồng nghĩa cùng kiểu Java khi chuyển object giữa hai loader.

## 3. `ClassLoader.loadClass`: tìm và nạp theo chiến lược loader

### `loadClass(String)`: API công khai thường dùng

```java
ClassLoader loader = Thread.currentThread().getContextClassLoader();
Class<?> type = loader.loadClass("com.example.Plugin");
```

Đoạn trên là **mảnh mã minh họa**, giả định `getContextClassLoader()` khác `null` và `com.example.Plugin` có thể được loader tìm thấy. Nó nhận `Class<?>`, **không tạo instance**, và việc chỉ gọi `loadClass` không yêu cầu khởi tạo lớp.

Đối với implementation mặc định của `ClassLoader`, `loadClass(name)` tương đương gọi `loadClass(name, false)`. Nếu lớp đã nạp, loader có thể trả về `Class` hiện có; không nhất thiết mở lại JAR hay đọc class bytes lần nữa.

### `loadClass(String, boolean)`: overload protected

Javadoc mô tả thuật toán mặc định của `loadClass(name, resolve)` theo thứ tự:

1. `findLoadedClass(name)` kiểm tra lớp tương ứng đã được nạp trong phạm vi mà loader này biết.
2. Nếu chưa có, ủy quyền cho parent loader; nếu không có parent, sử dụng cơ chế loader tích hợp của VM.
3. Nếu parent không tìm được, gọi `findClass(name)` — vị trí phổ biến để custom loader tải bytes và gọi `defineClass`.
4. Nếu tìm được và `resolve=true`, gọi `resolveClass(c)` cho `Class` kết quả.

Đây là **hành vi mặc định**; một subclass có thể override `loadClass` để thay đổi cách tìm hoặc delegation. Vì vậy đừng khẳng định mọi loader của plugin luôn parent-first: cần đọc implementation thực tế. Trong đa số loader theo delegation thông thường, override `findClass` là cách mở rộng an toàn và đúng thiết kế hơn là viết lại hoàn toàn `loadClass`.

```java
public final class MyLoader extends ClassLoader {
    public MyLoader(ClassLoader parent) {
        super(parent);
    }

    // Ví dụ rút gọn: trả kết quả của protected overload.
    public Class<?> loadAndLink(String binaryName)
            throws ClassNotFoundException {
        return super.loadClass(binaryName, true);
    }
}
```

Class `MyLoader` minh họa việc mở quyền gọi `protected` thông qua một method do subclass cung cấp; ví dụ này không triển khai `findClass` nên chưa phải một plugin loader tự tìm bytecode.

**Không nhầm `resolve=true` với `initialize=true`.** Hai cờ khác nhau, và `loadClass(name, true)` vẫn không tự yêu cầu thực thi `<clinit>`. Đổi tên biến `resolve` thành “initialize” trong code không đổi ngữ nghĩa thực tế.

## 4. `Class.forName`: lựa chọn tùy chọn initialization

### `Class.forName(String name)`

```java
Class<?> driver = Class.forName("com.example.Driver");
```

Đây là mảnh mã minh họa, giả định class có tồn tại. Overload một tham số **yêu cầu initialize** lớp nếu lớp chưa được init. Theo Javadoc Java SE 17, nó tương đương `Class.forName(name, true, currentLoader)`, trong đó `currentLoader` là **defining loader của class thực hiện lời gọi**. Không thể mặc nhiên thay nó bằng “system loader”, “thread context loader” hoặc “loader của mọi thư viện”.

Một ví dụ thường gặp là framework/plugin chạy dưới một class loader khác với caller. Gọi overload một tham số có thể **không tìm thấy class plugin**, dù plugin loader riêng biết class đó. Trong trường hợp ấy cần chỉ rõ loader thích hợp.

### `Class.forName(String name, boolean initialize, ClassLoader loader)`

```java
ClassLoader pluginLoader = /* loader đã được tạo đúng cách */;
Class<?> loaded = Class.forName(
        "com.example.Plugin", false, pluginLoader);

Class<?> initialized = Class.forName(
        "com.example.Plugin", true, pluginLoader);
```

Đây là **mã minh họa trong ngữ cảnh có biến `pluginLoader`**, không phải chương trình độc lập. Nếu lớp chưa được load, hai lời gọi có thể làm nó được load. Khác biệt quan trọng:

- `initialize=false`: **không yêu cầu chạy initializer** cho lớp đích ở lời gọi đó; không có nghĩa là JVM cam kết “chưa link bất kỳ thứ gì”.
- `initialize=true`: JVM bảo đảm khởi tạo theo quy tắc Java nếu lớp chưa được initialize. Trong quá trình đó, các superclass và một số superinterface cũng có thể được khởi tạo theo JLS.
- Nếu lớp đã initialize, gọi `forName(..., true, ...)` lần nữa trên **cùng runtime class** không chạy lại `<clinit>`.
- Nếu `loader == null` ở overload này, Javadoc quy định dùng **bootstrap class loader**, không phải “chọn loader hiện tại”.
- Nếu `name` là tên của array class, component type có thể được load nhưng **không được initialize chỉ bởi lời gọi cho array đó**.
- Chuỗi như `"int"` không trả về `int.class`; API này không phải cách tra primitive class object theo tên.

Với ứng dụng cần **xem metadata mà hạn chế side effects của static initializer**, lựa chọn `forName(name, false, correctLoader)` thường hữu ích. Dù vậy việc phản chiếu và thao tác tiếp theo (ví dụ invoke static method) vẫn có thể kích hoạt initialization.

### `forName` và reflection errors

`Class.forName` có thể ném `ClassNotFoundException` khi loader không tìm được class, `LinkageError` nếu liên kết bị lỗi và `ExceptionInInitializerError` nếu static initializer thất bại theo trường hợp áp dụng. Không coi việc bắt `ClassNotFoundException` là đủ để xử lý mọi sai lệch binary/API.

## 5. `defineClass`: định nghĩa lớp từ bytecode

`defineClass` là method **`protected final`** của `ClassLoader`. Các overload thường dùng:

```java
// Mã bên trong subclass của ClassLoader.
Class<?> type = defineClass(binaryName, bytecode, 0, bytecode.length);
```

JDK 17 còn có overload nhận `ProtectionDomain` hoặc `ByteBuffer`. `defineClass` chuyển dữ liệu hợp lệ của file class thành `Class<?>` được **loader hiện tại định nghĩa**, chịu các quy tắc về binary name, package và bảo vệ.

Một custom loader thường làm như sau:

```java
@Override
protected Class<?> findClass(String name) throws ClassNotFoundException {
    try {
        byte[] bytes = readBytecodeFor(name);
        return defineClass(name, bytes, 0, bytes.length);
    } catch (java.io.IOException e) {
        throw new ClassNotFoundException(name, e);
    }
}
```

Đây là **pseudocode Java** chỉ minh họa pattern: `readBytecodeFor(name)` chưa được khai báo và cần do người triển khai cung cấp. Ví dụ đầy đủ, chạy được nằm ở phần 7.

Lưu ý thiết yếu:

- `defineClass` **không tự quyết định cách tìm classpath, không tự triển khai parent delegation, không tự gọi constructor và không yêu cầu initialize**. Nó chỉ xử lý bytes mà caller cung cấp.
- Trong class loader tuân theo delegation, đường đi bình thường là `loadClass → findClass → defineClass` khi parent không tìm được. Gọi `defineClass` trực tiếp để “ép thay” một lớp đã được loader đó định nghĩa là sai mô hình.
- Cùng **binary name** không thể được định nghĩa hai lần bởi cùng defining loader theo cách thông thường; duplicate definition dẫn đến lỗi liên kết, ví dụ `LinkageError`.
- Tên kỳ vọng không trùng class bytes, dữ liệu không hợp lệ hoặc hạn chế package có thể dẫn đến `NoClassDefFoundError`, `ClassFormatError`, `SecurityException` hay lỗi liên kết tương ứng. Một loader ứng dụng không được tự định nghĩa class trong package bắt đầu bằng `java.`.
- `defineClass` với overload không chỉ rõ `ProtectionDomain` dùng protection domain mặc định của API. Khi thiết kế loader production, phải quan tâm quyền truy cập, nguồn code, signer/certificate consistency và vòng đời của package; không dùng mẫu tối giản này như giải pháp bảo mật hoàn chỉnh.
- Định nghĩa lớp tạo ra runtime identity gắn với loader. Hai loader khác nhau có thể định nghĩa cùng class bytes thành **hai loại runtime khác nhau**.

**Khi nào dùng?** Khi bạn **viết custom ClassLoader** và cần biến bytecode từ file/JAR/network/generated output thành runtime class. Code nghiệp vụ thông thường gần như không nên gọi trực tiếp API protected này.

## 6. `resolveClass`: liên kết, không chạy `<clinit>`

`resolveClass(Class<?> c)` cũng là method **`protected final`** của `ClassLoader`. Javadoc mô tả nó là thao tác **links the specified class**; nếu lớp đã được linked thì chỉ trở về. Tên method dễ gây hiểu nhầm: Java VM có khái niệm **resolution** của symbolic references, nhưng một số resolution có thể được trì hoãn cho tới khi sử dụng; đừng diễn dịch `resolveClass` là “tải và resolve toàn bộ dependency graph” hay “chạy static initializer”.

Ví dụ expose API từ subclass:

```java
final class LinkingLoader extends ClassLoader {
    void link(Class<?> type) {
        resolveClass(type);
    }
}
```

Đây là mảnh code minh họa: `link` chỉ có ý nghĩa nếu `type` đến từ một đường nạp/định nghĩa lớp phù hợp; đoạn trên không tự cung cấp bytes để tạo class. Trong custom loader, pattern phổ biến:

```text
bytes → defineClass(...) → Class<?> type → resolveClass(type)
                                │
                                └─ Sau đó một active use / forName(true)
                                   mới yêu cầu initialization
```

**`resolveClass` không có public overload** để code bất kỳ dùng trực tiếp bên ngoài subclass; khác với `Class.forName` vốn là static public API. `loadClass(name, true)` gọi `resolveClass` trong implementation mặc định; vì vậy **hai API có liên hệ nhưng không đồng nghĩa**.

## 7. Ví dụ Java 17+: so sánh ba loader, bốn API và thứ tự initialization

Thí nghiệm sau cố tình biên dịch class plugin **ngoài classpath của chương trình chủ**, để class chỉ được định nghĩa bởi loader tùy biến. Chúng ta quan sát bằng output cố định rằng `loadClass`, `defineClass`, `resolveClass` và `loadClass(name, true)` **không yêu cầu init**, trong khi `forName(..., true, loader)` làm khởi tạo mỗi runtime class.

### Cấu trúc

```text
working/
├── ClassLoadingComparison.java
├── plugin-src/
│   └── sample/
│       └── ProbeTarget.java
└── plugin-classes/
    └── sample/
        └── ProbeTarget.class  (javac tạo)
```

File **`plugin-src/sample/ProbeTarget.java`**:

```java
package sample;

public final class ProbeTarget {
    static {
        System.out.println("ProbeTarget initialized");
    }

    public static int value() {
        return 42;
    }
}
```

File **`ClassLoadingComparison.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClassLoadingComparison {
    private static final String NAME = "sample.ProbeTarget";

    static final class ByteLoader extends ClassLoader {
        ByteLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            try {
                byte[] bytes = Files.readAllBytes(
                        Path.of("plugin-classes", "sample", "ProbeTarget.class"));
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        Class<?> defineDirectly(String name)
                throws ClassNotFoundException {
            return findClass(name);
        }

        void link(Class<?> type) {
            resolveClass(type);
        }

        Class<?> loadAndResolve(String name)
                throws ClassNotFoundException {
            return super.loadClass(name, true);
        }
    }

    public static void main(String[] args) throws Exception {
        ByteLoader a = new ByteLoader();
        System.out.println("A: loadClass");
        Class<?> first = a.loadClass(NAME);
        System.out.println("A: forName(false) same Class = "
                + (first == Class.forName(NAME, false, a)));
        System.out.println("A: forName(true)");
        Class.forName(NAME, true, a);
        System.out.println("A: value = "
                + first.getMethod("value").invoke(null));

        ByteLoader b = new ByteLoader();
        System.out.println("B: defineClass");
        Class<?> second = b.defineDirectly(NAME);
        System.out.println("B: resolveClass");
        b.link(second);
        System.out.println("B: forName(true)");
        Class.forName(NAME, true, b);
        System.out.println("A and B different = " + (first != second));

        ByteLoader c = new ByteLoader();
        System.out.println("C: loadClass(resolve=true)");
        c.loadAndResolve(NAME);
        System.out.println("C: forName(true)");
        Class.forName(NAME, true, c);
    }
}
```

### Biên dịch và chạy

Tại thư mục `working`, tạo trước thư mục `plugin-src/sample` và `plugin-classes`; đặt hai file như trên, rồi chạy:

```bash
javac -d plugin-classes plugin-src/sample/ProbeTarget.java
javac ClassLoadingComparison.java
java -cp . ClassLoadingComparison
```

**Không thêm `plugin-classes` vào `-cp .`**: nếu plugin ở classpath của chương trình chủ, class loader cha/khác có thể nạp nó và thí nghiệm sẽ không còn chứng minh đúng đường `ByteLoader.defineClass`. Với lệnh này, dấu `.` đại diện đúng **một** entry nên dùng giống nhau trên Windows và Unix.

Output ứng dụng (không bật log JVM) **chính xác**:

```text
A: loadClass
A: forName(false) same Class = true
A: forName(true)
ProbeTarget initialized
A: value = 42
B: defineClass
B: resolveClass
B: forName(true)
ProbeTarget initialized
A and B different = true
C: loadClass(resolve=true)
C: forName(true)
ProbeTarget initialized
```

Giải thích lần lượt:

- **Loader A:** `a.loadClass(NAME)` trả `Class` mà chưa in `ProbeTarget initialized`. `forName(NAME, false, a)` trả **cùng `Class`** và cũng không yêu cầu init. `forName(NAME, true, a)` kích hoạt static initializer; reflection gọi `value()` sau đó trả `42`.
- **Loader B:** `defineDirectly` gọi `findClass`, trong đó `defineClass` định nghĩa một lớp mới từ cùng bytes. `b.link(second)` gọi `resolveClass` nhưng không in static initializer. `forName(true)` sau đó làm lớp của B init lần đầu.
- **Hai loader A/B:** `first != second` là `true` dù cùng `sample.ProbeTarget` và cùng file `.class`: defining loader khác tạo runtime class identity khác.
- **Loader C:** `loadAndResolve` đi qua `super.loadClass(name, true)`, tức resolve-flag của loader API. Nó **không initialize**. Lệnh `forName(true)` tiếp theo mới làm static initializer của lớp C chạy.

Mỗi loader định nghĩa một **runtime class riêng**, nên thấy ba dòng `ProbeTarget initialized` là hợp lệ; không phải JVM khởi tạo lại **cùng một** lớp ba lần.

Ví dụ được thiết kế để diễn giải API công khai/protected; nó **không** phải custom loader production. `defineDirectly` cố ý bỏ qua đường tìm kiếm/delegation chỉ để phân biệt `defineClass` với `loadClass`. Trong sản phẩm, nên ưu tiên implementation loader có locking, lookup policy và quản lý package/protection domain chuẩn.

### Thêm log JVM để quan sát

Trên HotSpot JDK 17+:

```bash
java -Xlog:class+load=info,class+init=info -cp . ClassLoadingComparison
```

Tìm các dòng chứa `sample.ProbeTarget` và tag `[class,load]` hoặc `[class,init]`. Log có thể chứa nhiều lớp JDK và thông tin động (timestamp, address, loader data); **không** cố khẳng định mọi bản HotSpot tạo ra một chuỗi log byte-for-byte giống nhau. Nếu cần lưu file:

```bash
java -Xlog:class+load=info,class+init=info:file=class-events.log:time,uptime,level,tags -cp . ClassLoadingComparison
```

Bật log class ở **lượt quan sát**, tránh lấy kết quả timing của lượt log làm benchmark so sánh hiệu năng vì I/O/logging có overhead. Các log tag giúp xác nhận class thực sự được load và init, không biến resolve thành init.

## 8. Bảng chọn API theo tình huống

| Tình huống | Nên dùng | Lý do |
|---|---|---|
| Muốn loader tìm và trả class theo chính sách delegation, **chưa cần side effect init** | `loader.loadClass(name)` | API công khai, không yêu cầu initialize |
| Muốn điều khiển rõ loader và thời điểm initialization | `Class.forName(name, false/true, loader)` | Tường minh class loader và cờ initialize |
| Muốn gọi `forName` mà dùng loader của chính class caller | `Class.forName(name)` | Javadoc xác định current loader và init=true |
| Muốn viết custom loader từ bytecode file/network/generated bytes | `defineClass` bên trong `findClass` | Chuyển bytes thành runtime class của loader hiện tại |
| Muốn class loader chủ động **link** lớp đã có | `resolveClass(c)` hoặc `loadClass(name, true)` trong subclass | Link là trách nhiệm của loader, tách khỏi init |
| Muốn lấy `Class` theo một named module mà không init và có thể trả `null` | `Class.forName(module, name)` | Overload module có semantics riêng |
| Muốn tạo object, chạy constructor và sử dụng dependency | Lấy `Class`/type rồi dùng constructor/API tương ứng | Bốn API trong bảng **không tự tạo instance** |
| Muốn unload class/plugin | Quản lý vòng đời loader và tham chiếu, quan sát GC/unloading | Không API nào trong bốn method là “unloadClass” |

Lưu ý `loadClass(name)` và `forName(name, false, loader)` thường được dùng để “chỉ load”, nhưng **không nên tuyên bố** hai đường này tương đương tuyệt đối với mọi custom loader: `forName` có quy định riêng của `Class`, còn implementation `loadClass` của một subclass có thể thay đổi hành vi mặc định hoặc có side effects riêng.

## 9. Các lỗi, giới hạn quyền truy cập và điểm dễ hiểu sai

### Phân biệt tên binary và tên source

Các phương thức nhận tên class dưới dạng string thường dùng **binary name**, ví dụ `java.util.Map$Entry` cho nested type `Map.Entry`. Với class `com.example.Demo`, tên đúng là `"com.example.Demo"`, không phải đường dẫn `"com/example/Demo.class"`. Với overload `Class.forName(String, boolean, ClassLoader)`, tên array còn có dạng descriptor như `"[Ljava.lang.String;"`, nên phải xem hợp đồng API trước khi tự nối chuỗi.

`defineClass(name, bytes, ...)` yêu cầu nội dung bytecode phù hợp với binary name dự kiến. Không được lấy bytes của `com.example.A` rồi yêu cầu định nghĩa tên `com.example.B` và kỳ vọng VM tự đổi tên trong constant pool.

### `ClassNotFoundException`, `NoClassDefFoundError` và các `LinkageError`

- `ClassNotFoundException` là checked exception phổ biến khi **yêu cầu tra tên lớp** mà loader không thể tìm được.
- `NoClassDefFoundError` là `LinkageError`, thường xuất hiện khi runtime cần một định nghĩa lớp từng được kỳ vọng tồn tại nhưng không thể tìm/nạp ở thời điểm đó; cũng có thể xuất hiện khi class trước đó initialization thất bại và được dùng lại.
- `ClassFormatError`, `UnsupportedClassVersionError` và các lỗi liên kết khác liên quan bytes/class version không hợp lệ, không tương thích runtime hoặc quá trình linking.
- `ExceptionInInitializerError` thường cho thấy static initializer/initialization bị lỗi, có thể chỉ xuất hiện khi `forName(..., true, loader)` hoặc lần active use chạy.
- `ClassCastException` khi chuyển object giữa hai runtime type cùng tên nhưng khác defining loader không nhất thiết là lỗi “không tìm thấy class”.

Đừng giải quyết các lỗi trên bằng cách gọi `resolveClass` nhiều lần hoặc thêm `Class.forName` một cách tùy tiện. Cần kiểm tra exception type, classpath, loader delegation, binary identity và version.

### `protected` không đồng nghĩa public API

`defineClass`, `resolveClass` và `loadClass(name, boolean)` là protected của `ClassLoader`; `defineClass`/`resolveClass` còn là final. Code nghiệp vụ bên ngoài subclass thông thường **không thể gọi** trực tiếp các method protected qua một object bất kỳ. Trong code ví dụ, `ByteLoader` công khai các wrapper riêng (`defineDirectly`, `link`, `loadAndResolve`) để minh họa và quan sát. Các wrapper ấy **không phải API tiêu chuẩn của Java**.

Không cố bypass bảo vệ Java module/access hoặc phản chiếu vào `ClassLoader#defineClass` chỉ để load plugin; cách đó có thể bị chặn bởi encapsulation của module `java.base` và làm thiết kế phụ thuộc chi tiết runtime.

### Resolve không phải initialize; initialize không có nghĩa “instantiate”

- `resolveClass(c)` → link class theo đặc tả; **không chạy `new`/constructor**.
- `Class.forName(name, true, loader)` → yêu cầu class initialization, gồm static initialization; **không gọi constructor instance**.
- `Class.forName(name, false, loader)` → không yêu cầu initialization; **không cam kết mọi metadata/dependency đều chưa được VM xử lý**.
- `ClassLoader.loadClass(name)` → tra/nạp, không bắt buộc phải thực hiện `<clinit>`; **không được nhầm với `Class.forName(name)`**.
- `defineClass(...)` → tạo `Class` định nghĩa từ bytes; **không thay JAR runtime toàn cục**, không tái định nghĩa cùng lớp đã được loader đó define.

Nếu cần một đối tượng, phải chọn constructor/factory đúng API và xử lý riêng các bước truy cập/phản chiếu. Một lần khởi tạo class có thể kéo theo khởi tạo superclass trước, theo quy tắc JLS `12.4`.

## 10. Quy trình debug khi class loader cho kết quả bất ngờ

1. **Ghi đúng API đang được gọi:** `loadClass(name)`, `forName(name)`, `forName(name, false, loader)`, `defineClass` hay `resolveClass`. Đặc biệt ghi rõ cờ `initialize` hoặc `resolve`.
2. **Chứng minh defining loader:** lấy `type.getClassLoader()`; `null` có thể đại diện bootstrap loader. Đừng suy chỉ từ tên package/class.
3. **Kiểm tra nguồn bytes và delegation:** classpath/module path, thứ tự JAR, loader parent/child, `findClass`. Trong plugin environment, parent có thể nạp phiên bản khác với file plugin.
4. **Xác nhận sự kiện load/init:** trên HotSpot, bật `-Xlog:class+load=info,class+init=info` trước khi tái hiện; log class load khác với log class init.
5. **Đọc nguyên exception và stack trace:** phân biệt missing class, invalid bytecode, linkage conflict và lỗi `<clinit>`. Không catch một loại rồi giả định mọi lỗi là do classpath.
6. **Kiểm tra phiên bản JVM và bytecode:** class file major version phải được runtime hỗ trợ; thay đổi dependency tại runtime không làm loader đang giữ `Class` cũ tự quên chúng.
7. **Giải pháp theo ranh giới đúng:** sửa dependency/loader policy, thống nhất API implementation, tạo loader mới trong lifecycle plugin nếu phù hợp; không dùng `System.gc()` để “sửa” mâu thuẫn class definition.

Ví dụ in nhanh loader identity trong code Java khi đã có `Class<?> type`:

```java
System.out.println("Class name = " + type.getName());
System.out.println("Defining loader = " + type.getClassLoader());
System.out.println("Module = " + type.getModule().getName());
```

Đây là đoạn **minh họa sử dụng biến `type` đã có**, không phải chương trình độc lập. Một unnamed module có thể có tên `null`; class do bootstrap loader định nghĩa cũng có loader `null`. Với cùng tên `getName()` nhưng loader khác, hai `Class<?>` vẫn khác nhau.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Hai câu `loader.loadClass("X")` và `Class.forName("X")` có bảo đảm khởi tạo lớp như nhau không?  
**Đáp án:** Không. `loadClass(String)` không yêu cầu init; `Class.forName(String)` yêu cầu init và dùng defining loader của caller.

**Câu 2.** Cờ `resolve=true` có tương đương `initialize=true` không?  
**Đáp án:** Không. `resolve=true` yêu cầu link qua `resolveClass` trong loader mặc định; `initialize=true` yêu cầu class initialization theo ngữ nghĩa `Class.forName`.

**Câu 3.** Có thể gọi `defineClass` hai lần để “ghi đè” cùng class đã định nghĩa bởi một loader không?  
**Đáp án:** Không. Một defining loader không thể định nghĩa cùng binary name lần nữa như thế; tạo loader khác sẽ cho runtime class khác, không thay class cũ.

**Câu 4.** `Class.forName("int")` có phải `int.class` không?  
**Đáp án:** Không. Theo Javadoc, overload tên chuỗi không dùng để lấy primitive `Class` từ tên primitive.

**Câu 5.** Tại sao ví dụ in `ProbeTarget initialized` ba lần?  
**Đáp án:** Ba `ByteLoader` khác nhau đã định nghĩa ba runtime class cùng binary name; mỗi class được yêu cầu init riêng.

**Câu 6.** Muốn biết class đã load nhưng chưa chạy `<clinit>` thì nên dùng gì?  
**Đáp án:** `loadClass(name)` hoặc `Class.forName(name, false, loader)` và quan sát log `class+load` so với `class+init`; chú ý side effects từ custom loader và thao tác tiếp theo.

## 12. Tài liệu gốc

- [Java SE 17 — `ClassLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass(String)`, `loadClass(String, boolean)`, `findClass`, `defineClass`, `resolveClass` và cơ chế delegation.
- [Java SE 17 — `Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): ba nhóm overload `forName` với `String`, `String/boolean/ClassLoader` và `Module/String`.
- [JLS 17 — Chương 12: Execution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html): định nghĩa loading, linking, initialization và unloading.
- [JVMS 17 — Chương 5: Loading, Linking and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): ngữ nghĩa VM và thời điểm liên kết.
- [JDK 17 — `java` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): cú pháp `-Xlog` để quan sát class load và init trên HotSpot.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-002">Bảng thao tác kích hoạt khởi tạo lớp hoặc interface</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng thao tác kích hoạt khởi tạo lớp hoặc interface trong Java

## 1. Cần phân biệt “đã nạp lớp” và “đã khởi tạo lớp”

Khi phân tích Java ClassLoader, câu hỏi **“lớp này đã được load chưa?”** không đồng nghĩa **“static initializer của lớp đã chạy chưa?”**. JVM có nhiều giai đoạn riêng:

1. **Loading:** tìm biểu diễn nhị phân, tạo runtime class/interface và đối tượng `Class<?>` tương ứng, với defining ClassLoader được xác định.
2. **Linking:** verification, preparation và resolution (thời điểm resolution có thể thay đổi theo chiến lược JVM).
3. **Initialization:** thực thi mã khởi tạo class/interface, đặc biệt các static field initializers và static initialization blocks (nếu có).

Ví dụ:

```java
class Sample {
    static {
        System.out.println("Sample initialized");
    }

    static int number = 123;
}
```

Đây là **đoạn source minh họa** không có `main`. Việc nhận được `Sample.class` hay kết quả của một lời gọi `loadClass("Sample")` chưa có nghĩa dòng `Sample initialized` đã được in. Ngược lại, khi code thực sự đọc `Sample.number` lần đầu mà class chưa khởi tạo, JVM phải khởi tạo `Sample` trước khi trả giá trị.

Trong Java, “class initialization” có ý nghĩa rất cụ thể. **Static initialization** của class không phải “chạy constructor” của từng instance và cũng không phải quá trình JVM nạp bytecode từ file. Bài này tập trung vào **bảng thao tác nào kích hoạt initialization, thao tác nào không, và các ngoại lệ quan trọng với interface, superclass, hằng compile-time và reflection**.

## 2. Quy tắc gốc theo JLS 17 §12.4.1

JLS 17 nêu rằng một class hoặc interface `T` cần được khởi tạo trước lần đầu xảy ra một trong các thao tác khởi tạo chủ động phù hợp. Các nhóm nền tảng là:

- **Tạo instance của class `T`**, ví dụ `new T()`.
- **Gọi static method được khai báo bởi `T`**.
- **Gán một static field được khai báo bởi `T`**.
- **Đọc một static field do `T` khai báo mà field ấy không phải *constant variable*.**
- **Một số thao tác phản chiếu** qua `java.lang.Class` hoặc `java.lang.reflect` theo hợp đồng API.

Khi JVM khởi tạo **class**, nó còn khởi tạo các **superclass** chưa được khởi tạo và những **superinterface có khai báo default method** liên quan theo quy tắc JLS/JVMS. Ngược lại, khi khởi tạo **interface**, việc đó **không tự động khởi tạo toàn bộ superinterface** của interface ấy.

Thứ tự thực thi các static field initializers và `static { ... }` trong một class/interface tuân theo **thứ tự xuất hiện trong source** và các quy tắc ngôn ngữ; mỗi runtime class/interface được JVM quản lý trạng thái initialization riêng. Một class đã khởi tạo thành công không chạy lại `<clinit>` chỉ vì có thêm một lời gọi `getstatic` hoặc `new`.

**Điểm cần nhớ:** cùng một thao tác có thể kéo theo initialization của **một class khác** mà không khởi tạo class xuất hiện trong biểu thức source. Ví dụ, `Sub.inheritedField` có thể thực sự là field do `Parent` khai báo; lúc ấy **Parent** là nơi được khởi tạo vì truy cập field, còn **Sub** không tự khởi tạo bởi việc ấy.

## 3. Bảng tra cứu nhanh: thao tác và initialization

Bảng dưới dùng giả định class/interface tương ứng **chưa được khởi tạo trước đó**, không có lỗi class file, và class member đang nói tới thực sự được khai báo ở kiểu đã nêu. “Có” nghĩa **thao tác đó trực tiếp đòi hỏi initialization** theo ngữ nghĩa Java. “Không” nghĩa **không chỉ vì riêng thao tác ấy**, dù việc nạp/link hoặc khởi tạo lớp liên quan có thể phát sinh từ ngữ cảnh khác.

| Thao tác | Khởi tạo loại nào? | Kết luận | Ghi chú chính xác |
| --- | --- | --- | --- |
| `new T()` với `T` là class | `T` cùng superclass/default-method superinterfaces cần thiết | **Có** | Trước khi tạo instance thành công, class `T` phải được initialization |
| Gọi `T.staticMethod()` do `T` khai báo | Class hoặc interface `T` | **Có** | Static method invocation là active use |
| Gán `T.staticField = ...` | Kiểu **khai báo field** | **Có** | Với field có thể gán; `putstatic` cần initialization |
| Đọc `T.staticField` không là constant variable | Kiểu **khai báo field** | **Có** | `getstatic` cho non-constant field là active use |
| Đọc `T.CONST` là *constant variable* | Thường không cần initialization chỉ để lấy hằng | **Không** | Giá trị có thể được đưa vào constant pool của caller |
| Tham chiếu `T.class` | `T` | **Không** | Class literal có thể dẫn đến loading/linking, không phải initialization |
| Tạo mảng `new T[n]` | Element class `T` | **Không** | Có thể tạo array class mà chưa initialize component class |
| `x instanceof T` | `T` | **Không** | Kiểm tra kiểu không phải active-use trigger của `T` |
| Ép kiểu `(T) x` | `T` | **Không** | `checkcast` không tự chạy static initializer của target type |
| Khai báo `T variable;` hoặc gán `T ref = null` | `T` | **Không** | Chỉ dùng tên kiểu ở source không khiến `T` khởi tạo |
| `ClassLoader.loadClass("pkg.T")` mặc định | `T` | **Không** | Load class không đồng nghĩa initialize; custom code có thể làm việc khác |
| `Class.forName(name, false, loader)` | `T` | **Không** | Tìm/nạp/link theo API nhưng không yêu cầu initialize |
| `Class.forName(name)` | `T` | **Có** đối với class/interface thông thường | Overload 1 tham số yêu cầu initialize |
| `Class.forName(name, true, loader)` | `T` | **Có** | Tham số boolean chọn initialization |
| `Class.forName(name, false, loader)` với tên array class | Component class | **Không** | Array type không đòi hỏi component initialization |
| `T.class.getName()` / `getClassLoader()` | `T` | **Không** | Đọc class metadata không đủ để kích hoạt `<clinit>` |
| `T.class.getDeclaredMethod("run")` | `T` | **Không** theo thao tác lookup | Có thể load/resolve metadata khác; không gọi method |
| `method.invoke(null)` với static method thuộc `T` | `T` | **Có** | Reflection invocation kích hoạt initialization khi cần |
| `field.get(null)` đọc static field thuộc `T` | `T` | **Có** theo reflection API | Kể cả field có giá trị compile-time constant trong thí nghiệm |
| `constructor.newInstance(...)` | Declaring class | **Có** | Tạo instance phản chiếu |
| `MethodHandle.invokeExact(...)` cho static method/field `T` | `T` | **Có** khi thực sự thực hiện thao tác | Lấy handle không đồng nghĩa gọi handle |
| Chỉ `lookup.findStatic(T.class, ...)` | `T` | **Không** trong ví dụ | Chưa invoke; xét API cụ thể, đừng gộp tìm handle với gọi handle |
| `MethodHandles.Lookup.ensureInitialized(T.class)` | `T` | **Có** | API yêu cầu đảm bảo class được khởi tạo khi quyền cho phép |
| `new ImplementsPlain()` với interface không có default method | Interface `Plain` | **Không** chỉ bởi việc khởi tạo class con | Class implementation vẫn được khởi tạo |
| `new ImplementsDefault()` với interface có khai báo default method | Superinterface `HasDefault` | **Có** trước class tương ứng theo quy tắc | Quan trọng với static field initializers của interface |
| Đọc static field không phải hằng trong interface `I` | `I` | **Có** | Truy cập owner interface; không mặc nhiên init các superinterfaces |
| Khởi tạo `Sub` extends `Parent` | `Parent` | **Có** nếu chưa khởi tạo | Parent được khởi tạo trước Sub |
| Chỉ đọc `Sub.field` nhưng field thật do Parent khai báo | `Parent`, không nhất thiết Sub | **Chỉ owner** | Tên `Sub` trong biểu thức không quyết định initialization |
| Tạo lambda `() -> T.staticMethod()`, chưa gọi lambda | `T` | **Không vì body chưa chạy** | Lambda linkage có chi tiết riêng; nếu body thực sự chạy sẽ có active use |
| Chỉ xuất hiện `T` trong generic type/type token | `T` | **Không** chỉ vì cú pháp kiểu | Phụ thuộc biểu thức runtime có gọi API kích hoạt khác hay không |

Bảng là công cụ tra cứu **trigger**, không phải lời khẳng định class luôn còn ở trạng thái uninitialized đến thời điểm đó: JVM có thể đã khởi tạo nó do code khác, một class phụ thuộc hoặc thao tác trước đó. Thêm nữa, trong môi trường custom loader, chính phương thức `loadClass` do framework override có thể chủ động gọi `Class.forName(..., true, ...)` hoặc thực thi code tạo side effect; bảng mô tả **hợp đồng API chuẩn**, không bảo đảm custom method không làm việc phụ.

## 4. Bảng riêng cho static fields và compile-time constants

Một nguồn nhầm lẫn rất phổ biến là suy từ chữ `static final` rằng field “không làm class khởi tạo”. Điều này **không luôn đúng**. Khái niệm trong JLS là **constant variable**: một biến `final` có kiểu primitive hoặc `String`, được khởi tạo bằng **constant expression**.

| Khai báo | Constant variable? | Đọc bằng Java source thường làm owner initialize? |
| --- | --- | --- |
| `static final int A = 10;` | Có | **Không** chỉ vì đọc `A` |
| `static final String S = "Java";` | Có | **Không** chỉ vì đọc `S` |
| `static final long L = 1L + 2L;` | Có | **Không** khi biểu thức là compile-time constant |
| `static final int X = Integer.parseInt("7");` | Không | **Có** nếu owner chưa init |
| `static final String T = new String("Java");` | Không | **Có** |
| `static final Integer I = 7;` | Không: reference wrapper không phải primitive/String | **Có** |
| `static int count = 5;` | Không | **Có** |
| `static final Object O = new Object();` | Không | **Có** |
| `static final int[] NUMBERS = {1, 2};` | Không: array reference | **Có** |

Ví dụ:

```java
final class Settings {
    static {
        System.out.println("init=Settings");
    }

    static final int PORT = 8080;
    static final String HOST = "localhost";
    static final int DYNAMIC_PORT = Integer.parseInt("8080");
}
```

Đây là **class minh họa** không có main. `System.out.println(Settings.PORT)` và `Settings.HOST` có thể được compiler thay bằng literal/constant pool, nên **không** đòi hỏi `Settings` initialization. Nhưng `Settings.DYNAMIC_PORT` không phải constant variable: đọc nó sẽ yêu cầu khởi tạo Settings trước.

**Cẩn thận với JAR versioning:** nếu client đã biên dịch và inline `Settings.PORT`, đổi giá trị PORT trong JAR ở runtime chưa chắc làm client hiện giá trị mới. Đây là đặc điểm binary compatibility của constant variables, không phải bằng chứng ClassLoader đã chọn sai class.

**Cẩn thận với reflection:** `Field.get(null)` đọc static field qua reflection có thể kích hoạt initialization ngay cả khi khai báo source là constant variable. Vì vậy không thể áp dụng quy tắc “hằng compile-time không init” cho mọi **cách truy cập**; cần phân biệt bytecode/biểu thức thường với reflection.

## 5. Ví dụ Java 17 đầy đủ: quan sát 21 thao tác và trạng thái khởi tạo

Ví dụ dưới đây gom các trường hợp thành một chương trình duy nhất, nhưng mỗi trường hợp sử dụng **một class/interface riêng chưa được đụng đến** khi cần đo thời điểm initialization. Điều này tránh lỗi khi test: nếu class đã khởi tạo ở dòng trước, dòng sau không thể in lại `init=...` dù thao tác đó vốn là active use.

Lưu mã vào file **`InitActionsDemo.java`**:

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class InitActionsDemo {
    private static String trace(String message) {
        System.out.println(message);
        return message;
    }

    static final class Passive {
        static { trace("init=Passive"); }
        static final int CONST = 42;
        static int number = 7;
        static String echo() { return "ok"; }
    }
    static final class WriteTarget {
        static { trace("init=WriteTarget"); }
        static int number;
    }
    static final class Constructed {
        static { trace("init=Constructed"); }
        String value() { return "object"; }
    }
    static class Parent {
        static { trace("init=Parent"); }
    }
    static final class Child extends Parent {
        static { trace("init=Child"); }
        static String value = "child";
    }
    static class FieldOwner {
        static { trace("init=FieldOwner"); }
        static int inherited = 11;
    }
    static final class Derived extends FieldOwner {
        static { trace("init=Derived"); }
    }
    interface Plain {
        String MARK = trace("init=Plain");
    }
    static final class ImplementsPlain implements Plain {
        static { trace("init=ImplementsPlain"); }
    }
    interface HasDefault {
        String MARK = trace("init=HasDefault");
        default String value() { return "default"; }
    }
    static final class ImplementsDefault implements HasDefault {
        static { trace("init=ImplementsDefault"); }
    }
    interface Base {
        String MARK = trace("init=Base");
    }
    interface Extended extends Base {
        String MARK = trace("init=Extended");
    }
    static final class ForNameTarget {
        static { trace("init=ForNameTarget"); }
    }
    static final class MethodTarget {
        static { trace("init=MethodTarget"); }
        public static String run() { return "method"; }
    }
    static final class FieldTarget {
        static { trace("init=FieldTarget"); }
        public static final int VALUE = 99;
    }
    static final class HandleTarget {
        static { trace("init=HandleTarget"); }
        public static String run() { return "handle"; }
    }

    public static void main(String[] args) throws Throwable {
        System.out.println("01 literal=" + Passive.class.getSimpleName());
        System.out.println("02 constant=" + Passive.CONST);
        System.out.println("03 array=" + (new Passive[2]).length);
        System.out.println("04 forNameFalse=" +
                (Class.forName(Passive.class.getName(), false,
                        InitActionsDemo.class.getClassLoader()) == Passive.class));
        System.out.println("05 getstatic=" + Passive.number);
        System.out.println("06 invokestatic=" + Passive.echo());
        WriteTarget.number = 9;
        System.out.println("07 putstatic=" + WriteTarget.number);
        System.out.println("08 new=" + new Constructed().value());
        System.out.println("09 subclass=" + Child.value);
        System.out.println("10 inherited=" + Derived.inherited);
        System.out.println("11 plainImplement=" + new ImplementsPlain().getClass().getSimpleName());
        System.out.println("12 plainField=" + Plain.MARK);
        System.out.println("13 defaultImplement=" + new ImplementsDefault().value());
        System.out.println("14 extendedField=" + Extended.MARK);
        Class<?> named = Class.forName(ForNameTarget.class.getName(), true,
                InitActionsDemo.class.getClassLoader());
        System.out.println("15 forNameTrue=" + named.getSimpleName());
        Method method = MethodTarget.class.getDeclaredMethod("run");
        System.out.println("16 reflectedMethodFound=" + (method != null));
        System.out.println("17 methodInvoke=" + method.invoke(null));
        Field field = FieldTarget.class.getDeclaredField("VALUE");
        System.out.println("18 fieldFound=" + (field != null));
        System.out.println("19 fieldGet=" + field.get(null));
        MethodHandle handle = MethodHandles.lookup().findStatic(
                HandleTarget.class, "run", MethodType.methodType(String.class));
        System.out.println("20 handleFound=" + (handle != null));
        System.out.println("21 handleInvoke=" + (String) handle.invokeExact());
    }
}
```

Biên dịch và chạy với **JDK 17 trở lên**:

```bash
javac --release 17 InitActionsDemo.java
java InitActionsDemo
```

Không cần Maven, Gradle, file JAR ngoài hoặc custom ClassLoader. Mã đã được biên dịch bằng `javac 21 --release 17` và chạy thử trên Java 21; các thao tác là API/syntax tương thích Java 17. **Stdout thực tế của chương trình:**

```text
01 literal=Passive
02 constant=42
03 array=2
04 forNameFalse=true
init=Passive
05 getstatic=7
06 invokestatic=ok
init=WriteTarget
07 putstatic=9
init=Constructed
08 new=object
init=Parent
init=Child
09 subclass=child
init=FieldOwner
10 inherited=11
init=ImplementsPlain
11 plainImplement=ImplementsPlain
init=Plain
12 plainField=init=Plain
init=HasDefault
init=ImplementsDefault
13 defaultImplement=default
init=Extended
14 extendedField=init=Extended
init=ForNameTarget
15 forNameTrue=ForNameTarget
16 reflectedMethodFound=true
init=MethodTarget
17 methodInvoke=method
18 fieldFound=true
init=FieldTarget
19 fieldGet=99
20 handleFound=true
init=HandleTarget
21 handleInvoke=handle
```

**Lưu ý về dòng `12 plainField=init=Plain`:** hàm `trace("init=Plain")` vừa in thông điệp, vừa **trả về chính chuỗi ấy** làm giá trị field `MARK`. Vì vậy dòng kết quả chứa `init=Plain` hai lần theo hai ý nghĩa khác nhau: một dòng độc lập báo lúc interface init và một giá trị string được in sau `plainField=`. Tương tự với `Extended.MARK`. Đây là output có chủ ý, không phải in nhầm/khởi tạo interface hai lần.

### 5.1. Phân tích nhóm thao tác không khởi tạo ngay

Bốn dòng đầu có liên quan tới class `Passive`:

```text
01 literal=Passive
02 constant=42
03 array=2
04 forNameFalse=true
```

**Chưa có** `init=Passive` trong bốn dòng này, vì:

- `Passive.class` lấy `Class<?>` mirror nhưng không yêu cầu chạy `<clinit>`.
- `Passive.CONST` là compile-time constant `42`, được dùng mà không cần đọc field runtime qua `getstatic`.
- `new Passive[2]` tạo **mảng chứa tham chiếu Passive**, không gọi `new Passive()` cho từng phần tử. Các ô của mảng lúc đầu là `null`.
- `Class.forName(name, false, loader)` yêu cầu tải/link class thích hợp nhưng **không chủ động initialization**.

Ở dòng 05 mới xuất hiện:

```text
init=Passive
05 getstatic=7
```

Đọc `Passive.number` là active use của static field không phải constant variable, nên initialization xảy ra trước khi giá trị `7` được in. Dòng 06 gọi static method `Passive.echo()` nhưng không in lại `init=Passive` vì class này đã khởi tạo.

### 5.2. Gán static field và tạo object

Các dòng:

```text
init=WriteTarget
07 putstatic=9
init=Constructed
08 new=object
```

cho thấy:

- **Gán** `WriteTarget.number = 9` yêu cầu initialize class trước khi phép gán hoàn tất, dù field chưa được đọc.
- `new Constructed()` yêu cầu initialize class trước việc tạo instance. Method `value()` được gọi trên instance đã có.

Đừng nhầm `new Constructed[5]` với `new Constructed()`: thao tác thứ nhất chỉ tạo **mảng**, không tạo năm đối tượng Constructed.

### 5.3. Superclass trước subclass, nhưng static field thuộc owner nào?

Khi truy cập `Child.value`, class Child phải khởi tạo và JVM bảo đảm superclass `Parent` được khởi tạo trước:

```text
init=Parent
init=Child
09 subclass=child
```

Ngược lại, khi truy cập `Derived.inherited`, field thực sự được khai báo trong `FieldOwner`. Kết quả:

```text
init=FieldOwner
10 inherited=11
```

**Không có `init=Derived`.** Đây là bằng chứng rằng tên subclass xuất hiện trong biểu thức source không tự làm subclass khởi tạo nếu member là static field do superclass khai báo. JLS §12.4.1 có ví dụ đúng trường hợp này.

### 5.4. Interface không có default method và interface có default method

Dòng 11:

```text
init=ImplementsPlain
11 plainImplement=ImplementsPlain
```

Khi khởi tạo `ImplementsPlain`, interface `Plain` không tự init chỉ do class implementing nó khởi tạo, vì Plain **không khai báo default method**. Khi code thật sự đọc `Plain.MARK`, vì MARK là field không phải constant variable, `Plain` mới init:

```text
init=Plain
12 plainField=init=Plain
```

Đối với interface `HasDefault`, nó khai báo `default String value()`. Khi class `ImplementsDefault` cần khởi tạo, JVM xử lý việc khởi tạo superinterface có default method cần thiết **trước** class:

```text
init=HasDefault
init=ImplementsDefault
13 defaultImplement=default
```

Không được suy rộng “tất cả interface được khởi tạo khi một class implements chúng”. Chỉ những superinterface thuộc nhóm được quy tắc JLS/JVMS yêu cầu khởi tạo cùng class. Một compiler đôi khi còn sinh **synthetic default methods** có liên quan đến việc xác định interface cần khởi tạo; vì vậy khi phân tích bytecode biến đổi, hãy xét định nghĩa class/interface thực tế.

### 5.5. Interface extends interface không tự init interface cha

Trong demo:

```java
interface Base {
    String MARK = trace("init=Base");
}
interface Extended extends Base {
    String MARK = trace("init=Extended");
}
```

Khi đọc `Extended.MARK`, chỉ interface **Extended** cần initialization cho field do nó khai báo:

```text
init=Extended
14 extendedField=init=Extended
```

**Không có `init=Base`.** Khởi tạo một interface **không tự kéo theo initialization của các superinterfaces**. Nếu sau đó thật sự đọc `Base.MARK`, Base có thể được khởi tạo vào thời điểm ấy.

### 5.6. Reflection: lấy metadata không giống invoke/get field

`Class.forName(..., true, ...)` chủ động initialize:

```text
init=ForNameTarget
15 forNameTrue=ForNameTarget
```

Nhưng `MethodTarget.class.getDeclaredMethod("run")` chỉ tra metadata nên dòng:

```text
16 reflectedMethodFound=true
```

xuất hiện mà chưa có `init=MethodTarget`. Khi gọi:

```java
method.invoke(null);
```

để thực thi **static method** được khai báo trong MethodTarget, reflection kích hoạt initialization trước:

```text
init=MethodTarget
17 methodInvoke=method
```

Tương tự, `getDeclaredField("VALUE")` không tự init, nhưng `Field.get(null)` trên static field dẫn đến:

```text
18 fieldFound=true
init=FieldTarget
19 fieldGet=99
```

Mặc dù `VALUE` là `public static final int VALUE = 99` (compile-time constant), **reflection read** vẫn có hợp đồng khởi tạo khác với biểu thức Java source được compiler inline. Đây là tình huống thường bị bỏ sót trong bảng tra cứu ngắn.

### 5.7. MethodHandle: phân biệt tìm handle và gọi handle

`MethodHandles.lookup().findStatic(HandleTarget.class, "run", ...)` **lấy method handle**. Thí nghiệm chưa in `init=HandleTarget` ở bước này:

```text
20 handleFound=true
```

Khi gọi `handle.invokeExact()`, JVM thực sự thực hiện static invocation, yêu cầu initialization:

```text
init=HandleTarget
21 handleInvoke=handle
```

**Không đánh đồng mọi thao tác của MethodHandles**: có API truy vấn handle, API đảm bảo initialized và API thực thi handle. Các thao tác có ngữ nghĩa khác nhau. Khi giải thích một call site cụ thể, nên xem invocation kind (static field getter/setter, static method, constructor), target và thời điểm thực thi, thay vì kết luận “thấy MethodHandle là init ngay”.

## 6. ClassLoader.loadClass, Class.forName và khởi tạo class

Một điểm cốt lõi với chủ đề ClassLoader:

```java
Class<?> loaded = loader.loadClass("pkg.Widget");
```

Với hành vi chuẩn, API `loadClass` **nạp/tìm** runtime class, có thể thực hiện linking theo các quy tắc liên quan, nhưng **không tự yêu cầu initialization**. Còn:

```java
Class<?> type = Class.forName("pkg.Widget", true, loader);
```

chủ động yêu cầu khởi tạo class `pkg.Widget`. Nếu class ấy chưa được initialized, code static liên quan sẽ được thực thi trước khi lời gọi hoàn thành bình thường.

Ba cách dùng:

| API | Chủ đích | Lưu ý |
| --- | --- | --- |
| `loader.loadClass("pkg.Widget")` | Tìm/nạp qua loader | Không tự init theo contract chuẩn; custom override có thể side effects |
| `Class.forName("pkg.Widget", false, loader)` | Tra class qua loader mà không init | Hữu ích cho scanner, kiểm tra metadata trước |
| `Class.forName("pkg.Widget", true, loader)` | Tra và bảo đảm initialization | Có thể gây side effect từ static initializer |

Với overload:

```java
Class.forName("pkg.Widget");
```

Java dùng ngữ cảnh caller để chọn loader phù hợp và **initialize class** theo hợp đồng. Không tự giả định lời gọi này luôn dùng `Thread.currentThread().getContextClassLoader()`. Để kiểm soát loader/initialization trong hệ plugin, dùng overload ba tham số với loader rõ.

### Vì sao false không bảo đảm “không có bất kỳ class nào khác initialized”?

`initialize=false` chỉ nói rằng **API không yêu cầu initialize chính class được yêu cầu**. Trong quá trình load/link, VM có thể load class phụ thuộc hoặc code custom ClassLoader thực thi side effects. Nếu custom loader **tự gọi** một static method của class khác, class khác có thể initialized. Bởi vậy:

- Không suy từ `Class.forName(... false ...)` rằng “không có initialization nào trong cả JVM”.
- Đừng lấy log constructor của một đối tượng khác làm bằng chứng class mục tiêu initialized.
- Khi cần kiểm thử sạch, dùng class riêng chưa đụng tới và log static initializer của chính nó.
- Xem stack trace/cause khi API thất bại ở linking hoặc initialization; hai nhóm lỗi khác nhau.

### Parent delegation không thay quy tắc initialization

Child loader có thể trả class do parent đã định nghĩa. Việc child trả class ấy **không chạy `<clinit>` một lần nữa**. Initialization gắn với **runtime class thật** được JVM quản lý, tức cùng binary name + defining loader. Nếu hai loader **tự định nghĩa hai runtime classes khác nhau** cùng binary name, chúng có trạng thái initialization riêng và có thể khởi tạo vào hai thời điểm khác nhau.

## 7. Bytecode liên quan: new, getstatic, putstatic, invokestatic

Khi đối chiếu source với bytecode, các instruction thường gắn với active use:

| Instruction | Mục đích | Có thể yêu cầu initialize? |
| --- | --- | --- |
| `new` | Tạo instance của class | **Có** đối với class được tạo |
| `getstatic` | Đọc static field | **Có** với field không phải constant variable / bytecode thực sự đọc field |
| `putstatic` | Gán static field | **Có** |
| `invokestatic` | Gọi static method | **Có** |
| `ldc` với String/int literal | Nạp hằng từ constant pool | **Không** tự init owner của constant variable từng xuất hiện trong source |
| `ldc` class literal | Nhận Class mirror | **Không** tự init class được biểu diễn |
| `anewarray` / `multianewarray` | Tạo array | **Không** tự init component class chỉ vì array creation |
| `checkcast` / `instanceof` | Kiểm tra/ép kiểu | **Không** tự init target class |
| `invokevirtual` cho instance method | Gọi instance method | **Không** tự init theo cùng nghĩa static active use | 
| `invokeinterface` cho instance method | Gọi method của instance qua interface | **Không** chỉ do instance call; object đã tồn tại và class thường đã init |

**Lưu ý về `getstatic` và constant variable:** biểu thức Java source `Settings.PORT` đối với hằng compile-time thường được compiler **inline**, nên bytecode của caller có thể không chứa `getstatic Settings.PORT`. Khi phân tích bytecode được sinh thủ công, không được bỏ qua ngữ nghĩa kích hoạt của một instruction `getstatic` thực sự được thực thi chỉ vì field trong class file mang `final`. Bảng JLS nói về cách truy cập thông thường từ Java source và JVMS mô tả kích hoạt ở mức instruction — phân biệt hai mức để tránh mâu thuẫn giả.

**Cách tự quan sát bytecode của ví dụ:**

```bash
javap -c -p InitActionsDemo
javap -c -p 'InitActionsDemo$Passive'
```

Hai lệnh dùng cú pháp trích dẫn single-quote phù hợp với Linux/macOS/Git Bash. Trên Windows PowerShell, cũng có thể quote tên nested class để dấu `$` không bị shell xử lý như biến; với `javap` nên chạy trong thư mục chứa các file `.class`.

Trong output `javap -c`, tìm `ldc`/constant, `getstatic`, `putstatic` và `invokestatic` theo đoạn tương ứng. Không suy initialization chỉ từ việc **constant pool có tham chiếu tới class**: cần xem instruction nào thực sự được thực thi và quy tắc JVM.

## 8. Thứ tự khởi tạo và tác động từ superclass/default interface

Khi class `Child` được khởi tạo, JVM tuân theo quy trình:

```text
Yêu cầu initialize Child
    |
    +-- Parent chưa init? -> initialize Parent
    |
    +-- superinterfaces khai báo default methods
    |   chưa init? -> initialize theo thứ tự quy định
    |
    +-- Child chạy static field initializers và static blocks
```

Một class có thể kế thừa nhiều interface gián tiếp. Trật tự và tập interface cần init được JLS 12.4.2 xác định chi tiết; không nên vẽ đơn giản “mọi interface trước mọi superclass”. Điều quan trọng trong thực hành là:

- **Superclass phải initialized trước subclass**.
- Khi class khởi tạo, những **superinterfaces khai báo default methods** thuộc phạm vi quy định phải initialized theo trình tự.
- Class implements một interface **chỉ có static field/abstract methods**, không có default methods, không bắt buộc init interface ấy chỉ vì class con khởi tạo.
- Khởi tạo interface **không tự** init superinterfaces.
- **Static field được khai báo ở kiểu nào thì chính kiểu đó** được init khi đọc field non-constant, không phải kiểu dùng để viết biểu thức.

Các tình huống này xuất hiện nhiều ở framework: interface khai báo biến static tạo singleton/registry có side effect; code có thể kích hoạt nó khi khởi tạo một implementation **nếu interface khai báo default method**, ngay cả trước khi phương thức default được gọi.

### Thứ tự khai báo static fields và static blocks

```java
class Ordering {
    static int a = print("a", 1);
    static {
        System.out.println("static block");
    }
    static int b = print("b", 2);

    static int print(String name, int value) {
        System.out.println(name);
        return value;
    }
}
```

Đây là source **minh họa**. Lần active use của Ordering dẫn tới chuỗi:

```text
a
static block
b
```

Static fields **không phải constant variables** được khởi tạo bằng các biểu thức thực thi theo source order. Đừng trộn điều này với **preparation**, giai đoạn runtime cấp các static fields giá trị mặc định trước initialization.

## 9. Đồng thời nhiều thread: chỉ một lượt initialization thành công

Khi hai thread lần đầu chạm cùng runtime class:

```text
Thread T1                            Thread T2
   |                                    |
   +-- yêu cầu initialize X              +-- yêu cầu initialize X
   |                                    |
   +-- JVM đánh dấu đang init X          +-- JVM chờ initialization của X
   |
   +-- chạy static fields / <clinit>
   |
   +-- hoàn thành thành công
                                        +-- tiếp tục sau khi X đã initialized
```

JLS 12.4.2 quy định **đồng bộ initialization theo từng runtime class/interface**, có trạng thái “chưa init”, “đang init”, “đã init” và “erroneous”. Nếu initialization cùng class được yêu cầu đệ quy bởi **chính thread đang thực hiện initialization**, JVM có quy tắc xử lý reentrant request; không đơn giản chạy `<clinit>` vô hạn lần.

Hệ quả:

- Static initialization của class thành công **chỉ diễn ra một lần cho định nghĩa runtime ấy**, không phải một lần mỗi thread.
- Hai defining ClassLoader khác nhau định nghĩa cùng binary name có thể dẫn tới **hai lượt initialization riêng**, vì đó là hai runtime classes.
- Nếu static initializer block mãi mãi hoặc đợi khóa theo chu trình giữa thread, chương trình có thể treo; ClassLoader không tự là hàng rào xử lý deadlock.
- Không dựa vào việc `System.out.println` trong static block luôn phản ánh toàn bộ ordering khi nhiều thread và nhiều lớp độc lập khởi tạo song song; mỗi class có quy tắc ordering riêng.

### Khi static initializer thất bại

Một class có static initializer ném exception:

```java
class Broken {
    static final int X = fail();

    static int fail() {
        throw new IllegalStateException("boom");
    }
}
```

Đây là mã **minh họa**. Lần đầu thực hiện một thao tác yêu cầu khởi tạo Broken (ví dụ truy cập `Broken.X`) thường có thể dẫn tới `ExceptionInInitializerError` bao bọc exception không phải Error. Class bị đánh dấu **erroneous** theo quy trình JLS, những yêu cầu initialize tiếp theo có thể nhận `NoClassDefFoundError` liên quan tới trạng thái lỗi.

Cần xem **cause và lần thất bại đầu tiên**, không tự sửa bằng cách gọi `Class.forName` lặp trên cùng defining loader rồi mong static block chạy lại. Với named class đã erroneous, thông thường phải sửa lỗi và quản lý lifecycle loader/class mới để có một định nghĩa runtime độc lập nếu kiến trúc cho phép.

## 10. Đọc log: lớp được “load” chưa đồng nghĩa được “initialized”

Nếu bật unified logging với JDK phù hợp:

```bash
java -Xlog:class+load=info -cp . InitActionsDemo
```

JVM ghi lại các sự kiện class loading. Log này hữu ích để thấy **lớp nào được nạp và có nguồn từ đâu**, nhưng không thay thế log static initialization. Một class có thể được load/link bởi reflection hoặc verifier mà chưa init. Muốn quan sát initialization qua logging, phải xác định tag/level hỗ trợ trên JVM đang dùng; không tự coi mọi dòng `class+load` là sự kiện `<clinit>`.

Trong ví dụ, chính các dòng `init=...` được in bởi static initializer hoặc initializer expression là bằng chứng rõ rằng **mã khởi tạo của lớp/interface đó thực sự chạy**.

### Một quy trình phân tích khi output bất ngờ

1. Xác định **runtime Class<?> và defining loader**, vì cùng tên class ở hai loader là hai trạng thái init khác nhau.
2. Kiểm tra class mục tiêu có static initializer hoặc static field initializer có side effect thật sự không. Class không có output không có nghĩa chưa initialized.
3. Xác định **thao tác active use đầu tiên**: `new`, static method, static field nonconstant, reflection hay Class.forName true.
4. Kiểm tra field thực sự do class/interface **nào khai báo**. Access qua subclass có thể init superclass chứ không init subclass.
5. Kiểm tra field có phải constant variable hay không; xem bytecode caller để nhận biết inline.
6. Nếu liên quan interface, kiểm tra default methods, synthetic defaults và quy tắc initialization của superinterfaces.
7. Phân biệt hành vi `getDeclaredMethod` với `Method.invoke`, `findStatic` với `invokeExact`, `forName false` với `forName true`.
8. Nếu class đã được initialized trước thí nghiệm, tạo **class khác** hoặc chạy một JVM mới; không kỳ vọng `<clinit>` tự chạy lại trong cùng runtime class.
9. Nếu có lỗi, đọc `ExceptionInInitializerError`/`NoClassDefFoundError` và original cause.
10. Nếu plugin nhiều classloader, bảo đảm không test một Class object từ parent rồi ghi nhận nó như định nghĩa của child.

## 11. Các lỗi hiểu nhầm phổ biến

| Nhận định sai | Sửa lại cho đúng |
| --- | --- |
| “ClassLoader.loadClass tức static block đã chạy.” | Loading khác initialization. Kiểm tra active use và `Class.forName` options. |
| “`T.class` chắc chắn kích hoạt `static {}`.” | Class literal không yêu cầu initialization. |
| “`static final` luôn không init khi đọc.” | Chỉ **constant variable** qua source access thường không init; reference values/runtime expressions khác. |
| “`new T[10]` tạo mười instance T.” | Tạo mảng chứa mười ô tham chiếu, thường ban đầu null. |
| “Có `getstatic` trong bytecode thì chắc chủ field được in trong source.” | Field declaring type mới là trọng tâm; source có thể dùng tên subclass để tham chiếu field owner khác. |
| “Khởi tạo một class luôn khởi tạo mọi interface nó implements.” | Chỉ những superinterface có default methods theo quy tắc. |
| “Khởi tạo một interface luôn khởi tạo interface cha.” | Không tự động. |
| “Đọc `Sub.parentField` bắt buộc init Sub.” | Có thể chỉ init Parent nếu field khai báo ở Parent. |
| “`Class.forName(name, false, loader)` chẳng làm gì.” | Nó có thể tải/link class nhưng không chủ động initialize target. |
| “`Class.forName(name)` giống hệt `loader.loadClass(name)`.” | Overload `forName` có hành vi initialization khác. |
| “Lấy Field/Method qua reflection đồng nghĩa thực thi code của owner.” | Lookup metadata khác với invoke/get/set runtime. |
| “Reading constant via Field.get không thể init.” | Reflection có hợp đồng initialization riêng; demo cho thấy `Field.get` init owner. |
| “Lấy MethodHandle static lập tức chạy class initializer.” | Tìm handle và invoke handle là hai việc khác nhau; xét API/call site. |
| “Static initializer chạy mỗi lần new object.” | Chỉ một lần khi init thành công cho một runtime class, không phải mỗi instance. |
| “Cùng class name ở hai loaders sẽ dùng chung static state.” | Hai defining loaders tạo hai runtime classes/statics riêng. |
| “JVM compile/link xong thì mọi static block của dependency chạy hết.” | Linking và initialization có quy tắc/timing khác nhau. |
| “ExceptionInInitializerError có thể chữa bằng gọi loadClass lại.” | Class ở trạng thái erroneous có quy tắc lỗi tiếp theo; tìm nguyên nhân khởi tạo. |

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `Sample.class` được lấy nhưng chưa có class static action nào. `<clinit>` đã chắc chắn chạy chưa?

**Đáp án:** Chưa. Class literal không phải trigger initialization.

**Câu 2.** `static final int X = 123;` và `static final Integer Y = 123;`. Đọc qua Java source thường có cùng tác động không?

**Đáp án:** Không. X là constant variable nếu biểu thức compile-time, thường được inline; Y là reference wrapper không thuộc kiểu primitive/String của constant variable, đọc Y là active use của owner.

**Câu 3.** `new Widget[3]` và `new Widget()` khác nhau thế nào đối với initialization?

**Đáp án:** Tạo array không bắt buộc Widget initialization; tạo instance Widget thì có.

**Câu 4.** `Child inheritedField` được truy cập qua tên Child nhưng field do Parent khai báo. Class nào được init do thao tác ấy?

**Đáp án:** Parent nếu chưa initialized; Child không tự init chỉ vì biểu thức field đó.

**Câu 5.** Interface I không khai báo default method. Khi khởi tạo class implements I, I có bắt buộc init không?

**Đáp án:** Không chỉ vì việc class implements nó. Nhưng đọc I.nonConstantField hoặc thao tác active use khác có thể init I.

**Câu 6.** Interface J extends I, đọc nonconstant static field do J khai báo. I có tự init theo không?

**Đáp án:** Không, initialization của J không tự kéo theo I.

**Câu 7.** `Class.forName(name, false, loader)` và `Class.forName(name, true, loader)` khác gì?

**Đáp án:** False không yêu cầu initialize target; true yêu cầu initialize target trước khi hoàn thành bình thường.

**Câu 8.** Vì sao reflection `Field.get(null)` của một `public static final int` có thể init owner dù source access thường không?

**Đáp án:** Reflection invocation có semantics yêu cầu initialize class trước khi lấy static field qua API; trong khi source constant variable có thể được compiler inline.

**Câu 9.** Nếu `getDeclaredMethod` trả Method object mà không in `init=Target`, nhưng `invoke(null)` lại in, có mâu thuẫn không?

**Đáp án:** Không. Tìm method metadata và thực thi method khác nhau.

**Câu 10.** Vì sao `HasDefault` init trước `ImplementsDefault` trong ví dụ?

**Đáp án:** Vì JVM khởi tạo những superinterface khai báo default methods theo JLS trước khi hoàn tất initialization của class.

**Câu 11.** Hai ClassLoaders độc lập định nghĩa cùng binary name T. Static initializer của T có dùng chung trạng thái không?

**Đáp án:** Không. Đó là hai runtime classes với hai defining loaders khác nhau, có trạng thái initialization riêng.

**Câu 12.** Khi Broken.<clinit> lỗi lần đầu, lần sau có chắc JVM thử chạy lại đầy đủ không?

**Đáp án:** Không. Class có thể ở trạng thái erroneous, và yêu cầu tiếp theo thường nhận NoClassDefFoundError. Cần phân tích nguyên nhân lần đầu.

## 13. Checklist khi lập bảng initialization cho ứng dụng thật

- [ ] Xác định class/interface **được khai báo** bởi thao tác, không chỉ tên dùng trong source.
- [ ] Tách loading, linking, initialization thành ba bước.
- [ ] Phân loại trigger: new, invokestatic, putstatic, getstatic nonconstant, reflection, MethodHandle invocation.
- [ ] Xác minh `static final` có thật là constant variable hay không.
- [ ] Kiểm tra class literal, array creation, cast, instanceof và `forName(false)` là các thao tác không tự init target.
- [ ] Kiểm tra parent class trước child class.
- [ ] Với interface, kiểm tra có default method và quan hệ superinterface.
- [ ] Không cho rằng interface initialization luôn kéo theo interface parent.
- [ ] Hiểu reflection lookup khác method/field invocation.
- [ ] Không nhầm static variable initialization với instance field/constructor initialization.
- [ ] Nếu cần test, dùng JVM mới và các class targets chưa khởi tạo.
- [ ] So sánh static output với bytecode qua `javap -c` khi nghi constant được inline.
- [ ] Khi có custom ClassLoader, ghi cả binary name và defining loader.
- [ ] Theo dõi lỗi init và trạng thái erroneous thay vì chỉ catch `ClassNotFoundException`.
- [ ] Không thiết kế side effect lớn/khó phục hồi trong static initializer.
- [ ] Xem JLS 12.4.1 và 12.4.2 là nguồn quyết định khi có tranh cãi về initialization timing.

## 14. Kết luận và tài liệu tham khảo

**Không phải mọi thao tác “đụng tới tên lớp” đều kích hoạt khởi tạo.** Java chỉ initialize class/interface khi các trigger cụ thể xảy ra hoặc khi có quan hệ initialization phụ thuộc theo quy tắc JVM. Trường hợp rõ nhất là `new T()`, static method, gán static field, đọc static field không phải constant variable và một số thao tác reflection. Ngược lại, `T.class`, `new T[n]`, `Class.forName(..., false, ...)`, đọc compile-time constant, cast và `instanceof` **không tự làm target initialized**.

Khi có kế thừa, điều kiện phụ thuộc trở nên tinh tế hơn: superclass trước subclass; superinterfaces có default methods được xét khi khởi tạo class; interface tự khởi tạo không có nghĩa mọi superinterface cũng khởi tạo. Cuối cùng, trạng thái init thuộc về **runtime class được định nghĩa bởi một defining loader**, không chỉ tên class trên giấy.

Nguồn chính thức để tra cứu:

- **JLS Java SE 17 §12.4.1 — When Initialization Occurs:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1
- **JLS Java SE 17 §12.4.2 — Detailed Initialization Procedure:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2
- **JVMS Java SE 17 §5.5 — Initialization:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- **JLS Java SE 17 §4.12.4 — final Variables and Constant Variables:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4
- **Java SE 17 `Class` API, `forName`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java SE 17 `Field` API, `get`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html
- **Java SE 17 `MethodHandles.Lookup` API:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-003">Bảng lỗi theo giai đoạn nạp, liên kết, khởi tạo và sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng lỗi theo giai đoạn nạp, liên kết, khởi tạo và sử dụng lớp Java

Khi ứng dụng Java báo lỗi ở lớp, classloader, dependency JAR, framework, plugin hoặc reflection, việc **nhìn tên exception mà sửa ngay classpath** thường không đủ. Một class đi qua nhiều bước khác nhau của JVM: **loading**, **linking** (verification, preparation, resolution), **initialization**, sau đó mới đến **sử dụng class/object/method**. Mỗi giai đoạn có những lỗi điển hình với nguyên nhân và cách khắc phục khác nhau.

Ví dụ:

- `ClassNotFoundException`: code **chủ động tìm class bằng tên** nhưng loader được chọn không tìm ra.
- `NoClassDefFoundError`: JVM cần class definition không tìm được theo tham chiếu bytecode, hoặc class trước đó đã initialization thất bại.
- `VerifyError`: bytecode không vượt được các kiểm tra verification.
- `NoSuchMethodError`: symbolic method reference không khớp method của **phiên bản class thực sự được nạp**.
- `ExceptionInInitializerError`: static initialization chạy rồi ném non-`Error` exception.
- `ClassCastException`: object không thể cast về runtime type được yêu cầu, đôi khi do cùng binary name nhưng **khác defining class loader**.

Bài này là **tài liệu tra cứu** theo giai đoạn, có bảng lỗi, ví dụ Java 17+ tái hiện những trường hợp phổ biến, lệnh chẩn đoán và quy trình khoanh vùng nguyên nhân. Các bảng mô tả **giai đoạn nguyên nhân**; JVM có quyền thực hiện một số hoạt động linking/resolution sớm hoặc muộn nên **thời điểm stack trace phát ra lỗi có thể không trùng chính xác với bước mà người viết dự đoán**.

## 1. Bản đồ giai đoạn Class Loading của JVM

Theo JVMS §5, luồng khái quát:

~~~text
    Source .java / library dependency / bytecode generator
                          |
                        javac
                          |
                     Class file bytes
                          |
                          v
                   (1) LOADING
           ClassLoader.loadClass/findClass
                          |
       find class representation, define runtime Class
                          |
                          v
                   (2) LINKING
         +----------------+------------------+
         |                |                  |
     Verification      Preparation        Resolution
     bytecode rules    static storage     symbolic refs
         |                |                  |
         +----------------+------------------+
                          |
                          v
                 (3) INITIALIZATION
             static fields / static blocks
                          |
                          v
                    (4) USAGE
         constructors, calls, reflection, casts,
          ServiceLoader, JNI, resource lookup
~~~

Thứ tự khái niệm không có nghĩa tất cả symbolic references **đều** phải được resolve hết trước khi class initialized. JVMS cho phép eager/lazy resolution tùy quy tắc; một số `LinkageError` có thể chỉ xuất hiện khi chương trình **thực sự gọi method hoặc truy cập field** trong một code path.

**Điểm cốt lõi:** Nếu exception phát sinh khi gọi `foo.bar()`, nó có thể là **lỗi linking/resolution** dù class `Foo` đã được loaded/initialized từ trước.

## 2. Bảng tra cứu nhanh theo giai đoạn

| Giai đoạn / tình huống | Throwable tiêu biểu | Dấu hiệu | Điều tra trước |
|---|---|---|---|
| Explicit class lookup | `ClassNotFoundException` | `Class.forName`/`loadClass` theo tên thất bại | Binary name, caller/explicit loader, classpath/URL |
| JVM cần dependency class | `NoClassDefFoundError` | Cần definition khi link/use nhưng thiếu, hoặc init từng fail | `Caused by`, runtime dependencies, init history |
| Derive class từ bytes | `ClassFormatError` | Class file cấu trúc không hợp lệ | Bytes/JAR, generator và corruption |
| Unsupported class-file version | `UnsupportedClassVersionError` | Bytecode build bằng version mới hơn runtime | `javac --release` và `java -version` |
| Verification | `VerifyError` | Bytecode không thỏa type-safety/stack-map rules | Compiler/instrumentation/transformer |
| Inheritance loading | `ClassCircularityError` | Superclass/interface graph circular bất hợp lệ | Bytecode/inheritance graph |
| Resolution method | `NoSuchMethodError` | Method name + descriptor không có ở runtime type | ABI và version của defining loader |
| Resolution field | `NoSuchFieldError` | Field symbol thiếu sau khi đổi version | Field name/type/staticness/runtime JAR |
| Binary compatibility | `IncompatibleClassChangeError` | Thay đổi không tương thích class/interface/instance/static shape | Compiler vs runtime ABI |
| Invocation | `AbstractMethodError` | Call không có concrete implementation tương thích | Version skew/interface/class hierarchy |
| Access checking | `IllegalAccessError` | JVM từ chối truy cập method/field/class | Runtime package, loader identity, module exports |
| Static initialization | `ExceptionInInitializerError` | Static block/field initializer ném non-Error exception | Root cause trong `<clinit>` |
| Previous failed initialization | `NoClassDefFoundError` | `Could not initialize class ...` ở lần sau | **Lỗi initializer đầu tiên** |
| Reflective lookup | `NoSuchMethodException`/`NoSuchFieldException` | Reflection tìm member theo name/signature không thấy | Java reflection API và đúng signature |
| Reflective accessibility | `IllegalAccessException`/`InaccessibleObjectException` | Reflection bị chặn bởi visibility/module | `public`, `exports`, `opens`, caller |
| Runtime cast | `ClassCastException` | Object không phải instance của runtime target type | `Class.getClassLoader()` của cả hai |
| Array store | `ArrayStoreException` | Ghi object sai runtime component type | Component type và array covariance |
| Native linking | `UnsatisfiedLinkError` | Load JNI binary hoặc native symbol sai | `java.library.path`, ABI, loader ownership |
| Service discovery | `ServiceConfigurationError` | Provider descriptor/type/constructor lỗi | `META-INF/services`, TCCL, module uses/provides |
| Bootstrap method | `BootstrapMethodError` | Bootstrap của `invokedynamic`/dynamic constant fail | Generated bytecode và bootstrap cause |
| Classloader resource exhaustion | `OutOfMemoryError` | Heap/metaspace/native limit | Memory/GC/loader leaks và allocations |

Các bảng là **gợi ý khoanh vùng**, không phải cam kết một throwable chỉ xuất hiện duy nhất ở một bước. Ví dụ `NoClassDefFoundError` có thể xuất hiện do missing dependency **hoặc** class erroneous sau initialization failure.

## 3. Loading: tìm class bytes và tạo runtime class

### 3.1. `ClassNotFoundException` là checked exception của explicit lookup

~~~java
Class<?> type = Class.forName("com.example.plugin.Entry");
~~~

Nếu caller's loader không tìm được tên `com.example.plugin.Entry`, lời gọi có thể ném `ClassNotFoundException`. Đây là **checked exception**, cần catch hoặc `throws`.

Các API hay dùng:

~~~java
Class.forName("p.Entry");
Class.forName("p.Entry", false, pluginLoader);
pluginLoader.loadClass("p.Entry");
~~~

Đây là **mảnh minh họa**; biến `pluginLoader` cần được khai báo. Overload một tham số của `Class.forName` dùng loader của caller theo quy tắc API; overload có `ClassLoader` dùng loader được truyền. `ClassLoader.loadClass` thông thường không yêu cầu initialize ngay, còn `Class.forName(String)` mặc định yêu cầu initialize.

**Những nguyên nhân phổ biến:**

- Binary name sai: dùng `p/Entry.class` thay vì `p.Entry`.
- Sai package capitalization, nested class phải dùng `Outer$Inner`.
- Thư viện có trong build classpath nhưng **không** ở runtime classpath.
- Host class loader không nhìn thấy child plugin JAR.
- `java -jar` dùng manifest/classpath semantics khác `java -cp`.
- Plugin JAR nằm ở URL khác hoặc chưa copy file class vào artifact.
- JPMS named module chưa được resolved trong layer cần thiết.

**Cách xử lý:** xác minh name + loader + artifact roots trước khi thay global classpath.

### 3.2. `NoClassDefFoundError`: không luôn đồng nghĩa CNFE

Một class `Main` đã compile với:

~~~java
MissingLibrary.run();
~~~

nhưng runtime thiếu `MissingLibrary.class`. JVM giải quyết symbolic reference ở code path cần class và có thể ném `NoClassDefFoundError`. Throwable này là một **`LinkageError`**, không phải checked exception.

Thông thường cause chain có thể chứa:

~~~text
java.lang.NoClassDefFoundError: p/MissingLibrary
Caused by: java.lang.ClassNotFoundException: p.MissingLibrary
~~~

Đây là **dạng thông báo minh họa**, stack trace cụ thể phụ thuộc JDK và loader. `NoClassDefFoundError` còn xuất hiện khi class đã bị đánh dấu erroneous sau một initialization failure — nguyên nhân khác hẳn thiếu file.

**Cảnh báo:** Không catch CNFE rồi mặc định rằng mọi `NoClassDefFoundError` sẽ biến mất; phải xác định root cause và phase.

### 3.3. `ClassFormatError` và `UnsupportedClassVersionError`

- `ClassFormatError`: class-file bytes không hợp lệ theo cấu trúc JVM class file, có thể do JAR hỏng, bytecode generator hoặc instrumentation tạo bytes sai.
- `UnsupportedClassVersionError` là subtype của `ClassFormatError`, thường do class file version mới hơn VM hỗ trợ.

Chẩn đoán:

~~~bash
java -version
javac -version
javap -verbose -classpath libs/plugin.jar p.Entry
~~~

`javap -verbose` cho biết `major version` của class khi tool đọc được artifact. Không nên kết luận dựa trên version của source file; compiler `--release`/target mới quyết định bytecode version.

Một cách build tương thích JDK 17:

~~~bash
javac --release 17 -d out src/p/Entry.java
~~~

Lệnh này là mảnh minh họa yêu cầu source đúng path. Với JDK hỗ trợ target 17, nó tạo class file version tương ứng Java 17 và hạn chế API reference theo target release. Nhưng **dependency runtime có native/API version khác** vẫn có thể gây lỗi khác.

### 3.4. `ClassCircularityError`

Khi superclass/superinterface graph bất hợp lệ tạo vòng lặp mà JVM phát hiện trong quá trình loading/linking, `ClassCircularityError` có thể xảy ra. Mã nguồn Java thông thường đã được compiler ngăn nhiều dạng circular inheritance; lỗi này thường liên quan tới bytecode generated/instrumented hoặc classloader có hành vi bất thường.

Không sửa bằng cách tạo thêm loader tùy tiện; kiểm tra **binary class hierarchy**, cycle và generator/build tool.

## 4. Linking gồm verification, preparation và resolution

### 4.1. Verification — `VerifyError`

JVM cần xác nhận bytecode thỏa các ràng buộc về kiểu, operand stack, control flow và class file verification. Ví dụ một transformer sửa bytecode nhưng không cập nhật stack map frames/bytecode metadata phù hợp có thể gây:

~~~text
java.lang.VerifyError: Bad type on operand stack
~~~

Đây là ví dụ wording, không phải output cố định. Những hướng kiểm tra:

- JVM/classfile version đúng không?
- Bytecode generator/ASM/Byte Buddy phiên bản có hỗ trợ target không?
- StackMapTable/branch targets/instruction signatures có hợp lệ không?
- Có Java agent/transformer khác sửa bytes sau compile không?
- Test class file trước và sau instrumentation.

**Đừng tắt bytecode verification trong production** chỉ để vượt lỗi; làm mất lớp bảo vệ type safety.

### 4.2. Preparation — static fields và bộ nhớ

Trong preparation, JVM cấp phát và khởi tạo giá trị mặc định cho những static fields theo quy tắc class preparation (không nhầm với việc chạy các initializer expressions Java trong `<clinit>`). Các lỗi có thể liên quan `OutOfMemoryError` hoặc failures khi chuẩn bị cấu trúc.

Ví dụ:

~~~java
static int count = compute();
~~~

`compute()` không được thực thi bởi preparation như một Java initializer thông thường; nó chạy ở **initialization** khi class được khởi tạo.

Cần phân biệt:

- **Preparation:** tạo trạng thái static mặc định.
- **Initialization:** chạy static field initializers và static blocks theo thứ tự Java.
- **Compile-time constants:** có quy tắc đặc biệt theo JVMS/JLS; đừng giả định mỗi lần đọc constant đều cần initialize class.

### 4.3. Resolution — symbolic references sang runtime entities

Bytecode có các symbolic references tới classes, methods, fields và dynamic call sites. Khi JVM resolve chúng, tên/symbol phải khớp với runtime definitions và access constraints.

Lỗi thường gặp:

| Lỗi | Nguyên nhân điển hình | Cần so sánh |
|---|---|---|
| `NoSuchMethodError` | Runtime class thiếu method đúng descriptor | Method name, params, return descriptor, runtime JAR |
| `NoSuchFieldError` | Runtime class thiếu field tương ứng | Field name/type và binary version |
| `IllegalAccessError` | Access check bị từ chối | `public`/package-private, loader, module |
| `IncompatibleClassChangeError` | Method/class/interface shape không tương thích | Instance vs static, class vs interface, ABI |
| `AbstractMethodError` | Không có concrete implementation phù hợp lúc invocation | Interface evolution, class hierarchy, version skew |
| `BootstrapMethodError` | Bootstrap method của dynamic call site/constant thất bại | Bootstrap target và nested cause |

**Quan trọng:** JVM có thể resolve references khi lần đầu sử dụng chúng, không phải ngay khi class được nạp. Do đó một plugin có thể khởi động thành công nhưng khi gọi một chức năng ít dùng mới phát sinh `NoSuchMethodError`.

### 4.4. Tại sao những lỗi này thường xuất hiện sau khi update thư viện?

Giả sử:

~~~text
Compile-time:
  api.Library v2 có static String extra()

Runtime:
  api.Library v1 không có extra()
~~~

Code `Client` compile được với v2, nhưng application loader thực tế nạp v1. Khi bytecode thực thi `invokestatic api.Library.extra()`, method không tồn tại ở runtime type và JVM ném `NoSuchMethodError`.

Không khắc phục bằng cách đổi `Class.forName` overload khi class đã nạp đúng tên nhưng **sai version/ABI**. Cần sửa classpath/container dependency version hoặc loader ownership.

## 5. Demo Java 17+ tái hiện NoSuchMethodError do version skew

Thí nghiệm cố ý compile `Client` với Library v2 nhưng chạy với v1.

### 5.1. Cấu trúc project

~~~text
project/
  src/
    v1/api/Library.java
    v2/api/Library.java
    client/Client.java
  out/
    v1/
    v2/
    client/
~~~

### 5.2. `src/v1/api/Library.java`

~~~java
package api;

public final class Library {
    public static String hello() {
        return "v1";
    }
}
~~~

### 5.3. `src/v2/api/Library.java`

~~~java
package api;

public final class Library {
    public static String hello() {
        return "v2";
    }

    public static String extra() {
        return "new-api";
    }
}
~~~

### 5.4. `src/client/Client.java`

~~~java
import api.Library;

public final class Client {
    public static void main(String[] args) {
        System.out.println(Library.extra());
    }
}
~~~

### 5.5. Build và chạy

~~~bash
mkdir -p out/v1 out/v2 out/client

javac -d out/v1 src/v1/api/Library.java
javac -d out/v2 src/v2/api/Library.java

javac -cp out/v2 -d out/client src/client/Client.java

java -cp "out/client:out/v1" Client
~~~

Lệnh chạy **cố ý** dùng v1 thay v2. Kết quả là lỗi thuộc dạng:

~~~text
Exception in thread "main" java.lang.NoSuchMethodError: 'java.lang.String api.Library.extra()'
~~~

Đây là **dòng chính minh họa**; stack trace sẽ còn vị trí source và có thể thay đổi chữ ký trình bày tùy JVM. Nếu chạy đúng runtime dependency v2:

~~~bash
java -cp "out/client:out/v2" Client
~~~

Stdout:

~~~text
new-api
~~~

**Bài học:** class `api.Library` vẫn tồn tại và được nạp; vấn đề là **method symbol không khớp runtime definition**. Nó không phải `ClassNotFoundException`.

Trên Windows, classpath separator ở các lệnh `-cp` cần là dấu `;` thay vì `:`.

## 6. Initialization — chạy static blocks và static field initializers

Một class được initialized khi active use hoặc thao tác API yêu cầu theo quy tắc JLS/JVMS. `Class.forName(name, true, loader)` yêu cầu initialization; `Class.forName(name, false, loader)` không yêu cầu.

### `ExceptionInInitializerError`

Nếu static block hoặc static field initializer ném một exception không phải `Error`, JVM thường bọc exception thành `ExceptionInInitializerError`.

~~~java
final class Config {
    static final String VALUE = load();

    static String load() {
        throw new IllegalStateException("bad config");
    }
}
~~~

Đây là class minh họa, chưa có `main`. Khi class được initialized, lỗi gốc có thể là `IllegalStateException` nhưng Throwable ngoài cùng là `ExceptionInInitializerError`. **Đọc `getCause()`** để thấy nguyên nhân thực.

Nếu initializer đã ném một `Error` như `UnsatisfiedLinkError` từ `System.loadLibrary`, Error có thể được propagate trực tiếp, không nhất thiết bị bọc thành `ExceptionInInitializerError`.

### Lần sử dụng sau — `NoClassDefFoundError`

Một runtime class đã thất bại initialization có thể bị đánh dấu **erroneous**. Với cùng defining loader/runtime class, các lần active use sau thường ném:

~~~text
java.lang.NoClassDefFoundError: Could not initialize class ...
~~~

**Không có nghĩa class-file bỗng dưng biến mất.** Muốn sửa, phải tìm **lỗi initializer đầu tiên** và sửa config/dependency/logic; tạo loader generation mới nếu cần nạp định nghĩa class mới. Không retry initializer vô hạn trên cùng erroneous class.

### Nguyên nhân thường gặp của init failure

- Đọc file cấu hình thiếu, environment variable không hợp lệ.
- Static initializer gọi network/database rồi throw.
- Static code dùng dependency method không tương thích.
- `System.loadLibrary` thất bại do JNI binary hoặc duplicate load.
- Circular static initialization tạo trạng thái không như dự định.
- Một static cache tạo resource lớn gây memory issue.

**Thiết kế tốt hơn:** Giảm side effects trong static initializer; dùng factory/lifecycle method có error handling rõ ràng khi khởi tạo plugin/service.

## 7. Demo Java 17+ phân biệt 4 loại lỗi trực tiếp

File độc lập **`PhaseErrorsDemo.java`** dưới đây tạo các trường hợp có chủ đích. Program chỉ bắt rộng `Throwable` trong test harness để in tên lỗi và không che sự khác nhau giữa các tình huống; **không** áp dụng kỹ thuật catch broad này trong production business logic.

### 7.1. Toàn bộ file `PhaseErrorsDemo.java`

~~~java
public final class PhaseErrorsDemo {

    static final class Exploding {
        static {
            if (alwaysFail()) {
                throw new IllegalStateException("broken initializer");
            }
        }

        static boolean alwaysFail() {
            return true;
        }
    }

    public static void main(String[] args) {
        // 1. Explicit lookup một class không có.
        try {
            Class.forName("missing.DoesNotExist");
        } catch (ClassNotFoundException ex) {
            System.out.println("lookup: "
                    + ex.getClass().getSimpleName());
        }

        // 2. Lần active use đầu tiên: initializer exception.
        try {
            Class.forName(
                    Exploding.class.getName(),
                    true,
                    PhaseErrorsDemo.class.getClassLoader());
        } catch (ExceptionInInitializerError ex) {
            System.out.println("init first: "
                    + ex.getClass().getSimpleName());
        }

        // 3. Lần sau: class ở trạng thái erroneous.
        try {
            Class.forName(
                    Exploding.class.getName(),
                    true,
                    PhaseErrorsDemo.class.getClassLoader());
        } catch (NoClassDefFoundError ex) {
            System.out.println("init second: "
                    + ex.getClass().getSimpleName());
        }

        // 4. Object có runtime type String không thể thành Integer.
        Object value = "hello";

        try {
            Integer number = (Integer) value;
            System.out.println(number);
        } catch (ClassCastException ex) {
            System.out.println("cast: "
                    + ex.getClass().getSimpleName());
        }
    }
}
~~~

### 7.2. Biên dịch và chạy

~~~bash
javac PhaseErrorsDemo.java
java PhaseErrorsDemo
~~~

**Stdout dự kiến trên JDK 17+:**

~~~text
lookup: ClassNotFoundException
init first: ExceptionInInitializerError
init second: NoClassDefFoundError
cast: ClassCastException
~~~

**Giải thích:**

1. `missing.DoesNotExist` không có trong loader của caller nên explicit lookup ném checked `ClassNotFoundException`.
2. `Exploding.class` chỉ lấy class literal, không yêu cầu initialize. `Class.forName(..., true, ...)` lần đầu chạy static block; `alwaysFail()` trả `true` và `IllegalStateException` bị bọc thành `ExceptionInInitializerError`.
3. JVM không tự chạy lại initializer của cùng erroneous class như một class mới; lần active use tiếp theo gây `NoClassDefFoundError`.
4. Object String và runtime type Integer không tương thích nên cast ném `ClassCastException` ở **giai đoạn sử dụng**.

Dòng 2–3 chứng minh vì sao phải lưu **stack trace của lần thất bại đầu tiên**: nếu logs chỉ bắt đầu ở lần retry, người vận hành có thể nhìn `NoClassDefFoundError` và tưởng thiếu file class.

## 8. Usage — lỗi xuất hiện dù loading và initialization thành công

### 8.1. `ClassCastException` và defining class loader

Hai class có cùng binary name nhưng được hai defining loaders khác nhau định nghĩa là **hai runtime types khác nhau**:

~~~text
(com.acme.Plugin, LoaderA) != (com.acme.Plugin, LoaderB)
~~~

Một object tạo bởi LoaderA không thể tự động cast thành implementation type do LoaderB định nghĩa, dù bytecode giống nhau. Trong hệ plugin, lỗi này thường do:

- Shared SPI/interface bị child-first loader định nghĩa lại.
- Host và plugin đóng gói hai bản API cùng tên.
- Classloader hierarchy không delegate đúng parent.
- Object từ generation cũ được truyền sang generation mới.

**Fix:** đưa shared API/DTOs vào loader cha chung và giao tiếp qua những runtime types thống nhất. Không dùng `--add-opens` để “sửa” class identity khác loader; module openness không hợp nhất hai loại `Class<?>`.

### 8.2. `NoSuchMethodException` khác `NoSuchMethodError`

Reflection:

~~~java
String.class.getMethod("methodThatDoesNotExist");
~~~

Ném **checked `NoSuchMethodException`** vì người gọi đang tìm method theo tên và signature bằng API reflection.

Ngược lại, bytecode `invokevirtual`/`invokestatic` tham chiếu method mà runtime type không có có thể gây **`NoSuchMethodError`**. Đây là `LinkageError`, thường do binary incompatibility.

**Không dùng hai tên lỗi này thay thế cho nhau** khi tìm hướng xử lý.

### 8.3. `IllegalAccessException` và `IllegalAccessError`

`IllegalAccessException` thuộc checked exceptions của reflection khi caller không được phép truy cập method/constructor/field theo access checks.

`IllegalAccessError` thuộc `LinkageError`, có thể xảy ra khi bytecode truy cập member/class không còn accessible ở runtime, chẳng hạn:

- Method từng `public` khi compile nhưng runtime version giảm visibility.
- Hai class cùng source package bị chia vào **different defining loaders**, nên không còn cùng runtime package cho package-private access.
- Một module export/access change phá rule bytecode cần.

Với JPMS reflection, `InaccessibleObjectException` là **runtime exception** có thể phát sinh khi `setAccessible(true)` bị từ chối do module encapsulation. Không chỉ cần “đúng tên” class mà còn phải tuân thủ exports/opens.

### 8.4. `ArrayStoreException`

Java arrays có runtime component type; khi lưu object không assignable vào reference array:

~~~java
Object[] elements = new String[1];
elements[0] = 123;
~~~

Đây là **mảnh minh họa**, chạy gây `ArrayStoreException`. Class loading và linking có thể hoàn toàn đúng; lỗi thuộc hành vi sử dụng/mutation của array.

### 8.5. `ServiceConfigurationError`

`ServiceLoader` đọc service provider config hoặc module `provides` và tìm/khởi tạo provider lazy. Các lỗi:

- `META-INF/services/<SPI binary name>` sai tên.
- Descriptor chứa provider binary name không tồn tại.
- Provider không assignable về runtime SPI interface.
- Constructor/factory method không đúng contract.
- TCCL hoặc explicit loader không thấy plugin.
- Module provider chưa resolved hoặc consumer thiếu `uses` theo module contract.

Có thể ném `ServiceConfigurationError` khi iterate/stream, không phải lúc `ServiceLoader.load(...)` tạo instance. Đây là **Error do service discovery**, có thể chứa CNFE/linkage/init failures làm cause.

### 8.6. `UnsatisfiedLinkError`

Native linkage có đường xử lý riêng:

~~~java
System.loadLibrary("codec");
~~~

Đây là mảnh minh họa, yêu cầu JNI binary thật. Nếu không tìm thấy library, sai OS/ABI/architecture, thiếu native dependency, JNI symbol hoặc native library đã thuộc classloader khác, có thể ném `UnsatisfiedLinkError`.

JNI library trong nhiều child loaders có **ownership rule**; thêm Java classpath không phải cách sửa cross-loader native-library conflict. Thường cần shared JNI bridge ở parent hoặc tách process khi native state không thể chia sẻ.

## 9. Bảng so sánh những cặp dễ nhầm

| Cặp lỗi | Sự khác nhau chủ yếu | Ví dụ nhận diện |
|---|---|---|
| `ClassNotFoundException` vs `NoClassDefFoundError` | Explicit string lookup (checked) vs JVM class definition/linkage/previous init (Error) | `Class.forName("missing.X")` vs bytecode `new X()` thiếu dependency |
| `NoSuchMethodException` vs `NoSuchMethodError` | Reflection lookup checked vs symbolic method resolution binary mismatch | `getMethod("missing")` vs build v2/run v1 |
| `IllegalAccessException` vs `IllegalAccessError` | Reflection API access denied vs bytecode access/linkage denied | `getMethod.invoke` vs runtime package split |
| `ExceptionInInitializerError` vs `NoClassDefFoundError` after init | Lần đầu static non-Error exception vs lần sau class erroneous | Demo `Exploding` |
| `ClassFormatError` vs `VerifyError` | Class-file structure invalid vs bytecode fails verifier constraints | Broken format vs invalid operand stack |
| `ClassCastException` vs `IllegalAccessError` | Runtime type mismatch vs bytecode access denied | Duplicate SPI classes vs package-private split |
| `UnsatisfiedLinkError` vs `ClassNotFoundException` | Native library/method linkage vs Java class name lookup | `System.loadLibrary` vs `loadClass` |
| `OutOfMemoryError` vs classloader leak | Throwable là resource exhaustion; leak chỉ là một khả năng nguyên nhân | Metaspace tăng + retained loaders |

Một error message có thể chứa **nhiều loại Throwable trong cause chain**. Hãy phân loại **outermost error + root cause + first failure**, không chỉ tìm chữ “class” trong thông báo.

## 10. Cách xác định giai đoạn từ stack trace/cause chain

### Bước 1 — Xác định thao tác đang chạy

- `Class.forName`/`ClassLoader.loadClass`: explicit name lookup, có initialization flag không?
- Constructor call/`new`: có cần linking/init không?
- `invokestatic`/`invokevirtual`: method resolution/invocation.
- `getstatic`/`putstatic`: field resolution/initialization.
- `ServiceLoader` iterator: provider discovery/instantiate.
- `System.load`/`native`: JNI loader/symbol.
- Reflection `getMethod`/`invoke`: reflective lookup/access/target invocation.

### Bước 2 — Đọc cause chain đầy đủ

Ví dụ:

~~~text
ExceptionInInitializerError
  caused by IllegalStateException: missing config
~~~

Fix thiếu config/static init, **không** thêm JAR ngẫu nhiên.

Ví dụ:

~~~text
NoClassDefFoundError: p/Dependency
  caused by ClassNotFoundException: p.Dependency
~~~

Fix runtime dependency/loader visibility.

Ví dụ:

~~~text
ServiceConfigurationError
  caused by provider not found / not assignable
~~~

Fix provider descriptor/visibility/runtime SPI identity.

### Bước 3 — In classloader và nguồn class

Khi đã có `Class<?>`:

~~~java
Class<?> type = object.getClass();
ClassLoader loader = type.getClassLoader();

System.out.println("type=" + type.getName());
System.out.println("loader=" + loader);
System.out.println("module=" + type.getModule());
~~~

Đây là mảnh minh họa với `object` khác null. `getClassLoader() == null` với bootstrap classes là bình thường.

Nếu CodeSource khả dụng:

~~~java
var domain = type.getProtectionDomain();
var codeSource = domain == null ? null : domain.getCodeSource();

System.out.println(codeSource == null
        ? "source unknown"
        : codeSource.getLocation());
~~~

Không phải class nào cũng có CodeSource hữu ích; có thể `null`. Với error chưa load được class, bắt đầu từ **caller loader, binary name và actual runtime URLs**.

### Bước 4 — Kiểm tra artifact/ABI

~~~bash
jar --list --file libs/plugin.jar
javap -classpath libs/plugin.jar -public p.Api
javap -classpath libs/plugin.jar -verbose p.Api
~~~

Các lệnh là ví dụ chẩn đoán, cần JAR và class tương ứng. `javap -public` có thể kiểm tra method signatures, `-verbose` xem class-file major version và constant pool; đừng chỉ tin dependency tree lúc build.

### Bước 5 — JVM logging/JFR

Trên HotSpot Java 17:

~~~bash
java -Xlog:class+load=info,class+unload=info \
    -cp out/app Main
~~~

Đây là **lệnh chẩn đoán**, không phải stdout cố định. Class-loading logs/JFR giúp thấy class/source/load/unload behavior nhưng không tự giải thích mọi access/cast/init/linkage errors. Khi nghi leak, phối hợp classloader stats, metaspace metrics, thread/TCCL inspection và heap dump.

## 11. Phân biệt lỗi do classpath, loader identity và module access

### Classpath/configuration

Một class `p.Feature` có thể nằm ở `plugins/p/Feature.class` nhưng JVM chỉ chạy với `-cp out/app`. Explicit lookup qua app loader không thấy nó và CNFE. Dùng đúng root URL/classpath hoặc explicit plugin loader.

### Loader identity

Hai copies cùng `api.Service` trong host và child-first plugin không phải cùng runtime interface. Cast/provider verification có thể fail dù class file tồn tại ở cả hai nơi.

### JPMS module access

Một class trong named module có thể tồn tại và được resolved, nhưng package không exported/opened phù hợp cho caller. Điều này không đồng nghĩa luôn CNFE; có thể gặp `IllegalAccessError`, `IllegalAccessException` hoặc `InaccessibleObjectException` tùy API/phase.

**Không dùng `--add-opens` như “fix mọi lỗi class loading”**; nó không đổi class identity và không thêm thiếu methods/dependencies.

## 12. Binary compatibility và change matrix

| Thay đổi giữa compile/runtime | Nguy cơ |
|---|---|
| Xóa method hoặc đổi method descriptor | `NoSuchMethodError` |
| Xóa field/đổi descriptor | `NoSuchFieldError` |
| Đổi method từ static sang instance hoặc ngược lại | `IncompatibleClassChangeError` hoặc subtype liên quan |
| Đổi class thành interface hoặc ngược lại | `IncompatibleClassChangeError` |
| Thay visibility khiến caller không access được | `IllegalAccessError` |
| Thêm abstract method nhưng runtime implementation không thỏa | `AbstractMethodError` khi invocation |
| Thay superclass/interfaces không nhất quán | Có thể `IncompatibleClassChangeError` hoặc lỗi liên quan |
| Build class file cho Java cao hơn runtime | `UnsupportedClassVersionError` |
| Instrumentation làm bytecode invalid | `VerifyError` hoặc `ClassFormatError` |
| Duplicate shared API ở nhiều loaders | `ClassCastException`, `LinkageError`/provider errors |
| Native library/method ABI không hợp lệ | `UnsatisfiedLinkError` |

Đây là bảng **nguy cơ thường gặp**, không đảm bảo mỗi thay đổi chỉ dẫn tới đúng một throwable duy nhất. JVM resolution/invocation rules và code path cụ thể quyết định lỗi được quan sát.

### Tại sao chỉ compiler/test unit vẫn chưa đủ?

Mã `Client` biên dịch với dependency v2, nhưng production launcher/container dùng v1. Compile-time classpath **khác runtime loader graph**, nên unit test có thể pass còn deployment ném `NoSuchMethodError`.

**Nên test cuối cùng với artifact và startup command thực tế**: parent classloader, child loaders, module path, JAR order, Java runtime version, resources và providers.

## 13. Quy trình xử lý lỗi theo nhóm

### A. Explicit lookup thất bại

1. Ghi chuỗi binary name thực sự truyền vào API.
2. Xác định overload `Class.forName`/`loadClass` và loader đang dùng.
3. Kiểm tra `jar --list`/directory root.
4. Xem launcher `-cp`, `-jar`, JPMS module graph.
5. Dùng explicit loader khi class được sở hữu bởi plugin child; đừng phá shared API boundary.

### B. Linking/ABI thất bại

1. Tìm method/field/class symbol trong exception.
2. Xác định defining loader của class thực tế được JVM nạp.
3. So runtime CodeSource/JAR với compile-time version.
4. So binary descriptors bằng `javap`.
5. Sửa version alignment hoặc selective loader policy nếu private dependencies cần bản riêng.

### C. Initialization thất bại

1. Tìm **lỗi đầu tiên** trong `<clinit>`.
2. Đọc underlying cause: config, JNI, DB, static code, dependency.
3. Không retry initializer vô hạn trên cùng erroneous class.
4. Sau khi sửa, dùng JVM/plugin generation phù hợp theo lifecycle.

### D. Runtime sử dụng/cast/access thất bại

1. So runtime `Class<?>` identity, không chỉ tên.
2. Xác minh shared SPI/DTO/interface từ một defining loader.
3. Kiểm tra reflection access/module exports/opens nếu liên quan.
4. Đừng đổi classpath mù quáng khiến thêm duplicate classes.

### E. Native/service discovery thất bại

1. JNI: kiểm tra lib path, OS/architecture, symbols, cross-loader native library ownership.
2. SPI: kiểm tra `META-INF/services` hoặc `uses`/`provides`.
3. Kiểm tra TCCL/explicit loader, provider class và constructor.
4. Chạy test thật sự instantiate provider/native call.

## 14. Anti-pattern khi chẩn đoán

**Bắt mọi Throwable rồi bỏ qua:**

~~~java
try {
    Class.forName(configuredName);
} catch (Throwable ignored) {
    // Sai: che hết linkage, init, memory, native...
}
~~~

Đây là đoạn **anti-pattern minh họa**, không phải giải pháp. Nếu cần plugin tùy chọn, bắt `ClassNotFoundException` ở boundary phù hợp và phân loại các loại `LinkageError` nghiêm trọng theo policy rõ ràng.

**Sửa tất cả bằng cách thêm mọi JAR vào application classpath:** có thể khiến Java class lookup pass nhưng phá version isolation, tạo `ClassCastException`, `NoSuchMethodError` hoặc duplicate native wrappers.

**Dùng `System.gc()` để chữa mọi lỗi classloader:** GC không thay binary method signatures, không sửa classpath, không buộc native library unload ngay và không tái chạy static initializer của erroneous class.

**Bật `--add-opens` bất kể lỗi:** module openness chỉ giải quyết một phần reflection/resource access; không chữa missing class/method hoặc type identity.

**Chỉ đọc exception message, bỏ cause:** nhiều errors là wrappers của lỗi sâu hơn; cần chuỗi cause, suppressed exceptions và thời điểm thất bại đầu tiên.

## 15. Checklist tra cứu nhanh trước khi sửa code

- [ ] Xác định throwable ngoài cùng thuộc `Exception`, `Error`, `LinkageError` hay runtime exception.
- [ ] Tìm giai đoạn loading, linking, initialization hay usage gây ra lỗi.
- [ ] Xác định API call/bytecode operation kích hoạt hành vi.
- [ ] Kiểm tra binary class/member name và chữ ký đầy đủ.
- [ ] Phân biệt `ClassNotFoundException` với `NoClassDefFoundError`.
- [ ] Nếu `NoClassDefFoundError`, kiểm tra init history và root cause.
- [ ] Nếu `ExceptionInInitializerError`, giữ nguyên stack trace của lần đầu.
- [ ] Nếu `NoSuchMethodError`/`NoSuchFieldError`, so compile/runtime ABI.
- [ ] Nếu `ClassCastException`, kiểm tra defining loader hai bên.
- [ ] Nếu `IllegalAccessError`, kiểm tra runtime package identity và JPMS exports.
- [ ] Nếu `VerifyError`, xem transformer/generator và classfile version.
- [ ] Nếu `UnsupportedClassVersionError`, kiểm tra Java target/runtime.
- [ ] Nếu `UnsatisfiedLinkError`, kiểm tra native ABI/library ownership.
- [ ] Nếu `ServiceConfigurationError`, kiểm tra descriptor/TCCL/JPMS provider rules.
- [ ] Không suy ra error timing luôn trùng thời điểm `loadClass`; linking resolution có thể lazy.
- [ ] Không dùng cùng giải pháp “thêm JAR” cho mọi nhóm lỗi.
- [ ] Test artifact cuối đúng launcher và classloader policy.
- [ ] Ghi đủ metadata loader/code source mà không lộ secrets.
- [ ] Nếu dùng plugin generation, dọn TCCL/caches/threads đúng lifecycle.

## 16. Bài tập tự kiểm tra

**Câu 1.** `Class.forName("p.Missing")` ném checked exception gì?

**Đáp án:** `ClassNotFoundException` nếu loader của caller không tìm được class; hãy kiểm tra binary name và loader/search path.

**Câu 2.** Client compile với method `Library.extra()` của v2 nhưng runtime nạp Library v1. Lỗi thường là gì?

**Đáp án:** `NoSuchMethodError` khi resolve/invoke method symbol, vì v1 không có method descriptor tương ứng.

**Câu 3.** Static initializer ném `IllegalStateException` ở lần dùng đầu, lỗi ngoài cùng có thể là gì?

**Đáp án:** `ExceptionInInitializerError`. Lần active use sau trên same erroneous class có thể thành `NoClassDefFoundError`.

**Câu 4.** Hai loaders khác nhau tự define cùng `api.Plugin`. Có chắc object plugin cast được về host `api.Plugin` không?

**Đáp án:** Không. Cùng binary name không đủ; khác defining loader là runtime types khác nhau, có thể `ClassCastException`.

**Câu 5.** `NoSuchMethodException` có luôn nghĩa `NoSuchMethodError` không?

**Đáp án:** Không. Một cái là checked reflection lookup exception, cái kia là `LinkageError` do symbolic method resolution/invocation mismatch.

**Câu 6.** Tại sao `VerifyError` không nên được sửa bằng cách tắt verifier?

**Đáp án:** Nó phản ánh bytecode không thỏa type-safety constraints; cần sửa generator/transformer/classfile thay vì bỏ lớp kiểm tra an toàn của JVM.

**Câu 7.** Class file major version quá mới so với JDK chạy, loại lỗi nào thường gặp?

**Đáp án:** `UnsupportedClassVersionError`; dùng compiler `--release` hoặc runtime phù hợp.

**Câu 8.** Plugin loader có class `p.C` nhưng `Class.forName("p.C")` từ host CNFE. Vì sao?

**Đáp án:** Caller loader không nhìn thấy child plugin loader; cần explicit plugin loader hoặc framework API phù hợp.

**Câu 9.** Reflection `setAccessible(true)` bị cấm vì named module không mở package. Đây có phải missing class không?

**Đáp án:** Không. Đây là access/module encapsulation issue, có thể `InaccessibleObjectException`; xử lý module policy phù hợp.

**Câu 10.** JNI lib nạp vào LoaderA rồi LoaderB cố nạp cùng library. Lỗi gì và fix kiến trúc?

**Đáp án:** `UnsatisfiedLinkError` do native-library ownership; dùng shared JNI bridge do parent sở hữu hoặc tách process nếu cần native state độc lập.

## 17. Cây quyết định tra cứu

~~~text
Throwable xuất hiện khi dùng Java class/plugin
                      |
                      v
          API/action nào kích hoạt?
           /       |        |       \
       Lookup    Linkage   Init    Runtime usage
         |         |        |         |
         v         v        v         v
    CNFE/NCDFE  ABI/verify  static    cast/access/
         |      /format      code     reflection/JNI
         |         |        |         |
   name+loader  version+    cause    type identity/
   +artifact    bytecode    chain    service/native
         \         |         |         /
          \        |         |        /
                  v
       Xác nhận giai đoạn + root cause
                  |
        so runtime defining loader,
        CodeSource, module, signature
                  |
                  v
       sửa đúng dependency/policy/code
                  |
                  v
      kiểm thử với artifact + JVM thật
~~~

**Kết luận:** Một lỗi “có chữ class” không nhất thiết là lỗi classpath. **Loading** chủ yếu là tìm/định nghĩa kiểu; **linking** là kiểm tra và giải quyết symbolic references; **initialization** chạy static code; **usage** gồm gọi method, reflection, cast, SPI và JNI. Xác định đúng giai đoạn, **Throwable type**, **cause chain**, **runtime defining loader** và **artifact ABI** mới cho phép sửa lỗi bền vững mà không tạo xung đột classloaders khác.

### Tài liệu chuẩn

- [JVMS 17 — Chapter 5: Loading, Linking and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html)
- [Java SE 17 — ClassNotFoundException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassNotFoundException.html)
- [Java SE 17 — NoClassDefFoundError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html)
- [Java SE 17 — ExceptionInInitializerError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ExceptionInInitializerError.html)
- [Java SE 17 — LinkageError](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/LinkageError.html)
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html)
- [Java 17 Java Language Specification — Binary Compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-004">Bảng thay đổi class loading giữa Java 8 và Java hiện đại</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng thay đổi class loading giữa Java 8 và Java hiện đại

Từ **Java 8** đến **Java 9+** rồi **Java 17, 21, 25 và các JDK mới hơn**, JVM vẫn giữ các khái niệm quan trọng: **binary name**, **defining loader**, **parent delegation**, **loading → linking → initialization**, `ClassLoader.loadClass` và định danh kiểu runtime. Thay đổi lớn nhất không phải Java “bỏ ClassLoader”, mà là **JDK 9 đưa hệ thống module JPMS vào runtime**, thay layout `rt.jar`/`tools.jar` bằng modular runtime image, thay **extension loader** bằng **platform loader**, và thay implementation của **application loader**. Các JDK sau siết thêm một số **ranh giới reflection / JDK internals**, bổ sung công cụ và API cho module/code generation.

Bài Knowledge này là **bảng tra cứu thay đổi class loading giữa Java 8 và Java hiện đại** thuộc **Java core – classloader / Tra cứu**. Nội dung phân biệt **quy tắc API** với **chi tiết implementation HotSpot/OpenJDK**, so sánh theo mốc phiên bản, đưa mã Java 8 và Java 17+ kiểm chứng thực tế, và cung cấp checklist di chuyển code cũ.

> **Phạm vi phiên bản:** “Java hiện đại” ở đây chủ yếu là **JDK 9+**, với ví dụ thực hành trên **JDK 17+**. Mốc JDK 21/25 được ghi khi có thay đổi hay API liên quan; không nên suy một hành vi của riêng HotSpot là cam kết chung cho mọi JVM hoặc tất cả JDK về sau.

## 1. Bảng tóm tắt nhanh: điều gì đổi, điều gì không?

| Chủ đề | Java 8 | Java 9+ / Java hiện đại | Hệ quả thực tế |
| --- | --- | --- | --- |
| Mô hình class loader built-in | Bootstrap → extension → application/system, trong các runtime thông dụng | Bootstrap → **platform** → application/system | Không tìm `ExtClassLoader` hay `lib/ext` như cơ chế nạp extension |
| Application loader implementation | Trên Oracle/OpenJDK 8 thường là subclass `URLClassLoader` | Built-in **không còn là `URLClassLoader`** | Không cast `ClassLoader.getSystemClassLoader()` sang `URLClassLoader` |
| Platform/extension loader | Extension loader được dùng với extension mechanism | Platform loader được truy cập qua `ClassLoader.getPlatformClassLoader()` (từ JDK 9) | Parent của custom loader có thể chọn platform loader |
| Classpath | Chủ yếu classpath + extension mechanism | Classpath **vẫn tồn tại**, cùng module path | App classpath thuộc unnamed module; module path cho named modules |
| Đóng gói JDK classes | `jre/lib/rt.jar`, `lib/tools.jar` và các JAR nội bộ | **Modular runtime image**, không còn `rt.jar`/`tools.jar` | Dùng `jrt:/`, `jmod`, `jlink` hoặc API module thay hardcode path |
| Runtime type identity | Binary name + defining loader | **Không đổi** | Hai loaders khác nhau vẫn có thể tạo hai `Class<?>` cùng tên |
| Parent delegation | Có | Vẫn có, nhưng module loading có cơ chế tìm theo module và loader graph | Không giản lược mọi module resolution thành một classpath tuyến tính |
| Module metadata | Không có `Class.getModule()`/`ModuleLayer` | `Class.getModule()`, `Module`, `ModuleLayer` | Log class loader **và** module, đặc biệt với lỗi access |
| Class visibility | Java access + class loader + một số package/security rules | Thêm **module readability / exports / opens** | `public` không đủ nếu module không export đúng package |
| JDK internal APIs | Nhiều thư viện dùng `sun.*`/reflective internals | JDK 9 đặt module boundaries; JDK 17 **strong encapsulation** mặc định | Phải bỏ phụ thuộc internal APIs hoặc dùng mở package có kiểm soát |
| Service discovery | `ServiceLoader` + `META-INF/services` | Thêm `uses`/`provides` và `ServiceLoader.stream()` | SPI có thể hoạt động qua module layer, không chỉ classpath resource |
| Resource lookup | `getResource(s)` theo loader/classpath/JAR | Thêm module encapsulation; resource URL JDK thường `jrt:` | `getResource` có thể không thấy resource trong named module bị đóng |
| Code generation | `ClassLoader.defineClass`, Java 8 `MethodHandles` có các API khác | `Lookup.defineClass` từ JDK 9, hidden classes từ JDK 15 | Framework nên dùng API hiện đại đúng package/module privileges |
| Bytecode version | Java 8 class file major **52** | Java 9=53, 11=55, 17=61, 21=65, 25=69 | `UnsupportedClassVersionError` nếu bytecode mới hơn JVM |
| Security Manager | Có cơ chế cũ và policies, dù không tự biến loader thành sandbox | Deprecated-for-removal JDK 17; **vô hiệu vĩnh viễn từ JDK 24** | Không thiết kế sandbox plugin mới chỉ bằng ClassLoader/Security Manager |
| Diagnostics | `-verbose:class`, `javap`, runtime class loader inspection | Unified Logging như `-Xlog:class+load`, `Class.getModule`, `jrt:` | Điều tra thêm module graph, declaring class và CodeSource |

**Đọc bảng theo đúng ba nhóm:** (1) thay đổi **thực sự trong Java/JDK API**, (2) thay đổi **implementation/layout mặc định**, và (3) thay đổi **mô hình quyền truy cập**. Không phải mọi dòng là một “breaking change” cho source Java thông thường.

## 2. Sơ đồ ClassLoader trước và sau JPMS

Java 8, kiểu hệ thống JDK thông dụng:

```text
bootstrap loader                (thường trả null từ getClassLoader)
        │
        ▼
extension loader                (một số extension JARs)
        │
        ▼
application / system loader     (classpath)
        │
        ▼
custom loaders                 (plugin/webapp/test)
```

Java 9+, mô hình phổ biến:

```text
bootstrap loader                     (java.base và một số module quan trọng)
        │
        ▼
platform loader                      (một số Java SE/JDK modules)
        │
        ▼
application / system loader          (classpath + các modules thích hợp)
        │
        ▼
custom loaders                       (tùy chính sách delegation/module)
```

**Cẩn trọng với hình vẽ:** đây là **quan hệ delegation** điển hình, **không phải quy tắc** rằng “mọi class `java.*` do platform loader định nghĩa”. JDK 9 chuyển **một số**, không phải tất cả, Java SE/JDK classes giữa bootstrap/platform loader. Javadoc cam kết các platform classes **visible through** platform loader, không cam kết tất cả đều **defined by** platform loader. Parent delegation cũng không bảo đảm class loader tự xây luôn parent-first nếu đã override `loadClass`.

**Nhớ:** `String.class.getClassLoader() == null` là biểu diễn bootstrap trong Java 8 lẫn JDK hiện đại; không có API public cho một “bootstrap ClassLoader instance” tương ứng theo cách các loaders khác có.

## 3. Java 8: extension loader và cơ chế thư mục `lib/ext`

Ở Java 8, extension mechanism cho phép Java runtime tìm một số JARs trong thư mục extension mà không yêu cầu chỉ ra từng JAR trên classpath. Nó gắn với **extension class loader** trong hierarchy thông dụng. Một số code cũ lấy parent của system loader và mặc định đó là extension loader, hoặc copy JAR trực tiếp tới `$JAVA_HOME/jre/lib/ext`.

Từ **JDK 9**, extension mechanism bị loại bỏ. Giải pháp **không phải** tạo lại `lib/ext` trong JDK mới, mà là đưa các libraries cần dùng vào **classpath, module path hoặc module configuration** phù hợp. Tên “extension loader” trong các mô tả cũ được thay bằng **platform loader**; mặc dù tổ tiên trong hierarchy có thể trông tương tự, **platform loader không còn quét `lib/ext` như Java 8**.

| Hành vi/code cũ | Trạng thái trên JDK 9+ | Hướng xử lý |
| --- | --- | --- |
| Copy custom JAR vào `jre/lib/ext` | Extension mechanism đã loại bỏ | Đưa vào app dependencies/classpath hoặc module path |
| Dùng `-Djava.ext.dirs=...` | Không còn giải pháp deployment hợp lệ | Xóa property, cấu hình dependencies tường minh |
| Dựa `lib/endorsed` / `java.endorsed.dirs` | Endorsed standards override mechanism bị loại bỏ | Xem upgradeable modules hoặc module path/classpath phù hợp |
| Giả định parent của app loader có thể đọc extension JARs cũ | Không còn đúng | Thiết kế loader graph/dependencies mới |
| Dùng literal class name `sun.misc.Launcher$ExtClassLoader` | Phụ thuộc implementation đã thay đổi | Dùng API `ClassLoader.getPlatformClassLoader()` nếu cần platform parent |

Một số thông báo lỗi từ JDK 9/10 khi còn `lib/ext` hoặc properties cũ có thể khiến VM không khởi động. **Đừng dùng `-XX:+IgnoreUnrecognizedVMOptions` làm “sửa” chính**: cấu hình cũ cần được loại bỏ và thay đúng mô hình deployment.

## 4. Application loader: vì sao không còn cast được sang `URLClassLoader`?

Trong Oracle/OpenJDK 8, nhiều ứng dụng từng viết:

```java
URLClassLoader app =
        (URLClassLoader) ClassLoader.getSystemClassLoader();
```

Đây là **mã phụ thuộc implementation**, không phải Java SE portable contract. Nó thường chạy trên JDK 8 HotSpot nhưng **không còn chạy** trên JDK 9+ built-in app loader: system loader được triển khai bằng internal implementation **không phải `URLClassLoader`**, nên có thể ném `ClassCastException`.

**Sửa theo nhu cầu:**

- **Muốn đọc application classpath:** xem `System.getProperty("java.class.path")` (lưu ý module path là cơ chế khác) và cấu hình launcher/build, không ép cast loader.
- **Muốn nạp plugin JAR ở runtime:** tạo `new URLClassLoader(urls, parent)` mà bạn **sở hữu** và quản lý lifecycle; chọn delegation/shared API đúng.
- **Muốn thêm dependency vào một named module:** dùng module resolution/layers hoặc cơ chế instrumentation/launcher phù hợp; không mong app loader có thể `addURL` như Java 8.
- **Muốn tìm JDK classes:** dùng public APIs của module/runtime image, không tìm hệ thống JAR để tự bổ sung search path.

**Không có API Java SE chung cho việc “thêm URL vào classpath của system loader” trong mọi implementation/JDK.** Việc cố reflectively gọi method nội bộ của `BuiltinClassLoader` rất dễ vấp JPMS strong encapsulation và không nên dùng làm kiến trúc.

## 5. Thực hành A — một chương trình Java 8 source để so sánh hai runtime

Mã chỉ dùng APIs đã có trên Java 8, nên có thể compile với JDK 8 hoặc **`javac --release 8`** trên JDK 17+. Chạy binary đó trên từng JDK để so khác biệt **runtime**, chứ không suy khác biệt từ compiler flags.

Tệp **`src/demo/PortableLoaderProbe.java`**:

```java
package demo;

import java.net.URL;
import java.net.URLClassLoader;

public final class PortableLoaderProbe {
    public static void main(String[] args) {
        ClassLoader system = ClassLoader.getSystemClassLoader();
        URL coreResource =
                ClassLoader.getSystemResource("java/lang/String.class");

        System.out.println("bootstrap String=" +
                (String.class.getClassLoader() == null));
        System.out.println("system is URLClassLoader=" +
                (system instanceof URLClassLoader));
        System.out.println("system parent exists=" +
                (system.getParent() != null));
        System.out.println("core resource protocol=" +
                (coreResource == null
                        ? "<unavailable>"
                        : coreResource.getProtocol()));
    }
}
```

Biên dịch/chạy trên **JDK 17+** từ thư mục project:

```sh
javac --release 8 -d out src/demo/PortableLoaderProbe.java
java -cp out demo.PortableLoaderProbe
```

**Stdout dự kiến trên OpenJDK/HotSpot 17:**

```text
bootstrap String=true
system is URLClassLoader=false
system parent exists=true
core resource protocol=jrt
```

Trên một máy có **Oracle/OpenJDK 8 thông dụng**, biên dịch bằng JDK 8:

```sh
javac -source 8 -target 8 -d out src/demo/PortableLoaderProbe.java
java -cp out demo.PortableLoaderProbe
```

**Stdout thường thấy trên Oracle/OpenJDK 8:**

```text
bootstrap String=true
system is URLClassLoader=true
system parent exists=true
core resource protocol=jar
```

**Giới hạn khẳng định:** các dòng kiểm tra implementation class của system loader và `getSystemResource` phản ánh **Oracle/OpenJDK/HotSpot trong cấu hình tiêu chuẩn**, **không phải bắt buộc trên mọi JVM**. User-defined `java.system.class.loader` hoặc custom JDK image có thể cho kết quả khác. Dòng `-source 8 -target 8` trên JDK 8 chỉ minh họa compile target; `--release 8` trên JDK 17 **không mô phỏng loader của Java 8**.

### Rút ra gì từ bốn dòng?

1. Bootstrap loader vẫn được đại diện bởi `null` ở `String.class.getClassLoader()`.
2. Built-in application loader đã đổi implementation trong modular JDK.
3. System loader vẫn thường có một parent object, nhưng trên JDK hiện đại parent đó là platform loader.
4. JDK runtime class resource chuyển từ dạng JAR resource (thường `jar:file:...rt.jar!...`) sang **`jrt:` resource** của modular runtime image.

## 6. `rt.jar`, `tools.jar`, `jrt:/` và modular runtime image

**Java 8:** nhiều JDK classes/resources nằm trong JARs như `jre/lib/rt.jar` và `lib/tools.jar`. Các công cụ/phần mềm cũ mở chúng bằng `JarFile` hoặc đọc từ những đường dẫn hardcoded.

**JDK 9+:** JEP 220/JPMS chuyển JDK image sang định dạng modular. Các file JAR nổi tiếng nói trên **không còn tồn tại với cấu trúc cũ**. Resource access có thể dùng **`jrt:/`** (JRT filesystem provider) để truy cập nội dung module runtime.

Ví dụ resource của Java SE class:

```text
Java 8 thường:
jar:file:/.../jre/lib/rt.jar!/java/lang/String.class

JDK 9+ thường:
jrt:/java.base/java/lang/String.class
```

Đây là **ví dụ URL minh họa**, không phải đường dẫn thật của mọi OS/JDK image. Nếu code tìm source JAR bằng cách strip `jar:file:` rồi tìm dấu `!`, nó sẽ hỏng trên JDK modular.

**Thay thế thích hợp:**

| Mục đích | Cách hiện đại |
| --- | --- |
| Đọc resource JDK module | `ClassLoader.getSystemResource` khi hợp lệ, `Module.getResourceAsStream`, `jrt:` filesystem |
| Liệt kê module runtime | `ModuleFinder.ofSystem()`, `java --list-modules` |
| Phân tích module dependencies | `jdeps` và metadata module |
| Đóng runtime nhỏ | `jlink` (module-aware runtime image) |
| Thư viện nội bộ app | Quản lý JAR riêng qua classpath/module path; không thêm vào `rt.jar` |
| Quan sát class đã load | `Class.getModule()`, `getClassLoader()`, optional `CodeSource`, `-Xlog:class+load` |

**Một JMOD không phải JAR classpath thông thường.** `.jmod` dùng cho đóng gói/linking modules trong JDK tooling; không nên kỳ vọng `URLClassLoader` đọc `jmods/java.base.jmod` như plugin JAR.

## 7. Thực hành B — kiểm chứng `jrt:` và module metadata trên Java 17+

Tệp **`src/demo/ModernRuntimeProbe.java`**:

```java
package demo;

import java.net.URL;

public final class ModernRuntimeProbe {
    public static void main(String[] args) {
        ClassLoader platform =
                ClassLoader.getPlatformClassLoader();
        ClassLoader app =
                ClassLoader.getSystemClassLoader();

        URL core = ClassLoader.getSystemResource(
                "java/lang/String.class");

        System.out.println("java.base module=" +
                String.class.getModule().getName());
        System.out.println("app module named=" +
                ModernRuntimeProbe.class.getModule().isNamed());
        System.out.println("String bootstrap=" +
                (String.class.getClassLoader() == null));
        System.out.println("app parent is platform=" +
                (app.getParent() == platform));
        System.out.println("JDK resource jrt=" +
                (core != null && "jrt".equals(core.getProtocol())));
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```sh
javac --release 17 -d out src/demo/ModernRuntimeProbe.java
java -cp out demo.ModernRuntimeProbe
```

**Stdout trên OpenJDK/HotSpot 17 trong cấu hình không thay system loader:**

```text
java.base module=java.base
app module named=false
String bootstrap=true
app parent is platform=true
JDK resource jrt=true
```

Các dòng kiểm tra:

- `String` thuộc named module **`java.base`**, nhưng vẫn do bootstrap định nghĩa trong cấu hình chuẩn.
- `ModernRuntimeProbe` chạy bằng `-cp out` nên thuộc **unnamed module** của app loader, không phải một named module do có package `demo`.
- `ClassLoader.getPlatformClassLoader()` là API mới từ JDK 9.
- Built-in app loader thường parent về platform loader. Nếu launcher thay system loader hoặc môi trường không chuẩn, không lấy chi tiết parent object này làm contract cho mọi triển khai.
- `ClassLoader.getSystemResource("java/lang/String.class")` thường trả `jrt:` URL trên modular HotSpot. Không đồng nhất `jrt:` với CodeSource URL; một class hoàn toàn hợp lệ có thể có `CodeSource == null`.

**Không dùng ví dụ này trên JDK 8**: code nhắc `getPlatformClassLoader` và `Class.getModule`, hai APIs chưa có ở source/runtime Java 8.

## 8. JPMS xuất hiện từ Java 9: classpath không biến mất

JPMS (**Java Platform Module System**, JEP 261) đem theo:

- `module-info.java` / module descriptor (`requires`, `exports`, `opens`, `uses`, `provides`).
- **Named modules**, module path và resolved module graph.
- **Unnamed modules** cho classpath classes; module metadata có thể truy vấn qua `Class.getModule()`.
- **Readability** giữa modules và **exports/opens** của packages.
- `ModuleLayer` để tạo những module configurations/layers mới tại runtime.
- Tích hợp module-aware service discovery và resource access.
- Class loaders có thể define classes thuộc các named modules, không chỉ tìm JARs trên một tuyến classpath.

Một class trên application classpath ở JDK 17 vẫn có `ClassLoader`, vẫn được load và vẫn có runtime type identity như Java 8, nhưng `Class.getModule().isNamed()` là **false** (unnamed module). Nếu cùng class được đóng gói/khởi chạy như **named module**, module metadata và access rules khác.

**Mô hình đúng:** từ Java 9+, cần đồng thời suy nghĩ về **“loader nào định nghĩa class?”** và **“class thuộc module/package nào, module có được đọc/export/open?”**.

## 9. Thực hành C — cùng source, so classpath mode với named-module mode

Dùng một class đơn giản để kiểm tra **module identity**. Tệp **`src/demo/ModuleModeDemo.java`**:

```java
package demo;

public final class ModuleModeDemo {
    public static void main(String[] args) {
        Module module = ModuleModeDemo.class.getModule();
        System.out.println("named=" + module.isNamed());
        System.out.println("module=" +
                (module.isNamed()
                        ? module.getName()
                        : "<unnamed>"));
    }
}
```

### C1. Chạy như Java 8-style classpath (trên JDK 17)

```sh
javac --release 17 -d out src/demo/ModuleModeDemo.java
java -cp out demo.ModuleModeDemo
```

**Stdout:**

```text
named=false
module=<unnamed>
```

### C2. Chạy như named module trên cùng JDK 17

Tạo tệp **`module-info.java`** ở project root:

```java
module com.example.probe {
    exports demo;
}
```

Biên dịch vào output directory **khác** để tránh nhầm classpath artifacts với module artifacts:

```sh
javac --release 17 -d mods/com.example.probe module-info.java src/demo/ModuleModeDemo.java
java --module-path mods -m com.example.probe/demo.ModuleModeDemo
```

**Stdout:**

```text
named=true
module=com.example.probe
```

**Điểm kiểm chứng:** source `ModuleModeDemo.java` không đổi, nhưng **cách launch và module descriptor** làm runtime class thuộc module khác. Không suy từ package name hoặc việc `javac` sinh `.class` rằng một class đã tự thuộc named module.

**Không nên biên dịch/running `module-info.java` bằng JDK 8** vì JPMS là API/tooling từ Java 9. Nếu library cần duy trì Java 8, cân nhắc **multi-release JAR, separate artifacts hoặc classpath-first build** theo nhu cầu, thay vì viết thẳng `module-info.java` rồi cố compile bằng `-source 8`.

## 10. Module layers và custom ClassLoaders

Trong Java 8, plugin frameworks thường tạo một `URLClassLoader` cho JARs rồi làm parent-first/child-first. JDK 9+ vẫn cho phép pattern này **trên classpath-style loading**, nhưng còn một lựa chọn: resolve **named modules** và define một `ModuleLayer` mới.

Mô hình:

```text
boot ModuleLayer
   ├─ java.base (bootstrap)
   ├─ java.logging, java.xml, ...
   └─ app modules
         │
         ▼
child ModuleLayer (plugin version/generation)
   ├─ com.example.plugin
   ├─ com.example.plugin.lib
   └─ loaders chosen by layer-definition API
```

Các APIs quan trọng:

- `ModuleFinder`: tìm descriptors/modules trên module path hoặc paths.
- `Configuration.resolve(...)`: tạo graph module được resolve; xác thực dependencies.
- `ModuleLayer.defineModulesWithOneLoader(...)`, `defineModulesWithManyLoaders(...)` hoặc `defineModules(...)`: tạo layer/module-to-loader mappings.
- `ModuleLayer.findLoader(moduleName)`: xác định loader của module trong layer.
- `Class.getModule()`: xem module của runtime class thực.
- `ServiceLoader.load(layer, Service.class)`: khám phá providers trong layer theo module-aware semantics.

**Lưu ý:** **module layer không phải OS sandbox**. Nó tăng khả năng tổ chức modules, version/configuration và access checks; code plugin vẫn thường chạy **trong cùng JVM/process và quyền OS**. Với code không tin cậy, có thể cần isolation cấp process/container/VM.

**Lưu ý về split packages:** named modules và class-loader mappings có những ràng buộc về package/module graph. Đừng chuyển nguyên bản thiết kế child-first loader “trộn nhiều JAR có cùng package” sang modules và mặc định nó sẽ hoạt động.

## 11. Exports và opens: thay đổi lớn với reflection

Trước Java 9, một library dùng reflection vào private members của JDK internals thường phụ thuộc implementation/security policy và có thể hoạt động trên Java 8. Từ Java 9, named modules có ranh giới:

- **`requires`**: module đọc module khác.
- **`exports p`**: package `p` được dùng qua public API bởi modules phù hợp.
- **`exports p to m`**: export có mục tiêu.
- **`opens p` / `opens p to m`**: hỗ trợ deep reflection theo scope.
- **`open module ...`**: mở nhiều packages để reflection, nhưng không làm mọi member thành public API theo nghĩa biên dịch.

**`public` chưa đủ** nếu target nằm trong package của named module không được export phù hợp. `setAccessible(true)` cũng không tự vượt qua JPMS khi package không được mở.

Từ **JDK 9–16**, migration từng cho phép một số relaxed illegal reflective access đối với JDK internals, kèm warnings/điều kiện. **JDK 17 (JEP 403)** áp dụng **strong encapsulation** mặc định với các JDK internals (ngoại trừ một số critical APIs); cơ chế nới lỏng hàng loạt kiểu cũ không còn. Nên **chuyển sang supported public APIs**, chỉ dùng `--add-opens`/`--add-exports` có chọn lọc cho migration nếu thực sự cần.

**Không tự coi mọi `InaccessibleObjectException` là vấn đề classpath.** Class đã tìm thấy; lỗi nằm ở module/package reflective access.

## 12. Thực hành D — deep reflection vào JDK internals bị chặn từ Java 17

Tệp **`src/demo/ReflectionBoundaryDemo.java`**:

```java
package demo;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;

public final class ReflectionBoundaryDemo {
    public static void main(String[] args) throws Exception {
        Field internal = String.class.getDeclaredField("value");

        try {
            internal.setAccessible(true);
            System.out.println("opened=true");
        } catch (InaccessibleObjectException denied) {
            System.out.println("opened=false");
        }

        System.out.println("owner module=" +
                String.class.getModule().getName());
    }
}
```

Biên dịch/chạy trên **JDK 17+**, không thêm `--add-opens`:

```sh
javac --release 17 -d out src/demo/ReflectionBoundaryDemo.java
java -cp out demo.ReflectionBoundaryDemo
```

**Stdout dự kiến trên OpenJDK/HotSpot 17 với mặc định JPMS:**

```text
opened=false
owner module=java.base
```

Nếu operator **cố ý** mở package `java.lang` của `java.base` cho unnamed modules:

```sh
java --add-opens java.base/java.lang=ALL-UNNAMED -cp out demo.ReflectionBoundaryDemo
```

**Stdout khi opening được JVM chấp nhận:**

```text
opened=true
owner module=java.base
```

**Ý nghĩa:** target class đã tồn tại, loader không có vấn đề. Chỉ **module openness** thay đổi reflective access outcome. `--add-opens` là lựa chọn có rủi ro và cần chỉ định package/scope; **không thêm wildcard opens toàn bộ JDK** như giải pháp mặc định.

**Cẩn trọng:** đây là demo về access check, **không khuyến khích đọc hoặc sửa private representation của `String`** trong sản phẩm. Chi tiết internal fields có thể thay đổi giữa JDK versions; demo chỉ cần metadata field `value` vốn có trên OpenJDK 17 và kiểm tra `setAccessible`.

## 13. Named module vs classpath resource lookup

`ClassLoader.getResource` và `Class.getResource` tồn tại từ Java 8, nhưng JDK 9+ thêm **module encapsulation** đối với resource lookup.

Ví dụ:

```java
URL resource =
        ClassLoader.getSystemResource("sample/config.properties");
```

Đây là **snippet** cần import `java.net.URL` và resource tồn tại. Trên classpath, kết quả thường chịu class loader delegation và search paths. Với resource trong named module, public resource lookup **có thể bị hạn chế** nếu package chứa resource chưa được **opened unconditionally** theo Javadoc của `ClassLoader.getResource`. Có ngoại lệ đặc biệt cho tên kết thúc bằng `".class"`, nhưng không nên coi đó là cách truy cập arbitrary module internals.

So sánh:

| Trường hợp | Java 8-style classpath | Java 9+ named module |
| --- | --- | --- |
| `config.properties` cùng JAR | Thường có thể tìm qua loader resource lookup nếu path đúng | Có thể `null` nếu package không mở phù hợp |
| Resource `.class` của JDK | Thường `jar:...rt.jar!...` | Thường `jrt:/module/...` |
| `Class.getResource` | Dựa package-relative path và loader | Với named module còn module-scoped lookup/encapsulation |
| `Module.getResourceAsStream` | Chưa có API này | Dùng module-specific resource lookup với access rules |
| Quét `META-INF/services` | SPI classpath descriptor | Có thêm JPMS provider declarations ngoài descriptors |

**Gỡ lỗi:** log requested resource name, loader, module, package openness và actual URL. Một `null` từ resource lookup **không luôn nghĩa file bị thiếu khỏi artifact**.

## 14. ServiceLoader: Java 8 descriptors và Java 9+ module providers

**Java 8/classpath SPI**:

```text
META-INF/services/com.example.spi.Parser
    └─ dòng: com.example.impl.FastParser
```

JDK 9+ vẫn hỗ trợ cơ chế này cho classpath/unnamed-module providers, nhưng named modules có:

```java
module com.example.consumer {
    requires com.example.api;
    uses com.example.api.Parser;
}
```

và:

```java
module com.example.provider {
    requires com.example.api;
    provides com.example.api.Parser
        with com.example.impl.FastParser;
}
```

Đây là **module-info snippets**, cần modules và classes tương ứng nếu muốn compile. Các APIs Java 9+:

- `ServiceLoader.load(layer, service)`: khám phá providers trong module layer.
- `ServiceLoader.stream()`: trả `Stream<ServiceLoader.Provider<S>>` để xem provider metadata lazily.
- `Provider.type()` và `Provider.get()`: phân biệt type inspection và lấy provider instance.

Cảnh báo vẫn quan trọng trên cả JDK cũ/mới: `ServiceLoader` là **lazy**, có provider cache, không thread-safe cho concurrent use trên **cùng instance**. `reload()` xóa provider cache nhưng **không redefine JVM classes**. TCCL ảnh hưởng overload `ServiceLoader.load(service)`; đừng mặc định `Class.forName(String)` dùng TCCL.

## 15. ClassLoader APIs: cũ, mới và phạm vi dùng

| API | Có trên Java 8? | Java 9+ | Ghi chú |
| --- | --- | --- | --- |
| `ClassLoader.loadClass(String)` | Có | Có | Không yêu cầu initialization theo hành vi chuẩn |
| `ClassLoader.defineClass` | Có | Có | Protected final, tạo runtime class, không tạo instance |
| `ClassLoader.findClass(String)` | Có | Có | Hook subclass; thường override thay vì thay cả `loadClass` |
| `ClassLoader.getSystemClassLoader()` | Có | Có | Trả system loader, implementation-dependent |
| `ClassLoader.getPlatformClassLoader()` | **Không** | **Có, từ 9** | Dùng khi cần platform loader as parent |
| `ClassLoader.getName()` | **Không** | **Có, từ 9** | Loader name có thể null, không unique |
| `ClassLoader.getUnnamedModule()` | **Không** | **Có, từ 9** | Unnamed module associated với loader |
| `Class.getModule()` | **Không** | **Có, từ 9** | Module của runtime type |
| `Class.forName(Module,String)` | **Không** | **Có, từ 9** | Không init target; có thể trả null |
| `ClassLoader.findClass(moduleName,name)` | **Không** | **Có, từ 9** | Cho loader hỗ trợ named modules |
| `ClassLoader.findResource(moduleName,name)` | **Không** | **Có, từ 9** | Resource lookup gắn module |
| `ClassLoader.resources(String)` | **Không** | **Có, từ 9** | Stream các resource URLs |
| `ModuleLayer`/`ModuleFinder` | **Không** | **Có, từ 9** | Module graph và layers |
| `MethodHandles.Lookup.defineClass(byte[])` | **Không** | **Có, từ 9** | Define class same loader/runtime package từ lookup |
| `MethodHandles.Lookup.defineHiddenClass` | **Không** | **Có, từ 15** | Hidden classes cho runtime frameworks |
| `ClassLoader.registerAsParallelCapable()` | **Có** | **Có** | Không phải tính năng mới của JPMS |
| `ClassLoader.getClassLoadingLock(String)` | **Có** | **Có** | Synchronization của loader, khác class-init lock |
| `Thread.getContextClassLoader()` | **Có** | **Có** | TCCL vẫn dùng cho SPI/framework |

**Sai lầm thường gặp:** gắn mọi API có chữ “loader” vào JDK 9. Ví dụ `registerAsParallelCapable` đã có từ trước, và **ClassLoader parent delegation không bị JPMS phát minh lại từ đầu**.

## 16. Binary compatibility và class-file major versions

| Java release | Class-file major |
| --- | ---: |
| Java 8 | 52 |
| Java 9 | 53 |
| Java 10 | 54 |
| Java 11 | 55 |
| Java 12 | 56 |
| Java 13 | 57 |
| Java 14 | 58 |
| Java 15 | 59 |
| Java 16 | 60 |
| Java 17 | 61 |
| Java 18 | 62 |
| Java 19 | 63 |
| Java 20 | 64 |
| Java 21 | 65 |
| Java 22 | 66 |
| Java 23 | 67 |
| Java 24 | 68 |
| Java 25 | 69 |

**Quan trọng:** class compiled cho Java 17 (major 61) **không thể chạy trực tiếp trên JVM Java 8** (chỉ hiểu tối đa 52). JVM hiện đại thường hỗ trợ classfile versions cũ, nhưng việc một thư viện “được compile từ Java 8” không bảo đảm không phụ thuộc các **internal APIs bị loại bỏ** hoặc reflective access bị JPMS chặn.

Lệnh kiểm tra:

```sh
java -version
javac -version
javap -verbose -classpath out demo.PortableLoaderProbe
```

Trong `javap -verbose`, tìm **`major version`**. Nếu bạn dùng JDK 17+ và muốn build library tương thích Java 8:

```sh
javac --release 8 -d out src/demo/PortableLoaderProbe.java
```

`--release 8` thiết lập target bytecode và supported API view phù hợp với release 8. Nhưng nó **không chuyển runtime Java 17 thành Java 8**, không khôi phục `rt.jar`, không biến system loader thành `URLClassLoader`.

### Multi-release JAR (JDK 9+)

Một **multi-release JAR** có thể chứa code baseline (ví dụ Java 8) và overrides dưới `META-INF/versions/9`, `META-INF/versions/17` ... với manifest `Multi-Release: true`. Tùy runtime, class/resource resolution chọn entry thích hợp theo cơ chế JEP 238. Đây là chiến lược compatibility, không tự sửa tất cả khác biệt về module encapsulation và loader identity.

**Đừng** đặt hai classes cùng binary name ngoài cơ chế multi-release/loader boundaries rồi mặc định JVM có thể chọn phiên bản theo ý bạn. Cần packaging/build tool đúng, test trên runtimes mục tiêu.

## 17. Class identity và loader constraints: những quy tắc không đổi

Dù có JPMS, runtime class vẫn được phân biệt theo **binary name và defining loader**. Một ví dụ:

```text
loader-A defines p.ServiceImpl → Class A
loader-B defines p.ServiceImpl → Class B

A.getName().equals(B.getName()) = true
A == B = false
```

Nếu hai loaders tự định nghĩa **bản sao API interface** thay vì delegate tới host, plugin implementation có thể gây `ClassCastException` hoặc loader-constraint `LinkageError`. Điều này đúng từ Java 8 và **không tự được giải quyết bởi module path**.

Những quy tắc cần giữ:

- Shared API/DTO/exception classes phải có defining-loader strategy nhất quán.
- `Class<?>` có thể thuộc named hoặc unnamed module; module name không thay binary name.
- `Class.getPackageName()` giống nhau **chưa bảo đảm same runtime package**, vì runtime package identity còn phụ thuộc defining loader.
- Parent-first delegation là mặc định của nhiều built-in/classpath loaders, nhưng custom child-first loader cần kiểm soát trùng definition và synchronization.
- Plugin reload muốn định nghĩa lại cùng binary name thường dùng **new defining loader generation**. `URLClassLoader.close()` không unload classes tức thì.
- Class loading, linking và initialization vẫn là những giai đoạn khác nhau. `Class.forName(name,true,loader)` yêu cầu initialization; `loadClass` chuẩn không.

**JPMS bổ sung module readability/exports/opens**, không thay thế toàn bộ các quy tắc identity/linking của JVM.

## 18. Custom loader parent nên là `null` hay platform loader?

Một pattern Java 8:

```java
ClassLoader isolated =
        new java.net.URLClassLoader(urls, null);
```

Đây là **snippet** cần biến `urls`. Parent `null` thường biểu diễn bootstrap-only parent, nhưng **từ JDK 9 bootstrap định nghĩa ít Java SE classes hơn** so với Java 8. Nếu custom loader muốn thấy platform classes mà không phụ thuộc system application loader, nên cân nhắc:

```java
ClassLoader platform =
        ClassLoader.getPlatformClassLoader();

java.net.URLClassLoader isolated =
        new java.net.URLClassLoader(urls, platform);
```

Đây là **snippet Java 9+**, cần `urls` hợp lệ và close loader khi lifecycle kết thúc. Dùng platform parent không có nghĩa plugin sẽ thấy tất cả application classes; đó có thể chính là mục tiêu isolation.

Cân nhắc ba chọn lựa:

| Parent | Ưu điểm | Nguy cơ/cần lưu ý |
| --- | --- | --- |
| `null` | Bớt visibility tới application/platform loader objects | Có thể thiếu Java SE/JDK modules do platform loader define |
| `ClassLoader.getPlatformClassLoader()` | Thấy platform classes mà hạn chế app classes | Shared app API không tự visible; cần thiết kế API sharing |
| `ClassLoader.getSystemClassLoader()` | Dễ thấy host APIs/dependencies | Parent-first có thể “nuốt” plugin version nếu host chứa trùng dependencies |

**Không có một parent tối ưu tuyệt đối cho mọi plugin.** Nó phụ thuộc shared contracts, API isolation, module graph, security threat model và lifecycle. `ClassLoader` không phải sandbox cho mã không đáng tin ngay cả khi parent là `null`.

## 19. Code generation: `Lookup.defineClass` và hidden classes

Java 8 frameworks thường dùng `ClassLoader#defineClass` (qua subclass), reflection vào internals hoặc những API không portable để sinh lớp. Java 9 thêm:

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
Class<?> generated = lookup.defineClass(bytes);
```

Đây là **snippet Java 9+**, cần import `java.lang.invoke.MethodHandles`, bytes của một class **cùng package** với lookup class, lookup có **PACKAGE** privileges. API định nghĩa named class với loader, runtime package và protection domain phù hợp với lookup class. Không thể dùng lookup bất kỳ để inject class vào package/module khác.

Từ **JDK 15**, `Lookup.defineHiddenClass` phục vụ anonymous-like/hidden runtime implementation types (JEP 371). Hidden classes không thiết kế để discover bằng binary name thông thường; rất hữu ích cho proxies/lambda/runtime language infrastructure, nhưng không phải replacement trực tiếp cho plugin APIs dựa trên `Class.forName("p.Name")`.

**Di chuyển thư viện sinh bytecode từ Java 8:**

1. Chọn đúng JVM target major version.
2. Dùng API lookup/hidden-class phù hợp thay vì reflection vào JDK internals.
3. Kiểm tra `NestHost`/`NestMembers`, sealed classes, records và package/module rules trên JDK 17+.
4. Đảm bảo framework phát hành có bản hỗ trợ JDK 17/21/25 nếu production dùng.
5. Không dựa vào `--add-opens` hàng loạt như kiến trúc vĩnh viễn.

## 20. Security Manager không phải tương lai của plugin isolation

Java 8 có Security Manager và permission model từng được các hệ thống dùng để hạn chế code dựa trên code source. Nhưng ngay cả khi đã bật, việc xây sandbox đáng tin từ **ClassLoader + SecurityManager** luôn đòi threat model và cấu hình đúng.

Mốc thay đổi:

| Mốc | Ý nghĩa |
| --- | --- |
| Java 8 | Security Manager và policy-based permission checks còn được dùng |
| Java 17 | JEP 411 **deprecated for removal** Security Manager |
| Java 24 | JEP 486 **permanently disables Security Manager** |
| Java 25+ | Không dùng Security Manager để xây sandbox Java truyền thống |

Khi cần chạy code plugin không tin cậy, giải pháp thực tế là **tách tiến trình/VM/container với OS permissions, resource quotas, filesystem/network policy và IPC validation**, không chỉ tùy biến `ClassLoader`. Một process riêng mà vẫn có toàn quyền host cũng **chưa tự thành sandbox**.

## 21. Diagnostics: lỗi cũ và lỗi mới thường gặp sau migration

| Lỗi/triệu chứng | Java 8 thường liên quan | Khi chuyển sang Java 9+ | Cách điều tra |
| --- | --- | --- | --- |
| `ClassCastException` của system loader → URLClassLoader | Implementation cast từng chạy | App loader không còn URLClassLoader | Bỏ cast, dùng custom URLClassLoader/API chính thức |
| `ClassNotFoundException` cho extension JAR | JAR được tìm qua `lib/ext` | Extension mechanism không còn | Đưa dependency vào classpath/module path |
| Tool báo không có `rt.jar`/`tools.jar` | Hardcode cấu trúc JDK 8 | Modular runtime image, `jrt:` | Cập nhật tool, dùng `ModuleReader`/jrt |
| `NoClassDefFoundError` cho platform API | Bootstrap parent từng thấy class | Class có thể do platform loader define | Chọn platform parent phù hợp |
| `IllegalAccessError` | Package/runtime type/access check | Thêm module readability/exports | Log caller/target loader/module/package |
| `InaccessibleObjectException` | Java 8 không có loại lỗi JPMS này | Deep reflection bị module openness chặn | Dùng public API, `--add-opens` khi bắt buộc |
| `ServiceConfigurationError` | Descriptor/classpath/TCCL issue | Còn có `uses/provides` và module layers | Kiểm tra service type, provider module và loader |
| `UnsupportedClassVersionError` | Target bytecode > major 52 | Vẫn xảy ra nếu target > JVM version | `javap -verbose`, build `--release` |
| Resource lookup trả `null` | Missing path/JAR | Có thể package named module chưa opened | Kiểm tra `Module.getResourceAsStream`/opens |
| `ClassNotFoundException` trong plugin child | Parent/classpath wrong | Vừa parent vừa module graph wrong | Ghi requested/defining loader, layer, code source |
| `NoSuchMethodError` sau nâng JDK/library | Binary signature mismatch | JDK internal APIs/version skew | Đối chiếu `javap -s` và artifacts |
| Custom bytecode framework fail | Constructor/ASM verifier mismatch | Classfile attributes/modules/nests mới | Nâng framework/ASM, test target release |

**Không đoán lỗi chỉ bằng exception class.** `NoClassDefFoundError` có thể do missing dependency hoặc **class initialization đã thất bại**; `ClassCastException` có thể do hai types trùng binary name khác defining loaders. Ghi cả **cause chain, loader identity, module và code source** nếu có.

## 22. Lệnh tra cứu hữu ích trên JDK hiện đại

### Module inventory

```sh
java --list-modules
java --describe-module java.base
```

Các lệnh giúp xem module graph/runtime image, không tự xác định custom loader của từng plugin class.

### Kiểm tra dependencies và JDK internals

```sh
jdeps --jdk-internals path/to/application.jar
jdeps --multi-release 17 path/to/application.jar
```

Lệnh là **mẫu**, cần thay `path/to/application.jar`. `jdeps` hỗ trợ tìm phụ thuộc internal APIs và multi-release JAR; nó không thể chứng minh mọi reflective access từ strings/dynamic loading đều được phát hiện.

### Class loading logs

```sh
java -Xlog:class+load=info -cp out demo.ModernRuntimeProbe
java -Xlog:help
```

`-Xlog:class+load` là giao diện **HotSpot Unified Logging** trên JDK 9+, không phải cú pháp có cùng semantics trên mọi JVM. Java 8 từng dùng `-verbose:class` để quan sát tương tự, nhưng output formatting khác. Không viết parser nghiệp vụ dựa vào text log này.

### Xem class file version và binary methods

```sh
javap -verbose -classpath out demo.ModernRuntimeProbe
javap -s -classpath path/to/library.jar com.example.Library
```

`-verbose` hiển thị `major version`; `-s` giúp so method descriptors khi gặp `NoSuchMethodError`.

### Xem resource từ module image

```sh
jimage list "$JAVA_HOME/lib/modules"
```

Lệnh dành cho JDK có công cụ `jimage`, path theo cú pháp shell POSIX. Thư mục runtime có thể khác tùy custom image; không hardcode cấu trúc khi phát hành ứng dụng portable.

## 23. Quy trình chuyển một plugin framework Java 8 sang Java 17+

**Bước 1 — phân loại code cũ.** Tìm việc cast system loader sang `URLClassLoader`, gọi `sun.*`/`com.sun.*` internals, sử dụng `lib/ext`/`rt.jar`, sửa `java.class.path` giữa runtime, dùng reflection vào JDK private members.

**Bước 2 — xác định mô hình deployment.** Nếu toàn bộ plugins là classpath JARs, có thể giữ `URLClassLoader` riêng của plugin dưới parent phù hợp. Nếu hệ thống muốn **named modules**, thiết kế `ModuleLayer`/module graph/`uses`/`provides`. Không cần module hóa mọi thư viện chỉ để chạy trên JDK 17.

**Bước 3 — định nghĩa shared API identity.** Đảm bảo host và plugins cùng resolve API types (interfaces, DTOs, exceptions) qua một defining loader được đồng ý. Kiểm tra `Class.getClassLoader()` và `Class.getModule()` khi debug.

**Bước 4 — sửa resource discovery.** Dùng đúng `META-INF/services` trên classpath hoặc module `uses/provides` trong named modules. Không giả định một loader nhìn thấy resource trong mọi named module; xét package opens.

**Bước 5 — loại phụ thuộc internals.** Thay reflective hacks bằng public Java APIs khi có, nâng bytecode/framework dependencies. Sử dụng `--add-opens` tạm thời, giới hạn đúng module/package/caller, có kế hoạch loại bỏ.

**Bước 6 — kiểm tra multi-release/version.** Phân biệt `javac --release 8` (target API/bytecode cũ) và JDK 17 runtime behavior. Kiểm tra `major version` của artifacts, thử trên JDK 8 nếu vẫn tuyên bố hỗ trợ.

**Bước 7 — test trong đúng loader graph.** IDE thường đưa JAR plugin lên host classpath, làm parent-first trả class của host. Phải test với host/plugin artifacts tách riêng, xác nhận defining loaders/module layers.

**Bước 8 — quản lý lifecycle.** Khi reload plugin, tạo loader/layer generation mới phù hợp, drain tasks và release references. `URLClassLoader.close()` không tự unload hết classes ngay; `ServiceLoader.reload()` chỉ xóa provider cache.

**Bước 9 — tạo diagnostics.** In requested class, lookup loader/TCCL, runtime defining loader, named/unnamed module, CodeSource location nếu có, cause chain, artifact ID/version và JDK release. Không lộ secrets/log URLs vô kiểm soát.

**Bước 10 — tách security boundary.** Nếu plugin là code không tin cậy, module/class loader chỉ là phần của architecture, không thay cho OS sandbox/process separation.

## 24. Bảng quyết định: dùng công nghệ nào?

| Nhu cầu | Java 8-style giải pháp | Trên JDK hiện đại nên cân nhắc |
| --- | --- | --- |
| Nạp một JAR plugin động | `URLClassLoader` riêng | Vẫn dùng được cho classpath plugin; chọn parent/close hợp lý |
| Cách ly hai phiên bản thư viện | Child loader tùy biến | Child loaders hoặc module layers tùy packaging |
| Mô tả API dependencies/module boundary | Maven/Gradle + classpath | Thêm `module-info.java` khi cần named modules |
| Plugin discovery | `META-INF/services` | Descriptor hoặc module `uses/provides`, ServiceLoader |
| Tạo runtime class cùng package host | Custom `defineClass`/framework hacks | `MethodHandles.Lookup.defineClass` với đúng lookup privileges |
| Sinh implementation class không cần binary name discovery | Framework-specific code gen | Hidden class API trong trường hợp phù hợp |
| Duyệt JDK classes | Đọc `rt.jar` | `jrt:`, `ModuleReader`, `jimage`/`jmod` tools |
| Quan sát loader | `Class.getClassLoader()` | Thêm `getModule()`/module layer, loader name/code source |
| Dùng JDK internals | Reflection hack | Thay bằng supported API, tránh `--add-opens` thường trực |
| Bảo mật plugin không đáng tin | Security Manager/class loader cũ | OS-sandboxed separate process/container/VM với IPC |
| Duy trì thư viện cho Java 8 và 17 | Java 8 bytecode | `--release 8` + test trên cả JDK 8/17; cân nhắc multi-release JAR |

**Không có một “công nghệ thay thế ClassLoader” toàn diện.** Module layer quản lý graph và encapsulation; ClassLoader định nghĩa/tra cứu runtime types; process boundary quản lý fault/security isolation ở cấp OS. Chọn công cụ theo ranh giới cần kiểm soát.

## 25. Những ngộ nhận điển hình sau nâng JDK

1. **“Java 9 bỏ classpath.”** Sai: classpath/unnamed modules vẫn hỗ trợ rộng rãi.
2. **“Extension loader mất hẳn nên mọi parent đều bootstrap.”** Sai: extension mechanism bị loại bỏ, nhưng **platform class loader** tồn tại.
3. **“Platform loader định nghĩa mọi class của Java SE.”** Sai: có classes do bootstrap định nghĩa; platform loader chỉ đảm bảo visibility cần thiết.
4. **“`getSystemClassLoader()` luôn trả `URLClassLoader`.”** Không đúng ở modular JDK và chưa từng là Java SE contract.
5. **“`ClassLoader.getPlatformClassLoader()` có trên Java 8.”** Sai: thêm từ Java 9.
6. **“`rt.jar` vẫn có thể đọc nếu tìm đúng thư mục JDK 17.”** Sai: modular runtime image dùng cấu trúc khác.
7. **“`jrt:` chính là JAR URL.”** Sai: filesystem/scheme riêng của runtime image.
8. **“`Class.getModule().getName()==null` là lỗi.”** Sai: unnamed module hợp lệ.
9. **“Dùng `module-info.java` thì class loader identity không còn ý nghĩa.”** Sai: binary name + defining loader vẫn quan trọng.
10. **“`public` luôn truy cập được từ mọi module.”** Sai: còn `exports`/readability.
11. **“`--add-opens` sửa classpath thiếu class.”** Sai: nó mở deep reflection, không thêm missing dependency.
12. **“`--add-exports` và `--add-opens` giống nhau.”** Không; một cái export public API, cái kia cho deep reflection theo scope.
13. **“`loadClass` chạy static initializer.”** Không theo implementation chuẩn; `Class.forName(name,true,loader)` mới yêu cầu initialize.
14. **“Module layer là sandbox.”** Không; plugin vẫn thường cùng JVM/OS permissions.
15. **“Chỉ cần chuyển dependency lên module path là mọi JAR lập tức modular với access giống named module.”** Không; automatic modules, explicit modules và unnamed modules có semantics khác nhau.
16. **“JDK 17 cho phép illegal reflective access cũ chỉ bằng một flag tổng quát.”** Không đúng với JEP 403 strong encapsulation.
17. **“Mọi công cụ JDK 8 sẽ chạy unchanged chỉ cần build `--release 8`.”** Không nếu nó hardcode runtime internals, directory layout hoặc reflection.
18. **“`ServiceLoader.reload()` redefines provider class.”** Không; nó clear cache, không thay runtime definition.
19. **“Một JAR trên module path chắc chắn được resolution và load.”** Không; cần module roots, dependencies và graph phù hợp.
20. **“Java 8 và Java 17 classfile cùng major version.”** Sai: 52 so với 61; JDK 21 major 65, JDK 25 major 69.

## 26. Câu hỏi tự kiểm tra

**1. Thay đổi class loader built-in nổi bật ở Java 9 là gì?** Extension loader được thay bằng **platform loader** trong mô hình mới; app loader built-in không còn là `URLClassLoader`.

**2. Java 9 có xóa parent delegation hay không?** Không; Java vẫn có delegation, dù module-aware loading phức tạp hơn.

**3. Có nên cast system loader sang `URLClassLoader` trên Java 17?** Không. Dùng custom `URLClassLoader` do ứng dụng sở hữu nếu cần URL-based plugin loading.

**4. Khi một custom loader cần platform classes nhưng không muốn app dependencies, parent nào nên cân nhắc?** `ClassLoader.getPlatformClassLoader()`.

**5. `Class.getModule()` có từ Java mấy?** Java 9.

**6. Class chạy trên `-cp` ở JDK 17 có thuộc module không?** Có; thường thuộc **unnamed module**.

**7. JDK 17 còn `rt.jar` ở vị trí Java 8 không?** Không; modular runtime image và `jrt:`.

**8. `String.class.getClassLoader()==null` vẫn hợp lệ trên Java 17?** Có; thường là bootstrap representation.

**9. Major classfile của Java 8, 17, 21 và 25 là gì?** 52, 61, 65, 69.

**10. `--release 8` làm app loader Java 17 thành Java 8 app loader?** Không; compile target và runtime behavior là hai việc khác.

**11. Deep reflection vào JDK internal `java.base/java.lang` từ unnamed module có thể bị gì trên Java 17?** `InaccessibleObjectException` trừ khi có openness được cấp hợp lệ.

**12. Java 9 thêm cách nào để define class cùng package/loader với lookup class?** `MethodHandles.Lookup.defineClass`.

**13. Named modules dùng API nào để khai báo service providers?** `uses`/`provides ... with ...`.

**14. ModuleLayer có tự tách heap/process cho plugin không?** Không; cần process boundary nếu yêu cầu isolation mạnh hơn.

**15. Làm sao xác định JDK runtime class resource trên modular runtime?** Sử dụng `jrt:`/Module APIs thay vì hardcode `rt.jar`.

**16. Tên class giống nhau nhưng hai defining loaders khác nhau có cùng runtime type identity không?** Không.

**17. `ServiceLoader.reload()` có làm version mới của class tự xuất hiện nếu loader cũ đã define class không?** Không.

**18. Vì sao resource tồn tại trong named module nhưng `ClassLoader.getResource` vẫn trả null?** Package resource có thể chưa được opened thích hợp theo module encapsulation.

**19. Class loader riêng có đảm bảo mã plugin không thể truy cập file/network không?** Không; cần OS permissions/sandbox.

**20. Khi di chuyển Java 8 → 17 nên test trên môi trường nào?** Cả build/runtime target cần hỗ trợ, đúng module/classpath configuration và loader graph thực tế, không chỉ IDE.

## 27. Nguồn chính thức và mốc kỹ thuật

- [Oracle JDK 17 Migration Guide — Migrating from JDK 8](https://docs.oracle.com/en/java/javase/17/migrate/migrating-jdk-8-later-jdk-releases.html): khác biệt class loaders, modular runtime images, extension mechanisms và strong encapsulation.
- [Oracle JDK 9 Migration Guide](https://docs.oracle.com/javase/9/migrate/): mốc Java 8 → 9, tên loader, `rt.jar` và module system.
- [JEP 261 — Module System](https://openjdk.org/jeps/261): JPMS, module path và module-aware class loading.
- [JEP 220 — Modular Run-Time Images](https://openjdk.org/jeps/220): JDK image/JRT và thay đổi layout từ JARs.
- [JEP 238 — Multi-Release JAR Files](https://openjdk.org/jeps/238): JAR version-specific class entries.
- [JEP 403 — Strongly Encapsulate JDK Internals](https://openjdk.org/jeps/403): strong encapsulation ở JDK 17.
- [JEP 411 — Deprecate the Security Manager for Removal](https://openjdk.org/jeps/411): JDK 17 deprecation.
- [JEP 486 — Permanently Disable the Security Manager](https://openjdk.org/jeps/486): JDK 24 security manager change.
- [JEP 371 — Hidden Classes](https://openjdk.org/jeps/371): API phục vụ dynamic runtime code generation từ JDK 15.
- [Java SE 17 ClassLoader Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `getPlatformClassLoader`, `getSystemClassLoader`, module-aware resource/findClass APIs.
- [Java SE 17 Class Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getModule`, `forName`, runtime class metadata.
- [Java SE 25 ClassLoader Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): hợp đồng ClassLoader trên JDK hiện đại.
- [Java Virtual Machine Specification 17, Chapter 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): loading/linking/initialization và runtime type identity.

**Kết luận:** Java 8 → Java hiện đại **không xóa ClassLoader**; nó chuyển runtime thành hệ **modules**, đổi implementation loader mặc định, loại `rt.jar` và extension dirs, bổ sung **platform loader**, module metadata/layers, rồi siết access vào JDK internals. Điều không đổi là **binary name + defining loader quyết định runtime type**, cùng các nguyên tắc loading/linking/initialization và nhu cầu quản lý class loader lifecycle. Khi nâng cấp, hãy **bỏ cast system loader**, ngừng hardcode JDK JARs, xác định classpath/module path rõ ràng và log đồng thời **loader + module + CodeSource** để chẩn đoán chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-005">Thuật ngữ defining loader, initiating loader, runtime package và layer</a>

<details>
<summary>Xem chi tiết</summary>

# Thuật ngữ defining loader, initiating loader, runtime package và layer

## 1. Bốn thuật ngữ không được đánh đồng

Trong Java, một chuỗi tên lớp hoặc tên package không đủ xác định thực thể lúc JVM chạy. Các thuật ngữ được quy định rõ ở JVMS 17, mục 5.3 và 5.3.6:

| Thuật ngữ | Định nghĩa | Đặc điểm |
|---|---|---|
| **Defining loader** | Loader thực sự **định nghĩa** một runtime class/interface từ representation của lớp | Xác định identity của class cùng với binary name |
| **Initiating loader** | Loader **khởi xướng** quá trình nạp một class, trực tiếp hoặc thông qua delegation | Có thể khác defining loader; JVM dùng trong loading constraints |
| **Run-time package** | Cặp **(package name, defining loader)** | Không đồng nhất với tên package trong Java source |
| **Module layer** | Tập runtime modules được tạo từ `Configuration` và ánh xạ module name tới loader | Một loader có thể phục vụ nhiều named modules |

Ba đẳng thức ý niệm:

~~~text
Runtime class identity = (binary name, defining ClassLoader)
Run-time package      = (package name, defining ClassLoader)
Module layer          = (runtime modules, module-to-loader mapping)
~~~

Vì thế hai class đều có tên `demo.plugin.Worker` có thể khác nhau hoàn toàn về runtime identity; hai package tên `demo.plugin` cũng có thể khác run-time package; một ModuleLayer không nhất thiết chỉ chứa một ClassLoader.

## 2. Defining loader

Một loader L thực sự tạo runtime class C bằng cách nạp representation thích hợp và nhờ JVM định nghĩa C được gọi là **defining loader**. Với `Class<?> clazz`, method `clazz.getClassLoader()` trả về defining loader; kết quả `null` biểu diễn bootstrap loader khi thích hợp.

~~~text
PluginLoader L1 định nghĩa demo.plugin.Worker v1 → C1
PluginLoader L2 định nghĩa demo.plugin.Worker v2 → C2

C1.getName().equals(C2.getName()) == true
C1 == C2                              == false
C1.getClassLoader() != C2.getClassLoader()
~~~

Class identity không do tên JAR, đường URL hoặc tên file quyết định trực tiếp. Cùng một binary name nhưng khác defining loaders là hai runtime classes; một object thuộc implementation v1 không thể tự ép thành implementation v2. Ngược lại, hai plugin có thể trao đổi qua một interface được loader cha định nghĩa chung.

**Phân biệt với `ClassLoader.getParent()`:** parent của loader chỉ là tham chiếu dùng cho một số delegation paths. Defining loader là vai trò gắn với một runtime class cụ thể. Child có thể định nghĩa implementation class nhưng yêu cầu parent định nghĩa public API class.

## 3. Initiating loader và chuỗi delegation

Một loader có thể được JVM yêu cầu tải class tên N, sau đó delegate cho một loader khác. Loader được yêu cầu ban đầu là **initiating loader** theo khái niệm JVM; loader cuối cùng định nghĩa class là **defining loader**, và cũng là initiating loader.

~~~text
JVM cần demo.api.Api qua loader CHILD
             |
             v
CHILD.loadClass("demo.api.Api")
             |
        delegate parent
             |
             v
PARENT định nghĩa demo.api.Api
             |
             v
Trả Class<?> đã định nghĩa về CHILD

Defining loader: PARENT
Initiating loaders trong mô hình này: CHILD, PARENT
~~~

JVMS nêu rõ: nếu yêu cầu bắt đầu ở L1 và loader L2 định nghĩa class, cả L1 và L2 là initiating loaders. **Những loader chỉ là trung gian trong chuỗi delegation không mặc nhiên được ghi nhận là initiating loaders**; vì vậy không suy ra mọi parent trung gian đều có vai trò ấy.

### Vì sao JVM cần initiating loader?

Nó giúp JVM duy trì **loading constraints** để kiểm tra an toàn kiểu khi hai classes từ hai loaders tương tác qua method descriptors và field descriptors. Các ký hiệu của JVM:

- `<N, Ld>`: type có binary name N, được defining loader Ld định nghĩa.
- `N^Li`: type name N được xét qua initiating loader Li.
- Constraint như `N^L1 = N^L2` yêu cầu hai cách resolve tên có liên quan biểu diễn cùng runtime type. Vi phạm có thể gây `LinkageError`.

**Giới hạn quan sát:** Java SE 17 không có public API thông dụng `Class.getInitiatingLoaders()`. `Class.getClassLoader()` cho biết **defining loader**, không phải tập initiating loaders mà VM ghi nhận. Gọi `child.loadClass` rồi nhận Class object của parent chứng minh **delegation/defining loader**, nhưng không phải phép đọc trực tiếp VM initiating-loader records.

## 4. Run-time package là cặp package name và defining loader

Theo JVMS, runtime package không chỉ là chữ `package com.example;` trong source. Nó được xác định bằng:

~~~text
(package name, defining ClassLoader)
~~~

Ví dụ:

~~~text
L1 định nghĩa demo.same.A → run-time package (demo.same, L1)
L2 định nghĩa demo.same.B → run-time package (demo.same, L2)

Tên package giống?          Có
Run-time package giống?     Không
~~~

Package-private access yêu cầu runtime package phù hợp, không chỉ FQCN prefix giống nhau. Nếu bytecode từ L2 cố truy cập package-private member của class do L1 định nghĩa, JVM có thể ném `IllegalAccessError`, kể cả khi cả hai source files đều viết `package demo.same;`.

Kiểm tra thực tế:

~~~java
// Mã minh họa, khi đã có Class<?> a, b.
boolean sameRuntimePackage =
        a.getPackageName().equals(b.getPackageName())
        && a.getClassLoader() == b.getClassLoader();
~~~

`Class.getPackageName()` chỉ trả **tên**, còn `Class.getPackage()` trả metadata `Package`, không phải định danh run-time package của JVMS. Muốn kiểm chứng quyền package-private, cần xét **cả tên package lẫn defining loader**, và các quy tắc module/access control liên quan.

**Quan hệ với JPMS:** Một run-time package thuộc đúng một runtime module. Khi nhiều named modules cùng một ClassLoader, JVM không cho một run-time package bị “chia” qua nhiều modules; vì vậy mapping hai modules có cùng package vào một loader có thể khiến `LayerInstantiationException`. Nhưng cùng package name ở hai loaders khác nhau vẫn có thể tồn tại vì chúng là hai run-time packages khác nhau.

## 5. Ví dụ Java 17+ số 1: hai JAR, một binary name

Ví dụ so sánh hai class cùng `demo.plugin.Worker` được định nghĩa bởi hai `URLClassLoader` khác nhau, và cùng dùng shared API do parent định nghĩa.

### 5.1. Cấu trúc

~~~text
loader-glossary/
├── api-src/demo/api/Api.java
├── v1-src/demo/plugin/Worker.java
├── v2-src/demo/plugin/Worker.java
└── host-src/LoaderGlossaryDemo.java
~~~

**api-src/demo/api/Api.java**:

~~~java
package demo.api;

public interface Api {
    String message();
}
~~~

**v1-src/demo/plugin/Worker.java**:

~~~java
package demo.plugin;

import demo.api.Api;

public final class Worker implements Api {
    public Worker() {
    }

    @Override
    public String message() {
        return "v1";
    }
}
~~~

**v2-src/demo/plugin/Worker.java**:

~~~java
package demo.plugin;

import demo.api.Api;

public final class Worker implements Api {
    public Worker() {
    }

    @Override
    public String message() {
        return "v2";
    }
}
~~~

**host-src/LoaderGlossaryDemo.java**:

~~~java
import demo.api.Api;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class LoaderGlossaryDemo {
    private LoaderGlossaryDemo() {
    }

    private static boolean sameRuntimePackage(
            Class<?> left, Class<?> right) {
        return left.getPackageName().equals(right.getPackageName())
                && left.getClassLoader() == right.getClassLoader();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: LoaderGlossaryDemo <v1.jar> <v2.jar>");
        }

        ClassLoader parent = Api.class.getClassLoader();

        URL url1 = Path.of(args[0]).toUri().toURL();
        URL url2 = Path.of(args[1]).toUri().toURL();

        try (URLClassLoader l1 =
                     new URLClassLoader(new URL[]{url1}, parent);
             URLClassLoader l2 =
                     new URLClassLoader(new URL[]{url2}, parent)) {

            Class<?> c1 = l1.loadClass("demo.plugin.Worker");
            Class<?> c2 = l2.loadClass("demo.plugin.Worker");

            Api p1 = (Api) c1.getConstructor().newInstance();
            Api p2 = (Api) c2.getConstructor().newInstance();

            Class<?> delegatedApi = l1.loadClass("demo.api.Api");

            System.out.println("Same binary name: "
                    + c1.getName().equals(c2.getName()));
            System.out.println("Same Class object: " + (c1 == c2));
            System.out.println("Different defining loaders: "
                    + (c1.getClassLoader() != c2.getClassLoader()));
            System.out.println("Same package name: "
                    + c1.getPackageName().equals(c2.getPackageName()));
            System.out.println("Same runtime package: "
                    + sameRuntimePackage(c1, c2));
            System.out.println("API delegated to parent: "
                    + (delegatedApi == Api.class));
            System.out.println("API defining loader is parent: "
                    + (delegatedApi.getClassLoader() == parent));
            System.out.println("Shared API cast works: "
                    + (p1 instanceof Api && p2 instanceof Api));
            System.out.println("Plugin values: "
                    + p1.message() + "," + p2.message());
            System.out.println("Distinct unnamed modules: "
                    + (c1.getModule() != c2.getModule()));
            System.out.println("Unnamed modules have no layer: "
                    + (c1.getModule().getLayer() == null
                    && c2.getModule().getLayer() == null));
        }
    }
}
~~~

**Tại sao hai plugin JAR không được đặt vào host classpath?** Vì `URLClassLoader` mặc định parent-first. Nếu parent đã có `demo.plugin.Worker`, cả hai child loaders có thể nhận bản của parent thay vì tự định nghĩa hai runtime classes. Hai JAR chỉ chứa implementation; **không** đóng gói `Api.class` bên trong chúng.

### 5.2. Bash: build và chạy

Từ thư mục `loader-glossary` với JDK 17+:

~~~bash
mkdir -p build/api build/host build/v1 build/v2 dist

javac -encoding UTF-8 -d build/api api-src/demo/api/Api.java

javac -encoding UTF-8 -cp build/api \
  -d build/v1 v1-src/demo/plugin/Worker.java

javac -encoding UTF-8 -cp build/api \
  -d build/v2 v2-src/demo/plugin/Worker.java

javac -encoding UTF-8 -cp build/api \
  -d build/host host-src/LoaderGlossaryDemo.java

jar --create --file dist/v1.jar -C build/v1 .
jar --create --file dist/v2.jar -C build/v2 .

java -cp "build/api:build/host" \
  LoaderGlossaryDemo dist/v1.jar dist/v2.jar
~~~

### 5.3. Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force build/api,build/host,build/v1,build/v2,dist | Out-Null

javac -encoding UTF-8 -d build/api api-src/demo/api/Api.java

javac -encoding UTF-8 -cp build/api -d build/v1 v1-src/demo/plugin/Worker.java
javac -encoding UTF-8 -cp build/api -d build/v2 v2-src/demo/plugin/Worker.java
javac -encoding UTF-8 -cp build/api -d build/host host-src/LoaderGlossaryDemo.java

jar --create --file dist/v1.jar -C build/v1 .
jar --create --file dist/v2.jar -C build/v2 .

java -cp "build/api;build/host" LoaderGlossaryDemo dist/v1.jar dist/v2.jar
~~~

### 5.4. Stdout chính xác

~~~text
Same binary name: true
Same Class object: false
Different defining loaders: true
Same package name: true
Same runtime package: false
API delegated to parent: true
API defining loader is parent: true
Shared API cast works: true
Plugin values: v1,v2
Distinct unnamed modules: true
Unnamed modules have no layer: true
~~~

Các dòng này kiểm chứng **cùng binary name nhưng khác runtime classes**, **cùng package name nhưng khác run-time packages**, **parent định nghĩa shared API**, và **mỗi plugin loader có unnamed module riêng**.

Nó chỉ quan sát defining-loader/delegation qua public APIs; không tự đọc initiating-loader records bên trong JVM.

## 6. ModuleLayer và Module không phải ClassLoader

Java 9+ có JPMS, trong đó `ModuleLayer` là nhóm runtime named modules được tạo từ `Configuration` và phép ánh xạ **module name → ClassLoader**.

~~~text
LAYER ONE                            LAYER MANY

demo.alpha ──┐                       demo.alpha → Loader LA
             ├── Loader L
demo.beta ───┘                       demo.beta  → Loader LB
~~~

Các APIs tiêu biểu:

| API | Ý nghĩa |
|---|---|
| `ModuleLayer.boot()` | Boot layer của JVM |
| `layer.findModule("demo.alpha")` | Module object tương ứng |
| `layer.findLoader("demo.alpha")` | Loader được gắn với module |
| `module.getClassLoader()` | Loader của runtime module |
| `module.getLayer()` | Layer của named module |
| `layer.parents()` | Parent ModuleLayers |
| `layer.defineModulesWithOneLoader(cf, parentLoader)` | Một loader **mới** cho nhiều modules |
| `layer.defineModulesWithManyLoaders(cf, parentLoader)` | Một loader **mới** cho từng module |
| `layer.defineModules(cf, mapper)` | Mapping do framework cung cấp |

**Quan trọng:** `parentLoader` trong các convenience APIs nói về **parent của loader mới**, không phải loader trực tiếp định nghĩa modules. `layer.parents()` không tương đương `loader.getParent()`: đây là hai quan hệ khác nhau.

Các modules dùng chung loader vẫn có ranh giới `requires`, `exports` và `opens` riêng. Một loader có thể gắn nhiều named modules và vẫn có `ClassLoader.getUnnamedModule()` của chính nó. Một class thuộc đúng một runtime module thông qua runtime package tương ứng.

## 7. Ví dụ Java 17+ số 2: named modules và layers

Tạo hai named modules: `demo.alpha` xuất public API; `demo.beta` có `requires demo.alpha` và gọi alpha. Host tạo hai layers: một layer dùng chung loader, một layer dùng loader riêng.

### 7.1. Cấu trúc

~~~text
module-layer-glossary/
├── src/demo.alpha/module-info.java
├── src/demo.alpha/demo/alpha/Alpha.java
├── src/demo.beta/module-info.java
├── src/demo.beta/demo/beta/Beta.java
└── host-src/ModuleLayerGlossary.java
~~~

**src/demo.alpha/module-info.java**:

~~~java
module demo.alpha {
    exports demo.alpha;
}
~~~

**src/demo.alpha/demo/alpha/Alpha.java**:

~~~java
package demo.alpha;

public final class Alpha {
    private Alpha() {
    }

    public static String value() {
        return "ALPHA";
    }
}
~~~

**src/demo.beta/module-info.java**:

~~~java
module demo.beta {
    requires demo.alpha;
    exports demo.beta;
}
~~~

**src/demo.beta/demo/beta/Beta.java**:

~~~java
package demo.beta;

import demo.alpha.Alpha;

public final class Beta {
    private Beta() {
    }

    public static String call() {
        return Alpha.value() + ":BETA";
    }
}
~~~

**host-src/ModuleLayerGlossary.java**:

~~~java
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;

public final class ModuleLayerGlossary {
    private ModuleLayerGlossary() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: ModuleLayerGlossary <jar-dir>");
        }

        ModuleLayer boot = ModuleLayer.boot();
        ModuleFinder finder = ModuleFinder.of(Path.of(args[0]));
        Configuration cf = boot.configuration().resolve(
                finder, ModuleFinder.of(), Set.of("demo.beta"));

        ClassLoader parent = ClassLoader.getSystemClassLoader();

        ModuleLayer one =
                boot.defineModulesWithOneLoader(cf, parent);
        ClassLoader oneAlpha = one.findLoader("demo.alpha");
        ClassLoader oneBeta = one.findLoader("demo.beta");
        Class<?> alphaOne = oneAlpha.loadClass("demo.alpha.Alpha");
        Class<?> betaOne = oneBeta.loadClass("demo.beta.Beta");

        ModuleLayer many =
                boot.defineModulesWithManyLoaders(cf, parent);
        ClassLoader manyAlpha = many.findLoader("demo.alpha");
        ClassLoader manyBeta = many.findLoader("demo.beta");
        Class<?> alphaMany =
                manyAlpha.loadClass("demo.alpha.Alpha");
        Class<?> betaMany =
                manyBeta.loadClass("demo.beta.Beta");

        System.out.println("One loader shared: "
                + (oneAlpha == oneBeta));
        System.out.println("One modules distinct: "
                + (alphaOne.getModule() != betaOne.getModule()));
        System.out.println("Alpha module named: "
                + alphaOne.getModule().isNamed());
        System.out.println("Alpha belongs to one layer: "
                + (alphaOne.getModule().getLayer() == one));
        System.out.println("Boot parent layer: "
                + one.parents().contains(boot));
        System.out.println("Many loaders distinct: "
                + (manyAlpha != manyBeta));
        System.out.println("Many class loaders mapped: "
                + (alphaMany.getClassLoader() == manyAlpha
                && betaMany.getClassLoader() == manyBeta));

        String result = (String) betaMany.getMethod("call")
                .invoke(null);
        System.out.println("Beta calls alpha: " + result);

        System.out.println("Alpha Class same across layers: "
                + (alphaOne == alphaMany));
        System.out.println("Alpha Module same across layers: "
                + (alphaOne.getModule() == alphaMany.getModule()));
    }
}
~~~

### 7.2. Bash

Chạy từ thư mục `module-layer-glossary`:

~~~bash
mkdir -p mods host dist

javac -encoding UTF-8 --module-source-path src -d mods \
  src/demo.alpha/module-info.java \
  src/demo.alpha/demo/alpha/Alpha.java \
  src/demo.beta/module-info.java \
  src/demo.beta/demo/beta/Beta.java

javac -encoding UTF-8 -d host host-src/ModuleLayerGlossary.java

jar --create --file dist/demo.alpha.jar -C mods/demo.alpha .
jar --create --file dist/demo.beta.jar -C mods/demo.beta .

java -cp host ModuleLayerGlossary dist
~~~

### 7.3. Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force mods,host,dist | Out-Null

javac -encoding UTF-8 --module-source-path src -d mods src/demo.alpha/module-info.java src/demo.alpha/demo/alpha/Alpha.java src/demo.beta/module-info.java src/demo.beta/demo/beta/Beta.java

javac -encoding UTF-8 -d host host-src/ModuleLayerGlossary.java

jar --create --file dist/demo.alpha.jar -C mods/demo.alpha .
jar --create --file dist/demo.beta.jar -C mods/demo.beta .

java -cp host ModuleLayerGlossary dist
~~~

Host không đặt modular JARs vào `-cp`, mà sử dụng `ModuleFinder.of(dist)` để resolve và tạo layer mới.

### 7.4. Stdout

~~~text
One loader shared: true
One modules distinct: true
Alpha module named: true
Alpha belongs to one layer: true
Boot parent layer: true
Many loaders distinct: true
Many class loaders mapped: true
Beta calls alpha: ALPHA:BETA
Alpha Class same across layers: false
Alpha Module same across layers: false
~~~

Đọc kết quả:

- Layer `one` đặt cả hai named modules vào **một loader**, nhưng modules vẫn là hai runtime objects khác nhau.
- Layer `many` đặt mỗi module vào một loader mới; beta vẫn gọi alpha nhờ `requires`/exports và direct delegation mà loader của layer hỗ trợ.
- Hai layers được tạo độc lập, class alpha và runtime module alpha ở mỗi layer khác identity.
- Layer creation không tự có nghĩa JVM eager-load toàn bộ classes; `loadClass` mới yêu cầu type cụ thể.

## 8. Tầng package, module và layer trong JPMS

Mối quan hệ nên đọc như sau:

~~~text
Runtime class
  |
  | binary name + defining loader
  v
Run-time package
  |
  | package name + defining loader
  v
Runtime Module (named/unnamed)
  |
  v
ModuleLayer (đối với named modules của layer)
~~~

Một run-time package thuộc **một runtime module**. Một runtime module được gắn với đúng một loader, nhưng một loader có thể phục vụ nhiều runtime modules. Một loader cũng có thể được gắn với modules từ nhiều layers trong một custom mapping hợp lệ.

Nếu gộp hai modules có overlapping packages vào cùng loader, `defineModulesWithOneLoader` có thể ném `LayerInstantiationException`. Split delegation (một loader phải tìm cùng package ở hơn một target loader) cũng là một giới hạn. Các constraints này không có nghĩa mọi JVM cấm hai loaders khác nhau định nghĩa cùng package name.

Cần phân biệt:

- **Package-private**: liên quan run-time package.
- **Module readability/exports/opens**: kiểm soát truy cập qua named-module boundary.
- **Loader visibility**: một loader có tìm được bytecode hoặc class qua delegation không.
- **Authorization** của người dùng cuối: chính sách ứng dụng; tìm thấy class/resource không có nghĩa người dùng được phép sử dụng.

## 9. Bảng chẩn đoán lỗi

| Lỗi hoặc dấu hiệu | Vấn đề có thể là |
|---|---|
| `ClassCastException` giữa hai tên class giống nhau | Hai defining loaders khác nhau tạo hai runtime types |
| `IllegalAccessError` khi gọi package-private member | Hai classes không cùng run-time package dù package names trùng |
| `LinkageError` liên quan loader constraint | Initiating-loader type resolutions không nhất quán |
| `NoClassDefFoundError` trong plugin | Loader đang resolve không nhìn thấy dependency đúng |
| `LayerInstantiationException` | Overlapping packages/split delegation/module mapping |
| `InaccessibleObjectException` | Thiếu module `opens` cho reflection |
| `IllegalAccessError` với public API named module | `requires` hoặc `exports` không phù hợp |
| `Class.getClassLoader() == null` | Lớp được bootstrap loader định nghĩa |
| Hai Module objects cùng name nhưng khác `==` | Hai runtime modules (có thể thuộc layers khác nhau) |

Không sửa bằng cách bắt `LinkageError` rồi tiếp tục vô điều kiện. Hãy kiểm tra classloader topology, runtime API identity, module graph và packaging.

## 10. Các ngộ nhận cần loại bỏ

| Nhận định sai | Giải thích đúng |
|---|---|
| Loader gọi `loadClass` chắc chắn define class | Nó có thể delegate cho parent |
| `Class.getClassLoader()` trả initiating loader | Trả defining loader |
| Mọi loader trung gian là initiating loader | JVMS không tự tính mọi trung gian |
| Cùng binary name tức cùng runtime type | Còn cần defining loader |
| Cùng package name tức cùng run-time package | Còn cần defining loader |
| Một loader chỉ phục vụ một module | Một loader có thể có nhiều named modules |
| Một ModuleLayer chính là một ClassLoader | Layer chứa modules, ánh xạ tới một/nhiều loaders |
| Parent layer bằng parent ClassLoader | Hai quan hệ khác nhau |
| Tạo layer là đã load mọi class trong đó | Module definition và class loading khác nhau |
| Named module khiến loader mất unnamed module | Loader vẫn có unnamed module riêng |
| TCCL quyết định identity của class | Defining loader mới quyết định runtime class identity |

## 11. Câu hỏi tự kiểm tra

**1.** Child yêu cầu Api, parent định nghĩa. Ai là defining loader?

**Đáp án:** Parent. Child có thể là initiating loader trong tiến trình nạp qua delegation.

**2.** Hai Classes `demo.plugin.Worker` cùng tên, khác defining loaders có cùng type?

**Đáp án:** Không. Class identity là binary name + defining loader.

**3.** Hai Classes có `package demo.same;` nhưng do hai loaders định nghĩa, cùng run-time package không?

**Đáp án:** Không. Tên package và defining loader phải cùng nhau trùng khớp.

**4.** `getClassLoader()` có cho biết mọi initiating loaders của một class không?

**Đáp án:** Không. Nó trả defining loader, còn initiating records thuộc ngữ nghĩa JVM.

**5.** Một layer có hai modules dùng một loader được không?

**Đáp án:** Có, nếu mapping hợp lệ, ví dụ `defineModulesWithOneLoader`.

**6.** Hai modules khác loader vẫn có thể giao tiếp?

**Đáp án:** Có, nếu readability/exports hợp lệ và loader delegation được tổ chức đúng.

**7.** Hai Module objects cùng tên ở hai layers có nhất thiết bằng nhau không?

**Đáp án:** Không. So sánh runtime identity bằng object tham chiếu.

**8.** Vì sao package-private access có thể thất bại dù source package giống nhau?

**Đáp án:** Vì defining loaders khác nhau tạo run-time packages khác nhau.

## 12. Tài liệu chính thức

- [JVMS 17 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3): defining và initiating loaders, runtime identities.
- [JVMS 17 — User-defined Loader](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.2): quy tắc loader khởi xướng.
- [JVMS 17 — Loading Constraints](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.4): an toàn kiểu qua loaders.
- [JVMS 17 — Modules and Layers](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.6): runtime module/package/layer.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): delegation và unnamed module.
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): defining loader/package/module introspection.
- [Java SE 17 — ModuleLayer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html): module-to-loader mapping.
- [Java SE 17 — Module](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html): runtime module identity.

**Tóm lại:** Defining loader tạo runtime class; initiating loader khởi xướng việc nạp và có thể khác defining loader. Run-time package gồm tên package **và** defining loader. ModuleLayer nhóm runtime modules với ánh xạ loaders; nó không phải ClassLoader và không bắt buộc một module trên một loader.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-006">Tra cứu JVMS, JLS, Javadoc và công cụ JDK theo chủ đề</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu JVMS, JLS, Javadoc và công cụ JDK theo chủ đề Java ClassLoader

## 1. Mục tiêu: tìm đúng nguồn trước khi giải thích lỗi

Khi nghiên cứu **ClassLoader**, nhiều câu hỏi nhìn giống nhau nhưng thuộc những tầng khác nhau của Java:

- `ClassLoader.loadClass` tìm lớp theo thứ tự nào?
- Vì sao hai class cùng tên không cast được cho nhau?
- Khi nào static initializer được chạy?
- Vì sao `NoSuchMethodError` xuất hiện dù mã nguồn biên dịch thành công?
- `getResourceAsStream` tìm file trong JAR theo tên nào?
- Vì sao module thấy được class nhưng reflection vào private member bị cấm?
- Dùng lệnh nào để kiểm tra classfile, dependency graph hoặc loaded classes thực tế?

Không nên giải tất cả bằng một bài blog hay một lệnh `javap` duy nhất. Để xác định đúng nguyên nhân, cần kết hợp **đặc tả (specification)**, **hợp đồng API**, **công cụ JDK** và **bằng chứng từ runtime**.

Bài này là một **bản đồ tra cứu** dành cho Java 17+ với trọng tâm class loading: cách chọn **JVMS**, **JLS**, **Javadoc**, **JDK tool manuals**, bảng tra theo chủ đề/triệu chứng, một ví dụ có thể tự chạy và một quy trình điều tra tái lập được.

> Nguyên tắc thực hành: **Xác định câu hỏi thuộc tầng nào → đọc nguồn chuẩn của tầng đó → dùng công cụ quan sát artifact/runtime → viết kết luận có điều kiện.** Đừng suy ngược toàn bộ ngữ nghĩa Java từ một dòng log trên một JVM cụ thể.

## 2. Phân biệt bốn nhóm nguồn

| Nguồn | Trả lời tốt nhất về | Ví dụ câu hỏi |
| --- | --- | --- |
| **JVMS — Java Virtual Machine Specification** | Classfile format, bytecode, loading/linking/resolution/initialization ở JVM, runtime class identity, access checks, VM errors | “Tại sao loader khác nhau tạo kiểu runtime khác nhau?”, “`VerifyError` xảy ra ở bước nào?” |
| **JLS — Java Language Specification** | Quy tắc của **ngôn ngữ Java**: tên, scope, access modifiers, initialization observable, source/binary compatibility | “Khi source dùng static field thì class được initialized thế nào?”, “Sửa `public` có ảnh hưởng binary compatibility không?” |
| **Javadoc — Java SE API Documentation** | Hợp đồng của các class/method chuẩn như `ClassLoader`, `Class`, `ModuleLayer`, `ServiceLoader`, `Proxy` | “`loadClass` có gọi `findLoadedClass` rồi parent không?”, “`getResourceAsStream` trả gì nếu resource thiếu?” |
| **JDK Tool Reference / man pages** | Cú pháp và khả năng của công cụ: `java`, `javap`, `jar`, `jdeps`, `jcmd`, `jfr`, `jlink` | “Lệnh nào hiển thị constant pool?”, “Làm sao bật log class loading?” |

Nguồn bổ sung thường gặp:

- **JEP (JDK Enhancement Proposal):** giải thích động lực, thiết kế và lộ trình của một tính năng; rất hữu ích để hiểu *vì sao* module system, CDS hoặc thay đổi JVM xuất hiện, nhưng **không thay** hợp đồng của Javadoc hoặc đặc tả phiên bản đang chạy.
- **Migration guides / troubleshooting guides:** hướng dẫn upgrade JDK, chuyển classpath sang module path, xử lý illegal reflective access; có ích cho tình huống cụ thể nhưng cần đối chiếu với API/spec.
- **Source code OpenJDK:** cung cấp cách *một bản implementation* triển khai, không mặc nhiên là hành vi bắt buộc trên mọi JVM tương thích Java.
- **Stack trace, `javap`, JFR, Unified Logging:** bằng chứng của *ứng dụng đang chạy*; chúng cần được diễn giải dưới các quy tắc của spec/API.

### 2.1. Không trộn “nguồn quy phạm” với “bằng chứng runtime”

Ví dụ một HotSpot build dùng application loader có tên hiển thị nội bộ cụ thể. Javadoc `ClassLoader` quy định cách lấy **defining loader** qua `Class.getClassLoader()`, còn tên class cụ thể của loader do HotSpot triển khai có thể khác theo JDK/distribution. Vì vậy:

~~~text
Thông tin có thể khẳng định:
  two Class<?> objects with same binary name + different defining loaders
  may represent different runtime types.

Không nên khẳng định cho mọi JVM:
  application loader luôn có cùng tên class implementation nội bộ
  hoặc class-loading log luôn có cùng thứ tự và wording.
~~~

Khi viết README/bug report, hãy ghi **Java SE version**, JVM vendor/build, classpath/module path và lệnh quan sát. Khi có xung đột apparent, xác định đang so **source-level language rule**, **bytecode runtime rule**, **API behavior** hay **implementation diagnostic output**.

## 3. Lộ trình đọc JVMS khi học ClassLoader

**Trang gốc Java SE 17:** https://docs.oracle.com/javase/specs/jvms/se17/html/index.html

| Đề tài | Mục JVMS nên mở | Điều cần tìm |
| --- | --- | --- |
| Định danh lớp runtime và defining/initiating loaders | **`5.3 Creation and Loading`** | Cặp (binary name, defining loader); delegation |
| Bootstrap loader, user-defined loader, arrays | **`5.3.1–5.3.3`** | Ai thực sự define Class, đặc thù array class |
| Loader constraints | **`5.3.4`** | Tính thống nhất kiểu khi loaders tương tác qua signatures |
| Tạo Class từ bytes / classfile | **`5.3.5`** và **Chapter 4** | Structural/classfile format constraints |
| Modules/layers khi tải classes | **`5.3.6`** | Tương tác giữa module, package và loaders |
| Linking tổng quát | **`5.4`** | Verification, preparation, resolution, access |
| Bytecode verification | **`5.4.1`**, **`4.9–4.10`** | `VerifyError` và type-state checks |
| Preparation và static field values | **`5.4.2`** | Chuẩn bị field trước `<clinit>` |
| Class, field, method resolution | **`5.4.3.1–5.4.3.4`** | Vì sao có `NoSuchMethodError`, `NoSuchFieldError` |
| Access control của JVM | **`5.4.4`** | `IllegalAccessError`, module/package access |
| Method selection / overriding | **`5.4.5–5.4.6`** | Dispatch và inheritance ở runtime |
| Initialization | **`5.5`** và **`2.9.2`** | `<clinit>`, thứ tự, locks, error state |
| Bytecode syntax và operands | **Chapter 6** | `getstatic`, `putstatic`, `invokestatic`, `new` |
| Runtime data areas | **`2.5`** | Method area, heap, stacks, constant pool |

Các URL trực tiếp có thể lưu trong bookmark:

- Loading: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- Loading constraints: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.4
- Linking: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4
- Resolution: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3
- Access control: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.4
- Initialization: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- Class file format: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
- Instructions: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html

### 3.1. Cách đọc một mục JVMS cho đúng

Thực hiện theo trình tự:

1. Đọc **heading và mục ngay trước/sau** để nhận ra điều kiện ban đầu.
2. Ghi rõ đang xem **lớp chưa load**, **đã load**, **đã verified**, **đang resolution** hay **đã initialized**.
3. Phân biệt **defining loader** với **initiating loader**: một loader có thể yêu cầu nạp, rồi ủy quyền cho parent thực sự define.
4. Ghi nguyên tắc **runtime class identity**: binary name và defining loader; hai loader khác nhau không nhất thiết tạo hai Class khác nhau nếu chúng cùng delegate về một parent.
5. Khi gặp từ `may`, `must`, `may be deferred`, không biến quyền lựa chọn của VM implementation thành một thứ tự bắt buộc cho mọi JVM.
6. Tìm câu quy định **exception/error nào xuất hiện khi điều kiện bị vi phạm**.
7. Dùng Javadoc để xác định ý nghĩa cụ thể của lời gọi API mà ứng dụng sử dụng.

**Bẫy điển hình:** đọc `5.4 Resolution` rồi kết luận “mọi symbolic reference đều phải được resolve trước khi first method run”. JVMS cho phép nhiều hoạt động resolution được thực hiện sớm hoặc muộn, miễn error xuất hiện ở điểm sử dụng phù hợp. Cần phân biệt link-time guarantee với JVM implementation strategy.

## 4. Lộ trình đọc JLS khi học class loading

**Trang gốc Java SE 17:** https://docs.oracle.com/javase/specs/jls/se17/html/index.html

| Đề tài nguồn Java | Mục JLS | Câu hỏi gợi ý |
| --- | --- | --- |
| Class/interface loading | **`12.2`** | Khi nào JVM cần tạo class và các điều kiện đúng |
| Linking | **`12.3`** | Liên kết trong vòng đời Java program |
| Initialization | **`12.4`** | Điều gì làm static initialization xảy ra |
| Instance creation | **`12.5`** | Constructor và initialization instance |
| Class unloading | **`12.7`** | Khi nào class có thể unload |
| Scope/naming và qualified names | **Chapters 6–7** | Binary names, packages, access |
| Access control | **`6.6`** | `public`, `protected`, package-private, private |
| Module declarations | **`7.7`** | `requires`, `exports`, `opens`, `uses`, `provides` |
| Class members/methods | **Chapters 8–9** | `static`, `final`, overriding, interfaces |
| Binary compatibility | **Chapter 13** | Vì sao client đã compile vẫn lỗi khi đổi thư viện |
| Memory model | **`17.4`** | Happens-before; tránh gán nhầm concurrency bug cho verifier |

Liên kết trực tiếp:

- Loading: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2
- Initialization: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4
- Unloading: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7
- Access: https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6
- Modules: https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7
- Binary compatibility: https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html

### 4.1. Lúc nào đọc cả JLS và JVMS?

Ví dụ **class initialization**:

- JLS `12.4` giải thích Java-language semantics, triggers và những gì programmer có thể quan sát.
- JVMS `5.5` diễn tả quy trình JVM, locks và trạng thái khởi tạo, cùng những instruction chủ yếu gây yêu cầu initialization.
- Javadoc `Class.forName` giải thích một **API call cụ thể** với tham số `initialize`.
- `javap -c` cho thấy bytecode compiler tạo; JFR/logs cho biết một process thực sự đã trải qua điều gì.

Mỗi nguồn giải thích **một chiều của cùng hiện tượng**. Không nên đọc duy nhất `Class.forName` Javadoc rồi khẳng định mọi `.class` literal hoặc `loadClass` tự động chạy `<clinit>`.

## 5. Tra Javadoc theo API và triệu chứng

**Java SE 17 API index:** https://docs.oracle.com/en/java/javase/17/docs/api/index.html

| API / loại | Vấn đề gắn với ClassLoader | Trang Javadoc |
| --- | --- | --- |
| `java.lang.ClassLoader` | Parent delegation, `loadClass`, `findClass`, `defineClass`, resources, parallel-capable | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html |
| `java.lang.Class` | `forName`, `getClassLoader`, `getModule`, reflection và init | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html |
| `java.lang.Module` | Module identity, `isExported`, `isOpen`, `getResourceAsStream` | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html |
| `java.lang.ModuleLayer` | Tạo layer mới và chọn loader mapping | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html |
| `java.lang.module.Configuration` | Module resolution và readability graph | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/Configuration.html |
| `java.lang.module.ModuleFinder` | Tìm module artifacts | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/module/ModuleFinder.html |
| `java.util.ServiceLoader` | Provider discovery, `META-INF/services`, service type compatibility | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html |
| `java.util.ServiceConfigurationError` | Invalid provider và các lỗi SPI | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceConfigurationError.html |
| `java.lang.reflect.Proxy` | Dynamic proxy, interface visibility, JPMS restrictions | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html |
| `java.lang.reflect.InvocationHandler` | Method dispatch, default methods và exceptions | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html |
| `java.lang.reflect.AccessibleObject` | `setAccessible`, `trySetAccessible` | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html |
| `java.lang.invoke.MethodHandles.Lookup` | `defineClass`, `defineHiddenClass`, lookup access | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html |
| `java.net.URLClassLoader` | Nạp classes/resources từ các URL roots | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html |
| `java.security.SecureClassLoader` | Code source / protection domains | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureClassLoader.html |
| `java.util.Properties` | Đọc config stream/reader khác encoding | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Properties.html |

### 5.1. Cách đọc Javadoc cho khỏi bỏ sót

Khi vào trang `ClassLoader`:

1. Tìm **Method Summary** rồi click đúng overload `loadClass(String)` hoặc `loadClass(String, boolean)`.
2. Đọc **Implementation Requirements** thay vì chỉ một câu mở đầu.
3. Đọc **Parameters**, **Returns**, **Throws** và **API Note**.
4. Kiểm tra `findClass`, `findLoadedClass`, `resolveClass`, `getClassLoadingLock` nếu làm custom loader.
5. Với resource, đọc riêng `ClassLoader.getResource` và `Class.getResource` vì slash đầu và module semantics **không giống nhau**.
6. Với `Class.forName`, phân biệt overload có `initialize=false` với overload mặc định có thể yêu cầu initialization.

**Quy tắc thực hành:** lời giải cho “`getResourceAsStream` trả null vì sao” nên bắt đầu từ Javadoc API đang gọi, sau đó kiểm tra JAR entries/classpath/module access. Đừng bắt đầu bằng JVMS bytecode verification vì đó không phải tầng trực tiếp xử lý lookup resource.

## 6. Tra cứu công cụ JDK: chọn lệnh theo bằng chứng cần thu

| Công cụ | Nên dùng khi | Ví dụ lệnh/mục |
| --- | --- | --- |
| **`java`** | Chạy cùng artifact, xem classpath/module path, bật class-loading logs | `java -cp out/classes lab.Probe`; `-Xlog:class+load=info` |
| **`javac`** | Kiểm tra source compile với target version/contract nào | `javac --release 17 -d out/classes ...` |
| **`javap`** | Xem classfile, descriptors, method opcodes, constant pool, frames | `javap -c -p -classpath out/classes lab.Probe`; `javap -v` |
| **`jar`** | Kiểm tra nội dung JAR, manifest, module descriptor | `jar --list --file app.jar`; `jar --describe-module --file app.jar` |
| **`jdeps`** | Phân tích dependency theo class/package/module, JDK internals | `jdeps --summary app.jar`; `jdeps --jdk-internals app.jar` |
| **`jcmd`** | Quan sát JVM process đang chạy, thống kê class loader, JFR | `jcmd -l`; `jcmd PID VM.classloader_stats` |
| **`jfr`** | Xem recording và events sau khi thu JFR | `jfr print recording.jfr` |
| **`jlink`** | Tạo runtime image gồm một tập modules | `jlink --help`; chọn modules từ graph |
| **`jmod`** | Quan sát/đóng gói JMOD | `jmod --help` |
| **`jshell`** | Thử nhanh Java API/JVM behavior quy mô nhỏ | `jshell` để thử `Class.forName`/reflection |

Các command manuals Java 17:

- java: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- javac: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html
- javap: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html
- jar: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jar.html
- jdeps: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jdeps.html
- jcmd: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- jfr: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html
- jlink: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jlink.html

**Một số `jcmd` commands là diagnostic commands phụ thuộc JVM implementation, build, process permissions và phiên bản JDK.** Trước khi dùng với production, xem `jcmd PID help` hoặc help riêng cho command; đừng coi tên internal class loader hay thứ tự bảng thống kê là API portable.

## 7. Sơ đồ nhanh: gặp vấn đề nào, mở trang nào?

~~~text
Triệu chứng / câu hỏi
    |
    +-- "Hai class cùng tên nhưng cast không được"
    |       JVMS 5.3 -> ClassLoader/Class Javadoc
    |       -> in Class.getClassLoader/getModule
    |
    +-- "Lỗi bytecode VerifyError"
    |       JVMS 4.9/4.10 + 5.4.1
    |       -> javap -v -p (Code/StackMapTable)
    |
    +-- "NoSuchMethodError/NoSuchFieldError"
    |       JVMS 5.4.3 + JLS Ch.13
    |       -> javap -c caller, javap -p runtime JAR
    |
    +-- "Static initializer chưa chạy / chạy sớm"
    |       JLS 12.4 + JVMS 5.5 + Class.forName Javadoc
    |       -> thử Class.forName(..., false/true, loader)
    |
    +-- "getResource không thấy file trong JAR"
    |       ClassLoader/Class/Module Javadoc
    |       -> jar --list, in resource URL/loader
    |
    +-- "ServiceLoader provider not a subtype"
    |       ServiceLoader Javadoc + JVMS 5.3
    |       -> so Class identity của service và provider
    |
    +-- "IllegalAccessError / reflection bị chặn"
    |       JVMS 5.4.4 + JLS 6.6 + AccessibleObject Javadoc
    |       -> check exports/opens/modules + javap
    |
    +-- "Split package / ModuleLayer không tạo được"
    |       ModuleLayer/Configuration Javadoc + JLS 7.7
    |       -> jar --describe-module; jdeps --check
    |
    +-- "Startup chậm do class loading?"
            JVMS 5.3/5.5 + ClassLoader Javadoc
            -> java -Xlog:class+load, JFR, startup profiling
~~~

Lưu ý: đây là **điểm bắt đầu** tra cứu, không phải bản xác định nguyên nhân tự động. Ví dụ `ClassCastException` cũng có thể xuất hiện chỉ vì cast logic sai, không có nhiều loaders; `NoSuchMethodError` có thể do binary incompatibility dù classpath chỉ có một loader.

## 8. Bảng tra chi tiết theo triệu chứng thực tế

| Quan sát | Nguồn chuẩn mở trước | Công cụ/bằng chứng | Điều cần kiểm chứng |
| --- | --- | --- | --- |
| `ClassNotFoundException` | ClassLoader Javadoc; JVMS 5.3 | Runtime `-cp`/module path, JAR entries | Đúng binary name, loader scope, resource có trong artifact? |
| `NoClassDefFoundError` | JVMS 5.4–5.5, JLS 12.4 | Full stack trace và cause, init logs | Dependency thiếu, initialization thất bại hay class definition không phù hợp? |
| `ClassCastException` cùng tên | JVMS 5.3, Class.getClassLoader | In cả hai `Class<?>`, loader và module | Cùng binary name nhưng khác defining loader? |
| `VerifyError` | JVMS 4.9–4.10 | `javap -v -p`, transformer output | Operand stack, locals, frames và version hợp lệ? |
| `ClassFormatError` | JVMS Chapter 4 | File bytes, `javap`, build steps | Header/constant pool/attributes sai? |
| `UnsupportedClassVersionError` | ClassLoader Javadoc, JVMS 4.1 | `javap -v` xem major version, `java -version` | Class được biên dịch cho JDK mới hơn runtime? |
| `NoSuchMethodError` | JVMS 5.4.3, JLS 13 | `javap -c` caller, `javap -p` runtime library | Method name, descriptor và version có giống compile-time? |
| `IllegalAccessError` | JVMS 5.4.4, JLS 13 | Caller/target classes, `Module.isExported` | Binary visibility/module access bị đổi? |
| `IllegalAccessException` | Method/Field Javadoc | Reflection callsite và modifiers | Caller có quyền invoke/get mà không suppress checks? |
| `InaccessibleObjectException` | AccessibleObject, Module Javadoc | `isOpen`, `module-info.java`, launcher args | Package có mở cho caller module? |
| `ServiceConfigurationError` | ServiceLoader Javadoc | `META-INF/services`, `jar --list`, loader identity | Provider class có subtype của đúng service Class? |
| `LayerInstantiationException` | ModuleLayer Javadoc | Module names/packages/loader mapping | Overlapping packages, mapping loader hoặc split delegation? |
| `ResolutionException` | Configuration, java.lang.module Javadoc | `jar --describe-module`, module graph | Exports/readability/dependency conflict? |
| Resource trả null | ClassLoader/Class/Module Javadoc | `getResource` URL và `jar --list` | Tên resource, dấu slash, module encapsulation? |
| Class unloading không xảy ra | JLS 12.7, ClassLoader/JFR docs | Loader stats/JFR/heap analysis | Còn reference tới loader/static cache/thread/TCCL? |
| Startup hoặc p99 đầu tiên chậm | JVMS 5.3–5.5, JFR docs | Startup traces, JFR, class logging | Loading hay initialization, I/O, JIT, DI/proxy? |

**Lưu ý khi gặp một Error:** luôn thu **đầy đủ message và cause chain**, binary class names, runtime JDK version, đúng artifact/classloader; việc chỉ đọc tên exception có thể dẫn đến chẩn đoán sai.

## 9. Demo Java 17+ runnable: nối tài liệu với bằng chứng

Demo nhỏ sau tạo một nested class, yêu cầu JVM tìm class nhưng **không yêu cầu initialization**, in loader identity, sau đó thực hiện **active use** khiến static initializer chạy. Chúng ta tiếp tục dùng `javap` và `jar` để đối chiếu bytecode và cách đóng gói.

### 9.1. Cây file

~~~text
lookup-lab/
└── src/
    └── lab/
        └── Probe.java
~~~

### 9.2. File src/lab/Probe.java

~~~java
package lab;

public final class Probe {
    static final class Deferred {
        static {
            System.out.println("Deferred initialized");
        }

        static int number() {
            return 42;
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = Probe.class.getClassLoader();

        Class<?> type = Class.forName(
                "lab.Probe$Deferred", false, loader);

        System.out.println("binary name: " + type.getName());
        System.out.println("same loader: "
                + (type.getClassLoader() == loader));
        System.out.println("module named: "
                + type.getModule().isNamed());

        System.out.println("value: " + Deferred.number());
    }
}
~~~

**Tại sao đúng Java 17:** `Class.forName(String, boolean, ClassLoader)` được Javadoc định nghĩa với flag initialization; `Class.getModule` có từ Java 9. Class `Deferred` là nested static class, có binary name `lab.Probe$Deferred` theo JLS/JVMS naming rules.

### 9.3. Biên dịch

Linux/macOS (bash/zsh), từ `lookup-lab`:

~~~bash
mkdir -p out/classes
javac --release 17 -d out/classes src/lab/Probe.java
java -cp out/classes lab.Probe
~~~

Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force out/classes | Out-Null
javac --release 17 -d out/classes src/lab/Probe.java
java -cp out/classes lab.Probe
~~~

**Stdout chính xác trong demo classpath này:**

~~~text
binary name: lab.Probe$Deferred
same loader: true
module named: false
Deferred initialized
value: 42
~~~

Giải thích:

1. `Class.forName("lab.Probe$Deferred", false, loader)` yêu cầu tìm Class nhưng **không yêu cầu chạy `<clinit>`** của `Deferred`; vì vậy dòng initialization chưa xuất hiện.
2. `type.getName()` in **binary name** với ký tự `$` đối với nested class.
3. `Probe` và `Deferred` được định nghĩa bởi loader từ cùng application classpath root; `same loader: true`.
4. Chúng nằm trong **unnamed module** khi chạy bằng `-cp`, vì thế `module named: false`.
5. Lời gọi `Deferred.number()` là active use, buộc initialization trước khi chạy method body. Thứ tự hai dòng cuối phản ánh điều đó.

**Không nhầm:** kết quả trên chứng minh API không yêu cầu initialization ở bước lookup; không chứng minh JVM không làm bất cứ loại class loading/linking nào trước đó. Thứ tự class-loading events trong VM log có thể khác.

## 10. Dùng javap để xem JVM thực sự nhận instruction nào

Sau khi compile `Probe`, chạy:

~~~bash
javap -classpath out/classes -c -p lab.Probe
~~~

Mục cần tìm trong output:

- `public static void main(java.lang.String[])`.
- Một lời gọi `java/lang/Class.forName` với descriptor có hai tham số bổ sung: `boolean` và `ClassLoader`.
- Một lời gọi `lab/Probe$Deferred.number:()I`.
- Instructions liên quan `getstatic java/lang/System.out`, `invokevirtual java/io/PrintStream.println`.

Để xem metadata chi tiết:

~~~bash
javap -classpath out/classes -v -p lab.Probe
~~~

Đây là công cụ quan sát **classfile**, cung cấp constant pool, major version, method descriptors, `Code` attributes, line tables và (khi có) `StackMapTable`.

**Không nên sao chép nguyên output dài của `javap -v` thành “kết quả bất biến”:** offsets, constant pool indexes và bootstrap entries phụ thuộc compiler/build. Hãy dùng nó để so phương thức, opcode, descriptor thực của artifact cụ thể.

### 10.1. Xem nested class đúng cách

Với bash, zsh hoặc PowerShell, đặt binary name có `$` vào dấu nháy **đơn** để shell không diễn giải ký tự:

~~~bash
javap -classpath out/classes -c -p 'lab.Probe$Deferred'
~~~

Trong một số môi trường khác như Windows CMD, quoting rules khác; bạn có thể chỉ định đường dẫn file class đã được tạo hoặc escape tên theo shell thực tế. Khi thấy output static initializer `static {};` hoặc `<clinit>`, liên hệ với JLS `12.4` và JVMS `5.5` thay vì đoán từ Java source.

## 11. Dùng jar để xác nhận artifact có đúng classfile không

Từ thư mục `lookup-lab`:

~~~bash
jar --create --file out/probe.jar -C out/classes .
jar --list --file out/probe.jar
java -cp out/probe.jar lab.Probe
~~~

**Lưu ý:** JAR trên **không** thiết lập `Main-Class` trong manifest, nên lệnh đúng ở đây là `java -cp out/probe.jar lab.Probe`, **không phải** `java -jar out/probe.jar`.

Trong kết quả `jar --list` sẽ có ít nhất:

~~~text
lab/Probe.class
lab/Probe$Deferred.class
~~~

JAR có thể có thêm entries như `META-INF/` và thứ tự output có thể khác; không yêu cầu output chỉ gồm hai dòng trên.

`java -cp out/probe.jar lab.Probe` sẽ có **cùng stdout năm dòng của mục 9.3**, vì class và nested class đều nằm đúng JAR root. Lệnh `jar --list` chứng minh artifact có entries, **không tự chứng minh** application runtime đã nạp class từ chính JAR đó nếu classpath có nhiều root trùng tên. Muốn điều tra runtime origin, kết hợp class-loading log và inspection của loader/Class.

### 11.1. Nhìn module descriptor

Với một JAR có `module-info.class` hoặc automatic module, thử:

~~~bash
jar --describe-module --file some-library.jar
~~~

**Đây là lệnh mẫu**, yêu cầu JAR thực tế. Nếu artifact là automatic module, công cụ có thể cho biết derived module name; nếu không phải modular JAR, đọc output theo semantics của tool. Đừng giả định mọi JAR có explicit `module-info.java`.

## 12. Dùng jdeps để xem dependencies thay vì suy từ imports

Với `out/probe.jar` vừa build:

~~~bash
jdeps --summary out/probe.jar
~~~

Mục đích là xem dependency ở mức module/archive. Với demo chỉ dùng APIs chuẩn, kết quả cần xem dependency tới **java.base**; cách trình bày đường dẫn, tên archive và wording là do công cụ định dạng, không phải transcript cố định.

Các lệnh hữu ích khác khi có artifact thật:

~~~bash
jdeps --verbose:class app.jar
jdeps --jdk-internals app.jar
jdeps --list-deps app.jar
~~~

- `--verbose:class`: class-level dependency edges trong phạm vi analyzer quan sát.
- `--jdk-internals`: tìm tham chiếu tới **JDK internal APIs**, hữu ích khi nâng JDK.
- `--list-deps`: liệt kê module dependencies theo kết quả phân tích.

Với multi-release JAR, dùng `--multi-release 17` khi cần phân tích view phù hợp target JDK:

~~~bash
jdeps --multi-release 17 --summary multi-release-app.jar
~~~

Nếu đang kiểm tra named module có tên xác định trên module path:

~~~bash
jdeps --module-path mods --check module.name
~~~

Hai lệnh cuối là **mẫu**: chúng yêu cầu file `multi-release-app.jar`, thư mục `mods` hoặc `module.name` thực sự tồn tại. Không dùng trực tiếp với `out/probe.jar` ở trên để kiểm tra `module.name`, vì demo chạy trên classpath và không có explicit module descriptor.

**Giới hạn:** `jdeps` là **static dependency analysis**. Nó không tự biết tên class được ghép từ chuỗi rồi nạp bằng reflection hay plugin metadata, cũng không thay việc kiểm tra actual runtime `ClassLoader`, `ServiceLoader` và module resolution graph.

## 13. Quan sát class loading trong một JVM process

### 13.1. Unified Logging

OpenJDK/HotSpot Java 17+ hỗ trợ:

~~~bash
java -Xlog:class+load=info -cp out/classes lab.Probe
~~~

Các log lines có thể cho biết class nào được load và nguồn classfile. Do JVM cũng load rất nhiều Java core classes, output rất dài. **Không có stdout cố định** cho lệnh có logging này; chỉ năm dòng do `Probe` tự in mới có thứ tự dự kiến như mục 9.3, và logs có thể xen vào stdout/stderr.

Khi điều tra unloading:

~~~bash
java -Xlog:class+unload=info -cp out/classes lab.Probe
~~~

Không kỳ vọng sẽ **thấy unload event** trong demo ngắn: Class có thể không unload trước process exit hoặc không đủ điều kiện unload. Không có event không chứng minh class unloading bị tắt.

Một số bản HotSpot hỗ trợ các logging levels/tags bổ sung. Hãy kiểm tra `java -Xlog:help` trên chính runtime để biết tag combinations mà build của bạn cho phép; JDK tool manual là nguồn cú pháp, kết quả thực tế là bằng chứng configuration.

### 13.2. jcmd: quan sát JVM đang chạy

Đầu tiên:

~~~bash
jcmd -l
~~~

Lệnh thường hiển thị các Java processes có thể attach theo quyền OS. Chọn **PID của tiến trình thực cần kiểm tra**, sau đó:

~~~text
jcmd PID help
jcmd PID VM.classloader_stats
jcmd PID VM.class_hierarchy
~~~

Các dòng trên là **template**, chữ `PID` phải được thay bằng **số process ID thật**; không phải command có thể chạy đúng nguyên văn. `VM.classloader_stats` cho thống kê class loaders, `VM.class_hierarchy` cho class hierarchy theo chẩn đoán HotSpot. Một số command có impact và permission requirements; tham khảo `jcmd PID help COMMAND` trong cùng JDK version.

**Không nhầm giữa loader có thể “find/load” một class và loader đã “define” class đó.** Với parent delegation, một application/plugin loader có thể yêu cầu class nhưng parent mới là defining loader. Nếu cần chẩn đoán identity, in `clazz.getClassLoader()` trong code với hai `Class<?>` cụ thể.

### 13.3. JFR cho startup/performance

JFR có thể thu thông tin liên quan loading, allocations, CPU và nhiều events khác theo cấu hình, nhưng **không mặc định mọi event đều bật với cùng độ chi tiết**.

Ví dụ kích hoạt recording từ lúc chạy ứng dụng:

~~~bash
java -XX:StartFlightRecording=filename=out/startup.jfr,settings=profile -cp out/classes lab.Probe
~~~

Sau đó xem recording:

~~~bash
jfr summary out/startup.jfr
jfr print out/startup.jfr
~~~

Với tiến trình quá ngắn, hãy xác minh file recording có thực và chứa events mong muốn; startup trigger, duration và JVM build có thể ảnh hưởng. Để phân tích class-loading events cụ thể, điều chỉnh event settings có kiểm soát thay vì kết luận “JFR không thấy nên class không load”.

## 14. Thực hành tra cứu theo từng ca lỗi

### 14.1. Ca A: ClassCastException “A cannot be cast to A”

**Triệu chứng:** cast một plugin object thành interface phía host thất bại mặc dù tên interface trong source giống nhau.

**Nguồn:** JVMS `5.3` (identity của runtime class là binary name + defining loader); Javadoc `Class.getClassLoader`, `Class.isAssignableFrom`.

**Thu thập bằng chứng:**

~~~java
Class<?> hostApi = HostApi.class;
Class<?> pluginApi = pluginLoader.loadClass(
        HostApi.class.getName());

System.out.println(hostApi == pluginApi);
System.out.println(hostApi.getClassLoader());
System.out.println(pluginApi.getClassLoader());
~~~

**Mã minh họa**, cần có `HostApi` và `pluginLoader`. Nếu `hostApi == pluginApi` false, plugin có thể đang define bản API riêng. Nếu true, cần điều tra cast target/object thực sự chứ không kết luận lỗi vì loader.

**Cách sửa:** chia sẻ interface/DTO/exception/annotation contract qua common parent loader/module, để private implementation classes ở plugin loader.

### 14.2. Ca B: NoSuchMethodError chỉ ở production

**Nguồn:** JVMS `5.4.3` resolution; JLS Chapter 13 binary compatibility; Javadoc method API nếu đó là Java library.

**Bằng chứng:**

~~~bash
javap -classpath compiled-client.jar -c -p client.Main
javap -classpath deployed-library.jar -p api.Library
jar --list --file deployed-library.jar
~~~

**Lệnh mẫu**, cần artifact thật. Xác định method name và descriptor mà client bytecode tham chiếu, đối chiếu với runtime library class. Có thể method bị đổi parameter, return type, static/instance form hoặc artifact deployment sai version.

**Cách sửa:** thống nhất compile/runtime dependency versions và test đúng packaged artifact. Không dùng `Class.forName` để “chữa” một symbolic method reference không còn hợp lệ.

### 14.3. Ca C: Resource tồn tại nhưng getResourceAsStream trả null

**Nguồn:** Javadoc `ClassLoader.getResourceAsStream`, `Class.getResource`, `Module.getResourceAsStream`.

**Bằng chứng:**

~~~bash
jar --list --file application.jar
~~~

và trong code:

~~~java
URL url = loader.getResource("config/app.properties");
System.out.println(url);
~~~

**Mã minh họa**, loader phải được khởi tạo. Kiểm tra tên entry `config/app.properties` đúng classpath root, slash đầu khác nhau giữa `Class` và `ClassLoader` APIs, loader lookup scope và JPMS resource encapsulation.

**Cách sửa:** đóng resources đúng root, chọn API/loader đúng, ưu tiên `getResourceAsStream` cho bytes trong JAR thay vì ép JAR URL thành `File`.

### 14.4. Ca D: VerifyError sau bytecode weaving

**Nguồn:** JVMS `4.9–4.10` và `5.4.1`.

**Bằng chứng:**

~~~bash
javap -classpath transformed.jar -v -p app.Transformed
~~~

Kiểm tra `Code` attributes, `max_stack`/`max_locals`, `StackMapTable` và classfile major version. Dùng **transformed artifact thực** làm đầu vào, không chỉ xem source trước weaving. `VerifyError` không phải chứng cứ rằng ứng dụng đã thử chạy mọi execution path và thấy logic sai.

### 14.5. Ca E: Split package/layer creation failure

**Nguồn:** Javadoc `Configuration`/`ModuleLayer` và JLS `7.7`.

**Bằng chứng:** list module descriptors/packages và phân biệt **module resolution** với **ModuleLayer definition**. `defineModulesWithOneLoader` không thể dùng một loader chứa các modules có package overlap; `defineModulesWithManyLoaders` cho mapping khác nhưng không sửa graph vi phạm exported package readability.

Lệnh hỗ trợ:

~~~bash
jar --describe-module --file library-a.jar
jar --describe-module --file library-b.jar
jdeps --module-path mods --check module.name
~~~

Các tên file/module là **placeholder dạng mẫu**, thay bằng artifacts thật; cần xem errors và graph cụ thể, không khẳng định hai JAR cùng có package trên disk luôn làm JVM fail ngay lập tức.

## 15. Tra cứu “phần nào của Java chịu trách nhiệm?” bằng ba bước

Một mẹo giúp tránh đọc sai nguồn:

### Bước 1 — Xác định đối tượng đang được hỏi

- Java source expression/declaration? → JLS.
- Bytecode opcode, constant pool, classfile verification, runtime identity? → JVMS.
- Method của `ClassLoader`/`Class`/`ServiceLoader`/`Proxy`? → Javadoc.
- Cú pháp `java`, `javap`, `jar`, `jdeps`, `jcmd`? → Tool manual.
- Một hành vi quan sát được trong HotSpot? → Run logs/JFR/source implementation, **sau đó** kiểm tra phần được spec cho phép.

### Bước 2 — Xác định version

Ví dụ: `ClassLoader` từ Java 8 và Java 17 khác nhau về named modules và API bổ sung. Tra cứu phải gắn version:

~~~text
Java SE 17:
  /javase/17/docs/api/
  /specs/jls/se17/
  /specs/jvms/se17/

Java phiên bản khác:
  tra đúng API/spec của phiên bản compiler/runtime dự kiến,
  không trộn một đoạn tutorial Java 8 với JPMS Java 17.
~~~

Với “Java hiện tại” hoặc một sự cố runtime thật, ghi **đúng phiên bản JDK trong dự án**. Ví dụ có thể chạy với JDK 17, không có nghĩa mọi tool output hoặc module access rule giống hệt ở mọi JDK sau đó.

### Bước 3 — Kiểm chứng bằng artifact/runtime thực

- Build lại sạch từ source/dependencies đã khóa.
- Kiểm tra `java -version`, `javac -version`, `java -cp` hoặc `--module-path`.
- `jar --list` để biết có bytes/resources không.
- `javap` để kiểm tra symbolic references/bytecode đã compile.
- `jdeps` cho static dependencies, nhưng đừng dùng nó làm chứng cứ toàn diện cho reflective loading.
- `jcmd`/JFR/logging để quan sát process khi lỗi chỉ xảy ra sau startup.
- In `Class.getName()`, `getClassLoader()`, `getModule()` đối với classes liên quan.

**Không phụ thuộc riêng vào một command:** `jar --list` có thể chứng minh file có trong JAR, nhưng class loader runtime có thể đã lấy một version khác từ parent. Ngược lại, thấy class được load không chứng minh static initializer đã chạy.

## 16. Đọc bản chuẩn bằng bookmark và từ khóa

Một bộ bookmark nhỏ có ích hơn việc ghi nhớ toàn bộ 600 trang spec:

| Bookmark | Từ khóa nên tìm trong trang |
| --- | --- |
| JVMS `5.3` | `defining loader`, `initiating loader`, `run-time package` |
| JVMS `5.4` | `verification`, `preparation`, `resolution`, `access control` |
| JVMS `5.5` | `initialization`, `initialization lock`, `erroneous state` |
| JVMS Chapter 4 | `ClassFile`, `Code`, `StackMapTable` |
| JLS `12.4` | `initialized`, `static`, `constant variable` |
| JLS Chapter 13 | `binary compatibility`, `accessibility` |
| ClassLoader Javadoc | `loadClass`, `findClass`, `getResource`, `parallel capable` |
| Class Javadoc | `forName`, `getClassLoader`, `getModule` |
| ModuleLayer Javadoc | `defineModulesWithOneLoader`, `defineModulesWithManyLoaders` |
| ServiceLoader Javadoc | `provider`, `META-INF/services`, `ServiceConfigurationError` |
| Proxy Javadoc | `newProxyInstance`, `visibility`, `non-public` |
| jcmd manual | `VM.classloader_stats`, `VM.class_hierarchy` |
| java manual | `-Xlog`, `--module-path`, `--add-exports`, `--add-opens` |

Khi đọc Javadoc, dùng tìm kiếm trong trang (`Ctrl+F` / `Cmd+F`) cho **tên method chính xác**. Chú ý các overload có tên giống nhau nhưng khác loader/context hoặc boolean flag. Với `ClassLoader.getResource` và `Class.getResource`, **dấu `/` ở đầu** mang ý nghĩa khác; đừng chép ví dụ API này vào API kia mà bỏ qua Javadoc.

## 17. Ngộ nhận phổ biến khi tra cứu ClassLoader

| Ngộ nhận | Cách kiểm chứng và hiểu đúng |
| --- | --- |
| “JLS và JVMS là hai bản copy của cùng luật” | JLS mô tả language semantics, JVMS mô tả classfile/runtime mechanics |
| “Đọc Javadoc là đủ kết luận mọi opcode JVM” | Method contract phải kết hợp JVMS khi nói về linking/bytecode |
| “Một HotSpot trace là quy tắc chuẩn trên mọi JVM” | Tool logs thường implementation-specific |
| “Hai class cùng binary name là cùng kiểu” | So `Class<?>` và defining loader, xem JVMS `5.3` |
| “loadClass luôn chạy static initializer” | Đối chiếu ClassLoader/Class Javadoc và JLS `12.4` |
| “Class.forName(false) không làm gì” | Có thể yêu cầu loading/linking, nhưng không yêu cầu initialization |
| “javap in đúng source” | Nó in **classfile** đã compile, không khôi phục đầy đủ ý định source |
| “jdeps thấy hết dynamic dependencies” | Reflection, ServiceLoader và computed names có thể không hiện trong static graph |
| “jar --list chứng minh app dùng đúng JAR” | Nó chỉ chứng minh artifact entries, không xác định runtime lookup order |
| “Cần một lệnh để chắc chắn mọi class đã loaded” | Runtime changes over time; tools có scope/permission/impact |
| “Thấy VerifyError nghĩa là logic Java sai” | Đó là bytecode verification issue |
| “NoSuchMethodError là compile error bình thường” | Thường là runtime binary linkage mismatch |
| “exports tương đương opens” | JPMS phân biệt public API access với deep reflection |
| “Tên class loader trong logs là Java SE API contract” | Implementation class names/log formats có thể khác |
| “Nếu không thấy ClassUnload log là JVM không unload” | Cần đủ điều kiện unloading, event/time window và logging |
| “Chỉ cần đổi classpath order sẽ sửa mọi duplicate API” | Cần biết loader graph, parent delegation và module ownership |

## 18. Checklist viết một báo cáo lỗi có thể tái lập

Trước khi nhờ đồng đội hoặc gửi issue cho thư viện/plugin framework, ghi:

1. **Triệu chứng:** exception/error đầy đủ, phần `Caused by` và thao tác gây lỗi.
2. **Runtime:** `java -version`, JDK vendor/build, OS và kiến trúc.
3. **Compile:** `javac --release` hoặc compiler target, dependency versions.
4. **Launch:** command-line arguments `-cp`/`--module-path`, module roots, relevant flags.
5. **Artifact:** chính file JAR/class mà runtime dùng; kết quả `jar --list`/`javap` cần thiết.
6. **Class identity:** cả hai `Class<?>` đang tương tác, `getName`/`getClassLoader`/`getModule`.
7. **Module access:** `requires`, `exports`, `opens`, caller/target modules nếu có.
8. **Resource path:** tên logic, directory/JAR entry và loader đã dùng.
9. **Tool evidence:** lệnh và vài dòng output liên quan, tránh dump hàng nghìn dòng class log không lọc.
10. **Nguồn chuẩn:** mục JVMS/JLS/Javadoc cụ thể, có version và fragment `#...`.
11. **Reproduction:** một project nhỏ đủ lệnh compile/run, nêu expected và actual; không thay code giữa các lần mà không ghi.
12. **Kết luận phân loại:** điều chắc chắn từ evidence, giả thuyết còn thiếu và thí nghiệm tiếp theo.

Khi viện dẫn spec nên ghi như sau:

~~~text
Claim: Hai Class cùng binary name nhưng khác defining ClassLoader
       không nhất thiết là cùng runtime type.
Source: JVMS SE 17 `5.3` — Creation and Loading.
Evidence: hostApi == pluginApi -> false; hai defining loader khác.
Scope: cùng process / classpath+plugin loader setup đã mô tả.
~~~

Ví dụ trên chỉ là **mẫu cách báo cáo**, không phải kết quả của demo `Probe` ở mục 9. Nó minh họa cách tách **quy tắc chuẩn** và **bằng chứng cụ thể**.

## 19. Bài tập tự kiểm tra và câu trả lời

**1. Muốn hiểu danh tính runtime của class, mở nguồn nào trước?**  
JVMS `5.3`; sau đó dùng `Class.getClassLoader()` và `==` trên hai Class runtime.

**2. Muốn biết `ClassLoader.getResourceAsStream` trả gì khi file thiếu?**  
Javadoc `ClassLoader`; thường trả null theo hợp đồng.

**3. Muốn hiểu static initializer chạy khi nào?**  
JLS `12.4` và JVMS `5.5`; đối chiếu `Class.forName` Javadoc khi dùng reflection.

**4. Gặp `NoSuchMethodError` sau khi deploy, nên xem gì?**  
JVMS resolution, JLS binary compatibility, `javap` caller và runtime library.

**5. Lệnh nào xem bytecode của một class?**  
`javap -c -p -classpath ROOT binary.Name`; `-v` xem thêm constant pool/attributes.

**6. Lệnh nào kiểm tra entry resource trong JAR?**  
`jar --list --file app.jar`.

**7. `jdeps` có luôn phát hiện class chỉ được nạp theo string từ reflection?**  
Không; đây là static dependency analyzer, không có quyền tiên tri dynamic behavior.

**8. Phân biệt `exports` và `opens` tìm ở đâu?**  
JLS `7.7` và Javadoc `Module`/`AccessibleObject`; kiểm tra caller/target module cụ thể.

**9. `jcmd PID VM.classloader_stats` là cú pháp hoàn chỉnh có thể copy ngay?**  
Không. Thay PID bằng số process ID thực và kiểm tra process attach permissions.

**10. Vì sao Class.forName(..., false, ...) không đồng nghĩa không load class?**  
Flag false liên quan việc **không yêu cầu initialization**, không phủ nhận loading/linking.

**11. Demo `Probe` chạy trên classpath thì `Class.getModule().isNamed()` là gì?**  
False, vì class thuộc unnamed module của loader.

**12. `java -Xlog:class+load=info` có output giống hệt trên mọi JVM/JDK?**  
Không. Đó là diagnostic logging của runtime hỗ trợ; logs, số lượng/thứ tự tùy môi trường.

**13. Khi nào Javadoc được ưu tiên hơn JVMS?**  
Khi câu hỏi trực tiếp là hợp đồng và edge case của một method thư viện cụ thể như `ClassLoader.getResources`.

**14. Tài liệu JEP có thay thế Java SE 17 API spec không?**  
Không. Dùng JEP để hiểu ý tưởng/động lực; dùng spec/Javadoc của target version cho behavior.

**15. Lý do phải ghi JDK version trong báo cáo?**  
Language/API, tool options, implementation diagnostics và module/security defaults có thể thay đổi theo phiên bản.

## 20. Tài nguyên tổng hợp và đường dẫn chính thức

### Đặc tả Java 17

- JVMS 17 — index: https://docs.oracle.com/javase/specs/jvms/se17/html/index.html
- JVMS 17 — Chapter 4 (class files): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
- JVMS 17 — Chapter 5 (loading/linking/initialization): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- JVMS 17 — Chapter 6 (instructions): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html
- JLS 17 — index: https://docs.oracle.com/javase/specs/jls/se17/html/index.html
- JLS 17 — Chapter 12 (execution): https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html
- JLS 17 — Chapter 13 (binary compatibility): https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html
- JLS 17 — Chapter 7 (packages/modules): https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html

### Javadoc và công cụ

- Java SE 17 API index: https://docs.oracle.com/en/java/javase/17/docs/api/index.html
- Java SE 17 tools/man pages: https://docs.oracle.com/en/java/javase/17/docs/specs/man/index.html
- `java`: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- `javap`: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html
- `jar`: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jar.html
- `jdeps`: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jdeps.html
- `jcmd`: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- `jfr`: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html

**Ghi nhớ:** Spec trả lời *hệ thống phải bảo đảm điều gì*; Javadoc trả lời *API hứa hành vi gì*; các công cụ JDK cung cấp *bằng chứng về artifact/runtime cụ thể*. Kết luận kỹ thuật tốt cần nối được cả ba.


</details>

- [Quay lại đầu trang](#back-to-top)
